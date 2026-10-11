'use strict';
// The gun animation stage's core (client-mods/gunfx/gunfx-core.js), offline: which items are guns, how the server's own
// item data turns into shots / reloads / casts (synthetic NBT sequences), and the pose it draws (bounded, settles,
// starts at the first frame drawn after the tick that saw the event).
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
global.JasprGunFXData = require('../client-mods/gunfx/gunfx-data.js');
const core = require('../client-mods/gunfx/gunfx-core.js');
const data = require('../scripts/gunfx-data.cjs');

let objects = 0;
const stack = () => ({ id: ++objects });
const apoc = (id, rounds, serial, speed) => ({ item: 293, damage: 0, apoc: { id, serial: serial || 'serial-' + id, rounds, speed: speed || 0 }, dungeon: null });
const dungeon = (id, rounds, extra) => Object.assign({ item: 417, damage: 0, apoc: null,
  dungeon: Object.assign({ kind: 'weapon', id, uuid: 'uuid-' + id, rounds, readyAt: 0, reloadAt: 0 }, extra || {}) });

/** A small driver: one hand held at a time, ticks with optional item-data changes and inputs. */
function rig(opts) {
  core.clear();
  const s = { main: null, off: null, outs: [], slot: 0 };
  s.hold = (desc, hand) => { const o = stack(); s[hand === 1 ? 'off' : 'main'] = o; return s.tick({ [hand === 1 ? 'offDesc' : 'mainDesc']: desc }); };
  s.tick = (extra) => {
    const input = Object.assign({ main: s.main, off: s.off, slot: s.slot, click: false, overlay: null, reloadSound: false, firstPerson: true, vfx: 0 }, opts || {}, extra || {});
    const out = core.tick(input); s.outs.push(out); return out;
  };
  s.idle = (n) => { let last = null; for (let i = 0; i < n; i++) last = s.tick(); return last; };
  s.events = (h) => core._test.hands()[h || 0].events;
  s.shots = (h) => s.events(h).filter(e => e.kind === 'shot').length;
  s.base = core.stats.shots; s.totalShots = () => core.stats.shots - s.base;
  return s;
}

test('the gun table: every Apocalypse firearm, the Portal Gun, the dungeon armoury and casters, each with a class and geometry', () => {
  const ids = Object.keys(core.APOC);
  assert.equal(ids.length, 41, '40 arsenal guns + the Portal Gun');
  for (const id of data.IDS) assert.ok(core.APOC[id], id + ' in the table');
  for (const [id, def] of Object.entries(core.APOC)) {
    assert.ok(core.CLASSES[def[0]], id + ' class ' + def[0]);
    assert.ok(JasprGunFXData[id], id + ' geometry');
    const g = core.identify(apoc(id, 3));
    assert.equal(g.cls, def[0]); assert.equal(g.portal, id === 'portal_gun');
    if (id !== 'portal_gun') assert.ok(g.reloadTicks >= 30 && g.reloadTicks <= 110, id + ' reload ' + g.reloadTicks);
  }
  for (const [id, def] of Object.entries(core.DUNGEON)) {
    const g = core.identify(dungeon(id, 4));
    assert.equal(g.cls, def[0]); assert.equal(g.magic, def[0] === 'magic'); assert.ok(g.geo, id + ' sprite geometry');
  }
  // the server's own reload times (Arsenal.java Gun enum), Speed Loader -15% per rank
  assert.equal(core.identify(apoc('rifle', 1)).reloadTicks, 48);
  assert.equal(core.identify(apoc('rifle', 1, 's', 2)).reloadTicks, Math.round(2400 * 0.7 / 50));
  for (const bad of [null, { item: 293, apoc: { id: 'trench_blade', rounds: 0 } }, dungeon('WAX_SCALPEL', 0), dungeon('BAPTIST_NEEDLER', 0, { kind: 'relic' })])
    assert.equal(core.identify(bad), null);
  for (const c of [293, 417, 418, 419, 369, 409, 370]) assert.ok(core.carrier(c));
  assert.equal(core.carrier(276), false, 'a sword is never read');
});

test('the generated geometry matches the pack models (scripts/gunfx-data.cjs)', () => {
  assert.equal(data.render(data.generate()), require('node:fs').readFileSync(data.OUT, 'utf8').replace(/\r\n/g, '\n'));
  const rifle = JasprGunFXData.rifle.r;
  assert.ok(rifle.muzzle[2] < -0.7 && rifle.pivot[2] > 0 && rifle.butt[2] > rifle.pivot[2], 'muzzle ahead, butt behind the grip');
  assert.ok(Math.abs(Math.hypot(...rifle.axis) - 1) < 1e-3 && rifle.axis[2] < -0.99, 'the bore points into the screen');
});

