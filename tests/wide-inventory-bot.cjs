'use strict';
/* Wide inventory, server half (scripts/java/wide-inventory, JasprWide.java): one low-priority Paper test server running the
 * patched jar from candidate/wide-server (node scripts/build-wide-inventory-server.cjs) with the test-only WideProbe
 * plugin, and two mineflayer bots: "Plain" never says hello (any old or vanilla client) and "Wide" says "wide1" on
 * jaspr:inv as the JasperCraft client does. Checks the wire (window sizes, set-slot numbers, the answer), the inventory
 * (fill order, held slot, armour/off hand at their flat indices), Bukkit (setItem packets, setHeldItemSlot, click-event
 * slot numbers), shift-click, Creative, a chest window, and that saved extra slots survive a rejoin.
 *
 *   NODE_PATH=<node_modules with mineflayer> node tests/wide-inventory-bot.cjs
 */
const fs = require('node:fs'), path = require('node:path'), net = require('node:net'), {spawn, execFileSync} = require('node:child_process');
const mineflayer = require('mineflayer');
const {Vec3} = require('vec3');
const ROOT = path.join(__dirname, '..'), OUT = path.join(ROOT, 'candidate', 'wide-server'), PORT = 25683;
const JAVA = (process.env.JASPR_JDK || 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot') + '/bin/java';
const RUN = path.join(OUT, 'run-' + new Date().toISOString().replace(/[:.]/g, '-'));
let child, log = '', checks = 0;
const bots = [];
const sleep = ms => new Promise(r => setTimeout(r, ms));
function check(ok, name, detail) {
  checks++;
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail !== undefined ? ' ' + String(detail).slice(0, 400) : ''));
  if (!ok) throw new Error(name);
}
async function until(fn, ms) { const end = Date.now() + ms; while (Date.now() < end) { if (await fn()) return true; await sleep(100); } return !!(await fn()); }
async function cmd(c, expect = /WPROBE_OK|WPROBE_ERR|WPROBE_INV|WPROBE_GIVE|WPROBE_HELD_REFUSED/, ms = 8000) {
  const from = log.length;
  child.stdin.write(c + '\n');
  await until(() => expect.test(log.slice(from)), ms);
  return log.slice(from);
}
async function inv(name) {
  const out = await cmd('wprobe inv ' + name, /WPROBE_INV[^\n]*\n/);
  const line = (out.match(/WPROBE_INV[^\n]*/) || [''])[0];
  const items = {};
  for (const part of ((line.match(/items=(\S*)/) || [])[1] || '').split(',').filter(Boolean)) {
    const [i, rest] = part.split(':');
    items[+i] = rest;
  }
  const num = k => +((line.match(new RegExp(k + '=(-?\\d+)')) || [])[1]);
  const word = k => (line.match(new RegExp(k + '=(\\S+)')) || [])[1];
  return {line, items, size: num('size'), storage: num('storage'), held: num('held'), window: num('window'), open: num('open'),
    hand: word('hand'), helmet: word('helmet'), offhand: word('offhand')};
}
function watch(bot) {
  const seen = {windowItems: [], setSlot: [], payload: [], held: [], open: []};
  bot._client.on('window_items', p => seen.windowItems.push({id: p.windowId, n: p.items.length}));
  bot._client.on('set_slot', p => seen.setSlot.push({id: p.windowId, slot: p.slot, item: p.item && p.item.blockId}));
  bot._client.on('custom_payload', p => { if (p.channel === 'jaspr:inv') seen.payload.push(p.data.toString('utf8')); });
  bot._client.on('held_item_slot', p => seen.held.push(p.slot));
  bot._client.on('open_window', p => seen.open.push(p));
  return seen;
}
async function join(name) {
  const bot = await new Promise((res, rej) => {
    const b = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: name, version: '1.12.2', auth: 'offline'});
    b.once('error', rej);
    b.seen = watch(b);
    // mineflayer asserts held slots are 0..8; the wide hotbar also uses items 36..40.
    b.once('login', () => {
      const quickBar = b.setQuickBarSlot.bind(b);
      b.setQuickBarSlot = slot => { if (slot >= 0 && slot < 9) quickBar(slot); };
    });
    b.once('spawn', async () => { await sleep(800); res(b); });
  });
  bots.push(bot);
  return bot;
}
function hello(bot) { bot._client.write('custom_payload', {channel: 'jaspr:inv', data: Buffer.concat([Buffer.from([5]), Buffer.from('wide1')])}); }
let action = 1;
function click(bot, slot, mode = 0, button = 0) {
  bot._client.write('window_click', {windowId: 0, slot, mouseButton: button, action: action++, mode, item: {blockId: -1}});
}

