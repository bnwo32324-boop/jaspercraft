/* Native TeaVM bridges of the first-person gun animation (JASPR_GUNFX). Written against the deployed client's own names
 * (scripts/build-gunfx-client.cjs checks every one is still declared):
 *   ItemStack: rA item, bK damage, bV tag, bg_ empty; C_q Item.getIdFromItem; GaK ItemStack.getSubCompound; F54 / DcG / FQJ
 *   NBTTagCompound.getString / getInteger / getLong; E$l getCompoundTag; DU Long_toNumber; $rt_str / $rt_ustr strings.
 *   HEH Minecraft: v player (b f c position, C bd yaw pitch, s p t motion, bx.gP hotbar slot), G settings (lv third-person
 *   view, QV particles), da GuiIngame (cbA action-bar text, chT its timer), bTh right-click delay (4 = a use click this
 *   tick), cj screen, fE RenderGlobal, cp / buv / J4.UM paused flag and partial ticks.
 *   ItemRenderer: bFQ / bpO the stacks it shows. TransformType Lkg / Lkf first-person right / left. DlD primary hand, Kua RIGHT.
 *   GL (GlStateManager): DPm translate, Gc9 rotate, GnI active unit, DCQ / CQ6 texture off / on, DFk / D75 lighting, F1Q / Ggy
 *   cull, Db2 / D6M alpha test, CyM / CTP blend, Fb_ / B$p blend funcs, EFX depth mask, C70 / DVf depth test, G0W lightmap,
 *   CFh colour; state globals Krr Kri HHT Krf HHV Krc Krd KqX HHR KrK KrL HEo HKI..HKL. Tessellator GdM (.dy), Ep0 begin,
 *   CUb pos, Eip colour, E74 endVertex, FE$ draw; C5 / HLn POSITION_COLOR. CsB RenderGlobal.spawnParticle (honours the
 *   particle setting); Bh / CN int arrays.
 * Hooks (all in original engine text no other stage replaced):
 *   C45 (EntityRenderer.updateRenderer, the inlined ItemRenderer.updateEquippedItem) after both held stacks are read:
 *       JasprGunFXTick(itemRenderer, player, main, off) -- resumable, once per client tick.
 *   Ch0 (ItemRenderer.renderItemSide) around RenderItem.renderItemModel: JasprGunFXPre / JasprGunFXPost -- plain JS, first
 *       person only: the recoil transform before the gun is drawn, the muzzle flash right after it.
 *   Chn (NetHandlerPlayClient.handleSoundEffect) before the sound plays: JasprGunFXSound -- plain JS, notes the Apocalypse
 *       reload sound (iron trapdoor, pitch 0.8, at this player).
 * Every part fails on its own: an exception switches that part off for the page, reports it (bounded diagnostics) and the
 * game keeps going without it.
 */
