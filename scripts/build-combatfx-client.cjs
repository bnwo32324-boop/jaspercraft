'use strict';
// JASPR_COMBATFX client stage (combat rework, workstream B): camera kicks (JasprCameraFX, also used by the gun stage),
// blade trails and impact sparks for the first-person melee motion. Reversible, anchor-checked insertion: every hook
// anchor is original client text that no other stage replaced and must match exactly once inside its function; every
// engine name the module uses must be declared by the client; the result must parse and unpatch(build(x)) must give x
// back byte for byte. latin1 in and out; output goes to candidate/combatfx/.
// Assembly order (COMBAT_REWORK.md): LIVE -> Better Combat head -> melee module -> THIS STAGE -> JASPR_GUNFX (outermost).
// Usage: node scripts/build-combatfx-client.cjs [input classes.js]          (default: the live site/classes.js)
//        node scripts/build-combatfx-client.cjs --unpatch [input classes.js]
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const { declared } = require('./build-mutants-client.cjs');
const { engineNames } = require('./stage-names.cjs');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_COMBATFX_BEGIN */', END = '/* JASPR_COMBATFX_END */';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const MODULES = ['combatfx-runtime.js', 'combatfx-teavm.js'];
// stages that are assembled on top of this one: they come off first
const OUTER = ['/* JASPR_GUNFX_BEGIN */'];
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
// [function, anchor, replacement, expected count]
const hooks = [
  // EntityRenderer.hurtCameraEffect(partialTicks): the camera kicks (world camera and first-person hand pass), and once
  // per frame the melee impact intents
  ['FN4', 'case 0:c=a.bu.hi;if(!(c instanceof Co))return;', 'case 0:JasprCameraFXApply(a,b);c=a.bu.hi;if(!(c instanceof Co))return;', 1],
  // EntityRenderer.renderHand: its hurtCameraEffect call is the hand pass (a push back moves the world only)
  ['Ddz', 'case 9:FN4(a,b);if(B()){break _;}', 'case 9:JasprCameraFX.pass=1;FN4(a,b);if(B()){break _;}JasprCameraFX.pass=0;', 1],
  // EntityRenderer.renderHand, right after both first-person hands: the blade trails in the hands' own matrices
  ['Ddz', 'case 32:DnA();if(B()){break _;}$p=33;', 'case 32:JasprCombatFXHand(a,b);DnA();if(B()){break _;}$p=33;', 1]
];
// names the module may use that are its own, another stage's (looked up with typeof) or TeaVM's runtime
const OWN = new Set(['JasprCameraFX', 'JasprCombatFX', 'JasprCameraFXApply', 'JasprCombatFXFrame', 'JasprCombatFXHand', 'JasprCombatFXDraw',
  'JasprCombatFXDiag', 'JasprCombatFXSend', 'JasprCombatFXSeen', 'JasprCombatFXReport', 'JasprMeleeMotion', 'JasprVideoState',
  '$rt_globals', 'module', 'require']);
function bounds(source, name) {
  const start = source.indexOf('function ' + name + '('), end = source.indexOf('\nfunction ', start + 10);
  if (start < 0 || end < 0) throw Error('Missing native function ' + name);
  return [start, end];
}
function replaceIn(source, name, from, to, count) {
  const [start, end] = bounds(source, name), body = source.slice(start, end), matches = body.split(from).length - 1;
  if (matches !== count) throw Error(`${name}: expected ${count} hook anchor(s) "${from.slice(0, 60)}", got ${matches}`);
  return source.slice(0, start) + body.split(from).join(to) + source.slice(end);
}
function moduleSource() {
  return MODULES.map(f => {
    let text = fs.readFileSync(path.join(root, 'client-mods/combatfx', f), 'utf8').replace(/\r\n/g, '\n');
    text = text.replace(/\nif \(typeof module !== 'undefined' && module\.exports\) module\.exports = [^\n]+\n?$/, '\n');
    if (/[^\x00-\x7f]/.test(text)) throw Error(f + ' is not ASCII');
    return '/* ---- ' + f + ' ---- */\n' + text;
  }).join('\n');
}
function unpatch(source) {
  if (!source.includes(BEGIN)) return source;
  for (const o of OUTER) if (source.includes(o)) throw Error('an outer stage is installed on top of JASPR_COMBATFX: unpatch it first (' + o + ')');
  const start = source.indexOf(BEGIN), end = source.indexOf(END, start);
  if (end < 0) throw Error('Incomplete JASPR_COMBATFX stage');
  if (source.indexOf(BEGIN, start + 1) >= 0) throw Error('Duplicate JASPR_COMBATFX stage');
  source = source.slice(0, start) + source.slice(end + END.length);
  for (const [fn, from, to, count] of hooks.slice().reverse()) source = replaceIn(source, fn, to, from, count);
  return source;
}
function build(input) {
  for (const o of OUTER) if (input.includes(o)) throw Error('unpatch the outer stage first: ' + o);
  const base = unpatch(input);
  if (/JasprCameraFX|JasprCombatFX/.test(base)) throw Error('a stray combat FX reference remains outside the stage');
  const module = moduleSource();
  const needed = engineNames(module).filter(n => !OWN.has(n));
  const missing = needed.filter(n => !declared(base, n));
  if (missing.length) throw Error('Native names missing from this client (re-audit the stage): ' + missing.join(', '));
  let s = base;
  for (const [fn, from, to, count] of hooks) s = replaceIn(s, fn, from, to, count);
  const end = s.lastIndexOf('}));');
  if (end < 0) throw Error('TeaVM module footer not found');
  s = s.slice(0, end) + BEGIN + '\n' + module + '\n' + END + s.slice(end);
  new vm.Script(s, { filename: 'classes.js' });
  if (unpatch(s) !== base) throw Error('Reversibility check failed');
  return { output: s, base, natives: needed };
}
if (require.main === module) {
  const unpatchOnly = process.argv[2] === '--unpatch';
  const inputPath = process.argv[unpatchOnly ? 3 : 2] || LIVE;
  const input = fs.readFileSync(inputPath, 'latin1'), dir = path.join(root, 'candidate/combatfx');
  fs.mkdirSync(dir, { recursive: true });
  if (unpatchOnly) {
    const base = unpatch(input);
    new vm.Script(base, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.without-combatfx.js'), base, 'latin1');
    console.log('Without JASPR_COMBATFX: ' + path.join(dir, 'classes.without-combatfx.js') + ' sha256 ' + sha(base));
    process.exit(0);
  }
  const { output, base, natives } = build(input);
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const stage = output.slice(output.indexOf(BEGIN), output.indexOf(END) + END.length);
  const manifest = { input: inputPath, inputSHA256: sha(input), baseSHA256: sha(base), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'),
    stageBytes: Buffer.byteLength(stage, 'latin1'), hooks: hooks.map(h => h[0]), natives: natives.length, builtAt: new Date().toISOString(),
    next: 'the gun stage (JASPR_GUNFX) builds on top of this output' };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, unpatch, hooks, BEGIN, END, MODULES, OUTER, LIVE, sha, moduleSource };
