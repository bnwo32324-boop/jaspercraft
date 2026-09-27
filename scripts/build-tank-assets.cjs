'use strict';
// JasprTanks models: code-native vehicle parts worn as helmets by two invisible marker armor stands that
// every client seats on the driver (passenger height 1.35 above the feet). The iron axe carries them on
// unbreakable damage bands 1 (tank hull), 2 (tank turret), 3 (Orbital Sentinel drone) and 4 (sentinel sensor
// pod); ordinary and damaged axes keep the vanilla look.
// Writes the pack under server/custom-plugins/JasprTanks/pack and a candidate client archive
// candidate/tanks/assets.epk that differs from site/assets.epk only in these four item models.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {merge} = require('./merge-apocalypse-assets.cjs');
const zfight = require('../apocalypse-pack/zfight.cjs');

const root = path.resolve(__dirname, '..');
const pack = path.join(root, 'server', 'custom-plugins', 'JasprTanks', 'pack');
const itemDir = path.join(pack, 'assets', 'minecraft', 'models', 'item');
const MAX = 251; // band thresholds sit just below band/250 (the iron axe's durability), like the sentry selector

// A full-size armor stand renders its helmet at 4x (the display clamp) and 0.625 layer scale: 6.4 model
// units per block. GROUND is the model Y of the driver's feet with the stand seated 1.35 above them.
const U = 6.4, GROUND = -11.44;
const textures = {
  particle: 'blocks/hardened_clay_stained_green',
  olive: 'blocks/hardened_clay_stained_green',
  dark: 'blocks/coal_block',
  steel: 'blocks/iron_block',
  lamp: 'blocks/glowstone',
  wood: 'blocks/planks_big_oak',
  bore: 'blocks/obsidian',
};
const droneTextures = {
  particle: 'blocks/iron_block',
  steel: 'blocks/iron_block',
  dark: 'blocks/coal_block',
  rotor: 'blocks/obsidian',
  stripe: 'blocks/lapis_block',
  thruster: 'blocks/sea_lantern',
  lamp: 'blocks/redstone_block',
};
const round = v => Math.round(v * 100) / 100;
const faces = ['north', 'south', 'east', 'west', 'up', 'down'];

/** Cuboid in blocks: x right, y up from the driver's feet, z toward the rear (the barrel points to -z). */
function box(comment, from, to, texture) {
  const f = [round(8 + from[0] * U), round(GROUND + from[1] * U), round(8 + from[2] * U)];
  const t = [round(8 + to[0] * U), round(GROUND + to[1] * U), round(8 + to[2] * U)];
  const size = [0, 1, 2].map(a => Math.min(16, Math.max(1, round(t[a] - f[a]))));
  const uv = {north: [size[0], size[1]], south: [size[0], size[1]], east: [size[2], size[1]], west: [size[2], size[1]],
    up: [size[0], size[2]], down: [size[0], size[2]]};
  return {__comment: comment, from: f, to: t,
    faces: Object.fromEntries(faces.map(face => [face, {uv: [0, 0, uv[face][0], uv[face][1]], texture: '#' + texture}]))};
}

const transform = (rotation, translation, scale) => ({rotation, translation, scale: [scale, scale, scale]});
function display() {
  // Only the head transform is seen in play; the rest keep a stray item legible.
  const lift = [0, 5.2, 0];
  return {
    head: transform([0, 0, 0], [0, 0, 0], 4),
    gui: transform([30, 225, 0], lift, 0.4),
    ground: transform([0, 0, 0], lift, 0.3),
    fixed: transform([0, 180, 0], lift, 0.4),
    thirdperson_righthand: transform([75, 45, 0], lift, 0.3),
    thirdperson_lefthand: transform([75, 45, 0], lift, 0.3),
    firstperson_righthand: transform([0, 45, 0], lift, 0.3),
    firstperson_lefthand: transform([0, 225, 0], lift, 0.3),
  };
}

function hull() {
  const elements = [
    box('left track', [-0.62, 0, -0.8], [-0.33, 0.4, 0.8], 'dark'),
    box('right track', [0.33, 0, -0.8], [0.62, 0.4, 0.8], 'dark'),
    box('left track guard', [-0.66, 0.4, -0.84], [-0.3, 0.46, 0.84], 'olive'),
    box('right track guard', [0.3, 0.4, -0.84], [0.66, 0.46, 0.84], 'olive'),
    box('hull body', [-0.33, 0.08, -0.72], [0.33, 0.5, 0.74], 'olive'),
    box('upper deck', [-0.5, 0.46, -0.6], [0.5, 0.62, 0.7], 'olive'),
    box('front glacis', [-0.45, 0.16, -0.84], [0.45, 0.46, -0.72], 'olive'),
    box('left headlight', [-0.42, 0.36, -0.9], [-0.3, 0.46, -0.84], 'lamp'),
    box('right headlight', [0.3, 0.36, -0.9], [0.42, 0.46, -0.84], 'lamp'),
    box('rear exhaust', [-0.35, 0.3, 0.74], [-0.2, 0.45, 0.82], 'dark'),
    box('left front road wheel', [-0.65, 0.06, -0.6], [-0.62, 0.3, -0.36], 'steel'),
    box('left middle road wheel', [-0.65, 0.06, -0.12], [-0.62, 0.3, 0.12], 'steel'),
    box('left rear road wheel', [-0.65, 0.06, 0.36], [-0.62, 0.3, 0.6], 'steel'),
    box('right front road wheel', [0.62, 0.06, -0.6], [0.65, 0.3, -0.36], 'steel'),
    box('right middle road wheel', [0.62, 0.06, -0.12], [0.65, 0.3, 0.12], 'steel'),
    box('right rear road wheel', [0.62, 0.06, 0.36], [0.65, 0.3, 0.6], 'steel'),
  ];
  return {__comment: 'JasprTanks hull / iron axe band 1 / tracks, olive hull, headlights', ambientocclusion: true,
    textures, display: display(), elements: zfight.separate(elements)};
}

