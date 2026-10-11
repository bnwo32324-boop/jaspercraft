'use strict';
/* The combat rework in the real client, assembled (LIVE -> A's Better Combat head -> B's melee module -> Mo' Bends module
 * swap -> B's JASPR_COMBATFX -> C's JASPR_GUNFX: candidate/combat-deploy/classes.js, node scripts/assemble-combat-client.cjs).
 * One headless Chrome at below-normal priority (640x360, render distance 2, 15 fps) against the loopback fixture
 * (scripts/tank-preview.cjs: a lean single-core Paper running a copy of the LIVE server jar) with the LIVE assets.epk and the
 * plugins the combat needs: JasprBetterCombat (this checkout, 1.5.6-port.5), the LIVE JasprApocalypse (guns, expedition
 * melee) and the LIVE AuthMe it depends on (fresh config; a generated throwaway account registered and logged in from the
 * server console). It checks:
 *   - Better Combat is active (hello, settings) and the combat stages are installed;
 *   - main-hand and off-hand attacks on a mob are hits by the server's verdict (combat feed, matched by ref);
 *   - alternating dual attacks with sword+sword and axe+dagger: both hands strike with their own class's moves, the waiting
 *     hand answers (support tracks), trails are drawn;
 *   - a right click on a villager with a sword in the off hand opens trading, with no off-hand swing;
 *   - sword, axe, hammer, dagger and spear each play their own class's move with a trail, a hit-stop and a camera kick;
 *   - an Apocalypse semi-automatic (Sepulcher) and an automatic (Whisper) gun fire and a reload runs once:
 *     JasprGunFXDiagnostics counts the shots, the recoil poses and the reload, and the kicks went through JasprCameraFX;
 *   - no page exception, no combat stage part switched off, no server exception.
 * Screenshots (JPEG), results.json and the logs go to the out folder; the fixture folder is removed afterwards.
 *   TANK_PREVIEW_ROOT=C:/Users/AM/Documents/JasperCraft-Journal node tests/combat-browser.cjs [out-dir]
 * PC safety: run it alone, through lockrun (one heavy process at a time).
 */
