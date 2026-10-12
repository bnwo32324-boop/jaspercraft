'use strict';
// Top functions of a Chrome .cpuprofile from tests/fps-bench.cjs, by self and by inclusive time, with Java names.
//   node tests/fps-profile-top.cjs <scene.cpuprofile> [--top 40] [--classes <classes.js>]
// Names: the client's own name table ("Fn","javaName" pairs in classes.js) and, when present, the source line index of the
// original bundle (fnindex.json: JS function -> Java file and lines).
const fs = require('node:fs'), path = require('node:path');
const argv = process.argv.slice(2);
const opt = (k, d) => { const i = argv.indexOf('--' + k); return i < 0 ? d : argv[i + 1]; };
const file = argv[0], TOP = +opt('top', 40);
const CLASSES = opt('classes', 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js');
// optional source-line index (TeaVM name -> Java method), e.g. from the Mutants port tooling; names fall back to the name table
const FNINDEX = process.env.FPS_FNINDEX || require('node:path').join(__dirname, '..', 'candidate', 'fnindex.json');
const src = fs.readFileSync(CLASSES, 'latin1');
const table = new Map();
for (const m of src.matchAll(/"([A-Za-z_$][\w$]{0,4})","([a-zA-Z_][\w]*)"/g)) if (!table.has(m[1])) table.set(m[1], m[2]);
const idx = fs.existsSync(FNINDEX) ? JSON.parse(fs.readFileSync(FNINDEX, 'utf8')) : {};
function javaName(fn) {
  const e = idx[fn], t = table.get(fn);
  const where = e ? Object.entries(e).filter(([f]) => !f.includes('teavm/classlib')).sort((a, b) => b[1][2] - a[1][2]).slice(0, 1).map(([f, r]) => f.split('/').pop().replace('.java', '') + ':' + r[0]).join('') : '';
  return (t ? t : '') + (where ? ' @' + where : '');
}
const prof = JSON.parse(fs.readFileSync(file, 'utf8'));
const nodes = new Map(prof.nodes.map(n => [n.id, n]));
const parent = new Map();
for (const n of prof.nodes) for (const c of n.children || []) parent.set(c, n.id);
// time per sample
const counts = new Map();
const dts = prof.timeDeltas;
for (let i = 0; i < prof.samples.length; i++) counts.set(prof.samples[i], (counts.get(prof.samples[i]) || 0) + (dts[i + 1] !== undefined ? dts[i + 1] : 0));
let total = 0; for (const v of counts.values()) total += v;
const self = new Map(), incl = new Map();
const key = n => n.callFrame.functionName || ('(' + (n.callFrame.url ? path.basename(n.callFrame.url) : 'native') + ')');
for (const [id, t] of counts) {
  const n = nodes.get(id);
  const k = key(n);
  self.set(k, (self.get(k) || 0) + t);
  // inclusive: once per distinct function on the stack
  const seen = new Set();
  for (let cur = id; cur !== undefined; cur = parent.get(cur)) { const kk = key(nodes.get(cur)); if (!seen.has(kk)) { seen.add(kk); incl.set(kk, (incl.get(kk) || 0) + t); } }
}
const idle = (self.get('(idle)') || 0) + (self.get('(program)') || 0) + (self.get('(garbage collector)') || 0);
const busy = total - (self.get('(idle)') || 0);
console.log('profile ' + path.basename(file) + ': ' + (total / 1000).toFixed(0) + ' ms sampled, busy ' + (100 * busy / total).toFixed(1) + '%, GC ' + (100 * (self.get('(garbage collector)') || 0) / total).toFixed(1) + '%, program ' + (100 * (self.get('(program)') || 0) / total).toFixed(1) + '%');
const row = (k, v) => (100 * v / busy).toFixed(1).padStart(5) + '%  ' + k.padEnd(28) + ' ' + javaName(k);
console.log('--- self time (% of busy)');
for (const [k, v] of [...self].sort((a, b) => b[1] - a[1]).slice(0, TOP)) console.log(row(k, v));
console.log('--- inclusive time (% of busy)');
for (const [k, v] of [...incl].sort((a, b) => b[1] - a[1]).slice(0, TOP)) console.log(row(k, v));
