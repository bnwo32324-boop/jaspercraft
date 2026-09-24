'use strict';
/* Boss trinket drops seen by a scripted 1.12.2 client (mineflayer) on a disposable loopback Paper
 * test server running candidate/gear/JasprGear.jar:
 *   a vanilla Wither and a "jaspr_boss"-tagged mob each drop one Survivor Gear trinket when killed;
 *   an ordinary zombie killed the same way drops none. The server needs spawn-monsters=true (/summon).
 *   GEAR_CMDS=<file the server console reads>  GEAR_PORT=25597  NODE_PATH=<mineflayer>  node tests/gear-boss-bot.cjs
 */
const fs = require('node:fs');
const mineflayer = require('mineflayer');
const PORT = Number(process.env.GEAR_PORT || 25597), CMDS = process.env.GEAR_CMDS;
if (!CMDS) { console.error('GEAR_CMDS is required'); process.exit(2); }
let failures = 0, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = line => fs.appendFileSync(CMDS, line + '\n');
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }

(async () => {
  const bot = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: 'BossHunter', version: '1.12.2', auth: 'offline'});
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('kicked', r => reject(new Error('kicked ' + r))); bot.once('error', reject); });
  await sleep(1000);
  console_('op BossHunter'); console_('gamemode creative BossHunter'); console_('difficulty 1');
  await sleep(800);
  // A stone floor in the sky away from spawn (spawn areas refuse mobs), so drops land where we can see
  // them. minecraft:tp because JasprApocalypse replaces /tp with teleport requests.
  const X = 400, Y = 200, Z = 400;
  console_(`minecraft:tp BossHunter ${X} ${Y + 1} ${Z}`);
  await sleep(2500);
  console_(`fill ${X - 4} ${Y} ${Z - 4} ${X + 4} ${Y} ${Z + 4} stone`);
  await sleep(1500);
  // Entity ids of dropped Survivor Gear items the bot can see.
  const trinkets = () => new Set(Object.values(bot.entities).filter(e => {
    if (e.name !== 'item') return false;
    const drop = typeof e.getDroppedItem === 'function' ? e.getDroppedItem() : null;
    return drop && drop.name === 'stone_hoe' && drop.nbt && JSON.stringify(drop.nbt).includes('JasprGear');
  }).map(e => e.id));
  // New trinkets after the kill (earlier ones may vanish; only new ids count). Polls, since the item's
  // metadata can arrive a moment after the item itself.
  const run = async (summon, kill, want) => {
    const before = trinkets(), fresh = () => [...trinkets()].filter(id => !before.has(id)).length;
    console_('summon ' + summon.replace('~2 ~ ~', `${X + 2} ${Y + 1} ${Z}`));
    await sleep(700);
    console_(kill);
    const end = Date.now() + (want ? 6000 : 2500);
    while (Date.now() < end && fresh() < Math.max(want, 1)) await sleep(100);
    return fresh();
  };
  let n;
  check((n = await run('zombie ~2 ~ ~ {NoAI:1b}', 'kill @e[type=zombie]', 0)) === 0, 'ordinary zombie: no trinket', n);
  check((n = await run('husk ~2 ~ ~ {NoAI:1b,Tags:["jaspr_boss"]}', 'kill @e[type=husk]', 1)) === 1, 'jaspr_boss-tagged mob: one trinket', n);
  check((n = await run('wither ~2 ~ ~', 'kill @e[type=wither]', 1)) === 1, 'vanilla Wither: one trinket', n);
  console_('gear status');
  await sleep(1000);
  bot.quit();
  console.log((failures ? 'GEAR_BOSS FAIL' : 'GEAR_BOSS PASS') + ' checks=' + checks + ' failures=' + failures);
  setTimeout(() => process.exit(failures ? 1 : 0), 300);
})().catch(e => { console.log('GEAR_BOSS ERROR ' + (e && e.stack || e)); process.exit(1); });
