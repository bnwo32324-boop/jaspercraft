'use strict';
// Realm armoury stats in the client's own data (owner 2026-10-05: "Emerald should be better than Diamond. That includes the
// helmet"): every armour piece beats its diamond counterpart (3/8/6/3) in the plugin, and the recipe panel's results and the
// Creative catalogue, whose tooltips read the generic.armor modifier from each item's SNBT, say the same
// (scripts/refresh-armory-stats-client.cjs: a same-length, idempotent edit of one number per armour item).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const stage = require('../scripts/refresh-armory-stats-client.cjs');
const DIAMOND = {helmet: 3, chestplate: 8, leggings: 6, boots: 3};

test('armoury armour: every piece is stronger than diamond, the helmet and boots included', () => {
  const want = stage.wanted();
  for (const [piece, armor] of Object.entries(DIAMOND)) assert.ok(want[piece].armor > armor, piece + ' ' + want[piece].armor + ' against diamond ' + armor);
  assert.deepEqual(Object.fromEntries(Object.entries(want).map(([k, v]) => [k, v.armor])), {helmet: 4, chestplate: 9, leggings: 7, boots: 4});
});

test('client data: the repo\'s recipe table and panel module carry the plugin\'s armour amounts', () => {
  const want = stage.wanted();
  for (const file of ['client-mods/recipe-table.json', 'client-mods/recipe-book-teavm.js']) {
    const text = fs.readFileSync(path.join(root, file), 'latin1'), found = stage.scan(text);
    assert.equal(found.length, 24, file + ': six sets of four armour pieces');
    for (const f of found) assert.equal(f.amount, want[f.piece].armor, file + ' ' + f.piece);
  }
});

const source = process.env.ARMORY_STATS_SOURCE || path.join(root, 'site', 'classes.js');
test('client bundle: the edit is same-length, idempotent, reaches every armoury armour item and still parses', {skip: !fs.existsSync(source) && 'no client bundle'}, () => {
  const raw = fs.readFileSync(source, 'latin1'), want = stage.wanted();
  const first = stage.refresh(raw, want);
  assert.ok(first.seen >= 24 && first.seen % 4 === 0, 'armoury armour items found: ' + first.seen);
  assert.equal(first.text.length, raw.length, 'same length');
  assert.equal(stage.refresh(first.text, want).changed, 0, 'idempotent');
  for (const f of stage.scan(first.text)) assert.equal(f.amount, want[f.piece].armor, f.piece);
  // only digits of those amounts changed
  let differing = 0;
  for (let i = 0; i < raw.length; i++) if (raw.charCodeAt(i) !== first.text.charCodeAt(i)) differing++;
  assert.equal(differing, first.changed, 'one character per updated amount (3 -> 4)');
  new vm.Script(Buffer.from(first.text, 'latin1').toString('utf8'), {filename: 'classes.js'});
});
