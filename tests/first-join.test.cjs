'use strict';
// First join: accounts with no saved JasperCraft data open Edit Profile ("create a character") before joining,
// instead of auto-joining in the middle of the browser's heavy first load (which timed out the handshake).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const profile = require('../site/jaspr-profile.js');

test('first run: only accounts that have never reached the world', () => {
  const p = profile.key, joined = profile.joinedKey;
  assert.equal(profile.firstRun({initialized: true, values: {}}), true, 'brand-new account');
  assert.equal(profile.firstRun({initialized: false, values: {}}), true, 'settings row but no character yet');
  assert.equal(profile.firstRun({initialized: false, values: {[p]: 'x', [profile.namespace + '.g']: 'y'}}), false, 'legacy player with a saved character');
  assert.equal(profile.firstRun({initialized: true, values: {[joined]: '1'}}), false, 'joined marker wins');
  assert.equal(profile.firstRun({initialized: false, values: {[p]: 'x', [joined]: '1'}}), false);
  assert.equal(profile.firstRun(null), true, 'no data at all');
  assert.match(joined, /^_eaglercraft_1122_tailscale_ui2\.jasprJoined$/, 'marker is an ordinary synced game setting');
});

test('client stage: Edit Profile first for first-timers, Back retries once for everyone else', () => {
  const {build} = require('../scripts/build-firstrun-client.cjs');
  const live = fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
  const staged = live.includes('JASPR_FIRSTRUN_V1') ? live : build(live);
  assert.equal(staged.split('/*JASPR_FIRSTRUN_V1*/').length - 1, 4);
  assert.equal(staged.split('/* JASPR_FIRSTRUN_V1_BEGIN */').length - 1, 1);
  assert.throws(() => build(staged), /already contains/);
  const start = staged.indexOf('function FEH('), feh = staged.slice(start, staged.indexOf('\nfunction', start + 10));
  assert.ok(feh.includes('case 195:BmS(b,f,a,h,d);') && feh.includes('case 196:BOH(c,b);') && feh.includes('case 197:GGw(a,c);'),
    'first run: GuiConnecting(parent=menu) becomes Edit Profile\'s parent and Edit Profile is shown');
  assert.ok(feh.includes('BmS(g,c,a,f,d);') && feh.includes('case 198:BmS(b,g,a,f,d);'), 'returning: a retry connection sits behind the first');
  new vm.Script(staged);
});

function startClient(session) {
  const source = fs.readFileSync(path.join(root, 'site/jaspr-client.js'), 'utf8');
  const store = new Map();
  const localStorage = {
    get length() { return store.size; }, key: i => [...store.keys()][i] ?? null,
    getItem: k => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)),
    removeItem: k => store.delete(k), clear: () => store.clear(),
  };
  let mained = false;
  const window = {localStorage, addEventListener() {}, WebSocket: function () {}, fetch: () => Promise.resolve()};
  const sandbox = {window, localStorage, parent: {}, location: {href: 'https://jaspr.chat/jaspercraft/client.html', protocol: 'https:', host: 'jaspr.chat'},
    document: {addEventListener() {}, hasFocus: () => true, querySelector: () => null, hidden: false},
    URL, Proxy, Reflect, Date, Math, JSON, Object, String, Number, Boolean, Array, setTimeout: () => 0, setInterval: () => 0, clearInterval() {},
    main: () => { mained = true; }, Storage: function () {}};
  Object.assign(window, sandbox);
  vm.runInNewContext(source, sandbox);
  window.JasperCraftClient.start(Object.assign({gameName: 'anon_1082', serverAddress: 'wss://jaspr.chat/jaspercraft/socket'}, session));
  return {opts: window.eaglercraftXOpts, firstRun: window.JasprFirstRun, mained};
}

test('client start: first-timers keep the join target for Done, returning players auto-join', () => {
  const first = startClient({join: false, firstRun: true});
  assert.equal(first.mained, true);
  assert.equal(first.firstRun, true);
  assert.equal(first.opts.joinServer, 'wss://jaspr.chat/jaspercraft/socket');
  const back = startClient({join: true, firstRun: false});
  assert.equal(back.firstRun, false);
  assert.equal(back.opts.joinServer, 'wss://jaspr.chat/jaspercraft/socket');
  const sso = fs.readFileSync(path.join(root, 'site/jaspr-sso.js'), 'utf8');
  assert.match(sso, /var firstRun = JasprProfile\.firstRun\(synced\);/);
  assert.match(sso, /join: !firstRun, firstRun: firstRun/);
});
