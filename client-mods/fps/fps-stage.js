/* JASPR_FPS: frame-time optimisations for the JasperCraft browser client (owner 2026-10-12: "massively optimized ... 165 frames
 * consistently ... clever optimizations ... preferably no compromises at all").
 *
 * Every optimisation here gives exactly the same picture and the same game: it only avoids doing the same work twice. Each one
 * is a memo of a pure lookup whose inputs cannot change while the memo is held; the world-change serial below is what makes
 * that hold:
 *   - the client world only changes inside Minecraft.runTick (packets, input, entity movement) and in the rare scheduled
 *     tasks, so every value derived from world blocks, light or entity positions is valid until the next of those;
 *   - the chunk map only changes in WorldClient.doPreChunk and ChunkProviderClient.unloadQueuedChunks.
 * Parts (each can be switched off in the browser: localStorage['jaspr.fps.v1'] = '{"memo":false}'):
 *   memo        ChunkCache (the region snapshot of one chunk rebuild / one path search) answers each block state and
 *               light query once; face culling asks every neighbour up to six times.
 *   chunks      ChunkProviderClient.provideChunk / getLoadedChunk answer from a small cache instead of hashing a 64-bit
 *               key (a BigInt in this client) for every block read.
 *   brightness  Entity.getBrightnessForRender / getBrightness are computed once per entity per world change instead of
 *               several times per frame (body, each layer, held items, name).
 * Diagnostics: JasprFpsStatus() in the page (hit/miss counters per part). */
var JasprFps = (function () {
  "use strict";
  var on = { memo: true, chunks: true, brightness: true, random: true, collide: true, rebuild: true, ticks: true, entity: true, verify: false };
  try {
    var saved = $rt_globals.localStorage && $rt_globals.localStorage.getItem('jaspr.fps.v1');
    if (saved) { var o = JSON.parse(saved); for (var k in o) if (k in on) on[k] = !!o[k]; }
  } catch (e) { }
  var stats = { memoHit: 0, memoMiss: 0, lightHit: 0, lightMiss: 0, chunkHit: 0, chunkMiss: 0, brightHit: 0, brightMiss: 0, airSkip: 0, verified: 0, mismatch: 0, mismatchAt: '', serial: 0 };
  return { on: on, stats: stats, serial: 1, chunkSerial: 1 };
})();
$rt_globals.JasprFpsStatus = function () { var s = {}; for (var k in JasprFps.stats) s[k] = JasprFps.stats[k]; s.serial = JasprFps.serial; s.on = JasprFps.on; return s; };

// verify mode (localStorage['jaspr.fps.v1'] = '{"verify":true}', tests only): every fast answer is checked against the engine's
// own code on the spot; JasprFpsStatus().mismatch must stay 0
function JasprFpsCheck(part, same) { JasprFps.stats.verified++; if (!same) { JasprFps.stats.mismatch++; if (!JasprFps.stats.mismatchAt) JasprFps.stats.mismatchAt = part; } }

// ---------------------------------------------------------------- world-change serial
// Minecraft.runTick: every world mutation of a client happens in here (or in a scheduled task, see runGameLoop below).
function CHq(a) { JasprFps.serial = JasprFps.serial + 1 | 0; return CHq$fps(a); }
// Util.runTask: a scheduled task (Minecraft.addScheduledTask) may change the world too
function GJv(b, c) { JasprFps.serial = JasprFps.serial + 1 | 0; var r = GJv$fps(b, c); JasprFps.serial = JasprFps.serial + 1 | 0; return r; }

