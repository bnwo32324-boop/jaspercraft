'use strict';
/* Big Mobs in the real client (owner 2026-10-05: dungeon bosses scaled up "literally ... a larger size and ... a larger hitbox"): one
 * headless Chrome at below-normal priority against the loopback fixture (scripts/tank-preview.cjs, a lean Paper server) with the candidate
 * client (node scripts/assemble-dungeon7-client.cjs). A stand-in plugin plays the dungeon's part: "bigscale <player> <hundredths>" sends
 * the jaspr:scale table for the nearest zombie, exactly as JasprDungeon's Bodies does ("id:hundredths"), and "bigscale <player> clear"
 * sends the empty table. It checks:
 *   - the client keeps the table, grows the zombie's client hitbox to 3x (width 0.6 -> 1.8, height 1.95 -> 5.85) and draws it 3x
 *     (the zombie covers several times more of the screen than before);
 *   - the empty table gives the zombie its own size back;
 *   - garbage entries are refused and counted; no page exception, and the client keeps rendering.
 * Screenshots go to the out folder.
 *   node tests/big-mobs-browser.cjs [out-dir]
 * Needs candidate/dungeon7-deploy/{classes.js,assets.epk}, candidate/browser-fixture/jaspr-paper-wide.jar and the fixture's own files.
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const DEPLOY = path.resolve(process.env.BIG_MOBS_DEPLOY || path.join(ROOT, 'candidate', 'dungeon7-deploy'));
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'big-mobs-browser'));
const NAME = 'BigTester', W = 960, H = 540;
const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 700) : ''));
}
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));

function decodePng(buf) {
  let at = 8, width = 0, height = 0, type = 0, idat = [];
  while (at < buf.length) {
    const len = buf.readUInt32BE(at), name = buf.toString('latin1', at + 4, at + 8), data = buf.subarray(at + 8, at + 8 + len);
    if (name === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); type = data[9]; }
    if (name === 'IDAT') idat.push(data);
    at += 12 + len;
  }
  const bpp = type === 6 ? 4 : 3, stride = width * bpp, raw = zlib.inflateSync(Buffer.concat(idat)), px = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? px[y * stride + x - bpp] : 0, b = y ? px[(y - 1) * stride + x] : 0, c = x >= bpp && y ? px[(y - 1) * stride + x - bpp] : 0;
      let v = line[x];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      px[y * stride + x] = v & 255;
    }
  }
  return {width, height, rgb: (x, y) => [px[y * stride + x * bpp], px[y * stride + x * bpp + 1], px[y * stride + x * bpp + 2]]};
}
/** Pixels of the frame that differ from the sky-and-grass scene without the zombie (the zombie's skin and clothes): a rough area. */
function zombieArea(img) {
  let n = 0;
  for (let y = 0; y < img.height; y += 2) for (let x = 0; x < img.width; x += 2) {
    const [r, g, b] = img.rgb(x, y);
    // zombie skin (green), shirt (cyan) and trousers (blue/purple), not the sky (light blue) nor the grass (olive)
    const skin = g > 90 && g > r * 1.25 && g > b * 1.2 && r < 120, shirt = b > 120 && g > 110 && r < 60, legs = b > 100 && r > 50 && r < 120 && g < 90;
    if (skin || shirt || legs) n++;
  }
  return n;
}

