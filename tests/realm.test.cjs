'use strict';
// JasprRealm: the browser client loads a realm's module (site/realms/<realm>.js) only when the server first marks the
// player as inside it, runs it every tick with a small engine API, and clears everything on leaving. Atlas's module
// paints each land's sky and fog, lets ash fall over occupied Dominion land (never over liberated land, never after
// victory), embers in the Forges, lumen motes in the Concord. Also: quartz gates burn half blue, half black.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {CLIENT_BUILD} = require('./client-build.cjs');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const live = () => fs.readFileSync(path.join(root, 'site/classes.js'), 'latin1');
const fenced = (text, name) => { const b = '/* ' + name + '_BEGIN */', e = '/* ' + name + '_END */'; return text.slice(text.indexOf(b), text.indexOf(e) + e.length); };

test('realm stage: four fenced hooks after the portal stage, idempotent, installed, particle fields resolved', () => {
  const {build, particleFields} = require('../scripts/build-realm-client.cjs');
  const text = live();
  const staged = build(text);
  assert.equal(staged, text, 'site/classes.js carries the current realm stage');
  assert.equal(staged.split('/*JASPR_REALM_V1*/').length - 1, 4);
  for (const hook of ['JasprPortal.tick(a);/*JASPR_REALM_V1*/JasprRealm.tick(a);', '/*JASPR_REALM_V1*/if(JasprRealm.sky){f=JasprRealm.sky[0];',
    '/*JASPR_REALM_V1*/if(JasprRealm.sun){r=JasprRealm.sun[0];s=JasprRealm.sun[1];}', 'JasprSky.fog(a);/*JASPR_REALM_V1*/JasprRealm.fogColor(a);'])
    assert.ok(staged.includes(hook), hook);
  const fields = particleFields(staged);
  assert.equal(fields.portal, 'Kum');
  for (const n of ['fallingdust', 'flame', 'lava', 'endRod', 'townaura']) assert.match(fields[n], /^\w+$/, n);
  new vm.Script(staged);
  assert.ok(read('site/client.html').includes('classes.js?v=' + CLIENT_BUILD), 'browsers fetch the new client');
});

/** Runs the realm block against a stub engine; returns the context and a handle to drive it. */
function realmHarness() {
  const block = fenced(live(), 'JASPR_REALM_V1');
  const spawned = [], cleared = [], scripts = [];
  let marker = null;
  const ctx = {
    Math, Date, JSON, String, Object, parseInt, encodeURIComponent, Int32Array,
    document: {currentScript: {src: 'https://jaspr.example/jaspercraft/classes.js?v=1'}, head: {appendChild: s => scripts.push(s)}, createElement: () => ({})},
    CC: () => {}, Bh: n => ({data: new Int32Array(n)}),
    FH1: (w, t, x, y, z, vx, vy, vz, q) => spawned.push({t, x, y, z, param: q.data.length ? q.data[0] : undefined}),
    DWa: () => ({bh: 100, bq: 70, bi: -200}), GuJ: (r, g, b) => cleared.push([r, g, b]),
    Cbd: (sb, name) => name === 'jrm' && marker ? {a47: marker} : null, $rt_str: s => s, $rt_ustr: s => s,
  };
  for (const [n, f] of Object.entries(require('../scripts/build-realm-client.cjs').particleFields(live()))) ctx[f] = 'type:' + n;
  ctx.$rt_globals = ctx;
  vm.createContext(ctx);
  vm.runInContext(block + '\nthis.JasprRealm=JasprRealm;', ctx);
  const mc = {v: {}, X: {k3: {}}};
  return {ctx, spawned, cleared, scripts, mc, setMarker: m => { marker = m; }, tick: n => { for (let i = 0; i < n; i++) ctx.JasprRealm.tick(mc); }};
}

test('realm loader: loads the module once, only on the marker, passes state, clears on leaving', () => {
  const h = realmHarness();
  h.tick(30);
  assert.equal(h.scripts.length, 0, 'no module is fetched outside a realm');
  h.setMarker('JRM v1 atlas 7 M 0 0');
  h.tick(10);
  assert.equal(h.scripts.length, 1);
  assert.equal(h.scripts[0].src, 'https://jaspr.example/jaspercraft/realms/atlas.js?v=7', 'fetched beside classes.js, versioned');
  vm.runInContext(read('site/realms/atlas.js'), h.ctx);
  h.scripts[0].onload();
  h.tick(20);
  const st = h.ctx.JasprRealmDiagnostics.status();
  assert.deepEqual({...st.state}, {realm: 'atlas', version: '7', zone: 'M', mask: 0, victory: false});
  assert.ok(st.loaded && st.ticks > 0 && st.failures.length === 0);
  assert.ok(h.spawned.some(p => p.t === 'type:fallingdust'), 'ash falls over the occupied Marches');
  const r = {eH: 0.5, eF: 0.6, eJ: 0.9};
  h.ctx.JasprRealm.fogColor(r);
  assert.ok(r.eH < 0.4 && h.cleared.length > 0, 'the fog is darkened toward ash');
  h.setMarker(null);
  h.tick(10);
  assert.equal(h.ctx.JasprRealmDiagnostics.status().state, null);
  assert.equal(h.ctx.JasprRealm.sky, null, 'leaving clears the sky');
  h.setMarker('JRM v1 atlas 7 C 0 0');
  h.tick(10);
  assert.equal(h.scripts.length, 1, 'the module is fetched only once');
  h.setMarker('JRM v1 ../x 7 C 0 0');
  h.tick(10);
  assert.equal(h.ctx.JasprRealmDiagnostics.status().state, null, 'malformed realm names are ignored');
});

