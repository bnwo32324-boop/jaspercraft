'use strict';
/* Big Mobs client stage (owner 2026-10-05: dungeon bosses and mobs scaled up "literally ... a larger size and ... a larger hitbox"). The
 * module client-mods/big-mobs-teavm.js (JasprBigMobs) keeps the server's jaspr:scale table; this stage adds it and three hooks:
 *   Cyr  handleCustomPayload        channel jaspr:scale is told apart before the jaspr:gear check, and new state 2991 reads the
 *                                   string (CRh, PacketBuffer.readString) and hands it to JasprBigMobs.receive;
 *   DWR  RenderLivingBase.doRender  after applyRotations (a.a95) the render goes through new state 2992 instead of straight to
 *                                   prepareScale (state 20): JasprBigMobs.prepare(entity) grows the client hitbox when the entity's
 *                                   factor changed (FET, Entity.setSize) and returns the factor; anything but 1 is applied with
 *                                   FWM (GlStateManager.scale) around the feet, the way the vanilla giant is drawn. State 2992 sits at
 *                                   the start of the switch and keeps the function's own catch (state 9: pop the matrix, log).
 * Every anchor must occur exactly once; the hooks carry the marker JBM and the module its fence; strip() restores the input byte
 * for byte and a rebuild is stable. The result must parse. A client without the module ignores the channel (old behaviour).
 *   node scripts/build-big-mobs-client.cjs --source <classes.js> [--out <file>]
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const BEGIN = '/* JASPR_BIGMOBS_BEGIN */', END = '/* JASPR_BIGMOBS_END */', JBM = '/*JBM*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');
const MODULE_FILE = path.join(ROOT, 'client-mods', 'big-mobs-teavm.js');
const CATCH = 'catch($$e){$$je=F($$e);if($$je instanceof P){l=$$je;}else{throw $$e;}}h=Lrh;m=C(7575);n=G(D,1);n.data[0]=l;$p=9;continue _;';

/** [function, vanilla, patched]. */
const EDITS = [
  ['Cyr', 'if($rt_ustr(b.S$)==="jaspr:gear"){$p=102;continue _;}',
    JBM + 'if($rt_ustr(b.S$)==="jaspr:scale"){$p=2991;continue _;}if($rt_ustr(b.S$)==="jaspr:gear"){$p=102;continue _;}'],
  ['Cyr', 'case 101:$z=CRh(b.Wm,16000);if(B()){break _;}JasprDH.context($rt_ustr($z));return;',
    'case 101:$z=CRh(b.Wm,16000);if(B()){break _;}JasprDH.context($rt_ustr($z));return;case 2991:' + JBM
      + '$z=CRh(b.Wm,32767);if(B()){break _;}if(typeof JasprBigMobs!=="undefined")JasprBigMobs.receive($rt_ustr($z));return;'],
  ['DWR', 'a.a95(b,q,i,g);if(B()){break _;}$p=20;continue _;',
    'a.a95(b,q,i,g);if(B()){break _;}' + JBM + '$p=2992;continue _;'],
  ['DWR', '_:while(true){switch($p){case 0:$p=1;case 1:Eu0();if(B()){break _;}$p=2;case 2:F1Q();',
    '_:while(true){switch($p){case 2992:' + JBM + 'try{u=typeof JasprBigMobs!=="undefined"?JasprBigMobs.prepare(b):1;if(u!==1){FWM(u,u,u);if(B()){break _;}}$p=20;continue _;}'
      + CATCH + 'case 0:$p=1;case 1:Eu0();if(B()){break _;}$p=2;case 2:F1Q();']];
const MODULE_ANCHOR = '\nfunction DWR(';

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }
function owner(text, at) { const s = text.lastIndexOf('\nfunction ', at); return text.slice(s + 10, text.indexOf('(', s)); }
function moduleText() {
  const body = fs.readFileSync(MODULE_FILE, 'latin1').replace(/\r?\n$/, '');
  if (/[^\x00-\x7f]/.test(body)) throw new Error('the module must be ASCII');
  return BEGIN + '\n' + body + '\n' + END;
}

function strip(text) {
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || count(out, BEGIN) !== 1) throw new Error('corrupt previous big mobs block');
    let end = e + END.length;
    if (out[end] === '\n') end++;
    out = out.slice(0, b) + out.slice(end);
  }
  for (const [, vanilla, patched] of EDITS) if (count(out, patched) === 1) out = out.split(patched).join(vanilla);
  if (out.includes(JBM)) throw new Error('big mobs hook present but not the expected one');
  return out;
}

function apply(base) {
  let out = base;
  EDITS.forEach(([fn, vanilla, patched], i) => {
    if (count(out, vanilla) !== 1) throw new Error('big mobs anchor ' + i + ': expected 1, got ' + count(out, vanilla));
    const at = out.indexOf(vanilla);
    if (owner(out, at) !== fn) throw new Error('big mobs anchor ' + i + ' is in ' + owner(out, at) + ', not ' + fn);
    out = out.split(vanilla).join(patched);
  });
  if (count(out, MODULE_ANCHOR) !== 1) throw new Error('module anchor: expected 1, got ' + count(out, MODULE_ANCHOR));
  return out.replace(MODULE_ANCHOR, () => '\n' + moduleText() + MODULE_ANCHOR);
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
  const target = arg('--out') || path.join(ROOT, 'candidate', 'big-mobs-client', 'classes.js');
  const raw = fs.readFileSync(source, 'latin1');
  const {base, result} = build(raw);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'big-mobs-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
    addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
}
module.exports = {build, strip, apply, EDITS, BEGIN, END, JBM, MODULE_ANCHOR};
