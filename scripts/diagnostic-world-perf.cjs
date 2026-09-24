'use strict';

// Disposable loopback-only gameplay benchmark. It creates a temporary Paper /
// Eaglercraft world and a tiny fixture plugin that moves one scoreboard-fed
// light source. No production server files, worlds, or site assets are edited.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const net = require('node:net');
const crypto = require('node:crypto');
const { spawn, spawnSync, execFile } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const java = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const bundleName = process.argv[2] || 'site/classes.js';
const lightMode = (process.argv[3] === '0' || process.argv[3] === '2') ? process.argv[3] : '1';
const bundlePath = path.resolve(root, bundleName);
const padOverride = process.argv[4] && /^\d+$/.test(process.argv[4]) ? Number(process.argv[4]) : null;
let bundle = fs.readFileSync(bundlePath);
if (padOverride !== null) {
  const source = bundle.toString('utf8');
  const marker = 'var MAX_SOURCES = 48, PAD = 22;';
  if (!source.includes(marker)) throw new Error('dynamic-light invalidation marker not found in bundle');
  bundle = Buffer.from(source.replace(marker, 'var MAX_SOURCES = 48, PAD = ' + padOverride + ';'), 'utf8');
}
const assets = fs.readFileSync(path.join(root, 'site/assets.epk'));
const browser = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(fs.existsSync);
if (!browser) throw new Error('No Chromium browser found');

function run(exe, args, cwd) {
  const result = spawnSync(path.join(java, exe + '.exe'), args, { cwd, encoding: 'utf8', windowsHide: true });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || exe + ' failed');
}

function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      server.close(() => resolve(port));
    });
  });
}

function removeProfile(profile) {
  for (let attempt = 0; attempt < 12; attempt++) {
    try {
      fs.rmSync(profile, { recursive: true, force: true });
      return;
    } catch (error) {
      if (error.code !== 'EPERM' && error.code !== 'EBUSY') throw error;
    }
  }
  process.stderr.write('temporary browser profile cleanup deferred: ' + profile + '\n');
}

function getJson(port, pathname) {
  return new Promise((resolve, reject) => {
    const request = http.get({ host: '127.0.0.1', port, path: pathname }, response => {
      let data = '';
      response.setEncoding('utf8');
      response.on('data', chunk => data += chunk);
      response.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (error) { reject(error); }
      });
    });
    request.on('error', reject);
  });
}

async function waitTarget(port) {
  for (let attempt = 0; attempt < 120; attempt++) {
    try {
      const targets = await getJson(port, '/json/list');
      const page = targets.find(target => target.type === 'page');
      if (page) return page;
    } catch (_) { }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('diagnostic browser target did not start');
}

function connectCdp(webSocketUrl) {
  const socket = new WebSocket(webSocketUrl);
  let sequence = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const resolve = pending.get(message.id);
      pending.delete(message.id);
      resolve(message);
    }
  });
  const opened = new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  return {
    async call(method, params) {
      await opened;
      const id = ++sequence;
      socket.send(JSON.stringify({ id, method, params: params || {} }));
      return new Promise(resolve => pending.set(id, resolve));
    },
    close() { try { socket.close(); } catch (_) { } }
  };
}

async function evaluate(session, expression) {
  const response = await session.call('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true
  });
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text || 'Runtime evaluation failed');
  return response.result && response.result.result && response.result.result.value;
}

