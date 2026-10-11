/* JasperCraft first-person melee motion v5 (2026-10-11): per-hand timelines, combos, dual-wield choreography, hit-stop
 * and the effect intents (impacts, trails) the JASPR_COMBATFX stage turns into camera kicks, sparks and blade trails.
 *
 * Everything is clocked by simulation ticks (player ticks + partial ticks), never by wall time alone, and is a pure
 * function of the swings observed so far and the tick being drawn, so it is deterministic and testable offline.
 * Visual only: damage, reach, cooldowns and packets are untouched; the only engine-facing output is the first-person
 * held-item matrix (one translate and one axis-angle rotate per hand) and the effect intents.
 *
 * A strike is a move of the weapon's class: wind-up (anticipation, an optional loaded pause at the top), a fast
 * committed stroke whose edge leads, and a recovery (follow-through, optional twirl, settle back to the vanilla rest).
 * Strike sources: JasprMeleeMotion.mark (the Better Combat head's markSwing), the combat feed's 'swing' events
 * (JasprCombatFeed, workstream A) and, as a fallback, JasprBetterCombat.motion(); the three are merged per hand.
 * While dual wielding, the hand that is not striking plays a support track tied to the striking hand's timeline (guard,
 * cock, counter, flow, tuck, brace, ready, duck), so both weapons move as one body; see melee-dual.js.
 */