var JasprGunFX = (function () {
  'use strict';
  var core = JasprGunFXCore, DEG = Math.PI / 180;
  var STORAGE_KEY = 'jaspr.gunfx.v1';
  // JasprCameraFX.kick (workstream B) takes pitch in degrees; positive = the view kicks up with the muzzle.
  var CAMERA_PITCH_SIGN = 1;
  var on = true;
  try { var saved = JSON.parse($rt_globals.localStorage.getItem(STORAGE_KEY)); if (saved && saved.enabled === false) on = false; } catch (_) {}
  core.setEnabled(on);

  // ================================================================ diagnostics and failure isolation
  var stats = { ticks: 0, reads: 0, aligned: 0, posed: 0, flashes: 0, spawned: 0, kicks: 0, kickMissing: 0, sounds: 0, overlays: 0, errors: 0, disabled: 0 };
  var disabled = Object.create(null), diag = { sent: 0, page: 'gunfx-' + Date.now().toString(36), lastError: '', firstStack: '', armed: false, fired: false };
  function send(event, details) {
    try {
      var loc = $rt_globals.location;
      if (diag.sent >= 12 || !loc || String(loc.pathname).indexOf('/jaspercraft/') !== 0 || typeof $rt_globals.fetch !== 'function') return;
      diag.sent++;
      $rt_globals.fetch('/api/diagnostics/events', { method: 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: { 'Content-Type': 'application/json', 'X-Jaspergers-Client': 'web-v1' },
        body: JSON.stringify({ events: [{ event: event, pageSessionId: diag.page, at: new Date().toISOString(), details: details }] }) }).catch(function () {});
    } catch (_) {}
  }
  function counters() { var o = {}, k; for (k in stats) o[k] = stats[k]; for (k in core.stats) o['core_' + k] = core.stats[k]; return o; }
  function state(what) { send('jaspercraft.gunfx.state', { state: String(what).slice(0, 40), version: core.VERSION, stats: counters() }); }
  function fail(part, error) {
    stats.errors++;
    diag.lastError = String(error && error.message || error).split('\r').join(' ').split('\n').join(' ').slice(0, 180);
    try { if (!diag.firstStack) diag.firstStack = (part + ': ' + String(error && error.stack || error)).split('\n').slice(0, 8).join(' | ').slice(0, 1000); } catch (_) {}
    if (!disabled[part]) { disabled[part] = diag.lastError; stats.disabled++; }
    try { if ($rt_globals.console) $rt_globals.console.warn('[JasperCraft gun animation] ' + part + ': ' + diag.lastError); } catch (_) {}
    send('jaspercraft.gunfx.error', { part: String(part).slice(0, 40), error: diag.lastError, stats: counters() });
  }
  function enabled(part) { return on && !disabled[part] && !disabled.all && !(part === 'tick' && disabled.read); }

  // ================================================================ cached Java strings
  var keys = Object.create(null);
  function key(name) { return keys[name] || (keys[name] = $rt_str(name)); }

  // ================================================================ inputs gathered outside the tick
  var reloadSound = false, lastOverlay = null, soundNames = typeof Map === 'function' ? new Map() : null;
  function soundName(ev) {
    if (soundNames && soundNames.has(ev)) return soundNames.get(ev);
    var n = '';
    try { n = ev && ev.WL && ev.WL.iX ? $rt_ustr(ev.WL.iX) : ''; } catch (_) { n = ''; }
    if (soundNames && soundNames.size < 512) soundNames.set(ev, n);
    return n;
  }
  /** Chn: every positioned sound packet. Only the Apocalypse reload (iron trapdoor opening at pitch 0.8 where this player
   * stands; vanilla trapdoors play at 0.9 to 1.0) is noted, for the next tick. */
  function sound(player, ev, volume, pitch, x, y, z) {
    if (!enabled('sound') || !player || !ev || !(Math.abs(pitch - 0.8) < 0.02)) return;
    try {
      if (soundName(ev) !== 'block.iron_trapdoor.open') return;
      var dx = x - player.b, dy = y - player.f, dz = z - player.c;
      if (dx * dx + dy * dy + dz * dz > 9) return;
      reloadSound = true; stats.sounds++;
    } catch (e) { fail('sound', e); }
  }

  // ================================================================ tick (plain parts of JasprGunFXTick)
  /** Which held stacks need their data read this tick (only when a hand holds a new object). */
  function begin(main, off) {
    stats.ticks++;
    return { readMain: main !== core.stack(0), readOff: off !== core.stack(1), mainDesc: undefined, offDesc: undefined };
  }
  function vfxLevel(mc) {
    var level = mc && mc.G ? mc.G.QV | 0 : 0;
    try { if (typeof JasprVideoState !== 'undefined' && JasprVideoState && JasprVideoState.value && JasprVideoState.value('particleEffects') === false) level = 2; } catch (_) {}
    return level < 0 ? 0 : level > 2 ? 2 : level;
  }
  /** The core's tick plus everything that is not a particle: renderer alignment, camera impulses, diagnostics. Returns
   * the abstract particles (placed in the world after the primary hand is known). */
  function step(renderer, player, main, off, plan) {
    var mc = typeof HEH !== 'undefined' ? HEH : null;
    if (plan.readMain) stats.reads++;
    if (plan.readOff) stats.reads++;
    var overlay = null;
    try {
      var gui = mc && mc.da;
      if (gui && gui.cbA !== lastOverlay) {
        lastOverlay = gui.cbA;
        var g0 = core.gun(0);
        if (gui.cbA && gui.chT >= 58 && ((g0 && g0.portal) || (plan.mainDesc && plan.mainDesc.apoc && plan.mainDesc.apoc.id === 'portal_gun'))) { overlay = $rt_ustr(gui.cbA); stats.overlays++; }
      }
    } catch (e) { fail('overlay', e); }
    var out = core.tick({ main: main, mainDesc: plan.readMain ? plan.mainDesc : undefined, off: off, offDesc: plan.readOff ? plan.offDesc : undefined,
      slot: player && player.bx ? player.bx.gP | 0 : 0, click: !!(mc && mc.bTh === 4 && mc.cj === null), overlay: overlay,
      reloadSound: reloadSound, firstPerson: !!(mc && mc.G && mc.G.lv === 0), vfx: vfxLevel(mc) });
    reloadSound = false;
    // the server's replacement stack of the same gun is the same item to the renderer: no re-equip dip on every shot
    if (out.align[0] !== null && renderer && renderer.bFQ === out.align[0]) { renderer.bFQ = main; stats.aligned++; }
    if (out.align[1] !== null && renderer && renderer.bpO === out.align[1]) { renderer.bpO = off; stats.aligned++; }
    if (out.kicks.length && enabled('camera')) {
      try { for (var i = 0; i < out.kicks.length; i++) cameraKick(out.kicks[i]); } catch (e) { fail('camera', e); }
    }
    if (!diag.armed && (core.gun(0) || core.gun(1))) { diag.armed = true; state('armed'); }
    if (!diag.fired && core.stats.shots > 0) { diag.fired = true; state('first-shot'); }
    return enabled('particles') ? out.particles : null;
  }
  function cameraApi() {
    var api = typeof JasprCameraFX !== 'undefined' ? JasprCameraFX : null;
    if (!api && $rt_globals && $rt_globals.JasprCameraFX) api = $rt_globals.JasprCameraFX;
    return api && typeof api.kick === 'function' ? api : null;
  }
  function cameraKick(k) {
    var api = cameraApi();
    if (!api) { stats.kickMissing++; return; }
    api.kick({ pitch: CAMERA_PITCH_SIGN * k.pitch, yaw: k.yaw, roll: k.roll, back: k.back, ms: k.ms });
    stats.kicks++;
  }
  // ---- particles: hand-frame geometry -> world (the first-person hand projection and the world share the 70 degree view)
  function rand(a, b) { return a + (b - a) * Math.random(); }
  /** Abstract particles -> spawn records {id, x, y, z, vx, vy, vz, args} for RenderGlobal.spawnParticle. */
  function place(list, player, rightHanded) {
    var out = [];
    if (!list || !list.length || !player) return out;
    var yaw = player.C * DEG, pitch = player.bd * DEG;
    var F = [-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)], Rt = [-Math.cos(yaw), 0, -Math.sin(yaw)];
    var U = [Rt[1] * F[2] - Rt[2] * F[1], Rt[2] * F[0] - Rt[0] * F[2], Rt[0] * F[1] - Rt[1] * F[0]];
    var eye = [player.b, player.f + 1.62, player.c], mv = [player.s || 0, player.p || 0, player.t || 0];
    function world(v) { return [eye[0] + Rt[0] * v[0] + U[0] * v[1] - F[0] * v[2], eye[1] + Rt[1] * v[0] + U[1] * v[1] - F[1] * v[2], eye[2] + Rt[2] * v[0] + U[2] * v[1] - F[2] * v[2]]; }
    function dir(r, u, f) { return [Rt[0] * r + U[0] * u + F[0] * f, Rt[1] * r + U[1] * u + F[1] * f, Rt[2] * r + U[2] * u + F[2] * f]; }
    for (var i = 0; i < list.length && out.length < 12; i++) {
      var q = list[i], gun = core.gun(q.hand);
      if (!gun || !gun.geo) continue;
      var side = (q.hand === 0) === rightHanded ? 1 : -1, geo = side > 0 ? gun.geo.r : gun.geo.l, n, k, p, v;
      var muzzle = world([side * 0.56 + geo.muzzle[0], -0.52 + geo.muzzle[1], -0.72 + geo.muzzle[2]]);
      if (q.kind === 'casing') {
        p = world([side * 0.56 + geo.pivot[0] + side * 0.04, -0.52 + geo.pivot[1] + 0.06, -0.72 + geo.pivot[2] - 0.14]);
        v = dir(side * rand(0.09, 0.14), rand(0.12, 0.17), rand(-0.03, 0.0));
        out.push({ id: 36, x: p[0], y: p[1], z: p[2], vx: v[0] + mv[0], vy: v[1] + mv[1], vz: v[2] + mv[2], item: q.item | 0 });
      } else if (q.kind === 'smoke') {
        for (n = Math.min(3, q.n | 0), k = 0; k < n; k++) { v = dir(rand(-0.01, 0.01), rand(0.01, 0.03), rand(0.01, 0.04)); out.push({ id: 11, x: muzzle[0], y: muzzle[1], z: muzzle[2], vx: v[0], vy: v[1], vz: v[2] }); }
      } else if (q.kind === 'spark') {
        for (n = Math.min(3, q.n | 0), k = 0; k < n; k++) { v = dir(rand(-0.06, 0.06), rand(-0.04, 0.08), rand(0.05, 0.15)); out.push({ id: 10, x: muzzle[0], y: muzzle[1], z: muzzle[2], vx: v[0], vy: v[1], vz: v[2] }); }
      } else if (q.kind === 'dust') {
        var c = q.color || [0.12, 0.56, 1.0];
        for (n = Math.min(6, q.n | 0), k = 0; k < n; k++) { p = [muzzle[0] + rand(-0.12, 0.12), muzzle[1] + rand(-0.12, 0.12), muzzle[2] + rand(-0.12, 0.12)];
          out.push({ id: 30, x: p[0], y: p[1], z: p[2], vx: Math.max(0.01, c[0]), vy: c[1], vz: c[2] }); }
      } else if (q.kind === 'spell') {
        for (n = Math.min(6, q.n | 0), k = 0; k < n; k++) { v = dir(rand(-0.05, 0.05), rand(0.0, 0.08), rand(0.02, 0.1));
          out.push({ id: q.id | 0, x: muzzle[0] + rand(-0.08, 0.08), y: muzzle[1] + rand(-0.08, 0.08), z: muzzle[2] + rand(-0.08, 0.08), vx: v[0], vy: v[1], vz: v[2] }); }
      }
    }
    return out;
  }
  function args(rec) { return rec.id === 36 ? CN([rec.item || 371, 0]) : Bh(0); }

  // ================================================================ first person (plain JS inside Ch0)
  var drawing = null;   // the pose applied by Pre, for Post's flash
  function partial(mc) { return mc.cp ? mc.buv : mc.J4.UM; }
  function pre(stack, transform, left) {
    drawing = null;
    if (!enabled('pose') || stack === null || stack === undefined || (transform !== Lkg && transform !== Lkf)) return;
    var h = stack === core.stack(0) ? 0 : stack === core.stack(1) ? 1 : -1;
    if (h < 0 || !core.gun(h)) return;
    try {
      var mc = HEH, p = core.pose(h, core.ticks() + partial(mc), !!left);
      if (!p) return;
      var v = p.pivot, a = p.axis;
      DPm(p.x + v[0], p.y + v[1], p.z + v[2]);
      if (p.yaw) Gc9(p.yaw, 0, 1, 0);
      if (p.climb) Gc9(p.climb, 1, 0, 0);
      if (p.roll) Gc9(p.roll, a[0], a[1], a[2]);
      DPm(-v[0], -v[1], -v[2]);
      drawing = p; stats.posed++;
    } catch (e) { fail('pose', e); }
  }
  function post() {
    var p = drawing; drawing = null;
    if (!p || !p.flash || !enabled('flash')) return;
    var mc = HEH;
    if (vfxLevel(mc) >= 2) return;
    try { drawFlash(core.flashQuads(p.flash)); } catch (e) { fail('flash', e); }
  }
  /** Additive, unlit, untextured, over the gun (no depth test or write); every touched GL state is put back. */
  function drawFlash(quads) {
    if (!quads.length) return;
    var s = { tex: Krr.data[0], light: Kri, cull: HHT, alpha: Krf, blend: HHV, src: Krc, dst: Krd, mask: KqX, depth: HHR, active: HEo,
      lx: KrK.data[1], ly: KrL.data[1], color: [HKI, HKJ, HKK, HKL] };
    try {
      GnI(33984); DCQ(); DFk(); F1Q(); Db2(); CyM(); Fb_(770, 1); EFX(0); C70(); G0W(33985, 240, 240); CFh(1, 1, 1, 1);
      var t = GdM(), buf = t.dy; C5(); Ep0(buf, 7, HLn);
      for (var i = 0; i < quads.length; i++) for (var j = 0; j < 4; j++) {
        var q = quads[i][j]; CUb(buf, q[0], q[1], q[2]); Eip(buf, q[3], q[4], q[5], q[6]); E74(buf);
      }
      FE$(t); stats.flashes++;
    } finally {
      GnI(33984); if (s.tex) CQ6(); else DCQ();
      if (s.light) D75(); else DFk();
      if (s.cull) Ggy(); else F1Q();
      if (s.alpha) D6M(); else Db2();
      B$p(s.src & 65535, s.dst & 65535, s.src >>> 16, s.dst >>> 16); if (s.blend) CyM(); else CTP();
      EFX(s.mask); if (s.depth) DVf(); else C70();
      G0W(33985, s.lx, s.ly); CFh(s.color[0], s.color[1], s.color[2], s.color[3]); GnI(33984 + s.active);
    }
  }

  var api = {
    key: key, begin: begin, step: step, place: place, args: args, sound: sound, pre: pre, post: post, fail: fail, enabled: enabled,
    stats: stats, status: function () {
      var o = { version: core.VERSION, enabled: on, cameraApi: !!cameraApi(), stats: counters(), hands: [core.handInfo(0), core.handInfo(1)],
        disabled: {}, lastError: diag.lastError, firstStack: diag.firstStack, eventsSent: diag.sent };
      for (var k in disabled) o.disabled[k] = disabled[k];
      return o;
    }
  };
  try { $rt_globals.JasprGunFXDiagnostics = Object.freeze({ status: function () { return api.status(); } }); } catch (_) {}
  return api;
}());

