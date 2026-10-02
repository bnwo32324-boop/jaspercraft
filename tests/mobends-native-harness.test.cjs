'use strict';
// Mo' Bends native harness (scripts/mobends-native-harness.cjs): the live TeaVM client loads in node:vm without a
// browser, WebGL or the game's main, and the minified engine names the Mo' Bends port relies on build real models.
// vanillaBox() below is an independent reference for the 1.12.2 ModelBox / TexturedQuad layout, written from the Java
// source, so the engine's boxes are checked against vanilla math rather than against themselves.
//
// The client is the live one next to this worktree (read-only), MOBENDS_CLASSES_JS overrides it, and anywhere else
// the repo's own site/classes.js is used.
const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {loadClient, loadClientSource} = require('../scripts/mobends-native-harness.cjs');

const root = path.resolve(__dirname, '..');
const LIVE = path.resolve(root, '..', 'Eaglercraft-1.12.2-Tailscale', 'site', 'classes.js');
const CLASSES = process.env.MOBENDS_CLASSES_JS || (fs.existsSync(LIVE) ? LIVE : path.join(root, 'site', 'classes.js'));

const nodeTimers = () => process.getActiveResourcesInfo().filter((r) => r === 'Timeout' || r === 'Immediate').length;

let client;
let fn;
let timersBefore;
let timersAfter;
before(() => {
  timersBefore = nodeTimers();
  client = loadClient(CLASSES);
  timersAfter = nodeTimers();
  fn = client.fn;
});
after(() => { if (client) client.dispose(); });

/** Vanilla ModelBox(renderer, texU, texV, x, y, z, dx, dy, dz, delta, mirror) and its six TexturedQuads. */
function vanillaBox({texU, texV, x, y, z, dx, dy, dz, delta, mirror, texW, texH}) {
  let f = x + dx;
  let f1 = y + dy;
  let f2 = z + dz;
  const box = {bounds: [x, y, z, f, f1, f2]};
  x -= delta; y -= delta; z -= delta; f += delta; f1 += delta; f2 += delta;
  if (mirror) [x, f] = [f, x];
  const vertex = (px, py, pz, u, v) => ({pos: [px, py, pz], uv: [u, v]});
  const p7 = vertex(x, y, z, 0, 0);
  const p0 = vertex(f, y, z, 0, 8);
  const p1 = vertex(f, f1, z, 8, 8);
  const p2 = vertex(x, f1, z, 8, 0);
  const p3 = vertex(x, y, f2, 0, 0);
  const p4 = vertex(f, y, f2, 0, 8);
  const p5 = vertex(f, f1, f2, 8, 8);
  const p6 = vertex(x, f1, f2, 8, 0);
  box.vertices = [p7, p0, p1, p2, p3, p4, p5, p6];
  // TexturedQuad(vertices, u1, v1, u2, v2, w, h) gives the corners (u2,v1) (u1,v1) (u1,v2) (u2,v2); mirror = flipFace().
  const quad = (corners, u1, v1, u2, v2) => {
    const uv = [[u2, v1], [u1, v1], [u1, v2], [u2, v2]];
    const out = corners.map((p, i) => ({pos: p.pos, uv: [uv[i][0] / texW, uv[i][1] / texH]}));
    return mirror ? out.reverse() : out;
  };
  box.quads = [
    quad([p4, p0, p1, p5], texU + dz + dx, texV + dz, texU + dz + dx + dz, texV + dz + dy),
    quad([p7, p3, p6, p2], texU, texV + dz, texU + dz, texV + dz + dy),
    quad([p4, p3, p7, p0], texU + dz, texV, texU + dz + dx, texV + dz),
    quad([p1, p2, p6, p5], texU + dz + dx, texV + dz, texU + dz + dx + dx, texV),
    quad([p0, p7, p2, p1], texU + dz, texV + dz, texU + dz + dx, texV + dz + dy),
    quad([p3, p4, p5, p6], texU + dz + dx + dz, texV + dz, texU + dz + dx + dz + dx, texV + dz + dy),
  ];
  return box;
}

const round = (n) => Math.round(n * 1e6) / 1e6 + 0;
const engineVertex = (v) => ({pos: [v.Kj.bh, v.Kj.bq, v.Kj.bi].map(round), uv: [v.cDP, v.cDQ].map(round)});
const referenceVertex = ({pos, uv}) => ({pos: pos.map(round), uv: uv.map(round)});

