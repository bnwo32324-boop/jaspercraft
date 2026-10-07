'use strict';
// Builds the browser-side files of the JasperCraft inventory look from the LIVE client files (never from the repo's site/ copies, which
// are older): classes.js (the wide window and Field Journal modules rebuilt with the new palette; the theme stage recolours the gear
// column and the two title texts), assets.epk (the five recoloured textures) and the two cache keys. Each stage builder strips its own earlier text first, so
// the stages are rebuilt on whatever the live client carries. Writes only --out, plus manifest.json with the before/after hashes the
// deploy guards on.
//   node scripts/assemble-theme-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261007-theme1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const wide = require('./build-wide-inventory-client.cjs'), journal = require('./build-journal-client.cjs'), label = require('./build-theme-client.cjs');
const pack = require('./build-theme-pack.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

// classes.js: the stages whose modules changed colours, then the title colours (the stages commute)
const classesBefore = fs.readFileSync(live('classes.js'));
let text = classesBefore.toString('latin1');
manifest.stages = [];
for (const [name, stage] of [['wide-inventory', wide], ['journal', journal], ['theme-labels-and-gear-colours', label]]) {
  text = stage.build(text).result;
  manifest.stages.push(name);
}
const classesAfter = Buffer.from(text, 'latin1');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);

// assets.epk: the recoloured textures
const epkBefore = fs.readFileSync(live('assets.epk'));
const merged = pack.merge(epkBefore);
fs.writeFileSync(path.join(OUT, 'assets.epk'), merged.output);
record('site/assets.epk', epkBefore, merged.output);
manifest.pack = {replaced: merged.replaced, unchangedEntries: merged.unchanged, report: merged.report};

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
