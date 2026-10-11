'use strict';
// First-person melee motion v5 (client-mods/melee, loaded exactly as the client region concatenates it): every move
// leaves and returns to the vanilla rest pose, moves continuously, keeps the weapon off the camera and mostly on screen,
// strikes fastest, lands its contact in time and recovers within the weapon's cooldown; the left hand mirrors the right;
// combos chain from where the blade is; the combat feed drives impacts, hit-stop, upgrades and refusals; every dual
// pair is defined; items map to their classes; everything is deterministic.
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../client-mods/melee/melee-load.cjs').load();
const S = require('../scripts/melee-preview-sim.cjs');
const { metrics } = require('../scripts/melee-preview-metrics.cjs');
const M = L.JasprMeleeMotion, C = L.JasprMeleeCore, T = M._test, D = L.JasprMeleeDual, IT = L.JasprMeleeItems;
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
const clipOf = (name, o) => T.makeClip(M.moves[name], M.classes[M.moves[name].cls], Object.assign({ tick: 0 }, o || {}));
const flourish = name => !!(M.moves[name].twirl || M.moves[name].windTwirl);
// a pop: one frame-to-frame step much larger than both of its neighbours (fast but smooth motion is not a pop)
function pops(frames, hi) {
  const steps = [];
  for (let i = 1; i < frames.length; i++) {
    const a = frames[i - 1].poses[hi], b = frames[i].poses[hi];
    steps.push(a && b ? Math.hypot(a.tx - b.tx, a.ty - b.ty, a.tz - b.tz) + Math.abs(a.rotation.angle - b.rotation.angle) / 400 : null);
  }
  const out = [];
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i]; if (s === null) continue;
    const n = Math.max(steps[i - 1] || 0, steps[i + 1] || 0);
    if (s > 3 * n + 0.03) out.push(frames[i + 1].t + ':' + s.toFixed(3) + '/' + n.toFixed(3));
  }
  return out;
}
// the sequence of clip names a hand played (a gap at rest separates two plays of the same move)
function played(frames, hi) {
  const out = []; let prev = null;
  for (const f of frames) { const n = f.names[hi]; if (n && n !== prev) out.push(n); prev = n; }
  return out;
}

test('every class has a chain, a sprint and a jump move of its own, and every move belongs to a class', () => {
  const names = Object.keys(M.classes);
  assert.equal(names.length, 18, 'eighteen weapon classes');
  for (const c of names) {
    const k = M.classes[c];
    assert.ok(k.chain.length >= 3 && k.chain.length <= 5, c + ' combo of 3-5 strokes');
    for (const n of k.chain.concat([k.sprint, k.jump])) { assert.ok(M.moves[n], c + ': ' + n); assert.equal(M.moves[n].cls, c, n + ' belongs to ' + c); }
    assert.ok(k.period > 0 && k.hitstop > 0 && k.hitstop <= 100 && k.kick, c + ' impact feel');
  }
  for (const n of M.clipNames) assert.ok(M.classes[M.moves[n].cls], n);
  // different weapons, different motion: no two classes share their opening stroke
  const openers = names.map(c => M.classes[c].chain[0]);
  assert.equal(new Set(openers).size, openers.length);
});

test('every move starts and ends exactly on the vanilla first-person pose', () => {
  for (const name of M.clipNames) for (const p of [0, 0.99999]) {
    const pose = M.pose(name, p, true);
    assert.ok(Math.hypot(pose.tx, pose.ty, pose.tz) < 2e-3, `${name} @${p} offset ${pose.tx},${pose.ty},${pose.tz}`);
    assert.ok(Math.abs(pose.rotation.angle) < 0.5 || Math.abs(pose.rotation.angle - 360) < 0.5, `${name} @${p} angle ${pose.rotation.angle}`);
  }
});

