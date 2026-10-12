'use strict';
/* FPS benchmark for the JasperCraft browser client (owner 2026-10-12: "use the FPS as a benchmark ... 165 frames consistently").
 *
 * One headless Chrome on this PC's REAL GPU (ANGLE/D3D11) with frame-rate limiting and GPU vsync off, below-normal priority,
 * against the loopback fixture (tank-preview: a lean single-core Paper running a copy of the LIVE server jar) whose world holds
 * a copy of the LIVE overworld's four spawn regions (real terrain and player builds). The client runs the desktop default
 * video preset ("Performance") exactly, except that the frame cap is lifted (VSync off, max FPS unlimited) so the benchmark
 * measures how many frames the client CAN draw. Nothing here is ever deployed: the timing probes go into a copy of classes.js.
 *
 * Scenes (each identical for every variant: fixed positions, commands, durations):
 *   spawn  - standing at the live spawn, the view sweeps 360 degrees (terrain, builds, distant terrain)
 *   horde  - 60 still mobs (zombies, skeletons, creepers, spiders, mutants) in front of the player at night
 *   gore   - the horde killed: death bursts, debris and particles
 *   fly    - flying forward over the terrain at sprint speed (chunk loading and rebuilding)
 * Per scene: frames, average FPS, frame-time p50/p95/p99, % of frames under 6.06 ms (165 FPS), CPU ms per frame (runGameLoop
 * start -> updateDisplay start), and with --profile a sampling CPU profile (top self time, mapped to Java names).
 *
 *   node tests/fps-bench.cjs --classes <classes.js> [--assets <assets.epk>] [--out <dir>] [--width 1920 --height 1080]
 *        [--scenes spawn,horde,gore,fly] [--seconds 15] [--profile] [--label name]
 * PC safety: run alone (one heavy process at a time); everything it starts is stopped at the end.
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const FIXTURE_ROOT = path.join(ROOT, 'candidate', 'fps-root');
const argv = process.argv.slice(2);
const opt = (k, d) => { const i = argv.indexOf('--' + k); return i < 0 ? d : (argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : true); };
const CLASSES = path.resolve(opt('classes', LIVE + '/site/classes.js'));
const VARIANTS = String(opt('variants', CLASSES)).split(',').map(p => path.resolve(p));
const LABELS = String(opt('labels', VARIANTS.map(p => path.basename(path.dirname(p)) + '/' + path.basename(p)).join(','))).split(',');
const ROUNDS = +opt('rounds', 1);
const TOGGLE = opt('toggle', false) ? String(opt('toggle')).split(',') : null;
const STORAGE = String(opt('storage', '')).split('|').map(s => s.trim() ? JSON.parse(s) : {});
const ASSETS = path.resolve(opt('assets', LIVE + '/site/assets.epk'));
const W = +opt('width', 1920), H = +opt('height', 1080), SECONDS = +opt('seconds', 15);
const SCENES = String(opt('scenes', 'spawn,horde,gore,fly')).split(',');
const PROFILE = !!opt('profile', false);
const LABEL = String(opt('label', path.basename(CLASSES)));
const out = path.resolve(opt('out', path.join(ROOT, 'candidate', 'fps-bench', new Date().toISOString().replace(/[:.]/g, '-'))));
const NAME = 'FpsBench';
const REGIONS = ['r.0.0.mca', 'r.-1.0.mca', 'r.0.-1.mca', 'r.-1.-1.mca'].map(f => LIVE + '/server/world/region/' + f);
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));

// ---------------------------------------------------------------- the probed copy of classes.js
function probe(source) {
  const fn = name => { const s = source.indexOf('\nfunction ' + name + '('), e = source.indexOf('\nfunction ', s + 10); if (s < 0) throw Error('missing ' + name); return [s, e]; };
  const patch = (name, from, to) => {
    const [s, e] = fn(name), body = source.slice(s, e);
    if (body.split(from).length !== 2) throw Error(name + ': anchor not unique: ' + from);
    source = source.slice(0, s) + body.replace(from, to) + source.slice(e);
  };
  patch('Dz6', 'case 0:$p=1;case 1:$z=DzO();', 'case 0:JasprFpsProbe.start();$p=1;case 1:$z=DzO();');      // Minecraft.runGameLoop
  patch('Gq3', 'case 0:JasprRevive.tick();', 'case 0:JasprFpsProbe.cpu();JasprRevive.tick();');              // Minecraft.updateDisplay
  // measuring capacity: the live display cap (JASPR_LOOK_V1, never draw faster than the screen) is lifted in this copy only
  if (source.split('function JasprInputPace(requested) {').length !== 2) throw Error('JasprInputPace not found');
  source = source.replace('function JasprInputPace(requested) {', 'function JasprInputPace(requested) { if ($rt_globals.JasprFpsUncapped) return requested;');
  // per-frame phase timing: the named engine functions are wrapped (time per call summed into the current frame)
  const PHASES = {DLV: 'updateChunks', CHq: 'runTick', DbP: 'renderEntities', DCR: 'renderBlockLayer', Gq_: 'renderParticles', Dep: 'soundListener', Fj1: 'cameraAndRender', FSa: 'rebuildChunk'};
  for (const [name, label] of Object.entries(PHASES)) {
    const head = source.indexOf('\nfunction ' + name + '(');
    if (head < 0) throw Error('missing ' + name);
    const args = source.slice(source.indexOf('(', head) + 1, source.indexOf(')', head));
    source = source.slice(0, head) + '\nfunction ' + name + '(' + args + '){var t=performance.now();JasprFpsProbe.depth++;var r;try{r=' + name + '$probe(' + args + ');}finally{JasprFpsProbe.depth--;}JasprFpsProbe.add(' + JSON.stringify(label) + ',performance.now()-t);return r;}' +
      '\nfunction ' + name + '$probe' + source.slice(head + 10 + name.length);
  }
  patch('Gq3', 'JasprDH.maintain();', 'var $fpsT=performance.now();JasprDH.maintain();JasprFpsProbe.add("distantTerrain",performance.now()-$fpsT);');
  const bridge = `
var JasprFpsProbe=(function(){var mode=0,modes=[],all=0,on=false,frames=[],cpu=[],phases=[],cur={},cnt={},tot={},mx={},t0=0,last=0;
return {depth:0,rebuilds:function(){return all;},add:function(k,v){if(k==='rebuildChunk')all++;cur[k]=(cur[k]||0)+v;cnt[k]=(cnt[k]||0)+1;tot[k]=(tot[k]||0)+v;if(v>(mx[k]||0))mx[k]=v;},setMode:function(m){mode=m;},start:function(){var n=performance.now();if(on){if(last){frames.push(n-last);phases.push(cur);modes.push(mode);}}cur={};last=n;t0=n;},
cpu:function(){if(on&&t0)cpu.push(performance.now()-t0);},
begin:function(){frames=[];cpu=[];phases=[];modes=[];cnt={};tot={};mx={};last=0;t0=0;on=true;},end:function(){on=false;return {frames:frames,cpu:cpu,phases:phases,modes:modes,calls:cnt,total:tot,max:mx};}};})();
$rt_globals.JasprFpsProbe=JasprFpsProbe;$rt_globals.JasprFpsUncapped=true;$rt_globals.__mc=function(){return HEH;};$rt_globals.__eval=function(s){return eval(s);};
`;
  const tail = source.lastIndexOf('}));');
  return source.slice(0, tail) + bridge + source.slice(tail);
}

function stats(frames, cpu) {
  if (!frames.length) return {frames: 0};
  const s = [...frames].sort((a, b) => a - b), sum = frames.reduce((a, b) => a + b, 0), q = p => +s[Math.min(s.length - 1, Math.floor((s.length - 1) * p))].toFixed(2);
  const c = [...cpu].sort((a, b) => a - b), cq = p => c.length ? +c[Math.min(c.length - 1, Math.floor((c.length - 1) * p))].toFixed(2) : null;
  return {frames: frames.length, fps: +(1000 * frames.length / sum).toFixed(1), p50: q(0.5), p95: q(0.95), p99: q(0.99), worst: q(1),
    at165: +(100 * frames.filter(x => x <= 1000 / 165).length / frames.length).toFixed(1), over16: frames.filter(x => x > 16.67).length,
    cpuAvg: c.length ? +(cpu.reduce((a, b) => a + b, 0) / cpu.length).toFixed(2) : null, cpuP50: cq(0.5), cpuP95: cq(0.95)};
}

// ---------------------------------------------------------------- run
let fixture = null, chrome = null, profileDir = null, fixtureDir = null;
// i5-12600K: logical processors 0-11 are the six P-cores, 12-15 the four E-cores. Windows 10 does not steer threads by core
// type, so an unpinned browser lands on either at random (+-20% run to run). The browser runs on the P-cores, the test
// server on the E-cores.
function pin(filter, mask) {
  const ps = "Get-CimInstance Win32_Process | Where-Object { " + filter + " } | ForEach-Object { try { $p = Get-Process -Id $_.ProcessId -ErrorAction Stop; $p.ProcessorAffinity = " + mask + " } catch {} }";
  try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 30000}); } catch (e) { }
}
function stopAll() {
  if (profileDir) {
    const tag = path.basename(profileDir).replace(/[^A-Za-z0-9_-]/g, '');
    const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in $ps){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 400 }";
    try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
    try { fs.rmSync(profileDir, {recursive: true, force: true}); } catch (e) { }
    profileDir = null;
  }
  if (fixture && fixture.exitCode === null) { try { fixture.stdin.write('stop\n'); } catch (e) { } }
}

(async () => {
  const classesPath = path.join(out, 'classes.probed.js');
  const probed = VARIANTS.map((p, i) => { const q = path.join(out, 'variant-' + i + '.js'); fs.writeFileSync(q, Buffer.from(probe(fs.readFileSync(p, 'latin1')), 'latin1')); return q; });
  fs.copyFileSync(probed[0], classesPath);
  for (const r of REGIONS) if (!fs.existsSync(r)) throw Error('missing region ' + r);
  fixture = spawn(process.execPath, [path.join(FIXTURE_ROOT, 'scripts', 'tank-preview.cjs')], {cwd: FIXTURE_ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_ROOT: FIXTURE_ROOT, TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: ASSETS,
      TANK_PREVIEW_PLUGINS: [LIVE + '/server/plugins/JasprMutants.jar'].join(','), TANK_PREVIEW_COPY: LIVE + '/server/jaspr-paper-clientbudget.jar=>paper.jar',
      TANK_PREVIEW_REGIONS: REGIONS.join(','), TANK_PREVIEW_XMX: '1024M', TANK_PREVIEW_VIEW: '6'})});
  try { os.setPriority(fixture.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  let fx = '';
  fixture.stdout.on('data', d => fx += d); fixture.stderr.on('data', d => fx += d);
  const end = Date.now() + 300000;
  while (Date.now() < end && !(/Preview URL: (\S+)/.test(fx) && /Done \(/.test(fx))) await sleep(500);
  if (!/Done \(/.test(fx)) throw Error('fixture did not start:\n' + fx.slice(-2000));
  fixtureDir = (fx.match(/Fixture: (.+)/) || [])[1];
  const url = fx.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  if (fixtureDir) pin("$_.Name -eq 'java.exe' -and $_.CommandLine -like '*paper.jar*' -and $_.CommandLine -like '*ActiveProcessorCount=1*'", '0xF000');
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(150); return t; };
  // the server-side view distance of the live server
  await cmd('gamerule randomTickSpeed 0'); await cmd('gamerule doDaylightCycle false'); await cmd('gamerule doMobSpawning false'); await cmd('gamerule doWeatherCycle false');
  await cmd('weather clear'); await cmd('time set 6000');

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-fps-bench-'));
  chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-gpu-rasterization',
    '--disable-frame-rate-limit', '--disable-gpu-vsync', '--autoplay-policy=no-user-gesture-required', '--no-first-run', '--no-default-browser-check', '--mute-audio', '--disable-extensions',
    '--disable-background-networking', '--disable-sync', '--disable-default-apps', '--disable-component-update', '--renderer-process-limit=1',
    '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows',
    '--disable-features=Translate,MediaRouter,OptimizationHints,CalculateNativeWinOcclusion', '--window-size=' + W + ',' + H, '--force-device-scale-factor=1',
    '--remote-debugging-port=' + debugPort, '--user-data-dir=' + profileDir, 'about:blank'], {windowsHide: true, stdio: 'ignore'});
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
  const inner = async src => evaluate('(function(){try{return JSON.stringify(__eval(' + JSON.stringify(src) + '));}catch(e){return JSON.stringify({error:String(e&&e.stack||e).slice(0,400)});}})()').then(t => t ? JSON.parse(t) : null);
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width: W, height: H, deviceScaleFactor: 1, mobile: false, screenOrientation: {type: 'landscapePrimary', angle: 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;document.dispatchEvent(new Event('pointerlockchange'));};Document.prototype.exitPointerLock=function(){locked=null;document.dispatchEvent(new Event('pointerlockchange'));};var A=window.AudioContext;if(A){window.AudioContext=function(o){var c=new A(o);window.__actx=c;return c;};window.AudioContext.prototype=A.prototype;}})();"});
  // desktop default preset (1, "Performance"), frame cap lifted for measuring
  const video = require(path.join(FIXTURE_ROOT, 'site', 'jaspercraft-video-core.js'));
  const values = Object.assign({}, video.preset(1), {vsync: 0, maxFps: 260, showFps: 1, mobileControls: 0});
  let storageScript = null;
  async function applyStorage(vi) {
    const spec = STORAGE[vi] || {};
    const vals = Object.assign({}, values, spec.video || {});
    const saved = {version: 1, tier: 1, auto: false, values: vals, goreDefaultVersion: 1, vsyncDefaultVersion: 2};
    let src = 'try{localStorage.setItem("jaspr.video.v1",' + JSON.stringify(JSON.stringify(saved)) + ');';
    for (const [k, v] of Object.entries(spec)) if (k !== 'video') src += 'localStorage.setItem(' + JSON.stringify(k) + ',' + JSON.stringify(String(v)) + ');';
    src += '}catch(e){}';
    if (storageScript) await send('Page.removeScriptToEvaluateOnNewDocument', {identifier: storageScript});
    storageScript = (await send('Page.addScriptToEvaluateOnNewDocument', {source: src})).result.identifier;
  }
  // JASPR_FPS stage settings for this run (e.g. --fpsconfig '{"verify":true}')
  if (opt('fpsconfig', false)) await send('Page.addScriptToEvaluateOnNewDocument', {source: 'try{localStorage.setItem("jaspr.fps.v1",' + JSON.stringify(String(opt('fpsconfig'))) + ');}catch(e){}'});
  await send('Network.enable'); await send('Network.setCacheDisabled', {cacheDisabled: true});
  const pinBrowser = () => pin("$_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + path.basename(profileDir) + "*'", '0x0FFF');
  const all = {width: W, height: H, seconds: SECONDS, rounds: ROUNDS, variants: LABELS, runs: []};
  for (let round = 0; round < ROUNDS; round++) for (let vi = 0; vi < VARIANTS.length; vi++) {
  fs.copyFileSync(probed[vi], classesPath);
  await applyStorage(vi);
  await send('Page.navigate', {url: url + '#' + NAME});
  await sleep(3000);
  pinBrowser();
  console.log('--- round ' + (round + 1) + ' variant ' + LABELS[vi]);
  const inWorld = async ms => { const e = Date.now() + ms; while (Date.now() < e) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v&&__mc().X);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
  if (!(await inWorld(240000))) throw Error('client did not join');
  const results = {label: LABELS[vi], round, scenes: {}};
  all.runs.push(results);
  results.env = await inner('(function(){var g=HEf,ext=g&&g.getExtension("WEBGL_debug_renderer_info");return {audio:window.__actx?window.__actx.state:null,gpu:g?g.getParameter(ext?ext.UNMASKED_RENDERER_WEBGL:g.RENDERER):null,size:[HEH.gk,HEH.fV],renderDistance:HEH.G.ni,maxFps:HEH.G.a3u,vsync:HEH.G.qQ};})()');
  console.log('env ' + JSON.stringify(results.env));
  await cmd('gamemode 1 ' + NAME);
  await cmd('spreadplayers 0 0 0 1 false ' + NAME);
  await sleep(1500);
  await cmd('minecraft:tp ' + NAME + ' ~ ~ ~ 0 0');
  // settle: every chunk in view loaded and built (no chunk arrived and nothing rebuilt for 3 s), at most 60 s
  async function settle(label) {
    const t0 = Date.now(); let last = null, stableSince = Date.now();
    while (Date.now() - t0 < 60000) {
      const now = await inner('[JasprFpsProbe.rebuilds(), HEH.X && HEH.X.a9n && HEH.X.a9n.a0G ? HEH.X.a9n.a0G.a8D : -1]');
      const key = JSON.stringify(now);
      if (key !== last) { last = key; stableSince = Date.now(); } else if (Date.now() - stableSince >= 3000) break;
      await sleep(500);
    }
    console.log('settled ' + label + ' in ' + ((Date.now() - t0) / 1000).toFixed(1) + ' s ' + last);
  }
  await sleep(2000);
  await settle('spawn');
  const look = (yaw, pitch) => inner('(function(){var p=HEH.v;p.C=' + yaw + ';p.bd=' + pitch + ';return 1;})()');

  async function measure(scene, drive) {
    let prof = null;
    if (PROFILE) { await send('Profiler.enable'); await send('Profiler.setSamplingInterval', {interval: 250}); await send('Profiler.start'); }
    pinBrowser();
    await evaluate('JasprFpsProbe.begin()');
    const t0 = Date.now();
    let lastFlip = 0, mode = 0;
    while (Date.now() - t0 < SECONDS * 1000) {
      if (TOGGLE && Date.now() - lastFlip >= 1000) {
        lastFlip = Date.now(); mode = 1 - mode;
        await inner('(function(){var o=JasprFps.on;' + TOGGLE.map(k => 'o.' + k + '=' + (mode ? 'false' : 'true') + ';').join('') + 'JasprFpsProbe.setMode(' + mode + ');return 1;})()');
      }
      if (drive) await drive((Date.now() - t0) / (SECONDS * 1000));
      await sleep(100);
    }
    if (TOGGLE) await inner('(function(){var o=JasprFps.on;' + TOGGLE.map(k => 'o.' + k + '=true;').join('') + 'JasprFpsProbe.setMode(0);return 1;})()');
    const raw = await evaluate('JSON.stringify(JasprFpsProbe.end())').then(JSON.parse);
    if (PROFILE) { prof = (await send('Profiler.stop')).result.profile; fs.writeFileSync(path.join(out, scene + '.cpuprofile'), JSON.stringify(prof)); }
    const st = stats(raw.frames, raw.cpu);
    if (TOGGLE) {
      // the first frame after each flip belongs to neither mode cleanly: drop it
      const pick = m => raw.frames.filter((x, i) => raw.modes[i] === m && i > 0 && raw.modes[i - 1] === m);
      st.on = stats(pick(0), []); st.off = stats(pick(1), []);
      const ph = m => { const o = {}; let n = 0; raw.phases.forEach((p, i) => { if (raw.modes[i] !== m) return; n++; for (const k in p) o[k] = (o[k] || 0) + p[k]; }); for (const k in o) o[k] = +(o[k] / n).toFixed(3); return o; };
      st.onPhases = ph(0); st.offPhases = ph(1);
      console.log('TOGGLE ' + scene.padEnd(6) + ' on: fps ' + st.on.fps + ' p50 ' + st.on.p50 + ' p95 ' + st.on.p95 + ' at165 ' + st.on.at165 + '% | off: fps ' + st.off.fps + ' p50 ' + st.off.p50 + ' p95 ' + st.off.p95 + ' at165 ' + st.off.at165 + '%');
      console.log('   on  phases ' + JSON.stringify(st.onPhases));
      console.log('   off phases ' + JSON.stringify(st.offPhases));
    }
    st.stage = await evaluate('JSON.stringify(window.JasprFpsStatus ? JasprFpsStatus() : null)').then(x => x ? JSON.parse(x) : null);
    st.chunkUpdates = await inner('HEH.bCq ? 0 : 0');
    // phase time per frame: all frames and the slow ones (> 6.06 ms, i.e. under 165 FPS)
    const avgPhases = list => { const o = {}; for (const p of list) for (const k in p) o[k] = (o[k] || 0) + p[k]; for (const k in o) o[k] = +(o[k] / Math.max(1, list.length)).toFixed(2); return o; };
    st.phases = avgPhases(raw.phases);
    st.slowPhases = avgPhases(raw.phases.filter((p, i) => raw.frames[i] > 1000 / 165));
    st.spikePhases = avgPhases(raw.phases.filter((p, i) => raw.frames[i] > 12));
    st.spikes = raw.frames.filter(x => x > 12).length;
    if (raw.calls.rebuildChunk) st.rebuild = {count: raw.calls.rebuildChunk, avgMs: +(raw.total.rebuildChunk / raw.calls.rebuildChunk).toFixed(2), maxMs: +raw.max.rebuildChunk.toFixed(2)};
    results.scenes[scene] = st;
    console.log(scene.padEnd(6) + ' ' + JSON.stringify(st));
    const shot = await send('Page.captureScreenshot', {format: 'jpeg', quality: 60});
    if (shot.result) fs.writeFileSync(path.join(out, scene + '.jpg'), Buffer.from(shot.result.data, 'base64'));
    return st;
  }
  if (SCENES.includes('rebuild')) {
    await look(45, 20);
    await sleep(500);
    let prof = null;
    if (PROFILE) { await send('Profiler.enable'); await send('Profiler.setSamplingInterval', {interval: 250}); await send('Profiler.start'); }
    pinBrowser();
    await evaluate('JasprFpsProbe.begin()');
    await inner('(function(){$rt_startThread(function(){DbR(HEH.fE);});return 1;})()');
    const t0 = Date.now(); let lastN = -1, stable = Date.now();
    while (Date.now() - t0 < 60000) { const n = await inner('JasprFpsProbe.rebuilds()'); if (n !== lastN) { lastN = n; stable = Date.now(); } else if (Date.now() - stable > 2000) break; await sleep(250); }
    const raw = await evaluate('JSON.stringify(JasprFpsProbe.end())').then(JSON.parse);
    if (PROFILE) { prof = (await send('Profiler.stop')).result.profile; fs.writeFileSync(path.join(out, 'rebuild.cpuprofile'), JSON.stringify(prof)); }
    const st = stats(raw.frames, raw.cpu);
    st.rebuild = raw.calls.rebuildChunk ? {count: raw.calls.rebuildChunk, totalMs: +raw.total.rebuildChunk.toFixed(1), avgMs: +(raw.total.rebuildChunk / raw.calls.rebuildChunk).toFixed(3), maxMs: +raw.max.rebuildChunk.toFixed(2)} : null;
    st.seconds = +((Date.now() - t0) / 1000).toFixed(1);
    results.scenes.rebuild = st;
    console.log('rebuild ' + JSON.stringify({rebuild: st.rebuild, frames: st.frames, fps: st.fps, p95: st.p95, worst: st.worst, seconds: st.seconds}));
  }
  if (SCENES.includes('meshcheck')) {
    const parts = String(opt('meshparts', 'memo,rebuild')).split(',');
    await look(45, 20); await sleep(500);
    await inner('(function(){var g=HEf;if(g.$fpsBD)return 1;var o=g.bufferData;g.$fpsBD=o;g.bufferData=function(t,d,u){var R=$rt_globals.__meshRec;if(R&&JasprFpsProbe.depth>0&&d&&d.byteLength>0){var b=new Uint8Array(d.buffer||d,d.byteOffset||0,d.byteLength),h=2166136261;for(var i=0;i<b.length;i++){h^=b[i];h=Math.imul(h,16777619);}R.push(d.byteLength+":"+(h>>>0).toString(16));}return o.apply(this,arguments);};return 1;})()');
    const timings = [];
    const run = async on => {
      await inner('(function(){var o=JasprFps.on;' + parts.map(k => 'o.' + k + '=' + on + ';').join('') + '$rt_globals.__meshRec=[];return 1;})()');
      pinBrowser();
      await evaluate('JasprFpsProbe.begin()');
      await inner('(function(){$rt_startThread(function(){DbR(HEH.fE);});return 1;})()');
      const t0 = Date.now(); let lastN = -1, stable = Date.now();
      while (Date.now() - t0 < 60000) { const n = await inner('JasprFpsProbe.rebuilds()'); if (n !== lastN) { lastN = n; stable = Date.now(); } else if (Date.now() - stable > 2500) break; await sleep(250); }
      const pr = await evaluate('JSON.stringify(JasprFpsProbe.end())').then(JSON.parse);
      timings.push({on, count: pr.calls.rebuildChunk, totalMs: +(pr.total.rebuildChunk || 0).toFixed(1), maxMs: +(pr.max.rebuildChunk || 0).toFixed(2)});
      return await inner('(function(){var r=$rt_globals.__meshRec;$rt_globals.__meshRec=null;return r;})()');
    };
    const onRec = await run(true), offRec = await run(false), onRec2 = await run(true), offRec2 = await run(false);
    await inner('(function(){var o=JasprFps.on;' + parts.map(k => 'o.' + k + '=true;').join('') + 'return 1;})()');
    const ms = a => { const m = new Map(); for (const x of a) m.set(x, (m.get(x) || 0) + 1); return m; };
    const diff = (a, b) => { const A = ms(a), Bm = ms(b); let d = 0; for (const [k, v] of A) d += Math.abs(v - (Bm.get(k) || 0)); for (const [k, v] of Bm) if (!A.has(k)) d += v; return d; };
    const res = {uploadsOn: onRec.length, uploadsOff: offRec.length, uploadsOn2: onRec2.length, differOnOff: diff(onRec, offRec), differOnOn: diff(onRec, onRec2), differOffOff: diff(offRec, offRec2), differOn2Off: diff(onRec2, offRec), differOn2Off2: diff(onRec2, offRec2), timings, bytesOn: onRec.reduce((s, x) => s + +x.split(':')[0], 0)};
    results.scenes.meshcheck = res;
    console.log('MESHCHECK ' + JSON.stringify(res));
  }
  // the view sweeps at constant speed; yaw/pitch are set on the client, the server follows like a real mouse
  if (SCENES.includes('spawn')) await measure('spawn', async f => look(f * 360, 5));
  if (SCENES.includes('horde') || SCENES.includes('gore')) {
    await cmd('time set 18000');
    await look(0, 10);
    const mobs = ['zombie', 'skeleton', 'creeper', 'spider', 'husk', 'stray'];
    for (let i = 0; i < 54; i++) {
      const x = ((i % 9) - 4) * 1.6, z = 5 + Math.floor(i / 9) * 1.8, kind = mobs[i % mobs.length];
      await getText(webPort, '/console?c=' + encodeURIComponent('execute ' + NAME + ' ~ ~ ~ summon minecraft:' + kind + ' ~' + x.toFixed(1) + ' ~ ~' + z.toFixed(1) + ' {NoAI:1b,PersistenceRequired:1b,Silent:1b}'));
    }
    for (const [k, x] of [['mutant_zombie', -6], ['mutant_skeleton', -2], ['mutant_creeper', 2], ['mutant_enderman', 6], ['mutant_snow_golem', 10], ['spider_pig', -10]])
      await getText(webPort, '/console?c=' + encodeURIComponent('execute ' + NAME + ' ~ ~ ~ summon mutantbeasts:' + k + ' ~' + x + ' ~ ~17 {NoAI:1b,PersistenceRequired:1b,Silent:1b}'));
    await sleep(5000);
    if (SCENES.includes('horde')) await measure('horde', async f => look(-25 + 50 * f, 10));
    if (SCENES.includes('gore')) {
      await cmd('kill @e[type=!player]');
      await sleep(400);
      await measure('gore', async f => look(-15 + 30 * f, 10));
    }
    await cmd('kill @e[type=!player]');
    await cmd('time set 6000');
  }
  if (SCENES.includes('fly')) {
    await cmd('spreadplayers 0 0 0 1 false ' + NAME);
    await sleep(1500);
    await look(-90, 15);
    // west at 11 blocks/s (sprint-flying speed), 95 high: Entity.setPosition each 100 ms, the client reports it like real movement
    const startX = await inner('HEH.v.b');
    await measure('fly', async f => inner('(function(){var p=HEH.v;p.RW(' + startX + ' - ' + (SECONDS * 11) + ' * ' + 'Math.min(1,' + f + '),95,p.c);p.s=0;p.p=0;p.t=0;return 1;})()'));
  }
  results.consoleErrors = consoleLog.filter(l => /EXCEPTION/i.test(l)).slice(0, 10);
  fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify(all, null, 2));
  }
  // summary: per scene and variant, the median over rounds
  const med = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.floor((s.length - 1) / 2)] : null; };
  for (const scene of SCENES) {
    const row = LABELS.map(l => { const rs = all.runs.filter(r => r.label === l && r.scenes[scene]).map(r => r.scenes[scene]);
      if (scene === 'rebuild') return l + ': rebuild total ' + med(rs.map(x => x.rebuild ? x.rebuild.totalMs : 0)) + 'ms avg ' + med(rs.map(x => x.rebuild ? x.rebuild.avgMs : 0)) + 'ms max ' + med(rs.map(x => x.rebuild ? x.rebuild.maxMs : 0)) + 'ms n ' + med(rs.map(x => x.rebuild ? x.rebuild.count : 0));
      return l + ': fps ' + med(rs.map(x => x.fps)) + ' at165 ' + med(rs.map(x => x.at165)) + '% p95 ' + med(rs.map(x => x.p95)) + ' cpu ' + med(rs.map(x => x.cpuAvg)) + (rs[0] && rs[0].rebuild ? ' rebuild ' + med(rs.map(x => x.rebuild ? x.rebuild.avgMs : 0)) + 'ms' : ''); });
    console.log('SUMMARY ' + scene.padEnd(6) + ' ' + row.join(' | '));
  }
  fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n'));
  try { ws.close(); } catch (e) { }
  stopAll();
  console.log('FPS_BENCH_DONE out=' + out);
  setTimeout(() => { if (fixtureDir) try { fs.rmSync(fixtureDir, {recursive: true, force: true}); } catch (e) { } process.exit(0); }, 15000);
})().catch(e => { console.log('FPS_BENCH_FAIL ' + (e && e.stack || e)); stopAll(); setTimeout(() => process.exit(1), 15000); });
setTimeout(() => { console.log('FPS_BENCH_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 15000); }, 15 * 60 * 1000).unref();