// ---------------------------------------------------------------- ChunkCache memo (block states and combined light)
var JasprFpsMemo = (function () {
  var MAX = 1 << 15;                                           // 32768 positions (a chunk rebuild needs 18^3 = 5832)
  return { MAX: MAX, states: new Array(MAX), stamp: new Int32Array(MAX), light: new Int32Array(MAX), lightStamp: new Int32Array(MAX),
    owner: null, serial: 0, gen: 1 };
})();
// ChunkCache(World, BlockPos from, BlockPos to, int sub): remember the covered box for the memo
function BOz(a, b, c, d, e) {
  var r = BOz$fps(a, b, c, d, e);
  if (B()) return r;
  var sx = d.m - c.m + 1, sy = d.i - c.i + 1, sz = d.l - c.l + 1;
  if (sx > 0 && sy > 0 && sz > 0 && sx * sy * sz <= JasprFpsMemo.MAX) { a.$fpsX0 = c.m; a.$fpsY0 = c.i; a.$fpsZ0 = c.l; a.$fpsSx = sx; a.$fpsSy = sy; a.$fpsSz = sz; }
  else a.$fpsSx = 0;
  return r;
}
function JasprFpsMemoIndex(a, b) {
  var M = JasprFpsMemo;
  if (M.owner !== a || M.serial !== JasprFps.serial) {
    M.owner = a; M.serial = JasprFps.serial; M.gen = M.gen + 1 | 0;
    if (M.gen > 0x3FFFFFFF) { M.stamp.fill(0); M.lightStamp.fill(0); M.gen = 1; }
  }
  var x = b.m - a.$fpsX0 | 0, y = b.i - a.$fpsY0 | 0, z = b.l - a.$fpsZ0 | 0;
  if (x < 0 || y < 0 || z < 0 || x >= a.$fpsSx || y >= a.$fpsSy || z >= a.$fpsSz) return -1;
  return (x * a.$fpsSz + z) * a.$fpsSy + y;
}
// ChunkCache.getBlockState(BlockPos)
function ETg(a, b) {
  if (!JasprFps.on.memo || !a.$fpsSx || FX()) return ETg$fps(a, b);
  var i = JasprFpsMemoIndex(a, b), M = JasprFpsMemo;
  if (i < 0) return ETg$fps(a, b);
  if (M.stamp[i] === M.gen) { JasprFps.stats.memoHit++; if (JasprFps.on.verify) JasprFpsCheck('memo', ETg$fps(a, b) === M.states[i]); return M.states[i]; }
  var r = ETg$fps(a, b);
  if (B()) return r;
  M.states[i] = r; M.stamp[i] = M.gen; JasprFps.stats.memoMiss++;
  return r;
}
// ChunkCache.getCombinedLight(BlockPos, int lightValue): sky << 20 | max(block, lightValue) << 4. The two light levels of a
// position are memoised; the lightValue floor is applied per call exactly as the original does.
function FfJ(a, b, c) {
  if (!JasprFps.on.memo || !a.$fpsSx || FX()) return FfJ$fps(a, b, c);
  var i = JasprFpsMemoIndex(a, b), M = JasprFpsMemo, g, h;
  if (i < 0) return FfJ$fps(a, b, c);
  if (M.lightStamp[i] === M.gen) { JasprFps.stats.lightHit++; g = M.light[i] >> 8; h = M.light[i] & 255; }
  else {
    var d = ETg(a, b);
    if (B()) return 0;
    var e = d.n;
    Hq();
    g = EVn(e, d, a, KxR, b);
    if (B()) return 0;
    h = EVn(e, d, a, K5Q, b);
    if (B()) return 0;
    M.light[i] = (g << 8) | (h & 255); M.lightStamp[i] = M.gen; JasprFps.stats.lightMiss++;
  }
  if (h >= c) c = h;
  if (JasprFps.on.verify) JasprFpsCheck('light', FfJ$fps(a, b, c) === (g << 20 | c << 4));
  return g << 20 | c << 4;
}