test('motion is continuous and finite, phases meet at rest points, and the stroke is the fastest part', () => {
  for (const name of M.clipNames) {
    const clip = clipOf(name), n = 500, top = { windup: 0, hold: 0, stroke: 0, recover: 0, rest: 0 };
    let prev = T.evalClip(clip, 0);
    for (let i = 1; i <= n; i++) {
      const t = clip.dur * i / n * 0.9999, pose = T.evalClip(clip, t);
      for (const v of [...pose.g, ...pose.q]) assert.ok(Number.isFinite(v), `${name} finite at ${t}`);
      const move = dist(prev.g, pose.g), turn = C.qangle(prev.q, pose.q);
      assert.ok(move < 0.06, `${name} grip jumps ${move.toFixed(3)} at ${t.toFixed(2)}`);
      const turnLimit = flourish(name) ? 280 * C.DEG * clip.dur / n : 0.2;   // a spin stays readable (no strobing at 60 fps)
      assert.ok(turn < turnLimit, `${name} blade flips ${(turn / C.DEG).toFixed(1)} degrees at ${t.toFixed(2)}`);
      // cuts are judged by the tip, thrusts by the drive of the hand
      const tip = C.itemPoint(pose, [0.93, 0.93, 0.5]), ptip = C.itemPoint(prev, [0.93, 0.93, 0.5]);
      top[pose.phase] = Math.max(top[pose.phase], M.moves[name].stroke.kind === 'thrust' ? move : Math.max(move, dist(tip, ptip)));
      prev = pose;
    }
    if (!flourish(name)) assert.ok(top.stroke > top.windup && top.stroke > top.recover, `${name}: fastest in the stroke ${JSON.stringify(top)}`);
    // the reversal points: the top of the wind-up (or the end of a loaded pause) and the end of the stroke
    for (const at of [clip.tw + clip.th, clip.strokeEnd]) {
      const h = 0.004, a = T.evalClip(clip, at - h), b = T.evalClip(clip, at + h);
      assert.ok(dist(a.g, b.g) / (2 * h) < 0.06, `${name} grip speed at ${at.toFixed(2)}`);
      assert.ok(C.qangle(a.q, b.q) / (2 * h) < 0.1, `${name} turn rate at ${at.toFixed(2)}`);
    }
  }
});

test('the weapon never comes at the camera and its working end stays mostly on screen', () => {
  for (const name of M.clipNames) {
    const m = metrics(name), swipe = M.moves[name].stroke.kind === 'swipe';
    // blades: no point of the blade nearer than 0.3 blocks; swipes turn the whole resting item: never nearer than at rest
    assert.ok(swipe ? m.minDepth >= 0.165 : m.minDepth >= 0.3, `${name} comes within ${m.minDepth} blocks of the eye`);
    assert.ok(m.visible >= 0.45, `${name} on screen only ${m.visible} of the clip`);
    // at contact the blade is in front of the target, around the crosshair
    assert.ok(Math.abs(m.contactMid[0]) <= 0.55 && Math.abs(m.contactMid[1]) <= 0.75, `${name} contact at ${m.contactMid}`);
  }
});

test('timing follows the weapon: contact soon after the click, back at rest about when it is recharged', () => {
  const contactLimit = { light: 3.6, blade: 4.3, tool: 5.0, pole: 6.5, heavy: 9.5 };
  for (const name of M.clipNames) {
    const mv = M.moves[name], cls = M.classes[mv.cls], clip = clipOf(name);
    assert.ok(clip.contact <= contactLimit[cls.cat], `${name}: contact ${clip.contact.toFixed(2)} ticks after the click`);
    assert.ok(clip.dur <= cls.period * 1.12 + 0.5, `${name}: ${clip.dur.toFixed(1)} ticks, the cooldown is ${cls.period}`);
    // a slower weapon of the class (longer cooldown) swings slower and recovers later; a faster one quicker
    const slow = clipOf(name, { period: cls.period * 1.5 }), fast = clipOf(name, { period: cls.period * 0.75 });
    assert.ok(slow.dur > clip.dur && fast.dur < clip.dur && slow.contact >= clip.contact && fast.contact <= clip.contact, name + ' scales with the cooldown');
    assert.ok(slow.tr / clip.tr > slow.contact / clip.contact, name + ': the recovery stretches more than the strike');
  }
  // a weak (spammed) strike swings a shorter arc around the same contact point
  const full = clipOf('sword-diag', { amp: 1 }), weak = clipOf('sword-diag', { amp: T.amplitude(0.2) });
  const a = T.evalClip(full, full.contact), b = T.evalClip(weak, weak.contact);
  assert.ok(dist(a.g, b.g) < 0.02, 'same contact');
  assert.ok(dist(T.evalClip(weak, weak.tw).g, b.g) < dist(T.evalClip(full, full.tw).g, a.g), 'shorter wind-up');
});

