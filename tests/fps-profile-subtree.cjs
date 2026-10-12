'use strict';
// Inclusive time of every function inside a function's subtree (outermost occurrence), as % of that subtree.
//   node tests/fps-profile-subtree.cjs <scene.cpuprofile> <functionName> [--top 40]
const fs = require('node:fs');
const [file, target] = process.argv.slice(2);
const TOP = +(process.argv.includes('--top') ? process.argv[process.argv.indexOf('--top') + 1] : 40);
const CLASSES = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const FNINDEX = 'C:/Users/AM/AppData/Local/Temp/claude/C--Users-AM-Desktop-Curser-Test-Jaspergers/4b6b9621-a7d3-4936-bfd4-485905c41eac/scratchpad/mutants/idx/fnindex.json';
const src = fs.readFileSync(CLASSES, 'latin1');
const table = new Map();
for (const m of src.matchAll(/"([A-Za-z_$][\w$]{0,4})","([a-zA-Z_][\w]*)"/g)) if (!table.has(m[1])) table.set(m[1], m[2]);
const idx = fs.existsSync(FNINDEX) ? JSON.parse(fs.readFileSync(FNINDEX, 'utf8')) : {};
const jn = fn => { const e = idx[fn], t = table.get(fn); const w = e ? Object.entries(e).filter(([f]) => !f.includes('teavm/classlib')).sort((a, b) => b[1][2] - a[1][2]).slice(0, 1).map(([f, r]) => f.split('/').pop().replace('.java', '') + ':' + r[0]).join('') : ''; return (t || '') + (w ? ' @' + w : ''); };
const prof = JSON.parse(fs.readFileSync(file, 'utf8'));
const nodes = new Map(prof.nodes.map(n => [n.id, n])), parent = new Map();
for (const n of prof.nodes) for (const c of n.children || []) parent.set(c, n.id);
const counts = new Map();
for (let i = 0; i < prof.samples.length; i++) counts.set(prof.samples[i], (counts.get(prof.samples[i]) || 0) + (prof.timeDeltas[i + 1] || 0));
const name = id => nodes.get(id).callFrame.functionName || '(anon)';
let total = 0; const incl = new Map(), self = new Map();
for (const [id, t] of counts) {
  const stack = []; for (let c = id; c !== undefined; c = parent.get(c)) stack.push(name(c));
  const at = stack.lastIndexOf(target);
  if (at < 0) continue;
  total += t;
  const seen = new Set();
  for (let i = 0; i < at; i++) if (!seen.has(stack[i])) { seen.add(stack[i]); incl.set(stack[i], (incl.get(stack[i]) || 0) + t); }
  self.set(stack[0], (self.get(stack[0]) || 0) + t);
}
console.log(target + ' subtree: ' + (total / 1000).toFixed(0) + ' ms');
console.log('--- inclusive within subtree');
for (const [k, v] of [...incl].sort((a, b) => b[1] - a[1]).slice(0, TOP)) console.log((100 * v / total).toFixed(1).padStart(5) + '%  ' + k.padEnd(26) + ' ' + jn(k));
console.log('--- self within subtree');
for (const [k, v] of [...self].sort((a, b) => b[1] - a[1]).slice(0, 25)) console.log((100 * v / total).toFixed(1).padStart(5) + '%  ' + k.padEnd(26) + ' ' + jn(k));
