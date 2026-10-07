'use strict';
/* The JasperCraft look of the inventory, client half (owner, 2026-10-07: "make the whole inventory fit the theme of Jasper Craft ...
 * a thematic, superficial change. Don't change any of the logic."). The inventory textures are recoloured in the resource archive
 * (scripts/build-theme-pack.cjs) and the widened window, the gear column and the Field Journal draw in the same palette from their own
 * modules. What is left is the title text of the two recoloured windows, drawn dark grey (4210752) on the old light grey body: it
 * becomes cream (0xF4E6BC) on the new teal one. Two literals, nothing else:
 *   E3x  GuiInventory.drawGuiContainerForegroundLayer  "Crafting" at 97, 8
 *   Gzj  GuiContainerCreative.drawGuiContainerForegroundLayer  the tab's name at 8, 6
 * Every other window (chests, crafting table, furnace ...) keeps its own grey look and its dark text.
 * The gear column's colours (client-mods/gear-teavm.js, a fenced module inside classes.js that is built from a catalogue file the gear
 * builder keeps outside the repository) are recoloured here as one exact text block inside that module instead of rebuilding the
 * stage: strip() puts the old block back, and a gear stage rebuilt from the changed module already carries the new one.
 * Each edit names its function and how often its anchor occurs there; each patched text carries the marker JTHEME, so strip() restores the
 * input byte for byte. The result must parse and a rebuild must be stable.
 *
 *   node scripts/build-theme-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-theme-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const JT = '/*JTHEME*/', LABEL = 0xF4E6BC;
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** [function, vanilla, patched, occurrences in that function]. */
const EDITS = [
  ['E3x', 'g=97;b=8;c=4210752;$p=2;case 2:Efa(d,e,g,b,c);', 'g=97;b=8;c=' + JT + LABEL + ';$p=2;case 2:Efa(d,e,g,b,c);', 1],
  ['Gzj', 'g=8;b=6;c=4210752;$p=6;case 6:Efa(f,e,g,b,c);', 'g=8;b=6;c=' + JT + LABEL + ';$p=6;case 6:Efa(f,e,g,b,c);', 1]];

/** The plan() lines of the gear column that choose colours, before and after (client-mods/gear-teavm.js). */
const GEAR_FROM = [
  '      r(x0 + 1, y0, w - 2, h, 0xFF000000); r(x0, y0 + 1, w, h - 2, 0xFF000000);',
  '      r(x0 + 1, y0 + 1, w - 2, h - 2, 0xFFC6C6C6);',
  '      r(x0 + 1, y0 + 1, w - 3, 2, 0xFFFFFFFF); r(x0 + 1, y0 + 1, 2, h - 3, 0xFFFFFFFF);',
  '      r(x0 + 3, y0 + h - 3, w - 4, 2, 0xFF555555); r(x0 + w - 3, y0 + 3, 2, h - 4, 0xFF555555);',
  '      var hover = slotAt(L, x, y);',
  '      for (var i = 0; i < COUNT; i++) {',
  '        var sx = x0 + 5, sy = y0 + 5 + i * 18;',
  '        r(sx - 1, sy - 1, 17, 1, 0xFF373737); r(sx - 1, sy - 1, 1, 17, 0xFF373737);',
  '        r(sx, sy + 16, 17, 1, 0xFFFFFFFF); r(sx + 16, sy, 1, 17, 0xFFFFFFFF);',
  '        r(sx, sy, 16, 16, 0xFF8B8B8B);'].join('\n');
const GEAR_TO = [
  '      // The JasperCraft palette of the inventory (scripts/jasper-theme.cjs): outline, body, 2px highlight and shade, slot cells.',
  '      r(x0 + 1, y0, w - 2, h, 0xFF03171B); r(x0, y0 + 1, w, h - 2, 0xFF03171B);',
  '      r(x0 + 1, y0 + 1, w - 2, h - 2, 0xFF12454F);',
  '      r(x0 + 1, y0 + 1, w - 3, 2, 0xFFF0B552); r(x0 + 1, y0 + 1, 2, h - 3, 0xFFF0B552);',
  '      r(x0 + 3, y0 + h - 3, w - 4, 2, 0xFF7A4E1C); r(x0 + w - 3, y0 + 3, 2, h - 4, 0xFF7A4E1C);',
  '      var hover = slotAt(L, x, y);',
  '      for (var i = 0; i < COUNT; i++) {',
  '        var sx = x0 + 5, sy = y0 + 5 + i * 18;',
  '        r(sx - 1, sy - 1, 17, 1, 0xFF06222A); r(sx - 1, sy - 1, 1, 17, 0xFF06222A);',
  '        r(sx, sy + 16, 17, 1, 0xFF3C8791); r(sx + 16, sy, 1, 17, 0xFF3C8791);',
  '        r(sx, sy, 16, 16, 0xFF0B2F37);'].join('\n');

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

function strip(text) {
  let out = text;
  if (count(out, GEAR_TO) > 1) throw new Error('gear colour block occurs more than once');
  if (count(out, GEAR_TO) === 1) out = out.split(GEAR_TO).join(GEAR_FROM);
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = editIn(out, fn, to, from, n, 'strip#' + i);
  }
  if (out.includes(JT)) throw new Error('theme residue after strip');
  return out;
}
function apply(base) {
  let out = base;
  EDITS.forEach(([fn, from, to, n], i) => { out = editIn(out, fn, from, to, n, 'edit#' + i); });
  const gear = count(out, GEAR_FROM);
  if (gear > 1) throw new Error('gear colour block occurs more than once');
  if (gear === 1) out = out.split(GEAR_FROM).join(GEAR_TO);
  else if (count(out, GEAR_TO) !== 1) throw new Error('the gear column colour block was not found (is the gear stage in the client?)');
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
    console.log(JSON.stringify({stage: 'theme-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'theme-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'theme-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result), edits: EDITS.length,
      addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, JT, LABEL, GEAR_FROM, GEAR_TO};
