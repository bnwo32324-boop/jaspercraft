'use strict';
// JasprCombatUse(a, MAIN) then (if it passed) JasprCombatUse(a, OFF) must do exactly what the live engine's
// Minecraft.rightClickMouse (F$w) does for both hands: same engine calls, same order, same arguments, same results.
// The right click runs the main hand first, then the off-hand attack, then the off hand, so it must be vanilla per hand.
// Reads the LIVE classes.js (read only). Run alone: node --test --test-concurrency=1 tests/bettercombat-use.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const { fnText } = require('../scripts/build-bettercombat-client.cjs');
const LIVE = process.env.BETTERCOMBAT_CLASSES_JS || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const live = fs.existsSync(LIVE);
const HEAD = path.join(__dirname, '../client-mods/bettercombat/bettercombat-head.js');

function harness(rightClick) {
  const trace = [];
  const ctx = { FX: () => false, B: () => false, Ds: () => ({ s() {}, l() {} }), FT: () => { throw Error('FT'); },
    HFd: 'MAIN', Kt_: 'OFF', HFf: { data: ['MAIN', 'OFF'] }, HFe: { data: [3, 2, 1] }, HFg: 'SUCCESS', HFc: 'AIR',
    Hc() {}, EI1() {}, BT() {}, BF() {}, IY() {}, HC9: {}, C: () => 'warning', E0X() {},
    JasprCombatAction: () => false, $rt_globals: {} };
  let sc = null;
  ctx.$set = s => { sc = s; };
  ctx.CjH = (p, hand) => { trace.push(['held', hand]); return sc.items[hand]; };
  ctx.CCH = st => st.empty ? 1 : 0;
  ctx.CRE = st => st.empty ? 0 : st.count;
  ctx.ANt = dw => dw.creative ? 1 : 0;
  ctx.EsL = (r, hand) => trace.push(['resetEquip', hand]);
  ctx.GfQ = (dw, p, e, ray, hand) => { trace.push(['interactAt', e.id, hand]); return sc.at[hand]; };
  ctx.Fht = (dw, p, e, hand) => { trace.push(['interact', e.id, hand]); return sc.interact[hand]; };
  ctx.CZr = (world, pos) => { trace.push(['state', pos.k]); return { material: sc.material }; };
  ctx.E0Z = st => st.material;
  ctx.Eha = (p, hand) => trace.push(['swing', hand]);
  vm.createContext(ctx);
  vm.runInContext(rightClick + '\n' + fs.readFileSync(HEAD, 'utf8'), ctx);
  const mc = () => ({
    v: {}, X: { world: 1 }, bTh: 0, fU: { bzi: {} },
    dw: { NS: 0, creative: sc.creative,
      d8I: (p, w, hand) => { trace.push(['useItem', hand]); return sc.item[hand]; },
      efH: (p, w, pos, side, vec, hand) => { trace.push(['useOnBlock', pos.k, side, vec, hand]); if (sc.consume[hand]) sc.items[hand].count--; return sc.block[hand]; } },
    h4: sc.hit === 'none' ? null : sc.hit === 'entity' ? { kE: { d: 2 }, ks: { id: 'zombie' } } :
      sc.hit === 'block' ? { kE: { d: 1 }, mh: { k: 'pos' }, q3: 'up', pN: 'vec' } : { kE: { d: 0 }, mh: { k: 'air' } } });
  return { ctx, trace, mc, set: s => ctx.$set(s) };
}

function rng(seed) { return () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }; }
const pick = (r, list) => list[Math.floor(r() * list.length)];
function scenario(r) {
  const item = () => r() < 0.25 ? { empty: true } : { empty: false, count: 1 + Math.floor(r() * 3) };
  const res = () => pick(r, ['PASS', 'SUCCESS', 'FAIL']);
  return { hit: pick(r, ['none', 'miss', 'entity', 'block']), material: pick(r, ['AIR', 'stone']), creative: r() < 0.3,
    items: { MAIN: item(), OFF: item() }, at: { MAIN: res(), OFF: res() }, interact: { MAIN: res(), OFF: res() },
    item: { MAIN: res(), OFF: res() }, block: { MAIN: res(), OFF: res() }, consume: { MAIN: r() < 0.5, OFF: r() < 0.5 } };
}
const clone = s => JSON.parse(JSON.stringify(s));

