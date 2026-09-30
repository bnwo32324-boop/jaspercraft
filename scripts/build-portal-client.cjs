'use strict';

// Exact, candidate-only JasprPortal stage for the composed browser client (site/classes.js) and its resource archive
// (site/assets.epk): every portal is coloured by its frame. A portal whose column stands on mossy cobblestone (a gate to
// Drownhollow, once Ul'Nhaar) glows green; a quartz gate (to Atlas, the Divided Realm) burns half blue and half black at once, split down
// its middle; a yellow glazed terracotta gate (to the Backrooms) hums fluorescent yellow; any other (obsidian: the Nether) keeps its
// purple. New gate kinds add a frame -> colour entry.
//  - assets: portal.png becomes a neutral (grey) animation, and portal_ns/portal_ew faces get tintindex 0;
//  - BlockColors.colorMultiplier (FEI): portal blocks (id 90) are tinted by their frame (walks down to the frame block);
//  - ParticlePortal factory (E_F): particles spawned inside a green portal are green;
//  - GuiIngame.renderPortal (F6G): the in-portal screen overlay takes the portal's colour;
//  - Minecraft.runTick: JasprPortal.tick(mc) keeps the client world and clears the cache when it changes.
// Four fenced hooks (/*JASPR_PORTAL_V1*/) plus one appended block; every anchor must match exactly once or the build
// stops. The runTick hook follows the sky stage's (scripts/build-sky-client.cjs), which must be installed first.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const zlib = require('node:zlib');
const {decode} = require('./merge-apocalypse-assets.cjs');
const root = path.resolve(__dirname, '..');
const source = process.env.PORTAL_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const assetSource = process.env.PORTAL_ASSETS_SOURCE || path.join(root, 'site', 'assets.epk');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MARK = '/*JASPR_PORTAL_V1*/';
// Vanilla purple as a multiplier over the neutral texture (least-squares fit of the original animation), and green.
const PURPLE = [0.457, 0.056, 1.0], GREEN = [0.30, 1.0, 0.42];
// The Backrooms' gate: the colour of old fluorescent light on yellow wallpaper.
const YELLOW = [1.0, 0.86, 0.30];
// Atlas's divided light: the Hearthstar's blue and the Cinder Heart's black (a little violet, so the swirl still shows),
// and the seam between them where a gate is an odd number of blocks wide.
const BLUE = [0.26, 0.55, 1.0], BLACK = [0.09, 0.07, 0.13], SEAM = [0.17, 0.30, 0.56];
const rgb = c => Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);

const BLOCK = [
  '/* JASPR_PORTAL_V1_BEGIN */',
  '/* JasprPortal: portals take the colour of their frame. The frame block is found by walking down the portal column',
  ' * (at most 24 blocks) in the client world; results are cached per block and cleared when the world changes. A quartz',
  ' * frame splits its portal down the middle: the half nearer the west (or north) end burns blue, the other black.',
  ' * Read-only; no engine calls from DOM handlers. */',
  'var JasprPortal=(function(){',
  '  var FRAMES={48:' + rgb(GREEN) + ',239:' + rgb(YELLOW) + '},PURPLE=' + rgb(PURPLE) + ',BLUE=' + rgb(BLUE) + ',BLACK=' + rgb(BLACK) + ',SEAM=' + rgb(SEAM) + ',TINT={},mc=null,world=null,cache=new Map(),failure=null;',
  '  TINT[' + rgb(GREEN) + ']=[' + GREEN.join(',') + '];TINT[' + rgb(YELLOW) + ']=[' + YELLOW.join(',') + '];TINT[PURPLE]=[' + PURPLE.join(',') + '];TINT[BLUE]=[' + BLUE.join(',') + '];TINT[BLACK]=[' + BLACK.join(',') + '];TINT[SEAM]=[' + SEAM.join(',') + '];',
  '  var api={ov:[' + PURPLE.join(',') + ']};',
  '  function key(x,y,z){return x+","+y+","+z;}',
  '  api.tick=function(m){mc=m;var w=m&&m.X;if(w!==world){world=w;cache.clear();}};',
  '  // BlockColors hook: the colour of the portal block at pos.',
  '  api.tint=function(pos){',
  '    var k=key(pos.m,pos.i,pos.l),c=cache.get(k);if(c!==undefined)return c;',
  '    c=PURPLE;',
  '    try{var w=mc&&mc.X,p=pos,id=90,n=0;if(w){while(id===90&&n++<24){p=EoL(p);id=ENW(CZr(w,p).n);}if(id===155)c=split(w,pos);else if(FRAMES[id]!==undefined)c=FRAMES[id];}}',
  '    catch(e){failure=String(e&&e.message||e).slice(0,160);}',
  '    if(cache.size>8192)cache.clear();cache.set(k,c);return c;',
  '  };',
  '  // Which half of a quartz gate a portal block is in: count portal blocks to either side along the plane.',
  '  function portal(w,p){return ENW(CZr(w,p).n)===90;}',
  '  function side(w,pos,f){var p=pos,n=0;Bw();while(n<22){p=DWK(p,f,1);if(!portal(w,p))break;n++;}return n;}',
  '  function split(w,pos){',
  '    Bw();var alongX=portal(w,DWK(pos,KsT,1))||portal(w,DWK(pos,KsU,1));',
  '    var a=side(w,pos,alongX?KsT:KsV),b=side(w,pos,alongX?KsU:KsW);',
  '    return a<b?BLUE:a>b?BLACK:SEAM;',
  '  }',
  '  function at(x,y,z){var c=cache.get(key(Math.floor(x),Math.floor(y),Math.floor(z)));return c===undefined?PURPLE:c;}',
  '  // Portal particles spawned inside a coloured portal take its colour (vanilla: red .9, green .3, blue 1).',
  '  api.particle=function(p,x,y,z,l){var c=at(x,y,z);if(c===PURPLE)return;var t=TINT[c]||[1,1,1];p.eC=l*t[0];p.ey=l*t[1];p.eu=l*t[2];};',
  '  // The in-portal overlay: the colour of the portal at the player\'s eyes or feet.',
  '  api.overlay=function(m){',
  '    var c=PURPLE;',
  '    try{var e=DWa(m.v,1.0);c=at(e.bh,e.bq,e.bi);if(c===PURPLE)c=at(e.bh,e.bq-1.0,e.bi);}catch(err){failure=String(err&&err.message||err).slice(0,160);}',
  '    api.ov=TINT[c]||TINT[PURPLE];',
  '  };',
  '  $rt_globals.JasprPortalDiagnostics={status:function(){var g=0,v=0,b=0,k=0;cache.forEach(function(c){if(c===PURPLE)v++;else if(c===BLUE)b++;else if(c===BLACK)k++;else g++;});return {coloured:g,purple:v,blue:b,black:k,failure:failure};}};',
  '  return api;',
  '})();',
  '/* JASPR_PORTAL_V1_END */',
].join('\r\n');

