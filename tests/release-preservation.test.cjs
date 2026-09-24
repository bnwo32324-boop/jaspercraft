'use strict';

// This gate imports functions only: no CLI, HTTP, Java, or real server fixtures.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const reset = require('../scripts/reset-horror-terrain.cjs');
const { nbt, verifyOfflineFiles } = require('../scripts/verify-structures-release.cjs');

const WORLDS = ['world', 'world_nether', 'world_the_end'];
const ALL_WORLDS = [...WORLDS, 'jaspr_backrooms'];
const PLAYER = '11111111-2222-3333-4444-555555555555';
const EPOCH = 'plugins/JasprHorrorBiomes/terrain-epoch.txt';
const CLAIMS = 'plugins/JasprHorrorBiomes/structure-loot-v2.journal';
const REQUIRED_MOVED = [
  'world/region', 'world_nether/DIM-1/region', 'world_the_end/DIM1/region',
  'world/jaspr-expansion-v3.boundary',
  'plugins/JasprApocalypse/turrets.yml',
];

// A small independent encoder exercises actual gzip NBT, including every tag type.
const tag = (type, value) => ({ type, value });
const compound = value => tag(10, value);
const int = value => tag(3, value);
const string = value => tag(8, value);
const list = (type, values) => tag(9, { type, values });
function scalar(size, method, value) {
  const result = Buffer.alloc(size);
  result[method](value, 0);
  return result;
}
function textBytes(value) {
  const bytes = Buffer.from(value, 'utf8');
  return Buffer.concat([scalar(2, 'writeUInt16BE', bytes.length), bytes]);
}
function payload({ type, value }) {
  switch (type) {
    case 1: return scalar(1, 'writeInt8', value);
    case 2: return scalar(2, 'writeInt16BE', value);
    case 3: return scalar(4, 'writeInt32BE', value);
    case 4: return scalar(8, 'writeBigInt64BE', BigInt(value));
    case 5: return scalar(4, 'writeFloatBE', value);
    case 6: return scalar(8, 'writeDoubleBE', value);
    case 7: return Buffer.concat([payload(int(value.length)), Buffer.from(value)]);
    case 8: return textBytes(value);
    case 9: return Buffer.concat([
      Buffer.from([value.type]), payload(int(value.values.length)),
      ...value.values.map(item => payload(tag(value.type, item))),
    ]);
    case 10: return Buffer.concat([
      ...Object.entries(value).map(([name, child]) => Buffer.concat([
        Buffer.from([child.type]), textBytes(name), payload(child),
      ])), Buffer.from([0]),
    ]);
    case 11: return Buffer.concat([payload(int(value.length)), ...value.map(item => payload(int(item)))]);
    case 12: return Buffer.concat([payload(int(value.length)), ...value.map(item => payload(tag(4, item)))]);
    default: throw Error('Unsupported fixture tag: ' + type);
  }
}
function gzipNbt(document) {
  assert.equal(document.type, 10);
  return zlib.gzipSync(Buffer.concat([Buffer.from([10]), textBytes(''), payload(document)]));
}
function unknownTags() {
  return compound({
    Byte: tag(1, -7), Short: tag(2, -1234), Int: int(2147483647),
    Long: tag(4, '9223372036854775806'), Float: tag(5, 1.25), Double: tag(6, -12.125),
    Bytes: tag(7, [0, 127, 128, 255]), String: string('unknown \u2603 metadata'),
    List: list(10, [{ Key: string('first'), Value: int(17) }, { Key: string('second'), Value: int(-9) }]),
    EmptyList: list(10, []), Compound: compound({ Child: string('keep nested data') }),
    Ints: tag(11, [-2147483648, 0, 2147483647]),
    Longs: tag(12, ['-9223372036854775808', '9007199254740993']),
    DragonFight: string('unrelated extension key must survive'),
  });
}
const UNKNOWN_VALUES = {
  Byte: -7, Short: -1234, Int: 2147483647, Long: '9223372036854775806',
  Float: 1.25, Double: -12.125, Bytes: '007f80ff', String: 'unknown \u2603 metadata',
  List: [{ Key: 'first', Value: 17 }, { Key: 'second', Value: -9 }],
  EmptyList: [], Compound: { Child: 'keep nested data' },
  Ints: [-2147483648, 0, 2147483647], Longs: ['-9223372036854775808', '9007199254740993'],
  DragonFight: 'unrelated extension key must survive',
};
function playerNbt(world) {
  return compound({
    UUIDMost: tag(4, '1229782938533638963'), UUIDLeast: tag(4, '4919150517987661141'),
    Dimension: int([0, -1, 1, 0][ALL_WORLDS.indexOf(world)]),
    Pos: list(6, [145.5, 81.25, -97.5]), playerGameType: int(2),
    Health: tag(5, 17.5), XpTotal: int(12345),
    Inventory: list(10, [{
      Slot: tag(1, 0), id: string('minecraft:diamond_sword'), Count: tag(1, 1), Damage: tag(2, 12),
      tag: compound({ display: compound({ Name: string('Synthetic keepsake') }), UnknownItemData: unknownTags() }),
    }]),
    EnderItems: list(10, [{ Slot: tag(1, 3), id: string('minecraft:diamond'), Count: tag(1, 23) }]),
    UnknownPlayerData: unknownTags(),
  });
}
function levelNbt(world) {
  const index = ALL_WORLDS.indexOf(world);
  return compound({
    Data: compound({
      LevelName: string(world), RandomSeed: tag(4, '9007199254740993'), GameType: int(2),
      GameRules: compound({ keepInventory: string('true'), doDaylightCycle: string('false'), unknownRule: string('keep-me') }),
      SpawnX: int(101 + index), SpawnY: int(64 + index), SpawnZ: int(-203 - index),
      Time: tag(4, '9007199254741011'), Player: playerNbt(world),
      DragonFight: compound({ DragonKilled: tag(1, 1), ExitPortalLocation: compound({ X: int(7), Y: int(65), Z: int(-9) }) }),
      DimensionData: compound({
        '1': compound({ DragonFight: compound({ DragonKilled: tag(1, 1) }), UnknownEndData: unknownTags() }),
        '-1': compound({ DragonFight: string('not an obsolete End reference'), UnknownNetherData: unknownTags() }),
      }),
      UnknownData: unknownTags(),
    }),
    UnknownRoot: unknownTags(), DragonFight: string('unknown root sibling, outside Data'),
  });
}
function authorizedMetadata(document, world) {
  const data = document.value.Data.value;
  if (world === 'world') {
    data.SpawnX = int(0); data.SpawnY = int(73); data.SpawnZ = int(0);
  }
  delete data.DragonFight;
  delete data.DimensionData.value['1'].value.DragonFight;
  return document;
}

