'use strict';
// Builds the browser-side files of the Mutant Creatures port (JASPR_MUTANTS) from the LIVE client files, never the repo's older
// site/ copies:
//   classes.js  the live client plus the Mutants stage (scripts/build-mutants-client.cjs; outermost: rebuilding any other stage
//               later needs --unpatch first, then this stage again);
//   assets.epk  the live archive plus the mod's textures, item models, sounds, sound events and names (scripts/build-mutants-pack.cjs);
//   client.html / jaspr-client.js  the two cache keys.
// Writes only --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-mutants-client.cjs [--game <live checkout>] [--out <folder>] [--key <cache key>]
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'mutants-deploy');
const KEY = arg('--key') || '20261009-mutants1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const stage = require('./build-mutants-client.cjs'), pack = require('./build-mutants-pack.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

const classesBefore = fs.readFileSync(live('classes.js'));
if (classesBefore.includes(stage.BEGIN)) throw new Error('the live client already carries a Mutants stage');
const built = stage.build(classesBefore.toString('latin1'));
const classesAfter = Buffer.from(built.output, 'latin1');
if (stage.unpatch(built.output) !== classesBefore.toString('latin1')) throw new Error('unpatch does not give the live client back');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);
manifest.hooks = stage.allHooks().map(h => h[0]);

const epkBefore = fs.readFileSync(live('assets.epk'));
const packed = pack.build(epkBefore);
fs.writeFileSync(path.join(OUT, 'assets.epk'), packed.output);
record('site/assets.epk', epkBefore, packed.output);
manifest.packFiles = Object.keys(packed.files).length;
manifest.soundEvents = packed.events;

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
