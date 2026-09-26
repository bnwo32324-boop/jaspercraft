'use strict';
/* So Many Enchantments client assets (JasprEnchantments). Adds to the Eaglercraft asset pack:
 *  - assets/minecraft/lang/en_us.lang: every SME lang line (names, descriptions, enchantment levels 11-256, potion
 *    potencies, death messages), fenced by "# JASPR_SME_BEGIN/END" and replaced idempotently. Rune names are green,
 *    ancient names yellow and Pandora's Curse dark red, as SME colours them (curses are red via the client itself).
 *  - assets/somanyenchantments/sounds.json and sounds/*.ogg: SME's six sound events.
 * Also writes the same lang block into site/lang/en_us.lang's candidate copy (the client's locales URL).
 *   SME_ASSETS_SOURCE=<assets.epk> node scripts/build-sme-assets.cjs
 * Output: candidate/sme/assets.epk, candidate/sme/lang/en_us.lang (candidates only).
 */
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib'), assert = require('node:assert/strict');
const {decode} = require('./merge-apocalypse-assets.cjs');
const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, 'server', 'custom-plugins', 'JasprEnchantments', 'resources');
const SOURCE = process.env.SME_ASSETS_SOURCE || path.join(ROOT, 'site', 'assets.epk');
const BEGIN = '# JASPR_SME_BEGIN', END = '# JASPR_SME_END';
const table = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc(bytes) { let c = 0xffffffff; for (const b of bytes) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function fileEntry(name, value) {
  const len = Buffer.alloc(4), check = Buffer.alloc(4); len.writeUInt32BE(value.length + 5); check.writeUInt32BE(crc(value));
  return Buffer.concat([Buffer.from('FILE'), Buffer.from([Buffer.byteLength(name)]), Buffer.from(name), len, check, value, Buffer.from(':>')]);
}

function smeLangBlock() {
  const rows = fs.readFileSync(path.join(SRC, 'enchantments.tsv'), 'utf8').trim().split(/\r?\n/).slice(1).map(l => l.split('\t'));
  const style = new Map(rows.map(r => [r[1], r[6]]));
  const colour = {rune: '§a', ancient: '§e', pandora: '§4'};
  const out = [BEGIN];
  for (let line of fs.readFileSync(path.join(SRC, 'client-assets', 'en_us.lang'), 'utf8').split(/\r?\n/)) {
    if (!line || line.startsWith('#') || !line.includes('=')) continue;
    const eq = line.indexOf('='), key = line.slice(0, eq), value = line.slice(eq + 1);
    const m = key.match(/^enchantment\.([a-z0-9_]+)$/);
    if (m && colour[style.get(m[1])]) line = key + '=' + colour[style.get(m[1])] + value;
    out.push(line);
  }
  out.push(END);
  return out.join('\n') + '\n';
}

function withBlock(text, block) {
  const a = text.indexOf(BEGIN);
  let base = text;
  if (a >= 0) { const b = text.indexOf(END, a); assert.ok(b > a, 'SME lang fence damaged'); base = text.slice(0, a) + text.slice(b + END.length).replace(/^\r?\n/, ''); }
  const eol = text.includes('\r\n') ? '\r\n' : '\n';     // keep the file's own line endings
  return base.replace(/\s*$/, eol) + block.replace(/\n/g, eol);
}

function build() {
  const input = fs.readFileSync(SOURCE);
  const parsed = decode(input);
  const block = smeLangBlock();
  const add = new Map();
  const lang = parsed.entries.find(e => e.name === 'assets/minecraft/lang/en_us.lang');
  assert.ok(lang, 'en_us.lang missing from the asset pack');
  add.set(lang.name, Buffer.from(withBlock(lang.value.toString('utf8'), block), 'utf8'));
  add.set('assets/somanyenchantments/sounds.json', fs.readFileSync(path.join(SRC, 'client-assets', 'sounds.json')));
  for (const f of fs.readdirSync(path.join(SRC, 'client-assets', 'sounds')).filter(f => f.endsWith('.ogg')).sort())
    add.set('assets/somanyenchantments/sounds/' + f, fs.readFileSync(path.join(SRC, 'client-assets', 'sounds', f)));
  const entries = parsed.entries.map(e => add.has(e.name) ? {...e, value: add.get(e.name), raw: fileEntry(e.name, add.get(e.name))} : e);
  for (const [name, value] of add) if (!parsed.entries.some(e => e.name === name)) entries.push({type: 'FILE', name, value, raw: fileEntry(name, value)});
  const header = Buffer.from(parsed.header); header.writeUInt32BE(entries.length, parsed.countOffset);
  const payload = Buffer.concat([...entries.map(e => e.raw), Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);
  const check = decode(output), byName = new Map(check.entries.map(e => [e.name, e]));
  for (const before of parsed.entries) if (!add.has(before.name)) assert.deepEqual(byName.get(before.name).raw, before.raw, before.name);
  for (const [name, value] of add) assert.deepEqual(byName.get(name).value, value, name);
  const siteLang = withBlock(fs.readFileSync(path.join(ROOT, 'site', 'lang', 'en_us.lang'), 'utf8'), block);
  return {output, siteLang, added: [...add.keys()], langLines: block.split('\n').length - 3};
}

if (require.main === module) {
  const r = build();
  const dir = path.join(ROOT, 'candidate', 'sme');
  fs.mkdirSync(path.join(dir, 'lang'), {recursive: true});
  fs.writeFileSync(path.join(dir, 'assets.epk'), r.output);
  fs.writeFileSync(path.join(dir, 'lang', 'en_us.lang'), r.siteLang);
  console.log(JSON.stringify({entries: r.added.length, langLines: r.langLines, bytes: r.output.length}));
}
module.exports = {build, smeLangBlock};
