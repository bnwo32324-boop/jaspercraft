/* Survivor Gear inventory panel for the deployed TeaVM client (JasprGear server plugin).
 *
 * Seven Baubles-style trinket slots (neck, ring, ring, belt, head, body, charm) drawn beside the
 * survival inventory. Nothing here is authoritative: a click on a slot sends "click <slot>
 * <button> <shift>" over the jaspr:gear plugin channel, Paper applies the move against its own
 * cursor and slots, then answers with the slot contents (SNBT) plus the vanilla cursor packet.
 * The panel appears only after the server answered this connection's hello, so servers or
 * clients without the feature simply never see it.
 *
 * Audited TeaVM names: APz GuiInventory; Gmh GuiContainer.mouseClicked; EGS mouseReleased;
 * E3x GuiInventory foreground layer (matrix at guiLeft/guiTop); EXw GuiInventory.drawScreen;
 * a.is guiLeft, a.l7 guiTop, a.q width, a.clo = !recipeBookVisible, a.hu RenderItem, a.j mc;
 * mc.v player, player.bx.fH cursor stack, player.d_.qf NetworkManager (wd = sendPacket);
 * D49 drawRect, FkM renderItemAndEffectIntoGUI, a.dIt renderToolTip, E0F JsonToNBT, BH8 ItemStack(NBT),
 * AKy/BgN CPacketCustomPayload, Iu/Lg PacketBuffer, Fru Unpooled.buffer, FuF writeString,
 * Cyr handleCustomPayload (CRh readString), DRw runTick, B$i GameSettings.<init>, BPd KeyBinding.
 * Every suspending call below has its own saved state; plain-JS entry points never suspend.
 */
