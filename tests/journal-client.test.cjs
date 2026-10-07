'use strict';
// The Field Journal in the browser client (owner 2026-10-07: "event forecast, character summary, active item effects" in the empty
// space of the wide inventory). The stage is built from the live client, parsed, reversed byte for byte; the module is run against
// mocks of the game's own helpers with the real font's character widths: every pixel the panel draws must stay inside its frame
// whatever the data, long lists page instead of being cut off, taps do what they say, and a hostile payload changes nothing.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const stage = require('../scripts/build-journal-client.cjs');

const root = path.resolve(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const hasLive = fs.existsSync(LIVE);
const MODULE_SOURCE = fs.readFileSync(path.join(root, 'client-mods', 'journal-teavm.js'), 'latin1');

// ------------------------------------------------------------------------------------------------------ the stage
test('stage: builds on the live client, parses, reverses byte for byte and rebuilds stably', {skip: !hasLive}, () => {
  const raw = fs.readFileSync(LIVE, 'latin1');
  assert.ok(raw.includes('/*JW*/JasprWidePocketDraw(a)'), 'the live client has the wide-inventory stage this one sits on');
  const built = stage.build(raw);
  assert.equal(stage.strip(built.result), built.base);
  assert.equal(stage.apply(stage.strip(built.result)), built.result);
  assert.equal(stage.strip(built.base), built.base, 'stripping an unpatched client changes nothing');
  assert.equal(built.result.split(stage.BEGIN).length - 1, 1, 'one fenced module');
  assert.equal(built.result.split(stage.END).length - 1, 1);
  // five marked edits plus nothing else outside the fence
  const outside = built.result.slice(0, built.result.indexOf(stage.BEGIN)) + built.result.slice(built.result.indexOf(stage.END) + stage.END.length + 1);
  assert.equal(outside.split(stage.JJ).length - 1, 5, 'five marked edits');
  assert.ok(outside.length > built.base.length && outside.length - built.base.length < 700);
});

test('stage: every hook is where it must be, and every state number it adds is new in its function', {skip: !hasLive}, () => {
  const built = stage.build(fs.readFileSync(LIVE, 'latin1'));
  const body = name => { const i = built.result.indexOf('\nfunction ' + name + '(') + 1; return built.result.slice(i, built.result.indexOf('\nfunction ', i + 5)); };
  // Each hook goes right after the other stages' text, never inside it (their builders find that text again to strip it).
  assert.match(body('C6T'), /case 290:\/\*JW\*\/JasprWidePocketDraw\(a\);if\(B\(\)\)\{break _;\}\$p=2;case 2:\/\*JJ\*\/JasprJournalDraw\(a\);if\(B\(\)\)\{break _;\}\$p=291;case 291:C7\(\);/, 'drawn right after the widened window');
  assert.match(body('E8R'), /\/\*JW\*\/JasprWideHello\(b\);if\(B\(\)\)\{break _;\}d=new BO7;d\.c\$o=b;\$p=10;case 10:\/\*JJ\*\/JasprJournalHello\(b\);if\(B\(\)\)\{break _;\}\$p=292;case 292:GZM\(d\);/);
  const cyr = body('Cyr');
  assert.ok(cyr.includes('/*JJ*/if($rt_ustr(b.S$)==="jaspr:journal"){$p=2995;continue _;}if($rt_ustr(b.S$)==="JASPR|World"){$p=101;continue _;}'), 'the channel is told apart');
  assert.ok(cyr.includes('case 1963:$z=CRh(b.Wm,32767);if(B()){break _;}JasprSurround.receive($rt_ustr($z));return;case 2995:/*JJ*/$z=CRh(b.Wm,32767);if(B()){break _;}JasprJournal.receive($rt_ustr($z));return;'), 'the same readString the gear channel uses');
  assert.ok(cyr.includes('case 2990:/*JW*/JasprWideServerSaid(a);if(B()){break _;}return;case 0:if($rt_ustr(b.S$)==="jaspr:inv")'), 'the wide stage\'s text is untouched');
  const gmh = body('Gmh');
  assert.ok(gmh.includes('e=d!=(a.j.G.Rk.gO+100|0)?0:1;/*JJ*/if(JasprJournalClick(a,b,c,d))return;$p=2;case 2:$z=FBP(a,b,c);'), 'right after the super call, before the slots');
  assert.ok(gmh.includes('case 0:if(JasprGearMouseDown(a,b,c,d))return;$p=92;'), 'the gear hook is untouched');
  for (const [fn, n] of stage.NEW_STATES) assert.equal(body(fn).split('case ' + n + ':').length - 1, 1, fn + ' state ' + n);
  // The names the hooks rely on: the same helpers the wide module's own hello calls.
  const wideHello = built.result.slice(built.result.indexOf('function JasprWideHello('), built.result.indexOf('function JasprWideServerSaid('));
  for (const call of ['d = new AKy; e = new Iu;', '$z = Fru()', 'Lg(e, $z)', '$z = FuF(e, f)', 'BgN(d, ', 'g.wd(d)']) assert.ok(wideHello.includes(call), call);
  const mine = built.result.slice(built.result.indexOf('function JasprJournalHello('), built.result.indexOf('function JasprJournalDraw('));
  for (const call of ['d = new AKy; e = new Iu;', '$z = Fru()', 'Lg(e, $z)', '$z = FuF(e, f)', 'BgN(d, ', 'g.wd(d)']) assert.ok(mine.includes(call), 'journal hello: ' + call);
});

test('stage: the engine helpers the module calls exist with the shapes it assumes', {skip: !hasLive}, () => {
  const raw = fs.readFileSync(LIVE, 'latin1');
  const fn = name => { const i = raw.indexOf('\nfunction ' + name + '(') + 1; return raw.slice(i, raw.indexOf('\nfunction ', i + 5)); };
  // Static helpers are numbered from b (their first parameter would be "this"); instance methods from a.
  assert.match(fn('D49'), /^function D49\(b,c,d,e,f\)/, 'drawRect(x1, y1, x2, y2, color)');
  assert.match(fn('CA'), /^function CA\(a,b\)/, 'getStringWidth(font, string)');
  assert.ok(!/FX\(\)/.test(fn('CA').slice(0, 120)), 'getStringWidth never suspends: a plain function may call it');
  assert.match(fn('CFh'), /^function CFh\(b,c,d,e\)/, 'GlStateManager.color(r, g, b, a)');
  assert.match(fn('Cn9'), /^function Cn9\(a,b\)/, 'sendChatMessage(player, text)');
  // The statistics key sends the very same call, so a button can too.
  assert.ok(raw.includes('e=$rt_str("/stats"); $p=3;') || raw.includes('"/stats"'), 'the stats command string');
  // The font method the module draws with, as the text-fit module uses it.
  assert.ok(fs.readFileSync(path.join(root, 'client-mods', 'text-fit-teavm.js'), 'latin1').includes('ei2'), 'FontRenderer.ei2 is what text-fit draws with');
  // A2Z is the container of the survival inventory (the wide module's own note) and GuiContainer.drawScreen reads gui.J as its font.
  assert.match(fs.readFileSync(path.join(root, 'client-mods', 'wide-inventory-teavm.js'), 'latin1'), /A2Z ContainerPlayer/);
});

test('stacking: every other stage can still be rebuilt on a client that carries this one, and the stages commute', {skip: !hasLive}, () => {
  // The live client may already carry this stage (once deployed): the unpatched client is the reference.
  const live = stage.strip(fs.readFileSync(LIVE, 'latin1'));
  const withJournal = stage.build(live).result;
  for (const name of ['build-wide-inventory-client', 'build-text-fit-client', 'build-silent-effects-client', 'build-nbt-skin-client', 'build-armor-bar-client']) {
    const other = require('../scripts/' + name + '.cjs');
    const plain = other.build(live), stacked = other.build(withJournal);
    assert.equal(stage.strip(stacked.result), plain.result, name + ': rebuilding it on top of the journal stage gives the same client once the journal stage is taken off');
    assert.equal(other.strip(stacked.result).includes('JasprJournal'), true, name + ': taking that stage off leaves this one in place');
    // (The two fenced blocks may sit in either order before the footer, which is harmless: only the content is compared above.)
    assert.equal(stage.strip(stage.build(plain.result).result), plain.result, name + ': the journal stage on top of its rebuild comes off cleanly too');
  }
});

// ------------------------------------------------------------------------------------------------------- the module
/** Minecraft's default font: advance widths of the ASCII glyphs (the glyph plus one pixel). */
function advance(c) {
  if (c === ' ') return 4;
  if ('il!|.,:;\''.includes(c)) return c === '\'' ? 3 : c === 'l' ? 3 : 2;
  if ('I[]t'.includes(c)) return c === 'I' || c === '[' || c === ']' ? (c === 'I' ? 4 : 4) : 4;
  if ('fk(){}<>*"'.includes(c)) return 5;
  if (c === '`') return 3;
  if (c === '@' || c === '~') return 7;
  return 6;
}
function load(opts = {}) {
  const store = new Map(), warnings = [], calls = [];
  const scale = opts.scale || 1;
  let clock = 1000;
  class ID { } class A2Z { }
  const env = {
    $rt_globals: {localStorage: {getItem: k => (store.has(k) ? store.get(k) : null), setItem: (k, v) => { if (opts.storageFails) throw new Error('blocked'); store.set(k, String(v)); }},
      performance: {now: () => clock}, console: {warn: m => warnings.push(m)}, location: {pathname: '/x'}},
    $rt_str: s => ({java: String(s)}),
    $rt_ustr: o => o.java,
    CA: (font, j) => Math.round([...j.java].reduce((n, c) => n + advance(c), 0) * scale),
    D49: (x1, y1, x2, y2, c) => calls.push(['rect', x1, y1, x2, y2, c]),
    CFh: (r, g, b, a) => calls.push(['color', r, g, b, a]),
    Cn9: (player, text) => calls.push(['chat', player, text.java]),
    FX: () => false, Ds: () => ({l() {}, s() {}}), B: () => false, FT: () => { throw new Error('unreachable state'); },
    AKy: function () { this.kind = 'packet'; }, Iu: function () { this.kind = 'buffer'; },
    Fru: () => ({kind: 'unpooled'}), Lg: (buf, u) => { buf.u = u; }, FuF: (buf, s) => { buf.written = s.java; return buf; },
    BgN: (packet, channel, buf) => { packet.channel = channel.java; packet.buf = buf; },
    ID, A2Z, JasprWide: {pocketWidth: gui => (gui.wide === false ? 0 : 90)}
  };
  const api = new Function(...Object.keys(env), MODULE_SOURCE + '; return {JasprJournal, JasprJournalHello, JasprJournalDraw, JasprJournalClick};')(...Object.values(env));
  const font = {ei2: (s, x, y, c, shadow) => calls.push(['text', s.java, x, y, c, shadow])};
  const gui = Object.assign(new A2Z(), {});
  const screen = Object.assign(new ID(), {is: 100, l7: 40, gv: 176, gx: 166, J: font, h2: new A2Z(), j: {v: {name: 'player'}}});
  return {api, env, store, warnings, calls, font, screen, advance: s => Math.round([...s].reduce((n, c) => n + advance(c), 0) * scale),
    tick: ms => { clock += ms; }};
}
const payload = (o = {}) => JSON.stringify(Object.assign({v: 1, d: 12, ph: 'Dusk', bm: 2, iv: [1, 15], dz: [1, ''], lv: 37, xp: 20, rp: [14, 9, 45, 3, 11],
  fx: [['Water Breathing', 0], ['Haste II', 23]]}, o));

test('parse: a good payload is read, anything else is refused or bounded', () => {
  const {api} = load();
  const good = api.JasprJournal.parse(payload());
  assert.deepEqual(good, {day: 12, phase: 'Dusk', level: 37, xp: 20, moon: 2, invasion: [1, 15], disaster: [1, ''], rpg: [14, 9, 45, 3, 11], gear: [],
    fx: [{name: 'Water Breathing', seconds: 0}, {name: 'Haste II', seconds: 23}]});
  for (const bad of [null, undefined, '', '{', '[]', '"x"', 'null', '{"v":2}', '{"v":"1"}', JSON.stringify({d: 1}), 'x'.repeat(5000), payload().slice(0, 40)]) {
    assert.equal(api.JasprJournal.parse(bad), null, String(bad).slice(0, 30));
  }
  // Numbers are clamped, wrong shapes dropped, strings reduced to plain ASCII and bounded.
  const wild = api.JasprJournal.parse(JSON.stringify({v: 1, d: -5, ph: '\u00e9vil<script>' + 'x'.repeat(50), lv: 1e12, xp: 99, bm: 'x', iv: [9, -1], dz: [7, 'y'.repeat(99)],
    rp: [1, 2, 3], fx: [['A'.repeat(80), 99999], ['ok'], 5, ['Fine', -3]].concat(Array.from({length: 40}, (_, i) => ['E' + i, i]))}));
  assert.equal(wild.day, 0);
  assert.ok(wild.phase.length <= 8 && !/[^\x20-\x7e]/.test(wild.phase));
  assert.equal(wild.level, 9999);
  assert.equal(wild.xp, 40);
  assert.equal(wild.moon, -1, 'a non-number clamps to none');
  assert.deepEqual(wild.invasion, [3, 0]);
  assert.equal(wild.disaster[0], 2);
  assert.ok(wild.disaster[1].length <= 24);
  assert.equal(wild.rpg, null, 'a stat summary of the wrong length is dropped');
  assert.equal(wild.fx.length, 12);
  assert.equal(wild.fx[0].name.length, 28);
  assert.equal(wild.fx[0].seconds, 3599);
  assert.equal(wild.fx[1].name, 'Fine');
  assert.equal(wild.fx[1].seconds, 0);
  // Parts that are not sent stay null so their rows are not drawn.
  const bare = api.JasprJournal.parse('{"v":1,"d":3,"ph":"Night","lv":0,"xp":0,"fx":[]}');
  assert.deepEqual([bare.moon, bare.invasion, bare.disaster, bare.rpg], [null, null, null, null]);
});

function open(t, data, overrides) {
  t.api.JasprJournal.receive(data === undefined ? payload() : data);
  return t.api.JasprJournal.plan(t.screen);
}
const rectsOf = p => p.rects.map(r => ({x1: r[0], y1: r[1], x2: r[2], y2: r[3], c: r[4]}));

test('plan: nothing is drawn without fresh data, in the wrong window, or without the widened window', () => {
  const t = load();
  assert.equal(t.api.JasprJournal.plan(t.screen), null, 'no data yet');
  assert.equal(open(t) !== null, true);
  t.tick(59000); assert.notEqual(t.api.JasprJournal.plan(t.screen), null, 'still fresh at 59 s');
  t.tick(2000); assert.equal(t.api.JasprJournal.plan(t.screen), null, 'stale after a minute without a packet');
  t.api.JasprJournal.receive(payload()); assert.notEqual(t.api.JasprJournal.plan(t.screen), null, 'a new packet brings it back');
  const chest = Object.assign(new (class ID { })(), {is: 100, l7: 40, gv: 176, J: t.font, h2: {}});
  assert.equal(t.api.JasprJournal.plan(chest), null, 'a chest window has no panel');
  const contracted = Object.assign(t.screen, {wide: false});
  assert.equal(t.api.JasprJournal.plan(contracted), null, 'no widened window (phone, contracted view): no panel');
  delete t.screen.wide;
  assert.equal(t.api.JasprJournal.plan(null), null);
  assert.equal(t.api.JasprJournal.plan({}), null);
});

test('plan: every rectangle and every word stays inside the frame, in every tab, for any font width', () => {
  for (const scale of [1, 1.15, 1.3]) {
    const t = load({scale});
    const L = t.screen.is, T = t.screen.l7, x0 = L + 176, x1 = L + 266 - 8, y0 = T + 4, y1 = T + 81;
    for (const tab of [0, 1, 2]) {
      t.api.JasprJournal.setTab(tab);
      const p = open(t, payload({fx: Array.from({length: 12}, (_, i) => ['Resistance IV', i % 2 ? 0 : 200 + i])}));
      assert.ok(p, 'tab ' + tab);
      for (const r of rectsOf(p)) {
        assert.ok(r.x1 >= x0 && r.x2 <= x1 + 1 && r.y1 >= y0 && r.y2 <= y1 + 1, `scale ${scale} tab ${tab}: rect ${JSON.stringify(r)} leaves the frame ${[x0, y0, x1, y1]}`);
        assert.ok(r.x1 < r.x2 && r.y1 < r.y2, 'positive extent');
      }
      for (const [, s, x, y] of p.texts.map(x => ['text', x.s.java, x.x, x.y])) {
        const w = t.advance(s);
        assert.ok(x >= x0 + 1 && x + w <= x1 - 1, `scale ${scale} tab ${tab}: "${s}" (${w}px at ${x}) is cut off by the frame ${[x0 + 1, x1 - 1]}`);
        assert.ok(y >= y0 && y + 8 <= y1, `scale ${scale} tab ${tab}: "${s}" at y ${y} is outside ${[y0, y1]}`);
      }
      // Never on top of the inventory rows (the first one starts 83 px below the top of the window).
      assert.ok(Math.max(...rectsOf(p).map(r => r.y2)) <= T + 83, 'above the first inventory row');
    }
  }
});

test('plan: rows never overlap and a page holds at most six', () => {
  const t = load();
  t.api.JasprJournal.setTab(2);
  const p = open(t, payload({fx: Array.from({length: 12}, (_, i) => ['Effect number ' + i, 0])}));
  const ys = p.texts.map(x => x.y).filter(y => y > t.screen.l7 + 4 + 12);
  const rows = [...new Set(ys)].sort((a, b) => a - b);
  assert.ok(rows.length <= 7 /* six rows and the page counter */, rows.join(','));
  for (let i = 1; i < rows.length; i++) assert.ok(rows[i] - rows[i - 1] >= 8, 'rows at least 8 px apart: ' + rows.join(','));
  assert.ok(p.texts.some(x => /^1\/\d+ tap$/.test(x.s.java)), 'a page counter says there is more');
});

test('tabs: the strip fills the inset, labels fit their boxes, narrow fonts fall back to short labels', () => {
  for (const scale of [1, 1.2, 1.5, 2]) {
    const t = load({scale});
    const p = open(t);
    const L = t.screen.is, x0 = L + 176, x1 = L + 258;
    const labels = p.texts.filter(x => x.y === t.screen.l7 + 4 + 1 + 2);
    assert.equal(labels.length, 3, 'three labels at scale ' + scale);
    const boxes = p.hits.tabs;
    assert.equal(boxes[0][0], x0 + 1);
    assert.equal(boxes[2][2], x1 - 1, 'the strip reaches the right edge');
    for (let i = 0; i < 3; i++) {
      if (i) assert.equal(boxes[i][0], boxes[i - 1][2], 'no gap between tabs');
      const w = t.advance(labels[i].s.java);
      assert.ok(labels[i].x >= boxes[i][0] && labels[i].x + w <= boxes[i][2], `label "${labels[i].s.java}" fits its box at scale ${scale}`);
    }
    if (scale === 1.2 || scale === 1.5) assert.deepEqual(labels.map(l => l.s.java), ['Soon', 'Me', 'Fx'], 'short labels when the full ones cannot fit');
    if (scale === 2) assert.deepEqual(labels.map(l => l.s.java), ['S', 'Y', 'P'], 'single letters when even those cannot');
    if (scale === 1) assert.deepEqual(labels.map(l => l.s.java), ['Soon', 'You', 'Perks'], 'the real font shows the full labels');
  }
});

test('content: the Soon tab reads right for each state', () => {
  const t = load();
  const lines = data => { const p = open(t, data); return p.texts.map(x => x.s.java).join('|'); };
  t.api.JasprJournal.setTab(0);
  const base = lines(payload());
  // Blood Moon, Invasion and Disaster each take two rows at the real font's widths (label, then value), and the day is the footer.
  assert.match(base, /Blood Moon\|in 2 nights\|Invasion\|from day 15\|Disaster\|brewing\|Day 12 \|Dusk$/);
  assert.match(lines(payload({bm: 0})), /Blood Moon\|tonight/);
  assert.match(lines(payload({bm: 1})), /Blood Moon\|tomorrow/);
  assert.match(lines(payload({bm: -2})), /Blood Moon\|NOW!/);
  assert.match(lines(payload({bm: -1})), /Blood Moon\|none/);
  assert.match(lines(payload({iv: [0, 0]})), /Invasion\|none marked/);
  assert.match(lines(payload({iv: [2, 0]})), /Invasion\|any night/);
  assert.match(lines(payload({iv: [3, 0]})), /Invasion\|UNDER WAY/);
  assert.match(lines(payload({dz: [0, '']})), /Disaster ?\|quiet/);
  assert.match(lines(payload({dz: [2, 'Tornado']})), /Tornado now!/);
  // Worst case: every row long, a disaster running with a long name: still one page (no "tap"), nothing hidden.
  const worst = lines(payload({bm: 5, iv: [2, 0], dz: [2, 'Thunder-Hell Storm']}));
  assert.ok(!/tap/.test(worst), worst);
  assert.match(worst, /Thunder-Hell\|Storm now!/);
  assert.equal(open(t, payload({bm: 5, iv: [2, 0], dz: [2, 'Thunder-Hell Storm']})).hits.pages, 1);
  // Rows of parts the server does not run are simply absent.
  const bare = lines(JSON.stringify({v: 1, d: 3, ph: 'Night', lv: 4, xp: 5, fx: []}));
  assert.equal(bare.includes('Blood Moon'), false);
  assert.equal(bare.includes('Invasion'), false);
  assert.equal(bare.includes('Disaster'), false);
  assert.match(bare, /Day 3 \|Night$/);
});

test('content: the You tab, the Perks tab and countdowns that run on the client', () => {
  const t = load();
  t.api.JasprJournal.setTab(1);
  let p = open(t);
  let all = p.texts.map(x => x.s.java).join('|');
  assert.match(all, /Level \|37\|14 ranks\|9\/45 stats\|Raise 3 now\|Open Stats/);
  assert.ok(p.hits.button && p.hits.button.command === '/stats', 'the button sends /stats');
  p = open(t, payload({rp: [0, 0, 45, 0, 6]}));
  assert.match(p.texts.map(x => x.s.java).join('|'), /next: 6 lv/);
  p = open(t, payload({rp: [200, 45, 45, 0, -1]}));
  assert.match(p.texts.map(x => x.s.java).join('|'), /200 ranks\|45\/45 stats\|all maxed/);
  assert.equal(p.hits.pages, 1, 'the You tab always fits one page');
  p = open(t, JSON.stringify({v: 1, d: 3, ph: 'Night', lv: 4, xp: 5, fx: []}));
  all = p.texts.map(x => x.s.java).join('|');
  assert.match(all, /Level \|4/);
  assert.equal(all.includes('ranks'), false, 'no stat sheet, no stat rows');
  // The experience bar: 5 of 40 filled out of the bar's width.
  const bar = rectsOf(p).filter(r => r.c === (0xFF55FF55 | 0) && r.y2 - r.y1 === 3);
  assert.equal(bar.length, 1);
  assert.equal(bar[0].x2 - bar[0].x1, Math.round((p.hits.frame[2] - p.hits.frame[0] - 2 - 6) * 5 / 40) - 1);

  t.api.JasprJournal.setTab(2);
  p = open(t);
  all = p.texts.map(x => x.s.java).join('|');
  assert.match(all, /Water\|Breathing\|Haste II \|0:23/, 'a name wider than the panel wraps onto a second row, never cut off');
  t.tick(10000);
  p = t.api.JasprJournal.plan(t.screen);
  assert.match(p.texts.map(x => x.s.java).join('|'), /Haste II \|0:13/, 'the countdown runs on the client between packets');
  t.tick(14000);
  t.api.JasprJournal.receive(payload({fx: [['Haste II', 23]]}));
  t.tick(25000);
  assert.equal(t.api.JasprJournal.plan(t.screen).texts.map(x => x.s.java).join('|').includes('Haste'), false, 'an expired effect is dropped');
  p = open(t, payload({fx: []}));
  assert.match(p.texts.map(x => x.s.java).join('|'), /Nothing active\|Worn trinkets\|and item\|effects are\|listed here\./);
  assert.equal(p.hits.pages, 1);
});

/** The seven trinkets of the gear column with their real headline effects (the first line of each item's tooltip). */
const WORN = [['Worn Teddy Bear', 'Sneak still to rest and heal'], ['Thermal Goggles', 'Immune to burning, -50% lava damage'], ['Scrap Magnet', '[J] Magnet: pull items and XP (7m)'],
  ['Capacitor Belt', '+10% speed; melee hits may discharge'], ['Phase Headset', '[H] Blink 8m, sneak+[H] ender chest'], ['Riot Vest', 'Plates absorb 6 damage, then recharge'],
  ['Rebreather', 'Breathe underwater, no blight poison']];

test('perks: worn trinkets are listed with their headline effect, kept together, paged, nothing cut off (the owner\'s report)', () => {
  for (const scale of [1, 1.15]) {
    const t = load({scale});
    t.api.JasprJournal.setTab(2);
    const L = t.screen.is, x0 = L + 176, x1 = L + 258;
    let p = open(t, payload({gw: WORN, fx: []}));
    assert.ok(p.hits.pages >= 4 && p.hits.pages <= 8, 'seven trinkets take a few pages: ' + p.hits.pages);
    const seen = [];
    for (let pg = 0; pg < p.hits.pages; pg++) {
      const lines = p.texts.filter(x => x.y > t.screen.l7 + 4 + 12).map(x => x.s.java);
      seen.push(lines.filter(s => !/^\d+\/\d+ tap$/.test(s)));
      for (const x of p.texts) {
        const w = t.advance(x.s.java);
        assert.ok(x.x >= x0 + 1 && x.x + w <= x1 - 1, `scale ${scale} page ${pg}: "${x.s.java}" is cut off`);
      }
      // A page never starts with a trinket's effect line without its title: the title is the aqua row above it.
      const rows = p.texts.filter(x => x.y > t.screen.l7 + 4 + 12 && !/^\d+\/\d+ tap$/.test(x.s.java));
      assert.ok(rows.length <= 6, 'six rows at most');
      if (pg < p.hits.pages - 1) { const body = p.hits.body; t.api.JasprJournalClick(t.screen, body[0] + 3, body[1] + 3, 0); p = t.api.JasprJournal.plan(t.screen); }
    }
    const all = seen.flat().join(' ');
    for (const [title] of WORN) for (const word of title.split(' ')) assert.ok(all.includes(word), `"${word}" of ${title} is shown at scale ${scale}`);
    // Every title is followed (on the same page) by the start of its own effect line: groups are never split when they fit.
    for (let pg = 0; pg < seen.length; pg++) {
      const first = seen[pg][0] || '';
      const isEffect = WORN.some(([, e]) => e.startsWith(first) && first.length > 0);
      assert.ok(!isEffect || scale !== 1, `scale ${scale}: page ${pg} does not start in the middle of a trinket ("${first}")`);
    }
  }
});

test('perks: trinkets and effects together, trinkets first; the empty message only when there is truly nothing', () => {
  const t = load();
  t.api.JasprJournal.setTab(2);
  let p = open(t, payload({gw: [WORN[0]], fx: [['Haste II', 0]]}));
  let all = p.texts.map(x => x.s.java).join('|');
  assert.match(all, /Worn Teddy\|Bear\|Sneak still to\|rest and heal\|Haste II/, all);
  assert.equal(all.includes('Nothing active'), false);
  p = open(t, payload({gw: [], fx: []}));
  assert.match(p.texts.map(x => x.s.java).join('|'), /Nothing active/);
  p = open(t, payload({gw: [WORN[2]], fx: []}));
  assert.equal(p.texts.map(x => x.s.java).join('|').includes('Nothing active'), false, 'one worn trinket is not "nothing"');
  // Hostile data: wrong shapes, too many, markup: bounded and plain.
  const wild = t.api.JasprJournal.parse(JSON.stringify({v: 1, d: 1, ph: 'Day', lv: 1, xp: 1, fx: [], gw: [['A', 'b'], ['x'], 5, null, ['<b>' + 'T'.repeat(80), 'e'.repeat(200)]].concat(Array.from({length: 20}, (_, i) => ['G' + i, 'e']))}));
  assert.equal(wild.gear.length, 8);
  assert.equal(wild.gear[0].title, 'A');
  assert.ok(wild.gear[1].title.length <= 28 && wild.gear[1].effect.length <= 60);
  assert.equal(t.api.JasprJournal.parse('{"v":1,"d":1,"ph":"Day","lv":1,"xp":1,"fx":[],"gw":"nope"}').gear.length, 0);
});

test('input: tabs, the button, pages; clicks on the panel are used up and others pass', () => {
  const t = load();
  const J = t.api.JasprJournal;
  t.api.JasprJournal.setTab(0);
  const twelve = payload({fx: Array.from({length: 12}, (_, i) => ['Perk ' + i, 0])});
  let p = open(t, twelve);
  const [tab0, tab1, tab2] = p.hits.tabs;
  const mid = b => [Math.floor((b[0] + b[2]) / 2), Math.floor((b[1] + b[3]) / 2)];
  assert.equal(t.api.JasprJournalClick(t.screen, ...mid(tab1), 0), 1, 'a tap on a tab is used up');
  assert.equal(J.tab(), 'you');
  assert.equal(t.store.get('jaspr.journal.tab.v1'), 'you', 'the choice is kept');
  p = J.plan(t.screen);
  // The button.
  const b = p.hits.button.box, hit = p.hits.button.hit;
  assert.ok(hit[3] - hit[1] > b[3] - b[1] + 4 && hit[3] - hit[1] >= 16, 'the tap target of the button is taller than the button: ' + (hit[3] - hit[1]));
  assert.equal(t.api.JasprJournalClick(t.screen, b[0] + 2, hit[3] - 2, 0), 1, 'a tap below the drawn button still presses it');
  const cmd = J.takeCommand();
  assert.deepEqual(cmd, {java: '/stats'});
  assert.equal(J.takeCommand(), null, 'a press sends one command, once');
  // Pages on the Perks tab.
  t.api.JasprJournalClick(t.screen, ...mid(J.plan(t.screen).hits.tabs[2]), 0);
  p = J.plan(t.screen);
  assert.equal(p.hits.pages, 2);
  const body = p.hits.body, before = p.texts.map(x => x.s.java).join('|');
  assert.equal(t.api.JasprJournalClick(t.screen, body[0] + 3, body[1] + 3, 0), 1);
  const after = J.plan(t.screen).texts.map(x => x.s.java).join('|');
  assert.notEqual(before, after, 'a tap on the list turns the page');
  assert.match(after, /Perk 6/);
  assert.match(after, /2\/2 tap/);
  // After the last page a tap flips to the next tab (here round to the first): the big target for a finger.
  // (A frame is drawn between two taps, as in the game: the panel is planned again after every change.)
  const tap = () => { J.plan(t.screen); assert.equal(t.api.JasprJournalClick(t.screen, body[0] + 3, body[1] + 3, 0), 1); };
  tap();
  assert.equal(J.tab(), 'soon', 'after the last page the next tab opens');
  assert.equal(J.status().page, 0);
  tap();
  assert.equal(J.tab(), 'you', 'and every tap on a one-page tab flips on');
  tap(); tap();
  assert.equal(J.tab(), 'perks', 'soon, you, perks ...');
  tap(); tap();
  assert.equal(J.tab(), 'you', 'perks has two pages: one tap per page, then round to the first tab');
  t.api.JasprJournal.setTab(2);
  // Other buttons: used up, nothing happens. Outside the frame: passed on to the slots.
  J.plan(t.screen);
  const f = J.plan(t.screen).hits.frame;
  const tabBefore = J.tab();
  assert.equal(t.api.JasprJournalClick(t.screen, f[0] + 3, f[1] + 20, 1), 1, 'a right click on the panel is used up too');
  assert.equal(J.tab(), tabBefore, 'and does nothing');
  assert.equal(t.api.JasprJournalClick(t.screen, f[0] - 2, f[1] + 20, 0), 0, 'left of the frame: a slot click');
  assert.equal(t.api.JasprJournalClick(t.screen, f[2] + 1, f[1] + 20, 0), 0);
  assert.equal(t.api.JasprJournalClick(t.screen, f[0] + 5, f[3] + 3, 0), 0, 'below the frame: the inventory rows');
  assert.equal(t.api.JasprJournalClick(t.screen, f[0] + 5, f[1] - 1, 0), 0, 'above it');
  // Without data nothing is used up.
  const cold = load();
  assert.equal(cold.api.JasprJournalClick(cold.screen, 280, 60, 0), 0);
  assert.equal(cold.api.JasprJournalClick(null, 1, 1, 0), 0);
});

test('input: a blocked localStorage and a window that changed size are survived', () => {
  const t = load({storageFails: true});
  open(t);
  t.api.JasprJournal.setTab(2);
  assert.equal(t.api.JasprJournal.tab(), 'perks');
  assert.equal(t.warnings.length, 0);
  // The window moves (the screen was resized): the panel follows, and a click hits where it is now.
  t.screen.is = 37;
  const p = t.api.JasprJournal.plan(t.screen);
  assert.ok(p.hits.frame[0] === 37 + 176);
  const b = p.hits.tabs[0];
  assert.equal(t.api.JasprJournalClick(t.screen, b[0] + 1, b[1] + 1, 0), 1);
  assert.equal(t.api.JasprJournal.tab(), 'soon');
});

// ------------------------------------------------------------------------------------- the resumable entry points
test('draw: rectangles first, then text, then a pending command, then the colour back to white', () => {
  const t = load();
  open(t);
  t.api.JasprJournal.setTab(1);
  t.api.JasprJournal.receive(payload());
  const plan = t.api.JasprJournal.plan(t.screen);
  t.calls.length = 0;
  const b = plan.hits.button.box;
  t.api.JasprJournalClick(t.screen, b[0] + 2, b[1] + 2, 0);
  t.api.JasprJournalDraw(t.screen);
  const kinds = t.calls.map(c => c[0]);
  assert.equal(kinds.filter(k => k === 'rect').length, plan.rects.length);
  assert.equal(kinds.filter(k => k === 'text').length, plan.texts.length);
  assert.equal(kinds.filter(k => k === 'chat').length, 1);
  assert.deepEqual(t.calls.find(c => c[0] === 'chat'), ['chat', t.screen.j.v, '/stats'], 'sent as the player, like the stats key');
  assert.equal(kinds[kinds.length - 1], 'color');
  assert.ok(kinds.lastIndexOf('rect') < kinds.indexOf('text'), 'all rectangles before any text');
  assert.ok(kinds.indexOf('text') < kinds.indexOf('chat'), 'the command last');
  // The next frame draws again but sends nothing.
  t.calls.length = 0;
  t.api.JasprJournalDraw(t.screen);
  assert.equal(t.calls.filter(c => c[0] === 'chat').length, 0);
  // No data: no drawing, no colour change.
  const cold = load();
  cold.api.JasprJournalDraw(cold.screen);
  assert.deepEqual(cold.calls, []);
});

test('hello: a new connection starts without data and says hello on jaspr:journal', () => {
  const t = load();
  open(t);
  const handler = {qf: {wd(packet) { handler.sent = packet; }, bkf: false}};
  t.api.JasprJournalHello(handler);
  assert.equal(handler.sent.channel, 'jaspr:journal');
  assert.equal(handler.sent.buf.written, 'hello 1');
  assert.equal(t.api.JasprJournal.plan(t.screen), null, 'the old connection\'s data is gone');
  assert.equal(t.api.JasprJournal.status().stats.hellos, 1);
  const closed = {qf: {wd() { throw new Error('must not send'); }, bkf: true}};
  t.api.JasprJournalHello(closed);
  t.api.JasprJournalHello({});
  t.api.JasprJournalHello(null);
  assert.equal(t.api.JasprJournal.status().stats.hellos, 1, 'a closed or missing connection says nothing');
});

test('safety: the module is ASCII, never uses innerHTML or eval, and keeps the packet to JSON.parse of bounded text', () => {
  assert.ok(!/[^\x00-\x7f]/.test(MODULE_SOURCE));
  assert.doesNotMatch(MODULE_SOURCE, /innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(|new Function|setTimeout|setInterval|XMLHttpRequest|WebSocket/);
  assert.match(MODULE_SOURCE, /json\.length > 4096/);
  // Top level: comments, one var, and function declarations only (the hooks may run before the block is reached).
  const top = MODULE_SOURCE.split('\n').filter(l => /^\S/.test(l) && !/^(\/\*|\s*\*|\}|function |var )/.test(l));
  assert.deepEqual(top, []);
});
