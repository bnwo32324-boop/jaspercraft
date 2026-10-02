'use strict';
// Sentry turret upgrade tree (owner, 2026-10-02: "Turret upgrade tree ... it should also change the look of the turret
// itself. The model should change with each upgrade. It should also change what sound effects it makes. Also, make the
// baseline initial turret, unupgraded, nerfed a little bit, and make it a little bit more expensive to craft.")
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zfight = require('../apocalypse-pack/zfight.cjs');
const upgrades = require('../apocalypse-pack/sentry-upgrades.cjs');

const root = path.resolve(__dirname, '..');
const models = path.join(root, 'apocalypse-pack/assets/minecraft/models/item');
const java = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/SentryTurret.java'), 'utf8');
const read = name => JSON.parse(fs.readFileSync(path.join(models, name + '.json'), 'utf8'));

test('every tier has its own head model on the shared, flush mount', () => {
  const base = read('apocalypse_sentry_turret');
  const shapes = new Set();
  for (const [id, damage, name] of upgrades.TIERS) {
    const model = read(name);
    assert.ok(model.elements.length >= 4 && model.elements.length <= 32, name + ' geometry budget');
    assert.equal(zfight.conflicts(model.elements).length, 0, name + ' z-fighting');
    assert.deepEqual(model.display.head, base.display.head, name + ' head transform');
    const plate = model.elements.find(e => e.__comment === 'base plate');
    assert.deepEqual([plate.from, plate.to], [[1.25, -3.5, 1.25], [14.75, -0.5, 14.75]], name + ' sits flush on the dispenser');
    // the working end points to -Z (the head turns it toward the target)
    const front = Math.min(...model.elements.map(e => e.from[2]));
    assert.ok(front < 0, name + ' reaches forward');
    const shape = JSON.stringify(model.elements.map(e => [e.from, e.to]));
    assert.ok(!shapes.has(shape), name + ' looks different from every other tier');
    shapes.add(shape);
    // the Java tier agrees with the model's damage value
    assert.match(java, new RegExp('\\("' + id + '", "[^"]+", "Mk [IV]+", ' + damage + ','), id + ' -> ' + damage);
  }
  assert.equal(upgrades.TIERS.length, 8);
});

test('the tree: Sentry, Reinforced, then Gatling, Cannon or Tesla, each with a second stage', () => {
  const parent = id => (java.match(new RegExp('\\("' + id + '", "[^"]+", "Mk [IV]+", \\d+, ([A-Z_]+|null),')) || [])[1];
  assert.equal(parent('sentry'), 'null');
  assert.equal(parent('reinforced'), 'SENTRY');
  for (const branch of ['gatling', 'cannon', 'tesla']) assert.equal(parent(branch), 'REINFORCED', branch);
  assert.equal(parent('storm_gatling'), 'GATLING');
  assert.equal(parent('howitzer'), 'CANNON');
  assert.equal(parent('arc_tower'), 'TESLA');
  // only the owner (or an operator / Creative) upgrades, paying from their inventory; collected turrets keep the upgrade
  assert.match(java, /private boolean mayUpgrade\(Player player, Turret turret\)/);
  assert.match(java, /take\(player, \(Material\) pick\.cost\[i\], \(Integer\) pick\.cost\[i \+ 1\]\)/);
  assert.match(java, /addItem\(itemFor\(turret\.tier\)\)/);
  assert.match(java, /turret\.tier = tierOf\(held\);/);
  assert.match(java, /map\.put\("tier", turret\.tier\.id\);/);
  assert.match(java, /SENTRY_UPGRADE player=/);
});

test('each tier sounds like itself, firing and locking on', () => {
  const block = (name) => java.slice(java.indexOf('static void ' + name + '('), java.indexOf('}', java.indexOf('default:', java.indexOf('static void ' + name + '('))));
  for (const fn of ['fireSound', 'lockSound']) {
    const body = block(fn);
    const sounds = new Map();
    for (const m of body.matchAll(/case ([A-Z_]+):[^]*?Sound\.([A-Z_]+)/g)) sounds.set(m[1], m[2]);
    const def = (body.match(/default: (?:world\.playSound\(muzzle, )?Sound\.([A-Z_]+)|default:\s*world\.playSound\(muzzle, Sound\.([A-Z_]+)/) || []);
    const plain = def[1] || def[2];
    assert.ok(plain, fn + ' has a sound for the plain sentry');
    for (const tier of ['REINFORCED', 'GATLING', 'STORM_GATLING', 'CANNON', 'HOWITZER', 'TESLA', 'ARC_TOWER']) {
      assert.ok(sounds.has(tier), fn + ' ' + tier);
      assert.notEqual(sounds.get(tier), plain, fn + ' ' + tier + ' differs from the plain sentry');
    }
  }
});

test('the plain sentry is a little weaker and a little dearer', () => {
  assert.match(java, /getDouble\("sentry\.slow-damage", 13\.0\)/);
  assert.match(java, /getDouble\("sentry\.normal-damage", 10\.0\)/);
  assert.match(java, /getDouble\("sentry\.fast-damage", 7\.0\)/);
  const config = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/resources/config.yml'), 'utf8');
  assert.match(config, /slow-damage: 13\.0[\s\S]*normal-damage: 10\.0[\s\S]*fast-damage: 7\.0/);
  // the cheap 4-ingot recipe is gone; the blueprint (7 ingots, an iron block, redstone) is the only one
  assert.doesNotMatch(java, /recipe\.shape\(" I ", "IBI", " I "\)/);
  const blueprints = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/Blueprints.java'), 'utf8');
  assert.match(blueprints, /add\(map, "sentry_turret", "other", "III", "IiI", "IRI"\);/);
  // gatling rounds land between recovery frames; shots are credited to the owner; Engineering raises the damage
  assert.match(java, /if \(pierce\) target\.setNoDamageTicks\(0\);/);
  assert.match(java, /static final String OWNER_META = "jaspr_sentry_owner";/);
  assert.match(java, /getMethod\("turretMultiplier", UUID\.class\)/);
  // the file is no longer written on every shot
  assert.doesNotMatch(java.slice(java.indexOf('private void fire('), java.indexOf('private LivingEntity nextChain(')), /\bsave\(\);/);
});
