'use strict';
// The Backrooms (JasprBackrooms, owner 2026-09-30): every level generates from the real generator and a player can walk
// from its entry to its arena, exit and boss (LayoutProbe); the zones, triggers, difficulty curve, monsters and gate
// frames behave (BackroomsLogicTest); the plugin compiles against the patched Paper jar and the shipped jar carries it;
// the rules the owner asked for are in the source; the browser client colours the gate yellow.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const paper = path.join(root, 'server/cache/patched_1.12.2.jar');
const src = path.join(root, 'server/custom-plugins/JasprBackrooms/src');
const read = f => fs.readFileSync(path.join(root, f), 'utf8');
const sources = () => fs.readdirSync(src, {recursive: true}).filter(f => f.endsWith('.java')).map(f => path.join(src, f));

function java(extra, main, args) {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-backrooms-test-'));
  const javac = spawnSync(path.join(jdk, 'javac'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-Xlint:-options', '-proc:none', '-cp', paper, '-d', out, ...sources(), ...extra], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java'), ['-Xmx3g', '-cp', out + path.delimiter + paper, main, ...args], {encoding: 'utf8', cwd: root, timeout: 600000});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  return run.stdout;
}

const haveJava = fs.existsSync(jdk) && fs.existsSync(paper);

test('every level generates, is walkable from its entry to its arena, exit and boss (no water can flow), and is lit', {skip: !haveJava, timeout: 600000}, () => {
  const out = java([path.join(root, 'tests/java/chat/jaspr/backrooms/LayoutProbe.java')], 'chat.jaspr.backrooms.LayoutProbe', ['20260930']);
  assert.match(out, /BACKROOMS_LAYOUT_OK/);
  // The probe spreads block light as the game does and fails a level that is dark where it should be bright.
  for (const lv of ['YELLOW', 'WAREHOUSE', 'TUNNELS', 'ELECTRICAL', 'OFFICE', 'CITY', 'POOLS']) assert.match(out, new RegExp(lv + ' chests=\\d+ .* light arrival=[\\d.]+ start=[\\d.]+ .* ok'), lv);
});

test('zones, triggers, the difficulty curve, monsters and gate frames', {skip: !haveJava, timeout: 300000}, () => {
  assert.match(java([path.join(root, 'tests/java/chat/jaspr/backrooms/BackroomsLogicTest.java')], 'chat.jaspr.backrooms.BackroomsLogicTest', []), /BACKROOMS_LOGIC_OK/);
});

test('the shipped jar carries the plugin', {skip: !haveJava}, () => {
  const list = spawnSync(path.join(jdk, 'jar'), ['tf', path.join(root, 'server/plugins/JasprBackrooms.jar')], {encoding: 'utf8'});
  assert.equal(list.status, 0);
  for (const f of ['chat/jaspr/backrooms/BackroomsPlugin.class', 'chat/jaspr/backrooms/GuideKit.class', 'chat/jaspr/backrooms/Pools.class', 'plugin.yml', 'config.yml']) assert.ok(list.stdout.includes(f), f);
});

test("the owner's rules are in the source", () => {
  const P = 'server/custom-plugins/JasprBackrooms/src/chat/jaspr/backrooms/';
  const protect = read(P + 'Protect.java'), conquest = read(P + 'Conquest.java'), portals = read(P + 'Portals.java'), mobs = read(P + 'Mobs.java'), items = read(P + 'Items.java');
  // Unbreakable: only placed blocks, chests and cobwebs; explosions, fire, pistons, water and mobs change nothing.
  for (const k of ['public void onBreak(BlockBreakEvent e)', 'BREAKABLE = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.WEB)', 'blocks.removeIf(b -> !placed(b))',
    'public void onFlow(BlockFromToEvent e)', 'public void onChange(EntityChangeBlockEvent e)', 'public void onPistonExtend', 'MAX_PLACED']) assert.ok(protect.includes(k), k);
  // Access: a conqueror of Atlas, Drownhollow, the Nether or the End (the dragon, now or before) lights and enters.
  for (const k of ['"jr_beat_atlas", "jr_beat_ruins", "jr_beat_nether"', 'NamespacedKey.minecraft("end/kill_dragon")', 'public void onDragon(EntityDeathEvent e)']) assert.ok(conquest.includes(k), k);
  for (const k of ['Material.YELLOW_GLAZED_TERRACOTTA', 'if (!plugin.conquest().conqueror(p)) {', 'private void goIn(Player p, Gate g)']) assert.ok(portals.includes(k), k);
  // Lit before it is seen: the Backrooms are lit by blocks, and a chunk sent before its light is worked out stays dark.
  assert.ok(read(P + 'BackroomsPlugin.java').includes('spigotConfig.randomLightUpdates = true'), 'chunks wait for their light');
  // A peaceful spawn; harder with every level and further in.
  assert.match(mobs, /static int sanctuary\(Level lv\) \{ return lv\.number == 1 \? 64 : 24; \}/);
  assert.match(mobs, /static double healthScale\(double g\) \{ return 0\.7 \+ 1\.1 \* g; \}/);
  // Seven sets, weapons, tools and trinkets.
  assert.equal((items.match(/\bset\(Level\.[A-Z]+, new String\[\]/g) || []).length, 7);
  for (const lv of ['YELLOW', 'WAREHOUSE', 'TUNNELS', 'ELECTRICAL', 'OFFICE', 'CITY', 'POOLS']) {
    assert.match(items, new RegExp('Level\\.' + lv + ', Kind\\.WEAPON'), lv + ' weapon');
    assert.match(items, new RegExp('Level\\.' + lv + ', Kind\\.TOOL'), lv + ' tool');
    assert.equal((items.match(new RegExp('Level\\.' + lv + ', Kind\\.TRINKET', 'g')) || []).length >= 2, true, lv + ' trinkets');
  }
});

test('the browser client colours the Backrooms gate yellow', () => {
  const client = fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
  assert.match(client, /var FRAMES=\{48:\d+,239:(\d+)\}/);
  assert.equal(Number(/var FRAMES=\{48:\d+,239:(\d+)\}/.exec(client)[1]), 0xffdb4d);
});
