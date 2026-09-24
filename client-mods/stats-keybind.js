/* Bounded input gate only. XP, purchases, authorization and death resets belong to /stats. */
function createJasprStatsKeybind(api) {
  "use strict";
  var pending = null, held = false, keyCode = 0, lastQueued = -Infinity, epoch = 0;
  var cooldownMs = 750, expiryMs = 1000;
  var accepted = 0, sent = 0;

  function binding(client) { return api.binding(client); }
  function invalidate(client) {
    epoch++;
    pending = null;
    held = false;
    var key = binding(client);
    if (key) key.bSp = 0;
  }
  function ready(request) {
    if (!request || !api.playing(request.client)) return false;
    var client = request.client, key = binding(client), now = api.now();
    return request.epoch === epoch && !!key && key.gO === request.code && client.v === request.player &&
      client.X === request.world && client.v.d_ === request.handler &&
      request.handler.qf === request.connection && now >= request.at && now - request.at <= expiryMs;
  }
  function key(client, code, down, repeat) {
    var bound = binding(client);
    if (!bound || !api.playing(client)) { invalidate(client); return false; }
    if (keyCode !== bound.gO) { invalidate(client); keyCode = bound.gO; }
    if (!code || code !== bound.gO) return false;
    if (!down) { held = false; return false; }
    if (repeat || held) return false;
    held = true;
    var now = api.now();
    if (pending || now - lastQueued < cooldownMs) return false;
    pending = {client:client, player:client.v, world:client.X, code:code, at:now, epoch:epoch,
      handler:client.v.d_, connection:client.v.d_.qf};
    lastQueued = now;
    accepted++;
    return true;
  }
  function take(client) {
    // Native onTick also increments pressTime. Never allow our unused counter to accumulate.
    var bound = binding(client);
    if (bound) bound.bSp = 0;
    var request = pending;
    pending = null;
    if (!request || request.client !== client || !ready(request)) return null;
    return request;
  }
  return {
    key:key, take:take, ready:ready, invalidate:invalidate,
    sent:function () { sent++; },
    status:function () { return {defaultKey:"K", cooldownMs:cooldownMs, pending:!!pending, accepted:accepted, sent:sent}; }
  };
}
if (typeof module !== "undefined") module.exports = {createJasprStatsKeybind:createJasprStatsKeybind};
