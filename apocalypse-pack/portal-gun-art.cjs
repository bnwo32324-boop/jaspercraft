'use strict';
/* The Portal Gun's model and its own painted texture (owner, 2026-10-05: "The texture looks really bad, and it should be redone
 * to look better. The model should be better.").
 *
 * The old model borrowed six vanilla block textures (quartz, coal, iron, diamond, gold, redstone) stretched over every face.
 * This one is original art: a white ceramic shell over a graphite chassis, brushed steel fittings, a rubber grip, and two glowing
 * energy cells -- blue on the left flank, orange on the right -- feeding a claw-shaped emitter whose lens is split blue and
 * orange. It is an original device, not a reproduction of any existing game's art or branding.
 *
 * Everything is generated: a list of cuboid parts, an automatic texture atlas (each visible face gets its own patch of a
 * 64x64 texture, about four texels to a model unit), and a painter per material. Deterministic: the same code always yields the
 * same model and the same pixels. Conventions are the game's own (north is -Z; the barrel points toward -Z and the grip toward
 * -Y, matching JasperCraft's held-item contract for every firearm model, so the display transforms of the old model still fit).
 *
 *   node apocalypse-pack/portal-gun-art.cjs        writes the model and the texture into this pack
 */
const fs = require('node:fs'), path = require('node:path');
const {encode} = require('../scripts/png-codec.cjs');

const TEXTURE = 'items/apocalypse_portal_gun';
const faces6 = ['north', 'south', 'east', 'west', 'up', 'down'];

// ------------------------------------------------------------------------------------------------------------ colours
const hex = n => [(n >> 16) & 255, (n >> 8) & 255, n & 255];
const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
const MAT = {
  shell: {hi: hex(0xffffff), light: hex(0xf2f4f7), base: hex(0xe1e5ea), shade: hex(0xb4bac5), deep: hex(0x858c99), seam: hex(0x6b7280)},
  graphite: {hi: hex(0x6b727e), light: hex(0x535a65), base: hex(0x3b414b), shade: hex(0x292d34), deep: hex(0x181b20), seam: hex(0x0e1013)},
  steel: {hi: hex(0xe8edf2), light: hex(0xc6ced7), base: hex(0x9ba4af), shade: hex(0x717a86), deep: hex(0x4d545e), seam: hex(0x3a4049)},
  rubber: {hi: hex(0x5a5e68), light: hex(0x474b54), base: hex(0x383b43), shade: hex(0x272a30), deep: hex(0x181a1e), seam: hex(0x0e0f12)},
  blue: {core: hex(0xf2fbff), hi: hex(0xa8e4ff), light: hex(0x5cc0ff), base: hex(0x2b8cff), shade: hex(0x1560e0), deep: hex(0x0b3aa0)},
  orange: {core: hex(0xfff7e0), hi: hex(0xffd98a), light: hex(0xffb347), base: hex(0xff8a14), shade: hex(0xe4600a), deep: hex(0x9c3a05)},
  glass: {hi: hex(0x39485c), base: hex(0x172233), shade: hex(0x0d1522), deep: hex(0x070c14)}
};

/** Cheap deterministic hash noise in [0, 1). */
function noise(x, y, seed) { let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return ((h >>> 0) % 1000) / 1000; }

// -------------------------------------------------------------------------------------------------------------- parts
/** A cuboid part. faces: which sides get a patch of the texture (hidden sides are left out). */
function box(name, mat, from, to, faces = faces6, opts = {}) { return {name, mat, from, to, faces, ...opts}; }

/**
 * A round-ish tube along Z: a regular octagon made of four slabs (two axis-aligned, two turned 45 degrees), which together cover
 * exactly the octagon of apothem p. Only the eight outer facets get texture.
 */
