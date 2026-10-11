'use strict';
// The gun animation stage inside the REAL client bundle (the candidate built from the live classes.js, loaded in node:vm
// by scripts/mobends-native-harness.cjs without starting the game; one load): the stage's reader on real engine NBT
// objects (NBTTagCompound Ce: setString GlQ, setInteger F_W, setLong Ds5, setTag F3I), so getSubCompound / getString /
// getInteger / getLong / getCompoundTag and the Java <-> JS string and long conversions are the engine's own. Only what
// needs a running game is stubbed: the item registry (C_q), the player's data manager (DlD), the world's particles (CsB),
// the Minecraft singleton (HEH) and log4j's getLogger (EEN, which cannot start in node:vm).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const builder = require('../scripts/build-gunfx-client.cjs');
const { loadClientSource } = require('../scripts/mobends-native-harness.cjs');

const LIVE = process.env.JASPR_LIVE_CLASSES || builder.LIVE;
test('the stage reads real engine NBT and animates a shot inside the real bundle', { skip: !fs.existsSync(LIVE) }, () => {
  const { output } = builder.build(fs.readFileSync(LIVE, 'latin1'));
  const client = loadClientSource(output, { filename: 'gunfx-candidate.js' });
  const fn = client.fn;
  try {
    // NBTTagCompound's static initialiser asks log4j for a logger, which cannot start in node:vm: hand it a stand-in
    fn.EEN = () => ({ logger: 'stand-in' });
    const S = s => fn.$rt_str(s);
    const compound = () => { const t = new fn.Ce(); fn.BMe(t); return t; };
    function apocalypse(rounds, speed) {
      const data = compound();
      fn.GlQ(data, S('id'), S('rifle')); fn.GlQ(data, S('arsenalMark'), S('jaspr-arsenal-v1'));
      fn.GlQ(data, S('serial'), S('8f7b5d0e-0000-4000-8000-000000000001')); fn.F_W(data, S('rounds'), rounds);
      const root = compound(); fn.F3I(root, S('JasprApocalypse'), data);
      if (speed) { const ab = compound(); fn.F_W(ab, S('speed_loader'), speed); const arm = compound(); fn.F3I(arm, S('Abilities'), ab); fn.F3I(root, S('JasprArmament'), arm); }
      return { rA: { $id: 293 }, bK: 1560, bg_: 0, bV: root };
    }
    function armoury(rounds, readyAt, reloadAt) {
      const data = compound();
      fn.F_W(data, S('version'), 1); fn.GlQ(data, S('kind'), S('weapon')); fn.GlQ(data, S('id'), S('BELFRY_REPEATER'));
      fn.GlQ(data, S('uuid'), S('c2eecce2-75ab-4de6-ae27-b39475fe5f14')); fn.F_W(data, S('rounds'), rounds);
      fn.Ds5(data, S('readyAt'), fn.N(readyAt)); fn.Ds5(data, S('reloadAt'), fn.N(reloadAt));
      const root = compound(); fn.F3I(root, S('JasprPenitentArmory'), data);
      return { rA: { $id: 417 }, bK: 0, bg_: 0, bV: root };
    }
    fn.C_q = item => (item ? item.$id | 0 : 0);
    const read = s => JSON.parse(JSON.stringify(fn.JasprGunFXRead(s)));
    assert.deepEqual(read(apocalypse(17, 2)), { item: 293, damage: 1560, apoc: { id: 'rifle', serial: '8f7b5d0e-0000-4000-8000-000000000001', rounds: 17, speed: 2 }, dungeon: null });
    assert.deepEqual(read(apocalypse(0)).apoc, { id: 'rifle', serial: '8f7b5d0e-0000-4000-8000-000000000001', rounds: 0, speed: 0 });
    assert.deepEqual(read(armoury(9, 1700000000, 0)).dungeon, { kind: 'weapon', id: 'BELFRY_REPEATER', uuid: 'c2eecce2-75ab-4de6-ae27-b39475fe5f14', rounds: 9, readyAt: 1700000000, reloadAt: 0 });
    assert.equal(fn.JasprGunFXRead({ rA: { $id: 293 }, bK: 0, bg_: 0, bV: compound() }), null, 'a carrier without gun data');

    // a tick on real stacks: the enum statics the hooks compare with exist, the shot is seen, the renderer keeps the item
    fn.H_(); fn.G9();
    assert.ok(fn.Lkg && fn.Lkf && fn.Kua && fn.Lkg !== fn.Lkf, 'TransformType and EnumHandSide statics');
    const spawned = [];
    fn.DlD = () => fn.Kua;
    fn.CsB = (...a) => { spawned.push(a[1]); return null; };
    const player = { b: 0.5, f: 70, c: 0.5, C: 0, bd: 0, s: 0, p: 0, t: 0, bx: { gP: 3 } };
    fn.HEH = { v: player, G: { lv: 0, QV: 0 }, da: null, bTh: 0, cj: null, fE: {}, cp: 0, buv: 0, J4: { UM: 0.5 } };
    const renderer = { bFQ: null, bpO: null };
    const first = apocalypse(18); fn.JasprGunFXTick(renderer, player, first, null); renderer.bFQ = first;
    const second = apocalypse(17); fn.JasprGunFXTick(renderer, player, second, null);
    const status = fn.JasprGunFX.status();
    assert.equal(status.stats.core_shots, 1, JSON.stringify(status));
    assert.equal(renderer.bFQ, second, 'no re-equip dip');
    assert.deepEqual(Array.from(spawned), [36], 'one casing through RenderGlobal.spawnParticle');
    assert.deepEqual(Object.keys(status.disabled), [], 'no part failed');
    // the third-person transform type leaves the matrix alone (no GL in node: any GL call would throw)
    assert.doesNotThrow(() => { fn.JasprGunFXPre(second, fn.Lkh || {}, 0); fn.JasprGunFXPost(); });
  } finally {
    client.dispose();
  }
});
