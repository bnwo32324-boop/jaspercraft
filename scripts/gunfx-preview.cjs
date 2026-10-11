'use strict';
// Offline first-person preview of the gun animation stage (client-mods/gunfx): the deployed client's first-person chain
// (hand projection gluPerspective(70), transformSideFirstPerson, the stage's recoil transform, the gun model's own
// firstperson display transform as DqX applies it, RenderItem's translate(-0.5)) on the real apocalypse-pack cuboid models
// with the game's block textures, plus the stage's additive muzzle flash, in the melee preview's software rasterizer.
// Frames are driven through the stage's own core (synthetic item data in, poses out), so what is drawn is what the client
// would draw at those ticks.
//
//   node scripts/gunfx-preview.cjs [outDir] [scenario ...]
// scenarios: shots (one shot per gun class), auto (sustained fire), reload (every reload style), portal, sprites (dungeon
// guns and casters), trace (pose channels over time), mosaic (rest / flash / peak / recovery per gun), paths
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib'), os = require('node:os');
try { os.setPriority(os.constants.priority.PRIORITY_LOW); } catch (e) { /* best effort: stay out of the game's way */ }
const R = require('./melee-preview-render.cjs');
const { bake } = require('./model-preview.cjs');
const { MODEL_OF } = require('./gunfx-data.cjs');
global.JasprGunFXData = require('../client-mods/gunfx/gunfx-data.js');
const core = require('../client-mods/gunfx/gunfx-core.js');
const M = R.M;
const ROOT = path.join(__dirname, '..');
const OUT = process.argv[2] || path.join(ROOT, 'candidate/gunfx-preview');
const ASSETS = path.join(ROOT, 'candidate/gunfx-preview/assets');
const EPK = process.env.JASPR_ASSETS_EPK || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/assets.epk';

// ------------------------------------------------------------------ textures from the game's own archive (read only)
function extract() {
  if (fs.existsSync(path.join(ASSETS, 'blocks/iron_block.png'))) return;
  const bytes = fs.readFileSync(EPK);
  let at = 8; at += 1 + bytes[at]; at += 1 + bytes[at]; at += 2 + bytes.readUInt16BE(at); at += 8;
  const count = bytes.readUInt32BE(at); at += 4; const mode = String.fromCharCode(bytes[at++]), packed = bytes.subarray(at, -8);
  const data = mode === 'G' ? zlib.gunzipSync(packed) : mode === 'Z' ? zlib.inflateSync(packed) : packed;
  for (let i = 0, p = 0; i < count; i++) {
    const type = data.subarray(p, p + 4).toString(); p += 4;
    const n = data[p++], name = data.subarray(p, p + n).toString(); p += n;
    const size = data.readUInt32BE(p); p += 4;
    if (type === 'FILE') {
      const value = data.subarray(p + 4, p + size - 1);
      const m = name.match(/textures\/(blocks|items)\/([a-z0-9_]+)\.png$/);
      if (m) { fs.mkdirSync(path.join(ASSETS, m[1]), { recursive: true }); fs.writeFileSync(path.join(ASSETS, m[1], m[2] + '.png'), value); }
    }
    p += size; p++;
  }
}
const texCache = new Map();
function texture(ref) {
  if (texCache.has(ref)) return texCache.get(ref);
  const rel = String(ref).replace(/^minecraft:/, '');
  let t = null;
  const file = path.join(ASSETS, rel + '.png');
  if (fs.existsSync(file)) { const img = R.decodePNG(fs.readFileSync(file)); t = { width: img.width, height: Math.min(img.height, img.width), data: img.data }; }
  texCache.set(ref, t);
  return t;
}
function resolve(model, ref) { let r = ref, g = 0; while (typeof r === 'string' && r[0] === '#' && g++ < 8) r = (model.textures || {})[r.slice(1)]; return r; }

