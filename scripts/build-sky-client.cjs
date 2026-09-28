'use strict';

// Exact, candidate-only JasprSky stage for the composed browser client (site/classes.js): the eerie sky of Ul'Nhaar.
// While the server's hidden scoreboard objective "jrs" (display "JRS v1 ...") is present, the client paints:
//  - Minecraft.runTick: JasprSky.tick(mc) checks for the objective every 10 ticks (next to the tank hook);
//  - RenderGlobal.renderSky (DJP): a sickly green sky dome, a blood-red moon (and sun), reddened stars;
//  - EntityRenderer.updateFogColor (GmS wrapper): a mist that slowly turns from corpse-green to blood-red and back
//    (about 40 s), so the horizon and the haze over the ruins keep changing (at low render distances the whole sky is
//    that mist; at higher ones the zenith stays green).
// Five fenced hooks (/*JASPR_SKY_V1*/) plus one appended block; every anchor must match exactly once or the build stops.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.SKY_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MARK = '/*JASPR_SKY_V1*/';

const BLOCK = [
  '/* JASPR_SKY_V1_BEGIN */',
  '/* JasprSky: the sky over Ul\'Nhaar. The server marks players in that dimension with the hidden objective "jrs"; while',
  ' * it is present the sky dome is a sickly green over a mist that turns from corpse-green to blood-red and back, and',
  ' * the moon and stars are blood red.',
  ' * Read-only state; no engine calls from DOM handlers. */',
  'var JasprSky=(function(){',
  '  var checked=0,failure=null,GREEN=[0.11,0.18,0.10],RED=[0.25,0.04,0.05],pulse=0;',
  '  var api={on:false,sky:[0.10,0.20,0.12]};',
  '  api.tick=function(mc){',
  '    var p=mc&&mc.v,w,sb,o;pulse+=0.025;if(!p){api.on=false;return;}',
  '    if(++checked<10)return;checked=0;',
  '    try{w=mc.X;sb=w&&w.k3;o=sb?Cbd(sb,$rt_str("jrs")):null;api.on=!!(o&&$rt_ustr(o.a47).indexOf("JRS v1")===0);failure=null;}',
  '    catch(e){api.on=false;failure=String(e&&e.message||e).slice(0,160);}',
  '  };',
  '  // After the vanilla fog colour: blend most of the way to the mist (lingering at each colour) and clear to it.',
  '  api.mist=function(){var w=0.5+0.5*Math.sin(pulse*0.3);return w*w*(3-2*w);};',
  '  api.fog=function(r){',
  '    if(!api.on)return;',
  '    var w=api.mist(),v=1-w;',
  '    r.eH=r.eH*0.12+(GREEN[0]*v+RED[0]*w)*0.88;r.eF=r.eF*0.12+(GREEN[1]*v+RED[1]*w)*0.88;r.eJ=r.eJ*0.12+(GREEN[2]*v+RED[2]*w)*0.88;',
  '    GuJ(r.eH,r.eF,r.eJ,0.0);',
  '  };',
  '  $rt_globals.JasprSkyDiagnostics={status:function(){return {on:api.on,failure:failure,mist:Math.round(api.mist()*100)/100};}};',
  '  return api;',
  '})();',
  '/* JASPR_SKY_V1_END */',
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
  const begin = '/* JASPR_SKY_V1_BEGIN */', end = '/* JASPR_SKY_V1_END */';
  if (input.split(MARK).length - 1 !== 5) throw new Error('Installed sky hooks are damaged');
  if (input.split(begin).length !== 2 || input.split(end).length !== 2) throw new Error('Sky block fences must occur exactly once');
  const output = input.slice(0, input.indexOf(begin)) + BLOCK + input.slice(input.indexOf(end) + end.length);
  new vm.Script(output, {filename: 'candidate/sky-client/classes.js'});
  return output;
}

function build(input) {
  if (input.includes('JASPR_SKY_V1')) return refresh(input);
  let output = input;
  output = once(output, 'JasprTank.tick(a);', 'JasprTank.tick(a);' + MARK + 'JasprSky.tick(a);', 'runTick hook');
  output = within(output, 'DJP', 'e=$z;f=e.bh;g=e.bq;h=e.bi;',
    'e=$z;f=e.bh;g=e.bq;h=e.bi;' + MARK + 'if(JasprSky.on){f=JasprSky.sky[0];g=JasprSky.sky[1];h=JasprSky.sky[2];}', 'sky dome');
  output = within(output, 'DJP', 'q=1.0-R$(a.d8,b);j=1.0;r=1.0;s=1.0;',
    'q=1.0-R$(a.d8,b);j=1.0;r=1.0;s=1.0;' + MARK + 'if(JasprSky.on){r=0.22;s=0.18;}', 'blood moon');
  output = within(output, 'DJP', 'case 45:CFh(u,u,u,u);',
    'case 45:' + MARK + 'CFh(u,JasprSky.on?u*0.35:u,JasprSky.on?u*0.4:u,u);', 'red stars');
  output = within(output, 'GmS', 'GmS_orig(a, b);', 'GmS_orig(a, b);' + MARK + 'JasprSky.fog(a);', 'fog colour');
  output = once(output, '/* JASPR_TANK_V1_END */', '/* JASPR_TANK_V1_END */\r\n\r\n' + BLOCK, 'append anchor');
  new vm.Script(output, {filename: 'candidate/sky-client/classes.js'});
  return output;
}

if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const output = build(inputBytes.toString('latin1'));
  const outputBytes = Buffer.from(output, 'latin1');
  const dir = path.join(root, 'candidate', 'sky-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes),
    inputBytes: inputBytes.length, outputBytes: outputBytes.length, hooks: output.split(MARK).length - 1};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build};