function assertVanillaBox(box, spec, label) {
  const ref = vanillaBox(spec);
  assert.ok(box instanceof fn.DZG, label + ': a ModelBox');
  assert.deepEqual([box.dsk, box.dsh, box.dse, box.dsl, box.dsi, box.dsf].map(round), ref.bounds.map(round), label + ': bounds');
  assert.deepEqual(Array.from(box.dSQ.data, engineVertex), ref.vertices.map(referenceVertex), label + ': vertexPositions');
  assert.deepEqual(Array.from(box.a4t.data, (q) => Array.from(q.a4R.data, engineVertex)),
    ref.quads.map((q) => q.map(referenceVertex)), label + ': quads');
}

const biped = () => { const model = new fn.OB(); fn.AB4(model, 0, 0, 64, 32); return model; };
const firstBox = (part) => fn.Bm(part.a6Y, 0);

test('the live client loads in node:vm: no browser, no WebGL, main never runs', (t) => {
  t.diagnostic('client ' + client.file);
  t.diagnostic(`load ${Math.round(client.loadMs)} ms (read + scan + compile + evaluate; evaluation alone ${Math.round(client.evalMs)} ms)`);
  t.diagnostic(`profile ${client.profile}; ${client.names.length} top-level bindings; ${client.classes.size} classes kept their Java names`);
  t.diagnostic('stubbed globals read during load: ' + (client.stubs.touchedDuringLoad.join(', ') || 'none'));
  if (client.fallbackError) t.diagnostic('the minimal profile failed first: ' + client.fallbackError.split('\n')[0]);
  assert.ok(client.names.length > 30000, 'the minified top level is exposed');
  assert.equal(typeof fn.OB, 'function');
  assert.equal(fn.G, fn.$rt_createArray);
  assert.equal(fn.T, fn.$rt_createArrayFromData, 'T is declared across a line break ("var T\\n=") and still found');
  assert.equal(typeof client.context.main, 'function', 'TeaVM exported its entry point ...');
  assert.equal('main' in fn, false, '... which is a global, not a module binding');
  assert.deepEqual(client.pending(), {timers: 0, frames: 0, idle: 0, microtasks: 0}, 'nothing scheduled: main and its threads never started');
  assert.equal(timersAfter, timersBefore, 'loading created no Node timer');
  assert.deepEqual(client.logs.filter((entry) => entry.level === 'error'), [], 'no console errors during load');
  assert.equal(fn.Lt6, null, 'ModelBiped.ArmPose.EMPTY is not initialised before first use');
});

test('class names and hierarchy behind the minified names', () => {
  assert.equal(client.classes.get('net.minecraft.client.model.ModelRenderer'), 'M2');
  assert.equal(client.classes.get('net.minecraft.client.model.ModelBox'), 'DZG');
  assert.equal(client.classes.get('net.minecraft.util.math.Vec3d'), 'ET');
  assert.equal(client.classes.get('java.util.ArrayList'), 'Kn');
  assert.equal(client.classes.get('net.minecraft.client.model.ModelBiped$ArmPose'), 'AGu');
  assert.equal(client.nameOf(fn.OB.$meta.superclass), 'DQ', 'ModelBiped extends ModelBase (DQ)');
  assert.equal(fn.BVS.$meta.superclass, fn.OB, 'ModelPlayer extends ModelBiped');
  for (const name of ['DQ', 'M2', 'DZG', 'Bgq', 'Xb', 'ET']) assert.equal(fn[name].$meta.superclass, fn.D, name + ' extends Object');
});

test('ModelBase init (Gs): boxList and a 64x32 texture', () => {
  const base = new fn.DQ();
  fn.Gs(base);
  assert.deepEqual([base.vI, base.vd], [64, 32]);
  assert.ok(base.cJ9 instanceof fn.Kn, 'boxList is a java.util.ArrayList');
  assert.equal(base.cJ9.g, 0);
});

