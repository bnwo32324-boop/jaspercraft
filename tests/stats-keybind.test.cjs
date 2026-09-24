'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const patch=require('../scripts/build-stats-client.cjs');
const gore=require('../scripts/build-gore-client.cjs'),biomes=require('../scripts/biome-client-patch.cjs');
const {createJasprStatsKeybind}=require('../client-mods/stats-keybind.js');
const {createJasprWaypointKeybind}=require('../client-mods/waypoint-keybind.js');
const root=path.resolve(__dirname,'..');
const live=fs.readFileSync(path.join(root,'site/classes.js'),'utf8');
const base=patch.unpatch(live),candidate=patch.build(live);
const adapter=fs.readFileSync(path.join(root,'client-mods/stats-keybind-teavm.js'),'utf8');
const nativeCache=new Map();
function nativeBody(source,name){
  const key=(source===candidate?'candidate:':'base:')+name;
  if(!nativeCache.has(key))nativeCache.set(key,patch.nativeFunction(source,name).body);
  return nativeCache.get(key);
}

function fixture(){
  let now=1000,playing=true;
  const world={},connection={},handler={qf:connection};
  const client={X:world,v:{d_:handler},G:{$jasprStatsKey:{gO:37,bSp:0},$jasprWaypointKey:{gO:50,bSp:0}}};
  const gate=createJasprStatsKeybind({binding:c=>c&&c.G&&c.G.$jasprStatsKey,playing:()=>playing,now:()=>now});
  return {client,gate,key:client.G.$jasprStatsKey,setPlaying:v=>playing=v,advance:(ms=800)=>now+=ms,
    press:(code=37,repeat=false)=>gate.key(client,code,true,repeat),release:(code=37)=>gate.key(client,code,false,false)};
}

test('K produces one request per physical press, not a held/repeating input stream',()=>{
  const f=fixture();assert.equal(f.press(),true);assert.equal(f.press(),false);
  assert.ok(f.gate.take(f.client));
  for(let i=0;i<10000;i++){f.advance(50);assert.equal(f.press(37,true),false);assert.equal(f.gate.take(f.client),null);}
  assert.equal(f.press(),false,'missing keyup cannot produce more requests');
  f.release();assert.equal(f.press(),true);assert.ok(f.gate.take(f.client));
  assert.equal(f.gate.status().accepted,2);
});
test('rapid taps have a bounded cooldown and the one-slot queue never replays old input',()=>{
  const f=fixture();f.press();f.release();f.advance(749);assert.equal(f.press(),false);
  assert.ok(f.gate.take(f.client));assert.equal(f.gate.take(f.client),null);
  f.release();f.advance(1);assert.equal(f.press(),true);f.advance(1001);
  assert.equal(f.gate.take(f.client),null,'expired commands are discarded');
  f.key.bSp=0x7fffffff;f.gate.take(f.client);assert.equal(f.key.bSp,0);
});
test('rebound and disabled keys use the native binding instead of a hardcoded K listener',()=>{
  const f=fixture();f.key.gO=21;assert.equal(f.press(),false);assert.equal(f.press(21),true);
  f.key.gO=22;assert.equal(f.gate.take(f.client),null,'rebinding invalidates queued old key');
  f.key.gO=0;f.advance();assert.equal(f.press(0),false);assert.equal(f.press(),false);
  f.key.gO=37;assert.equal(f.press(),true);
});
test('a mouse binding uses the same edge/cooldown gate, without changing the default K',()=>{
  const f=fixture();f.key.gO=-96;assert.equal(f.press(-96),true);
  assert.ok(f.gate.take(f.client));f.advance();assert.equal(f.press(-96),false);
  f.release(-96);assert.equal(f.press(-96),true);assert.equal(f.gate.status().defaultKey,'K');
  assert.match(nativeBody(candidate,'Dka'),/if\(c>=0\)JasprStatsBridge\.key\(a,b,d,false\);/);
});
test('invalid contexts and menu/world invalidation cannot leak a queued press',()=>{
  const f=fixture();f.press();f.gate.invalidate(f.client);assert.equal(f.gate.take(f.client),null);
  f.setPlaying(false);f.advance();assert.equal(f.press(),false);
  f.setPlaying(true);assert.equal(f.press(37,true),false,'repeat after closing a menu stays ignored');
  assert.equal(f.press(),true);f.client.X={};assert.equal(f.gate.take(f.client),null);
  f.release();f.advance();f.press();f.client.v={d_:{qf:{}}};assert.equal(f.gate.take(f.client),null);
  f.release();f.advance();f.press();f.client.v.d_.qf={};assert.equal(f.gate.take(f.client),null);
});

