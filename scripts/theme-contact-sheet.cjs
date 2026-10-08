'use strict';
// Renders the recoloured textures of the JasperCraft look next to their vanilla versions (a review aid, no browser): one PNG per group
// of textures, each texture as [vanilla | themed] at a scale, on a dark ground. Reads the EPK (default: the live one).
//   node scripts/theme-contact-sheet.cjs <out folder> [scale] [group size]       (env THEME_EPK)
const fs = require('node:fs'), path = require('node:path');
const png = require('./png-codec.cjs');
const theme = require('./jasper-theme.cjs');
const {decode} = require('./merge-apocalypse-assets.cjs');
const EPK = process.env.THEME_EPK || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/assets.epk';
const [out, scaleArg = '2', groupArg = '2'] = process.argv.slice(2);
if (!out) throw new Error('usage: node scripts/theme-contact-sheet.cjs <out folder> [scale] [group size]');
const scale = Number(scaleArg), group = Number(groupArg);
const by = new Map(decode(fs.readFileSync(EPK)).entries.map(e => [e.name, e.value]));
const names = Object.keys(theme.TEXTURES);
function crop(img) {                                       // the used part of a texture: up to the last opaque row / column
  let w = 0, h = 0;
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) if (img.rgba[(y * img.w + x) * 4 + 3] > 0) { w = Math.max(w, x + 1); h = Math.max(h, y + 1); }
  return {w, h};
}
function draw(canvas, cw, img, dx, dy, c) {
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) {
    const i = (y * img.w + x) * 4, a = img.rgba[i + 3];
    for (let sy = 0; sy < scale; sy++) for (let sx = 0; sx < scale; sx++) {
      const o = ((dy + y * scale + sy) * cw + dx + x * scale + sx) * 4;
      canvas[o] = Math.round(0x16 * (1 - a / 255) + img.rgba[i] * a / 255); canvas[o + 1] = Math.round(0x16 * (1 - a / 255) + img.rgba[i + 1] * a / 255);
      canvas[o + 2] = Math.round(0x1a * (1 - a / 255) + img.rgba[i + 2] * a / 255); canvas[o + 3] = 255;
    }
  }
}
fs.mkdirSync(out, {recursive: true});
for (let g = 0; g * group < names.length; g++) {
  const part = names.slice(g * group, g * group + group).map(n => {
    const vanillaBytes = by.get('assets/minecraft/textures/gui/' + n), now = png.decode(vanillaBytes);
    // The archive may already carry the theme: the vanilla side is then not available; show the themed one twice.
    const themed = theme.retheme(now, n);
    return {n, vanilla: themed === now ? null : now, themed, c: crop(themed)};
  });
  const pad = 8, cw = part.reduce((s, p) => s + (p.c.w * scale + pad) * 2, pad), ch = Math.max(...part.map(p => p.c.h)) * scale + pad * 2;
  const canvas = Buffer.alloc(cw * ch * 4);
  for (let i = 0; i < cw * ch; i++) { canvas[i * 4] = 0x16; canvas[i * 4 + 1] = 0x16; canvas[i * 4 + 2] = 0x1a; canvas[i * 4 + 3] = 255; }
  let x = pad;
  for (const p of part) {
    if (p.vanilla) draw(canvas, cw, p.vanilla, x, pad, p.c);
    x += p.c.w * scale + pad;
    draw(canvas, cw, p.themed, x, pad, p.c);
    x += p.c.w * scale + pad;
  }
  const file = path.join(out, 'sheet' + g + '.png');
  fs.writeFileSync(file, png.encode({w: cw, h: ch, rgba: canvas}));
  console.log(file, part.map(p => p.n).join(', '));
}
