'use strict';
/* Realm armoury worn skins for the deployed TeaVM client (JasprGear 5.0.0). Armour carrying the NBT integer
 * JasprArmorySkin (1..6: emerald, blazeforged, abyssal, titan, liminal, void) is drawn with
 * textures/models/armor/jaspr_<set>_layer_<1|2>.png instead of the diamond texture its item is based on; everything else
 * about the armour layer (model, Mo' Bends' bendable armour, tint, enchantment glint) is unchanged.
 *
 * One fenced hook in LayerArmorBase.renderArmorLayer (D$Y): after getArmorResource (CVA) picked the vanilla texture at
 * state 8, two new states read the stack's tag (k.bV) with NBTTagCompound.getInteger (DcG) and, for a skin number, bind a
 * cached ResourceLocation (Bb, constructed by Gp9) instead; a stack without one goes straight on to bindTexture (FTd) as
 * before. The locals it borrows (p, q, t) are written later in the function before they are read. Plus one module block
 * (JasprArmory) before the Creative catalogue. Every anchor must occur exactly once; the stage is removable by marker and
 * a rebuild on an already patched client first takes the old stage off. Binary-safe (latin1), and the result must parse.
 *   node scripts/build-armory-client.cjs [--source <classes.js>] [--out <file>]
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const BEGIN = '/* JASPR_ARMORY_BEGIN */', END = '/* JASPR_ARMORY_END */', HOOK = '/*JASPR_ARMORY_V1*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');
const SETS = ['emerald', 'blazeforged', 'abyssal', 'titan', 'liminal', 'void'];

const ANCHOR = 'case 8:$z=CVA(a,l,o,j);if(B()){break _;}j=$z;$p=9;case 9:FTd(n,j);';
const PATCHED = 'case 8:$z=CVA(a,l,o,j);if(B()){break _;}j=$z;' + HOOK
  + 'p=k.bV;if(p===null||!JasprArmory.on){$p=9;continue _;}q=JasprArmory.key();$p=90;'
  + 'case 90:$z=DcG(p,q);if(B()){break _;}t=$z;p=JasprArmory.cached(t,o);if(p!==null){j=p;$p=9;continue _;}'
  + 'q=JasprArmory.path(t,o);if(q===null){$p=9;continue _;}p=new Bb;$p=91;'
  + 'case 91:Gp9(p,q);if(B()){break _;}JasprArmory.store(t,o,p);j=p;$p=9;'
  + 'case 9:FTd(n,j);';
const CATALOG_ANCHOR = '\nvar JasprCreativeCatalog=[';

const MODULE = BEGIN + '\n' + [
  'var JasprArmory = (function () {',
  '  "use strict";',
  '  // Realm armoury worn skins (scripts/build-armory-client.cjs): skin number -> cached ResourceLocation per layer.',
  '  var SETS = ' + JSON.stringify(SETS) + ';',
  '  var keyString = null, cache = {}, stats = {skinned: 0, built: 0};',
  '  var api = {on: true, stats: stats};',
  '  api.key = function () { if (keyString === null) keyString = $rt_str("JasprArmorySkin"); return keyString; };',
  '  api.cached = function (id, legs) { var r = cache[id + (legs ? "b" : "a")]; if (r) { stats.skinned++; return r; } return null; };',
  '  api.path = function (id, legs) {',
  '    if (!(id >= 1 && id <= SETS.length)) return null;',
  '    return $rt_str("textures/models/armor/jaspr_" + SETS[id - 1] + "_layer_" + (legs ? 2 : 1) + ".png");',
  '  };',
  '  api.store = function (id, legs, rl) { cache[id + (legs ? "b" : "a")] = rl; stats.built++; stats.skinned++; };',
  '  return api;',
  '})();'].join('\n') + '\n' + END;

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

function strip(text) {
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || count(out, BEGIN) !== 1) throw new Error('corrupt previous armoury block');
    let end = e + END.length;
    if (out[end] === '\n') end++;
    out = out.slice(0, b) + out.slice(end);
  }
  if (count(out, PATCHED) === 1) out = out.split(PATCHED).join(ANCHOR);
  if (out.includes(HOOK)) throw new Error('armoury hook present but not the expected one');
  return out;
}

function apply(base) {
  if (count(base, ANCHOR) !== 1) throw new Error('renderArmorLayer anchor: expected 1, got ' + count(base, ANCHOR));
  if (count(base, CATALOG_ANCHOR) !== 1) throw new Error('catalogue anchor: expected 1, got ' + count(base, CATALOG_ANCHOR));
  let out = base.split(ANCHOR).join(PATCHED);
  out = out.replace(CATALOG_ANCHOR, () => '\n' + MODULE + CATALOG_ANCHOR);
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
  const source = arg('--source') || path.join(ROOT, 'site', 'classes.js');
  const target = arg('--out') || path.join(ROOT, 'candidate', 'armory-client', 'classes.js');
  const raw = fs.readFileSync(source, 'latin1');
  const {base, result} = build(raw);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'realm-armory-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
    addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
}
module.exports = {build, strip, apply, ANCHOR, PATCHED, MODULE, SETS};
