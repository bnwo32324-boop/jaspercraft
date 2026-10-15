'use strict';
// JasprMutantLairs on ONE short isolated Paper 1.12.2 test server (port 25687, one core, below-normal priority, a watchdog),
// with JasprMutants (the real mutant bosses) and one protocol bot. No live server, world or player data is touched.
//   node tests/mutant-lairs-server.cjs <JasprMutantLairs.jar> <scratch dir>
// Phases: A) every one of the twelve lairs built by the operator command and checked block by block against its drawing;
// B) a boss woken by walking in (the real mutant, its name, health, garrison, the discovery title), its sealed vault
// refusing to open, the boss killed, the vault opened, the lair still matching its drawing with the seals gone;
// C) a lair found and built by the Director on its own in untouched land; D) a restart: everything persisted.
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), cp = require('node:child_process'), net = require('node:net');
const MODULES = process.env.LAIRS_NODE_MODULES || 'C:/Users/AM/Documents/JasperCraft-Dungeon-Sandbox-20261002/node_modules';
const protocol = require(path.join(MODULES, 'minecraft-protocol'));
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin/';
const PORT = 25687, WATCHDOG_MS = 14 * 60 * 1000;
const jar = process.argv[2], dir = process.argv[3];
const MUTANTS = process.env.MUTANTS_JAR || LIVE + '/server/plugins/JasprMutants.jar';
if (!jar || !dir || !fs.existsSync(jar)) { console.error('usage: node tests/mutant-lairs-server.cjs <JasprMutantLairs.jar> <scratch dir>'); process.exit(2); }
const delay = ms => new Promise(r => setTimeout(r, ms));
const results = [];
let server = null, log = '', exitCode = 0;
function check(name, ok, actual) {
  results.push({ name, passed: !!ok, actual });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : ' ' + JSON.stringify(actual).slice(0, 700)));
  if (!ok) exitCode = 1;
}
async function until(fn, ms = 20000, what = '') { const end = Date.now() + ms; while (Date.now() < end) { const v = fn(); if (v) return v; await delay(100); } throw Error('timeout ' + what); }
function put(file, text) { const p = path.join(dir, file); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, text); }
async function free() { await new Promise((res, rej) => { const s = net.createServer(); s.once('error', rej); s.listen(PORT, '127.0.0.1', () => s.close(res)); }); }

