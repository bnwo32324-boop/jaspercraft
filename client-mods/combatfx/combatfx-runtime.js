/* JasperCraft JASPR_COMBATFX runtime (2026-10-11): camera impulses, impact sparks and blade trails for the combat rework.
 * Pure logic, no engine names: combatfx-teavm.js is the engine glue (the camera hook in hurtCameraEffect, the trail
 * pass after the first-person hands, World.spawnParticle). Everything is short and bounded: no full-screen flashes, no
 * glow or outline on entities, nothing full-bright; the client particle setting decides whether sparks spawn and the
 * "minimal" setting also turns trails off.
 */

/* JasprCameraFX: additive, decaying, bounded view kicks. Never moves the player's real rotation or what the server sees
 * (only the render transform of the camera and the first-person hands).
 *   JasprCameraFX.kick({ pitch, yaw, roll, back, ms, curve })
 *     pitch  degrees, POSITIVE = the view kicks UP (muzzle climb); a melee chop dips the view with a negative pitch
 *     yaw    degrees, positive = the view turns right
 *     roll   degrees, positive = the view rolls clockwise (right side down)
 *     back   blocks,  positive = the camera is pushed back (world view only; the hands stay where they are)
 *     ms     duration (16..1000, default 120); curve: 'punch' (default: quick rise, smooth fall), 'recoil' (very quick
 *            rise, slower return), 'snap' (instant, then a smooth return), 'smooth' (sine), 'spring' (damped wobble)
 *   Each kick is clamped (6 degrees, back -0.15..0.25 blocks), at most 12 run at once (the oldest is dropped) and their
 *   sum is clamped (8 degrees, back -0.2..0.3). Clocked by the player's ticks + partial ticks. Third person gets half.
 */
var JasprCameraFX = (function () {
  'use strict';
  var MAX = 12, kicks = [], now = NaN, scale = 1, pass = 0, enabled = true;
  var stats = { kicks: 0, rejected: 0, dropped: 0, frames: 0, active: 0, last: null };
  var CURVES = {
    punch: function (u) { return u < 0.18 ? Math.sin(u / 0.18 * Math.PI / 2) : Math.pow(1 - (u - 0.18) / 0.82, 2); },
    recoil: function (u) { return u < 0.08 ? Math.sin(u / 0.08 * Math.PI / 2) : Math.pow(1 - (u - 0.08) / 0.92, 1.6); },
    snap: function (u) { return Math.pow(1 - u, 2); },
    smooth: function (u) { return Math.sin(Math.PI * u); },
    spring: function (u) { return Math.exp(-4 * u) * Math.cos(3 * Math.PI * u) * (1 - u); }
  };
  function lim(v, a, b) { v = +v; return Number.isFinite(v) ? Math.max(a, Math.min(b, v)) : 0; }
  // a reduced-motion preference of the browser halves every kick
  try { var g = typeof $rt_globals !== 'undefined' ? $rt_globals : null; if (g && typeof g.matchMedia === 'function' && g.matchMedia('(prefers-reduced-motion: reduce)').matches) scale = 0.5; } catch (e) {}
  function kick(o) {
    if (!enabled || !o || typeof o !== 'object') { stats.rejected++; return false; }
    var k = { pitch: lim(o.pitch, -6, 6), yaw: lim(o.yaw, -6, 6), roll: lim(o.roll, -6, 6), back: lim(o.back, -0.15, 0.25),
      ticks: lim(o.ms == null ? 120 : o.ms, 16, 1000) / 50, curve: CURVES[o.curve] ? o.curve : 'punch', start: now };
    if (!k.pitch && !k.yaw && !k.roll && !k.back) { stats.rejected++; return false; }
    if (kicks.length >= MAX) { kicks.shift(); stats.dropped++; }
    kicks.push(k); stats.kicks++; stats.last = { pitch: k.pitch, yaw: k.yaw, roll: k.roll, back: k.back, ms: k.ticks * 50, curve: k.curve };
    return true;
  }
  // the summed offset at render time T (ticks); prunes finished kicks; null when nothing is running
  function offsets(T, third) {
    if (!Number.isFinite(T)) return null;
    if (Number.isFinite(now) && T < now - 2) kicks.length = 0;            // the tick count restarted (respawn, new world)
    now = T; stats.frames++;
    if (!kicks.length || !enabled) return null;
    var p = 0, y = 0, r = 0, b = 0, live = [];
    for (var i = 0; i < kicks.length; i++) {
      var k = kicks[i];
      if (!Number.isFinite(k.start)) k.start = T;
      var u = (T - k.start) / k.ticks;
      if (u >= 1) continue;
      live.push(k);
      if (u < 0) continue;
      var f = CURVES[k.curve](u);
      p += k.pitch * f; y += k.yaw * f; r += k.roll * f; b += k.back * f;
    }
    kicks = live; stats.active = live.length;
    var s = scale * (third ? 0.5 : 1);
    var o = { pitch: lim(p, -8, 8) * s, yaw: lim(y, -8, 8) * s, roll: lim(r, -8, 8) * s, back: lim(b, -0.2, 0.3) * s };
    return o.pitch || o.yaw || o.roll || o.back ? o : null;
  }
  function clear() { kicks.length = 0; }
  function status() { var o = {}; for (var k in stats) o[k] = stats[k]; o.scale = scale; o.enabled = enabled; o.running = kicks.length; return o; }
  return {
    kick: kick, offsets: offsets, clear: clear, status: status, CURVES: CURVES,
    get scale() { return scale; }, set scale(v) { scale = lim(v, 0, 1); },
    get enabled() { return enabled; }, set enabled(v) { enabled = !!v; if (!enabled) kicks.length = 0; },
    get pass() { return pass; }, set pass(v) { pass = v ? 1 : 0; },
    get now() { return now; }
  };
}());

