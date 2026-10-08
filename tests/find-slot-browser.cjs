'use strict';
/* Find on an inventory item in the real client, end to end (owner 2026-10-08: "right-click any item in your inventory and click Find.
 * The UI should look the same, and it should have the same functionality of identifying the items in nearby chests"; "It should be
 * Shift + right-click, since right-click already has a function in the inventory to halve item stack"): a real Paper server with the
 * wide-inventory jar and the real JasprFinder 1.1.0 (search: all, so a chest nobody opened counts), one headless Chrome at below-normal
 * priority, the candidate client (node scripts/assemble-find-slot-client.cjs). It checks, with real mouse and key events:
 *   - a plain right click on a stack halves it (the cursor holds half) and opens no menu;
 *   - Shift + right click on an item opens the menu (titled with the item's name, Find in chests and Cancel, the panel menu's colours),
 *     moves nothing, and hides the item tooltip;
 *   - Find sends "find slot ..." and closes the screen, the server finds the chest holding the item (FINDER_FIND via=slot found=1) and
 *     says so; an item no chest holds finds none; an armour piece works; an empty slot opens no menu;
 *   - Cancel and Escape close the menu without closing the inventory; the phone's way (a synthetic Shift key, as the touch toggle sends)
 *     opens it too;
 *   - an item that left its slot before Find reaches the server is refused there (FINDER_FIND_REFUSED reason=empty);
 *   - no page exceptions, the recipe book never disabled itself, and the plugin's metrics line counts the slot requests.
 * Screenshots go to the out folder.
 *
 *   node tests/find-slot-browser.cjs [out-dir]
 * Needs candidate/find-slot-deploy/{classes.js,assets.epk} (node scripts/assemble-find-slot-client.cjs), server/plugins/JasprFinder.jar
 * (node scripts/patch-plugin-jars.cjs JasprFinder), candidate/browser-fixture/jaspr-paper-wide.jar, and the fixture's own borrowed files
 * (candidate/tanks/JasprTanks.jar, candidate/tank-client/).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const DEPLOY = path.resolve(process.env.FIND_SLOT_DEPLOY || path.join(ROOT, 'candidate', 'find-slot-deploy'));
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'find-slot-browser'));
const NAME = 'FindTester', W = 960, H = 540;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 700) : ''));
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

const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
/** A stand-in plugin with one console command, "opentable <player>", that opens a crafting table window for that player (a window with an
 * id of its own, which a test cannot get by aiming in a headless client). */
