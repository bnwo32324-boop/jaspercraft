'use strict';
/* Silent effects and own icons in the real TeaVM client (owner 2026-10-05: the Pearl of the Drowned's "Water Breathing 0:03" box
 * covered the Easier Crafting search bar for good): one headless Chrome (below normal priority) against the loopback fixture
 * (scripts/tank-preview.cjs) with the wide-inventory server jar and the test-only TrinketProbe plugin. The survival inventory is
 * opened three times with the same items: with no effect, with a silent effect (ambient, no particles) and with a plain potion
 * effect. The effect box's region must look the same with a silent effect as with none, and different with the potion; a
 * skinned trinket must draw differently from the vanilla stone sword it rides on. Screenshots go to the out folder.
 *
 *   node tests/silent-effects-browser.cjs [out-dir]
 * Needs candidate/deploy/classes.js and assets.epk (node scripts/assemble-trinket-client.cjs) and candidate/browser-fixture/ (see FIXTURE below).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'silent-effects-browser'));
const NAME = 'WideTester', W = 960, H = 540;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 500) : ''));
}
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));

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
/** Mean absolute colour difference between two screenshots over a rectangle. */
function diff(a, b, x0, y0, w, h) {
  let sum = 0, n = 0;
  for (let y = Math.max(0, y0); y < Math.min(a.height, y0 + h); y++) for (let x = Math.max(0, x0); x < Math.min(a.width, x0 + w); x++) {
    const p = a.rgb(x, y), q = b.rgb(x, y); sum += Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]); n += 3;
  }
  return n ? sum / n : 0;
}

// A test copy of the candidate client that lets the probe reach the Minecraft instance.
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(ROOT, 'candidate', 'deploy', 'classes.js'), 'latin1');
if (!candidate.includes('$rt_globals.JasprWideBridge = {') || !candidate.includes('/*JF*/')) throw new Error('candidate lacks the wide inventory or silent-effects stage');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace('$rt_globals.JasprWideBridge = {', '$rt_globals.__wideMc = function () { return HEH; }; $rt_globals.JasprWideBridge = {'), 'latin1'));

function stopAll() {
  if (profile) {
    const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
    const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in $ps){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep 2 }";
    try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
    try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { }
  }
  if (fixture && fixture.exitCode === null) { try { fixture.stdin.write('stop\n'); } catch (e) { } }
}

(async () => {
  // FIXTURE: candidate/browser-fixture/{jaspr-paper-wide.jar, TrinketProbe.jar} next to the borrowed tank-preview files.
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(ROOT, 'candidate', 'deploy', 'assets.epk'),
      TANK_PREVIEW_COPY: 'candidate/browser-fixture/jaspr-paper-wide.jar=>paper.jar;candidate/browser-fixture/TrinketProbe.jar=>plugins/TrinketProbe.jar'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2000));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  check(!/VerifyError|NoSuchFieldError|NoSuchMethodError/.test(fxOut), 'patched server classes load');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-silentfx-probe-'));
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
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;document.dispatchEvent(new Event('pointerlockchange'));};document.exitPointerLock=function(){locked=null;document.dispatchEvent(new Event('pointerlockchange'));};})();"});
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
  /** The open screen: window position, CSS px per GUI px, the guiLeft shift flag and every slot. */
  const screen = () => evaluate(`(function(){try{var m=__wideMc(),g=m.cj;if(!g||!g.h2)return null;var l=g.h2.cn,s=[];
    for(var k=0;k<l.g;k++){var x=l.qN.data[k];s.push({n:x.pO,i:x.bQx,x:x.Lr,y:x.Fg,jw:x.$jw||0});}
    var rw=(g.q|0)+(g.$jwCut|0);return {is:g.is,l7:g.l7,q:g.q,clo:g.clo,gv:g.gv,k:document.querySelector('#game_frame canvas').getBoundingClientRect().width/rw,slots:s};}catch(e){return {error:String(e)};}})()`);

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__wideMc&&__wideMc()&&__wideMc().v);}catch(e){return false;}})()')) return true; await sleep(1000); } return false; };
    check(await inWorld(150000), 'client joins the fixture server');
    await sleep(3000);
    await cmd('gamemode 0 ' + NAME);
    // Slot 1: a vanilla stone sword. Slot 2: the same sword as a trinket carrier (band 43 = the Spore Pendant, unbreakable, hidden lines).
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.0 minecraft:stone_sword 1 0');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.1 minecraft:stone_sword 1 43 {Unbreakable:1b,HideFlags:63,display:{Name:"Spore Pendant"}}');
    await cmd('trkprobe clear ' + NAME);
    await sleep(1200);

    const openInventory = async label => {
      await key('KeyE', 69, 'e'); await sleep(1500);
      const sc = await screen();
      check(sc && sc.slots && sc.slots.length > 40, label + ': the inventory screen is open', sc && (sc.error || sc.slots.length));
      await sleep(500);
      const img = await shot(label);
      await key('Escape', 27, 'Escape');
      return {sc, img};
    };
    const none = await openInventory('no-effect');
    const box = sc => [Math.round((sc.is - 124) * sc.k), Math.round(sc.l7 * sc.k), Math.round(140 * sc.k), Math.round(32 * sc.k)];

    await cmd('trkprobe silent ' + NAME);
    await sleep(1500);
    const silent = await openInventory('silent-effect');
    await cmd('trkprobe clear ' + NAME);
    await cmd('trkprobe shown ' + NAME);
    await sleep(1500);
    const shown = await openInventory('potion-effect');
    await cmd('trkprobe clear ' + NAME);

    if (none.img && silent.img && shown.img) {
      const region = box(none.sc);
      const dSilent = diff(none.img, silent.img, ...region), dShown = diff(none.img, shown.img, ...region);
      check(dSilent < 2, 'a silent effect leaves the effect box area exactly as with no effect', {region, dSilent: +dSilent.toFixed(2)});
      check(dShown > 8, 'a plain potion effect does draw its box there', {dShown: +dShown.toFixed(2)});
      check(silent.sc.is === none.sc.is, 'a silent effect does not move the window', {none: none.sc.is, silent: silent.sc.is});
      // The two sword slots (item 0 and item 1 = window slots 36 and 37): the trinket draws differently from the vanilla sword.
      const slotBox = (sc, n) => { const s = sc.slots.find(x => x.n === n); return [Math.round((sc.is + s.x) * sc.k), Math.round((sc.l7 + s.y) * sc.k), Math.round(16 * sc.k), Math.round(16 * sc.k)]; };
      const a = slotBox(none.sc, 36), b = slotBox(none.sc, 37);
      let d = 0, n = 0;
      for (let y = 0; y < a[3]; y++) for (let x = 0; x < a[2]; x++) { const p = none.img.rgb(a[0] + x, a[1] + y), q = none.img.rgb(b[0] + x, b[1] + y); d += Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]); n += 3; }
      check(d / n > 10, 'the skinned trinket draws differently from the vanilla stone sword', {slotA: a, slotB: b, meanDiff: +(d / n).toFixed(2)});
    }
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'server.txt'), await getText(webPort, '/log').catch(() => ''));
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'SILENTFX_BROWSER_FAIL ' : 'SILENTFX_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('SILENTFX_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('SILENTFX_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 9 * 60 * 1000).unref();
