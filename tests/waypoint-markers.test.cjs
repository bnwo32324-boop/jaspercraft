'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
// Mirror the browser bundle: codec globals are concatenated before the markers file.
Object.assign(globalThis, require('../client-mods/waypoint-codec.js'));
const { createJasprWaypointMarkers } = require('../client-mods/waypoint-markers.js');

function runtime() { return createJasprWaypointMarkers({}); }
function entries() {
  return [
    { holder: 'JW00b1njcl71njc54', score: 64 },
    { holder: 'JN0Home', score: 0 },
    { holder: 'JWe1515occg25ecn4', score: -64 },
    { holder: 'JNeNether Hub', score: 0 }
  ];
}

test('scoreboard entries join into slot-ordered markers with names', () => {
  const markers = runtime().sync(entries());
  assert.equal(markers.length, 2);
  assert.deepEqual([markers[0].slot, markers[1].slot], [0, 14]);
  assert.deepEqual([markers[0].name, markers[1].name], ['Home', 'Nether Hub']);
  assert.deepEqual([markers[0].x, markers[0].y, markers[0].z], [123, 64, -456]);
  assert.deepEqual([markers[1].death, markers[1].color], [true, 5]);
});

test('dangling halves and garbage never become markers', () => {
  const rt = runtime();
  assert.deepEqual(rt.sync([{ holder: 'JW00b1njcl71njc54', score: 64 }])[0].name, '');
  assert.deepEqual(rt.sync([{ holder: 'JN0Home', score: 0 }]), []);
  assert.deepEqual(rt.sync([{ holder: 'XX00b1njcl71njc54', score: 1 }]), []);
  assert.deepEqual(rt.sync([{ holder: null, score: 1 }]), []);
  assert.deepEqual(rt.sync([]), []);
});

test('marker list is bounded at twenty-four', () => {
  const many = [];
  for (let s = 0; s < 36; s++) {
    const slot = '0123456789abcdefghijklmnopqrstuvwxyz'[s];
    many.push({ holder: 'JW' + slot + '0b1njcl71njc54', score: 64 });
  }
  assert.equal(runtime().sync(many).length, 24);
});

test('beams are two crossed quads in camera space with the marker color', () => {
  const rt = runtime();
  const [marker] = rt.sync(entries());
  const beams = rt.draw([marker], [100, 60, 100]);
  assert.equal(beams.length, 1);
  assert.deepEqual(beams[0].color, [0x00 / 255, 0x00 / 255, 0xFF / 255]);
  const [a, b] = beams[0].quads;
  // Marker (123.5, 64, -455.5), camera (100, 60, 100): relative center (23.5, y, -555.5).
  for (const q of [a, b]) assert.equal(q.points.length, 4);
  assert.deepEqual(a.points[0], [23.5 - 0.35, 64 - 4 - 60, -555.5]);
  assert.deepEqual(a.points[2], [23.5 + 0.35, 64 + 72 - 60, -555.5]);
  assert.deepEqual(a.normal, [0, 0, 1]);
  assert.deepEqual(b.normal, [1, 0, 0]);
  assert.ok(b.points[0][2] < b.points[1][2], 'second quad spans depth');
});

test('unusable beams are skipped, never thrown', () => {
  const rt = runtime();
  assert.deepEqual(rt.draw([{ slot: 0 }], [0, 0, 0]), []);
  assert.deepEqual(rt.draw([{ slot: 0, x: 1, y: 2, z: 3, color: 0 }], null), []);
  assert.deepEqual(rt.draw([{ slot: 0, x: 1, y: 2, z: 3, color: 0 }], [NaN, 0, 0]), []);
});

test('status reports counts and fails closed after repeated errors', () => {
  const rt = runtime();
  assert.deepEqual(rt.status().markers, 0);
  rt.sync(entries());
  assert.equal(rt.status().markers, 2);
  assert.equal(rt.isDisabled(), false);
  for (let i = 0; i < 6; i++) rt.noteError();
  assert.equal(rt.isDisabled(), true);
  rt.reset();
  assert.equal(rt.isDisabled(), false);
});

test('rgb unpacks the shared color table', () => {
  const rt = runtime();
  assert.deepEqual(rt.rgb(5), [0, 1, 0]);
  assert.deepEqual(rt.rgb(0), [1, 1, 1]);
  assert.deepEqual(rt.rgb(14), [1, 0, 0]);
});
