'use strict';
// Jerky for Sanitized Flesh (owner 2026-10-05: "sanitized flesh should have its own texture, and it should look like jerky").
// Sanitized Flesh is a stackable cooked beef, which has no damage to select a model with, so the client gets an NBT-skin stage:
// the cooked beef answers a model predicate named jaspr_skin from the id its own data carries, and the resource pack gives the
// stack with a skin its own model. The stage is built from the repo's client, parsed, reversed byte for byte, and the module is
// run against mocks of the game's own helpers; the pack side is merged into the repo's archive and the selector emulated.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const stage = require('../scripts/build-nbt-skin-client.cjs');
const pack = require('../scripts/build-guns-pack.cjs');
const {decode} = require('../scripts/png-codec.cjs');

const root = path.resolve(__dirname, '..');
const raw = fs.readFileSync(path.join(root, 'site', 'classes.js'), 'latin1');
const built = stage.build(raw);

test('stage: builds, parses, reverses byte for byte and rebuilds stably', () => {
  assert.equal(stage.strip(built.result), built.base);
  assert.equal(stage.apply(stage.strip(built.result)), built.result);
  assert.equal(stage.strip(built.base), built.base, 'stripping an unpatched client changes nothing');
  assert.equal(built.result.split(stage.JN).length - 1, 1, 'one marked edit');
  assert.equal(built.result.split(stage.BEGIN).length - 1, 1, 'one fenced module');
  assert.equal(built.result.split(stage.END).length - 1, 1);
});

test('hook: the cooked beef is handed to the module right after the registry returns it, inside Items.<clinit>', () => {
  const start = built.result.indexOf('\nfunction F7x(') + 1, end = built.result.indexOf('\nfunction ', start + 5);
  const body = built.result.slice(start, end);
  assert.equal(body.split('KR7=c;' + stage.JN + 'JasprNbtSkinInstall(KR7);c=C(3710);$p=124;').length - 1, 1);
  // The state before it fetches "cooked_beef" (pool string 3708) from the registry; the one after fetches the next item.
  assert.match(body, /c=C\(3708\);\$p=123;case 123:\$z\s*=B_N\(c\);/, 'KR7 is Items.COOKED_BEEF');
  // Nothing else in the client changed: with the fenced block removed, the only difference is that one inserted call.
  const withoutBlock = built.result.slice(0, built.result.indexOf(stage.BEGIN)) + built.result.slice(built.result.indexOf(stage.END) + stage.END.length + 1);
  assert.equal(withoutBlock.length - built.base.length, (stage.JN + 'JasprNbtSkinInstall(KR7);').length);
});