var JasprMeleeMotion = (function (C, MV, IT, DU) {
  'use strict';
  var ZERO = C.ZERO, REST = C.REST;
  var COMBO_WINDOW = 26;      // ticks after a stroke ends within which the next strike continues the combo chain
  var BOTH_WINDOW = 4.5;      // ticks: a strike of the other hand this soon after one hand's click is a joint move
  var MERGE_MS = 320;         // observations of one swing from different sources (mark, feed, poll) are merged
  var MAX_EFFECTS = 16, MAX_FEED = 64;

  var knobs = { motion: true, hitstop: 1, amplitude: true, support: true, impacts: true, trails: true, dual: true, trailTicks: 1.8, trailSamples: 10 };
  var stats = { draws: 0, marks: 0, observed: 0, merged: 0, strikes: 0, chained: 0, queued: 0, supports: 0, joint: 0, impacts: 0,
    stops: 0, late: 0, upgrades: 0, feedEvents: 0, polled: 0, effectsDropped: 0, dropped: 0, errors: 0, last: null, clips: {} };

  // ---------------------------------------------------------------- clips
  // Phase lengths are authored in ticks at the class's nominal cooldown; the real cooldown of the held weapon scales
  // them: the anticipation and stroke gently (snappy stays snappy), the recovery fully, so a weapon is back at rest
  // about when it is recharged.
  function timing(move, cls, period) {
    var P = cls.period || 12.5, p = period > 0 && Number.isFinite(period) ? period : P, s = C.clamp(p / P, 0.7, 1.6), f = Math.pow(s, 0.4);
    return { tw: move.w * f, th: (move.h || 0) * f, ts: move.s * f, tr: Math.max(2.5, move.r * s) };
  }
  // amplitude from the energy at the click: a full-energy strike swings the full arc, a spammed one a shorter arc
  // around the same contact point (it looks as weak as it hits)
  function amplitude(energy) { return !knobs.amplitude || !Number.isFinite(energy) ? 1 : C.clamp(0.6 + 0.4 * Math.pow(C.clamp(energy, 0, 1), 0.7), 0.6, 1); }
  function makeClip(move, cls, o) {
    var tm = timing(move, cls, o.period), st = move.stroke, ks = C.strokeKeys(st), ci = move.contactKey == null ? 1 : move.contactKey;
    if (o.delay > 0) tm.th += o.delay;   // a joining strike held cocked so it lands just after the other hand's
    var kc = ks[Math.min(ci, ks.length - 1)], a = o.amp == null ? 1 : o.amp, u0 = kc * (1 - a), u1 = kc + (1 - kc) * a;
    var tc = C.strokeTimeOf((kc - u0) / Math.max(1e-6, u1 - u0), move.peak, move.lead, move.top);
    var clip = { move: move, name: move.name, cls: cls.name, tw: tm.tw, th: tm.th, ts: tm.ts, tr: tm.tr, u0: u0, u1: u1, kc: kc, amp: a,
      tick: o.tick, start: o.start || null, hand: o.hand, seq: o.seq || 0, variant: o.variant || null, joint: !!o.joint,
      target: -1, result: null, crit: false, sweep: false, fatal: false, impactDone: false, impactFired: false, upgraded: false,
      stop: null, keysW: null, keysH: null, keysR: null, at: o.at || 0 };
    clip.dur = clip.tw + clip.th + clip.ts + clip.tr;
    clip.strokeEnd = clip.tw + clip.th + clip.ts;
    clip.contact = clip.tw + clip.th + clip.ts * tc;
    clip.realDur = clip.dur;
    return clip;
  }
  function cockedPose(clip) {
    var m = clip.move;
    if (!(clip.th > 0)) return C.strokePose(m.stroke, clip.u0);
    // arrive a little into the stroke, then draw back during the pause: the loaded moment before a heavy blow
    return C.strokePose(m.stroke, Math.min(clip.kc, clip.u0 + (m.tense == null ? 0.07 : m.tense)));
  }
  function viaKey(clip, at, k) {
    var st = clip.move.stroke;
    if (typeof k.blade === 'number' && st.kind !== 'thrust') {
      var pb = C.planeBasis(C.planeNormal(st.plane)), th = k.blade * C.DEG, d = C.add(C.mul(pb.xp, Math.cos(th)), C.mul(pb.yp, Math.sin(th)));
      var flat = st.flip ? C.mul(pb.n, -1) : pb.n;
      if (k.roll) flat = C.rotateAbout(flat, d, k.roll * C.DEG);
      return { t: at, g: C.view(k.grip), q: C.qframes(st.reverse || k.reverse ? C.mul(C.BLADE, -1) : C.BLADE, C.FLAT, d, flat) };
    }
    var p = k.pose || (k.pose = poseOf(k));
    return k.still ? { t: at, g: p.g, q: p.q, v: ZERO, w: ZERO } : { t: at, g: p.g, q: p.q };
  }
  // a free key: grip (screen x, y, depth), aim [yaw, pitch] of the blade, roll of the flat about the blade (degrees);
  // or, for swipes, rot [axis x, y, z, degrees] relative to the resting item
  function poseOf(k) {
    if (k.rot) return { g: C.view(k.grip), q: C.qexp(C.rotVec(k.rot)) };
    var dir = k.dir || C.aimDir(k.aim[0], k.aim[1]), ref = k.flat || [-1, 0, 0];
    var f = C.sub(ref, C.mul(dir, C.dot(ref, dir)));
    if (C.len(f) < 1e-3) f = C.cross(dir, [0, 0, 1]);
    f = C.unit(f);
    if (k.roll) f = C.rotateAbout(f, dir, k.roll * C.DEG);
    return C.freePose(k.grip, dir, f, k.reverse);
  }
  function windupKeys(clip) {
    if (clip.keysW) return clip.keysW;
    var from = clip.start, m = clip.move, first = cockedPose(clip), keys;
    keys = [from ? { t: 0, g: from.g, q: from.q, v: from.v, w: from.w } : { t: 0, g: REST.g, q: REST.q, v: ZERO, w: ZERO }];
    (m.ready || []).forEach(function (k) { if (!from || k.always || nearRest(from)) keys.push(viaKey(clip, clip.tw * k.at, k)); });
    keys.push({ t: clip.tw, g: first.g, q: first.q, v: ZERO, w: ZERO });
    return (clip.keysW = keys);
  }
  function nearRest(s) { return C.len(C.sub(s.g, REST.g)) < 0.06 && C.qangle(s.q, REST.q) < 0.2 && C.len(s.v) < 0.02; }
  function holdKeys(clip) {
    if (clip.keysH) return clip.keysH;
    var a = cockedPose(clip), b = C.strokePose(clip.move.stroke, clip.u0);
    return (clip.keysH = [{ t: clip.tw, g: a.g, q: a.q, v: ZERO, w: ZERO }, { t: clip.tw + clip.th, g: b.g, q: b.q, v: ZERO, w: ZERO }]);
  }
  function recoverKeys(clip) {
    if (clip.keysR) return clip.keysR;
    var m = clip.move, t0 = clip.strokeEnd, last = C.strokePose(m.stroke, clip.u1), keys = [{ t: t0, g: last.g, q: last.q, v: ZERO, w: ZERO }];
    (m.recover || []).forEach(function (k) { keys.push(viaKey(clip, t0 + clip.tr * k.at, k)); });
    keys.push({ t: clip.dur, g: REST.g, q: REST.q, v: ZERO, w: ZERO });
    return (clip.keysR = keys);
  }
  // A flourish: whole turns of the weapon about one of its own axes, layered on a phase (the fingers spinning a dagger,
  // a staff whirled overhead, a rapier's wrist roll). Whole turns end exactly where they began, so phases still meet.
  var TWIRL_AXES = { flat: C.FLAT, blade: C.BLADE, edge: C.EDGE };
  function spinOf(tw, t, a, b, pose) {
    if (!tw || t <= a) return pose;
    var f = t >= b ? 1 : C.smooth((t - a) / (b - a)), angle = 2 * Math.PI * Math.round(tw.turns) * f;
    return { g: pose.g, q: C.qnorm(C.qmul(pose.q, C.qaxis(TWIRL_AXES[tw.axis] || C.FLAT, angle))) };
  }
  function twirl(clip, pose, t) { var tw = clip.move.twirl; return tw ? spinOf(tw, t, clip.strokeEnd + clip.tr * tw.from, clip.strokeEnd + clip.tr * tw.to, pose) : pose; }
  function windTwirl(clip, pose, t) { var tw = clip.move.windTwirl; return tw ? spinOf(tw, t, clip.tw * tw.from, clip.tw * tw.to, pose) : pose; }
  // Hit-stop: at contact the stroke all but stops for `len` ticks, then catches up over the follow-through, so the
  // clip still ends when it would have (the cooldown picture stays true).
  function warp(clip, t) {
    var s = clip.stop; if (!s || t <= s.at) return t;
    if (t < s.at + s.len) return s.at + (t - s.at) * s.creep;
    var x = (t - s.at - s.len) / s.catchup; if (x >= 1) return t;
    return t - s.len * (1 - s.creep) * (1 - C.smooth(x));
  }
  function evalClip(clip, t) {
    var m = clip.move, pose, phase;
    if (t < clip.tw) { var t0 = Math.max(0, t); pose = windTwirl(clip, C.keyed(windupKeys(clip), t0), t0); phase = 'windup'; }
    else if (t < clip.tw + clip.th) { pose = C.keyed(holdKeys(clip), t); phase = 'hold'; }
    else if (t < clip.strokeEnd) {
      var u = clip.u0 + (clip.u1 - clip.u0) * C.strokeProgress((t - clip.tw - clip.th) / clip.ts, m.peak, m.lead, m.top);
      pose = C.strokePose(m.stroke, u); phase = 'stroke';
    } else { var tt = Math.min(t, clip.dur); pose = twirl(clip, C.keyed(recoverKeys(clip), tt), tt); phase = t >= clip.dur ? 'rest' : 'recover'; }
    pose.phase = phase;
    return pose;
  }
  function clipAt(clip, T) { return evalClip(clip, warp(clip, T - clip.tick)); }
  function clipActive(clip, T) { return !!clip && T >= clip.tick && T - clip.tick < clip.realDur; }

  // ---------------------------------------------------------------- support tracks (the hand that is not striking)
  // Key poses come from melee-dual.js for the pair; they are timed to the leader's wind-up end, contact, stroke end and
  // end, and evaluated on the leader's own (hit-stop warped) clock, so the two hands stay locked together.
  function makeSupport(leader, style, cls, start, hi) {
    var keys = [start ? { t: 0, g: start.g, q: start.q, v: start.v, w: start.w } : { t: 0, g: REST.g, q: REST.q, v: ZERO, w: ZERO }];
    var poses = DU.supportPoses(style, cls, leader.cls, leader.move);
    var times = [leader.tw + leader.th * 0.5, leader.contact, leader.strokeEnd + leader.tr * 0.25];
    for (var i = 0; i < poses.length && i < times.length; i++) if (poses[i]) { var p = poses[i]; keys.push({ t: times[i], g: p.g, q: p.q }); }
    keys.push({ t: leader.dur, g: REST.g, q: REST.q, v: ZERO, w: ZERO });
    // keys must increase in time
    for (var j = 1; j < keys.length; j++) if (keys[j].t <= keys[j - 1].t) keys[j].t = keys[j - 1].t + 0.05;
    stats.supports++;
    return { leader: leader, style: style, keys: keys, hand: hi, tick: leader.tick };
  }
  function supportAt(sup, T) {
    var lt = warp(sup.leader, T - sup.leader.tick), last = sup.keys[sup.keys.length - 1].t;
    var pose = C.keyed(sup.keys, C.clamp(lt, 0, last));
    pose.phase = lt >= last ? 'rest' : 'support';
    return pose;
  }
  function supportActive(sup, T) { return !!sup && T >= sup.tick && warp(sup.leader, T - sup.leader.tick) < sup.keys[sup.keys.length - 1].t; }

  // ---------------------------------------------------------------- hands
  function newHand(i) { return { index: i, strike: null, queued: null, support: null, pending: [], seq: 0, lastEnd: -1e9, lastSide: 'right', desc: null, boundAt: -1e9, right: i === 0, energy: 1, period: 0, lastPoll: 0 }; }
  var hands = [newHand(0), newHand(1)], effects = [], feedSeq = -1, feedOverride = null, lastFrameT = null, lastNow = 0;
  var player = { sprint: false, ground: true, falling: false, at: -1e9 };

  function poseOfHand(h, T) {
    if (clipActive(h.strike, T)) return clipAt(h.strike, T);
    if (supportActive(h.support, T)) return supportAt(h.support, T);
    return null;
  }
  // pose and velocity (per tick) of a hand right now: where a new strike or support track starts from
  function stateOf(h, T) {
    var p = poseOfHand(h, T);
    if (!p) return null;
    var e = 0.03, a = poseOfHand(h, T - e) || p, b = poseOfHand(h, T + e) || { g: REST.g, q: REST.q };
    return { g: p.g, q: p.q, v: C.mul(C.sub(b.g, a.g), 1 / (2 * e)), w: C.mul(C.qrot(a.q, C.qlog(C.qmul(C.qconj(a.q), b.q))), 1 / (2 * e)) };
  }
  function sideOf(state) { if (!state) return 'right'; var s = C.screen(state.g); return s[0] < -0.12 ? 'left' : s[1] > 0.25 ? 'high' : 'right'; }

  // ---------------------------------------------------------------- observing swings
  // A swing can be reported by markSwing (tick known), by the feed ('swing', wall time) or by polling motion(); the
  // reports of one swing are merged, each new swing becomes a pending strike resolved at the hand's next draw.
  function observe(hi, o) {
    var h = hands[hi], now = o.at;
    stats.observed++;
    var list = h.pending.concat(h.strike ? [h.strike] : [], h.queued ? [h.queued] : []);
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      if (s.sources && !s.sources[o.source] && Math.abs((s.at || 0) - now) <= MERGE_MS) {
        s.sources[o.source] = true; stats.merged++;
        if (o.target != null && o.target >= 0) s.target = o.target;
        if (o.source === 'mark' && Number.isFinite(o.tick) && s.tickFrom !== 'mark') { s.tickFrom = 'mark'; }
        return s;
      }
    }
    if (h.pending.length >= 3) h.pending.shift();
    var p = { at: now, tick: o.tick, tickFrom: o.source, id: o.id, target: o.target != null ? o.target : -1, sources: {}, energy: o.energy,
      period: o.period, sprint: player.sprint && lastNow - player.at < 400, airborne: !player.ground && player.falling && lastNow - player.at < 400 };
    p.sources[o.source] = true;
    h.pending.push(p);
    return p;
  }
  function mark(off, id, now, tick) {
    if (!knobs.motion) return;
    stats.marks++;
    var hi = off ? 1 : 0, h = hands[hi];
    // the click is handled before the player's tick count advances: the swing starts at the next tick boundary
    var t = Number.isFinite(tick) ? tick + 1 : lastFrameT;
    if (!Number.isFinite(t)) return;
    observe(hi, { source: 'mark', at: Number.isFinite(now) ? now : Date.now(), tick: t, id: id, energy: h.energy, period: h.period });
  }

  // ---------------------------------------------------------------- the combat feed (workstream A), optional
  function feed() {
    if (feedOverride) return feedOverride;
    try { return typeof JasprCombatFeed !== 'undefined' && JasprCombatFeed && typeof JasprCombatFeed.since === 'function' ? JasprCombatFeed : null; } catch (e) { return null; }
  }
  function syncFeed(T, now) {
    var f = feed(); if (!f) return;
    var list; try { list = f.since(feedSeq); } catch (e) { return; }
    if (!list || !list.length) return;
    var n = 0;
    for (var i = 0; i < list.length && n < MAX_FEED; i++) {
      var e = list[i]; if (!e || !(e.seq > feedSeq)) continue;
      feedSeq = e.seq; n++; stats.feedEvents++;
      var hi = e.hand === 'O' ? 1 : 0, at = Number.isFinite(e.at) ? e.at : now;
      if (e.kind === 'swing') {
        // the player's tick count at the click when the feed carries it (the click comes before the tick advances)
        var tick = Number.isFinite(e.tick) && e.tick >= 0 && Math.abs(e.tick + 1 - T) < 40 ? e.tick + 1 : T - Math.max(0, now - at) / 50;
        var s = observe(hi, { source: 'feed', at: at, tick: tick, id: e.item, target: Number.isFinite(e.target) ? e.target : -1, energy: hands[hi].energy, period: hands[hi].period });
        if (s) { if (Number.isFinite(e.target)) s.target = e.target; s.feedRef = e.seq; }
      } else if (e.kind === 'hit' || e.kind === 'blocked' || e.kind === 'miss') confirm(hi, e, T, at, e.kind);
      // a server refusal after a swing (ref set) is no hit: like a miss; a click consumed without a swing has no strike
      else if (e.kind === 'denied' && e.ref > 0) confirm(hi, e, T, at, 'miss');
    }
  }
  // the server's verdict on a swing: upgrades a predicted impact, or fires a late one the client did not predict. The
  // feed's ref names the swing; without it the nearest unanswered swing of that hand (same target first) is taken.
  function confirm(hi, e, T, at, kind) {
    var h = hands[hi], cands = [h.queued, h.strike].concat(h.pending).concat(h.recent || []), best = null;
    for (var i = 0; i < cands.length; i++) {
      var s = cands[i]; if (!s || s.result) continue;
      if (e.ref > 0) { if (s.feedRef === e.ref) { best = s; break; } continue; }
      if (at - (s.at || 0) > 1500 || (s.at || 0) - at > 120) continue;
      if (!best || (s.target === e.target && best.target !== e.target) || ((s.target === e.target) === (best.target === e.target) && (s.at || 0) < (best.at || 0))) best = s;
    }
    if (!best) return;
    best.result = kind || e.kind; best.crit = !!e.crit; best.sweep = !!e.sweep; best.fatal = !!e.fatal;
    if (Number.isFinite(e.target) && e.target >= 0 && best.target < 0) best.target = e.target;
    if (best.move && best.impactDone) {
      if (!best.impactFired && best.result !== 'miss' && T - (best.tick + best.contact) < 6) { fireImpact(hands[hi], best, T, false); stats.late++; }
      else if (best.impactFired && !best.upgraded && best.result !== 'miss' && (best.crit || best.fatal || best.sweep || best.result === 'blocked')) { best.upgraded = true; pushEffect(impactEffect(hands[hi], best, 'upgrade')); stats.upgrades++; }
    }
  }
  // fallback for a head that neither calls mark() nor writes a feed: JasprBetterCombat.motion(main) -> {at, sequence}
  function pollMotion(T, now) {
    try {
      if (typeof JasprBetterCombat === 'undefined' || !JasprBetterCombat || typeof JasprBetterCombat.motion !== 'function') return;
      for (var hi = 0; hi < 2; hi++) {
        var m = JasprBetterCombat.motion(hi === 0), h = hands[hi];
        if (!m || !(m.sequence > h.lastPoll)) continue;
        h.lastPoll = m.sequence; stats.polled++;
        observe(hi, { source: 'poll', at: m.at, tick: T - Math.max(0, now - m.at) / 50, energy: h.energy, period: h.period });
      }
    } catch (e) {}
  }

  // ---------------------------------------------------------------- resolving strikes
  var FRESH_MS = 450;
  function fresh(h) { return !!h.desc && lastNow - h.boundAt <= FRESH_MS; }
  function classOf(h) { return fresh(h) && h.desc.cls && MV.classes[h.desc.cls] ? MV.classes[h.desc.cls] : null; }
  function pairOf() {
    if (!knobs.dual) return null;
    var a = classOf(hands[0]), b = classOf(hands[1]);
    return a && b && hands[1].dualOk !== false ? DU.pair(a.name, b.name) : null;
  }
  function resolve(h, T) {
    if (!h.pending.length) return;
    var cls = classOf(h);
    if (!cls) {
      // bound to a fist or a non-melee item: the vanilla arm stays; never bound yet: wait a little for the first draw
      if (fresh(h) || lastNow - (h.pending[0].at || 0) > 600) { stats.dropped += h.pending.length; h.pending.length = 0; }
      return;
    }
    while (h.pending.length) {
      var p = h.pending.shift();
      if (!Number.isFinite(p.tick) || p.tick > T + 2 || T - p.tick > 30) { stats.dropped++; continue; }
      request(h, p, cls, T);
    }
  }
  function request(h, p, cls, T) {
    var cur = h.strike;
    // a click during a wind-up or stroke never cuts the stroke off: one move waits and flows out of its follow-through
    if (cur && clipActive(cur, p.tick) && warp(cur, p.tick - cur.tick) < cur.strokeEnd) {
      if (!h.queued) { h.queued = p; stats.queued++; } else { h.queued.at = p.at; }
      return;
    }
    startStrike(h, p, cls, p.tick, T);
  }
  var forced = [null, null];   // previews and tests: the next move of a hand
  function chooseMove(h, cls, p, start, tick, pair) {
    var other = hands[1 - h.index], side = sideOf(start), name = null, joint = false;
    if (forced[h.index] && MV.moves[forced[h.index]]) { var f = forced[h.index]; forced[h.index] = null; return { move: MV.moves[f], joint: false, delay: 0 }; }
    if (tick - h.lastEnd > COMBO_WINDOW || h.lastCls !== cls.name) h.seq = 0;
    // a joint move: the other hand clicked just before and is still winding up or striking
    var delay = 0;
    if (pair && other.strike && clipActive(other.strike, p.tick) && p.tick - other.strike.tick <= BOTH_WINDOW && other.strike.contact > p.tick - other.strike.tick - 1) {
      name = DU.jointMove(pair, h.index === 0 ? 'main' : 'off', other.strike, cls.name);
      joint = !!name;
      if (joint) delay = DU.jointDelay(pair, h.index === 0 ? 'main' : 'off');
    }
    if (!name && p.airborne && cls.jump) name = cls.jump;
    else if (!name && p.sprint && cls.sprint) name = cls.sprint;
    if (!name) {
      var chain = (pair && DU.chain(pair, h.index === 0 ? 'main' : 'off')) || cls.chain, n = chain.length;
      for (var k = 0; k < n; k++) {
        var cand = chain[(h.seq + k) % n], mv = MV.moves[cand];
        if (!mv) continue;
        if (mv.from === 'any' || mv.from === side || (side === 'high' && mv.from === 'right')) { name = cand; h.seq = (h.seq + k) % n + 1; break; }
      }
      if (!name) { name = chain[0]; h.seq = 1; }
    }
    return { move: MV.moves[name], joint: joint, delay: delay };
  }
  function retire(h, clip) { if (clip) h.recent = [clip].concat(h.recent || []).slice(0, 2); }
  function startStrike(h, p, cls, tick, T) {
    var start = stateOf(h, tick), pair = pairOf();
    if (h.strike) h.lastEnd = Math.max(h.lastEnd, Math.min(tick, realTimeOf(h.strike, h.strike.strokeEnd)));
    var pick = chooseMove(h, cls, p, start, tick, pair);
    if (!pick.move) return;
    var clip = makeClip(pick.move, cls, { tick: tick, start: start, hand: h.index, seq: h.seq, period: Math.max(p.period || h.period || 0, h.desc && h.desc.period || 0),
      amp: amplitude(p.energy), variant: p.airborne ? 'jump' : p.sprint ? 'sprint' : null, joint: pick.joint, at: p.at, delay: pick.delay });
    clip.sources = p.sources; clip.target = p.target; clip.id = p.id; clip.feedRef = p.feedRef;
    clip.result = p.result || null; clip.crit = !!p.crit; clip.sweep = !!p.sweep; clip.fatal = !!p.fatal;
    if (h.strike) { retire(h, h.strike); if (start) stats.chained++; }
    h.strike = clip; h.queued = null; h.support = null; h.lastCls = cls.name; h.lastSide = sideOf(start);
    stats.strikes++; if (pick.joint) stats.joint++;
    stats.clips[clip.name] = (stats.clips[clip.name] || 0) + 1;
    // the other hand answers: a support track tied to this strike, unless it is winding up or striking itself (its
    // recovery is taken over: the support starts where the weapon is, at its current speed)
    var other = hands[1 - h.index], ocls = classOf(other);
    if (!knobs.support || !pair || pick.joint || !ocls) return;
    var busy = other.strike && clipActive(other.strike, tick) && warp(other.strike, tick - other.strike.tick) < other.strike.strokeEnd;
    if (busy) return;
    var style = DU.supportStyle(pair, h.index === 0 ? 'main' : 'off');
    if (!style) return;
    var os = stateOf(other, tick);
    retire(other, other.strike); other.strike = null; other.queued = null;
    other.support = makeSupport(clip, style, ocls.name, os, other.index);
  }
  // the waiting move takes over when the playing stroke has finished
  function promote(h, T) {
    var cur = h.strike, q = h.queued; if (!cur || !q) return;
    var endReal = realTimeOf(cur, cur.strokeEnd);
    if (T < endReal) return;
    h.lastEnd = endReal;
    var cls = classOf(h); if (!cls) { h.queued = null; return; }
    q.tick = endReal; startStrike(h, q, cls, endReal, T); stats.chained++;
  }
  // the real (unwarped) local time at which the clip's warped time reaches tw
  function realTimeOf(clip, tw) {
    var s = clip.stop; if (!s || tw <= s.at) return clip.tick + tw;
    var lo = tw, hi = tw + s.len + s.catchup + 1;
    for (var i = 0; i < 30; i++) { var m = (lo + hi) / 2; if (warp(clip, m) < tw) lo = m; else hi = m; }
    return clip.tick + (lo + hi) / 2;
  }

  // ---------------------------------------------------------------- impacts and effect intents
  function pushEffect(e) { if (effects.length >= MAX_EFFECTS) { effects.shift(); stats.effectsDropped++; } effects.push(e); }
  function impactEffect(h, clip, kind) {
    var cls = MV.classes[clip.cls] || {}, m = clip.move, k = m.kick || cls.kick || { pitch: 0.4, yaw: 0, roll: 0.4 };
    var mult = (clip.crit ? 1.5 : 1) * (clip.fatal ? 1.25 : 1) * (clip.result === 'blocked' ? 0.6 : 1) * (0.55 + 0.45 * clip.amp) * (kind === 'upgrade' ? 0.5 : 1);
    var mirror = h.right === false ? -1 : 1;   // a left-hand swing kicks the other way
    return { kind: kind, hand: h.index === 0 ? 'M' : 'O', cls: clip.cls, move: clip.name, target: clip.target, crit: clip.crit, sweep: clip.sweep,
      fatal: clip.fatal, blocked: clip.result === 'blocked', confirmed: clip.result === 'hit' || clip.result === 'blocked',
      kick: { pitch: k.pitch * mult, yaw: (k.yaw || 0) * mult * mirror, roll: (k.roll || 0) * mult * mirror, back: (k.back || 0) * mult, ms: (cls.kickMs || 120) * (clip.crit ? 1.2 : 1), curve: k.curve || 'punch' },
      sparks: m.sparks || cls.sparks || 'blade', dir: m.dir ? [m.dir[0] * mirror, m.dir[1]] : [-mirror, -0.3], tint: h.desc && h.desc.tint || cls.color, at: lastNow };
  }
  function hitstopTicks(clip) {
    var cls = MV.classes[clip.cls] || {}, ms = (cls.hitstop || 45) + (clip.crit ? 25 : 0) + (clip.fatal ? 20 : 0);
    if (clip.result === 'blocked') ms = Math.min(ms, 45);
    return ms / 50 * knobs.hitstop;
  }
  function fireImpact(h, clip, T, withStop) {
    clip.impactFired = true; stats.impacts++;
    if (withStop && knobs.hitstop > 0) {
      var len = hitstopTicks(clip), room = clip.dur - clip.contact - len;
      if (len > 0.1 && room > 1) { clip.stop = { at: clip.contact, len: len, creep: 0.03, catchup: Math.min(4, room - 0.2) }; clip.realDur = clip.dur; stats.stops++; }
    }
    if (knobs.impacts) pushEffect(impactEffect(h, clip, 'impact'));
  }
  function impacts(T) {
    for (var hi = 0; hi < 2; hi++) {
      var h = hands[hi], c = h.strike;
      if (!c || c.impactDone || T < c.tick + c.contact) continue;
      c.impactDone = true;
      var predicted = c.target >= 0 && c.result !== 'miss', confirmed = c.result === 'hit' || c.result === 'blocked';
      if (predicted || confirmed) fireImpact(h, c, T, true);
    }
  }

  // ---------------------------------------------------------------- per frame
  function frame(T, now) {
    if (!Number.isFinite(T)) return;
    if (T === lastFrameT) return;
    if (lastFrameT !== null && T < lastFrameT - 2) clear();   // respawn or a new world: the tick count restarted
    lastFrameT = T; lastNow = Number.isFinite(now) ? now : Date.now();
    syncFeed(T, lastNow);
    pollMotion(T, lastNow);
    for (var hi = 0; hi < 2; hi++) {
      var h = hands[hi];
      resolve(h, T);
      promote(h, T);
      if (h.strike && T > h.strike.tick && !clipActive(h.strike, T)) {
        h.lastEnd = Math.max(h.lastEnd, realTimeOf(h.strike, h.strike.strokeEnd));
        retire(h, h.strike); h.strike = null;
      }
      if (h.support && T > h.support.tick && !supportActive(h.support, T)) h.support = null;
    }
    impacts(T);
  }
  // A hand's item for this frame (from the real stack, JasprMeleeDescriptor; cls null = not a melee weapon), which side
  // it is drawn on, and whether the off hand's attacks are enabled (dual wielding).
  function bind(hi, desc, right, now, dualOk) {
    var h = hands[hi], key = desc && desc.cls ? desc.key : null;
    if ((h.desc && h.desc.cls ? h.desc.key : null) !== key && (h.strike || h.support || h.queued)) {
      // another weapon (or none): the old swing is stale
      retire(h, h.strike); h.strike = null; h.queued = null; h.support = null; h.seq = 0;
    }
    h.desc = desc || { cls: null, key: null }; h.right = right !== false; h.boundAt = Number.isFinite(now) ? now : lastNow;
    if (dualOk !== undefined) h.dualOk = !!dualOk;
  }
  function sample(hi, energy, period) { var h = hands[hi]; if (Number.isFinite(energy)) h.energy = C.clamp(energy, 0, 1); if (period > 0 && Number.isFinite(period)) h.period = period; }
  function samplePlayer(sprint, ground, falling, now) { player.sprint = !!sprint; player.ground = !!ground; player.falling = !!falling; player.at = Number.isFinite(now) ? now : lastNow; }
  // The native pose for a hand at tick T, or null for the vanilla path. The engine asks twice per hand and frame (the
  // swing-offset hook and the transform hook), so the answer is kept for the frame.
  var poseCache = [{ T: NaN }, { T: NaN }];
  function poseFor(hi, T, right) {
    var h = hands[hi], cache = poseCache[hi];
    right = right !== false;
    if (cache.T === T && cache.right === right && cache.strike === h.strike && cache.support === h.support && cache.desc === h.desc) return cache.value;
    resolve(h, T); promote(h, T);
    var p = poseOfHand(h, T), out = null;
    if (p && p.phase !== 'rest') {
      var src = clipActive(h.strike, T) ? h.strike : h.support ? h.support.leader : null;
      var name = clipActive(h.strike, T) ? h.strike.name : 'support:' + (h.support ? h.support.style : '');
      var progress = src ? C.clamp((T - src.tick) / src.realDur, 0, 1) : 0;
      out = C.toNative(p, right, name, progress, p.phase, frameOf(h));
    }
    cache.T = T; cache.right = right; cache.strike = h.strike; cache.support = h.support; cache.desc = h.desc; cache.value = out;
    return out;
  }
  function sequence(hi) { var s = hands[hi].strike; return s ? s.seq : 0; }
  // keep the main grip fully raised (no vanilla cooldown dip) while the main hand holds a melee weapon
  function equipFull(id) { return !!(classOf(hands[0]) || IT.VANILLA[id] || IT.MUTANTS[id]); }
  function frameOf(h) { var f = h.desc && h.desc.frame; return f ? C.modelFrame(f) : null; }

  // ---------------------------------------------------------------- blade trails (geometry for JASPR_COMBATFX)
  // Samples the edge over the last trailTicks of the hand's own (warped) motion: a frame-rate independent ribbon that
  // follows the blade exactly, frozen (not collapsed) during hit-stop. Points are view space, right-hand convention.
  function trail(hi, T) {
    var h = hands[hi], c = h.strike;
    if (!knobs.trails || !clipActive(c, T)) return null;
    var cls = MV.classes[c.cls]; if (!cls || cls.trail === false) return null;
    var lt = warp(c, T - c.tick), from = c.tw + c.th - 0.2, to = c.strokeEnd + c.tr * 0.45;
    if (lt < from || lt > to + knobs.trailTicks) return null;
    var tr = c.move.trail || cls.trail || {}, a = tr.from || [0.45, 0.45, 0.5], b = tr.to || [0.93, 0.93, 0.5];
    var fr = frameOf(h), local = !!fr;
    if (fr) { a = fr.a; b = fr.b; }
    var N = knobs.trailSamples, L = knobs.trailTicks * (c.sweep ? 1.5 : 1) * (tr.len || 1), dt = L / N, pts = [], alive = 0, tips = [], bases = [];
    var flash = c.impactFired && c.stop ? C.clamp(1.4 - (lt - c.stop.at) / 3, 1, 1.4) : 1;
    for (var i = 0; i <= N + 1; i++) {
      var tw = Math.min(lt - dt * (i - 1), to);
      var pose = evalClip(c, Math.max(tw, 0));
      bases.push(C.framedPoint(pose, fr, a, local)); tips.push(C.framedPoint(pose, fr, b, local));
    }
    for (var j = 1; j <= N + 1; j++) {
      if (lt - dt * (j - 1) < from) break;
      // speed of the tip around this sample (blocks per tick): slow parts of the motion leave no trail
      var sp = C.len(C.sub(tips[j - 1], tips[Math.min(j + 1, N + 1)])) / (dt * (Math.min(j + 1, N + 1) - (j - 1)));
      var speedA = C.clamp((sp - 0.06) / 0.22, 0, 1);
      var alpha = (1 - (j - 1) / N) * speedA * flash * (c.crit ? 1.25 : 1) * (tr.alpha || 1);
      if (alpha > 0.02) alive++;
      var bp = bases[j], tp = tips[j];
      pts.push(bp[0], bp[1], bp[2], tp[0], tp[1], tp[2], C.clamp(alpha, 0, 1));
    }
    if (pts.length < 14 || !alive) return null;
    return { hand: hi, right: h.right !== false, n: pts.length / 7, pts: pts, color: (h.desc && h.desc.tint) || cls.color || [1, 1, 1],
      width: (tr.width || 1) * (c.sweep ? 1.7 : 1), crit: c.crit, sweep: c.sweep, cls: c.cls };
  }

  // ---------------------------------------------------------------- compatibility and diagnostics
  function family(id) { return IT.family(id); }
  function describe(id, nbt, damage) { return IT.describe(id, nbt, damage); }
  function clear() { hands = [newHand(0), newHand(1)]; effects.length = 0; stats.last = null; lastFrameT = null; }
  function record(p, main, id, sequence) {
    stats.draws++; stats.last = { clip: p.clip, progress: p.progress, phase: p.phase, main: main, id: id, sequence: sequence };
  }
  function drain() { var out = effects.slice(); effects.length = 0; return out; }
  function status() {
    var o = {}; for (var k in stats) o[k] = stats[k];
    o.hands = hands.map(function (h) { return { cls: h.desc ? h.desc.cls : null, strike: h.strike ? h.strike.name : null, support: h.support ? h.support.style : null, queued: !!h.queued, pending: h.pending.length, seq: h.seq }; });
    o.feed = !!feed(); o.feedSeq = feedSeq; o.knobs = knobs;
    return o;
  }
  // offline evaluation of one move from rest (previews and tests)
  function pose(name, progress, right, opts) {
    var mv = MV.moves[name]; if (!mv) return null;
    var cls = MV.classes[mv.cls], clip = makeClip(mv, cls, { tick: 0, period: opts && opts.period, amp: opts && opts.amp });
    var p = evalClip(clip, C.clamp(progress, 0, 1) * clip.dur), f = opts && opts.frame ? C.modelFrame(opts.frame) : null;
    return C.toNative(p, right !== false, name, progress, p.phase, f);
  }

  return { mark: mark, frame: frame, bind: bind, sample: sample, samplePlayer: samplePlayer, poseFor: poseFor, trail: trail, drain: drain,
    effects: effects, family: family, describe: describe, clear: clear, record: record, status: status, pose: pose, stats: stats,
    sequence: sequence, equipFull: equipFull, sampledAt: NaN,
    knobs: knobs, partial: 0, descriptors: new WeakMap(), moves: MV.moves, classes: MV.classes,
    clipNames: Object.keys(MV.moves),
    _test: { makeClip: makeClip, evalClip: evalClip, clipAt: clipAt, warp: warp, realTimeOf: realTimeOf, stateOf: stateOf, sideOf: sideOf,
      hands: function () { return hands; }, setFeed: function (f) { feedOverride = f; feedSeq = -1; }, makeSupport: makeSupport, supportAt: supportAt,
      force: function (hi, name) { forced[hi] = name; },
      timing: timing, amplitude: amplitude, player: player, observe: observe, constants: { COMBO_WINDOW: COMBO_WINDOW, BOTH_WINDOW: BOTH_WINDOW, MERGE_MS: MERGE_MS } } };
}(typeof JasprMeleeCore !== 'undefined' ? JasprMeleeCore : require('./melee-core.js'),
  typeof JasprMeleeMoves !== 'undefined' ? JasprMeleeMoves : require('./melee-moves.js'),
  typeof JasprMeleeItems !== 'undefined' ? JasprMeleeItems : require('./melee-items.js'),
  typeof JasprMeleeDual !== 'undefined' ? JasprMeleeDual : require('./melee-dual.js')));
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeMotion;
