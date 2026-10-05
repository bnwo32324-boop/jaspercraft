'use strict';
/* Jerky and the new Portal Gun in the real TeaVM client (owner 2026-10-05: "sanitized flesh should have its own texture, and it
 * should look like jerky"; "the portal gun ... texture looks really bad ... the model should be better"): one headless Chrome (below
 * normal priority) against the loopback fixture (scripts/tank-preview.cjs) with the wide-inventory server jar. Cooked beef is given
 * to the player five ways -- as the server makes Sanitized Flesh (JasprApocalypse.id), as the creative catalogue shows it
 * (JasprCreative.id), plain, and as another supply (a field ration) -- next to a Portal Gun (diamond hoe, band 1160) and a plain
 * diamond hoe. The inventory screen is opened and every hotbar slot is compared with what its item must look like:
 *   - the two Sanitized Flesh stacks draw the jerky icon (close to the 16x16 pixels of scripts/supply-art/jerky.cjs, and far from
 *     the vanilla steak), plain cooked beef and the other supply keep the vanilla steak;
 *   - the Portal Gun draws its own model (blue and orange glow, white shell), not the vanilla hoe;
 *   - the NBT-skin stage is live (the getter was installed once, answered for the flesh, never failed);
 *   - no page exceptions. Screenshots, including the gun in the first-person hand, go to the out folder.
 *
 *   node tests/guns-art-browser.cjs [out-dir]
 * Needs candidate/deploy/classes.js and assets.epk (node scripts/assemble-guns-client.cjs) and candidate/browser-fixture/ (the wide
 * server jar), candidate/tanks/JasprTanks.jar and candidate/tank-client/ (borrowed by the fixture).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'guns-art-browser'));
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

// What the two cooked-beef icons must look like: the jerky from the generator, the steak from the archive being tested.
const jerky = require('../scripts/supply-art/jerky.cjs');
const {decode: decodeEpk} = require('../scripts/merge-apocalypse-assets.cjs');
const {decode: decodeIcon} = require('../scripts/png-codec.cjs');
const epk = decodeEpk(fs.readFileSync(path.join(ROOT, 'candidate', 'deploy', 'assets.epk')));
const steakEntry = epk.entries.find(e => e.name === 'assets/minecraft/textures/items/beef_cooked.png');
const steak = decodeIcon(steakEntry.value);
const jerkyPixels = jerky.pixels();
const steakPixels = Array.from({length: 256}, (_, i) => steak.rgba[i * 4 + 3] ? [steak.rgba[i * 4], steak.rgba[i * 4 + 1], steak.rgba[i * 4 + 2]] : null);

/** The pixels of a slot box in a screenshot (the slot is 16 GUI px = k css px per GUI px). */
function crop(img, box) {
  const [x0, y0, w, h] = box, rows = [];
  for (let y = 0; y < h; y++) { const row = []; for (let x = 0; x < w; x++) row.push(img.rgb(x0 + x, y0 + y)); rows.push(row); }
  return rows;
}
const SLOT_GREY = [139, 139, 139];
const isBackground = c => Math.abs(c[0] - SLOT_GREY[0]) < 14 && Math.abs(c[1] - SLOT_GREY[1]) < 14 && Math.abs(c[2] - SLOT_GREY[2]) < 14;
/** Mean absolute difference of two equal-size crops, and how many pixels of the first are item pixels (not the slot background). */
function compare(a, b) {
  let sum = 0, n = 0;
  for (let y = 0; y < a.length; y++) for (let x = 0; x < a[y].length; x++) { const p = a[y][x], q = b[y][x]; sum += Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]); n += 3; }
  return sum / n;
}
/** An icon (256 entries) drawn the way the game draws it into a k x k box: nearest-neighbour over the slot background. */
function expected(pixels, size) {
  const rows = [];
  for (let y = 0; y < size; y++) { const row = []; for (let x = 0; x < size; x++) row.push(pixels[Math.floor(y * 16 / size) * 16 + Math.floor(x * 16 / size)] || SLOT_GREY); rows.push(row); }
  return rows;
}
const count = (rows, pick) => rows.flat().filter(pick).length;