// ---------------------------------------------------------------- chunk lookup cache
var JasprFpsChunks = (function () {
  var N = 1024;
  return { N: N, x: new Int32Array(N), z: new Int32Array(N), chunk: new Array(N), stamp: new Int32Array(N), owner: null, serial: 0, gen: 1 };
})();
function JasprFpsChunkSlot(a, b, c) {
  var C = JasprFpsChunks;
  if (C.owner !== a || C.serial !== JasprFps.chunkSerial) {
    C.owner = a; C.serial = JasprFps.chunkSerial; C.gen = C.gen + 1 | 0;
    if (C.gen > 0x3FFFFFFF) { C.stamp.fill(0); C.gen = 1; }
  }
  var s = ((b & 31) << 5) | (c & 31);
  return (C.stamp[s] === C.gen && C.x[s] === b && C.z[s] === c) ? s : ~s;
}
// ChunkProviderClient.provideChunk(x, z): the loaded chunk or the blank chunk
function F_U(a, b, c) {
  if (!JasprFps.on.chunks || FX()) return F_U$fps(a, b, c);
  var C = JasprFpsChunks, s = JasprFpsChunkSlot(a, b, c);
  if (s >= 0) { JasprFps.stats.chunkHit++; if (JasprFps.on.verify) JasprFpsCheck('chunks', F_U$fps(a, b, c) === C.chunk[s]); return C.chunk[s]; }
  s = ~s;
  var r = F_U$fps(a, b, c);
  if (B()) return r;
  C.x[s] = b; C.z[s] = c; C.chunk[s] = r; C.stamp[s] = C.gen; JasprFps.stats.chunkMiss++;
  return r;
}
// the two places that change the chunk map: WorldClient.doPreChunk (load/unload) and unloadQueuedChunks
function Cu_(a, b, c, d) { JasprFps.chunkSerial = JasprFps.chunkSerial + 1 | 0; var r = Cu_$fps(a, b, c, d); JasprFps.chunkSerial = JasprFps.chunkSerial + 1 | 0; return r; }
function E9P(a) { JasprFps.chunkSerial = JasprFps.chunkSerial + 1 | 0; var r = JasprFps.on.ticks && !FX() ? JasprFpsUnloadQueued(a) : E9P$fps(a); JasprFps.chunkSerial = JasprFps.chunkSerial + 1 | 0; return r; }
// ChunkProviderClient.unloadQueuedChunks: every loaded chunk's onTick(System.currentTimeMillis() - start > 5), in the map's own
// iteration order (the same iterator), then the slow-tick log line. The engine boxes the clock into a 64-bit long (a BigInt)
// for every chunk; this reads the same clock (System.currentTimeMillis = floor(HB8())) as a number.
function JasprFpsUnloadQueued(a) {
  GJ(); Dr();
  var b = Math.floor(HB8()), c = a.a0G, d = new BMM;
  d.dIL = c; AXs(d); d.cEE = new A6f;
  var e = B1f(c); d.dIO = AZq(e); d.cAc = e & c.Dk;
  while (R5(d)) Fsa(Tz(d).a5H, Math.floor(HB8()) - b <= 5 ? 0 : 1);
  var f = Math.floor(HB8());
  if (f - b <= 100) return 0;
  G6a();
  var g = G(D, 1); g.data[0] = Fh(Long_fromNumber(f - b));
  ETe(LOb, C(10122), g);
  return 0;
}

// ---------------------------------------------------------------- entity brightness
// Entity.getBrightnessForRender(): combined light at (floor x, floor(y + eye height), floor z); 0 where the column is not loaded
function Ei5(a) {
  if (!JasprFps.on.brightness || FX()) return Ei5$fps(a);
  var e = a.hz();
  if (a.$fpsBs === JasprFps.serial && a.$fpsBx === a.b && a.$fpsBy === a.f && a.$fpsBz === a.c && a.$fpsBe === e) { JasprFps.stats.brightHit++; if (JasprFps.on.verify) JasprFpsCheck('brightness', Ei5$fps(a) === a.$fpsBv); return a.$fpsBv; }
  var r = Ei5$fps(a);
  if (B()) return r;
  a.$fpsBs = JasprFps.serial; a.$fpsBx = a.b; a.$fpsBy = a.f; a.$fpsBz = a.c; a.$fpsBe = e; a.$fpsBv = r; JasprFps.stats.brightMiss++;
  return r;
}
// Entity.getBrightness(): the same position, World.getLightBrightness
function Dy0(a) {
  if (!JasprFps.on.brightness || FX()) return Dy0$fps(a);
  var e = a.hz();
  if (a.$fpsLs === JasprFps.serial && a.$fpsLx === a.b && a.$fpsLy === a.f && a.$fpsLz === a.c && a.$fpsLe === e) { JasprFps.stats.brightHit++; if (JasprFps.on.verify) JasprFpsCheck('brightness2', Dy0$fps(a) === a.$fpsLv); return a.$fpsLv; }
  var r = Dy0$fps(a);
  if (B()) return r;
  a.$fpsLs = JasprFps.serial; a.$fpsLx = a.b; a.$fpsLy = a.f; a.$fpsLz = a.c; a.$fpsLe = e; a.$fpsLv = r; JasprFps.stats.brightMiss++;
  return r;
}

