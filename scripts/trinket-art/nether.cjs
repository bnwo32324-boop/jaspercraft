'use strict';
/* Icons for the Nether's carried and off-hand trinkets (JasprNether Items ids, upper-cased). */
const {M, Icon, ramp} = require('./engine.cjs');
const C = require('./palette.cjs');
const P = M.poly, D = M.disc, R = M.rect, E = M.ellipse, L = M.line, PA = M.path, RG = M.ring, ER = M.ering, U = M.U, S = M.sub, AR = M.arc, I = M.inter;
const PI = Math.PI;
const make = draw => { const i = new Icon(); draw(i); return i; };
const heart = (cx, cy, s = 1) => U(D(cx - 2.3 * s, cy - 1.6 * s, 3.5 * s), D(cx + 2.3 * s, cy - 1.6 * s, 3.5 * s), P([[cx - 5.6 * s, cy - .9 * s], [cx + 5.6 * s, cy - .9 * s], [cx, cy + 6 * s]]));
const flame = (i, cx, top, h, w, outer = C.flame, inner = C.hot) => {
  i.paint(P([[cx, top], [cx + w, top + h * .5], [cx, top + h], [cx - w, top + h * .5]]), outer);
  i.paint(P([[cx, top + h * .3], [cx + w * .5, top + h * .6], [cx, top + h * .9], [cx - w * .5, top + h * .6]]), inner);
};

const ICONS = {
  BRIMSTONE_IDOL: i => {
    i.paint(R(4.4, 13, 7.2, 1.8), ramp(0x8a7a22));
    i.paint(P([[4.8, 13], [4.6, 7.6], [11.4, 7.6], [11.2, 13]]), ramp(0xd8c23a), {spec: [1, 1]});
    i.paint(D(8, 5.6, 3.3), ramp(0xe6d14a), {spec: [1, 1]});
    i.paint(P([[5, 3.6], [3.4, 1.4], [6.2, 2.6]]), C.ember); i.paint(P([[11, 3.6], [12.6, 1.4], [9.8, 2.6]]), C.ember);
    i.dots([[6.6, 5.4], [9.4, 5.4]], 0xd0301c); i.mark(L(6.6, 7.4, 9.4, 7.4, .7), 0x6b5a14); i.dots([[6, 10], [9, 11], [10, 9]], 0xa89418);
  },
  HEART_OF_CINDERS: i => {
    i.paint(heart(8, 9, .95), ramp(0x5a1f1a), {spec: [2, 1]});
    i.mark(PA([[8, 6], [7, 9], [9, 11.4]], 1), 0xff7a1c); i.mark(PA([[5, 7], [6.6, 9]], .8), 0xff9a2e); i.mark(PA([[11, 7], [9.8, 8.6]], .8), 0xff9a2e);
    flame(i, 8, 0.6, 4, 1.6);
  },
  HELLHOUND_COLLAR: i => {
    i.paint(ER(8, 8.4, 6, 5.4, 2.4), ramp(0x7a2820), {grain: 'x', spec: [3, 0]});
    i.dots([[3, 6], [4, 3.6], [6.4, 2.6], [9.6, 2.6], [12, 3.6], [13, 6]], 0xd5dae2);
    i.paint(D(8, 14, 1.4), C.gold);
  },
  EMBER_HEART: i => {
    i.paint(heart(8, 8.6, 1), ramp(0xe05a1c), {spec: [2, 1]}); i.paint(heart(8, 8.6, .55), ramp(0xffc04a));
    i.dots([[3, 3], [13, 4], [8, 1]], 0xffd96a);
  },
  WITHER_WARD: i => {
    i.paint(P([[8, 1.4], [13.6, 4.6], [13.6, 11.4], [8, 14.6], [2.4, 11.4], [2.4, 4.6]]), ramp(0x2f2b36), {spec: [2, 2]});
    i.mark(U(D(8, 6.6, 3.3), R(6, 8.4, 4, 2.8)), 0xe4deee);
    i.dots([[6, 6], [7, 6], [6, 7], [7, 7], [9, 6], [10, 6], [9, 7], [10, 7]], 0x16121f); i.dots([[8, 8.4]], 0x16121f);
    i.dots([[6.6, 10.6], [8.2, 10.6], [9.4, 10.6]], 0x16121f);
  },
  MAGMA_BAND: i => {
    i.paint(ER(8, 8, 6, 6, 2.6), ramp(0x4a2c2a), {grain: 'dots'});
    i.mark(U(L(3.4, 8, 5.4, 8.6, .8), L(10, 4, 11.4, 6, .8), L(9.6, 12.4, 11, 10.4, .8), L(5, 3.6, 7, 4.6, .8)), 0xff7a1c);
    i.paint(D(8, 2.4, 1.5), C.lava); i.dots([[8, 2]], 0xffe58a);
  },
  GHASTLY_PENDANT: i => {
    i.paint(AR(8, 5.6, 4.6, PI, 2 * PI, .9), C.silver);
    i.paint(P([[8, 5.4], [10.4, 8.6], [10.8, 11], [9.2, 13.2], [8, 13.6], [6.8, 13.2], [5.2, 11], [5.6, 8.6]]), ramp(0xe9f2f6), {spec: [2, 3]});
    i.paint(RG(8, 10.2, 3.6, 2.9), C.gold); i.dots([[7, 9], [8.6, 11]], 0xb8d4e4);
  },
  TYRANT_HEART: i => {
    i.paint(heart(8, 9.2, 1), ramp(0x7a1020), {spec: [2, 1]});
    i.paint(P([[4.4, 4.4], [4, 1.6], [6.2, 3.2], [8, .8], [9.8, 3.2], [12, 1.6], [11.6, 4.4]]), C.gold);
    i.mark(PA([[5.6, 8], [7.4, 10.4]], .9), 0xc43a4a); i.dots([[8, 5]], 0xfff3b8);
  },
  SCARAB_AMULET: i => {
    i.paint(ER(8, 2.4, 1.7, 1.7, .8), C.gold);
    i.paint(U(PA([[2, 6.4], [5, 8]], 1.1), PA([[1.6, 10], [4.6, 10.4]], 1.1), PA([[2.4, 14], [5.4, 12]], 1.1), PA([[14, 6.4], [11, 8]], 1.1), PA([[14.4, 10], [11.4, 10.4]], 1.1), PA([[13.6, 14], [10.6, 12]], 1.1)), C.darkIron);
    i.paint(E(8, 9.6, 3.9, 4.9), ramp(0x2aa6a0), {spec: [1, 1]}); i.paint(D(8, 4.6, 2), C.gold);
    i.paint(P([[6.4, 3.4], [5.6, 1.8], [7.2, 2.6]]), C.gold); i.paint(P([[9.6, 3.4], [10.4, 1.8], [8.8, 2.6]]), C.gold);
    i.mark(L(8, 6.6, 8, 14, .7), 0x146a66); i.dots([[6, 8.4], [10, 8.4]], 0xa7f0e8);
  },
  PHOENIX_FEATHER: i => {
    i.paint(P([[13.6, 1.4], [10.4, 2.2], [6.6, 5], [4.4, 9], [4, 13.4], [6.6, 11.4], [10.4, 8.6], [12.8, 4.6]]), ramp(0xf0701c), {spec: [3, 1]});
    i.mark(P([[13.6, 1.4], [10.6, 2.6], [12.4, 5.4]]), 0xffe58a); i.mark(L(13, 2, 4.4, 13, .9), 0xc43a1c);
    i.dots([[2, 14], [3, 11], [14, 8]], 0xffb13a);
  }
};
module.exports = {ICONS, make, draw: name => make(ICONS[name])};
