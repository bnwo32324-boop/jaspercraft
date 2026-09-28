/* Atlas, the Divided Realm: the browser client's realm module. The JasprRealm stage in classes.js loads this file the
 * first time the server marks a player as being in Atlas, and calls tick(api, state) every client tick while they stay:
 * state = {realm, version, zone, mask, victory}, zone one of C (the Concord), L (the Pharos Line), W (the Wound),
 * M (the Ashen Marches, the Teeth, the Gate Road), E (the Petrified Weald), F (the Scorched Forges), A (the Fallen
 * Cities), P (the Plateau of Cinders), R (the Rim); mask has a bit per liberated province (1 Marches, 2 Weald,
 * 4 Forges, 8 Fallen Cities, 16 Plateau).
 *
 * What it does: the sky and fog of each land (the Concord bright and warm, the Wound hazy, each Dominion province its
 * own darkness), ash falling without end over occupied Dominion land, embers drifting up in the Forges and on the
 * Plateau, lumen motes in the Concord's air; liberated land clears to a pale, healing sky, and when Atlas is won the ash
 * stops everywhere and the whole sky clears. Colours change smoothly as the player crosses a border.
 * No DOM, no storage, no network: only the engine API it is given. */
(function () {
  'use strict';
  var GRAY_POWDER = 252 | 7 << 12, BLACK_POWDER = 252 | 15 << 12;   // falling-dust particles take the block's colour

  // Per zone: fog colour and weight, sky dome colour, sun tint (green, blue multipliers: red-shifted in the east),
  // ash (particles per tick at full strength), embers, lumen motes, and the province bit that liberates it.
  var ZONES = {
    C: {fog: [0.84, 0.88, 0.96], w: 0.25, sky: [0.46, 0.66, 1.00], motes: 1},
    L: {fog: [0.80, 0.84, 0.92], w: 0.3, sky: [0.46, 0.64, 0.98], motes: 1},
    R: {fog: [0.80, 0.84, 0.92], w: 0.3, sky: [0.50, 0.66, 0.98]},
    W: {fog: [0.47, 0.43, 0.39], w: 0.75, sky: [0.44, 0.42, 0.40], sun: [0.8, 0.7], ash: 0.35},
    M: {fog: [0.19, 0.17, 0.16], w: 0.9, sky: [0.15, 0.13, 0.13], sun: [0.45, 0.32], ash: 1, bit: 1},
    E: {fog: [0.29, 0.32, 0.29], w: 0.9, sky: [0.22, 0.25, 0.22], sun: [0.62, 0.55], ash: 0.7, bit: 2},
    F: {fog: [0.33, 0.12, 0.06], w: 0.9, sky: [0.20, 0.07, 0.04], sun: [0.30, 0.18], ash: 0.8, embers: 1, bit: 4},
    A: {fog: [0.22, 0.20, 0.27], w: 0.9, sky: [0.16, 0.14, 0.20], sun: [0.50, 0.50], ash: 0.8, bit: 8},
    P: {fog: [0.12, 0.05, 0.05], w: 0.95, sky: [0.08, 0.03, 0.03], sun: [0.22, 0.12], ash: 1.2, embers: 0.6, bit: 16}
  };
  // Freed land: a pale sky, the haze thinning (still a little ash-grey, the land is healing), no ash.
  var FREED = {fog: [0.72, 0.77, 0.84], w: 0.35, sky: [0.50, 0.64, 0.94]};
  var WON = {fog: [0.82, 0.87, 0.95], w: 0.2, sky: [0.48, 0.67, 1.00]};

  var fog = null, sky = null, weight = 0, n = 0;

  function towards(cur, target, rate) {
    if (!cur) return target.slice();
    for (var i = 0; i < 3; i++) cur[i] += (target[i] - cur[i]) * rate;
    return cur;
  }

  function around(api, p, radius, low, high) {
    var a = Math.random() * Math.PI * 2, d = Math.sqrt(Math.random()) * radius;
    return {x: p.x + Math.cos(a) * d, y: p.y + low + Math.random() * (high - low), z: p.z + Math.sin(a) * d};
  }

  var module = {
    tick: function (api, s) {
      var z = ZONES[s.zone] || ZONES.C;
      var freed = !!(z.bit && (s.mask & z.bit));
      var look = s.victory ? WON : freed ? FREED : z;
      fog = towards(fog, look.fog, 0.04);
      sky = towards(sky, look.sky, 0.04);
      weight += (look.w - weight) * 0.04;
      api.setFog(fog, weight);
      api.setSky(sky);
      api.setSun(s.victory || freed || !z.sun ? null : z.sun);
      n++;
      var p = api.player();
      // Ash, falling without end over the occupied east (and thinly over the Wound).
      if (!s.victory && !freed && z.ash) {
        var count = Math.round(8 * z.ash);
        for (var i = 0; i < count; i++) {
          var q = around(api, p, 18, 6, 16);
          api.particle('fallingdust', q.x, q.y, q.z, 0, 0, 0, Math.random() < 0.6 ? GRAY_POWDER : BLACK_POWDER);
        }
        if (n % 5 === 0) { var s1 = around(api, p, 14, -1, 3); api.particle('townaura', s1.x, s1.y, s1.z, 0, 0, 0); }
      }
      // Embers drifting up from the Forges' ground and the Plateau's cracks.
      if (!s.victory && !freed && z.embers && Math.random() < z.embers) {
        var e = around(api, p, 12, -2, 2);
        api.particle(Math.random() < 0.25 ? 'lava' : 'flame', e.x, e.y, e.z, (Math.random() - 0.5) * 0.02, 0.03 + Math.random() * 0.04, (Math.random() - 0.5) * 0.02);
      }
      // Lumen motes in the Concord's air, and over land that has been won back.
      if ((z.motes || freed || s.victory) && n % 3 === 0) {
        var m = around(api, p, 14, 0, 6);
        api.particle('endRod', m.x, m.y, m.z, 0, 0.01, 0);
      }
    },
    leave: function (api) {
      api.setFog(null);
      api.setSky(null);
      api.setSun(null);
      fog = null; sky = null; weight = 0;
    }
  };

  var g = typeof window !== "undefined" ? window : globalThis;
  var registry = g.JasprRealmModules || {};
  registry.atlas = module;
  g.JasprRealmModules = registry;
})();
