'use strict';
// The JasperCraft look of the inventory and of every container window (owner, 2026-10-07: "make the whole inventory fit the theme of
// Jasper Craft. I just want a thematic, superficial change. Don't change any of the logic."; then: "The UI is fucked up in furnaces,
// crafting tables, etc., and chests" - the widened part drew in the new colours next to windows that were still grey, so every
// container window is recoloured too). Colours only: the textures are recoloured pixel for pixel (same geometry, same slots, same
// sprites), the widened window, the gear column and the Field Journal panel draw in the same palette (client-mods/*), and the window
// titles follow (scripts/build-theme-client.cjs).
// The palette comes from the game's own art: the teal of the cat favicon, torchlight amber from the banner, a night sky.
const png = require('./png-codec.cjs');

const rgb = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
/** '#RRGGBB' -> signed 32-bit ARGB int, as the client's drawRect takes it. */
const argb = hex => (0xFF000000 | parseInt(hex.slice(1), 16)) | 0;
const hexOf = (r, g, b) => '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => { const A = rgb(a), B = rgb(b); return hexOf(...A.map((v, i) => Math.round(v + (B[i] - v) * t))); };

/** frame*: the window frame (outline, 2px highlight top/left, 2px shade bottom/right); body; slot*: a slot (dark top/left edge,
 * light bottom/right edge, inside); arrow: decorative arrows and the like (crafting arrow); label: the colour of the window's title
 * text on the body; panel: a dark inset panel (beacon); selected*: a chosen button (beacon); sky*, ridge*, star*: the night scene
 * behind the player model and the horse. */
const JASPER = {
  outline: '#03171B', frameHi: '#F0B552', frameShade: '#7A4E1C', body: '#12454F',
  slotDark: '#06222A', slotHi: '#3C8791', slot: '#0B2F37', arrow: '#F0B552', del: '#6E2F2F',
  label: '#F4E6BC', panel: '#04141A', selected: '#8F6A2C', selectedHi: '#F6D98B',
  skyTop: '#04141A', skyMid: '#0C3A44', skyLow: '#6A4A24', ridge: '#041A20', ridgeRim: '#0E4450', star: '#E9E2C8', starLow: '#9FB6B4'
};
const PALETTES = {jasper: JASPER};

const hexAt = (img, x, y) => { const i = (y * img.w + x) * 4; return [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2], img.rgba[i + 3]].map(v => v.toString(16).padStart(2, '0')).join(''); };

/** What each texture is. w x h: the window rectangle from the top left (frame highlight, slot edges and the recess are told apart
 * inside it); strict: an unknown colour inside the window is an error (the survival and creative windows are fully known), otherwise
 * it is kept (the enchanting table's parchment, the villager's trade icons ...); glyph: the colour of the grey drawings that are not
 * slots (arrows, flames, plus signs); sprites: rectangles outside the window with sprites drawn over it (progress arrows, buttons):
 * grey / white are the colour names their greys and whites become there (a lit progress sprite is cream, a button edge is a slot's). */
const TEXTURES = {
  'container/inventory.png': {w: 176, h: 166, strict: true, sprites: [{rect: [176, 0, 200, 40], grey: 'slot', white: 'slotHi'}]},
  'container/creative_inventory/tab_inventory.png': {w: 195, h: 136, strict: true},
  'container/creative_inventory/tab_items.png': {w: 195, h: 136, strict: true},
  'container/creative_inventory/tab_item_search.png': {w: 195, h: 136, strict: true},
  'container/creative_inventory/tabs.png': {w: 256, h: 256, sheet: true},
  'container/generic_54.png': {w: 176, h: 222, strict: true},
  'container/shulker_box.png': {w: 176, h: 166, strict: true},
  'container/dispenser.png': {w: 176, h: 166, strict: true},
  'container/hopper.png': {w: 176, h: 133, strict: true},
  'container/crafting_table.png': {w: 176, h: 166, sprites: [{rect: [0, 168, 20, 205], grey: 'slot', white: 'slotHi'}]},
  'container/furnace.png': {w: 176, h: 166, glyph: 'frameShade', sprites: [{rect: [176, 0, 200, 31], grey: 'frameShade', white: 'label'}]},
  'container/brewing_stand.png': {w: 176, h: 166, glyph: 'frameShade', sprites: [{rect: [176, 0, 209, 33], grey: 'frameShade', white: 'label'}]},
  'container/enchanting_table.png': {w: 176, h: 166},
  'container/anvil.png': {w: 176, h: 166, sprites: [{rect: [176, 0, 204, 21], grey: 'arrow', white: 'label'}]},
  'container/beacon.png': {w: 230, h: 219, panel: true, sprites: [{rect: [0, 219, 132, 242], grey: 'slot', white: 'slotHi'}]},
  'container/horse.png': {w: 176, h: 166, sprites: [{rect: [0, 166, 90, 238], grey: 'slot', white: 'slotHi'}]},
  'container/villager.png': {w: 176, h: 166, sprites: [{rect: [176, 0, 240, 34], grey: 'slot', white: 'slotHi'}]}
};