function atlasModule() {
  const ctx = {Math, globalThis: null};
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('site/realms/atlas.js'), ctx);
  const module = ctx.JasprRealmModules.atlas;
  const run = (state, n = 40) => {
    const out = {particles: [], fog: null, w: 0, sky: null, sun: undefined};
    const api = {player: () => ({x: 0, y: 64, z: 0}), particle: (name, x, y, z, vx, vy, vz, param) => out.particles.push({name, param}),
      setFog: (c, w) => { out.fog = c && c.slice(); out.w = w; }, setSky: c => { out.sky = c && c.slice(); }, setSun: c => { out.sun = c; }};
    for (let i = 0; i < n; i++) module.tick(api, state);
    module.leave(api);
    return out;
  };
  return run;
}

test('atlas module: each land its sky; ash only over occupied land; victory clears everything', () => {
  const run = atlasModule();
  const occupied = run({zone: 'M', mask: 0, victory: false}, 150);
  assert.ok(occupied.particles.filter(p => p.name === 'fallingdust').length > 500, 'thick ash in the Marches');
  assert.ok(occupied.particles.every(p => p.name !== 'fallingdust' || [252 | 7 << 12, 252 | 15 << 12].includes(p.param)), 'grey and black ash');
  const freed = run({zone: 'M', mask: 1, victory: false}, 150);
  assert.equal(freed.particles.filter(p => p.name === 'fallingdust').length, 0, 'no ash once the Marches are free');
  const won = run({zone: 'F', mask: 31, victory: true}, 150);
  assert.equal(won.particles.filter(p => p.name === 'fallingdust' || p.name === 'flame' || p.name === 'lava').length, 0, 'no ash or embers after victory');
  const forges = run({zone: 'F', mask: 0, victory: false}, 150);
  assert.ok(forges.particles.some(p => p.name === 'flame' || p.name === 'lava'), 'embers in the Forges');
  const concord = run({zone: 'C', mask: 0, victory: false}, 150);
  assert.ok(concord.particles.some(p => p.name === 'endRod') && !concord.particles.some(p => p.name === 'fallingdust'), 'lumen motes, no ash, in the Concord');
});

test('portal stage: quartz gates burn half blue and half black; mossy stays green; obsidian purple', () => {
  const {BLUE, BLACK, SEAM, GREEN, PURPLE} = require('../scripts/build-portal-client.cjs');
  const rgb = c => Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
  const block = fenced(live(), 'JASPR_PORTAL_V1');
  assert.ok(block.includes('if(id===155)c=split(w,pos);'), 'installed in site/classes.js');
  const blocks = new Map();
  const set = (x, y, z, id) => blocks.set(x + ',' + y + ',' + z, id);
  // A 4-wide quartz gate along x at z=0, a 3-wide one along z at x=50, a mossy one and an obsidian one.
  for (let x = -1; x <= 4; x++) for (let y = 0; y <= 4; y++) set(x, y, 0, x < 0 || x > 3 || y === 0 || y === 4 ? 155 : 90);
  for (let z = -1; z <= 3; z++) for (let y = 0; y <= 4; y++) set(50, y, z, z < 0 || z > 2 || y === 0 || y === 4 ? 155 : 90);
  for (let y = 0; y <= 4; y++) { set(100, y, 0, y === 0 ? 48 : 90); set(200, y, 0, y === 0 ? 49 : 90); }
  const pos = (x, y, z) => ({m: x, i: y, l: z});
  const F = {KsT: [-1, 0, 0], KsU: [1, 0, 0], KsV: [0, 0, -1], KsW: [0, 0, 1]};
  const ctx = {Map, Math, String, $rt_globals: {}, Bw: () => {}, ENW: s => s, CZr: (w, p) => ({n: blocks.get(p.m + ',' + p.i + ',' + p.l) || 0}),
    EoL: p => pos(p.m, p.i - 1, p.l), DWK: (p, f, n) => pos(p.m + f[0] * n, p.i + f[1] * n, p.l + f[2] * n), ...F};
  vm.createContext(ctx);
  vm.runInContext(block + '\nthis.JasprPortal=JasprPortal;', ctx);
  ctx.JasprPortal.tick({X: {}});
  const tint = (x, y, z) => ctx.JasprPortal.tint(pos(x, y, z));
  assert.deepEqual([0, 1, 2, 3].map(x => tint(x, 2, 0)), [rgb(BLUE), rgb(BLUE), rgb(BLACK), rgb(BLACK)], 'west half blue, east half black');
  assert.deepEqual([0, 1, 2].map(z => tint(50, 1, z)), [rgb(BLUE), rgb(SEAM), rgb(BLACK)], 'an odd gate has a seam down its middle');
  assert.equal(tint(100, 3, 0), rgb(GREEN));
  assert.equal(tint(200, 3, 0), rgb(PURPLE));
});
