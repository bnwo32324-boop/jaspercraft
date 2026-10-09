/* Big Mobs for JasperCraft (owner, 2026-10-05: in the Dungeon Dimension bosses and mobs must be scaled up "literally ... a larger
 * size and ... a larger hitbox", relative to their room and how much of a threat they are; 2026-10-08: "The bosses should be harder").
 *
 * The server (JasprDungeon Bodies) gives a scaled mob its real body (width, height, the bounding box around its feet) and, once a
 * second, sends every player in a dungeon world the table of scaled mobs near them on plugin channel jaspr:scale:
 * "id:hundredths,id:hundredths" (an empty string clears it). This module keeps that table and, for every entity in it,
 *   - draws the model scaled from its feet (RenderLivingBase.doRender, just before prepareScale, the way the vanilla giant is drawn),
 *   - grows its client hitbox to the same factor (Entity.setSize, once per change; its own size is remembered and given back when
 *     it leaves the table), so it is aimed at where it stands and its name tag floats over its head.
 * An entity not in the table is drawn exactly as before. Old servers never send the channel; nothing changes then.
 * Nothing here throws: a bad entry is skipped and counted.
 */
var JasprBigMobs = (function () {
  "use strict";
  var MAX_ENTRIES = 256, MIN_HUNDREDTHS = 25, MAX_HUNDREDTHS = 800, MAX_TEXT = 8192;
  var table = {}, size = 0, receivedAt = 0;
  var stats = {tables: 0, rejected: 0, grown: 0, restored: 0, draws: 0, failures: 0};
  var bodies = typeof WeakMap === "function" ? new WeakMap() : null;

  /** A jaspr:scale message: "id:hundredths,..." -> the new table (the old one is replaced whole). */
  function receive(text) {
    try {
      var next = {}, n = 0;
      if (typeof text === "string" && text.length > 0 && text.length <= MAX_TEXT) {
        var parts = text.split(",");
        for (var i = 0; i < parts.length && n < MAX_ENTRIES; i++) {
          var p = parts[i].split(":");
          if (p.length !== 2 || !/^\d{1,10}$/.test(p[0]) || !/^\d{1,3}$/.test(p[1])) { stats.rejected++; continue; }
          var id = +p[0], h = +p[1];
          if (h < MIN_HUNDREDTHS || h > MAX_HUNDREDTHS) { stats.rejected++; continue; }
          if (!(id in next)) n++;
          next[id] = h / 100;
        }
      } else if (typeof text === "string" && text.length > MAX_TEXT) stats.rejected++;
      table = next; size = n; receivedAt = Date.now(); stats.tables++;
    } catch (error) { stats.failures++; }
  }

  /** The factor an entity is drawn at (1: its own size). entity.cu is the entity id. */
  function scale(entity) {
    try { var s = entity ? table[entity.cu | 0] : undefined; return s > 0 ? s : 1; } catch (error) { return 1; }
  }

  /**
   * Called from the render: grows (or gives back) the client hitbox to the entity's factor, once per change, and returns the
   * factor to draw at. entity.bI is its width, entity.bZ its height; FET is Entity.setSize (which on the client never moves it).
   */
  function prepare(entity) {
    try {
      var s = scale(entity);
      if (!entity || !bodies) return s;
      var body = bodies.get(entity);
      if (!body) { if (s === 1) return 1; body = {w: entity.bI, h: entity.bZ, s: 1}; bodies.set(entity, body); }
      // Something else resized it meanwhile (a slime's size, a zombie growing up): that is its own size now.
      if (Math.abs(entity.bI - body.w * body.s) > 1e-4 || Math.abs(entity.bZ - body.h * body.s) > 1e-4) { body.w = entity.bI; body.h = entity.bZ; body.s = 1; }
      if (body.s !== s) {
        FET(entity, body.w * s, body.h * s);
        if (s === 1) stats.restored++; else stats.grown++;
        body.s = s;
        // The last bodies changed, for the diagnostics (a handful, newest last).
        changed.push({id: entity.cu | 0, scale: s, width: Math.round(entity.bI * 100) / 100, height: Math.round(entity.bZ * 100) / 100});
        if (changed.length > 8) changed.shift();
      }
      if (s !== 1) stats.draws++;
      return s;
    } catch (error) { stats.failures++; return 1; }
  }
  var changed = [];

  function status() {
    return {entries: size, ageMs: receivedAt ? Date.now() - receivedAt : -1, tables: stats.tables, rejected: stats.rejected, grown: stats.grown,
      restored: stats.restored, draws: stats.draws, failures: stats.failures, changed: changed.slice()};
  }
  return {receive: receive, scale: scale, prepare: prepare, status: status};
}());
try { if (typeof window !== "undefined" && window) window.JasprBigMobsDiagnostics = Object.freeze({status: function () { return JasprBigMobs.status(); }}); }
catch (error) { /* diagnostics are optional */ }
