'use strict';
// Overworld structures 2x (owner 2026-10-04: "Make structures in the overworld 2x common ... Don't regenerate the
// overworld, but do retrofit structures that would normally spawn if it was regenerated"), JasprHorrorBiomes 3.29.0.
// Tier 2 is a second full 1x on top of the 3.27 layers: every older layer must come out byte for byte as live 3.27.6
// placed it (the fingerprints below were taken from the live jar's source over the same box), tier 2 must match the
// older count family by family, every tier-2 site must be recognised once built, and tier 2 must be decided site by
// site (Tier2 receipts) and retrofitted only where nobody has been (Tier2Retrofit).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const plugin = path.join(root, 'server/custom-plugins/JasprHorrorBiomes');
const src = path.join(plugin, 'src/chat/jaspr/biomes');
const read = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const api = path.join(root, 'server/cache/patched_1.12.2.jar');

// Live 3.27.6 over seed 3127727864271777472, chunks 400..700 x 400..700 (tests/java/.../StructureRatesProbe.java).
const OLD = {
  'catalogue.tier0': [13, 'dd5edc2f5ae6b63d'], 'catalogue.tier1': [14, '18194c2b69ff3f96'],
  'room.A': [1150, 'e289cf2091c589c4'], 'room.B': [821, 'b1cad8b71674b56e'], 'room.C': [986, '018d02b3c791a3cf'],
  'sanctuary.new': [1, '9d3c5c0f4e8cee62'], 'sanctuary.old': [1, '945275a2c7a20e15'],
  'setpiece.primary': [410, '52c826ddb658196c'], 'setpiece.secondary': [216, 'd59e6c336a5f98d2'],
};

test('overworld 2x: older layers unmoved, tier 2 a second full 1x, every tier-2 site recognised', {skip: !fs.existsSync(jdk) || !fs.existsSync(api), timeout: 600000}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-density-'));
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/biomes/StructureRatesProbe.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', api, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx1500m', '-cp', [out, api, path.join(plugin, 'resources')].join(path.delimiter),
    'chat.jaspr.biomes.StructureRatesProbe', '3127727864271777472', '400', '400', '300', '--assume-built'], {encoding: 'utf8', timeout: 540000});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout.slice(-2000));
  const layer = name => { const m = new RegExp('LAYER ' + name.replace('.', '\\.') + ' count=(\\d+) sha256=([0-9a-f]+)').exec(run.stdout); return m ? [Number(m[1]), m[2]] : [0, '']; };
  for (const [name, want] of Object.entries(OLD)) assert.deepEqual(layer(name), want, name + ' is placed exactly as live 3.27.6 placed it');
  assert.doesNotMatch(run.stdout, /ERROR\./, 'every tier-2 site is recognised where it stands');
  const sum = names => names.reduce((a, n) => a + layer(n)[0], 0);
  const ratio = (tier2, old) => tier2 / old;
  const setPieces = ratio(layer('setpiece.tertiary')[0], sum(['setpiece.primary', 'setpiece.secondary']));
  const rooms = ratio(layer('room.D')[0], sum(['room.A', 'room.B', 'room.C']));
  const catalogue = ratio(sum(['catalogue.tier2.legacy', 'catalogue.tier2.legacy2', 'catalogue.tier2.expansion', 'catalogue.tier2.expansion2']), sum(['catalogue.tier0', 'catalogue.tier1']));
  console.log('tier2/older: setPieces=' + setPieces.toFixed(3) + ' rooms=' + rooms.toFixed(3) + ' catalogue=' + catalogue.toFixed(3));
  assert.ok(setPieces > 0.9 && setPieces < 1.1, 'set pieces: tier 2 about as many again as before: ' + setPieces);
  assert.ok(rooms > 0.9 && rooms < 1.1, 'lattice rooms: tier 2 about as many again: ' + rooms);
  assert.ok(catalogue > 0.6, 'catalogue: tier 2 adds about as many again (small box, so loosely): ' + catalogue);
  assert.equal(layer('sanctuary.tier2')[0], 0, 'no tier-2 portal sanctuaries');
});

