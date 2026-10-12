'use strict';
// JASPR_FPS stage, offline: builds from the LIVE client (read only), loads it in node:vm and checks that every optimisation
// gives exactly what the engine's own code gives.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
try { os.setPriority(os.constants.priority.PRIORITY_LOW); } catch (e) { }
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');
const builder = require('../scripts/build-fps-client.cjs');
const LIVE = process.env.FPS_CLASSES_JS || builder.LIVE;
const skip = !fs.existsSync(LIVE);
let cached = null;
function load() {
  if (cached) return cached;
  const input = fs.readFileSync(LIVE, 'latin1');
  const built = builder.build(input);
  const client = loadClientSource(built.output, { filename: 'fps-candidate.js' });
  const ev = client.evaluate;
  // TeaVM's eager static initialisers (what main() runs first), then Bootstrap.register
  const gi = built.output.indexOf('function GNc(');
  const body = built.output.slice(gi, built.output.indexOf('$p=1;case 1:', gi));
  for (const init of body.slice(body.indexOf('case 0:') + 7).split(';').map(x => x.trim()).filter(x => /^[A-Za-z_$][\w$]*\(\)$/.test(x))) ev(init);
  cached = { input, built, ev };
  return cached;
}
const plain = v => JSON.parse(JSON.stringify(v));

test('build: parses, reversible, deterministic, outermost', { skip }, () => {
  const { input, built } = load();
  assert.equal(builder.unpatch(built.output), builder.unpatch(input));
  assert.equal(builder.build(input).output, built.output);
  assert.equal(builder.build(built.output).output, built.output, 'rebuilding on a built client gives the same bytes');
  for (const n of builder.WRAP) assert.equal(built.output.split('\nfunction ' + n + '$fps(').length - 1, 1, n);
});

test('random: next/nextInt/nextFloat/nextDouble/nextGaussian/nextLong/nextBoolean are bit-identical to the engine', { skip }, () => {
  const { ev } = load();
  const r = ev(`(function(){
    var out = { mismatches: [], draws: 0 };
    var seeds = [0, 1, 42, -1, 123456789, -987654321, 0x7fffffff, -0x80000000, 1e15, -1e15, 2463534242, 9007199254740991];
    function make(seed, scramble) { var a = new Ff(), b = new Ff(); var s = Long_fromNumber(seed); AYx(a, s, scramble); AYx(b, s, scramble); return [a, b]; }
    var bounds = [1, 2, 3, 7, 8, 16, 100, 255, 256, 1000, 1024, 65536, 0x40000000, 0x7fffffff];
    for (var si = 0; si < seeds.length; si++) for (var sc = 0; sc < 2; sc++) {
      var p = make(seeds[si], sc), fast = p[0], ref = p[1];
      for (var i = 0; i < 400; i++) {
        var kind = i % 7, x, y, bits = 1 + (i * 7) % 32, bd = bounds[i % bounds.length];
        // the same call twice: on the fast object with the fast path on, on the reference object with every wrapper falling
        // back to the engine's own code
        var call = function (o) { return kind === 0 ? YJ(o, bits) : kind === 1 ? H(o, bd) : kind === 2 ? X(o) : kind === 3 ? BN(o) : kind === 4 ? Cv(o) : kind === 5 ? String(F2(o)) : DD(o); };
        JasprFps.on.random = true; x = call(fast);
        JasprFps.on.random = false; y = call(ref);
        JasprFps.on.random = true;
        out.draws++;
        if (x !== y && !(x !== x && y !== y)) { out.mismatches.push([seeds[si], sc, i, kind, x, y]); if (out.mismatches.length > 5) return out; }
        if (i === 200) { var ns = Long_fromNumber(seeds[(si + 3) % seeds.length]); Gz(fast, ns); Gz(ref, ns); }
      }
    }
    return out;
  })()`);
  assert.deepEqual(plain(r.mismatches), []);
  assert.ok(r.draws > 9000);
});