// Reads what the gun animation needs from one held stack: null for anything that cannot be a gun (only the seven carrier
// items are looked into). Engine fiber function: every NBT and registry call sits in its own resumable state.
function JasprGunFXRead(a) {
  var b, c, d, $p, $z;
  $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
  case 0:
    if (a === null || a === undefined || !a.rA || a.bg_ || a.bV === null || a.bV === undefined) return null;
    b = { item: 0, damage: a.bK | 0, apoc: null, dungeon: null }; $p = 1;
  case 1:
    $z = C_q(a.rA); if (B()) { break _; } b.item = $z | 0;
    if (!JasprGunFXCore.carrier(b.item)) return null;
    $p = 2;
  case 2:
    $z = GaK(a, JasprGunFX.key('JasprApocalypse')); if (B()) { break _; } c = $z;
    if (c === null) { $p = 9; continue _; }
    b.apoc = { id: '', serial: '', rounds: 0, speed: 0 }; $p = 3;
  case 3:
    $z = F54(c, JasprGunFX.key('id')); if (B()) { break _; } b.apoc.id = $rt_ustr($z);
    if (!Object.prototype.hasOwnProperty.call(JasprGunFXCore.APOC, b.apoc.id)) return null;
    $p = 4;
  case 4:
    $z = F54(c, JasprGunFX.key('serial')); if (B()) { break _; } b.apoc.serial = $rt_ustr($z); $p = 5;
  case 5:
    $z = DcG(c, JasprGunFX.key('rounds')); if (B()) { break _; } b.apoc.rounds = $z | 0; $p = 6;
  case 6:
    $z = GaK(a, JasprGunFX.key('JasprArmament')); if (B()) { break _; } d = $z; if (d === null) return b; $p = 7;
  case 7:
    $z = E$l(d, JasprGunFX.key('Abilities')); if (B()) { break _; } d = $z; $p = 8;
  case 8:
    $z = DcG(d, JasprGunFX.key('speed_loader')); if (B()) { break _; } b.apoc.speed = $z | 0; return b;
  case 9:
    $z = GaK(a, JasprGunFX.key('JasprPenitentArmory')); if (B()) { break _; } c = $z; if (c === null) return null;
    b.dungeon = { kind: '', id: '', uuid: '', rounds: 0, readyAt: 0, reloadAt: 0 }; $p = 10;
  case 10:
    $z = F54(c, JasprGunFX.key('kind')); if (B()) { break _; } b.dungeon.kind = $rt_ustr($z); if (b.dungeon.kind !== 'weapon') return null; $p = 11;
  case 11:
    $z = F54(c, JasprGunFX.key('id')); if (B()) { break _; } b.dungeon.id = $rt_ustr($z);
    if (!Object.prototype.hasOwnProperty.call(JasprGunFXCore.DUNGEON, b.dungeon.id)) return null;
    $p = 12;
  case 12:
    $z = F54(c, JasprGunFX.key('uuid')); if (B()) { break _; } b.dungeon.uuid = $rt_ustr($z); $p = 13;
  case 13:
    $z = DcG(c, JasprGunFX.key('rounds')); if (B()) { break _; } b.dungeon.rounds = $z | 0; $p = 14;
  case 14:
    $z = FQJ(c, JasprGunFX.key('readyAt')); if (B()) { break _; } b.dungeon.readyAt = DU($z); $p = 15;
  case 15:
    $z = FQJ(c, JasprGunFX.key('reloadAt')); if (B()) { break _; } b.dungeon.reloadAt = DU($z); return b;
  default: FT();
  }}
  Ds().s(a, b, c, d, $p);
}