test('the off hand is the mirror image of the main hand', () => {
  for (const name of ['sword-diag', 'axe-chop', 'spear-thrust', 'dagger-slash', 'claw-rake', 'hammer-slam']) {
    for (const p of [0.1, 0.3, 0.5, 0.8]) {
      const r = M.pose(name, p, true), l = M.pose(name, p, false);
      assert.ok(Math.abs(r.tx + l.tx) < 1e-9 && Math.abs(r.ty - l.ty) < 1e-9 && Math.abs(r.tz - l.tz) < 1e-9, name);
      assert.ok(Math.abs(r.rotation.angle - l.rotation.angle) < 1e-9);
      assert.ok(Math.abs(r.rotation.x - l.rotation.x) < 1e-9 && Math.abs(r.rotation.y + l.rotation.y) < 1e-9 && Math.abs(r.rotation.z + l.rotation.z) < 1e-9);
    }
  }
});

test('combos: a click mid-stroke waits and flows out of the follow-through; a backhand only from the left; a new combo after a pause', () => {
  const sim = S.play({ main: { cls: 'sword' }, clicks: [{ tick: 0 }, { tick: 1.0 }, { tick: 1.5 }], ticks: 30, step: 0.05 });
  assert.deepEqual(played(sim.frames, 0), ['sword-diag', 'sword-rise'], 'two clicks during the stroke make one waiting move: the backhand from the left');
  assert.deepEqual(pops(sim.frames, 0), [], 'no pop where the waiting move takes over');
  // slow clicking from rest: forehand, level cut (the backhand needs the blade on the left), thrust finisher
  const slow = S.play({ main: { cls: 'sword' }, clicks: [{ tick: 0 }, { tick: 13 }, { tick: 26 }, { tick: 39 }], ticks: 52, step: 0.5 });
  assert.deepEqual(played(slow.frames, 0), ['sword-diag', 'sword-flat', 'sword-thrust', 'sword-diag']);
  // after a long pause the combo starts over
  const reset = S.play({ main: { cls: 'axe' }, clicks: [{ tick: 0 }, { tick: 60 }], ticks: 70, step: 0.5 });
  assert.deepEqual(played(reset.frames, 0), ['axe-chop', 'axe-chop']);
});

test('sprint and falling clicks play the class variants', () => {
  const sprint = S.play({ main: { cls: 'katana' }, clicks: [{ tick: 0, sprint: true }], ticks: 2, step: 0.5 });
  assert.equal(sprint.frames[1].names[0], 'katana-dash');
  const jump = S.play({ main: { cls: 'hammer' }, clicks: [{ tick: 0, jump: true }], ticks: 2, step: 0.5 });
  assert.equal(jump.frames[1].names[0], 'hammer-meteor');
});

