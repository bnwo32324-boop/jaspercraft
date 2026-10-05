'use strict';
/* Tiny 16x16 pixel-art engine for the trinket icons (owner, 2026-10-05: every trinket gets its own custom texture, never one
 * borrowed from a vanilla item). Shapes are masks sampled at pixel centres; a mask is painted with a colour ramp and shaded by
 * its own rim (light from the top left: highlight on the upper and left rim, shadow on the lower and right rim), later shapes
 * cover earlier ones, and the finished icon gets a one pixel outline in the darkest neighbouring colour.
 * Deterministic: the same recipe always yields the same pixels.
 */
const zlib = require('node:zlib');
const SIZE = 16;

// ---------------------------------------------------------------------------------------------------------------- colour
const clamp = (v, lo = 0, hi = 255) => Math.max(lo, Math.min(hi, v));
function hex(n) { return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
function mix(a, b, t) { return [0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t)); }
const WHITE = [255, 255, 255], BLACK = [0, 0, 0];
/** A ramp from one mid colour: the highlight warms, the shadow cools and darkens, the outline is nearly black. */
function ramp(mid) {
  const m = typeof mid === 'number' ? hex(mid) : mid;
  return {
    spec: mix(m, WHITE, .78), hi: mix(mix(m, [255, 244, 214], .42), WHITE, .08), mid: m,
    lo: mix(mix(m, [26, 18, 54], .38), BLACK, .06), dark: mix(mix(m, [14, 8, 30], .8), BLACK, .1)
  };
}
/** A flat colour (no shading), e.g. glowing cracks or printed marks: every level is the same. */
function flat(c) { const m = typeof c === 'number' ? hex(c) : c; return {spec: m, hi: m, mid: m, lo: m, dark: mix(m, BLACK, .65), flat: true}; }

// ---------------------------------------------------------------------------------------------------------------- masks
const idx = (x, y) => y * SIZE + x;
const empty = () => new Uint8Array(SIZE * SIZE);
function sample(inside) { const m = empty(); for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (inside(x + .5, y + .5)) m[idx(x, y)] = 1; return m; }
function U(...ms) { const o = empty(); for (const m of ms) for (let i = 0; i < o.length; i++) if (m[i]) o[i] = 1; return o; }
function sub(a, ...bs) { const o = a.slice(); for (const b of bs) for (let i = 0; i < o.length; i++) if (b[i]) o[i] = 0; return o; }
function inter(a, b) { const o = empty(); for (let i = 0; i < o.length; i++) if (a[i] && b[i]) o[i] = 1; return o; }
function shift(m, dx, dy) {
  const o = empty();
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (m[idx(x, y)]) { const nx = x + dx, ny = y + dy; if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE) o[idx(nx, ny)] = 1; }
  return o;
}
function flipX(m) { const o = empty(); for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (m[idx(x, y)]) o[idx(SIZE - 1 - x, y)] = 1; return o; }
const count = m => m.reduce((n, v) => n + (v ? 1 : 0), 0);

const M = {
  rect: (x, y, w, h) => sample((px, py) => px >= x && px <= x + w && py >= y && py <= y + h),
  disc: (cx, cy, r) => sample((px, py) => (px - cx) ** 2 + (py - cy) ** 2 <= r * r),
  ellipse: (cx, cy, rx, ry) => sample((px, py) => ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1),
  ring: (cx, cy, ro, ri) => sample((px, py) => { const d = (px - cx) ** 2 + (py - cy) ** 2; return d <= ro * ro && d >= ri * ri; }),
  ering: (cx, cy, rx, ry, w) => sample((px, py) => {
    const a = ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2, b = ((px - cx) / (rx - w)) ** 2 + ((py - cy) / (ry - w)) ** 2;
    return a <= 1 && b >= 1;
  }),
  /** Polygon by the even-odd rule on pixel centres. */
  poly: pts => sample((px, py) => {
    let inside = false;
    for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
      const [xi, yi] = pts[i], [xj, yj] = pts[j];
      if (((yi > py) !== (yj > py)) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  }),
  /** A thick line segment (a capsule). */
  line: (x0, y0, x1, y1, t = 1) => sample((px, py) => {
    const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy;
    const u = l2 === 0 ? 0 : clamp(((px - x0) * dx + (py - y0) * dy) / l2, 0, 1);
    return (px - (x0 + u * dx)) ** 2 + (py - (y0 + u * dy)) ** 2 <= (t / 2) ** 2;
  }),
  /** A polyline of thick segments. */
  path: (pts, t = 1) => { let m = empty(); for (let i = 0; i + 1 < pts.length; i++) m = U(m, M.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], t)); return m; },
  /** An arc of a circle between two angles (radians, 0 = east, clockwise on screen), thick. */
  arc: (cx, cy, r, a0, a1, t = 1) => sample((px, py) => {
    if (Math.abs(Math.hypot(px - cx, py - cy) - r) > t / 2) return false;
    let a = Math.atan2(py - cy, px - cx);
    while (a < a0) a += Math.PI * 2;
    while (a > a0 + Math.PI * 2) a -= Math.PI * 2;
    return a <= a1 + 1e-9;
  }),
  px: (...pts) => { const m = empty(); for (const [x, y] of pts) if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) m[idx(x, y)] = 1; return m; },
  U, sub, inter, shift, flipX
};

