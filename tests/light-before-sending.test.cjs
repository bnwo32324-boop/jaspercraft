'use strict';
// Chunk light before sending (LIGHT_UPDATE.md, 2026-09-30). This Paper runs spigot.yml random-light-updates: false, so a new
// chunk is sent before its block light is worked out. The worlds that are lit by blocks (the Nether, Atlas, Drownhollow, the
// Backrooms) switch it on for their own world only, from the plugin that owns the world, at world init (before the first chunk
// is prepared) and whenever the plugin loads or creates the world; spigot.yml itself stays as it is, and the overworld, which
// was measured and arrives lit, is left alone. Each plugin logs one <NAME>_LIGHT line per world object so a live log shows it.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const haveJavap = fs.existsSync(path.join(jdk, 'javap.exe')) || fs.existsSync(path.join(jdk, 'javap'));
const read = f => fs.readFileSync(path.join(root, f), 'utf8');

// plugin, its main class, where the world comes to life (the methods that must switch the flag on), the log token
const WORLDS = [
  {plugin: 'JasprNether', cls: 'chat.jaspr.nether.NetherPlugin', file: 'chat/jaspr/nether/NetherPlugin.java', log: 'NETHER_LIGHT', world: 'world_nether',
    hooks: ['private void attach(World w)'], call: 'lightBeforeSending(w);'},
  {plugin: 'JasprAtlas', cls: 'chat.jaspr.atlas.AtlasPlugin', file: 'chat/jaspr/atlas/AtlasPlugin.java', log: 'ATLAS_LIGHT', world: 'jaspr_atlas',
    hooks: ['public void worldInit(WorldInitEvent e)', 'synchronized World ensureAtlas()'], call: 'lightBeforeSending('},
  {plugin: 'JasprRuins', cls: 'chat.jaspr.ruins.RuinsPlugin', file: 'chat/jaspr/ruins/RuinsPlugin.java', log: 'RUINS_LIGHT', world: 'jaspr_ruins',
    hooks: ['public void worldInit(WorldInitEvent e)', 'synchronized World ensureRuins()'], call: 'lightBeforeSending('},
  {plugin: 'JasprBackrooms', cls: 'chat.jaspr.backrooms.BackroomsPlugin', file: 'chat/jaspr/backrooms/BackroomsPlugin.java', log: 'BACKROOMS_LIGHT', world: 'jaspr_levels',
    hooks: ['synchronized World ensureWorld()'], call: 'lightBeforeSending(w);'},
];
const srcOf = w => read('server/custom-plugins/' + w.plugin + '/src/' + w.file);

/** The text of the method whose declaration contains `signature` (from its opening brace to the matching one). */
function bodyOf(src, signature) {
  const at = src.indexOf(signature);
  assert.ok(at >= 0, 'method not found: ' + signature);
  const open = src.indexOf('{', at);
  let depth = 0;
  for (let i = open; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}' && --depth === 0) return src.slice(open, i + 1);
  }
  throw new Error('unbalanced braces after ' + signature);
}

test('each world lit by blocks switches the flag on for its own world, where the world comes to life', () => {
  for (const w of WORLDS) {
    const src = srcOf(w);
    assert.ok(src.includes('getHandle().spigotConfig.randomLightUpdates = true;'), w.plugin + ' sets the flag');
    assert.ok(src.includes('private void lightBeforeSending(World w)'), w.plugin + ' has the helper');
    for (const hook of w.hooks) assert.ok(bodyOf(src, hook).includes(w.call), w.plugin + ': ' + hook + ' switches it on');
    // a diagnostic line per world object, and the fallback when the server internals are not there
    assert.ok(src.includes(w.log + ' mode='), w.plugin + ' logs ' + w.log);
    assert.ok(src.includes('mode=unavailable'), w.plugin + ' says so when it cannot switch the flag on');
  }
});

test('the flag is per world: nothing here switches it off again or writes the global setting', () => {
  for (const w of WORLDS) assert.ok(!/randomLightUpdates\s*=\s*false/.test(srcOf(w)), w.plugin + ' never switches it off');
  const tuned = read('scripts/perf-server-config.cjs');
  assert.ok(!tuned.includes('random-light-updates'), 'spigot.yml keeps the server default (random-light-updates: false)');
  for (const f of ['server/custom-plugins/JasprPerfTweaks/src/chat/jaspr/perf/PerfTweaks.java', 'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/TerrainLighting.java'])
    assert.ok(!read(f).includes('randomLightUpdates'), f + ': the overworld (measured, it arrives lit) is left as it is');
});

test('the shipped jars switch the flag on', {skip: !haveJavap}, () => {
  for (const w of WORLDS) {
    const jar = path.join(root, 'server/plugins/' + w.plugin + '.jar');
    const out = spawnSync(path.join(jdk, 'javap'), ['-c', '-p', '-classpath', jar, w.cls], {encoding: 'utf8', maxBuffer: 1 << 26});
    assert.equal(out.status, 0, out.stderr);
    assert.match(out.stdout, /putfield\s+#\d+\s+\/\/ Field org\/spigotmc\/SpigotWorldConfig\.randomLightUpdates:Z/, w.plugin + '.jar writes the flag');
    assert.ok(out.stdout.includes('mode=unavailable'), w.plugin + '.jar carries the fallback');
  }
});

// At least the versions this change shipped in (later releases carry it too).
const atLeast = (text, re, min) => {
  const m = re.exec(text);
  assert.ok(m, 'version found: ' + re);
  const a = m[1].split('.').map(Number), b = min.split('.').map(Number);
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return assert.ok(a[i] > b[i], m[1] + ' >= ' + min);
};

test('the plugin versions say this change is in', () => {
  atLeast(read('server/custom-plugins/JasprNether/resources/plugin.yml'), /^version: (\d+\.\d+\.\d+)$/m, '1.2.1');
  atLeast(read('server/custom-plugins/JasprAtlas/resources/plugin.yml'), /^version: (\d+\.\d+\.\d+)$/m, '1.0.1');
  atLeast(read('server/custom-plugins/JasprRuins/resources/plugin.yml'), /^version: (\d+\.\d+\.\d+)$/m, '1.1.2');
  atLeast(read('server/custom-plugins/JasprNether/src/chat/jaspr/nether/NetherPlugin.java'), /static final String VERSION = "(\d+\.\d+\.\d+)";/, '1.2.1');
});
