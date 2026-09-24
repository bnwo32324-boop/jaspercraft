/* Shared shader-pack runtime. Pure logic only; the browser glue lives in
 * client-mods/shader-packs-teavm.js. Packs mirror their OptiFine sources in
 * C:/Users/AM/Downloads (see per-pack notes below). Everything fails closed:
 * unknown modes behave as OFF, malformed lightmap data passes through.
 *
 * MakeUp-UltraFast-9.5e (COLOR_SCHEME=1, no-effects profile): vivid clean
 *   grade, gentle filmic curve, subtle vignette. No shadows/DOF/bloom in the
 *   profile itself, so the port is its color science, not missing effects.
 * Chocapic13 HighPerformance Toaster (deferred.fsh/composite.fsh): Unreal
 *   filmic tonemap (exact formula), warm torch (1.0/0.42/0.11), custom
 *   torch/sky lightmap curves, 8-bit dither, soft vignette.
 * miniature-shader-2.19 (LOWEST profile, SHADOW_DARKNESS 0.0): shadowless
 *   flat lighting (dark floor lifted), LIGHT_BRIGHTNESS 0.9 exposure, boosted
 *   saturation/contrast, stronger vignette. The pack itself has no tilt-shift
 *   pass; the diorama feel here comes from its flat-bright lighting, which is
 *   what this port reproduces (plus an optional focus band for flavor).
 * BSL v8.2.05: the pack's own tonemap shape -- an extended Reinhard with a
 *   white point, opened up by its upper curve -- plus its warm blocklight, the
 *   bright filmic contrast it is known for, and a light vignette. BSL's warm
 *   highlight / cool shadow split is approximated with a warm overall tint,
 *   since this post path carries one multiply rather than a split-tone.
 * Complementary Unbound r5.9.3: the clean, natural end of the two Complementary
 *   editions. Ported as an ACES-style filmic curve (which is what its highlight
 *   rolloff reads as), a neutral tint, restrained contrast and the lightest
 *   vignette of the set, with a vivid but not electric saturation lift.
 * Sildur's Vibrant Shaders v1.41 Lite (supplied in Downloads): the active Lite
 *   profile's Uncharted2 final tone map, vibrant color grade, warm torch/blue-
 *   sky lightmap shaping, and cool fog tint. Its native Contrast 2.2 value is
 *   adapted here because the Eaglercraft post-process input is already display
 *   encoded; using 2.2 as a second gamma lift would wash out the scene.
 *   OptiFine-only geometry passes remain on the native Eaglercraft renderer;
 *   the compatible visual profile is applied through the same fail-closed
 *   post/lightmap path.
 */
"use strict";
var SHADER_PACK_OFF = 0;
var SHADER_PACK_MAKEUP = 1;
var SHADER_PACK_CHOCAPIC = 2;
var SHADER_PACK_MINIATURE = 3;
var SHADER_PACK_SILDUR = 4;
var SHADER_PACK_BSL = 5;
var SHADER_PACK_COMPLEMENTARY = 6;
var SHADER_PACK_KEY = "jaspr.shader.pack";
var SHADER_PACK_LABELS = ["OFF", "MakeUp UltraFast", "Chocapic13 Toaster", "Miniature",
  "Sildur's Vibrant Lite", "BSL", "Complementary Unbound"];
var SHADER_PACK_COUNT = 7;
// The native Video Settings button is only 150px wide. Keep the selector
// label short while retaining the full names in the picker and diagnostics.
var SHADER_PACK_SELECTOR_LABELS = ["OFF", "MakeUp", "Chocapic", "Miniature", "Sildur Lite",
  "BSL", "Complementary"];

function shaderPackLabel(mode) {
  if (mode < 0 || mode >= SHADER_PACK_COUNT) return SHADER_PACK_LABELS[0];
  return SHADER_PACK_LABELS[mode | 0];
}

function shaderPackSelectorLabel(mode) {
  if (mode < 0 || mode >= SHADER_PACK_COUNT) return SHADER_PACK_SELECTOR_LABELS[0];
  return SHADER_PACK_SELECTOR_LABELS[mode | 0];
}

function shaderPackCycle(mode) {
  return ((mode | 0) + 1) % SHADER_PACK_COUNT;
}

