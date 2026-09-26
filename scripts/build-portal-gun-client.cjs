'use strict';

// Exact, candidate-only update for the already-composed browser client. This preserves
// every unrelated client stage while changing the Portal Gun in the creative catalogue
// and EasierCrafting result from the vanilla hoe model (0) to model band 1160.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.PORTAL_GUN_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');

function objectAt(text, marker) {
  const markerAt = text.indexOf(marker);
  if (markerAt < 0) throw new Error('Missing '+marker);
  const start = text.lastIndexOf('{', markerAt);
  let depth = 0, string = false, escape = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (string) {
      if (escape) escape = false;
      else if (c === '\\') escape = true;
      else if (c === '"') string = false;
    } else if (c === '"') string = true;
    else if (c === '{') depth++;
    else if (c === '}' && --depth === 0) return [start, i + 1, text.slice(start, i + 1)];
  }
  throw new Error('Unterminated object for '+marker);
}
function once(text, from, to, label) {
  if (text.split(from).length !== 2) throw new Error(label+' must occur exactly once');
  return text.replace(from, to);
}
function build(input) {
  let output = input;
  const catalogueStart = output.indexOf('var JasprCreativeCatalog=[');
  const catalogueEnd = output.indexOf('];', catalogueStart);
  if (catalogueStart < 0 || catalogueEnd < 0) throw new Error('Creative catalogue section is missing');
  const catalogue = output.slice(catalogueStart, catalogueEnd);
  if (catalogue.split('"id":"portal_gun"').length !== 2) throw new Error('Creative catalogue must contain one Portal Gun');
  let [start, end, object] = objectAt(catalogue, '"id":"portal_gun"');
  object = once(object, '"model":0', '"model":1160', 'creative model');
  object = once(object, 'Damage:0s', 'Damage:1160s', 'creative damage');
  output = output.slice(0, catalogueStart + start) + object + output.slice(catalogueStart + end);
  if (output.split('"key":"jasprapocalypse:jaspr_portal_gun"').length !== 2) throw new Error('Recipe table must contain one Portal Gun');
  [start, end, object] = objectAt(output, '"key":"jasprapocalypse:jaspr_portal_gun"');
  object = once(object, 'Damage:0s', 'Damage:1160s', 'recipe result damage');
  output = output.slice(0, start) + object + output.slice(end);
  new vm.Script(output, {filename: 'candidate/portal-gun-client/classes.js'});
  return output;
}
if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const input = inputBytes.toString('latin1');
  const output = build(input);
  const outputBytes = Buffer.from(output, 'latin1');
  const dir = path.join(root, 'candidate', 'portal-gun-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const manifest = {stage: 'portal-gun-model-v1', source: path.relative(root, source),
    inputSHA256: sha(inputBytes), sha256: sha(outputBytes), model: 1160, edits: 3};
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2)+'\n');
  console.log(JSON.stringify(manifest, null, 2));
}
module.exports = {build};
