'use strict';
/* Every trinket, bauble, charm and seal that carries its own icon, with the carrier and band the servers give it.
 *
 * A carrier is an unbreakable stone tool whose damage value selects the model (the same technique as the survivor gear's stone
 * hoe, the sentry turrets' iron pickaxe and the armoury's diamond tools): stone tools cannot be smelted or burned, and the carrier
 * models below belong to nobody else. A band is the damage value; the vanilla look stays for every other value.
 *   stone_sword  : the Dungeon Dimension (bands 1-73: the 72 classic baubles in dungeon.cjs order, then the pouch; generation 7:
 *                  74-76 the Floor Guardians' trophy weapons, 77-130 its first baubles)
 *   stone_shovel : Drownhollow (1-15), the Nether (21-32), the Backrooms (41-55), the Dungeon Dimension's generation 7 (66-123)
 * A band, once given, never moves: generation 7's are an explicit table (DUNGEON7_BANDS), not positions.
 * The plugins hard-code the same numbers (Skin.java in each); tests/trinket-art.test.cjs keeps both sides identical.
 */
const dungeon = require('./dungeon.cjs');
const ruins = require('./ruins.cjs');
const nether = require('./nether.cjs');
const backrooms = require('./backrooms.cjs');
const dungeon7 = require('./dungeon7.cjs');

/** Max durability of each carrier (stone tier). */
const CARRIERS = {stone_sword: 131, stone_shovel: 131};

/** JasprRuins: bands follow the Trinket enum position + 1 (the worn Faceless Mask = 8 and Crown = 10 keep their vanilla items); seals follow Seal position + 11. */
const RUINS_BANDS = {WARDSTONE: 1, TIDE_PEARL: 2, TENTACLE_CHARM: 3, STAR_SHARD: 4, NIGHTGAUNT_PINION: 5, GHOUL_TOOTH: 6, MIGO_CYLINDER: 7, IDOL_OF_THE_DREAMER: 9,
  SEAL_TIDES: 11, SEAL_STONE: 12, SEAL_HUNGER: 13, SEAL_DEEP: 14, SEAL_SILENCE: 15};
/** JasprNether: 21 + position among the carried trinkets. */
const NETHER_BANDS = {BRIMSTONE_IDOL: 21, HEART_OF_CINDERS: 22, HELLHOUND_COLLAR: 23, EMBER_HEART: 24, WITHER_WARD: 25, MAGMA_BAND: 26, GHASTLY_PENDANT: 27, TYRANT_HEART: 28,
  SCARAB_AMULET: 29, PHOENIX_FEATHER: 30, SPORE_HEART: 31, ORACLE_PRISM: 32};
/** JasprBackrooms: 41 + position among its trinkets. */
const BACKROOMS_BANDS = {CANTEEN: 41, FLICKERING_BULB: 42, FORKLIFT_KEY: 43, PACKING_TAPE: 44, PRESSURE_GAUGE: 45, COOLANT_VIAL: 46, SURGE_PROTECTOR: 47, CAPACITOR: 48,
  EMPLOYEE_BADGE: 49, COLD_COFFEE: 50, DEAD_MANS_WATCH: 51, SUBWAY_TOKEN: 52, RUBBER_DUCK: 53, WHISTLE: 54, EXIT_SIGN: 55};

/**
 * The Dungeon Dimension's generation 7 items (Relics.Type and TrophyCatalog.Trophy names): carrier and band, written out once and
 * never renumbered. Free for others afterwards: stone_sword none, stone_shovel 16-20, 33-40, 56-65 and 124-130.
 */
