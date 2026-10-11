/* JasperCraft first-person gun animation (JASPR_GUNFX, owner request 2026-10-11: "give gun animations when they shoot,
 * such as recoil, etc." / "make the combat for melee weapons and gunplay extremely satisfying").
 *
 * Pure logic, no engine names: which held items are guns, what happened to them (from the server's own item data), and
 * the first-person pose that shows it. Visual only: it never changes damage, fire rate, ammunition or packets, and it
 * never invents a shot. A shot is the held gun's own `rounds` dropping for the same serial (Apocalypse arsenal) or uuid
 * (dungeon armoury); a reload is `rounds` rising (dungeon: also its `reloadAt` being set; Apocalypse: the server's
 * reload sound); a dungeon caster's cast is its `readyAt` moving on; a Portal Gun shot is the server's own action-bar
 * line. Clocked by client ticks plus partial ticks, never by wall time: an event starts at the first frame drawn after
 * the tick that saw it, so the kick and the flash are never skipped, whatever the frame rate.
 *
 * Every motion is in the first-person HAND frame (the frame after vanilla's transformSideFirstPerson, before the item's
 * display transform): x right, y up, z toward the eye, blocks and degrees. Geometry per gun (muzzle, bore axis, grip,
 * butt) comes from JasprGunFXData (scripts/gunfx-data.cjs, measured from the apocalypse pack's models).
 */
