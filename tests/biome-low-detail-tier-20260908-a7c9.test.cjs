'use strict';

// Offline resource regression only: never build/deploy a plugin or start/stop Paper.
// All 62 profiles disable artificial landmarks and litter in every realm.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const resources = path.resolve(__dirname, '../server/custom-plugins/JasprHorrorBiomes/resources');
function rows(name) {
  return fs.readFileSync(path.join(resources, name), 'utf8').split(/\r?\n/)
    .filter(line => line.trim() && !line.startsWith('#')).map(line => line.split('|'));
}
function digest(data) {
  return crypto.createHash('sha256').update(data.map(row => row.join('|')).join('\n') + '\n').digest('hex');
}
const biomes = rows('biomes.tsv');
const details = rows('biome-details.tsv');
const structures = rows('structures/catalog-v1.tsv');
test('all 62 carriers disable artificial landmarks and litter', () => {
  assert.equal(biomes.length, 62);
  assert.equal(details.length, 62);
  assert.deepEqual(details.map(row => row[0]), biomes.map(row => row[0]));
  for (const row of details) {
    assert.equal(row.length, 10, row[0]);
    assert.equal(row[9], '0', row[0] + ' must disable artificial detail');
  }
});

test('all 62 retain their exact terrain, tree, atmosphere and authored detail identities', () => {
  // Captured BEFORE the resource edit, canonicalized only for comments/newlines.
  assert.equal(digest(biomes), '460e40b3cb1a791cccc2baf5bcbad52d517b5d374916589505dd95d565256ca6',
    'Names, inspiration, terrain, tree density, structure family, atmosphere and water must be preserved');
  assert.equal(digest(details.map(row => row.slice(0, 9))), '817c15ea06b66eccac1d2a4354a078a8b18fe31854f993a19c67b6112d543bd5',
    'Only landmark attempt counts may change; retain all signature motifs, litter and palettes');
  assert.equal(new Set(biomes.map(row => row[1])).size, 62);
  assert.equal(new Set(details.map(row => row[1])).size, 62);
  assert.equal(new Set(details.map(row => row.slice(2, 9).join('|'))).size, 62);
  for (const row of details) {
    assert.ok(row[1].trim(), `${row[0]} needs an identity`);
    assert.equal(new Set(row.slice(2, 5)).size, 3, `${row[0]} must retain all three motifs`);
  }
  assert.equal(new Set(details.flatMap(row => row.slice(2, 5))).size, 47);
  assert.equal(new Set(details.map(row => row[5])).size, 12);
});

test('all 62 retain biome-relevant mappings, the original 230 designs, and four missing major landmarks', () => {
  assert.equal(structures.length, 234);
  assert.equal(digest(structures.slice(0, 230)), '8d97d7298ea3ea770a90419a26d6d78766a6482d6f1f23ba49b94d94129d3d38',
    'The previous 230 structure identities, biome ownership, tiers, placement modes and room graphs must be preserved');
  assert.deepEqual(structures.slice(230).map(row => row[0]), [
    'viridian_tide_reactor', 'bloodroot_sawmill_keep',
    'endless_night_signal_citadel', 'grinning_hollow_amusement_hospital',
  ]);
  for (let index = 0; index < biomes.length; index++) {
    const choices = structures.filter(row => row[4].split(',').map(Number).includes(index));
    assert.ok(choices.some(row => row[5] === 'true' && row[4] === String(index)),
      `${biomes[index][1]} needs an exclusive structure`);
    assert.ok(choices.some(row => row[5] === 'false'), `${biomes[index][1]} needs a shared structure`);
  }
});
