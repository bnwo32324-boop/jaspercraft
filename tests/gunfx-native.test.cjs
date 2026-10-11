'use strict';
// The gun animation stage's engine glue (client-mods/gunfx/gunfx-native.js) on mocks of the deployed client's names, run
// the way the TeaVM runtime runs it: a simulated fiber suspends at engine calls and resumes them, so the hand-written
// resumable functions (JasprGunFXRead, JasprGunFXTick) are checked to give the same result whether or not anything
// suspends and never to repeat an engine call. Plus: the first-person transform and flash (GL state put back exactly),
// the reload sound, failure isolation, bounded diagnostics, and the builder on the LIVE client (read only).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const builder = require('../scripts/build-gunfx-client.cjs');

// ------------------------------------------------------------------ a fake engine
function engine(opts) {
  opts = opts || {};
  const log = [], thread = { stack: [], resuming: false, suspending: false }, plan = { every: 0, n: 0, only: null };
  const J = s => ({ js: String(s) });                               // a "Java string"
  const ctx = { console: { warn() {} }, Math, JSON, Object, Array, String, Number, Map, Date, BigInt, isFinite };
  ctx.$rt_globals = ctx;
  ctx.$rt_str = s => J(s);
  ctx.$rt_ustr = j => (j && typeof j.js === 'string' ? j.js : '');
  ctx.FX = () => thread.resuming;
  ctx.B = () => thread.suspending;
  ctx.Ds = () => ({ l: () => thread.stack.pop(), s: (...v) => { thread.stack.push(...v); } });
  ctx.FT = () => { throw new Error('invalid recorded state'); };
  const nat = (name, impl) => function (...args) {
    if (thread.resuming) {
      const mark = thread.stack.pop();
      if (mark !== 'leaf:' + name) throw new Error('resumed ' + name + ' but the suspended leaf was ' + mark);
      thread.resuming = false; log.push(name + ':resumed'); return impl(...args);
    }
    plan.n++;
    if (plan.every && plan.n % plan.every === 0 && (!plan.only || plan.only.includes(name))) {
      thread.suspending = true; thread.stack.push('leaf:' + name); log.push(name + ':suspend'); return undefined;
    }
    log.push(name); return impl(...args);
  };
  const tag = (stack, key) => { const t = stack && stack.bV && stack.bV.tags[key.js]; return t && t.tags ? t : null; };
  ctx.C_q = nat('C_q', item => (item ? item.id : 0));
  ctx.GaK = nat('GaK', (stack, key) => tag(stack, key));
  ctx.F54 = nat('F54', (t, key) => J(typeof t.tags[key.js] === 'string' ? t.tags[key.js] : ''));
  ctx.DcG = nat('DcG', (t, key) => (typeof t.tags[key.js] === 'number' ? t.tags[key.js] | 0 : 0));
  ctx.FQJ = nat('FQJ', (t, key) => BigInt(typeof t.tags[key.js] === 'number' ? t.tags[key.js] : 0));
  ctx.E$l = nat('E$l', (t, key) => (t.tags[key.js] && t.tags[key.js].tags ? t.tags[key.js] : { tags: {} }));
  ctx.DU = x => Number(x);
  ctx.Kua = { side: 'RIGHT' }; ctx.Lkg = { t: 'FIRST_PERSON_RIGHT_HAND' }; ctx.Lkf = { t: 'FIRST_PERSON_LEFT_HAND' };
  ctx.DlD = nat('DlD', () => ctx.Kua);
  const spawned = [];
  ctx.CsB = nat('CsB', (rg, id, ignore, x, y, z, vx, vy, vz, args) => { spawned.push({ id, ignore, x, y, z, vx, vy, vz, args }); return null; });
  ctx.Bh = n => ({ array: 'int', length: n, data: new Int32Array(n) });
  ctx.CN = a => ({ array: 'int', length: a.length, data: Int32Array.from(a) });
  // GL state and calls (GlStateManager semantics on the state globals the module saves and restores)
  Object.assign(ctx, { Krr: { data: [1, 1] }, Kri: 1, HHT: 1, Krf: 1, HHV: 1, Krc: 770 | (1 << 16), Krd: 771, KqX: 1, HHR: 1, HEo: 0,
    KrK: { data: [0, 120] }, KrL: { data: [0, 200] }, HKI: 1, HKJ: 0.9, HKK: 0.8, HKL: 1, HLn: 'POSITION_COLOR' });
  const gl = [];
  const g = (name, fn) => (...a) => { gl.push([name, ...a]); if (opts.glThrow === name) throw new Error('GL ' + name); if (fn) fn(...a); };
  ctx.DPm = g('translate'); ctx.Gc9 = g('rotate');
  ctx.GnI = g('activeTexture', u => { ctx.HEo = u - 33984; });
  ctx.DCQ = g('disableTexture', () => { ctx.Krr.data[ctx.HEo] = 0; }); ctx.CQ6 = g('enableTexture', () => { ctx.Krr.data[ctx.HEo] = 1; });
  ctx.DFk = g('disableLighting', () => { ctx.Kri = 0; }); ctx.D75 = g('enableLighting', () => { ctx.Kri = 1; });
  ctx.F1Q = g('disableCull', () => { ctx.HHT = 0; }); ctx.Ggy = g('enableCull', () => { ctx.HHT = 1; });
  ctx.Db2 = g('disableAlpha', () => { ctx.Krf = 0; }); ctx.D6M = g('enableAlpha', () => { ctx.Krf = 1; });
  ctx.CyM = g('enableBlend', () => { ctx.HHV = 1; }); ctx.CTP = g('disableBlend', () => { ctx.HHV = 0; });
  ctx.Fb_ = g('blendFunc', (s, d) => { ctx.Krc = s | s << 16; ctx.Krd = d | d << 16; });
  ctx.B$p = g('blendFuncSeparate', (s, d, sa, da) => { ctx.Krc = s | sa << 16; ctx.Krd = d | da << 16; });
  ctx.EFX = g('depthMask', b => { ctx.KqX = b; }); ctx.C70 = g('disableDepth', () => { ctx.HHR = 0; }); ctx.DVf = g('enableDepth', () => { ctx.HHR = 1; });
  ctx.G0W = g('lightmap', (u, x, y) => { ctx.KrK.data[1] = x; ctx.KrL.data[1] = y; });
  ctx.CFh = g('color', (r, gg, b, a) => { ctx.HKI = r; ctx.HKJ = gg; ctx.HKK = b; ctx.HKL = a; });
  const buf = { verts: [] };
  ctx.GdM = () => ({ dy: buf }); ctx.C5 = () => {};
  ctx.Ep0 = g('begin', (b, mode, fmt) => { buf.mode = mode; buf.fmt = fmt; buf.verts = []; });
  ctx.CUb = (b, x, y, z) => { buf.cur = [x, y, z]; return b; };
  ctx.Eip = (b, r, gg, bb, a) => { buf.cur.push(r, gg, bb, a); return b; };
  ctx.E74 = b => { b.verts.push(buf.cur); };
  ctx.FE$ = g('draw');
  const player = { b: 100.5, f: 64, c: -20.5, C: 90, bd: -10, s: 0, p: 0, t: 0, bx: { gP: 0 } };
  ctx.HEH = { v: player, G: { lv: 0, QV: 0 }, da: { cbA: null, chT: 0 }, bTh: 0, cj: null, fE: { renderGlobal: true }, cp: 0, buv: 0, J4: { UM: 0.25 } };
  if (opts.storage) ctx.localStorage = { getItem: () => opts.storage };
  if (opts.location) ctx.location = { pathname: opts.location };
  const sent = [];
  if (opts.fetch) ctx.fetch = (url, init) => { sent.push({ url, body: JSON.parse(init.body) }); return { catch() {} }; };
  vm.createContext(ctx);
  vm.runInContext(builder.moduleSource(), ctx, { filename: 'gunfx-module.js' });
  /** Runs a resumable function to completion like the TeaVM scheduler, resuming as often as it suspends. */
  function run(fn, ...args) {
    let r = fn(...args), n = 0;
    while (thread.suspending) { thread.suspending = false; thread.resuming = true; r = fn(...args); if (++n > 1000) throw new Error('never finished'); }
    assert.equal(thread.stack.length, 0, 'fiber stack unwound');
    assert.equal(thread.resuming, false);
    return r;
  }
  return { ctx, log, gl, buf, spawned, sent, plan, run, player, J };
}
let serials = 0;
function rifle(rounds, extra) {
  return { rA: { id: 293 }, bK: 1560, bg_: 0, bV: { tags: Object.assign({ JasprApocalypse: { tags: { id: 'rifle', arsenalMark: 'jaspr-arsenal-v1', serial: 'S-1', rounds } } }, extra || {}) } };
}
function belfry(rounds, readyAt, reloadAt) {
  return { rA: { id: 417 }, bK: 0, bg_: 0, bV: { tags: { JasprPenitentArmory: { tags: { version: 1, kind: 'weapon', id: 'BELFRY_REPEATER', uuid: 'U-' + (++serials > 0 ? 'b' : ''), rounds, readyAt, reloadAt, loading: 0 } } } } };
}

