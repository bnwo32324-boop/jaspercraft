/* Tab-held waypoint readout, TeaVM side. No new binding is registered: this
 * reads the existing player-list binding (description key.playerlist, LWJGL
 * default 15). GO.mz is that binding's live pressed flag -- the same field the
 * game reads to decide whether to show the player list, set by setKeyBindState
 * on every key event and cleared by unPressAllKeys -- so a hold lasts exactly
 * as long as the key is down. The native poll still feeds edges, but only as a
 * fallback for the case where the binding cannot be resolved.
 * ENU, FME and Cn9 may suspend: the tick below keeps one saved state per call,
 * mirroring JasprWaypointTick. JasprWaypointTabHeld gates the DbP beam draw.
 */
var JasprWaypointTabBindCache = null, JasprWaypointTabBindOwner = null;

function JasprWaypointTabUstr(value) {
  if (typeof value === "string") return value;
  return $rt_ustr(value);
}

// The player-list binding, found by description so rebinds keep working.
// Cached per options object; the scan is far too costly to repeat every frame.
function JasprWaypointTabBinding(client) {
  try {
    var settings = client && client.G;
    if (!settings) return null;
    if (JasprWaypointTabBindOwner === settings && JasprWaypointTabBindCache !== null)
      return JasprWaypointTabBindCache;
    var list = settings.a$W, data = list && list.data;
    if (!data) return null;
    var found = null;
    for (var i = 0; i < data.length; i++) {
      var key = data[i];
      if (!key) continue;
      var description = null;
      try { description = JasprWaypointTabUstr(key.a98); } catch (e) { continue; }
      if (description === "key.playerlist") { found = key; break; }
    }
    if (found === null) return null;
    JasprWaypointTabBindOwner = settings;
    JasprWaypointTabBindCache = found;
    return found;
  } catch (e) { return null; }
}

function JasprWaypointTabNow() {
  try {
    return $rt_globals.performance.now();
  } catch (e) { return Date.now(); }
}

var JasprWaypointTab = createJasprWaypointTab({
  now: JasprWaypointTabNow,
  // null means "cannot tell", which sends the runtime to its edge fallback.
  pressed: function (client) {
    var binding = JasprWaypointTabBinding(client);
    if (binding === null) return null;
    try { return binding.mz ? true : false; } catch (e) { return null; }
  },
  // A live connection to this world. Enough to send a release while a screen is up.
  connected: function (client) {
    if (!client || !client.X || !client.v) return false;
    var player = client.v, handler = player.d_;
    if (player.a !== client.X) return false;
    if (!handler || handler.bk !== client.X || !handler.qf || handler.qf.bkf) return false;
    return true;
  },
  // In control of the player, with no screen in the way and the page in front.
  playing: function (client) {
    if (!client || !client.X || !client.v || client.cj !== null || !client.uE || client.cp) return false;
    var player = client.v, handler = player.d_, doc = $rt_globals.document;
    if (player.a !== client.X || player.uS > 0 || !handler || handler.bk !== client.X ||
        !handler.qf || handler.qf.bkf) return false;
    if (doc) {
      if (doc.hidden) return false;
      var active = doc.activeElement;
      if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return false;
    }
    return true;
  }
});

// Called from the CFB/Dka native poll with (client, code, down, repeatFlag).
function JasprWaypointTabPoll(client, code, down, repeat) {
  try {
    if (!code || code !== JasprWaypointTabCode(client)) return false;
    var now = JasprWaypointTabNow();
    if (repeat) return JasprWaypointTab.touch(client, now);
    return JasprWaypointTab.edge(client, !!down, now);
  } catch (e) { return false; }
}

function JasprWaypointTabCode(client) {
  var binding = JasprWaypointTabBinding(client);
  if (binding && binding.gO) return binding.gO;
  return 15;
}

// Render-thread gate for the DbP beam draw. Never suspends.
function JasprWaypointTabHeld(renderGlobal) {
  try {
    var client = renderGlobal && renderGlobal.fd;
    if (!client) return false;
    return JasprWaypointTab.held(client);
  } catch (e) { return false; }
}

// Only called by DRw state 49, on the existing game fiber. No extra fibers or timers.
function JasprWaypointTabTick(a) {
  var b, c, d, e, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprWaypointTab.take(a); if (b === null) return;
      c = b.player; d = b.connection; $p = 1;
    case 1:
      $z = ENU(c); if (B()) break _;
      if (!($z > 0) || !JasprWaypointTab.ready(b)) return;
      $p = 2;
    case 2:
      $z = FME(d); if (B()) break _;
      if (!$z || !JasprWaypointTab.ready(b)) return;
      e = b.kind === 'release' ? $rt_str("/wp compassoff") : $rt_str("/wp compass"); $p = 3;
    case 3:
      Cn9(c, e); if (B()) break _;
      JasprWaypointTab.sent();
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, $p);
}
