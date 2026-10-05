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
// the twenty great structures of epoch 6 and their keepers
const GREATS = ['NECROPOLIS', 'CATHEDRAL', 'SLEEPER', 'STAR_TOWER', 'SHOGGOTH_VATS', 'DREADNOUGHT', 'MIGO_HIVE', 'TINDALOS', 'BLACK_GOAT', 'BEACON',
  'VIADUCT', 'CELAENO', 'TERRACES', 'BASTION', 'SILVER_GATE', 'LENG', 'ELDER_VAULT', 'CISTERN', 'ORRERY', 'GOLGOTHA'];
const KEEPERS = ['GHOUL_KING', 'DROWNED_BISHOP', 'SLEEPERS_AVATAR', 'STAR_PRIEST', 'ELDER_SHOGGOTH', 'DROWNED_ADMIRAL', 'MIGO_OVERSEER', 'TINDALOS_ALPHA',
  'DARK_YOUNG', 'LAMPLIGHTER', 'TOLL_KEEPER', 'LIBRARIAN', 'DROWNED_QUEEN', 'DEEP_WARLORD', 'GATE_GUARDIAN', 'HIGH_PRIEST', 'ELDER_THING', 'CISTERN_GORGON',
  'KEEPER_OF_AEONS', 'BONE_TYRANT'];
const designClass = k => 'Great' + k.split('_').map(w => w[0] + w.slice(1).toLowerCase()).join('');

