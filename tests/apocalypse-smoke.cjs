#!/usr/bin/env node
'use strict';

/**
 * Real Paper 1.12.2 integration test, using only built-in Node modules and JDK 17.
 * Run: node tests/apocalypse-smoke.cjs --paper
 * Optional: --wait-seconds=90 --timeout-seconds=240 --java-home="path to JDK 17"
 *
 * The candidate jar is the runtime subject. Production sources are compiled first
 * into the fixture to catch build errors, then the probe is compiled against the
 * candidate. Neither compilation updates the shared candidate or live plugins.
 * Only patched Paper, AuthMe.jar and the candidate jar enter the fresh server.
 * Every generated file stays in candidate/apocalypse-smoke-UUID; artifacts remain
 * for diagnosis. No launcher, live server command, world or live config is used.
 */
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..');
// An explicit disposable build can be tested without writing the shared release candidate.
const CANDIDATE = process.env.JASPR_APOCALYPSE_TEST_JAR
  ? path.resolve(process.env.JASPR_APOCALYPSE_TEST_JAR)
  : path.join(ROOT, 'candidate', 'apocalypse', 'JasprApocalypse.jar');
const SOURCE = path.join(ROOT, 'server', 'custom-plugins', 'JasprApocalypse', 'src');
const PROBE = path.join(__dirname, 'java', 'chat', 'jaspr', 'apocalypse', 'ApocalypseProbe.java');
const SEED = '6840227782638526189';
const RESULT_PREFIX = 'APOCALYPSE_SMOKE_RESULT ';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function options(argv) {
  const result = { wait: 90, timeout: 240, javaHome: process.env.JAVA17_HOME || '' };
  for (const arg of argv) {
    if (arg === '--paper') continue; // Always bypass the launcher's update/download path.
    if (arg === '--help') return null;
    if (arg.startsWith('--java-home=')) result.javaHome = arg.slice('--java-home='.length);
    else if (arg.startsWith('--wait-seconds=')) result.wait = Number(arg.split('=')[1]);
    else if (arg.startsWith('--timeout-seconds=')) result.timeout = Number(arg.split('=')[1]);
    else throw new Error(`Unknown argument: ${arg}`);
  }
  if (!Number.isFinite(result.wait) || result.wait < 0 || result.wait > 600
      || !Number.isFinite(result.timeout) || result.timeout < 30 || result.timeout > 1200) {
    throw new Error('Use wait-seconds in 0..600 and timeout-seconds in 30..1200.');
  }
  return result;
}

function javaTool(settings, name) {
  return settings.javaHome
    ? path.join(settings.javaHome, 'bin', name + (process.platform === 'win32' ? '.exe' : ''))
    : name;
}

function javaSources(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(dir, entry.name);
    return entry.isDirectory() ? javaSources(file) : entry.isFile() && file.endsWith('.java') ? [file] : [];
  }).sort();
}

function runTool(command, args, cwd, logFile) {
  const temporary = path.join(cwd, 'tmp');
  fs.mkdirSync(temporary, { recursive: true });
  const flag = /^java(?:\.exe)?$/i.test(path.basename(command)) ? '-D' : '-J-D';
  args = [`${flag}java.io.tmpdir=${temporary}`, ...args];
  const run = spawnSync(command, args, { cwd, encoding: 'utf8', windowsHide: true,
    env: { ...process.env, TMP: temporary, TEMP: temporary, TMPDIR: temporary },
    timeout: 90000, maxBuffer: 8 * 1024 * 1024 });
  fs.appendFileSync(logFile, `$ ${path.basename(command)} ${args.join(' ')}\n${run.stdout || ''}${run.stderr || ''}\n`);
  if (run.error) throw run.error;
  if (run.status !== 0) throw new Error(`${path.basename(command)} failed (${run.status}); see ${logFile}\n${run.stderr || run.stdout}`);
  return (run.stdout || '') + (run.stderr || '');
}

