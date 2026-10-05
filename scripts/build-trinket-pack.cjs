'use strict';
// Trinket icons (owner, 2026-10-05: "I want every trinket to have its own custom texture, not just stolen from some kind of
// vanilla item"). Renders the 16x16 art of scripts/trinket-art, writes one item model per icon and the damage-band overrides of
// the two carrier tools (stone sword, stone shovel), and merges them into an EPK. Every unrelated entry stays byte-identical and
// the merge is idempotent. Writes only the --out folder.
//   node scripts/build-trinket-pack.cjs --epk <assets.epk> --out <folder>
// Rebuild order for the live archive: live EPK -> this script (no other pack owns stone_sword.json or stone_shovel.json).
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), zlib = require('node:zlib');
const assert = require('node:assert/strict');
const {png, crc32} = require('./trinket-art/engine.cjs');
const {decode, fileEntry} = require('./merge-apocalypse-assets.cjs');
const {CARRIERS, ENTRIES} = require('./trinket-art/catalog.cjs');
const {sheet} = require('./trinket-art/sheet.cjs');

const json = v => Buffer.from(JSON.stringify(v, null, 2) + '\n');
const textureName = e => `assets/minecraft/textures/items/jaspr_trk_${e.key}.png`;
const modelName = e => `assets/minecraft/models/item/jaspr_trk_${e.key}.json`;

/** The files of the pack. `baseModel(carrier)` returns the carrier's existing model (parsed) so its other fields survive. */
function build(baseModel = carrier => ({parent: 'item/handheld', textures: {layer0: 'items/' + carrier}})) {
  const files = new Map(), byCarrier = {}, pixels = new Map();
  for (const e of ENTRIES) {
    const px = e.icon().finish();
    pixels.set(e.key, px);
    files.set(textureName(e), png(px, 16, 16));
    files.set(modelName(e), json({parent: 'item/generated', textures: {layer0: `items/jaspr_trk_${e.key}`}}));
    (byCarrier[e.carrier] = byCarrier[e.carrier] || []).push([e.band, `item/jaspr_trk_${e.key}`]);
  }
  const overridesOf = {};
  for (const [carrier, max] of Object.entries(CARRIERS)) {
    const bands = (byCarrier[carrier] || []).sort((a, b) => a[0] - b[0]);
    const damages = new Set(bands.map(b => b[0]));
    const vanilla = `item/jaspr_trk_vanilla_${carrier}`;
    files.set(`assets/minecraft/models/item/jaspr_trk_vanilla_${carrier}.json`, json({parent: 'item/handheld', textures: {layer0: 'items/' + carrier}}));
    // Thresholds d/(max+1) sit strictly between (d-1)/max and d/max, so float rounding cannot select a neighbouring band.
    // Later overrides win; each band is capped by the next value.
    const overrides = [];
    for (const [d, model] of bands) {
      overrides.push({predicate: {damaged: 0, damage: d / (max + 1)}, model});
      if (!damages.has(d + 1)) overrides.push({predicate: {damaged: 0, damage: (d + 1) / (max + 1)}, model: vanilla});
    }
    overrides.push({predicate: {damaged: 1}, model: vanilla});
    overridesOf[carrier] = overrides;
    files.set(`assets/minecraft/models/item/${carrier}.json`, json({...baseModel(carrier), overrides}));
  }
  return {files, byCarrier, overridesOf, pixels};
}

/** Emulates 1.12 ItemOverrideList: reversed order, first override whose predicates are all <= value. */
function resolve(overrides, damage, max, unbreakable) {
  const damaged = !unbreakable && damage > 0 ? 1 : 0, value = Math.fround(Math.fround(damage) / Math.fround(max));
  for (let i = overrides.length - 1; i >= 0; i--) {
    const p = overrides[i].predicate;
    if ((p.damaged === undefined || damaged >= Math.fround(p.damaged)) && (p.damage === undefined || value >= Math.fround(p.damage))) return overrides[i].model;
  }
  return 'base';
}

function merge(input) {
  const parsed = decode(input), byName = new Map(parsed.entries.map(e => [e.name, e]));
  for (const carrier of Object.keys(CARRIERS)) assert.ok(byName.has(`assets/minecraft/models/item/${carrier}.json`), 'EPK layout (assets/ prefix) changed: ' + carrier);
  const {files, overridesOf} = build(carrier => {
    const old = JSON.parse(byName.get(`assets/minecraft/models/item/${carrier}.json`).value.toString('utf8'));
    delete old.overrides; return old;
  });
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
    if (model.parent && !model.parent.startsWith('builtin/')) assert.ok(after.has('assets/minecraft/models/' + model.parent + '.json'), name + ': missing parent ' + model.parent);
    for (const o of model.overrides || []) assert.ok(after.has('assets/minecraft/models/' + o.model + '.json'), name + ': missing model ' + o.model);
  }
  return {output, unchanged, replaced: [...files.keys()].filter(n => byName.has(n)), added: [...files.keys()].filter(n => !byName.has(n)), overridesOf};
}

function main() {
  const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
  const source = arg('--epk'), out = arg('--out');
  if (!source || !out) throw new Error('usage: --epk <assets.epk> --out <folder>');
  const input = fs.readFileSync(source);
  const merged = merge(input);
  // Selector proof over every state of both carriers: each band picks its icon, every other value keeps the vanilla look.
  for (const [carrier, max] of Object.entries(CARRIERS)) {
    const bands = new Map(ENTRIES.filter(e => e.carrier === carrier).map(e => [e.band, `item/jaspr_trk_${e.key}`]));
    for (let d = 0; d <= max; d++) {
      assert.equal(resolve(merged.overridesOf[carrier], d, max, true), bands.get(d) || (d === 0 ? 'base' : `item/jaspr_trk_vanilla_${carrier}`), `${carrier} unbreakable damage ${d}`);
      assert.equal(resolve(merged.overridesOf[carrier], d, max, false), d === 0 ? 'base' : `item/jaspr_trk_vanilla_${carrier}`, `${carrier} breakable damage ${d}`);
    }
  }
  const again = merge(merged.output);
  assert.ok(again.output.equals(merged.output), 'merge not idempotent');
  fs.mkdirSync(out, {recursive: true});
  fs.writeFileSync(path.join(out, 'assets.epk'), merged.output);
  const {files} = build();
  const packDir = path.join(out, 'pack');
  fs.rmSync(packDir, {recursive: true, force: true});
  for (const [name, value] of files) { const p = path.join(packDir, name); fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, value); }
  fs.writeFileSync(path.join(out, 'sheet.png'), sheet(ENTRIES.map(e => [e.key, e.icon()]), 4, 14));
  const report = {source, sourceSha256: crypto.createHash('sha256').update(input).digest('hex'), sha256: crypto.createHash('sha256').update(merged.output).digest('hex'),
    beforeBytes: input.length, afterBytes: merged.output.length, unchangedEntries: merged.unchanged, replaced: merged.replaced, added: merged.added.length,
    icons: ENTRIES.length, carriers: Object.keys(CARRIERS), selectorStatesChecked: Object.keys(CARRIERS).length * 132 * 2};
  fs.writeFileSync(path.join(out, 'asset-merge-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
if (require.main === module) main();
module.exports = {build, merge, resolve, textureName, modelName};
