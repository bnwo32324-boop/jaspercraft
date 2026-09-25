'use strict';
// Survivor Gear assets: original 16x16 pixel art for the 15 trinkets and 6 empty-slot icons,
// item models, and stone_hoe damage-band overrides (the same unbreakable-carrier technique as
// the apocalypse arsenal). Writes candidate/gear/pack/ and candidate/gear/assets.epk only.
// Every unrelated EPK entry must stay byte-identical; the merge is idempotent.
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib'), crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {decode} = require('./merge-apocalypse-assets.cjs');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'candidate', 'gear');
const catalog = JSON.parse(fs.readFileSync(path.join(out, 'gear-catalog.json'), 'utf8'));
const MAX_DAMAGE = 131; // stone hoe

const PALETTE = {
  '0': [20, 20, 20], '1': [43, 43, 43], '2': [77, 77, 77], '3': [115, 115, 115], '4': [158, 158, 158], '5': [207, 207, 207],
  b: [46, 28, 16], B: [79, 49, 28], n: [117, 73, 42], N: [156, 106, 63], r: [92, 14, 14], R: [163, 32, 28], o: [212, 102, 28],
  y: [184, 144, 30], Y: [227, 192, 67], L: [255, 242, 154], u: [23, 49, 80], U: [47, 95, 148], c: [42, 166, 184], C: [159, 244, 247],
  v: [39, 74, 26], V: [76, 138, 42], g: [155, 224, 74], G: [217, 255, 138], p: [58, 29, 79], P: [122, 63, 168], m: [201, 133, 240],
  s: [94, 84, 66], S: [138, 125, 98], k: [181, 167, 132], w: [217, 200, 168], f: [74, 50, 32], F: [122, 86, 52], e: [165, 124, 79],
  x: [0, 0, 0], i: [111, 168, 220], I: [184, 220, 245], h: [90, 106, 120], H: [143, 163, 181], j: [201, 214, 224],
  a: [55, 55, 55, 190], A: [55, 55, 55, 90],
  q: [240, 140, 200], Q: [168, 66, 138], z: [236, 240, 244], Z: [168, 178, 190]
};

