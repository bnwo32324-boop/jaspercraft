'use strict';
/* Wide inventory, client half: the plain-JS logic of client-mods/wide-inventory-teavm.js in a sandbox (hotbar positions,
 * which windows get the extension and in what order, pocket layout, screen width, clicks, shift-click orders identical to
 * the server's JasprWide.java), the builder on the live client (every hook, reversible, stable, parses), and the touch
 * hotbar. The server half runs in tests/wide-inventory-bot.cjs. */
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const MODULE = fs.readFileSync(path.join(ROOT, 'client-mods', 'wide-inventory-teavm.js'), 'utf8');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';

function sandbox() {
  const ctx = {$rt_globals: {location: {pathname: '/test/'}, console: {warn() {}}}, $rt_str: s => s, HEH: null};
  vm.createContext(ctx);
  vm.runInContext('function Biv(){} function YD(){} function ID(){} function Other(){}\n' + MODULE, ctx);
  return ctx;
}
/** A window: `own` container slots, then the player's 27 + 9 (at the y rows given), like every vanilla container. */
function window(ctx, own, rows = [84, 102, 120], hotbarY = 142) {
  const inv = new ctx.Biv(), other = new ctx.Other(), slots = [];
  const add = (BX, bQx, Lr, Fg) => slots.push({BX, bQx, Lr, Fg, pO: slots.length});
  for (let i = 0; i < own; i++) add(other, i, 8 + (i % 9) * 18, 18 + (i / 9 | 0) * 18);
  if (rows) for (let r = 0; r < 3; r++) for (let c = 0; c < 9; c++) add(inv, 9 + r * 9 + c, 8 + c * 18, rows[r]);
  for (let c = 0; c < 9; c++) add(inv, c, 8 + c * 18, hotbarY);
  return {inv, container: {cn: {get g() { return slots.length; }, qN: {data: slots}}}, slots};
}
/** What JasprWideAdopt does with a plan: Dk(inv, index, 0, 0), $jw, then addSlotToContainer. */
function adopt(ctx, w) {
  const plan = ctx.JasprWide.adoptPlan(w.container);
  if (!plan) return null;
  for (const i of plan.add) w.slots.push({BX: plan.inv, bQx: i, Lr: 0, Fg: 0, pO: w.slots.length, $jw: 1});
  ctx.JasprWide.adopted(w.container, plan);
  return Array.from(plan.add);
}

test('hotbar positions: 9 until the server agrees, then 14 (items 0-8 and 36-40)', () => {
  const ctx = sandbox(), W = ctx.JasprWide;
  assert.equal(W.slots(), 9);
  assert.equal(W.scroll(8, -1), 0, 'vanilla wrap');
  assert.equal(W.hotbarLeft(240, 480), 149);
  assert.equal(W.span(), 182);
  assert.equal(W.answered(null), null);
  assert.equal(W.slots(), 14);
  assert.deepEqual([0, 8, 9, 13].map(W.index), [0, 8, 36, 40]);
  assert.deepEqual([0, 8, 36, 40].map(W.pos), [0, 8, 9, 13]);
  assert.equal(W.scroll(8, -1), 36, 'wheel down from position 9 goes to position 10');
  assert.equal(W.scroll(40, -1), 0, 'and wraps after 14');
  assert.equal(W.scroll(0, 1), 40, 'wheel up from position 1 wraps to 14');
  assert.deepEqual([0, 8, 36, 40].map(W.heldWindow), [36, 44, 46, 50], 'creative middle-click window slots');
  assert.deepEqual([45, 53, 54, 58].map(W.creativeWindow), [36, 44, 46, 50], 'ContainerCreative hotbar -> window 0');
  assert.equal(W.span(), 282);
  assert.equal(W.hotbarLeft(240, 480), 99, 'centred 282px bar');
  assert.equal(W.hotbarLeft(160, 320), 30, 'narrowest screen: room for a left off hand');
  assert.equal(W.hotbarRight(480), 381);
  assert.deepEqual(Array.from(W.order()).slice(0, 16), [0, 1, 2, 3, 4, 5, 6, 7, 8, 36, 37, 38, 39, 40, 9, 10]);
  assert.equal(W.order().length, 56);
  W.hello(null);
  assert.equal(W.slots(), 9, 'a new connection starts vanilla');
});

