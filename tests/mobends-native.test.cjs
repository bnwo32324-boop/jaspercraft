'use strict';
// The Mo' Bends stage inside the real TeaVM client, offline (scripts/mobends-native-harness.cjs): the build is applied
// to the live classes.js, then real ModelPlayer / ModelZombie / ModelSkeleton / ModelSpider / ModelSquid / ModelWolf
// objects are mutated, and every display list the bent model draws is recorded with the engine's own modelview
// matrix. In the rest pose the bent player must occupy exactly the space of the vanilla player, limb for limb, and
// the split limbs must carry the original UVs. Armor wrappers slice the vanilla armor at the elbow and knee.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadClient, loadClientSource } = require('../scripts/mobends-native-harness.cjs');
const { build } = require('../scripts/build-mobends-client.cjs');

const LIVE = process.env.MOBENDS_CLASSES_JS || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const live = fs.existsSync(LIVE);
let client = null, source = null;
function load() {
  if (client) return client;
  const { output } = build(fs.readFileSync(LIVE, 'latin1'));
  source = output;
  client = loadClientSource(output, { filename: 'mobends-candidate.js' });
  return client;
}
// The engine's GlStateManager needs a WebGL context to initialise, so the GL calls the model path makes are stood
// in for by a column-major matrix stack with OpenGL's post-multiplication semantics.
const gl = { stack: [[1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]] };
function top() { return gl.stack[gl.stack.length - 1]; }
function mulTop(b) {
  const a = top(), c = new Array(16);
  for (let col = 0; col < 4; col++) for (let row = 0; row < 4; row++) { let n = 0; for (let k = 0; k < 4; k++) n += a[k * 4 + row] * b[col * 4 + k]; c[col * 4 + row] = n; }
  gl.stack[gl.stack.length - 1] = c;
}
function rot(rad, x, y, z) {
  const c = Math.cos(rad), s = Math.sin(rad), t = 1 - c, l = Math.hypot(x, y, z); x /= l; y /= l; z /= l;
  return [t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0, t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0, t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0, 0, 0, 0, 1];
}
function stubGl(fn) {
  gl.stack = [[1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]];
  fn.Eu0 = () => { gl.stack.push(top().slice()); };
  fn.ECi = () => { if (gl.stack.length > 1) gl.stack.pop(); };
  fn.DPm = fn.GkS = (x, y, z) => mulTop([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1]);
  fn.FWM = (x, y, z) => mulTop([x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1]);
  fn.Gc9 = (deg, x, y, z) => mulTop(rot(deg * Math.PI / 180, x, y, z));
  fn.GoB = (x, y, z) => { if (z) mulTop(rot(z, 0, 0, 1)); if (y) mulTop(rot(y, 0, 1, 0)); if (x) mulTop(rot(x, 1, 0, 0)); };
  fn.EQk = m => mulTop([m.h_, m.h$, m.ia, m.g4, m.h7, m.h9, m.h8, m.g3, m.h5, m.hy, m.h6, m.gy, m.lB, m.lD, m.lC, m.jU]);
  fn.CFh = () => {};
  if (!fn.HKM) fn.HKM = { data: [new fn.Kn()] };
}
function matrixNow() { return top().slice(); }
function apply(m, x, y, z) { return [m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]]; }
// Record every display-list draw (gore's JasprGoreDraw wrapper is the single draw entry for model parts).
function recorder(fn) {
  const draws = [];
  fn.F$t = function (part, scale) { part.clh = 1; part.bWg = draws.length + 1000; part.$jasprGoreScale = scale; };
  fn.JasprGoreDraw = function (part, scale) { draws.push({ part, scale, matrix: matrixNow() }); };
  stubGl(fn);
  return draws;
}
function points(draw) {
  const out = [];
  const boxes = draw.part.a6Y;
  for (let i = 0; i < boxes.g; i++) {
    const quads = boxes.qN.data[i].a4t.data;
    for (const q of quads) for (const v of q.a4R.data) out.push(apply(draw.matrix, v.Kj.bh * draw.scale, v.Kj.bq * draw.scale, v.Kj.bi * draw.scale));
  }
  return out;
}
function bbox(pts) {
  const b = [Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity];
  for (const p of pts) for (let j = 0; j < 3; j++) { b[j] = Math.min(b[j], p[j]); b[j + 3] = Math.max(b[j + 3], p[j]); }
  return b;
}
function uvs(box) { return Array.from(box.a4t.data).map(q => Array.from(q.a4R.data).map(v => [Math.round(v.cDP * 64), Math.round(v.cDQ * 64)])); }

