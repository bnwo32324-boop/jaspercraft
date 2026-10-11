'use strict';
// Numbers behind the previews: for every move (or the given ones), how close the weapon comes to the eye, how much of
// the clip its working end is on screen, where it is at contact, and how fast the stroke is. Used to tune the moves and
// by the bounds tests. Usage: node scripts/melee-preview-metrics.cjs [move|class:<name> ...]
const L = require('../client-mods/melee/melee-load.cjs').load(), M = L.JasprMeleeMotion, C = L.JasprMeleeCore, T = M._test;
// sprite corners and blade line, item space
const CORNERS = [[0, 0, 0.47], [1, 0, 0.47], [1, 1, 0.47], [0, 1, 0.47], [0, 0, 0.53], [1, 0, 0.53], [1, 1, 0.53], [0, 1, 0.53]];
const HEAD = [0.8, 0.8, 0.5], MID = [0.6, 0.6, 0.5];
function onScreen(p) { const s = C.screen(p); return s[2] > 0.05 && Math.abs(s[0]) <= 1 && Math.abs(s[1]) <= 1; }
function metrics(name) {
  const mv = M.moves[name], cls = M.classes[mv.cls], clip = T.makeClip(mv, cls, { tick: 0 });
  const n = 240; let minDepth = Infinity, minAt = 0, vis = 0, swipe = mv.stroke.kind === 'swipe', topSpeed = 0, prevHead = null, fastest = null;
  for (let i = 0; i <= n; i++) {
    const t = clip.dur * i / n, pose = T.evalClip(clip, t);
    const tr = mv.trail || cls.trail || {}, pts = [[0.22, 0.2, 0.5], [0.45, 0.45, 0.5], MID, HEAD, [0.93, 0.93, 0.5]].concat(tr.from ? [tr.from, tr.to] : []);
    for (const c of (swipe ? CORNERS : pts)) { const p = C.itemPoint(pose, c); if (-p[2] < minDepth) { minDepth = -p[2]; minAt = t; } }
    const head = C.itemPoint(pose, HEAD);
    if (onScreen(head) || onScreen(C.itemPoint(pose, MID))) vis++;
    if (prevHead) { const sp = C.len(C.sub(head, prevHead)) / (clip.dur / n); if (sp > topSpeed) { topSpeed = sp; fastest = pose.phase; } }
    prevHead = head;
  }
  const at = T.evalClip(clip, clip.contact), mid = C.screen(C.itemPoint(at, MID)), tip = C.screen(C.itemPoint(at, HEAD));
  return { name, cls: mv.cls, dur: +clip.dur.toFixed(1), contact: +clip.contact.toFixed(2), minDepth: +minDepth.toFixed(3), minAt: +(minAt / clip.dur).toFixed(2),
    visible: +(vis / (n + 1)).toFixed(2), contactMid: mid.map(v => +v.toFixed(2)), contactTip: tip.map(v => +v.toFixed(2)), topSpeed: +topSpeed.toFixed(2), fastest, swipe };
}
if (require.main === module) {
  const specs = process.argv.slice(2), names = [];
  if (!specs.length) names.push(...M.clipNames);
  for (const s of specs) { if (s.startsWith('class:')) { for (const n of M.clipNames) if (M.moves[n].cls === s.slice(6)) names.push(n); } else names.push(s); }
  for (const n of names) {
    const m = metrics(n), warn = [];
    if (m.minDepth < 0.3) warn.push('CLOSE'); if (m.visible < 0.6) warn.push('HIDDEN'); if (m.fastest !== 'stroke') warn.push('SLOWSTROKE');
    console.log(n.padEnd(20), 'dur', String(m.dur).padStart(5), 'contact', String(m.contact).padStart(5), 'minDepth', m.minDepth.toFixed(2), '@' + m.minAt,
      'vis', m.visible.toFixed(2), 'mid@contact', JSON.stringify(m.contactMid), 'top', m.topSpeed, warn.join(' '));
  }
}
module.exports = { metrics };