test('a shot is the held gun\'s rounds dropping for the same serial; nothing else is a shot', () => {
  const s = rig();
  s.hold(apoc('rifle', 18));
  assert.equal(s.shots(), 0);
  const before = s.main, out = s.hold(apoc('rifle', 17));
  assert.equal(s.shots(), 1, 'one round down, one shot');
  assert.equal(out.align[0], before, 'the replacement stack keeps the renderer\'s item (no re-equip dip)');
  assert.equal(out.kicks.length, 1, 'one camera impulse');
  assert.ok(out.particles.some(p => p.kind === 'casing'), 'a casing');
  // the server rewriting the same gun (lore, armament data) is not a shot
  s.hold(apoc('rifle', 17)); assert.equal(s.shots(), 1);
  // another serial (a different gun) is never compared with the last one, even with fewer rounds
  s.hold(apoc('rifle', 3, 'another')); assert.equal(s.shots(), 0, 'switching guns starts clean');
  // moving the hotbar to another slot with the same gun does not count either
  s.hold(apoc('rifle', 3, 'another')); s.slot = 4; s.hold(apoc('rifle', 1, 'another')); assert.equal(s.shots(), 0);
  // the off hand is tracked the same way
  const o = rig(); o.hold(apoc('sepulcher', 12), 1); o.hold(apoc('sepulcher', 11), 1); assert.equal(o.shots(1), 1);
  assert.equal(o.shots(0), 0);
});

test('random item-data sequences without a rounds drop never produce a shot', () => {
  let seed = 7; const r = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
  for (let trial = 0; trial < 40; trial++) {
    const s = rig(); let rounds = 0; const id = Object.keys(core.APOC)[trial % 41], serial = 'x' + trial;
    for (let i = 0; i < 60; i++) {
      if (r() < 0.3) { rounds += Math.floor(r() * 3); s.hold(apoc(id, rounds, serial)); }
      else s.tick({ click: r() < 0.3, reloadSound: r() < 0.1, overlay: r() < 0.1 ? 'Something else entirely' : null });
    }
    assert.equal(s.totalShots(), 0, id + ': no shot without a drop');
  }
});

test('automatic fire: every update is a shot, the pose accumulates within its bounds and settles', () => {
  for (const id of ['whisper', 'ironpsalm', 'pallbearer', 'cinder', 'rifle']) {
    const s = rig(); let rounds = 28; s.hold(apoc(id, rounds));
    let peak = 0;
    for (let shot = 0; shot < 12; shot++) {
      s.hold(apoc(id, --rounds));
      for (let f = 0; f < 4; f++) {          // 4 frames per tick, 5 ticks between shots (240 ms)
        for (let k = 0; k < 4; k++) {
          const p = core.pose(0, core.ticks() + k / 4, false);
          assert.ok(p, 'animating while firing');
          for (const key of ['x', 'y', 'z', 'climb', 'roll', 'yaw']) {
            assert.ok(Number.isFinite(p[key]), key + ' finite');
            assert.ok(Math.abs(p[key]) <= core.LIMIT[key] + 1e-9, id + ' ' + key + ' bounded: ' + p[key]);
          }
          peak = Math.max(peak, p.climb);
        }
        if (f < 4) s.tick();
      }
    }
    assert.equal(s.shots(), 12, id + ' shots');
    assert.ok(peak > core.CLASSES[core.APOC[id][0]].kick.climb * 0.8, id + ' visible climb ' + peak);
    s.idle(70);
    assert.equal(core.pose(0, core.ticks() + 0.5, false), null, id + ' settles back to the vanilla pose');
  }
});

