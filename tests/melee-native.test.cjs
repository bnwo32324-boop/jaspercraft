'use strict';
// The first-person melee rework inside the real client, offline (scripts/mobends-native-harness.cjs; one load).
// The build replaces the BetterCombat 7 rig module, takes its third-person layer out (Mo' Bends owns third person),
// restores the native ModelBiped.setRotationAngles and animation-packet handler, and the native first-person hook
// then draws exactly the motion's pose: translate + one rotation after the vanilla side transform.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const melee = require('../scripts/build-melee-client.cjs');
const mobends = require('../scripts/build-mobends-client.cjs');
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');

const LIVE = process.env.MOBENDS_CLASSES_JS || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const live = fs.existsSync(LIVE);
let built = null;
function builds() {
  if (built) return built;
  const input = fs.readFileSync(LIVE, 'latin1'), stage = melee.build(input), full = mobends.build(stage).output;
  built = { input, stage, full };
  return built;
}
function fnText(s, name) { const at = s.indexOf('function ' + name + '('); return at < 0 ? null : s.slice(at, s.indexOf('\nfunction ', at + 9)); }

test('the build swaps the rig for the first-person motion and takes the third-person layer out', { skip: !live }, () => {
  const { input, stage, full } = builds();
  assert.ok(stage.includes(melee.BEGIN) && stage.includes(melee.END));
  for (const gone of ['JasprMeleeThirdPlan', 'JasprMeleeApplyBody', 'JasprMeleeRemoteSwing', 'JasprMeleeOriginalBiped', '/* JASPR_MELEE_RIG_BEGIN */'])
    assert.equal(full.includes(gone), false, gone);
  const dgz = fnText(full, 'DGZ');
  assert.ok(dgz.includes('0.6661999821662903') && dgz.includes('AGZ(a.lA,a.Ea);return;'), 'the native ModelBiped.setRotationAngles is back');
  assert.equal(fnText(full, 'FKv').includes('JasprMelee'), false, 'the animation-packet handler is vanilla again');
  assert.ok(full.includes("build:'bettercombat8'"));
  // other stages and BetterCombat's gameplay functions are untouched
  for (const name of ['JasprCombatAction', 'JasprCombatPick', 'JasprCombatSend', 'CDe', 'JasprDSTick', 'JasprCombatTransform'])
    assert.equal(fnText(stage, name), fnText(mobends.unpatch(input), name), name);
  // rebuilding on its own output only refreshes the module; Mo' Bends stays the removable outermost stage
  assert.equal(melee.build(full), stage);
  assert.equal(mobends.unpatch(full), stage);
});

test('the native first-person hook draws the motion pose, and only while BetterCombat and the option are on', { skip: !live }, () => {
  const { full } = builds();
  const { fn } = loadClientSource(full, { filename: 'melee-candidate.js' });
  const calls = [];
  fn.FX2 = (a, side, swing) => calls.push(['FX2', swing]);
  fn.DPm = (x, y, z) => calls.push(['translate', x, y, z]);
  fn.Gc9 = (angle, x, y, z) => calls.push(['rotate', angle, x, y, z]);
  fn.FMS = (a, equip, swing) => calls.push(['arm', swing]);
  fn.JasprCombatSpecial = () => 0;
  fn.C_q = () => 276;
  fn.G9();
  const net = {}, mc = { v: { d_: { qf: net }, cv: 102 }, X: {} }, renderer = { kD: mc }, sword = { rA: {}, bg_: 0, bV: null, bK: 0 };
  const settings = { v: 1, range: 3, width: 1, remaining: 0, period: 12, enabled: true, main: true, off: true, motion: true, mainItem: 276, offItem: 0, offSwing: 0 };
  fn.JasprBetterCombat.outgoing(mc);
  fn.JasprBetterCombat.receive(JSON.stringify(settings));
  assert.equal(fn.JasprBetterCombat.active(mc), true);
  const M = fn.JasprMeleeMotion;
  M.clear(); M.partial = 0;
  M.mark(false, 276, Date.now(), 100);
  fn.JasprCombatTransform(renderer, fn.Kua, 0.3, sword, 1);
  const stamp = M.current(true, 276, Date.now()), want = M.sample(stamp, M.phase(stamp, 102), true);
  assert.deepEqual(calls[0], ['FX2', 0], 'the vanilla swing waggle is replaced, not added to');
  assert.deepEqual(calls[1], ['translate', want.tx, want.ty, want.tz]);
  assert.deepEqual(calls[2], ['rotate', want.rotation.angle, want.rotation.x, want.rotation.y, want.rotation.z]);
  assert.equal(want.clip, 'sword-forehand');
  // the classic bob offset is cancelled while posing
  calls.length = 0; fn.JasprEpicSwingOffset(renderer, fn.Kua, 0.3, sword, 1, 0.1, 0.2, 0.3);
  assert.equal(calls.length, 0);
  // fists keep the vanilla arm
  calls.length = 0; M.mark(false, 0, Date.now(), 100); fn.JasprMeleeEmptyArm(renderer, 0, 0.4, fn.Kua);
  assert.deepEqual(calls, [['arm', 0.4]]);
  // the personal "JasperCraft melee animations" option off: vanilla motion
  fn.JasprBetterCombat.receive(JSON.stringify({ ...settings, motion: false }));
  M.mark(false, 276, Date.now(), 100);
  calls.length = 0; fn.JasprCombatTransform(renderer, fn.Kua, 0.3, sword, 1);
  assert.deepEqual(calls, [['FX2', 0.3]]);
});
