'use strict';
// Better Combat client head (bettercombat9): the dual-wield click decisions, the server verdicts and the combat feed,
// offline (tests/bettercombat-stubs.cjs). Run alone: node --test --test-concurrency=1 tests/bettercombat-client.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const { world, stack, entity, connect, tick, rightPress, rightHeld, left, aim, events, CFG } = require('./bettercombat-stubs.cjs');

test('the combat feed: contract shape, bounded to 64, since(seq)', () => {
  const w = world(); connect(w);
  const zombie = entity('mob');
  for (let i = 0; i < 70; i++) { tick(w); aim(w, zombie); assert.equal(left(w), true); }
  const feed = w.ctx.JasprCombatFeed;
  assert.equal(feed.events.length, 64);
  assert.equal(feed.seq, 70);
  assert.equal(feed.events[0].seq, 7, 'oldest first, the first six dropped');
  assert.deepEqual(Array.from(feed.since(68), e => e.seq), [69, 70]);
  assert.deepEqual(feed.since(-5).length, 64);
  const e = feed.events[63];
  assert.deepEqual(Object.keys(e).sort(), ['at', 'crit', 'fatal', 'hand', 'item', 'kind', 'pair', 'ref', 'seq', 'sweep', 'target', 'tick'].sort());
  assert.equal(e.kind, 'swing'); assert.equal(e.hand, 'M'); assert.equal(e.item, 276); assert.equal(e.target, zombie.cu);
  assert.deepEqual(JSON.parse(JSON.stringify(e.pair)), { main: 276, off: 267 });
  assert.equal(e.crit, false); assert.equal(e.sweep, false); assert.equal(e.fatal, false); assert.equal(e.ref, -1);
  assert.equal(typeof e.at, 'number');
  // an empty hand is -1 in the feed (the engine's 0 is air), the melee module still gets the engine id for fists
  w.player.main = w.ctx.Ktg; tick(w); w.log.length = 0;
  assert.equal(left(w), true);
  const fist = feed.events[63];
  assert.equal(fist.item, -1); assert.equal(fist.pair.main, -1); assert.equal(fist.pair.off, 267);
  assert.deepEqual(w.log.find(x => x[0] === 'mark').slice(0, 3), ['mark', false, 0]);
});

test('no interception before the handshake; protocol 2 after cfg + state; legacy without cfg', () => {
  const w = world();
  aim(w, entity('mob'));
  assert.equal(left(w), false, 'nothing until the server answered');
  connect(w);
  tick(w); assert.equal(left(w), true);
  assert.deepEqual(w.sent, ['attack M 1'], 'protocol 2: sequence numbers');
  const l = world(); connect(l, {}, true); aim(l, entity('mob'));
  tick(l); assert.equal(left(l), true);
  assert.deepEqual(l.sent, ['attack M'], 'an older server gets the old message');
  assert.equal(l.ctx.JasprBetterCombat.status().pending, 0, 'no verdict is awaited from an older server');
});

test('main hand: hit, air miss and mining keep their original behaviour; the held slot is synced before the attack', () => {
  const w = world(); connect(w);
  const zombie = entity('mob');
  tick(w); aim(w, zombie); w.log.length = 0;
  assert.equal(left(w), true);
  assert.deepEqual(w.log.map(x => x[0]), ['syncHeldItem', 'send', 'resetCooldown', 'mark']);
  assert.equal(w.player.G1, 1); assert.equal(w.player.crX, 'MAIN');
  tick(w); aim(w, null); w.log.length = 0; w.player.bDd = 40;
  assert.equal(left(w), true, 'a miss in the air is still Better Combat (refund, swing)');
  assert.deepEqual(w.log.map(x => x[0]), ['syncHeldItem', 'send', 'swingArm', 'mark']);
  assert.equal(w.player.bDd, 40, 'refund on miss: the energy stays');
  tick(w); aim(w, null, true);
  assert.equal(left(w), false, 'a block under the crosshair and nothing to hit: vanilla mining');
});

