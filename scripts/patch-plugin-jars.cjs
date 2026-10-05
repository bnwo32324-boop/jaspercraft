'use strict';
// Rebuilds the changed plugins' jars from the live jars: compiles each plugin's sources, compares every class with the one in
// the live jar, and writes a copy of the live jar in which only the classes that differ (and plugin.yml) are replaced. Every other
// entry stays byte-identical, so a small source change ships as a small, reviewable jar change.
//   node scripts/patch-plugin-jars.cjs --game <live checkout> --out <folder> [Plugin ...]
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), os = require('node:os');
const {spawnSync, execFileSync} = require('node:child_process');

const ROOT = path.resolve(__dirname, '..');
const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : null; };
const GAME = arg('--game') || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const OUT = arg('--out') || path.join(ROOT, 'candidate', 'jars');
const WANT = process.argv.slice(2).filter((a, i, all) => !a.startsWith('--') && !(i > 0 && all[i - 1].startsWith('--')));
const PLUGINS = ['JasprApocalypse', 'JasprDungeon', 'JasprRuins', 'JasprNether', 'JasprBackrooms', 'JasprRPG'];
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const walk = (dir, base = dir) => fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name), base) : [path.relative(base, path.join(dir, e.name)).split(path.sep).join('/')]);

function run(cmd, args, opts) {
  const r = spawnSync(cmd, args, {encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...opts});
  if (r.status !== 0) throw new Error(path.basename(cmd) + ' failed: ' + (r.stdout + r.stderr).slice(0, 3000));
  return r;
}

const classpath = [path.join(GAME, 'server/cache/patched_1.12.2.jar'), ...fs.readdirSync(path.join(GAME, 'server/plugins')).filter(f => f.endsWith('.jar')).map(f => path.join(GAME, 'server/plugins', f))].join(path.delimiter);
fs.mkdirSync(OUT, {recursive: true});
const report = {};
for (const name of PLUGINS.filter(p => !WANT.length || WANT.includes(p))) {
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-jar-' + name + '-'));
  const classes = path.join(work, 'classes'), live = path.join(work, 'live');
  fs.mkdirSync(classes); fs.mkdirSync(live);
  const src = path.join(ROOT, 'server/custom-plugins', name, 'src');
  const sources = walk(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  run(path.join(JDK, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xlint:-options', '-cp', classpath, '-d', classes, ...sources]);
  const liveJar = path.join(GAME, 'server/plugins', name + '.jar');
  run(path.join(JDK, 'jar.exe'), ['xf', liveJar], {cwd: live});
  const changed = [];
  for (const file of walk(classes)) {
    const before = path.join(live, file);
    if (!fs.existsSync(before) || sha(fs.readFileSync(before)) !== sha(fs.readFileSync(path.join(classes, file)))) changed.push(file);
  }
  const yml = path.join(ROOT, 'server/custom-plugins', name, 'resources', 'plugin.yml');
  const ymlChanged = sha(fs.readFileSync(yml)) !== sha(fs.readFileSync(path.join(live, 'plugin.yml')));
  const target = path.join(OUT, name + '.jar');
  fs.copyFileSync(liveJar, target);
  const args = ['uf', target];
  for (const file of changed) args.push('-C', classes, file);
  if (ymlChanged) args.push('-C', path.dirname(yml), 'plugin.yml');
  if (changed.length || ymlChanged) run(path.join(JDK, 'jar.exe'), args);
  report[name] = {liveSha256: sha(fs.readFileSync(liveJar)), sha256: sha(fs.readFileSync(target)), bytes: fs.statSync(target).size, changedClasses: changed, pluginYml: ymlChanged,
    removedFromSource: walk(live).filter(f => f.endsWith('.class') && !fs.existsSync(path.join(classes, f)))};
  fs.rmSync(work, {recursive: true, force: true});
}
fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
