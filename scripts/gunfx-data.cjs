'use strict';
// Gun geometry for the first-person gun animation stage (JASPR_GUNFX), measured from the apocalypse pack's item models:
// where the muzzle is (flash, smoke), where the hand grips the gun (recoil pivot) and where the butt ends (shoulder
// pivot for heavy guns), all in the first-person HAND frame the stage animates in: the frame after vanilla's
// transformSideFirstPerson (hand anchor at view (+-0.56, -0.52, -0.72)), before the item's own display transform. The
// display transform is applied the way the deployed client's ItemCameraTransforms.applyTransformSide (DqX) does it:
// translate, rotate X, rotate Y, rotate Z, scale, then RenderItem's translate(-0.5); the left hand uses the
// firstperson_lefthand entry with Y and Z rotations negated and X translation mirrored.
//
//   node scripts/gunfx-data.cjs            write client-mods/gunfx/gunfx-data.js
//   node scripts/gunfx-data.cjs --check    exit 1 when the committed file differs from what the models give now
const fs = require('node:fs'), path = require('node:path');
const ROOT = path.join(__dirname, '..');
const MODELS = path.join(ROOT, 'apocalypse-pack/assets/minecraft/models/item');
const OUT = path.join(ROOT, 'client-mods/gunfx/gunfx-data.js');
// Apocalypse gun id -> model file (the three original guns kept their older model names)
const MODEL_OF = { rifle: 'last_light', shotgun: 'requiem', railgun: 'gravebreaker' };
const IDS = ['rifle', 'shotgun', 'railgun', 'whisper', 'tempest', 'bastion', 'longwatch', 'sunlance', 'adjudicator', 'cyclops',
  'frostbite', 'sepulcher', 'vesper', 'ossuary', 'turnstile', 'cinder', 'tunnelrat', 'blackbox', 'quarantine', 'signal',
  'gallows', 'watchtower', 'whiteout', 'bellringer', 'lockjaw', 'choir', 'ashfall', 'nullpoint', 'cenotaph', 'witchlight',
  'stormcoil', 'hexbreaker', 'pallbearer', 'ironpsalm', 'deadfrequency', 'rapture', 'g18', 'magnum44', 'wingman', 'mozambique',
  'portal_gun'];

