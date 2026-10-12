'use strict';
// JasperCraft FPS stage (JASPR_FPS, client-mods/fps): reversible, anchor-checked insertion into the deployed TeaVM client.
// Input is the LIVE site/classes.js (read only); output goes to candidate/fps/. Operations:
//   - wrap: an engine function `function X(` is renamed to `function X$fps(` and the stage defines X as a wrapper around it
//     (vtables and direct calls keep calling X, so every caller goes through the wrapper);
//   - fields: cache fields are added to a class constructor, so every object of the class has them from its creation
//     (stable object shapes for the JIT);
//   - the module text is appended before the TeaVM module footer between JASPR_FPS_BEGIN / _END.
// Every anchor must match exactly once, every name the stage wraps must be defined by the stage, the result must parse and
// unpatch(build(x)) must give back x byte for byte. This stage is the OUTERMOST one: rebuild other stages only after
// `--unpatch`, then build this one again.
//   node scripts/build-fps-client.cjs [input classes.js] [--out dir]      node scripts/build-fps-client.cjs --unpatch <classes.js>
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_FPS_BEGIN */', END = '/* JASPR_FPS_END */';
const LIVE = 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
const MODULES = ['fps-stage.js'];
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
// engine functions wrapped by the stage (see fps-stage.js for what each one is)
const WRAP = ['CHq', 'GJv', 'BOz', 'ETg', 'FfJ', 'F_U', 'Cu_', 'E9P', 'Ei5', 'Dy0', 'YJ', 'Gz', 'H', 'BN', 'C8D', 'EF8', 'F85', 'FTY', 'FGU', 'FkW', 'Fij'];
// [constructor function, anchor (its start), fields added right after the anchor]
const FIELDS = [
  ['Eg', 'function Eg(){var a=this;D.call(a);', 'a.$fpsBs=0;a.$fpsBx=0.0;a.$fpsBy=0.0;a.$fpsBz=0.0;a.$fpsBe=0.0;a.$fpsBv=0;a.$fpsLs=0;a.$fpsLx=0.0;a.$fpsLy=0.0;a.$fpsLz=0.0;a.$fpsLe=0.0;a.$fpsLv=0.0;'],
  ['AGt', 'function AGt(){var a=this;D.call(a);', 'a.$fpsX0=0;a.$fpsY0=0;a.$fpsZ0=0;a.$fpsSx=0;a.$fpsSy=0;a.$fpsSz=0;'],
  ['Ff', 'function Ff(){var a=this;A3Q.call(a);', 'a.$rh=0;a.$rl=0;a.$rv=0;'],
  ['BMl', 'function BMl(){var a=this;D.call(a);', 'a.$fpsE=null;']
];
const once = (s, x, what) => { const n = s.split(x).length - 1; if (n !== 1) throw Error(what + ': expected 1 match, got ' + n + ' for ' + x.slice(0, 80)); };
function moduleSource() {
  return MODULES.map(f => {
    const text = fs.readFileSync(path.join(root, 'client-mods/fps', f), 'utf8');
    if (/[^\x00-\x7f]/.test(text)) throw Error(f + ' is not ASCII');
    return '/* ---- ' + f + ' ---- */\n' + text;
  }).join('\n');
}
function unpatch(source) {
  if (!source.includes(BEGIN)) return source;
  const start = source.indexOf(BEGIN), end = source.indexOf(END, start);
  if (end < 0) throw Error('Incomplete FPS extension');
  if (source.indexOf(BEGIN, start + 1) >= 0) throw Error('Duplicate FPS extension');
  source = source.slice(0, start) + source.slice(end + END.length);
  for (const [, anchor, fields] of FIELDS) { once(source, anchor + fields, 'unpatch fields'); source = source.replace(anchor + fields, anchor); }
  for (const name of WRAP) { once(source, '\nfunction ' + name + '$fps(', 'unpatch ' + name); source = source.replace('\nfunction ' + name + '$fps(', '\nfunction ' + name + '('); }
  return source;
}
function build(input) {
  const base = unpatch(input);
  if (base.includes('$fps(') || base.includes('JasprFps')) throw Error('A stray FPS reference remains outside the extension block');
  const module = moduleSource();
  for (const name of WRAP) if (!module.includes('\nfunction ' + name + '(')) throw Error('the stage does not define the wrapper ' + name);
  let s = base;
  for (const name of WRAP) { once(s, '\nfunction ' + name + '(', 'wrap ' + name); s = s.replace('\nfunction ' + name + '(', '\nfunction ' + name + '$fps('); }
  for (const [, anchor, fields] of FIELDS) { once(s, anchor, 'fields'); s = s.replace(anchor, anchor + fields); }
  const end = s.lastIndexOf('}));');
  if (end < 0) throw Error('TeaVM module footer not found');
  s = s.slice(0, end) + BEGIN + '\n' + module + '\n' + END + s.slice(end);
  new vm.Script(s, { filename: 'classes.js' });
  if (unpatch(s) !== base) throw Error('Reversibility check failed');
  return { output: s, base };
}
if (require.main === module) {
  const args = process.argv.slice(2);
  const unpatchOnly = args[0] === '--unpatch';
  const outIdx = args.indexOf('--out');
  const dir = outIdx >= 0 ? path.resolve(args[outIdx + 1]) : path.join(root, 'candidate/fps');
  const inputPath = (unpatchOnly ? args[1] : (args[0] && !args[0].startsWith('--') ? args[0] : null)) || LIVE;
  const input = fs.readFileSync(inputPath, 'latin1');
  fs.mkdirSync(dir, { recursive: true });
  if (unpatchOnly) {
    const base = unpatch(input);
    new vm.Script(base, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.without-fps.js'), base, 'latin1');
    console.log('Without the FPS stage: ' + path.join(dir, 'classes.without-fps.js') + ' sha256 ' + sha(base));
    process.exit(0);
  }
  const { output, base } = build(input);
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const manifest = { inputSHA256: sha(input), baseSHA256: sha(base), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'), wrapped: WRAP, builtAt: new Date().toISOString() };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, unpatch, WRAP, FIELDS, BEGIN, END, LIVE, sha };
