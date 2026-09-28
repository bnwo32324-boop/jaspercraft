'use strict';

// Exact, candidate-only JasprRealm stage for the composed browser client (site/classes.js): a loader for realm modules
// that are fetched only when a player first enters their realm. The server marks a player in a realm with the hidden
// scoreboard objective "jrm" (display "JRM v1 <realm> <module version> <zone> <liberation mask> <victory>"); the first
// time the client sees it, it loads site/realms/<realm>.js?v=<version> (one small script, cached by the browser) and
// then hands the module the marker's state every tick together with a small engine API:
//  - Minecraft.runTick: JasprRealm.tick(mc) reads the marker (every 10 ticks) and runs the module (every tick);
//  - RenderGlobal.renderSky (DJP): the sky dome colour and the sun's tint, when the module sets them;
//  - EntityRenderer.updateFogColor (GmS wrapper): the fog and clear colour, blended by the module's weight.
// The API: player() (eye position), particle(name, x, y, z, vx, vy, vz[, param]) for a fixed set of particle types,
// setSky(rgb|null), setSun([green, blue]|null), setFog(rgb|null, weight). Leaving the realm clears everything.
// Four fenced hooks (/*JASPR_REALM_V1*/) plus one appended block; every anchor must match exactly once or the build
// stops. It follows the sky and portal stages (scripts/build-sky-client.cjs, scripts/build-portal-client.cjs).
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.REALM_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MARK = '/*JASPR_REALM_V1*/';

/** The particle types a module may spawn, by vanilla name and EnumParticleTypes ordinal. */
const PARTICLES = {smoke: 11, largesmoke: 12, townaura: 22, portal: 24, flame: 26, lava: 27, cloud: 29, endRod: 43, fallingdust: 46};

/** Finds the static field holding each particle type, from the enum's initialiser (checked, never guessed). */
function particleFields(input) {
  const flat = input.replace(/\r\n/g, '');
  const start = flat.indexOf('function EZI(');
  if (start < 0 || flat.indexOf('function EZI(', start + 1) >= 0) throw new Error('the particle types initialiser (EZI) must be defined once');
  const body = flat.slice(start, flat.indexOf('\nfunction', start + 10) < 0 ? flat.length : flat.indexOf('function ', start + 10));
  const fields = {};
  for (const [name, ordinal] of Object.entries(PARTICLES)) {
    const re = new RegExp('d=' + ordinal + ';e=C\\(\\d+\\);f=' + ordinal + ';g=\\d+;(?:h=\\d+;)?\\$p=\\d+;case \\d+:\\w+\\(b,c,d,e,f,g(?:,h)?\\);if\\(B\\(\\)\\)\\{break _;\\}(\\w+)=b;');
    const m = body.match(re);
    if (!m) throw new Error('particle type ' + name + ' (' + ordinal + ') not found');
    fields[name] = m[1];
  }
  if (fields.portal !== 'Kum') throw new Error('particle fields do not line up with the portal stage (portal should be Kum)');
  return fields;
}

