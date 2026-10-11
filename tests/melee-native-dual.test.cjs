'use strict';
// The whole combat assembly offline, in COMBAT_REWORK.md's order: LIVE -> workstream A's Better Combat head
// (bettercombat9, scripts/build-bettercombat-client.cjs) -> the melee module -> JASPR_COMBATFX. Through A's real head
// code: markSwing starts the motion, the feed's swing (target, tick) and verdict (ref) drive the impact, hit-stop, the
// camera kick and the sparks; a server refusal ('denied' with a ref) stays quiet; the off hand follows offReady() and
// answers the main hand's strike with its support track.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const melee = require('../scripts/build-melee-client.cjs');
const fx = require('../scripts/build-combatfx-client.cjs');
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');
const HEAD = path.join(__dirname, '../scripts/build-bettercombat-client.cjs');
const LIVE = process.env.MELEE_CLASSES_JS || melee.LIVE;
const ready = fs.existsSync(LIVE) && fs.existsSync(HEAD);
let fn = null;
function client() {
  if (fn) return fn;
  const head = require(HEAD), full = fx.build(melee.build(head.build(fs.readFileSync(LIVE, 'latin1'))).output).output;
  assert.ok(full.includes("'bettercombat9'"), 'workstream A head');
  return (fn = loadClientSource(full, { filename: 'combat-assembly.js' }).fn);
}

test('through the bettercombat9 head: a feed-confirmed hit lands with hit-stop, a camera kick and sparks; a refusal stays quiet', { skip: !ready }, () => {
  const f = client(), calls = [], spawned = [];
  f.FX2 = () => {}; f.DPm = (x, y, z) => calls.push(['translate', x, y, z]); f.Gc9 = (a, x, y, z) => calls.push(['rotate', a, x, y, z]);
  f.FMS = () => {}; f.JasprCombatSpecial = () => 0; f.C_q = () => 276; f.FiS = () => 12.5; f.EuW = () => 0; f.G9();
  f.Efj = (w, id) => spawned.push(id); f.Bh = n => ({ data: new Int32Array(n) });
  const net = {}, player = { d_: { qf: net }, cv: 500, bDd: 30, bQ: 1, p: 0, b: 0, f: 64, c: 0, C: 0, bd: 0 };
  const mc = { v: player, X: { baK: id => id === 31 ? { ed: 0, bc: { ct: -0.3, bv: 64, cz: 2, cH: 0.3, cl: 65.95, cK: 2.6 } } : null }, G: { lv: 0, QV: 0, Nc: { bSp: 0 } }, cj: null, uE: 1, dw: {} };
  mc.bu = mc; mc.kD = mc; HEHset(f, mc);
  const B = f.JasprBetterCombat, M = f.JasprMeleeMotion, renderer = { kD: mc, bu: mc }, axe = { rA: {}, bg_: 0, bV: null, bK: 0 };
  B.outgoing(mc);
  B.receive(JSON.stringify({ v: 1, range: 3, width: 1, remaining: 0, period: 20, enabled: true, main: true, off: true, dual: true, motion: true, mainItem: 258, offItem: 0, offSwing: 0, proto: 2 }));
  assert.equal(B.active(mc), true);
  f.C_q = () => 258;
  M.clear(); M.partial = 0;
  // the head at the click: the 'swing' event (target 31, the player's tick) then markSwing
  const swing = f.JasprCombatFeedPush({ seq: 0, at: Date.now(), hand: 'M', kind: 'swing', item: 258, target: 31, crit: false, sweep: false, fatal: false, pair: { main: 258, off: -1 }, ref: -1, tick: 500 });
  B.markSwing(false, 258);
  f.JasprCombatFeedPush({ seq: 0, at: Date.now(), hand: 'M', kind: 'hit', item: 258, target: 31, crit: true, sweep: false, fatal: false, pair: { main: 258, off: -1 }, ref: swing.seq, tick: 500 });
  let impactAt = null;
  for (let t = 501; t < 513; t += 0.25) {
    player.cv = Math.floor(t); M.partial = t - Math.floor(t);
    calls.length = 0;
    f.JasprCameraFXApply(renderer, M.partial);
    f.JasprCombatTransform(renderer, f.Kua, 0, axe, 1);
    if (impactAt === null && M.stats.impacts > 0) impactAt = t;
  }
  const clip = M._test.hands()[0].strike || M._test.hands()[0].recent[0];
  assert.equal(clip.name, 'axe-chop'); assert.equal(clip.result, 'hit'); assert.equal(clip.crit, true, 'the verdict found its swing by ref');
  assert.ok(clip.stop, 'hit-stop'); assert.ok(impactAt !== null && Math.abs(impactAt - (501 + clip.contact)) < 0.6, 'impact at contact');
  assert.ok(spawned.length > 0 && spawned.includes(10), 'sparks, with crit sparks: ' + spawned.join(','));
  assert.ok(f.JasprCameraFX.status().kicks >= 1, 'camera kick');
  // a refusal: no impact, no sparks
  const before = M.stats.impacts, sparks = spawned.length;
  const s2 = f.JasprCombatFeedPush({ seq: 0, at: Date.now(), hand: 'M', kind: 'swing', item: 258, target: 31, crit: false, sweep: false, fatal: false, pair: { main: 258, off: -1 }, ref: -1, tick: 529 });
  player.cv = 529; B.markSwing(false, 258);
  f.JasprCombatFeedPush({ seq: 0, at: Date.now(), hand: 'M', kind: 'denied', item: 258, target: -1, crit: false, sweep: false, fatal: false, pair: { main: 258, off: -1 }, ref: s2.seq, tick: 529 });
  for (let t = 530; t < 545; t += 0.25) { player.cv = Math.floor(t); M.partial = t - Math.floor(t); f.JasprCameraFXApply(renderer, M.partial); f.JasprCombatTransform(renderer, f.Kua, 0, axe, 1); }
  assert.equal(M.stats.impacts, before, 'a refused swing makes no impact');
  assert.equal(spawned.length, sparks);
});

