'use strict';
/* Overloaded Armor Bar for the deployed TeaVM client: a port of LocusWay and Tfarcenim's Overloaded Armor Bar 1.0.4g
 * (MIT, https://github.com/Tfarcenim/OverpoweredArmorBar, Minecraft 1.12.2). Armour above 20 no longer disappears off
 * the end of the bar: the bar wraps, and each further 20 points is drawn over the previous row in the next colour
 * (white, orange FF5500, gold FFC747, cyan 27FFE3, green 00FF00, purple 7F00FF; the last colour repeats). A half icon is
 * split: its left half in the new colour, its right half the previous row's colour, exactly as the mod draws it.
 *
 * Kept from vanilla rather than the mod's defaults: empty armour outlines while the bar has not wrapped, and the bar's
 * height (GuiIngame's own, which already allows for every row of hearts; the mod clamps health to one row for health-bar
 * mods this client does not have). The mod's Lava Waders charm overlay belongs to another mod and is not ported.
 *
 * Armaments (owner 2026-10-07: two identical Emerald Boots, one an Ancient armament with "+40% protection", drew the same bar): the
 * armour drawn is the client's armour attribute plus JasprJournal.armorBonus(), the whole armour points the worn armaments add (the
 * server counts every enhanced piece's own armour once more by its rarity's protection bonus and sends the sum with the Field Journal's
 * data; 0 without fresh data, and without the Journal stage the bar is exactly what it was). Display only: damage is unchanged.
 *
 * Hooks GuiIngame.renderPlayerStats (DJ1): both entries into vanilla's ten-icon armour loop (with and without the
 * Regeneration bob) jump to one new state, 90, which calls JasprArmorBarDraw(gui, xStart, y, armour) and continues at
 * state 20 (the hearts). n is xStart, w the armour row's y, y the total armour value; the loop's old states stay in place
 * but are no longer entered. JasprArmorBarDraw is written in TeaVM's own resumable style (every engine call - CFh
 * GlStateManager.color, FYu Gui.drawTexturedModalRect - has its own saved state), and resets the colour to white.
 * Every anchor must occur exactly once; the stage is fenced, removable and rebuilt in place. The result must parse.
 *   node scripts/build-armor-bar-client.cjs [--source <classes.js>] [--out <file>]
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const BEGIN = '/* JASPR_ARMORBAR_BEGIN */', END = '/* JASPR_ARMORBAR_END */', HOOK = '/*JASPR_ARMORBAR_V1*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

const LOOP = 'ba=0;while(ba<10){if(y>0){i=n+(ba*8|0)|0;bb=Bd((ba*2|0)+1|0,y);if(bb<0){bc=34;bd=9;u=9;be=9;$p=23;continue _;}'
  + 'if(!bb){bc=25;bd=9;u=9;be=9;$p=25;continue _;}if(bb>0){bb=16;bc=9;bd=9;u=9;$p=28;continue _;}}';
/** [vanilla, patched]: the plain entry (no Regeneration) and the Regeneration entry. */
const EDITS = [
  [LOOP + 'ba\r\n=ba+1|0;}$p=20;continue _;case 17:',
    HOOK + '$p=90;continue _;case 90:JasprArmorBarDraw(a,n,w,y);if(B()){break _;}$p=20;continue _;case 17:'],
  ['bb=$z;z=i%bb|0;' + LOOP + 'ba=ba+1|0;}$p=20;case 20:',
    'bb=$z;z=i%bb|0;' + HOOK + '$p=90;continue _;case 20:']];
const CATALOG_ANCHOR = '\nvar JasprCreativeCatalog=[';

