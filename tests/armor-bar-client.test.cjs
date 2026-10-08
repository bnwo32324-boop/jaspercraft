'use strict';
// Overloaded Armor Bar port (scripts/build-armor-bar-client.cjs; the mod is Overloaded Armor Bar 1.0.4g, MIT). Builds the
// stage on the client bundle (ARMOR_BAR_SOURCE or site/classes.js), checks it is exact, reversible and stable, then runs
// the ported renderer against stand-ins for the engine and compares every icon with what the mod draws.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const stage = require('../scripts/build-armor-bar-client.cjs');
const source = process.env.ARMOR_BAR_SOURCE || path.join(root, 'site', 'classes.js');
const raw = fs.existsSync(source) ? fs.readFileSync(source, 'latin1') : '';
const usable = stage.EDITS.every(([vanilla, patched]) => raw.includes(vanilla) || raw.includes(patched));

test('armour bar stage: both loop entries, exact, reversible, stable, parses', {skip: !usable && 'client bundle without the armour loop'}, () => {
  const {base, result} = stage.build(raw);
  assert.equal(stage.strip(result), base);
  assert.equal(stage.build(result).result, result, 'rebuilding a patched client changes nothing');
  const start = result.indexOf('\nfunction DJ1(') + 1, end = result.indexOf('\nfunction ', start);
  const fn = result.slice(start, end);
  assert.equal(fn.split('case 90:').length, 2, 'one new state');
  assert.equal(fn.split('$p=90;continue _;').length, 3, 'both entries jump to it');
  assert.equal(fn.split('/*JASPR_ARMORBAR_V1*/').length, 3);
  assert.match(fn, /case 90:JasprArmorBarDraw\(a,n,w,y\);if\(B\(\)\)\{break _;\}\$p=20;continue _;/);
});

/** Runs JasprArmorBarDraw with stand-ins; returns the draws as "colour:x:u:w" strings and the final colour. */
function harness(journal) {
  const calls = [];
  let color = 'ffffff';
  const hex = v => Math.round(v * 255).toString(16).padStart(2, '0');
  const ctx = {FX: () => false, B: () => false, Ds: () => ({l() {}, s() {}}), FT: () => { throw new Error('FT'); },
    CFh: (r, g, b) => { color = hex(r) + hex(g) + hex(b); },
    FYu: (gui, x, y, u, v, w, h) => { assert.equal(v, 9); assert.equal(h, 9); assert.equal(y, 200); calls.push(color + ':' + x + ':' + u + ':' + w); }};
  if (journal !== undefined) ctx.JasprJournal = journal;      // the Field Journal stage's module, when the client has it
  vm.createContext(ctx);
  const m = stage.MODULE;
  vm.runInContext(m + '\nthis.draw = JasprArmorBarDraw; this.bar = JasprArmorBar;', ctx);
  return armor => { calls.length = 0; ctx.draw({}, 100, 200, armor); return {calls: calls.slice(), color, bar: ctx.bar}; };
}

test('armour bar: the mod\'s icons for every case', () => {
  const draw = harness();
  const W = 'ffffff', O = 'ff5500', G = 'ffc747', C = '27ffe3', N = '00ff00', P = '7f00ff';
  const full = (c, i) => c + ':' + (100 + i * 8) + ':34:9';
  const empty = i => W + ':' + (100 + i * 8) + ':16:9';
  const half = (left, right, i, wrapped) => [left + ':' + (100 + i * 8) + ':25:5', right + ':' + (105 + i * 8) + ':' + (wrapped ? 39 : 30) + ':4'];
  assert.deepEqual(draw(0).calls, [], 'no armour, no bar (as vanilla)');
  // under 20: vanilla look - full, half, empty outlines
  assert.deepEqual(draw(7).calls, [full(W, 0), full(W, 1), full(W, 2), ...half(W, W, 3, false), empty(4), empty(5), empty(6), empty(7), empty(8), empty(9)]);
  assert.deepEqual(draw(20).calls, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => full(W, i)), 'exactly full stays white');
  // wrapped: the next row in orange over a full white row
  assert.deepEqual(draw(22).calls, [full(O, 0), ...[1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => full(W, i))]);
  assert.deepEqual(draw(23).calls, [full(O, 0), ...half(O, W, 1, true), ...[2, 3, 4, 5, 6, 7, 8, 9].map(i => full(W, i))]);
  assert.deepEqual(draw(30).calls, [...[0, 1, 2, 3, 4].map(i => full(O, i)), ...[5, 6, 7, 8, 9].map(i => full(W, i))], 'the 1.12 cap');
  assert.deepEqual(draw(40).calls, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => full(O, i)));
  assert.deepEqual(draw(41).calls, [...half(G, O, 0, true), ...[1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => full(O, i))]);
  assert.deepEqual(draw(62).calls[0], full(C, 0));
  assert.deepEqual(draw(82).calls[0], full(N, 0));
  assert.deepEqual(draw(102).calls[0], full(P, 0));
  assert.deepEqual(draw(300).calls[0], full(P, 0), 'the last colour repeats');
  const r = draw(23);
  assert.equal(r.color, W, 'the colour is reset to white afterwards');
  assert.ok(r.bar.stats.recalcs >= 10 && r.bar.stats.wrapped > 0);
});

