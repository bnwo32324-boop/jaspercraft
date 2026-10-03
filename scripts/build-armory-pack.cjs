'use strict';
// Realm armoury assets (JasprGear 5.0.0) for the browser client's resource archive, applied on top of whatever the archive
// already holds (run it last: after scripts/build-gear-pack.cjs and scripts/merge-apocalypse-assets.cjs, whose diamond
// item models it extends):
//  - 54 item icons, textures/items/jaspr_armory_<set>_<piece>.png, and 12 worn-armour layers,
//    textures/models/armor/jaspr_<set>_layer_<1|2>.png, drawn from the archive's own vanilla diamond textures: every cyan
//    "diamond" pixel is re-coloured by its brightness along the set's five-stop ramp (outlines, sticks and handles keep
//    their colours, white highlights take the ramp's lightest tone), and the worn layers get each set's small accent
//    (embers, pearls, rivets, wallpaper stripes, stars, facets);
//  - one 2D model per piece (models/item/jaspr_armory_<set>_<piece>.json);
//  - the nine diamond base items' damage overrides that pick them: every armoury piece is an unbreakable diamond item
//    whose damage value is its set's model number (100, 102 ... 110: ArmorySet.model()), with the value just above each a
//    fallback to the vanilla look, beside the expedition gear's (10-41) and the guns' and blades' (1140+). Earlier
//    armoury entries are removed first, so the step is idempotent and safe to re-run after anything else rebuilt a base
//    model. A base model without a vanilla fallback of its own gets jaspr_vanilla_<base>.json.
// Every texture, parent and override a written model names must exist in the result, or the build stops.
//   node scripts/build-armory-pack.cjs <input assets.epk> [out dir]   -> <out>/assets.epk, <out>/preview.png
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const {decode, fileEntry} = require('./merge-apocalypse-assets.cjs');
const png = require('./png-codec.cjs');

const SETS = ['emerald', 'blazeforged', 'abyssal', 'titan', 'liminal', 'void'];
/** piece, base item, vanilla max durability, held like a tool/weapon */
const PIECES = [
  ['helmet', 'diamond_helmet', 363, false], ['chestplate', 'diamond_chestplate', 528, false],
  ['leggings', 'diamond_leggings', 495, false], ['boots', 'diamond_boots', 429, false],
  ['sword', 'diamond_sword', 1561, true], ['axe', 'diamond_axe', 1561, true], ['pickaxe', 'diamond_pickaxe', 1561, true],
  ['shovel', 'diamond_shovel', 1561, true], ['hoe', 'diamond_hoe', 1561, true]];
const model = set => 100 + 2 * SETS.indexOf(set);
const name = (set, piece) => 'jaspr_armory_' + set + '_' + piece;

/** Five stops, dark to light, per set. */
const RAMPS = {
  emerald: ['04301a', '0b6b34', '14a84f', '2ee070', '9cf7bc'],
  blazeforged: ['3a0a04', '8a1c08', 'd4400e', 'ff8a1e', 'ffd45a'],
  abyssal: ['04121f', '0b2e4a', '145e78', '2a9aa8', '8be0e0'],
  titan: ['241005', '5e2c0e', '9c5521', 'd08a3c', 'f7cf8a'],
  liminal: ['2e3016', '5f6428', 'a3aa4c', 'd5dc85', 'f8fbdc'],
  void: ['120420', '3a0f5c', '6a2aa0', 'a65ce0', 'e8c2ff']};
/** Accent colour per set for the worn layers. */
const ACCENT = {emerald: 'eafff0', blazeforged: 'ffe066', abyssal: 'd8fbff', titan: 'ffe6a0', liminal: 'eef2b8', void: 'ff7af5'};

const hex = h => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
const luma = (r, g, b) => 0.299 * r + 0.587 * g + 0.114 * b;

/** A diamond-cyan pixel: green and blue well above red (vanilla's 0e3f36 .. 3cffee); white is a highlight. */
function kind(r, g, b) {
  if (r > 200 && g > 230 && b > 225) return 'white';
  if (g > r * 1.6 + 8 && b > r * 1.4 + 8) return 'diamond';
  return 'keep';
}

function ramp(set, t) {
  const stops = RAMPS[set].map(hex);
  const x = Math.max(0, Math.min(1, t)) * (stops.length - 1), i = Math.min(stops.length - 2, Math.floor(x)), f = x - i;
  return stops[i].map((c, k) => Math.round(c + (stops[i + 1][k] - c) * f));
}

/** Brightness of the diamond palette mapped to 0..1 (darkest vanilla diamond pixel 0, brightest 1). */
const DARK = luma(0x08, 0x25, 0x20), LIGHT = luma(0x3c, 0xff, 0xee);
const level = (r, g, b) => (luma(r, g, b) - DARK) / (LIGHT - DARK);

