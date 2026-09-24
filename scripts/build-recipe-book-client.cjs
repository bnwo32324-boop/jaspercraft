'use strict';
/* Adds the EasierCrafting port to the deployed TeaVM client.
 *
 * Writes only candidate/recipe-book-client/. Pins the exact pre-extension SHA256, asserts
 * every anchor count, requires each anchor to be consumed by its own edit so a second run
 * cannot double-apply, and verifies byte-for-byte reversal before the candidate is accepted.
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');

const ROOT = path.join(__dirname, '..');
const SOURCE = path.join(ROOT, 'site', 'classes.js');
const MODULE = path.join(ROOT, 'client-mods', 'recipe-book-teavm.js');

const BASE = 'd91cad91a08595b5f28fe34f4a222df8639382ce547b072def12989642692e53';
const sha = s => crypto.createHash('sha256').update(s).digest('hex');

const CRAFT_FG_HEAD = 'function CNC(a,b,c){var d,e,f,g,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:d=a.J;';
const INV_FG_HEAD = 'function E3x(a,b,c){var d,e,f,g,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:d=a.J;';
const CLICK_HEAD = 'function Gmh(a,b,c,d){var e,f,g,h,i,j,k,l,m,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();m=$T.l();l=$T.l();k=$T.l();j=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:$p=1;case 1:E7D(a,b,c,d);if(B()){break _;}';
const KEY_HEAD = '_:while(true){switch($p){case 0:if(c!=1&&c!=a.j.G.Hb.gO){$p=2;continue _;}';

// A gadget the catalogue never got, and a supply item that no longer exists.
const PORTAL_GUN = ',{"id":"portal_gun","title":"Portal Gun","category":"gadget","material":"minecraft:diamond_hoe","model":0,"color":"\\u00a7b","snbt":"{id:\\"minecraft:diamond_hoe\\",Count:1b,Damage:0s,tag:{Unbreakable:1b,HideFlags:6,display:{Name:\\"\\u00a7bPortal Gun\\",Lore:[\\"\\u00a77JasperCraft custom gadget\\",\\"\\u00a78Creative catalogue - server-issued on pickup\\"]},JasprCreative:{id:\\"portal_gun\\"}}}","search":"portal gun portal gun gadget portal teleport"}';

function ammoEntry(text) {
  const start = text.indexOf(',{"id":"ammo","title":"Forged Cartridges"');
  if (start < 0) return null;
  let depth = 0, inStr = false, esc = false, i = start + 1;
  for (; i < text.length; i++) {
    const c = text[i];
    if (inStr) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false; continue; }
    if (c === '"') inStr = true;
    else if (c === '{') depth++;
    else if (c === '}') { depth--; if (depth === 0) { i++; break; } }
  }
  return text.slice(start, i);
}

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

function replaceAll(text, from, to, expected, label) {
  const found = count(text, from);
  if (found !== expected) throw new Error(label + ': expected ' + expected + ' anchor(s), got ' + found);
  const out = text.split(from).join(to);
  const left = count(out, from);
  if (left !== 0) throw new Error(label + ': anchor survived the edit (' + left + ') -- not consuming');
  return out;
}

function edits(module, ammo) {
  return [
    // The module itself, ahead of the first mod already in the bundle. The anchor takes in
    // the comment terminator above the declaration so that inserting between the two
    // destroys it, which is what makes a second run fail loudly instead of double-applying.
    [' */\nvar JasprZoomKeyDescription = null, JasprZoomKeyLabel = null;',
     ' */\n' + module + '\nvar JasprZoomKeyDescription = null, JasprZoomKeyLabel = null;', 1],
    // Attach the book as each screen finishes laying itself out, which is the first moment
    // guiLeft is known and therefore the first moment the panel can be positioned.
    // The slot numbers are the original's: a table is 1/3x3/result 0/inventory 10, the
    // player's own 2x2 is 1/2x2/result 0/inventory 9.
    ['a.is=e;f=new Bft;h=10;i=e+5|0;',
     'a.is=e;JasprRecipeBookInit(a,1,3,0,10);f=new Bft;h=10;i=e+5|0;', 1],
    ['a.is=e;c=new Bft;h=10;i=e+104|0;',
     'a.is=e;JasprRecipeBookInit(a,1,2,0,9);c=new Bft;h=10;i=e+104|0;', 1],
    // Draw the panel first, because these methods reuse b and c as scratch immediately after.
    [CRAFT_FG_HEAD,
     CRAFT_FG_HEAD.replace('case 0:d=a.J;',
       'case 0:$p=90;case 90:JasprRecipeBookDraw(a,b,c);if(B()){break _;}$p=91;case 91:d=a.J;'), 1],
    [INV_FG_HEAD,
     INV_FG_HEAD.replace('case 0:d=a.J;',
       'case 0:$p=90;case 90:JasprRecipeBookDraw(a,b,c);if(B()){break _;}$p=91;case 91:d=a.J;'), 1],
    // Our click runs before the screen's, and swallows it when it was ours: a click on the
    // panel is a click outside the window, which vanilla would read as throwing the cursor.
    [CLICK_HEAD,
     CLICK_HEAD.replace('case 0:$p=1;case 1:E7D(a,b,c,d);if(B()){break _;}',
       'case 0:$p=92;case 92:JasprRecipeBookClick(a,b,c,d);if(B()){break _;}if(JasprRecipeBookConsumedClick())return;$p=1;case 1:E7D(a,b,c,d);if(B()){break _;}'), 1],
    [KEY_HEAD,
     '_:while(true){switch($p){case 0:if(JasprRecipeBookKeyTyped(a,b,c))return;if(c!=1&&c!=a.j.G.Hb.gO){$p=2;continue _;}', 1],
    // One swap does both catalogue fixes: the supply item that no longer exists becomes the
    // gadget that was never listed, so the array keeps its length and its shape.
    [ammo, PORTAL_GUN, 1],
  ];
}