var JasprGunFXCore = (function () {
  'use strict';
  var VERSION = 'gunfx1';

  // ------------------------------------------------------------------------------------------------ gun classes
  // kick (one shot): back toward the eye, up, side (random), climb = muzzle rise, roll (random cant), yaw (random);
  // a: ticks to the kick's peak; t / r: the spring that returns translation / rotation after it (w rad per tick,
  // z damping ratio; below 1 it overshoots a little and settles); drift: what rapid fire stacks up (per shot, fades
  // over T ticks, never more than max); shoulder: 0 turns about the grip, 1 about the butt; cycle: the action after the
  // shot; reload: choreography (RELOADS); flash: muzzle flash (life in ticks); casing / smoke: world particles; cam:
  // camera impulse handed to JasprCameraFX.kick (degrees, blocks, milliseconds).
  var CLASSES = {
    pistol: { kick: { back: 0.07, up: 0.015, side: 0.006, climb: 11, roll: 3.5, yaw: 1.5 }, a: 0.55, t: { w: 1.2, z: 0.55 }, r: { w: 1.0, z: 0.5 },
      drift: { climb: 0.5, back: 0.004, T: 7, max: 4 }, shoulder: 0, cycle: { type: 'slide', at: 1.0, len: 1.8, amp: 0.014 }, reload: 'pistol',
      flash: { size: 0.11, len: 0.16, spikes: 5, style: 'gun', life: 1.3 }, casing: 'brass', smoke: 0, cam: { pitch: 0.7, yaw: 0.25, roll: 0.3, back: 0.01, ms: 140 } },
    mpistol: { kick: { back: 0.045, up: 0.01, side: 0.006, climb: 6, roll: 2.6, yaw: 1.6 }, a: 0.5, t: { w: 1.35, z: 0.6 }, r: { w: 1.1, z: 0.55 },
      drift: { climb: 1.2, back: 0.01, T: 9, max: 6 }, shoulder: 0.1, cycle: { type: 'slide', at: 0.9, len: 1.4, amp: 0.01 }, reload: 'pistol',
      flash: { size: 0.1, len: 0.14, spikes: 5, style: 'gun', life: 1.2 }, casing: 'brass', smoke: 0, cam: { pitch: 0.45, yaw: 0.25, roll: 0.2, back: 0.005, ms: 110 } },
    revolver: { kick: { back: 0.085, up: 0.025, side: 0.006, climb: 16, roll: 5, yaw: 1.8 }, a: 0.6, t: { w: 1.0, z: 0.55 }, r: { w: 0.8, z: 0.5 },
      drift: { climb: 0.4, back: 0.003, T: 8, max: 3 }, shoulder: 0, cycle: { type: 'hammer', at: 5, len: 5, amp: 1 }, reload: 'cylinder',
      flash: { size: 0.15, len: 0.22, spikes: 6, style: 'gun', life: 1.4 }, casing: null, smoke: 1, cam: { pitch: 1.3, yaw: 0.3, roll: 0.5, back: 0.02, ms: 190 } },
    magnum: { kick: { back: 0.11, up: 0.035, side: 0.008, climb: 21, roll: 7, yaw: 2.2 }, a: 0.65, t: { w: 0.9, z: 0.5 }, r: { w: 0.72, z: 0.48 },
      drift: null, shoulder: 0, cycle: { type: 'hammer', at: 6, len: 5, amp: 1.3 }, reload: 'cylinder',
      flash: { size: 0.19, len: 0.28, spikes: 7, style: 'gun', life: 1.5 }, casing: null, smoke: 1, cam: { pitch: 2.0, yaw: 0.4, roll: 0.6, back: 0.03, ms: 230 } },
    smg: { kick: { back: 0.042, up: 0.008, side: 0.005, climb: 3.5, roll: 1.6, yaw: 1.2 }, a: 0.45, t: { w: 1.4, z: 0.65 }, r: { w: 1.15, z: 0.6 },
      drift: { climb: 1.4, back: 0.012, T: 10, max: 6 }, shoulder: 0.3, cycle: null, reload: 'mag',
      flash: { size: 0.11, len: 0.2, spikes: 5, style: 'gun', life: 1.2 }, casing: 'brass', smoke: 0, cam: { pitch: 0.35, yaw: 0.2, roll: 0.15, back: 0.005, ms: 100 } },
    carbine: { kick: { back: 0.065, up: 0.012, side: 0.005, climb: 5.5, roll: 2, yaw: 1.2 }, a: 0.5, t: { w: 1.2, z: 0.6 }, r: { w: 0.95, z: 0.55 },
      drift: { climb: 0.8, back: 0.007, T: 9, max: 5 }, shoulder: 0.35, cycle: null, reload: 'mag',
      flash: { size: 0.13, len: 0.24, spikes: 5, style: 'gun', life: 1.3 }, casing: 'brass', smoke: 0, cam: { pitch: 0.6, yaw: 0.2, roll: 0.2, back: 0.01, ms: 130 } },
    rifle: { kick: { back: 0.08, up: 0.015, side: 0.005, climb: 6.5, roll: 2.2, yaw: 1.2 }, a: 0.5, t: { w: 1.15, z: 0.6 }, r: { w: 0.9, z: 0.55 },
      drift: { climb: 0.9, back: 0.008, T: 10, max: 5 }, shoulder: 0.4, cycle: null, reload: 'mag',
      flash: { size: 0.14, len: 0.28, spikes: 5, style: 'gun', life: 1.3 }, casing: 'brass', smoke: 0, cam: { pitch: 0.7, yaw: 0.2, roll: 0.25, back: 0.01, ms: 140 } },
    dmr: { kick: { back: 0.095, up: 0.018, side: 0.004, climb: 8.5, roll: 2.4, yaw: 1 }, a: 0.55, t: { w: 1.0, z: 0.58 }, r: { w: 0.8, z: 0.55 },
      drift: { climb: 0.4, back: 0.004, T: 9, max: 3 }, shoulder: 0.45, cycle: null, reload: 'mag',
      flash: { size: 0.15, len: 0.3, spikes: 6, style: 'gun', life: 1.4 }, casing: 'brass', smoke: 1, cam: { pitch: 1.0, yaw: 0.25, roll: 0.3, back: 0.015, ms: 170 } },
    burst: { kick: { back: 0.065, up: 0.012, side: 0.005, climb: 5, roll: 2, yaw: 1.3 }, a: 0.5, t: { w: 1.2, z: 0.6 }, r: { w: 0.95, z: 0.55 },
      drift: { climb: 1.2, back: 0.008, T: 12, max: 5 }, shoulder: 0.35, cycle: null, reload: 'mag',
      flash: { size: 0.13, len: 0.26, spikes: 5, style: 'gun', life: 1.3 }, casing: 'brass', smoke: 0, cam: { pitch: 0.6, yaw: 0.25, roll: 0.2, back: 0.01, ms: 130 } },
    bolt: { kick: { back: 0.13, up: 0.025, side: 0.004, climb: 11, roll: 2.6, yaw: 1 }, a: 0.6, t: { w: 0.85, z: 0.55 }, r: { w: 0.7, z: 0.52 },
      drift: null, shoulder: 0.55, cycle: { type: 'bolt', at: 7, len: 14, amp: 1 }, reload: 'bolt',
      flash: { size: 0.18, len: 0.38, spikes: 6, style: 'gun', life: 1.5 }, casing: 'brass', ejectOnCycle: true, smoke: 1, cam: { pitch: 1.6, yaw: 0.3, roll: 0.4, back: 0.03, ms: 240 } },
    antimat: { kick: { back: 0.2, up: 0.035, side: 0.005, climb: 14, roll: 3.5, yaw: 1.2 }, a: 0.7, t: { w: 0.7, z: 0.55 }, r: { w: 0.6, z: 0.5 },
      drift: null, shoulder: 0.7, cycle: { type: 'bolt', at: 9, len: 16, amp: 1.15 }, reload: 'bolt',
      flash: { size: 0.24, len: 0.5, spikes: 8, style: 'gun', life: 1.7 }, casing: 'brass', ejectOnCycle: true, smoke: 2, cam: { pitch: 2.4, yaw: 0.4, roll: 0.6, back: 0.05, ms: 300 } },
    pump: { kick: { back: 0.15, up: 0.03, side: 0.006, climb: 13, roll: 4, yaw: 1.6 }, a: 0.6, t: { w: 0.85, z: 0.5 }, r: { w: 0.7, z: 0.5 },
      drift: null, shoulder: 0.5, cycle: { type: 'pump', at: 5, len: 8, amp: 1 }, reload: 'shells',
      flash: { size: 0.22, len: 0.34, spikes: 8, style: 'wide', life: 1.5 }, casing: 'shell', ejectOnCycle: true, smoke: 2, cam: { pitch: 2.0, yaw: 0.4, roll: 0.6, back: 0.04, ms: 240 } },
    breakgun: { kick: { back: 0.16, up: 0.03, side: 0.006, climb: 14, roll: 4, yaw: 1.8 }, a: 0.6, t: { w: 0.85, z: 0.5 }, r: { w: 0.7, z: 0.5 },
      drift: null, shoulder: 0.45, cycle: null, reload: 'break',
      flash: { size: 0.22, len: 0.34, spikes: 8, style: 'wide', life: 1.5 }, casing: null, smoke: 2, cam: { pitch: 2.2, yaw: 0.4, roll: 0.7, back: 0.04, ms: 250 } },
    drum: { kick: { back: 0.13, up: 0.025, side: 0.006, climb: 10, roll: 3.5, yaw: 1.6 }, a: 0.55, t: { w: 0.95, z: 0.55 }, r: { w: 0.78, z: 0.5 },
      drift: { climb: 0.6, back: 0.006, T: 10, max: 4 }, shoulder: 0.45, cycle: null, reload: 'drum',
      flash: { size: 0.2, len: 0.3, spikes: 8, style: 'wide', life: 1.4 }, casing: 'shell', smoke: 1, cam: { pitch: 1.6, yaw: 0.4, roll: 0.5, back: 0.03, ms: 200 } },
    heavy: { kick: { back: 0.08, up: 0.015, side: 0.007, climb: 4, roll: 2, yaw: 1.5 }, a: 0.6, t: { w: 0.8, z: 0.65 }, r: { w: 0.65, z: 0.62 },
      drift: { climb: 1.6, back: 0.016, T: 14, max: 8 }, shoulder: 0.7, cycle: { type: 'thump', at: 0.8, len: 2.4, amp: 1 }, reload: 'belt',
      flash: { size: 0.17, len: 0.32, spikes: 6, style: 'gun', life: 1.4 }, casing: 'brass', smoke: 1, cam: { pitch: 0.6, yaw: 0.3, roll: 0.25, back: 0.015, ms: 150 } },
    rotary: { kick: { back: 0.04, up: 0.007, side: 0.006, climb: 1.8, roll: 1.2, yaw: 1.4 }, a: 0.4, t: { w: 1.3, z: 0.6 }, r: { w: 1.1, z: 0.6 },
      drift: { climb: 0.9, back: 0.012, T: 14, max: 6 }, shoulder: 0.6, cycle: { type: 'spin', at: 0, len: 6, amp: 0.006 }, reload: 'belt',
      flash: { size: 0.14, len: 0.26, spikes: 5, style: 'gun', life: 1.2 }, casing: 'brass', smoke: 0, cam: { pitch: 0.28, yaw: 0.25, roll: 0.15, back: 0.005, ms: 95 } },
    rail: { kick: { back: 0.16, up: 0.02, side: 0.003, climb: 7, roll: 1.5, yaw: 0.7 }, a: 1.3, t: { w: 0.6, z: 0.42 }, r: { w: 0.55, z: 0.45 },
      drift: null, shoulder: 0.5, cycle: { type: 'vent', at: 2, len: 12, amp: 2.5 }, reload: 'cell',
      flash: { size: 0.12, len: 0.75, spikes: 4, style: 'rail', life: 2.0 }, casing: null, smoke: 0, spark: 2, cam: { pitch: 1.8, yaw: 0.2, roll: 0.3, back: 0.05, ms: 320 } },
    plasma: { kick: { back: 0.11, up: 0.02, side: 0.004, climb: 7, roll: 3, yaw: 1.2 }, a: 1.0, t: { w: 0.7, z: 0.45 }, r: { w: 0.62, z: 0.45 },
      drift: null, shoulder: 0.4, cycle: { type: 'vent', at: 1.5, len: 10, amp: 3 }, reload: 'cell',
      flash: { size: 0.16, len: 0.4, spikes: 6, style: 'plasma', life: 1.6 }, casing: null, smoke: 0, spark: 1, cam: { pitch: 1.2, yaw: 0.3, roll: 0.4, back: 0.03, ms: 240 } },
    pulse: { kick: { back: 0.07, up: 0.012, side: 0.004, climb: 5, roll: 2, yaw: 1.2 }, a: 0.7, t: { w: 1.0, z: 0.5 }, r: { w: 0.85, z: 0.5 },
      drift: { climb: 1.2, back: 0.008, T: 12, max: 5 }, shoulder: 0.35, cycle: { type: 'vent', at: 1, len: 6, amp: 1.5 }, reload: 'cell',
      flash: { size: 0.13, len: 0.3, spikes: 5, style: 'plasma', life: 1.4 }, casing: null, smoke: 0, spark: 1, cam: { pitch: 0.7, yaw: 0.25, roll: 0.25, back: 0.01, ms: 140 } },
    portal: { kick: { back: 0.06, up: 0.012, side: 0.006, climb: 6, roll: 7, yaw: 1.8 }, a: 0.7, t: { w: 0.9, z: 0.4 }, r: { w: 0.75, z: 0.38 },
      drift: null, shoulder: 0.2, cycle: { type: 'vent', at: 1, len: 8, amp: 2 }, reload: null,
      flash: { size: 0.13, len: 0.22, spikes: 6, style: 'portal', life: 1.5 }, casing: null, smoke: 0, dust: 5, cam: { pitch: 0.5, yaw: 0.15, roll: 0.4, back: 0, ms: 150 } },
    magic: { kick: { back: -0.2, up: 0.06, side: 0.005, climb: 16, roll: 7, yaw: 1.5 }, a: 1.6, t: { w: 0.7, z: 0.6 }, r: { w: 0.6, z: 0.55 },
      drift: null, shoulder: 0, cycle: null, reload: null, flash: null, casing: null, smoke: 0, spell: 6, cam: { pitch: 0.4, yaw: 0, roll: 0.2, back: -0.02, ms: 160 } }
  };

  // ------------------------------------------------------------------------------------------------ reloads
  // lower: the pose while working (full envelope); work: a repeated motion while lowered; seat: the magazine / shell /
  // cylinder going home; rack: the charging handle, slide, bolt or pump at the end. Same units as the kicks. The vanilla
  // first-person gun already sits in the lower right corner, so a reload brings it in toward the middle and cants it
  // (muzzle up, magazine well toward the eye) instead of dropping it out of sight.
  var RELOADS = {
    pistol: { lower: { x: -0.07, y: 0.02, z: 0.05, climb: 14, roll: 30, yaw: 18 }, work: 'bob', seat: { y: 0.025, roll: -4 }, rack: { z: 0.035, climb: -3 } },
    mag: { lower: { x: -0.1, y: 0.0, z: 0.06, climb: 10, roll: 40, yaw: 16 }, work: 'bob', seat: { y: 0.03, roll: -5 }, rack: { z: 0.05, roll: -6 } },
    drum: { lower: { x: -0.11, y: -0.01, z: 0.06, climb: 12, roll: 46, yaw: 16 }, work: 'bob', seat: { y: 0.035, roll: -6 }, rack: { z: 0.045, roll: -4 } },
    bolt: { lower: { x: -0.08, y: 0.0, z: 0.05, climb: 8, roll: 30, yaw: 12 }, work: 'bob', seat: { y: 0.03, roll: -4 }, rack: { z: 0.05, roll: 14 } },
    shells: { lower: { x: -0.04, y: -0.02, z: 0.04, climb: 10, roll: -34, yaw: -8 }, work: 'shells', seat: null, rack: { z: 0.07, y: -0.015, roll: -5 } },
    break: { lower: { x: -0.08, y: 0.07, z: 0.08, climb: -10, roll: 18, yaw: 10 }, work: 'bob', seat: null, rack: { climb: 10, y: 0.015 } },
    cylinder: { lower: { x: -0.07, y: 0.0, z: 0.03, climb: 16, roll: 58, yaw: 14 }, work: 'spin', seat: null, rack: { roll: -10, climb: 2 } },
    belt: { lower: { x: -0.06, y: -0.04, z: 0.07, climb: 14, roll: 26, yaw: 12 }, work: 'bob', seat: { y: -0.03, climb: -2 }, rack: { z: 0.05, roll: -4 } },
    cell: { lower: { x: -0.08, y: -0.02, z: 0.06, climb: 10, roll: -30, yaw: -10 }, work: 'hum', seat: { y: 0.02, roll: 3 }, rack: { z: -0.03, climb: 2 } }
  };

  // ------------------------------------------------------------------------------------------------ the guns
  // Apocalypse arsenal (JasprApocalypse Arsenal.java; diamond hoe carriers, NBT JasprApocalypse {id, serial, rounds}):
  // [class, reload ms (server's base, before Speed Loader), extra]. Extra: q = suppressed, c = flash colour (energy).
  var APOC = {
    rifle: ['rifle', 2400], shotgun: ['pump', 3000], railgun: ['rail', 4200, { c: [0.55, 0.95, 1.0] }],
    whisper: ['smg', 2600, { q: 1 }], tempest: ['burst', 3200], bastion: ['heavy', 4600], longwatch: ['bolt', 2800],
    sunlance: ['plasma', 3800, { c: [1.0, 0.72, 1.0] }], adjudicator: ['dmr', 3100], cyclops: ['pump', 4000],
    frostbite: ['rail', 3500, { c: [0.75, 0.92, 1.0] }], sepulcher: ['pistol', 1800], vesper: ['pistol', 2200, { q: 1 }],
    ossuary: ['magnum', 3300], turnstile: ['revolver', 2400], cinder: ['mpistol', 2500], tunnelrat: ['carbine', 2700],
    blackbox: ['burst', 3100], quarantine: ['rifle', 2900], signal: ['bolt', 3200], gallows: ['bolt', 3400],
    watchtower: ['antimat', 4400], whiteout: ['bolt', 3300, { q: 1 }], bellringer: ['breakgun', 2600], lockjaw: ['pump', 3100],
    choir: ['breakgun', 3600], ashfall: ['drum', 4100], nullpoint: ['rail', 3600, { c: [0.7, 0.95, 1.0] }],
    cenotaph: ['rail', 4700, { c: [0.78, 0.62, 1.0] }], witchlight: ['plasma', 3400, { c: [0.5, 1.0, 0.55] }],
    stormcoil: ['rail', 4100, { c: [0.5, 0.65, 1.0] }], hexbreaker: ['plasma', 3800, { c: [1.0, 0.55, 0.95] }],
    pallbearer: ['heavy', 5000], ironpsalm: ['rotary', 5200], deadfrequency: ['pulse', 4200, { c: [0.35, 1.0, 0.9] }],
    rapture: ['pistol', 1900], g18: ['mpistol', 2100], magnum44: ['magnum', 3000], wingman: ['revolver', 2400],
    mozambique: ['breakgun', 2600, { small: 0.7 }], portal_gun: ['portal', 0]
  };
  // Dungeon armoury (JasprDungeon ArmoryCatalog; horse armour / rod / shard / tear carriers, NBT JasprPenitentArmory
  // {kind:"weapon", id, uuid, rounds, loading, readyAt, reloadAt}): [class, reload ms, burst (rounds and shots per trigger),
  // extra]. Casters have no ammunition; a cast moves readyAt on. p = the cast's particle (vanilla id), c = flash colour.
  var DUNGEON = {
    BAPTIST_NEEDLER: ['carbine', 1400, 1], BELFRY_REPEATER: ['burst', 1800, 3], CARRION_SCATTERGUN: ['pump', 1800, 1],
    OPALINE_RAIL: ['rail', 2400, 1, { c: [0.92, 0.85, 1.0] }], FOUNDRY_CANNON: ['magnum', 2200, 1], MENAGERIE_DARTER: ['pistol', 1600, 1, { q: 1 }],
    SCRIPTORIUM_SIPHON: ['pistol', 1700, 1], VESPER_DRILL: ['rifle', 1900, 1], PAUPERS_RICOCHET: ['revolver', 1800, 1],
    ASTRAL_ORRERY: ['plasma', 2100, 1, { c: [1.0, 0.95, 0.7] }], LABYRINTH_DOUBLE: ['burst', 1800, 2], LAST_ABSOLUTION: ['bolt', 2400, 1],
    CARRION_CENSER: ['magic', 0, 0, { p: 17 }], OPAL_PRISM: ['magic', 0, 0, { p: 10 }], SUNLESS_BRAND: ['magic', 0, 0, { p: 26 }],
    PALE_LANTERN: ['magic', 0, 0, { p: 13 }]
  };
  // Item ids that can carry a gun (the native reader skips every other stack without touching its NBT).
  var CARRIERS = { 293: 1, 417: 1, 418: 1, 419: 1, 369: 1, 409: 1, 370: 1 };
  var FLASH = {   // core and rim colours of the muzzle flash styles (rgb 0..1); energy guns tint theirs with c
    gun: { core: [1.0, 0.96, 0.82], rim: [1.0, 0.62, 0.2] }, wide: { core: [1.0, 0.94, 0.78], rim: [1.0, 0.55, 0.16] },
    rail: { core: [0.92, 0.98, 1.0], rim: [0.45, 0.85, 1.0] }, plasma: { core: [1.0, 0.92, 1.0], rim: [0.8, 0.45, 1.0] },
    portal: { core: [0.9, 0.95, 1.0], rim: [0.12, 0.56, 1.0] }, suppressed: { core: [1.0, 0.86, 0.62], rim: [0.9, 0.5, 0.2] }
  };
  var PORTAL_BLUE = [0.12, 0.56, 1.0], PORTAL_ORANGE = [1.0, 0.55, 0.0];

  // ------------------------------------------------------------------------------------------------ curves
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function smooth(u) { u = clamp(u, 0, 1); return u * u * (3 - 2 * u); }
  function bump(u) { if (u <= 0 || u >= 1) return 0; var s = Math.sin(Math.PI * u); return s * s; }
  /** One shot's kick: an ease-out rise to 1 at `a` ticks, then a damped spring back through a small overshoot. */
  function kick(tau, a, w, z) {
    if (!(tau > 0)) return 0;
    if (tau < a) { var u = tau / a; return 1 - (1 - u) * (1 - u); }
    var s = tau - a; if (s > 80) return 0;
    var wd = w * Math.sqrt(Math.max(1e-6, 1 - z * z));
    return Math.exp(-z * w * s) * (Math.cos(wd * s) + (z * w / wd) * Math.sin(wd * s));
  }
  /** What one shot adds to sustained fire: rises over about a tick, fades over T. */
  function drift(tau, T) { return tau > 0 && tau < 12 * T ? (1 - Math.exp(-tau / 1.2)) * Math.exp(-tau / T) : 0; }
  /** Soft limit: linear for small values, never beyond m. */
  function soft(v, m) { return m > 0 ? m * Math.tanh(v / m) : 0; }
  /** Deterministic per-shot randomness in [0, 1) (tests and previews see exactly what the game shows). */
  function rnd(seq, k) { var x = Math.sin(seq * 12.9898 + k * 78.233) * 43758.5453; return x - Math.floor(x); }

  // ------------------------------------------------------------------------------------------------ state
  var MAX_EVENTS = 16, EVENT_LIFE = 60;
  var ticks = 0, seq = 0, enabled = true;
  var stats = { ticks: 0, reads: 0, guns: 0, shots: 0, bursts: 0, reloads: 0, reloadsDone: 0, dry: 0, casts: 0, portal: 0, kicks: 0,
    particles: 0, aligned: 0, poses: 0, flashes: 0 };
  function Hand() { this.stack = null; this.gun = null; this.slot = -1; this.events = []; this.pending = []; this.reload = null; }
  var hands = [new Hand(), new Hand()];

  function geometry(id) {
    var d = typeof JasprGunFXData !== 'undefined' && JasprGunFXData ? JasprGunFXData : null;
    return d && (d[id] || d.$sprite) || null;
  }
  /** The gun a descriptor (what the native reader took from a stack) describes, or null. */
  function identify(desc) {
    if (!desc) return null;
    var g = null, def, extra;
    if (desc.apoc && desc.apoc.id && Object.prototype.hasOwnProperty.call(APOC, desc.apoc.id)) {
      def = APOC[desc.apoc.id]; extra = def[2] || {};
      var speed = clamp(desc.apoc.speed | 0, 0, 5);
      g = { source: 'apoc', id: desc.apoc.id, serial: String(desc.apoc.serial || ''), rounds: desc.apoc.rounds | 0, cost: 1, burst: 1,
        cls: def[0], reloadTicks: Math.round(def[1] * (1 - 0.15 * speed) / 50), extra: extra, geo: geometry(desc.apoc.id),
        portal: def[0] === 'portal', magic: false, readyAt: 0, reloadAt: 0 };
    } else if (desc.dungeon && desc.dungeon.kind === 'weapon' && Object.prototype.hasOwnProperty.call(DUNGEON, desc.dungeon.id)) {
      def = DUNGEON[desc.dungeon.id]; extra = def[3] || {};
      g = { source: 'dungeon', id: desc.dungeon.id, serial: String(desc.dungeon.uuid || ''), rounds: desc.dungeon.rounds | 0,
        cost: Math.max(1, def[2] | 0), burst: Math.max(1, def[2] | 0), cls: def[0], reloadTicks: Math.round(def[1] / 50), extra: extra,
        geo: geometry(def[0] === 'magic' ? '$caster' : '$sprite'), portal: false, magic: def[0] === 'magic', readyAt: +desc.dungeon.readyAt || 0, reloadAt: +desc.dungeon.reloadAt || 0 };
    }
    if (g) g.ident = g.source + ':' + g.id + ':' + g.serial;
    return g;
  }

  // ------------------------------------------------------------------------------------------------ events
  function emit(h, kind, out, extra) {
    var hand = hands[h], g = hand.gun, P = g && CLASSES[g.cls];
    if (!P) return null;
    var e = { kind: kind, t: ticks, seq: ++seq, t0: undefined, color: extra && extra.color || null };
    hand.events.push(e);
    while (hand.events.length > MAX_EVENTS) hand.events.shift();
    if (kind === 'shot' || kind === 'portal' || kind === 'cast') {
      // a shot ends a reload animation (it cannot be reloading any more), easing out over a few ticks instead of snapping
      if (kind === 'shot') { stats.shots++; if (hand.reload && hand.reload.cut === undefined) hand.reload.cut = ticks; }
      else if (kind === 'portal') stats.portal++; else stats.casts++;
      if (P.cam && out.firstPerson) {
        var j = 0.85 + 0.3 * rnd(e.seq, 21);
        out.kicks.push({ pitch: P.cam.pitch * j, yaw: P.cam.yaw * (2 * rnd(e.seq, 22) - 1), roll: P.cam.roll * (2 * rnd(e.seq, 23) - 1),
          back: P.cam.back, ms: P.cam.ms });
      }
      if (out.vfx < 2) particlesFor(h, e, P, g, out);
    } else if (kind === 'dry') stats.dry++;
    return e;
  }
  function particlesFor(h, e, P, g, out) {
    var lean = out.vfx === 1;
    if (e.kind === 'shot' && P.casing && !P.ejectOnCycle) out.particles.push({ kind: 'casing', hand: h, item: P.casing === 'shell' ? 331 : 371, seq: e.seq });
    if (e.kind === 'shot' && P.casing && P.ejectOnCycle && P.cycle) hands[h].pending.push({ due: ticks + Math.round(P.cycle.at + P.cycle.len * 0.35), kind: 'casing', item: P.casing === 'shell' ? 331 : 371 });
    if (e.kind === 'shot' && !lean && (P.smoke || (g.extra && g.extra.q))) out.particles.push({ kind: 'smoke', hand: h, n: Math.max(1, P.smoke | 0), seq: e.seq });
    if (e.kind === 'shot' && P.spark && !lean) out.particles.push({ kind: 'spark', hand: h, n: P.spark, seq: e.seq });
    if (e.kind === 'portal' && P.dust) out.particles.push({ kind: 'dust', hand: h, n: lean ? 2 : P.dust, color: e.color || PORTAL_BLUE, seq: e.seq });
    if (e.kind === 'cast' && P.spell) out.particles.push({ kind: 'spell', hand: h, n: lean ? 2 : P.spell, id: g.extra && g.extra.p || 17, seq: e.seq });
  }
  function startReload(h, dur, why) {
    var hand = hands[h];
    if (reloading(hand) && ticks - hand.reload.t < 10) return;     // the same reload seen twice (sound and data)
    // shots still queued to spread out merged updates are cosmetic: the server does not reload while it is still firing
    for (var i = hand.pending.length - 1; i >= 0; i--) if (hand.pending[i].kind === 'shot') hand.pending.splice(i, 1);
    hand.reload = { t: ticks, t0: undefined, dur: Math.max(8, dur | 0), done: -1, why: why };
    stats.reloads++;
  }
  function finishReload(h) {
    var r = hands[h].reload;
    if (r && r.done < 0) { r.done = ticks; stats.reloadsDone++; }
    else if (!r) {                                                  // a reload we never saw start: a short dip and the closing flourish
      hands[h].reload = { t: ticks, t0: undefined, dur: 10, done: ticks + 3, why: 'late' };
      stats.reloadsDone++;
    }
  }
  function reset(hand) { hand.events = []; hand.pending = []; hand.reload = null; }
  /** A reload animation that is still running (not ended by a shot). */
  function reloading(hand) { return !!(hand.reload && hand.reload.cut === undefined); }

  function compare(h, a, b, out) {
    if (b.magic) { if (b.readyAt > a.readyAt) emit(h, 'cast', out); return; }
    if (b.portal) return;                                           // Portal Gun shots come from the action bar
    if (b.source === 'dungeon' && b.reloadAt > 0 && b.reloadAt !== a.reloadAt) startReload(h, b.reloadTicks, 'nbt');
    if (b.rounds < a.rounds) {
      var drop = a.rounds - b.rounds, i;
      if (b.source === 'dungeon') {
        // one trigger pays for its whole burst; the later rounds leave 11 ticks apart (the server's Pending jobs)
        var pulls = Math.max(1, Math.round(drop / b.burst)), n = Math.min(6, pulls * b.burst);
        if (b.burst > 1) stats.bursts++;
        for (i = 0; i < n; i++) { var delay = (i % b.burst) * 11 + Math.floor(i / b.burst) * 2; if (delay === 0) emit(h, 'shot', out); else hands[h].pending.push({ due: ticks + delay, kind: 'shot' }); }
      } else {
        // one round per shot, each with its own update (Apocalypse bursts included); merged updates are spread out
        emit(h, 'shot', out);
        for (i = 1; i < Math.min(4, drop); i++) hands[h].pending.push({ due: ticks + 2 * i, kind: 'shot' });
      }
    } else if (b.rounds > a.rounds) finishReload(h);
  }
  /** A held stack changed (a new object in that hand): compare it with the last one. */
  function update(h, stack, desc, slot, out) {
    var hand = hands[h], g = identify(desc), prev = hand.gun;
    stats.reads++;
    var same = !!(prev && g && prev.ident === g.ident && (h === 1 || hand.slot === slot));
    if (same) { compare(h, prev, g, out); out.align[h] = hand.stack; }
    else { reset(hand); if (g) stats.guns++; }
    hand.gun = g; hand.stack = stack; hand.slot = slot;
  }

  /**
   * One client tick. input: {main, mainDesc, off, offDesc (a descriptor only when that hand's stack object changed,
   * else undefined), slot, click (a use click was processed this tick), overlay (a new action-bar line, or null),
   * reloadSound (the Apocalypse reload sound reached this player), firstPerson, vfx (0 all, 1 decreased, 2 off)}.
   * Returns {align: [old stack or null, ...] (the renderer may keep showing the new stack as the same item), kicks,
   * particles}.
   */
  function tick(input) {
    ticks++; stats.ticks++;
    var out = { align: [null, null], kicks: [], particles: [], firstPerson: !!input.firstPerson, vfx: input.vfx | 0 };
    if (!enabled) return out;
    if (input.mainDesc !== undefined) update(0, input.main, input.mainDesc, input.slot | 0, out);
    else if (input.main !== hands[0].stack) { hands[0].stack = input.main; }
    if (input.offDesc !== undefined) update(1, input.off, input.offDesc, -1, out);
    else if (input.off !== hands[1].stack) { hands[1].stack = input.off; }
    var main = hands[0].gun;
    if (main && input.reloadSound && main.source === 'apoc' && !main.portal) startReload(0, main.reloadTicks, 'sound');
    if (main && main.portal && typeof input.overlay === 'string') {
      // the Portal Gun's own action-bar line (PortalGun.fire): which portal opened, or no surface (a dry shot)
      var line = plain(input.overlay);
      if (line.indexOf('Blue portal set.') === 0) emit(0, 'portal', out, { color: PORTAL_BLUE });
      else if (line.indexOf('Orange portal set.') === 0) emit(0, 'portal', out, { color: PORTAL_ORANGE });
      else if (line.indexOf('No surface in range.') === 0) emit(0, 'dry', out);
    }
    if (main && input.click && !main.portal && !main.magic && main.rounds < main.cost && !reloading(hands[0])) emit(0, 'dry', out);
    for (var h = 0; h < 2; h++) {
      var hand = hands[h];
      for (var i = 0; i < hand.pending.length; i++) {
        var p = hand.pending[i];
        if (p.due > ticks) continue;
        hand.pending.splice(i--, 1);
        if (!hand.gun) continue;
        if (p.kind === 'shot') emit(h, 'shot', out);
        else if (p.kind === 'casing' && out.vfx < 2) out.particles.push({ kind: 'casing', hand: h, item: p.item, seq: ++seq });
      }
      while (hand.events.length && ticks - hand.events[0].t > EVENT_LIFE) hand.events.shift();
      var r = hand.reload;
      if (r && (ticks - r.t > Math.max(r.dur + 60, (r.done >= 0 ? r.done - r.t : 0) + 30) || (r.cut !== undefined && ticks - r.cut > 4))) hand.reload = null;
    }
    stats.kicks += out.kicks.length; stats.particles += out.particles.length;
    return out;
  }

  /** An action-bar line without formatting codes and leading blanks. */
  function plain(s) { var o = '', i = 0; s = String(s); while (i < s.length && s.charCodeAt(i) <= 32) i++; for (; i < s.length; i++) { if (s.charCodeAt(i) === 167) { i++; continue; } o += s.charAt(i); } return o; }

  // ------------------------------------------------------------------------------------------------ the pose
  function startOf(e, now) { if (e.t0 === undefined) e.t0 = now - e.t <= 1.5 ? Math.max(now, e.t) : e.t; return e.t0; }
  function addCycle(c, tau, amp, acc, seed) {
    if (!c) return;
    var u = (tau - c.at) / c.len;
    if (c.type === 'spin') {   // rotary barrels: a fast shiver that fades
      if (tau <= 0 || tau > 4 * c.len) return;
      var f = Math.exp(-tau / (c.len * 0.45)) * amp;
      acc.x += c.amp * f * Math.sin(tau * 5.3 + seed); acc.y += c.amp * f * Math.cos(tau * 6.1 + seed); return;
    }
    if (c.type === 'vent') {   // energy weapons: the shove rings out as a wobble
      var s = tau - c.at; if (s <= 0 || s > 4 * c.len) return;
      var e = Math.exp(-s / (c.len * 0.35)) * Math.sin(s * 2 * Math.PI / (c.len * 0.33)) * amp * c.amp;
      acc.roll += e; acc.yaw += 0.35 * e; return;
    }
    if (u <= 0 || u >= 1) return;
    if (c.type === 'slide') { acc.z += c.amp * bump(u) * amp; return; }
    if (c.type === 'thump') { acc.y -= 0.01 * bump(u) * amp; acc.climb -= 0.8 * bump(u) * amp; return; }
    if (c.type === 'hammer') { var b = bump(u) * c.amp * amp; acc.roll -= 2.5 * b; acc.climb -= 1.4 * b; return; }
    if (c.type === 'pump') {   // the fore-end pulled back and driven home: the whole gun rocks with it
      var p = smooth(u / 0.4) * (1 - smooth((u - 0.55) / 0.45)) * c.amp * amp;
      acc.z += 0.07 * p; acc.y -= 0.02 * p; acc.roll -= 5 * p; acc.climb -= 2 * p; return;
    }
    if (c.type === 'bolt') {   // turned up, drawn back, driven forward, turned down
      var turn = smooth(u / 0.22) * (1 - smooth((u - 0.78) / 0.22)), draw = smooth((u - 0.25) / 0.2) * (1 - smooth((u - 0.52) / 0.2));
      acc.roll += 16 * turn * c.amp * amp; acc.y += 0.018 * turn * c.amp * amp; acc.z += 0.05 * draw * c.amp * amp; acc.climb += 1.5 * turn * c.amp * amp;
    }
  }
  function addReload(hand, P, now, acc) {
    var r = hand.reload, style = r && P && RELOADS[P.reload];
    if (!style) return false;
    var tau = now - startOf(r, now), lowerT = clamp(r.dur * 0.25, 3, 6);
    var predicted = Math.max(lowerT, r.dur - 7), finish = r.done >= 0 ? Math.max(lowerT, Math.min(predicted, r.done - r.t)) : predicted;
    var v = (tau - finish) / 10;
    if (v >= 1) return false;
    var cut = r.cut === undefined ? 1 : 1 - smooth((now - r.cut) / 3);
    if (cut <= 0) return false;
    var E = smooth(tau / lowerT) * (v < 0.15 ? 1 : 1 - smooth((v - 0.15) / 0.65)) * cut, L = style.lower, k;
    for (k in L) acc[k] += L[k] * E;
    if (tau > lowerT && v < 0.1) {
      var w = tau - lowerT, f = smooth(w / 3) * (v < 0 ? 1 : 1 - v / 0.1) * cut;
      if (style.work === 'bob') { acc.y += 0.008 * Math.sin(w * 0.55) * f; acc.roll += 1.5 * Math.sin(w * 0.37) * f; }
      else if (style.work === 'shells') { var ph = (w % 7) / 7, s = bump(ph / 0.45) * f; acc.y += 0.018 * s; acc.z -= 0.012 * s; acc.roll += 4 * s; }
      else if (style.work === 'spin') { acc.roll += 3 * Math.sin(w * 1.1) * f; }
      else if (style.work === 'hum') { acc.x += 0.0025 * Math.sin(w * 4.7) * f; acc.y += 0.0025 * Math.cos(w * 5.9) * f; }
    }
    if (style.seat) { var sb = bump((v + 0.05) / 0.35) * cut; for (k in style.seat) acc[k] += style.seat[k] * sb; }
    if (style.rack) { var rb = bump((v - 0.5) / 0.4) * cut; for (k in style.rack) acc[k] += style.rack[k] * rb; }
    return Math.max(1e-6, E);
  }
  var LIMIT = { x: 0.3, y: 0.3, z: 0.35, climb: 50, roll: 70, yaw: 30 };
  /**
   * The first-person pose of hand h (0 main, 1 off) at render time `now` (client ticks + partial ticks), drawn on the
   * left side when `left`. Null when nothing is animating. {x, y, z, climb, roll, yaw, pivot, axis, flash}: translate,
   * then turn about `pivot` (yaw about up, climb about right, roll about the bore `axis`); flash = the muzzle flash.
   */
  function pose(h, now, left) {
    var hand = hands[h], g = hand.gun;
    if (!enabled || !g || !g.geo) return null;
    var P = CLASSES[g.cls]; if (!P) return null;
    var geo = left ? g.geo.l : g.geo.r;
    var acc = { x: 0, y: 0, z: 0, climb: 0, roll: 0, yaw: 0 }, dc = 0, db = 0, live = false, flash = null, small = g.extra && g.extra.small || 1;
    for (var i = 0; i < hand.events.length; i++) {
      var e = hand.events[i], tau = now - startOf(e, now);
      if (tau < 0 || tau > EVENT_LIFE) continue;
      live = true;
      if (e.kind === 'dry') { var d = bump(tau / 3); acc.climb -= 1.6 * d; acc.z += 0.008 * d; acc.roll += 0.6 * d; continue; }
      var K = P.kick, ct = kick(tau, P.a, P.t.w, P.t.z), cr = kick(tau, P.a, P.r.w, P.r.z);
      var rollR = 2 * rnd(e.seq, 1) - 1 + 0.3, amp = small;
      acc.z += K.back * (0.9 + 0.2 * rnd(e.seq, 2)) * ct * amp; acc.y += K.up * ct * amp; acc.x += K.side * (2 * rnd(e.seq, 3) - 1) * ct;
      acc.climb += K.climb * (0.85 + 0.3 * rnd(e.seq, 4)) * cr * amp; acc.roll += K.roll * rollR * cr; acc.yaw += K.yaw * (2 * rnd(e.seq, 5) - 1) * cr;
      if (P.drift) { var dr = drift(tau, P.drift.T); dc += P.drift.climb * dr; db += P.drift.back * dr; }
      addCycle(P.cycle, tau, amp, acc, e.seq);
      if (P.flash && (e.kind === 'shot' || e.kind === 'portal') && tau < P.flash.life) flash = { e: e, tau: tau };
    }
    var reloading = addReload(hand, P, now, acc);   // the reload's envelope (0..1), false when none runs
    if (!live && !reloading) return null;
    if (P.drift) { acc.climb += soft(dc, P.drift.max); acc.z += soft(db, P.drift.max * 0.01); }
    for (var k in LIMIT) acc[k] = soft(acc[k], LIMIT[k]);
    // the kick turns about the grip or the shoulder; the hands work a reload about the grip
    var sh = (P.shoulder || 0) * (1 - (reloading || 0)), pv = [geo.pivot[0] + (geo.butt[0] - geo.pivot[0]) * sh, geo.pivot[1] + (geo.butt[1] - geo.pivot[1]) * sh,
      geo.pivot[2] + (geo.butt[2] - geo.pivot[2]) * sh];
    var mirror = left ? -1 : 1;
    var out = { x: acc.x * mirror, y: acc.y, z: acc.z, climb: acc.climb, roll: acc.roll * mirror, yaw: acc.yaw * mirror, pivot: pv, axis: geo.axis, flash: null };
    if (flash) out.flash = flashOf(P, g, geo, flash.e, flash.tau, small);
    stats.poses++;
    return out;
  }
  function flashOf(P, g, geo, e, tau, small) {
    var F = P.flash, quiet = g.extra && g.extra.q, style = FLASH[quiet ? 'suppressed' : F.style] || FLASH.gun;
    // full size in the first frame, then it fades while it opens up a little
    var life = F.life, u = clamp(tau / life, 0, 1), I = Math.pow(1 - u, 1.6), rim = style.rim, core = style.core;
    if (g.extra && g.extra.c) rim = g.extra.c;
    if (e.color) { rim = e.color; core = [0.75 + 0.25 * e.color[0], 0.75 + 0.25 * e.color[1], 0.75 + 0.25 * e.color[2]]; }
    var size = F.size * small * (quiet ? 0.35 : 1) * (0.85 + 0.35 * u), len = F.len * small * (quiet ? 0.2 : 1) * (0.9 + 0.3 * u);
    return { at: geo.muzzle, axis: geo.axis, size: size, len: len, spikes: F.spikes, rot: rnd(e.seq, 7) * Math.PI * 2,
      seed: e.seq, intensity: I * (quiet ? 0.55 : 1), core: core, rim: rim, style: quiet ? 'suppressed' : F.style };
  }

  // ------------------------------------------------------------------------------------------------ flash geometry
  // The muzzle flash as GL_QUADS of coloured vertices [x, y, z, r, g, b, a] in the hand frame, drawn additively over the
  // gun (no depth test, no depth write) right after it: a hot hexagonal core, a star of tapered spikes across the bore
  // (what the eye sees from behind the gun) and two crossed flares along it (what it sees from the side). Bounded: at
  // most 6 + 9 + 2 quads.
  function cross3(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function unit3(a) { var l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }
  function at3(c, a, sa, b, sb) { return [c[0] + a[0] * sa + b[0] * sb, c[1] + a[1] * sa + b[1] * sb, c[2] + a[2] * sa + b[2] * sb]; }
  function vtx(p, col, alpha) { return [p[0], p[1], p[2], col[0], col[1], col[2], clamp(alpha, 0, 1)]; }
  function radial(right, up, phi) { var c = Math.cos(phi), s = Math.sin(phi); return [right[0] * c + up[0] * s, right[1] * c + up[1] * s, right[2] * c + up[2] * s]; }
  function flashQuads(f) {
    var quads = [];
    if (!f || !(f.intensity > 0.01) || !(f.size > 0)) return quads;
    var fwd = unit3(f.axis), right = unit3(cross3(fwd, [0, 1, 0])), up = unit3(cross3(right, fwd));
    var I = clamp(f.intensity, 0, 1), c = at3(f.at, fwd, f.size * 0.2, up, 0), n = Math.max(3, Math.min(9, f.spikes | 0));
    var rail = f.style === 'rail', quiet = f.style === 'suppressed', k, phi;
    // core: six fat kites, a soft hexagon that is hottest in the middle
    var rc = f.size * (quiet ? 0.6 : 0.42);
    for (k = 0; k < 6; k++) {
      phi = f.rot + Math.PI * k / 3;
      quads.push([vtx(c, f.core, I), vtx(at3(c, radial(right, up, phi - 0.5), rc * 0.75, fwd, 0), f.core, 0.55 * I),
        vtx(at3(c, radial(right, up, phi), rc, fwd, 0), f.rim, 0), vtx(at3(c, radial(right, up, phi + 0.5), rc * 0.75, fwd, 0), f.core, 0.55 * I)]);
    }
    // spikes
    var w = f.size * (rail ? 0.1 : 0.17);
    for (k = 0; k < (quiet ? 3 : n); k++) {
      phi = f.rot + 0.4 + 2 * Math.PI * k / n;
      var L = f.size * (0.75 + 0.75 * rnd(f.seed, 30 + k)) * (rail && k % 2 ? 0.5 : 1) * (quiet ? 0.6 : 1), d = radial(right, up, phi), p = unit3(cross3(fwd, d));
      quads.push([vtx(c, f.core, I), vtx(at3(c, d, L * 0.3, p, w), f.rim, 0.8 * I), vtx(at3(c, d, L, p, 0), f.rim, 0), vtx(at3(c, d, L * 0.3, p, -w), f.rim, 0.8 * I)]);
    }
    // flares along the bore
    if (!quiet && f.len > 0) {
      var w2 = f.size * (rail ? 0.2 : 0.38), tip = at3(c, fwd, f.len, up, 0), axes = [right, up];
      for (var a = 0; a < 2; a++) {
        var b = axes[a];
        quads.push([vtx(at3(c, b, -w2, up, 0), f.core, 0.85 * I), vtx(at3(c, b, w2, up, 0), f.core, 0.85 * I),
          vtx(at3(tip, b, w2 * 0.3, up, 0), f.rim, 0), vtx(at3(tip, b, -w2 * 0.3, up, 0), f.rim, 0)]);
      }
    }
    return quads;
  }

  // ------------------------------------------------------------------------------------------------ api
  function clear() { hands = [new Hand(), new Hand()]; }
  function handInfo(h) { var x = hands[h]; return { gun: x.gun ? x.gun.ident : null, cls: x.gun ? x.gun.cls : null, rounds: x.gun ? x.gun.rounds : null,
    events: x.events.length, pending: x.pending.length, reloading: reloading(x) }; }
  return {
    VERSION: VERSION, CLASSES: CLASSES, RELOADS: RELOADS, APOC: APOC, DUNGEON: DUNGEON, CARRIERS: CARRIERS, FLASH: FLASH, LIMIT: LIMIT,
    tick: tick, pose: pose, identify: identify, clear: clear, stats: stats, handInfo: handInfo, flashQuads: flashQuads,
    carrier: function (item) { return CARRIERS[item] === 1; },
    stack: function (h) { return hands[h].stack; },
    gun: function (h) { return hands[h].gun; },
    ticks: function () { return ticks; },
    setEnabled: function (on) { enabled = !!on; if (!enabled) clear(); },
    enabled: function () { return enabled; },
    _test: { kick: kick, drift: drift, soft: soft, rnd: rnd, bump: bump, hands: function () { return hands; }, setTicks: function (t) { ticks = t; } }
  };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = JasprGunFXCore;
