'use strict';

// Read-only surface-v4 preservation gate: no reset.apply, CLI reset, HTTP,
// processes, or filesystem writes. Exit 0 passes coverage; exit 1 fails it.
// Only live plan EBUSY/EACCES/EPERM defer hash planning until a graceful stop.
// Exercise the real allowlist against an in-memory filesystem so an empty live
// stats/lives directory cannot hide omission of future per-player checkpoints.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const server = path.join(root, 'server');
const sourceFile = path.join(root, 'scripts/reset-horror-terrain.cjs');
const reset = require(sourceFile);
const EPOCH = 'plugins/JasprHorrorBiomes/terrain-epoch.txt';
const LEDGER = 'plugins/JasprApocalypse/ruins-ledger-v1.bin';
const WORLDS = ['world', 'world_nether', 'world_the_end', 'jaspr_backrooms'];
const CONFIGS = ['server.properties', 'bukkit.yml', 'spigot.yml', 'paper.yml',
  'permissions.yml', 'ops.json', 'whitelist.json', 'banned-players.json',
  'banned-ips.json', 'usercache.json'];

function probeAllowlist() {
  const base = path.resolve(root, 'tests', '__virtual_server_never_created__');
  const entries = [
    ...WORLDS.flatMap(world => ['playerdata/player.dat', 'stats/player.json',
      'advancements/player.json', 'uid.dat', 'data/map_0.dat'].map(p => world + '/' + p)),
    'jaspr_backrooms/region/r.0.0.mca',
    'jaspr_backrooms/level.dat',
    'plugins/AuthMe/authme.db', 'plugins/TestServerControl/config.yml',
    'plugins/AuthMe/authme.db-wal', 'plugins/TestServerControl/accounts/identity.json',
    'plugins/JasprHorrorBiomes/structure-loot-v2.journal',
    'plugins/JasprHorrorBiomes/structure-encounters-details-v3.bin',
    'plugins/JasprHorrorBiomes/liminal-returns-v1/player.json',
    'plugins/JasprApocalypse/survivors.yml',
    'plugins/JasprApocalypse/stats/lives/11111111-2222-3333-4444-555555555555.life',
    'plugins/JasprApocalypse/config.yml',
    'plugins/SkinsRestorer/players/player.player',
    'plugins/SkinsRestorer/skins/custom.skin',
    'plugins/EaglercraftXServer/settings/account.json',
    'plugins/FuturePlugin/nested/account-settings.bin',
    'plugins/FuturePlugin/ruins-ledger-v1.bin', // Only the exact Apocalypse ledger is excluded.
    ...CONFIGS, EPOCH,
  ];
  const targets = ['world/region', 'world_nether/DIM-1/region',
    'world_the_end/DIM1/region', 'world/data/Village.dat', LEDGER,
    'world/jaspr-expansion-v3.boundary'];
  const unprotected = [...targets.map(p => p.endsWith('/region') ? p + '/r.0.0.mca' : p),
    ...WORLDS.slice(0, 3).map(w => w + '/level.dat'), 'plugins/JasprApocalypse.jar'];
  const contents = new Map([...entries, ...unprotected].map(p => [path.join(base, p),
    Buffer.from(p === EPOCH ? 'details-v3\n' : 'synthetic preservation bytes: ' + p)]));
  const files = new Set(contents.keys());
  const dirs = new Set([base]);
  for (const file of files) {
    for (let p = path.dirname(file); p.startsWith(base); p = path.dirname(p)) {
      dirs.add(p);
      if (p === base) break;
    }
  }
  const fakeFs = {
    realpathSync: p => {
      assert.ok(files.has(p) || dirs.has(p), 'Unexpected virtual realpath');
      return p;
    },
    readFileSync: (p, encoding) => {
      assert.ok(contents.has(p), 'Unexpected virtual read');
      return encoding ? contents.get(p).toString(encoding) : Buffer.from(contents.get(p));
    },
    existsSync: p => files.has(p) || dirs.has(p),
    lstatSync: p => {
      assert.ok(files.has(p) || dirs.has(p), 'Unexpected virtual lstat');
      return { isSymbolicLink: () => false };
    },
    statSync: p => {
      assert.ok(files.has(p) || dirs.has(p), 'Unexpected virtual stat');
      return { isFile: () => files.has(p), isDirectory: () => dirs.has(p) };
    },
    readdirSync: p => [...new Set([...files, ...dirs]
      .filter(entry => entry !== p && path.dirname(entry) === p)
      .map(entry => path.basename(entry)))],
  };
  const virtualModule = { exports: {} };
  const sandboxRequire = name => {
    if (name === 'node:fs') return fakeFs;
    if (name === 'node:path') return path;
    if (name === 'node:crypto') return require('node:crypto');
    throw Error('Unexpected dependency: ' + name);
  };
  // require.main is absent, so the production CLI cannot execute here.
  vm.runInNewContext(fs.readFileSync(sourceFile, 'utf8'), {
    require: sandboxRequire, module: virtualModule, __dirname: path.dirname(sourceFile),
  }, { filename: sourceFile, timeout: 1000 });
  const protectedPaths = new Set(virtualModule.exports.protectedFiles(base));
  assert.deepEqual([...protectedPaths].sort(), [...entries].sort(), 'Complete synthetic allowlist');
  const planned = virtualModule.exports.plan(base, { epoch: 'surface-v4' });
  assert.equal(planned.previousEpoch, 'details-v3', 'Exact predecessor accepted');
  assert.equal(planned.epoch, 'surface-v4');
  assert.equal(planned.epochFile, EPOCH);
  assert.equal(planned.epochBeforeSha256, virtualModule.exports.hash(path.join(base, EPOCH)));
  assert.deepEqual(Array.from(planned.targets).sort(), [...targets].sort(), 'Only expected terrain targets');
  assert.deepEqual(Object.keys(planned.protectedHashes).sort(), entries.filter(p => p !== EPOCH).sort(),
    'Only the separately recorded epoch leaves the protected hashes');
  for (const [relative, hash] of Object.entries(planned.protectedHashes)) {
    assert.equal(hash, virtualModule.exports.hash(path.join(base, relative)), 'Protected bytes: ' + relative);
    assert.ok(!planned.targets.some(p => relative === p || relative.startsWith(p + '/')),
      'Protected state must not be moved with terrain: ' + relative);
  }
  for (const predecessor of ['structures-v2', 'surface-v4']) {
    contents.set(path.join(base, EPOCH), Buffer.from(predecessor + '\n'));
    assert.throws(() => virtualModule.exports.plan(base, { epoch: 'surface-v4' }), /exact details-v3 predecessor/);
  }
  return []; // Retain the probe API: any omission now throws instead of reporting a stale blocker.
}

