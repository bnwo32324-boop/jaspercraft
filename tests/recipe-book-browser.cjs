'use strict';
/* EasierCrafting panel in the real TeaVM client (headless Chromium), in the client-side Structure
 * Testing Grounds world (vanilla recipes only: no plugins in singleplayer). Switches to survival, gives
 * logs, cobblestone, sticks, charcoal, leather and string, opens the inventory and checks the panel:
 * vanilla recipes that fit the 2x2 grid are listed (planks, torches), 3x3-only ones are not (furnace,
 * the satchel), then shift-clicks Oak Wood Planks and checks all seven logs became 28 planks.
 * Same loopback fixture as tests/gear-hud-browser.cjs (the served copy exposes the engine for inspection).
 *   NODE_PATH=<playwright-core> node tests/recipe-book-browser.cjs [out-dir]
 */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname, '..'), out = path.resolve(process.argv[2] || path.join(root, 'candidate', 'recipe-book'));
const classes = fs.readFileSync(path.join(root, process.env.GEAR_CLASSES || 'candidate/gear-client/classes.js'), 'latin1')
  .replace('window.JasprGearDiagnostics = Object.freeze({', 'window.__JasprMc = function () { return HEH; }; window.JasprGearDiagnostics = Object.freeze({')
  .replace('function JasprUiRepairDue() {', 'function JasprUiRepairDue() { return false;')
  .replace('window.JasprRecipeBookDiagnostics = Object.freeze({', 'window.__RB = JasprRecipeBook; window.__RT = JasprRecipeTable; window.JasprRecipeBookDiagnostics = Object.freeze({');
const epk = path.join(root, process.env.GEAR_EPK || 'site/assets.epk');
const html = `<!doctype html><html><head><meta charset="UTF-8"><title>Recipe book fixture</title>
<style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:black}</style>
<script>window.JasprAccountName="jasper_e_";</script><script src="/classes.js"></script></head><body><div id="game_frame"></div><script>
window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_recipe_book',
worldsDB:'recipe_book_worlds',resourcePacksDB:'recipe_book_packs',servers:[],relays:[],crashOnUncaughtExceptions:true};
window.addEventListener('load',function(){main();});</script></body></html>`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
let failures = 0, checks = 0;
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }

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
  page.on('console', m => { if (/recipe book\] disabled|Uncaught/i.test(m.text())) errors.push('console ' + m.text()); });
  await page.goto(`http://127.0.0.1:${web.address().port}/`);
  const shot = async name => { await page.screenshot({path: path.join(out, name + '.png')}); console.log('shot ' + name); };
  const inWorld = () => page.evaluate('(function(){try{return !!window.__JasprMc().v;}catch(e){return false;}})()');
  const key = async k => { await page.keyboard.down(k); await sleep(120); await page.keyboard.up(k); await sleep(500); };
  const say = async text => { await key('t'); await sleep(400); await page.keyboard.type(text, {delay: 15}); await key('Enter'); await sleep(700); };

  await sleep(16000);
  await page.mouse.click(480, 445); await sleep(4000);   // first run: Edit Profile -> Done
  await page.mouse.click(480, 393); await sleep(4000);   // "Default Username Detected" -> Continue Anyway
  for (let attempt = 0; attempt < 3 && !(await inWorld()); attempt++) {
    await page.mouse.click(480, 345);                     // Structure Testing Grounds row
    for (let t = 0; t < 30 && !(await inWorld()); t++) await sleep(2000);
  }
  check(await inWorld(), 'entered the test world');
  await sleep(3000);
  await say('/gamemode 0');
  await say('/clear');
  for (const g of ['log 7', 'cobblestone 20', 'stick 6', 'coal 3 1', 'leather 6', 'string 2']) await say('/give @p ' + g);
  await sleep(1500);
  await key('e'); await sleep(2500);
  await shot('01-inventory-panel');
  const status = await page.evaluate('window.JasprRecipeBookDiagnostics.status()');
  check(status.open && status.grid === 2 && !status.disabled, 'panel attached to the 2x2 inventory', {open: status.open, grid: status.grid, failure: status.failure});
  const listed = new Set(status.titles);
  for (const k of ['minecraft:oak_planks', 'minecraft:torch']) check(listed.has(k), 'lists ' + k);
  for (const k of ['minecraft:furnace', 'jasprgear:satchel', 'minecraft:stone_pickaxe']) check(!listed.has(k), 'hides 3x3-only ' + k);
  console.log('categories ' + JSON.stringify(status.categories) + ' craftable=' + status.craftable);

  // Shift-click Oak Wood Planks in the panel.
  const where = await page.evaluate(`(function(){
    var RB = window.__RB, book = RB.books()[RB.books().length - 1];
    var idx = -1; for (var i = 0; i < window.__RT.recipes.length; i++) if (window.__RT.recipes[i].key === 'minecraft:oak_planks') idx = i;
    var item = book.lastPlan.items.filter(function (x) { return x.i === idx; })[0];
    return item ? {x: item.x, y: item.y, left: book.gui.is | 0, top: book.gui.l7 | 0, idx: idx} : null; })()`);
  check(!!where, 'Oak Wood Planks drawn in the panel');
  let hovered = false, px = 0, py = 0;
  for (const scale of [2, 3, 1, 1.5]) {
    if (!where) break;
    px = (where.left + where.x + 8) * scale; py = (where.top + where.y + 8) * scale;
    await page.mouse.move(px, py); await sleep(400);
    hovered = await page.evaluate(`window.__RB.books()[window.__RB.books().length - 1].hover === ${where.idx}`);
    if (hovered) break;
  }
  check(hovered, 'hovering shows the recipe (ingredients under the window)');
  await shot('02-hover');
  await page.keyboard.down('Shift'); await sleep(200); await page.mouse.click(px, py); await sleep(400); await page.keyboard.up('Shift');
  await sleep(2500);
  const counts = await page.evaluate(`(function(){
    var RB = window.__RB, book = RB.books()[RB.books().length - 1], prep = RB.prepared();
    var planks = prep.results[${where ? where.idx : 0}].rA, n = {planks: 0};
    for (var s = 0; s < book.inventory.length; s++) {
      var st = book.inventory[s]; if (RB.empty(st)) continue;
      if (st.rA === planks && (st.bK | 0) === 0) n.planks += st.PD | 0;
    }
    var grid = book.craftStacks.filter(function (x) { return !RB.empty(x); }).length;
    return {planks: n.planks, gridBusy: grid, pending: !!book.pending}; })()`);
  check(counts.planks === 28 && counts.gridBusy === 0, 'shift-click crafted all seven logs into 28 planks', counts);
  await shot('03-after-craft');
  const after = await page.evaluate('window.JasprRecipeBookDiagnostics.status()');
  check(new Set(after.titles).has('minecraft:crafting_table') && new Set(after.titles).has('minecraft:stick'), 'planks unlock crafting table and sticks');
  check(!errors.length && !after.disabled, 'no errors', errors.concat(after.failure ? [after.failure] : []));
  fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify({status, counts, after, errors}, null, 2));
  await browser.close(); web.close();
  console.log((failures ? 'RECIPE_BROWSER FAIL' : 'RECIPE_BROWSER PASS') + ' checks=' + checks + ' failures=' + failures);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
