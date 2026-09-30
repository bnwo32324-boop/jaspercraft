'use strict';
// Loopback-only fresh-vs-revisit light test (never touches the live server). One throwaway Paper (the live server jar,
// the live spigot/paper/bukkit settings, the live seed), the JasperCraft plugins from --jars, and a mineflayer bot that
// visits never-seen terrain in each world, then goes elsewhere, then revisits it. For every visit it counts, in the chunk
// data the server SENT, the light-emitting blocks (glowstone, lava, torches...) whose own block-light value is below their
// emission ("dark emitters"):
//   first = the first full copy of each chunk as it arrived        final = what the bot holds once the terrain has settled
//   server = the server's own light arrays at that moment (LightProbe census)
// A chunk sent before its light was worked out arrives with dark emitters; the same chunk sent again arrives without.
// Setup: bash scripts/light-lab/build-probe.sh (once). Needs mineflayer (JASPR_NODE_MODULES, or the perf sandbox's copy).
//   node scripts/light-lab/light-lab.cjs --jars <dir> --tag <name> [--worlds overworld,nether,end,atlas,ruins,backrooms] [--sites 3]
//        [--vd 4] [--ticktest nether,atlas] [--reload atlas,ruins] [--preflag on|off|none]
// --jars defaults to server/plugins (what is about to ship); point it at a copy of the live jars to measure "before".
// Results: candidate/light-lab/runs/<tag>-<time>/results.json and console.log (see scripts/light-lab/README.md).
const fs = require('fs'), path = require('path'), net = require('net'), os = require('os'), {spawn} = require('child_process');
const ROOT = path.resolve(__dirname, '../..');
let mineflayer;
for (const where of [process.env.JASPR_NODE_MODULES && path.join(process.env.JASPR_NODE_MODULES, 'mineflayer'), 'mineflayer', 'C:/Users/AM/Documents/JasperCraft-PerfSandbox/tools/node_modules/mineflayer']) {
  if (!where) continue;
  try { mineflayer = require(where); break; } catch (e) { }
}
if (!mineflayer) throw new Error('mineflayer not found: set JASPR_NODE_MODULES to a node_modules folder that has it');
const JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin/java.exe';
// the live server folder: its jar and its spigot/paper/bukkit settings are copied (read only) into the fixture
const LIVE = process.env.JASPR_LIVE_SERVER || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/server';
const LAB = path.join(ROOT, 'candidate/light-lab');

const argv = process.argv.slice(2);
const opt = (k, d) => { const i = argv.indexOf('--' + k); return i < 0 ? d : argv[i + 1]; };
const JARS = opt('jars', path.join(ROOT, 'server/plugins'));
const TAG = opt('tag', 'run');
const WORLDS = opt('worlds', 'overworld,nether,atlas,ruins').split(',');
const SITES_N = Math.max(2, Number(opt('sites', 3)));   // a revisit needs somewhere else to have been in between
const VD = Number(opt('vd', 4));
const PREFLAG = opt('preflag', null);       // 'on' | 'off' | 'none': overrides the per-world forced flag (the Backrooms control is forced off)
const TICKTEST = opt('ticktest', '').split(',').filter(Boolean);   // worlds: idle tick time with the flag off and on, alternating
const RANDOMTEST = opt('randomtest', '').split(',').filter(Boolean);   // worlds: do the game's random light checks near players light lamps the server never lit?
const RELOAD = opt('reload', '').split(',').filter(Boolean);       // worlds: leave, wait for the plugin to unload the world, come back
const FLAGS = opt('flags', '');            // 'on': switch the flag on for every world through the probe (A/B on one server)
const SEED = '-6436856966336135554';         // the live world's seed: same generator, same terrain as live
const runDir = path.join(LAB, 'runs', TAG + '-' + new Date().toISOString().replace(/[:.]/g, '-')), server = path.join(runDir, 'server');
const log = [];
let child, bot;
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function port() { const s = net.createServer(); await new Promise(r => s.listen(0, '127.0.0.1', r)); const p = s.address().port; await new Promise(r => s.close(r)); return p; }
const cmd = c => { child.stdin.write(c + '\n'); };
async function waitLog(re, ms, from = 0) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { for (let i = from; i < log.length; i++) if (re.test(log[i])) return log[i]; await sleep(150); }
  return null;
}

