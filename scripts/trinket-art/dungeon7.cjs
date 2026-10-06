'use strict';
/* Icons for the Dungeon Dimension's generation 7 items: the 112 baubles of its 66 new themes (Floor I's eighteen, the Underworks'
 * and the Abyssal Citadel's twenty-four each, and the victor's laurel) and the three Floor Guardians' trophy weapons, keyed by
 * Relics.Type / TrophyCatalog.Trophy names. Their bands are explicit (catalog.cjs DUNGEON7_BANDS), so this table's order is free:
 * it follows the Java enum for reading. The 72 classic baubles and the pouch keep dungeon.cjs and its order (bands 1..73).
 */
const {M, Icon, ramp, flat} = require('./engine.cjs');
const C = require('./palette.cjs');
const P = M.poly, D = M.disc, R = M.rect, E = M.ellipse, L = M.line, PA = M.path, RG = M.ring, ER = M.ering, U = M.U, S = M.sub, AR = M.arc;
const PI = Math.PI;
/** The engine sets a dot at y * 16 + x as given, so a fractional dot would land on another pixel: here a dot is the pixel holding the point. */
const make = draw => { const i = new Icon(), dots = i.dots.bind(i); i.dots = (pts, color) => dots(pts.map(([x, y]) => [Math.floor(x), Math.floor(y)]), color); draw(i); return i; };

// ----------------------------------------------------------------------------------------------------- shared shapes
const heart = (cx, cy, s = 1) => U(D(cx - 2.3 * s, cy - 1.6 * s, 3.5 * s), D(cx + 2.3 * s, cy - 1.6 * s, 3.5 * s), P([[cx - 5.6 * s, cy - .9 * s], [cx + 5.6 * s, cy - .9 * s], [cx, cy + 6 * s]]));
/** A diamond flame from top, h tall and 2w wide, with a hot heart. */
const flame = (i, cx, top, h, w, outer = C.flame, inner = C.hot) => {
  i.paint(P([[cx, top], [cx + w, top + h * .55], [cx, top + h], [cx - w, top + h * .55]]), outer);
  i.paint(P([[cx, top + h * .35], [cx + w * .5, top + h * .62], [cx, top + h * .92], [cx - w * .5, top + h * .62]]), inner);
};
/** A leaf (a narrow diamond) at (x, y) lying along the tangent of angle a. */
const leaf = (x, y, a, l = 1.5, w = .8) => {
  const tx = -Math.sin(a), ty = Math.cos(a), nx = Math.cos(a), ny = Math.sin(a);
  return P([[x + tx * l, y + ty * l], [x + nx * w, y + ny * w], [x - tx * l, y - ty * l], [x - nx * w, y - ny * w]]);
};
const SOUL = ramp(0x3fb8d0), SOUL_HOT = flat(0xc8f6ff), VOID = ramp(0x4b2a7a), BASALT = ramp(0x4d5566);

