'use strict';
/* Chest search hooks for the deployed TeaVM client (owner, 2026-10-03: "Chests themselves should also have search boxes
 * UI where you can search any item"). The search itself lives in the EasierCrafting module
 * (client-mods/recipe-book-teavm.js: JasprChestSearch, JasprChestSearchDraw), which also takes the box's clicks and keys
 * through its existing GuiContainer hooks; this stage only adds the two draw hooks.
 *
 * GuiChest (chests, trapped and ender chests) and GuiShulkerBox each get one new first state in their
 * drawGuiContainerForegroundLayer (DUK, D2Q), as the recipe book hooks the crafting screens: state 90 calls
 * JasprChestSearchDraw(gui, mouseX, mouseY, container inventory) -- a.d0b for a chest, a.dmc for a shulker box, asked once
 * for its title -- and continues at 91 into the vanilla labels. The call is guarded with typeof, so a client whose module
 * has no chest search keeps working. Every anchor must occur exactly once; the hooks carry a marker, are removed byte
 * for byte by strip(), and a rebuild is stable. The result must parse.
 *
 * Order for a candidate (the module first, then these hooks):
 *   node scripts/build-recipe-book-client.cjs --upgrade --source <live classes.js>
 *   node scripts/build-chest-search-client.cjs --source candidate/recipe-book-client/classes.js [--out <file>]
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const HOOK = '/*JASPR_CHESTSEARCH_V1*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

const hook = inv => HOOK + '$p=90;case 90:if(typeof JasprChestSearchDraw==="function"){JasprChestSearchDraw(a,b,c,' + inv
  + ');if(B()){break _;}}$p=91;case 91:';
/** [vanilla, patched]: GuiChest and GuiShulkerBox foreground draws, at their first state. */
const EDITS = [
  ['_:while(true){switch($p){case 0:d=a.J;e=a.d0b;$p=1;case 1:$z=e.iG();',
    '_:while(true){switch($p){case 0:' + hook('a.d0b') + 'd=a.J;e=a.d0b;$p=1;case 1:$z=e.iG();'],
  ['_:while(true){switch($p){case 0:d=a.J;e=a.dmc;$p=1;case 1:$z=e.iG();',
    '_:while(true){switch($p){case 0:' + hook('a.dmc') + 'd=a.J;e=a.dmc;$p=1;case 1:$z=e.iG();']];
const OWNERS = ['\nfunction DUK(a,b,c){', '\nfunction D2Q(a,b,c){'];

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

function strip(text) {
  let out = text;
  for (const [vanilla, patched] of EDITS) if (count(out, patched) === 1) out = out.split(patched).join(vanilla);
  if (out.includes(HOOK)) throw new Error('chest search hook present but not the expected one');
  return out;
}

function apply(base) {
  let out = base;
  EDITS.forEach(([vanilla, patched], i) => {
    if (count(out, vanilla) !== 1) throw new Error('chest screen anchor ' + i + ': expected 1, got ' + count(out, vanilla));
    // The anchor must sit inside the screen it is meant for.
    const at = out.indexOf(vanilla), owner = out.lastIndexOf('\nfunction ', at);
    if (out.indexOf(OWNERS[i], owner) !== owner) throw new Error('chest screen anchor ' + i + ' is not in ' + OWNERS[i].trim());
    out = out.split(vanilla).join(patched);
  });
  return out;
}

function build(raw) {
  const base = strip(raw);
  const result = apply(base);
  if (strip(result) !== base) throw new Error('reversal did not restore the unpatched client byte for byte');
  if (apply(strip(result)) !== result) throw new Error('rebuild not stable');
  new vm.Script(Buffer.from(result, 'latin1').toString('utf8'), {filename: 'classes.js'});
  return {base, result};
}

if (require.main === module) {
  const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
  const source = arg('--source') || path.join(ROOT, 'candidate', 'recipe-book-client', 'classes.js');
  const target = arg('--out') || path.join(ROOT, 'candidate', 'chest-search-client', 'classes.js');
  const raw = fs.readFileSync(source, 'latin1');
  if (!raw.includes('function JasprChestSearchDraw(')) throw new Error('the source has no chest search module: run the recipe book --upgrade first');
  const {base, result} = build(raw);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'chest-search-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
    addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
}
module.exports = {build, strip, apply, EDITS, HOOK};
