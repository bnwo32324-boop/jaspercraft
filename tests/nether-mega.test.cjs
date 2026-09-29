'use strict';
// JasprNether mega structures and wonders (owner, 2026-09-28: "massive structures in the Nether that spawn naturally",
// "make the Nether more interesting and fun to explore", "regenerate the Nether"). Compiles the plugin with the offline
// check (tests/java/chat/jaspr/nether/MegaPreview.java) and runs it: each of the five structures is drawn chunk by
// chunk on synthetic Nether terrain, exactly as population clips it, and must be deterministic (chunk by chunk == all
// at once), free of forbidden or valuable blocks, stocked (chests, a vault, garrisons, its urn / statue / spawners)
// and cheap. Then the wiring: loot ids exist, every table a structure names is rolled, the mods' small structures stay
// out of a mega structure's reach, the Golden Bazaar keeps the peace, garrisons never hand out wither skulls, the
// regenerated Nether marks its history complete, lava is sealed out of the caverns, a player in rock is rescued.
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
const KINDS = ['golden_bazaar', 'soul_pyramid', 'cinder_forge', 'spore_cathedral', 'frozen_citadel'];

test('nether mega structures: drawn per chunk, deterministic, no valuables, stocked, cheap', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-nether-mega-'));
  const classes = path.join(out, 'classes');
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/nether/MegaPreview.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', classes, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx1200m', '-Djava.awt.headless=true', '-cp', classes + path.delimiter + cp,
    'chat.jaspr.nether.MegaPreview', '-', path.join(root, 'server/custom-plugins/JasprNether/resources/blocks.tsv')], {encoding: 'utf8', timeout: 900000, cwd: out});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout.slice(-3000));
  assert.match(run.stdout, /MEGA_OK/);
  for (const id of KINDS) assert.match(run.stdout, new RegExp('mega ' + id + ' .*deterministic=true forbidden=\\{\\} .*PASS'), id);
  assert.match(run.stdout, /mega spore_cathedral .*urn=1/, 'the Ghast Queen urn crowns the cathedral');
  assert.match(run.stdout, /mega golden_bazaar .*statue=1/, 'the Respawner Statue stands in the palace');
  assert.match(run.stdout, /mega cinder_forge .*spawners=\{blaze=2, magma_cube=1\}/, 'the forge keeps its furnaces');
});

