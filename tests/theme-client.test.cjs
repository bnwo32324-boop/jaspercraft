'use strict';
// The JasperCraft look of the inventory (owner 2026-10-07: "make the whole inventory fit the theme of Jasper Craft. I just want a
// thematic, superficial change. Don't change any of the logic."). Colours only, so the tests pin what must NOT change as much as what
// does: the recoloured textures keep every shape and every pixel outside the windows, the wide window / gear column / journal draw in
// the textures' palette, the two title colours are the only client text that moves, every other stage still rebuilds on top of it, and
// the text stays readable on the new colours.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const GAME = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/';
const LIVE = GAME + 'classes.js', EPK = GAME + 'assets.epk';
const theme = require('../scripts/jasper-theme.cjs');
const stage = require('../scripts/build-theme-client.cjs');
const png = require('../scripts/png-codec.cjs');
const P = theme.JASPER;
const read = f => fs.readFileSync(path.join(ROOT, f), 'latin1');
const argbHex = name => 'FF' + P[name].slice(1).toUpperCase();

// ------------------------------------------------------------------------------------------------------ the palette
test('palette: the wide window, the gear column and the journal draw in the colours the textures are recoloured with', () => {
  const wide = read('client-mods/wide-inventory-teavm.js');
  for (const [id, name] of [['OUTLINE', 'outline'], ['FRAME_HI', 'frameHi'], ['BODY', 'body'], ['FRAME_SHADE', 'frameShade'], ['SLOT_DARK', 'slotDark'], ['SLOT_HI', 'slotHi'], ['SLOT', 'slot']]) {
    assert.ok(new RegExp('\\b' + id + ' = 0x' + argbHex(name) + ' \\| 0').test(wide), 'wide window ' + id + ' is the palette ' + name);
  }
  // The gear column's plan(): the new block of the theme stage is the module's text, and its colours are the palette's.
  const gear = read('client-mods/gear-teavm.js');
  assert.equal(gear.split(stage.GEAR_TO).length - 1, 1, 'the gear module carries the block the stage swaps in');
  assert.equal(gear.includes(stage.GEAR_FROM), false);
  const used = new Set(stage.GEAR_TO.match(/0xFF[0-9A-F]{6}/g));
  assert.deepEqual([...used].sort(), ['outline', 'body', 'frameHi', 'frameShade', 'slotDark', 'slotHi', 'slot'].map(n => '0x' + argbHex(n)).sort());
  // The journal: the slot-like inset, the amber accent, the bronze lines.
  const j = read('client-mods/journal-teavm.js'), c = name => { const m = new RegExp('\\b' + name + ': 0x([0-9A-F]{8})').exec(j); assert.ok(m, name); return m[1]; };
  assert.equal(c('edgeDark'), argbHex('slotDark'));
  assert.equal(c('edgeLight'), argbHex('slotHi'));
  assert.equal(c('accent'), argbHex('frameHi'));
  assert.equal(c('gold'), argbHex('frameHi'));
  assert.equal(c('line'), argbHex('frameShade'));
  assert.equal(c('white'), argbHex('label'), 'the main text colour is the window title colour');
  // The title colour of the client.
  assert.equal(stage.LABEL, parseInt(P.label.slice(1), 16));
  assert.equal(theme.argb('#F0B552'), 0xFFF0B552 | 0);
});