function functionBody(text, name) {
  const start = text.indexOf('function ' + name + '(');
  if (start < 0 || text.indexOf('function ' + name + '(', start + 1) >= 0) throw new Error(name + ' must be defined once');
  const end = text.indexOf('\nfunction', start + 10);
  return [start, end < 0 ? text.length : end];
}
function within(text, name, from, to, label) {
  const [start, end] = functionBody(text, name);
  const body = text.slice(start, end);
  if (body.split(from).length !== 2) throw new Error(label + ' anchor must occur exactly once in ' + name);
  return text.slice(0, start) + body.replace(from, () => to) + text.slice(end);
}
function once(text, from, to, label) {
  if (text.split(from).length !== 2) throw new Error(label + ' must occur exactly once');
  return text.replace(from, () => to);
}

function refresh(input) {
  const begin = '/* JASPR_PORTAL_V1_BEGIN */', end = '/* JASPR_PORTAL_V1_END */';
  if (input.split(MARK).length - 1 !== 4) throw new Error('Installed portal hooks are damaged');
  if (input.split(begin).length !== 2 || input.split(end).length !== 2) throw new Error('Portal block fences must occur exactly once');
  const output = input.slice(0, input.indexOf(begin)) + BLOCK + input.slice(input.indexOf(end) + end.length);
  new vm.Script(output, {filename: 'candidate/portal-client/classes.js'});
  return output;
}

/** The engine names the quartz split relies on: EnumFacing's init (Bw), its WEST/EAST/NORTH/SOUTH fields and offset(DWK). */
function checkNames(input) {
  const flat = input.replace(/\r\n/g, '');
  const facing = 'KsU=b;Lih=T(Gu,[HFo,KsS,KsV,KsW,KsT,b]);';
  if (flat.split(facing).length !== 2) throw new Error('EnumFacing fields changed');
  for (const [field, vec] of [['KsV', 'k=ZJ(0,0,(-1));'], ['KsW', 'k=ZJ(0,0,1);'], ['KsT', 'k=ZJ((-1),0,0);'], ['KsU', 'k=ZJ(1,0,0);']]) {
    const at = flat.indexOf('{break _;}' + field + '=b;'), v = at < 0 ? -1 : flat.lastIndexOf(vec, at);
    if (at < 0 || v < 0 || at - v > 120) throw new Error(field + ' is not the expected facing');
  }
  for (const fn of ['function Bw(', 'function DWK(']) if (input.split(fn).length !== 2) throw new Error(fn + ' must be defined once');
}