function fixture(t, { epoch = 'details-v3', seed = null } = {}) {
  const tempRoot = fs.realpathSync(os.tmpdir());
  const projectRoot = fs.mkdtempSync(path.join(tempRoot, 'jaspr-release-preservation-'));
  t.after(() => {
    // Resolve and bound the exact generated directory before recursive cleanup.
    const target = fs.realpathSync(projectRoot);
    assert.equal(target, projectRoot);
    assert.equal(path.dirname(target), tempRoot);
    assert.ok(path.basename(target).startsWith('jaspr-release-preservation-'));
    fs.rmSync(target, { recursive: true, force: true });
  });
  const server = path.join(projectRoot, 'server');
  const archive = path.join(projectRoot, 'world-resets', epoch + '-synthetic');
  const protectedOriginals = new Map();
  function write(relative, bytes, protect = false) {
    const file = path.join(server, relative);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, bytes);
    if (protect) protectedOriginals.set(relative, Buffer.from(bytes));
  }
  for (const world of ALL_WORLDS) {
    const folded = world === 'jaspr_backrooms';
    for (const name of ['level.dat', 'level.dat_old']) write(world + '/' + name, gzipNbt(levelNbt(world)), folded);
    write(world + '/uid.dat', Buffer.alloc(16, ALL_WORLDS.indexOf(world) + 1), true);
    write(world + '/playerdata/' + PLAYER + '.dat', gzipNbt(playerNbt(world)), true);
    write(world + '/stats/' + PLAYER + '.json', '{"stat.playOneMinute":321,"unknown":17}', true);
    write(world + '/advancements/' + PLAYER + '.json', '{"fixture:earned":{"done":true}}', true);
    write(world + '/data/map_0.dat', gzipNbt(compound({ data: unknownTags() })), true);
    if (!folded) write(world + '/data/Village.dat', gzipNbt(compound({ data: unknownTags() })));
  }
  // Region and journal bytes are synthetic sentinels; this gate hashes, not parses, them.
  for (const relative of REQUIRED_MOVED.slice(0, 3)) write(relative + '/r.0.0.mca', 'old synthetic terrain: ' + relative);
  write(REQUIRED_MOVED[3], 'old synthetic occupancy boundary');
  write('jaspr_backrooms/region/r.0.0.mca', 'Fold terrain and opened container contents', true);
  write('jaspr_backrooms/session.lock', Buffer.alloc(8, 7), true);
  write(CLAIMS, 'synthetic structures-v2 claim\nsynthetic fold-v1 claimed chest\n', true);
  write('plugins/JasprHorrorBiomes/structure-encounters-v1.bin', 'old defeated encounters', true);
  write('plugins/JasprHorrorBiomes/liminal-returns-v1/' + PLAYER + '.json', JSON.stringify({
    version: 1, worldName: 'world_nether', x: 145.5, y: 81.25, z: -97.5,
  }), true);
  write('plugins/JasprHorrorBiomes/unknown-extension.bin', Buffer.from([0, 1, 127, 255]), true);
  write('plugins/JasprApocalypse/survivors.yml', 'synthetic-player: introduced\n', true);
  write('plugins/JasprApocalypse/ruins-ledger-v1.bin', 'obsolete terrain-linked ruins');
  write('plugins/JasprApocalypse/turrets.yml', 'turrets:\n- world: world\n  x: 3\n  y: 73\n  z: 3\n');
  if (epoch === 'sparse-v5') write(EPOCH, 'surface-v4\n');
  if (epoch === 'rare-v6') write(EPOCH, 'sparse-v5\n');
  if (epoch === 'rare-v7') write(EPOCH, 'rare-v6\n');

  const manifest = reset.apply(server, archive, { epoch, seed });
  assert.equal(manifest.verified, true);
  assert.deepEqual(Object.keys(manifest.protectedHashes).sort(), [...protectedOriginals.keys()].sort());
  if (epoch === 'details-v3') for (const world of WORLDS) write(world + '/level.dat', gzipNbt(authorizedMetadata(levelNbt(world), world)));
  const verify = () => verifyOfflineFiles(projectRoot, archive);
  // Every negative starts with a passing real reset, never an already-broken fixture.
  assert.equal(verify().verified, true);
  return {
    projectRoot, server, archive, manifest, protectedOriginals, write, verify,
    metadata(world, mutate) {
      const document = levelNbt(world);
      if (world !== 'jaspr_backrooms') authorizedMetadata(document, world);
      mutate(document.value.Data.value, document.value);
      write(world + '/level.dat', gzipNbt(document));
    },
    player(world, mutate) {
      const document = playerNbt(world);
      mutate(document.value);
      write(world + '/playerdata/' + PLAYER + '.dat', gzipNbt(document));
    },
  };
}
function rejected(f, reason) {
  assert.throws(f.verify, error => error.code === 'ERR_ASSERTION' && error.message.includes(reason));
}

