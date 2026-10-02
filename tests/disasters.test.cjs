'use strict';
// JasprDisasters 1.3.0 (owner 2026-10-02): three more kinds of natural disaster (earthquake, tornado,
// blizzard), and disasters in general twice as rare: one shared timer instead of one per kind
// (DisasterScheduleTest measures it against the old two-timer schedule). No server is started.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const live = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const paper = fs.existsSync(path.join(root, 'server/cache/patched_1.12.2.jar')) ? path.join(root, 'server/cache/patched_1.12.2.jar') : path.join(live, 'server/cache/patched_1.12.2.jar');
const plugin = path.join(root, 'server/custom-plugins/JasprDisasters');
const src = path.join(plugin, 'src/chat/jaspr/disasters');
const haveJava = fs.existsSync(jdk) && fs.existsSync(paper);
const read = f => fs.readFileSync(path.join(src, f), 'utf8');

test('one shared schedule, twice as rare as the two old timers; kinds drawn at random, never twice in a row', {skip: !haveJava, timeout: 300000}, () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-disasters-test-'));
  const sources = fs.readdirSync(src).filter(f => f.endsWith('.java')).map(f => path.join(src, f));
  const javac = spawnSync(path.join(jdk, 'javac'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-Xlint:-options', '-proc:none', '-cp', paper, '-d', out,
    ...sources, path.join(root, 'tests/java/chat/jaspr/disasters/DisasterScheduleTest.java')], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java'), ['-cp', out + path.delimiter + paper, 'chat.jaspr.disasters.DisasterScheduleTest', path.join(plugin, 'resources/config.yml')], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, /DISASTER_SCHEDULE_OK/);
});

test('five kinds, each with its own command, all admin-only, all run through the one shared slot', () => {
  const yml = fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8');
  for (const command of ['shower', 'lightning', 'quake', 'tornado', 'blizzard']) {
    assert.match(yml, new RegExp('\\n  ' + command + ':\\n(?:    .*\\n)*?    permission: jaspr\\.disasters\\.admin'), command);
  }
  assert.match(yml, /version: 1\.3\.0/);
  const main = read('DisasterPlugin.java');
  for (const kind of ['QUAKE("earthquake", "quake")', 'TORNADO("tornado", "tornado")', 'BLIZZARD("blizzard", "blizzard")']) assert.ok(main.includes(kind), kind);
  assert.ok(main.includes('Schedule.next(now, settings.scheduleMinDays, settings.scheduleMaxDays, random)'), 'one timer after every disaster');
  assert.ok(!/next-shower-epoch-ms|next-storm-epoch-ms/.test(main), 'the per-kind timers are gone');
  assert.ok(main.includes('registerEvents(new ObsidianProtection(), this)'), 'the obsidian rule stays registered');
});

test('the new disasters respect protected blocks and obsidian, clean up after themselves, and stay bounded', () => {
  const impacts = read('Impacts.java'), quake = read('Earthquake.java'), tornado = read('Tornado.java'), blizzard = read('Blizzard.java');
  assert.ok(/PROTECTED = EnumSet\.of\([\s\S]*Material\.OBSIDIAN/.test(impacts), 'obsidian is protected');
  assert.ok(impacts.includes('return material != Material.AIR && !isLiquid(material) && !isProtected(material);'));
  // fissures only split natural ground and only carve what canBreak allows
  assert.ok(quake.includes('Impacts.naturalGround(world, x, z)') && quake.includes('if (Impacts.canBreak(block.getType())) block.setType(Material.AIR, false);'));
  assert.ok(quake.includes('settings.quakeBreakBlocks ? settings.quakeFissures : 0'), 'break-blocks off: no fissures');
  // rocks burst instead of becoming blocks, and time out if they never land
  assert.ok(quake.includes('void onRockLanded') && quake.includes('expireOverdue'));
  // the tornado tears up only loose natural surface, a bounded number of blocks, and gives the weather back
  assert.ok(tornado.includes('settings.tornadoBreakBlocks ? settings.tornadoMaxDebris : 0') && tornado.includes('Impacts.isProtected(type)'));
  assert.ok(/case GRASS:[\s\S]*case LEAVES_2:[\s\S]*default:\s*return null;/.test(tornado), 'only grass, dirt, sand, gravel and leaves fly');
  assert.ok((tornado.match(/weather\.restore\(\)/g) || []).length >= 2, 'weather restored on end and cancel');
  assert.ok(tornado.includes('heading += Math.PI;   // the edge of the loaded world'), 'never loads chunks');
  // the blizzard thaws exactly what it placed, and thaws at once if cancelled
  assert.ok(blizzard.includes('if (block.getType() == Material.SNOW) block.setType(Material.AIR, false);'));
  assert.ok(blizzard.includes('if (block.getType() == Material.ICE) block.setType(Material.STATIONARY_WATER, false);'));
  assert.ok(blizzard.includes('if (settings.blizzardThaw) thaw(Integer.MAX_VALUE, Integer.MAX_VALUE);'));
  assert.ok(blizzard.includes('snow.size() >= settings.blizzardMaxSnow') && blizzard.includes('ice.size() < settings.blizzardMaxIce'));
  assert.ok(blizzard.includes('playerNear(world, water.getLocation().add(0.5d, 0.5d, 0.5d), 3.0d)'), 'never ices over water next to a player');
  // every new kind looks before it reads terrain: no chunk is ever loaded for a disaster
  for (const [name, text] of [['Earthquake', quake], ['Tornado', tornado], ['Blizzard', blizzard]]) {
    const reads = (text.match(/getHighestBlockYAt\(/g) || []).length, guards = (text.match(/isChunkLoaded\(/g) || []).length;
    assert.ok(guards >= reads, name + ': ' + reads + ' height reads, ' + guards + ' loaded-chunk checks');
  }
});

test('the shipped config documents the rarer schedule and every new kind', () => {
  const config = fs.readFileSync(path.join(plugin, 'resources/config.yml'), 'utf8');
  assert.match(config, /\nschedule:\n  min-days: 2\n  max-days: 13\n/);
  assert.equal((config.match(/^ {2}(min|max)-days:/gm) || []).length, 2, 'only the shared schedule has a day window');
  for (const section of ['earthquake:', 'tornado:', 'blizzard:']) assert.ok(config.includes('\n' + section + '\n'), section);
});
