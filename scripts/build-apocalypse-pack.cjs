#!/usr/bin/env node
'use strict';

// Optional standalone pack. Static assets can instead be merged into the browser EPK by the lead.
// Never reads or writes assets.epk, server configuration, plugins, or any world directory.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const expansion = require('../apocalypse-pack/arsenal-expansion.cjs');

const project = path.resolve(__dirname, '..');
const source = path.join(project, 'apocalypse-pack');
const defaultOutput = path.join(source, 'build', 'jaspr-apocalypse.zip');
const guns = new Map([
  ...expansion.gunSpecs.map(([id, band]) => [band, 'item/apocalypse_'+id]),
  [1460, 'item/apocalypse_frostbite'],
  [1470, 'item/apocalypse_cyclops'],
  [1480, 'item/apocalypse_adjudicator'],
  [1490, 'item/apocalypse_sunlance'],
  [1500, 'item/apocalypse_longwatch'],
  [1510, 'item/apocalypse_bastion'],
  [1520, 'item/apocalypse_tempest'],
  [1530, 'item/apocalypse_whisper'],
  [1540, 'item/apocalypse_gravebreaker'],
  [1550, 'item/apocalypse_requiem'],
  [1560, 'item/apocalypse_last_light'],
]);
const fallback = 'item/apocalypse_vanilla_diamond_hoe';
const vanillaTextures = new Set([
  'blocks/iron_block', 'blocks/coal_block', 'blocks/gold_block', 'blocks/planks_big_oak',
  'blocks/diamond_block', 'blocks/redstone_block', 'blocks/obsidian',
  'blocks/quartz_block_side', 'blocks/prismarine_rough', 'items/diamond_hoe',
  'items/diamond_sword', 'items/diamond_helmet', 'items/diamond_chestplate', 'items/diamond_leggings', 'items/diamond_boots',
  'items/iron_pickaxe',
]);
const faces = ['north', 'south', 'east', 'west', 'up', 'down'];
const views = ['firstperson_righthand', 'firstperson_lefthand', 'thirdperson_righthand',
  'thirdperson_lefthand', 'gui', 'ground', 'fixed'];

function filesAt(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0)
    .flatMap(entry => {
      const file = path.join(directory, entry.name);
      assert(!entry.isSymbolicLink(), `Symlinks are not pack inputs: ${file}`);
      assert(entry.isDirectory() || entry.isFile(), `Unsupported input: ${file}`);
      return entry.isDirectory() ? filesAt(file) : [file];
    });
}

function triple(value, label, min, max) {
  assert(Array.isArray(value) && value.length === 3, `${label} must have three numbers`);
  for (const component of value) assert(Number.isFinite(component) && component >= min && component <= max, `${label} out of bounds`);
}

function modelPath(reference) {
  assert(/^(minecraft:)?item\/[a-z0-9_]+$/.test(reference), `Invalid model reference: ${reference}`);
  return `assets/minecraft/models/${reference.replace(/^minecraft:/, '')}.json`;
}

function rotate(vector, axis, degrees) {
  const radians = degrees * Math.PI / 180, c = Math.cos(radians), s = Math.sin(radians);
  const [x, y, z] = vector;
  return axis === 'x' ? [x, c * y - s * z, s * y + c * z]
    : axis === 'y' ? [c * x + s * z, y, -s * x + c * z]
    : [c * x - s * y, s * x + c * y, z];
}

function heldVector(vector, transform, left, thirdPerson) {
  const [x, y, z] = transform.rotation, side = left ? -1 : 1;
  // Client ItemCameraTransforms.applyTransformSide calls GL rotations X, Y, Z, so vectors
  // receive Z, Y, X. Left-hand rendering negates model yaw and roll, not pitch.
  let result = rotate(rotate(rotate(vector, 'z', z * side), 'y', y * side), 'x', x);
  if (thirdPerson) {
    // LayerHeldItem: Rx(-90) Ry(180). RenderLivingBase.prepareScale: (-1,-1,1).
    // Include the living-model inversion: cancelling only the hand transform flips verticals.
    result = rotate(rotate(result, 'y', 180), 'x', -90);
    result = [-result[0], -result[1], result[2]];
  }
  return result;
}

