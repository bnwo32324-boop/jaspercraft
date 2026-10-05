'use strict';
/* Realm armoury armour made before 2026-10-05 (helmet and boots 3 armour, exactly diamond's) is brought up to date in place on a
 * real Paper server (the live runtime jar, the wide-inventory build) with the new JasprGear and one protocol bot, at below-normal
 * priority and on its own loopback port. The bot wears an old Emerald Helmet (enchanted, with an anvil cost) beside current
 * leggings and carries an old pair of boots in its pack: within a few seconds both are rewritten (armour 4, everything else kept),
 * the server logs ARMORY_UPGRADE with counts only, the worn helmet's attribute reaches the client (armour 10 -> 11), the current
 * leggings are never touched, and a second pass changes nothing.
 *
 *   NODE_PATH=<a node_modules with mineflayer> node tests/armory-upgrade-runtime.cjs [out-dir]
 * Env: ARMORY_GEAR_JAR (default server/plugins/JasprGear.jar), REF_ROOT (the live checkout holding server/jaspr-paper-clientbudget.jar).
 */
const fs = require('node:fs'), path = require('node:path'), net = require('node:net'), os = require('node:os');
const {spawn, execFileSync} = require('node:child_process');
const mineflayer = require('mineflayer');
const ROOT = path.resolve(__dirname, '..');
const REF = process.env.REF_ROOT || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const GEAR_JAR = path.resolve(process.env.ARMORY_GEAR_JAR || path.join(ROOT, 'server', 'plugins', 'JasprGear.jar'));
const PORT = 25674, NAME = 'ArmorUpgrader';
const out = path.resolve(process.argv.slice(2).find(a => !a.startsWith('--')) || path.join(ROOT, 'candidate', 'armory-upgrade-runtime'));
fs.rmSync(out, {recursive: true, force: true});
fs.mkdirSync(path.join(out, 'plugins'), {recursive: true});
fs.copyFileSync(GEAR_JAR, path.join(out, 'plugins', 'JasprGear.jar'));
fs.copyFileSync(path.join(REF, 'server/eula.txt'), path.join(out, 'eula.txt'));
fs.writeFileSync(path.join(out, 'server.properties'), ['server-ip=127.0.0.1', 'server-port=' + PORT, 'online-mode=false', 'level-type=FLAT', 'generator-settings=3;7,2*3,2;1;',
  'spawn-protection=0', 'spawn-animals=false', 'spawn-monsters=false', 'allow-nether=false', 'view-distance=3', 'max-players=2', 'gamemode=0', 'difficulty=0', ''].join('\n'));
fs.writeFileSync(path.join(out, 'bukkit.yml'), 'settings:\n  allow-end: false\n');
let child, log = '', bot, checks = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));
function check(ok, what, extra) { checks++; console.log((ok ? 'PASS ' : 'FAIL ') + what + (extra !== undefined ? ' ' + JSON.stringify(extra).slice(0, 400) : '')); if (!ok) throw new Error(what); }
async function until(fn, ms) { const end = Date.now() + ms; while (Date.now() < end) { if (await fn()) return true; await sleep(150); } return !!(await fn()); }
const cmd = c => child.stdin.write(c + '\n');
/** A vanilla slot's modifier id as the signed halves 1.12 NBT writes. */
const half = (hex, lo) => String(BigInt.asIntN(64, BigInt('0x' + (lo ? hex.slice(16) : hex.slice(0, 16)))));
const UUIDS = {head: '2AD3F246FEE14E67B88669FD380BB150', legs: 'D8499B040E664726AB2964469D734E0D', feet: '845DB27CC624495F8C9F6020A9A58B6B'};
/** An emerald armour piece's SNBT with the given armour amount (the rest as ArmoryItems writes it). */
function snbt(piece, slot, armor, extra) {
  const m = (attr, name, amount) => '{AttributeName:"' + attr + '",Name:"' + name + '",Amount:' + amount + 'd,Operation:0,UUIDMost:' + half(UUIDS[slot], false) + 'L,UUIDLeast:' + half(UUIDS[slot], true) + 'L,Slot:"' + slot + '"}';
  return '{Unbreakable:1b,JasprArmory:{set:"emerald",piece:"' + piece + '"},JasprArmorySkin:1,AttributeModifiers:[' + m('generic.armor', 'Armor modifier', armor.toFixed(1))
    + ',' + m('generic.armorToughness', 'Armor toughness', '3.0') + ',' + m('generic.knockbackResistance', 'Armor knockback resistance', '0.05') + ']' + (extra || '') + '}';
}
const modifiers = item => ((item && item.nbt && item.nbt.value && item.nbt.value.AttributeModifiers && item.nbt.value.AttributeModifiers.value.value) || [])
  .map(x => ({attr: x.AttributeName.value, amount: x.Amount.value}));
