/* Browser glue for waypoint markers. Audited against the live bundle: RenderGlobal
 * DbP case 42 runs after the entity pass with the world modelview active (same site
 * as JasprGoreBridge.render); a.Pp.bP0/bP1/bPZ is the fresh camera-relative origin;
 * a.fd is Minecraft (AGY), a.fd.X the world with .k3 scoreboard; Cbd(sb, name) is
 * getObjective, EEW(sb, obj) getSortedScores; Score fields X5 holder, jk points;
 * objective display string a47. GL helpers mirror gore-teavm.js drawLocal exactly.
 * Everything fails closed: any unexpected shape disables markers until reset.
 */
var JasprWaypointMarkers = (function () {
  "use strict";
  var runtime = null;

  function ensure() {
    if (!runtime) runtime = createJasprWaypointMarkers({});
    return runtime;
  }

  function wpDraw(mesh) {
    if (!mesh.length) return;
    var t = GdM(), buffer = t.dy;
    Ep0(buffer, 7, Lq0);
    for (var i = 0; i < mesh.length; i += 8) {
      CUb(buffer, mesh[i], mesh[i + 1], mesh[i + 2]);
      EpJ(buffer, mesh[i + 3], mesh[i + 4]);
      Gu1(buffer, mesh[i + 5], mesh[i + 6], mesh[i + 7]);
      E74(buffer);
    }
    FE$(t);
  }

  function wpSaveState() {
    return { mode: KrH, texture: HEn.data[0], active: HEo, tex0: Krr.data[0], tex1: Krr.data[1],
      lighting: Krh, cull: HHT, color: [HKI, HKJ, HKK, HKL], light: [KrK.data[1], KrL.data[1]] };
  }

  function wpRestore(s) {
    GnI(33984); FUe(s.texture); if (s.tex0) CQ6(); else DCQ();
    GnI(33985); if (s.tex1) CQ6(); else DCQ(); G0W(33985, s.light[0], s.light[1]);
    if (s.lighting) ElS(); else Fpb(); if (s.cull) Ggy(); else F1Q();
    CFh(s.color[0], s.color[1], s.color[2], 1); GnI(33984 + s.active); DSz(s.mode);
  }

  function wpDrawLocal(mesh, color) {
    var s = wpSaveState();
    try {
      GnI(33984); DCQ();
      CFh(color[0], color[1], color[2], 1);
      wpDraw(mesh);
    } finally { wpRestore(s); }
  }

  function ustr(value) {
    if (typeof value === "string") return value;
    return $rt_ustr(value);
  }

  function readEntries(renderGlobal) {
    var out = [];
    var mc = renderGlobal && renderGlobal.fd;
    var world = mc && mc.X;
    var sb = world && world.k3;
    if (!sb) return out;
    var obj;
    try { obj = Cbd(sb, $rt_str("jwp")); } catch (e) { return out; }
    if (!obj) return out;
    var display = "";
    try { display = ustr(obj.a47); } catch (e) { return out; }
    if (!display || display.indexOf("JWP v1") !== 0) return out;
    var scores;
    try { scores = EEW(sb, obj); } catch (e) { return out; }
    var list = (scores && scores.qN) ? Array.prototype.slice.call(scores.qN.data, 0, scores.g) : [];
    for (var i = 0; i < list.length; i++) {
      var holder;
      try { holder = ustr(list[i].X5); } catch (e) { continue; }
      out.push({ holder: holder, score: list[i].jk | 0 });
    }
    return out;
  }

  function finite3(v) {
    return v && isFinite(v[0]) && isFinite(v[1]) && isFinite(v[2]);
  }

  return {
    draw: function (renderGlobal) {
      var rt = ensure();
      if (rt.isDisabled()) return;
      try {
        var markers = rt.sync(readEntries(renderGlobal));
        var manager = renderGlobal && renderGlobal.Pp;
        if (!manager) return;
        var camera = [manager.bP0, manager.bP1, manager.bPZ];
        if (!finite3(camera)) return;
        var beams = rt.draw(markers, camera);
        for (var i = 0; i < beams.length; i++) {
          var mesh = [], quads = beams[i].quads;
          for (var q = 0; q < quads.length; q++) {
            for (var v = 0; v < 4; v++) {
              mesh.push(quads[q].points[v][0], quads[q].points[v][1], quads[q].points[v][2],
                0, 0, quads[q].normal[0], quads[q].normal[1], quads[q].normal[2]);
            }
          }
          wpDrawLocal(mesh, beams[i].color);
        }
      } catch (e) { rt.noteError(); }
    },
    reset: function () { ensure().reset(); },
    status: function () {
      var rt = ensure(), s = rt.status();
      s.engine = "renderGlobal.Pp/a.fd.X.k3";
      return s;
    }
  };
}());

if (typeof window !== "undefined" && window) {
  try { window.JasprWaypointDiagnostics = Object.freeze({ status: function () { return JasprWaypointMarkers.status(); } }); }
  catch (e) { /* diagnostics stay local-only */ }
}
