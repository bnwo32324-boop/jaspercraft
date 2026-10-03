'use strict';
// Adds the realm armoury's 54 recipes (JasprGear 5.0.0) to client-mods/recipe-table.json, the recipe panel's export, from
// the gear catalogue GearExport writes (candidate/gear/gear-catalog.json): emerald pieces in the vanilla shapes from
// emerald blocks and sticks; every other set as its diamond piece ringed by the realm's materials (corners C, edges E).
// Idempotent: earlier "jasprgear:armory_*" recipes are replaced; choices already in the table are reused. Run
// scripts/sync-recipe-table.cjs afterwards to regenerate the panel module.
//   node scripts/sync-armory-recipes.cjs [catalogue.json]
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const TABLE = path.join(ROOT, 'client-mods', 'recipe-table.json');
const SHAPES = {helmet: ['XXX', 'X X'], chestplate: ['X X', 'XXX', 'XXX'], leggings: ['XXX', 'X X', 'X X'], boots: ['X X', 'X X'],
  sword: ['X', 'X', 'S'], axe: ['XX', 'XS', ' S'], pickaxe: ['XXX', ' S ', ' S '], shovel: ['X', 'S', 'S'], hoe: ['XX', ' S', ' S']};
const FORGE = {blazeforged: ['blaze_rod', 'magma_cream'], void: ['shulker_shell', 'chorus_fruit_popped'],
  abyssal: ['abyssal_pearl'], titan: ['titan_shard'], liminal: ['liminal_fragment']};
const NAMES = {emerald_block: 'Block of Emerald', stick: 'Stick', blaze_rod: 'Blaze Rod', magma_cream: 'Magma Cream',
  shulker_shell: 'Shulker Shell', chorus_fruit_popped: 'Popped Chorus Fruit'};
const BASE = {helmet: 'diamond_helmet', chestplate: 'diamond_chestplate', leggings: 'diamond_leggings', boots: 'diamond_boots',
  sword: 'diamond_sword', axe: 'diamond_axe', pickaxe: 'diamond_pickaxe', shovel: 'diamond_shovel', hoe: 'diamond_hoe'};

function title(id) { return id.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' '); }

function sync(catalog, table) {
  const vanilla = (id, max) => ({snbt: '{id:"minecraft:' + id + '",Count:1b,Damage:0s}', id: 'minecraft:' + id, data: 0,
    name: NAMES[id] || title(id), max});
  const materials = new Map((catalog.armoryMaterials || []).map(m => [m.id, m]));
  const choice = option => {
    let i = table.choices.findIndex(c => c.length === 1 && c[0].snbt === option.snbt);
    if (i < 0) { table.choices.push([option]); i = table.choices.length - 1; }
    return i;
  };
  const ingredient = id => {
    const m = materials.get(id);
    if (!m) return choice(vanilla(id, 64));
    const base = /id:"(minecraft:[a-z_]+)"/.exec(m.snbt)[1];
    return choice({snbt: m.snbt, id: base, data: 0, name: m.title, max: 64});
  };
  table.recipes = table.recipes.filter(r => !/^jasprgear:armory_/.test(r.key));
  let added = 0;
  for (const item of catalog.armory) {
    const recipe = {key: 'jasprgear:armory_' + item.id, type: 'shaped'};
    if (item.set === 'emerald') {
      const shape = SHAPES[item.piece];
      recipe.w = Math.max(...shape.map(r => r.length));
      recipe.h = shape.length;
      recipe.cells = [];
      for (const row of shape) for (let x = 0; x < recipe.w; x++) {
        const c = row[x] || ' ';
        recipe.cells.push(c === 'X' ? ingredient('emerald_block') : c === 'S' ? ingredient('stick') : -1);
      }
    } else {
      const [corner, edge] = FORGE[item.set].length === 2 ? FORGE[item.set] : [FORGE[item.set][0], FORGE[item.set][0]];
      const C = ingredient(corner), E = ingredient(edge), D = choice(vanilla(BASE[item.piece], 1));
      recipe.w = 3; recipe.h = 3; recipe.cells = [C, E, C, E, D, E, C, E, C];
    }
    recipe.result = item.snbt;
    recipe.count = 1;
    recipe.title = item.title;
    recipe.tab = item.armour || item.piece === 'sword' || item.piece === 'axe' ? 'combat' : 'tools';
    table.recipes.push(recipe);
    added++;
  }
  return added;
}

if (require.main === module) {
  const catalogFile = process.argv[2] || path.join(ROOT, 'candidate', 'gear', 'gear-catalog.json');
  const catalog = JSON.parse(fs.readFileSync(catalogFile, 'utf8'));
  if (!Array.isArray(catalog.armory) || catalog.armory.length !== 54) throw new Error('catalogue without the 54 armoury pieces: ' + catalogFile);
  const table = JSON.parse(fs.readFileSync(TABLE, 'utf8'));
  const added = sync(catalog, table);
  fs.writeFileSync(TABLE, JSON.stringify(table));   // the export's own compact, single-line form
  console.log('armoury recipes: ' + added + '; table: ' + table.recipes.length + ' recipes, ' + table.choices.length + ' choices');
}
module.exports = {sync, SHAPES, FORGE};
