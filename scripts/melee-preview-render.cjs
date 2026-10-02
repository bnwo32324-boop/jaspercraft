'use strict';
// Offline first-person preview of held-item motion. It reproduces the 1.12 first-person chain of the deployed client
// (hand projection gluPerspective(70), transformSideFirstPerson, the melee motion's translate + rotate, the item's
// "firstperson_righthand" display transform as applied by the native DqX: translate, rotate X then Y then Z, scale,
// then RenderItem's translate(-0.5)) on the extruded item sprite (ItemModelGenerator: front/back faces plus one-pixel
// edge faces), in a small software rasterizer. Used to judge choreography frame by frame and to plot the blade path.
const fs = require('node:fs'), zlib = require('node:zlib');

// ------------------------------------------------------------------ PNG
function decodePNG(buf) {
  let p = 8, width = 0, height = 0, depth = 0, ctype = 0, palette = null, trns = null;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString('latin1', p + 4, p + 8), data = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); depth = data[8]; ctype = data[9]; if (data[12]) throw Error('interlaced PNG'); }
    else if (type === 'PLTE') palette = data;
    else if (type === 'tRNS') trns = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    p += 12 + len;
  }
  if (depth !== 8) throw Error('unsupported PNG bit depth ' + depth);
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype], stride = width * channels;
  const raw = zlib.inflateSync(Buffer.concat(idat)), out = Buffer.alloc(width * height * 4);
  let prev = Buffer.alloc(stride), line = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (stride + 1)], src = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? line[x - channels] : 0, b = prev[x], c = x >= channels ? prev[x - channels] : 0;
      let v = src[x];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      line[x] = v & 255;
    }
    for (let x = 0; x < width; x++) {
      const o = (y * width + x) * 4;
      if (ctype === 6) { line.copy(out, o, x * 4, x * 4 + 4); }
      else if (ctype === 2) { line.copy(out, o, x * 3, x * 3 + 3); out[o + 3] = 255; }
      else if (ctype === 3) { const i = line[x]; out[o] = palette[i * 3]; out[o + 1] = palette[i * 3 + 1]; out[o + 2] = palette[i * 3 + 2]; out[o + 3] = trns && i < trns.length ? trns[i] : 255; }
      else if (ctype === 4) { out[o] = out[o + 1] = out[o + 2] = line[x * 2]; out[o + 3] = line[x * 2 + 1]; }
      else { out[o] = out[o + 1] = out[o + 2] = line[x]; out[o + 3] = 255; }
    }
    const t = prev; prev = line; line = t;
  }
  return { width, height, data: out };
}
function chunk(type, data) {
  const head = Buffer.alloc(8); head.writeUInt32BE(data.length, 0); head.write(type, 4, 'latin1');
  const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(Buffer.concat([head.subarray(4), data])) >>> 0, 0);
  return Buffer.concat([head, data, crc]);
}
function encodePNG(width, height, rgb) {
  const raw = Buffer.alloc((width * 3 + 1) * height);
  for (let y = 0; y < height; y++) { raw[y * (width * 3 + 1)] = 0; rgb.copy(raw, y * (width * 3 + 1) + 1, y * width * 3, (y + 1) * width * 3); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 6 })), chunk('IEND', Buffer.alloc(0))]);
}

// ------------------------------------------------------------------ column-major 4x4 matrices with GL post-multiplication
const M = {
  id: () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
  mul(a, b) { const c = new Array(16); for (let col = 0; col < 4; col++) for (let row = 0; row < 4; row++) { let n = 0; for (let k = 0; k < 4; k++) n += a[k * 4 + row] * b[col * 4 + k]; c[col * 4 + row] = n; } return c; },
  translate: (x, y, z) => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1],
  scale: (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1],
  rotate(deg, x, y, z) {
    const r = deg * Math.PI / 180, c = Math.cos(r), s = Math.sin(r), t = 1 - c, l = Math.hypot(x, y, z) || 1; x /= l; y /= l; z /= l;
    return [t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0, t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0, t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0, 0, 0, 0, 1];
  },
  perspective(fovy, aspect, near, far) { const f = 1 / Math.tan(fovy * Math.PI / 360); return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0]; },
  apply(m, v) { const [x, y, z] = v, w = v.length > 3 ? v[3] : 1; return [m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w, m[3] * x + m[7] * y + m[11] * z + m[15] * w]; },
  chain(...ms) { return ms.reduce((a, b) => M.mul(a, b)); }
};

