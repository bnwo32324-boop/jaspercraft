'use strict';
/* Survivor Gear Phase 3 end-to-end check: two scripted 1.12.2 clients (mineflayer) against a
 * disposable loopback Paper test server running candidate/gear/JasprGear.jar.
 *
 *   GEAR_CMDS=<file the server console reads>  GEAR_PORT=25597  GEAR_PHASE=main|restart
 *   NODE_PATH=<dir with mineflayer>  node tests/gear-phase3-bot.cjs
 *
 * main: mutagens (Brute health modifier, one mutation at a time, purge), Sprite flight grant,
 * the R ability spending adrenaline, admin mutate; worn-gear packets seen by another hello-3
 * client (and hidden with /gear show off); the Creative column messages (ctake / cput, slot
 * typing, survival refused). restart: mutation and show-setting persist.
 */
const fs = require('node:fs'), path = require('node:path');
const mineflayer = require('mineflayer');
const PORT = Number(process.env.GEAR_PORT || 25597), CMDS = process.env.GEAR_CMDS, PHASE = process.env.GEAR_PHASE || 'main';
if (!CMDS) { console.error('GEAR_CMDS is required'); process.exit(2); }
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'candidate', 'gear', 'gear-catalog.json'), 'utf8'));
const SNBT = id => catalog.items.find(i => i.id === id).snbt;
let failures = 0, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = line => fs.appendFileSync(CMDS, line + '\n');
function check(ok, what, extra) { checks++; if (!ok) { failures++; console.log('FAIL ' + what + (extra !== undefined ? ' ' + JSON.stringify(extra) : '')); } else console.log('ok   ' + what); }
function payload(text) { const body = Buffer.from(text, 'utf8'), len = []; let n = body.length; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; len.push(b); } while (n); return Buffer.concat([Buffer.from(len), body]); }
function unpayload(buf) { let n = 0, shift = 0, i = 0; for (;;) { const b = buf[i++]; n |= (b & 0x7f) << shift; if (!(b & 0x80)) break; shift += 7; } return buf.slice(i, i + n).toString('utf8'); }
async function until(fn, ms = 4000) { const end = Date.now() + ms; while (Date.now() < end) { if (fn()) return true; await sleep(100); } return fn(); }

function join(name, hello) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: name, version: '1.12.2', auth: 'offline'});
    bot.hud = null; bot.worn = null; bot.state = null; bot.cursor = undefined; bot.flags = 0;
    bot._client.on('custom_payload', packet => {
      if (packet.channel !== 'jaspr:gear') return;
      const msg = JSON.parse(unpayload(packet.data));
      if (msg.t === 'hud') bot.hud = msg; else if (msg.t === 'worn') bot.worn = msg; else if (Array.isArray(msg.slots)) bot.state = msg;
    });
    bot._client.on('set_slot', p => { if (p.windowId === -1) bot.cursor = p.item; });
    bot._client.on('abilities', p => { bot.flags = p.flags; });
    bot.say = text => bot._client.write('custom_payload', {channel: 'jaspr:gear', data: payload(text)});
    bot.once('spawn', async () => { await sleep(800); bot.say(hello); await sleep(700); resolve(bot); });
    bot.once('kicked', reason => reject(new Error('kicked ' + reason)));
    bot.once('error', reject);
  });
}
const byModel = model => i => i.name === 'stone_hoe' && i.metadata === model;
async function use(bot, model) {
  const item = bot.inventory.items().find(byModel(model));
  if (!item) return false;
  await bot.equip(item, 'hand');
  await sleep(300);
  bot.activateItem();
  await sleep(700);
  return true;
}
const wornOf = (bot, other) => { const e = bot.worn && bot.worn.p.find(x => x[0] === other.entity.id); return e ? e[1] : null; };
// CraftBukkit sends a player's own max health as a flat base value (modifiers folded in).
const maxHealth = bot => { const a = bot.entity.attributes && bot.entity.attributes['generic.maxHealth']; return a ? a.value : null; };

