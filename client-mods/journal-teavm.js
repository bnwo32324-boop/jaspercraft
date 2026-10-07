/* The Field Journal for JasperCraft (owner, 2026-10-07: the empty space in the wide inventory, beside the crafting grid, should
 * show something useful: "1. event forecast, 3. character summary, 4. active item effects").
 *
 * The wide inventory (client-mods/wide-inventory-teavm.js) widens the player's window by 90 pixels, which leaves a gap right of
 * the crafting result. This stage draws a small dark panel there with three tabs:
 *   Soon  - the day and time of day, the Blood Moon, this player's invasion mark, a coarse disaster hint;
 *   You   - experience level and bar, stat ranks, how many stats can be raised now, a button that opens the stat sheet;
 *   Perks - the effects items keep on you (the ones the inventory deliberately lists no box for).
 * Every number comes from the server (plugin JasprJournal) on the plugin channel jaspr:journal as one JSON string: the client
 * says "hello 1" when it joins, the server then sends the panel whenever it changes and at least every ten seconds. Nothing
 * else about the game is read here and nothing is sent back but the hello and the "/stats" command a button press stands for.
 * Without data (an older server, the plugin off) the panel is simply not drawn; on phones in the contracted inventory view
 * there is no widened window, so no panel either (Expand brings both back). Nothing is hover-only: a tab, the Open Stats button
 * (its tap target runs to the bottom of the panel) and any other spot on the panel are plain taps (a spot turns the page of a
 * long list and then flips to the next tab: the big target for a finger).
 *
 * TeaVM names (audited on live classes.js): ID GuiContainer (is guiLeft, l7 guiTop, gv xSize, h2 container, J FontRenderer,
 * j Minecraft with v the player), A2Z ContainerPlayer, D49 drawRect, CA getStringWidth, ei2 FontRenderer.drawString(String, float,
 * float, int, boolean), CFh GlStateManager.color, Cn9 EntityPlayerSP.sendChatMessage, Cyr handleCustomPayload (CRh readString),
 * E8R handleJoinGame (AKy/Iu/Fru/Lg/FuF/BgN as the wide module's hello). The builder (scripts/build-journal-client.cjs) checks
 * they are all still declared. Plain-JS entry points never suspend; the two functions that call the engine are written in
 * TeaVM's resumable style (every engine call has its own saved state). ASCII only.
 */