test('BUG: an off-hand click with the ground or a wall under the crosshair behind the target attacks (it used block use)', () => {
  const w = world(); connect(w);
  const zombie = entity('mob');
  tick(w, 1); aim(w, zombie, true); w.log.length = 0;
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.sent, ['attack O 1']);
  assert.deepEqual(w.log.map(x => x[0] + (x[1] ? ':' + x[1] : '')).filter(x => x !== 'mark:true'), ['use:MAIN', 'syncHeldItem', 'send:attack O 1']);
  assert.equal(w.player.G1, 1); assert.equal(w.player.crX, 'OFF');
  assert.equal(w.mc.bTh, 4, 'the right-click delay of vanilla');
  const swing = events(w, 'swing')[0];
  assert.equal(swing.hand, 'O'); assert.equal(swing.item, 267); assert.equal(swing.target, zombie.cu);
});

test('the main hand keeps its right click: food, shields, bows, block placing, entity interactions go first', () => {
  const w = world(); connect(w);
  tick(w, 1); aim(w, entity('mob')); w.ctx.$use = { MAIN: 1 }; w.log.length = 0;
  assert.equal(rightPress(w), true, 'consumed by the vanilla main-hand use');
  assert.deepEqual(w.sent, []);
  assert.deepEqual(w.log.map(x => x[0]), ['use']);
  assert.equal(events(w).length, 0, 'not a combat event');
});

test('a block and nothing to hit: the off hand keeps its own vanilla use (hoe tilling, shovel paths)', () => {
  const w = world(); connect(w);
  tick(w, 1); aim(w, null, true); w.log.length = 0;
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.log, [['use', 'MAIN'], ['use', 'OFF']]);
  assert.deepEqual(w.sent, []);
});

test('BUG: villagers, horses, armor stands and the player\'s own pet keep their interaction (no dead off-hand swing)', () => {
  const w = world(); connect(w);
  for (const typeId of [120, 100, 30]) {
    tick(w, 1); aim(w, entity('mob', { typeId })); w.log.length = 0;
    assert.equal(rightPress(w), true);
    assert.deepEqual(w.log, [['use', 'MAIN'], ['use', 'OFF']], 'type ' + typeId);
  }
  const pet = entity('pet', { typeId: 95, owner: w.player });
  tick(w, 1); aim(w, pet); w.log.length = 0;
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.log, [['use', 'MAIN'], ['use', 'OFF']], 'own wolf');
  const stray = entity('pet', { typeId: 95, owner: {} });
  tick(w, 1); aim(w, stray);
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.sent, ['attack O 1'], 'someone else\'s wolf is a target as before');
  // players: attackable unless the server's blacklist covers them
  tick(w, 1); aim(w, entity('player', { typeId: -1 }));
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.sent, ['attack O 1', 'attack O 2']);
  w.ctx.JasprBetterCombat.receive(JSON.stringify({ v: 1, cfg: Object.assign({}, CFG.cfg, { players: true }) }));
  tick(w, 1); aim(w, entity('player', { typeId: -1 })); w.log.length = 0;
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.log, [['use', 'MAIN'], ['use', 'OFF']]);
});

test('BUG: holding the use button (or the phone\'s Use button) attacks once, its repeats stay vanilla', () => {
  const w = world(); connect(w);
  const zombie = entity('mob');
  tick(w, 1); aim(w, zombie);
  assert.equal(rightPress(w), true);
  for (let i = 0; i < 8; i++) { tick(w); if (i % 4 === 3) assert.equal(rightHeld(w), false, 'repeat ' + i); }
  assert.deepEqual(w.sent, ['attack O 1']);
  tick(w, 1);
  assert.equal(rightPress(w), true, 'a new press attacks again');
  assert.deepEqual(w.sent, ['attack O 1', 'attack O 2']);
  // two presses in one tick: both are presses
  tick(w, 2); assert.equal(rightPress(w), true); assert.equal(rightPress(w), true);
  assert.deepEqual(events(w, 'denied').map(e => e.hand), ['O'], 'but one attack per hand and tick (the server\'s rule)');
});