function hash(x, y, salt) {
  let h = (x * 374761393 + y * 668265263 + salt * 2246822519) >>> 0;
  h = (h ^ (h >>> 13)) * 1274126177 >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

/** The set's worn-layer accent at a pixel, or null (only ever on recoloured pixels). */
function accent(set, x, y, t) {
  const salt = SETS.indexOf(set) + 1;
  switch (set) {
    case 'blazeforged': return t < 0.55 && hash(x, y, salt) < 0.07 ? ACCENT.blazeforged : null;   // embers in the dark metal
    case 'abyssal': return t > 0.35 && hash(x, y, salt) < 0.035 ? ACCENT.abyssal : null;         // pearls
    case 'titan': return x % 4 === 1 && y % 4 === 1 ? ACCENT.titan : null;                       // rivets
    case 'liminal': return y % 4 === 0 && t > 0.25 ? ACCENT.liminal : null;                      // wallpaper stripes
    case 'void': return hash(x, y, salt) < 0.03 ? ACCENT.void : null;                            // stars
    case 'emerald': return (x + y) % 6 === 0 && t > 0.5 ? ACCENT.emerald : null;                 // facets
    default: return null;
  }
}

function recolor(img, set, worn) {
  const out = {w: img.w, h: img.h, rgba: Buffer.from(img.rgba)};
  const light = ramp(set, 1);
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
    const i = (y * img.w + x) * 4;
    if (!out.rgba[i + 3]) continue;
    const r = out.rgba[i], g = out.rgba[i + 1], b = out.rgba[i + 2], k = kind(r, g, b);
    if (k === 'keep') continue;
    let c;
    if (k === 'white') c = light.map(v => Math.round(v * 0.4 + 255 * 0.6));
    else {
      const t = level(r, g, b);
      const a = worn ? accent(set, x, y, t) : null;
      c = a ? hex(a) : ramp(set, t);
    }
    out.rgba[i] = c[0]; out.rgba[i + 1] = c[1]; out.rgba[i + 2] = c[2];
  }
  return out;
}

const json = value => Buffer.from(JSON.stringify(value, null, 2) + '\n');

/** {archive path: bytes} for every armoury texture and model, and the base models extended for them. */
function assets(byName) {
  const prefix = byName.has('assets/minecraft/textures/items/diamond_helmet.png') ? 'assets/' : '';
  const at = p => prefix + 'minecraft/' + p;
  const read = p => {
    const v = byName.get(at('textures/' + p + '.png'));
    if (!v) throw new Error('vanilla texture missing from the archive: ' + p);
    return png.decode(v);
  };
  const out = {};
  for (const [piece, base, max, handheld] of PIECES) {
    const source = read('items/' + base);
    for (const set of SETS) {
      out[at('textures/items/' + name(set, piece) + '.png')] = png.encode(recolor(source, set, false));
      out[at('models/item/' + name(set, piece) + '.json')] = json({parent: handheld ? 'item/handheld' : 'item/generated',
        textures: {layer0: 'items/' + name(set, piece)}});
    }
    const baseFile = at('models/item/' + base + '.json');
    if (!byName.has(baseFile)) throw new Error('base item model missing from the archive: ' + base);
    const current = JSON.parse(byName.get(baseFile).toString('utf8'));
    const overrides = current.overrides || [];
    const damaged = overrides.find(o => o.predicate && o.predicate.damaged === 1);
    const fallback = damaged ? damaged.model : 'item/jaspr_vanilla_' + base;
    if (fallback === 'item/jaspr_vanilla_' + base)   // ours: (re)written so a re-run produces the same files
      out[at('models/item/jaspr_vanilla_' + base + '.json')] = json({parent: current.parent, textures: current.textures});
    const mine = new Set(SETS.map(s => 'item/' + name(s, piece)));
    const keep = [];
    for (let i = 0; i < overrides.length; i++) {
      const o = overrides[i];
      if (o.predicate && o.predicate.damaged === 1) continue;                                  // re-added last
      if (mine.has(o.model)) { i++; continue; }                                               // ours and its fallback
      keep.push(o);
    }
    for (const set of SETS) {
      keep.push({predicate: {damaged: 0, damage: model(set) / max}, model: 'item/' + name(set, piece)});
      keep.push({predicate: {damaged: 0, damage: (model(set) + 1) / max}, model: fallback});
    }
    keep.sort((a, b) => a.predicate.damage - b.predicate.damage);
    keep.push({predicate: {damaged: 1}, model: fallback});
    current.overrides = keep;
    out[baseFile] = json(current);
  }
  for (const layer of [1, 2]) {
    const source = read('models/armor/diamond_layer_' + layer);
    for (const set of SETS) out[at('textures/models/armor/jaspr_' + set + '_layer_' + layer + '.png')] = png.encode(recolor(source, set, true));
  }
  return {files: out, prefix};
}

