'use strict';
/* Survivor Gear Phase 2 end-to-end check with a scripted 1.12.2 client (mineflayer) against a
 * disposable loopback Paper test server that runs candidate/gear/JasprGear.jar.
 *
 *   GEAR_CMDS=<file the server console reads>  (e.g. `tail -f cmds.txt | java -jar paper.jar`)
 *   GEAR_PORT=25597  GEAR_PHASE=main|restart   NODE_PATH=<dir with mineflayer installed>
 *   node tests/gear-phase2-bot.cjs
 *
 * main: the hello-2 handshake and HUD packets, ability costs, Paralysis (movement + ability lock),
 * Bleeding and the Field Bandage, candy, stim (Invigorated speed modifier), Full Restore, the
 * Adrenaline Crystal, lightning vs Lightning Resistance, and that a hello-1 (Phase 1) client
 * never receives a HUD packet. restart: after a server restart the crystal and adrenaline persist.
 * Never touches a real server: loopback only.
 */
const fs = require('node:fs');
const mineflayer = require('mineflayer');
const PORT = Number(process.env.GEAR_PORT || 25597), CMDS = process.env.GEAR_CMDS, PHASE = process.env.GEAR_PHASE || 'main';
if (!CMDS) { console.error('GEAR_CMDS is required'); process.exit(2); }
const VIGOR = 'bf1f3cf7'; // first block of UUID.nameUUIDFromBytes("jaspr-gear:status:vigor:GENERIC_MOVEMENT_SPEED")
let failures = 0, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = line => fs.appendFileSync(CMDS, line + '\n');
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }
function payload(text) { const body = Buffer.from(text, 'utf8'), len = []; let n = body.length; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; len.push(b); } while (n); return Buffer.concat([Buffer.from(len), body]); }
function unpayload(buf) { let n = 0, shift = 0, i = 0; for (;;) { const b = buf[i++]; n |= (b & 0x7f) << shift; if (!(b & 0x80)) break; shift += 7; } return buf.slice(i, i + n).toString('utf8'); }

function join(name, hello) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: name, version: '1.12.2', auth: 'offline'});
    bot.hud = null; bot.huds = []; bot.states = 0;
    bot._client.on('custom_payload', packet => {
      if (packet.channel !== 'jaspr:gear') return;
      const msg = JSON.parse(unpayload(packet.data));
      if (msg.t === 'hud') { bot.hud = msg; bot.huds.push(msg); } else if (Array.isArray(msg.slots)) bot.states++;
    });
    bot.say = text => bot._client.write('custom_payload', {channel: 'jaspr:gear', data: payload(text)});
    bot.once('spawn', async () => { await sleep(800); if (hello) bot.say(hello); await sleep(600); resolve(bot); });
    bot.once('kicked', reason => reject(new Error('kicked ' + reason)));
    bot.once('error', reject);
  });
}
const fx = bot => (bot.hud && bot.hud.fx || []).map(f => f[0]);
async function until(fn, ms = 4000) { const end = Date.now() + ms; while (Date.now() < end) { if (fn()) return true; await sleep(100); } return fn(); }
// A use aimed at a block within reach is a block click, never "air": face open air first.
async function aimAtAir(bot) {
  for (const pitch of [0, Math.PI / 2, Math.PI / 4, -Math.PI / 4])
    for (let k = 0; k < 8; k++) {
      await bot.look(bot.entity.yaw + k * Math.PI / 4, pitch, true);
      if (!bot.blockAtCursor(6)) return true;
    }
  return false;
}
async function holdAndUse(bot, predicate) {
  await sleep(300);
  const item = bot.inventory.items().find(predicate);
  if (!item) return false;
  await bot.equip(item, 'hand');
  await sleep(300);
  const yaw = bot.entity.yaw, pitch = bot.entity.pitch;
  await aimAtAir(bot);
  bot.activateItem();
  await sleep(700);
  await bot.look(yaw, pitch, true);
  return true;
}
const byModel = model => i => i.name === 'stone_hoe' && i.metadata === model;

