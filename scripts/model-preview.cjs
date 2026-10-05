'use strict';
/* A small software renderer for Minecraft 1.12 item models (cuboid elements with per-face UVs), so a model can be designed and
 * checked without starting a game client. It follows the game's own conventions: face vertex order and UV corner assignment
 * (FaceBakery / EnumFaceDirection), element rotation about an origin, display transforms applied as
 * translate * rotateY * rotateX * rotateZ * scale * translate(-0.5) (ItemCameraTransforms.applyTransformSide), nearest-neighbour
 * texture sampling, back faces culled, and a simple directional light. Orthographic only.
 *
 *   node scripts/model-preview.cjs <model.json> <textures-folder> <out.png> [view...]
 * views: gui | side | front | rear | top | iso | iso2 | hand1 | hand3 (default: gui side iso top)
 * <textures-folder> holds the PNGs the model names (items/foo -> <folder>/items/foo.png); missing ones render magenta.
 */
const fs = require('node:fs'), path = require('node:path');
const {decode, encode} = require('./png-codec.cjs');

// ------------------------------------------------------------------------------------------------------------- vectors
const rad = d => d * Math.PI / 180;
function rot(p, axis, deg) {
  const c = Math.cos(rad(deg)), s = Math.sin(rad(deg)), [x, y, z] = p;
  if (axis === 'x') return [x, y * c - z * s, y * s + z * c];
  if (axis === 'y') return [x * c + z * s, y, -x * s + z * c];
  return [x * c - y * s, x * s + y * c, z];
}
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = v => { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; };
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// --------------------------------------------------------------------------------------------------------------- baking
/** Vertex corner selectors per face, in the game's order: vertex 0 gets (u0, v0), 1 (u0, v1), 2 (u1, v1), 3 (u1, v0). */
const FACE_VERTS = {
  north: [['x1', 'y1', 'z0'], ['x1', 'y0', 'z0'], ['x0', 'y0', 'z0'], ['x0', 'y1', 'z0']],
  south: [['x0', 'y1', 'z1'], ['x0', 'y0', 'z1'], ['x1', 'y0', 'z1'], ['x1', 'y1', 'z1']],
  west: [['x0', 'y1', 'z0'], ['x0', 'y0', 'z0'], ['x0', 'y0', 'z1'], ['x0', 'y1', 'z1']],
  east: [['x1', 'y1', 'z1'], ['x1', 'y0', 'z1'], ['x1', 'y0', 'z0'], ['x1', 'y1', 'z0']],
  up: [['x0', 'y1', 'z0'], ['x0', 'y1', 'z1'], ['x1', 'y1', 'z1'], ['x1', 'y1', 'z0']],
  down: [['x0', 'y0', 'z1'], ['x0', 'y0', 'z0'], ['x1', 'y0', 'z0'], ['x1', 'y0', 'z1']]
};
const NORMALS = {north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0], up: [0, 1, 0], down: [0, -1, 0]};

/** The textured quads of a model, in model units (0..16): {v: 4 points, uv: 4 [u, v] in 0..16, tex, n}. */
function bake(model) {
  const quads = [];
  for (const e of model.elements || []) {
    const [x0, y0, z0] = e.from, [x1, y1, z1] = e.to, sel = {x0, y0, z0, x1, y1, z1};
    for (const [dir, face] of Object.entries(e.faces || {})) {
      const verts = FACE_VERTS[dir].map(([a, b, c]) => [sel[a], sel[b], sel[c]]);
      const uv = face.uv || [0, 0, 16, 16];
      const corners = [[uv[0], uv[1]], [uv[0], uv[3]], [uv[2], uv[3]], [uv[2], uv[1]]];
      const shift = ((face.rotation || 0) / 90) % 4;
      const uvs = [0, 1, 2, 3].map(i => corners[(i + shift) % 4]);
      let pts = verts, n = NORMALS[dir];
      if (e.rotation && e.rotation.angle) {
        const o = e.rotation.origin || [8, 8, 8], axis = e.rotation.axis, a = e.rotation.angle;
        const k = e.rotation.rescale ? 1 / Math.cos(rad(Math.abs(a))) : 1;
        pts = verts.map(p => {
          let q = sub(p, o); q = rot(q, axis, a);
          if (k !== 1) q = q.map((c, i) => 'xyz'[i] === axis ? c : c * k);
          return [q[0] + o[0], q[1] + o[1], q[2] + o[2]];
        });
        n = rot(n, axis, a);
      }
      quads.push({v: pts, uv: uvs, tex: face.texture, n, element: e.__comment || ''});
    }
  }
  return quads;
}

