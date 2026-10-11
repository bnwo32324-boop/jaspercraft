'use strict';
// Renders a combo the way the game plays it (real timeline: each strike flows out of the weapon's current pose and
// speed; trails and hit-stop included). Usage:
//   node scripts/melee-preview-chain.cjs <out.png> <class> <clickTick,clickTick,...> [ticksTotal] [--off <class> <ticks,...>]
const R = require('./melee-preview-render.cjs'), S = require('./melee-preview-sim.cjs');
const args = process.argv.slice(2), oi = args.indexOf('--off');
const off = oi >= 0 ? args.splice(oi, 3).slice(1) : null;
const [outFile, cls, clicksArg, totalArg] = args;
const clicks = clicksArg.split(',').map(Number).map(t => ({ hand: 'M', tick: t, result: 'hit' }));
if (off) for (const t of off[1].split(',').map(Number)) clicks.push({ hand: 'O', tick: t, result: 'hit' });
const last = Math.max(...clicks.map(c => c.tick)), total = Number(totalArg || last + 16);
const sim = S.play({ main: { cls }, off: off ? { cls: off[0] } : null, clicks, ticks: total, step: 0.5 });
const a = R.previewItem(sim.main.art), b = sim.off ? R.previewItem(sim.off.art) : null, frames = [], marks = [], names = [];
for (const f of sim.frames) {
  const items = [{ ...a, side: 1, motion: f.poses[0] }];
  if (b) items.push({ ...b, side: -1, motion: f.poses[1] });
  frames.push(R.frame({ width: 240, height: 135, items, trails: f.trails }));
  marks.push(f.t / total);
  for (const n of f.names) if (n && names[names.length - 1] !== n) names.push(n);
}
R.save(R.sheet(frames, 10, marks), outFile);
console.log(names.join(' -> '), '| frames', frames.length, '| impacts', sim.effects.filter(e => e.kind === 'impact').length);
