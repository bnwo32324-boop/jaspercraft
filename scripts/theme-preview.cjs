'use strict';
// Offline preview of the widened survival inventory with the JasperCraft look (or the vanilla textures, for comparison): the window
// texture, the wide module's own pocket plan, a few item icons, the real Field Journal plan drawn with the game's own font sheet. No
// browser. Reads the EPK (default: the live one), writes one PNG.
//   node scripts/theme-preview.cjs <out.png> [jasper|vanilla] [tab 0..2]       (env PREVIEW_EPK, PREVIEW_SCALE, PREVIEW_PAYLOAD)
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const png = require('./png-codec.cjs');
const theme = require('./jasper-theme.cjs');
const {decode} = require('./merge-apocalypse-assets.cjs');
const ROOT = path.join(__dirname, '..');
const EPK = process.env.PREVIEW_EPK || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/assets.epk';
const [out, paletteName = 'jasper', tabArg = '0'] = process.argv.slice(2).filter(a => !a.startsWith('--'));
if (!out) throw new Error('usage: node scripts/theme-preview.cjs <out.png> [jasper|vanilla] [tab]');
const SCALE = Number(process.env.PREVIEW_SCALE || 4);

const parsed = decode(fs.readFileSync(EPK)), byName = new Map(parsed.entries.map(e => [e.name, e]));
const asset = n => { const e = byName.get('assets/minecraft/textures/' + n); if (!e) throw new Error('missing ' + n); return png.decode(e.value); };
const font = asset('font/ascii.png');
const advances = (() => {
  const w = [];
  for (let c = 0; c < 256; c++) {
    const gx = (c % 16) * 8, gy = (c >> 4) * 8; let k = 7;
    for (; k >= 0; k--) { let any = false; for (let y = 0; y < 8; y++) if (font.rgba[((gy + y) * font.w + gx + k) * 4 + 3] > 0) any = true; if (any) break; }
    w[c] = c === 32 ? 4 : k + 2;
  }
  return w;
})();
const stringWidth = s => [...s].reduce((n, ch) => n + advances[ch.charCodeAt(0) & 255], 0);

// ---- canvas (window-relative GUI pixels, a small margin around)
const W = 266, H = 166, PADX = 6, PADY = 6, CW = W + PADX * 2, CH = H + PADY * 2;
const canvas = Buffer.alloc(CW * CH * 4);
for (let i = 0; i < CW * CH; i++) { canvas[i * 4] = 0x16; canvas[i * 4 + 1] = 0x16; canvas[i * 4 + 2] = 0x1a; canvas[i * 4 + 3] = 255; }
function plot(x, y, argb) {
  x += PADX; y += PADY;
  if (x < 0 || y < 0 || x >= CW || y >= CH) return;
  const a = (argb >>> 24) / 255, i = (y * CW + x) * 4;
  canvas[i] = Math.round(canvas[i] * (1 - a) + ((argb >>> 16) & 255) * a); canvas[i + 1] = Math.round(canvas[i + 1] * (1 - a) + ((argb >>> 8) & 255) * a);
  canvas[i + 2] = Math.round(canvas[i + 2] * (1 - a) + (argb & 255) * a);
}
const rect = (x1, y1, x2, y2, c) => { for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) plot(x, y, c >>> 0); };
function glyphs(text, x, y, color) {
  for (const ch of text) {
    const code = ch.charCodeAt(0) & 255, gx = (code % 16) * 8, gy = (code >> 4) * 8;
    for (let yy = 0; yy < 8; yy++) for (let xx = 0; xx < 8; xx++) if (font.rgba[((gy + yy) * font.w + gx + xx) * 4 + 3] > 0) plot(x + xx, y + yy, color >>> 0);
    x += advances[code];
  }
}
function drawText(text, x, y, color, shadow) {
  if (shadow) glyphs(text, x + 1, y + 1, (0xFF000000 | ((color & 0xFCFCFC) >> 2)) >>> 0);
  glyphs(text, x, y, (color | 0xFF000000) >>> 0);
}
function blit(img, sx, sy, sw, sh, dx, dy) {
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    const i = ((sy + y) * img.w + sx + x) * 4, a = img.rgba[i + 3];
    if (a) plot(dx + x, dy + y, ((a << 24) | (img.rgba[i] << 16) | (img.rgba[i + 1] << 8) | img.rgba[i + 2]) >>> 0);
  }
}

// ---- the window texture (from the archive: vanilla or already themed; recoloured here when asked for jasper)
const base = byName.get('assets/minecraft/textures/gui/container/inventory.png').value;
const tex = paletteName === 'vanilla' ? png.decode(base) : theme.retheme(png.decode(base), 'container/inventory.png', paletteName);
blit(tex, 0, 0, 176, 166, 0, 0);