// ------------------------------------------------------------------ the gun model in the first-person chain
const models = {};
function model(id) {
  if (models[id]) return models[id];
  const m = JSON.parse(fs.readFileSync(path.join(ROOT, 'apocalypse-pack/assets/minecraft/models/item/apocalypse_' + (MODEL_OF[id] || id) + '.json'), 'utf8'));
  return (models[id] = { json: m, quads: bake(m) });
}
const LIGHT0 = norm([0.2, 1.0, -0.7]), LIGHT1 = norm([-0.2, 1.0, 0.7]);
function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }
function handMatrix(side, pose) {
  let m = M.translate(side * 0.56, -0.52, -0.72);
  if (pose) {
    const p = pose.pivot, a = pose.axis;
    m = M.chain(m, M.translate(pose.x + p[0], pose.y + p[1], pose.z + p[2]), M.rotate(pose.yaw, 0, 1, 0), M.rotate(pose.climb, 1, 0, 0),
      M.rotate(pose.roll, a[0], a[1], a[2]), M.translate(-p[0], -p[1], -p[2]));
  }
  return m;
}
function drawGun(canvas, proj, id, hand, side) {
  const g = model(id), d = g.json.display[side < 0 ? 'firstperson_lefthand' : 'firstperson_righthand'];
  const mm = M.chain(hand, R.displayMatrix(d, side < 0), M.scale(1 / 16, 1 / 16, 1 / 16));
  for (const q of g.quads) {
    const pts = q.v.map((v, i) => ({ p: M.apply(mm, v), uv: [q.uv[i][0] / 16, q.uv[i][1] / 16] }));
    const e1 = pts[1].p.map((v, k) => v - pts[0].p[k]), e2 = pts[2].p.map((v, k) => v - pts[1].p[k]);
    const n = norm([e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]);
    const light = Math.min(1, 0.4 + 0.6 * (Math.max(0, n[0] * LIGHT0[0] + n[1] * LIGHT0[1] + n[2] * LIGHT0[2]) + Math.max(0, n[0] * LIGHT1[0] + n[1] * LIGHT1[1] + n[2] * LIGHT1[2])));
    const tex = texture(resolve(g.json, q.tex));
    canvas.polygon(proj, pts, light, tex, tex ? null : [200, 0, 200]);
  }
}
// The stage's own flash geometry (core.flashQuads), drawn additively over the gun like the client does.
function flashQuads(f) { return core.flashQuads(f); }
function additive(canvas, proj, hand, quads) {
  for (const q of quads) {
    const sv = q.map(v => { const p = M.apply(hand, [v[0], v[1], v[2]]), c = M.apply(proj, p), w = c[3]; return { x: (c[0] / w * 0.5 + 0.5) * canvas.W, y: (0.5 - c[1] / w * 0.5) * canvas.H, z: c[2] / w, col: [v[3], v[4], v[5], v[6]], behind: w <= 0.05 }; });
    if (sv.some(v => v.behind)) continue;
    tri(canvas, sv[0], sv[1], sv[2]); tri(canvas, sv[0], sv[2], sv[3]);
  }
}
function tri(c, a, b, d) {
  const minX = Math.max(0, Math.floor(Math.min(a.x, b.x, d.x))), maxX = Math.min(c.W - 1, Math.ceil(Math.max(a.x, b.x, d.x)));
  const minY = Math.max(0, Math.floor(Math.min(a.y, b.y, d.y))), maxY = Math.min(c.H - 1, Math.ceil(Math.max(a.y, b.y, d.y)));
  const area = (b.x - a.x) * (d.y - a.y) - (b.y - a.y) * (d.x - a.x);
  if (Math.abs(area) < 1e-9) return;
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
    const px = x + 0.5, py = y + 0.5;
    const w0 = ((b.x - px) * (d.y - py) - (b.y - py) * (d.x - px)) / area, w1 = ((d.x - px) * (a.y - py) - (d.y - py) * (a.x - px)) / area, w2 = 1 - w0 - w1;
    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
    const o = y * c.W + x;
    // drawn over the gun like the client does (no depth test, no depth write)
    const al = w0 * a.col[3] + w1 * b.col[3] + w2 * d.col[3];
    for (let k = 0; k < 3; k++) c.rgb[o * 3 + k] = Math.min(255, c.rgb[o * 3 + k] + 255 * al * (w0 * a.col[k] + w1 * b.col[k] + w2 * d.col[k]));
  }
}
// Dungeon armoury guns and casters are flat vanilla items (item/generated, the same first-person transform as tools).
const SPRITES = { BAPTIST_NEEDLER: 'iron_horse_armor', BELFRY_REPEATER: 'iron_horse_armor', CARRION_SCATTERGUN: 'iron_horse_armor', OPALINE_RAIL: 'diamond_horse_armor',
  FOUNDRY_CANNON: 'gold_horse_armor', MENAGERIE_DARTER: 'iron_horse_armor', SCRIPTORIUM_SIPHON: 'iron_horse_armor', VESPER_DRILL: 'gold_horse_armor',
  PAUPERS_RICOCHET: 'iron_horse_armor', ASTRAL_ORRERY: 'diamond_horse_armor', LABYRINTH_DOUBLE: 'gold_horse_armor', LAST_ABSOLUTION: 'diamond_horse_armor',
  CARRION_CENSER: 'blaze_rod', OPAL_PRISM: 'prismarine_shard', SUNLESS_BRAND: 'blaze_rod', PALE_LANTERN: 'ghast_tear' };
