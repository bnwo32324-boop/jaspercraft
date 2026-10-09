'use strict';
// JasprMutants (Mutant Creatures Legacy on Paper, owner 2026-10-05/2026-10-08) on one throwaway Paper server with the live
// server jar, below-normal priority, port 25678, and one protocol client. Never on the live server.
//   node tests/mutants-runtime.cjs            (NODE_PATH must reach mineflayer, e.g. the dungeon sandbox's node_modules)
//   MUTANTS_JAR=<jar>  default server/plugins/JasprMutants.jar (scripts/build-mutants-plugin.sh)
// Checks: the plugin's ready line (registries, materials, data, data keys); the jaspr:mutants HELLO and the sound filter
// for clients without the stage; every living kind spawns through MutantsApi as a Bukkit mob and reaches the client as a
// SPAWN message (MUTANTS_PROTOCOL.md section 2) with its metadata; natural spawning only in the main overworld; items,
// recipes, advancements, loot (Hulk Hammer from a player-killed Mutant Zombie), brewing Chemical X, shearing the Mutant
// Snow Golem, the mod channel's validation, the mutants attacking a player, chunk save/load and a restart.
const fs = require('node:fs'), path = require('node:path'), net = require('node:net'), {spawn, execFileSync} = require('node:child_process');
const mineflayer = require('mineflayer');
const ROOT = path.resolve(__dirname, '..'), PORT = 25678, NAME = 'MutantTester';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const JAR = path.resolve(process.env.MUTANTS_JAR || path.join(ROOT, 'server/plugins/JasprMutants.jar'));
const RUN = path.join(ROOT, 'candidate', 'mutants-runtime', new Date().toISOString().replace(/[:.]/g, '-'));
const TYPES = {mutant_zombie: 221, mutant_skeleton: 219, mutant_creeper: 217, mutant_enderman: 218, mutant_snow_golem: 220, spider_pig: 223, creeper_minion: 213, endersoul_clone: 212};
const sleep = ms => new Promise(r => setTimeout(r, ms));
let child = null, log = '', bot = null, checks = 0, failures = 0, phase = 0;
const results = [];
function check(pass, name, detail) {
  checks++; if (!pass) failures++;
  results.push({name, pass: !!pass, detail});
  console.log((pass ? 'PASS ' : 'FAIL ') + name + (detail !== undefined ? ' ' + String(detail).slice(-600) : ''));
}
async function until(fn, timeout = 10000) { const end = Date.now() + timeout; while (Date.now() < end) { if (await fn()) return true; await sleep(150); } return !!(await fn()); }
async function free() { await new Promise((res, rej) => { const s = net.createServer(); s.once('error', rej); s.listen(PORT, '127.0.0.1', () => s.close(res)); }); }

// ---- server setup ----
fs.mkdirSync(path.join(RUN, 'plugins', 'bStats'), {recursive: true});
fs.copyFileSync(path.join(LIVE, 'server/eula.txt'), path.join(RUN, 'eula.txt'));
fs.copyFileSync(JAR, path.join(RUN, 'plugins', 'JasprMutants.jar'));
fs.writeFileSync(path.join(RUN, 'plugins/bStats/config.yml'), 'enabled: false\nserverUuid: 00000000-0000-0000-0000-000000000000\nlogFailedRequests: false\n');
fs.writeFileSync(path.join(RUN, 'server.properties'), ['server-ip=127.0.0.1', 'server-port=' + PORT, 'online-mode=false', 'level-type=FLAT', 'level-name=world',
  'generate-structures=false', 'allow-nether=false', 'view-distance=4', 'spawn-protection=0', 'difficulty=2', 'max-players=2', 'enable-query=false',
  'enable-rcon=false', 'snooper-enabled=false', 'spawn-monsters=true', 'spawn-animals=false', 'network-compression-threshold=-1', ''].join('\n'));
fs.writeFileSync(path.join(RUN, 'bukkit.yml'), 'settings:\n  allow-end: false\n  update-folder: update\nticks-per:\n  autosave: 0\n');
fs.writeFileSync(path.join(RUN, 'spigot.yml'), 'config-version: 11\nsettings:\n  timeout-time: 300\n  restart-on-crash: false\n  bungeecord: false\nstats:\n  disable-saving: true\n');
const runtimeJar = path.join(RUN, 'paper.jar');
fs.copyFileSync(path.join(LIVE, 'server/jaspr-paper-clientbudget.jar'), runtimeJar);

