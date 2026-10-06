'use strict';
// Own icons for every trinket, bauble and seal (owner 2026-10-05: "I want every trinket to have its own custom texture, not just
// stolen from some kind of vanilla item"). The art catalogue, the servers' band tables and the pack must agree, and no trinket
// definition may be left on a vanilla stand-in.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {ENTRIES, CARRIERS, RUINS_BANDS, NETHER_BANDS, BACKROOMS_BANDS, DUNGEON7_BANDS} = require('../scripts/trinket-art/catalog.cjs');
const dungeonArt = require('../scripts/trinket-art/dungeon.cjs');
const dungeon7Art = require('../scripts/trinket-art/dungeon7.cjs');
const pack = require('../scripts/build-trinket-pack.cjs');
const {TARGETS, render} = require('../scripts/trinket-art/gen-skin.cjs');

const root = path.resolve(__dirname, '..');
const read = f => fs.readFileSync(path.join(root, f), 'utf8');
const plugins = 'server/custom-plugins/';
// The Dungeon plugin's source: the live copy, or (JASPR_DUNGEON_SRC=<.../src/chat/jaspr/dungeon>) a sandbox being prepared for it.
const DUNGEON_SRC = process.env.JASPR_DUNGEON_SRC ? path.resolve(process.env.JASPR_DUNGEON_SRC) : path.join(root, plugins, 'JasprDungeon/src/chat/jaspr/dungeon');
const dungeonSource = f => fs.readFileSync(path.join(DUNGEON_SRC, f), 'utf8');
/** Relics.Type's constants in order, each with its explicit carrier and band (generation 7) or null (classic: position + 1). */
function relicTypes(src) {
  const body = src.slice(src.indexOf('public enum Type {') + 'public enum Type {'.length, src.indexOf('public final LootCatalog.Bauble spec;')).replace(/\/\/[^\n]*/g, '');
  return [...body.matchAll(/\b([A-Z][A-Z0-9_]+)(?:\(Skin\.(SWORD|SHOVEL),(\d+)\))?\s*[,;]/g)]
    .map(m => ({name: m[1], carrier: m[2] ? (m[2] === 'SWORD' ? 'stone_sword' : 'stone_shovel') : null, band: m[3] ? +m[3] : null}));
}
/** TrophyCatalog's weapons and their stone-sword bands (an empty map before generation 7). */
function trophyBands(src) { return Object.fromEntries([...src.matchAll(/^\s+([A-Z_]+)\((\d+), \d+, Kind\.WEAPON/gm)].map(m => [m[1], +m[2]])); }
const enumNames = (text, name) => {
  const start = text.indexOf('enum ' + name + ' {');
  assert.ok(start >= 0, 'enum ' + name);
  const body = text.slice(start, text.indexOf(';', start));
  return [...body.matchAll(/(?:^|[,{\s])([A-Z][A-Z0-9_]+)\s*(?:\(|,|$)/gm)].map(m => m[1]).filter(n => n !== name.toUpperCase());
};

test('Dungeon: the 72 classic baubles keep their icons and bands (their enum position + 1), the pouch 73', () => {
  const loot = dungeonSource('LootCatalog.java');
  const baubles = [...loot.slice(loot.indexOf('public enum Bauble')).matchAll(/^\s+([A-Z][A-Z_]+)\("[^"]+","[A-Z_]+",\d,\d+,Trigger/gm)].map(m => m[1]);
  assert.ok(baubles.length >= 72);
  assert.deepEqual(Object.keys(dungeonArt.ICONS), baubles.slice(0, 72), 'classic icons follow LootCatalog.Bauble order');
  const relics = dungeonSource('Relics.java'), types = relicTypes(relics);
  assert.deepEqual(types.map(t => t.name), baubles, 'Relics.Type has the same order');
  assert.ok(types.slice(0, 72).every(t => t.band === null), 'a classic bauble keeps position + 1');
  assert.match(relics, /public int band\(\)\{return (ordinal\(\)\+1|icon>0\?icon:ordinal\(\)\+1);\}/, 'band = position + 1 unless explicit');
  assert.match(relics, /static final int POUCH_BAND=(Type\.values\(\)\.length\+1|73);/, 'the pouch follows the 72 classic baubles');
  if (/POUCH_BAND=Type\.values\(\)\.length\+1/.test(relics)) assert.equal(types.length, 72, 'a pouch band computed from the enum only holds with exactly 72 baubles');
  const entries = ENTRIES.filter(e => e.plugin === 'dungeon' && !(e.id in DUNGEON7_BANDS));
  assert.equal(entries.length, 73);
  assert.deepEqual(entries.map(e => e.band), Array.from({length: 73}, (_, i) => i + 1));
  assert.ok(entries.every(e => e.carrier === 'stone_sword'));
  assert.match(relics, /Skin\.apply\(edit\(item,d->\{d\.setString\("kind","relic"\)/, 'new baubles are skinned');
  assert.match(relics, /Skin\.apply\(edit\(item,d->\{d\.setString\("kind","pouch"\)/, 'new pouches are skinned');
});

test('Dungeon generation 7: every new bauble and trophy has its own explicit band, and the plugin agrees once it names them', () => {
  assert.deepEqual(Object.keys(dungeon7Art.ICONS).sort(), Object.keys(DUNGEON7_BANDS).sort(), 'art and band tables name the same items');
  assert.equal(Object.keys(DUNGEON7_BANDS).length, 115, '112 baubles (the victor\'s laurel included) and 3 trophy weapons');
  for (const [id, [carrier, band]] of Object.entries(DUNGEON7_BANDS)) {
    if (carrier === 'stone_sword') assert.ok(band >= 74 && band <= 130, id + ': after the pouch, below the sword\'s durability');
    else {
      assert.equal(carrier, 'stone_shovel', id);
      assert.ok(band >= 56 && band <= 130, id + ': clear of Drownhollow (1-15), the Nether (21-32) and the Backrooms (41-55)');
    }
  }
  assert.ok(Object.values(DUNGEON7_BANDS).filter(([c]) => c === 'stone_sword').length === 57, 'the stone sword is full: 74..130');
  const relics = dungeonSource('Relics.java'), types = relicTypes(relics), fresh = types.filter(t => t.band !== null);
  if (!fresh.length) { assert.equal(types.length, 72, 'before generation 7 the plugin names its 72 classic baubles only'); return; }
  // The plugin names its generation 7 items: both sides must agree band for band.
  const java = {};
  for (const t of fresh) java[t.name] = [t.carrier, t.band];
  for (const [id, band] of Object.entries(trophyBands(dungeonSource('TrophyCatalog.java')))) java[id] = ['stone_sword', band];
  assert.deepEqual(java, DUNGEON7_BANDS, 'Relics.Type and TrophyCatalog carry exactly the catalogue\'s bands');
  assert.equal(types.length - 72, fresh.length, 'every bauble after the classic 72 has an explicit band');
  assert.match(relics, /static final int POUCH_BAND=73;/, 'the pouch band is fixed');
  assert.match(relics, /Skin\.apply\(edit\(item,d->\{d\.setString\("kind","relic"\);d\.setString\("id",t\.name\(\)\);d\.setInt\("version",1\);\}\),t\.carrier\(\),t\.band\(\)\)/, 'a bauble is skinned on its own carrier and band');
  assert.match(dungeonSource('Trophies.java'), /return Skin\.apply\(CraftItemStack\.asBukkitCopy\(n\),Skin\.SWORD,t\.band\);/, 'a trophy weapon is skinned on its stone-sword band');
});

test('Drownhollow: skinned relics and seals match the Java bands; only the worn two keep vanilla items', () => {
  const src = read(plugins + 'JasprRuins/src/chat/jaspr/ruins/Trinkets.java');
  const bandSwitch = new Map([...src.slice(src.indexOf('int band() {'), src.indexOf('enum Seal')).matchAll(/case ([A-Z_]+): return (\d+);/g)].map(m => [m[1], +m[2]]));
  const trinkets = [...src.slice(src.indexOf('enum Trinket {'), src.indexOf('final String title;')).matchAll(/^\s+([A-Z_]+)\("/gm)].map(m => m[1]);
  assert.equal(trinkets.length, 10);
  const seals = [...src.slice(src.indexOf('enum Seal {'), src.indexOf('final String title, keeper;')).matchAll(/([A-Z]+)\("Seal of/g)].map(m => m[1]);
  assert.deepEqual(seals, ['TIDES', 'STONE', 'HUNGER', 'DEEP', 'SILENCE']);
  const expected = {};
  for (const t of trinkets) if (bandSwitch.has(t)) expected[t] = bandSwitch.get(t);
  seals.forEach((s, n) => { expected['SEAL_' + s] = 11 + n; });
  assert.deepEqual(expected, RUINS_BANDS);
  assert.deepEqual(trinkets.filter(t => !bandSwitch.has(t)), ['FACELESS_MASK', 'CROWN'], 'only the helmet-slot relics keep their vanilla items');
  assert.match(src, /return t\.band\(\) > 0 \? Skin\.apply\(item, Skin\.SHOVEL, t\.band\(\)\) : item;/);
  assert.match(src, /return Skin\.apply\(item, Skin\.SHOVEL, s\.band\(\)\);/);
  assert.match(src, /int band\(\) \{ return 11 \+ ordinal\(\); \}/);
});

test('Nether: every carried or off-hand trinket is skinned with the catalogue band', () => {
  const src = read(plugins + 'JasprNether/src/chat/jaspr/nether/Items.java');
  const defs = [...src.matchAll(/def\(new Def\("([a-z_]+)",[^\n]*/g)].map(m => ({id: m[1], line: m[0]}));
  const skinned = {};
  for (const d of defs) { const m = /\.skin\((\d+)\)\)/.exec(d.line); if (m) skinned[d.id.toUpperCase()] = +m[1]; }
  assert.deepEqual(skinned, NETHER_BANDS);
  // The audit: nothing that says it is carried or held in the off hand may still be a vanilla stand-in.
  for (const d of defs) if (/"(Carried:|In the off hand:)/.test(d.line) && !(d.id.toUpperCase() in NETHER_BANDS)) assert.fail(d.id + ' is a trinket without an icon of its own');
  assert.match(src, /return d\.skin > 0 \? Skin\.apply\(made, Skin\.SHOVEL, d\.skin\) : made;/);
});

test('Backrooms: every trinket is skinned with the catalogue band', () => {
  const src = read(plugins + 'JasprBackrooms/src/chat/jaspr/backrooms/Items.java');
  const trinkets = src.split('\n').filter(l => /Kind\.TRINKET,/.test(l) && /def\(new Def\(/.test(l));
  assert.equal(trinkets.length, 15);
  const skinned = {};
  for (const l of trinkets) {
    const id = /new Def\("([a-z_]+)"/.exec(l)[1], m = /\.skin\((\d+)\);/.exec(l);
    assert.ok(m, id + ' is a trinket without an icon of its own');
    skinned[id.toUpperCase()] = +m[1];
  }
  assert.deepEqual(skinned, BACKROOMS_BANDS);
  assert.match(src, /return d\.skin > 0 \? Skin\.apply\(made, Skin\.SHOVEL, d\.skin\) : made;/);
});

test('every plugin upgrades old items where they are found and never lets a trinket act as a tool', () => {
  for (const [file, log] of [['JasprDungeon/src/chat/jaspr/dungeon/Relics.java', 'DUNGEON_RELIC_ICONS'], ['JasprRuins/src/chat/jaspr/ruins/Trinkets.java', 'RUINS_RELIC_ICONS'],
    ['JasprNether/src/chat/jaspr/nether/Relics.java', 'NETHER_TRINKET_ICONS'], ['JasprBackrooms/src/chat/jaspr/backrooms/Gear.java', 'BACKROOMS_TRINKET_ICONS']]) {
    const src = read(plugins + file);
    for (const hook of ['iconsOnJoin', 'iconsOnOpen', 'iconsOnPickup', 'noToolUse']) assert.match(src, new RegExp('void ' + hook + '\\('), file + ' ' + hook);
    assert.ok(src.includes(log), file + ' logs ' + log);
    assert.match(src, /Skin\.carrier\(e\.getItem\(\)\)\)[^;]*setUseItemInHand/, file + ': no path-making with a trinket');
  }
});

test('Skin.java is the same generated file in every plugin', () => {
  for (const [dir, pkg] of Object.entries(TARGETS)) assert.equal(read(dir + '/Skin.java'), render(pkg), dir);
  const template = read('scripts/trinket-art/Skin.java.template');
  assert.match(template, /STONE_SWORD, SHOVEL = Material\.STONE_SPADE/);
  assert.ok(template.includes('tag.setBoolean("Unbreakable", true)'), 'carriers are unbreakable');
  assert.ok(template.includes('tag.setInt("HideFlags", tag.getInt("HideFlags") | 2 | 4)'), 'durability and attributes stay hidden');
});

test('catalogue: every icon is 16x16, drawn, distinct, and fits with its outline', () => {
  assert.equal(ENTRIES.length, 228);   // 111 + the two carried relics of the colossi (2026-10-05) + the Dungeon Dimension's 115 generation 7 items
  const seen = new Map();
  for (const e of ENTRIES) {
    const icon = e.icon(), box = icon.box(), px = icon.finish();
    assert.ok(box.x1 - box.x0 + 1 <= 14 && box.y1 - box.y0 + 1 <= 14, e.key + ' fits with its outline');
    assert.ok(px.filter(Boolean).length >= 40, e.key + ' is more than a speck');
    const colours = new Set(px.filter(Boolean).map(c => c.join(',')));
    assert.ok(colours.size >= 4, e.key + ' has shading');
    const hash = crypto.createHash('sha256').update(JSON.stringify(px)).digest('hex');
    assert.ok(!seen.has(hash), e.key + ' is identical to ' + seen.get(hash));
    seen.set(hash, e.key);
  }
});

test('pack: bands, selector states and the merge into the archive', {skip: !fs.existsSync(path.join(root, 'site', 'assets.epk'))}, () => {
  const {files, overridesOf} = pack.build();
  for (const e of ENTRIES) {
    assert.ok(files.has(pack.textureName(e)) && files.has(pack.modelName(e)), e.key);
    assert.equal(files.get(pack.textureName(e)).subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  }
  for (const [carrier, max] of Object.entries(CARRIERS)) {
    const bands = new Map(ENTRIES.filter(e => e.carrier === carrier).map(e => [e.band, 'item/jaspr_trk_' + e.key]));
    for (let d = 0; d <= max; d++) {
      assert.equal(pack.resolve(overridesOf[carrier], d, max, true), bands.get(d) || (d === 0 ? 'base' : 'item/jaspr_trk_vanilla_' + carrier), carrier + ' unbreakable ' + d);
      assert.equal(pack.resolve(overridesOf[carrier], d, max, false), d === 0 ? 'base' : 'item/jaspr_trk_vanilla_' + carrier, carrier + ' breakable ' + d);
    }
  }
  const merged = pack.merge(fs.readFileSync(path.join(root, 'site', 'assets.epk')));
  assert.deepEqual(merged.replaced.sort(), ['assets/minecraft/models/item/stone_shovel.json', 'assets/minecraft/models/item/stone_sword.json']);
  assert.equal(merged.added.length, ENTRIES.length * 2 + Object.keys(CARRIERS).length, 'one texture and one model per icon, one fallback per carrier');
  assert.ok(pack.merge(merged.output).output.equals(merged.output), 'the merge is idempotent');
});
