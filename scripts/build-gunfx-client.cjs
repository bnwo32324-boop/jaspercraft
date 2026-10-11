'use strict';
// JasperCraft first-person gun animation stage (JASPR_GUNFX): reversible, anchor-checked insertion into the deployed TeaVM
// client (client-mods/gunfx: recoil, cycling, reloads, dry fire, casts, muzzle flash, casings, camera impulses through
// JasprCameraFX). Input is the LIVE site/classes.js (read only) or the lead's assembled client; output goes to
// candidate/gunfx/. This stage is meant to be the OUTERMOST one (combat rework assembly: A's BetterCombat head, B's melee
// module and JASPR_COMBATFX, then this): it takes itself off first when the input already has it.
// Rules checked here: every hook anchor is original engine text (also present in the unpatched bundle when that copy is
// available) found exactly once inside its function and outside every fenced JASPR_* region; every engine name the module
// uses is declared by the client; the result parses; unpatch(build(x)) === x byte for byte; latin1 in and out.
//
//   node scripts/build-gunfx-client.cjs [input classes.js]          -> candidate/gunfx/classes.js + manifest.json
//   node scripts/build-gunfx-client.cjs --unpatch [input classes.js] -> candidate/gunfx/classes.without-gunfx.js
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm'), os = require('node:os');
try { os.setPriority(os.constants.priority.PRIORITY_LOW); } catch (_) { /* best effort */ }
const { engineNames, declared } = require('./build-mutants-client.cjs');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_GUNFX_BEGIN */', END = '/* JASPR_GUNFX_END */';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const ORIGINAL = 'C:/Users/AM/Documents/.eagler-1122-evaluation/1.12.2-WASM/classes.js';
const MODULES = ['gunfx-data.js', 'gunfx-core.js', 'gunfx-native.js'];
const STATES = [7491];   // resumable states this stage adds (C45); chosen far from engine and other stages' numbers
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');

// [function, anchor (original engine text), replacement, expected count]
const HOOKS = [
  // EntityRenderer.updateRenderer (inlined ItemRenderer.updateEquippedItem): both held stacks were just read (g main, j off,
  // b the ItemRenderer, f the player) and the equality checks that start a re-equip come next. Once per client tick.
  ['C45', 'case 5:$z=EjD(f);if(B()){break _;}j=$z;if(!f.bBT){d=1.0;$p=7;continue _;}',
    'case 5:$z=EjD(f);if(B()){break _;}j=$z;$p=7491;case 7491:JasprGunFXTick(b,f,g,j);if(B()){break _;}if(!f.bBT){d=1.0;$p=7;continue _;}', 1],
  // ItemRenderer.renderItemSide (c stack, d transform type, e left hand): the recoil transform right before
  // RenderItem.renderItemModel draws the gun, the muzzle flash right after it. Ch0 pushed the matrix it pops at state 9.
  ['Ch0', 'b=$z;$p=12;case 12:CdY(h,c,b,d,e);if(B()){break _;}',
    'b=$z;JasprGunFXPre(c,d,e);$p=12;case 12:CdY(h,c,b,d,e);if(B()){break _;}JasprGunFXPost();', 1],
  // NetHandlerPlayClient.handleSoundEffect (b player, g SoundEvent, i volume, j pitch, d e f position), before it plays.
  ['Chn', 'j=a.cbJ;$p=1;case 1:Cfa(c,b,d,e,f,g,h,i,j);',
    'j=a.cbJ;JasprGunFXSound(b,g,i,j,d,e,f);$p=1;case 1:Cfa(c,b,d,e,f,g,h,i,j);', 1]
];
const OWN = ['JasprGunFX', 'JasprGunFXCore', 'JasprGunFXData', 'JasprGunFXRead', 'JasprGunFXTick', 'JasprGunFXPre', 'JasprGunFXPost', 'JasprGunFXSound'];
// optional neighbours (feature-detected with typeof) and runtime globals the client provides
const EXTERNAL = ['JasprCameraFX', 'JasprVideoState', '$rt_globals', '$rt_str', '$rt_ustr', 'Map'];

