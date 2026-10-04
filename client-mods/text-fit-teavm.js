/* Text that always fits on screen (owner, 2026-10-04: item descriptions were "cut off, and I can't read it when hovering
 * above it with my mouse cursor ... in creative mode and survival mode", and "the Dark Souls-esque text that appears on
 * screen when entering a new biome ... is sometimes cut off. Depending on how lengthy the text is, it should never be
 * cut off. It should appear on screen, and it should be legible and readable just like the item descriptions").
 *
 * Tooltips (GuiScreen.drawHoveringText, C8Q): vanilla puts the box right of the cursor and flips it left when it
 * would cross the screen width, without ever wrapping, so a long line of lore ran off the left edge. Here the lines
 * are first wrapped (colour and format codes carried onto the next line) to fit beside the cursor, or across the
 * whole screen when neither side has room; the box is then placed right of the cursor, left of it, or clamped onto
 * the screen, against the real screen width (a widened inventory window lays itself out on a narrower gui.q, see
 * client-mods/wide-inventory-teavm.js), and never above the top edge.
 *
 * Titles, subtitles and the action bar (GuiIngame.renderGameOverlay, Ewc): vanilla draws the title at 4x and the
 * subtitle at 2x, centred, at any length. Here each one keeps its size when it fits; otherwise it steps down to a
 * smaller scale that still reads clearly (title 3.5 / 3 / 2.5 / 2, subtitle 1.5 / 1) and, if it still does not fit at
 * the smallest, wraps onto more lines (the title grows upward, the subtitle downward, the action bar upward).
 *
 * TeaVM names: C8Q drawHoveringText (a gui, b List<String>, c/j x, k y, f widest line, a.J FontRenderer, a.q width,
 * a.L height), Ewc renderGameOverlay (d scaled width, f FontRenderer, a.bOl title, a.bTS subtitle, a.cbA action bar),
 * CA getStringWidth, FWM GlStateManager.scale, ei2 FontRenderer.drawString(String, float, float, int, boolean),
 * Y ArrayList.add (.g size, .qN.data, .f6 modCount), $rt_str / $rt_ustr Java <-> JS strings.
 */
