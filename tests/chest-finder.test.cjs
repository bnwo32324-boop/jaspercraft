'use strict';
// Chest Finder (owner, 2026-10-03: "With the easier crafting UI ... you should be able to right-click, and then it comes up with
// a UI element that says Find. If you click Find, it should show particle effects on the chest that has that particular item.
// Chests themselves should also have search boxes UI where you can search any item"; "There also should be a sorting button
// for chests that auto-organizes everything").
// The crafting panel's right-click menu and the Find message (client-mods/recipe-book-teavm.js), the chest search box, the
// chest screen hooks (scripts/build-chest-search-client.cjs) and the server plugin (server/custom-plugins/JasprFinder, with
// the offline check tests/java/chat/jaspr/finder/FinderCheck.java, fed every request the panel can send).
// 2026-10-08, owner: "right-click any item in your inventory and click Find. The UI should look the same, and it should have
// the same functionality of identifying the items in nearby chests" -- "It should be Shift + right-click, since right-click
// already has a function in the inventory to halve item stack": the same menu on an inventory item (Shift + right click), and
// "find slot <window> <slot> <name>", which the server resolves from its own copy of that slot.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'client-mods', 'recipe-book-teavm.js'), 'utf8');
const plugin = path.join(root, 'server/custom-plugins/JasprFinder');
const java = name => fs.readFileSync(path.join(plugin, 'src/chat/jaspr/finder', name + '.java'), 'utf8');

// Objects made inside the vm have their own prototypes, so compare them as plain JSON.
const same = (actual, expected, message) => assert.deepEqual(JSON.parse(JSON.stringify(actual)), JSON.parse(JSON.stringify(expected)), message);
const EMPTY = {rA: null, bg_: true};
const log = [];
const keysDown = new Set();   // keyboard codes held, as the game's Keyboard.isKeyDown (Jz) sees them: 42 is left shift
const SHIFT = 42;
const jstr = s => ({java: String(s)});
const ctx = {
  Ktg: EMPTY, $rt_globals: {}, console,
  FX: () => false, B: () => false, Ds: () => ({l() {}, s() {}}), FT: () => { throw new Error('FT'); },
  $rt_str: jstr, $rt_ustr: j => j.java,
  D49: (x1, y1, x2, y2, color) => log.push({op: 'rect', x: x1, y: y1, w: x2 - x1, h: y2 - y1, color: color >>> 0}),
  Efa: (font, j, x, y, color) => log.push({op: 'text', s: j.java, x, y, color}),
  C70: () => log.push({op: 'depthOff'}), DVf: () => log.push({op: 'depthOn'}),
  CA: (font, j) => j.java.length * 6, DQt: t => t,
  EJv: stack => { log.push({op: 'name'}); return jstr(stack.name); },
  FkM: () => {}, GcM: () => {}, MH: () => {}, Jz: code => keysDown.has(code), LyA: 'pickup', LyB: 'quick',
  FYl: (gui, slot, n, button, mode) => log.push({op: 'click', n, button, mode}),
  Cpd: player => log.push({op: 'close', who: player.name}),
  AKy: function () { this.packet = 'custom_payload'; }, Iu: function () {}, Fru: () => ({buffer: true}),
  Lg: (d, buf) => { d.buf = buf; }, FuF: (d, e) => { d.text = e.java; return d; },
  BgN: (c, channel, data) => { c.channel = channel.java; c.text = data.text; },
};
vm.createContext(ctx);
vm.runInContext(source + '\nthis.RB = JasprRecipeBook; this.TABLE = JasprRecipeTable; this.CS = JasprChestSearch;', ctx);
const {RB, TABLE, CS} = ctx;

const items = new Map();
const item = id => { if (!items.has(id)) items.set(id, {id}); return items.get(id); };
function parse(snbt) {
  const id = /id:"([^"]+)"/.exec(snbt)[1], dmg = /Damage:(-?\d+)s/.exec(snbt);
  return {rA: item(id), bK: dmg ? +dmg[1] : 0, PD: +(/Count:(\d+)b/.exec(snbt) || [0, 1])[1], bV: /tag:\{/.test(snbt) ? {} : null};
}
const prep = RB.newPrepared();
for (const job of RB.jobs()) RB.store(prep, job, parse(job.snbt));
RB.setPrepared(prep);
ctx.JasprRecipeBookCache = prep;   // the draw's own prepare step finds the cache ready
const stack = (id, n, dmg = 0) => ({rA: item('minecraft:' + id), bK: dmg, PD: n, bV: null});
const index = key => TABLE.recipes.findIndex(r => r.key === key);

const sent = [];
function craftingScreen(inventory) {
  const gui = {is: 200, l7: 40, gv: 176, gx: 166, q: 640, L: 300,
    j: {v: {name: 'player', d_: {qf: {bkf: false, wd: packet => sent.push(packet)}}}}};
  RB.attach(gui, 1, 3, 0, 10);
  const book = RB.of(gui);
  book.inventory = inventory.slice();
  while (book.inventory.length < 36) book.inventory.push(null);
  book.craftStacks = [];
  RB.computeCraftable(book);
  book.lastPlan = RB.plan(book, -1000, -1000);
  return {gui, book};
}
const itemOf = (book, i) => book.lastPlan.items.find(it => it.i === i);
const row = (menu, act) => ({x: menu.x + 10, y: menu.y + 2 + 12 * (menu.entries.findIndex(e => e.act === act) + 1) + 4});

