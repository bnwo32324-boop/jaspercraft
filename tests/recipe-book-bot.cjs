'use strict';
/* EasierCrafting click scripts against the real server (every live plugin): a mineflayer client opens a
 * crafting table, the panel's own engine (client-mods/recipe-book-teavm.js, run here in Node) decides
 * what is craftable and which slot clicks to send, and the clicks are sent as raw window clicks, exactly
 * what the browser client sends. Checks the server hands out the right item for vanilla (shift-crafting
 * several at once), Survivor Gear, a backpack and JasperCraft blueprints (one plain click).
 * Results are read from the server itself ("clear <player> <item> <data> 0" counts without removing),
 * because a bot's own inventory view lags behind after a window closes.
 *   GEAR_CMDS=<file the server console reads>  GEAR_LOG=<its output, default server.out next to it>
 *   GEAR_PORT=25597  NODE_PATH=<mineflayer>  node tests/recipe-book-bot.cjs
 */
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const mineflayer = require('mineflayer');
const Item = require('prismarine-item')('1.12.2');
const {Vec3} = require('vec3');
const PORT = Number(process.env.GEAR_PORT || 25597), CMDS = process.env.GEAR_CMDS;
const LOG = process.env.GEAR_LOG || path.join(path.dirname(CMDS || '.'), 'server.out');
if (!CMDS) { console.error('GEAR_CMDS is required'); process.exit(2); }
let failures = 0, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = line => fs.appendFileSync(CMDS, line + '\n');
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }

// The panel's engine, with item identity by registry name.
const ctx = {Ktg: {rA: null, bg_: true}, $rt_globals: {}, console};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'client-mods', 'recipe-book-teavm.js'), 'utf8')
  + '\nthis.RB = JasprRecipeBook; this.TABLE = JasprRecipeTable;', ctx);
const {RB, TABLE} = ctx;
const items = new Map(), item = id => { if (!items.has(id)) items.set(id, {id}); return items.get(id); };
const prep = RB.newPrepared();
for (const job of RB.jobs()) {
  const id = /id:"([^"]+)"/.exec(job.snbt)[1], dmg = /Damage:(-?\d+)s/.exec(job.snbt);
  RB.store(prep, job, {rA: item(id), bK: dmg ? +dmg[1] : 0, PD: 1, bV: null});
}
RB.setPrepared(prep);
const toStack = it => it ? {rA: item('minecraft:' + it.name), bK: it.metadata | 0, PD: it.count, bV: it.nbt ? {} : null} : null;
const index = key => TABLE.recipes.findIndex(r => r.key === key);