function main() {
  const protectedPaths = reset.protectedFiles(server); // Names only; no locked journal reads.
  const missingFromRecoveryAndHashes = probeAllowlist();
  assert.deepEqual(missingFromRecoveryAndHashes, []);
  const epochPath = path.join(server, EPOCH);
  const currentEpoch = fs.existsSync(epochPath) ? fs.readFileSync(epochPath, 'utf8').trim() : null;
  assert.equal(currentEpoch, 'details-v3', 'Live server must still have the expected predecessor');
  let planned = null;
  let liveHashPlan;
  try {
    planned = reset.plan(server, { epoch: 'surface-v4' }); // Read-only; never apply.
  } catch (error) {
    if (!['EBUSY', 'EACCES', 'EPERM'].includes(error.code)) throw error;
    liveHashPlan = {
      verified: false,
      deferred: true,
      errorCode: error.code,
      path: error.path ? path.relative(server, error.path).replaceAll('\\', '/') : null,
      reason: 'Live file access prevented hash planning; rerun after a graceful Paper stop.',
    };
  }
  if (planned) {
    assert.equal(planned.previousEpoch, currentEpoch);
    assert.equal(planned.epoch, 'surface-v4');
    assert.equal(planned.epochBeforeSha256, reset.hash(epochPath));
    assert.deepEqual(Object.keys(planned.protectedHashes).sort(), protectedPaths.filter(p => p !== EPOCH).sort());
    liveHashPlan = { verified: true, deferred: false };
  }
  const protectedSet = new Set(protectedPaths);
  assert.ok(protectedSet.has(EPOCH), 'Predecessor epoch remains in the preservation allowlist');
  // Independently enumerate live player, plugin/account and configuration paths.
  // This catches omissions even when the synthetic fixture has no matching file.
  function liveFiles(relative) {
    const absolute = path.join(server, relative);
    if (!fs.existsSync(absolute)) return [];
    const stat = fs.lstatSync(absolute);
    assert.ok(!stat.isSymbolicLink(), 'Linked preservation path: ' + relative);
    return stat.isDirectory() ? fs.readdirSync(absolute).flatMap(name => liveFiles(relative + '/' + name)) : [relative];
  }
  const required = [...CONFIGS.flatMap(liveFiles), ...liveFiles('jaspr_backrooms')];
  for (const world of WORLDS.slice(0, 3)) {
    for (const part of ['playerdata', 'stats', 'advancements', 'uid.dat']) required.push(...liveFiles(world + '/' + part));
  }
  for (const name of fs.readdirSync(path.join(server, 'plugins'))) {
    const stat = fs.lstatSync(path.join(server, 'plugins', name));
    assert.ok(!stat.isSymbolicLink(), 'Linked plugin path: ' + name);
    if (stat.isDirectory()) required.push(...liveFiles('plugins/' + name));
  }
  for (const relative of required.filter(p => p !== EPOCH && p !== LEDGER)) {
    assert.ok(protectedSet.has(relative), 'Missing live preservation: ' + relative);
    if (planned) {
      assert.ok(Object.hasOwn(planned.protectedHashes, relative), 'Missing live hash: ' + relative);
      assert.ok(!planned.targets.some(p => relative === p || relative.startsWith(p + '/')),
        'Live protected state overlaps a terrain target: ' + relative);
    }
  }
  assert.ok(!protectedSet.has(LEDGER), 'Terrain-linked Apocalypse ledger is excluded');
  if (planned) {
    assert.ok(!Object.hasOwn(planned.protectedHashes, LEDGER));
    assert.equal(planned.targets.includes(LEDGER), fs.existsSync(path.join(server, LEDGER)));
  }
  const lifeDirectory = path.join(server, 'plugins/JasprApocalypse/stats/lives');
  const lifeFiles = fs.existsSync(lifeDirectory)
    ? fs.readdirSync(lifeDirectory).filter(name => name.endsWith('.life')).length : 0;
  const counts = {};
  for (const world of WORLDS) {
    counts[world] = {};
    for (const kind of ['playerdata', 'stats', 'advancements']) {
      counts[world][kind] = protectedPaths.filter(p => p.startsWith(world + '/' + kind + '/')).length;
    }
  }
  console.log(JSON.stringify({
    readOnly: true,
    verified: true,
    gate: 'surface-v4-preservation-coverage',
    currentEpoch,
    requestedEpoch: 'surface-v4',
    plannedEpoch: planned ? planned.epoch : null,
    predecessorVerified: true,
    livePathCoverageVerified: true,
    liveHashPlan,
    protectedFileCount: protectedPaths.filter(p => p !== EPOCH).length,
    hashedFileCount: planned ? Object.keys(planned.protectedHashes).length : null,
    protectedPlayerFileCounts: counts,
    liveLifeCheckpointFiles: lifeFiles,
    syntheticAllowlistProbe: { missingFromRecoveryAndHashes, planAndHashesVerified: true },
    targets: planned ? planned.targets : null,
    limitation: 'Passing coverage does not certify deferred live hashes. No reset, recovery-copy verification, or external Jaspr database backup is performed.',
  }, null, 2));
}

if (require.main === module) {
  try { main(); } catch (error) { console.error(error.stack); process.exitCode = 1; }
}
module.exports = { probeAllowlist };
