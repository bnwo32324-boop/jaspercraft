'use strict';
// Builds the browser-side files of "Find on an inventory item" (owner, 2026-10-08: "right-click any item in your inventory and click Find";
// "It should be Shift + right-click") from the LIVE client files (never from the repo's site/ copies, which are older): classes.js with the
// Easier Crafting / Chest Finder module upgraded in place (client-mods/recipe-book-teavm.js: the Find menu on a window slot) and client.html
// with the new classes.js cache key. assets.epk is not changed by this update; it is copied next to them (and is not part of the manifest)
// so the browser test can serve a complete client. The module is one region of classes.js, and every other stage must find the result the
// way it left it: the wide window, the Field Journal, the theme's text blocks, the Overloaded Armor Bar and the chest hooks are each rebuilt
// on it and have to give the same text back. Writes only --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-find-slot-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261008-findslot1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const recipeBook = require('./build-recipe-book-client.cjs');
const stages = [['chest-search', require('./build-chest-search-client.cjs')], ['wide-inventory', require('./build-wide-inventory-client.cjs')],
  ['journal', require('./build-journal-client.cjs')], ['theme', require('./build-theme-client.cjs')], ['overloaded-armor-bar', require('./build-armor-bar-client.cjs')]];

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}, stages: ['easier-crafting-module'], stable: []};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

const classesBefore = fs.readFileSync(live('classes.js'));
const upgraded = recipeBook.upgrade(live('classes.js'));            // replaces the module's region only; the screen hooks must still be there
manifest.module = {replacedBytes: upgraded.replacedBytes, moduleBytes: upgraded.moduleBytes};
const text = fs.readFileSync(path.join(ROOT, 'candidate', 'recipe-book-client', 'classes.js')).toString('latin1');
// The stages are rebuilt in the order that built the live client (the wide window is stripped and put back first, the Field Journal after
// it), so the whole chain has to give the upgraded text back; each stage on its own besides, where it does not depend on that order.
let chained = text;
for (const [name, stage] of stages) {
  chained = stage.build(chained).result;
  manifest.stable.push(name);
}
if (chained !== text) throw new Error('the stages, rebuilt in order, do not give the upgraded client back unchanged');
for (const [name, stage] of stages)
  if (name !== 'wide-inventory' && stage.build(text).result !== text) throw new Error('the ' + name + ' stage does not give the upgraded client back unchanged');
const classesAfter = Buffer.from(text, 'latin1');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);

const html = fs.readFileSync(live('client.html'), 'utf8');
const htmlAfter = html.replace(/(<script defer src="classes\.js\?v=)[^"]+(")/, '$1' + KEY + '$2');
if (htmlAfter === html) throw new Error('client.html: the classes.js cache key was not found or is already ' + KEY);
fs.writeFileSync(path.join(OUT, 'client.html'), htmlAfter);
record('site/client.html', Buffer.from(html), Buffer.from(htmlAfter));

fs.copyFileSync(live('assets.epk'), path.join(OUT, 'assets.epk'));
fs.copyFileSync(live('jaspr-client.js'), path.join(OUT, 'jaspr-client.js'));
fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest, null, 2));
