/* Wide inventory for JasperCraft (owner, 2026-10-03: "The player's inventory, by default, should be 1.5x as big. This goes
 * for all players, past, present, and future. This change should affect the hotbar as well").
 *
 * Every row of the player inventory grows from 9 to 14 slots (1.5 x 9 = 13.5, rounded up): a 14-slot hotbar and three
 * 14-slot rows, 56 slots instead of 36. InventoryPlayer.mainInventory holds 56 stacks; the first 36 keep their vanilla
 * meaning. Items 36..40 are hotbar positions 10..14 and 41..55 extend the three main rows (5 each). The flat inventory
 * index puts armour at 56..59 and the off hand at 60, exactly as the patched server (scripts/java/wide-inventory) does.
 *
 * The client only uses the extra slots after the server agreed for this connection: handleJoinGame sends "wide1" on
 * plugin channel jaspr:inv and the server answers on the same channel. From then on every window that shows the
 * player's vanilla slots gets the extension slots appended (items 36..55 in index order, or only the rows the window
 * shows), the same rule the server applies, so window slot numbers agree. Without the answer (old server, the
 * server's kill switch, singleplayer) everything stays vanilla.
 *
 * The extension slots sit in a "pocket" panel beside each window, level with the rows they extend. Container screens
 * are laid out as if the screen were narrower by the pocket width (GuiScreen.setWorldAndResolution), so window and
 * pocket are centred together; the pocket also draws the dark backdrop over that strip and counts as inside the window
 * for clicks. The hotbar HUD draws 14 cells; scrolling, the touch hotbar (JasprWideBridge) and the held-slot packet use
 * positions 0..13 mapped to items 0..8 and 36..40.
 *
 * TeaVM names (audited on live classes.js 356375d5): Biv InventoryPlayer (eL main, gP currentItem), A2Z ContainerPlayer
 * (BON ctor, FH2 transferStackInSlot), H9 Container (cn slots, ArrayList .g size / .qN.data), G7 Slot (BX inventory,
 * bQx index, Lr x, Fg y, pO slotNumber), Dk new Slot, DE9 addSlotToContainer, B_G mergeItemStack, ID GuiContainer
 * (gv xSize, gx ySize, h2 container, is guiLeft, l7 guiTop, q width, L height), B0Z GuiContainer ctor, CA_ initGui,
 * C6T drawScreen, Gmh/EGS mouseClicked/mouseReleased (d1Y hasClickedOutside), C9y setWorldAndResolution,
 * ABp GuiContainerCreative (Coc setCurrentCreativeTab, Dcy handleMouseClick, YD CreativeSlot), BS0 ContainerCreative
 * (Fu1 transferStackInSlot), Ckt GuiIngame.renderHotbar, FYu drawTexturedModalRect, D49 drawRect, DNC drawGradientRect,
 * CFh GlStateManager.color, E8R handleJoinGame, Cyr handleCustomPayload, Cnj handleSetSlot, Ghi middleClickMouse,
 * Dka mouse wheel, AKy/BgN CPacketCustomPayload, Iu/Lg PacketBuffer, Fru Unpooled.buffer, FuF writeString, HEH Minecraft.
 * Every suspending call below has its own saved state; plain-JS entry points never suspend.
 */
