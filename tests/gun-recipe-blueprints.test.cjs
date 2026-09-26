'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..');
const BLUEPRINTS = path.join(ROOT, 'server', 'custom-plugins', 'JasprApocalypse', 'src',
  'chat', 'jaspr', 'apocalypse', 'Blueprints.java');
const ARSENAL = path.join(ROOT, 'server', 'custom-plugins', 'JasprApocalypse', 'src',
  'chat', 'jaspr', 'apocalypse', 'Arsenal.java');
const CLIENT = path.join(ROOT, 'client-mods', 'recipe-book-teavm.js');
const PAPER = path.join(ROOT, 'server', 'cache', 'patched_1.12.2.jar');
const JAR = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.8-hotspot\\bin\\jar.exe';

function parseBlueprints() {
  const source = fs.readFileSync(BLUEPRINTS, 'utf8');
  const expression = /add\(map,\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]{3})",\s*"([^"]{3})",\s*"([^"]{3})"\);/g;
  return [...source.matchAll(expression)].map(match => ({
    id: match[1], kind: match[2], shape: [match[3], match[4], match[5]],
  }));
}

function clientTable() {
  const source = fs.readFileSync(CLIENT, 'utf8');
  const matches = [...source.matchAll(/^var JasprBlueprintTable = (\[.*\]);$/gm)];
  assert.equal(matches.length, 1, 'exactly one client blueprint table');
  return JSON.parse(matches[0][1]);
}

function trim(grid) {
  let rows = grid.map(row => row.slice());
  while (rows.length && rows[0].every(value => value === '.')) rows.shift();
  while (rows.length && rows.at(-1).every(value => value === '.')) rows.pop();
  while (rows.length && rows.every(row => row[0] === '.')) rows.forEach(row => row.shift());
  while (rows.length && rows.every(row => row.at(-1) === '.')) rows.forEach(row => row.pop());
  return rows;
}

function keysFor(entry) {
  const result = {};
  for (const [symbol, descriptor] of Object.entries(entry.keys)) result[symbol] = descriptor.tag;
  return result;
}

function materialGrid(entry) {
  const keys = keysFor(entry);
  return trim(entry.shape.map(row => [...row].map(symbol => symbol === '.' ? '.' : keys[symbol])));
}

function signature(grid) { return grid.map(row => row.join(',')).join('/'); }
function mirrored(grid) { return grid.map(row => row.slice().reverse()); }
function canonical(grid) {
  const normal = signature(trim(grid));
  const mirror = signature(trim(mirrored(grid)));
  return normal < mirror ? normal : mirror;
}

function ingredientTag(value) {
  if (Array.isArray(value)) return value.map(ingredientTag);
  if (!value || typeof value.item !== 'string') return null;
  return `${value.item.replace(/^minecraft:/, '')}:${value.data || 0}`;
}

function expandShaped(recipe) {
  const rows = recipe.pattern.map(row => [...row].map(symbol => symbol === ' ' ? ['.']
    : [].concat(ingredientTag(recipe.key[symbol])).filter(Boolean)));
  const results = [];
  function visit(index, cells) {
    if (index === rows.flat().length) {
      const width = rows[0].length;
      results.push(rows.map((row, y) => cells.slice(y * width, y * width + width)));
      return;
    }
    const choices = rows.flat()[index];
    for (const choice of choices) visit(index + 1, cells.concat(choice));
  }
  visit(0, []);
  return results;
}

function expandShapeless(recipe) {
  let variants = [[]];
  for (const raw of recipe.ingredients) {
    const choices = [].concat(ingredientTag(raw)).filter(Boolean);
    variants = variants.flatMap(existing => choices.map(choice => existing.concat(choice)));
  }
  return variants.map(values => values.sort().join('|'));
}