// world -> where the bot looks. Sites are far apart (more than two view distances), so each visit is fresh terrain.
const SITES = {
  overworld: {world: 'world', y: 120, pts: [[1200, 900], [-1400, 600], [300, -1500], [-900, -1100]]},
  nether: {world: 'world_nether', y: 64, pts: [[300, 300], [-400, 200], [200, -450], [-300, -350]]},
  end: {world: 'world_the_end', y: 70, pts: [[1400, 300], [-1300, 900], [300, -1500], [-900, -1000]]},
  atlas: {world: 'jaspr_atlas', y: 130, pts: [[-600, 0], [-420, -380], [620, 230], [850, 0]]},      // Astreion, Lampsa, Great Engine, Pellene
  ruins: {world: 'jaspr_ruins', y: 100, pts: [[320, 320], [-320, 640], [960, -320], [-640, -640]]},
  // the positive control: the Backrooms (their plugin already waits for light), switched OFF through the probe before any visit
  backrooms: {world: 'jaspr_levels', y: 41, pts: [[60, 0], [204, 0], [348, 0]], preflag: 'off'},
};

// --pts "atlas=1000,0;-1000,0|ruins=320,320": other visit sites for a world (e.g. next to Atlas's world border)
for (const spec of (opt('pts', '') || '').split('|').filter(Boolean)) {
  const [name, list] = spec.split('=');
  if (SITES[name]) SITES[name].pts = list.split(';').map(p => p.split(',').map(Number));
}

// Light emitters as minecraft-data knows them (emitLight >= 7; the unlit variants of furnaces and redstone ore are left
// out, they emit nothing). The chunk packets are parsed here, not by prismarine-chunk, which does not read the light
// nibbles in their wire order.
const NOT_EMITTERS = new Set(['redstone_ore', 'furnace', 'unpowered_repeater', 'unpowered_comparator']);
let EMIT = null;
function emitTable() {
  const t = new Map();
  for (const b of Object.values(bot.registry.blocks)) if (b.emitLight >= 7 && !NOT_EMITTERS.has(b.name)) t.set(b.id, {name: b.name, lvl: b.emitLight});
  return t;
}
const blank = () => ({cols: 0, sections: 0, emitters: 0, dark: 0, lit: 0, byType: {}});
function addInto(into, r) {
  into.cols += r.cols || 0; into.sections += r.sections || 0; into.emitters += r.emitters; into.dark += r.dark; into.lit += r.lit;
  for (const [k, v] of Object.entries(r.byType)) { const s = into.byType[k] || (into.byType[k] = {n: 0, dark: 0}); s.n += v.n; s.dark += v.dark; }
}
/** One full or partial chunk packet (1.12.2, protocol 340): per section the blocks and the two light arrays. */
function parseChunkPacket(packet) {
  const buf = packet.chunkData, skyLightSent = bot.game.dimension === 'overworld';
  let off = 0;
  const varInt = () => { let v = 0, shift = 0, b; do { b = buf[off++]; v |= (b & 0x7f) << shift; shift += 7; } while (b & 0x80); return v; };
  const secs = new Array(16).fill(null);
  for (let y = 0; y < 16; y++) {
    if (!((packet.bitMap >> y) & 1)) continue;
    const bits = buf[off++];
    const plen = varInt();
    let palette = null;
    if (bits <= 8) { palette = []; for (let i = 0; i < plen; i++) palette.push(varInt()); }
    const nlongs = varInt();
    const words = new Uint32Array(nlongs * 2 + 2);
    for (let k = 0; k < nlongs; k++) { words[2 * k] = buf.readUInt32BE(off + 8 * k + 4); words[2 * k + 1] = buf.readUInt32BE(off + 8 * k); }
    off += nlongs * 8;
    const blockLight = buf.subarray(off, off + 2048); off += 2048;
    if (skyLightSent) off += 2048;
    const mask = (1 << bits) - 1, st = blank();
    st.sections = 1;
    for (let i = 0; i < 4096; i++) {
      const bp = i * bits, w = bp >>> 5, sh = bp & 31;
      let v = words[w] >>> sh;
      if (sh + bits > 32) v |= words[w + 1] << (32 - sh);
      v &= mask;
      const light = (i & 1) ? blockLight[i >> 1] >> 4 : blockLight[i >> 1] & 15;
      if (light > 0) st.lit++;
      const e = EMIT.get((palette ? palette[v] : v) >> 4);
      if (e) {
        st.emitters++;
        const s = st.byType[e.name] || (st.byType[e.name] = {n: 0, dark: 0});
        s.n++;
        if (light < e.lvl) { st.dark++; s.dark++; }
      }
    }
    secs[y] = st;
  }
  if (off + (packet.groundUp ? 256 : 0) !== buf.length) throw new Error(`chunk packet length mismatch: parsed ${off}+${packet.groundUp ? 256 : 0} of ${buf.length}`);
  return secs;
}
function sumSections(secs) { const r = blank(); r.cols = 1; for (const s of secs) if (s) addInto(r, s); return r; }

