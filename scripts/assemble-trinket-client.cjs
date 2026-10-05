'use strict';
// Builds the browser-side files of the silent-effects / own-icons update from the LIVE client files (never from the repo's site/
// copies, which are older): classes.js (silent-effects stage, then the Dungeon creative entries re-skinned), assets.epk (the
// trinket pack) and the two cache keys. Writes only --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-trinket-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), {execFileSync} = require('node:child_process');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261005-trinkets1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const silent = require('./build-silent-effects-client.cjs');
const creative = require('./update-dungeon-creative-icons.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

// classes.js: the silent-effects stage, then the Dungeon creative catalogue entries
const classesBefore = fs.readFileSync(live('classes.js'));
const staged = silent.build(classesBefore.toString('latin1'));
const withSilent = Buffer.from(staged.result, 'latin1');
const {result: classesAfter, changed} = creative.update(withSilent);
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);
manifest.creativeEntriesReskinned = changed;

// assets.epk: the trinket pack
const epkBefore = fs.readFileSync(live('assets.epk'));
execFileSync('node', [path.join(__dirname, 'build-trinket-pack.cjs'), '--epk', live('assets.epk'), '--out', path.join(OUT, 'pack')], {stdio: 'pipe', maxBuffer: 64 * 1024 * 1024});
const epkAfter = fs.readFileSync(path.join(OUT, 'pack', 'assets.epk'));
fs.copyFileSync(path.join(OUT, 'pack', 'assets.epk'), path.join(OUT, 'assets.epk'));
record('site/assets.epk', epkBefore, epkAfter);

// cache keys: the page loads classes.js with ?v=, jaspr-client.js loads the archive with ?build=
const html = fs.readFileSync(live('client.html'), 'utf8');
const htmlAfter = html.replace(/(<script defer src="classes\.js\?v=)[^"]+(")/, '$1' + KEY + '$2');
if (htmlAfter === html) throw new Error('client.html: the classes.js cache key was not found');
fs.writeFileSync(path.join(OUT, 'client.html'), htmlAfter);
record('site/client.html', Buffer.from(html), Buffer.from(htmlAfter));
const js = fs.readFileSync(live('jaspr-client.js'), 'utf8');
const jsAfter = js.replace(/(assetsURI: "assets\.epk\?build=)[^"]+(")/, '$1' + KEY + '$2');
if (jsAfter === js) throw new Error('jaspr-client.js: the assets cache key was not found');
fs.writeFileSync(path.join(OUT, 'jaspr-client.js'), jsAfter);
record('site/jaspr-client.js', Buffer.from(js), Buffer.from(jsAfter));

fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest, null, 2));