test('JasprGunFXRead: the same descriptor whether or not the engine calls suspend, never a repeated call', () => {
  const stack = rifle(17, { JasprArmament: { tags: { Abilities: { tags: { speed_loader: 2 } } } } });
  let reference = null;
  for (const every of [0, 1, 2, 3, 5]) {
    const e = engine(); e.plan.every = every;
    const d = e.run(e.ctx.JasprGunFXRead, stack);
    const plain = JSON.parse(JSON.stringify(d));
    if (!reference) reference = plain; else assert.deepEqual(plain, reference, 'suspending every ' + every);
    // each engine call either ran once or suspended once and was resumed once
    const calls = e.log.filter(l => !l.includes(':'));
    const suspends = e.log.filter(l => l.endsWith(':suspend')).length, resumes = e.log.filter(l => l.endsWith(':resumed')).length;
    assert.equal(suspends, resumes, 'every suspension resumed');
    assert.equal(calls.length + suspends, 8, 'C_q, GaK, F54 x2, DcG, GaK, E$l, DcG: ' + e.log.join(' '));
  }
  assert.deepEqual(reference, { item: 293, damage: 1560, apoc: { id: 'rifle', serial: 'S-1', rounds: 17, speed: 2 }, dungeon: null });
  const e = engine();
  assert.equal(e.run(e.ctx.JasprGunFXRead, { rA: { id: 276 }, bK: 0, bg_: 0, bV: { tags: { JasprApocalypse: { tags: { id: 'rifle' } } } } }), null, 'a sword');
  assert.deepEqual(e.log, ['C_q'], 'nothing but the item id is read from a non-carrier');
  assert.equal(e.run(e.ctx.JasprGunFXRead, { rA: { id: 293 }, bK: 0, bg_: 0, bV: null }), null, 'a plain hoe');
  assert.equal(e.run(e.ctx.JasprGunFXRead, { rA: { id: 293 }, bK: 0, bg_: 0, bV: { tags: { JasprApocalypse: { tags: { id: 'trench_blade' } } } } }), null);
  const d = e.run(e.ctx.JasprGunFXRead, belfry(9, 1700000000123, 0));
  assert.deepEqual(JSON.parse(JSON.stringify(d.dungeon)), { kind: 'weapon', id: 'BELFRY_REPEATER', uuid: d.dungeon.uuid, rounds: 9, readyAt: 1700000000123, reloadAt: 0 });
});