function textureName(model, ref) {
  let r = ref, guard = 0;
  while (typeof r === 'string' && r[0] === '#' && guard++ < 8) r = (model.textures || {})[r.slice(1)];
  return r;
}

// ----------------------------------------------------------------------------------------------------------- transforms
/** A display transform as a function on model points (0..16 units) giving block-space coordinates (1 = a block). */
function displayFn(d) {
  if (!d) return p => p.map(c => c / 16 - 0.5);
  const rotation = d.rotation || [0, 0, 0], translation = d.translation || [0, 0, 0], scale = d.scale || [1, 1, 1];
  return p => {
    let q = p.map(c => c / 16 - 0.5);
    q = q.map((c, i) => c * scale[i]);
    q = rot(q, 'z', rotation[2]); q = rot(q, 'x', rotation[0]); q = rot(q, 'y', rotation[1]);
    return [q[0] + translation[0] / 16, q[1] + translation[1] / 16, q[2] + translation[2] / 16];
  };
}
/** Named cameras: an orbit (yaw about Y, pitch about X) applied after the display transform, plus a zoom. */
const VIEWS = {
  // zoom: pixels per block as a fraction of the picture width; 'fit' frames the whole model.
  gui: {display: 'gui', yaw: 0, pitch: 0, zoom: 1.0, label: 'gui slot', fixedCentre: [0, 0, 0]},
  side: {display: null, yaw: 90, pitch: 0, zoom: 'fit', label: 'side (muzzle left)'},
  front: {display: null, yaw: 180, pitch: 0, zoom: 'fit', label: 'muzzle on'},
  rear: {display: null, yaw: 0, pitch: 0, zoom: 'fit', label: 'from behind'},
  top: {display: null, yaw: 0, pitch: 90, zoom: 'fit', label: 'top (muzzle up)'},
  iso: {display: null, yaw: 150, pitch: 22, zoom: 'fit', label: 'three quarter'},
  iso2: {display: null, yaw: 215, pitch: 20, zoom: 'fit', label: 'other side'},
  hand1: {display: 'firstperson_righthand', yaw: 0, pitch: 0, zoom: 0.8, label: 'first person', fixedCentre: [0, 0, 0]},
  hand3: {display: 'thirdperson_righthand', yaw: 0, pitch: 0, zoom: 0.8, label: 'third person', fixedCentre: [0, 0, 0]}
};

const AXES = [['down', [0, -1, 0]], ['up', [0, 1, 0]], ['north', [0, 0, -1]], ['south', [0, 0, 1]], ['west', [-1, 0, 0]], ['east', [1, 0, 0]]];
/** FaceBakery.getFacingFromVertexData: the axis a quad is treated as facing; the first of an equal pair wins. */
function snapFacing(n) {
  let best = null, f = 0;
  for (const [, v] of AXES) { const d = dot(n, v); if (d >= 0 && d > f) { f = d; best = v; } }
  return best || n;
}
function lightFor(n) {
  // Two lights like the GUI's standard item lighting, plus ambient: bright from above and the viewer's left.
  const l1 = norm([0.2, 1, -0.7]), l2 = norm([-0.2, 1, 0.7]);
  return Math.min(1, 0.4 + 0.6 * Math.max(0, dot(n, l1)) * 0.65 + 0.6 * Math.max(0, dot(n, l2)) * 0.65);
}

