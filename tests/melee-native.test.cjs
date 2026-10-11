'use strict';
// The first-person melee module and JASPR_COMBATFX inside the real client, offline (scripts/mobends-native-harness.cjs;
// one load of LIVE -> melee module -> COMBATFX): the native first-person hook draws exactly the motion's pose (one
// translate and one rotation after the vanilla side transform), the vanilla path stays when the option is off, fists
// keep the vanilla arm, the camera hook applies kicks with the shared sign convention, the trail pass draws coloured
// quads and puts every GL state back, and impacts spawn sparks through World.spawnParticle.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const melee = require('../scripts/build-melee-client.cjs');
const fx = require('../scripts/build-combatfx-client.cjs');
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');

const LIVE = process.env.MELEE_CLASSES_JS || melee.LIVE;
const live = fs.existsSync(LIVE);
let loaded = null;
function client() {
  if (loaded) return loaded;
  const full = fx.build(melee.build(fs.readFileSync(LIVE, 'latin1')).output).output;
  return (loaded = loadClientSource(full, { filename: 'melee-combatfx-candidate.js' }).fn);
}
function setup(fn, settingsOver) {
  const calls = [];
  fn.FX2 = (a, side, swing) => calls.push(['FX2', swing]);
  fn.DPm = (x, y, z) => calls.push(['translate', x, y, z]);
  fn.Gc9 = (angle, x, y, z) => calls.push(['rotate', angle, x, y, z]);
  fn.FMS = (a, equip, swing) => calls.push(['arm', swing]);
  fn.JasprCombatSpecial = () => 0;
  fn.C_q = () => 276;
  fn.FiS = () => 12.5;
  fn.EuW = () => 0;
  fn.G9();
  const net = {}, player = { d_: { qf: net }, cv: 100, bDd: 20, bQ: 1, p: 0, b: 0, f: 64, c: 0, C: 0, bd: 0 }, mc = { v: player, X: {}, G: { lv: 0, QV: 0 } };
  mc.bu = mc; mc.kD = mc;
  const settings = Object.assign({ v: 1, range: 3, width: 1, remaining: 0, period: 12, enabled: true, main: true, off: true, dual: true, motion: true, mainItem: 276, offItem: 276, offSwing: 0 }, settingsOver || {});
  fn.JasprBetterCombat.outgoing(mc);
  fn.JasprBetterCombat.receive(JSON.stringify(settings));
  return { calls, mc, player, renderer: { kD: mc, bu: mc } };
}

test('the first-person hook draws the motion pose for a swing, and only while Better Combat and the option are on', { skip: !live }, () => {
  const fn = client(), { calls, mc, player, renderer } = setup(fn), M = fn.JasprMeleeMotion;
  assert.equal(fn.JasprBetterCombat.active(mc), true);
  const sword = { rA: {}, bg_: 0, bV: null, bK: 0 };
  M.clear(); M.partial = 0;
  M.mark(false, 276, Date.now(), 99);                       // clicked at tick 99: the swing starts at 100
  player.cv = 102;
  fn.JasprCombatTransform(renderer, fn.Kua, 0.3, sword, 1);
  const want = M.poseFor(0, 102, true);
  assert.ok(want && want.clip === 'sword-diag', 'a sword forehand two ticks in');
  assert.deepEqual(calls[0], ['FX2', 0], 'the vanilla swing waggle is replaced, not added to');
  assert.deepEqual(calls[1], ['translate', want.tx, want.ty, want.tz]);
  assert.deepEqual(calls[2], ['rotate', want.rotation.angle, want.rotation.x, want.rotation.y, want.rotation.z]);
  // the classic bob offset is cancelled while posing; the same frame's pose is computed once
  calls.length = 0; fn.JasprEpicSwingOffset(renderer, fn.Kua, 0.3, sword, 1, 0.1, 0.2, 0.3);
  assert.equal(calls.length, 0);
  // fists keep the vanilla arm
  calls.length = 0; M.mark(false, 0, Date.now(), 101); fn.JasprMeleeEmptyArm(renderer, 0, 0.4, fn.Kua);
  assert.deepEqual(calls, [['arm', 0.4]]);
  // the personal "JasperCraft melee animations" option off: vanilla motion
  fn.JasprBetterCombat.receive(JSON.stringify({ v: 1, range: 3, width: 1, remaining: 0, period: 12, enabled: true, main: true, off: true, dual: true, motion: false, mainItem: 276, offItem: 276, offSwing: 0 }));
  M.mark(false, 276, Date.now(), 102); player.cv = 104;
  calls.length = 0; fn.JasprCombatTransform(renderer, fn.Kua, 0.3, sword, 1);
  assert.deepEqual(calls, [['FX2', 0.3]]);
});

