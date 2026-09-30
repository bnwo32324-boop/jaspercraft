'use strict';

// Exact fix for the composed browser client (site/classes.js): right-clicking a nitwit villager crashed the game with
// "Cannot read properties of null (reading 'bl')" in EntityVillager.processInteract. The TeaVM build's trade table
// (EntityVillager.bootstrap, EvX) lists only five professions (farmer .. butcher); a nitwit (profession 5: Atlas
// householders, elders, children and some captives, JasprNether nitwits, vanilla village nitwits) reads past its end,
// populateBuyingList (DF_) throws inside its own catch-all before it creates the list, buyingList stays null and
// processInteract calls buyingList.isEmpty(). getDisplayName (Fy8) repeated the failing call every time for an unnamed
// nitwit. This stage makes DF_ create the (empty) list first and stop cleanly for a profession without a trade table,
// as vanilla does for nitwits: the list stays empty, so the click falls through to EntityLiving's handling and the
// server decides what happens (trading, Atlas dialogue). Every anchor must match exactly once, or the build stops.
// Idempotent; `revert` restores the input byte for byte.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');

const MARKER = '/*JASPR_VILLAGER_V1*/';
const EDITS = [
  {
    label: 'populateBuyingList creates the list before any lookup',
    before: 'a=$T.l();}_:while(true){switch($p){case 0:try{$p=1;continue _;}catch($$e){$$je=F($$e);if($$je instanceof K){}else{throw $$e;}}return;case 1:try{AMu();',
    after: 'a=$T.l();}_:while(true){switch($p){case 0:' + MARKER + 'if(a.v4===null)a.v4=Hdv();try{$p=1;continue _;}catch($$e){$$je=F($$e);if($$je instanceof K){}else{throw $$e;}}return;case 1:try{AMu();',
  },
  {
    label: 'a profession without a trade table keeps the empty list',
    before: 'case 3:a:{try{$z=ACy(b,c);if(B()){break _;}b=$z;b=b;if(a.Yt){',
    after: 'case 3:a:{try{$z=ACy(b,c);if(B()){break _;}b=$z;b=b;if(!b)return;if(a.Yt){',
  },
];

/** The code the fix relies on: DF_ is populateBuyingList, Hdv builds a MerchantRecipeList, processInteract (Ew1)
 * populates a null list and then asks it isEmpty (E3i), and the trade table has five professions. */
const CHECKS = [
  ['Hdv', 'function Hdv(){var a=new Bit();GPm(a);return a;}', 'Hdv constructs the MerchantRecipeList'],
  ['DF_', 'b=KFH;$p=2;continue _;', 'populateBuyingList reads the trade table'],
  ['DF_', '$z=FRt(a);if(B()){break _;}c=$z;$p=3;continue _;', 'populateBuyingList looks up the profession'],
  ['FRt', 'return B6(c.bn%6|0,0);', 'getProfession returns 0..5'],
  ['ACy', 'function ACy(a,b){return a.clU.data[b];}', 'the trade table lookup has no bounds check'],
  ['Ew1', 'if(a.v4===null){$p=13;continue _;}', 'processInteract populates a null list'],
  ['Ew1', 'case 13:DF_(a);if(B()){break _;}', 'processInteract calls populateBuyingList'],
  ['Ew1', 'case 16:$z=E3i(e);if(B()){break _;}', 'processInteract asks the list isEmpty'],
  ['EvX', 'b=G(IF,5);', 'the trade table lists five professions'],
];

function functionBody(text, name) {
  const start = text.indexOf('function ' + name + '(');
  if (start < 0 || text.indexOf('function ' + name + '(', start + 1) >= 0) throw new Error(name + ' must be defined once');
  const end = text.indexOf('\nfunction ', start + 10);
  return text.slice(start, end < 0 ? text.length : end);
}

const count = (text, needle) => text.split(needle).length - 1;

function isFixed(text) {
  const body = functionBody(text, 'DF_');
  return EDITS.every(e => body.includes(e.after));
}

function build(input) {
  for (const [name, anchor, label] of CHECKS) if (!functionBody(input, name).includes(anchor)) throw new Error(label + ' (' + name + ') changed: re-audit');
  if (isFixed(input)) {
    if (count(input, MARKER) !== 1) throw new Error('the villager fix must be installed exactly once');
    return input;
  }
  if (count(input, MARKER)) throw new Error('partly installed villager fix: re-audit');
  const body = functionBody(input, 'DF_');
  let fixed = body;
  for (const e of EDITS) {
    if (count(fixed, e.before) !== 1) throw new Error(e.label + ': anchor must occur exactly once in DF_ (re-audit)');
    fixed = fixed.replace(e.before, () => e.after);
  }
  const output = input.replace(body, () => fixed);
  if (!isFixed(output)) throw new Error('fix did not apply');
  new vm.Script(output, {filename: 'villager-client/classes.js'});
  return output;
}

/** Undoes the fix (for the byte-for-byte reversal check). */
function revert(output) {
  if (!isFixed(output)) throw new Error('the villager fix is not installed');
  const body = functionBody(output, 'DF_');
  let original = body;
  for (const e of EDITS) original = original.replace(e.after, () => e.before);
  return output.replace(body, () => original);
}

if (require.main === module) {
  const args = process.argv.slice(2);
  const check = args.includes('--check');
  const file = args.find(a => !a.startsWith('--')) || path.join(root, 'site', 'classes.js');
  const input = fs.readFileSync(file, 'latin1');
  const output = build(input);
  if (output !== input && revert(output) !== input) throw new Error('reversal is not byte-exact');
  if (output === input) console.log('villager fix already installed');
  else if (check) { console.log('villager fix missing'); process.exitCode = 1; }
  else { fs.writeFileSync(file + '.tmp', output, 'latin1'); fs.renameSync(file + '.tmp', file); console.log('villager fix installed in ' + file); }
}

module.exports = {build, revert, isFixed, EDITS, CHECKS, MARKER};