// ---------------------------------------------------------------------------------------------------------------- canvas
class Icon {
  constructor() { this.cell = new Array(SIZE * SIZE).fill(null); }
  /**
   * Paints a mask. opts.shade: 'rim' (default) bevels the edge, 'flat' paints the mid colour only;
   * opts.spec: [dx, dy] puts a bright pixel at that offset from the mask's top-left corner;
   * opts.grain: 'h' | 'v' | 'x' | 'dots' | 'dust' breaks up wood, cloth, leather and stone.
   */
  paint(mask, r, opts = {}) {
    const inMask = (x, y) => x >= 0 && y >= 0 && x < SIZE && y < SIZE && mask[idx(x, y)];
    let top = SIZE, left = SIZE;
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (mask[idx(x, y)]) { top = Math.min(top, y); left = Math.min(left, x); }
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
      if (!mask[idx(x, y)]) continue;
      let level = 'mid';
      if (!r.flat && opts.shade !== 'flat') {
        const lit = !inMask(x - 1, y) || !inMask(x, y - 1), shadow = !inMask(x + 1, y) || !inMask(x, y + 1);
        level = lit && shadow ? 'mid' : lit ? 'hi' : shadow ? 'lo' : 'mid';
        if (level === 'mid' && opts.grain) {
          const g = opts.grain;
          if (g === 'h' && (y + top) % 2 === 1) level = 'lo';
          else if (g === 'v' && (x + left) % 2 === 1) level = 'lo';
          else if (g === 'x' && (x + y) % 2 === 1) level = 'lo';
          else if (g === 'dots' && (x * 3 + y * 5) % 7 === 0) level = 'lo';
          else if (g === 'dust' && (x * 7 + y * 3) % 9 === 0) level = 'hi';
        }
      }
      this.cell[idx(x, y)] = {r, level};
    }
    if (opts.spec && !r.flat) {
      const x = left + opts.spec[0], y = top + opts.spec[1];
      if (inMask(x, y)) this.cell[idx(x, y)] = {r, level: 'spec'};
    }
    return this;
  }
  /** Sets single pixels to a flat colour (sparkles, eyes, marks); they join the silhouette. */
  dots(pts, color) {
    const r = flat(color);
    for (const [x, y] of pts) if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) this.cell[idx(x, y)] = {r, level: 'mid'};
    return this;
  }
  /** Draws a flat-colour mask on top of what is there, only where something is already painted. */
  mark(mask, color) {
    const r = flat(color);
    for (let i = 0; i < mask.length; i++) if (mask[i] && this.cell[i]) this.cell[i] = {r, level: 'mid'};
    return this;
  }
  /** The bounding box of painted pixels. */
  box() {
    let x0 = SIZE, y0 = SIZE, x1 = -1, y1 = -1;
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (this.cell[idx(x, y)]) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    return x1 < 0 ? null : {x0, y0, x1, y1};
  }
  /** Outline, centre the silhouette (with its outline), return the pixels (an array of [r, g, b] or null). */
  finish(opts = {}) {
    const b = this.box();
    if (!b) throw new Error('empty icon');
    const w = b.x1 - b.x0 + 1, h = b.y1 - b.y0 + 1;
    const ox = opts.pin ? 0 : Math.floor((SIZE - w) / 2) - b.x0, oy = opts.pin ? 0 : Math.floor((SIZE - h) / 2) - b.y0;
    const grid = new Array(SIZE * SIZE).fill(null);
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
      const c = this.cell[idx(x, y)]; if (!c) continue;
      const nx = x + ox, ny = y + oy;
      if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) throw new Error('icon does not fit after centring');
      grid[idx(nx, ny)] = c;
    }
    const out = new Array(SIZE * SIZE).fill(null);
    const lum = c => c[0] * .3 + c[1] * .59 + c[2] * .11;
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
      const c = grid[idx(x, y)];
      if (c) { out[idx(x, y)] = c.r[c.level]; continue; }
      let dark = null;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) continue;
        const n = grid[idx(nx, ny)]; if (n && (!dark || lum(n.r.dark) < lum(dark))) dark = n.r.dark;
      }
      if (dark) out[idx(x, y)] = dark;
    }
    return out;
  }
}

// ---------------------------------------------------------------------------------------------------------------- PNG
const table = Array.from({length: 256}, (_, n) => { for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1; return n >>> 0; });
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
/** A pixel grid (arrays of [r, g, b(, a)] or null) as an RGBA PNG. */
function png(pixels, width, height) {
  const stride = 1 + width * 4, raw = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    raw[y * stride] = 0;
    for (let x = 0; x < width; x++) {
      const c = pixels[y * width + x]; if (!c) continue;
      raw.set([c[0], c[1], c[2], c.length > 3 ? c[3] : 255], y * stride + 1 + x * 4);
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

module.exports = {SIZE, ramp, flat, hex, mix, M, Icon, png, crc32, idx, count};
