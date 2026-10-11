/* JasperCraft first-person melee v5: the weapon classes and their movesets.
 *
 * Every move is authored for the right hand the way it looks: at each key the grip sits at screen (x, y) from -1 to 1
 * and `depth` blocks from the eye, and the blade's tip shows at screen (tx, ty) (sc: a cut; its plane is fitted so the
 * edge leads). Times are ticks at the class's nominal cooldown (w wind-up, h loaded pause at the top, s stroke,
 * r recovery). The left hand plays the mirror image. A class has a combo chain (strokes chosen in order, each only when
 * it starts where the weapon is: a backhand waits for a blade on the left), a sprint variant, a jump (falling) variant,
 * and its impact feel: hit-stop, camera kick, sparks, trail colour and the stretch of the item the trail follows.
 */
var JasprMeleeMoves = (function (C) {
  'use strict';
  var moves = {}, classes = {};
  // ---------------------------------------------------------------- authoring by what you see
  // sc([[gx, gy, depth, tx, ty], ...], o): the blade's directions at the keys are fitted with one plane through the
  // eye-facing sweep (the stroke's plane: the edge leads), their angles in it unwrapped, and the face chosen so the
  // cutting side (an axe's bit, the sprite's upper-left) leads. o.reverse: a reverse grip (the tip hangs below the fist).
  var BLADE_LEN = 0.68;
  function tipView(g, ts, near) {
    var r = C.unit([ts[0] * C.TAN_H, ts[1] * C.TAN_V, -1]), b = C.dot(r, g), c = C.dot(g, g) - BLADE_LEN * BLADE_LEN, disc = b * b - c;
    var t = disc < 0 ? b : near ? b - Math.sqrt(disc) : b + Math.sqrt(disc);
    return C.mul(r, t);
  }
  function dirOf(grip, tip, reverse) { var g = C.view(grip), d = C.unit(C.sub(tipView(g, tip), g)); return reverse ? C.mul(d, -1) : d; }
  function sc(keys, o) {
    o = o || {};
    var grips = keys.map(function (k) { return [k[0], k[1], k[2]]; });
    var dirs = keys.map(function (k) { return dirOf([k[0], k[1], k[2]], [k[3], k[4]], o.reverse); });
    var n = [0, 0, 0];
    for (var i = 0; i + 1 < dirs.length; i++) n = C.add(n, C.cross(dirs[i], dirs[i + 1]));
    if (dirs.length > 2) n = C.add(n, C.mul(C.cross(dirs[0], dirs[dirs.length - 1]), 0.5));
    n = C.unit(n);
    if (n[2] < 0) n = C.mul(n, -1);                                   // the plane faces the eye
    var pb = C.planeBasis(n), prev = null, blade = dirs.map(function (d) {
      var a = Math.atan2(C.dot(d, pb.yp), C.dot(d, pb.xp)) / C.DEG;
      if (prev !== null) { while (a - prev > 180) a -= 360; while (a - prev < -180) a += 360; }
      prev = a; return +a.toFixed(2);
    });
    var c = { kind: 'cut', plane: [+(Math.atan2(n[0], n[2]) / C.DEG).toFixed(3), +(Math.asin(C.clamp(n[1], -1, 1)) / C.DEG).toFixed(3)], grip: grips, blade: blade };
    // with the angle rising the blade moves along n x d, the side the sprite's upper-left faces when its flat faces
    // the eye; a falling angle turns the other face to the eye
    c.flip = o.flip != null ? o.flip : blade[blade.length - 1] < blade[0];
    for (var k in o) if (k !== 'flip') c[k] = o[k];
    return c;
  }
  // a via key from [at, grip [x, y, depth], tip [tx, ty], extra]: its flat follows the stroke's plane
  function via(e, stroke) {
    if (!Array.isArray(e)) return e;
    var extra = e[3] || {}, d = dirOf(e[1], e[2], extra.reverse);
    var n = stroke && stroke.kind === 'cut' ? C.planeNormal(stroke.plane) : [0, 0, 1];
    if (stroke && stroke.flip) n = C.mul(n, -1);
    var k = { at: e[0], grip: e[1], dir: d, flat: extra.flat || n };
    for (var x in extra) if (x !== 'flat') k[x] = extra[x];
    return k;
  }
  function thrust(grip, o) { var c = { kind: 'thrust', grip: grip, ks: [0, 0.68, 1], aim: [0, 0, 2.6] }; for (var k in o) c[k] = o[k]; return c; }
  function swipe(grip, rot, o) { var c = { kind: 'swipe', grip: grip, rot: rot }; for (var k in o) c[k] = o[k]; return c; }
  function move(name, cls, d) {
    d.name = name; d.cls = cls; d.from = d.from || 'any';
    if (d.ready) d.ready = d.ready.map(function (e) { return via(e, d.stroke); });
    if (d.recover) d.recover = d.recover.map(function (e) { return via(e, d.stroke); });
    moves[name] = d; return name;
  }
  function klass(name, d) { d.name = name; classes[name] = d; return d; }
  // key sets (sword sized), reused and reshaped per class: [gx, gy, depth, tx, ty]
  function shape(keys, f) { return keys.map(function (k, i) { var o = f ? f(k.slice(), i) : k.slice(); return o; }); }
  var FORE = [[0.78, 0.24, 0.56, 0.96, 0.96], [0.3, -0.3, 0.62, -0.55, 0.06], [-0.55, -0.88, 0.56, -1.05, -0.85]];
  var BACK = [[-0.58, -0.82, 0.56, -1.02, -0.52], [0.05, -0.28, 0.62, -0.4, 0.5], [0.8, 0.24, 0.56, 0.98, 0.96]];
  var LEVEL = [[0.92, -0.3, 0.55, 1.08, 0.06], [0.25, -0.42, 0.64, -0.55, -0.22], [-0.8, -0.5, 0.54, -1.25, -0.6]];
  var LEVEL_BACK = [[-0.82, -0.52, 0.54, -1.22, -0.3], [-0.05, -0.4, 0.64, 0.68, -0.2], [0.9, -0.3, 0.55, 1.15, -0.02]];
  var OVER = [[0.42, 0.4, 0.52, 0.3, 0.98], [0.18, -0.15, 0.62, -0.45, 0.12], [0.02, -0.88, 0.56, -0.3, -1.25]];
  // recovery and wind-up vias: [at, grip, tip]
  var REC_LOW_LEFT = [[0.28, [-0.2, -0.96, 0.58], [-0.45, -0.32]], [0.62, [0.45, -0.98, 0.62], [0.62, -0.06]]];
  var REC_HIGH_RIGHT = [[0.3, [0.92, 0.0, 0.58], [1.08, 0.62]], [0.66, [0.62, -0.88, 0.66], [0.85, 0.05]]];
  var REC_LOW_CENTER = [[0.3, [0.12, -0.98, 0.58], [0.1, -0.3]], [0.64, [0.45, -0.98, 0.62], [0.62, -0.06]]];
  var REC_LEFT = [[0.3, [-0.42, -0.9, 0.56], [-0.82, -0.5]], [0.64, [0.4, -0.98, 0.62], [0.6, -0.1]]];
  var REC_THRUST = [[0.4, [0.58, -0.75, 0.64], [0.55, 0.15]]];
  function at(list, scale) { return list.map(function (e) { return [e[0], e[1], e[2], e[3]]; }); }
  var THRUST = [[0.8, -0.86, 0.42], [0.34, -0.5, 0.82], [0.4, -0.56, 0.75]];

  // ================================================================ SWORD: flowing diagonals, a level cut, a thrust finisher
  klass('sword', { cat: 'blade', period: 12.5, weight: 0.45, hitstop: 45, kickMs: 110, sparks: 'blade', color: [0.92, 0.95, 1],
    kick: { pitch: 0.35, yaw: -0.35, roll: -0.5 }, trail: { from: [0.42, 0.42, 0.5], to: [0.93, 0.93, 0.5] },
    chain: ['sword-diag', 'sword-rise', 'sword-flat', 'sword-thrust'], sprint: 'sword-lunge', jump: 'sword-cleave', preview: 'diamond_sword' });
  move('sword-diag', 'sword', { from: 'right', ends: 'left', w: 2.2, s: 2.0, r: 6.8, peak: 0.42,
    stroke: sc(FORE, { contact: 0.45 }), ready: [[0.5, [0.95, -0.4, 0.6], [1.18, 0.45]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 0.35, yaw: -0.35, roll: -0.5 }, dir: [-0.85, -0.55] });
  move('sword-rise', 'sword', { from: 'left', ends: 'high', w: 1.6, s: 2.0, r: 6.8, peak: 0.42,
    stroke: sc(BACK, { contact: 0.45 }), recover: at(REC_HIGH_RIGHT),
    kick: { pitch: -0.3, yaw: 0.35, roll: 0.45 }, dir: [0.85, 0.55] });
  move('sword-flat', 'sword', { from: 'right', ends: 'left', w: 2.0, s: 2.0, r: 6.8, peak: 0.4,
    stroke: sc(LEVEL, { contact: 0.45 }), ready: [[0.55, [1.02, -0.62, 0.58], [1.28, -0.18]]], recover: at(REC_LEFT),
    kick: { pitch: 0.12, yaw: -0.6, roll: -0.25 }, dir: [-1, -0.08] });
  move('sword-thrust', 'sword', { from: 'any', ends: 'center', w: 3.2, s: 1.2, r: 7.4, peak: 0.5, lead: 0.04,
    stroke: thrust(THRUST, { aim: [0, 0, 2.6], rollKeys: [-10, 80, 90] }),
    ready: [{ at: 0.55, grip: [0.95, -0.98, 0.38], aim: [-12, 6], flat: [1, 0, 0] }], recover: at(REC_THRUST),
    kick: { pitch: 0.15, yaw: 0, roll: 0, back: 0.03 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.7, 0.7, 0.5], to: [0.93, 0.93, 0.5], len: 0.7 } });
  // sprint: a running rising cut, low right up through the target to high left
  move('sword-lunge', 'sword', { from: 'any', ends: 'high', w: 1.8, s: 2.1, r: 7.2, peak: 0.45,
    stroke: sc([[0.82, -0.88, 0.52, 1.25, -0.72], [0.18, -0.25, 0.7, -0.5, 0.25], [-0.62, 0.3, 0.56, -0.98, 0.98]], { contact: 0.46 }),
    recover: [[0.32, [-0.35, -0.2, 0.6], [-0.7, 0.5]], [0.68, [0.4, -0.95, 0.62], [0.62, -0.08]]],
    kick: { pitch: -0.35, yaw: -0.3, roll: -0.45, back: -0.03 }, dir: [-0.7, 0.7] });
  // jump (falling): a vertical cleave
  move('sword-cleave', 'sword', { from: 'any', ends: 'low', w: 2.3, h: 0.5, s: 1.9, r: 7.0, peak: 0.45,
    stroke: sc(OVER, { contact: 0.47 }), ready: [[0.5, [0.85, -0.2, 0.58], [0.95, 0.68]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.8, yaw: 0, roll: -0.2 }, dir: [-0.2, -1] });

  // ================================================================ KATANA / LONGSWORD: an iaido draw, kesa cuts, a thrust with chiburi
  klass('katana', { cat: 'blade', period: 12.5, weight: 0.4, hitstop: 40, kickMs: 95, sparks: 'blade', color: [0.78, 0.9, 1],
    kick: { pitch: 0.3, yaw: -0.4, roll: -0.45 }, trail: { from: [0.4, 0.4, 0.5], to: [0.93, 0.93, 0.5], alpha: 1.1 },
    chain: ['katana-draw', 'katana-kesa', 'katana-gyaku', 'katana-tsuki'], sprint: 'katana-dash', jump: 'katana-kabuto', preview: 'iron_sword' });
  // the draw: the blade is carried across to the left hip, then cut out level and rising to the right in one motion
  move('katana-draw', 'katana', { from: 'any', ends: 'right', w: 2.4, s: 1.5, r: 6.6, peak: 0.36, lead: 0.08, top: 2.1,
    stroke: sc([[-0.66, -0.72, 0.52, -1.22, -0.82], [0.0, -0.36, 0.66, 0.72, -0.08], [0.92, -0.05, 0.55, 1.2, 0.5]], { contact: 0.42 }),
    ready: [[0.45, [0.15, -1.0, 0.52], [-0.4, -1.15]]],
    recover: [[0.3, [0.95, -0.35, 0.58], [1.15, 0.35]], [0.68, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: -0.1, yaw: 0.5, roll: 0.35 }, dir: [1, 0.3] });
  move('katana-kesa', 'katana', { from: 'right', ends: 'left', w: 1.6, s: 1.4, r: 6.6, peak: 0.38, top: 2.0,
    stroke: sc(shape(FORE, function (k, i) { if (i === 0) { k[1] = 0.32; k[4] = 1.0; } if (i === 2) { k[1] = -0.95; k[4] = -0.98; } return k; }), { contact: 0.45 }),
    ready: [[0.5, [0.95, -0.25, 0.58], [1.12, 0.6]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 0.4, yaw: -0.35, roll: -0.5 }, dir: [-0.8, -0.65] });
  move('katana-gyaku', 'katana', { from: 'left', ends: 'high', w: 1.4, s: 1.4, r: 6.6, peak: 0.38, top: 2.0,
    stroke: sc(BACK, { contact: 0.45 }), recover: at(REC_HIGH_RIGHT),
    kick: { pitch: -0.35, yaw: 0.35, roll: 0.45 }, dir: [0.8, 0.65] });
  // the finisher: a straight thrust, then chiburi - the blade flicked down to the side - before it comes back
  move('katana-tsuki', 'katana', { from: 'any', ends: 'center', w: 2.5, s: 1.1, r: 7.8, peak: 0.5,
    stroke: thrust([[0.62, -0.8, 0.45], [0.3, -0.45, 0.85], [0.36, -0.5, 0.8]], { aim: [0, 0.02, 2.8], rollKeys: [0, 0, 0] }),
    ready: [{ at: 0.5, grip: [0.85, -0.98, 0.42], aim: [-8, 10], flat: [1, 0, 0] }],
    recover: [[0.2, [0.5, -0.42, 0.66], [0.75, 0.32]], [0.38, [0.82, -0.88, 0.62], [1.18, -0.95]], [0.72, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: 0.12, back: 0.03 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.68, 0.68, 0.5], to: [0.93, 0.93, 0.5], len: 0.7 } });
  move('katana-dash', 'katana', { from: 'any', ends: 'left', w: 1.4, s: 1.4, r: 6.8, peak: 0.36, top: 2.1,
    stroke: sc(shape(LEVEL, function (k, i) { k[2] += 0.06; return k; }), { contact: 0.44 }), recover: at(REC_LEFT),
    kick: { pitch: 0.1, yaw: -0.7, roll: -0.3, back: -0.03 }, dir: [-1, -0.1] });
  move('katana-kabuto', 'katana', { from: 'any', ends: 'low', w: 2.0, h: 0.4, s: 1.5, r: 7.0, peak: 0.42,
    stroke: sc(OVER, { contact: 0.47 }), ready: [[0.5, [0.85, -0.2, 0.58], [0.95, 0.68]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.75, roll: -0.15 }, dir: [-0.15, -1] });

  // ================================================================ RAPIER: lunges, flicks, a riposte, a flourish
  klass('rapier', { cat: 'blade', period: 10, weight: 0.3, hitstop: 35, kickMs: 85, sparks: 'pierce', color: [0.96, 0.96, 1],
    kick: { pitch: 0.12, back: 0.025 }, trail: { from: [0.6, 0.6, 0.5], to: [0.93, 0.93, 0.5], width: 0.7 },
    chain: ['rapier-lunge', 'rapier-flick', 'rapier-riposte', 'rapier-flourish'], sprint: 'rapier-fleche', jump: 'rapier-plunge', preview: 'gold_sword' });
  move('rapier-lunge', 'rapier', { from: 'any', ends: 'center', w: 2.5, s: 1.1, r: 5.8, peak: 0.5,
    stroke: thrust([[0.62, -0.75, 0.46], [0.28, -0.4, 0.98], [0.33, -0.45, 0.9]], { aim: [0, 0.05, 3.0], rollKeys: [0, 15, 15] }),
    ready: [{ at: 0.5, grip: [0.82, -0.9, 0.44], aim: [-5, 12], flat: [1, 0, 0] }], recover: at(REC_THRUST),
    kick: { pitch: 0.1, back: 0.03 }, dir: [0, 0] });
  move('rapier-flick', 'rapier', { from: 'any', ends: 'left', w: 1.2, s: 1.1, r: 5.0, peak: 0.4,
    stroke: sc([[0.62, -0.05, 0.6, 0.85, 0.75], [0.32, -0.3, 0.68, -0.25, 0.25], [0.02, -0.55, 0.64, -0.6, -0.42]], { contact: 0.45 }),
    recover: [[0.42, [0.5, -0.82, 0.64], [0.6, 0.05]]],
    kick: { pitch: 0.15, yaw: -0.2, roll: -0.2 }, sparks: 'blade', dir: [-0.6, -0.6] });
  // disengage: the point circles under the guard, then drives home
  move('rapier-riposte', 'rapier', { from: 'any', ends: 'center', w: 1.4, s: 1.9, r: 5.6, peak: 0.6, contactKey: 2,
    stroke: thrust([[0.62, -0.55, 0.52], [0.46, -0.68, 0.58], [0.27, -0.4, 0.95], [0.32, -0.45, 0.88]], { ks: [0, 0.32, 0.74, 1], aim: [0, 0.03, 3.0], rollKeys: [0, 40, 90, 90] }),
    recover: at(REC_THRUST), kick: { pitch: 0.1, back: 0.03 }, dir: [0, 0] });
  // the finisher: a high thrust and a showman's flourish of the wrist on the way back
  move('rapier-flourish', 'rapier', { from: 'any', ends: 'center', w: 2.0, s: 1.1, r: 7.2, peak: 0.5,
    stroke: thrust([[0.55, -0.48, 0.5], [0.26, -0.22, 0.95], [0.3, -0.3, 0.88]], { aim: [0, 0.15, 3.0], rollKeys: [90, 90, 90] }),
    recover: [{ at: 0.3, grip: [0.45, -0.4, 0.62], aim: [25, 62], flat: [0, 0, 1] }, { at: 0.64, grip: [0.58, -0.75, 0.64], aim: [18, 32], flat: [-0.5, 0, 0.86] }],
    twirl: { from: 0.05, to: 0.55, turns: 1, axis: 'blade' }, kick: { pitch: -0.05, back: 0.03 }, dir: [0, 0] });
  move('rapier-fleche', 'rapier', { from: 'any', ends: 'center', w: 1.9, s: 1.2, r: 6.0, peak: 0.5,
    stroke: thrust([[0.66, -0.82, 0.44], [0.26, -0.38, 1.04], [0.32, -0.44, 0.94]], { aim: [0, 0.03, 3.2], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.08, back: 0.05 }, dir: [0, 0] });
  move('rapier-plunge', 'rapier', { from: 'any', ends: 'low', w: 2.1, s: 1.2, r: 6.0, peak: 0.5,
    stroke: thrust([[0.45, 0.42, 0.5], [0.22, -0.12, 0.82], [0.26, -0.24, 0.76]], { aim: [0, -0.35, 2.2], rollKeys: [90, 90, 90] }),
    recover: at(REC_THRUST), kick: { pitch: 0.4, back: 0.02 }, dir: [0, -1] });

  // ================================================================ GREATSWORD: huge loaded sweeps, an overhead cleave, a lunge
  klass('greatsword', { cat: 'heavy', period: 20, weight: 0.9, hitstop: 70, kickMs: 170, sparks: 'blade', color: [1, 0.93, 0.82],
    kick: { pitch: 0.7, yaw: -0.7, roll: -0.9 }, trail: { from: [0.38, 0.38, 0.5], to: [0.93, 0.93, 0.5], width: 1.3 },
    chain: ['great-sweep', 'great-return', 'great-overhead', 'great-thrust'], sprint: 'great-charge', jump: 'great-fall', preview: 'iron_sword' });
  var GREAT_LEVEL = [[1.02, -0.22, 0.52, 1.22, 0.2], [0.2, -0.45, 0.66, -0.62, -0.25], [-0.9, -0.55, 0.52, -1.38, -0.72]];
  move('great-sweep', 'greatsword', { from: 'right', ends: 'left', w: 3.8, h: 1.2, s: 2.5, r: 10.5, peak: 0.45, tense: 0.08,
    stroke: sc(GREAT_LEVEL, { contact: 0.46 }), ready: [[0.45, [1.02, -0.7, 0.56], [1.32, -0.18]]],
    recover: [[0.22, [-0.92, -0.88, 0.52], [-1.3, -0.95]], [0.52, [-0.15, -0.98, 0.58], [-0.4, -0.35]], [0.8, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.2, yaw: -0.9, roll: -0.7 }, dir: [-1, -0.1] });
  move('great-return', 'greatsword', { from: 'left', ends: 'high', w: 2.4, s: 2.5, r: 10.5, peak: 0.45,
    stroke: sc([[-0.85, -0.7, 0.52, -1.3, -0.5], [0.0, -0.25, 0.64, -0.5, 0.55], [0.9, 0.32, 0.52, 1.08, 1.0]], { contact: 0.46 }),
    recover: at(REC_HIGH_RIGHT), kick: { pitch: -0.4, yaw: 0.6, roll: 0.8 }, dir: [0.85, 0.55] });
  move('great-overhead', 'greatsword', { from: 'any', ends: 'low', w: 4.4, h: 1.6, s: 2.3, r: 10.5, peak: 0.48, tense: 0.09,
    stroke: sc([[0.42, 0.48, 0.5, 0.25, 0.98], [0.14, -0.15, 0.62, -0.5, 0.08], [0.0, -0.95, 0.56, -0.3, -1.3]], { contact: 0.48 }),
    ready: [[0.5, [0.85, -0.1, 0.56], [0.9, 0.78]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 1.2, yaw: 0, roll: -0.2, back: 0.02 }, dir: [-0.1, -1] });
  move('great-thrust', 'greatsword', { from: 'any', ends: 'center', w: 4.0, s: 1.5, r: 10.0, peak: 0.5,
    stroke: thrust([[0.8, -0.88, 0.42], [0.32, -0.48, 0.86], [0.38, -0.54, 0.79]], { aim: [0, 0, 2.8], rollKeys: [80, 90, 90] }),
    ready: [{ at: 0.5, grip: [1.0, -1.0, 0.4], aim: [-10, 8], flat: [0, 1, 0] }], recover: at(REC_THRUST),
    kick: { pitch: 0.25, back: 0.05 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.65, 0.65, 0.5], to: [0.93, 0.93, 0.5], len: 0.7, width: 1.3 } });
  move('great-charge', 'greatsword', { from: 'any', ends: 'left', w: 2.6, s: 2.5, r: 10.5, peak: 0.45,
    stroke: sc([[1.05, -0.62, 0.5, 1.4, -0.4], [0.18, -0.38, 0.72, -0.62, -0.1], [-0.92, -0.2, 0.52, -1.35, 0.15]], { contact: 0.46 }),
    recover: at(REC_LEFT), kick: { pitch: 0.2, yaw: -0.8, roll: -0.6, back: -0.04 }, dir: [-1, 0.15] });
  move('great-fall', 'greatsword', { from: 'any', ends: 'low', w: 3.0, h: 0.8, s: 2.1, r: 10.5, peak: 0.48,
    stroke: sc([[0.35, 0.5, 0.5, 0.15, 0.98], [0.1, -0.2, 0.62, -0.48, 0.0], [-0.02, -1.0, 0.56, -0.22, -1.35]], { contact: 0.48 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 1.5, roll: -0.1, back: 0.03 }, dir: [0, -1] });

  // ================================================================ AXE: shoulder wind-up and chop, a cleave whose momentum carries, a hook back
  klass('axe', { cat: 'heavy', period: 20, weight: 0.65, hitstop: 60, kickMs: 150, sparks: 'blade', color: [1, 0.86, 0.7],
    kick: { pitch: 0.9, yaw: -0.3, roll: -0.7 }, trail: { from: [0.42, 0.68, 0.5], to: [0.6, 0.93, 0.5], width: 1.1 },
    chain: ['axe-chop', 'axe-hook', 'axe-cleave', 'axe-heave'], sprint: 'axe-rush', jump: 'axe-drop', preview: 'iron_axe' });
  // the chop: hoisted over the right shoulder (its head in view at the top), a loaded pause, then down through the target
  move('axe-chop', 'axe', { from: 'right', ends: 'low', w: 3.2, h: 1.0, s: 2.1, r: 10.0, peak: 0.46, tense: 0.08,
    stroke: sc([[0.62, 0.42, 0.52, 0.55, 0.95], [0.22, -0.18, 0.62, -0.42, 0.18], [-0.12, -1.0, 0.54, -0.42, -1.4]], { contact: 0.48 }),
    ready: [[0.45, [0.95, -0.25, 0.56], [1.05, 0.55]]],
    recover: [[0.25, [-0.2, -0.98, 0.56], [-0.42, -0.36]], [0.6, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 1.0, yaw: -0.2, roll: -0.5 }, dir: [-0.3, -1] });
  move('axe-hook', 'axe', { from: 'left', ends: 'right', w: 2.0, s: 2.1, r: 10.0, peak: 0.44,
    stroke: sc([[-0.72, -0.72, 0.55, -1.2, -0.62], [0.05, -0.28, 0.62, 0.0, 0.48], [0.9, 0.05, 0.55, 1.2, 0.5]], { contact: 0.46 }),
    recover: [[0.3, [0.98, -0.25, 0.56], [1.18, 0.38]], [0.68, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: -0.2, yaw: 0.5, roll: 0.6 }, dir: [1, 0.3] });
  // the cleave's momentum carries the axe low past the left hip before it is hauled back
  move('axe-cleave', 'axe', { from: 'right', ends: 'left', w: 2.8, s: 2.1, r: 10.0, peak: 0.44,
    stroke: sc([[0.85, 0.2, 0.54, 1.02, 0.9], [0.28, -0.3, 0.62, -0.55, 0.0], [-0.72, -0.92, 0.54, -1.2, -0.95]], { contact: 0.46 }),
    ready: [[0.5, [0.98, -0.4, 0.58], [1.2, 0.42]]],
    recover: [[0.2, [-0.85, -0.98, 0.52], [-1.25, -0.88]], [0.5, [-0.15, -0.98, 0.58], [-0.4, -0.35]], [0.8, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.6, yaw: -0.5, roll: -0.7 }, dir: [-0.85, -0.55] });
  move('axe-heave', 'axe', { from: 'any', ends: 'low', w: 3.8, h: 1.4, s: 2.1, r: 10.0, peak: 0.48, tense: 0.09,
    stroke: sc([[0.35, 0.5, 0.5, 0.18, 0.98], [0.12, -0.15, 0.6, -0.48, 0.1], [0.0, -1.02, 0.54, -0.25, -1.4]], { contact: 0.48 }),
    ready: [[0.5, [0.8, -0.1, 0.56], [0.86, 0.8]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 1.4, roll: -0.2, back: 0.02 }, dir: [0, -1] });
  move('axe-rush', 'axe', { from: 'any', ends: 'left', w: 2.0, s: 2.1, r: 10.0, peak: 0.44,
    stroke: sc([[0.9, 0.0, 0.52, 1.18, 0.62], [0.24, -0.3, 0.72, -0.55, -0.05], [-0.75, -0.8, 0.54, -1.2, -0.8]], { contact: 0.46 }),
    recover: at(REC_LOW_LEFT), kick: { pitch: 0.5, yaw: -0.6, roll: -0.6, back: -0.03 }, dir: [-0.9, -0.4] });
  move('axe-drop', 'axe', { from: 'any', ends: 'low', w: 2.6, h: 0.6, s: 1.9, r: 10.0, peak: 0.48,
    stroke: sc([[0.5, 0.45, 0.5, 0.4, 0.98], [0.18, -0.2, 0.62, -0.45, 0.1], [-0.05, -1.0, 0.54, -0.35, -1.38]], { contact: 0.48 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 1.3, roll: -0.2, back: 0.02 }, dir: [-0.1, -1] });

  // ================================================================ GREATAXE: everything bigger and slower, long loaded pauses
  klass('greataxe', { cat: 'heavy', period: 30, weight: 0.95, hitstop: 80, kickMs: 190, sparks: 'blunt', color: [1, 0.76, 0.55],
    kick: { pitch: 1.3, yaw: -0.4, roll: -0.8 }, trail: { from: [0.42, 0.68, 0.5], to: [0.6, 0.93, 0.5], width: 1.4 },
    chain: ['greataxe-overhead', 'greataxe-sweep', 'greataxe-return', 'greataxe-execute'], sprint: 'greataxe-charge', jump: 'greataxe-fall', preview: 'diamond_axe' });
  move('greataxe-overhead', 'greataxe', { from: 'any', ends: 'low', w: 4.8, h: 2.0, s: 2.4, r: 14.0, peak: 0.5, tense: 0.1,
    stroke: sc([[0.3, 0.55, 0.5, 0.1, 1.0], [0.1, -0.2, 0.6, -0.5, 0.02], [-0.02, -1.05, 0.54, -0.2, -1.45]], { contact: 0.49 }),
    ready: [[0.45, [0.9, -0.15, 0.55], [0.95, 0.7]]],
    recover: [[0.25, [0.1, -1.02, 0.56], [0.05, -0.4]], [0.6, [0.42, -0.98, 0.62], [0.6, -0.08]]],
    kick: { pitch: 1.8, roll: -0.2, back: 0.03 }, dir: [0, -1] });
  move('greataxe-sweep', 'greataxe', { from: 'right', ends: 'left', w: 4.2, h: 1.2, s: 2.6, r: 14.0, peak: 0.46, tense: 0.08,
    stroke: sc([[1.05, -0.2, 0.52, 1.25, 0.25], [0.15, -0.48, 0.66, -0.65, -0.28], [-0.95, -0.58, 0.5, -1.42, -0.78]], { contact: 0.46 }),
    ready: [[0.45, [1.05, -0.72, 0.55], [1.35, -0.2]]],
    recover: [[0.22, [-0.95, -0.9, 0.5], [-1.35, -1.0]], [0.55, [-0.12, -0.98, 0.58], [-0.38, -0.36]], [0.85, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.3, yaw: -1.1, roll: -0.8 }, dir: [-1, -0.1] });
  move('greataxe-return', 'greataxe', { from: 'left', ends: 'high', w: 2.8, s: 2.6, r: 14.0, peak: 0.46,
    stroke: sc([[-0.9, -0.78, 0.52, -1.35, -0.6], [0.02, -0.25, 0.64, -0.45, 0.55], [0.92, 0.35, 0.52, 1.08, 1.0]], { contact: 0.46 }),
    recover: at(REC_HIGH_RIGHT), kick: { pitch: -0.5, yaw: 0.8, roll: 0.9 }, dir: [0.85, 0.55] });
  // the executioner's blow: a long, still hold high on the right, then a steep diagonal through the target
  move('greataxe-execute', 'greataxe', { from: 'any', ends: 'low', w: 5.0, h: 2.4, s: 2.4, r: 14.0, peak: 0.5, tense: 0.11,
    stroke: sc([[0.72, 0.48, 0.5, 0.72, 1.0], [0.22, -0.22, 0.62, -0.42, 0.12], [-0.42, -1.0, 0.54, -0.8, -1.3]], { contact: 0.48 }),
    ready: [[0.45, [1.0, -0.2, 0.55], [1.12, 0.6]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 1.6, yaw: -0.4, roll: -0.6, back: 0.03 }, dir: [-0.5, -0.9] });
  move('greataxe-charge', 'greataxe', { from: 'any', ends: 'left', w: 3.0, s: 2.6, r: 14.0, peak: 0.46,
    stroke: sc([[1.05, -0.48, 0.5, 1.42, -0.2], [0.15, -0.42, 0.72, -0.65, -0.18], [-0.95, -0.38, 0.5, -1.4, -0.3]], { contact: 0.46 }),
    recover: at(REC_LEFT), kick: { pitch: 0.4, yaw: -1.0, roll: -0.7, back: -0.04 }, dir: [-1, 0] });
  move('greataxe-fall', 'greataxe', { from: 'any', ends: 'low', w: 3.6, h: 1.2, s: 2.2, r: 14.0, peak: 0.5,
    stroke: sc([[0.28, 0.55, 0.5, 0.08, 1.0], [0.08, -0.22, 0.6, -0.5, -0.02], [-0.02, -1.05, 0.54, -0.18, -1.45]], { contact: 0.49 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 2.0, back: 0.04 }, dir: [0, -1] });

  // ================================================================ HAMMER / MAUL / MACE: an overhead slam with a pause at the top
  klass('hammer', { cat: 'heavy', period: 24, weight: 1.0, hitstop: 85, kickMs: 200, sparks: 'blunt', color: [0.9, 0.86, 0.76],
    kick: { pitch: 1.6, yaw: 0, roll: -0.3, back: 0.03 }, trail: { from: [0.55, 0.7, 0.5], to: [0.85, 0.93, 0.5], width: 1.4, alpha: 0.8 },
    chain: ['hammer-slam', 'hammer-swing', 'hammer-upper', 'hammer-pound'], sprint: 'hammer-ram', jump: 'hammer-meteor', preview: 'jaspr_mutants/hulk_hammer' });
  move('hammer-slam', 'hammer', { from: 'any', ends: 'low', w: 4.6, h: 2.0, s: 2.1, r: 12.0, peak: 0.52, tense: 0.1,
    stroke: sc([[0.36, 0.52, 0.5, 0.18, 0.98], [0.12, -0.2, 0.6, -0.45, 0.05], [0.0, -1.0, 0.54, -0.2, -1.42]], { contact: 0.5 }),
    ready: [[0.45, [0.88, -0.15, 0.55], [0.92, 0.7]]],
    recover: [[0.25, [0.1, -1.02, 0.56], [0.05, -0.4]], [0.6, [0.42, -0.98, 0.62], [0.6, -0.08]]],
    kick: { pitch: 1.8, roll: -0.2, back: 0.03 }, dir: [0, -1] });
  move('hammer-swing', 'hammer', { from: 'right', ends: 'left', w: 3.8, h: 0.8, s: 2.5, r: 12.0, peak: 0.46, tense: 0.07,
    stroke: sc([[1.02, -0.25, 0.52, 1.22, 0.18], [0.2, -0.45, 0.66, -0.6, -0.22], [-0.92, -0.5, 0.52, -1.38, -0.62]], { contact: 0.47 }),
    ready: [[0.45, [1.02, -0.7, 0.56], [1.32, -0.18]]],
    recover: [[0.24, [-0.92, -0.88, 0.52], [-1.3, -0.95]], [0.55, [-0.15, -0.98, 0.58], [-0.4, -0.35]], [0.84, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.4, yaw: -1.0, roll: -0.7 }, dir: [-1, -0.1] });
  move('hammer-upper', 'hammer', { from: 'left', ends: 'high', w: 2.8, s: 2.3, r: 12.0, peak: 0.46,
    stroke: sc([[-0.55, -0.95, 0.52, -0.98, -1.15], [0.08, -0.25, 0.62, -0.12, 0.58], [0.65, 0.42, 0.52, 1.0, 0.98]], { contact: 0.46 }),
    recover: at(REC_HIGH_RIGHT), kick: { pitch: -1.0, yaw: 0.3, roll: 0.5 }, dir: [0.6, 0.8] });
  move('hammer-pound', 'hammer', { from: 'any', ends: 'low', w: 5.2, h: 2.6, s: 2.1, r: 12.0, peak: 0.54, tense: 0.12,
    stroke: sc([[0.22, 0.58, 0.5, 0.0, 1.02], [0.06, -0.25, 0.58, -0.5, -0.05], [-0.02, -1.08, 0.54, -0.1, -1.5]], { contact: 0.5 }),
    ready: [[0.4, [0.75, -0.05, 0.55], [0.72, 0.78]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 2.2, back: 0.05 }, dir: [0, -1] });
  move('hammer-ram', 'hammer', { from: 'any', ends: 'center', w: 3.5, s: 1.5, r: 12.0, peak: 0.5,
    stroke: thrust([[0.75, -0.9, 0.42], [0.32, -0.5, 0.78], [0.38, -0.56, 0.72]], { aim: [0, 0.4, 2.0], rollKeys: [90, 90, 90] }),
    recover: at(REC_THRUST), kick: { pitch: 0.4, back: 0.06 }, dir: [0, 0] });
  move('hammer-meteor', 'hammer', { from: 'any', ends: 'low', w: 3.6, h: 1.4, s: 1.9, r: 12.0, peak: 0.54,
    stroke: sc([[0.28, 0.58, 0.5, 0.05, 1.02], [0.08, -0.25, 0.58, -0.48, -0.02], [0.0, -1.08, 0.54, -0.12, -1.5]], { contact: 0.5 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 2.4, back: 0.05 }, dir: [0, -1] });

  // ================================================================ DAGGER: quick stabs, a reverse-grip slash, a twirl-and-stab finisher
  klass('dagger', { cat: 'light', period: 8, weight: 0.2, hitstop: 25, kickMs: 70, sparks: 'blade', color: [0.82, 1, 0.95],
    kick: { pitch: 0.15, yaw: -0.15, roll: -0.2 }, trail: { from: [0.5, 0.5, 0.5], to: [0.93, 0.93, 0.5], width: 0.75 },
    chain: ['dagger-stab', 'dagger-slash', 'dagger-flick', 'dagger-twirl'], sprint: 'dagger-lunge', jump: 'dagger-plunge', preview: 'iron_sword' });
  var DAGGER_THRUST = [[0.72, -0.85, 0.44], [0.36, -0.5, 0.72], [0.42, -0.56, 0.66]];
  move('dagger-stab', 'dagger', { from: 'any', ends: 'center', w: 1.8, s: 0.75, r: 4.8, peak: 0.5,
    stroke: thrust(DAGGER_THRUST, { aim: [0, 0, 2.2], rollKeys: [0, 0, 0] }), recover: at(REC_THRUST),
    kick: { pitch: 0.1, back: 0.015 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.65, 0.65, 0.5], to: [0.93, 0.93, 0.5], len: 0.6, width: 0.7 } });
  // reverse grip: the blade hangs below the fist while the fist sweeps across
  move('dagger-slash', 'dagger', { from: 'right', ends: 'left', w: 1.3, s: 1.0, r: 4.8, peak: 0.42,
    stroke: sc([[0.78, -0.1, 0.56, 0.95, -0.9], [0.22, -0.18, 0.6, -0.05, -0.92], [-0.45, -0.3, 0.58, -0.82, -1.05]], { contact: 0.45, reverse: true }),
    ready: [[0.5, [0.95, -0.55, 0.58], [1.15, -1.25], { reverse: true }]],
    recover: [[0.35, [-0.15, -0.65, 0.6], [-0.3, -1.3], { reverse: true }], [0.7, [0.55, -0.92, 0.64], [0.72, -0.02]]],
    kick: { pitch: 0.1, yaw: -0.25, roll: -0.2 }, dir: [-1, -0.2] });
  move('dagger-flick', 'dagger', { from: 'left', ends: 'right', w: 1.0, s: 1.0, r: 4.8, peak: 0.4,
    stroke: sc([[-0.45, -0.6, 0.58, -0.95, -0.45], [0.05, -0.38, 0.64, 0.05, 0.38], [0.68, -0.1, 0.58, 1.1, 0.42]], { contact: 0.45 }),
    recover: [[0.42, [0.75, -0.55, 0.6], [1.0, 0.18]]],
    kick: { pitch: -0.05, yaw: 0.25, roll: 0.2 }, dir: [1, 0.3] });
  // the finisher: a hard stab, then the dagger flipped once in the fingers on the way back
  move('dagger-twirl', 'dagger', { from: 'any', ends: 'center', w: 2.0, s: 0.75, r: 6.4, peak: 0.5,
    stroke: thrust([[0.66, -0.7, 0.46], [0.34, -0.45, 0.76], [0.4, -0.52, 0.7]], { aim: [0, 0, 2.2], rollKeys: [90, 90, 90] }),
    recover: [{ at: 0.12, grip: [0.55, -0.42, 0.6], aim: [0, 90], flat: [0, 0, 1], still: true }, { at: 0.7, grip: [0.55, -0.42, 0.6], aim: [0, 90], flat: [0, 0, 1], still: true }],
    twirl: { from: 0.13, to: 0.69, turns: 1, axis: 'flat' },
    kick: { pitch: 0.12, back: 0.02 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.65, 0.65, 0.5], to: [0.93, 0.93, 0.5], len: 0.6, width: 0.7 } });
  move('dagger-lunge', 'dagger', { from: 'any', ends: 'center', w: 1.8, s: 0.85, r: 5.0, peak: 0.5,
    stroke: thrust([[0.75, -0.88, 0.42], [0.32, -0.45, 0.84], [0.38, -0.52, 0.76]], { aim: [0, 0, 2.4], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.08, back: 0.03 }, sparks: 'pierce', dir: [0, 0], trail: { from: [0.65, 0.65, 0.5], to: [0.93, 0.93, 0.5], len: 0.6, width: 0.7 } });
  // a downward stab in reverse grip, from above
  move('dagger-plunge', 'dagger', { from: 'any', ends: 'low', w: 2.6, s: 0.85, r: 5.0, peak: 0.48,
    stroke: sc([[0.42, 0.55, 0.56, 0.32, -0.25], [0.22, -0.05, 0.62, 0.08, -0.82], [0.12, -0.55, 0.62, -0.05, -1.35]], { contact: 0.5, reverse: true }),
    ready: [[0.5, [0.75, 0.1, 0.58], [0.8, -0.65], { reverse: true }]],
    recover: [[0.45, [0.42, -0.9, 0.62], [0.58, -0.05]]],
    kick: { pitch: 0.35, roll: -0.1 }, sparks: 'pierce', dir: [0, -1] });

  // ================================================================ SPEAR / LANCE: thrusts, a quick jab, a sweeping butt-strike
  klass('spear', { cat: 'pole', period: 18, weight: 0.55, hitstop: 50, kickMs: 110, sparks: 'pierce', color: [1, 1, 0.9],
    kick: { pitch: 0.2, back: 0.04 }, trail: { from: [0.72, 0.72, 0.5], to: [0.93, 0.93, 0.5], len: 0.7 },
    chain: ['spear-thrust', 'spear-jab', 'spear-sweep', 'spear-impale'], sprint: 'spear-charge', jump: 'spear-skewer', preview: 'iron_sword' });
  move('spear-thrust', 'spear', { from: 'any', ends: 'center', w: 3.5, s: 1.2, r: 8.0, peak: 0.5,
    stroke: thrust([[0.88, -0.78, 0.36], [0.34, -0.45, 0.88], [0.4, -0.5, 0.8]], { aim: [0, 0, 3.2], rollKeys: [0, 0, 0] }),
    ready: [{ at: 0.5, grip: [1.02, -0.98, 0.34], aim: [-12, 6], flat: [1, 0, 0] }], recover: at(REC_THRUST),
    kick: { pitch: 0.15, back: 0.045 }, dir: [0, 0] });
  move('spear-jab', 'spear', { from: 'any', ends: 'center', w: 2.0, s: 0.85, r: 6.0, peak: 0.5,
    stroke: thrust([[0.66, -0.66, 0.46], [0.36, -0.48, 0.78], [0.41, -0.53, 0.72]], { aim: [0, -0.05, 3.0], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.1, back: 0.025 }, dir: [0, 0] });
  // the shaft swings across, butt end leading
  move('spear-sweep', 'spear', { from: 'right', ends: 'left', w: 2.4, s: 2.1, r: 8.0, peak: 0.44,
    stroke: sc([[0.9, -0.45, 0.54, 0.62, 0.45], [0.2, -0.5, 0.64, 0.8, 0.2], [-0.78, -0.6, 0.56, -0.15, 0.1]], { contact: 0.46, reverse: true }),
    recover: [[0.35, [-0.3, -0.92, 0.58], [-0.6, -0.3]], [0.7, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.1, yaw: -0.5, roll: -0.3 }, sparks: 'blunt', dir: [-1, -0.1], trail: { from: [0.2, 0.2, 0.5], to: [0.4, 0.4, 0.5], width: 0.9 } });
  // the finisher: two short feints and a deep drive
  move('spear-impale', 'spear', { from: 'any', ends: 'center', w: 2.4, s: 2.6, r: 8.0, peak: 0.66, contactKey: 3,
    stroke: thrust([[0.78, -0.72, 0.4], [0.42, -0.52, 0.72], [0.6, -0.62, 0.58], [0.33, -0.44, 0.94], [0.39, -0.5, 0.86]],
      { ks: [0, 0.22, 0.42, 0.75, 1], aim: [0, 0, 3.2], rollKeys: [0, 0, 0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.2, back: 0.06 }, dir: [0, 0] });
  move('spear-charge', 'spear', { from: 'any', ends: 'center', w: 2.5, s: 1.3, r: 8.0, peak: 0.5,
    stroke: thrust([[0.82, -0.82, 0.38], [0.32, -0.42, 0.98], [0.38, -0.48, 0.9]], { aim: [0, 0, 3.4], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.15, back: 0.07 }, dir: [0, 0] });
  move('spear-skewer', 'spear', { from: 'any', ends: 'low', w: 2.7, s: 1.2, r: 8.0, peak: 0.5,
    stroke: thrust([[0.55, 0.45, 0.46], [0.26, -0.18, 0.84], [0.3, -0.28, 0.78]], { aim: [0, -0.4, 2.4], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.6, back: 0.03 }, dir: [0, -1] });

  // ================================================================ GLAIVE / HALBERD: high chopping arcs, a pole thrust, a low whirl
  klass('glaive', { cat: 'pole', period: 22, weight: 0.75, hitstop: 60, kickMs: 150, sparks: 'blade', color: [0.95, 0.9, 1],
    kick: { pitch: 0.8, yaw: -0.5, roll: -0.6 }, trail: { from: [0.6, 0.6, 0.5], to: [0.93, 0.93, 0.5], width: 1.2 },
    chain: ['glaive-arc', 'glaive-return', 'glaive-thrust', 'glaive-whirl'], sprint: 'glaive-charge', jump: 'glaive-fall', preview: 'iron_hoe' });
  move('glaive-arc', 'glaive', { from: 'right', ends: 'left', w: 3.4, h: 0.8, s: 2.3, r: 11.0, peak: 0.46, tense: 0.07,
    stroke: sc([[0.88, 0.32, 0.5, 1.05, 1.0], [0.25, -0.25, 0.64, -0.55, 0.12], [-0.72, -0.9, 0.52, -1.2, -0.95]], { contact: 0.47 }),
    ready: [[0.45, [1.0, -0.35, 0.56], [1.2, 0.5]]],
    recover: [[0.22, [-0.85, -0.98, 0.52], [-1.25, -0.88]], [0.55, [-0.15, -0.98, 0.58], [-0.4, -0.35]], [0.84, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.8, yaw: -0.5, roll: -0.6 }, dir: [-0.8, -0.6] });
  move('glaive-return', 'glaive', { from: 'left', ends: 'high', w: 2.4, s: 2.3, r: 11.0, peak: 0.46,
    stroke: sc([[-0.82, -0.75, 0.52, -1.25, -0.55], [0.04, -0.25, 0.64, -0.42, 0.55], [0.9, 0.3, 0.52, 1.06, 1.0]], { contact: 0.46 }),
    recover: at(REC_HIGH_RIGHT), kick: { pitch: -0.4, yaw: 0.5, roll: 0.6 }, dir: [0.85, 0.55] });
  move('glaive-thrust', 'glaive', { from: 'any', ends: 'center', w: 3.1, s: 1.2, r: 10.0, peak: 0.5,
    stroke: thrust([[0.86, -0.8, 0.38], [0.34, -0.45, 0.88], [0.4, -0.5, 0.8]], { aim: [0, 0, 3.2], rollKeys: [90, 90, 90] }),
    ready: [{ at: 0.5, grip: [1.0, -0.98, 0.36], aim: [-12, 6], flat: [0, 1, 0] }], recover: at(REC_THRUST),
    kick: { pitch: 0.2, back: 0.05 }, sparks: 'pierce', dir: [0, 0] });
  // a low whirl at the legs
  move('glaive-whirl', 'glaive', { from: 'any', ends: 'left', w: 3.0, s: 2.6, r: 11.0, peak: 0.46,
    stroke: sc([[1.05, -0.62, 0.5, 1.42, -0.42], [0.18, -0.62, 0.66, -0.62, -0.5], [-0.95, -0.66, 0.5, -1.4, -0.82]], { contact: 0.46 }),
    ready: [[0.5, [1.05, -0.9, 0.52], [1.4, -0.55]]], recover: at(REC_LEFT),
    kick: { pitch: 0.2, yaw: -0.9, roll: -0.6 }, dir: [-1, 0] });
  move('glaive-charge', 'glaive', { from: 'any', ends: 'center', w: 2.5, s: 1.3, r: 10.0, peak: 0.5,
    stroke: thrust([[0.84, -0.84, 0.38], [0.32, -0.42, 0.98], [0.38, -0.48, 0.9]], { aim: [0, 0, 3.4], rollKeys: [90, 90, 90] }),
    recover: at(REC_THRUST), kick: { pitch: 0.15, back: 0.07 }, sparks: 'pierce', dir: [0, 0] });
  move('glaive-fall', 'glaive', { from: 'any', ends: 'low', w: 3.0, h: 0.8, s: 2.1, r: 11.0, peak: 0.48,
    stroke: sc([[0.4, 0.5, 0.5, 0.22, 0.98], [0.14, -0.18, 0.62, -0.48, 0.08], [0.0, -1.0, 0.54, -0.28, -1.38]], { contact: 0.48 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 1.2, back: 0.02 }, dir: [0, -1] });

  // ================================================================ SCYTHE / SICKLE: wide reaping arcs that pull back toward you
  klass('scythe', { cat: 'pole', period: 22, weight: 0.7, hitstop: 55, kickMs: 140, sparks: 'blade', color: [0.78, 0.96, 0.82],
    kick: { pitch: 0.3, yaw: -0.8, roll: -0.6 }, trail: { from: [0.62, 0.86, 0.5], to: [0.4, 0.94, 0.5], width: 1.2, len: 1.2 },
    chain: ['scythe-reap', 'scythe-return', 'scythe-high', 'scythe-harvest'], sprint: 'scythe-sweep', jump: 'scythe-fall', preview: 'iron_hoe' });
  // the reap: raised at the right, swept round and down through the target, the blade drawn back in toward you
  move('scythe-reap', 'scythe', { from: 'right', ends: 'left', w: 3.2, s: 2.5, r: 11.0, peak: 0.44,
    stroke: sc([[0.95, 0.12, 0.54, 1.25, 0.72], [0.22, -0.38, 0.72, -0.55, -0.05], [-0.58, -0.5, 0.48, -1.2, -0.22]], { contact: 0.46 }),
    ready: [[0.5, [1.02, -0.45, 0.56], [1.3, 0.2]]],
    recover: [[0.25, [-0.65, -0.85, 0.48], [-1.2, -0.6]], [0.58, [-0.05, -0.98, 0.58], [-0.3, -0.35]], [0.85, [0.45, -0.98, 0.62], [0.62, -0.06]]],
    kick: { pitch: 0.2, yaw: -0.8, roll: -0.5 }, dir: [-1, -0.3] });
  move('scythe-return', 'scythe', { from: 'left', ends: 'right', w: 2.4, s: 2.5, r: 11.0, peak: 0.44,
    stroke: sc([[-0.72, -0.55, 0.5, -1.25, -0.32], [0.04, -0.38, 0.7, 0.72, -0.05], [0.88, -0.15, 0.5, 1.1, 0.45]], { contact: 0.46 }),
    recover: [[0.3, [0.98, -0.5, 0.54], [1.2, 0.15]], [0.72, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: 0.1, yaw: 0.8, roll: 0.5 }, dir: [1, 0.2] });
  move('scythe-high', 'scythe', { from: 'right', ends: 'low', w: 3.0, h: 0.6, s: 2.3, r: 11.0, peak: 0.46,
    stroke: sc([[0.75, 0.48, 0.5, 0.88, 1.02], [0.22, -0.22, 0.66, -0.52, 0.05], [-0.38, -0.98, 0.54, -0.82, -1.25]], { contact: 0.47 }),
    ready: [[0.45, [1.0, -0.25, 0.55], [1.15, 0.55]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 0.9, yaw: -0.3, roll: -0.5 }, dir: [-0.5, -0.9] });
  // the finisher: the reap continues into a hooking pull, the blade drawn back toward you through the target
  move('scythe-harvest', 'scythe', { from: 'any', ends: 'left', w: 3.4, h: 0.8, s: 2.9, r: 11.0, peak: 0.4, contactKey: 1,
    stroke: sc([[1.0, 0.05, 0.54, 1.3, 0.62], [0.18, -0.4, 0.78, -0.55, -0.08], [-0.32, -0.55, 0.62, -0.95, -0.42], [-0.65, -0.45, 0.46, -1.3, -0.15]], { ks: [0, 0.4, 0.7, 1] }),
    ready: [[0.45, [1.05, -0.48, 0.56], [1.35, 0.15]]],
    recover: [[0.3, [-0.55, -0.92, 0.5], [-1.1, -0.65]], [0.7, [0.42, -0.98, 0.62], [0.6, -0.08]]],
    kick: { pitch: 0.2, yaw: -0.7, roll: -0.6, back: -0.03 }, dir: [-1, -0.2] });
  move('scythe-sweep', 'scythe', { from: 'any', ends: 'left', w: 2.4, s: 2.5, r: 11.0, peak: 0.44,
    stroke: sc([[1.02, -0.32, 0.52, 1.38, 0.1], [0.18, -0.5, 0.74, -0.62, -0.3], [-0.85, -0.58, 0.48, -1.38, -0.55]], { contact: 0.46 }),
    recover: at(REC_LEFT), kick: { pitch: 0.15, yaw: -0.9, roll: -0.6, back: -0.03 }, dir: [-1, -0.1] });
  move('scythe-fall', 'scythe', { from: 'any', ends: 'low', w: 2.8, h: 0.8, s: 2.1, r: 11.0, peak: 0.48,
    stroke: sc([[0.5, 0.48, 0.5, 0.4, 0.98], [0.16, -0.2, 0.66, -0.48, 0.05], [-0.18, -1.0, 0.54, -0.55, -1.32]], { contact: 0.48 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 1.1, roll: -0.3 }, dir: [-0.2, -1] });

  // ================================================================ FLAIL / WHIP: whirled overhead, lashed across, cracked down
  klass('flail', { cat: 'heavy', period: 22, weight: 0.7, hitstop: 55, kickMs: 150, sparks: 'blunt', color: [0.92, 0.82, 0.82],
    kick: { pitch: 0.8, yaw: -0.4, roll: -0.5 }, trail: { from: [0.62, 0.62, 0.5], to: [0.93, 0.93, 0.5], width: 1.0, len: 1.3 },
    chain: ['flail-whirl', 'flail-lash', 'flail-crack'], sprint: 'flail-lash', jump: 'flail-crack', preview: 'iron_axe' });
  // whirled once overhead (in view, in the plane facing you), then brought down
  move('flail-whirl', 'flail', { from: 'any', ends: 'low', w: 4.8, s: 2.1, r: 10.0, peak: 0.5,
    stroke: sc([[0.4, 0.45, 0.52, 0.28, 0.98], [0.16, -0.2, 0.62, -0.48, 0.05], [0.0, -0.98, 0.56, -0.3, -1.35]], { contact: 0.48 }),
    ready: [{ at: 0.2, grip: [0.45, 0.2, 0.62], aim: [0, 90], flat: [0, 0, 1], always: true, still: true }, { at: 0.82, grip: [0.45, 0.2, 0.62], aim: [0, 90], flat: [0, 0, 1], always: true, still: true }],
    windTwirl: { from: 0.21, to: 0.81, turns: 1, axis: 'flat' }, recover: at(REC_LOW_CENTER),
    kick: { pitch: 1.2, roll: -0.2 }, dir: [-0.1, -1] });
  // a lash: the head trails the hand and whips through late
  move('flail-lash', 'flail', { from: 'any', ends: 'left', w: 2.6, s: 2.3, r: 10.0, peak: 0.55,
    stroke: sc([[1.0, -0.25, 0.52, 1.0, 0.45], [0.42, -0.42, 0.64, 0.3, 0.3], [-0.85, -0.52, 0.52, -1.35, -0.6]], { ks: [0, 0.38, 1], contact: 0.38 }),
    recover: at(REC_LEFT), kick: { pitch: 0.2, yaw: -0.8, roll: -0.5 }, dir: [-1, -0.1] });
  // a crack: a sharp downward stroke that snaps back a little at the end
  move('flail-crack', 'flail', { from: 'any', ends: 'center', w: 2.8, s: 2.1, r: 10.0, peak: 0.42, contactKey: 1,
    stroke: sc([[0.55, 0.42, 0.52, 0.5, 0.98], [0.18, -0.28, 0.64, -0.45, -0.05], [0.12, -0.72, 0.6, -0.3, -1.1], [0.18, -0.55, 0.6, -0.35, -0.85]], { ks: [0, 0.45, 0.75, 1] }),
    recover: [[0.4, [0.42, -0.95, 0.62], [0.6, -0.08]]], kick: { pitch: 0.9, roll: -0.3 }, dir: [-0.2, -1] });

  // ================================================================ STAFF / SCEPTRE / WAND: bludgeoning swings, a poke, a twirl strike
  klass('staff', { cat: 'pole', period: 15, weight: 0.5, hitstop: 45, kickMs: 120, sparks: 'magic', color: [0.82, 0.76, 1],
    kick: { pitch: 0.5, yaw: -0.4, roll: -0.5 }, trail: { from: [0.62, 0.62, 0.5], to: [0.93, 0.93, 0.5], alpha: 1.1 },
    chain: ['staff-swing', 'staff-rise', 'staff-poke', 'staff-twirl'], sprint: 'staff-poke', jump: 'staff-fall', preview: 'blaze_rod' });
  move('staff-swing', 'staff', { from: 'right', ends: 'left', w: 2.6, s: 2.1, r: 8.0, peak: 0.44,
    stroke: sc([[0.88, 0.0, 0.54, 1.15, 0.62], [0.24, -0.36, 0.64, -0.55, -0.1], [-0.62, -0.75, 0.54, -1.15, -0.7]], { contact: 0.46 }),
    ready: [[0.5, [1.0, -0.5, 0.58], [1.25, 0.25]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 0.4, yaw: -0.5, roll: -0.5 }, dir: [-0.9, -0.4] });
  move('staff-rise', 'staff', { from: 'left', ends: 'high', w: 2.0, s: 2.1, r: 8.0, peak: 0.44,
    stroke: sc(BACK, { contact: 0.46 }), recover: at(REC_HIGH_RIGHT),
    kick: { pitch: -0.4, yaw: 0.4, roll: 0.5 }, dir: [0.85, 0.55] });
  move('staff-poke', 'staff', { from: 'any', ends: 'center', w: 2.5, s: 1.0, r: 7.0, peak: 0.5,
    stroke: thrust([[0.75, -0.8, 0.44], [0.34, -0.46, 0.84], [0.4, -0.52, 0.77]], { aim: [0, 0.05, 2.8], rollKeys: [0, 0, 0] }),
    recover: at(REC_THRUST), kick: { pitch: 0.15, back: 0.035 }, dir: [0, 0] });
  // the finisher twirls the staff once in front of you before bringing it down
  move('staff-twirl', 'staff', { from: 'any', ends: 'low', w: 4.6, s: 1.9, r: 8.0, peak: 0.48,
    stroke: sc([[0.4, 0.42, 0.54, 0.28, 0.98], [0.15, -0.16, 0.62, -0.46, 0.08], [0.0, -0.9, 0.56, -0.3, -1.28]], { contact: 0.48 }),
    ready: [{ at: 0.17, grip: [0.45, 0.15, 0.64], aim: [0, 90], flat: [0, 0, 1], always: true, still: true }, { at: 0.84, grip: [0.45, 0.15, 0.64], aim: [0, 90], flat: [0, 0, 1], always: true, still: true }],
    windTwirl: { from: 0.18, to: 0.83, turns: 1, axis: 'flat' }, recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.9, roll: -0.2 }, dir: [-0.1, -1] });
  move('staff-fall', 'staff', { from: 'any', ends: 'low', w: 2.4, h: 0.4, s: 1.9, r: 8.0, peak: 0.48,
    stroke: sc(OVER, { contact: 0.48 }), ready: [[0.5, [0.85, -0.2, 0.58], [0.95, 0.68]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.9, roll: -0.15 }, dir: [-0.1, -1] });

  // ================================================================ CLAW (Endersoul Hand): rakes and swipes of the resting hand
  // Swipes turn the resting item about view axes, so they read right however the hand model is drawn.
  klass('claw', { cat: 'light', period: 12.5, weight: 0.3, hitstop: 35, kickMs: 90, sparks: 'claw', color: [0.8, 0.55, 1],
    kick: { pitch: 0.3, yaw: -0.3, roll: -0.4 }, trail: false,
    chain: ['claw-rake', 'claw-backrake', 'claw-swipe', 'claw-uppercut'], sprint: 'claw-lunge', jump: 'claw-drop', preview: 'jaspr_mutants/endersoul_hand' });
  var REST_GRIP = [0.689, -1.136, 0.736];
  move('claw-rake', 'claw', { from: 'right', ends: 'left', w: 1.8, s: 1.5, r: 6.6, peak: 0.42,
    stroke: swipe([[0.82, -0.6, 0.74], [0.2, -0.9, 0.78], [-0.55, -1.25, 0.76]], [[0, 0, 1, 22], [0, 0, 1, 2], [0, 0, 1, -24]], { contact: 0.45 }),
    ready: [{ at: 0.5, grip: [0.92, -0.75, 0.76], rot: [0, 0, 1, 16] }],
    recover: [{ at: 0.4, grip: [0.15, -1.3, 0.78], rot: [0, 0, 1, -10] }],
    kick: { pitch: 0.4, yaw: -0.3, roll: -0.4 }, dir: [-0.8, -0.6] });
  move('claw-backrake', 'claw', { from: 'left', ends: 'right', w: 1.4, s: 1.5, r: 6.6, peak: 0.42,
    stroke: swipe([[-0.5, -1.2, 0.76], [0.15, -0.88, 0.78], [0.85, -0.62, 0.74]], [[0, 0, 1, -24], [0, 0, 1, 0], [0, 0, 1, 20]], { contact: 0.45 }),
    recover: [{ at: 0.4, grip: [0.85, -0.95, 0.76], rot: [0, 0, 1, 8] }],
    kick: { pitch: -0.2, yaw: 0.3, roll: 0.4 }, dir: [0.8, 0.6] });
  move('claw-swipe', 'claw', { from: 'right', ends: 'left', w: 1.6, s: 1.5, r: 6.6, peak: 0.42,
    stroke: swipe([[0.9, -0.8, 0.74], [0.15, -0.95, 0.78], [-0.65, -1.05, 0.76]], [[0, 1, 0, 22], [0, 1, 0, 0], [0, 1, 0, -26]], { contact: 0.45 }),
    recover: [{ at: 0.4, grip: [0.0, -1.25, 0.78], rot: [0, 1, 0, -10] }],
    kick: { pitch: 0.1, yaw: -0.5, roll: -0.2 }, dir: [-1, -0.1] });
  move('claw-uppercut', 'claw', { from: 'any', ends: 'high', w: 1.9, s: 1.5, r: 6.6, peak: 0.42,
    stroke: swipe([[0.55, -1.35, 0.74], [0.25, -0.75, 0.78], [0.2, -0.3, 0.76]], [[1, 0, 0, -18], [1, 0, 0, 6], [1, 0, 0, 24]], { contact: 0.45 }),
    recover: [{ at: 0.4, grip: [0.55, -0.85, 0.76], rot: [1, 0, 0, 10] }],
    kick: { pitch: -0.6, roll: 0.1 }, dir: [0, 1] });
  move('claw-lunge', 'claw', { from: 'any', ends: 'center', w: 1.8, s: 1.3, r: 6.6, peak: 0.5,
    stroke: swipe([[0.78, -1.15, 0.7], [0.32, -0.75, 0.98], [0.38, -0.82, 0.92]], [[1, 0, 0, -12], [1, 0, 0, 14], [1, 0, 0, 12]], { ks: [0, 0.68, 1] }),
    recover: [{ at: 0.4, grip: [0.6, -1.0, 0.78], rot: [1, 0, 0, 6] }],
    kick: { pitch: 0.15, back: 0.04 }, dir: [0, 0] });
  move('claw-drop', 'claw', { from: 'any', ends: 'low', w: 1.8, s: 1.4, r: 6.6, peak: 0.45,
    stroke: swipe([[0.45, -0.2, 0.76], [0.25, -0.8, 0.8], [0.1, -1.35, 0.76]], [[1, 0, 0, 28], [1, 0, 0, 0], [1, 0, 0, -20]], { contact: 0.45 }),
    recover: [{ at: 0.4, grip: [0.45, -1.35, 0.78], rot: [1, 0, 0, -8] }],
    kick: { pitch: 0.7 }, dir: [0, -1] });

  // ================================================================ ODD OBJECTS (keys, valve keys): clubbing swipes
  klass('odd', { cat: 'tool', period: 14, weight: 0.45, hitstop: 40, kickMs: 110, sparks: 'blunt', color: [1, 0.95, 0.85],
    kick: { pitch: 0.4, yaw: -0.3, roll: -0.3 }, trail: false,
    chain: ['odd-club', 'odd-backclub', 'odd-jab'], sprint: 'odd-jab', jump: 'odd-bonk', preview: 'stick' });
  move('odd-club', 'odd', { from: 'right', ends: 'left', w: 2.2, s: 1.8, r: 7.0, peak: 0.44,
    stroke: swipe([[0.85, -0.55, 0.74], [0.22, -0.9, 0.78], [-0.5, -1.2, 0.76]], [[0, 0, 1, 26], [0, 0, 1, 0], [0, 0, 1, -28]], { contact: 0.46 }),
    ready: [{ at: 0.5, grip: [0.95, -0.72, 0.76], rot: [0, 0, 1, 18] }],
    recover: [{ at: 0.4, grip: [0.15, -1.3, 0.78], rot: [0, 0, 1, -12] }],
    kick: { pitch: 0.4, yaw: -0.3, roll: -0.35 }, dir: [-0.8, -0.6] });
  move('odd-backclub', 'odd', { from: 'left', ends: 'right', w: 1.6, s: 1.8, r: 7.0, peak: 0.44,
    stroke: swipe([[-0.45, -1.15, 0.76], [0.2, -0.85, 0.78], [0.88, -0.6, 0.74]], [[0, 0, 1, -26], [0, 0, 1, 0], [0, 0, 1, 24]], { contact: 0.46 }),
    recover: [{ at: 0.4, grip: [0.85, -0.95, 0.76], rot: [0, 0, 1, 8] }],
    kick: { pitch: -0.2, yaw: 0.3, roll: 0.35 }, dir: [0.8, 0.6] });
  move('odd-jab', 'odd', { from: 'any', ends: 'center', w: 1.9, s: 1.3, r: 7.0, peak: 0.5,
    stroke: swipe([[0.78, -1.15, 0.7], [0.32, -0.78, 0.96], [0.38, -0.85, 0.9]], [[1, 0, 0, -12], [1, 0, 0, 14], [1, 0, 0, 12]], { ks: [0, 0.68, 1] }),
    recover: [{ at: 0.4, grip: [0.6, -1.0, 0.78], rot: [1, 0, 0, 6] }],
    kick: { pitch: 0.15, back: 0.03 }, dir: [0, 0] });
  move('odd-bonk', 'odd', { from: 'any', ends: 'low', w: 2.2, h: 0.4, s: 1.7, r: 7.0, peak: 0.46,
    stroke: swipe([[0.45, -0.15, 0.76], [0.25, -0.8, 0.8], [0.1, -1.35, 0.76]], [[1, 0, 0, 32], [1, 0, 0, 0], [1, 0, 0, -22]], { contact: 0.46 }),
    recover: [{ at: 0.4, grip: [0.45, -1.35, 0.78], rot: [1, 0, 0, -8] }],
    kick: { pitch: 0.8 }, dir: [0, -1] });

  // ================================================================ PICKAXE: an overhand hack with the point leading, a swipe, a backhand
  klass('pickaxe', { cat: 'tool', period: 16.7, weight: 0.5, hitstop: 45, kickMs: 120, sparks: 'blunt', color: [0.9, 0.92, 1],
    kick: { pitch: 0.8, yaw: -0.2, roll: -0.4 }, trail: { from: [0.62, 0.8, 0.5], to: [0.28, 0.94, 0.5], width: 0.9 },
    chain: ['pick-hack', 'pick-back', 'pick-swipe'], sprint: 'pick-swipe', jump: 'pick-hack', preview: 'iron_pickaxe' });
  move('pick-hack', 'pickaxe', { from: 'any', ends: 'low', w: 2.8, h: 0.4, s: 1.9, r: 8.4, peak: 0.46,
    stroke: sc([[0.5, 0.45, 0.5, 0.45, 0.98], [0.2, -0.2, 0.62, -0.42, 0.12], [0.02, -0.95, 0.54, -0.25, -1.32]], { contact: 0.48 }),
    ready: [[0.5, [0.9, -0.25, 0.56], [1.0, 0.6]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.9, roll: -0.3 }, dir: [-0.15, -1] });
  move('pick-swipe', 'pickaxe', { from: 'right', ends: 'left', w: 2.2, s: 1.9, r: 8.4, peak: 0.44,
    stroke: sc(FORE, { contact: 0.46 }), ready: [[0.5, [0.95, -0.4, 0.6], [1.18, 0.45]]], recover: at(REC_LOW_LEFT),
    kick: { pitch: 0.4, yaw: -0.3, roll: -0.4 }, dir: [-0.85, -0.55] });
  move('pick-back', 'pickaxe', { from: 'left', ends: 'right', w: 1.8, s: 1.9, r: 8.4, peak: 0.44,
    stroke: sc([[-0.52, -0.62, 0.56, -1.0, -0.42], [0.05, -0.3, 0.62, 0.15, 0.48], [0.75, -0.08, 0.56, 1.12, 0.45]], { contact: 0.46 }),
    recover: [[0.35, [0.92, -0.4, 0.56], [1.15, 0.28]], [0.72, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: -0.1, yaw: 0.35, roll: 0.4 }, dir: [0.9, 0.3] });

  // ================================================================ SHOVEL: a jab with the blade, a flat bash, a rising scoop
  klass('shovel', { cat: 'tool', period: 20, weight: 0.5, hitstop: 45, kickMs: 120, sparks: 'blunt', color: [0.96, 0.9, 0.8],
    kick: { pitch: 0.3, back: 0.03 }, trail: { from: [0.68, 0.72, 0.5], to: [0.9, 0.92, 0.5], width: 1.0 },
    chain: ['shovel-jab', 'shovel-bash', 'shovel-scoop'], sprint: 'shovel-jab', jump: 'shovel-slam', preview: 'iron_shovel' });
  move('shovel-jab', 'shovel', { from: 'any', ends: 'center', w: 2.9, s: 1.1, r: 9.0, peak: 0.5,
    stroke: thrust([[0.78, -0.85, 0.42], [0.34, -0.5, 0.82], [0.4, -0.56, 0.75]], { aim: [0, -0.1, 2.4], rollKeys: [90, 90, 90] }),
    recover: at(REC_THRUST), kick: { pitch: 0.2, back: 0.04 }, dir: [0, 0] });
  // the flat of the blade leads: a bash, not a cut
  move('shovel-bash', 'shovel', { from: 'right', ends: 'left', w: 2.4, s: 1.9, r: 9.0, peak: 0.44,
    stroke: sc(LEVEL, { contact: 0.46, roll: 90 }), ready: [[0.55, [1.02, -0.62, 0.58], [1.28, -0.18]]], recover: at(REC_LEFT),
    kick: { pitch: 0.2, yaw: -0.5, roll: -0.3 }, dir: [-1, -0.1] });
  move('shovel-scoop', 'shovel', { from: 'any', ends: 'high', w: 2.2, s: 1.9, r: 9.0, peak: 0.44,
    stroke: sc([[0.62, -1.0, 0.52, 0.95, -1.35], [0.18, -0.35, 0.66, -0.15, 0.45], [-0.12, 0.3, 0.56, -0.5, 0.98]], { contact: 0.46 }),
    recover: [[0.35, [0.25, -0.2, 0.6], [0.3, 0.6]], [0.72, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: -0.5, roll: 0.2 }, dir: [-0.2, 1] });
  move('shovel-slam', 'shovel', { from: 'any', ends: 'low', w: 2.6, h: 0.5, s: 1.9, r: 9.0, peak: 0.46,
    stroke: sc(OVER, { contact: 0.48, roll: 90 }), ready: [[0.5, [0.85, -0.2, 0.58], [0.95, 0.68]]], recover: at(REC_LOW_CENTER),
    kick: { pitch: 0.9, back: 0.02 }, dir: [-0.1, -1] });

  // ================================================================ HOE: a hooking reap, an overhand chop, a backhand
  klass('hoe', { cat: 'tool', period: 12, weight: 0.45, hitstop: 40, kickMs: 110, sparks: 'blade', color: [0.86, 1, 0.82],
    kick: { pitch: 0.3, yaw: -0.5, roll: -0.4 }, trail: { from: [0.62, 0.88, 0.5], to: [0.4, 0.93, 0.5], width: 0.9 },
    chain: ['hoe-hook', 'hoe-back', 'hoe-chop'], sprint: 'hoe-hook', jump: 'hoe-chop', preview: 'iron_hoe' });
  move('hoe-hook', 'hoe', { from: 'right', ends: 'left', w: 2.2, s: 1.9, r: 7.2, peak: 0.44,
    stroke: sc([[0.92, 0.05, 0.54, 1.2, 0.62], [0.25, -0.38, 0.7, -0.5, -0.05], [-0.42, -0.55, 0.5, -1.0, -0.3]], { contact: 0.46 }),
    ready: [[0.5, [1.0, -0.45, 0.56], [1.25, 0.2]]], recover: at(REC_LEFT),
    kick: { pitch: 0.2, yaw: -0.5, roll: -0.4 }, dir: [-1, -0.3] });
  move('hoe-back', 'hoe', { from: 'left', ends: 'right', w: 1.8, s: 1.9, r: 7.2, peak: 0.44,
    stroke: sc([[-0.62, -0.55, 0.52, -1.15, -0.32], [0.04, -0.38, 0.68, 0.7, -0.08], [0.84, -0.15, 0.52, 1.1, 0.42]], { contact: 0.46 }),
    recover: [[0.35, [0.95, -0.5, 0.54], [1.18, 0.15]], [0.72, [0.6, -0.92, 0.66], [0.84, 0.0]]],
    kick: { pitch: 0.1, yaw: 0.5, roll: 0.4 }, dir: [1, 0.2] });
  move('hoe-chop', 'hoe', { from: 'any', ends: 'low', w: 2.4, h: 0.3, s: 1.9, r: 7.2, peak: 0.46,
    stroke: sc([[0.48, 0.45, 0.5, 0.42, 0.98], [0.2, -0.2, 0.62, -0.42, 0.12], [0.02, -0.95, 0.54, -0.25, -1.32]], { contact: 0.48 }),
    recover: at(REC_LOW_CENTER), kick: { pitch: 0.7, roll: -0.3 }, dir: [-0.15, -1] });

  return { moves: moves, classes: classes, REST_GRIP: REST_GRIP, sc: sc, via: via };
}(typeof JasprMeleeCore !== 'undefined' ? JasprMeleeCore : require('./melee-core.js')));
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeMoves;
