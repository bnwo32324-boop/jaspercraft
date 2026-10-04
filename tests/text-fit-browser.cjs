'use strict';
/* Text that always fits, in the real TeaVM client (client-mods/text-fit-teavm.js): one headless Chrome (below normal
 * priority) against the loopback fixture (scripts/tank-preview.cjs) with the wide-inventory server jar and the test-only
 * WideProbe plugin. A helmet with a long lore line sits in hotbar slot 14 (the far right of the widened window, as in the
 * owner's screenshot); hovering it in the survival inventory and in Creative must show the whole tooltip on screen.
 * A long title, subtitle and action bar must fit the screen too. Screenshots go to the out folder.
 *
 *   node tests/text-fit-browser.cjs [out-dir] [--mobile]
 * Needs candidate/text-fit-client/classes.js (node scripts/build-text-fit-client.cjs on the live client) and the same
 * fixture files as tests/wide-inventory-browser.cjs.
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const mobile = process.argv.includes('--mobile');
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'text-fit-browser'));
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
const candidate = fs.readFileSync(path.join(ROOT, 'candidate', 'text-fit-client', 'classes.js'), 'latin1');
if (!candidate.includes('$rt_globals.JasprWideBridge = {') || !candidate.includes('function JasprTextDraw(')) throw new Error('candidate lacks the wide inventory or text-fit stage');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace('$rt_globals.JasprWideBridge = {', '$rt_globals.__wideMc = function () { return HEH; }; $rt_globals.JasprTextFitStatus = function () { return JasprTextFit.status(); }; $rt_globals.JasprWideBridge = {'), 'latin1'));

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
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-textfit-probe-'));
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
    const fit = () => evaluate('JSON.stringify((function(){try{return JasprTextFitStatus();}catch(e){return {error:String(e)};}})())').then(t => JSON.parse(t || '{}'));
    await cmd('gamemode 0 ' + NAME);
    await cmd('wprobe clear ' + NAME);
    // The screenshot's helmet: a long lore line, in hotbar slot 14 (item 40, the far right of the widened window).
    const lore = '&dEnder_Helm|&5Night_vision,_no_pearl_harm,_saved_once_from_the_void,_and_3%_of_every_blow_is_turned_aside_by_the_old_ward|&7When_on_head:|&9+3_Armor';
    await cmd('wprobe lore ' + NAME + ' 40 DIAMOND_HELMET ' + lore);
    await sleep(1200);
    const hover = async (x, y) => { await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: x - 3, y}); await sleep(120); await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x, y}); await sleep(900); };
    const onScreen = (st, label) => {
      const l = st && st.last;
      check(!!l && l.x >= 7 && l.x + l.w + 7 <= l.screen, label + ': the whole tooltip is on screen', l);
    };
    // ---- survival inventory ----
    await key('KeyE', 69, 'e');
    await sleep(1500);
    let sc = await screen();
    check(sc && sc.slots && sc.slots.length === 66, 'inventory screen open (66 slots)', sc && (sc.error || sc.slots.length));
    await hover(...at(sc, 50));
    let st = await fit();
    onScreen(st, 'survival inventory, hotbar slot 14');
    check(st.stats && st.stats.wrappedTooltips >= 1, 'the long lore line was wrapped', st.stats);
    await shot('tooltip-inventory');
    // The far left too (hotbar slot 1 holds nothing: move the helmet there).
    await key('Escape', 27, 'Escape');
    await cmd('wprobe lore ' + NAME + ' 0 DIAMOND_HELMET ' + lore);
    await sleep(800);
    await key('KeyE', 69, 'e');
    await sleep(1500);
    sc = await screen();
    await hover(...at(sc, 36));
    st = await fit();
    onScreen(st, 'survival inventory, hotbar slot 1');
    await shot('tooltip-inventory-left');
    await key('Escape', 27, 'Escape');
    // ---- Creative: the hotbar extension right of the scrollbar, as in the screenshot ----
    await cmd('gamemode 1 ' + NAME);
    await sleep(800);
    await key('KeyE', 69, 'e');
    await sleep(1500);
    sc = await screen();
    const creativeSlot = sc && sc.slots.find(x => x.jw === 1 && x.i === 40);
    check(!!creativeSlot, 'Creative shows hotbar slot 14', sc && (sc.error || sc.slots.length));
    if (creativeSlot) {
      await hover(Math.round((sc.is + creativeSlot.x + 8) * sc.k), Math.round((sc.l7 + creativeSlot.y + 8) * sc.k));
      st = await fit();
      onScreen(st, 'Creative, hotbar slot 14');
      await shot('tooltip-creative');
    }
    await key('Escape', 27, 'Escape');
    await cmd('gamemode 0 ' + NAME);
    // ---- title, subtitle, action bar ----
    const before = (await fit()).stats || {};
    await cmd('title ' + NAME + ' times 5 300 10');
    await cmd('title ' + NAME + ' subtitle {"text":"A ruin of black glass where the old fires still burn under the floor and nothing grows","color":"gray"}');
    await cmd('title ' + NAME + ' title {"text":"The Weeping Hollow of the Old Kings","color":"gold"}');
    await sleep(1500);
    st = await fit();
    const fits = (v, min, label) => check(!!v && v.width <= v.screen - 16 && v.scale >= min, label, v);
    check(st.stats && st.stats.scaled > (before.scaled | 0), 'the long title was scaled down', st.stats);
    fits(st.shown && st.shown[0], 2, 'title: every line within the screen, at 2x or larger');
    fits(st.shown && st.shown[1], 1, 'subtitle: every line within the screen, at 1x or larger');
    await shot('title');
    await cmd('title ' + NAME + ' clear');
    await cmd('title ' + NAME + ' actionbar {"text":"You hear the Ashen Vale stirring: the old wards are breaking and every gate of the realm now stands open","color":"yellow"}');
    await sleep(1200);
    st = await fit();
    fits(st.shown && st.shown[2], 1, 'action bar: every line within the screen');
    await shot('actionbar');
    const errors = consoleLog.filter(l => /EXCEPTION|text fit\]|wide inventory\]/.test(l));
    check(errors.length === 0, 'no page exceptions or text-fit warnings', errors.slice(0, 3));
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'server.txt'), await getText(webPort, '/log').catch(() => ''));
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'TEXTFIT_BROWSER_FAIL ' : 'TEXTFIT_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('TEXTFIT_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('TEXTFIT_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 9 * 60 * 1000).unref();
