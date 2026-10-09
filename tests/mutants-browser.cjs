'use strict';
/* Mutant Creatures (JASPR_MUTANTS + JasprMutants) in the real client: one headless Chrome at below-normal priority against the
 * loopback fixture (scripts/tank-preview.cjs, a lean single-core Paper server running the LIVE server jar copy and the candidate
 * JasprMutants plugin) with the candidate client (candidate/mutants/classes.js + candidate/mutants-pack/assets.epk). It checks:
 *   - HELLO reaches the server (MUTANTS_CLIENT_HELLO), the stage installs, preloads its textures and registers its renderers;
 *   - every mutant species and the creeper minion summoned in front of the player arrives as its client twin, is drawn by its own
 *     renderer (the frame changes where it stands) and nothing is switched off;
 *   - held items (hulk hammer, the endersoul hand's 3D model through the TEISR) and the worn mutant skeleton armour (the skull's
 *     own model through the armour hook, third person) are drawn;
 *   - chemical X bursting on the ground spawns skull spirit particles through the stage's factory;
 *   - the creeper minion tracker screen opens for an owned minion and its first button really toggles "destroys blocks" on the
 *     server (CreeperMinionTrackerPacket -> validator -> data watcher back to the client);
 *   - no page exception, no stage failure, no server exception.
 * Screenshots go to the out folder.
 *   TANK_PREVIEW_ROOT=<checkout with candidate/tanks and candidate/tank-client> node tests/mutants-browser.cjs [out-dir]
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path'), zlib = require('node:zlib');
const crypto = require('node:crypto');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const FIXTURE_ROOT = path.resolve(process.env.TANK_PREVIEW_ROOT || ROOT);
const LIVE_JAR = process.env.MUTANTS_SERVER_JAR || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/server/jaspr-paper-clientbudget.jar';
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'mutants-browser'));
const NAME = 'MutantTester', W = 960, H = 540;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, chrome = null, profile = null;
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 700) : ''));
}
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));
// Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name): the offline-mode player UUID
function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return x.slice(0, 8) + '-' + x.slice(8, 12) + '-' + x.slice(12, 16) + '-' + x.slice(16, 20) + '-' + x.slice(20);
}
function decodePng(buf) {
  let at = 8, width = 0, height = 0, type = 0, idat = [];
  while (at < buf.length) {
    const len = buf.readUInt32BE(at), name = buf.toString('latin1', at + 4, at + 8), data = buf.subarray(at + 8, at + 8 + len);
    if (name === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); type = data[9]; }
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
/** Pixels (every second one) of the middle of the frame that differ clearly from the baseline frame. */
function changed(a, b) {
  if (!a || !b) return 0;
  let n = 0;
  for (let y = Math.floor(a.height * 0.15); y < a.height * 0.85; y += 2) for (let x = Math.floor(a.width * 0.25); x < a.width * 0.75; x += 2) {
    const p = a.rgb(x, y), q = b.rgb(x, y);
    if (Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]) > 60) n++;
  }
  return n;
}

const DEPLOY_CLASSES = path.join(ROOT, 'candidate', 'mutants', 'classes.js'), DEPLOY_ASSETS = path.join(ROOT, 'candidate', 'mutants-pack', 'assets.epk');
const PLUGIN = path.join(ROOT, 'server', 'plugins', 'JasprMutants.jar');
for (const f of [DEPLOY_CLASSES, DEPLOY_ASSETS, PLUGIN, LIVE_JAR]) if (!fs.existsSync(f)) throw new Error('missing ' + f);
const classesPath = path.join(out, 'classes.test.js');
const candidate = fs.readFileSync(DEPLOY_CLASSES, 'latin1');
const ANCHOR = '$rt_globals.JasprWideBridge = {';
if (candidate.split(ANCHOR).length !== 2 || !candidate.includes('/* JASPR_MUTANTS_BEGIN */')) throw new Error('the candidate client lacks the Mutants stage');
// test-only window into the TeaVM module scope (the fixture's copy only)
fs.writeFileSync(classesPath, Buffer.from(candidate.replace(ANCHOR, '$rt_globals.__mc = function () { return HEH; }; $rt_globals.__eval = function (s) { return eval(s); }; ' + ANCHOR), 'latin1'));