async function main() {
  const a = await join('Mutant', 'hello 3'), b = await join('Watcher', 'hello 3');
  for (const n of ['Mutant', 'Watcher']) { console_('op ' + n); console_('gamemode survival ' + n); console_('gear effect ' + n + ' clear'); }
  console_('gear mutate Mutant baseline');
  console_('gear give Mutant supplies'); console_('gear give Mutant capacitor_belt');
  await sleep(1500);

  // Worn gear seen by another Phase 3 client.
  check(await use(a, 1), 'belt equipped by right-click');
  check(await until(() => { const w = wornOf(b, a); return w && w[3] === 'capacitor_belt'; }), 'watcher receives the worn belt (slot 3)', b.worn);
  a.chat('/gear show off');
  check(await until(() => b.worn && !wornOf(b, a)), '/gear show off hides it', b.worn);
  a.chat('/gear show on');
  check(await until(() => wornOf(b, a)), '/gear show on shows it again');

  // Mutations.
  check(await use(a, 27), 'brute mutagen used');
  check(await until(() => a.hud && a.hud.mu === 'Brute'), 'HUD shows the Brute mutation', a.hud);
  await sleep(1200);
  check(await until(() => maxHealth(a) === 28), 'Brute: max health 28 (+4 hearts)', maxHealth(a));
  check(await use(a, 24) && a.inventory.items().some(byModel(24)) && a.hud.mu === 'Brute', 'second mutagen refused while mutated (serum kept)');
  check(await use(a, 21), 'purge serum used');
  check(await until(() => a.hud && !a.hud.mu), 'purge returns to baseline', a.hud);
  check(await until(() => maxHealth(a) === 20), 'purge: max health back to 20', maxHealth(a));
  check(await use(a, 25), 'sprite mutagen used');
  check(await until(() => (a.flags & 4) === 4), 'Sprite may fly (abilities flag)', a.flags);
  await until(() => a.hud.a >= 60, 3000);
  const before = a.hud.a;
  a.say('key mutate');
  check(await until(() => a.hud.a <= before - 20), 'R: Mending Mist spends 30 adrenaline (net of regen)', {before, after: a.hud.a});
  console_('gear mutate Mutant wyrm');
  check(await until(() => a.hud.mu === 'Wyrm' && (a.flags & 4) === 0), 'admin mutate to Wyrm; Sprite flight revoked', {mu: a.hud.mu, flags: a.flags});
  await sleep(500);
  a.say('key mutate'); // cooldown was reset by the new mutation
  const w0 = a.hud.a;
  check(await until(() => a.hud.a <= w0 - 20), 'R: Fire Breath spends 30 (net of regen)', {w0, now: a.hud.a});

  // Creative column protocol.
  a.say('cput 3 ' + SNBT('capacitor_belt'));
  await sleep(800);
  check(a.state && a.state.slots[3].includes('capacitor_belt'), 'survival cput refused (slot unchanged, still the worn belt)');
  console_('gamemode creative Mutant');
  await sleep(1200);
  a.cursor = undefined;
  a.say('ctake 3 0');
  check(await until(() => a.state && a.state.slots[3] === '' && a.cursor), 'creative ctake: slot emptied, cursor set by the server', {cursor: a.cursor && a.cursor.blockId});
  check(a.cursor && a.cursor.blockId === 291 && a.cursor.itemDamage === 1, 'cursor carries the Capacitor Belt (stone hoe damage 1)', a.cursor);
  a.say('cput 0 ' + SNBT('capacitor_belt'));
  await sleep(800);
  check(a.state.slots[0] === '', 'belt refused in the neck slot');
  a.cursor = undefined;
  a.say('cput 3 ' + SNBT('capacitor_belt'));
  check(await until(() => a.state.slots[3].includes('capacitor_belt')), 'creative cput equips the belt');
  check(await until(() => a.cursor && a.cursor.blockId === -1), 'cursor cleared by the server', a.cursor);
  console_('gamemode survival Mutant');
  console_('gear status');
  await sleep(1500);
  a.quit(); b.quit();
}

async function restart() {
  const a = await join('Mutant', 'hello 3');
  check(await until(() => a.hud && a.hud.mu === 'Wyrm'), 'mutation survived a server restart', a.hud);
  a.quit();
}

(PHASE === 'restart' ? restart() : main()).then(() => {
  console.log((failures ? 'GEAR_BOT3 FAIL' : 'GEAR_BOT3 PASS') + ' checks=' + checks + ' failures=' + failures);
  setTimeout(() => process.exit(failures ? 1 : 0), 300);
}, error => { console.log('GEAR_BOT3 ERROR ' + (error && error.stack || error)); process.exit(1); });
