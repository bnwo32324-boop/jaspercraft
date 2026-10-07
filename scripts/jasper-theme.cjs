'use strict';
// The JasperCraft look of the inventory (owner, 2026-10-07: "make the whole inventory fit the theme of Jasper Craft. I just want a
// thematic, superficial change. Don't change any of the logic."). Colours only: the inventory textures are recoloured pixel for
// pixel (same geometry, same slots, same sprites), the widened window, the gear column and the Field Journal panel draw in the
// same palette (client-mods/*), and two label colours in the client follow (scripts/build-theme-client.cjs).
// The palette comes from the game's own art: the teal of the cat favicon, torchlight amber from the banner, a night sky.
const png = require('./png-codec.cjs');

const rgb = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
/** '#RRGGBB' -> signed 32-bit ARGB int, as the client's drawRect takes it. */
const argb = hex => (0xFF000000 | parseInt(hex.slice(1), 16)) | 0;
const hexOf = (r, g, b) => '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => { const A = rgb(a), B = rgb(b); return hexOf(...A.map((v, i) => Math.round(v + (B[i] - v) * t))); };

/** frame*: the window frame (outline, 2px highlight top/left, 2px shade bottom/right); body; slot*: a slot (dark top/left edge,
 * light bottom/right edge, inside); label: the colour of the window's title text on the body. sky*, ridge*: the night scene
 * behind the player model. */
const JASPER = {
  outline: '#03171B', frameHi: '#F0B552', frameShade: '#7A4E1C', body: '#12454F',
  slotDark: '#06222A', slotHi: '#3C8791', slot: '#0B2F37', arrow: '#F0B552', del: '#6E2F2F',
  label: '#F4E6BC', skyTop: '#04141A', skyMid: '#0C3A44', skyLow: '#6A4A24', ridge: '#041A20', ridgeRim: '#0E4450', star: '#E9E2C8', starLow: '#9FB6B4'
};
const PALETTES = {jasper: JASPER};

const hexAt = (img, x, y) => { const i = (y * img.w + x) * 4; return [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2], img.rgba[i + 3]].map(v => v.toString(16).padStart(2, '0')).join(''); };

/** The window to recolour in each texture: pixels (w x h) from the top left; arrow: [x0, y0, x1, y1] of the crafting arrow, if any. */
const TEXTURES = {
  'container/inventory.png': {w: 176, h: 166, arrow: [133, 18, 153, 40]},
  'container/creative_inventory/tab_inventory.png': {w: 195, h: 136},
  'container/creative_inventory/tab_items.png': {w: 195, h: 136},
  'container/creative_inventory/tab_item_search.png': {w: 195, h: 136},
  'container/creative_inventory/tabs.png': {w: 256, h: 256, sheet: true}
};

/** The night scene in the player recess: sky (ordered-dithered gradient), stars, a mountain ridge. box = interior black pixels. */
function scene(p, box, x, y) {
  const h = box.y1 - box.y0 + 1, t = (y - box.y0) / (h - 1);
  const bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]][y & 3][x & 3] / 16 - 0.5;
  const u = Math.max(0, Math.min(1, t + bayer * 0.12));
  const ridge = Math.round(6 + 3 * Math.sin(x * 0.33) + 2 * Math.sin(x * 0.91 + 1.3));
  const depth = box.y1 - y;                                  // 0 at the bottom row
  if (depth < ridge - 1) return p.ridge;
  if (depth === ridge - 1) return p.ridgeRim;
  return u < 0.62 ? mix(p.skyTop, p.skyMid, u / 0.62) : mix(p.skyMid, p.skyLow, (u - 0.62) / 0.38);
}
const STARS = [[0.05, 0.06], [0.28, 0.03], [0.55, 0.1], [0.82, 0.05], [0.14, 0.2], [0.46, 0.17], [0.9, 0.24], [0.2, 0.34], [0.7, 0.3], [0.38, 0.09]];