var JasprWide = (function () {
  "use strict";
  var HELLO = "wide1", CHANNEL = "jaspr:inv";
  var POCKET = 100, GAP = 2, PAD = 4, CELL = 18, MIN_WINDOW_AREA = 200;
  var on = false, failed = false, lastError = "", channelStr = null, helloStr = null, connection = null;
  var stats = {hellos: 0, answers: 0, adopted: 0, added: 0, layouts: 0, pockets: 0, scrolls: 0, errors: 0};
  var diagnostics = {sent: 0, page: "wideinv-" + Date.now().toString(36)};

  function send(event, details) {
    // Bounded, same-origin, through the existing diagnostics route; counts and states only.
    try {
      var loc = $rt_globals.location;
      if (diagnostics.sent >= 8 || !loc || String(loc.pathname).indexOf("/jaspercraft/") !== 0 || typeof $rt_globals.fetch !== "function") return;
      diagnostics.sent++;
      $rt_globals.fetch("/api/diagnostics/events", {method: "POST", credentials: "same-origin", cache: "no-store",
        headers: {"Content-Type": "application/json", "X-Jaspergers-Client": "web-v1"},
        body: JSON.stringify({events: [{event: event, pageSessionId: diagnostics.page, at: new Date().toISOString(), details: details}]})})
        .catch(function () {});
    } catch (ignored) { }
  }
  function report(where, error) {
    stats.errors++;
    lastError = String(error && error.message || error).replace(/[\r\n]+/g, " ").slice(0, 180);
    try { if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft wide inventory] " + where + ": " + lastError); } catch (ignored) { }
    if (stats.errors <= 3) send("jaspercraft.wideinv.error", {stage: String(where).slice(0, 40), error: lastError});
  }

  // ---- hotbar positions ------------------------------------------------------------------------------------------
  function slots() { return on ? 14 : 9; }
  function index(p) { p = p | 0; return p < 9 ? p : 27 + p; }               // position 9 -> item 36
  function pos(i) { i = i | 0; return i >= 36 && i < 41 ? i - 27 : i; }
  function scroll(current, dir) {
    var n = slots(), p = pos(current);
    if (p < 0 || p >= n) p = 0;
    p = ((p - (dir | 0)) % n + n) % n;
    stats.scrolls++;
    return index(p);
  }
  /** Window-0 slot of a hotbar item: 0..8 -> 36..44, 36..40 -> 46..50. */
  function heldWindow(i) { i = i | 0; return i >= 36 && i < 41 ? 10 + i : 36 + i; }
  /** ContainerCreative slot (45 + hotbar position) -> window-0 slot. */
  function creativeWindow(n) { var k = (n | 0) - 45; return k < 9 ? 36 + k : 37 + k; }
  var WIDE_ORDER = [], VANILLA_ORDER = [];
  (function () {
    var i, r;
    for (i = 0; i < 9; i++) WIDE_ORDER.push(i);
    for (i = 36; i < 41; i++) WIDE_ORDER.push(i);
    for (r = 0; r < 3; r++) {
      for (i = 0; i < 9; i++) WIDE_ORDER.push(9 + r * 9 + i);
      for (i = 0; i < 5; i++) WIDE_ORDER.push(41 + r * 5 + i);
    }
    for (i = 0; i < 36; i++) VANILLA_ORDER.push(i);
  }());
  function order() { return on ? WIDE_ORDER : VANILLA_ORDER; }

  // Window-0 slot orders for shift-click, identical to JasprWide.java on the server.
  var IDS = {hotbar: [36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50], main: [], all: [], result: []};
  (function () {
    for (var r = 0; r < 3; r++) {
      for (var c = 0; c < 9; c++) IDS.main.push(9 + r * 9 + c);
      for (c = 0; c < 5; c++) IDS.main.push(51 + r * 5 + c);
    }
    IDS.all = IDS.main.concat(IDS.hotbar);
    IDS.result = IDS.all.slice().reverse();
  }());

  // ---- HUD hotbar -------------------------------------------------------------------------------------------------
  function span() { return on ? 282 : 182; }
  /** Left edge of the hotbar: centred, but leaving 30px for a left off-hand slot when the screen is narrow. */
  function hotbarLeft(center, width) {
    center = center | 0;
    if (!on) return center - 91 | 0;
    var left = center - 141;
    if (left < 30) left = Math.min(30, (width | 0) - 283);
    return left | 0;
  }
  function hotbarRight(width) { return hotbarLeft((width | 0) / 2 | 0, width) + span() | 0; }

  // ---- windows ----------------------------------------------------------------------------------------------------
  function list(container) { var l = container ? container.cn : null; return l && l.qN ? l : null; }
  function isInv(x) { return !!x && typeof Biv === "function" && x instanceof Biv; }
  function isCreativeSlot(s) { return typeof YD === "function" && s instanceof YD; }

  /** Rows of the player inventory this window shows, or null when it already has (or does not need) the extension. */
  function adoptPlan(container) {
    try {
      if (!on || failed) return null;
      var l = list(container);
      if (!l) return null;
      var inv = null, rows = {}, n = l.g | 0;
      for (var k = 0; k < n; k++) {
        var s = l.qN.data[k];
        if (!s || isCreativeSlot(s) || !isInv(s.BX)) continue;
        var i = s.bQx | 0;
        if (i >= 36 && i < 56) return null;
        if (i >= 0 && i < 36) { inv = s.BX; rows[i < 9 ? 3 : (i - 9) / 9 | 0] = true; }
      }
      if (!inv) return null;
      var add = [], c;
      if (rows[3]) for (c = 0; c < 5; c++) add.push(36 + c);
      for (var r = 0; r < 3; r++) if (rows[r]) for (c = 0; c < 5; c++) add.push(41 + r * 5 + c);
      return add.length ? {inv: inv, add: add} : null;
    } catch (error) { report("adopt", error); return null; }
  }
  function adopted(container, plan) {
    stats.adopted++;
    stats.added += plan.add.length;
    place(container, 176);
  }

  /** Puts the extension slots in the pocket: x from the window width, y level with the row they extend. */
  function place(container, xSize) {
    var l = list(container);
    if (!l) return false;
    var n = l.g | 0, rowY = {}, k, s, i, any = false;
    for (k = 0; k < n; k++) {
      s = l.qN.data[k];
      if (!s || s.$jw || isCreativeSlot(s) || !isInv(s.BX)) continue;
      i = s.bQx | 0;
      if (i === 0) rowY[3] = s.Fg | 0;
      else if (i === 9 || i === 18 || i === 27) rowY[(i - 9) / 9 | 0] = s.Fg | 0;
    }
    var x0 = (xSize | 0) + GAP + PAD + 1;
    for (k = 0; k < n; k++) {
      s = l.qN.data[k];
      if (!s || s.$jw !== 1) continue;
      i = s.bQx | 0;
      var r = i < 41 ? 3 : (i - 41) / 5 | 0, c = i < 41 ? i - 36 : (i - 41) % 5;
      if (rowY[r] === undefined) { s.Lr = -2000; s.Fg = -2000; continue; }
      s.Lr = x0 + c * CELL | 0;
      s.Fg = rowY[r];
      any = true;
    }
    return any;
  }
  /** GuiContainer.initGui: lay the pocket out for this screen's window width. */
  function layout(gui) {
    try {
      if (failed || !gui || !gui.h2) return;
      if (place(gui.h2, gui.gv | 0)) stats.layouts++;
    } catch (error) { report("layout", error); }
  }
  /** Creative inventory tab: the CreativeSlot for window slot h (46..65) goes in the pocket right of the 195px window. */
  function creativeSlot(slot, h) {
    try {
      h = h | 0;
      var x0 = 195 + GAP + PAD + 1;
      if (h < 51) { slot.Lr = x0 + (h - 46) * CELL | 0; slot.Fg = 112; }
      else { slot.Lr = x0 + ((h - 51) % 5) * CELL | 0; slot.Fg = 54 + ((h - 51) / 5 | 0) * CELL | 0; }
      slot.$jw = 2;
    } catch (error) { report("creative", error); }
  }

  /** Pocket bounds (window-relative) from the extension slots on screen now, or null. */
  function bounds(gui) {
    var l = list(gui ? gui.h2 : null);
    if (!l) return null;
    var n = l.g | 0, x1 = 1e9, y1 = 1e9, x2 = -1e9, y2 = -1e9, cells = [];
    for (var k = 0; k < n; k++) {
      var s = l.qN.data[k];
      if (!s || !s.$jw) continue;
      var x = s.Lr | 0, y = s.Fg | 0;
      if (x < -1000 || y < -1000) continue;
      cells.push(x, y);
      if (x < x1) x1 = x;
      if (y < y1) y1 = y;
      if (x > x2) x2 = x;
      if (y > y2) y2 = y;
    }
    if (!cells.length) return null;
    return {x: x1 - 1 - PAD, y: y1 - 1 - PAD, x2: x2 + 17 + PAD, y2: y2 + 17 + PAD, cells: cells};
  }
  function wide(gui) { try { return !failed && on && !!bounds(gui); } catch (error) { return false; } }

  /** GuiScreen.setWorldAndResolution: a widened window is centred together with its pocket. */
  function width(gui, w) {
    try {
      if (!gui || typeof ID !== "function" || !(gui instanceof ID)) return w;
      gui.$jwCut = 0;
      if (!wide(gui)) return w;
      var cut = Math.max(0, Math.min(POCKET, (w | 0) - MIN_WINDOW_AREA));
      gui.$jwCut = cut;
      return (w | 0) - cut | 0;
    } catch (error) { report("width", error); return w; }
  }
  function realWidth(gui) { return (gui.q | 0) + (gui.$jwCut | 0) | 0; }
  function pocketWidth(gui) { return wide(gui) ? POCKET : 0; }

  /** The pocket counts as inside the window (a click there must not drop the carried stack). */
  function inPocket(gui, mx, my) {
    try {
      var b = bounds(gui);
      if (!b) return false;
      var x = (mx | 0) - (gui.is | 0), y = (my | 0) - (gui.l7 | 0);
      return x >= b.x && x < b.x2 && y >= b.y && y < b.y2;
    } catch (error) { report("click", error); return false; }
  }

  /** Rectangles for the pocket (absolute coordinates): backdrop strip, frame, then one vanilla-looking cell per slot. */
  function pocketPlan(gui) {
    try {
      if (failed) return null;
      var b = bounds(gui);
      if (!b) return null;
      var L = gui.is | 0, T = gui.l7 | 0, px = L + b.x, py = T + b.y, qx = L + b.x2, qy = T + b.y2, rects = [];
      rects.push([px + 1, py, qx - 1, qy, 0xFF000000 | 0], [px, py + 1, qx, qy - 1, 0xFF000000 | 0],
        [px + 1, py + 1, qx - 1, qy - 1, 0xFFFFFFFF | 0], [px + 3, py + 3, qx - 1, qy - 1, 0xFF555555 | 0],
        [px + 3, py + 3, qx - 3, qy - 3, 0xFFC6C6C6 | 0]);
      for (var k = 0; k < b.cells.length; k += 2) {
        var x = L + b.cells[k], y = T + b.cells[k + 1];
        rects.push([x - 1, y - 1, x + 17, y + 17, 0xFF373737 | 0], [x, y, x + 17, y + 17, 0xFFFFFFFF | 0], [x, y, x + 16, y + 16, 0xFF8B8B8B | 0]);
      }
      var cut = gui.$jwCut | 0, strip = cut > 0 ? [gui.q | 0, 0, (gui.q | 0) + cut | 0, gui.L | 0] : null;
      stats.pockets++;
      return {strip: strip, rects: rects};
    } catch (error) { report("pocket", error); return null; }
  }

  // ---- connection -------------------------------------------------------------------------------------------------
  function str(s) { return $rt_str(s); }
  /** handleJoinGame: a new connection starts vanilla; the NetworkManager to say hello on, or null. */
  function hello(handler) {
    on = false;
    connection = handler || null;
    if (failed) return null;
    try {
      var net = handler ? handler.qf : null;
      if (!net || net.bkf) return null;
      if (!channelStr) { channelStr = str(CHANNEL); helloStr = str(HELLO); }
      return net;
    } catch (error) { report("hello", error); return null; }
  }
  function sent() { stats.hellos++; }
  /** The server agreed: turn the wide inventory on; the player's inventory window to widen now, or null. */
  function answered(handler) {
    try {
      if (failed) return null;
      if (!on) send("jaspercraft.wideinv.state", {state: "enabled", hellos: stats.hellos});
      on = true;
      stats.answers++;
      var mc = handler ? handler.cb : null, player = mc ? mc.v : null;
      return player && player.fm ? player.fm : null;
    } catch (error) { report("answer", error); return null; }
  }

  // Touch hotbar (site/jaspercraft-mobile-controls.js): number of buttons and selecting a position.
  try {
    $rt_globals.JasprWideBridge = {
      slots: function () { return slots(); },
      select: function (p) {
        try { p = p | 0; if (typeof HEH !== "undefined" && HEH && HEH.v && p >= 0 && p < slots()) HEH.v.bx.gP = index(p); }
        catch (error) { report("touch", error); }
      },
      selected: function () { try { return typeof HEH !== "undefined" && HEH && HEH.v ? pos(HEH.v.bx.gP) : -1; } catch (error) { return -1; } }
    };
  } catch (ignored) { }

  return {
    on: function () { return on && !failed; },
    slots: slots, index: index, pos: pos, scroll: scroll, heldWindow: heldWindow, creativeWindow: creativeWindow, order: order,
    ids: function (name) { return IDS[name]; },
    span: span, hotbarLeft: hotbarLeft, hotbarRight: hotbarRight,
    adoptPlan: adoptPlan, adopted: adopted, layout: layout, creativeSlot: creativeSlot,
    width: width, realWidth: realWidth, pocketWidth: pocketWidth, inPocket: inPocket, pocketPlan: pocketPlan,
    /** Window slot numbers of the extension slots in this window, in item order (36..55); [] when it has none. */
    extraSlots: function (container) {
      var out = [], l = list(container);
      if (!l) return out;
      for (var k = 0; k < (l.g | 0); k++) { var s = l.qN.data[k]; if (s && s.$jw === 1) out.push(s.pO | 0); }
      return out;
    },
    hello: hello, sent: sent, answered: answered,
    channel: function () { return channelStr; }, helloText: function () { return helloStr; },
    status: function () {
      return {on: on, failed: failed, lastError: lastError, slots: slots(), stats: JSON.parse(JSON.stringify(stats))};
    }
  };
}());