// every chunk packet, parsed on arrival: the first full copy of each chunk, and the state the bot holds (partial updates applied)
let packets = 0, lastChunkAt = 0, firstSeen = null, parseFailures = 0, parseError = null;
const held = new Map();
function onMapChunk(packet) {
  packets++; lastChunkAt = Date.now();
  let secs;
  try { secs = parseChunkPacket(packet); } catch (e) { parseFailures++; parseError = e.message; return; }
  const key = packet.x + ',' + packet.z;
  if (packet.groundUp) held.set(key, secs);
  else { const cur = held.get(key); if (cur) for (let y = 0; y < 16; y++) if (secs[y]) cur[y] = secs[y]; }
  if (firstSeen && packet.groundUp && !firstSeen.has(key)) firstSeen.set(key, {cx: packet.x, cz: packet.z, r: sumSections(secs)});
}
function onUnload(packet) { held.delete(packet.chunkX + ',' + packet.chunkZ); }
function heldNear(bx, bz, radius) {
  const r = blank();
  for (const [key, secs] of held) { const [cx, cz] = key.split(',').map(Number); if (Math.abs(cx - bx) <= radius && Math.abs(cz - bz) <= radius) addInto(r, sumSections(secs)); }
  return r;
}
function colsNear(radius) {
  const p = bot.entity.position, bx = Math.floor(p.x / 16), bz = Math.floor(p.z / 16);
  let n = 0;
  for (const key of held.keys()) { const [cx, cz] = key.split(',').map(Number); if (Math.abs(cx - bx) <= radius && Math.abs(cz - bz) <= radius) n++; }
  return n;
}
const strip = s => s.replace(/^.*?\]: /, '');
/** Teleports (console command or a chat command the bot runs), waits for the terrain to arrive and go quiet, counts. */
async function visit(label, how, target, worldName) {
  const t0 = Date.now();
  packets = 0; lastChunkAt = 0; firstSeen = new Map();
  const logFrom = log.length;
  if (how === 'chat') bot.chat(target.chat); else cmd(`lprobe tp lightbot ${target.world} ${target.x} ${target.y} ${target.z}`);
  let arrived = false;
  for (let i = 0; i < 400 && !arrived; i++) {
    const p = bot.entity && bot.entity.position;
    if (p && Math.abs(p.x - target.x) < 48 && Math.abs(p.z - target.z) < 48) arrived = true; else await sleep(150);
  }
  const arrivedMs = Date.now() - t0;
  const want = Math.floor((2 * VD + 1) * (2 * VD + 1) * 0.5);
  let settled = false;
  while (Date.now() - t0 < 180000) {
    if (packets > 0 && Date.now() - lastChunkAt > 3500 && colsNear(VD) >= want) { settled = true; break; }
    await sleep(250);
  }
  const p = bot.entity.position, bx = Math.floor(p.x / 16), bz = Math.floor(p.z / 16);
  const finalState = heldNear(bx, bz, VD);
  const first = blank();
  for (const f of firstSeen.values()) if (Math.abs(f.cx - bx) <= VD && Math.abs(f.cz - bz) <= VD) addInto(first, f.r);
  const from = log.length;
  cmd(`lprobe census ${target.world} ${bx} ${bz} ${VD}`);
  const sl = await waitLog(/LP_CENSUS/, 15000, from);
  const srv = {};
  if (sl) for (const m of sl.matchAll(/(\w+)=(\S+)/g)) srv[m[1]] = /^-?\d+$/.test(m[2]) ? Number(m[2]) : m[2];
  const out = {label, arrived, settled, arrivedMs, settleMs: Date.now() - t0, packets, pos: p.floored().toArray(), first, held: finalState, server: srv};
  const errs = log.slice(logFrom).filter(l => /Exception|SEVERE|Could not pass/.test(l));
  if (errs.length) out.errors = errs.slice(0, 3).map(strip);
  return out;
}
const pct = (a, b) => b ? (100 * a / b).toFixed(1) + '%' : '-';
function line(v) {
  return `${v.label.padEnd(10)} pk=${String(v.packets).padStart(3)} settle=${(v.settleMs / 1000).toFixed(0).padStart(3)}s  first: cols=${v.first.cols} emit=${v.first.emitters} dark=${v.first.dark} (${pct(v.first.dark, v.first.emitters)}) lit=${v.first.lit}` +
    `  | held: cols=${v.held.cols} emit=${v.held.emitters} dark=${v.held.dark} (${pct(v.held.dark, v.held.emitters)})  | server: chunks=${v.server.chunks} emit=${v.server.emitters} dark=${v.server.dark} (${pct(v.server.dark, v.server.emitters)})`;
}

