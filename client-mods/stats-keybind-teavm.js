/* Audited against cff2ab19...: native KeyBinding + native input fiber, no DOM key listener.
 * GameSettings.a$W is the Controls/save/load array; BPd registers in HFa/LqU/LqV.
 * CFB reads native keyboard events (HFV.cX2: repeat=2), Dka supports mouse rebinding;
 * DRw is the input tick. GGw/Emm/Gsc invalidate menu/focus/world transitions.
 * ENU, FME, BPd and Cn9 may suspend: every call below has a distinct saved state.
 * Cn9 is EntityPlayerSP.sendChatMessage, NOT nearby Eha (swingArm).
 */
var JasprStatsKeyDescription = null, JasprStatsKeyLabel = null;
var JasprStatsBridge = createJasprStatsKeybind({
  binding:function (client) { return client && client.G && client.G.$jasprStatsKey; },
  now:function () {
    return $rt_globals.performance && typeof $rt_globals.performance.now === "function" ?
      $rt_globals.performance.now() : Date.now();
  },
  playing:function (client) {
    if (!client || !client.X || !client.v || client.cj !== null || !client.uE || client.cp) return false;
    var player = client.v, handler = player.d_, doc = $rt_globals.document;
    if (player.a !== client.X || player.uS > 0 || !handler || handler.bk !== client.X ||
        !handler.qf || handler.qf.bkf) return false;
    if (doc) {
      if (doc.hidden || (typeof doc.hasFocus === "function" && !doc.hasFocus())) return false;
      var active = doc.activeElement;
      if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return false;
    }
    return true;
  }
});

// Called once from B$i state 40 BEFORE DBw loads the account-restored options blob.
function JasprStatsInstall(a) {
  var b,c,d,$p=0;
  if (FX()) { var $T=Ds(); $p=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (a.$jasprStatsKey) return;
      if (JasprStatsKeyDescription === null) {
        JasprStatsKeyDescription = $rt_str("key.jaspr.stats");
        JasprStatsKeyLabel = $rt_str("Upgrade Stats");
      }
      b=new GO; c=JasprStatsKeyDescription; d=C(6273); $p=1;
    case 1:
      BPd(b,c,37,d); if (B()) break _;
      a.$jasprStatsKey=b;
      a.a$W=G6V(a.a$W,T(GO,[b]));
      return;
    default: FT();
  } }
  Ds().s(a,b,c,d,$p);
}

// Only called by DRw state 49, on the existing game fiber. No extra fibers or timers.
function JasprStatsTick(a) {
  var b,c,d,e,$p=0,$z;
  if (FX()) { var $T=Ds(); $p=$T.l(); e=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b=JasprStatsBridge.take(a); if (b === null) return;
      c=b.player; d=b.connection; $p=1;
    case 1:
      $z=ENU(c); if (B()) break _;
      if (!($z > 0) || !JasprStatsBridge.ready(b)) return;
      $p=2;
    case 2:
      $z=FME(d); if (B()) break _;
      if (!$z || !JasprStatsBridge.ready(b)) return;
      e=$rt_str("/stats"); $p=3;
    case 3:
      Cn9(c,e); if (B()) break _;
      JasprStatsBridge.sent();
      return;
    default: FT();
  } }
  Ds().s(a,b,c,d,e,$p);
}
