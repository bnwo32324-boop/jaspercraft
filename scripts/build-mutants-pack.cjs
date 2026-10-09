'use strict';
// Mutant Creatures assets (JASPR_MUTANTS client stage) for the browser client's resource archive. The client loads the
// minecraft resource domain, so the mod's assets (client-mods/mutants/assets/mutantbeasts, AGPL-3.0 / see
// ASSETS_LICENSE.txt) are placed under it, each in a jaspr_mutants folder the stage's code names:
//  - textures/entity/**            -> minecraft/textures/entity/jaspr_mutants/**   (renderers, MutantBeasts.getEntityTexture)
//  - textures/gui/*.png            -> minecraft/textures/gui/jaspr_mutants/*.png   (CreeperMinionTrackerScreen)
//  - textures/particle/*.png       -> minecraft/textures/particle/jaspr_mutants/*.png (SkullSpiritParticle)
//  - textures/item/**              -> minecraft/textures/items/jaspr_mutants/**    (item model layers)
//  - textures/models/armor/mutant_skeleton_layer_<n>.png -> minecraft/textures/models/armor/jaspr_mutants_mutant_skeleton_layer_<n>.png
//    (the armour material is named jaspr_mutants_mutant_skeleton on the client)
//  - models/item/<name>.json       -> minecraft/models/item/jaspr_mutants/<name>.json, texture references rewritten
//  - sounds/**                     -> minecraft/sounds/jaspr/mutants/**, and every sounds.json event as
//    jaspr.mutants.<event> in minecraft/sounds.json (the stage registers its 43 sound events under those names).
//    The newer mutant skeleton sounds are All Rights Reserved and are never shipped (MUTANTS_PROTOCOL.md 1.4): their
//    events play the legacy files (ambient, death, hurt, step; the server is configured to use the legacy events) or a
//    vanilla event close to them (bite, bow draw, bow shot, jump, punch);
//  - lang/en_us.lang               -> appended to minecraft/lang/en_us.lang between fences (replaced on a re-run).
// Existing entries other than sounds.json and en_us.lang stay byte-identical; every sound, texture and parent a written
// file names must exist in the result, or the build stops. Idempotent: running it on its own output gives the same bytes.
//   node scripts/build-mutants-pack.cjs <input assets.epk> [out dir]   -> <out>/assets.epk, <out>/assets/** (the added files)
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {decode, fileEntry} = require('./merge-apocalypse-assets.cjs');

const SRC = path.join(__dirname, '..', 'client-mods', 'mutants', 'assets', 'mutantbeasts');
const LANG_BEGIN = '# ---- JASPR_MUTANTS_BEGIN (Mutant Creatures, AGPL-3.0) ----', LANG_END = '# ---- JASPR_MUTANTS_END ----';
// The newer skeleton sound files are not shipped: what each of their events plays instead.
const SKELETON_SUBSTITUTES = {
  'entity.mutant_skeleton.ambient': [{name: 'jaspr.mutants.entity.mutant_skeleton.ambient.legacy', type: 'event'}],
  'entity.mutant_skeleton.death': [{name: 'jaspr.mutants.entity.mutant_skeleton.death.legacy', type: 'event'}],
  'entity.mutant_skeleton.hurt': [{name: 'jaspr.mutants.entity.mutant_skeleton.hurt.legacy', type: 'event'}],
  'entity.mutant_skeleton.step': [{name: 'jaspr.mutants.entity.mutant_skeleton.step.legacy', type: 'event'}],
  'entity.mutant_skeleton.bite': [{name: 'entity.skeleton.hurt', type: 'event', pitch: 0.6}],
  'entity.mutant_skeleton.bow_draw': [{name: 'item.armor.equip_chain', type: 'event', pitch: 0.7}],
  'entity.mutant_skeleton.bow_shoot': [{name: 'entity.skeleton.shoot', type: 'event', pitch: 0.8}],
  'entity.mutant_skeleton.jump': [{name: 'entity.skeleton.step', type: 'event', pitch: 0.6}],
  'entity.mutant_skeleton.punch': [{name: 'entity.player.attack.strong', type: 'event', pitch: 0.8}]
};

function walk(dir, base = dir, out = []) {
  for (const e of fs.readdirSync(dir, {withFileTypes: true}).sort((a, b) => a.name < b.name ? -1 : 1)) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, base, out); else out.push(path.relative(base, p).replaceAll('\\', '/'));
  }
  return out;
}

