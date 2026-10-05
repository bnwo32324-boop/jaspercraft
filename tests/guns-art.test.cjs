'use strict';
// Own art for the Portal Gun and Sanitized Flesh (owner 2026-10-05: "The texture looks really bad, and it should be redone to look
// better. The model should be better."; "sanitized flesh should have its own texture, and it should look like jerky").
// The Portal Gun's model and texture are generated together (apocalypse-pack/portal-gun-art.cjs): every face of every cuboid gets
// its own painted patch of one atlas. These tests check the generator is deterministic, the atlas is sound (patches inside the
// texture, never overlapping, fully painted), the model obeys the game's limits and the held-item contract, the round parts
// really are regular polygons, and the models render. The jerky icon is checked for shape and colour.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const art = require('../apocalypse-pack/portal-gun-art.cjs');
const jerky = require('../scripts/supply-art/jerky.cjs');
const preview = require('../scripts/model-preview.cjs');
const {decode, encode} = require('../scripts/png-codec.cjs');

const root = path.resolve(__dirname, '..');
const pack = path.join(root, 'apocalypse-pack', 'assets', 'minecraft');
const built = art.build();

test('portal gun: the generator is deterministic and the checked-in model and texture are its output', () => {
  const again = art.build();
  assert.deepEqual(again.model, built.model);
  assert.ok(Buffer.from(again.texture.rgba).equals(Buffer.from(built.texture.rgba)));
  const model = JSON.parse(fs.readFileSync(path.join(pack, 'models', 'item', 'apocalypse_portal_gun.json'), 'utf8'));
  assert.deepEqual(model, built.model);
  assert.ok(fs.readFileSync(path.join(pack, 'textures', 'items', 'apocalypse_portal_gun.png')).equals(encode(built.texture)));
});

test('portal gun atlas: every face has its own painted patch, inside the texture, never overlapping another', () => {
  const size = built.texture.w;
  assert.ok([64, 128].includes(size) && built.texture.h === size, 'a small power-of-two texture');
  const patches = [];
  for (const [ei, e] of built.model.elements.entries()) {
    for (const [dir, face] of Object.entries(e.faces)) {
      const [u0, v0, u1, v1] = face.uv.map(v => v / 16 * size);
      for (const v of [u0, v0, u1, v1]) assert.ok(Math.abs(v - Math.round(v)) < 1e-2, `element ${ei} ${dir}: UV on a texel boundary`);
      const r = {x: Math.round(u0), y: Math.round(v0), w: Math.round(u1 - u0), h: Math.round(v1 - v0), name: `${e.__comment}/${dir}`};
      assert.ok(r.w >= 2 && r.h >= 2, r.name + ': at least 2x2 texels');
      assert.ok(r.x >= 0 && r.y >= 0 && r.x + r.w <= size && r.y + r.h <= size, r.name + ': inside the texture');
      assert.equal(face.texture, '#t');
      patches.push(r);
    }
  }
  assert.ok(patches.length > 150);
  for (let a = 0; a < patches.length; a++) for (let b = a + 1; b < patches.length; b++) {
    const p = patches[a], q = patches[b];
    assert.ok(p.x + p.w <= q.x || q.x + q.w <= p.x || p.y + p.h <= q.y || q.y + q.h <= p.y, `${p.name} overlaps ${q.name}`);
  }
  // Fully painted: no transparent texel inside any patch.
  for (const p of patches) for (let y = p.y; y < p.y + p.h; y++) for (let x = p.x; x < p.x + p.w; x++) {
    assert.equal(built.texture.rgba[(y * size + x) * 4 + 3], 255, `${p.name}: texel ${x},${y} is not painted`);
  }
  // Painted with colour, not a flat fill: a good spread of distinct colours.
  const colours = new Set();
  for (let i = 0; i < built.texture.rgba.length; i += 4) if (built.texture.rgba[i + 3]) colours.add(built.texture.rgba.readUIntBE(i, 3));
  assert.ok(colours.size >= 150, 'painted, not flat: ' + colours.size + ' colours');
});

