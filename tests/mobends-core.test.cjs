'use strict';
// Mo' Bends core (client-mods/mobends/mobends-core.js) driven by fake entities: every bender's controller runs
// through its states (stand, walk, sprint, sneak, jump, fall, swim, climb, ride, fly, elytra, sleep, attacks, bow,
// spider IK/crawl/death, squid, the wolf's keyframe animator) and must keep every part finite and normalised.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createJasprMoBendsCore } = require('../client-mods/mobends/mobends-core.js');

const assetsCtx = {};
vm.createContext(assetsCtx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../client-mods/mobends/mobends-assets.js'), 'utf8'), assetsCtx);
const ASSETS = assetsCtx.JasprMoBendsAssets;

const AIR = { kind: 'air' }, SWORD = { kind: 'sword' }, BOW = { kind: 'bow' }, APPLE = { kind: 'food' }, TORCH = { kind: 'torch' }, PICK = { kind: 'tool' };
function stack(item) { return { item }; }
function makeEntity(extra) {
  return Object.assign({ id: 7, x: 0, y: 64, z: 0, px: 0, py: 64, pz: 0, mx: 0, my: 0, mz: 0, yaw: 0, pyaw: 0, pitch: 0, ppitch: 0, body: 0, pbody: 0, head: 0, phead: 0,
    limbSwing: 0, limbSwingAmount: 0, plimbSwingAmount: 0, swing: 0, swinging: false, ticks: 0, height: 1.8, child: false, riding: null, sneaking: false,
    sprinting: false, water: false, ladder: false, right: true, main: stack(AIR), off: stack(AIR), active: stack(AIR), useCount: 0, useMax: 0, activeMain: true,
    alive: true, sleeping: false, elytra: 0, flying: false, health: 20, living: true, sitting: false, interest: 0, shake: 0, tail: 0.6, squid: 0, psquid: 0,
    climbable: false, onGround: true, world: true }, extra || {});
}
function makeN(log) {
  const gl = { translate() { log.gl++; }, rotate() { log.gl++; }, scale() { log.gl++; }, push() { log.push++; }, pop() { log.pop++; }, color() {},
    multQuat(x, y, z, w) { log.gl++; const l = Math.hypot(x, y, z, w); if (!(Math.abs(l - 1) < 1e-6)) log.badQuat++; } };
  const E = {
    id: e => e.id, posX: e => e.x, posY: e => e.y, posZ: e => e.z, prevPosX: e => e.px, prevPosY: e => e.py, prevPosZ: e => e.pz,
    motionX: e => e.mx, motionY: e => e.my, motionZ: e => e.mz, rotationYaw: e => e.yaw, prevRotationYaw: e => e.pyaw, rotationPitch: e => e.pitch, prevRotationPitch: e => e.ppitch,
    renderYawOffset: e => e.body, prevRenderYawOffset: e => e.pbody, rotationYawHead: e => e.head, prevRotationYawHead: e => e.phead,
    limbSwing: e => e.limbSwing, limbSwingAmount: e => e.limbSwingAmount, prevLimbSwingAmount: e => e.plimbSwingAmount,
    getSwingProgress: e => e.swing, swingProgressField: e => e.swing, isSwingInProgress: e => e.swinging, ticksExisted: e => e.ticks, height: e => e.height,
    hasWorld: e => e.world, isChild: e => e.child, isRiding: e => e.riding !== null, ridingEntity: e => e.riding, isLiving: x => !!(x && x.living),
    isSneaking: e => e.sneaking, isSprinting: e => e.sprinting, isInWater: e => e.water, isOnLadder: e => e.ladder,
    lookVec: e => { const y = e.yaw * Math.PI / 180, p = e.pitch * Math.PI / 180; return [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)]; },
    primaryHandRight: e => e.right, mainStack: e => e.main, offStack: e => e.off, activeStack: e => e.active, mainItem: e => e.main.item, offItem: e => e.off.item,
    stackUseAction: s => s.item === BOW ? 'bow' : s.item === APPLE ? 'eat' : 'none', itemInUseCount: e => e.useCount, itemInUseMaxCount: e => e.useMax,
    activeHandMain: e => e.activeMain, isEntityAlive: e => e.alive, isPlayerSleeping: e => e.sleeping, ticksElytraFlying: e => e.elytra,
    capabilitiesFlying: e => e.flying, health: e => e.health, smallArms: () => false,
    chasing: e => [e.px, e.py, e.pz, e.x, e.y, e.z], cameraYaw: () => [0, 0.1], distanceWalked: e => [e.limbSwing, e.limbSwing + 0.1],
    wolfSitting: e => e.sitting, wolfInterestedAngle: e => e.interest, wolfShakeAngle: e => e.shake, wolfTailRotation: e => e.tail,
    squidRotation: e => e.squid, prevSquidRotation: e => e.psquid, besideClimbable: e => e.climbable
  };
  const W = { isStairs: () => false, isClimbable: (e) => e.ladder, isStaticLiquid: (e) => e.water, isAir: () => true,
    climbableFacing: () => 'south', collidesBelow: e => e.onGround };
  const I = { isAir: i => i === AIR, isSword: i => i === SWORD, isFood: i => i === APPLE, isBow: i => i === BOW, isTorch: i => i === TORCH,
    stackEmpty: s => s.item === AIR, stackItem: s => s.item };
  return {
    gl, entity: E, world: W, item: I, drawTrail: v => { log.trail += v.length / 7; }, viewEntity: () => null, worldEntityById: () => null,
    loadBendsAnimation: key => { const b64 = ASSETS.animations[key]; return b64 ? core().loadBinaryAnimation(new Uint8Array(Buffer.from(b64, 'base64'))) : null; },
    report: (event, error) => { throw error; }
  };
  function core() { return log.core; }
}
function setup() {
  const log = { gl: 0, push: 0, pop: 0, badQuat: 0, trail: 0 };
  const N = makeN(log), core = createJasprMoBendsCore(N);
  log.core = core;
  return { log, N, core };
}
function partsOf(data) {
  const out = [];
  data.nameToPartMap.forEach((p, k) => out.push([k, p.isModelPart ? p.rotation : p]));
  return out;
}
function assertFinite(data, label) {
  for (const [k, rot] of partsOf(data)) {
    const q = rot.getSmooth();
    for (const c of ['x', 'y', 'z', 'w']) assert.ok(Number.isFinite(q[c]), `${label}: ${k}.${c} = ${q[c]}`);
    const len = Math.hypot(q.x, q.y, q.z, q.w);
    assert.ok(Math.abs(len - 1) < 1e-6 || len === 0, `${label}: ${k} not normalised (${len})`);
  }
  for (const v of ['globalOffset', 'localOffset']) for (const c of ['getX', 'getY', 'getZ']) assert.ok(Number.isFinite(data[v][c]()), `${label}: ${v}.${c}`);
}
// One client tick plus a few render frames, like DataUpdateHandler drives it in the game.
function run(core, data, entity, ticks, step) {
  const DUH = core.DataUpdateHandler;
  for (let t = 0; t < ticks; t++) {
    if (step) step(entity, t);
    entity.px = entity.x; entity.py = entity.y; entity.pz = entity.z;
    entity.x += entity.mx; entity.y += entity.my; entity.z += entity.mz;
    entity.ticks++; entity.limbSwing += entity.limbSwingAmount;
    data.updateClient();
    for (let f = 0; f < 3; f++) {
      const pt = (f + 1) / 3, newTicks = entity.ticks + pt;
      DUH.partialTicks = pt; DUH.ticksPerFrame = Math.min(Math.max(0, newTicks - DUH.ticks), 1); DUH.ticks = newTicks;
      data.update(pt);
      data.headYaw.set(entity.head - entity.body); data.headPitch.set(entity.pitch);
      data.limbSwing.set(entity.limbSwing); data.limbSwingAmount.set(entity.limbSwingAmount); data.swingProgress.set(entity.swing);
      data.getController().perform(data);
    }
  }
}