(async () => {
  try {
    for (const f of ['jaspr-paper-wide.jar', 'WideProbe.jar']) if (!fs.existsSync(path.join(OUT, f))) throw new Error('missing ' + f + ': run node scripts/build-wide-inventory-server.cjs');
    await new Promise((res, rej) => { const s = net.createServer(); s.once('error', rej); s.listen(PORT, '127.0.0.1', () => s.close(res)); });
    fs.mkdirSync(path.join(RUN, 'plugins'), {recursive: true});
    fs.copyFileSync(path.join(OUT, 'jaspr-paper-wide.jar'), path.join(RUN, 'paper.jar'));
    fs.copyFileSync(path.join(OUT, 'WideProbe.jar'), path.join(RUN, 'plugins', 'WideProbe.jar'));
    fs.writeFileSync(path.join(RUN, 'eula.txt'), 'eula=true\n');
    fs.writeFileSync(path.join(RUN, 'server.properties'), ['server-ip=127.0.0.1', 'server-port=' + PORT, 'online-mode=false', 'level-type=FLAT',
      'level-seed=1', 'generator-settings=3;7,2*3,2;1;', 'spawn-protection=0', 'spawn-animals=false', 'spawn-monsters=false', 'spawn-npcs=false',
      'allow-nether=false', 'view-distance=2', 'max-players=4', 'gamemode=0', 'difficulty=0', 'motd=wide inventory test', ''].join('\n'));
    fs.writeFileSync(path.join(RUN, 'bukkit.yml'), 'settings:\n  allow-end: false\n  connection-throttle: 0\n');
    child = spawn(JAVA, ['-Xms256M', '-Xmx1024M', '-XX:ActiveProcessorCount=2', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', 'nogui', '--nojline'],
      {cwd: RUN, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
    try { execFileSync('powershell', ['-NoProfile', '-Command', '(Get-Process -Id ' + child.pid + ').PriorityClass=\'BelowNormal\''], {windowsHide: true}); } catch (e) { }
    child.stdout.on('data', b => log += b); child.stderr.on('data', b => log += b);
    check(await until(() => log.includes('Done ('), 240000), 'Paper starts on the patched jar');
    check(!/VerifyError|NoSuchFieldError|NoSuchMethodError|ClassFormatError/.test(log), 'patched classes load', (log.match(/\S*Error[^\n]*/) || [''])[0]);
    await cmd('gamerule doDaylightCycle false', /Game rule|gamerule/i);

    // ---- a client that never says hello keeps the vanilla 36 ----------------------------------------------------
    const plain = await join('Plain');
    check(plain.seen.windowItems.some(w => w.id === 0 && w.n === 46), 'plain client: inventory window has 46 slots', JSON.stringify(plain.seen.windowItems));
    let p = await inv('Plain');
    check(p.size === 61 && p.storage === 56 && p.window === 46, 'plain client: 56 storage slots server-side, 46-slot window', p.line);
    for (let i = 0; i < 36; i++) await cmd('wprobe set Plain ' + i + ' STONE 64');
    await cmd('give Plain minecraft:dirt 5', /Given|Gave/i);
    await sleep(500);
    p = await inv('Plain');
    check(!/DIRT/.test(p.line) && Object.keys(p.items).every(i => i < 36), 'plain client: pickups never go to slots it cannot see', p.line);
    const plainSlots = plain.seen.setSlot.filter(s => s.id === 0 && s.slot >= 46);
    await cmd('wprobe set Plain 45 DIAMOND 1');
    await sleep(300);
    check(plain.seen.setSlot.filter(s => s.id === 0 && s.slot >= 46).length === plainSlots.length, 'plain client: no set-slot beyond window slot 45');
    const refused = await cmd('wprobe held Plain 38');
    check(/WPROBE_HELD_REFUSED/.test(refused), 'plain client: Bukkit refuses an extension held slot');

    // ---- the JasperCraft client says hello ----------------------------------------------------------------------
    const wide = await join('Wide');
    check(wide.seen.windowItems.some(w => w.id === 0 && w.n === 46), 'wide client: starts vanilla before hello');
    hello(wide);
    check(await until(() => wide.seen.payload.length > 0, 5000), 'wide client: server answers on jaspr:inv', JSON.stringify(wide.seen.payload));
    check(wide.seen.payload[0] === 'wide1', 'answer is wide1', wide.seen.payload[0]);
    check(await until(() => wide.seen.windowItems.some(w => w.id === 0 && w.n === 66), 5000), 'wide client: inventory window resent with 66 slots');
    check(/JASPR_WIDE_ENABLED player=Wide again=false window=66/.test(log), 'log: JASPR_WIDE_ENABLED');
    await cmd('wprobe clear Wide');
    const names = ['stone', 'dirt', 'cobblestone', 'planks', 'sand', 'gravel', 'log', 'glass', 'wool', 'brick_block', 'bookshelf', 'obsidian', 'ice', 'clay', 'pumpkin'];
    for (const n of names) await cmd('give Wide minecraft:' + n + ' 1', /Given|Gave/i);
    await sleep(400);
    let w = await inv('Wide');
    check(w.items[0] === 'STONEx1' && w.items[8] === 'WOOLx1' && w.items[36] === 'BRICKx1' && w.items[40] === 'CLAYx1' && w.items[9] === 'PUMPKINx1',
      'wide fill order: hotbar 1-9, hotbar 10-14 (items 36-40), then row 1', w.line);
    wide._client.write('held_item_slot', {slotId: 38});
    await sleep(400);
    w = await inv('Wide');
    check(w.held === 38 && w.hand === 'OBSIDIAN', 'held slot 12 (item 38) accepted, main hand is that item', w.line);
    check(/WPROBE_HELD from=0 to=38/.test(log), 'PlayerItemHeldEvent fired for the extension slot');
    const heldBefore = wide.seen.held.length;
    await cmd('wprobe held Wide 39');
    check(await until(() => wide.seen.held.length > heldBefore && wide.seen.held[wide.seen.held.length - 1] === 39, 3000), 'Bukkit setHeldItemSlot(39) reaches the client');
    for (const [index, mat, slot] of [[45, 'DIAMOND', 55], [41, 'EMERALD', 51], [3, 'GOLD_INGOT', 39], [20, 'COAL', 20], [56, 'IRON_BOOTS', 8]]) {
      const n = wide.seen.setSlot.length;
      await cmd('wprobe set Wide ' + index + ' ' + mat + ' 1');
      check(await until(() => wide.seen.setSlot.slice(n).some(s => s.id === 0 && s.slot === slot), 3000), 'Bukkit setItem(' + index + ') -> window slot ' + slot);
    }
    await cmd('wprobe helmet Wide IRON_HELMET');
    await cmd('wprobe offhand Wide SHIELD 1');
    w = await inv('Wide');
    check(w.helmet === 'IRON_HELMET' && w.offhand === 'SHIELD' && w.items[59] === 'IRON_HELMETx1' && w.items[60] === 'SHIELDx1' && w.items[56] === 'IRON_BOOTSx1',
      'armour at flat 56-59, off hand at 60', w.line);
    // Click events: Bukkit slot numbers and types for extension, armour and off-hand window slots.
    for (const [raw, slot, type] of [[50, 40, 'QUICKBAR'], [51, 41, 'CONTAINER'], [65, 55, 'CONTAINER'], [5, 59, 'ARMOR'], [45, 60, 'QUICKBAR'], [44, 8, 'QUICKBAR'], [9, 9, 'CONTAINER']]) {
      const from = log.length;
      click(wide, raw); await sleep(150); click(wide, raw);
      check(await until(() => new RegExp('WPROBE_CLICK raw=' + raw + ' slot=' + slot + ' type=' + type).test(log.slice(from)), 3000),
        'click raw ' + raw + ' -> slot ' + slot + ' ' + type, (log.slice(from).match(/WPROBE_CLICK[^\n]*/) || [''])[0]);
    }
    await sleep(300);
    // Shift-click: row 1 -> first free hotbar position (vanilla hotbar first, then 10-14); extension -> rows.
    await cmd('wprobe clear Wide');
    await cmd('wprobe set Wide 9 STONE 64');
    click(wide, 9, 1); await sleep(400);
    w = await inv('Wide');
    check(w.items[0] === 'STONEx64' && !w.items[9], 'shift-click row 1 -> hotbar 1', w.line);
    for (let i = 0; i < 9; i++) await cmd('wprobe set Wide ' + i + ' DIRT 64');
    await cmd('wprobe set Wide 9 STONE 64');
    click(wide, 9, 1); await sleep(400);
    w = await inv('Wide');
    check(w.items[36] === 'STONEx64' && !w.items[9], 'shift-click row 1 with a full vanilla hotbar -> hotbar 10 (item 36)', w.line);
    click(wide, 46, 1); await sleep(400);
    w = await inv('Wide');
    check(w.items[9] === 'STONEx64' && !w.items[36], 'shift-click hotbar 10 -> row 1', w.line);
    await cmd('wprobe set Wide 55 COBBLESTONE 10');
    await cmd('wprobe set Wide 10 COBBLESTONE 60');
    click(wide, 65, 1); await sleep(400);
    w = await inv('Wide');
    check(w.items[36] === 'COBBLESTONEx10' || (w.items[10] === 'COBBLESTONEx64' && w.items[36] === 'COBBLESTONEx6'), 'shift-click a row extension -> hotbar', w.line);
    // Creative: window slots 46..65 are settable by a wide client.
    await cmd('gamemode 1 Wide', /game mode|gamemode/i);
    await sleep(300);
    wide._client.write('set_creative_slot', {slot: 52, item: {blockId: 1, itemCount: 5, itemDamage: 0}});
    await sleep(400);
    w = await inv('Wide');
    check(w.items[42] === 'STONEx5', 'Creative set-slot 52 -> item 42', w.line);
    check(/WPROBE_CREATIVE raw=52 slot=42/.test(log), 'InventoryCreativeEvent names item 42');
    // A chest window carries the 20 extension slots after the player's 36.
    const at = wide.entity.position.floored().offset(2, 0, 0);
    await cmd('setblock ' + at.x + ' ' + at.y + ' ' + at.z + ' minecraft:chest', /placed|Block placed/i);
    await sleep(600);
    const opened = wide.seen.windowItems.length;
    await wide.activateBlock(wide.blockAt(new Vec3(at.x, at.y, at.z)));
    check(await until(() => wide.seen.windowItems.slice(opened).some(x => x.id !== 0 && x.n === 83), 5000), 'chest window: 27 + 36 + 20 = 83 slots',
      JSON.stringify(wide.seen.windowItems.slice(opened)));
    w = await inv('Wide');
    check(w.open === 83, 'server chest container has 83 slots', w.line);
    wide.closeWindow(wide.currentWindow || {id: 1});
    await sleep(300);
    // Saved extension slots survive a rejoin; a rejoin without hello is vanilla on the wire.
    await cmd('wprobe set Wide 50 EMERALD 7');
    await cmd('wprobe save Wide');
    wide.quit(); await sleep(1500);
    const again = await join('Wide');
    check(again.seen.windowItems.some(x => x.id === 0 && x.n === 46), 'rejoin without hello: 46-slot window');
    w = await inv('Wide');
    check(w.items[50] === 'EMERALDx7' && w.items[42] === 'STONEx5' && w.window === 46, 'rejoin: extension items kept server-side', w.line);
    hello(again);
    check(await until(() => again.seen.windowItems.some(x => x.id === 0 && x.n === 66), 5000), 'rejoin + hello: 66-slot window with the kept items');
    check(!/Exception|Could not pass event|tried to set an invalid carried item/.test(log), 'no server errors', (log.match(/[^\n]*(Exception|Could not pass)[^\n]*/) || [''])[0]);
    console.log('WIDE_BOT_PASS checks=' + checks);
  } catch (e) {
    console.log('WIDE_BOT_FAIL ' + e.message);
    const tail = log.split('\n').filter(l => /WARN|ERROR|Exception|JASPR_WIDE/.test(l)).slice(-15).join('\n');
    if (tail) console.log(tail);
    process.exitCode = 1;
  } finally {
    for (const b of bots) try { b.quit(); } catch (e) { }
    if (child) {
      child.stdin.write('stop\n');
      await until(() => child.exitCode !== null, 30000);
      if (child.exitCode === null) child.kill();
    }
  }
})();
