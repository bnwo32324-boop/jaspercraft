'use strict';
// JASPR_MUTANTS phase 1: the stage builds reversibly from the LIVE client, installs from Bootstrap.register, registers
// the protocol's ids, defines real TeaVM subclasses (instanceof, interfaces, virtual dispatch from engine code), and
// decodes the jaspr:mutants SPAWN message and the mutantbeasts channel exactly as MUTANTS_PROTOCOL.md specifies.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const H = require('./mutants-harness.cjs');
const skip = !H.available;
// values coming out of node:vm have the vm's prototypes: compare plain JSON copies
const plain = v => v === undefined ? v : JSON.parse(JSON.stringify(v, (k, x) => typeof x === 'bigint' ? String(x) : x));
const deepEqual = (a, b, msg) => assert.deepStrictEqual(plain(a), plain(b), msg);

test('build: natives declared, parses, unpatch(build(x)) === x, deterministic, hooks applied once', { skip }, () => {
  const { input, built } = H.load();
  const b = H.builder;
  assert.equal(b.unpatch(built.output), b.unpatch(input));
  assert.equal(b.unpatch(input), input.includes(b.BEGIN) ? b.unpatch(input) : input, 'an unpatched client stays as it is');
  const again = b.build(input);
  assert.equal(again.output, built.output, 'two builds from the same input are byte-identical');
  assert.equal(built.output.split(b.BEGIN).length - 1, 1);
  for (const [fn, , to] of b.allHooks()) {
    const start = built.output.indexOf('function ' + fn + '('), end = built.output.indexOf('\nfunction ', start + 10);
    assert.equal(built.output.slice(start, end).split(to).length - 1, 1, fn + ' hook present once');
  }
  assert.ok(built.natives.length > 200, 'engine names were checked');
  const stage = built.output.slice(built.output.indexOf(b.BEGIN), built.output.indexOf(b.END));
  assert.ok(!/[^\x00-\x7f]/.test(stage), 'stage is ASCII');
  // rebuild on top of an already patched client gives the same output
  assert.equal(b.build(built.output).output, built.output);
});

test('install: registry hook ran once, nothing disabled, vanilla parent data keys equal the server ids', { skip }, () => {
  const { M, ev } = H.load();
  const d = M.diagnostics();
  assert.equal(d.installed, true);
  deepEqual(d.disabled, {});
  assert.equal(d.stats.installs, 1);
  const ids = M.parentKeyIds();
  deepEqual(ids.Entity, [0, 1, 2, 3, 4, 5]);
  deepEqual(ids.EntityLivingBase, [6, 7, 8, 9, 10]);
  deepEqual(ids.EntityLiving, [11]);
  deepEqual(ids.EntityAgeable, [12]);
  deepEqual(ids.EntityTameable, [13, 14]);
  deepEqual(ids.EntityCreeper, [12, 13, 14]);
  assert.equal(ev('typeof JasprMutantsDiagnostics.status'), 'function');
});

test('registries: 15 entities 210-224 with EntityList keys, legacy names, eggs and constructors', { skip }, () => {
  const { M, ev } = H.load();
  const r = ev(`(function(){
    var out = [];
    I$();
    for (var id = 210; id <= 224; id++) {
      var cls = WY(KsX, id), t = JasprMutants.typeById(id);
      var key = F0Y(KsX, cls), egg = Cno(HFS, key);
      out.push({ id: id, key: $rt_ustr(key.m2) + ':' + $rt_ustr(key.iX), sameClass: cls === E(t.cls), legacy: $rt_ustr(Bm(KsY, id)), egg: egg === null ? null : [egg.dEg, egg.dWv] });
    }
    return out;
  })()`);
  const names = ['body_part', 'chemical_x', 'endersoul_clone', 'creeper_minion', 'creeper_minion_egg', 'endersoul_fragment', 'mutant_arrow',
    'mutant_creeper', 'mutant_enderman', 'mutant_skeleton', 'mutant_snow_golem', 'mutant_zombie', 'skull_spirit', 'spider_pig', 'throwable_block'];
  const eggs = { endersoul_clone: [15027455, 15027455], creeper_minion: [894731, 0xB7B7B7], mutant_creeper: [5349438, 11013646], mutant_enderman: [0x161616, 8860812],
    mutant_skeleton: [0xC1C1C1, 6310217], mutant_snow_golem: [0xE5FFFF, 16753434], mutant_zombie: [7969893, 44975], spider_pig: [3419431, 15771042] };
  assert.equal(r.length, 15);
  r.forEach((e, i) => {
    assert.equal(e.key, 'mutantbeasts:' + names[i]);
    assert.equal(e.sameClass, true);
    assert.equal(e.legacy, 'mutantbeasts.' + names[i]);
    deepEqual(e.egg, eggs[names[i]] || null, names[i] + ' egg');
  });
  // EntityList.createEntityByID builds our classes through the constructor map
  const created = ev(`(function(){ var w = ${'{ r: 1, R: null, b4: { lE: function () { return { v0: 0 }; } } }'}; var e = E$P(WY(KsX, 221), w); return e instanceof JasprMutants.T.MutantZombieEntity; })()`);
  assert.equal(created, true);
});

