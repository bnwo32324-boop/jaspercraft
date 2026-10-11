/* JasperCraft first-person melee v5: dual-wield choreography for every main-hand x off-hand class pair.
 *
 * Each pair (cell) defines:
 *  - support styles: what the hand that is NOT striking does while the other strikes (guard, cock, counter, flow, tuck,
 *    brace, ready, duck), tied to the striker's wind-up, contact and stroke end, so the weapons move as one body;
 *  - the joint move: when the second hand clicks while the first is still winding up or striking, the second plays a
 *    move chosen for the pair (twin blades cross in an X, a dagger stabs through the opening a sword cut made, a light
 *    weapon jabs after a heavy one lands, twin axes chop together ...), optionally held so it lands just after;
 *  - optional chain overrides (a light weapon beside a heavy one keeps to quick jabs).
 * Cells come from a category table (blade, heavy, light, pole, tool) refined by same-class and signature pairs; every
 * cell is listed by matrix() for MELEE_ANIMATION_V5.md. Poses are authored for the right hand (the left mirrors).
 */
var JasprMeleeDual = (function (C, MV) {
  'use strict';
  var CATS = ['blade', 'heavy', 'light', 'pole', 'tool'];
  // support style of the hand that waits, by [striker category][waiting category]
  var STYLE = {
    blade: { blade: 'guard', heavy: 'brace', light: 'cock', pole: 'ready', tool: 'guard' },
    heavy: { blade: 'counter', heavy: 'brace', light: 'tuck', pole: 'duck', tool: 'counter' },
    light: { blade: 'flow', heavy: 'brace', light: 'counter', pole: 'ready', tool: 'flow' },
    pole: { blade: 'guard', heavy: 'brace', light: 'tuck', pole: 'ready', tool: 'guard' },
    tool: { blade: 'guard', heavy: 'brace', light: 'cock', pole: 'ready', tool: 'counter' }
  };
  // the second hand's move when it joins a strike already under way, by [first category][second category]:
  // a role of the second hand's class and an optional hold (ticks) so it lands after the first
  var JOINT = {
    blade: { blade: ['diag', 0, 'X-cross'], heavy: ['opener', 0, 'cut then cleave'], light: ['stab', 0.6, 'cut and stab'], pole: ['stab', 0.4, 'cut and thrust'], tool: ['diag', 0, 'cut and hack'] },
    heavy: { blade: ['flat', 1.0, 'blow then cut'], heavy: ['overhead', 0.4, 'double blow'], light: ['stab', 1.6, 'slam then jab'], pole: ['stab', 1.2, 'blow then thrust'], tool: ['opener', 0.8, 'blow then hack'] },
    light: { blade: ['flat', 0, 'stab then cut'], heavy: ['opener', 0, 'jab then blow'], light: ['slash', 0, 'scissor'], pole: ['stab', 0, 'jab and thrust'], tool: ['opener', 0, 'jab then hack'] },
    pole: { blade: ['diag', 0.6, 'thrust then cut'], heavy: ['opener', 0.6, 'thrust then blow'], light: ['stab', 0.8, 'thrust and jab'], pole: ['stab', 0.8, 'double thrust'], tool: ['opener', 0.6, 'thrust then hack'] },
    tool: { blade: ['diag', 0, 'hack and cut'], heavy: ['opener', 0.4, 'hack then blow'], light: ['stab', 0.6, 'hack and stab'], pole: ['stab', 0.4, 'hack and thrust'], tool: ['opener', 0.3, 'double hack'] }
  };
  // the class moves behind each role
  var ROLES = {
    sword: { diag: 'sword-diag', flat: 'sword-flat', stab: 'sword-thrust', slash: 'sword-flat', overhead: 'sword-cleave', opener: 'sword-diag' },
    katana: { diag: 'katana-kesa', flat: 'katana-dash', stab: 'katana-tsuki', slash: 'katana-kesa', overhead: 'katana-kabuto', opener: 'katana-kesa' },
    rapier: { diag: 'rapier-flick', flat: 'rapier-flick', stab: 'rapier-lunge', slash: 'rapier-flick', overhead: 'rapier-plunge', opener: 'rapier-lunge' },
    greatsword: { diag: 'great-sweep', flat: 'great-sweep', stab: 'great-thrust', slash: 'great-sweep', overhead: 'great-overhead', opener: 'great-sweep' },
    axe: { diag: 'axe-cleave', flat: 'axe-cleave', stab: 'axe-chop', slash: 'axe-cleave', overhead: 'axe-chop', opener: 'axe-chop' },
    greataxe: { diag: 'greataxe-execute', flat: 'greataxe-sweep', stab: 'greataxe-overhead', slash: 'greataxe-sweep', overhead: 'greataxe-overhead', opener: 'greataxe-overhead' },
    hammer: { diag: 'hammer-swing', flat: 'hammer-swing', stab: 'hammer-ram', slash: 'hammer-swing', overhead: 'hammer-slam', opener: 'hammer-slam' },
    flail: { diag: 'flail-lash', flat: 'flail-lash', stab: 'flail-crack', slash: 'flail-lash', overhead: 'flail-crack', opener: 'flail-whirl' },
    dagger: { diag: 'dagger-slash', flat: 'dagger-slash', stab: 'dagger-stab', slash: 'dagger-slash', overhead: 'dagger-plunge', opener: 'dagger-stab' },
    claw: { diag: 'claw-rake', flat: 'claw-swipe', stab: 'claw-lunge', slash: 'claw-swipe', overhead: 'claw-drop', opener: 'claw-rake' },
    spear: { diag: 'spear-sweep', flat: 'spear-sweep', stab: 'spear-jab', slash: 'spear-sweep', overhead: 'spear-skewer', opener: 'spear-thrust' },
    glaive: { diag: 'glaive-arc', flat: 'glaive-whirl', stab: 'glaive-thrust', slash: 'glaive-arc', overhead: 'glaive-fall', opener: 'glaive-arc' },
    scythe: { diag: 'scythe-reap', flat: 'scythe-reap', stab: 'scythe-high', slash: 'scythe-reap', overhead: 'scythe-high', opener: 'scythe-reap' },
    staff: { diag: 'staff-swing', flat: 'staff-swing', stab: 'staff-poke', slash: 'staff-swing', overhead: 'staff-fall', opener: 'staff-swing' },
    pickaxe: { diag: 'pick-swipe', flat: 'pick-swipe', stab: 'pick-hack', slash: 'pick-swipe', overhead: 'pick-hack', opener: 'pick-hack' },
    shovel: { diag: 'shovel-bash', flat: 'shovel-bash', stab: 'shovel-jab', slash: 'shovel-bash', overhead: 'shovel-slam', opener: 'shovel-jab' },
    hoe: { diag: 'hoe-hook', flat: 'hoe-hook', stab: 'hoe-chop', slash: 'hoe-hook', overhead: 'hoe-chop', opener: 'hoe-hook' },
    odd: { diag: 'odd-club', flat: 'odd-club', stab: 'odd-jab', slash: 'odd-club', overhead: 'odd-bonk', opener: 'odd-club' }
  };
  // Named pairs: same-class twins and classic combinations with their own feel (main class, off class).
  var SIGNATURE = {
    'sword+sword': { name: 'twin swords: alternating X-cuts', style: ['guard', 'guard'], joint: ['diag', 0.2] },
    'katana+katana': { name: 'twin katanas: crossing kesa cuts', style: ['guard', 'guard'], joint: ['diag', 0.2] },
    'sword+katana': { name: 'sword and katana: crossing cuts', style: ['guard', 'guard'], joint: ['diag', 0.2] },
    'katana+sword': { name: 'katana and sword: crossing cuts', style: ['guard', 'guard'], joint: ['diag', 0.2] },
    'katana+dagger': { name: 'daisho: long cut, short stab', style: ['cock', 'guard'], joint: ['stab', 0.5] },
    'sword+dagger': { name: 'sword and dagger: cut-and-stab', style: ['cock', 'guard'], joint: ['stab', 0.5] },
    'rapier+dagger': { name: 'main-gauche: the dagger parries while the rapier thrusts', style: ['guard', 'cock'], joint: ['slash', 0.2] },
    'rapier+rapier': { name: 'twin rapiers: alternating lunges', style: ['cock', 'cock'], joint: ['stab', 0.8] },
    'dagger+dagger': { name: 'twin daggers: flurry', style: ['counter', 'counter'], joint: ['slash', 0], offChain: ['dagger-stab', 'dagger-flick', 'dagger-slash', 'dagger-twirl'] },
    'dagger+sword': { name: 'dagger and sword: stab then cut', style: ['flow', 'cock'], joint: ['flat', 0] },
    'axe+axe': { name: 'twin axes: alternating chops', style: ['cock', 'cock'], joint: ['overhead', 0.6] },
    'greataxe+greataxe': { name: 'twin greataxes: heavy alternating blows', style: ['brace', 'brace'], joint: ['overhead', 1.0] },
    'hammer+hammer': { name: 'twin hammers: alternating slams', style: ['brace', 'brace'], joint: ['overhead', 1.0] },
    'greatsword+greatsword': { name: 'twin greatswords: crossing sweeps', style: ['brace', 'brace'], joint: ['diag', 0.8] },
    'hammer+dagger': { name: 'hammer and knife: slam, then a quick jab', style: ['tuck', 'brace'], joint: ['stab', 1.6], offChain: ['dagger-stab', 'dagger-flick', 'dagger-stab', 'dagger-twirl'] },
    'greataxe+dagger': { name: 'greataxe and knife: cleave, then a quick jab', style: ['tuck', 'brace'], joint: ['stab', 1.8], offChain: ['dagger-stab', 'dagger-flick', 'dagger-stab', 'dagger-twirl'] },
    'greatsword+dagger': { name: 'greatsword and knife: sweep, then a quick jab', style: ['tuck', 'brace'], joint: ['stab', 1.4] },
    'axe+dagger': { name: 'axe and knife: chop and stab', style: ['tuck', 'cock'], joint: ['stab', 1.0] },
    'axe+sword': { name: 'axe and sword: chop then cut', style: ['guard', 'counter'], joint: ['flat', 0.8] },
    'sword+axe': { name: 'sword and axe: cut then chop', style: ['brace', 'guard'], joint: ['opener', 0.2] },
    'spear+spear': { name: 'twin spears: piston thrusts', style: ['cock', 'cock'], joint: ['stab', 0.8] },
    'spear+dagger': { name: 'spear and knife: thrust and close in', style: ['tuck', 'ready'], joint: ['stab', 0.8] },
    'scythe+scythe': { name: 'twin scythes: mirrored reaping', style: ['flow', 'flow'], joint: ['slash', 0.4] },
    'flail+flail': { name: 'twin flails: alternating whirls', style: ['counter', 'counter'], joint: ['slash', 0.6] },
    'staff+staff': { name: 'twin staves: alternating strikes', style: ['counter', 'counter'], joint: ['stab', 0.4] },
    'claw+dagger': { name: 'claw and knife: rake and stab', style: ['counter', 'flow'], joint: ['stab', 0.2] },
    'claw+sword': { name: 'claw and sword: rake then cut', style: ['guard', 'flow'], joint: ['diag', 0.3] }
  };
  var cache = {};
  function catOf(cls) { var c = MV.classes[cls]; return c ? c.cat : 'tool'; }
  function pair(main, off) {
    var key = main + '+' + off;
    if (cache[key]) return cache[key];
    var sig = SIGNATURE[key], mc = catOf(main), oc = catOf(off);
    var p = { key: key, main: main, off: off, signature: !!sig,
      // style of the off hand while the main strikes, and of the main hand while the off strikes
      offSupport: sig ? sig.style[0] : STYLE[mc][oc], mainSupport: sig ? sig.style[1] : STYLE[oc][mc],
      jointOff: sig ? sig.joint : JOINT[mc][oc], jointMain: sig ? (sig.jointMain || JOINT[oc][mc]) : JOINT[oc][mc],
      offChain: sig && sig.offChain || null, mainChain: sig && sig.mainChain || null };
    p.name = sig ? sig.name : main + ' and ' + off + ': ' + (JOINT[mc][oc][2] || 'joined strikes');
    return (cache[key] = p);
  }
  function supportStyle(p, leaderRole) { return leaderRole === 'main' ? p.offSupport : p.mainSupport; }
  function chain(p, role) { return role === 'main' ? p.mainChain : p.offChain; }
  // the move the joining hand plays; the hold is applied by the caller through the move's `delay`
  function jointMove(p, role, otherStrike, cls) {
    var spec = role === 'off' ? p.jointOff : p.jointMain, roles = ROLES[cls];
    if (!spec || !roles) return null;
    var name = roles[spec[0]] || roles.opener;
    return name && MV.moves[name] ? name : null;
  }
  function jointDelay(p, role) { var spec = role === 'off' ? p.jointOff : p.jointMain; return spec ? spec[1] || 0 : 0; }

  // ---------------------------------------------------------------- support poses
  // Keys at the striker's wind-up end (A), contact (B) and just after its stroke (C), as grip (screen x, y, depth) and
  // a turn of the resting weapon (axis in view space, degrees), for the waiting hand in its own right-hand frame.
  // x grows outward (away from the screen centre), so the left hand's mirror reads the same.
  var R = C.REST_SCREEN;
  var STYLES = {
    // blade raised at the side and tipped inward, braced at the striker's contact
    guard: [[-0.08, 0.24, -0.08, [1, 0, 0, -12], [0, 0, 1, 14]], [-0.04, 0.18, -0.05, [1, 0, 0, -8], [0, 0, 1, 10]], [0, 0.08, -0.02, [1, 0, 0, -4], [0, 0, 1, 5]]],
    // drawn back and cocked, ready to strike next
    cock: [[0.16, 0.36, -0.12, [1, 0, 0, -16], [0, 0, 1, -24]], [0.22, 0.46, -0.14, [1, 0, 0, -22], [0, 0, 1, -32]], [0.16, 0.32, -0.1, [1, 0, 0, -14], [0, 0, 1, -20]]],
    // torque: a little in during the wind-up, out and back as the other hand lands
    counter: [[-0.08, 0.12, -0.04, [0, 0, 1, 8]], [0.2, -0.04, -0.08, [0, 0, 1, -18], [0, 1, 0, 10]], [0.12, -0.02, -0.04, [0, 0, 1, -8]]],
    // carried along with the other hand's swing (direction set by the striker; dx below is outward for a swing toward
    // the waiting side)
    flow: [[-0.05, 0.06, -0.02, [0, 0, 1, 6]], [0.16, 0.0, -0.04, [0, 0, 1, -12]], [0.1, 0.02, -0.02, [0, 0, 1, -6]]],
    // pulled in low and close, out of the way
    tuck: [[-0.12, -0.12, -0.12, [1, 0, 0, -20]], [-0.16, -0.18, -0.14, [1, 0, 0, -24]], [-0.08, -0.1, -0.08, [1, 0, 0, -14]]],
    // a heavy weapon barely moves: a small dip, then the rebound
    brace: [[0.02, -0.06, 0.0, [1, 0, 0, -6]], [0.03, -0.1, -0.02, [1, 0, 0, -10]], [0.01, -0.04, 0.0, [1, 0, 0, -4]]],
    // a polearm drawn back with its point forward
    ready: [[0.12, 0.12, -0.14, [1, 0, 0, -34]], [0.16, 0.08, -0.16, [1, 0, 0, -40]], [0.1, 0.08, -0.1, [1, 0, 0, -28]]],
    // dropped low under a wide arc
    duck: [[0.06, -0.2, -0.04, [1, 0, 0, -10]], [0.08, -0.3, -0.06, [1, 0, 0, -16]], [0.03, -0.16, -0.02, [1, 0, 0, -8]]]
  };
  function supportPoses(style, cls, leaderCls, leaderMove) {
    var keys = STYLES[style]; if (!keys) return [];
    var sw = (MV.classes[cls] || {}).weight || 0.5, lw = (MV.classes[leaderCls] || {}).weight || 0.5;
    var amp = (0.55 + 0.6 * lw) * (1.25 - 0.55 * sw);
    // a flow follows the striker: toward the waiting side's outside when the swing heads that way, else inward
    var dir = 1;
    if (style === 'flow' && leaderMove && leaderMove.dir) dir = leaderMove.dir[0] <= 0 ? 1 : -1;
    return keys.map(function (k) {
      var grip = [R[0] + k[0] * amp * dir, R[1] + k[1] * amp, R[2] + k[2] * amp], q = [0, 0, 0, 1];
      for (var i = 3; i < k.length; i++) { var r = k[i]; q = C.qmul(C.qexp(C.rotVec([r[0], r[1], r[2], r[3] * amp * (r[2] && style === 'flow' ? dir : 1)])), q); }
      return { g: C.view(grip), q: C.qnorm(q) };
    });
  }

  // ---------------------------------------------------------------- the full matrix (docs, tests)
  function offCapable(cls) { return cls !== 'claw'; }   // only tool/weapon item classes attack from the off hand
  function matrix() {
    var names = Object.keys(MV.classes), out = [];
    names.forEach(function (m) {
      names.forEach(function (o) {
        if (!offCapable(o)) return;
        var p = pair(m, o);
        out.push({ main: m, off: o, name: p.name, signature: p.signature, offSupport: p.offSupport, mainSupport: p.mainSupport,
          offJoins: jointMove(p, 'off', null, o), offJoinHold: jointDelay(p, 'off'), mainJoins: jointMove(p, 'main', null, m), mainJoinHold: jointDelay(p, 'main'),
          offChain: p.offChain, mainChain: p.mainChain });
      });
    });
    return out;
  }
  return { pair: pair, supportStyle: supportStyle, supportPoses: supportPoses, jointMove: jointMove, jointDelay: jointDelay, chain: chain,
    matrix: matrix, STYLE: STYLE, JOINT: JOINT, ROLES: ROLES, SIGNATURE: SIGNATURE, STYLES: STYLES, CATS: CATS, offCapable: offCapable };
}(typeof JasprMeleeCore !== 'undefined' ? JasprMeleeCore : require('./melee-core.js'),
  typeof JasprMeleeMoves !== 'undefined' ? JasprMeleeMoves : require('./melee-moves.js')));
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeDual;
