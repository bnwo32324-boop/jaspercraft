'use strict';
// One row of small frames per move (real timeline, trails and hit-stop included), for many moves at once.
// Usage: node scripts/melee-preview-overview.cjs out.png <move|class:<name>> ... [--item <art>] [--cols 12]
const path = require('node:path');
const R = require('./melee-preview-render.cjs'), S = require('./melee-preview-sim.cjs');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf(n); return i >= 0 ? args.splice(i, 2)[1] : null; };
const itemArg = opt('--item'), cols = Number(opt('--cols') || 12);
const [outFile, ...specs] = args;
const M0 = require('../client-mods/melee/melee-load.cjs').load().JasprMeleeMotion;
const names = [];
for (const s of specs) {
  if (s.startsWith('class:')) { const c = s.slice(6); for (const n of M0.clipNames) if (M0.moves[n].cls === c) names.push(n); }
  else names.push(s);
}
const frames = [], marks = [];
for (const name of names) {
  const mv = M0.moves[name], cls = M0.classes[mv.cls];
  const apoc = itemArg && M0.describe(276, '{JasprApocalypse:{id:"' + itemArg.replace(/^apocalypse_/, '') + '"}}', 0).cls ? itemArg.replace(/^apocalypse_/, '') : null;
  const main = apoc ? { id: 276, nbt: '{JasprApocalypse:{id:"' + apoc + '"}}', art: 'apocalypse_' + apoc } : { cls: mv.cls, art: itemArg || cls.preview };
  const probe = S.play({ main, clicks: [{ tick: 0, move: name, result: 'hit' }], ticks: 0.5, step: 0.25 });
  const dur = probe.M._test.hands()[0].strike.realDur;
  const sim = S.play({ main, clicks: [{ tick: 0, move: name, result: 'hit' }], ticks: dur + 0.25, step: 0.25 });
  const item = R.previewItem(sim.main.art);
  for (let i = 0; i < cols; i++) {
    const t = i / (cols - 1) * dur, f = sim.frames[Math.min(sim.frames.length - 1, Math.round(t / 0.25))];
    frames.push(R.frame({ width: 192, height: 108, items: [{ ...item, side: 1, motion: f.poses[0] }], trails: [f.trails[0]] }));
    marks.push(t / dur);
  }
}
R.save(R.sheet(frames, cols, marks), outFile);
console.log('wrote', outFile, names.length, 'moves');
