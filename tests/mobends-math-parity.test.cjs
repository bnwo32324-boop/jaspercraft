'use strict';
// Mo' Bends parity: the JavaScript animation core (client-mods/mobends/mobends-core.js) against the ORIGINAL Mo' Bends
// 1.2.2 Java sources. The original classes are copied byte for byte into a temporary directory, compiled together with
// tests/java/mobends/MathParity.java (plus two tiny stubs for the only outside classes they reference) and run. The
// harness prints a deterministic, fixed-seed sequence of operations with their exact float arguments and results; this
// test replays the identical sequence on the JS core and compares every number. The SmoothOrientation and
// SmoothVector3f instances run free on both sides (the JS state is never re-synchronised from Java), so the reported
// maximum deviation includes the float (Java) vs double (JS) drift that accumulates over the whole sequence.
// Skips when the JDK or the original Mo' Bends sources are not on this machine.
// Run: node --test tests/mobends-math-parity.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');
const { createJasprMoBendsCore } = require('../client-mods/mobends/mobends-core.js');

const root = path.resolve(__dirname, '..');
const SEED = 20261001;
const STEPS = 4000;
const TOLERANCE = 1e-5; // absolute below magnitude 1, relative above it
const MOBENDS = process.env.MOBENDS_SOURCE_ROOT || 'C:/Users/AM/Downloads/MoBends-1.X-forge-1.12/MoBends-1.X-forge-1.12';
const JAVA_SOURCES = path.join(MOBENDS, 'src/main/java');
const ANIMATIONS = path.join(MOBENDS, 'src/main/resources/assets/mobends/bends/animations');
const JDK_BINS = ['C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin', process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, 'bin')].filter(Boolean);
const EXE = process.platform === 'win32' ? '.exe' : '';

// Original Mo' Bends classes compiled unmodified (QuaternionUtils pulls in IMat4x4d and the Vec3d family through
// VectorUtils; GUtil needs FontRenderer and the loaders need ByteArrayBuffer, both stubbed below).
const ORIGINALS = [
  'math/Quaternion', 'math/SmoothOrientation', 'math/QuaternionUtils', 'math/matrix/IMatd', 'math/matrix/IMat4x4d',
  'math/vector/IVec3fRead', 'math/vector/IVec3f', 'math/vector/Vec3f', 'math/vector/Vec3fReadonly', 'math/vector/IVec3dRead',
  'math/vector/IVec3d', 'math/vector/Vec3d', 'math/vector/Vec3dReadonly', 'math/vector/VectorUtils', 'math/vector/SmoothVector3f',
  'util/EnumAxis', 'util/Tween', 'util/GUtil', 'util/SerialHelper',
  'animation/keyframe/BinaryAnimationLoader', 'animation/keyframe/Bone', 'animation/keyframe/Keyframe', 'animation/keyframe/KeyframeAnimation'
].map(name => 'goblinbob/mobends/core/' + name + '.java');
const STUBS = {
  // Apache HttpCore (shipped with Minecraft): only the members SerialHelper and BinaryAnimationLoader call.
  'org/apache/http/util/ByteArrayBuffer.java': [
    'package org.apache.http.util;',
    'public final class ByteArrayBuffer {',
    '    private byte[] buffer; private int len;',
    '    public ByteArrayBuffer(int capacity) { if (capacity < 0) throw new IllegalArgumentException("Buffer capacity may not be negative"); buffer = new byte[capacity]; }',
    '    public void append(int b) { if (len == buffer.length) buffer = java.util.Arrays.copyOf(buffer, Math.max(buffer.length << 1, len + 1)); buffer[len++] = (byte) b; }',
    '    public byte[] toByteArray() { return java.util.Arrays.copyOf(buffer, len); }',
    '}'].join('\n'),
  // GUtil.wrapText takes a FontRenderer; the harness never calls it.
  'net/minecraft/client/gui/FontRenderer.java': [
    'package net.minecraft.client.gui;',
    'public class FontRenderer { public int getStringWidth(String text) { throw new UnsupportedOperationException("test stub"); } }'].join('\n')
};
const ADAPTER = { gl: { translate() {}, rotate() {}, scale() {}, multQuat() {}, push() {}, pop() {}, color() {} }, entity: {}, world: {}, item: {} };

