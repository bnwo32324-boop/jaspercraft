'use strict';
// First-person melee motion (client-mods/melee/melee-motion.js): every clip leaves and returns to the vanilla rest
// pose exactly, moves continuously (no pops, also across a combo click), is fastest in the strike, mirrors for the
// off hand, picks combo moves from where the weapon is, and reaches the target on a sword about 0.1 s after the click.
const test = require('node:test');
const assert = require('node:assert/strict');
const motion = require('../client-mods/melee/melee-motion.js');
const T = motion._test;

function family(name) { return Object.keys(motion.groups).find(g => motion.groups[g].includes(name)); }
function swingOf(name) { return { clip: name, duration: motion.durations[family(name)], start: null }; }
function angleOf(q) { const s = Math.hypot(q[0], q[1], q[2]); return 2 * Math.atan2(s, Math.abs(q[3])); }
function between(a, b) { const d = Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]); return 2 * Math.acos(Math.min(1, d)); }
function dist(a, b) { return Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]); }

test('every clip starts and ends exactly on the vanilla first-person pose', () => {
  for (const name of motion.clipNames) {
    for (const p of [0, 0.9999]) {
      const pose = motion.pose(name, p, true);
      assert.ok(Math.hypot(pose.tx, pose.ty, pose.tz) < 2e-3, `${name} @${p} offset ${pose.tx},${pose.ty},${pose.tz}`);
      assert.ok(Math.abs(pose.rotation.angle) < 0.5 || Math.abs(pose.rotation.angle - 360) < 0.5, `${name} @${p} angle ${pose.rotation.angle}`);
    }
  }
});

// the blade's tip in view space: what the eye follows during a cut
function tip(pose) { const r = T.qrot(pose.q, T.BLADE.map(v => v * 0.68)); return [pose.g[0] + r[0], pose.g[1] + r[1], pose.g[2] + r[2]]; }
// a move that only starts from the left (a backhand) begins where its family's first cut ended
function realistic(name) {
  const swing = swingOf(name);
  if (T.clips[name].side !== 'left') return swing;
  const opener = swingOf(motion.groups[family(name)][0]), c = T.clips[opener.clip], at = (c.windup + c.stroke + 0.05) * opener.duration;
  const pose = T.evaluate(opener, at), vel = T.velocity(opener, at);
  swing.start = { g: pose.g, q: pose.q, v: vel.v, w: vel.w };
  return swing;
}

test('motion is continuous and finite, and the strike is the fastest part of every swing', () => {
  for (const name of motion.clipNames) {
    const swing = realistic(name), T0 = swing.duration, steps = 600, thrust = !!T.clips[name].thrust, top = { windup: 0, stroke: 0, recover: 0 };
    let prev = T.evaluate(swing, 0);
    for (let i = 1; i <= steps; i++) {
      const tick = T0 * i / steps * 0.9999, pose = T.evaluate(swing, tick);
      for (const v of [...pose.g, ...pose.q]) assert.ok(Number.isFinite(v), `${name} finite at ${tick}`);
      const move = dist(prev.g, pose.g), turn = between(prev.q, pose.q);
      // at 600 samples per clip one step is under a millisecond of game time
      assert.ok(move < 0.03, `${name} grip jumps ${move.toFixed(3)} blocks at tick ${tick.toFixed(2)}`);
      assert.ok(turn < 0.12, `${name} blade flips ${(turn * 180 / Math.PI).toFixed(1)} degrees at tick ${tick.toFixed(2)}`);
      // cuts are judged by the tip, thrusts by the drive of the hand
      const speed = thrust ? move : Math.max(move, dist(tip(prev), tip(pose)));
      top[pose.phase] = Math.max(top[pose.phase], speed);
      prev = pose;
    }
    assert.ok(top.stroke > top.windup && top.stroke > top.recover, `${name}: fastest in the strike (${JSON.stringify(top)})`);
  }
});

test('wind-up, strike and recovery join without a velocity kink', () => {
  for (const name of motion.clipNames) {
    const swing = swingOf(name), clip = T.clips[name], D = swing.duration, h = 0.004;
    for (const at of [clip.windup, clip.windup + clip.stroke]) {
      const t = at * D, a = T.evaluate(swing, t - h), b = T.evaluate(swing, t + h);
      // both sides come to rest at the joint: the reversal points of a real swing
      assert.ok(dist(a.g, b.g) / (2 * h) < 0.05, `${name} grip speed at ${at}: ${(dist(a.g, b.g) / (2 * h)).toFixed(3)} blocks/tick`);
      assert.ok(between(a.q, b.q) / (2 * h) < 0.08, `${name} turn rate at ${at}`);
    }
  }
});

test('the off hand is the mirror image of the main hand', () => {
  for (const name of ['sword-forehand', 'axe-chop', 'spear-drive']) {
    for (const p of [0.1, 0.3, 0.5, 0.8]) {
      const r = motion.pose(name, p, true), l = motion.pose(name, p, false);
      assert.ok(Math.abs(r.tx + l.tx) < 1e-9 && Math.abs(r.ty - l.ty) < 1e-9 && Math.abs(r.tz - l.tz) < 1e-9);
      assert.ok(Math.abs(r.rotation.angle - l.rotation.angle) < 1e-9);
      assert.ok(Math.abs(r.rotation.x - l.rotation.x) < 1e-9 && Math.abs(r.rotation.y + l.rotation.y) < 1e-9 && Math.abs(r.rotation.z + l.rotation.z) < 1e-9);
    }
  }
});

