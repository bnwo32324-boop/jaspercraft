'use strict';
// Realm armoury worn skins in the browser client (scripts/build-armory-client.cjs, JasprGear 5.0.0). Builds the stage on
// the client bundle (ARMORY_CLIENT_SOURCE or site/classes.js), checks it is exact, reversible and stable, then runs the
// patched LayerArmorBase.renderArmorLayer (D$Y) against stand-ins for the engine to see which texture it binds.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const stage = require('../scripts/build-armory-client.cjs');
const source = process.env.ARMORY_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const raw = fs.existsSync(source) ? fs.readFileSync(source, 'latin1') : '';
const usable = raw.includes(stage.ANCHOR) || raw.includes(stage.PATCHED);

test('armoury client stage: exact, reversible, stable, parses', {skip: !usable && 'client bundle without the armour layer anchor'}, () => {
  const {base, result} = stage.build(raw);
  assert.equal(stage.strip(result), base);
  assert.equal(stage.build(result).result, result, 'rebuilding a patched client changes nothing');
  assert.equal(result.split(stage.PATCHED).length, 2);
  assert.ok(result.indexOf('/* JASPR_ARMORY_BEGIN */') < result.indexOf('\nvar JasprCreativeCatalog=['));
});

test('armoury client stage: binds the set texture, the vanilla one otherwise', {skip: !usable && 'client bundle without the armour layer anchor'}, () => {
  const {result} = stage.build(raw);
  const start = result.indexOf('\nfunction D$Y(') + 1, end = result.indexOf('\nfunction ', start);
  const fn = result.slice(start, end);
  const module = result.slice(result.indexOf('/* JASPR_ARMORY_BEGIN */'), result.indexOf('/* JASPR_ARMORY_END */'));
  const bound = [];
  const ctx = {
    FX: () => false, B: () => false, Ds: () => ({l() {}, s() {}, push() {}}), FT: () => { throw new Error('FT'); },
    F6: function F6() {}, C52: s => s.item, E6n: () => ({Lo() {}}), BBz() {}, BMg() {}, F3G: (a, slot) => slot === 'legs' ? 1 : 0,
    CVA: (a, item, legs, overlay) => ({path: 'textures/models/armor/' + item.GZ.dZ1 + '_layer_' + (legs ? 2 : 1) + (overlay ? '_' + overlay : '') + '.png'}),
    FTd: (renderer, rl) => bound.push(rl.path), Hpg() {}, Lvs: {data: [0, 1, 2, 3, 4, 5]}, CFh() {}, FUa() {}, EmX: () => 0, Fzs() {},
    Ea2: () => 0xffffff, DcG: (tag, key) => tag[key] || 0, $rt_str: s => s, C: () => 'overlay',
    Bb: function Bb() { this.path = null; }, Gp9: (rl, p) => { rl.path = p; }};
  vm.createContext(ctx);
  vm.runInContext(module + '\nthis.JasprArmory = JasprArmory;\n' + fn + '\nthis.render = D$Y;', ctx);
  const layer = {bOZ: {dRJ: () => ({})}, cfN: 1, cfM: 1, cfK: 1, b29: 1, de3: 1};
  const render = (tag, slot) => {
    bound.length = 0;
    const item = new ctx.F6();
    item.a6G = slot;
    item.GZ = {d: 5, dZ1: 'diamond'};
    const stack = {item, bV: tag};
    ctx.render(layer, {yI: () => stack}, 0, 0, 0, 0, 0, 0, 0, slot);
    return bound.slice();
  };
  assert.deepEqual(render(null, 'chest'), ['textures/models/armor/diamond_layer_1.png']);
  assert.deepEqual(render({}, 'chest'), ['textures/models/armor/diamond_layer_1.png']);
  assert.deepEqual(render({JasprArmorySkin: 3}, 'chest'), ['textures/models/armor/jaspr_abyssal_layer_1.png']);
  assert.deepEqual(render({JasprArmorySkin: 3}, 'head'), ['textures/models/armor/jaspr_abyssal_layer_1.png']);
  assert.equal(ctx.JasprArmory.stats.built, 1, 'the second abyssal upper piece uses the cached location');
  assert.deepEqual(render({JasprArmorySkin: 6}, 'legs'), ['textures/models/armor/jaspr_void_layer_2.png']);
  assert.deepEqual(render({JasprArmorySkin: 1}, 'feet'), ['textures/models/armor/jaspr_emerald_layer_1.png']);
  assert.deepEqual(render({JasprArmorySkin: 9}, 'chest'), ['textures/models/armor/diamond_layer_1.png'], 'unknown skin');
  ctx.JasprArmory.on = false;
  assert.deepEqual(render({JasprArmorySkin: 3}, 'chest'), ['textures/models/armor/diamond_layer_1.png'], 'switched off');
});

test('armoury client stage: every set has both worn layers in the pack builder', () => {
  const pack = fs.readFileSync(path.join(root, 'scripts/build-armory-pack.cjs'), 'utf8');
  assert.ok(pack.includes("at('textures/models/armor/jaspr_' + set + '_layer_' + layer + '.png')"), 'worn layers in the pack builder');
  const {SETS} = require('../scripts/build-armory-pack.cjs');
  assert.deepEqual(SETS, stage.SETS);
  const items = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear/ArmoryItems.java'), 'utf8');
  assert.match(items, /if \(piece\.armour\(\)\) tag\.setInt\(SKIN_TAG, set\.ordinal\(\) \+ 1\);/);
  const sets = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear/ArmorySet.java'), 'utf8');
  assert.deepEqual([...sets.matchAll(/^    [A-Z]+\("([a-z]+)", "/gm)].map(m => m[1]), SETS, 'client set order is the server order');
});