// -------------------------------------------------------------------------------------------------------------- raster
function render(model, textures, viewName, size = 256, opts = {}) {
  const view = VIEWS[viewName]; if (!view) throw new Error('unknown view ' + viewName);
  const quads = bake(model), toBlock = displayFn(view.display ? (model.display || {})[view.display] : null);
  const ss = opts.supersample || 2, W = size * ss, H = size * ss;
  const color = new Uint8ClampedArray(W * H * 4), depth = new Float32Array(W * H).fill(-Infinity);
  const bgA = opts.bg || [92, 92, 100];
  const proj = p => {
    let q = toBlock(p);
    q = rot(q, 'y', view.yaw); q = rot(q, 'x', view.pitch);
    return q;
  };
  // Framing: a fixed centre for the game's own views, otherwise the middle and radius of the model's bounding sphere.
  let cp = [0, 0, 0], px = W * (typeof view.zoom === 'number' ? view.zoom : 1);
  if (!view.fixedCentre || view.zoom === 'fit') {
    const all = quads.flatMap(q => q.v.map(proj));
    const lo = [0, 1, 2].map(i => Math.min(...all.map(p => p[i]))), hi = [0, 1, 2].map(i => Math.max(...all.map(p => p[i])));
    cp = [0, 1, 2].map(i => (lo[i] + hi[i]) / 2);
    if (view.zoom === 'fit') px = W / (Math.max(hi[0] - lo[0], hi[1] - lo[1]) * 1.12);
  }
  for (const quad of quads) {
    const camPts = quad.v.map(proj);
    // Face normal in camera space from the transformed vertices (more robust than rotating the normal by hand).
    const nrm = norm(cross(sub(camPts[1], camPts[0]), sub(camPts[2], camPts[1])));
    if (nrm[2] <= 1e-6) continue;    // back face: culled, like the game
    const name = textureName(model, quad.tex), tex = textures(name);
    // The game lights a quad by the axis it is closest to, not by its true slope: turn that axis into view space.
    const snapped = snapFacing(norm(cross(sub(quad.v[1], quad.v[0]), sub(quad.v[2], quad.v[1]))));
    const o0 = proj([0, 0, 0].map((_, i) => 8)), o1 = proj([8 + snapped[0], 8 + snapped[1], 8 + snapped[2]]);
    const light = lightFor(norm(sub(o1, o0)));
    const scr = camPts.map(q => [W / 2 + (q[0] - cp[0]) * px, H / 2 - (q[1] - cp[1]) * px, q[2]]);
    for (const tri of [[0, 1, 2], [0, 2, 3]]) {
      const a = scr[tri[0]], b = scr[tri[1]], c = scr[tri[2]];
      // Mip level like the game's atlas (nearest texel, linear between levels): texels covered by one final-size pixel.
      let lod = 0;
      if (opts.mip !== false && tex.mips) {
        const pa = quad.uv[tri[0]], pb = quad.uv[tri[1]], pc = quad.uv[tri[2]];
        const uvArea = Math.abs((pb[0] - pa[0]) * (pc[1] - pa[1]) - (pc[0] - pa[0]) * (pb[1] - pa[1])) / 2 / 256 * tex.w * tex.h;
        const scrArea = Math.abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2 / (ss * ss);
        if (scrArea > 1e-6 && uvArea > 0) lod = Math.max(0, Math.min(tex.mips.length - 1, 0.5 * Math.log2(uvArea / scrArea)));
      }
      const minX = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]))), maxX = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
      const minY = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]))), maxY = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
      const den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
      if (Math.abs(den) < 1e-9) continue;
      for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
        const sx = x + 0.5, sy = y + 0.5;
        const l1 = ((b[1] - c[1]) * (sx - c[0]) + (c[0] - b[0]) * (sy - c[1])) / den;
        const l2 = ((c[1] - a[1]) * (sx - c[0]) + (a[0] - c[0]) * (sy - c[1])) / den;
        const l3 = 1 - l1 - l2;
        if (l1 < -1e-6 || l2 < -1e-6 || l3 < -1e-6) continue;
        const z = l1 * a[2] + l2 * b[2] + l3 * c[2];
        if (z <= depth[y * W + x]) continue;
        const ua = quad.uv[tri[0]], ub = quad.uv[tri[1]], uc = quad.uv[tri[2]];
        const u = (l1 * ua[0] + l2 * ub[0] + l3 * uc[0]) / 16, v = (l1 * ua[1] + l2 * ub[1] + l3 * uc[1]) / 16;
        const t = lod > 0 ? tex.sampleLod(u, v, lod) : tex.sample(u, v);
        if (t[3] < 128) continue;
        depth[y * W + x] = z;
        const o = (y * W + x) * 4;
        color[o] = t[0] * light; color[o + 1] = t[1] * light; color[o + 2] = t[2] * light; color[o + 3] = 255;
      }
    }
  }
  // Downsample (box filter) over the background.
  const out = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    let r = 0, g = 0, b = 0;
    for (let dy = 0; dy < ss; dy++) for (let dx = 0; dx < ss; dx++) {
      const o = ((y * ss + dy) * W + x * ss + dx) * 4;
      if (color[o + 3]) { r += color[o]; g += color[o + 1]; b += color[o + 2]; } else { r += bgA[0]; g += bgA[1]; b += bgA[2]; }
    }
    const n = ss * ss, o = (y * size + x) * 4;
    out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n; out[o + 3] = 255;
  }
  return {w: size, h: size, rgba: out};
}