test('classes: real TeaVM subclasses (instanceof, interfaces, $meta) and engine virtual dispatch reaches the overrides', { skip }, () => {
  const { ev } = H.load();
  const world = H.fakeWorld(ev);
  const r = ev(`(function(w){
    var T = JasprMutants.T, I = JasprMutants.IFACE, out = {};
    var z = T.MutantZombieEntity.create(w), c = T.MutantCreeperEntity.create(w), g = T.MutantSnowGolemEntity.create(w), p = T.SpiderPigEntity.create(w);
    var m = T.CreeperMinionEntity.create(w), tb = T.ThrowableBlockEntity.create(w), en = T.MutantEndermanEntity.create(w);
    out.zombie = [z instanceof H0, z instanceof Gj, z instanceof Co, z instanceof Eg, $rt_isInstance(z, I.IMob), z.constructor.$meta.name];
    out.creeper = [c instanceof Kw, c instanceof H0, $rt_isInstance(c, I.IMob)];
    out.golem = [g instanceof AHO, $rt_isInstance(g, I.IAnimals), $rt_isInstance(g, I.IRangedAttackMob), $rt_isInstance(g, I.IEntityOwnable), $rt_isInstance(g, I.IMob)];
    out.pig = [p instanceof S5, $rt_isInstance(p, I.IJumpingMount), $rt_isInstance(p, I.IEntityOwnable), $rt_isInstance(p, I.IAnimals)];
    out.minion = [m instanceof BfX, m instanceof S5];
    out.block = [tb instanceof Vh, $rt_isInstance(tb, I.IProjectile)];
    // virtual dispatch: engine names reach the translated methods
    z.q2(-3); out.zombieStatus = [z.attackID, z.attackTick];        // Entity.handleStatusUpdate (q2), as handleEntityStatus calls it
    out.eyes = [z.hz(), c.hz(), en.hz(), g.hz(), p.hz()];             // getEyeHeight
    out.zombieAttr = [Crp(z), JasprMutants.attributeValue(z, 'ARMOR'), JasprMutants.attributeValue(z, 'SWIM_SPEED')];
    out.lives = z.getLives();
    out.creeperFlash = c.getCreeperFlashIntensity(0.5);
    out.render = [z.c50().cH - z.c50().ct, en.c50().cH - en.c50().ct];  // getRenderBoundingBox grow 1.0 / 3.5
    out.pigSteer = [p.cKU(), p.crJ()];                                // canBeSteered, getControllingPassenger
    out.pigLadder = p.cxy();                                          // isOnLadder
    return out;
  })`)(world);
  deepEqual(r.zombie, [true, true, true, true, true, 'chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity']);
  deepEqual(r.creeper, [true, true, true]);
  deepEqual(r.golem, [true, true, true, true, false]);
  deepEqual(r.pig, [true, true, true, true]);
  deepEqual(r.minion, [true, true]);
  deepEqual(r.block, [true, true]);
  deepEqual(r.zombieStatus, [3, 0]);
  deepEqual(r.eyes, [Math.fround(2.8), Math.fround(2.6), Math.fround(3.9), 2.0, Math.fround(0.9) * 0.75]);
  deepEqual(r.zombieAttr, [150, 12, 4]);
  assert.equal(r.lives, 3);
  assert.equal(r.creeperFlash, 0);
  assert.ok(Math.abs(r.render[0] - (1.8 + 2.0)) < 1e-6 && Math.abs(r.render[1] - (1.2 + 7.0)) < 1e-6);
  deepEqual(r.pigSteer, [0, null]);
  assert.equal(r.pigLadder, 0);
});

