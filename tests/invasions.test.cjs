'use strict';
// JasprInvasions and sleep (owner 2026-09-30): clicking a bed started an invasion on the spot and refused the sleep,
// every night. Now sleeping always works; a night in bed marks the player, and their invasion comes on a night at
// least 7 Minecraft days later (InvasionScheduleTest); the shipped plugin carries it.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const paper = path.join(root, 'server/cache/patched_1.12.2.jar');
const plugin = path.join(root, 'server/custom-plugins/JasprInvasions');
const src = path.join(plugin, 'src/chat/jaspr/invasions');
const haveJava = fs.existsSync(jdk) && fs.existsSync(paper);

test('sleeping marks you; the invasion comes on a night 7+ days later, never on the spot', {skip: !haveJava, timeout: 300000}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-invasions-test-'));
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  const javac = spawnSync(path.join(jdk, 'javac'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-Xlint:-options', '-proc:none', '-cp', paper, '-d', out,
    ...sources, path.join(root, 'tests/java/chat/jaspr/invasions/InvasionScheduleTest.java')], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java'), ['-cp', out + path.delimiter + paper, 'chat.jaspr.invasions.InvasionScheduleTest', path.join(plugin, 'resources/config.yml')], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /INVASION_SCHEDULE_OK/);
});

test('a bed never starts an invasion or refuses the sleep by itself', () => {
  const listener = fs.readFileSync(path.join(src, 'InvasionListener.java'), 'utf8');
  const main = fs.readFileSync(path.join(src, 'InvasionPlugin.java'), 'utf8');
  assert.ok(!main.includes('tryBedSummon') && !listener.includes('tryBedSummon'), 'no summon from the bed');
  // The only refusal is the opt-in prevent-sleep during a running invasion.
  assert.equal((listener.match(/setCancelled\(true\)/g) || []).length, 1, 'the opt-in sleep refusal is the only cancel');
  assert.ok(listener.includes('if (!plugin.settings().preventSleep || !plugin.isBeingInvaded(player.getUniqueId())) return;'));
  assert.ok(listener.includes('player.isSleeping()) plugin.noteSleep(player)'), 'only a sleep that happened marks you');
  assert.ok(main.includes('private void summonDue()') && main.includes('"sleep+" + waited + "d"'));
  const config = fs.readFileSync(path.join(plugin, 'resources/config.yml'), 'utf8');
  assert.match(config, /\n {2}days-after-sleep: 7\n/);
  assert.match(config, /\n {2}prevent-sleep: false\n/);
});

test('the shipped jar is the new build', {skip: !haveJava}, () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-inv-jar-'));
  const x = spawnSync(path.join(jdk, 'jar'), ['xf', path.join(root, 'server/plugins/JasprInvasions.jar'), 'plugin.yml', 'config.yml'], {encoding: 'utf8', cwd: dir});
  assert.equal(x.status, 0, x.stderr);
  const yml = fs.readFileSync(path.join(dir, 'plugin.yml'), 'utf8'), config = fs.readFileSync(path.join(dir, 'config.yml'), 'utf8');
  fs.rmSync(dir, {recursive: true, force: true});
  assert.match(yml, /version: 1\.1\.0/);
  assert.match(config, /days-after-sleep: 7/);
});