function buildTableProbe() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-findslot-probe-src-'));
  try {
    const classes = path.join(dir, 'classes'), src = path.join(dir, 'src');
    fs.mkdirSync(classes); fs.mkdirSync(src);
    fs.writeFileSync(path.join(src, 'TableProbe.java'), 'package chat.jaspr.finderprobe;\npublic final class TableProbe extends org.bukkit.plugin.java.JavaPlugin {\n'
      + '  @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {\n'
      + '    org.bukkit.entity.Player p = args.length > 0 ? getServer().getPlayerExact(args[0]) : null;\n'
      + '    if (p == null) return false;\n    p.openWorkbench(null, true);\n    return true;\n  }\n}\n');
    const run = (tool, args) => {
      const r = spawnSync(path.join(JDK, tool + '.exe'), ['-J-XX:ActiveProcessorCount=1', ...args], {encoding: 'utf8', windowsHide: true, cwd: dir});
      if (r.status !== 0) throw new Error(tool + ' failed: ' + (r.stdout + r.stderr).slice(0, 2000));
    };
    run('javac', ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-cp', path.join(ROOT, 'server', 'cache', 'patched_1.12.2.jar'), '-d', classes, path.join(src, 'TableProbe.java')]);
    fs.writeFileSync(path.join(classes, 'plugin.yml'), 'name: TableProbe\nversion: 1\nmain: chat.jaspr.finderprobe.TableProbe\ncommands:\n  opentable:\n    description: opens a crafting table window\n    usage: /opentable <player>\n');
    const jar = path.join(out, 'TableProbe.jar');
    run('jar', ['--create', '--file', jar, '--no-manifest', '-C', classes, '.']);
    return jar;
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
}

for (const f of ['classes.js', 'assets.epk']) if (!fs.existsSync(path.join(DEPLOY, f))) throw new Error('missing candidate client file: ' + path.join(DEPLOY, f));
for (const f of ['server/plugins/JasprFinder.jar', 'candidate/browser-fixture/jaspr-paper-wide.jar', 'candidate/tanks/JasprTanks.jar']) if (!fs.existsSync(path.join(ROOT, f))) throw new Error('missing fixture input: ' + f);
// A test copy of the candidate client that lets the probe reach the Minecraft instance, the recipe book's state and the open window.
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(DEPLOY, 'classes.js'), 'latin1');
const ANCHOR = '$rt_globals.JasprWideBridge = {';
if (candidate.split(ANCHOR).length !== 2 || !candidate.includes('function JasprRecipeBookClick(') || !candidate.includes('d.menu.slot !== undefined) a.a_b = null')) throw new Error('the candidate client lacks the Find-on-slot module');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace(ANCHOR,
  '$rt_globals.__mc = function () { return HEH; };' +
  ' $rt_globals.__isPlayerInventory = function () { var g = HEH && HEH.cj; return !!g && g.h2 instanceof A2Z; };' +
  ' $rt_globals.__book = function () { var all = JasprRecipeBook.books(); return all.length ? all[all.length - 1] : null; };' +
  ' $rt_globals.__state = function () { var b = __book(); if (!b) return null; var m = b.menu;' +
  '   return {craftable: !!b.craftable, menu: m ? {slot: m.slot, title: m.title, x: m.x, y: m.y, w: m.w, h: m.h, acts: m.entries.map(function (e) { return e.act; }), labels: m.entries.map(function (e) { return e.label; })} : null,' +
  '   inventory: b.inventory ? b.inventory.map(function (s) { return s && s.rA && !s.bg_ ? s.PD + "x" + s.bK : null; }) : null, hovered: !!(HEH.cj && HEH.cj.a_b)}; };' +
  ' $rt_globals.__held = function () { var s = HEH.v && HEH.v.bx ? HEH.v.bx.fH : null; return s && s.rA && !s.bg_ ? s.PD : 0; };' +
  ' $rt_globals.__gui = function () { var g = HEH && HEH.cj; if (!g || !g.h2) return null; var l = g.h2.cn, c = document.querySelector("#game_frame canvas").getBoundingClientRect(), rw = (g.q | 0) + (g.$jwCut | 0);' +
  '   var slots = []; for (var i = 0; i < (l.g | 0); i++) slots.push([l.qN.data[i].Lr, l.qN.data[i].Fg]);' +
  '   return {is: g.is, l7: g.l7, q: g.q, win: g.h2.iu, k: c.width / rw, cw: c.width, ch: c.height, slots: slots, inv: __isPlayerInventory()}; };' +
  ' ' + ANCHOR), 'latin1'));

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
  // The plugin's own default is search: opened (only chests the player opened); the test asks for every chest in range.
  const config = path.join(out, 'finder-config.yml');
  fs.writeFileSync(config, 'search: all\nradius: 48\n');
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(DEPLOY, 'assets.epk'), TANK_PREVIEW_PLUGINS: 'server/plugins/JasprFinder.jar,' + path.relative(ROOT, buildTableProbe()).split(path.sep).join('/'),
      TANK_PREVIEW_COPY: 'candidate/browser-fixture/jaspr-paper-wide.jar=>paper.jar;' + path.relative(ROOT, config).split(path.sep).join('/') + '=>plugins/JasprFinder/config.yml'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2500));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port, fixtureDir = (fxOut.match(/Fixture: (.+)/) || [])[1].trim();
  check(!/VerifyError|NoSuchFieldError|NoSuchMethodError/.test(fxOut), 'patched server classes load');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };
  const serverLog = () => getText(webPort, '/log').catch(() => '');

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-findslot-probe-'));
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
  const shiftDown = () => send('Input.dispatchKeyEvent', {type: 'keyDown', code: 'ShiftLeft', key: 'Shift', windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16, modifiers: 8});
  const shiftUp = () => send('Input.dispatchKeyEvent', {type: 'keyUp', code: 'ShiftLeft', key: 'Shift', windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16});
  /** The phone's Shift toggle: a synthetic key event on the window (no shiftKey on the mouse events that follow). */
  const phoneShift = down => evaluate(`window.dispatchEvent(new KeyboardEvent('${down ? 'keydown' : 'keyup'}',{bubbles:true,cancelable:true,code:'ShiftLeft',key:'Shift',keyCode:16,which:16})); true`);
  const clickAt = async (x, y, button = 'left', modifiers = 0) => {
    await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x, y, modifiers}); await sleep(150);
    await send('Input.dispatchMouseEvent', {type: 'mousePressed', x, y, button, clickCount: 1, modifiers}); await sleep(90);
    await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x, y, button, clickCount: 1, modifiers}); await sleep(600);
  };
  const gui = () => evaluate('JSON.stringify(__gui())').then(t => (t ? JSON.parse(t) : null));
  const state = () => evaluate('JSON.stringify(__state())').then(t => (t ? JSON.parse(t) : null));
  const held = () => evaluate('__held()');
  const slotPoint = (g, slot) => [Math.round((g.is + g.slots[slot][0] + 8) * g.k), Math.round((g.l7 + g.slots[slot][1] + 8) * g.k)];
  const entryPoint = (g, menu, act) => [Math.round((g.is + menu.x + 10) * g.k), Math.round((g.l7 + menu.y + 2 + 12 * (menu.acts.indexOf(act) + 1) + 6) * g.k)];
  const near = (img, x, y, want, tol = 30, r = 3) => {
    for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
      const px = Math.min(img.width - 1, Math.max(0, x + dx)), py = Math.min(img.height - 1, Math.max(0, y + dy)), c = img.rgb(px, py);
      if (Math.abs(c[0] - want[0]) <= tol && Math.abs(c[1] - want[1]) <= tol && Math.abs(c[2] - want[2]) <= tol) return true;
    }
    return false;
  };
  /** Pixels in a screen rectangle (CSS px) within tol of a colour. */
  const count = (img, x0, y0, x1, y1, want, tol = 40) => {
    let n = 0;
    for (let y = Math.max(0, y0); y < Math.min(img.height, y1); y++) for (let x = Math.max(0, x0); x < Math.min(img.width, x1); x++) {
      const c = img.rgb(x, y);
      if (Math.abs(c[0] - want[0]) <= tol && Math.abs(c[1] - want[1]) <= tol && Math.abs(c[2] - want[2]) <= tol) n++;
    }
    return n;
  };
  const openInventory = async () => {
    await key('KeyE', 69, 'e');
    const end = Date.now() + 20000;
    while (Date.now() < end) { const g = await gui(); const s = await state(); if (g && g.inv && s && s.craftable) return g; await sleep(500); }
    return null;
  };

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(180000), 'client joins the fixture server');
    await sleep(3000);
    const log0 = await serverLog();
    check(/FINDER_READY channel=jaspr:find sort=jaspr:sort slotFind=true radius=48 search=all/.test(log0 + fxOut), 'the Finder plugin is up with Find on slots (search: all)');
    check(/Enabling JasprFinder v1\.1\.0/.test(log0 + fxOut), 'JasprFinder 1.1.0 enabled');
    await cmd('gamerule announceAdvancements false');
    await cmd('gamemode 0 ' + NAME);
    await cmd('time set 0');
    await cmd('effect ' + NAME + ' clear');
    // Hotbar 0: a diamond pickaxe (a chest nearby holds one), 1: 40 cobblestone (no chest holds any), 2: 6 stone bricks; helmet; chest 3 blocks away.
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.0 minecraft:diamond_pickaxe 1 0');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.1 minecraft:cobblestone 40 0');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.2 minecraft:stonebrick 6 0');
    await cmd('replaceitem entity ' + NAME + ' slot.armor.head minecraft:iron_helmet 1 0');
    await cmd('execute ' + NAME + ' ~ ~ ~ setblock ~3 ~ ~ minecraft:chest');
    await cmd('execute ' + NAME + ' ~ ~ ~ replaceitem block ~3 ~ ~ slot.container.0 minecraft:diamond_pickaxe 1 0');
    await cmd('execute ' + NAME + ' ~ ~ ~ replaceitem block ~3 ~ ~ slot.container.1 minecraft:iron_helmet 1 0');
    await sleep(13000);   // the join lines fade out of the chat

    // ---- plain right click: the game's own half-stack pick-up, no menu --------------------------------------------------
    let g = await openInventory();
    check(g && g.inv, 'the survival inventory is open with the recipe panel ready', g && {win: g.win, k: g.k, slots: g.slots.length});
    if (!g) throw new Error('no inventory');
    const HOT0 = 36, HOT1 = 37, HOT2 = 38, HEAD = 5, EMPTY = 20;
    let s0 = await state();
    check(s0 && s0.inventory[HOT1 - 9] === '40x0', 'slot 37 holds the 40 cobblestone', s0 && s0.inventory.slice(27, 30));
    await clickAt(...slotPoint(g, HOT1), 'right');
    check((await held()) === 20, 'a plain right click halves the stack: 20 on the cursor, as before', await held());
    let s = await state();
    check(s && !s.menu, 'and opens no menu');
    await clickAt(...slotPoint(g, HOT1), 'left');   // put it back
    check((await held()) === 0, 'the stack is put back');

    // ---- Shift + right click: the menu ------------------------------------------------------------------------------------
    await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: slotPoint(g, HOT0)[0], y: slotPoint(g, HOT0)[1]}); await sleep(700);
    check((await state()).hovered === true, 'the game hovers the slot under the pointer (its tooltip would show)');
    await shiftDown(); await sleep(150);
    await clickAt(...slotPoint(g, HOT0), 'right', 8);
    await shiftUp();
    s = await state();
    check(s && s.menu && s.menu.slot === HOT0 && s.menu.title === 'Diamond Pickaxe', 'Shift + right click on the pickaxe opens the menu titled with its name', s && s.menu);
    check(s && s.menu && s.menu.acts.join() === 'find,close' && s.menu.labels.join() === 'Find in chests,Cancel', 'with Find in chests and Cancel', s && s.menu && s.menu.labels);
    check((await held()) === 0, 'the cursor stays empty');
    await sleep(400);
    s = await state();
    check(s.menu && s.hovered === false, 'with the menu open the game hovers no slot, so no item tooltip is drawn');
    check(s.inventory[HOT0 - 9] === '1x0' && s.inventory[HOT1 - 9] === '40x0' && s.inventory[HOT2 - 9] === '6x0', 'nothing moved: no quick-move, no half-stack', s.inventory.slice(27, 30));
    await sleep(600);
    const img = await shot('menu-open');
    if (img && s && s.menu) {
      const m = s.menu, x0 = Math.round((g.is + m.x) * g.k), y0 = Math.round((g.l7 + m.y) * g.k), x1 = Math.round((g.is + m.x + m.w) * g.k), y1 = Math.round((g.l7 + m.y + m.h) * g.k);
      check(near(img, Math.round((x0 + x1) / 2), y0 - Math.round(g.k / 2), [80, 0, 160], 28, 2), 'the menu has the panel menu\'s purple frame', [x0, y0, x1, y1]);
      check(count(img, x0, y0, x1, y1, [255, 255, 85], 60) > 8, 'a yellow title');
      check(count(img, x0, y0 + Math.round(12 * g.k), x1, y1, [85, 255, 85], 60) > 8, 'a green Find in chests');
      check(count(img, x0, y0 + Math.round(24 * g.k), x1, y1, [160, 160, 160], 50) > 8, 'a grey Cancel');
      const dark = img.rgb(x1 - Math.round(3 * g.k), y0 + Math.round(3 * g.k));
      check(dark[0] < 50 && dark[1] < 30 && dark[2] < 60, 'on the same dark fill', dark);
    }

    // ---- Find: the server finds the chest -------------------------------------------------------------------------------
    s = await state();
    await clickAt(...entryPoint(g, s.menu, 'find'), 'left');
    await sleep(1200);
    check(!(await gui()), 'Find closes the inventory so the sparks can be seen');
    let log = await serverLog();
    check(/FINDER_FIND player=\S+ item=DIAMOND_PICKAXE:0 via=slot found=1 scanned=\d+ ms=\d+/.test(log), 'the server read the slot and found the chest holding a diamond pickaxe (via=slot found=1)');
    await sleep(1500);
    await shot('after-find');   // the chat line "[Find] Diamond Pickaxe is in 1 chest nearby ..."

    // ---- a miss, an armour piece, an empty slot, Cancel, Escape ------------------------------------------------------------
    await sleep(2200);   // one Find every two seconds per player
    g = await openInventory();
    await shiftDown(); await sleep(150);
    await clickAt(...slotPoint(g, HOT1), 'right', 8);
    s = await state();
    check(s && s.menu && s.menu.slot === HOT1 && s.menu.title === 'Cobblestone', 'Shift + right click on the cobblestone', s && s.menu);
    await shot('menu-cobblestone');
    await clickAt(...entryPoint(g, s.menu, 'find'), 'left', 8);
    await shiftUp();
    await sleep(1200);
    log = await serverLog();
    check(/FINDER_FIND player=\S+ item=COBBLESTONE:0 via=slot found=0 /.test(log), 'an item no chest holds finds none (found=0)');
    await sleep(2200);
    g = await openInventory();
    await shiftDown(); await sleep(150);
    await clickAt(...slotPoint(g, HEAD), 'right', 8);
    s = await state();
    check(s && s.menu && s.menu.slot === HEAD && s.menu.title === 'Iron Helmet', 'an armour slot works: Iron Helmet', s && s.menu);
    await clickAt(...entryPoint(g, s.menu, 'close'), 'left', 8);
    s = await state();
    check(s && !s.menu, 'Cancel closes the menu');
    check(!!(await gui()), '...and the inventory stays open');
    await clickAt(...slotPoint(g, EMPTY), 'right', 8);
    s = await state();
    check(s && !s.menu, 'Shift + right click on an empty slot opens no menu');
    await clickAt(...slotPoint(g, HOT2), 'right', 8);
    await shiftUp();
    s = await state();
    check(s && s.menu && s.menu.title === 'Stone Bricks', 'stone bricks: the menu', s && s.menu);
    await key('Escape', 27, 'Escape');
    s = await state();
    check(s && !s.menu && !!(await gui()), 'Escape closes the menu first and leaves the inventory open');

    // ---- the phone's way: a synthetic Shift key, then a right click without a Shift modifier -----------------------------------
    await phoneShift(true); await sleep(250);
    await clickAt(...slotPoint(g, HOT0), 'right', 0);
    s = await state();
    check(s && s.menu && s.menu.slot === HOT0, 'the touch Shift toggle (a synthetic key) opens the menu too', s && s.menu);
    await clickAt(...entryPoint(g, s.menu, 'close'), 'right', 0);   // Right and Shift are still on: the tap on Cancel is the menu's
    s = await state();
    check(s && !s.menu && !!(await gui()), 'on the phone a tap on Cancel (Right and Shift still on) closes the menu, and no new menu opens for the slot under it');
    await phoneShift(false); await sleep(250);
    await clickAt(...slotPoint(g, HOT1), 'right', 0);
    s = await state();
    check(s && !s.menu && (await held()) === 20, 'with Shift off again a right click halves the stack once more', {held: await held()});
    await clickAt(...slotPoint(g, HOT1), 'left');
    check((await held()) === 0, 'the stack is put back');

    // ---- an item that left its slot before Find reached the server -------------------------------------------------------------
    await shiftDown(); await sleep(150);
    await clickAt(...slotPoint(g, HOT2), 'right', 8);
    await shiftUp();
    s = await state();
    check(s && s.menu && s.menu.slot === HOT2, 'the stone bricks menu is open');
    await cmd('clear ' + NAME + ' minecraft:stonebrick');
    await sleep(1500);
    await clickAt(...entryPoint(g, s.menu, 'find'), 'left');
    await sleep(1500);
    log = await serverLog();
    check(/FINDER_FIND_REFUSED player=\S+ via=slot reason=empty window=0 slot=38/.test(log), 'a slot that was emptied meanwhile is refused on the server (reason=empty)');
    await shot('after-refused');

    // ---- a crafting table: its window has an id of its own (the server compares it with the window that is open) ------------------
    await sleep(2200);
    await cmd('opentable ' + NAME);
    await sleep(2500);
    const table = await gui();
    check(table && !table.inv && table.win > 0, 'a crafting table window opens, with an id of its own', table && {win: table.win, slots: table.slots.length});
    if (table && !table.inv && table.win > 0) {
      for (let i = 0; i < 40 && !((await state()) || {}).craftable; i++) await sleep(250);
      const T0 = 37;   // the first hotbar slot of the table's window (the pickaxe)
      await phoneShift(true); await sleep(250);   // the phone's way throughout: Shift on, every tap a right click
      await clickAt(...slotPoint(table, T0), 'right', 0);
      s = await state();
      check(s && s.menu && s.menu.slot === T0 && s.menu.title === 'Diamond Pickaxe', 'a tap on the pickaxe at the table opens the menu (Right and Shift on)', s && s.menu);
      await shot('table-menu');
      await clickAt(...entryPoint(table, s.menu, 'find'), 'right', 0);   // a right click on Find in chests, Shift still on, is the menu's
      await phoneShift(false);
      await sleep(1500);
      const file = fs.readFileSync(path.join(fixtureDir, 'paper.log'), 'utf8');
      check((file.match(/FINDER_FIND player=\S+ item=DIAMOND_PICKAXE:0 via=slot found=1/g) || []).length === 2, 'the server found the chest for the table window\'s slot too', (file.match(/FINDER_FIND .*/g) || []).slice(-3));
      check((file.match(/FINDER_FIND_REFUSED/g) || []).length === 1, 'and refused only the emptied slot (the table window id matched the open window)', file.match(/FINDER_FIND_REFUSED.*/g));
      await shot('after-table-find');
    }

    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    check(!consoleLog.some(l => /recipe book\] disabled|chest search\] disabled/.test(l)), 'the recipe book and chest search never disabled themselves', consoleLog.filter(l => /disabled/.test(l)).slice(0, 2));
    check(!/Exception|VerifyError|FINDER_SAVE_FAILED|FINDER_LOAD_FAILED/.test((await serverLog()).replace(/javax\.imageio\.IIOException: Can't read input file!/g, '')), 'no server exceptions');
    fs.writeFileSync(path.join(out, 'server.txt'), await serverLog());
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  // The plugin's metrics line, written when the server stops.
  const end = Date.now() + 60000;
  while (fixture.exitCode === null && Date.now() < end) await sleep(500);
  let paperLog = '';
  try { paperLog = fs.readFileSync(path.join(fixtureDir, 'paper.log'), 'utf8'); } catch (e) { }
  check(/FINDER_METRICS requests=3 found=2 misses=1 .*slotRequests=3 slotStale=0 slotEmpty=1 /.test(paperLog), 'FINDER_METRICS counts the three slot searches (two found, one miss) and the refused one', (paperLog.match(/FINDER_METRICS.*/) || [''])[0]);
  console.log((failures ? 'FIND_SLOT_BROWSER_FAIL ' : 'FIND_SLOT_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('FIND_SLOT_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('FIND_SLOT_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 12 * 60 * 1000).unref();
