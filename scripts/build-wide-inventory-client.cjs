'use strict';
/* Wide inventory stage for the deployed TeaVM client (owner, 2026-10-03: "The player's inventory, by default, should be
 * 1.5x as big ... This change should affect the hotbar as well"). See client-mods/wide-inventory-teavm.js for the
 * layout and the TeaVM names, scripts/java/wide-inventory for the server half.
 *
 * Every edit names the function it belongs to and how often its anchor occurs there; every patched text carries the
 * marker JW, so strip() puts the vanilla text back byte for byte. Two functions are generated from the client's own
 * code: JasprWideMergeIds (mergeItemStack B_G walking an explicit slot list) and JasprWideShiftPlayer
 * (ContainerPlayer.transferStackInSlot FH2 with the wide slot orders); both go into the fenced module block before
 * the TeaVM footer, after any Mo' Bends block. The result must parse, and a rebuild must be stable.
 *
 *   node scripts/build-wide-inventory-client.cjs --source <classes.js> [--out <file>]
 *   node scripts/build-wide-inventory-client.cjs --unpatch --source <classes.js> --out <file>
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = path.join(ROOT, 'client-mods', 'wide-inventory-teavm.js');
const BEGIN = '/* JASPR_WIDEINV_BEGIN */', END = '/* JASPR_WIDEINV_END */', JW = '/*JW*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

/** [function, vanilla, patched, occurrences in that function]. */
const EDITS = [
  // InventoryPlayer: 56 main slots, hotbar = 0..8 and 36..40, off hand at flat index 60, fill order.
  ['B4a', 'd=new Biv;e=36;', 'd=new Biv;e=' + JW + '56;', 1],
  ['Bac', 'return b>=0&&b<9?1:0;', 'return ' + JW + 'b>=0&&b<9||b>=36&&b<41?1:0;', 1],
  ['GlC', 'c=40;$p=3;', 'c=' + JW + '60;$p=3;', 1],
  ['GlC', 'if(c)return 40;', 'if(c)return ' + JW + '60;', 1],
  ['DCu', '_:while(true){switch($p){case 0:b=0;c=a.eL;$p=1;',
    '_:while(true){switch($p){case 0:' + JW + '$p=290;case 290:$z=JasprWideFirstEmpty(a);if(B()){break _;}return $z;case 291:b=0;c=a.eL;$p=1;', 1],
  // Mouse wheel over 9 or 14 hotbar positions.
  ['Dka', 'e.gP=e.gP-b|0;while(true){b=e.gP;if(b>=0)break;e.gP=b+9|0;}while(true){b=e.gP;if(b<9)break;e.gP=b-9|0;}',
    JW + 'e.gP=JasprWide.scroll(e.gP,b);', 1],
  // HUD hotbar: 282px, 14 cells, off hand / attack indicator beside the wider bar.
  ['Ckt', 'm=l-91|0;', 'm=' + JW + 'JasprWide.hotbarLeft(l,b.f7);', 1],
  ['Ckt', 'case 7:FYu(a,m,n,o,p,q,r);if(B()){break _;}s=(m-1|0)+(h.bx.gP*20|0)|0;',
    'case 7:FYu(a,m,n,o,p,q,r);if(B()){break _;}$p=291;case 291:' + JW + 'if(JasprWide.on()){JasprWideHotbarExt(a,m,n);if(B()){break _;}}s=(m-1|0)+(JasprWide.pos(h.bx.gP)*20|0)|0;', 1],
  ['Ckt', 'o=l+91|0;s=b.ff-23|0;', 'o=' + JW + 'm+JasprWide.span()|0;s=b.ff-23|0;', 1],
  ['Ckt', 'r=l-90|0;if(p>=9)', 'r=' + JW + 'm+1|0;if(p>=JasprWide.slots())', 1],
  ['Ckt', 'p=p+1|0;if(p>=9){$p=15;continue _;}', 'p=p+1|0;if(p>=' + JW + 'JasprWide.slots()){$p=15;continue _;}', 1],
  ['Ckt', 'case 16:$z=DA(i,p);', 'case 16:$z=DA(i,' + JW + 'JasprWide.index(p));', 1],
  ['Ckt', 'p=(l+91|0)+10|0;', 'p=(' + JW + 'm+JasprWide.span()|0)+10|0;', 1],
  ['Ckt', 's=(l+91|0)+6|0;', 's=(' + JW + 'm+JasprWide.span()|0)+6|0;', 1],
  // ContainerPlayer: armour 56..59, off hand 60, extension slots once the server agreed; wide shift-click.
  ['BON', '36+(3-f|0)|0', JW + '56+(3-f|0)|0', 3],
  ['BON', 'Ix(d,b,40,77,62)', 'Ix(d,b,' + JW + '60,77,62)', 'all'],
  ['BON', 'case 6:DE9(a,d);if(B()){break _;}return;', 'case 6:DE9(a,d);if(B()){break _;}$p=290;case 290:' + JW + 'JasprWideAdopt(a);if(B()){break _;}return;', 1],
  ['FH2', '_:while(true){switch($p){case 0:$p=1;case 1:Cj();',
    '_:while(true){switch($p){case 290:' + JW + '$z=JasprWideShiftPlayer(a,b,c);if(B()){break _;}return $z;case 0:if(a.cn.g>=66){$p=290;continue _;}$p=1;case 1:Cj();', 1],
  // Containers shown in a GuiContainer get the extension; the screen is laid out and drawn with the pocket.
  ['B0Z', 'a.h2=b;a.a3J=1;return;', 'a.h2=b;a.a3J=1;$p=290;case 290:' + JW + 'JasprWideAdopt(b);if(B()){break _;}return;', 1],
  ['C9y', 'a.q=c;a.L=d;', 'a.q=' + JW + 'JasprWide.width(a,c);a.L=d;', 1],
  // handleMouseInput scales the mouse by this.width: use the real screen width, or clicks miss their slots.
  ['Fpj', 'c=W(b!==null?b.dnu:(-1),a.q);', 'c=W(b!==null?b.dnu:(-1),' + JW + 'JasprWide.realWidth(a));', 1],
  ['CA_', 'a.l7=(a.L-a.gx|0)/2|0;return;', 'a.l7=(a.L-a.gx|0)/2|0;' + JW + 'JasprWide.layout(a);return;', 1],
  ['C6T', 'case 1:a.Fz(d,b,c);if(B()){break _;}$p=2;', 'case 1:a.Fz(d,b,c);if(B()){break _;}$p=290;case 290:' + JW + 'JasprWidePocketDraw(a);if(B()){break _;}$p=2;', 1],
  ['Gmh', '$z=a.d1Y(b,c,h,i);if(B()){break _;}j=$z;', '$z=a.d1Y(b,c,h,i);if(B()){break _;}j=$z;' + JW + 'if(j&&JasprWide.inPocket(a,b,c))j=0;', 1],
  ['EGS', '$z=a.d1Y(b,c,f,g);if(B()){break _;}h=$z;', '$z=a.d1Y(b,c,f,g);if(B()){break _;}h=$z;' + JW + 'if(h&&JasprWide.inPocket(a,b,c))h=0;', 1],
  // Creative: inventory-tab pocket slots, hotbar window slots, shift-click clears the whole hotbar.
  ['Coc', 'else if(h==45){i.Lr=35;i.Fg=20;}else if(h<g.cn.g){', 'else if(h==45){i.Lr=35;i.Fg=20;}else if(' + JW + 'h>=46&&h<66){JasprWide.creativeSlot(i,h);}else if(h<g.cn.g){', 1],
  ['Dcy', 'c=((c-i|0)+9|0)+36|0;', 'c=' + JW + 'JasprWide.creativeWindow(c);', 1],
  ['Dcy', 'c=((i-c|0)+9|0)+36|0;', 'c=' + JW + 'JasprWide.creativeWindow(i);', 1],
  ['Fu1', 'if(c>=(d-9|0)&&c<d)', 'if(c>=' + JW + '45&&c<d)', 1],
  ['Ghi', 'd=36+f.gP|0;', 'd=' + JW + 'JasprWide.heldWindow(f.gP);', 1],
  // Connection: hello after MC|Brand, the answer on jaspr:inv, hotbar set-slot animation for the extension.
  ['E8R', 'case 9:d.wd(c);if(B()){break _;}d=new BO7;', 'case 9:d.wd(c);if(B()){break _;}$p=291;case 291:' + JW + 'JasprWideHello(b);if(B()){break _;}d=new BO7;', 1],
  ['Cyr', '_:while(true){switch($p){case 0:if($rt_ustr(b.S$)==="JASPR|Revive")',
    '_:while(true){switch($p){case 2990:' + JW + 'JasprWideServerSaid(a);if(B()){break _;}return;case 0:if($rt_ustr(b.S$)==="jaspr:inv"){$p=2990;continue _;}if($rt_ustr(b.S$)==="JASPR|Revive")', 1],
  ['Cnj', '!g&&a.bk9>=36&&f<45', '!g&&(' + JW + 'a.bk9>=36&&f<45||f>=46&&f<51)', 3]];

