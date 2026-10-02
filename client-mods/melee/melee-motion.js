/* JasperCraft first-person melee motion (v4, 2026-10-02).
 * Places the held weapon in first person as if a real arm swung it: the grip travels on arcs around a virtual shoulder,
 * a cut keeps the blade in its swing plane so the edge leads the motion, the wrist cocks before the strike and turns
 * over through it, and the reversal points (top of the wind-up, end of the follow-through) slow to a stop the way a
 * real swing does. Clocked by simulation ticks (player ticks + partial ticks), never by wall time. Third-person attack
 * poses are Mo' Bends' job; nothing here touches the player model. No camera shake, no gameplay, timing or packet
 * change: only the first-person held-item matrix (one translate and one axis-angle rotate) is produced.
 */
var JasprMeleeMotion = (function () {
  'use strict';
  var DEG = Math.PI / 180;

  // ---------------------------------------------------------------- vectors and quaternions ([x, y, z, w])
  function add(a, b) { return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]; }
  function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
  function mul(a, s) { return [a[0] * s, a[1] * s, a[2] * s]; }
  function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function len(a) { return Math.sqrt(dot(a, a)); }
  function unit(a) { var l = len(a); return l > 1e-12 ? mul(a, 1 / l) : [0, 0, 0]; }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function lerp3(a, b, t) { return [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)]; }
  function qmul(a, b) {
    return [a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1], a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
      a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3], a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]];
  }
  function qconj(q) { return [-q[0], -q[1], -q[2], q[3]]; }
  function qnorm(q) { var l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]) || 1; return [q[0] / l, q[1] / l, q[2] / l, q[3] / l]; }
  function qaxis(axis, angle) { var a = unit(axis), s = Math.sin(angle / 2); return [a[0] * s, a[1] * s, a[2] * s, Math.cos(angle / 2)]; }
  function qrot(q, v) { var r = qmul(qmul(q, [v[0], v[1], v[2], 0]), qconj(q)); return [r[0], r[1], r[2]]; }
  // rotation vector (axis * angle) of a unit quaternion, shortest arc
  function qlog(q) {
    if (q[3] < 0) q = [-q[0], -q[1], -q[2], -q[3]];
    var s = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
    if (s < 1e-9) return [q[0] * 2, q[1] * 2, q[2] * 2];
    var angle = 2 * Math.atan2(s, q[3]);
    return [q[0] / s * angle, q[1] / s * angle, q[2] / s * angle];
  }
  function qexp(v) { var angle = len(v); return angle < 1e-12 ? [v[0] / 2, v[1] / 2, v[2] / 2, 1] : qaxis(v, angle); }
  // rotation taking the orthonormal frame (a1, b1) onto (a2, b2)
  function qframes(a1, b1, a2, b2) {
    var c1 = cross(a1, b1), c2 = cross(a2, b2), m = [[0, 0, 0], [0, 0, 0], [0, 0, 0]];
    for (var i = 0; i < 3; i++) for (var j = 0; j < 3; j++) m[i][j] = a2[i] * a1[j] + b2[i] * b1[j] + c2[i] * c1[j];
    var tr = m[0][0] + m[1][1] + m[2][2], q;
    if (tr > 0) { var s = Math.sqrt(tr + 1) * 2; q = [(m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s, s / 4]; }
    else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) { var s0 = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2; q = [s0 / 4, (m[0][1] + m[1][0]) / s0, (m[0][2] + m[2][0]) / s0, (m[2][1] - m[1][2]) / s0]; }
    else if (m[1][1] > m[2][2]) { var s1 = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2; q = [(m[0][1] + m[1][0]) / s1, s1 / 4, (m[1][2] + m[2][1]) / s1, (m[0][2] - m[2][0]) / s1]; }
    else { var s2 = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2; q = [(m[0][2] + m[2][0]) / s2, (m[1][2] + m[2][1]) / s2, s2 / 4, (m[1][0] - m[0][1]) / s2]; }
    return qnorm(q);
  }
  // cubic Hermite basis on [0, 1] for value pairs and slopes already scaled by the span
  function hermite(p0, p1, m0, m1, t) {
    var t2 = t * t, t3 = t2 * t;
    return (2 * t3 - 3 * t2 + 1) * p0 + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * p1 + (t3 - t2) * m1;
  }
  function hermite3(p0, p1, m0, m1, t) { return [hermite(p0[0], p1[0], m0[0], m1[0], t), hermite(p0[1], p1[1], m0[1], m1[1], t), hermite(p0[2], p1[2], m0[2], m1[2], t)]; }

  // ---------------------------------------------------------------- the resting weapon (vanilla first person)
  // transformSideFirstPerson puts the right hand's pivot at (0.56, -0.52, -0.72); item/handheld's
  // firstperson_righthand (translate 1.13/3.2/1.13 px, rotate 0/-90/25, scale 0.68) then holds the sprite.
  var PIVOT = [0.56, -0.52, -0.72];
  function display(v, direction) {
    var x = (v[0] - (direction ? 0 : 0.5)) * 0.68, y = (v[1] - (direction ? 0 : 0.5)) * 0.68, z = (v[2] - (direction ? 0 : 0.5)) * 0.68;
    var c = Math.cos(25 * DEG), s = Math.sin(25 * DEG), x1 = x * c - y * s, y1 = x * s + y * c;
    var out = [-z, y1, x1];   // rotate -90 degrees about Y: (x, y, z) -> (-z, y, x)
    return direction ? unit(out) : add(out, [1.13 / 16, 3.2 / 16, 1.13 / 16]);
  }
  var GRIP_LOCAL = display([0.22, 0.20, 0.5]);          // where the hand holds the handle, relative to the pivot
  var TIP_LOCAL = display([0.93, 0.93, 0.5]);
  var GRIP = add(PIVOT, GRIP_LOCAL);                   // grip at rest, view space (blocks)
  var BLADE = unit(sub(TIP_LOCAL, GRIP_LOCAL));        // rest blade direction: up and a little toward the eye
  var FLAT = display([0, 0, 1], true);                 // rest normal of the blade's flat: (-1, 0, 0)
  var EDGE = unit(cross(BLADE, FLAT));                 // rest forward edge direction

  var REST = { g: GRIP, q: [0, 0, 0, 1] };

  // ---------------------------------------------------------------- strokes
  // Positions are given the way they look: (screen x, screen y, depth) with x and y from -1 to 1 across a 16:9 view
  // seen through the hand's fixed 70 degree field of view, and depth in blocks in front of the eye.
  var TAN_V = Math.tan(35 * DEG), TAN_H = TAN_V * 16 / 9;
  function view(p) { return [p[0] * TAN_H * p[2], p[1] * TAN_V * p[2], -p[2]]; }
  function smooth(t) { return t * t * (3 - 2 * t); }
  // shape-preserving cubic through (0, v0), (k, v1), (1, v2): no overshoot, flat at a turning point
  function curve(v0, v1, v2, k, u) {
    var s0 = (v1 - v0) / k, s1 = (v2 - v1) / (1 - k), m = 0;
    if (s0 * s1 > 0) { var w1 = 2 * (1 - k) + k, w2 = (1 - k) + 2 * k; m = (w1 + w2) / (w1 / s0 + w2 / s1); }
    var m0 = (3 * s0 - m) / 2, m2 = (3 * s1 - m) / 2;
    if (m0 * s0 <= 0) m0 = 0; if (m2 * s1 <= 0) m2 = 0;
    return u <= k ? hermite(v0, v1, m0 * k, m * k, u / k) : hermite(v1, v2, m * (1 - k), m2 * (1 - k), (u - k) / (1 - k));
  }
  function curve3(a, b, c, k, u) { return [curve(a[0], b[0], c[0], k, u), curve(a[1], b[1], c[1], k, u), curve(a[2], b[2], c[2], k, u)]; }
  // in-plane axes: yp is "up" seen in the plane ("forward" for a level plane), xp is to its right
  function planeBasis(n) {
    var ref = Math.abs(n[1]) > 0.95 ? [0, 0, -1] : [0, 1, 0], yp = unit(sub(ref, mul(n, dot(ref, n))));
    return { n: n, yp: yp, xp: cross(yp, n) };
  }
  function planeNormal(tilt) { var y = (tilt ? tilt[0] : 0) * DEG, x = (tilt ? tilt[1] : 0) * DEG; return [Math.sin(y) * Math.cos(x), Math.sin(x), Math.cos(y) * Math.cos(x)]; }
  // cut: the blade turns inside one plane that faces the eye (its normal tipped by plane = [yaw, pitch] degrees from
  // the line of sight), like cutting through a sheet hung in front of you, so the edge always leads the motion.
  // grip: the hand at the start of the stroke, at contact and at the end; blade: the blade's angle in that plane at
  // the same moments (0 = pointing right, 90 = up, 180 = left, 270 = down); flip turns the other face to the eye.
  function cutPose(c, u) {
    var B = planeBasis(planeNormal(c.plane)), n = B.n, yp = B.yp, xp = B.xp, k = c.contact;
    var g = curve3(view(c.grip[0]), view(c.grip[1]), view(c.grip[2]), k, u);
    var th = curve(c.blade[0], c.blade[1], c.blade[2], k, u) * DEG;
    var d = add(mul(xp, Math.cos(th)), mul(yp, Math.sin(th)));
    return { g: g, q: qframes(BLADE, FLAT, d, c.flip ? mul(n, -1) : n) };
  }
  // thrust: the hand drives from grip[0] to grip[1] while the blade points at the aim point (screen x, y, depth),
  // its flat turned `roll` degrees from upright.
  function thrustPose(c, u) {
    var t = smooth(u), g = lerp3(view(c.grip[0]), view(c.grip[1]), t), aim = view(c.aim || [0, 0, 2.4]);
    var d = unit(sub(aim, g)), up = unit(sub([0, 1, 0], mul(d, d[1]))), side = cross(d, up), r = (c.roll || 0) * DEG;
    var flat = add(mul(side, Math.cos(r)), mul(up, Math.sin(r)));
    return { g: g, q: qframes(BLADE, FLAT, d, flat) };
  }

  // Every clip: wind-up into the stroke's first pose, the stroke itself, recovery to the guard. Times are fractions
  // of the clip; the stroke's speed profile peaks at `peak` (fraction of the stroke) and is zero at both ends.
  var clips = {
    // Swords. From the guard: a falling diagonal forehand, a cut straight down and a level slash; once the blade is
    // over on the left (a quick follow-up), a rising backhand brings it back across.
    'sword-forehand': { side: 'right', windup: 0.13, stroke: 0.30, peak: 0.40,
      cut: { plane: [0, -20], grip: [[0.80, 0.30, 0.55], [0.28, -0.32, 0.62], [-0.50, -0.85, 0.55]], blade: [80, 165, 230], contact: 0.45 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'sword-overhead': { side: 'right', windup: 0.15, stroke: 0.30, peak: 0.42,
      cut: { plane: [-40, -5], grip: [[0.38, 1.05, 0.45], [0.18, -0.10, 0.60], [0.02, -0.75, 0.55]], blade: [75, 165, 240], contact: 0.47 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'sword-sweep': { side: 'right', windup: 0.14, stroke: 0.30, peak: 0.40,
      cut: { plane: [0, 72], grip: [[0.95, -0.25, 0.50], [0.20, -0.40, 0.66], [-0.75, -0.50, 0.50]], blade: [20, 120, 195], contact: 0.45 },
      recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'sword-backhand': { side: 'left', windup: 0.16, stroke: 0.30, peak: 0.40,
      cut: { plane: [0, -15], grip: [[-0.55, -0.55, 0.55], [0.05, -0.30, 0.62], [0.80, -0.15, 0.55]], blade: [165, 40, -10], contact: 0.45 } },
    // Axes: a cocked wind-up over the shoulder and a steep chop, a diagonal cleave, a backhand cleave from the left.
    'axe-chop': { side: 'right', windup: 0.20, stroke: 0.28, peak: 0.45,
      cut: { plane: [-30, -10], grip: [[0.55, 0.95, 0.48], [0.22, -0.20, 0.62], [-0.10, -0.70, 0.52]], blade: [75, 175, 240], contact: 0.48 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'axe-cleave': { side: 'right', windup: 0.18, stroke: 0.28, peak: 0.42,
      cut: { plane: [0, -10], grip: [[0.85, 0.20, 0.52], [0.25, -0.30, 0.62], [-0.50, -0.65, 0.55]], blade: [60, 160, 220], contact: 0.46 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'axe-backhand': { side: 'left', windup: 0.20, stroke: 0.28, peak: 0.42,
      cut: { plane: [0, -10], grip: [[-0.60, -0.40, 0.55], [0.05, -0.28, 0.62], [0.85, -0.10, 0.55]], blade: [160, 45, -15], contact: 0.46, flip: true } },
    // Mauls, hammers, greatswords: slow and big. An overhead smash, a wide level sweep, a backhand return.
    'heavy-smash': { side: 'right', windup: 0.26, stroke: 0.26, peak: 0.48,
      cut: { plane: [-40, -5], grip: [[0.35, 1.20, 0.45], [0.14, -0.22, 0.62], [0.02, -0.75, 0.55]], blade: [60, 175, 250], contact: 0.50 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'heavy-sweep': { side: 'right', windup: 0.24, stroke: 0.27, peak: 0.45,
      cut: { plane: [0, 68], grip: [[1.05, -0.20, 0.48], [0.15, -0.42, 0.66], [-0.75, -0.40, 0.50]], blade: [10, 120, 200], contact: 0.48 },
      recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'heavy-return': { side: 'left', windup: 0.20, stroke: 0.27, peak: 0.45,
      cut: { plane: [0, -10], grip: [[-0.65, -0.45, 0.55], [0.05, -0.30, 0.64], [0.90, -0.10, 0.55]], blade: [160, 45, -20], contact: 0.48, flip: true } },
    // Spears, lances, rapiers: drawn back, then driven at the crosshair; a quicker jab.
    'spear-drive': { side: 'right', windup: 0.34, stroke: 0.16, thrust: { grip: [[0.80, -0.66, 0.36], [0.28, -0.34, 0.74]], aim: [0, 0, 2.6] } },
    'spear-jab': { side: 'right', windup: 0.30, stroke: 0.14, thrust: { grip: [[0.74, -0.60, 0.42], [0.34, -0.38, 0.68]], aim: [0, 0, 2.6] } },
    // Scythes, sickles, halberds, whips: wide reaping sweeps that pull back toward the body.
    'hook-reap': { side: 'right', windup: 0.20, stroke: 0.30, peak: 0.44,
      cut: { plane: [0, 55], grip: [[1.00, 0.05, 0.55], [0.20, -0.35, 0.70], [-0.55, -0.45, 0.48]], blade: [30, 140, 210], contact: 0.46 },
      recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'hook-return': { side: 'left', windup: 0.18, stroke: 0.30, peak: 0.44,
      cut: { plane: [0, 55], grip: [[-0.70, -0.50, 0.50], [0.05, -0.35, 0.68], [0.90, -0.15, 0.50]], blade: [170, 60, 0], contact: 0.46, flip: true } },
    // Daggers: short, quick cuts close to the body and a stab.
    'dagger-slash': { side: 'right', windup: 0.12, stroke: 0.30, peak: 0.38,
      cut: { plane: [0, -15], grip: [[0.70, 0.05, 0.50], [0.25, -0.35, 0.58], [-0.30, -0.70, 0.52]], blade: [75, 160, 215], contact: 0.42 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'dagger-stab': { side: 'right', windup: 0.30, stroke: 0.16, thrust: { grip: [[0.74, -0.72, 0.38], [0.30, -0.38, 0.62]], aim: [0, 0, 2.2] } },
    'dagger-backhand': { side: 'left', windup: 0.16, stroke: 0.30, peak: 0.38,
      cut: { plane: [0, -15], grip: [[-0.40, -0.60, 0.50], [0.05, -0.35, 0.58], [0.65, -0.15, 0.52]], blade: [175, 50, 0], contact: 0.42 } },
    // Pickaxes, shovels, hoes used as weapons: an overhand hack, a short side swipe, a backhand return.
    'tool-hack': { side: 'right', windup: 0.18, stroke: 0.30, peak: 0.45,
      cut: { plane: [-45, -10], grip: [[0.45, 0.85, 0.48], [0.20, -0.22, 0.62], [0.05, -0.90, 0.52]], blade: [70, 170, 245], contact: 0.48 } },
    'tool-swipe': { side: 'right', windup: 0.15, stroke: 0.30, peak: 0.42,
      cut: { plane: [0, -15], grip: [[0.80, 0.15, 0.52], [0.25, -0.30, 0.62], [-0.45, -0.75, 0.55]], blade: [70, 160, 225], contact: 0.45 }, recover: [{ at: 0.45, grip: [0.55, -0.82, 0.58], blade: [0.70, -0.10, -0.70], flat: [-0.55, 0.10, 0.83] }] },
    'tool-backhand': { side: 'left', windup: 0.16, stroke: 0.30, peak: 0.42,
      cut: { plane: [0, -15], grip: [[-0.50, -0.55, 0.55], [0.05, -0.30, 0.62], [0.75, -0.15, 0.55]], blade: [165, 45, -5], contact: 0.45 } }
  };
  var groups = { sword: ['sword-forehand', 'sword-overhead', 'sword-sweep', 'sword-backhand'], axe: ['axe-chop', 'axe-cleave', 'axe-backhand'],
    heavy: ['heavy-smash', 'heavy-sweep', 'heavy-return'], spear: ['spear-drive', 'spear-jab'], hook: ['hook-reap', 'hook-return'],
    dagger: ['dagger-slash', 'dagger-stab', 'dagger-backhand'], tool: ['tool-hack', 'tool-swipe', 'tool-backhand'] };
  var durations = { sword: 9, axe: 12, heavy: 14, spear: 9, hook: 12, dagger: 7, tool: 10, fist: 7 };

  function strokePose(clip, u) { return clip.cut ? cutPose(clip.cut, u) : thrustPose(clip.thrust, u); }
  // stroke progress with zero speed at both ends and the fastest point at `peak`
  function strokeProgress(t, peak) {
    // piecewise Hermite through (0,0) slope 0, (peak, peak + 0.06) at its top speed, (1,1) slope 0
    var k = Math.max(0.2, Math.min(0.8, peak || 0.45)), mid = Math.min(0.92, k + 0.06), v = 1.9;
    if (t <= k) return hermite(0, mid, 0, v * k, t / k);
    return hermite(mid, 1, v * (1 - k), 0, (t - k) / (1 - k));
  }

  // Pose tracks: keys {t, g, q} with optional grip velocity v and world angular velocity w (both per unit of clip
  // progress). Missing velocities are averaged from the neighbouring segments; the first and last key default to
  // rest. Orientation is a cubic Hermite in the tangent space of each segment's first key, so a key can be passed
  // through at speed instead of stopping at it.
  var ZERO = [0, 0, 0];
  function keyed(keys, t) {
    var i = 0; while (i < keys.length - 2 && t > keys[i + 1].t) i++;
    var a = keys[i], b = keys[i + 1], h = Math.max(1e-6, b.t - a.t), u = Math.max(0, Math.min(1, (t - a.t) / h));
    var delta = segment(a, b), wa = qrot(qconj(a.q), spin(keys, i)), wb = qrot(qconj(a.q), spin(keys, i + 1));
    var g = hermite3(a.g, b.g, mul(speed(keys, i), h), mul(speed(keys, i + 1), h), u);
    var r = hermite3(ZERO, delta, mul(wa, h), mul(wb, h), u);
    return { g: g, q: qnorm(qmul(a.q, qexp(r))) };
  }
  function segment(a, b) { return qlog(qmul(qconj(a.q), b.q)); }
  function speed(keys, i) {
    var k = keys[i]; if (k.v) return k.v; if (i === 0 || i === keys.length - 1) return ZERO;
    return mul(sub(keys[i + 1].g, keys[i - 1].g), 1 / (keys[i + 1].t - keys[i - 1].t));
  }
  function spin(keys, i) {
    var k = keys[i]; if (k.w) return k.w; if (i === 0 || i === keys.length - 1) return ZERO;
    var p = keys[i - 1], n = keys[i + 1];
    var w0 = mul(qrot(p.q, segment(p, k)), 1 / (k.t - p.t)), w1 = mul(qrot(k.q, segment(k, n)), 1 / (n.t - k.t));
    return mul(add(w0, w1), 0.5);
  }
  // a via key inside a clip: grip position (screen x, y, depth) and the blade's angle in the stroke's plane
  function via(clip, at, key) {
    if (typeof key.blade !== 'number') {
      var b = unit(key.blade), f0 = key.flat || FLAT, f = unit(sub(f0, mul(b, dot(f0, b))));
      return { t: at, g: view(key.grip), q: qframes(BLADE, FLAT, b, f) };
    }
    var c = clip.cut || clip.thrust, B = planeBasis(planeNormal(c.plane)), n = B.n, yp = B.yp, xp = B.xp, th = key.blade * DEG;
    var d = add(mul(xp, Math.cos(th)), mul(yp, Math.sin(th)));
    return { t: at, g: view(key.grip), q: qframes(BLADE, FLAT, d, c.flip ? mul(n, -1) : n) };
  }
  function recoveryKeys(clip) {
    if (clip.recoveryKeys) return clip.recoveryKeys;
    var t0 = clip.windup + clip.stroke, last = strokePose(clip, 1), keys = [{ t: t0, g: last.g, q: last.q, v: ZERO, w: ZERO }];
    (clip.recover || []).forEach(function (k) { keys.push(via(clip, t0 + (1 - t0) * k.at, k)); });
    keys.push({ t: 1, g: REST.g, q: REST.q, v: ZERO, w: ZERO });
    return (clip.recoveryKeys = keys);
  }
  function windupKeys(swing, clip, T) {
    if (swing.windupKeys && swing.windupClip === swing.clip) return swing.windupKeys;
    var from = swing.start, first = strokePose(clip, 0), keys;
    keys = [from ? { t: 0, g: from.g, q: from.q, v: mul(from.v, T), w: mul(from.w, T) } : { t: 0, g: REST.g, q: REST.q, v: ZERO, w: ZERO }];
    (from ? [] : clip.ready || []).forEach(function (k) { keys.push(via(clip, clip.windup * k.at, k)); });
    keys.push({ t: clip.windup, g: first.g, q: first.q, v: ZERO, w: ZERO });
    swing.windupClip = swing.clip;
    return (swing.windupKeys = keys);
  }
  // A swing in progress: `start` is where the weapon was (pose and velocity per tick) when the attack began, so a
  // combo or a quick re-click flows out of the previous motion instead of snapping to the guard first.
  function evaluate(swing, tick) {
    var clip = clips[swing.clip];
    if (!clip) return { g: REST.g, q: REST.q, phase: 'rest' };
    var T = swing.duration, p = Math.max(0, Math.min(1, tick / T)), w = clip.windup, s = clip.stroke, pose;
    if (p < w) { pose = keyed(windupKeys(swing, clip, T), p); pose.phase = 'windup'; return pose; }
    if (p < w + s) { pose = strokePose(clip, strokeProgress((p - w) / s, clip.peak)); pose.phase = 'stroke'; return pose; }
    pose = keyed(recoveryKeys(clip), p); pose.phase = 'recover'; return pose;
  }
  function velocity(swing, tick) {
    var h = 0.02, t0 = Math.max(0, tick - h), a = evaluate(swing, t0), b = evaluate(swing, tick + h), dt = tick + h - t0;
    return { v: mul(sub(b.g, a.g), 1 / dt), w: mul(qrot(a.q, qlog(qmul(qconj(a.q), b.q))), 1 / dt) };
  }
  function screenX(g) { return g[2] < -1e-3 ? g[0] / -g[2] / TAN_H : 0; }

  // The native transform after transformSideFirstPerson: translate, then one axis-angle rotation, so that the
  // rest grip lands on the pose's grip and the weapon takes the pose's orientation. Left hands mirror X.
  function toNative(pose, right, name, progress) {
    var q = qnorm(pose.q), g = pose.g, t = sub(sub(g, PIVOT), qrot(q, GRIP_LOCAL));
    var s = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]), angle = 2 * Math.atan2(s, q[3]) / DEG;
    var axis = s > 1e-10 ? [q[0] / s, q[1] / s, q[2] / s] : [0, 0, 1];
    if (!right) { t = [-t[0], t[1], t[2]]; axis = [axis[0], -axis[1], -axis[2]]; }
    return { clip: name, progress: progress, tx: t[0], ty: t[1], tz: t[2], rotation: { angle: angle, x: axis[0], y: axis[1], z: axis[2] }, phase: pose.phase };
  }

  // ---------------------------------------------------------------- items -> families (vanilla ids + custom metadata)
  function family(id) {
    return id === 0 ? 'fist' : [267, 268, 272, 276, 283].indexOf(id) >= 0 ? 'sword' : [258, 271, 275, 279, 286].indexOf(id) >= 0 ? 'axe'
      : [256, 257, 269, 270, 273, 274, 277, 278, 284, 285, 290, 291, 292, 293, 294].indexOf(id) >= 0 ? 'tool' : null;
  }
  function describe(id, nbt) {
    var type = family(id), key = 'vanilla:' + id, part = typeof nbt === 'string' ? nbt.match(/(?:^|[,{])JasprApocalypse:\{([^{}]*)\}/) : null;
    var name = part && part[1].match(/(?:^|,)id:(?:"([a-z0-9_]+)"|([a-z0-9_]+))(?:,|$)/);
    if (name && type) {
      key = name[1] || name[2];
      type = /spear|lance|rapier/.test(key) ? 'spear' : /dagger|dirk|trench_blade|gravespike/.test(key) ? 'dagger' : /scythe|sickle|glaive|halberd|whip/.test(key) ? 'hook'
        : /axe|cleaver/.test(key) ? 'axe' : /maul|hammer|mallet|mace|flail|greatsword|rebar_sword|execution_sword/.test(key) ? 'heavy' : type;
    }
    return { family: type, key: key, id: id };
  }

  // ---------------------------------------------------------------- per-hand timelines
  // Each hand plays one swing and holds at most one more. A click during a wind-up or strike never cuts the stroke
  // off: the next move waits for that stroke to finish and flows out of its follow-through, so even fast clicking
  // alternates forehand and backhand like a real combo. A click during the recovery starts at once, out of the
  // weapon's current pose and velocity. Damage is untouched: this only decides what the first-person weapon shows.
  var hands = [null, null], descriptors = new WeakMap();
  var stats = { draws: 0, marks: 0, chained: 0, queued: 0, last: null, clips: {} };
  // moves that start where the weapon is: a backhand only begins once the blade is already over on the left
  var fitting = {};   // per family and side, built once (restyle runs every frame)
  function choose(type, sequence, side) {
    var names = groups[type]; if (!names) return null;
    var key = type + '/' + side, fits = fitting[key];
    if (!fits) { fits = names.filter(function (n) { return (clips[n].side || 'right') === side; }); if (!fits.length) fits = names; fitting[key] = fits; }
    return fits[(sequence - 1) % fits.length];
  }
  function strokeEnd(swing) { var c = clips[swing.clip]; return swing.tick + (c.windup + c.stroke) * swing.duration; }
  function stateAt(swing, tick) { var at = tick - swing.tick, pose = evaluate(swing, at), vel = velocity(swing, at); return { g: pose.g, q: pose.q, v: vel.v, w: vel.w }; }
  function mark(off, id, now, tick) {
    var type = family(id), index = off ? 1 : 0, hand = hands[index];
    if (!type || !groups[type]) { hands[index] = null; return; }   // fists keep the vanilla arm
    stats.marks++;
    var same = hand && hand.id === id && now >= hand.at && now - hand.at <= 1300 && Number.isFinite(tick) && hand.tick !== null && tick >= hand.tick;
    if (same && tick < strokeEnd(hand)) {
      if (!hand.queued) { hand.queued = { sequence: hand.sequence + 1 }; stats.queued++; }
      hand.at = now;
      return;
    }
    var swing = { id: id, at: now, tick: Number.isFinite(tick) ? tick : null, sequence: same ? hand.sequence + 1 : 1, type: same ? hand.type : type,
      key: same ? hand.key : null, bound: same ? hand.bound : false, side: 'right', duration: 0, start: null, queued: null };
    if (same && tick < hand.tick + hand.duration) {
      swing.start = stateAt(hand, tick); stats.chained++;
      if (screenX(swing.start.g) < -0.15) swing.side = 'left';
    }
    swing.duration = durations[swing.type];
    swing.clip = choose(swing.type, swing.sequence, swing.side);
    hands[index] = swing.clip ? swing : null;
  }
  // the waiting move takes over when the playing stroke has finished, starting exactly where that stroke ended
  function promote(hand) {
    var at = strokeEnd(hand), start = stateAt(hand, at);
    hand.sequence = hand.queued.sequence; hand.queued = null; hand.tick = at; hand.start = start; hand.windupKeys = null;
    hand.side = screenX(start.g) < -0.15 ? 'left' : 'right';
    hand.clip = choose(hand.type, hand.sequence, hand.side); hand.duration = durations[hand.type];
    stats.chained++;
  }
  // The real item decides the family (a custom spear on a vanilla id is a spear); a different custom item cancels.
  function restyle(stamp, descriptor) {
    if (!stamp || !descriptor.family) return null;
    if (stamp.bound && stamp.key !== descriptor.key) return null;
    if (stamp.key && stamp.key !== descriptor.key) { stamp.sequence = 1; stamp.start = null; stamp.queued = null; }
    stamp.key = descriptor.key; stamp.bound = true; stamp.type = descriptor.family;
    stamp.clip = choose(stamp.type, stamp.sequence, stamp.side || 'right'); stamp.duration = durations[stamp.type];
    if (!stamp.clip) return null;
    if (stamp.windupClip !== stamp.clip) stamp.windupKeys = null;
    return stamp;
  }
  function current(main, id, now) { var s = hands[main ? 0 : 1]; return s && s.id === id && now >= s.at && now - s.at < 1600 ? s : null; }
  function phase(stamp, tick) {
    if (!stamp || !Number.isFinite(tick) || stamp.tick === null) return null;
    if (stamp.queued && tick >= strokeEnd(stamp)) promote(stamp);
    return Math.max(0, (tick - stamp.tick) / stamp.duration);
  }
  function sample(stamp, progress, right) { return toNative(evaluate(stamp, progress * stamp.duration), right, stamp.clip, progress); }
  function pose(name, progress, right) {
    var type = null; for (var g in groups) if (groups[g].indexOf(name) >= 0) type = g;
    return toNative(evaluate({ clip: name, duration: durations[type] || 9, start: null }, progress * (durations[type] || 9)), right, name, progress);
  }
  function clear() { hands = [null, null]; stats.last = null; }
  function record(p, main, id, sequence) { stats.draws++; stats.clips[p.clip] = (stats.clips[p.clip] || 0) + 1; stats.last = { clip: p.clip, progress: p.progress, main: main, id: id, sequence: sequence }; }

  return { family: family, describe: describe, mark: mark, restyle: restyle, current: current, phase: phase, sample: sample, pose: pose,
    clear: clear, record: record, stats: stats, descriptors: descriptors, durations: durations, groups: groups, clipNames: Object.keys(clips), partial: 0,
    _test: { evaluate: evaluate, velocity: velocity, keyed: keyed, screenX: screenX, clips: clips, strokePose: strokePose, REST: REST, GRIP: GRIP, GRIP_LOCAL: GRIP_LOCAL, PIVOT: PIVOT,
      BLADE: BLADE, FLAT: FLAT, EDGE: EDGE, view: view, qrot: qrot, toNative: toNative, hands: function () { return hands; } } };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeMotion;