test('the stage loads inside the real client and wires the Video Settings switch', { skip: !live }, () => {
  const { fn } = load();
  const B = fn.JasprMoBendsBridge;
  assert.ok(B && B._test && typeof B.render === 'function');
  assert.equal(fn.$rt_ustr(fn.JasprVideoLabel(973)), "Mo' Bends animations: ON");
  fn.JasprVideoAction(973);
  assert.equal(fn.$rt_ustr(fn.JasprVideoLabel(973)), "Mo' Bends animations: OFF");
  assert.equal(B.enabled(), false);
  fn.JasprVideoAction(973);
  assert.equal(B.enabled(), true, 'the switch toggles back on');
  assert.equal(fn.$rt_ustr(fn.JasprVideoLabel(972)).indexOf('Optional particles'), 0, 'the neighbouring option keeps its label');
});

test('the client tick runs once per Minecraft.runTick, not once per frame, and stops while the game is paused', { skip: !live }, () => {
  const { fn } = load();
  // Minecraft.runGameLoop calls runTick timer.elapsedTicks times per frame: the tick hook belongs in that function
  const loop = /case \d+:([\w$]+)\(a\);if\(B\(\)\)\{break _;\}(\w)=\2\+1\|0;if\(\2>=a\.[\w$]+\.[\w$]+\)\{\$p=\d+;continue _;\}continue _;/.exec(source);
  assert.ok(loop, 'the timer loop of Minecraft.runGameLoop');
  const start = source.indexOf('function ' + loop[1] + '('), body = source.slice(start, source.indexOf('\nfunction ', start + 10));
  assert.ok(body.includes('case 0:JasprMoBendsBridge.tick();'), 'the hook opens runTick (' + loop[1] + '), where Forge fires ClientTickEvent');
  assert.equal(source.split('JasprMoBendsBridge.tick()').length - 1, 1, 'and is not also called per frame');
  const B = fn.JasprMoBendsBridge, stats = B._test.stats, DUH = B._test.core.DataUpdateHandler, saved = fn.HEH;
  try {
    fn.HEH = { v: { a: {}, cv: 100 }, cp: 0 };
    const ticks = stats.ticks;
    B.tick(); assert.equal(stats.ticks, ticks + 1);
    B.frame(0.25, null); assert.equal(DUH.partialTicks, 0.25);
    fn.HEH.cp = 1;
    B.tick(); assert.equal(stats.ticks, ticks + 1, 'no client tick while paused');
    B.frame(0.75, null);
    assert.equal(DUH.ticksPerFrame, 0, 'animations hold still while paused');
    assert.equal(DUH.partialTicks, 0.25, 'and keep the last partial tick');
  } finally { fn.HEH = saved; }
});

