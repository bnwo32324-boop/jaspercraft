/* Pure waypoint marker state and beam geometry. No engine natives: the TeaVM glue
 * (waypoint-markers-teavm.js) injects scoreboard entries and draws the returned
 * beams. Codec primitives come from waypoint-codec.js, concatenated before this file.
 */
"use strict";
function createJasprWaypointMarkers(api) {
  var MAX_MARKERS = 24;
  var BEAM_HALF_WIDTH = 0.35;
  var BEAM_BELOW = 4;
  var BEAM_ABOVE = 72;
  var errors = 0, draws = 0, lastCount = 0, disabled = false;

  function finite3(v) {
    return v && isFinite(v[0]) && isFinite(v[1]) && isFinite(v[2]);
  }

  // entries: [{holder:string, score:int}] as read from the hidden objective.
  function sync(entries) {
    var coords = Object.create(null), names = Object.create(null);
    for (var i = 0; i < entries.length; i++) {
      var holder = entries[i] && entries[i].holder;
      if (typeof holder !== "string") continue;
      if (holder.slice(0, 2) === "JW") {
        var parsed = waypointParseCoordHolder(holder);
        if (parsed) coords[parsed.slot] = { parsed: parsed, y: entries[i].score | 0 };
      } else if (holder.slice(0, 2) === "JN") {
        var named = waypointParseNameHolder(holder);
        if (named) names[named.slot] = named.name;
      }
    }
    var markers = [];
    for (var slot in coords) {
      if (markers.length >= MAX_MARKERS) break;
      var c = coords[slot];
      markers.push({ slot: c.parsed.slot, death: c.parsed.death, color: c.parsed.color,
        x: c.parsed.x, y: c.y, z: c.parsed.z, name: names[slot] || "" });
    }
    markers.sort(function (a, b) { return a.slot - b.slot; });
    lastCount = markers.length;
    return markers;
  }

  function rgb(color) {
    var hex = WAYPOINT_COLOR_RGB[color & 15];
    return [((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255];
  }

  // Two crossed vertical quads in camera-relative coords, matching the gore
  // drawLocal vertex layout [x,y,z,u,v,nx,ny,nz] x4. Returns null when unusable.
  function beam(marker, camera) {
    if (!marker || !finite3(camera)) return null;
    if (!isFinite(marker.x) || !isFinite(marker.y) || !isFinite(marker.z)) return null;
    var cx = marker.x + 0.5 - camera[0], cz = marker.z + 0.5 - camera[2];
    var y0 = marker.y - BEAM_BELOW - camera[1], y1 = marker.y + BEAM_ABOVE - camera[1];
    if (!(y1 > y0)) return null;
    var w = BEAM_HALF_WIDTH;
    return { color: rgb(marker.color), quads: [
      { points: [[cx - w, y0, cz], [cx + w, y0, cz], [cx + w, y1, cz], [cx - w, y1, cz]], normal: [0, 0, 1] },
      { points: [[cx, y0, cz - w], [cx, y0, cz + w], [cx, y1, cz + w], [cx, y1, cz - w]], normal: [1, 0, 0] }
    ] };
  }

  function draw(markers, camera) {
    var out = [];
    for (var i = 0; i < markers.length; i++) {
      var beamMesh = beam(markers[i], camera);
      if (beamMesh) out.push(beamMesh);
    }
    draws++;
    return out;
  }

  function noteError() {
    errors++;
    if (errors > 5) disabled = true;
  }

  return {
    sync: sync, beam: beam, draw: draw, rgb: rgb,
    noteError: noteError, reset: function () { errors = 0; disabled = false; },
    status: function () {
      return { markers: lastCount, draws: draws, errors: errors, disabled: disabled,
        maxMarkers: MAX_MARKERS, beamBelow: BEAM_BELOW, beamAbove: BEAM_ABOVE };
    },
    isDisabled: function () { return disabled; }
  };
}
if (typeof module !== "undefined") module.exports = {createJasprWaypointMarkers:createJasprWaypointMarkers};
