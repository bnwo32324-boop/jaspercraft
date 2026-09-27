'use strict';
// Disposable headless-Chrome driver for the loopback tank fixture (scripts/tank-preview.cjs), emulating a touch
// phone (Android user agent, touch points, landscape viewport). Never touches the user's browser profile.
// Usage: node scripts/tank-cdp-probe.cjs <steps.json> <outDir> <fixtureUrl> [width] [height]   (TANK_PROBE_DESKTOP=1: mouse/keyboard desktop)
// steps: ["wait",ms] ["tap",x,y] ["hold",x,y,ms] ["drag",x1,y1,x2,y2,ms] ["key",code,keyCode,key] ["shot","name"]
//        ["eval","js expression"] ["cmd","server command with {name}"] ["rclick",x,y]   (x/y in CSS pixels)
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const [stepsFile, outDir, url, w = '740', h = '360'] = process.argv.slice(2);
const width = Number(w), height = Number(h);
const steps = JSON.parse(fs.readFileSync(stepsFile, 'utf8'));
const debugPort = 9300 + Math.floor(Math.random() * 600);
fs.mkdirSync(outDir, {recursive: true});
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-tank-probe-'));
// Started below normal priority (children inherit it) and stripped of audio, extensions and background services.
const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--no-sandbox',
  '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check', '--mute-audio', '--disable-extensions',
  '--disable-background-networking', '--disable-sync', '--disable-default-apps', '--disable-component-update', '--renderer-process-limit=1',
  '--disable-features=Translate,MediaRouter,OptimizationHints', '--window-size=' + width + ',' + height, '--force-device-scale-factor=1',
  '--remote-debugging-port=' + debugPort, '--user-data-dir=' + profile, 'about:blank'], {windowsHide: true, stdio: 'ignore'});
