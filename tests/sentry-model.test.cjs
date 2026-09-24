'use strict';
// Sentry turret presentation: custom iron-pickaxe carrier model, damage-band
// selector with vanilla fallback, and Java item-definition consistency.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { validate } = require('../scripts/build-apocalypse-pack.cjs');
const creative = require('../scripts/creative-catalog.cjs');

const root = path.resolve(__dirname, '..');
const read = name => fs.readFileSync(path.join(root, 'apocalypse-pack/assets/minecraft/models/item', name), 'utf8');
const model = JSON.parse(read('apocalypse_sentry_turret.json'));
const selector = JSON.parse(read('iron_pickaxe.json'));
const fallback = JSON.parse(read('apocalypse_vanilla_iron_pickaxe.json'));
const BAND = 100, MAX = 251;

test('turret model is bounded six-times effective geometry on verified vanilla textures', () => {
  assert.ok(model.elements.length >= 4 && model.elements.length <= 32, 'geometry budget');
  if (model.parent) assert.ok(['item/handheld', 'item/generated'].includes(model.parent));
  for (const texture of Object.values(model.textures)) assert.ok(!texture.startsWith('#'));
  const names = model.elements.map(e => e.__comment);
  assert.ok(new Set(names).size === names.length, 'every cuboid is named');
  assert.ok(names.some(n => /barrel/i.test(n)), 'barrel cuboid present');
  assert.ok(names.some(n => /eye|lamp|beacon/i.test(n)), 'targeting lamp present');
  const barrel = model.elements.find(e => /barrel facing negative Z/i.test(e.__comment));
  const dome = model.elements.find(e => /sensor dome/i.test(e.__comment));
  assert.ok(barrel.from[2] < dome.from[2] && barrel.to[2] <= dome.from[2], 'muzzle faces negative Z, ahead of the dome');
  assert.deepEqual(model.display.head.rotation, [0, 0, 0], 'head mount keeps model forward, never mirrored');
  assert.deepEqual(model.display.head.scale, [4, 4, 4], 'head display transform stays within the vanilla clamp');
  const base = model.elements.find(e => e.__comment === 'base plate');
  assert.deepEqual(base.from, [1.25, -3.5, 1.25], 'base plate has the flush mount offset');
  assert.deepEqual(base.to, [14.75, -0.5, 14.75], 'base plate keeps its six-times footprint');
  for (const view of ['firstperson_righthand', 'firstperson_lefthand', 'thirdperson_righthand',
      'thirdperson_lefthand', 'gui', 'ground', 'fixed']) {
    assert.ok(model.display[view], 'missing ' + view);
  }
});

test('turret geometry is distinct from every gun and blade', () => {
  const mine = JSON.stringify(model.elements.map(({ from, to }) => ({ from, to })));
  const result = validate();
  for (const entry of result.entries) {
    const name = entry.name;
    if (!name.endsWith('.json') || name.endsWith('sentry_turret.json')) continue;
    if (name.endsWith('iron_pickaxe.json') || name.includes('vanilla')) continue;
    const other = JSON.parse(entry.data.toString('utf8'));
    if (!other.elements) continue;
    assert.notEqual(JSON.stringify(other.elements.map(({ from, to }) => ({ from, to }))), mine, name + ' recolored');
  }
});

test('pickaxe selector isolates the turret band with vanilla fallback', () => {
  assert.equal(selector.overrides.length, 3);
  const [custom, plain, damaged] = selector.overrides;
  assert.deepEqual(custom.predicate, { damaged: 0, damage: 100 / 251 });
  assert.equal(custom.model, 'item/apocalypse_sentry_turret');
  assert.equal(plain.model, 'item/apocalypse_vanilla_iron_pickaxe');
  assert.deepEqual(damaged, { predicate: { damaged: 1 }, model: 'item/apocalypse_vanilla_iron_pickaxe' });
  assert.ok(Math.abs(custom.predicate.damage - 100 / 251) < 1e-12, 'exact float band');
  assert.ok(!fallback.overrides, 'no fallback cycles');
  // Emulate 1.12 float predicates with last-match precedence over every durability.
  const fround = v => Math.fround(v);
  for (let durability = 0; durability <= MAX; durability++) for (const unbreakable of [false, true]) {
    const properties = { damage: fround(durability / MAX), damaged: !unbreakable && durability > 0 ? 1 : 0 };
    let selected = 'item/apocalypse_vanilla_iron_pickaxe';
    for (const override of selector.overrides) {
      if (Object.entries(override.predicate).every(([k, v]) => properties[k] >= fround(v))) selected = override.model;
    }
    assert.equal(selected, unbreakable && durability === BAND ? 'item/apocalypse_sentry_turret' : 'item/apocalypse_vanilla_iron_pickaxe',
      `pickaxe damage=${durability} unbreakable=${unbreakable}`);
  }
});

test('Java item definition matches the model band and catalogue template', () => {
  const java = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'), 'utf8');
  assert.match(java, /new Spec\("sentry_turret", "Sentry Turret", "block", Material\.IRON_PICKAXE, 100, 0, 0,/);
  const entry = creative.catalogue().find(item => item.id === 'sentry_turret');
  assert.ok(entry);
  assert.equal(entry.category, 'block');
  assert.equal(entry.material, 'minecraft:iron_pickaxe');
  assert.equal(entry.model, 100);
  assert.match(entry.snbt, /Damage:100s/);
  assert.ok(entry.search.includes('turret') && entry.search.includes('sentry'));
});