test('the combat feed: impacts at contact with hit-stop, verdicts by ref, refusals and misses stay quiet, upgrades, swing-only without a feed', () => {
  // a predicted hit (the swing aimed at an entity): one impact at contact, hit-stop, the clip still ends on time
  const hit = S.play({ main: { cls: 'axe' }, clicks: [{ tick: 0, target: 42, result: 'hit' }], ticks: 18, step: 0.25 });
  const fx = hit.effects.filter(e => e.kind === 'impact');
  assert.equal(fx.length, 1); assert.equal(fx[0].target, 42); assert.equal(fx[0].hand, 'M');
  const clip = hit.M._test.hands()[0].recent[0] || hit.M._test.hands()[0].strike;
  assert.ok(clip.stop && clip.stop.len >= 1, 'hit-stop set'); assert.equal(clip.realDur, clip.dur, 'ends on schedule');
  assert.ok(Math.abs(fx[0].t - clip.contact) <= 0.25, 'the impact fires at contact');
  assert.ok(fx[0].kick.pitch > 0 && fx[0].kick.ms > 0, 'a chop dips the view');
  // frozen during the stop
  const a = T.evalClip(clip, T.warp(clip, clip.stop.at + 0.1)), b = T.evalClip(clip, T.warp(clip, clip.stop.at + clip.stop.len * 0.8));
  assert.ok(dist(a.g, b.g) < 0.03, 'held at contact (' + dist(a.g, b.g).toFixed(3) + ')');
  // a swing at nothing: no impact; the server's miss on a predicted target cancels it if it arrives first
  const air = S.play({ main: { cls: 'sword' }, clicks: [{ tick: 0, target: -1, result: 'miss' }], ticks: 12, step: 0.25 });
  assert.equal(air.effects.length, 0);
  const missFirst = S.play({ main: { cls: 'hammer' }, clicks: [{ tick: 0, target: 5, result: 'miss', rtt: 50 }], ticks: 20, step: 0.25 });
  assert.equal(missFirst.effects.length, 0, 'the verdict (by ref) arrived before contact');
  const refused = S.play({ main: { cls: 'hammer' }, clicks: [{ tick: 0, target: 5, result: 'denied', rtt: 50 }], ticks: 20, step: 0.25 });
  assert.equal(refused.effects.length, 0, "a server refusal after a swing ('denied' with ref) is a miss");
  // a crit confirmed after the contact upgrades the impact (extra sparks and kick)
  const crit = S.play({ main: { cls: 'dagger' }, clicks: [{ tick: 0, target: 9, result: 'hit', crit: true, rtt: 200 }], ticks: 10, step: 0.25 });
  assert.deepEqual(crit.effects.map(e => e.kind), ['impact', 'upgrade']); assert.equal(crit.effects[1].crit, true);
  // the server hit a target the client did not aim at: a late impact without hit-stop
  const late = S.play({ main: { cls: 'sword' }, clicks: [{ tick: 0, target: -1, result: 'hit', rtt: 250 }], ticks: 12, step: 0.25 });
  assert.equal(late.effects.length, 1); assert.equal(late.M._test.hands()[0].recent[0] ? late.M._test.hands()[0].recent[0].stop : late.M._test.hands()[0].strike.stop, null);
  // no feed: the swing animates, nothing else
  const plain = S.play({ main: { cls: 'sword' }, clicks: [{ tick: 0, target: 3, result: 'hit' }], ticks: 12, step: 0.25, noFeed: true });
  assert.equal(plain.effects.length, 0); assert.ok(plain.frames.some(f => f.poses[0]), 'still animated');
});

test('every main x off pair has its own defined choreography', () => {
  const classes = Object.keys(M.classes), styles = Object.keys(D.STYLES), cells = D.matrix();
  assert.equal(cells.length, classes.length * (classes.length - 1), 'every main class x every off-hand-capable class');
  const names = new Set();
  for (const c of cells) {
    assert.ok(styles.includes(c.offSupport) && styles.includes(c.mainSupport), c.main + '+' + c.off + ' support styles');
    assert.ok(M.moves[c.offJoins] && M.moves[c.offJoins].cls === c.off, c.main + '+' + c.off + ': the off hand joins with its own move');
    assert.ok(M.moves[c.mainJoins] && M.moves[c.mainJoins].cls === c.main, c.main + '+' + c.off + ': the main hand joins with its own move');
    assert.ok(c.offJoinHold >= 0 && c.offJoinHold <= 2 && c.mainJoinHold >= 0 && c.mainJoinHold <= 2);
    for (const ch of [c.offChain, c.mainChain]) if (ch) for (const n of ch) assert.ok(M.moves[n], n);
    assert.ok(typeof c.name === 'string' && c.name.length > 8);
    names.add(c.main + '+' + c.off);
  }
  assert.equal(names.size, cells.length);
  for (const s of styles) assert.equal(D.supportPoses(s, 'sword', 'axe', M.moves['axe-chop']).length, 3, s);
});