test('right click on a panel item opens the menu: Find in chests, Fill grid when it can be made, Cancel', () => {
  const {book} = craftingScreen([stack('cobblestone', 8)]);
  const furnace = index('minecraft:furnace'), it = itemOf(book, furnace);
  assert.ok(it, 'the furnace is listed');
  same(RB.planClicks(book, it.x + 3, it.y + 3, 1), [], 'a right click sends no slot clicks');
  same(book.menu.entries.map(e => e.act), ['find', 'fill', 'close']);
  assert.equal(book.menu.entries[0].label, 'Find in chests');
  // drawn over the list's items, with the entry under the pointer highlighted
  const at = row(book.menu, 'find'), plan = RB.plan(book, at.x, at.y);
  assert.ok(plan.over.texts.some(t => t.s === 'Find in chests'));
  assert.ok(plan.over.rects.some(r => r.color === 0xFF3A2A6A && r.y <= at.y && at.y < r.y + r.h), 'hovered entry');
  // Find: the request is queued for the click state machine, the menu closes
  same(RB.planClicks(book, at.x, at.y, 0), []);
  assert.equal(book.findRequest, 'find minecraft:furnace 0 0 Furnace');
  assert.equal(book.menu, null);
});

test('the menu: Fill grid is the old right click; Cancel, a click elsewhere and Escape close it', () => {
  const {book} = craftingScreen([stack('cobblestone', 8)]);
  const furnace = index('minecraft:furnace'), it = itemOf(book, furnace);
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  const fill = RB.planClicks(book, row(book.menu, 'fill').x, row(book.menu, 'fill').y, 0);
  same(fill, RB.clickScript(book, furnace, 1, false), 'fill only, as the original right click');
  assert.ok(fill.length > 0 && !fill.some(c => c[0] === 0), 'never takes the result');
  assert.equal(book.pending, null);
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  same(RB.planClicks(book, row(book.menu, 'close').x, row(book.menu, 'close').y, 0), []);
  assert.equal(book.menu, null);
  assert.ok(!book.findRequest);
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  assert.equal(RB.planClicks(book, -9999, -9999, 0), null, 'a click elsewhere closes the menu and still reaches the screen');
  assert.equal(book.menu, null);
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  assert.equal(RB.key(book, 0, 1), 1, 'Escape closes the menu, not the screen');
  assert.equal(book.menu, null);
  assert.equal(RB.key(book, 0, 1), 0, 'a second Escape is the screen\'s');
});

test('the menu for an item that cannot be made: Find and Cancel only; kept on screen', () => {
  const {book} = craftingScreen([stack('dirt', 1)]);
  book.search = 'diamond pick';
  book.lastPlan = RB.plan(book, -1000, -1000);
  const pick = index('minecraft:diamond_pickaxe'), it = itemOf(book, pick);
  assert.ok(it, 'search results are listed');
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  same(book.menu.entries.map(e => e.act), ['find', 'close']);
  const gui = book.gui;
  RB.openMenu(book, pick, 10000, 10000);
  assert.ok(book.menu.x + book.menu.w <= gui.q - gui.is && book.menu.y + book.menu.h <= gui.L - gui.l7, 'inside the bottom-right corner');
  RB.openMenu(book, pick, -10000, -10000);
  assert.ok(book.menu.x >= -gui.is && book.menu.y >= -gui.l7, 'inside the top-left corner');
});

test('Find requests name the exact item: vanilla by id and damage, JasperCraft model items exactly', () => {
  assert.equal(RB.findText(index('minecraft:diamond_pickaxe')), 'find minecraft:diamond_pickaxe 0 0 Diamond Pickaxe');
  const gun = RB.findText(index('jasprapocalypse:jaspr_portal_gun'));
  assert.match(gun, /^find minecraft:diamond_hoe \d+ 1 Portal Gun$/, 'an unbreakable JasperCraft item is exact');
  for (let i = 0; i < TABLE.recipes.length; i++) {
    const text = RB.findText(i);
    assert.match(text, /^find [a-z0-9_]+:[a-z0-9_./]+ \d{1,5} [01] [^\u0000-\u001f\u00a7]{1,48}$/, TABLE.recipes[i].key);
    assert.ok(Buffer.byteLength(text) <= 200, 'within the server\'s bound');
  }
});

test('clicking Find sends jaspr:find once and closes the screen', () => {
  const {gui, book} = craftingScreen([stack('cobblestone', 8)]);
  const it = itemOf(book, index('minecraft:furnace'));
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  const at = row(book.menu, 'find');
  sent.length = 0; log.length = 0;
  const before = RB.finds | 0;
  ctx.JasprRecipeBookClick(gui, gui.is + at.x, gui.l7 + at.y, 0);
  assert.equal(ctx.JasprRecipeBookConsumedClick(), 1, 'the screen never sees the click');
  assert.equal(sent.length, 1);
  same({channel: sent[0].channel, text: sent[0].text}, {channel: 'jaspr:find', text: 'find minecraft:furnace 0 0 Furnace'});
  assert.equal(RB.finds, before + 1);
  same(log.filter(e => e.op === 'close' || e.op === 'click'), [{op: 'close', who: 'player'}], 'closed, no slot clicks');
  // without a connection nothing is sent (and nothing throws)
  gui.j.v.d_.qf = null;
  RB.planClicks(book, it.x + 3, it.y + 3, 1);
  ctx.JasprRecipeBookClick(gui, gui.is + row(book.menu, 'find').x, gui.l7 + row(book.menu, 'find').y, 0);
  assert.equal(sent.length, 1);
});

