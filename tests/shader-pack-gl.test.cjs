'use strict';
// Executes the real shader-pack browser glue (client-mods/shader-packs-teavm.js)
// against a mock WebGL context with vertex-array domains and seeded engine
// residue, and asserts the graded draw is well-formed AND fully restored:
// acquisition precedes the draw, unit 0 carries the copy texture, our program
// is current via the engine wrapper, engine VAO contents are untouched,
// engine bindings/program/attribs/flags/viewport are restored, caches are
// invalidated after, and failures latch visibly after 5 frames with a
// blit->copy fallback per frame. The fiber entry (JasprShadersPass)
// intentionally stays uncovered: it only gates on mode/pass and delegates.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');

function mockGL(options) {
  options = options || {};
  const calls = [];
  let nextId = 1;
  const handle = (kind) => ({ __kind: kind, __id: nextId++ });
  const state = {
    activeUnit: 33984,
    bindings: { 33984: null },
    program: null,
    buffer: null,
    viewport: [0, 0, 1280, 720],
    flags: {},
    errors: [],
    uniforms: [],
    vaoCur: 'def',
    vaoAttribs: { def: {} },
  };
  const curStore = () => state.vaoAttribs[state.vaoCur] || (state.vaoAttribs[state.vaoCur] = {});
  const C = {
    NO_ERROR: 0, TEXTURE0: 33984, TEXTURE_2D: 3553, RGBA: 6408, UNSIGNED_BYTE: 5121,
    LINEAR: 9729, CLAMP_TO_EDGE: 33071, FRAMEBUFFER: 36160, COLOR_ATTACHMENT0: 36064,
    READ_FRAMEBUFFER: 36008, DRAW_FRAMEBUFFER: 36009, COLOR_BUFFER_BIT: 16384, NEAREST: 9728,
    TRIANGLES: 4, FLOAT: 5126, ARRAY_BUFFER: 34962, STATIC_DRAW: 35044,
    VERTEX_SHADER: 35633, FRAGMENT_SHADER: 35632, COMPILE_STATUS: 35713, LINK_STATUS: 35714,
    CURRENT_PROGRAM: 35725, ARRAY_BUFFER_BINDING: 34964, TEXTURE_BINDING_2D: 32873,
    ACTIVE_TEXTURE: 34016, VIEWPORT: 2978, BLEND: 3042, DEPTH_TEST: 2929, CULL_FACE: 2884,
    SCISSOR_TEST: 3089, BLEND_SRC_RGB: 7401, BLEND_DST_RGB: 7402,
    VERTEX_ATTRIB_ARRAY_ENABLED: 34338, VERTEX_ATTRIB_ARRAY_SIZE: 34339,
    VERTEX_ATTRIB_ARRAY_STRIDE: 34340, VERTEX_ATTRIB_ARRAY_TYPE: 34341,
    VERTEX_ATTRIB_ARRAY_NORMALIZED: 34922,
    FRAMEBUFFER_BINDING: 36006,
    READ_FRAMEBUFFER_BINDING: 36010, DRAW_FRAMEBUFFER_BINDING: 36011,
    FRAMEBUFFER_COMPLETE: 36053, DEPTH_COMPONENT16: 33189, DEPTH_ATTACHMENT: 36096,
    RENDERBUFFER: 36161, DEPTH_BUFFER_BIT: 256,
  };
  const failNext = { copy: !!options.failCopy, blit: !!options.failBlit };
  const alwaysFail = !!options.alwaysFail;
  const g = { calls, state };
  g.uniforms = state.uniforms;
  for (const [k, v] of Object.entries(C)) g[k] = v;
  g.getError = () => {
    calls.push(['getError']);
    if (state.errors.length) return state.errors.shift();
    return 0;
  };
  g.getParameter = (p) => {
    calls.push(['getParameter', p]);
    if (p === C.VIEWPORT) return state.viewport.slice();
    if (p === C.ACTIVE_TEXTURE) return state.activeUnit;
    if (p === C.TEXTURE_BINDING_2D) return state.bindings[state.activeUnit] || null;
    if (p === C.CURRENT_PROGRAM) return state.program;
    if (p === C.ARRAY_BUFFER_BINDING) return state.buffer;
    if (p === C.FRAMEBUFFER_BINDING || p === C.READ_FRAMEBUFFER_BINDING || p === C.DRAW_FRAMEBUFFER_BINDING) return null;
    if (p === C.BLEND || p === C.DEPTH_TEST || p === C.CULL_FACE || p === C.SCISSOR_TEST) return !!state.flags[p];
    if (p === C.BLEND_SRC_RGB || p === C.BLEND_DST_RGB) return 1;
    return 0;
  };
  g.getVertexAttrib = (i, p) => {
    calls.push(['getVertexAttrib', i, p]);
    const rec = curStore()[i] || {};
    if (p === C.VERTEX_ATTRIB_ARRAY_ENABLED) return !!rec.enabled;
    if (p === C.VERTEX_ATTRIB_ARRAY_SIZE) return rec.size == null ? 4 : rec.size;
    if (p === C.VERTEX_ATTRIB_ARRAY_TYPE) return rec.type == null ? 5126 : rec.type;
    if (p === C.VERTEX_ATTRIB_ARRAY_NORMALIZED) return !!rec.norm;
    if (p === C.VERTEX_ATTRIB_ARRAY_STRIDE) return rec.stride == null ? 0 : rec.stride;
    return 0;
  };
  g.createShader = (t) => { const h = handle('shader'); calls.push(['createShader', t]); return h; };
  g.deleteShader = (h) => { calls.push(['deleteShader', h && h.__id]); };
  g.shaderSource = () => {};
  g.compileShader = () => {};
  g.getShaderParameter = () => true;
  g.getShaderInfoLog = () => '';
  g.createProgram = () => { const h = handle('program'); calls.push(['createProgram']); return h; };
  g.deleteProgram = (h) => { calls.push(['deleteProgram', h && h.__id]); };
  g.attachShader = () => {};
  g.bindAttribLocation = (p, i, n) => { calls.push(['bindAttribLocation', i, n]); };
  g.linkProgram = () => {};
  g.getProgramParameter = () => true;
  g.getProgramInfoLog = () => '';
  g.getUniformLocation = (p, n) => ({ __loc: n });
  g.createBuffer = () => { const h = handle('buffer'); calls.push(['createBuffer']); return h; };
  g.bindBuffer = (t, b) => { calls.push(['bindBuffer', b && b.__id]); if (t === C.ARRAY_BUFFER) state.buffer = b; };
  g.bufferData = () => {};
  g.deleteBuffer = (h) => { calls.push(['deleteBuffer', h && h.__id]); };
  g.createTexture = () => { const h = handle('texture'); calls.push(['createTexture']); return h; };
  g.bindTexture = (t, h) => { calls.push(['bindTexture', h && h.__id]); state.bindings[state.activeUnit] = h; };
  g.texParameteri = () => {};
  g.texImage2D = () => {};
  g.deleteTexture = (h) => { calls.push(['deleteTexture', h && h.__id]); };
  g.createFramebuffer = () => { const h = handle('fbo'); calls.push(['createFramebuffer']); return h; };
  g.bindFramebuffer = () => {};
  g.framebufferTexture2D = () => {};
  g.checkFramebufferStatus = () => C.FRAMEBUFFER_COMPLETE;
  g.deleteFramebuffer = (h) => { calls.push(['deleteFramebuffer', h && h.__id]); };
  g.createRenderbuffer = () => handle('rb');
  g.bindRenderbuffer = () => {};
  g.renderbufferStorage = () => {};
  g.deleteRenderbuffer = () => {};
  if (!options.noVAO) {
  g.createVertexArray = () => { const h = handle('vao'); calls.push(['createVertexArray']); return h; };
  g.deleteVertexArray = (h) => { calls.push(['deleteVertexArray', h && h.__id]); };
  if (options.noVAO) delete g.createVertexArray;
  }
  g.blitFramebuffer = options.webgl1 ? undefined : () => {
    calls.push(['blitFramebuffer']);
    if (alwaysFail || failNext.blit) { failNext.blit = false; state.errors.push(1280); }
  };
  if (options.webgl1) delete g.blitFramebuffer;
  g.copyTexImage2D = () => {
    calls.push(['copyTexImage2D']);
    if (alwaysFail || failNext.copy) { failNext.copy = false; state.errors.push(1280); }
  };
  g.activeTexture = (u) => { calls.push(['activeTexture', u]); state.activeUnit = u; };
  g.useProgram = (p) => { calls.push(['useProgram', p && p.__id]); state.program = p; };
  g.uniform1i = (l, v) => { calls.push(['uniform1i', l.__loc, v]); state.uniforms.push([l.__loc, v]); };
  g.uniform2f = (l, a, b) => { calls.push(['uniform2f', l.__loc, a, b]); state.uniforms.push([l.__loc, a, b]); };
  g.uniform1f = (l, v) => { calls.push(['uniform1f', l.__loc, v]); state.uniforms.push([l.__loc, v]); };
  g.uniform3f = (l, a, b, c) => { calls.push(['uniform3f', l.__loc, a, b, c]); state.uniforms.push([l.__loc, a, b, c]); };
  g.enableVertexAttribArray = (i) => { calls.push(['enableAttrib', i]); const r = curStore()[i] || (curStore()[i] = {}); r.enabled = true; };
  g.disableVertexAttribArray = (i) => { calls.push(['disableAttrib', i]); const r = curStore()[i] || (curStore()[i] = {}); r.enabled = false; };
  g.vertexAttribPointer = (i, s, t, n, st, o) => {
    calls.push(['attribPointer', i, s, t, n, st, o]);
    const r = curStore()[i] || (curStore()[i] = {});
    Object.assign(r, { enabled: r.enabled, size: s, type: t, norm: !!n, stride: st, offset: o });
  };
  g.drawArrays = (m, f, c) => {
    calls.push(['drawArrays', m, f, c, {
      program: state.program && state.program.__id,
      unit0: state.bindings[33984] && state.bindings[33984].__id,
      active: state.activeUnit,
      vao: state.vaoCur,
    }]);
  };
  g.enable = (c) => { state.flags[c] = true; };
  g.disable = (c) => { state.flags[c] = false; };
  g.viewport = (a, b, c, d) => { state.viewport = [a, b, c, d]; };
  g.blendFunc = () => {};
  g.getExtension = () => null;
  return g;
}