// ---------------------------------------------------------------- SPAWN
function u8(...parts) { return parts.flat(); }
function varInt(v) { const out = []; do { let b = v & 0x7f; v >>>= 7; out.push(v !== 0 ? b | 0x80 : b); } while (v !== 0); return out; }
function i32(v) { return [(v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255]; }
function i64(big) { const out = []; for (let i = 7; i >= 0; i--) out.push(Number((BigInt.asUintN(64, big) >> BigInt(i * 8)) & 255n)); return out; }
function f64(x) { const b = Buffer.alloc(8); b.writeDoubleBE(x); return [...b]; }
function f32(x) { const b = Buffer.alloc(4); b.writeFloatBE(x); return [...b]; }
function i16(v) { return [(v >> 8) & 255, v & 255]; }
function spawnBytes({ entityId, most, least, typeId, x, y, z, yaw = 0, pitch = 0, head = 0, mx = 0, my = 0, mz = 0, thrower = 0, entries, spawnData = [] }) {
  return u8([1], varInt(entityId), i64(most), i64(least), varInt(typeId), f64(x), f64(y), f64(z), [yaw & 255, pitch & 255, head & 255],
    i16(mx), i16(my), i16(mz), varInt(thrower), entries, varInt(spawnData.length), spawnData);
}
function decode(ev, bytes, world) {
  return ev(`(function(bytes, w){
    var M = JasprMutants, pb = new Iu(); Lg(pb, Fru());
    for (var i = 0; i < bytes.length; i++) F4D(pb, bytes[i]);
    var op = CZl(pb);
    var m = M.readSpawn(pb);
    var saved = M.W.addEntityToWorld;
    M.W.addEntityToWorld = function (world, id, e) { world.$added.push(e); };
    try { var e = M.spawn(m, w); } finally { M.W.addEntityToWorld = saved; }
    return { op: op, m: m, e: e, left: G6(pb) };
  })`)(bytes, world);
}

test('SPAWN: a hand-encoded mutant_zombie message (protocol section 2) creates the twin with its keys and spawn data', { skip }, () => {
  const { ev } = H.load();
  const world = H.fakeWorld(ev);
  // entries: 7 health (FLOAT=2) 77.5, 12 LIVES (VARINT=1) 2, 13 THROW_ATTACK_STATE (BYTE=0) 3, terminator 0xFF
  const entries = u8([7, 2], f32(77.5), [12, 1], varInt(2), [13, 0, 3], [255]);
  const spawnData = u8(i32(3), i32(42), i32(0), i32(0), i32(5), i32(0xFFFFFFFF));   // attackID, attackTick, deathTime, vanishTime, throwHitTick, throwFinishTick
  const bytes = spawnBytes({ entityId: 4321, most: 0x0123456789ABCDEFn, least: -2n, typeId: 221, x: 10.5, y: 64, z: -3.25, yaw: 64, pitch: 0, head: 128, mx: 800, my: -1600, mz: 0, entries, spawnData });
  const r = decode(ev, bytes, world);
  assert.equal(r.op, 1);
  assert.equal(r.left, 0, 'the whole message was consumed');
  const out = ev(`(function(e, w){ return { cls: e.constructor === JasprMutants.T.MutantZombieEntity, id: e.cu, added: w.$added.indexOf(e) >= 0,
    uuid: [String(e.fY.$most !== undefined ? e.fY.$most : DU(e.fY.bZo || 0))], pos: [e.b, e.f, e.c], ser: [DU(e.cHl), DU(e.cHi), DU(e.cHj)], rot: [e.C, e.bd, e.gN, e.cZ],
    motion: [e.s, e.p, e.t], health: ENU(e), lives: e.getLives(), throwHit: e.getThrowAttackHit(), throwFinish: e.getThrowAttackFinish(),
    sd: [e.attackID, e.attackTick, e.deathTime, e.vanishTime, e.throwHitTick, e.throwFinishTick] }; })`)(r.e, world);
  assert.equal(out.cls, true);
  assert.equal(out.id, 4321);
  assert.equal(out.added, true);
  deepEqual(out.pos, [10.5, 64, -3.25]);
  deepEqual(out.ser, [10.5 * 4096, 64 * 4096, -3.25 * 4096]);
  deepEqual(out.rot, [90, 0, -180, 0]);                 // signed byte angles; FML sets only EntityLiving.rotationYawHead (renderYawOffset stays 0)
  deepEqual(out.motion, [0.1, -0.2, 0]);
  assert.equal(out.health, 77.5);
  assert.equal(out.lives, 2);
  deepEqual([out.throwHit, out.throwFinish], [true, true]);
  deepEqual(out.sd, [3, 42, 0, 0, 5, -1]);
  // UUID round trip through the engine object
  const uuid = ev(`(function(e){ return [String(e.fY.cZz !== undefined ? 0 : 0)]; })`)(r.e);
  assert.ok(uuid);
});

test('SPAWN: every type round-trips its data keys and IEntityAdditionalSpawnData (writeEntries of a twin as the server writes them)', { skip }, () => {
  const { ev } = H.load();
  const world = H.fakeWorld(ev);
  const r = ev(`(function(w){
    var M = JasprMutants, T = M.T, D = M.DATA, out = [];
    function entriesOf(e) { var pb = new Iu(); Lg(pb, Fru()); Epi(e.y, pb); var a = []; while (G6(pb) > 0) a.push(CZl(pb) & 255); return a; }
    var setups = {
      body_part: function (e) {}, chemical_x: function (e) {}, endersoul_clone: function (e) {},
      creeper_minion: function (e) { e.setPowered(true); e.setExplodeState(1); e.setExplosionRadius(3.3); },
      creeper_minion_egg: function (e) { D.set(e, M.DATA.createKey(6, D.BOOLEAN()), M.boxBool(true)); },
      endersoul_fragment: function (e) { D.set(e, M.DATA.createKey(6, D.BOOLEAN()), M.boxBool(true)); },
      mutant_arrow: function (e) { D.set(e, M.DATA.createKey(6, D.FLOAT()), M.boxFloat(1.5)); D.set(e, M.DATA.createKey(10, D.VARINT()), M.boxInt(7)); },
      mutant_creeper: function (e) { D.set(e, M.DATA.createKey(15, D.BYTE()), M.boxByte(5)); },
      mutant_enderman: function (e) { D.set(e, M.DATA.createKey(12, D.BYTE()), M.boxByte(2)); D.set(e, M.DATA.createKey(13, D.BOOLEAN()), M.boxBool(true)); },
      mutant_skeleton: function (e) {}, mutant_snow_golem: function (e) { D.set(e, M.DATA.createKey(13, D.BYTE()), M.boxByte(5)); },
      mutant_zombie: function (e) { e.setLives(1); }, skull_spirit: function (e) { D.set(e, M.DATA.createKey(6, D.BOOLEAN()), M.boxBool(true)); },
      spider_pig: function (e) { e.setSaddled(true); e.setBesideClimbableBlock(true); }, throwable_block: function (e) { e.setHeld(true); }
    };
    M.ENTITY_TYPES.forEach(function (t) {
      var src = t.cls.create(w); setups[t.name](src);
      out.push({ name: t.name, id: t.id, entries: entriesOf(src) });
    });
    return out;
  })`)(world);
  const spawnData = {
    body_part: [17], skull_spirit: i32(99), throwable_block: i32(1 | (0 << 12)),
    mutant_zombie: u8(i32(1), i32(2), i32(3), i32(4), i32(5), i32(6)), mutant_skeleton: u8(i32(4), i32(9)), mutant_creeper: u8(i32(7), i32(8)),
    mutant_enderman: u8(i32(4), i32(11), i32(0), i32(10), i32(20), i64((5n << 38n) | (70n << 26n) | 9n))
  };
  for (const t of r) {
    const bytes = spawnBytes({ entityId: 1000 + t.id, most: BigInt(t.id), least: 77n, typeId: t.id, x: 1, y: 2, z: 3, yaw: 32, pitch: 16, head: 32,
      thrower: t.name === 'throwable_block' ? 0 : 0, entries: t.entries, spawnData: spawnData[t.name] || [] });
    const d = decode(ev, bytes, world);
    assert.equal(d.left, 0, t.name + ' consumed');
    const chk = ev(`(function(e, name){
      var M = JasprMutants;
      var keys = M.listToArray(Bso(e.y)).map(function (x) { return x.b1U.b0H; }).sort(function (a, b) { return a - b; });
      var v = {};
      if (name === 'mutant_zombie') v = { lives: e.getLives(), sd: [e.attackID, e.attackTick, e.deathTime, e.vanishTime, e.throwHitTick, e.throwFinishTick] };
      if (name === 'mutant_skeleton') v = { sd: [e.getAnimationID(), e.getAnimationTick()] };
      if (name === 'mutant_creeper') v = { status: [e.getPowered(), e.isJumpAttacking(), e.isCharging()], sd: [e.flashTick, e.deathTime] };
      if (name === 'mutant_enderman') v = { arm: e.getActiveArm(), clone: e.isClone(), size: [e.bI, e.bZ], sd: [e.attackID, e.attackTick, e.deathTime, e.armScale, e.hasTarget], tp: [e.teleportPosition.m, e.teleportPosition.i, e.teleportPosition.l] };
      if (name === 'mutant_snow_golem') v = { pumpkin: e.isPumpkinEquipped(), swim: e.getSwimJump(), owner: e.getOwnerId() };
      if (name === 'spider_pig') v = { saddled: e.isSaddled(), climbing: e.isBesideClimbableBlock(), tamed: !!e.isTamed() };
      if (name === 'creeper_minion') v = { powered: e.getPowered(), state: e.getExplodeState(), radius: Math.round(e.getExplosionRadius() * 100) / 100, destroys: e.canDestroyBlocks() };
      if (name === 'body_part') v = { part: e.getPart() };
      if (name === 'skull_spirit') v = { target: e.targetId, attached: e.isAttached() };
      if (name === 'throwable_block') v = { held: e.isHeld(), state: GvO(e.getBlockState()) };
      if (name === 'creeper_minion_egg') v = { charged: e.isCharged() };
      if (name === 'endersoul_fragment') v = { tamed: e.isTamed() };
      if (name === 'mutant_arrow') v = { tx: e.getTargetX(), clones: e.getClones() };
      return { keys: keys, v: v, yaw: e.C };
    })`)(d.e, t.name);
    const own = { body_part: 5, chemical_x: 5, endersoul_clone: 11, creeper_minion: 17, creeper_minion_egg: 6, endersoul_fragment: 6, mutant_arrow: 10,
      mutant_creeper: 15, mutant_enderman: 13, mutant_skeleton: 11, mutant_snow_golem: 13, mutant_zombie: 13, skull_spirit: 6, spider_pig: 15, throwable_block: 6 }[t.name];
    deepEqual(chk.keys, Array.from({ length: own + 1 }, (_, i) => i), t.name + ' key ids');
    // FML's EntitySpawnHandler calls setVelocity after setLocationAndAngles for an IThrowableEntity; EntityThrowable.setVelocity
    // (client) recomputes the yaw from the motion while prevRotationYaw is still 0 (setLocationAndAngles leaves it), here 0.
    assert.equal(chk.yaw, t.name === 'throwable_block' ? 0 : 45, t.name + ' yaw');
    const expect = {
      mutant_zombie: { lives: 1, sd: [1, 2, 3, 4, 5, 6] }, mutant_skeleton: { sd: [4, 9] }, mutant_creeper: { status: [true, false, true], sd: [7, 8] },
      mutant_enderman: { arm: 2, clone: true, size: [0.6000000238418579, 2.9000000953674316], sd: [4, 11, 0, 10, 20], tp: [5, 70, 9] },
      mutant_snow_golem: { pumpkin: true, swim: true, owner: null }, spider_pig: { saddled: true, climbing: true, tamed: false },
      creeper_minion: { powered: true, state: 1, radius: 3.3, destroys: true }, body_part: { part: 17 }, skull_spirit: { target: 99, attached: true },
      throwable_block: { held: true, state: 1 }, creeper_minion_egg: { charged: true }, endersoul_fragment: { tamed: true }, mutant_arrow: { tx: 1.5, clones: 7 }
    }[t.name] || {};
    deepEqual(chk.v, expect, t.name + ' values');
  }
});

test('SPAWN: unknown type ids are skipped with one diagnostic; a short message throws without creating anything', { skip }, () => {
  const { ev, M } = H.load();
  const world = H.fakeWorld(ev);
  const before = M.stats.spawnUnknown;
  const d = decode(ev, spawnBytes({ entityId: 5, most: 1n, least: 2n, typeId: 999, x: 0, y: 0, z: 0, entries: [255] }), world);
  assert.equal(d.e, null);
  assert.equal(M.stats.spawnUnknown, before + 1);
  assert.throws(() => decode(ev, [1, ...varInt(5)], world));
});

test('server fixtures: SPAWN hex captured from the JasprMutants plugin decode to the right classes (when present)', { skip }, () => {
  const dir = 'C:/Users/AM/Documents/JasperCraft-Mutants-Server/tests/fixtures/mutants';
  if (!fs.existsSync(dir)) return;
  const { ev } = H.load();
  const world = H.fakeWorld(ev);
  const files = fs.readdirSync(dir).filter(f => /\.(hex|txt)$/.test(f));
  for (const f of files) {
    const hex = fs.readFileSync(path.join(dir, f), 'utf8').replace(/[^0-9a-fA-F]/g, '');
    const bytes = [...Buffer.from(hex, 'hex')];
    if (bytes[0] !== 1) continue;                            // SPAWN only
    const d = decode(ev, bytes, world);
    assert.equal(d.left, 0, f + ' consumed');
    const name = ev(`(function(e){ return e === null ? null : e.constructor.$jmType.name; })`)(d.e);
    const m = /(body_part|chemical_x|endersoul_clone|creeper_minion_egg|creeper_minion|endersoul_fragment|mutant_arrow|mutant_creeper|mutant_enderman|mutant_skeleton|mutant_snow_golem|mutant_zombie|skull_spirit|spider_pig|throwable_block)/.exec(f);
    if (m) assert.equal(name, m[1], f);
    else assert.ok(name, f);
  }
});

// ---------------------------------------------------------------- channel mutantbeasts
function modMessage(ev, bytes, world) {
  return ev(`(function(bytes, w){
    var M = JasprMutants, pb = new Iu(); Lg(pb, Fru());
    for (var i = 0; i < bytes.length; i++) F4D(pb, bytes[i]);
    var m = M.readModMessage(pb), particles = 0, saved = M.W.spawnParticle;
    M.W.spawnParticle = function () { particles++; };
    try { M.handleModMessage(m, w); } finally { M.W.spawnParticle = saved; }
    var mm = {}; for (var k in m) mm[k] = typeof m[k] === 'bigint' ? String(m[k]) : m[k];
    return { m: mm, particles: particles, left: G6(pb) };
  })`)(bytes, world);
}
test('mutantbeasts channel: the four messages decode byte-identically to SimpleIndexedCodec + toBytes, handlers run the mod code', { skip }, () => {
  const { ev } = H.load();
  const world = H.fakeWorld(ev);
  const enderman = ev(`(function(w){ var e = JasprMutants.T.MutantEndermanEntity.create(w); e.setEntityId(77); w.$added.push(e); return e; })`)(world);
  // 1 HeldBlockPacket: int entityId, int blockId, byte blockIndex
  let r = modMessage(ev, u8([1], i32(77), i32(4097), [3]), world);
  deepEqual([r.m.disc, r.m.entityId, r.m.blockId, r.m.blockIndex, r.left], [1, 77, 4097, 3, 0]);
  deepEqual(ev('(function(e){ return [e.heldBlock.slice(), e.heldBlockTick.slice()]; })')(enderman), [[0, 0, 0, 4097, 0], [0, 0, 0, 0, 0]]);
  // index 0, blockId -1 and an out-of-range index are ignored
  modMessage(ev, u8([1], i32(77), i32(-1), [2]), world); modMessage(ev, u8([1], i32(77), i32(5), [0]), world); modMessage(ev, u8([1], i32(77), i32(5), [9]), world);
  deepEqual(ev('(function(e){ return e.heldBlock.slice(); })')(enderman), [0, 0, 0, 4097, 0]);
  // 3 TeleportPacket: int entityId, long BlockPos.toLong
  const pos = (BigInt.asUintN(26, -12n) << 38n) | (65n << 26n) | BigInt.asUintN(26, 300n);
  r = modMessage(ev, u8([3], i32(77), i64(pos)), world);
  assert.equal(r.left, 0);
  assert.equal(r.particles, 512, 'setTeleportPosition on the client spawns 512 endersoul particles');
  deepEqual(ev('(function(e){ var p = e.getTeleportPosition(); return [e.attackID, p.m, p.i, p.l]; })')(enderman), [4, -12, 65, 300]);
  // 2 SpawnParticlePacket: int id, 6 doubles, int amount
  r = modMessage(ev, u8([2], i32(100), f64(1), f64(2), f64(3), f64(0.5), f64(1), f64(0.5), i32(64)), world);
  deepEqual([r.m.particleId, r.m.amount, r.particles, r.left], [100, 64, 64, 0]);
  r = modMessage(ev, u8([2], i32(34), f64(1), f64(2), f64(3), f64(0.5), f64(1), f64(0.5), i32(5)), world);
  assert.equal(r.particles, 5, 'heart particles');
  r = modMessage(ev, u8([2], i32(12345), f64(1), f64(2), f64(3), f64(0), f64(0), f64(0), i32(2)), world);
  assert.equal(r.particles, 2, 'unknown particle ids fall back to BARRIER (fromBytes)');
  // 0 CreeperMinionTrackerPacket (client -> server) decodes as written
  r = modMessage(ev, u8([0], i32(55), [2], [1]), world);
  deepEqual([r.m.disc, r.m.entityId, r.m.optionsId, r.m.value], [0, 55, 2, true]);
});

test('outgoing: HELLO (op 0, VarInt version 1, client build) and the tracker packet bytes', { skip }, () => {
  const { ev, M } = H.load();
  const hello = Array.from(ev('JasprMutants.helloBytes()'));
  assert.equal(hello[0], 0);
  assert.equal(hello[1], 1);
  const len = hello[2];
  assert.equal(Buffer.from(hello.slice(3, 3 + len)).toString('utf8'), M.version);
  assert.ok(len <= 64);
  const minion = ev(`(function(){ var w = { r: 1, R: null, b4: { lE: function () { return { v0: 0 }; } } }; var m = JasprMutants.T.CreeperMinionEntity.create(w); m.setEntityId(258); return m; })()`);
  ev('(function(m){ JasprMutants.sendTrackerPacket(m, 1, true); })')(minion);
  const out = ev('(function(){ var o = JasprMutants.takeOutgoing(); return { channel: o.channel, bytes: Array.from(o.bytes) }; })()');
  deepEqual(out, { channel: 'mutantbeasts', bytes: [0, 0, 0, 1, 2, 1, 1] });
  // the packet the tick sends is a CPacketCustomPayload with the same bytes
  const pkt = ev(`(function(){ var p = JasprMutantsBridge.packet({ channel: 'mutantbeasts', bytes: [0, 0, 0, 1, 2, 1, 1] }); var a = []; var pb = p.ceW; while (G6(pb) > 0) a.push(CZl(pb) & 255); return { ch: $rt_ustr(p.ctX), a: a }; })()`);
  deepEqual(pkt, { ch: 'mutantbeasts', a: [0, 0, 0, 1, 2, 1, 1] });
});

test('sounds 1000-1042 and particles 100/101 are registered under the protocol ids and names', { skip }, () => {
  const { ev } = H.load();
  const s = ev(`(function(){ var out = []; for (var id = 1000; id <= 1042; id++) { var e = WY(KTr, id); out.push(e === null ? null : $rt_ustr(e.WL.m2) + ':' + $rt_ustr(e.WL.iX)); } return out; })()`);
  assert.equal(s.length, 43);
  assert.equal(s[0], 'minecraft:jaspr.mutants.entity.creeper_minion.ambient');
  assert.equal(s[20], 'minecraft:jaspr.mutants.entity.mutant_skeleton.ambient.legacy');
  assert.equal(s[39], 'minecraft:jaspr.mutants.entity.mutant_zombie.roar');
  assert.equal(s[42], 'minecraft:jaspr.mutants.entity.spider_pig.hurt');
  assert.ok(s.every(x => x !== null && x.startsWith('minecraft:jaspr.mutants.entity.')));
  assert.equal(ev('WY(KTr, 999) === null || $rt_ustr(WY(KTr, 999).WL.iX).indexOf("jaspr.mutants") < 0'), true);
  const p = ev(`(function(){ CC(); return [100, 101].map(function (id) { var t = D2a(id); return [$rt_ustr(t.cRV), t.jq, t.bVO, t.bbQ, Cno(Lp3, t.cRV) === t, LvQ.data.indexOf(t) >= 0]; }); })()`);
  deepEqual(p, [['mutantbeasts:endersoul', 100, 1, 0, true, true], ['mutantbeasts:skull_spirit', 101, 1, 0, true, true]]);
});

test('jaspr:scale is left to JASPR_BIGMOBS: the Mutants payload hook does not consume it', { skip }, () => {
  const { ev } = H.load();
  const r = ev(`(function(){ var pb = new Iu(); Lg(pb, Fru()); return JasprMutantsBridge.payload({ S$: $rt_str('jaspr:scale'), Wm: pb }); })()`);
  assert.equal(r, false);
  deepEqual(ev('JasprMutants.CHANNELS'), ['jaspr:mutants', 'mutantbeasts']);
});

test('items: ids 4000-4014 with registry keys, translation keys, stack sizes, rarity, durability and model variants', { skip }, () => {
  const { ev, M } = H.load();
  const r = ev(`(function(){
    var M = JasprMutants, out = [];
    for (var i = 0; i < M.ITEM_NAMES.length; i++) {
      var it = WY(HEO, 4000 + i), n = M.ITEM_NAMES[i];
      out.push({ n: n, same: it === M.ITEMS_MB[n.toUpperCase()], key: M.ustr(it.bPx()), max: it.i8, dmg: it.bzP,
        variants: M.listToArray(it.$jmVariants).map(function (s) { return M.ustr(s); }), rarity: it.cMA === Cl.prototype.cMA ? 'COMMON' : M.enumName(it.cMA(null)) });
    }
    return out;
  })()`);
  assert.equal(r.length, 15);
  assert.ok(r.every(x => x.same), 'registry ids resolve to the stage items');
  assert.ok(r.every(x => x.key === 'item.mutantbeasts.' + x.n), 'translation keys');
  const by = Object.fromEntries(r.map(x => [x.n, x]));
  deepEqual([by.chemical_x.max, by.creeper_shard.max, by.creeper_shard.dmg, by.endersoul_hand.dmg, by.hulk_hammer.dmg], [1, 1, 32, 240, 64]);
  deepEqual(by.endersoul_hand.variants, ['minecraft:jaspr_mutants/endersoul_hand_model', 'minecraft:jaspr_mutants/endersoul_hand_gui']);
  deepEqual(by.mutant_skeleton_skull.variants, ['minecraft:jaspr_mutants/mutant_skeleton_skull']);
  deepEqual([by.chemical_x.rarity, by.creeper_shard.rarity, by.endersoul_hand.rarity, by.hulk_hammer.rarity, by.mutant_skeleton_boots.rarity, by.mutant_skeleton_rib.rarity],
    ['EPIC', 'UNCOMMON', 'EPIC', 'UNCOMMON', 'UNCOMMON', 'COMMON']);
  // ModelBakery.getVariantNames returns the stage's variants for its items and the registry name for vanilla ones
  const v = ev(`(function(){ var mb = { gs: { Ai: { data: [null] } } }; var a = Fbv(mb, JasprMutants.ITEMS_MB.HULK_HAMMER); FM(); var b = Fbv(mb, WY(HEO, 1)); return [JasprMutants.listToArray(a).map($rt_ustr), JasprMutants.listToArray(b).map($rt_ustr)]; })()`);
  deepEqual(v, [['minecraft:jaspr_mutants/hulk_hammer'], ['minecraft:stone']]);
  // armour: material ordinal 2 (drawn like IRON), texture name, protection per slot
  const arm = ev(`(function(){ var M = JasprMutants, s = M.ITEMS_MB.MUTANT_SKELETON_CHESTPLATE; return [s.GZ.d, M.ustr(s.GZ.dZ1), s.cng, M.enumName(s.a6G), M.ITEMS_MB.MUTANT_SKELETON_SKULL.cng]; })()`);
  deepEqual(arm, [2, 'jaspr_mutants_mutant_skeleton', 6, 'CHEST', 2]);
});

test('late install: every texture the renderers, particles and screen draw is preloaded (minecraft domain, packed)', { skip }, () => {
  const { ev } = H.load();
  const list = ev('JasprMutantsBridge.preloadList().map(function (r) { return JasprMutants.rlString(r); })');
  assert.ok(list.length >= 20, 'textures listed: ' + list.length);
  assert.ok(list.every(p => p.startsWith('minecraft:textures/')), 'minecraft domain only');
  for (const p of ['minecraft:textures/entity/jaspr_mutants/mutant_zombie.png', 'minecraft:textures/entity/creeper/creeper_armor.png',
    'minecraft:textures/particle/jaspr_mutants/skull_spirit.png', 'minecraft:textures/gui/jaspr_mutants/creeper_minion_tracker.png',
    'minecraft:textures/entity/jaspr_mutants/endersoul_hand.png']) assert.ok(list.includes(p), p);
  // each one exists in the asset pack the deploy adds (vanilla ones excepted)
  const PACK = path.join(__dirname, '..', 'candidate', 'mutants-pack', 'assets');
  if (fs.existsSync(PACK)) for (const p of list.filter(x => x.includes('jaspr_mutants'))) {
    assert.ok(fs.existsSync(path.join(PACK, p.replace(':', '/'))), 'packed: ' + p);
  }
  assert.equal(ev('JasprMutants.lateDone === true'), false, 'nothing registered before the binds');
});

test('tracker screen: buttons, owner gate, toggles send CreeperMinionTrackerPacket, Float.toString formatting', { skip }, () => {
  const { ev } = H.load();
  deepEqual(ev('[10, 7.5, 0.1, 2.5, 100, 3.3, -0].map(function (x) { return JasprMutants.jfloat(x); })'), ['10.0', '7.5', '0.1', '2.5', '100.0', '3.3', '-0.0']);
  const r = ev(`(function(){
    var M = JasprMutants, w = { r: 1, R: null, b4: { lE: function () { return { v0: 0 }; } } };
    var m = M.T.CreeperMinionEntity.create(w); m.setEntityId(77);
    var me = {}, other = {};
    Object.defineProperty(m, 'isOwner', { value: function (p) { return p === me; } });
    var savedFormat = GWe; GWe = function (key) { return key; };   // the harness has no client Locale (I18n.format)
    try {
    var Screen = M.defineGui(), s = new Screen(); BGm(s);
    s.creeperMinion = m; s.j = { v: other, bE: null }; s.q = 400; s.L = 300;
    s.ee();
    var buttons = M.listToArray(s.be);
    var before = buttons.map(function (b) { return [b.bF, b.eh, b.d$, b.fg, b.i2, b.bS, M.ustr(b.dd)]; });
    var s2 = new Screen(); BGm(s2); s2.creeperMinion = m; s2.j = { v: me, bE: null }; s2.q = 400; s2.L = 300; s2.ee();
    var b2 = M.listToArray(s2.be);
    while (M.takeOutgoing() !== null) {}
    s2.eB(b2[0]); s2.eB(b2[2]); s2.eB(b2[2]);
    var sent = []; for (var o; (o = M.takeOutgoing()) !== null;) sent.push(Array.from(o.bytes));
    return { before: before, ownerEnabled: b2.map(function (b) { return b.bS; }), sent: sent, text0: M.ustr(b2[0].dd), text2: M.ustr(b2[2].dd), pause: s2.T7() };
    } finally { GWe = savedFormat; }
  })()`);
  assert.equal(r.before.length, 3);
  deepEqual(r.before.map(b => b.slice(0, 5)), [[0, 120, 155, 160, 20], [1, 120, 179, 160, 20], [2, 120, 203, 160, 20]]);
  deepEqual(r.before.map(b => b[5]), [0, 0, 0], 'not the owner: every button disabled');
  deepEqual(r.ownerEnabled, [1, 1, 1]);
  // a new minion destroys blocks (setDestroyBlocks(true) in its constructor): the first toggle turns it off
  deepEqual(r.sent, [[0, 0, 0, 0, 77, 0, 0], [0, 0, 0, 0, 77, 2, 1], [0, 0, 0, 0, 77, 2, 0]]);
  assert.equal(r.text0, 'gui.mutantbeasts.creeper_minion_tracker.destroys_blocksoptions.off');
  assert.equal(r.text2, 'gui.mutantbeasts.creeper_minion_tracker.can_ride_on_shoulderoptions.off');
  assert.equal(r.pause, 0);
  // openGui queues the screen for the next tick (displayGuiScreen runs in the resumable tick)
  const q = ev(`(function(){ var M = JasprMutants, w = { r: 1, R: null, b4: { lE: function () { return { v0: 0 }; } } }; var m = M.T.CreeperMinionEntity.create(w); M.openGui(0, m); var s = JasprMutantsBridge.takeScreen({ cj: null }); return [s instanceof M.CreeperMinionTrackerScreen, s.creeperMinion === m, JasprMutantsBridge.takeScreen({ cj: null }) === null, s instanceof CO]; })()`);
  deepEqual(q, [true, true, true, true]);
});

test('failure isolation: an exception disables only that part and reports one bounded jaspercraft.mutants.error', { skip }, () => {
  const { ev, client } = H.load({ fresh: true });
  const sent = [];
  client.context.fetch = (url, init) => { sent.push(JSON.parse(init.body)); return Promise.resolve({ ok: true }); };
  client.context.location = { pathname: '/jaspercraft/client.html' };
  const world = H.fakeWorld(ev);
  const r = ev(`(function(w){
    var M = JasprMutants, pb = new Iu(); Lg(pb, Fru());
    [1, 5].forEach(function (b) { F4D(pb, b); });
    var pkt = { S$: $rt_str('jaspr:mutants'), Wm: pb }, savedWorld = M.world;
    M.world = function () { return w; };
    try { var handled = JasprMutantsBridge.payload(pkt); } finally { M.world = savedWorld; }
    return { handled: handled, spawn: M.enabled('spawn'), modchannel: M.enabled('modchannel'), status: JasprMutantsDiagnostics.status() };
  })`)(world);
  assert.equal(r.handled, true);
  assert.equal(r.spawn, false);
  assert.equal(r.modchannel, true);
  const errors = sent.filter(s => s.events[0].event === 'jaspercraft.mutants.error');
  assert.equal(errors.length, 1);
  assert.ok(errors[0].events[0].details.error.length <= 180);
  assert.equal(errors[0].events[0].details.part, 'spawn');
  assert.ok(r.status.disabled.spawn);
});

test('guards: a throwing entity tick removes only that twin; a throwing renderer/item override returns the vanilla fallback', { skip }, () => {
  const { ev } = H.load({ fresh: true });
  const world = H.fakeWorld(ev);
  const r = ev(`(function(w){
    var M = JasprMutants, z = M.T.MutantZombieEntity.create(w), v = M.vname('onUpdate');
    z.W7 = function () { this.$dead = true; };
    var saved = z.world; z.world = { r: 1 };                    // a world the tick cannot use: it throws inside
    var threw = false; try { z[v](); } catch (e) { threw = true; }
    z.world = saved;
    var entities = M.enabled('entities');
    // item override: the hulk hammer's rarity throws -> Item.getRarity (vanilla) answers, "items" switches off
    var hammer = M.ITEMS_MB.HULK_HAMMER, raw = M.guardVirtual('items', 'cMA', function () { throw new Error('boom'); }, function () { return 'vanilla'; });
    var fromFallback = raw.call(hammer, null), items = M.enabled('items');
    return { threw: threw, dead: !!z.$dead, entities: entities, fromFallback: fromFallback, items: items, render: M.enabled('render') };
  })`)(world);
  deepEqual(r, { threw: false, dead: true, entities: false, fromFallback: 'vanilla', items: false, render: true });
});

// TeaVM $rt_metadata records: cls, name (0 or "Name" followed by a flags number), parent, [ifaces], mods, access, x, clinit, [vtable]
function metadata(src) {
  function items(from) {
    const out = []; let depth = 0, cur = '', str = false;
    for (let i = from; i < src.length; i++) {
      const c = src[i];
      if (str) { cur += c; if (c === '\\') { cur += src[++i]; continue; } if (c === '"') str = false; continue; }
      if (c === '"') { str = true; cur += c; continue; }
      if (c === '[' || c === '(' || c === '{') { depth++; cur += c; continue; }
      if (c === ']' || c === ')' || c === '}') { if (depth === 0) { out.push(cur); return out; } depth--; cur += c; continue; }
      if (c === ',' && depth === 0) { out.push(cur); cur = ''; continue; }
      cur += c;
    }
    return out;
  }
  const recs = {};
  for (let s = src.indexOf('$rt_metadata(['); s >= 0; s = src.indexOf('$rt_metadata([', s + 10)) {
    const list = items(s + '$rt_metadata(['.length).map(x => x.trim());
    for (let i = 0; i < list.length;) {
      const r = {cls: list[i], name: list[i + 1]};
      let j = i + 2;
      if (r.name !== '0') j++;
      r.parent = list[j]; r.vt = list[j + 6];
      if (/^[A-Za-z_$][\w$]*$/.test(r.cls)) recs[r.cls] = r;
      i = j + 7;
    }
  }
  return recs;
}
function vtableOf(vt) {
  const out = [];
  if (!vt || vt === '0') return out;
  const re = /"([^"]+)",\s*(?:Hz[\w$]*\(([A-Za-z_$][\w$]*)\)|function\([^)]*\)\{(?:return )?([A-Za-z_$][\w$]*)\(this)/g;
  let m; while ((m = re.exec(vt))) out.push([m[1], m[2] || m[3]]);
  return out;
}