/** The survival inventory window at vanilla positions: output 0, the 2x2 grid 1-4, armour 5-8, main 9-35, hotbar 36-44, off-hand 45
 * (and `extra` more slots, as the wide inventory adds); contents maps a slot to {id, name, n}. Window id `win`. */
function inventoryScreen(contents = {}, {win = 0, extra = 0} = {}) {
  const stacks = [], slots = [];
  const place = k => k === 0 ? [154, 28] : k <= 4 ? [98 + ((k - 1) % 2) * 18, 18 + (((k - 1) / 2) | 0) * 18]
    : k <= 8 ? [8, 8 + (k - 5) * 18] : k <= 35 ? [8 + ((k - 9) % 9) * 18, 84 + (((k - 9) / 9) | 0) * 18]
    : k <= 44 ? [8 + (k - 36) * 18, 142] : k === 45 ? [77, 62] : [8 + ((k - 46) % 10) * 18, 170 + (((k - 46) / 10) | 0) * 18];
  for (let k = 0; k < 46 + extra; k++) {
    const [x, y] = place(k), content = contents[k];
    stacks.push(content ? {rA: item('minecraft:' + content.id), bK: 0, PD: content.n || 1, bV: null, name: content.name} : EMPTY);
    slots.push({Lr: x, Fg: y, eew: () => stacks[k]});
  }
  const gui = {is: 200, l7: 40, gv: 176, gx: 166, q: 640, L: 300, J: 'font', hu: 'items', a_b: null,
    h2: {iu: win, cn: {g: 46 + extra, qN: {data: slots}}},
    j: {v: {name: 'player', d_: {qf: {bkf: false, wd: packet => sent.push(packet)}}}}};
  RB.attach(gui, 1, 2, 0, 9);
  const book = RB.of(gui);
  book.inventory = stacks.slice(9, 45);
  book.craftStacks = stacks.slice(1, 5);
  RB.computeCraftable(book);
  book.refreshAt = Date.now() + 1e9;   // no scan is due: the draw uses these stacks
  const at = k => ({x: gui.is + slots[k].Lr + 8, y: gui.l7 + slots[k].Fg + 8});
  const click = (k, button = 1) => { const p = at(k); ctx.JasprRecipeBookClick(gui, p.x, p.y, button); return ctx.JasprRecipeBookConsumedClick(); };
  return {gui, book, stacks, slots, at, click};
}

test('Shift + right click on an inventory item opens the same menu, titled with its name; the click is ours', () => {
  const win = inventoryScreen({36: {id: 'diamond_pickaxe', name: 'Diamond Pickaxe'}, 5: {id: 'iron_helmet', name: 'Iron Helmet'}, 45: {id: 'shield', name: 'Shield'}});
  const {gui, book} = win;
  keysDown.add(SHIFT);
  try {
    for (const [slot, name] of [[36, 'Diamond Pickaxe'], [5, 'Iron Helmet'], [45, 'Shield']]) {
      book.menu = null; sent.length = 0; log.length = 0;
      const menus = RB.slotMenus | 0;
      assert.equal(win.click(slot), 1, name + ': the screen never sees the click');
      assert.equal(log.filter(e => e.op === 'name').length, 1, 'the name is asked once');
      assert.equal(book.menu.slot, slot);
      assert.equal(book.menu.title, name);
      same(book.menu.entries.map(e => e.act), ['find', 'close'], 'Find in chests and Cancel, as for a panel item that cannot be made');
      same(book.menu.entries.map(e => e.label), ['Find in chests', 'Cancel']);
      assert.equal(book.menu.w, 96);
      assert.equal(book.menu.h, 12 * 3 + 4);
      assert.equal(RB.slotMenus, menus + 1);
      assert.equal(sent.length, 0, 'opening the menu sends nothing');
      assert.ok(!log.some(e => e.op === 'click' || e.op === 'close'), 'no slot click, the screen stays open');
    }
    // the draw: the menu looks like the panel's (purple frame, dark fill, yellow title, green Find, grey Cancel), over everything
    gui.a_b = {hovered: true};
    book.menu = null;
    win.click(36);
    log.length = 0;
    const at = win.at(36);
    ctx.JasprRecipeBookDraw(gui, at.x, at.y);
    assert.equal(gui.a_b, null, 'no item tooltip over the menu');
    const from = log.findIndex(e => e.op === 'depthOff'), over = log.slice(from);
    assert.ok(from > 0 && over[over.length - 1].op === 'depthOn', 'drawn last, with the depth test off');
    assert.ok(over.some(e => e.op === 'rect' && e.color === 0xFF5000A0 && e.w === 98), 'purple frame');
    assert.ok(over.some(e => e.op === 'rect' && e.color === 0xF8100010 && e.w === 96), 'dark fill');
    assert.ok(over.some(e => e.op === 'text' && e.s === 'Diamond Pickaxe' && e.color === 0xFFFF55), 'yellow title');
    assert.ok(over.some(e => e.op === 'text' && e.s === 'Find in chests' && e.color === 0x55FF55), 'green Find');
    assert.ok(over.some(e => e.op === 'text' && e.s === 'Cancel' && e.color === 0xA0A0A0), 'grey Cancel');
    // with no menu open the game keeps its own hover (and its tooltip)
    book.menu = null;
    gui.a_b = {hovered: true};
    ctx.JasprRecipeBookDraw(gui, at.x, at.y);
    assert.deepEqual(gui.a_b, {hovered: true});
  } finally { keysDown.delete(SHIFT); }
});

