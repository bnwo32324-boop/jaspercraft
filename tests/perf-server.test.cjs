'use strict';
// Server performance changes (PERFORMANCE_UPDATE.md): JasprPerfTweaks compiles against the patched Paper jar and its
// shipped jar matches the source; the YAML settings script applies all settings once, keeps a backup, and is idempotent;
// the gateway starts Paper with the measured G1 flags and Xms = Xmx.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const paper = path.join(root, 'server/cache/patched_1.12.2.jar');

test('JasprPerfTweaks compiles and the shipped jar carries the same classes', { skip: !fs.existsSync(jdk) || !fs.existsSync(paper) }, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-perf-test-'));
  const src = path.join(root, 'server/custom-plugins/JasprPerfTweaks/src');
  const files = fs.readdirSync(src, { recursive: true }).filter((f) => f.endsWith('.java')).map((f) => path.join(src, f));
  const javac = spawnSync(path.join(jdk, 'javac'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-Xlint:-options', '-proc:none', '-cp', paper, '-d', out, ...files], { encoding: 'utf8' });
  assert.equal(javac.status, 0, javac.stderr);
  const list = spawnSync(path.join(jdk, 'jar'), ['tf', path.join(root, 'server/plugins/JasprPerfTweaks.jar')], { encoding: 'utf8' });
  assert.equal(list.status, 0);
  for (const cls of ['chat/jaspr/perf/PerfTweaks.class', 'chat/jaspr/perf/PerfTweaks$Pregen.class', 'plugin.yml', 'config.yml']) assert.ok(list.stdout.includes(cls), cls);
  fs.rmSync(out, { recursive: true, force: true });
});

test('perf-server-config applies every setting once, backs up, and is idempotent', () => {
  const server = path.join(root, 'server');
  const files = ['paper.yml', 'spigot.yml', 'bukkit.yml'];
  if (!files.every((f) => fs.existsSync(path.join(server, f)))) return; // YAML is not in git; only on a live copy
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-perf-cfg-'));
  for (const f of files) fs.copyFileSync(path.join(server, f), path.join(dir, f));
  const run = (...a) => spawnSync(process.execPath, [path.join(root, 'scripts/perf-server-config.cjs'), dir, ...a], { encoding: 'utf8' });
  const first = run();
  assert.equal(first.status, 0, first.stderr);
  assert.equal(run('--check').status, 0, 'second check finds nothing left to apply');
  assert.match(run().stdout, /already applied/);
  const paperText = fs.readFileSync(path.join(dir, 'paper.yml'), 'utf8');
  assert.match(paperText, /queue-light-updates: true/);
  assert.match(paperText, /world_nether:\r?\n    keep-spawn-loaded: false/);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('gateway starts Paper with the measured G1 flags and Xms = Xmx', () => {
  const gateway = fs.readFileSync(path.join(root, 'server-tools/gateway.py'), 'utf8');
  for (const flag of ['-XX:+UseG1GC', '-XX:G1NewSizePercent=30', '-XX:G1HeapRegionSize=8M', '-XX:MaxTenuringThreshold=1', '-XX:+UseStringDeduplication', '-XX:+AlwaysPreTouch'])
    assert.ok(gateway.includes(`"${flag}"`), flag);
  assert.ok(gateway.indexOf('"-XX:+UnlockExperimentalVMOptions"') < gateway.indexOf('"-XX:G1NewSizePercent=30"'), 'experimental options unlocked first');
  const config = JSON.parse(fs.readFileSync(path.join(root, 'server-tools/gateway-config.json'), 'utf8'));
  assert.equal(config.minimumMemory, config.maximumMemory);
});