function turret() {
  const elements = [
    box('turret body', [-0.48, 0.62, -0.42], [0.48, 1.0, 0.5], 'olive'),
    box('hatch ring front', [-0.3, 1.0, -0.3], [0.3, 1.06, -0.24], 'steel'),
    box('hatch ring back', [-0.3, 1.0, 0.24], [0.3, 1.06, 0.3], 'steel'),
    box('hatch ring left', [-0.3, 1.0, -0.24], [-0.24, 1.06, 0.24], 'steel'),
    box('hatch ring right', [0.24, 1.0, -0.24], [0.3, 1.06, 0.24], 'steel'),
    box('gun mantlet', [-0.22, 0.68, -0.54], [0.22, 0.94, -0.42], 'dark'),
    box('barrel facing negative Z', [-0.065, 0.78, -1.5], [0.065, 0.91, -0.54], 'steel'),
    box('muzzle brake', [-0.1, 0.75, -1.62], [0.1, 0.94, -1.46], 'dark'),
    box('bore', [-0.04, 0.805, -1.63], [0.04, 0.885, -1.62], 'bore'),
    box('antenna', [0.36, 1.0, 0.36], [0.39, 1.7, 0.39], 'steel'),
    box('stowage crate', [-0.44, 0.7, 0.5], [0.2, 0.9, 0.6], 'wood'),
    box('left smoke launcher', [-0.56, 0.78, -0.3], [-0.48, 0.9, -0.12], 'dark'),
    box('right smoke launcher', [0.48, 0.78, -0.3], [0.56, 0.9, -0.12], 'dark'),
  ];
  return {__comment: 'JasprTanks turret / iron axe band 2 / hatch, barrel toward -Z', ambientocclusion: true,
    textures, display: display(), elements: zfight.separate(elements)};
}

function drone() {
  const elements = [
    box('pod shell', [-0.45, 0.15, -0.45], [0.45, 0.8, 0.45], 'steel'),
    box('pod skirt', [-0.5, 0.35, -0.5], [0.5, 0.55, 0.5], 'dark'),
    box('stripe ring', [-0.46, 0.62, -0.46], [0.46, 0.66, 0.46], 'stripe'),
    box('thruster', [-0.2, 0, -0.2], [0.2, 0.15, 0.2], 'thruster'),
    box('status light facing negative Z', [-0.06, 0.66, -0.5], [0.06, 0.74, -0.46], 'lamp'),
    box('arm north', [-0.06, 0.62, -1.1], [0.06, 0.7, -0.5], 'dark'),
    box('arm south', [-0.06, 0.62, 0.5], [0.06, 0.7, 1.1], 'dark'),
    box('arm west', [-1.1, 0.62, -0.06], [-0.5, 0.7, 0.06], 'dark'),
    box('arm east', [0.5, 0.62, -0.06], [1.1, 0.7, 0.06], 'dark'),
    box('hub north', [-0.1, 0.7, -1.2], [0.1, 0.8, -1.0], 'steel'),
    box('hub south', [-0.1, 0.7, 1.0], [0.1, 0.8, 1.2], 'steel'),
    box('hub west', [-1.2, 0.7, -0.1], [-1.0, 0.8, 0.1], 'steel'),
    box('hub east', [1.0, 0.7, -0.1], [1.2, 0.8, 0.1], 'steel'),
    box('rotor north', [-0.35, 0.8, -1.45], [0.35, 0.83, -0.75], 'rotor'),
    box('rotor south', [-0.35, 0.8, 0.75], [0.35, 0.83, 1.45], 'rotor'),
    box('rotor west', [-1.45, 0.8, -0.35], [-0.75, 0.83, 0.35], 'rotor'),
    box('rotor east', [0.75, 0.8, -0.35], [1.45, 0.83, 0.35], 'rotor'),
  ];
  return {__comment: 'JasprTanks Orbital Sentinel drone / iron axe band 3 / pod, four rotors', ambientocclusion: true,
    textures: droneTextures, display: display(), elements: zfight.separate(elements)};
}

