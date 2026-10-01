'use strict';
// JasprNether's colossal structures and the Endless Catacombs (owner, 2026-10-01: "Add three new mega structures to the
// Nether. Each one should have a boss. One should be a giant pyramid. One should be a giant fire fortress inspired by
// Avatar: The Last Airbender. One should be an ancient, sprawling dungeon that goes on for miles ... Add loot, enemies,
// different dangers, puzzles, and traps", then "don't forget about loot and mini-bosses"). Compiles the plugin with the
// offline check (tests/java/chat/jaspr/nether/ColossusPreview.java) and runs it: the Great Pyramid and the Caldera
// Citadel drawn chunk by chunk on synthetic Nether terrain (deterministic, no forbidden or valuable block, every boss's
// arena, stocked, cheap), windows of the Catacombs likewise (and no lava left touching the halls), and the whole
// labyrinth's map walked from the Heart (the four Wardens' doors reached, nearly all of it connected). Then the wiring.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/JasprNether/src/chat/jaspr/nether');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');
const NEW = ['History', 'Colossi', 'ColossusDesign', 'ColossusPyramid', 'ColossusCitadel', 'Depths', 'Ordeals', 'Dwellers', 'Spoils'];
const LORDS = ['sunless_pharaoh', 'ember_sovereign', 'hollow_king'];
const CHAMPIONS = {sphinx_sentinel: 'canopic_jar_duamutef', vizier_hekkat: 'canopic_jar_imsety', scarab_matriarch: 'canopic_jar_qebehsenuef',
  high_fire_sage: 'sun_seal', blazing_admiral: 'admiral_seal', boiling_warden: 'warden_seal',
  gaoler: 'warden_key_gaol', bone_harrower: 'warden_key_ossuary', weeping_shade: 'warden_key_gallery', rot_mother: 'warden_key_pits'};
const SETS = ['hellforged', 'soulweave', 'wither_bone', 'orange_salamander_hide', 'black_salamander_hide', 'pharaoh', 'ember_guard', 'deepwarden'];

test('colossal structures and the Catacombs: per chunk, deterministic, no valuables, every boss, stocked, connected, cheap', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-nether-colossi-'));
  const classes = path.join(out, 'classes');
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  for (const f of ['ColossusPreview', 'MegaPreview']) sources.push(path.join(root, 'tests/java/chat/jaspr/nether', f + '.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx1200m', '-Djava.awt.headless=true', '-cp', classes + path.delimiter + cp,
    'chat.jaspr.nether.ColossusPreview', '-', path.join(root, 'server/custom-plugins/JasprNether/resources/blocks.tsv')], {encoding: 'utf8', timeout: 900000, cwd: out});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout.slice(-4000));
  assert.match(run.stdout, /COLOSSI_OK/);
  for (const id of ['great_pyramid', 'caldera_citadel']) assert.match(run.stdout, new RegExp('colossus ' + id + ' .*deterministic=true forbidden=\\{\\} .*PASS'), id);
  for (const lord of ['sunless_pharaoh', 'sphinx_sentinel', 'vizier_hekkat', 'scarab_matriarch']) assert.match(run.stdout, new RegExp('colossus great_pyramid .*lord:' + lord + '=1'), lord);
  for (const lord of ['ember_sovereign', 'high_fire_sage', 'blazing_admiral', 'boiling_warden']) assert.match(run.stdout, new RegExp('colossus caldera_citadel .*lord:' + lord + '=1'), lord);
  assert.match(run.stdout, /colossus great_pyramid .*ordeals=\{[^}]*keyseal=1[^}]*levers=1/, 'the Canopic Seal and the Hall of Stars');
  assert.match(run.stdout, /colossus caldera_citadel .*ordeals=\{[^}]*braziers=1[^}]*keyseal=1/, 'the Sun Temple rite and the Throne Gate');
  for (const w of ['heart', 'vault', 'maze']) assert.match(run.stdout, new RegExp('depths ' + w + ' .*deterministic=true forbidden=\\{\\} lavaTouching=0 .*PASS'), w);
  assert.match(run.stdout, /depths heart .*lord:hollow_king=1/);
  assert.match(run.stdout, /depths vault .*lord:gaoler=1/);
  // miles of it: 3,073 blocks square, the four vaults reached from the Heart, nearly all of it one labyrinth
  const map = run.stdout.match(/depths map size=(\d+)x\d+ open=(\d+) reached=([\d.]+)% vaults=true .*PASS/);
  assert.ok(map, 'the labyrinth map');
  assert.ok(Number(map[1]) >= 3000 && Number(map[3]) > 90, map[0]);
});