test('details-v3 accepts only authorized gzip NBT changes and preserves every fixture field and recovery copy', t => {
  const f = fixture(t);
  const result = f.verify();
  assert.equal(result.metadataChanges, 'spawn-and-obsolete-dragon-references-only');
  assert.equal(result.resetManifestSha256, reset.hash(path.join(f.archive, 'reset-manifest.json')));
  assert.deepEqual(result.protectedHashes, f.manifest.protectedHashes);
  for (const [relative, bytes] of f.protectedOriginals) {
    assert.deepEqual(fs.readFileSync(path.join(f.server, relative)), bytes, 'Live preservation: ' + relative);
    assert.deepEqual(fs.readFileSync(path.join(f.archive, 'protected', relative)), bytes, 'Recovery preservation: ' + relative);
  }
  for (const world of WORLDS) {
    const file = path.join(f.server, world, 'level.dat');
    assert.deepEqual([...fs.readFileSync(file).subarray(0, 2)], [0x1f, 0x8b]);
    const before = nbt(path.join(f.archive, 'metadata', world, 'level.dat'));
    assert.deepEqual(before, nbt(path.join(f.server, world, 'level.dat_old')), 'Previous metadata stays intact');
    const after = nbt(file);
    assert.deepEqual(after.UnknownRoot, UNKNOWN_VALUES);
    assert.deepEqual(after.Data.UnknownData, UNKNOWN_VALUES);
    assert.deepEqual(after.Data.DimensionData['1'].UnknownEndData, UNKNOWN_VALUES);
    assert.deepEqual(after.Data.Player, before.Data.Player);
    assert.equal(after.DragonFight, before.DragonFight);
    assert.deepEqual(after.Data.DimensionData['-1'], before.Data.DimensionData['-1']);
    assert.equal(Object.hasOwn(after.Data, 'DragonFight'), false);
    assert.equal(Object.hasOwn(after.Data.DimensionData['1'], 'DragonFight'), false);
    const expected = structuredClone(before);
    if (world === 'world') Object.assign(expected.Data, { SpawnX: 0, SpawnY: 73, SpawnZ: 0 });
    delete expected.Data.DragonFight;
    delete expected.Data.DimensionData['1'].DragonFight;
    assert.deepEqual(after, expected, world + ': full metadata, not a selected-field projection');
    for (const name of ['level.dat', 'level.dat_old']) {
      assert.deepEqual(fs.readFileSync(path.join(f.archive, 'metadata', world, name)), gzipNbt(levelNbt(world)));
    }
  }
  assert.equal(fs.readFileSync(path.join(f.server, EPOCH), 'utf8'), 'details-v3\n');
  assert.deepEqual(f.manifest.moved, f.manifest.targets);
  for (const relative of REQUIRED_MOVED) assert.ok(f.manifest.moved.includes(relative), relative);
  for (const relative of f.manifest.moved) {
    assert.equal(fs.existsSync(path.join(f.server, relative)), false);
    assert.equal(fs.existsSync(path.join(f.archive, 'terrain', relative)), true);
  }
});

