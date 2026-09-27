'use strict';
// JasprRuins (the Ancient Ruins dimension): compiles the Lost Cities and Ruins sources against the patched Paper jar and
// HorrorBiomes, then runs the offline generator check (tests/java/chat/jaspr/ruins/RuinsPreview.java): every site kind
// and an old city appear, generation is deterministic, the populator's recomputed chests/spawners match the blocks,
// the Lost Cities weathering hook works, mossy portal frames are detected, and chunks generate quickly.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const plugins = path.join(root, 'server/custom-plugins');
const javaFiles = dir => fs.readdirSync(dir, {recursive: true}).filter(f => f.endsWith('.java')).map(f => path.join(dir, f));

test('ruins generator: sites, old city, determinism, tiles, weathering, portal frames, speed', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-ruins-test-'));
  const classes = path.join(out, 'classes'), images = path.join(out, 'preview');
  const cp = [path.join(root, 'server/cache/patched_1.12.2.jar'), path.join(root, 'server/plugins/JasprHorrorBiomes.jar')].join(path.delimiter);
  const sources = [...javaFiles(path.join(plugins, 'JasprLostCities/src')), ...javaFiles(path.join(plugins, 'JasprRuins/src')),
    path.join(root, 'tests/java/chat/jaspr/ruins/RuinsPreview.java')];
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  // Lost Cities assets are read from its resources at runtime by the plugin only; the offline check needs none.
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx768m', '-ea', '-Djava.awt.headless=true',
    '-cp', classes + path.delimiter + cp, 'chat.jaspr.ruins.RuinsPreview', images], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /RUINS_OK/);
  for (const kind of ['TEMPLE', 'COLONNADE', 'ZIGGURAT', 'WATCHTOWER', 'AQUEDUCT', 'AMPHITHEATER', 'STONES', 'CRYPT', 'GATEHOUSE', 'COLOSSUS'])
    assert.match(run.stdout, new RegExp('site ' + kind + ' '), kind);
});

test('ruins plugin wiring: Lost Cities registration, mossy portals, client-green biomes, deploy ready token', () => {
  const read = f => fs.readFileSync(path.join(root, f), 'utf8');
  const plugin = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/RuinsPlugin.java');
  assert.match(plugin, /CityApi\.registerWorld\(WORLD, "The Ruins of %s", new Weathering\(seed\)\)/, 'Lost Cities build in the ruins, restyled');
  assert.match(plugin, /setKeepSpawnInMemory\(false\)/, 'no spawn preload');
  assert.match(plugin, /RUINS_READY/);
  const portals = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Portals.java');
  assert.match(portals, /FRAME = 48/, 'portal frames are mossy cobblestone');
  assert.match(portals, /BlockPhysicsEvent/, 'portal blocks are shielded from vanilla frame checks');
  assert.match(portals, /PlayerPortalEvent/, 'no vanilla Nether trips through mossy portals');
  const gen = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/RuinsGenerator.java');
  assert.doesNotMatch(gen, /Biome\.(SWAMPLAND|PLAINS|JUNGLE_EDGE|FOREST)\b/, 'slots the client restyles dry or snowy are avoided');
  const api = read('server/custom-plugins/JasprLostCities/src/chat/jaspr/lostcities/CityApi.java');
  assert.match(api, /public static void registerWorld\(String worldName, String titleFormat, PrimerHook hook\)/);
  assert.match(read('scripts/deploy/DeployLogic.ps1'), /'JasprRuins'\s+= @\('RUINS_READY'\)/);
  const yml = read('server/custom-plugins/JasprRuins/resources/plugin.yml');
  assert.match(yml, /depend: \[JasprLostCities, JasprHorrorBiomes\]/);
  assert.match(yml, /jaspr\.ruins\.admin:[\s\S]*default: op/);
});
