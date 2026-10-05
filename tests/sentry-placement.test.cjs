'use strict';
// Sentry turret placement (owner 2026-10-05): "placed down anywhere as long as it doesn't clip through the ceiling, so
// about two blocks ... get rid of the logic that says they need space horizontally". Only the body-overlap geometry is
// compiled and run (no server); the plugin source is checked for the rules that use it.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const plugin = path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse');

test('placement geometry: only a body really inside the block refuses a mount', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-sentry-place-'));
  const sources = [path.join(plugin, 'SentryPlacement.java'), path.join(root, 'tests/java/chat/jaspr/apocalypse/SentryPlacementTest.java')];
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-cp', out, 'chat.jaspr.apocalypse.SentryPlacementTest'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /SentryPlacementTest PASS/);
});

test('turret mount rules: two blocks of headroom, no horizontal space, exact body overlap', () => {
  const turret = fs.readFileSync(path.join(plugin, 'SentryTurret.java'), 'utf8');
  const room = turret.slice(turret.indexOf('private String roomFor('), turret.indexOf('private void faceDispenser('));
  assert.ok(room.length > 200, 'roomFor found');
  assert.match(turret, /private static final int PLACE_HEADROOM = 2;/, 'the head needs two blocks above the body, as before');
  assert.match(room, /SentryPlacement\.overlaps\(/, 'players refuse a mount only when their body is inside the block');
  assert.doesNotMatch(room, /getNearbyEntities\([^)]*,\s*1\s*,\s*1\s*,\s*1\s*\)/, 'no one-block scan around the mount any more');
  for (const rule of room.split('\n').filter(l => /return "/.test(l))) {
    assert.doesNotMatch(rule, /horizontal|sideways|beside|around|space/i, 'no message asks for room beside the sentry: ' + rule.trim());
  }
  assert.match(turret, /type == Material\.LONG_GRASS \|\| type == Material\.DEAD_BUSH/, 'plants a vanilla block replaces are mountable');
  assert.match(turret, /Sentries mount on floors and walls, not ceilings/, 'ceilings stay refused');
});