test('sparse-v5 preserves level metadata byte-for-byte and requires the surface-v4 predecessor', t => {
  const f = fixture(t, { epoch: 'sparse-v5' });
  const result = f.verify();
  assert.equal(result.metadataChanges, 'none-byte-identical');
  assert.equal(result.epoch, 'sparse-v5');
  assert.equal(result.previousEpoch, 'surface-v4');
  assert.equal(fs.readFileSync(path.join(f.server, EPOCH), 'utf8'), 'sparse-v5\n');
  assert.equal(fs.readFileSync(path.join(f.archive, 'metadata', EPOCH), 'utf8'), 'surface-v4\n');
  for (const world of WORLDS) {
    assert.deepEqual(
      fs.readFileSync(path.join(f.server, world, 'level.dat')),
      fs.readFileSync(path.join(f.archive, 'metadata', world, 'level.dat')),
      world + ' metadata is unchanged during the sparse migration',
    );
  }
});

test('rare-v6 changes only the seed, safe spawn and obsolete End references while preserving player state', {timeout: 120000}, t => {
  const seed = '4184677908398476141';
  const f = fixture(t, { epoch: 'rare-v6', seed });
  const result = f.verify();
  assert.equal(result.metadataChanges, 'seed-spawn-and-obsolete-dragon-references-only');
  assert.equal(result.epoch, 'rare-v6');
  assert.equal(result.previousEpoch, 'sparse-v5');
  assert.equal(f.manifest.reseed.oldSeed, '9007199254740993');
  assert.equal(f.manifest.reseed.newSeed, seed);
  assert.equal(f.manifest.reseed.worlds, 3);
  assert.deepEqual(f.manifest.reseed.spawn, {x: 0, y: 73, z: 0});
  assert.equal(fs.readFileSync(path.join(f.server, EPOCH), 'utf8'), 'rare-v6\n');
  for (const world of WORLDS) {
    const before = nbt(path.join(f.archive, 'metadata', world, 'level.dat'));
    const after = nbt(path.join(f.server, world, 'level.dat'));
    assert.equal(after.Data.RandomSeed, seed);
    assert.equal(before.Data.RandomSeed, '9007199254740993');
    assert.deepEqual(after.Data.Player, before.Data.Player);
    assert.deepEqual(after.Data.GameRules, before.Data.GameRules);
    assert.equal(after.Data.GameType, before.Data.GameType);
  }
  for (const [relative, bytes] of f.protectedOriginals) {
    assert.deepEqual(fs.readFileSync(path.join(f.server, relative)), bytes, 'Live preservation: ' + relative);
    assert.deepEqual(fs.readFileSync(path.join(f.archive, 'protected', relative)), bytes, 'Recovery preservation: ' + relative);
  }
});