function tube(name, mat, cx, cy, z0, z1, p, opts = {}, sides = 8) {
  const q = p * Math.tan(Math.PI / sides), zc = (z0 + z1) / 2, o = [cx, cy, zc];
  const r = n => Math.round(n * 1000) / 1000;
  // sides/2 slabs fan out at 360/sides degree steps. A slab at an angle over 45 degrees is a vertical slab turned by the
  // difference (the game turns elements by at most 45 degrees). Each facet knows which way it faces (degrees from +X toward
  // +Y seen from behind the gun), so its shade can follow the light.
  const step = 360 / sides, out = [];
  for (let k = 0; k < sides / 2; k++) {
    const theta = k * step;
    // theta in [0, 180): <= 45 horizontal turned by theta; (45, 135] vertical turned by theta - 90; > 135 horizontal turned by theta - 180
    let turned, isVertical = false;
    if (theta <= 45 + 1e-9) turned = theta; else if (theta <= 135 + 1e-9) { isVertical = true; turned = theta - 90; } else turned = theta - 180;
    const from = isVertical ? [r(cx - q), r(cy - p), z0] : [r(cx - p), r(cy - q), z0];
    const to = isVertical ? [r(cx + q), r(cy + p), z1] : [r(cx + p), r(cy + q), z1];
    const base = isVertical ? 90 : 0, names = isVertical ? ['up', 'down'] : ['east', 'west'];
    const a1 = ((base + turned) % 360 + 360) % 360, a2 = (a1 + 180) % 360;
    const el = box(name + '.' + k, mat, from, to, names, {facet: {[names[0]]: a1, [names[1]]: a2}, ...opts});
    if (turned) el.rotation = {origin: o, axis: 'z', angle: turned};
    out.push(el);
  }
  return out;
}

