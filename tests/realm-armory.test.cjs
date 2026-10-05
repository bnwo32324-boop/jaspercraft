'use strict';
// JasprGear 5.0.0 realm armouries (owner, 2026-10-02: "every dimension should have an armor set that's unique to that
// dimension and that is stronger than diamond ... also add this to the looting system of each dimension including the
// overworld"; "The armor should have accompanying tools and weapons as well, and they should also work with the upgrade
// system, the armaments upgrade system"). Compiles JasprGear with tests/java/chat/jaspr/gear/ArmoryCheck.java and runs it
// offline (no server), then checks the wiring that only a running server would exercise.
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

test('armouries: six sets of nine, identity, stats, forging, loot', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-armory-'));
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/gear/ArmoryCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx384m', '-cp', out + path.delimiter + cp, 'chat.jaspr.gear.ArmoryCheck'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /ARMORY_OK sets=6 pieces=54 materials=3/);
});

test('armouries: the live hooks are wired', () => {
  const plugin = java('GearPlugin'), armory = java('Armory'), api = java('GearApi'), exp = java('GearExport');
  assert.match(plugin, /registerEvents\(armory, this\)/);
  assert.match(plugin, /armory\.registerRecipes\(\);/);
  assert.match(plugin, /if \(tick % 6 == 0\) armory\.fast\(p, now\);/);
  assert.match(plugin, /armory\.second\(p\);/);
  assert.match(plugin, /ARMORY_METRICS /);
  assert.match(plugin, /armorySets=/);
  // pieces made before the stats changed (the helmet and boots 3 -> 4 armour, 2026-10-05) are brought up to date in place, every second
  assert.match(armory, /void second\(Player p\) \{\s*if \(!p\.isDead\(\)\) carried\(p\);/);
  assert.match(armory, /ArmoryItems\.upgraded\(inv\.getItem\(i\)\)/);
  assert.match(armory, /"ARMORY_UPGRADE player="/);
  assert.match(plugin, /armoryArmour=/);
  assert.match(java('ArmoryPiece'), /HELMET\("helmet", "Helmet", Material\.DIAMOND_HELMET, "head", 4,/);
  assert.match(java('ArmoryPiece'), /BOOTS\("boots", "Boots", Material\.DIAMOND_BOOTS, "feet", 4,/);
  // realm plugins and overworld generators reach the loot through the public API
  assert.match(api, /public static List<ItemStack> rollRealmLoot\(Random random, String world, int tier\)/);
  assert.match(api, /if \(pick instanceof ArmoryPiece\)/);
  // forging: the diamond piece must be plain, the realm material genuine; nothing marked stands in elsewhere
  assert.match(armory, /if \(!plainDiamond\(in\)\) \{ e\.getInventory\(\)\.setResult\(null\); return; \}/);
  assert.match(armory, /if \(!want\.set\.materialId\.equals\(ArmoryItems\.materialOf\(in\)\)\)/);
  assert.match(armory, /for \(ItemStack in : matrix\) if \(ArmoryItems\.marked\(in\)\) \{ e\.getInventory\(\)\.setResult\(null\); return; \}/);
  // extra breaks go through the player's own vanilla break (every protection still applies) and never take containers
  assert.match(armory, /playerInteractManager\.breakBlock\(/);
  assert.match(armory, /if \(b\.getState\(\) instanceof InventoryHolder\) continue;/);
  // vanilla loot chests only on their first opening; the dragon always leaves a Void piece
  assert.match(armory, /if \(!chest\.hasLootTable\(\) \|\| chest\.hasBeenFilled\(\)\) return;/);
  assert.match(armory, /if \(dead instanceof EnderDragon\)/);
  // spawner creatures never drop (bosses always may)
  assert.match(armory, /if \(!boss && \(!GearExtras\.hostileAnywhere\(dead\) \|\| plugin\.spawned\(dead\)\)\) return;/);
  // the catalogue export carries every piece
  assert.match(exp, /root\.add\("armory", armory\);/);
});