test('BUG: right after a hotbar switch or F the click uses the item in hand now (no round trip, no dead click)', () => {
  const w = world(); connect(w, { offItem: 276, off: true, mainItem: 267 });   // the server last saw other items
  w.player.off = stack(258);                                                   // axe in the off hand now
  tick(w, 1); aim(w, entity('mob'));
  assert.equal(rightPress(w), true);
  assert.deepEqual(w.sent, ['attack O 1']);
  assert.equal(events(w, 'swing')[0].item, 258);
  assert.deepEqual(JSON.parse(JSON.stringify(events(w, 'swing')[0].pair)), { main: 276, off: 258 });
  w.player.off = stack(364);                                                   // steak: not a weapon
  tick(w, 1);
  assert.equal(rightPress(w), false, 'not a weapon: vanilla right click');
  // an older server: its item ids still gate (the old behaviour)
  const l = world(); connect(l, { offItem: 276 }, true); l.player.off = stack(258);
  tick(l, 1); aim(l, entity('mob'));
  assert.equal(rightPress(l), false);
});

test('guns, gadgets and portal devices keep all their clicks; expedition melee weapons stay weapons', () => {
  const w = world(); connect(w);
  aim(w, entity('mob'));
  w.player.main = stack(293, { JasprApocalypse: {} });
  tick(w, 1); assert.equal(left(w), false); assert.equal(rightPress(w), false);
  w.player.main = stack(276, { JasprApocalypse: {} });
  tick(w, 1); assert.equal(left(w), true);
  w.player.off = stack(267, { JasprGearIcon: 1 });
  tick(w, 1); assert.equal(rightPress(w), false, 'a gear item in the off hand is no weapon');
  w.player.off = stack(267, { JasprPortalGun: 1 });
  tick(w, 1); assert.equal(rightPress(w), false);
  w.player.off = stack(267, { display: { Name: 'named' }, Unbreakable: 1 });
  tick(w, 1); assert.equal(rightPress(w), true, 'named / unbreakable swords are weapons');
});

test('spectators, open screens, rowing and block breaking stay vanilla', () => {
  const w = world(); connect(w); aim(w, entity('mob'));
  w.mc.dw.jC = 'SPECTATOR'; tick(w, 1);
  assert.equal(left(w), false); assert.equal(rightPress(w), false);
  w.mc.dw.jC = 'SURVIVAL'; w.mc.cj = {}; tick(w, 1); assert.equal(left(w), false);
  w.mc.cj = null; w.player.bBT = 1; tick(w); assert.equal(left(w), false);
  w.player.bBT = 0; w.mc.dw.NS = 1; tick(w, 1); assert.equal(rightPress(w), false);
  assert.deepEqual(w.sent, []);
});

test('full-energy gate: a recharging click is consumed and fed as denied, nothing sent', () => {
  const w = world(); connect(w, { full: true });
  aim(w, entity('mob'));
  w.player.strength = 0.5; tick(w);
  assert.equal(left(w), true);
  w.ctx.JasprBetterCombat.receive(JSON.stringify(Object.assign({}, require('./bettercombat-stubs.cjs').SETTINGS, { full: true, remaining: 6 })));
  tick(w, 1); assert.equal(rightPress(w), true);
  assert.deepEqual(w.sent, []);
  assert.deepEqual(events(w).map(e => e.kind + e.hand), ['deniedM', 'deniedO']);
});