/** Survivor Gear module text (client-mods/gear-teavm.js carries the same change, so a gear build made from the current
 * module already has it): its trinket column goes right of the pocket, its HUD bar right of the 282px hotbar.
 * [label, text of the older module, current code]; applied only while the older text is there. */
const MODULE_EDITS = [
  ['gear layout', '    var left = gui.is | 0, width = gui.q | 0, x0 = (gui instanceof APz ? 176 : 195) + 3;',
    '    var wide = typeof JasprWide !== "undefined" && JasprWide ? JasprWide : null;\n'
    + '    var left = gui.is | 0, width = wide ? wide.realWidth(gui) | 0 : gui.q | 0;\n'
    + '    var x0 = (gui instanceof APz ? 176 : 195) + 3 + (wide ? wide.pocketWidth(gui) | 0 : 0);'],
  ['gear hud', '      var w = width | 0, h = height | 0, x0 = (w / 2 | 0) + 91 + 31, room = w - x0 - 3;',
    '      var w = width | 0, h = height | 0, room;\n'
    + '      var x0 = (typeof JasprWide !== "undefined" && JasprWide ? JasprWide.hotbarRight(w) | 0 : (w / 2 | 0) + 91) + 31;\n'
    + '      room = w - x0 - 3;']];
const marked = code => code.replace(/^(\s*)/, '$1' + JW);

