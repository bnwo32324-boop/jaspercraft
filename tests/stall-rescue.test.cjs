'use strict';
// TestServerControl stall rescue: a player whose browser game keeps freezing at one spot (keepalives unanswered)
// is moved off it; the pure bookkeeping is compiled and run against the patched Paper jar.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/TestServerControl/src/local/eagler/testserver');

test('stall rescue: what counts as a stall, and a repeat at the same spot within 15 minutes', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-stall-test-'));
  const cp = [path.join(root, 'server/cache/patched_1.12.2.jar')].join(path.delimiter);
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out,
    path.join(src, 'StallRescue.java'), path.join(root, 'tests/java/local/eagler/testserver/StallRescueTest.java')], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-ea', '-cp', out + path.delimiter + cp, 'local.eagler.testserver.StallRescueTest'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /STALL_RESCUE_OK/);
});

test('stall rescue is wired: quit, join, /unstick owner command, and privacy-safe log events', () => {
  const plugin = fs.readFileSync(path.join(src, 'TestServerControlPlugin.java'), 'utf8');
  assert.match(plugin, /stallRescue\.playerQuit\(event\.getPlayer\(\)\)/);
  assert.match(plugin, /stallRescue\.playerJoined\(player\)/);
  assert.match(plugin, /equalsIgnoreCase\("unstick"\)/);
  const yml = fs.readFileSync(path.join(root, 'server/custom-plugins/TestServerControl/resources/plugin.yml'), 'utf8');
  assert.match(yml, /unstick:[\s\S]*permission: testserver\.unstick/);
  assert.match(yml, /testserver\.unstick:\s*\n\s*description:[^\n]*\n\s*default: op/);
  const rescue = fs.readFileSync(path.join(src, 'StallRescue.java'), 'utf8');
  assert.match(rescue, /JASPR_NET event=client_stall/);
  assert.match(rescue, /JASPR_NET event=stall_rescue/);
  assert.match(rescue, /root\.remove\("RootVehicle"\)/, 'the stuck vehicle is dropped from the saved player');
  assert.doesNotMatch(rescue, /getAddress|getHostString/, 'no addresses in the log');
});