test('everything else stays the game\'s: a plain right click (half a stack), Shift + left click, an empty slot, the crafting output', () => {
  const win = inventoryScreen({36: {id: 'cobblestone', name: 'Cobblestone', n: 40}, 0: {id: 'stick', name: 'Stick', n: 4}});
  const {book} = win;
  sent.length = 0;
  assert.equal(win.click(36, 1), 0, 'right click: the game halves the stack');
  assert.ok(!book.menu);
  keysDown.add(SHIFT);
  try {
    assert.equal(win.click(36, 0), 0, 'Shift + left click: the game moves it');
    assert.equal(win.click(20, 1), 0, 'Shift + right click on an empty slot');
    assert.equal(win.click(0, 1), 0, 'Shift + right click on the crafting output keeps crafting');
    assert.ok(!book.menu);
    ctx.JasprRecipeBookClick(win.gui, win.gui.is - 500, win.gui.l7 - 500, 1);
    assert.equal(ctx.JasprRecipeBookConsumedClick(), 0, 'off every slot');
    assert.ok(!book.menu);
    // the panel is not ready (still being prepared): the click is the game's rather than a menu that cannot be drawn
    const ready = RB.prepared();
    RB.setPrepared(null);
    assert.equal(win.click(36, 1), 0);
    RB.setPrepared(ready);
    assert.equal(win.click(36, 1), 1, 'ready again');
  } finally { keysDown.delete(SHIFT); }
  assert.equal(sent.length, 0);
});

test('Find on an inventory item sends "find slot <window> <slot> <name>" once and closes the screen', () => {
  const win = inventoryScreen({36: {id: 'diamond_pickaxe', name: '§bTitan §lPickaxe'}}, {win: 7, extra: 20});
  const {gui, book} = win;
  keysDown.add(SHIFT);
  try {
    assert.equal(win.click(36), 1);
    assert.equal(book.menu.title, 'Titan Pickaxe', 'colour codes are not shown');
    const find = row(book.menu, 'find'), before = RB.finds | 0, slotFinds = RB.slotFinds | 0;
    sent.length = 0; log.length = 0;
    keysDown.delete(SHIFT);
    ctx.JasprRecipeBookClick(gui, gui.is + find.x, gui.l7 + find.y, 0);
    assert.equal(ctx.JasprRecipeBookConsumedClick(), 1, 'the screen never sees the click');
    assert.equal(sent.length, 1);
    same({channel: sent[0].channel, text: sent[0].text}, {channel: 'jaspr:find', text: 'find slot 7 36 Titan Pickaxe'});
    assert.equal(RB.finds, before + 1);
    assert.equal(RB.slotFinds, slotFinds + 1);
    same(log.filter(e => e.op === 'close' || e.op === 'click'), [{op: 'close', who: 'player'}], 'closed, no slot clicks');
    assert.ok(!book.menu);
    assert.equal(book.findRequest, null);
    // a slot the wide inventory added is just another slot
    keysDown.add(SHIFT);
    win.stacks[50] = {rA: item('minecraft:ender_pearl'), bK: 0, PD: 16, bV: null, name: 'Ender Pearl'};
    sent.length = 0;
    assert.equal(win.click(50), 1);
    assert.equal(book.menu.slot, 50);
    keysDown.delete(SHIFT);
    const again = row(book.menu, 'find');
    ctx.JasprRecipeBookClick(gui, gui.is + again.x, gui.l7 + again.y, 0);
    assert.equal(sent[0].text, 'find slot 7 50 Ender Pearl');
    // without a connection nothing is sent (and nothing throws)
    keysDown.add(SHIFT);
    win.click(36);
    keysDown.delete(SHIFT);
    gui.j.v.d_.qf = null;
    const none = row(book.menu, 'find');
    ctx.JasprRecipeBookClick(gui, gui.is + none.x, gui.l7 + none.y, 0);
    assert.equal(sent.length, 1);
  } finally { keysDown.delete(SHIFT); }
});

test('the item menu: Cancel, a click elsewhere and Escape close it; kept on screen; a window id is needed', () => {
  const win = inventoryScreen({44: {id: 'torch', name: 'Torch', n: 12}, 45: {id: 'shield', name: 'Shield'}});
  const {gui, book} = win;
  keysDown.add(SHIFT);
  try {
    gui.q = 300; gui.L = 200;   // a small screen: the window's bottom-right corner is nearly its edge
    win.click(44);
    assert.ok(book.menu.x > 0 && book.menu.x + book.menu.w <= gui.q - gui.is && book.menu.y + book.menu.h <= gui.L - gui.l7, 'inside the bottom-right corner');
    assert.equal(book.menu.x, gui.q - gui.is - book.menu.w - 1, 'pushed back from the edge');
    const cancel = row(book.menu, 'close');
    sent.length = 0;
    keysDown.delete(SHIFT);
    ctx.JasprRecipeBookClick(gui, gui.is + cancel.x, gui.l7 + cancel.y, 0);
    assert.equal(ctx.JasprRecipeBookConsumedClick(), 1);
    assert.ok(!book.menu);
    assert.ok(!book.findRequest);
    keysDown.add(SHIFT);
    win.click(44);
    keysDown.delete(SHIFT);
    ctx.JasprRecipeBookClick(gui, gui.is - 900, gui.l7 - 900, 0);
    assert.equal(ctx.JasprRecipeBookConsumedClick(), 0, 'a click elsewhere closes the menu and still reaches the screen');
    assert.ok(!book.menu);
    keysDown.add(SHIFT);
    win.click(45);
    keysDown.delete(SHIFT);
    assert.equal(RB.key(book, 0, 1), 1, 'Escape closes the menu, not the screen');
    assert.ok(!book.menu);
    assert.equal(RB.key(book, 0, 1), 0, 'a second Escape is the screen\'s');
    assert.equal(sent.length, 0);
    // a screen whose window id is unknown cannot be asked about
    keysDown.add(SHIFT);
    win.click(45);
    keysDown.delete(SHIFT);
    gui.h2.iu = undefined;
    const find = row(book.menu, 'find');
    ctx.JasprRecipeBookClick(gui, gui.is + find.x, gui.l7 + find.y, 0);
    assert.equal(sent.length, 0, 'nothing to send without a window id');
    assert.ok(!book.menu);
  } finally { keysDown.delete(SHIFT); }
});

