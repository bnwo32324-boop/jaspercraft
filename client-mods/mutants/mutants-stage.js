/* Hook entry points of the JASPR_MUTANTS stage (installed by scripts/build-mutants-client.cjs). Synchronous hooks go
 * through JasprMutantsBridge and never suspend; the ones that may reach an @Async engine path (first texture bind,
 * network send, opening a screen) are the resumable function below, written in TeaVM's state-machine form. Any
 * exception switches off only the failing part (JasprMutants.fail) and the game continues with vanilla behaviour.
 *
 * Install order: Bootstrap.register (registry hook) adds sounds, particle types, entities and items, before the model
 * bakery reads the item variants. The first Minecraft.runTick (main menu) then defines the renderers, particles and the
 * tracker screen, binds every texture they draw once (resumable, so the image decode may suspend there and nowhere
 * else) and only then registers renderers, shoulder layers, item models and particle factories (ClientProxy.preInit /
 * init / onModelRegistry). Until then a mutant has no renderer and is simply not drawn. */
var JasprMutantsBridge = (function (M) {
  "use strict";
  var B = {};
  B.registry = function () {                                 // end of Bootstrap.register
    try { M.install(); M.state("installed", { entities: M.ENTITY_TYPES.length, sounds: M.SOUND_NAMES.length, items: M.ITEM_NAMES.length }); }
    catch (e) { M.fail("all", e); }
  };
  B.payload = function (packet) {                            // NetHandlerPlayClient.handleCustomPayload
    try { return M.payload(packet); } catch (e) { M.fail("payload", e); return false; }
  };
  // ---- late install (first runTick)
  var defined = false;
  B.preloadList = function () {
    if (!M.installed || M.lateDone || !M.enabled("render")) return null;
    try {
      if (!defined) { defined = true; M.defineRenderers(); M.defineParticles(); M.defineGui(); }
      var all = M.preloadTextures().concat(M.particleTextures(), M.guiTextures()), out = [];
      for (var i = 0; i < all.length; i++) if (all[i].a3s !== 1) out.push(all[i]);
      return out;
    } catch (e) { M.fail("render", e); return null; }
  };
  B.lateInstall = function (mc) {
    if (M.lateDone) return;
    M.lateDone = true;
    var done = {};
    try { done.renderers = M.registerRenderers(mc.AM); done.shoulders = M.installShoulderLayers(mc.AM); } catch (e) { M.fail("render", e); }
    try { done.itemModels = M.registerItemModels(mc.u4); } catch (e) { M.fail("items", e); }
    try { done.particles = M.registerParticles(mc.it); } catch (e) { M.fail("particles", e); }
    done.textures = M.texturesReady();
    M.state("ready", done);
  };
  // ---- synchronous render hooks
  B.transform = function (type) { M.noteTransform(type); }   // ItemCameraTransforms.getTransform(type)
  B.teisr = function (stack) {                               // TileEntityItemStackRenderer.renderByItem
    if (!M.lateDone || !M.enabled("items")) return false;
    try { return M.teisr(stack); } catch (e) { M.fail("items", e); return false; }
  };
  B.armorModel = function (entity, stack, slot, model) {    // ForgeHooksClient.getArmorModel in LayerArmorBase.renderArmorLayer
    if (!M.installed || !M.enabled("items")) return model;
    try { return M.armorModel(entity, stack, slot, model); } catch (e) { M.fail("items", e); return model; }
  };
  // ---- per tick (start of Minecraft.runTick): HELLO once per connection, then the queued messages
  var lastConnection = null;
  B.nextSend = function (mc) {
    var player = mc.v;
    if (player === null || player.d_ === null || player.d_ === undefined || !M.installed) return null;
    var conn = player.d_, net = conn.qf;
    if (conn !== lastConnection) { lastConnection = conn; M.stats.hello++; return { net: net, channel: "jaspr:mutants", bytes: M.helloBytes() }; }
    var o = M.takeOutgoing();
    return o === null ? null : { net: net, channel: o.channel, bytes: o.bytes };
  };
  B.takeScreen = function (mc) {
    var s = M.pendingScreen || null; M.pendingScreen = null;
    if (s === null && !M.enabled("gui") && M.CreeperMinionTrackerScreen && mc.cj instanceof M.CreeperMinionTrackerScreen) return { close: true };
    return s;
  };
  B.packet = function (msg) {                                // CPacketCustomPayload(channel, PacketBuffer(bytes))
    var buf = new Iu(); Lg(buf, Fru());
    for (var i = 0; i < msg.bytes.length; i++) F4D(buf, msg.bytes[i]);
    var pkt = new AKy(); BgN(pkt, M.JS(msg.channel), buf);
    return pkt;
  };
  return B;
})(JasprMutants);

/* Minecraft.runTick, state 3105 (resumable): the late install's texture binds (once), a pending tracker screen, then at
 * most four queued messages per tick, like JasprGearTick. */
function JasprMutantsTick(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
    case 0:
      b = JasprMutantsBridge.preloadList();
      d = 0;
      if (b === null) { $p = 4; continue _; }
      $p = 10;
    case 10:
      if (d >= b.length) { JasprMutantsBridge.lateInstall(a); $p = 4; continue _; }
      c = b[d];
      $p = 11;
    case 11:
      D17(a.bE, c); if (B()) break _;                         // TextureManager.bindTexture: loads the image once
      d = d + 1 | 0;
      $p = 10;
      continue _;
    case 4:
      try { c = JasprMutantsBridge.takeScreen(a); } catch (e) { JasprMutants.fail("gui", e); c = null; }
      if (c === null) { d = 0; $p = 1; continue _; }
      if (c.close === true) c = null;                          // a failed tracker screen: back to the game
      else if (!JasprMutants.enabled("gui")) { d = 0; $p = 1; continue _; }
      $p = 5;
    case 5:
      GGw(a, c); if (B()) break _;                            // Minecraft.displayGuiScreen
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