test('server verdicts: hit / blocked / miss / denied are fed with the server\'s flags and a link to the swing', () => {
  const w = world(); connect(w);
  const zombie = entity('mob');
  tick(w); aim(w, zombie); left(w);
  tick(w, 1); rightPress(w);
  const B = w.ctx.JasprBetterCombat;
  B.receive(JSON.stringify({ v: 1, res: { s: 1, h: 'M', k: 'hit', t: zombie.cu, i: 276, c: 1, w: 0, f: 1, e: 1 } }));
  B.receive(JSON.stringify({ v: 1, res: { s: 2, h: 'O', k: 'blocked', t: zombie.cu, i: 267, c: 0, w: 0, f: 0, e: 1 } }));
  B.receive(JSON.stringify({ v: 1, res: { s: 2, h: 'O', k: 'hit', t: zombie.cu } }));     // answered already: ignored
  B.receive(JSON.stringify({ v: 1, res: { s: 99, h: 'O', k: 'hit', t: zombie.cu } }));    // never sent: ignored
  const list = events(w);
  assert.deepEqual(list.map(e => e.kind + e.hand), ['swingM', 'swingO', 'hitM', 'blockedO']);
  assert.equal(list[2].ref, list[0].seq); assert.equal(list[3].ref, list[1].seq);
  assert.equal(list[2].crit, true); assert.equal(list[2].fatal, true); assert.equal(list[2].target, zombie.cu);
  assert.deepEqual(JSON.parse(JSON.stringify(list[3].pair)), { main: 276, off: 267 });
  assert.equal(B.status().pending, 0);
});

test('BUG: the main-hand energy follows the server (a refunded miss gives it back; an unforeseen hit spends it)', () => {
  const w = world(); connect(w);
  const B = w.ctx.JasprBetterCombat;
  tick(w); aim(w, entity('mob')); w.player.bDd = 37;
  left(w);
  assert.equal(w.player.bDd, 0, 'predicted hit: reset at the click');
  tick(w); tick(w); w.player.bDd = 2;
  B.receive(JSON.stringify({ v: 1, res: { s: 1, h: 'M', k: 'miss', t: -1, e: 0 } }));
  assert.equal(w.player.bDd, 39, 'the server found nothing and kept the energy: given back (37 + 2 ticks)');
  tick(w); aim(w, null); w.player.bDd = 25;
  left(w);
  assert.equal(w.player.bDd, 25, 'predicted miss: no reset');
  B.receive(JSON.stringify({ v: 1, res: { s: 2, h: 'M', k: 'hit', t: 7, e: 1 } }));
  assert.equal(w.player.bDd, 0, 'the server hit a target the client did not see: spent');
});

test('the off-hand recharge bar reacts at the click and follows the server', () => {
  const w = world(); connect(w);
  const B = w.ctx.JasprBetterCombat;
  tick(w, 1); aim(w, entity('mob'));
  assert.equal(B.remaining(), 0);
  rightPress(w);
  assert.equal(B.remaining(), 12, 'predicted from the period of the off-hand weapon');
  w.advance(300);
  assert.equal(B.remaining(), 6);
  B.receive(JSON.stringify({ v: 1, res: { s: 1, h: 'O', k: 'denied', t: -1, e: 0, r: 'target' } }));
  assert.equal(B.remaining(), 0, 'not spent: the prediction is dropped');
  assert.equal(events(w, 'denied')[0].ref, events(w, 'swing')[0].seq);
});

test('the off-hand bar follows the item in the off hand (per tick), not the last server packet', () => {
  const w = world(); connect(w, { off: false, offItem: 0, remaining: 6 });
  w.player.strength = 1;
  tick(w);
  w.log.length = 0;
  assert.equal(w.ctx.JasprCombatIndicators({ ds: w.mc }, 0, { f7: 480, ff: 270 }), true);
  assert.deepEqual(w.log.map(x => x.slice(1)), [[248, 144, 36, 94, 16, 4], [248, 144, 52, 94, 8, 4]], 'off bar from the sword in hand');
  w.player.off = stack(364); tick(w); w.log.length = 0;
  w.ctx.JasprCombatIndicators({ ds: w.mc }, 0, { f7: 480, ff: 270 });
  assert.deepEqual(w.log, [], 'food in the off hand: no off bar');
  const l = world(); connect(l, { off: false, remaining: 6 }, true); tick(l); l.log.length = 0;
  l.ctx.JasprCombatIndicators({ ds: l.mc }, 0, { f7: 480, ff: 270 });
  assert.deepEqual(l.log, [], 'an older server: its flag as before');
});