/** Connected groups (4-neighbour: the corner pixels of neighbouring slots touch only diagonally and must stay apart) of the pixels inside [0,w) x [0,h) for which pick(x, y) is true. */
function groups(img, w, h, pick) {
  const seen = new Uint8Array(w * h), out = [];
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (seen[y * w + x] || !pick(x, y)) continue;
    const stack = [[x, y]], g = {x0: x, y0: y, x1: x, y1: y, px: []};
    seen[y * w + x] = 1;
    while (stack.length) {
      const [cx, cy] = stack.pop();
      g.px.push(cy * w + cx); g.x0 = Math.min(g.x0, cx); g.x1 = Math.max(g.x1, cx); g.y0 = Math.min(g.y0, cy); g.y1 = Math.max(g.y1, cy);
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = cx + dx, ny = cy + dy;
        if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[ny * w + nx] && pick(nx, ny)) { seen[ny * w + nx] = 1; stack.push([nx, ny]); }
      }
    }
    out.push(g);
  }
  return out;
}

/** The night scene in a recess box: sky (ordered-dithered gradient), a mountain ridge; stars are added by the caller. */
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

/** Recolours one texture (decoded RGBA); returns a new image, or the same one when nothing is left to recolour (a texture that
 * carries the palette already may still have sprites that do not: those are recoloured on their own). */
