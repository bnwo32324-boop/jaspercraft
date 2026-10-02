'use strict';
// Sentry turret upgrade models (owner, 2026-10-02: "Turret upgrade tree ... it should also change the look of the turret
// itself. The model should change with each upgrade."). Each upgrade tier is its own cuboid model on the tracking head:
// an unbreakable iron pickaxe whose damage value picks the model (100 = the original sentry, 101-107 the upgrades).
// The base plate, mount column and collar are the original's, so every tier sits flush on the dispenser; the working
// end always points to -Z (the head turns it toward the target). Run `node apocalypse-pack/sentry-upgrades.cjs` to
// (re)write the models and the iron_pickaxe selector; scripts/build-apocalypse-pack.cjs validates them.
const fs = require('node:fs');
const path = require('node:path');

const dir = path.join(__dirname, 'assets', 'minecraft', 'models', 'item');
const MAX = 251;

/** id, model damage, name: the tiers in the order of their damage values (SentryTurret.Tier must agree). */
const TIERS = [
  ['sentry', 100, 'apocalypse_sentry_turret'],
  ['reinforced', 101, 'apocalypse_sentry_reinforced'],
  ['gatling', 102, 'apocalypse_sentry_gatling'],
  ['storm_gatling', 103, 'apocalypse_sentry_storm_gatling'],
  ['cannon', 104, 'apocalypse_sentry_cannon'],
  ['howitzer', 105, 'apocalypse_sentry_howitzer'],
  ['tesla', 106, 'apocalypse_sentry_tesla'],
  ['arc_tower', 107, 'apocalypse_sentry_arc_tower'],
];

const TEXTURES = {
  particle: 'blocks/iron_block', steel: 'blocks/iron_block', dark: 'blocks/coal_block', red: 'blocks/redstone_block',
  gold: 'blocks/gold_block', blue: 'blocks/diamond_block', black: 'blocks/obsidian', wood: 'blocks/planks_big_oak',
};

function box(comment, from, to, texture, sides = {}) {
  const faces = {};
  for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) faces[f] = {uv: [0, 0, 16, 16], texture: '#' + (sides[f] || texture)};
  return {__comment: comment, from, to, faces};
}

// The original sentry's mount: identical in every tier.
const MOUNT = [
  box('base plate', [1.25, -3.5, 1.25], [14.75, -0.5, 14.75], 'steel', {down: 'dark'}),
  box('mount column', [5.75, -0.5, 5.75], [10.25, 5.5, 10.25], 'dark'),
  box('traverse collar', [4.25, 5.5, 4.25], [11.75, 7.75, 11.75], 'steel', {up: 'gold', down: 'dark'}),
];

