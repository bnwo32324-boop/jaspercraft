'use strict';
/* Icons for the Backrooms' trinkets (JasprBackrooms Items ids, upper-cased): two per level, the Poolrooms' pair, and the Exit Sign. */
const {M, Icon, ramp} = require('./engine.cjs');
const C = require('./palette.cjs');
const P = M.poly, D = M.disc, R = M.rect, E = M.ellipse, L = M.line, PA = M.path, RG = M.ring, ER = M.ering, U = M.U, S = M.sub, AR = M.arc;
const PI = Math.PI;
const make = draw => { const i = new Icon(); draw(i); return i; };

const ICONS = {
  CANTEEN: i => {
    i.paint(AR(8, 4.4, 3.4, PI, 2 * PI, 1), C.leather);
    i.paint(E(8, 9.6, 5, 5.2), ramp(0x6f8a5a), {spec: [2, 1]}); i.paint(R(6.6, 2.8, 2.8, 2.2), C.darkIron);
    i.mark(RG(8, 9.6, 3.6, 3.2), 0x4d6340); i.dots([[8, 9.4]], 0xf1e6c8);
  },
  FLICKERING_BULB: i => {
    i.paint(E(8, 6.4, 4, 4.6), ramp(0xfff2a0), {spec: [1, 1]}); i.paint(R(6.2, 10.4, 3.6, 3.6), C.steel); i.mark(L(6.4, 12, 9.6, 12, .7), 0x4a5668);
    i.mark(PA([[6.6, 8.6], [7.4, 5.8], [8.6, 7.6], [9.4, 5]], .6), 0xe0902a); i.dots([[1.6, 3], [14.4, 4], [2, 10], [14, 10]], 0xfff2a0);
  },
  FORKLIFT_KEY: i => {
    i.paint(ER(5, 5, 3.3, 3.3, 1.6), C.steel); i.paint(L(7, 7, 13, 13, 1.7), C.steel); i.paint(L(11, 11, 12.6, 9.4, 1.5), C.steel); i.paint(L(12.6, 12.6, 14, 11.2, 1.4), C.steel);
    i.paint(P([[1.6, 9.4], [6, 9.4], [6, 14.6], [1.6, 14.6]]), ramp(0xf08a1e), {spec: [1, 1]}); i.dots([[3.4, 12]], 0x3b2410);
  },
  PACKING_TAPE: i => {
    i.paint(ER(8, 7.6, 6, 6, 2.6), ramp(0xc9a45c), {spec: [1, 1]}); i.paint(RG(8, 7.6, 3.4, 3.2), ramp(0x8a6a2e));
    i.paint(P([[10.4, 12.4], [14.4, 12.4], [14.4, 14.6], [11.4, 14.6]]), ramp(0xdcc07a));
  },
  PRESSURE_GAUGE: i => {
    i.paint(R(6.6, 12.4, 2.8, 2.6), C.brass); i.paint(D(8, 7.6, 6), C.steel); i.paint(D(8, 7.6, 4.6), ramp(0xf4f0e4), {shade: 'flat'});
    i.mark(L(8, 7.6, 11, 5, .9), 0xc82a2a); i.dots([[8, 3.6], [12, 7.6], [4, 7.6], [10.6, 4.6], [5.4, 4.6]], 0x3a3a44); i.dots([[8, 7.6]], 0x16121f);
  },
  COOLANT_VIAL: i => {
    i.paint(R(6.6, 1.2, 2.8, 2.2), C.wood); i.paint(R(5.4, 3.2, 5.2, 11.2), ramp(0xbfe3f2), {spec: [1, 1]}); i.paint(R(6, 6.6, 4, 7.2), ramp(0x4fc3f7));
    i.mark(U(L(8, 7.4, 8, 12.6, .7), L(6.4, 10, 9.6, 10, .7)), 0xe8faff); i.dots([[7, 5]], 0xffffff);
  },
  SURGE_PROTECTOR: i => {
    i.paint(PA([[13, 11], [14.6, 13], [12.4, 14.8]], 1), C.darkIron);
    i.paint(R(1.8, 4, 12, 7, 0), ramp(0xe4e6ec), {spec: [1, 1]}); i.paint(R(1.8, 8.4, 12, 2.6), ramp(0xc4c8d2));
    i.dots([[4, 6], [5, 6], [4, 7], [5, 7], [8, 6], [9, 6], [8, 7], [9, 7], [12, 6], [12, 7]], 0x2b2f3a); i.dots([[4, 9.6], [6, 9.6]], 0x32d46a);
  },
  CAPACITOR: i => {
    i.paint(L(6.4, 13, 6.4, 15, .9), C.silver); i.paint(L(9.6, 13, 9.6, 15, .9), C.silver);
    i.paint(R(4.2, 2.6, 7.6, 10.8), ramp(0x2f5fb0), {spec: [1, 1]}); i.paint(R(4.2, 2.6, 7.6, 1.6), C.silver);
    i.mark(R(4.6, 5.4, 1.5, 7.6), 0xdfe9ff); i.mark(U(L(7.4, 7, 10.4, 7, .7), L(8.9, 5.6, 8.9, 8.4, .7)), 0xdfe9ff);
  },
  EMPLOYEE_BADGE: i => {
    i.paint(R(6.4, 1.2, 3.2, 2.4), C.steel);
    i.paint(R(3, 3, 10, 11.4), ramp(0xeef0f4), {spec: [1, 1]}); i.paint(R(3, 3, 10, 2.6), ramp(0x2f5fb0));
    i.paint(R(4.6, 6.6, 3.8, 4.4), ramp(0x9aa3b4), {shade: 'flat'}); i.mark(U(R(9.2, 7, 2.6, .8), R(9.2, 8.8, 2.6, .8), R(4.6, 12, 6.8, .8)), 0x5b6478);
  },
  COLD_COFFEE: i => {
    i.paint(P([[3.8, 4.4], [12.2, 4.4], [11, 14.6], [5, 14.6]]), ramp(0xf0e6d2), {spec: [2, 2]});
    i.paint(P([[3.6, 2.4], [12.4, 2.4], [12.2, 4.8], [3.8, 4.8]]), ramp(0x3b2a20)); i.paint(P([[4.2, 7.6], [11.8, 7.6], [11.4, 11], [4.6, 11]]), ramp(0x9b6b3d));
    i.dots([[8, 9.2]], 0xf0e6d2); i.dots([[6, 1], [10, .6]], 0xaed7ff);
  },
  DEAD_MANS_WATCH: i => {
    i.paint(ER(8, 2.6, 1.7, 1.7, .8), C.gold); i.paint(R(7, 3.4, 2, 1.6), C.gold);
    i.paint(D(8, 9.2, 5), C.gold, {spec: [1, 1]}); i.paint(D(8, 9.2, 3.8), ramp(0xf4f0e4), {shade: 'flat'});
    i.mark(U(L(8, 9.2, 8, 6.6, .8), L(8, 9.2, 10.2, 10.4, .8)), 0x2b2f3a); i.dots([[8, 5.8], [11.8, 9.2], [4.2, 9.2], [8, 12.6]], 0x7a6a3a);
  },
  SUBWAY_TOKEN: i => {
    i.paint(S(D(8, 8, 5.2), M.rect(6.6, 6.6, 2.8, 2.8)), C.brass, {spec: [1, 1]});
    i.mark(RG(8, 8, 4.4, 3.8), 0x8a6a1c); i.mark(U(L(5.4, 6, 5.4, 10, .8), L(10.6, 6, 10.6, 10, .8)), 0x8a6a1c);
  },
  RUBBER_DUCK: i => {
    i.paint(P([[2, 9.4], [3.6, 7], [9, 7], [12.4, 10], [11, 13], [5, 13.6], [2.6, 12]]), ramp(0xf4c81c), {spec: [2, 1]});
    i.paint(D(10, 5.2, 2.7), ramp(0xf4c81c), {spec: [1, 1]}); i.paint(P([[11.8, 5.2], [14.6, 5.8], [11.8, 7]]), C.ember);
    i.dots([[9.6, 4.6]], 0x16121f); i.mark(PA([[4, 10], [6.4, 11.4], [8.6, 10.6]], .7), 0xd8a50c);
  },
  WHISTLE: i => {
    i.paint(ER(5.6, 9.4, 3.6, 3.6, 1.7), ramp(0xd8dde6), {spec: [1, 1]}); i.paint(P([[8.6, 7.4], [14.6, 7.4], [14.6, 10], [8.8, 10.8]]), ramp(0xd8dde6));
    i.paint(D(5.6, 9.4, 1.4), C.black); i.paint(AR(6, 4, 3, PI, 2 * PI, .8), C.velvet); i.dots([[12, 8.6]], 0x16121f);
  },
  EXIT_SIGN: i => {
    i.paint(R(1.8, 3.6, 12.4, 8.8), ramp(0x1fa04a), {spec: [1, 1]}); i.mark(U(R(2.8, 4.6, 10.4, .8), R(2.8, 11, 10.4, .8)), 0xe8ffe8);
    i.paint(D(6.2, 6.4, 1), C.white); i.mark(U(L(6.2, 7.2, 5.4, 9.8, .9), L(6.2, 7.6, 8, 9, .9), L(6.2, 7.8, 7.2, 9.8, .9)), 0xffffff);
    i.mark(U(L(9.4, 8, 12.2, 8, .9), P([[12.8, 8], [11.2, 6.6], [11.2, 9.4]])), 0xffffff);
  }
};
module.exports = {ICONS, make, draw: name => make(ICONS[name])};
