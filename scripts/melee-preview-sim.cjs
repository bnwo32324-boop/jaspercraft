'use strict';
// Plays first-person melee timelines offline through the module's real API (bind, mark, the combat feed, frame,
// poseFor, trail, drain) for previews and tests: no game, no browser.
const loader = require('../client-mods/melee/melee-load.cjs');

// a hand's item: a class with its preview art, or a real item (id + NBT) as the client would describe it
function itemFor(L, spec) {
  if (!spec) return null;
  const M = L.JasprMeleeMotion;
  if (spec.nbt || spec.id) return { desc: M.describe(spec.id || 276, spec.nbt || null, spec.damage || 0), art: spec.art };
  const cls = M.classes[spec.cls];
  if (!cls) throw Error('unknown class ' + spec.cls);
  return { desc: { cls: spec.cls, key: 'preview:' + spec.cls, id: 276, family: spec.cls, tint: spec.tint || null }, art: spec.art || cls.preview };
}
// script: { main: {cls}|{id,nbt}, off: ..., clicks: [{hand:'M'|'O', tick, target, result, crit, sweep}], ticks, step, energy }
function play(script) {
  const L = loader.load(), M = L.JasprMeleeMotion;
  const main = itemFor(L, script.main), off = itemFor(L, script.off);
  const events = [], pending = [];
  let seq = 0, nowMs = 0;
  // the feed is append-only in arrival order: the server's verdict is appended a round trip after the swing (rtt ms)
  const arrive = () => { pending.sort((a, b) => a.visibleAt - b.visibleAt); while (pending.length && pending[0].visibleAt <= nowMs) { const e = pending.shift(); e.seq = ++seq; events.push(e); } };
  M._test.setFeed({ since: s => { arrive(); return events.filter(e => e.seq > s); } });
  M.clear();
  const step = script.step || 0.5, total = script.ticks || 24, frames = [], effects = [];
  const clicks = (script.clicks || []).slice().sort((a, b) => a.tick - b.tick);
  let next = 0;
  const T0 = 1000;
  for (let t = 0; t <= total + 1e-9; t += step) {
    const T = T0 + t, now = T * 50;
    nowMs = now;
    // the hands are drawn every frame (descriptors bound) before clicks are handled at a tick boundary
    if (main) M.bind(0, main.desc, true, now, !!off);
    if (off) M.bind(1, off.desc, false, now, true);
    M.sample(0, script.energy == null ? 1 : script.energy, 0); M.sample(1, script.energy == null ? 1 : script.energy, 0);
    while (next < clicks.length && clicks[next].tick <= t + 1e-9) {
      const c = clicks[next++], hand = c.hand || 'M', cv = Math.floor(T0 + c.tick) - 1, at = (cv + 1) * 50;
      if (c.sprint != null || c.jump) M.samplePlayer(!!c.sprint, !c.jump, !!c.jump, at);
      if (c.move) M._test.force(hand === 'O' ? 1 : 0, c.move);
      if (c.energy != null) M.sample(hand === 'O' ? 1 : 0, c.energy, 0);
      M.mark(hand === 'O', 276, at, cv);
      if (!script.noFeed) {
        // as workstream A's head: the 'swing' (tick = the player's tick count at the click), then the verdict (ref = the swing)
        const sw = { seq: ++seq, at, hand, kind: 'swing', item: 276, target: c.target == null ? 7 : c.target, crit: false, sweep: false, fatal: false, ref: -1, tick: cv };
        events.push(sw);
        if (c.result) pending.push({ at: at + (c.rtt || 60), visibleAt: at + (c.rtt || 60), hand, kind: c.result, item: 276, target: c.target == null ? 7 : c.target, crit: !!c.crit, sweep: !!c.sweep, fatal: !!c.fatal, ref: c.noRef ? -1 : sw.seq, tick: cv });
      }
      M.samplePlayer(false, true, false, at);
    }
    M.frame(T, now);
    const poses = [main ? M.poseFor(0, T, true) : null, off ? M.poseFor(1, T, false) : null];
    const trails = [main ? M.trail(0, T) : null, off ? M.trail(1, T) : null];
    for (const e of M.drain()) effects.push(Object.assign({ t }, e));
    const st = M._test.hands();
    frames.push({ t, T, poses, trails, names: [st[0].strike ? st[0].strike.name : st[0].support ? 'support:' + st[0].support.style : null, st[1].strike ? st[1].strike.name : st[1].support ? 'support:' + st[1].support.style : null] });
  }
  return { L, M, frames, effects, main, off };
}
module.exports = { play, itemFor };