function loadRuntime(options) {
  const engineCalls = [];
  const store = {};
  const g = mockGL(options);
  const ctx = {
    console, Float32Array, Infinity, Math, JSON,
    $rt_globals: { localStorage: {
      getItem: (k) => (k in store ? store[k] : null),
      setItem: (k, v) => { store[k] = String(v); },
    } },
    $rt_str: (s) => String(s),
    $rt_ustr: (s) => String(s),
    HEf: g,
    HHY: { width: 1280, height: 720 },
    HHJ: 1280, HHK: 720, HIH: 0, HII: 0, HIJ: 1280, HIK: 720,
    HDF: null,
    Fwv: (b) => {
      engineCalls.push(['Fwv', b && b.cE1 ? 'vao' : 'null']);
      g.state.vaoCur = (b && b.cE1 && b.cE1.__id) ? b.cE1.__id : 'def';
      ctx.HDF = b || null;
    },
    FNV: (m) => { engineCalls.push(['FNV', m]); },
    CGj: (b) => { engineCalls.push(['CGj', !!(b && b.Yv)]); g.useProgram(b && b.Yv); },
    Egf: () => { engineCalls.push(['Egf']); },
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(root, 'client-mods/shader-packs.js'), 'utf8'), ctx);
  vm.runInContext(fs.readFileSync(path.join(root, 'client-mods/shader-packs-teavm.js'), 'utf8'), ctx);
  return { ctx, g, engineCalls, store };
}

