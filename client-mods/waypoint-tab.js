/* Tab-held waypoint gate.
 *
 * The state that matters is the player-list binding's own pressed flag, which
 * the game already keeps live (it is what makes the vanilla player list stay up
 * for exactly as long as Tab is down). This reads that flag every frame and
 * every tick, so a hold lasts as long as the key does. Key edges from the
 * native poll are only a fallback for the case where the binding cannot be
 * found; nothing here depends on auto-repeat events or on a timeout.
 *
 * On the game fiber: /wp compass when the hold opens and as a heartbeat while
 * it lasts, /wp compassoff the moment it ends. The server redraws the readout
 * continuously between those two, so it never reaches its fade-out.
 * DbP gates beam rendering on held() from the same flag, so the beams and the
 * readout appear and vanish together.
 */
function createJasprWaypointTab(api) {
  "use strict";
  var epoch = 0, shown = false, inFlight = null, lastSent = -Infinity;
  var opens = 0, sends = 0;
  var resendMs = 2000;
  // Fallback only: used when the binding is unreadable, where edges are all we have.
  var edgeHeld = false, edgeSeen = -Infinity, edgeTimeoutMs = 1500;

  function invalidate(client) {
    epoch++;
    inFlight = null;
    edgeHeld = false;
    // shown is deliberately left alone: the next tick sends the release.
  }
  // True while the key is physically down, by the game's own flag where possible.
  function down(client) {
    var live = api.pressed(client);
    if (live !== null) return live;
    return edgeHeld && api.now() - edgeSeen <= edgeTimeoutMs;
  }
  function request(client, kind, now) {
    return {client:client, kind:kind, at:now, epoch:epoch,
      player:client.v, world:client.X, handler:client.v.d_, connection:client.v.d_.qf};
  }
  // Identity only. A release has to survive a screen opening, so this asks for a
  // live connection rather than for the player being in control.
  function ready(request) {
    if (!request || request.epoch !== epoch) return false;
    var client = request.client;
    if (!api.connected(client)) return false;
    return client.v === request.player && client.X === request.world &&
      client.v.d_ === request.handler && request.handler.qf === request.connection;
  }
  function take(client) {
    if (!client || !api.connected(client)) { shown = false; inFlight = null; return null; }
    var now = api.now(), want = api.playing(client) && down(client);
    if (want) {
      if (shown && now - lastSent < resendMs) return null;
      var open = request(client, shown ? 'resend' : 'press', now);
      if (!ready(open)) return null;
      if (!shown) opens++;
      lastSent = now;
      inFlight = open;
      return open;
    }
    if (!shown) return null;
    var close = request(client, 'release', now);
    if (!ready(close)) { shown = false; inFlight = null; return null; }
    lastSent = now;
    inFlight = close;
    return close;
  }
  // The fiber calls this once the chat packet is actually away.
  function sent() {
    sends++;
    if (!inFlight) return;
    shown = inFlight.kind !== 'release';
    inFlight = null;
  }
  function held(client) {
    if (!client) return false;
    return api.playing(client) && down(client);
  }
  // Native poll edges. Only the fallback path reads these.
  function edge(client, isDown, now) {
    edgeSeen = now;
    edgeHeld = !!isDown;
    return false;
  }
  function touch(client, now) {
    if (edgeHeld) edgeSeen = now;
    return false;
  }
  return {
    edge:edge, touch:touch, take:take, ready:ready, invalidate:invalidate, held:held, sent:sent,
    status:function () { return {resendMs:resendMs, shown:shown, opens:opens, sends:sends}; }
  };
}
if (typeof module !== "undefined") module.exports = {createJasprWaypointTab:createJasprWaypointTab};