// Tiny deterministic TeaVM fiber harness. Native BPd/Gnm/Cn9/Fsg/DRw are taken from the
// candidate itself. Child calls can suspend; stacked parent states must resume in order.
function nativeFixture(){
  const stack=[],suspendNames=new Set(),calls={packets:[],normalTicks:0,registrations:0,saves:0};
  let resuming=false,suspended=false,running=false,now=1000,event={code:37,down:true,repeat:false};
  const javaString=s=>({text:s});
  const ctx={createJasprStatsKeybind,createJasprWaypointKeybind,Date,console,Infinity,
    $rt_str:javaString,$rt_ustr:s=>s.text,
    $rt_globals:{performance:{now:()=>now},document:{hidden:false,activeElement:null,hasFocus:()=>true}},
    FX:()=>resuming,B:()=>suspended,
    Ds:()=>({s:(...values)=>stack.push(...values),l:()=>{assert.ok(stack.length,'fiber stack underflow');const value=stack.pop();if(!stack.length)resuming=false;return value;}}),
    FT:()=>{throw Error('bad fiber state');},D:function(){},Z3:()=>{},
    C:n=>javaString(n===6273?'key.categories.gameplay':String(n)),
    HFa:new Map(),LqU:new Map(),LqV:new Set(),
    G:(_type,length)=>({data:new Array(length)}),T:(_type,data)=>({data}),
    CK:(source,from,target,to,n)=>{for(let i=0;i<n;i++)target.data[to+i]=source.data[from+i];},
    Tr:(map,code,key)=>map.set(code,key),
    AU8:function(){},AU9:function(){},Bg:s=>s.text.length,Cu:(s,a,b)=>javaString(s.text.slice(a,b)),
    AJX:()=>{},HFl:{},A2b:function(){},
    AQp:()=>event.code,ANI:()=>event.down?1:0,BsJ:()=>0,HFV:{cX2:0},
    GWe:()=>{throw Error('original locale lookup unexpectedly used');}
  };
  function child(name,finish){return function(...args){
    assert.equal(running,true,name+' ran outside the native game fiber');
    if(resuming){assert.equal(ctx.Ds().l(),name,'resume wrong child');}
    else if(suspendNames.delete(name)){stack.push(name);suspended=true;return;}
    return finish(...args);
  };}
  ctx.EDK=child('register',(map,name,key)=>{calls.registrations++;map.set(name.text,key);});
  ctx.F5A=child('category',(set,category)=>set.add(category.text));
  ctx.ENU=child('health',p=>p.health);
  ctx.FME=child('connection',c=>c.open?1:0);
  ctx.FhK=child('normalTick',()=>calls.normalTicks++);
  ctx.DuC=child('save',settings=>{calls.saves++;calls.saved=settings.a$W.data.map(k=>'key_'+k.a98.text+':'+k.gO).join('\n');});
  vm.createContext(ctx);
  for(const source of [
    'waypoint-codec.js',
    'waypoint-markers.js',
    'waypoint-markers-teavm.js',
    'waypoint-keybind-teavm.js',
    'waypoint-tab.js',
    'waypoint-tab-teavm.js',
    'dynamic-lights.js',
    'dynamic-lights-teavm.js',
  ]){
    const resolved=require('path').join(__dirname,'..','client-mods',source);
    vm.runInContext(require('fs').readFileSync(resolved,'utf8'),ctx,{filename:resolved});
  }
  for(const name of ['GO','BPd','G6V','Gnm','Cn9','Fsg','C$e','DLK','DHP']){
    // Do not include trailing native global declarations in the test extraction.
    const body=nativeBody(candidate,name).split(/\r?\nvar /)[0];
    vm.runInContext(body,ctx);
  }
  vm.runInContext(adapter,ctx);
  for(const name of ['CFB','DRw'])vm.runInContext(nativeBody(candidate,name),ctx);
  const world={},connection={open:true,bkf:0,wd:child('send',p=>calls.packets.push(p.cmS.text))};
  const handler={bk:world,qf:connection};
  const client={G:{a$W:{data:[]}},X:world,cj:null,uE:1,cp:0,v:{a:world,d_:handler,uS:0,health:20,Fv:0}};
  for(const m of nativeBody(candidate,'DRw').matchAll(/a\.G\.([A-Za-z_$][\w$]*)/g)){
    if(!client.G[m[1]])client.G[m[1]]={gO:0,bSp:0,mz:0};
  }
  client.G.Lv={data:Array.from({length:9},()=>({gO:0,bSp:0,mz:0}))};
  client.G.a7k={gO:46,bSp:0,mz:0};client.G.bdU={gO:45,bSp:0,mz:0};
  client.G.tw=ctx.HFl;client.G.b$I={gO:60,bSp:0,mz:0};
  function call(name,...args){
    assert.equal(running,false);running=true;suspended=false;resuming=stack.length>0;
    try{return ctx[name](...args);}finally{running=false;}
  }
  function press(code=37,repeat=false,down=true){
    event={code,down,repeat};ctx.HFV={cX2:down?(repeat?2:0):1};call('CFB',client);
  }
  function install(){call('JasprStatsInstall',client.G);}
  return {ctx,client,world,handler,connection,calls,stack,install,press,call,
    suspend:name=>suspendNames.add(name),advance:(ms=800)=>now+=ms,tick:()=>call('DRw',client),
    get suspended(){return suspended;}};
}