const AX = 8, AY = 10.6;     // the bore axis
function parts() {
  const P = [];
  // -- chassis and shell, rear to front ---------------------------------------------------------------------------------
  P.push(box('rear cap', 'graphite', [6.1, 9.0, 15.6], [9.9, 12.4, 17.0], ['north', 'south', 'east', 'west', 'up', 'down'], {paint: 'rearcap'}));
  P.push(box('rear bulb', 'shell', [5.2, 8.4, 9.8], [10.8, 13.0, 15.6], faces6, {paint: 'shell'}));
  P.push(box('mid body', 'shell', [5.8, 8.8, 3.6], [10.2, 12.6, 9.8], faces6, {paint: 'shell'}));
  P.push(...tube('neck', 'graphite', AX, AY, 0.6, 3.6, 1.45, {paint: 'tube'}));
  P.push(...tube('barrel', 'steel', AX, AY, -2.4, 0.6, 1.05, {paint: 'tube'}));
  // collars hide the joins
  P.push(box('collar rear', 'steel', [5.5, 8.6, 9.5], [10.5, 12.8, 10.1], ['up', 'down', 'east', 'west', 'north'], {paint: 'band'}));
  P.push(box('collar mid', 'steel', [6.1, 9.1, 3.3], [9.9, 12.3, 3.9], ['up', 'down', 'east', 'west', 'north'], {paint: 'band'}));
  P.push(box('collar neck', 'steel', [6.2, 9.2, 0.2], [9.8, 12.0, 0.8], ['up', 'down', 'east', 'west', 'north'], {paint: 'band'}));
  // -- emitter ----------------------------------------------------------------------------------------------------------
  P.push(box('flange top', 'shell', [6.0, 11.4, -3.6], [10.0, 12.6, -2.4], faces6, {paint: 'shell'}));
  P.push(box('flange bottom', 'shell', [6.0, 8.6, -3.6], [10.0, 9.8, -2.4], faces6, {paint: 'shell'}));
  P.push(box('flange left', 'shell', [6.0, 9.8, -3.6], [7.2, 11.4, -2.4], ['north', 'west', 'up', 'down'], {paint: 'shell'}));
  P.push(box('flange right', 'shell', [8.8, 9.8, -3.6], [10.0, 11.4, -2.4], ['north', 'east', 'up', 'down'], {paint: 'shell'}));
  P.push(box('core', 'blue', [7.2, 9.8, -4.5], [8.8, 11.4, -2.4], ['north', 'east', 'west', 'up', 'down'], {paint: 'lens', density: 6}));
  // the emitter cage: long top and bottom rails, short side rails, steel tips
  for (const [n, f, t, faces] of [
    ['rail top', [6.4, 11.8, -6.6], [9.6, 12.6, -3.6], ['north', 'east', 'west', 'up']],
    ['rail bottom', [6.4, 8.6, -6.6], [9.6, 9.4, -3.6], ['north', 'east', 'west', 'down']],
    ['rail left', [6.0, 9.4, -5.6], [6.8, 11.8, -3.6], ['north', 'west', 'up', 'down']],
    ['rail right', [9.2, 9.4, -5.6], [10.0, 11.8, -3.6], ['north', 'east', 'up', 'down']]]) {
    P.push(box(n, 'shell', f, t, faces, {paint: 'claw'}));
  }
  P.push(box('tip top', 'steel', [6.4, 11.8, -7.0], [9.6, 12.6, -6.6], ['north', 'east', 'west', 'up'], {paint: 'band'}));
  P.push(box('tip bottom', 'steel', [6.4, 8.6, -7.0], [9.6, 9.4, -6.6], ['north', 'east', 'west', 'down'], {paint: 'band'}));
  // -- energy cells: blue on the left flank, orange on the right ---------------------------------------------------------
  const cellL = tube('cell blue', 'blue', 4.9, 10.0, 3.9, 9.5, 1.0, {paint: 'cell'}, 16);
  const cellR = tube('cell orange', 'orange', 11.1, 10.0, 3.9, 9.5, 1.0, {paint: 'cell'}, 16);
  P.push(...cellL, ...cellR);
  for (const [side, cx] of [['blue', 4.9], ['orange', 11.1]]) {
    for (const [tag, z0, z1] of [['front', 3.5, 4.1], ['rear', 9.3, 9.9]]) {
      P.push(box('cell cap ' + side + ' ' + tag, 'steel', [cx - 1.15, 8.85, z0], [cx + 1.15, 11.15, z1], ['north', 'south', 'east', 'west', 'up', 'down'], {paint: 'band'}));
    }
    // a saddle ties each cell to the body
    const lo = cx < 8 ? cx + 1.0 : 10.2, hi = cx < 8 ? 5.8 : cx - 1.0;
    P.push(box('saddle ' + side, 'steel', [Math.min(lo, hi), 9.5, 6.0], [Math.max(lo, hi), 10.5, 7.0], ['up', 'down', 'north', 'south'], {paint: 'band'}));
  }
  // -- spine, sights ----------------------------------------------------------------------------------------------------
  P.push(box('rail blue', 'blue', [6.9, 12.6, 5.4], [8.0, 13.05, 9.6], ['up', 'west', 'north', 'south'], {paint: 'rail'}));
  P.push(box('rail orange', 'orange', [8.0, 12.6, 5.4], [9.1, 13.05, 9.6], ['up', 'east', 'north', 'south'], {paint: 'rail'}));
  P.push(box('spine front', 'graphite', [7.2, 12.6, 4.4], [8.8, 13.0, 5.4], ['up', 'east', 'west', 'north'], {paint: 'plain'}));
  P.push(box('front sight', 'graphite', [7.7, 13.0, 4.4], [8.3, 14.3, 5.1], ['up', 'east', 'west', 'north', 'south'], {paint: 'plain'}));
  P.push(box('sight tip', 'orange', [7.78, 14.1, 4.3], [8.22, 14.3, 4.45], ['north', 'up'], {paint: 'glow'}));
  for (const k of [0, 1, 2, 3]) {
    P.push(box('fin ' + k, 'steel', [6.5, 13.0, 10.6 + k * 1.15], [9.5, 13.55, 11.15 + k * 1.15], ['up', 'east', 'west', 'north', 'south'], {paint: 'band'}));
  }
  P.push(box('rear sight left', 'graphite', [6.7, 13.55, 14.4], [7.4, 14.2, 15.0], ['up', 'east', 'west', 'north', 'south'], {paint: 'plain'}));
  P.push(box('rear sight right', 'graphite', [8.6, 13.55, 14.4], [9.3, 14.2, 15.0], ['up', 'east', 'west', 'north', 'south'], {paint: 'plain'}));
  // -- side screen on the right flank ------------------------------------------------------------------------------------
  P.push(box('screen', 'glass', [10.2, 10.0, 5.0], [10.45, 12.1, 8.8], ['east', 'up', 'north', 'south'], {paint: 'screen', density: 6}));
  // -- under-barrel capacitor ---------------------------------------------------------------------------------------------
  P.push(box('capacitor', 'graphite', [6.9, 7.5, -0.2], [9.1, 8.8, 6.2], ['north', 'south', 'east', 'west', 'down'], {paint: 'capacitor'}));
  P.push(box('capacitor band 1', 'steel', [6.75, 7.35, 0.9], [9.25, 8.8, 1.5], ['north', 'south', 'east', 'west', 'down'], {paint: 'band'}));
  P.push(box('capacitor band 2', 'steel', [6.75, 7.35, 4.4], [9.25, 8.8, 5.0], ['north', 'south', 'east', 'west', 'down'], {paint: 'band'}));
  // -- grip, guard and trigger ----------------------------------------------------------------------------------------------
  P.push(box('grip upper', 'rubber', [6.7, 5.5, 10.8], [9.3, 8.4, 14.2], faces6, {paint: 'grip'}));
  P.push(box('grip lower', 'rubber', [6.7, 3.0, 11.4], [9.3, 5.5, 14.8], faces6, {paint: 'grip'}));
  P.push(box('pommel', 'steel', [6.4, 2.2, 11.1], [9.6, 3.0, 15.1], faces6, {paint: 'band'}));
  P.push(box('guard front', 'graphite', [7.35, 5.7, 7.9], [8.65, 8.4, 8.5], ['north', 'south', 'east', 'west', 'down'], {paint: 'plain'}));
  P.push(box('guard bottom', 'graphite', [7.35, 5.3, 7.9], [8.65, 5.9, 11.0], ['north', 'east', 'west', 'down', 'up'], {paint: 'plain'}));
  P.push(box('trigger', 'orange', [7.5, 6.1, 9.3], [8.5, 8.4, 9.95], ['north', 'south', 'east', 'west', 'down'], {paint: 'glow'}));
  return P;
}