const fixture = path.join(root, 'candidate', 'diagnostic-world-perf-' + crypto.randomUUID());
const serverRoot = path.join(fixture, 'server');
const javaSource = path.join(fixture, 'JdlFixture.java');
const javaText = `
package chat.jaspr.perf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class JdlFixture extends JavaPlugin implements Listener {
    private final Map<UUID, BukkitTask> tasks = new HashMap<UUID, BukkitTask>();
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @Override public void onDisable() { for (BukkitTask task : tasks.values()) task.cancel(); tasks.clear(); }
    @EventHandler public void join(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        final Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        final Objective objective = board.registerNewObjective("jdl", "dummy");
        objective.setDisplayName("JDL v1 n=1");
        player.setScoreboard(board);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            private String previous;
            private int step;
            @Override public void run() {
                if (!player.isOnline()) return;
                int x = player.getLocation().getBlockX() + (step++ % 32);
                int y = player.getLocation().getBlockY() + 1;
                int z = player.getLocation().getBlockZ();
                String next = "#jdl." + x + "." + y + "." + z + ".15";
                if (previous != null) board.resetScores(previous);
                objective.getScore(next).setScore(0);
                previous = next;
                syncObjective(board);
            }
        }, 20L, 10L);
        tasks.put(player.getUniqueId(), task);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        BukkitTask task = tasks.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }
    private void syncObjective(Scoreboard view) {
        try {
            if (!(view instanceof org.bukkit.craftbukkit.v1_12_R1.scoreboard.CraftScoreboard)) return;
            net.minecraft.server.v1_12_R1.Scoreboard handle =
                ((org.bukkit.craftbukkit.v1_12_R1.scoreboard.CraftScoreboard) view).getHandle();
            if (!(handle instanceof net.minecraft.server.v1_12_R1.ScoreboardServer)) return;
            net.minecraft.server.v1_12_R1.ScoreboardObjective nms = handle.getObjective("jdl");
            if (nms != null) ((net.minecraft.server.v1_12_R1.ScoreboardServer) handle).e(nms);
        } catch (RuntimeException ignored) { }
    }
}
`;

