'use strict';
/* Muse+GLM_Maps client stage (JasprMuseMaps): adds the six Essences to the Creative catalogue, fenced by
 * JASPR_MUSE_CAT_BEGIN/END at the start of the JasprCreativeCatalog array (the gear stage owns its end). Creative
 * accepts the item as sent, so each entry is the exact item JasprMuseMaps' Essences.item() makes -- absorbing it in
 * survival works the same as a looted one. Idempotent and reversible: the fenced block is stripped and re-inserted.
 *   MUSE_CLIENT_SOURCE=<classes.js> node scripts/build-muse-client.cjs [--check]
 * Output: candidate/muse-client/classes.js (candidate only; CR count and everything outside the block unchanged).
 */
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const SOURCE = process.env.MUSE_CLIENT_SOURCE || path.join(ROOT, 'site', 'classes.js');
const BEGIN = '/*JASPR_MUSE_CAT_BEGIN*/', END = '/*JASPR_MUSE_CAT_END*/';
const ESSENCES = [
  ['VITALITY', 'Essence of Vitality', 'speckled_melon', 'c', '+2 max health per rank'],
  ['MIGHT', 'Essence of Might', 'blaze_powder', '6', '+5% attack damage per rank'],
  ['CELERITY', 'Essence of Celerity', 'sugar', 'b', '+3% movement speed per rank'],
  ['BULWARK', 'Essence of the Bulwark', 'shulker_shell', '7', '+1 armor per rank'],
  ['RESOLVE', 'Essence of Resolve', 'ghast_tear', 'f', '+1 armor toughness and +4% knockback resistance per rank'],
  ['FORTUNE', 'Essence of Fortune', 'rabbit_foot', 'a', '+1 luck per rank (better loot and fishing)'],
];
const S = '§';

function entries() {
  return ESSENCES.map(([kind, title, item, color, text]) => {
    const lore = [S + '7' + text, S + '8Right-click to absorb (permanent, up to rank 5)', S + '5Muse+GLM_Maps essence'];
    const snbt = '{id:' + JSON.stringify('minecraft:' + item) + ',Count:1b,tag:{ench:[{id:34s,lvl:1s}],HideFlags:1,display:{Name:'
      + JSON.stringify(S + color + title) + ',Lore:[' + lore.map(l => JSON.stringify(l)).join(',') + ']},JasprMuse:{essence:' + JSON.stringify(kind) + '}}}';
    return {id: 'muse_essence_' + kind.toLowerCase(), title, category: 'consumable', material: 'minecraft:' + item, model: 0,
      color: S + color, snbt, search: ('essence permanent upgrade character muse ' + title + ' ' + text).toLowerCase()};
  });
}

const count = (h, n) => h.split(n).length - 1;
function strip(text) {
  const a = text.indexOf(BEGIN);
  if (a < 0) return text;
  const b = text.indexOf(END, a);
  if (b < 0 || count(text, BEGIN) !== 1) throw new Error('muse catalogue fence damaged');
  return text.slice(0, a) + text.slice(b + END.length);
}

function apply(base) {
  const start = base.indexOf('var JasprCreativeCatalog=[');
  if (start < 0 || count(base, 'var JasprCreativeCatalog=[') !== 1) throw new Error('catalogue anchor');
  // Right after the opening bracket, so the array's closing '}];' (the gear stage's anchor) is never touched.
  const at = start + 'var JasprCreativeCatalog=['.length;
  const block = BEGIN + entries().map(e => Buffer.from(JSON.stringify(e), 'utf8').toString('latin1') + ',').join('') + END;
  return base.slice(0, at) + block + base.slice(at);
}

function build() {
  const raw = fs.readFileSync(SOURCE, 'latin1');
  const base = strip(raw);
  const result = apply(base);
  if (strip(result) !== base) throw new Error('reversal did not restore the input byte for byte');
  if (count(result, '\r') !== count(raw, '\r')) throw new Error('CR count changed');
  new vm.Script(Buffer.from(result, 'latin1').toString('utf8'), {filename: 'classes.js'});
  return {raw, result};
}

if (require.main === module) {
  const {raw, result} = build();
  if (process.argv.includes('--check')) { console.log(raw === result ? 'CHECK_PASS' : 'CHECK_STALE'); process.exit(raw === result ? 0 : 1); }
  const dir = path.join(ROOT, 'candidate', 'muse-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'muse-catalogue-v1', essences: ESSENCES.length, bytes: result.length}));
}
module.exports = {build, strip, apply, entries};
