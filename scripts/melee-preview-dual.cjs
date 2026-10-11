'use strict';
// Dual-wield choreography offline: both hands through the real timeline (support tracks, joint moves, trails).
// Usage:
//   node scripts/melee-preview-dual.cjs <out.png> <main> <off> [alt|joint|both]     one pair: alternating clicks and/or a joint strike
//   node scripts/melee-preview-dual.cjs --sheet <outDir> [main ...]               one sheet per main class, a row per off-hand class
const fs = require('node:fs'), path = require('node:path');
const R = require('./melee-preview-render.cjs'), S = require('./melee-preview-sim.cjs');
const M0 = require('../client-mods/melee/melee-load.cjs').load().JasprMeleeMotion;
const W = 192, H = 108;
// alternating: main, off, main, off at about each weapon's pace; joint: off joins the main's wind-up
function scriptFor(main, off, kind) {
  const pm = M0.classes[main].period, po = M0.classes[off].period, gap = Math.max(4, Math.min(pm, po) * 0.55);
  if (kind === 'joint') return { clicks: [{ hand: 'M', tick: 0, result: 'hit' }, { hand: 'O', tick: 1.5, result: 'hit' }], ticks: Math.max(pm, po) * 1.05 };
  return { clicks: [{ hand: 'M', tick: 0, result: 'hit' }, { hand: 'O', tick: gap, result: 'hit' }, { hand: 'M', tick: 2 * gap, result: 'hit' }, { hand: 'O', tick: 3 * gap, result: 'hit' }], ticks: 3 * gap + po * 0.9 };
}
function row(main, off, kind, cols) {
  const sc = scriptFor(main, off, kind), sim = S.play({ main: { cls: main }, off: { cls: off }, clicks: sc.clicks, ticks: sc.ticks, step: 0.25 });
  const a = R.previewItem(sim.main.art), b = R.previewItem(sim.off.art), frames = [], marks = [], names = new Set();
  for (let i = 0; i < cols; i++) {
    const t = i / (cols - 1) * sc.ticks, f = sim.frames[Math.min(sim.frames.length - 1, Math.round(t / 0.25))];
    frames.push(R.frame({ width: W, height: H, items: [{ ...a, side: 1, motion: f.poses[0] }, { ...b, side: -1, motion: f.poses[1] }], trails: f.trails }));
    marks.push(t / sc.ticks); f.names.forEach(n => n && names.add(n));
  }
  return { frames, marks, names: [...names] };
}
const args = process.argv.slice(2);
if (args[0] === '--sheet') {
  const outDir = args[1]; fs.mkdirSync(outDir, { recursive: true });
  const mains = args.length > 2 ? args.slice(2) : Object.keys(M0.classes);
  const offs = Object.keys(M0.classes).filter(c => c !== 'claw');
  for (const main of mains) {
    const frames = [], marks = [];
    for (const off of offs) { const r = row(main, off, 'alt', 10); frames.push(...r.frames); marks.push(...r.marks); }
    R.save(R.sheet(frames, 10, marks), path.join(outDir, 'dual-' + main + '.png'));
    console.log('wrote dual-' + main + '.png', offs.length, 'rows');
  }
} else {
  const [outFile, main, off, kind] = args, kinds = kind === 'both' || !kind ? ['alt', 'joint'] : [kind];
  const frames = [], marks = [];
  for (const k of kinds) { const r = row(main, off, k, 12); frames.push(...r.frames); marks.push(...r.marks); console.log(k, r.names.join(' | ')); }
  R.save(R.sheet(frames, 12, marks), outFile);
}