/** A preview sheet: per set a row of the nine icons (x4) and the two worn layers (x2). */
function preview(files, prefix) {
  const W = 9 * 72 + 2 * 136 + 16, H = SETS.length * 72 + 8;
  const sheet = {w: W, h: H, rgba: Buffer.alloc(W * H * 4)};
  for (let i = 0; i < W * H; i++) { sheet.rgba[i * 4] = 46; sheet.rgba[i * 4 + 1] = 46; sheet.rgba[i * 4 + 2] = 52; sheet.rgba[i * 4 + 3] = 255; }
  const blit = (img, ox, oy, s) => {
    for (let y = 0; y < img.h * s; y++) for (let x = 0; x < img.w * s; x++) {
      if (ox + x >= W || oy + y >= H) continue;
      const si = (Math.floor(y / s) * img.w + Math.floor(x / s)) * 4, a = img.rgba[si + 3] / 255, di = ((oy + y) * W + ox + x) * 4;
      for (let k = 0; k < 3; k++) sheet.rgba[di + k] = Math.round(img.rgba[si + k] * a + sheet.rgba[di + k] * (1 - a));
    }
  };
  SETS.forEach((set, row) => {
    PIECES.forEach(([piece], col) => blit(png.decode(files[prefix + 'minecraft/textures/items/' + name(set, piece) + '.png']), 4 + col * 72, 4 + row * 72, 4));
    [1, 2].forEach((layer, k) => blit(png.decode(files[prefix + 'minecraft/textures/models/armor/jaspr_' + set + '_layer_' + layer + '.png']),
      9 * 72 + 8 + k * 136, 4 + row * 72, 2));
  });
  return png.encode(sheet);
}

/** Input archive plus the armoury assets (entries of the same names are replaced; every other entry is untouched). */
function build(input) {
  const parsed = decode(input);
  const byName = new Map(parsed.entries.map(e => [e.name, e.value]));
  const {files, prefix} = assets(byName);
  const names = new Set(Object.keys(files));
  const entries = parsed.entries.filter(e => !names.has(e.name)).map(e => e.raw);
  for (const [n, value] of Object.entries(files)) entries.push(fileEntry(n, value));
  const header = Buffer.from(parsed.header);
  header.writeUInt32BE(entries.length, parsed.countOffset);
  const payload = Buffer.concat([...entries, Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);
  const check = new Map(decode(output).entries.map(e => [e.name, e.value]));
  for (const [n, value] of Object.entries(files)) if (Buffer.compare(check.get(n), value)) throw new Error('round trip ' + n);
  for (const e of parsed.entries) if (!names.has(e.name) && Buffer.compare(check.get(e.name), e.value)) throw new Error('changed ' + e.name);
  // every reference a written model makes resolves in the result
  const res = (ref, type, ext) => { const [ns, local] = ref.includes(':') ? ref.split(':') : ['minecraft', ref]; return prefix + ns + '/' + type + '/' + local + ext; };
  for (const n of names) {
    if (!n.endsWith('.json')) continue;
    const m = JSON.parse(files[n].toString('utf8'));
    for (const t of Object.values(m.textures || {})) if (!t.startsWith('#') && !check.has(res(t, 'textures', '.png'))) throw new Error(n + ': missing texture ' + t);
    if (m.parent && !m.parent.startsWith('builtin/') && !check.has(res(m.parent, 'models', '.json'))) throw new Error(n + ': missing parent ' + m.parent);
    for (const o of m.overrides || []) if (!check.has(res(o.model, 'models', '.json'))) throw new Error(n + ': missing override ' + o.model);
  }
  return {output, files, preview: preview(files, prefix)};
}

if (require.main === module) {
  const input = process.argv[2];
  if (!input) { console.error('usage: node scripts/build-armory-pack.cjs <input assets.epk> [out dir]'); process.exit(2); }
  const out = process.argv[3] || path.join(__dirname, '..', 'candidate', 'armory');
  fs.mkdirSync(out, {recursive: true});
  const result = build(fs.readFileSync(input));
  fs.writeFileSync(path.join(out, 'assets.epk'), result.output);
  fs.writeFileSync(path.join(out, 'preview.png'), result.preview);
  console.log(JSON.stringify({files: Object.keys(result.files).length, bytes: result.output.length,
    sha256: crypto.createHash('sha256').update(result.output).digest('hex')}));
}
module.exports = {build, assets, recolor, RAMPS, SETS, PIECES, model, name, kind};
