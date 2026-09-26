'use strict';
// BetterNether + NetherEx (JasprNether) and Lost Cities (JasprLostCities) invariants, and the gates that keep the other
// world generators out of cities and out of the Nether. Run: node --test tests/nether-lostcities.test.cjs
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const nether = 'server/custom-plugins/JasprNether';
const biomes = 'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/';

// Valuable or unsafe vanilla blocks never placed by generation (owner rule: no valuable blocks in structures).
const FORBIDDEN = new Set(['diamond_block', 'gold_block', 'emerald_block', 'iron_block', 'beacon', 'lapis_block', 'redstone_block',
  'tnt', 'portal', 'end_portal', 'command_block', 'repeating_command_block', 'chain_command_block', 'structure_block', 'barrier']);

function rows() {
  return read(nether + '/resources/blocks.tsv').split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => l.split('\t'));
}

test('JasprNether: one emulation table, every row resolvable and no valuable stand-ins', () => {
  const seen = new Set();
  for (const r of rows()) {
    const [source, meta, vanilla, vmeta, role] = r;
    assert.match(source, /^(betternether|netherex):[a-z0-9_]+$/, 'source ' + source);
    assert.match(meta, /^(\*|\d{1,2})$/, source);
    assert.ok(vanilla === 'void' || /^minecraft:[a-z0-9_]+$/.test(vanilla), source + ' -> ' + vanilla);
    assert.ok(!FORBIDDEN.has(vanilla.replace('minecraft:', '')), source + ' is emulated by a forbidden block ' + vanilla);
    assert.match(vmeta, /^(=|\+\d+|\d{1,2})$/, source);
    assert.ok(['structure', 'worldgen', 'mechanic', 'player'].includes(role), source + ' role ' + role);
    const key = source + '@' + meta;
    assert.ok(!seen.has(key), 'duplicate row ' + key);
    seen.add(key);
  }
  assert.ok(seen.size >= 100);
});

test('JasprNether: every structure template block has a table row and no forbidden vanilla block', () => {
  const table = new Map();
  for (const [source, meta] of rows()) { if (!table.has(source)) table.set(source, new Set()); table.get(source).add(meta); }
  const dir = path.join(root, nether, 'resources/structures');
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.jnt'));
  assert.ok(files.length >= 50, 'templates ' + files.length);
  for (const f of files) {
    let inPalette = false;
    for (const line of fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/)) {
      const p = line.split(' ');
      if (p[0] === 'palette') { inPalette = true; continue; }
      if (p[0] === 'data' || p[0] === 'marker') inPalette = false;
      if (!inPalette || p.length !== 3) continue;
      const [id, meta] = p;
      if (id.startsWith('minecraft:')) { assert.ok(!FORBIDDEN.has(id.slice(10)), f + ' places ' + id); continue; }
      const metas = table.get(id);
      assert.ok(metas && (metas.has('*') || metas.has(meta)), f + ': ' + id + ' meta ' + meta + ' has no blocks.tsv row');
    }
  }
});

test('JasprNether: owns world_nether, loads at startup, and logs its readiness', () => {
  const yml = read(nether + '/resources/plugin.yml');
  assert.match(yml, /^name: JasprNether$/m);
  assert.match(yml, /^load: STARTUP$/m);
  assert.match(read(nether + '/resources/config.yml'), /^world: world_nether$/m);
  const plugin = read(nether + '/src/chat/jaspr/nether/NetherPlugin.java');
  assert.match(plugin, /NETHER_READY version=/);
  // The owner's "regenerate the current Nether": one-shot, before worlds load, and a move (never a delete).
  assert.match(read(nether + '/resources/config.yml'), /^regenerate-once: true$/m);
  assert.match(plugin, /@Override public void onLoad\(\)/);
  assert.match(plugin, /"regenerated-v1\.txt"/);
  assert.match(plugin, /java\.nio\.file\.Files\.move\(region\.toPath\(\), new File\(backup, "region"\)\.toPath\(\)\)/);
  assert.doesNotMatch(plugin, /\.delete\(\)|deleteIfExists|deleteRecursively/);
});

test('HorrorBiomes: Outer Realms stays out of a JasprNether Nether (populator, ambience titles, /where)', () => {
  const plugin = read(biomes + 'HorrorPlugin.java');
  assert.match(plugin, /!netherOwned\(w\)&&!w\.getPopulators\(\)/);
  assert.match(plugin, /\|\|netherOwned\(p\.getWorld\(\)\)\)continue;/);
  assert.match(plugin, /getPlugin\("JasprNether"\)!=null/);
  assert.match(read(biomes + 'Survey.java'), /if \(HorrorPlugin\.netherOwned\(w\)\) return true;/);
});

