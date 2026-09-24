/* Event-driven mobile viewport sizing. Never reloads the game or locks orientation. */
(function(){
 'use strict';
 var mobile=!!(navigator.maxTouchPoints&&(/Android|iPhone|iPad|iPod/.test(navigator.userAgent||'')||matchMedia('(pointer: coarse)').matches));
 if(!mobile)return;
 var root=document.documentElement,host=document.getElementById('game-host'),role=host?'host':'client',width=0,height=0,queued=0,settle=[],sent=0,resizeReports=0,lastReport=0,lastAction='',parentSize=null;
 var pageId='viewport-'+role+'-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8);
 root.classList.add('jaspr-mobile-viewport');
 if(!host)root.classList.add('jaspr-mobile-client');
 function status(){var vv=window.visualViewport,o=screen.orientation;return {role:role,width:width,height:height,innerWidth:window.innerWidth,innerHeight:window.innerHeight,visualWidth:vv?Math.round(vv.width):0,visualHeight:vv?Math.round(vv.height):0,angle:o&&typeof o.angle==='number'?o.angle:typeof window.orientation==='number'?window.orientation:null,hidden:document.hidden,online:navigator.onLine!==false,lastAction:lastAction};}
 function report(event,extra,ending){
  if(sent>=24||location.pathname.indexOf('/jaspercraft/')!==0||typeof fetch!=='function')return;
  if(event==='jaspercraft.viewport.changed'){if(resizeReports>=12)return;resizeReports++;}
  sent++;var details=Object.assign(status(),extra||{}),body=JSON.stringify({events:[{event:event,at:new Date().toISOString(),pageSessionId:pageId,details:details}]});
  try{fetch('/api/diagnostics/events',{method:'POST',credentials:'same-origin',cache:'no-store',keepalive:!!ending,headers:{'Content-Type':'application/json','X-Jaspergers-Client':'web-v1'},body:body}).catch(function(){});}catch(_){}
 }
 function notifyFrame(){if(!host)return;var frame=host.querySelector('iframe');if(frame&&frame.contentWindow){var r=frame.getBoundingClientRect();if(r.width>0&&r.height>0)frame.contentWindow.postMessage({type:'jaspr-mobile-viewport',width:Math.round(r.width),height:Math.round(r.height)},location.origin);}}
 function apply(){
  queued=0;if(document.hidden)return;
  var vv=window.visualViewport,w=window.innerWidth,h=window.innerHeight;
  if(vv&&Math.abs((vv.scale||1)-1)<.02){w=vv.width||w;h=vv.height||h;}
  if(parentSize&&window.parent!==window){w=parentSize.width;h=parentSize.height;}
  w=Math.max(1,Math.round(w));h=Math.max(1,Math.round(h));
  if(w!==width||h!==height){
   width=w;height=h;root.style.setProperty('--jaspr-viewport-width',w+'px');root.style.setProperty('--jaspr-viewport-height',h+'px');
   root.dataset.jasprOrientation=w>h?'landscape':'portrait';
   if(window.JasprMobile)window.JasprMobile.release();
   var now=Date.now();if(now-lastReport>1500){lastReport=now;report('jaspercraft.viewport.changed');}
  }
  notifyFrame();
 }
 function schedule(){if(!queued)queued=setTimeout(apply,0);}
 function rotation(){if(window.JasprMobile)window.JasprMobile.release();parentSize=null;settle.forEach(clearTimeout);schedule();settle=[100,350,1000].map(function(ms){return setTimeout(schedule,ms);});}
 window.addEventListener('resize',function(){parentSize=null;schedule();});
 window.addEventListener('orientationchange',rotation);
 if(screen.orientation&&screen.orientation.addEventListener)screen.orientation.addEventListener('change',rotation);
 if(window.visualViewport)window.visualViewport.addEventListener('resize',schedule);
 window.addEventListener('message',function(e){var d=e.data;if(role!=='client'||e.source!==window.parent||e.origin!==location.origin||!d||d.type!=='jaspr-mobile-viewport')return;if(!Number.isFinite(d.width)||!Number.isFinite(d.height)||d.width<1||d.height<1||d.width>32768||d.height>32768)return;parentSize={width:d.width,height:d.height};schedule();});
 window.addEventListener('pageshow',function(e){rotation();report('jaspercraft.page.lifecycle',{event:'pageshow',persisted:!!e.persisted});});
 window.addEventListener('pagehide',function(e){clearTimeout(queued);queued=0;settle.forEach(clearTimeout);report('jaspercraft.page.lifecycle',{event:'pagehide',persisted:!!e.persisted},true);});
 document.addEventListener('visibilitychange',function(){report('jaspercraft.page.lifecycle',{event:'visibilitychange'},document.hidden);if(!document.hidden)rotation();});
 window.addEventListener('offline',function(){report('jaspercraft.page.lifecycle',{event:'offline'});});
 window.addEventListener('online',function(){report('jaspercraft.page.lifecycle',{event:'online'});});
 document.addEventListener('webglcontextlost',function(){report('jaspercraft.page.lifecycle',{event:'webglcontextlost'});},true);
 document.addEventListener('webglcontextrestored',function(){report('jaspercraft.page.lifecycle',{event:'webglcontextrestored'});},true);
 if(host){
  var back=document.getElementById('back-chat');if(back)back.addEventListener('click',function(){lastAction='back-to-chat';report('jaspercraft.page.lifecycle',{event:'back-to-chat'});},true);
  if(typeof ResizeObserver==='function'){var observer=new ResizeObserver(schedule);observer.observe(host);}
  host.addEventListener('load',schedule,true);
 }
 window.JasprMobileViewport={status:status};schedule();
})();
