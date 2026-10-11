'use strict';
// Builders of the first-person melee module (region swap) and of the JASPR_COMBATFX stage, on the LIVE classes.js (read
// only): only the module region changes, the previous module puts the input back byte for byte, rebuilding is
// idempotent, Mo' Bends and every other stage stay untouched, the COMBATFX hooks sit on original engine text and come
// off exactly (unpatch(build(x)) === x), and the stage refuses to build under an outer stage.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const melee = require('../scripts/build-melee-client.cjs');
const fx = require('../scripts/build-combatfx-client.cjs');
const LIVE = process.env.MELEE_CLASSES_JS || melee.LIVE;
const live = fs.existsSync(LIVE);
let cache = null;
function builds() {
  if (cache) return cache;
  const input = fs.readFileSync(LIVE, 'latin1'), m = melee.build(input), f = fx.build(m.output);
  return (cache = { input, m, f });
}
const fnText = (s, name) => { const a = s.indexOf('\nfunction ' + name + '('); return a < 0 ? null : s.slice(a, s.indexOf('\nfunction ', a + 9)); };

test('the melee builder swaps only the module region, and the previous module puts the input back', { skip: !live }, () => {
  const { input, m } = builds();
  const [a, b] = melee.region(input), [c, d] = melee.region(m.output);
  assert.equal(m.output.slice(0, c), input.slice(0, a), 'everything before the region is unchanged');
  assert.equal(m.output.slice(d), input.slice(b), 'everything after the region is unchanged');
  assert.equal(m.output.slice(c, d), melee.moduleText());
  assert.equal(melee.restore(m.output, m.previous), input, 'restore(build(x)) === x');
  // Mo' Bends and the outer stages are untouched (no unpatching needed for a module swap)
  for (const fence of ['/* JASPR_MOBENDS_BEGIN */', '/* JASPR_MUTANTS_BEGIN */', '/* JASPR_BETTERCOMBAT_V1_BEGIN */'])
    if (input.includes(fence)) assert.ok(m.output.includes(fence), fence);
  for (const name of ['JasprCombatTransform', 'JasprCombatAction', 'Dk0', 'C45', 'DGZ']) assert.equal(fnText(m.output, name), fnText(input, name), name);
  for (const name of melee.EXPORTS) assert.ok(fnText(m.output, name), name);
  // rebuilding on its own output is idempotent
  assert.equal(melee.build(m.output).output, m.output);
});

test('JASPR_COMBATFX: hooks on original engine text, a fenced module, exact unpatch, idempotent, refuses an outer stage', { skip: !live }, () => {
  const { m, f } = builds();
  assert.equal(fx.unpatch(f.output), m.output, 'unpatch(build(x)) === x');
  assert.ok(fnText(f.output, 'FN4').includes('case 0:JasprCameraFXApply(a,b);c=a.bu.hi;'));
  assert.ok(fnText(f.output, 'Ddz').includes('JasprCameraFX.pass=1;FN4(a,b);if(B()){break _;}JasprCameraFX.pass=0;'));
  assert.ok(fnText(f.output, 'Ddz').includes('case 32:JasprCombatFXHand(a,b);DnA();'));
  const begin = f.output.indexOf(fx.BEGIN), end = f.output.indexOf(fx.END);
  assert.ok(begin > 0 && end > begin && f.output.indexOf(fx.BEGIN, begin + 1) < 0);
  assert.ok(f.output.slice(end + fx.END.length).trimStart().startsWith('}));'), 'the stage sits at the module footer, outermost so far');
  // only FN4 and Ddz change outside the fenced module
  const outside = s => s.slice(0, s.indexOf(fx.BEGIN)) + s.slice(s.indexOf(fx.END) + fx.END.length);
  const changed = [];
  for (const name of ['FN4', 'Ddz', 'Dk0', 'C45', 'Ch0', 'Chn', 'CCV', 'DbP', 'CHq']) if (fnText(outside(f.output), name) !== fnText(m.output, name)) changed.push(name);
  assert.deepEqual(changed, ['FN4', 'Ddz'], 'C45, Ch0 and Chn (the gun stage\'s hooks) are left alone');
  assert.equal(fx.build(f.output).output, f.output, 'idempotent');
  assert.throws(() => fx.build(f.output + '/* JASPR_GUNFX_BEGIN */'), /outer stage/);
  assert.throws(() => fx.unpatch(f.output.replace('}));', '/* JASPR_GUNFX_BEGIN *//* JASPR_GUNFX_END */}));')), /outer stage/);
});
