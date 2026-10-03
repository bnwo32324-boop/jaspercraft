'use strict';
// Minimal PNG codec for the asset builders: decodes 8-bit greyscale, grey+alpha, RGB, RGBA and palette images (palette
// bit depths 1-8, with tRNS), non-interlaced, into {w, h, rgba: Buffer}; encodes RGBA back to a PNG (filter 0, zlib 9).
const zlib = require('node:zlib');

const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }

function decode(bytes) {
  const b = Buffer.from(bytes);
  if (b.readUInt32BE(0) !== 0x89504e47) throw new Error('not a PNG');
  let o = 8, w = 0, h = 0, depth = 0, type = 0, interlace = 0, palette = null, trns = null;
  const idat = [];
  while (o < b.length) {
    const len = b.readUInt32BE(o), kind = b.toString('ascii', o + 4, o + 8), data = b.subarray(o + 8, o + 8 + len);
    if (kind === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; type = data[9]; interlace = data[12]; }
    else if (kind === 'PLTE') palette = data;
    else if (kind === 'tRNS') trns = data;
    else if (kind === 'IDAT') idat.push(data);
    o += 12 + len;
  }
  if (interlace) throw new Error('interlaced PNG not supported');
  const channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[type];
  if (!channels || (type !== 3 && depth !== 8)) throw new Error('unsupported PNG type ' + type + '/' + depth);
  const bpp = Math.max(1, (channels * depth) >> 3);
  const stride = Math.ceil(w * channels * depth / 8);
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const rgba = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const filter = raw[y * (stride + 1)], line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? line[i - bpp] : 0, up = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      let v = line[i];
      if (filter === 1) v += a; else if (filter === 2) v += up; else if (filter === 3) v += (a + up) >> 1;
      else if (filter === 4) { const p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? up : c; }
      line[i] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      const at = (y * w + x) * 4;
      if (type === 6) line.copy(rgba, at, x * 4, x * 4 + 4);
      else if (type === 2) { rgba[at] = line[x * 3]; rgba[at + 1] = line[x * 3 + 1]; rgba[at + 2] = line[x * 3 + 2]; rgba[at + 3] = 255; }
      else if (type === 0) { rgba[at] = rgba[at + 1] = rgba[at + 2] = line[x]; rgba[at + 3] = 255; }
      else if (type === 4) { rgba[at] = rgba[at + 1] = rgba[at + 2] = line[x * 2]; rgba[at + 3] = line[x * 2 + 1]; }
      else {
        const bit = x * depth, idx = (line[bit >> 3] >> (8 - depth - (bit & 7))) & ((1 << depth) - 1);
        rgba[at] = palette[idx * 3]; rgba[at + 1] = palette[idx * 3 + 1]; rgba[at + 2] = palette[idx * 3 + 2];
        rgba[at + 3] = trns && idx < trns.length ? trns[idx] : 255;
      }
    }
    prev = line;
  }
  return {w, h, rgba};
}

function encode(img) {
  const {w, h, rgba} = img;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const chunk = (kind, data) => {
    const len = Buffer.alloc(4), crc = Buffer.alloc(4), body = Buffer.concat([Buffer.from(kind, 'ascii'), data]);
    len.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

module.exports = {decode, encode, crc32};