test('a menu already open: Shift + right click on another item moves it there; on the menu itself the click is the menu\'s', () => {
  const win = inventoryScreen({36: {id: 'diamond_pickaxe', name: 'Diamond Pickaxe'}, 37: {id: 'cobblestone', name: 'Cobblestone', n: 40}, 5: {id: 'iron_helmet', name: 'Iron Helmet'}});
  const {gui, book} = win;
  keysDown.add(SHIFT);
  try {
    // a panel menu is open (the recipe list's): Shift + right click on an item replaces it rather than falling through to a quick move
    RB.openMenu(book, index('minecraft:furnace'), 10, 10);
    assert.equal(book.menu.slot, undefined);
    sent.length = 0;
    assert.equal(win.click(36), 1);
    assert.equal(book.menu.slot, 36);
    assert.equal(book.menu.title, 'Diamond Pickaxe');
    // an item menu is open: another item gets its own menu
    assert.equal(win.click(37), 1);
    assert.equal(book.menu.slot, 37);
    assert.equal(book.menu.title, 'Cobblestone');
    // Shift is still held and the right button used on the menu itself: that is the menu's entry, not another menu for the slot under it
    const cancel = row(book.menu, 'close');
    ctx.JasprRecipeBookClick(gui, gui.is + cancel.x, gui.l7 + cancel.y, 1);
    assert.equal(ctx.JasprRecipeBookConsumedClick(), 1);
    assert.equal(book.menu, null, 'Cancel closed it');
    assert.equal(sent.length, 0);
    // an empty slot closes the open menu and the click is the game's
    assert.equal(win.click(36), 1);
    assert.equal(win.click(20), 0, 'an empty slot: the game\'s click');
    assert.equal(book.menu, null);
  } finally { keysDown.delete(SHIFT); }
});

/** A chest screen: 27 chest slots, then the player's 36; slot k holds stacks[k]. */
function chestScreen(names, title = 'Chest', top = 40) {
  const stacks = [], slots = [];
  for (let k = 0; k < 63; k++) {
    const name = k < 27 ? names[k] : (k === 30 ? 'Diamond' : undefined);
    stacks.push(name ? {rA: item('x' + k), bK: 0, PD: 1, bV: null, name} : EMPTY);
    slots.push({Lr: 8 + (k % 9) * 18, Fg: k < 27 ? 18 + ((k / 9) | 0) * 18 : 84 + (((k - 27) / 9) | 0) * 18, eew: () => stacks[k]});
  }
  const gui = {is: 200, l7: top, gv: 176, gx: 166, q: 640, L: 300, J: 'font', h2: {iu: 7, cn: {g: 63, qN: {data: slots}}},
    j: {v: {name: 'player', d_: {qf: {bkf: false, wd: packet => sent.push(packet)}}}}};
  const inv = {iG: () => jstr(title)};
  return {gui, inv, stacks, draw() { log.length = 0; const st = CS.of(gui); if (st) st.scanAt = 0; ctx.JasprChestSearchDraw(gui, 0, 0, inv); return log.slice(); }};
}
const type = (gui, text) => { for (const ch of text) assert.equal(CS.key(gui, ch.charCodeAt(0), 0), 1); };