/** States a patched function gains; the vanilla function must not use them already. */
const NEW_STATES = {DCu: [290, 291], Ckt: [291], BON: [290], FH2: [290], B0Z: [290], C6T: [290], E8R: [291], Cyr: [2990]};
/** TeaVM names the module and hooks call; all must be declared in the client. */
const NATIVE = ['Biv', 'YD', 'ID', 'HEH', 'Dk', 'DE9', 'FYu', 'D49', 'DNC', 'CFh', 'DA', 'CCH', 'AKy', 'Iu', 'Fru', 'Lg', 'FuF', 'BgN', 'Bm',
  'FX', 'Ds', 'B', 'FT', 'Cj', 'Ktg', 'B_G', 'FH2'];

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
  if (expected === 'all') {
    if (n < 1 || n !== count(text, from)) throw new Error(label + ': ' + name + ' holds ' + n + ' of ' + count(text, from) + ' anchors');
  } else if (n !== expected) throw new Error(label + ': expected ' + expected + ' in ' + name + ', got ' + n);
  return text.slice(0, s) + body.split(from).join(to) + text.slice(e);
}
function declared(text, name) {
  const e = name.replace(/\$/g, '\\$');
  return new RegExp('(?:^|[\\s;{}(])function ' + e + '\\(|(?:^|[\\s;,{}])(?:var|let) ' + e + '\\s*[=;,]|[;,\\s]' + e + '\\s*=').test(text);
}

