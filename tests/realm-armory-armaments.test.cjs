'use strict';
// JasprRPG 1.3.0 (owner, 2026-10-02: the realm armour "should have accompanying tools and weapons as well, and they should
// also work with the upgrade system, the armaments upgrade system"): armoury pieces are always armaments (Uncommon or
// better), keep their own lore above the "- Armament -" header, and realm tools level by digging with their own roster.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const src = path.join(root, 'server/custom-plugins/JasprRPG/src/chat/jaspr/rpg');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');

test('armaments: realm armoury pieces and tools', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-armament-'));
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/rpg/ArmamentToolCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx256m', '-cp', out + path.delimiter + cp, 'chat.jaspr.rpg.ArmamentToolCheck'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /ARMAMENT_OK tools=5/);
});

test('armaments: the tool hooks are wired', () => {
  const plugin = java('RpgPlugin'), tools = java('ToolArmaments'), header = java('Armament');
  assert.match(plugin, /registerEvents\(tools, this\)/);
  assert.match(plugin, /Armament\.toolsMode = settings\.toolsMode;/);
  // the header string is the contract with JasprGear's ArmoryItems.ARMAMENT_HEADER
  assert.match(header, /ARMORY_HEADER = ChatColor\.DARK_GRAY \+ "- Armament -"/);
  const gear = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprGear/src/chat/jaspr/gear/ArmoryItems.java'), 'utf8');
  assert.match(gear, /ARMAMENT_HEADER = ChatColor\.DARK_GRAY \+ "- Armament -"/);
  // extra digging through the player's own vanilla break; placed blocks never pay; experience in batches
  assert.match(tools, /playerInteractManager\.breakBlock\(/);
  assert.match(tools, /int xp = wasPlaced \? 0 : ore \? 4 : crop \? 2 : 1;/);
  assert.match(tools, /if \(state\[0\] >= FLUSH\) flush\(p, state\);/);
});

test('armaments: every weapon, armour piece, gun and realm tool becomes one, whatever the path (owner 2026-10-08)', () => {
  // "I'm not getting points to upgrade this weapon ... yet it works on other weapons ... Fix this globally" and "I think 100% of
  // weapons should have armament capabilities": the 35% roll on craft and pickup is gone, and so is the gap (loot taken out of a
  // chest never rolled at all). The behaviour itself is checked by ArmamentToolCheck.everyItemIsAnArmament.
  const armament = java('Armament'), listener = java('ArmamentListener'), plugin = java('RpgPlugin'), menu = java('ArmamentMenu'), config = java('RpgConfig');
  assert.doesNotMatch(armament + listener + menu + config, /maybeEnhance|enchantChance|creativeChance|nextDouble\(\) > chance/, 'no roll chance is left');
  assert.match(armament, /static ItemStack ensure\(ItemStack item, RpgConfig settings, Random random\) \{\n\s*if \(!isEligible\(item\) \|\| isEnhanced\(item\)\) return item;/);
  // Every acquisition path and every use makes one: pickup, creative, any container's contents, output slots, crafting, a blow dealt,
  // a blow taken, the armament sheet, and once a second whatever is held or worn.
  for (const handler of ['onPickup', 'onCreative', 'onOpenContainer', 'onTakeResult', 'onCraft']) {
    const start = listener.indexOf('public void ' + handler + '('), body = listener.slice(start, listener.indexOf('\n    }\n', start));
    assert.match(body, /made\(/, handler + ' makes an armament');
  }
  assert.doesNotMatch(listener.slice(listener.indexOf('public void onOpenContainer('), listener.indexOf('public void onTakeResult(')), /isArmory/, 'every container item, not only realm pieces');
  assert.match(listener, /if \(!Armament\.isEnhanced\(weapon\)\) \{\n\s*\/\/ A weapon that is not an armament yet becomes one with its first blow/);
  assert.match(listener, /if \(!Armament\.isEnhanced\(piece\)\) \{\n\s*\/\/ A piece that is not an armament yet becomes one with the first blow it takes\./);
  assert.match(menu, /plugin\.armaments\(\)\.made\(held\)/);
  assert.match(plugin, /if \(settings\.armamentsEnabled && ticks % 20L == 0L\) armaments\.ensureCarried\(player\);/);
  assert.match(listener, /view\.getType\(\) != org\.bukkit\.event\.inventory\.InventoryType\.CRAFTING/, 'never swaps an item under a container window');
  assert.match(plugin, /armaments\.metrics\(\)/, 'RPG_METRICS counts the items made armaments');
  assert.match(fs.readFileSync(path.join(root, 'server/custom-plugins/JasprRPG/resources/plugin.yml'), 'utf8'), /^version: 1\.3\.4$/m);
  assert.doesNotMatch(fs.readFileSync(path.join(root, 'server/custom-plugins/JasprRPG/resources/config.yml'), 'utf8'), /enhance-chance:/);
});