test('ModelBiped (OB + AB4) builds the seven vanilla parts', () => {
  const model = biped();
  assert.ok(model instanceof fn.DQ);
  assert.deepEqual([model.vI, model.vd], [64, 32]);
  const parts = ['lA', 'Ea', 'k_', 'gM', 'f3', 'mD', 'nc'];        // head, headwear, body, right/left arm, right/left leg
  assert.equal(model.cJ9.g, 7, 'seven parts in boxList');
  parts.forEach((field, i) => {
    const part = model[field];
    assert.ok(part instanceof fn.M2, field + ' is a ModelRenderer');
    assert.equal(fn.Bm(model.cJ9, i), part, field + ' is boxList[' + i + ']');
    assert.equal(part.ddy, model, field + ': baseModel');
    assert.deepEqual([part.bdO, part.bby], [64, 32], field + ': texture size');
    assert.equal(part.a6Y.g, 1, field + ': one box');
    assert.deepEqual([part.eT, part.cIT, part.A, part.bb, part.bX], [1, 0, 0, 0, 0], field + ': shown, not hidden, unrotated');
    assert.deepEqual([part.OS, part.clh, part.bWg], [null, 0, 0], field + ': no children, not compiled, no display list');
  });
  const f = Math.fround;
  const layout = {   // texture offset, rotation point, mirror -- vanilla ModelBiped(0, 0, 64, 32)
    lA: [0, 0, 0, 0, 0, 0], Ea: [32, 0, 0, 0, 0, 0], k_: [16, 16, 0, 0, 0, 0], gM: [40, 16, -5, 2, 0, 0],
    f3: [40, 16, 5, 2, 0, 1], mD: [0, 16, f(-1.9), 12, 0, 0], nc: [0, 16, f(1.9), 12, 0, 1],
  };
  for (const [field, expected] of Object.entries(layout)) {
    const p = model[field];
    assert.deepEqual([p.bH9, p.bH$, p.cD, p.bs, p.bA, p.i$], expected, field + ': texture offset, rotation point, mirror');
  }
  assert.ok(fn.Lt6 instanceof fn.AGu, 'ArmPose.EMPTY was initialised on first use (lazy clinit) and fn sees it');
  assert.deepEqual([model.a2N, model.a6t], [fn.Lt6, fn.Lt6], 'left/right arm pose = EMPTY');
  assert.equal(String(fn.A3n), 'function(){}', 'the clinit trampoline replaced itself; fn follows the live binding');
});

test('right arm: one box (-3,-2,-2)..(1,10,2), 6 quads of 4 vertices, vanilla UVs', () => {
  const model = biped();
  const box = firstBox(model.gM);
  assert.ok(box instanceof fn.DZG);
  assert.deepEqual([box.dsk, box.dsh, box.dse, box.dsl, box.dsi, box.dsf], [-3, -2, -2, 1, 10, 2]);
  assert.equal(box.dSQ.data.length, 8);
  assert.equal(box.a4t.data.length, 6);
  for (const quad of box.a4t.data) {
    assert.ok(quad instanceof fn.Bgq);
    assert.deepEqual([quad.emm, quad.a4R.data.length, quad.ecB], [4, 4, 0]);
    for (const vertex of quad.a4R.data) assert.ok(vertex instanceof fn.Xb && vertex.Kj instanceof fn.ET);
  }
  // Quad 0 on the 64x32 texture at offset (40,16): u from 40+4+4=48 to 52, v from 16+4=20 to 32.
  assert.deepEqual(Array.from(box.a4t.data[0].a4R.data, (v) => [v.cDP, v.cDQ]),
    [[52 / 64, 20 / 32], [48 / 64, 20 / 32], [48 / 64, 32 / 32], [52 / 64, 32 / 32]]);
  assertVanillaBox(box, {texU: 40, texV: 16, x: -3, y: -2, z: -2, dx: 4, dy: 12, dz: 4, delta: 0, mirror: false, texW: 64, texH: 32}, 'right arm');
  assert.equal(box.a4t.data[0].a4R.data[0].Kj, box.dSQ.data[5].Kj, 'quad vertices share the box vertex positions');
  assertVanillaBox(firstBox(model.f3), {texU: 40, texV: 16, x: -1, y: -2, z: -2, dx: 4, dy: 12, dz: 4, delta: 0, mirror: true, texW: 64, texH: 32}, 'mirrored left arm');
  assertVanillaBox(firstBox(model.Ea), {texU: 32, texV: 0, x: -4, y: -8, z: -4, dx: 8, dy: 8, dz: 8, delta: 0.5, mirror: false, texW: 64, texH: 32}, 'inflated headwear');
});

