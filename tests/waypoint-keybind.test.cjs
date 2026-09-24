'use strict';
// M keybind + marker glue tests. Fixture mirrors stats-keybind.test.cjs so both
// bridges coexist exactly as in the shipped bundle.
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const patch=require('../scripts/build-stats-client.cjs');
const {createJasprStatsKeybind}=require('../client-mods/stats-keybind.js');
const {createJasprWaypointKeybind}=require('../client-mods/waypoint-keybind.js');
const root=path.resolve(__dirname,'..');
const live=fs.readFileSync(path.join(root,'site/classes.js'),'utf8');
const base=patch.unpatch(live),candidate=patch.build(live);
const adapter=fs.readFileSync(path.join(root,'client-mods/stats-keybind-teavm.js'),'utf8');
const waypointAdapter=fs.readFileSync(path.join(root,'client-mods/waypoint-keybind-teavm.js'),'utf8');
function nativeBody(source,name){
  return patch.nativeFunction(source,name).body;
}

function fixture(){
  let now=1000,playing=true;
  const world={},connection={},handler={qf:connection};
  const client={X:world,v:{d_:handler},G:{$jasprStatsKey:{gO:37,bSp:0},$jasprWaypointKey:{gO:50,bSp:0}}};
  const stats=createJasprStatsKeybind({binding:c=>c&&c.G&&c.G.$jasprStatsKey,playing:()=>playing,now:()=>now});
  const gate=createJasprWaypointKeybind({binding:c=>c&&c.G&&c.G.$jasprWaypointKey,playing:()=>playing,now:()=>now});
  return {client,stats,gate,
    key:client.G.$jasprWaypointKey,
    setPlaying:v=>playing=v,advance:(ms=800)=>now+=ms,
    press:(code=50,repeat=false)=>gate.key(client,code,true,repeat),
    release:(code=50)=>gate.key(client,code,false,false),
    statsPress:(code=37,repeat=false)=>stats.key(client,code,true,repeat)};
}

test('M produces one /waypoints request per physical press',()=>{
  const f=fixture();
  assert.equal(f.press(),true);assert.equal(f.press(),false);
  assert.ok(f.gate.take(f.client));
  for(let i=0;i<1000;i++){f.advance(50);assert.equal(f.press(50,true),false);assert.equal(f.gate.take(f.client),null);}
  assert.equal(f.press(),false,'missing keyup cannot produce more requests');
  f.release();assert.equal(f.press(),true);assert.ok(f.gate.take(f.client));
});

test('K and M bridges stay independent with separate cooldowns',()=>{
  const f=fixture();
  assert.equal(f.statsPress(),true);assert.equal(f.press(),true);
  assert.ok(f.stats.take(f.client));assert.ok(f.gate.take(f.client));
  assert.equal(f.gate.status().defaultKey,'M');assert.equal(f.stats.status().defaultKey,'K');
  f.key.gO=0;f.advance();assert.equal(f.press(),false);assert.equal(f.press(50),false);
  f.key.gO=50;assert.equal(f.press(),true);
});

test('rebinding M does not disturb the K binding',()=>{
  const f=fixture();
  f.client.G.$jasprStatsKey.gO=37;f.key.gO=21;
  assert.equal(f.press(21),true);assert.equal(f.statsPress(37),true);
  assert.ok(f.gate.take(f.client));assert.ok(f.stats.take(f.client));
});

// Tiny deterministic TeaVM fiber harness, mirroring stats-keybind.test.cjs.
function nativeFixture(){
  const stack=[],suspendNames=new Set(),calls={packets:[],normalTicks:0,registrations:0,saves:0};
  let resuming=false,suspended=false,running=false,now=1000,event={code:50,down:true,repeat:false};
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
  for(const name of ['GO','BPd','G6V','Gnm','Cn9','Fsg','C$e','DLK','DHP']){
    const body=nativeBody(candidate,name).split(/\r?\nvar /)[0];
    vm.runInContext(body,ctx);
  }
  vm.runInContext(adapter,ctx);
  vm.runInContext(waypointAdapter,ctx);
  vm.runInContext(fs.readFileSync(path.join(root,'client-mods/waypoint-tab.js'),'utf8'),ctx);
  vm.runInContext(fs.readFileSync(path.join(root,'client-mods/waypoint-tab-teavm.js'),'utf8'),ctx);
  vm.runInContext(fs.readFileSync(path.join(root,'client-mods/dynamic-lights.js'),'utf8'),ctx);
  vm.runInContext(fs.readFileSync(path.join(root,'client-mods/dynamic-lights-teavm.js'),'utf8'),ctx);
  for(const source of ['waypoint-codec.js','waypoint-markers.js','waypoint-markers-teavm.js']){
    const file=require('path').join(root,'client-mods',source);
    vm.runInContext(require('fs').readFileSync(file,'utf8'),ctx,{filename:file});
  }
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
  function press(code=50,repeat=false,down=true){
    event={code,down,repeat};ctx.HFV={cX2:down?(repeat?2:0):1};call('CFB',client);
  }
  function install(){call('JasprWaypointInstall',client.G);}
  function installStats(){call('JasprStatsInstall',client.G);}
  return {ctx,client,world,handler,connection,calls,stack,install,installStats,press,call,
    suspend:name=>suspendNames.add(name),advance:(ms=800)=>now+=ms,tick:()=>call('DRw',client),
    get suspended(){return suspended;}};
}