/* handleJoinGame: say hello on jaspr:inv (CPacketCustomPayload with a PacketBuffer string, as the gear module does). */
function JasprWideHello(a) {
  var d, e, f, g, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      g = JasprWide.hello(a);
      if (g === null) return;
      d = new AKy; e = new Iu;
      $p = 1;
    case 1:
      $z = Fru(); if (B()) break _;
      Lg(e, $z); f = JasprWide.helloText();
      $p = 2;
    case 2:
      $z = FuF(e, f); if (B()) break _;
      BgN(d, JasprWide.channel(), $z);
      $p = 3;
    case 3:
      g.wd(d); if (B()) break _;
      JasprWide.sent();
      return;
    default: FT();
  } }
  Ds().s(a, d, e, f, g, $p);
}

/* handleCustomPayload on jaspr:inv: the server agreed; widen the player's inventory window now. */
function JasprWideServerSaid(a) {
  var b, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprWide.answered(a);
      if (b === null) return;
      $p = 1;
    case 1:
      JasprWideAdopt(b); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, $p);
}

/* Appends the extension slots to a window that shows the player's vanilla slots (GuiContainer ctor, ContainerPlayer ctor). */
function JasprWideAdopt(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprWide.adoptPlan(a);
      if (b === null) return;
      c = 0;
      $p = 1;
    case 1:
      if (c >= b.add.length) { JasprWide.adopted(a, b); return; }
      d = Dk(b.inv, b.add[c], 0, 0);
      d.$jw = 1;
      $p = 2;
    case 2:
      DE9(a, d); if (B()) break _;
      c = c + 1 | 0;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

/* renderHotbar: cells 10..14 and the right edge, copied from a middle cell of the 182px hotbar texture. */
function JasprWideHotbarExt(a, b, c) {
  var d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      d = 0;
      $p = 1;
    case 1:
      if (d >= 5) { $p = 3; continue _; }
      $p = 2;
    case 2:
      FYu(a, (b + 181 | 0) + (d * 20 | 0) | 0, c, 21, 0, 20, 22); if (B()) break _;
      d = d + 1 | 0;
      $p = 1;
      continue _;
    case 3:
      FYu(a, b + 281 | 0, c, 181, 0, 1, 22); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

/* GuiContainer.drawScreen, right after the window background: backdrop strip, pocket frame and cells. */
function JasprWidePocketDraw(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprWide.pocketPlan(a);
      if (b === null) return;
      c = 0;
      if (b.strip === null) { $p = 2; continue _; }
      $p = 1;
    case 1:
      DNC(a, b.strip[0], b.strip[1], b.strip[2], b.strip[3], -1072689136, -804253680); if (B()) break _;
      $p = 2;
    case 2:
      if (c >= b.rects.length) { $p = 4; continue _; }
      d = b.rects[c];
      $p = 3;
    case 3:
      D49(d[0], d[1], d[2], d[3], d[4]); if (B()) break _;
      c = c + 1 | 0;
      $p = 2;
      continue _;
    case 4:
      CFh(1.0, 1.0, 1.0, 1.0); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}

/* InventoryPlayer.getFirstEmptyStack: the first empty slot in fill order (wide: the 14-slot hotbar, then each row). */
function JasprWideFirstEmpty(a) {
  var b, c, d, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprWide.order();
      c = 0;
      $p = 1;
    case 1:
      if (c >= b.length) return (-1);
      $p = 2;
    case 2:
      $z = DA(a.eL, b[c]); if (B()) break _;
      d = $z;
      $p = 3;
    case 3:
      $z = CCH(d); if (B()) break _;
      if ($z) return b[c];
      c = c + 1 | 0;
      $p = 1;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}