// ---- the probe plugin, compiled against the live server jar and the plugin under test ----
(function buildProbe() {
  const work = path.join(RUN, 'probe-build'), cls = path.join(work, 'classes');
  fs.mkdirSync(cls, {recursive: true});
  execFileSync(path.join(JDK, 'javac.exe'), ['-J-XX:ActiveProcessorCount=1', '--release', '8', '-nowarn', '-encoding', 'UTF-8', '-proc:none',
    '-cp', runtimeJar + path.delimiter + JAR, '-d', cls, path.join(ROOT, 'tests/java/mutantsprobe/MutantsProbe.java')], {stdio: 'inherit', windowsHide: true});
  fs.writeFileSync(path.join(cls, 'plugin.yml'), 'name: MutantsProbe\nversion: 1\nmain: mutantsprobe.MutantsProbe\ndepend: [JasprMutants]\ncommands:\n  mprobe:\n    description: test probe\n');
  execFileSync(path.join(JDK, 'jar.exe'), ['--create', '--file', path.join(RUN, 'plugins', 'MutantsProbe.jar'), '-C', cls, '.'], {stdio: 'inherit', windowsHide: true});
})();

async function start() {
  await free(); phase++; log = '';
  const sink = fs.createWriteStream(path.join(RUN, 'phase-' + phase + '.log'));
  child = spawn(path.join(JDK, 'java.exe'), ['-Xms256M', '-Xmx1280M', '-XX:ActiveProcessorCount=1', '-XX:+UseSerialGC', '-Dcom.mojang.eula.agree=true',
    '-DIReallyKnowWhatIAmDoingISwear=true', '-Dfile.encoding=UTF-8', '-jar', runtimeJar, 'nogui', '--nojline'], {cwd: RUN, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  try { execFileSync('powershell', ['-NoProfile', '-Command', '(Get-Process -Id ' + child.pid + ').PriorityClass=\'BelowNormal\''], {windowsHide: true}); } catch (e) { console.log('NOTE priority not lowered'); }
  child.stdout.on('data', b => { log += b; sink.write(b); }); child.stderr.on('data', b => { log += b; sink.write(b); }); child.on('exit', () => sink.end());
  await until(() => log.includes('Done (') || child.exitCode !== null, 240000);
  if (!log.includes('Done (')) throw Error('Paper did not start:\n' + log.slice(-3000));
  await until(() => /MUTANTS_READY|MUTANTS_FAILED/.test(log), 20000);
}
async function cmd(command, expect = /PROBE_\w+/, timeout = 15000) {
  const from = log.length; child.stdin.write(command + '\n');
  await until(() => expect.test(log.slice(from)) || log.slice(from).includes('PROBE_FAIL'), timeout);
  const out = log.slice(from);
  const line = (out.match(new RegExp('(' + expect.source + '|PROBE_FAIL)[^\\r\\n]*')) || [''])[0];
  return {out, line};
}
const lastId = kind => { const m = log.match(new RegExp('PROBE_SPAWNED kind=' + kind + ' id=\\d+', 'g')); return m ? +m.pop().split('id=')[1] : -1; };
const field = (line, k) => ((line || '').match(new RegExp('\\b' + k + '=([^ \\r\\n]+)')) || [])[1];
async function stop() {
  if (bot) { try { bot.quit(); } catch (e) { } bot = null; }
  if (child && child.exitCode === null) { const c = child; c.stdin.write('stop\n'); if (!await until(() => c.exitCode !== null, 60000)) { c.kill(); } }
  child = null;
}

// ---- the protocol client ----
const seen = {names: {}, spawns: [], sounds: [], particles: [], status: [], metadata: new Set(), slots: new Set(), advancements: '', payloads: []};
function readVarInt(buf, at) { let n = 0, shift = 0, b; do { b = buf[at.i++]; n |= (b & 0x7f) << shift; shift += 7; } while (b & 0x80); return n; }
function decodeSpawn(d) {
  const at = {i: 1}, id = readVarInt(d, at); at.i += 16; const type = readVarInt(d, at);
  const x = d.readDoubleBE(at.i), y = d.readDoubleBE(at.i + 8), z = d.readDoubleBE(at.i + 16);
  return {id, type, x, y, z, bytes: d.length};
}
async function join() {
  return new Promise((resolve, reject) => {
    const b = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: NAME, version: '1.12.2', auth: 'offline'});
    b.on('error', reject); b.on('kicked', r => console.log('BOT_KICK ' + r));
    b._client.on('packet', (data, meta) => {
      seen.names[meta.name] = (seen.names[meta.name] || 0) + 1;
      if (meta.name === 'advancements') seen.advancementRaw = (seen.advancementRaw || 0) + 1;
      if (meta.name === 'custom_payload') {
        seen.payloads.push(data.channel);
        if (data.channel === 'jaspr:mutants' && data.data[0] === 1) seen.spawns.push(decodeSpawn(data.data));
      } else if (meta.name === 'sound_effect') seen.sounds.push(data.soundId);
      else if (meta.name === 'world_particles') seen.particles.push(data.particleId);
      else if (meta.name === 'entity_status') seen.status.push([data.entityId, data.entityStatus]);
      else if (meta.name === 'entity_metadata') seen.metadata.add(data.entityId);
      else if (meta.name === 'set_slot' && data.item && data.item.blockId >= 4000) seen.slots.add(data.item.blockId);
      else if (meta.name === 'window_items') { for (const it of data.items || []) if (it && it.blockId >= 4000) seen.slots.add(it.blockId); }
      else if (meta.name === 'advancements') seen.advancements += JSON.stringify(data, (k, v) => typeof v === 'bigint' ? String(v) : v).slice(0, 200000);
    });
    b.once('spawn', async () => { await sleep(800); resolve(b); });
  });
}
function hello() {
  const build = Buffer.from('mutants-runtime', 'utf8');
  bot._client.write('custom_payload', {channel: 'jaspr:mutants', data: Buffer.concat([Buffer.from([0, 1, build.length]), build])});
}