test('Every overworld generator asks the Lost Cities reservation before placing', () => {
  const cities = read(biomes + 'Cities.java');
  assert.match(cities, /getPlugin\("JasprLostCities"\)/);
  assert.match(cities, /"chat\.jaspr\.lostcities\.CityApi"/);
  assert.match(cities, /getMethod\("reserved", World\.class, int\.class, int\.class, int\.class, int\.class\)/);
  assert.match(read(biomes + 'Megaliths.java'), /if \(Cities\.reserved\(t\.seed, x, z, C_SX\[k\], C_SZ\[k\]\)\) return false;/);
  const dungeons = read(biomes + 'Dungeons.java');
  assert.match(dungeons, /return cityless\(t, anchorRaw\(/);
  assert.match(dungeons, /return cityless\(t, surfaceAnchorRaw\(/);
  const planner = read(biomes + 'StructurePlanner.java');
  assert.match(planner, /return cityless\(seed,planRaw\(/);
  assert.match(planner, /return cityless\(seed,planExpansionRaw\(/);
  assert.match(read(biomes + 'RuinSupplies.java'), /if\(!Cities\.reserved\(w\.getSeed\(\),c\.getX\(\)\*16,c\.getZ\(\)\*16,16,16\)\)Dungeons\.populate/);
  for (const p of ['server/custom-plugins/JasprImportedWorldgen/patch/chat/jaspr/imported/Admission.java',
    'server/custom-plugins/JasprImportedWorldgen/live-1.2.2/Admission.java']) {
    const s = read(p);
    assert.match(s, /if \(Admission\.city\(seed, x, z, sizeX, sizeZ\)\) \{\s*return false;/, p);
    assert.match(s, /chat\.jaspr\.biomes\.Cities\.reserved\(seed, x, z, sizeX, sizeZ\)/, p);
  }
  const muse = read('server/custom-plugins/JasprMuseMaps/src/chat/jaspr/muse/MusePlugin.java');
  assert.match(muse, /if \(cityReserved\(seed, x, z, w, dp\)\) return false;/);
  assert.match(muse, /\|\| r\.abandoned \|\| yieldsToCity\(ctx, r\)\) return;/);
  assert.match(muse, /MUSE_SITE_YIELDED_TO_CITY/);
});

test('JasprLostCities: public CityApi, startup load after HorrorBiomes, and no valuable or marker blocks placed', () => {
  const lc = 'server/custom-plugins/JasprLostCities/';
  const api = read(lc + 'src/chat/jaspr/lostcities/CityApi.java');
  assert.match(api, /public static boolean cityRegion\(long worldSeed, int chunkX, int chunkZ\)/);
  assert.match(api, /public static boolean reserved\(World world, int blockX, int blockZ, int width, int depth\)/);
  const yml = read(lc + 'resources/plugin.yml');
  assert.match(yml, /^name: JasprLostCities$/m);
  assert.match(yml, /^load: STARTUP$/m);
  assert.match(yml, /^depend: \[JasprHorrorBiomes\]$/m);
  const plugin = read(lc + 'src/chat/jaspr/lostcities/LostCitiesPlugin.java');
  assert.match(plugin, /LOST_CITIES_READY version=/);
  // Plans other packs committed to before the first city are kept out of city land like pre-existing chunks.
  assert.match(plugin, /Committed committed = Committed\.open\(world, getDataFolder\(\)\.getParentFile\(\), getLogger\(\)\);/);
  assert.match(plugin, /\(x, z\) -> boundary\.contains\(x, z\) \|\| committed\.contains\(x, z\)/);
  const committed = read(lc + 'src/chat/jaspr/lostcities/Committed.java');
  assert.match(committed, /"jaspr-cities-v1\.committed"/);
  for (const dir of ['"cells-" + uid', '"cells2-" + uid', '"JasprImportedWorldgen"', '"JasprMuseMaps"']) assert.ok(committed.includes(dir), dir);
  // Every palette block goes through B.sanitize: the mod's '!' valuables become ordinary blocks of the same hue.
  const b = read(lc + 'src/chat/jaspr/lostcities/B.java');
  assert.match(b, /result = sanitize\(result\);/);
  for (const id of [41, 42, 57, 133, 22, 152, 138]) assert.ok(b.includes('case ' + id + ': return c('), 'sanitize ' + id);
  assert.match(read(lc + 'src/chat/jaspr/lostcities/CityGenerator.java'), /\(b >> 4\) == 137\) \{\s*\/\/ hard air/);
  // The mod's city data ships unchanged; only the chisel style's 7 non-vanilla blocks are remapped (to concrete).
  for (const f of ['palette.json', 'palette_desert.json', 'buildingparts.json', 'library.json', 'highwayparts.json', 'railparts.json'])
    assert.ok(fs.existsSync(path.join(root, lc, 'resources/lostcities/citydata', f)), f);
});