var JasprJournal = (function () {
  "use strict";
  var CHANNEL = "jaspr:journal", HELLO = "hello 1", TAB_KEY = "jaspr.journal.tab.v1";
  // Window-relative geometry (GUI pixels). The window is S wide (176) and, widened, W = S + pocket (266) with a 4px frame.
  var INSET_TOP = 4, INSET_BOTTOM = 81, MARGIN_LEFT = 0, MARGIN_RIGHT = 8, MIN_POCKET = 86;
  var PAD = 3, TAB_H = 11, LINE = 9, LINES = 6, FOOTER = 8, STALE_MS = 60000;
  var TABS = [{id: "soon", label: "Soon", short: "Soon", one: "S"}, {id: "you", label: "You", short: "Me", one: "Y"}, {id: "perks", label: "Perks", short: "Fx", one: "P"}];

  var C = {
    white: 0xFFFFFFFF | 0, gray: 0xFFAAAAAA | 0, dim: 0xFF8E8E8E | 0, red: 0xFFFF5555 | 0, gold: 0xFFFFAA00 | 0, green: 0xFF55FF55 | 0,
    aqua: 0xFF55FFFF | 0, yellow: 0xFFFFFF55 | 0, blue: 0xFF5599FF | 0, orange: 0xFFFF9A2E | 0,
    edgeDark: 0xFF373737 | 0, edgeLight: 0xFFFFFFFF | 0, screen: 0xFF1B1B1B | 0, tabOn: 0xFF4A4A4A | 0, tabOff: 0xFF2C2C2C | 0,
    line: 0xFF5E5E5E | 0, barBack: 0xFF000000 | 0, barFill: 0xFF55FF55 | 0, barEdge: 0xFF5E5E5E | 0, button: 0xFF3E5C8A | 0, buttonHi: 0xFF6C93C9 | 0
  };

  var data = null, receivedAt = 0, seq = 0, tab = 0, page = 0, plans = null, command = null, hello = {connection: null};
  var stats = {hellos: 0, packets: 0, drawn: 0, clicks: 0, commands: 0, rejected: 0, errors: 0};
  var lastError = "", diagnostics = {sent: 0, page: "journal-" + Date.now().toString(36)};
  try { var saved = $rt_globals.localStorage.getItem(TAB_KEY); for (var t = 0; t < TABS.length; t++) if (TABS[t].id === saved) tab = t; } catch (ignored) { }

  function send(event, details) {
    // Bounded, same-origin, through the existing diagnostics route; counts and states only.
    try {
      var loc = $rt_globals.location;
      if (diagnostics.sent >= 6 || !loc || String(loc.pathname).indexOf("/jaspercraft/") !== 0 || typeof $rt_globals.fetch !== "function") return;
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
    try { if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft journal] " + where + ": " + lastError); } catch (ignored) { }
    if (stats.errors <= 3) send("jaspercraft.journal.error", {stage: String(where).slice(0, 40), error: lastError});
  }
  function now() {
    try { return $rt_globals.performance && typeof $rt_globals.performance.now === "function" ? $rt_globals.performance.now() : Date.now(); }
    catch (error) { return Date.now(); }
  }

  // ---- data -----------------------------------------------------------------------------------------------------------
  function int(v, lo, hi, fallback) { v = +v; return isFinite(v) ? Math.max(lo, Math.min(hi, Math.floor(v))) : fallback; }
  function text(v, max) {
    if (typeof v !== "string") return "";
    var out = "";
    for (var i = 0; i < v.length && out.length < max; i++) { var c = v.charCodeAt(i); if (c >= 32 && c < 127) out += v.charAt(i); }
    return out;
  }
  /** The payload as the panel uses it, every field bounded; null when it is not a version 1 object. */
  function parse(json) {
    if (typeof json !== "string" || json.length === 0 || json.length > 4096) return null;
    var o;
    try { o = JSON.parse(json); } catch (error) { return null; }
    if (!o || typeof o !== "object" || o.v !== 1) return null;
    var d = {day: int(o.d, 0, 99999999, 0), phase: text(o.ph, 8) || "Day", level: int(o.lv, 0, 9999, 0), xp: int(o.xp, 0, 40, 0),
      moon: null, invasion: null, disaster: null, rpg: null, fx: []};
    if (o.bm !== undefined) d.moon = int(o.bm, -2, 99999, -1);
    if (o.iv instanceof Array && o.iv.length === 2) d.invasion = [int(o.iv[0], 0, 3, 0), int(o.iv[1], 0, 99999999, 0)];
    if (o.dz instanceof Array && o.dz.length === 2) d.disaster = [int(o.dz[0], 0, 2, 0), text(o.dz[1], 24)];
    if (o.rp instanceof Array && o.rp.length === 5) {
      d.rpg = [int(o.rp[0], 0, 99999, 0), int(o.rp[1], 0, 999, 0), int(o.rp[2], 0, 999, 0), int(o.rp[3], 0, 999, 0), int(o.rp[4], -1, 99999, -1)];
    }
    if (o.fx instanceof Array) {
      for (var i = 0; i < o.fx.length && d.fx.length < 12; i++) {
        var e = o.fx[i];
        if (e instanceof Array && e.length === 2) d.fx.push({name: text(e[0], 28) || "?", seconds: int(e[1], 0, 3599, 0)});
      }
    }
    return d;
  }
  /** handleCustomPayload on jaspr:journal. */
  function receive(json) {
    try {
      var d = parse(json);
      if (!d) { stats.rejected++; return; }
      if (data === null) send("jaspercraft.journal.state", {state: "receiving", effects: d.fx.length});
      data = d;
      receivedAt = now();
      seq++;
      stats.packets++;
    } catch (error) { report("receive", error); }
  }
  function fresh() { return data !== null && now() - receivedAt < STALE_MS; }

  // ---- text layout ------------------------------------------------------------------------------------------------------
  function width(font, s) { return CA(font, $rt_str(s)) | 0; }
  /** Greedy word wrap of one text to a pixel width; words longer than the width are cut at the width. */
  function wrap(font, s, max) {
    if (width(font, s) <= max) return [s];
    var words = s.split(" "), lines = [], cur = "";
    for (var i = 0; i < words.length; i++) {
      var test = cur ? cur + " " + words[i] : words[i];
      if (width(font, test) <= max) { cur = test; continue; }
      if (cur) lines.push(cur);
      cur = words[i];
      while (width(font, cur) > max && cur.length > 1) {
        var cut = cur.length - 1;
        while (cut > 1 && width(font, cur.slice(0, cut)) > max) cut--;
        lines.push(cur.slice(0, cut));
        cur = cur.slice(cut);
      }
    }
    if (cur) lines.push(cur);
    return lines;
  }
  /** One row: segments [[text, color], ...]. When they do not fit side by side, each segment gets its own row (wrapped to the
   * width if it is still too long), the label first: nothing is ever cut off. */
  function row(font, segs, max, out) {
    var w = 0, i, k;
    for (i = 0; i < segs.length; i++) w += width(font, segs[i][0]);
    if (w <= max) { out.push({segs: segs}); return; }
    for (i = 0; i < segs.length; i++) {
      var parts = wrap(font, segs[i][0].replace(/^\s+|\s+$/g, ""), max);
      for (k = 0; k < parts.length; k++) out.push({segs: [[parts[k], segs[i][1]]]});
    }
  }

  function moonText(m) {
    if (m === -2) return ["NOW!", C.red];
    if (m === 0) return ["tonight", C.red];
    if (m === 1) return ["tomorrow", C.gold];
    if (m < 0) return ["none", C.dim];
    return ["in " + m + " nights", C.white];
  }
  function mmss(s) { return Math.floor(s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }

  /** The rows of the open tab, already wrapped to `max` pixels. */
  function rowsFor(font, which, max, elapsed) {
    var out = [], d = data, id = TABS[which].id;
    if (id === "soon") {
      if (d.moon !== null) { var m = moonText(d.moon); row(font, [["Blood Moon ", C.gray], [m[0], m[1]]], max, out); }
      if (d.invasion) {
        var iv = d.invasion, label = "Invasion ";
        if (iv[0] === 0) row(font, [[label, C.gray], ["none marked", C.dim]], max, out);
        else if (iv[0] === 1) row(font, [[label, C.gray], ["from day " + iv[1], C.white]], max, out);
        else if (iv[0] === 2) row(font, [[label, C.gray], ["any night", C.red]], max, out);
        else row(font, [[label, C.gray], ["UNDER WAY", C.red]], max, out);
      }
      if (d.disaster) {
        var dz = d.disaster;
        if (dz[0] === 2) row(font, [[(dz[1] || "Disaster") + " now!", C.red]], max, out);
        else if (dz[0] === 1) row(font, [["Disaster ", C.gray], ["brewing", C.gold]], max, out);
        else row(font, [["Disaster ", C.gray], ["quiet", C.green]], max, out);
      }
    } else if (id === "you") {
      row(font, [["Level ", C.gray], ["" + d.level, C.green]], max, out);
      out.push({bar: d.xp});
      if (d.rpg) {
        var r = d.rpg;
        row(font, [["" + r[0] + " ranks", C.white]], max, out);
        row(font, [["" + r[1] + "/" + r[2] + " stats", C.gray]], max, out);
        if (r[3] > 0) row(font, [["Raise " + r[3] + " now", C.green]], max, out);
        else if (r[4] > 0) row(font, [["next: " + r[4] + " lv", C.gray]], max, out);
        else row(font, [["all maxed", C.gold]], max, out);
      }
      out.push({button: "Open Stats", command: "/stats"});
    } else {
      if (!d.fx.length) {
        row(font, [["Nothing active", C.gray]], max, out);
        row(font, [["Items give perks while carried or worn.", C.dim]], max, out);
      }
      for (var i = 0; i < d.fx.length; i++) {
        var e = d.fx[i], left = e.seconds > 0 ? e.seconds - elapsed : 0;
        if (e.seconds > 0 && left <= 0) continue;
        if (e.seconds > 0) row(font, [[e.name + " ", C.white], [mmss(left), C.aqua]], max, out);
        else row(font, [[e.name, C.white]], max, out);
      }
    }
    return out;
  }

  // ---- the plan: absolute rectangles and strings -------------------------------------------------------------------
  function inContainer(gui) { try { return !!gui && typeof ID === "function" && gui instanceof ID && typeof A2Z === "function" && gui.h2 instanceof A2Z; } catch (error) { return false; } }
  /** The panel's frame in absolute coordinates, or null when this screen has no room for it. */
  function frame(gui) {
    try {
      if (!inContainer(gui) || typeof JasprWide === "undefined") return null;
      var pocket = JasprWide.pocketWidth(gui) | 0;
      if (pocket < MIN_POCKET) return null;
      var L = gui.is | 0, T = gui.l7 | 0, S = gui.gv | 0, W = S + pocket;
      return {x0: L + S + MARGIN_LEFT, x1: L + W - MARGIN_RIGHT, y0: T + INSET_TOP, y1: T + INSET_BOTTOM, L: L, T: T};
    } catch (error) { report("frame", error); return null; }
  }
  /** Tab buttons across the top of the inset: [{x0, x1}] in absolute x, measured with the real font. */
  function tabBoxes(font, f) {
    var inner = f.x1 - f.x0 - 2, sets = ["label", "short", "one"], labels = [], widths = [], total = 0, pad, s;
    search: for (s = 0; s < sets.length; s++) {
      labels = TABS.map(function (t) { return t[sets[s]]; });
      for (pad = s === 0 ? 3 : 2; pad >= (s === sets.length - 1 ? 0 : 1); pad--) {
        widths = labels.map(function (x) { return width(font, x) + pad * 2; });
        total = widths.reduce(function (a, b) { return a + b; }, 0);
        if (total <= inner) break search;
      }
    }
    // Spare width is shared out so the strip fills the inset.
    var extra = Math.max(0, inner - total), boxes = [], x = f.x0 + 1;
    for (var i = 0; i < widths.length; i++) {
      var share = i === widths.length - 1 ? extra - Math.floor(extra / widths.length) * i : Math.floor(extra / widths.length), w = widths[i] + share;
      boxes.push({x0: x, x1: x + w, label: labels[i]});
      x += w;
    }
    return boxes;
  }

  function plan(gui) {
    try {
      if (!fresh()) return null;
      var f = frame(gui), font = gui.J;
      if (!f || !font) return null;
      var t = now(), elapsed = Math.floor((t - receivedAt) / 1000);
      var key = [seq, tab, page, f.x0, f.y0, f.x1, f.y1, elapsed, TABS.length].join("/");
      if (plans && plans.key === key && plans.font === font) return plans.value;
      var rects = [], texts = [], hits = {tabs: [], body: null, button: null}, i;
      function rect(x1, y1, x2, y2, c) { rects.push([x1 | 0, y1 | 0, x2 | 0, y2 | 0, c | 0]); }
      function str(s, x, y, c) { texts.push({s: $rt_str(s), x: x, y: y, c: c | 0, shadow: 1}); }
      // The inset, like a slot: dark top and left edge, light bottom and right edge.
      rect(f.x0, f.y0, f.x1, f.y1, C.edgeDark); rect(f.x0 + 1, f.y0 + 1, f.x1 + 1, f.y1 + 1, C.edgeLight); rect(f.x0 + 1, f.y0 + 1, f.x1 - 1, f.y1 - 1, C.screen);
      rect(f.x1 - 1, f.y0, f.x1, f.y0 + 1, C.screen); rect(f.x0, f.y1 - 1, f.x0 + 1, f.y1, C.screen);
      // Tabs.
      var boxes = tabBoxes(font, f), ty = f.y0 + 1;
      for (i = 0; i < boxes.length; i++) {
        var b = boxes[i], on = i === tab;
        rect(b.x0, ty, b.x1, ty + TAB_H, on ? C.tabOn : C.tabOff);
        if (on) rect(b.x0, ty + TAB_H - 1, b.x1, ty + TAB_H, C.green);
        if (i > 0) rect(b.x0, ty, b.x0 + 1, ty + TAB_H, C.edgeDark);
        var tw = width(font, b.label);
        str(b.label, b.x0 + Math.floor((b.x1 - b.x0 - tw) / 2), ty + 2, on ? C.white : C.gray);
        hits.tabs.push([b.x0, ty, b.x1, ty + TAB_H + 2]);
      }
      rect(f.x0 + 1, ty + TAB_H, f.x1 - 1, ty + TAB_H + 1, C.line);
      // Content: pages of LINES rows.
      var max = f.x1 - f.x0 - 2 - PAD * 2, rows = rowsFor(font, tab, max, elapsed), pages = Math.max(1, Math.ceil(rows.length / LINES));
      if (page >= pages) { page = 0; }
      var top = ty + TAB_H + 3, from = page * LINES, shown = rows.slice(from, from + LINES);
      for (i = 0; i < shown.length; i++) {
        var r = shown[i], y = top + i * LINE, x = f.x0 + 1 + PAD;
        if (r.bar !== undefined) {
          var bw = max, filled = Math.round(bw * r.bar / 40);
          rect(x, y + 1, x + bw, y + 6, C.barEdge); rect(x + 1, y + 2, x + bw - 1, y + 5, C.barBack);
          if (filled > 2) rect(x + 1, y + 2, x + Math.min(bw - 1, filled), y + 5, C.barFill);
        } else if (r.button !== undefined) {
          var bwid = max, by = y;
          rect(x, by, x + bwid, by + 11, C.buttonHi); rect(x + 1, by + 1, x + bwid, by + 11, C.edgeDark); rect(x + 1, by + 1, x + bwid - 1, by + 10, C.button);
          var lw = width(font, r.button);
          str(r.button, x + Math.floor((bwid - lw) / 2), by + 2, C.white);
          // The tap target runs on to the bottom of the inset: a taller target than the drawn button for a finger.
          hits.button = {box: [x, by, x + bwid, by + 11], hit: [f.x0 + 1, by - 1, f.x1 - 1, f.y1 - 1], command: r.command};
        } else {
          var cx = x;
          for (var s = 0; s < r.segs.length; s++) { str(r.segs[s][0], cx, y, r.segs[s][1]); cx += width(font, r.segs[s][0]); }
        }
      }
      var fy = f.y1 - 1 - FOOTER;
      if (pages > 1) {
        var label = (page + 1) + "/" + pages + " tap", lw2 = width(font, label);
        str(label, f.x1 - 1 - PAD - lw2, fy, C.dim);
      } else if (TABS[tab].id === "soon") {
        var dayText = "Day " + data.day + " ", fx = f.x0 + 1 + PAD, phaseColor = data.phase === "Night" ? C.aqua : /^(Morning|Midday)$/.test(data.phase) ? C.yellow : C.orange;
        if (width(font, dayText + data.phase) <= max) { str(dayText, fx, fy, C.gray); str(data.phase, fx + width(font, dayText), fy, phaseColor); }
        else str("Day " + data.day, fx, fy, C.gray);
      }
      hits.body = [f.x0 + 1, ty + TAB_H + 1, f.x1 - 1, f.y1 - 1];
      hits.pages = pages;
      hits.frame = [f.x0, f.y0, f.x1, f.y1];
      var value = {rects: rects, texts: texts, hits: hits};
      plans = {key: key, font: font, value: value};
      stats.drawn++;
      return value;
    } catch (error) { report("plan", error); return null; }
  }

  // ---- input ------------------------------------------------------------------------------------------------------------
  function inside(box, x, y) { return !!box && x >= box[0] && x < box[2] && y >= box[1] && y < box[3]; }
  function setTab(i) {
    tab = i; page = 0; plans = null;
    try { $rt_globals.localStorage.setItem(TAB_KEY, TABS[i].id); } catch (ignored) { }
  }
  /** GuiContainer.mouseClicked: true when the click was on the panel (it is then used up, not passed to the slots). */
  function click(gui, mx, my, button) {
    try {
      if (!fresh() || !plans || !inContainer(gui)) return false;
      var f = frame(gui);
      if (!f) return false;
      mx = mx | 0; my = my | 0;
      var h = plans.value.hits;
      if (!inside(h.frame, mx, my)) return false;
      if ((button | 0) !== 0) return true;
      stats.clicks++;
      for (var i = 0; i < h.tabs.length; i++) if (inside(h.tabs[i], mx, my)) { if (i !== tab) setTab(i); return true; }
      if (h.button && inside(h.button.hit, mx, my)) { command = h.button.command; stats.commands++; return true; }
      // A tap anywhere else on the panel turns the page of a long list, and after the last page flips to the next tab: a big
      // target on a phone, where the tabs themselves are small.
      if (inside(h.body, mx, my)) {
        if (h.pages > 1 && page < h.pages - 1) { page++; plans = null; }
        else setTab((tab + 1) % TABS.length);
      }
      return true;
    } catch (error) { report("click", error); return false; }
  }
  /** The command a button press asked for, once (the drawing function sends it: it is the resumable one). */
  function takeCommand() { var c = command; command = null; return c === null ? null : $rt_str(c); }

  // ---- connection -------------------------------------------------------------------------------------------------------
  /** handleJoinGame: the NetworkManager to say hello on, or null. A new connection starts without data. */
  function helloTarget(handler) {
    data = null; plans = null; command = null; page = 0;
    hello.connection = handler || null;
    try {
      var net = handler ? handler.qf : null;
      if (!net || net.bkf) return null;
      return net;
    } catch (error) { report("hello", error); return null; }
  }
  function sent() { stats.hellos++; }

  return {
    channel: function () { return $rt_str(CHANNEL); }, helloText: function () { return $rt_str(HELLO); },
    receive: receive, parse: parse, plan: plan, click: click, takeCommand: takeCommand, helloTarget: helloTarget, sent: sent,
    tab: function () { return TABS[tab].id; }, setTab: setTab,
    status: function () { return {tab: TABS[tab].id, page: page, fresh: fresh(), hasData: data !== null, lastError: lastError, stats: JSON.parse(JSON.stringify(stats))}; }
  };
}());

/* handleJoinGame: say hello on jaspr:journal (CPacketCustomPayload with a PacketBuffer string, exactly as the wide module does). */
function JasprJournalHello(a) {
  var d, e, f, g, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      g = JasprJournal.helloTarget(a);
      if (g === null) return;
      d = new AKy; e = new Iu;
      $p = 1;
    case 1:
      $z = Fru(); if (B()) break _;
      Lg(e, $z); f = JasprJournal.helloText();
      $p = 2;
    case 2:
      $z = FuF(e, f); if (B()) break _;
      BgN(d, JasprJournal.channel(), $z);
      $p = 3;
    case 3:
      g.wd(d); if (B()) break _;
      JasprJournal.sent();
      return;
    default: FT();
  } }
  Ds().s(a, d, e, f, g, $p);
}