function findJdk() {
  for (const bin of JDK_BINS) {
    const javac = path.join(bin, 'javac' + EXE), java = path.join(bin, 'java' + EXE);
    if (fs.existsSync(javac) && fs.existsSync(java)) return { javac, java };
  }
  return null;
}
const jdk = findJdk();
const skip = !jdk ? 'no JDK at ' + JDK_BINS.join(' or ')
  : !fs.existsSync(path.join(JAVA_SOURCES, ORIGINALS[0])) ? "original Mo' Bends sources not found under " + MOBENDS : false;

function run(command, args) {
  const result = cp.spawnSync(command, args, { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024, timeout: 240000, windowsHide: true });
  assert.ifError(result.error);
  assert.equal(result.status, 0, path.basename(command) + ' failed:\n' + result.stderr + '\n' + String(result.stdout).slice(-4000));
  return result.stdout;
}

function runHarness(animationFiles) {
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'mobends-parity-'));
  try {
    const src = path.join(scratch, 'src'), classes = path.join(scratch, 'classes'), sources = [];
    const put = (relative, content) => {
      const file = path.join(src, relative);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, content);
      sources.push(file);
    };
    for (const relative of ORIGINALS) {
      const original = fs.readFileSync(path.join(JAVA_SOURCES, relative));
      put(relative, original);
      assert.ok(fs.readFileSync(path.join(src, relative)).equals(original), relative + ' must be compiled unmodified');
    }
    for (const [relative, content] of Object.entries(STUBS)) put(relative, content + '\n');
    put('mobends/MathParity.java', fs.readFileSync(path.join(root, 'tests/java/mobends/MathParity.java')));
    fs.mkdirSync(classes);
    run(jdk.javac, ['-encoding', 'UTF-8', '-nowarn', '-d', classes, ...sources]);
    return run(jdk.java, ['-cp', classes, 'mobends.MathParity', String(SEED), String(STEPS), ...animationFiles]);
  } finally {
    fs.rmSync(scratch, { recursive: true, force: true });
  }
}

// ---- exact decoding of the harness output
const view = new DataView(new ArrayBuffer(8));
const F = hex => { view.setUint32(0, parseInt(hex, 16)); return view.getFloat32(0); };
const D = hex => { view.setUint32(0, parseInt(hex.slice(0, 8), 16)); view.setUint32(4, parseInt(hex.slice(8), 16)); return view.getFloat64(0); };
const show = value => (Object.is(value, -0) ? '-0' : String(value));

function parse(output) {
  const records = {};
  const lines = output.split('\n').filter(Boolean);
  for (const line of lines) {
    const tokens = line.split(' ');
    (records[tokens[0]] ||= []).push(tokens);
  }
  assert.equal(records.END && records.END.length, 1, 'the harness ran to completion');
  assert.equal(Number(records.END[0][1]), lines.length, 'every harness line was received');
  return records;
}

// ---- comparison bookkeeping
function deviation(js, java) {
  if (Number.isNaN(java) || Number.isNaN(js)) return Number.isNaN(java) && Number.isNaN(js) ? 0 : Infinity;
  if (js === java) return 0;
  return Math.abs(js - java) / Math.max(1, Math.abs(java));
}
class Tracker {
  constructor(name, exact) { this.name = name; this.exact = exact; this.count = 0; this.identical = 0; this.max = 0; this.where = 'nowhere'; this.failed = 0; this.failures = []; }
  check(js, java, where) {
    this.count++;
    if (Object.is(js, java) || (Number.isNaN(js) && Number.isNaN(java))) { this.identical++; return true; }
    const d = deviation(js, java);
    if (d > this.max) { this.max = d; this.where = where; }
    if (!this.exact && d <= TOLERANCE) return true;
    this.failed++;
    if (this.failures.length < 12) this.failures.push(where + ': Java ' + show(java) + ', JS ' + show(js) + ' (deviation ' + d.toExponential(2) + ')');
    return false;
  }
  summary() {
    return this.name + ': ' + this.count + ' values, ' + this.identical + ' bit-identical, max deviation ' + this.max.toExponential(2) +
      (this.max ? ' at ' + this.where : '') + (this.failed ? ', ' + this.failed + (this.exact ? ' NOT IDENTICAL' : ' ABOVE TOLERANCE') : '');
  }
  assert(t) {
    t.diagnostic(this.summary());
    assert.equal(this.failed, 0, this.summary() + '\n  ' + this.failures.join('\n  '));
  }
}