function apply(text, list, reverse) {
  let out = text;
  list.forEach(([from, to, n], i) => {
    const label = 'edit#' + i + (reverse ? ' (reverse)' : '');
    out = reverse ? replaceAll(out, to, from, n, label) : replaceAll(out, from, to, n, label);
  });
  return out;
}

function build() {
  const input = fs.readFileSync(SOURCE, 'utf8');
  const inputSHA = sha(input);
  if (inputSHA !== BASE) throw new Error('Refusing to patch an unpinned client.\n  expected ' + BASE + '\n  found    ' + inputSHA);
  const module = fs.readFileSync(MODULE, 'utf8');
  const ammo = ammoEntry(input);
  if (!ammo) throw new Error('Could not find the retired ammo catalogue entry');
  const list = edits(module, ammo);

  const result = apply(input, list, false);
  const restored = apply(result, list, true);
  if (restored !== input) throw new Error('unpatch() did not restore the input byte for byte');
  new (require('node:vm').Script)(result, {filename: 'classes.js'});

  const dir = path.join(ROOT, 'candidate', 'recipe-book-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), result);
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify({
    stage: 'easiercrafting-port-v1', baseSHA256: inputSHA, sha256: sha(result),
    edits: list.length, moduleBytes: Buffer.byteLength(module),
    catalogue: {removed: 'ammo', added: 'portal_gun'},
  }, null, 2) + '\n');
  return {dir, sha256: sha(result), bytes: Buffer.byteLength(result)};
}

function unpatch() {
  const patched = fs.readFileSync(SOURCE, 'utf8');
  const module = fs.readFileSync(MODULE, 'utf8');
  return apply(patched, edits(module, ammoEntry(patched) || ''), true);
}

if (require.main === module) {
  const out = build();
  console.log('Candidate only: ' + path.join(out.dir, 'classes.js'));
  console.log('SHA256 ' + out.sha256);
  console.log('bytes  ' + out.bytes);
}
module.exports = {build, unpatch, sha, BASE};
