'use strict';
// Tab-held gate runtime tests: edges, heartbeat resend, expiry, invalidation.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createJasprWaypointTab } = require('../client-mods/waypoint-tab.js');

function fixture() {
  let now = 10000, playing = true;
  const world = {}, connection = {}, handler = { qf: connection };
  const client = { X: world, v: { d_: handler }, G: {} };
  const tab = createJasprWaypointTab({ playing: () => playing, now: () => now });
  return { client, tab, world, connection, handler,
    setPlaying: (v) => { playing = v; },
    advance: (ms) => { now += ms; },
    now: () => now };
}

test('press edge queues one compass request, repeat is ignored', () => {
  const f = fixture();
  assert.equal(f.tab.edge(f.client, true, f.now()), true);
  assert.equal(f.tab.edge(f.client, true, f.now()), false, 'held repeat is not an edge');
  const req = f.tab.take(f.client);
  assert.ok(req);
  assert.equal(req.kind, 'press');
  assert.equal(f.tab.take(f.client), null, 'queue drains once');
});

test('release edge queues an instant clear', () => {
  const f = fixture();
  f.tab.edge(f.client, true, f.now());
  f.tab.take(f.client);
  assert.equal(f.tab.edge(f.client, false, f.now()), true);
  const req = f.tab.take(f.client);
  assert.ok(req);
  assert.equal(req.kind, 'release');
  assert.equal(f.tab.held(f.client), false);
});

test('heartbeat resends while held, then stops after release', () => {
  const f = fixture();
  f.tab.edge(f.client, true, f.now());
  f.tab.take(f.client);
  f.advance(1000);
  assert.equal(f.tab.take(f.client), null, 'no resend before 2500ms');
  f.advance(1600);
  const resend = f.tab.take(f.client);
  assert.ok(resend);
  assert.equal(resend.kind, 'resend');
  f.tab.edge(f.client, false, f.now());
  const release = f.tab.take(f.client);
  assert.ok(release && release.kind === 'release');
  f.advance(5000);
  assert.equal(f.tab.take(f.client), null, 'nothing after release');
});

test('stale hold expires without repeats, invalidate clears', () => {
  const f = fixture();
  f.tab.edge(f.client, true, f.now());
  assert.equal(f.tab.held(f.client), true);
  f.advance(1600);
  assert.equal(f.tab.held(f.client), false, 'hold times out without key repeats');
  f.tab.edge(f.client, true, f.now());
  f.tab.touch(f.client, f.now());
  f.advance(1400);
  assert.equal(f.tab.held(f.client), true, 'repeat refreshes the hold');
  f.tab.invalidate(f.client);
  assert.equal(f.tab.held(f.client), false);
  assert.equal(f.tab.take(f.client), null);
});

test('not playing invalidates and suppresses everything', () => {
  const f = fixture();
  f.tab.edge(f.client, true, f.now());
  f.setPlaying(false);
  assert.equal(f.tab.held(f.client), false);
  assert.equal(f.tab.take(f.client), null);
  assert.equal(f.tab.edge(f.client, true, f.now()), false);
});

test('rapid re-press is debounced', () => {
  const f = fixture();
  assert.equal(f.tab.edge(f.client, true, f.now()), true);
  f.tab.take(f.client);
  f.tab.edge(f.client, false, f.now());
  f.tab.take(f.client);
  assert.equal(f.tab.edge(f.client, true, f.now()), false, 'within 300ms debounce');
  f.advance(400);
  assert.equal(f.tab.edge(f.client, true, f.now()), true);
});

test('ready() rejects stale worlds, players and epochs', () => {
  const f = fixture();
  f.tab.edge(f.client, true, f.now());
  const req = f.tab.take(f.client);
  assert.ok(req && f.tab.ready(req));
  f.advance(1500);
  assert.equal(f.tab.ready(req), false, 'request expires after 1000ms');
  f.tab.edge(f.client, true, f.now());
  const req2 = f.tab.take(f.client);
  assert.ok(!req2, 'second press while held is not an edge');
  f.tab.edge(f.client, false, f.now());
  f.tab.take(f.client);
  f.advance(400);
  f.tab.edge(f.client, true, f.now());
  const req3 = f.tab.take(f.client);
  assert.ok(req3);
  f.tab.invalidate(f.client);
  assert.equal(f.tab.ready(req3), false, 'invalidate bumps the epoch');
});

test('status reports gate state', () => {
  const f = fixture();
  const idle = f.tab.status();
  assert.equal(idle.held, false);
  assert.equal(idle.pending, false);
  f.tab.edge(f.client, true, f.now());
  assert.equal(f.tab.status().pending, true);
  f.tab.take(f.client);
  f.tab.sent();
  assert.equal(f.tab.status().sent, 1);
});
