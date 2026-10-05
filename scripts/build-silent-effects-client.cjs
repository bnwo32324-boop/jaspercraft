'use strict';
/* Silent-effects stage for the deployed TeaVM client (owner, 2026-10-05: the Pearl of the Drowned kept a "Water Breathing 0:03"
 * box in the inventory, and it covered the Easier Crafting search bar for good -- "I don't want you to stop the water breathing,
 * but make the effect invisible").
 *
 * The servers already mark every effect that an item, trinket, bauble, armament or stat keeps on its bearer as ambient with no
 * particles (PotionEffect(type, ticks, amplifier, true, false)); the HUD already hides particle-less effects, but the inventory
 * screens (InventoryEffectRenderer) list every effect. This stage makes them skip an effect that is ambient AND particle-less,
 * the combination vanilla never produces on its own (potions are not ambient; beacons show particles):
 *   - InventoryEffectRenderer.drawActivePotionEffects (CaO) leaves such an effect out of the list;
 *   - InventoryEffectRenderer.updateActivePotionEffects (CX3) only shifts the window right for effects that will be listed.
 * The effects themselves are untouched: the client still applies them (jump height, haste) -- only the box is gone.
 *
 * Every edit names its function and how often its anchor occurs; every patched text carries the marker JF, so strip()
 * restores the input byte for byte. The result must parse, and a rebuild must be stable.
 *
 *   node scripts/build-silent-effects-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-silent-effects-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const JF = '/*JF*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** The original updateActivePotionEffects, and the version that only counts effects the list will show. */
const CX3_FROM = 'function CX3(a){var b,c,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){' +
  'case 0:b=a.j.v;$p=1;case 1:$z=F9x(b);if(B()){break _;}b=$z;$p=2;case 2:$z=E3i(b);if(B()){break _;}c=$z;' +
  'if(c){a.is=(a.q-a.gv|0)/2|0;a.clo=0;}else{a.is=160+(((a.q-a.gv|0)-200|0)/2|0)|0;a.clo=1;}return;default:FT();}}Ds().s(a,b,c,$p);}';
const CX3_TO = 'function CX3(a){var b,c,d,e,f,g,$p,$z;' + JF + '$p=0;if(FX()){var $T=Ds();$p=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){' +
  'case 0:b=a.j.v;$p=1;case 1:$z=F9x(b);if(B()){break _;}b=$z;$p=2;case 2:$z=E3i(b);if(B()){break _;}c=$z;if(c){$p=7;continue _;}d=Li3;$p=3;' +
  'case 3:$z=D0L(d,b);if(B()){break _;}b=$z;$p=4;case 4:$z=BA(b);if(B()){break _;}e=$z;$p=5;' +
  'case 5:$z=Bz(e);if(B()){break _;}f=$z;if(!f){c=1;$p=7;continue _;}$p=6;' +
  'case 6:$z=GDx(e);if(B()){break _;}g=$z;if(!(g.pl&&!g.xI)){c=0;$p=7;continue _;}$p=5;continue _;' +
  'case 7:if(c){a.is=(a.q-a.gv|0)/2|0;a.clo=0;}else{a.is=160+(((a.q-a.gv|0)-200|0)/2|0)|0;a.clo=1;}return;default:FT();}}Ds().s(a,b,c,d,e,f,g,$p);}';

/** [label, vanilla, patched, occurrences in the whole client]. */
const EDITS = [
  ['CX3 updateActivePotionEffects', CX3_FROM, CX3_TO, 1],
  // CaO drawActivePotionEffects: after the next effect is read (case 10), an ambient particle-less one goes back to the loop head (case 9).
  ['CaO drawActivePotionEffects', 'd=$z;o=d;p=o.jr;j=1.0;', 'd=$z;o=d;' + JF + 'if(o.pl&&!o.xI){$p=9;continue _;}p=o.jr;j=1.0;', 1]];
/** TeaVM names the edits rely on. */
const NATIVE = ['CX3', 'CaO', 'F9x', 'E3i', 'D0L', 'BA', 'Bz', 'GDx', 'FX', 'Ds', 'B', 'FT'];
const GLOBALS = ['Li3'];

const WS = String.fromCharCode(32, 9, 10, 13);
function declared(text, name) {
  const e = name.split('$').join('[$]');
  return new RegExp('(?:^|[' + WS + ';{}(])function ' + e + '[(]|(?:^|[' + WS + ';,{}])(?:var|let) ' + e + '[' + WS + ']*[=;,]|[;,' + WS + ']' + e + '[' + WS + ']*=').test(text);
}
function replaceUnique(text, from, to, expected, label) {
  const n = count(text, from);
  if (n !== expected) throw new Error(label + ': expected ' + expected + ' anchor(s), got ' + n);
  const at = text.indexOf(from);
  return text.slice(0, at) + to + text.slice(at + from.length);
}

function strip(text) {
  let out = text;
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [label, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = replaceUnique(out, to, from, n, 'strip ' + label);
  }
  if (out.includes(JF)) throw new Error('silent-effects residue after strip');
  return out;
}

function apply(base) {
  const missing = NATIVE.filter(n => !declared(base, n)).concat(GLOBALS.filter(n => !declared(base, n)));
  if (missing.length) throw new Error('TeaVM names missing (re-audit): ' + missing.join(', '));
  let out = base;
  for (const [label, from, to, n] of EDITS) out = replaceUnique(out, from, to, n, label);
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
  const raw = fs.readFileSync(source, 'latin1');
  if (process.argv.includes('--unpatch')) {
    const base = strip(raw), out = arg('--out');
    if (!out) throw new Error('--unpatch needs --out');
    fs.writeFileSync(out, Buffer.from(base, 'latin1'));
    console.log(JSON.stringify({stage: 'silent-effects-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'silent-effects-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'silent-effects-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
      edits: EDITS.length, addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, JF, CX3_TO};
