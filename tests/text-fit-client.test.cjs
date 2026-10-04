'use strict';
/* Text that always fits (client-mods/text-fit-teavm.js): word wrap that carries colour and format codes, tooltips that
 * wrap and stay on screen (also over a widened inventory window), HUD titles / subtitles / action bar that shrink to a
 * still-readable scale and then wrap, and the builder on the live client. The real client runs in
 * tests/text-fit-browser.cjs. */
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = fs.readFileSync(path.join(ROOT, 'client-mods', 'text-fit-teavm.js'), 'utf8');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';

/** A sandbox with a 6px-per-character font (formatting codes 0px, bold +1 per character, as FontRenderer). */
function sandbox(extra) {
  const ctx = Object.assign({$rt_globals: {console: {warn() {}}}}, extra || {});
  ctx.$rt_str = s => ({s: String(s)});
  ctx.$rt_ustr = o => (o && o.s !== undefined ? o.s : String(o));
  ctx.CA = (font, o) => {
    const s = o.s; let w = 0, bold = false;
    for (let i = 0; i < s.length; i++) {
      if (s[i] === '\u00a7' && i + 1 < s.length) { const c = s[i + 1].toLowerCase(); if (c === 'l') bold = true; else if (c === 'r' || '0123456789abcdef'.includes(c)) bold = false; i++; continue; }
      w += 6 + (bold ? 1 : 0);
    }
    return w;
  };
  ctx.Y = (list, item) => { list.qN.data[list.g] = item; list.g++; list.f6++; return 1; };
  vm.createContext(ctx);
  vm.runInContext(MODULE + '\nthis.JasprTextFit = JasprTextFit;', ctx);
  return ctx;
}
const list = lines => ({qN: {data: lines.map(s => ({s}))}, g: lines.length, f6: 0});
const read = l => l.qN.data.slice(0, l.g).map(o => o.s);
const width = s => s.replace(/\u00a7./g, '').length * 6;

test('wrap: words to the width, colour and format codes carried onto each new line, long words split', () => {
  const T = sandbox().JasprTextFit, font = {};
  assert.deepEqual(Array.from(T.wrap(font, 'short', 100)), ['short']);
  const lines = Array.from(T.wrap(font, '\u00a75\u00a7oSaved once from the void, with no pearl harm at all', 120));
  assert.ok(lines.length > 1);
  assert.ok(lines.every(l => width(l) <= 120), JSON.stringify(lines));
  assert.ok(lines.slice(1).every(l => l.startsWith('\u00a75\u00a7o')), 'purple italic on every line');
  assert.equal(lines.map(l => l.replace(/\u00a7./g, '')).join(' '), 'Saved once from the void, with no pearl harm at all');
  assert.equal(T.formats('\u00a7lBold \u00a7cred \u00a7nunder'), '\u00a7c\u00a7n', 'a colour resets the formats before it');
  assert.equal(T.formats('a \u00a7r b'), '');
  const split = Array.from(T.wrap(font, 'x'.repeat(50), 60));
  assert.ok(split.every(l => width(l) <= 60) && split.join('') === 'x'.repeat(50), 'a word wider than the line is split');
});