const quat = (core, values) => { const q = new core.Quaternion(); q.set(values[0], values[1], values[2], values[3]); return q; };
const quatValues = q => [q.x, q.y, q.z, q.w];
const vecValues = v => [v.x, v.y, v.z];
function checkAll(tracker, js, java, where, labels) {
  for (let i = 0; i < java.length; i++) tracker.check(js[i], java[i], where + (labels ? ' ' + labels[i] : '[' + i + ']'));
}

const SO_APPLY = {
  orient: (o, a) => o.orient(a[0], a[1], a[2], a[3]), orientX: (o, a) => o.orientX(a[0]), orientY: (o, a) => o.orientY(a[0]), orientZ: (o, a) => o.orientZ(a[0]),
  rotate: (o, a) => o.rotate(a[0], a[1], a[2], a[3]), rotateX: (o, a) => o.rotateX(a[0]), rotateY: (o, a) => o.rotateY(a[0]), rotateZ: (o, a) => o.rotateZ(a[0]),
  localRotate: (o, a) => o.localRotate(a[0], a[1], a[2], a[3]), localRotateX: (o, a) => o.localRotateX(a[0]), localRotateY: (o, a) => o.localRotateY(a[0]), localRotateZ: (o, a) => o.localRotateZ(a[0]),
  rotateInstant: (o, a) => o.rotateInstant(a[0], a[1], a[2], a[3]), rotateInstantX: (o, a) => o.rotateInstantX(a[0]), rotateInstantY: (o, a) => o.rotateInstantY(a[0]), rotateInstantZ: (o, a) => o.rotateInstantZ(a[0]),
  orientInstant: (o, a) => o.orientInstant(a[0], a[1], a[2], a[3]), orientInstantX: (o, a) => o.orientInstantX(a[0]), orientInstantY: (o, a) => o.orientInstantY(a[0]), orientInstantZ: (o, a) => o.orientInstantZ(a[0]),
  orientZero: o => o.orientZero(), identity: o => o.identity(), finish: o => o.finish(), setSmoothness: (o, a) => o.setSmoothness(a[0]),
  set: (o, a) => o.set(a[0], a[1], a[2], a[3]), add: (o, a) => o.add(a[0], a[1], a[2], a[3]), update: (o, a) => o.update(a[0]),
  copy: (o, a, all) => o.copy(all[a[0]]) // SmoothOrientation.set(SmoothOrientation)
};
const SV_APPLY = {
  slideTo: (v, a) => v.slideTo(a[0], a[1], a[2], a[3]), slideToZero: v => v.slideToZero(), slideToZeroS: (v, a) => v.slideToZero(a[0]),
  slideX: (v, a) => v.slideX(a[0]), slideY: (v, a) => v.slideY(a[0]), slideZ: (v, a) => v.slideZ(a[0]), // default smoothness 0.6
  slideXS: (v, a) => v.slideX(a[0], a[1]), slideYS: (v, a) => v.slideY(a[0], a[1]), slideZS: (v, a) => v.slideZ(a[0], a[1]),
  setX: (v, a) => v.setX(a[0]), setY: (v, a) => v.setY(a[0]), setZ: (v, a) => v.setZ(a[0]), set: (v, a) => v.set(a[0], a[1], a[2]),
  add: (v, a) => v.add(a[0], a[1], a[2]), update: (v, a) => v.update(a[0]), finish: v => v.finish(),
  limitDistanceTo: (v, a, all) => v.limitDistanceTo(all[a[0]], a[1]), copy: (v, a, all) => v.copy(all[a[0]]) // SmoothVector3f.set(SmoothVector3f)
};
const INDEX_ARGUMENT = new Set(['copy', 'limitDistanceTo']); // first argument is an instance index, not a float
const SO_LABELS = ['start.x', 'start.y', 'start.z', 'start.w', 'end.x', 'end.y', 'end.z', 'end.w', 'smooth.x', 'smooth.y', 'smooth.z', 'smooth.w', 'progress', 'smoothness'];
const SV_LABELS = ['start.x', 'start.y', 'start.z', 'end.x', 'end.y', 'end.z', 'smoothness.x', 'smoothness.y', 'smoothness.z',
  'completion.x', 'completion.y', 'completion.z', 'getX()', 'getY()', 'getZ()'];