test('rest pose: the bent player covers the vanilla player limb for limb, with the original UVs', { skip: !live }, () => {
  const { fn } = load();
  const T = fn.JasprMoBendsBridge._test, draws = recorder(fn);
  const vanilla = new fn.BVS(); fn.Hlq(vanilla, 0, 0);
  const renderer = { iK: vanilla, d9q: 0, cHT: fn.Bq() };
  const bender = T.registry()[0], mut = new T.PlayerMutator(bender);
  assert.equal(mut.mutate(renderer), true);
  const bent = renderer.iK;
  assert.notEqual(bent, vanilla, 'the renderer draws a clone; the vanilla model stays untouched');
  assert.ok(vanilla.gM.$mb === undefined && bent.gM.$mb, 'vanilla parts carry no Mo\' Bends state');
  assert.equal(fn.Bm(bent.cJ9, 0).ddy, bent, 'bent parts belong to the clone');
  // [vanilla field, label, base limb whose render also draws the lower wear layer, that lower wear part]
  const limbs = [['lA', 'head'], ['k_', 'body'], ['gM', 'right arm'], ['f3', 'left arm'], ['mD', 'right leg'], ['nc', 'left leg'], ['bq3', 'jacket'], ['Ea', 'hat'],
    ['Wo', 'right sleeve', 'gM', mut.rightForeArmwear], ['a0W', 'left sleeve', 'f3', mut.leftForeArmwear],
    ['bqQ', 'right pants', 'mD', mut.rightForeLegwear], ['a4$', 'left pants', 'nc', mut.leftForeLegwear]];
  // vanilla ModelPlayer keeps its wear layers at rotationPointZ 10 until setRotationAngles copies the limbs' angles
  for (const [from, to] of [['gM', 'Wo'], ['f3', 'a0W'], ['mD', 'bqQ'], ['nc', 'a4$'], ['k_', 'bq3']]) fn.AGZ(vanilla[from], vanilla[to]);
  const S = 0.0625, tol = 0.012 * S + 1e-6;
  const lowerWear = new Set([mut.leftForeArmwear, mut.rightForeArmwear, mut.leftForeLegwear, mut.rightForeLegwear].map(p => p.native));
  for (const [field, label, base, lower] of limbs) {
    draws.length = 0; fn.E7Q(vanilla[field], S);
    const v = bbox(draws.flatMap(points));
    draws.length = 0; fn.E7Q(bent[field], S);
    assert.ok(draws.length >= 1, label + ' draws');
    let own = draws.filter(d => !lowerWear.has(d.part));
    if (lower) {
      // the lower sleeve is a child of the bent forearm/shin, so it is drawn with the limb rather than the wear part
      const upperWear = own.slice();
      draws.length = 0; fn.E7Q(bent[base], S);
      own = upperWear.concat(draws.filter(d => d.part === lower.native));
    }
    const b = bbox(own.flatMap(points));
    // Mo' Bends inflates limbs by 0.01 px (0.0025-0.005 for the wear layer) and its sleeves end 0.25 px short.
    const loose = lower ? 0.3 * S : tol;
    for (let j = 0; j < 6; j++) assert.ok(Math.abs(v[j] - b[j]) <= loose, `${label} bound ${j}: vanilla ${v[j].toFixed(4)} bent ${b[j].toFixed(4)}`);
  }
  // The right arm is two boxes: upper arm without its bottom face, forearm (the extension) without its top face.
  draws.length = 0; fn.E7Q(bent.gM, S);
  assert.equal(draws.length, 3, 'upper arm, forearm and the forearm sleeve'); // sleeve is a child of the forearm
  const upper = fn.Bm(bent.gM.a6Y, 0), fore = fn.Bm(mut.rightForeArm.native.a6Y, 0);
  assert.equal(upper.a4t.data.length, 5); assert.equal(fore.a4t.data.length, 5);
  // front face (index 4) of the upper arm: u 44..48, v 20..26; of the forearm: v 26..32 (vanilla arm front: v 20..32)
  const frontUpper = uvs(upper)[3], frontFore = uvs(fore)[3];
  assert.deepEqual(frontUpper.map(p => p[0]).sort(), [44, 44, 48, 48]);
  assert.deepEqual([...new Set(frontUpper.map(p => p[1]))].sort((a, b) => a - b), [20, 26]);
  assert.deepEqual([...new Set(frontFore.map(p => p[1]))].sort((a, b) => a - b), [26, 32]);
  // dead first-pass limbs are pruned; the extension is listed as a child for the dismemberment system
  for (let i = 0; i < bent.cJ9.g; i++) assert.ok(fn.Bm(bent.cJ9, i).$mb, 'every registered part is a Mo\' Bends part');
  assert.ok(Array.from(bent.gM.OS.qN.data).slice(0, bent.gM.OS.g).includes(mut.rightForeArm.native));
  // demutate restores the vanilla model on the renderer
  mut.demutate();
  assert.equal(renderer.iK, vanilla);
});

