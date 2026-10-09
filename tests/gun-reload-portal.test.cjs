'use strict';
// Owner 2026-10-05: "Guns should reload automatically when their magazine is empty, provided there are enough bullets in your
// inventory. Still preserve the shift-reload." and "[the portal gun] should just alternate between blue and orange. There should be
// no shift-click necessary because it just alternates every time you place a portal."
// The behaviour itself is exercised on a real Paper server by tests/guns-smoke.cjs (GunsProbe); these are the source contracts
// that keep it from drifting, and the documentation of what players are told.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = f => fs.readFileSync(path.join(root, f), 'utf8').replace(/\r\n/g, '\n');
const APOC = 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/';
const DUNGEON = 'server/custom-plugins/JasprDungeon/src/chat/jaspr/dungeon/';

test('portal gun: the shots alternate by themselves, with no sneak logic and no cyan or amber left', () => {
  const java = read(APOC + 'PortalGun.java');
  assert.doesNotMatch(java, /isSneaking/, 'sneaking no longer chooses the colour');
  assert.doesNotMatch(java, /cyan|amber/i);
  assert.match(java, /private static final class Pair \{ Portal blue, orange; boolean nextOrange; \}/);
  assert.match(java, /boolean orange = pair\.nextOrange;/);
  assert.match(java, /pair\.nextOrange = !orange;/);
  // The turn only passes after a portal was actually placed: the early return for "no surface" comes before the toggle.
  assert.ok(java.indexOf('No surface in range.') < java.indexOf('pair.nextOrange = !orange;'));
  assert.match(java, /BLUE = \{30, 144, 255\}/);
  assert.match(java, /ORANGE = \{255, 140, 0\}/);
  assert.match(java, /"Blue portal set\."/);
  assert.match(java, /"Orange portal set\."/);
  assert.match(java, /Next: /);
});

test('portal gun: lore, command message and migration of guns made before the change', () => {
  const equipment = read(APOC + 'ExpeditionEquipment.java');
  const spec = equipment.match(/add\(new Spec\("portal_gun", [^\n]*\n/)[0];
  assert.match(spec, /fire a portal; shots alternate blue and orange/);
  assert.doesNotMatch(spec, /cyan|amber|Sneak/i);
  assert.match(equipment, /static ItemStack refreshPortalGunLore\(ItemStack item\)/);
  assert.match(equipment, /refreshPortalGunLore\(upgradeLegacyPortalGun\(before\)\)/, 'applied when a player joins and when the equipment starts');
  const plugin = read(APOC + 'ApocalypsePlugin.java');
  assert.match(plugin, /fire a portal; shots alternate blue and orange/);
  assert.doesNotMatch(plugin, /cyan portal|amber/i);
});

test('apocalypse guns: an empty gun reloads by itself, the last round starts the reload, sneak + right-click still reloads early', () => {
  const java = read(APOC + 'Arsenal.java');
  // The old sneak path is intact.
  assert.match(java, /if \(player\.isSneaking\(\)\) \{/);
  // Empty magazine on a click: reload when the inventory holds enough nuggets, say so when it does not.
  assert.match(java, /if \(availableAmmo\(player\) >= gun\.ammoCost\) reload\(player, gun, held\);/);
  assert.match(java, /Empty magazine and no iron nuggets to load it with\./);
  // After a plain shot, and after the last shot of a burst.
  assert.match(java, /else if \(count == 1\) autoReload\(player, gun\);/);
  assert.match(java, /if \(expected - 1 == 0\) autoReload\(player, gun\);/);
  // The helper refuses in every state that would double up or run away.
  const helper = java.slice(java.indexOf('private void autoReload(Player player, Gun gun)'));
  assert.match(helper.slice(0, 900), /reloading\.containsKey\(id\) \|\| bursting\.containsKey\(id\) \|\| !allowed\(player\)/);
  assert.match(helper.slice(0, 900), /identify\(held\) != gun \|\| data\(held\)\.getInt\("rounds"\) != 0/);
  assert.match(helper.slice(0, 900), /availableAmmo\(player\) < gun\.ammoCost/);
  // What players are told.
  assert.match(java, /Right-click: fire \| Reloads itself when empty \| Sneak: reload early/);
  assert.match(java, /An empty gun reloads itself while you carry iron nuggets; sneak \+ right-click reloads early/);
  assert.match(java, /Right-click: fire\. An empty gun reloads itself\.\\nSneak \+ right-click: reload early\./);
});

test('dungeon guns: the same rule for the Dungeon armoury', () => {
  const java = read(DUNGEON + 'Arsenal.java');
  assert.match(java, /if\(p\.isSneaking\(\)&&t\.gun\(\)\)\{reload\(p,item,t\);return;\}/, 'sneak + right-click reload kept');
  assert.match(java, /final boolean load;/);
  assert.match(java, /if\(job\.load\)\{load\(p,job\);continue;\}/);
  assert.match(java, /private void load\(Player p,Pending job\)/);
  assert.match(java, /if\(t\.gun\(\)&&left<t\.triggerCost\(\)\)enqueue\(new Pending\(/, 'the shot that empties the magazine queues the reload');
  assert.match(java, /Right-click fires; reloads itself when empty; sneak \+ right-click reloads early\./);
  // The queued reload re-checks everything when it comes due: same gun, still short, not already reloading, plain nuggets in hand.
  const body = java.slice(java.indexOf('private void load(Player p,Pending job)'));
  assert.match(body.slice(0, 1400), /rounds\(item,t\)>=t\.triggerCost\(\)/);
  assert.match(body.slice(0, 1400), /reloadAt/);
  assert.match(body.slice(0, 1400), /ammo\(/);
});

test('plugin versions are bumped so the deployer can tell the update went live', () => {
  assert.match(read('server/custom-plugins/JasprApocalypse/resources/plugin.yml'), /^version: 3\.7\.3$/m);
  assert.match(read('server/custom-plugins/JasprDungeon/resources/plugin.yml'), /^version: (6\.0\.[2-9]|7\.\d+\.\d+)$/m);   // 6.0.3: dungeon deaths wake at spawn; 7.0.0: generation 7
});

test('audit: nothing in the changed paths logs secrets, tokens or addresses', () => {
  for (const f of [APOC + 'PortalGun.java', APOC + 'Arsenal.java', DUNGEON + 'Arsenal.java']) {
    const java = read(f);
    for (const m of java.matchAll(/getLogger\(\)\.[a-z]+\(([^;]*)\);/g)) {
      assert.doesNotMatch(m[1], /password|token|cookie|address|getAddress|getHostString|ip\b/i, f + ': ' + m[1].slice(0, 80));
    }
  }
});
