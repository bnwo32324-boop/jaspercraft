'use strict';
/* Backpacks end to end with a scripted 1.12.2 client (mineflayer) on a disposable loopback Paper test
 * server running candidate/gear/JasprGear.jar (spawn-monsters irrelevant; survival mode):
 *   main:    craft a Leather Satchel from leather and string at a crafting table; right-click it in hand
 *            (18 slots); shift-click cobblestone in; a backpack is refused inside a backpack (shift-click
 *            and cursor place); right-click in the inventory screen reopens it with the cobblestone; the
 *            Hauler's Frame Pack opens with 54 slots; vanilla loot-table chests get Survivor Gear rolls.
 *   restart: after a server restart a satchel with the same contents uuid still holds the cobblestone (the test
 *            template does not save vanilla player data, so the item itself is given again).
 *   GEAR_CMDS=<file the server console reads>  GEAR_PORT=25597  GEAR_PHASE=main|restart  NODE_PATH=<mineflayer>
 *   node tests/gear-backpack-bot.cjs
 */
const fs = require('node:fs');
const mineflayer = require('mineflayer');
const {Vec3} = require('vec3');
const PORT = Number(process.env.GEAR_PORT || 25597), CMDS = process.env.GEAR_CMDS, PHASE = process.env.GEAR_PHASE || 'main';
if (!CMDS) { console.error('GEAR_CMDS is required'); process.exit(2); }
let failures = 0, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = line => fs.appendFileSync(CMDS, line + '\n');
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }
const X = 420, Y = 200, Z = 420;
const nbt = item => item && item.nbt ? JSON.stringify(item.nbt) : '';
const UUID_FILE = require('node:path').join(require('node:path').dirname(CMDS), 'gear-backpack-uuid.txt');
const packUuid = item => { const m = item && /JasprGearPack.*?"uuid":\{"type":"string","value":"([0-9a-f-]{36})"/.exec(nbt(item)); return m ? m[1] : null; };
const packId = item => { const m = item && item.name === 'stone_hoe' && /JasprGearPack.*?"id":\{"type":"string","value":"(\w+)"/.exec(nbt(item)); return m ? m[1] : null; };
const isGearLoot = item => item && item.name === 'stone_hoe' && /JasprGear(Pack|Use)?"/.test(nbt(item));
const slotsOf = (window, from, to) => window.slots.slice(from, to).map((item, i) => ({item, slot: from + i})).filter(x => x.item);

// A use aimed at a block within reach is a block click, never "air": face open air first.
async function aimAtAir(bot) {
  for (const pitch of [0, Math.PI / 2, Math.PI / 4, -Math.PI / 4])
    for (let k = 0; k < 8; k++) {
      await bot.look(bot.entity.yaw + k * Math.PI / 4, pitch, true);
      if (!bot.blockAtCursor(6)) return true;
    }
  return false;
}
async function click(bot, slot, button, mode) {
  try { await bot.clickWindow(slot, button, mode); return true; } catch (e) { return false; } // false = server refused
}
async function nextWindow(bot, action, ms = 4000) {
  const opened = new Promise(resolve => { const t = setTimeout(() => resolve(null), ms); bot.once('windowOpen', w => { clearTimeout(t); resolve(w); }); });
  await action();
  return opened;
}
function join() {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: 'Packer', version: '1.12.2', auth: 'offline'});
    bot.once('spawn', async () => { await sleep(1200); resolve(bot); });
    bot.once('kicked', r => reject(new Error('kicked ' + r)));
    bot.once('error', reject);
  });
}
/** Right-click the backpack with this id in the plain inventory screen (window 0). */
async function openFromInventory(bot, id) {
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(500); }
  const hit = slotsOf(bot.inventory, 9, 45).find(x => packId(x.item) === id);
  if (!hit) return null;
  return nextWindow(bot, () => click(bot, hit.slot, 1, 0));
}

