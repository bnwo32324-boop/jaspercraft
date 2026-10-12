'use strict';
// Who calls a function in a .cpuprofile, and what it calls: time by caller chain (up to --depth frames) and by direct child.
//   node tests/fps-profile-callers.cjs <scene.cpuprofile> <functionName> [--depth 3]
const fs = require('node:fs');
const [file, target] = process.argv.slice(2);
const depth = +(process.argv.includes('--depth') ? process.argv[process.argv.indexOf('--depth') + 1] : 3);
const prof = JSON.parse(fs.readFileSync(file, 'utf8'));
const nodes = new Map(prof.nodes.map(n => [n.id, n])), parent = new Map();
for (const n of prof.nodes) for (const c of n.children || []) parent.set(c, n.id);
const counts = new Map();
for (let i = 0; i < prof.samples.length; i++) counts.set(prof.samples[i], (counts.get(prof.samples[i]) || 0) + (prof.timeDeltas[i + 1] || 0));
let busy = 0; for (const [id, t] of counts) if (nodes.get(id).callFrame.functionName !== '(idle)') busy += t;
const name = id => nodes.get(id).callFrame.functionName || '(anon)';
const skip = id => !nodes.get(id).callFrame.functionName;
const chains = new Map(), children = new Map();
let total = 0;
for (const [id, t] of counts) {
  // innermost occurrence of target on this sample's stack
  let cur = id, below = null, hit = null;
  while (cur !== undefined) { if (name(cur) === target) { hit = cur; break; } if (!skip(cur)) below = cur; cur = parent.get(cur); }
  if (hit === null) continue;
  total += t;
  const chain = [];
  for (let c = parent.get(hit), d = 0; c !== undefined && d < depth; c = parent.get(c)) { if (skip(c)) continue; chain.push(name(c)); d++; }
  const k = chain.join(' < ');
  chains.set(k, (chains.get(k) || 0) + t);
  const ch = below === null ? '(self)' : name(below);
  children.set(ch, (children.get(ch) || 0) + t);
}
console.log(target + ': ' + (100 * total / busy).toFixed(1) + '% of busy');
console.log('--- callers');
for (const [k, v] of [...chains].sort((a, b) => b[1] - a[1]).slice(0, 12)) console.log((100 * v / busy).toFixed(2).padStart(6) + '%  ' + k);
console.log('--- children');
for (const [k, v] of [...children].sort((a, b) => b[1] - a[1]).slice(0, 15)) console.log((100 * v / busy).toFixed(2).padStart(6) + '%  ' + k);