test('tooltips: wrapped only when neither side of the cursor has room; the box always stays on screen', () => {
  const ctx = sandbox(), T = ctx.JasprTextFit, gui = {J: {}, q: 480, L: 270};
  // Fits right of the cursor: untouched, placed right.
  let l = list(['\u00a7bDiamond Sword', 'A short line']);
  T.tooltip(gui, l, 50);
  assert.deepEqual(read(l), ['\u00a7bDiamond Sword', 'A short line']);
  assert.equal(T.tooltipX(gui, 62, 78), 62);
  // The screenshot's case: a long lore line with the cursor left of the middle.
  const lore = '\u00a75Night vision, no pearl harm, saved once from the void, and 3% of every blow';
  l = list(['\u00a7dEnder Helm', lore]);
  T.tooltip(gui, l, 230);
  const out = read(l), widest = Math.max(...out.map(width));
  assert.ok(out.length > 2 && widest <= 480 - 230 - 12 - 7, JSON.stringify(out));
  assert.ok(out.slice(2).every(s => s.startsWith('\u00a75')), 'the lore keeps its colour on every line');
  const x = T.tooltipX(gui, 242, widest);
  assert.ok(x >= 7 && x + widest + 7 <= 480, 'right of the cursor and on screen: ' + x);
  // Cursor at the right edge: left of it.
  assert.equal(T.tooltipX(gui, 462, 200), 450 - 16 - 200);
  // Neither side: clamped on screen.
  const c = T.tooltipX(gui, 252, 400);
  assert.ok(c >= 7 && c + 400 + 7 <= 480, String(c));
  // A widened inventory window lays out on a narrower gui.q: the real screen width decides.
  ctx.JasprWide = {realWidth: g => (g.q | 0) + (g.$jwCut | 0)};
  vm.runInContext('var JasprWide = this.JasprWide;', ctx);
  const wide = {J: {}, q: 390, $jwCut: 90, L: 270};
  assert.equal(T.tooltipX(wide, 362, 100), 362, 'fits in the real 480px, so it stays right of the cursor');
  // Non-ArrayList lists are left alone (Arrays.asList for a one-line hover).
  const other = {g: undefined};
  T.tooltip(gui, other, 10);
  assert.deepEqual(other, {g: undefined});
});

test('HUD text: the title shrinks to a readable scale, then wraps upward; the subtitle wraps downward', () => {
  const T = sandbox().JasprTextFit, font = {}, text = s => ({s});
  // Fits at 4x on a wide screen: vanilla.
  let p = T.plan(font, text('\u00a77Ashen Vale'), 4, -10, 480, 0);
  assert.equal(p.scale, 4);
  assert.equal(p.lines.length, 1);
  // "The Weeping Hollow of the Old Kings" (35 characters, 210px) on a 370px phone: 1.5x would be too small, so 2x and two lines.
  p = T.plan(font, text('\u00a77The Weeping Hollow of the Old Kings'), 4, -10, 370, 0);
  assert.equal(p.scale, 2);
  assert.ok(p.lines.length >= 2 && p.lines.every(l => width(l.s.s) * 2 <= 370 - 16), JSON.stringify(p.lines));
  assert.equal(p.lines[p.lines.length - 1].y, -10, 'the last title line sits where vanilla draws the title');
  assert.ok(p.lines[0].y < -10, 'earlier lines above it');
  // A short title that does not fit at 4x: the largest scale that fits.
  p = T.plan(font, text('Caldera Citadel'), 4, -10, 320, 0);   // 90px: 3x = 270 <= 304
  assert.equal(p.scale, 3);
  // Subtitle: 2x, 1.5x, 1x, then wrap downward from its vanilla line.
  p = T.plan(font, text('\u00a78The world remembers.'), 2, 5, 480, 1);
  assert.equal(p.scale, 2);
  const blurb = 'A ruin of black glass where the old fires still burn under the floor and nothing grows';
  p = T.plan(font, text(blurb), 2, 5, 370, 1);
  assert.equal(p.scale, 1);
  assert.ok(p.lines.length >= 2 && p.lines[0].y === 5 && p.lines[1].y === 15, JSON.stringify(p.lines.map(l => l.y)));
  assert.ok(p.lines.every(l => l.x === -(width(l.s.s) / 2 | 0)), 'each line centred');
  // Action bar: 1x, wraps upward, no shadow.
  p = T.plan(font, text(blurb), 1, -4, 300, 2);
  assert.equal(p.scale, 1);
  assert.equal(p.shadow, 0);
  assert.equal(p.lines[p.lines.length - 1].y, -4);
});

test('builder: every hook on the live client, reversible byte for byte, stable, parses', {skip: !fs.existsSync(LIVE)}, () => {
  const {build, strip, EDITS} = require('../scripts/build-text-fit-client.cjs');
  const raw = fs.readFileSync(LIVE, 'latin1');
  const {base, result} = build(raw);
  assert.equal(strip(result), base);
  assert.equal(result.split('/*JT*/').length - 1, EDITS.reduce((n, e) => n + e[3], 0));
  assert.ok(result.includes('function JasprTextDraw('));
  assert.ok(!/case 72:FWM\(b,m,o\)/.test(result), 'the fixed 4x title scale is gone');
});
