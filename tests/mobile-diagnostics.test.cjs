'use strict';
// Phone diagnostics (site/jaspercraft-mobile-diagnostics.js) run in a VM with a fake phone, clock and network: the
// session, periodic perf and controls reports, a 17.8 s stall with the browser's frame attribution (the freeze a
// phone player hit on 2026-09-29), control anomalies (stuck key, follow cancel), the rate and size bounds, and no
// personal fields. The touch controls expose the counters, the stuck-key watchdog and the follow hand-back.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'site', 'jaspercraft-mobile-diagnostics.js'), 'utf8');

function phone({mobile = true, observer = 'long-animation-frame'} = {}) {
  let clock = 0, timers = [], nextId = 1, observed = null;
  const sent = [], listeners = {};
  const anomalies = [];
  const controls = {taps: 3, canvasTouches: 9, lookMoves: 4, stickTouches: 2, stickMs: 5400, buttons: {jump: 2, strike: 1}, cancels: 0, lostCaptures: 0,
    stuckReleases: 0, followCancels: 0, maxHoldMs: 3100, maxHoldKey: 'forward', heldNow: ['forward'], activeTouches: 1, last: 'stick', sinceLastMs: 120};
  const schedule = (fn, ms, repeat) => { const id = nextId++; timers.push({id, at: clock + ms, fn, repeat: repeat ? ms : 0}); return id; };
  const cancel = id => { timers = timers.filter(t => t.id !== id); };
  const window = {
    innerWidth: 852, innerHeight: 370, devicePixelRatio: 3,
    matchMedia: () => ({matches: mobile}),
    addEventListener: (type, fn) => { (listeners[type] = listeners[type] || []).push(fn); },
    JasprVideoMobileBridge: {state: () => ({playing: true, menu: false, enabled: true}), tank: () => ({supported: true, active: true, mode: 'sentinel', follow: true, pickup: false})},
    JasprVideoDiagnostics: {status: () => ({name: 'Hyper', health: {frames: clock / 33, activeMs: clock}, values: {maxFps: 30, renderDistance: 3, resolution: 70}, scaler: {effectiveResolution: 70}})},
    JasprMobile: {stats: (reset, after) => Object.assign({}, controls, {anomalies: anomalies.filter(a => a.id > (after | 0))})},
  };
  const ctx = {
    window, screen: {orientation: {angle: 90}},
    navigator: {maxTouchPoints: mobile ? 5 : 0, userAgent: mobile ? 'Mozilla/5.0 (Linux; Android 14) Chrome/140.0 Mobile' : 'Mozilla/5.0 (Windows NT 10.0) Chrome/140.0',
      hardwareConcurrency: 4, deviceMemory: 4, connection: {effectiveType: '4g', rtt: 150, downlink: 3.2, saveData: false}},
    location: {pathname: '/jaspercraft/client.html'},
    document: {hidden: false, addEventListener: (type, fn) => { (listeners[type] = listeners[type] || []).push(fn); }},
    performance: {now: () => clock, memory: {usedJSHeapSize: 300 * 1048576, jsHeapSizeLimit: 1024 * 1048576}},
    PerformanceObserver: Object.assign(function (cb) { this.observe = () => { observed = cb; }; }, {supportedEntryTypes: observer ? [observer] : []}),
    fetch: (url, init) => { sent.push({url, init, body: JSON.parse(init.body)}); return Promise.resolve({}); },
    setTimeout: (fn, ms) => schedule(fn, ms, false), clearTimeout: cancel,
    setInterval: (fn, ms) => schedule(fn, ms, true), clearInterval: cancel,
    Date, Math, JSON, String, Object, Array, Promise,
  };
  Object.assign(ctx, {innerWidth: 852, innerHeight: 370, devicePixelRatio: 3, matchMedia: window.matchMedia});
  vm.createContext(ctx);
  // the script reads browser globals directly and through window
  ctx.window = new Proxy(window, {get: (t, k) => (k in t ? t[k] : ctx[k])});
  vm.runInContext(source, ctx);
  const api = {
    sent, anomalies, controls, listeners,
    events: () => sent.flatMap(r => r.body.events),
    advance(ms) {
      const end = clock + ms;
      for (;;) {
        timers.sort((a, b) => a.at - b.at);
        const t = timers[0];
        if (!t || t.at > end) break;
        clock = Math.max(clock, t.at); // an overdue timer runs late, as in a browser
        if (t.repeat) t.at = clock + t.repeat; else timers.shift();
        t.fn();
      }
      clock = end;
    },
    freeze(ms) { clock += ms; }, // the main thread is blocked: no timer runs until it ends
    frame(entry) { observed({getEntries: () => [entry]}); },
    get observing() { return !!observed; },
    get now() { return clock; },
  };
  return api;
}

const PERSONAL = /"(name|username|player|x|y|z|ip|address|token|password|cookie|chat|text|message|userAgent)"\s*:/;

test('desktop browsers send nothing', () => {
  const p = phone({mobile: false});
  p.advance(120000);
  assert.equal(p.sent.length, 0);
});