(async () => {
  try {
    await start();
    const ready = (log.match(/MUTANTS_READY[^\n]*/) || [''])[0];
    check(/MUTANTS_READY entities=15 items=15 sounds=43 particles=2 materials=15 bukkitSounds=43 /.test(ready), 'JasprMutants is ready: 15 entities, 15 items, 43 sounds, 2 particles, 15 Bukkit materials', ready || (log.match(/MUTANTS_FAILED[^\n]*/) || [''])[0]);
    check(/recipes=12 /.test(ready) && /brewing=8 /.test(ready) && /lootTables=8 /.test(ready) && /advancements=7 /.test(ready), 'The mod\'s data: 12 recipes, 8 brewing recipes, 8 loot tables, 7 advancements', ready);
    check(/dataKeys=ok/.test(ready), 'Every entity\'s data keys match MUTANTS_PROTOCOL.md 1.2', (log.match(/MUTANTS_DATAKEYS_MISMATCH[^\n]*/g) || []).join(' | ') || ready);
    check(/spawns=overworld/.test(ready) && /lang=\d{2,}/.test(ready), 'Natural spawning is on and the language keys are loaded', ready);
    check(!/Error occurred while enabling|Could not load 'plugins/.test(log), 'No plugin enable error');

    bot = await join();
    await cmd('op ' + NAME, /opped|Made|already/i); await cmd('gamemode creative ' + NAME, /game mode|gamemode/i);
    // Clients without the stage: the server drops mod sounds and particles (MUTANTS_PROTOCOL.md section 5).
    seen.sounds.length = 0;
    await cmd('mprobe sound ' + NAME + ' 1039', /PROBE_SOUND/);
    await cmd('mprobe sound ' + NAME + ' 3', /PROBE_SOUND/);
    await until(() => seen.sounds.includes(3), 4000);
    check(seen.sounds.includes(3) && !seen.sounds.includes(1039), 'Before HELLO: a mod sound (1039) is filtered, a vanilla one (3) arrives', JSON.stringify(seen.sounds));
    hello();
    check(await until(() => /MUTANTS_CLIENT_HELLO version=1 player=MutantTester/.test(log), 5000), 'The client\'s HELLO is accepted', (log.match(/MUTANTS_CLIENT_HELLO[^\n]*/) || [''])[0]);
    seen.sounds.length = 0;
    await cmd('mprobe sound ' + NAME + ' 1039', /PROBE_SOUND/);
    check(await until(() => seen.sounds.includes(1039), 4000), 'After HELLO: the mod sound reaches the client (entity.mutant_zombie.roar = 1039)', JSON.stringify(seen.sounds));

    // Every living kind through MutantsApi, as Bukkit mobs, to the client as SPAWN messages.
    const sp = await cmd('mprobe spawnall ' + NAME, /PROBE_OK spawnall/, 20000);
    const lines = sp.out.split('\n').filter(l => l.includes('PROBE_SPAWN '));
    check(lines.length === 8, 'MutantsApi spawns all 8 living kinds', lines.length + ' ' + (sp.line || ''));
    const ids = {};
    for (const l of lines) { ids[field(l, 'kind')] = +field(l, 'id'); }
    check(lines.every(l => field(l, 'kindOf') === field(l, 'kind') && +field(l, 'health') > 0 && field(l, 'health') === field(l, 'max')), 'Each spawns at full health and names its kind', lines.map(l => field(l, 'kind') + ':' + field(l, 'max')).join(' '));
    check(['mutant_zombie', 'mutant_skeleton', 'mutant_creeper', 'mutant_enderman', 'endersoul_clone'].every(k => lines.find(l => field(l, 'kind') === k && field(l, 'monster') === 'true')), 'The mutants are Bukkit Monsters (turrets, RPG and daylight logic see mobs)', lines.map(l => field(l, 'kind') + ':' + field(l, 'bukkit')).join(' '));
    check(await until(() => Object.keys(TYPES).every(k => seen.spawns.some(s => s.id === ids[k] && s.type === TYPES[k])), 8000),
      'The client gets a SPAWN message with the protocol type id for each', JSON.stringify(seen.spawns.map(s => s.id + ':' + s.type)));
    check(await until(() => Object.values(ids).every(id => seen.metadata.has(id)), 5000), 'and their metadata follows', [...seen.metadata].join(','));
    const hurt = (await cmd('mprobe hurtall ' + NAME, /PROBE_HURTALL/)).line;
    check(+field(hurt, 'n') >= 8 && field(hurt, 'ok') === field(hurt, 'n') && !/failed=\S/.test(hurt), 'Every living kind takes hits (plain and from a player) without an exception (Paper resets a tameable\'s sit goal)', hurt);

    // Natural spawning: the mod's biome entries, used only in the main overworld.
    const bio = (await cmd('mprobe biomes', /PROBE_BIOMES/)).line;
    check(+field(bio, 'entries') > 40 && /MutantZombieEntity/.test(bio) && /MutantSkeletonEntity/.test(bio) && /MutantCreeperEntity/.test(bio) && /MutantEndermanEntity/.test(bio), 'The mod\'s spawn entries are in the vanilla biomes (copySpawnsForMutant)', bio);
    const nat = (await cmd('mprobe natural', /PROBE_NATURAL/, 60000)).line;
    check(field(nat, 'main') === 'true' && field(nat, 'other') === 'false', 'A natural spawn is kept in the main overworld and refused in any other world', nat);

    // Items, recipes, advancements.
    const it = (await cmd('mprobe items ' + NAME, /PROBE_ITEMS/)).line;
    check(field(it, 'n') === '15' && /MUTANTBEASTS_HULK_HAMMER=4004/.test(it), 'The 15 items are Bukkit materials with the protocol ids', it);
    check(await until(() => seen.slots.size >= 15, 5000), 'and reach the client\'s inventory as items 4000-4014', [...seen.slots].sort().join(','));
    const rec = (await cmd('mprobe recipes', /PROBE_RECIPES/)).line;
    check(field(rec, 'mod') === '12' && field(rec, 'chestplate') === 'true', 'The mod\'s 12 crafting recipes are registered', rec);
    const adv = (await cmd('mprobe advancements', /PROBE_ADVANCEMENTS/)).line;
    check(field(adv, 'mod') === '7' && field(adv, 'root') === 'true', 'The mod\'s 7 advancements are loaded', adv);
    await cmd('advancement grant ' + NAME + ' only mutantbeasts:root', /granted|Granted|criteria/i);
    check(await until(() => seen.advancements.includes('mutantbeasts:root'), 5000), 'and, once granted, sent to the client (vanilla shows an advancement tab after progress)', 'advancement packets=' + (seen.advancementRaw || 0) + ' text=' + seen.advancements.slice(0, 300));

    // Loot: a player-killed Mutant Zombie drops the Hulk Hammer (killed_by_player).
    await cmd('mprobe kill ' + NAME + ' mutant_zombie', /PROBE_KILLED/);
    await sleep(1500);
    const deaths = (await cmd('mprobe deaths', /PROBE_DEATHS/)).line;
    check(/kind=mutant_zombie drops=[^ ]*MUTANTBEASTS_HULK_HAMMER/.test(deaths), 'A Mutant Zombie killed by a player drops the Hulk Hammer (its loot table)', deaths);

    // Shearing the Mutant Snow Golem (Forge's ItemShears on an IShearable).
    await cmd('mprobe clear', /PROBE_CLEARED/);
    await cmd('mprobe spawn ' + NAME + ' mutant_snow_golem 3 0 noai', /PROBE_SPAWNED/);
    const g0 = (await cmd('mprobe golem', /PROBE_GOLEM/)).line;
    await cmd('mprobe give ' + NAME + ' SHEARS', /PROBE_GIVE/); await sleep(600);
    bot._client.write('use_entity', {target: +field(g0, 'id'), mouse: 0, hand: 0});
    await sleep(1200);
    const g1 = (await cmd('mprobe golem', /PROBE_GOLEM/)).line;
    const drops = (await cmd('mprobe drops', /PROBE_DROPS/)).line;
    check(field(g0, 'pumpkin') === 'true' && field(g1, 'pumpkin') === 'false' && +field(drops, 'jack_o_lantern') >= 1 && field(g1, 'owner') === 'null',
      'Shears take the Mutant Snow Golem\'s pumpkin and drop a lit pumpkin (owner untouched)', g0 + ' -> ' + g1 + ' ' + drops);

    // The mod channel: the tracker packet is validated (exact size, the player's own minion).
    await cmd('mprobe spawn ' + NAME + ' creeper_minion 2 2 noai', /PROBE_SPAWNED/);
    const minion = lastId('creeper_minion');
    const pkt = Buffer.alloc(7); pkt[0] = 0; pkt.writeInt32BE(minion, 1); pkt[5] = 1; pkt[6] = 1;
    bot._client.write('custom_payload', {channel: 'mutantbeasts', data: pkt});
    bot._client.write('custom_payload', {channel: 'mutantbeasts', data: Buffer.from([0, 1, 2])});
    check(await until(() => /MUTANTS_PACKET_REJECTED channel=mutantbeasts reason=not_owner/.test(log) && /MUTANTS_PACKET_REJECTED channel=mutantbeasts reason=size/.test(log), 5000),
      'The mod channel refuses a stranger\'s minion and a short packet', (log.match(/MUTANTS_PACKET_REJECTED[^\n]*/g) || []).join(' | '));

    // Brewing: thick potions + an End Crystal become Chemical X (Forge's BrewingRecipeRegistry).
    const br = (await cmd('mprobe brew ' + NAME, /PROBE_BREW_STARTED/)).line;
    const pos = [field(br, 'x'), field(br, 'y'), field(br, 'z')].join(' ');
    await sleep(1500);
    const bt = (await cmd('mprobe brewfast ' + pos, /PROBE_BREWTIME/)).line;
    await sleep(2500);
    const bd = (await cmd('mprobe brewed ' + pos, /PROBE_BREWED/)).line;
    check(+field(bt, 'was') > 0 && /0=MUTANTBEASTS_CHEMICAL_Xx1,1=MUTANTBEASTS_CHEMICAL_Xx1,2=MUTANTBEASTS_CHEMICAL_Xx1,3=EMPTY/.test(bd), 'A brewing stand turns thick potions and an End Crystal into Chemical X', bt + ' ' + bd);

    // Forge's item hooks: the enchanting table (Item.canApplyAtEnchantingTable), armour ticking (onArmorTick), Creeper Shards
    // surviving explosions, the Hulk Hammer's seismic waves (onItemRightClick + the player tick).
    const en = (await cmd('mprobe enchant ' + NAME, /PROBE_ENCHANT/, 20000)).line;
    const weapon = /(DAMAGE_ALL|DAMAGE_UNDEAD|DAMAGE_ARTHROPODS|KNOCKBACK|FIRE_ASPECT|LOOT_BONUS_MOBS):/;
    const part = k => (en.match(new RegExp(k + '\\[[^\\]]*\\]', 'g')) || ['']).join(' ');
    check(weapon.test(part('MUTANTBEASTS_HULK_HAMMER')) && /done=true/.test(part('MUTANTBEASTS_HULK_HAMMER')) && !/SWEEPING/.test(part('MUTANTBEASTS_HULK_HAMMER')) && /clues=\d/.test(part('MUTANTBEASTS_HULK_HAMMER')),
      'The enchanting table offers and applies weapon enchantments to the Hulk Hammer (no Sweeping Edge), with clues', part('MUTANTBEASTS_HULK_HAMMER'));
    check(weapon.test(part('MUTANTBEASTS_ENDERSOUL_HAND')) && /done=true/.test(part('MUTANTBEASTS_ENDERSOUL_HAND')), 'and to the Endersoul Hand', part('MUTANTBEASTS_ENDERSOUL_HAND'));
    check(/done=true enchants=[A-Z_]+:/.test(part('DIAMOND_SWORD')), 'A vanilla sword still enchants at the same table', part('DIAMOND_SWORD'));
    await cmd('mprobe armor ' + NAME, /PROBE_ARMOR/); await sleep(1000);
    const ef = (await cmd('mprobe effects ' + NAME, /PROBE_EFFECTS/)).line;
    check(field(ef, 'speed') === 'true' && field(ef, 'jump') === 'true' && +field(ef, 'armor') >= 15, 'Mutant Skeleton armour: leggings give Speed, boots Jump Boost (onArmorTick), full set armour 15', ef);
    await cmd('mprobe unarmor ' + NAME, /PROBE_UNARMOR/);
    const sh = (await cmd('mprobe shard ' + NAME, /PROBE_SHARD/, 10000)).line;
    check(field(sh, 'shard') === 'true' && field(sh, 'dirt') === 'false', 'An explosion destroys a dirt item but not a Creeper Shard next to it', sh);
    await cmd('mprobe give ' + NAME + ' MUTANTBEASTS_HULK_HAMMER', /PROBE_GIVE/);
    await bot.look(0, -0.9, true); await sleep(600);
    const vic = (await cmd('mprobe victim ' + NAME + ' 4', /PROBE_VICTIM/)).line;
    bot.activateItem(); await sleep(300); bot.deactivateItem();
    await sleep(2500);
    const vh = (await cmd('mprobe health ' + field(vic, 'id'), /PROBE_HEALTH/)).line;
    check(field(vh, 'health') === 'gone' || +field(vh, 'health') < +field(vic, 'health'), 'The Hulk Hammer\'s seismic wave hurts a zombie in front of the player', vic + ' -> ' + vh);

    // The mutants fight: a survival player near a Mutant Zombie and a Mutant Skeleton gets attacked.
    await cmd('mprobe clear', /PROBE_CLEARED/);
    await cmd('gamemode survival ' + NAME, /game mode|gamemode/i);
    await cmd('effect ' + NAME + ' minecraft:resistance 600 4 true', /effect|Given|Applied/i);
    await cmd('effect ' + NAME + ' minecraft:regeneration 600 4 true', /effect|Given|Applied/i);
    seen.status.length = 0; const spawnsBefore = seen.spawns.length;
    await cmd('mprobe spawn ' + NAME + ' mutant_zombie 5 0', /PROBE_SPAWNED/);
    await cmd('mprobe spawn ' + NAME + ' mutant_skeleton -6 0', /PROBE_SPAWNED/);
    const zid = lastId('mutant_zombie');
    const sid = lastId('mutant_skeleton');
    const attacked = await until(() => seen.status.some(([e, s]) => (e === zid || e === sid) && s < 0) || seen.spawns.slice(spawnsBefore).some(s => s.type === 216), 30000);
    check(attacked, 'The Mutant Zombie or Mutant Skeleton attacks the player (an attack status byte or a mutant arrow)',
      JSON.stringify(seen.status.filter(([e]) => e === zid || e === sid).slice(0, 12)) + ' new spawns ' + JSON.stringify(seen.spawns.slice(spawnsBefore).map(s => s.type)));
    await cmd('gamemode creative ' + NAME, /game mode|gamemode/i);

    // Persistence: chunks saved and loaded again keep every mutant (NBT round trip), then a restart.
    await cmd('mprobe clear', /PROBE_CLEARED/);
    await cmd('mprobe spawnall ' + NAME, /PROBE_OK spawnall/, 20000);
    const before = (await cmd('mprobe count', /PROBE_COUNT/)).line;
    await cmd('tp ' + NAME + ' 3000 80 3000', /Teleported|teleport/i); await sleep(3000);
    const ul = (await cmd('mprobe unloadmod', /PROBE_UNLOADED/)).line; await sleep(1500);
    const during = (await cmd('mprobe count', /PROBE_COUNT/)).line;
    const ld = (await cmd('mprobe loadmod', /PROBE_LOADED/)).line; await sleep(1500);
    const reloaded = (await cmd('mprobe count', /PROBE_COUNT/)).line;
    check(/unloaded=[1-9]/.test(ul) && /PROBE_COUNT \{\}/.test(during) && reloaded.replace(/^.*PROBE_COUNT /, '') === before.replace(/^.*PROBE_COUNT /, ''), 'Unloading their chunks removes the mutants; loading them again brings every one back (save/load)', ul + ' | ' + during + ' | ' + ld + ' | ' + reloaded);
    await cmd('save-all', /Saved the world|saved/i, 30000);
    await stop();
    await start();
    check(/MUTANTS_READY/.test(log), 'After a restart the plugin is ready again', (log.match(/MUTANTS_READY[^\n]*/) || [''])[0]);
    bot = await join();
    await cmd('tp ' + NAME + ' 0 5 0', /Teleported|teleport/i); await sleep(4000);
    const after = (await cmd('mprobe count', /PROBE_COUNT/)).line;
    check(after.replace(/^.*PROBE_COUNT /, '') === before.replace(/^.*PROBE_COUNT /, ''), 'Every mutant is still there after the restart (registered before the worlds load)', before + ' -> ' + after);
    check(!/Skipping Entity with id mutantbeasts|Unknown entity|MUTANTS_HOOK_FAILED|MUTANTS_TRACKER_REFLECTION_FAILED|MUTANTS_LOOT_INJECT_FAILED|MUTANTS_ADVANCEMENTS_FAILED/.test(fs.readdirSync(RUN).filter(f => f.startsWith('phase-')).map(f => fs.readFileSync(path.join(RUN, f), 'utf8')).join('\n')), 'No skipped entity, hook, tracker, loot or advancement failure in any phase');
    const allLogs = fs.readdirSync(RUN).filter(f => f.startsWith('phase-')).map(f => fs.readFileSync(path.join(RUN, f), 'utf8')).join('\n');
    const ex = allLogs.split('\n').filter(l => /Exception|Error:/.test(l) && !/GeoLite|IIOException|Could not pass event .*bStats/.test(l));
    check(ex.length === 0, 'No exception in the server logs', ex.slice(0, 5).join(' | '));
    console.log('NOTE packets ' + JSON.stringify(seen.names));
    const status = await cmd('mutants status', /MUTANTS_STATUS/);
    console.log('NOTE ' + status.line);
  } catch (e) {
    failures++;
    console.log('FAIL ' + (e && e.stack || e));
  } finally {
    await stop();
    fs.writeFileSync(path.join(RUN, 'results.json'), JSON.stringify(results, null, 1));
    console.log((failures ? 'MUTANTS_RUNTIME_FAIL' : 'MUTANTS_RUNTIME_PASS') + ' checks=' + checks + ' failures=' + failures + ' run=' + RUN);
    process.exit(failures ? 1 : 0);
  }
})();
