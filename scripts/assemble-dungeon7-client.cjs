'use strict';
// Builds the browser-side files of Dungeon Dimension generation 7 (owner 2026-10-05 and 2026-10-08: three floors, real bosses, crowds that
// stay spread over big rooms, new mobs, relics and secrets) from the LIVE client files, never the repo's older site/ copies:
//   classes.js  the Dungeon block of the Creative catalogue rebuilt from the generation 7 plugin's own export (926 entries: the new
//               floors' relics and trophies on their icon bands), then the Big Mobs stage (scaled models and hitboxes);
//   assets.epk  the trinket pack, which now carries generation 7's 115 new relic icons;
//   client.html / jaspr-client.js  the two cache keys.
// Every other stage must find the result the way it left it (rebuilt in the order that built the live client, they give it back
// unchanged). Writes only --out, plus manifest.json with the before/after hashes the deploy guards on.
//   node scripts/assemble-dungeon7-client.cjs --game <live checkout> --export <creative-catalog.json> --out <folder> --key <cache key>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), {execFileSync} = require('node:child_process');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'dungeon7-deploy');
const KEY = arg('--key') || '20261008-dungeon7';
const EXPORT = arg('--export');
if (!EXPORT) throw new Error('--export <creative-catalog.json from the generation 7 plugin> is required');
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const catalog = require('./dungeon-creative-client.cjs'), bigMobs = require('./build-big-mobs-client.cjs');
const stages = [['chest-search', require('./build-chest-search-client.cjs')], ['wide-inventory', require('./build-wide-inventory-client.cjs')],
  ['journal', require('./build-journal-client.cjs')], ['theme', require('./build-theme-client.cjs')], ['overloaded-armor-bar', require('./build-armor-bar-client.cjs')],
  ['big-mobs', bigMobs]];

fs.mkdirSync(OUT, {recursive: true});
const live = f => path.join(GAME, 'site', f);
const manifest = {key: KEY, files: {}, stable: []};
function record(name, before, after) { manifest.files[name] = {before: sha(before), after: sha(after), bytes: after.length}; }

// classes.js: the Dungeon catalogue block, then Big Mobs
const classesBefore = fs.readFileSync(live('classes.js'));
const entries = catalog.validateCatalog(JSON.parse(fs.readFileSync(EXPORT, 'utf8').replace(/^\uFEFF/, '')));
catalog.setRebase(true);   // later stages (wide inventory, text fit, the journal ...) changed bytes outside the block since it was first written
const withCatalog = catalog.apply(classesBefore, entries);
const before = catalog.readCatalog(classesBefore).entries.filter(e => e.id.startsWith('penitent_')).length;
manifest.dungeonEntries = {before, after: entries.length};
const text = bigMobs.build(withCatalog.toString('latin1')).result;
// The stages, rebuilt in the order that built the live client (the wide window first, the Journal after it), give the text back.
let chained = text;
for (const [name, stage] of stages) { chained = stage.build(chained).result; manifest.stable.push(name); }
if (chained !== text) throw new Error('the stages, rebuilt in order, do not give the generation 7 client back unchanged');
// The block's header pins the client around it, so it is checked by content: exactly the plugin's export, in its order.
const carried = catalog.readCatalog(Buffer.from(text, 'latin1')).entries.filter(e => e.id.startsWith('penitent_'));
if (JSON.stringify(carried) !== JSON.stringify(entries)) throw new Error('the client does not carry exactly the plugin\'s export');
const classesAfter = Buffer.from(text, 'latin1');
fs.writeFileSync(path.join(OUT, 'classes.js'), classesAfter);
record('site/classes.js', classesBefore, classesAfter);

// assets.epk: the trinket pack (generation 7's new relic icons included)
const epkBefore = fs.readFileSync(live('assets.epk'));
execFileSync('node', [path.join(__dirname, 'build-trinket-pack.cjs'), '--epk', live('assets.epk'), '--out', path.join(OUT, 'pack')], {stdio: 'pipe', maxBuffer: 64 * 1024 * 1024});
const epkAfter = fs.readFileSync(path.join(OUT, 'pack', 'assets.epk'));
fs.copyFileSync(path.join(OUT, 'pack', 'assets.epk'), path.join(OUT, 'assets.epk'));
record('site/assets.epk', epkBefore, epkAfter);

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
