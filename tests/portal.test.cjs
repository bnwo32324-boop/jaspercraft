'use strict';
// JasprPortal: portals take their frame's colour (mossy cobblestone gates to Drownhollow, once Ul'Nhaar, are green, obsidian Nether portals
// stay purple). The fenced client stage (scripts/build-portal-client.cjs) and the neutral portal texture with tinted
// models are installed in site/, rebuild byte-for-byte, and the browsers are told to fetch them.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('portal stage: four fenced hooks, block refresh is idempotent, installed, parses', () => {
  const {build} = require('../scripts/build-portal-client.cjs');
  const live = fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
  const staged = build(live);
  assert.equal(staged.split('/*JASPR_PORTAL_V1*/').length - 1, 4);
  assert.equal(staged.split('/* JASPR_PORTAL_V1_BEGIN */').length - 1, 1);
  for (const hook of ['JasprSky.tick(a);/*JASPR_PORTAL_V1*/JasprPortal.tick(a);', 'h=$z;/*JASPR_PORTAL_V1*/if(h===90)return JasprPortal.tint(d);',
    '/*JASPR_PORTAL_V1*/JasprPortal.particle(k,d,e,f,l);return k;', '/*JASPR_PORTAL_V1*/JasprPortal.overlay(a.ds);'])
    assert.ok(staged.includes(hook), hook);
  assert.ok(staged.includes('var FRAMES={48:'), 'mossy cobblestone frames are coloured');
  assert.equal(build(staged), staged, 'refreshing the fenced block is idempotent');
  assert.equal(staged, live, 'site/classes.js carries the current portal block');
  new vm.Script(staged);
});

test('portal assets: neutral animation, tinted faces, everything else untouched, fetched afresh', () => {
  const {buildAssets} = require('../scripts/build-portal-client.cjs');
  const {decode} = require('../scripts/merge-apocalypse-assets.cjs');
  const live = fs.readFileSync(path.join(root, 'site/assets.epk'));
  const neutral = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprRuins/pack/portal-neutral.png'));
  const byName = new Map(decode(live).entries.map(e => [e.name, e.value]));
  assert.ok(byName.get('assets/minecraft/textures/blocks/portal.png').equals(neutral), 'portal.png is the neutral animation');
  for (const name of ['portal_ns', 'portal_ew']) {
    const model = JSON.parse(byName.get('assets/minecraft/models/block/' + name + '.json').toString('utf8'));
    for (const element of model.elements) for (const face of Object.values(element.faces)) assert.equal(face.tintindex, 0, name);
  }
  assert.ok(buildAssets(live, neutral).equals(live), 'rebuilding the installed archive changes nothing');
  assert.ok(read('site/jaspr-client.js').includes('assets.epk?build=20260927-portal1'), 'browsers fetch the new archive');
  assert.ok(read('site/client.html').includes('classes.js?v=20260929-cloud1') && read('site/client.html').includes('jaspr-client.js?build=20260927-atlas1'));
});

test('gates: linked pairs, return to the gate you came from, no ping-pong, step-down arrival, stray vanilla arrivals', () => {
  const portals = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Portals.java');
  for (const k of ['cameThrough', 'mustLeave', 'route=', 'RUINS_PORTAL_STRAY_ARRIVAL', 'for (int drop = 0; drop <= 3; drop++)', 'String id()', 'private Portal touching(Location l, double halfWidth, double height)', 'RUINS_PORTAL_VANILLA_BLOCKED', 'RUINS_PORTAL_ADOPTED'])
    assert.ok(portals.includes(k), k);
  const plugin = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/RuinsPlugin.java');
  assert.ok(plugin.includes('setMonsterSpawnLimit((int) Math.round(75 * EASE))') && plugin.includes('static final double EASE = 0.5;'), 'the monster cap halved again (38)');
  // Owner, 2026-09-28: half the difficulty and half the spawns, and the city renamed Drownhollow.
  const horrors = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Horrors.java');
  assert.ok(horrors.includes('e.setDamage(e.getDamage() * RuinsPlugin.EASE);'), 'hostile damage to players halved');
  assert.ok(horrors.includes('kind.health * RuinsPlugin.EASE') && horrors.includes('SpawnReason.SPAWNER && random.nextDouble() >= RuinsPlugin.EASE'), 'half health, spawners at half rate');
  assert.ok(read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Bosses.java').includes('boss.health * RuinsPlugin.EASE'), 'bosses at half health');
  const trinkets = read('server/custom-plugins/JasprRuins/src/chat/jaspr/ruins/Trinkets.java');
  assert.ok(trinkets.includes('"Relic of Drownhollow - "') && trinkets.includes('marker(item, OLD_RELIC)') && trinkets.includes('marker(item, OLD_SEAL)'), 'renamed; old relics and seals still count');
  // 2026-09-29: the gate guide kit's compass replaced the Drowned Star Compass; the kit aims every compass a player carries.
  assert.ok(plugin.includes('guide.arrive(p, quest.takeGate(p));') && !plugin.includes('OLD_COMPASS_MARK'), 'the guide kit greets arrivals');
});