const spriteMeshes = {};
function spriteMesh(id) { return spriteMeshes[id] || (spriteMeshes[id] = R.itemMesh(R.decodePNG(fs.readFileSync(path.join(ASSETS, 'items', SPRITES[id] + '.png'))))); }
function frame(id, pose, opts = {}) {
  const W = opts.width || 320, H = opts.height || 180, canvas = new R.Canvas(W, H);
  R.drawWorld(canvas, W / H);
  const proj = M.perspective(70, W / H, 0.05, 50), side = opts.left ? -1 : 1, hand = handMatrix(side, pose);
  if (SPRITES[id]) R.drawItem(canvas, proj, spriteMesh(id), M.mul(hand, R.displayMatrix(R.HANDHELD, side < 0)));
  else drawGun(canvas, proj, id, hand, side);
  if (pose && pose.flash) additive(canvas, proj, hand, flashQuads(pose.flash));
  R.crosshair(canvas);
  return canvas;
}

// ------------------------------------------------------------------ driving the stage's core with synthetic item data
let stackSeq = 0;
function apoc(id, rounds, serial) { return { item: 293, damage: 0, apoc: { id, serial: serial || 'S-' + id, rounds, speed: 0 }, dungeon: null }; }
function dungeon(id, rounds, readyAt) { return { item: 417, damage: 0, apoc: null, dungeon: { kind: 'weapon', id, uuid: 'U-' + id, rounds, readyAt: readyAt || 0, reloadAt: 0 } }; }
function sim(id, script, total, step, left) {
  const make = SPRITES[id] ? (r, c) => dungeon(id, r, c && c.readyAt) : (r) => apoc(id, r);
  // script: list of [tick, change] where change = {rounds} | {reloadSound: true} | {overlay: '...'}
  core.clear();
  let rounds = 20, frames = [], times = [];
  const stack = () => ({ id: ++stackSeq });
  let main = stack();
  core.tick({ main, mainDesc: make(rounds), off: null, offDesc: null, slot: 0, firstPerson: true, vfx: 0 });
  const base = core.ticks();
  for (let t = 0, si = 0; t <= total + 1e-9; t += step) {
    while (core.ticks() - base < Math.floor(t + 1e-9)) {
      const now = core.ticks() - base + 1;
      const input = { main, mainDesc: undefined, off: null, offDesc: undefined, slot: 0, firstPerson: true, vfx: 0, click: false, overlay: null, reloadSound: false };
      while (si < script.length && script[si][0] <= now) {
        const c = script[si++][1];
        if (c.rounds !== undefined || c.readyAt !== undefined) { if (c.rounds !== undefined) rounds = c.rounds; main = stack(); input.main = main; input.mainDesc = make(rounds, c); }
        if (c.reloadSound) input.reloadSound = true;
        if (c.overlay) input.overlay = c.overlay;
      }
      core.tick(input);
    }
    frames.push(core.pose(0, base + t, !!left)); times.push(t);
  }
  return { frames, times };
}
const SHOW = { pistol: 'sepulcher', mpistol: 'cinder', revolver: 'turnstile', magnum: 'magnum44', smg: 'whisper', carbine: 'tunnelrat', rifle: 'rifle',
  dmr: 'adjudicator', burst: 'tempest', bolt: 'gallows', antimat: 'watchtower', pump: 'lockjaw', breakgun: 'bellringer', drum: 'ashfall',
  heavy: 'pallbearer', rotary: 'ironpsalm', rail: 'railgun', plasma: 'witchlight', pulse: 'deadfrequency', portal: 'portal_gun' };