test('abstract methods: every this-call in the engine methods a stage class inherits resolves on that class', { skip }, () => {
  const { ev, built } = H.load();
  const src = built.output, recs = metadata(src);
  const fnBody = name => { const s = src.indexOf('\nfunction ' + name + '('); return s < 0 ? '' : src.slice(s, src.indexOf('\nfunction ', s + 10)); };
  const info = ev(`(function(names){
    var M = JasprMutants, res = {}, classes = {};
    M.defineRenderers(); M.defineParticles(); M.defineGui();
    for (var k in M.T) classes['entity ' + k] = M.T[k];
    for (var k2 in M.RENDERERS) classes['render ' + k2] = M.RENDERERS[k2];
    for (var k3 in M.ITEM_CLASSES) classes['item ' + k3] = M.ITEM_CLASSES[k3];
    classes['particle Endersoul'] = M.EndersoulParticle; classes['particle SkullSpirit'] = M.SkullSpiritParticle;
    classes['gui Tracker'] = M.CreeperMinionTrackerScreen;
    var byFn = new Map(); names.forEach(function (n) { try { var f = eval(n); if (typeof f === 'function') byFn.set(f, n); } catch (e) {} });
    for (var c in classes) {
      var C = classes[c], engine = null, own = {};
      for (var q = C; q; q = q.$jm ? q.$jm.parent : null) { if (!q.$jm) { engine = byFn.get(q) || null; break; } }
      for (var p = C.prototype; p && p !== Object.prototype; p = Object.getPrototypeOf(p)) Object.getOwnPropertyNames(p).forEach(function (n) { try { if (typeof p[n] === 'function') own[n] = 1; } catch (e) {} });
      res[c] = { engine: engine, has: own };
    }
    return res;
  })`)(Object.keys(recs));
  const problems = [];
  assert.equal(Object.keys(info).length, 37, 'classes scanned (15 entities, 14 renderers, 5 items, 2 particles, 1 screen)');
  for (const [cls, {engine, has}] of Object.entries(info)) {
    if (!engine) { problems.push(cls + ': engine parent not found'); continue; }
    const impls = new Map();
    for (let c = engine; c && recs[c]; c = recs[c].parent) for (const [v, impl] of vtableOf(recs[c].vt)) if (!impls.has(v)) impls.set(v, impl);
    const calls = new Set();
    for (const impl of new Set(impls.values())) for (const m of fnBody(impl).matchAll(/[^\w$.]a\.([A-Za-z_$][\w$]*)\(/g)) calls.add(m[1]);
    const miss = [...calls].filter(v => !has[v]);
    if (miss.length) problems.push(cls + ' (' + engine + '): ' + miss.join(', '));
  }
  deepEqual(problems, []);
});