test('random: a reference java.util.Random (BigInt) gives the same stream', { skip }, () => {
  const { ev } = load();
  const got = ev(`(function(){ var a = new Ff(); AYx(a, Long_fromNumber(42), 1); var o = []; for (var i = 0; i < 50; i++) o.push(H(a, 1000)); for (var j = 0; j < 20; j++) o.push(YJ(a, 32)); return o; })()`);
  // java.util.Random(42): scramble, then next(bits) = (int)(seed >>> (48 - bits))
  let seed = (42n ^ 0x5DEECE66Dn) & ((1n << 48n) - 1n);
  const next = bits => { seed = (seed * 0x5DEECE66Dn + 0xBn) & ((1n << 48n) - 1n); return Number(BigInt.asIntN(32, seed >> BigInt(48 - bits))); };
  const nextInt = bound => { let r = next(31); const m = bound - 1; if ((bound & m) === 0) return Number((BigInt(bound) * BigInt(r)) >> 31n); for (let u = r; u - (r = u % bound) + m < 0; u = next(31)); return r; };
  const want = [];
  for (let i = 0; i < 50; i++) want.push(nextInt(1000));
  for (let j = 0; j < 20; j++) want.push(next(32));
  assert.deepEqual(plain(got), want);
});

test('entity: data-manager entry cache and Matrix4f.store give what the engine gives', { skip }, () => {
  const { ev } = load();
  const r = ev(`(function(){
    var out = { bad: [], checks: 0 };
    JasprFps.on.entity = true;
    var m = new BMl(), map = new A2T(); Bu3(map); m.a2m = map;
    var ids = [0, 1, 2, 5, 13, 31, 254], keys = [];
    for (var i = 0; i < ids.length; i++) { var k = new BhB(); k.b0H = ids[i]; keys.push(k); D6R(map, ids[i], GNu(k, 'v' + i)); }
    var missing = new BhB(); missing.b0H = 7;
    for (var round = 0; round < 3; round++) for (var j = 0; j < keys.length; j++) {
      var fast = FGU(m, keys[j]), ref = FGU$fps(m, keys[j]); out.checks++;
      if (fast !== ref || E24(m, keys[j]) !== 'v' + j) out.bad.push(['entry', round, j]);
    }
    if (FGU(m, missing) !== null || FGU(m, missing) !== FGU$fps(m, missing)) out.bad.push(['missing']);
    // register resets the array (an id above 254 is refused by the engine after the reset)
    var big = new BhB(); big.b0H = 300;
    try { FkW(m, big, null); } catch (e) { }
    if (m.$fpsE !== null) out.bad.push(['register']);
    // Matrix4f.store: the same floats at the same position, the same position after, the same failure when it does not fit
    var mat = new ALo(), f = ['h_','h$','ia','g4','h7','h9','h8','g3','h5','hy','h6','gy','lB','lD','lC','jU'];
    for (var q = 0; q < 16; q++) mat[f[q]] = Math.fround(Math.sin(q * 7.3) * 1000 / (q + 1));
    var mk = function (pos, cap) { return { s8: pos, Ti: cap, a0H: new Float32Array(40) }; };
    for (var pos = 0; pos <= 24; pos += 3) for (var cap = 16; cap <= 40; cap += 12) {
      var A = mk(pos, cap), Bb = mk(pos, cap), ea = null, eb = null;
      try { Fij(mat, A); } catch (e) { ea = 'threw'; }
      JasprFps.on.entity = false; try { Fij(mat, Bb); } catch (e) { eb = 'threw'; } JasprFps.on.entity = true;
      out.checks++;
      if (ea !== eb || A.s8 !== Bb.s8 || A.a0H.join() !== Bb.a0H.join()) out.bad.push(['store', pos, cap, ea, eb]);
    }
    return out;
  })()`);
  assert.deepEqual(plain(r.bad), []);
  assert.ok(r.checks > 20);
});

test('random: switching the fast path off and on mid-stream continues the same java.util.Random stream', { skip }, () => {
  const { ev } = load();
  const got = ev(`(function(){ var a = new Ff(); AYx(a, Long_fromNumber(-7), 1); var o = [];
    for (var i = 0; i < 300; i++) { JasprFps.on.random = (i % 7) < 4; o.push(YJ(a, 1 + i % 32)); }
    JasprFps.on.random = true; return o; })()`);
  let seed = (-7n ^ 0x5DEECE66Dn) & ((1n << 48n) - 1n);
  const next = bits => { seed = (seed * 0x5DEECE66Dn + 0xBn) & ((1n << 48n) - 1n); return Number(BigInt.asIntN(32, seed >> BigInt(48 - bits))); };
  const want = [];
  for (let i = 0; i < 300; i++) want.push(next(1 + i % 32));
  assert.deepEqual(plain(got), want);
});
