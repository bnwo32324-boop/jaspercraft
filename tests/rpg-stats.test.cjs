'use strict';
// JasprRPG stat sheet (owner, 2026-10-02: "More stats and ranks ... Add many more at your discretion"): 45 stats in five
// tabs, mastery ranks 11-15 worth half an ordinary level at a flat 50 levels each. Compiles the plugin with the offline
// check (tests/java/chat/jaspr/rpg/RpgCheck.java), then checks the wiring of the new effects.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/JasprRPG/src/chat/jaspr/rpg');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');

test('stat sheet: 45 stats, mastery ranks, prices, tabs, every description', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-rpg-'));
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/rpg/RpgCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx256m', '-cp', out + path.delimiter + cp, 'chat.jaspr.rpg.RpgCheck'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /RPG_OK stats=45 total15=485/);
});

test('stat sheet: the new effects are wired', () => {
  const effects = java('StatEffects'), menu = java('StatsMenu'), api = java('RpgApi'), plugin = java('RpgPlugin');
  // criticals, guns, axes, bosses
  assert.match(effects, /double chance = Math\.min\(CRIT_CHANCE_CAP, stats\.linear\(StatType\.PRECISION, config\)\);/);
  assert.match(effects, /multiplier \*= 1\.0d \+ CRIT_BASE \+ stats\.linear\(StatType\.FEROCITY, config\);/);
  assert.match(effects, /else if \(Armament\.isGun\(held\)\) multiplier = stats\.multiplier\(StatType\.GUNSLINGER, config\);/);
  assert.match(effects, /if \(bossOrElite\(event\.getEntity\(\)\)\) multiplier \*= stats\.multiplier\(StatType\.SLAYER, config\);/);
  // defense: dodges, wards, second wind; never immunity
  assert.match(effects, /public void onAttacked\(EntityDamageByEntityEvent event\)/);
  assert.match(effects, /case BLOCK_EXPLOSION: case ENTITY_EXPLOSION: reduction \+= fraction\(stats, StatType\.BLAST_WARD, config\)/);
  assert.match(effects, /reduction = Math\.min\(0\.80d, reduction\);/);
  assert.match(effects, /public void onWounded\(EntityDamageEvent event\)/);
  // standing bonuses are attribute modifiers with fixed ids
  for (const s of ['steadfast', 'fleet_foot', 'luck']) assert.ok(effects.includes('"jaspr_rpg_' + s + '"'), s);
  // gathering bonuses never pay for blocks the player put down
  assert.match(effects, /if \(wasPlaced\) \{ placedSkips\+\+; return; \}/);
  assert.match(effects, /static boolean plain\(ItemStack item\)/);
  // the menu: tabs, a holder (no title matching), drags refused
  assert.match(menu, /static final class Holder implements InventoryHolder/);
  assert.match(menu, /public void onDrag\(InventoryDragEvent event\)/);
  assert.match(menu, /RPG_STAT_UP player=/);
  // turrets read Engineering through the public API
  assert.match(api, /public static double turretMultiplier\(UUID owner\)/);
  assert.match(plugin, /RpgApi\.bind\(this\);/);
  assert.match(plugin, /RPG_METRICS/);
  // no night vision, full-bright or glowing (owner's mood rule)
  for (const name of ['StatEffects', 'StatsMenu']) assert.doesNotMatch(java(name), /NIGHT_VISION|GLOWING/, name);
});
