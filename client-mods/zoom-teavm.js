/* JasperCraft camera zoom -- OptiFine parity pass.
 *
 * The first pass already registered a native KeyBinding on C (LWJGL 46) and
 * divided the final FOV by four, which is OptiFine's divisor. Two things were
 * still missing, and this file adds them.
 *
 * 1. OptiFine does not only narrow the FOV. While the zoom key is held it also
 *    turns the game's own cinematic-camera mouse smoothing on, and turns it off
 *    again on release. That smoothing is the whole reason OptiFine's zoom feels
 *    steady enough to aim with, and it is also the "wobble" other zoom mods
 *    advertise removing. Reproducing the zoom without it is not the same zoom.
 *
 *    Rather than writing gameSettings.smoothCamera (bIS) and restoring it --
 *    which persists to the player's options file and is how OptiFine manages to
 *    clear a cinematic-camera setting the player chose themselves -- the two
 *    places that read the flag are widened to "flag set OR zoom held". The
 *    rendering result is identical and no stored setting is ever touched.
 *
 *      C45 (EntityRenderer tick)  if(!b.bIS){...}  -- resets both MouseFilters
 *      Fj1 (updateCameraAndRender) if(!k.bIS){...} -- the mouse-look path
 *
 *    The false branch of each already calls AWf() on both MouseFilters, which is
 *    MouseFilter.reset(). That happens on the first frame after the key comes up,
 *    so OptiFine's explicit `new MouseFilter()` on release is reproduced by the
 *    native code and does not need to be inserted.
 *
 * 2. LWJGL 46 is not free. It is vanilla's key.saveToolbarActivator (Save Toolbar
 *    Activator, creative mode). Two bindings on one key is a conflict: the key
 *    hash is rebuilt last-writer-wins by Dd$(), so the zoom happened to win and
 *    the toolbar binding was left dead and shown in red under Controls.
 *
 *    The default moves to LWJGL 47 (V), keeping it next to its sibling
 *    key.loadToolbarActivator on 45 (X). Players whose saved Controls already
 *    pin 46 are migrated once, through the same native path the Controls screen
 *    uses, so the change persists and the key hash is rebuilt correctly.
 */

/* Added to the JasprZoom module state. */
var SAVE_TOOLBAR_KEY = 47, CONFLICT_DONE = false;

/* Added to the JasprZoom module's returned object. */
smoothing: function (settings, client) {
  // Native smoothing stays authoritative; the zoom only ever adds to it.
  try {
    if (settings && settings.bIS) return 1;
    return active(client) ? 1 : 0;
  } catch (e) {
    return settings && settings.bIS ? 1 : 0;
  }
},
needsConflictFix: function (client) {
  if (CONFLICT_DONE) return false;
  try {
    var settings = client && client.G;
    if (!settings) return false;
    var zoom = settings.$jasprZoomKey, save = settings.a7k;
    if (!zoom || !save) return false;
    // Nothing to do unless the two are genuinely on the same key.
    if (zoom.gO !== save.gO) { CONFLICT_DONE = true; return false; }
    return true;
  } catch (e) { CONFLICT_DONE = true; return false; }
},
conflictFixed: function () { CONFLICT_DONE = true; },
saveToolbarKey: function () { return SAVE_TOOLBAR_KEY; },

/* Called from DRw state 49, on the existing game fiber, beside the other
 * per-tick mod hooks. C$e is GameSettings.setOptionKeyBinding (writes gO then
 * saves options) and Dd$ is KeyBinding.resetKeyBindingArrayAndHash; both may
 * suspend, so each gets its own saved state.
 */
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
