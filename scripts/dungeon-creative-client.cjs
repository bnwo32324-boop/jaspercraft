'use strict';
// Copied from the dungeon sandbox (client/scripts/build-creative-client.cjs): the byte-preserving, data-only extension of the native Creative catalogue that carries the Dungeon items. Used by update-dungeon-creative-icons.cjs.
// No TeaVM recompilation, runtime hook changes, external dependencies or production writes.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ROOT = path.resolve(__dirname, '..');
const CLIENT = path.join(ROOT, 'candidate', 'dungeon-creative');
// JasperCraft addition: later client stages (wide inventory, text fit, silent effects) change bytes outside the fence, so the block is rebased onto the current client.
let REBASE = false;
const ANCHOR = 'var JasprCreativeCatalog=[';
const BEGIN = '/*JASPR_DUNGEON_CAT_V3_BEGIN:';
const END = '/*JASPR_DUNGEON_CAT_V3_END*/';
const CATEGORIES = ['gun', 'melee', 'armor', 'gear', 'artifact'];
const FIELDS = ['id', 'title', 'category', 'material', 'model', 'color', 'snbt', 'search'];
const HOOKS = {
  JasprCreativeTabAllows: 'd1d206d4d963448e185c475e2783f2a756df6e212efaf4654b5ee4b42ad2f3d3',
  JasprCreativeAppend: '032c9a65e676f3e6dec4caf01de797276722ed8a662fbf6a5f45d66b6eb869f3'
};
const CALLS = [
  'case 7:$p=12;case 12:JasprCreativeAppend(a.za.cA,b.v2,1);if(B()){break _;}$p=13;',
  'case 5:b.eee(e);if(B()){break _;}$p=29;case 29:JasprCreativeAppend(b,e,0);if(B()){break _;}'
];
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const count = (text, needle) => text.split(needle).length - 1;
const ascii = value => JSON.stringify(value).replace(/[\u007f-\uffff]/g, c => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0')).replace(/</g, '\\u003c').replace(/>/g, '\\u003e');
function check(condition, reason) { if (!condition) throw new Error(reason); }
function once(text, needle) { check(count(text, needle) === 1, 'expected exactly one anchor: ' + needle); return text.indexOf(needle); }

// Only used for known data literals and two audited adapter functions. No evaluation.
// Returns matching bracket and the same text with comments masked (offsets preserved).
function balanced(text, at) {
  const close = {'[': ']', '{': '}', '(': ')'};
  check(close[text[at]], 'expected opening bracket');
  const stack = [], chunks = [];
  let quote = null, comment = null, escape = false;
  for (let i = at; i < text.length; i++) {
    const c = text[i], next = text[i + 1];
    if (comment === 'line') { chunks.push(c === '\n' ? c : ' '); if (c === '\n') comment = null; continue; }
    if (comment === 'block') {
      chunks.push(c === '\n' ? c : ' ');
      if (c === '*' && next === '/') { chunks.push(' '); i++; comment = null; }
      continue;
    }
    if (quote) {
      chunks.push(c);
      if (escape) escape = false;
      else if (c === '\\') escape = true;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === '/' && (next === '/' || next === '*')) { comment = next === '/' ? 'line' : 'block'; chunks.push(' ', ' '); i++; continue; }
    chunks.push(c);
    if (c === '"' || c === "'") { quote = c; continue; }
    check(c !== '`', 'template literals are unsupported in audited data/hooks');
    if (close[c]) stack.push(close[c]);
    else if (']})'.includes(c)) {
      check(stack.pop() === c, 'unbalanced data/hooks');
      if (!stack.length) return {end: i, json: chunks.join('')};
    }
  }
  throw new Error('unterminated data/hooks');
}
function readCatalog(input) {
  check(Buffer.isBuffer(input), 'client input must be a Buffer');
  const text = input.toString('latin1'), start = once(text, ANCHOR) + ANCHOR.length - 1;
  const scanned = balanced(text, start);
  check(/^\s*;/.test(text.slice(scanned.end + 1)), 'catalogue must be an array declaration');
  const entries = JSON.parse(Buffer.from(scanned.json, 'latin1').toString('utf8'));
  check(Array.isArray(entries) && entries.length > 0, 'native catalogue must be nonempty');
  check(entries.every(e => e && typeof e.id === 'string'), 'native catalogue requires item IDs');
  check(new Set(entries.map(e => e.id)).size === entries.length, 'duplicate existing catalogue IDs');
  return {entries, start, end: scanned.end};
}
function nativeHooks(input) {
  const text = input.toString('latin1'), bodies = {};
  for (const [name, expected] of Object.entries(HOOKS)) {
    const start = once(text, 'function ' + name + '('), brace = text.indexOf('{', start);
    const end = balanced(text, brace).end;
    const body = text.slice(start, end + 1);
    check(sha(Buffer.from(body, 'latin1')) === expected, name + ' changed; audit the new native hook before patching');
    bodies[name] = body;
  }
  for (const call of CALLS) once(text, call);
  check(count(text, 'JasprCreativeAppend(') === 3, 'native Creative append call count changed');
  check(count(text, 'JasprCreativeTabAllows(') === 2, 'native Creative tab call count changed');
  return bodies;
}

// Structural SNBT reader: values are never reserialized or substituted into server templates.
// Paper remains the authority on item semantics. Handles quoted/unquoted keys, lists and typed arrays.
function parseSnbt(text) {
  let at = 0;
  function ws() { while (/\s/.test(text[at] || '') && at < text.length) at++; }
  function token(key) {
    ws(); const q = text[at];
    if (q === '"' || q === "'") {
      at++; let value = '';
      while (at < text.length) {
        const c = text[at++];
        if (c === q) return value;
        if (c === '\\') { check(at < text.length, 'SNBT escape truncated'); const e = text[at++]; check(e === q || e === '\\', 'invalid SNBT string escape'); value += e; }
        else value += c;
      }
      throw new Error('unterminated SNBT string');
    }
    const start = at, pattern = key ? /[A-Za-z0-9_+.-]/ : /[^\s,\]}:;{\["']/;
    while (at < text.length && pattern.test(text[at])) at++;
    check(at > start, 'missing SNBT token'); return text.slice(start, at);
  }
  function value(depth) {
    check(depth < 64, 'SNBT nesting limit'); ws();
    if (text[at] === '{') {
      at++; ws(); const result = Object.create(null);
      if (text[at] === '}') { at++; return result; }
      while (at < text.length) {
        const key = token(true); ws(); check(text[at++] === ':', 'SNBT compound separator');
        check(!Object.hasOwn(result, key), 'duplicate SNBT key: ' + key);
        result[key] = value(depth + 1); ws(); const end = text[at++];
        if (end === '}') return result;
        check(end === ',', 'SNBT compound delimiter');
      }
      throw new Error('unterminated SNBT compound');
    }
    if (text[at] === '[') {
      at++; ws(); const result = [];
      if (/^[BIL];/.test(text.slice(at))) at += 2;
      ws(); if (text[at] === ']') { at++; return result; }
      while (at < text.length) { result.push(value(depth + 1)); ws(); const end = text[at++]; if (end === ']') return result; check(end === ',', 'SNBT list delimiter'); }
      throw new Error('unterminated SNBT list');
    }
    return token(false);
  }
  const result = value(0); ws(); check(at === text.length, 'trailing SNBT data'); return result;
}
function validateCatalog(entries) {
  check(Array.isArray(entries) && entries.length > 0, 'export must be a nonempty JSON array');
  check(entries.length <= 20000, 'catalogue exceeds safety limit');
  const ids = new Set();
  for (const e of entries) {
    check(e && typeof e === 'object' && !Array.isArray(e), 'catalogue entry must be an object');
    check(FIELDS.every(k => Object.hasOwn(e, k)) && Object.keys(e).length === FIELDS.length, 'catalogue fields must be ' + FIELDS.join(','));
    check(typeof e.id === 'string' && /^penitent_[a-z0-9_]+$/.test(e.id), 'entry requires penitent_ ID: ' + e.id);
    check(!ids.has(e.id), 'duplicate exported ID: ' + e.id); ids.add(e.id);
    check(CATEGORIES.includes(e.category), 'unsupported native category: ' + e.category);
    for (const k of ['title', 'material', 'snbt', 'search']) check(typeof e[k] === 'string' && e[k].trim().length > 0, e.id + ': missing ' + k);
    check(typeof e.color === 'string', e.id + ': color must be a string');
    check(/^minecraft:[a-z0-9_]+$/.test(e.material), e.id + ': invalid material');
    check(Number.isInteger(e.model) && e.model >= 0 && e.model <= 32767, e.id + ': invalid model');
    check(e.search === e.search.toLowerCase(), e.id + ': native search requires lowercase search text');
    check(e.search.includes(e.title.toLowerCase()), e.id + ': title missing from search');
    check(e.snbt.length <= 32767, e.id + ': SNBT too long');
    const nbt = parseSnbt(e.snbt), marker = nbt && nbt.tag && nbt.tag.JasprDungeonCreative;
    check(nbt.id === e.material, e.id + ': SNBT material mismatch');
    check(marker && marker.id === e.id, e.id + ': tag.JasprDungeonCreative.id must match exported ID');
  }
  return entries;
}

function strip(input) {
  check(Buffer.isBuffer(input), 'client input must be a Buffer');
  const text = input.toString('latin1'), nb = count(text, BEGIN), ne = count(text, END);
  if (nb === 0 && ne === 0) { check(!text.includes('JASPR_DUNGEON_CAT_V3_'), 'unknown dungeon patch marker'); return Buffer.from(input); }
  check(nb === 1 && ne === 1, 'corrupt or repeated dungeon patch fences');
  const start = text.indexOf(BEGIN), headerEnd = text.indexOf('*/', start + BEGIN.length), end = text.indexOf(END);
  check(headerEnd > start && headerEnd < end, 'corrupt dungeon patch header');
  const meta = JSON.parse(text.slice(start + BEGIN.length, headerEnd));
  check(meta.version === 3 && /^[a-f0-9]{64}$/.test(meta.baseSha256) && /^[a-f0-9]{64}$/.test(meta.payloadSha256), 'invalid dungeon patch metadata');
  const payload = text.slice(headerEnd + 2, end);
  check(payload.startsWith(','), 'invalid appended catalogue block');
  check(sha(Buffer.from(payload, 'latin1')) === meta.payloadSha256, 'dungeon patch payload checksum mismatch');
  const ownEntries = validateCatalog(JSON.parse('[' + payload.slice(1) + ']'));
  check(ownEntries.length === meta.count, 'dungeon patch count mismatch');
  const catalog = readCatalog(input);
  check(start > catalog.start && end + END.length === catalog.end, 'dungeon patch must terminate the native array');
  const base = Buffer.concat([input.subarray(0, start), input.subarray(end + END.length)]);
  // A later third-party change must be audited/rebased explicitly; never silently strip its bytes.
  check(REBASE || sha(base) === meta.baseSha256, 'base client changed since patch; restore/rebase from the source snapshot');
  return base;
}
function apply(input, entries) {
  validateCatalog(entries);
  const base = strip(input), original = readCatalog(base);
  nativeHooks(base);
  const ids = new Set(original.entries.map(e => e.id));
  for (const e of entries) check(!ids.has(e.id), 'exported ID already exists outside our fence: ' + e.id);
  const payload = ',' + entries.map(ascii).join(',');
  const meta = {version: 3, count: entries.length, baseSha256: sha(base), payloadSha256: sha(Buffer.from(payload, 'ascii'))};
  const block = Buffer.from(BEGIN + JSON.stringify(meta) + '*/' + payload + END, 'ascii');
  const result = Buffer.concat([base.subarray(0, original.end), block, base.subarray(original.end)]);
  assert.deepEqual(strip(result), base, 'patch must strip byte for byte');
  const after = readCatalog(result).entries;
  assert.deepEqual(after.slice(0, original.entries.length), original.entries, 'preserve all preexisting entries');
  assert.deepEqual(after.slice(original.entries.length), entries, 'preserve exact server export data');
  nativeHooks(result);
  new vm.Script(result.toString('utf8'), {filename: 'classes.js'});
  return result;
}
function confined(file) {
  const absolute = path.resolve(file), relative = path.relative(CLIENT, absolute);
  check(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'output must stay inside sandbox client/');
  // Reject junctions/symlinks, including an existing output file, before any write.
  let current = CLIENT;
  check(!fs.existsSync(current) || !fs.lstatSync(current).isSymbolicLink(), 'client/ cannot be a symlink');
  for (const segment of relative.split(path.sep)) { current = path.join(current, segment); check(!fs.existsSync(current) || !fs.lstatSync(current).isSymbolicLink(), 'output cannot traverse a symlink: ' + current); }
  return absolute;
}
function write(file, bytes) {
  file = confined(file);
  check(!fs.existsSync(file) || fs.statSync(file).nlink === 1, 'output cannot be a hard link');
  fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, bytes);
}
function cli(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i];
    check(['--source', '--catalog', '--out', '--strip', '--fixture', '--preflight'].includes(key), 'unknown option: ' + key);
    check(!Object.hasOwn(options, key), 'repeated option: ' + key);
    options[key] = ['--strip', '--fixture', '--preflight'].includes(key) ? true : argv[++i];
    check(options[key] && !String(options[key]).startsWith('--'), 'missing option value: ' + key);
  }
  const source = path.resolve(options['--source'] || path.join(CLIENT, 'source/classes.js'));
  const out = confined(options['--out'] || path.join(CLIENT, 'candidate/classes.js'));
  check(source !== out, 'source and output must differ');
  check(!options['--fixture'] || !options['--preflight'], 'choose either fixture or preflight provenance');
  const raw = fs.readFileSync(source);
  if (options['--strip']) {
    check(!options['--catalog'] && !options['--fixture'] && !options['--preflight'], '--strip cannot accept catalogue options');
    const restored = strip(raw); new vm.Script(restored.toString('utf8'));
    write(out, restored); console.log(JSON.stringify({out, restoredSha256: sha(restored)})); return;
  }
  const cataloguePath = path.resolve(options['--catalog'] || path.join(ROOT, 'build/creative-catalog.json'));
  const catalogBytes = fs.readFileSync(cataloguePath), entries = JSON.parse(catalogBytes.toString('utf8').replace(/^\uFEFF/, ''));
  const result = apply(raw, entries), base = strip(raw);
  assert.deepEqual(apply(result, entries), result, 'reapplication must be byte-identical');
  const report = {stage: 'dungeon-native-creative-v3', catalogueKind: options['--fixture'] ? 'miniature-test-fixture' : options['--preflight'] ? 'preflight-Paper-export' : 'server-export',
    cataloguePath, catalogueSha256: sha(catalogBytes), source, sourceSha256: sha(raw), baseSha256: sha(base), sha256: sha(result),
    originalEntries: readCatalog(base).entries.length, appendedEntries: entries.length, totalEntries: readCatalog(result).entries.length,
    categories: Object.fromEntries(CATEGORIES.map(c => [c, entries.filter(e => e.category === c).length])),
    bytes: result.length, addedBytes: result.length - base.length, nativeHooks: HOOKS,
    checks: {compiledJavaScriptSyntax: true, exactExportRoundtrip: true, stripByteIdentical: true, idempotent: true, existingEntriesPreserved: true},
    browserValidated: false, actualPaperPickupValidated: false, customTexturesVerified: false};
  const reportPath = path.join(path.dirname(out), 'creative-client-report.json');
  check(out !== reportPath && source !== reportPath && cataloguePath !== out && cataloguePath !== reportPath, 'input/output path collision');
  confined(reportPath);
  write(out, result); write(reportPath, JSON.stringify(report, null, 2) + '\n'); console.log(JSON.stringify(report, null, 2));
}
if (require.main === module) { try { cli(process.argv.slice(2)); } catch (e) { console.error('Creative candidate failed: ' + e.message); process.exitCode = 1; } }
module.exports = {setRebase: v => { REBASE = !!v; }, apply, strip, readCatalog, nativeHooks, validateCatalog, parseSnbt, balanced, confined, write, sha, BEGIN, END, ANCHOR, CATEGORIES, CALLS, HOOKS, cli};