const DUNGEON7_BANDS = {
  MERCYS_LAST_KEY: ['stone_sword', 74], DEEPBREAKER: ['stone_sword', 75], ABYSSAL_SCEPTRE: ['stone_sword', 76], FOUNDERS_FORK: ['stone_sword', 77],
  BRONZE_TOLL: ['stone_sword', 78], MOTH_COCOON: ['stone_sword', 79], WINGDUST_PHIAL: ['stone_sword', 80], VOTIVE_STUB: ['stone_sword', 81],
  WRIGHTS_TAPER: ['stone_sword', 82], LIBRARIANS_CHAIN: ['stone_sword', 83], MARGIN_NOTE: ['stone_sword', 84], BATH_SPONGE: ['stone_sword', 85],
  PENITENT_PUMICE: ['stone_sword', 86], STONE_LIKENESS: ['stone_sword', 87], EFFIGY_WAX: ['stone_sword', 88], MARKET_LEDGER: ['stone_sword', 89],
  MOURNERS_OBOL: ['stone_sword', 90], WEEPING_BOUGH: ['stone_sword', 91], BITTER_FRUIT: ['stone_sword', 92], CASKET_NAIL: ['stone_sword', 93],
  CORRODED_SIGIL: ['stone_sword', 94], LANTERN_GLASS: ['stone_sword', 95], LAMPLIGHTERS_HOOK: ['stone_sword', 96], SALT_CELLAR: ['stone_sword', 97],
  COOKS_LADLE: ['stone_sword', 98], MIMES_GLOVE: ['stone_sword', 99], CURTAIN_CORD: ['stone_sword', 100], GUTTER_RAG: ['stone_sword', 101],
  RAT_KING_KNOT: ['stone_sword', 102], INCENSE_CONE: ['stone_sword', 103], SWINGING_THURIBLE: ['stone_sword', 104], GARDEN_BLOOM: ['stone_sword', 105],
  GARDENERS_TWINE: ['stone_sword', 106], HOSTEL_BLANKET: ['stone_sword', 107], PILGRIMS_TOKEN: ['stone_sword', 108], SEXTONS_MEASURE: ['stone_sword', 109],
  BURIAL_SHROUD: ['stone_sword', 110], UNLIT_WICK: ['stone_sword', 111], NAVE_VEIL: ['stone_sword', 112], MAGMA_GIZZARD: ['stone_sword', 113],
  BASALT_HEART: ['stone_sword', 114], STALACTITE_TOOTH: ['stone_sword', 115], CAVERN_ECHO: ['stone_sword', 116], GROTTO_CAP: ['stone_sword', 117],
  SPOREBURST_SAC: ['stone_sword', 118], PUMP_VALVE: ['stone_sword', 119], FLOODED_LANTERN: ['stone_sword', 120], RESONANT_CRYSTAL: ['stone_sword', 121],
  CRYSTAL_LATTICE: ['stone_sword', 122], CHITIN_PLATE: ['stone_sword', 123], VENOM_GLAND: ['stone_sword', 124], MARROW_FLUTE: ['stone_sword', 125],
  BONE_DICE: ['stone_sword', 126], SLING_STONE: ['stone_sword', 127], QUENCH_STONE: ['stone_sword', 128], SPRAY_VEIL: ['stone_sword', 129],
  LABYRINTH_CHALK: ['stone_sword', 130], BLAST_DAMPER: ['stone_shovel', 66], FUSE_SNIPS: ['stone_shovel', 67], SWITCHMANS_FLAG: ['stone_shovel', 68],
  BRAKE_LEVER: ['stone_shovel', 69], SULFUR_SALVE: ['stone_shovel', 70], SPRING_FLASK: ['stone_shovel', 71], QUARRY_WEDGE: ['stone_shovel', 72],
  WORM_LURE: ['stone_shovel', 73], FORGE_TEMPER: ['stone_shovel', 74], RIVERBED_PEBBLE: ['stone_shovel', 75], RUST_EATER_TOOTH: ['stone_shovel', 76],
  GEODE_HEART: ['stone_shovel', 77], ROOTDRINKER: ['stone_shovel', 78], SMUGGLERS_SHIV: ['stone_shovel', 79], CONTRABAND_PLATE: ['stone_shovel', 80],
  COLUMN_CAPITAL: ['stone_shovel', 81], BURROW_EMBER: ['stone_shovel', 82], SMOLDERING_ZEAL: ['stone_shovel', 83], TYRANTS_TALLY: ['stone_shovel', 84],
  FOREMANS_BELL: ['stone_shovel', 85], SENTINEL_RIVET: ['stone_shovel', 86], COURTIERS_CLOAK: ['stone_shovel', 87], KNEELERS_CUSHION: ['stone_shovel', 88],
  SANGUINE_MERLON: ['stone_shovel', 89], RAMPART_STONE: ['stone_shovel', 90], VOID_THORN: ['stone_shovel', 91], GRAVITY_SEED: ['stone_shovel', 92],
  OBSIDIAN_SPLINTER: ['stone_shovel', 93], SOULFIRE_WICK: ['stone_shovel', 94], SOULFIRE_CENSER: ['stone_shovel', 95], PENANCE_CHAIN: ['stone_shovel', 96],
  SHACKLE_LINK: ['stone_shovel', 97], SOUL_EMBER: ['stone_shovel', 98], SKYFALL_TALON: ['stone_shovel', 99], CROWDS_ROAR: ['stone_shovel', 100],
  GLADIATORS_TORC: ['stone_shovel', 101], HEADSMANS_LEDGER: ['stone_shovel', 102], FERRYMANS_LANTERN: ['stone_shovel', 103], PEARL_INDEX: ['stone_shovel', 104],
  SERGEANTS_WHISTLE: ['stone_shovel', 105], MUSTER_ROLL: ['stone_shovel', 106], FOUNDRY_SIGHTS: ['stone_shovel', 107], DOOM_RIVET: ['stone_shovel', 108],
  SILVERED_RETORT: ['stone_shovel', 109], UNMARRED_REFLECTION: ['stone_shovel', 110], ASHEN_CROWN_SHARD: ['stone_shovel', 111], THRONE_ASH: ['stone_shovel', 112],
  GATEBREAKER_SIGIL: ['stone_shovel', 113], ABYSSAL_KEYSTONE: ['stone_shovel', 114], PYRE_URN: ['stone_shovel', 115], STAR_IRON_LENS: ['stone_shovel', 116],
  STARFALL_SHARD: ['stone_shovel', 117], GRAVE_WIND_SHROUD: ['stone_shovel', 118], MOLTEN_CORE: ['stone_shovel', 119], CURSED_COIN: ['stone_shovel', 120],
  BASTION_STANDARD: ['stone_shovel', 121], BASTION_HORN: ['stone_shovel', 122], VICTORS_LAUREL: ['stone_shovel', 123]
};