function bounds(source, name) {
  const head = '\nfunction ' + name + '(', start = source.indexOf(head);
  if (start < 0 || source.indexOf(head, start + 1) >= 0) throw Error('expected exactly one native function ' + name);
  const end = source.indexOf('\nfunction ', start + head.length);
  return [start + 1, end < 0 ? source.length : end];
}
function replaceIn(source, name, from, to, count) {
  const [start, end] = bounds(source, name), body = source.slice(start, end), n = body.split(from).length - 1;
  if (n !== count) throw Error(name + ': expected ' + count + ' hook anchor(s), found ' + n + ' (another stage may have replaced this text)');
  return source.slice(0, start) + body.split(from).join(to) + source.slice(end);
}
/** [start, end) ranges of every fenced JASPR_* stage block in the text. */
function fences(source) {
  const out = [], re = /\/\* ?JASPR_([A-Z0-9_]+?)_BEGIN ?\*\//g; let m;
  while ((m = re.exec(source))) {
    const name = m[1], end = source.indexOf('_END', m.index);
    const close = new RegExp('/\\* ?JASPR_' + name + '_END ?\\*/', 'g'); close.lastIndex = m.index;
    const c = close.exec(source);
    out.push([m.index, c ? c.index + c[0].length : (end < 0 ? source.length : end), name]);
  }
  return out;
}
function unpatch(source) {
  if (!source.includes(BEGIN)) {
    if (/JasprGunFX/.test(source)) throw Error('JasprGunFX hook text without the JASPR_GUNFX block: refusing to guess');
    return source;
  }
  const start = source.indexOf(BEGIN), end = source.indexOf(END, start);
  if (end < 0) throw Error('incomplete JASPR_GUNFX block');
  if (source.indexOf(BEGIN, start + 1) >= 0) throw Error('duplicate JASPR_GUNFX block');
  let out = source.slice(0, start) + source.slice(end + END.length + (source[end + END.length] === '\n' ? 1 : 0));
  for (const [fn, from, to, count] of HOOKS.slice().reverse()) out = replaceIn(out, fn, to, from, count);
  if (/JasprGunFX/.test(out)) throw Error('JasprGunFX residue after unpatch');
  return out;
}
function moduleSource() {
  const parts = [];
  for (const f of MODULES) {
    const text = fs.readFileSync(path.join(root, 'client-mods/gunfx', f), 'utf8').replace(/\r\n/g, '\n');
    if (/[^\x00-\x7f]/.test(text)) throw Error(f + ' is not ASCII');
    // the Node test exports stay out of the client
    parts.push('/* ---- ' + f + ' ---- */\n' + text.replace(/\nif \(typeof module !== 'undefined' && module\.exports\)[^\n]*\n?$/, '\n'));
  }
  const body = parts.join('\n');
  if (/module\.exports/.test(body)) throw Error('a Node export trailer was not stripped');
  return body;
}
/** A regular-expression literal in code (comments and strings removed): a slash that starts an operand. */
function hasRegexLiteral(src) {
  const code = src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ')
    .replace(/"(?:[^"\\\n]|\\.)*"/g, '""').replace(/'(?:[^'\\\n]|\\.)*'/g, "''");
  return /(^|[=(,:;!&|?{}[]|\breturn)\s*\/[^/*\s]/.test(code);
}
function checkAnchors(base) {
  const fenced = fences(base);
  const original = fs.existsSync(ORIGINAL) ? fs.readFileSync(ORIGINAL, 'latin1') : null;
  for (const [fn, from] of HOOKS) {
    const [start] = bounds(base, fn), at = base.indexOf(from, start);
    for (const [a, b, name] of fenced) if (at >= a && at < b) throw Error(fn + ' anchor lies inside the fenced JASPR_' + name + ' region');
    if (original) {
      const [os, oe] = bounds(original, fn), n = original.slice(os, oe).split(from).length - 1;
      if (n !== 1) throw Error(fn + ': the anchor is not original engine text (' + n + ' in the unpatched bundle)');
    }
    for (const state of STATES) if (base.slice(...bounds(base, fn)).includes('case ' + state + ':')) throw Error(fn + ' already has a state ' + state);
  }
  return !!original;
}
function build(input) {
  const base = unpatch(input);
  const verifiedOriginal = checkAnchors(base);
  const module = moduleSource();
  if (hasRegexLiteral(module)) throw Error('the module must not use regular expression literals (the engine-name scan cannot read them)');
  const needed = engineNames(module).filter(n => !OWN.includes(n) && !EXTERNAL.includes(n));
  const missing = needed.filter(n => !declared(base, n));
  if (missing.length) throw Error('engine names missing from this client (re-audit gunfx-native.js): ' + missing.join(', '));
  let s = base;
  for (const [fn, from, to, count] of HOOKS) s = replaceIn(s, fn, from, to, count);
  const end = s.lastIndexOf('}));');
  if (end < 0) throw Error('TeaVM module footer not found');
  s = s.slice(0, end) + BEGIN + ' // ' + JSON.stringify({ stage: 'gunfx1', sha256: sha(module) }) + '\n' + module + '\n' + END + '\n' + s.slice(end);
  new vm.Script(s, { filename: 'classes.js' });
  if (unpatch(s) !== base) throw Error('reversibility check failed');
  return { output: s, base, natives: needed, verifiedOriginal };
}
if (require.main === module) {
  const unpatchOnly = process.argv[2] === '--unpatch';
  const inputPath = process.argv[unpatchOnly ? 3 : 2] || LIVE;
  const input = fs.readFileSync(inputPath, 'latin1');
  const dir = path.join(root, 'candidate/gunfx');
  fs.mkdirSync(dir, { recursive: true });
  if (unpatchOnly) {
    const base = unpatch(input);
    new vm.Script(base, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.without-gunfx.js'), base, 'latin1');
    console.log('Without the gun animation stage: ' + path.join(dir, 'classes.without-gunfx.js') + ' sha256 ' + sha(base));
    process.exit(0);
  }
  const { output, base, natives, verifiedOriginal } = build(input);
  // a rebuild from the output must give the same output (the stage takes itself off first)
  if (build(output).output !== output) throw Error('rebuild is not stable');
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const block = output.slice(output.indexOf(BEGIN), output.indexOf(END) + END.length);
  const manifest = { stage: 'gunfx1', input: inputPath, inputSHA256: sha(input), baseSHA256: sha(base), sha256: sha(output),
    bytes: Buffer.byteLength(output, 'latin1'), stageBytes: Buffer.byteLength(block, 'latin1'), hooks: HOOKS.map(h => h[0]),
    natives: natives.length, anchorsVerifiedAgainstOriginal: verifiedOriginal, builtAt: new Date().toISOString() };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, unpatch, HOOKS, BEGIN, END, MODULES, moduleSource, fences, sha, LIVE, ORIGINAL };