const ART = {
  capacitor_belt: [
    '................', '................', '...........C....', '..........CcC...', '.........c.C....', '........c.......',
    '0000000000000000', '0nNnnnn0Yy0nnNn0', '0BBBBBB0yY0BBBB0', '0bbbbbb0000bbbb0', '0000000000000000',
    '.0hjh0....0hjh0.', '.0HcH0....0HcH0.', '.0h2h0....0h2h0.', '.00000....00000.', '................'],
  riot_vest: [
    '................', '...0000..0000...', '..0uUUu00uUUu0..', '..0uUUUuuUUUu0..', '.0uUhhhhhhhhUu0.', '.0uUhjjjjjjhUu0.',
    '.0uUhjjjjjjhUu0.', '.0uUhhhhhhhhUu0.', '.0uUUUUUUUUUUu0.', '.0yYYYYYYYYYYy0.', '.0uUUUUUUUUUUu0.', '.0uUhhhhhhhhUu0.',
    '.0uUhjjjjjjhUu0.', '.0uUhhhhhhhhUu0.', '.00000000000000.', '................'],
  thermal_goggles: [
    '................', '................', '................', '................', '..0000....0000..', '.0hjjh0..0hjjh0.',
    '0hRooRh00hRooRh0', '0hoyYoh22hoyYoh0', '0hoYyoh00hoYyoh0', '0hRooRh..hRooRh0', '.0hhhh0..0hhhh0.', '..0000....0000..',
    '.bB..........Bb.', '..bBBBBBBBBBBb..', '...bbbbbbbbbb...', '................'],
  phase_headset: [
    '................', '.....000000.....', '...0022222200...', '..022000000220..', '.020........020.', '.020........020.',
    '0000........0000', '0120........0210', '01P0........0P10', '01m0........0m10', '01P0........0P10', '0120........0210',
    '0000........0000', '......0hhhhh0...', '.....0m00000....', '................'],
  field_journal: [
    '................', '..000000000000..', '..0BBBBBBBBBw0..', '..0BnnnnnnnBw0..', '..0Bnn0gg0nBw0..', '..0Bn0gGGg0Bw0..',
    '..0Bn0gGgg0Bw0..', '..0Bnn0gg0nBw0..', '..0BnnnnnnnBw0..', '..0BnnnnnnnBw0..', '..0BnnRnnnnBw0..', '..0BBBRBBBBBw0..',
    '..0000R0000000..', '......R.........', '.....R.R........', '................'],
  razor_claws: [
    '................', '................', '...55..55..55...', '..044004400440..', '.00000000000000.', '.0HjjHHjjHHjjH0.',
    '.0H11HH11HH11H0.', '.0H11HH11HH11H0.', '.0HhhHHhhHHhhH0.', '.00000000000000.', '....0HhH0.......', '....0HhhH0......',
    '.....0HhhH0.....', '......0HhH0.....', '.......000......', '................'],
  tritium_ring: [
    '................', '......000.......', '.....0gGg0......', '.....0GGG0......', '....00gGg00.....', '...0HjH000HjH0..',
    '..0Hj0.....0jH0.', '..0H0.......0H0.', '..0h0.......0h0.', '..0h0.......0h0.', '..0hH0.....0Hh0.', '...0hH00000Hh0..',
    '....00hhhhh00...', '......00000.....', '................', '................'],
  sprint_brace: [
    '................', '.....000000.....', '....0BnNNnB0....', '....0BnNNnB0....', '...0000000000...', '...0hjjjjjjh0...',
    '...0000000000...', '....0BnNNnB0....', '....0BnYYnB0....', '....0BYYnnB0....', '....0BnnnnB0....', '...0000000000...',
    '...0hjjjjjjh0...', '...0000000000...', '....0BnNNnB0....', '.....000000.....'],
  gyro_stabilizer: [
    '................', '.......0........', '.....00j00......', '...00yYjYy00....', '..0yY0.j.0Yy0...', '.0yY0..j..0Yy0..',
    '.0YhHHHHHHHhY0..', '0yYh0..R..0hYy0.', '0yYh0.RRR.0hYy0.', '0yYh0..R..0hYy0.', '.0YhHHHHHHHhY0..', '.0yY0..j..0Yy0..',
    '..0yY0.j.0Yy0...', '...00yYjYy00....', '.....00j00......', '.......0........'],
  toxin_injector: [
    '.......5........', '.......4........', '.......4........', '......000.......', '.....0IIi0......', '.....0gGg0......',
    '.....0ggg0......', '.....0gGg0......', '.....0ggg0......', '.....0vgv0......', '.....0vvv0......', '....0000000.....',
    '.......2........', '.......2........', '.....00000......', '................'],
  scrap_magnet: [
    '.......C........', '..0000....0000..', '..0jj0.c..0jj0..', '..0jj0....0jj0..', '..0000....0000..', '..0RR0....0RR0..',
    '..0RR0....0RR0..', '..0RR0....0RR0..', '..0RR0....0RR0..', '..0RRR0..0RRR0..', '..0rRRR00RRRr0..', '...0rRRRRRRr0...',
    '....00rrrr00....', '......0000......', '................', '................'],
  rebreather: [
    '................', '.....000000.....', '...00SSSSSS00...', '..0SSSSSSSSSS0..', 'bb0S0ii00ii0S0bb', '..0S0Ii00Ii0S0..',
    '..0SS00SS00SS0..', '..0sSSS00SSSs0..', '...0sS0hh0Ss0...', '....00hjjh00....', '.....0hjjh0.....', '.....0h11h0.....',
    '.....0hjjh0.....', '......0000......', '................', '................'],
  // Charcoal gas-mask filter canister with a green blight mark (JasprGear 3.3.0).
  blight_filter: [
    '................', '.....000000.....', '....0jjjjjj0....', '....0HHHHHH0....', '...0000000000...', '...0hHHHHHHh0...',
    '...0x5x5x5x50...', '...0hHHHHHHh0...', '...0sSSSSSSs0...', '...0sSgGGgSs0...', '...0sSgggGSs0...', '...0sSSSSSSs0...',
    '...0hHHHHHHh0...', '...0hhhhhhhh0...', '....00000000....', '................'],
  teddy_bear: [
    '................', '...000....000...', '...0eF0000Fe0...', '...0FFFFFFFF0...', '..0FFxFFFFxFF0..', '..0FFFFeeFFFF0..',
    '..0FFFexxeFFF0..', '...0FFeeeeFF0...', '...00FFFFFF00...', '..0FF0eeee0FF0..', '.0FFF0eeRe0FFF0.', '.0ff0FeeeeF0ff0.',
    '..00FFFFFFFF00..', '..0eeF0000Fee0..', '..0eF0....0Fe0..', '...00......00...'],
  grav_harness: [
    '................', '..00........00..', '.0bB0......0Bb0.', '.0Bn0......0nB0.', '..0Bn0....0nB0..', '...0Bn0..0nB0...',
    '....0Bn00nB0....', '.....0hhhh0.....', '....0hPmmPh0....', '....0hmPPmh0....', '.....0hhhh0.....', '....0Bn00nB0....',
    '...0Bn0..0nB0...', '..0Bn0....0nB0..', '.0bB0......0Bb0.', '..00........00..'],
  // Empty-slot hints: translucent grey silhouettes like vanilla's empty armor slots.
  slot_neck: [
    '................', '................', '..a..........a..', '..a..........a..', '...a........a...', '....a......a....',
    '.....a....a.....', '......a..a......', '.......aa.......', '......aAAa......', '......aAAa......', '.......aa.......',
    '................', '................', '................', '................'],
  slot_ring: [
    '................', '................', '................', '.......AA.......', '......aaaa......', '.....a....a.....',
    '....a......a....', '....a......a....', '....a......a....', '....a......a....', '.....a....a.....', '......aaaa......',
    '................', '................', '................', '................'],
  slot_belt: [
    '................', '................', '................', '................', '................', '................',
    'aaaaaaaaaaaaaaaa', 'AAAAAAa..aAAAAAA', 'AAAAAAa..aAAAAAA', 'aaaaaaaaaaaaaaaa', '................', '................',
    '................', '................', '................', '................'],
  slot_head: [
    '................', '................', '................', '................', '................', '..aaaa....aaaa..',
    '.aAAAAa..aAAAAa.', 'aAAAAAAaaAAAAAAa', 'aAAAAAAaaAAAAAAa', '.aAAAAa..aAAAAa.', '..aaaa....aaaa..', '................',
    '................', '................', '................', '................'],
  slot_body: [
    '................', '................', '...aaaa..aaaa...', '..aAAAAaaAAAAa..', '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.',
    '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.', '.aAAAAAAAAAAAAa.',
    '.aAAAAAAAAAAAAa.', '.aaaaaaaaaaaaaa.', '................', '................'],
  slot_charm: [
    '................', '................', '.......aa.......', '.......aa.......', '......aAAa......', 'aaaaaaAAAAaaaaaa',
    '.aAAAAAAAAAAAAa.', '..aAAAAAAAAAAa..', '...aAAAAAAAAa...', '...aAAAaaAAAa...', '..aAAAa..aAAAa..', '..aAAa....aAAa..',
    '.aAa........aAa.', '.aa..........aa.', '................', '................']
};
// The Necrotic Ring is the ring template re-forged in dark iron with a bone-white stone.
ART.necrotic_ring = ART.tritium_ring.map(row => row.replace(/[gGHjh]/g, c => ({g: 'p', G: 'w', H: '3', j: '4', h: '2'})[c]));

