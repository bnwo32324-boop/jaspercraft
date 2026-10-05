'use strict';
/* Brings the realm armoury's armour stats in the client's own data up to what the plugin says (ArmoryPiece.java is the single
 * source: owner 2026-10-05, "Emerald should be better than Diamond. That includes the helmet": the helmet and boots went from 3 to
 * 4 armour). The browser shows these items in two places, both written from the plugin's export: the recipe panel's results
 * (JasprRecipeTable) and the Creative catalogue (JASPR_GEAR_CAT), and their tooltips read the generic.armor modifier out of the
 * item's SNBT. Only that one number per armour piece is changed, and only where it differs, so the edit never changes the file's
 * length; a rebuild from the plugin's export (scripts/build-gear-client.cjs, scripts/sync-armory-recipes.cjs) writes the same numbers.
 *
 *   node scripts/refresh-armory-stats-client.cjs --source <classes.js> --out <file>        (a client bundle, latin1)
 *   node scripts/refresh-armory-stats-client.cjs --write <file> [<file> ...]               (client-mods data files, in place)
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const PIECES = path.join(ROOT, 'server', 'custom-plugins', 'JasprGear', 'src', 'chat', 'jaspr', 'gear', 'ArmoryPiece.java');
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');

/** {helmet: {slot: 'head', armor: 4}, chestplate: ..., leggings: ..., boots: ...} read from ArmoryPiece.java. */
function wanted(file = PIECES) {
  const out = {};
  for (const m of fs.readFileSync(file, 'utf8').matchAll(/^\s*(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS)\("(\w+)", "\w+", Material\.\w+, "(\w+)", (\d+),/gm)) out[m[1]] = {slot: m[2], armor: Number(m[3])};
  if (Object.keys(out).length !== 4) throw new Error('ArmoryPiece.java: expected the four armour pieces, got ' + Object.keys(out));
  return out;
}

const ITEM = /JasprArmory:\{set:\\"[a-z]+\\",piece:\\"(helmet|chestplate|leggings|boots)\\"\}/g;
/** Every armoury armour item's modifier: [{piece, slot, from, at, length}] (at = index of the amount's digits). */
function scan(text) {
  const found = [];
  let m;
  ITEM.lastIndex = 0;
  while ((m = ITEM.exec(text))) {
    const start = text.lastIndexOf('{id:\\"minecraft:', m.index), end = text.indexOf('Damage:', m.index);
    if (start < 0 || end < 0 || end - start > 5000) throw new Error('armoury item without a clear SNBT around index ' + m.index);
    const slice = text.slice(start, end), mod = /Amount:([0-9.]+)d,Slot:\\"(head|chest|legs|feet)\\",AttributeName:\\"generic\.armor\\"/.exec(slice);
    if (!mod) throw new Error('armoury ' + m[1] + ' without a generic.armor modifier around index ' + m.index);
    found.push({piece: m[1], slot: mod[2], amount: Number(mod[1]), at: start + mod.index + 'Amount:'.length, length: mod[1].length});
  }
  return found;
}

/** {text, seen, changed}: the text with every armoury armour amount as the plugin says (a same-length edit). */
function refresh(text, want = wanted()) {
  let out = text, seen = 0, changed = 0;
  for (const f of scan(text)) {
    seen++;
    const w = want[f.piece];
    if (f.slot !== w.slot) throw new Error(f.piece + ' is on slot ' + f.slot + ', not ' + w.slot);
    if (f.amount === w.armor) continue;
    const digits = w.armor.toFixed(1);
    if (digits.length !== f.length) throw new Error('amount ' + digits + ' does not fit the ' + f.length + ' characters of ' + f.amount);
    out = out.slice(0, f.at) + digits + out.slice(f.at + f.length);
    changed++;
  }
  if (out.length !== text.length) throw new Error('the edit changed the length');
  return {text: out, seen, changed};
}

if (require.main === module) {
  const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
  const writeAt = process.argv.indexOf('--write');
  if (writeAt > 0) {
    for (const file of process.argv.slice(writeAt + 1).filter(a => !a.startsWith('--'))) {
      const raw = fs.readFileSync(file, 'latin1'), r = refresh(raw);
      if (r.changed) fs.writeFileSync(file, Buffer.from(r.text, 'latin1'));
      console.log(file + ': ' + r.seen + ' armoury armour items, ' + r.changed + ' updated');
    }
  } else {
    const source = arg('--source') || path.join(ROOT, 'site', 'classes.js'), target = arg('--out') || path.join(ROOT, 'candidate', 'armory-stats-client', 'classes.js');
    const raw = fs.readFileSync(source, 'latin1'), r = refresh(raw);
    new vm.Script(Buffer.from(r.text, 'latin1').toString('utf8'), {filename: 'classes.js'});   // must still parse
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, Buffer.from(r.text, 'latin1'));
    console.log(JSON.stringify({stage: 'armory-stats-v1', source, sourceSha256: sha(raw), sha256: sha(r.text), armouryArmourItems: r.seen, updated: r.changed, wanted: wanted()}, null, 2));
  }
}
module.exports = {wanted, scan, refresh};
