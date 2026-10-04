'use strict';
/* Builds the wide-inventory server jar (scripts/java/wide-inventory, see JasprWide.java) from the installed Paper jar:
 * compiles the ASM patcher against the jar's own ASM, rewires the jar, compiles JasprWide against the rewired classes
 * (it reads the new PlayerInventory.jasprWide field), and adds it. Also builds the test-only WideProbe plugin.
 * Writes candidate/wide-server/ only (gitignored); the source jar is never modified.
 *
 *   node scripts/build-wide-inventory-server.cjs [--source <server jar>]
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), {execFileSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/server/jaspr-paper-clientbudget.jar';
const JDK = process.env.JASPR_JDK || 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot';
const WIDE = path.join(ROOT, 'scripts', 'java', 'wide-inventory');
const OUT = path.join(ROOT, 'candidate', 'wide-server');
const sha = f => crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const run = (tool, args) => execFileSync(path.join(JDK, 'bin', tool), args, {encoding: 'utf8', windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});

function build(source = LIVE) {
  const work = path.join(OUT, 'work');
  fs.rmSync(work, {recursive: true, force: true});
  for (const d of ['patcher', 'classes', 'probe']) fs.mkdirSync(path.join(work, d), {recursive: true});
  run('javac', ['-nowarn', '-cp', source, '-d', path.join(work, 'patcher'), path.join(WIDE, 'patcher', 'WidePatcher.java')]);
  const cp = path.join(work, 'patcher') + path.delimiter + source;
  const stage1 = path.join(work, 'stage1.jar');
  run('java', ['-cp', cp, 'WidePatcher', source, stage1]);
  run('javac', ['--release', '8', '-nowarn', '-cp', stage1, '-d', path.join(work, 'classes'),
    path.join(WIDE, 'src', 'net', 'minecraft', 'server', 'v1_12_R1', 'JasprWide.java')]);
  const jar = path.join(OUT, 'jaspr-paper-wide.jar');
  const report = run('java', ['-cp', cp, 'WidePatcher', source, jar, path.join(work, 'classes')]);
  if (!/WIDE_PATCH_OK edits=18/.test(report)) throw new Error('unexpected patch report:\n' + report);
  // Test-only probe plugin (never deployed).
  run('javac', ['--release', '8', '-nowarn', '-cp', jar, '-d', path.join(work, 'probe'), path.join(WIDE, 'test', 'probe', 'src', 'wideprobe', 'WideProbe.java')]);
  fs.copyFileSync(path.join(WIDE, 'test', 'probe', 'resources', 'plugin.yml'), path.join(work, 'probe', 'plugin.yml'));
  const probe = path.join(OUT, 'WideProbe.jar');
  fs.rmSync(probe, {force: true});
  run('jar', ['cf', probe, '-C', path.join(work, 'probe'), '.']);
  return {source, sourceSha256: sha(source), jar, sha256: sha(jar), probe, edits: report.trim().split('\n').filter(l => !l.startsWith('WIDE_PATCH_OK')).length};
}

if (require.main === module) {
  const at = process.argv.indexOf('--source');
  console.log(JSON.stringify(build(at > 0 ? path.resolve(process.argv[at + 1]) : LIVE), null, 2));
}
module.exports = {build};