test('the renderer evaluates overrides for an item only when it has a property: EAO calls hasCustomProperties (FRn) first', () => {
  const start = built.result.indexOf('\nfunction EAO(') + 1, end = built.result.indexOf('\nfunction ', start + 5);
  const body = built.result.slice(start, end);
  assert.ok(body.includes('$z=FRn(e);if(B()){break _;}g=$z;if(!g)return f;'), 'no property, no override evaluation');
  const frn = built.result.slice(built.result.indexOf('\nfunction FRn(') + 1);
  assert.ok(/^function FRn\(a\)\{[^]*?return/.test(frn.slice(0, 600)), 'FRn is a plain predicate');
  assert.ok(built.result.includes('function F5b(a,b){var c,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:c=a.csx;'),
    'getPropertyGetter reads the item property map (csx), where DQE puts the getter');
  assert.ok(built.result.includes('function DQE(a,b,c){var d,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:d=a.csx;'),
    'addPropertyOverride writes the same map');
});

// ------------------------------------------------------------------------------------------------ the module, on mocks
function load() {
  const source = fs.readFileSync(path.join(root, 'client-mods', 'nbt-skin-teavm.js'), 'latin1');
  const puts = [];
  const env = {
    $rt_str: s => ({java: s}), $rt_ustr: o => o.java,
    Bb: function () {}, Gp9: (key, s) => { key.iX = s.java; },
    DQE: (item, key, getter) => puts.push({item, key, getter}),
    // ItemStack.getSubCompound(String) and NBTTagCompound.getString(String), over plain objects
    GaK: (stack, key) => { if (stack.explode) throw new Error('boom'); return stack.tag && stack.tag[key.java] ? stack.tag[key.java] : null; },
    F54: (compound, key) => ({java: compound[key.java] === undefined ? '' : compound[key.java]})
  };
  const api = new Function(...Object.keys(env), source + '; return {JasprNbtSkinInstall, JasprNbtSkinValue, JasprNbtSkinState, JasprNbtSkinTable};')(...Object.values(env));
  return {api, puts};
}

test('module: install registers one getter named jaspr_skin on the item it is given', () => {
  const {api, puts} = load(), item = {name: 'cooked beef'};
  api.JasprNbtSkinInstall(item);
  assert.equal(puts.length, 1);
  assert.equal(puts[0].item, item);
  assert.equal(puts[0].key.iX, 'jaspr_skin');
  assert.equal(typeof puts[0].getter.TM, 'function', 'IItemPropertyGetter.apply is the compiled method TM');
  api.JasprNbtSkinInstall(null);
  assert.equal(puts.length, 1, 'a missing item registers nothing');
  assert.equal(api.JasprNbtSkinState.errors, 0);
});

test('module: the getter answers 1 for Sanitized Flesh by the id in its data, and 0 for everything else', () => {
  const {api, puts} = load();
  api.JasprNbtSkinInstall({});
  const value = stack => puts[0].getter.TM(stack, null, null);
  assert.equal(value({tag: {JasprApocalypse: {id: 'sanitized_flesh', equipmentMark: 'x', tier: 1}}}), 1, 'the item as the server and the recipe book make it');
  assert.equal(value({tag: {JasprCreative: {id: 'sanitized_flesh'}}}), 1, 'the creative catalogue placeholder');
  assert.equal(value({tag: {JasprApocalypse: {id: 'field_ration'}}}), 0, 'another supply keeps its own look');
  assert.equal(value({tag: {JasprApocalypse: {id: 'sanitized_flesh '}}}), 0, 'exact ids only');
  assert.equal(value({tag: {}}), 0, 'a plain cooked beef');
  assert.equal(value({}), 0, 'a stack with no data at all');
  assert.equal(value(null), 0);
  assert.equal(value(undefined), 0);
  for (const id of ['constructor', 'toString', '__proto__', 'hasOwnProperty', 'valueOf']) {
    assert.equal(value({tag: {JasprApocalypse: {id}}}), 0, 'an id that is an Object.prototype name: ' + id);
  }
  assert.equal(api.JasprNbtSkinState.errors, 0);
  assert.equal(api.JasprNbtSkinState.hits, 2);
});

test('module: a failure while reading the stack never reaches the renderer and is counted', () => {
  const {api, puts} = load();
  api.JasprNbtSkinInstall({});
  assert.equal(puts[0].getter.TM({explode: true}, null, null), 0);
  assert.equal(api.JasprNbtSkinState.errors, 1);
});

test('module: ASCII only, function declarations only at the top level (the hook may run before the block is reached)', () => {
  const source = fs.readFileSync(path.join(root, 'client-mods', 'nbt-skin-teavm.js'), 'latin1');
  assert.ok(!/[^\x00-\x7f]/.test(source));
  const topLevel = source.split('\n').filter(l => /^\S/.test(l) && !/^(\/\*|\s*\*|\}|function |var )/.test(l));
  assert.deepEqual(topLevel, [], 'nothing but comments, var tables and functions');
  assert.ok(/^function JasprNbtSkinInstall\(/m.test(source) && /^function JasprNbtSkinValue\(/m.test(source));
});

// ----------------------------------------------------------------------------------------------------------- the pack
test('pack: cooked_beef gains exactly one override, the jerky model and texture exist, and the selector picks them by skin', () => {
  const files = pack.build({parent: 'item/generated', textures: {layer0: 'items/beef_cooked'}, extra: 'kept'});
  const beef = JSON.parse(files.get(pack.NAMES.beefModel));
  assert.deepEqual(beef, {parent: 'item/generated', textures: {layer0: 'items/beef_cooked'}, extra: 'kept',
    overrides: [{predicate: {jaspr_skin: 1}, model: 'item/jaspr_sanitized_flesh'}]});
  assert.deepEqual(JSON.parse(files.get(pack.NAMES.jerkyModel)), {parent: 'item/generated', textures: {layer0: 'items/jaspr_sanitized_flesh'}});
  assert.equal(pack.resolve(beef.overrides, {jaspr_skin: 0}, 'base'), 'base');
  assert.equal(pack.resolve(beef.overrides, {jaspr_skin: 1}, 'base'), 'item/jaspr_sanitized_flesh');
  assert.equal(pack.resolve(beef.overrides, {}, 'base'), 'base', 'an item without the property keeps the vanilla model');
  const img = decode(files.get(pack.NAMES.jerkyTexture));
  assert.equal(img.w, 16);
  assert.equal(img.h, 16);
});

test('pack: merging into the repo archive adds only the guns pack files, keeps every other entry and is idempotent', () => {
  const input = fs.readFileSync(path.join(root, 'site', 'assets.epk'));
  const merged = pack.merge(input);
  assert.deepEqual(merged.replaced.sort(), [pack.NAMES.beefModel, pack.NAMES.portalModel].sort());
  assert.deepEqual(merged.added.sort(), [pack.NAMES.jerkyModel, pack.NAMES.jerkyTexture, pack.NAMES.portalTexture].sort());
  assert.ok(merged.unchanged > 5000);
  assert.ok(pack.merge(merged.output).output.equals(merged.output), 'idempotent');
  assert.ok(merged.output.length - input.length < 40000, 'the guns pack adds little to the archive');
});

test('server contract: Sanitized Flesh is still a cooked beef whose data carries its id (the client skins it by that id)', () => {
  const java = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'), 'utf8');
  assert.match(java, /new Spec\("sanitized_flesh", "Sanitized Flesh", "consumable", Material\.COOKED_BEEF, 0,/);
  assert.match(java, /data\.setString\("id", id\)/);
  const table = fs.readFileSync(path.join(root, 'client-mods', 'nbt-skin-teavm.js'), 'utf8');
  assert.match(table, /JasprNbtSkinTable = \{sanitized_flesh: 1\}/);
});