test('overworld 2x wiring: decided once, built only where nobody has been, recognised only once built', () => {
  const tier2 = read('Tier2'), retro = read('Tier2Retrofit'), packs = read('PackPlans'), rates = read('StructureRates');
  // the plan is pure (a regenerated world's), the boundary only routes old ground to the retrofit
  assert.match(rates, /static boolean permits2\(long seed, int x, int z, int sizeX, int sizeZ\) \{\s*return !FAILED_V2\.containsKey\(seed\) && permits\(seed, x, z, sizeX, sizeZ\);/);
  assert.match(rates, /static final double SELECT_2 = 0\.0;/);
  assert.match(read('Dungeons'), /public static final int ATTEMPTS_V2 = 78;/);
  // receipts: appended and synced before anything is built; read back at start
  assert.match(tier2, /out\.getFD\(\)\.sync\(\);/);
  assert.match(tier2, /"tier2-" \+ world\.getUID\(\) \+ "\.receipts"/);
  // populate decides only all-new ground, and yields to the other packs' plans
  assert.match(tier2, /if \(StructureRates\.old2\(seed, cx, cz\)\) \{ pendingCount\+\+; return false; \}/);
  assert.match(tier2, /(?:String why = |if \(why == null\) why = )PackPlans\.conflict\(l\.world,/);   // (3.30.0: after the variety rule)
  assert.match(packs, /"JasprImportedWorldgen"/);
  assert.match(packs, /"JasprMuseMaps"/);
  assert.match(packs, /return "imported-unreadable";/, 'an unreadable pack refuses the site, never guesses');
  // the retrofit's rules
  assert.match(retro, /static final long INHABITED_LIMIT = 1200;/);
  assert.match(retro, /static final int PLAYER_MARGIN = 112;/);
  assert.match(retro, /if \(guarded\(cx, cz\)\) \{ why = "guarded"; break; \}/);
  assert.match(retro, /if \(inhabited\(cx, cz\) > INHABITED_LIMIT\) \{ why = "inhabited"; break; \}/);
  assert.match(retro, /return Long\.MAX_VALUE;/, 'an unreadable chunk counts as lived-in');
  assert.match(retro, /if \(playerNear\(box\[0\], box\[1\], box\[2\], box\[3\]\)\) \{ defer\(key\); return; \}/);
  assert.match(retro, /Tier2\.only\(b\.key, b\.x0, b\.z0, b\.x1, b\.z1, \(\) -> \{/);
  // in a retrofit pass nothing older is built, and no write leaves the one site's box
  assert.match(read('Dungeons'), /if \(!Tier2\.writable\(c, x, z\)\) return;/);
  assert.equal((read('Megaliths').match(/boolean older = !Tier2\.restricted\(\);/g) || []).length, 3, 'ground, deep and sunken');
  assert.equal((read('Megaliths').match(/if \(!Tier2\.allow\(t\.seed, Tier2\.setPieceKey\(salt3, acx, acz\)/g) || []).length, 3);
  assert.match(read('Landmarks'), /if \(tertiary && !Tier2\.allow\(t\.seed, Tier2\.setPieceKey\(salt2, acx, acz\)/);
  assert.equal((read('Dungeons').match(/Anchor a = gate\(c, t, /g) || []).length, 16, 'every room builder, entrance and clearing');
  // recognition only of what is built
  assert.match(read('Megaliths'), /if \(base >= 0 && Tier2\.built\(t\.seed, Tier2\.setPieceKey\(C_SALT3\[k\], acx, acz\)\)\) return/);
  assert.match(read('Dungeons'), /if \(!Tier2\.built\(t\.seed, Tier2\.roomKey\(i, /);
  assert.match(read('StructurePlanner'), /if\(site!=null&&site\.intersects\(cx,cz\)&&Tier2\.built\(seed,site\.key\)\) result\.add\(site\);/);
  assert.match(read('WorldgenExpansion'), /result\.addAll\(StructurePlanner\.builtTier2\(world\.getSeed\(\),cx,cz\)\);/);
  // the generator stamps the 3.27 grids; tier-2 catalogue sites are drawn as their chunks populate
  assert.match(read('HorrorGenerator'), /WorldgenExpansion\.generated\(w,cx,cz\)\)site\.stamp\(d,cx,cz\);/);
  assert.match(read('RuinSupplies'), /StructurePlanner\.stampTier2\(w,c\);\s*Dungeons\.populate\(w,c,t,new Caves\(t\)\);/);
  // the old dungeon retrofit (which re-ran its builders over any new region file) is retired
  const main = read('HorrorPlugin');
  assert.match(main, /DUNGEON_RETROFIT retired=v8 replacedBy=tier2/);
  assert.doesNotMatch(main, /new DungeonRetrofit\(/);
  assert.match(main, /TIER2_READY receipts=/);
  assert.match(fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8'), /^version: 3\.(29|30)\.\d+$/m);   // (3.30.0: overworld variety, tests/overworld-variety.test.cjs)
});
