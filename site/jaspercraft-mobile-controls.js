/* Touch input uses the existing Minecraft key bindings, inventory and commands. */
(function(){
 'use strict';
 var mobile=!!(navigator.maxTouchPoints&&(/Android|iPhone|iPad|iPod/.test(navigator.userAgent)||matchMedia('(pointer: coarse)').matches));
 if(!mobile)return;
 var host=null,canvas=null,lastMode='',lastEnabled='',held=new Set(),pointers=new Map(),rightClick=false,shiftClick=false,sheet=null;
 function bridge(){return window.JasprVideoMobileBridge;}
 function state(){return bridge()?bridge().state():{ready:false,playing:false,menu:false,enabled:true,sensitivity:1};}
 function key(field,down){if(!bridge()||held.has(field)===!!down)return;if(down)held.add(field);else held.delete(field);bridge().key(field,down);}
 function release(){held.forEach(function(field){if(bridge())bridge().key(field,false);});held.clear();pointers.clear();if(canvas)mouse('mouseup',0,0,0);
  if(shiftClick){shiftClick=false;window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'ShiftLeft',key:'Shift',keyCode:16,which:16}));var shift=host&&host.querySelector('[data-zone="shift"]');if(shift)shift.textContent='Shift: OFF';}
 }
 function press(code,keyName,keyCode){['keydown','keyup'].forEach(function(type){window.dispatchEvent(new KeyboardEvent(type,{bubbles:true,cancelable:true,code:code,key:keyName,keyCode:keyCode,which:keyCode}));});}
 function mouse(type,x,y,button){if(canvas)canvas.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:button,buttons:type==='mouseup'?0:button===2?2:1}));}
 function pulse(field){key(field,true);setTimeout(function(){key(field,false);},90);}
 function textSheet(chat){
  release();if(sheet)sheet.remove();sheet=document.createElement('form');sheet.className='jaspr-touch-text';sheet.setAttribute('aria-label',chat?'Chat or command':'Type into selected game field');
  var input=document.createElement('input');input.type='text';input.maxLength=256;input.autocomplete='off';input.autocapitalize='off';input.spellcheck=false;input.placeholder=chat?'Message or /command':'Text for selected field';input.setAttribute('aria-label',input.placeholder);
  var send=document.createElement('button');send.type='submit';send.textContent=chat?'Send':'Type';var cancel=document.createElement('button');cancel.type='button';cancel.textContent='Close';cancel.onclick=close;
  function close(){if(sheet)sheet.remove();sheet=null;if(canvas)canvas.focus();}
  sheet.append(input,send,cancel);sheet.onsubmit=function(e){e.preventDefault();if(bridge()&&input.value)bridge().text(input.value,chat);close();};document.body.append(sheet);input.focus();
 }
 function button(label,zone,action,hold){var b=document.createElement('button'),began=0,released=true,timer=0;b.type='button';b.textContent=label;b.setAttribute('aria-label',label);b.dataset.zone=zone;
  b.addEventListener('pointerdown',function(e){e.preventDefault();e.stopPropagation();clearTimeout(timer);began=performance.now();released=false;b.setPointerCapture(e.pointerId);if(hold)key(hold,true);else action();});
  function up(e){e.preventDefault();if(released)return;released=true;if(hold){var remaining=e.type==='pointerup'&&hold==='bvG'?90-(performance.now()-began):0;if(remaining>0)timer=setTimeout(function(){key(hold,false);},remaining);else key(hold,false);}}
  b.addEventListener('pointerup',up);b.addEventListener('pointercancel',up);b.addEventListener('lostpointercapture',up);host.append(b);return b;}
 function attach(){
  if(host)return;document.documentElement.classList.add('jaspr-touch-device');host=document.createElement('div');host.id='jaspr-touch';host.setAttribute('aria-label','Minecraft touch controls');document.body.append(host);
  button('Pause','pause',function(){press('Escape','Escape',27);});button('Bag','bag',function(){pulse('Hb');});button('Chat','chat',function(){textSheet(true);});
  button('Jump','jump',null,'bvG');button('Mine / Attack','mine',null,'A$');button('Use / Place','use',null,'Nc');button('Sneak','sneak',null,'b3c');button('Sprint','sprint',null,'bOT');
  button('Drop','drop',function(){pulse('bBx');});button('Swap','swap',function(){pulse('bUd');});button('Stats','stats',function(){pulse('$jasprStatsKey');});button('Waypoints','waypoints',function(){pulse('$jasprWaypointKey');});
  button('Back','back',function(){press('Escape','Escape',27);});button('Keyboard','keyboard',function(){textSheet(false);});
  var right=button('Right: OFF','right',function(){rightClick=!rightClick;right.textContent='Right: '+(rightClick?'ON':'OFF');});
  var shift=button('Shift: OFF','shift',function(){shiftClick=!shiftClick;shift.textContent='Shift: '+(shiftClick?'ON':'OFF');window.dispatchEvent(new KeyboardEvent(shiftClick?'keydown':'keyup',{bubbles:true,cancelable:true,code:'ShiftLeft',key:'Shift',keyCode:16,which:16}));});
  var bar=document.createElement('div');bar.className='jaspr-touch-slots';for(var i=0;i<9;i++)(function(slot){var b=document.createElement('button');b.textContent=String(slot+1);b.setAttribute('aria-label','Hotbar slot '+(slot+1));b.onpointerdown=function(e){e.preventDefault();if(bridge())bridge().slot(slot);};bar.append(b);})(i);host.append(bar);
  var stick=document.createElement('div');stick.className='jaspr-touch-stick';stick.setAttribute('aria-label','Movement joystick');stick.textContent='MOVE';
  function move(e){var r=stick.getBoundingClientRect(),dx=(e.clientX-r.left-r.width/2)/(r.width/2),dy=(e.clientY-r.top-r.height/2)/(r.height/2);key('bDc',dy<-.23);key('bIW',dy>.23);key('bPQ',dx<-.23);key('bZ5',dx>.23);}
  stick.onpointerdown=function(e){e.preventDefault();stick.setPointerCapture(e.pointerId);move(e);};stick.onpointermove=function(e){if(stick.hasPointerCapture(e.pointerId)){e.preventDefault();move(e);}};
  function end(){['bDc','bIW','bPQ','bZ5'].forEach(function(k){key(k,false);});}stick.onpointerup=end;stick.onpointercancel=end;stick.onlostpointercapture=end;host.append(stick);
  window.addEventListener('blur',release);window.addEventListener('pagehide',release);document.addEventListener('visibilitychange',release);window.addEventListener('orientationchange',release);
 }
 function attachCanvas(c){
  if(canvas===c)return;canvas=c;canvas.style.touchAction='none';canvas.tabIndex=0;
  canvas.addEventListener('pointerdown',function(e){if(e.pointerType!=='touch')return;e.preventDefault();canvas.setPointerCapture(e.pointerId);var s=state();pointers.set(e.pointerId,{x:e.clientX,y:e.clientY,menu:s.menu,button:rightClick?2:0});
    if(s.menu){mouse('mousemove',e.clientX,e.clientY,0);mouse('mousedown',e.clientX,e.clientY,rightClick?2:0);}
  });
  canvas.addEventListener('pointermove',function(e){var p=pointers.get(e.pointerId);if(!p)return;e.preventDefault();var dx=e.clientX-p.x,dy=e.clientY-p.y,s=state();
    if(p.menu){if(pointers.size>1)canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,clientX:e.clientX,clientY:e.clientY,deltaY:-dy*3}));else mouse('mousemove',e.clientX,e.clientY,p.button);}
    else if(s.playing&&bridge())bridge().look(dx*.23*s.sensitivity,dy*.23*s.sensitivity);
    p.x=e.clientX;p.y=e.clientY;
  });
  function end(e){var p=pointers.get(e.pointerId);if(!p)return;e.preventDefault();if(p.menu)mouse('mouseup',e.clientX,e.clientY,p.button);pointers.delete(e.pointerId);}
  canvas.addEventListener('pointerup',end);canvas.addEventListener('pointercancel',end);canvas.addEventListener('lostpointercapture',end);
 }
 window.JasprMobile={sync:function(){if(!canvas||!canvas.isConnected){var c=document.querySelector('#game_frame canvas');if(!c)return;release();attach();attachCanvas(c);}var s=state(),mode=s.playing&&!sheet?'play':s.menu?'menu':'hidden',enabled=s.enabled?'true':'false';
   if(enabled!==lastEnabled){host.dataset.enabled=enabled;lastEnabled=enabled;}
   if(mode!==lastMode){release();host.dataset.mode=mode;document.documentElement.classList.toggle('jaspr-touch-menu',mode==='menu');lastMode=mode;}
  },release:release,status:function(){return {mobile:mobile,mode:lastMode,held:Array.from(held),pointers:pointers.size};}};
 // Small bounded bootstrap; after initialization the engine owns updates.
 var attempts=0,timer=setInterval(function(){window.JasprMobile.sync();if(bridge()||++attempts>120)clearInterval(timer);},250);
})();
