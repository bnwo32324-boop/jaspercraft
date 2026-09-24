'use strict';

const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const BLUEPRINTS = path.join(ROOT, 'server', 'custom-plugins', 'JasprApocalypse', 'src',
  'chat', 'jaspr', 'apocalypse', 'Blueprints.java');
const DEFAULT_MODULE = path.join(ROOT, 'client-mods', 'recipe-book-teavm.js');
const DECLARATION = /^var JasprBlueprintTable = (\[.*\]);$/m;

const EXTRA_DESCRIPTORS = {
  V: descriptor('glass', 'Glass'),
  d: descriptor('diamond_block', 'Block of Diamond'),
  f: descriptor('emerald_block', 'Block of Emerald'),
  h: descriptor('hopper', 'Hopper'),
  p: descriptor('piston', 'Piston'),
  r: descriptor('redstone_block', 'Block of Redstone'),
  s: descriptor('slime', 'Slime Block'),
  t: descriptor('repeater', 'Redstone Repeater'),
};

function descriptor(id, name, damage = 0) {
  return {
    tag: `${id}:${damage}`,
    snbt: `{id:"minecraft:${id}",Count:1b,Damage:${damage}s}`,
    name,
  };
}

function option(name, fallback = null) {
  const prefix = `--${name}=`;
  const found = process.argv.slice(2).find(value => value.startsWith(prefix));
  return found ? path.resolve(found.slice(prefix.length)) : fallback;
}

function exactlyOne(text, expression, label) {
  const matches = [...text.matchAll(new RegExp(expression.source, expression.flags.includes('g')
    ? expression.flags : expression.flags + 'g'))];
  if (matches.length !== 1) throw new Error(`${label}: expected one match, found ${matches.length}`);
  return matches[0];
}

function tableFrom(text, label) {
  const match = exactlyOne(text, DECLARATION, label);
  return { match, entries: JSON.parse(match[1]) };
}

function descriptorLibrary(entries) {
  const library = new Map(Object.entries(EXTRA_DESCRIPTORS));
  for (const entry of entries) {
    for (const row of entry.shape) {
      for (const symbol of row) {
        if (symbol === '.') continue;
        const value = entry.keys[symbol];
        if (!value) throw new Error(`${entry.id}: no descriptor for '${symbol}'`);
        if (library.has(symbol)
            && JSON.stringify(library.get(symbol)) !== JSON.stringify(value)) {
          throw new Error(`Ingredient '${symbol}' has inconsistent descriptors`);
        }
        library.set(symbol, value);
      }
    }
  }
  return library;
}

function sourceBlueprints() {
  const source = fs.readFileSync(BLUEPRINTS, 'utf8');
  const expression = /add\(map,\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]{3})",\s*"([^"]{3})",\s*"([^"]{3})"\);/g;
  const result = [...source.matchAll(expression)].map(match => ({
    id: match[1], kind: match[2], shape: [match[3], match[4], match[5]],
  }));
  if (result.length < 80) throw new Error(`Parsed only ${result.length} blueprints`);
  if (new Set(result.map(value => value.id)).size !== result.length) throw new Error('Duplicate blueprint id');
  return result;
}

function generatedTable(previous) {
  const library = descriptorLibrary(previous);
  return sourceBlueprints().map(blueprint => {
    const keys = {};
    for (const row of blueprint.shape) {
      for (const symbol of row) {
        if (symbol === '.' || keys[symbol]) continue;
        if (!library.has(symbol)) throw new Error(`${blueprint.id}: unknown ingredient '${symbol}'`);
        keys[symbol] = library.get(symbol);
      }
    }
    return { id: blueprint.id, shape: blueprint.shape, keys };
  });
}

function replaceTable(filename, replacementLine, expectedIds) {
  const input = fs.readFileSync(filename, 'utf8');
  const match = exactlyOne(input, DECLARATION, filename);
  const embedded = JSON.parse(match[1]);
  if (embedded.map(entry => entry.id).join('\n') !== expectedIds.join('\n')) {
    throw new Error(`${filename}: embedded blueprint catalogue differs from the server source`);
  }
  const output = input.slice(0, match.index) + replacementLine + input.slice(match.index + match[0].length);
  fs.writeFileSync(filename, output);
}

const modulePath = option('module', DEFAULT_MODULE);
const clientPath = option('client');
const moduleText = fs.readFileSync(modulePath, 'utf8');
const current = tableFrom(moduleText, modulePath);
const next = generatedTable(current.entries);
const newLine = `var JasprBlueprintTable = ${JSON.stringify(next)};`;
const expectedIds = next.map(entry => entry.id);
replaceTable(modulePath, newLine, expectedIds);
if (clientPath) replaceTable(clientPath, newLine, expectedIds);

const guns = sourceBlueprints().filter(value => value.kind.startsWith('gun/'));
console.log(JSON.stringify({ blueprints: next.length, guns: guns.length,
  module: modulePath, client: clientPath || null }));
