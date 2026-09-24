'use strict';
/* Regenerates JasprRecipeTable in client-mods/recipe-book-teavm.js (the EasierCrafting panel) from
 * client-mods/recipe-table.json, the complete crafting-recipe export of a server running every live
 * plugin (scripts/export-recipes.sh). Titles and categories of JasperCraft items come from the
 * client's own Creative catalogue (site/classes.js), vanilla ones from the item's creative tab.
 *   node scripts/sync-recipe-table.cjs [--check]    (--check: fail when the module is out of date)
 */
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const EXPORT = path.join(ROOT, 'client-mods', 'recipe-table.json');
const MODULE = path.join(ROOT, 'client-mods', 'recipe-book-teavm.js');
const CLIENT = path.join(ROOT, 'site', 'classes.js');
const DECLARATION = /^var JasprRecipeTable = .*;$/m;

const TABS = {buildingBlocks: 'Building Blocks', decorations: 'Decoration Blocks', redstone: 'Redstone',
  transportation: 'Transportation', misc: 'Miscellaneous', food: 'Foodstuffs', tools: 'Tools', combat: 'Combat',
  brewing: 'Brewing', materials: 'Materials', '': 'Miscellaneous'};
const LABEL = {gun: 'Firearms', melee: 'Melee', armor: 'Exoskeletons', gadget: 'Gadgets', block: 'Blocks',
  material: 'Materials', consumable: 'Supplies', supply: 'Supplies', artifact: 'Relics'};

function catalogue() {
  const text = fs.readFileSync(CLIENT, 'latin1');
  const start = text.indexOf('var JasprCreativeCatalog=[');
  if (start < 0 || text.indexOf('var JasprCreativeCatalog=[', start + 1) >= 0) throw new Error('JasprCreativeCatalog declaration not found exactly once');
  const end = text.indexOf('];', start);
  const ctx = {};
  vm.runInNewContext(text.slice(start, end + 2).replace('var JasprCreativeCatalog=', 'this.c='), ctx);
  const byId = new Map();
  for (const e of ctx.c) byId.set(e.id, e);
  return byId;
}

function describe(recipe, byId) {
  const [ns, name] = recipe.key.split(':');
  if (ns === 'minecraft') return {title: recipe.title, category: TABS[recipe.tab] || 'Miscellaneous'};
  if (ns === 'jasprgear') {
    const e = byId.get('gear_' + name);
    const search = e ? e.search || '' : '';
    return {title: e ? e.title : recipe.title,
      category: /backpack/.test(search) ? 'Backpacks' : /supply|consumable/.test(search) ? 'Supplies' : 'Survivor Gear'};
  }
  const id = name.replace(/^jaspr_/, '');
  const e = byId.get(id);
  if (e) return {title: e.title, category: LABEL[e.category] || 'Other'};
  return {title: recipe.title, category: id === 'field_guide' ? 'Supplies' : 'Gadgets'};
}

function build() {
  const data = JSON.parse(fs.readFileSync(EXPORT, 'utf8'));
  if (data.format !== 1 || !Array.isArray(data.recipes) || !Array.isArray(data.choices)) throw new Error('unexpected export format');
  const byId = catalogue();
  const recipes = data.recipes.map(r => {
    const d = describe(r, byId);
    const out = {key: r.key, type: r.type};
    if (r.type === 'shaped') { out.w = r.w; out.h = r.h; out.cells = r.cells; } else out.ingredients = r.ingredients;
    out.result = r.result; out.count = r.count; out.title = d.title; out.category = d.category;
    // The server refuses shift-crafting these (Arsenal and SentryTurret craft handlers).
    if (/^jasprapocalypse:(jaspr_|sentry_turret$)/.test(r.key)) out.single = true;
    for (const c of out.cells || out.ingredients) if (c >= data.choices.length) throw new Error(r.key + ': bad choice ' + c);
    return out;
  });
  const choices = data.choices.map(list => list.map(o => ({snbt: o.snbt, data: o.data, max: o.max})));
  const table = {format: 1, choices, recipes};
  const json = JSON.stringify(table).replace(/[\u0080-￿]/g, ch => '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0'));
  return 'var JasprRecipeTable = ' + json + ';';
}

const line = build();
const source = fs.readFileSync(MODULE, 'utf8');
const found = source.match(new RegExp(DECLARATION.source, 'gm')) || [];
if (found.length !== 1) throw new Error('expected one JasprRecipeTable declaration in the module, found ' + found.length);
if (process.argv.includes('--check')) {
  if (found[0] !== line) { console.error('JasprRecipeTable is out of date: run node scripts/sync-recipe-table.cjs'); process.exit(1); }
  console.log('JasprRecipeTable up to date');
} else {
  fs.writeFileSync(MODULE, source.replace(DECLARATION, () => line));
  const t = JSON.parse(line.slice('var JasprRecipeTable = '.length, -1));
  const cats = {};
  for (const r of t.recipes) cats[r.category] = (cats[r.category] || 0) + 1;
  console.log('JasprRecipeTable: ' + t.recipes.length + ' recipes, ' + t.choices.length + ' ingredients, ' + line.length + ' bytes');
  console.log(JSON.stringify(cats));
}
