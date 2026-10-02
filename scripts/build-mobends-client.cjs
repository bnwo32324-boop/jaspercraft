'use strict';
// JasperCraft Mo' Bends client stage: reversible, anchor-checked insertion into the deployed TeaVM client.
// Input is the LIVE site/classes.js (other client stages are deployed there), output goes to candidate/mobends-client/.
// Every hook anchor must match exactly the expected number of times inside its function, every native name the
// adapter uses must exist, the result must parse, and unpatch(build(x)) must give back x byte for byte.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const BEGIN = '/* JASPR_MOBENDS_BEGIN */', END = '/* JASPR_MOBENDS_END */';
const sha = s => crypto.createHash('sha256').update(s, 'latin1').digest('hex');
// [function, anchor, replacement, expected count]
const hooks = [
  // RenderManager.renderEntity: RenderLivingEvent.Pre/Post around doRender (before gore's begin, after gore's end)
  ['Gxv', 'D_f(j,l);if(B()){break _;}JasprGoreBridge.begin(b,j);', 'D_f(j,l);if(B()){break _;}JasprMoBendsBridge.pre(b,j,g,a);JasprGoreBridge.begin(b,j);'],
  ['Gxv', 'j.jV(b,c,d,e,f,g);if(B()){break _;}JasprGoreBridge.end();break b;', 'j.jV(b,c,d,e,f,g);if(B()){break _;}JasprGoreBridge.end();JasprMoBendsBridge.post(b,j);break b;'],
  // ModelRenderer.render / renderWithRotation / postRender delegate to the Mo' Bends ModelPart that owns the renderer
  ['E7Q', 'case 0:if(!a.cIT&&a.eT){if(!a.clh){$p=2;continue _;}', 'case 0:if(a.$mb!==undefined){JasprMoBendsBridge.render(a,b);return;}if(!a.cIT&&a.eT){if(!a.clh){$p=2;continue _;}'],
  ['Eu3', 'case 0:if(!a.cIT&&a.eT){if(a.clh){$p=1;continue _;}$p=6;continue _;}return;', 'case 0:if(a.$mb!==undefined){JasprMoBendsBridge.render(a,b);return;}if(!a.cIT&&a.eT){if(a.clh){$p=1;continue _;}$p=6;continue _;}return;'],
  ['FFZ', 'case 0:a:{b:{if(!a.cIT&&a.eT){if(!a.clh){$p=1;continue _;}', 'case 0:if(a.$mb!==undefined){JasprMoBendsBridge.postRender(a,b);return;}a:{b:{if(!a.cIT&&a.eT){if(!a.clh){$p=1;continue _;}'],
  // DataUpdateHandler: one render-tick update per world pass; the client tick at the start of Minecraft.runTick (CHq),
  // where Forge fires ClientTickEvent -- not Minecraft.updateDisplay (Gq3), which runs once per frame
  ['DbP', 'BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);', 'BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);JasprMoBendsBridge.frame(d,a.Pp);'],
  ['CHq', 'case 0:$p=1963;case 1963:JasprDSTick(a);', 'case 0:JasprMoBendsBridge.tick();$p=1963;case 1963:JasprDSTick(a);'],
  // LayerArmorBase.getModelFromSlot: Forge's getArmorModelHook (ArmorModelFactory)
  ['E6n', 'return !c?a.bfj:a.a_a;', 'return JasprMoBendsArmor(a,!c?a.bfj:a.a_a);'],
  // LayerHeldItem.renderHeldItem (after gore's missing-hand check), LayerCape / LayerElytra doRenderLayer
  ['Cqg', 'case 0:if(JasprGoreBridge.hand(e))return;$p=1;', 'case 0:if(JasprGoreBridge.hand(e))return;if(JasprMoBendsBridge.heldItem(a,b,c,d,e))return;$p=1;'],
  ['DVH', 'case 0:$p=1;case 1:$z=Gyc(b);', 'case 0:if(JasprMoBendsBridge.cape(a,b,c,d,e,f,g,h,i))return;$p=1;case 1:$z=Gyc(b);'],
  ['EDY', 'case 0:Dt();j=Kuf;$p=1;', 'case 0:if(JasprMoBendsBridge.elytra(a,b,c,d,e,f,g,h,i))return;Dt();j=Kuf;$p=1;'],
  // RenderPlayer.renderRightArm / renderLeftArm: RenderHandEvent -> PlayerMutator.poseForFirstPersonView
  ['DjM', 'case 0:if(a.b9z)return;', 'case 0:if(a.b9z)return;JasprMoBendsBridge.firstPerson(a);'],
  ['E$B', 'case 0:if(a.b9z)return;', 'case 0:if(a.b9z)return;JasprMoBendsBridge.firstPerson(a);'],
  // RenderArrow.doRender: RenderBendsArrow's trail before the arrow
  ['Fi9', 'case 0:$p=1;case 1:Ew0(a,b);', 'case 0:JasprMoBendsBridge.arrow(b,c,d,e,g);$p=1;case 1:Ew0(a,b);'],
  // Video Settings: give the last row its second button (id 973, "Mo' Bends animations")
  ['JasprVideoBuild', 'case 6:if(j>972){h=null;', 'case 6:if(j>973){h=null;']
];
// Native names the adapter relies on: each must be declared (function or var) in the client.
const NATIVE = ['M2', 'DGf', 'DW', 'FR', 'Bq', 'Y', 'Bm', 'HB', 'ECM', 'HV', 'F2d', 'Ehl', 'DZG', 'AMs', 'A5Y', 'T', 'G', 'Xb', 'Bgq', 'F$t', 'Dle', 'E7Q',
  'DPm', 'Gc9', 'FWM', 'Eu0', 'ECi', 'CFh', 'EQk', 'HKM', 'HKD', 'KrH', 'GnI', 'CQ6', 'DCQ', 'D75', 'DFk', 'Ggy', 'F1Q', 'CyM', 'CTP', 'Fb_', 'CcT', 'FUe',
  'HHV', 'HHT', 'Kri', 'Krr', 'HEo', 'Krc', 'Krd', 'HKI', 'HKJ', 'HKK', 'HKL', 'GdM', 'Ep0', 'CUb', 'EpJ', 'Gu1', 'Eip', 'E74', 'FE$', 'C5', 'HLn', 'Lq0',
  'F6T', 'Clc', 'ELo', 'G9', 'Kua', 'Kvp', 'Hc', 'HFd', 'Wq', 'Kul', 'Lbp', 'Kui', 'Kuh', 'By', 'KIO', 'Ktv', 'Dt', 'Kuf', 'AKp', 'LqA', 'U6', 'KtW', 'HJ',
  'KXV', 'KXW', 'KXX', 'KXY', 'KsS', 'HFo', 'KsW', 'KsT', 'KsU', 'C3W', 'CBf', 'Dyw', 'EZ5', 'EjD', 'C52', 'Fr4', 'EG7', 'CCb', 'ENU', 'F9J', 'CF$', 'EUr',
  'Dn1', 'Dy', 'D0', 'CAi', 'DNo', 'FF', 'CCH', 'Co', 'Vf', 'Iw', 'OF', 'PP', 'SN', 'ZL', 'KF', 'Kx', 'OB', 'BVS', 'C4z', 'Gky', 'F$X', 'BIY', 'C2y', 'A7l',
  'OE', 'HM', 'AM9', 'BlY', 'Ht', 'Xh', 'GY', 'AFo', 'Ro', 'BkV', 'Blj', 'E32', 'Ch0', 'Gyc', 'DfJ', 'Clb', 'DQF', 'FTd', 'FmP', 'Dv8', 'Fxy', 'LvD', 'DqH',
  'Djv', 'EmX', 'Fzs', 'YW', 'Fl9', 'Egf', 'FST', 'Gs', 'HEH', '$rt_str', 'JasprGoreDraw', 'JasprVideoLabel', 'JasprVideoAction', 'JasprMeleeApplyBody'];
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
  return new RegExp('(?:^|[\\s;{}(])function ' + e + '\\(|(?:^|[\\s;,{}])var ' + e + '\\s*=|[;,\\s]' + e + '\\s*=\\s*(?:null|0|[A-Za-z_$])').test(source);
}
function unpatch(source) {
  if (!source.includes(BEGIN)) return source;
  const start = source.indexOf(BEGIN), end = source.indexOf(END, start);
  if (end < 0) throw Error('Incomplete Mo\' Bends extension');
  if (source.indexOf(BEGIN, start + 1) >= 0) throw Error('Duplicate Mo\' Bends extension');
  source = source.slice(0, start) + source.slice(end + END.length);
  for (const [fn, from, to, count] of [...hooks].reverse()) source = replaceIn(source, fn, to, from, count);
  return source;
}
function moduleSource() {
  const read = f => fs.readFileSync(path.join(root, 'client-mods/mobends', f), 'utf8');
  const core = read('mobends-core.js').replace(/\nif \(typeof module[^\n]+\n?$/, '\n');
  return [read('mobends-assets.js'), core, read('mobends-teavm.js')].join('\n');
}
function build(input) {
  const base = unpatch(input);
  if (base.includes('JasprMoBendsBridge')) throw Error('A stray Mo\' Bends reference remains outside the extension block');
  if (base.includes("[973,")) throw Error('Video Settings id 973 is already in use: pick another id');
  const missing = NATIVE.filter(n => !declared(base, n));
  if (missing.length) throw Error('Native names missing from this client (re-audit the adapter): ' + missing.join(', '));
  let s = base;
  for (const [fn, from, to, count] of hooks) s = replaceIn(s, fn, from, to, count);
  const end = s.lastIndexOf('}));');
  if (end < 0) throw Error('TeaVM module footer not found');
  s = s.slice(0, end) + BEGIN + '\n' + moduleSource() + '\n' + END + s.slice(end);
  new vm.Script(s, { filename: 'classes.js' });
  if (unpatch(s) !== base) throw Error('Reversibility check failed');
  return { output: s, base };
}
if (require.main === module) {
  // Mo' Bends is the outermost client stage (its hooks sit next to gore's, the video stage's and Dynamic Surroundings'):
  // to rebuild another stage, take it out first with --unpatch, rebuild that stage, then run this builder again.
  const unpatchOnly = process.argv[2] === '--unpatch';
  const inputPath = process.argv[unpatchOnly ? 3 : 2] || 'C:/Users/AM/Documents/Eaglercraft-1.12.2-Tailscale/site/classes.js';
  const input = fs.readFileSync(inputPath, 'latin1');
  const dir = path.join(root, 'candidate/mobends-client');
  fs.mkdirSync(dir, { recursive: true });
  if (unpatchOnly) {
    const base = unpatch(input);
    new vm.Script(base, { filename: 'classes.js' });
    fs.writeFileSync(path.join(dir, 'classes.without-mobends.js'), base, 'latin1');
    console.log('Without the Mo\' Bends stage: ' + path.join(dir, 'classes.without-mobends.js') + ' sha256 ' + sha(base));
    process.exit(0);
  }
  const { output, base } = build(input);
  fs.writeFileSync(path.join(dir, 'classes.js'), output, 'latin1');
  const manifest = { inputSHA256: sha(input), baseSHA256: sha(base), sha256: sha(output), bytes: Buffer.byteLength(output, 'latin1'), hooks: hooks.map(h => h[0]), builtAt: new Date().toISOString() };
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Candidate only: ' + path.join(dir, 'classes.js') + '\n' + JSON.stringify(manifest));
}
module.exports = { build, unpatch, hooks, NATIVE, BEGIN, END, sha };