function kinds(calls, name) { return calls.filter((c) => c[0] === name); }

test('picker selection chooses an exact pack and does not cycle the button', () => {
  const { ctx, store } = loadRuntime({});
  const button = {};
  ctx.JasprShaders.bind(button);
  ctx.JasprShaders.click(button);
  assert.equal(ctx.JasprShaders.mode(), 0);
  assert.equal(ctx.JasprShaders.choose(2), 2);
  assert.equal(store['jaspr.shader.pack'], '2');
  assert.equal(button.dd, 'Shaders: Chocapic');
  assert.equal(ctx.JasprShaders.choose(99), 0);
  assert.equal(store['jaspr.shader.pack'], '0');
});

test('picker labels expose each pack once and blank invalid rows', () => {
  const { ctx } = loadRuntime({});
  const labels = Array.from({ length: 5 }, (_, mode) => ctx.JasprShadersPickerLabel(mode));
  assert.deepEqual(labels, ['* OFF', 'MakeUp UltraFast', 'Chocapic13 Toaster', 'Miniature', "Sildur's Vibrant Lite"]);
  assert.equal(new Set(labels.map((label) => label.replace(/^\\* /, ''))).size, 5);
  assert.equal(ctx.JasprShadersPickerLabel(5), '');
});

test('picker buttons derive their label from their stable ID', () => {
  const { ctx } = loadRuntime({});
  ctx.FX = () => false;
  ctx.B = () => false;
  ctx.LIw = (id, x, style, label) => ({ bF: id, dd: label });
  const buttons = [910, 911, 912, 913, 914].map((id) =>
    ctx.JasprShadersPickerButton(id, 0, 'stale label'));
  assert.deepEqual(buttons.map((button) => button.dd), [
    '* OFF', 'MakeUp UltraFast', 'Chocapic13 Toaster', 'Miniature', "Sildur's Vibrant Lite"
  ]);
  assert.equal(new Set(buttons.map((button) => button.dd.replace(/^\\* /, ''))).size, buttons.length);
});