test('JasprCombatUse per hand == the engine\'s rightClickMouse loop (600 generated scenarios + the corners)', { skip: !live }, () => {
  const rightClick = fnText(fs.readFileSync(LIVE, 'latin1'), 'F$w');
  const vanilla = harness(rightClick), ours = harness(rightClick);
  const r = rng(20261011), cases = [];
  for (const hit of ['none', 'miss', 'entity', 'block'])
    for (const outcome of ['PASS', 'SUCCESS', 'FAIL'])
      cases.push({ hit, material: 'stone', creative: false, items: { MAIN: { empty: false, count: 2 }, OFF: { empty: false, count: 1 } },
        at: { MAIN: outcome, OFF: 'PASS' }, interact: { MAIN: 'PASS', OFF: outcome }, item: { MAIN: 'PASS', OFF: outcome },
        block: { MAIN: outcome, OFF: 'SUCCESS' }, consume: { MAIN: false, OFF: true } });
  for (let i = 0; i < 600; i++) cases.push(scenario(r));
  let used = 0, passed = 0;
  for (const sc of cases) {
    vanilla.set(clone(sc)); vanilla.trace.length = 0;
    const a = vanilla.mc(); vanilla.ctx.F$w(a);
    ours.set(clone(sc)); ours.trace.length = 0;
    const b = ours.mc();
    let done = ours.ctx.JasprCombatUse(b, 'MAIN');
    if (!done) done = ours.ctx.JasprCombatUse(b, 'OFF');
    assert.deepEqual(ours.trace, vanilla.trace, JSON.stringify(sc));
    assert.equal(a.bTh, 4, 'vanilla sets the right-click delay (the head sets it before its main-hand pass)');
    if (done) used++; else passed++;
  }
  assert.ok(used > 100 && passed > 100, 'both outcomes are exercised: ' + used + '/' + passed);
});

test('the main-hand pass stops exactly where vanilla stops (an interaction, a placed block, a used item)', { skip: !live }, () => {
  const rightClick = fnText(fs.readFileSync(LIVE, 'latin1'), 'F$w');
  const h = harness(rightClick);
  const base = { hit: 'entity', material: 'stone', creative: false, items: { MAIN: { empty: false, count: 1 }, OFF: { empty: false, count: 1 } },
    at: { MAIN: 'PASS', OFF: 'PASS' }, interact: { MAIN: 'PASS', OFF: 'PASS' }, item: { MAIN: 'PASS', OFF: 'PASS' }, block: { MAIN: 'PASS', OFF: 'PASS' }, consume: { MAIN: false, OFF: false } };
  const run = (patch) => { const sc = clone(Object.assign({}, base, patch)); h.set(sc); h.trace.length = 0; return h.ctx.JasprCombatUse(h.mc(), 'MAIN'); };
  assert.equal(run({}), 0, 'a sword on a zombie passes: the off-hand attack follows');
  assert.deepEqual(h.trace.map(x => x[0]), ['held', 'interactAt', 'interact', 'useItem']);
  assert.equal(run({ interact: { MAIN: 'SUCCESS', OFF: 'PASS' } }), 1, 'a villager trade / horse ride / name tag: done');
  assert.equal(run({ item: { MAIN: 'SUCCESS', OFF: 'PASS' } }), 1, 'food, a bow, a shield, a pearl: done');
  assert.equal(run({ hit: 'block', block: { MAIN: 'SUCCESS', OFF: 'PASS' } }), 1, 'a placed block / an opened door: done');
  assert.deepEqual(h.trace.map(x => x[0]), ['held', 'state', 'useOnBlock', 'swing'], 'with vanilla\'s arm swing');
  assert.equal(run({ hit: 'block', items: { MAIN: { empty: true }, OFF: { empty: false, count: 1 } } }), 0, 'an empty hand on stone passes');
});