(async () => {
  fs.mkdirSync(path.join(server, 'plugins'), {recursive: true});
  fs.copyFileSync(path.join(LIVE, 'jaspr-paper-clientbudget.jar'), path.join(server, 'paper.jar'));
  const jars = ['JasprHorrorBiomes', 'JasprImportedWorldgen', 'JasprLostCities', 'JasprNether', 'JasprAtlas', 'JasprRuins', 'JasprPerfTweaks'];
  if (WORLDS.includes('backrooms')) jars.push('JasprBackrooms');
  for (const j of jars) fs.copyFileSync(path.join(JARS, j + '.jar'), path.join(server, 'plugins', j + '.jar'));
  fs.copyFileSync(path.join(LAB, 'LightProbe.jar'), path.join(server, 'plugins/LightProbe.jar'));
  // the live settings (spigot/paper/bukkit), with the view distance trimmed for a small fixture (the End only when tested)
  fs.copyFileSync(path.join(LIVE, 'paper.yml'), path.join(server, 'paper.yml'));
  fs.writeFileSync(path.join(server, 'spigot.yml'), fs.readFileSync(path.join(LIVE, 'spigot.yml'), 'utf8').replace(/view-distance: \d+/, 'view-distance: ' + VD));
  fs.writeFileSync(path.join(server, 'bukkit.yml'), fs.readFileSync(path.join(LIVE, 'bukkit.yml'), 'utf8').replace('allow-end: true', 'allow-end: ' + (WORLDS.includes('end') ? 'true' : 'false')));
  fs.writeFileSync(path.join(server, 'eula.txt'), 'eula=true\n');
  const p = await port();
  fs.writeFileSync(path.join(server, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${p}\nonline-mode=false\nlevel-name=world\nlevel-seed=${SEED}\nlevel-type=DEFAULT\ngenerate-structures=true\nallow-nether=true\nview-distance=${VD}\nmax-players=3\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\ndifficulty=0\ngamemode=1\nallow-flight=true\nspawn-protection=0\nenable-rcon=false\nenable-query=false\nannounce-player-achievements=false\n`);
  const args = ['-Xms1G', '-Xmx2G', '-XX:+UseG1GC', '-XX:ActiveProcessorCount=3', '-Dfile.encoding=UTF-8', '-Dcom.mojang.eula.agree=true', '-jar', 'paper.jar', 'nogui'];
  child = spawn(JAVA, args, {cwd: server, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  try { os.setPriority(child.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); } catch (e) { }
  const out = fs.createWriteStream(path.join(runDir, 'console.log'));
  let buf = '';
  const onLine = l => { log.push(l); out.write(l + '\n'); };
  child.stdout.on('data', d => { buf += d; let i; while ((i = buf.indexOf('\n')) >= 0) { onLine(buf.slice(0, i).replace(/\r$/, '')); buf = buf.slice(i + 1); } });
  child.stderr.on('data', d => onLine('[stderr] ' + String(d).trim()));
  console.log(`[lab] ${TAG}: jars=${path.basename(JARS)} port=${p} worlds=${WORLDS} sites=${SITES_N} vd=${VD}`);
  const t0 = Date.now();
  const done = await waitLog(/Done \(/, 420000);
  if (!done) throw new Error('server did not start');
  console.log(`[lab] server up in ${((Date.now() - t0) / 1000).toFixed(0)} s: ${strip(done)}`);
  await sleep(2000);
  cmd('lprobe worlds');
  const worldsLine = await waitLog(/LP_WORLDS/, 8000);
  console.log('[lab] flags at start: ' + (worldsLine || '?').replace(/^.*LP_WORLDS /, ''));
  const lightLines = log.filter(l => /_LIGHT|PERF_TWEAKS_READY/.test(l)).map(strip);
  for (const l of lightLines) console.log('[lab]   log: ' + l);

  bot = mineflayer.createBot({host: '127.0.0.1', port: p, username: 'lightbot', version: '1.12.2', auth: 'offline', hideErrors: true, viewDistance: 12, checkTimeoutInterval: 240000});
  bot.on('error', e => console.log('[bot error] ' + e.message));
  bot.on('kicked', r => console.log('[bot kicked] ' + String(r).slice(0, 200)));
  bot.on('message', m => { const t = m.toString(); if (t.trim()) log.push('[chat] ' + t); });
  await new Promise(r => bot.once('spawn', r));
  EMIT = emitTable();
  console.log(`[lab] emitter kinds (emitLight>=7): ${EMIT.size}`);
  bot._client.on('map_chunk', onMapChunk);
  bot._client.on('unload_chunk', onUnload);
  bot.physicsEnabled = false;
  cmd('op lightbot'); cmd('gamemode creative lightbot');
  await sleep(1500);
  if (FLAGS === 'on') for (const w of ['world', 'world_nether', 'world_the_end']) cmd(`lprobe flag ${w} on`);

  const results = {tag: TAG, jars: JARS, vd: VD, flagsAtStart: worldsLine && worldsLine.replace(/^.*LP_WORLDS /, ''), lightLines, worlds: {}};
  for (const name of WORLDS) {
    const S = SITES[name];
    if (!S) { console.log('[lab] unknown world ' + name); continue; }
    const pts = S.pts.slice(0, SITES_N);
    console.log(`[lab] === ${name} (${S.world})`);
    const wr = results.worlds[name] = {world: S.world, fresh: [], revisit: []};
    // getting into plugin-owned worlds: their own owner commands create them
    if (name === 'atlas') wr.fresh.push(await visit('fresh#0', 'chat', {chat: `/atlas at ${pts[0][0]} ${pts[0][1]}`, world: S.world, x: pts[0][0], y: S.y, z: pts[0][1]}));
    else if (name === 'ruins') { bot.chat('/ruins tp'); await sleep(6000); }
    else if (name === 'backrooms') { bot.chat('/backrooms tp 1'); await sleep(8000); }
    cmd('lprobe worlds');
    const wl = await waitLog(/LP_WORLDS/, 8000, log.length - 5);
    wr.flagsNow = wl && wl.replace(/^.*LP_WORLDS /, '');
    console.log('[lab]   flags now: ' + wr.flagsNow);
    const wlog = log.filter(l => /_LIGHT/.test(l)).map(strip);
    for (const l of wlog.filter(l => !lightLines.includes(l))) { console.log('[lab]   log: ' + l); lightLines.push(l); }
    if (FLAGS === 'on') cmd(`lprobe flag ${S.world} on`);
    const pre = PREFLAG === null ? S.preflag : (PREFLAG === 'none' ? null : PREFLAG);
    if (pre) { cmd(`lprobe flag ${S.world} ${pre}`); await sleep(500); console.log(`[lab]   flag ${S.world} forced ${pre}`); }
    for (let i = name === 'atlas' ? 1 : 0; i < pts.length; i++) { const v = await visit('fresh#' + i, 'cmd', {world: S.world, x: pts[i][0], y: S.y, z: pts[i][1]}); wr.fresh.push(v); console.log('[lab]   ' + line(v)); }
    for (let i = 0; i < pts.length; i++) { const v = await visit('revisit#' + i, 'cmd', {world: S.world, x: pts[i][0], y: S.y, z: pts[i][1]}); wr.revisit.push(v); console.log('[lab]   ' + line(v)); }
    const sum = (arr, k) => arr.reduce((a, r) => ({cols: a.cols + (r[k].cols || r[k].chunks || 0), emitters: a.emitters + r[k].emitters, dark: a.dark + r[k].dark, lit: a.lit + (r[k].lit || 0)}), {cols: 0, emitters: 0, dark: 0, lit: 0});
    wr.total = {fresh: {first: sum(wr.fresh, 'first'), held: sum(wr.fresh, 'held'), server: sum(wr.fresh, 'server')}, revisit: {first: sum(wr.revisit, 'first'), held: sum(wr.revisit, 'held'), server: sum(wr.revisit, 'server')}};
    for (const k of ['fresh', 'revisit']) {
      const t = wr.total[k];
      console.log(`[lab]   TOTAL ${k.padEnd(7)} first: emit=${t.first.emitters} dark=${t.first.dark} (${pct(t.first.dark, t.first.emitters)})  held: dark=${t.held.dark}/${t.held.emitters} (${pct(t.held.dark, t.held.emitters)})  server: dark=${t.server.dark}/${t.server.emitters} (${pct(t.server.dark, t.server.emitters)})`);
    }
    if (TICKTEST.includes(name)) {
      const sample = async () => { const from = log.length; cmd('lprobe ticks'); const l = await waitLog(/LP_TICKS/, 5000, from); const m = l && /meanUs=(\d+) maxUs=(\d+)/.exec(l); return m ? [Number(m[1]), Number(m[2])] : null; };
      const avg = a => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
      const res = {off: [], on: []};
      for (const cond of ['off', 'on', 'off', 'on', 'off', 'on']) {
        cmd(`lprobe flag ${S.world} ${cond}`); await sleep(15000);
        const means = [], maxes = [];
        for (let i = 0; i < 8; i++) { await sleep(2500); const t = await sample(); if (t) { means.push(t[0]); maxes.push(t[1]); } }
        res[cond].push({meanUs: Math.round(avg(means)), maxUs: Math.max(...maxes)});
        console.log(`[lab]   tick window flag=${cond}: mean ${Math.round(avg(means))} us, worst ${Math.max(...maxes)} us`);
      }
      wr.tick = res;
      cmd(`lprobe flag ${S.world} on`);
    }
    if (RANDOMTEST.includes(name)) {
      const from0 = log.length, cx0 = Math.floor(bot.entity.position.x / 16), cz0 = Math.floor(bot.entity.position.z / 16);
      cmd(`lprobe finddark ${S.world} ${cx0} ${cz0} 3`);
      const dl = await waitLog(/LP_DARK/, 10000, from0);
      const spots = dl ? [...dl.matchAll(/(-?\d+),(-?\d+),(-?\d+)/g)].map(m => m.slice(1).map(Number)) : [];
      if (!spots.length) console.log('[lab]   randomtest: no dark emitter found');
      else {
        const [x, y, z] = spots[0];
        cmd(`lprobe tp lightbot ${S.world} ${x + 0.5} ${y + 1} ${z + 0.5}`);
        await sleep(3000);
        const near = async () => { const f = log.length; cmd(`lprobe near ${S.world} ${x} ${y} ${z} 5`); const l = await waitLog(/LP_NEAR/, 8000, f); const m = l && /emitters=(\d+) dark=(\d+)/.exec(l); return m ? [Number(m[1]), Number(m[2])] : null; };
        const series = {off: [], on: []};
        cmd(`lprobe flag ${S.world} off`);
        for (let i = 0; i < 3; i++) { series.off.push(await near()); await sleep(30000); }
        cmd(`lprobe flag ${S.world} on`);
        for (let i = 0; i < 7; i++) { series.on.push(await near()); await sleep(30000); }
        wr.randomTest = {spot: [x, y, z], series};
        console.log(`[lab]   random light checks near a dark emitter at ${x},${y},${z} ([emitters, dark] within 5 blocks, every 30 s): flag off ${JSON.stringify(series.off)}; flag on ${JSON.stringify(series.on)}`);
      }
    }
    if (RELOAD.includes(name) && (name === 'atlas' || name === 'ruins')) {
      const tag = name === 'atlas' ? 'ATLAS' : 'RUINS';
      const from = log.length;
      cmd('lprobe tp lightbot world 0 100 0');
      const unloaded = await waitLog(new RegExp(tag + '_WORLD_UNLOADED'), 150000, from);
      console.log(`[lab]   ${S.world} unloaded by its plugin: ${!!unloaded}`);
      const fromLoad = log.length;
      bot.chat(name === 'atlas' ? '/atlas at -600 0' : '/ruins tp');
      const loaded = await waitLog(new RegExp(tag + '_WORLD_LOADED'), 90000, fromLoad);
      await sleep(2000);
      const lightAgain = log.slice(fromLoad).filter(l => /_LIGHT/.test(l)).map(strip);
      cmd('lprobe worlds');
      const wl2 = await waitLog(/LP_WORLDS/, 8000, fromLoad);
      wr.reload = {unloaded: !!unloaded, loaded: !!loaded, lightLines: lightAgain, flags: wl2 && wl2.replace(/^.*LP_WORLDS /, '')};
      console.log(`[lab]   reloaded: ${!!loaded}; light lines: ${lightAgain.join(' | ') || 'none'}; flags: ${wr.reload.flags}`);
    }
    fs.writeFileSync(path.join(runDir, 'results.json'), JSON.stringify(results, null, 1));
  }
  results.parseFailures = parseFailures; results.parseError = parseError;
  results.errors = log.filter(l => /Exception|SEVERE|Could not pass event/.test(l)).slice(0, 30).map(strip);
  fs.writeFileSync(path.join(runDir, 'results.json'), JSON.stringify(results, null, 1));
  console.log(`[lab] exceptions in server log: ${results.errors.length}; packet parse failures: ${parseFailures}${parseError ? ' (' + parseError + ')' : ''}`);
  bot.quit();
  cmd('stop');
  await Promise.race([new Promise(r => child.once('exit', r)), sleep(60000)]);
  try { if (child.exitCode === null) child.kill(); } catch (e) { }
  console.log('[lab] done: ' + path.join(runDir, 'results.json'));
  process.exit(0);
})().catch(e => {
  console.log('[lab] DRIVER ERROR ' + (e.stack || e));
  try { if (bot) bot.quit(); } catch (x) { }
  try { cmd('stop'); } catch (x) { }
  setTimeout(() => { try { if (child && child.exitCode === null) child.kill(); } catch (x) { } process.exit(2); }, 15000);
});
