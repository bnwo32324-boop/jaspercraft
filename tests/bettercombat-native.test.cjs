'use strict';
// The Better Combat head against the REAL client (LIVE classes.js, read only):
// 1. every engine name the adapter source uses exists in the client (a typo would only fail in a browser), and
// 2. with the built client loaded offline (scripts/mobends-native-harness.cjs, one load, no game started), the engine
//    classes and enum constants the head's decisions rest on are what the head assumes.
// Only calls that are safe without a running game are made (class initialisers of small enums, instanceof).
// Run alone: node --test --test-concurrency=1 tests/bettercombat-native.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const B = require('../scripts/build-bettercombat-client.cjs');
const LIVE = process.env.BETTERCOMBAT_CLASSES_JS || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const live = fs.existsSync(LIVE);
const KEYWORDS = new Set(('break case catch continue default delete do else false finally for function if in instanceof new null return switch this ' +
  'throw true try typeof var void while with undefined NaN JSON Math Date Number Object Array String Error').split(' '));

// identifiers of the adapter that look like TeaVM engine names: short, not a property access, not a local
function engineNames(source) {
  const code = source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ').replace(/'(?:\\.|[^'\\])*'/g, "''");
  const names = new Set();
  for (const m of code.matchAll(/(^|[^.\w$])([A-Za-z_$][\w$]*)(?![\w$])(?!\s*:(?!:))/g)) {
    const n = m[2];
    if (n.length > 4 || KEYWORDS.has(n) || n === '_' || /^[a-z]$/.test(n) || /^\$[a-zA-Z]$/.test(n)) continue;   // label _, locals a..z, $p $z $T
    if (/^[a-z][a-z]+$/.test(n)) continue;          // short lowercase words are locals / keys here (e.g. off, ids, now)
    names.add(n);
  }
  return [...names].sort();
}
function defined(bundle, n) {
  const e = n.replace(/\$/g, '\\$');
  return new RegExp('(?:function ' + e + '\\(|(?:var |[,;{])\\s*' + e + '\\s*=|\\[' + e + ',"|[,\\[]' + e + ',"[A-Z])').test(bundle);
}

test('every engine name the adapter uses exists in the live client', { skip: !live }, () => {
  const bundle = fs.readFileSync(LIVE, 'latin1');
  const names = engineNames(B.adapterText());
  for (const must of ['EZ5', 'EjD', 'C_q', 'CjH', 'GfQ', 'Fht', 'HFd', 'HFf', 'HNb', 'KsX', 'S5', 'Cb', 'F78', 'GgT', 'I$', 'Gi', 'BW', 'Ktg', 'HEH'])
    assert.ok(names.includes(must), 'scanner sees ' + must);
  const missing = names.filter(n => !defined(bundle, n));
  assert.deepEqual(missing, [], 'names the client does not define');
});

test('the scanner catches an undefined engine name', { skip: !live }, () => {
  const bundle = fs.readFileSync(LIVE, 'latin1');
  assert.equal(defined(bundle, 'Zq9W'), false);
  const names = engineNames(B.adapterText().replace('$z=CjH(f,e);', '$z=Zq9W(f,e);'));
  assert.deepEqual(names.filter(n => !defined(bundle, n)), ['Zq9W']);
});

test('real engine: the classes and enum constants the head relies on', { skip: !live, timeout: 120000 }, () => {
  const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');
  const { fn } = loadClientSource(B.build(fs.readFileSync(LIVE, 'latin1')), { filename: 'bettercombat-candidate.js' });
  const make = cls => Object.create(cls.prototype);
  // own-pet check (EntityTameable) and player check (EntityPlayer covers other players and the local one)
  assert.ok(make(fn.KF) instanceof fn.S5, 'a wolf is an EntityTameable');
  assert.ok(!(make(fn.Iw) instanceof fn.S5), 'a zombie is not');
  assert.ok(make(fn.B98) instanceof fn.Cb && make(fn.BpL) instanceof fn.Cb, 'other players and the local player are EntityPlayer');
  assert.ok(!(make(fn.IW) instanceof fn.Cb), 'a villager is not');
  // RayTraceResult.Type switch map: MISS -> default, BLOCK -> 2, ENTITY -> 1 (as rightClickMouse uses it)
  fn.EI1(); assert.deepEqual(Array.from(fn.HFe.data), [3, 2, 1]);
  // EnumHand: values() = [MAIN_HAND, OFF_HAND]; CjH's off-hand constant is the second
  fn.Hc(); assert.equal(fn.HFf.data[0], fn.HFd); assert.equal(fn.HFf.data[1], fn.Kt_);
  // GameType: HNb is the fifth constant, SPECTATOR (NOT_SET, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR)
  fn.Fz(); assert.equal(fn.HNc.data[4], fn.HNb); assert.equal(fn.HNc.data.length, 5);
  // EnumActionResult: HFg is SUCCESS, the first of SUCCESS / PASS / FAIL
  fn.BT(); assert.equal(fn.Ly6.data[0], fn.HFg);
  // the head itself in the real bundle: the feed, the protocol-2 handshake, plain JS only
  assert.equal(fn.JasprCombatFeed.seq, 0);
  const net = { wd() {}, bkf: 0 }, mc = { v: { d_: { qf: net }, cv: 1 }, X: {} };
  fn.JasprBetterCombat.outgoing(mc);
  fn.JasprBetterCombat.receive(JSON.stringify({ v: 1, cfg: { proto: 2, offIds: [267], noAttack: [120], players: false } }));
  fn.JasprBetterCombat.receive(JSON.stringify({ v: 1, enabled: true, proto: 2, range: 5, width: 1.5, refund: true, full: false, dual: true, motion: true,
    off: true, main: true, use: false, mainItem: 276, offItem: 267, remaining: 0, period: 12, offSwing: 0 }));
  assert.equal(fn.JasprBetterCombat.active(mc), true);
  assert.equal(fn.JasprBetterCombat.status().proto, 2);
  assert.equal(fn.JasprBetterCombat.attackable(make(fn.IW), 120), false, 'villager type id from the server keeps its interaction');
  assert.equal(fn.JasprBetterCombat.attackable(make(fn.B98), -1), true, 'players stay targets unless blacklisted');
});