// ----------------------------------------------------------------------------------------------------------- culling
const NORMAL_OF = {north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]};
/** Sample points on a face (a 5 x 5 grid, pulled in from the rim), nudged a hair outward along the normal. */
function faceSamples(p, dir) {
  const [x0, y0, z0] = p.from, [x1, y1, z1] = p.to, n = NORMAL_OF[dir], pts = [], eps = 0.02;
  for (let i = 0; i < 5; i++) for (let j = 0; j < 5; j++) {
    const a = 0.08 + 0.84 * i / 4, b = 0.08 + 0.84 * j / 4;
    let q;
    if (dir === 'north' || dir === 'south') q = [x0 + (x1 - x0) * a, y0 + (y1 - y0) * b, dir === 'north' ? z0 : z1];
    else if (dir === 'east' || dir === 'west') q = [dir === 'east' ? x1 : x0, y0 + (y1 - y0) * b, z0 + (z1 - z0) * a];
    else q = [x0 + (x1 - x0) * a, dir === 'up' ? y1 : y0, z0 + (z1 - z0) * b];
    pts.push([q[0] + n[0] * eps, q[1] + n[1] * eps, q[2] + n[2] * eps]);
  }
  return pts;
}
/** A face is hidden when every sample just outside it lies inside another (unrotated) part; rotated parts keep all their faces. */
function visibleFaces(list, p) {
  if (p.rotation || p.facet) return p.faces;
  return p.faces.filter(dir => !faceSamples(p, dir).every(q => list.some(o => o !== p && !o.rotation && !o.facet
    && q[0] > o.from[0] && q[0] < o.to[0] && q[1] > o.from[1] && q[1] < o.to[1] && q[2] > o.from[2] && q[2] < o.to[2])));
}

// ------------------------------------------------------------------------------------------------------------- atlas
/** The pixel size of a part's face patch from its geometry. */
function faceUnits(p, dir) {
  const [x0, y0, z0] = p.from, [x1, y1, z1] = p.to, dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
  return dir === 'north' || dir === 'south' ? [dx, dy] : dir === 'east' || dir === 'west' ? [dz, dy] : [dx, dz];
}