// ------------------------------------------------------------------ item sprite mesh (ItemModelGenerator)
function itemMesh(tex) {
  const W = tex.width, H = tex.height, quads = [], z0 = 7.5 / 16, z1 = 8.5 / 16;
  const alpha = (x, y) => (x < 0 || y < 0 || x >= W || y >= H) ? 0 : tex.data[(y * W + x) * 4 + 3];
  const color = (x, y) => { const o = (y * W + x) * 4; return [tex.data[o], tex.data[o + 1], tex.data[o + 2]]; };
  quads.push({ tex: true, v: [[0, 0, z1], [1, 0, z1], [1, 1, z1], [0, 1, z1]], uv: [[0, 1], [1, 1], [1, 0], [0, 0]], n: [0, 0, 1] });
  quads.push({ tex: true, v: [[1, 0, z0], [0, 0, z0], [0, 1, z0], [1, 1, z0]], uv: [[1, 1], [0, 1], [0, 0], [1, 0]], n: [0, 0, -1] });
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (alpha(x, y) < 26) continue;
    const c = color(x, y), x0 = x / W, x1 = (x + 1) / W, y0 = 1 - (y + 1) / H, y1 = 1 - y / H;
    if (alpha(x - 1, y) < 26) quads.push({ c, v: [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]], n: [-1, 0, 0] });
    if (alpha(x + 1, y) < 26) quads.push({ c, v: [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]], n: [1, 0, 0] });
    if (alpha(x, y - 1) < 26) quads.push({ c, v: [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]], n: [0, 1, 0] });
    if (alpha(x, y + 1) < 26) quads.push({ c, v: [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], n: [0, -1, 0] });
  }
  return { tex, quads };
}

// ------------------------------------------------------------------ rasterizer
function Canvas(W, H) {
  this.W = W; this.H = H; this.rgb = Buffer.alloc(W * H * 3); this.depth = new Float32Array(W * H).fill(Infinity);
}
Canvas.prototype.clearDepth = function () { this.depth.fill(Infinity); };
Canvas.prototype.fill = function (top, bottom) {
  for (let y = 0; y < this.H; y++) { const t = y / (this.H - 1); for (let x = 0; x < this.W; x++) { const o = (y * this.W + x) * 3; for (let k = 0; k < 3; k++) this.rgb[o + k] = Math.round(top[k] + (bottom[k] - top[k]) * t); } }
};
Canvas.prototype.pixel = function (x, y, c) { if (x < 0 || y < 0 || x >= this.W || y >= this.H) return; const o = (y * this.W + x) * 3; this.rgb[o] = c[0]; this.rgb[o + 1] = c[1]; this.rgb[o + 2] = c[2]; };
// polygon in view space -> clipped against the near plane, projected, filled with perspective-correct uv
Canvas.prototype.polygon = function (proj, pts, shade, tex, solid) {
  const near = 0.05;
  let poly = pts;
  const out = [];
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i], b = poly[(i + 1) % poly.length], ina = a.p[2] <= -near, inb = b.p[2] <= -near;
    if (ina) out.push(a);
    if (ina !== inb) { const t = (-near - a.p[2]) / (b.p[2] - a.p[2]); out.push({ p: a.p.map((v, k) => v + (b.p[k] - v) * t), uv: a.uv ? [a.uv[0] + (b.uv[0] - a.uv[0]) * t, a.uv[1] + (b.uv[1] - a.uv[1]) * t] : null }); }
  }
  if (out.length < 3) return;
  const sv = out.map(v => { const c = M.apply(proj, v.p), w = c[3]; return { x: (c[0] / w * 0.5 + 0.5) * this.W, y: (0.5 - c[1] / w * 0.5) * this.H, z: c[2] / w, iw: 1 / w, uv: v.uv }; });
  for (let i = 1; i + 1 < sv.length; i++) this.triangle(sv[0], sv[i], sv[i + 1], shade, tex, solid);
};
Canvas.prototype.triangle = function (a, b, c, shade, tex, solid) {
  const minX = Math.max(0, Math.floor(Math.min(a.x, b.x, c.x))), maxX = Math.min(this.W - 1, Math.ceil(Math.max(a.x, b.x, c.x)));
  const minY = Math.max(0, Math.floor(Math.min(a.y, b.y, c.y))), maxY = Math.min(this.H - 1, Math.ceil(Math.max(a.y, b.y, c.y)));
  const area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
  if (Math.abs(area) < 1e-9) return;
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
    const px = x + 0.5, py = y + 0.5;
    let w0 = ((b.x - px) * (c.y - py) - (b.y - py) * (c.x - px)) / area, w1 = ((c.x - px) * (a.y - py) - (c.y - py) * (a.x - px)) / area, w2 = 1 - w0 - w1;
    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
    const z = w0 * a.z + w1 * b.z + w2 * c.z, o = y * this.W + x;
    if (z >= this.depth[o]) continue;
    let col = solid;
    if (tex) {
      const iw = w0 * a.iw + w1 * b.iw + w2 * c.iw, u = (w0 * a.uv[0] * a.iw + w1 * b.uv[0] * b.iw + w2 * c.uv[0] * c.iw) / iw, v = (w0 * a.uv[1] * a.iw + w1 * b.uv[1] * b.iw + w2 * c.uv[1] * c.iw) / iw;
      const tx = Math.min(tex.width - 1, Math.max(0, Math.floor(u * tex.width))), ty = Math.min(tex.height - 1, Math.max(0, Math.floor(v * tex.height))), t = (ty * tex.width + tx) * 4;
      if (tex.data[t + 3] < 26) continue;
      col = [tex.data[t], tex.data[t + 1], tex.data[t + 2]];
    }
    this.depth[o] = z;
    this.rgb[o * 3] = Math.min(255, col[0] * shade); this.rgb[o * 3 + 1] = Math.min(255, col[1] * shade); this.rgb[o * 3 + 2] = Math.min(255, col[2] * shade);
  }
};
Canvas.prototype.line = function (x0, y0, x1, y1, c) {
  const n = Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) || 1;
  for (let i = 0; i <= n; i++) this.pixel(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), c);
};
Canvas.prototype.dot = function (x, y, r, c) { for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) if (dx * dx + dy * dy <= r * r) this.pixel(Math.round(x + dx), Math.round(y + dy), c); };