// ---- the wide module: its real pocket plan
const wctx = {$rt_globals: {location: {pathname: '/test/'}, console: {warn() {}}}, $rt_str: s => s, HEH: null};
vm.createContext(wctx);
vm.runInContext('function Biv(){} function YD(){} function ID(){} function ABp(){} function Other(){}\n' + fs.readFileSync(path.join(ROOT, 'client-mods', 'wide-inventory-teavm.js'), 'utf8'), wctx);
const WIDE = wctx.JasprWide;
WIDE.answered(null);
const inv = new wctx.Biv(), other = new wctx.Other(), slots = [];
const add = (BX, bQx, Lr, Fg) => slots.push({BX, bQx, Lr, Fg, pO: slots.length});
for (let i = 0; i < 5; i++) add(other, i, 8 + (i % 9) * 18, 18 + (i / 9 | 0) * 18);
for (let r = 0; r < 3; r++) for (let c = 0; c < 9; c++) add(inv, 9 + r * 9 + c, 8 + c * 18, [84, 102, 120][r]);
for (let c = 0; c < 9; c++) add(inv, c, 8 + c * 18, 142);
const container = {cn: {get g() { return slots.length; }, qN: {data: slots}}};
const plan = WIDE.adoptPlan(container);
for (const i of plan.add) slots.push({BX: plan.inv, bQx: i, Lr: 0, Fg: 0, pO: slots.length, $jw: 1});
WIDE.adopted(container, plan);
const gui = Object.assign(new wctx.ID(), {h2: container, gv: 176, gx: 166, is: 0, l7: 0, q: 266, L: 300, $jwCut: 0});
WIDE.layout(gui);
const pocket = WIDE.pocketPlan(gui);
for (const r of pocket.rects) rect(r[0], r[1], r[2], r[3], r[4]);

// ---- items on some slots (2D item icons stand in for the game's)
const icons = ['diamond_sword', 'coal', 'bread', 'iron_ingot', 'apple_golden', 'ender_pearl', 'gold_ingot', 'redstone_dust', 'diamond_pickaxe', 'bucket_water',
  'emerald', 'bone', 'diamond', 'book_normal', 'flint', 'arrow', 'bow_standby'].map(i => { try { return asset('items/' + i + '.png'); } catch (e) { return null; } }).filter(Boolean);
let n = 0;
for (const s of slots) {
  if (s.Lr < 0 || s.Fg < 0 || s.Lr > 400 || (s.BX === other && s.bQx >= 1)) continue;
  if ((n++ % 3) === 0 || s.Fg === 142) blit(icons[n % icons.length], 0, 0, 16, 16, s.Lr, s.Fg);
}
const label = paletteName === 'vanilla' ? 0x404040 : parseInt(theme.PALETTES[paletteName].label.slice(1), 16);
drawText('Crafting', 97, 8, label, false);

// ---- the journal: the real module plan
const store = new Map();
const jenv = {
  $rt_globals: {localStorage: {getItem: k => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v))}, performance: {now: () => 1000}, console: {warn() {}}, location: {pathname: '/x'}},
  $rt_str: s => ({java: String(s)}), $rt_ustr: o => o.java, CA: (f, j) => stringWidth(j.java), ID: wctx.ID, A2Z: class A2Z { },
  JasprWide: {pocketWidth: () => 90}
};
const J = new Function(...Object.keys(jenv), fs.readFileSync(path.join(ROOT, 'client-mods', 'journal-teavm.js'), 'latin1') + '; return JasprJournal;')(...Object.values(jenv));
const screen = Object.assign(new jenv.ID(), {is: 0, l7: 0, gv: 176, gx: 166, J: {}, h2: new jenv.A2Z(), j: {v: {}}});
const payload = JSON.parse(process.env.PREVIEW_PAYLOAD || JSON.stringify({v: 1, d: 12, ph: 'Dusk', bm: 2, iv: [1, 15], dz: [1, ''], lv: 37, xp: 22, rp: [14, 9, 45, 3, 11],
  gw: [['Cat Burglar', 'Move silently'], ['Tank Treads', '+10% armour'], ['Night Eye', 'See in the dark']], fx: [['Water Breathing', 0], ['Haste II', 83]]}));
J.setTab(Number(tabArg) | 0);
J.receive(JSON.stringify(payload));
const jp = J.plan(screen);
if (jp) {
  for (const r of jp.rects) rect(r[0], r[1], r[2], r[3], r[4]);
  for (const t of jp.texts) drawText(t.s.java, t.x, t.y, t.c, t.shadow);
}

// ---- output (nearest-neighbour upscale)
const big = Buffer.alloc(CW * SCALE * CH * SCALE * 4);
for (let y = 0; y < CH * SCALE; y++) for (let x = 0; x < CW * SCALE; x++) canvas.copy(big, (y * CW * SCALE + x) * 4, ((y / SCALE | 0) * CW + (x / SCALE | 0)) * 4, ((y / SCALE | 0) * CW + (x / SCALE | 0)) * 4 + 4);
fs.mkdirSync(path.dirname(path.resolve(out)), {recursive: true});
fs.writeFileSync(out, png.encode({w: CW * SCALE, h: CH * SCALE, rgba: big}));
console.log('wrote', out, CW * SCALE + 'x' + CH * SCALE, 'journal', jp ? 'drawn' : 'NOT drawn', 'pocket rects', pocket.rects.length);
