'use strict';
/* Wide inventory in the real TeaVM client: one headless Chrome (below normal priority, lightest video settings) against
 * the loopback fixture (scripts/tank-preview.cjs) running the patched server jar and the test-only WideProbe plugin.
 * Checks the hello/answer, the 14-slot hotbar (selection reaches the server), the inventory screen (66 slots, the pocket
 * beside the window, a click into a pocket slot and back, shift-click from the pocket, a click on the pocket frame does
 * not drop the carried stack), a chest window (83 slots) and the Creative screen; screenshots go to the out folder.
 * With --mobile it also checks the 14 touch hotbar buttons.
 *
 *   node tests/wide-inventory-browser.cjs [out-dir] [--mobile]
 * Needs: candidate/wide-client/classes.js, candidate/wide-server/{jaspr-paper-wide.jar,WideProbe.jar},
 * server/cache/patched_1.12.2.jar, server/plugins/EaglerXServer.jar, candidate/tanks/JasprTanks.jar and
 * candidate/tank-client/jaspercraft-mobile-controls.{js,css} (copies of site/).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const mobile = process.argv.includes('--mobile');
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'wide-browser'));
const NAME = 'WideTester';
const W = mobile ? 740 : 960, H = mobile ? 360 : 540;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 500) : ''));
}
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));

// A test copy of the candidate client that lets the probe reach the Minecraft instance.
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(path.join(ROOT, 'candidate', 'wide-client', 'classes.js'), 'latin1');
if (!candidate.includes('$rt_globals.JasprWideBridge = {')) throw new Error('candidate has no wide inventory stage');
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
  // ---- fixture ---------------------------------------------------------------------------------------------------
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: path.join(LIVE, 'site', 'assets.epk'),
      TANK_PREVIEW_COPY: 'candidate/wide-server/jaspr-paper-wide.jar=>paper.jar;candidate/wide-server/WideProbe.jar=>plugins/WideProbe.jar'})});
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 240000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2000));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  check(!/VerifyError|NoSuchFieldError|NoSuchMethodError/.test(fxOut), 'patched server classes load');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(200); return t; };
  const inv = async () => {
    const lines = async () => ((await getText(webPort, '/log')).match(/WPROBE_INV[^\n]*/g) || []);
    const before = (await lines()).length;
    await cmd('wprobe inv ' + NAME);
    let all = await lines();
    for (let i = 0; i < 30 && all.length <= before; i++) { await sleep(200); all = await lines(); }
    const line = all[all.length - 1] || '', items = {};
    for (const part of ((line.match(/items=(\S*)/) || [])[1] || '').split(',').filter(Boolean)) { const [i, r] = part.split(':'); items[+i] = r; }
    return {line, items, held: +((line.match(/held=(-?\d+)/) || [])[1]), window: +((line.match(/window=(\d+)/) || [])[1]), open: +((line.match(/open=(\d+)/) || [])[1])};
  };

  // ---- browser ---------------------------------------------------------------------------------------------------
  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-wide-probe-'));
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
  await send('Emulation.setDeviceMetricsOverride', {width: W, height: H, deviceScaleFactor: 1, mobile, screenOrientation: {type: 'landscapePrimary', angle: mobile ? 90 : 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;setTimeout(function(){document.dispatchEvent(new Event('pointerlockchange'));},0);return Promise.resolve();};Document.prototype.exitPointerLock=function(){locked=null;setTimeout(function(){document.dispatchEvent(new Event('pointerlockchange'));},0);};})();"});
  const video = {version: 1, tier: -1, auto: false, values: {renderDistance: 2, maxFps: 15, fancyGraphics: 0, ao: 0, particles: 2, clouds: 0, entityShadows: 0, vsync: 0,
    mipmaps: 0, chunkUpdates: 1, fog: 1, viewBobbing: 0, resolution: 100, chunkBudget: 1, entityDistance: 32, fastVisibility: 1, animations: 0, ambientEffects: 0,
    weatherEffects: 0, particleEffects: 0, dynamicLights: 0, gore: 0, shader: 0, dhEnabled: 0, showFps: 0, showCoords: 0, mobileControls: 1, touchSensitivity: 100}};
  await send('Page.addScriptToEvaluateOnNewDocument', {source: 'try{localStorage.setItem("jaspr.video.v1",' + JSON.stringify(JSON.stringify(video)) + ');}catch(e){}'});
  await send('Page.navigate', {url: url + '#' + NAME});
  const shot = async name => {
    const r = await Promise.race([send('Page.captureScreenshot', {format: 'png'}), sleep(20000).then(() => null)]);
    if (r && r.result) fs.writeFileSync(path.join(out, name + '.png'), Buffer.from(r.result.data, 'base64'));
    console.log('shot ' + name + (r && r.result ? '' : ' TIMEOUT'));
  };
  const key = async (code, keyCode, k, shift) => {
    const mods = shift ? 8 : 0;
    await send('Input.dispatchKeyEvent', {type: 'keyDown', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode, modifiers: mods}); await sleep(90);
    await send('Input.dispatchKeyEvent', {type: 'keyUp', code, key: k, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode, modifiers: mods}); await sleep(500);
  };
  const click = async (x, y, shift) => {
    const mods = shift ? 8 : 0;
    await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x, y, modifiers: mods}); await sleep(80);
    await send('Input.dispatchMouseEvent', {type: 'mousePressed', x, y, button: 'left', clickCount: 1, modifiers: mods}); await sleep(90);
    await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x, y, button: 'left', clickCount: 1, modifiers: mods}); await sleep(450);
  };
  // A phone tap: the touch controls turn it into the menu's mouse press and release at that point.
  const tap = async (x, y) => {
    await send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [{x, y, id: 2}]}); await sleep(90);
    await send('Input.dispatchTouchEvent', {type: 'touchEnd', touchPoints: []}); await sleep(450);
  };
  const press = mobile ? tap : click;
  const shiftDown = () => send('Input.dispatchKeyEvent', {type: 'rawKeyDown', code: 'ShiftLeft', key: 'Shift', windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16, modifiers: 8});
  const shiftUp = () => send('Input.dispatchKeyEvent', {type: 'keyUp', code: 'ShiftLeft', key: 'Shift', windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16});
  /** The open screen: window position, real width, CSS px per GUI px, and every slot (index, window number, position, item). */
  const screen = () => evaluate(`(function(){try{var m=__wideMc(),g=m.cj;if(!g||!g.h2)return null;var l=g.h2.cn,s=[];
    for(var k=0;k<l.g;k++){var x=l.qN.data[k];s.push({n:x.pO,i:x.bQx,x:x.Lr,y:x.Fg,jw:x.$jw||0});}
    var rw=(g.q|0)+(g.$jwCut|0);return {is:g.is,l7:g.l7,q:g.q,cut:g.$jwCut|0,gv:g.gv,k:document.querySelector('#game_frame canvas').getBoundingClientRect().width/rw,slots:s};}catch(e){return {error:String(e)};}})()`);
  const at = (sc, n) => { const s = sc.slots.find(x => x.n === n); return [Math.round((sc.is + s.x + 8) * sc.k), Math.round((sc.l7 + s.y + 8) * sc.k)]; };

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__wideMc&&__wideMc()&&__wideMc().v);}catch(e){return false;}})()')) return true; await sleep(1000); } return false; };
    check(await inWorld(150000), 'client joins the fixture server');
    await sleep(3000);
    // Phones and tablets keep the vanilla 9-slot hotbar but get the larger inventory (owner, 2026-10-04).
    check(await evaluate('window.JasprWideBridge.slots()') === (mobile ? 9 : 14), 'server answered: ' + (mobile ? 9 : 14) + ' hotbar slots');
    let p = await inv();
    check(p.window === 66, 'server: inventory window widened to 66 slots', p.line);
    await cmd('gamemode 0 ' + NAME);
    await cmd('wprobe clear ' + NAME);
    const names = ['stone', 'dirt', 'cobblestone', 'planks', 'sand', 'gravel', 'log', 'glass', 'wool', 'brick_block', 'bookshelf', 'obsidian', 'ice', 'clay', 'pumpkin'];
    for (const n of names) await cmd('give ' + NAME + ' minecraft:' + n + ' 1');
    await sleep(1500);
    const clientItems = () => evaluate('(function(){var e=__wideMc().v.bx.eL,o={};for(var i=0;i<56;i++){var s=e.byz.c4(i);if(s&&s.rA&&s.PD>0)o[i]=s.PD;}return o;})()');
    const ci = await clientItems();
    check(ci && ci[36] === 1 && ci[40] === 1 && ci[9] === 1, 'client inventory holds items 36-40 and row 1', ci);
    if (!mobile) {
      await evaluate('window.JasprWideBridge.select(11)');
      await sleep(1200);
      p = await inv();
      check(p.held === 38, 'hotbar position 12 selected in the client -> server holds item 38', p.line);
      await shot('hud-14-slots');
    } else {
      await shot('hud-9-slots');
      const bar = await evaluate(`(function(){var b=document.querySelector('.jaspr-touch-slots');if(!b)return null;var v=[].slice.call(b.children).filter(function(x){return getComputedStyle(x).display!=='none';});var r=b.getBoundingClientRect();return {n:v.length,slots:b.dataset.slots,left:r.left,right:r.right,w:innerWidth};})()`);
      check(bar && bar.n === 9 && bar.slots === '9' && bar.left >= 0 && bar.right <= bar.w, 'touch hotbar: the vanilla 9 buttons', bar);
      const btn = await evaluate(`(function(){var b=document.querySelectorAll('.jaspr-touch-slots button')[7].getBoundingClientRect();return [b.left+b.width/2,b.top+b.height/2];})()`);
      await send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [{x: btn[0], y: btn[1], id: 1}]}); await sleep(90);
      await send('Input.dispatchTouchEvent', {type: 'touchEnd', touchPoints: []}); await sleep(1200);
      p = await inv();
      check(p.held === 7, 'touch button 8 selects hotbar slot 8', p.line);
      // A held slot from the 14-slot hotbar (saved on a computer) comes back to slot 1 on a phone.
      await cmd('wprobe held ' + NAME + ' 38');
      await sleep(1500);
      p = await inv();
      check(p.held === 0 && await evaluate('__wideMc().v.bx.gP') === 0, 'held item 38 from a computer goes back to slot 1 on a phone', p.line);
    }
    // ---- inventory screen ----
    await key('KeyE', 69, 'e');
    await sleep(1500);
    let sc = await screen();
    check(sc && sc.slots && sc.slots.length === 66 && sc.slots.filter(s => s.jw === 1).length === 20, 'inventory screen: 66 slots, 20 in the pocket', sc && (sc.error || sc.slots.length));
    // One grid: hotbar 10 (item 36) one slot pitch after hotbar 9 (item 8), row 1's extension after its 9th slot (item 17).
    const by = i => sc && sc.slots.find(s => s.i === i && (i < 36 ? !s.jw : s.jw === 1));
    check(sc && by(36).x === by(8).x + 18 && by(36).y === by(8).y && by(41).x === by(17).x + 18 && by(41).y === by(17).y && by(55).x === by(8).x + 90,
      'extension columns continue the rows and the hotbar', sc && [by(8), by(36), by(17), by(41), by(55)].map(s => s && [s.x, s.y]));
    const realW = sc.q + sc.cut;
    check(Math.abs((sc.is + (sc.gv + 90) / 2) - realW / 2) <= 1, 'the 266px window is centred on the screen', [sc.is, sc.gv, realW]);
    await shot('inventory');
    // A click on hotbar 10 (window 46) picks the brick up; a click on row 2 slot 3 (window 20) puts it down.
    await press(...at(sc, 46));
    await press(...at(sc, 20));
    await sleep(800);
    p = await inv();
    check(p.items[20] === 'BRICKx1' && !p.items[36], (mobile ? 'tap' : 'click') + ' pocket slot then a row slot: brick moved 36 -> 20', p.line);
    // Shift-click from the pocket (row 1 extension, window 51) goes to the first free hotbar slot (hotbar 10, item 36).
    await cmd('wprobe set ' + NAME + ' 41 EMERALD 3');
    await sleep(800);
    await shiftDown(); await click(...at(sc, 51), true); await shiftUp();
    await sleep(800);
    p = await inv();
    const c2 = await clientItems();
    check(p.items[36] === 'EMERALDx3' && !p.items[41], 'shift-click row 1 extension -> hotbar 10 (server)', p.line);
    check(c2 && c2[36] === 3 && !c2[41], 'client agrees after shift-click', c2);
    // Carrying a stack, a click on the pocket frame keeps it (no drop); it goes back to its slot.
    await click(...at(sc, 36));
    const sc2 = await screen();
    await click(Math.round((sc2.is + 250) * sc2.k), Math.round((sc2.l7 + 30) * sc2.k));   // the widened window's empty top-right
    await click(...at(sc2, 36));
    await sleep(800);
    p = await inv();
    check(p.items[0] === 'STONEx1', 'a click on the widened part of the window does not drop the carried stack', p.line);
    if (mobile) {
      // Expand / Contract in the touch menu bar (phones and tablets only).
      const wideButton = () => evaluate(`(function(){var b=document.querySelector('[data-zone="wideview"]');if(!b)return null;var r=b.getBoundingClientRect();return {shown:getComputedStyle(b).display!=='none',text:b.textContent,x:r.left+r.width/2,y:r.top+r.height/2};})()`);
      const tapAt = async (x, y) => { await send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [{x, y, id: 3}]}); await sleep(90); await send('Input.dispatchTouchEvent', {type: 'touchEnd', touchPoints: []}); await sleep(1200); };
      let b = await wideButton();
      check(b && b.shown && b.text === 'Contract', 'menu bar shows Contract over the inventory', b);
      await tapAt(b.x, b.y);
      const small = await screen();
      b = await wideButton();
      check(small && small.cut === 0 && small.slots.filter(x => x.jw).every(x => x.x === -2000) && /^Expand \(\+\d+\)$/.test(b.text),
        'Contract: the vanilla window, the extension slots hidden, Expand counts what they hold', [small && small.cut, b && b.text]);
      await shot('inventory-contracted');
      await tapAt(b.x, b.y);
      const big = await screen();
      b = await wideButton();
      check(big && big.cut === 90 && big.slots.filter(x => x.jw === 1).every(x => x.x >= 170) && b.text === 'Contract', 'Expand: the 266px window again', [big && big.cut, b && b.text]);
    }
    await key('Escape', 27, 'Escape');
    // ---- chest window ----
    await cmd('wprobe chest ' + NAME);
    await sleep(1500);
    sc = await screen();
    check(sc && sc.slots && sc.slots.length === 83, 'chest screen: 27 + 36 + 20 slots', sc && (sc.error || sc.slots.length));
    p = await inv();
    check(p.open === 83, 'server chest window has 83 slots', p.line);
    await shot('chest');
    await click(...at(sc, 27 + 36 + 4));    // pocket: hotbar 14 (item 40, clay) picked up ...
    await click(...at(sc, 0));               // ... into the chest's first slot
    await sleep(800);
    const chestLog = await getText(webPort, '/log');
    check(/WPROBE_CLICK raw=0 slot=0 type=CONTAINER top=true/.test(chestLog) && /WPROBE_CLICK raw=67 slot=40 type=QUICKBAR/.test(chestLog), 'chest clicks: pocket raw 67 = item 40, chest raw 0');
    p = await inv();
    check(!p.items[40], 'clay left the pocket for the chest', p.line);
    await key('Escape', 27, 'Escape');
    await cmd('wprobe furnace ' + NAME);
    await sleep(1500);
    sc = await screen();
    check(sc && sc.slots && sc.slots.length === 3 + 36 + 20, 'furnace screen: 3 + 36 + 20 slots', sc && (sc.error || sc.slots.length));
    await shot('furnace');
    await key('Escape', 27, 'Escape');
    // ---- Creative ----
    await cmd('gamemode 1 ' + NAME);
    await sleep(800);
    await key('KeyE', 69, 'e');
    await sleep(1500);
    sc = await screen();
    check(sc && sc.slots && sc.slots.filter(s => s.jw === 1).length === 5 && sc.slots.filter(s => s.jw === 1).every(s => s.x >= 193 && s.y === 112),
      'Creative item tab: the hotbar extension (5 slots) right of the scrollbar', sc && (sc.error || sc.slots.filter(s => s.jw).map(s => [s.x, s.y])));
    await shot('creative');
    await key('Escape', 27, 'Escape');
    const errors = consoleLog.filter(l => /EXCEPTION|wide inventory\]/.test(l));
    check(errors.length === 0, 'no page exceptions or wide-inventory warnings', errors.slice(0, 3));
    const serverLog = await getText(webPort, '/log');
    check(!/Could not pass event|invalid carried item|JasprWide[^\n]*Exception|at net\.minecraft\.server\.v1_12_R1\.JasprWide/.test(serverLog),
      'no server errors from the wide inventory');
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'server.txt'), await getText(webPort, '/log').catch(() => ''));
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'WIDE_BROWSER_FAIL ' : 'WIDE_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('WIDE_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('WIDE_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 9 * 60 * 1000).unref();