test('portal gun model: obeys the game\'s element limits and uses only its own texture', () => {
  const m = built.model;
  assert.deepEqual(m.textures, {particle: 'items/apocalypse_portal_gun', t: 'items/apocalypse_portal_gun'});
  assert.ok(m.elements.length >= 12 && m.elements.length <= 80, 'cuboid budget');
  for (const e of m.elements) {
    for (const k of ['from', 'to']) for (const v of e[k]) assert.ok(v >= -16 && v <= 32, e.__comment + ': inside the -16..32 limit');
    assert.ok(e.from.every((v, i) => v < e.to[i]), e.__comment + ': positive extent');
    if (e.rotation) {
      assert.ok([-45, -22.5, 22.5, 45].includes(e.rotation.angle), e.__comment + ': the game turns elements in 22.5 degree steps up to 45');
      assert.ok(['x', 'y', 'z'].includes(e.rotation.axis));
      assert.equal(e.rotation.origin.length, 3);
    }
    assert.ok(Object.keys(e.faces).length > 0);
  }
  const text = JSON.stringify(m);
  assert.ok(!/blocks\//.test(text), 'no vanilla block texture is borrowed any more');
});

test('portal gun model: barrel toward -Z, grip toward -Y, sights up; held transforms are the firearm contract', () => {
  const by = name => built.model.elements.filter(e => e.__comment === name || e.__comment.startsWith(name));
  const minZ = Math.min(...built.model.elements.map(e => e.from[2])), maxZ = Math.max(...built.model.elements.map(e => e.to[2]));
  assert.ok(minZ < -5 && maxZ > 15 && -minZ < maxZ, 'the muzzle is in front (-Z), the stock behind (+Z)');
  const lens = by('core')[0];
  assert.ok(lens.faces.north && lens.from[2] <= minZ + 3, 'the lens faces north, at the front');
  const pommel = by('pommel')[0], body = by('mid body')[0];
  assert.ok(pommel.to[1] <= body.from[1] - 4, 'the grip hangs below the body');
  assert.equal(Math.min(...built.model.elements.map(e => e.from[1])), pommel.from[1], 'the grip is the lowest part');
  const sight = by('front sight')[0];
  assert.ok(sight.to[1] > body.to[1], 'sights stand above the body');
  const t = art.DISPLAY;
  assert.deepEqual(t.thirdperson_righthand, {rotation: [90, 0, 0], translation: [0, 1, 0], scale: [0.6, 0.6, 0.6]});
  assert.deepEqual(t.thirdperson_lefthand, {rotation: [90, 0, 0], translation: [0, 1, 0], scale: [0.6, 0.6, 0.6]});
  assert.deepEqual(t.firstperson_righthand, {rotation: [6, 0, -3], translation: [1, 0, -1], scale: [0.6, 0.6, 0.6]});
  assert.deepEqual(t.firstperson_lefthand, {rotation: [6, 0, 3], translation: [1, 0, -1], scale: [0.6, 0.6, 0.6]});
  // The inventory icon keeps the other guns' rotation (muzzle to the upper left) but is as large as the slot allows: the scale and
  // translation are computed from the model's projected extent, which must fill the slot (92% of it) and stay inside it.
  const gui = built.model.display.gui;
  assert.deepEqual(gui.rotation, [25, 140, -20]);
  assert.ok(gui.scale[0] > 0.6 && gui.scale[0] < 0.9 && gui.scale.every(v => v === gui.scale[0]), 'larger than the 0.48 of the old icon: ' + gui.scale[0]);
  const fn = preview.displayFn(gui), lo = [9, 9], hi = [-9, -9];
  for (const q of preview.bake(built.model)) for (const v of q.v) { const p = fn(v); for (let i = 0; i < 2; i++) { lo[i] = Math.min(lo[i], p[i]); hi[i] = Math.max(hi[i], p[i]); } }
  assert.ok(lo[0] >= -0.5 && hi[0] <= 0.5 && lo[1] >= -0.5 && hi[1] <= 0.5, 'the icon stays inside the slot: ' + JSON.stringify([lo, hi]));
  assert.ok(Math.max(hi[0] - lo[0], hi[1] - lo[1]) > 0.9, 'and fills it');
  assert.ok(Math.abs((lo[0] + hi[0]) / 2) < 0.02 && Math.abs((lo[1] + hi[1]) / 2) < 0.02, 'centred');
  assert.deepEqual({...built.model.display, gui: null}, {...t, gui: null}, 'every other transform is the contract');
});

test('portal gun colours: blue on the left flank, orange on the right, split blue/orange lens', () => {
  const texel = (e, dir) => {
    const f = e.faces[dir], size = built.texture.w;
    const x = Math.round((f.uv[0] + f.uv[2]) / 2 / 16 * size), y = Math.round((f.uv[1] + f.uv[3]) / 2 / 16 * size);
    const o = (y * size + x) * 4; return [built.texture.rgba[o], built.texture.rgba[o + 1], built.texture.rgba[o + 2]];
  };
  const blue = c => c[2] > c[0] + 40, orange = c => c[0] > c[2] + 60 && c[0] > 150;
  const cellsBlue = built.model.elements.filter(e => e.__comment.startsWith('cell blue')), cellsOrange = built.model.elements.filter(e => e.__comment.startsWith('cell orange'));
  assert.equal(cellsBlue.length, 8);
  assert.equal(cellsOrange.length, 8);
  assert.ok(cellsBlue.every(e => e.to[0] < 8) && cellsOrange.every(e => e.from[0] > 8), 'blue cell left of the axis, orange right of it');
  for (const e of cellsBlue) for (const dir of Object.keys(e.faces)) assert.ok(blue(texel(e, dir)), `${e.__comment}/${dir} is blue`);
  for (const e of cellsOrange) for (const dir of Object.keys(e.faces)) assert.ok(orange(texel(e, dir)), `${e.__comment}/${dir} is orange`);
  const lens = built.model.elements.find(e => e.__comment === 'core');
  const f = lens.faces.north, size = built.texture.w, w = Math.round((f.uv[2] - f.uv[0]) / 16 * size), h = Math.round((f.uv[3] - f.uv[1]) / 16 * size);
  const at = (dx, dy) => { const x = Math.round(f.uv[0] / 16 * size) + dx, y = Math.round(f.uv[1] / 16 * size) + dy, o = (y * size + x) * 4; return [built.texture.rgba[o], built.texture.rgba[o + 1], built.texture.rgba[o + 2]]; };
  assert.ok(blue(at(Math.floor(w * 0.3), Math.floor(h / 2))), 'the lens is blue on its left half');
  assert.ok(orange(at(Math.floor(w * 0.7), Math.floor(h / 2))), 'and orange on its right half');
});

// ------------------------------------------------------------------------------------------------------ round parts
/** Is the point (x, y) inside the element's footprint in the XY plane (the element is a slab along Z)? */
function inSlab(e, x, y) {
  let px = x, py = y;
  if (e.rotation) {
    assert.equal(e.rotation.axis, 'z');
    const a = -e.rotation.angle * Math.PI / 180, ox = e.rotation.origin[0], oy = e.rotation.origin[1];
    const dx = x - ox, dy = y - oy;
    px = ox + dx * Math.cos(a) - dy * Math.sin(a); py = oy + dx * Math.sin(a) + dy * Math.cos(a);
  }
  return px >= e.from[0] - 1e-9 && px <= e.to[0] + 1e-9 && py >= e.from[1] - 1e-9 && py <= e.to[1] + 1e-9;
}
for (const sides of [8, 16]) {
  test(`tube: ${sides} slabs-to-facets cover exactly the regular ${sides}-gon (no gaps, nothing poking out)`, () => {
    const p = 1.7, cx = 8, cy = 10.6, slabs = art.tube('t', 'steel', cx, cy, 0, 5, p, {}, sides);
    assert.equal(slabs.length, sides / 2);
    const apothemAt = angle => { // distance from the centre to the polygon boundary in a direction
      const step = 2 * Math.PI / sides, k = Math.round(angle / step), off = angle - k * step;
      return p / Math.cos(off);
    };
    let inside = 0;
    for (let i = 0; i < 20000; i++) {
      const a = (i * 0.61803398875 % 1) * 2 * Math.PI, r = Math.sqrt((i * 0.7548776662 % 1)) * p * 1.12;
      const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
      const poly = r <= apothemAt(a) - 1e-9, covered = slabs.some(s => inSlab(s, x, y));
      if (poly) { assert.ok(covered, `gap at ${x.toFixed(3)},${y.toFixed(3)}`); inside++; }
      else if (r > apothemAt(a) + 1e-6) assert.ok(!covered, `a slab pokes out at ${x.toFixed(3)},${y.toFixed(3)}`);
    }
    assert.ok(inside > 10000);
    // Facets: one per side, facing the angles k * (360 / sides) exactly once.
    const angles = slabs.flatMap(s => Object.values(s.facet)).sort((a, b) => a - b);
    assert.deepEqual(angles, Array.from({length: sides}, (_, k) => k * 360 / sides));
    for (const s of slabs) assert.deepEqual(s.faces.slice().sort(), Object.keys(s.facet).sort(), 'only the outer facets are textured');
  });
}

// ---------------------------------------------------------------------------------------------------------- rendering
test('preview renderer: the portal gun renders in every named view, with its colours', () => {
  const loader = preview.textureLoader(path.join(pack, 'textures'));
  const model = built.model;
  for (const view of ['gui', 'side', 'front', 'rear', 'top', 'iso', 'iso2', 'hand1', 'hand3']) {
    const img = preview.render(model, loader, view, 96, {supersample: 1, bg: [92, 92, 100]});
    let painted = 0;
    for (let i = 0; i < img.rgba.length; i += 4) if (Math.abs(img.rgba[i] - 92) + Math.abs(img.rgba[i + 1] - 92) + Math.abs(img.rgba[i + 2] - 100) > 12) painted++;
    assert.ok(painted > 150, view + ' shows the gun (' + painted + ' px)');
  }
  // From the left flank the blue cell shows, from the right flank the orange one.
  const count = (view, pick) => {
    const img = preview.render(model, loader, view, 160, {supersample: 1, mip: false});
    let n = 0;
    for (let i = 0; i < img.rgba.length; i += 4) if (pick(img.rgba[i], img.rgba[i + 1], img.rgba[i + 2])) n++;
    return n;
  };
  const blue = (r, g, b) => b > 150 && r < 140 && b > r + 60, orange = (r, g, b) => r > 190 && g > 80 && g < 190 && b < 90;
  const leftBlue = count('iso', blue), rightBlue = count('iso2', blue), leftOrange = count('iso', orange), rightOrange = count('iso2', orange);
  assert.ok(leftBlue > rightBlue, 'the blue cell is on the side that "iso" shows (' + leftBlue + ' vs ' + rightBlue + ')');
  assert.ok(rightOrange > leftOrange, 'the orange cell is on the side that "iso2" shows (' + rightOrange + ' vs ' + leftOrange + ')');
});

test('preview renderer: a one-cube model follows the game\'s UV and culling conventions', () => {
  // A cube with a 2x2 texture whose texels are red (top left), green (top right), blue (bottom left), white (bottom right).
  const tex = {w: 2, h: 2, mips: null, sample: (u, v) => [[255, 0, 0, 255], [0, 255, 0, 255], [0, 0, 255, 255], [255, 255, 255, 255]][(v >= .5 ? 2 : 0) + (u >= .5 ? 1 : 0)]};
  const faces = {};
  for (const d of ['north', 'south', 'east', 'west', 'up', 'down']) faces[d] = {uv: [0, 0, 16, 16], texture: '#t'};
  const model = {textures: {t: 'x'}, elements: [{from: [0, 0, 0], to: [16, 16, 16], faces}]};
  const at = (view, fx, fy) => { const img = preview.render(model, () => tex, view, 64, {supersample: 1, mip: false}); const o = (Math.floor(fy * 64) * 64 + Math.floor(fx * 64)) * 4; return [img.rgba[o], img.rgba[o + 1], img.rgba[o + 2]]; };
  const near = (c, want) => assert.ok(c.every((v, i) => Math.abs(v - want[i] * 0.74) < 40 || Math.abs(v - want[i]) < 40 || (want[i] === 0 && v < 40)), JSON.stringify(c) + ' vs ' + JSON.stringify(want));
  // "rear" looks at the south face from the south (yaw 0): west is on its left, so its texture reads left to right.
  near(at('rear', 0.4, 0.4), [255, 0, 0]);
  near(at('rear', 0.6, 0.4), [0, 255, 0]);
  near(at('rear', 0.4, 0.6), [0, 0, 255]);
  // "front" looks at the north face from the north: east is on its left, so the texture's left is the east side.
  const front = [at('front', 0.4, 0.4), at('front', 0.6, 0.4)];
  near(front[0], [255, 0, 0]);
  near(front[1], [0, 255, 0]);
});

// ------------------------------------------------------------------------------------------------------------ jerky
test('jerky icon: 16x16, deterministic, outlined, brown and red, recognisably a tied bundle of strips', () => {
  const px = jerky.pixels();
  assert.equal(px.length, 256);
  assert.deepEqual(jerky.pixels(), px);
  const png = jerky.png(), img = decode(png);
  assert.equal(img.w, 16);
  assert.equal(img.h, 16);
  const at = (x, y) => px[y * 16 + x];
  let opaque = 0, r = 0, g = 0, b = 0;
  for (const c of px) if (c) { opaque++; r += c[0]; g += c[1]; b += c[2]; }
  assert.ok(opaque >= 110 && opaque <= 215, 'a bundle, not a speck or a slab: ' + opaque + ' px');
  assert.ok(r > g && g > b, 'warm browns overall');
  const lum = (r * .3 + g * .59 + b * .11) / opaque;
  assert.ok(lum > 40 && lum < 135, 'dark dried meat: mean luminance ' + lum.toFixed(0));
  // Every painted pixel that touches empty space or the canvas edge is the dark outline colour.
  const outline = jerky.C.outline;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = at(x, y);
    if (!c) continue;
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const nx = x + dx, ny = y + dy; return nx < 0 || ny < 0 || nx > 15 || ny > 15 || !at(nx, ny); });
    if (edge && !(x === 0 || y === 0 || x === 15 || y === 15)) assert.deepEqual(c, outline, `outline at ${x},${y}`);
  }
  // The twine is there: a light tan column crossing the bundle.
  const twine = px.filter(c => c && (c === jerky.C.twine || c === jerky.C.twineLit)).length;
  assert.ok(twine >= 6, 'the string that ties the strips: ' + twine + ' px');
  // At least three distinct dark-red strip tones plus the highlights.
  assert.ok(new Set(px.filter(Boolean).map(c => c.join(','))).size >= 8);
});

test('jerky icon: distinct from the vanilla cooked beef (not a recolour of the steak silhouette)', () => {
  // The steak is a squat rounded blob that fills the middle of the icon; the bundle is long, diagonal and reaches both bottom-left
  // and top-right corners of the canvas.
  const px = jerky.pixels();
  const opaqueAt = (x, y) => !!px[y * 16 + x];
  const topRight = [[12, 3], [13, 3], [13, 2], [12, 2], [14, 3]].some(([x, y]) => opaqueAt(x, y));
  const bottomLeft = [[2, 12], [3, 12], [1, 12], [2, 13], [1, 11]].some(([x, y]) => opaqueAt(x, y));
  assert.ok(topRight && bottomLeft, 'long diagonal silhouette');
});