// ---------------------------------------------------------------- EaglercraftRandom without BigInt
// java.util.Random's 48-bit LCG (seed = seed * 0x5DEECE66D + 0xB mod 2^48) kept as two 24-bit halves in plain numbers: every
// product stays below 2^53, so each step and each result is bit-identical to Java's. The BigInt seed field (bJv) stays the
// source of truth whenever the game sets a seed (setSeed and the constructors go through Gz / the field initialiser).
var JasprFpsPow2 = (function () { var p = []; for (var i = 0; i <= 48; i++) p.push(Math.pow(2, i)); return p; })();
function JasprFpsRandomSync(a) {
  var v = a.bJv;
  if (typeof v === 'bigint') {
    var u = $rt_globals.BigInt.asUintN(48, v);
    a.$rh = Number(u >> $rt_globals.BigInt(24)); a.$rl = Number(u & $rt_globals.BigInt(16777215));
  } else {                                                    // TeaVM's Long object (browsers without BigInt)
    var lo = v.lo >>> 0, hi = v.hi & 0xFFFF;
    a.$rl = lo & 0xFFFFFF; a.$rh = hi * 256 + (lo >>> 24);
  }
  a.$rv = 1;
}
// switched off at runtime: the halves are written back to the seed field first, so the engine continues the same stream
function JasprFpsRandomFlush(a) {
  var v = a.$rh * 16777216 + a.$rl;                         // < 2^48, exact
  a.bJv = typeof a.bJv === 'bigint' ? $rt_globals.BigInt(v) : Long_fromNumber(v);
  a.$rv = 0;
}
// Random.next(bits)
function YJ(a, b) {
  if (!JasprFps.on.random) { if (a.$rv === 1) JasprFpsRandomFlush(a); return YJ$fps(a, b); }
  if (a.$rv !== 1) JasprFpsRandomSync(a);
  var lo = a.$rl, hi = a.$rh;
  var p0 = lo * 15525485 + 11;                                // low 24 bits of the multiplier: 0xECE66D
  var nlo = p0 % 16777216, c0 = (p0 - nlo) / 16777216;
  var nhi = (hi * 15525485 + lo * 1502 + c0) % 16777216;     // high bits of the multiplier: 0x5DE
  a.$rl = nlo; a.$rh = nhi;
  return Math.floor((nhi * 16777216 + nlo) / JasprFpsPow2[48 - b]) | 0;
}
// Random.setSeed: the BigInt seed changed, the halves resync on the next draw
function Gz(a, b) { Gz$fps(a, b); a.$rv = 0; }
// Random.nextInt(bound): for a power of two, (int)((bound * (long)next(31)) >> 31) is next(31) >> (31 - log2(bound))
function H(a, b) {
  if (!JasprFps.on.random || b <= 0) return H$fps(a, b);
  var c = YJ(a, 31), d = b - 1 | 0, e;
  if (!(b & d)) return c >> Math.clz32(b);
  while (true) { e = c % b | 0; if (((c - e | 0) + d | 0) >= 0) break; c = YJ(a, 31); }
  return e;
}
// Random.nextDouble: ((long)next(26) << 27) + next(27), exact in a double
function BN(a) {
  if (!JasprFps.on.random) return BN$fps(a);
  var hi = YJ(a, 26);
  return (hi * 134217728 + YJ(a, 27)) * 1.1102230246251565E-16;
}

// ---------------------------------------------------------------- World.getCollisionBoxes (func_191504_a) fast path
// The same loops, border logic and calls as the engine, except: the chunk of each column is looked up once instead of once
// per block, and air is not asked for its collision box (BlockAir's box is NULL_AABB, so it never adds anything).
function C8D(a, b, c, d, e) {
  if (!JasprFps.on.collide || FX()) return C8D$fps(a, b, c, d, e);
  var h = Gmc(c.ct) - 1 | 0, j = Ghr(c.cH) + 1 | 0, i = Gmc(c.bv) - 1 | 0, g = Ghr(c.cl) + 1 | 0, l = Gmc(c.cz) - 1 | 0, m = Ghr(c.cK) + 1 | 0;
  var n = a.ek, o = b !== null && b.biL ? 1 : 0, p = b !== null ? (FiG(a, b) ? 1 : 0) : 0;
  Z();
  var q = KEE.e, air = HNM.e, r = Di(), s = g - 1 | 0, t = m - 1 | 0, u = j - 1 | 0, stats = JasprFps.stats;
  for (var v = h; v < j; v = v + 1 | 0) {
    for (var w = l; w < m; w = w + 1 | 0) {
      var k = v == h || v == u ? 1 : 0, x = w == l || w == t ? 1 : 0;
      if (k && x) continue;
      Cf(r, v, 64, w);
      if (!CmD(a, r)) continue;
      var chunk = EDn(a, r);
      for (var ba = i; ba < g; ba = ba + 1 | 0) {
        if (!(!k && !x || ba != s)) continue;
        if (!d) { if (b !== null && o == p) b.biL = p ? 0 : 1; }
        else if (!(v >= -30000000 && v < 30000000 && w >= -30000000 && w < 30000000)) return 1;
        Cf(r, v, ba, w);
        var y;
        if (!d && p && !DKv(n, r)) y = q;                      // the border test only matters inside the border (p); both are pure
        else y = (ba >= 0 && ba < 256) ? chunk.cO(r) : air;
        if (y === air) { stats.airSkip++; continue; }
        ECa(y, a, r, c, e, b, 0);
        if (d && !E3i(e)) return 1;
      }
    }
  }
  var res = E3i(e) ? 0 : 1;
  if (JasprFps.on.verify && !d) JasprFpsVerifyCollide(a, b, c, e, res);
  return res;
}
function JasprFpsVerifyCollide(a, b, c, e, res) {
  var biL = b !== null ? b.biL : 0, mine = M2L(e), ref = Bq();
  var r2 = C8D$fps(a, b, c, 0, ref);
  if (b !== null) b.biL = biL;
  var A = M2L(e), Bl = M2L(ref), same = r2 === res && A.length === Bl.length;
  for (var i = 0; same && i < A.length; i++) { var p = A[i], q = Bl[i]; same = p.ct === q.ct && p.bv === q.bv && p.cz === q.cz && p.cH === q.cH && p.cl === q.cl && p.cK === q.cK; }
  JasprFpsCheck('collide', same);
}
function M2L(l) { var n = l.bl(), o = []; for (var i = 0; i < n; i++) o.push(l.c4(i)); return o; }