const armourOf = item => (modifiers(item).find(x => x.attr === 'generic.armor') || {}).amount;
/** The client's armour attribute as the HUD reads it: the base value plus the added modifiers (the protocol sends them apart). */
const attr = () => {
  const a = bot.entity && bot.entity.attributes && bot.entity.attributes['generic.armor'];
  return a ? Math.min(30, a.value + (a.modifiers || []).filter(m => m.operation === 0).reduce((n, m) => n + m.amount, 0)) : undefined;
};

(async () => {
  try {
    child = spawn('java', ['-Xms256M', '-Xmx768M', '-XX:ActiveProcessorCount=1', '-Dcom.mojang.eula.agree=true', '-jar', path.join(REF, 'server/jaspr-paper-clientbudget.jar'), 'nogui', '--nojline'],
      {cwd: out, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
    try { os.setPriority(child.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
    const sink = fs.createWriteStream(path.join(out, 'server.log'));
    for (const s of [child.stdout, child.stderr]) s.on('data', b => { log += b; sink.write(b); });
    check(await until(() => log.includes('Done (') || child.exitCode !== null, 240000) && log.includes('Done ('), 'Paper starts');
    check(/GEAR_READY[^\n]* armoryArmour=24 /.test(log) && !/Error occurred while enabling/.test(log), 'JasprGear enables and reports 24 armoury armour points', (log.match(/GEAR_READY[^\n]*/) || [''])[0].slice(-200));
    bot = await new Promise((resolve, reject) => {
      const b = mineflayer.createBot({host: '127.0.0.1', port: PORT, username: NAME, version: '1.12.2', auth: 'offline'});
      b.on('error', reject); b.once('spawn', () => setTimeout(() => resolve(b), 800));
    });
    cmd('gamemode 0 ' + NAME);
    // Old helmet worn (enchanted, with an anvil cost), current leggings worn, old boots in the pack.
    cmd('replaceitem entity ' + NAME + ' slot.armor.legs minecraft:diamond_leggings 1 100 ' + snbt('leggings', 'legs', 7));
    cmd('replaceitem entity ' + NAME + ' slot.armor.head minecraft:diamond_helmet 1 100 ' + snbt('helmet', 'head', 3, ',ench:[{id:0s,lvl:3s}],RepairCost:5'));
    cmd('replaceitem entity ' + NAME + ' slot.hotbar.3 minecraft:diamond_boots 1 100 ' + snbt('boots', 'feet', 3));
    const upgrades = () => [...log.matchAll(/ARMORY_UPGRADE player=\S+ pieces=(\d+)/g)].reduce((a, m) => a + Number(m[1]), 0);
    check(await until(() => upgrades() >= 2, 15000), 'the server upgrades the worn helmet and the boots in the pack', {pieces: upgrades()});
    await sleep(2500);
    check(upgrades() === 2, 'nothing else is touched (the current leggings stay as they are)', {pieces: upgrades()});
    check(!/ARMORY_UPGRADE[^\n]*player=[^\n]*(password|token|ip=)/i.test(log), 'the log carries counts only');
    const head = bot.inventory.slots[5], legs = bot.inventory.slots[7];
    check(armourOf(head) === 4 && armourOf(legs) === 7, 'the helmet now reads 4 armour, the leggings still 7', {head: armourOf(head), legs: armourOf(legs)});
    const packBoots = bot.inventory.slots.find((it, i) => it && it.name === 'diamond_boots' && i >= 9);
    check(packBoots && armourOf(packBoots) === 4, 'the boots in the pack read 4 armour', {boots: packBoots && armourOf(packBoots)});
    const attrs = modifiers(head).map(x => x.attr).sort().join(',');
    check(attrs === 'generic.armor,generic.armorToughness,generic.knockbackResistance' && modifiers(head).every(x => x.amount === (x.attr === 'generic.armor' ? 4 : x.attr === 'generic.armorToughness' ? 3 : 0.05)), 'toughness and knockback resistance as before', modifiers(head));
    const nbt = head.nbt.value;
    check(nbt.RepairCost && nbt.RepairCost.value === 5 && nbt.ench && nbt.ench.value.value.length === 1 && nbt.JasprArmory.value.piece.value === 'helmet', 'enchantment, anvil cost and identity kept');
    check(await until(() => attr() === 11, 6000), 'the worn helmet counts: the client\'s armour attribute is 11 (helmet 4 + leggings 7)', {armour: attr()});
    await sleep(2500);
    check(upgrades() === 2, 'a second pass changes nothing', {pieces: upgrades()});
    check(!/Exception|SEVERE/.test(log.replace(/javax\.imageio\.IIOException/g, '')), 'no server exceptions');
    console.log('ARMORY_UPGRADE_RUNTIME_PASS checks=' + checks);
  } catch (e) {
    console.log('ARMORY_UPGRADE_RUNTIME_FAIL ' + (e && e.message));
    process.exitCode = 1;
  } finally {
    try { if (bot) bot.quit(); } catch (e) { }
    if (child && child.exitCode === null) { child.stdin.write('stop\n'); if (!(await until(() => child.exitCode !== null, 30000))) child.kill(); }
    setTimeout(() => process.exit(), 500).unref();
  }
})();