test('JasprGunFXTick: a shot keeps the renderer\'s item, kicks the camera when B\'s API is there, spawns a casing; suspension-safe', () => {
  for (const every of [0, 1, 2, 4]) {
    const e = engine(), c = e.ctx, renderer = { bFQ: null, bpO: null };
    let s1 = rifle(18);
    e.plan.every = every;
    e.run(c.JasprGunFXTick, renderer, e.player, s1, null);
    renderer.bFQ = s1;                                              // the renderer shows the gun
    const s2 = rifle(17);
    e.run(c.JasprGunFXTick, renderer, e.player, s2, null);
    assert.equal(renderer.bFQ, s2, 'the replacement stack is the same item to the renderer (no re-equip dip)');
    const status = c.JasprGunFXDiagnostics.status();
    assert.equal(status.stats.core_shots, 1, 'one shot');
    assert.equal(status.stats.kickMissing, 1, 'no camera API yet: nothing camera-wise');
    const casings = e.spawned.filter(p => p.id === 36);
    assert.equal(casings.length, 1, 'one casing spawned (' + every + ')');
    assert.equal(casings[0].ignore, 0, 'through the particle setting');
    assert.deepEqual(Array.from(casings[0].args.data), [371, 0], 'a brass-coloured fleck');
    const d = Math.hypot(casings[0].x - e.player.b, casings[0].y - (e.player.f + 1.62), casings[0].z - e.player.c);
    assert.ok(d > 0.3 && d < 1.5, 'next to the gun, ' + d.toFixed(2) + ' blocks from the eye');
    assert.equal(e.spawned.length, 1, 'exactly the requested particles, none repeated by a resume');
  }
  // with workstream B's camera API present: one kick per shot, bounded and decaying fields only
  const e = engine(), c = e.ctx, kicks = [];
  c.JasprCameraFX = { kick: k => kicks.push(k) };
  e.run(c.JasprGunFXTick, {}, e.player, rifle(5), null);
  e.run(c.JasprGunFXTick, {}, e.player, rifle(4), null);
  assert.equal(kicks.length, 1);
  assert.deepEqual(Object.keys(kicks[0]).sort(), ['back', 'ms', 'pitch', 'roll', 'yaw']);
  assert.ok(kicks[0].pitch > 0 && kicks[0].pitch < 3 && kicks[0].ms > 50 && kicks[0].ms < 400);
  c.HEH.G.lv = 1; e.run(c.JasprGunFXTick, {}, e.player, rifle(3), null); assert.equal(kicks.length, 1, 'third person: no camera impulse');
});

