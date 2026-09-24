'use strict';
/* Adds OptiFine-parity camera zoom to the deployed TeaVM client.
 *
 * Writes only candidate/zoom-client/. Pins the exact pre-extension SHA256,
 * asserts every anchor count, and verifies that unpatch() restores the input
 * byte for byte before the candidate is considered valid.
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');

const ROOT = path.join(__dirname, '..');
const SOURCE = path.join(ROOT, 'site', 'classes.js');

// The shipped build this extension is audited against, plus its own output so a
// re-run is idempotent rather than a second application.
const BASE = 'e62546c51fb5939580bacc8b6f3ca0ab53701d2f53c2166c0666eda30a4ba054';
const CURRENT_BASE = '';
const sha = s => crypto.createHash('sha256').update(s).digest('hex');
const acceptedBase = h => h === BASE || (CURRENT_BASE && h === CURRENT_BASE);

/* [anchor, replacement, expectedCount] -- counts are asserted both ways. */
const MODULE_STATE = [
  '  var FACTOR = 4.0;\n  var LAST_INPUT = null, LAST_OUTPUT = null, CALLS = 0;',
  '  var FACTOR = 4.0;\n  var SAVE_TOOLBAR_KEY = 47, CONFLICT_DONE = false;\n  var LAST_INPUT = null, LAST_OUTPUT = null, CALLS = 0;',
  1,
];

const MODULE_API = [
  '  return {\n    active: active,\n    fov: function (value, renderer) {',
  `  return {
    active: active,
    smoothing: function (settings, client) {
      try {
        if (settings && settings.bIS) return 1;
        return active(client) ? 1 : 0;
      } catch (e) { return settings && settings.bIS ? 1 : 0; }
    },
    needsConflictFix: function (client) {
      if (CONFLICT_DONE) return false;
      try {
        var settings = client && client.G;
        if (!settings) return false;
        var zoom = settings.$jasprZoomKey, save = settings.a7k;
        if (!zoom || !save) return false;
        if (zoom.gO !== save.gO) { CONFLICT_DONE = true; return false; }
        return true;
      } catch (e) { CONFLICT_DONE = true; return false; }
    },
    conflictFixed: function () { CONFLICT_DONE = true; },
    saveToolbarKey: function () { return SAVE_TOOLBAR_KEY; },
    fov: function (value, renderer) {`,
  1,
];

/* Cinematic-camera smoothing is widened to "native flag OR zoom held". */
const SMOOTH_TICK = ['if(!b.bIS){a.bVQ=0.0;', 'if(!JasprZoom.smoothing(b,a.bu)){a.bVQ=0.0;', 2];
const SMOOTH_LOOK = ['if(!k.bIS){a.bmL=0.0;', 'if(!JasprZoom.smoothing(k,a.bu)){a.bmL=0.0;', 1];

/* key.saveToolbarActivator default: LWJGL 46 (C) -> 47 (V). */
const TOOLBAR_DEFAULT = [
  'a.Lv=c;d=new GO;g=C(6295);f=46;e=C(6296);',
  'a.Lv=c;d=new GO;g=C(6295);f=47;e=C(6296);',
  1,
];

/* One per-tick hook, beside the existing ones, on the same game fiber. */
const TICK_HOOK = [
  'JasprWaypointTabTick(a);if(B()){break _;}b=a.G.ckx;',
  'JasprWaypointTabTick(a);if(B()){break _;}JasprZoomTick(a);if(B()){break _;}b=a.G.ckx;',
  1,
];

const TICK_FUNCTION = `
// Called from DRw state 49 only, on the existing game fiber. C$e is
// GameSettings.setOptionKeyBinding and Dd$ is KeyBinding.resetKeyBindingArrayAndHash;
// both may suspend, so each gets its own saved state.
function JasprZoomTick(a) {
  var b,c,$p=0;
  if (FX()) { var $T=Ds(); $p=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (!JasprZoom.needsConflictFix(a)) return;
      b=a.G; c=b.a7k; $p=1;
    case 1:
      C$e(b,c,JasprZoom.saveToolbarKey()); if (B()) break _;
      $p=2;
    case 2:
      Dd$(); if (B()) break _;
      JasprZoom.conflictFixed();
      return;
    default: FT();
  } }
  Ds().s(a,b,c,$p);
}
`;

