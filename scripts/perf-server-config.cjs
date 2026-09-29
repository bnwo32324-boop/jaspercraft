'use strict';
// Applies (or checks) the measured Paper/Spigot/Bukkit performance settings on a JasperCraft server folder.
//   node scripts/perf-server-config.cjs [serverDir] [--check]
// The three YAML files are not tracked in git (Paper rewrites them on every start), so this script is the record of the
// change. It edits values in place (comments and layout kept), backs each changed file up next to it once
// (<file>.before-perf), and is idempotent. Measured in the performance sandbox: see PERFORMANCE_UPDATE.md.
const fs = require('node:fs');
const path = require('node:path');

// [file, dotted key, value, why]
const SETTINGS = [
  ['paper.yml', 'timings.enabled', 'false', 'Timings profiler off (it cost ~3% of every tick)'],
  ['paper.yml', 'world-settings.default.queue-light-updates', 'true', 'Lighting updates batched per tick (Phosphor-style)'],
  ['paper.yml', 'world-settings.default.container-update-tick-rate', '3', 'Open inventories resync every 3 ticks'],
  ['paper.yml', 'world-settings.default.grass-spread-tick-rate', '4', 'Grass/mycelium spread checks every 4 ticks'],
  ['paper.yml', 'world-settings.default.mob-spawner-tick-rate', '2', 'Mob spawners tick every 2 ticks'],
  ['paper.yml', 'world-settings.default.armor-stands-do-collision-entity-lookups', 'false', 'Armor stands stop scanning for entities to push'],
  ['paper.yml', 'world-settings.default.game-mechanics.disable-chest-cat-detection', 'true', 'Chests no longer scan for sitting cats'],
  ['spigot.yml', 'world-settings.default.max-entity-collisions', '2', 'Mob crowds push at most 2 neighbours per tick'],
  ['spigot.yml', 'world-settings.default.entity-activation-range.tick-inactive-villagers', 'false', 'Far villagers tick like other inactive mobs'],
  ['bukkit.yml', 'chunk-gc.period-in-ticks', '400', 'Unused chunks unloaded sooner'],
];
// [file, world, key, value, why]
const WORLD_SETTINGS = [
  ['paper.yml', 'world_nether', 'keep-spawn-loaded', 'false', 'Nether spawn chunks not kept loaded with nobody there'],
  ['paper.yml', 'world_the_end', 'keep-spawn-loaded', 'false', 'End spawn chunks not kept loaded with nobody there'],
];

function walk(lines, dotted) {
  const want = dotted.split('.'), stack = [];
  for (let i = 0; i < lines.length; i++) {
    const m = /^(\s*)([^\s#:][^:]*?|'[^']*'|"[^"]*"):(\s*)(.*)$/.exec(lines[i]);
    if (!m || lines[i].trimStart().startsWith('-')) continue;
    const indent = m[1].length, key = m[2].replace(/^['"]|['"]$/g, '');
    while (stack.length && stack[stack.length - 1].indent >= indent) stack.pop();
    stack.push({ indent, key });
    if (stack.length === want.length && stack.every((s, j) => s.key === want[j])) return { i, m };
  }
  return null;
}

function setKey(lines, dotted, value) {
  const hit = walk(lines, dotted);
  if (!hit) throw new Error(`key not found: ${dotted}`);
  const current = hit.m[4].replace(/\s+#.*$/, '').trim();
  if (current === value) return false;
  const comment = /\s+#.*$/.exec(hit.m[4]);
  lines[hit.i] = `${hit.m[1]}${hit.m[2]}: ${value}${comment ? comment[0] : ''}`;
  return true;
}

function setWorld(lines, world, key, value) {
  const ws = lines.findIndex((l) => /^world-settings:\s*$/.test(l));
  if (ws < 0) throw new Error('no world-settings');
  let section = -1, end = lines.length;
  for (let i = ws + 1; i < lines.length; i++) { if (/^\S/.test(lines[i])) { end = i; break; } if (lines[i] === `  ${world}:`) section = i; }
  if (section < 0) { lines.splice(ws + 1, 0, `  ${world}:`, `    ${key}: ${value}`); return true; }
  for (let i = section + 1; i < end && /^    /.test(lines[i]); i++) {
    if (lines[i].startsWith(`    ${key}:`)) { if (lines[i].trim() === `${key}: ${value}`) return false; lines[i] = `    ${key}: ${value}`; return true; }
  }
  lines.splice(section + 1, 0, `    ${key}: ${value}`);
  return true;
}

function main() {
  const args = process.argv.slice(2);
  const check = args.includes('--check');
  const server = path.resolve(args.find((a) => !a.startsWith('--')) || path.join(__dirname, '..', 'server'));
  let pending = 0;
  for (const file of ['paper.yml', 'spigot.yml', 'bukkit.yml']) {
    const full = path.join(server, file);
    const text = fs.readFileSync(full, 'utf8');
    const eol = text.includes('\r\n') ? '\r\n' : '\n';
    const lines = text.split(/\r?\n/);
    let changed = 0;
    for (const [f, key, value, why] of SETTINGS) if (f === file && setKey(lines, key, value)) { changed++; console.log(`${check ? 'would set' : 'set'} ${file} ${key} = ${value}  (${why})`); }
    for (const [f, world, key, value, why] of WORLD_SETTINGS) if (f === file && setWorld(lines, world, key, value)) { changed++; console.log(`${check ? 'would set' : 'set'} ${file} world-settings.${world}.${key} = ${value}  (${why})`); }
    pending += changed;
    if (!changed || check) continue;
    const backup = full + '.before-perf';
    if (!fs.existsSync(backup)) fs.copyFileSync(full, backup);
    fs.writeFileSync(full, lines.join(eol));
  }
  console.log(pending ? (check ? `${pending} setting(s) not applied yet` : `applied ${pending} setting(s); Paper reads them at the next start`) : 'all performance settings already applied');
  if (check && pending) process.exitCode = 1;
}

main();
