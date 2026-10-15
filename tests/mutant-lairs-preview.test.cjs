'use strict';
// JasprMutantLairs offline (no server): compiles the twelve lair designs with the Preview tool, draws each one at three seeds
// and checks every drawing (a boss point standing in the open, a vault with its chests and seal, a garrison, sign text that
// fits a sign) and that there are three lairs for each hostile mutant. The server side: tests/mutant-lairs-server.cjs.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), cp = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, 'server/custom-plugins/JasprMutantLairs/src/chat/jaspr/lairs');
const JDK = process.env.JDK_BIN || 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const exe = n => path.join(JDK, n + (process.platform === 'win32' ? '.exe' : ''));
const skip = !fs.existsSync(exe('javac'));
let out = null;
function compile() {
  if (out) return out;
  out = fs.mkdtempSync(path.join(os.tmpdir(), 'lairs-preview-'));
  const files = ['M', 'Canvas', 'Design', 'Designs', 'Preview', 'Planner', 'ZombieLairs', 'SkeletonLairs', 'CreeperLairs', 'EndermanLairs'].map(n => path.join(SRC, n + '.java'));
  cp.execFileSync(exe('javac'), ['-J-XX:ActiveProcessorCount=1', '--release', '8', '-encoding', 'UTF-8', '-Xlint:-options', '-d', path.join(out, 'classes'), ...files], { stdio: 'pipe', windowsHide: true });
  return out;
}
const run = (cls, args) => cp.execFileSync(exe('java'), ['-XX:ActiveProcessorCount=1', '-Xmx512m', '-cp', path.join(compile(), 'classes'), cls, ...args], { encoding: 'utf8', windowsHide: true });

test('every lair draws cleanly at three seeds', { skip }, () => {
  for (const seed of ['1', '12345', '-987654321']) {
    const text = run('chat.jaspr.lairs.Preview', [path.join(compile(), 'pv-' + seed.replace('-', 'm')), 'all', seed]);
    const lairs = text.split(/\r?\n/).filter(l => l.startsWith('LAIR '));
    assert.equal(lairs.length, 12, text);
    for (const l of lairs) assert.match(l, / OK$/, l);
    assert.match(text, /PREVIEW OK/);
    for (const l of lairs) {
      const m = l.match(/chests=(\d+) vault=(\d+) spawners=(\d+) garrisons=(\d+) seals=(\d+)/);
      assert.ok(+m[1] >= 3 && +m[2] >= 2 && +m[4] >= 5 && +m[5] >= 1, l);
    }
  }
});

test('three lairs for each of the four hostile mutants, twelve distinct ids', { skip }, () => {
  const src = fs.readFileSync(path.join(SRC, 'ZombieLairs.java'), 'utf8') + fs.readFileSync(path.join(SRC, 'SkeletonLairs.java'), 'utf8')
    + fs.readFileSync(path.join(SRC, 'CreeperLairs.java'), 'utf8') + fs.readFileSync(path.join(SRC, 'EndermanLairs.java'), 'utf8');
  const kinds = {};
  for (const m of src.matchAll(/super\("([a-z_]+)", "[^"]+", "(mutant_[a-z]+)"/g)) kinds[m[2]] = (kinds[m[2]] || []).concat(m[1]);
  assert.deepEqual(Object.keys(kinds).sort(), ['mutant_creeper', 'mutant_enderman', 'mutant_skeleton', 'mutant_zombie']);
  for (const k in kinds) assert.equal(kinds[k].length, 3, k);
  assert.equal(new Set(Object.values(kinds).flat()).size, 12);
});