/** True when the texture carries this palette already (the frame's highlight pixel at 2,1 is the palette's). */
function isThemed(img, name) { return hexAt(img, 2, 1) === PALETTES[name].frameHi.slice(1).toLowerCase() + 'ff'; }

/** Recolours one texture (decoded RGBA); returns a new image. Throws on a colour it does not know inside the window. */
function retheme(img, texture, name = 'jasper') {
  const p = PALETTES[name], spec = TEXTURES[texture];
  if (!p) throw new Error('unknown palette ' + name);
  if (!spec) throw new Error('no theme rules for ' + texture);
  if (isThemed(img, name)) return img;
  if (hexAt(img, 2, 1) !== 'ffffffff' && !spec.sheet) throw new Error(texture + ': neither vanilla nor themed (pixel 2,1 is ' + hexAt(img, 2, 1) + ')');
  const out = Buffer.from(img.rgba);
  const put = (x, y, hex) => { const i = (y * img.w + x) * 4, c = rgb(hex); out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2]; out[i + 3] = 255; };
  // The recess behind the player model: the black pixels well inside the window.
  let box = null;
  if (!spec.sheet) {
    for (let y = 4; y < spec.h - 5; y++) for (let x = 4; x < spec.w - 5; x++) if (hexAt(img, x, y) === '000000ff') {
      box = box || {x0: x, y0: y, x1: x, y1: y}; box.x0 = Math.min(box.x0, x); box.x1 = Math.max(box.x1, x); box.y0 = Math.min(box.y0, y); box.y1 = Math.max(box.y1, y);
    }
  }
  const unknown = new Map();
  for (let y = 0; y < spec.h; y++) for (let x = 0; x < spec.w; x++) {
    const c = hexAt(img, x, y);
    if (c.slice(6) === '00') continue;                       // transparent (rounded corners, unused sheet area) stays
    const edgeBand = x <= 2 || y <= 2;
    if (spec.sheet) {
      // Tab and scroller sprites: the same colour classes everywhere (their highlights are all frame highlights).
      const m = {c6c6c6ff: 'body', '555555ff': 'frameShade', '373737ff': 'slotDark', ffffffff: 'frameHi', '8b8b8bff': 'slot', '000000ff': 'outline'}[c];
      if (m) put(x, y, p[m]);
      continue;                                              // every other colour (the unused red/blue/green labels) is kept
    }
    if (c === 'c6c6c6ff' || c === 'c5c5c5ff') put(x, y, p.body);
    else if (c === '555555ff') put(x, y, p.frameShade);
    else if (c === '373737ff') put(x, y, p.slotDark);
    else if (c === 'ffffffff') put(x, y, edgeBand ? p.frameHi : p.slotHi);
    else if (c === '000000ff') put(x, y, box && x >= box.x0 && x <= box.x1 && y >= box.y0 && y <= box.y1 ? scene(p, box, x, y) : p.outline);
    else if (c === '8b8b8bff') {
      const a = spec.arrow;
      put(x, y, a && x >= a[0] && y >= a[1] && x < a[2] && y < a[3] ? p.arrow : p.slot);
    } else if (c === 'ab7f7fff') put(x, y, p.del);           // the creative "destroy item" slot
    else if (c === '1f1f1fff') continue;                     // its cross
    else unknown.set(c, (unknown.get(c) || 0) + 1);
  }
  if (unknown.size) throw new Error(texture + ': unexpected colours ' + JSON.stringify([...unknown]));
  if (box) for (const [fx, fy] of STARS) { const x = box.x0 + Math.round(fx * (box.x1 - box.x0)), y = box.y0 + Math.round(fy * (box.y1 - box.y0)); put(x, y, fy < 0.2 ? p.star : p.starLow); }
  return {w: img.w, h: img.h, rgba: out};
}

function recolour(bytes, texture, name = 'jasper') {
  const img = png.decode(bytes), done = retheme(img, texture, name);
  return done === img ? Buffer.from(bytes) : png.encode(done);
}

module.exports = {PALETTES, JASPER, TEXTURES, argb, rgb, mix, retheme, recolour, isThemed, scene};
