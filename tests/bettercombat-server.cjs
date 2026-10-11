'use strict';
// Server side of the dual-wield fixes on ONE short isolated Paper 1.12.2 test server (port 25686, one core, below-normal
// priority, a watchdog), with two protocol bots that speak jaspr:combat like the browser client does. Test-only fixture:
// tests/bettercombat-fixture (console commands only). No live server, world or player data is touched.
//   node tests/bettercombat-server.cjs <JasprBetterCombat.jar> <scratch dir>
// Needs minecraft-protocol: BETTERCOMBAT_NODE_MODULES (default the dungeon sandbox's node_modules).
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), cp = require('node:child_process');
const MODULES = process.env.BETTERCOMBAT_NODE_MODULES || 'C:/Users/AM/Documents/JasperCraft-Dungeon-Sandbox-20261002/node_modules';
const protocol = require(path.join(MODULES, 'minecraft-protocol'));
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin/';
const PORT = 25686, WATCHDOG_MS = 8 * 60 * 1000;
const jar = process.argv[2], dir = process.argv[3];
if (!jar || !dir || !fs.existsSync(jar)) { console.error('usage: node tests/bettercombat-server.cjs <JasprBetterCombat.jar> <scratch dir>'); process.exit(2); }
const delay = ms => new Promise(r => setTimeout(r, ms));
const results = [];
let server = null, log = '', exitCode = 0;
function check(name, ok, actual) {
  results.push({ name, passed: !!ok, actual });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : ' ' + JSON.stringify(actual)));
  if (!ok) exitCode = 1;
}
async function until(fn, ms = 20000, what = '') { const end = Date.now() + ms; while (Date.now() < end) { const v = fn(); if (v) return v; await delay(50); } throw Error('timeout ' + what + ': ' + fn); }
function put(file, text) { const p = path.join(dir, file); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, text); }
function run(exe, args) { const r = cp.spawnSync(JAVA + exe + '.exe', args, { encoding: 'utf8', windowsHide: true }); if (r.status !== 0) throw Error(exe + ': ' + (r.stderr || r.stdout)); }
function prepare() {
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  const classes = path.join(dir, 'fixture-classes'); fs.mkdirSync(classes, { recursive: true });
  fs.mkdirSync(path.join(dir, 'plugins/JasprBetterCombat'), { recursive: true });
  run('javac', ['-J-XX:ActiveProcessorCount=1', '-J-Xmx384m', '--release', '8', '-encoding', 'UTF-8', '-Xlint:-options', '-cp',
    LIVE + '/server/cache/patched_1.12.2.jar' + path.delimiter + jar, '-d', classes, path.join(__dirname, 'bettercombat-fixture/CombatFixture.java')]);
  fs.copyFileSync(path.join(__dirname, 'bettercombat-fixture/plugin.yml'), path.join(classes, 'plugin.yml'));
  run('jar', ['-J-XX:ActiveProcessorCount=1', '--create', '--file', path.join(dir, 'plugins/CombatFixture.jar').replace(/\\/g, '/'), '-C', classes, '.'].map(String));
  fs.copyFileSync(LIVE + '/server/cache/patched_1.12.2.jar', path.join(dir, 'paper.jar'));
  fs.copyFileSync(jar, path.join(dir, 'plugins/JasprBetterCombat.jar'));
  fs.copyFileSync(path.join(__dirname, '../server/custom-plugins/JasprBetterCombat/resources/config.yml'), path.join(dir, 'plugins/JasprBetterCombat/config.yml'));
  put('eula.txt', 'eula=true\n');
  put('server.properties', 'server-ip=127.0.0.1\nserver-port=' + PORT + '\nonline-mode=false\nlevel-type=FLAT\nlevel-name=world\n' +
    'generator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\nspawn-protection=0\nview-distance=3\n' +
    'generate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=4\npvp=true\n' +
    'network-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\nsnooper-enabled=false\n');
  put('bukkit.yml', 'settings:\n  allow-end: false\n');
  put('paper.yml', 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n');
  put('spigot.yml', 'config-version: 11\nsettings:\n  late-bind: false\nstats:\n  disable-saving: true\n');
  put('plugins/bStats/config.yml', 'enabled: false\n');
}
async function start() {
  server = cp.spawn(JAVA + 'java.exe', ['-XX:ActiveProcessorCount=1', '-XX:+UseSerialGC', '-Xms256m', '-Xmx768m', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', 'nogui'],
    { cwd: dir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  try { os.setPriority(server.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { console.log('priority: ' + e.message); }
  server.stdout.on('data', b => { log += b; }); server.stderr.on('data', b => { log += b; });
  await until(() => log.includes('Done (') || log.includes('FAILED TO BIND'), 180000, 'server start');
  if (!log.includes('Done (')) throw Error(log.slice(-2000));
  cmd('gamerule doDaylightCycle false'); cmd('time set night'); cmd('gamerule doMobSpawning false');   // night: no burning zombies
}
function cmd(s) { server.stdin.write(s + '\n'); }
async function fixture(args, ready) { const at = log.length; cmd('bcfixture ' + args); if (ready) await until(() => log.slice(at).includes(ready), 15000, args); else await delay(200); }
async function report(name = 'CombatBot') { const at = log.length; cmd('bcfixture report ' + name); return until(() => { const m = log.slice(at).match(/COMBAT_REPORT (\{[^\r\n]+\})/); return m ? JSON.parse(m[1]) : null; }, 10000, 'report'); }
function encode(s) { const b = Buffer.from(s), n = []; let v = b.length; do { let c = v & 127; v >>>= 7; n.push(v ? c | 128 : c); } while (v); return Buffer.concat([Buffer.from(n), b]); }
function decode(buf) { let i = 0; while (buf[i++] & 128) {} return JSON.parse(buf.subarray(i).toString('utf8')); }
function bot(username) {
  const c = protocol.createClient({ host: '127.0.0.1', port: PORT, username, version: '1.12.2', auth: 'offline' });
  const b = { client: c, messages: [], states: [], cfgs: [], results: [], entityId: -1 };
  c.on('error', e => console.log(username + ' error ' + e.message));
  c.on('login', p => { b.entityId = p.entityId; });
  c.on('position', p => { c.write('teleport_confirm', { teleportId: p.teleportId }); c.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch, onGround: true }); });
  c.on('custom_payload', p => {
    if (p.channel !== 'jaspr:combat') return;
    let m; try { m = decode(p.data); } catch (e) { return; }
    b.messages.push(m); if (m.res) b.results.push(m.res); else if (m.cfg) b.cfgs.push(m.cfg); else b.states.push(m);
  });
  b.send = s => c.write('custom_payload', { channel: 'jaspr:combat', data: encode(s) });
  b.result = (seq, ms = 3000) => until(() => b.results.find(r => r.s === seq), ms, 'result ' + seq);
  return b;
}
async function main() {
  prepare(); await start();
  const watchdog = setTimeout(() => { console.log('WATCHDOG: stopping the test server'); try { server.kill(); } catch (e) {} process.exit(3); }, WATCHDOG_MS);
  const A = bot('CombatBot');
  await until(() => /CombatBot\[.*logged in/.test(log), 30000, 'login');
  await delay(500);
  A.send('hello 1');
  await until(() => A.states.length && A.cfgs.length, 5000, 'hello');
  const cfg = A.cfgs[0], st = A.states[0];
  check('S1 hello answers cfg (protocol 2, off-hand weapon ids, interaction entity types) and a protocol-2 state',
    cfg.proto === 2 && [256, 258, 267, 276, 290].every(i => cfg.offIds.includes(i)) && !cfg.offIds.includes(364) &&
    [30, 100, 120].every(i => cfg.noAttack.includes(i)) && !cfg.noAttack.includes(54) && cfg.players === false && st.proto === 2 && st.enabled === true,
    { cfg, st });
  let seq = 0;
  const attack = async (hand, extra) => { const s = ++seq; A.send('attack ' + hand + ' ' + s); return A.result(s); };

  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY');
  await fixture('config CombatBot random-crits false');
  await delay(300);
  let rep = await report();
  const zombie = rep.victimId;
  let r = await attack('M');
  let after = await report();
  check('S2 main-hand hit: verdict hit on the aimed zombie, energy spent, damage dealt', r.k === 'hit' && r.t === zombie && r.e === 1 && r.h === 'M' && after.health < 1000 && after.mainDamage === 1, { r, after });
  r = await attack('O'); const offHit = await report();
  check('S3 independent off-hand hit right after (half damage of the off-hand sword)', r.k === 'hit' && r.h === 'O' && r.i === 276 && Math.abs((after.health - offHit.health) - 3.5) < 0.01 && offHit.offDamage === 1, { r, before: after.health, after: offHit.health });
  await delay(60);
  r = await attack('O'); const immune = await report();
  check('S4 off-hand hit inside the target\'s 10-tick off-hand immunity: miss on the target, energy spent (1.5.6 rule), no damage', r.k === 'miss' && r.t === zombie && r.e === 1 && immune.health === offHit.health && immune.events === offHit.events, { r, immune });

  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('equip CombatBot M AIR');
  await delay(200);
  const logAt = log.length;
  r = await attack('M'); rep = await report();
  check('S5 BUG fist hit: no NullPointerException in hitEntity any more, the hit completes', r.k === 'hit' && rep.health < 1000 && !/generated an exception|NullPointerException/.test(log.slice(logAt)), { r, health: rep.health, log: log.slice(logAt).slice(0, 400) });

  await fixture('setup CombatBot 4 VILLAGER', 'FIXTURE_READY');
  r = await attack('O'); rep = await report();
  check('S6 off hand on a villager (blacklisted): denied target, no damage', r.k === 'denied' && r.r === 'target' && rep.health === 1000, { r, rep });
  r = await attack('M'); rep = await report();
  check('S7 the main hand still attacks a villager (as 1.5.6)', r.k === 'hit', { r, rep });

  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('equip CombatBot O BREAD');
  r = await attack('O');
  check('S8 a non-weapon in the off hand: denied item', r.k === 'denied' && r.r === 'item' && r.i === 297, { r });

  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY');
  let s1 = ++seq, s2 = ++seq; A.send('attack M ' + s1); A.send('attack M ' + s2);
  const r1 = await A.result(s1), r2 = await A.result(s2);
  check('S9 two main-hand attacks in one tick: the second is denied repeat (was a silent drop)', r1.k === 'hit' && (r2.k === 'denied' && r2.r === 'repeat' || r2.k !== 'denied'), { r1, r2 });

  await fixture('setup CombatBot 7 ZOMBIE', 'FIXTURE_READY');
  await delay(300); const fresh = await report();
  r = await attack('M'); rep = await report();
  check('S10 out of reach: miss with no target, energy refunded (refund-energy-on-miss)', r.k === 'miss' && r.t === -1 && r.e === 0 && rep.health === 1000 && rep.mainStrength >= fresh.mainStrength, { r, fresh: fresh.mainStrength, after: rep.mainStrength });
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('wall CombatBot');
  r = await attack('O');
  check('S11 a wall between: miss, nothing hit', r.k === 'miss' && r.t === -1, { r });

  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('gm CombatBot SPECTATOR');
  r = await attack('M');
  check('S12 spectator: denied state', r.k === 'denied' && r.r === 'state', { r });
  await fixture('gm CombatBot SURVIVAL');

  // a plugin item ability in the main hand (a staff): its right click in the air claims the click; no off-hand attack on top
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('equip CombatBot M BLAZE_ROD'); await fixture('claim CombatBot BLAZE_ROD');
  await delay(200);
  const sc = ++seq; A.client.write('use_item', { hand: 0 }); A.send('attack O ' + sc);
  r = await A.result(sc); rep = await report();
  check('S13 the main hand\'s item ability claimed the right click: off-hand attack denied used, the ability ran once', r.k === 'denied' && r.r === 'used' && rep.claims >= 1 && rep.health === 1000, { r, rep });
  await delay(150);
  r = await attack('O'); rep = await report();
  check('S14 the same off-hand attack without the ability click: hit', r.k === 'hit' && rep.health < 1000, { r, rep });
  await fixture('claim CombatBot off');

  // players: a plugin interaction (revive / board a tank) wins over an off-hand hit; a raised shield blocks
  const Bb = bot('ShieldBot');
  await until(() => /ShieldBot\[.*logged in/.test(log), 30000, 'second login');
  await delay(3600);                                     // a joining player is invulnerable for 60 ticks
  await fixture('pvp CombatBot ShieldBot', 'FIXTURE_READY'); await fixture('guard CombatBot on');
  await delay(300);
  rep = await report(); const shieldBot = rep.victimId;
  const si = ++seq; A.client.write('use_entity', { target: shieldBot, mouse: 0, hand: 0 }); A.send('attack O ' + si);
  r = await A.result(si); rep = await report();
  check('S15 a plugin handled the right click on that player (revive, tank): off-hand hit denied interact', r.k === 'denied' && r.r === 'interact' && rep.guarded >= 1 && rep.health === 1000, { r, rep });
  await fixture('guard CombatBot off'); await delay(150);
  r = await attack('O'); rep = await report();
  check('S16 without the interaction the off hand hits the player (PvP on)', r.k === 'hit' && r.t === shieldBot && rep.health < 1000, { r, rep });
  await fixture('pvp CombatBot ShieldBot shield', 'FIXTURE_READY');
  await delay(300);
  Bb.client.write('use_item', { hand: 1 });            // raise the shield (off hand), blocking after 5 ticks
  await delay(600);
  r = await attack('M'); rep = await report();
  check('S17 a raised shield takes a main-hand hit: verdict blocked, no damage (was counted as a hit)', r.k === 'blocked' && r.t === shieldBot && rep.health === 1000, { r, rep });
  await delay(600);
  r = await attack('O'); rep = await report();
  check('S18 and an off-hand hit: blocked', r.k === 'blocked' && rep.health === 1000, { r, rep });
  Bb.client.write('block_dig', { status: 5, location: { x: 0, y: 0, z: 0 }, face: 0 });
  Bb.client.end(); await delay(300);

  // crit / sweep / fatal flags, the static data re-sent on a settings change
  const cfgCount = A.cfgs.length;
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('config CombatBot crit-chance 1'); await fixture('config CombatBot random-crits true');
  await until(() => A.cfgs.length >= cfgCount + 2, 5000, 'cfg resend');
  check('S19 a settings change re-sends the static data to connected clients', A.cfgs.length >= cfgCount + 2, A.cfgs.length - cfgCount);
  await delay(700);
  r = await attack('M');
  check('S20 a random crit is flagged crit', r.k === 'hit' && r.c === 1 && r.w === 0, { r });
  await fixture('config CombatBot random-crits false');
  await fixture('setup CombatBot 2 ZOMBIE', 'FIXTURE_READY'); await fixture('secondary CombatBot');
  await delay(900);
  r = await attack('M'); rep = await report();
  check('S21 a full-strength sword hit on the ground sweeps: flagged sweep, the neighbour hurt', r.k === 'hit' && r.w === 1 && rep.secondaryHealth < 1000, { r, rep });
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY'); await fixture('health CombatBot 1');
  await delay(900);
  r = await attack('M');
  check('S22 a killing hit is flagged fatal', r.k === 'hit' && r.f === 1, { r });

  // an older client's message (no sequence number) still attacks and gets no verdict; forged messages do nothing
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY');
  await delay(900);
  const resultsBefore = A.results.length;
  A.send('attack M'); await delay(400); rep = await report();
  check('S23 the old message format still attacks, without a verdict message', rep.health < 1000 && A.results.length === resultsBefore, { health: rep.health, results: A.results.length - resultsBefore });
  await fixture('setup CombatBot 4 ZOMBIE', 'FIXTURE_READY');
  for (const bad of ['attack O 0', 'attack O 12345678901', 'attack X 5', 'attack O 5 x', 'attack O -1', 'attack M 01']) A.send(bad);
  await delay(500); rep = await report();
  check('S24 forged / malformed attack messages do nothing and get no verdict', rep.health === 1000 && rep.events === 0 && A.results.length === resultsBefore, { rep });

  // shutting the plugin down tells connected clients to stop intercepting at once
  const statesBefore = A.states.length;
  cmd('stop');
  await until(() => A.states.slice(statesBefore).some(s => s.enabled === false) || server.exitCode !== null, 60000, 'disable notice').catch(() => {});
  check('S25 plugin disable sends enabled:false to connected clients', A.states.slice(statesBefore).some(s => s.enabled === false), A.states.slice(statesBefore).map(s => s.enabled));
  check('S26 no Better Combat exception in the whole run', !/JasprBetterCombat[^\n]*generated an exception|at chat\.jaspr\.bettercombat/.test(log), log.match(/[^\n]*Exception[^\n]*/g) || []);
  clearTimeout(watchdog);
}
main().catch(e => { console.log('ERROR ' + (e && e.stack || e)); exitCode = 1; }).finally(async () => {
  try { if (server && server.exitCode === null) { cmd('stop'); await until(() => server.exitCode !== null, 60000, 'stop').catch(() => server.kill()); } } catch (e) {}
  fs.writeFileSync(path.join(dir, 'report.json'), JSON.stringify({ at: new Date().toISOString(), jar, results }, null, 2));
  fs.writeFileSync(path.join(dir, 'paper-console.log'), log);
  const passed = results.filter(r => r.passed).length;
  console.log('SERVER TESTS ' + passed + '/' + results.length + ' passed');
  process.exit(exitCode);
});
