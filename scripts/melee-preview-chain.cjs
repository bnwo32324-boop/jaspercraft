'use strict';
// Renders a combo the way the game plays it: each attack starts from the weapon's current pose and velocity.
// Usage: node scripts/melee-preview-chain.cjs <out.png> <family> <clickTick,clickTick,...> [ticksTotal]
const fs = require('node:fs'), path = require('node:path');
const R = require('./melee-preview-render.cjs');
const motion = require('../client-mods/melee/melee-motion.js');
const [outFile, family, clicksArg, totalArg] = process.argv.slice(2);
const clicks = clicksArg.split(',').map(Number), total = Number(totalArg || clicks[clicks.length - 1] + 12);
const item = { sword: 'diamond_sword', axe: 'iron_axe', heavy: 'iron_axe', spear: 'iron_sword', hook: 'iron_hoe', dagger: 'iron_sword', tool: 'iron_pickaxe' }[family];
const mesh = R.itemMesh(R.decodePNG(fs.readFileSync(path.join(__dirname, '../candidate/melee-preview/assets/textures/items', item + '.png'))));
const id = { sword: 276, axe: 258, heavy: 258, spear: 267, hook: 292, dagger: 267, tool: 257 }[family];
motion.clear();
const frames = [], marks = [], step = 0.5, names = [];
let next = 0;
for (let t = 0; t <= total; t += step) {
  while (next < clicks.length && clicks[next] <= t + 1e-9) { motion.mark(false, id, clicks[next] * 50, clicks[next]); const h = motion._test.hands()[0]; if (family !== motion.family(id)) motion.restyle(h, { family, key: 'preview-' + family, id }); names.push(h.clip); next++; }
  const h = motion._test.hands()[0];
  let pose = null;
  if (h) { const p = motion.phase(h, t); if (p !== null && p < 1) pose = motion.sample(h, p, true); }
  frames.push(R.frame({ width: 240, height: 135, items: [{ mesh, side: 1, motion: pose }] }));
  marks.push(t / total);
}
R.save(R.sheet(frames, 10, marks), outFile);
console.log(names.join(' -> '), 'frames', frames.length, 'chained', motion.stats.chained);