test('session, then perf and controls every 30 s, bounded and without personal fields', () => {
  const p = phone();
  p.advance(5000);
  assert.deepEqual(p.events().map(e => e.event), ['jaspercraft.mobile.session']);
  const session = p.events()[0].details;
  assert.equal(session.os, 'android');
  assert.equal(session.observer, 'loaf');
  p.frame({duration: 180, startTime: p.now - 180, renderStart: p.now - 40, blockingDuration: 130, scripts: [{duration: 120, invokerType: 'user-callback', invoker: 'FrameRequestCallback', sourceFunctionName: 'C88', sourceURL: 'https://jaspr.chat/jaspercraft/classes.js?v=1', sourceCharPosition: 4242}]});
  p.advance(25000);
  const names = p.events().map(e => e.event);
  assert.deepEqual(names, ['jaspercraft.mobile.session', 'jaspercraft.mobile.perf', 'jaspercraft.mobile.controls']);
  assert.equal(p.sent.length, 2, 'one request per batch');
  const perf = p.events()[1].details, controls = p.events()[2].details;
  assert.equal(perf.long.over100, 1);
  assert.equal(perf.worstFrameMs, 180);
  assert.equal(perf.settings.renderDistance, 3);
  assert.equal(perf.heap.usedMb, 300);
  assert.equal(controls.stickMs, 5400);
  assert.deepEqual(controls.heldNow, ['forward']);
  assert.equal(controls.tank.follow, true);
  for (const e of p.events()) {
    const json = JSON.stringify(e.details);
    assert.ok(json.length <= 1900, e.event + ' fits the gateway limit');
    assert.doesNotMatch(json, PERSONAL, e.event + ' carries no personal fields');
    assert.equal(e.pageSessionId, p.events()[0].pageSessionId);
  }
  assert.equal(p.sent[0].url, '/api/diagnostics/events');
  assert.equal(p.sent[0].init.credentials, 'same-origin');
});

test('a 17.8 s freeze is reported once with the frame attribution and what the player was doing', () => {
  const p = phone();
  p.advance(6000);
  p.freeze(17837);
  p.frame({duration: 17837, startTime: p.now - 17837, renderStart: p.now - 300, blockingDuration: 17787,
    scripts: [{duration: 17400, invokerType: 'event-listener', invoker: 'WebSocket.onmessage', sourceFunctionName: 'Gm2', sourceURL: 'https://jaspr.chat/jaspercraft/classes.js?v=20260929-mobile1', sourceCharPosition: 1234567}]});
  p.frame({duration: 2500, startTime: p.now - 2500, scripts: []});
  const stalls = p.events().filter(e => e.event === 'jaspercraft.mobile.stall');
  assert.equal(stalls.length, 1, 'a second stall within 10 s is counted, not sent');
  const d = stalls[0].details;
  assert.equal(d.ms, 17837);
  assert.equal(d.how, 'frame');
  assert.equal(d.scriptMs, 17400);
  assert.deepEqual(d.scripts[0], {ms: 17400, type: 'event-listener', invoker: 'WebSocket.onmessage', fn: 'Gm2', file: 'classes.js', pos: 1234567, layoutMs: 0});
  assert.equal(d.context.tank.follow, true);
  assert.equal(d.context.lastControl, 'stick');
  p.advance(30000);
  const perf = p.events().filter(e => e.event === 'jaspercraft.mobile.perf').pop().details;
  assert.equal(perf.stalls, 2);
  assert.equal(perf.long.over1000, 2);
});

test('without frame attribution the heartbeat catches the freeze', () => {
  const p = phone({observer: null});
  p.advance(6000);
  p.freeze(5000);
  p.advance(1000);
  const stall = p.events().find(e => e.event === 'jaspercraft.mobile.stall');
  assert.ok(stall && stall.details.how === 'heartbeat' && stall.details.ms >= 4000);
});

test('control anomalies are sent once each, and every report is capped per page', () => {
  const p = phone();
  p.anomalies.push({id: 1, kind: 'stuck-release', at: 1000, keys: ['forward', 'sprint']}, {id: 2, kind: 'follow-cancel', at: 1500, source: 'stick'});
  p.advance(3000);
  const sent = p.events().filter(e => e.event === 'jaspercraft.mobile.control_anomaly');
  assert.deepEqual(sent.map(e => e.details.kind), ['stuck-release', 'follow-cancel']);
  assert.deepEqual(sent[0].details.keys, ['forward', 'sprint']);
  assert.equal(sent[1].details.source, 'stick');
  p.advance(10 * 3600 * 1000);
  const periodic = p.events().filter(e => e.event === 'jaspercraft.mobile.perf').length;
  assert.equal(periodic, 240, 'at most 240 periodic batches per page');
  assert.equal(p.events().filter(e => e.event === 'jaspercraft.mobile.control_anomaly').length, 2);
});

test('touch controls: counters, stuck-key watchdog and the sentinel follow hand-back', () => {
  const js = fs.readFileSync(path.join(root, 'site', 'jaspercraft-mobile-controls.js'), 'utf8');
  new vm.Script(js);
  assert.match(js, /function watchdog\(\)\{if\(!touchSeen\|\|!held\.size\|\|fingers>0\)/, 'watchdog needs held keys and no finger on the screen');
  assert.match(js, /now-stuckSince<2000/, 'two seconds with no finger');
  assert.match(js, /anomaly\('stuck-release'/);
  assert.match(js, /syncTank\(s\);watchdog\(\);/, 'runs on every engine sync');
  assert.match(js, /takeControl\('stick'\)/, 'the stick hands control back');
  assert.match(js, /if\(zone==='jump'\|\|zone==='sneak'\)takeControl\(zone\)/, 'so do Up and Down');
  assert.match(js, /bridge\(\)\.text\('\/tank unlock '\+source,true\)/);
  assert.match(js, /stats:function\(reset,after\)/);
  const html = fs.readFileSync(path.join(root, 'site', 'client.html'), 'utf8');
  assert.match(html, /jaspercraft-mobile-diagnostics\.js\?build=20260929-mobile1/);
  assert.match(html, /jaspercraft-mobile-controls\.js\?build=20260929-mobile1/);
  const tank = fs.readFileSync(path.join(root, 'site', 'classes.js'), 'latin1');
  assert.ok(tank.includes('else if(n==="follow")cache.follow=(s.jk|0)===1;'), 'the client reads the follow score');
});
