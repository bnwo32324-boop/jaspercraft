'use strict';

// Historical filename retained so existing QA entry points keep working. The
// v4 near-spawn prevalence contract was intentionally superseded by rare-v7.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');
const { nbt } = require('../scripts/verify-structures-release.cjs');

const root = path.resolve(__dirname, '..');
const plugin = path.join(root, 'server/custom-plugins/JasprHorrorBiomes');
const api = path.join(root, 'server/cache/patched_1.12.2.jar');
const jar = path.join(root, 'server/plugins/JasprHorrorBiomes.jar');
const audit = path.join(__dirname, 'java/chat/jaspr/biomes/StructureRarityAudit.java');

function run(command, args, cwd) {
  const result = cp.spawnSync(command, args, {
    cwd,
    encoding: 'utf8',
    windowsHide: true,
    timeout: 180000,
    maxBuffer: 8 * 1024 * 1024
  });
  assert.equal(
    result.status,
    0,
    String(result.error || '') + (result.stdout || '') + (result.stderr || '')
  );
  return result.stdout;
}

test('rare-v7 source, deployed plugin and production metadata agree on sparse discovery', {timeout: 300000}, () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-rare-v7-prevalence-'));
  const current = path.join(temp, 'current');
  const deployed = path.join(temp, 'deployed');
  fs.mkdirSync(current);
  fs.mkdirSync(deployed);

  const sources = fs.readdirSync(path.join(plugin, 'src/chat/jaspr/biomes'))
    .filter(name => name.endsWith('.java'))
    .map(name => path.join(plugin, 'src/chat/jaspr/biomes', name));

  run('javac', ['--release', '8', '-encoding', 'UTF-8', '-cp', api, '-d', current, ...sources, audit], temp);
  run('javac', ['--release', '8', '-encoding', 'UTF-8', '-cp', [jar, api].join(path.delimiter), '-d', deployed, audit], temp);

  const seed = nbt(path.join(root, 'server', 'world', 'level.dat')).Data.RandomSeed;
  const main = 'chat.jaspr.biomes.StructureRarityAudit';
  const sourceReport = run('java', [
    '-Xmx768m',
    '-cp',
    [current, api, path.join(plugin, 'resources')].join(path.delimiter),
    main,
    seed
  ], temp);
  const deployedReport = run('java', [
    '-Xmx768m',
    '-cp',
    [deployed, jar, api].join(path.delimiter),
    main,
    seed
  ], temp);

  assert.equal(deployedReport, sourceReport, 'Deployed placement logic must exactly match tested source');
  assert.match(sourceReport, /STRUCTURE_RARITY_PASS expectedRetention=10\.000% .*spawnExclusion=3072/);
  const seedReport = sourceReport.match(new RegExp('SEED_RARITY seed=' + seed + ' .*nearestStructureEdge=([0-9.]+)'));
  assert.ok(seedReport, 'Seed-specific rarity report');
  assert.ok(Number(seedReport[1]) >= 3072, 'No custom structure intersects the protected spawn radius');
  assert.equal(
    fs.readFileSync(path.join(root, 'server/plugins/JasprHorrorBiomes/terrain-epoch.txt'), 'utf8').trim(),
    'rare-v7'
  );
  for (const world of ['world', 'world_nether', 'world_the_end']) {
    assert.equal(nbt(path.join(root, 'server', world, 'level.dat')).Data.RandomSeed, seed, world + ' live seed');
  }
  process.stdout.write(sourceReport);
});
