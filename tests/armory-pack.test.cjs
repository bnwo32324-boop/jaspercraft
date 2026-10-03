'use strict';
// Realm armoury assets (scripts/build-armory-pack.cjs, JasprGear 5.0.0): icons, worn layers, models and the diamond base
// items' overrides, applied on top of an archive (ARMORY_PACK_SOURCE or site/assets.epk) without touching anything else.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const pack = require('../scripts/build-armory-pack.cjs');
const {decode} = require('../scripts/merge-apocalypse-assets.cjs');
const png = require('../scripts/png-codec.cjs');
const source = process.env.ARMORY_PACK_SOURCE || path.join(root, 'site', 'assets.epk');

test('armoury pack: textures, models, overrides; idempotent; nothing else changes', {skip: !fs.existsSync(source) && 'no client archive'}, () => {
  const input = fs.readFileSync(source);
  const first = pack.build(input);
  const names = Object.keys(first.files);
  const icons = names.filter(n => /textures\/items\/jaspr_armory_/.test(n)), worn = names.filter(n => /textures\/models\/armor\/jaspr_/.test(n));
  const models = names.filter(n => /models\/item\/jaspr_armory_/.test(n));
  assert.equal(icons.length, 54); assert.equal(worn.length, 12); assert.equal(models.length, 54);
  for (const n of icons) { const img = png.decode(first.files[n]); assert.deepEqual([img.w, img.h], [16, 16], n); }
  for (const n of worn) { const img = png.decode(first.files[n]); assert.deepEqual([img.w, img.h], [64, 32], n); }
  // each base item: one override per set at model/max, a fallback just above, the damaged entry last
  for (const [piece, base, max] of pack.PIECES) {
    const file = names.find(n => n.endsWith('models/item/' + base + '.json'));
    const j = JSON.parse(first.files[file].toString('utf8'));
    const last = j.overrides[j.overrides.length - 1];
    assert.deepEqual(last.predicate, {damaged: 1}, base);
    for (const set of pack.SETS) {
      const i = j.overrides.findIndex(o => o.model === 'item/' + pack.name(set, piece));
      assert.ok(i >= 0, base + ' ' + set);
      assert.equal(j.overrides[i].predicate.damage, pack.model(set) / max);
      assert.equal(j.overrides[i + 1].predicate.damage, (pack.model(set) + 1) / max);
      assert.equal(j.overrides[i + 1].model, last.model, 'fallback is the base model\'s own vanilla look');
    }
    const thresholds = j.overrides.slice(0, -1).map(o => o.predicate.damage);
    assert.deepEqual(thresholds, [...thresholds].sort((a, b) => a - b), base + ' sorted');
  }
  // re-running on its own output changes nothing; untouched entries are byte-identical
  const second = pack.build(first.output);
  for (const n of names) assert.deepEqual(second.files[n], first.files[n], 'idempotent ' + n);
  const before = new Map(decode(input).entries.map(e => [e.name, e.value]));
  const after = new Map(decode(first.output).entries.map(e => [e.name, e.value]));
  for (const [n, v] of before) if (!names.includes(n)) assert.deepEqual(after.get(n), v, 'unchanged ' + n);
  for (const n of after.keys()) assert.ok(before.has(n) || names.includes(n), 'only our additions: ' + n);
});

test('armoury pack: sets and pieces match the server', () => {
  const sets = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear/ArmorySet.java'), 'utf8');
  assert.deepEqual([...sets.matchAll(/^    [A-Z]+\("([a-z]+)", "/gm)].map(m => m[1]), pack.SETS);
  assert.match(sets, /int model\(\) \{ return 100 \+ 2 \* ordinal\(\); \}/);
  const pieces = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear/ArmoryPiece.java'), 'utf8');
  assert.deepEqual([...pieces.matchAll(/^    [A-Z]+\("([a-z]+)", "[A-Z][a-z]+", Material\.DIAMOND_[A-Z]+/gm)].map(m => m[1]), pack.PIECES.map(p => p[0]));
});
