/* JasperCraft first-person melee, v5 core (2026-10-11): vectors, quaternions, the vanilla resting weapon, and the pose
 * primitives every move is built from. Pure math, no engine names, deterministic.
 *
 * Space: view space of the first-person hand pass (x right, y up, -z ahead, blocks, eye at the origin). A pose is the
 * grip position g plus a rotation q (from the vanilla resting orientation). The native transform after the engine's
 * transformSideFirstPerson is one translate and one axis-angle rotate, so a pose costs two matrix calls.
 * Moves are authored the way they look: grip positions as (screen x, screen y, depth) with x and y from -1 to 1 across a
 * 16:9 view through the hand pass's fixed 70 degree field of view, depth in blocks in front of the eye.
 */
var JasprMeleeCore = (function () {
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
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function smooth(t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
  function smoother(t) { t = clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }
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
  function qangle(a, b) { var d = Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]); return 2 * Math.acos(Math.min(1, d)); }
  function qslerp(a, b, t) {
    var d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    if (d < 0) { b = [-b[0], -b[1], -b[2], -b[3]]; }
    return qnorm(qmul(a, qexp(mul(qlog(qmul(qconj(a), b)), t))));
  }
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
  var GRIP_ITEM = [0.22, 0.20, 0.5];                     // where the hand holds the handle, in item space (0..1)
  var GRIP_LOCAL = display(GRIP_ITEM);                   // relative to the pivot
  var TIP_LOCAL = display([0.93, 0.93, 0.5]);
  var GRIP = add(PIVOT, GRIP_LOCAL);                     // grip at rest, view space (blocks)
  var BLADE = unit(sub(TIP_LOCAL, GRIP_LOCAL));          // rest blade direction: up and a little toward the eye
  var FLAT = display([0, 0, 1], true);                   // rest normal of the blade's flat: (-1, 0, 0)
  var EDGE = unit(cross(BLADE, FLAT));                   // rest "back" side; the item's upper-left side (an axe's bit) is -EDGE
  var REST = { g: GRIP, q: [0, 0, 0, 1] };
  // view-space position of an item-space point (0..1 sprite coordinates) for a pose
  function itemPoint(pose, p) { return add(pose.g, qrot(pose.q, sub(display(p), GRIP_LOCAL))); }

  // ---------------------------------------------------------------- screen <-> view
  var TAN_V = Math.tan(35 * DEG), TAN_H = TAN_V * 16 / 9;
  function view(p) { return [p[0] * TAN_H * p[2], p[1] * TAN_V * p[2], -p[2]]; }
  function screen(g) { var d = -g[2]; return d > 1e-6 ? [g[0] / d / TAN_H, g[1] / d / TAN_V, d] : [0, 0, d]; }
  var REST_SCREEN = screen(GRIP);

  // ---------------------------------------------------------------- curves
  // Shape-preserving cubic through N values at increasing parameters (Fritsch-Butland slopes): no overshoot, flat at a
  // turning point, and with three points the same curve as v4's.
  function curveN(vals, ks, u) {
    var n = vals.length, i = 0;
    if (u <= ks[0]) return vals[0];
    if (u >= ks[n - 1]) return vals[n - 1];
    while (i < n - 2 && u > ks[i + 1]) i++;
    var h = ks[i + 1] - ks[i], m0 = slopeAt(vals, ks, i), m1 = slopeAt(vals, ks, i + 1);
    return hermite(vals[i], vals[i + 1], m0 * h, m1 * h, (u - ks[i]) / h);
  }
  function slopeAt(vals, ks, i) {
    var n = vals.length;
    if (n < 2) return 0;
    if (i === 0 || i === n - 1) {
      // quadratic end condition, clamped so the end never turns back
      var j = i === 0 ? 0 : n - 2, s = (vals[j + 1] - vals[j]) / (ks[j + 1] - ks[j]);
      if (n === 2) return s;
      var mi = slopeAt(vals, ks, i === 0 ? 1 : n - 2), m = (3 * s - mi) / 2;
      return m * s <= 0 ? 0 : m;
    }
    var h0 = ks[i] - ks[i - 1], h1 = ks[i + 1] - ks[i], s0 = (vals[i] - vals[i - 1]) / h0, s1 = (vals[i + 1] - vals[i]) / h1;
    if (s0 * s1 <= 0) return 0;
    var w1 = 2 * h1 + h0, w2 = h1 + 2 * h0;
    return (w1 + w2) / (w1 / s0 + w2 / s1);
  }
  function curveN3(points, ks, u) { return [curveN(points.map(function (p) { return p[0]; }), ks, u), curveN(points.map(function (p) { return p[1]; }), ks, u), curveN(points.map(function (p) { return p[2]; }), ks, u)]; }

  // ---------------------------------------------------------------- planes and strokes
  // in-plane axes: yp is "up" seen in the plane ("forward" for a level plane), xp is to its right
  function planeBasis(n) {
    var ref = Math.abs(n[1]) > 0.95 ? [0, 0, -1] : [0, 1, 0], yp = unit(sub(ref, mul(n, dot(ref, n))));
    return { n: n, yp: yp, xp: cross(yp, n) };
  }
  // the plane's normal tipped [yaw, pitch] degrees from the line of sight (toward the eye)
  function planeNormal(tilt) { var y = (tilt ? tilt[0] : 0) * DEG, x = (tilt ? tilt[1] : 0) * DEG; return [Math.sin(y) * Math.cos(x), Math.sin(x), Math.cos(y) * Math.cos(x)]; }
  // A cut: the blade turns inside one plane that faces the eye, like cutting through a sheet hung in front of you, so
  // the edge always leads. grip: hand positions (screen x, y, depth) at the stroke's keys; blade: the blade's angle in
  // the plane at the same keys (0 = pointing right, 90 = up, 180 = left, 270 = down); ks: the keys' parameters (default
  // evenly spaced, the contact key at `contact`); flip turns the other face to the eye; reverse holds the blade
  // pointing back out of the bottom of the fist (a reverse grip: the blade lies opposite the hand's direction).
  function strokeKeys(c) {
    if (c.ks) return c.ks;
    var n = c.grip.length;
    if (n === 3) return [0, c.contact == null ? 0.45 : c.contact, 1];
    var ks = []; for (var i = 0; i < n; i++) ks.push(i / (n - 1));
    return ks;
  }
  function cutPose(c, u) {
    var pb = c.basis || (c.basis = planeBasis(planeNormal(c.plane))), n = pb.n, ks = strokeKeys(c);
    var views = c.views || (c.views = c.grip.map(view));
    var g = curveN3(views, ks, u), th = curveN(c.blade, ks, u) * DEG;
    var d = add(mul(pb.xp, Math.cos(th)), mul(pb.yp, Math.sin(th)));
    var flat = c.flip ? mul(n, -1) : n;
    if (c.roll) { flat = rotateAbout(flat, d, c.roll * DEG); }
    return { g: g, q: qframes(c.reverse ? mul(BLADE, -1) : BLADE, FLAT, d, flat) };
  }
  function rotateAbout(v, axis, angle) { return qrot(qaxis(axis, angle), v); }
  // A thrust: the hand drives through the grip keys while the blade points at the aim point (screen x, y, depth),
  // its flat turned `roll` degrees from upright.
  function thrustPose(c, u) {
    var ks = strokeKeys(c), views = c.views || (c.views = c.grip.map(view));
    var g = curveN3(views, ks, u), aim = c.aimView || (c.aimView = view(c.aim || [0, 0, 2.4]));
    var d = unit(sub(aim, g)), up = unit(sub([0, 1, 0], mul(d, d[1]))), side = cross(d, up);
    var r = (c.rollKeys ? curveN(c.rollKeys, ks, u) : c.roll || 0) * DEG;
    var flat = add(mul(side, Math.cos(r)), mul(up, Math.sin(r)));
    return { g: g, q: qframes(c.reverse ? mul(BLADE, -1) : BLADE, FLAT, d, flat) };
  }
  // A swipe: the resting weapon carried along the grip keys and turned by rotation vectors relative to rest (axis x, y, z
  // in view space and degrees). Independent of how the item is drawn, for hands, claws and odd objects.
  function swipePose(c, u) {
    var ks = strokeKeys(c), views = c.views || (c.views = c.grip.map(view));
    var rv = c.rv || (c.rv = c.rot.map(rotVec));
    return { g: curveN3(views, ks, u), q: qexp(curveN3(rv, ks, u)) };
  }
  function rotVec(r) { return mul(unit([r[0], r[1], r[2]]), r[3] * DEG); }
  function strokePose(c, u) { return c.kind === 'thrust' ? thrustPose(c, u) : c.kind === 'swipe' ? swipePose(c, u) : cutPose(c, u); }
  // Stroke progress with zero speed at both ends and the fastest point at `peak` (fraction of the stroke's time):
  // piecewise Hermite through (0, 0) slope 0, (peak, peak + lead) at top speed, (1, 1) slope 0.
  function strokeProgress(t, peak, lead, top) {
    t = clamp(t, 0, 1);
    var k = clamp(peak == null ? 0.45 : peak, 0.2, 0.8), mid = Math.min(0.92, k + (lead == null ? 0.06 : lead)), v = top || 1.9;
    if (t <= k) return hermite(0, mid, 0, v * k, t / k);
    return hermite(mid, 1, v * (1 - k), 0, (t - k) / (1 - k));
  }
  // time fraction of the stroke at which the path parameter reaches u (bisection on the monotone profile)
  function strokeTimeOf(u, peak, lead, top) {
    var lo = 0, hi = 1;
    for (var i = 0; i < 40; i++) { var m = (lo + hi) / 2; if (strokeProgress(m, peak, lead, top) < u) lo = m; else hi = m; }
    return (lo + hi) / 2;
  }

  // ---------------------------------------------------------------- pose keys
  // Keys {t, g, q} with optional grip velocity v and angular velocity w (per unit of t). Missing velocities are
  // averaged from the neighbouring segments; the first and last key default to rest (zero velocity). Orientation is a
  // cubic Hermite in the tangent space of each segment's first key, so a key can be passed through at speed.
  var ZERO = [0, 0, 0];
  function keyed(keys, t) {
    var i = 0; while (i < keys.length - 2 && t > keys[i + 1].t) i++;
    var a = keys[i], b = keys[i + 1], h = Math.max(1e-6, b.t - a.t), u = clamp((t - a.t) / h, 0, 1);
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
  // A free pose: grip (screen x, y, depth), the blade's direction and its flat's normal in view space.
  function freePose(grip, dir, flat, reverse) {
    var b = unit(dir), f0 = flat || FLAT, f = unit(sub(f0, mul(b, dot(f0, b))));
    if (len(f) < 1e-6) f = unit(cross(b, [0, 1, 0]));
    return { g: view(grip), q: qframes(reverse ? mul(BLADE, -1) : BLADE, FLAT, b, f) };
  }
  // A blade direction given the way it looks: yaw/pitch of the blade's pointing direction (0/0 = straight ahead,
  // yaw 90 = right, pitch 90 = up) - handy for poses outside any stroke plane.
  function aimDir(yaw, pitch) { var y = yaw * DEG, p = pitch * DEG; return [Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p)]; }

  // ---------------------------------------------------------------- item frames (3D models)
  // A model's first-person display (translate px, rotate X then Y then Z degrees, scale, centred) applied to a point in
  // model pixels, relative to the hand pivot, at rest.
  function displayPoint(p, d) {
    var s = d.s, x = (p[0] / 16 - 0.5) * s, y = (p[1] / 16 - 0.5) * s, z = (p[2] / 16 - 0.5) * s;
    var v = rotXYZ([x, y, z], d.r);
    return [v[0] + d.t[0] / 16, v[1] + d.t[1] / 16, v[2] + d.t[2] / 16];
  }
  function rotXYZ(v, r) {
    // GL post-multiplication rotate(x) rotate(y) rotate(z): the vertex turns about Z first, then Y, then X
    var a = r[2] * DEG, c = Math.cos(a), s = Math.sin(a), x = v[0] * c - v[1] * s, y = v[0] * s + v[1] * c, z = v[2];
    a = r[1] * DEG; c = Math.cos(a); s = Math.sin(a); var x2 = x * c + z * s, z2 = -x * s + z * c; x = x2; z = z2;
    a = r[0] * DEG; c = Math.cos(a); s = Math.sin(a); var y2 = y * c - z * s, z3 = y * s + z * c; y = y2; z = z3;
    return [x, y, z];
  }
  // The frame of a model whose working end is model +Y and whose cutting side is edge * model +X: its grip (pivot
  // relative), the rotation R taking its rest axes onto the canonical sprite's (blade -> BLADE, cutting side -> -EDGE),
  // and its trail end points relative to the grip.
  function modelFrame(m) {
    if (m.frame) return m.frame;
    var d = { r: m.r, t: m.t, s: m.s }, e = m.edge || 1, o = displayPoint([8, 8, 8], d);
    var dirOf = function (v) { return unit(sub(displayPoint(add([8, 8, 8], v), d), o)); };
    var bladeM = dirOf([0, 1, 0]), flatM = dirOf([0, 0, -e]), grip = displayPoint(m.grip, d);
    var R = qframes(bladeM, flatM, BLADE, FLAT);
    return (m.frame = { grip: grip, R: R, a: qrot(R, sub(displayPoint(m.base, d), grip)), b: qrot(R, sub(displayPoint(m.tip, d), grip)) });
  }
  // how far a pose is from rest, 0..1 (a model's own rest look holds near rest and blends into the canonical frame)
  function restBlend(pose) {
    var d = len(sub(pose.g, REST.g)) / 0.12 + qangle(pose.q, REST.q) / 0.5;
    return smooth(clamp(d, 0, 1));
  }
  // the rotation and grip actually sent to the engine for an item frame (null frame = the canonical sprite)
  function calibrated(pose, frame) {
    if (!frame) return { q: pose.q, grip: GRIP_LOCAL };
    var s = restBlend(pose);
    return { q: qnorm(qmul(pose.q, qslerp([0, 0, 0, 1], frame.R, s))), grip: lerp3(GRIP_LOCAL, frame.grip, s) };
  }
  // view-space point of an item: a canonical sprite point (0..1) or a model trail point already relative to the grip
  function framedPoint(pose, frame, p, local) {
    if (!local) return itemPoint(pose, p);
    // local points are given in the canonical frame (R applied), so the pose's own rotation places them
    return add(pose.g, qrot(pose.q, p));
  }

  // ---------------------------------------------------------------- native transform
  // The native transform after transformSideFirstPerson: translate, then one axis-angle rotation, so that the rest grip
  // lands on the pose's grip and the weapon takes the pose's orientation. Left hands mirror X (the left-hand display
  // transform is the mirror image of the right one: sprites lie in the mirror plane, 3D models are mirrored by
  // ItemTransformVec3f.apply's negated Y and Z rotations).
  function toNative(pose, right, name, progress, phase, frame) {
    var cal = calibrated(pose, frame), q = qnorm(cal.q), g = pose.g, t = sub(sub(g, PIVOT), qrot(q, cal.grip));
    if (q[3] < 0) q = [-q[0], -q[1], -q[2], -q[3]];
    var s = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]), angle = 2 * Math.atan2(s, q[3]) / DEG;
    var axis = s > 1e-10 ? [q[0] / s, q[1] / s, q[2] / s] : [0, 0, 1];
    if (!right) { t = [-t[0], t[1], t[2]]; axis = [axis[0], -axis[1], -axis[2]]; }
    return { clip: name, progress: progress, tx: t[0], ty: t[1], tz: t[2], rotation: { angle: angle, x: axis[0], y: axis[1], z: axis[2] }, phase: phase || pose.phase };
  }
  function mirror(p) { return [-p[0], p[1], p[2]]; }

  return { DEG: DEG, add: add, sub: sub, mul: mul, dot: dot, cross: cross, len: len, unit: unit, lerp: lerp, lerp3: lerp3, clamp: clamp,
    smooth: smooth, smoother: smoother, qmul: qmul, qconj: qconj, qnorm: qnorm, qaxis: qaxis, qrot: qrot, qlog: qlog, qexp: qexp,
    qangle: qangle, qslerp: qslerp, qframes: qframes, hermite: hermite, hermite3: hermite3, PIVOT: PIVOT, display: display,
    GRIP_ITEM: GRIP_ITEM, GRIP_LOCAL: GRIP_LOCAL, TIP_LOCAL: TIP_LOCAL, GRIP: GRIP, BLADE: BLADE, FLAT: FLAT, EDGE: EDGE, REST: REST,
    REST_SCREEN: REST_SCREEN, itemPoint: itemPoint, TAN_V: TAN_V, TAN_H: TAN_H, view: view, screen: screen, curveN: curveN,
    curveN3: curveN3, planeBasis: planeBasis, planeNormal: planeNormal, cutPose: cutPose, thrustPose: thrustPose,
    strokePose: strokePose, strokeKeys: strokeKeys, strokeProgress: strokeProgress, strokeTimeOf: strokeTimeOf, ZERO: ZERO,
    keyed: keyed, freePose: freePose, aimDir: aimDir, toNative: toNative, mirror: mirror, rotateAbout: rotateAbout, rotVec: rotVec,
    displayPoint: displayPoint, modelFrame: modelFrame, restBlend: restBlend, calibrated: calibrated, framedPoint: framedPoint };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeCore;