/** MaxRects packing (best short side fit), largest first, one texel of gap around every patch. Null when they do not fit. */
function pack(list, size) {
  const order = list.map((r, i) => ({...r, i})).sort((a, b) => Math.max(b.w, b.h) - Math.max(a.w, a.h) || b.w * b.h - a.w * a.h || a.i - b.i);
  let free = [{x: 0, y: 0, w: size, h: size}];
  const out = new Array(list.length);
  for (const r of order) {
    const rw = r.w + 1, rh = r.h + 1;
    let best = null, bs = Infinity, bl = Infinity;
    for (const f of free) {
      if (f.w < rw || f.h < rh) continue;
      const lw = f.w - rw, lh = f.h - rh, short = Math.min(lw, lh), long = Math.max(lw, lh);
      if (short < bs || (short === bs && long < bl)) { best = f; bs = short; bl = long; }
    }
    if (!best) return null;
    const used = {x: best.x, y: best.y, w: rw, h: rh};
    out[r.i] = {x: used.x, y: used.y, w: r.w, h: r.h};
    const next = [];
    for (const f of free) {
      if (used.x >= f.x + f.w || used.x + used.w <= f.x || used.y >= f.y + f.h || used.y + used.h <= f.y) { next.push(f); continue; }
      if (used.x > f.x) next.push({x: f.x, y: f.y, w: used.x - f.x, h: f.h});
      if (used.x + used.w < f.x + f.w) next.push({x: used.x + used.w, y: f.y, w: f.x + f.w - used.x - used.w, h: f.h});
      if (used.y > f.y) next.push({x: f.x, y: f.y, w: f.w, h: used.y - f.y});
      if (used.y + used.h < f.y + f.h) next.push({x: f.x, y: used.y + used.h, w: f.w, h: f.y + f.h - used.y - used.h});
    }
    free = next.filter((a, ia) => !next.some((b, ib) => ia !== ib && a.x >= b.x && a.y >= b.y && a.x + a.w <= b.x + b.w && a.y + a.h <= b.y + b.h
      && (ia > ib || a.x !== b.x || a.y !== b.y || a.w !== b.w || a.h !== b.h)));
  }
  return out;
}

// ----------------------------------------------------------------------------------------------------------- painting
class Patch {
  constructor(img, rect, dir) { this.img = img; this.x0 = rect.x; this.y0 = rect.y; this.w = rect.w; this.h = rect.h; this.dir = dir; }
  set(x, y, c, a = 255) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const o = ((this.y0 + y) * this.img.w + this.x0 + x) * 4;
    this.img.rgba[o] = c[0]; this.img.rgba[o + 1] = c[1]; this.img.rgba[o + 2] = c[2]; this.img.rgba[o + 3] = a;
  }
  get(x, y) { const o = ((this.y0 + y) * this.img.w + this.x0 + x) * 4; return [this.img.rgba[o], this.img.rgba[o + 1], this.img.rgba[o + 2]]; }
  fill(c) { for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) this.set(x, y, c); }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  /** A vertical gradient from the top colour to the bottom colour. */
  vgrad(a, b) { for (let y = 0; y < this.h; y++) { const t = this.h > 1 ? y / (this.h - 1) : 0; for (let x = 0; x < this.w; x++) this.set(x, y, mix(a, b, t)); } }
  hgrad(a, b) { for (let x = 0; x < this.w; x++) { const t = this.w > 1 ? x / (this.w - 1) : 0; for (let y = 0; y < this.h; y++) this.set(x, y, mix(a, b, t)); } }
  /**
   * One pixel bevel: light on the top and left rim, shadow on the bottom and right rim. It is blended into the face rather than
   * painted over it: a harsh rim on every face turns to sparkle when the item is drawn small with mipmaps off.
   */
  bevel(light, dark) {
    if (this.w < 3 || this.h < 3) return;
    const lit = (x, y) => this.set(x, y, mix(this.get(x, y), light, .55)), shade = (x, y) => this.set(x, y, mix(this.get(x, y), dark, .55));
    for (let x = 0; x < this.w; x++) { lit(x, 0); shade(x, this.h - 1); }
    for (let y = 0; y < this.h; y++) { lit(0, y); shade(this.w - 1, y); }
  }
  speckle(amount, seed, tint) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const n = noise(x, y, seed);
      if (n < amount) this.set(x, y, mix(this.get(x, y), tint, .35));
    }
  }
}