var JasprGear = (function () {
  "use strict";
  var PROTOCOL = 1, COUNT = 7, PANEL_W = 26, PANEL_H = 134;
  var ICONS = __JASPR_GEAR_ICONS__;
  var slots = blank(), builtFrom = blank(), stacks = nulls(), icons = nulls(), iconTried = falses();
  var seen = false, connection = null, helloAt = 0, helloTries = 0, queue = [], press = null;
  var disabled = false, failure = null, channel = null;
  var stats = {received: 0, rejected: 0, sent: 0, clicks: 0, keys: 0, builds: 0, buildErrors: 0, hellos: 0};

  function blank() { return ["", "", "", "", "", "", ""]; }
  function nulls() { return [null, null, null, null, null, null, null]; }
  function falses() { return [false, false, false, false, false, false, false]; }

  function die(where, error) {
    if (disabled) return;
    disabled = true;
    failure = where + ": " + (error && error.message ? error.message : String(error));
    try { if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft gear] panel disabled -- " + failure); } catch (ignored) { }
  }
  function empty(stack) {
    return !stack || stack === Ktg || stack.rA === null || stack.rA === undefined || !!stack.bg_;
  }
  function inventory(gui) { return !!gui && gui instanceof APz; }

  // Right of the 176px window; left of it when the screen is too narrow.
  function layout(gui) {
    var left = gui.is | 0, width = gui.q | 0, x0 = 179;
    if (left + x0 + PANEL_W > width) {
      if (gui.clo === 0) return null;
      x0 = -PANEL_W - 3;
      if (left + x0 < 0) return null;
    }
    return {x0: x0, y0: 4};
  }
  // Window-relative point: slot index, -2 panel chrome, -1 not ours.
  function slotAt(L, x, y) {
    if (x < L.x0 || y < L.y0 || x >= L.x0 + PANEL_W || y >= L.y0 + PANEL_H) return -1;
    var sx = L.x0 + 5;
    if (x >= sx - 1 && x < sx + 17) {
      var i = Math.floor((y - (L.y0 + 4)) / 18);
      if (i >= 0 && i < COUNT) {
        var sy = L.y0 + 5 + i * 18;
        if (y >= sy - 1 && y < sy + 17) return i;
      }
    }
    return -2;
  }

  function plan(gui, x, y) {
    try {
      if (disabled || !seen || !inventory(gui)) return null;
      var L = layout(gui);
      if (!L) return null;
      var rects = [], items = [], x0 = L.x0, y0 = L.y0, w = PANEL_W, h = PANEL_H;
      var r = function (rx, ry, rw, rh, c) { rects.push({x: rx, y: ry, w: rw, h: rh, color: c | 0}); };
      r(x0 + 1, y0, w - 2, h, 0xFF000000); r(x0, y0 + 1, w, h - 2, 0xFF000000);
      r(x0 + 1, y0 + 1, w - 2, h - 2, 0xFFC6C6C6);
      r(x0 + 1, y0 + 1, w - 3, 2, 0xFFFFFFFF); r(x0 + 1, y0 + 1, 2, h - 3, 0xFFFFFFFF);
      r(x0 + 3, y0 + h - 3, w - 4, 2, 0xFF555555); r(x0 + w - 3, y0 + 3, 2, h - 4, 0xFF555555);
      var hover = slotAt(L, x, y);
      for (var i = 0; i < COUNT; i++) {
        var sx = x0 + 5, sy = y0 + 5 + i * 18;
        r(sx - 1, sy - 1, 17, 1, 0xFF373737); r(sx - 1, sy - 1, 1, 17, 0xFF373737);
        r(sx, sy + 16, 17, 1, 0xFFFFFFFF); r(sx + 16, sy, 1, 17, 0xFFFFFFFF);
        r(sx, sy, 16, 16, 0xFF8B8B8B);
        if (hover === i) r(sx, sy, 16, 16, 0x80FFFFFF);
        var stack = stacks[i] || icons[i];
        if (stack) items.push({stack: stack, x: sx, y: sy});
      }
      return {rects: rects, items: items};
    } catch (error) { die("plan", error); return null; }
  }

  function tooltip(gui, x, y) {
    try {
      if (disabled || !seen || !inventory(gui)) return null;
      var player = gui.j ? gui.j.v : null;
      if (!player || !player.bx || !empty(player.bx.fH)) return null;
      var L = layout(gui);
      if (!L) return null;
      var i = slotAt(L, x, y);
      return i < 0 ? null : (stacks[i] || icons[i] || null);
    } catch (error) { die("tooltip", error); return null; }
  }

  function mouseDown(gui, mx, my, button) {
    try {
      if (disabled || !seen || !inventory(gui)) return false;
      var L = layout(gui);
      if (!L) return false;
      var i = slotAt(L, (mx | 0) - (gui.is | 0), (my | 0) - (gui.l7 | 0));
      if (i === -1) return false;
      press = gui;
      if (i >= 0 && (button === 0 || button === 1) && queue.length < 8) {
        var shift = false;
        try { shift = !!(Jz(42) || Jz(54)); } catch (ignored) { }
        queue.push("click " + i + " " + button + " " + (shift ? 1 : 0));
        stats.clicks++;
      }
      return true;
    } catch (error) { die("mouseDown", error); return false; }
  }
  // A press we swallowed must not reach vanilla on release: outside the window that would throw
  // the cursor stack on the ground.
  function mouseUp(gui) {
    var ours = press !== null && press === gui;
    press = null;
    return ours;
  }

  function receive(text) {
    try {
      if (disabled || typeof text !== "string" || text.length > 40000) { stats.rejected++; return; }
      var packet = JSON.parse(text);
      if (!packet || packet.v !== PROTOCOL || !Array.isArray(packet.slots) || packet.slots.length !== COUNT) { stats.rejected++; return; }
      for (var i = 0; i < COUNT; i++) {
        var s = packet.slots[i];
        if (typeof s !== "string" || s.length > 4096 || (s !== "" && (s.charAt(0) !== "{" || s.charAt(s.length - 1) !== "}"))) { stats.rejected++; return; }
      }
      for (var j = 0; j < COUNT; j++) slots[j] = packet.slots[j];
      seen = true;
      stats.received++;
    } catch (error) { stats.rejected++; }
  }

  function nextBuild() {
    if (disabled) return null;
    for (var i = 0; i < COUNT; i++) {
      if (slots[i] === builtFrom[i]) continue;
      if (slots[i] === "") { builtFrom[i] = ""; stacks[i] = null; continue; }
      return {icon: false, index: i, snbt: slots[i]};
    }
    if (!seen) return null;
    for (var k = 0; k < COUNT; k++) if (!iconTried[k]) return {icon: true, index: k, snbt: ICONS[k]};
    return null;
  }
  function built(request, stack) {
    if (request.icon) { iconTried[request.index] = true; icons[request.index] = stack || null; }
    else if (slots[request.index] === request.snbt) { builtFrom[request.index] = request.snbt; stacks[request.index] = stack || null; }
    if (stack) stats.builds++; else stats.buildErrors++;
    if (stats.buildErrors > 32) die("build", "too many item build failures");
  }

  function outgoing(client) {
    try {
      if (disabled || !client) return null;
      var player = client.v, handler = player ? player.d_ : null, net = handler ? handler.qf : null;
      if (!net || !client.X || net.bkf) return null;
      var t = Date.now();
      if (net !== connection) {
        connection = net; seen = false; queue = []; press = null; helloTries = 0; helloAt = t + 1000;
        slots = blank(); builtFrom = blank(); stacks = nulls();
      }
      if (!seen && helloTries < 3 && t >= helloAt) {
        helloTries++; helloAt = t + 5000; stats.hellos++;
        return {net: net, text: "hello " + PROTOCOL};
      }
      if (queue.length) return {net: net, text: queue.shift()};
      return null;
    } catch (error) { die("outgoing", error); return null; }
  }
  function queueKey(action) {
    if (disabled || !seen || queue.length >= 8) return;
    queue.push("key " + action);
    stats.keys++;
  }

  return {
    plan: plan, tooltip: tooltip, mouseDown: mouseDown, mouseUp: mouseUp, receive: receive,
    nextBuild: nextBuild, built: built, outgoing: outgoing, queueKey: queueKey, die: die,
    enabled: function () { return !disabled && seen; },
    channel: function () { if (channel === null) channel = $rt_str("jaspr:gear"); return channel; },
    sent: function () { stats.sent++; },
    status: function () {
      var worn = 0;
      for (var i = 0; i < COUNT; i++) if (slots[i] !== "") worn++;
      return {protocol: PROTOCOL, serverSeen: seen, disabled: disabled, failure: failure, worn: worn, queued: queue.length,
        received: stats.received, rejected: stats.rejected, sent: stats.sent, clicks: stats.clicks, keys: stats.keys,
        hellos: stats.hellos, builds: stats.builds, buildErrors: stats.buildErrors};
    }
  };
}());