test('OFF path does not allocate, assemble, or issue GL work', () => {
  const { ctx, g } = loadRuntime({});
  assert.equal(ctx.JasprShaders.mode(), 0);
  const beforeStatus = g.calls.length;
  assert.equal(ctx.JasprShaders.status().loaded, false);
  assert.equal(ctx.JasprShaders.status().sourcesBuilt, false);
  assert.ok(g.calls.length > beforeStatus, 'diagnostic status may inspect the viewport');
  g.calls.length = 0;
  assert.equal(ctx.JasprShaders.grade(), false);
  assert.equal(ctx.JasprShaders.gradeLightmap({ cKO: { data: new Array(256).fill(0) } }), false);
  assert.equal(ctx.JasprShaders.fog(), null);
  assert.deepEqual(g.calls, [], 'OFF should not touch WebGL');
});

test('turning shaders OFF unloads pack resources and restores the zero-cost path', () => {
  const { ctx, g } = loadRuntime({});
  assert.equal(ctx.JasprShaders.choose(1), 1);
  assert.equal(ctx.JasprShaders.grade(), true);
  assert.equal(ctx.JasprShaders.status().loaded, true);
  const beforeOff = g.calls.length;
  assert.equal(ctx.JasprShaders.choose(0), 0);
  assert.equal(ctx.JasprShaders.status().loaded, false);
  assert.equal(ctx.JasprShaders.status().sourcesBuilt, false);
  assert.ok(g.calls.slice(beforeOff).some((c) => /^delete/.test(c[0])), 'OFF releases GL resources');
  const afterOff = g.calls.length;
  assert.equal(ctx.JasprShaders.grade(), false);
  assert.equal(ctx.JasprShaders.gradeLightmap({ cKO: { data: new Array(256).fill(0) } }), false);
  assert.equal(ctx.JasprShaders.fog(), null);
  assert.equal(g.calls.length, afterOff, 'released OFF path stays idle');
});

test('Sildur grade sends its Uncharted2 and gamma parameters', () => {
  const { ctx, g } = loadRuntime({});
  assert.equal(ctx.JasprShaders.choose(4), 4);
  assert.equal(ctx.JasprShaders.grade(), true);
  const uniform = (name) => {
    const values = g.uniforms.filter((u) => u[0] === name);
    return values[values.length - 1] && values[values.length - 1][1];
  };
  assert.equal(uniform('uTonemap'), 4);
  assert.equal(uniform('uGamma'), 1.3);
});

