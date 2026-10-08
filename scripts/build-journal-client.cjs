'use strict';
/* Field Journal stage for the deployed TeaVM client (owner, 2026-10-07: fill the empty space in the wide inventory with an event
 * forecast, a character summary and the active item effects). See client-mods/journal-teavm.js: a small panel with three tabs drawn
 * right of the crafting result in the widened inventory window, fed by the server plugin JasprJournal over the plugin channel
 * jaspr:journal.
 *
 * Four hooks, each placed right AFTER the text the wide-inventory stage (and the gear and recipe-book builders) put in the same
 * function, never inside it: those builders find their own patched text again to strip it, so this stage leaves every character
 * of it contiguous and any of them can still be rebuilt on a client that carries this one.
 *   C6T  GuiContainer.drawScreen   the start of state 2 (right after wide's JasprWidePocketDraw) draws the panel, then goes on in
 *                                  new state 291 with the original state-2 code;
 *   E8R  handleJoinGame            state 10 first says "hello 1" on jaspr:journal (new state 292 continues with the original code);
 *   Cyr  handleCustomPayload       channel jaspr:journal is told apart before the JASPR|World check, and new state 2995 reads
 *                                  the string and gives it to JasprJournal.receive;
 *   Gmh  GuiContainer.mouseClicked a click on the panel (tab, button, page) is used up right after the super call, before the
 *                                  slots see it.
 * Every edit names its function and how often its anchor occurs there; every patched text carries the marker JJ, so strip()
 * restores the input byte for byte. The module goes in a fenced block before the TeaVM footer. A state number this stage adds must
 * be new in its function (a duplicate case label would silently never run). The result must parse, and a rebuild must be stable.
 *
 *   node scripts/build-journal-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-journal-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = path.join(ROOT, 'client-mods', 'journal-teavm.js');
const BEGIN = '/* JASPR_JOURNAL_BEGIN */', END = '/* JASPR_JOURNAL_END */', JJ = '/*JJ*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** [function, vanilla, patched, occurrences in that function]. */
const EDITS = [
  ['C6T', 'case 2:C7();', 'case 2:' + JJ + 'JasprJournalDraw(a);if(B()){break _;}$p=291;case 291:C7();', 1],
  ['E8R', 'case 10:GZM(d);if(B()){break _;}return;', 'case 10:' + JJ + 'JasprJournalHello(b);if(B()){break _;}$p=292;case 292:GZM(d);if(B()){break _;}return;', 1],
  ['Cyr', 'if($rt_ustr(b.S$)==="JASPR|World"){$p=101;continue _;}',
    JJ + 'if($rt_ustr(b.S$)==="jaspr:journal"){$p=2995;continue _;}if($rt_ustr(b.S$)==="JASPR|World"){$p=101;continue _;}', 1],
  ['Cyr', 'case 1963:$z=CRh(b.Wm,32767);if(B()){break _;}JasprSurround.receive($rt_ustr($z));return;',
    'case 1963:$z=CRh(b.Wm,32767);if(B()){break _;}JasprSurround.receive($rt_ustr($z));return;case 2995:' + JJ + '$z=CRh(b.Wm,32767);if(B()){break _;}JasprJournal.receive($rt_ustr($z));return;', 1],
  ['Gmh', 'e=d!=(a.j.G.Rk.gO+100|0)?0:1;$p=2;case 2:$z=FBP(a,b,c);',
    'e=d!=(a.j.G.Rk.gO+100|0)?0:1;' + JJ + 'if(JasprJournalClick(a,b,c,d))return;$p=2;case 2:$z=FBP(a,b,c);', 1]];
/** The state numbers this stage adds: [function, number]. */
const NEW_STATES = [['C6T', 291], ['E8R', 292], ['Cyr', 2995]];
/** TeaVM names the module and hooks use. */
const NATIVE = ['C6T', 'E8R', 'Cyr', 'Gmh', 'ID', 'A2Z', 'D49', 'CA', 'CFh', 'Cn9', 'CRh', 'AKy', 'Iu', 'Fru', 'Lg', 'FuF', 'BgN', 'FX', 'Ds', 'B', 'FT',
  'JasprWide', 'JasprWidePocketDraw', 'JasprWideHello', 'JasprGearMouseDown'];

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
    if (e < 0 || out.indexOf(BEGIN, b + 1) >= 0) throw new Error('journal block incomplete or duplicated');
    out = out.slice(0, b) + out.slice(e + END.length + 1);
  }
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = editIn(out, fn, to, from, n, 'strip#' + i);
  }
  // The Overloaded Armor Bar's block asks the Journal for the armour the worn armaments add (when this stage is in the client), so
  // that block may name it; nothing else may.
  const outside = out.replace(/\/\* JASPR_ARMORBAR_BEGIN \*\/[^]*?\/\* JASPR_ARMORBAR_END \*\//, '');
  if (out.includes(JJ) || outside.includes('JasprJournal') || out.includes(BEGIN)) throw new Error('journal residue after strip');
  return out;
}

function apply(base) {
  const missing = NATIVE.filter(n => !declared(base, n));
  if (missing.length) throw new Error('TeaVM names missing (re-audit; the wide-inventory stage must be in): ' + missing.join(', '));
  for (const [fn, n] of NEW_STATES) {
    const [s, e] = fnRange(base, fn);
    if (count(base.slice(s, e), 'case ' + n + ':') !== 0) throw new Error('state ' + n + ' is already used in ' + fn + ': pick another');
  }
  let out = base;
  EDITS.forEach(([fn, from, to, n], i) => { out = editIn(out, fn, from, to, n, 'edit#' + i); });
  for (const [fn, n] of NEW_STATES) {
    const [s, e] = fnRange(out, fn);
    if (count(out.slice(s, e), 'case ' + n + ':') !== 1) throw new Error('state ' + n + ' in ' + fn + ' must occur exactly once');
  }
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
    console.log(JSON.stringify({stage: 'journal-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'journal-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'journal-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
      edits: EDITS.length, addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, NEW_STATES, BEGIN, END, JJ};