test('first person: the recoil transform before the gun, the flash after it, every GL state put back', () => {
  const e = engine(), c = e.ctx, renderer = {};
  const s1 = rifle(10); e.run(c.JasprGunFXTick, renderer, e.player, s1, null);
  const s2 = rifle(9); e.run(c.JasprGunFXTick, renderer, e.player, s2, null);
  const before = JSON.stringify([c.Krr.data, c.Kri, c.HHT, c.Krf, c.HHV, c.Krc, c.Krd, c.KqX, c.HHR, c.HEo, c.KrK.data[1], c.KrL.data[1], c.HKI, c.HKJ, c.HKK, c.HKL]);
  c.JasprGunFXCore.pose(0, c.JasprGunFXCore.ticks(), false);       // the first frame of the shot was drawn a quarter tick ago
  c.JasprGunFXPre(s2, c.Lkg, 0);
  const transform = e.gl.slice();
  assert.equal(transform[0][0], 'translate'); assert.equal(transform[transform.length - 1][0], 'translate');
  assert.ok(transform.filter(t => t[0] === 'rotate').length >= 2, 'climb and roll');
  e.gl.length = 0; c.JasprGunFXPost();
  assert.ok(e.gl.some(t => t[0] === 'draw'), 'the flash is drawn');
  assert.deepEqual(e.gl.find(t => t[0] === 'blendFunc').slice(1), [770, 1], 'additive');
  assert.equal(e.buf.mode, 7); assert.equal(e.buf.fmt, 'POSITION_COLOR');
  assert.ok(e.buf.verts.length >= 32 && e.buf.verts.length % 4 === 0 && e.buf.verts.length <= 68, 'bounded quads: ' + e.buf.verts.length);
  for (const v of e.buf.verts) { assert.equal(v.length, 7); v.forEach(n => assert.ok(Number.isFinite(n))); }
  const after = JSON.stringify([c.Krr.data, c.Kri, c.HHT, c.Krf, c.HHV, c.Krc, c.Krd, c.KqX, c.HHR, c.HEo, c.KrK.data[1], c.KrL.data[1], c.HKI, c.HKJ, c.HKK, c.HKL]);
  assert.equal(after, before, 'GL state restored exactly');
  // third person, another item, or a stack the tick has not seen: untouched
  e.gl.length = 0;
  c.JasprGunFXPre(s2, { t: 'THIRD_PERSON_RIGHT_HAND' }, 0); c.JasprGunFXPost();
  c.JasprGunFXPre(rifle(9), c.Lkg, 0); c.JasprGunFXPost();
  assert.equal(e.gl.length, 0);
  // minimal particles: recoil still, no flash
  c.HEH.G.QV = 2; c.JasprGunFXPre(s2, c.Lkg, 0); e.gl.length = 0; c.JasprGunFXPost(); assert.equal(e.gl.length, 0);
});

