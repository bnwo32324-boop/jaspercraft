'use strict';
// JasperCraft first-person melee v5 (owner request 2026-10-11): replaces ONLY the JASPR_MELEE_FP module region nested in
// the Better Combat head of the LIVE classes.js (read only) with client-mods/melee (core, model frames, items, moves,
// dual choreography, motion, native bridges). Every byte outside the region stays as it is: no other stage (Mo' Bends,
// the outer stages, the Better Combat head itself) needs to come off for a module swap. The builder checks the engine
// call sites and the native names the module uses, parses the result (vm.Script) and proves that putting the previous
// module back gives the input byte for byte. latin1 in and out; output goes to candidate/melee-client/.
// Usage: node scripts/build-melee-client.cjs [input classes.js]      (default: the live site/classes.js)
//        node scripts/build-melee-client.cjs --restore <built classes.js> <previous-module.js>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const loader = require('../client-mods/melee/melee-load.cjs');
const { declared } = require('./build-mutants-client.cjs');
const { engineNames } = require('./stage-names.cjs');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_MELEE_FP_BEGIN */', END = '/* JASPR_MELEE_FP_END */';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
// what the engine and the Better Combat head call in this module (signatures fixed by their call sites)
const EXPORTS = ['JasprEpicSwingOffset', 'JasprEpicEquipMatch', 'JasprEpicEquipFull', 'JasprMeleeEmptyArm', 'JasprMeleeDescriptor', 'JasprEpicPose'];
const CALL_SITES = ['JasprMeleeMotion.partial=c;', 'JasprEpicSwingOffset(a,j,f,g,i,c,r,s)', 'JasprCombatTransform(a,j,f,g,i)', 'JasprMeleeEmptyArm(a,h,f,j)',
  'JasprEpicEquipMatch(b,f,g,1)', 'JasprEpicEquipFull(b,g)', 'JasprEpicEquipMatch(b,f,j,0)'];
// names the module may use that the module region itself or the Better Combat head provide
const OWN = new Set(['JasprMeleeCore', 'JasprMeleeModelTable', 'JasprMeleeItems', 'JasprMeleeMoves', 'JasprMeleeDual', 'JasprMeleeMotion',
  'JasprCombatFeed', 'JasprBetterCombat', 'JasprCombatSpecial', 'require', 'module', '$rt_globals', '$rt_str', '$rt_ustr'].concat(EXPORTS));
function moduleText() {
  const body = loader.body();
  if (/[^\x00-\x7f]/.test(body)) throw Error('the melee module must be ASCII');
  return BEGIN + ' // ' + JSON.stringify({ v: 5, sha256: sha(body) }) + '\n' + body + '\n' + END;
}
function region(s) {
  const a = s.indexOf(BEGIN), b = a < 0 ? -1 : s.indexOf(END, a);
  if (a < 0 || b < 0) throw Error('the first-person melee module region was not found');
  if (s.indexOf(BEGIN, a + 1) >= 0 || s.indexOf(END, b + 1) >= 0) throw Error('more than one first-person melee module region');
  return [a, b + END.length];
}
function build(input) {
  const [a, b] = region(input), previous = input.slice(a, b), text = moduleText();
  for (const site of CALL_SITES) if (!input.includes(site)) throw Error('engine call site changed: ' + site);
  const output = input.slice(0, a) + text + input.slice(b);
  // the module defines what the engine calls, and every other name it uses exists in this client
  for (const name of EXPORTS) if (!new RegExp('\\nfunction ' + name + '\\(').test(text)) throw Error('module lacks ' + name);
  const outside = output.slice(0, a) + output.slice(a + text.length);
  const missing = engineNames(text).filter(n => !OWN.has(n) && !declared(outside, n));
  if (missing.length) throw Error('native names missing from this client: ' + missing.join(', '));
  new vm.Script(output, { filename: 'classes.js' });
  if (restore(output, previous) !== input) throw Error('reversibility check failed');
  return { output, previous, natives: engineNames(text).filter(n => !OWN.has(n)) };
}
// puts a previous module back (the inverse of build)
function restore(output, previous) {
  const [a, b] = region(output);
  if (!previous.startsWith(BEGIN) || !previous.endsWith(END)) throw Error('not a module region');
  return output.slice(0, a) + previous + output.slice(b);
}
if (require.main === module) {
  const dir = path.join(root, 'candidate/melee-client');
  fs.mkdirSync(dir, { recursive: true });
  if (process.argv[2] === '--restore') {
    const out = restore(fs.readFileSync(process.argv[3], 'latin1'), fs.readFileSync(process.argv[4], 'latin1'));
    new vm.Script(out, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.restored.js'), out, 'latin1');
    console.log('restored: ' + path.join(dir, 'classes.restored.js') + ' sha256 ' + sha(out));
    process.exit(0);
  }
  const inputPath = process.argv[2] || LIVE, input = fs.readFileSync(inputPath, 'latin1');
  const { output, previous, natives } = build(input);
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  fs.writeFileSync(path.join(dir, 'previous-module.js'), previous, 'latin1');
  const manifest = { input: inputPath, inputSHA256: sha(input), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'),
    moduleBytes: Buffer.byteLength(moduleText(), 'latin1'), previousSHA256: sha(previous), natives: natives.length, builtAt: new Date().toISOString(),
    next: 'node scripts/build-combatfx-client.cjs candidate/melee-client/classes.js' };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, restore, moduleText, region, BEGIN, END, EXPORTS, CALL_SITES, LIVE, sha };
