'use strict';
// Disposable, loopback-only browser/Paper fixture for JasprTanks. No production files are written or served
// from the live plugin folder; the candidate plugin, client and asset archive come from candidate/.
// Usage: node scripts/tank-preview.cjs  (prints the page URL; GET /console?c=<command> runs a server command)
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), net = require('node:net');
const {spawn} = require('node:child_process'), crypto = require('node:crypto'), os = require('node:os');
const root = path.resolve(__dirname, '..'), java = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture = path.join(root, 'candidate/tank-preview-' + crypto.randomUUID()), server = path.join(fixture, 'server');
fs.mkdirSync(path.join(server, 'plugins'), {recursive: true});
function write(file, data) { const p = path.join(fixture, file); fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, data); }
async function port() { const s = net.createServer(); await new Promise(r => s.listen(0, '127.0.0.1', r)); const p = s.address().port; await new Promise(r => s.close(r)); return p; }
(async () => {
  const socketPort = await port(), webPort = await port();
  fs.copyFileSync(path.join(root, 'server/cache/patched_1.12.2.jar'), path.join(server, 'paper.jar'));
  // Lean fixture: 1.12 browsers need only EaglerXServer (no Via/Rewind protocol translation).
  fs.copyFileSync(path.join(root, 'server/plugins/EaglerXServer.jar'), path.join(server, 'plugins/EaglerXServer.jar'));
  fs.copyFileSync(path.join(root, 'candidate/tanks/JasprTanks.jar'), path.join(server, 'plugins/JasprTanks.jar'));
  write('server/eula.txt', 'eula=true\n');
  write('server/server.properties', `server-ip=127.0.0.1\nserver-port=${socketPort}\nonline-mode=false\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\nlevel-name=world\nspawn-protection=0\nview-distance=3\ngenerate-structures=false\nallow-nether=false\nspawn-animals=true\nspawn-monsters=true\nspawn-npcs=true\nmax-players=3\nnetwork-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\ndifficulty=2\npvp=true\n`);
  write('server/bukkit.yml', 'settings:\n  allow-end: false\n');
  write('server/paper.yml', 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n');
  write('server/spigot.yml', 'config-version: 11\nsettings:\n  late-bind: true\nworld-settings:\n  default:\n    view-distance: 3\n    mob-spawn-range: 1\n    entity-activation-range:\n      animals: 8\n      monsters: 16\n      misc: 4\n');
  write('server/plugins/bStats/config.yml', 'enabled: false\nserverUuid: "' + crypto.randomUUID() + '"\n');
  write('server/plugins/EaglercraftXServer/listener.yml', 'dual_stack: true\nforward_ip: false\nforward_secret: false\ntls_config:\n  enable_tls: false\n  require_tls: false\nratelimit:\n  disable_ratelimit: [127.0.0.0/8]\n');
  write('server/plugins/EaglercraftXServer/settings.yml', 'server_name: Tank fixture\nserver_uuid: "' + crypto.randomUUID() + '"\nprotocols:\n  max_minecraft_protocol: 340\n  eaglerxrewind_allowed: true\n  protocol_v3_allowed: true\n  protocol_v4_allowed: true\nvoice_service:\n  enable_voice_service: false\nupdate_checker:\n  enable_update_checker: false\nupdate_service:\n  enable_update_system: false\n  download_latest_certs: false\n');
  const html = `<!doctype html><html style="width:100%;height:100%;background:black"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,viewport-fit=cover"><title>JasperCraft tank fixture</title>
<script defer src="/jaspercraft-video-core.js"></script><script defer src="/classes.js"></script><link rel="stylesheet" href="/jaspercraft-mobile-controls.css"><script defer src="/jaspercraft-mobile-controls.js"></script><script src="/jaspr-profile.js"></script></head>
<body id="game_frame" style="margin:0;width:100%;height:100%;overflow:hidden;background:black"><script>addEventListener('DOMContentLoaded',function(){var name=(location.hash.slice(1)||'TankProbe');JasprProfile.prepare(name,localStorage).then(function(){window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_tank_fixture',worldsDB:'tank_fixture_worlds',resourcePacksDB:'tank_fixture_packs',joinServer:'ws://127.0.0.1:${socketPort}/',servers:[],relays:[],crashOnUncaughtExceptions:true};main();});});</script></body></html>`;
  const files = {
    '/classes.js': process.env.TANK_PREVIEW_CLASSES || 'candidate/tank-client/classes.js',
    '/assets.epk': 'candidate/tanks/assets.epk',
    '/jaspr-profile.js': 'site/jaspr-profile.js',
    '/jaspercraft-mobile-controls.js': 'candidate/tank-client/jaspercraft-mobile-controls.js',
    '/jaspercraft-mobile-controls.css': 'candidate/tank-client/jaspercraft-mobile-controls.css',
  };
  const tail = [];
  let child;
  const web = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    if (url.pathname === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    if (url.pathname === '/console') {
      const command = (url.searchParams.get('c') || '').replace(/[\r\n]/g, '').slice(0, 300);
      if (command) child.stdin.write(command + '\n');
      setTimeout(() => { res.setHeader('Content-Type', 'text/plain; charset=utf-8'); res.end(tail.slice(-40).join('')); }, 400);
      return;
    }
    if (url.pathname === '/jaspercraft-video-core.js') {
      // Fixture-only copy: lets probes drop the render resolution to 10% (players keep the 35% floor).
      const core = fs.readFileSync(path.join(root, 'site/jaspercraft-video-core.js'), 'utf8').replace('ranges.resolution=[35,100,5]', 'ranges.resolution=[10,100,5]');
      res.setHeader('Content-Type', 'application/javascript; charset=utf-8'); res.setHeader('Cache-Control', 'no-store'); res.end(core); return;
    }
    if (url.pathname === '/log') { res.setHeader('Content-Type', 'text/plain; charset=utf-8'); res.end(tail.join('')); return; }
    const file = files[url.pathname];
    if (!file) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', url.pathname.endsWith('.js') ? 'application/javascript; charset=utf-8' : url.pathname.endsWith('.css') ? 'text/css' : 'application/octet-stream');
    res.setHeader('Cache-Control', 'no-store');
    fs.createReadStream(path.join(root, file)).pipe(res);
  });
  web.listen(webPort, '127.0.0.1');
  const log = fs.createWriteStream(path.join(fixture, 'paper.log'));
  child = spawn(path.join(java, 'java.exe'), ['-Xms128M', '-Xmx640M', '-XX:ActiveProcessorCount=1', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', '--nojline'], {cwd: server, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  // Yield the CPU to the owner and other work on this PC.
  try { os.setPriority(child.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  let saving = true;
  const output = data => { log.write(data); tail.push(data.toString()); if (tail.length > 400) tail.splice(0, tail.length - 400);
    // Throwaway world: no chunk saving at all, so a busy disk cannot stall the test server.
    if (saving && /Done \(/.test(data.toString())) { saving = false; child.stdin.write('save-off\n'); }
    if (/TANK|Done \(|ERROR|SEVERE|logged in|Exception/.test(data.toString())) process.stdout.write(data); };
  child.stdout.on('data', output); child.stderr.on('data', output);
  process.stdin.on('data', data => child.stdin.write(data));
  let stopping = false;
  function stop() { if (stopping) return; stopping = true; child.stdin.write('stop\n'); setTimeout(() => { if (child.exitCode === null) child.kill(); }, 20000).unref(); }
  process.on('SIGINT', stop); process.on('SIGTERM', stop);
  const timeout = setTimeout(stop, 90 * 60 * 1000);
  child.on('exit', () => { clearTimeout(timeout); web.close(); log.end(); process.exit(); });
  process.on('exit', () => { if (child.exitCode === null) child.kill(); });
  write('fixture.json', JSON.stringify({fixture, webPort, socketPort, pid: child.pid}, null, 2));
  console.log('Preview URL: http://127.0.0.1:' + webPort + '/');
  console.log('Fixture: ' + fixture);
})();