function seedEngineResidue(g, ctx) {
  const atlas = { __kind: 'texture', __id: 9001 };
  const light = { __kind: 'texture', __id: 9002 };
  const prog = { __kind: 'program', __id: 9003 };
  const buf = { __kind: 'buffer', __id: 9004 };
  const engVAO = { __kind: 'vao', __id: 9005 };
  g.state.bindings[33984] = atlas;
  g.state.bindings[33985] = light;
  g.state.activeUnit = 33985;
  g.state.program = prog;
  g.state.buffer = buf;
  g.state.viewport = [0, 0, 1280, 720];
  g.state.flags[3042] = true;
  g.state.flags[2929] = true;
  g.state.vaoAttribs[engVAO.__id] = {
    0: { enabled: true, size: 3, type: 5126, norm: false, stride: 24, offset: 0 },
    1: { enabled: false },
    2: { enabled: false },
    3: { enabled: true, size: 4, type: 5126, norm: false, stride: 16, offset: 0 },
    4: { enabled: false },
    5: { enabled: false },
    6: { enabled: false },
    7: { enabled: false },
  };
  g.state.vaoCur = engVAO.__id;
  const wrapper = { cE1: engVAO };
  ctx.HDF = wrapper;
  return { atlas, light, prog, buf, engVAO, wrapper };
}

test('graded draw samples the copy on unit 0 with our program current', () => {
  const { ctx, g, engineCalls } = loadRuntime({});
  assert.equal(ctx.JasprShaders.cycle(), 1);
  assert.equal(ctx.JasprShaders.grade(), true);
  const draws = kinds(g.calls, 'drawArrays');
  assert.equal(draws.length, 1);
  const [,,,, snap] = draws[0];
  const programs = g.calls.filter((c) => c[0] === 'createProgram');
  assert.equal(programs.length, 1);
  assert.ok(snap.program, 'a program is current at draw');
  assert.ok(snap.unit0, 'unit 0 has a texture at draw');
  const textures = g.calls.filter((c) => c[0] === 'createTexture');
  assert.equal(textures.length, 1);
  const acq = g.calls.findIndex((c) => c[0] === 'blitFramebuffer' || c[0] === 'copyTexImage2D');
  const drawIx = g.calls.findIndex((c) => c[0] === 'drawArrays');
  assert.ok(acq >= 0 && acq < drawIx, 'pixels acquired before draw');
  assert.ok(engineCalls.some((c) => c[0] === 'CGj' && c[1] === true), 'program bound via CGj');
  assert.ok(engineCalls.some((c) => c[0] === 'FNV' && c[1] === 63), 'FNV(63) after draw');
  const ptr = g.calls.filter((c) => c[0] === 'attribPointer');
  assert.deepEqual(ptr.map((c) => c.slice(1, 4)), [[0, 2, 5126]]);
  const uni = Object.fromEntries(g.uniforms.filter((u) => u[0] === 'uTonemap' || u[0] === 'uExpo').map((u) => u));
  assert.equal(uni.uTonemap, 2);
  assert.equal(uni.uExpo, 1.06);
  const st = ctx.JasprShaders.status();
  assert.equal(st.failed, false);
  assert.equal(st.fails, 0);
});