function prepare() {
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  fs.copyFileSync(LIVE + '/server/jaspr-paper-clientbudget.jar', path.join(dir, 'paper.jar'));
  fs.copyFileSync(MUTANTS, path.join(dir, 'plugins/JasprMutants.jar'));
  fs.copyFileSync(jar, path.join(dir, 'plugins/JasprMutantLairs.jar'));
  put('plugins/JasprMutantLairs/config.yml', 'enabled: true\nworld: world\nspawn-exclusion: 0\nplayer-margin: 40\nmax-inhabited-ticks: 1200\nboss-health-multiplier: 2.0\nboss-rest-minutes: 1\n');
  put('plugins/bStats/config.yml', 'enabled: false\n');
  put('eula.txt', 'eula=true\n');
  put('server.properties', 'server-ip=127.0.0.1\nserver-port=' + PORT + '\nonline-mode=false\nlevel-type=FLAT\nlevel-name=world\n' +
    'generator-settings=3;minecraft:bedrock,59*minecraft:stone,3*minecraft:dirt,minecraft:grass;1;\nspawn-protection=0\nview-distance=3\n' +
    'generate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=2\ndifficulty=2\n' +
    'network-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\nsnooper-enabled=false\n');
  put('bukkit.yml', 'settings:\n  allow-end: false\nticks-per:\n  autosave: 0\n');
  put('paper.yml', 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n');
  put('spigot.yml', 'config-version: 11\nsettings:\n  late-bind: false\n  timeout-time: 300\n  restart-on-crash: false\nstats:\n  disable-saving: true\n');
  put('ops.json', '[]');
}
async function start() {
  await free();
  log = '';
  server = cp.spawn(JAVA + 'java.exe', ['-XX:ActiveProcessorCount=1', '-XX:+UseSerialGC', '-Xms256m', '-Xmx1280m', '-Dcom.mojang.eula.agree=true',
    '-DIReallyKnowWhatIAmDoingISwear=true', '-jar', 'paper.jar', 'nogui', '--nojline'], { cwd: dir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  try { os.setPriority(server.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { console.log('priority: ' + e.message); }
  server.stdout.on('data', b => { log += b; }); server.stderr.on('data', b => { log += b; });
  await until(() => log.includes('Done (') || log.includes('FAILED TO BIND') || server.exitCode !== null, 240000, 'server start');
  if (!log.includes('Done (')) throw Error(log.slice(-3000));
}
async function stop() {
  cmd('stop');
  await until(() => server.exitCode !== null, 90000, 'stop').catch(() => server.kill());
}
function cmd(s) { server.stdin.write(s + '\n'); }
async function waitLog(re, from, ms = 60000, what = '') {
  return until(() => { const m = log.slice(from).match(re); return m || null; }, ms, what || String(re));
}
function bot(username) {
  const c = protocol.createClient({ host: '127.0.0.1', port: PORT, username, version: '1.12.2', auth: 'offline' });
  const b = { client: c, titles: [], chats: [], windows: 0, pos: null };
  c.on('error', e => console.log(username + ' error ' + e.message));
  c.on('position', p => { b.pos = p; c.write('teleport_confirm', { teleportId: p.teleportId }); c.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch, onGround: true }); });
  c.on('title', p => { if (p.text) b.titles.push(p.text); });
  c.on('chat', p => { b.chats.push(p.message); });
  c.on('open_window', () => { b.windows++; });
  return b;
}
const LAIRS = ['plague_pit', 'rotting_abattoir', 'gravewarden_mausoleum', 'ossuary_spire', 'colossus_ribcage', 'bone_barbican',
  'blast_crater', 'overgrown_greenhouse', 'powder_mill', 'void_obelisk', 'shattered_observatory', 'ender_grove'];

async function main() {
  prepare();
  await start();
  const watchdog = setTimeout(() => { console.log('WATCHDOG: stopping the test server'); try { server.kill(); } catch (e) {} process.exit(3); }, WATCHDOG_MS);
  check('S1 the plugin and JasprMutants start (LAIRS_READY, no enable error)', /LAIRS_READY version=1\.0\.0 designs=12/.test(log) && !/Error occurred while enabling JasprMutantLairs/.test(log), log.match(/[^\n]*LAIRS_[A-Z_]+[^\n]*/g));

  // ---- A: all twelve, built by the operator command, each in its own cell, each turned differently
  const built = {};
  for (let k = 0; k < LAIRS.length; k++) {
    const x = 600 + (k % 4) * 520, z = 600 + Math.floor(k / 4) * 520, at = log.length;
    cmd('lairs build ' + LAIRS[k] + ' ' + x + ' ' + z + ' ' + (k % 4));
    const m = await waitLog(/LAIR_BUILT key=(\S+) design=(\S+) at=(\S+) blocks=(\d+) ms=(\d+) boss=(\S+) vaultChest=(\S+) vault=(\S+)/, at, 120000, 'build ' + LAIRS[k]);
    built[LAIRS[k]] = { key: m[1], at: m[3].split(',').map(Number), blocks: +m[4], ms: +m[5], boss: m[6].split(',').map(Number), chest: m[7].split(',').map(Number), vault: m[8].split(',').map(Number) };
  }
  check('A1 all twelve lairs built by /lairs build', Object.keys(built).length === 12, Object.keys(built));
  check('A2 each build took under 10 s of chunk steps and wrote thousands of blocks', Object.values(built).every(b => b.ms < 10000 && b.blocks > 2000), Object.values(built).map(b => [b.blocks, b.ms]));
  let at = log.length;
  cmd('lairs check');
  await waitLog(/LAIR_CHECKS n=\d+ bad=\d+/, at, 60000, 'checks');
  const checks = log.slice(at).match(/LAIR_CHECK key=[^\n]*/g) || [];
  const parse = line => { const o = {}; for (const kv of line.trim().split(' ')) { const i = kv.indexOf('='); if (i > 0) o[kv.slice(0, i)] = kv.slice(i + 1); } return o; };
  const cs = checks.map(parse);
  check('A3 every lair matches its drawing block for block (rotations included)', cs.length === 12 && cs.every(c => c.mismatches === '0'), checks);
  check('A4 every chest holds loot, every spawner has its mob, every sign its words, every vault seal is shut',
    cs.every(c => { const [a, b] = c.chests.split('/'); const [s1, s2] = c.spawners.split('/'); const [g1, g2] = c.signs.split('/'); const [h1, h2] = c.sealsShut.split('/'); return a === b && +b >= 2 && s1 === s2 && g1 === g2 && +g2 >= 2 && h1 === h2 && +h2 >= 1; }), checks);

  // ---- B: a boss, its vault
  const A = bot('LairBot');
  await until(() => /LairBot\[.*logged in/.test(log), 30000, 'login');
  await delay(1500);
  const pit = built.plague_pit;
  // the vault first (in creative, so the boss does not wake yet): stand on the chest and try to open it
  cmd('gamemode creative LairBot');
  cmd('tp LairBot ' + (pit.chest[0] + 0.5) + ' ' + (pit.chest[1] + 1) + ' ' + (pit.chest[2] + 0.5));
  await delay(1500);
  const chats0 = A.chats.length;
  A.client.write('block_place', { location: { x: pit.chest[0], y: pit.chest[1], z: pit.chest[2] }, direction: 1, hand: 0, cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5 });
  await delay(800);
  check('B1 a sealed vault chest does not open (and says why)', A.windows === 0 && A.chats.slice(chats0).some(m => /Sealed/.test(m)), { windows: A.windows, chats: A.chats.slice(chats0) });
  // into the arena: the discovery title, the boss wakes
  at = log.length;
  cmd('gamemode survival LairBot');
  cmd('tp LairBot ' + (pit.boss[0] + 0.5) + ' ' + pit.boss[1] + ' ' + (pit.boss[2] + 4.5));
  const wake = await waitLog(/LAIR_BOSS_WAKE key=(\S+) design=plague_pit mutant=(\S+) fallback=(\S+) health=(\d+) minions=(\d+)/, at, 20000, 'wake');
  cmd('gamemode creative LairBot');
  await delay(500);
  check('B2 walking into the arena wakes the real Mutant Zombie (not a stand-in) with twice its health and its garrison',
    wake[2] === 'mutant_zombie' && wake[3] === 'false' && +wake[4] >= 299 && +wake[5] >= 8, wake.slice(1));
  check('B3 the player is told where they are (discovery title) and that the boss stirs', A.titles.some(t => /Plague Pit/.test(t)) && A.titles.some(t => /Pit Warden/.test(t)), A.titles);
  // kill it: the vault opens
  at = log.length;
  cmd('kill @e[tag=jaspr_lair_boss]');
  const dead = await waitLog(/LAIR_BOSS_DEFEATED key=(\S+) design=plague_pit kills=(\d+) vaultOpened=(\S+)/, at, 15000, 'defeat');
  check('B4 the boss falls: the vault opens (first defeat)', dead[3] === 'true' && /LAIR_VAULT_OPENED/.test(log.slice(at)), dead.slice(1));
  at = log.length;
  cmd('lairs check ' + pit.key);
  const after = parse((await waitLog(/LAIR_CHECK key=[^\n]*/, at, 15000, 'check after'))[0]);
  check('B5 after the defeat the lair still matches its drawing, with every seal gone', after.mismatches === '0' && after.sealsShut.startsWith('0/') && after.vaultOpen === 'true', after);
  cmd('tp LairBot ' + (pit.chest[0] + 0.5) + ' ' + (pit.chest[1] + 1) + ' ' + (pit.chest[2] + 0.5));
  await delay(1500);
  A.client.write('block_place', { location: { x: pit.chest[0], y: pit.chest[1], z: pit.chest[2] }, direction: 1, hand: 0, cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5 });
  await until(() => A.windows > 0, 5000, 'open').catch(() => {});
  check('B6 now the vault chest opens', A.windows > 0, A.windows);
  // the boss rests: walking back in does not wake it at once
  at = log.length;
  cmd('gamemode survival LairBot');
  cmd('tp LairBot ' + (pit.boss[0] + 0.5) + ' ' + pit.boss[1] + ' ' + (pit.boss[2] + 4.5));
  await delay(3000);
  cmd('gamemode creative LairBot');
  check('B7 a defeated boss rests (no second wake right away)', !/LAIR_BOSS_WAKE key=\S+ design=plague_pit/.test(log.slice(at)), log.slice(at).match(/LAIR_[A-Z_]+[^\n]*/g));

  // ---- the three other mutants wake in their lairs too
  cmd('kill @e[tag=jaspr_lair_minion]');
  const others = [['ossuary_spire', 'mutant_skeleton', 299], ['blast_crater', 'mutant_creeper', 299], ['void_obelisk', 'mutant_enderman', 399]], woke = [];
  for (const [design, mutant, hp] of others) {
    const L = built[design]; at = log.length;
    cmd('gamemode survival LairBot');
    cmd('tp LairBot ' + (L.boss[0] + 0.5) + ' ' + (L.boss[1] + 0.2) + ' ' + (L.boss[2] + 0.5));
    const w = await waitLog(new RegExp('LAIR_BOSS_WAKE key=\\S+ design=' + design + ' mutant=(\\S+) fallback=(\\S+) health=(\\d+)'), at, 20000, 'wake ' + design).catch(() => null);
    cmd('gamemode creative LairBot');
    woke.push(w ? [design, w[1], w[2], +w[3]] : [design, null]);
    await delay(800);
    cmd('kill @e[tag=jaspr_lair_boss]'); cmd('kill @e[tag=jaspr_lair_minion]');
    await delay(800);
  }
  check('B8 the Mutant Skeleton, Creeper and Enderman wake as themselves in their lairs, each with twice its health',
    woke.every((w, i) => w[1] === others[i][1] && w[2] === 'false' && w[3] >= others[i][2]), woke);

  // ---- C: the Director finds land on its own: untouched, generated, populated land in cell (-2, 3)
  at = log.length;
  cmd('lairs gen -65 95 -32 128');
  await waitLog(/LAIR_GEN chunks=\d+/, at, 180000, 'gen');
  cmd('tp LairBot -420 64 1800');
  const decided = await waitLog(/LAIR_DECIDED cell=-2_3 design=(\S+) at=\S+ rot=\d+ candidate=(\d+)/, at, 240000, 'natural decision').catch(e => null);
  const naturalBuilt = decided ? await waitLog(/LAIR_BUILT key=-2_3 design=\S+/, at, 120000, 'natural build').catch(e => null) : null;
  check('C1 the Director decided and built a lair in untouched land by itself', !!decided && !!naturalBuilt, { decided: decided && decided.slice(1), status: log.slice(at).match(/LAIR_[A-Z_]+[^\n]*/g) });

  // ---- D: restart, everything persisted
  A.client.end(); await delay(500);
  await stop();
  await start();
  const known = +((log.match(/LAIRS_READY version=1\.0\.0 designs=12 grid=512 lairs=(\d+)/) || [])[1] || 0);
  check('D1 after a restart the lairs are still known (the twelve, the one found, any the Director found meanwhile)', known >= 13, log.match(/[^\n]*LAIRS_READY[^\n]*/));
  at = log.length;
  cmd('lairs check');
  await waitLog(/LAIR_CHECKS n=\d+ bad=\d+/, at, 90000, 'checks after restart');
  const final = (log.slice(at).match(/LAIR_CHECK key=[^\n]*/g) || []).map(parse);
  check('D2 every lair still matches its drawing after the restart', final.length === known && final.every(c => c.mismatches === '0'), final.map(c => c.key + ':' + c.mismatches));
  check('D3 no lair exception in the whole run', !/at chat\.jaspr\.lairs|LAIRS_STEP_FAILED|LAIR_CHUNK_FAILED|LAIR_TILE_FAILED/.test(log), log.match(/[^\n]*(Exception|LAIRS_STEP_FAILED|LAIR_CHUNK_FAILED)[^\n]*/g));
  clearTimeout(watchdog);
}
main().catch(e => { console.log('ERROR ' + (e && e.stack || e)); exitCode = 1; }).finally(async () => {
  try { if (server && server.exitCode === null) await stop(); } catch (e) {}
  fs.writeFileSync(path.join(dir, 'report.json'), JSON.stringify({ at: new Date().toISOString(), jar, results }, null, 2));
  fs.writeFileSync(path.join(dir, 'paper-console.log'), log);
  const passed = results.filter(r => r.passed).length;
  console.log('LAIRS SERVER TESTS ' + passed + '/' + results.length + ' passed');
  process.exit(exitCode);
});