async function main() {
  const socketPort = await freePort();
  const webPort = await freePort();
  fs.mkdirSync(path.join(serverRoot, 'plugins'), { recursive: true });
  fs.mkdirSync(path.join(fixture, 'classes'), { recursive: true });
  fs.writeFileSync(javaSource, javaText, 'utf8');
  run('javac', ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-cp', path.join(root, 'server/cache/patched_1.12.2.jar'), '-d', path.join(fixture, 'classes'), javaSource], fixture);
  fs.writeFileSync(path.join(fixture, 'classes', 'plugin.yml'),
    'name: JdlFixture\nmain: chat.jaspr.perf.JdlFixture\nversion: 1\n', 'utf8');
  run('jar', ['--create', '--file', path.join(serverRoot, 'plugins', 'JdlFixture.jar'), '-C', path.join(fixture, 'classes'), '.'], fixture);
  fs.copyFileSync(path.join(root, 'server/cache/patched_1.12.2.jar'), path.join(serverRoot, 'paper.jar'));
  for (const file of ['EaglerXServer.jar', 'EaglerXRewind.jar', 'ViaVersion.jar', 'ViaBackwards.jar', 'ViaRewind.jar', 'ViaRewind-Legacy-Support.jar'])
    fs.copyFileSync(path.join(root, 'server/plugins', file), path.join(serverRoot, 'plugins', file));
  fs.writeFileSync(path.join(serverRoot, 'eula.txt'), 'eula=true\n', 'utf8');
  fs.writeFileSync(path.join(serverRoot, 'server.properties'),
    'server-ip=127.0.0.1\nserver-port=' + socketPort + '\nonline-mode=false\nlevel-type=FLAT\n' +
    'generator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\n' +
    'level-name=world\nspawn-protection=0\nview-distance=4\ngenerate-structures=false\n' +
    'allow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\n' +
    'max-players=2\nnetwork-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\n', 'utf8');
  fs.writeFileSync(path.join(serverRoot, 'bukkit.yml'), 'settings:\n  allow-end: false\n', 'utf8');
  fs.writeFileSync(path.join(serverRoot, 'paper.yml'), 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n', 'utf8');
  fs.writeFileSync(path.join(serverRoot, 'spigot.yml'), 'config-version: 11\nsettings:\n  late-bind: true\n', 'utf8');
  fs.mkdirSync(path.join(serverRoot, 'plugins', 'bStats'), { recursive: true });
  fs.writeFileSync(path.join(serverRoot, 'plugins', 'bStats', 'config.yml'), 'enabled: false\n', 'utf8');
  fs.mkdirSync(path.join(serverRoot, 'plugins', 'EaglercraftXServer'), { recursive: true });
  fs.writeFileSync(path.join(serverRoot, 'plugins', 'EaglercraftXServer', 'listener.yml'),
    'dual_stack: true\nforward_ip: false\nforward_secret: false\ntls_config:\n  enable_tls: false\n  require_tls: false\nratelimit:\n  disable_ratelimit: [127.0.0.0/8]\n', 'utf8');
  fs.writeFileSync(path.join(serverRoot, 'plugins', 'EaglercraftXServer', 'settings.yml'),
    'server_name: JasperCraft performance fixture\nserver_uuid: "' + crypto.randomUUID() + '"\nprotocols:\n  max_minecraft_protocol: 340\n  eaglerxrewind_allowed: true\n  protocol_v3_allowed: true\n  protocol_v4_allowed: true\nvoice_service:\n  enable_voice_service: false\nupdate_checker:\n  enable_update_checker: false\nupdate_service:\n  enable_update_system: false\n  download_latest_certs: false\n', 'utf8');

  const html = `<!doctype html><html><head><meta charset="UTF-8"><style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:#000}#report{position:fixed;z-index:9;top:0;left:0;color:#fff;background:#000c;font:12px monospace;white-space:pre}</style><script>try{localStorage.setItem('jaspr.dl.mode','${lightMode}')}catch(_){}</script><script src="/classes.js"></script><script src="/jaspr-profile.js"></script></head><body><div id="game_frame"></div><output id="report">starting</output><script>
window.__perf={started:performance.now(),last:performance.now(),frames:0,maxGap:0,gaps50:0,gaps100:0,gaps250:0,gaps1000:0};
function sample(now){var p=window.__perf,g=now-p.last;p.frames++;if(g>p.maxGap)p.maxGap=g;if(g>50)p.gaps50++;if(g>100)p.gaps100++;if(g>250)p.gaps250++;if(g>1000)p.gaps1000++;p.last=now;requestAnimationFrame(sample)}requestAnimationFrame(sample);
function send(type,code,key){var e=new KeyboardEvent(type,{bubbles:true,cancelable:true,code:code,key:key,keyCode:87,which:87});window.dispatchEvent(e);document.dispatchEvent(e)}
function report(){var p=window.__perf,out={elapsedMs:performance.now()-p.started,frames:p.frames,maxGapMs:p.maxGap,gapsOver50:p.gaps50,gapsOver100:p.gaps100,gapsOver250:p.gaps250,gapsOver1000:p.gaps1000,canvas:document.querySelectorAll('canvas').length};for(var k of ['JasprShadersDiagnostics','JasprDynamicLightsDiagnostics','JasprWaypointDiagnostics','JasprGoreDiagnostics'])try{out[k]=window[k]?window[k].status():null}catch(e){out[k]='error:'+e.message}document.querySelector('#report').textContent=JSON.stringify(out)}
window.addEventListener('load',function(){setTimeout(function(){try{localStorage.setItem('jaspr.dl.mode','${lightMode}')}catch(_){ } JasprProfile.prepare('PerfProbe',localStorage).then(function(){try{window.eaglercraftXOpts={demoMode:false,container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_jaspr_world_perf_probe',worldsDB:'jaspr_world_perf_worlds',resourcePacksDB:'jaspr_world_perf_packs',servers:[{addr:'ws://127.0.0.1:${socketPort}/',name:'fixture'}],relays:[],joinServer:'ws://127.0.0.1:${socketPort}/'};main()}catch(e){document.querySelector('#report').textContent='main error: '+e.stack}})},0)});
</script></body></html>`;
  const web = http.createServer((req, res) => {
    const key = (req.url || '').split('?')[0];
    if (key === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    if (key === '/classes.js') { res.setHeader('Content-Type', 'application/javascript; charset=utf-8'); res.setHeader('Cache-Control', 'no-store'); res.end(bundle); return; }
    if (key === '/jaspr-profile.js') { res.setHeader('Content-Type', 'application/javascript; charset=utf-8'); res.end(fs.readFileSync(path.join(root, 'site/jaspr-profile.js'))); return; }
    if (key === '/assets.epk') { res.setHeader('Content-Type', 'application/octet-stream'); res.setHeader('Cache-Control', 'no-store'); res.end(assets); return; }
    res.writeHead(404); res.end();
  });
  await new Promise(resolve => web.listen(webPort, '127.0.0.1', resolve));
  const log = fs.createWriteStream(path.join(fixture, 'paper.log'));
  const child = spawn(path.join(java, 'java.exe'), ['-Xms128M', '-Xmx768M', '-XX:ActiveProcessorCount=2', '-Djava.awt.headless=true', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', '--nojline'], { cwd: serverRoot, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  child.stdout.on('data', data => log.write(data));
  child.stderr.on('data', data => log.write(data));
  let stopped = false;
  function stopServer() {
    if (stopped) return;
    stopped = true;
    try { child.stdin.write('stop\n'); } catch (_) { }
    setTimeout(() => { if (child.exitCode === null) child.kill(); }, 10000).unref();
  }
  const browserProfile = fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'jaspr-world-perf-'));
  const debugPort = await freePort();
  const args = [
    '--headless=new', '--no-sandbox', '--disable-gpu', '--enable-unsafe-swiftshader', '--use-angle=swiftshader',
    '--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows', '--disable-renderer-backgrounding',
    '--run-all-compositor-stages-before-draw', '--no-first-run', '--no-default-browser-check',
    '--window-size=1024,640', '--remote-debugging-port=' + debugPort,
    '--user-data-dir=' + browserProfile, 'http://127.0.0.1:' + webPort + '/'
  ];
  const browserChild = spawn(browser, args, { windowsHide: true, stdio: ['ignore', 'ignore', 'ignore'] });
  let session;
  try {
    const target = await waitTarget(debugPort);
    session = connectCdp(target.webSocketDebuggerUrl);
    await session.call('Runtime.enable');
    const readyStarted = Date.now();
    let ready;
    for (;;) {
      ready = await evaluate(session, `JSON.stringify({
        canvas: document.querySelectorAll('canvas').length,
        dynamic: window.JasprDynamicLightsDiagnostics ? window.JasprDynamicLightsDiagnostics.status() : null,
        shaders: window.JasprShadersDiagnostics ? window.JasprShadersDiagnostics.status() : null,
        title: document.title
      })`);
      let parsed;
      try { parsed = JSON.parse(ready || '{}'); } catch (_) { parsed = {}; }
    if (parsed.canvas > 0 && (!parsed.dynamic || parsed.dynamic.mode === 0 || parsed.dynamic.sources > 0)) { ready = parsed; break; }
      if (Date.now() - readyStarted > 45000) { ready = parsed; break; }
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    await new Promise(resolve => setTimeout(resolve, 3000));
    await evaluate(session, `window.__perf={started:performance.now(),last:performance.now(),frames:0,maxGap:0,gaps50:0,gaps100:0,gaps250:0,gaps1000:0}; true`);
    await session.call('Input.dispatchKeyEvent', { type: 'keyDown', key: 'w', code: 'KeyW', windowsVirtualKeyCode: 87, nativeVirtualKeyCode: 87 });
    await new Promise(resolve => setTimeout(resolve, 15000));
    await session.call('Input.dispatchKeyEvent', { type: 'keyUp', key: 'w', code: 'KeyW', windowsVirtualKeyCode: 87, nativeVirtualKeyCode: 87 });
    await new Promise(resolve => setTimeout(resolve, 1500));
    const report = await evaluate(session, `JSON.stringify((function(){
      var p=window.__perf||{};
      return {elapsedMs:performance.now()-p.started,frames:p.frames,maxGapMs:p.maxGap,gapsOver50:p.gaps50,gapsOver100:p.gaps100,gapsOver250:p.gaps250,gapsOver1000:p.gaps1000,ready:${JSON.stringify(ready)}};
    })())`);
    process.stdout.write(report + '\n');
  } finally {
    try { if (session) await session.call('Browser.close'); } catch (_) { }
    if (session) session.close();
    try { browserChild.kill(); } catch (_) { }
  }
  stopServer();
  await new Promise(resolve => child.once('exit', resolve));
  web.close();
  log.end();
  try {
    const serverLog = fs.readFileSync(path.join(fixture, 'paper.log'), 'utf8');
    const relevant = serverLog.split(/\r?\n/).filter(line => /Done \(|Eagler|logged in|lost connection|ERROR|SEVERE|Exception/.test(line)).slice(-40);
    if (relevant.length) process.stderr.write('fixture-server-log:\n' + relevant.join('\n') + '\n');
  } catch (_) { }
  removeProfile(browserProfile);
  fs.rmSync(fixture, { recursive: true, force: true });
}

main().catch(error => { process.stderr.write((error.stack || String(error)) + '\n'); process.exitCode = 1; });
