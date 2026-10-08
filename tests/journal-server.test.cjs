'use strict';
// The Field Journal's server half (owner 2026-10-07: "the empty space ... event forecast, character summary, active item effects").
// The pure arithmetic runs in a one-processor JVM (JournalRulesTest); the rest are source contracts: the rule the test copies is the
// siege's own text, the plugin reads other plugins only through the narrow, read-only doors, and the wire format is what the
// browser client reads. The behaviour on a real Paper server is tests/journal-smoke.cjs.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const read = f => fs.readFileSync(path.join(root, f), 'utf8').replace(/\r\n/g, '\n');
const JDK = process.env.JAVA17_HOME ? path.join(process.env.JAVA17_HOME, 'bin') : 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const SRC = 'server/custom-plugins/JasprJournal/src/chat/jaspr/journal/';

function run(tool, args, cwd) {
  const r = spawnSync(path.join(JDK, tool + '.exe'), ['-J-XX:ActiveProcessorCount=1', ...args], {encoding: 'utf8', cwd, windowsHide: true, timeout: 120000, maxBuffer: 8 * 1024 * 1024});
  assert.ifError(r.error);
  assert.equal(r.status, 0, r.stderr + '\n' + r.stdout);
  return r.stdout;
}

test('rules: phases, Blood Moon countdown (against a simulation), invasion and disaster states, effect names, payload', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-journal-rules-'));
  try {
    run('javac', ['--release', '8', '-encoding', 'UTF-8', '-d', dir, path.join(root, SRC, 'JournalRules.java'), path.join(root, 'tests/java/chat/jaspr/journal/JournalRulesTest.java')], dir);
    const java = path.join(JDK, 'java.exe');
    const r = spawnSync(java, ['-XX:ActiveProcessorCount=1', '-Xmx64m', '-cp', dir, 'chat.jaspr.journal.JournalRulesTest'], {encoding: 'utf8', windowsHide: true, timeout: 120000});
    assert.ifError(r.error);
    assert.equal(r.status, 0, r.stderr + r.stdout);
    assert.match(r.stdout, /JOURNAL_RULES_PASS checks=\d+/);
    const samples = r.stdout.split('\n').filter(l => l.startsWith('SAMPLE ')).map(l => JSON.parse(l.slice(7)));
    assert.equal(samples.length, 5);
    const [full, bare, wornSample, evil, armedSample] = samples;
    assert.deepEqual(wornSample.gw, [['Worn Teddy Bear', 'Sneak still to rest and heal'], ['Scrap Magnet', ''], ['Rebreather', '']]);
    assert.deepEqual(wornSample.fx, [['Haste II', 0]]);
    assert.deepEqual(full, {v: 1, d: 12, ph: 'Dusk', bm: 2, iv: [1, 15], dz: [1, ''], lv: 37, xp: 20, rp: [14, 9, 45, 3, 11], fx: [['Water Breathing', 0], ['Haste II', 23]]});
    assert.deepEqual(bare, {v: 1, d: 0, ph: 'Night', lv: 0, xp: 0, fx: []});
    assert.deepEqual(evil.dz, [2, 'Tor']);
    assert.deepEqual(armedSample, {v: 1, d: 0, ph: 'Morning', lv: 0, xp: 0, rp: [1, 1, 45, 0, 5], ab: 4, fx: []}, 'armament armour travels as the whole points ab');
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
});

test('the test\'s copy of the siege rule is the real one, and the plugin asks the real rule', () => {
  const real = read('server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/SiegeRules.java');
  const body = real.match(/public static boolean bloodMoon\(long fullTime, int every\) \{\n([^}]*)\}/)[1].replace(/\s+/g, ' ').trim();
  const copy = read('tests/java/chat/jaspr/journal/JournalRulesTest.java').match(/static boolean siege\(long fullTime, int every\) \{\n([^}]*)\}/)[1].replace(/\s+/g, ' ').trim();
  assert.equal(copy, body);
  const sources = read(SRC + 'JournalSources.java');
  assert.match(sources, /chat\.jaspr\.apocalypse\.SiegeRules/);
  assert.match(sources, /getDeclaredMethod\("bloodMoon", long\.class, int\.class\)/);
  assert.match(sources, /getInt\("siege\.blood-moon-every-nights", 3\)/);
  assert.match(read('server/custom-plugins/JasprApocalypse/resources/config.yml'), /blood-moon-every-nights: 3/);
});