test('the event starts at the first frame drawn after it, so the kick and the flash are never skipped', () => {
  const s = rig(); s.hold(apoc('magnum44', 6)); s.hold(apoc('magnum44', 5));
  const t = core.ticks();
  const first = core.pose(0, t + 0.93, false);              // the first frame happens late in the tick
  assert.ok(first.flash && first.flash.intensity > 0.99, 'full flash in the first frame');
  assert.ok(Math.abs(first.climb) < 1e-6 && Math.abs(first.z) < 1e-6, 'the kick starts from rest');
  const later = core.pose(0, t + 0.93 + core.CLASSES.magnum.a, false);
  assert.ok(later.climb > core.CLASSES.magnum.kick.climb * 0.8, 'peak climb after the attack time');
  assert.ok(later.z > 0, 'kicks back toward the eye');
  const gone = core.pose(0, t + 0.93 + core.CLASSES.magnum.flash.life + 0.01, false);
  assert.equal(gone.flash, null, 'the flash lasts life ticks (a frame or two)');
  // an event first seen long after it happened (third person, a screen open) is not replayed from the start
  const q = rig(); q.hold(apoc('rifle', 6)); q.hold(apoc('rifle', 5)); q.idle(10);
  const p = core.pose(0, core.ticks() + 0.5, false);
  assert.ok(!p || !p.flash, 'no late flash');
});

test('kick curve: snappy rise, one small overshoot, settled', () => {
  const { kick } = core._test;
  for (const c of Object.values(core.CLASSES)) {
    assert.equal(kick(0, c.a, c.t.w, c.t.z), 0);
    assert.ok(Math.abs(kick(c.a, c.a, c.t.w, c.t.z) - 1) < 1e-9, 'peak 1 at a');
    let min = 0; for (let t = c.a; t < 40; t += 0.05) min = Math.min(min, kick(t, c.a, c.t.w, c.t.z));
    assert.ok(min > -0.35, 'overshoot below 35%');
    assert.ok(Math.abs(kick(40, c.a, c.t.w, c.t.z)) < 0.02, 'settled after 2 s');
    // continuous (no jumps between 0.01-tick steps)
    for (let t = 0; t < 20; t += 0.01) assert.ok(Math.abs(kick(t + 0.01, c.a, c.t.w, c.t.z) - kick(t, c.a, c.t.w, c.t.z)) < 0.05);
  }
});

test('bursts: Apocalypse bursts update per shot; dungeon bursts pay up front and fire 11 ticks apart', () => {
  const a = rig(); a.hold(apoc('tempest', 24)); a.hold(apoc('tempest', 23)); a.idle(10); a.hold(apoc('tempest', 22)); a.idle(10); a.hold(apoc('tempest', 21));
  assert.equal(a.shots(), 3);
  const d = rig(); d.hold(dungeon('BELFRY_REPEATER', 12)); d.hold(dungeon('BELFRY_REPEATER', 9));
  assert.equal(d.shots(), 1, 'the first round leaves at once');
  d.idle(10); assert.equal(d.shots(), 1); d.idle(1); assert.equal(d.shots(), 2, '11 ticks later');
  d.idle(11); assert.equal(d.shots(), 3, 'and 11 more');
  // the server drops the rest of a burst when the player switches away: so does the client
  const e = rig(); e.hold(dungeon('LABYRINTH_DOUBLE', 8)); e.hold(dungeon('LABYRINTH_DOUBLE', 6)); e.hold({ item: 276, apoc: null, dungeon: null });
  e.idle(15); assert.equal(e.totalShots(), 1, 'only the round that left before the switch');
});