test('rare-v7 reseeds sanitized structures while preserving every player and account byte', {timeout: 120000}, t => {
  const seed = '-7357615461451535643';
  const f = fixture(t, { epoch: 'rare-v7', seed });
  const result = f.verify();
  assert.equal(result.metadataChanges, 'seed-spawn-and-obsolete-dragon-references-only');
  assert.equal(result.epoch, 'rare-v7');
  assert.equal(result.previousEpoch, 'rare-v6');
  assert.equal(f.manifest.reseed.oldSeed, '9007199254740993');
  assert.equal(f.manifest.reseed.newSeed, seed);
  assert.equal(fs.readFileSync(path.join(f.server, EPOCH), 'utf8'), 'rare-v7\n');
  for (const world of WORLDS) {
    const before = nbt(path.join(f.archive, 'metadata', world, 'level.dat'));
    const after = nbt(path.join(f.server, world, 'level.dat'));
    assert.equal(after.Data.RandomSeed, seed);
    assert.deepEqual(after.Data.Player, before.Data.Player);
    assert.deepEqual(after.Data.GameRules, before.Data.GameRules);
    assert.equal(after.Data.GameType, before.Data.GameType);
  }
  for (const [relative, bytes] of f.protectedOriginals) {
    assert.deepEqual(fs.readFileSync(path.join(f.server, relative)), bytes, 'Live preservation: ' + relative);
    assert.deepEqual(fs.readFileSync(path.join(f.archive, 'protected', relative)), bytes, 'Recovery preservation: ' + relative);
  }
});

for (const world of ALL_WORLDS) {
  for (const [name, mutate] of [
    ['inventory', data => { data.Inventory.value.values[0].Count = tag(1, 2); }],
    ['ender chest', data => { data.EnderItems.value.values[0].Count = tag(1, 22); }],
    ['saved player mode', data => { data.playerGameType = int(1); }],
    ['experience', data => { data.XpTotal = int(0); }],
  ]) test('rejects ' + world + ' tampered ' + name, t => {
    const f = fixture(t);
    f.player(world, mutate);
    rejected(f, 'Offline protected state: ' + world + '/playerdata/');
  });
  for (const [name, relative] of [
    ['UID', world + '/uid.dat'],
    ['stats', world + '/stats/' + PLAYER + '.json'],
    ['advancements', world + '/advancements/' + PLAYER + '.json'],
  ]) test('rejects ' + world + ' tampered ' + name, t => {
    const f = fixture(t);
    const changed = fs.readFileSync(path.join(f.server, relative));
    changed[0] ^= 1;
    f.write(relative, changed);
    rejected(f, 'Offline protected state: ' + relative);
  });
  for (const [name, mutate] of [
    ['seed (including a one-unit change beyond Number precision)', data => { data.RandomSeed = tag(4, '9007199254740994'); }],
    ['default gamemode', data => { data.GameType = int(1); }],
    ['gamerules', data => { data.GameRules.value.keepInventory = string('false'); }],
    ['unknown root field', (data, root) => { delete root.UnknownRoot; }],
    ['unknown nested list field', data => { data.UnknownData.value.List.value.values[1].Value = int(-8); }],
  ]) test('rejects ' + world + ' changed gzip NBT ' + name, t => {
    const f = fixture(t);
    f.metadata(world, mutate);
    rejected(f, world === 'jaspr_backrooms' ? 'Offline protected state: jaspr_backrooms/level.dat' : 'Only authorized spawn and dragon references changed in ' + world);
  });
}