test('colossal wiring: placement, history, bosses, champions, keys, creatures, loot, ordeals, safety', () => {
  const gen = java('Gen'), plugin = java('NetherPlugin'), lords = java('Lords'), items = java('Items'), loot = java('Loot'), mobs = java('Mobs');
  const ordeals = java('Ordeals'), dwellers = java('Dwellers'), depths = java('Depths'), life = java('GlmLife');
  const designs = ['ColossusPyramid', 'ColossusCitadel', 'Depths'].map(java).join('\n');
  // placement: a colossus only on land newer than this update and clear of every recorded structure, decided once;
  // a mega site or a GLM build gives way to a built colossus; the Nether is not regenerated for it
  assert.match(gen, /on = !history\.anyOld\(s\.minX, s\.minZ, s\.maxX, s\.maxZ\) && !registry\.structureReach\(s\.minX, s\.minZ, s\.maxX, s\.maxZ\);/);
  assert.match(gen, /registry\.setColossusDecision\(s\.cellX, s\.cellZ, on\);/);
  assert.equal((gen.match(/if \(on && colossusClaims\(s\.minX, s\.minZ, s\.maxX, s\.maxZ\)\) on = false;/g) || []).length, 2, 'mega and GLM give way');
  assert.match(plugin, /static final int REGEN_EPOCH = 4;/);
  // the history is taken once and kept with the world's records; the Heart's place is chosen once and kept
  assert.match(java('History'), /"colossi-history\.txt"/);
  assert.match(plugin, /gen\.history = History\.of\(w, data, getLogger\(\)\);/);
  assert.match(plugin, /new File\(data, "catacombs\.txt"\)/);
  assert.match(depths, /if \(!sectorOk\(c\.i \* P \+ 6, c\.j \* P \+ 6\)\) continue;/, 'the Catacombs keep out of old land');
  // the vertical partition: the colossi above y 23, the Catacombs at y 22 and below; stairwells never climb into a structure
  assert.match(depths, /FLOOR = 9, TOP = 22/);
  assert.match(gen, /boolean shaftClaim\(int x0, int z0, int x1, int z1\)/);
  assert.match(depths, /claims\.above\(cx - 6, cz - 6, cx \+ 6, cz \+ 6\)/);
  // the bosses: three Lords (they count towards the Urn of Sorrow) with relics and sigils, ten champions with keys
  const known = new Set([...items.matchAll(/new Def\("([a-z_]+)"/g)].map(m => m[1]));
  for (const set of SETS) for (const slot of ['helmet', 'chestplate', 'leggings', 'boots']) known.add(set + '_' + slot);
  for (const h of ['withered', 'blazed', 'frosted']) for (const t of ['sword', 'pickaxe', 'shovel', 'axe', 'hoe', 'hammer']) known.add(h + '_amedian_' + t);
  for (const lord of LORDS) {
    const m = lords.match(new RegExp('def\\(new Def\\("' + lord + '"[^;]*?BarColor\\.[A-Z]+, "([a-z_]+)"\\)'));
    assert.ok(m, lord);
    assert.ok(known.has(m[1]), lord + ' relic ' + m[1]);
    assert.ok(known.has('sigil_' + lord), lord + ' sigil');
  }
  for (const [champion, key] of Object.entries(CHAMPIONS)) {
    assert.match(lords, new RegExp('def\\(new Def\\("' + champion + '"[^;]*\\.champion\\("' + key + '"\\)'), champion);
    assert.ok(known.has(key), key);
    assert.ok(designs.includes('"lord:' + champion + '"') || depths.match(/WARDENS = \{[^}]*\}/)[0].includes('"' + champion + '"'), champion + ' has an arena');
  }
  assert.match(lords, /static \{ for \(Def d : DEFS\.values\(\)\) if \(!d\.champion\) COUNTED\.add\(d\.id\); \}/);
  assert.match(lords, /if \(d\.champion\) keys\(t\.e, d, f\); else credit\(t\.e, d, f\);/);
  assert.match(lords, /plugin\.ordeals\.bossFell\(d\.id, t\.e\.getWorld\(\), hx, hz\);/, 'a boss\'s fall opens its treasury');
  assert.match(lords, /if \(d\.sight && !sees\(p\.getEyeLocation\(\), e\.x1 \+ 0\.5, e\.y1 \+ 1\.5, e\.z1 \+ 0\.5\)\) continue;/, 'no boss wakes behind a wall');
  // the seals' keys: the Canopic Jars, the Throne's seals, the Wardens' keys are all named by the designs and exist
  for (const m of designs.matchAll(/(JARS|SEALS|KEYS) = \{([^}]*)\}/g)) for (const id of m[2].match(/"([a-z_]+)"/g).map(s => s.slice(1, -1))) assert.ok(known.has(id), m[1] + ' ' + id);
  for (const m of designs.matchAll(/"(canopic_jar_hapy|hollow_reliquary)"/g)) assert.ok(known.has(m[1]), 'puzzle reward ' + m[1]);
  // the creatures: ten kinds wired into Mobs; every garrison, spawner and minion kind exists
  const kinds = [...dwellers.matchAll(/new Mobs\.Spec\("([a-z_]+)"/g)].map(m => m[1]);
  assert.equal(kinds.length, 10);
  assert.match(mobs, /Dwellers\.specs\(Mobs::spec\);/);
  assert.match(mobs, /default: if \(!Fiends\.think\(this, t\)\) Dwellers\.think\(this, t\);/);
  const all = new Set([...kinds, ...[...java('Fiends').matchAll(/new Mobs\.Spec\("([a-z_]+)"/g)].map(m => m[1])]);
  for (const m of designs.matchAll(/"garrison:([a-z_+]+)"/g)) for (const k of m[1].split('+')) assert.ok(all.has(k), 'garrison kind ' + k);
  for (const m of designs.matchAll(/spawner\([^;]*?"([a-z_]+)"\)/g)) assert.ok(all.has(m[1]), 'spawner kind ' + m[1]);
  for (const m of designs.matchAll(/q < [0-9.]+ \? "([a-z_]+)" : (?:q < [0-9.]+ \? "([a-z_]+)" : )?"([a-z_]+)"/g)) for (const k of m.slice(1).filter(Boolean)) assert.ok(all.has(k), 'spawner mob ' + k);
  for (const m of lords.matchAll(/(?:true|false), "([a-z_]+)", BarColor/g)) assert.ok(all.has(m[1]), 'minion ' + m[1]);
  for (const name of ['GlmLife']) for (const k of life.match(/ROSTER = \{[^;]*;/g).join(' ').match(/"([a-z_]+)"/g).map(s => s.slice(1, -1))) assert.ok(all.has(k), name + ' roster ' + k);
  // loot: every table the designs and the bosses name is rolled by Loot.roll and by the self-test; vaults hold regalia
  const named = new Set([...(designs + lords).matchAll(/"(jaspr:(?:colossus|depths)\/[a-z_]+)(?:!trap)?"/g)].map(m => m[1]));
  assert.ok(named.size >= 14, 'tables named: ' + named.size);
  const listed = loot.slice(loot.indexOf('static final String[] TABLES'));
  for (const t of named) {
    assert.ok(loot.includes('case "' + t + '":'), 'Loot.roll handles ' + t);
    assert.ok(listed.includes('"' + t + '"'), 'the self-test rolls ' + t);
  }
  for (const [t, set] of [['colossus/pyramid_vault', 'pharaoh'], ['colossus/citadel_vault', 'ember_guard'], ['depths/warden', 'deepwarden'], ['depths/heart', 'deepwarden']])
    assert.match(loot, new RegExp('case "jaspr:' + t + '": [^\\n]*armour\\("' + set + '"\\)\\.make\\(r\\)'), t + ' always holds a piece of ' + set);
  for (const m of loot.matchAll(/nx\("([a-z_]+)"|Items\.create\("([a-z_]+)"|armour\("([a-z_]+)"\)/g)) {
    const id = m[1] || m[2];
    if (id) assert.ok(known.has(id), 'loot names an unknown item ' + id);
    if (m[3]) assert.ok(known.has(m[3] + '_helmet'), 'armour set ' + m[3]);
  }
  // the ordeals: seals are clicked (nothing placed against them), never broken or blown apart, never closed on a player;
  // pits are never closed over a player in them; puzzles reset; work is bounded to players' surroundings
  assert.match(ordeals, /case KEYSEAL: if \(o\.contains\(b\.getX\(\) \+ 0\.5, b\.getY\(\) \+ 0\.5, b\.getZ\(\) \+ 0\.5\)\) \{ e\.setCancelled\(true\); keySeal/);
  assert.match(ordeals, /public void onBreak\(BlockBreakEvent e\)/);
  assert.match(ordeals, /public void onExplode\(org\.bukkit\.event\.entity\.EntityExplodeEvent e\)/);
  assert.match(ordeals, /public void onBlockExplode\(org\.bukkit\.event\.block\.BlockExplodeEvent e\)/);
  assert.match(ordeals, /if \(someone\) continue;/);
  assert.match(ordeals, /l\.getY\(\) > h\.y - 12/, 'a fallen floor stays open while someone is in the pit');
  assert.match(ordeals, /for \(Ordeal o : near\(l\.getBlockX\(\), l\.getBlockZ\(\), 24\)\)/);
  assert.match(plugin, /ordeals\.tick\(ticks\);/);
  assert.match(plugin, /if \(ordeals != null\) ordeals\.shutdown\(\);/);
  // logs for the new paths
  for (const ev of ['NETHER_PUZZLE_SOLVED', 'NETHER_PUZZLE_FAILED', 'NETHER_SEAL_OPENED', 'NETHER_ORDEAL_FAILED']) assert.ok(ordeals.includes(ev), ev);
  for (const ev of ['NETHER_CHAMPION_DEFEATED', 'NETHER_LORD_RISEN']) assert.ok(lords.includes(ev), ev);
  for (const ev of ['NETHER_COLOSSUS_PLANNED']) assert.ok(gen.includes(ev), ev);
  for (const ev of ['NETHER_CATACOMBS heart=']) assert.ok(plugin.includes(ev), ev);
  assert.ok(life.includes('NETHER_AMBUSH where='));
  // safety: no area effect clouds (older browser clients froze on them), no night vision, full-bright or glowing;
  // their fire never sets the halls alight; no forbidden or valuable block is named in the drawing code
  for (const name of NEW.concat(['Lords'])) {
    const code = java(name);
    assert.doesNotMatch(code, /AreaEffectCloud|DragonFireball/, name + ' spawns no clouds');
    assert.doesNotMatch(code, /NIGHT_VISION|GLOWING/, name + ' gives no night vision or glowing');
    assert.doesNotMatch(code, /getPlayer\(\)\.getName\(\)|getAddress\(\)/, name + ' logs no player names or addresses');
  }
  assert.match(dwellers, /if \(p instanceof SmallFireball\) \(\(SmallFireball\) p\)\.setIsIncendiary\(false\);/);
  assert.match(mobs, /if \(t != null && Dwellers\.KINDS\.contains\(t\.spec\.kind\)\) e\.setCancelled\(true\);/, 'no asp hides in the stone, no door breaks');
  assert.match(life, /plugin\.registry\.at\(b\.getX\(\), b\.getY\(\), b\.getZ\(\), "colossus"\) != null/, 'fire never eats a colossus');
  for (const id of [41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154])
    assert.doesNotMatch(designs + java('Colossi'), new RegExp('\\bb\\(' + id + '[,)]'), 'forbidden block ' + id);
});