// ----------------------------------------------------------------------------------------------------------- textures
function textureLoader(folder) {
  const cache = new Map();
  return name => {
    if (cache.has(name)) return cache.get(name);
    const file = path.join(folder, name + '.png');
    let t;
    if (fs.existsSync(file)) {
      const img = decode(fs.readFileSync(file));
      // A mip pyramid like the game builds per sprite: each level averages 2x2 texels (transparent ones do not count).
      const mips = [img];
      while (mips.length < 5 && mips[mips.length - 1].w > 1 && mips[mips.length - 1].h > 1) {
        const p = mips[mips.length - 1], w = p.w >> 1, h = p.h >> 1, rgba = Buffer.alloc(w * h * 4);
        for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
          let r = 0, g = 0, b = 0, a = 0, n = 0;
          for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
            const o = ((y * 2 + dy) * p.w + x * 2 + dx) * 4;
            if (p.rgba[o + 3] > 0) { r += p.rgba[o]; g += p.rgba[o + 1]; b += p.rgba[o + 2]; a += p.rgba[o + 3]; n++; }
          }
          const q = (y * w + x) * 4;
          if (n) { rgba[q] = r / n; rgba[q + 1] = g / n; rgba[q + 2] = b / n; rgba[q + 3] = a / 4; }
        }
        mips.push({w, h, rgba});
      }
      const at = (lvl, u, v) => {
        const m = mips[lvl], x = Math.max(0, Math.min(m.w - 1, Math.floor(u * m.w))), y = Math.max(0, Math.min(m.h - 1, Math.floor(v * m.h)));
        const o = (y * m.w + x) * 4; return [m.rgba[o], m.rgba[o + 1], m.rgba[o + 2], m.rgba[o + 3]];
      };
      t = {w: img.w, h: img.h, mips, sample: (u, v) => at(0, u, v), sampleLod: (u, v, lod) => {
        const lo = Math.floor(lod), hi = Math.min(mips.length - 1, lo + 1), f = lod - lo, a = at(lo, u, v), b = at(hi, u, v);
        return [0, 1, 2, 3].map(i => a[i] + (b[i] - a[i]) * f);
      }};
    } else t = {w: 16, h: 16, missing: true, sample: (u, v) => ((Math.floor(u * 16) + Math.floor(v * 16)) & 1 ? [255, 0, 255, 255] : [40, 0, 40, 255])};
    cache.set(name, t);
    return t;
  };
}

/** A contact sheet: each view rendered at `size`, side by side with a thin gap. */
function sheet(model, folder, views, size = 256, opts = {}) {
  const tl = textureLoader(folder), imgs = views.map(v => render(model, tl, v, size, opts));
  const gap = 4, W = imgs.length * size + (imgs.length - 1) * gap, out = Buffer.alloc(W * size * 4, 0);
  imgs.forEach((img, k) => {
    for (let y = 0; y < size; y++) img.rgba.copy(out, (y * W + k * (size + gap)) * 4, y * size * 4, (y + 1) * size * 4);
  });
  for (let i = 3; i < out.length; i += 4) if (!out[i]) { out[i] = 255; out[i - 1] = out[i - 2] = out[i - 3] = 40; }
  return {w: W, h: size, rgba: out};
}

if (require.main === module) {
  const [modelFile, folder, outFile, ...views] = process.argv.slice(2);
  if (!modelFile || !folder || !outFile) { console.log('usage: model-preview.cjs <model.json> <textures-folder> <out.png> [view...]'); process.exit(1); }
  const model = JSON.parse(fs.readFileSync(modelFile, 'utf8'));
  const size = +(process.env.SIZE || 256);
  const img = sheet(model, folder, views.length ? views : ['gui', 'side', 'iso', 'top'], size);
  fs.mkdirSync(path.dirname(path.resolve(outFile)), {recursive: true});
  fs.writeFileSync(outFile, encode(img));
  console.log(outFile, img.w + 'x' + img.h);
}
module.exports = {bake, render, sheet, textureLoader, VIEWS, displayFn};