for (const world of WORLDS) {
  for (const [name, mutate] of [
    ['wrong spawn (only Overworld 0,73,0 is authorized)', data => { data.SpawnX = int(999); }],
    ['obsolete root DragonFight retained', data => { data.DragonFight = levelNbt(world).value.Data.value.DragonFight; }],
    ['obsolete nested DragonFight retained', data => { data.DimensionData.value['1'].value.DragonFight = compound({ DragonKilled: tag(1, 1) }); }],
    ['unrelated DragonFight deleted', data => { delete data.DimensionData.value['-1'].value.DragonFight; }],
  ]) test('rejects ' + world + ' ' + name, t => {
    const f = fixture(t);
    f.metadata(world, mutate);
    rejected(f, 'Only authorized spawn and dragon references changed in ' + world);
  });
}

for (const [name, relative, bytes] of [
  ['Fold region', 'jaspr_backrooms/region/r.0.0.mca', 'regenerated Fold terrain'],
  ['Fold claim in the shared journal', CLAIMS, 'synthetic structures-v2 claim\nsynthetic fold-v1 UNCLAIMED chest\n'],
  ['legacy return record', 'plugins/JasprHorrorBiomes/liminal-returns-v1/' + PLAYER + '.json', '{"version":2}'],
  ['old encounter progress', 'plugins/JasprHorrorBiomes/structure-encounters-v1.bin', 'cleared encounters'],
]) test('rejects tampered ' + name, t => {
  const f = fixture(t);
  f.write(relative, bytes);
  rejected(f, 'Offline protected state: ' + relative);
});

for (const relative of ['world/playerdata/' + PLAYER + '.dat', 'jaspr_backrooms/region/r.0.0.mca', CLAIMS]) {
  test('rejects corrupted recovery copy: ' + relative, t => {
    const f = fixture(t);
    fs.appendFileSync(path.join(f.archive, 'protected', relative), 'tampered');
    rejected(f, 'Verified recovery copy: ' + relative);
  });
}

test('rejects a live terrain epoch that disagrees with the reset manifest', t => {
  const f = fixture(t);
  f.write(EPOCH, 'structures-v2\n');
  assert.throws(f.verify, { code: 'ERR_ASSERTION' });
});

for (const relative of REQUIRED_MOVED) {
  test('rejects old terrain still present on server: ' + relative, t => {
    const f = fixture(t);
    if (relative.endsWith('/region')) fs.mkdirSync(path.join(f.server, relative), { recursive: true });
    else f.write(relative, 'stale occupancy');
    rejected(f, 'Old terrain removed: ' + relative);
  });
  test('requires recorded move of pre-existing terrain: ' + relative, t => {
    const f = fixture(t);
    const manifestFile = path.join(f.archive, 'reset-manifest.json');
    const manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
    manifest.moved = manifest.moved.filter(target => target !== relative);
    fs.writeFileSync(manifestFile, JSON.stringify(manifest));
    assert.throws(f.verify, 'Release must reject an incomplete moved-terrain manifest: ' + relative);
  });
  test('requires recoverable archived terrain: ' + relative, t => {
    const f = fixture(t);
    const original = path.join(f.archive, 'terrain', relative);
    // Move only this synthetic archive entry aside: no fixture data is destroyed.
    fs.renameSync(original, original + '.missing-for-test');
    assert.throws(f.verify, 'Release must reject missing archived terrain: ' + relative);
  });
}
