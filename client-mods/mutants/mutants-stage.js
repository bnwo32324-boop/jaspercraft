/* Hook entry points of the JASPR_MUTANTS stage (installed by scripts/build-mutants-client.cjs). Synchronous hooks go
 * through JasprMutantsBridge and never suspend; the ones that may reach an @Async engine path (first texture bind,
 * network send) are the resumable functions below, written in TeaVM's state-machine form. Any exception switches
 * off only the failing part (JasprMutants.fail) and the game continues with vanilla behaviour. */
var JasprMutantsBridge = (function (M) {
  "use strict";
  var B = {};
  B.registry = function () {                                 // end of Bootstrap.register
    try { M.install(); M.state("installed", { entities: M.ENTITY_TYPES.length, sounds: M.SOUND_NAMES.length }); }
    catch (e) { M.fail("all", e); }
  };
  B.payload = function (packet) {                            // NetHandlerPlayClient.handleCustomPayload
    try { return M.payload(packet); } catch (e) { M.fail("payload", e); return false; }
  };
  B.scaleRender = function (entity) {                        // RenderLivingBase.doRender, after renderLivingAt
    if (M.scaleTable.size === 0) return;
    try { var s = M.renderScaleOf(entity); if (s !== 1.0) FWM(s, s, s); } catch (e) { M.fail("scale", e); }
  };
  // per tick (start of Minecraft.runTick): HELLO once per connection, then the queued messages
  var lastConnection = null;
  B.nextSend = function (mc) {
    var player = mc.v;
    if (player === null || player.d_ === null || player.d_ === undefined || !M.installed) return null;
    var conn = player.d_, net = conn.qf;
    if (conn !== lastConnection) { lastConnection = conn; M.stats.hello++; return { net: net, channel: "jaspr:mutants", bytes: M.helloBytes() }; }
    var o = M.takeOutgoing();
    return o === null ? null : { net: net, channel: o.channel, bytes: o.bytes };
  };
  B.tick = function (mc) {
    try { if (M.installed && M.scaleTable.size > 0) M.updateScaledEntities(mc.X); } catch (e) { M.fail("scale", e); }
  };
  B.packet = function (msg) {                                // CPacketCustomPayload(channel, PacketBuffer(bytes))
    var buf = new Iu(); Lg(buf, Fru());
    for (var i = 0; i < msg.bytes.length; i++) F4D(buf, msg.bytes[i]);
    var pkt = new AKy(); BgN(pkt, M.JS(msg.channel), buf);
    return pkt;
  };
  return B;
})(JasprMutants);

/* Minecraft.runTick, state 3105 (resumable): sends at most four queued messages per tick, like JasprGearTick. */
function JasprMutantsTick(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
    case 0:
      JasprMutantsBridge.tick(a);
      d = 0;
      $p = 1;
    case 1:
      if (d >= 4) return;
      try { b = JasprMutants.enabled("send") ? JasprMutantsBridge.nextSend(a) : null; c = b === null ? null : JasprMutantsBridge.packet(b); }
      catch (e) { JasprMutants.fail("send", e); return; }
      if (c === null) return;
      b = b.net;
      $p = 2;
    case 2:
      b.wd(c); if (B()) break _;                              // NetworkManager.sendPacket
      d = d + 1 | 0;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}
