'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createJasprGore}=require('../client-mods/gore-runtime.js');
const {build,unpatch,sha,BASE}=require('../scripts/build-gore-client.cjs');
const identity=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
function cube(){const m=[];const faces=[[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],[[1,0,1],[0,0,1],[0,1,1],[1,1,1]],[[0,0,1],[0,0,0],[0,1,0],[0,1,1]],[[1,0,0],[1,0,1],[1,1,1],[1,1,0]],[[0,1,0],[1,1,0],[1,1,1],[0,1,1]],[[0,0,1],[1,0,1],[1,0,0],[0,0,0]]];for(const f of faces)for(let i=0;i<4;i++)m.push(...f[i],i===1||i===2?1:0,i>=2?1:0,0,1,0);return m;}
function fixture(count=6,options={}){
  const model={parts:[]},world={},entity={};let calls={stumps:0,draws:0,physics:0,start:0,end:0,local:0,blood:0};
  for(let i=0;i<count;i++)model.parts.push({owner:model,pivot:[i<4?i%2?2:-2:0,i,0],children:[]});
  const api={parts:m=>m.parts,hasGeometry:()=>true,bounds:()=>[0,0,0,1,1,1],pivot:p=>p.pivot,owner:p=>p.owner,children:p=>p.children,
    geometry:()=>cube(),drawLocal:()=>calls.local++,stump:()=>calls.stumps++,raycast:()=>{calls.physics++;return null;},
    startEffects:()=>calls.start++,endEffects:()=>calls.end++,drawPiece:()=>calls.draws++,drawBlood:d=>calls.blood+=d.length};
  const gore=createJasprGore(api,options),snapshot={mob:true,id:7,type:'Zombie',pos:[0,1,5],height:2,health:20,maxHealth:20,dead:false,light:[0,192]};
  let time=1000;
  function frame(health=20,who=entity){time+=50;gore.frame(world,[0,1,0],identity(),time);gore.begin(who,model,{...snapshot,health,dead:health<=0});const hidden=model.parts.map(p=>gore.part(p,.0625,identity(),1,[0,192]));gore.end();return hidden;}
  return {gore,api,model,world,entity,snapshot,calls,frame,get time(){return time;}};
}
test('progressive cuts, final breakup once, and no shared model mutation',()=>{
  const f=fixture(),before=JSON.stringify(f.model.parts.map(p=>p.pivot));
  assert.deepEqual(f.frame(),[false,false,false,false,false,false]);
  assert.equal(f.frame(14).filter(Boolean).length,1);assert.equal(f.gore.status().stats.amputations,1);
  assert.equal(f.frame(8).filter(Boolean).length,2);assert.equal(f.frame(3).filter(Boolean).length,3);
  assert.equal(f.frame(20,{}).filter(Boolean).length,0);
  assert.equal(JSON.stringify(f.model.parts.map(p=>p.pivot)),before);
  assert.equal(f.frame(0).filter(Boolean).length,6);
  const stats=f.gore.status().stats;assert.equal(stats.deaths,1);assert.equal(stats.piecesSpawned,6);
  f.frame(0);assert.equal(f.gore.status().stats.piecesSpawned,stats.piecesSpawned);assert.equal(f.gore.status().stats.deaths,stats.deaths);
});
test('one-shot kills and arbitrary mob types use the same geometry path',()=>{
  for(const type of ['Cow','Chicken','Spider','Bat','Dragon','Squid','Guardian','Villager','Slime','NewModdedMob']){
    const f=fixture();f.snapshot.type=type;assert.equal(f.frame(0).filter(Boolean).length,6,type);assert.equal(f.gore.status().stats.piecesSpawned,6,type);
  }
});
test('single-body models are chipped, not made invisible alive',()=>{
  const f=fixture(1);f.frame();f.frame(14);assert.ok(f.calls.local>0);assert.equal(f.gore.status().stats.piecesSpawned,1);
  f.frame(8);assert.equal(f.gore.status().stats.piecesSpawned,2);
  f.frame(0);assert.equal(f.gore.status().stats.piecesSpawned,4);assert.equal(f.gore.status().stats.deaths,1);
});
test('a detachable parent branch can never suppress an entire living mob',()=>{
  const f=fixture(6);
  f.model.parts[0].children=f.model.parts.slice(1);
  for(const health of [14,8,3]){
    const hidden=f.frame(health).filter(Boolean).length;
    assert.ok(hidden>0,'damage still detaches visible geometry');
    assert.ok(hidden<6,'a living mob always keeps native geometry');
    assert.ok(hidden<=3,'a living mob keeps at least half of its geometry');
  }
  assert.equal(f.frame(0).filter(Boolean).length,6,'death still dismembers the whole model');
});
test('parts of other living mobs, players and armor stands are not affected by another draw',()=>{
  const f=fixture();f.frame(8);f.gore.begin({},f.model,{...f.snapshot,mob:false});
  assert.equal(f.gore.part(f.model.parts[0],1,identity(),1,[0,0]),false);
  f.gore.end();assert.equal(f.gore.part(f.model.parts[0],1,identity(),1,[0,0]),false);
});
test('a stale mob context fails open for unrelated models while the active layer remains scoped',()=>{
  const f=fixture();
  f.gore.frame(f.world,[0,1,0],identity(),f.time+50);
  f.gore.begin(f.entity,f.model,{...f.snapshot,health:0,dead:true});
  const unrelatedModel={},unrelatedPart={owner:unrelatedModel,pivot:f.model.parts[0].pivot,children:[]};
  assert.equal(f.gore.part(unrelatedPart,1,identity(),1,[0,0]),false);
  f.gore.end();

  f.gore.frame(f.world,[0,1,0],identity(),f.time+100);
  f.gore.begin(f.entity,f.model,{...f.snapshot,health:14,dead:false});
  const cut=f.model.parts.find((part)=>f.gore.part(part,1,identity(),1,[0,0]));
  assert.ok(cut);
  const layerModel={},layerPart={owner:layerModel,pivot:cut.pivot,children:[]};
  f.gore.layer(layerModel);
  assert.equal(f.gore.part(layerPart,1,identity(),1,[0,0]),true);
  assert.equal(f.gore.part({owner:{},pivot:cut.pivot,children:[]},1,identity(),1,[0,0]),false);
  f.gore.end();
});
test('healing restores the appropriate visible anatomy',()=>{
  const f=fixture();f.frame(3);assert.equal(f.frame(20).filter(Boolean).length,0);assert.equal(f.frame(14).filter(Boolean).length,1);
});
test('healing within a damage band does not re-emit already severed limbs',()=>{
  const f=fixture();f.frame(8);const before=f.gore.status().stats.piecesSpawned;
  f.frame(8.2);assert.equal(f.gore.status().stats.piecesSpawned,before);
  f.frame(20);f.frame(8);assert.ok(f.gore.status().stats.piecesSpawned>before);
});
test('world changes and expiry release all debris and entity state',()=>{
  const f=fixture();f.frame(0);f.gore.render(f.time+30000);assert.equal(f.gore.status().pieces,0);assert.equal(f.gore.status().blood,0);
  f.gore.frame({},[0,0,0],identity(),f.time+31000);assert.equal(f.gore.status().states,0);
});
test('effects use collision hits for both walls and ground, with paired render state cleanup',()=>{
  const f=fixture();f.frame(0);f.api.raycast=(_w,_a,b)=>({p:b,n:[0,1,0]});f.gore.render(f.time+50);
  assert.ok(f.gore.status().stains>0);assert.equal(f.calls.start,f.calls.end);assert.ok(f.calls.draws>0);
});
test('crowds and mobile effects have strict bounded memory and physics work',()=>{
  const f=fixture(6,{mobile:true});for(let i=0;i<300;i++)f.frame(0,{});
  const s=f.gore.status();assert.ok(s.states<=256);assert.ok(s.pieces<=48);assert.ok(s.blood<=48);
  f.gore.render(f.time+50);assert.ok(f.calls.physics<=48);
});
test('a cosmetic error disables only the effect and restores the native draw path',()=>{
  const f=fixture();f.api.geometry=()=>{throw Error('test fault');};f.frame(14);
  assert.equal(f.gore.status().enabled,false);assert.equal(f.gore.status().stats.errors,1);assert.equal(f.gore.part({},1,identity(),0,[0,0]),false);
});
test('render failures restore graphics state before disabling the cosmetic effect',()=>{
  const f=fixture();f.frame(0);f.api.drawPiece=()=>{throw Error('test draw failure');};f.gore.render(f.time+50);
  assert.equal(f.calls.start,1);assert.equal(f.calls.end,1);assert.equal(f.gore.status().enabled,false);
});
test('detached pieces sample local block light instead of inheriting an emissive mob',()=>{
  const f=fixture();let lightCalls=0;f.api.light=()=>{lightCalls++;return [0,0];};f.frame(0);assert.ok(lightCalls>0);
  let rendered=false;f.api.drawPiece=p=>{rendered=true;assert.deepEqual(p.light,[0,0]);};f.gore.render(f.time+50);assert.ok(rendered);
});
test('camera/world affine conversions round-trip and clipped UVs stay inside source range',()=>{
  const f=fixture(),m=[0,2,0,0,-3,0,0,0,0,0,4,0,10,20,30,1],math=f.gore._math;
  const result=math.multiply(m,math.invert(m));result.forEach((n,i)=>assert.ok(Math.abs(n-identity()[i])<1e-7));
  const clipped=math.sliceMesh(cube(),0,.25,.5);assert.ok(clipped.length>0);assert.equal(clipped.length%32,0);
  for(let i=0;i<clipped.length;i+=8){assert.ok(clipped[i]>=.25-1e-7&&clipped[i]<=.5+1e-7);assert.ok(clipped[i+3]>=0&&clipped[i+3]<=1);}
});
test('native patch is hash pinned, repeatable, fully reversible, and keeps previous startup fixes',()=>{
  const live=fs.readFileSync(path.join(__dirname,'../site/classes.js'),'utf8'),candidate=build(live);
  assert.equal(sha(unpatch(candidate)),BASE);assert.equal(build(candidate),candidate);
  assert.match(candidate,/function DJn\(\)\{HIl=1;\}/);assert.match(candidate,/function FKS\(\)\{\}/);
  assert.throws(()=>build(live+'\n'),/client changed/);
  assert.ok(candidate.includes('if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}'));
  assert.ok(candidate.includes('a.$jasprGoreScale=b;'));
  assert.ok(candidate.includes('p.$jasprGoreScale===undefined?scale:p.$jasprGoreScale'));
});