test('nether mega wiring: loot, tables, quiet mods, peace, skulls, history, lava seal, rescue', () => {
  const loot = java('Loot'), items = java('Items');
  const known = new Set([...items.matchAll(/def\(new Def\("([a-z_]+)"/g)].map(m => m[1]));
  for (const h of ['withered', 'blazed', 'frosted']) for (const t of ['sword', 'pickaxe', 'shovel', 'axe', 'hoe', 'hammer']) known.add(h + '_amedian_' + t);
  for (const set of ['wither_bone', 'orange_salamander_hide', 'black_salamander_hide']) for (const slot of ['helmet', 'chestplate', 'leggings', 'boots']) known.add(set + '_' + slot);
  for (const m of loot.matchAll(/nx\("([a-z_]+)"|Items\.create\("([a-z_]+)"|armour\("([a-z_]+)"\)/g)) {
    const id = m[1] || m[2];
    if (id) assert.ok(known.has(id), 'loot names an unknown item ' + id);
    if (m[3]) assert.ok(known.has(m[3] + '_helmet'), 'armour set ' + m[3]);
  }
  // every vault holds its structure's progression prize
  for (const [t, prize] of [['bazaar_vault', 'amethyst_crystal'], ['pyramid_vault', 'armour("wither_bone")'], ['forge_vault', 'armour("orange_salamander_hide")'],
    ['cathedral_vault', 'Items.create("potion_sorrow", 1)'], ['citadel_vault', 'Items.create("frosted_wither_bone", 1)']])
    assert.ok(new RegExp('case "jaspr:mega/' + t + '": [^\\n]*' + prize.replace(/[()".]/g, '\\$&')).test(loot), t + ' always holds ' + prize);
  // every table a structure names is rolled by Loot.roll and by the self-test
  const designs = ['MegaBazaar', 'MegaPyramid', 'MegaForge', 'MegaCathedral', 'MegaCitadel', 'Wonders'].map(java).join('\n');
  const named = new Set([...designs.matchAll(/"(jaspr:(?:mega|wonder)\/[a-z_]+)"/g)].map(m => m[1]));
  assert.ok(named.size >= 14, 'tables named: ' + named.size);
  const listed = loot.slice(loot.indexOf('static final String[] TABLES'));
  for (const t of named) {
    assert.ok(loot.includes('case "' + t + '":'), 'Loot.roll handles ' + t);
    assert.ok(listed.includes('"' + t + '"'), 'the self-test rolls ' + t);
  }
  // no forbidden or valuable block is named in the drawing code (the offline check also scans every block written)
  const drawing = designs + java('Draw') + java('Mega');
  for (const id of [41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154])
    assert.doesNotMatch(drawing, new RegExp('\\bb\\(' + id + '[,)]'), 'forbidden block ' + id);
  // the generator: the mods' small structures stay out of a mega structure's reach; each site is decided once
  const gen = java('Gen');
  assert.match(gen, /if \(s\.maxX >= bx0 && s\.minX <= bx1 && s\.maxZ >= bz0 && s\.minZ <= bz1\) quiet = true;/);
  // sites are redrawn over the whole 2x2-chunk area (wiping late spills into the cavern), tiles only in the box
  assert.match(gen, /Mega\.draw\(s, new Draw\(a, a\.ox, a\.oz, a\.ox \+ 31, a\.oz \+ 31, bx0, bz0, bx1, bz1, tiles\)\);/);
  assert.match(java('Draw'), /if \(!inTiles\(x, z\) && Blocks\.isTileEntity\(c\.get\(x, y, z\) >> 4\)\) return;/);
  assert.match(gen, /bn\.populate\(a, rand, post, quiet\);/);
  assert.match(gen, /if \(r\.nextDouble\(\) < 0\.25 && !quiet\) village\(a, r, post\);/);
  assert.match(gen, /registry\.setMegaDecision\(s\.cellX, s\.cellZ, on\);/);
  assert.match(gen, /on = megaComplete \|\| centreHere \|\| !world\.isChunkGenerated\(ccx, ccz\);/);
  assert.match(gen, /if \(!quiet\) wonders\.populate\(/);
  // mega sites keep out of the cities, and (since the GLM update) of the Lords' strongholds and the great GLM builds
  assert.match(gen, /this\.mega = new Mega\(seed, biomes::nex, this::cityOrLordReach\);/);
  assert.match(gen, /boolean cityOrLordReach\(int x0, int z0, int x1, int z1\) \{\s*return cityReach\(x0, z0, x1, z1\)/);
  // the Golden Bazaar keeps the peace; garrisons never hand out wither skulls and log no player
  assert.match(java('Mobs'), /if \(plugin\.gen\.peaceful\(l\.getBlockX\(\), l\.getBlockZ\(\), type == EntityType\.GHAST\)\) \{ e\.setCancelled\(true\);/);
  const garrisons = java('Garrisons');
  assert.match(garrisons, /s\.getType\(\) == Material\.SKULL_ITEM && s\.getDurability\(\) == 1/);
  assert.match(garrisons, /"NETHER_GARRISON_ROUSED at=" \+ e\.x1/);
  assert.doesNotMatch(garrisons, /getName\(\)|getUniqueId\(\)/);
  // the caverns seal the lava in their rock so no spring pours onto a structure
  assert.match(java('Mega'), /if \(id == Blocks\.LAVA \|\| id == Blocks\.LAVA_FLOW\) d\.set\(x, y, z, rock\.at\(x, y, z\)\);/);
  // the regenerated Nether's history is complete (every site built); a player in rock or lava is rescued on join
  const plugin = java('NetherPlugin');
  assert.ok(plugin.includes('"mega-complete.txt"'));
  assert.ok(plugin.includes('NETHER_RESCUED cause=join'));
  assert.ok(plugin.includes('" mega=" + Mega.Kind.values().length'));
  // camps carry the expedition journal with rumours of the nearest mega structures
  assert.match(java('StructureOps'), /if \(table\.equals\("jaspr:wonder\/camp"\) && plugin\.gen != null\)/);
  assert.match(java('Wonders'), /g\.mega\.nearest\(k, x, z, 4\)/);
});