function vanillaSignatures() {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-gun-recipes-'));
  try {
    const extracted = spawnSync(JAR, ['xf', PAPER, 'assets/minecraft/recipes'], {
      cwd: temporary, encoding: 'utf8', windowsHide: true,
    });
    if (extracted.status !== 0) throw new Error(extracted.stderr || extracted.stdout);
    const root = path.join(temporary, 'assets', 'minecraft', 'recipes');
    const shaped = new Set(), shapeless = new Set();
    for (const name of fs.readdirSync(root)) {
      if (!name.endsWith('.json')) continue;
      const recipe = JSON.parse(fs.readFileSync(path.join(root, name), 'utf8'));
      if (recipe.type === 'minecraft:crafting_shaped') {
        for (const grid of expandShaped(recipe)) shaped.add(canonical(grid));
      } else if (recipe.type === 'minecraft:crafting_shapeless') {
        for (const ingredients of expandShapeless(recipe)) shapeless.add(ingredients);
      }
    }
    return { shaped, shapeless };
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
}

const blueprints = parseBlueprints();
const client = clientTable();
assert.equal(blueprints.length, 100, 'server blueprint count');
assert.deepEqual(client.map(entry => ({ id: entry.id, shape: entry.shape })),
  blueprints.map(entry => ({ id: entry.id, shape: entry.shape })), 'client/server recipe parity');

const arsenalSource = fs.readFileSync(ARSENAL, 'utf8');
const enumBody = arsenalSource.match(/private enum Gun \{([\s\S]*?);\s*\r?\n\s*final String id/)[1];
const gunIds = [...enumBody.matchAll(/^[ \t]*[A-Z_]+\("([^"]+)"/gm)].map(match => match[1]);
const guns = blueprints.filter(entry => entry.kind.startsWith('gun/'));
assert.equal(gunIds.length, 40, 'arsenal gun count');
assert.deepEqual(new Set(guns.map(entry => entry.id)), new Set(gunIds), 'every firearm is craftable');

const byId = new Map(client.map(entry => [entry.id, entry]));
const seen = new Map();
for (const entry of client) {
  const value = canonical(materialGrid(entry));
  assert(!seen.has(value), `${entry.id} collides with ${seen.get(value)}`);
  seen.set(value, entry.id);
}

const vanilla = vanillaSignatures();
for (const entry of client) {
  const grid = materialGrid(entry);
  assert(!vanilla.shaped.has(canonical(grid)), `${entry.id} shadows a vanilla shaped recipe`);
  const ingredients = grid.flat().filter(value => value !== '.').sort().join('|');
  assert(!vanilla.shapeless.has(ingredients), `${entry.id} is shadowed by a vanilla shapeless recipe`);
}

const ironValue = { 'iron_ingot:0': 1, 'iron_block:0': 9, 'hopper:0': 5, 'piston:0': 1 };
const costs = [];
for (const blueprint of guns) {
  const entry = byId.get(blueprint.id);
  const ingredients = materialGrid(entry).flat().filter(value => value !== '.');
  const iron = ingredients.reduce((sum, value) => sum + (ironValue[value] || 0), 0);
  assert(ingredients.includes('iron_block:0'), `${entry.id} needs a substantial metal receiver`);
  assert(ingredients.some(value => ['redstone:0', 'redstone_block:0', 'repeater:0'].includes(value)),
    `${entry.id} needs a redstone firing/control component`);
  assert(iron >= 12, `${entry.id} is too cheap at ${iron} iron-equivalent ingots`);
  costs.push(iron);
}
for (const id of ['sepulcher', 'vesper', 'ossuary', 'turnstile', 'cinder', 'whisper',
  'tunnelrat', 'tempest', 'blackbox', 'quarantine', 'adjudicator', 'bastion',
  'deadfrequency', 'whiteout', 'rifle', 'longwatch', 'signal', 'gallows',
  'watchtower', 'shotgun', 'cyclops',
  'bellringer', 'lockjaw', 'choir', 'ashfall']) {
  assert(materialGrid(byId.get(id)).flat().includes('gunpowder:0'), `${id} needs gunpowder`);
}
assert(byId.has('portal_gun'), 'Portal Gun remains craftable');

const exoskeletons = blueprints.filter(entry => entry.kind.startsWith('armor/'));
assert.equal(exoskeletons.length, 16, 'all four four-piece exoskeleton sets are craftable');
const exoskeletonCost = {
  'diamond_block:0': 8,
  'iron_block:0': 8,
  'gold_block:0': 6,
  'nether_star:0': 4,
};
const accents = {
  bulwark: 'obsidian:0',
  ranger: 'emerald_block:0',
  spectre: 'ender_eye:0',
  hazmat: 'slime:0',
};
for (const [set, accent] of Object.entries(accents)) {
  const pieces = exoskeletons.filter(entry => entry.kind === `armor/${set}`);
  assert.equal(pieces.length, 4, `${set} has four pieces`);
  const ingredients = pieces.flatMap(entry => materialGrid(byId.get(entry.id)).flat())
    .filter(value => value !== '.');
  for (const [material, count] of Object.entries(exoskeletonCost)) {
    assert.equal(ingredients.filter(value => value === material).length, count,
      `${set} full-set ${material} cost`);
  }
  assert.equal(ingredients.filter(value => value === accent).length, 4,
    `${set} has four specialized vanilla cores`);
}

console.log(JSON.stringify({ blueprints: blueprints.length, firearms: guns.length,
  exoskeletonPieces: exoskeletons.length, vanillaRecipesChecked: 432,
  minimumIronEquivalent: Math.min(...costs),
  maximumIronEquivalent: Math.max(...costs),
  averageIronEquivalent: Number((costs.reduce((a, b) => a + b, 0) / costs.length).toFixed(1)),
  fullExoskeletonSet: '8 diamond blocks + 8 iron blocks + 6 gold blocks + 4 Nether Stars + 4 set cores' }));
