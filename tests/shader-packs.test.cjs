'use strict';
// Golden vectors for the shader-pack runtime (client-mods/shader-packs.js).
const test = require('node:test');
const assert = require('node:assert/strict');
const sp = require('../client-mods/shader-packs.js');

test('pack labels cycle OFF through all installed packs and wrap', () => {
  assert.equal(sp.shaderPackLabel(0), 'OFF');
  assert.equal(sp.shaderPackLabel(1), 'MakeUp UltraFast');
  assert.equal(sp.shaderPackLabel(2), 'Chocapic13 Toaster');
  assert.equal(sp.shaderPackLabel(3), 'Miniature');
  assert.equal(sp.shaderPackLabel(4), "Sildur's Vibrant Lite");
  assert.equal(sp.shaderPackLabel(99), 'OFF');
  assert.deepEqual(sp.SHADER_PACK_SELECTOR_LABELS, ['OFF', 'MakeUp', 'Chocapic', 'Miniature', 'Sildur Lite']);
  assert.equal([...new Set(sp.SHADER_PACK_LABELS)].length, sp.SHADER_PACK_COUNT);
  assert.equal(sp.shaderPackSelectorLabel(4), 'Sildur Lite');
  assert.equal(sp.shaderPackSelectorLabel(99), 'OFF');
  assert.equal(sp.shaderPackCycle(0), 1);
  assert.equal(sp.shaderPackCycle(1), 2);
  assert.equal(sp.shaderPackCycle(2), 3);
  assert.equal(sp.shaderPackCycle(3), 4);
  assert.equal(sp.shaderPackCycle(4), 0);
});

test('Unreal tonemap matches the Chocapic13 formula', () => {
  assert.ok(Math.abs(sp.shaderTonemapUnreal(1.0) - 0.8830) < 0.001);
  assert.equal(sp.shaderTonemapUnreal(0), 0);
  assert.ok(sp.shaderTonemapUnreal(2.0) < 2.0, 'highlights roll off');
  assert.ok(sp.shaderTonemapUnreal(0.5) > sp.shaderTonemapUnreal(0.25), 'monotonic');
});

test('Sildur tonemap follows its normalized Uncharted2 final pass', () => {
  assert.ok(Math.abs(sp.shaderTonemapSildur(0)) < 1e-12);
  assert.ok(sp.shaderTonemapSildur(1) > 0 && sp.shaderTonemapSildur(1) < 1);
  const dark = sp.shaderGradePixel(4, 0.1, 0.1, 0.1, 0.5, 0.5);
  const bright = sp.shaderGradePixel(4, 0.5, 0.5, 0.5, 0.5, 0.5);
  assert.ok(bright.r > dark.r, 'Sildur remains monotonic');
  assert.ok(bright.r > 0.5 && bright.r < 0.8, 'Sildur keeps midtones below the blown-out range');
  assert.equal(bright.blurMix, 0);
});

test('Sildur profile is adapted for the display-encoded Eaglercraft backbuffer', () => {
  const grade = sp.shaderPackGrade(4);
  assert.equal(grade.exposure, 0.72);
  assert.equal(grade.gamma, 1.30);
  assert.equal(grade.vignette, 0.14);
  assert.ok(sp.shaderGradePixel(4, 0.8, 0.8, 0.8, 0.5, 0.5).r < 0.9, 'bright terrain retains highlight headroom');
});

test('OFF grade is identity, packs shape the image', () => {
  const id = sp.shaderGradePixel(0, 0.4, 0.5, 0.6, 0.5, 0.5);
  assert.ok(Math.abs(id.r - 0.4) < 1e-9 && Math.abs(id.g - 0.5) < 1e-9 && Math.abs(id.b - 0.6) < 1e-9);
  assert.equal(id.blurMix, 0);
  const makeup = sp.shaderGradePixel(1, 0.5, 0.5, 0.5, 0.5, 0.5);
  assert.ok(makeup.r > 0.5 && makeup.g > 0.5, 'makeup lifts mid gray');
  const choco = sp.shaderGradePixel(2, 1.0, 1.0, 1.0, 0.5, 0.5);
  assert.ok(choco.r < 1.0, 'unreal rolls off white');
  assert.ok(choco.r >= choco.g && choco.g >= choco.b, 'chocapic stays warm');
  const edge = sp.shaderGradePixel(3, 0.5, 0.5, 0.5, 0.5, 0.0);
  const center = sp.shaderGradePixel(3, 0.5, 0.5, 0.5, 0.5, 0.55);
  assert.equal(center.blurMix, 0);
  assert.ok(edge.blurMix > 0.5, 'miniature blurs the frame edges');
});

test('light texels follow torch/sky axes and stay in range', () => {
  for (const mode of [1, 2, 3, 4]) {
    const dark = sp.shaderLightTexel(mode, 0, 0);
    const torch = sp.shaderLightTexel(mode, 15, 0);
    const sky = sp.shaderLightTexel(mode, 0, 15);
    for (const v of dark.concat(torch, sky)) assert.ok(v >= 0 && v <= 1, 'mode ' + mode);
    assert.ok(torch[0] > dark[0] && sky[0] > dark[0], 'mode ' + mode + ' lights up');
    assert.ok(torch[0] >= torch[1] && torch[1] >= torch[2], 'mode ' + mode + ' torch is warm');
  }
  const chocoTorch = sp.shaderLightTexel(2, 15, 0);
  assert.ok(chocoTorch[1] / chocoTorch[0] < 0.6, 'chocapic torch strongly orange');
});

test('fog multipliers are neutral when off and shaped per pack', () => {
  assert.deepEqual(sp.shaderPackFog(0), [1.0, 1.0, 1.0]);
  assert.deepEqual(sp.shaderPackFog(99), [1.0, 1.0, 1.0]);
  const choco = sp.shaderPackFog(2);
  assert.ok(choco[0] > choco[2], 'chocapic fog runs warm');
  for (const mode of [1, 2, 3, 4]) {
    const fog = sp.shaderPackFog(mode);
    assert.equal(fog.length, 3);
    for (const v of fog) assert.ok(v > 0.5 && v < 1.5);
  }
  const sildur = sp.shaderPackFog(4);
  assert.ok(sildur[2] > sildur[0], 'Sildur fog keeps a cool sky tint');
});
