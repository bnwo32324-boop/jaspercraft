'use strict';

// Build-time projection of the authoritative Java item definitions into harmless
// browser-menu templates. Tests pin this parser to every server catalogue category.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse');
const colors = Object.freeze({
  BLACK:'0',DARK_BLUE:'1',DARK_GREEN:'2',DARK_AQUA:'3',DARK_RED:'4',DARK_PURPLE:'5',
  GOLD:'6',GRAY:'7',DARK_GRAY:'8',BLUE:'9',GREEN:'a',AQUA:'b',RED:'c',LIGHT_PURPLE:'d',YELLOW:'e',WHITE:'f'
});

function read(name) { return fs.readFileSync(path.join(source, name), 'utf8'); }
function title(value) { return value[0].toUpperCase() + value.slice(1); }
function material(value) { return 'minecraft:' + value.toLowerCase(); }
function color(value) {
  if (!(value in colors)) throw new Error('Unknown ChatColor in creative catalogue: ' + value);
  return '\u00a7' + colors[value];
}
function quotedList(sourceText, name) {
  const match = sourceText.match(new RegExp('String\\[\\] '+name+' = \\{([^}]+)\\}'));
  if (!match) throw new Error('Missing '+name+' equipment list');
  return [...match[1].matchAll(/"([^"]+)"/g)].map(m => m[1]);
}
function template(entry) {
  const displayName = entry.color + entry.title;
  const lore = [
    '\u00a77JasperCraft custom ' + entry.category,
    '\u00a78Creative catalogue - server-issued on pickup'
  ];
  const tags = [];
  if (entry.model > 0) tags.push('Unbreakable:1b', 'HideFlags:6');
  if (entry.id === 'guide') tags.push('title:'+JSON.stringify(entry.title), 'author:"JasperCraft Survivors"',
    'pages:["Open the server-issued copy to read The Last Broadcast."]');
  tags.push('display:{Name:'+JSON.stringify(displayName)+',Lore:['+lore.map(JSON.stringify).join(',')+']}');
  tags.push('JasprCreative:{id:'+JSON.stringify(entry.id)+'}');
  entry.snbt = '{id:'+JSON.stringify(entry.material)+',Count:1b,Damage:'+entry.model+'s,tag:{'+tags.join(',')+'}}';
  entry.search = [entry.id.replace(/_/g,' '), entry.title, entry.category,
    entry.category === 'gun' ? 'gun firearm weapon arsenal' : '',
    entry.category === 'melee' ? 'melee weapon blade' : '',
    entry.category === 'armor' ? 'armor armour exoskeleton gear' : '',
    entry.category === 'supply' ? 'supply ammunition ammo salvage relic' : '',
    entry.category === 'artifact' ? 'artifact trophy guide book' : '',
    entry.category === 'block' ? 'block placeable turret sentry defense dispenser' : ''
  ].join(' ').toLowerCase();
  return entry;
}

function catalogue() {
  const arsenal = read('Arsenal.java'), equipment = read('ExpeditionEquipment.java'), items = read('ApocalypseItems.java');
  const result = [];
  for (const match of arsenal.matchAll(/^\s+[A-Z][A-Z0-9_]*\("([a-z0-9_]+)", "([^"]+)", ChatColor\.([A-Z_]+), (\d+),/gm)) {
    result.push(template({id:match[1],title:match[2],category:'gun',material:'minecraft:diamond_hoe',model:+match[4],color:color(match[3])}));
  }
  for (const match of equipment.matchAll(/melee\("([a-z0-9_]+)", "([^"]+)", (\d+),/g)) {
    result.push(template({id:match[1],title:match[2],category:'melee',material:'minecraft:diamond_sword',model:+match[3],color:'\u00a7b'}));
  }
  const sets = quotedList(equipment, 'SETS'), parts = quotedList(equipment, 'PARTS');
  for (let set=0; set<sets.length; set++) for (const part of parts) {
    result.push(template({id:sets[set]+'_'+part,title:title(sets[set])+' Exoskeleton '+title(part),category:'armor',
      material:material('DIAMOND_'+part.toUpperCase()),model:10+set*10,color:'\u00a7b'}));
  }
  for (const match of equipment.matchAll(/add\(new Spec\("([a-z0-9_]+)", "([^"]+)", "(material|consumable|block|gadget)", Material\.([A-Z_]+), (\d+),/g)) {
    result.push(template({id:match[1],title:match[2],category:match[3],material:material(match[4]),model:+match[5],color:'\u00a7b'}));
  }
  const amountFactories = [...items.matchAll(/public static ItemStack (relic|scrap|ammo)\(int amount\) \{\s*return item\(Material\.([A-Z_]+), amount, "([a-z0-9_]+)", ChatColor\.([A-Z_]+) \+ "([^"]+)"/g)];
  for (const match of amountFactories) {
    if (match[1] !== match[3]) throw new Error('Supply factory/id drift: '+match[1]+'/'+match[3]);
    result.push(template({id:match[3],title:match[5],category:'supply',material:material(match[2]),model:0,color:color(match[4])}));
  }
  const trophy = items.match(/public static ItemStack trophy\(\) \{\s*return item\(Material\.([A-Z_]+), 1, "([a-z0-9_]+)", ChatColor\.([A-Z_]+) \+ "([^"]+)"/);
  if (!trophy) throw new Error('Missing expedition trophy factory');
  result.push(template({id:trophy[2],title:trophy[4],category:'artifact',material:material(trophy[1]),model:0,color:color(trophy[3])}));
  const guide = items.match(/book\.setTitle\("([^"]+)"\); book\.setAuthor/);
  if (!guide) throw new Error('Missing field-guide title');
  result.push(template({id:'guide',title:guide[1],category:'artifact',material:'minecraft:written_book',model:0,color:'\u00a76'}));

  const ids = new Set(result.map(item => item.id));
  if (ids.size !== result.length) throw new Error('Duplicate creative catalogue IDs');
  const expected = {gun:40,melee:24,armor:16,material:4,consumable:4,supply:2,artifact:2,block:1,gadget:1};
  for (const [category,count] of Object.entries(expected)) {
    const actual = result.filter(item => item.category === category).length;
    if (actual !== count) throw new Error(`Creative ${category} catalogue drift: ${actual}, expected ${count}`);
  }
  if (result.length !== 94) throw new Error('Creative catalogue must contain all 94 custom gameplay items');
  return result;
}

module.exports = {catalogue, colors, root};
