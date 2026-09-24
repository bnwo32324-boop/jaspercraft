/* Shared waypoint wire codec. Mirror of Waypoint.java; both sides pin the same
 * golden vectors in tests/waypoint-codec.test.cjs and EquipmentProbe ("waypoints").
 *
 * Coord holder: "JW" + slot(b36,1) + death("1"/"0") + color(b36,1) + x(6) + z(6).
 * x/z are offset by +100,000,000, lowercase base36, zero-padded to 6.
 * Name holder: "JN" + slot(1) + sanitized name (<=36 chars).
 * Coord score = block y. Name score = 0. Objective "jwp", display "JWP v1 n=<count>".
 */
"use strict";
var WAYPOINT_B36 = "0123456789abcdefghijklmnopqrstuvwxyz";
var WAYPOINT_COORD_OFFSET = 100000000;
var WAYPOINT_COORD_DIGITS = 6;
var WAYPOINT_MAX_NAME = 36;
var WAYPOINT_OBJECTIVE = "jwp";
var WAYPOINT_DISPLAY_PREFIX = "JWP v1 n=";
var WAYPOINT_COLOR_NAMES = ["White", "Orange", "Magenta", "Light Blue",
  "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue",
  "Brown", "Green", "Red", "Black"];
var WAYPOINT_COLOR_RGB = [0xFFFFFF, 0xFF7F00, 0xFF00FF, 0x00BFFF,
  0xFFFF00, 0x00FF00, 0xFF69B4, 0x808080, 0xC0C0C0, 0x00CED1, 0x800080, 0x0000FF,
  0x8B4513, 0x008000, 0xFF0000, 0x000000];

function waypointBase36(value, digits) {
  var out = "";
  for (var i = 0; i < digits; i++) {
    out = WAYPOINT_B36[value % 36] + out;
    value = Math.floor(value / 36);
  }
  return out;
}

function waypointUnbase36(text) {
  var value = 0;
  for (var i = 0; i < text.length; i++) {
    var digit = WAYPOINT_B36.indexOf(text.charAt(i));
    if (digit < 0) return null;
    value = value * 36 + digit;
  }
  return value;
}

function waypointSanitizeName(name) {
  if (name === null || name === undefined) return "Waypoint";
  var clean = String(name).replace(/\u00a7/g, "?").trim().replace(/\s+/g, " ");
  if (!clean) return "Waypoint";
  return clean.length > WAYPOINT_MAX_NAME ? clean.slice(0, WAYPOINT_MAX_NAME) : clean;
}

function waypointCoordHolder(wp) {
  if (wp.slot < 0 || wp.slot >= 36 || Math.abs(wp.x) > 30000000 || Math.abs(wp.z) > 30000000) return null;
  return "JW" + WAYPOINT_B36[wp.slot] + (wp.death ? "1" : "0") + WAYPOINT_B36[wp.color & 15]
    + waypointBase36(wp.x + WAYPOINT_COORD_OFFSET, WAYPOINT_COORD_DIGITS)
    + waypointBase36(wp.z + WAYPOINT_COORD_OFFSET, WAYPOINT_COORD_DIGITS);
}

function waypointNameHolder(wp) {
  return "JN" + WAYPOINT_B36[wp.slot] + waypointSanitizeName(wp.name);
}

function waypointParseCoordHolder(holder) {
  if (typeof holder !== "string" || holder.length !== 17 || holder.slice(0, 2) !== "JW") return null;
  var slot = WAYPOINT_B36.indexOf(holder.charAt(2));
  var death = holder.charAt(3);
  var color = WAYPOINT_B36.indexOf(holder.charAt(4));
  if (slot < 0 || color < 0 || (death !== "0" && death !== "1")) return null;
  var x = waypointUnbase36(holder.slice(5, 11));
  var z = waypointUnbase36(holder.slice(11, 17));
  if (x === null || z === null) return null;
  x -= WAYPOINT_COORD_OFFSET;
  z -= WAYPOINT_COORD_OFFSET;
  if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return null;
  return { slot: slot, death: death === "1", color: color, x: x, z: z };
}

function waypointParseNameHolder(holder) {
  if (typeof holder !== "string" || holder.length < 3 || holder.length > 39 || holder.slice(0, 2) !== "JN") return null;
  var slot = WAYPOINT_B36.indexOf(holder.charAt(2));
  if (slot < 0) return null;
  return { slot: slot, name: holder.slice(3) };
}

if (typeof module !== "undefined") module.exports = {WAYPOINT_B36:WAYPOINT_B36,WAYPOINT_COORD_OFFSET:WAYPOINT_COORD_OFFSET,WAYPOINT_COORD_DIGITS:WAYPOINT_COORD_DIGITS,WAYPOINT_MAX_NAME:WAYPOINT_MAX_NAME,WAYPOINT_OBJECTIVE:WAYPOINT_OBJECTIVE,WAYPOINT_DISPLAY_PREFIX:WAYPOINT_DISPLAY_PREFIX,WAYPOINT_COLOR_NAMES:WAYPOINT_COLOR_NAMES,WAYPOINT_COLOR_RGB:WAYPOINT_COLOR_RGB,waypointBase36:waypointBase36,waypointUnbase36:waypointUnbase36,waypointSanitizeName:waypointSanitizeName,waypointCoordHolder:waypointCoordHolder,waypointNameHolder:waypointNameHolder,waypointParseCoordHolder:waypointParseCoordHolder,waypointParseNameHolder:waypointParseNameHolder};
