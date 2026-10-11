'use strict';
// JASPR_COMBATFX runtime (client-mods/combatfx/combatfx-runtime.js), offline: camera kicks are bounded, decay to nothing,
// follow the shared sign convention (positive pitch raises the view, as the gun stage expects) and are halved in third
// person; melee impact intents become kicks and bounded spark plans; trails become mirrored, fading ribbons.
const test = require('node:test');
const assert = require('node:assert/strict');
function fresh() {
  delete require.cache[require.resolve('../client-mods/combatfx/combatfx-runtime.js')];
  return require('../client-mods/combatfx/combatfx-runtime.js');
}

test('kicks: clamped, additive, decaying to nothing, bounded in number', () => {
  const { JasprCameraFX: K } = fresh();
  assert.equal(K.offsets(100, false), null, 'nothing running');
  assert.equal(K.kick({ pitch: 50, yaw: -50, roll: 9, back: 3, ms: 99999 }), true);
  let o = K.offsets(100.2, false);
  assert.ok(o.pitch > 0 && o.pitch <= 6 && o.yaw < 0 && o.yaw >= -6 && o.roll <= 6 && o.back <= 0.25, JSON.stringify(o));
  for (let i = 0; i < 30; i++) K.kick({ pitch: 6, ms: 1000 });
  o = K.offsets(100.4, false);
  assert.ok(o.pitch <= 8 && o.back <= 0.3, 'the sum is clamped');
  assert.ok(K.status().running <= 12 && K.status().dropped >= 19, 'at most 12 at once, the oldest dropped');
  assert.equal(K.offsets(100 + 1000 / 50 + 1, false), null, 'everything decays away');
  assert.equal(K.kick({}), false, 'an empty kick is refused');
  assert.equal(K.kick(null), false);
  assert.equal(K.kick({ pitch: NaN, yaw: 'x' }), false);
});

test('kick shape: punch rises fast and falls smoothly; third person gets half; restarted tick counts drop kicks', () => {
  const { JasprCameraFX: K } = fresh();
  K.offsets(10, false);
  K.kick({ pitch: 2, ms: 200, curve: 'punch' });          // 4 ticks
  const v = [10, 10.2, 10.5, 10.72, 11, 12, 13, 13.9].map(t => { const o = K.offsets(t, false); return o ? o.pitch : 0; });
  assert.ok(v[0] === 0 && v[1] > 0.5 && v[3] > 1.9 && v[4] < v[3] && v[5] < v[4] && v[6] < v[5] && v[7] < 0.05, v.join(' '));
  K.kick({ yaw: 2, ms: 400, curve: 'smooth' });
  const a = K.offsets(15, false).yaw, b = K.offsets(15, true).yaw;
  assert.ok(Math.abs(b - a / 2) < 1e-9, 'third person: half');
  K.kick({ roll: 3, ms: 400 });
  assert.equal(K.offsets(2, false), null, 'respawn: the tick count went back, the kicks are gone');
  for (const c of Object.keys(K.CURVES)) for (const u of [0, 0.3, 0.7, 0.999]) assert.ok(Math.abs(K.CURVES[c](u)) <= 1.0001, c);
});

test('melee impacts: a dipping chop becomes a negative (downward) camera pitch; sparks are planned once per frame and bounded', () => {
  const { JasprCameraFX: K, JasprCombatFX: F } = fresh();
  K.offsets(50, false);
  const queue = [{ kind: 'impact', hand: 'M', cls: 'axe', target: 12, crit: true, kick: { pitch: 1.0, yaw: -0.3, roll: -0.5, back: 0, ms: 150, curve: 'punch' }, sparks: 'blade', dir: [-0.3, -1] },
    { kind: 'impact', hand: 'O', cls: 'sword', target: -1, kick: { pitch: 0.3, yaw: 0.2, roll: 0.3, back: 0, ms: 110 }, sparks: 'blade', dir: [1, 0.3] }];
  const melee = { drain: () => queue.splice(0) };
  const sparks = F.frame(50.5, melee);
  assert.equal(sparks.length, 1, 'only an impact on an entity gets sparks');
  assert.equal(F.frame(50.5, melee), null, 'once per frame');
  const o = K.offsets(51, false);
  assert.ok(o.pitch < 0, 'the view dips: ' + o.pitch);
  const rnd = (s => () => (s = (s * 16807) % 2147483647) / 2147483647)(7);
  const plan = F.sparkPlan(sparks[0], { x0: 0, y0: 64, z0: 2, x1: 0.6, y1: 65.95, z1: 2.6 }, [0.3, 65.62, 0], 0, 0, rnd, 51);
  assert.ok(plan.length > 0 && plan.length <= 9, 'a handful of sparks');
  assert.ok(plan.some(p => p.id === 10), 'a crit adds magic-crit sparks');
  for (const p of plan) { assert.ok(p.z >= 1.6 && p.z <= 2.4, 'on the near side of the target'); for (const k of ['x', 'y', 'z', 'vx', 'vy', 'vz']) assert.ok(Number.isFinite(p[k])); }
  let total = 0;
  for (let i = 0; i < 30; i++) total += F.sparkPlan(sparks[0], { x0: 0, y0: 64, z0: 2, x1: 0.6, y1: 65.95, z1: 2.6 }, [0.3, 65.62, 0], 0, 0, rnd, 51).length;
  assert.ok(total + plan.length <= 48, 'at most 48 sparks a second');
});

test('trails: a fading ribbon, brightest along the edge, mirrored for the left hand, nothing when off', () => {
  const { JasprCombatFX: F } = fresh();
  const pts = [];
  for (let i = 0; i < 6; i++) pts.push(0.2 + i * 0.05, -0.3, -0.6, 0.5 + i * 0.05, 0.1, -0.7, 1 - i / 6);
  const right = F.trailVertices({ n: 6, pts, right: true, color: [1, 0.5, 0.25], width: 1 });
  const left = F.trailVertices({ n: 6, pts, right: false, color: [1, 0.5, 0.25], width: 1 });
  assert.equal(right.length, 5 * 4 * 7, 'four vertices per segment');
  for (let i = 0; i < right.length; i += 7) { assert.equal(left[i], -right[i]); assert.equal(left[i + 1], right[i + 1]); assert.ok(right[i + 6] >= 0 && right[i + 6] <= 1); }
  assert.ok(right[1 * 7 + 6] > right[0 * 7 + 6] * 5, 'the edge is brighter than the hilt side');
  assert.ok(right[6] > right[(4 * 4) * 7 + 6], 'the tail fades');
  F.parts.trails = false;
  assert.equal(F.trailVertices({ n: 6, pts, right: true }).length, 0);
});