function luminance(hex) {
  const [r, g, b] = theme.rgb(hex).map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const contrast = (a, b) => { const [x, y] = [luminance(a), luminance(b)].sort((m, n) => n - m); return (x + 0.05) / (y + 0.05); };
test('legibility: every text colour reads on the colour it is drawn on, and the slots stand out from the body', () => {
  const j = read('client-mods/journal-teavm.js'), col = name => '#' + new RegExp('\\b' + name + ': 0xFF([0-9A-F]{6})').exec(j)[1];
  const onScreen = ['white', 'gray', 'red', 'gold', 'green', 'aqua', 'yellow', 'orange'];
  for (const n of onScreen) assert.ok(contrast(col(n), col('screen')) >= 4.5, `${n} on the panel screen: ${contrast(col(n), col('screen')).toFixed(1)}`);
  assert.ok(contrast(col('dim'), col('screen')) >= 3, 'the faint text (page counter, hints) still reads: ' + contrast(col('dim'), col('screen')).toFixed(1));
  assert.ok(contrast(col('white'), col('tabOn')) >= 4.5, 'selected tab label');
  assert.ok(contrast(col('gray'), col('tabOff')) >= 4.5, 'unselected tab label');
  assert.ok(contrast(col('white'), col('button')) >= 3, 'button label (bold shadowed text on brass)');
  assert.ok(contrast(P.label, P.body) >= 7, 'the window title on the body');
  assert.ok(contrast(P.slotHi, P.slot) >= 2.5 && contrast(P.slot, P.body) >= 1.2, 'a slot is visible as a socket in the window');
  assert.ok(contrast(P.frameHi, P.body) >= 3, 'the frame highlight stands out');
});

// ---------------------------------------------------------------------------------------------------- the textures
const hasEpk = fs.existsSync(EPK);
function liveTextures() {
  const {decode} = require('../scripts/merge-apocalypse-assets.cjs');
  const parsed = decode(fs.readFileSync(EPK)), by = new Map(parsed.entries.map(e => [e.name, e.value]));
  return Object.keys(theme.TEXTURES).map(t => [t, by.get('assets/minecraft/textures/gui/' + t)]);
}
const hexAt = (img, x, y) => { const i = (y * img.w + x) * 4; return [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2], img.rgba[i + 3]].map(v => v.toString(16).padStart(2, '0')).join(''); };
test('textures: every shape and every pixel outside the windows is kept; only the colours of the window change', {skip: !hasEpk}, () => {
  for (const [texture, bytes] of liveTextures()) {
    assert.ok(bytes, texture + ' exists in the archive');
    const before = png.decode(bytes), spec = theme.TEXTURES[texture];
    // The live archive may already carry the theme; the vanilla reference is the other way round then.
    const after = theme.retheme(before, texture);
    assert.equal(after.w, before.w); assert.equal(after.h, before.h);
    for (let y = 0; y < before.h; y++) for (let x = 0; x < before.w; x++) {
      const i = (y * before.w + x) * 4, inside = x < spec.w && y < spec.h;
      assert.equal(after.rgba[i + 3] === 0, before.rgba[i + 3] === 0, `${texture}: transparency at ${x},${y}`);
      if (!inside && !spec.sheet) assert.deepEqual(after.rgba.subarray(i, i + 4), before.rgba.subarray(i, i + 4), `${texture}: pixel ${x},${y} outside the window changed`);
    }
    // Idempotent: a themed texture stays as it is.
    if (spec.sheet) assert.deepEqual(theme.retheme(after, texture).rgba, after.rgba, texture + ' recolours once'); else assert.equal(theme.retheme(after, texture), after, texture + ' recolours once');
    assert.ok(Buffer.from(theme.recolour(png.encode(after), texture)).equals(png.encode(after)), texture + ' byte-stable');
    if (!spec.sheet) {
      // No vanilla grey is left in the window.
      const left = new Set();
      for (let y = 0; y < spec.h; y++) for (let x = 0; x < spec.w; x++) { const c = hexAt(after, x, y); if (['c6c6c6ff', '8b8b8bff', '373737ff', '555555ff', 'ffffffff', '000000ff'].includes(c)) left.add(c); }
      assert.deepEqual([...left], [], texture + ' still has vanilla colours');
      assert.equal(hexAt(after, 2, 1), P.frameHi.slice(1).toLowerCase() + 'ff', 'the frame highlight starts at 2,1 as in the vanilla texture');
    }
  }
});

test('textures: the survival window keeps its 46 slot cells, each a dark top/left edge, a light bottom/right edge and an inside', {skip: !hasEpk}, () => {
  const [, bytes] = liveTextures().find(([t]) => t === 'container/inventory.png');
  const img = theme.retheme(png.decode(bytes), 'container/inventory.png');
  const px = (x, y) => '#' + hexAt(img, x, y).slice(0, 6);
  const want = n => P[n].toLowerCase();
  const cells = [[7, 7], [7, 25], [7, 43], [7, 61], [76, 61], [97, 17], [115, 17], [97, 35], [115, 35], [153, 27]];
  for (let j = 0; j < 3; j++) for (let i = 0; i < 9; i++) cells.push([7 + 18 * i, 83 + 18 * j]);
  for (let i = 0; i < 9; i++) cells.push([7 + 18 * i, 141]);
  for (const [cx, cy] of cells) for (let y = 0; y < 18; y++) for (let x = 0; x < 18; x++) {
    const corner = (x === 17 && y === 0) || (x === 0 && y === 17);
    const expect = corner ? 'slot' : y === 0 || x === 0 ? 'slotDark' : y === 17 || x === 17 ? 'slotHi' : 'slot';
    assert.equal(px(cx + x, cy + y), want(expect), `cell ${cx},${cy} pixel ${x},${y}`);
  }
  // The frame: outline, 2px highlight, body; bottom/right: body, 2px shade, outline.
  assert.deepEqual([px(100, 0), px(100, 1), px(100, 2), px(100, 3)], [want('outline'), want('frameHi'), want('frameHi'), want('body')]);
  assert.deepEqual([px(172, 80), px(173, 80), px(174, 80), px(175, 80)], [want('body'), want('frameShade'), want('frameShade'), want('outline')]);
  // The player recess keeps its box (its edge is a slot's); the crafting arrow is drawn in the accent colour.
  assert.equal(px(25, 7), want('slotDark'));
  assert.equal(px(75, 7), want('slot'), 'the recess box corner');
  assert.equal(px(140, 35), want('arrow'));
  // The rest of the sheet (the gear icons below the window, the book buttons right of it) is untouched.
  const before = png.decode(bytes);
  assert.equal(hexAt(img, 144, 168), hexAt(before, 144, 168));
});

