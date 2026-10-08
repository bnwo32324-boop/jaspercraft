'use strict';
/* The JasperCraft look of the inventory, client half (owner, 2026-10-07: "make the whole inventory fit the theme of Jasper Craft ...
 * a thematic, superficial change. Don't change any of the logic."). The inventory textures are recoloured in the resource archive
 * (scripts/build-theme-pack.cjs) and the widened window, the gear column and the Field Journal draw in the same palette from their own
 * modules. What is left is the title text of the recoloured windows, drawn dark grey (4210752) on the old light grey body: it becomes
 * cream (0xF4E6BC) on the new teal one. One literal in each of these functions (every container window has its own), nothing else:
 *   E3x survival inventory ("Crafting"), Gzj creative (the tab's name), CNC crafting table (+ "Inventory"), DUK / D2Q chests and shulker
 *   boxes, C9e / FdZ hoppers and horses, CND / DRC / FQN / D1r furnace, brewing stand, dispenser and the villager's trade window, FZh the
 *   enchanting table, F84 the anvil. The advancements screen (CDJ) is not a container window and keeps its dark title.
 * Colours inside two fenced modules are swapped here as exact text blocks instead of rebuilding their stages: the gear column
 * (client-mods/gear-teavm.js, built from a catalogue file the gear builder keeps outside the repository) and the Easier Crafting /
 * Chest Finder search boxes and Sort button (client-mods/recipe-book-teavm.js; its stage sits under the wide-inventory one, which has to
 * be taken off before it could be rebuilt). strip() puts the old blocks back, and a stage rebuilt from the changed module already
 * carries the new ones.
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

/** The title-drawing functions and how many dark title colours (4210752) each draws: the window's title and "Inventory". */
const TITLE_SITES = [['E3x', 1], ['Gzj', 1], ['CNC', 2], ['DUK', 2], ['D2Q', 2], ['C9e', 2], ['FdZ', 2], ['CND', 2], ['DRC', 2], ['FQN', 2], ['D1r', 2], ['FZh', 2], ['F84', 1]];
/** [function, vanilla, patched, occurrences in that function]. */
const EDITS = TITLE_SITES.map(([fn, n]) => [fn, '4210752', JT + LABEL, n]);

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

/** The search boxes and the Sort button of the Easier Crafting / Chest Finder module (client-mods/recipe-book-teavm.js): exact lines
 * before and after. They draw in near-black and grey, which sits oddly on the teal windows. */
const RB_FROM_TO = [
  [['    rects.push({x: xOffset, y: y, w: book.textBoxSize, h: SIZE, color: book.focused ? 0xFF303030 : 0xFF1A1A1A});',
    '    rects.push({x: xOffset + 1, y: y + 1, w: book.textBoxSize - 2, h: SIZE - 2, color: 0xFF000000});'].join('\n'),
  ['    rects.push({x: xOffset, y: y, w: book.textBoxSize, h: SIZE, color: book.focused ? 0xFF3C8791 : 0xFF0E4450});',
    '    rects.push({x: xOffset + 1, y: y + 1, w: book.textBoxSize - 2, h: SIZE - 2, color: 0xFF04141A});'].join('\n')],
  ['      var border = st.focused ? 0xFFFFFFFF : 0xFF8B8B8B;', '      var border = st.focused ? 0xFFF4E6BC : 0xFF3C8791;'],
  ['      rects.push({x: b.x, y: b.y, w: b.w, h: b.h, color: 0xFF000000});', '      rects.push({x: b.x, y: b.y, w: b.w, h: b.h, color: 0xFF04141A});'],
  [['      rects.push({x: sb.x - 1, y: sb.y - 1, w: sb.w + 2, h: sb.h + 2, color: hot ? 0xFFFFFFFF : 0xFF8B8B8B});',
    '      rects.push({x: sb.x, y: sb.y, w: sb.w, h: sb.h, color: pressed ? 0xFF2E5A2E : hot ? 0xFF4A4A6A : 0xFF373737});'].join('\n'),
  ['      rects.push({x: sb.x - 1, y: sb.y - 1, w: sb.w + 2, h: sb.h + 2, color: hot ? 0xFFF4E6BC : 0xFFF0B552});',
    '      rects.push({x: sb.x, y: sb.y, w: sb.w, h: sb.h, color: pressed ? 0xFF5E3D14 : hot ? 0xFFC58A35 : 0xFFA9772C});'].join('\n')]];
/** Every text block of the client that is swapped as a whole: [before, after, what]. */
const BLOCKS = [[GEAR_FROM, GEAR_TO, 'the gear column colours'], ...RB_FROM_TO.map(([from, to], i) => [from, to, 'search box / Sort button colours #' + i])];

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
  for (const [from, to, what] of BLOCKS) {
    if (count(out, to) > 1) throw new Error(what + ': the block occurs more than once');
    if (count(out, to) === 1) out = out.split(to).join(from);
  }
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i], [s, e] = fnRange(out, fn);
    if (count(out.slice(s, e), to) > 0) out = editIn(out, fn, to, from, n, 'strip#' + i);
  }
  if (out.includes(JT)) throw new Error('theme residue after strip');
  return out;
}
function apply(base) {
  let out = base;
  EDITS.forEach(([fn, from, to, n], i) => { out = editIn(out, fn, from, to, n, 'edit#' + i); });
  for (const [from, to, what] of BLOCKS) {
    const n = count(out, from);
    if (n > 1) throw new Error(what + ': the block occurs more than once');
    if (n === 1) out = out.split(from).join(to);
    else if (count(out, to) !== 1) throw new Error(what + ': the block was not found (is its stage in the client?)');
  }
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
module.exports = {build, strip, apply, EDITS, BLOCKS, JT, LABEL, GEAR_FROM, GEAR_TO, RB_FROM_TO};