test('ModelRenderer API: BX registers a part, CH / B$ add boxes, HV adds a child', () => {
  const base = new fn.DQ();
  fn.Gs(base);
  const part = fn.BX(base, 8, 4);
  assert.ok(part instanceof fn.M2);
  assert.equal(base.cJ9.g, 1);
  assert.equal(fn.Bm(base.cJ9, 0), part);
  assert.equal(part.ddy, base);
  assert.deepEqual([part.bH9, part.bH$, part.bdO, part.bby, part.a6Y.g, part.i$], [8, 4, 64, 32, 0, 0]);
  const spec = {texU: 8, texV: 4, delta: 0, mirror: false, texW: 64, texH: 32};
  assert.equal(fn.CH(part, -1, -2, -3, 2, 4, 6), part, 'CH (addBox) returns the renderer');
  assert.equal(part.a6Y.g, 1);
  assertVanillaBox(fn.Bm(part.a6Y, 0), {...spec, x: -1, y: -2, z: -3, dx: 2, dy: 4, dz: 6}, 'CH box');
  fn.B$(part, 0, 0, 0, 1, 1, 1, 0.5);
  assert.equal(part.a6Y.g, 2);
  assertVanillaBox(fn.Bm(part.a6Y, 1), {...spec, x: 0, y: 0, z: 0, dx: 1, dy: 1, dz: 1, delta: 0.5}, 'B$ box with delta');
  part.i$ = 1;                                                   // the renderer's mirror flag applies to boxes added later
  fn.CH(part, 0, 0, 0, 3, 2, 1);
  assertVanillaBox(fn.Bm(part.a6Y, 2), {...spec, x: 0, y: 0, z: 0, dx: 3, dy: 2, dz: 1, mirror: true}, 'mirrored CH box');
  const child = fn.BX(base, 0, 0);
  assert.equal(part.OS, null, 'childModels starts null');
  fn.HV(part, child);
  assert.ok(part.OS instanceof fn.Kn);
  assert.equal(part.OS.g, 1);
  assert.equal(fn.Bm(part.OS, 0), child);
  assert.equal(base.cJ9.g, 2, 'children register in the model boxList too');
});

test('TeaVM arrays and java.util.ArrayList helpers (G, T, Bq, Y, Bm)', () => {
  const empty = fn.G(fn.Xb, 3);
  assert.equal(empty.type, fn.Xb);
  assert.ok(Array.isArray(empty.data));
  assert.deepEqual(Array.from(empty.data), [null, null, null]);
  const v = fn.AMs(0, 0, 0, 0, 0);
  const filled = fn.T(fn.Xb, [v, v]);
  assert.equal(filled.type, fn.Xb);
  assert.ok(Array.isArray(filled.data));
  assert.equal(filled.data.length, 2);
  assert.equal(filled.data[1], v);
  assert.equal(filled.constructor.$meta.item, fn.Xb, 'an array class of PositionTextureVertex');
  const list = fn.Bq();
  assert.ok(list instanceof fn.Kn);
  assert.equal(list.g, 0);
  for (let i = 0; i < 12; i++) assert.equal(fn.Y(list, 'item' + i), 1, 'add returns true');   // grows past 10
  assert.equal(list.g, 12);
  assert.equal(fn.Bm(list, 11), 'item11');
  assert.equal(list.qN.data[0], 'item0');
  assert.throws(() => fn.Bm(list, 12), 'get past the end throws');
});