// Phase 2 consumables (pills, bandage, auto-injector, energy drink, crystal ampoule).
const PILL = ['HYH', 'YoR', 'HRH'];
ART.adrenaline_candy = [
  '................', '................', '................', '................', '.00000000000000.', '.0jjjjjjjjjjjj0.',
  ...PILL.map(p => '.0j' + p + 'j' + p + 'j' + p + '0.'),
  '.0jjjjjjjjjjjj0.', '.0RRRRRRRRRRRR0.', '.0jHjHjHjHjHjH0.', '.00000000000000.', '................', '................', '................'];
ART.field_bandage = [
  '................', '................', '..000000000000..', '.0' + '555555555555' + '0.',
  '.0' + '55555RR55555' + '0.', '.0' + '55555RR55555' + '0.', 'w0' + '55RRRRRRRR55' + '0w', 'w0' + '55RRRRRRRR55' + '0w',
  '.0' + '55555RR55555' + '0.', '.0' + '55555RR55555' + '0.', '.0' + '555555555555' + '0.', '.0' + 'kkkkkkkkkkkk' + '0.',
  '..000000000000..', '................', '................', '................'];
ART.stim_reagent = (() => {
  // A diagonal auto-injector: blue grip, green stim window, orange safety tip, steel needle.
  const g = Array.from({length: 16}, () => Array(16).fill('.'));
  const body = ['UiU', 'UiU', 'UiU', 'uUu', 'jjj', 'gGg', 'gGg', 'gGg', 'jjj', 'oYo', 'oYo'];
  body.forEach(([shade, hi, core], i) => { const x = 1 + i, y = 13 - i; g[y][x] = core; g[y][x + 1] = shade; g[y - 1][x] = hi; });
  g[2][12] = '4'; g[1][13] = '5'; g[0][14] = '5';
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (g[y][x] !== '.') continue;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const c = (g[y + dy] || [])[x + dx]; return c && c !== '.' && c !== '0' && '45'.indexOf(c) < 0; });
    if (near) g[y][x] = '0';
  }
  return g.map(row => row.join(''));
})();
ART.full_restore = [
  '................', '.....000000.....', '....04555540....', '....03444430....',
  '....0' + '322221' + '0....', '....0' + '3222Y1' + '0....', '....0' + '322YY1' + '0....', '....0' + '32YYY1' + '0....',
  '....0' + '3YYY21' + '0....', '....0' + '3YY221' + '0....', '....0' + '3Y2221' + '0....', '....0' + 'gGGGGg' + '0....',
  '....0' + '322221' + '0....', '....03444430....', '.....000000.....', '................'];