const HEADS = {
  reinforced: [
    box('sensor dome', [4.5, 7.75, 3], [11.5, 13.5, 12], 'steel', {down: 'dark'}),
    box('armour cheek left', [3, 8, 2.5], [4.5, 12.5, 11], 'gold'),
    box('armour cheek right', [11.5, 8, 2.5], [13, 12.5, 11], 'gold'),
    box('barrel facing negative Z', [6.625, 9, -9], [9.375, 11.75, 3], 'dark'),
    box('barrel shroud', [6.25, 8.625, -4], [9.75, 12.125, 0.5], 'steel'),
    box('muzzle brake', [5.875, 8.25, -10.75], [10.125, 12.5, -9], 'steel', {north: 'dark'}),
    box('targeting eye', [6.75, 12, 2.85], [9.25, 13, 3.15], 'red'),
    box('sight rail', [7.25, 13.5, 4.5], [8.75, 14.625, 10.5], 'gold'),
    box('rank chevron', [5.5, 13.5, 6], [6.75, 13.875, 9], 'gold'),
    box('signal mast', [7.625, 14.625, 7.625], [8.375, 19, 8.375], 'dark'),
    box('mast beacon', [7.25, 19, 7.25], [8.75, 20.125, 8.75], 'red'),
    box('rear cooling vent', [5.5, 8.5, 12], [10.5, 12, 12.75], 'dark'),
  ],
  gatling: [
    box('sensor dome', [5, 7.75, 6], [11, 12.5, 12], 'steel', {down: 'dark'}),
    box('barrel housing', [5.25, 8.25, 2], [10.75, 13.25, 6], 'dark'),
    box('barrel axle facing negative Z', [7.375, 10.375, -8], [8.625, 11.625, 2], 'steel'),
    box('barrel top', [7.375, 11.875, -9], [8.625, 13.125, 2], 'dark'),
    box('barrel bottom', [7.375, 8.875, -9], [8.625, 10.125, 2], 'dark'),
    box('barrel left', [5.875, 10.375, -9], [7.125, 11.625, 2], 'dark'),
    box('barrel right', [8.875, 10.375, -9], [10.125, 11.625, 2], 'dark'),
    box('front clamp', [5.5, 8.5, -6.5], [10.5, 13.5, -5.5], 'steel'),
    box('muzzle clamp', [5.5, 8.5, -9.75], [10.5, 13.5, -9], 'steel', {north: 'dark'}),
    box('drum magazine', [10.75, 8, 3], [13.75, 12.25, 9], 'gold'),
    box('targeting lamp', [7.25, 13.25, 3], [8.75, 14, 4.5], 'red'),
    box('spin motor', [6.5, 9.5, 12], [9.5, 12, 13.5], 'dark'),
  ],
  storm_gatling: [
    box('barrel housing', [3, 8.5, 2], [13, 13.5, 6.5], 'dark'),
    box('sensor dome', [4.5, 7.75, 6.5], [11.5, 12.75, 12.5], 'steel', {down: 'dark'}),
    ...[5.5, 10.5].flatMap((cx, i) => {
      const side = i === 0 ? 'left' : 'right';
      return [
        box('barrel ' + side + ' top', [cx - 0.625, 11.625, -10], [cx + 0.625, 12.875, 2], 'dark'),
        box('barrel ' + side + ' lower inner', [cx - 1.725, 9.475, -10], [cx - 0.475, 10.725, 2], 'dark'),
        box('barrel ' + side + ' lower outer', [cx + 0.475, 9.475, -10], [cx + 1.725, 10.725, 2], 'dark'),
        box('front clamp ' + side, [cx - 2.25, 8.75, -7], [cx + 2.25, 13.25, -6], 'steel'),
        box('muzzle ring ' + side, [cx - 2.25, 8.75, -10.75], [cx + 2.25, 13.25, -10], 'steel', {north: 'dark'}),
        box('cooling jacket ' + side, [cx - 2, 9, -4.5], [cx + 2, 13, -0.5], 'steel', {north: 'red', south: 'red'}),
      ];
    }),
    box('drum magazine left', [0.75, 7.75, 3.5], [3, 12.25, 10], 'gold'),
    box('drum magazine right', [13, 7.75, 3.5], [15.25, 12.25, 10], 'gold'),
    box('targeting lamp', [7.25, 13.5, 3], [8.75, 14.25, 4.5], 'red'),
    box('exhaust vent left', [5.5, 9, 12.5], [7, 11.5, 13], 'red'),
    box('exhaust vent right', [9, 9, 12.5], [10.5, 11.5, 13], 'red'),
  ],
  cannon: [
    box('breech block', [4.5, 7.75, 3], [11.5, 13, 11.5], 'dark'),
    box('barrel facing negative Z', [5.5, 8.5, -8.5], [10.5, 12.875, 3], 'steel'),
    box('bore', [6.5, 9.5, -8.75], [9.5, 12, -8.5], 'dark'),
    box('reinforcing band front', [5.25, 8.25, -6], [10.75, 13.125, -5], 'gold'),
    box('reinforcing band rear', [5.25, 8.25, -1], [10.75, 13.125, 0], 'gold'),
    box('recoil spring left', [3.25, 9, 1], [4.5, 10.25, 10], 'gold'),
    box('recoil spring right', [11.5, 9, 1], [12.75, 10.25, 10], 'gold'),
    box('gun shield', [2.5, 7.75, 1.75], [13.5, 14, 2.5], 'steel'),
    box('targeting lamp', [10.75, 13, 4], [11.5, 14, 6], 'red'),
    box('ammunition crate', [5.5, 7.75, 11.5], [10.5, 11.5, 13.5], 'wood'),
  ],
  howitzer: [
    box('breech block', [4, 7.75, 2.5], [12, 13.5, 12], 'dark'),
    box('barrel facing negative Z', [5.25, 8.25, -11], [10.75, 13.25, 2.5], 'steel'),
    box('muzzle brake', [4.5, 7.5, -13], [11.5, 14, -11], 'black'),
    box('bore', [6.25, 9.25, -13.25], [9.75, 12.25, -13], 'dark'),
    box('band front', [5, 8, -9], [11, 13.5, -8], 'gold'),
    box('band middle', [5, 8, -5], [11, 13.5, -4], 'gold'),
    box('band rear', [5, 8, -1], [11, 13.5, 0], 'gold'),
    box('recoil cylinder left', [2.75, 8.75, 0], [4, 10.25, 11], 'steel', {north: 'gold'}),
    box('recoil cylinder right', [12, 8.75, 0], [13.25, 10.25, 11], 'steel', {north: 'gold'}),
    box('shield wing left', [1.5, 7.75, 0.75], [5, 14.5, 1.75], 'steel'),
    box('shield wing right', [11, 7.75, 0.75], [14.5, 14.5, 1.75], 'steel'),
    box('shell crate left', [4.5, 7.75, 12], [7.75, 11, 14], 'wood'),
    box('shell crate right', [8.25, 7.75, 12], [11.5, 11, 14], 'wood'),
    box('targeting lamp', [10.75, 13.5, 3.5], [11.75, 14.5, 5.5], 'red'),
    box('loading hatch', [6, 13.5, 6], [10, 14.25, 10], 'black'),
  ],
  tesla: [
    box('coil base', [5, 7.75, 5], [11, 9.5, 11], 'dark'),
    box('coil core', [7, 9.5, 7], [9, 17.5, 9], 'steel'),
    box('coil ring 1', [5.5, 10.25, 5.5], [10.5, 11, 10.5], 'gold'),
    box('coil ring 2', [5.75, 11.75, 5.75], [10.25, 12.5, 10.25], 'steel'),
    box('coil ring 3', [6, 13.25, 6], [10, 14, 10], 'gold'),
    box('coil ring 4', [6.25, 14.75, 6.25], [9.75, 15.5, 9.75], 'steel'),
    box('storm orb', [6, 17.5, 6], [10, 21.5, 10], 'blue'),
    box('emitter rod facing negative Z', [7.5, 19, -5], [8.5, 20, 6], 'gold'),
    box('emitter tip', [7, 18.5, -6.5], [9, 20.5, -5], 'blue'),
    box('targeting eye', [7, 8, 4.75], [9, 9, 5], 'red'),
    box('capacitor left', [3, -0.5, 6], [5, 11, 10], 'red'),
    box('capacitor right', [11, -0.5, 6], [13, 11, 10], 'red'),
  ],
  arc_tower: [
    box('coil base', [4.5, 7.75, 4.5], [11.5, 9.5, 11.5], 'dark'),
    box('coil core', [7, 9.5, 7], [9, 21, 9], 'steel'),
    ...[[10.25, 2.75, 'gold'], [11.75, 2.6, 'steel'], [13.25, 2.45, 'gold'], [14.75, 2.3, 'steel'], [16.25, 2.15, 'gold'], [17.75, 2, 'steel']]
      .map(([y, hw, t], i) => box('coil ring ' + (i + 1), [8 - hw, y, 8 - hw], [8 + hw, y + 0.75, 8 + hw], t)),
    box('storm orb', [5.5, 21, 5.5], [10.5, 26, 10.5], 'blue'),
    box('emitter fork left facing negative Z', [6, 22.75, -5.5], [7, 23.75, 5.5], 'gold'),
    box('emitter fork right', [9, 22.75, -5.5], [10, 23.75, 5.5], 'gold'),
    box('fork tip left', [5.75, 22.5, -7], [7.25, 24, -5.5], 'blue'),
    box('fork tip right', [8.75, 22.5, -7], [10.25, 24, -5.5], 'blue'),
    box('pylon left', [2.75, -0.5, 7], [4.25, 14, 9], 'red'),
    box('pylon right', [11.75, -0.5, 7], [13.25, 14, 9], 'red'),
    box('pylon cap left', [2.5, 14, 6.75], [4.5, 15.5, 9.25], 'blue'),
    box('pylon cap right', [11.5, 14, 6.75], [13.5, 15.5, 9.25], 'blue'),
    box('targeting eye', [7, 8, 4.25], [9, 9, 4.5], 'red'),
  ],
};

