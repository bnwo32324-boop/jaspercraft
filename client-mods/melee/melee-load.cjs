'use strict';
// Loads the first-person melee module exactly as the client's JASPR_MELEE_FP region concatenates it (one shared scope,
// the same file order, the CommonJS export lines stripped), for the builder, the tests and the offline previews.
const fs = require('node:fs'), path = require('node:path');
const FILES = ['melee-core.js', 'melee-models.js', 'melee-items.js', 'melee-moves.js', 'melee-dual.js', 'melee-motion.js', 'melee-native.js'];
const NAMES = ['JasprMeleeCore', 'JasprMeleeModelTable', 'JasprMeleeItems', 'JasprMeleeMoves', 'JasprMeleeDual', 'JasprMeleeMotion'];
function read(f) { return fs.readFileSync(path.join(__dirname, f), 'utf8').replace(/\r\n/g, '\n'); }
// the region body (ASCII, LF): every file, minus its trailing CommonJS export line
function body() {
  return FILES.map(f => read(f).replace(/\nif \(typeof module !== 'undefined' && module\.exports\) module\.exports = [A-Za-z]+;\n?$/, '\n')).join('\n');
}
// evaluate in this realm (plain objects and arrays compare normally in tests); engine names stay unresolved until called
function load(globals) {
  const g = globals || {};
  const fn = new Function('$rt_globals', body() + '\nreturn {' + NAMES.map(n => n + ':' + n).join(',') + '};');
  return fn(g.$rt_globals || {});
}
module.exports = { FILES, NAMES, body, load };
