'use strict';
// Builds the browser-side files of the guns / portal gun / jerky update from the LIVE client files (never from the repo's site/
// copies, which are older): classes.js (the NBT-skin stage), assets.epk (jerky texture + model, cooked_beef.json with its skin
// override, the Portal Gun's own model and texture) and the two cache keys. Writes only --out, plus manifest.json with the
// before/after hashes the deploy guards on.
//   node scripts/assemble-guns-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261005-guns1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const nbtSkin = require('./build-nbt-skin-client.cjs');
const gunsPack = require('./build-guns-pack.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

// classes.js: the NBT-skin stage
const classesBefore = fs.readFileSync(live('classes.js'));
const staged = nbtSkin.build(classesBefore.toString('latin1'));
const classesAfter = Buffer.from(staged.result, 'latin1');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);

// assets.epk: the guns pack
const epkBefore = fs.readFileSync(live('assets.epk'));
const merged = gunsPack.merge(epkBefore);
fs.writeFileSync(path.join(OUT, 'assets.epk'), merged.output);
record('site/assets.epk', epkBefore, merged.output);
manifest.pack = {replaced: merged.replaced, added: merged.added, unchangedEntries: merged.unchanged};

// cache keys: the page loads classes.js with ?v=, jaspr-client.js loads the archive with ?build=
const html = fs.readFileSync(live('client.html'), 'utf8');
const htmlAfter = html.replace(/(<script defer src="classes\.js\?v=)[^"]+(")/, '$1' + KEY + '$2');
if (htmlAfter === html) throw new Error('client.html: the classes.js cache key was not found or is already ' + KEY);
fs.writeFileSync(path.join(OUT, 'client.html'), htmlAfter);
record('site/client.html', Buffer.from(html), Buffer.from(htmlAfter));
const js = fs.readFileSync(live('jaspr-client.js'), 'utf8');
const jsAfter = js.replace(/(assetsURI: "assets\.epk\?build=)[^"]+(")/, '$1' + KEY + '$2');
if (jsAfter === js) throw new Error('jaspr-client.js: the assets cache key was not found or is already ' + KEY);
fs.writeFileSync(path.join(OUT, 'jaspr-client.js'), jsAfter);
record('site/jaspr-client.js', Buffer.from(js), Buffer.from(jsAfter));

fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest, null, 2));