test('shift-click orders match the server (JasprWide.java)', () => {
  const W = sandbox().JasprWide, java = fs.readFileSync(path.join(ROOT, 'scripts/java/wide-inventory/src/net/minecraft/server/v1_12_R1/JasprWide.java'), 'utf8');
  assert.deepEqual(Array.from(W.ids('hotbar')), [36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50]);
  assert.match(java, /HOTBAR_ORDER = \{36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50\}/);
  const main = Array.from(W.ids('main'));
  assert.deepEqual(main.slice(0, 14), [9, 10, 11, 12, 13, 14, 15, 16, 17, 51, 52, 53, 54, 55]);
  assert.deepEqual(main.slice(28), [27, 28, 29, 30, 31, 32, 33, 34, 35, 61, 62, 63, 64, 65]);
  assert.match(java, /MAIN_ORDER\[n\+\+\] = 9 \+ row \* 9 \+ c;[\s\S]*MAIN_ORDER\[n\+\+\] = 51 \+ row \* 5 \+ c;/);
  assert.deepEqual(Array.from(W.ids('all')), main.concat(Array.from(W.ids('hotbar'))));
  assert.deepEqual(Array.from(W.ids('result')), Array.from(W.ids('all')).reverse());
  assert.match(java, /RESULT_ORDER\[k\] = ALL_ORDER\[55 - k\]/);
});

test('windows: the 20 extension slots in item order, only once the server agreed, laid out in the pocket', () => {
  const ctx = sandbox(), W = ctx.JasprWide;
  const chest = window(ctx, 27, [140, 158, 176], 198);
  assert.equal(W.adoptPlan(chest.container), null, 'vanilla until the answer');
  W.answered(null);
  const added = adopt(ctx, chest);
  assert.deepEqual(added, [36, 37, 38, 39, 40].concat(Array.from({length: 15}, (_, k) => 41 + k)));
  assert.equal(chest.slots.length, 27 + 36 + 20, 'same count as the server window (83)');
  assert.equal(W.adoptPlan(chest.container), null, 'never twice');
  assert.deepEqual(Array.from(W.extraSlots(chest.container)), Array.from({length: 20}, (_, k) => 63 + k));
  W.layout({h2: chest.container, gv: 176});
  const at = i => chest.slots.find(s => s.$jw && s.bQx === i);
  assert.deepEqual([at(36).Lr, at(36).Fg], [183, 198], 'hotbar 10 beside the hotbar row');
  assert.deepEqual([at(40).Lr, at(40).Fg], [255, 198]);
  assert.deepEqual([at(41).Lr, at(41).Fg], [183, 140], 'row 1 extension beside row 1');
  assert.deepEqual([at(55).Lr, at(55).Fg], [255, 176]);
  // Creative's ContainerCreative shows only the hotbar: only the hotbar extension.
  const creative = window(ctx, 45, null, 112);
  assert.deepEqual(adopt(ctx, creative), [36, 37, 38, 39, 40]);
  W.layout({h2: creative.container, gv: 195});
  assert.deepEqual([creative.slots[54].Lr, creative.slots[54].Fg], [202, 112]);
  // Creative inventory tab: CreativeSlots for window slots 46..65.
  const slot = {};
  W.creativeSlot(slot, 46); assert.deepEqual([slot.Lr, slot.Fg, slot.$jw], [202, 112, 2]);
  W.creativeSlot(slot, 65); assert.deepEqual([slot.Lr, slot.Fg], [274, 90]);
});