test('the camera hook: positive pitch raises the view, push back moves only the world, kicks decay', { skip: !live }, () => {
  const fn = client(), { calls, player, renderer } = setup(fn), K = fn.JasprCameraFX;
  K.clear(); K.enabled = true; K.scale = 1;
  player.cv = 200;
  fn.JasprCameraFXApply(renderer, 0);                       // the clock
  K.kick({ pitch: 2, back: 0.1, ms: 300, curve: 'smooth' });
  calls.length = 0;
  fn.JasprCameraFXApply(renderer, 0.5);                      // world pass
  const rx = calls.find(c => c[0] === 'rotate' && c[2] === 1), tb = calls.find(c => c[0] === 'translate');
  assert.ok(rx && rx[1] < 0, 'rotate(-pitch) about X raises the view: ' + JSON.stringify(calls));
  assert.ok(tb && tb[3] < 0, 'the camera is pushed back');
  calls.length = 0; K.pass = 1;
  fn.JasprCameraFXApply(renderer, 0.5);                      // hand pass: same rotation, no push back
  K.pass = 0;
  assert.ok(calls.some(c => c[0] === 'rotate') && !calls.some(c => c[0] === 'translate'));
  player.cv = 230; calls.length = 0;
  fn.JasprCameraFXApply(renderer, 0);
  assert.equal(calls.length, 0, 'decayed');
});

test('the trail pass draws coloured quads in the hands\' matrices and restores the GL state; impacts spawn sparks', { skip: !live }, () => {
  const fn = client(), { calls, mc, player, renderer } = setup(fn), M = fn.JasprMeleeMotion;
  const gl = [], verts = [];
  // the GL state the trail pass saves and restores (GlStateManager's statics; the game initialised them long before)
  for (const [k, v] of Object.entries({ HHV: 0, HHT: 1, Kri: 1, HEo: 0, Krc: 770 | 770 << 16, Krd: 771 | 771 << 16, HKI: 1, HKJ: 1, HKK: 1, HKL: 1, KqX: 1, KqW: 518, Krf: 1 })) fn[k] = v;
  fn.Krr = { data: [1, 1] };
  for (const n of ['GnI', 'DCQ', 'CQ6', 'DFk', 'D75', 'F1Q', 'Ggy', 'Db2', 'D6M', 'CyM', 'CTP', 'Fb_', 'EFX', 'CcT', 'CFh', 'C5']) fn[n] = (...a) => gl.push([n, ...a]);
  fn.GdM = () => ({ dy: {} });
  fn.Ep0 = (buf, mode) => gl.push(['begin', mode]);
  fn.CUb = (buf, x, y, z) => verts.push([x, y, z]);
  fn.Eip = () => {}; fn.E74 = () => {};
  fn.FE$ = () => gl.push(['draw']);
  const sword = { rA: {}, bg_: 0, bV: null, bK: 0 };
  M.clear(); M.partial = 0;
  // a swing aimed at entity 77 (feed), drawn through its stroke
  let seq = 0; const events = [];
  M._test.setFeed({ since: s => events.filter(e => e.seq > s) });
  player.cv = 300;
  M.mark(false, 276, Date.now(), 299);
  events.push({ seq: ++seq, at: Date.now(), hand: 'M', kind: 'swing', item: 276, target: 77, ref: -1, tick: 299 });
  let drew = 0;
  for (let t = 300; t < 306; t += 0.25) {
    player.cv = Math.floor(t); M.partial = t - Math.floor(t);
    fn.JasprCombatTransform(renderer, fn.Kua, 0, sword, 1);
    gl.length = 0; verts.length = 0;
    fn.JasprCombatFXHand(renderer, t - Math.floor(t));
    if (verts.length) {
      drew++;
      assert.equal(verts.length % 4, 0, 'quads');
      assert.ok(gl.some(g => g[0] === 'begin' && g[1] === 7) && gl.some(g => g[0] === 'draw'), 'one tessellator draw');
      // the state touched comes back: depth mask, blend, lighting, texture, alpha test
      const last = n => gl.filter(g => g[0] === n).pop();
      assert.ok(last('EFX') && gl.filter(g => g[0] === 'EFX').length >= 2, 'depth mask set and restored');
    }
  }
  assert.ok(drew >= 3, 'a trail during the stroke: ' + drew + ' ' + JSON.stringify(fn.JasprCombatFX.status()));
  // the impact at contact becomes sparks at the target through World.spawnParticle
  const spawned = [];
  fn.Efj = (w, id) => spawned.push(id);
  fn.Bh = n => ({ data: new Int32Array(n) });
  mc.X = { baK: id => id === 77 ? { ed: 0, bc: { ct: -0.3, bv: 64, cz: 2, cH: 0.3, cl: 65.95, cK: 2.6 } } : null };
  fn.JasprCameraFX.clear();
  fn.JasprCombatFX.reset();
  fn.JasprCameraFXApply(renderer, 0.9);
  assert.ok(spawned.length > 0 && spawned.length <= 9, 'sparks: ' + spawned.join(','));
  assert.ok(fn.JasprCameraFX.status().kicks > 0, 'and a camera kick');
  void calls;
});
