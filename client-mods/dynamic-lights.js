/* Shared dynamic-lights wire codec + sampling math. Java mirror: DynamicLights.java.
 * Holder: "#jdl." + x + "." + y + "." + z + "." + level (block coords, level 1-15).
 * Objective "jdl", display gated on "JDL v1". Score value unused (0).
 * Combined light packing (vanilla): sky << 20 | block << 4; inject takes max(block).
 * Falloff mirrors vanilla emission decay: level drops 1 per block (FAST), softer
 * in SMOOTH. Everything fails closed: malformed holders are ignored, mix errors
 * return the native value.
 */
"use strict";
var DYNAMIC_LIGHTS_OBJECTIVE = "jdl";
var DYNAMIC_LIGHTS_DISPLAY_PREFIX = "JDL v1";
var DYNAMIC_LIGHTS_HOLDER_PREFIX = "#jdl.";
var DYNAMIC_LIGHTS_OFF = 0;
var DYNAMIC_LIGHTS_FAST = 1;
var DYNAMIC_LIGHTS_SMOOTH = 2;
var DYNAMIC_LIGHTS_MAX_SOURCES = 48;

function dynamicLightsHolder(x, y, z, level) {
  if (!isFinite(x) || !isFinite(y) || !isFinite(z) || !isFinite(level)) return null;
  x |= 0; y |= 0; z |= 0; level |= 0;
  if (level < 1 || level > 15) return null;
  if (y < 0 || y > 255) return null;
  if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return null;
  return DYNAMIC_LIGHTS_HOLDER_PREFIX + x + "." + y + "." + z + "." + level;
}

function dynamicLightsParseHolder(holder) {
  if (typeof holder !== "string") return null;
  if (holder.slice(0, 5) !== DYNAMIC_LIGHTS_HOLDER_PREFIX) return null;
  var parts = holder.slice(5).split(".");
  if (parts.length !== 4) return null;
  for (var i = 0; i < 4; i++) {
    if (!/^-?\d{1,9}$/.test(parts[i])) return null;
  }
  var x = parseInt(parts[0], 10), y = parseInt(parts[1], 10);
  var z = parseInt(parts[2], 10), level = parseInt(parts[3], 10);
  if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return null;
  if (y < 0 || y > 255) return null;
  if (level < 1 || level > 15) return null;
  return { x: x, y: y, z: z, level: level };
}

function dynamicLightsFalloff(mode) {
  return mode === DYNAMIC_LIGHTS_SMOOTH ? 0.7 : 1.0;
}

function dynamicLightsRadius(level, mode) {
  if (mode === DYNAMIC_LIGHTS_SMOOTH) return Math.ceil(level / 0.7);
  return level;
}

// Max dynamic block-light (0-15) at a block position from parsed sources.
function dynamicLightsLevelAt(sources, x, y, z, mode) {
  var falloff = dynamicLightsFalloff(mode), best = 0;
  for (var i = 0; i < sources.length; i++) {
    var s = sources[i];
    var radius = dynamicLightsRadius(s.level, mode);
    var dx = x - s.x; if (dx > radius || dx < -radius) continue;
    var dy = y - s.y; if (dy > radius || dy < -radius) continue;
    var dz = z - s.z; if (dz > radius || dz < -radius) continue;
    var d2 = dx * dx + dy * dy + dz * dz;
    if (d2 > radius * radius) continue;
    var v = s.level - Math.floor(Math.sqrt(d2) * falloff);
    if (v > best) {
      best = v;
      if (best >= 15) return 15;
    }
  }
  return best;
}

// Inject dynamic light into a vanilla combined value. Never darkens.
function dynamicLightsMix(packed, x, y, z, sources, mode) {
  var dyn = dynamicLightsLevelAt(sources, x, y, z, mode);
  if (dyn <= 0) return packed;
  var nativeBlock = (packed >> 4) & 15;
  if (dyn <= nativeBlock) return packed;
  return (packed & ~0xF0) | (dyn << 4);
}

// Canonical signature for change detection (sorted so mesh invalidation is stable).
function dynamicLightsSignature(holders) {
  var copy = holders.slice(0);
  copy.sort();
  return copy.join("|");
}

// World-level sampler (entities, particles, hand, drops, tile entities):
// orig(world, pos, value) with pos carrying integer m/i/l fields.
function dynamicLightsSampleWorld(orig, world, pos, value, mode, sources) {
  var nativeValue = orig(world, pos, value);
  if (mode === DYNAMIC_LIGHTS_OFF || !sources || sources.length === 0) return nativeValue;
  try {
    return dynamicLightsMix(nativeValue, pos.m | 0, pos.i | 0, pos.l | 0, sources, mode);
  } catch (e) { return nativeValue; }
}

if (typeof module !== "undefined") module.exports = {DYNAMIC_LIGHTS_OBJECTIVE:DYNAMIC_LIGHTS_OBJECTIVE,DYNAMIC_LIGHTS_DISPLAY_PREFIX:DYNAMIC_LIGHTS_DISPLAY_PREFIX,DYNAMIC_LIGHTS_HOLDER_PREFIX:DYNAMIC_LIGHTS_HOLDER_PREFIX,DYNAMIC_LIGHTS_OFF:DYNAMIC_LIGHTS_OFF,DYNAMIC_LIGHTS_FAST:DYNAMIC_LIGHTS_FAST,DYNAMIC_LIGHTS_SMOOTH:DYNAMIC_LIGHTS_SMOOTH,DYNAMIC_LIGHTS_MAX_SOURCES:DYNAMIC_LIGHTS_MAX_SOURCES,dynamicLightsHolder:dynamicLightsHolder,dynamicLightsParseHolder:dynamicLightsParseHolder,dynamicLightsFalloff:dynamicLightsFalloff,dynamicLightsRadius:dynamicLightsRadius,dynamicLightsLevelAt:dynamicLightsLevelAt,dynamicLightsMix:dynamicLightsMix,dynamicLightsSignature:dynamicLightsSignature,dynamicLightsSampleWorld:dynamicLightsSampleWorld};
