'use strict';
// scripts/build-bettercombat-client.cjs on the LIVE classes.js (read only): only the Better Combat head region changes,
// the engine-derived tail and every engine call site stay byte for byte, the result parses, a rebuild is a no-op, and a
// changed engine mapping stops the build. Run alone: node --test --test-concurrency=1 tests/bettercombat-build.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const B = require('../scripts/build-bettercombat-client.cjs');
const LIVE = process.env.BETTERCOMBAT_CLASSES_JS || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const live = fs.existsSync(LIVE);
let cache = null;
function built() { if (!cache) { const input = fs.readFileSync(LIVE, 'latin1'); cache = { input, output: B.build(input) }; } return cache; }

test('only the head region changes; the melee module, the tail and everything else are byte-identical', { skip: !live }, () => {
  const { input, output } = built();
  const a = B.headRange(input), b = B.headRange(output);
  assert.equal(a.start, b.start);
  assert.equal(output.slice(0, a.start), input.slice(0, a.start), 'before the head');
  assert.equal(output.slice(b.end), input.slice(a.end), 'the melee module, the end marker and the rest of the client');
  const oldHead = input.slice(a.start, a.end), newHead = output.slice(b.start, b.end);
  assert.ok(newHead.startsWith(B.adapterText()), 'the adapter source');
  assert.equal(newHead.slice(newHead.indexOf(B.TAIL)), oldHead.slice(oldHead.indexOf(B.TAIL)), 'engine-derived tail verbatim');
  assert.ok(output.includes("build:'bettercombat9'") && !output.includes("build:'bettercombat8'"));
  assert.ok(output.includes("setAttribute('data-jaspr-combat-build','bettercombat9')"));
});

test('a rebuild of the output is a no-op (idempotent)', { skip: !live }, () => {
  const { output } = built();
  assert.equal(B.build(output), output);
});

test('every engine call site into the head and every engine function stay unchanged', { skip: !live }, () => {
  const { input, output } = built();
  for (const name of ['Cfq', 'F$w', 'DRw', 'Dk0', 'Cyr', 'EyW', 'CDe', 'EQX', 'DOj', 'GgT', 'DzY', 'Ev$', 'Dr7', 'FKv', 'DGZ'])
    try { assert.equal(B.fnText(output, name), B.fnText(input, name), name); } catch (e) { if (!/missing native function/.test(e.message)) throw e; }
  // the head still defines what the engine and the other stages call (Dynamic Surroundings uses blocks() and JasprCombatSpecial)
  for (const name of ['JasprCombatAction', 'JasprCombatTick', 'JasprCombatIndicators', 'JasprCombatTransform', 'JasprCombatSweep',
    'JasprCombatOptionsButton', 'JasprCombatOpenOptions', 'JasprCombatSpecial', 'JasprCombatSend', 'JasprCombatPick', 'JasprCombatBox', 'JasprCombatSweepRender',
    'JasprCombatUse', 'JasprCombatOffWeapon', 'JasprCombatFeedPush'])
    assert.ok(B.fnText(output, name), name);
  assert.ok(output.includes('var JasprCombatFeed={seq:0,events:[],since:'));
  assert.ok(output.includes('if(b===a.v&&JasprBetterCombat.blocks(a))return;'), 'the Dynamic Surroundings hook still finds blocks()');
});

test('a changed engine mapping or region stops the build instead of guessing', { skip: !live }, () => {
  const { input } = built();
  const swap = (s, a, b) => { assert.equal(s.split(a).length, 2, 'anchor ' + a); return s.replace(a, () => b); };
  const cde = B.fnText(input, 'CDe');
  assert.throws(() => B.build(swap(input, cde, cde.split('a.G1=1;a.crX=b;').join('a.G1=1;a.crY=b;'))), /swingArm fields/);
  assert.throws(() => B.build(swap(input, 'function EQX(a){var $p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();a=$T.l();}_:while(true){switch($p){case 0:a.bDd=0;',
    'function EQX(a){var $p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();a=$T.l();}_:while(true){switch($p){case 0:a.bDe=0;')), /resetCooldown/);
  const right = B.fnText(input, 'F$w');
  assert.throws(() => B.build(swap(input, right, right.replace('a.bTh=4;', 'a.bTh=5;'))), /rightClickMouse/);
  assert.throws(() => B.build(input.replace(B.NEXT, '/* MOVED */')), /melee module/);
  const head = input.slice(B.headRange(input).start, B.headRange(input).end);
  assert.throws(() => B.build(input.replace(head, head.replace('function JasprCombatBox(b){', 'function JasprCombatBox(b){0;'))), /engine-derived head functions changed/);
});