test('PositionTextureVertex (AMs, AY_), TexturedQuad (A5Y) and ModelBox (Ehl, F2d)', () => {
  const v = fn.AMs(1, 2, 3, 0.25, 0.5);
  assert.ok(v instanceof fn.Xb && v.Kj instanceof fn.ET);
  assert.deepEqual([v.Kj.bh, v.Kj.bq, v.Kj.bi, v.cDP, v.cDQ], [1, 2, 3, 0.25, 0.5]);
  const moved = fn.AY_(v, 0.75, 1);
  assert.notEqual(moved, v);
  assert.equal(moved.Kj, v.Kj, 'setTexturePosition keeps the position vector');
  assert.deepEqual([moved.cDP, moved.cDQ, v.cDP, v.cDQ], [0.75, 1, 0.25, 0.5], 'and leaves the original alone');
  const corners = [fn.AMs(0, 0, 0, 0, 0), fn.AMs(1, 0, 0, 0, 0), fn.AMs(1, 1, 0, 0, 0), fn.AMs(0, 1, 0, 0, 0)];
  const quad = fn.A5Y(fn.T(fn.Xb, corners), 4, 8, 12, 24, 64, 32);
  assert.ok(quad instanceof fn.Bgq);
  assert.deepEqual([quad.emm, quad.ecB], [4, 0]);
  assert.deepEqual(Array.from(quad.a4R.data, (x) => [x.cDP, x.cDQ]), [[12 / 64, 8 / 32], [4 / 64, 8 / 32], [4 / 64, 24 / 32], [12 / 64, 24 / 32]]);
  assert.ok(Array.from(quad.a4R.data).every((x, i) => x.Kj === corners[i].Kj));
  const base = new fn.DQ();
  fn.Gs(base);
  base.vd = 64;                                                  // a 64x64 model texture, as ModelPlayer uses
  const renderer = fn.BX(base, 0, 0);
  const spec = {texU: 16, texV: 32, x: -2, y: 0, z: -2, dx: 4, dy: 12, dz: 4, delta: 0.25, texW: 64, texH: 64};
  const box = new fn.DZG();
  fn.Ehl(box, renderer, 16, 32, -2, 0, -2, 4, 12, 4, 0.25, 1);
  assertVanillaBox(box, {...spec, mirror: true}, 'Ehl with mirror');
  assert.equal(renderer.a6Y.g, 0, 'building a box does not add it to the renderer');
  assertVanillaBox(fn.F2d(renderer, 16, 32, -2, 0, -2, 4, 12, 4, 0.25), {...spec, mirror: false}, 'F2d (mirror from the renderer)');
});

test('ModelPlayer (BVS + Hlq) smoke: slim arms are 3 pixels wide', () => {
  const width = (part) => { const box = firstBox(part); return box.dsl - box.dsk; };
  const slim = new fn.BVS();
  fn.Hlq(slim, 0, 1);
  const wide = new fn.BVS();
  fn.Hlq(wide, 0, 0);
  assert.ok(slim instanceof fn.OB);
  assert.deepEqual([slim.dWp, wide.dWp, slim.vI, slim.vd], [1, 0, 64, 64]);
  for (const field of ['gM', 'f3', 'Wo', 'a0W']) {
    assert.equal(width(slim[field]), 3, 'slim ' + field);
    assert.equal(width(wide[field]), 4, 'wide ' + field);
  }
  assert.deepEqual([slim.f3.cD, slim.f3.bs, slim.gM.cD, slim.gM.bs], [5, 2.5, -5, 2.5], 'slim arms pivot 0.5 lower');
  assert.deepEqual([wide.f3.cD, wide.f3.bs, wide.gM.cD, wide.gM.bs], [5, 2, -5, 2]);
  for (const field of ['a0W', 'Wo', 'a4$', 'bqQ', 'bq3', 'bky']) assert.ok(slim[field] instanceof fn.M2, field);
  assert.deepEqual([slim.bky.bdO, slim.bky.bby], [64, 32], 'the cape keeps a 64x32 texture');
  assert.deepEqual([slim.cJ9.g, wide.cJ9.g], [17, 16], 'biped parts + ears, cape, new arms and legs, overlays');
  assertVanillaBox(firstBox(slim.a0W), {texU: 48, texV: 48, x: -1, y: -2, z: -2, dx: 3, dy: 12, dz: 4, delta: 0.25, mirror: false, texW: 64, texH: 64}, 'slim left sleeve');
});

test('the live client also loads in the browser profile (the fallback the harness would use)', (t) => {
  const before = nodeTimers();
  const browser = loadClient(CLASSES, {profile: 'browser'});
  try {
    t.diagnostic(`browser profile: ${Math.round(browser.loadMs)} ms; stubbed globals read: ${browser.stubs.touchedDuringLoad.join(', ')}`);
    assert.equal(browser.profile, 'browser');
    assert.deepEqual(browser.names, client.names, 'the same bindings as in the minimal profile');
    assert.ok(browser.stubs.touchedDuringLoad.includes('document'), 'the page-only branches of the client mods ran');
    assert.deepEqual(browser.logs.filter((entry) => entry.level === 'error'), [], 'no console errors during load');
    assert.deepEqual(browser.pending(), {timers: 0, frames: 0, idle: 0, microtasks: 0});
    const model = new browser.fn.OB();
    browser.fn.AB4(model, 0, 0, 64, 32);
    assert.equal(model.cJ9.g, 7);
  } finally {
    browser.dispose();
  }
  assert.equal(nodeTimers(), before, 'no Node timer was created');
});

