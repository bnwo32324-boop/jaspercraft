'use strict';
// Renders first-person melee clips offline: a strip of frames per clip and a path plot (grip and blade tip on screen
// at equal time steps; wide spacing = fast). Usage: node scripts/melee-preview.cjs [outDir] [clip ...]
const fs = require('node:fs'), path = require('node:path');
const R = require('./melee-preview-render.cjs');
const motion = require('../client-mods/melee/melee-motion.js');
const out = process.argv[2] || path.join(__dirname, '../candidate/melee-preview/out');
const textures = path.join(__dirname, '../candidate/melee-preview/assets/textures/items');
// the item textures come from the game's own assets.epk (EPK v2: header, entry count, compression flag, entries)
if (!fs.existsSync(textures)) extractTextures(process.env.JASPR_ASSETS_EPK || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/assets.epk', textures);
function extractTextures(file, dir) {
  const zlib = require('node:zlib'), bytes = fs.readFileSync(file);
  let at = 8; at += 1 + bytes[at]; at += 1 + bytes[at]; at += 2 + bytes.readUInt16BE(at); at += 8;
  const count = bytes.readUInt32BE(at); at += 4; const mode = String.fromCharCode(bytes[at++]), packed = bytes.subarray(at, -8);
  const data = mode === 'G' ? zlib.gunzipSync(packed) : mode === 'Z' ? zlib.inflateSync(packed) : packed;
  fs.mkdirSync(dir, { recursive: true });
  for (let i = 0, p = 0; i < count; i++) {
    const type = data.subarray(p, p + 4).toString(); p += 4;
    const n = data[p++], name = data.subarray(p, p + n).toString(); p += n;
    const size = data.readUInt32BE(p); p += 4;
    if (type === 'FILE') { const value = data.subarray(p + 4, p + size - 1); p += size;
      const m = name.match(/textures\/items\/([a-z_]+)\.png$/); if (m) fs.writeFileSync(path.join(dir, m[1] + '.png'), value); }
    else p += size;
    p++;   // '>'
  }
}
fs.mkdirSync(out, { recursive: true });
const item = { sword: 'diamond_sword', axe: 'iron_axe', heavy: 'iron_axe', spear: 'iron_sword', hook: 'iron_hoe', dagger: 'iron_sword', tool: 'iron_pickaxe', fist: 'iron_sword' };
const meshes = {};
function mesh(name) { return meshes[name] || (meshes[name] = R.itemMesh(R.decodePNG(fs.readFileSync(path.join(textures, name + '.png'))))); }
const family = n => Object.keys(motion.groups).find(g => motion.groups[g].includes(n));
const clips = process.argv.length > 3 ? process.argv.slice(3) : motion.clipNames.filter(n => !n.startsWith('fist'));
const W = 320, H = 180, N = 14;
for (const name of clips) {
  const m = mesh(item[family(name)]), frames = [], marks = [];
  for (let i = 0; i < N; i++) { const p = i / (N - 1) * 0.999; frames.push(R.frame({ width: W, height: H, items: [{ mesh: m, side: 1, motion: motion.pose(name, p, true) }] })); marks.push(p); }
  R.save(R.sheet(frames, 7, marks), path.join(out, name + '.png'));
  // path plot at 2x
  const c = R.frame({ width: W * 2, height: H * 2, items: [{ mesh: m, side: 1, motion: null }] }), proj = R.M.perspective(70, W / H, 0.05, 50);
  const colors = { windup: [255, 220, 0], stroke: [230, 40, 40], recover: [40, 90, 255] };
  for (let i = 0; i <= 90; i++) {
    const p = i / 90 * 0.999, pose = motion.pose(name, p, true), hm = R.handMatrix(1, pose);
    const tip = R.project(proj, hm, [0.93, 0.93, 0.5], W * 2, H * 2), grip = R.project(proj, hm, [0.22, 0.2, 0.5], W * 2, H * 2);
    const col = colors[pose.phase] || [255, 255, 255];
    if (!tip.behind) c.dot(tip.x, tip.y, 2, col);
    if (!grip.behind) c.dot(grip.x, grip.y, 1, [255, 255, 255]);
  }
  R.save(c, path.join(out, name + '-path.png'));
}
console.log('wrote', clips.length, 'clips to', out);