function validate() {
  const inputs = [path.join(source, 'pack.mcmeta'), ...filesAt(path.join(source, 'assets'))];
  const entries = inputs.map(file => ({
    name: path.relative(source, file).split(path.sep).join('/'),
    data: fs.readFileSync(file),
  })).sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0);
  const models = new Map();
  for (const entry of entries) {
    assert(entry.name === 'pack.mcmeta' || /^assets\/minecraft\/models\/item\/[a-z0-9_]+\.json$/.test(entry.name),
      `Unexpected pack input: ${entry.name}`);
    const json = JSON.parse(entry.data.toString('utf8'));
    if (entry.name === 'pack.mcmeta') {
      assert.equal(json.pack.pack_format, 3, 'Minecraft 1.12.2 requires pack_format 3');
      assert.equal(typeof json.pack.description, 'string');
    } else models.set(entry.name, json);
  }
  for (const [name, model] of models) {
    if (model.parent) assert(['item/handheld', 'minecraft:item/handheld', 'item/generated'].includes(model.parent), `${name}: unexpected parent`);
    for (const texture of Object.values(model.textures || {})) {
      assert(vanillaTextures.has(texture.replace(/^minecraft:/, '')), `${name}: unverified vanilla texture ${texture}`);
    }
    for (const override of model.overrides || []) assert(models.has(modelPath(override.model)), `Missing override ${override.model}`);
    if (!model.elements) continue;
    const isGun = [...guns.values()].some(reference => modelPath(reference) === name);
    assert(model.elements.length >= (isGun ? 12 : 4) && model.elements.length <= 32, `${name}: geometry budget`);
    for (const [index, element] of model.elements.entries()) {
      const label = `${name} element ${index}`;
      triple(element.from, `${label} from`, -16, 32);
      triple(element.to, `${label} to`, -16, 32);
      assert(element.from.every((value, axis) => value < element.to[axis]), `${label}: zero/inverted extent`);
      assert.deepEqual(Object.keys(element.faces).sort(), [...faces].sort(), `${label}: missing face`);
      for (const face of Object.values(element.faces)) {
        assert(typeof face.texture === 'string' && face.texture.startsWith('#') && model.textures[face.texture.slice(1)], `${label}: missing texture`);
        assert(Array.isArray(face.uv) && face.uv.length === 4 && face.uv.every(value => value >= 0 && value <= 16), `${label}: invalid UV`);
      }
    }
    for (const view of views) {
      assert(model.display[view], `${name}: missing ${view} transform`);
      triple(model.display[view].rotation, `${view} rotation`, -360, 360);
      triple(model.display[view].translation, `${view} translation`, -80, 80);
      triple(model.display[view].scale, `${view} scale`, 0.01, 4);
    }
    const isBlade = /apocalypse_(?:trench_blade|breacher_axe|mono_katana|shock_baton|gravity_maul|reaper_scythe|thermal_machete|sentinel_spear)\.json$/.test(name)
      || expansion.meleeSpecs.some(([id]) => name.endsWith('/apocalypse_'+id+'.json'));
    if (isBlade) for (const hand of views.filter(view => view.includes('person'))) {
      const tip = heldVector([0,1,0], model.display[hand], hand.endsWith('lefthand'), hand.startsWith('third'));
      assert(hand.startsWith('third') ? tip[2] < -.99 && Math.abs(tip[1]) < 1e-6 : tip[1] > .9 && tip[2] < -.4,
        `${name}/${hand}: melee working end must project upright/forward, never grip-first`);
    }
    if (!isGun) continue;
    // Models use -Z for the muzzle, +Z for the shoulder stock and -Y for the grip.
    // Positive first-person X pitch lifts the muzzle; zero yaw keeps it pointing forward.
    for (const hand of ['firstperson_righthand', 'firstperson_lefthand']) {
      assert(model.display[hand].rotation[0] > 0 && model.display[hand].rotation[0] <= 12, `${name}: muzzle pitch`);
      assert.equal(model.display[hand].rotation[1], 0, `${name}: muzzle must point forward`);
    }
    for (const hand of ['thirdperson_righthand', 'thirdperson_lefthand']) {
      assert.deepEqual(model.display[hand].rotation, [90, 0, 0], `${name}: compensate hand transform and living-model inversion`);
    }
    for (const hand of views.filter(view => view.includes('person'))) {
      const left = hand.endsWith('lefthand'), third = hand.startsWith('third');
      const muzzle = heldVector([0, 0, -1], model.display[hand], left, third);
      const grip = heldVector([0, -1, 0], model.display[hand], left, third);
      const sight = heldVector([0, 1, 0], model.display[hand], left, third);
      assert(muzzle[2] < -0.98 && Math.abs(muzzle[0]) < 0.01, `${name}/${hand}: muzzle must face forward`);
      assert(grip[1] < -0.98, `${name}/${hand}: grip must face down`);
      assert(sight[1] > .98, `${name}/${hand}: sights must face up`);
      assert(third ? Math.abs(muzzle[1]) < 1e-6 : muzzle[1] > 0, `${name}/${hand}: muzzle vertical direction`);
    }
  }

  const selector = models.get('assets/minecraft/models/item/diamond_hoe.json');
  assert(selector && selector.overrides.length === guns.size * 2 + 1, 'Expected exact gun damage bands plus damaged fallback');
  const plainHoe = models.get(modelPath(fallback));
  assert(plainHoe && !plainHoe.overrides, 'Fallback must not create an override cycle');
  for (const model of guns.values()) assert(models.has(modelPath(model)), `Missing gun ${model}`);
  // Emulate 1.12 float predicates and last-match precedence, over every legal durability.
  // Ordinary damaged hoes and all unassigned unbreakable values must keep the vanilla model.
  let checked = 0;
  for (let durability = 0; durability <= 1561; durability++) {
    for (const unbreakable of [false, true]) {
      const properties = { damage: Math.fround(durability / 1561), damaged: !unbreakable && durability > 0 ? 1 : 0 };
      let selected = fallback;
      for (const override of selector.overrides) {
        if (Object.entries(override.predicate).every(([key, value]) => properties[key] >= Math.fround(value))) selected = override.model;
      }
      assert.equal(selected, unbreakable && guns.has(durability) ? guns.get(durability) : fallback,
        `Incorrect model at damage=${durability}, unbreakable=${unbreakable}`);
      checked++;
    }
  }
  const java = fs.readFileSync(path.join(project, 'server', 'custom-plugins', 'JasprApocalypse', 'src', 'chat', 'jaspr', 'apocalypse', 'Arsenal.java'), 'utf8');
  for (const [id, durability] of [['rifle', 1560], ['shotgun', 1550], ['railgun', 1540],
      ['whisper',1530], ['tempest',1520], ['bastion',1510], ['longwatch',1500], ['sunlance',1490], ['adjudicator',1480], ['cyclops',1470], ['frostbite',1460],
      ...expansion.gunSpecs.map(([id,band]) => [id,band])]) {
    assert(new RegExp(`\\("${id}",[^\\n]*, ${durability},`).test(java), `Arsenal ${id} durability differs from model selector`);
  }
  const equipmentJava = fs.readFileSync(path.join(project, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'), 'utf8');
  const blades = [...equipmentJava.matchAll(/melee\("([a-z_]+)",[^\n]*?, (\d+),/g)].map(match => [Number(match[2]), match[1]]);
  assert.equal(blades.length, 24, 'Twenty-four distinct melee definitions');
  assert.equal(new Set(blades.map(([band])=>band)).size, 24, 'Unique sword model bands');
  assert.equal(guns.size, 35, 'Thirty-five distinct gun bands');
  const bands = [['diamond_sword', 1561, blades]];
  for (const [part,max] of Object.entries({helmet:363,chestplate:528,leggings:495,boots:429})) {
    bands.push(['diamond_'+part, max, ['bulwark','ranger','spectre','hazmat'].map((set,i)=>[10+i*10,set+'_'+part])]);
  }
  for (const [base,max,definitions] of bands) {
    const selector = models.get(modelPath('item/'+base)), expected = new Map(definitions);
    const fallback = 'item/apocalypse_vanilla_'+base;
    assert.equal(selector.overrides.length, definitions.length*2+1);
    assert(!models.get(modelPath(fallback)).overrides, 'No fallback cycles');
    for (let durability=0;durability<=max;durability++) for(const unbreakable of [false,true]) {
      const properties={damage:Math.fround(durability/max),damaged:!unbreakable&&durability>0?1:0};
      let selected=fallback;
      for(const override of selector.overrides) if(Object.entries(override.predicate).every(([key,value])=>properties[key]>=Math.fround(value))) selected=override.model;
      assert.equal(selected,unbreakable&&expected.has(durability)?'item/apocalypse_'+expected.get(durability):fallback,`${base}: ${durability}/${unbreakable}`);
      checked++;
    }
  }
  const geometry = [...guns.values()].map(reference => JSON.stringify(models.get(modelPath(reference)).elements.map(({from,to})=>({from,to}))));
  assert.equal(new Set(geometry).size, guns.size, 'Every firearm needs its own geometry, not recolors');
  const bladeGeometry = blades.map(([,id])=>JSON.stringify(models.get(modelPath('item/apocalypse_'+id)).elements.map(({from,to})=>({from,to}))));
  assert.equal(new Set(bladeGeometry).size, 24, 'Every melee weapon needs its own geometry, not recolors');
  for (const [name, expected] of expansion.models()) {
    const actual = models.get('assets/minecraft/models/item/'+name);
    assert.deepEqual(actual, expected, `${name}: checked-in geometry differs from cuboid source`);
  }
  for (const [id,band] of expansion.gunSpecs) {
    assert(band > 0 && band < 1460, 'New firearm band must not replace existing gear');
    const model = models.get(modelPath('item/apocalypse_'+id));
    const bore=model.elements.find(e=>e.__comment==='muzzle bore facing negative Z');
    const grip=model.elements.find(e=>e.__comment==='grip below receiver');
    const sight=model.elements.find(e=>e.__comment==='front sight above bore');
    assert(bore.to[2]<grip.from[2] && sight.from[1]>=bore.to[1] && grip.to[1]<=bore.from[1],
      `${id}: physical muzzle/sight/grip geometry disagrees with tested axes`);
  }
  return { entries, checked, models: models.size, heldOrientationCases: guns.size*4, meleeOrientationCases: blades.length*4 };
}

const crcTable = Array.from({ length: 256 }, (_, value) => {
  for (let i = 0; i < 8; i++) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});

function crc32(data) {
  let value = 0xffffffff;
  for (const byte of data) value = crcTable[(value ^ byte) & 255] ^ (value >>> 8);
  return (value ^ 0xffffffff) >>> 0;
}

function zip(entries) {
  // ZIP STORE avoids platform/zlib version differences; sorted paths and a fixed DOS epoch make
  // identical inputs byte-identical. These JSON-only packs are tiny without compression.
  const localParts = [], directoryParts = [];
  let offset = 0;
  for (const entry of entries) {
    const name = Buffer.from(entry.name, 'utf8');
    const crc = crc32(entry.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(33, 12); // 1980-01-01, time 00:00:00
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(entry.data.length, 18);
    local.writeUInt32LE(entry.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    localParts.push(local, name, entry.data);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(33, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(entry.data.length, 20);
    central.writeUInt32LE(entry.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    directoryParts.push(central, name);
    offset += local.length + name.length + entry.data.length;
  }
  const directory = Buffer.concat(directoryParts);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...localParts, directory, end]);
}

function main(args) {
  let output = defaultOutput, checkOnly = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--check') checkOnly = true;
    else if (args[i] === '--output' && args[i + 1]) output = path.resolve(args[++i]);
    else throw new Error('Usage: node scripts/build-apocalypse-pack.cjs [--check] [--output path.zip]');
  }
  assert.equal(path.extname(output).toLowerCase(), '.zip', 'Output must be a .zip file');
  assert(!output.toLowerCase().includes('assets.epk'), 'EPK output is outside this builder');
  const result = validate();
  const archive = zip(result.entries);
  assert(archive.equals(zip(result.entries)), 'ZIP generation is not deterministic');
  const sha1 = crypto.createHash('sha1').update(archive).digest('hex');
  if (!checkOnly) {
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, archive);
    fs.writeFileSync(output + '.sha1', sha1 + '\n', 'utf8');
  }
  console.log(JSON.stringify({ mode: checkOnly ? 'validated; no files written' : 'built',
    models: result.models, durabilityCases: result.checked, heldOrientationCases: result.heldOrientationCases,
    meleeOrientationCases: result.meleeOrientationCases,
    entries: result.entries.map(entry => entry.name),
    bytes: archive.length, sha1, ...(checkOnly ? {} : { output }) }, null, 2));
}

if (require.main === module) main(process.argv.slice(2));
module.exports = { validate, zip, crc32, heldVector };