const MODULE = BEGIN + '\n' + String.raw`var JasprArmorBar = (function () {
  "use strict";
  // Port of Overloaded Armor Bar 1.0.4g by LocusWay and Tfarcenim, Copyright (c) 2016, MIT License
  // (notice: client-mods/armor-bar/THIRD_PARTY_NOTICES.txt). Colours in wrap order: 2nd row orange, 3rd gold ... last repeats.
  var COLORS = [[1, 1, 1], [1, 0x55 / 255, 0], [1, 0xC7 / 255, 0x47 / 255], [0x27 / 255, 1, 0xE3 / 255], [0, 1, 0], [0x7F / 255, 0, 1]];
  var SHOW_EMPTY = true;           // vanilla's empty outlines while the bar has not wrapped (the mod's option, off there)
  var lastArmor = -1, lastOps = null, stats = {draws: 0, wrapped: 0, recalcs: 0, boosted: 0};
  // The armour the worn armaments add (owner 2026-10-07: an Ancient piece with "+40% protection" showed the same bar as the plain
  // one). The server counts each enhanced piece's own armour once more by its rarity's bonus and the Field Journal (JasprJournal)
  // hands the whole-point sum over; it is 0 without fresh data (an older server, the plugin off), so the bar is then vanilla's.
  function extra() {
    try { return typeof JasprJournal !== "undefined" && JasprJournal && typeof JasprJournal.armorBonus === "function" ? JasprJournal.armorBonus() | 0 : 0; }
    catch (error) { return 0; }
  }
  function op(c, dx, u, w) { return {r: c[0], g: c[1], b: c[2], dx: dx, u: u, w: w}; }
  // ArmorBar.calculateArmorIcons + OverlayEventHandler.renderArmorBar: what to draw for this armour value.
  function ops(armor) {
    if (!(armor > 0)) return null;
    var scale = Math.floor(armor / 20), counter = armor - scale * 20;
    if (scale > 0 && counter === 0) { scale -= 1; counter = 20; }   // exactly full: stay on the previous row
    var cur = COLORS[Math.min(scale, COLORS.length - 1)], prev = COLORS[Math.max(0, Math.min(scale - 1, COLORS.length - 1))];
    var wrapped = armor > 20, out = [];
    for (var i = 0; i < 10; i++) {
      var dx = i * 8;
      if (counter >= 2) { out.push(op(cur, dx, 34, 9)); counter -= 2; }
      else if (counter === 1) { out.push(op(cur, dx, 25, 5)); out.push(op(prev, dx + 5, wrapped ? 39 : 30, 4)); counter -= 1; }
      else if (wrapped) out.push(op(prev, dx, 34, 9));
      else if (SHOW_EMPTY) out.push(op(prev, dx, 16, 9));
    }
    return out;
  }
  return {
    stats: stats,
    ops: ops,
    extra: extra,
    frame: function (armor) {
      if (armor > 0) { var more = extra(); if (more > 0) { armor += more; stats.boosted++; } }
      if (armor !== lastArmor) { lastArmor = armor; lastOps = ops(armor); stats.recalcs++; }
      if (lastOps !== null) { stats.draws++; if (armor > 20) stats.wrapped++; }
      return lastOps;
    }
  };
})();
function JasprArmorBarDraw(a, b, c, d) {
  var e, f, g, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      e = JasprArmorBar.frame(d);
      if (e === null) return;
      f = 0;
      $p = 1;
    case 1:
      if (f >= e.length) { $p = 4; continue _; }
      g = e[f];
      $p = 2;
    case 2:
      CFh(g.r, g.g, g.b, 1.0); if (B()) break _;
      $p = 3;
    case 3:
      FYu(a, b + g.dx | 0, c, g.u, 9, g.w, 9); if (B()) break _;
      f = f + 1 | 0;
      $p = 1;
      continue _;
    case 4:
      CFh(1.0, 1.0, 1.0, 1.0); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, g, $p);
}` + '\n' + END;

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }

function strip(text) {
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || count(out, BEGIN) !== 1) throw new Error('corrupt previous armour bar block');
    let end = e + END.length;
    if (out[end] === '\n') end++;
    out = out.slice(0, b) + out.slice(end);
  }
  for (const [vanilla, patched] of EDITS) if (count(out, patched) === 1) out = out.split(patched).join(vanilla);
  if (out.includes(HOOK)) throw new Error('armour bar hook present but not the expected one');
  return out;
}

function apply(base) {
  let out = base;
  EDITS.forEach(([vanilla, patched], i) => {
    if (count(out, vanilla) !== 1) throw new Error('armour loop anchor ' + i + ': expected 1, got ' + count(out, vanilla));
    out = out.split(vanilla).join(patched);
  });
  if (count(out, CATALOG_ANCHOR) !== 1) throw new Error('catalogue anchor: expected 1, got ' + count(out, CATALOG_ANCHOR));
  return out.replace(CATALOG_ANCHOR, () => '\n' + MODULE + CATALOG_ANCHOR);
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
  const target = arg('--out') || path.join(ROOT, 'candidate', 'armor-bar-client', 'classes.js');
  const raw = fs.readFileSync(source, 'latin1');
  const {base, result} = build(raw);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'overloaded-armor-bar-v1', source, sourceSha256: sha(raw), unpatchedSha256: sha(base), sha256: sha(result),
    addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1')}, null, 2));
}
module.exports = {build, strip, apply, EDITS, MODULE};
