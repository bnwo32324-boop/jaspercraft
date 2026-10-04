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
  // Epoch 4 (owner 2026-10-04): Drownhollow structures 2x as common. 0.83 per 56-block cell against epoch 3's 0.85 per 80.
  const plans = fs.readFileSync(path.join(plugins, 'JasprRuins/src/chat/jaspr/ruins/Plans.java'), 'utf8');
  const grid = Number(/SITE_GRID = (\d+)/.exec(plans)[1]), chance = Number(/SITE_CHANCE = ([\d.]+)/.exec(plans)[1]);
  const ratio = (chance / (grid * grid)) / (0.85 / (80 * 80));
  assert.ok(ratio > 1.95 && ratio < 2.05, 'site candidates per area about 2x epoch 3: ' + ratio);
  assert.match(run.stdout, /SITE_DENSITY grid=56 /);
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
  // The dimension lives on disk while empty and is regenerated for this design (the old world is renamed, not deleted).
  assert.match(plugin, /static final int EPOCH = 4;/);
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