function build(input) {
  checkNames(input);
  if (input.includes('JASPR_PORTAL_V1')) return refresh(input);
  let output = input;
  output = once(output, '/*JASPR_SKY_V1*/JasprSky.tick(a);', '/*JASPR_SKY_V1*/JasprSky.tick(a);' + MARK + 'JasprPortal.tick(a);', 'runTick hook (after the sky stage)');
  output = within(output, 'FEI', 'h=$z;g=PX(f,h);if(g===null)return (-1);',
    'h=$z;' + MARK + 'if(h===90)return JasprPortal.tint(d);g=PX(f,h);if(g===null)return (-1);', 'portal block colour');
  output = within(output, 'E_F', 'IC(k,C9()*8.0|0);return k;', 'IC(k,C9()*8.0|0);' + MARK + 'JasprPortal.particle(k,d,e,f,l);return k;', 'portal particle colour');
  output = within(output, 'F6G', 'h=1.0;i=1.0;j=1.0;$p=5;',
    'h=1.0;i=1.0;j=1.0;' + MARK + 'JasprPortal.overlay(a.ds);h=JasprPortal.ov[0];i=JasprPortal.ov[1];j=JasprPortal.ov[2];$p=5;', 'portal overlay colour');
  output = once(output, '/* JASPR_SKY_V1_END */', '/* JASPR_SKY_V1_END */\r\n\r\n' + BLOCK, 'append anchor');
  new vm.Script(output, {filename: 'candidate/portal-client/classes.js'});
  return output;
}

// ------------------------------------------------------------------ assets.epk: neutral portal texture, tinted faces

const table = Array.from({length: 256}, (_, n) => { for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1; return n >>> 0; });
function crc(bytes) { let c = 0xffffffff; for (const b of bytes) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function fileEntry(name, value) {
  const len = Buffer.alloc(4), check = Buffer.alloc(4); len.writeUInt32BE(value.length + 5); check.writeUInt32BE(crc(value));
  return Buffer.concat([Buffer.from('FILE'), Buffer.from([Buffer.byteLength(name)]), Buffer.from(name), len, check, value, Buffer.from(':>')]);
}
const TEXTURE = 'assets/minecraft/textures/blocks/portal.png', MODELS = ['assets/minecraft/models/block/portal_ns.json', 'assets/minecraft/models/block/portal_ew.json'];

function tintedModel(value) {
  const model = JSON.parse(value.toString('utf8'));
  for (const element of model.elements) for (const face of Object.values(element.faces)) face.tintindex = 0;
  return Buffer.from(JSON.stringify(model, null, 4) + '\n');
}

/** The archive with portal.png replaced by {@code neutralPng} and both portal models tinted; all else byte-identical. */
function buildAssets(input, neutralPng) {
  const parsed = decode(input), replacements = new Map([[TEXTURE, neutralPng]]);
  for (const name of MODELS) {
    const entry = parsed.entries.find(e => e.name === name);
    if (!entry) throw new Error('missing ' + name);
    const model = JSON.parse(entry.value.toString('utf8'));
    const tinted = model.elements.every(e => Object.values(e.faces).every(f => f.tintindex === 0));
    replacements.set(name, tinted ? entry.value : tintedModel(entry.value));
  }
  if (!parsed.entries.some(e => e.name === TEXTURE)) throw new Error('missing ' + TEXTURE);
  const entries = parsed.entries.map(e => replacements.has(e.name) ? {...e, raw: fileEntry(e.name, replacements.get(e.name))} : e);
  const header = Buffer.from(parsed.header);
  const payload = Buffer.concat([...entries.map(e => e.raw), Buffer.from('END$')]);
  const compressed = parsed.compression === 'G' ? zlib.gzipSync(payload, {level: 9}) : parsed.compression === 'Z' ? zlib.deflateSync(payload, {level: 9}) : payload;
  const output = Buffer.concat([header, compressed, Buffer.from(':::YEE:>')]);
  const verified = decode(output), byName = new Map(verified.entries.map(e => [e.name, e]));
  for (const before of parsed.entries) if (!replacements.has(before.name)) {
    if (!byName.get(before.name).raw.equals(before.raw)) throw new Error('changed ' + before.name);
  }
  for (const [name, value] of replacements) if (!byName.get(name).value.equals(value)) throw new Error('not written ' + name);
  return output;
}

if (require.main === module) {
  const dir = path.join(root, 'candidate', 'portal-client');
  fs.mkdirSync(dir, {recursive: true});
  const inputBytes = fs.readFileSync(source);
  const output = build(inputBytes.toString('latin1'));
  const outputBytes = Buffer.from(output, 'latin1');
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const neutral = fs.readFileSync(path.join(root, 'server/custom-plugins/JasprRuins/pack/portal-neutral.png'));
  const assetsIn = fs.readFileSync(assetSource), assetsOut = buildAssets(assetsIn, neutral);
  fs.writeFileSync(path.join(dir, 'assets.epk'), assetsOut);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes), hooks: output.split(MARK).length - 1,
    assetsInSha256: sha(assetsIn), assetsOutSha256: sha(assetsOut), assetsOutBytes: assetsOut.length};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build, buildAssets, PURPLE, GREEN, BLUE, BLACK, SEAM};