test('chest search box: in the title row, focus on click, typing dims what does not match and frames what does', () => {
  const names = ['Diamond', '\u00a7bTitan Pickaxe', 'Diamond Sword', 'Oak Wood Planks'];
  const chest = chestScreen(names);
  let ops = chest.draw();
  const box = CS.of(chest.gui).box;
  same(box, {x: 176 - 7 - 26 - 3 - 64, y: 4, w: 64, h: 12});
  same(CS.of(chest.gui).sort, {x: 176 - 7 - 26, y: 4, w: 26, h: 12});
  assert.equal(ops[0].op, 'depthOff');
  assert.equal(ops[ops.length - 1].op, 'depthOn');
  assert.ok(ops.some(o => o.op === 'text' && o.s === 'Search\u2026'));
  assert.equal(ops.filter(o => o.op === 'name').length, 0, 'nothing is read while the box is empty');
  // keys before the box has focus belong to the screen (E still closes the chest)
  assert.equal(CS.key(chest.gui, 'e'.charCodeAt(0), 18), 0);
  assert.equal(CS.click(chest.gui, chest.gui.is + box.x + 5, chest.gui.l7 + box.y + 5, 0), true);
  type(chest.gui, 'diamond');
  ops = chest.draw();
  const st = CS.of(chest.gui);
  same(st.matches.slice(0, 4), [true, false, true, false]);
  assert.equal(st.matches.length, 27, 'only the chest\'s own slots (the Diamond in the player inventory is not counted)');
  assert.equal(st.found, 2);
  const dims = ops.filter(o => o.op === 'rect' && o.color === 0xB8101010), gold = ops.filter(o => o.op === 'rect' && o.color === 0xFFFFC000 && (o.w === 1 || o.h === 1));
  assert.equal(dims.length, 25);
  assert.equal(gold.length, 2 * 4, 'a frame around each match');
  assert.ok(!dims.some(r => r.y >= 84), 'the player\'s inventory is left alone');
  assert.ok(ops.some(o => o.op === 'text' && o.s === '2' && o.color === 0xFFC000), 'the count');
  assert.ok(ops.some(o => o.op === 'rect' && o.color === 0xFFFFC000 && o.w === box.w + 2), 'a gold border while something matches');
  assert.equal(ops.filter(o => o.op === 'name').length, 4, 'each stack\'s name asked once');
  ops = chest.draw();
  assert.equal(ops.filter(o => o.op === 'name').length, 0, 'and remembered');
  // every word must match; colour codes are ignored
  CS.key(chest.gui, 0, 211);
  type(chest.gui, 'titan pick');
  chest.draw();
  same(st.matches.slice(0, 4), [false, true, false, false]);
  // nothing found: red border and 0
  CS.key(chest.gui, 0, 211);
  type(chest.gui, 'zzz');
  ops = chest.draw();
  assert.equal(st.found, 0);
  assert.ok(ops.some(o => o.op === 'rect' && o.color === 0xFFFF5555));
  assert.ok(ops.some(o => o.op === 'text' && o.s === '0'));
  // backspace as the client sends it (key 14, char 0), then a right click on the box clears it
  CS.key(chest.gui, 0, 14);
  assert.equal(st.search, 'zz');
  assert.equal(CS.click(chest.gui, chest.gui.is + box.x + 5, chest.gui.l7 + box.y + 5, 1), true);
  assert.equal(st.search, '');
  assert.equal(st.matches, null);
  // a click elsewhere unfocuses and reaches the screen; Escape leaves the box first
  type(chest.gui, 'oak');
  assert.equal(CS.click(chest.gui, chest.gui.is + 20, chest.gui.l7 + 30, 0), false);
  assert.equal(CS.key(chest.gui, 'e'.charCodeAt(0), 18), 0);
  CS.click(chest.gui, chest.gui.is + box.x + 5, chest.gui.l7 + box.y + 5, 0);
  assert.equal(CS.key(chest.gui, 0, 1), 1);
  assert.equal(st.focused, false);
  assert.equal(st.search, 'oak', 'the search stays');
  // the box's clicks arrive through the recipe book's GuiContainer hook
  ctx.JasprRecipeBookClick(chest.gui, chest.gui.is + box.x + 5, chest.gui.l7 + box.y + 5, 0);
  assert.equal(ctx.JasprRecipeBookConsumedClick(), 1);
  assert.equal(ctx.JasprRecipeBookKeyTyped(chest.gui, 'x'.charCodeAt(0), 45), 1);
  assert.equal(st.search, 'oakx');
});

test('chest search box: a long chest name moves the box above the window when there is room', () => {
  const long = 'A very long custom chest name';
  const above = chestScreen([], long, 40);
  above.draw();
  same(CS.of(above.gui).box, {x: 176 - 26 - 3 - 64, y: -14, w: 64, h: 12});
  same(CS.of(above.gui).sort, {x: 176 - 26, y: -14, w: 26, h: 12});
  const cramped = chestScreen([], long, 6);
  cramped.draw();
  assert.equal(CS.of(cramped.gui).box.y, 4, 'no room above: stays in the title row');
  const short = chestScreen([], 'Chest', 40);
  short.draw();
  assert.equal(CS.of(short.gui).box.y, 4);
});