(async () => {
  const bot = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: 'Crafter', version: '1.12.2', auth: 'offline'});
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('kicked', r => reject(new Error('kicked ' + r))); bot.once('error', reject); });
  await sleep(1200);
  const X = 480, Y = 200, Z = 480;
  const TURRET = /id:"(minecraft:\w+)"/.exec(TABLE.recipes[index('jasprapocalypse:sentry_turret')].result)[1] + ' -1 0';
  console_('op Crafter'); console_('gamemode creative Crafter'); console_(`minecraft:tp Crafter ${X} ${Y + 1} ${Z}`);
  await sleep(2500);
  console_(`fill ${X - 4} ${Y} ${Z - 4} ${X + 4} ${Y} ${Z + 4} stone`);
  console_(`setblock ${X + 2} ${Y + 1} ${Z} crafting_table`);
  console_(`minecraft:tp Crafter ${X} ${Y + 1} ${Z}`);
  await sleep(1000);
  console_('gamemode survival Crafter');
  await sleep(800);
  let actionId = 30000, marker = 0;
  /** How many of an item the server says Crafter holds. */
  async function count(spec) {
    const tag = 'CHK' + (++marker) + '_' + Date.now();
    const before = fs.statSync(LOG).size;
    console_('say ' + tag);
    console_('clear Crafter ' + spec);
    for (let t = 0; t < 40; t++) {
      await sleep(100);
      const text = fs.readFileSync(LOG, 'utf8').slice(before);
      const at = text.indexOf(tag);
      if (at < 0) continue;
      const rest = text.slice(at);
      const m = /Crafter has (\d+) items? that match/.exec(rest);
      if (m) return +m[1];
      if (/Could not clear the inventory of Crafter/.test(rest)) return 0;
    }
    return -1;
  }

  async function craft(label, give, key, shift, expect) {
    console_('clear Crafter');
    await sleep(500);
    for (const g of give) console_('give Crafter ' + g);
    await sleep(1200);
    const table = bot.blockAt(new Vec3(X + 2, Y + 1, Z));
    const opened = new Promise(r => bot.once('windowOpen', r));
    await bot.activateBlock(table);
    const w = await Promise.race([opened, sleep(4000).then(() => null)]);
    if (!w) { check(false, label + ': table opened'); return; }
    await sleep(400);
    const book = {grid: 3, firstCraft: 1, resultSlot: 0, firstInv: 10, craftStacks: [],
      inventory: w.slots.slice(10, 46).map(toStack)};
    RB.computeCraftable(book);
    const i = index(key);
    check(i >= 0 && !!book.craftable.set[i], label + ': listed as craftable');
    const clicks = RB.clickScript(book, i, 0, shift);
    // Like the browser client: fill clicks are predicted locally and confirmed by the server...
    for (const [slot, button] of clicks) { try { await bot.clickWindow(slot, button, 0); } catch (e) { } }
    // ...and the result is taken only once the server has put it in the output slot.
    book.pending = {index: i, until: Date.now() + 1500};
    let finish = null;
    while (!finish && book.pending) {
      await sleep(50);
      book.resultStack = toStack(w.slots[0]);
      book.inventory = w.slots.slice(10, 46).map(toStack);
      finish = RB.finish(book);
    }
    check(!!finish, label + ': result appeared in the output');
    for (const [slot, button, mode] of finish || []) {
      bot._client.write('window_click', {windowId: w.id, slot, mouseButton: button, action: actionId++,
        mode: mode === 'quick' ? 1 : 0, item: Item.toNotch(w.slots[slot] || null)});
      await sleep(60);
    }
    await sleep(800);
    bot.closeWindow(w);
    await sleep(800);
    const got = {};
    for (const [name, spec] of Object.entries(expect)) got[name] = await count(spec);
    const want = Object.fromEntries(Object.entries(expect).map(([k]) => [k, +k.split('=')[1]]));
    check(Object.keys(want).every(k => got[k] === want[k]), label, got);
  }

  await craft('oak planks, shift: all 7 logs at once', ['log 7'], 'minecraft:oak_planks', true,
    {'planks=28': 'minecraft:planks 0 0', 'log=0': 'minecraft:log -1 0'});
  await craft('torches from charcoal', ['stick 3', 'coal 2 1'], 'minecraft:torch', false,
    {'torch=4': 'minecraft:torch -1 0', 'charcoal=1': 'minecraft:coal 1 0', 'stick=2': 'minecraft:stick -1 0'});
  await craft('chest from mixed planks', ['planks 3 0', 'planks 6 2'], 'minecraft:chest', false,
    {'chest=1': 'minecraft:chest -1 0', 'planks=1': 'minecraft:planks -1 0'});
  await craft('stone pickaxe from split stacks', ['stick 1', 'stick 1', 'cobblestone 5'], 'minecraft:stone_pickaxe', false,
    {'pickaxe=1': 'minecraft:stone_pickaxe -1 0', 'cobblestone=2': 'minecraft:cobblestone -1 0', 'stick=0': 'minecraft:stick -1 0'});
  await craft('Leather Satchel (Survivor Gear backpack)', ['leather 6', 'string 2'], 'jasprgear:satchel', false,
    {'satchel=1': 'minecraft:stone_hoe -1 0 {JasprGearPack:{id:"satchel"}}', 'leather=0': 'minecraft:leather -1 0'});
  await craft('Sentry Turret (one plain click)', ['iron_ingot 4', 'iron_block 1'], 'jasprapocalypse:sentry_turret', false,
    {'iron=0': 'minecraft:iron_ingot -1 0', 'block=0': 'minecraft:iron_block -1 0', 'turret=1': TURRET});
  // Military Salvage: 4 iron nuggets + 1 iron ingot -> 4 tagged iron nuggets.
  await craft('Military Salvage blueprint (one plain click, x4)', ['iron_nugget 4', 'iron_ingot 1'], 'jasprapocalypse:jaspr_scrap', false,
    {'salvage=4': 'minecraft:iron_nugget -1 0 {JasprApocalypse:{id:"scrap"}}', 'nuggets=4': 'minecraft:iron_nugget -1 0', 'ingot=0': 'minecraft:iron_ingot -1 0'});
  bot.quit();
  console.log((failures ? 'RECIPE_BOOK FAIL' : 'RECIPE_BOOK PASS') + ' checks=' + checks + ' failures=' + failures);
  setTimeout(() => process.exit(failures ? 1 : 0), 300);
})().catch(e => { console.log('RECIPE_BOOK ERROR ' + (e && e.stack || e)); process.exit(1); });
