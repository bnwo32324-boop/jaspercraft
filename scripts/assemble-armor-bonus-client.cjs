'use strict';
// Builds the browser-side files of the armament armour update from the LIVE client files (never from the repo's site/ copies, which are
// older): classes.js with the Field Journal stage (it now keeps the armour points the worn armaments add: "ab") and the Overloaded Armor
// Bar stage (it adds them to the armour it draws), and client.html with the new classes.js cache key. assets.epk is not changed by this
// update; it is copied next to them (and is not part of the manifest) so the browser test can serve a complete client. Writes only
// --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-armor-bonus-client.cjs --game <live checkout> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'deploy');
const KEY = arg('--key') || '20261007-armor1';
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const journal = require('./build-journal-client.cjs'), armorBar = require('./build-armor-bar-client.cjs');

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}, stages: ['journal', 'overloaded-armor-bar']};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

const classesBefore = fs.readFileSync(live('classes.js'));
let text = classesBefore.toString('latin1');
for (const stage of [journal, armorBar]) text = stage.build(text).result;      // the stages commute; each strips its own earlier text first
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
