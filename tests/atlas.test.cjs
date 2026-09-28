'use strict';
// JasprAtlas (Atlas, the Divided Realm): compiles the plugin with the offline check (tests/java/chat/jaspr/atlas/
// AtlasPreview.java) and runs it: every place is drawn and peopled, generation is deterministic and fast, every cell
// of the Concord and the Dominion holds something, every story spot exists (ten key figures, twelve bosses, nine
// mechanisms, twelve captives with berths, heliodromes, wards, talkers, the monument), and every book fits its pages.
// Then the plugin's wiring: lazy world, unload when empty, persistent state, the client marker the browser reads,
// the quartz gates, the Concord knowledge each Ash-Crowned needs, and the deploy's ready line.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/JasprAtlas/src/chat/jaspr/atlas');
const read = f => fs.readFileSync(path.join(root, f), 'utf8');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');

test('atlas generator: places, density, determinism, story spots, books, speed', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-atlas-test-'));
  const classes = path.join(out, 'classes'), images = path.join(out, 'preview');
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/atlas/AtlasPreview.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx1200m', '-ea', '-Djava.awt.headless=true',
    '-cp', classes + path.delimiter + cp, 'chat.jaspr.atlas.AtlasPreview', images], {encoding: 'utf8', timeout: 900000});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout.slice(-3000));
  assert.match(run.stdout, /ATLAS_OK/);
  assert.match(run.stdout, /spots keys=10 bosses=12 mechanisms=9 berths=\d+ wards=4 heliodromes=\d+ captives=12 choir=6 talkers=2 monument=true/);
  assert.match(run.stdout, /density concord cells=(\d+) filled=\1/, 'every Concord cell holds something');
  assert.match(run.stdout, /density marches cells=(\d+) filled=\1/, 'every Dominion cell holds something');
  for (const p of ['GATE_OF_STRANGERS', 'ASTREION', 'LAMPSA', 'HIERANTHE', 'MNEMEIA', 'LAST_WATCH', 'PYLON', 'STILLED_GARDEN', 'GREAT_ENGINE', 'PELLENE', 'ANTHRAKION', 'UZGAR_HIDE', 'VESK_BURROW'])
    assert.match(run.stdout, new RegExp('place ' + p + ' tiles=\\d+ people=[1-9]'), p + ' is drawn and peopled');
});

test('atlas plugin wiring: lazy world, persistence, client marker, gates, deploy ready line', () => {
  const plugin = java('AtlasPlugin');
  assert.match(plugin, /static final String WORLD = "jaspr_atlas";/);
  assert.match(plugin, /setKeepSpawnInMemory\(false\)/, 'no spawn preload');
  assert.ok(plugin.includes('Bukkit.unloadWorld(w, true)') && plugin.includes('now - emptySince < 60_000L'), 'saved and unloaded a minute after its last player leaves');
  assert.ok(plugin.includes('public void login(PlayerLoginEvent e)'), 'loaded again for a player who logged out inside it');
  assert.ok(plugin.includes('getWorldBorder().setSize(Realm.BORDER * 2)'), 'the Rim is the world border');
  assert.ok(plugin.includes('ATLAS_READY'));
  assert.match(read('scripts/deploy/DeployLogic.ps1'), /'JasprAtlas'\s+= @\('ATLAS_READY'\)/);
  const yml = read('server/custom-plugins/JasprAtlas/resources/plugin.yml');
  assert.match(yml, /main: chat\.jaspr\.atlas\.AtlasPlugin/);
  assert.match(yml, /jaspr\.atlas\.admin:[\s\S]*default: op/);
  // Shared progress only moves forward and is saved atomically; rewards are claimed once per player.
  const state = java('State');
  assert.ok(state.includes('Files.move(tmp.toPath(), f.toPath()'), 'state is written to a temp file and moved into place');
  const bosses = java('Bosses');
  assert.ok(bosses.includes('if (!rec.claimed.add("boss_" + b.id)) return;'), 'a boss reward is given once per player');
  assert.ok(bosses.includes('if (fallen(b)) return;') && bosses.includes('boolean mayRise(Boss b)'), 'a fallen boss never rises or pays again');
  // The browser marker: "JRM v1 atlas <version> <zone> <mask> <victory>", matching the client's realm loader.
  const marker = java('Marker');
  assert.ok(marker.includes('"JRM v1 atlas " + plugin.clientModuleVersion() + " " + code(z) + " " + s.liberated + " " + (s.victory ? 1 : 0)'));
  assert.ok(marker.includes('OBJECTIVE = "jrm"'));
  const loader = read('scripts/build-realm-client.cjs');
  assert.ok(loader.includes('Cbd(sb,$rt_str("jrm"))') && loader.includes('f[0]==="JRM"&&f[1]==="v1"'), 'the client reads the same marker');
  // Quartz gates: detection, kindling with both halves, vanilla Nether trips blocked, travel both ways.
  const gates = java('Gates');
  for (const k of ['FRAME = 155', 'PlayerPortalEvent', 'BlockPhysicsEvent', 'Material.INK_SACK && item.getDurability() == 4', 'Material.COAL', 'route = "home"', 'route = back.fixed ? "threshold" : "atlasGate"', 'player.isDead()', 'frameOf('])
    assert.ok(gates.includes(k), k);
});

test('atlas: the Concord knowledge each Ash-Crowned needs, always recoverable', () => {
  const mech = java('Mechanisms'), figures = java('Figures'), items = java('Items');
  assert.ok(mech.includes('case KALLIAS: return oathNear(e) ? null'), 'Kallias can only be hurt with the Oath held near him');
  assert.ok(mech.includes('case MELAINA: return count("font:") >= 3 ? null'), 'Melaina only once her three Fonts are sung quiet');
  assert.ok(mech.includes('case DAIDAROS: return count("governor:") >= 3 ? null'), 'Daidaros only once the three Governors are stilled');
  assert.ok(mech.includes('case KELEOS: return count("edict:") >= 3 ? null'), 'Keleos only once his Edict Stones are silenced');
  assert.ok(mech.includes('Items.holding(p, "hymn")') && mech.includes('Items.holding(p, "charter")') && mech.includes('Items.has(p, "counterpoint")'));
  assert.ok(mech.includes('for (int j = 0; j < k; j++) if (!s.mechanisms.contains("governor:" + j)) inOrder = false;'), 'the Governors break deep, middle, high');
  assert.ok(mech.includes('Items.holding(p, "light")') && mech.includes('f.heartBroken = true'), 'the Light breaks the Heart');
  for (const key of ['oath', 'hymn', 'counterpoint', 'charter', 'light']) {
    assert.ok(items.includes('case "' + key + '": return'), key + ' is an item');
    assert.ok(figures.includes('c.lost("' + key + '"'), key + ' can always be asked for again');
  }
});