test('every mob mutator builds on its real vanilla model and draws a finite, closed pose', { skip: !live }, () => {
  const { fn } = load();
  const T = fn.JasprMoBendsBridge._test, draws = recorder(fn);
  const S = 0.0625;
  const cases = [
    ['zombie', () => { const m = new fn.C4z(); fn.GZx(m, 0, 0); return m; }, T.ZombieMutator, ['lA', 'k_', 'gM', 'f3', 'mD', 'nc'], 1],
    ['skeleton', () => fn.BHm(0, 0), T.SkeletonMutator, ['lA', 'k_', 'gM', 'f3', 'mD', 'nc'], 2],
    ['spider', () => { const m = new fn.F$X(); fn.GKn(m); return m; }, T.SpiderMutator, ['coY', 'cSt', 'cx0', 'bLf', 'bLi'], 4],
    ['wolf', () => { const m = new fn.C2y(); fn.HgX(m); return m; }, T.WolfMutator, ['XW', 'Y8', 'bel', 'Y9', 'a79'], 6]
  ];
  for (const [label, make, Mutator, fields, benderIndex] of cases) {
    const vanilla = make(), renderer = { iK: vanilla, d9q: 0, cHT: fn.Bq() };
    const mut = new Mutator(T.registry()[benderIndex]);
    assert.equal(mut.mutate(renderer), true, label + ' mutates');
    const bent = renderer.iK;
    for (const f of fields) {
      assert.ok(bent[f] && bent[f].$mb, `${label}.${f} is a Mo' Bends part`);
      draws.length = 0; fn.E7Q(bent[f], S);
      assert.ok(draws.length >= 1, `${label}.${f} draws`);
      for (const p of draws.flatMap(points)) for (const c of p) assert.ok(Number.isFinite(c), `${label}.${f} vertex`);
    }
    mut.demutate();
    assert.equal(renderer.iK, vanilla, label + ' demutates');
  }
});

test('armor follows the bent limbs: vanilla armor boxes are sliced at the elbow and knee', { skip: !live }, () => {
  const { fn } = load();
  const T = fn.JasprMoBendsBridge._test, core = T.core, draws = recorder(fn);
  const armor = new fn.OB(); fn.AB4(armor, 1, 0, 64, 32);
  const vanillaArm = armor.gM;
  const w = new T.ArmorWrapper(armor);
  const data = { body: new core.ModelPartTransform(), head: null };
  for (const k of ['head', 'leftArm', 'rightArm', 'leftForeArm', 'rightForeArm', 'leftLeg', 'rightLeg', 'leftForeLeg', 'rightForeLeg']) data[k] = new core.ModelPartTransform();
  data.body.position.set(0, 12, 0); data.rightArm.position.set(-5, -10, 0); data.rightForeArm.position.set(0, 4, 2);
  w.prepare(data);
  assert.notEqual(armor.gM, vanillaArm, 'the armor model draws the wrapper while applied');
  draws.length = 0; fn.E7Q(armor.gM, 0.0625);
  assert.equal(draws.length, 4, 'upper part, its anchor, the lower part and its anchor (two hold boxes)');
  const boxes = draws.filter(d => d.part.a6Y.g > 0);
  assert.equal(boxes.length, 2, 'the sleeve becomes an upper and a lower box');
  w.deapply();
  assert.equal(armor.gM, vanillaArm, 'deapply restores the vanilla armor parts');
});

