'use strict';
// The Creative catalogue's Dungeon entries show each bauble and the Reliquary pouch as the vanilla stand-in they used to be
// (owner 2026-10-05: every trinket has its own texture). This rewrites those entries -- and only those -- to the stone-sword
// carrier of the catalogue's band, exactly what the server now issues, through the same byte-preserving fenced block.
//   node scripts/update-dungeon-creative-icons.cjs --source <classes.js> --out <classes.js>
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const builder = require('./dungeon-creative-client.cjs');
const dungeon = require('./trinket-art/dungeon.cjs');

builder.setRebase(true);   // the fence was written against an older client; later stages changed other bytes
const BANDS = new Map(Object.keys(dungeon.ICONS).map((name, n) => ['penitent_bauble_' + name.toLowerCase(), n + 1]));
BANDS.set('penitent_pouch', Object.keys(dungeon.ICONS).length + 1);
const CARRIER = 'stone_sword';

/** The entry as the server now issues it: an unbreakable stone sword whose damage value is the band. */
function skinned(entry, band) {
  const old = /^\{id:"minecraft:([a-z_]+)",Count:1b,tag:\{/.exec(entry.snbt);
  assert.ok(old, entry.id + ': unexpected SNBT start');
  assert.ok(entry.snbt.endsWith(',Damage:0s}'), entry.id + ': unexpected SNBT end (already skinned?)');
  const snbt = '{id:"minecraft:' + CARRIER + '",Count:1b,tag:{Unbreakable:1b,HideFlags:63,' + entry.snbt.slice(old[0].length, -'Damage:0s}'.length) + 'Damage:' + band + 's}';
  return {...entry, material: 'minecraft:' + CARRIER, model: band, snbt};
}

function update(input) {
  const base = builder.strip(input);
  const all = builder.readCatalog(input).entries.filter(e => e.id.startsWith('penitent_'));
  assert.ok(all.length > 300, 'the Dungeon block is present');
  let changed = 0;
  const entries = all.map(e => {
    const band = BANDS.get(e.id);
    if (band === undefined) return e;
    if (e.material === 'minecraft:' + CARRIER && e.model === band) return e;   // already skinned: stable
    changed++;
    return skinned(e, band);
  });
  assert.equal(BANDS.size, 73);
  assert.ok(changed === 73 || changed === 0, 'all 73 or none changed: ' + changed);
  const result = builder.apply(input, entries);
  assert.deepEqual(builder.apply(result, entries), result, 'reapplication must be byte-identical');
  return {result, base, entries, changed};
}

if (require.main === module) {
  const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
  const source = arg('--source'), out = arg('--out');
  if (!source || !out) throw new Error('usage: --source <classes.js> --out <classes.js>');
  const raw = fs.readFileSync(source), {result, base, entries, changed} = update(raw);
  fs.mkdirSync(path.dirname(out), {recursive: true});
  fs.writeFileSync(out, result);
  console.log(JSON.stringify({source, sourceSha256: builder.sha(raw), baseSha256: builder.sha(base), sha256: builder.sha(result), dungeonEntries: entries.length, reskinned: changed, bytes: result.length - raw.length}, null, 2));
}
module.exports = {update, skinned, BANDS};
