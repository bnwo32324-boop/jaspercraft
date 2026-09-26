'use strict';

// Keep the checked-in recipe export aligned with the server-issued Portal Gun model.
const fs = require('node:fs');
const path = require('node:path');
const file = path.join(__dirname, '..', 'client-mods', 'recipe-table.json');
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
const matches = data.recipes.filter(recipe => recipe.key === 'jasprapocalypse:jaspr_portal_gun');
if (matches.length !== 1) throw new Error('Expected exactly one Portal Gun recipe');
if (!matches[0].result.includes('Damage:0s') && !matches[0].result.includes('Damage:1160s'))
  throw new Error('Unexpected Portal Gun recipe result');
matches[0].result = matches[0].result.replace('Damage:0s', 'Damage:1160s');
fs.writeFileSync(file, JSON.stringify(data));
console.log('Portal Gun recipe model: 1160');