// A test copy of the candidate client that lets the probe reach the Minecraft instance and the NBT-skin state.
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(ROOT, 'candidate', 'deploy', 'classes.js'), 'latin1');
if (!candidate.includes('$rt_globals.JasprWideBridge = {') || !candidate.includes('JasprNbtSkinInstall(KR7)')) throw new Error('candidate lacks the wide inventory or the NBT-skin stage');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace('$rt_globals.JasprWideBridge = {', '$rt_globals.__wideMc = function () { return HEH; }; $rt_globals.__nbtSkin = function () { return JasprNbtSkinState; }; $rt_globals.JasprWideBridge = {'), 'latin1'));

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
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(ROOT, 'candidate', 'deploy', 'assets.epk'),
      TANK_PREVIEW_COPY: 'candidate/browser-fixture/jaspr-paper-wide.jar=>paper.jar'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2000));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  check(!/VerifyError|NoSuchFieldError|NoSuchMethodError/.test(fxOut), 'patched server classes load');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-guns-probe-'));
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
  const key = async (code, keyCode, k) => {
    await send('Input.dispatchKeyEvent', {type: 'keyDown', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode}); await sleep(90);
    await send('Input.dispatchKeyEvent', {type: 'keyUp', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode}); await sleep(500);
  };
  const screen = () => evaluate(`(function(){try{var m=__wideMc(),g=m.cj;if(!g||!g.h2)return null;var l=g.h2.cn,s=[];
    for(var k=0;k<l.g;k++){var x=l.qN.data[k];s.push({n:x.pO,i:x.bQx,x:x.Lr,y:x.Fg,jw:x.$jw||0});}
    var rw=(g.q|0)+(g.$jwCut|0);return {is:g.is,l7:g.l7,q:g.q,clo:g.clo,gv:g.gv,k:document.querySelector('#game_frame canvas').getBoundingClientRect().width/rw,slots:s};}catch(e){return {error:String(e)};}})()`);
  const skinState = () => evaluate('(function(){try{var s=__nbtSkin();return {installed:s.installed,hits:s.hits,errors:s.errors};}catch(e){return {error:String(e)};}})()');

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__wideMc&&__wideMc()&&__wideMc().v);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(150000), 'client joins the fixture server');
    await sleep(3000);
    await cmd('gamemode 0 ' + NAME);
    const flesh = 'JasprApocalypse:{id:"sanitized_flesh",equipmentMark:"jaspr-expedition-v1",tier:1}';
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.0 minecraft:cooked_beef 1 0 {' + flesh + ',display:{Name:"Sanitized Flesh"}}');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.1 minecraft:cooked_beef 1 0');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.2 minecraft:cooked_beef 1 0 {JasprCreative:{id:"sanitized_flesh"}}');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.3 minecraft:cooked_beef 1 0 {JasprApocalypse:{id:"field_ration",tier:1}}');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.4 minecraft:diamond_hoe 1 1160 {Unbreakable:1b,HideFlags:63,display:{Name:"Portal Gun"}}');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.5 minecraft:diamond_hoe 1 0');
    await sleep(1200);

    await key('KeyE', 69, 'e'); await sleep(1500);
    const sc = await screen();
    check(sc && sc.slots && sc.slots.length > 40, 'the inventory screen is open', sc && (sc.error || sc.slots.length));
    await sleep(500);
    const img = await shot('inventory');
    const slotBox = n => { const s = sc.slots.find(x => x.n === n); return [Math.round((sc.is + s.x) * sc.k), Math.round((sc.l7 + s.y) * sc.k), Math.round(16 * sc.k), Math.round(16 * sc.k)]; };
    if (sc && sc.slots && img) {
      const size = Math.round(16 * sc.k), boxes = [0, 1, 2, 3, 4, 5].map(i => slotBox(36 + i)), crops = boxes.map(b => crop(img, b));
      const expJerky = expected(jerkyPixels, size), expSteak = expected(steakPixels, size);
      const dJerky = crops.map(c => +compare(c, expJerky).toFixed(1)), dSteak = crops.map(c => +compare(c, expSteak).toFixed(1));
      console.log('distance to the jerky icon per slot', dJerky, 'to the steak icon', dSteak);
      for (const [i, what] of [[0, 'Sanitized Flesh as the server and the recipe book make it'], [2, 'the creative catalogue placeholder']]) {
        check(dJerky[i] < dSteak[i] - 6, what + ' draws jerky, not the steak', {jerky: dJerky[i], steak: dSteak[i]});
        check(dJerky[i] < 26, what + ' is close to the jerky icon', {jerky: dJerky[i]});
      }
      for (const [i, what] of [[1, 'plain cooked beef'], [3, 'another supply on the same item']]) {
        check(dSteak[i] < dJerky[i] - 6, what + ' keeps the vanilla steak', {jerky: dJerky[i], steak: dSteak[i]});
      }
      check(compare(crops[0], crops[2]) < 6, 'the two Sanitized Flesh stacks look the same', {diff: +compare(crops[0], crops[2]).toFixed(2)});
      check(compare(crops[1], crops[3]) < 4, 'plain cooked beef and the other supply look the same', {diff: +compare(crops[1], crops[3]).toFixed(2)});
      // The Portal Gun: its own model, with colour.
      const gun = crops[4], hoe = crops[5];
      check(compare(gun, hoe) > 12, 'the Portal Gun draws differently from the vanilla hoe', {diff: +compare(gun, hoe).toFixed(2)});
      // Saturated blue (not the diamond hoe's cyan, whose green is as high as its blue), orange, and white shell.
      const blue = c => c[2] > 140 && c[2] > c[0] + 60 && c[1] < c[2] - 40, orange = c => c[0] > 190 && c[0] > c[2] + 70 && c[1] > 70, white = c => c[0] > 190 && c[1] > 190 && c[2] > 190;
      const items = count(gun, c => !isBackground(c));
      check(items > 60, 'the Portal Gun draws something substantial', {itemPixels: items});
      check(count(gun, blue) >= 3, 'the Portal Gun shows its blue energy cell', {blue: count(gun, blue)});
      check(count(gun, white) >= 5, 'and its white shell', {white: count(gun, white)});
      // The icon fills the slot (the display transform is fitted to it) instead of the old small corner of it.
      const xs = [], ys = [];
      gun.forEach((row, y) => row.forEach((c, x) => { if (!isBackground(c)) { xs.push(x); ys.push(y); } }));
      const span = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) / size;
      check(span > 0.7 && span <= 1, 'the Portal Gun icon fills the slot', {span: +span.toFixed(2)});
      check(count(hoe, blue) === 0 && count(hoe, orange) === 0, 'the vanilla hoe has neither');
    }
    await key('Escape', 27, 'Escape');
    await sleep(800);
    const state = await skinState();
    check(state && state.installed === 1 && state.errors === 0 && state.hits >= 2, 'the NBT-skin stage is live: installed once, answered for the flesh, never failed', state);
    // In the hand: select the Portal Gun and look at it in first person; and the hotbar with all six items.
    await key('Digit5', 53, '5'); await sleep(1500);
    const hand = await shot('first-person-portal-gun');
    if (hand) {
      // Held in first person (seen from behind, over the top and the left flank): the shell, the blue cell and the split rail's orange half.
      const region = crop(hand, [520, 330, 440, 210]);
      const white = c => c[0] > 190 && c[1] > 190 && c[2] > 190, blue = c => c[2] > 140 && c[2] > c[0] + 60 && c[1] < c[2] - 40, orange = c => c[0] > 190 && c[0] > c[2] + 70 && c[1] > 70 && c[1] < 200;
      check(count(region, white) > 1500, 'in the hand the Portal Gun shows its white shell', {white: count(region, white)});
      check(count(region, blue) > 300, 'and its blue cell and rail', {blue: count(region, blue)});
      check(count(region, orange) > 40, 'and the orange half of the rail', {orange: count(region, orange)});
    }
    await key('Digit1', 49, '1'); await sleep(1200);
    await shot('first-person-jerky');
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    const modelErrors = consoleLog.filter(l => /Missing model|Unable to load model|model.*(error|fail)/i.test(l));
    check(modelErrors.length === 0, 'no model loading errors in the console', modelErrors.slice(0, 3));
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'server.txt'), await getText(webPort, '/log').catch(() => ''));
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'GUNS_BROWSER_FAIL ' : 'GUNS_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('GUNS_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('GUNS_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 9 * 60 * 1000).unref();
