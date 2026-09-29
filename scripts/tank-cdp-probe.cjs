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
    if (m.method === 'Runtime.exceptionThrown') console_.push('EXCEPTION: ' + JSON.stringify(m.params.exceptionDetails).slice(0, 600));
    if (m.method === 'Inspector.targetCrashed' || m.method === 'Inspector.detached') console_.push('PAGE ' + m.method + ' ' + JSON.stringify(m.params || {})); });
  await new Promise(r => ws.addEventListener('open', r, {once: true}));
  const send = (method, params = {}) => new Promise(r => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({id, method, params})); });
  await send('Runtime.enable'); await send('Page.enable');
  // TANK_PROBE_DEBUG=1: debugger on from the start, so a later ["stack"] can interrupt a page stuck in a loop
  if (process.env.TANK_PROBE_DEBUG === '1') { await send('Debugger.enable'); ws.debugging = true; }
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
  // TANK_PROBE_VIDEO: JSON of values to override, to match a player's settings (e.g. {"dynamicLights":1}).
  if (process.env.TANK_PROBE_VIDEO) Object.assign(video.values, JSON.parse(process.env.TANK_PROBE_VIDEO));
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
      // a frozen page never answers: give up on the picture after 20 s and say so
      const r = await Promise.race([send('Page.captureScreenshot', {format: 'jpeg', quality: 60}), sleep(20000).then(() => null)]);
      if (r && r.result) fs.writeFileSync(path.join(outDir, a + '.jpg'), Buffer.from(r.result.data, 'base64'));
      log.push('shot ' + a + (r && r.result ? '' : ' TIMEOUT (page not answering)'));
      if (!(r && r.result)) log.push(procs());
    } else if (op === 'cmd') {
      const logText = await getText(fixture.hostname, fixture.port, '/log');
      const names = [...logText.matchAll(/UUID of player (\S+) is/g)].map(m => m[1]);
      const line = a.split('{name}').join(names[names.length - 1] || 'nobody');
      const out = await getText(fixture.hostname, fixture.port, '/console?c=' + encodeURIComponent(line));
      log.push('cmd ' + line + ' => ' + out.trim().split('\n').slice(-1)[0]);
    } else if (op === 'eval') {
      const r = await send('Runtime.evaluate', {expression: a, returnByValue: true, awaitPromise: true});
      log.push('eval ' + JSON.stringify(r.result.result ? r.result.result.value : r.result).slice(0, 1500));
    } else if (op === 'stack') {
      // where the page's main thread is right now (works even when a busy loop keeps the page from answering)
      if (!ws.debugging) ws.debugging = await Promise.race([send('Debugger.enable').then(() => true), sleep(10000).then(() => false)]);
      const paused = new Promise(r => { const h = ev => { const m = JSON.parse(ev.data); if (m.method === 'Debugger.paused') { ws.removeEventListener('message', h); r(m.params); } }; ws.addEventListener('message', h); });
      if (ws.debugging) send('Debugger.pause');
      const p = ws.debugging ? await Promise.race([paused, sleep(15000).then(() => null)]) : null;
      if (!p) { log.push('stack ' + (a || '') + ' TIMEOUT (JavaScript not interruptible: blocked in native or GPU code)'); log.push(procs()); }
      else {
        const lines = [];
        for (const f of p.callFrames.slice(0, 14)) {
          let snippet = '';
          try {
            ws.sources = ws.sources || {};
            if (!ws.sources[f.location.scriptId]) {
              const src = await send('Debugger.getScriptSource', {scriptId: f.location.scriptId});
              ws.sources[f.location.scriptId] = ((src.result && src.result.scriptSource) || '').split('\n');
            }
            const row = ws.sources[f.location.scriptId][f.location.lineNumber] || '';
            snippet = row.slice(Math.max(0, f.location.columnNumber - 90), f.location.columnNumber + 60).replace(/\s+/g, ' ');
          } catch (e) { }
          lines.push('  ' + (f.functionName || '?') + ' @' + f.location.lineNumber + ':' + f.location.columnNumber + '  ' + snippet);
        }
        log.push('stack ' + (a || '') + '\n' + lines.join('\n'));
        // ["stack", label, functionName, "expr1,expr2"]: read variables of that frame while paused
        const fr = b ? p.callFrames.find(f => f.functionName === b) : null;
        if (fr && c) for (const expr of String(c).split(',')) {
          const r = await send('Debugger.evaluateOnCallFrame', {callFrameId: fr.callFrameId, expression: expr, returnByValue: false});
          const v = r.result && r.result.result;
          log.push('  ' + b + '.' + expr + ' = ' + (v ? (v.description !== undefined ? v.description : JSON.stringify(v.value)) + ' (' + v.type + (v.className ? ' ' + v.className : '') + ')' : JSON.stringify(r.error || r.result).slice(0, 200)));
        }
        await send('Debugger.resume');
      }
    }
    // the log is kept current, so a run cut short still says how far it got and what the page printed
    fs.writeFileSync(path.join(outDir, 'log.txt'), log.concat(['--- console ---'], console_.slice(-60)).join('\n') + '\n');
  }
  fs.writeFileSync(path.join(outDir, 'log.txt'), log.concat(['--- console ---'], console_.slice(-60)).join('\n') + '\n');
  console.log(log.concat(['--- console (last 15) ---'], console_.slice(-15)).join('\n'));
  ws.close(); kill();
  setTimeout(() => { try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { } process.exit(0); }, 500);
})().catch(e => { console.error(e); kill(); process.exit(1); });
function procs() {
  // this run's Chrome processes (type, CPU seconds, memory): tells a busy renderer from a stuck GPU process or a crash
  const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
  const ps = "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' } | ForEach-Object { $t = if ($_.CommandLine -match '--type=([a-z-]+)') { $Matches[1] } else { 'browser' }; $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue; if ($p) { '  ' + $t + ' pid=' + $_.ProcessId + ' cpu=' + [math]::Round($p.CPU,1) + 's ws=' + [math]::Round($p.WorkingSet64/1MB) + 'MB' } }";
  try { return 'procs\n' + spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 30000, encoding: 'utf8'}).stdout.trimEnd(); } catch (e) { return 'procs ?'; }
}
function kill() {
  // headless=new detaches its browser processes from the launcher: stop exactly this run's profile.
  const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
  const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in (@($ps | Where-Object { $_.CommandLine -notmatch '--type=' }) + @($ps))){ if($p){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue } }; Start-Sleep 2 }";
  try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
}
setTimeout(() => {
  console.error('probe timeout');
  kill();
  try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { }   // a frozen page's run leaves no profile behind
  process.exit(3);
}, Number(process.env.PROBE_TIMEOUT || 240000)).unref();