test('reloads: the Apocalypse reload sound starts one, rounds rising finishes it; dungeon guns use reloadAt', () => {
  const s = rig(); s.hold(apoc('rifle', 1)); s.hold(apoc('rifle', 0));
  s.tick({ reloadSound: true });
  const h = core._test.hands()[0];
  assert.ok(h.reload && h.reload.why === 'sound');
  assert.equal(h.reload.dur, 48, 'the gun\'s own reload time');
  s.idle(12);
  const low = core.pose(0, core.ticks() + 0.5, false);
  assert.ok(low.x < -0.06 && low.roll > 25 && low.climb > 5, 'brought in toward the middle, canted and muzzle up while reloading');
  s.idle(30); s.hold(apoc('rifle', 18));
  assert.ok(h.reload.done >= 0, 'finished by the rounds coming back');
  s.idle(20);
  assert.equal(core.pose(0, core.ticks() + 0.5, false), null, 'back to rest');
  // the sound for another player's gun or with no Apocalypse gun in hand does nothing
  const n = rig(); n.hold(dungeon('VESPER_DRILL', 0)); n.tick({ reloadSound: true }); assert.equal(core._test.hands()[0].reload, null);
  // dungeon: reloadAt set (and loading) starts it, settled rounds finish it
  const d = rig(); d.hold(dungeon('VESPER_DRILL', 0)); d.hold(dungeon('VESPER_DRILL', 0, { reloadAt: 1e12 + 1900, readyAt: 1e12 + 1900 }));
  const dh = core._test.hands()[0]; assert.ok(dh.reload && dh.reload.why === 'nbt' && dh.reload.dur === 38);
  d.idle(30); d.hold(dungeon('VESPER_DRILL', 5)); assert.ok(dh.reload.done >= 0);
  // a reload whose start was never seen still gets the closing flourish
  const l = rig(); l.hold(apoc('lockjaw', 0)); l.hold(apoc('lockjaw', 5));
  assert.equal(core._test.hands()[0].reload.why, 'late');
  assert.ok(core.pose(0, core.ticks() + 0.5, false), 'animating');
  // a shot ends a reload animation at once
  const x = rig(); x.hold(apoc('rifle', 5)); x.tick({ reloadSound: true }); x.idle(8); x.hold(apoc('rifle', 4));
  const cutAt = core.ticks(), xr = core._test.hands()[0].reload;
  assert.ok(xr && xr.cut === cutAt, 'ended by the shot');
  const eased = core.pose(0, cutAt + 1.5, false), lowered = core.RELOADS.mag.lower.roll;
  assert.ok(eased.roll > 0 && eased.roll < lowered, 'eases out instead of snapping: ' + eased.roll);
  x.idle(5); assert.equal(core._test.hands()[0].reload, null, 'gone a few ticks later');
});

test('dry fire: a use click on an empty gun twitches it; never with rounds loaded or while reloading', () => {
  const s = rig(); s.hold(apoc('sepulcher', 0)); s.tick({ click: true });
  assert.equal(s.events().filter(e => e.kind === 'dry').length, 1);
  assert.equal(s.outs[s.outs.length - 1].kicks.length, 0, 'no camera impulse for a click');
  s.tick({ reloadSound: true }); s.tick({ click: true });
  assert.equal(s.events().filter(e => e.kind === 'dry').length, 1, 'not while reloading');
  const f = rig(); f.hold(apoc('sepulcher', 3)); f.tick({ click: true }); assert.equal(f.events().length, 0, 'loaded: the shot comes from the server');
  const d = rig(); d.hold(dungeon('BELFRY_REPEATER', 2)); d.tick({ click: true });
  assert.equal(d.events().filter(e => e.kind === 'dry').length, 1, 'a burst gun with fewer rounds than one trigger costs');
});

test('Portal Gun: its own action-bar line is the shot (blue or orange), "No surface" is a dry shot', () => {
  const s = rig(); s.hold(apoc('portal_gun', 0, ''));
  s.tick({ overlay: 'Blue portal set.  Fire again to open the other.  Next: orange.' });
  let e = s.events(); assert.equal(e.length, 1); assert.equal(e[0].kind, 'portal'); assert.deepEqual(e[0].color, [0.12, 0.56, 1.0]);
  s.tick({ overlay: '§6Orange portal set.§7  Portals linked.§8  Next: blue.' });
  e = s.events(); assert.equal(e[1].kind, 'portal'); assert.deepEqual(e[1].color, [1.0, 0.55, 0.0]);
  const p = core.pose(0, core.ticks() + 0.1, false); assert.deepEqual(p.flash.rim, [1.0, 0.55, 0.0], 'orange flash');
  s.tick({ overlay: 'No surface in range.' }); assert.equal(s.events()[2].kind, 'dry');
  s.tick({ overlay: 'Reloading Last Light (2.4s)...' }); assert.equal(s.events().length, 3, 'other lines are ignored');
  s.tick({ click: true }); assert.equal(s.events().length, 3, 'its clicks are not dry fire');
  const r = rig(); r.hold(apoc('rifle', 5)); r.tick({ overlay: 'Blue portal set.' }); assert.equal(r.events().length, 0, 'only with the Portal Gun in hand');
});

test('dungeon casters: readyAt moving on is a cast', () => {
  const s = rig(); s.hold(dungeon('OPAL_PRISM', 0, { readyAt: 1000 })); s.hold(dungeon('OPAL_PRISM', 0, { readyAt: 1000 }));
  assert.equal(s.events().length, 0);
  const out = s.hold(dungeon('OPAL_PRISM', 0, { readyAt: 2400 }));
  assert.equal(s.events()[0].kind, 'cast');
  assert.ok(out.particles.some(p => p.kind === 'spell' && p.id === 10), 'its own particle');
  const p = core.pose(0, core.ticks() + 0.1 + core.CLASSES.magic.a, false); assert.ok(p.z < 0, 'thrust away from the eye');
});