test('engine residue is fully restored and its VAO untouched', () => {
  const { ctx, g, engineCalls } = loadRuntime({});
  const seed = seedEngineResidue(g, ctx);
  ctx.JasprShaders.cycle();
  assert.equal(ctx.JasprShaders.grade(), true);
  assert.equal(g.state.bindings[33984], seed.atlas, 'unit 0 texture restored');
  assert.equal(g.state.bindings[33985], seed.light, 'unit 1 texture restored');
  assert.equal(g.state.activeUnit, 33985, 'active unit restored');
  assert.equal(g.state.program, seed.prog, 'program restored');
  assert.equal(g.state.buffer, seed.buf, 'array buffer restored');
  assert.deepEqual(g.state.viewport, [0, 0, 1280, 720], 'viewport restored');
  assert.equal(g.state.flags[3042], true, 'blend restored');
  assert.equal(g.state.flags[2929], true, 'depth restored');
  assert.deepEqual(g.state.vaoAttribs[seed.engVAO.__id], {
    0: { enabled: true, size: 3, type: 5126, norm: false, stride: 24, offset: 0 },
    1: { enabled: false },
    2: { enabled: false },
    3: { enabled: true, size: 4, type: 5126, norm: false, stride: 16, offset: 0 },
    4: { enabled: false },
    5: { enabled: false },
    6: { enabled: false },
    7: { enabled: false },
  }, 'engine VAO contents pristine');
  assert.equal(g.state.vaoCur, seed.engVAO.__id, 'engine VAO rebound');
  assert.equal(ctx.HDF, seed.wrapper, 'VAO cache coherent');
  assert.ok(engineCalls.some((c) => c[0] === 'FNV' && c[1] === 63), 'caches invalidated');
  // Our triangle config went to our own VAO, not the engine's.
  const mine = Object.keys(g.state.vaoAttribs).filter((k) => k !== 'def' && +k !== seed.engVAO.__id);
  assert.equal(mine.length, 1, 'exactly one private VAO used');
  assert.deepEqual(g.state.vaoAttribs[mine[0]][0], { enabled: false, size: 2, type: 5126, norm: false, stride: 0, offset: 0 });
});

test('no-VAO contexts reissue the assumed layout instead of leaking', () => {
  const { ctx, g } = loadRuntime({ noVAO: true });
  g.state.vaoAttribs.def[0] = { enabled: true, size: 3, type: 5126, norm: false, stride: 24, offset: 0 };
  ctx.JasprShaders.cycle();
  assert.equal(ctx.JasprShaders.grade(), true);
  assert.deepEqual(g.state.vaoAttribs.def[0],
    { enabled: true, size: 3, type: 5126, norm: false, stride: 24, offset: 0 });
  assert.equal(kinds(g.calls, 'drawArrays').length, 1);
});

test('webgl1 path falls back to copy and still grades', () => {
  const { ctx, g } = loadRuntime({ webgl1: true });
  assert.equal(ctx.JasprShaders.cycle(), 1);
  ctx.JasprShaders.cycle();
  assert.equal(ctx.JasprShaders.mode(), 2);
  assert.equal(ctx.JasprShaders.grade(), true);
  assert.ok(kinds(g.calls, 'copyTexImage2D').length >= 1, 'copy fallback ran');
  assert.equal(kinds(g.calls, 'drawArrays').length, 1);
});

test('blit failure falls back to copy within the same frame', () => {
  const { ctx, g } = loadRuntime({ failBlit: true });
  ctx.JasprShaders.cycle();
  assert.equal(ctx.JasprShaders.grade(), true, 'copy rescues a failed blit');
  assert.ok(kinds(g.calls, 'copyTexImage2D').length >= 1);
  assert.equal(ctx.JasprShaders.status().failed, false);
});

test('persistent failure latches visibly after 5 frames and labels the button', () => {
  const { ctx } = loadRuntime({ alwaysFail: true });
  const button = {};
  ctx.JasprShaders.click(button);
  ctx.JasprShaders.choose(1);
  for (let i = 0; i < 5; i++) assert.equal(ctx.JasprShaders.grade(), false);
  const st = ctx.JasprShaders.status();
  assert.equal(st.failed, true);
  assert.equal(st.fails, 5);
  assert.ok(/copy|resolve|Error|1280/i.test(st.lastError), 'reason recorded, got: ' + st.lastError);
  assert.ok(String(button.dd).indexOf('(unavailable)') >= 0, 'button shows the failure');
});

test('lightmap core raises texels toward the pack curve and re-uploads', () => {
  const { ctx, engineCalls } = loadRuntime({});
  ctx.JasprShaders.cycle();
  const data = new Array(256).fill(-16777216);
  const renderer = { cKO: { data }, bKW: {} };
  assert.equal(ctx.JasprShaders.gradeLightmap(renderer), true);
  assert.ok(engineCalls.some((c) => c[0] === 'Egf'), 'texture re-uploaded');
  assert.ok(data.some((v) => v !== -16777216), 'dark texels lifted');
});