test('dual wielding: the waiting hand answers each strike, takes over smoothly, and a quick second click joins', () => {
  for (const [main, off] of [['sword', 'sword'], ['sword', 'dagger'], ['axe', 'axe'], ['hammer', 'dagger'], ['dagger', 'dagger'], ['spear', 'sword'], ['claw', 'katana']]) {
    const alt = S.play({ main: { cls: main }, off: { cls: off }, clicks: [{ hand: 'M', tick: 0 }, { hand: 'O', tick: 7 }, { hand: 'M', tick: 14 }, { hand: 'O', tick: 21 }], ticks: 40, step: 0.1 });
    const offNames = new Set(alt.frames.map(f => f.names[1]).filter(Boolean)), mainNames = new Set(alt.frames.map(f => f.names[0]).filter(Boolean));
    assert.ok([...offNames].some(n => n.startsWith('support:')) && [...offNames].some(n => !n.startsWith('support:')), main + '+' + off + ' off hand supports and strikes');
    // the main hand answers an off-hand strike when it is not striking itself
    const idle = S.play({ main: { cls: main }, off: { cls: off }, clicks: [{ hand: 'M', tick: 0 }, { hand: 'O', tick: 40 }], ticks: 44, step: 0.5 });
    assert.ok(idle.frames.some(f => f.names[0] && f.names[0].startsWith('support:')), main + '+' + off + ' main hand supports');
    void mainNames;
    for (const hi of [0, 1]) assert.deepEqual(pops(alt.frames, hi), [], `${main}+${off} hand ${hi}: no pop at a takeover`);
    const joint = S.play({ main: { cls: main }, off: { cls: off }, clicks: [{ hand: 'M', tick: 0 }, { hand: 'O', tick: 1.5 }], ticks: 4, step: 0.5 });
    const pair = D.pair(main, off), want = D.jointMove(pair, 'off', null, off);
    assert.equal(joint.frames[joint.frames.length - 1].names[1], want, `${main}+${off} joint move`);
  }
});

// a native pose back in view space (the left hand mirrored back): grip and orientation of the canonical item
function viewPose(n, right) {
  let t = [n.tx, n.ty, n.tz], axis = [n.rotation.x, n.rotation.y, n.rotation.z];
  if (!right) { t = [-t[0], t[1], t[2]]; axis = [axis[0], -axis[1], -axis[2]]; }
  const q = C.qaxis(axis, n.rotation.angle * C.DEG);
  return { g: C.add(C.add(C.PIVOT, t), C.qrot(q, C.GRIP_LOCAL)), q };
}
test('every pair, both hands: support poses and strikes keep the blades off the camera', () => {
  const classes = Object.keys(M.classes);
  let worst = Infinity, where = '';
  for (const style of Object.keys(D.STYLES)) for (const sup of classes) for (const lead of classes)
    for (const p of D.supportPoses(style, sup, lead, M.moves[M.classes[lead].chain[0]]))
      for (const ip of [[0.22, 0.2, 0.5], [0.6, 0.6, 0.5], [0.93, 0.93, 0.5]]) { const d = -C.itemPoint(p, ip)[2]; if (d < worst) { worst = d; where = style + ' ' + sup + ' by ' + lead; } }
  assert.ok(worst >= 0.35, 'support pose within ' + worst.toFixed(3) + ' of the eye: ' + where);
  for (const main of classes) for (const off of classes) {
    if (off === 'claw') continue;
    const sim = S.play({ main: { cls: main }, off: { cls: off }, clicks: [{ hand: 'M', tick: 0 }, { hand: 'O', tick: 6 }, { hand: 'M', tick: 13 }, { hand: 'O', tick: 15 }], ticks: 34, step: 0.5 });
    for (const f of sim.frames) for (const hi of [0, 1]) {
      const n = f.poses[hi]; if (!n) continue;
      const cls = hi ? off : main;
      if (M.classes[cls].trail === false) continue;   // swipe classes turn the whole resting item: covered per move
      const vp = viewPose(n, hi === 0);
      for (const ip of [[0.22, 0.2, 0.5], [0.6, 0.6, 0.5], [0.93, 0.93, 0.5]]) {
        const d = -C.itemPoint(vp, ip)[2];
        assert.ok(d >= 0.3, `${main}+${off} hand ${hi} at ${f.t}: ${d.toFixed(3)} from the eye (${f.names[hi]})`);
      }
    }
  }
});

