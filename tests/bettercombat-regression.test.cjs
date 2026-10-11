'use strict';
// Before / after: each dual-wield client bug reproduced on the head the live client runs today (bettercombat8, extracted
// verbatim in client-mods/bettercombat/bettercombat-live-head.js) and shown fixed in the new head (bettercombat9), on the
// same scenario. Run alone: node --test --test-concurrency=1 tests/bettercombat-regression.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const { liveHead, world, stack, entity, connect, tick, rightPress, rightHeld, left, aim } = require('./bettercombat-stubs.cjs');

function both(scenario) {
  const out = {};
  for (const [name, options] of [['before', { source: liveHead(), filename: 'bettercombat-live-head.js' }], ['after', {}]]) {
    const w = world(options);
    out[name] = scenario(w);
  }
  return out;
}

test('B1 off-hand click on a target with the ground / a wall under the crosshair: dead click before, attack now', () => {
  const r = both(w => { connect(w); tick(w, 1); aim(w, entity('mob'), true); const consumed = rightPress(w); return { consumed, sent: w.sent.slice() }; });
  assert.deepEqual(r.before, { consumed: false, sent: [] }, 'before: handed to vanilla block use (a sword does nothing there)');
  assert.deepEqual(r.after, { consumed: true, sent: ['attack O 1'] });
});

test('B2 right click on a villager / horse / armor stand: stolen by a dead swing before, its interaction now', () => {
  const r = both(w => { connect(w); tick(w, 1); aim(w, entity('mob', { typeId: 120 })); const consumed = rightPress(w); return { consumed, sent: w.sent.slice(), log: w.log.filter(x => x[0] === 'use') }; });
  assert.deepEqual(r.before.sent, ['attack O'], 'before: an attack message the server refuses, no trade window');
  assert.equal(r.before.consumed, true);
  assert.deepEqual(r.after.sent, []);
  assert.deepEqual(r.after.log, [['use', 'MAIN'], ['use', 'OFF']], 'now: the vanilla right click (trade, ride, armor stand)');
});

test('B3 holding the use button: an attack every 4 ticks before (weak spam), one per press now', () => {
  const r = both(w => {
    connect(w); aim(w, entity('mob'));
    tick(w, 1); rightPress(w);
    for (let i = 0; i < 12; i++) { tick(w); if (i % 4 === 3) rightHeld(w); }
    return w.sent.length;
  });
  assert.equal(r.before, 4);
  assert.equal(r.after, 1);
});

test('B4 a hotbar switch or F right before the click: vanilla fallback (dead off-hand click) before, the item in hand now', () => {
  const r = both(w => { connect(w, { offItem: 276 }); w.player.off = stack(258); tick(w, 1); aim(w, entity('mob')); return { consumed: rightPress(w), sent: w.sent.slice() }; });
  assert.deepEqual(r.before, { consumed: false, sent: [] });
  assert.deepEqual(r.after, { consumed: true, sent: ['attack O 1'] });
});

test('B5 a server silence of 3.5 s (lag spike): Better Combat switched off mid-fight before, still on now', () => {
  const r = both(w => { connect(w); aim(w, entity('mob')); w.advance(3500); tick(w); return left(w); });
  assert.equal(r.before, false);
  assert.equal(r.after, true);
});

test('B6 food / a bow without arrows in the main hand: the off hand could never attack before; now it attacks when the main hand passes', () => {
  const r = both(w => { connect(w, { use: true }); w.player.main = stack(364); tick(w, 1); aim(w, entity('mob')); return { consumed: rightPress(w), sent: w.sent.slice() }; });
  assert.deepEqual(r.before, { consumed: false, sent: [] });
  assert.deepEqual(r.after, { consumed: true, sent: ['attack O 1'] });
});

test('B7 spectator mode: clicks were swallowed (no spectating an entity) before, vanilla now', () => {
  const r = both(w => { connect(w); w.mc.dw.jC = 'SPECTATOR'; tick(w); aim(w, entity('mob')); return { consumed: left(w), sent: w.sent.slice() }; });
  assert.deepEqual(r.before, { consumed: true, sent: ['attack M'] });
  assert.deepEqual(r.after, { consumed: false, sent: [] });
});

test('B8 two clicks of one hand in one tick: two messages (the server dropped one silently) before, one + a denied feed event now', () => {
  const r = both(w => { connect(w); tick(w); aim(w, entity('mob')); left(w); left(w); return w.sent.length; });
  assert.equal(r.before, 2);
  assert.equal(r.after, 1);
});