const TICK_DEFINITION = [
  '  } catch (e) { /* diagnostics are optional */ }\n}\n\nvar JasprStatsKeyDescription',
  '  } catch (e) { /* diagnostics are optional */ }\n}\n' + TICK_FUNCTION + '\nvar JasprStatsKeyDescription',
  1,
];

const EDITS = [MODULE_STATE, MODULE_API, SMOOTH_TICK, SMOOTH_LOOK, TOOLBAR_DEFAULT, TICK_HOOK, TICK_DEFINITION];

function count(haystack, needle) {
  let n = 0, i = -1;
  while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++;
  return n;
}

function replaceAll(text, from, to, expected, label) {
  const found = count(text, from);
  if (found !== expected) throw new Error(label + ': expected ' + expected + ' anchor(s), got ' + found);
  const out = text.split(from).join(to);
  // Every anchor is chosen so applying the edit consumes it. If the anchor
  // survives, the edit is not idempotent and a second run would double-apply.
  const left = count(out, from);
  if (left !== 0) throw new Error(label + ': anchor still present after edit (' + left + ') -- not consuming');
  return out;
}

function apply(text, edits, reverse) {
  let out = text;
  edits.forEach(([from, to, n], i) => {
    const label = 'edit#' + i;
    out = reverse ? replaceAll(out, to, from, n, label + ' (reverse)')
                  : replaceAll(out, from, to, n, label);
  });
  return out;
}

function build() {
  const input = fs.readFileSync(SOURCE, 'utf8');
  const inputSHA = sha(input);
  if (!acceptedBase(inputSHA)) {
    throw new Error('Refusing to patch an unpinned client.\n  expected ' + BASE + '\n  found    ' + inputSHA);
  }
  const result = apply(input, EDITS, false);

  // Byte-for-byte reversal is the assertion that the edits are surgical.
  const restored = apply(result, EDITS, true);
  if (restored !== input) throw new Error('unpatch() did not restore the input byte for byte');

  // The candidate must still parse.
  new (require('node:vm').Script)(result, {filename: 'classes.js'});

  const dir = path.join(ROOT, 'candidate', 'zoom-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), result);
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify({
    stage: 'optifine-parity-zoom-v1',
    baseSHA256: inputSHA,
    sha256: sha(result),
    edits: EDITS.length,
    zoomKeyCode: 46,
    zoomKeyName: 'C',
    fovDivisor: 4.0,
    smoothCameraWhileZoomed: true,
    saveToolbarActivatorMovedTo: 47,
    saveToolbarActivatorKeyName: 'V',
  }, null, 2) + '\n');
  return {dir, sha256: sha(result), bytes: Buffer.byteLength(result)};
}

/* Reverses this extension, for when the underlying TeaVM client is rebuilt. */
function unpatch(file) {
  const patched = fs.readFileSync(file || SOURCE, 'utf8');
  return apply(patched, EDITS, true);
}

if (require.main === module) {
  if (process.argv.includes('--unpatch')) {
    const dir = path.join(ROOT, 'candidate', 'zoom-client');
    fs.mkdirSync(dir, {recursive: true});
    const restored = unpatch();
    fs.writeFileSync(path.join(dir, 'classes.unpatched.js'), restored);
    console.log('Reversed to ' + path.join(dir, 'classes.unpatched.js'));
    console.log('SHA256 ' + sha(restored));
    process.exit(0);
  }
  const out = build();
  console.log('Candidate only: ' + path.join(out.dir, 'classes.js'));
  console.log('SHA256 ' + out.sha256);
  console.log('bytes  ' + out.bytes);
}
module.exports = {build, apply, EDITS, sha, BASE};