function stopAll() {
  if (profile) {
    const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
    const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in $ps){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 400 }";
    try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
    try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { }
    profile = null;
  }
  if (fixture && fixture.exitCode === null) { try { fixture.stdin.write('stop\n'); } catch (e) { } }
}

(async () => {
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_ROOT: FIXTURE_ROOT, TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: DEPLOY_ASSETS,
      TANK_PREVIEW_PLUGINS: PLUGIN, TANK_PREVIEW_COPY: LIVE_JAR + '=>paper.jar', TANK_PREVIEW_XMX: '768M'})});
  try { os.setPriority(fixture.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 300000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; await sleep(500); } return false; };
  if (!(await ready())) throw new Error('fixture did not start:\n' + fxOut.slice(-2500));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(300); return t; };
  const serverLog = () => getText(webPort, '/log').catch(() => '');

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-mutants-probe-'));
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
  const inner = async src => evaluate('(function(){try{return JSON.stringify(__eval(' + JSON.stringify(src) + '));}catch(e){return JSON.stringify({error:String(e&&e.stack||e).slice(0,400)});}})()').then(t => t ? JSON.parse(t) : null);
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width: W, height: H, deviceScaleFactor: 1, mobile: false, screenOrientation: {type: 'landscapePrimary', angle: 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;document.dispatchEvent(new Event('pointerlockchange'));};Document.prototype.exitPointerLock=function(){locked=null;document.dispatchEvent(new Event('pointerlockchange'));};})();"});
  const video = {version: 1, tier: -1, auto: false, values: {renderDistance: 2, maxFps: 15, fancyGraphics: 0, ao: 0, particles: 0, clouds: 0, entityShadows: 0, vsync: 0,
    mipmaps: 0, chunkUpdates: 1, fog: 1, viewBobbing: 0, resolution: 100, chunkBudget: 1, entityDistance: 32, fastVisibility: 1, animations: 0, ambientEffects: 0,
    weatherEffects: 0, particleEffects: 0, dynamicLights: 0, gore: 0, shader: 0, dhEnabled: 0, showFps: 0, showCoords: 0, mobileControls: 0, touchSensitivity: 100}};
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
  const status = () => evaluate('JSON.stringify(window.JasprMutantsDiagnostics ? JasprMutantsDiagnostics.status() : null)').then(t => (t ? JSON.parse(t) : null));
  const twins = () => inner('JasprMutants.listToArray(HEH.X.gw).filter(function(e){return !!e.constructor.$jm;}).map(function(e){return [e.constructor.$jm.name.split(".").pop(), e.cu];})');
  const key = async (k, code) => { await send('Input.dispatchKeyEvent', {type: 'keyDown', key: k, code, windowsVirtualKeyCode: code === 'Escape' ? 27 : 0}); await sleep(80); await send('Input.dispatchKeyEvent', {type: 'keyUp', key: k, code, windowsVirtualKeyCode: code === 'Escape' ? 27 : 0}); };

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v&&__mc().X);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(240000), 'client joins the fixture server');
    await sleep(4000);
    let log = await serverLog();
    check(/MUTANTS_READY entities=15 items=15/.test(log), 'the server port is ready', (log.match(/MUTANTS_READY[^\n]*/) || [''])[0]);
    check(new RegExp('MUTANTS_CLIENT_HELLO version=1 player=' + NAME).test(log), 'the client said HELLO (protocol 1)');
    let st = await status();
    check(st && st.installed && st.ready && st.textures && Object.keys(st.disabled).length === 0, 'the stage installed, preloaded its textures and registered its renderers', st && {ready: st.ready, textures: st.textures, disabled: st.disabled});
    await cmd('gamemode 1 ' + NAME);
    await cmd('time set 6000');
    await cmd('gamerule doDaylightCycle false');
    await cmd('gamerule doMobSpawning false');
    await cmd('weather clear');
    await cmd('tp ' + NAME + ' ~ ~ ~ 0 10');
    await sleep(2500);
    const base = await shot('00-empty');
    const kinds = [['mutant_zombie', 'MutantZombieEntity', 7], ['mutant_skeleton', 'MutantSkeletonEntity', 7], ['mutant_creeper', 'MutantCreeperEntity', 7],
      ['mutant_enderman', 'MutantEndermanEntity', 8], ['mutant_snow_golem', 'MutantSnowGolemEntity', 6], ['spider_pig', 'SpiderPigEntity', 5], ['creeper_minion', 'CreeperMinionEntity', 3]];
    for (const [kind, cls, dist] of kinds) {
      const before = (await status()).stats.renders;
      await cmd('execute ' + NAME + ' ~ ~ ~ summon mutantbeasts:' + kind + ' ~ ~ ~' + dist + ' {NoAI:1b,PersistenceRequired:1b,Silent:1b}');
      await sleep(3500);
      const t = await twins();
      const img = await shot('10-' + kind);
      st = await status();
      const diff = changed(base, img);
      check(Array.isArray(t) && t.some(x => x[0] === cls) && st.stats.renders > before && diff > 150 && Object.keys(st.disabled).length === 0,
        kind + ': its client twin arrives and its own renderer draws it', {twins: t, renders: st.stats.renders - before, changedPixels: diff, disabled: st.disabled});
      await cmd('kill @e[type=!player]');                   // the mutant and whatever it left (body parts, fragments, drops)
      await sleep(1500);
    }
    // items in hand
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.0 mutantbeasts:hulk_hammer');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.1 mutantbeasts:endersoul_hand');
    await cmd('replaceitem entity ' + NAME + ' slot.hotbar.2 mutantbeasts:chemical_x');
    await sleep(1500);
    await shot('20-hulk-hammer');
    await inner('(function(){HEH.v.bx.gP=1;return 1;})()');   // InventoryPlayer.currentItem = 1 (the endersoul hand)
    await sleep(2500);
    const tei0 = (await status()).stats.teisr;
    await sleep(1500);
    await shot('21-endersoul-hand');
    st = await status();
    check(st.stats.teisr > tei0 && Object.keys(st.disabled).length === 0, 'the endersoul hand is drawn by its 3D model (TEISR)', {teisr: st.stats.teisr - tei0, disabled: st.disabled});
    // armour, seen from the front (third person)
    for (const [slot, item] of [['head', 'mutant_skeleton_skull'], ['chest', 'mutant_skeleton_chestplate'], ['legs', 'mutant_skeleton_leggings'], ['feet', 'mutant_skeleton_boots']]) {
      await cmd('replaceitem entity ' + NAME + ' slot.armor.' + slot + ' mutantbeasts:' + item);
    }
    await inner('(function(){HEH.G.lv=2;return 1;})()');     // gameSettings.thirdPersonView = 2 (front)
    await sleep(3000);
    const armor0 = (await status()).stats.armor;
    await sleep(1500);
    await shot('30-armor-front');
    st = await status();
    check(st.stats.armor > armor0 && Object.keys(st.disabled).length === 0, 'the worn skull uses its own armour model', {armor: st.stats.armor - armor0});
    const speed = await inner('(function(){return [CcO(HEH.v, JasprMutants.potion("speed"))?1:0, CcO(HEH.v, JasprMutants.potion("jump_boost"))?1:0];})()');
    check(Array.isArray(speed) && speed[0] === 1 && speed[1] === 1, 'leggings give speed and boots jump boost', speed);
    await inner('(function(){HEH.G.lv=0;return 1;})()');
    // particles: chemical X bursting on the ground
    const p0 = (await status()).stats.particles;
    await cmd('execute ' + NAME + ' ~ ~ ~ summon mutantbeasts:chemical_x ~ ~3 ~4');
    await sleep(4000);
    st = await status();
    check(st.stats.particles > p0 && Object.keys(st.disabled).length === 0, 'chemical X bursts into skull spirit particles through the stage factory', {particles: st.stats.particles - p0});
    // the tracker screen and its packet
    await cmd('execute ' + NAME + ' ~ ~ ~ summon mutantbeasts:creeper_minion ~ ~ ~3 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tamed:1b,DestroysBlocks:1b,OwnerUUID:"' + offlineUuid(NAME) + '"}');
    await sleep(3000);
    const opened = await inner('(function(){var M=JasprMutants,m=M.listToArray(HEH.X.gw).filter(function(e){return e instanceof M.T.CreeperMinionEntity;})[0];if(!m)return "no minion";M.$testMinion=m;M.openGui(0,m);return [m.isTamed()?1:0, m.isOwner(HEH.v)?1:0, m.canDestroyBlocks()?1:0];})()');
    await sleep(1500);
    const screen = await inner('(function(){var s=HEH.cj,M=JasprMutants;if(!(s instanceof M.CreeperMinionTrackerScreen))return null;return M.listToArray(s.be).map(function(b){return [b.bF,b.bS,M.ustr(b.dd)];});})()');
    await shot('40-tracker');
    check(Array.isArray(opened) && opened[0] === 1 && opened[1] === 1 && opened[2] === 1 && Array.isArray(screen) && screen.length === 3 && screen.every(b => b[1] === 1),
      'the tracker screen opens for the owner with three enabled buttons', {opened, screen});
    await inner('(function(){var s=HEH.cj;s.eB(JasprMutants.listGet(s.be,0));return 1;})()');   // "Destroys blocks: ON" -> OFF
    await sleep(2500);
    const toggled = await inner('(function(){return JasprMutants.$testMinion.canDestroyBlocks()?1:0;})()');
    log = await serverLog();
    check(toggled === 0 && !/MUTANTS_[A-Z_]*REJECT/.test(log), 'the toggle reaches the server and comes back through the data watcher', {toggled});
    await key('Escape', 'Escape');
    await sleep(1500);
    check(await inner('HEH.cj === null'), 'Escape closes the screen');
    // the end
    st = await status();
    check(st && Object.keys(st.disabled).length === 0 && st.stats.errors === 0, 'no stage part was switched off', st && {disabled: st.disabled, errors: st.stats.errors, lastError: st.lastError, firstStack: st.firstStack});
    check(await evaluate('(function(){try{return !!(__mc()&&__mc().v);}catch(e){return false;}})()'), 'the client keeps running');
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    const soundWarnings = consoleLog.filter(l => /jaspr[./]mutants|mutantbeasts/.test(l) && /(not exist|unknown|missing|could not|failed)/i.test(l));
    check(soundWarnings.length === 0, 'no missing sound or resource warnings for the mod', soundWarnings.slice(0, 3));
    log = await serverLog();
    check(!/Exception/.test(log.replace(/javax\.imageio\.IIOException: Can't read input file!/g, '')), 'no server exceptions', (log.match(/.*Exception.*/) || [''])[0].slice(0, 300));
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    try { fs.writeFileSync(path.join(out, 'server.txt'), await serverLog()); } catch (e) { }
    try { ws.close(); } catch (e) { }
    stopAll();
  }
  console.log((failures ? 'MUTANTS_BROWSER_FAIL ' : 'MUTANTS_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 25000).unref();
})().catch(e => { console.log('MUTANTS_BROWSER_FAIL ' + (e && e.stack || e)); stopAll(); process.exitCode = 1; setTimeout(() => process.exit(1), 25000).unref(); });
setTimeout(() => { console.log('MUTANTS_BROWSER_FAIL timeout'); stopAll(); setTimeout(() => process.exit(3), 20000); }, 12 * 60 * 1000).unref();
