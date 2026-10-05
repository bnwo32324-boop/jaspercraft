'use strict';
/* Overloaded Armor Bar in the real TeaVM client (owner 2026-10-05: "Overloaded armor bar is not working" -- the gear in the
 * report, an Emerald Helmet, an enchanted diamond chestplate and diamond leggings and boots, adds up to exactly 20 armour,
 * which is one full white row by the mod's own rule; this proves the bar does wrap above 20 in the real engine).
 *
 * One headless Chrome (below normal priority) against the loopback fixture (scripts/tank-preview.cjs, a lean Paper server with
 * the client's own files). The player is in survival and wears armour pieces that carry explicit generic.armor modifiers, so the
 * server computes 0, 7, 20, 22, 23 and 30 armour and the client's HUD reads it from the attribute as in play. Each state is
 * screenshotted and the ten icons of the armour row are classified by colour:
 *   0: no row   7: three white, a half, six empty outlines   20: ten white   22: one orange over nine white
 *   23: orange, an orange/white half, eight white   30: five orange, five white   22 with Regeneration: as 22 (the other loop entry)
 * Screenshots (and a 4x crop of the row) go to the out folder.
 *
 *   node tests/armor-bar-browser.cjs [out-dir]
 * Env: ARMOR_BAR_CLASSES (client bundle, default site/classes.js), ARMOR_BAR_ASSETS (default site/assets.epk), TANK_PREVIEW_ROOT (the
 * checkout that holds candidate/tanks and candidate/tank-client, default this one). Needs Chrome and the JDK at their usual paths.
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const FIXTURE_ROOT = path.resolve(process.env.TANK_PREVIEW_ROOT || ROOT);
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'armor-bar-browser'));
const NAME = 'ArmorTester', W = 960, H = 540, S = 2;   // viewport, and the GUI scale it gets (960/3 = 320 but 540/3 = 180 < 240)
fs.mkdirSync(out, {recursive: true});
fs.rmSync(path.join(out, 'fixture'), {recursive: true, force: true});   // a fresh world and player every run (no armour kept from the last one)
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 600) : ''));
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
const CRC = (() => { const t = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
/** A 4x crop of a screenshot region as a PNG, to look at the armour row by eye. */
function cropPng(img, x0, y0, w, h, k) {
  const rows = [];
  for (let y = 0; y < h * k; y++) {
    const row = Buffer.alloc(1 + w * k * 3);
    for (let x = 0; x < w * k; x++) { const p = img.rgb(x0 + Math.floor(x / k), y0 + Math.floor(y / k)); row[1 + x * 3] = p[0]; row[2 + x * 3] = p[1]; row[3 + x * 3] = p[2]; }
    rows.push(row);
  }
  const chunk = (type, data) => { const b = Buffer.alloc(12 + data.length); b.writeUInt32BE(data.length, 0); b.write(type, 4, 'latin1'); data.copy(b, 8); b.writeUInt32BE(crc32(b.subarray(4, 8 + data.length)), 8 + data.length); return b; };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w * k, 0); ihdr.writeUInt32BE(h * k, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(Buffer.concat(rows))), chunk('IEND', Buffer.alloc(0))]);
}

// The armour row, in the GUI's own coordinates: x from the hotbar's left edge, y one row above the hearts (20 health, one row).
const X0 = W / S / 2 - 91, Y0 = H / S - 39 - 10;
/** Counts the bright pixels of an icon box (or a part of it) by colour: orange (the mod's 2nd colour), white and dark (outline). */
function paint(img, i, from, to) {
  const c = {orange: 0, white: 0, dark: 0, other: 0};
  for (let y = Math.round(Y0 * S); y < Math.round((Y0 + 9) * S); y++) for (let x = Math.round((X0 + i * 8 + from) * S); x < Math.round((X0 + i * 8 + to) * S); x++) {
    const [r, g, b] = img.rgb(x, y), hi = Math.max(r, g, b), lo = Math.min(r, g, b);
    if (hi < 70 && hi - lo < 25) c.dark++;
    else if (r > 140 && g > 30 && g < 125 && b < 60 && r > 1.6 * g) c.orange++;
    else if (lo > 140 && hi - lo < 45) c.white++;
    else c.other++;
  }
  return c;
}
/** 'orange' | 'white' | 'empty' (outline only) | 'none' (nothing drawn) | 'mixed' for one icon, or its left (0..5) / right (5..9) part. */
function kind(img, i, from = 0, to = 9) {
  const c = paint(img, i, from, to), bright = c.orange + c.white;
  if (c.orange >= 4 && c.white < 3) return 'orange';
  if (c.white >= 4 && c.orange < 3) return 'white';
  if (c.orange >= 3 && c.white >= 3) return 'mixed';
  if (bright < 3 && c.dark >= 4) return 'empty';
  if (bright < 3) return 'none';
  return 'unclear ' + JSON.stringify(c);
}
const row = (img, from = 0, to = 9) => [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => kind(img, i, from, to));
const rep = (k, n) => new Array(n).fill(k);

