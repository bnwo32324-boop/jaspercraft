'use strict';
/* EasierCrafting engine (client-mods/recipe-book-teavm.js) against the real recipe table, without a
 * browser: which recipes an inventory can pay for in the 2x2 and 3x3 grids, and that every click
 * script, replayed on a model of vanilla slot clicks, fills exactly the recipe and leaves the
 * cursor empty.   node --test tests/recipe-book-engine.test.cjs
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'client-mods', 'recipe-book-teavm.js'), 'utf8');
const EMPTY = {rA: null, bg_: true};
const ctx = {Ktg: EMPTY, $rt_globals: {}, console};
vm.createContext(ctx);
vm.runInContext(source + '\nthis.RB = JasprRecipeBook; this.TABLE = JasprRecipeTable;', ctx);
const {RB, TABLE} = ctx;

// Items are identified by object identity in the client; here one object per registry id.
const items = new Map();
const item = id => { if (!items.has(id)) items.set(id, {id}); return items.get(id); };
function parse(snbt) {
  const id = /id:"([^"]+)"/.exec(snbt)[1], dmg = /Damage:(-?\d+)s/.exec(snbt);
  return {rA: item(id), bK: dmg ? +dmg[1] : 0, PD: +(/Count:(\d+)b/.exec(snbt) || [0, 1])[1], bV: /tag:\{/.test(snbt) ? {} : null};
}
const prep = RB.newPrepared();
for (const job of RB.jobs()) RB.store(prep, job, parse(job.snbt));
RB.setPrepared(prep);

const stack = (id, n, dmg = 0, tagged = false) => ({rA: item('minecraft:' + id), bK: dmg, PD: n, bV: tagged ? {} : null});
function book(grid, inventory) {
  const b = {grid, firstCraft: 1, resultSlot: 0, firstInv: grid === 3 ? 10 : 9, inventory: inventory.slice(), craftStacks: []};
  while (b.inventory.length < 36) b.inventory.push(null);
  RB.computeCraftable(b);
  return b;
}
const keys = b => new Set(Object.keys(b.craftable.set).map(i => TABLE.recipes[i].key));
const index = key => TABLE.recipes.findIndex(r => r.key === key);

/* Vanilla 1.12 PICKUP clicks on plain stacks, and "quick" on the result (recorded only). */
function replay(b, clicks, slots = null) {
  const cursor = {s: null};
  if (!slots) { slots = new Map(); b.inventory.forEach((s, i) => { if (s) slots.set(b.firstInv + i, {...s}); }); }
  const max = 64, results = [];
  for (const [slot, button, mode] of clicks) {
    if (slot === b.resultSlot) {
      results.push(mode);
      if (mode === 'pickup') { assert.equal(cursor.s, null, 'result taken with an empty cursor'); cursor.s = {rA: item('result'), bK: 0, PD: 1}; }
      continue;
    }
    const here = slots.get(slot) || null, cur = cursor.s;
    if (!cur) {
      assert.ok(here, 'click on an empty slot with an empty cursor at ' + slot);
      if (button === 0) { cursor.s = here; slots.delete(slot); }
      else { const take = Math.ceil(here.PD / 2); cursor.s = {...here, PD: take}; here.PD -= take; if (!here.PD) slots.delete(slot); }
    } else if (!here) {
      const put = button === 0 ? cur.PD : 1;
      slots.set(slot, {...cur, PD: put}); cur.PD -= put; if (!cur.PD) cursor.s = null;
    } else {
      assert.ok(here.rA === cur.rA && here.bK === cur.bK, 'click would swap different items at ' + slot);
      const put = Math.min(button === 0 ? cur.PD : 1, max - here.PD);
      here.PD += put; cur.PD -= put; if (!cur.PD) cursor.s = null;
    }
  }
  assert.equal(cursor.s, null, 'cursor empty at the end');
  return {slots, results};
}

test('the table holds every server recipe, vanilla and JasperCraft', () => {
  assert.ok(TABLE.recipes.length >= 540, 'recipes ' + TABLE.recipes.length);
  for (const key of ['minecraft:crafting_table', 'minecraft:torch', 'minecraft:furnace', 'minecraft:stone_pickaxe',
    'jasprapocalypse:sentry_turret', 'jasprapocalypse:field_guide', 'jasprgear:satchel', 'jasprgear:capacitor_belt'])
    assert.ok(index(key) >= 0, key);
  const apoc = TABLE.recipes.filter(r => r.key.startsWith('jasprapocalypse:jaspr_'));
  assert.equal(apoc.length, 89, 'all Apocalypse blueprints');
  assert.ok(apoc.every(r => r.single), 'blueprints craft with one plain click');
});

test('logs and cobblestone: planks in the 2x2, furnace only at a table', () => {
  const inv = [stack('log', 7), stack('cobblestone', 64), stack('cobblestone', 20)];
  const small = keys(book(2, inv)), table = keys(book(3, inv));
  assert.ok(small.has('minecraft:oak_planks'), 'oak planks from oak logs');
  assert.ok(!small.has('minecraft:furnace') && table.has('minecraft:furnace'), 'furnace needs the 3x3');
  assert.ok(table.has('minecraft:cobblestone_wall') && table.has('minecraft:stone_slab4') || table.has('minecraft:cobblestone_slab'));
  assert.ok(!table.has('minecraft:birch_planks'), 'no birch planks from oak logs');
});