const PAINTERS = {
  /** Plain bevelled metal or plastic in the part's own material. */
  plain(c, m) {
    c.vgrad(m.light, m.base); c.bevel(m.hi, m.shade);
  },
  /** Brushed steel band: fine lines across the face. */
  band(c, m) {
    c.vgrad(m.light, m.base);
    for (let y = 1; y < c.h - 1; y += 2) for (let x = 0; x < c.w; x++) if (noise(x, y, c.seedOf) < 0.55) c.set(x, y, mix(c.get(x, y), m.hi, .22));
    c.bevel(m.hi, m.shade);
  },
  /** White ceramic shell: a soft vertical gradient, a hairline seam, a vent of three slits on large faces. */
  shell(c, m) {
    c.vgrad(m.light, m.base);
    c.bevel(m.hi, m.shade);
    if (c.w >= 12 && c.h >= 8 && (c.dir === 'east' || c.dir === 'west')) {
      // a long seam below the middle, and a short vent near the rear
      const sy = Math.floor(c.h * 0.62);
      for (let x = 2; x < c.w - 2; x++) { c.set(x, sy, mix(m.base, m.seam, .8)); c.set(x, sy + 1, mix(m.base, m.hi, .6)); }
      for (let k = 0; k < 3; k++) { const vx = 3 + k * 2; for (let y = 2; y < Math.min(c.h - 4, sy - 2); y++) c.set(c.dir === 'east' ? vx : c.w - 1 - vx, y, mix(m.base, m.deep, .65)); }
      // a side stripe: blue on the left flank, orange on the right
      const stripe = c.dir === 'west' ? MAT.blue : MAT.orange, ty = Math.floor(c.h * 0.3);
      for (let x = Math.floor(c.w * 0.32); x < c.w - 2; x++) { c.set(x, ty, stripe.base); c.set(x, ty + 1, stripe.light); c.set(x, ty + 2, stripe.base); }
    } else if (c.w >= 8 && c.h >= 6 && (c.dir === 'up' || c.dir === 'north' || c.dir === 'south')) {
      const sy = Math.floor(c.h / 2);
      for (let x = 1; x < c.w - 1; x++) c.set(x, sy, mix(m.base, m.seam, .8));
    }
  },
  /** The two claws and any small shell part: gradient with a thin seam. */
  claw(c, m) { c.vgrad(m.light, m.base); c.bevel(m.hi, m.shade); },
  rearcap(c, m) {
    c.vgrad(m.light, m.base); c.bevel(m.hi, m.shade);
    if (c.dir === 'south' && c.w >= 8) {
      // the exhaust: a glowing blue and orange pair behind a dark bezel
      const bx = Math.floor(c.w / 2), by = Math.floor(c.h / 2);
      c.rect(bx - 4, by - 2, 8, 5, m.seam);
      c.rect(bx - 3, by - 1, 3, 3, MAT.blue.base); c.set(bx - 2, by, MAT.blue.core);
      c.rect(bx, by - 1, 3, 3, MAT.orange.base); c.set(bx + 1, by, MAT.orange.core);
    }
  },
  /** A glowing cell facet: lit by its angle, with a white-hot glint on the facets that face the light. */
  cell(c, m, p) {
    const a = p.facet[c.dir], lit = 0.5 + 0.5 * Math.cos((a - 110) * Math.PI / 180);          // 0 facing away, 1 facing the light
    for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
      // an energy cell glows from within: dim on the side away from the light, bright and white-hot where it faces it
      let col = lit < 0.5 ? mix(m.shade, m.base, lit * 2) : mix(m.base, m.light, (lit - 0.5) * 2);
      if (lit > 0.8) col = mix(col, m.core, (lit - 0.8) * 3.2);
      // beads of energy travelling along the cell
      if ((y + Math.floor(c.seedOf)) % 7 === 0 && lit > 0.2) col = mix(col, m.hi, .4);
      c.set(x, y, col);
    }
  },
  /** A metal tube facet: smooth shading that follows the light, a faint brushed grain along its length. */
  tube(c, m, p) {
    const a = p.facet[c.dir], lit = 0.5 + 0.5 * Math.cos((a - 110) * Math.PI / 180);
    for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
      let col = lit < 0.5 ? mix(m.deep, m.shade, lit * 2) : mix(m.shade, m.light, (lit - 0.5) * 2);
      c.set(x, y, col);
    }
  },
  /** The glowing split rail on top of the gun: a bright core line between darker edges. */
  rail(c, m, p) {
    // The two halves brighten toward the seam between them, so the colours meet in a white-hot line down the middle.
    const inner = p.mat === 'blue' ? 1 : 0;
    for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
      const along = c.w > 1 ? x / (c.w - 1) : 0.5, t = c.dir === 'up' ? (inner ? along : 1 - along) : 0.55;
      let col = mix(m.shade, m.light, Math.min(1, t * 1.15));
      if (c.dir === 'up' && t > 0.8) col = mix(col, m.core, (t - 0.8) * 4);
      c.set(x, y, col);
    }
  },
  /** A bright emitter or trigger: gradient with a white-hot top edge. */
  glow(c, m) { c.vgrad(m.light, m.base); c.bevel(m.core, m.shade); },
  /** The lens: a ring of dark metal, then a field split blue (left) and orange (right) around a white-hot centre. */
  lens(c, m) {
    if (c.dir !== 'north') { c.vgrad(MAT.blue.light, MAT.blue.shade); for (let x = 0; x < c.w; x++) c.set(x, 0, MAT.blue.hi); return; }
    const cx = (c.w - 1) / 2, cy = (c.h - 1) / 2, r = Math.min(c.w, c.h) / 2;
    for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
      const d = Math.hypot(x - cx, y - cy) / r;
      let col;
      if (d > 0.96) col = MAT.graphite.deep;
      else if (d > 0.78) col = MAT.steel.base;
      else {
        const side = x < cx ? MAT.blue : MAT.orange;
        col = mix(side.base, side.light, 1 - d / 0.78);
        if (Math.abs(x - cx) < 0.9) col = mix(MAT.blue.core, MAT.orange.core, .5);
        if (d < 0.3) col = mix(col, [255, 255, 255], 1 - d / 0.3);
      }
      c.set(x, y, col);
    }
  },
  /** The side screen: dark glass with a blue ring and an orange ring that overlap, as the two portals do. */
  screen(c, m) {
    c.fill(m.base); c.bevel(m.hi, m.deep);
    if (c.dir !== 'east') return;
    const cy = (c.h - 1) / 2;
    for (const [col, cx] of [[MAT.blue, c.w * 0.38], [MAT.orange, c.w * 0.62]]) {
      for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
        const d = Math.hypot((x - cx) * 1.0, (y - cy) * 1.15);
        if (d <= c.h * 0.42 && d >= c.h * 0.26) c.set(x, y, col.light);
        else if (d < c.h * 0.26 && d > c.h * 0.15) c.set(x, y, mix(m.base, col.base, .4));
      }
    }
  },
  /** The under-barrel capacitor: a dark body with two coloured charge stripes. */
  capacitor(c, m) {
    c.vgrad(m.light, m.base); c.bevel(m.hi, m.shade);
    if (c.w >= 10 && (c.dir === 'east' || c.dir === 'west')) {
      const col = c.dir === 'west' ? MAT.blue : MAT.orange, y = Math.floor(c.h / 2);
      for (let x = 3; x < c.w - 3; x++) { c.set(x, y, col.base); c.set(x, y + 1, col.light); }
    }
  },
  /** Rubber grip: horizontal ribs. */
  grip(c, m) {
    c.vgrad(m.light, m.base);
    if (c.dir === 'east' || c.dir === 'west' || c.dir === 'north' || c.dir === 'south') for (let y = 1; y < c.h - 1; y += 3) for (let x = 0; x < c.w; x++) { c.set(x, y, mix(m.base, m.shade, .7)); c.set(x, y + 1, mix(m.base, m.hi, .5)); }
    c.bevel(m.hi, m.deep);
  }
};

