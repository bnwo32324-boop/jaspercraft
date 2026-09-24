/* Green water.
 *
 * Water colour is decided entirely on the client: the server sends a biome id and
 * the client multiplies the water texture by that biome's own colour. Nothing the
 * server can say changes it, which is why the water tint in biomes.tsv has never
 * been visible in game.
 *
 * The biome constructor already calls JasprBiomeStyle to apply this pack's per-biome
 * styling. This declaration appears later in the same closure than the original, so
 * it is the one that survives hoisting and the one that runs. It keeps every other
 * styled value and forces the water multiplier for every biome that is ever built,
 * whether or not the styles table has an entry for it -- so ocean, river, swamp,
 * cave pools and the Nether all come out the same green.
 */
/* The tint is a per-channel multiply against the water texture, so it can only
 * take colour away, never add it. Anything left in the blue channel comes back as
 * teal, because blue is the brightest channel the texture has -- 0x3FA64B still
 * passed three quarters of it. Green is held at full, blue is cut to almost
 * nothing, and red is kept high enough to pull the result towards the yellow side
 * of green, which is what reads as toxic rather than merely dark. */
var JASPR_WATER_TINT = 0x70FF14;

function JasprBiomeStyle(a) {
  var p = JasprBiomeStyles[$rt_ustr(a.b8d)];
  if (p) { a.b2R = p[1]; a.bPP = p[2]; a.bBD = p[3]; a.d3M = p[4]; }
  a.cOK = JASPR_WATER_TINT;
}