// Once per client tick from EntityRenderer.updateRenderer (C45), right after it read the held stacks: a = ItemRenderer,
// b = player, c = main-hand stack, d = off-hand stack. Reads the data of a hand whose stack is a new object, lets the core
// turn it into shots / reloads / casts, and spawns the requested particles through RenderGlobal.spawnParticle.
function JasprGunFXTick(a, b, c, d) {
  var e, f, g, h, $p, $z;
  $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); h = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
  case 0:
    if (!JasprGunFX.enabled('tick') || b === null || b === undefined) return;
    try { e = JasprGunFX.begin(c, d); } catch (err) { JasprGunFX.fail('tick', err); return; }
    if (!e.readMain) { $p = 2; continue _; }
    $p = 1;
  case 1:
    try { $z = JasprGunFXRead(c); if (B()) { break _; } e.mainDesc = $z; } catch (err) { JasprGunFX.fail('read', err); return; }
    $p = 2;
  case 2:
    if (!e.readOff) { $p = 4; continue _; }
    $p = 3;
  case 3:
    try { $z = JasprGunFXRead(d); if (B()) { break _; } e.offDesc = $z; } catch (err) { JasprGunFX.fail('read', err); return; }
    $p = 4;
  case 4:
    try { f = JasprGunFX.step(a, b, c, d, e); } catch (err) { JasprGunFX.fail('tick', err); return; }
    if (!f || !f.length) return;
    $p = 5;
  case 5:
    try { $z = DlD(b); if (B()) { break _; } f = JasprGunFX.place(f, b, $z === Kua); } catch (err) { JasprGunFX.fail('particles', err); return; }
    g = 0; $p = 6;
  case 6:
    if (g >= f.length) return;
    h = f[g]; $p = 7;
  case 7:
    try { CsB(HEH.fE, h.id, 0, h.x, h.y, h.z, h.vx, h.vy, h.vz, JasprGunFX.args(h)); if (B()) { break _; } } catch (err) { JasprGunFX.fail('particles', err); return; }
    JasprGunFX.stats.spawned++; g = g + 1 | 0; $p = 6; continue _;
  default: FT();
  }}
  Ds().s(a, b, c, d, e, f, g, h, $p);
}
// Ch0 (ItemRenderer.renderItemSide), around RenderItem.renderItemModel: stack, transform type, left-hand flag.
function JasprGunFXPre(c, d, e) { JasprGunFX.pre(c, d, e); }
function JasprGunFXPost() { JasprGunFX.post(); }
// Chn (handleSoundEffect): player, SoundEvent, volume, pitch, x, y, z.
function JasprGunFXSound(b, g, i, j, d, e, f) { JasprGunFX.sound(b, g, i, j, d, e, f); }
