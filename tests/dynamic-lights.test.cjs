'use strict';
// Golden vectors shared with DynamicLights.java holder format.
const test = require('node:test');
const assert = require('node:assert/strict');
const dl = require('../client-mods/dynamic-lights.js');

test('golden holder encodes exactly and round-trips', () => {
  assert.equal(dl.dynamicLightsHolder(123, 64, -456, 14), '#jdl.123.64.-456.14');
  assert.deepEqual(dl.dynamicLightsParseHolder('#jdl.123.64.-456.14'), { x: 123, y: 64, z: -456, level: 14 });
});

test('golden holder covers extremes', () => {
  assert.equal(dl.dynamicLightsHolder(-30000000, 0, 30000000, 15), '#jdl.-30000000.0.30000000.15');
  assert.deepEqual(dl.dynamicLightsParseHolder('#jdl.-30000000.0.30000000.15'),
    { x: -30000000, y: 0, z: 30000000, level: 15 });
});

test('malformed holders fail closed', () => {
  for (const bad of [null, '', '#jdl', '#jdl.1.2.3', '#jdl.1.2.3.4.5', '#jdl.a.64.0.14',
      '#jdl.1.64.0.0', '#jdl.1.64.0.16', '#jdl.1.256.0.14', '#jdl.1.-1.0.14',
      '#jdl.30000001.64.0.14', 'JW00b1njcl71njc54', '#jdl.1.2.3.4x', '#jdl.1..0.14']) {
    assert.equal(dl.dynamicLightsParseHolder(bad), null, String(bad));
  }
  assert.equal(dl.dynamicLightsHolder(0, 64, 0, 0), null);
  assert.equal(dl.dynamicLightsHolder(0, 64, 0, 16), null);
  assert.equal(dl.dynamicLightsHolder(0, 256, 0, 14), null);
});

test('torch falloff drops one level per block (FAST)', () => {
  const sources = [{ x: 0, y: 64, z: 0, level: 14 }];
  assert.equal(dl.dynamicLightsLevelAt(sources, 0, 64, 0, dl.DYNAMIC_LIGHTS_FAST), 14);
  assert.equal(dl.dynamicLightsLevelAt(sources, 5, 64, 0, dl.DYNAMIC_LIGHTS_FAST), 9);
  assert.equal(dl.dynamicLightsLevelAt(sources, 14, 64, 0, dl.DYNAMIC_LIGHTS_FAST), 0);
  assert.equal(dl.dynamicLightsLevelAt(sources, 15, 64, 0, dl.DYNAMIC_LIGHTS_FAST), 0);
});

test('smooth mode reaches further and softer', () => {
  const sources = [{ x: 0, y: 64, z: 0, level: 14 }];
  assert.equal(dl.dynamicLightsRadius(14, dl.DYNAMIC_LIGHTS_SMOOTH), 20);
  assert.equal(dl.dynamicLightsRadius(14, dl.DYNAMIC_LIGHTS_FAST), 14);
  assert.ok(dl.dynamicLightsLevelAt(sources, 15, 64, 0, dl.DYNAMIC_LIGHTS_SMOOTH) > 0);
  assert.equal(dl.dynamicLightsLevelAt(sources, 21, 64, 0, dl.DYNAMIC_LIGHTS_SMOOTH), 0);
});

test('mix takes the max and never darkens', () => {
  const sources = [{ x: 0, y: 64, z: 0, level: 14 }];
  const sky15dark = (15 << 20) | (0 << 4);
  const mixed = dl.dynamicLightsMix(sky15dark, 0, 64, 0, sources, dl.DYNAMIC_LIGHTS_FAST);
  assert.equal(mixed, (15 << 20) | (14 << 4));
  // Native torch already 14: unchanged.
  assert.equal(dl.dynamicLightsMix((15 << 20) | (14 << 4), 0, 64, 0, sources, dl.DYNAMIC_LIGHTS_FAST),
    (15 << 20) | (14 << 4));
  // Far away: unchanged.
  assert.equal(dl.dynamicLightsMix(sky15dark, 100, 64, 100, sources, dl.DYNAMIC_LIGHTS_FAST), sky15dark);
  // Sky bits preserved exactly.
  assert.equal((mixed >> 20) & 15, 15);
});

test('signature is order-independent', () => {
  assert.equal(dl.dynamicLightsSignature(['b', 'a']), dl.dynamicLightsSignature(['a', 'b']));
  assert.notEqual(dl.dynamicLightsSignature(['a']), dl.dynamicLightsSignature(['a', 'b']));
});

test('world sampler injects at the entity position and fails closed', () => {
  const sources = [{ x: 10, y: 64, z: 10, level: 14 }];
  const pos = { m: 10, i: 64, l: 10 };
  const dark = (0 << 20) | (0 << 4);
  const fakeOrig = (world, p, value) => {
    assert.equal(p, pos);
    assert.equal(value, 0);
    return dark;
  };
  assert.equal(dl.dynamicLightsSampleWorld(fakeOrig, {}, pos, 0, dl.DYNAMIC_LIGHTS_FAST, sources),
    (0 << 20) | (14 << 4));
  // OFF mode and empty sources pass through untouched.
  assert.equal(dl.dynamicLightsSampleWorld(fakeOrig, {}, pos, 0, dl.DYNAMIC_LIGHTS_OFF, sources), dark);
  assert.equal(dl.dynamicLightsSampleWorld(fakeOrig, {}, pos, 0, dl.DYNAMIC_LIGHTS_FAST, []), dark);
  // A throwing native still returns its value; broken math returns native.
  assert.equal(dl.dynamicLightsSampleWorld(() => dark, {}, null, 0, dl.DYNAMIC_LIGHTS_FAST, sources), dark);
});
