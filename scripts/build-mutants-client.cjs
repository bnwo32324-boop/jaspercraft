'use strict';
// JasperCraft Mutant Creatures client stage (JASPR_MUTANTS): reversible, anchor-checked insertion into the deployed
// TeaVM client. Input is the LIVE site/classes.js (read only), output goes to candidate/mutants/. Every hook anchor
// must match exactly the expected number of times inside its function (anchors are original client text that no
// other stage replaced), every engine name the module uses must be declared by the client, the result must parse and
// unpatch(build(x)) must give back x byte for byte. Read and written as latin1. This stage is the OUTERMOST one:
// rebuild other stages only after --unpatch, then run this builder again.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_MUTANTS_BEGIN */', END = '/* JASPR_MUTANTS_END */';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
const MODULES = ['mutants-native.js', 'mutants-config.js', 'mutants-registry.js', 'mutants-entities.js', 'mutants-entities2.js',
  'mutants-animation.js', 'mutants-models.js', 'mutants-renderers.js', 'mutants-particles.js', 'mutants-items.js', 'mutants-gui.js',
  'mutants-network.js', 'mutants-stage.js'];
// [function, anchor, replacement, expected count]
const hooks = [
  // Bootstrap.register, after the vanilla registries (Forge's RegistryEvent.Register point)
  ['Fga', 'case 14:Dc8();if(B()){break _;}return;', 'case 14:Dc8();if(B()){break _;}JasprMutantsBridge.registry();return;'],
  // NetHandlerPlayClient.handleCustomPayload: channels jaspr:mutants, mutantbeasts, jaspr:scale
  ['Cyr', 'c=C(1771);d=b.S$;$p=1;case 1:', 'if(JasprMutantsBridge.payload(b))return;c=C(1771);d=b.S$;$p=1;case 1:'],
  // Minecraft.runTick: HELLO, queued messages (resumable state 3105), Big Mobs hitboxes
  ['CHq', 'if(b>0)a.bTh=b-1|0;if(!a.cp){c=a.da;$p=3;continue _;}', 'if(b>0)a.bTh=b-1|0;$p=3105;case 3105:JasprMutantsTick(a);if(B()){break _;}if(!a.cp){c=a.da;$p=3;continue _;}'],
  // RenderLivingBase.doRender, after renderLivingAt: Big Mobs render scale about the feet
  ['DWR', 'case 17:try{a.dqK(b,c,d,e);if(B()){break _;}$p=18;continue _;}', 'case 17:try{a.dqK(b,c,d,e);if(B()){break _;}JasprMutantsBridge.scaleRender(b);$p=18;continue _;}'],
  // EntityPlayerSP.isRidingHorse: IJumpingMount.canJump of the spider pig (TeaVM devirtualised the call to AbstractHorse)
  ['EQy', 'if(c&&Cm(b,AP8)){', 'if(c&&b!==null&&b.$jmJumpingMount)return b.canJump()?1:0;if(c&&Cm(b,AP8)){'],
  // EntityPlayerSP.onLivingUpdate: IJumpingMount.setJumpPower (devirtualised to AbstractHorse.setJumpPower)
  ['F4A', 'case 45:Gy9(n,b);if(B()){break _;}', 'case 45:if(n.$jmJumpingMount)n.setJumpPower(b);else Gy9(n,b);if(B()){break _;}'],
  // ModelRenderer.render: ScalableModelRenderer.render(scale) = push, scale(s), super.render(scale), pop. Children are
  // rendered with direct E7Q calls, so the override must sit in E7Q itself (entry, before the resume check)
  ['E7Q', 'function E7Q(a,b){var c,d,e,f,g,h,i,$p,$z;$p=0;', 'function E7Q(a,b){var c,d,e,f,g,h,i,$p,$z;$p=0;if(a.$jmS!==undefined&&a.$jmS!==1&&!FX()){JasprMutants.scaledRender(a,b);return;}']
];
const EXTRA_HOOKS = [];                                      // added by later parts (renderers, items, gui)
function allHooks() { return hooks.concat(EXTRA_HOOKS); }
function bounds(source, name) {
  const start = source.indexOf('function ' + name + '('), end = source.indexOf('\nfunction ', start + 10);
  if (start < 0 || end < 0) throw Error('Missing native function ' + name);
  return [start, end];
}
function replaceIn(source, name, from, to, count = 1) {
  const [start, end] = bounds(source, name), body = source.slice(start, end), matches = body.split(from).length - 1;
  if (matches !== count) throw Error(`${name}: expected ${count} hook anchors, got ${matches}`);
  return source.slice(0, start) + body.split(from).join(to) + source.slice(end);
}
function declared(source, name) {
  const e = name.replace(/\$/g, '\\$');
  return new RegExp('(?:^|[\\s;{}(])function ' + e + '\\(|(?:^|[\\s;,{}])var ' + e + '\\s*=|[;,\\s]' + e + '\\s*=\\s*(?:null|0|[A-Za-z_$(\\-0-9])|(?:^|[\\s;,{}])let ' + e + '\\s*=').test(source);
}
function unpatch(source) {
  if (!source.includes(BEGIN)) return source;
  const start = source.indexOf(BEGIN), end = source.indexOf(END, start);
  if (end < 0) throw Error('Incomplete Mutants extension');
  if (source.indexOf(BEGIN, start + 1) >= 0) throw Error('Duplicate Mutants extension');
  source = source.slice(0, start) + source.slice(end + END.length);
  for (const [fn, from, to, count] of [...allHooks()].reverse()) source = replaceIn(source, fn, to, from, count);
  return source;
}
function moduleSource() {
  const parts = [];
  for (const f of MODULES) {
    const p = path.join(root, 'client-mods/mutants', f);
    if (!fs.existsSync(p)) continue;
    const text = fs.readFileSync(p, 'utf8');
    if (/[^\x00-\x7f]/.test(text)) throw Error(f + ' is not ASCII');
    parts.push('/* ---- ' + f + ' ---- */\n' + text);
  }
  return parts.join('\n');
}
// Engine names the module uses: identifiers that are not property names, not declared by the module itself and not
// JavaScript globals. Each must be declared by the client (function or var).
const JS_GLOBALS = new Set(('Object Array Math String Number Boolean JSON Error TypeError RangeError Map Set WeakMap Proxy Reflect ' +
  'Date BigInt DataView Uint8Array Int8Array Uint16Array Int32Array Float32Array Float64Array TextEncoder TextDecoder ' +
  'isFinite isNaN parseInt parseFloat undefined NaN Infinity console arguments this true false null typeof instanceof new ' +
  'return var function if else for while do break continue switch case default throw try catch finally in of delete void ' +
  'let const class extends super yield await async with debugger import export static get set').split(' '));
