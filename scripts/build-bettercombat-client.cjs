'use strict';
// Better Combat client head (dual wielding, client build "bettercombat9"; combat rework 2026-10-11, workstream A).
// Input: the LIVE classes.js (read only). Replaces ONLY the Better Combat head region -- from "/* JASPR_BETTERCOMBAT_V1_BEGIN"
// up to the nested "/* JASPR_MELEE_FP_BEGIN */" -- with client-mods/bettercombat/bettercombat-head.js followed by the
// engine-derived functions already at the end of that head (JasprCombatSweepRender, JasprCombatPick, JasprCombatBox, kept
// verbatim). Every engine call site stays as it is; no other byte of the client changes. Running it on its own output gives
// the same output. Writes candidate/bettercombat/classes.js + manifest.json (latin1 in and out).
//   node scripts/build-bettercombat-client.cjs [input classes.js] [--check]
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const BEGIN = '/* JASPR_BETTERCOMBAT_V1_BEGIN', NEXT = '/* JASPR_MELEE_FP_BEGIN */', END = '/* JASPR_BETTERCOMBAT_V1_END */';
const TAIL = 'function JasprCombatSweepRender(';
const BUILD = 'bettercombat9';
// The engine-derived functions at the end of the head, as audited (TeaVM text with the engine's own CRLF breaks).
const TAIL_SHA = 'dafb9bf92c504071146e4a7c65b3cc18fb34b7afa69e6cefd2443c30cb13ffbc';
// rightClickMouse, whose per-hand loop body JasprCombatUse reproduces call for call (with the head's call site in it).
const RIGHT_CLICK_SHA = '38ee0ec3da6a6632589fb36d7b673281bb0c2322fb1c0261b61bf97d0a35db3f';
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
function count(s, a) { return s.split(a).length - 1; }
function fnText(s, name) {
  const at = s.indexOf('function ' + name + '(');
  if (at < 0) throw Error('missing native function ' + name);
  let end = s.indexOf('\nfunction ', at + 9);
  return s.slice(at, end < 0 ? s.length : end);
}
function headRange(s) {
  if (count(s, BEGIN) !== 1) throw Error('expected exactly one Better Combat head, found ' + count(s, BEGIN));
  if (count(s, NEXT) !== 1) throw Error('expected exactly one first-person melee module, found ' + count(s, NEXT));
  if (count(s, END) !== 1) throw Error('expected exactly one Better Combat end marker');
  const start = s.indexOf(BEGIN), end = s.indexOf(NEXT);
  if (!(start < end && end < s.indexOf(END))) throw Error('Better Combat head / melee module / end marker out of order');
  return { start, end };
}
function adapterText() {
  const text = fs.readFileSync(path.join(root, 'client-mods/bettercombat/bettercombat-head.js'), 'latin1').replace(/\r\n/g, '\n');
  if (!text.startsWith(BEGIN + ' */\n')) throw Error('the adapter must start with the head marker');
  if (/[^\x09\x0a\x20-\x7e]/.test(text)) throw Error('the adapter must be plain ASCII');
  if (text.includes(TAIL) || text.includes(NEXT)) throw Error('the adapter must not carry the engine-derived tail or the melee module');
  return text.endsWith('\n') ? text : text + '\n';
}
// Every engine name the adapter relies on, and what each must still mean in this client.
const NAMES = ['EZ5', 'EjD', 'C_q', 'DOj', 'EQX', 'CDe', 'GgT', 'Hc', 'Fz', 'CjH', 'CCH', 'CRE', 'ANt', 'EsL', 'EI1', 'BT', 'BF',
  'CZr', 'E0Z', 'GfQ', 'Fht', 'Eha', 'I$', 'Gi', 'BW', 'F78', 'FOL', 'DaN', 'Fru', 'Lg', 'FuF', 'BgN', 'CTM', 'CIb', 'Bq3', 'Y',
  'GGw', 'FYu', 'FX2', 'DPm', 'Gc9', 'Gnm', 'JasprEpicPose', 'JasprCombatPick', 'JasprCombatBox', 'JasprCombatSweepRender'];