function main() {
  extract();
  fs.mkdirSync(OUT, { recursive: true });
  const want = process.argv.slice(3);
  const run = s => !want.length || want.includes(s);
  const written = [];
  if (run('shots')) for (const [cls, id] of Object.entries(SHOW)) {
    if (cls === 'portal') continue;
    const times = [0, 0.25, 0.5, 1, 1.5, 2.5, 4, 6, 9, 13, 18, 24];
    const { frames } = sim(id, [[1, { rounds: 19 }]], 26, 0.25);
    const pick = times.map(t => frames[Math.round((1 + t) / 0.25)] || null);
    const canv = pick.map(p => frame(id, p, { width: 320, height: 180 }));
    const file = path.join(OUT, 'shot-' + cls + '-' + id + '.png');
    R.save(R.sheet(canv, 6, times.map(t => t / 24)), file); written.push(file);
  }
  if (run('auto')) for (const [id, every, n] of [['whisper', 5, 6], ['ironpsalm', 8, 6], ['pallbearer', 14, 5], ['rifle', 6, 6], ['cinder', 6, 6]]) {
    const script = []; for (let i = 0; i < n; i++) script.push([1 + i * every, { rounds: 19 - i }]);
    const total = 1 + n * every + 16, { frames, times } = sim(id, script, total, 1);
    const canv = frames.map(p => frame(id, p, { width: 200, height: 112 }));
    const file = path.join(OUT, 'auto-' + id + '.png');
    R.save(R.sheet(canv, 10, times.map(t => t / total)), file); written.push(file);
  }
  if (run('reload')) for (const id of ['rifle', 'lockjaw', 'bellringer', 'magnum44', 'gallows', 'railgun', 'sepulcher', 'pallbearer']) {
    const dur = Math.round(core.APOC[id][1] / 50);
    const script = [[1, { rounds: 0 }], [2, { reloadSound: true }], [2 + dur, { rounds: 8 }]];
    const total = dur + 16, step = total / 29, { frames, times } = sim(id, script, total, step);
    const canv = frames.map(p => frame(id, p, { width: 200, height: 112 }));
    const file = path.join(OUT, 'reload-' + id + '.png');
    R.save(R.sheet(canv, 10, times.map(t => t / total)), file); written.push(file);
  }
  if (run('portal')) {
    const script = [[1, { overlay: 'Blue portal set.  Fire again to open the other.  Next: orange.' }], [12, { overlay: 'Orange portal set.  Portals linked.  Next: blue.' }]];
    const { frames, times } = sim('portal_gun', script, 24, 1);
    const canv = frames.map(p => frame('portal_gun', p, { width: 200, height: 112 }));
    const file = path.join(OUT, 'portal.png');
    R.save(R.sheet(canv, 10, times.map(t => t / 24)), file); written.push(file);
  }
  if (run('trace')) for (const [id, every, n] of [['whisper', 5, 10], ['ironpsalm', 8, 10], ['pallbearer', 14, 8], ['rifle', 6, 10], ['magnum44', 18, 4],
    ['lockjaw', 23, 3], ['gallows', 30, 2], ['railgun', 40, 2], ['tempest', 11, 3]]) {
    // the pose channels over time: climb (red, degrees), back toward the eye (yellow, x100), roll (blue), yaw (green), flash (white)
    const script = []; for (let i = 0; i < n; i++) script.push([1 + i * every, { rounds: 19 - i }]);
    const total = 1 + n * every + 30, { frames, times } = sim(id, script, total, 0.1);
    const W = 720, H = 220, c = new R.Canvas(W, H), mid = H * 0.62, sx = (W - 20) / total, sy = H * 0.5 / 30;
    c.rgb.fill(22);
    c.line(10, mid, W - 10, mid, [90, 90, 90]);
    for (const [t] of script) c.line(10 + t * sx, 8, 10 + t * sx, H - 8, [60, 60, 70]);
    const chan = [['climb', [235, 70, 60], 1], ['z', [235, 210, 60], 100], ['roll', [80, 140, 255], 1], ['yaw', [90, 210, 110], 1]];
    for (const [key, col, k] of chan) {
      let prev = null;
      frames.forEach((p, i) => { const y = mid - (p ? p[key] * k : 0) * sy, x = 10 + times[i] * sx; if (prev) c.line(prev[0], prev[1], x, y, col); prev = [x, y]; });
    }
    frames.forEach((p, i) => { if (p && p.flash) c.dot(10 + times[i] * sx, 12, 1, [255, 255, 255]); });
    const file = path.join(OUT, 'trace-' + id + '.png'); R.save(c, file); written.push(file);
  }
  if (run('mosaic')) {
    // one row per gun at a glance: rest, the first frame (flash), the kick at its peak, recovering
    const ids = ['sepulcher', 'magnum44', 'rifle', 'lockjaw', 'gallows', 'watchtower', 'pallbearer', 'railgun', 'witchlight', 'bellringer'], out = [];
    for (const id of ids) { const { frames } = sim(id, [[1, { rounds: 19 }]], 6, 0.25); for (const i of [0, 4, 7, 20]) out.push(frame(id, frames[i], { width: 400, height: 225 })); }
    const file = path.join(OUT, 'mosaic.png'); R.save(R.sheet(out, 4), file); written.push(file);
  }
  if (run('sprites')) for (const [id, script, total] of [['BELFRY_REPEATER', [[1, { rounds: 17 }]], 34], ['FOUNDRY_CANNON', [[1, { rounds: 19 }]], 20],
    ['OPAL_PRISM', [[1, { readyAt: 5000 }]], 20], ['SUNLESS_BRAND', [[1, { readyAt: 5000 }]], 20]]) {
    const { frames, times } = sim(id, script, total, total / 19);
    const canv = frames.map(p => frame(id, p, { width: 200, height: 112 }));
    const file = path.join(OUT, 'sprite-' + id + '.png');
    R.save(R.sheet(canv, 10, times.map(t => t / total)), file); written.push(file);
  }
  if (run('paths')) for (const id of ['sepulcher', 'magnum44', 'rifle', 'gallows', 'lockjaw', 'railgun', 'whisper']) {
    // the muzzle on screen at 0.25-tick steps (yellow: kick, red: return, blue: later)
    const W = 480, H = 270, c = frame(id, null, { width: W, height: H }), proj = M.perspective(70, W / H, 0.05, 50);
    const script = id === 'whisper' ? [1, 6, 11, 16, 21].map((t, i) => [t, { rounds: 19 - i }]) : [[1, { rounds: 19 }]];
    const { frames, times } = sim(id, script, 40, 0.25);
    const geo = JasprGunFXData[id].r;
    frames.forEach((p, i) => {
      const hm = handMatrix(1, p), q = M.apply(proj, M.apply(hm, geo.muzzle)), w = q[3];
      const x = (q[0] / w * 0.5 + 0.5) * W, y = (0.5 - q[1] / w * 0.5) * H, t = times[i];
      c.dot(x, y, 1, t < 2 ? [255, 220, 0] : t < 8 ? [230, 40, 40] : [60, 110, 255]);
    });
    const file = path.join(OUT, 'path-' + id + '.png'); R.save(c, file); written.push(file);
  }
  console.log(written.join('\n'));
}
if (require.main === module) main();
module.exports = { frame, sim, handMatrix, SHOW };
