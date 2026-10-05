'use strict';
/* NBT-skin stage for the deployed TeaVM client (owner, 2026-10-05: "sanitized flesh should have its own texture, and it should
 * look like jerky"). See client-mods/nbt-skin-teavm.js: a stackable item that cannot be told apart by damage (Sanitized
 * Flesh is a cooked beef) answers the model predicate jaspr_skin from the id its own data carries, so the resource pack can give
 * it a model of its own.
 *
 * One hook: Items' static initialiser (F7x) hands the cooked beef to JasprNbtSkinInstall right after it is fetched from the
 * registry (the item gets a property getter, which also makes the renderer evaluate its model overrides at all). The module
 * goes in a fenced block before the TeaVM footer. Every patched text carries the marker JN, so strip() restores the input
 * byte for byte. The result must parse, and a rebuild must be stable.
 *
 *   node scripts/build-nbt-skin-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-nbt-skin-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = path.join(ROOT, 'client-mods', 'nbt-skin-teavm.js');
const BEGIN = '/* JASPR_NBTSKIN_BEGIN */', END = '/* JASPR_NBTSKIN_END */', JN = '/*JN*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** [function, vanilla, patched, occurrences in that function]. KR7 is Items.COOKED_BEEF, assigned from the registry (B_N). */
const EDITS = [
  ['F7x', 'c=$z;KR7=c;c=C(3710);$p=124;', 'c=$z;KR7=c;' + JN + 'JasprNbtSkinInstall(KR7);c=C(3710);$p=124;', 1]];
/** TeaVM names the module and hook use. */
const NATIVE = ['F7x', 'B_N', 'Bb', 'Gp9', 'DQE', 'GaK', 'F54', 'F5b', 'EAO', 'FRn', '$rt_str', '$rt_ustr'];

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
    if (e < 0 || out.indexOf(BEGIN, b + 1) >= 0) throw new Error('nbt-skin block incomplete or duplicated');
    out = out.slice(0, b) + out.slice(e + END.length + 1);
  }
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = editIn(out, fn, to, from, n, 'strip#' + i);
  }
  if (out.includes(JN) || out.includes('JasprNbtSkin') || out.includes(BEGIN)) throw new Error('nbt-skin residue after strip');
  return out;
}

function apply(base) {
  // $rt_str and $rt_ustr are TeaVM runtime functions: they are checked as declarations like the obfuscated names.
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
    console.log(JSON.stringify({stage: 'nbt-skin-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'nbt-skin-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'nbt-skin-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
      edits: EDITS.length, addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, BEGIN, END, JN};
