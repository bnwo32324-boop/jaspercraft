'use strict';
/* The Field Journal in the real client, end to end (owner 2026-10-07: "event forecast, character summary, active item effects" in
 * the empty space of the wide inventory): a real Paper server with the wide-inventory jar and the real Journal, RPG, Disasters and
 * Invasions plugins (plus a stand-in JasprApocalypse that carries the siege's real SiegeRules class, so the Blood Moon row is
 * the real rule), one headless Chrome at below-normal priority, the candidate client with the journal stage. It checks:
 *   - the server saw the client's hello, and the panel's data arrived over the plugin channel;
 *   - the panel is drawn in the empty space of the survival inventory, inside the widened window, clear of every slot;
 *   - the three tabs show what the server sent (and the text is not cut off), a silent effect appears under Perks with a countdown;
 *   - real mouse (or touch) input: a tap on a tab switches it, a tap on Open Stats makes the server open the stat sheet;
 *   - nothing is drawn in a chest window, and there are no page exceptions or panel errors.
 * With --mobile it runs as a phone (touch emulation and the phone user agent) in short landscape (740x360), then in portrait
 * (390x780), where the panel must still fit on screen and respond to taps. Screenshots go to the out folder.
 *
 *   node tests/journal-browser.cjs [out-dir] [--mobile]
 * Needs candidate/deploy/classes.js (the client with the journal stage: node scripts/assemble-journal-client.cjs) and assets.epk,
 * server/plugins/JasprJournal.jar and candidate/jars/{JasprRPG,JasprDisasters}.jar (scripts/build-journal-plugin.cjs and
 * scripts/patch-plugin-jars.cjs), candidate/browser-fixture/{jaspr-paper-wide.jar,TrinketProbe.jar}, candidate/tanks/JasprTanks.jar and
 * candidate/tank-client/ (borrowed by the fixture).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const mobile = process.argv.includes('--mobile');
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', mobile ? 'journal-browser-mobile' : 'journal-browser'));
const NAME = 'WideTester';
let W = mobile ? 740 : 960, H = mobile ? 360 : 540;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 600) : ''));
}
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));
const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';

/** A minimal PNG reader (8-bit truecolour, with or without alpha): {width, height, rgb(x, y)}. */
function decodePng(buf) {
  let at = 8, width = 0, height = 0, type = 0, idat = [];
  while (at < buf.length) {
    const len = buf.readUInt32BE(at), name = buf.toString('latin1', at + 4, at + 8), data = buf.subarray(at + 8, at + 8 + len);
    if (name === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); type = data[9]; if (data[8] !== 8 || data[12] !== 0) throw new Error('unsupported PNG'); }
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

/** The stand-in JasprApocalypse: the siege's real SiegeRules class with the shipped configuration, nothing else (no AuthMe needed). */
function buildStandIn() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-journal-standin-'));
  try {
    const classes = path.join(dir, 'classes'), src = path.join(dir, 'src');
    fs.mkdirSync(classes); fs.mkdirSync(src);
    fs.writeFileSync(path.join(src, 'ApocalypseStandIn.java'), 'package chat.jaspr.apocalypse;\npublic final class ApocalypseStandIn extends org.bukkit.plugin.java.JavaPlugin {\n  @Override public void onEnable() { saveDefaultConfig(); }\n}\n');
    const run = (tool, args) => {
      const r = spawnSync(path.join(JDK, tool + '.exe'), ['-J-XX:ActiveProcessorCount=1', ...args], {encoding: 'utf8', windowsHide: true, cwd: dir});
      if (r.status !== 0) throw new Error(tool + ' failed: ' + (r.stdout + r.stderr).slice(0, 2000));
    };
    run('javac', ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-cp', path.join(ROOT, 'server', 'cache', 'patched_1.12.2.jar'), '-d', classes,
      path.join(ROOT, 'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/SiegeRules.java'), path.join(src, 'ApocalypseStandIn.java')]);
    fs.writeFileSync(path.join(classes, 'plugin.yml'), 'name: JasprApocalypse\nversion: 0-standin\nmain: chat.jaspr.apocalypse.ApocalypseStandIn\n');
    fs.writeFileSync(path.join(classes, 'config.yml'), 'siege:\n  blood-moon-every-nights: 3\n');
    const jar = path.join(ROOT, 'candidate', 'browser-fixture', 'JasprApocalypse-standin.jar');
    run('jar', ['--create', '--file', jar, '--no-manifest', '-C', classes, '.']);
    return jar;
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
}