test('armour bar: the armour the worn armaments add is drawn (owner 2026-10-07: two identical boots, one with +40% protection, drew the same bar)', () => {
  // Without the Journal module, with one that has no data, and with garbage: the bar is exactly vanilla's, whatever the armour.
  for (const journal of [undefined, null, {}, {armorBonus: 5}, {armorBonus: () => 0}, {armorBonus: () => -4}, {armorBonus: () => { throw new Error('boom'); }}]) {
    const draw = harness(journal), plain = harness();
    for (const armor of [0, 7, 20, 22, 23, 30]) assert.deepEqual(draw(armor).calls, plain(armor).calls, 'no bonus: ' + JSON.stringify(journal) + ' armour ' + armor);
  }
  // The bonus is whole points added to the armour attribute: 22 + 2 draws what 24 does, 22 + 9 what 31 does, 7 + 3 what 10 does.
  const plain = harness();
  for (const [armor, bonus] of [[22, 2], [22, 9], [7, 3], [20, 1], [30, 10], [1, 1]]) {
    const boosted = harness({armorBonus: () => bonus})(armor);
    assert.deepEqual(boosted.calls, plain(armor + bonus).calls, `${armor} + ${bonus}`);
    assert.equal(boosted.bar.stats.boosted, 1);
  }
  // A string or a fraction from the Journal is cut to whole points (the Journal sends integers; this is only a guard).
  assert.deepEqual(harness({armorBonus: () => '3'})(20).calls, plain(23).calls);
  assert.deepEqual(harness({armorBonus: () => 2.9})(20).calls, plain(22).calls);
  // No armour, no bar: a bonus alone never draws one (an armament piece always has armour of its own).
  assert.deepEqual(harness({armorBonus: () => 6})(0).calls, [], 'no armour, no bar');
  // A change of the bonus changes the bar on the next frame (the cache follows the sum, not the attribute).
  let bonus = 0;
  const live = harness({armorBonus: () => bonus});
  const before = live(22).calls.join();
  bonus = 4;
  const after = live(22);
  assert.notEqual(after.calls.join(), before, 'putting the armament on changes the bar');
  assert.deepEqual(after.calls, plain(26).calls);
  bonus = 0;
  assert.equal(live(22).calls.join(), before, 'taking it off changes it back');
  assert.equal(typeof after.bar.extra, 'function');
});

test('armour bar: the port follows the mod source', () => {
  const src = 'C:/Users/AM/Downloads/OverloadedArmorBar-1.12/OverloadedArmorBar-1.12/src/main/java/locusway/overpoweredarmorbar';
  if (!fs.existsSync(src)) return;
  const config = fs.readFileSync(path.join(src, 'ModConfig.java'), 'utf8');
  const colors = [...config.matchAll(/"#([0-9A-F]{6})"/g)].map(m => m[1].toLowerCase());
  assert.deepEqual(colors, ['ffffff', 'ff5500', 'ffc747', '27ffe3', '00ff00', '7f00ff']);
  const bar = fs.readFileSync(path.join(src, 'overlay/ArmorBar.java'), 'utf8');
  assert.match(bar, /if\(scale > 0 && counter == 0\)/);
});