test('Sort button: beside the box, sends "sort <window id>" on jaspr:sort, keeps the screen open, at most every 0.6 s', () => {
  const chest = chestScreen(['Dirt', 'Stone', 'Dirt']);
  chest.gui.h2.iu = 12;
  let ops = chest.draw();
  const button = CS.of(chest.gui).sort;
  assert.ok(ops.some(o => o.op === 'text' && o.s === 'Sort'), 'labelled');
  const at = {x: chest.gui.is + button.x + 5, y: chest.gui.l7 + button.y + 5};
  // hovered: cream border (the JasperCraft look; it was white)
  log.length = 0; CS.of(chest.gui).scanAt = 0;
  ctx.JasprChestSearchDraw(chest.gui, at.x, at.y, chest.inv);
  assert.ok(log.some(o => o.op === 'rect' && o.color === 0xFFF4E6BC && o.w === button.w + 2), 'hover');
  sent.length = 0; log.length = 0;
  const before = RB.sorts | 0;
  ctx.JasprRecipeBookClick(chest.gui, at.x, at.y, 0);
  assert.equal(ctx.JasprRecipeBookConsumedClick(), 1, 'the screen never sees the click');
  assert.equal(sent.length, 1);
  same({channel: sent[0].channel, text: sent[0].text}, {channel: 'jaspr:sort', text: 'sort 12'});
  assert.equal(RB.sorts, before + 1);
  assert.equal(log.filter(o => o.op === 'close').length, 0, 'the chest stays open');
  ops = chest.draw();
  assert.ok(ops.some(o => o.op === 'text' && o.s === 'Sort' && o.color === 0x55FF55), 'pressed');
  // a second click right away, a right click, and a click without a window id send nothing (but are still ours)
  ctx.JasprRecipeBookClick(chest.gui, at.x, at.y, 0);
  assert.equal(ctx.JasprRecipeBookConsumedClick(), 1);
  assert.equal(sent.length, 1, 'once every 0.6 s');
  CS.of(chest.gui).sortAt = 0;
  ctx.JasprRecipeBookClick(chest.gui, at.x, at.y, 1);
  assert.equal(sent.length, 1, 'right click does not sort');
  chest.gui.h2.iu = 0;
  ctx.JasprRecipeBookClick(chest.gui, at.x, at.y, 0);
  assert.equal(sent.length, 1, 'no window, no sort');
  assert.equal(CS.status().sorts >= 1, true);
  // the search box keeps working beside it
  const box = CS.of(chest.gui).box;
  assert.equal(CS.click(chest.gui, chest.gui.is + box.x + 3, chest.gui.l7 + box.y + 3, 0), true);
  assert.equal(CS.of(chest.gui).focused, true);
  assert.equal(CS.click(chest.gui, at.x, at.y, 0), true);
  assert.equal(CS.of(chest.gui).focused, false, 'Sort takes the focus off the box');
});

test('chest screen hooks: GuiChest and GuiShulkerBox, exact, reversible, stable, parses', () => {
  const stage = require('../scripts/build-chest-search-client.cjs');
  const file = process.env.CHEST_SEARCH_SOURCE || path.join(root, 'site', 'classes.js');
  const raw = fs.existsSync(file) ? fs.readFileSync(file, 'latin1') : '';
  if (!stage.EDITS.every(([vanilla, patched]) => raw.includes(vanilla) || raw.includes(patched))) return;
  const {base, result} = stage.build(raw);
  assert.equal(stage.strip(result), base);
  assert.equal(stage.build(result).result, result);
  for (const [name, inv] of [['DUK', 'a.d0b'], ['D2Q', 'a.dmc']]) {
    const start = result.indexOf('\nfunction ' + name + '(') + 1, fn = result.slice(start, result.indexOf('\nfunction ', start));
    assert.ok(fn.includes(stage.HOOK + '$p=90;case 90:if(typeof JasprChestSearchDraw==="function"){JasprChestSearchDraw(a,b,c,' + inv + ');if(B()){break _;}}$p=91;case 91:d=a.J;e=' + inv + ';'), name);
    assert.equal(fn.split('case 90:').length, 2, name + ': one new state');
  }
});