// Final-grade uniforms per pack. Saturation/contrast pivot on middle gray,
// tint multiplies, vignette darkens corners, tilt enables the focus band.
function shaderPackGrade(mode) {
  switch (mode | 0) {
    case SHADER_PACK_MAKEUP:
      return { exposure: 1.06, saturation: 1.34, contrast: 1.07,
        tint: [1.0, 0.99, 0.97], vignette: 0.28, tonemap: 2, dither: 1,
        gamma: 1.0, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
    case SHADER_PACK_CHOCAPIC:
      return { exposure: 0.95, saturation: 1.06, contrast: 1.06,
        tint: [1.0, 0.96, 0.91], vignette: 0.32, tonemap: 3, dither: 1,
        gamma: 1.0, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
    case SHADER_PACK_MINIATURE:
      return { exposure: 0.92, saturation: 1.42, contrast: 1.10,
        tint: [1.0, 1.0, 0.99], vignette: 0.38, tonemap: 2, dither: 0,
        gamma: 1.0, tilt: 1, focusY: 0.55, focusH: 0.32, blur: 6.0 };
    case SHADER_PACK_SILDUR:
      // The source pack's Contrast=2.2 is too strong when applied to this
      // already display-encoded backbuffer. These renderer-adapted values
      // keep Sildur's color character while restoring readable highlights.
      return { exposure: 0.72, saturation: 1.12, contrast: 1.06,
        tint: [1.0, 0.99, 0.97], vignette: 0.14, tonemap: 4, dither: 0,
        gamma: 1.30, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
    case SHADER_PACK_BSL:
      // Bright and filmic with warm highlights, which is the whole reason people
      // pick BSL. The curve holds the top end, so the contrast can be pushed.
      return { exposure: 1.04, saturation: 1.20, contrast: 1.12,
        tint: [1.0, 0.982, 0.952], vignette: 0.20, tonemap: 6, dither: 1,
        gamma: 1.0, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
    case SHADER_PACK_COMPLEMENTARY:
      // Unbound is the restrained one: colour that looks like the game, only
      // better lit. Neutral tint, gentle contrast, barely any vignette.
      return { exposure: 1.0, saturation: 1.16, contrast: 1.05,
        tint: [1.0, 1.0, 1.0], vignette: 0.10, tonemap: 5, dither: 1,
        gamma: 1.0, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
    default:
      return { exposure: 1.0, saturation: 1.0, contrast: 1.0,
        tint: [1.0, 1.0, 1.0], vignette: 0.0, tonemap: 0, dither: 0,
        gamma: 1.0, tilt: 0, focusY: 0.55, focusH: 0.28, blur: 0.0 };
  }
}

// Unreal-3 style filmic tonemap, verbatim from Chocapic13 composite.fsh.
function shaderTonemapUnreal(x) {
  return x / (0.98135426889 * x + 0.154 * 0.98135426889);
}

// Gentle filmic S-curve for the MakeUp/miniature path.
function shaderTonemapSoft(x) {
  var a = x * (2.2 * x + 0.15);
  return a / (x * (2.2 * x + 0.9) + 0.15);
}

// ACES filmic approximation (Narkowicz). Clean rolloff, neutral hue shift --
// the curve Complementary Unbound's highlights read as.
function shaderTonemapAces(x) {
  var a = x * (2.51 * x + 0.03), b = x * (2.43 * x + 0.59) + 0.14;
  var v = b === 0 ? 0 : a / b;
  return v < 0 ? 0 : (v > 1 ? 1 : v);
}

// BSL's shape: extended Reinhard against a white point, then opened back up by
// the upper curve, which is what keeps its highlights bright instead of grey.
function shaderTonemapBsl(x) {
  var c = x < 0 ? 0 : x;
  c = c * (1.0 + c * 0.25) / (1.0 + c);
  return Math.pow(c < 0 ? 0 : c, 1.0 / 1.3);
}

// Uncharted2 curve and normalization used by Sildur's final.fsh.
function shaderTonemapSildur(x) {
  var A = 0.28, B = 0.29, C = 0.10, D = 0.2, E = 0.025, F = 0.35;
  return ((x * (A * x + C * B) + D * E) /
    (x * (A * x + B) + D * F)) - E / F;
}

function shaderClamp01(x) {
  return x < 0 ? 0 : (x > 1 ? 1 : x);
}

// CPU mirror of the blit-shader grade for golden tests (operates on 0..1 rgb).
function shaderGradePixel(mode, r, g, b, u, v) {
  var grade = shaderPackGrade(mode);
  var dist = Math.sqrt((u - 0.5) * (u - 0.5) + (v - 0.5) * (v - 0.5));
  var blurMix = 0;
  if (grade.tilt) {
    var band = Math.abs(v - grade.focusY) - grade.focusH * 0.5;
    blurMix = shaderClamp01(band / Math.max(0.05, grade.focusH));
  }
  r *= grade.exposure; g *= grade.exposure; b *= grade.exposure;
  if (grade.tonemap === 6) { r = shaderTonemapBsl(r); g = shaderTonemapBsl(g); b = shaderTonemapBsl(b); }
  else if (grade.tonemap === 5) { r = shaderTonemapAces(r); g = shaderTonemapAces(g); b = shaderTonemapAces(b); }
  else if (grade.tonemap === 4) {
    var white = shaderTonemapSildur(15.2);
    var gamma = Math.max(0.1, grade.gamma || 1.0);
    r = Math.pow(Math.max(0, shaderTonemapSildur(r * 4.7) / white), 1 / gamma);
    g = Math.pow(Math.max(0, shaderTonemapSildur(g * 4.7) / white), 1 / gamma);
    b = Math.pow(Math.max(0, shaderTonemapSildur(b * 4.7) / white), 1 / gamma);
  } else if (grade.tonemap === 1) { r = shaderTonemapUnreal(r); g = shaderTonemapUnreal(g); b = shaderTonemapUnreal(b); }
  else if (grade.tonemap === 2) { r = shaderTonemapSoft(r); g = shaderTonemapSoft(g); b = shaderTonemapSoft(b); }
  var luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  r = luma + (r - luma) * grade.saturation;
  g = luma + (g - luma) * grade.saturation;
  b = luma + (b - luma) * grade.saturation;
  r = (r - 0.5) * grade.contrast + 0.5;
  g = (g - 0.5) * grade.contrast + 0.5;
  b = (b - 0.5) * grade.contrast + 0.5;
  r *= grade.tint[0]; g *= grade.tint[1]; b *= grade.tint[2];
  var vig = 1.0 - grade.vignette * dist * dist * 2.0;
  r *= vig; g *= vig; b *= vig;
  return { r: r, g: g, b: b, blurMix: blurMix };
}

// Per-pack lightmap parameters. torchRGB tints the block axis, torchLift and
// torchGamma reshape its curve, skyScale/exposure scale the sky axis, floor
// lifts the darkest texels (miniature flat lighting). Strength blends toward
// vanilla; mix rule "never darker" is applied by the caller.
function shaderPackLight(mode) {
  switch (mode | 0) {
    case SHADER_PACK_MAKEUP:
      return { torchRGB: [1.0, 0.9, 0.78], torchLift: 0.06, torchGamma: 0.85,
        skyScale: 1.08, exposure: 1.1, floor: 0.012 };
    case SHADER_PACK_CHOCAPIC:
      return { torchRGB: [1.0, 0.42, 0.11], torchLift: 0.02, torchGamma: 1.0,
        skyScale: 1.0, exposure: 1.0, floor: 0.001 };
    case SHADER_PACK_MINIATURE:
      return { torchRGB: [1.0, 0.85, 0.7], torchLift: 0.10, torchGamma: 0.8,
        skyScale: 1.0, exposure: 0.9, floor: 0.05 };
    case SHADER_PACK_SILDUR:
      return { torchRGB: [1.12, 0.68, 0.28], torchLift: 0.018, torchGamma: 0.92,
        skyScale: 1.08, exposure: 1.04, floor: 0.002 };
    case SHADER_PACK_BSL:
      // BSL's blocklight is the warmest of the set and its sky axis is lifted,
      // which together give the daylight-through-a-window look it is known for.
      return { torchRGB: [1.0, 0.56, 0.24], torchLift: 0.03, torchGamma: 0.88,
        skyScale: 1.12, exposure: 1.06, floor: 0.004 };
    case SHADER_PACK_COMPLEMENTARY:
      return { torchRGB: [1.0, 0.72, 0.42], torchLift: 0.022, torchGamma: 0.9,
        skyScale: 1.06, exposure: 1.02, floor: 0.003 };
    default:
      return { torchRGB: [1.0, 1.0, 1.0], torchLift: 0.0, torchGamma: 1.0,
        skyScale: 1.0, exposure: 1.0, floor: 0.0 };
  }
}

// Pack light level 0..1 for one axis level 0..15 (Chocapic-style curves).
function shaderAxisTorch(level, params) {
  var t = level / 15;
  t = t * t;
  var v = (((t * t) * (t * t)) * (t * 20) + t * 2) * 0.11 + params.torchLift;
  return Math.pow(Math.max(0, v), params.torchGamma);
}

function shaderAxisSky(level, params) {
  var t = level / 15;
  return Math.pow(t, 2.23) * params.skyScale;
}

// Pack rgb (0..1) for texel (block, sky). Caller keeps max(vanilla, pack).
function shaderLightTexel(mode, block, sky) {
  var params = shaderPackLight(mode);
  var torch = shaderAxisTorch(block, params);
  var skyL = shaderAxisSky(sky, params);
  var r = (torch * params.torchRGB[0] + skyL) * params.exposure;
  var g = (torch * params.torchRGB[1] + skyL * 0.96) * params.exposure;
  var b = (torch * params.torchRGB[2] + skyL * 0.96) * params.exposure;
  var m = Math.max(r, Math.max(g, b));
  if (m > 1) { r /= m; g /= m; b /= m; }
  r = Math.max(r, params.floor); g = Math.max(g, params.floor); b = Math.max(b, params.floor);
  return [r, g, b];
}

// Fog color multipliers per pack (applied to computed fog rgb).
function shaderPackFog(mode) {
  switch (mode | 0) {
    case SHADER_PACK_MAKEUP: return [1.02, 1.0, 0.98];
    case SHADER_PACK_CHOCAPIC: return [1.05, 0.98, 0.9];
    case SHADER_PACK_MINIATURE: return [1.06, 1.04, 1.02];
    case SHADER_PACK_SILDUR: return [0.98, 1.0, 1.08];
    case SHADER_PACK_BSL: return [1.0, 1.0, 1.05];
    case SHADER_PACK_COMPLEMENTARY: return [1.0, 1.01, 1.02];
    default: return [1.0, 1.0, 1.0];
  }
}

if (typeof module !== "undefined") module.exports = {SHADER_PACK_OFF:SHADER_PACK_OFF,SHADER_PACK_MAKEUP:SHADER_PACK_MAKEUP,SHADER_PACK_CHOCAPIC:SHADER_PACK_CHOCAPIC,SHADER_PACK_MINIATURE:SHADER_PACK_MINIATURE,SHADER_PACK_SILDUR:SHADER_PACK_SILDUR,SHADER_PACK_BSL:SHADER_PACK_BSL,SHADER_PACK_COMPLEMENTARY:SHADER_PACK_COMPLEMENTARY,SHADER_PACK_KEY:SHADER_PACK_KEY,SHADER_PACK_LABELS:SHADER_PACK_LABELS,SHADER_PACK_SELECTOR_LABELS:SHADER_PACK_SELECTOR_LABELS,SHADER_PACK_COUNT:SHADER_PACK_COUNT,shaderPackLabel:shaderPackLabel,shaderPackSelectorLabel:shaderPackSelectorLabel,shaderPackCycle:shaderPackCycle,shaderPackGrade:shaderPackGrade,shaderTonemapUnreal:shaderTonemapUnreal,shaderTonemapSoft:shaderTonemapSoft,shaderTonemapSildur:shaderTonemapSildur,shaderTonemapAces:shaderTonemapAces,shaderTonemapBsl:shaderTonemapBsl,shaderClamp01:shaderClamp01,shaderGradePixel:shaderGradePixel,shaderPackLight:shaderPackLight,shaderAxisTorch:shaderAxisTorch,shaderAxisSky:shaderAxisSky,shaderLightTexel:shaderLightTexel,shaderPackFog:shaderPackFog};