/* Native KeyBindings (Controls-remappable, saved with the account options): G arc shot,
 * H dodge / blink (sneak: remote ender chest), J magnet (sneak: repel). Only while playing. */
var JasprGearKeys = (function () {
  "use strict";
  var defs = [
    {field: "$jasprGearArc", key: "key.jaspr.gear.arc", label: "Gear: Arc Shot", code: 34, action: "arc"},
    {field: "$jasprGearDodge", key: "key.jaspr.gear.dodge", label: "Gear: Dodge / Blink", code: 35, action: "dodge"},
    {field: "$jasprGearMagnet", key: "key.jaspr.gear.magnet", label: "Gear: Magnet", code: 36, action: "magnet"}];
  var names = [], held = [false, false, false], last = [0, 0, 0], pressed = 0;
  function description(i) {
    if (!names[i]) { var s = $rt_str(defs[i].key); s.$jasprGearLabel = $rt_str(defs[i].label); names[i] = s; }
    return names[i];
  }
  function playing(client) {
    if (!client || !client.X || !client.v || client.cj !== null || !client.uE || client.cp) return false;
    var player = client.v, handler = player.d_, doc = $rt_globals.document;
    if (player.a !== client.X || player.uS > 0 || !handler || handler.bk !== client.X || !handler.qf || handler.qf.bkf) return false;
    if (doc) {
      if (doc.hidden || (typeof doc.hasFocus === "function" && !doc.hasFocus())) return false;
      var active = doc.activeElement;
      if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return false;
    }
    return true;
  }
  function key(client, code, down, repeat) {
    try {
      if (!client || !client.G || !code) return;
      for (var i = 0; i < defs.length; i++) {
        var binding = client.G[defs[i].field];
        if (!binding || binding.gO !== code) continue;
        if (!down) { held[i] = false; continue; }
        if (repeat || held[i]) continue;
        held[i] = true;
        if (!playing(client)) continue;
        var t = Date.now();
        if (t - last[i] < 250) continue;
        last[i] = t;
        pressed++;
        JasprGear.queueKey(defs[i].action);
      }
    } catch (ignored) { }
  }
  // Native onTick also increments pressTime on our bindings; never let it accumulate.
  function poll(client) {
    try {
      if (!client || !client.G) return;
      for (var i = 0; i < defs.length; i++) { var b = client.G[defs[i].field]; if (b) b.bSp = 0; }
    } catch (ignored) { }
  }
  return {defs: defs, description: description, key: key, poll: poll,
    status: function () { return {keys: "G/H/J", pressed: pressed}; }};
}());

function JasprGearMouseDown(a, b, c, d) { return JasprGear.mouseDown(a, b, c, d) ? 1 : 0; }
function JasprGearMouseUp(a, b, c, d) { return JasprGear.mouseUp(a) ? 1 : 0; }

