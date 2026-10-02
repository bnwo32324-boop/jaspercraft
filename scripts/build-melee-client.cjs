'use strict';
// JasperCraft first-person melee rework (BetterCombat client stage "bettercombat8", owner request 2026-10-02).
// Replaces the BetterCombat 7 melee rig module (JASPR_MELEE_RIG) with the new first-person motion
// (client-mods/melee), drops the rig's third-person layer -- Mo' Bends owns third-person attacks -- and puts the
// native ModelBiped.setRotationAngles (DGZ) and the animation-packet handler (FKv) back exactly as they were.
// Input: the LIVE classes.js. Mo' Bends is the outermost stage, so it is taken off first; the output has no Mo' Bends:
// run scripts/build-mobends-client.cjs on candidate/melee-client/classes.js afterwards.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const mobends = require('./build-mobends-client.cjs');
const root = path.resolve(__dirname, '..');
const RIG_BEGIN = '/* JASPR_MELEE_RIG_BEGIN */', RIG_END = '/* JASPR_MELEE_RIG_END */';
const BEGIN = '/* JASPR_MELEE_FP_BEGIN */', END = '/* JASPR_MELEE_FP_END */';
const RIG_SHA = '74fa6f50364a987845b498c067c09330c20796602e3c0be5dc6b89c231a3c71a';
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
function fnText(s, name) {
  const at = s.indexOf('function ' + name + '('); if (at < 0) throw Error('missing native function ' + name);
  const next = s.indexOf('\nfunction ', at + 9); return s.slice(at, next < 0 ? s.length : next);
}
function once(s, a, b) { const n = s.split(a).length - 1; if (n !== 1) throw Error('expected exactly one "' + a.slice(0, 90) + '", found ' + n); return s.replace(a, () => b); }
function moduleText() {
  const read = f => fs.readFileSync(path.join(root, 'client-mods/melee', f), 'utf8');
  const body = read('melee-motion.js').replace(/\nif \(typeof module[^\n]+\n?$/, '\n') + '\n' + read('melee-native.js');
  return BEGIN + ' // ' + JSON.stringify({ sha256: sha(body) }) + '\n' + body + '\n' + END;
}
function build(input) {
  let s = mobends.unpatch(input);
  if (s.includes(BEGIN)) {
    // an earlier build of this stage: only the module changes
    const a = s.indexOf(BEGIN), b = s.indexOf(END, a);
    if (b < 0) throw Error('incomplete first-person melee module');
    s = s.slice(0, a) + moduleText() + s.slice(b + END.length);
  } else {
    if (!s.includes("build:'bettercombat7'")) throw Error('expected the BetterCombat 7 client (melee rig)');
    const a = s.indexOf(RIG_BEGIN), b = s.indexOf(RIG_END, a);
    if (a < 0 || b < 0) throw Error('melee rig module not found');
    const header = JSON.parse(s.slice(a + RIG_BEGIN.length, s.indexOf('\n', a)).trim().replace(/^\/\/\s*/, ''));
    if (header.sha256 !== RIG_SHA) throw Error('the melee rig module changed since it was audited: inspect before replacing');
    s = s.slice(0, a) + moduleText() + s.slice(b + RIG_END.length);
    // the rig renamed the native setRotationAngles and wrapped it; its wrapper lived in the module just replaced
    const original = fnText(s, 'JasprMeleeOriginalBiped');
    if (!original.includes('0.6661999821662903') || !original.includes('AGZ(a.lA,a.Ea);return;')) throw Error('ModelBiped.setRotationAngles mapping changed');
    s = once(s, 'function JasprMeleeOriginalBiped(', 'function DGZ(');
    // the animation-packet handler loses the rig's remote observation; vanilla swingArm runs as before
    s = once(s, 'JasprMeleeRemoteSwing(b,true);', '');
    s = once(s, 'JasprMeleeRemoteSwing(b,false);', '');
    s = once(s, "build:'bettercombat7',ticks", "build:'bettercombat8',ticks");
    s = once(s, "setAttribute('data-jaspr-combat-build','bettercombat7')", "setAttribute('data-jaspr-combat-build','bettercombat8')");
  }
  for (const gone of ['JasprMeleeThirdPlan', 'JasprMeleeApplyBody', 'JasprMeleeRemoteSwing', 'JasprMeleeOriginalBiped', RIG_BEGIN])
    if (s.includes(gone)) throw Error('third-person rig left over: ' + gone);
  if (s.split('function DGZ(').length !== 2) throw Error('expected one native DGZ');
  for (const name of ['JasprCombatTransform', 'JasprEpicPose', 'JasprEpicSwingOffset', 'JasprEpicEquipMatch', 'JasprEpicEquipFull', 'JasprMeleeEmptyArm', 'JasprMeleeDescriptor'])
    fnText(s, name);
  new vm.Script(s, { filename: 'classes.js' });
  return s;
}
if (require.main === module) {
  const inputPath = process.argv[2] || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
  const input = fs.readFileSync(inputPath, 'latin1'), output = build(input), dir = path.join(root, 'candidate/melee-client');
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const manifest = { inputSHA256: sha(input), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'), builtAt: new Date().toISOString(), next: 'node scripts/build-mobends-client.cjs candidate/melee-client/classes.js' };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Without Mo\' Bends: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, BEGIN, END, RIG_SHA, moduleText };