test('player controller: every state keeps parts finite and normalised', () => {
  const { core } = setup();
  const p = makeEntity(), data = new core.PlayerData(p);
  assert.ok(data.isPlayer && data.isBiped);
  const states = [
    ['stand', e => { e.mx = e.mz = 0; e.limbSwingAmount = 0; }],
    ['walk', e => { e.mz = 0.1; e.limbSwingAmount = 0.6; }],
    ['sprint', e => { e.mz = 0.28; e.sprinting = true; e.limbSwingAmount = 1; }],
    ['sneak', e => { e.sprinting = false; e.sneaking = true; e.mz = 0.05; }],
    ['jump', (e, t) => { e.sneaking = false; e.onGround = t > 6; e.my = t < 3 ? 0.42 : t < 6 ? -0.2 : 0; }],
    ['fall', e => { e.onGround = false; e.my = -0.6; }],
    ['swim', e => { e.onGround = false; e.my = 0; e.water = true; e.mz = 0.1; }],
    ['climb', e => { e.water = false; e.ladder = true; e.my = 0.12; e.mz = 0; }],
    ['ride', e => { e.ladder = false; e.onGround = true; e.my = 0; e.riding = { living: true, body: 30, pbody: 30 }; }],
    ['sit', e => { e.riding = { living: false }; }],
    ['fly', e => { e.riding = null; e.flying = true; e.onGround = false; e.mz = 0.3; }],
    ['elytra', e => { e.flying = false; e.elytra = 20; e.my = -0.3; }],
    ['sleep', e => { e.elytra = 0; e.onGround = true; e.my = 0; e.mz = 0; e.sleeping = true; }],
    ['sword combo', (e, t) => { e.sleeping = false; e.main = stack(SWORD); e.swinging = t % 8 < 6; e.swing = (t % 8) / 6; }],
    ['punch', (e, t) => { e.main = stack(AIR); e.swinging = t % 8 < 6; e.swing = (t % 8) / 6; }],
    ['tool', (e, t) => { e.main = stack(PICK); e.swinging = true; e.swing = (t % 6) / 6; }],
    ['bow', (e, t) => { e.swinging = false; e.main = stack(BOW); e.active = stack(BOW); e.useCount = t + 1; e.useMax = t; }],
    ['eat', (e, t) => { e.main = stack(APPLE); e.active = stack(APPLE); e.useCount = 30 - t; }],
    ['torch', () => { }],
  ];
  for (const [name, step] of states) {
    if (name === 'torch') { p.active = stack(AIR); p.useCount = 0; p.main = stack(TORCH); p.mx = p.mz = 0; }
    run(core, data, p, 24, step);
    assertFinite(data, name);
  }
  // The attack combo walks through slash moves (SwordAction) and the spin is enabled as in the original config
  assert.equal(core.ModConfig.performSpinAttack, true);
  // Third-person attacks are Mo' Bends' own: nothing outside the core can switch its attack layer off.
  const fighter = makeEntity({ id: 9 }), fd = new core.PlayerData(fighter);
  fd.bettercombatActive = true;
  run(core, fd, fighter, 12, (e, t) => { e.main = stack(SWORD); e.swinging = t % 8 < 6; e.swing = (t % 8) / 6; });
  const action = fd.getController().actionController;
  assert.ok(action.layerAction.isPlaying(), 'the sword attack layer plays');
  assert.equal(action.currentAttackActionType !== null, true);
});