// ---------------------------------------------------------------- chunk meshing: flat (non-AO) block models
// Block.shouldSideBeRendered: the engine's own checks in the same order; the neighbour is read through one scratch position
// instead of a new BlockPos per face (block-state lookups only read its coordinates).
var JasprFpsSide = null;
function JasprFpsSideInit() {
  Bw();
  var faces = KEm.data, s = { x: [], y: [], z: [], pos: new Ba() };
  Bp(s.pos, 0, 0, 0);
  for (var i = 0; i < faces.length; i++) { var f = faces[i]; s.x[f.d] = COB(f); s.y[f.d] = D4O(f); s.z[f.d] = Fhu(f); }
  JasprFpsSide = s;
}
function EF8(a, b, c, d, e) {
  if (!JasprFps.on.rebuild || FX()) return EF8$fps(a, b, c, d, e);
  if (JasprFpsSide === null) JasprFpsSideInit();
  var f = Fqx(b, c, d);                                       // state.getBoundingBox(world, pos)
  HvO();
  switch (KIM.data[e.d]) {
    case 1: if (f.bv > 0.0) return 1; break;
    case 2: if (f.cl < 1.0) return 1; break;
    case 3: if (f.cz > 0.0) return 1; break;
    case 4: if (f.cK < 1.0) return 1; break;
    case 5: if (f.ct > 0.0) return 1; break;
    case 6: if (f.cH < 1.0) return 1; break;
    default: break;
  }
  var S = JasprFpsSide, p = S.pos, o = e.d;
  p.m = d.m + S.x[o] | 0; p.i = d.i + S.y[o] | 0; p.l = d.l + S.z[o] | 0;
  var r = Dd9(c.cO(p)) ? 0 : 1;                               // !world.getBlockState(pos.offset(side)).isOpaqueCube()
  if (JasprFps.on.verify) JasprFpsCheck('side', EF8$fps(a, b, c, d, e) === r);
  return r;
}
// BlockModelRenderer.renderModelFlat: a face is drawn when the model has quads for it and the side is visible. Both checks
// are side-effect free, so the visibility test runs first and a hidden face never asks the model for its quads. The
// faces, their order, the light and the quad emitter (renderQuadsFlat) are the engine's own. renderQuadsFlat writes both
// BitSet bits before it reads them, so one BitSet serves every block.
var JasprFpsBits = null;
function F85(a, b, c, d, e, f, g, h) {
  if (!JasprFps.on.rebuild || FX()) return F85$fps(a, b, c, d, e, f, g, h);
  if (JasprFpsBits === null) JasprFpsBits = A9v(3);
  var i = 0, j = JasprFpsBits;
  Bw();
  var faces = KEm.data;
  for (var l = 0; l < faces.length; l++) {
    var o = faces[l];
    if (g && !CiB(d, b, e, o)) continue;                      // state.shouldSideBeRendered(world, pos, side)
    var p = c.GN(d, o, h);                                    // model.getQuads(state, side, rand)
    if (p.c_()) continue;
    CsD(a, b, d, e, DtH(d, b, Eua(e, o)), 0, f, p, j);        // light of the neighbour; renderQuadsFlat
    i = 1;
  }
  var n = c.GN(d, null, h);
  if (n.c_()) return i;
  CsD(a, b, d, e, -1, 1, f, n, j);
  return 1;
}
// BlockModelRenderer.renderModelSmooth (ambient occlusion on): the same face order and checks as above. The engine allocates
// a quad-bounds array, a BitSet and an AmbientOcclusionFace (with six scratch positions) for every block; renderQuadsSmooth
// overwrites all of them before reading, so one set serves every block.
var JasprFpsAO = null;
function FTY(a, b, c, d, e, f, g, h) {
  if (!JasprFps.on.rebuild || FX()) return FTY$fps(a, b, c, d, e, f, g, h);
  Bw();
  if (JasprFpsAO === null) {
    var l = new BNV, m;
    l.Oy = Ej(4); l.Yh = Bh(4);
    m = new Ba; Bp(m, 0, 0, 0); l.dvf = m; m = new Ba; Bp(m, 0, 0, 0); l.ba_ = m; m = new Ba; Bp(m, 0, 0, 0); l.OB = m;
    m = new Ba; Bp(m, 0, 0, 0); l.bSu = m; m = new Ba; Bp(m, 0, 0, 0); l.bSt = m; m = new Ba; Bp(m, 0, 0, 0); l.kx = m;
    JasprFpsAO = { j: Ej(KEm.data.length * 2 | 0), k: A9v(3), l: l };
  }
  var A = JasprFpsAO, i = 0, faces = KEm.data;
  for (var o = 0; o < faces.length; o++) {
    var face = faces[o];
    if (g && !CiB(d, b, e, face)) continue;                   // state.shouldSideBeRendered(world, pos, side)
    var r = c.GN(d, face, h);                                 // model.getQuads(state, side, rand)
    if (r.c_()) continue;
    GCz(a, b, d, e, f, r, A.j, A.k, A.l);                     // renderQuadsSmooth
    i = 1;
  }
  var q = c.GN(d, null, h);
  if (q.c_()) return i;
  GCz(a, b, d, e, f, q, A.j, A.k, A.l);
  return 1;
}

