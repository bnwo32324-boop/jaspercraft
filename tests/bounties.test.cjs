'use strict';
// JasprBounties, the bounty board (owner, 2026-10-02: "Bounty board ... This should also be accessible through a command.
// Reminders should be posted in the chat on occasions"). Compiles the plugin with the offline check
// (tests/java/chat/jaspr/bounties/BountyCheck.java): the bounty list, rolling boards, progress, rewards, the player file;
// then checks the wiring: the command, sign boards, reminders, kill credit and the anti-farming rules.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const plugin = path.join(root, 'server/custom-plugins/JasprBounties');
const src = path.join(plugin, 'src/chat/jaspr/bounties');
const java = name => fs.readFileSync(path.join(src, name + '.java'), 'utf8');

test('bounty board: list, rolls, progress, rewards, files', {skip: !fs.existsSync(jdk)}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-bounties-'));
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/bounties/BountyCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx256m', '-cp', out + path.delimiter + cp, 'chat.jaspr.bounties.BountyCheck'], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /BOUNTIES_OK templates=(\d+) seen=\1/);
});

test('bounty board: command, signs, reminders, credit, no farming', () => {
  const yml = fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8');
  assert.match(yml, /bounty:[\s\S]*aliases: \[bounties, contracts\]/, 'reachable by command');
  assert.match(yml, /jaspr\.bounties\.use:[\s\S]*default: true/);
  assert.match(yml, /jaspr\.bounties\.admin:[\s\S]*default: op/);
  const main = java('BountiesPlugin'), tracker = java('Tracker'), menu = java('BoardMenu'), rewards = java('Rewards');
  // reminders: periodic, after joining, before the reset, on completion; a player can switch them off
  assert.match(main, /b\.nextReminder = now \+ remindEveryMs \+ random\.nextInt\(300_000\);/);
  assert.match(main, /b\.nextReminder = System\.currentTimeMillis\(\) \+ joinDelayMs;/);
  assert.match(main, /The daily bounties reset in /);
  assert.match(main, /if \(!b\.remind\) continue;/);
  assert.match(main, /line\.setClickEvent\(new ClickEvent\(ClickEvent\.Action\.RUN_COMMAND, "\/bounty"\)\);/, 'reminders open the board when clicked');
  // the board: a sign anyone can make, a chest menu that cannot be looted
  assert.match(tracker, /static final String SIGN_LINE = "\[Bounties\]";/);
  assert.match(menu, /public void onDrag\(InventoryDragEvent e\)/);
  assert.match(menu, /e\.setCancelled\(true\);\s*if \(!\(e\.getWhoClicked\(\) instanceof Player\)\) return;/);
  // credit: own kills, owned turrets, shared boss fights; placed blocks and silk touch never count
  assert.match(tracker, /if \(killer == null\) killer = turretOwner\(dead\);/);
  assert.match(tracker, /p\.getLocation\(\)\.distanceSquared\(dead\.getLocation\(\)\) > 64 \* 64/);
  assert.match(tracker, /if \(wasPlaced\) \{ placedSkips\+\+; return; \}/);
  assert.match(tracker, /containsEnchantment\(Enchantment\.SILK_TOUCH\)\) \{ silkSkips\+\+; return; \}/);
  // unclaimed rewards are paid at the reset, never lost
  assert.match(main, /b\.owed\.add\("xp:" \+ x\.xp\);/);
  // trinkets come from JasprGear by reflection (no hard dependency)
  assert.match(rewards, /getMethod\("bossLoot", Random\.class\)/);
  // logs for diagnostics
  for (const token of ['BOUNTIES_READY', 'BOUNTY_ROLL', 'BOUNTY_DONE', 'BOUNTY_CLAIM', 'BOUNTY_REROLL', 'BOUNTY_STREAK', 'BOUNTIES_METRICS'])
    assert.ok(main.includes(token), token);
});