// ------------------------------------------------------------------------------------------------------------- build
function build() {
  const list = parts();
  for (const p of list) p.faces = visibleFaces(list, p);
  for (const size of [64, 128]) {
    for (const d of size === 64 ? [4, 3.5, 3] : [4, 3.75, 3.5, 3.25, 3, 2.75, 2.5, 2.25, 2]) {
      const wants = [];
      list.forEach((p, pi) => p.faces.forEach(dir => {
        const [uw, uh] = faceUnits(p, dir);
        const k = p.density ? Math.min(p.density, d + 2) : d;
        wants.push({pi, dir, group: p.mat, w: Math.max(2, Math.round(uw * k)), h: Math.max(2, Math.round(uh * k))});
      }));
      const placed = pack(wants, size);
      if (!placed) continue;
      return finish(list, wants, placed, size, d);
    }
  }
  throw new Error('the portal gun texture does not fit in 128x128 even at two texels per unit');
}

function finish(list, wants, placed, size, density) {
  const img = {w: size, h: size, rgba: Buffer.alloc(size * size * 4)};
  const elements = list.map(p => ({__comment: p.name, from: p.from.slice(), to: p.to.slice(), ...(p.rotation ? {rotation: {...p.rotation, origin: p.rotation.origin.slice()}} : {}), faces: {}}));
  wants.forEach((w, k) => {
    const rect = placed[k], p = list[w.pi], patch = new Patch(img, rect, w.dir);
    patch.seedOf = w.pi * 7 + faces6.indexOf(w.dir);
    const painter = PAINTERS[p.paint || 'plain'];
    painter(patch, MAT[p.mat] || MAT.graphite, p);
    const r = v => Math.round(v * 10000) / 10000;
    elements[w.pi].faces[w.dir] = {uv: [r(rect.x / size * 16), r(rect.y / size * 16), r((rect.x + rect.w) / size * 16), r((rect.y + rect.h) / size * 16)], texture: '#t'};
  });
  pad(img);
  // Faces that were listed in a part but never painted cannot exist; elements with no faces are dropped.
  const finalElements = require('./zfight.cjs').separate(elements.filter(e => Object.keys(e.faces).length));
  const model = {
    __comment: 'Portal Gun / stable band 1160 / white ceramic shell, graphite chassis, steel fittings, blue and orange energy cells -- own texture, own model',
    ambientocclusion: true,
    textures: {particle: TEXTURE, t: TEXTURE},
    display: {...DISPLAY, gui: fitGui(finalElements)},
    elements: finalElements
  };
  return {model, texture: img, size, density, parts: list.length, patches: wants.length};
}

