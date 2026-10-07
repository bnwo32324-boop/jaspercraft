'use strict';
// Compiles the Field Journal plugin (JasprJournal) against the patched server jar and packs it: classes + plugin.yml, nothing else.
// One processor for javac (the owner's PC must not be loaded: see the load-limit notes), Java 8 bytecode like every plugin here.
//   node scripts/build-journal-plugin.cjs [--out <jar>] [--game <checkout with server/cache/patched_1.12.2.jar>]
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), crypto = require('node:crypto');
const {spawnSync} = require('node:child_process');
const ROOT = path.resolve(__dirname, '..');
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const JDK = process.env.JAVA17_HOME ? path.join(process.env.JAVA17_HOME, 'bin') : 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const OUT = path.resolve(arg('--out') || path.join(ROOT, 'server', 'plugins', 'JasprJournal.jar'));
const GAME = arg('--game') || ROOT;
const PLUGIN = path.join(ROOT, 'server', 'custom-plugins', 'JasprJournal');
const walk = dir => fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
function run(tool, args, cwd) {
  const r = spawnSync(path.join(JDK, tool + '.exe'), args, {encoding: 'utf8', cwd, windowsHide: true, maxBuffer: 32 * 1024 * 1024});
  if (r.status !== 0) throw new Error(tool + ' failed:\n' + (r.stdout + r.stderr).slice(0, 4000));
}
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-journal-build-'));
try {
  const classes = path.join(work, 'classes');
  fs.mkdirSync(classes);
  const sources = walk(path.join(PLUGIN, 'src')).filter(f => f.endsWith('.java')).sort();
  run('javac', ['-J-XX:ActiveProcessorCount=1', '--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xlint:-options',
    '-cp', path.join(GAME, 'server', 'cache', 'patched_1.12.2.jar'), '-d', classes, ...sources], work);
  fs.copyFileSync(path.join(PLUGIN, 'resources', 'plugin.yml'), path.join(classes, 'plugin.yml'));
  fs.mkdirSync(path.dirname(OUT), {recursive: true});
  fs.rmSync(OUT, {force: true});
  run('jar', ['-J-XX:ActiveProcessorCount=1', '--create', '--file', OUT, '--no-manifest', '-C', classes, '.'], work);
  const sha = crypto.createHash('sha256').update(fs.readFileSync(OUT)).digest('hex');
  console.log(JSON.stringify({jar: OUT, bytes: fs.statSync(OUT).size, sha256: sha, classes: walk(classes).length - 1}));
} finally {
  fs.rmSync(work, {recursive: true, force: true});
}