function block(fields) {
  const table = Object.entries(fields).map(([n, f]) => n + ':' + f).join(',');
  return [
    '/* JASPR_REALM_V1_BEGIN */',
    '/* JasprRealm: loads a realm\'s client module the first time the server marks the player as inside it (objective',
    ' * "jrm"), and gives it the marker\'s state and a small engine API every tick. Module failures are caught and counted;',
    ' * a module that fails to load is retried after 30 seconds. DOM callbacks only record the loaded module: all engine',
    ' * calls happen inside the game tick. */',
    'var JasprRealm=(function(){',
    '  var mcRef=null,state=null,module=null,moduleName=null,loading=false,retryAt=0,checked=0,failures=[],spawned=0,ticks=0,types=null,loadedMs=0;',
    '  var base=(function(){try{var s=document.currentScript&&document.currentScript.src;return s?s.slice(0,s.split("?")[0].lastIndexOf("/")+1):"";}catch(e){return "";}})();',
    '  var api={sky:null,sun:null,fog:null,fogw:0};',
    '  function fail(e){failures.push(String(e&&e.message||e).slice(0,160));if(failures.length>10)failures.shift();}',
    '  function ptype(name){if(!types){CC();types={' + table + '};}return types[name]||null;}',
    '  var engine={',
    '    player:function(){var e=DWa(mcRef.v,1.0);return {x:e.bh,y:e.bq,z:e.bi};},',
    '    particle:function(name,x,y,z,vx,vy,vz,param){var t=ptype(name),w=mcRef&&mcRef.X;if(!t||!w)return false;',
    '      var q=Bh(param===undefined?0:1);if(param!==undefined)q.data[0]=param|0;FH1(w,t,x,y,z,vx||0,vy||0,vz||0,q);spawned++;return true;},',
    '    setSky:function(c){api.sky=c||null;},',
    '    setSun:function(c){api.sun=c||null;},',
    '    setFog:function(c,w){api.fog=c||null;api.fogw=c?Math.max(0,Math.min(1,+w||0)):0;}',
    '  };',
    '  function clear(){api.sky=null;api.sun=null;api.fog=null;api.fogw=0;}',
    '  function leave(){if(module&&module.leave){try{module.leave(engine);}catch(e){fail(e);}}state=null;clear();}',
    '  function load(name,ver){',
    '    var mods=$rt_globals.JasprRealmModules||{};',
    '    if(mods[name]){module=mods[name];moduleName=name;return;}',
    '    if(loading||Date.now()<retryAt)return;',
    '    loading=true;var t0=Date.now(),s=document.createElement("script");',
    '    s.src=base+"realms/"+name+".js?v="+encodeURIComponent(ver);s.async=true;',
    '    s.onload=function(){loading=false;var m=($rt_globals.JasprRealmModules||{})[name];if(m){module=m;moduleName=name;loadedMs=Date.now()-t0;}else{fail("module "+name+" did not register");retryAt=Date.now()+30000;}};',
    '    s.onerror=function(){loading=false;fail("could not load realms/"+name+".js");retryAt=Date.now()+30000;};',
    '    (document.head||document.documentElement).appendChild(s);',
    '  };',
    '  api.tick=function(mc){',
    '    mcRef=mc;var p=mc&&mc.v;if(!p){if(state)leave();return;}',
    '    if(++checked>=10){checked=0;',
    '      try{var w=mc.X,sb=w&&w.k3,o=sb?Cbd(sb,$rt_str("jrm")):null,d=o?$rt_ustr(o.a47):"",f=d.split(" ");',
    '        if(f.length>=7&&f[0]==="JRM"&&f[1]==="v1"&&/^[a-z]{1,16}$/.test(f[2])&&/^[A-Za-z0-9._-]{1,16}$/.test(f[3])){',
    '          if(state&&state.realm!==f[2])leave();',
    '          state={realm:f[2],version:f[3],zone:f[4],mask:parseInt(f[5],10)|0,victory:f[6]==="1"};',
    '          if(!module||moduleName!==f[2]){module=null;load(f[2],f[3]);}',
    '        }else if(state)leave();',
    '      }catch(e){fail(e);if(state)leave();}',
    '    }',
    '    if(state&&module&&module.tick){ticks++;try{module.tick(engine,state);}catch(e){fail(e);}}',
    '  };',
    '  // After the vanilla (and Ul\'Nhaar) fog colour: blend toward the module\'s fog and clear to it.',
    '  api.fogColor=function(r){if(!api.fog||!state)return;var w=api.fogw,v=1-w;r.eH=r.eH*v+api.fog[0]*w;r.eF=r.eF*v+api.fog[1]*w;r.eJ=r.eJ*v+api.fog[2]*w;GuJ(r.eH,r.eF,r.eJ,0.0);};',
    '  $rt_globals.JasprRealmDiagnostics={status:function(){return {state:state,module:moduleName,loaded:!!module,loading:loading,loadedMs:loadedMs,ticks:ticks,particles:spawned,sky:api.sky,fog:api.fog,fogw:api.fogw,failures:failures.slice(),base:base};}};',
    '  return api;',
    '})();',
    '/* JASPR_REALM_V1_END */',
  ].join('\r\n');
}

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

function refresh(input, fields) {
  const begin = '/* JASPR_REALM_V1_BEGIN */', end = '/* JASPR_REALM_V1_END */';
  if (input.split(MARK).length - 1 !== 4) throw new Error('Installed realm hooks are damaged');
  if (input.split(begin).length !== 2 || input.split(end).length !== 2) throw new Error('Realm block fences must occur exactly once');
  const output = input.slice(0, input.indexOf(begin)) + block(fields) + input.slice(input.indexOf(end) + end.length);
  new vm.Script(output, {filename: 'candidate/realm-client/classes.js'});
  return output;
}

function build(input) {
  const fields = particleFields(input);
  for (const fn of ['CC', 'FH1', 'DWa', 'Cbd', 'GuJ']) functionBody(input, fn);
  if (input.includes('JASPR_REALM_V1')) return refresh(input, fields);
  let output = input;
  output = once(output, '/*JASPR_PORTAL_V1*/JasprPortal.tick(a);', '/*JASPR_PORTAL_V1*/JasprPortal.tick(a);' + MARK + 'JasprRealm.tick(a);', 'runTick hook (after the portal stage)');
  output = within(output, 'DJP', 'if(JasprSky.on){f=JasprSky.sky[0];g=JasprSky.sky[1];h=JasprSky.sky[2];}',
    'if(JasprSky.on){f=JasprSky.sky[0];g=JasprSky.sky[1];h=JasprSky.sky[2];}' + MARK + 'if(JasprRealm.sky){f=JasprRealm.sky[0];g=JasprRealm.sky[1];h=JasprRealm.sky[2];}', 'sky dome');
  output = within(output, 'DJP', 'if(JasprSky.on){r=0.22;s=0.18;}', 'if(JasprSky.on){r=0.22;s=0.18;}' + MARK + 'if(JasprRealm.sun){r=JasprRealm.sun[0];s=JasprRealm.sun[1];}', 'sun tint');
  output = within(output, 'GmS', 'JasprSky.fog(a);', 'JasprSky.fog(a);' + MARK + 'JasprRealm.fogColor(a);', 'fog colour');
  output = once(output, '/* JASPR_PORTAL_V1_END */', '/* JASPR_PORTAL_V1_END */\r\n\r\n' + block(fields), 'append anchor');
  new vm.Script(output, {filename: 'candidate/realm-client/classes.js'});
  return output;
}

if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const output = build(inputBytes.toString('latin1'));
  const outputBytes = Buffer.from(output, 'latin1');
  const dir = path.join(root, 'candidate', 'realm-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes),
    inputBytes: inputBytes.length, outputBytes: outputBytes.length, hooks: output.split(MARK).length - 1, particles: particleFields(output)};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build, particleFields, PARTICLES};
