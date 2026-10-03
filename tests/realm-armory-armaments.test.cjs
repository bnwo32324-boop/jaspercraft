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