test('determinism: the same clicks give the same frames', () => {
  const script = { main: { cls: 'greatsword' }, off: { cls: 'dagger' }, clicks: [{ hand: 'M', tick: 0, target: 4, result: 'hit', crit: true }, { hand: 'O', tick: 3 }, { hand: 'O', tick: 11, result: 'hit' }], ticks: 30, step: 0.25 };
  const a = S.play(script), b = S.play(script);
  assert.deepEqual(a.frames.map(f => [f.poses, f.names]), b.frames.map(f => [f.poses, f.names]));
  assert.deepEqual(a.effects.map(e => [e.kind, e.t, e.kick]), b.effects.map(e => [e.kind, e.t, e.kick]));
});

test('blade trails follow the stroke only, mirror for the left hand, freeze during hit-stop, and the effect queue is bounded', () => {
  const sim = S.play({ main: { cls: 'sword' }, off: { cls: 'sword' }, clicks: [{ hand: 'O', tick: 0, target: 3, result: 'hit' }], ticks: 12, step: 0.25 });
  const withTrail = sim.frames.filter(f => f.trails[1]);
  assert.ok(withTrail.length >= 3, 'a trail during the stroke');
  for (const f of withTrail) {
    assert.equal(f.trails[1].right, false, 'off hand drawn on the left');
    for (const v of f.trails[1].pts) assert.ok(Number.isFinite(v));
  }
  assert.ok(sim.frames.filter(f => f.t < 1).every(f => !f.trails[1]), 'no trail during the early wind-up');
  const h = sim.M._test.hands()[1], clip = h.recent && h.recent[0] || h.strike;
  const t1 = sim.M._test.hands && clip.stop ? [clip.tick + clip.stop.at + 0.2, clip.tick + clip.stop.at + clip.stop.len * 0.8] : null;
  assert.ok(t1, 'hit-stop on the off-hand strike');
  // effects: never more than 16 waiting
  M.clear();
  for (let i = 0; i < 40; i++) M.effects.push({ kind: 'impact' });
  assert.ok(M.effects.length >= 40);
  M.clear();
  assert.equal(M.effects.length, 0);
});