/** The stand-in for JasprDungeon's Bodies: "bigscale <player> <hundredths|clear|garbage>" sends jaspr:scale for the nearest zombie. */
function buildScaleProbe() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-bigmobs-probe-src-'));
  try {
    const classes = path.join(dir, 'classes'), src = path.join(dir, 'src');
    fs.mkdirSync(classes); fs.mkdirSync(src);
    fs.writeFileSync(path.join(src, 'ScaleProbe.java'), [
      'package chat.jaspr.bigmobsprobe;',
      'public final class ScaleProbe extends org.bukkit.plugin.java.JavaPlugin {',
      '  @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {',
      '    org.bukkit.entity.Player p = args.length > 1 ? getServer().getPlayerExact(args[0]) : null;',
      '    if (p == null) return false;',
      '    org.bukkit.entity.Entity best = null; double far = Double.MAX_VALUE;',
      '    for (org.bukkit.entity.Entity e : p.getWorld().getEntitiesByClass(org.bukkit.entity.Zombie.class)) { double d = e.getLocation().distanceSquared(p.getLocation()); if (d < far) { far = d; best = e; } }',
      '    String text = args[1].equals("clear") ? "" : args[1].equals("garbage") ? "x:1,5:9999,-3:100,abc,7" : (best == null ? "" : best.getEntityId() + ":" + Integer.parseInt(args[1]));',
      '    io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();',
      '    new net.minecraft.server.v1_12_R1.PacketDataSerializer(buf).a(text);',
      '    ((org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) p).getHandle().playerConnection.sendPacket(new net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload("jaspr:scale", new net.minecraft.server.v1_12_R1.PacketDataSerializer(buf)));',
      '    getLogger().info("SCALE_SENT text=" + text + " zombie=" + (best == null ? -1 : best.getEntityId()));',
      '    return true;',
      '  }',
      '}', ''].join('\n'));
    const run = (tool, args) => {
      const r = spawnSync(path.join(JDK, tool + '.exe'), ['-J-XX:ActiveProcessorCount=1', ...args], {encoding: 'utf8', windowsHide: true, cwd: dir});
      if (r.status !== 0) throw new Error(tool + ' failed: ' + (r.stdout + r.stderr).slice(0, 2000));
    };
    run('javac', ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-cp', path.join(ROOT, 'server', 'cache', 'patched_1.12.2.jar'), '-d', classes, path.join(src, 'ScaleProbe.java')]);
    fs.writeFileSync(path.join(classes, 'plugin.yml'), 'name: ScaleProbe\nversion: 1\nmain: chat.jaspr.bigmobsprobe.ScaleProbe\ncommands:\n  bigscale:\n    description: sends jaspr:scale\n    usage: /bigscale <player> <hundredths|clear|garbage>\n');
    const jar = path.join(out, 'ScaleProbe.jar');
    run('jar', ['--create', '--file', jar, '--no-manifest', '-C', classes, '.']);
    return jar;
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
}

for (const f of ['classes.js', 'assets.epk']) if (!fs.existsSync(path.join(DEPLOY, f))) throw new Error('missing candidate client file: ' + path.join(DEPLOY, f));
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(DEPLOY, 'classes.js'), 'latin1');
const ANCHOR = '$rt_globals.JasprWideBridge = {';
if (candidate.split(ANCHOR).length !== 2 || !candidate.includes('/* JASPR_BIGMOBS_BEGIN */')) throw new Error('the candidate client lacks the Big Mobs stage');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace(ANCHOR, '$rt_globals.__mc = function () { return HEH; }; ' + ANCHOR), 'latin1'));

function stopAll() {
  if (profile) {
    const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
    const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in $ps){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 400 }";
    try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
    try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { }
  }
  if (fixture && fixture.exitCode === null) { try { fixture.stdin.write('stop\n'); } catch (e) { } }
}