/** mergeItemStack over an explicit slot list: c = window slot numbers, d = their count, e = 0 (forward). */
function mergeIds(base) {
  const [s, e] = fnRange(base, 'B_G');
  let f = base.slice(s, e);
  if (!f.startsWith('function B_G(a,b,c,d,e){')) throw new Error('B_G signature changed');
  f = 'function JasprWideMergeIds(a,b,c,d,e){' + f.slice('function B_G(a,b,c,d,e){'.length);
  const swap = (from, to, n) => { if (count(f, from) !== n) throw new Error('B_G: expected ' + n + ' x ' + from + ', got ' + count(f, from)); f = f.split(from).join(to); };
  swap('g=!e?c:d-1|0', 'g=0', 2);
  swap('Bm(i,g)', 'Bm(i,c[g])', 2);
  if (count(f, 'Bm(') !== 2) throw new Error('B_G reads slots elsewhere');
  return f.replace(/\n$/, '');
}
/** ContainerPlayer.transferStackInSlot with the wide orders (identical to JasprWide.shiftPlayer on the server). */
function shiftPlayer(base) {
  const [s, e] = fnRange(base, 'FH2');
  let f = base.slice(s, e);
  if (!f.startsWith('function FH2(a,b,c){')) throw new Error('FH2 signature changed');
  f = 'function JasprWideShiftPlayer(a,b,c){' + f.slice('function FH2(a,b,c){'.length);
  const ids = name => 'g=JasprWide.ids("' + name + '");j=g.length;k=0;';
  const swap = (from, to, n) => { if (count(f, from) !== n) throw new Error('FH2: expected ' + n + ' x ' + from + ', got ' + count(f, from)); f = f.split(from).join(to); };
  swap('if(!c){g=9;j=45;k=1;$p=7;', 'if(!c){' + ids('result') + '$p=7;', 1);
  swap('if(c>=1&&c<5){g=9;j=45;k=0;$p=9;', 'if(c>=1&&c<5){' + ids('all') + '$p=9;', 1);
  swap('if(c>=5&&c<9){g=9;j=45;k=0;$p=13;', 'if(c>=5&&c<9){' + ids('all') + '$p=13;', 1);
  swap('g=8-i.en|0;j=g+1|0;k=0;$p=18;', 'g=[8-i.en|0];j=1;k=0;$p=18;', 1);
  swap('g=45;j=46;k=0;$p=21;', 'g=[45];j=1;k=0;$p=21;', 1);
  swap('if(c>=9&&c<36){g=36;j=45;k=0;$p=24;', 'if(c>=9&&c<36||c>=51&&c<66){' + ids('hotbar') + '$p=24;', 3);
  swap('if(c>=36&&c<45){g=9;j=36;k=0;$p=26;', 'if(c>=36&&c<45||c>=46&&c<51){' + ids('main') + '$p=26;', 3);
  swap('g=9;j=45;k=0;$p=23;', ids('all') + '$p=23;', 3);
  swap('B_G(a,h,g,j,k)', 'JasprWideMergeIds(a,h,g,j,k)', 8);
  if (/B_G\(|g=9;|j=45;|j=36;/.test(f)) throw new Error('FH2 kept a vanilla range');
  return f.replace(/\n$/, '');
}

function strip(text) {
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || out.indexOf(BEGIN, b + 1) >= 0) throw new Error('wide inventory block incomplete or duplicated');
    out = out.slice(0, b) + out.slice(e + END.length + 1);
  }
  for (let i = EDITS.length - 1; i >= 0; i--) {
    const [fn, from, to, n] = EDITS[i];
    if (count(out, to) > 0) out = editIn(out, fn, to, from, n === 'all' ? count(out, to) : n, 'strip#' + i);
  }
  for (const [label, older, code] of MODULE_EDITS) {
    const n = count(out, marked(code));
    if (n > 1) throw new Error(label + ': patched more than once');
    if (n === 1) out = out.split(marked(code)).join(older);
  }
  if (out.includes(JW) || out.includes('JasprWideHello') || out.includes(BEGIN)) throw new Error('wide inventory residue after strip');
  return out;
}

function apply(base) {
  const missing = NATIVE.filter(n => !declared(base, n));
  if (missing.length) throw new Error('TeaVM names missing (re-audit): ' + missing.join(', '));
  for (const [fn, states] of Object.entries(NEW_STATES)) {
    const [s, e] = fnRange(base, fn);
    for (const st of states) if (base.slice(s, e).includes('case ' + st + ':')) throw new Error(fn + ' already has state ' + st);
  }
  const generated = mergeIds(base) + '\n' + shiftPlayer(base) + '\n';
  let out = base;
  EDITS.forEach(([fn, from, to, n], i) => { out = editIn(out, fn, from, to, n, 'edit#' + i); });
  for (const [label, older, code] of MODULE_EDITS) {
    if (count(out, older) === 1) out = out.split(older).join(marked(code));
    else if (count(out, code) !== 1) throw new Error(label + ': neither the older module text nor the current code found once');
  }
  const module = fs.readFileSync(MODULE, 'latin1');
  if (/[^\x00-\x7f]/.test(module)) throw new Error('module must be ASCII');
  const end = out.lastIndexOf('}));');
  if (end < 0) throw new Error('TeaVM module footer not found');
  return out.slice(0, end) + BEGIN + '\n' + module.replace(/\n$/, '') + '\n' + generated + END + '\n' + out.slice(end);
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
    new vm.Script(Buffer.from(base, 'latin1').toString('utf8'), {filename: 'classes.js'});
    fs.writeFileSync(out, Buffer.from(base, 'latin1'));
    console.log(JSON.stringify({stage: 'wide-inventory-v1', unpatched: out, sha256: sha(base)}, null, 2));
  } else {
    const target = arg('--out') || path.join(ROOT, 'candidate', 'wide-client', 'classes.js');
    const {base, result} = build(raw);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(result, 'latin1'));
    console.log(JSON.stringify({stage: 'wide-inventory-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
      edits: EDITS.length, addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
  }
}
module.exports = {build, strip, apply, EDITS, BEGIN, END};