/* GuiContainer.drawScreen, right after the widened window's own drawing: the panel (rectangles, then text), then a pending button
 * command ("/stats") as the player's own chat line, then the colour back to white. */
function JasprJournalDraw(a) {
  var b, c, d, e, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b = JasprJournal.plan(a);
      if (b === null) return;
      c = 0;
      $p = 1;
    case 1:
      if (c >= b.rects.length) { c = 0; $p = 3; continue _; }
      d = b.rects[c];
      $p = 2;
    case 2:
      D49(d[0], d[1], d[2], d[3], d[4]); if (B()) break _;
      c = c + 1 | 0;
      $p = 1;
      continue _;
    case 3:
      if (c >= b.texts.length) { $p = 5; continue _; }
      d = b.texts[c];
      $p = 4;
    case 4:
      a.J.ei2(d.s, d.x, d.y, d.c, d.shadow); if (B()) break _;
      c = c + 1 | 0;
      $p = 3;
      continue _;
    case 5:
      e = JasprJournal.takeCommand();
      if (e === null) { $p = 7; continue _; }
      $p = 6;
    case 6:
      Cn9(a.j.v, e); if (B()) break _;
      $p = 7;
    case 7:
      CFh(1.0, 1.0, 1.0, 1.0); if (B()) break _;
      return;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, $p);
}

/* GuiContainer.mouseClicked: a click on the panel (a tab, the Open Stats button, the page of a long list) is used up here. */
function JasprJournalClick(a, b, c, d) {
  return JasprJournal.click(a, b, c, d) ? 1 : 0;
}