// Called once from B$i state 96, BEFORE DBw loads the account-restored options blob.
function JasprGearInstall(a) {
  var b, c, d, e, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      e = 0;
      $p = 1;
    case 1:
      if (e >= JasprGearKeys.defs.length) return;
      if (a[JasprGearKeys.defs[e].field]) { e = e + 1 | 0; continue _; }
      b = new GO; c = JasprGearKeys.description(e); d = C(6273);
      $p = 2;
    case 2:
      BPd(b, c, JasprGearKeys.defs[e].code, d); if (B()) break _;
      a[JasprGearKeys.defs[e].field] = b;
      a.a$W = G6V(a.a$W, T(GO, [b]));
      e = e + 1 | 0;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, $p);
}

// Builds and sends one CPacketCustomPayload("jaspr:gear", writeString(text)) on netManager a.
function JasprGearSend(a, b) {
  var c, d, e, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      c = new AKy; d = new Iu;
      $p = 1;
    case 1:
      $z = Fru(); if (B()) break _;
      Lg(d, $z); e = $rt_str(b);
      $p = 2;
    case 2:
      $z = FuF(d, e); if (B()) break _;
      BgN(c, JasprGear.channel(), $z);
      $p = 3;
    case 3:
      a.wd(c); if (B()) break _;
      JasprGear.sent();
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, $p);
}

// DRw state 97 (key-bind tick, no screen open) and the inventory draw: at most two messages per call.
function JasprGearTick(a) {
  var b, c, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      JasprGearKeys.poll(a);
      b = JasprGear.outgoing(a);
      if (b === null) return;
      c = 0;
      $p = 1;
    case 1:
      JasprGearSend(b.net, b.text); if (B()) break _;
      c = c + 1 | 0;
      if (c >= 2) return;
      b = JasprGear.outgoing(a);
      if (b === null) return;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, $p);
}

// Turns server SNBT (slots) and the fixed icon SNBT into native ItemStacks, only when changed.
function JasprGearPrepare(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprGear.nextBuild();
      if (b === null) return;
      c = null;
      $p = 1;
    case 1:
      try { $z = E0F($rt_str(b.snbt)); if (B()) break _; c = $z; } catch ($e) { c = null; }
      if (c === null) { JasprGear.built(b, null); $p = 0; continue _; }
      d = new Bk;
      $p = 2;
    case 2:
      try { BH8(d, c); if (B()) break _; } catch ($e) { d = null; }
      JasprGear.built(b, d);
      $p = 0;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

// GuiInventory foreground (E3x state 93): matrix already at guiLeft/guiTop. DRw (the key-bind
// tick) does not run while a screen is open, so queued clicks are flushed from here first.
function JasprGearDraw(a, b, c) {
  var d, e, f, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      $p = 6;
    case 6:
      JasprGearTick(a.j); if (B()) break _;
      if (!JasprGear.enabled()) return;
      $p = 1;
    case 1:
      JasprGearPrepare(a); if (B()) break _;
      d = JasprGear.plan(a, (b | 0) - (a.is | 0), (c | 0) - (a.l7 | 0));
      if (d === null) return;
      e = 0;
      $p = 2;
    case 2:
      if (e >= d.rects.length) { e = 0; $p = 4; continue _; }
      f = d.rects[e];
      $p = 3;
    case 3:
      D49(f.x, f.y, f.x + f.w | 0, f.y + f.h | 0, f.color); if (B()) break _;
      e = e + 1 | 0;
      $p = 2;
      continue _;
    case 4:
      if (e >= d.items.length) return;
      f = d.items[e];
      $p = 5;
    case 5:
      FkM(a.hu, a.j.v, f.stack, f.x, f.y); if (B()) break _;
      e = e + 1 | 0;
      $p = 4;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, $p);
}

// GuiInventory.drawScreen (EXw state 97): native item tooltip over a gear slot.
function JasprGearTooltip(a, b, c) {
  var d, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      d = JasprGear.tooltip(a, (b | 0) - (a.is | 0), (c | 0) - (a.l7 | 0));
      if (d === null) return;
      $p = 1;
    case 1:
      a.dIt(d, b, c); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

if (typeof window !== "undefined" && window) {
  try {
    window.JasprGearDiagnostics = Object.freeze({
      status: function () { var s = JasprGear.status(); s.keys = JasprGearKeys.status(); return s; }
    });
  } catch (ignored) { }
}