const ENTRIES = [];
function add(plugin, id, carrier, band, art) {
  ENTRIES.push({plugin, id, carrier, band, key: plugin + '_' + id.toLowerCase(), icon: () => art.draw(id)});
}
Object.keys(dungeon.ICONS).forEach((id, n) => add('dungeon', id, 'stone_sword', n + 1, dungeon));
add('dungeon', 'POUCH', 'stone_sword', Object.keys(dungeon.ICONS).length + 1, dungeon);
for (const [id, [carrier, band]] of Object.entries(DUNGEON7_BANDS)) add('dungeon', id, carrier, band, dungeon7);
for (const [id, band] of Object.entries(RUINS_BANDS)) add('ruins', id, 'stone_shovel', band, ruins);
for (const [id, band] of Object.entries(NETHER_BANDS)) add('nether', id, 'stone_shovel', band, nether);
for (const [id, band] of Object.entries(BACKROOMS_BANDS)) add('backrooms', id, 'stone_shovel', band, backrooms);

// The art tables and the band tables must name exactly the same items.
for (const [plugin, art, bands] of [['dungeon7', dungeon7, DUNGEON7_BANDS], ['ruins', ruins, RUINS_BANDS], ['nether', nether, NETHER_BANDS], ['backrooms', backrooms, BACKROOMS_BANDS]]) {
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
module.exports = {CARRIERS, ENTRIES, RUINS_BANDS, NETHER_BANDS, BACKROOMS_BANDS, DUNGEON7_BANDS};