test('native registration installs a real configurable binding exactly once',()=>{
  const f=nativeFixture();f.install();f.install();
  const key=f.client.G.$jasprStatsKey;
  assert.equal(key.gO,37);assert.equal(key.bSN,37);assert.equal(key.a98.text,'key.jaspr.stats');
  assert.equal(key.bnI.text,'key.categories.gameplay');
  assert.equal(f.ctx.HFa.get('key.jaspr.stats'),key);assert.equal(f.ctx.LqU.get(37),key);
  assert.equal(f.client.G.a$W.data.length,1);assert.equal(f.calls.registrations,1);
  vm.runInContext(patch.nativeFunction(candidate,'GWe').body,f.ctx);
  assert.equal(f.call('GWe',key.a98,{data:[]}).text,'Upgrade Stats');
  f.call('C$e',f.client.G,key,21);
  assert.equal(f.calls.saves,1);assert.equal(f.calls.saved,'key_key.jaspr.stats:21');
  f.press();f.tick();assert.deepEqual(f.calls.packets,[]);
  f.press(21);f.tick();assert.deepEqual(f.calls.packets,['/stats']);
});
test('native registration resumes before appending; it cannot allocate duplicate bindings',()=>{
  for(const where of ['register','category']){
    const f=nativeFixture();f.suspend(where);f.install();assert.ok(f.suspended);
    assert.equal(f.client.G.$jasprStatsKey,undefined);assert.equal(f.client.G.a$W.data.length,0);
    f.install();assert.equal(f.suspended,false);assert.equal(f.stack.length,0);
    assert.equal(f.calls.registrations,1);assert.equal(f.client.G.a$W.data.length,1);
  }
});
test('native CFB -> DRw -> Cn9 sends the exact server command once without opening chat',()=>{
  const f=nativeFixture();f.install();f.press();f.tick();
  assert.deepEqual(f.calls.packets,['/stats']);assert.equal(f.client.cj,null);
  for(let i=0;i<100;i++){f.advance();f.press(37,true);f.tick();}
  assert.deepEqual(f.calls.packets,['/stats']);assert.equal(f.calls.normalTicks,101);
  f.press(37,false,false);f.press();f.tick();assert.deepEqual(f.calls.packets,['/stats','/stats']);
});
test('each native suspension resumes both the sidecar and original DRw fiber exactly once',()=>{
  for(const where of ['health','connection','send','normalTick']){
    const f=nativeFixture();f.install();f.press();f.suspend(where);f.tick();
    assert.ok(f.suspended,where);assert.ok(f.stack.length>0,where);
    f.tick();assert.equal(f.stack.length,0,where);assert.equal(f.suspended,false,where);
    assert.deepEqual(f.calls.packets,['/stats'],where);assert.equal(f.calls.normalTicks,1,where);
    f.tick();assert.deepEqual(f.calls.packets,['/stats'],where);assert.equal(f.calls.normalTicks,2,where);
  }
});
test('native state guards reject chat, menus, death, lost focus and disconnected/loading worlds',()=>{
  const changes={
    chat:f=>f.client.cj={},inventory:f=>f.client.cj={},pause:f=>f.client.cp=1,
    noFocus:f=>f.client.uE=0,hidden:f=>f.ctx.$rt_globals.document.hidden=true,
    inactiveTab:f=>f.ctx.$rt_globals.document.hasFocus=()=>false,
    textField:f=>f.ctx.$rt_globals.document.activeElement={tagName:'INPUT'},
    textarea:f=>f.ctx.$rt_globals.document.activeElement={tagName:'TEXTAREA'},
    editor:f=>f.ctx.$rt_globals.document.activeElement={isContentEditable:true},
    dead:f=>f.client.v.uS=1,zeroHealth:f=>f.client.v.health=0,
    missingWorld:f=>f.client.X=null,loading:f=>f.handler.bk=null,
    missingPlayer:f=>f.client.v=null,missingHandler:f=>f.client.v.d_=null,
    missingConnection:f=>f.handler.qf=null,disconnecting:f=>f.connection.bkf=1,
    closedSocket:f=>f.connection.open=false
  };
  for(const [name,change]of Object.entries(changes)){
    const f=nativeFixture();f.install();change(f);
    // Check sidecar fiber directly: vanilla DRw itself assumes a non-null player.
    if(name==='chat'||name==='inventory')f.ctx.JasprStatsBridge.key(f.client,37,true,false);
    else f.press();
    f.call('JasprStatsTick',f.client);assert.deepEqual(f.calls.packets,[],name);
  }
});
test('context changes while native health/connection checks yield discard the request',()=>{
  const changes={
    death:f=>f.client.v.health=0,
    menu:f=>f.client.cj={},disconnect:f=>f.connection.open=false,
    player:f=>f.client.v={...f.client.v},world:f=>f.client.X={},
    closedMenu:f=>{f.ctx.JasprStatsBridge.invalidate(f.client);f.ctx.JasprStatsBridge.invalidate(f.client);},
    expiry:f=>f.advance(1001)
  };
  for(const [name,change]of Object.entries(changes)){
    const f=nativeFixture();f.install();f.press();f.suspend('health');f.call('JasprStatsTick',f.client);
    assert.ok(f.suspended);change(f);f.call('JasprStatsTick',f.client);
    assert.equal(f.stack.length,0,name);assert.deepEqual(f.calls.packets,[],name);
  }
});
test('the actual native Controls/save/load paths all use the appended keyBindings array',()=>{
  const ctor=patch.nativeFunction(candidate,'B$i').body;
  assert.ok(ctor.indexOf('JasprStatsInstall(a)')<ctor.indexOf('DBw(a)'));
  assert.match(ctor,/case 40:JasprWaypointInstall\(a\);if\(B\(\)\)\{break _;\}JasprDynamicLightsInstall\(a\);if\(B\(\)\)\{break _;\}JasprStatsInstall\(a\);if\(B\(\)\)\{break _;\}\$p=38;case 38:DBw\(a\)/);
  const save=patch.nativeFunction(candidate,'C77').body,load=patch.nativeFunction(candidate,'Ely').body;
  assert.match(save,/j=a\.a\$W\.data;f=j\.length;k=0/);
  assert.match(save,/DLK\(i\)/);assert.match(save,/DHP\(i\)/);
  assert.match(load,/b=a\.a\$W\.data;f=b\.length;p=0/);assert.match(load,/DLK\(o\)/);
  assert.ok(load.includes('DrU(o,'),'native loader applies the saved key code');
  assert.match(patch.nativeFunction(candidate,'Gme').body,/f=c\.G\.a\$W;/);
  for(const name of ['C77','Ely','DBw','C$E','DuC','C$e','Gme','CjR'])
    assert.equal(patch.nativeFunction(candidate,name).body,patch.nativeFunction(base,name).body,name+' unchanged');
});
test('the final stage is pinned, parsed, idempotent and byte-for-byte reversible',()=>{
  assert.equal(patch.sha(base),patch.CURRENT_BASE);new vm.Script(candidate);
  assert.equal(patch.unpatch(candidate),base);assert.equal(patch.build(candidate),candidate);
  // Stage-bloat tripwire (raised 85k->110k for the shader-pack stage, 110k->125k
  // for its hardening, 125k->140k for the tab-held waypoint readout).
  assert.ok(Buffer.byteLength(candidate)-Buffer.byteLength(base)<140000);
  assert.throws(()=>patch.build(base+'\n'),/Native client changed/);
  assert.throws(()=>patch.build(candidate.replace('case 49:JasprStatsTick(a);','case 49:JasprStatsTick(b);')),/hook anchor/);
  assert.throws(()=>patch.unpatch(candidate.replace(patch.end,'')),/Incomplete/);
  assert.throws(()=>patch.unpatch(candidate+patch.begin),/duplicate/);
  assert.throws(()=>patch.unpatch(base+patch.end),/Orphaned/);
});
test('the native sound mixer applies master gain continuously, then category gain',()=>{
  const master={},music={},settings={volumes:new Map([[master,1],[music,1]])};
  const ctx={
    FX:()=>false,B:()=>false,Ds:()=>({s:()=>{}}),FT:()=>{throw Error('bad fiber state');},
    Mx:s=>s.volume,Cx:()=>{},Lnd:master,D9S:(options,category)=>options.volumes.get(category)??1,
    FjJ:(value,min,max)=>Math.max(min,Math.min(max,value))
  };
  vm.createContext(ctx);vm.runInContext(nativeBody(candidate,'FyU'),ctx);
  const sound={volume:1,yo:music},gain=()=>ctx.FyU({U7:settings},sound);
  settings.volumes.set(master,0.01);assert.equal(gain(),0.01);
  settings.volumes.set(master,0.25);assert.equal(gain(),0.25);
  settings.volumes.set(master,1);assert.equal(gain(),1);
  settings.volumes.set(master,0.5);settings.volumes.set(music,0.4);assert.equal(gain(),0.2);
  sound.yo=null;assert.equal(gain(),0.5,'uncategorized sources still obey master volume');
  sound.yo=master;assert.equal(gain(),0.5,'master-tagged sources are not multiplied twice');
});
test('earlier builders remove stats first and reapply it once without circular recursion',()=>{
  assert.equal(gore.sha(gore.unpatch(candidate)),gore.BASE);
  // The biome input pin predates the current gore stage (layer companion arg +
  // runtime comment); the clean post-gore composition is its CURRENT_BASE.
  assert.ok([biomes.BASE,biomes.CURRENT_BASE].includes(biomes.sha(biomes.unpatch(candidate))));
  assert.equal(gore.build(candidate),candidate);assert.equal(biomes.build(candidate),candidate);
  assert.equal(patch.build(gore.build(biomes.build(candidate))),candidate);
  assert.equal(candidate.split(patch.begin).length,2);
});
test('prior stages and all unrelated native functions stay byte-identical',()=>{
  // DtH/DQP/GyZ/GmS are renamed (not hook-patched) by the lighting stages, so
  // they and their renamed twins are audited separately below instead of skipped blindly.
  const modified=new Set(patch.hooks.map(h=>h[0]));modified.add('DtH').add('DtH_orig').add('DQP').add('DQP_orig').add('GyZ').add('GyZ_orig').add('GmS').add('GmS_orig');
  function nativeSections(source){
    source=source.slice(0,source.indexOf('/* JASPR_GORE_NATIVE_BEGIN */'));
    const functions=[...source.matchAll(/\bfunction ([A-Za-z_$][\w$]*)\(/g)];
    const sections=new Map();
    for(let i=0;i<functions.length-1;i++)sections.set(functions[i][1],source.slice(functions[i].index,functions[i+1].index));
    return sections;
  }
  const before=nativeSections(base),after=nativeSections(candidate);
  assert.equal(after.size,before.size);assert.ok(before.size>10000);
  for(const [name,body]of before){
    if(modified.has(name))continue;
    assert.equal(after.get(name),body,name);
  }
  for(const marker of ['JASPR_GORE_NATIVE','JASPR_BIOMES']){
    const a='/* '+marker+'_BEGIN */',b='/* '+marker+'_END */';
    assert.equal(candidate.slice(candidate.indexOf(a),candidate.indexOf(b)+b.length),base.slice(base.indexOf(a),base.indexOf(b)+b.length));
  }
  const beforeDtH=before.get('DtH'),afterDtHOrig=after.get('DtH_orig');
  assert.ok(beforeDtH&&afterDtHOrig,'sampler rename preserves the native body');
  assert.equal(afterDtHOrig,beforeDtH.replace('function DtH(','function DtH_orig('));
  assert.equal(after.get('DtH'),undefined,'no second DtH declaration outside the adapter block');
  assert.match(candidate,/function DtH\(a, b, c\) \{\s*return JasprDynamicLights\.sample\(DtH_orig, a, b, c\);\s*\}/);
  const beforeDQP=before.get('DQP'),afterDQPOorig=after.get('DQP_orig');
  assert.ok(beforeDQP&&afterDQPOorig,'world sampler rename preserves the native body');
  assert.equal(afterDQPOorig,beforeDQP.replace('function DQP(','function DQP_orig('));
  assert.equal(after.get('DQP'),undefined,'no second DQP declaration outside the adapter block');
  assert.match(candidate,/function DQP\(a, b, c\) \{\s*return JasprDynamicLights\.sampleWorld\(DQP_orig, a, b, c\);\s*\}/);
  for (const name of ['GyZ', 'GmS']) {
    const beforeFn = before.get(name), afterFn = after.get(name + '_orig');
    assert.ok(beforeFn && afterFn, name + ' rename preserves the native body');
    assert.equal(afterFn, beforeFn.replace('function ' + name + '(', 'function ' + name + '_orig('));
    assert.equal(after.get(name), undefined, 'no second ' + name + ' declaration outside the adapter block');
  }
  assert.doesNotMatch(adapter,/addEventListener|setTimeout|setInterval|fetch\(|localStorage|WebSocket|requestAnimationFrame/);
});
