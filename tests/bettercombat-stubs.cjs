'use strict';
// Offline harness for the Better Combat client head (client-mods/bettercombat/bettercombat-head.js): the adapter runs in a
// node:vm context with small stand-ins for the TeaVM engine functions it calls. Nothing suspends here (FX/B are false),
// so the resumable state machines run straight through, exactly the path the browser takes when no call suspends.
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const HEAD = path.join(root, 'client-mods/bettercombat/bettercombat-head.js');

function load(options = {}) {
  const log = [], sent = [];
  let now = options.now || 1_000_000;
  class FakeDate extends Date { static now() { return now; } }
  class Cb {}                      // EntityPlayer
  class S5 {}                      // EntityTameable
  const ctx = {
    console, Math, JSON, Number, Object, Array, String, Error, WeakMap, Map, Set, Date: FakeDate,
    $rt_globals: {}, $rt_str: s => String(s), $rt_ustr: s => String(s),
    FX: () => false, B: () => false, Ds: () => ({ s() {}, l() {} }), FT: () => { throw Error('FT: invalid TeaVM state'); },
    HEH: null, Ktg: { empty: true }, HFd: 'MAIN', HFf: { data: ['MAIN', 'OFF'] }, HNb: 'SPECTATOR', KsX: {},
    Hc() {}, Fz() {}, I$() {},
    Gi: (registry, cls) => cls && Number.isInteger(cls.typeId) ? cls.typeId : -1,
    BW: e => e.cls || null,
    Cb, S5,
    EZ5: p => p.main, EjD: p => p.off,
    C_q: item => item ? item.id : 0,
    DOj: (p, pt) => p.strength,
    EQX: p => { p.bDd = 0; log.push(['resetCooldown']); },
    CDe: (p, hand) => { p.G1 = 1; p.crX = hand; log.push(['swingArm', hand]); },
    GgT: () => log.push(['syncHeldItem']),
    F78: (e, p) => e.owner === p ? 1 : 0,
    DaN: (tag, key) => Object.prototype.hasOwnProperty.call(tag, key) ? 1 : 0,
    AKy: function () {}, Iu: function () {}, Fru: () => ({}), Lg() {}, FuF: (d, e) => e,
    BgN: (c, channel, text) => { c.channel = channel; c.text = text; },
    FYu: (gui, x, y, u, v, w, h) => log.push(['draw', x, y, u, v, w, h]),
    CTM: () => ({}), CIb: () => log.push(['sweepParticle']),
    JasprMeleeMotion: { mark: (off, id, at, tick) => log.push(['mark', off, id, tick]), clear: () => log.push(['meleeClear']) },
    // the engine-derived pick at the end of the real head: the scenario decides what the combat ray finds
    JasprCombatPick: (g) => { g.bu.h4 = ctx.$pick || null; },
  };
  vm.createContext(ctx);
  vm.runInContext(options.source || fs.readFileSync(HEAD, 'utf8'), ctx, { filename: options.filename || 'bettercombat-head.js' });
  // vanilla's per-hand right click is replaced by a recorder unless a test asks for the real one
  if (!options.realUse && ctx.JasprCombatUse) ctx.JasprCombatUse = (a, hand) => { log.push(['use', hand]); return ctx.$use && ctx.$use[hand] ? 1 : 0; };
  const net = { wd(c) { sent.push(c.text); log.push(['send', c.text]); }, bkf: 0 };
  return { ctx, log, sent, net, advance(ms) { now += ms; }, get now() { return now; } };
}

let nextEntity = 500;
function stack(id, tag) { return { rA: { id }, bg_: 0, bV: tag || null, bK: 0, PD: 1 }; }
function entity(kind, extra = {}) {
  return Object.assign(kind === 'player' ? Object.create(h.ctx.Cb.prototype) : kind === 'pet' ? Object.create(h.ctx.S5.prototype) : {},
    { cu: nextEntity++, cls: { typeId: extra.typeId === undefined ? 54 : extra.typeId } }, extra);
}
let h = null;
function world(options = {}) {
  h = load(options);
  const player = { d_: { qf: h.net }, cv: 100, bDd: 40, G1: 0, crX: null, bBT: 0, main: stack(276), off: stack(267), strength: 1 };
  const mc = { v: player, X: {}, cj: null, uE: 1, dw: { NS: 0, jC: 'SURVIVAL' }, G: { Nc: { bSp: 0, mz: 0 }, o4: 1 }, h4: null, bTh: 0 };
  h.ctx.HEH = mc;
  return Object.assign(h, { mc, player });
}
const SETTINGS = { v: 1, enabled: true, proto: 2, range: 5, width: 1.5, refund: true, full: false, dual: true, motion: true, off: true, main: true,
  use: false, mainItem: 276, offItem: 267, remaining: 0, period: 12, offSwing: 0 };
const CFG = { v: 1, cfg: { proto: 2, offIds: [256, 257, 258, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 283, 284, 285, 286, 290, 291, 292, 293, 294],
  noAttack: [30, 100, 120], players: false } };
// say hello on this connection and receive the server's static data and state (legacy: no cfg, protocol 1 state)
function connect(w, settings = {}, legacy = false) {
  w.ctx.JasprBetterCombat.outgoing(w.mc);
  if (!legacy) w.ctx.JasprBetterCombat.receive(JSON.stringify(CFG));
  const s = Object.assign({}, SETTINGS, settings);
  if (legacy) delete s.proto;
  w.ctx.JasprBetterCombat.receive(JSON.stringify(s));
  return s;
}
// one client tick: JasprCombatTick at the start of processKeyBinds, with `uses` fresh presses of the use key pending
function tick(w, uses = 0) { w.mc.G.Nc.bSp += uses; w.ctx.JasprCombatTick(w.mc); w.player.cv++; }
// the engine's press loop: isPressed() takes one press, then rightClickMouse calls the head
function rightPress(w) { if (w.mc.G.Nc.bSp > 0) w.mc.G.Nc.bSp--; return w.ctx.JasprCombatAction(w.mc, true); }
function rightHeld(w) { return w.ctx.JasprCombatAction(w.mc, true); }
function left(w) { return w.ctx.JasprCombatAction(w.mc, false); }
function aim(w, target, block) {      // what the combat ray finds, and whether the vanilla crosshair is on a block
  w.ctx.$pick = target ? { ks: target, kE: { d: 2 } } : block ? { ks: null, mh: {}, kE: { d: 1 } } : null;
  w.mc.h4 = block ? { ks: null, mh: {}, kE: { d: 1 } } : target ? { ks: target, mh: null, kE: { d: 2 } } : null;
}
function events(w, kind) { return Array.from(w.ctx.JasprCombatFeed.events).filter(e => !kind || e.kind === kind); }
// The live client's head before this rework (bettercombat8), without its engine-derived tail, for before/after checks.
function liveHead() {
  const text = fs.readFileSync(path.join(root, 'client-mods/bettercombat/bettercombat-live-head.js'), 'latin1');
  return text.slice(0, text.indexOf('function JasprCombatSweepRender('));
}
module.exports = { liveHead, load, world, stack, entity, connect, tick, rightPress, rightHeld, left, aim, events, SETTINGS, CFG, root };