test('the Apocalypse reload sound: only pitch 0.8 iron trapdoor at this player starts a reload animation', () => {
  const e = engine(), c = e.ctx, trapdoor = { WL: { iX: e.J('block.iron_trapdoor.open') } }, other = { WL: { iX: e.J('block.iron_trapdoor.close') } };
  e.run(c.JasprGunFXTick, {}, e.player, rifle(0), null);
  c.JasprGunFXSound(e.player, other, 0.5, 0.8, e.player.b, e.player.f, e.player.c);
  c.JasprGunFXSound(e.player, trapdoor, 0.5, 0.95, e.player.b, e.player.f, e.player.c);       // vanilla trapdoors
  c.JasprGunFXSound(e.player, trapdoor, 0.5, 0.8, e.player.b + 20, e.player.f, e.player.c);  // somewhere else
  e.run(c.JasprGunFXTick, {}, e.player, c.JasprGunFXCore.stack(0), null);
  assert.equal(c.JasprGunFXCore.handInfo(0).reloading, false);
  c.JasprGunFXSound(e.player, trapdoor, 0.5, 0.800000011920929, e.player.b, e.player.f + 0.1, e.player.c);
  e.run(c.JasprGunFXTick, {}, e.player, c.JasprGunFXCore.stack(0), null);
  assert.equal(c.JasprGunFXCore.handInfo(0).reloading, true);
  assert.equal(c.JasprGunFXDiagnostics.status().stats.sounds, 1);
});

test('Portal Gun shots from the action bar, dry clicks from the right-click delay', () => {
  const e = engine(), c = e.ctx;
  const portal = { rA: { id: 293 }, bK: 1160, bg_: 0, bV: { tags: { JasprApocalypse: { tags: { id: 'portal_gun', tier: 1 } } } } };
  e.run(c.JasprGunFXTick, {}, e.player, portal, null);
  c.HEH.da.cbA = e.J('Orange portal set.  Portals linked.  Next: blue.'); c.HEH.da.chT = 60;
  e.run(c.JasprGunFXTick, {}, e.player, portal, null);
  assert.equal(c.JasprGunFXCore.stats.portal, 1);
  e.run(c.JasprGunFXTick, {}, e.player, portal, null);
  assert.equal(c.JasprGunFXCore.stats.portal, 1, 'the same line object is read once');
  assert.ok(e.spawned.some(p => p.id === 30 && p.vx > 0.9 && p.vz === 0), 'orange dust');
  const empty = rifle(0); e.run(c.JasprGunFXTick, {}, e.player, empty, null);
  c.HEH.bTh = 4; e.run(c.JasprGunFXTick, {}, e.player, empty, null); c.HEH.bTh = 3;
  assert.equal(c.JasprGunFXCore.stats.dry, 1, 'a use click on the empty gun');
  c.HEH.cj = { screen: true }; c.HEH.bTh = 4; e.run(c.JasprGunFXTick, {}, e.player, empty, null);
  assert.equal(c.JasprGunFXCore.stats.dry, 1, 'not with a screen open');
});

test('a failing part switches itself off; the game never sees the exception', () => {
  const e = engine({ glThrow: 'rotate' }), c = e.ctx;
  e.run(c.JasprGunFXTick, {}, e.player, rifle(5), null);
  const s = rifle(4); e.run(c.JasprGunFXTick, {}, e.player, s, null);
  c.JasprGunFXCore.pose(0, c.JasprGunFXCore.ticks(), false);
  assert.doesNotThrow(() => { c.JasprGunFXPre(s, c.Lkg, 0); c.JasprGunFXPost(); });
  let st = c.JasprGunFXDiagnostics.status();
  assert.ok(st.disabled.pose, 'pose disabled'); assert.ok(st.lastError.includes('GL rotate'));
  e.gl.length = 0; c.JasprGunFXPre(s, c.Lkg, 0); assert.equal(e.gl.length, 0, 'stays off');
  const f = engine(), d = f.ctx;
  d.GaK = () => { throw new Error('broken NBT'); };
  assert.doesNotThrow(() => f.run(d.JasprGunFXTick, {}, f.player, rifle(5), null));
  st = d.JasprGunFXDiagnostics.status();
  assert.ok(st.disabled.read); assert.equal(st.stats.errors, 1);
  assert.doesNotThrow(() => f.run(d.JasprGunFXTick, {}, f.player, rifle(4), null));
  assert.equal(d.JasprGunFXDiagnostics.status().stats.errors, 1, 'no further attempts');
});