function retheme(img, texture, name = 'jasper') {
  const p = PALETTES[name], spec = TEXTURES[texture];
  if (!p) throw new Error('unknown palette ' + name);
  if (!spec) throw new Error('no theme rules for ' + texture);
  const windowThemed = isThemed(img, name);
  if (!windowThemed && hexAt(img, 2, 1) !== 'ffffffff' && !spec.sheet) throw new Error(texture + ': neither vanilla nor themed (pixel 2,1 is ' + hexAt(img, 2, 1) + ')');
  const out = Buffer.from(img.rgba);
  const put = (x, y, hex) => { const i = (y * img.w + x) * 4, c = rgb(hex); out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2]; out[i + 3] = 255; };
  const ghost = mix(p.slot, p.slotHi, 0.45), glyph = p[spec.glyph || 'arrow'];
  const W = spec.w, H = spec.h, at = (x, y) => hexAt(img, x, y);

  if (spec.sheet) {
    // Tab and scroller sprites: the same colour classes everywhere (their highlights are all frame highlights).
    const m = {c6c6c6ff: 'body', '555555ff': 'frameShade', '373737ff': 'slotDark', ffffffff: 'frameHi', '8b8b8bff': 'slot', '000000ff': 'outline'};
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (m[at(x, y)]) put(x, y, p[m[at(x, y)]]);
    return out.equals(img.rgba) ? img : {w: img.w, h: img.h, rgba: out};
  }

  // Greys inside the window. A slot (or any box: the scroll track, the brewing stand's fuel gauge) has a dark top and left edge: its
  // greys, ghost icons and corner pixels included, are slot colour. Of the greys left over, the filled and at least 3 px thick groups
  // are boxes without that edge (a search field), a single pixel is a corner, and everything else (arrows, flames, plus signs, tubes)
  // is a glyph.
  const greyKind = new Map(), dark = (x, y) => x >= 0 && y >= 0 && x < W && y < H && at(x, y) === '373737ff';
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (!dark(x, y) || dark(x - 1, y) || dark(x, y - 1)) continue;
    let nr = 0, nd = 0;
    while (dark(x + nr, y)) nr++;
    while (dark(x, y + nd)) nd++;
    if (nr < 3 || nd < 3) continue;
    for (let yy = y; yy <= Math.min(H - 1, y + nd); yy++) for (let xx = x; xx <= Math.min(W - 1, x + nr); xx++) if (at(xx, yy) === '8b8b8bff') greyKind.set(yy * W + xx, 'slot');
  }
  // A grey pixel whose row to the left and column above (through greys and ghost-icon pixels) end at a dark edge is inside a box whose
  // edge another drawing interrupts (the brewing stand's tubes run into its potion slots).
  const grey = (x, y) => { const c = at(x, y); return c === '8b8b8bff' || c === '686868ff'; };
  const edgeAfter = (x, y, dx, dy) => { do { x += dx; y += dy; } while (x >= 0 && y >= 0 && grey(x, y)); return dark(x, y); };
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (at(x, y) === '8b8b8bff' && !greyKind.has(y * W + x) && edgeAfter(x, y, -1, 0) && edgeAfter(x, y, 0, -1)) greyKind.set(y * W + x, 'slot');
  for (const g of groups(img, W, H, (x, y) => at(x, y) === '8b8b8bff' && !greyKind.has(y * W + x))) {
    const bw = g.x1 - g.x0 + 1, bh = g.y1 - g.y0 + 1, slot = g.px.length <= 2 || (Math.min(bw, bh) >= 3 && g.px.length >= 0.7 * bw * bh);
    for (const i of g.px) greyKind.set(i, slot ? 'slot' : 'glyph');
  }
  // Black groups well inside the window: a filled rectangle of some size is a recess (the player model, the horse), the rest is outline.
  const recess = new Map(), boxes = [];
  for (const g of groups(img, W, H, (x, y) => x >= 4 && y >= 4 && x < W - 5 && y < H - 5 && at(x, y) === '000000ff')) {
    const bw = g.x1 - g.x0 + 1, bh = g.y1 - g.y0 + 1;
    if (g.px.length >= 100 && g.px.length === bw * bh) { boxes.push(g); for (const i of g.px) recess.set(i, g); }
  }
  const unknown = new Map();
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
    const c = at(x, y);
    if (c.slice(6) === '00') continue;                       // transparent (rounded corners, unused sheet area) stays
    if (x < W && y < H) {
      if (windowThemed) continue;
      const edgeBand = x <= 2 || y <= 2;
      if (c === 'c6c6c6ff' || c === 'c5c5c5ff') put(x, y, p.body);
      else if (c === '555555ff') put(x, y, p.frameShade);
      else if (c === '373737ff') put(x, y, p.slotDark);
      else if (c === 'ffffffff') put(x, y, edgeBand ? p.frameHi : p.slotHi);
      else if (c === '000000ff') put(x, y, recess.has(y * W + x) ? scene(p, recess.get(y * W + x), x, y) : p.outline);
      else if (c === '8b8b8bff') put(x, y, greyKind.get(y * W + x) === 'glyph' ? glyph : p.slot);
      else if (c === '686868ff') put(x, y, ghost);           // the ghost icons in empty slots, drawing outlines
      else if (c === '212121ff' && spec.panel) put(x, y, p.panel);   // the beacon's dark panel
      else if (c === 'ab7f7fff') put(x, y, p.del);           // the creative "destroy item" slot
      else if (c === '1f1f1fff') continue;                   // its cross
      else if (spec.strict) unknown.set(c, (unknown.get(c) || 0) + 1);
      continue;
    }
    // Outside the window: only the sprite rectangles named in the spec, and only the greys of the vanilla texture in them.
    const s = (spec.sprites || []).find(r => x >= r.rect[0] && y >= r.rect[1] && x < r.rect[2] && y < r.rect[3]);
    if (!s) continue;
    if (c === 'c6c6c6ff') put(x, y, p.body);
    else if (c === '8b8b8bff') put(x, y, p[s.grey]);
    else if (c === 'ffffffff') put(x, y, p[s.white]);
    else if (c === '373737ff') put(x, y, p.slotDark);
    else if (c === '555555ff') put(x, y, p.frameShade);
    else if (c === '000000ff') put(x, y, p.outline);
    else if (c === '686868ff') put(x, y, s.grey === 'frameShade' ? p.frameShade : ghost);
    else if (c === '7778a0ff') put(x, y, p.selected);       // the beacon's chosen button
    else if (c === 'cfd0f7ff') put(x, y, p.selectedHi);
    else if (c === '494949ff') put(x, y, p.slotDark);
  }
  if (unknown.size) throw new Error(texture + ': unexpected colours ' + JSON.stringify([...unknown]));
  for (const box of boxes) for (const [fx, fy] of STARS) {
    const x = box.x0 + Math.round(fx * (box.x1 - box.x0)), y = box.y0 + Math.round(fy * (box.y1 - box.y0));
    put(x, y, fy < 0.2 ? p.star : p.starLow);
  }
  return out.equals(img.rgba) ? img : {w: img.w, h: img.h, rgba: out};
}

function recolour(bytes, texture, name = 'jasper') {
  const img = png.decode(bytes), done = retheme(img, texture, name);
  return done === img ? Buffer.from(bytes) : png.encode(done);
}

module.exports = {PALETTES, JASPER, TEXTURES, argb, rgb, mix, retheme, recolour, isThemed, scene, groups};