test('BetterCombat strikes drive the bent arm: the upper arm ends up exactly at the rig\'s angles', { skip: !live }, () => {
  const { fn } = load();
  const T = fn.JasprMoBendsBridge._test, core = T.core, draws = recorder(fn);
  const vanilla = new fn.BVS(); fn.Hlq(vanilla, 0, 0);
  const renderer = { iK: vanilla, d9q: 0, cHT: fn.Bq() };
  const mut = new T.PlayerMutator(T.registry()[0]);
  assert.equal(mut.mutate(renderer), true);
  const bent = renderer.iK;
  mut.lastData = { renderRightItemRotation: new core.SmoothOrientation(), renderLeftItemRotation: new core.SmoothOrientation() };
  mut.lastData.renderRightItemRotation.orientInstantX(90);
  // what the rig leaves on the native joints after JasprMeleeApplyBody: torso twist/lean and a raised sword arm
  bent.k_.A = 0.12; bent.k_.bb = 0.6; bent.k_.bX = 0;
  bent.gM.A = -1.9; bent.gM.bb = 0.35; bent.gM.bX = 0.2;
  T.meleePose(bent, { parts: [{ right: true, main: true }] });
  draws.length = 0; fn.E7Q(bent.gM, 0.0625);
  const upper = draws.find(d => d.part === bent.gM);
  assert.ok(upper, 'the upper arm draws');
  const want = [rot(0.2, 0, 0, 1), rot(0.35, 0, 1, 0), rot(-1.9, 1, 0, 0)].reduce((a, b) => {
    const c = new Array(16);
    for (let col = 0; col < 4; col++) for (let row = 0; row < 4; row++) { let n = 0; for (let k = 0; k < 4; k++) n += a[k * 4 + row] * b[col * 4 + k]; c[col * 4 + row] = n; }
    return c;
  });
  for (const i of [0, 1, 2, 4, 5, 6, 8, 9, 10]) assert.ok(Math.abs(upper.matrix[i] - want[i]) < 1e-6, `rotation[${i}] ${upper.matrix[i]} vs ${want[i]}`);
  const item = mut.lastData.renderRightItemRotation.getSmooth();
  assert.ok(Math.abs(item.w - 1) < 1e-9, 'the Mo\' Bends item tilt yields to the rig during the strike');
  assert.ok(typeof mut.lastData.bettercombatTime === 'number', 'the strike pauses Mo\' Bends\' own attack layer');
  mut.demutate();
});

test('diagnostics: the switch reports jaspercraft.mobends.state; a failure reports one bounded jaspercraft.mobends.error', { skip: !live }, () => {
  load();
  // a separate client instance: the failure below switches the stage off for the rest of that page
  const { fn, context } = loadClientSource(source, { filename: 'mobends-diagnostics.js' });
  const sent = [];
  context.fetch = (url, init) => { sent.push({ url, init, body: JSON.parse(init.body) }); return Promise.resolve({ ok: true }); };
  context.location = { pathname: '/' };
  const B = fn.JasprMoBendsBridge;
  fn.JasprVideoAction(973);
  assert.equal(sent.length, 0, 'events only leave the game page');
  context.location = { pathname: '/jaspercraft/client.html' };
  fn.JasprVideoAction(973);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].url, '/api/diagnostics/events');
  assert.equal(sent[0].init.credentials, 'same-origin');
  const state = sent[0].body.events[0];
  assert.equal(state.event, 'jaspercraft.mobends.state');
  assert.deepEqual([state.details.enabled, state.details.failed], [true, false]);
  // an exception inside a hook: the stage turns itself off for the session, restores vanilla and reports once
  fn.HEH = { cp: 0, get v() { throw new Error('model state broke\nsecond line ' + 'x'.repeat(400)); } };
  B.tick(); B.tick(); B.frame(0.5, null);
  const errors = sent.filter(s => s.body.events[0].event === 'jaspercraft.mobends.error');
  assert.equal(errors.length, 1, 'one error event');
  const details = errors[0].body.events[0].details;
  assert.equal(details.stage, 'tick');
  assert.ok(details.error.length <= 180 && !/[\r\n]/.test(details.error), 'the message is one bounded line');
  assert.equal(details.stats.errors, 1);
  assert.equal(B.enabled(), false);
  assert.equal(context.JasprMoBendsDiagnostics.status().failed, true);
  assert.equal(fn.$rt_ustr(fn.JasprVideoLabel(973)), "Mo' Bends animations: OFF (error)");
  // bounded: at most 12 events per page however often the switch flips
  for (let i = 0; i < 30; i++) B.setEnabled(i % 2 === 0);
  assert.equal(sent.length, 12);
});