test('sources are read-only doors: nothing is written to another plugin, and a failure is counted, not thrown', () => {
  const sources = read(SRC + 'JournalSources.java');
  for (const forbidden of [/\.set(?!Accessible)\w*\(/, /\.put\(/, /\.markDirty/, /\.save\(/, /\.invoke\(store, ?"get"/]) {
    // Field.set / Method calls that write: none (the only .put calls are on the failure counters)
    const hits = sources.split('\n').filter(l => forbidden.test(l) && !/failures|reported/.test(l));
    assert.deepEqual(hits, [], String(forbidden));
  }
  assert.doesNotMatch(sources, /getDeclaredMethod\("get"/, 'the invasion store is read with all(), never get() (which creates records)');
  assert.match(sources, /getDeclaredMethod\("all"\)/);
  assert.equal((sources.match(/catch \(Throwable error\)/g) || []).length, 6, 'every source guards itself');
  assert.match(sources, /JOURNAL_SOURCE_UNAVAILABLE source=/);
  // The doors on the other plugins exist and are small.
  assert.match(read('server/custom-plugins/JasprDisasters/src/chat/jaspr/disasters/DisasterPlugin.java'), /public String journalState\(\) \{/);
  assert.match(read('server/custom-plugins/JasprRPG/src/chat/jaspr/rpg/RpgApi.java'), /public static int\[\] summary\(UUID player, int xpLevels\) \{/);
  // The worn-trinket door: read-only, never loads a profile from disk, in slot order.
  const gear = read('server/custom-plugins/JasprGear/src/chat/jaspr/gear/GearApi.java');
  const door = gear.slice(gear.indexOf('public static List<String[]> worn('), gear.indexOf('/** All gear ids in catalogue order. */'));
  assert.match(door, /plugin\.existing\(player\)/, 'only an already loaded profile: nothing is read from disk');
  assert.doesNotMatch(door, /plugin\.profile\(|\.save|\.set\w*\(|slots\[[^\]]*\]\s*=/, 'nothing is written');
  assert.match(door, /for \(org\.bukkit\.inventory\.ItemStack stack : prof\.slots\)/, 'slot order');
  assert.match(sources, /chat\.jaspr\.gear\.GearApi/);
  assert.match(sources, /getDeclaredMethod\("worn", org\.bukkit\.entity\.Player\.class\)/);
  const inv = read('server/custom-plugins/JasprInvasions/src/chat/jaspr/invasions/InvasionPlugin.java');
  for (const f of ['active', 'settings', 'store']) assert.match(inv, new RegExp('private [^\\n]*\\b' + f + ';|private final [^\\n]*\\b' + f + ' ='), 'InvasionPlugin.' + f);
  assert.match(read('server/custom-plugins/JasprInvasions/src/chat/jaspr/invasions/ProgressStore.java'), /Collection<PlayerProgress> all\(\)/);
  assert.match(read('server/custom-plugins/JasprInvasions/src/chat/jaspr/invasions/PlayerProgress.java'), /final UUID id;[^]*long sleptAt = -1L;/);
  assert.match(read('server/custom-plugins/JasprInvasions/src/chat/jaspr/invasions/InvasionConfig.java'), /final int daysAfterSleep;/);
});

test('armament armour: the number the armour bar adds counts exactly the pieces that turn damage aside, and writes nothing', () => {
  // The door (owner 2026-10-07: two identical Emerald Boots, one an Ancient armament with +40% protection, drew the same armour bar).
  const api = read('server/custom-plugins/JasprRPG/src/chat/jaspr/rpg/RpgApi.java');
  const start = api.indexOf('public static int armamentArmor(Player player)'), door = api.slice(start, api.indexOf('/** Damage multiplier', start));
  assert.ok(start > 0, 'RpgApi.armamentArmor(Player) exists and is public');
  assert.match(door, /player\.getInventory\(\)\.getArmorContents\(\)/, 'the worn pieces only');
  assert.match(door, /Armament\.isEnhanced\(piece\) \|\| !Armament\.isArmour\(piece\)\) continue;/, 'the same test the damage handler applies');
  assert.match(door, /Armament\.rarity\(piece\)\.bonus/, 'the rarity bonus the lore states');
  assert.match(door, /extra \+= bonus \* armourOf\(piece, WORN\[i\]\)/, 'the piece\'s own armour once more by its bonus');
  assert.match(door, /Math\.min\(MAX_ARMOUR_BONUS, Math\.round\(extra\)\)/, 'whole points, bounded');
  assert.match(api, /WORN = \{EnumItemSlot\.FEET, EnumItemSlot\.LEGS, EnumItemSlot\.CHEST, EnumItemSlot\.HEAD\}/, 'Bukkit lists boots first, helmet last');
  assert.match(api, /nms\.a\(slot\)\.get\("generic\.armor"\)/, 'the armour modifiers of the item in that slot (its own NBT ones, or vanilla\'s)');
  assert.doesNotMatch(door, /\.set\w*\(|\.add\w*\(|\.remove\w*\(|\.put\(/, 'nothing is written');
  // The damage handler is untouched by the display: it still scales the damage by (1 - min(0.55, bonus * 0.55)) per enhanced piece.
  const listener = read('server/custom-plugins/JasprRPG/src/chat/jaspr/rpg/ArmamentListener.java');
  // (Since JasprRPG 1.3.4 a worn piece that is not an armament yet becomes one with the first blow it takes, and every second it is worn.)
  assert.match(listener, /if \(!Armament\.isArmour\(piece\)\) continue;\n\s*if \(!Armament\.isEnhanced\(piece\)\) \{[^]*?event\.setDamage\(event\.getDamage\(\) \* \(1\.0d - Math\.min\(0\.55d, rarity\.bonus \* 0\.55d\)\)\);/);
  // The lore says "+N% protection" with N the same bonus.
  assert.match(read('server/custom-plugins/JasprRPG/src/chat/jaspr/rpg/Armament.java'), /int percent = \(int\) Math\.round\(rarity\.bonus \* 100\.0d\);/);
  // The Journal asks through reflection, tells the client the points ("ab") and a change in them is a change worth sending.
  const sources = read(SRC + 'JournalSources.java'), rules = read(SRC + 'JournalRules.java'), plugin = read(SRC + 'JournalPlugin.java');
  assert.match(sources, /loadClass\("chat\.jaspr\.rpg\.RpgApi"\)[^]*getDeclaredMethod\("armamentArmor", org\.bukkit\.entity\.Player\.class\)/);
  assert.match(plugin, /s\.armor = sources\.armor\(player\);/);
  assert.match(rules, /if \(armor > 0\) b\.append\(",\\"ab\\":"\)\.append\(Math\.min\(MAX_ARMOR_BONUS, armor\)\);/);
  assert.match(plugin, /armorFail=/);
});

test('disasters: only the coarse state leaves the plugin, never the time', () => {
  const plugin = read('server/custom-plugins/JasprDisasters/src/chat/jaspr/disasters/DisasterPlugin.java');
const start = plugin.indexOf('public String journalState()'), door = plugin.slice(start, plugin.indexOf('\n    }\n', start));
  assert.match(door, /"active:" \+ kind\.label/);
  assert.match(door, /return nextAt - System\.currentTimeMillis\(\) <= DisasterConfig\.MILLIS_PER_MC_DAY \? "brewing" : "quiet";/);
  assert.equal(door.split('nextAt').length - 1, 1, 'nextAt appears once: it is compared, never returned');
  assert.deepEqual([...door.matchAll(/return ([^;]*);/g)].map(m => m[1].startsWith('"active:"') || m[1].includes('"brewing" : "quiet"')), [true, true], 'only the two coarse strings are returned');
  const journal = read(SRC + 'JournalPlugin.java') + read(SRC + 'JournalRules.java');
  assert.doesNotMatch(journal, /minutesUntilNext|nextAt/, 'the journal has no way to see the exact time');
});

test('wire format: channel, hello, bounded decode, payload only on change or heartbeat', () => {
  const plugin = read(SRC + 'JournalPlugin.java');
  assert.match(plugin, /CHANNEL = "jaspr:journal"/);
  assert.match(plugin, /"hello 1"\.equals\(message\)/);
  assert.match(plugin, /MAX_MESSAGES_PER_SECOND = 6/);
  assert.match(plugin, /bytes\.length > 64/, 'the incoming message is bounded');
  assert.match(plugin, /buf\.a\(snapshot\.json\(true\)\)/, 'sent as the string the client reads with readString');
  assert.match(plugin, /key\.equals\(c\.lastKey\) && tick - c\.lastSent < HEARTBEAT_TICKS/);
  assert.match(plugin, /HEARTBEAT_TICKS = 200L/);
  // Nothing from the client but the hello is ever acted on.
  assert.equal((plugin.match(/onPluginMessageReceived/g) || []).length, 1);
  assert.match(plugin, /if \(!"hello 1"\.equals\(message\)\) \{ rejected\+\+; return; \}/);
  // Logs: counts and names of sources only.
  for (const m of plugin.matchAll(/getLogger\(\)\.[a-z]+\(([^;]*)\);/g)) assert.doesNotMatch(m[1], /getName|getAddress|getUniqueId|password|token|cookie|ip\b/i, m[1].slice(0, 90));
  const yml = read('server/custom-plugins/JasprJournal/resources/plugin.yml');
  assert.match(yml, /^version: 1\.0\.2$/m);
  assert.match(yml, /softdepend: \[JasprApocalypse, JasprRPG, JasprInvasions, JasprDisasters, JasprGear\]/);
});

test('versions of the plugins that gained a door are bumped', () => {
  assert.match(read('server/custom-plugins/JasprRPG/resources/plugin.yml'), /^version: 1\.3\.4$/m);
  assert.match(read('server/custom-plugins/JasprDisasters/resources/plugin.yml'), /^version: 1\.4\.1$/m);
  assert.match(read('server/custom-plugins/JasprGear/resources/plugin.yml'), /^version: 5\.0\.3$/m);
});