for (const f of ['candidate/deploy/classes.js', 'candidate/deploy/assets.epk', 'server/plugins/JasprJournal.jar', 'candidate/jars/JasprRPG.jar', 'candidate/jars/JasprDisasters.jar',
  'candidate/browser-fixture/jaspr-paper-wide.jar', 'candidate/browser-fixture/TrinketProbe.jar', 'server/plugins/JasprInvasions.jar']) {
  if (!fs.existsSync(path.join(ROOT, f))) throw new Error('missing fixture input: ' + f);
}
// A test copy of the candidate client that lets the probe reach the Minecraft instance and the panel.
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(ROOT, 'candidate', 'deploy', 'classes.js'), 'latin1');
if (!candidate.includes('$rt_globals.JasprWideBridge = {') || !candidate.includes('JASPR_JOURNAL_BEGIN')) throw new Error('candidate lacks the wide inventory or the journal stage');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace('$rt_globals.JasprWideBridge = {',
  '$rt_globals.__wideMc = function () { return HEH; }; $rt_globals.__journal = function () { return JasprJournal; };' +
  ' $rt_globals.__isPlayerInventory = function () { var g = HEH && HEH.cj; return !!g && g.h2 instanceof A2Z; };' +
  ' $rt_globals.__plan = function () { var g = HEH && HEH.cj, p = g ? JasprJournal.plan(g) : null; return p ? {texts: p.texts.map(function (t) { return {s: $rt_ustr(t.s), x: t.x, y: t.y}; }, this), hits: p.hits, rects: p.rects} : null; };' +
  ' $rt_globals.__width = function (s) { return CA(HEH.cj.J, $rt_str(s)); };' +
  ' $rt_globals.JasprWideBridge = {'), 'latin1'));

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
  const standIn = buildStandIn();
  const plugins = ['server/plugins/JasprJournal.jar', 'candidate/jars/JasprRPG.jar', 'candidate/jars/JasprDisasters.jar', 'server/plugins/JasprInvasions.jar', path.relative(ROOT, standIn)].join(',');
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(ROOT, 'candidate', 'deploy', 'assets.epk'), TANK_PREVIEW_PLUGINS: plugins,
      TANK_PREVIEW_COPY: 'candidate/browser-fixture/jaspr-paper-wide.jar=>paper.jar;candidate/browser-fixture/TrinketProbe.jar=>plugins/TrinketProbe.jar'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2500));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  check(!/VerifyError|NoSuchFieldError|NoSuchMethodError/.test(fxOut), 'patched server classes load');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-journal-probe-'));
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
  if (mobile) {
    await send('Emulation.setUserAgentOverride', {userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36', platform: 'Android'});
    await send('Emulation.setTouchEmulationEnabled', {enabled: true, maxTouchPoints: 5});
  }
  const metrics = async (w, h) => { W = w; H = h; await send('Emulation.setDeviceMetricsOverride', {width: w, height: h, deviceScaleFactor: 1, mobile, screenOrientation: {type: w > h ? 'landscapePrimary' : 'portraitPrimary', angle: w > h ? 90 : 0}}); };
  await metrics(W, H);
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
  const key = async (code, keyCode, k) => {
    await send('Input.dispatchKeyEvent', {type: 'keyDown', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode}); await sleep(90);
    await send('Input.dispatchKeyEvent', {type: 'keyUp', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode}); await sleep(500);
  };
  const pressAt = async (x, y) => {
    if (mobile) {
      await send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [{x, y, id: 2}]}); await sleep(90);
      await send('Input.dispatchTouchEvent', {type: 'touchEnd', touchPoints: []}); await sleep(600);
    } else {
      await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x, y}); await sleep(60);
      await send('Input.dispatchMouseEvent', {type: 'mousePressed', x, y, button: 'left', clickCount: 1}); await sleep(80);
      await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x, y, button: 'left', clickCount: 1}); await sleep(600);
    }
  };
  /** The open window: guiLeft/Top, widths and the CSS pixels per GUI pixel. */
  const screen = () => evaluate(`(function(){try{var m=__wideMc(),g=m.cj;if(!g||!g.h2)return null;
    var rw=(g.q|0)+(g.$jwCut|0);return {is:g.is,l7:g.l7,q:g.q,gv:g.gv,gx:g.gx,k:document.querySelector('#game_frame canvas').getBoundingClientRect().width/rw,
    cw:document.querySelector('#game_frame canvas').getBoundingClientRect().width,ch:document.querySelector('#game_frame canvas').getBoundingClientRect().height,inv:__isPlayerInventory()};}catch(e){return {error:String(e)};}})()`);
  const plan = () => evaluate('JSON.stringify(__plan())').then(t => (t ? JSON.parse(t) : null));
  const status = () => evaluate('JSON.stringify(__journal().status())').then(t => (t ? JSON.parse(t) : null));
  const flat = p => p ? p.texts.map(t => t.s).join('|') : '';
  const css = (sc, x, y) => [Math.round(x * sc.k), Math.round(y * sc.k)];

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__wideMc&&__wideMc()&&__wideMc().v);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(150000), 'client joins the fixture server');
    await sleep(3000);
    await cmd('gamemode 0 ' + NAME);
    await cmd('time set 0');
    await sleep(2500);
    const log = await getText(webPort, '/log').catch(() => '');
    check(/JOURNAL_READY version=1\.0\.1 channel=jaspr:journal/.test(log + fxOut), 'the Journal plugin is up');
    check(/JOURNAL_CLIENT_HELLO clients=1/.test(log + fxOut), 'the server received the client\'s hello on jaspr:journal');
    let st = await status();
    check(st && st.hasData && st.fresh && st.stats.packets >= 1 && st.stats.hellos === 1, 'the panel data arrived over the plugin channel', st && st.stats);

    // ---- the survival inventory ------------------------------------------------------------------------------------
    await key('KeyE', 69, 'e'); await sleep(1500);
    let sc = await screen();
    check(sc && !sc.error && sc.inv, 'the survival inventory is open', sc);
    let p = await plan();
    check(p && p.hits, 'the panel has a plan for the open window');
    console.log('canvas ' + sc.cw + 'x' + sc.ch + ', ' + sc.k.toFixed(2) + ' css px per GUI px');
    const img0 = await shot('soon');
    const fr = p.hits.frame;
    // Geometry: inside the widened window, in the gap beside the crafting grid, clear of the slot rows, on screen.
    const L = sc.is, T = sc.l7, S = sc.gv;
    check(fr[0] >= L + S && fr[2] <= L + 266 - 7 && fr[1] >= T + 3 && fr[3] <= T + 83, 'the panel sits in the widened window\'s empty space, above the inventory rows', {frame: fr, L, T});
    const cssFrame = [fr[0] * sc.k, fr[1] * sc.k, fr[2] * sc.k, fr[3] * sc.k];
    check(cssFrame[0] >= 0 && cssFrame[1] >= 0 && cssFrame[2] <= sc.cw && cssFrame[3] <= sc.ch, 'and entirely on screen', cssFrame.map(v => Math.round(v)));
    // Pixels: a dark screen with bright text in it, a light slot-style edge below and to the right.
    if (img0) {
      const px = (gx, gy) => img0.rgb(Math.round(gx * sc.k), Math.round(gy * sc.k));
      const dark = c => c[0] < 60 && c[1] < 60 && c[2] < 60;
      const inner = px(fr[0] + 4, fr[1] + 40), corner = px(fr[2] - 3, fr[1] + 40);
      check(dark(inner) && dark(corner), 'the screen is drawn dark inside the frame', {inner, corner});
      let bright = 0, total = 0;
      for (let gy = fr[1] + 14; gy < fr[3] - 2; gy += 1) for (let gx = fr[0] + 2; gx < fr[2] - 2; gx += 1) { const c = px(gx, gy); total++; if (c[0] > 150 || c[1] > 150 || c[2] > 150) bright++; }
      check(bright > 40 && bright / total < 0.45, 'with text in it (not blank, not a wash)', {bright, total});
      // The window body just left of the panel is the JasperCraft teal (scripts/jasper-theme.cjs), drawn by the recoloured texture.
      const body = px(fr[0] - 3, fr[1] + 30), want = require('../scripts/jasper-theme.cjs').rgb(require('../scripts/jasper-theme.cjs').JASPER.body);
      check(body.every((v, i) => Math.abs(v - want[i]) <= 12), 'the window body just left of the panel is the theme colour', {body, want});
      // A slot in the vanilla part (recoloured texture) and one in the widened part (the module's rectangles) are the same colour,
      // and the frame highlight (amber) runs along the top of the window and of the widened part alike.
      const th = require('../scripts/jasper-theme.cjs'), near = (c, hex) => c.every((v, i) => Math.abs(v - th.rgb(hex)[i]) <= 12);
      const slotA = px(L + 12, T + 146), slotB = px(L + 178, T + 146), hiA = px(L + 100, T + 1.5), hiB = px(L + 220, T + 1.5);
      check(near(slotA, th.JASPER.slot) && near(slotB, th.JASPER.slot), 'slots are the theme colour in the window and in its widened part', {slotA, slotB});
      check(near(hiA, th.JASPER.frameHi) && near(hiB, th.JASPER.frameHi), 'the amber frame highlight runs along the top of both parts', {hiA, hiB});
    }
    // Content from the server.
    let t = flat(p);
    check(/Soon\|You\|Perks|S\|Y\|P|Soon\|Me\|Fx/.test(t), 'three tabs are labelled', t.slice(0, 40));
    check(/Blood Moon/.test(t) && /Invasion/.test(t) && /Disaster/.test(t), 'the Soon tab shows the Blood Moon, the invasion mark and the disaster hint', t);
    check(/in 2 nights|tonight|tomorrow/.test(t), 'the Blood Moon row counts nights (time set to morning of day 0: two nights to the Blood Moon)', t);
    check(/Day 0 \|Morning/.test(t), 'the footer names the day and the time of day', t);
    for (const tx of p.texts) {
      const [x0, x1] = [fr[0] + 1, fr[2] - 1];
      const w = await evaluate('__width(' + JSON.stringify(tx.s) + ')');
      check(tx.x >= x0 && tx.x + w <= x1, `"${tx.s}" is inside the frame (${w}px at ${tx.x})`);
    }

    // ---- real input: tabs -------------------------------------------------------------------------------------------
    await cmd('trkprobe silent ' + NAME);                 // a silent effect: Water Breathing, ambient, no particles
    await sleep(2500);
    const tabBox = i => { const b = p.hits.tabs[i]; return css(sc, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2); };
    await pressAt(...tabBox(1));
    st = await status();
    check(st.tab === 'you', 'a tap on the "You" tab switches to it', st.tab);
    p = await plan(); t = flat(p);
    check(/Level/.test(t) && /ranks/.test(t) && /stats/.test(t) && /Open Stats/.test(t), 'the You tab shows level, ranks, stats and the button', t);
    await shot('you');
    await pressAt(...tabBox(2));
    p = await plan(); t = flat(p);
    st = await status();
    check(st.tab === 'perks' && /Water/.test(t) && /Breathing/.test(t), 'the Perks tab lists the silent effect (and the inventory still draws no box for it)', t);
    check(/\d+:\d\d/.test(t), 'with its countdown', t);
    await shot('perks');
    // A tap on the panel body flips on to the next tab (after the last page): perks has one page here.
    const body = p.hits.body;
    await pressAt(...css(sc, body[0] + 12, body[1] + 12));
    st = await status();
    check(st.tab === 'soon', 'a tap on the body flips to the next tab (round to the first)', st.tab);
    check((await evaluate('localStorage.getItem("jaspr.journal.tab.v1")')) === 'soon', 'and the choice is remembered on this device');
    // The button: tap You, then Open Stats: the server's stat sheet opens (a chest-like window replaces the inventory).
    p = await plan();
    await pressAt(...tabBox(1));
    p = await plan();
    const hit = p.hits.button.hit;
    await pressAt(...css(sc, (hit[0] + hit[2]) / 2, (hit[1] + hit[3]) / 2));
    await sleep(1800);
    const afterStats = await screen();
    check(afterStats && !afterStats.inv && afterStats.error === undefined, 'Open Stats made the server open the stat sheet (the inventory window was replaced)', afterStats && {inv: afterStats.inv});
    const chestPlan = await plan();
    check(chestPlan === null, 'no panel is drawn in another window');
    await shot('stats-open');
    await key('Escape', 27, 'Escape'); await sleep(800);

    // ---- more data: the Blood Moon, an invasion mark, the stat sheet -------------------------------------------------
    await cmd('time set 12000');
    await sleep(2500);
    await key('KeyE', 69, 'e'); await sleep(1500);
    sc = await screen(); p = await plan();
    await pressAt(...css(sc, (p.hits.tabs[0][0] + p.hits.tabs[0][2]) / 2, (p.hits.tabs[0][1] + p.hits.tabs[0][3]) / 2));
    p = await plan(); t = flat(p);
    check(/Dusk/.test(t), 'dusk is shown as the phase', t);
    await shot('soon-dusk');

    const errors = consoleLog.filter(l => /EXCEPTION/.test(l)), warnings = consoleLog.filter(l => /JasperCraft journal/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    check(warnings.length === 0, 'no panel warnings in the console', warnings.slice(0, 3));
    st = await status();
    check(st.stats.errors === 0 && st.stats.rejected === 0, 'the panel counted no errors and rejected no packets', st.stats);
    await key('Escape', 27, 'Escape');

    // ---- phones: the same panel in portrait ---------------------------------------------------------------------------
    if (mobile) {
      await metrics(390, 780);
      await sleep(1500);
      await key('KeyE', 69, 'e'); await sleep(1500);
      sc = await screen(); p = await plan();
      check(sc && sc.inv && p && p.hits, 'portrait phone: the inventory opens with the panel');
      if (p) {
        const f2 = p.hits.frame, cf = [f2[0] * sc.k, f2[1] * sc.k, f2[2] * sc.k, f2[3] * sc.k];
        check(cf[0] >= 0 && cf[2] <= sc.cw && cf[3] <= sc.ch, 'the panel fits the narrow screen', cf.map(v => Math.round(v)));
        const tapH = (p.hits.tabs[0][3] - p.hits.tabs[0][1]) * sc.k, bodyH = (p.hits.body[3] - p.hits.body[1]) * sc.k;
        console.log('portrait: ' + sc.k.toFixed(2) + ' px per GUI px; tab target ' + tapH.toFixed(0) + 'px high, panel body ' + bodyH.toFixed(0) + 'px');
        await shot('portrait');
        const before = (await status()).tab;
        await pressAt(...css(sc, p.hits.body[0] + 10, p.hits.body[1] + 10));
        const s3 = await status();
        check(s3.tab !== before, 'a tap on the panel responds in portrait too', {before, after: s3.tab});
      }
      await key('Escape', 27, 'Escape');
    }
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'server.txt'), await getText(webPort, '/log').catch(() => ''));
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'JOURNAL_BROWSER_FAIL ' : 'JOURNAL_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('JOURNAL_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('JOURNAL_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 10 * 60 * 1000).unref();