(async () => {
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(DEPLOY, 'assets.epk'),
      TANK_PREVIEW_PLUGINS: path.relative(ROOT, buildScaleProbe()).split(path.sep).join('/'), TANK_PREVIEW_COPY: 'candidate/browser-fixture/jaspr-paper-wide.jar=>paper.jar'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2500));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };
  const serverLog = () => getText(webPort, '/log').catch(() => '');

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-bigmobs-probe-'));
  chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
    '--no-first-run', '--no-default-browser-check', '--mute-audio', '--disable-extensions', '--disable-background-networking', '--disable-sync',
    '--disable-default-apps', '--disable-component-update', '--renderer-process-limit=1', '--disable-features=Translate,MediaRouter,OptimizationHints',
    '--window-size=' + W + ',' + H, '--force-device-scale-factor=1', '--remote-debugging-port=' + debugPort, '--user-data-dir=' + profile, 'about:blank'],
    {windowsHide: true, stdio: 'ignore'});
  try { os.setPriority(chrome.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  let target;
  for (let i = 0; i < 100 && !target; i++) { try { target = JSON.parse(await getText(debugPort, '/json/list')).find(t => t.type === 'page'); } catch (e) { } if (!target) await sleep(100); }
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let seq = 0; const pending = new Map(), consoleLog = [];
  ws.addEventListener('message', ev => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    if (m.method === 'Runtime.consoleAPICalled') consoleLog.push(m.params.type + ': ' + m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300));
    if (m.method === 'Runtime.exceptionThrown') consoleLog.push('EXCEPTION: ' + JSON.stringify(m.params.exceptionDetails).slice(0, 600));
  });
  await new Promise(r => ws.addEventListener('open', r, {once: true}));
  const send = (method, params = {}) => new Promise(r => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({id, method, params})); });
  const evaluate = async expr => { const r = await send('Runtime.evaluate', {expression: expr, returnByValue: true, awaitPromise: true}); return r.result && r.result.result ? r.result.result.value : undefined; };
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width: W, height: H, deviceScaleFactor: 1, mobile: false, screenOrientation: {type: 'landscapePrimary', angle: 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;document.dispatchEvent(new Event('pointerlockchange'));};Document.prototype.exitPointerLock=function(){locked=null;document.dispatchEvent(new Event('pointerlockchange'));};})();"});
  const video = {version: 1, tier: -1, auto: false, values: {renderDistance: 2, maxFps: 15, fancyGraphics: 0, ao: 0, particles: 2, clouds: 0, entityShadows: 0, vsync: 0,
    mipmaps: 0, chunkUpdates: 1, fog: 1, viewBobbing: 0, resolution: 100, chunkBudget: 1, entityDistance: 32, fastVisibility: 1, animations: 0, ambientEffects: 0,
    weatherEffects: 0, particleEffects: 0, dynamicLights: 0, gore: 0, shader: 0, dhEnabled: 0, showFps: 0, showCoords: 0, mobileControls: 1, touchSensitivity: 100}};
  await send('Page.addScriptToEvaluateOnNewDocument', {source: 'try{localStorage.setItem("jaspr.video.v1",' + JSON.stringify(JSON.stringify(video)) + ');}catch(e){}'});
  await send('Page.navigate', {url: url + '#' + NAME});
  const shot = async name => {
    const r = await Promise.race([send('Page.captureScreenshot', {format: 'png'}), sleep(20000).then(() => null)]);
    if (!r || !r.result) { console.log('shot ' + name + ' TIMEOUT'); return null; }
    const bytes = Buffer.from(r.result.data, 'base64');
    fs.writeFileSync(path.join(out, name + '.png'), bytes);
    console.log('shot ' + name);
    return decodePng(bytes);
  };
  const status = () => evaluate('JSON.stringify(window.JasprBigMobsDiagnostics ? JasprBigMobsDiagnostics.status() : null)').then(t => (t ? JSON.parse(t) : null));

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(180000), 'client joins the fixture server');
    await sleep(3000);
    let st = await status();
    check(st && st.entries === 0 && st.failures === 0, 'the Big Mobs module is in the client, its table empty', st);
    await cmd('gamemode 1 ' + NAME);
    await cmd('time set 6000');
    await cmd('gamerule doMobSpawning false');
    // the player faces south (+z); a still zombie six blocks ahead, a stone button on its head so the noon sun does not set it alight
    // (its flames would hide the colours the area count looks for)
    await cmd('tp ' + NAME + ' ~ ~ ~ 0 10');
    await sleep(1200);
    await cmd('execute ' + NAME + ' ~ ~ ~ summon zombie ~ ~ ~6 {NoAI:1b,PersistenceRequired:1b,Silent:1b,ArmorItems:[{},{},{},{id:"minecraft:stone_button",Count:1b}]}');
    await sleep(4000);
    const a = await shot('01-normal');
    const areaA = a ? zombieArea(a) : 0;
    let log = await cmd('bigscale ' + NAME + ' 300');
    check(/SCALE_SENT text=\d+:300 zombie=\d+/.test(log + await serverLog()), 'the stand-in sends the zombie\'s factor (3.00) on jaspr:scale');
    await sleep(2500);
    st = await status();
    check(st && st.entries === 1 && st.grown >= 1 && st.draws > 0, 'the client keeps the table and grows the zombie', st);
    const body = st && st.changed.length ? st.changed[st.changed.length - 1] : null;
    check(body && body.scale === 3 && Math.abs(body.width - 1.8) < .02 && Math.abs(body.height - 5.85) < .03, 'its client hitbox is 3x (0.6 x 1.95 -> 1.8 x 5.85)', body);
    const b = await shot('02-scaled');
    const areaB = b ? zombieArea(b) : 0;
    check(areaA > 20 && areaB > 4 * areaA, 'it is drawn 3x: it covers several times more of the screen', {before: areaA, after: areaB});
    await cmd('bigscale ' + NAME + ' garbage');
    await sleep(1500);
    st = await status();
    check(st && st.rejected >= 4 && st.failures === 0, 'garbage entries are refused and counted', st);
    await cmd('bigscale ' + NAME + ' clear');
    await sleep(2500);
    st = await status();
    const back = st && st.changed.length ? st.changed[st.changed.length - 1] : null;
    check(st && st.entries === 0 && st.restored >= 1 && back && back.scale === 1 && Math.abs(back.width - .6) < .02, 'the empty table gives the zombie its own size back', st);
    const c = await shot('03-restored');
    const areaC = c ? zombieArea(c) : 0;
    check(areaC < areaB / 3, 'and it is drawn at its own size again', {scaled: areaB, restored: areaC});
    check(await evaluate('(function(){try{return !!(__mc()&&__mc().v);}catch(e){return false;}})()'), 'the client keeps running');
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    check(!/Exception/.test((await serverLog()).replace(/javax\.imageio\.IIOException: Can't read input file!/g, '')), 'no server exceptions');
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'BIG_MOBS_BROWSER_FAIL ' : 'BIG_MOBS_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('BIG_MOBS_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('BIG_MOBS_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 10 * 60 * 1000).unref();
