'use strict';
// Silent effects in the inventory (owner 2026-10-05): an effect that is ambient and particle-less -- how every item, trinket,
// bauble, armament and stat keeps an effect on its bearer -- is not listed in the inventory screens and does not push the
// window aside; every other effect still is. The client stage is built from the repo's client, parsed, reversed byte for
// byte, and the rewritten updateActivePotionEffects is run against mocks of the game's own helpers.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const stage = require('../scripts/build-silent-effects-client.cjs');

const root = path.resolve(__dirname, '..');
const raw = fs.readFileSync(path.join(root, 'site', 'classes.js'), 'latin1');
const built = stage.build(raw);

test('stage: builds, parses, reverses byte for byte and rebuilds stably', () => {
  assert.equal(stage.strip(built.result), built.base);
  assert.equal(stage.apply(stage.strip(built.result)), built.result);
  assert.ok(built.result.length > built.base.length);
  assert.equal(stage.strip(built.base), built.base, 'stripping an unpatched client changes nothing');
  assert.equal(built.result.split(stage.JF).length - 1, 2, 'two marked edits');
});

test('drawActivePotionEffects: an ambient particle-less effect goes back to the loop head, others are drawn', () => {
  const start = built.result.indexOf('function CaO(a){');
  const body = built.result.slice(start, built.result.indexOf('Ds().s(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,$p);}', start));
  const hook = 'd=$z;o=d;' + stage.JF + 'if(o.pl&&!o.xI){$p=9;continue _;}p=o.jr;';
  assert.equal(body.split(hook).length - 1, 1, 'hook after the next effect is read');
  // State 9 asks the iterator for another effect (and ends the draw when there is none); state 10 reads it.
  assert.ok(body.includes('case 9:$z=Bz(e);if(B()){break _;}f=$z;if(!f)return;$p=10;case 10:$z=GDx(e);'), 'case 9 is the loop head, case 10 reads the effect');
});

function run(effects) {
  const env = {
    FX: () => false, Ds: () => ({l() {}, s() {}}), B: () => false, FT: () => { throw new Error('unreachable state'); },
    F9x: player => player.effects, E3i: collection => collection.length === 0, Li3: 'natural ordering',
    D0L: (ordering, collection) => collection.slice(), BA: list => ({list, at: 0}),
    Bz: it => (it.at < it.list.length ? 1 : 0), GDx: it => it.list[it.at++]
  };
  const make = new Function(...Object.keys(env), stage.CX3_TO + '; return CX3;');
  const screen = {j: {v: {effects}}, q: 800, gv: 176, is: -1, clo: -1};
  make(...Object.values(env))(screen);
  return screen;
}
const SILENT = {pl: 1, xI: 0}, SHOWN = {pl: 0, xI: 1}, BEACON = {pl: 1, xI: 1}, PLAIN_HIDDEN = {pl: 0, xI: 0};
const BESIDE = 160 + (((800 - 176) - 200) / 2 | 0), CENTRED = (800 - 176) / 2 | 0;

test('updateActivePotionEffects: the window only moves aside for effects that will be listed', () => {
  assert.deepEqual(run([]), {j: {v: {effects: []}}, q: 800, gv: 176, is: CENTRED, clo: 0}, 'no effects');
  for (const [name, effects, listed] of [
    ['one silent effect', [SILENT], false], ['several silent effects', [SILENT, SILENT, SILENT], false],
    ['a potion', [SHOWN], true], ['a beacon effect (ambient, with particles)', [BEACON], true],
    ['a plugin effect that is not ambient and has no particles', [PLAIN_HIDDEN], true],
    ['silent first, then a potion', [SILENT, SILENT, SHOWN], true], ['a potion first, then silent', [SHOWN, SILENT], true]]) {
    const screen = run(effects);
    assert.equal(screen.clo, listed ? 1 : 0, name + ': effect boxes ' + (listed ? 'drawn' : 'not drawn'));
    assert.equal(screen.is, listed ? BESIDE : CENTRED, name + ': window position');
  }
});

test('server contract: every effect an item keeps on its bearer is ambient and particle-less', () => {
  const plugins = path.join(root, 'server/custom-plugins');
  const read = f => fs.readFileSync(path.join(plugins, f), 'utf8');
  const silent = /PotionEffect[(][^;]*,\s*true,\s*false[)]/;
  for (const [file, what] of [
    ['JasprRuins/src/chat/jaspr/ruins/Trinkets.java', 'Drownhollow relics (Pearl of the Drowned and the rest)'],
    ['JasprNether/src/chat/jaspr/nether/Relics.java', 'Nether trinkets'],
    ['JasprGear/src/chat/jaspr/gear/GearAbilities.java', 'Survivor gear trinkets'],
    ['JasprBackrooms/src/chat/jaspr/backrooms/Gear.java', 'Backrooms trinkets'],
    ['JasprRPG/src/chat/jaspr/rpg/StatEffects.java', 'stat effects']]) {
    assert.match(read(file), silent, what + ' use silent effects');
  }
});
