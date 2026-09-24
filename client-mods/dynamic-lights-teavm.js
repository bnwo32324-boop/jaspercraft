/* Dynamic lights browser glue. Audited against the live bundle:
 * DtH(a,b,c) is RegionRenderCache.getCombinedLight (sole mesher light sampler,
 * called by flat path CsD and smooth path EpH); the build renames it to DtH_orig
 * and this file provides the wrapper. DbP case 42 runs every frame with the
 * renderGlobal (a.fd=Minecraft, a.d8=world, a.Pp=RenderManager, a.crE 6-arg =
 * markBlockRangeForRenderUpdate via GuH +-1 delegation). Scoreboard read mirrors
 * waypoint-markers-teavm.js (world.k3, Cbd/EEW, holder X5, display a47).
 * Key poll mirrors stats-keybind CFB/Dka shape; the action is local-only so no
 * fiber tick is needed. Mark dirty-flags through a tiny fiber (commit only after
 * Mark returns, so resume/re-run can neither lose nor double-issue).
 */
var JasprDynamicLightsKeyDescription = null, JasprDynamicLightsKeyLabel = null;

var JasprDynamicLights = (function () {
  "use strict";
  var MODE = 1, SOURCES = [], LAST_SIG = "", LAST_BOX = null, FRAME = 0;
  var MAX_SOURCES = 48;

  function loadMode() {
    try {
      var store = $rt_globals.localStorage;
      if (!store) return 1;
      var raw = store.getItem("jaspr.dl.mode");
      if (raw === "0" || raw === "2") return parseInt(raw, 10);
      return 1;
    } catch (e) { return 1; }
  }
  MODE = loadMode();

  function ustr(value) {
    if (typeof value === "string") return value;
    return $rt_ustr(value);
  }

  function readHolders(renderGlobal) {
    var out = [];
    var mc = renderGlobal && renderGlobal.fd;
    var world = (mc && mc.X) || (renderGlobal && renderGlobal.d8);
    var sb = world && world.k3;
    if (!sb) return out;
    var obj;
    try { obj = Cbd(sb, $rt_str("jdl")); } catch (e) { return out; }
    if (!obj) return out;
    var display = "";
    try { display = ustr(obj.a47); } catch (e) { return out; }
    if (!display || display.indexOf("JDL v1") !== 0) return out;
    var scores;
    try { scores = EEW(sb, obj); } catch (e) { return out; }
    var list = (scores && scores.qN) ? Array.prototype.slice.call(scores.qN.data, 0, scores.g) : [];
    for (var i = 0; i < list.length; i++) {
      try { out.push(ustr(list[i].X5)); } catch (e) { continue; }
    }
    return out;
  }

  // The old adapter used a fixed 22-block pad for every source and every
  // mode. That is the maximum SMOOTH radius for level 15, but it makes the
  // FAST default invalidate a 45^3 block volume whenever a held light moves.
  // Bound each source to the radius actually used by the sampler instead.
  function boxOf(sources) {
    if (!sources.length) return null;
    var x1 = Infinity, y1 = Infinity, z1 = Infinity, x2 = -Infinity, y2 = -Infinity, z2 = -Infinity;
    for (var i = 0; i < sources.length; i++) {
      var s = sources[i];
      var radius;
      try { radius = dynamicLightsRadius(s.level | 0, MODE) | 0; }
      catch (e) { radius = s.level | 0; }
      if (s.x - radius < x1) x1 = s.x - radius;
      if (s.y - radius < y1) y1 = s.y - radius;
      if (s.z - radius < z1) z1 = s.z - radius;
      if (s.x + radius > x2) x2 = s.x + radius;
      if (s.y + radius > y2) y2 = s.y + radius;
      if (s.z + radius > z2) z2 = s.z + radius;
    }
    return [x1, y1, z1, x2, y2, z2];
  }

  function union(a, b) {
    if (!a) return b;
    if (!b) return a;
    return [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
      Math.max(a[3], b[3]), Math.max(a[4], b[4]), Math.max(a[5], b[5])];
  }

  return {
    mode: function () { return MODE; },
    cycle: function () {
      MODE = MODE === 1 ? 2 : (MODE === 2 ? 0 : 1);
      try { if ($rt_globals.localStorage) $rt_globals.localStorage.setItem("jaspr.dl.mode", String(MODE)); }
      catch (e) { /* in-memory mode still applies */ }
      return MODE;
    },
    reset: function () { SOURCES = []; LAST_SIG = ""; LAST_BOX = null; },
    active: function () { return MODE !== 0 && SOURCES.length > 0; },
    // Pure: compute the dirty box for this frame, or null. No commit yet.
    plan: function (renderGlobal) {
      FRAME++;
      if (MODE === 0) {
        if (LAST_SIG === "off") return null;
        var oldBox = LAST_BOX;
        SOURCES = [];
        return { sig: "off", box: oldBox, sources: [] };
      }
      if (MODE === 1 && (FRAME % 4) !== 0 && LAST_SIG !== "") return null;
      var holders;
      try { holders = readHolders(renderGlobal); }
      catch (e) { return null; }
      var sig = MODE + "|" + dynamicLightsSignature(holders);
      if (sig === LAST_SIG) return null;
      var sources = [];
      for (var i = 0; i < holders.length && sources.length < MAX_SOURCES; i++) {
        var parsed;
        try { parsed = dynamicLightsParseHolder(holders[i]); }
        catch (e) { continue; }
        if (parsed) sources.push(parsed);
      }
      var box = union(LAST_BOX, boxOf(sources));
      SOURCES = sources;
      return { sig: sig, box: box, sources: sources };
    },
    commit: function (done) {
      if (!done) return;
      LAST_SIG = done.sig;
      LAST_BOX = boxOf(done.sources);
    },
    sample: function (orig, a, b, c) {
      var nativeValue = orig(a, b, c);
      if (MODE === 0 || SOURCES.length === 0) return nativeValue;
      try {
        return dynamicLightsMix(nativeValue, c.m | 0, c.i | 0, c.l | 0, SOURCES, MODE);
      } catch (e) { return nativeValue; }
    },
    sampleWorld: function (orig, a, b, c) {
      return dynamicLightsSampleWorld(orig, a, b, c, MODE, SOURCES);
    },
    status: function () {
      return { mode: MODE, sources: SOURCES.length };
    }
  };
}());