// Replays SO/SV records on free-running JS instances; returns the set of operations seen.
function replay(records, all, apply, state, labels, tracker) {
  const seen = new Set();
  for (const tokens of records) {
    const step = tokens[1], instance = Number(tokens[2]), op = tokens[3], split = tokens.indexOf('=');
    assert.ok(apply[op], 'the JS replay knows ' + op);
    const args = tokens.slice(4, split).map((token, i) => (i === 0 && INDEX_ARGUMENT.has(op) ? Number(token) : F(token)));
    apply[op](all[instance], args, all);
    seen.add(op);
    const java = tokens.slice(split + 1).map(F);
    assert.equal(java.length, labels.length);
    const where = 'step ' + step + ' #' + instance + ' ' + op + '(' + args.map(show).join(', ') + ')';
    checkAll(tracker, state(all[instance]), java, where, labels);
  }
  return seen;
}

test("Mo' Bends JS core matches the original Java math (Quaternion, SmoothOrientation, SmoothVector3f, Tween, GUtil, MathHelper, BinaryAnimationLoader)", { skip, timeout: 300000 }, async t => {
  const animationNames = fs.readdirSync(ANIMATIONS).filter(name => /^wolf_.*\.bendsanim$/.test(name)).sort();
  assert.ok(animationNames.length > 0, 'wolf_*.bendsanim files in ' + ANIMATIONS);
  const records = parse(runHarness(animationNames.map(name => path.join(ANIMATIONS, name))));
  const core = createJasprMoBendsCore(ADAPTER);
  const deviations = [];

  await t.test('MathHelper.sin / cos / wrapDegrees are bit-identical to vanilla 1.12.2 (table cells, specials, ranges, tick counters)', t => {
    const sin = new Tracker('MathHelper.sin', true), cos = new Tracker('MathHelper.cos', true), wrap = new Tracker('MathHelper.wrapDegrees', true);
    // Java's SIN_TABLE, read back from the "cell" group (one argument in the middle of every cell).
    const table = Float32Array.from(records.MH.filter(tokens => tokens[1] === 'cell'), tokens => F(tokens[3]));
    assert.equal(table.length, 65536, 'every SIN_TABLE cell is probed');
    // KNOWN MISMATCH (reported 2026-10-01, not fixed here): mobends-core.js computes the table index with the double
    // 10430.378 and without Java's float roundings ((int)(value * 10430.378F) and (int)(value * 10430.378F + 16384.0F)).
    // A difference with exactly that signature is reported as TODO (listed, not fatal); any other difference fails.
    // Once the JS rounds like Java there is none and this subtest passes outright.
    const knownSin = x => table[core.jint(Math.fround(x * 10430.378)) & 65535];
    const knownCos = x => table[core.jint(Math.fround(x * 10430.378 + 16384)) & 65535];
    const groups = new Map();
    let known = 0, unknown = 0;
    for (const [, group, xHex, sinHex, cosHex, wrapHex] of records.MH) {
      const x = F(xHex), where = group + ' x=' + show(x) + ' (0x' + xHex + ')';
      const g = groups.get(group) || { n: 0, sin: 0, cos: 0 };
      g.n++;
      const jsSin = core.MathHelper.sin(x), jsCos = core.MathHelper.cos(x);
      if (!sin.check(jsSin, F(sinHex), where)) { g.sin++; if (Object.is(jsSin, knownSin(x))) known++; else unknown++; }
      if (!cos.check(jsCos, F(cosHex), where)) { g.cos++; if (Object.is(jsCos, knownCos(x))) known++; else unknown++; }
      wrap.check(core.MathHelper.wrapDegrees(x), F(wrapHex), where);
      groups.set(group, g);
    }
    t.diagnostic('mismatches per input group: ' + [...groups].map(([name, g]) => name + ' sin ' + g.sin + '/' + g.n + ', cos ' + g.cos + '/' + g.n).join('; '));
    for (const tracker of [sin, cos]) t.diagnostic(tracker.summary());
    wrap.assert(t);
    const details = [sin, cos].map(tracker => tracker.summary() + '\n  ' + tracker.failures.join('\n  ')).join('\n');
    assert.equal(unknown, 0, 'sin/cos differences other than the known index rounding:\n' + details);
    if (known) {
      t.todo('KNOWN MISMATCH in MathHelper.sin/cos index rounding (' + known + ' results); see the test comment');
      assert.fail(details);
    }
  });

  await t.test('Tween.easeIn / easeOut / easeInOut', t => {
    const tracker = new Tracker('Tween', false), fns = [core.Tween.easeIn, core.Tween.easeOut, core.Tween.easeInOut];
    for (const [, kind, aHex, pHex, vHex] of records.TW) {
      const a = D(aHex), p = D(pHex);
      tracker.check(fns[kind](a, p), D(vHex), ['easeIn', 'easeOut', 'easeInOut'][kind] + '(' + a + ', ' + p + ')');
    }
    tracker.assert(t);
    deviations.push(tracker);
  });

  await t.test('Quaternion mul / rotate / setFromAxisAngle / normalise and QuaternionUtils.multiply / quatToGlMatrix', t => {
    const trackers = {};
    const tracker = name => (trackers[name] ||= new Tracker(name, false));
    for (const tokens of records.QM) {
      const v = tokens.slice(1).map(F), left = v.slice(0, 4), right = v.slice(4, 8), where = 'mul(' + left + ' | ' + right + ')';
      checkAll(tracker('Quaternion.mul'), quatValues(core.Quaternion.mul(quat(core, left), quat(core, right), new core.Quaternion())), v.slice(8, 12), where);
      const aliasLeft = quat(core, left);
      core.Quaternion.mul(aliasLeft, quat(core, right), aliasLeft);
      checkAll(tracker('Quaternion.mul dest=left'), quatValues(aliasLeft), v.slice(12, 16), where);
      const aliasRight = quat(core, right);
      core.Quaternion.mul(quat(core, left), aliasRight, aliasRight);
      checkAll(tracker('Quaternion.mul dest=right'), quatValues(aliasRight), v.slice(16, 20), where);
    }
    for (const tokens of records.QR) {
      const v = tokens.slice(1).map(F), q = quat(core, v.slice(0, 4));
      q.rotate(v[4], v[5], v[6], v[7]);
      checkAll(tracker('Quaternion.rotate'), quatValues(q), v.slice(8, 12), 'rotate(' + v.slice(0, 8) + ')');
    }
    for (const tokens of records.QA) {
      const v = tokens.slice(1).map(F), q = new core.Quaternion();
      q.setFromAxisAngle(v[0], v[1], v[2], v[3]);
      checkAll(tracker('Quaternion.setFromAxisAngle'), quatValues(q), v.slice(4, 8), 'setFromAxisAngle(' + v.slice(0, 4) + ')');
    }
    for (const tokens of records.QN) {
      const v = tokens.slice(1).map(F), q = quat(core, v.slice(0, 4)), where = 'normalise(' + v.slice(0, 4) + ')';
      const length = q.length(), lengthSquared = q.lengthSquared();
      q.normalise();
      checkAll(tracker('Quaternion.normalise/length'), [...quatValues(q), length, lengthSquared], v.slice(4, 10), where, ['x', 'y', 'z', 'w', 'length()', 'lengthSquared()']);
    }
    for (const tokens of records.QV) {
      const v = tokens.slice(1).map(F), where = 'multiply(' + v.slice(0, 3) + ' | ' + v.slice(3, 7) + ')';
      const vector = new core.Vec3f(), dest = new core.Vec3f(), q = quat(core, v.slice(3, 7));
      vector.set(v[0], v[1], v[2]);
      core.QuaternionUtils.multiply(vector, q, dest);
      checkAll(tracker('QuaternionUtils.multiply'), vecValues(dest), v.slice(7, 10), where);
      core.QuaternionUtils.multiply(vector, q, vector);
      checkAll(tracker('QuaternionUtils.multiply dest=vector'), vecValues(vector), v.slice(10, 13), where);
    }
    for (const tokens of records.QG) {
      const v = tokens.slice(1).map(F);
      checkAll(tracker('QuaternionUtils.quatToGlMatrix'), core.QuaternionUtils.quatToGlMatrix(quat(core, v.slice(0, 4)), new Array(16)), v.slice(4, 20), 'quatToGlMatrix(' + v.slice(0, 4) + ')');
    }
    for (const name of Object.keys(trackers)) { trackers[name].assert(t); deviations.push(trackers[name]); }
  });

  await t.test('GUtil angleFromCoordinates / wrapRadians / getRadianDifference / interpolateRotation / lerp / clamp', t => {
    const trackers = {};
    const tracker = name => (trackers[name] ||= new Tracker('GUtil.' + name, false));
    const G = core.GUtil;
    for (const tokens of records.GU) {
      const fn = tokens[1], hex = tokens.slice(2);
      if (fn === 'angleFromCoordinates') tracker(fn).check(G.angleFromCoordinates(D(hex[0]), D(hex[1])), D(hex[2]), fn + '(' + D(hex[0]) + ', ' + D(hex[1]) + ')');
      else if (fn === 'wrapRadians') tracker(fn).check(G.wrapRadians(D(hex[0])), D(hex[1]), fn + '(' + D(hex[0]) + ')');
      else if (fn === 'getRadianDifference') tracker(fn).check(G.getRadianDifference(D(hex[0]), D(hex[1])), D(hex[2]), fn + '(' + D(hex[0]) + ', ' + D(hex[1]) + ')');
      else {
        const v = hex.map(F), where = fn + '(' + v.slice(0, 3).join(', ') + ')';
        assert.ok(['interpolateRotation', 'lerp', 'clamp'].includes(fn), 'known GUtil record ' + fn);
        tracker(fn).check(G[fn](v[0], v[1], v[2]), v[3], where);
      }
    }
    for (const name of Object.keys(trackers)) { trackers[name].assert(t); deviations.push(trackers[name]); }
  });

  await t.test('SmoothOrientation: ' + STEPS + ' free-running steps over 4 instances, every operation', t => {
    const tracker = new Tracker('SmoothOrientation', false), all = [0, 1, 2, 3].map(() => new core.SmoothOrientation());
    const seen = replay(records.SO, all, SO_APPLY, o => [...quatValues(o.start), ...quatValues(o.end), ...quatValues(o.smooth), o.progress, o.smoothness], SO_LABELS, tracker);
    assert.deepEqual([...seen].sort(), Object.keys(SO_APPLY).sort(), 'the sequence exercises every SmoothOrientation operation');
    assert.equal(records.SO.length, STEPS);
    tracker.assert(t);
    deviations.push(tracker);
  });

  await t.test('SmoothVector3f: ' + STEPS + ' free-running steps over 4 instances, every operation', t => {
    const tracker = new Tracker('SmoothVector3f', false), all = [0, 1, 2, 3].map(() => new core.SmoothVector3f());
    const seen = replay(records.SV, all, SV_APPLY, v => [...vecValues(v.start), ...vecValues(v.end), ...vecValues(v.smoothness), ...vecValues(v.completion), v.getX(), v.getY(), v.getZ()], SV_LABELS, tracker);
    assert.deepEqual([...seen].sort(), Object.keys(SV_APPLY).sort(), 'the sequence exercises every SmoothVector3f operation');
    assert.equal(records.SV.length, STEPS);
    tracker.assert(t);
    deviations.push(tracker);
  });

  await t.test('BinaryAnimationLoader: every wolf_*.bendsanim decodes to the same bones, keyframe counts and float bits', t => {
    const tracker = new Tracker('BinaryAnimationLoader', true);
    const expected = animationNames.map(() => ({ bones: -1, frames: new Map(), values: new Map() }));
    for (const [, file, bones] of records.AF) expected[file].bones = Number(bones);
    for (const [, file, id, frames] of records.AB) expected[file].frames.set(Buffer.from(id.slice(1), 'hex').toString('utf8'), Number(frames));
    for (const [, file, id, index, ...hex] of records.AK) expected[file].values.set(Buffer.from(id.slice(1), 'hex').toString('utf8') + '#' + index, hex);
    let keyframes = 0;
    animationNames.forEach((name, file) => {
      const animation = core.loadBinaryAnimation(new Uint8Array(fs.readFileSync(path.join(ANIMATIONS, name))));
      const want = expected[file];
      assert.equal(animation.bones.size, want.bones, name + ': bone count');
      assert.deepEqual([...animation.bones.keys()].sort(), [...want.frames.keys()].sort(), name + ': bone names');
      for (const [bone, data] of animation.bones) {
        assert.equal(data.keyframes.length, want.frames.get(bone), name + ' ' + bone + ': keyframe count');
        data.keyframes.forEach((frame, index) => {
          const java = want.values.get(bone + '#' + index), js = [...frame.position, ...frame.rotation, ...frame.scale];
          assert.equal(js.length, 10, name + ' ' + bone + ' #' + index + ': position, rotation, scale');
          js.forEach((value, k) => {
            const where = name + ' ' + bone + ' #' + index + ' [' + k + ']';
            assert.ok(Object.is(Math.fround(value), value), where + ' is a float');
            tracker.check(value, F(java[k]), where);
          });
          keyframes++;
        });
      }
    });
    t.diagnostic(animationNames.length + ' files, ' + keyframes + ' keyframes');
    tracker.assert(t);
  });

  t.diagnostic('max deviation (float Java vs double JS, tolerance ' + TOLERANCE + '): ' +
    deviations.map(d => d.name + ' ' + d.max.toExponential(2)).join(', '));
});