/** The files this pack adds (archive names without the "assets/" prefix handled by the caller). */
function assets() {
  const files = {};
  const add = (name, value) => { assert.ok(!(name in files), 'duplicate ' + name); files[name] = value; };
  for (const rel of walk(path.join(SRC, 'textures'))) {
    const value = fs.readFileSync(path.join(SRC, 'textures', rel));
    assert.equal(value.subarray(1, 4).toString(), 'PNG', rel);
    let m;
    if ((m = /^entity\/(.+\.png)$/.exec(rel))) add('minecraft/textures/entity/jaspr_mutants/' + m[1], value);
    else if ((m = /^gui\/(.+\.png)$/.exec(rel))) add('minecraft/textures/gui/jaspr_mutants/' + m[1], value);
    else if ((m = /^particle\/(.+\.png)$/.exec(rel))) add('minecraft/textures/particle/jaspr_mutants/' + m[1], value);
    else if ((m = /^item\/(.+\.png)$/.exec(rel))) add('minecraft/textures/items/jaspr_mutants/' + m[1], value);
    else if ((m = /^models\/armor\/mutant_skeleton_layer_([12])\.png$/.exec(rel))) add('minecraft/textures/models/armor/jaspr_mutants_mutant_skeleton_layer_' + m[1] + '.png', value);
    else throw Error('unmapped texture ' + rel);
  }
  for (const rel of walk(path.join(SRC, 'models', 'item'))) {
    const model = JSON.parse(fs.readFileSync(path.join(SRC, 'models', 'item', rel), 'utf8'));
    for (const [k, v] of Object.entries(model.textures || {})) {
      const m = /^mutantbeasts:item\/(.+)$/.exec(v);
      if (!m) throw Error(rel + ': texture ' + v);
      model.textures[k] = 'items/jaspr_mutants/' + m[1];
    }
    if (model.parent && model.parent.startsWith('mutantbeasts:')) throw Error(rel + ': parent ' + model.parent);
    add('minecraft/models/item/jaspr_mutants/' + rel, Buffer.from(JSON.stringify(model, null, 2) + '\n'));
  }
  for (const rel of walk(path.join(SRC, 'sounds'))) {
    // only the legacy skeleton sounds may be shipped from the mutant_skeleton folder
    if (/^entity\/mutant_skeleton\//.test(rel) && !/^entity\/mutant_skeleton\/legacy\//.test(rel)) throw Error('All Rights Reserved sound present: ' + rel);
    const value = fs.readFileSync(path.join(SRC, 'sounds', rel));
    assert.equal(value.subarray(0, 4).toString(), 'OggS', rel);
    add('minecraft/sounds/jaspr/mutants/' + rel, value);
  }
  return files;
}

/** The mod's sounds.json events as minecraft events named jaspr.mutants.<event>. */
function soundEvents() {
  const mod = JSON.parse(fs.readFileSync(path.join(SRC, 'sounds.json'), 'utf8'));
  const out = {};
  const ref = s => {
    const m = /^mutantbeasts:(.+)$/.exec(s);
    return m ? 'jaspr/mutants/' + m[1] : s;
  };
  for (const [event, def] of Object.entries(mod)) {
    const copy = JSON.parse(JSON.stringify(def));
    if (SKELETON_SUBSTITUTES[event]) copy.sounds = SKELETON_SUBSTITUTES[event];
    else copy.sounds = copy.sounds.map(s => typeof s === 'string' ? ref(s) : Object.assign(s, s.type === 'event' ? {} : {name: ref(s.name)}));
    if ('replace' in copy) delete copy.replace;
    out['jaspr.mutants.' + event] = copy;
  }
  return out;
}

function langBlock() {
  const lines = fs.readFileSync(path.join(SRC, 'lang', 'en_us.lang'), 'utf8').replace(/\r\n?/g, '\n').split('\n')
    .filter(l => l.trim() && !l.trimStart().startsWith('#') && l.includes('='));   // values keep their trailing spaces, as Locale reads them
  return LANG_BEGIN + '\n' + lines.join('\n') + '\n' + LANG_END + '\n';
}
function mergeLang(text) {
  let t = text.replace(/\r\n/g, '\n');
  const a = t.indexOf(LANG_BEGIN);
  if (a >= 0) {
    const b = t.indexOf(LANG_END, a);
    if (b < 0) throw Error('en_us.lang: unterminated Mutants block');
    t = t.slice(0, a) + t.slice(b + LANG_END.length).replace(/^\n/, '');
  }
  if (!t.endsWith('\n')) t += '\n';
  return t + langBlock();
}

function build(input) {
  const parsed = decode(input);
  const prefix = parsed.entries.some(e => e.name === 'assets/minecraft/sounds.json') ? 'assets/' : '';
  const byName = new Map(parsed.entries.map(e => [e.name, e.value]));
  const files = {};
  for (const [n, v] of Object.entries(assets())) files[prefix + n] = v;
  // sounds.json: the vanilla events plus ours (earlier jaspr.mutants.* entries replaced)
  const soundsName = prefix + 'minecraft/sounds.json';
  const sounds = JSON.parse(byName.get(soundsName).toString('utf8'));
  for (const k of Object.keys(sounds)) if (k.startsWith('jaspr.mutants.')) delete sounds[k];
  const ours = soundEvents();
  Object.assign(sounds, ours);
  files[soundsName] = Buffer.from(JSON.stringify(sounds, null, 2) + '\n');
  const langName = prefix + 'minecraft/lang/en_us.lang';
  files[langName] = Buffer.from(mergeLang(byName.get(langName).toString('utf8')), 'utf8');

  const names = new Set(Object.keys(files));
  const entries = parsed.entries.filter(e => !names.has(e.name)).map(e => e.raw);
  for (const [n, value] of Object.entries(files).sort((a, b) => a[0] < b[0] ? -1 : 1)) entries.push(fileEntry(n, value));
  const header = Buffer.from(parsed.header);
  header.writeUInt32BE(entries.length, parsed.countOffset);
  const payload = Buffer.concat([...entries, Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);

  // verify: untouched entries byte-identical, ours present, every reference resolves
  const check = decode(output), after = new Map(check.entries.map(e => [e.name, e]));
  for (const e of parsed.entries) if (!names.has(e.name)) assert.deepEqual(after.get(e.name).raw, e.raw, e.name);
  for (const [n, v] of Object.entries(files)) assert.deepEqual(after.get(n).value, v, n);
  const has = n => after.has(prefix + n);
  const resource = (ref, type, ext) => { const [ns, local] = ref.includes(':') ? ref.split(':') : ['minecraft', ref]; return ns + '/' + type + '/' + local + ext; };
  for (const n of Object.keys(files).filter(n => n.endsWith('.json') && n.includes('/models/'))) {
    const model = JSON.parse(files[n]);
    for (const t of Object.values(model.textures || {})) assert.ok(has(resource(t, 'textures', '.png')), n + ': missing texture ' + t);
    if (model.parent && !model.parent.startsWith('builtin/')) assert.ok(has(resource(model.parent, 'models', '.json')), n + ': missing parent ' + model.parent);
  }
  for (const [event, def] of Object.entries(ours)) for (const s of def.sounds) {
    if (typeof s === 'object' && s.type === 'event') assert.ok(s.name in sounds, event + ': missing event ' + s.name);
    else assert.ok(has(resource(typeof s === 'string' ? s : s.name, 'sounds', '.ogg')), event + ': missing sound ' + JSON.stringify(s));
  }
  return {output, files, prefix, events: Object.keys(ours).length};
}

if (require.main === module) {
  const input = process.argv[2];
  if (!input) { console.error('usage: node scripts/build-mutants-pack.cjs <input assets.epk> [out dir]'); process.exit(2); }
  const out = process.argv[3] || path.join(__dirname, '..', 'candidate', 'mutants-pack');
  const result = build(fs.readFileSync(input));
  fs.rmSync(path.join(out, 'assets'), {recursive: true, force: true});
  for (const [n, v] of Object.entries(result.files)) {
    const p = path.join(out, result.prefix ? n : 'assets/' + n);
    fs.mkdirSync(path.dirname(p), {recursive: true});
    fs.writeFileSync(p, v);
  }
  fs.writeFileSync(path.join(out, 'assets.epk'), result.output);
  const report = {files: Object.keys(result.files).length, soundEvents: result.events, bytes: result.output.length,
    sha256: crypto.createHash('sha256').update(result.output).digest('hex')};
  fs.writeFileSync(path.join(out, 'pack-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report));
}
module.exports = {build, assets, soundEvents, mergeLang, LANG_BEGIN, LANG_END, SKELETON_SUBSTITUTES};