function DtH(a, b, c) {
  return JasprDynamicLights.sample(DtH_orig, a, b, c);
}

function DQP(a, b, c) {
  return JasprDynamicLights.sampleWorld(DQP_orig, a, b, c);
}

// Fiber wrapper: dirty-mark a box, then commit the plan. Re-run safe.
function JasprDynamicLightsFrame(a) {
  var b, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprDynamicLights.plan(a); if (b === null) return;
      if (b.box === null) { JasprDynamicLights.commit(b); return; }
      $p = 1;
    case 1:
      JasprDynamicLightsMark(a, b.box[0], b.box[1], b.box[2], b.box[3], b.box[4], b.box[5]);
      if (B()) break _;
      JasprDynamicLights.commit(b);
      return;
    default: FT();
  } }
  Ds().s(a, b, $p);
}

function JasprDynamicLightsMark(a, b, c, d, e, f, g) {
  var $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      a.crE(b, c, d, e, f, g); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, g, $p);
}

var JasprDynamicLightsKey = {
  key: function (client, code, down, repeat) {
    try {
      var bound = client && client.G && client.G.$jasprDynamicLightsKey;
      if (!bound || !code || code !== bound.gO) return false;
      if (!down || repeat) return false;
      JasprDynamicLights.cycle();
      return true;
    } catch (e) { return false; }
  }
};

// Called once from B$i state 40 BEFORE DBw loads the account-restored options blob.
function JasprDynamicLightsInstall(a) {
  var b, c, d, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (a.$jasprDynamicLightsKey) return;
      if (JasprDynamicLightsKeyDescription === null) {
        JasprDynamicLightsKeyDescription = $rt_str("key.jaspr.dynamiclights");
        JasprDynamicLightsKeyLabel = $rt_str("Dynamic Lights");
      }
      b = new GO; c = JasprDynamicLightsKeyDescription; d = C(6273); $p = 1;
    case 1:
      BPd(b, c, 38, d); if (B()) break _;
      a.$jasprDynamicLightsKey = b;
      a.a$W = G6V(a.a$W, T(GO, [b]));
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

if (typeof window !== "undefined" && window) {
  try { window.JasprDynamicLightsDiagnostics = Object.freeze({ status: function () { return JasprDynamicLights.status(); } }); }
  catch (e) { /* diagnostics stay local-only */ }
}
