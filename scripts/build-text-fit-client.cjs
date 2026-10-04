'use strict';
/* Text-fit stage for the deployed TeaVM client (owner, 2026-10-04: tooltips and area titles were cut off; "it should
 * never be cut off. It should appear on screen, and it should be legible and readable"). See
 * client-mods/text-fit-teavm.js: tooltips wrap and stay on screen (GuiScreen.drawHoveringText C8Q); the HUD title,
 * subtitle and action bar shrink to a still-readable scale and then wrap (GuiIngame.renderGameOverlay Ewc).
 *
 * Every edit names its function and how often its anchor occurs there; every patched text carries the marker JT, so
 * strip() restores the input byte for byte. The module goes in a fenced block before the TeaVM footer. The result must
 * parse, and a rebuild must be stable.
 *
 *   node scripts/build-text-fit-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-text-fit-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = path.join(ROOT, 'client-mods', 'text-fit-teavm.js');
const BEGIN = '/* JASPR_TEXTFIT_BEGIN */', END = '/* JASPR_TEXTFIT_END */', JT = '/*JT*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** [function, vanilla, patched, occurrences in that function]. */
const EDITS = [
  // Tooltips: wrap first, place against the real screen width, never above the top edge.
  ['C8Q', '_:while(true){switch($p){case 0:$p=1;case 1:$z=E3i(b);',
    '_:while(true){switch($p){case 0:' + JT + 'JasprTextFit.tooltip(a,b,c);$p=1;case 1:$z=E3i(b);', 1],
  ['C8Q', 'if((j+f|0)>a.q)j=j-(28+f|0)|0;', 'j=' + JT + 'JasprTextFit.tooltipX(a,j,f);', 2],
  ['C8Q', 'c=(k+l|0)+6|0;d=a.L;if(c>d)k=(d-l|0)-6|0;', 'c=(k+l|0)+6|0;d=a.L;if(c>d)k=(d-l|0)-6|0;' + JT + 'if(k<6)k=6;', 2],
  // HUD title (4x), subtitle (2x) and action bar: scale down, then wrap.
  ['Ewc', 'case 72:FWM(b,m,o);if(B()){break _;}i=h<<24&(-16777216);k=a.bOl;b=( -CA(f,k)|0)/2|0;m=(-10.0);h=16777215|i;i=1;$p=73;case 73:f.ei2(k,b,m,h,i);if(B()){break _;}$p=74;',
    'case 72:' + JT + 'i=h<<24&(-16777216);k=a.bOl;h=16777215|i;i=1;$p=73;case 73:JasprTextDraw(f,k,4.0,(-10.0),h,d,0);if(B()){break _;}$p=74;', 1],
  ['Ewc', 'case 76:FWM(b,m,o);if(B()){break _;}k=a.bTS;b=( -CA(f,k)|0)/2|0;m=5.0;i=1;$p=77;case 77:f.ei2(k,b,m,h,i);if(B()){break _;}$p=78;',
    'case 76:' + JT + 'k=a.bTS;i=1;$p=77;case 77:JasprTextDraw(f,k,2.0,5.0,h,d,1);if(B()){break _;}$p=78;', 1],
  ['Ewc', 'case 55:Efa(f,k,i,u,h);if(B()){break _;}', 'case 55:' + JT + 'JasprTextDraw(f,k,1.0,(-4.0),h,d,2);if(B()){break _;}', 1]];
/** TeaVM names the module and hooks use. */
const NATIVE = ['C8Q', 'Ewc', 'E3i', 'CA', 'FWM', 'Y', 'FX', 'Ds', 'B', 'FT'];

function fnRange(text, name) {
  const head = '\nfunction ' + name + '(';
  const at = text.indexOf(head);
  if (at < 0 || text.indexOf(head, at + 1) >= 0) throw new Error('function ' + name + ': expected exactly one definition');
  const start = at + 1, next = text.indexOf('\nfunction ', start + 5);
  return [start, next < 0 ? text.length : next];
}
function editIn(text, name, from, to, expected, label) {
  const [s, e] = fnRange(text, name);
  const body = text.slice(s, e), n = count(body, from);
  if (n !== expected) throw new Error(label + ': expected ' + expected + ' in ' + name + ', got ' + n);
  return text.slice(0, s) + body.split(from).join(to) + text.slice(e);
}
function declared(text, name) {
  const e = name.replace(/\$/g, '\\$');
  return new RegExp('(?:^|[\\s;{}(])function ' + e + '\\(|(?:^|[\\s;,{}])(?:var|let) ' + e + '\\s*[=;,]|[;,\\s]' + e + '\\s*=').test(text);
}

function strip(text) {
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || out.indexOf(BEGIN, b + 1) >= 0) throw new Error('text-fit block incomplete or duplicated');
    out = out.slice(0, b) + out.slice(e + END.length + 1);
  }
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = editIn(out, fn, to, from, n, 'strip#' + i);
  }
  if (out.includes(JT) || out.includes('JasprTextFit') || out.includes(BEGIN)) throw new Error('text-fit residue after strip');
  return out;
}

function apply(base) {
  const missing = NATIVE.filter(n => !declared(base, n));
  if (missing.length) throw new Error('TeaVM names missing (re-audit): ' + missing.join(', '));
  let out = base;
  EDITS.forEach(([fn, from, to, n], i) => { out = editIn(out, fn, from, to, n, 'edit#' + i); });
  const module = fs.readFileSync(MODULE, 'latin1');
  if (/[^\x00-\x7f]/.test(module)) throw new Error('module must be ASCII');
  const end = out.lastIndexOf('}));');
  if (end < 0) throw new Error('TeaVM module footer not found');
  return out.slice(0, end) + BEGIN + '\n' + module.replace(/\n$/, '') + '\n' + END + '\n' + out.slice(end);
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
    console.log(JSON.stringify({stage: 'text-fit-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'text-fit-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'text-fit-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
      edits: EDITS.length, addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, BEGIN, END};
