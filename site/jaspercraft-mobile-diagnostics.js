/* Phone diagnostics (owner request 2026-09-29): performance and touch controls of mobile players, bounded and
 * same-origin, through the existing /api/diagnostics/events route (Jaspr.chat logs them as client.jaspercraft.mobile.*).
 *  - jaspercraft.mobile.session   once: device class, screen, browser support for frame attribution.
 *  - jaspercraft.mobile.perf      every 30 s while visible: fps, long frames by size, main-thread busy time,
 *                                 heap, network class, video settings.
 *  - jaspercraft.mobile.controls  every 30 s while visible: control counters (taps, stick, buttons, cancels,
 *                                 stuck-key releases, follow cancels), held keys, vehicle state.
 *  - jaspercraft.mobile.stall     right after the page stalled 1 s or more: duration, the browser's own attribution
 *                                 (entry point, script vs rendering time) and what the player was doing.
 *  - jaspercraft.mobile.control_anomaly  a stuck key released, a sentinel follow started, ended or cancelled.
 * Never account names, chat or typed text, coordinates, device identifiers, network addresses or credentials:
 * counts, durations, states and settings only. At most 240 periodic batches, 30 stall reports and 60 anomalies
 * per page; one request per batch; no retries, no storage. */
(function(){
 'use strict';
 var mobile=!!(navigator.maxTouchPoints&&(/Android|iPhone|iPad|iPod/.test(navigator.userAgent)||(window.matchMedia&&matchMedia('(pointer: coarse)').matches)));
 if(!mobile||typeof fetch!=='function'||location.pathname.indexOf('/jaspercraft/')!==0)return;
 var BUILD='20260929-mobile1',PERIOD=30000,MAX_BATCHES=240,MAX_STALLS=30,MAX_ANOMALIES=60,STALL_MS=1000,STALL_GAP_MS=10000,LIMIT=1900;
 var pageId='mobile-'+Date.now().toString(36)+'-'+Math.floor(Math.random()*1e6).toString(36);
 var batches=0,stalls=0,anomaliesSent=0,lastStallAt=-1e9,lastAnomaly=0,observer='none',stopped=false,periodic=0,beat=0,lastBeat=0,prevHealth=null,sessionSent=false;
 function fresh(){return {long50:0,long100:0,long250:0,long1000:0,worstMs:0,busyMs:0,scriptMs:0,renderMs:0,stalls:0,beatStalls:0,since:performance.now()};}
 var win=fresh();
 function now(){return performance.now();}
 function round(v,d){var m=Math.pow(10,d||0);return Math.round(v*m)/m;}
 function heap(){var m=performance.memory;return m&&m.usedJSHeapSize?{usedMb:round(m.usedJSHeapSize/1048576,1),limitMb:round(m.jsHeapSizeLimit/1048576)}:null;}
 function base(u){u=String(u||'');var q=u.search(/[?#]/);if(q>=0)u=u.slice(0,q);return u.slice(u.lastIndexOf('/')+1).slice(0,48);}
 function platform(){var ua=navigator.userAgent,m;
  var os=/Android/.test(ua)?'android':/iPhone|iPad|iPod/.test(ua)?'ios':/Macintosh/.test(ua)?'ipados':'other';
  var br=(m=ua.match(/(?:Chrome|CriOS)\/(\d+)/))?'chrome '+m[1]:(m=ua.match(/Firefox\/(\d+)/))?'firefox '+m[1]:(m=ua.match(/Version\/(\d+).*Safari/))?'safari '+m[1]:'other';
  return {os:os,browser:br};}
 function net(){var c=navigator.connection;return c?{type:String(c.effectiveType||'').slice(0,8),rttMs:c.rtt|0,downMbps:round(c.downlink||0,1),saveData:!!c.saveData}:null;}
 function screenInfo(){var o=screen.orientation;return {w:innerWidth|0,h:innerHeight|0,dpr:round(window.devicePixelRatio||1,2),angle:o?o.angle|0:(window.orientation|0)};}
 function bridgeState(){var b=window.JasprVideoMobileBridge,s=null,t=null;try{s=b&&b.state();}catch(e){}try{t=b&&b.tank&&b.tank();}catch(e){}
  return {playing:!!(s&&s.playing),menu:!!(s&&s.menu),controls:s?!!s.enabled:null,tank:t&&t.supported?{on:!!t.active,mode:t.mode,follow:!!t.follow,pickup:!!t.pickup}:null};}
 function controls(reset){var m=window.JasprMobile;try{return m&&m.stats?m.stats(reset,lastAnomaly):null;}catch(e){return null;}}
 function fit(d,drop){var s=JSON.stringify(d);for(var i=0;s.length>LIMIT&&i<drop.length;i++){delete d[drop[i]];d.trimmed=(d.trimmed||0)+1;s=JSON.stringify(d);}return s.length>LIMIT?{trimmed:true,build:BUILD}:d;}
 function ev(name,details){return {event:name,pageSessionId:pageId,at:new Date().toISOString(),details:details};}
 function send(events,ending){if(!events.length)return;
  try{fetch('/api/diagnostics/events',{method:'POST',credentials:'same-origin',cache:'no-store',keepalive:!!ending,headers:{'Content-Type':'application/json','X-Jaspergers-Client':'web-v1'},body:JSON.stringify({events:events.slice(0,25)})}).catch(function(){});}catch(e){}}

 // What the player was doing: state, held keys, last control and how long ago, vehicle.
 function context(){var c=controls(false),b=bridgeState();
  return {playing:b.playing,menu:b.menu,hidden:document.hidden,tank:b.tank,heldNow:c?c.heldNow:null,touches:c?c.activeTouches:null,
   lastControl:c?c.last:null,sinceControlMs:c?c.sinceLastMs:null,heap:heap()};}

 function stall(ms,how,extra){win.stalls++;if(stalls>=MAX_STALLS||batches>=MAX_BATCHES)return;var t=now();if(t-lastStallAt<STALL_GAP_MS)return;lastStallAt=t;stalls++;
  var d={build:BUILD,ms:Math.round(ms),how:how,n:stalls};for(var k in extra)d[k]=extra[k];d.context=context();
  send([ev('jaspercraft.mobile.stall',fit(d,['scripts','context']))]);}

 // Browser frame attribution: Long Animation Frames (Chrome 123+) name the entry point and split script from
 // rendering time; Long Tasks give the duration only; a 1 s heartbeat covers browsers with neither.
 function bucket(ms){if(ms>=50)win.long50++;if(ms>=100)win.long100++;if(ms>=250)win.long250++;if(ms>=1000)win.long1000++;if(ms>win.worstMs)win.worstMs=Math.round(ms);win.busyMs+=ms;}
 function scriptsOf(list){return (list||[]).slice().sort(function(a,b){return b.duration-a.duration;}).slice(0,3).map(function(s){
  return {ms:Math.round(s.duration),type:String(s.invokerType||'').slice(0,24),invoker:base(s.invoker).slice(0,48),fn:String(s.sourceFunctionName||'').slice(0,40),file:base(s.sourceURL),pos:s.sourceCharPosition|0,layoutMs:Math.round(s.forcedStyleAndLayoutDuration||0)};});}
 function frame(e){var ms=e.duration,script=0;bucket(ms);(e.scripts||[]).forEach(function(s){script+=s.duration||0;});win.scriptMs+=script;
  var render=e.renderStart?Math.max(0,e.startTime+ms-e.renderStart):0;win.renderMs+=render;
  if(ms>=STALL_MS)stall(ms,'frame',{scriptMs:Math.round(script),renderMs:Math.round(render),blockingMs:Math.round(e.blockingDuration||0),scripts:scriptsOf(e.scripts)});}
 function task(e){bucket(e.duration);if(e.duration>=STALL_MS)stall(e.duration,'task',{});}
 try{var types=(window.PerformanceObserver&&PerformanceObserver.supportedEntryTypes)||[];
  if(types.indexOf('long-animation-frame')>=0){new PerformanceObserver(function(l){l.getEntries().forEach(frame);}).observe({type:'long-animation-frame'});observer='loaf';}
  else if(types.indexOf('longtask')>=0){new PerformanceObserver(function(l){l.getEntries().forEach(task);}).observe({type:'longtask'});observer='longtask';}
 }catch(e){observer='error';}
 function heartbeat(){var t=now();
  if(lastBeat&&!document.hidden){var gap=t-lastBeat-1000;if(gap>=STALL_MS*1.5){win.beatStalls++;if(observer==='none'||observer==='error')stall(gap,'heartbeat',{});}}
  lastBeat=document.hidden?0:t;anomalies();}
 function anomalies(){var c=controls(false);if(!c||!c.anomalies||!c.anomalies.length)return;var out=[];
  c.anomalies.forEach(function(a){lastAnomaly=Math.max(lastAnomaly,a.id);if(anomaliesSent>=MAX_ANOMALIES)return;anomaliesSent++;
   var d={build:BUILD,kind:String(a.kind).slice(0,24),source:a.source?String(a.source).slice(0,16):undefined,keys:a.keys,agoMs:Math.round(now()-a.at),context:context()};out.push(ev('jaspercraft.mobile.control_anomaly',fit(d,['context'])));});
  send(out);}

 function sample(ending){if(stopped||batches>=MAX_BATCHES)return;var t=now(),ms=t-win.since;if(ms<1000)return;
  var v=window.JasprVideoDiagnostics,s=null;try{s=v&&v.status();}catch(e){}
  var h=s&&s.health,fps=0;if(h&&prevHealth&&h.frames>=prevHealth.frames&&h.activeMs>prevHealth.activeMs)fps=round((h.frames-prevHealth.frames)*1000/(h.activeMs-prevHealth.activeMs),1);
  if(h)prevHealth={frames:h.frames,activeMs:h.activeMs};
  var b=bridgeState(),c=controls(true),vals=s&&s.values||{};
  var perf={build:BUILD,sampledMs:Math.round(ms),playing:b.playing,menu:b.menu,fps:fps,worstFrameMs:win.worstMs,
   long:{over50:win.long50,over100:win.long100,over250:win.long250,over1000:win.long1000},busyPct:round(win.busyMs*100/ms,1),scriptMs:Math.round(win.scriptMs),renderMs:Math.round(win.renderMs),
   stalls:win.stalls,beatStalls:win.beatStalls,heap:heap(),net:net(),screen:screenInfo(),tier:s?s.name:null,
   settings:{maxFps:vals.maxFps,renderDistance:vals.renderDistance,resolution:vals.resolution,shader:vals.shader,chunkBudget:vals.chunkBudget,effectiveResolution:s&&s.scaler?s.scaler.effectiveResolution:null}};
  var events=[ev('jaspercraft.mobile.perf',fit(perf,['settings','net','screen']))];
  if(c){var ctl={build:BUILD,sampledMs:Math.round(ms),taps:c.taps,canvasTouches:c.canvasTouches,lookMoves:c.lookMoves,stickTouches:c.stickTouches,stickMs:c.stickMs,buttons:c.buttons,
    cancels:c.cancels,lostCaptures:c.lostCaptures,stuckReleases:c.stuckReleases,followCancels:c.followCancels,maxHoldMs:c.maxHoldMs,maxHoldKey:c.maxHoldKey,heldNow:c.heldNow,touches:c.activeTouches,
    controls:b.controls,tank:b.tank};
   events.push(ev('jaspercraft.mobile.controls',fit(ctl,['buttons'])));}
  win=fresh();batches++;send(events,ending);}

 function session(){if(sessionSent)return;sessionSent=true;var p=platform();
  send([ev('jaspercraft.mobile.session',fit({build:BUILD,os:p.os,browser:p.browser,cores:navigator.hardwareConcurrency|0,memoryGb:navigator.deviceMemory||null,touchPoints:navigator.maxTouchPoints|0,
   screen:screenInfo(),observer:observer,heap:heap(),net:net()},['net']))]);}
 function schedule(){clearTimeout(periodic);if(!stopped&&!document.hidden&&batches<MAX_BATCHES)periodic=setTimeout(function(){sample(false);schedule();},PERIOD);}
 document.addEventListener('visibilitychange',function(){lastBeat=0;if(document.hidden)sample(true);else win=fresh();schedule();});
 window.addEventListener('pagehide',function(){sample(true);stopped=true;clearTimeout(periodic);clearInterval(beat);});
 beat=setInterval(heartbeat,1000);
 setTimeout(session,5000);schedule();
 window.JasprMobileDiagnostics={status:function(){return {build:BUILD,observer:observer,batches:batches,stalls:stalls,anomalies:anomaliesSent,window:win};}};
})();