test('BUG: a long server silence no longer switches combat off after 3 s; the client says hello again after 5 s', () => {
  const w = world(); connect(w);
  aim(w, entity('mob'));
  w.advance(4000); tick(w);
  assert.equal(left(w), true, 'still active after 4 s of silence (was 3 s)');
  w.advance(1500); w.sent.length = 0; tick(w);
  assert.deepEqual(w.sent, ['hello 1'], 'asks again after 5 s');
  w.advance(1000); w.sent.length = 0; tick(w);
  assert.deepEqual(w.sent, [], 'not more than every 5 s');
  w.advance(10000); tick(w);
  assert.equal(w.ctx.JasprBetterCombat.active(w.mc), false, 'gone after 15 s');
  connect(w); assert.equal(w.ctx.JasprBetterCombat.active(w.mc), true);
  // a new connection resets everything
  w.player.d_ = { qf: { wd() {}, bkf: 0 } }; tick(w);
  assert.equal(w.ctx.JasprBetterCombat.active(w.mc), false);
});

test('results of a dead connection are dropped after 4 s and never fed', () => {
  const w = world(); connect(w);
  tick(w); aim(w, entity('mob')); left(w);
  assert.equal(w.ctx.JasprBetterCombat.status().pending, 1);
  w.advance(4100); tick(w);
  assert.equal(w.ctx.JasprBetterCombat.status().pending, 0);
  w.ctx.JasprBetterCombat.receive(JSON.stringify({ v: 1, res: { s: 1, h: 'M', k: 'hit', t: 1, e: 1 } }));
  assert.equal(events(w, 'hit').length, 0);
});

test('malformed packets are ignored; settings bounds still apply', () => {
  const w = world(); connect(w);
  const B = w.ctx.JasprBetterCombat;
  for (const bad of ['', '{', '[]', '{"v":2,"res":{"s":1}}', '{"v":1,"res":"x"}', '{"v":1,"cfg":{"proto":"2"}}', '{"v":1,"range":99,"width":1,"remaining":0,"period":1}'])
    B.receive(bad);
  assert.equal(B.settings().range, 5);
  assert.equal(B.status().proto, 2);
});

test('a throwing melee module never breaks a click', () => {
  const w = world(); connect(w);
  w.ctx.JasprMeleeMotion = { mark() { throw Error('boom'); }, clear() {} };
  tick(w); aim(w, entity('mob'));
  assert.equal(left(w), true);
  assert.equal(w.ctx.JasprBetterCombat.status().stats.errors, 1);
  assert.equal(w.ctx.JasprBetterCombat.status().lastError, 'boom');
});

test('compatibility: markSwing(off) / motion(main) / blocks / empty / special keep their old meaning', () => {
  const w = world(); connect(w);
  const B = w.ctx.JasprBetterCombat;
  B.markSwing(true);
  assert.deepEqual(w.log.at(-1).slice(0, 3), ['mark', true, 267], 'without an id: the server-side item id as before');
  assert.ok(B.motion(false)); assert.equal(B.motion(true), null);
  assert.equal(B.empty(null), true); assert.equal(B.empty(w.ctx.Ktg), true); assert.equal(B.empty(stack(1)), false);
  w.mc.h4 = { ks: null, mh: {}, kE: { d: 1 } }; assert.ok(B.blocks(w.mc));
  for (const key of ['JasprGear', 'JasprGearUse', 'JasprGearPack', 'JasprGearIcon', 'JasprPortalGun'])
    assert.equal(w.ctx.JasprCombatSpecial(stack(267, { [key]: 1 })), true, key);
  assert.equal(w.ctx.JasprCombatSpecial(stack(293, { JasprApocalypse: {} })), true);
  assert.equal(w.ctx.JasprCombatSpecial(stack(276, { JasprApocalypse: {} })), false);
  assert.equal(w.ctx.JasprCombatSpecial(stack(276)), false);
});