function display(original) { return JSON.parse(JSON.stringify(original.display)); }

function models() {
  const original = JSON.parse(fs.readFileSync(path.join(dir, 'apocalypse_sentry_turret.json'), 'utf8'));
  const out = {};
  for (const [id, , name] of TIERS) {
    if (id === 'sentry') continue;
    const used = {};
    const elements = [...MOUNT, ...HEADS[id]];
    for (const e of elements) for (const f of Object.values(e.faces)) used[f.texture.slice(1)] = true;
    const textures = {particle: TEXTURES.particle};
    for (const key of Object.keys(TEXTURES)) if (used[key]) textures[key] = TEXTURES[key];
    out[name] = {ambientocclusion: true, textures, display: display(original), elements};
  }
  return out;
}

/** The iron_pickaxe selector: each tier's damage value picks its model; everything else stays a vanilla pickaxe. */
function selector() {
  const overrides = TIERS.map(([, damage, name]) => ({predicate: {damaged: 0, damage: damage / MAX}, model: 'item/' + name}));
  overrides.push({predicate: {damaged: 0, damage: (TIERS[TIERS.length - 1][1] + 1) / MAX}, model: 'item/apocalypse_vanilla_iron_pickaxe'});
  overrides.push({predicate: {damaged: 1}, model: 'item/apocalypse_vanilla_iron_pickaxe'});
  return {parent: 'item/handheld', textures: {layer0: 'items/iron_pickaxe'}, overrides};
}

function write() {
  for (const [name, model] of Object.entries(models())) fs.writeFileSync(path.join(dir, name + '.json'), JSON.stringify(model, null, 2) + '\n');
  fs.writeFileSync(path.join(dir, 'iron_pickaxe.json'), JSON.stringify(selector(), null, 2) + '\n');
}

module.exports = {TIERS, MAX, models, selector, write};
if (require.main === module) { write(); console.log('wrote ' + (TIERS.length - 1) + ' sentry upgrade models and the iron_pickaxe selector'); }