/**
 * One texel of padding around every patch: each empty texel that touches a painted one takes its colour, so a sample that lands a
 * hair outside a face (rounding, mip levels) still finds the face's own colour and never a hole that shows what is behind.
 */
function pad(img) {
  const {w, h, rgba} = img, src = Buffer.from(rgba);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const o = (y * w + x) * 4;
    if (src[o + 3]) continue;
    for (const [dx, dy] of [[-1, 0], [0, -1], [1, 0], [0, 1], [-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
      const p = (ny * w + nx) * 4;
      if (src[p + 3]) { rgba[o] = src[p]; rgba[o + 1] = src[p + 1]; rgba[o + 2] = src[p + 2]; rgba[o + 3] = 255; break; }
    }
  }
}

/**
 * The inventory icon: the other guns' rotation (muzzle to the upper left), but as large as the slot allows. The projected extent of
 * the model at that rotation decides the scale (the biggest that keeps 92% of the slot) and the translation that centres it.
 */
function fitGui(elements) {
  const {bake, displayFn} = require('../scripts/model-preview.cjs');
  const rotation = [25, 140, -20], fn = displayFn({rotation, translation: [0, 0, 0], scale: [1, 1, 1]});
  const lo = [9, 9], hi = [-9, -9];
  for (const q of bake({elements})) for (const v of q.v) { const p = fn(v); for (let i = 0; i < 2; i++) { lo[i] = Math.min(lo[i], p[i]); hi[i] = Math.max(hi[i], p[i]); } }
  const s = Math.floor(0.92 / Math.max(hi[0] - lo[0], hi[1] - lo[1]) * 1000) / 1000, r2 = v => Math.round(v * 100) / 100;
  return transform(rotation, [r2(-(lo[0] + hi[0]) / 2 * s * 16), r2(-(lo[1] + hi[1]) / 2 * s * 16), 0], s);
}

const transform = (rotation, translation, scale) => ({rotation, translation, scale: [scale, scale, scale]});
/** Held transforms are the old model's, unchanged: the barrel still points toward -Z and the grip toward -Y. */
const DISPLAY = {
  thirdperson_righthand: transform([90, 0, 0], [0, 1, 0], 0.6),
  thirdperson_lefthand: transform([90, 0, 0], [0, 1, 0], 0.6),
  firstperson_righthand: transform([6, 0, -3], [1, 0, -1], 0.6),
  firstperson_lefthand: transform([6, 0, 3], [1, 0, -1], 0.6),
  gui: transform([25, 140, -20], [0, 0, 0], 0.48),
  ground: transform([0, 0, 90], [0, 2, 0], 0.35),
  fixed: transform([0, 90, -35], [0, 0, 0], 0.45)
};

function model() { return build().model; }
function textureImage() { return build().texture; }

if (require.main === module) {
  const root = path.join(__dirname, 'assets', 'minecraft');
  const out = build();
  fs.writeFileSync(path.join(root, 'models', 'item', 'apocalypse_portal_gun.json'), JSON.stringify(out.model, null, 2) + '\n');
  fs.mkdirSync(path.join(root, 'textures', 'items'), {recursive: true});
  fs.writeFileSync(path.join(root, 'textures', 'items', 'apocalypse_portal_gun.png'), encode(out.texture));
  console.log(JSON.stringify({parts: out.parts, patches: out.patches, atlas: out.size, texelsPerUnit: out.density, elements: out.model.elements.length}));
}
module.exports = {build, model, textureImage, TEXTURE, DISPLAY, parts, tube};