test('mob controllers: zombie, pig zombie, skeleton, spider, squid and wolf animate without NaN', () => {
  const { core } = setup();
  const zombie = makeEntity({ id: 3 }), zd = new core.ZombieData(zombie);
  assert.ok(zd.getAnimationSet() === 0 || zd.getAnimationSet() === 1);
  run(core, zd, zombie, 40, (e, t) => { e.mz = t > 10 ? 0.08 : 0; e.limbSwingAmount = t > 10 ? 0.5 : 0; });
  assertFinite(zd, 'zombie');
  const pig = makeEntity({ id: 4 }), pd = new core.PigZombieData(pig);
  run(core, pd, pig, 30, (e, t) => { e.swing = t % 10 > 5 ? 0.5 : 0; e.mz = 0.05; e.limbSwingAmount = 0.4; });
  assertFinite(pd, 'pig zombie');
  const skel = makeEntity({ id: 5 }), sd = new core.SkeletonData(skel);
  run(core, sd, skel, 30, (e, t) => { e.main = stack(BOW); e.active = stack(BOW); e.useCount = t; e.useMax = t; e.mx = 0.05; e.limbSwingAmount = 0.4; });
  assertFinite(sd, 'skeleton');
  const spider = makeEntity({ id: 6, height: 0.9 }), spd = new core.SpiderData(spider);
  run(core, spd, spider, 30, (e, t) => { e.mz = t < 15 ? 0.1 : 0; e.limbSwingAmount = t < 15 ? 0.6 : 0; e.body += 2; e.pbody = e.body - 2; });
  run(core, spd, spider, 20, e => { e.climbable = true; e.ladder = true; e.my = 0.1; });
  run(core, spd, spider, 20, e => { e.climbable = false; e.ladder = false; e.onGround = false; e.my = -0.4; });
  run(core, spd, spider, 20, e => { e.onGround = true; e.my = 0; e.health = 0; });
  assertFinite(spd, 'spider');
  for (const l of spd.limbs) assert.ok(Number.isFinite(l.worldX) && Number.isFinite(l.worldZ));
  const squid = makeEntity({ id: 8 }), qd = new core.SquidData(squid);
  run(core, qd, squid, 40, (e, t) => { e.psquid = e.squid; e.squid = (t * 0.3) % (Math.PI * 2); });
  assertFinite(qd, 'squid');
  assert.equal(qd.squidTentacles.length, 8);
  assert.equal(qd.squidTentacles[0].length, core.TENTACLE_SECTIONS);
  const wolf = makeEntity({ id: 9, height: 0.85 }), wd = new core.WolfData(wolf);
  assert.ok(wd.getController().kumoAnimatorState, 'wolf animator built from the embedded Kumo template');
  run(core, wd, wolf, 30, (e, t) => { e.mz = t > 10 ? 0.1 : 0; e.limbSwingAmount = t > 10 ? 0.7 : 0; });
  run(core, wd, wolf, 60, e => { e.mz = 0; e.limbSwingAmount = 0; e.sitting = true; });
  run(core, wd, wolf, 40, e => { e.sitting = false; e.interest = 0.3; e.shake = 0.2; });
  assertFinite(wd, 'wolf');
});