test('settings: camera impulses only in first person, particles honour the particle setting', () => {
  const t = rig({ firstPerson: false }); t.hold(apoc('rifle', 5)); const a = t.hold(apoc('rifle', 4));
  assert.equal(a.kicks.length, 0);
  const m = rig({ vfx: 2 }); m.hold(apoc('lockjaw', 5)); const b = m.hold(apoc('lockjaw', 4)); m.idle(20);
  assert.equal(b.particles.length, 0); assert.ok(m.outs.every(o => o.particles.length === 0), 'minimal: none at all');
  const d = rig({ vfx: 1 }); d.hold(apoc('lockjaw', 5)); const c = d.hold(apoc('lockjaw', 4));
  assert.ok(!c.particles.some(p => p.kind === 'smoke'), 'decreased: no smoke');
  const e = rig(); e.hold(apoc('lockjaw', 5)); e.hold(apoc('lockjaw', 4)); e.idle(12);
  assert.ok(e.outs.some(o => o.particles.some(p => p.kind === 'casing' && p.item === 331)), 'a pump shell leaves with the pump');
});

test('cycling motions and reload styles move the gun and come back', () => {
  const sample = (id, from, to, script) => {
    const s = rig(); s.hold(apoc(id, 5)); script(s); const t0 = core.ticks(); const out = [];
    for (let t = from; t <= to; t += 0.25) { while (core.ticks() < t0 + Math.floor(t)) s.tick(); out.push(core.pose(0, t0 + t, false)); }
    return out;
  };
  const bolt = sample('gallows', 0, 30, s => s.hold(apoc('gallows', 4)));
  const roll = bolt.slice(32, 80).map(p => p ? p.roll : 0);   // the bolt turns the gun over after the kick settles
  assert.ok(Math.max(...roll) > 8, 'the bolt cycle turns the gun: ' + Math.max(...roll));
  const pump = sample('lockjaw', 0, 30, s => s.hold(apoc('lockjaw', 4)));
  assert.ok(Math.max(...pump.slice(24, 60).map(p => p ? p.z : 0)) > 0.04, 'the pump pulls the gun back');
  for (const [id, style] of [['bellringer', 'break'], ['magnum44', 'cylinder'], ['railgun', 'cell'], ['pallbearer', 'belt'], ['lockjaw', 'shells'], ['sepulcher', 'pistol']]) {
    assert.equal(core.CLASSES[core.APOC[id][0]].reload, style);
    const r = sample(id, 0, 140, s => { s.hold(apoc(id, 0)); s.tick({ reloadSound: true }); });
    const mid = r[Math.round(core.identify(apoc(id, 0)).reloadTicks * 0.5 / 0.25)];
    const L = core.RELOADS[style].lower;
    for (const k of Object.keys(L)) if (Math.abs(L[k]) > 0.05 && k !== 'z') assert.ok(Math.sign(mid[k]) === Math.sign(L[k]), id + ' ' + k + ' lowered');
  }
});

test('flash geometry is bounded and fades', () => {
  const s = rig(); s.hold(apoc('watchtower', 3)); s.hold(apoc('watchtower', 2));
  const p = core.pose(0, core.ticks() + 0.2, false), q = core.flashQuads(p.flash);
  assert.ok(q.length >= 8 && q.length <= 17);
  for (const quad of q) { assert.equal(quad.length, 4); for (const v of quad) { assert.equal(v.length, 7); v.forEach(n => assert.ok(Number.isFinite(n))); assert.ok(v[6] >= 0 && v[6] <= 1); } }
  const far = Math.max(...q.flat().map(v => Math.hypot(v[0] - p.flash.at[0], v[1] - p.flash.at[1], v[2] - p.flash.at[2])));
  assert.ok(far < 0.8, 'stays near the muzzle: ' + far);
  const quiet = rig(); quiet.hold(apoc('whiteout', 3)); quiet.hold(apoc('whiteout', 2));
  const f2 = core.pose(0, core.ticks() + 0.2, false).flash; assert.equal(f2.style, 'suppressed'); assert.ok(f2.size < p.flash.size / 3, 'suppressed guns barely flash');
  assert.deepEqual(core.flashQuads(null), []);
});
