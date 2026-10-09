'use strict';
// Big Mobs client (owner 2026-10-05: dungeon bosses and mobs scaled up "literally ... a larger size and ... a larger hitbox"): the module
// (client-mods/big-mobs-teavm.js) run in a vm against stand-in entities, and the stage (scripts/build-big-mobs-client.cjs) against the
// client it is built on: exact anchors in their own functions, reversible, stable, parses. The real client is tests/big-mobs-browser.cjs.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const sized = [];
const ctx = {FET: (e, w, h) => { sized.push([e.cu, w, h]); e.bI = w; e.bZ = h; }, WeakMap, Date, Math, JSON, Object, String, Number, RegExp};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'client-mods', 'big-mobs-teavm.js'), 'utf8') + '\nthis.BM = JasprBigMobs;', ctx);
const BM = ctx.BM;
const zombie = id => ({cu: id, bI: .6, bZ: 1.95});

test('the table: "id:hundredths" pairs replace the old table; garbage is refused and counted', () => {
  BM.receive('12:300,40:115');
  assert.equal(BM.scale({cu: 12}), 3);
  assert.equal(BM.scale({cu: 40}), 1.15);
  assert.equal(BM.scale({cu: 7}), 1, 'not in the table: its own size');
  BM.receive('7:250');
  assert.equal(BM.scale({cu: 12}), 1, 'a new table replaces the old one');
  assert.equal(BM.scale({cu: 7}), 2.5);
  const before = BM.status().rejected;
  BM.receive('x:1,5:9999,-3:100,abc,8:20,9:,:5,10:300');
  assert.equal(BM.status().rejected, before + 7, 'every malformed or out-of-range entry is refused');
  assert.equal(BM.scale({cu: 10}), 3, 'the good entry among them is kept');
  BM.receive('');
  assert.equal(BM.status().entries, 0, 'the empty table clears it');
  BM.receive('1:200,'.repeat(2000));
  assert.ok(BM.status().entries <= 256 && BM.status().rejected > before, 'bounded');
  assert.equal(BM.scale(null), 1);
  assert.equal(BM.status().failures, 0);
});

test('the hitbox: grown once per change from the entity\'s own size, given back when it leaves the table', () => {
  sized.length = 0;
  const z = zombie(21);
  BM.receive('');
  assert.equal(BM.prepare(z), 1);
  assert.equal(sized.length, 0, 'an entity never in the table is never touched');
  BM.receive('21:300');
  assert.equal(BM.prepare(z), 3);
  assert.deepEqual(sized.at(-1).map(v => Math.round(v * 100) / 100), [21, 1.8, 5.85]);
  BM.prepare(z); BM.prepare(z);
  assert.equal(sized.length, 1, 'once per change, not every frame');
  BM.receive('21:150');
  BM.prepare(z);
  assert.deepEqual(sized.at(-1).map(v => Math.round(v * 100) / 100), [21, .9, 2.93], 'from its own size, never compounded');
  BM.receive('');
  assert.equal(BM.prepare(z), 1);
  assert.deepEqual(sized.at(-1).map(v => Math.round(v * 100) / 100), [21, .6, 1.95], 'its own size back');
  // something else resized it meanwhile (a slime grew): that is its own size from then on
  BM.receive('21:200');
  BM.prepare(z);
  z.bI = 1.02; z.bZ = 1.02;
  BM.prepare(z);
  assert.deepEqual(sized.at(-1).map(v => Math.round(v * 100) / 100), [21, 2.04, 2.04]);
  const s = BM.status();
  assert.ok(s.grown >= 3 && s.restored >= 1 && s.draws > 0 && s.changed.length > 0 && s.failures === 0, JSON.stringify(s));
});

test('the stage: hooks in the payload handler and the living render, reversible, stable, parses', () => {
  const stage = require('../scripts/build-big-mobs-client.cjs');
  const file = process.env.BIG_MOBS_SOURCE || path.join(root, 'site', 'classes.js');
  const raw = fs.existsSync(file) ? fs.readFileSync(file, 'latin1') : '';
  if (!stage.EDITS.every(([, vanilla, patched]) => raw.includes(vanilla) || raw.includes(patched))) return;   // an older client: nothing to check
  const {base, result} = stage.build(raw);
  assert.equal(stage.strip(result), base);
  assert.equal(stage.build(result).result, result);
  assert.equal(result.split(stage.BEGIN).length, 2);
  for (const [fn, , patched] of stage.EDITS) {
    const at = result.indexOf(patched), owner = result.slice(result.lastIndexOf('\nfunction ', at) + 10, result.indexOf('(', result.lastIndexOf('\nfunction ', at)));
    assert.equal(owner, fn);
  }
  // DWR: the scale happens after applyRotations and before prepareScale, through its own state, inside the function's catch
  assert.match(result, /a\.a95\(b,q,i,g\);if\(B\(\)\)\{break _;\}\/\*JBM\*\/\$p=2992;continue _;/);
  assert.match(result, /case 2992:\/\*JBM\*\/try\{u=typeof JasprBigMobs!=="undefined"\?JasprBigMobs\.prepare\(b\):1;if\(u!==1\)\{FWM\(u,u,u\);if\(B\(\)\)\{break _;\}\}\$p=20;continue _;\}catch/);
  assert.match(result, /if\(\$rt_ustr\(b\.S\$\)==="jaspr:scale"\)\{\$p=2991;continue _;\}/);
});
