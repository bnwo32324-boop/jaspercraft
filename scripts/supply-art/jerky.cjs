'use strict';
/* The Sanitized Flesh icon (owner, 2026-10-05: "sanitized flesh should have its own texture, and it should look like jerky").
 * A 16x16 pair of flat dried-meat strips tied with twine. Each strip is a ribbon that climbs from the lower left to the upper
 * right, drawn column by column: lit on top, curling into shadow underneath, grained along its length (dry fibres), flecked
 * with salt and cracked pepper, and the whole bundle gets a dark outline. Deterministic.
 * `pixels()` returns 256 entries ([r, g, b] or null); `png()` the encoded file.
 */
const {png} = require('./../trinket-art/engine.cjs');

const C = {
  outline: [0x22, 0x0e, 0x09], deep: [0x3c, 0x16, 0x10], dark: [0x55, 0x20, 0x16], meat: [0x70, 0x2c, 0x1c],
  light: [0x8c, 0x3c, 0x24], high: [0xad, 0x55, 0x30], fibre: [0xc4, 0x7c, 0x4c], salt: [0xdc, 0xc0, 0x92], pepper: [0x18, 0x0a, 0x08],
  twine: [0xd9, 0xbb, 0x78], twineLit: [0xee, 0xd8, 0x9b], twineDark: [0x8f, 0x6d, 0x39]
};
/**
 * Ribbons from back to front. x0..x1 is the span; yc(x) = y0 - slope * (x - x0) is the centre line; t(x) the thickness in
 * rows (it wobbles by a row so the edge is torn and not sawn); `end` lists the rows trimmed from each end so they look cut.
 */
const RIBBONS = [
  {x0: 4, x1: 14, y0: 5.4, slope: 0.5, t: 3.2, seed: 1},
  {x0: 2, x1: 14, y0: 8.7, slope: 0.5, t: 3.4, seed: 2},
  {x0: 1, x1: 13, y0: 12.2, slope: 0.5, t: 3.4, seed: 3}
];
const TWINE = {x: 7};

/** A cheap deterministic hash in [0, 1) for flecks and wobble. */
function noise(x, y, seed) { let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return ((h >>> 0) % 1000) / 1000; }

/** The rows [top, bottom] a ribbon covers in column x, or null outside its span. */
function column(r, x) {
  if (x < r.x0 || x > r.x1) return null;
  const yc = r.y0 - r.slope * (x - r.x0) + 0.55 * Math.sin(x * 0.8 + r.seed * 2.1);
  const wob = (noise(x, 0, r.seed) - 0.5) * 1.1, wob2 = (noise(x, 1, r.seed) - 0.5) * 1.1;
  let top = Math.round(yc - r.t / 2 + wob), bot = Math.round(yc + r.t / 2 + wob2);
  // The ends are cut at an angle: the first and last column lose a row on the side that faces the end.
  if (x === r.x0) bot -= 1;
  if (x === r.x1) top += 1;
  return bot >= top ? [top, bot] : null;
}

function shadeAt(r, ri, x, y, top, bot) {
  const rows = bot - top, k = y - top;
  let col = C.meat;
  if (k === 0) col = C.high;
  else if (k === 1 && rows >= 3) col = C.light;
  else if (k === rows) col = C.deep;
  else if (k === rows - 1 && rows >= 3) col = C.dark;
  // Fibres: a row in the middle of the strip runs lighter, broken by a darker crease every few columns.
  if (k > 0 && k < rows) {
    const line = (y + Math.floor((x - r.x0) * r.slope + 0.5)) % 2 === 0;
    if (line && ((x * 5 + ri * 3) % 7) !== 0) col = k <= rows / 2 ? C.light : C.meat;
    if (!line && ((x * 3 + ri) % 5) === 0) col = C.dark;
  }
  const n = noise(x, y, 11 + ri);
  if (n > 0.955 && k > 0 && k < rows) col = C.salt;
  else if (n > 0.915 && k > 0 && k < rows) col = C.pepper;
  else if (n < 0.04 && k > 0 && k < rows) col = C.fibre;
  return col;
}

function pixels() {
  const N = 16, grid = new Array(N * N).fill(null);
  RIBBONS.forEach((r, ri) => {
    for (let x = 0; x < N; x++) {
      const c = column(r, x); if (!c) continue;
      for (let y = c[0]; y <= c[1]; y++) if (y >= 0 && y < N) grid[y * N + x] = shadeAt(r, ri, x, y, c[0], c[1]);
    }
    // Where this strip lies over the one behind it, the strip behind gets a dark edge.
    if (ri > 0) for (let x = 0; x < N; x++) {
      const c = column(r, x), b = column(RIBBONS[ri - 1], x); if (!c || !b) continue;
      const y = c[0] - 1;
      if (y >= b[0] && y <= b[1] && y >= 0) grid[y * N + x] = C.deep;
    }
  });
  // Twine: one pixel wide across both strips, twisted (two tones alternate), with a short tail below the knot.
  let lo = N, hi = -1;
  for (let y = 0; y < N; y++) if (grid[y * N + TWINE.x]) { lo = Math.min(lo, y); hi = Math.max(hi, y); }
  for (let y = lo; y <= hi; y++) grid[y * N + TWINE.x] = y % 2 === 0 ? C.twineLit : C.twine;
  grid[(hi + 1) * N + TWINE.x] = C.twineDark; grid[(hi + 1) * N + TWINE.x + 1] = C.twine; grid[(hi + 2) * N + TWINE.x + 1] = C.twineDark;
  const out = grid.slice();
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (grid[y * N + x]) continue;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx, ny = y + dy;
      if (nx >= 0 && ny >= 0 && nx < N && ny < N && grid[ny * N + nx]) { out[y * N + x] = C.outline; break; }
    }
  }
  return out;
}
module.exports = {pixels, png: () => png(pixels(), 16, 16), C};
