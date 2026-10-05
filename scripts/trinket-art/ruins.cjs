'use strict';
/* Icons for the Drownhollow relics (JasprRuins Trinkets) and the five Seals of the Great Door. Keys are the Trinket / Seal enum
 * names (seals are prefixed SEAL_). The Faceless Mask and the Crown of the Drowned Star are worn in the helmet slot, so they stay
 * on the skull and the diamond helmet they already are. */
const {M, Icon, ramp} = require('./engine.cjs');
const C = require('./palette.cjs');
const P = M.poly, D = M.disc, R = M.rect, E = M.ellipse, L = M.line, PA = M.path, RG = M.ring, ER = M.ering, U = M.U, S = M.sub, AR = M.arc;
const PI = Math.PI;
const make = draw => { const i = new Icon(); draw(i); return i; };

/** A round seal plaque on a ring, with an emblem drawn over it. */
function medal(i, r, emblem) {
  i.paint(ER(8, 2.6, 1.7, 1.7, .8), C.silver);
  i.paint(D(8, 8.4, 5.6), r, {spec: [1, 1]}); i.mark(RG(8, 8.4, 4.6, 4.1), r.lo);
  emblem(i);
}

const ICONS = {
  WARDSTONE: i => {
    i.paint(R(3.4, 12.6, 9.2, 2), ramp(0x666b78));
    i.paint(P([[4.4, 13], [4.4, 5], [6, 2.4], [10, 2.4], [11.6, 5], [11.6, 13]]), ramp(0x8a8f9b), {grain: 'dots', spec: [2, 2]});
    i.mark(U(ER(8, 6.8, 2.2, 2.2, .9), R(7.5, 9, 1, 3.4)), 0x59e0e6); i.dots([[8, 6.8]], 0xc8ffff);
  },
  TIDE_PEARL: i => {
    i.paint(D(8, 8, 5.2), ramp(0x4fbfb4), {spec: [1, 1]});
    i.mark(AR(8, 8, 3, PI * .2, PI * 1.1, .8), 0xa4efe6); i.dots([[3, 3], [13, 4], [12, 13]], 0xcdf6ff);
  },
  TENTACLE_CHARM: i => {
    i.paint(AR(8, 4, 2.6, PI, 2 * PI, 1), C.leather);
    i.paint(PA([[8, 14.2], [11.4, 12], [11, 8.6], [7.4, 8], [6, 5]], 2.5), ramp(0x6b4d8f), {spec: [1, 1]});
    i.dots([[11, 12], [11.4, 9.8], [8.6, 8.6], [6.8, 6.4]], 0xe9b5d6);
  },
  STAR_SHARD: i => {
    i.paint(P([[8, 1.6], [10.6, 5.4], [12.8, 9.4], [10, 13.8], [5.4, 13], [3.2, 8.6], [5.4, 4.6]]), ramp(0x4d5588), {spec: [3, 2]});
    i.mark(L(8, 3, 8, 12, .8), 0x717bc0); i.dots([[7, 5], [9, 8], [6, 10], [10, 11]], 0xeaf2ff);
    i.dots([[13, 2], [13, 3], [13, 4], [12, 3], [14, 3]], 0xffffff);
  },
  NIGHTGAUNT_PINION: i => {
    i.paint(P([[13.6, 1.4], [10, 2.2], [6.4, 5], [4, 9], [3.4, 13.4], [6.4, 11.6], [10.4, 8.8], [12.8, 4.6]]), ramp(0x2d2445), {spec: [3, 1]});
    i.mark(L(13, 2, 3.8, 13, .9), 0x6a5a92); i.mark(L(9, 4.6, 11, 7, .7), 0x4a3c6a); i.mark(L(6.6, 7.4, 8.6, 9.6, .7), 0x4a3c6a);
    i.dots([[4, 13]], 0xb9a8ff);
  },
  GHOUL_TOOTH: i => {
    i.paint(P([[8, 14.6], [6.4, 10.6], [3.6, 7.8], [3.4, 4.4], [5.2, 2.4], [8, 3.2], [10.8, 2.4], [12.6, 4.4], [12.4, 7.8], [9.6, 10.6]]), ramp(0xf0e8c8), {spec: [2, 1]});
    i.paint(P([[8, 14.6], [7, 11], [9, 11]]), ramp(0xcfc08a)); i.mark(PA([[7, 4], [6.2, 7], [8, 8.6]], .8), 0xbcae78); i.dots([[10, 5.6]], 0x6b5a2f);
  },
  MIGO_CYLINDER: i => {
    i.paint(R(4.5, 3, 7, 10), ramp(0x9fd8e0), {spec: [1, 1]}); i.paint(E(8, 8.2, 2.6, 3.6), ramp(0xe58fb4), {spec: [1, 1]});
    i.mark(PA([[7, 6.6], [8.6, 8], [7.4, 9.4]], .6), 0xb5527e);
    i.paint(R(3.6, 1.6, 8.8, 2), C.steel); i.paint(R(3.6, 12.4, 8.8, 2), C.steel); i.dots([[5.4, 6], [10.6, 10]], 0xffffff);
  },
  IDOL_OF_THE_DREAMER: i => {
    i.paint(R(4.2, 13, 7.6, 1.8), ramp(0x344a40));
    i.paint(P([[5.4, 13], [5.2, 8.8], [10.8, 8.8], [10.6, 13]]), ramp(0x4d7a62), {spec: [1, 1]});
    i.paint(PA([[6.2, 8.6], [5.2, 11.4]], 1.2), ramp(0x4d7a62)); i.paint(PA([[8, 8.8], [8, 11.8]], 1.2), ramp(0x4d7a62)); i.paint(PA([[9.8, 8.6], [10.8, 11.4]], 1.2), ramp(0x4d7a62));
    i.paint(P([[5, 9.4], [2.4, 6], [2.6, 11.4]]), ramp(0x3b6150)); i.paint(P([[11, 9.4], [13.6, 6], [13.4, 11.4]]), ramp(0x3b6150));
    i.paint(D(8, 5.6, 3.2), ramp(0x5c9376), {spec: [1, 1]}); i.dots([[6.6, 5.2], [9.4, 5.2]], 0xf4d35e);
  },
  SEAL_TIDES: i => medal(i, ramp(0x4aa0b8), j => { j.mark(PA([[4.6, 7.2], [6.2, 5.6], [8, 7.2], [9.8, 5.6], [11.4, 7.2]], .9), 0xd7f6ff); j.mark(PA([[4.6, 10.4], [6.2, 8.8], [8, 10.4], [9.8, 8.8], [11.4, 10.4]], .9), 0xd7f6ff); }),
  SEAL_STONE: i => medal(i, ramp(0x7a7f88), j => { j.mark(E(8, 11, 3, 1.5), 0x34373d); j.mark(E(8, 8.4, 2.2, 1.3), 0x34373d); j.mark(E(8, 6, 1.4, 1), 0x34373d); }),
  SEAL_HUNGER: i => medal(i, ramp(0x8a3b2c), j => { j.mark(E(8, 8.4, 3.6, 2.5), 0x2a0f12); j.dots([[5, 7], [7, 7], [9, 7], [11, 7], [6, 10], [8, 10], [10, 10]], 0xf0e8d0); }),
  SEAL_DEEP: i => medal(i, ramp(0x2e6f62), j => { j.mark(P([[4, 8], [8, 5.2], [12, 8], [8, 10.8]]), 0x0e2a28); j.dots([[8, 8], [7, 8], [9, 8]], 0x9af0b4); j.mark(PA([[6, 11], [5.4, 12.6]], .7), 0x9af0b4); j.mark(PA([[10, 11], [10.6, 12.6]], .7), 0x9af0b4); }),
  SEAL_SILENCE: i => medal(i, ramp(0xc6c6d2), j => { j.mark(L(5, 8.6, 11, 8.6, .9), 0x55556a); j.dots([[6, 7.6], [8, 7.6], [10, 7.6], [6, 9.6], [8, 9.6], [10, 9.6]], 0x55556a); })
};
module.exports = {ICONS, make, draw: name => make(ICONS[name])};