/** [head, chest, legs, feet] armour points (null = no piece). Server-side: explicit generic.armor modifiers on diamond items. */
const SCENARIOS = [
  {label: '00-none', total: 0, pieces: [null, null, null, null], expect: img => ({row: row(img), want: rep('none', 10)})},
  {label: '07-three-and-a-half', total: 7, pieces: [2, 3, 2, null], expect: img => ({row: row(img), want: [...rep('white', 3), 'white', ...rep('empty', 6)], half: [kind(img, 3, 0, 5), kind(img, 3, 5, 9)], wantHalf: ['white', 'empty']})},
  {label: '20-diamond-full', total: 20, pieces: [3, 8, 6, 3], expect: img => ({row: row(img), want: rep('white', 10)})},
  {label: '22-emerald-set', total: 22, pieces: [3, 9, 7, 3], expect: img => ({row: row(img), want: ['orange', ...rep('white', 9)]})},
  {label: '22-regeneration', total: 22, pieces: [3, 9, 7, 3], effect: 'regeneration', expect: img => ({row: row(img), want: ['orange', ...rep('white', 9)]})},
  {label: '23-half-wrap', total: 23, pieces: [3, 10, 7, 3], expect: img => ({row: row(img, 0, 5), want: ['orange', 'orange', ...rep('white', 8)], right: row(img, 5, 9), wantRight: ['orange', 'white', ...rep('white', 8)]})},
  {label: '30-the-cap', total: 30, pieces: [4, 12, 9, 5], expect: img => ({row: row(img), want: [...rep('orange', 5), ...rep('white', 5)]})}
];

// A test copy of the client bundle that lets the probe reach the Minecraft instance (the player is HEH.v).
const classesPath = path.join(out, 'classes.test.js');
const bundle = fs.readFileSync(path.resolve(process.env.ARMOR_BAR_CLASSES || path.join(ROOT, 'site', 'classes.js')), 'latin1');
const ANCHOR = '$rt_globals.JasprWideBridge = {';
if (bundle.split(ANCHOR).length !== 2) throw new Error('the client bundle lacks the wide inventory stage anchor');
if (!bundle.includes('/* JASPR_ARMORBAR_BEGIN */') || bundle.split('/*JASPR_ARMORBAR_V1*/').length !== 3) throw new Error('the client bundle lacks the armour bar stage');
fs.writeFileSync(classesPath, Buffer.from(bundle.replace(ANCHOR, '$rt_globals.__mc = function () { return HEH; }; ' + ANCHOR), 'latin1'));
const assetsPath = path.resolve(process.env.ARMOR_BAR_ASSETS || path.join(ROOT, 'site', 'assets.epk'));

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
  fixture = spawn(process.execPath, [path.join(FIXTURE_ROOT, 'scripts', 'tank-preview.cjs')], {cwd: FIXTURE_ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_ROOT: FIXTURE_ROOT, TANK_PREVIEW_REUSE: path.join(out, 'fixture'), TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: assetsPath})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2000));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-armorbar-probe-'));
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
    const img = decodePng(bytes);
    fs.writeFileSync(path.join(out, name + '-row.png'), cropPng(img, Math.round(X0 * S) - 6, Math.round(Y0 * S) - 6, 8 * 9 * S + 24, 9 * S + 40, 4));
    return img;
  };

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v);}catch(e){return false;}})()')) return true; await sleep(1000); } return false; };
    check(await inWorld(150000), 'client joins the fixture server');
    await sleep(3000);
    await cmd('gamerule announceAdvancements false');   // no toast and no chat line when the diamond pieces go on
    await cmd('gamemode 0 ' + NAME);
    await cmd('effect ' + NAME + ' clear');
    await sleep(13000);   // the join and game mode lines fade out of the chat (it is drawn over the armour row and dims it)
    const SLOTS = ['head', 'chest', 'legs', 'feet'], BASE = ['helmet', 'chestplate', 'leggings', 'boots'];
    for (const sc of SCENARIOS) {
      for (let k = 0; k < 4; k++) {
        const points = sc.pieces[k];
        if (points === null) continue;
        await cmd('replaceitem entity ' + NAME + ' slot.armor.' + SLOTS[k] + ' minecraft:diamond_' + BASE[k] + ' 1 0 {Unbreakable:1b,AttributeModifiers:[{AttributeName:"generic.armor",Name:"abt' + k + '",Amount:' + points + ',Operation:0,UUIDMost:' + (7000 + k) + 'L,UUIDLeast:' + (9000 + k) + 'L,Slot:"' + SLOTS[k] + '"}]}');
      }
      if (sc.effect) await cmd('effect ' + NAME + ' minecraft:' + sc.effect + ' 30 0 true');
      await sleep(2500);
      const img = await shot(sc.label);
      if (!img) { check(false, sc.label + ': screenshot'); continue; }
      const r = sc.expect(img);
      check(JSON.stringify(r.row) === JSON.stringify(r.want), sc.label + ': the armour row for ' + sc.total + ' armour', {got: r.row.join(' '), want: r.want.join(' ')});
      if (r.half) check(JSON.stringify(r.half) === JSON.stringify(r.wantHalf), sc.label + ': the half icon (left, right)', {got: r.half, want: r.wantHalf});
      if (r.right) check(JSON.stringify(r.right) === JSON.stringify(r.wantRight), sc.label + ': the right halves (the previous row\'s colour)', {got: r.right.join(' '), want: r.wantRight.join(' ')});
      if (sc.effect) await cmd('effect ' + NAME + ' clear');
    }
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    const server = await getText(webPort, '/log').catch(() => '');
    check(!/Exception|VerifyError|Modifier is already applied/.test(server.replace(/javax\.imageio\.IIOException: Can't read input file!/g, '')), 'no server exceptions');
    fs.writeFileSync(path.join(out, 'server.txt'), server);
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'ARMOR_BAR_BROWSER_FAIL ' : 'ARMOR_BAR_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('ARMOR_BAR_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('ARMOR_BAR_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 9 * 60 * 1000).unref();