const ICONS = {
  // ------------------------------------------------------------------------------------------- Floor I: The House of Mercy (36..53)
  FOUNDERS_FORK: i => {
    i.paint(U(R(4.4, 1.6, 1.8, 7), R(9.8, 1.6, 1.8, 7), AR(8, 8.2, 2.7, 0, PI, 1.8)), C.brass, {spec: [1, 1]});
    i.paint(R(7.2, 10.4, 1.6, 2.8), C.brass); i.paint(D(8, 13.4, 1.3), C.bronze);
    i.dots([[13, 3], [13.6, 5], [13, 7], [2.6, 3], [2, 5], [2.6, 7]], 0xfff3b8);
  },
  BRONZE_TOLL: i => {
    i.paint(P([[8, 2.6], [10.4, 3.8], [11, 7.4], [12.4, 11], [3.6, 11], [5, 7.4], [5.6, 3.8]]), C.bronze, {spec: [2, 2]});
    i.paint(R(3.2, 10.6, 9.6, 1.6), C.bronze); i.paint(D(8, 13.4, 1.2), C.darkIron); i.paint(R(7.2, 1.2, 1.6, 1.6), C.darkIron);
    i.paint(AR(8, 7.4, 6.2, -PI * .28, PI * .28, .8), ramp(0xf3d9a4)); i.paint(AR(8, 7.4, 6.2, PI * .72, PI * 1.28, .8), ramp(0xf3d9a4));
  },
  MOTH_COCOON: i => {
    i.paint(L(8, 1.2, 8, 3.8, 1), C.cloth);
    i.paint(E(8, 9, 3.6, 5.4), C.linen, {spec: [1, 2]});
    i.mark(U(L(4.6, 6, 11.4, 8.4, .7), L(4.6, 9.4, 11.4, 11.6, .7), L(5.2, 12.4, 10.8, 14, .6)), 0xc9bfa6);
  },
  WINGDUST_PHIAL: i => {
    i.paint(P([[8, 6], [2.4, 2.6], [2, 8], [8, 9]]), C.ash, {spec: [1, 1]}); i.paint(P([[8, 6], [13.6, 2.6], [14, 8], [8, 9]]), C.ash);
    i.paint(P([[8, 9], [3.2, 9.6], [4.6, 13.4], [8, 11]]), ramp(0x857d74)); i.paint(P([[8, 9], [12.8, 9.6], [11.4, 13.4], [8, 11]]), ramp(0x857d74));
    i.paint(E(8, 8.6, 1.2, 4), C.soot); i.mark(U(D(4.8, 5.4, 1), D(11.2, 5.4, 1)), 0x3a3540);
    i.paint(PA([[7.4, 4.6], [6.2, 1.8]], .7), C.soot); i.paint(PA([[8.6, 4.6], [9.8, 1.8]], .7), C.soot);
  },
  VOTIVE_STUB: i => {
    i.paint(P([[3.6, 6.6], [12.4, 6.6], [11.4, 14.4], [4.6, 14.4]]), ramp(0xb0303a), {spec: [1, 1]});
    i.paint(R(5, 5.4, 6, 2.4), C.waxWhite); i.paint(L(7.5, 3.6, 7.5, 5.4, .8), C.soot);
    flame(i, 8, 1, 3.6, 1.6); i.mark(R(5, 9, 6, .8), 0xe25560);
  },
  WRIGHTS_TAPER: i => {
    i.paint(L(4.6, 12.2, 10.4, 4.8, 1.9), C.waxWhite, {spec: [1, 1]}); i.paint(L(3.4, 13.4, 5.2, 11.4, 2.4), C.brass);
    i.paint(L(10.4, 4.8, 11.2, 3.8, .8), C.soot); flame(i, 12, 1.4, 3.4, 1.5);
  },
  LIBRARIANS_CHAIN: i => {
    i.paint(R(2, 2, 8.4, 10.4), ramp(0x6b2e2a), {spec: [1, 1]}); i.paint(R(2, 2, 1.6, 10.4), ramp(0x4a1e1c)); i.mark(R(4.6, 4.4, 4.4, 1), 0xd8b45c);
    i.paint(D(10.4, 7.4, 1.3), C.iron);
    i.paint(ER(11.8, 10, 1.5, 2.1, .9), C.iron); i.paint(ER(12.6, 13.2, 2, 1.4, .9), C.iron);
  },
  MARGIN_NOTE: i => {
    i.paint(R(3, 1.6, 10, 12.8), C.paper, {spec: [1, 1]});
    i.mark(L(5.4, 1.8, 5.4, 14.2, .7), 0xc23b3b);
    for (const y of [4.5, 6.5, 8.5, 10.5, 12.5]) i.mark(L(6.6, y, 12, y, .6), 0x9fb0c8);
    i.mark(PA([[3.4, 5], [4.6, 6.4], [3.6, 7.8]], .6), 0x1d2a5c);
  },
  BATH_SPONGE: i => {
    i.paint(P([[3, 5], [5, 3.4], [11, 3.4], [13, 5], [13, 11.6], [11, 13.2], [5, 13.2], [3, 11.6]]), ramp(0xe8cf52), {grain: 'dots', spec: [2, 1]});
    i.dots([[5, 6], [8, 5], [10, 7], [6, 9], [9, 10], [11, 11], [5, 12]], 0xa98a20);
    i.paint(RG(12.4, 2.4, 1.6, .8), C.glass); i.paint(D(13.8, 5, .9), C.glass);
  },
  PENITENT_PUMICE: i => {
    i.paint(P([[2.4, 8], [3.6, 4.4], [7, 3], [11.6, 3.6], [13.8, 7], [12.6, 11.4], [8.4, 13.2], [4, 12.2]]), ramp(0xb7b2a8), {grain: 'dots', spec: [2, 1]});
    i.dots([[5, 6], [7, 5], [10, 5], [11, 8], [8, 8], [5, 9], [7, 11], [10, 11], [12, 6]], 0x6f6a62);
  },
  STONE_LIKENESS: i => {
    i.paint(P([[2.6, 14.4], [3.6, 11], [6, 9.6], [10, 9.6], [12.4, 11], [13.4, 14.4]]), ramp(0x9a9a9e), {spec: [2, 1]});
    i.paint(E(8, 5.8, 3.4, 4.2), ramp(0xb2b2b6), {spec: [1, 1]});
    i.mark(U(L(6.4, 5.4, 7.4, 5.4, .6), L(8.6, 5.4, 9.6, 5.4, .6), L(7.2, 8.5, 8.8, 8.5, .6)), 0x5e5e64);
    i.mark(PA([[10.6, 2.6], [9.6, 5], [10.8, 7.6]], .6), 0x4a4a52);
  },
  EFFIGY_WAX: i => {
    i.paint(D(8, 4, 2.6), C.waxWhite, {spec: [1, 1]});
    i.paint(P([[4.6, 14.4], [5.4, 8], [6.6, 6.6], [9.4, 6.6], [10.6, 8], [11.4, 14.4]]), C.waxWhite);
    i.paint(U(E(4.2, 11.6, .8, 1.8), E(11.8, 10.8, .8, 2)), C.waxWhite);
    i.mark(U(D(7, 4, .5), D(9, 4, .5)), 0x8a7e66); i.mark(L(6.8, 10.5, 9.2, 10.5, .6), 0xc9bf9f);
  },
  MARKET_LEDGER: i => {
    i.paint(P([[1.6, 4], [7.6, 5.2], [7.6, 13], [1.6, 11.8]]), C.parchment, {spec: [1, 1]}); i.paint(P([[14.4, 4], [8.4, 5.2], [8.4, 13], [14.4, 11.8]]), C.parchment);
    i.paint(L(8, 5, 8, 13.4, 1), C.leather);
    for (const y of [6.6, 8.4, 10.2]) { i.mark(L(2.6, y - .6, 6.8, y + .2, .5), 0x7b6a4a); i.mark(L(9.2, y + .2, 13.4, y - .6, .5), 0x7b6a4a); }
    i.paint(D(12, 12.4, 1.8), C.gold);
  },
  MOURNERS_OBOL: i => {
    i.paint(D(8, 8, 5.4), ramp(0x9da3ad), {spec: [1, 1]}); i.mark(RG(8, 8, 4.6, 4.1), 0x52565e);
    i.mark(P([[8, 4.6], [9.6, 7.6], [9.4, 9.6], [8, 10.6], [6.6, 9.6], [6.4, 7.6]]), 0x5f8fd0); i.dots([[7, 8]], 0xdfe8f8);
  },
  WEEPING_BOUGH: i => {
    i.paint(AR(8, 13, 6, PI * 1.08, PI * 1.92, 1.4), C.darkWood);
    for (const [x, y] of [[3.4, 9.4], [5.6, 8.2], [8, 7.6], [10.4, 8.2], [12.6, 9.4]]) i.paint(E(x, y + 2, .9, 2), C.leaf);
    i.paint(P([[12.6, 11.8], [13.6, 13.2], [12.6, 14.4], [11.6, 13.2]]), C.water);
  },
  BITTER_FRUIT: i => {
    i.paint(S(U(D(6.4, 9, 4.4), D(9.6, 9, 4.4)), D(13.6, 8, 2.2)), ramp(0x6f8a2c), {spec: [2, 1]});
    i.paint(L(8, 4.8, 8.6, 2.2, 1), C.darkWood); i.paint(E(10.4, 3, 1.8, 1), C.leaf);
    i.mark(U(D(11.6, 7.4, .6), D(11.4, 9.4, .6)), 0xe8e4c0);
  },
  CASKET_NAIL: i => {
    i.paint(L(5.4, 5.4, 13, 13, 1.6), C.iron); i.paint(P([[2, 4.6], [4.6, 2], [7.4, 4.8], [4.8, 7.4]]), C.darkIron, {spec: [1, 1]});
    i.paint(P([[12, 12.6], [13.8, 13.8], [12.6, 12]]), C.iron); i.dots([[8, 9], [10, 10], [9, 8]], 0x985a32);
  },
  CORRODED_SIGIL: i => {
    i.paint(P([[3, 2], [13, 2], [13, 8], [8, 14.4], [3, 8]]), C.bronze, {spec: [1, 1]});
    i.mark(U(L(8, 4, 8, 11, 1), L(5.4, 6.4, 10.6, 6.4, 1)), 0x5c3a1c);
    i.dots([[4, 4], [11, 3], [12, 7], [5, 9], [9, 12], [10, 9]], 0x4fa38c);
  },
  LANTERN_GLASS: i => {
    i.paint(ER(8, 2.4, 1.6, 1.4, .8), C.darkIron); i.paint(P([[4.4, 4.6], [11.6, 4.6], [8, 2.8]]), C.darkIron);
    i.paint(R(4.6, 4.8, 6.8, 7.6), ramp(0xf4c46a), {spec: [1, 1]}); i.paint(R(4, 12.2, 8, 1.8), C.darkIron);
    i.mark(U(L(4.8, 5, 4.8, 12.2, .7), L(11.2, 5, 11.2, 12.2, .7)), 0x484e58);
    flame(i, 8, 6.4, 4.6, 1.5);
  },
  LAMPLIGHTERS_HOOK: i => {
    i.paint(L(3, 14, 10.4, 4.6, 1.4), C.wood, {grain: 'v'});
    i.paint(AR(11.4, 3.8, 2.2, PI * .6, PI * 2.1, 1.1), C.brass);
    flame(i, 11.4, 7, 3.6, 1.3);
  },
  SALT_CELLAR: i => {
    i.paint(P([[4.6, 14.4], [3.8, 8], [5, 5.6], [11, 5.6], [12.2, 8], [11.4, 14.4]]), C.pearl, {spec: [2, 2]});
    i.paint(E(8, 5, 3.6, 1.6), C.silver); i.paint(R(7, 2.4, 2, 2.4), C.silver); i.dots([[6.6, 4.8], [8, 4.4], [9.4, 4.8]], 0x2b2f3a);
    i.dots([[13, 3], [13.8, 5], [13.2, 1.6]], 0xffffff);
  },
  COOKS_LADLE: i => {
    i.paint(L(12.8, 1.8, 7.6, 9, 1.3), C.steel); i.paint(ER(13, 2, 1.2, 1.2, .7), C.steel);
    i.paint(E(5.6, 11, 3.6, 3), C.steel, {spec: [1, 1]}); i.paint(E(5.6, 10.6, 2.4, 1.6), ramp(0xb07a3a), {shade: 'flat'});
  },
  MIMES_GLOVE: i => {
    i.paint(U(R(4.1, 2.6, 1.8, 5.6), R(7.1, 1.6, 1.8, 6.6), R(10.1, 2.6, 1.8, 5.6), R(4.1, 7.6, 7.8, 5), P([[4.4, 10], [1.6, 7.6], [2.6, 6.6], [5, 8.4]])), C.cream, {spec: [1, 1]});
    i.paint(R(3.6, 12.4, 8.8, 2.2), ramp(0x2d2a34)); i.mark(R(3.6, 13.1, 8.8, .6), 0x6a6478);
  },
  CURTAIN_CORD: i => {
    i.paint(PA([[3, 1.6], [6, 4], [7, 7], [8, 9]], 1.3), C.velvet);
    i.paint(D(8, 9.4, 1.6), C.gold); i.paint(P([[6.4, 10.6], [9.6, 10.6], [11.4, 14.6], [4.6, 14.6]]), C.velvet, {grain: 'v'});
    i.mark(R(5, 13.4, 6, .8), 0xd8b45c);
  },
  GUTTER_RAG: i => {
    i.paint(S(P([[2.6, 3], [13.4, 2.4], [12.8, 9], [13.6, 13.6], [10.6, 12.6], [8.4, 14.2], [6, 12.4], [3.4, 13.6], [3.6, 8]]), D(9, 6.6, 1.4), D(5.4, 10, 1)), ramp(0x8a7f6a), {grain: 'x', spec: [1, 1]});
    i.paint(R(9.6, 9, 2.6, 2.4), ramp(0x5c6a7a));
  },
  RAT_KING_KNOT: i => {
    for (const [x, y, bend] of [[2.4, 3.4, -1.4], [13.6, 3.4, 1.4], [2.4, 12.4, 1.4], [13.6, 12.6, -1.4], [8, 14.4, 1.6], [8, 1.6, -1.6]]) i.paint(PA([[8, 8], [(8 + x) / 2 + bend, (8 + y) / 2], [x, y]], .9), ramp(0xd9a0a0));
    i.paint(D(8, 8, 2.6), ramp(0xb98a8a), {spec: [1, 1]}); i.paint(D(8, 8, 1.4), ramp(0x8a5a5a));
  },
  INCENSE_CONE: i => {
    i.paint(E(8, 13, 5.4, 1.6), C.brass, {spec: [2, 0]});
    i.paint(P([[8, 5.6], [10.4, 12.4], [5.6, 12.4]]), ramp(0x8a3a2a), {spec: [1, 2]}); i.dots([[8, 5.4]], 0xff8a22);
    i.paint(PA([[8, 4.6], [9.4, 3.4], [8, 2.2], [9.2, 1.2]], .9), ramp(0xc9c7cc));
  },
  SWINGING_THURIBLE: i => {
    i.paint(L(3, 1.6, 7.4, 6.4, .9), C.brass);
    i.paint(D(9, 9.4, 4), C.brass, {spec: [1, 1]}); i.paint(P([[5.6, 7.6], [9, 4.6], [12.4, 7.6]]), C.brass);
    i.mark(R(5, 9.2, 8, .8), 0x6e5418); i.dots([[7, 11], [9, 11.6], [11, 11]], 0x3a2a10);
    i.paint(U(D(13.4, 3.6, 1.2), D(12, 2, .9)), ramp(0xcfcfd6));
  },
  GARDEN_BLOOM: i => {
    i.paint(AR(6.2, 5.6, 3.8, PI * 1.1, PI * 2, 1), C.leaf); i.paint(E(3.4, 4.4, 1.6, .9), C.leaf);
    i.paint(P([[10, 4.8], [12.8, 6.8], [13.6, 11], [12, 10.2], [10, 12], [8, 10.2], [6.4, 11], [7.2, 6.8]]), C.rose, {spec: [2, 1]});
    i.paint(L(10.5, 11.4, 10.5, 13.2, .8), C.straw); i.dots([[10, 13]], 0xf4c430);
  },
  GARDENERS_TWINE: i => {
    i.paint(R(3, 2.4, 10, 2), C.wood); i.paint(R(3, 11.6, 10, 2), C.wood);
    i.paint(R(4.4, 4.4, 7.2, 7.2), ramp(0x7f9a4a), {grain: 'h', spec: [1, 1]});
    i.paint(PA([[11.6, 8], [13.6, 9.4], [12.4, 12], [14, 14]], .9), ramp(0x7f9a4a));
  },
  HOSTEL_BLANKET: i => {
    i.paint(R(2.4, 4, 11.2, 3.4), ramp(0x8a4a3a), {spec: [1, 0]}); i.paint(R(2.4, 7.6, 11.2, 3.4), ramp(0xc9a46a)); i.paint(R(2.4, 11.2, 11.2, 2.6), ramp(0x8a4a3a));
    i.mark(U(L(2.6, 5.6, 13.4, 5.6, .6), L(2.6, 12.4, 13.4, 12.4, .6)), 0xe6d2a0);
  },
  PILGRIMS_TOKEN: i => {
    i.paint(ER(8, 2.6, 1.6, 1.6, .8), ramp(0x8c8f98));
    i.paint(D(8, 9, 5), ramp(0x8c8f98), {spec: [1, 1]}); i.mark(RG(8, 9, 4.2, 3.7), 0x5a5e68);
    i.mark(U(L(8, 5.8, 8, 12.2, 1.1), L(5.6, 7.5, 10.4, 7.5, .9), L(6.4, 11.5, 9.6, 11.5, .8)), 0x3b3e46);
  },
  SEXTONS_MEASURE: i => {
    i.paint(P([[1.6, 11.6], [11.4, 1.8], [14, 4.4], [4.2, 14.2]]), ramp(0xd8b46a), {spec: [2, 1]});
    for (let k = 0; k < 6; k++) { const x = 3.4 + k * 1.6, y = 12.2 - k * 1.6; i.mark(L(x, y, x + .9, y + .9, .5), 0x4a3a1a); }
    i.mark(L(7.6, 5.6, 10.2, 8.2, .7), 0x6b4a1a);
  },
  BURIAL_SHROUD: i => {
    i.paint(P([[8, 1.6], [11.6, 3.6], [12.4, 8], [13.6, 14.4], [2.4, 14.4], [3.6, 8], [4.4, 3.6]]), C.linen, {spec: [2, 2]});
    i.mark(U(L(6, 9, 5, 14, .6), L(10, 9, 11, 14, .6), L(7.5, 10, 7.5, 14.2, .6)), 0xc9c0aa);
    i.mark(U(E(6.6, 5.6, .7, .5), E(9.4, 5.6, .7, .5)), 0x8a8270);
  },
  UNLIT_WICK: i => {
    i.paint(E(8, 13.4, 4.4, 1.2), C.darkIron); i.paint(R(5.8, 6.4, 4.4, 7), C.waxBlack, {spec: [1, 1]});
    i.paint(L(7.5, 4.6, 7.5, 6.4, .8), C.soot); i.paint(PA([[8, 4.2], [6.8, 3.2], [8, 2.2], [7, 1.4]], .8), ramp(0xa8a6b0));
  },
  NAVE_VEIL: i => {
    i.paint(P([[8, 1.6], [12, 3], [13.6, 7], [13.6, 14], [2.4, 14], [2.4, 7], [4, 3]]), ramp(0x3a2a56), {grain: 'dots', spec: [2, 2]});
    i.mark(U(L(2.6, 13.4, 13.4, 13.4, .7), L(5.4, 4, 4, 13, .5), L(10.6, 4, 12, 13, .5)), 0x6a5a96);
    i.dots([[8, 6], [6, 9], [10, 9], [8, 12]], 0x8a7ab6);
  },
  // ------------------------------------------------------------------------------------------- Floor II: The Underworks (54..77)
  MAGMA_GIZZARD: i => {
    i.paint(S(E(8, 8.6, 6, 4.8), D(8.6, 13.6, 2.4)), ramp(0x9a3a1c), {spec: [2, 1]});
    i.mark(PA([[3.4, 8], [6, 7], [8, 9], [11, 7.4], [12.8, 9]], .8), 0xff8a22); i.dots([[6, 10], [10, 10.6]], 0xffb13a);
  },
  BASALT_HEART: i => {
    i.paint(heart(8, 8, 1), BASALT, {spec: [2, 1]});
    i.mark(U(L(4.5, 4, 4.5, 10, .5), L(7.5, 6, 7.5, 12, .5), L(10.5, 4, 10.5, 10, .5), L(3.4, 6.5, 12.6, 6.5, .5)), 0x2e333e);
    i.mark(PA([[6, 9.6], [7, 11], [9, 10.4], [10, 12]], .8), 0xff7a1c);
  },
  STALACTITE_TOOTH: i => {
    i.paint(P([[3.4, 1.6], [12.6, 1.6], [10.6, 4], [9.2, 9], [8, 13.2], [6.8, 9], [5.4, 4]]), ramp(0x8f8778), {grain: 'dust', spec: [1, 1]});
    i.mark(U(L(7, 3, 7.6, 8, .5), L(9.4, 2.6, 9, 6, .5)), 0x6a6458); i.paint(P([[8, 13.4], [8.8, 14.4], [8, 15], [7.2, 14.4]]), C.water);
  },
  CAVERN_ECHO: i => {
    i.paint(P([[1.6, 14], [1.6, 7], [3.6, 3], [7, 2], [9.4, 4], [9.6, 14]]), ramp(0x6a6458), {spec: [1, 1]});
    i.paint(P([[3.4, 14], [3.6, 8], [5.4, 5.4], [7.6, 7], [7.8, 14]]), C.soot);
    for (const r of [2, 3.6, 5.2]) i.paint(AR(8.6, 9, r, -PI * .32, PI * .32, .8), ramp(0xc7e8f0));
  },
  GROTTO_CAP: i => {
    i.paint(R(6.6, 9, 2.8, 5.4), C.cream); i.paint(P([[1.6, 9.6], [3, 5], [8, 2.6], [13, 5], [14.4, 9.6]]), ramp(0x8a5a36), {spec: [3, 1]});
    i.mark(R(2, 9, 12, .8), 0xd8c8a8); i.dots([[5, 6], [9, 4.6], [11, 7]], 0xe8dcc4);
  },
  SPOREBURST_SAC: i => {
    i.paint(D(8, 9.4, 4.6), ramp(0x8a6aa0), {spec: [1, 1]}); i.mark(PA([[6, 6], [8, 8], [10.4, 6.2]], .7), 0x4a3060);
    i.dots([[3, 3], [5, 2], [8, 1.6], [11, 2], [13, 3.6], [2, 6], [14, 6.4], [6.4, 3.6], [9.6, 3.6]], 0xa9d97c);
  },
  PUMP_VALVE: i => {
    i.paint(R(6.8, 10, 2.4, 4.4), C.iron); i.paint(R(3.4, 13, 9.2, 1.6), C.iron);
    i.paint(ER(8, 6.6, 5, 5, 1.4), ramp(0xb03a2a), {spec: [1, 0]});
    i.paint(U(L(3.6, 6.6, 12.4, 6.6, 1), L(8, 2.2, 8, 11, 1)), ramp(0xb03a2a)); i.paint(D(8, 6.6, 1.3), C.steel);
  },
  FLOODED_LANTERN: i => {
    i.paint(ER(8, 2.2, 1.5, 1.3, .8), C.iron); i.paint(R(4.4, 3.6, 7.2, 1.4), C.iron);
    i.paint(R(4.8, 5, 6.4, 8.2), C.glass, {spec: [1, 1]}); i.paint(R(4.8, 9, 6.4, 4.2), C.water); i.paint(R(4.4, 13, 7.2, 1.4), C.iron);
    i.mark(U(L(5.5, 5, 5.5, 13, .6), L(10.5, 5, 10.5, 13, .6)), 0x5c6470); i.dots([[7, 10], [9, 11.6]], 0xcfe8ff);
  },
  RESONANT_CRYSTAL: i => {
    i.paint(P([[8, 1.4], [10, 4], [10, 13], [6, 13], [6, 4]]), C.amethyst, {spec: [1, 2]});
    i.paint(P([[4, 5.6], [5.6, 7], [5.6, 13.4], [2.6, 13.4], [2.6, 7]]), ramp(0x7448b0)); i.paint(P([[12, 5.6], [13.4, 7], [13.4, 13.4], [10.4, 13.4], [10.4, 7]]), ramp(0x7448b0));
    i.mark(L(7.5, 3, 7.5, 12, .6), 0xc8a8f0);
  },
  CRYSTAL_LATTICE: i => {
    i.paint(P([[8, 1.6], [14.4, 8], [8, 14.4], [1.6, 8]]), C.aqua, {spec: [2, 2]});
    i.mark(U(L(4.8, 4.8, 11.2, 11.2, .6), L(11.2, 4.8, 4.8, 11.2, .6), L(8, 1.8, 8, 14.2, 1.1), L(1.8, 8, 14.2, 8, 1.1)), 0xd6fbff);
  },
  CHITIN_PLATE: i => {
    i.paint(E(8, 8.6, 5.4, 5.8), ramp(0x3e5a32), {spec: [2, 2]});
    i.mark(L(8, 3, 8, 14.2, 1.1), 0x1e2e18); i.mark(U(AR(8, 4, 4.6, PI * .15, PI * .85, .6), AR(8, 8, 5, PI * .15, PI * .85, .6)), 0x26381e);
    i.paint(U(L(4, 3, 2.4, 1.6, .7), L(12, 3, 13.6, 1.6, .7)), C.soot);
  },
  VENOM_GLAND: i => {
    i.paint(E(8, 6.6, 5, 4.4), ramp(0x6a3a7a), {spec: [2, 1]}); i.mark(U(D(6, 6, 1), D(10, 7, .8)), 0x9a6aaa);
    i.paint(P([[6.6, 9.8], [9.4, 9.8], [8, 13]]), C.bone); i.paint(P([[8, 13], [8.8, 14.2], [8, 15], [7.2, 14.2]]), ramp(0x7ac83a));
  },
  MARROW_FLUTE: i => {
    i.paint(L(3.4, 12.6, 12.6, 3.4, 2.4), C.bone, {spec: [1, 1]}); i.paint(U(D(2.8, 13.2, 1.5), D(13.2, 2.8, 1.5)), C.bone);
    i.dots([[6, 9], [8, 7], [10, 5]], 0x4a3a2a);
  },
  BONE_DICE: i => {
    i.paint(R(2, 6.6, 6.6, 6.6), C.bone, {spec: [1, 1]}); i.dots([[3.6, 8], [5.4, 10], [7, 12]], 0x2a1e18);
    i.paint(P([[8.6, 3], [13.4, 1.8], [14.6, 6.6], [9.8, 7.8]]), C.cream, {spec: [1, 1]}); i.dots([[11.6, 4.8], [10.6, 3.4], [12.6, 6.2]], 0x2a1e18);
  },
  SLING_STONE: i => {
    i.paint(PA([[2.2, 2], [5.4, 8.6]], .9), C.straw); i.paint(PA([[13.8, 2], [10.6, 8.6]], .9), C.straw);
    i.paint(E(8, 10, 3.8, 2.8), C.leather, {grain: 'x', spec: [1, 1]}); i.paint(D(8, 9, 1.9), ramp(0x8f8f94));
  },
  QUENCH_STONE: i => {
    i.paint(P([[2.6, 9], [4, 4.6], [8, 3], [12, 4.4], [13.6, 9], [11.6, 13], [6.4, 13.6], [3.4, 12]]), ramp(0x6f9fc8), {spec: [2, 1]});
    i.mark(PA([[4, 8], [6, 7], [8, 8.4], [10, 7], [12, 8]], .7), 0xe0f4ff); i.dots([[13.4, 2], [12, 1.6], [14, 4]], 0xaed7ff);
  },
  SPRAY_VEIL: i => {
    i.paint(P([[3, 1.6], [13, 1.6], [14, 6], [12.6, 10], [13.6, 14.2], [10.6, 12.4], [8, 14.4], [5.4, 12.4], [2.4, 14.2], [3.4, 10], [2, 6]]), ramp(0x9fd0e4), {spec: [2, 1]});
    i.mark(U(PA([[5.5, 2], [5, 6], [6, 10], [5.5, 12]], .8), PA([[10.5, 2], [11, 6], [10, 10], [10.5, 12]], .8)), 0xd8f0fa);
    i.dots([[8, 5], [7, 8], [9, 10], [8, 12]], 0xffffff); i.dots([[3, 3], [12, 3]], 0xff9a3a);
  },
  LABYRINTH_CHALK: i => {
    i.paint(L(2.8, 13.2, 7.4, 8.6, 2.6), ramp(0xeeeeea), {spec: [1, 1]});
    i.paint(PA([[8.4, 7.6], [12.8, 3.2]], 1.4), ramp(0xd8dce4)); i.paint(P([[14, 2], [14, 6], [10, 2]]), ramp(0xd8dce4));
  },
  BLAST_DAMPER: i => {
    i.paint(R(2.4, 2.4, 11.2, 11.2), ramp(0x8a8f6a), {spec: [1, 1]});
    i.mark(U(L(2.6, 6.5, 13.4, 6.5, .6), L(2.6, 9.5, 13.4, 9.5, .6), L(6.5, 2.6, 6.5, 13.4, .6), L(9.5, 2.6, 9.5, 13.4, .6)), 0x5a5e40);
    i.dots([[4, 4], [8, 4], [12, 4], [4, 8], [8, 8], [12, 8], [4, 12], [8, 12], [12, 12]], 0xd0d4a8);
  },
  FUSE_SNIPS: i => {
    i.paint(L(4, 5, 11, 12, 1.5), C.steel); i.paint(L(11, 5, 4, 12, 1.5), C.steel);
    i.paint(ER(3.4, 13.2, 1.8, 1.8, .9), ramp(0xc8402a)); i.paint(ER(12.6, 13.2, 1.8, 1.8, .9), ramp(0xc8402a));
    i.paint(PA([[1.6, 2.4], [6, 4.4], [10, 2.6], [14.4, 4.2]], .8), ramp(0xd0302a)); i.dots([[14.4, 3]], 0xffb13a);
  },
  SWITCHMANS_FLAG: i => {
    i.paint(L(3.6, 2, 3.6, 14.6, 1.2), C.darkWood);
    i.paint(P([[4.2, 2.4], [13.6, 2.4], [13.6, 8.6], [4.2, 8.6]]), ramp(0xc82a2a), {spec: [1, 1]});
    i.mark(P([[4.4, 2.6], [13.4, 8.4], [4.4, 8.4]]), 0xf0e8e0);
  },
  BRAKE_LEVER: i => {
    i.paint(R(2.6, 11, 10.8, 3.4), C.darkIron, {spec: [1, 0]}); i.paint(D(5, 11, 1.6), C.iron);
    i.paint(L(5, 11, 11.6, 3.4, 1.3), C.iron); i.paint(E(12.2, 2.8, 1.8, 1.4), ramp(0xc8402a));
  },
  SULFUR_SALVE: i => {
    i.paint(E(8, 11.4, 5.6, 2.6), C.steel); i.paint(R(2.4, 8.6, 11.2, 2.8), C.steel);
    i.paint(E(8, 8.6, 5.6, 2.4), ramp(0xd8c63a), {spec: [2, 1], grain: 'dots'});
    i.paint(E(8, 3.6, 5, 1.8), C.silver, {spec: [1, 0]});
  },
  SPRING_FLASK: i => {
    i.paint(R(6.8, 1.6, 2.4, 2), C.wood); i.paint(R(7, 3.4, 2, 2.4), C.glass);
    i.paint(D(8, 10, 4.4), C.glass, {spec: [2, 2]}); i.paint(S(D(8, 10, 3.4), R(3, 5, 10, 4)), C.water);
    i.dots([[5, 4], [11, 4.6], [12, 2.6]], 0xe8f4ff);
  },
  QUARRY_WEDGE: i => {
    i.paint(P([[3, 2.4], [13, 2.4], [8, 14.4]]), C.steel, {spec: [3, 1]}); i.paint(R(3, 2.4, 10, 2), C.darkIron);
    i.mark(L(8, 5, 8, 12, 1.1), 0x5c6a7c); i.dots([[2, 7], [14, 6], [2.6, 9.6], [13.4, 10]], 0x8f8778);
  },
  WORM_LURE: i => {
    i.paint(L(9, 1.6, 9, 9.4, 1.2), C.silver); i.paint(AR(6.6, 9.4, 2.4, 0, PI * 1.05, 1.2), C.silver); i.paint(P([[3.8, 9.2], [4.6, 6.8], [5.4, 9.4]]), C.silver);
    i.paint(PA([[11, 4], [13, 6], [11.4, 8], [13.4, 10.4], [11.6, 13]], 1.4), C.rose);
  },
  FORGE_TEMPER: i => {
    i.paint(P([[1.6, 5], [12, 5], [14.4, 3.6], [14.4, 7], [11, 8.6], [10, 11], [12, 13.8], [4, 13.8], [6, 11], [5, 8.6], [1.6, 7.4]]), C.darkIron, {spec: [2, 1]});
    i.paint(R(4, 2.6, 7, 2.2), ramp(0xff6a1c)); i.dots([[6, 3.4], [9, 3.2]], 0xffe58a);
  },
  RIVERBED_PEBBLE: i => {
    i.paint(E(8, 8.6, 5.6, 4.2), ramp(0x7a8c96), {spec: [2, 1]});
    i.mark(U(L(3.6, 7.4, 12.4, 9, .6), L(4.4, 10.4, 11.6, 11.4, .6)), 0xc8d6de);
    i.paint(ER(8, 13.6, 6, 1.4, .7), C.water);
  },
  RUST_EATER_TOOTH: i => {
    i.paint(P([[5, 3], [11, 2], [12.4, 5], [11.8, 9], [9.6, 12.4], [6.4, 14.4], [7.8, 10.8], [8.6, 7.4], [7, 4.6]]), C.bone, {spec: [3, 1]});
    i.paint(U(P([[5, 3], [6.4, 1.2], [7.6, 3.2]]), P([[9.4, 2.4], [11, .8], [11.6, 2.6]])), ramp(0xb08a6a));
    i.dots([[10, 5], [11, 7], [9, 9], [10, 8], [8, 11]], 0xa0522d);
  },
  GEODE_HEART: i => {
    i.paint(D(8, 8, 6), ramp(0x8a8278), {grain: 'dust', spec: [1, 1]}); i.paint(D(8, 8, 4.4), ramp(0xd8d0e8)); i.paint(D(8, 8, 3.2), C.amethyst, {spec: [1, 1]});
    i.dots([[6, 7], [9, 6], [10, 9], [7, 10], [8, 8]], 0xe8d8ff);
  },
  ROOTDRINKER: i => {
    i.paint(PA([[8, 1.6], [6, 4], [9, 7], [6.6, 10], [8, 12.2]], 1.8), C.root);
    i.paint(PA([[6, 4], [3, 5], [2, 7.4]], .9), C.root); i.paint(PA([[9, 7], [12, 7.6], [13.4, 10]], .9), C.root);
    i.paint(P([[8, 12], [9.4, 13.6], [8, 15], [6.6, 13.6]]), C.water);
  },
  SMUGGLERS_SHIV: i => {
    i.paint(P([[13.6, 2.4], [12.4, 6.4], [7.4, 10], [6, 8.6], [9.6, 3.6]]), C.steel, {spec: [3, 1]});
    i.paint(L(6.4, 9.6, 3, 13, 2.2), ramp(0x8a7a5a), {grain: 'x'}); i.mark(U(L(5.6, 10.4, 6.8, 11.6, .5), L(4.4, 11.6, 5.6, 12.8, .5)), 0x4a3a20);
  },
  CONTRABAND_PLATE: i => {
    i.paint(P([[2.4, 3], [13.6, 2.4], [13, 13.6], [3, 13]]), C.iron, {spec: [1, 1]});
    i.dots([[3.6, 4], [12.4, 3.6], [12, 12.4], [4, 12]], 0xc9d1dc);
    i.paint(D(8.6, 7.4, 1.6), ramp(0x5a6270)); i.dots([[8.6, 7.4]], 0x23262e); i.mark(U(L(6.2, 9.8, 4.6, 11, .6), L(10.6, 9, 12, 10.2, .6)), 0x5a6270);
  },
  COLUMN_CAPITAL: i => {
    i.paint(R(3.6, 4.2, 8.8, 2.6), C.salt, {spec: [1, 0]}); i.paint(U(D(3.6, 7.6, 2.4), D(12.4, 7.6, 2.4)), C.salt);
    i.mark(U(RG(3.6, 7.6, 1.6, 1), RG(12.4, 7.6, 1.6, 1)), 0x9aa8b4);
    i.paint(R(5.6, 9, 4.8, 5.4), ramp(0xc8ced6), {grain: 'v'}); i.paint(R(2.6, 2.4, 10.8, 1.8), ramp(0xd6dce4));
  },
  BURROW_EMBER: i => {
    i.paint(E(8, 10, 6, 4.4), ramp(0x6b4a2e), {grain: 'dots', spec: [2, 1]});
    i.paint(E(8, 9.4, 3, 2.2), C.ember); i.paint(D(8, 9.2, 1.2), ramp(0xffc04a));
    i.dots([[6, 4], [9, 3], [11, 5]], 0xff9a2e);
  },
  SMOLDERING_ZEAL: i => {
    i.paint(ER(8, 8.6, 5.4, 5.4, 1.4), C.darkIron, {spec: [1, 0]});
    flame(i, 8, 3.2, 10.2, 3.2);
    i.dots([[3, 2.4], [13, 2.6]], 0xff9a2e);
  },
  TYRANTS_TALLY: i => {
    i.paint(R(2, 4.6, 12, 8), C.wood, {grain: 'h', spec: [1, 1]});
    for (const x of [3.5, 5.5, 7.5, 9.5]) i.mark(L(x, 6, x, 11.2, .7), 0x2a1a0e);
    i.mark(L(2.6, 10.2, 10.6, 6.6, .7), 0x2a1a0e);
    i.paint(P([[10.4, 4.4], [11.4, 2.2], [12.2, 3.8], [13, 2.2], [14, 4.4], [14, 5.8], [10.4, 5.8]]), C.gold);
  },
  FOREMANS_BELL: i => {
    i.paint(R(7, 1.4, 2, 4), C.darkWood); i.paint(D(8, 1.8, 1.2), C.darkWood);
    i.paint(P([[8, 5], [10.4, 6], [11, 9], [12.8, 12.4], [3.2, 12.4], [5, 9], [5.6, 6]]), C.steel, {spec: [2, 2]});
    i.paint(R(2.8, 12, 10.4, 1.6), C.brass); i.paint(D(8, 14, 1), C.darkIron);
  },
  // ------------------------------------------------------------------------------------------- Floor III: The Abyssal Citadel (78..101)
  SENTINEL_RIVET: i => {
    i.paint(P([[5.4, 1.6], [10.6, 1.6], [14.4, 5.4], [14.4, 10.6], [10.6, 14.4], [5.4, 14.4], [1.6, 10.6], [1.6, 5.4]]), ramp(0x3a3640), {spec: [2, 2]});
    i.mark(RG(8, 8, 4.8, 4.2), 0x24222a); i.paint(P([[8, 5], [11, 8], [8, 11], [5, 8]]), ramp(0xb02030), {spec: [1, 1]});
  },
  COURTIERS_CLOAK: i => {
    i.paint(P([[5, 2.4], [11, 2.4], [12.4, 7], [14, 14.4], [2, 14.4], [3.6, 7]]), ramp(0x5a2a7a), {grain: 'v', spec: [2, 2]});
    i.paint(R(4.4, 1.6, 7.2, 2), ramp(0x8a5aaa)); i.paint(D(8, 3.6, 1.4), C.gold);
  },
  KNEELERS_CUSHION: i => {
    i.paint(P([[3, 4], [13, 4], [14, 8.6], [13, 13], [3, 13], [2, 8.6]]), ramp(0xa82a3a), {spec: [2, 1]});
    i.mark(U(L(3.4, 8.5, 12.6, 8.5, .6), L(7.5, 4.4, 7.5, 12.6, .6)), 0x7a1a28);
    for (const [x, y] of [[2.6, 3.6], [13.4, 3.6], [2.6, 13.4], [13.4, 13.4]]) i.paint(D(x, y, 1), C.gold);
  },
  SANGUINE_MERLON: i => {
    i.paint(S(R(2.4, 3, 11.2, 11), R(6.2, 2.6, 3.6, 3.4)), ramp(0x8a8278), {spec: [1, 1]});
    i.mark(U(L(2.6, 9.5, 13.4, 9.5, .6), L(8.5, 9.6, 8.5, 13.8, .6), L(4.5, 6.2, 4.5, 9.2, .6), L(11.5, 6.2, 11.5, 9.2, .6)), 0x5c564e);
    i.paint(U(E(7.4, 7.4, .8, 1.6), E(10.4, 11.4, .8, 2), E(4.6, 12, .7, 1.4)), C.blood);
  },
  RAMPART_STONE: i => {
    i.paint(R(2, 4, 12, 9.6), ramp(0x9a948a), {spec: [1, 1]});
    i.mark(U(L(2.2, 7.5, 13.8, 7.5, .6), L(2.2, 10.5, 13.8, 10.5, .6), L(5.5, 4.2, 5.5, 7.2, .6), L(10.5, 4.2, 10.5, 7.2, .6), L(7.5, 7.6, 7.5, 10.2, .6), L(12.5, 7.6, 12.5, 10.2, .6), L(4.5, 10.6, 4.5, 13.4, .6), L(9.5, 10.6, 9.5, 13.4, .6)), 0x5c564e);
    i.paint(P([[6, 1.4], [10, 1.4], [10, 3.8], [8, 5.4], [6, 3.8]]), ramp(0x2f5fb0), {spec: [1, 0]});
  },
  VOID_THORN: i => {
    i.paint(P([[3, 14], [5, 9], [9, 5], [13.6, 1.6], [11.6, 6.4], [7.6, 11], [5, 14.4]]), ramp(0x3a1f5a), {spec: [2, 1]});
    i.paint(P([[7.6, 8.6], [4.6, 6.6], [8.4, 7.2]]), ramp(0x3a1f5a)); i.paint(P([[10, 6.6], [13, 8], [10.6, 8.6]]), ramp(0x3a1f5a));
    i.dots([[3, 4], [5, 2.6], [13, 11], [11, 13]], 0xb07ae8);
  },
  GRAVITY_SEED: i => {
    i.paint(E(8, 7.6, 3.4, 4.6), VOID, {spec: [1, 1]}); i.mark(L(7.5, 3.6, 7.5, 11.6, .6), 0x2a1640);
    i.paint(ER(8, 8, 6.2, 2, .7), ramp(0x9a7ac8));
    i.paint(U(P([[6.4, 12.6], [9.6, 12.6], [8, 14.6]]), R(7.4, 12, 1.2, 1)), ramp(0x9a7ac8));
  },
  OBSIDIAN_SPLINTER: i => {
    i.paint(P([[2, 14.4], [4, 10], [11, 3], [14.4, 1.6], [12.4, 5], [6, 12]]), C.obsidian, {spec: [3, 2]});
    i.mark(L(4.4, 11, 12, 3.6, .6), 0x7a5ab6); i.dots([[12.4, 2.6], [3, 13]], 0xcdb8ff);
  },
  SOULFIRE_WICK: i => {
    i.paint(E(8, 13.4, 4.6, 1.3), C.darkIron); i.paint(R(5.8, 7.4, 4.4, 5.8), C.waxBlue, {spec: [1, 1]});
    i.paint(L(7.5, 5.8, 7.5, 7.4, .8), C.soot);
    flame(i, 8, 1.2, 5, 2, SOUL, SOUL_HOT);
  },
  SOULFIRE_CENSER: i => {
    i.paint(P([[2.6, 7.6], [13.4, 7.6], [11.6, 11], [4.4, 11]]), C.darkIron, {spec: [2, 0]}); i.paint(R(7, 11, 2, 2.4), C.darkIron); i.paint(R(4.6, 13, 6.8, 1.4), C.darkIron);
    i.paint(P([[4, 7.8], [5, 4], [6.6, 6], [8, 1.6], [9.4, 6], [11, 4], [12, 7.8]]), SOUL); i.paint(P([[6.4, 7.8], [8, 4.4], [9.6, 7.8]]), SOUL_HOT);
  },
  PENANCE_CHAIN: i => {
    i.paint(ER(4.6, 4.4, 2.8, 2, 1), C.iron, {spec: [1, 0]}); i.paint(ER(7.4, 7.4, 2, 2.8, 1), C.iron); i.paint(ER(10.4, 10.4, 2.8, 2, 1), C.iron);
    i.paint(S(ER(12.8, 13, 2, 2, 1), R(13.4, 10.8, 3, 2.4)), C.darkIron);
    i.dots([[5, 3], [8, 8], [11, 11]], 0x985a32); i.dots([[14, 10], [14, 11]], 0x8a8f98);
  },
  SHACKLE_LINK: i => {
    i.paint(S(ER(6.6, 8.4, 4.6, 4.6, 1.8), R(5.6, 3, 2, 2.6)), C.darkIron, {spec: [1, 1]}); i.paint(D(6.6, 13, 1.2), C.iron);
    i.paint(ER(12.4, 5, 1.8, 2.6, .9), C.iron); i.paint(L(10.6, 6, 9.4, 6.6, .9), C.iron);
  },
  SOUL_EMBER: i => {
    i.paint(P([[8, 1.6], [11.4, 6], [12, 10], [10, 13.6], [8, 14.4], [6, 13.6], [4, 10], [4.6, 6]]), SOUL, {spec: [2, 3]});
    i.paint(P([[8, 6], [10, 9.4], [9.4, 12], [8, 12.6], [6.6, 12], [6, 9.4]]), ramp(0xc8f6ff));
    i.dots([[7, 10], [9, 10]], 0x1d3a5c);
  },
  SKYFALL_TALON: i => {
    i.paint(E(8, 4, 3.6, 2.6), C.gold, {spec: [1, 1]});
    i.paint(PA([[5.4, 5.4], [3.6, 9], [3.8, 12.4], [5.4, 14]], 1.4), C.bone); i.paint(PA([[8, 6.4], [8, 10.6], [9, 14]], 1.4), C.bone); i.paint(PA([[10.6, 5.4], [12.4, 9], [12.2, 12.4], [10.6, 14]], 1.4), C.bone);
    i.dots([[5.6, 14.4], [9.2, 14.6], [10.4, 14.4]], 0x2a1e18);
  },
  CROWDS_ROAR: i => {
    i.paint(D(8, 8, 5.6), ramp(0xc89a3a), {spec: [1, 1]});
    i.paint(E(8, 10, 3, 2.4), C.blood); i.dots([[6.4, 8.6], [7.6, 8.4], [8.6, 8.4], [9.8, 8.6]], 0xf4ecd0);
    i.mark(U(D(5.8, 5.6, .8), D(10.2, 5.6, .8)), 0x2a1a0e);
    i.dots([[1.8, 4], [14.2, 4], [1.8, 12], [14.2, 12]], 0xfff3b8);
  },
  GLADIATORS_TORC: i => {
    i.paint(S(ER(8, 8, 5.6, 5, 1.8), P([[6.6, 14.4], [9.4, 14.4], [8, 9.6]])), C.gold, {spec: [2, 0], grain: 'x'});
    i.paint(D(6, 12, 1.5), C.gold); i.paint(D(10, 12, 1.5), C.gold);
  },
  HEADSMANS_LEDGER: i => {
    i.paint(R(3, 2, 10, 12.4), C.waxBlack, {spec: [1, 1]}); i.paint(R(3, 2, 1.6, 12.4), ramp(0x1a1620));
    i.mark(U(L(6.4, 11.6, 10.4, 4.8, .8), P([[9, 4], [12, 5.6], [10.6, 7.6], [8.6, 6]])), 0xb02030);
  },
  FERRYMANS_LANTERN: i => {
    i.paint(AR(8, 3.4, 2.2, PI, PI * 2, .9), C.iron); i.paint(L(5.8, 3.4, 5.8, 5, .9), C.iron); i.paint(L(10.2, 3.4, 10.2, 5, .9), C.iron);
    i.paint(D(8, 9.6, 4.6), ramp(0xe89b1b), {spec: [1, 1]}); i.mark(U(L(3.4, 9.5, 12.6, 9.5, .6), L(7.5, 5.2, 7.5, 14, .6)), 0x6e4610);
    i.paint(R(5.6, 4.6, 4.8, 1.2), C.iron); i.dots([[6.6, 8]], 0xffe58a);
  },
  PEARL_INDEX: i => {
    i.paint(R(2.4, 4.4, 10, 9.6), C.paper, {spec: [1, 1]}); i.paint(R(4, 3, 2.4, 1.6), ramp(0x2fa56b)); i.paint(R(7.4, 3, 2.4, 1.6), ramp(0x3066cc)); i.paint(R(10.4, 3, 2, 1.6), ramp(0xcb2540));
    for (const y of [7.5, 9.5, 11.5]) i.mark(L(3.6, y, 9, y, .5), 0x9fb0c8);
    i.paint(D(11.6, 11.4, 2.4), ramp(0x2a8a7a), {spec: [1, 1]});
  },
  SERGEANTS_WHISTLE: i => {
    i.paint(PA([[2, 2], [4, 6], [6.6, 7.6]], 1), ramp(0xb02030));
    i.paint(U(D(9.6, 10.2, 3.4), R(6, 6.6, 6, 2.8)), C.silver, {spec: [1, 1]}); i.dots([[7.6, 7.4]], 0x23262e);
    i.mark(U(L(8.4, 9.4, 10.8, 11.6, .6), L(10.8, 9.4, 8.4, 11.6, .6)), 0x6a7480);
  },
  MUSTER_ROLL: i => {
    i.paint(R(3.4, 3.4, 9.2, 9.4), C.parchment, {spec: [1, 1]}); i.paint(U(E(8, 3, 5.4, 1.4), E(8, 13, 5.4, 1.4)), ramp(0xc8b47c));
    for (const y of [5.5, 7.5, 9.5]) i.mark(L(4.6, y, 11.4, y, .5), 0x6b5a3a);
    i.paint(D(10.6, 11, 1.6), C.waxRed);
  },
  FOUNDRY_SIGHTS: i => {
    i.paint(ER(8, 8, 6, 6, 1.6), C.darkIron, {spec: [1, 0]}); i.paint(U(L(8, 2.6, 8, 5.6, 1), L(8, 10.4, 8, 13.4, 1), L(2.6, 8, 5.6, 8, 1), L(10.4, 8, 13.4, 8, 1)), C.darkIron);
    i.dots([[8, 8]], 0xff5a2a);
  },
  DOOM_RIVET: i => {
    for (let k = 0; k < 8; k++) { const a = k * PI / 4; i.paint(P([[8 + 3.6 * Math.cos(a - .3), 8 + 3.6 * Math.sin(a - .3)], [8 + 6.2 * Math.cos(a), 8 + 6.2 * Math.sin(a)], [8 + 3.6 * Math.cos(a + .3), 8 + 3.6 * Math.sin(a + .3)]]), C.darkIron); }
    i.paint(D(8, 8, 4), ramp(0x7a1a20), {spec: [1, 1]}); i.dots([[8, 8]], 0x16121f);
  },
  SILVERED_RETORT: i => {
    i.paint(D(6, 10, 4), C.silver, {spec: [1, 1]}); i.paint(L(8, 7.6, 13.2, 3, 1.6), C.silver); i.paint(L(13.2, 3, 14, 4.6, 1.2), C.silver);
    i.paint(S(D(6, 10.6, 2.6), R(2, 6, 9, 3.8)), ramp(0x8fd2d6)); i.dots([[4.6, 8.6]], 0xffffff);
  },
  UNMARRED_REFLECTION: i => {
    i.paint(D(8, 6, 4.6), C.gold, {spec: [1, 1]}); i.paint(D(8, 6, 3.4), ramp(0xcfe8f4), {shade: 'flat'});
    i.paint(L(8, 10.4, 8, 14.4, 1.6), C.gold); i.dots([[6.6, 4.6], [7.4, 4]], 0xffffff);
  },
  ASHEN_CROWN_SHARD: i => {
    i.paint(P([[2.4, 12.6], [2.4, 5], [5.4, 8.4], [8, 3.4], [9.6, 7.6], [11, 6.4], [9.4, 10], [12.6, 12.6]]), ramp(0x9a8a5a), {spec: [2, 3]});
    i.paint(R(2.4, 11, 10, 2), ramp(0x6a6458)); i.mark(PA([[9.6, 7.6], [9, 9.6], [10.2, 11]], .6), 0x2a2420);
    i.dots([[4, 10], [7, 11.6], [12, 4], [13, 6]], 0xc8c4bc);
  },
  THRONE_ASH: i => {
    i.paint(P([[1.6, 13.6], [4, 9], [7, 6], [9.4, 7], [12, 10], [14.4, 13.6]]), C.ash, {grain: 'dust', spec: [3, 2]});
    i.dots([[8, 9], [5, 12], [11, 12]], 0xdcab2e); i.dots([[7, 3], [9, 2], [10, 4]], 0xa8a6b0);
  },
  GATEBREAKER_SIGIL: i => {
    i.paint(P([[2.4, 14], [2.4, 5], [8, 1.6], [13.6, 5], [13.6, 14]]), ramp(0x5a1e24), {spec: [2, 2]});
    i.mark(U(L(4.5, 5, 4.5, 13.6, .7), L(8, 3.4, 8, 13.6, 1.1), L(11.5, 5, 11.5, 13.6, .7), L(2.6, 8.5, 13.4, 8.5, .7), L(2.6, 11.5, 13.4, 11.5, .7)), 0xd8a03a);
    i.mark(PA([[9.6, 2.6], [7, 6.6], [9.4, 9], [6.6, 13.8]], .9), 0x16121f);
  },
  ABYSSAL_KEYSTONE: i => {
    i.paint(P([[2.4, 2.4], [13.6, 2.4], [11, 13.6], [5, 13.6]]), ramp(0x4a4458), {spec: [1, 1]});
    i.mark(U(L(8, 4.4, 8, 11.6, 1.2), AR(8, 8, 2.6, PI * .1, PI * .9, .8), L(6, 5.5, 10, 5.5, .8)), 0xa060ff);
  },
  PYRE_URN: i => {
    i.paint(P([[6, 4.6], [10, 4.6], [9.4, 6], [12, 8.6], [11.4, 12.4], [9, 14.4], [7, 14.4], [4.6, 12.4], [4, 8.6], [6.6, 6]]), ramp(0x9a5a2e), {spec: [2, 2]});
    i.mark(R(4.4, 9.4, 7.2, .8), 0x2a1a0e); i.paint(R(5.6, 4, 4.8, 1.2), ramp(0x6a3a1e));
    flame(i, 8, 1, 3.4, 1.8);
  },
  STAR_IRON_LENS: i => {
    i.paint(D(7, 7, 5.4), ramp(0x4a4e5c), {spec: [1, 1]}); i.paint(D(7, 7, 4), ramp(0x2f4fa8), {shade: 'flat'});
    i.mark(P([[7, 4], [7.8, 6.2], [10, 7], [7.8, 7.8], [7, 10], [6.2, 7.8], [4, 7], [6.2, 6.2]]), 0xeef4ff);
    i.paint(L(10.8, 10.8, 14, 14, 1.6), ramp(0x4a4e5c));
  },
  STARFALL_SHARD: i => {
    i.paint(P([[10.4, 2.4], [11.4, 5], [14.2, 5.2], [12, 7], [12.8, 9.8], [10.4, 8.2], [8, 9.8], [8.8, 7], [6.6, 5.2], [9.4, 5]]), C.gold, {spec: [2, 2]});
    i.paint(PA([[8.4, 8.6], [5, 11.4], [2, 13.8]], 1.6), ramp(0xf0c860)); i.paint(PA([[7.2, 7], [3.6, 8.6]], .9), ramp(0xf0c860));
  },
  GRAVE_WIND_SHROUD: i => {
    i.paint(P([[3, 3], [13, 3], [12, 9], [13.6, 14], [10, 12.6], [8, 14.4], [6, 12.6], [2.4, 14], [4, 9]]), ramp(0x6a8a8a), {grain: 'v', spec: [1, 1]});
    i.mark(U(AR(8, 6.4, 3, PI, PI * 2.4, .7), AR(8, 6.4, 1.4, 0, PI * 1.5, .6)), 0xd8f0ec);
  },
  MOLTEN_CORE: i => {
    i.paint(D(8, 8, 5.8), ramp(0x3a2a28), {spec: [1, 1]});
    i.paint(D(8, 8, 3.6), ramp(0xff7a1c), {spec: [1, 1]}); i.paint(D(8, 8, 1.8), ramp(0xffd04a));
    i.mark(U(L(2.6, 6, 5, 7.4, .7), L(11, 8.6, 13.4, 10, .7), L(7, 2.4, 7.6, 4.6, .7)), 0xff9a2e);
  },
  CURSED_COIN: i => {
    i.paint(D(8, 8, 5), C.gold, {spec: [1, 1]}); i.mark(RG(8, 8, 4.2, 3.6), 0x9a6f14);
    i.mark(U(D(8, 7.4, 2), R(6.8, 8.4, 2.4, 1.8)), 0x3a2a4a); i.dots([[7, 7], [9, 7]], 0xdcab2e);
    i.dots([[2.4, 3], [13.6, 4], [2, 12], [14, 12.6], [8, 1.6]], 0xa060ff);
  },
  BASTION_STANDARD: i => {
    i.paint(L(8, 1.6, 8, 14.6, 1.2), C.darkWood); i.paint(L(3, 2.6, 13, 2.6, 1), C.darkWood);
    i.paint(P([[3.6, 3], [12.4, 3], [12.4, 10], [8, 12.6], [3.6, 10]]), ramp(0x2f5fb0), {spec: [1, 1]});
    i.mark(P([[8, 4.6], [10.4, 7], [8, 9.8], [5.6, 7]]), 0xdcab2e);
  },
  BASTION_HORN: i => {
    i.paint(PA([[2, 5], [5, 7], [8.4, 8], [11, 7], [13, 4.6]], 2.2), C.bone, {spec: [1, 1]});
    i.paint(E(13.4, 4, 1.4, 2.2), ramp(0xc8b48a)); i.paint(D(2, 5, 1.1), C.darkIron);
    i.mark(U(L(5.4, 5.6, 5, 8.2, .7), L(9.5, 6.8, 9.5, 9.2, .7)), 0x6a6458);
    i.paint(PA([[4, 8.6], [8, 11.6], [12, 8.6]], .8), C.leather);
  },
  VICTORS_LAUREL: i => {
    const leaves = [];
    for (let k = 0; k < 6; k++) { const a1 = PI * (.62 + k * .15), a2 = PI * (2.38 - k * .15); leaves.push(leaf(8 + 4.8 * Math.cos(a1), 7.6 + 4.8 * Math.sin(a1), a1), leaf(8 + 4.8 * Math.cos(a2), 7.6 + 4.8 * Math.sin(a2), a2)); }
    i.paint(U(...leaves), ramp(0x5f9a36), {spec: [2, 2]});
    i.paint(U(P([[8, 12.2], [6.2, 14.6], [7.6, 14.4]]), P([[8, 12.2], [9.8, 14.6], [8.4, 14.4]]), D(8, 12.4, 1)), ramp(0xc82a2a));
    i.dots([[4.6, 4.4], [11.4, 4.4], [3.4, 8], [12.6, 8]], 0xf4c430);
  },
  // ------------------------------------------------------------------------------------------- the Floor Guardians' trophies (stone sword 74..76)
  MERCYS_LAST_KEY: i => {
    i.paint(ER(4.6, 4.6, 3.4, 3.4, 1.6), C.darkIron, {spec: [1, 0]}); i.paint(D(4.6, 4.6, 1.2), ramp(0xb02030));
    i.paint(L(6.8, 6.8, 13.2, 13.2, 1.8), C.iron);
    i.paint(U(L(10.4, 10.4, 12.4, 8.4, 1.6), L(12.2, 12.2, 14.2, 10.2, 1.6)), C.iron);
    i.dots([[2.6, 1.6], [1.6, 2.6]], 0xd8b45c);
  },
  DEEPBREAKER: i => {
    i.paint(L(2.6, 13.4, 9.6, 6.6, 1.6), C.darkWood, {grain: 'v'}); i.paint(D(2.6, 13.4, 1.2), C.darkIron);
    i.paint(P([[6, 4.4], [10, 1.2], [14.4, 6.6], [10.4, 9.8]]), BASALT, {spec: [2, 1]});
    i.mark(L(8, 3.6, 12.4, 8.6, .7), 0xff7a1c);
  },
  ABYSSAL_SCEPTRE: i => {
    i.paint(L(3.4, 13.4, 9.2, 7.6, 1.4), C.gold); i.paint(D(3.2, 13.6, 1.1), C.gold);
    i.paint(D(11, 5, 3.4), VOID, {spec: [1, 1]});
    i.paint(U(P([[8, 2.6], [9, .8], [9.4, 3]]), P([[12.8, 1.6], [14.6, 1.4], [13.4, 3.4]]), P([[13.6, 7.4], [15, 8.6], [13, 8.8]])), C.gold);
    i.dots([[10, 4], [11, 3.4]], 0xd8b8ff);
  }
};

module.exports = {ICONS, make, draw: name => make(ICONS[name])};