const VARS = ['HFd', 'HFf', 'HNb', 'HFe', 'HFg', 'HFc', 'KsX', 'Ktg', 'HEH'];
function audit(base) {
  for (const n of NAMES) fnText(base, n);
  for (const v of VARS) if (!new RegExp('(?:var |,)' + v.replace(/\$/g, '\\$') + '=').test(base)) throw Error('missing engine variable ' + v);
  const expect = (cond, what) => { if (!cond) throw Error('engine mapping changed: ' + what); };
  expect(fnText(base, 'CDe').includes('a.G1=1;a.crX=b;'), 'swingArm fields (isSwingInProgress, swingingHand)');
  expect(fnText(base, 'EQX').includes('a.bDd=0;'), 'resetCooldown / ticksSinceLastSwing');
  expect(fnText(base, 'DOj').includes('b=a.bDd+b;'), 'getCooledAttackStrength');
  expect(fnText(base, 'GgT').includes('b=a.fi.v.bx.gP;if(b==a.dxO)return;'), 'syncCurrentPlayItem');
  expect(fnText(base, 'DzY').includes('GgT(a);') && fnText(base, 'DzY').includes('e=a.jC;Fz();if(e===HNb)return;'), 'attackEntity / spectator game type');
  expect(fnText(base, 'Gnm').includes('b=a.bSp;if(!b)return 0;a.bSp=b-1|0;return 1;'), 'KeyBinding.isPressed (pressTime)');
  expect(fnText(base, 'EsL').includes('if(b!==HFd)a.TL=0.0;else a.Wf=0.0;'), 'resetEquippedProgress');
  expect(fnText(base, 'F78').includes('$z=FOL(a);') && fnText(base, 'F78').includes('return b!==c?0:1;'), 'EntityTameable.isOwner');
  expect(fnText(base, 'DaN').includes('case 0:c=a.hs;') && fnText(base, 'DaN').includes('$z=D$G(c,b);'), 'NBTTagCompound.hasKey');
  expect(fnText(base, 'BW').trim() === 'function BW(a){return AYP(a.constructor);}', 'Object.getClass');
  expect(fnText(base, 'Gi').trim() === 'function Gi(a,b){return U7(a.bMM,b);}', 'Registry.getIDForObject');
  expect(base.includes('KF,"EntityWolf",148,S5,') && base.includes('Cb,"EntityPlayer",'), 'EntityTameable / EntityPlayer classes');
  expect(fnText(base, 'Clw').includes('a.b0q=Gi(KsX,BW(b));'), 'EntityList registry (network type id)');
  const right = fnText(base, 'F$w');
  expect(sha(right) === RIGHT_CLICK_SHA, 'rightClickMouse (F$w) differs from the audited text ' + sha(right));
  // the engine's call sites into the head, unchanged
  expect(fnText(base, 'Cfq').includes('case 0:$p=191;case 191:$z=JasprCombatAction(a,false);if(B()){break _;}if($z)return;'), 'left click call site');
  expect(right.includes('case 0:$p=191;case 191:$z=JasprCombatAction(a,true);if(B()){break _;}if($z)return;'), 'right click call site');
  expect(fnText(base, 'DRw').includes('case 198:JasprCombatTick(a);if(B()){break _;}'), 'tick call site (start of processKeyBinds)');
  expect(base.includes('JasprBetterCombat.receive($rt_ustr($z));return;'), 'jaspr:combat receive call site');
  expect(base.includes('$z=JasprCombatIndicators(a,b,c);if(B()){break _;}if($z)return;'), 'crosshair indicator call site');
}
function build(input) {
  const { start, end } = headRange(input);
  const head = input.slice(start, end), at = head.indexOf(TAIL);
  if (at < 0) throw Error('the engine-derived functions are missing from the head');
  const tail = head.slice(at);
  if (sha(tail) !== TAIL_SHA) throw Error('the engine-derived head functions changed since they were audited: ' + sha(tail));
  audit(input);
  const next = adapterText() + tail;
  const output = input.slice(0, start) + next + input.slice(end);
  // only the head region changed, and the result parses
  if (output.slice(0, start) !== input.slice(0, start) || output.slice(start + next.length) !== input.slice(end)) throw Error('bytes outside the head changed');
  new vm.Script(output, { filename: 'classes.js' });
  return output;
}
if (require.main === module) {
  const args = process.argv.slice(2), check = args.includes('--check');
  const inputPath = args.find(a => !a.startsWith('--')) || LIVE;
  const input = fs.readFileSync(inputPath, 'latin1'), output = build(input);
  if (build(output) !== output) throw Error('not idempotent');
  const { start, end } = headRange(input), head = headRange(output);
  const manifest = { build: BUILD, input: inputPath, inputSHA256: sha(input), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'),
    liveHeadSHA256: sha(input.slice(start, end)), headSHA256: sha(output.slice(head.start, head.end)), adapterSHA256: sha(adapterText()),
    headRange: [start, end], unchangedOutsideHead: true, idempotent: true, builtAt: new Date().toISOString() };
  if (!check) {
    const dir = path.join(root, 'candidate/bettercombat');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
    fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  }
  console.log(JSON.stringify(manifest));
}
module.exports = { build, headRange, adapterText, fnText, audit, BEGIN, NEXT, END, TAIL, BUILD, sha };