var JasprTextFit = (function () {
  "use strict";
  var MARGIN = 4, LINE = 10, CACHE_MAX = 64;
  var TITLE = [4, 3.5, 3, 2.5, 2], SUBTITLE = [2, 1.5, 1], ACTION = [1];
  var failed = false, lastError = "", tooltips = new Map(), plans = new Map();
  var stats = {wrappedTooltips: 0, placedTooltips: 0, plans: 0, scaled: 0, wrappedTitles: 0, errors: 0};
  var last = null, shown = {};

  function report(where, error) {
    stats.errors++;
    lastError = String(error && error.message || error).replace(/[\r\n]+/g, " ").slice(0, 180);
    try { if ($rt_globals.console) $rt_globals.console.warn("[JasperCraft text fit] " + where + ": " + lastError); } catch (ignored) { }
    if (stats.errors > 20) failed = true;   // something is badly wrong: fall back to vanilla
  }
  function js(s) { return s === null || s === undefined ? "" : $rt_ustr(s); }
  function width(font, s) { return CA(font, $rt_str(s)) | 0; }
  function remember(map, key, value) {
    map.set(key, value);
    if (map.size > CACHE_MAX) map.delete(map.keys().next().value);
    return value;
  }
  /** The real screen width a screen lays out on (a widened inventory window uses a narrower gui.q). */
  function screenWidth(gui) {
    try { if (typeof JasprWide !== "undefined" && JasprWide) return JasprWide.realWidth(gui) | 0; } catch (ignored) { }
    return gui.q | 0;
  }

  /** The formatting in force at the end of s, as FontRenderer.getFormatFromString: a colour, then format codes. */
  function formats(s) {
    var out = "";
    for (var i = 0; i < s.length - 1; i++) {
      if (s.charAt(i) !== "\u00a7") continue;
      var c = s.charAt(i + 1).toLowerCase();
      if ("0123456789abcdef".indexOf(c) >= 0) out = "\u00a7" + c;
      else if ("klmno".indexOf(c) >= 0) out += "\u00a7" + c;
      else if (c === "r") out = "";
      i++;
    }
    return out;
  }
  /** Word wrap to max pixels; each new line starts with the formatting in force; a word wider than a line is split. */
  function wrap(font, s, max) {
    if (width(font, s) <= max) return [s];
    var words = s.split(" "), lines = [], cur = "", started = false;
    for (var w = 0; w < words.length; w++) {
      var word = words[w], test = started ? cur + " " + word : cur + word;
      if (width(font, test) <= max) { cur = test; started = true; continue; }
      if (started) { lines.push(cur); cur = formats(cur) + word; }
      else cur = cur + word;
      started = true;
      while (width(font, cur) > max) {
        var cut = cur.length - 1;
        while (cut > 1 && width(font, cur.slice(0, cut)) > max) cut--;
        if (cut > 1 && cur.charAt(cut - 1) === "\u00a7") cut--;
        if (cut < 1 || cut >= cur.length) break;
        var head = cur.slice(0, cut);
        lines.push(head);
        cur = formats(head) + cur.slice(cut);
      }
    }
    lines.push(cur);
    return lines;
  }

  // ---- tooltips ---------------------------------------------------------------------------------------------------
  /** drawHoveringText, before it measures: wrap the lines of an ArrayList in place so the box fits on screen. */
  function tooltip(gui, list, mouseX) {
    try {
      if (failed || !gui || !list || !list.qN || typeof list.g !== "number") return;
      var font = gui.J, n = list.g | 0;
      if (!font || n <= 0) return;
      var W = screenWidth(gui), H = gui.L | 0, src = [], widest = 0;
      for (var i = 0; i < n; i++) {
        var t = js(list.qN.data[i]);
        src.push(t);
        var w = width(font, t);
        if (w > widest) widest = w;
      }
      var right = W - (mouseX + 12) - MARGIN - 3, left = mouseX - 16 - MARGIN - 3, full = W - 2 * (MARGIN + 3);
      if (widest <= right || widest <= left || widest <= 0) return;
      var max = Math.max(right, left);
      if (max < 150) max = full;
      max = Math.max(40, Math.min(max, full));
      var key = max + "\u0000" + src.join("\n"), lines = tooltips.get(key);
      if (!lines) {
        var plain = [];
        for (var a = 0; a < src.length; a++) plain = plain.concat(wrap(font, src[a], max));
        // Too tall for the screen and narrower than it could be: use the whole width.
        if (plain.length * LINE + 2 > H - 2 * MARGIN && max < full) {
          plain = [];
          for (var b = 0; b < src.length; b++) plain = plain.concat(wrap(font, src[b], full));
        }
        lines = remember(tooltips, key, plain.map(function (s) { return $rt_str(s); }));
        stats.wrappedTooltips++;
      }
      list.g = 0;
      list.f6 = (list.f6 | 0) + 1;
      for (var k = 0; k < lines.length; k++) Y(list, lines[k]);
    } catch (error) { report("tooltip", error); }
  }
  /** The box's left edge (j is mouse x + 12, f the widest line): right of the cursor, else left of it, else clamped. */
  function tooltipX(gui, j, f) {
    var x = j | 0;
    try {
      var mouseX = x - 12, W = screenWidth(gui), edge = MARGIN + 3;
      if (x + f + edge > W) {
        x = mouseX - 16 - f;
        if (x < edge) x = Math.max(edge, Math.min(mouseX + 12, W - f - edge));
      }
      stats.placedTooltips++;
      last = {x: x | 0, w: f | 0, screen: W, mouseX: mouseX};
      return x | 0;
    } catch (error) { report("tooltipX", error); return x; }
  }

  // ---- titles, subtitles, action bar ------------------------------------------------------------------------------
  /** How to draw one HUD text: {scale, shadow, lines: [{s: Java string, x, y}]} in scaled units, or null. */
  function plan(font, text, base, y0, screen, kind) {
    try {
      if (failed || !font || text === null || text === undefined) return null;
      var s = js(text);
      var key = kind + "\u0000" + screen + "\u0000" + s, p = plans.get(key);
      if (p) return p;
      var scales = kind === 0 ? TITLE : kind === 1 ? SUBTITLE : ACTION, max = Math.max(40, (screen | 0) - 4 * MARGIN);
      var w = width(font, s), scale = scales[scales.length - 1], lines;
      for (var i = 0; i < scales.length; i++) if (w * scales[i] <= max) { scale = scales[i]; break; }
      if (scale < base) stats.scaled++;
      lines = w * scale <= max ? [s] : wrap(font, s, Math.floor(max / scale));
      if (lines.length > 1) stats.wrappedTitles++;
      var n = lines.length, out = [];
      for (var k = 0; k < n; k++) {
        // The title grows upward from its vanilla line, the subtitle downward, the action bar upward from the hotbar.
        var y = kind === 1 ? y0 + k * LINE : y0 - (n - 1 - k) * LINE;
        out.push({s: $rt_str(lines[k]), x: -(width(font, lines[k]) / 2 | 0), y: y});
      }
      stats.plans++;
      var widest = 0;
      for (var q = 0; q < n; q++) widest = Math.max(widest, width(font, lines[q]));
      shown[kind] = {scale: scale, lines: n, width: Math.round(widest * scale), screen: screen | 0};
      return remember(plans, key, {scale: scale, shadow: kind === 2 ? 0 : 1, lines: out});
    } catch (error) { report("plan", error); return null; }
  }

  return {
    tooltip: tooltip, tooltipX: tooltipX, plan: plan, wrap: wrap, formats: formats,
    status: function () { return {failed: failed, lastError: lastError, last: last, shown: shown, stats: JSON.parse(JSON.stringify(stats))}; }
  };
}());

/* GuiIngame title, subtitle and action bar (Ewc): at the plan's scale, every line centred; the caller pushed the matrix
 * (translated to the text's centre) and pops it afterwards. */
function JasprTextDraw(a, b, c, d, e, f, g) {
  var h, i, j, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); j = $T.l(); i = $T.l(); h = $T.l(); g = $T.l(); f = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      h = JasprTextFit.plan(a, b, c, d, f, g);
      if (h === null) return;
      $p = 1;
    case 1:
      FWM(h.scale, h.scale, h.scale); if (B()) break _;
      i = 0;
      $p = 2;
    case 2:
      if (i >= h.lines.length) return;
      j = h.lines[i];
      $p = 3;
    case 3:
      a.ei2(j.s, j.x, j.y, e, h.shadow); if (B()) break _;
      i = i + 1 | 0;
      $p = 2;
      continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, e, f, g, h, i, j, $p);
}
