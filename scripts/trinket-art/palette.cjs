'use strict';
/* Named colour ramps shared by every trinket recipe, so icons from different realms still read as one set. */
const {ramp, flat} = require('./engine.cjs');

const C = {
  // metals
  gold: ramp(0xdcab2e), brass: ramp(0xb98e32), bronze: ramp(0xa8683a), copper: ramp(0xc7743c), rust: ramp(0x985a32),
  silver: ramp(0xbcc4d0), steel: ramp(0x8c9db2), iron: ramp(0x7d8793), darkIron: ramp(0x484e58), obsidian: ramp(0x2b2339),
  // organic
  bone: ramp(0xe8dec4), cream: ramp(0xf2e8cb), salt: ramp(0xe6eef3), ash: ramp(0x9b9895), charcoal: ramp(0x38343e), soot: ramp(0x23202a),
  leather: ramp(0x8c5b36), darkLeather: ramp(0x573520), wood: ramp(0x9d6c3e), darkWood: ramp(0x5c3d25), straw: ramp(0xd8bf6a),
  parchment: ramp(0xdfcb9c), paper: ramp(0xeee4c8), reed: ramp(0xa9a85a),
  // cloth and wax
  velvet: ramp(0x8c1f33), cloth: ramp(0xbdb9ae), linen: ramp(0xe9e2d0), waxRed: ramp(0xab312d), waxWhite: ramp(0xe6dfcd), waxBlue: ramp(0x365f9c),
  waxBlack: ramp(0x2d2a34),
  // gems and glass
  ruby: ramp(0xcb2540), emerald: ramp(0x2fa56b), sapphire: ramp(0x3066cc), amethyst: ramp(0x8751c9), amber: ramp(0xe89b1b), aqua: ramp(0x3fb8b2),
  ice: ramp(0xaadcf2), pearl: ramp(0xebe6f0), glass: ramp(0x8fd2d6), water: ramp(0x3f8ee0), violetPearl: ramp(0x6a5c86), rose: ramp(0xe08aa6),
  // living and burning things
  leaf: ramp(0x5fa03d), moss: ramp(0x486f35), spore: ramp(0xa9d97c), fungus: ramp(0x6fbf9a), root: ramp(0x7a5230),
  flame: ramp(0xf08c1f), ember: ramp(0xdc5120), coal: ramp(0x2f2a2e), lava: ramp(0xff7a1c), blood: ramp(0x9b1c26), tallow: ramp(0xefe3a8),
  // flat colours for glints, marks and glowing cracks
  white: flat(0xffffff), black: flat(0x16121f), glow: flat(0xffb13a), hot: flat(0xffe58a), spark: flat(0xfff7c8), crack: flat(0xff8a22),
  inkBlue: flat(0x1d2a5c), markDark: flat(0x3b2a22), redMark: flat(0x7d1620), blueMark: flat(0x23477e), greenMark: flat(0x2c7a4a)
};
module.exports = C;