test('combos: a click mid-strike waits for the stroke and then flows into a backhand; a click in guard starts fresh', () => {
  motion.clear();
  const ms = tick => 1000 + (tick - 100) * 50;
  motion.mark(false, 276, ms(100), 100);
  const hand = T.hands()[0], c = T.clips['sword-forehand'], end = 100 + (c.windup + c.stroke) * hand.duration;
  assert.equal(hand.clip, 'sword-forehand');
  // spam: two more clicks during the strike; the stroke is not cut off and only one move waits
  motion.mark(false, 276, ms(101.5), 101.5); motion.mark(false, 276, ms(102), 102);
  assert.equal(T.hands()[0], hand); assert.equal(hand.clip, 'sword-forehand'); assert.equal(hand.queued.sequence, 2);
  const before = T.evaluate(hand, end - 100 - 1e-6);
  assert.ok(motion.phase(hand, end - 0.01) < (c.windup + c.stroke), 'still the forehand until its stroke ends');
  assert.equal(motion.phase(hand, end), 0, 'the waiting move starts exactly when the stroke ends');
  assert.equal(hand.clip, 'sword-backhand', 'the blade ended on the left, so the follow-up is a backhand');
  const flow = T.evaluate(hand, 0);
  assert.ok(dist(flow.g, before.g) < 1e-4 && between(flow.q, before.q) < 1e-4, 'no pop where the backhand takes over');
  // a click during the backhand's recovery starts at once, out of the moving weapon
  const b = T.clips['sword-backhand'], during = end + (b.windup + b.stroke + 0.2) * hand.duration;
  const moving = T.evaluate(hand, during - hand.tick);
  motion.mark(false, 276, ms(during), during);
  const third = T.hands()[0];
  assert.notEqual(third, hand); assert.ok(third.start); assert.equal(third.tick, during);
  assert.ok(dist(T.evaluate(third, 0).g, moving.g) < 1e-9, 'continuous out of the recovery');
  assert.equal(T.clips[third.clip].side, 'right'); assert.notEqual(third.clip, 'sword-forehand');
  // more than 1.3 s later the combo starts over with the forehand
  motion.mark(false, 276, ms(during) + 2000, during + 40);
  assert.equal(T.hands()[0].clip, 'sword-forehand'); assert.equal(T.hands()[0].sequence, 1);
});

test('items pick their family; custom JasprApocalypse weapons use their real type; fists keep the vanilla arm', () => {
  assert.equal(motion.describe(276, null).family, 'sword');
  assert.equal(motion.describe(258, null).family, 'axe');
  assert.equal(motion.describe(257, null).family, 'tool');
  assert.equal(motion.describe(267, '{JasprApocalypse:{id:"sentinel_spear",tier:2}}').family, 'spear');
  assert.equal(motion.describe(267, '{JasprApocalypse:{id:"trench_blade"}}').family, 'dagger');
  assert.equal(motion.describe(292, '{JasprApocalypse:{id:"reaper_scythe"}}').family, 'hook');
  assert.equal(motion.describe(258, '{JasprApocalypse:{id:"gravity_maul"}}').family, 'heavy');
  assert.equal(motion.describe(261, null).family, null);
  motion.clear();
  motion.mark(false, 0, 5000, 10);
  assert.equal(motion.current(true, 0, 5000), null, 'no first-person fist clip: the vanilla arm stays');
  motion.mark(false, 267, 6000, 30);
  const stamp = motion.current(true, 267, 6000);
  assert.ok(stamp);
  assert.equal(motion.restyle(stamp, motion.describe(267, '{JasprApocalypse:{id:"sentinel_spear"}}')).clip.startsWith('spear-'), true);
  assert.equal(motion.restyle(stamp, { family: 'spear', key: 'another_item', id: 267 }), null, 'a different custom item cancels the stale swing');
});

test('a sword reaches the crosshair about a tenth of a second after the click', () => {
  // the tip of the blade crosses the middle of the screen during the forehand's strike
  const swing = swingOf('sword-forehand'), tipLocal = [0, 0, 0];
  let crossed = null;
  for (let i = 0; i <= 400 && crossed === null; i++) {
    const tick = swing.duration * i / 400, pose = T.evaluate(swing, tick);
    const tip = T.qrot(pose.q, [T.BLADE[0] * 0.68, T.BLADE[1] * 0.68, T.BLADE[2] * 0.68]);
    const x = (pose.g[0] + tip[0]) / -(pose.g[2] + tip[2]);
    if (pose.phase === 'stroke' && x < 0) crossed = tick;
    void tipLocal;
  }
  assert.ok(crossed !== null, 'the tip crosses the centre');
  assert.ok(crossed >= 1.6 && crossed <= 3.2, `contact at ${(crossed * 50).toFixed(0)} ms`);
});
