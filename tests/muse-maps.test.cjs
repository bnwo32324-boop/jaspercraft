'use strict';
// Muse+GLM_Maps pack invariants (JasprMuseMaps). Run: node --test tests/muse-maps.test.cjs
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), zlib = require('node:zlib');
const root = path.resolve(__dirname, '..');
const plugin = path.join(root, 'server/custom-plugins/JasprMuseMaps');
const catalog = JSON.parse(fs.readFileSync(path.join(plugin, 'pack/maps/catalog.json'), 'utf8'));
const sites = catalog.sites;

test('all 139 owner schematics are in their own, identifiable collection', () => {
  assert.equal(catalog.collection, 'Muse+GLM_Maps');
  assert.equal(sites.length, 139);
  const sources = fs.readdirSync(path.join(plugin, 'maps', 'Muse+GLM_Maps')).filter(f => f.endsWith('.schem'));
  assert.equal(sources.length, 139);
  assert.deepEqual(new Set(sites.map(s => s.source)), new Set(sources));
  for (const s of sites) {
    assert.match(s.id, /^muse:[a-z0-9_]+$/);
    assert.equal(s.collection, 'Muse+GLM_Maps');
    assert.ok(s.dimensions[1] >= 1 && s.dimensions[1] <= 255 && s.dimensions[0] <= 256 && s.dimensions[2] <= 256, s.id);
  }
});

test('pack files match their hashes and hold no valuable or unsafe blocks', () => {
  const forbidden = new Set([41, 42, 57, 133, 138, 46, 52, 90, 119, 137, 210, 211, 255, 166]);
  for (const s of sites) {
    const packed = fs.readFileSync(path.join(plugin, 'pack/maps', s.file));
    assert.equal(crypto.createHash('sha256').update(packed).digest('hex'), s.sha256, s.id);
    const raw = zlib.gunzipSync(packed);
    assert.equal(raw.toString('latin1', 0, 4), 'SE45');
    const [x, y, z, n] = [4, 8, 12, 16].map(o => raw.readUInt32BE(o));
    assert.deepEqual([x, y, z], s.dimensions, s.id);
    assert.equal(raw.length, 20 + 7 * n, s.id);
    for (let i = 0; i < n; i++) assert.ok(!forbidden.has(raw.readUInt16BE(20 + 7 * i + 4)), s.id + ' has a forbidden block');
  }
});

test('every structure has dangers from all three dimensions, a unique custom mob, unique hazards and unique loot', () => {
  const mobs = new Set(), profiles = new Set(), specials = new Set(), names = new Set();
  for (const s of sites) {
    const realms = new Set(s.danger.garrison.map(g => g.realm));
    for (const r of ['overworld', 'nether', 'end']) assert.ok(realms.has(r), s.id + ' lacks ' + r);
    assert.equal(s.danger.signature.abilities.length, 2);
    assert.ok(!['ZOMBIE', 'ZOMBIE_VILLAGER'].includes(s.danger.signature.type), s.id);
    mobs.add(s.danger.signature.type + ':' + [...s.danger.signature.abilities].sort().join('+'));
    names.add(s.danger.signature.name);
    assert.equal(s.danger.hazards.length, 2);
    profiles.add(JSON.stringify([s.danger.garrison, s.danger.signature, s.danger.hazards]));
    specials.add(s.special.material + JSON.stringify(s.special.enchants));
    assert.ok(s.special.enchants.length >= 2, s.id);
    assert.ok(s.spots.length >= 4, s.id + ' needs spawn spots');
    assert.ok(s.weight > 0, s.id);
  }
  assert.equal(mobs.size, 139); assert.equal(names.size, 139); assert.equal(profiles.size, 139); assert.equal(specials.size, 139);
});

test('ten bosses, each with an arena, a unique key and a weapon the Apocalypse arsenal defines', () => {
  const bosses = sites.filter(s => s.boss);
  assert.equal(bosses.length, 10);
  assert.equal(new Set(bosses.map(s => s.boss.key)).size, 10);
  const equipment = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'), 'utf8');
  const weapons = fs.readFileSync(path.join(plugin, 'src/chat/jaspr/muse/Weapons.java'), 'utf8');
  for (const s of bosses) {
    assert.ok(Array.isArray(s.bossArena) && s.bossArena.length === 3, s.id);
    assert.ok(s.boss.health > 0 && s.boss.health <= 1024 && s.boss.abilities.length >= 4, s.id);
    assert.ok(!['ZOMBIE', 'HUSK', 'ZOMBIE_VILLAGER'].includes(s.boss.type), s.id);
    assert.match(equipment, new RegExp('melee\\("' + s.boss.weapon + '",'), s.boss.weapon);
    assert.match(weapons, new RegExp('BOSS_WEAPON\\.put\\("' + s.boss.key + '", "' + s.boss.weapon + '"\\)'), s.boss.key);
  }
});

test('the plugin announces itself and /where names the collection', () => {
  const main = fs.readFileSync(path.join(plugin, 'src/chat/jaspr/muse/MusePlugin.java'), 'utf8');
  assert.match(main, /MUSE_MAPS_READY/);
  assert.match(main, /equalsIgnoreCase\("\/where"\)/);
  assert.match(fs.readFileSync(path.join(plugin, 'src/chat/jaspr/muse/Catalog.java'), 'utf8'), /COLLECTION = "Muse\+GLM_Maps"/);
  const yml = fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8');
  assert.match(yml, /depend: \[JasprHorrorBiomes, JasprMuseMapsPack\]/);
});
