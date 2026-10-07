'use strict';
// The JasperCraft look of the inventory, texture half (owner, 2026-10-07: "make the whole inventory fit the theme of Jasper Craft ...
// a thematic, superficial change. Don't change any of the logic."). Recolours the survival inventory window, the three creative
// windows and the creative tab sprites of an EPK (scripts/jasper-theme.cjs: same pixels, same geometry, new colours) and leaves every
// other entry byte-identical. Idempotent: a texture that already carries the palette is kept as it is. Writes only the --out folder.
//   node scripts/build-theme-pack.cjs --epk <assets.epk> --out <folder>
// Rebuild order for the live archive: live EPK -> this script (it only touches the five textures below, no other pack owns them).
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), zlib = require('node:zlib');
const assert = require('node:assert/strict');
const {decode, fileEntry} = require('./merge-apocalypse-assets.cjs');
const png = require('./png-codec.cjs');
const theme = require('./jasper-theme.cjs');

const entryName = texture => 'assets/minecraft/textures/gui/' + texture;

function merge(input, palette = 'jasper') {
  const parsed = decode(input), byName = new Map(parsed.entries.map(e => [e.name, e]));
  const files = new Map(), report = {};
  for (const texture of Object.keys(theme.TEXTURES)) {
    const e = byName.get(entryName(texture));
    assert.ok(e, 'EPK layout changed: ' + texture + ' is missing');
    const after = theme.recolour(e.value, texture, palette);
    report[texture] = after.equals(e.value) ? 'already themed' : 'recoloured';
    if (!after.equals(e.value)) files.set(entryName(texture), after);
  }
  const outputEntries = parsed.entries.map(e => files.has(e.name) ? {...e, value: files.get(e.name), raw: fileEntry(e.name, files.get(e.name))} : e);
  const header = Buffer.from(parsed.header);
  header.writeUInt32BE(outputEntries.length, parsed.countOffset);
  const payload = Buffer.concat([...outputEntries.map(e => e.raw), Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);
  const verified = decode(output), after = new Map(verified.entries.map(e => [e.name, e]));
  assert.equal(verified.entries.length, parsed.entries.length, 'entry count changed');
  let unchanged = 0;
  for (const before of parsed.entries) if (!files.has(before.name)) { assert.deepEqual(after.get(before.name).raw, before.raw, before.name); unchanged++; }
  for (const [name, value] of files) {
    assert.deepEqual(after.get(name).value, value, name);
    // The recoloured picture must have the same size and the same transparency (and so the same shapes) as the original.
    const was = png.decode(byName.get(name).value), now = png.decode(value);
    assert.equal(now.w, was.w); assert.equal(now.h, was.h);
    for (let i = 3; i < was.rgba.length; i += 4) assert.equal(now.rgba[i] === 0, was.rgba[i] === 0, name + ': transparency changed at byte ' + i);
  }
  return {output, unchanged, report, replaced: [...files.keys()]};
}

function main() {
  const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
  const source = arg('--epk'), out = arg('--out');
  if (!source || !out) throw new Error('usage: --epk <assets.epk> --out <folder>');
  const input = fs.readFileSync(source);
  const merged = merge(input);
  const again = merge(merged.output);
  assert.ok(again.output.equals(merged.output), 'merge not idempotent');
  fs.mkdirSync(out, {recursive: true});
  fs.writeFileSync(path.join(out, 'assets.epk'), merged.output);
  const report = {source, sourceSha256: crypto.createHash('sha256').update(input).digest('hex'), sha256: crypto.createHash('sha256').update(merged.output).digest('hex'),
    beforeBytes: input.length, afterBytes: merged.output.length, unchangedEntries: merged.unchanged, textures: merged.report};
  fs.writeFileSync(path.join(out, 'theme-pack-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
if (require.main === module) main();
module.exports = {merge, entryName};
