'use strict';

// Disposable CDP helper for the local renderer fixture. It never attaches to
// the user's normal browser profile and never changes a production file.
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

const url = process.argv[2] || 'http://127.0.0.1:56697/';
const debugPort = Number(process.argv[3] || 9223);
const settleMs = Number(process.argv[4] || 2500);
const browser = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-cdp-probe-'));
const child = spawn(browser, [
  '--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--window-size=756,425', '--remote-debugging-port=' + debugPort,
  '--user-data-dir=' + profile, url
], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });

function getJson(pathname) {
  return new Promise((resolve, reject) => {
    const req = http.get({ host: '127.0.0.1', port: debugPort, path: pathname }, response => {
      let data = '';
      response.setEncoding('utf8');
      response.on('data', chunk => data += chunk);
      response.on('end', () => { try { resolve(JSON.parse(data)); } catch (error) { reject(error); } });
    });
    req.on('error', reject);
  });
}

async function waitTarget() {
  for (let i = 0; i < 80; i++) {
    try {
      const targets = await getJson('/json/list');
      const page = targets.find(target => target.type === 'page');
      if (page) return page;
    } catch (_) { }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('CDP target did not start');
}

function cdp(wsUrl) {
  const socket = new WebSocket(wsUrl);
  let sequence = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const resolve = pending.get(message.id); pending.delete(message.id); resolve(message);
    }
  });
  const open = new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  return {
    async call(method, params) {
      await open;
      const id = ++sequence;
      socket.send(JSON.stringify({ id, method, params: params || {} }));
      return new Promise(resolve => pending.set(id, resolve));
    },
    close() { try { socket.close(); } catch (_) { } }
  };
}

function removeProfile() {
  for (let i = 0; i < 8; i++) {
    try { fs.rmSync(profile, { recursive: true, force: true }); return; }
    catch (error) { if (error.code !== 'EPERM' && error.code !== 'EBUSY') throw error; }
  }
}

(async () => {
  const target = await waitTarget();
  const session = cdp(target.webSocketDebuggerUrl);
  await session.call('Runtime.enable');
  await new Promise(resolve => setTimeout(resolve, settleMs));
  const state = await session.call('Runtime.evaluate', { expression: `JSON.stringify({
    title: document.title,
    status: document.querySelector('#status') && document.querySelector('#status').textContent,
    report: document.querySelector('#report') && document.querySelector('#report').textContent,
    body: document.body.innerText,
    canvas: Array.from(document.querySelectorAll('canvas')).map(c => [c.width, c.height])
  })`, returnByValue: true });
  const shot = await session.call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  process.stdout.write('STATE=' + (state.result && state.result.result && state.result.result.value || '') + '\n');
  process.stdout.write('SCREENSHOT_BASE64=' + (shot.result && shot.result.data || '') + '\n');
  await session.call('Browser.close');
  session.close();
  removeProfile();
})().catch(error => { process.stderr.write((error.stack || String(error)) + '\n'); try { child.kill(); } catch (_) { } removeProfile(); process.exitCode = 1; });