// ------------------------------------------------------------------ first-person chain
const LIGHT0 = norm([0.2, 1.0, -0.7]), LIGHT1 = norm([-0.2, 1.0, 0.7]);
function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }
// "firstperson_righthand" of item/handheld (swords, axes, tools): DqX applies translate, Rx, Ry, Rz, scale
const HANDHELD = { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] };
function displayMatrix(display, left) {
  const d = left ? -1 : 1, t = display.translation.map(v => Math.max(-80, Math.min(80, v)) / 16), r = display.rotation.slice();
  // the left-hand JSON entry is the mirror of the right one and DqX negates Y and Z again for the left hand
  return M.chain(M.translate(d * t[0], t[1], t[2]), M.rotate(r[0], 1, 0, 0), M.rotate(r[1], 0, 1, 0), M.rotate(r[2], 0, 0, 1), M.scale(...display.scale), M.translate(-0.5, -0.5, -0.5));
}
// side: +1 right hand, -1 left hand; motion: {tx,ty,tz, rotation:{angle,x,y,z}} or null; equip: 0 = fully raised
function handMatrix(side, motion, equip = 0, display = HANDHELD) {
  let m = M.translate(side * 0.56, -0.52 + equip * -0.6, -0.72);
  if (motion) m = M.chain(m, M.translate(motion.tx, motion.ty, motion.tz), M.rotate(motion.rotation.angle, motion.rotation.x, motion.rotation.y, motion.rotation.z));
  return M.mul(m, displayMatrix(display, side < 0));
}
function drawItem(canvas, proj, mesh, model) {
  const nm = model; // rigid transform: normals with the upper 3x3
  for (const q of mesh.quads) {
    const pts = q.v.map((v, i) => ({ p: M.apply(model, v), uv: q.uv ? q.uv[i] : null }));
    const n = norm(M.apply(nm, [q.n[0], q.n[1], q.n[2], 0]));
    const light = Math.min(1, 0.4 + 0.6 * (Math.max(0, n[0] * LIGHT0[0] + n[1] * LIGHT0[1] + n[2] * LIGHT0[2]) + Math.max(0, n[0] * LIGHT1[0] + n[1] * LIGHT1[1] + n[2] * LIGHT1[2])));
    canvas.polygon(proj, pts, light, q.tex ? mesh.tex : null, q.c || null);
  }
}
// A flat world for scale: sky, a grass plain with a block grid, and a zombie-sized target 2.2 blocks ahead.
function drawWorld(canvas, aspect) {
  canvas.fill([120, 167, 255], [175, 205, 255]);
  const proj = M.perspective(70, aspect, 0.05, 200), eye = 1.62;
  for (let gz = -40; gz < 2; gz++) for (let gx = -20; gx < 20; gx++) {
    const shade = (gx + gz) & 1 ? 0.92 : 1;
    canvas.polygon(proj, [[gx, -eye, gz], [gx + 1, -eye, gz], [gx + 1, -eye, gz + 1], [gx, -eye, gz + 1]].map(p => ({ p })), shade, null, [108, 152, 47]);
  }
  canvas.clearDepth();
  const box = (x0, y0, z0, x1, y1, z1, c) => {
    const f = [[[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1], 1], [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1], 1.05], [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0], 0.8], [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1], 0.8]];
    for (const q of f) canvas.polygon(proj, q.slice(0, 4).map(p => ({ p })), q[4], null, c);
  };
  const z = -2.2;
  box(-0.25, -eye, z - 0.125, -0.01, -eye + 0.75, z + 0.125, [60, 70, 140]);
  box(0.01, -eye, z - 0.125, 0.25, -eye + 0.75, z + 0.125, [60, 70, 140]);
  box(-0.25, -eye + 0.75, z - 0.125, 0.25, -eye + 1.5, z + 0.125, [40, 150, 160]);
  box(-0.5, -eye + 0.8, z - 0.125, -0.25, -eye + 1.5, z + 0.125, [80, 140, 70]);
  box(0.25, -eye + 0.8, z - 0.125, 0.5, -eye + 1.5, z + 0.125, [80, 140, 70]);
  box(-0.25, -eye + 1.5, z - 0.25, 0.25, -eye + 2.0, z + 0.25, [80, 150, 70]);
  canvas.clearDepth();
}
function crosshair(canvas) {
  const cx = canvas.W >> 1, cy = canvas.H >> 1;
  canvas.line(cx - 6, cy, cx + 6, cy, [255, 255, 255]); canvas.line(cx, cy - 6, cx, cy + 6, [255, 255, 255]);
}
// screen position of a model-space point (for path plots)
function project(proj, model, v, W, H) { const c = M.apply(proj, M.apply(model, v)), w = c[3]; return { x: (c[0] / w * 0.5 + 0.5) * W, y: (0.5 - c[1] / w * 0.5) * H, behind: w <= 0.05 }; }
function frame(opts) {
  const W = opts.width || 320, H = opts.height || 180, canvas = new Canvas(W, H), aspect = W / H;
  drawWorld(canvas, aspect);
  const proj = M.perspective(70, aspect, 0.05, 50);
  for (const item of opts.items) drawItem(canvas, proj, item.mesh, handMatrix(item.side, item.motion, item.equip || 0, item.display));
  crosshair(canvas);
  return canvas;
}
function sheet(canvases, cols, labels) {
  const W = canvases[0].W, H = canvases[0].H, rows = Math.ceil(canvases.length / cols), out = new Canvas(cols * W + (cols - 1) * 2, rows * H + (rows - 1) * 2);
  out.rgb.fill(20);
  canvases.forEach((c, i) => { const ox = (i % cols) * (W + 2), oy = Math.floor(i / cols) * (H + 2); for (let y = 0; y < H; y++) c.rgb.copy(out.rgb, ((oy + y) * out.W + ox) * 3, y * W * 3, (y + 1) * W * 3); if (labels) label(out, ox + 2, oy + H - 3, labels[i]); });
  return out;
}
// progress bar along the bottom edge of a frame (the fraction of the clip it shows)
function label(canvas, x, y, fraction) {
  const w = Math.round((canvas.W - 4) * Math.max(0, Math.min(1, fraction)));
  for (let i = 0; i < w; i++) { canvas.pixel(x + i, y, [255, 220, 0]); canvas.pixel(x + i, y + 1, [255, 220, 0]); }
}
function save(canvas, file) { fs.writeFileSync(file, encodePNG(canvas.W, canvas.H, canvas.rgb)); }

module.exports = { decodePNG, encodePNG, M, itemMesh, Canvas, frame, sheet, save, handMatrix, displayMatrix, project, HANDHELD, drawWorld, drawItem, crosshair, label };
