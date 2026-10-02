'use strict';
// One row of 10 small frames per clip, for several clips at once. Usage: node scripts/melee-preview-overview.cjs out.png clip...
const fs = require('node:fs'), path = require('node:path');
const R = require('./melee-preview-render.cjs');
const motion = require('../client-mods/melee/melee-motion.js');
const [outFile, ...names] = process.argv.slice(2);
const item = { sword: 'diamond_sword', axe: 'iron_axe', heavy: 'iron_axe', spear: 'iron_sword', hook: 'iron_hoe', dagger: 'iron_sword', tool: 'iron_pickaxe' };
const meshes = {}, mesh = n => meshes[n] || (meshes[n] = R.itemMesh(R.decodePNG(fs.readFileSync(path.join(__dirname, '../candidate/melee-preview/assets/textures/items', n + '.png')))));
const family = n => Object.keys(motion.groups).find(g => motion.groups[g].includes(n));
const frames = [], marks = [];
for (const name of names) for (let i = 0; i < 10; i++) { const p = i / 9 * 0.999; frames.push(R.frame({ width: 192, height: 108, items: [{ mesh: mesh(item[family(name)]), side: 1, motion: motion.pose(name, p, true) }] })); marks.push(p); }
R.save(R.sheet(frames, 10, marks), outFile);