test('native registration installs the M binding alongside K without duplicates',()=>{
  const f=nativeFixture();f.install();f.installStats();f.install();
  const key=f.client.G.$jasprWaypointKey;
  assert.equal(key.gO,50);assert.equal(key.bSN,50);assert.equal(key.a98.text,'key.jaspr.waypoints');
  assert.equal(key.bnI.text,'key.categories.gameplay');
  assert.equal(f.ctx.HFa.get('key.jaspr.waypoints'),key);assert.equal(f.ctx.LqU.get(50),key);
  assert.equal(f.client.G.a$W.data.length,2);assert.equal(f.calls.registrations,2);
  vm.runInContext(patch.nativeFunction(candidate,'GWe').body,f.ctx);
  assert.equal(f.call('GWe',key.a98,{data:[]}).text,'Waypoints Menu');
  f.call('C$e',f.client.G,key,21);
  assert.ok(f.calls.saved.includes('key_key.jaspr.waypoints:21'));
  assert.ok(f.calls.saved.includes('key_key.jaspr.stats:37'));
});

test('native M press sends /waypoints without disturbing /stats traffic',()=>{
  const f=nativeFixture();f.install();f.installStats();f.press();f.tick();
  assert.deepEqual(f.calls.packets,['/waypoints']);
  f.press(37);f.tick();
  assert.deepEqual(f.calls.packets,['/waypoints','/stats']);
});

test('native Tab press/release sends compass and instant clear',()=>{
  const f=nativeFixture();f.install();f.installStats();
  f.press(15);f.tick();
  assert.deepEqual(f.calls.packets,['/wp compass']);
  f.press(15,false,false);f.tick();
  assert.deepEqual(f.calls.packets,['/wp compass','/wp compassoff']);
});

test('Tab repeat refreshes without duplicate compass sends',()=>{
  const f=nativeFixture();f.install();f.installStats();
  f.press(15);f.tick();
  assert.deepEqual(f.calls.packets,['/wp compass']);
  f.press(15,true);f.tick();
  assert.deepEqual(f.calls.packets,['/wp compass']);
});