// ---------------------------------------------------------------- entity rendering
// EntityDataManager.getEntry(key): the entry objects of a manager are created once (register) and only their values change
// afterwards (set, setEntryValues write into the same entry), so each manager keeps its entries in an array by key id
// instead of boxing the id and probing the hash map for every get (a mob reads several per frame: flags, health, pose ...).
function FGU(a, b) {
  if (!JasprFps.on.entity || FX()) return FGU$fps(a, b);
  var t = a.$fpsE, id = b.b0H, e;
  if (t !== null && (e = t[id]) !== undefined) { if (JasprFps.on.verify) JasprFpsCheck('entry', FGU$fps(a, b) === e); return e; }
  var r = FGU$fps(a, b);
  if (B()) return r;
  if (r !== null) { if (t === null) t = a.$fpsE = []; t[id] = r; }
  return r;
}
// EntityDataManager.register: a new entry, the array is rebuilt on the next read
function FkW(a, b, c) { a.$fpsE = null; return FkW$fps(a, b, c); }
// Matrix4f.store(FloatBuffer): the sixteen puts of the engine written straight into the buffer's array when they all fit
// (when they do not, the engine's own code runs and fails at the same element as before)
function Fij(a, b) {
  if (!JasprFps.on.entity || FX()) return Fij$fps(a, b);
  var c = b.s8;
  if (c < 0 || (c + 16 | 0) > b.Ti) return Fij$fps(a, b);
  var d = b.a0H;
  d[c] = a.h_; d[c + 1] = a.h$; d[c + 2] = a.ia; d[c + 3] = a.g4; d[c + 4] = a.h7; d[c + 5] = a.h9; d[c + 6] = a.h8; d[c + 7] = a.g3;
  d[c + 8] = a.h5; d[c + 9] = a.hy; d[c + 10] = a.h6; d[c + 11] = a.gy; d[c + 12] = a.lB; d[c + 13] = a.lD; d[c + 14] = a.lC; d[c + 15] = a.jU;
  b.s8 = c + 16 | 0;
  return a;
}