ART.adrenaline_crystal = [
  '................', '.......00.......', '......0mm0......', '.....0mCmP0.....', '....0mCmmPP0....', '....0mmmmPP0....',
  '...0mCmmmPPp0...', '...0mmmmmPPp0...', '...0mmmmPPpp0...', '...0mmmmPPpp0...', '....0mmPPpp0....', '....0mmPPpp0....',
  '.....0mPpp0.....', '......0Pp0......', '.......00.......', '................'];

// Phase 3 serums: one stoppered flask, liquid recoloured per mutation (X main, x shade).
const FLASK = [
  '................', '......0000......', '......0FF0......', '.....0FffF0.....', '.....05II50.....', '.....05II50.....',
  '....05IIII50....', '...05XXXXXX50...', '..05XXXXXXXX50..', '..05XCXXXXXx50..', '..05XXXXXXXx50..', '..05xXXXXXxx50..',
  '...05xxxxxx50...', '....05555550....', '.....000000.....', '................'];
const LIQUID = {purge_serum: 'zZ', mutagen_burrower: 'nB', mutagen_stalker: 'cu', mutagen_feral: 'or', mutagen_sprite: 'qQ',
  mutagen_scavenger: 'gv', mutagen_brute: 'Rr', mutagen_charger: 'Yy', mutagen_wyrm: 'Pp'};
for (const [id, [main, shade]] of Object.entries(LIQUID)) ART[id] = FLASK.map(row => row.replace(/[Xx]/g, c => c === 'X' ? main : shade));

// 3.2.0 backpacks: one pack template (handle, flap, buckle, front pocket), recoloured per tier.
// X main, x shade, Y flap highlight, b pocket seam.
const PACK = [
  '................', '......0000......', '.....0h00h0.....', '...0000000000...', '..0XYYYYYYYYX0..', '.0XYYYYYYYYYYX0.',
  '.0XXXXX55XXXXX0.', '.0XxxxX55XxxxX0.', '.0XxxxxxxxxxxX0.', '.0XxbbbbbbbbxX0.', '.0XxbxxYYxxbxX0.', '.0XxbxxxxxxbxX0.',
  '.0XxbbbbbbbbxX0.', '.0XxxxxxxxxxxX0.', '..000000000000..', '................'];
const PACK_COLORS = {satchel: 'nBNb', rucksack: 'VvgB', field_pack: 'Sskf', expedition_pack: 'Uui1', frame_pack: 'Rro2'};
for (const [id, [main, shade, flap, seam]] of Object.entries(PACK_COLORS))
  ART[id] = PACK.map(row => row.replace(/[XxYb]/g, c => ({X: main, x: shade, Y: flap, b: seam})[c]));