test('torches from coal or charcoal, stone pickaxe from sticks and cobblestone', () => {
  const coal = keys(book(2, [stack('stick', 2), stack('coal', 1)]));
  const charcoal = keys(book(2, [stack('stick', 2), stack('coal', 1, 1)]));
  assert.ok(coal.has('minecraft:torch') && charcoal.has('minecraft:torch'));
  const pick = keys(book(3, [stack('stick', 1), stack('stick', 1), stack('cobblestone', 3)]));
  assert.ok(pick.has('minecraft:stone_pickaxe'), 'two sticks from two stacks');
  assert.ok(!keys(book(3, [stack('stick', 1), stack('cobblestone', 3)])).has('minecraft:stone_pickaxe'));
});

test('tagged stacks are never spent', () => {
  assert.ok(!keys(book(2, [stack('log', 7, 0, true)])).has('minecraft:oak_planks'));
});

test('custom recipes: satchel, sentry turret, a blueprint', () => {
  assert.ok(keys(book(3, [stack('leather', 6), stack('string', 2)])).has('jasprgear:satchel'));
  assert.ok(!keys(book(2, [stack('leather', 6), stack('string', 2)])).has('jasprgear:satchel'));
  assert.ok(keys(book(3, [stack('iron_ingot', 4), stack('iron_block', 1)])).has('jasprapocalypse:sentry_turret'));
  const scrap = TABLE.recipes[index('jasprapocalypse:jaspr_scrap')];
  assert.ok(scrap && scrap.count === 4);
});

test('click scripts replay exactly on a vanilla slot model', () => {
  const cases = [
    [3, [stack('cobblestone', 64), stack('stick', 1), stack('stick', 5)], 'minecraft:stone_pickaxe', false],
    [3, [stack('cobblestone', 64), stack('stick', 1), stack('stick', 5)], 'minecraft:stone_pickaxe', true],
    [2, [stack('log', 7)], 'minecraft:oak_planks', true],
    [3, [stack('planks', 3, 0), stack('planks', 30, 2), stack('planks', 7, 1)], 'minecraft:chest', true],
    [3, [stack('leather', 6), stack('string', 2), stack('dirt', 1)], 'jasprgear:satchel', false],
    [3, [stack('iron_ingot', 9), stack('iron_block', 2)], 'jasprapocalypse:sentry_turret', false],
    [2, [stack('stick', 10), stack('coal', 3), stack('coal', 40, 1)], 'minecraft:torch', true],
  ];
  for (const [grid, inv, key, shift] of cases) {
    const b = book(grid, inv), i = index(key), r = TABLE.recipes[i], before = b.inventory.slice();
    assert.ok(b.craftable.set[i], key + ' craftable');
    const clicks = RB.clickScript(b, i, 0, shift);
    assert.ok(clicks.length > 0 && clicks.every(c => c[0] !== 0), key + ' fills the grid first, output untouched');
    const {slots} = replay(b, clicks);
    // Second half, once the server shows the result: the draw loop calls finish().
    b.pending = {index: i, until: Date.now() + 1000};
    b.resultStack = null;
    assert.equal(RB.finish(b), null, key + ' waits for the result');
    b.resultStack = {rA: item('result'), bK: 0, PD: r.count, bV: null};
    b.inventory = Array.from({length: 36}, (_, n) => slots.get(b.firstInv + n) || null);
    const finish = RB.finish(b);
    assert.ok(finish && finish.length, key + ' takes the result');
    const {results} = replay(b, finish, slots);
    const first = slots.get(1 + RB.cells(r, grid)[0][0]);
    const amount = shift ? (first ? first.PD : 0) : 1;
    assert.ok(amount >= 1 && amount <= RB.maxCrafts(before, RB.cells(r, grid)), key + ' amount ' + amount);
    if (key === 'minecraft:stone_pickaxe' && shift) assert.equal(amount, 3, 'six sticks make three pickaxes');
    if (key === 'minecraft:oak_planks' && shift) assert.equal(amount, 7, 'all seven logs');
    for (const [at, choice] of RB.cells(r, grid)) {
      const cell = slots.get(1 + at);
      assert.ok(cell && cell.PD === amount && RB.accepts(choice, cell), key + ' cell ' + at + ' holds ' + amount);
    }
    const filled = [...slots.keys()].filter(s => s >= 1 && s < 1 + grid * grid).length;
    assert.equal(filled, RB.cells(r, grid).length, key + ' no stray items in the grid');
    assert.equal(results.join(), r.single ? 'pickup' : 'quick', key + (r.single ? ' taken with one plain click' : ' shift-crafted'));
  }
});

test('right click only fills; a busy grid or a missing ingredient sends nothing', () => {
  const b = book(3, [stack('cobblestone', 8)]), i = index('minecraft:furnace');
  const clicks = RB.clickScript(b, i, 1, false);
  assert.ok(clicks.length && clicks.every(c => c[0] !== 0));
  assert.equal(RB.clickScript(b, index('minecraft:stone_pickaxe'), 0, false).length, 0);
  b.craftStacks = [stack('dirt', 1)]; b.hover = i;
  assert.equal(RB.planClicks(b, -9999, -9999, 0).length, 0);
});

test('search covers every recipe, craftable or not', () => {
  const b = book(2, []);
  b.search = 'pickaxe';
  const found = RB.searchResults(b).map(i => TABLE.recipes[i].key);
  assert.ok(found.includes('minecraft:diamond_pickaxe') && found.includes('minecraft:wooden_pickaxe'));
  b.search = 'satchel';
  assert.ok(RB.searchResults(b).map(i => TABLE.recipes[i].key).includes('jasprgear:satchel'));
});
