/* Bounded, same-origin mobile/low-end health reports to the existing diagnostics route.
 * No account names, chat, coordinates, input events, or credentials in payloads.
 * No rendering loop, GPU queries, persistent storage, retries or background work.
 */
(function(){
 'use strict';
 if(location.pathname.indexOf('/jaspercraft/')!==0||typeof fetch!=='function')return;
 var attempts=0,sent=0,previous=null,stopped=false,timer=0,pageId='video-'+Date.now().toString(36);
 function sample(){
  if(stopped||document.hidden)return;
  var diag=window.JasprVideoDiagnostics;
  if(!diag){if(++attempts<30)timer=setTimeout(sample,3000);return;}
  var s=diag.status();if(!s.hardware.mobile&&!s.hardware.lowEndIntel&&!s.hardware.software&&s.tier!==4){if(++attempts<30)timer=setTimeout(sample,15000);return;}
  var h=s.health,v=s.values,frames=previous&&h.frames>=previous.frames?h.frames-previous.frames:0,ms=previous&&h.activeMs>=previous.activeMs?h.activeMs-previous.activeMs:0;
  previous={frames:h.frames,activeMs:h.activeMs};
  var details={build:'20260920-ultra1',tier:s.name,auto:s.auto,hardware:s.hardware,dpr:window.devicePixelRatio||1,loadedInMs:s.loadedInMs,
   fps:ms>0?Math.round(frames*10000/ms)/10:0,sampledMs:ms,health:h,
   settings:{maxFps:v.maxFps,renderDistance:v.renderDistance,resolution:v.resolution,ambientEffects:v.ambientEffects,shader:v.shader,gore:v.gore,chunkBudget:v.chunkBudget},
   effectiveResolution:s.scaler.effectiveResolution,scalerError:s.scaler.error||''};
  try{fetch('/api/diagnostics/events',{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json','X-Jaspergers-Client':'web-v1'},body:JSON.stringify({events:[{event:'jaspercraft.video.health',pageSessionId:pageId,at:new Date().toISOString(),details:details}]})}).catch(function(){});}catch(_){}
  if(++sent<12)timer=setTimeout(sample,15000);
 }
 document.addEventListener('visibilitychange',function(){clearTimeout(timer);previous=null;if(!document.hidden&&!stopped&&sent<12)timer=setTimeout(sample,3000);});
 window.addEventListener('pagehide',function(){stopped=true;clearTimeout(timer);});
 timer=setTimeout(sample,5000);
})();
