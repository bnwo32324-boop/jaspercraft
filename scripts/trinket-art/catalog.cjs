'use strict';
/* Every trinket, bauble, charm and seal that carries its own icon, with the carrier and band the servers give it.
 *
 * A carrier is an unbreakable stone tool whose damage value selects the model (the same technique as the survivor gear's stone
 * hoe, the sentry turrets' iron pickaxe and the armoury's diamond tools): stone tools cannot be smelted or burned, and the carrier
 * models below belong to nobody else. A band is the damage value; the vanilla look stays for every other value.
 *   stone_sword  : the Dungeon Dimension (bands 1-73)
 *   stone_shovel : Drownhollow (1-15), the Nether (21-30), the Backrooms (41-55)
 * The plugins hard-code the same numbers (Skin.java in each); tests/trinket-art.test.cjs keeps both sides identical.
 */
const dungeon = require('./dungeon.cjs');
const ruins = require('./ruins.cjs');
const nether = require('./nether.cjs');
const backrooms = require('./backrooms.cjs');

/** Max durability of each carrier (stone tier). */
const CARRIERS = {stone_sword: 131, stone_shovel: 131};

/** JasprRuins: bands follow the Trinket enum position + 1 (the worn Faceless Mask = 8 and Crown = 10 keep their vanilla items); seals follow Seal position + 11. */
const RUINS_BANDS = {WARDSTONE: 1, TIDE_PEARL: 2, TENTACLE_CHARM: 3, STAR_SHARD: 4, NIGHTGAUNT_PINION: 5, GHOUL_TOOTH: 6, MIGO_CYLINDER: 7, IDOL_OF_THE_DREAMER: 9,
  SEAL_TIDES: 11, SEAL_STONE: 12, SEAL_HUNGER: 13, SEAL_DEEP: 14, SEAL_SILENCE: 15};
/** JasprNether: 21 + position among the carried trinkets. */
const NETHER_BANDS = {BRIMSTONE_IDOL: 21, HEART_OF_CINDERS: 22, HELLHOUND_COLLAR: 23, EMBER_HEART: 24, WITHER_WARD: 25, MAGMA_BAND: 26, GHASTLY_PENDANT: 27, TYRANT_HEART: 28,
  SCARAB_AMULET: 29, PHOENIX_FEATHER: 30};
/** JasprBackrooms: 41 + position among its trinkets. */
const BACKROOMS_BANDS = {CANTEEN: 41, FLICKERING_BULB: 42, FORKLIFT_KEY: 43, PACKING_TAPE: 44, PRESSURE_GAUGE: 45, COOLANT_VIAL: 46, SURGE_PROTECTOR: 47, CAPACITOR: 48,
  EMPLOYEE_BADGE: 49, COLD_COFFEE: 50, DEAD_MANS_WATCH: 51, SUBWAY_TOKEN: 52, RUBBER_DUCK: 53, WHISTLE: 54, EXIT_SIGN: 55};

const ENTRIES = [];
function add(plugin, id, carrier, band, art) {
  ENTRIES.push({plugin, id, carrier, band, key: plugin + '_' + id.toLowerCase(), icon: () => art.draw(id)});
}
Object.keys(dungeon.ICONS).forEach((id, n) => add('dungeon', id, 'stone_sword', n + 1, dungeon));
add('dungeon', 'POUCH', 'stone_sword', Object.keys(dungeon.ICONS).length + 1, dungeon);
for (const [id, band] of Object.entries(RUINS_BANDS)) add('ruins', id, 'stone_shovel', band, ruins);
for (const [id, band] of Object.entries(NETHER_BANDS)) add('nether', id, 'stone_shovel', band, nether);
for (const [id, band] of Object.entries(BACKROOMS_BANDS)) add('backrooms', id, 'stone_shovel', band, backrooms);

// The art tables and the band tables must name exactly the same items.
for (const [plugin, art, bands] of [['ruins', ruins, RUINS_BANDS], ['nether', nether, NETHER_BANDS], ['backrooms', backrooms, BACKROOMS_BANDS]]) {
  const a = Object.keys(art.ICONS).sort().join(','), b = Object.keys(bands).sort().join(',');
  if (a !== b) throw new Error(plugin + ': art and band tables differ: ' + a + ' vs ' + b);
}
const seen = new Map();
for (const e of ENTRIES) {
  const slot = e.carrier + ':' + e.band;
  if (seen.has(slot)) throw new Error('band ' + slot + ' used by ' + seen.get(slot) + ' and ' + e.key);
  if (!(e.band > 0 && e.band < CARRIERS[e.carrier])) throw new Error('band out of range: ' + e.key);
  seen.set(slot, e.key);
}
module.exports = {CARRIERS, ENTRIES, RUINS_BANDS, NETHER_BANDS, BACKROOMS_BANDS};
