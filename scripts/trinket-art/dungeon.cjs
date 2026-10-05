'use strict';
/* Icons for the Dungeon Dimension's 72 baubles and its Reliquary pouch, keyed by the Relics.Type name, in the enum's own order
 * (the band of each icon on the stone-sword carrier is its position here plus one; tests/trinket-art.test.cjs keeps this order
 * identical to LootCatalog.Bauble and Relics.Type). Every bauble gets a silhouette of its own: what it is, not what it replaced.
 */
const {M, Icon, ramp, flat} = require('./engine.cjs');
const C = require('./palette.cjs');
const P = M.poly, D = M.disc, R = M.rect, E = M.ellipse, L = M.line, PA = M.path, RG = M.ring, ER = M.ering, U = M.U, S = M.sub, AR = M.arc, PX = M.px, I = M.inter;
const PI = Math.PI;
const make = draw => { const i = new Icon(); draw(i); return i; };

// ----------------------------------------------------------------------------------------------------- shared shapes
const heart = (cx, cy, s = 1) => U(D(cx - 2.3 * s, cy - 1.6 * s, 3.5 * s), D(cx + 2.3 * s, cy - 1.6 * s, 3.5 * s), P([[cx - 5.6 * s, cy - .9 * s], [cx + 5.6 * s, cy - .9 * s], [cx, cy + 6 * s]]));
/** A gear: a disc with round teeth, a hole, optionally a cross-shaped hole. */
function gearMask(cx, cy, r, teeth, rt, hole) {
  const parts = [D(cx, cy, r)];
  for (let k = 0; k < teeth; k++) { const a = k * 2 * PI / teeth; parts.push(D(cx + rt * Math.cos(a), cy + rt * Math.sin(a), 1.25)); }
  return S(U(...parts), D(cx, cy, hole));
}
/** A scalloped wax seal with two ribbon tails. */
function seal(i, wax, mark, ribbon) {
  if (ribbon) { i.paint(P([[5.4, 9], [3.4, 14.6], [5.8, 13], [7.2, 14.8], [8.2, 9]]), ribbon); i.paint(P([[7.8, 9], [8.8, 14.8], [10.2, 13], [12.6, 14.6], [10.6, 9]]), ribbon); }
  const scallop = U(D(8, 6.4, 3.7), ...[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(k => D(8 + 3.9 * Math.cos(k * PI / 5), 6.4 + 3.9 * Math.sin(k * PI / 5), 1.1)));
  i.paint(scallop, wax, {spec: [1, 1]});
  i.mark(RG(8, 6.4, 3.3, 2.9), wax.lo);
  i.mark(mark, wax.dark);
}
const ring = (cx, cy, r, w = 1.4) => ER(cx, cy, r, r, w);

// ----------------------------------------------------------------------------------------------------- the baubles
const ICONS = {
  SALT_TEAR: i => {
    i.paint(P([[8, 1.5], [10.4, 5.2], [12.2, 8.6], [11.8, 11.6], [9.9, 13.7], [8, 14.3], [6.1, 13.7], [4.2, 11.6], [3.8, 8.6], [5.6, 5.2]]), C.salt, {spec: [2, 5]});
    i.mark(L(8, 5, 8, 11.5, 1), 0xbfd0de); i.dots([[6, 10], [10, 9]], 0xffffff);
  },
  MARROW_BEAD: i => {
    i.paint(AR(8, 5.4, 3.2, PI, 2 * PI, 1.3), C.leather);
    i.paint(D(8, 9.4, 4.6), C.bone, {spec: [1, 1]});
    i.paint(D(8, 9.4, 1.7), C.rose, {shade: 'flat'});
  },
  PILGRIM_KNOT: i => {
    i.paint(PA([[6.4, 9], [4.4, 14.4]], 1.8), C.straw); i.paint(PA([[9.6, 9], [11.6, 14.4]], 1.8), C.straw);
    i.paint(U(D(8, 6.6, 4.2), D(5, 5, 2), D(11, 5.2, 2)), C.straw, {grain: 'x', spec: [2, 1]});
    i.mark(U(L(5, 4.4, 11, 8.6, .8), L(6, 8.8, 11, 4.4, .8)), 0x7a5d22);
  },
  CINDER_HEART: i => {
    i.paint(heart(8, 7.5), C.coal, {spec: [2, 1]});
    i.mark(PA([[8, 3.8], [6.8, 7], [9, 9], [8, 12.4]], 1), 0xff7a1c); i.dots([[4, 5], [12, 6], [9, 7]], 0xffb13a);
  },
  HOLLOW_LENS: i => {
    i.paint(RG(8, 7.4, 5.2, 3.5), C.silver);
    i.paint(D(8, 7.4, 3.5), C.glass, {spec: [1, 1]}); i.dots([[6, 5], [7, 4.4]], 0xffffff);
    i.paint(PA([[11.4, 11.4], [13, 13.2], [12.2, 14.6]], 1), C.silver);
  },
  RUSTED_HALO: i => {
    i.paint(ER(8, 8, 6.4, 3.5, 1.9), C.rust, {grain: 'dust'});
    i.dots([[4, 8], [12, 9], [8, 10], [6, 6]], 0xd57a30); i.dots([[8, 2], [8, 3]], 0xfff3b8);
  },
  BLOOD_THREAD: i => {
    i.paint(R(3.5, 2.5, 9, 2), C.wood); i.paint(R(5, 4.5, 6, 7), C.blood, {grain: 'h'}); i.paint(R(3.5, 11.5, 9, 2), C.wood);
    i.paint(PA([[10.6, 11.6], [13.4, 13.2], [12.4, 14.8]], .9), C.blood);
  },
  LAMB_BELL: i => {
    i.paint(P([[8, 2.2], [10.6, 3.6], [11.6, 7], [12.4, 10], [14, 11.4], [14, 12.6], [2, 12.6], [2, 11.4], [3.6, 10], [4.4, 7], [5.4, 3.6]]), C.gold, {spec: [3, 3]});
    i.paint(D(8, 13.8, 1.3), C.bronze); i.paint(ER(8, 1.9, 1.6, 1.4, .8), C.silver);
  },
  MOURNING_PEARL: i => {
    i.paint(D(8, 8, 6), C.waxBlack); i.paint(D(8, 8, 4.4), C.violetPearl, {spec: [1, 1]}); i.dots([[8, 1.6]], 0xd8d3e0);
  },
  CONFESSOR_SEAL: i => seal(i, C.waxWhite, U(R(7.4, 3.6, 1.3, 5.6), R(5.6, 5.2, 4.9, 1.3)), C.velvet),
  WARDEN_EYE: i => {
    i.paint(P([[1, 8], [3.5, 5], [8, 3.4], [12.5, 5], [15, 8], [12.5, 11], [8, 12.6], [3.5, 11]]), C.bone);
    i.paint(D(8, 8, 3.2), C.emerald, {spec: [1, 1]}); i.paint(D(8, 8, 1.3), C.black); i.dots([[6, 6]], 0xffffff);
  },
  CROWN_OF_MERCY: i => {
    i.paint(P([[2.5, 12.6], [2.5, 4.6], [5.6, 8], [8, 3], [10.4, 8], [13.5, 4.6], [13.5, 12.6]]), C.gold, {spec: [3, 3]});
    i.paint(R(2.5, 11, 11, 2.2), C.brass); i.paint(D(8, 9.2, 1.4), C.pearl); i.dots([[2.5, 4], [13.5, 4], [8, 2]], 0xfff3b8);
  },
  EMBER_VIAL: i => {
    i.paint(R(6.8, 1.4, 2.4, 2), C.wood); i.paint(R(7, 3.4, 2, 2.6), C.glass);
    i.paint(E(8, 10, 4.5, 4.3), C.glass, {spec: [2, 2]}); i.paint(E(8, 11, 3.4, 3), C.ember); i.paint(D(8, 10.6, 1.4), C.flame);
    i.dots([[8, 10]], 0xffe58a);
  },
  ASHEN_BOOKMARK: i => {
    i.paint(P([[5, 1.5], [11, 1.5], [11, 14.5], [8, 11.8], [5, 14.5]]), C.ash, {grain: 'dust'});
    i.mark(U(R(7.5, 3.2, 1, 3), R(6.5, 4.2, 3, 1)), 0x4a4744); i.dots([[7, 8], [9, 9]], 0x5b5855);
  },
  SCRIBE_QUILL: i => {
    i.paint(P([[13.6, 1.4], [10.4, 2.4], [7, 5.4], [5, 9], [4.6, 11.6], [6.6, 10.6], [10, 8.2], [12.6, 4.8]]), C.cream, {spec: [3, 1]});
    i.paint(L(13, 2, 4.6, 12.4, .9), C.leather); i.paint(L(4.6, 12, 2.8, 14.6, 1.3), C.darkIron); i.dots([[2, 14.4]], 0x16121f);
  },
  RUBY_BROOCH: i => {
    i.paint(L(2, 14, 14, 2.4, 1), C.silver);
    i.paint(D(8, 8.2, 5.4), C.gold, {spec: [1, 1]}); i.paint(P([[8, 4.2], [11.8, 8.2], [8, 12.2], [4.2, 8.2]]), C.ruby, {spec: [2, 1]});
  },
  RIME_NEEDLE: i => {
    i.paint(L(12.4, 2.8, 3.4, 13.2, 1.3), C.ice); i.paint(ER(12.6, 2.8, 1.7, 1.7, .7), C.ice);
    i.dots([[3, 3], [13, 12], [6, 5], [10, 12]], 0xffffff);
  },
  THAWED_LOCKET: i => {
    i.paint(ER(8, 2.8, 1.8, 2.2, .9), C.silver);
    i.paint(E(8, 9, 4.6, 5.3), C.gold, {spec: [2, 2]});
    i.paint(P([[8, 6], [9.7, 8.8], [9.3, 10.8], [8, 11.6], [6.7, 10.8], [6.3, 8.8]]), C.water);
  },
  ROOT_HEART: i => {
    i.paint(PA([[6.8, 12.4], [5.2, 14.4]], 1), C.root); i.paint(PA([[9.2, 12.4], [10.8, 14.4]], 1), C.root);
    i.paint(heart(8, 8.4, .92), ramp(0x8a3a30), {grain: 'x', spec: [2, 1]}); i.mark(PA([[5, 7], [7, 9], [6.6, 11]], .8), 0x5a2220);
    i.paint(L(8, 2.4, 8, 4.6, 1), C.leaf); i.paint(U(E(6.4, 2.6, 1.7, 1), E(9.6, 2.2, 1.7, 1)), C.leaf);
  },
  GRAVE_SEED: i => {
    i.paint(E(8, 8, 3.7, 5.5), C.soot, {spec: [1, 1]});
    i.mark(U(L(8, 4, 8, 12, 1), L(6.2, 7, 9.8, 7, 1)), 0xd8d0b8);
  },
  BRASS_ESCAPEMENT: i => {
    i.paint(gearMask(8, 9, 4, 8, 5.1, 1.5), C.brass, {spec: [2, 1]});
    i.paint(P([[5.4, 1.6], [10.6, 1.6], [9.4, 4], [6.6, 4]]), C.darkIron);
  },
  PENANCE_COG: i => {
    i.paint(S(gearMask(8, 8, 4.1, 6, 5.1, 1.2), U(R(7.4, 5.4, 1.2, 5.2), R(5.4, 7.4, 5.2, 1.2))), C.iron, {spec: [2, 1]});
  },
  SURGEON_THIMBLE: i => {
    i.paint(P([[4.5, 14], [4, 8.4], [5.4, 4.4], [8, 2.6], [10.6, 4.4], [12, 8.4], [11.5, 14]]), C.silver, {spec: [3, 2]});
    i.paint(R(3.6, 12.2, 8.8, 2), C.steel); i.dots([[6, 6], [8, 5], [10, 6], [7, 8], [9, 8], [6, 10], [8, 10], [10, 10]], 0x6a7a90); i.dots([[5, 13]], 0xb02030);
  },
  QUARANTINE_MASK: i => {
    i.paint(P([[4.4, 1.8], [11.6, 1.8], [12.6, 5.6], [11.4, 8.4], [9.2, 9], [8.2, 15.2], [7.8, 15.2], [6.8, 9], [4.6, 8.4], [3.4, 5.6]]), C.leather, {grain: 'x', spec: [3, 1]});
    i.paint(D(5.9, 5.2, 1.8), C.glass); i.paint(D(10.1, 5.2, 1.8), C.glass);
    i.mark(U(RG(5.9, 5.2, 2.1, 1.6), RG(10.1, 5.2, 2.1, 1.6)), 0x6f4b24); i.mark(L(8, 9, 8, 14, .6), 0x5a3a1c);
  },
  SILVER_VERDICT: i => {
    i.paint(L(8, 7, 12.6, 13.6, 1.7), C.darkWood);
    i.paint(P([[2.6, 4.6], [8.6, 1.4], [11.4, 5.2], [5.6, 8.4]]), C.silver, {spec: [3, 1]});
    i.paint(R(8.4, 13.6, 6.4, 1.6), C.darkIron);
  },
  MIRROR_SHARD: i => {
    i.paint(P([[8, 1.5], [13, 6], [11.5, 13.4], [6, 14.4], [3, 8.4]]), C.steel, {spec: [3, 2]});
    i.mark(L(6, 5, 10, 9, .9), 0xe8f4ff); i.dots([[5, 9], [9, 12], [8, 3]], 0xffffff);
  },
  SALT_CENSER: i => {
    i.paint(L(8, 1, 8, 4.4, 1), C.silver);
    i.paint(D(8, 9.2, 4.8), C.silver, {spec: [1, 1]}); i.paint(R(3.4, 8.6, 9.2, 1), C.darkIron);
    i.dots([[6, 6], [10, 6], [6, 12], [10, 12], [8, 11]], 0x47566c); i.dots([[12.6, 4], [13.4, 2.6], [12.4, 1.6]], 0xffffff);
  },
  CHOIR_SHELL: i => {
    i.paint(P([[8, 14], [1.6, 7.2], [3, 3.8], [6, 2.4], [8, 2], [10, 2.4], [13, 3.8], [14.4, 7.2]]), ramp(0xf0cfd0), {spec: [3, 2]});
    for (const [x, y] of [[3.4, 5], [5.8, 4], [8, 3.6], [10.2, 4], [12.6, 5]]) i.mark(L(8, 13, x, y, .8), 0xc79aa2);
    i.paint(R(6.6, 13, 2.8, 1.6), C.bone);
  },
  DIVER_SEAL: i => seal(i, C.waxBlue, PA([[5, 6.8], [6.4, 5.2], [8, 6.8], [9.6, 5.2], [11, 6.8]], .9), C.linen),
  RELIQUARY_KEY: i => {
    i.paint(ER(8, 4.2, 3.2, 3.2, 1.6), C.gold); i.paint(R(7.2, 7, 1.7, 7.8), C.gold);
    i.paint(R(8.8, 10.6, 2.4, 1.3), C.gold); i.paint(R(8.8, 13, 1.8, 1.3), C.gold); i.dots([[8, 4]], 0xfff3b8);
  },
  HANGMAN_LOOP: i => {
    i.paint(ER(8, 9.2, 4.2, 4, 1.7), C.straw, {grain: 'x'});
    i.paint(L(8, 1.4, 8, 4.4, 1.5), C.straw); i.paint(R(6.2, 3.8, 3.6, 3.6), C.straw, {grain: 'x'}); i.mark(L(6.4, 4.4, 9.6, 6.6, .7), 0x7a5d22); i.mark(L(6.4, 6.4, 9.6, 4.4, .7), 0x7a5d22);
  },
  FASTING_SPOON: i => {
    i.paint(P([[7, 7.4], [9, 7.4], [9.5, 14.6], [6.5, 14.6]]), C.wood, {grain: 'v'});
    i.paint(E(8, 4.6, 3.4, 3.8), C.wood, {spec: [1, 1]}); i.paint(E(8, 4.8, 2.1, 2.5), ramp(0xd9b27a), {shade: 'flat'});
  },
  STAR_CHART: i => {
    i.paint(R(3, 2.6, 10, 10.8), ramp(0x26346e), {shade: 'flat'});
    i.paint(R(1.6, 2, 2.2, 12), C.parchment); i.paint(R(12.2, 2, 2.2, 12), C.parchment);
    i.dots([[5, 5], [8, 4], [10, 7], [6, 9], [11, 11], [9, 5]], 0xffffff); i.mark(PA([[5, 5], [8, 4], [10, 7]], .7), 0x9fb2ff);
  },
  NIGHT_TALLOW: i => {
    i.paint(U(D(8, 10.4, 4.2), D(6, 9, 3), D(10.6, 9.2, 2.6), R(4.4, 10, 7.2, 3.6)), C.tallow, {spec: [2, 2]});
    i.paint(L(8, 4.6, 8, 8.6, .9), C.soot); i.paint(E(12, 13.2, 1, 1.6), C.tallow); i.dots([[9, 3], [8, 2], [9, 1]], 0x8a8794);
  },
  THORN_BROOCH: i => {
    i.paint(ER(8, 8.6, 5.2, 5.2, 1.6), C.bronze); i.paint(P([[8, 1], [9.7, 10], [8, 11], [6.3, 10]]), C.darkWood, {spec: [1, 1]});
  },
  SANCTUARY_ACORN: i => {
    i.paint(E(8, 10, 3.7, 4.3), C.straw, {spec: [1, 1]});
    i.paint(P([[3.2, 8.4], [4.2, 5], [8, 3.6], [11.8, 5], [12.8, 8.4]]), C.darkWood, {grain: 'x'}); i.paint(R(7.3, 1.6, 1.5, 2.4), C.wood);
  },
  WAX_SEAL: i => seal(i, C.waxRed, P([[8, 3.4], [9.6, 6], [8, 9.2], [6.4, 6]]), C.cloth),
  VIGIL_WICK: i => {
    i.paint(E(8, 13, 5, 1.7), C.waxWhite); i.paint(L(8, 12.4, 8, 6.6, .9), C.soot);
    i.paint(P([[8, 1.2], [10.2, 4.4], [8, 7], [5.8, 4.4]]), C.flame); i.paint(P([[8, 3.4], [9, 5], [8, 6.4], [7, 5]]), C.hot);
  },
  CRIMSON_SUTURE: i => {
    i.paint(AR(8, 8, 5.6, PI * .12, PI * 1.25, 1.3), C.silver);
    i.paint(PA([[4, 12.6], [2.8, 10], [4.6, 8.2], [3.4, 5.6]], .9), C.blood); i.dots([[12, 11]], 0xffffff);
  },
  CHALICE_CHAIN: i => {
    i.paint(P([[3.5, 2.4], [12.5, 2.4], [11.6, 7.4], [9, 9.2], [7, 9.2], [4.4, 7.4]]), C.gold, {spec: [2, 2]});
    i.paint(R(3.8, 2.4, 8.4, 1.3), C.blood); i.paint(R(7.2, 9, 1.7, 3.2), C.gold); i.paint(E(8, 13.2, 3.4, 1.3), C.gold);
    i.paint(PA([[13, 4], [14.4, 6.6], [13, 9], [14.4, 11.6]], .9), C.silver);
  },
  BASILICA_CHIP: i => {
    i.paint(P([[3, 6], [7, 1.8], [13.4, 4], [12, 11], [7.5, 14.4], [3.6, 11]]), C.darkIron);
    i.mark(P([[7, 2.8], [12.6, 4.6], [9, 7.4]]), 0xc4263a); i.mark(P([[4, 6.8], [8.6, 7.8], [6, 12]]), 0x2d5fb8); i.mark(P([[9.6, 8.6], [11.8, 10.4], [8, 13]]), 0xe5b82a);
  },
  FRACTURED_ICON: i => {
    i.paint(R(3, 1.8, 10, 12.4), C.gold); i.paint(R(4.8, 3.6, 6.4, 8.8), ramp(0x3a2c5a), {shade: 'flat'});
    i.paint(D(8, 6.6, 1.5), C.cream); i.paint(P([[5.8, 12], [6.4, 8.6], [9.6, 8.6], [10.2, 12]]), C.cream);
    i.mark(PA([[10.4, 2], [8.2, 6], [9.6, 9], [7, 14]], .9), 0x16121f);
  },
  SPORE_PENDANT: i => {
    i.paint(AR(8, 5.2, 3.2, PI, 2 * PI, 1), C.silver);
    i.paint(P([[3.4, 10], [4, 7.2], [6, 5], [8, 4.4], [10, 5], [12, 7.2], [12.6, 10]]), C.fungus, {spec: [2, 2]});
    i.paint(R(6.6, 10, 2.8, 4.2), C.cream); i.dots([[5, 7], [9, 6], [10, 8]], 0xd9f5c8); i.dots([[2, 12], [13, 12], [14, 8]], 0xa9d97c);
  },
  MYCELIAL_PAD: i => {
    i.paint(E(8, 9.4, 6.4, 4), ramp(0xd9cfe6), {grain: 'dots'});
    i.mark(L(3.4, 9, 12.6, 10, .7), 0xf4eefa); i.mark(L(5, 11.4, 11, 7.6, .7), 0xf4eefa);
    i.paint(D(5.2, 6.4, 1.7), C.rose); i.paint(D(10.8, 5.8, 1.5), C.fungus);
  },
  IRON_WRIT: i => {
    i.paint(R(3, 1.8, 10, 12.4), C.iron, {spec: [1, 1]});
    for (const y of [3.6, 5.6, 7.6]) i.mark(R(4.8, y, 6.4, .9), 0xd4dbe6);
    i.paint(D(8, 11.2, 1.6), C.waxRed); i.dots([[4, 3], [12, 3], [4, 13], [12, 13]], 0xc9d1dc);
  },
  CUSTODIAN_RIVET: i => {
    i.paint(R(6.5, 8, 3, 6), C.iron); i.paint(R(5, 13.4, 6, 1.5), C.iron);
    i.paint(E(8, 6, 5.4, 3.8), C.steel, {spec: [2, 1]}); i.dots([[8, 5.6]], 0x23304a);
  },
  VELVET_RIBBON: i => {
    i.paint(P([[8, 7.6], [2.4, 3], [1.8, 10.2]]), C.velvet, {spec: [1, 1]}); i.paint(P([[8, 7.6], [13.6, 3], [14.2, 10.2]]), C.velvet);
    i.paint(P([[6.6, 9], [4.4, 14.8], [7, 12.8], [8, 9]]), C.velvet); i.paint(P([[9.4, 9], [11.6, 14.8], [9, 12.8], [8, 9]]), C.velvet);
    i.paint(D(8, 7.8, 1.8), ramp(0xb82a42));
  },
  FUNERAL_BUTTON: i => {
    i.paint(D(8, 8, 5.2), C.waxBlack, {spec: [1, 1]}); i.mark(RG(8, 8, 4.3, 3.7), 0x4b465c);
    i.dots([[7, 7], [9, 7], [7, 9], [9, 9]], 0x9a93ab); i.mark(U(L(7, 7, 9, 9, .6), L(9, 7, 7, 9, .6)), 0xc7c1d6);
  },
  AMBER_PRISM: i => {
    i.paint(P([[8, 1.8], [14, 13], [2, 13]]), C.amber, {spec: [3, 4]}); i.mark(P([[8, 5.2], [11.4, 11.6], [4.6, 11.6]]), 0xf3be4c); i.dots([[8, 9]], 0x5a3410);
  },
  BAPTISM_DROP: i => {
    i.paint(ER(8, 13, 5.2, 1.7, .9), C.ice);
    i.paint(P([[8, 1.4], [10.8, 6], [11.6, 8.4], [10.2, 10.8], [8, 11.6], [5.8, 10.8], [4.4, 8.4], [5.2, 6]]), C.water, {spec: [2, 5]});
  },
  MUFFLED_CLAPPER: i => {
    i.paint(L(8, 1.4, 8, 8, 1.4), C.iron); i.paint(D(8, 10.6, 3.9), C.cloth, {spec: [1, 1], grain: 'x'});
    i.mark(L(4.6, 9, 11.4, 9.4, .8), 0x7c786f); i.mark(L(4.2, 11.8, 11.8, 12, .8), 0x7c786f);
  },
  BELL_COUNTERWEIGHT: i => {
    i.paint(P([[6, 5], [10, 5], [12.6, 12], [11, 14.6], [5, 14.6], [3.4, 12]]), C.bronze, {spec: [2, 2]}); i.paint(ER(8, 3.2, 2.2, 2.2, 1.1), C.iron);
  },
  CARRION_TOKEN: i => {
    i.paint(D(8, 8, 5.4), C.charcoal, {spec: [1, 1]}); i.mark(RG(8, 8, 4.6, 4.1), 0x55505e);
    i.mark(P([[4.8, 9.4], [7, 6.8], [10, 6.2], [11.6, 7.2], [13, 7], [11.8, 8.4], [10.6, 10.4], [7, 11], [5.2, 10.6]]), 0xd8d3e0);
  },
  KEEPER_WHISTLE: i => {
    i.paint(R(3.4, 5.4, 9, 3.6), C.brass, {spec: [1, 0]}); i.paint(R(1.6, 6.2, 2.6, 2.2), C.darkIron);
    i.dots([[8, 5.4]], 0x16121f); i.paint(ER(12.4, 11, 2.2, 2.2, .9), C.straw); i.paint(L(11.4, 9, 12, 9.4, 1), C.straw);
  },
  OPAL_CABOCHON: i => {
    i.paint(E(8, 8, 6.2, 4.7), C.gold); i.paint(E(8, 8, 4.7, 3.3), C.pearl, {spec: [2, 0]});
    i.dots([[6, 8], [9, 7], [8, 9], [10, 9]], 0xf08ac0); i.dots([[7, 7], [10, 8]], 0x5fe0d8); i.dots([[8, 8]], 0xa4e86a);
  },
  PRISMATIC_CLASP: i => {
    i.paint(S(R(2, 3.6, 12, 9), R(4, 5.6, 8, 5)), C.silver, {spec: [1, 1]}); i.paint(R(7.4, 5.4, 1.3, 5.2), C.silver);
    i.paint(P([[8, 6.4], [10, 9.6], [6, 9.6]]), C.glass); i.dots([[4, 4], [8, 4], [12, 4]], 0xe0507a); i.dots([[6, 4], [10, 4]], 0x5fe0d8);
  },
  FOUNDRY_SLAG: i => {
    i.paint(P([[3, 9], [4.4, 4.6], [8, 2.8], [12, 4], [13.6, 8.6], [11.6, 13], [6, 14], [3.6, 12]]), C.coal, {grain: 'dots'});
    i.mark(PA([[5, 6], [8, 8.6], [7, 12.4]], 1), 0xff7a1c); i.mark(PA([[8, 8.6], [11.6, 7.6]], .8), 0xffb13a);
  },
  TEMPERED_RIVET: i => {
    i.paint(R(6.5, 8, 3, 6), C.iron); i.paint(R(5, 13.4, 6, 1.5), C.iron);
    i.paint(E(8, 6, 5.4, 3.8), ramp(0x5870c8), {spec: [2, 1]}); i.mark(E(8, 4.6, 3.6, 1.2), 0xd7b13a); i.mark(R(4.4, 6.2, 7.2, .9), 0x8a58b8);
  },
  MENAGERIE_TAG: i => {
    i.paint(P([[3, 5.4], [10.6, 5.4], [13.4, 8.4], [10.6, 11.4], [3, 11.4]]), C.leather, {grain: 'x', spec: [1, 1]});
    i.dots([[4.6, 8.4]], 0x16121f); i.paint(ER(12.2, 4, 2.4, 2.4, .8), C.straw);
    i.mark(U(D(8, 9.2, 1.1), D(6.6, 7.6, .55), D(8, 7.2, .55), D(9.4, 7.6, .55)), 0x3a2418);
  },
  PALE_FEATHER: i => {
    i.paint(P([[2.6, 1.8], [6.2, 2], [10, 4.4], [12.6, 8.2], [13, 12.8], [10, 11.4], [6.4, 9], [3.6, 5.8]]), C.linen, {spec: [2, 1]});
    i.mark(L(3, 2.4, 12.4, 12.4, .8), 0xa6adbd);
    for (const [x, y] of [[5, 4], [7.2, 5.8], [9.4, 8], [11.2, 10.4]]) { i.mark(L(x, y, x + 2.6, y - 1.8, .7), 0xcdd3df); i.mark(L(x, y, x - .6, y + 2.4, .7), 0xcdd3df); }
  },
  SODDEN_BOOKMARK: i => {
    i.paint(P([[5, 1.5], [11, 1.5], [11, 11.4], [9.6, 13.2], [8, 11.6], [6.4, 13.2], [5, 11.4]]), ramp(0x7f9a9a), {grain: 'dust'});
    i.mark(PA([[6, 3], [8, 5], [10, 4], [8, 8], [6, 7.4]], .8), 0x546b6c); i.dots([[7, 13.8], [9, 14]], 0x3f8ee0);
  },
  SCRIBE_REED: i => {
    i.paint(L(12.6, 1.6, 4.6, 12.6, 1.7), C.reed, {grain: 'v'}); i.mark(L(9.6, 5.8, 10.8, 4.8, .8), 0x6b6a30);
    i.paint(P([[4.6, 12.4], [2.8, 14.8], [5.8, 13.8]]), C.darkIron); i.dots([[2.2, 15]], 0x16121f);
  },
  OBSIDIAN_CLASP: i => {
    i.paint(P([[8, 1.8], [13.6, 5], [13.6, 11], [8, 14.2], [2.4, 11], [2.4, 5]]), C.obsidian, {spec: [2, 2]});
    i.mark(P([[8, 2.8], [12.6, 5.4], [8, 8]]), 0x5a3f86); i.mark(P([[3.4, 5.6], [8, 8], [3.4, 10.6]]), 0x3d2c5c); i.dots([[7, 4]], 0xcdb8ff);
  },
  VESTRY_PIN: i => {
    i.paint(L(8, 4, 8, 15, 1.1), C.silver); i.paint(R(7.1, 1.2, 1.8, 4.4), C.gold); i.paint(R(5.4, 2.6, 5.2, 1.5), C.gold);
  },
  PAUPER_COIN: i => {
    i.paint(D(8, 8, 5.2), C.copper, {spec: [1, 1]}); i.mark(RG(8, 8, 4.4, 3.8), 0x8f5128); i.mark(U(R(7.3, 5, 1.4, 6), R(5.6, 6.2, 4.8, 1.2)), 0x8f5128);
  },
  GILDED_CRUMB: i => {
    i.paint(P([[3, 10], [4, 6], [7, 4], [11, 4.6], [13.4, 8], [12, 12.2], [7.5, 13.4], [4, 12.6]]), C.straw, {grain: 'dots', spec: [2, 1]});
    i.dots([[6, 7], [10, 6], [9, 10], [5, 11], [11, 9]], 0xf4c430);
  },
  ASTRAL_COMPASS: i => {
    i.paint(D(8, 8, 5.7), C.gold, {spec: [1, 1]}); i.paint(D(8, 8, 4.2), ramp(0x1f2d66), {shade: 'flat'});
    i.mark(P([[8, 3.6], [9.1, 6.9], [12.4, 8], [9.1, 9.1], [8, 12.4], [6.9, 9.1], [3.6, 8], [6.9, 6.9]]), 0xcfe3ff); i.dots([[8, 8]], 0xe84a4a);
  },
  ORBIT_BEAD: i => {
    i.paint(ER(8, 8.2, 6.6, 2.7, .9), C.silver); i.paint(D(8, 8.2, 3.5), C.amethyst, {spec: [1, 1]});
    i.paint(I(ER(8, 8.2, 6.6, 2.7, .9), R(0, 8.2, 16, 8)), C.silver); i.dots([[13, 5], [3, 11.4]], 0xfff3b8);
  },
  LABYRINTH_THREAD: i => {
    i.paint(D(8, 7.6, 5.2), ramp(0xe0b84c), {spec: [1, 1]});
    i.mark(AR(8, 7.6, 3.5, 0, PI * 1.6, .8), 0x9a7418); i.mark(AR(8, 7.6, 1.8, PI * .6, PI * 2.1, .8), 0x9a7418);
    i.paint(PA([[11.6, 11.2], [13.8, 13], [12.6, 14.8]], .9), ramp(0xe0b84c));
  },
  MOURNER_TREAD: i => {
    i.paint(P([[5.2, 2.2], [9.4, 1.8], [10.8, 5], [10, 9], [11.2, 12.2], [9.6, 14.6], [6, 14.6], [5, 11], [5.8, 7.4], [4.4, 4.8]]), C.darkLeather, {grain: 'x', spec: [2, 1]});
    for (const y of [4, 7, 10, 13]) i.mark(L(5.6, y, 10, y - .4, .7), 0x2a1810);
    i.paint(P([[10.4, 4], [13.2, 6], [12.6, 7.4], [9.8, 5.4]]), C.waxBlack);
  },
  ABSOLUTION_MEDAL: i => {
    i.paint(P([[4, 1], [7.4, 1], [9.8, 7], [6.4, 7]]), C.linen); i.paint(P([[12, 1], [8.6, 1], [6.2, 7], [9.6, 7]]), C.velvet);
    i.paint(D(8, 10.8, 3.9), C.gold, {spec: [1, 1]}); i.mark(U(R(7.3, 8.2, 1.4, 5.2), R(5.6, 10, 4.8, 1.3)), 0x9a6f14);
  },
  LAST_CANDLE: i => {
    i.paint(E(8, 13.6, 4.6, 1.4), C.brass); i.paint(R(6, 6.4, 4, 7.4), C.waxWhite, {spec: [1, 1]}); i.paint(R(9.2, 6.8, .9, 3), C.waxWhite);
    i.dots([[8, 5.2]], 0x16121f); i.paint(P([[8, .8], [10.2, 3.8], [8, 6], [5.8, 3.8]]), C.flame); i.paint(P([[8, 2.6], [9, 4.2], [8, 5.4], [7, 4.2]]), C.hot);
  }
};

/** The Dungeon Reliquary: a leather drawstring pouch with a golden clasp. */
const POUCH = i => {
  i.paint(P([[3.6, 13.8], [2.8, 9.6], [4.6, 6.4], [6, 5.2], [10, 5.2], [11.4, 6.4], [13.2, 9.6], [12.4, 13.8], [8, 14.8]]), C.leather, {grain: 'x', spec: [3, 1]});
  i.paint(R(5.6, 3.8, 4.8, 2.6), C.darkLeather); i.paint(PA([[6, 3.8], [4, 1.4]], 1), C.straw); i.paint(PA([[10, 3.8], [12, 1.4]], 1), C.straw);
  i.paint(D(8, 10, 2), C.gold, {spec: [1, 1]}); i.mark(U(R(7.5, 8.8, 1, 2.4), R(6.8, 9.5, 2.4, 1)), 0x8a5a14);
};

module.exports = {ICONS, POUCH, make, draw: (name) => make(name === 'POUCH' ? POUCH : ICONS[name])};