// A minimal bundle in TeaVM's UMD wrapper, for the harness's own guarantees.
const bundle = (body) => '"use strict";\n(function(root,module){if(typeof define===\'function\'&&define.amd){' +
  'define([\'exports\'],function(exports){module(root,exports);});}else if(typeof exports===\'object\'&&exports!==null&&' +
  'typeof exports.nodeName!==\'string\'){module(global,exports);}else{module(root,root);}}(typeof self!==\'undefined\'?' +
  'self:this,function($rt_globals,$rt_exports){' + body + '\n}));\n\n//# sourceMappingURL=../classes.js.map\n';

test('harness: load-time browser globals get inert stubs; nothing is scheduled for real', async () => {
  const before = nodeTimers();
  const fake = loadClientSource(bundle(`
function Foo(){this.x=1;}
var Bar
=42;
var Baz=null,Qux='q';
function setBaz(v){Baz=v;}
function lazy(){lazy=function(){return 2;};return 1;}
document.body.setAttribute('data-harness','1');
HTMLCanvasElement.prototype.harnessPatched=true;
var Agent=navigator.userAgent;
localStorage.setItem('k','v');
var Stored=localStorage.getItem('k');
var Socket=new WebSocket('ws://example.invalid/');
setInterval(function(){throw new Error('interval fired');},1);
setTimeout(function(){throw new Error('timeout fired');},0);
requestAnimationFrame(function(){throw new Error('frame fired');});
queueMicrotask(function(){throw new Error('microtask fired');});
fetch('http://example.invalid/').then(function(){throw new Error('fetch settled');});
$rt_exports.main=function(){throw new Error('main ran');};`), {filename: 'fake-classes.js'});
  assert.equal(fake.profile, 'browser', 'retried with browser stubs');
  assert.match(fake.fallbackError, /document is not defined/);
  for (const name of ['document', 'HTMLCanvasElement', 'navigator', 'localStorage', 'WebSocket', 'setInterval',
    'setTimeout', 'requestAnimationFrame', 'queueMicrotask', 'fetch']) {
    assert.ok(fake.stubs.touchedDuringLoad.includes(name), name + ' was stubbed');
  }
  assert.equal(fake.evaluate('HTMLCanvasElement.prototype.harnessPatched'), true, 'DOM classes take prototype patches');
  assert.deepEqual(fake.pending(), {timers: 2, frames: 1, idle: 0, microtasks: 1}, 'recorded, not run');
  assert.equal(fake.fn.Bar, 42, 'a declaration split across lines');
  assert.equal(fake.fn.Qux, 'q', 'a later binding of a var list');
  assert.equal(fake.fn.Stored, 'v', 'in-memory storage');
  assert.equal(fake.fn.Socket.readyState, 3, 'the socket never connects');
  assert.equal(typeof fake.fn.Agent, 'string');
  assert.equal(fake.fn.lazy(), 1);
  assert.equal(fake.fn.lazy(), 2, 'fn follows a function that replaced itself');
  fake.fn.setBaz(5);
  assert.equal(fake.fn.Baz, 5, 'fn reads the current value');
  fake.fn.Baz = 7;
  assert.equal(fake.evaluate('Baz'), 7, 'assigning fn.X rebinds X inside the bundle');
  assert.equal(fake.evaluate('typeof Foo'), 'function');
  assert.ok(!('main' in fake.fn) && typeof fake.context.main === 'function', 'main is exported and never called');
  assert.ok(!Object.keys(fake.context).some((key) => key.startsWith('__nativeHarness')), 'the harness hook is removed');
  await new Promise((resolve) => setImmediate(resolve));       // any callback that ran would throw
  await new Promise((resolve) => setTimeout(resolve, 20));
  fake.dispose();
  assert.deepEqual(fake.pending(), {timers: 0, frames: 0, idle: 0, microtasks: 0});
  assert.equal(nodeTimers(), before, 'no Node timer was created');
});

test('harness: refuses non-TeaVM input and reports evaluation errors', () => {
  assert.throws(() => loadClientSource('var x = 1;'), /not a TeaVM UMD bundle/);
  assert.throws(() => loadClientSource(bundle('throw new Error("boom at load");')), /boom at load/);
  assert.throws(() => loadClientSource(bundle('document.title;'), {profile: 'minimal'}), /document is not defined/);
});