const name = e => String(e.__comment || e.name || '');
function box(list) {
  const lo = [0, 1, 2].map(i => Math.min(...list.map(e => Math.min(e.from[i], e.to[i]))));
  const hi = [0, 1, 2].map(i => Math.max(...list.map(e => Math.max(e.from[i], e.to[i]))));
  return { lo, hi };
}
/** Model-space (0..16 units) features of one gun model: muzzle centre, bore radius, grip top, butt end. */
function features(model) {
  const els = model.elements || [];
  if (!els.length) throw Error('model without elements');
  let mz = els.filter(e => /muzzle bore/i.test(name(e)));
  if (!mz.length) mz = els.filter(e => /muzzle|bore/i.test(name(e)) && !/housing|sight/i.test(name(e)));
  if (!mz.length) {
    const minZ = Math.min(...els.map(e => Math.min(e.from[2], e.to[2])));
    mz = els.filter(e => Math.min(e.from[2], e.to[2]) <= minZ + 0.75 && !/sight|bipod/i.test(name(e)));
  }
  const m = box(mz);
  // a bore element is a thin marker: the flash size follows the housing around it when there is one
  const housing = els.filter(e => /muzzle housing/i.test(name(e)));
  const hb = housing.length ? box(housing) : m;
  const radius = Math.max(0.5, Math.max(hb.hi[0] - hb.lo[0], hb.hi[1] - hb.lo[1]) / 2);
  let grips = els.filter(e => /grip/i.test(name(e)) && !/fore|pump|heel|lower/i.test(name(e)));
  if (!grips.length) grips = els.filter(e => /receiver|body/i.test(name(e)));
  const g = box(grips.length ? [grips[0]] : els);
  const all = box(els);
  return {
    muzzle: [(m.lo[0] + m.hi[0]) / 2, (m.lo[1] + m.hi[1]) / 2, m.lo[2]],
    radius,
    grip: [(g.lo[0] + g.hi[0]) / 2, g.hi[1], (g.lo[2] + g.hi[2]) / 2],
    butt: [8, (g.hi[1] + all.hi[1]) / 2, all.hi[2]],
    length: all.hi[2] - all.lo[2]
  };
}
const rad = d => d * Math.PI / 180;
function rotX(p, d) { const c = Math.cos(rad(d)), s = Math.sin(rad(d)); return [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c]; }
function rotY(p, d) { const c = Math.cos(rad(d)), s = Math.sin(rad(d)); return [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c]; }
function rotZ(p, d) { const c = Math.cos(rad(d)), s = Math.sin(rad(d)); return [p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]]; }
/** A model point (0..16) in the hand frame: DqX = T * Rx * Ry * Rz * S, then translate(-0.5). */
function handPoint(display, left, p) {
  const t = (display.translation || [0, 0, 0]).map(v => Math.max(-80, Math.min(80, v)) / 16);
  const r = (display.rotation || [0, 0, 0]).slice(), s = (display.scale || [1, 1, 1]).map(v => Math.max(-4, Math.min(4, v)));
  if (left) { r[1] = -r[1]; r[2] = -r[2]; }
  let q = [(p[0] / 16 - 0.5) * s[0], (p[1] / 16 - 0.5) * s[1], (p[2] / 16 - 0.5) * s[2]];
  q = rotZ(q, r[2]); q = rotY(q, r[1]); q = rotX(q, r[0]);
  return [q[0] + (left ? -1 : 1) * t[0], q[1] + t[1], q[2] + t[2]];
}
const round = v => Math.round(v * 10000) / 10000;
function handData(model, left) {
  const f = features(model), d = (model.display || {})[left ? 'firstperson_lefthand' : 'firstperson_righthand'];
  if (!d) throw Error('no first-person display transform');
  const scale = (d.scale || [1, 1, 1])[0];
  const muzzle = handPoint(d, left, f.muzzle), ahead = handPoint(d, left, [f.muzzle[0], f.muzzle[1], f.muzzle[2] - 4]);
  const axis = [ahead[0] - muzzle[0], ahead[1] - muzzle[1], ahead[2] - muzzle[2]], l = Math.hypot(...axis);
  return { muzzle: muzzle.map(round), axis: axis.map(v => round(v / l)), pivot: handPoint(d, left, f.grip).map(round),
    butt: handPoint(d, left, f.butt).map(round), bore: round(f.radius / 16 * scale), length: round(f.length / 16 * scale) };
}
function generate() {
  const guns = {};
  for (const id of IDS) {
    const file = path.join(MODELS, 'apocalypse_' + (MODEL_OF[id] || id) + '.json');
    const model = JSON.parse(fs.readFileSync(file, 'utf8'));
    guns[id] = { r: handData(model, false), l: handData(model, true) };
  }
  // Dungeon armoury guns and casters are vanilla flat items (horse armour, blaze rod, shard, tear) held with the
  // item/generated first-person transform: the far upper edge of the sprite stands in for a muzzle.
  const sprite = { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] };
  const spriteHand = left => {
    const d = left ? { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] } : sprite;
    const muzzle = handPoint(d, left, [1.5, 13, 8]), pivot = handPoint(d, left, [5, 4, 8]);
    const axis = [muzzle[0] - pivot[0], muzzle[1] - pivot[1], muzzle[2] - pivot[2]], l = Math.hypot(...axis);
    return { muzzle: muzzle.map(round), axis: axis.map(v => round(v / l)), pivot: pivot.map(round), butt: handPoint(d, left, [12, 2, 8]).map(round),
      bore: 0.03, length: 0.6 };
  };
  guns.$sprite = { r: spriteHand(false), l: spriteHand(true) };
  return guns;
}
function render(guns) {
  return '/* GENERATED by scripts/gunfx-data.cjs from apocalypse-pack/assets/minecraft/models/item -- do not edit by hand.\n' +
    ' * First-person hand-frame geometry per gun (r = right hand, l = left hand), in blocks: muzzle centre, bore axis (unit),\n' +
    ' * grip pivot, butt end, bore radius and gun length. $sprite is the flat-item stand-in (dungeon armoury, casters). */\n' +
    'var JasprGunFXData = ' + JSON.stringify(guns).replace(/\},"/g, '},\n  "') + ';\n' +
    'if (typeof module !== \'undefined\' && module.exports) module.exports = JasprGunFXData;\n';
}
if (require.main === module) {
  const text = render(generate());
  if (process.argv.includes('--check')) {
    const now = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
    if (now !== text) { console.error('client-mods/gunfx/gunfx-data.js is out of date: run node scripts/gunfx-data.cjs'); process.exit(1); }
    console.log('gunfx-data up to date (' + Object.keys(generate()).length + ' entries)');
  } else {
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, text);
    console.log('wrote ' + OUT);
  }
}
module.exports = { generate, render, features, handPoint, IDS, MODEL_OF, OUT };
