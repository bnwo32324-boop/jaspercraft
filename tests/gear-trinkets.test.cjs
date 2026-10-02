'use strict';
// JasprGear 4.0.0 (owner, 2026-10-02: "Add twice the amount of trinkets and baubles (make sure some of them are
// exclusive to certain dimensions)"): 16 -> 32 trinkets; eight found only in their realm (two each: the Nether,
// Drownhollow, Atlas, the Backrooms). Compiles the plugin with tests/java/chat/jaspr/gear/GearCheck.java, then checks
// the wiring, the art and the turret hook.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');

test('trinkets: 32, eight realm-only, models, recipes, loot', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-gear-'));
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/gear/GearCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx384m', '-cp', out + path.delimiter + cp, 'chat.jaspr.gear.GearCheck'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /GEAR_OK trinkets=32 realm=8 craftable=24/);
});

test('trinkets: every one has its own 16x16 art', () => {
  const ids = [...java('GearItem').matchAll(/^\s+[A-Z_]+\("([a-z_]+)", "/gm)].map(m => m[1]);
  assert.equal(ids.length, 32);
  const pack = fs.readFileSync(path.join(root, 'scripts/build-gear-pack.cjs'), 'utf8');
  for (const id of ids) assert.ok(new RegExp('(^\\s+' + id + ': \\[|ART\\.' + id + ' = )', 'm').test(pack), 'art for ' + id);
});

test('trinkets: the new mechanics are wired', () => {
  const plugin = java('GearPlugin'), abilities = java('GearAbilities'), extras = java('GearExtras'), api = java('GearApi'), vitals = java('GearVitals');
  // realm drops: only creatures slain in the realm, more from elites and bosses, never from spawners (except bosses)
  assert.match(plugin, /public void onRealmDeath\(EntityDeathEvent e\)/);
  assert.match(plugin, /GearItem\.ofRealm\(dead\.getWorld\(\)\.getName\(\)\)/);
  assert.match(plugin, /if \(!bossLike && \(!GearExtras\.hostileAnywhere\(dead\) \|\| spawned\(dead\)\)\) return;/);
  assert.match(plugin, /GEAR_REALM_DROP item=/);
  assert.match(plugin, /if \(!item\.craftable\(\)\) continue;   \/\/ realm trinkets are only found/);
  // standing bonuses are attribute modifiers; the rest are event multipliers
  for (const item of ['TRENCH_COAT', 'LUCKY_COIN', 'VIGIL_RING', 'DOMINION_SIGNET', 'TITANS_GIRDLE', 'EXIT_SIGN'])
    assert.match(abilities, new RegExp('modifier\\(GearItem\\.' + item + ','), item);
  for (const handler of ['onShoot(EntityShootBowEvent', 'onHit(EntityDamageByEntityEvent', 'onHurt(EntityDamageEvent', 'onKill(EntityDeathEvent', 'onRegain(EntityRegainHealthEvent'])
    assert.ok(extras.includes('public void ' + handler), handler);
  // the Exit Sign's noclip works only in the Backrooms, never through bedrock or barriers, and spends adrenaline
  assert.match(abilities, /GearExtras\.BACKROOMS\.equals\(p\.getWorld\(\)\.getName\(\)\)/);
  assert.match(extras, /return m == Material\.BEDROCK \|\| m == Material\.BARRIER/);
  assert.match(vitals, /worn\.contains\(GearItem\.EXIT_SIGN\)/);
  // arrows that come back cannot also be picked up (no duplication)
  assert.match(extras, /arrow\.setPickupStatus\(Arrow\.PickupStatus\.CREATIVE_ONLY\);/);
  // copies of drops are only ever plain items
  assert.match(extras, /if \(!plain\(drop\)\) continue;/);
  // the Engineer's Toolbelt reaches the sentries through the public API
  assert.match(api, /public static boolean wearing\(org\.bukkit\.entity\.Player player, String gearId\)/);
  const sentry = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/SentryTurret.java'), 'utf8');
  assert.match(sentry, /wearing\.invoke\(null, owner, "engineers_toolbelt"\)/);
  // no night vision, glowing or light (owner's mood rule)
  assert.doesNotMatch(extras, /NIGHT_VISION|GLOWING/);
  // the deploy self-test knows the new roster
  const selftest = java('GearSelfTest');
  assert.match(selftest, /check\(GearItem\.values\(\)\.length == 32 && realmItems == 8, "thirty-two trinkets, eight of them realm-only"\);/);
  assert.match(selftest, /structure loot never rolls a realm trinket/);
});
