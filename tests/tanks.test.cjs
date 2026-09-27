'use strict';
// JasprTanks: models and selector, the fenced client stage, touch-control wiring, and the plugin's pure logic
// plus the /tank public-command policy (Java, compiled on the fly against the patched Paper jar).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('models: bands 1-4 select tank hull/turret and sentinel drone/pod, every other iron axe stays vanilla', () => {
  const {models, validate} = require('../scripts/build-tank-assets.cjs');
  const generated = models();
  validate(generated);
  const pack = path.join(root, 'server/custom-plugins/JasprTanks/pack/assets/minecraft/models/item');
  for (const [name, model] of Object.entries(generated))
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(pack, name), 'utf8')), model, name + ' is stale: run node scripts/build-tank-assets.cjs');
  const turret = generated['jaspr_tank_turret.json'];
  const barrel = turret.elements.find(e => /barrel facing negative Z/.test(e.__comment));
  const body = turret.elements.find(e => e.__comment === 'turret body');
  assert.ok(barrel.from[2] < body.from[2], 'barrel points forward (-Z), ahead of the turret');
  const eye = generated['jaspr_sentinel_pod.json'].elements.find(e => /sensor eye facing negative Z/.test(e.__comment));
  assert.ok(eye && eye.from[2] < 8, 'sentinel sensor looks forward (-Z)');
  assert.equal(generated['jaspr_sentinel_drone.json'].elements.filter(e => /^rotor /.test(e.__comment)).length, 4, 'four rotors');
});

test('plugin bands match the model selector', () => {
  const source = read('server/custom-plugins/JasprTanks/src/chat/jaspr/tanks/TanksPlugin.java');
  assert.match(source, /HULL_BAND = 1, TURRET_BAND = 2/);
  assert.match(source, /Material\.IRON_AXE/);
  const tank = read('server/custom-plugins/JasprTanks/src/chat/jaspr/tanks/Tank.java');
  assert.match(tank, /TANK\(\(short\) 1, \(short\) 2\), SENTINEL\(\(short\) 3, \(short\) 4\)/);
});

test('client stage: four fenced hooks, block refresh is idempotent, parses', () => {
  const {build} = require('../scripts/build-tank-client.cjs');
  const live = fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
  const staged = build(live);
  assert.equal(staged.split('/*JASPR_TANK_V1*/').length - 1, 4);
  assert.equal(staged.split('/* JASPR_TANK_V1_BEGIN */').length - 1, 1);
  assert.equal(build(staged), staged, 'refreshing the fenced block is idempotent');
  assert.equal(staged, live, 'site/classes.js carries the current tank block');
  assert.ok(staged.includes('n==="mode"') && staged.includes('n==="pickup"'), 'client reads vehicle mode and pickup');
  for (const hook of ['JasprTank.tick(a);', 'JasprTank.align(a,b);', '!JasprTank.free(b,h)', 'tankView:function()'])
    assert.ok(staged.includes(hook), hook);
  new vm.Script(staged);
});

test('touch controls: claim, fire on swap-hands, hop in/out, view switch, reticle, sentinel mode and taps', () => {
  const file = fs.existsSync(path.join(root, 'site/jaspercraft-mobile-controls.js')) ? 'site/jaspercraft-mobile-controls.js' : 'candidate/tank-client/jaspercraft-mobile-controls.js';
  const js = read(file), css = read(file.replace(/\.js$/, '.css'));
  new vm.Script(js);
  assert.match(js, /b\.text\('\/tank mobile',true\)/, 'claims once the server advertises tanks');
  assert.match(js, /pulse\('bUd'\)/, 'FIRE uses the swap-hands key');
  assert.match(js, /bridge\(\)\.text\('\/tank',true\)/, 'Tank button toggles');
  assert.match(js, /tankView\(\)/, 'View button');
  assert.match(js, /bridge\(\)\.text\('\/tank mode',true\)/, 'Mode button swaps Tank and Orbital Sentinel');
  assert.match(js, /bridge\(\)\.text\('\/tank pickup',true\)/, 'Pickup button toggles automatic pickup');
  assert.match(js, /tapAim\(e\.clientX,e\.clientY\)/, 'a short tap aims and taps (grab an item, lock onto a player)');
  assert.match(js, /'STRIKE'/);
  assert.match(js, /button\('Chat','chat',openChat\)/, 'Chat opens the game chat too, so chat buttons (teleport ACCEPT/DENY) can be tapped');
  const tpa = read('server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/TeleportRequests.java');
  assert.match(tpa, /button\("ACCEPT".*"\/tpaccept"/, 'teleport requests carry a clickable ACCEPT');
  assert.match(tpa, /button\("DENY".*"\/tpdeny"/);
  assert.match(tpa, /button\("CANCEL".*"\/tpcancel"/);
  assert.match(css, /\[data-tank="on"\] \[data-zone="fire"\]/);
  assert.match(css, /\[data-view="third"\] \.jaspr-touch-reticle/);
  assert.match(css, /\[data-vehicle="sentinel"\] \[data-zone="pickup"\]/);
  assert.match(css, /#jaspr-touch \[data-zone="fire"\],#jaspr-touch \[data-zone="tank"\],#jaspr-touch \[data-zone="view"\],#jaspr-touch \[data-zone="mode"\],#jaspr-touch \[data-zone="pickup"\],#jaspr-touch \.jaspr-touch-reticle\{display:none\}/,
    'vehicle controls stay hidden on servers without tanks');
});

function java(sources, main, extraClasspath) {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-tanks-test-'));
  const cp = [path.join(root, 'server/cache/patched_1.12.2.jar'), ...extraClasspath].join(path.delimiter);
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-ea', '-cp', out + path.delimiter + cp, main], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  return run.stdout;
}

test('plugin logic: rays, steering, browser classes, clamped config', {skip: !fs.existsSync(jdk)}, () => {
  const dir = path.join(root, 'server/custom-plugins/JasprTanks/src/chat/jaspr/tanks');
  const sources = fs.readdirSync(dir).map(f => path.join(dir, f)).concat(path.join(root, 'tests/java/chat/jaspr/tanks/TanksLogicTest.java'));
  assert.match(java(sources, 'chat.jaspr.tanks.TanksLogicTest', []), /TANKS_LOGIC_OK/);
});

test('command policy: /tank is public, everything else stays gated', {skip: !fs.existsSync(jdk)}, () => {
  const dir = path.join(root, 'server/custom-plugins/TestServerControl/src/local/eagler/testserver');
  const sources = [path.join(dir, 'PlayerCommandPolicy.java'), path.join(root, 'tests/java/local/eagler/testserver/CommandPolicyTest.java')];
  assert.match(java(sources, 'local.eagler.testserver.CommandPolicyTest', []), /COMMAND_POLICY_OK/);
});
