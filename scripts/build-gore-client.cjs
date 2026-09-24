'use strict';
// Reversible, hash-pinned adapter insertion; never writes the live site.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),vm=require('node:vm');
const root=path.resolve(__dirname,'..');
const BASE='8211696dbce7f7af1eed48324bbb0b3e9d887fab9441e5da900f69a9f9acc901';
const markerStart='/* JASPR_GORE_NATIVE_BEGIN */',markerEnd='/* JASPR_GORE_NATIVE_END */';
function sha(s){return crypto.createHash('sha256').update(s).digest('hex');}
const hooks=[
  ['DbP','BD9(a.Pp,f,p,h);','BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);'],
  ['DbP','case 42:GdY(a);','case 42:JasprGoreBridge.render();GdY(a);'],
  ['Gxv','D_f(j,l);if(B()){break _;}$p=12;','D_f(j,l);if(B()){break _;}JasprGoreBridge.begin(b,j);$p=12;'],
  ['Gxv','j.jV(b,c,d,e,f,g);if(B()){break _;}break b;','j.jV(b,c,d,e,f,g);if(B()){break _;}JasprGoreBridge.end();break b;'],
  ['E7Q','Dle(f);','JasprGoreDraw(a,b,f);',3],
  ['Eu3','Dle(e);','JasprGoreDraw(a,b,e);'],
  ['CM$','case 0:j=a.cHT;','case 0:if(JasprGoreBridge.layer(a.cHT))return;j=a.cHT;'],
  ['Cqg','case 0:$p=1;','case 0:if(JasprGoreBridge.hand(e))return;$p=1;'],
  ['Gsc','case 0:c=C(14);','case 0:JasprGoreBridge.reset();c=C(14);'],
  ['F$t','case 0:$p=1;','case 0:a.$jasprGoreScale=b;$p=1;']
];
function replaceIn(source,name,from,to,count=1){
  const start=source.indexOf('function '+name+'('),end=source.indexOf('\nfunction ',start+10);
  if(start<0||end<0)throw Error('Missing native function '+name);
  const body=source.slice(start,end),matches=body.split(from).length-1;
  if(matches!==count)throw Error(`${name}: expected ${count} hook anchors, got ${matches}`);
  return source.slice(0,start)+body.split(from).join(to)+source.slice(end);
}
function unpatch(source){
  // Stats is the final independent stage. Remove it before either earlier stage.
  if(source.includes('/* JASPR_STATS_KEYBIND_BEGIN */'))source=require('./build-stats-client.cjs').unpatch(source);
  // The later biome presentation stage is independent and reverses before native gore hooks.
  if(source.includes('/* JASPR_BIOMES_BEGIN */'))source=require('./biome-client-patch.cjs').unpatch(source);
  if(!source.includes(markerStart))return source;
  let start=source.indexOf(markerStart),end=source.indexOf(markerEnd,start);
  if(end<0)throw Error('Incomplete gore extension');
  // The previously published gore bundle called layer() without passing the
  // companion model. Migrate that one deployed form before reversing the
  // current hook so a live candidate remains fully reversible.
  const legacyLayerHook='case 0:if(JasprGoreBridge.layer())return;j=a.cHT;';
  const currentLayerHook=hooks.find(([fn])=>fn==='CM$')[2];
  if(source.includes(legacyLayerHook)){
    source=replaceIn(source,'CM$',legacyLayerHook,currentLayerHook);
    start=source.indexOf(markerStart);end=source.indexOf(markerEnd,start);
  }
  source=source.slice(0,start)+source.slice(end+markerEnd.length);
  for(const [fn,from,to,count]of [...hooks].reverse())source=replaceIn(source,fn,to,from,count);
  return source;
}
function build(source){
  const keepStats=source.includes('/* JASPR_STATS_KEYBIND_BEGIN */');
  const keepBiomes=source.includes('/* JASPR_BIOMES_BEGIN */');
  source=unpatch(source);
  if(sha(source)!==BASE)throw Error('Native client changed: re-audit adapter before building. Got '+sha(source));
  for(const [fn,from,to,count]of hooks)source=replaceIn(source,fn,from,to,count);
  const runtime=fs.readFileSync(path.join(root,'client-mods/gore-runtime.js'),'utf8').replace(/\nif\(typeof module[^\n]+\n?$/,'\n');
  const adapter=fs.readFileSync(path.join(root,'client-mods/gore-teavm.js'),'utf8');
  const end=source.lastIndexOf('}));');
  source=source.slice(0,end)+markerStart+'\n'+runtime+'\n'+adapter+'\n'+markerEnd+source.slice(end);
  new vm.Script(source,{filename:'classes.js'});
  if(sha(unpatch(source))!==BASE)throw Error('Reversibility check failed');
  if(keepBiomes)source=require('./biome-client-patch.cjs').build(source);
  return keepStats?require('./build-stats-client.cjs').build(source):source;
}
if(require.main===module){
  const output=path.join(root,'candidate/gore-client');fs.mkdirSync(output,{recursive:true});
  const result=build(fs.readFileSync(path.join(root,'site/classes.js'),'utf8'));
  fs.writeFileSync(path.join(output,'classes.js'),result);
  fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify({baseSHA256:BASE,sha256:sha(result),bytes:Buffer.byteLength(result),hooks:hooks.map(h=>h[0]),finalStages:['gore','biomes','stats','tab-ping-overlay'],pingOverlayField:'NetworkPlayerInfo.bzW',builtAt:new Date().toISOString()},null,2)+'\n');
  console.log('Candidate only: '+path.join(output,'classes.js')+'\nSHA256 '+sha(result));
}
module.exports={build,unpatch,sha,BASE,hooks};