test('waypoint hooks are present, ordered, and reversible',()=>{
  const bodies={
    ctor:nativeBody(candidate,'B$i'),
    drw:nativeBody(candidate,'DRw'),
    gwe:nativeBody(candidate,'GWe'),
    dka:nativeBody(candidate,'Dka'),
    ggw:nativeBody(candidate,'GGw'),
    emm:nativeBody(candidate,'Emm'),
    gsc:nativeBody(candidate,'Gsc'),
    dbp:nativeBody(candidate,'DbP')
  };
  assert.ok(bodies.ctor.indexOf('JasprWaypointInstall(a)')<bodies.ctor.indexOf('JasprStatsInstall(a)'),
    'waypoint binding installs before stats in the same state');
  assert.match(bodies.gwe,/if\(b!==null&&b===JasprWaypointKeyDescription\)return JasprWaypointKeyLabel;/);
  assert.match(bodies.dka,/if\(c>=0\)JasprStatsBridge\.key\(a,b,d,false\);if\(c>=0\)JasprWaypointBridge\.key\(a,b,d,false\);/);
  assert.match(bodies.drw,/JasprStatsTick\(a\);if\(B\(\)\)\{break _;\}JasprWaypointTick\(a\);if\(B\(\)\)\{break _;\}JasprWaypointTabTick\(a\);if\(B\(\)\)\{break _;\}/);
  assert.match(bodies.ggw,/JasprStatsBridge\.invalidate\(a\);JasprWaypointBridge\.invalidate\(a\);JasprWaypointTab\.invalidate\(a\);/);
  assert.match(bodies.emm,/JasprStatsBridge\.invalidate\(a\);JasprWaypointBridge\.invalidate\(a\);JasprWaypointTab\.invalidate\(a\);/);
  assert.match(bodies.gsc,/JasprWaypointBridge\.invalidate\(a\);JasprWaypointTab\.invalidate\(a\);JasprWaypointMarkers\.reset\(\);/);
  assert.match(bodies.dbp,/case 42:if\(JasprWaypointTabHeld\(a\)\)JasprWaypointMarkers\.draw\(a\);/);
  assert.match(bodies.dka,/if\(c>=0\)JasprStatsBridge\.key\(a,b,d,false\);if\(c>=0\)JasprWaypointBridge\.key\(a,b,d,false\);if\(c>=0\)JasprDynamicLightsKey\.key\(a,b,d,false\);if\(c>=0\)JasprWaypointTabPoll\(a,b,d,false\);/);
  const dab = nativeBody(candidate, 'DaB'), cay = nativeBody(candidate, 'Cay'),
    cef = nativeBody(candidate, 'CeF'), fjm = nativeBody(candidate, 'Fjm');
  assert.match(dab, /a\.cGa=JasprShadersPickerMode\?JasprShadersPickerTitle\(\):b;/);
  assert.match(dab, /e=JasprShadersPickerMode\?SHADER_PACK_COUNT:j\.length;/);
  assert.match(dab, /JasprShadersAppendVideoRow\(a,b,i\)/);
  assert.match(dab, /case 20:\$z=JasprShadersPickerButton\(910\+\(f\|0\),g,JasprShadersPickerLabel\(f\)\)/);
  assert.match(dab, /case 21:if\(\(f\+1\|0\)>=SHADER_PACK_COUNT\)\{\$p=8;continue _;\}\$z=JasprShadersPickerButton\(911\+\(f\|0\),e,JasprShadersPickerLabel\(f\+1\)\)/);
  assert.doesNotMatch(dab, /e=901;f=\(a\.q\/2\|0\)\+5\|0;g=a\.L-27\|0;/);
  assert.match(cay, /h\.bF===901\)\{i=a\.Wy;\$p=7;continue _;/);
  assert.match(cay, /h\.bF>=910&&h\.bF<=914\)\{i=a\.Wy;\$p=8;continue _;/);
  assert.match(cay, /case 8:JasprShaders\.choose\(h\.bF-910\);JasprShadersPickerMode=0;/);
  assert.match(cef, /if\(b\.bS&&b\.bF==200\)\{JasprShadersPickerMode=0;/);
  assert.match(fjm, /case 7:if\(!JasprShadersEnabled\)\{Ctb\(a,f,b,c\);if\(B\(\)\)\{break _;\}return;\}JasprShadersPass\(a,f,b,c\);if\(B\(\)\)\{break _;\}return;/);
});

test('dynamic lights installs an L binding, labels it, and cycles modes locally',()=>{
  const f=nativeFixture();f.install();f.installStats();
  f.call('JasprDynamicLightsInstall',f.client.G);
  const key=f.client.G.$jasprDynamicLightsKey;
  assert.equal(key.gO,38);assert.equal(key.bSN,38);assert.equal(key.a98.text,'key.jaspr.dynamiclights');
  assert.equal(f.ctx.HFa.get('key.jaspr.dynamiclights'),key);assert.equal(f.ctx.LqU.get(38),key);
  vm.runInContext(patch.nativeFunction(candidate,'GWe').body,f.ctx);
  assert.equal(f.call('GWe',key.a98,{data:[]}).text,'Dynamic Lights');
  assert.equal(f.ctx.JasprDynamicLights.mode(),1);
  f.press(38);assert.equal(f.ctx.JasprDynamicLights.mode(),2);
  f.press(38);assert.equal(f.ctx.JasprDynamicLights.mode(),0);
  f.press(38);assert.equal(f.ctx.JasprDynamicLights.mode(),1);
  f.press(38,true);assert.equal(f.ctx.JasprDynamicLights.mode(),1,'key repeat never cycles');
});

test('marker diagnostics load without natives and report safely',()=>{
  const f=nativeFixture();
  f.ctx.JasprWaypointMarkers.reset();
  const status=f.ctx.JasprWaypointMarkers.status();
  assert.equal(status.markers,0);assert.equal(status.disabled,false);
  assert.equal(status.maxMarkers,24);assert.equal(status.engine,'renderGlobal.Pp/a.fd.X.k3');
});

test('new client files carry no page-level side effects',()=>{
  for(const name of ['waypoint-keybind.js','waypoint-keybind-teavm.js','waypoint-codec.js','waypoint-markers.js','waypoint-markers-teavm.js',
      'waypoint-tab.js','waypoint-tab-teavm.js',
      'dynamic-lights.js','dynamic-lights-teavm.js','shader-packs.js','shader-packs-teavm.js']){
    const source=fs.readFileSync(path.join(root,'client-mods',name),'utf8');
    assert.doesNotMatch(source,/addEventListener|setTimeout|setInterval|fetch\(|WebSocket|requestAnimationFrame/);
    // Settings persistence is the only client file use allowed for storage,
    // and only through single namespaced keys.
    if(/localStorage/.test(source))assert.ok(/jaspr\.(dl\.mode|shader\.(pack|status))/.test(source),name);
  }
});