test('screen: window and pocket centred together; the pocket is inside for clicks; frame and cells drawn', () => {
  const ctx = sandbox(), W = ctx.JasprWide;
  W.answered(null);
  const inv = window(ctx, 5, [84, 102, 120], 142);
  adopt(ctx, inv);
  const gui = new ctx.ID();
  Object.assign(gui, {h2: inv.container, gv: 176, gx: 166, is: 0, l7: 0, q: 0, L: 300});
  assert.equal(W.width(new ctx.Other(), 480), 480, 'other screens keep their width');
  W.layout(gui);
  assert.equal(W.width(gui, 480), 380);
  assert.equal(gui.$jwCut, 100);
  gui.q = 380;
  assert.equal(W.realWidth(gui), 480);
  assert.equal(W.pocketWidth(gui), 100);
  assert.equal(W.width(gui, 250), 200, 'never squeezes the window area below 200px');
  gui.q = 380; gui.$jwCut = 100; gui.is = (380 - 176) / 2; gui.l7 = 67;
  assert.equal(W.inPocket(gui, gui.is + 190, gui.l7 + 90), true);
  assert.equal(W.inPocket(gui, gui.is + 290, gui.l7 + 90), false);
  assert.equal(W.inPocket(gui, gui.is + 100, gui.l7 + 90), false, 'the window itself is not the pocket');
  const plan = W.pocketPlan(gui);
  assert.equal(plan.rects.length, 5 + 3 * 20);
  assert.deepEqual(Array.from(plan.strip), [380, 0, 480, 300], 'dark backdrop over the strip right of gui.q');
  assert.ok(plan.rects.every(r => r[0] >= gui.is + 176 && r[2] <= gui.is + 276), 'pocket stays in the 100px beside the window');
});

test('builder: every hook on the live client, reversible byte for byte, stable, parses', {skip: !fs.existsSync(LIVE)}, () => {
  const {build, strip, EDITS} = require('../scripts/build-wide-inventory-client.cjs');
  const raw = fs.readFileSync(LIVE, 'latin1');
  const {base, result} = build(raw);
  assert.equal(strip(result), base);
  assert.equal(result.split('/*JW*/').length - 1, EDITS.reduce((n, e) => n + (e[3] === 'all' ? 5 : e[3]), 0) + 2, 'one marker per hooked site');
  for (const name of ['JasprWideMergeIds', 'JasprWideShiftPlayer', 'JasprWideHello', 'JasprWideServerSaid', 'JasprWideAdopt', 'JasprWideHotbarExt',
    'JasprWidePocketDraw', 'JasprWideFirstEmpty']) assert.ok(result.includes('function ' + name + '('), name);
  assert.ok(result.indexOf('/* JASPR_WIDEINV_BEGIN */') > result.indexOf('/* JASPR_MOBENDS_END */'), 'after the Mo\' Bends block');
});

test('touch hotbar: 14 buttons, the last 5 only with the wide hotbar, selection through JasprWideBridge', () => {
  const js = fs.readFileSync(path.join(ROOT, 'site', 'jaspercraft-mobile-controls.js'), 'utf8');
  const css = fs.readFileSync(path.join(ROOT, 'site', 'jaspercraft-mobile-controls.css'), 'utf8');
  assert.match(js, /for\(var i=0;i<14;i\+\+\)/);
  assert.match(js, /if\(slot>=9\)b\.className='jaspr-wide'/);
  assert.match(js, /var w=window\.JasprWideBridge;if\(w\)w\.select\(slot\);else if\(bridge\(\)&&slot<9\)bridge\(\)\.slot\(slot\);/);
  assert.match(js, /function syncSlots\(\)/);
  assert.match(css, /button\.jaspr-wide\{display:none\}/);
  assert.match(css, /\[data-slots="14"\] button\.jaspr-wide\{display:block\}/);
  assert.match(css, /\[data-slots="14"\] button\{width:min\(34px,calc\(\(100vw - 24px\) \/ 14 - 2px\)\)\}/, '14 buttons fit a 320px phone');
});
