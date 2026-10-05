'use strict';
// Overworld variety (owner 2026-10-05: "I keep seeing the same structures over and over again, and I don't encounter
// the big structures or unique ones a lot"), JasprHorrorBiomes 3.30.0, for ground generated from now on only:
//  - the catalogue's expedition architecture (the overworld's big, unique sites) may stand from 512 blocks out instead
//    of 3,072, but only where none of its ground (two chunks of reserve included) existed when 3.30.0 first started;
//  - the third tier-2 expansion grid plans at full density (no catalogue site had been decided anywhere yet);
//  - tier 2 keeps only 40% of its not yet decided extra copies of the commonest surface kinds.
// Everything already placed stays placed and recognised: the older layers' fingerprints are unmoved
// (tests/structure-density.test.cjs) and the near-spawn rule never admits a site that touches ground that existed.
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
const LIVE_SEED = '-6436856966336135554';

test('overworld variety: the big unique sites near spawn on new ground, fewer extra copies of the commonest kinds', {skip: !fs.existsSync(jdk) || !fs.existsSync(api), timeout: 600000}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-variety-'));
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/biomes/StructureRatesProbe.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', api, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const probe = extra => spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx1500m', '-cp', [out, api, path.join(plugin, 'resources')].join(path.delimiter),
    'chat.jaspr.biomes.StructureRatesProbe', LIVE_SEED, '-160', '-160', '320', '--assume-built', ...extra], {encoding: 'utf8', timeout: 240000});
  const old = probe([]), fresh = probe(['--fresh-v3']);
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(old.status, 0, old.stderr + old.stdout.slice(-2000));
  assert.equal(fresh.status, 0, fresh.stderr + fresh.stdout.slice(-2000));
  const catalogue = s => [...s.matchAll(/LAYER catalogue\.\S+ count=(\d+)/g)].reduce((a, m) => a + Number(m[1]), 0);
  console.log('catalogue sites within 2560 blocks of spawn: old rule ' + catalogue(old.stdout) + ', new ground ' + catalogue(fresh.stdout));
  assert.ok(catalogue(old.stdout) < 10, 'the old rule: hardly any big unique site within 2.5 km of spawn');
  assert.ok(catalogue(fresh.stdout) >= 6 * catalogue(old.stdout) && catalogue(fresh.stdout) > 50, 'new ground near spawn takes the big unique sites');
  // the same layers far out are untouched by the near-spawn rule (it only ever opens ground inside 3,072 blocks)
  const v = /VARIETY commonSetPieceKept=([\d.]+) otherSetPieceKept=([\d.]+) commonRoomKept=([\d.]+) otherRoomKept=([\d.]+)/.exec(fresh.stdout);
  assert.ok(v, 'the variety line');
  assert.ok(Math.abs(Number(v[1]) - 0.4) < 0.03 && Math.abs(Number(v[3]) - 0.4) < 0.03, 'about 40% of the commonest kinds\' extra copies kept: ' + v[0]);
  assert.equal(Number(v[2]), 1, 'every other set piece kept');
  assert.equal(Number(v[4]), 1, 'every other room kept');
});

test('overworld variety wiring: v3 boundary, the near-spawn rule in every admission, tier 2 and the retrofit decide alike', () => {
  const rates = read('StructureRates'), planner = read('StructurePlanner'), tier2 = read('Tier2'), retro = read('Tier2Retrofit'), plugin2 = read('HorrorPlugin');
  assert.match(rates, /BOUNDARY_FILE_V3 = "jaspr-rates-v3\.boundary";/);
  assert.match(rates, /FAILED_V3\.put\(seed, Boolean\.TRUE\);\s*\/\/ fail closed/);
  assert.match(rates, /if \(b == null\) return true;/, 'no v3 boundary: the old rule');
  assert.match(planner, /SPAWN_EXCLUSION_RADIUS=3072;/);
  assert.match(planner, /SPAWN_EXCLUSION_RADIUS_V3=512;/);
  assert.equal((planner.match(/spawnExcluded\(seed,x,z,width,depth\)/g) || []).length, 2, 'legacy and expansion admission');
  assert.match(planner, /if\(spawnExcluded\(terrain\.seed,x,z,width,depth\)\)return false;/, 'tier-2 admission');
  assert.doesNotMatch(planner.replace(/static boolean intersectsSpawnExclusion[\s\S]*?\n    \}/, '').replace(/static boolean spawnExcluded[\s\S]*?\n    \}/, ''),
    /intersectsSpawnExclusion\(/, 'no admission asks the old test alone');
  assert.match(planner, /EXP_DENSITY=\{TIER2_EXPANSION_DENSITY,TIER2_EXPANSION_DENSITY,TIER2_EXPANSION_DENSITY\};/);
  assert.match(tier2, /static final double COMMON_KEEP = 0\.4;/);
  assert.match(tier2, /String why = variety\(key\);\s*if \(why == null\) why = PackPlans\.conflict/);
  assert.ok(tier2.indexOf('Boolean known = l.receipts.get(key);') < tier2.indexOf('String why = variety(key);'), 'a decided site keeps its receipt');
  assert.match(retro, /if \(why == null\) why = Tier2\.variety\(key\);/);
  assert.match(plugin2, /int n3=StructureRates\.initializeV3\(w\);/);
  assert.ok(plugin2.indexOf('initializeV3(w)') < plugin2.indexOf('Tier2.open(w,'), 'the v3 boundary opens before tier 2 decides anything');
  assert.match(fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8'), /^version: 3\.30\.0$/m);
});