async function main() {
  const bot = await join('GearBot', 'hello 2');
  // A closed stone room in the sky, built in Creative: near some spawn spots the H dodge dash carried the bot
  // over a drop, and the fall killed it (items and all).
  console_('op GearBot'); console_('gamemode creative GearBot'); console_('minecraft:tp GearBot 460 201 460');
  await sleep(2500);
  console_('fill 450 200 450 470 205 470 stone 0 hollow'); console_('minecraft:tp GearBot 460 201 460');
  await sleep(1000);
  console_('gamemode survival GearBot'); console_('gear effect GearBot clear');
  console_('difficulty 1'); // peaceful would heal 1/s and hide bleeding
  await until(() => bot.hud);
  check(bot.states >= 1, 'hello 2: slot state received');
  check(bot.hud && bot.hud.t === 'hud' && bot.hud.m === 100 && bot.hud.on === true, 'hello 2: HUD packet (max 100)', bot.hud);
  const start = bot.hud.a;
  console_('gear give GearBot capacitor_belt'); console_('gear give GearBot supplies');
  await sleep(1200);
  check(await holdAndUse(bot, byModel(1)), 'belt in hand, right-click equips it');
  await until(() => bot.inventory.items().filter(byModel(1)).length === 0, 3000);
  check(bot.inventory.items().filter(byModel(1)).length === 0, 'belt left the inventory (worn)');

  // Ability cost: Dodge 15.
  const before = bot.hud.a;
  bot.say('key dodge');
  check(await until(() => bot.hud.a <= before - 14), 'dodge spends 15 adrenaline', {before, after: bot.hud.a});
  console.log('     adrenaline start=' + start + ' before=' + before + ' after=' + bot.hud.a);

  // Paralysis: HUD, movement lock and ability lock.
  await sleep(4200); // dodge cooldown
  console_('gear effect GearBot para 3');
  check(await until(() => fx(bot).includes('para')), 'paralysis shows on the HUD', bot.hud);
  const p0 = bot.entity.position.clone(), a0 = bot.hud.a;
  bot.setControlState('forward', true); bot.setControlState('jump', true);
  await sleep(1200);
  bot.setControlState('forward', false); bot.setControlState('jump', false);
  await sleep(300);
  const moved = Math.hypot(bot.entity.position.x - p0.x, bot.entity.position.z - p0.z);
  check(moved < 0.6, 'paralysis locks movement', {moved});
  bot.say('key dodge');
  await sleep(600);
  check(bot.hud.a >= a0, 'paralysis locks abilities (no adrenaline spent)', {a0, now: bot.hud.a});
  check(await until(() => !fx(bot).includes('para'), 4000), 'paralysis wears off');

  // Bleeding + Field Bandage.
  console_('gear effect GearBot bleed 20');
  check(await until(() => fx(bot).includes('bleed')), 'bleeding shows on the HUD');
  const h0 = bot.health;
  await sleep(2600);
  check(bot.health < h0, 'bleeding deals damage over time', {h0, h: bot.health});
  check(await holdAndUse(bot, byModel(17)), 'field bandage used');
  check(await until(() => !fx(bot).includes('bleed')), 'bandage stops the bleeding', bot.hud);
  const bandage = bot.inventory.items().find(byModel(17));
  check(bandage && bandage.nbt && JSON.stringify(bandage.nbt).includes('Doses: 1/2'), 'bandage lost one dose (1/2 left)');

  // Candy (+20): spend first so the bar is not full.
  bot.say('key dodge');
  await until(() => bot.hud.a < 90, 3000);
  const c0 = bot.hud.a;
  check(await holdAndUse(bot, byModel(16)), 'adrenaline candy used');
  check(await until(() => bot.hud.a >= Math.min(100, c0 + 18)), 'candy restores 20', {c0, now: bot.hud.a});
  const candy = bot.inventory.items().find(byModel(16));
  check(candy && JSON.stringify(candy.nbt).includes('Doses: 2/3'), 'candy lost one dose (2/3 left)');

  // Stim: Invigorated + speed modifier.
  check(await holdAndUse(bot, byModel(18)), 'stim reagent used');
  check(await until(() => fx(bot).includes('vigor')), 'stim gives Invigorated', bot.hud);
  await sleep(1300);
  const speed = bot.entity.attributes && (bot.entity.attributes['generic.movementSpeed'] || bot.entity.attributes['minecraft:generic.movement_speed']);
  check(speed && speed.modifiers.some(m => String(m.uuid).toLowerCase().startsWith(VIGOR)), 'Invigorated adds the speed modifier', speed);
  check(bot.inventory.items().filter(byModel(18)).length === 0, 'single-use stim is gone');

  // Crystal: max 110.
  check(await holdAndUse(bot, byModel(20)), 'adrenaline crystal used');
  check(await until(() => bot.hud.m === 110), 'crystal raises max to 110', bot.hud);

  // Full Restore: full bar, ice + volt; lightning is then harmless-ish and does not paralyse.
  check(await holdAndUse(bot, byModel(19)), 'full restore used');
  check(await until(() => fx(bot).includes('ice') && fx(bot).includes('volt') && bot.hud.a >= 109), 'full restore: full adrenaline, ice + lightning resistance', bot.hud);
  console_('execute GearBot ~ ~ ~ summon lightning_bolt ~ ~ ~');
  await sleep(1500);
  check(!fx(bot).includes('para'), 'lightning resistance: no paralysis from a strike', bot.hud);
  console_('gear effect GearBot clear');
  await sleep(4000); // past any paralysis immunity window
  console_('execute GearBot ~ ~ ~ summon lightning_bolt ~ ~ ~');
  check(await until(() => fx(bot).includes('para'), 3000), 'unprotected lightning strike paralyses', bot.hud);

  // Phase 1 client: never a HUD packet.
  const old = await join('OldClient', 'hello 1');
  await sleep(2500);
  check(old.states >= 1 && old.huds.length === 0, 'hello 1 client gets slots but no HUD', {states: old.states, huds: old.huds.length});
  old.quit();
  console_('gear status');
  await sleep(1500);
  fs.writeFileSync(CMDS + '.expect', JSON.stringify({a: bot.hud.a, m: bot.hud.m}));
  bot.quit();
}

async function restart() {
  const expect = JSON.parse(fs.readFileSync(CMDS + '.expect', 'utf8'));
  const bot = await join('GearBot', 'hello 2');
  await until(() => bot.hud);
  check(bot.hud && bot.hud.m === 110, 'crystal survived a server restart', bot.hud);
  check(bot.hud && bot.hud.a >= Math.min(expect.a, 100) - 5, 'adrenaline survived a server restart', {expect, hud: bot.hud});
  bot.quit();
}

(PHASE === 'restart' ? restart() : main()).then(() => {
  console.log((failures ? 'GEAR_BOT FAIL' : 'GEAR_BOT PASS') + ' checks=' + checks + ' failures=' + failures);
  setTimeout(() => process.exit(failures ? 1 : 0), 300);
}, error => { console.log('GEAR_BOT ERROR ' + (error && error.stack || error)); process.exit(1); });
