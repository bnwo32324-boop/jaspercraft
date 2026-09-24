/* JasperCraft video profiles. Engine-independent policy; no rendering loop or GPU probe. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory();else root.JasprVideoCore=factory();})(typeof globalThis==='object'?globalThis:this,function(){
  'use strict';
  var KEY='jaspr.video.v1';
  var fields={renderDistance:'ni',maxFps:'a3u',fancyGraphics:'u8',ao:'vH',particles:'QV',clouds:'Hz',entityShadows:'t8',vsync:'qQ',mipmaps:'wU',chunkUpdates:'ly',fog:'r3',viewBobbing:'IE',anaglyph:'mn',guiScale:'IO',gamma:'bCE',attackIndicator:'o4',skins:'jp',showFps:'sc',showCoords:'q0',chunkFix:'sf'};
  var defaults={renderDistance:8,maxFps:60,fancyGraphics:0,ao:1,particles:1,clouds:1,entityShadows:1,vsync:1,mipmaps:0,chunkUpdates:1,fog:1,viewBobbing:1,anaglyph:0,guiScale:0,gamma:.5,attackIndicator:1,skins:1,showFps:1,showCoords:1,chunkFix:1,
    resolution:100,chunkBudget:4,entityDistance:96,fastVisibility:1,fastBlockData:1,animations:1,ambientEffects:3,dynamicLights:1,gore:1,shader:0,mobileControls:1,touchSensitivity:100};
  var ranges={renderDistance:[2,16,1],maxFps:[10,260,1],fancyGraphics:[0,1,1],ao:[0,2,1],particles:[0,2,1],clouds:[0,2,1],entityShadows:[0,1,1],vsync:[0,1,1],mipmaps:[0,4,1],chunkUpdates:[1,5,1],fog:[0,1,1],viewBobbing:[0,1,1],anaglyph:[0,1,1],guiScale:[0,3,1],gamma:[0,1,.01],attackIndicator:[0,2,1],skins:[0,1,1],showFps:[0,1,1],showCoords:[0,1,1],chunkFix:[0,1,1],resolution:[50,100,5],chunkBudget:[1,12,1],entityDistance:[32,192,16],fastVisibility:[0,1,1],animations:[0,1,1],dynamicLights:[0,2,1],gore:[0,1,1],shader:[0,6,1],mobileControls:[0,1,1],touchSensitivity:[25,200,5]};
  // Append the new ID: existing saved presets must never change meaning.
  var names=['Hyper','Performance','Balanced','Quality','Ultra'],qualityOrder=[4,0,1,2,3];
  defaults.weatherEffects=1;defaults.particleEffects=1;
  ranges.weatherEffects=[0,1,1];ranges.particleEffects=[0,1,1];ranges.resolution=[35,100,5];
  ranges.fastBlockData=[0,1,1];
  ranges.ambientEffects=[0,3,1];
  var dhDefaults={dhEnabled:1,dhDistance:32,dhDetail:4,dhBudget:1,dhCache:64,dhFog:1,dhBrightness:100,dhSaturation:100,dhCaves:1,dhAdaptive:1};
  Object.assign(defaults,dhDefaults);
  Object.assign(ranges,{dhEnabled:[0,1,1],dhDistance:[8,64,8],dhDetail:[1,8,1],dhBudget:[.5,4,.5],dhCache:[16,256,16],dhFog:[0,1,1],dhBrightness:[50,150,5],dhSaturation:[50,150,5],dhCaves:[0,1,1],dhAdaptive:[0,1,1]});
  var overrides=[
    {renderDistance:3,maxFps:30,ao:0,particles:2,clouds:0,entityShadows:0,vsync:0,resolution:70,chunkBudget:2,entityDistance:48,animations:0,ambientEffects:1,dynamicLights:0,gore:0,dhEnabled:0,dhDistance:8,dhDetail:8,dhBudget:.5,dhCache:16},
    {renderDistance:4,maxFps:144,vsync:0,ao:0,particles:1,clouds:0,entityShadows:0,chunkBudget:2,entityDistance:64,ambientEffects:2,dynamicLights:0,dhDistance:16,dhDetail:8,dhBudget:.5,dhCache:32},
    {},
    {renderDistance:12,maxFps:90,fancyGraphics:1,ao:2,particles:0,clouds:2,chunkBudget:5,entityDistance:128,dhDistance:64,dhDetail:2,dhBudget:2,dhCache:128},
    {renderDistance:2,maxFps:60,ao:0,particles:2,clouds:0,entityShadows:0,vsync:0,resolution:50,chunkBudget:1,entityDistance:32,animations:0,ambientEffects:0,dynamicLights:0,gore:0,shader:0,weatherEffects:0,particleEffects:0,dhEnabled:0,dhDistance:8,dhDetail:8,dhBudget:.5,dhCache:16}
  ];
  function copy(x){return JSON.parse(JSON.stringify(x));}
  function clean(x){var r={};Object.keys(defaults).forEach(function(k){var v=x&&x[k],d=ranges[k];r[k]=typeof v==='number'&&isFinite(v)?+Math.max(d[0],Math.min(d[1],Math.round(v/d[2])*d[2])).toFixed(4):defaults[k];});if([1,2,4,8].indexOf(r.dhDetail)<0)r.dhDetail=4;return r;}
  function preset(tier){return clean(Object.assign({},defaults,overrides[tier]||{}));}
  function recommend(h){h=h||{};if(h.software||h.lowEndIntel)return 4;if(h.mobile||(h.cores>0&&h.cores<=2)||(h.memory>0&&h.memory<=2))return 0;
    if((h.cores>0&&h.cores<=4)||(h.memory>0&&h.memory<=4))return 1;
    return h.cores>=12&&h.memory>=8&&!h.integrated?3:2;}
  function create(storage,hardware,onChange){
    var state={version:1,tier:recommend(hardware),auto:true,values:null,saved:null},writeOK=true;
    try{var previous=JSON.parse(storage.getItem(KEY));if(previous&&previous.version===1){state.tier=previous.tier===-1?-1:Math.max(0,Math.min(4,previous.tier|0));state.auto=previous.auto===true;state.values=clean(previous.values);state.saved=previous.saved?clean(previous.saved):null;}}catch(_){}
    function devicePreset(tier){var p=preset(tier);if(hardware&&hardware.mobile)p.maxFps=Math.min(p.maxFps,60);return p;}
    if(!state.values)state.values=devicePreset(state.tier);
    // Migrate only the newly introduced field for an existing named preset.
    // Existing manual values and saved Custom presets retain full ambience.
    if(previous&&previous.values&&previous.values.ambientEffects===undefined&&state.tier>=0)state.values.ambientEffects=preset(state.tier).ambientEffects;
    if(previous&&previous.values)Object.keys(dhDefaults).forEach(function(k){if(previous.values[k]===undefined)state.values[k]=state.tier>=0?preset(state.tier)[k]:(k==='dhEnabled'?0:dhDefaults[k]);});
    if(previous&&previous.saved&&previous.saved.dhEnabled===undefined&&state.saved)state.saved.dhEnabled=0;
    // Apply the mobile-safe default once, including existing installations.
    // Persist the migration so subsequent deliberate manual choices survive.
    state.mobileDefaultVersion=previous&&previous.version===1&&previous.mobileDefaultVersion===1?1:0;
    if(hardware&&hardware.mobile&&state.mobileDefaultVersion<1){
      if(previous&&previous.version===1&&state.tier===-1&&!state.saved)state.saved=copy(state.values);
      state.tier=0;state.auto=true;state.values=devicePreset(0);state.mobileDefaultVersion=1;
    }
    // Reclassify only automatic installations. Never overwrite manual choices.
    if(state.auto&&state.tier>=0&&recommend(hardware)===4){state.tier=4;state.values=devicePreset(4);}
    var samples=new Float64Array(600),count=0,start=0,last=0,warmUntil=0,badWindows=0,goodWindows=0,lastChange=-Infinity,worldToken=null;
    var stats={fps:0,p95:0,p99:0,windows:0,reason:'hardware estimate',sampledFrames:0};
    function save(){try{storage.setItem(KEY,JSON.stringify(state));writeOK=true;}catch(_){writeOK=false;}}
    function changed(reason){stats.reason=reason;count=0;start=0;last=0;badWindows=goodWindows=0;save();if(onChange)onChange(copy(state.values));}
    function select(tier,automatic){state.tier=Math.max(0,Math.min(4,tier|0));state.auto=automatic===true;state.values=devicePreset(state.tier);changed(automatic?'automatic recommendation':'manual preset');}
    var api={
      status:function(){return Object.assign(copy(state),{name:state.tier<0?'Custom':names[state.tier],persistent:writeOK,metrics:copy(stats)});},
      value:function(key){return state.values[key];},
      select:select,
      adjust:function(key,value){if(!ranges[key])return;var next=Object.assign({},state.values);next[key]=value;state.values=clean(next);state.tier=-1;state.auto=false;changed('custom setting');},
      observe:function(values){var difference=false,next=clean(Object.assign({},state.values,values));Object.keys(values).forEach(function(k){if(ranges[k]&&state.values[k]!==next[k])difference=true;});if(!difference)return false;
        state.values=next;state.tier=-1;state.auto=false;save();stats.reason='custom setting';return true;},
      saveCustom:function(){state.saved=copy(state.values);save();return writeOK;},
      loadCustom:function(){if(!state.saved)return false;state.values=copy(state.saved);state.tier=-1;state.auto=false;changed('saved custom preset');return true;},
      reset:function(){select(recommend(hardware),true);stats.reason='reset to recommended defaults';},
      auto:function(enabled){if(enabled)select(recommend(hardware),true);else if(state.auto){state.auto=false;save();}},
      sample:function(now,active,token){
        if(token!==worldToken){worldToken=token;warmUntil=now+12000;last=0;count=0;start=0;badWindows=goodWindows=0;}
        if(!active||now<warmUntil){last=0;count=0;start=0;return;}
        if(!last){last=now;start=now;return;}
        var delta=now-last;last=now;
        // Hidden tabs / context loss are not evidence about device performance.
        if(delta<=0||delta>2000){count=0;start=now;return;}
        samples[count++]=delta;stats.sampledFrames++;
        if(now-start<8000&&count<samples.length)return;
        var ordered=Array.from(samples.subarray(0,count)).sort(function(a,b){return a-b;});
        stats.fps=+(count*1000/(now-start)).toFixed(1);stats.p95=+ordered[Math.floor((count-1)*.95)].toFixed(1);stats.p99=+ordered[Math.floor((count-1)*.99)].toFixed(1);stats.windows++;
        count=0;start=now;
        if(!state.auto||state.tier<0)return;
        var target=Math.min(state.values.maxFps,hardware&&hardware.mobile?30:60);
        var bad=stats.fps<target*.72&&stats.p95>1000/target*1.45;
        badWindows=bad?badWindows+1:0;
        goodWindows=!bad&&stats.fps>target*.94&&stats.p95<1000/target*1.25?goodWindows+1:0;
        if(now-lastChange<45000)return;
        var rank=qualityOrder.indexOf(state.tier),recommendedRank=qualityOrder.indexOf(recommend(hardware));
        if(badWindows>=3&&rank>0){lastChange=now;select(qualityOrder[rank-1],true);warmUntil=now+12000;}
        // Upgrades are conservative and never exceed the hardware estimate.
        else if(goodWindows>=8&&rank<recommendedRank){lastChange=now;select(qualityOrder[rank+1],true);warmUntil=now+12000;}
      }
    };
    save();return api;
  }
  // Allocation-free 16^3 connectivity walk, inspired by the primitive queues in
  // VintageFix/LoliASM VisGraph optimizations. No Java object/enum boxing in the loop.
  // Face bit order: down, up, north, south, west, east (Minecraft EnumFacing).
  var queue=new Uint16Array(4096);
  function visibility(words,seed){
    var head=0,tail=0,faces=0;queue[tail++]=seed;words[seed>>>5]|=1<<(seed&31);
    function visit(n){var w=n>>>5,m=1<<(n&31);if(!(words[w]&m)){words[w]|=m;queue[tail++]=n;}}
    while(head<tail){var p=queue[head++],x=p&15,z=(p>>>4)&15,y=p>>>8;
      if(x===0)faces|=16;else visit(p-1);if(x===15)faces|=32;else visit(p+1);
      if(y===0)faces|=1;else visit(p-256);if(y===15)faces|=2;else visit(p+256);
      if(z===0)faces|=4;else visit(p-16);if(z===15)faces|=8;else visit(p+16);
    }
    return faces;
  }
  // View the existing packed storage, never copy/cache decoded blocks. Mutations
  // remain immediately visible; handle entries straddling a 32/64-bit boundary.
  function packedRead32(words,bits,index,littleEndian){var start=index*bits,word=start>>>5,offset=start&31,swap=littleEndian?0:1,value=words[word^swap]>>>offset;
    if(offset+bits>32)value|=words[(word+1)^swap]<<(32-offset);return value&((1<<bits)-1);
  }
  return {KEY:KEY,fields:fields,defaults:defaults,ranges:ranges,names:names,preset:preset,clean:clean,recommend:recommend,create:create,visibility:visibility,packedRead32:packedRead32};
});