test('through the bettercombat9 head: with a weapon in each hand the off hand answers the main hand (offReady)', { skip: !ready }, () => {
  const f = client();
  const player = fn.JasprMeleeMotion && { d_: { qf: {} }, cv: 700, bDd: 30, bQ: 1, p: 0, b: 0, f: 64, c: 0, C: 0, bd: 0 };
  const mc = { v: player, X: { baK: () => null }, G: { lv: 0, QV: 0, Nc: { bSp: 0 } }, cj: null, uE: 1, dw: {} };
  mc.bu = mc; mc.kD = mc; HEHset(f, mc);
  const B = f.JasprBetterCombat, M = f.JasprMeleeMotion, renderer = { kD: mc, bu: mc }, sword = { rA: {}, bg_: 0, bV: null, bK: 0 };
  f.C_q = () => 276;
  B.outgoing(mc);
  B.receive(JSON.stringify({ v: 1, cfg: { proto: 2, offIds: [276], noAttack: [], players: false } }));
  B.receive(JSON.stringify({ v: 1, range: 3, width: 1, remaining: 0, period: 12, enabled: true, main: true, off: true, dual: true, motion: true, mainItem: 276, offItem: 276, offSwing: 0, proto: 2 }));
  B.hud(true);
  assert.equal(B.offReady(), true);
  M.clear(); M.partial = 0;
  // both hands are drawn every frame before the click (their items bound)
  for (const t of [699, 699.5]) { player.cv = Math.floor(t); M.partial = t - Math.floor(t); f.JasprCombatTransform(renderer, f.Kua, 0, sword, 1); f.JasprCombatTransform(renderer, f.Kvp, 0, sword, 0); }
  player.cv = 700; B.markSwing(false, 276);
  const offPoses = [];
  for (let t = 701; t < 712; t += 0.5) {
    player.cv = Math.floor(t); M.partial = t - Math.floor(t);
    f.JasprCombatTransform(renderer, f.Kua, 0, sword, 1);
    f.JasprCombatTransform(renderer, f.Kvp, 0, sword, 0);
    const h = M._test.hands()[1];
    if (h.support) offPoses.push(h.support.style);
  }
  assert.ok(offPoses.length > 0 && offPoses.every(s => s === 'guard'), 'twin swords: the off hand guards while the main hand strikes');
  // off hand not ready (no weapon in it): no support, the off hand keeps the vanilla path
  B.hud(false);
  M.clear(); player.cv = 719; f.JasprCombatTransform(renderer, f.Kua, 0, sword, 1); f.JasprCombatTransform(renderer, f.Kvp, 0, sword, 0); B.markSwing(false, 276); player.cv = 720;
  let supported = false;
  for (let t = 720; t < 726; t += 0.5) { player.cv = Math.floor(t); M.partial = t - Math.floor(t); f.JasprCombatTransform(renderer, f.Kua, 0, sword, 1); f.JasprCombatTransform(renderer, f.Kvp, 0, sword, 0); if (M._test.hands()[1].support) supported = true; }
  assert.equal(supported, false);
});

// the harness rebinds module-scope names through fn.X = value; HEH is Minecraft.theMinecraft
function HEHset(f, mc) { f.HEH = mc; }
