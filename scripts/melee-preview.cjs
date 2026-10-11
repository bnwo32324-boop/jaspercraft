'use strict';
// Renders first-person melee moves offline through the real timeline (bind, click, combat feed, frame, poseFor, trail):
// a strip of frames per move with its blade trail and hit-stop, and a path plot (blade tip on screen at equal time steps,
// yellow wind-up, orange loaded pause, red stroke, blue recovery; wide spacing = fast).
// Usage: node scripts/melee-preview.cjs [outDir] [move ...] [--item <art or apocalypse id>] [--miss]
const fs = require('node:fs'), path = require('node:path');
const R = require('./melee-preview-render.cjs'), S = require('./melee-preview-sim.cjs');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf(n); return i >= 0 ? args.splice(i, 2)[1] : null; };
const flag = n => { const i = args.indexOf(n); if (i >= 0) { args.splice(i, 1); return true; } return false; };
const itemArg = opt('--item'), miss = flag('--miss');
const out = args[0] || path.join(__dirname, '../candidate/melee-preview/out');
const L0 = require('../client-mods/melee/melee-load.cjs').load(), M0 = L0.JasprMeleeMotion;
const names = args.length > 1 ? args.slice(1) : M0.clipNames;
fs.mkdirSync(out, { recursive: true });
const W = 320, H = 180;
for (const name of names) {
  const mv = M0.moves[name]; if (!mv) { console.log('no move', name); continue; }
  const cls = M0.classes[mv.cls];
  const apoc = itemArg && /^apocalypse_|^[a-z_]+$/.test(itemArg) && M0.describe(276, '{JasprApocalypse:{id:"' + itemArg.replace(/^apocalypse_/, '') + '"}}', 0).cls ? itemArg.replace(/^apocalypse_/, '') : null;
  const main = apoc ? { id: 276, nbt: '{JasprApocalypse:{id:"' + apoc + '"}}', art: 'apocalypse_' + apoc } : { cls: mv.cls, art: itemArg || cls.preview };
  const sim = S.play({ main, clicks: [{ hand: 'M', tick: 0, move: name, result: miss ? 'miss' : 'hit', target: miss ? -1 : 7 }], ticks: 0, step: 0.25 });
  const clip = sim.M._test.hands()[0].strike || sim.M._test.hands()[0].recent && sim.M._test.hands()[0].recent[0];
  const dur = clip ? clip.realDur + 0.5 : 14;
  const sim2 = S.play({ main, clicks: [{ hand: 'M', tick: 0, move: name, result: miss ? 'miss' : 'hit', target: miss ? -1 : 7 }], ticks: dur, step: 0.25 });
  const item = R.previewItem(sim2.main.art);
  const N = 16, frames = [], marks = [];
  for (let i = 0; i < N; i++) {
    const t = i / (N - 1) * dur, f = sim2.frames[Math.min(sim2.frames.length - 1, Math.round(t / 0.25))];
    frames.push(R.frame({ width: W, height: H, items: f.poses[0] ? [{ ...item, side: 1, motion: f.poses[0] }] : [{ ...item, side: 1, motion: null }], trails: [f.trails[0]] }));
    marks.push(t / dur);
  }
  R.save(R.sheet(frames, 8, marks), path.join(out, name + '.png'));
  // path plot at 2x: the tip of the blade on screen every 0.25 tick
  const c = R.frame({ width: W * 2, height: H * 2, items: [{ ...item, side: 1, motion: null }] }), proj = R.M.perspective(70, W / H, 0.05, 50);
  const colors = { windup: [255, 220, 0], hold: [255, 140, 0], stroke: [230, 40, 40], recover: [40, 90, 255] };
  const table = require('../client-mods/melee/melee-models.js'), mt = apoc && table[apoc];
  const tipLocal = item.mesh.model && mt ? mt.tip.map(v => v / 16) : [0.93, 0.93, 0.5];
  for (const f of sim2.frames) {
    const pose = f.poses[0]; if (!pose) continue;
    const hm = R.handMatrix(1, pose, 0, item.display), tip = R.project(proj, R.M.mul(hm, R.M.id()), tipLocal, W * 2, H * 2);
    if (!tip.behind) c.dot(tip.x, tip.y, 2, colors[pose.phase] || [255, 255, 255]);
  }
  R.save(c, path.join(out, name + '-path.png'));
  const fx = sim2.effects.filter(e => e.kind === 'impact');
  console.log(name.padEnd(20), 'dur', dur.toFixed(1), 'contact', clip ? clip.contact.toFixed(2) : '-', 'impacts', fx.length, fx[0] ? 'kick ' + JSON.stringify(fx[0].kick) : '');
}