const fs = require('node:fs'), http = require('node:http'), os = require('node:os'), path = require('node:path');
const crypto = require('node:crypto');
const {spawn, spawnSync} = require('node:child_process');
const ROOT = path.join(__dirname, '..');
const LIVE = (process.env.COMBAT_LIVE_ROOT || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale').split('\\').join('/');
const FIXTURE_ROOT = path.resolve(process.env.TANK_PREVIEW_ROOT || ROOT);
const CLASSES = path.resolve(process.env.COMBAT_CLASSES || path.join(ROOT, 'candidate', 'combat-deploy', 'classes.js'));
const ASSETS = LIVE + '/site/assets.epk', LIVE_JAR = LIVE + '/server/jaspr-paper-clientbudget.jar';
const PLUGINS = [path.join(ROOT, 'server', 'plugins', 'JasprBetterCombat.jar'), (process.env.COMBAT_APOCALYPSE_JAR || path.join(ROOT, 'server', 'plugins', 'JasprApocalypse.jar')), LIVE + '/server/plugins/AuthMe.jar'];
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'combat-browser'));
const NAME = 'CombatTester', W = 640, H = 360, CX = W / 2, CY = H / 2;
fs.mkdirSync(out, {recursive: true});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let checks = 0, failures = 0, fixture = null, fixtureDir = null, profile = null;
const results = [];
function check(ok, what, extra) {
  checks++;
  if (!ok) failures++;
  results.push({ok: !!ok, what, extra});
  console.log((ok ? 'ok   ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 900) : ''));
}
function note(what, extra) { results.push({note: what, extra}); console.log('info ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 900) : '')); }
const getText = (port, p) => new Promise((res, rej) => http.get({host: '127.0.0.1', port, path: p}, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => res(d)); }).on('error', rej));

for (const f of [CLASSES, ASSETS, LIVE_JAR, ...PLUGINS]) if (!fs.existsSync(f)) throw new Error('missing ' + f);
const candidate = fs.readFileSync(CLASSES, 'latin1');
for (const fence of ['/* JASPR_MELEE_FP_BEGIN */', '/* JASPR_COMBATFX_BEGIN */', '/* JASPR_GUNFX_BEGIN */', '/* JASPR_MOBENDS_BEGIN */'])
  if (!candidate.includes(fence)) throw new Error('the candidate client lacks ' + fence);
const ANCHOR = '$rt_globals.JasprWideBridge = {';
if (candidate.split(ANCHOR).length !== 2) throw new Error('the test anchor is not in the candidate exactly once');
// test-only window into the TeaVM module scope (the fixture's copy only)
const classesPath = path.join(out, 'classes.test.js');
fs.writeFileSync(classesPath, Buffer.from(candidate.replace(ANCHOR, '$rt_globals.__mc = function () { return HEH; }; $rt_globals.__eval = function (s) { return eval(s); }; ' + ANCHOR), 'latin1'));
// fresh plugin configs: AuthMe without GeoIP downloads or login timeout; Apocalypse peaceful (its siege undead cannot
// live, so nothing attacks the tester) with the ruins off
const authmeConfig = path.join(out, 'authme-config.yml'), apocalypseConfig = path.join(out, 'apocalypse-config.yml');
fs.writeFileSync(authmeConfig, 'DataSource:\n  backend: SQLITE\nsettings:\n  sessions:\n    enabled: false\n  registration:\n    enabled: true\n    force: true\n' +
  '  restrictions:\n    ForceSingleSession: true\n    kickNonRegistered: false\n    ProtectInventoryBeforeLogIn: false\n    timeout: 0\n    allowCommands:\n      - /login\n      - /register\n' +
  'Protection:\n  enableProtection: false\n  geoIpDatabase:\n    enabled: false\n');
fs.writeFileSync(apocalypseConfig, "world: world\ndifficulty: peaceful\nruins:\n  enabled: false\nresource-pack:\n  url: ''\n  sha1: ''\n");
const password = crypto.randomBytes(12).toString('base64').replace(/[^A-Za-z0-9]/g, 'x');   // throwaway, loopback fixture only

const uuid = () => crypto.randomUUID();
const expedition = (id, model) => 'minecraft:diamond_sword 1 ' + model + ' {Unbreakable:1b,HideFlags:4,JasprApocalypse:{id:"' + id + '",equipmentMark:"jaspr-expedition-v1",serial:"' + uuid() + '"}}';
const gun = (id, model, rounds) => 'minecraft:diamond_hoe 1 ' + model + ' {Unbreakable:1b,HideFlags:6,JasprApocalypse:{id:"' + id + '",arsenalMark:"jaspr-arsenal-v1",serial:"' + uuid() + '",rounds:' + rounds + '}}';

function stopChrome() {
  if (!profile) return;
  const tag = path.basename(profile).replace(/[^A-Za-z0-9_-]/g, '');
  const ps = "for($i=0;$i -lt 6;$i++){ $ps=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'chrome.exe' -and $_.CommandLine -like '*" + tag + "*' }; if(-not $ps){break}; foreach($p in $ps){ Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 400 }";
  try { spawnSync('powershell', ['-NoProfile', '-Command', ps], {windowsHide: true, timeout: 40000}); } catch (e) { }
  try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { }
  profile = null;
}
async function stopFixture() {
  if (!fixture) return;
  if (fixture.exitCode === null) { try { fixture.stdin.write('stop\n'); } catch (e) { } }
  for (let i = 0; i < 60 && fixture.exitCode === null; i++) await sleep(500);
  if (fixture.exitCode === null) { try { fixture.kill(); } catch (e) { } await sleep(2000); }
  // keep the whole server log, then remove the throwaway fixture (worlds, plugin copies, the test account database)
  if (fixtureDir && path.basename(fixtureDir).startsWith('tank-preview-') && path.dirname(fixtureDir) === path.join(FIXTURE_ROOT, 'candidate')) {
    try { fs.copyFileSync(path.join(fixtureDir, 'paper.log'), path.join(out, 'server.txt')); } catch (e) { }
    for (let i = 0; i < 5; i++) { try { fs.rmSync(fixtureDir, {recursive: true, force: true}); break; } catch (e) { await sleep(1500); } }
  }
  fixture = null;
}

(async () => {
  fixture = spawn(process.execPath, [path.join(ROOT, 'scripts', 'tank-preview.cjs')], {cwd: ROOT, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.assign({}, process.env, {TANK_PREVIEW_ROOT: FIXTURE_ROOT, TANK_PREVIEW_CLASSES: classesPath, TANK_PREVIEW_ASSETS: ASSETS,
      TANK_PREVIEW_PLUGINS: PLUGINS.join(','), TANK_PREVIEW_XMX: '768M',
      TANK_PREVIEW_COPY: [LIVE_JAR + '=>paper.jar', authmeConfig + '=>plugins/AuthMe/config.yml', apocalypseConfig + '=>plugins/JasprApocalypse/config.yml'].join(';')})});
  try { os.setPriority(fixture.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  let fxOut = '';
  fixture.stdout.on('data', d => fxOut += d); fixture.stderr.on('data', d => fxOut += d);
  const ready = async () => { const end = Date.now() + 300000; while (Date.now() < end) { if (/Preview URL: (\S+)/.test(fxOut) && /Done \(/.test(fxOut)) return true; if (fixture.exitCode !== null) return false; await sleep(500); } return false; };
  const started = await ready();
  if (/Fixture: (\S+)/.test(fxOut)) fixtureDir = path.resolve(fxOut.match(/Fixture: (\S+)/)[1]);
  if (!started) throw new Error('fixture did not start:\n' + fxOut.slice(-2500));
  const url = fxOut.match(/Preview URL: (\S+)/)[1], webPort = +new URL(url).port;
  const cmd = async c => { const t = await getText(webPort, '/console?c=' + encodeURIComponent(c)); await sleep(250); return t; };
  const serverLog = () => getText(webPort, '/log').catch(() => '');
  // the throwaway account is registered while the tester is offline (AuthMe kicks an online player an admin registers);
  // AuthMe hashes off-thread
  await cmd('authme register ' + NAME + ' ' + password);
  await sleep(2500);

  const debugPort = 9300 + Math.floor(Math.random() * 600);
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-combat-probe-'));
  const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
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
  await send('Emulation.setFocusEmulationEnabled', {enabled: true});   // a focused, active page (the game grabs the mouse only then)
  await send('Emulation.setDeviceMetricsOverride', {width: W, height: H, deviceScaleFactor: 1, mobile: false, screenOrientation: {type: 'landscapePrimary', angle: 0}});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: "(function(){var locked=null;Object.defineProperty(Document.prototype,'pointerLockElement',{get:function(){return locked;},configurable:true});Element.prototype.requestPointerLock=function(){locked=this;document.dispatchEvent(new Event('pointerlockchange'));};Document.prototype.exitPointerLock=function(){locked=null;document.dispatchEvent(new Event('pointerlockchange'));};})();"});
  // lean test-only settings (players' settings are untouched): render distance 2, 15 fps; particles and the particle
  // effects stay on so trails, sparks, muzzle flashes and casings can be checked
  const video = {version: 1, tier: -1, auto: false, values: {renderDistance: 2, maxFps: 15, fancyGraphics: 0, ao: 0, particles: 0, clouds: 0, entityShadows: 0, vsync: 0,
    mipmaps: 0, chunkUpdates: 1, fog: 1, viewBobbing: 0, resolution: 100, chunkBudget: 1, entityDistance: 32, fastVisibility: 1, animations: 0, ambientEffects: 0,
    weatherEffects: 0, particleEffects: 1, dynamicLights: 0, gore: 0, shader: 0, dhEnabled: 0, showFps: 0, showCoords: 0, mobileControls: 0, touchSensitivity: 100}};
  await send('Page.addScriptToEvaluateOnNewDocument', {source: 'try{localStorage.setItem("jaspr.video.v1",' + JSON.stringify(JSON.stringify(video)) + ');}catch(e){}'});
  await send('Page.navigate', {url: url + '#' + NAME});
  // the pointer rests in the middle from the start, so later clicks there never turn the view
  await sleep(1500);
  await send('Input.dispatchMouseEvent', {type: 'mouseMoved', x: CX, y: CY});

  const shots = [];
  const shot = async name => {
    const r = await Promise.race([send('Page.captureScreenshot', {format: 'jpeg', quality: 72}), sleep(20000).then(() => null)]);
    if (!r || !r.result) { console.log('shot ' + name + ' TIMEOUT'); return false; }
    fs.writeFileSync(path.join(out, name + '.jpg'), Buffer.from(r.result.data, 'base64'));
    shots.push(name + '.jpg');
    return true;
  };
  const press = (button, type) => send('Input.dispatchMouseEvent', {type, x: CX, y: CY, button, buttons: type === 'mousePressed' ? (button === 'left' ? 1 : 2) : 0, clickCount: 1});
  const click = async (button, hold) => { await press(button, 'mousePressed'); await sleep(hold || 70); await press(button, 'mouseReleased'); };
  const key = async (k, code, vk) => { await send('Input.dispatchKeyEvent', {type: 'keyDown', key: k, code, windowsVirtualKeyCode: vk}); await sleep(80); await send('Input.dispatchKeyEvent', {type: 'keyUp', key: k, code, windowsVirtualKeyCode: vk}); };
  const diag = () => evaluate('JSON.stringify({melee:window.JasprMeleeDiagnostics?JasprMeleeDiagnostics.status():null,fx:window.JasprCombatFXDiagnostics?JasprCombatFXDiagnostics.status():null,' +
    'gun:window.JasprGunFXDiagnostics?JasprGunFXDiagnostics.status():null,bc:window.JasprBetterCombatStatus?JasprBetterCombatStatus():null,mb:window.JasprMoBendsDiagnostics?JasprMoBendsDiagnostics.status():null})').then(t => (t ? JSON.parse(t) : null));
  const feedSeq = () => inner('JasprCombatFeed.seq');
  const feedSince = s => inner('JasprCombatFeed.since(' + s + ')').then(e => (Array.isArray(e) ? e : []));
  const verdictOf = (events, swing) => swing && events.find(e => e.ref === swing.seq && e.kind !== 'swing');
  const waitVerdicts = async (from, swings, ms) => {
    const end = Date.now() + ms; let events = [];
    while (Date.now() < end) {
      events = await feedSince(from);
      const sw = events.filter(e => e.kind === 'swing');
      if (sw.length >= swings && sw.every(s => verdictOf(events, s))) break;
      await sleep(150);
    }
    return events;
  };
  const clipsDelta = (a, b) => { const o = {}; for (const k in b.melee.clips) { const d = b.melee.clips[k] - ((a.melee.clips || {})[k] || 0); if (d > 0) o[k] = d; } return o; };
  const delta = (a, b, f) => f(b) - f(a);
  const equip = async (main, off) => {
    await cmd('replaceitem entity ' + NAME + ' slot.weapon.mainhand ' + main);
    await cmd('replaceitem entity ' + NAME + ' slot.weapon.offhand ' + (off || 'minecraft:air'));
  };
  const view = lv => inner('(function(){HEH.G.lv=' + lv + ';return 1;})()');

  try {
    const inWorld = async ms => { const end = Date.now() + ms; while (Date.now() < end) { if (await evaluate('(function(){try{return !!(window.__mc&&__mc()&&__mc().v&&__mc().X);}catch(e){return false;}})()')) return true; await sleep(500); } return false; };
    check(await inWorld(240000), 'the client joins the fixture server');
    // the throwaway account (registered before the join) is logged in from the console
    let loggedIn = false;
    for (let i = 0; i < 6 && !loggedIn; i++) {
      await sleep(1200);
      await cmd('authme forcelogin ' + NAME);
      await sleep(800);
      loggedIn = /Force login for CombatTester performed/i.test(await serverLog());
    }
    check(loggedIn, 'the tester is logged in (AuthMe, console)');
    await cmd('gamemode 0 ' + NAME);
    await cmd('time set 6000');
    await cmd('gamerule doDaylightCycle false');
    await cmd('gamerule doMobSpawning false');
    await cmd('gamerule doWeatherCycle false');
    await cmd('weather clear');
    await cmd('effect ' + NAME + ' minecraft:resistance 600 4 true');
    let bc = null;
    for (let i = 0; i < 40; i++) { bc = await evaluate('JSON.stringify(window.JasprBetterCombatStatus?JasprBetterCombatStatus():null)').then(t => (t ? JSON.parse(t) : null)); if (bc && bc.active) break; await sleep(500); }
    let log = await serverLog();
    check(bc && bc.active && bc.combat.build === 'bettercombat9' && bc.combat.proto >= 2 && /COMBAT_CLIENT_HELLO player=CombatTester/.test(log),
      'Better Combat is active (hello answered, protocol 2, head bettercombat9)', bc && {active: bc.active, build: bc.combat.build, proto: bc.combat.proto, focus: bc.focus});
    let d = await diag();
    check(d && d.melee && !d.melee.error && d.fx && d.gun && d.mb && d.gun.enabled && d.gun.cameraApi && d.fx.camera.enabled && !d.mb.failed,
      'the melee module, COMBATFX, GUNFX and Mo\' Bends are installed (the gun stage found the camera API)', d && {gunCamera: d.gun && d.gun.cameraApi, camera: d.fx && d.fx.camera.enabled, mobends: d.mb && d.mb.enabled});
    // the in-game mouse: grab it with a click at nothing if the join did not
    if (!(bc && bc.focus)) { await click('left'); await sleep(800); }
    bc = await evaluate('JSON.stringify(JasprBetterCombatStatus())').then(t => JSON.parse(t));
    check(bc.focus === true && bc.screen === null, 'the game has the mouse (in-game focus, no screen open)', {focus: bc.focus, screen: bc.screen});

    // ---- the target: an iron golem (no AI, 1000 health) 2.5 blocks ahead; peaceful keeps other mobs away
    await cmd('kill @e[type=!player]');
    await cmd('minecraft:tp ' + NAME + ' ~ ~ ~ 0 0');   // vanilla tp: JasprApocalypse's /tp is a player-to-player request
    await sleep(600);
    await cmd('execute ' + NAME + ' ~ ~ ~ summon villager_golem ~ ~ ~2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1000.0f,Attributes:[{Name:"generic.maxHealth",Base:1000.0d}]}');
    await sleep(2500);
    await shot('00-target');

    // ---- 1. main-hand and off-hand hits by the server's verdict
    await equip('minecraft:diamond_sword', 'minecraft:iron_sword');
    await sleep(2200);
    let from = await feedSeq();
    await click('left');
    await sleep(140); await shot('10-main-hit');
    let events = await waitVerdicts(from, 1, 3000);
    let swing = events.find(e => e.kind === 'swing'), verdict = verdictOf(events, swing);
    check(swing && swing.hand === 'M' && swing.target >= 0 && verdict && verdict.kind === 'hit', 'a main-hand attack on the golem is a hit by the server\'s verdict',
      {swing: swing && {hand: swing.hand, target: swing.target, item: swing.item}, verdict: verdict && {kind: verdict.kind, crit: verdict.crit}});
    await sleep(1100);
    from = await feedSeq();
    await click('right');
    await sleep(140); await shot('11-off-hit');
    events = await waitVerdicts(from, 1, 3000);
    swing = events.find(e => e.kind === 'swing'); verdict = verdictOf(events, swing);
    check(swing && swing.hand === 'O' && swing.target >= 0 && verdict && verdict.kind === 'hit', 'an off-hand attack (right click) on the golem is a hit by the server\'s verdict',
      {swing: swing && {hand: swing.hand, target: swing.target, item: swing.item}, verdict: verdict && {kind: verdict.kind, crit: verdict.crit}});
    await sleep(1500);

    // ---- 2. alternating dual attacks, two pairs
    const dual = async (label, main, off, mainCls, offCls, spacing) => {
      await equip(main, off);
      await sleep(2600);
      const a = await diag(), f0 = await feedSeq(), seen = {main: new Set(), off: new Set(), support: new Set()};
      const pair = await inner('JasprMeleeDual.pair(' + JSON.stringify(mainCls) + ',' + JSON.stringify(offCls) + ')');
      let sampling = true;
      const sampler = (async () => {
        while (sampling) {
          const s = await evaluate('JSON.stringify(JasprMeleeDiagnostics.status().hands)').then(t => (t ? JSON.parse(t) : null));
          if (Array.isArray(s)) {
            if (s[0].strike) seen.main.add(s[0].strike);
            if (s[1].strike) seen.off.add(s[1].strike);
            for (const h of s) if (h.support) seen.support.add(h.support);
          }
          await sleep(90);
        }
      })();
      const t0 = Date.now();
      for (let i = 0; i < 4; i++) {
        const wait = t0 + i * spacing - Date.now();
        if (wait > 0) await sleep(wait);
        await click(i % 2 ? 'right' : 'left');
        if (i < 2) { await sleep(150); await shot(label + '-' + (i ? 'off' : 'main')); }
      }
      await sleep(1600);
      sampling = false; await sampler;
      const b = await diag(), ev = await feedSince(f0);
      const swings = ev.filter(e => e.kind === 'swing');
      const clips = clipsDelta(a, b);
      const mainClips = Object.keys(clips).filter(k => k.startsWith(mainCls + '-')), offClips = Object.keys(clips).filter(k => k.startsWith(offCls + '-'));
      check(swings.filter(e => e.hand === 'M').length >= 2 && swings.filter(e => e.hand === 'O').length >= 2 && seen.main.size && seen.off.size &&
        mainClips.length && offClips.length && delta(a, b, x => x.melee.supports) > 0 && delta(a, b, x => x.fx.fx.trails) > 0 &&
        [...seen.main].every(n => n.startsWith(mainCls + '-')) && [...seen.off].every(n => n.startsWith(offCls + '-')),
        label + ': both hands strike with their own moves, the waiting hand answers, trails drawn',
        {pair: pair && pair.name, mainStrikes: [...seen.main], offStrikes: [...seen.off], supports: [...seen.support], clips,
          supportTracks: delta(a, b, x => x.melee.supports), joint: delta(a, b, x => x.melee.joint), trails: delta(a, b, x => x.fx.fx.trails),
          verdicts: ev.filter(e => e.kind !== 'swing').map(e => e.hand + ':' + e.kind)});
    };
    await dual('20-sword-sword', 'minecraft:diamond_sword', 'minecraft:iron_sword', 'sword', 'sword', 380);
    // Mo' Bends, third person from behind: an off-hand attack swings the off arm
    await view(1);
    await sleep(1500);
    await click('right');
    await sleep(160); await shot('22-third-person-off-attack');
    await view(0);
    await sleep(1200);
    await dual('23-axe-dagger', 'minecraft:diamond_axe', expedition('trench_blade', 1530), 'axe', 'dagger', 450);
    await sleep(1500);

    // ---- 3. one weapon of each class: its own move, a trail, a hit-stop and a camera kick
    const classes = [['sword', 'minecraft:diamond_sword'], ['axe', 'minecraft:diamond_axe'], ['hammer', expedition('gravity_maul', 1490)],
      ['dagger', expedition('trench_blade', 1530)], ['spear', expedition('sentinel_spear', 1460)]];
    for (const [cls, item] of classes) {
      await equip(item, null);
      await sleep(2600);
      const a = await diag(), f0 = await feedSeq();
      await click('left');
      await sleep(140); await shot('30-' + cls);
      const ev = await waitVerdicts(f0, 1, 3000);
      await sleep(900);
      const b = await diag(), sw = ev.find(e => e.kind === 'swing'), v = verdictOf(ev, sw), clips = clipsDelta(a, b);
      const own = Object.keys(clips).filter(k => k.startsWith(cls + '-'));
      check(b.melee.hands[0].cls === cls && own.length > 0 && v && v.kind === 'hit' && delta(a, b, x => x.melee.stops) > 0 && delta(a, b, x => x.fx.fx.trails) > 0 &&
        delta(a, b, x => x.fx.camera.kicks) > 0 && delta(a, b, x => x.fx.fx.impacts) > 0,
        cls + ': its own move hits with a trail, a hit-stop and a camera kick',
        {cls: b.melee.hands[0].cls, moves: own, verdict: v && v.kind, hitStops: delta(a, b, x => x.melee.stops), trails: delta(a, b, x => x.fx.fx.trails),
          trailQuads: delta(a, b, x => x.fx.fx.trailQuads), kicks: delta(a, b, x => x.fx.camera.kicks), sparks: delta(a, b, x => x.fx.fx.sparks), lastKick: b.fx.camera.last});
    }

    // ---- 4. a right click on a villager with a sword in the off hand trades
    await cmd('kill @e[type=villager_golem]');
    await sleep(800);
    await cmd('execute ' + NAME + ' ~ ~ ~ summon villager ~ ~ ~2.2 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Profession:0}');
    await equip('minecraft:diamond_sword', 'minecraft:iron_sword');
    await sleep(2600);
    from = await feedSeq();
    await click('right');
    await sleep(1800);
    const trading = await inner('HEH.cj!==null&&HEH.cj instanceof AQH');
    events = await feedSince(from);
    await shot('40-villager-trade');
    check(trading === true && !events.some(e => e.kind === 'swing' && e.hand === 'O'), 'a right click on a villager with a sword in the off hand opens trading, no off-hand swing',
      {trading, events: events.map(e => e.hand + ':' + e.kind)});
    await key('Escape', 'Escape', 27);
    await sleep(1200);
    await cmd('kill @e[type=villager]');

    // ---- 5. guns: semi-automatic, automatic, a reload; recoil and camera kicks
    await cmd('minecraft:tp ' + NAME + ' ~ ~ ~ 90 -15');
    await equip(gun('sepulcher', 1450, 12), null);
    await sleep(2200);
    let a = await diag();
    for (let i = 0; i < 3; i++) { await click('right'); if (i === 0) { await sleep(70); await shot('50-sepulcher-shot'); } await sleep(650); }
    await sleep(800);
    let b = await diag();
    const gstat = (x, k) => (x.gun.stats[k] || 0);
    check(delta(a, b, x => gstat(x, 'core_shots')) >= 3 && delta(a, b, x => gstat(x, 'posed')) > 0 && delta(a, b, x => gstat(x, 'kicks')) >= 3 &&
      delta(a, b, x => gstat(x, 'kickMissing')) === 0 && delta(a, b, x => x.fx.camera.kicks) >= 3 && b.fx.camera.last && b.fx.camera.last.pitch > 0,
      'Sepulcher (semi-automatic): three shots, recoil poses, kicks through JasprCameraFX (view climbs)',
      {shots: delta(a, b, x => gstat(x, 'core_shots')), posed: delta(a, b, x => gstat(x, 'posed')), flashes: delta(a, b, x => gstat(x, 'flashes')),
        spawned: delta(a, b, x => gstat(x, 'spawned')), gunKicks: delta(a, b, x => gstat(x, 'kicks')), cameraKicks: delta(a, b, x => x.fx.camera.kicks), last: b.fx.camera.last, hand: b.gun.hands[0]});
    await equip(gun('whisper', 1530, 24), null);
    await sleep(2200);
    a = await diag();
    await press('right', 'mousePressed');
    await sleep(900); await shot('51-whisper-auto');
    await sleep(900);
    await press('right', 'mouseReleased');
    await sleep(900);
    b = await diag();
    check(delta(a, b, x => gstat(x, 'core_shots')) >= 3 && delta(a, b, x => x.fx.camera.kicks) >= 3, 'Whisper (automatic, trigger held): sustained fire with a kick per shot',
      {shots: delta(a, b, x => gstat(x, 'core_shots')), cameraKicks: delta(a, b, x => x.fx.camera.kicks), posed: delta(a, b, x => gstat(x, 'posed')), hand: b.gun.hands[0]});
    await cmd('give ' + NAME + ' minecraft:iron_nugget 64');
    await equip(gun('sepulcher', 1450, 2), null);
    await sleep(2200);
    a = await diag();
    await click('right'); await sleep(650); await click('right');
    await sleep(700); await shot('52-reload');
    await sleep(2800);
    b = await diag();
    check(delta(a, b, x => gstat(x, 'core_shots')) >= 2 && delta(a, b, x => gstat(x, 'core_reloads')) >= 1 && delta(a, b, x => gstat(x, 'core_reloadsDone')) >= 1 && b.gun.hands[0].rounds > 0,
      'the emptied Sepulcher reloads itself: reload animation started and finished, rounds back',
      {shots: delta(a, b, x => gstat(x, 'core_shots')), reloads: delta(a, b, x => gstat(x, 'core_reloads')), done: delta(a, b, x => gstat(x, 'core_reloadsDone')),
        sounds: delta(a, b, x => gstat(x, 'sounds')), hand: b.gun.hands[0]});

    // ---- the end: nothing switched off, no exceptions
    d = await diag();
    const bcs = await evaluate('JSON.stringify(JasprBetterCombat.status())').then(t => (t ? JSON.parse(t) : null)).catch(() => null);
    check(d.melee.errors === 0 && d.fx.fx.errors === 0 && d.fx.fx.parts.kicks && d.fx.fx.parts.sparks && d.fx.fx.parts.trails && d.fx.camera.enabled &&
      Object.keys(d.gun.disabled).length === 0 && d.gun.stats.errors === 0 && !d.mb.failed && (!bcs || !bcs.lastError),
      'no combat stage part was switched off',
      {melee: d.melee.errors, fx: d.fx.fx.errors, fxLast: d.fx.fx.lastError, parts: d.fx.fx.parts, gunDisabled: d.gun.disabled, gunError: d.gun.lastError, mobends: d.mb.failed ? d.mb.lastError : 'ok', bettercombat: bcs && bcs.lastError});
    const others = await evaluate('JSON.stringify(Object.keys(window).filter(function(k){return /^Jaspr.*Diagnostics$/.test(k);}).map(function(k){try{var s=window[k]&&typeof window[k].status==="function"?window[k].status():null;' +
      'return [k,s&&(s.disabled&&typeof s.disabled==="object"&&Object.keys(s.disabled).length?s.disabled:(s.failed?s.lastError||"failed":null))];}catch(e){return [k,"status threw"];}}))').then(t => (t ? JSON.parse(t) : []));
    note('every stage diagnostics object (disabled parts or failures)', others.filter(x => x[1]));
    note('Mo\' Bends counters', d.mb.stats);
    note('melee counters', {strikes: d.melee.strikes, impacts: d.melee.impacts, stops: d.melee.stops, supports: d.melee.supports, joint: d.melee.joint, feedEvents: d.melee.feedEvents, clips: d.melee.clips});
    note('COMBATFX counters', d.fx);
    check(await evaluate('(function(){try{return !!(__mc()&&__mc().v);}catch(e){return false;}})()'), 'the client keeps running');
    const errors = consoleLog.filter(l => /EXCEPTION/.test(l));
    check(errors.length === 0, 'no page exceptions', errors.slice(0, 3));
    const warnings = consoleLog.filter(l => /\[JasperCraft (combat FX|gun animation)\]/.test(l));
    check(warnings.length === 0, 'no combat stage warnings in the console', warnings.slice(0, 3));
    log = await serverLog();
    const exceptions = (log.match(/.*(Exception|Error:).*/g) || []).filter(l => !/Can't read input file/.test(l));
    check(exceptions.length === 0, 'no server exceptions', exceptions.slice(0, 3));
  } finally {
    fs.writeFileSync(path.join(out, 'console.txt'), consoleLog.join('\n') + '\n');
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({results, shots}, null, 2) + '\n');
    try { ws.close(); } catch (e) { }
    stopChrome();
    await stopFixture();
  }
  console.log('screenshots: ' + shots.map(s => path.join(out, s)).join(' '));
  console.log((failures ? 'COMBAT_BROWSER_FAIL ' : 'COMBAT_BROWSER_PASS ') + 'checks=' + checks + ' failures=' + failures + ' out=' + out);
  process.exitCode = failures ? 1 : 0;
  setTimeout(() => process.exit(), 5000).unref();
})().catch(async e => { console.log('COMBAT_BROWSER_FAIL ' + (e && e.stack || e)); stopChrome(); await stopFixture(); process.exitCode = 1; setTimeout(() => process.exit(1), 5000).unref(); });
setTimeout(() => { console.log('COMBAT_BROWSER_FAIL timeout'); stopChrome(); try { if (fixture) fixture.stdin.write('stop\n'); } catch (e) { } setTimeout(() => process.exit(3), 20000); }, 14 * 60 * 1000).unref();