test('items: every JasperCraft melee weapon maps to its class; trinkets, guns and the sentry turret do not animate', () => {
  const ap = id => IT.describe(276, '{JasprApocalypse:{id:"' + id + '",equipmentMark:"jaspr-expedition-v1",tier:3},Unbreakable:1b,HideFlags:4}', 1400);
  const want = { trench_blade: 'dagger', mono_katana: 'katana', gravity_maul: 'hammer', reaper_scythe: 'scythe', sentinel_spear: 'spear',
    hollow_halberd: 'glaive', ossuary_flail: 'flail', wire_whip: 'flail', glass_rapier: 'rapier', rebar_sword: 'greatsword',
    magmaforged_greataxe: 'greataxe', bone_reaver: 'axe', railpick: 'axe', shock_baton: 'sword', hunters_kukri: 'dagger' };
  for (const k in want) { const d = ap(k); assert.equal(d.cls, want[k], k); assert.ok(d.frame && d.frame.grip, k + ' has its 3D model frame'); assert.ok(d.period > 0, k + ' server recovery'); }
  assert.equal(Object.keys(IT.APOCALYPSE).length, 40);
  assert.equal(IT.describe(293, '{JasprApocalypse:{id:"rifle",rounds:5}}', 1560).cls, null, 'guns stay vanilla');
  assert.equal(IT.describe(257, '{JasprApocalypse:{id:"sentry_turret"}}', 100).cls, null, 'the sentry turret is not a weapon');
  assert.equal(IT.describe(267, '{JasprPenitentArmory:{version:1,kind:"weapon",id:"WAX_SCALPEL",uuid:"x"}}', 0).cls, 'dagger');
  assert.equal(IT.describe(258, '{JasprPenitentArmory:{id:"INQUISITOR_MAUL"}}', 0).cls, 'hammer');
  assert.equal(IT.describe(419, '{JasprPenitentArmory:{id:"LAST_ABSOLUTION"}}', 0).cls, null, 'dungeon guns stay vanilla');
  assert.equal(IT.describe(272, '{JasprPenitentRelic:{kind:"trophy",id:"DEEPBREAKER",version:1},Unbreakable:1b}', 75).cls, 'hammer');
  assert.equal(IT.describe(272, '{JasprPenitentRelic:{kind:"relic",id:"BONE_DICE"},Unbreakable:1b}', 12).cls, null, 'trinket icons');
  assert.equal(IT.describe(273, '{JasprRuins:{id:"x"},Unbreakable:1b}', 11).cls, null, 'stone shovel trinket bands');
  const realm = IT.describe(279, '{JasprArmory:{set:"titan",piece:"axe"},Unbreakable:1b}', 106);
  assert.equal(realm.cls, 'axe'); assert.deepEqual(realm.tint, IT.SETS.titan);
  assert.equal(IT.describe(276, '{JasprArmory:{set:"void",material:"x"}}', 0).cls, null);
  assert.equal(IT.describe(276, '{JasprNether:{id:"cursed_katana"}}', 0).cls, 'katana');
  assert.equal(IT.describe(278, '{JasprNether:{id:"withered_amedian_hammer"}}', 0).cls, 'hammer');
  assert.equal(IT.describe(409, '{JasprNether:{id:"rime_scepter"}}', 0).cls, 'staff');
  assert.equal(IT.describe(279, '{JasprNether:{id:"obsidian_aegis"}}', 0).cls, null);
  assert.equal(IT.describe(323, '{JasprBackrooms:{id:"stop_sign"}}', 0).cls, 'hammer');
  assert.equal(IT.describe(359, '{JasprBackrooms:{id:"utility_knife"}}', 0).cls, 'dagger');
  assert.equal(IT.describe(4004, null, 3).cls, 'hammer'); assert.equal(IT.describe(4003, null, 0).cls, 'claw');
  assert.equal(IT.describe(280, null, 0).cls, null, 'a plain stick is not a weapon');
  assert.equal(IT.describe(276, null, 0).cls, 'sword'); assert.equal(IT.describe(286, null, 0).cls, 'axe'); assert.equal(IT.describe(277, null, 0).cls, 'shovel');
  assert.equal(IT.describe(293, '{display:{Name:"Windmill Scythe-Hoe",Lore:["x"]}}', 0).cls, 'scythe');
  assert.equal(IT.describe(0, null, 0).cls, null, 'fists keep the vanilla arm');
  // the NBT reader respects nesting and quoting
  assert.equal(IT.field(IT.compound('{a:{b:{c:1}},JasprArmory:{set:"emer,ald",piece:"sword"}}', 'JasprArmory'), 'set'), 'emer,ald');
});

test('3D model frames: identity at rest, the model blade follows the canonical blade in motion', () => {
  const d = IT.describe(276, '{JasprApocalypse:{id:"voidsteel_katana"}}', 1150), f = C.modelFrame(d.frame);
  const rest = C.toNative({ g: C.REST.g, q: C.REST.q }, true, 'x', 0, 'rest', f);
  assert.ok(Math.hypot(rest.tx, rest.ty, rest.tz) < 1e-9 && (rest.rotation.angle < 1e-6 || Math.abs(rest.rotation.angle - 360) < 1e-6));
  const clip = clipOf('katana-kesa'), pose = T.evalClip(clip, clip.contact), cal = C.calibrated(pose, f);
  const bladeM = C.unit(C.sub(C.displayPoint([8, 9, 8], d.frame), C.displayPoint([8, 8, 8], d.frame)));
  const drawn = C.qrot(cal.q, bladeM), canonical = C.qrot(pose.q, C.BLADE);
  assert.ok(C.dot(drawn, canonical) > 0.999, 'same blade direction');
});

test('MELEE_ANIMATION_V5.md lists the pair matrix exactly as the code defines it', () => {
  const doc = require('node:fs').readFileSync(require('node:path').join(__dirname, '../MELEE_ANIMATION_V5.md'), 'utf8');
  assert.ok(doc.includes(require('../scripts/melee-dual-matrix.cjs').table()), 'regenerate with node scripts/melee-dual-matrix.cjs');
});
