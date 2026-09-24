'use strict';
// Disposable, loopback-only Paper + browser fixture for Survivor Gear. No production writes.
// Paper/Eagler on 127.0.0.1:25597, static test page on 127.0.0.1:25598. Server console lines can
// be queued by appending to <fixture>/commands.txt. Stops itself after 45 minutes or on "stop".
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), net = require('node:net');
const {spawn} = require('node:child_process'), crypto = require('node:crypto');
const root = path.resolve(__dirname, '..'), java = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const SOCKET = Number(process.env.GEAR_SOCKET_PORT || 25597); // Paper test server: 25597 or 25598 only
let WEB = Number(process.env.GEAR_WEB_PORT || 0); // static page: any free loopback port
// GEAR_FIXTURE_DIR=<existing fixture> restarts that same world and plugin data (restart/persistence test).
const reuse = process.env.GEAR_FIXTURE_DIR && fs.existsSync(path.join(process.env.GEAR_FIXTURE_DIR, 'server', 'paper.jar'));
const fixture = reuse ? process.env.GEAR_FIXTURE_DIR : path.join(root, 'candidate', 'gear-preview-' + crypto.randomUUID()), server = path.join(fixture, 'server');
function write(file, data) { const p = path.join(fixture, file); fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, data); }
function free(port) { return new Promise(resolve => { const s = net.createServer(); s.once('error', () => resolve(false)); s.listen(port, '127.0.0.1', () => s.close(() => resolve(true))); }); }
(async () => {
  if (SOCKET !== 25597 && SOCKET !== 25598) { console.error('Paper test port must be 25597 or 25598'); process.exit(2); }
  if (!(await free(SOCKET))) { console.error('Port ' + SOCKET + ' is busy; refusing to start.'); process.exit(2); }
  if (!WEB) WEB = await new Promise(r => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)); }); });
  if (reuse) { fs.copyFileSync(process.env.GEAR_JAR || path.join(root, 'candidate/gear/JasprGear.jar'), path.join(server, 'plugins/JasprGear.jar')); return start(); }
  fs.mkdirSync(path.join(server, 'plugins'), {recursive: true});
  fs.copyFileSync(path.join(root, 'server/cache/patched_1.12.2.jar'), path.join(server, 'paper.jar'));
  for (const file of ['EaglerXServer.jar', 'EaglerXRewind.jar', 'ViaVersion.jar', 'ViaBackwards.jar', 'ViaRewind.jar', 'ViaRewind-Legacy-Support.jar'])
    fs.copyFileSync(path.join(root, 'server/plugins', file), path.join(server, 'plugins', file));
  fs.copyFileSync(process.env.GEAR_JAR || path.join(root, 'candidate/gear/JasprGear.jar'), path.join(server, 'plugins/JasprGear.jar'));
  write('server/eula.txt', 'eula=true\n');
  write('server/server.properties', `server-ip=127.0.0.1\nserver-port=${SOCKET}\nonline-mode=false\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\nlevel-name=world\nspawn-protection=0\nview-distance=3\ngenerate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=2\nnetwork-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\ndifficulty=1\n`);
  write('server/bukkit.yml', 'settings:\n  allow-end: false\n');
  write('server/paper.yml', 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n');
  write('server/spigot.yml', 'config-version: 11\nsettings:\n  late-bind: true\n');
  write('server/plugins/bStats/config.yml', 'enabled: false\nserverUuid: "' + crypto.randomUUID() + '"\n');
  write('server/plugins/EaglercraftXServer/listener.yml', 'dual_stack: true\nforward_ip: false\nforward_secret: false\ntls_config:\n  enable_tls: false\n  require_tls: false\nratelimit:\n  disable_ratelimit: [127.0.0.0/8]\n');
  write('server/plugins/EaglercraftXServer/settings.yml', 'server_name: Gear fixture\nserver_uuid: "' + crypto.randomUUID() + '"\nprotocols:\n  max_minecraft_protocol: 340\n  eaglerxrewind_allowed: true\n  protocol_v3_allowed: true\n  protocol_v4_allowed: true\nvoice_service:\n  enable_voice_service: false\nupdate_checker:\n  enable_update_checker: false\nupdate_service:\n  enable_update_system: false\n  download_latest_certs: false\n');
  start();
})();
function start() {
  const html = `<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Gear fixture</title><style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:black}#status{position:fixed;bottom:0;left:0;z-index:9;font:11px monospace;color:#fff;background:#000a;pointer-events:none}</style><script src="/classes.js"></script><script src="/jaspr-profile.js"></script></head><body><div id="game_frame"></div><output id="status">gear fixture</output><script>JasprProfile.prepare('GearProbe',localStorage).then(function(){window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_gear_fixture',localStorageLoaded:function(k){return localStorage.getItem(k.startsWith('_eaglercraft_')?k:'_eaglercraft_1122_gear_fixture.'+k);},worldsDB:'gear_fixture_worlds',resourcePacksDB:'gear_fixture_packs',joinServer:'ws://127.0.0.1:${SOCKET}/',servers:[],relays:[],crashOnUncaughtExceptions:true};main();});setInterval(function(){if(window.JasprGearDiagnostics){document.querySelector('#status').textContent=JSON.stringify(JasprGearDiagnostics.status());}},500);</script></body></html>`;
  const allowed = {'/classes.js': process.env.GEAR_CLASSES || 'candidate/gear-client/classes.js', '/assets.epk': process.env.GEAR_EPK || 'candidate/gear/assets.epk', '/jaspr-profile.js': 'site/jaspr-profile.js'};
  const web = http.createServer((req, res) => {
    if (req.url === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    const key = req.url.split('?')[0];
    if (!allowed[key]) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', key.endsWith('.js') ? 'application/javascript; charset=utf-8' : 'application/octet-stream');
    res.setHeader('Cache-Control', 'no-store');
    fs.createReadStream(path.join(root, allowed[key])).pipe(res);
  });
  web.listen(WEB, '127.0.0.1');
  const log = fs.createWriteStream(path.join(fixture, 'paper.log'));
  const child = spawn(path.join(java, 'java.exe'), ['-Xms256M', '-Xmx1G', '-XX:ActiveProcessorCount=2', '-Djaspr.gear.selftest=true',
    '-Djava.awt.headless=true', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', '--nojline'], {cwd: server, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  const output = data => { log.write(data); if (/GEAR_|Done \(|ERROR|SEVERE|Exception|logged in/.test(data.toString())) process.stdout.write(data); };
  child.stdout.on('data', output); child.stderr.on('data', output);
  const commands = path.join(fixture, 'commands.txt');
  fs.writeFileSync(commands, '');
  let consumed = 0;
  const poll = setInterval(() => {
    try {
      const text = fs.readFileSync(commands, 'utf8');
      if (text.length <= consumed) return;
      const fresh = text.slice(consumed); consumed = text.length;
      for (const line of fresh.split(/\r?\n/)) if (line.trim()) { child.stdin.write(line.trim() + '\n'); if (line.trim() === 'stop') stop(); }
    } catch (ignored) { }
  }, 500);
  let stopping = false;
  function stop() { if (stopping) return; stopping = true; try { child.stdin.write('stop\n'); } catch (e) { } setTimeout(() => { if (child.exitCode === null) child.kill(); }, 25000).unref(); }
  process.on('SIGINT', stop); process.on('SIGTERM', stop);
  const timeout = setTimeout(stop, 45 * 60 * 1000);
  child.on('exit', code => { clearTimeout(timeout); clearInterval(poll); web.close(); log.end(); console.log('PAPER_EXIT ' + code); process.exit(); });
  process.on('exit', () => { if (child.exitCode === null) child.kill(); });
  write('fixture.json', JSON.stringify({fixture, webPort: WEB, socketPort: SOCKET, pid: child.pid, node: process.pid}, null, 2));
  console.log('Preview URL: http://127.0.0.1:' + WEB + '/');
  console.log('Fixture: ' + fixture);
}