const table = Array.from({length: 256}, (_, n) => { for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1; return n >>> 0; });
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(name, rows) {
  assert.equal(rows.length, 16, name + ': 16 rows');
  const raw = Buffer.alloc(16 * (1 + 16 * 4));
  rows.forEach((row, y) => {
    assert.equal(row.length, 16, `${name} row ${y}: "${row}" is ${row.length} wide`);
    raw[y * 65] = 0;
    for (let x = 0; x < 16; x++) {
      const ch = row[x];
      if (ch === '.') continue;
      const c = PALETTE[ch];
      assert.ok(c, `${name}: unknown palette key ${ch}`);
      raw.set([c[0], c[1], c[2], c.length > 3 ? c[3] : 255], y * 65 + 1 + x * 4);
    }
  });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(16, 0); ihdr.writeUInt32BE(16, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

function build() {
  const files = new Map(); // EPK name -> Buffer
  const bands = []; // [damage, model]
  const json = v => Buffer.from(JSON.stringify(v, null, 2) + '\n');
  for (const item of catalog.items) {
    assert.ok(ART[item.id], 'missing art for ' + item.id);
    files.set(`assets/minecraft/textures/items/jaspr_gear_${item.id}.png`, png(item.id, ART[item.id]));
    files.set(`assets/minecraft/models/item/jaspr_gear_${item.id}.json`, json({parent: 'item/generated', textures: {layer0: `items/jaspr_gear_${item.id}`}}));
    bands.push([item.model, `item/jaspr_gear_${item.id}`]);
  }
  for (const item of catalog.consumables || []) {
    assert.ok(ART[item.id], 'missing art for ' + item.id);
    files.set(`assets/minecraft/textures/items/jaspr_gear_${item.id}.png`, png(item.id, ART[item.id]));
    files.set(`assets/minecraft/models/item/jaspr_gear_${item.id}.json`, json({parent: 'item/generated', textures: {layer0: `items/jaspr_gear_${item.id}`}}));
    bands.push([item.model, `item/jaspr_gear_${item.id}`]);
  }
  for (const item of catalog.backpacks || []) {
    assert.ok(ART[item.id], 'missing art for ' + item.id);
    files.set(`assets/minecraft/textures/items/jaspr_gear_${item.id}.png`, png(item.id, ART[item.id]));
    files.set(`assets/minecraft/models/item/jaspr_gear_${item.id}.json`, json({parent: 'item/generated', textures: {layer0: `items/jaspr_gear_${item.id}`}}));
    bands.push([item.model, `item/jaspr_gear_${item.id}`]);
  }
  const iconModels = new Map();
  for (const icon of catalog.icons) iconModels.set(icon.model, icon.type.toLowerCase());
  for (const [model, type] of iconModels) {
    const key = 'slot_' + type;
    assert.ok(ART[key], 'missing icon art ' + key);
    files.set(`assets/minecraft/textures/items/jaspr_gear_${key}.png`, png(key, ART[key]));
    files.set(`assets/minecraft/models/item/jaspr_gear_${key}.json`, json({parent: 'item/generated', textures: {layer0: `items/jaspr_gear_${key}`}}));
    bands.push([model, `item/jaspr_gear_${key}`]);
  }
  bands.sort((a, b) => a[0] - b[0]);
  const damages = new Set();
  for (const [d] of bands) { assert.ok(d > 0 && d < MAX_DAMAGE, 'band range ' + d); assert.ok(!damages.has(d), 'duplicate band ' + d); damages.add(d); }
  const vanilla = 'item/jaspr_gear_vanilla_stone_hoe';
  files.set('assets/minecraft/models/item/jaspr_gear_vanilla_stone_hoe.json', json({parent: 'item/handheld', textures: {layer0: 'items/stone_hoe'}}));
  // Thresholds d/(max+1) sit strictly between (d-1)/max and d/max, so float rounding cannot
  // select a neighbouring band. Later overrides win; each band is capped by the next value.
  const overrides = [];
  for (const [d, model] of bands) {
    overrides.push({predicate: {damaged: 0, damage: d / (MAX_DAMAGE + 1)}, model});
    if (!damages.has(d + 1)) overrides.push({predicate: {damaged: 0, damage: (d + 1) / (MAX_DAMAGE + 1)}, model: vanilla});
  }
  overrides.push({predicate: {damaged: 1}, model: vanilla});
  files.set('assets/minecraft/models/item/stone_hoe.json', json({parent: 'item/handheld', textures: {layer0: 'items/stone_hoe'}, overrides}));
  return {files, bands, overrides};
}

// Emulates 1.12 ItemOverrideList: reversed order, first override whose predicates are all <= value.
function resolve(overrides, damage, unbreakable) {
  const damaged = !unbreakable && damage > 0 ? 1 : 0, value = Math.fround(Math.fround(damage) / Math.fround(MAX_DAMAGE));
  for (let i = overrides.length - 1; i >= 0; i--) {
    const p = overrides[i].predicate;
    if ((p.damaged === undefined || damaged >= Math.fround(p.damaged)) && (p.damage === undefined || value >= Math.fround(p.damage))) return overrides[i].model;
  }
  return 'base';
}

function fileEntry(name, value) {
  const len = Buffer.alloc(4), check = Buffer.alloc(4);
  len.writeUInt32BE(value.length + 5); check.writeUInt32BE(crc32(value));
  return Buffer.concat([Buffer.from('FILE'), Buffer.from([Buffer.byteLength(name)]), Buffer.from(name), len, check, value, Buffer.from(':>')]);
}

function merge(input, files) {
  const parsed = decode(input);
  const byName = new Map(parsed.entries.map(e => [e.name, e]));
  assert.ok(byName.has('assets/minecraft/models/item/stone_hoe.json'), 'EPK layout (assets/ prefix) changed');
  const outputEntries = parsed.entries.map(e => files.has(e.name) ? {...e, value: files.get(e.name), raw: fileEntry(e.name, files.get(e.name))} : e);
  for (const [name, value] of files) if (!byName.has(name)) outputEntries.push({type: 'FILE', name, value, raw: fileEntry(name, value)});
  const header = Buffer.from(parsed.header);
  header.writeUInt32BE(outputEntries.length, parsed.countOffset);
  const payload = Buffer.concat([...outputEntries.map(e => e.raw), Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);
  const verified = decode(output), after = new Map(verified.entries.map(e => [e.name, e]));
  let unchanged = 0;
  for (const before of parsed.entries) if (!files.has(before.name)) { assert.deepEqual(after.get(before.name).raw, before.raw, before.name); unchanged++; }
  for (const [name, value] of files) assert.deepEqual(after.get(name).value, value, name);
  for (const [name, value] of files) {
    if (!name.endsWith('.json')) continue;
    const model = JSON.parse(value);
    for (const t of Object.values(model.textures || {})) assert.ok(after.has('assets/minecraft/textures/' + t + '.png'), name + ': missing texture ' + t);
    for (const o of model.overrides || []) assert.ok(after.has('assets/minecraft/models/' + o.model + '.json'), name + ': missing model ' + o.model);
  }
  return {output, unchanged, replaced: [...files.keys()].filter(n => byName.has(n)), added: [...files.keys()].filter(n => !byName.has(n))};
}

if (require.main === module) {
  const {files, bands, overrides} = build();
  // Model-selection proof over every stone hoe state, like the arsenal selector tests.
  for (let d = 0; d <= MAX_DAMAGE; d++) {
    const band = bands.find(b => b[0] === d);
    assert.equal(resolve(overrides, d, true), band ? band[1] : (d === 0 ? 'base' : 'item/jaspr_gear_vanilla_stone_hoe'), 'unbreakable damage ' + d);
    assert.equal(resolve(overrides, d, false), d === 0 ? 'base' : 'item/jaspr_gear_vanilla_stone_hoe', 'breakable damage ' + d);
  }
  const packDir = path.join(out, 'pack');
  fs.rmSync(packDir, {recursive: true, force: true});
  for (const [name, value] of files) { const p = path.join(packDir, name); fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, value); }
  const source = process.argv[2] || path.join(root, 'site', 'assets.epk');
  const input = fs.readFileSync(source);
  const merged = merge(input, files);
  const again = merge(merged.output, files); // idempotent: merging twice changes nothing
  assert.ok(again.output.equals(merged.output) || decode(again.output).entries.length === decode(merged.output).entries.length, 'merge not idempotent');
  fs.writeFileSync(path.join(out, 'assets.epk'), merged.output);
  const report = {source: path.relative(root, source), sourceSha256: crypto.createHash('sha256').update(input).digest('hex'),
    sha256: crypto.createHash('sha256').update(merged.output).digest('hex'), beforeBytes: input.length, afterBytes: merged.output.length,
    unchangedEntries: merged.unchanged, replaced: merged.replaced, added: merged.added.length, bands: bands.length, overrides: overrides.length,
    selectorStatesChecked: (MAX_DAMAGE + 1) * 2};
  fs.writeFileSync(path.join(out, 'asset-merge-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build, resolve, ART, PALETTE};