try { os.setPriority(chrome.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
const getText = (host, port, p) => new Promise((res, rej) => http.get({host, port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const fixture = new URL(url);
(async () => {
  let page;
  for (let i = 0; i < 100 && !page; i++) { try { page = JSON.parse(await getText('127.0.0.1', debugPort, '/json/list')).find(t => t.type === 'page'); } catch (e) { } if (!page) await sleep(100); }
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  let seq = 0; const pending = new Map(), console_ = [];
  ws.addEventListener('message', ev => { const m = JSON.parse(ev.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    if (m.method === 'Runtime.consoleAPICalled') console_.push(m.params.type + ': ' + m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300));
    if (m.method === 'Runtime.exceptionThrown') console_.push('EXCEPTION: ' + JSON.stringify(m.params.exceptionDetails).slice(0, 600)); });
  await new Promise(r => ws.addEventListener('open', r, {once: true}));
  const send = (method, params = {}) => new Promise(r => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({id, method, params})); });
  await send('Runtime.enable'); await send('Page.enable');
  const desktop = process.env.TANK_PROBE_DESKTOP === '1';
  if (!desktop) {
  await send('Emulation.setUserAgentOverride', {userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36', platform: 'Android'});
  await send('Emulation.setTouchEmulationEnabled', {enabled: true, maxTouchPoints: 5});
  await send('Emulation.setEmitTouchEventsForMouse', {enabled: true, configuration: 'mobile'});
  }
  await send('Emulation.setDeviceMetricsOverride', {width, height, deviceScaleFactor: 1, mobile: !desktop, screenOrientation: {type: width > height ? 'landscapePrimary' : 'portraitPrimary', angle: width > height ? 90 : 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: '(function(){var locked=null;Object.defineProperty(Document.prototype,\'pointerLockElement\',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;setTimeout(function(){document.dispatchEvent(new Event(\'pointerlockchange\'));},0);return Promise.resolve();};Document.prototype.exitPointerLock=function(){locked=null;setTimeout(function(){document.dispatchEvent(new Event(\'pointerlockchange\'));},0);};})();'});
  // Lightest settings the client allows (custom preset): 10% resolution, render distance 2, 15 fps, effects off.
  const video = {version: 1, tier: -1, auto: false, values: {renderDistance: 2, maxFps: 15, fancyGraphics: 0, ao: 0, particles: 2, clouds: 0,
    entityShadows: 0, vsync: 0, mipmaps: 0, chunkUpdates: 1, fog: 1, viewBobbing: 0, resolution: 10, chunkBudget: 1, entityDistance: 32,
    fastVisibility: 1, animations: 0, ambientEffects: 0, weatherEffects: 0, particleEffects: 0, dynamicLights: 0, gore: 0, shader: 0,
    dhEnabled: 0, showFps: 1, showCoords: 1, mobileControls: 1, touchSensitivity: 100}};
  await send('Page.addScriptToEvaluateOnNewDocument', {source: 'try{localStorage.setItem("jaspr.video.v1",' + JSON.stringify(JSON.stringify(video)) + ');}catch(e){}'});
  await send('Page.navigate', {url});
  const log = [];
  const touch = (type, points) => send('Input.dispatchTouchEvent', {type, touchPoints: points.map(([x, y], id) => ({x, y, id}))});
  for (const step of steps) {
    const [op, a, b, c, d, e] = step;
    if (op === 'wait') await sleep(a);
    else if (op === 'tap' || op === 'hold') {
      await touch('touchStart', [[a, b]]); await sleep(op === 'hold' ? c : 90); await touch('touchEnd', []);
      log.push(op + ' ' + a + ',' + b);
    } else if (op === 'drag') {
      await touch('touchStart', [[a, b]]);
      const n = 6; for (let i = 1; i <= n; i++) { await sleep(40); await touch('touchMove', [[a + (c - a) * i / n, b + (d - b) * i / n]]); }
      await sleep(e || 1000); await touch('touchEnd', []);
      log.push('drag ' + [a, b, c, d, e].join(','));
    } else if (op === 'dragstart') {
      await touch('touchStart', [[a, b]]);
      for (let i = 1; i <= 6; i++) { await sleep(40); await touch('touchMove', [[a + (c - a) * i / 6, b + (d - b) * i / 6]]); }
      log.push('dragstart ' + [a, b, c, d].join(','));
    } else if (op === 'dragend') {
      await touch('touchEnd', []); log.push('dragend');
    } else if (op === 'click') {
      await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: a, y: b}); await sleep(60);
      await send('Input.dispatchMouseEvent', {type: 'mousePressed', x: a, y: b, button: 'left', clickCount: 1}); await sleep(90);
      await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x: a, y: b, button: 'left', clickCount: 1});
      log.push('click ' + a + ',' + b);
    } else if (op === 'rclick') {
      await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: a, y: b}); await sleep(40);
      await send('Input.dispatchMouseEvent', {type: 'mousePressed', x: a, y: b, button: 'right', clickCount: 1}); await sleep(90);
      await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x: a, y: b, button: 'right', clickCount: 1});
      log.push('rclick ' + a + ',' + b);
    } else if (op === 'key') {
      await send('Input.dispatchKeyEvent', {type: 'keyDown', code: a, key: c, windowsVirtualKeyCode: b, nativeVirtualKeyCode: b});
      await sleep(80);
      await send('Input.dispatchKeyEvent', {type: 'keyUp', code: a, key: c, windowsVirtualKeyCode: b, nativeVirtualKeyCode: b});
    } else if (op === 'shot') {
      const r = await send('Page.captureScreenshot', {format: 'jpeg', quality: 60});
      fs.writeFileSync(path.join(outDir, a + '.jpg'), Buffer.from(r.result.data, 'base64'));
      log.push('shot ' + a);
    } else if (op === 'cmd') {
      const logText = await getText(fixture.hostname, fixture.port, '/log');
      const names = [...logText.matchAll(/UUID of player (\S+) is/g)].map(m => m[1]);
      const line = a.split('{name}').join(names[names.length - 1] || 'nobody');
      const out = await getText(fixture.hostname, fixture.port, '/console?c=' + encodeURIComponent(line));
      log.push('cmd ' + line + ' => ' + out.trim().split('\n').slice(-1)[0]);
    } else if (op === 'eval') {
      const r = await send('Runtime.evaluate', {expression: a, returnByValue: true, awaitPromise: true});
      log.push('eval ' + JSON.stringify(r.result.result ? r.result.result.value : r.result).slice(0, 1500));
    }
  }
  fs.writeFileSync(path.join(outDir, 'log.txt'), log.concat(['--- console ---'], console_.slice(-60)).join('\n') + '\n');
  console.log(log.concat(['--- console (last 15) ---'], console_.slice(-15)).join('\n'));
  ws.close(); kill();
  setTimeout(() => { try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { } process.exit(0); }, 500);
})().catch(e => { console.error(e); kill(); process.exit(1); });
function kill() {
  // headless=new detaches its browser processes from the launcher: stop exactly this run's profile.
  const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
  const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in (@($ps | Where-Object { $_.CommandLine -notmatch '--type=' }) + @($ps))){ if($p){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue } }; Start-Sleep 2 }";
  try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
}
setTimeout(() => { console.error('probe timeout'); kill(); process.exit(3); }, Number(process.env.PROBE_TIMEOUT || 240000)).unref();