async function reservePort() {
  const reservation = net.createServer();
  await new Promise((resolve, reject) => {
    reservation.once('error', reject);
    reservation.listen({ host: '127.0.0.1', port: 0, exclusive: true }, resolve);
  });
  return { port: reservation.address().port,
    release: () => new Promise((resolve, reject) => reservation.close(error => error ? reject(error) : resolve())) };
}

async function main() {
  const settings = options(process.argv.slice(2));
  if (!settings) {
    console.log('node tests/apocalypse-smoke.cjs [--paper] [--wait-seconds=90] [--timeout-seconds=240] [--java-home=JDK17]');
    return;
  }
  const fixture = path.join(ROOT, 'candidate', `apocalypse-smoke-${crypto.randomUUID()}`);
  fs.mkdirSync(path.dirname(fixture), { recursive: true });
  fs.mkdirSync(fixture); // Exclusive UUID directory: never reuse an existing server/world.
  const server = path.join(fixture, 'server');
  const buildLog = path.join(fixture, 'build.log');
  const paperLog = path.join(fixture, 'paper.log');
  const reportPath = path.join(fixture, 'result.json');
  let child, reservation, logFd, interrupted, closed = false, childError, exitCode, exitSignal;
  let tail = '', pendingLine = '', probeResult, ready = false;
  const serverErrors = [];
  const report = { fixture, seed: SEED, subject: CANDIDATE, success: false, startedAt: new Date().toISOString() };
  const onSignal = signal => { interrupted = new Error(`Interrupted by ${signal}`); };
  const onInt = () => onSignal('SIGINT');
  const onTerm = () => onSignal('SIGTERM');
  const onExit = () => { if (child && !closed) child.kill(); };
  process.on('SIGINT', onInt);
  process.on('SIGTERM', onTerm);
  process.once('exit', onExit);
  function checkRunning() {
    if (interrupted) throw interrupted;
    if (childError) throw childError;
    if (child && closed) throw new Error(`Paper exited before completion (code=${exitCode}, signal=${exitSignal}).\n${tail}`);
  }
  function write(relative, text) {
    const destination = path.join(server, relative);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, text);
  }
  function output(chunk) {
    fs.writeSync(logFd, chunk);
    const text = chunk.toString('utf8');
    tail = (tail + text).slice(-16000);
    pendingLine += text;
    let end;
    while ((end = pendingLine.indexOf('\n')) !== -1) {
      const line = pendingLine.slice(0, end).replace(/\r$/, '');
      pendingLine = pendingLine.slice(end + 1);
      if (/Done \([\d.,]+s\)!/.test(line)) ready = true;
      if (/\b(?:ERROR|SEVERE)\b/.test(line) && serverErrors.length < 50) serverErrors.push(line);
      const marker = line.indexOf(RESULT_PREFIX);
      if (marker !== -1) {
        try {
          probeResult = JSON.parse(line.slice(marker + RESULT_PREFIX.length));
          console.log(`Bukkit result: success=${probeResult.success} assertions=${probeResult.assertions} failures=${probeResult.failures.length}`);
        }
        catch (error) { childError = new Error(`Invalid probe result: ${error.message}`); }
      } else if (line.includes('APOCALYPSE_SMOKE_') || line.includes('APOCALYPSE_READY')) console.log(line);
    }
  }
  console.log(`Isolated apocalypse fixture: ${fixture}`);
  try {
    const waitDeadline = Date.now() + settings.wait * 1000;
    if (!fs.existsSync(CANDIDATE)) console.log(`Waiting up to ${settings.wait}s for ${CANDIDATE}`);
    while (!fs.existsSync(CANDIDATE)) {
      checkRunning();
      if (Date.now() >= waitDeadline) throw new Error(`Candidate jar not available: ${CANDIDATE}`);
      await delay(250);
    }
    const api = path.join(ROOT, 'server', 'cache', 'patched_1.12.2.jar');
    const auth = path.join(ROOT, 'server', 'plugins', 'AuthMe.jar');
    for (const input of [api, auth, CANDIDATE, PROBE]) {
      if (!fs.statSync(input).isFile()) throw new Error(`Required input is not a file: ${input}`);
    }
    const version = runTool(javaTool(settings, 'java'), ['-version'], fixture, buildLog);
    if (!/version "17(?:\.|"|-)/.test(version)) throw new Error('This fixture requires Java 17. Set --java-home.');
    const compiler = runTool(javaTool(settings, 'javac'), ['-version'], fixture, buildLog);
    if (!/javac 17(?:\.|\s|$)/.test(compiler)) throw new Error('The fixture compiler must also be JDK 17.');
    fs.mkdirSync(path.join(server, 'plugins'), { recursive: true });
    const stagedJar = path.join(server, 'plugins', 'JasprApocalypse.jar');
    fs.copyFileSync(api, path.join(server, 'patched_1.12.2.jar'));
    fs.copyFileSync(auth, path.join(server, 'plugins', 'AuthMe.jar'));
    fs.copyFileSync(CANDIDATE, stagedJar);
    report.sha256 = crypto.createHash('sha256').update(fs.readFileSync(stagedJar)).digest('hex');
    const pluginClasses = path.join(fixture, 'plugin-classes');
    const probeClasses = path.join(fixture, 'probe-classes');
    fs.mkdirSync(pluginClasses);
    fs.mkdirSync(probeClasses);
    const compileArgs = ['--release', '8', '-encoding', 'UTF-8', '-proc:none'];
    console.log('Compiling production plugin sources, then the Bukkit probe against the candidate jar.');
    runTool(javaTool(settings, 'javac'), [...compileArgs, '-cp', [api, auth].join(path.delimiter),
      '-d', pluginClasses, ...javaSources(SOURCE)], fixture, buildLog);
    runTool(javaTool(settings, 'javac'), [...compileArgs, '-cp', [api, auth, stagedJar].join(path.delimiter),
      '-d', probeClasses, PROBE], fixture, buildLog);
    fs.writeFileSync(path.join(probeClasses, 'plugin.yml'),
      'name: ApocalypseProbe\nversion: 1.0\nmain: chat.jaspr.apocalypse.ApocalypseProbe\ndepend: [AuthMe, JasprApocalypse]\ncommands:\n  apocalypsesmoke:\n    description: Console-only isolated integration probe\n');
    runTool(javaTool(settings, 'jar'), ['--create', '--file', path.join(server, 'plugins', 'ApocalypseProbe.jar'),
      '-C', probeClasses, '.'], fixture, buildLog);
    reservation = await reservePort();
    report.port = reservation.port;
    write('eula.txt', 'eula=true\n');
    write('server.properties', `server-ip=127.0.0.1\nserver-port=${reservation.port}\nonline-mode=false\nenable-rcon=false\nenable-query=false\nlevel-name=world\nlevel-seed=${SEED}\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\ngenerate-structures=false\ndifficulty=1\nmax-players=1\nview-distance=2\nspawn-protection=0\nallow-nether=false\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\nmax-tick-time=60000\nnetwork-compression-threshold=-1\n`);
    write('bukkit.yml', 'settings:\n  allow-end: false\n  update-folder: update\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  ambient: 0\n');
    write('spigot.yml', 'config-version: 11\nsettings:\n  late-bind: true\nworld-settings:\n  default:\n    view-distance: 2\n');
    write('paper.yml', 'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n');
    write('plugins/bStats/config.yml', 'enabled: false\nserverUuid: "' + crypto.randomUUID() + '"\nlogFailedRequests: false\n');
    write('plugins/AuthMe/config.yml', 'DataSource:\n  backend: SQLITE\nsettings:\n  sessions:\n    enabled: false\n  registration:\n    enabled: true\n    force: true\n  restrictions:\n    ForceSingleSession: true\n    kickNonRegistered: false\n    ProtectInventoryBeforeLogIn: false\n    allowCommands:\n      - /login\n      - /register\n      - /tp\n      - /teleport\n  updates:\n    checkForUpdates: false\nProtection:\n  geoIpDatabase:\n    enabled: false\n');
    write('plugins/JasprApocalypse/config.yml', 'world: world\nsiege:\n  detection-range: 48\n  max-active-zombies: 80\n  block-breaks-per-pass: 6\n  tnt-radius: 2.6\n  tnt-max-blocks: 32\nruins:\n  enabled: true\nresource-pack:\n  url: ""\n  sha1: ""\n');
    fs.writeFileSync(path.join(fixture, 'fixture.json'), JSON.stringify(report, null, 2) + '\n');
    checkRunning();
    await reservation.release();
    reservation = null;
    logFd = fs.openSync(paperLog, 'wx');
    const temporary = path.join(fixture, 'tmp');
    const args = ['-Xms128M', '-Xmx768M', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-Djaspr.apocalypse.smoke=true',
      `-Djava.io.tmpdir=${temporary}`,
      '-Djava.awt.headless=true', '-Dcom.mojang.eula.agree=true', '-jar', 'patched_1.12.2.jar', '--nojline'];
    console.log(`Starting Java 17 / patched Paper directly on 127.0.0.1:${report.port}`);
    child = spawn(javaTool(settings, 'java'), args, { cwd: server, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
      env: { ...process.env, TMP: temporary, TEMP: temporary, TMPDIR: temporary } });
    report.pid = child.pid;
    child.stdout.on('data', output);
    child.stderr.on('data', output);
    child.stdin.on('error', error => { if (!closed) childError = error; });
    child.on('error', error => { childError = error; });
    child.on('close', (code, signal) => { closed = true; exitCode = code; exitSignal = signal; });
    const deadline = Date.now() + settings.timeout * 1000;
    while (!ready) {
      checkRunning();
      if (Date.now() >= deadline) throw new Error(`Paper readiness timed out.\n${tail}`);
      await delay(100);
    }
    child.stdin.write('apocalypsesmoke\n');
    while (!probeResult) {
      checkRunning();
      if (Date.now() >= deadline) throw new Error(`Bukkit probe timed out.\n${tail}`);
      await delay(100);
    }
    report.probe = probeResult;
    if (probeResult.success !== true) throw new Error(`Bukkit assertions failed: ${probeResult.failures.join('; ')}`);
    report.success = true;
  } catch (error) {
    report.error = error.stack || String(error);
    process.exitCode = 1;
  } finally {
    if (reservation) await reservation.release().catch(error => { report.cleanupError = String(error); });
    if (child && !closed) {
      console.log(`Stopping isolated Paper child ${child.pid}.`);
      if (child.stdin.writable && !child.stdin.destroyed) child.stdin.write('stop\n');
      let deadline = Date.now() + 15000;
      while (!closed && Date.now() < deadline) await delay(100);
      if (!closed) {
        report.forcedStop = true;
        child.kill('SIGKILL'); // Only this directly spawned JVM; never taskkill/java-wide cleanup.
        deadline = Date.now() + 5000;
        while (!closed && Date.now() < deadline) await delay(100);
      }
      if (!closed) report.cleanupError = `Could not confirm exit of fixture JVM ${child.pid}`;
    }
    if (logFd !== undefined) fs.closeSync(logFd);
    report.childStopped = !child || closed;
    if (child) { report.exitCode = exitCode; report.exitSignal = exitSignal; }
    report.serverErrors = serverErrors;
    if (serverErrors.length || report.cleanupError || report.forcedStop || (child && exitCode !== 0)) report.success = false;
    if (!report.success) process.exitCode = 1;
    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
    process.removeListener('SIGINT', onInt);
    process.removeListener('SIGTERM', onTerm);
    if (report.childStopped) process.removeListener('exit', onExit);
    console.log(`${report.success ? 'PASS' : 'FAIL'} apocalypse integration; result: ${reportPath}`);
    console.log(`Server log: ${paperLog}\nBuild log: ${buildLog}`);
    if (report.error) console.error(report.error);
    if (serverErrors.length) console.error(`Server errors (including shutdown):\n${serverErrors.join('\n')}`);
    if (report.cleanupError) console.error(report.cleanupError);
  }
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
