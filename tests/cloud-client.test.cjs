'use strict';
// Area effect clouds: the browser client registered the cloud's six data keys at ids 8..13 while a 1.12.2 server sends
// them at 6..11, so every cloud (lingering potions, dragon breath, the Nether's spore clouds) stored the server's
// "waiting" Boolean as its radius and the client's particle loop (bound pi*r*r = NaN) never ended: the tab froze.
// scripts/build-cloud-client.cjs renumbers the keys in place; the Nether draws its spore cloud with particles only.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {CLIENT_BUILD} = require('./client-build.cjs');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const live = () => fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');

test('cloud stage: keys at the server ids 6..11, installed, idempotent, offsets kept, reversible', () => {
  const {build, revert, cloudKeyIds, BROKEN, FIXED} = require('../scripts/build-cloud-client.cjs');
  const text = live();
  assert.deepEqual(cloudKeyIds(text), [6, 7, 8, 9, 10, 11], 'site/classes.js carries the fix');
  assert.equal(build(text), text, 'rebuilding the installed client changes nothing');
  assert.equal(FIXED.length, BROKEN.length, 'the fix keeps every byte offset (the source map stays exact)');
  const before = revert(text);
  assert.deepEqual(cloudKeyIds(before), [8, 9, 10, 11, 12, 13], 'the reversal restores the old ids');
  assert.equal(build(before), text, 'building from the old client gives the installed one');
  new vm.Script(text);
  assert.ok(read('site/client.html').includes('classes.js?v=' + CLIENT_BUILD), 'browsers fetch the fixed client');
  assert.ok(read('site/jaspr-sso.js').includes('client.html?build=20260929-cloud1') && read('site/index.html').includes('jaspr-sso.js?build=20260929-cloud1'));
});

test('cloud stage: refuses a client whose cloud accessors or key initialiser changed', () => {
  const {build, revert} = require('../scripts/build-cloud-client.cjs');
  const old = revert(live());
  assert.throws(() => build(old.replace('c=LfI;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;return c.fB;', () => 'c=LfI;$p=2;case 2:$z=E24(b,c);if(B()){break _;}c=$z;return c.fC;')), /re-audit/);
  assert.throws(() => build(old.replace('LfJ=C0(9,Ks7)', 'LfJ=C0(5,Ks7)')), /re-audit/);
});

test('the radius read: with the old ids a cloud radius is undefined and the particle bound NaN; with the fix it is the radius', () => {
  // The metadata a 1.12.2 server sends for a spore cloud, by id: 6 radius (Float), 7 colour, 8 waiting (Boolean), 9..11 particle.
  const sent = {6: {fB: 4.2}, 7: {bn: 0x8E6028}, 8: {br: 0}, 9: {bn: 15}, 10: {bn: 0}, 11: {bn: 0}};
  const radius = ids => { const slot = sent[ids[0]]; return slot && slot.fB; };
  const bound = r => 3.1415927410125732 * r * r;
  const old = bound(radius([8, 9, 10, 11, 12, 13]));
  assert.ok(Number.isNaN(old), 'old ids: pi*r*r is NaN, and "e >= NaN" never ends the loop');
  assert.ok(Math.abs(bound(radius([6, 7, 8, 9, 10, 11])) - 55.4) < 0.1, 'fixed ids: 55 particles a tick');
});

test('nether: the spore burst draws its cloud with particles, never an AreaEffectCloud entity', () => {
  const mobs = read('server/custom-plugins/JasprNether/src/chat/jaspr/nether/Mobs.java');
  assert.ok(!/AREA_EFFECT_CLOUD|AreaEffectCloud\s+cloud/.test(mobs), 'no cloud entity is spawned');
  assert.ok(mobs.includes('sporeCloud(l, (float) radius * 0.7f);') && mobs.includes('w.spawnParticle(Particle.SPELL_MOB,'));
  const self = read('server/custom-plugins/JasprNether/src/chat/jaspr/nether/SelfTest.java');
  assert.ok(self.includes('check("spore_cloud_particles_only"'), 'the self-test checks it in game');
});