function engineNames(src) {
  // strip comments, strings and regex-free code (the module uses no regex literals with quotes)
  const code = src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ').replace(/"(?:[^"\\\n]|\\.)*"/g, '""').replace(/'(?:[^'\\\n]|\\.)*'/g, "''");
  const toks = [...code.matchAll(/[A-Za-z_$][\w$]*|\d[\w.]*|\S/g)].map(m => m[0]);
  const declaredHere = new Set(), used = new Set();
  const isId = t => /^[A-Za-z_$][\w$]*$/.test(t);
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i];
    if ((t === 'var' || t === 'let' || t === 'const')) {
      // declarator list up to the matching ';' (or 'in'/'of' in for loops) at depth 0
      let depth = 0, expectName = true;
      for (let j = i + 1; j < toks.length; j++) {
        const u = toks[j];
        if (u === '(' || u === '[' || u === '{') depth++;
        else if (u === ')' || u === ']' || u === '}') { if (depth === 0) break; depth--; }
        else if (depth === 0 && (u === ';' || u === 'in' || u === 'of')) break;
        else if (depth === 0 && u === ',') { expectName = true; continue; }
        if (expectName && isId(u)) { declaredHere.add(u); expectName = false; }
        else if (u !== '=') expectName = false;
      }
    } else if (t === 'function') {
      let j = i + 1;
      if (isId(toks[j])) { declaredHere.add(toks[j]); j++; }
      if (toks[j] === '(') for (j++; j < toks.length && toks[j] !== ')'; j++) if (isId(toks[j])) declaredHere.add(toks[j]);
    } else if (t === 'catch' && toks[i + 1] === '(' && isId(toks[i + 2])) declaredHere.add(toks[i + 2]);
  }
  for (let i = 0; i < toks.length; i++) {
    const t = toks[i];
    if (!isId(t) || JS_GLOBALS.has(t)) continue;
    if (toks[i - 1] === '.') continue;                                   // property access
    if ((toks[i - 1] === '{' || toks[i - 1] === ',') && toks[i + 1] === ':') continue;   // object key
    if (toks[i - 1] === 'break' || toks[i - 1] === 'continue') continue;              // label reference
    if ((toks[i - 1] === ';' || toks[i - 1] === '}') && toks[i + 1] === ':') continue;   // statement label
    used.add(t);
  }
  return [...used].filter(n => !declaredHere.has(n)).sort();
}
function build(input, opts = {}) {
  const base = unpatch(input);
  if (base.includes('JasprMutantsBridge')) throw Error('A stray Mutants reference remains outside the extension block');
  const module = moduleSource();
  const needed = engineNames(module).filter(n => !['JasprMutants', 'JasprMutantsBridge', 'JasprMutantsTick', 'JasprMutantsBind',
    'JasprMutantsItemDraw', 'JasprMutantsGuiDraw', 'JasprMutantsTeisr', '$rt_globals', '$rt_str', '$rt_ustr', '$rt_metadata', '$rt_isInstance',
    'JasprGoreDraw', 'JasprMoBendsBridge', 'JasprVideoLabel', 'JasprVideoAction'].includes(n));
  const missing = needed.filter(n => !declared(base, n));
  if (missing.length) throw Error('Native names missing from this client (re-audit the adapter): ' + missing.join(', '));
  let s = base;
  for (const [fn, from, to, count] of allHooks()) s = replaceIn(s, fn, from, to, count);
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
  const input = fs.readFileSync(inputPath, 'latin1');
  const dir = path.join(root, 'candidate/mutants');
  fs.mkdirSync(dir, { recursive: true });
  if (unpatchOnly) {
    const base = unpatch(input);
    new vm.Script(base, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.without-mutants.js'), base, 'latin1');
    console.log('Without the Mutants stage: ' + path.join(dir, 'classes.without-mutants.js') + ' sha256 ' + sha(base));
    process.exit(0);
  }
  const { output, base, natives } = build(input);
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const stage = output.slice(output.indexOf(BEGIN), output.indexOf(END) + END.length);
  const manifest = { inputSHA256: sha(input), baseSHA256: sha(base), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'),
    stageBytes: Buffer.byteLength(stage, 'latin1'), hooks: allHooks().map(h => h[0]), natives: natives.length, builtAt: new Date().toISOString() };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, unpatch, hooks, allHooks, EXTRA_HOOKS, BEGIN, END, sha, engineNames, declared, LIVE, MODULES };