test('JasprFinder: requests, matching, decode and every panel request (offline Java check)', {skip: !fs.existsSync('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin') && 'no JDK'}, () => {
  const jdk = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-finder-'));
  const requests = path.join(out, 'panel-requests.txt'), slotRequests = path.join(out, 'slot-requests.txt');
  fs.writeFileSync(requests, TABLE.recipes.map((r, i) => RB.findText(i)).join('\n') + '\n', 'utf8');
  // every shape of name the inventory menu can send for a slot: colour codes, long, empty, unicode, doubled spaces
  const named = inventoryScreen({}, {win: 9});
  const names = ['Diamond Pickaxe', '§bTitan §lPickaxe', 'x'.repeat(100), '', 'Name  with   spaces', 'éclair', '§c§r'];
  const asked = [];
  for (const name of names)
    for (const slot of [0, 5, 36, 45]) { RB.openSlotMenu(named.book, slot, 20, 20, name); asked.push(RB.findSlotText(named.book, named.book.menu)); }
  fs.writeFileSync(slotRequests, asked.join('\n') + '\n', 'utf8');
  const cp = path.join(root, 'server/cache/patched_1.12.2.jar');
  const sources = fs.readdirSync(path.join(plugin, 'src/chat/jaspr/finder')).map(f => path.join(plugin, 'src/chat/jaspr/finder', f));
  sources.push(path.join(root, 'tests/java/chat/jaspr/finder/FinderCheck.java'));
  const javac = spawnSync(path.join(jdk, 'javac.exe'), ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-cp', cp, '-d', out, ...sources], {encoding: 'utf8'});
  assert.equal(javac.status, 0, javac.stderr);
  const run = spawnSync(path.join(jdk, 'java.exe'), ['-XX:ActiveProcessorCount=1', '-Xmx256m', '-cp', out + path.delimiter + cp, 'chat.jaspr.finder.FinderCheck', requests, slotRequests], {encoding: 'utf8'});
  fs.rmSync(out, {recursive: true, force: true});
  assert.equal(run.status, 0, run.stderr + run.stdout);
  assert.match(run.stdout, new RegExp('FINDER_PANEL requests=' + TABLE.recipes.length));
  assert.match(run.stdout, new RegExp('FINDER_SLOT requests=' + asked.length), 'every "find slot" message the menu can send parses');
  assert.match(run.stdout, /FINDER_OK checks=\d+/);
});

test('JasprFinder wiring: the channel, privacy default, bounds, sparks for the requester only, logs', () => {
  const main = java('FinderPlugin'), yml = fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8');
  const config = fs.readFileSync(path.join(plugin, 'resources/config.yml'), 'utf8');
  assert.match(yml, /main: chat\.jaspr\.finder\.FinderPlugin/);
  assert.doesNotMatch(yml, /commands:/, 'a plugin channel, not a command: nothing for the command gate to block');
  assert.match(main, /static final String CHANNEL = "jaspr:find", SORT_CHANNEL = "jaspr:sort", SLOT_PREFIX = "find slot ";/);
  assert.match(main, /registerIncomingPluginChannel\(this, CHANNEL, this\)/);
  assert.match(config, /^search: opened$/m, 'by default only containers this player opened are searched');
  assert.match(main, /searchAll = "all"\.equalsIgnoreCase\(getConfig\(\)\.getString\("search", "opened"\)\);/);
  assert.match(main, /radius = Math\.max\(8, Math\.min\(96, getConfig\(\)\.getInt\("radius", 48\)\)\);/);
  assert.match(main, /MAX_REMEMBERED = 4096, MAX_SCAN = 600, MAX_SHOWN = 16/);
  assert.match(main, /COOLDOWN_MS = 2000/);
  assert.match(main, /if \(!w\.isChunkLoaded\(cx \+ dx, cz \+ dz\)\) continue;/, 'never loads a chunk');
  assert.match(main, /p\.spawnParticle\(Particle\.VILLAGER_HAPPY/);
  assert.doesNotMatch(main, /world\.spawnParticle|w\.spawnParticle|getWorld\(\)\.spawnParticle/, 'nobody else sees the sparks');
  assert.match(main, /for \(ItemStack s : p\.getEnderChest\(\)\.getContents\(\)\)/, 'the player\'s own ender chest');
  for (const token of ['FINDER_READY', 'FINDER_FIND', 'FINDER_METRICS', 'FINDER_SAVE_FAILED', 'FINDER_LOAD_FAILED'])
    assert.ok(main.includes(token), token);
  assert.doesNotMatch(main, /getAddress\(\)/, 'no IP addresses in the logs');
  // Find on an inventory slot: the server reads the slot itself, only in the window that is open now, and says why it found nothing
  assert.match(main, /SLOT_PREFIX = "find slot "/);
  assert.match(main, /boolean viaSlot = text != null && text\.startsWith\(SLOT_PREFIX\);/);
  assert.match(main, /FindRequest request = viaSlot \? fromSlot\(p, text\) : FindRequest\.parse\(text\);/, 'the panel\'s message is parsed as before');
  assert.match(main, /lookup\(\(\(org\.bukkit\.craftbukkit\.v1_12_R1\.entity\.CraftPlayer\) p\)\.getHandle\(\)\.activeContainer, ref\[0\], ref\[1\]\)/, 'the player\'s own open window');
  assert.match(main, /if \(open == null \|\| open\.windowId != window\) return new SlotLookup\(null, "stale"\);/, 'a late request never reads the next screen');
  assert.match(main, /if \(slot < 0 \|\| slot >= open\.slots\.size\(\)\) return new SlotLookup\(null, "range"\);/, 'bounded');
  assert.match(main, /window < 0 \|\| window > 255 \|\| slot < 0 \|\| slot > 255 \? null/);
  assert.match(main, /find\(p, request, now, viaSlot \? "slot" : "panel"\);/);
  for (const token of ['FINDER_FIND_REFUSED player=', 'via=slot reason=', ' via=" + via', 'slotRequests=', 'slotStale=', 'slotEmpty=', 'slotFind=true'])
    assert.ok(main.includes(token), token);
  assert.doesNotMatch(main, /getOpenInventory\(\)\.getItem/, 'one way to read a slot');
  assert.match(java('FindRequest'), /static FindRequest of\(ItemStack stack, String clientTitle\)/);
  assert.match(fs.readFileSync(path.join(plugin, 'resources/plugin.yml'), 'utf8'), /^version: 1\.1\.0$/m);
  // Sort: its own channel, only the window that is open, only real containers, never a plugin's menu
  const sorter = java('ChestSorter');
  assert.match(main, /SORT_CHANNEL = "jaspr:sort"/);
  assert.match(main, /registerIncomingPluginChannel\(this, SORT_CHANNEL, this\)/);
  assert.match(main, /getHandle\(\)\.activeContainer\.windowId != window \|\| window <= 0\) \{ sortStale\+\+; return; \}/);
  assert.match(main, /if \(holder instanceof DoubleChest\) return "double_chest";/);
  assert.match(main, /m == Material\.CHEST \|\| m == Material\.TRAPPED_CHEST \|\| m\.name\(\)\.endsWith\("SHULKER_BOX"\)/);
  assert.match(main, /top\.getType\(\) == InventoryType\.ENDER_CHEST/);
  assert.match(main, /SORT_COOLDOWN_MS = 500/);
  assert.match(main, /ItemStack\[\] after = ChestSorter\.sorted\(before\);\s*if \(after == null\)/, 'never writes an unsafe result');
  assert.match(sorter, /return same\(contents, out\) \? out : null;/, 'checked to hold exactly the same items');
  assert.doesNotMatch(main + sorter, /\.update\(/, 'never BlockState.update() after changing a live inventory');
  for (const token of ['FINDER_SORT player=', 'FINDER_SORT_REFUSED', 'FINDER_SORT_FAILED', 'sorts=']) assert.ok(main.includes(token), token);
});