test('wolf keyframe state machine moves through idle, walking, sitting down, sitting, standing up', () => {
  const { core } = setup();
  const wolf = makeEntity({ id: 11 }), wd = new core.WolfData(wolf);
  const layer = wd.getController().kumoAnimatorState.layerStates[0];
  const nodeIndex = () => layer.nodeStates.indexOf(layer.currentNode);
  run(core, wd, wolf, 5, e => { e.mz = 0; });
  assert.equal(nodeIndex(), 0, 'idle');
  run(core, wd, wolf, 5, e => { e.mz = 0.1; e.limbSwingAmount = 0.7; });
  assert.equal(nodeIndex(), 1, 'walking');
  run(core, wd, wolf, 2, e => { e.mz = 0; e.limbSwingAmount = 0; e.sitting = true; });
  assert.equal(nodeIndex(), 2, 'sitting down');
  run(core, wd, wolf, 40, () => {});
  assert.equal(nodeIndex(), 3, 'sitting');
  run(core, wd, wolf, 2, e => { e.sitting = false; });
  assert.equal(nodeIndex(), 4, 'standing up');
  run(core, wd, wolf, 40, () => {});
  assert.equal(nodeIndex(), 0, 'back to idle');
});

test('renderer transforms: beforeRender balances and sword trails emit quads', () => {
  const { core, log } = setup();
  const p = makeEntity(), data = new core.PlayerData(p);
  p.main = stack(SWORD);
  run(core, data, p, 6, (e, t) => { e.swinging = t < 4; e.swing = t / 4; });
  const r = new core.MutatedRenderer('player');
  const before = log.push - log.pop;
  r.beforeRender(data, p, 0.5);
  assert.equal(log.push - log.pop, before, 'beforeRender leaves the matrix stack balanced');
  assert.ok(log.trail > 0 || data.swordTrail.trailPartList.length === 0);
  assert.equal(log.badQuat, 0, 'every rotation the renderer applies is a unit quaternion');
});

test('MathHelper table sin/cos and wrapDegrees match vanilla semantics', () => {
  const { core } = setup();
  const M = core.MathHelper;
  for (const v of [0, 0.5, 1, -1, Math.PI, -Math.PI / 2, 10, 123.456, -987.6]) {
    assert.ok(Math.abs(M.sin(v) - Math.sin(v)) < 2e-4, 'sin ' + v);
    assert.ok(Math.abs(M.cos(v) - Math.cos(v)) < 2e-4, 'cos ' + v);
  }
  assert.equal(M.wrapDegrees(190), -170);
  assert.equal(M.wrapDegrees(-190), 170);
  assert.equal(M.wrapDegrees(180), -180);
  assert.equal(core.jint(-5.7), -5);
  assert.equal(core.jint(1e12), 2147483647);
  assert.equal(core.jint(NaN), 0);
});