async function main() {
  const bot = await join();
  // Build the sky platform in Creative (no fall damage while the chunk loads), then play in survival.
  console_('op Packer'); console_('gamemode creative Packer'); console_('gear effect Packer clear');
  console_(`minecraft:tp Packer ${X} ${Y + 1} ${Z}`);
  await sleep(2500);
  console_(`fill ${X - 4} ${Y} ${Z - 4} ${X + 4} ${Y} ${Z + 4} stone`);
  console_(`minecraft:tp Packer ${X} ${Y + 1} ${Z}`);
  await sleep(1000);
  console_('gamemode survival Packer'); console_('clear Packer');
  console_(`setblock ${X + 2} ${Y + 1} ${Z} crafting_table`);
  console_('give Packer leather 6'); console_('give Packer string 2'); console_('give Packer cobblestone 64');
  console_('gear give Packer frame_pack');
  await sleep(2000);

  // 1. Craft a satchel: SLS / L.L / LLL.
  const table = bot.blockAt(new Vec3(X + 2, Y + 1, Z));
  let w = await nextWindow(bot, () => bot.activateBlock(table));
  check(w && w.inventoryStart === 10, 'crafting table opened');
  if (w) {
    const leather = slotsOf(w, 10, 46).find(x => x.item.name === 'leather'), string = slotsOf(w, 10, 46).find(x => x.item.name === 'string');
    await click(bot, leather.slot, 0, 0);
    for (const g of [2, 4, 6, 7, 8, 9]) await click(bot, g, 1, 0);
    await click(bot, string.slot, 0, 0);
    for (const g of [1, 3]) await click(bot, g, 1, 0);
    await sleep(800);
    check(packId(w.slots[0]) === 'satchel', 'leather + string make a Leather Satchel', w.slots[0] && w.slots[0].name);
    await click(bot, 0, 0, 0);
    const free = w.slots.findIndex((it, i) => i >= 10 && !it);
    await click(bot, free, 0, 0);
    bot.closeWindow(w);
    await sleep(800);
  }
  const satchel = () => bot.inventory.items().find(i => packId(i) === 'satchel');
  check(!!satchel(), 'the crafted satchel is in the inventory');

  // 2. Right-click in hand.
  await bot.equip(satchel(), 'hand');
  await aimAtAir(bot);
  await sleep(400);
  w = await nextWindow(bot, () => bot.activateItem());
  check(w && w.inventoryStart === 18, 'right-click in hand opens 18 slots (half the inventory)', w && w.inventoryStart);
  if (w) {
    // 3. Shift-click cobblestone in.
    const cobble = slotsOf(w, 18, w.slots.length).find(x => x.item.name === 'cobblestone');
    await click(bot, cobble.slot, 0, 1);
    await sleep(600);
    // 4. Backpacks never go inside backpacks.
    const frame = slotsOf(w, 18, w.slots.length).find(x => packId(x.item) === 'frame_pack');
    // The server refuses both (checked after reopening: the satchel holds no backpack).
    await click(bot, frame.slot, 0, 1);
    await sleep(400);
    if (await click(bot, frame.slot, 0, 0)) await click(bot, 5, 0, 0);
    await sleep(400);
    if (w.selectedItem) await click(bot, frame.slot, 0, 0); // put it back if the cursor still holds it
    bot.closeWindow(w);
    // The server keeps a refused backpack on the cursor; mineflayer can lose track of it, and closing the
    // window then drops it a couple of blocks away. Pull it back (minecraft:tp: JasprApocalypse replaces tp).
    await sleep(1000);
    console_('execute Packer ~ ~ ~ minecraft:tp @e[type=item,r=8] Packer');
    await sleep(2500);
  }
  // 5. Right-click in the inventory screen reopens it; contents persisted, no backpack inside.
  w = await openFromInventory(bot, 'satchel');
  check(w && w.inventoryStart === 18, 'right-click in the inventory opens the satchel');
  if (w) {
    const top = slotsOf(w, 0, 18);
    check(top.some(x => x.item.name === 'cobblestone' && x.item.count === 64), 'cobblestone stored in the satchel', top.map(x => x.item.name));
    check(!top.some(x => x.item.name === 'stone_hoe'), 'a backpack is refused inside a backpack (shift-click and cursor)');
    check(!slotsOf(w, 18, w.slots.length).some(x => x.item.name === 'cobblestone'), 'cobblestone left the player inventory');
    check(slotsOf(w, 18, w.slots.length).some(x => packId(x.item) === 'frame_pack'), 'frame pack still in the player inventory');
  }
  const uuid = packUuid(satchel());
  check(!!uuid, 'the opened satchel carries its contents uuid', uuid);
  if (uuid) fs.writeFileSync(UUID_FILE, uuid);
  // 6. The top tier: 54 slots.
  w = await openFromInventory(bot, 'frame_pack');
  check(w && w.inventoryStart === 54, "Hauler's Frame Pack opens with 54 slots", w && w.inventoryStart);
  if (w) { bot.closeWindow(w); await sleep(500); }

  // 7. Vanilla loot-table chests roll Survivor Gear (trinket, supply or backpack) like structure chests.
  let filled = 0, gear = 0;
  const N = 60, at = new Vec3(X - 2, Y + 1, Z);
  for (let i = 0; i < N; i++) {
    console_(`setblock ${at.x} ${at.y} ${at.z} air`);
    console_(`setblock ${at.x} ${at.y} ${at.z} chest 0 replace {LootTable:"minecraft:chests/simple_dungeon",LootTableSeed:${1000 + i}L}`);
    await sleep(350);
    const chest = await nextWindow(bot, () => bot.activateBlock(bot.blockAt(at)), 3000);
    if (!chest) continue;
    await sleep(250);
    const items = slotsOf(chest, 0, chest.inventoryStart);
    if (items.length) filled++;
    if (items.some(x => isGearLoot(x.item))) gear++;
    bot.closeWindow(chest);
    await sleep(150);
  }
  check(filled === N, 'every vanilla loot chest filled', filled);
  check(gear > 0, 'vanilla loot chests got Survivor Gear rolls (expect ~7 of 60)', gear);
  console_('gear status');
  await sleep(1000);
  bot.quit();
}

async function restart() {
  const bot = await join();
  const uuid = fs.readFileSync(UUID_FILE, 'utf8').trim();
  console_('gamemode survival Packer'); console_('clear Packer');
  console_(`give Packer stone_hoe 1 30 {Unbreakable:1b,JasprGearPack:{id:"satchel",uuid:"${uuid}"}}`);
  await sleep(1500);
  const w = await openFromInventory(bot, 'satchel');
  const top = w ? slotsOf(w, 0, 18) : [];
  check(top.some(x => x.item.name === 'cobblestone' && x.item.count === 64), 'satchel contents survived a server restart', top.map(x => x.item.name));
  bot.quit();
}

(PHASE === 'restart' ? restart() : main()).then(() => {
  console.log((failures ? 'GEAR_PACK FAIL' : 'GEAR_PACK PASS') + ' checks=' + checks + ' failures=' + failures);
  setTimeout(() => process.exit(failures ? 1 : 0), 300);
}, e => { console.log('GEAR_PACK ERROR ' + (e && e.stack || e)); process.exit(1); });
