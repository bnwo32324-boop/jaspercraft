'use strict';
/* Survivor Gear Phase 2 HUD render check in the real TeaVM client (headless Chromium).
 * Boots candidate/gear-client/classes.js + candidate/gear/assets.epk from a loopback static page,
 * enters the client-side Structure Testing Grounds world (its owner check is answered by this
 * fixture's own loopback /api stubs; nothing leaves 127.0.0.1), injects HUD packets through
 * JasprGear.receive (the handler behind the jaspr:gear channel; exposed to the page by rewriting
 * the served copy only) and saves screenshots: bar + statuses, low bar, hidden while chat is open.
 * Needs playwright-core (NODE_PATH) and Chromium (CHROMIUM or /opt/pw-browsers).
 *   NODE_PATH=... node tests/gear-hud-browser.cjs [out-dir]
 */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname, '..'), out = path.resolve(process.argv[2] || path.join(root, 'candidate', 'gear-hud'));
const classes = fs.readFileSync(path.join(root, process.env.GEAR_CLASSES || 'candidate/gear-client/classes.js'), 'latin1')
  .replace('window.JasprGearDiagnostics = Object.freeze({', 'window.__JasprGearTest = JasprGear; window.JasprGearDiagnostics = Object.freeze({');
const epk = path.join(root, process.env.GEAR_EPK || 'candidate/gear/assets.epk');
const html = `<!doctype html><html><head><meta charset="UTF-8"><title>Gear HUD fixture</title>
<style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:black}</style>
<script>window.JasprAccountName="jasper_e_";</script><script src="/classes.js"></script></head><body><div id="game_frame"></div><script>
window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_gear_hud',
worldsDB:'gear_hud_worlds',resourcePacksDB:'gear_hud_packs',servers:[],relays:[],crashOnUncaughtExceptions:true};
window.addEventListener('load',function(){main();});</script></body></html>`;
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  fs.mkdirSync(out, {recursive: true});
  const web = http.createServer((req, res) => {
    const key = req.url.split('?')[0], json = v => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(v)); };
    if (key === '/') { res.setHeader('Content-Type', 'text/html'); return res.end(html); }
    if (key === '/classes.js') { res.setHeader('Content-Type', 'application/javascript'); return res.end(Buffer.from(classes, 'latin1')); }
    if (key === '/assets.epk') return fs.createReadStream(epk).pipe(res);
    if (key === '/api/jaspercraft/me') return json({authenticated: true, gameName: 'jasper_e_'});
    if (key === '/api/bootstrap') return json({currentUser: {isOwner: true}});
    if (key === '/jaspercraft-grounds-world.js' || key === '/jaspercraft-grounds-index.js') {
      const f = path.join(root, 'site', key);
      res.setHeader('Content-Length', fs.statSync(f).size);
      return fs.createReadStream(f).pipe(res);
    }
    res.writeHead(404); res.end();
  }).listen(0, '127.0.0.1');
  await new Promise(r => web.once('listening', r));
  const exe = process.env.CHROMIUM || fs.readdirSync('/opt/pw-browsers').filter(d => d.startsWith('chromium-')).map(d => `/opt/pw-browsers/${d}/chrome-linux/chrome`).find(f => fs.existsSync(f));
  const browser = await chromium.launch({executablePath: exe, args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist']});
  const page = await browser.newPage({viewport: {width: 960, height: 540}});
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror ' + e));
  page.on('console', m => { if (/HUD disabled|panel disabled|Uncaught/i.test(m.text())) errors.push('console ' + m.text()); });
  await page.goto(`http://127.0.0.1:${web.address().port}/`);
  const shot = async name => { await page.screenshot({path: path.join(out, name + '.png')}); console.log('shot ' + name); };
  const gear = expr => page.evaluate('(function(G){' + expr + '})(window.__JasprGearTest)');
  await sleep(16000);
  await page.mouse.click(480, 445); // first run: Edit Profile -> Done
  await sleep(4000); await shot('01-menu');
  await page.mouse.click(480, 345); // Structure Testing Grounds row
  await sleep(45000); await shot('02-world');
  await gear("G.receive(JSON.stringify({v:1,t:'hud',on:true,a:64,m:110,fx:[['bleed',4],['vigor',38],['para',1]]}));");
  await sleep(1500); await shot('03-hud');
  const drawn = await gear('return G.status();');
  await gear("G.receive(JSON.stringify({v:1,t:'hud',on:true,a:12,m:100,fx:[]}));");
  await sleep(1000); await shot('04-hud-low');
  await page.keyboard.press('t'); await sleep(800);
  const f0 = (await gear('return G.status();')).hudFrames;
  await sleep(1000); await shot('05-chat-open');
  const f1 = (await gear('return G.status();')).hudFrames;
  await page.keyboard.press('Escape');
  const report = {drawn, framesWhileChatOpen: f1 - f0, errors};
  fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close(); web.close();
  const pass = drawn.hudFrames > 0 && !drawn.hudDisabled && drawn.hud && drawn.hud.adrenaline === 64 && f1 === f0 && !errors.length;
  console.log(pass ? 'GEAR_HUD PASS' : 'GEAR_HUD FAIL');
  process.exit(pass ? 0 : 1);
})().catch(e => { console.error(e); process.exit(1); });
