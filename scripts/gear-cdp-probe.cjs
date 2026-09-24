'use strict';
// Disposable headless-Chrome driver for the loopback gear fixture (scripts/gear-preview.cjs).
// Never touches the user's browser profile or production files. Usage:
//   node scripts/gear-cdp-probe.cjs <steps.json> <outDir> [url] [debugPort]
// steps: ["wait",ms] ["key","e"] ["keydown","ShiftLeft"] ["keyup","ShiftLeft"] ["click",x,y,button?]
//        ["move",x,y] ["shot","name"] ["eval","js expression"]   (x/y in CSS pixels, 1024x576 page)
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const [stepsFile, outDir, url = 'http://127.0.0.1:25598/', portArg = String(9300 + Math.floor(Math.random() * 600))] = process.argv.slice(2);
const steps = JSON.parse(fs.readFileSync(stepsFile, 'utf8'));
const debugPort = Number(portArg);
fs.mkdirSync(outDir, {recursive: true});
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-gear-probe-'));
const child = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--no-sandbox', '--use-angle=swiftshader',
  '--enable-unsafe-swiftshader', '--no-first-run', '--no-default-browser-check', '--window-size=1024,576', '--force-device-scale-factor=1',
  '--remote-debugging-port=' + debugPort, '--user-data-dir=' + profile, 'about:blank'], {windowsHide: true, stdio: 'ignore'});
const getJson = p => new Promise((res, rej) => http.get({host: '127.0.0.1', port: debugPort, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => { try { res(JSON.parse(d)); } catch (e) { rej(e); } }); }).on('error', rej));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const KEYS = {e: ['KeyE', 69, 'e'], g: ['KeyG', 71, 'g'], h: ['KeyH', 72, 'h'], j: ['KeyJ', 74, 'j'], Escape: ['Escape', 27, 'Escape'],
  ShiftLeft: ['ShiftLeft', 16, 'Shift'], t: ['KeyT', 84, 't'], Enter: ['Enter', 13, 'Enter'], w: ['KeyW', 87, 'w'], space: ['Space', 32, ' '], '1': ['Digit1', 49, '1'], '2': ['Digit2', 50, '2'], '3': ['Digit3', 51, '3'], '4': ['Digit4', 52, '4'], '5': ['Digit5', 53, '5']};
(async () => {
  let page;
  for (let i = 0; i < 100 && !page; i++) { try { page = (await getJson('/json/list')).find(t => t.type === 'page'); } catch (e) { } if (!page) await sleep(100); }
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  let seq = 0; const pending = new Map(), console_ = [];
  ws.addEventListener('message', ev => { const m = JSON.parse(ev.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    if (m.method === 'Runtime.consoleAPICalled') console_.push(m.params.type + ': ' + m.params.args.map(a => a.value || a.description || '').join(' ').slice(0, 300));
    if (m.method === 'Runtime.exceptionThrown') console_.push('EXCEPTION: ' + JSON.stringify(m.params.exceptionDetails).slice(0, 600)); });
  await new Promise(r => ws.addEventListener('open', r, {once: true}));
  const send = (method, params = {}) => new Promise(r => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({id, method, params})); });
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width: 1024, height: 576, deviceScaleFactor: 1, mobile: false});
  // Headless Chrome refuses pointer lock and the game would pause on every click: emulate it.
  await send('Page.addScriptToEvaluateOnNewDocument', {source: '(function(){var locked=null;Object.defineProperty(Document.prototype,\'pointerLockElement\',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;setTimeout(function(){document.dispatchEvent(new Event(\'pointerlockchange\'));},0);return Promise.resolve();};Document.prototype.exitPointerLock=function(){locked=null;setTimeout(function(){document.dispatchEvent(new Event(\'pointerlockchange\'));},0);};Document.prototype.hasFocus=function(){return true;};})();'});
  await send('Page.navigate', {url});
  const log = [];
  let mx = 512, my = 288;
  for (const step of steps) {
    const [op, a, b, c] = step;
    if (op === 'wait') await sleep(a);
    else if (op === 'key' || op === 'keydown' || op === 'keyup') {
      const [code, keyCode, key] = KEYS[a] || ['Key' + a.toUpperCase(), a.toUpperCase().charCodeAt(0), a];
      if (op !== 'keyup') await send('Input.dispatchKeyEvent', {type: 'keyDown', code, key, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode, text: key.length === 1 ? key : undefined});
      if (op === 'key') await sleep(60);
      if (op !== 'keydown') await send('Input.dispatchKeyEvent', {type: 'keyUp', code, key, windowsVirtualKeyCode: keyCode, nativeVirtualKeyCode: keyCode});
    } else if (op === 'move') { mx = a; my = b; await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: a, y: b}); }
    else if (op === 'click') {
      const button = c === 1 ? 'right' : 'left'; mx = a; my = b;
      await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: a, y: b});
      await sleep(40);
      await send('Input.dispatchMouseEvent', {type: 'mousePressed', x: a, y: b, button, clickCount: 1});
      await sleep(80);
      await send('Input.dispatchMouseEvent', {type: 'mouseReleased', x: a, y: b, button, clickCount: 1});
    } else if (op === 'shot') {
      const r = await send('Page.captureScreenshot', {format: 'png'});
      fs.writeFileSync(path.join(outDir, a + '.png'), Buffer.from(r.result.data, 'base64'));
      log.push('shot ' + a);
    } else if (op === 'text') {
      await send('Input.insertText', {text: a});
    } else if (op === 'cmd') {
      const fx = process.env.GEAR_FIXTURE, logText = fs.readFileSync(path.join(fx, 'paper.log'), 'utf8');
      const names = [...logText.matchAll(/UUID of player (\S+) is/g)].map(m => m[1]);
      const line = a.replace('{name}', names[names.length - 1] || 'nobody');
      fs.appendFileSync(path.join(fx, 'commands.txt'), line + '\n');
      log.push('cmd ' + line);
    } else if (op === 'eval') {
      const r = await send('Runtime.evaluate', {expression: a, returnByValue: true});
      log.push('eval ' + JSON.stringify(r.result.result ? r.result.result.value : r.result).slice(0, 1500));
    }
  }
  fs.writeFileSync(path.join(outDir, 'log.txt'), log.concat(['--- console ---'], console_.slice(-60)).join('\n') + '\n');
  console.log(log.concat(['--- console (last 20) ---'], console_.slice(-20)).join('\n'));
  ws.close(); kill();
  setTimeout(() => { try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { } process.exit(0); }, 500);
})().catch(e => { console.error(e); kill(); process.exit(1); });
function kill() {
  // headless=new detaches its browser processes from the launcher: stop exactly this run's profile.
  const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
  const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in (@($ps | Where-Object { $_.CommandLine -notmatch '--type=' }) + @($ps))){ if($p){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue } }; Start-Sleep 2 }";
  try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
}
setTimeout(() => { console.error('probe timeout'); kill(); process.exit(3); }, Number(process.env.PROBE_TIMEOUT || 150000)).unref();
