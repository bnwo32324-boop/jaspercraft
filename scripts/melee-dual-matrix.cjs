'use strict';
// Prints the dual-wield choreography of every main x off-hand class pair as Markdown (the matrix in
// MELEE_ANIMATION_V5.md is this output). Usage: node scripts/melee-dual-matrix.cjs [--check [MELEE_ANIMATION_V5.md]]
const fs = require('node:fs'), path = require('node:path');
const L = require('../client-mods/melee/melee-load.cjs').load(), D = L.JasprMeleeDual, M = L.JasprMeleeMotion;
const STYLE_WORDS = { guard: 'guards', cock: 'cocks back', counter: 'counter-turns', flow: 'flows with it', tuck: 'tucks in', brace: 'braces', ready: 'readies its point', duck: 'ducks under' };
function table() {
  const rows = ['| Main | Off | Choreography | Off hand while main strikes | Main hand while off strikes | Off joins with | Main joins with |', '| --- | --- | --- | --- | --- | --- | --- |'];
  for (const c of D.matrix()) {
    const join = (n, h) => n + (h ? ' (held ' + h + ' t)' : '');
    rows.push('| ' + c.main + ' | ' + c.off + ' | ' + (c.signature ? '**' + c.name + '**' : c.name) + ' | ' + STYLE_WORDS[c.offSupport] + ' | ' + STYLE_WORDS[c.mainSupport] +
      ' | ' + join(c.offJoins, c.offJoinHold) + ' | ' + join(c.mainJoins, c.mainJoinHold) + (c.offChain ? ' (off chain: ' + c.offChain.join(', ') + ')' : '') + ' |');
  }
  return rows.join('\n') + '\n';
}
if (require.main === module) {
  const i = process.argv.indexOf('--check');
  if (i > 0) {
    const file = process.argv[i + 1] || path.join(__dirname, '..', 'MELEE_ANIMATION_V5.md');
    const doc = fs.readFileSync(file, 'utf8'), want = table();
    const ok = doc.includes(want);
    console.log(ok ? 'the matrix in ' + file + ' matches the code' : 'the matrix in ' + file + ' is STALE');
    process.exitCode = ok ? 0 : 1;
  } else process.stdout.write(table());
}
module.exports = { table, STYLE_WORDS };