/* JasprCombatFX: turns the first-person melee motion's impact intents (JasprMeleeMotion.drain) into camera kicks and
 * spark plans, and its blade trails (JasprMeleeMotion.trail) into ribbon vertices. Each part disables itself on error.
 */
var JasprCombatFX = (function () {
  'use strict';
  var parts = { kicks: true, sparks: true, trails: true }, failed = {};
  var stats = { frames: 0, impacts: 0, upgrades: 0, sparkPlans: 0, sparks: 0, sparkDrops: 0, trails: 0, trailQuads: 0, errors: 0, lastError: null };
  var lastT = NaN, budget = { at: -1e9, used: 0 }, SPARKS_PER_SECOND = 48;
  // particle ids (EnumParticleTypes): crit 9, magicCrit 10, smoke 11, witchMagic 17, portal 24, cloud 29
  var KINDS = {
    blade: [[9, 4, 0.32]], pierce: [[9, 3, 0.18]], blunt: [[9, 2, 0.28], [29, 2, 0.06]], claw: [[24, 4, 0.5], [9, 1, 0.25]],
    magic: [[17, 3, 0.25], [9, 1, 0.25]]
  };
  function fail(part, e) {
    stats.errors++; stats.lastError = part + ': ' + String(e && e.message || e).replace(/[\r\n]+/g, ' ').slice(0, 160);
    if (!failed[part]) { failed[part] = true; parts[part] = false; }
    return stats.lastError;
  }
  function on(part) { return !!parts[part] && !failed[part]; }
  // Once per rendered frame: drain the melee impact intents. Returns the spark requests the engine glue spawns.
  function frame(T, melee) {
    if (!Number.isFinite(T) || T === lastT) return null;
    lastT = T; stats.frames++;
    var list;
    try { list = melee && typeof melee.drain === 'function' ? melee.drain() : null; } catch (e) { fail('kicks', e); return null; }
    if (!list || !list.length) return null;
    var out = [];
    for (var i = 0; i < list.length && i < 8; i++) {
      var e = list[i]; if (!e) continue;
      if (e.kind === 'upgrade') stats.upgrades++; else stats.impacts++;
      if (on('kicks') && e.kick) {
        try {
          // the melee data dips the view with a positive pitch; the camera API raises it with a positive pitch
          JasprCameraFX.kick({ pitch: -e.kick.pitch, yaw: e.kick.yaw, roll: e.kick.roll, back: e.kick.back, ms: e.kick.ms, curve: e.kick.curve });
        } catch (x) { fail('kicks', x); }
      }
      if (on('sparks') && e.target >= 0) out.push(e);
    }
    return out.length ? out : null;
  }
  // Spark requests for one impact at a target box ({x0,y0,z0,x1,y1,z1}) seen from the eye, with the player's view angles
  // (yaw, pitch degrees, Minecraft convention). rnd: () => 0..1. Bounded per impact and per second (frame time T ticks).
  function sparkPlan(e, box, eye, yaw, pitch, rnd, T) {
    if (!on('sparks') || !box || !eye) return [];
    if (T - budget.at > 20) { budget.at = T; budget.used = 0; }
    var kinds = (KINDS[e.sparks] || KINDS.blade).slice();
    if (e.crit) kinds.push([10, 4, 0.4]);
    if (e.blocked) kinds = [[9, 5, 0.35]];
    if (e.fatal) kinds.push([29, 2, 0.05]);
    if (e.kind === 'upgrade') kinds = e.crit ? [[10, 4, 0.4]] : e.blocked ? [[9, 3, 0.3]] : [];
    var cx = (box.x0 + box.x1) / 2, cz = (box.z0 + box.z1) / 2, cy = box.y0 + (box.y1 - box.y0) * 0.62;
    // the side of the target facing the eye, where the blade met it
    var dx = eye[0] - cx, dz = eye[2] - cz, dl = Math.sqrt(dx * dx + dz * dz) || 1, half = Math.min(0.6, (box.x1 - box.x0) / 2);
    var hx = cx + dx / dl * half, hz = cz + dz / dl * half, hy = Math.min(box.y1 - 0.1, Math.max(box.y0 + 0.1, Math.min(cy, eye[1] - 0.15)));
    // the swing's screen direction, in the world: right and up of the view
    var ya = yaw * Math.PI / 180, pa = pitch * Math.PI / 180;
    var right = [-Math.cos(ya), 0, -Math.sin(ya)], look = [-Math.sin(ya) * Math.cos(pa), -Math.sin(pa), Math.cos(ya) * Math.cos(pa)];
    var up = [right[1] * look[2] - right[2] * look[1], right[2] * look[0] - right[0] * look[2], right[0] * look[1] - right[1] * look[0]];
    var d = e.dir || [0, 0], sx = d[0], sy = d[1], out = [];
    for (var k = 0; k < kinds.length; k++) {
      var id = kinds[k][0], n = kinds[k][1], sp = kinds[k][2];
      for (var j = 0; j < n && out.length < 9; j++) {
        if (budget.used >= SPARKS_PER_SECOND) { stats.sparkDrops++; return out; }
        budget.used++;
        var jx = (rnd() - 0.5) * 0.5, jy = (rnd() - 0.5) * 0.5, jz = (rnd() - 0.5) * 0.5;
        var vx = (right[0] * sx + up[0] * sy - look[0] * 0.35 + jx) * sp, vy = (right[1] * sx + up[1] * sy - look[1] * 0.35 + jy + 0.15) * sp, vz = (right[2] * sx + up[2] * sy - look[2] * 0.35 + jz) * sp;
        out.push({ id: id, x: hx + (rnd() - 0.5) * 0.2, y: hy + (rnd() - 0.5) * 0.25, z: hz + (rnd() - 0.5) * 0.2, vx: vx, vy: vy, vz: vz });
      }
    }
    stats.sparkPlans++; stats.sparks += out.length;
    return out;
  }
  // A ribbon from a blade trail: per segment 4 vertices (x, y, z, r, g, b, a), brightest along the edge's path, fading
  // toward the hilt and the tail. Mirrored for a hand drawn on the left.
  function trailVertices(t, out) {
    out = out || [];
    if (!t || !on('trails') || !(t.n >= 2)) return out;
    var p = t.pts, s = t.right ? 1 : -1, c = t.color || [1, 1, 1], peak = (t.crit ? 0.72 : 0.6) * Math.min(1.25, t.width || 1), base = 0.08;
    var r = Math.min(1, c[0]), g = Math.min(1, c[1]), b = Math.min(1, c[2]), quads = 0;
    for (var i = 0; i + 1 < t.n; i++) {
      var a = i * 7, z = (i + 1) * 7, aa = Math.min(1, p[a + 6] * peak), az = Math.min(1, p[z + 6] * peak);
      if (aa < 0.01 && az < 0.01) continue;
      out.push(p[a] * s, p[a + 1], p[a + 2], r, g, b, aa * base,
        p[a + 3] * s, p[a + 4], p[a + 5], r, g, b, aa,
        p[z + 3] * s, p[z + 4], p[z + 5], r, g, b, az,
        p[z] * s, p[z + 1], p[z + 2], r, g, b, az * base);
      quads++;
    }
    stats.trails++; stats.trailQuads += quads;
    return out;
  }
  function status() { var o = {}; for (var k in stats) o[k] = stats[k]; o.parts = { kicks: on('kicks'), sparks: on('sparks'), trails: on('trails') }; return o; }
  return { frame: frame, sparkPlan: sparkPlan, trailVertices: trailVertices, fail: fail, on: on, parts: parts, status: status, KINDS: KINDS,
    reset: function () { lastT = NaN; budget.at = -1e9; budget.used = 0; } };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = { JasprCameraFX: JasprCameraFX, JasprCombatFX: JasprCombatFX };