test('ruins generator: sites, old city, determinism, tiles, weathering, portal frames, speed', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-ruins-test-'));
  const classes = path.join(out, 'classes'), images = path.join(out, 'preview');
  const cp = [path.join(root, 'server/cache/patched_1.12.2.jar'), path.join(root, 'server/plugins/JasprHorrorBiomes.jar')].join(path.delimiter);
  const sources = [...javaFiles(path.join(plugins, 'JasprLostCities/src')), ...javaFiles(path.join(plugins, 'JasprRuins/src')),
    path.join(root, 'tests/java/chat/jaspr/ruins/RuinsPreview.java'), path.join(root, 'tests/java/chat/jaspr/ruins/GreatPreview.java')];
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  // Lost Cities assets are read from its resources at runtime by the plugin only; the offline check needs none.
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx768m', '-ea', '-Djava.awt.headless=true',
    '-cp', classes + path.delimiter + cp, 'chat.jaspr.ruins.RuinsPreview', images], {encoding: 'utf8'});
  // Epoch 6 (owner 2026-10-04: "make 20 new big structures there as well. They all should be unique and have bosses, and they
  // should include all types of mobs and custom mobs"): every great structure generated with the real generator and checked.
  const greats = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx900m', '-ea', '-Djava.awt.headless=true',
    '-cp', classes + path.delimiter + cp, 'chat.jaspr.ruins.GreatPreview', '-'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /RUINS_OK/);
  assert.equal(greats.status, 0, greats.stderr + greats.stdout.slice(-4000));
  assert.match(greats.stdout, /GREATS_OK/);
  assert.match(greats.stdout, /great plan .*kindsFound=20\/20/);
  for (const k of GREATS) assert.match(greats.stdout, new RegExp('great ' + k + ' .*roster=complete strangers=\[\] badPoints=0 boss=ok forbidden=\{\} deterministic=true PASS'), k);
  // Epoch 5 (owner 2026-10-04: "more dense with dungeons and structures. make new ones. 2x it"), measured in RuinsPreview
  // against epoch 4 on the same seed and square: ruins plus lesser ruins, dungeons and catacomb rooms each at least 2x.
  const density = /SITE_DENSITY grid=40 .*structures=[\d.]+ \(x([\d.]+)\) dungeons=[\d.]+ \(x([\d.]+)\) catacombRooms=[\d.+]+ \(x([\d.]+)\).* clashes=0/.exec(run.stdout);
  assert.ok(density, 'density line');
  for (const [i, what] of [[1, 'structures'], [2, 'dungeons'], [3, 'catacomb rooms']]) assert.ok(Number(density[i]) >= 2, what + ' x' + density[i]);
  for (const kind of ['TEMPLE', 'COLONNADE', 'ZIGGURAT', 'WATCHTOWER', 'AQUEDUCT', 'AMPHITHEATER', 'STONES', 'CRYPT', 'GATEHOUSE', 'COLOSSUS',
    'BELFRY', 'CLOISTER', 'NECROPOLIS', 'SCRIPTORIUM', 'UNDERCROFT', 'OUBLIETTE', 'KINGS_HALL', 'SUNKEN_TEMPLE', 'WRECK', 'LIGHTHOUSE', 'TIDE_SHRINE'])
    assert.match(run.stdout, new RegExp('site ' + kind + ' '), kind);
  assert.match(run.stdout, /deepRooms=\d+ deepHollow=\d+ descents=(\d+) ladders=\1\b/, 'every shaft reaches the deep catacombs');
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
  // The dimension lives on disk while empty and is regenerated for this design (the old world is renamed, not deleted).
  assert.match(plugin, /static final int EPOCH = 6;/);
  // The great structures (epoch 6): planned before the ruins and the field, drawn last, each a full design with its keeper
  // and garrisons of every horror and every overworld monster; dangerous inside, quiet at the gates.
  const ruinsSrc = f => read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/' + f + '.java');
  const plans = ruinsSrc('Plans'), greatsSrc = ruinsSrc('Greats'), keepers = ruinsSrc('Bosses'), garrisons = ruinsSrc('Garrisons');
  for (const [i, k] of GREATS.entries()) {
    assert.match(plans, new RegExp(k + '\("[^"]+", \d+, (true|false), "' + KEEPERS[i] + '"\)'), k + ' keeps ' + KEEPERS[i]);
    assert.ok(greatsSrc.includes('= new ' + designClass(k) + '();'), designClass(k) + ' registered');
    assert.match(ruinsSrc(designClass(k)), new RegExp('final class ' + designClass(k) + ' extends GreatDesign'), designClass(k) + ' is a full design');
    assert.ok(keepers.includes(KEEPERS[i] + '(EntityType'), KEEPERS[i]);
  }
  assert.ok(gen.includes('if (g != null) Greats.draw(g, c);'), 'the great structures are drawn last');
  assert.ok(plans.includes('if (greatNear(x, z, r + SITE_PAD + 3)) return null;'), 'the ruins keep clear of them');
  assert.ok(ruinsSrc('Danger').includes('return "great";'), 'inside one is inside a structure');
  assert.ok(keepers.includes('RUINS_KEEPER_SLAIN') && keepers.includes('KEEPER_COOLDOWN = 30L * 60_000L'), 'keepers rest half an hour');
  assert.ok(garrisons.includes('plugin.danger().sanctuary(at)') && garrisons.includes('e.blockList().clear()') && garrisons.includes('RUINS_GARRISON_ROUSED'),
    'garrisons: never at the gates, never blasting the stone, logged');
  assert.doesNotMatch(garrisons, /getName\(\)|getAddress\(\)/, 'no player names in the garrison log');
  const roster = ruinsSrc('GreatDesign');
  for (const k of ['deep_one', 'ghoul', 'cult_zealot', 'tomb_crawler', 'nightgaunt', 'shoggoth', 'mi_go', 'star_spawn', 'hound', 'cult_adept',
    'zombie', 'skeleton', 'spider', 'creeper', 'witch', 'enderman', 'slime', 'evoker', 'illusioner', 'wither_skeleton']) assert.ok(roster.includes('"' + k + '"'), 'roster ' + k);
  assert.ok(plugin.includes('plans().forget();'), 'plans made before the Lost Cities attached are forgotten once the world is open');
  assert.ok(plugin.includes('Bukkit.unloadWorld(w, true)'), 'saved and unloaded after its last player leaves');
  assert.ok(plugin.includes('public void login(PlayerLoginEvent e)'), 'loaded for a player who logged out inside it');
  assert.ok(plugin.includes('folder.renameTo(retired)'), 'an older world is renamed aside, never deleted');
  assert.ok(read('server/custom-plugins/JasprLostCities/src/chat/jaspr/lostcities/LostCitiesPlugin.java').includes('worldUnload(WorldUnloadEvent e)'), 'Lost Cities re-attach after a reload');
  // Beating it: the guide kit on arrival (compass, checklist, map), five Wardens with Seals, three Seals open the Door, the Herald's hoard.
  assert.ok(plugin.includes('guide = new GuideKit(this, quest);') && plugin.includes('case "primer": give(player, Lore.guide(plans().door().x, plans().door().z))'), 'the kit on arrival; the Primer on request');
  const bosses = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Bosses.java');
  for (const b of ['HIEROPHANT', 'PILLAR_WARDEN', 'BROOD_MOTHER', 'SPAWN_OF_THE_DEEP', 'FACELESS_PRIEST', 'HERALD']) assert.ok(bosses.includes(b + '(EntityType'), b);
  assert.match(bosses, /SEALS_NEEDED = 3/);
  assert.match(read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Lore.java'), /STEP 5: SLAY THE HERALD/);
  const horrors = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Horrors.java');
  for (const k of ['DEEP_ONE', 'GHOUL', 'SHOGGOTH', 'NIGHTGAUNT', 'MI_GO', 'HOUND', 'STAR_SPAWN', 'CULT_ZEALOT', 'CULT_ADEPT', 'TOMB_CRAWLER']) assert.ok(horrors.includes(k + '(EntityType'), k);
  // More dangers, and an eerie sky the patched client paints only while the server says so.
  for (const k of ['void elder(', 'public void ambush(', 'private void crumble(', 'Something answers your fear.']) assert.ok(horrors.includes(k), k);
  const sky = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Sky.java');
  assert.ok(sky.includes('JRS v1 eerie'), 'sky objective name');
  assert.ok(plugin.includes('sky::tick'), 'sky task scheduled');
  for (const d of ['FORTRESS', 'LABYRINTH', 'OSSUARY', 'DEEP_TEMPLE', 'OBSERVATORY', 'GREAT_IDOL']) assert.ok(read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Plans.java').includes(d + '('), d);
  assert.match(read('server/custom-plugins/JasprDaylight/src/chat/jaspr/daylight/DaylightPlugin.java'), /jaspr_daylight_exempt/, 'horrors keep full speed in overworld daytime');
  assert.doesNotMatch(read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/RuinsPopulator.java'), /generateTree/, 'nothing grows');
  const yml = read('server/custom-plugins/JasprRuins/resources/plugin.yml');
  assert.match(yml, /depend: \[JasprLostCities, JasprHorrorBiomes\]/);
  assert.match(yml, /jaspr\.ruins\.admin:[\s\S]*default: op/);
});

// Owner, 2026-09-29: "make the spawn point, when you go through the portal, virtually safe, and as you venture out, it
// gets more and more dangerous. Most of the dangers should come from dungeons and not from random spawns ... Cap the
// spawn rate even more ... Spawn should be relatively peaceful." (the ramp's numbers are checked in RuinsPreview)
test('Drownhollow danger: safe gates, danger growing outward, most of it inside structures', () => {
  const read = f => fs.readFileSync(path.join(root, 'server/custom-plugins/JasprRuins/src/chat/jaspr/ruins', f), 'utf8');
  const danger = read('Danger.java'), horrors = read('Horrors.java'), plugin = read('RuinsPlugin.java'), portals = read('Portals.java');
  assert.match(danger, /static final int SAFE = 48, FULL = 448, WILD_DREAD = 54, WILD_RANGE = 96;/);
  assert.match(portals, /double gateDistance\(String world, double x, double z\)/, 'the sanctuary is measured from every lit gate');
  // spawns are weighed before a creature exists (Paper's pre-spawn event); none at all around a player at a gate
  assert.match(horrors, /public void preSpawn\(PreCreatureSpawnEvent e\)/);
  assert.match(horrors, /public void calmAround\(PlayerNaturallySpawnCreaturesEvent e\)/);
  assert.match(horrors, /if \(hostile && UNBIDDEN\.contains\(reason\) && plugin\.danger\(\)\.sanctuary\(at\)\)/, 'nothing hostile appears on its own at a gate');
  // strays are driven off, nothing hunts or hurts a player at a gate
  assert.match(horrors, /if \(danger\.sanctuary\(e\.getLocation\(\)\)\) \{ banish\(e\); continue; \}/);
  assert.match(horrors, /public void calm\(EntityTargetEvent e\)/);
  assert.match(horrors, /public void sheltered\(EntityDamageByEntityEvent e\)/);
  // open ground: a few wanderers far out; structures: the danger, by the ramp
  assert.match(danger, /if \(allowed == 0 \|\| horrorsNear\(at, WILD_RANGE, 48\) >= allowed\) \{ refusedWild\+\+; return STOP; \}/);
  assert.match(danger, /if \(random\.nextDouble\(\) >= strength\(d\) \|\| horrorsNear\(at, 24, 12\) >= crowdCap\(d\)\)/);
  assert.match(danger, /if \(random\.nextDouble\(\) >= RuinsPlugin\.EASE \* strength\(d\)\) \{ refusedCage\+\+; return SKIP; \}/);
  assert.match(horrors, /double elders = 0\.12 \* RuinsPlugin\.EASE \* plugin\.danger\(\)\.level\(at\);/, 'no Elders near the gates');
  // the Dread: calm at a gate, only a whisper on open ground, the full terror (and the shadows) under roofs and underground
  assert.match(horrors, /int cap = safe \? 0 : Danger\.dreadCap\(danger\.place\(w, l\.getBlockX\(\), l\.getBlockY\(\), l\.getBlockZ\(\)\), Danger\.ramp\(gate\)\);/);
  assert.match(horrors, /else if \(safe \|\| lit\(p\)\) d = Math\.max\(0, d - 4\);/);
  assert.match(horrors, /if \(deep && d >= 90 /);
  // falling masonry and chest ambushes only inside, never at a gate
  assert.match(horrors, /if \(danger\.sanctuary\(at\) \|\| !danger\.inside\(w, at\.getBlockX\(\), at\.getBlockY\(\), at\.getBlockZ\(\)\)\) continue;/);
  assert.match(horrors, /if \(plugin\.danger\(\)\.sanctuary\(where\) \|\| random\.nextDouble\(\) >= 0\.3 \* RuinsPlugin\.EASE \* Danger\.strength/);
  // fewer spawns, and the logs say so
  assert.match(plugin, /static final int MONSTER_CAP = 20, SPAWN_TICKS = 20;/);
  assert.match(plugin, /w\.setMonsterSpawnLimit\(MONSTER_CAP\);/);
  assert.match(plugin, /w\.setTicksPerMonsterSpawns\(SPAWN_TICKS\);/);
  assert.match(plugin, /" sanctuary=" \+ Danger\.SAFE \+ " fullDanger=" \+ Danger\.FULL/);
  assert.match(plugin, /\(danger == null \? "" : " " \+ danger\.describe\(\)\)/, 'RUINS_METRICS counts refusals and banishments');
  assert.match(plugin, /RUINS_DANGER player=/);
  // the guides tell it the same way
  assert.match(read('Lore.java'), /The gates are safe\. Danger grows the farther you go/);
  assert.match(read('RuinsQuest.java'), /The gates are safe\. Danger grows the farther you go/);
});