function pod() {
  const elements = [
    box('sensor turret', [-0.18, 0.02, -0.62], [0.18, 0.2, -0.35], 'dark'),
    box('sensor eye facing negative Z', [-0.08, 0.06, -0.66], [0.08, 0.16, -0.62], 'lamp'),
    box('left strike rail', [-0.3, 0.08, -0.7], [-0.22, 0.14, -0.2], 'steel'),
    box('right strike rail', [0.22, 0.08, -0.7], [0.3, 0.14, -0.2], 'steel'),
    box('antenna', [0.3, 0.8, 0.3], [0.33, 1.3, 0.33], 'steel'),
    box('antenna tip', [0.29, 1.3, 0.29], [0.34, 1.35, 0.34], 'lamp'),
  ];
  return {__comment: 'JasprTanks Orbital Sentinel sensor pod / iron axe band 4 / red eye toward -Z', ambientocclusion: true,
    textures: droneTextures, display: display(), elements: zfight.separate(elements)};
}

function selector() {
  return {
    parent: 'item/handheld',
    textures: {layer0: 'items/iron_axe'},
    overrides: [
      {predicate: {damaged: 0, damage: round4(1 / MAX)}, model: 'item/jaspr_tank_hull'},
      {predicate: {damaged: 0, damage: round4(2 / MAX)}, model: 'item/jaspr_tank_turret'},
      {predicate: {damaged: 0, damage: round4(3 / MAX)}, model: 'item/jaspr_sentinel_drone'},
      {predicate: {damaged: 0, damage: round4(4 / MAX)}, model: 'item/jaspr_sentinel_pod'},
      {predicate: {damaged: 0, damage: round4(5 / MAX)}, model: 'item/jaspr_vanilla_iron_axe'},
      {predicate: {damaged: 1}, model: 'item/jaspr_vanilla_iron_axe'},
    ],
  };
}
function round4(v) { return Math.floor(v * 1e6) / 1e6; }

function models() {
  return {
    'iron_axe.json': selector(),
    'jaspr_tank_hull.json': hull(),
    'jaspr_tank_turret.json': turret(),
    'jaspr_sentinel_drone.json': drone(),
    'jaspr_sentinel_pod.json': pod(),
    'jaspr_vanilla_iron_axe.json': {parent: 'item/handheld', textures: {layer0: 'items/iron_axe'}},
  };
}

function validate(all) {
  for (const [name, model] of Object.entries(all)) {
    if (!model.elements) continue;
    assert.ok(model.elements.length >= 4 && model.elements.length <= 32, name + ': geometry budget');
    assert.equal(zfight.conflicts(model.elements).length, 0, name + ': z-fighting faces');
    for (const e of model.elements) {
      for (const v of [...e.from, ...e.to]) assert.ok(v >= -16 && v <= 32, name + ': ' + e.__comment + ' outside -16..32');
      assert.ok(e.from.every((v, a) => v < e.to[a]), name + ': ' + e.__comment + ' has no volume');
    }
    assert.deepEqual(model.display.head.scale, [4, 4, 4]);
  }
  // Bands the plugin uses (TanksPlugin.HULL_BAND / TURRET_BAND) must select the two tank models.
  const pick = damage => { let m = null; for (const o of all['iron_axe.json'].overrides) {
    const p = o.predicate; if ((p.damaged ?? 0) === 0 && damage / 250 >= (p.damage ?? 0)) m = o.model; } return m; };
  assert.equal(pick(1), 'item/jaspr_tank_hull');
  assert.equal(pick(2), 'item/jaspr_tank_turret');
  assert.equal(pick(3), 'item/jaspr_sentinel_drone');
  assert.equal(pick(4), 'item/jaspr_sentinel_pod');
  assert.equal(pick(5), 'item/jaspr_vanilla_iron_axe');
  assert.equal(pick(0), null, 'a fresh axe keeps the base model');
}

function write() {
  const all = models();
  validate(all);
  fs.mkdirSync(itemDir, {recursive: true});
  for (const [name, model] of Object.entries(all)) fs.writeFileSync(path.join(itemDir, name), JSON.stringify(model, null, 2) + '\n');
  return all;
}

if (require.main === module) {
  write();
  const source = process.env.TANK_ASSETS_SOURCE || path.join(root, 'site', 'assets.epk');
  const input = fs.readFileSync(source);
  const result = merge(input, pack);
  const target = path.join(root, 'candidate', 'tanks');
  fs.mkdirSync(target, {recursive: true});
  fs.writeFileSync(path.join(target, 'assets.epk'), result.output);
  const report = {models: result.models, unchangedEntries: result.unchangedEntries, beforeBytes: input.length,
    afterBytes: result.output.length, sha256: crypto.createHash('sha256').update(result.output).digest('hex')};
  fs.writeFileSync(path.join(target, 'asset-merge-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {models, validate, U, GROUND};
