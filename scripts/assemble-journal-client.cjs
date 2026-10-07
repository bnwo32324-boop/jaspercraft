'use strict';
// Builds the browser-side files of the Field Journal update from the LIVE client files (never from the repo's site/ copies, which
// are older): classes.js with the journal stage, and client.html with the new classes.js cache key. assets.epk is not changed by
// this update; it is copied next to them (and is not part of the manifest) so the browser test can serve a complete client.
// Writes only --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-journal-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261007-journal1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const journal = require('./build-journal-client.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

const classesBefore = fs.readFileSync(live('classes.js'));
const built = journal.build(classesBefore.toString('latin1'));
const classesAfter = Buffer.from(built.result, 'latin1');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);

const html = fs.readFileSync(live('client.html'), 'utf8');
const htmlAfter = html.replace(/(<script defer src="classes\.js\?v=)[^"]+(")/, '$1' + KEY + '$2');
if (htmlAfter === html) throw new Error('client.html: the classes.js cache key was not found or is already ' + KEY);
fs.writeFileSync(path.join(OUT, 'client.html'), htmlAfter);
record('site/client.html', Buffer.from(html), Buffer.from(htmlAfter));

fs.copyFileSync(live('assets.epk'), path.join(OUT, 'assets.epk'));
fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest, null, 2));