// ---------------------------------------------------------------------------------------------------- the client
const hasLive = fs.existsSync(LIVE);
test('stage: two title colours and the gear column block, nothing else; reversible byte for byte and stable', {skip: !hasLive}, () => {
  const raw = fs.readFileSync(LIVE, 'latin1');
  const built = stage.build(raw);
  assert.equal(stage.strip(built.result), built.base);
  assert.equal(stage.apply(stage.strip(built.result)), built.result);
  assert.equal(stage.build(built.result).result, built.result, 'building on a client that already carries the stage changes nothing');
  assert.equal(built.result.split(stage.JT).length - 1, 2, 'two marked edits');
  assert.equal(built.result.split(String(stage.LABEL)).length - 1 >= 2, true);
  const body = name => { const i = built.result.indexOf('\nfunction ' + name + '(') + 1; return built.result.slice(i, built.result.indexOf('\nfunction ', i + 5)); };
  assert.ok(body('E3x').includes('g=97;b=8;c=' + stage.JT + stage.LABEL + ';$p=2;case 2:Efa(d,e,g,b,c);'), '"Crafting" in the survival inventory');
  assert.ok(body('Gzj').includes('g=8;b=6;c=' + stage.JT + stage.LABEL + ';$p=6;case 6:Efa(f,e,g,b,c);'), 'the tab name in the creative inventory');
  assert.equal(built.result.split(stage.GEAR_TO).length - 1, 1, 'the gear column colours');
  assert.equal(built.base.split(stage.GEAR_FROM).length - 1 + built.base.split(stage.GEAR_TO).length - 1, 1);
  // The size of the change is the two literals and the block: nothing else moved.
  const added = Buffer.byteLength(built.result, 'latin1') - Buffer.byteLength(built.base, 'latin1');
  assert.ok(added > 0 && added < 400, 'added ' + added + ' bytes');
  // Every other window keeps the dark title colour.
  assert.ok(built.result.split('4210752').length - 1 >= 12, 'chests, furnaces ... keep their dark titles');
});

test('stacking: every other stage still rebuilds on a client that carries this one, and the stages commute', {skip: !hasLive}, () => {
  const plainLive = stage.strip(fs.readFileSync(LIVE, 'latin1'));
  const withTheme = stage.build(plainLive).result;
  for (const name of ['build-wide-inventory-client', 'build-journal-client', 'build-text-fit-client', 'build-silent-effects-client', 'build-nbt-skin-client', 'build-armor-bar-client']) {
    const other = require('../scripts/' + name + '.cjs');
    const plain = other.build(plainLive), stacked = other.build(withTheme);
    assert.equal(stage.strip(stacked.result), plain.result, name + ': rebuilt on top of the theme stage it equals the plain rebuild once the theme stage is taken off');
    assert.equal(stacked.result.includes(stage.JT), true, name + ': the theme stage stays in place');
    assert.equal(stage.strip(stage.build(plain.result).result), plain.result, name + ': the theme stage comes off its rebuild cleanly');
  }
});

test('builders: the modules the stage depends on are what the deployed palette needs', () => {
  const ascii = f => assert.equal(/[^\x00-\x7f]/.test(read(f)), false, f + ' must stay ASCII');
  for (const f of ['client-mods/wide-inventory-teavm.js', 'client-mods/journal-teavm.js', 'client-mods/gear-teavm.js', 'scripts/build-theme-client.cjs', 'scripts/jasper-theme.cjs']) ascii(f);
  assert.equal(read('client-mods/journal-teavm.js').includes('innerHTML'), false);
});