test('diagnostics: same-origin, only on the game page, at most 12 events, no positions or names', () => {
  const quiet = engine({ fetch: true });
  quiet.run(quiet.ctx.JasprGunFXTick, {}, quiet.player, rifle(5), null);
  assert.equal(quiet.sent.length, 0, 'not outside /jaspercraft/');
  const e = engine({ fetch: true, location: '/jaspercraft/play' }), c = e.ctx;
  e.run(c.JasprGunFXTick, {}, e.player, rifle(5), null);
  e.run(c.JasprGunFXTick, {}, e.player, rifle(4), null);
  assert.deepEqual(e.sent.map(s => s.body.events[0].event), ['jaspercraft.gunfx.state', 'jaspercraft.gunfx.state']);
  assert.deepEqual(e.sent.map(s => s.body.events[0].details.state), ['armed', 'first-shot']);
  for (let i = 0; i < 30; i++) c.JasprGunFX.fail('part' + i, new Error('boom ' + i));
  assert.equal(e.sent.length, 12, 'bounded');
  const text = JSON.stringify(e.sent);
  assert.ok(!text.includes('100.5') && !text.includes('S-1'), 'no position, no serial');
  assert.equal(e.sent[0].url, '/api/diagnostics/events');
});

test('the personal opt-out (localStorage jaspr.gunfx.v1 enabled:false) turns everything off', () => {
  const e = engine({ storage: '{"enabled":false}' }), c = e.ctx;
  e.run(c.JasprGunFXTick, {}, e.player, rifle(5), null);
  const s = rifle(4); e.run(c.JasprGunFXTick, {}, e.player, s, null);
  c.JasprGunFXPre(s, c.Lkg, 0); c.JasprGunFXPost();
  assert.equal(e.gl.length, 0); assert.equal(e.spawned.length, 0); assert.equal(c.JasprGunFXCore.stats.shots >= 0, true);
  assert.equal(c.JasprGunFXDiagnostics.status().enabled, false);
});

const LIVE = process.env.JASPR_LIVE_CLASSES || builder.LIVE;
test('builder on the LIVE client: three hooks, outside every fence, reversible byte for byte, stable', { skip: !fs.existsSync(LIVE) }, () => {
  const input = fs.readFileSync(LIVE, 'latin1');
  const { output, base } = builder.build(input);
  assert.equal(base, builder.unpatch(input));
  assert.equal(builder.unpatch(output), base, 'unpatch(build(x)) === x');
  assert.equal(builder.build(output).output, output, 'rebuild stable');
  assert.equal(output.split(builder.BEGIN).length, 2); assert.equal(output.split(builder.END).length, 2);
  for (const [fn, , to] of builder.HOOKS) {
    const at = output.indexOf(to);
    assert.ok(at > 0, fn + ' hook present');
    for (const [a, b, name] of builder.fences(output)) assert.ok(at < a || at >= b, fn + ' hook outside JASPR_' + name);
  }
  assert.ok(output.lastIndexOf(builder.END) > output.lastIndexOf('/* JASPR_MUTANTS_END */'), 'the outermost block');
  // an anchor another stage replaced is refused instead of guessed
  const broken = base.replace('j=a.cbJ;$p=1;case 1:Cfa(c,b,d,e,f,g,h,i,j);', 'j=a.cbJ;X();$p=1;case 1:Cfa(c,b,d,e,f,g,h,i,j);');
  assert.throws(() => builder.build(broken), /Chn: expected 1 hook anchor/);
});
