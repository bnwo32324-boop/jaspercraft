'use strict';
// Golden vectors shared with Waypoint.java (see EquipmentProbe "waypoints" phase).
const test = require('node:test');
const assert = require('node:assert/strict');
const codec = require('../client-mods/waypoint-codec.js');

test('golden vector V1 encodes exactly', () => {
  const wp = { slot: 0, name: 'Home', x: 123, y: 64, z: -456, color: 11, death: false };
  assert.equal(codec.waypointCoordHolder(wp), 'JW00b1njcl71njc54');
  assert.equal(codec.waypointNameHolder(wp), 'JN0Home');
  assert.deepEqual(codec.waypointParseCoordHolder('JW00b1njcl71njc54'),
    { slot: 0, death: false, color: 11, x: 123, z: -456 });
  assert.deepEqual(codec.waypointParseNameHolder('JN0Home'), { slot: 0, name: 'Home' });
});

test('golden vector V2 encodes extremes, death flag and color', () => {
  const wp = { slot: 14, name: 'Nether Hub', x: -30000000, y: -64, z: 30000000, color: 5, death: true };
  assert.equal(codec.waypointCoordHolder(wp), 'JWe1515occg25ecn4');
  assert.equal(codec.waypointNameHolder(wp), 'JNeNether Hub');
  assert.deepEqual(codec.waypointParseCoordHolder('JWe1515occg25ecn4'),
    { slot: 14, death: true, color: 5, x: -30000000, z: 30000000 });
});

test('malformed holders fail closed', () => {
  for (const bad of [null, '', 'JW', 'JW00b1njcl7', 'JW00b1njcl71njc5!', 'XX00b1njcl71njc54',
      'JW-0b1njcl71njc54', 'JW00b1njcl71njc54x', 'JN', 'JN0', 'XX0Home', 'JN-abc']) {
    assert.equal(codec.waypointParseCoordHolder(bad), null, String(bad));
  }
  assert.equal(codec.waypointParseNameHolder('JN' + 'A'.repeat(37)), null);
  assert.deepEqual(codec.waypointParseNameHolder('JN0'), { slot: 0, name: '' });
});

test('names sanitize identically to the server (no section signs, bounded)', () => {
  assert.equal(codec.waypointSanitizeName('  A  B  '), 'A B');
  assert.equal(codec.waypointSanitizeName('\u00a7cHi'), '?cHi');
  assert.equal(codec.waypointSanitizeName(''), 'Waypoint');
  assert.equal(codec.waypointSanitizeName(null), 'Waypoint');
  assert.equal(codec.waypointSanitizeName('x'.repeat(99)).length, 36);
});

test('out-of-range coordinates refuse to encode', () => {
  assert.equal(codec.waypointCoordHolder({ slot: 0, x: 30000001, z: 0, color: 0, death: false }), null);
  assert.equal(codec.waypointCoordHolder({ slot: 36, x: 0, z: 0, color: 0, death: false }), null);
});

test('color table has sixteen distinct entries', () => {
  assert.equal(codec.WAYPOINT_COLOR_NAMES.length, 16);
  assert.equal(codec.WAYPOINT_COLOR_RGB.length, 16);
  assert.equal(new Set(codec.WAYPOINT_COLOR_RGB).size, 16);
  assert.equal(codec.WAYPOINT_COLOR_NAMES[5], 'Lime');
});
