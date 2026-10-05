'use strict';
// Trinket icons on the real item classes of a real Paper 1.12.2 server (owner 2026-10-05: every trinket has its own texture).
// Compiles the four plugins' sources, loads their classes (never enabling the plugins) through tests/java/.../TrinketProbe and
// checks that every trinket, bauble and seal is an unbreakable stone carrier of the catalogue's band that its plugin still
// recognises, that items made before the icons are upgraded in place, and that the worn Faceless Mask and Crown stay vanilla.
// One server, below-normal priority, stopped at the end. Skipped when the server jar is missing.
//   TRK_SERVER_JAR=<paper runtime jar> TRK_GAME=<live checkout with server/cache and server/plugins> node tests/trinket-skins-runtime.cjs
const fs = require('node:fs'), path = require('node:path'), net = require('node:net');
const {spawn, spawnSync, execFileSync} = require('node:child_process');
const ROOT = path.resolve(__dirname, '..');
const GAME = process.env.TRK_GAME || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale';
const SERVER = process.env.TRK_SERVER_JAR || 'C:/Users/AM/Documents/JasperCraft-Dungeon-BigMobs/deps/runtime-paper.jar';
const JDK = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const PORT = 25673;
if (!fs.existsSync(SERVER) || !fs.existsSync(path.join(GAME, 'server/cache/patched_1.12.2.jar'))) { console.log('SKIP trinket-skins-runtime: server or game jars missing'); process.exit(0); }
const RUN = path.join(ROOT, 'testserver', 'trinkets-' + new Date().toISOString().replace(/[:.]/g, '-'));
fs.mkdirSync(path.join(RUN, 'plugins'), {recursive: true});
const api = path.join(GAME, 'server/cache/patched_1.12.2.jar');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let child, log = '', checks = 0;
const files = dir => fs.readdirSync(dir, {recursive: true}).filter(f => f.endsWith('.java')).map(f => path.join(dir, f));
function javac(sources, classpath, out) {
  fs.mkdirSync(out, {recursive: true});
  const r = spawnSync(path.join(JDK, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xlint:-options', '-cp', classpath, '-d', out, ...sources], {encoding: 'utf8'});
  if (r.status !== 0) throw new Error('javac failed: ' + (r.stdout + r.stderr).slice(0, 2000));
}
const cp = [api, ...fs.readdirSync(path.join(GAME, 'server/plugins')).filter(f => f.endsWith('.jar')).map(f => path.join(GAME, 'server/plugins', f))].join(path.delimiter);
function check(pass, name, detail) { checks++; console.log((pass ? 'PASS ' : 'FAIL ') + name + (detail ? ' ' + detail : '')); if (!pass) throw new Error(name); }
async function until(fn, timeout) { const end = Date.now() + timeout; while (Date.now() < end) { if (await fn()) return true; await sleep(150); } return !!(await fn()); }

(async () => {
  try {
    // 1. the plugins' item code, freshly compiled, and the probe
    for (const name of ['JasprDungeon', 'JasprRuins', 'JasprNether', 'JasprBackrooms'])
      javac(files(path.join(ROOT, 'server/custom-plugins', name, 'src')), cp, path.join(RUN, 'plugin-lib', name));
    const probeClasses = path.join(RUN, 'probe-classes');
    javac(files(path.join(ROOT, 'tests/java/chat/jaspr/trinketprobe')), api, probeClasses);
    execFileSync(path.join(JDK, 'jar.exe'), ['--create', '--file', path.join(RUN, 'plugins', 'TrinketProbe.jar'), '-C', probeClasses, '.', '-C', path.join(ROOT, 'tests/probe-resources/trinket-probe'), 'plugin.yml']);
    check(true, 'The four plugins and the probe compile');
    // 2. one small server
    fs.writeFileSync(path.join(RUN, 'eula.txt'), 'eula=true\n');
    fs.writeFileSync(path.join(RUN, 'server.properties'), ['server-ip=127.0.0.1', 'server-port=' + PORT, 'online-mode=false', 'level-name=world', 'level-type=FLAT', 'generator-settings=3;7,2*3,2;1;',
      'spawn-protection=0', 'spawn-animals=false', 'spawn-monsters=false', 'allow-nether=false', 'view-distance=3', 'max-players=2', 'max-tick-time=60000', ''].join('\n'));
    await new Promise((resolve, reject) => { const s = net.createServer(); s.once('error', reject); s.listen(PORT, '127.0.0.1', () => s.close(resolve)); });
    const sink = fs.createWriteStream(path.join(RUN, 'server.log'));
    child = spawn('java', ['-Xms128M', '-Xmx640M', '-XX:ActiveProcessorCount=2', '-Dcom.mojang.eula.agree=true', '-jar', SERVER, 'nogui', '--nojline'], {cwd: RUN, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
    try { execFileSync('powershell', ['-NoProfile', '-Command', '(Get-Process -Id ' + child.pid + ').PriorityClass="BelowNormal"'], {windowsHide: true}); } catch (e) { console.log('NOTE priority not lowered ' + e.message); }
    child.stdout.on('data', b => { log += b; sink.write(b); });
    child.stderr.on('data', b => { log += b; sink.write(b); });
    child.on('exit', () => sink.end());
    check(await until(() => log.includes('Done (') || child.exitCode !== null, 180000) && log.includes('Done ('), 'The server starts', log.slice(-200));
    check(log.includes('Enabling TrinketProbe'), 'The probe plugin is enabled');
    // 3. the checks
    for (const what of ['dungeon', 'ruins', 'nether', 'backrooms']) {
      const from = log.length;
      child.stdin.write('trkprobe ' + what + '\n');
      const done = await until(() => /TRK_(OK|FAIL) /.test(log.slice(from)), 30000);
      const text = log.slice(from), line = (text.split('\n').find(l => /TRK_(OK|FAIL) /.test(l)) || '').trim();
      const detail = (text.split('\n').find(l => l.includes('TRK_DETAIL')) || '').replace(/^.*TRK_DETAIL /, '').trim();
      const ok = done && /TRK_OK /.test(line);
      check(ok, what + ': every item is skinned, recognised and upgraded', (ok ? detail + ' | ' : '') + line.replace(/^.*(TRK_\w+)/, '$1') + (ok ? '' : '\n' + text.slice(0, 1500)));
    }
    check(!/Exception|DUNGEON_|RUINS_RELIC|NETHER_TRINKET/.test(log.split('Could not load server icon').join('')), 'No server exceptions');
    console.log('TRINKET_SKINS_RUNTIME_PASS checks=' + checks);
  } catch (e) {
    console.log('TRINKET_SKINS_RUNTIME_FAIL ' + (e && e.message));
    const tail = log.split('\n').filter(l => /WARN|ERROR|Exception|TRK_/.test(l)).slice(-14).join('\n');
    if (tail) console.log(tail);
    process.exitCode = 1;
  } finally {
    if (child && child.exitCode === null) { const c = child; c.stdin.write('stop\n'); if (!await until(() => c.exitCode !== null, 30000)) c.kill(); }
  }
})();
