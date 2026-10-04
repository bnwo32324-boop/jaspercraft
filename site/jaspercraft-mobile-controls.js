/* Touch input uses the existing Minecraft key bindings, inventory and commands. */
(function(){
 'use strict';
 var mobile=!!(navigator.maxTouchPoints&&(/Android|iPhone|iPad|iPod/.test(navigator.userAgent)||matchMedia('(pointer: coarse)').matches));
 if(!mobile)return;
 var host=null,canvas=null,slotBar=null,wideButton=null,wideLabel='',lastMode='',lastEnabled='',held=new Set(),pointers=new Map(),rightClick=false,shiftClick=false,sheet=null;
 // Tank mode (JasprTanks): the server advertises tanks; these controls claim one and drive it.
 var tank={state:'none',view:'first',vehicle:'',pickup:null,follow:false,unlockAt:0,claimed:false,polled:0,cooldownMs:1500,coolUntil:0},fireButton=null,tankButton=null,viewButton=null,modeButton=null,pickupButton=null,jumpButton=null,sneakButton=null,stickLabel=null,stickSprint=false;
 function bridge(){return window.JasprVideoMobileBridge;}
 // Control diagnostics (read by jaspercraft-mobile-diagnostics.js): counts, durations and states only, never
 // positions or typed text. Readable names for the engine's key-binding fields.
 var NAMES={bDc:'forward',bIW:'back',bPQ:'left',bZ5:'right',bOT:'sprint',bvG:'jump',b3c:'sneak','A$':'attack',Nc:'use',bUd:'swap',Hb:'inventory',bBx:'drop',$jasprStatsKey:'stats',$jasprWaypointKey:'waypoints'};
 function counters(){return {taps:0,canvasTouches:0,lookMoves:0,stickTouches:0,stickMs:0,buttons:{},cancels:0,lostCaptures:0,stuckReleases:0,followCancels:0,maxHoldMs:0,maxHoldKey:''};}
 var stats=counters(),activeTouches=0,heldSince={},stuckSince=0,last='',lastAt=0,anomalies=[],anomalyId=0,fingers=0,touchSeen=false;
 // Fingers actually on the screen, as the browser counts them (independent of the controls' own bookkeeping).
 ['touchstart','touchend','touchcancel'].forEach(function(t){document.addEventListener(t,function(e){touchSeen=true;fingers=e.touches?e.touches.length:0;},{capture:true,passive:true});});
 function noteControl(what){last=what;lastAt=performance.now();}
 function anomaly(kind,extra){if(anomalies.length>=20)anomalies.shift();var a={id:++anomalyId,kind:kind,at:Math.round(performance.now())};for(var k in extra)a[k]=extra[k];anomalies.push(a);}
 function name(field){return NAMES[field]||'other';}
 // A following sentinel flies itself; touching the stick or Up/Down hands control back to the driver.
 function takeControl(source){var now=performance.now();if(tank.vehicle!=='sentinel'||!tank.follow||now-tank.unlockAt<1500||!bridge())return;tank.unlockAt=now;stats.followCancels++;anomaly('follow-cancel',{source:source});bridge().text('/tank unlock '+source,true);}
 // Tank or Orbital Sentinel, as chosen on the Edit Profile screen (kept by the game client in this browser).
 function vehicleChoice(){try{return localStorage.getItem('jaspr.vehicle.v1')==='sentinel'?'sentinel':'tank';}catch(e){return 'tank';}}
 function state(){return bridge()?bridge().state():{ready:false,playing:false,menu:false,enabled:true,sensitivity:1};}
 function key(field,down){if(!bridge()||held.has(field)===!!down)return;var now=performance.now();if(down){held.add(field);heldSince[field]=now;}else{held.delete(field);var ms=now-(heldSince[field]||now);if(ms>stats.maxHoldMs){stats.maxHoldMs=Math.round(ms);stats.maxHoldKey=name(field);}delete heldSince[field];}bridge().key(field,down);}
 function release(){held.forEach(function(field){if(bridge())bridge().key(field,false);});held.clear();heldSince={};pointers.clear();stuckSince=0;stickSprint=false;if(canvas)mouse('mouseup',0,0,0);
  if(shiftClick){shiftClick=false;window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'ShiftLeft',key:'Shift',keyCode:16,which:16}));var shift=host&&host.querySelector('[data-zone="shift"]');if(shift)shift.textContent='Shift: OFF';}
 }
 function press(code,keyName,keyCode){['keydown','keyup'].forEach(function(type){window.dispatchEvent(new KeyboardEvent(type,{bubbles:true,cancelable:true,code:code,key:keyName,keyCode:keyCode,which:keyCode}));});}
 function mouse(type,x,y,button){if(canvas)canvas.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:button,buttons:type==='mouseup'?0:button===2?2:1}));}
 function pulse(field){key(field,true);setTimeout(function(){key(field,false);},90);}
 function fire(){var now=performance.now();if(tank.state!=='on'||now<tank.coolUntil)return;pulse('bUd');tank.coolUntil=now+tank.cooldownMs;
  fireButton.style.setProperty('--jaspr-cool',tank.cooldownMs+'ms');fireButton.classList.remove('jaspr-cooling');void fireButton.offsetWidth;fireButton.classList.add('jaspr-cooling');
  if(navigator.vibrate)try{navigator.vibrate(25);}catch(e){}}
 function tapAim(x,y){if(!canvas||!bridge())return;var r=canvas.getBoundingClientRect(),half=r.height/2,t=Math.tan(35*Math.PI/180),
   nx=(x-r.left-r.width/2)/half*t,ny=(y-r.top-half)/half*t,yaw=Math.atan(nx)*180/Math.PI,pitch=Math.atan(ny/Math.sqrt(1+nx*nx))*180/Math.PI;
  bridge().look(yaw,pitch);setTimeout(function(){pulse('A$');},80);}
 function syncTank(s){var b=bridge(),now=performance.now();if(!host||now-tank.polled<250)return;tank.polled=now;
  var t=b&&b.tank?b.tank():null,state=!t||!t.supported?'none':t.active?'on':'off';if(t&&t.cooldown>0)tank.cooldownMs=t.cooldown*50;
  var follow=!!(t&&t.follow);if(follow!==tank.follow){tank.follow=follow;anomaly(follow?'follow-start':'follow-end',{});}
  var vehicle=t&&t.mode==='sentinel'?'sentinel':'tank',pickup=!!(t&&t.pickup);
  if(vehicle!==tank.vehicle||pickup!==tank.pickup){tank.vehicle=vehicle;tank.pickup=pickup;host.dataset.vehicle=vehicle;var sky=vehicle==='sentinel';
   modeButton.textContent=sky?'Mode: Sentinel':'Mode: Tank';fireButton.textContent=sky?'STRIKE':'FIRE';jumpButton.textContent=sky?'Up':'Jump';sneakButton.textContent=sky?'Down':'Sneak';
   pickupButton.textContent=pickup?'Pickup: Auto':'Pickup: Tap';}
  // Claim a tank once per connection, as soon as the server advertises them.
  if(!t||!t.supported)tank.claimed=false;else if(!tank.claimed&&s.playing){tank.claimed=true;b.text('/tank mobile '+vehicleChoice(),true);}
  var view=t&&t.view>0?'third':'first';if(view!==tank.view){tank.view=view;host.dataset.view=view;viewButton.textContent=view==='third'?'View: 3rd':'View: 1st';}
  if(state===tank.state)return;tank.state=state;host.dataset.tank=state;tankButton.textContent=state==='on'?'Tank: ON':'Tank: OFF';stickLabel.textContent=state==='on'?'DRIVE':'MOVE';
  if(state!=='on'&&stickSprint){stickSprint=false;key('bOT',false);}}
 // Chat opens the game's own chat screen as well, so links in it (teleport ACCEPT / DENY) can be tapped.
 var chatScreen=false;
 function openChat(){if(state().playing){press('KeyT','t',84);chatScreen=true;}textSheet(true);}
 function textSheet(chat){
  release();if(sheet)sheet.remove();sheet=document.createElement('form');sheet.className='jaspr-touch-text';sheet.setAttribute('aria-label',chat?'Chat or command':'Type into selected game field');
  var input=document.createElement('input');input.type='text';input.maxLength=256;input.autocomplete='off';input.autocapitalize='off';input.spellcheck=false;input.placeholder=chat?'Message or /command (tap buttons in chat above)':'Text for selected field';input.setAttribute('aria-label',input.placeholder);
  var send=document.createElement('button');send.type='submit';send.textContent=chat?'Send':'Type';var cancel=document.createElement('button');cancel.type='button';cancel.textContent='Close';cancel.onclick=close;
  function close(){if(sheet)sheet.remove();sheet=null;if(chat&&chatScreen){chatScreen=false;if(state().menu)press('Escape','Escape',27);}if(canvas)canvas.focus();}
  sheet.append(input,send,cancel);sheet.onsubmit=function(e){e.preventDefault();if(bridge()&&input.value)bridge().text(input.value,chat);close();};document.body.append(sheet);input.focus();
 }
 function button(label,zone,action,hold){var b=document.createElement('button'),began=0,released=true,timer=0;b.type='button';b.textContent=label;b.setAttribute('aria-label',label);b.dataset.zone=zone;
  b.addEventListener('pointerdown',function(e){e.preventDefault();e.stopPropagation();clearTimeout(timer);began=performance.now();if(released)activeTouches++;released=false;b.setPointerCapture(e.pointerId);
   stats.buttons[zone]=(stats.buttons[zone]||0)+1;noteControl(zone);if(zone==='jump'||zone==='sneak')takeControl(zone);if(hold)key(hold,true);else action();});
  function up(e){e.preventDefault();if(released)return;released=true;activeTouches=Math.max(0,activeTouches-1);if(e.type==='pointercancel')stats.cancels++;else if(e.type==='lostpointercapture')stats.lostCaptures++;if(hold){var remaining=e.type==='pointerup'&&hold==='bvG'?90-(performance.now()-began):0;if(remaining>0)timer=setTimeout(function(){key(hold,false);},remaining);else key(hold,false);}}
  b.addEventListener('pointerup',up);b.addEventListener('pointercancel',up);b.addEventListener('lostpointercapture',up);host.append(b);return b;}
 function attach(){
  if(host)return;document.documentElement.classList.add('jaspr-touch-device');host=document.createElement('div');host.id='jaspr-touch';host.dataset.tank='none';host.dataset.view='first';host.dataset.vehicle='tank';host.setAttribute('aria-label','Minecraft touch controls');document.body.append(host);
  button('Pause','pause',function(){press('Escape','Escape',27);});button('Bag','bag',function(){pulse('Hb');});button('Chat','chat',openChat);
  jumpButton=button('Jump','jump',null,'bvG');button('Mine / Attack','mine',null,'A$');button('Use / Place','use',null,'Nc');sneakButton=button('Sneak','sneak',null,'b3c');button('Sprint','sprint',null,'bOT');
  button('Drop','drop',function(){pulse('bBx');});button('Swap','swap',function(){pulse('bUd');});button('Stats','stats',function(){pulse('$jasprStatsKey');});button('Waypoints','waypoints',function(){pulse('$jasprWaypointKey');});
  // The cannon fires on the swap-hands key; the server cancels the swap for tank drivers.
  fireButton=button('FIRE','fire',fire);tankButton=button('Tank','tank',function(){if(bridge())bridge().text('/tank',true);});
  viewButton=button('View','view',function(){var b=bridge();if(b&&b.tankView){b.tankView();tank.polled=0;}});
  // Tank or Orbital Sentinel; the sentinel picks items up by tap or automatically.
  modeButton=button('Mode','mode',function(){if(bridge())bridge().text('/tank mode',true);});
  pickupButton=button('Pickup','pickup',function(){if(bridge())bridge().text('/tank pickup',true);});
  // Vanilla draws no crosshair in third person; the camera sits on the aim line, so the screen centre is the aim.
  var reticle=document.createElement('i');reticle.className='jaspr-touch-reticle';reticle.setAttribute('aria-hidden','true');host.append(reticle);
  button('Back','back',function(){press('Escape','Escape',27);});button('Keyboard','keyboard',function(){textSheet(false);});
  // Wide inventory (owner, 2026-10-04): expand or contract the inventory windows; only while one is open (JasprWideBridge).
  wideButton=button('Contract','wideview',function(){var w=window.JasprWideBridge;if(w&&w.toggle){w.toggle();syncWide();}});
  var right=button('Right: OFF','right',function(){rightClick=!rightClick;right.textContent='Right: '+(rightClick?'ON':'OFF');});
  var shift=button('Shift: OFF','shift',function(){shiftClick=!shiftClick;shift.textContent='Shift: '+(shiftClick?'ON':'OFF');window.dispatchEvent(new KeyboardEvent(shiftClick?'keydown':'keyup',{bubbles:true,cancelable:true,code:'ShiftLeft',key:'Shift',keyCode:16,which:16}));});
  // Wide inventory: a 14-slot hotbar once the server agreed (JasprWideBridge in classes.js); slots 10-14 stay hidden until then.
  var bar=document.createElement('div');bar.className='jaspr-touch-slots';bar.dataset.slots='9';slotBar=bar;for(var i=0;i<14;i++)(function(slot){var b=document.createElement('button');b.textContent=String(slot+1);b.setAttribute('aria-label','Hotbar slot '+(slot+1));if(slot>=9)b.className='jaspr-wide';b.onpointerdown=function(e){e.preventDefault();var w=window.JasprWideBridge;if(w)w.select(slot);else if(bridge()&&slot<9)bridge().slot(slot);};bar.append(b);})(i);host.append(bar);
  var stick=document.createElement('div');stick.className='jaspr-touch-stick';stick.setAttribute('aria-label','Movement joystick');
  stickLabel=document.createElement('span');stickLabel.textContent='MOVE';var knob=document.createElement('i');knob.className='jaspr-touch-knob';stick.append(stickLabel,knob);
  function move(e){var r=stick.getBoundingClientRect(),dx=(e.clientX-r.left-r.width/2)/(r.width/2),dy=(e.clientY-r.top-r.height/2)/(r.height/2),m=Math.hypot(dx,dy);
   if(m>1){dx/=m;dy/=m;}var reach=r.width/2-knob.offsetWidth/2;knob.style.transform='translate('+(dx*reach).toFixed(1)+'px,'+(dy*reach).toFixed(1)+'px)';
   key('bDc',dy<-.23);key('bIW',dy>.23);key('bPQ',dx<-.23);key('bZ5',dx>.23);
   // Tanks sprint when the stick is pushed all the way forward.
   var sprint=tank.state==='on'&&dy<-.85&&Math.abs(dx)<.6;if(sprint!==stickSprint){stickSprint=sprint;key('bOT',sprint);}}
  var stickAt=0;
  stick.onpointerdown=function(e){e.preventDefault();stick.setPointerCapture(e.pointerId);if(!stickAt){stickAt=performance.now();activeTouches++;stats.stickTouches++;}noteControl('stick');takeControl('stick');stick.classList.add('jaspr-held');move(e);};stick.onpointermove=function(e){if(stick.hasPointerCapture(e.pointerId)){e.preventDefault();move(e);}};
  function end(e){if(stickAt){stats.stickMs+=Math.round(performance.now()-stickAt);stickAt=0;activeTouches=Math.max(0,activeTouches-1);if(e&&e.type==='pointercancel')stats.cancels++;}['bDc','bIW','bPQ','bZ5'].forEach(function(k){key(k,false);});if(stickSprint){stickSprint=false;key('bOT',false);}stick.classList.remove('jaspr-held');knob.style.transform='';}stick.onpointerup=end;stick.onpointercancel=end;stick.onlostpointercapture=end;host.append(stick);
  window.addEventListener('blur',release);window.addEventListener('pagehide',release);document.addEventListener('visibilitychange',release);window.addEventListener('orientationchange',release);
 }
 function attachCanvas(c){
  if(canvas===c)return;canvas=c;canvas.style.touchAction='none';canvas.tabIndex=0;
  canvas.addEventListener('pointerdown',function(e){if(e.pointerType!=='touch')return;e.preventDefault();canvas.setPointerCapture(e.pointerId);stats.canvasTouches++;var s=state();pointers.set(e.pointerId,{x:e.clientX,y:e.clientY,menu:s.menu,button:rightClick?2:0,at:performance.now(),moved:0});
    if(s.menu){mouse('mousemove',e.clientX,e.clientY,0);mouse('mousedown',e.clientX,e.clientY,rightClick?2:0);}
  });
  canvas.addEventListener('pointermove',function(e){var p=pointers.get(e.pointerId);if(!p)return;e.preventDefault();var dx=e.clientX-p.x,dy=e.clientY-p.y,s=state();
    if(p.menu){if(pointers.size>1)canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,clientX:e.clientX,clientY:e.clientY,deltaY:-dy*3}));else mouse('mousemove',e.clientX,e.clientY,p.button);}
    else if(s.playing&&bridge()){bridge().look(dx*.23*s.sensitivity,dy*.23*s.sensitivity);if(!p.looked){p.looked=1;stats.lookMoves++;noteControl('look');}}
    p.moved+=Math.abs(dx)+Math.abs(dy);p.x=e.clientX;p.y=e.clientY;
  });
  function end(e){var p=pointers.get(e.pointerId);if(!p)return;e.preventDefault();if(p.menu)mouse('mouseup',e.clientX,e.clientY,p.button);pointers.delete(e.pointerId);if(e.type==='pointercancel')stats.cancels++;
   if(e.type==='pointerup'&&!p.menu&&p.moved<12&&performance.now()-p.at<250&&tank.state==='on'&&tank.vehicle==='sentinel'){stats.taps++;noteControl('tap');tapAim(e.clientX,e.clientY);}}
  canvas.addEventListener('pointerup',end);canvas.addEventListener('pointercancel',end);canvas.addEventListener('lostpointercapture',end);
 }
 window.JasprMobile={sync:function(){if(!canvas||!canvas.isConnected){var c=document.querySelector('#game_frame canvas');if(!c)return;release();attach();attachCanvas(c);}var s=state(),mode=s.playing&&!sheet?'play':s.menu?'menu':'hidden',enabled=s.enabled?'true':'false';
   if(enabled!==lastEnabled){host.dataset.enabled=enabled;lastEnabled=enabled;}
   if(mode!==lastMode){release();host.dataset.mode=mode;document.documentElement.classList.toggle('jaspr-touch-menu',mode==='menu');lastMode=mode;}
   syncSlots();syncWide();syncTank(s);watchdog();
  },release:release,status:function(){return {mobile:mobile,mode:lastMode,held:Array.from(held),pointers:pointers.size,tank:tank.state,view:tank.view,vehicle:tank.vehicle,pickup:tank.pickup,follow:tank.follow,claimed:tank.claimed,cooldownMs:tank.cooldownMs};},
  // Control counters since the last reset, held keys by name, and control anomalies newer than `after`.
  stats:function(reset,after){var out=stats,now=performance.now();out.activeTouches=activeTouches;out.heldNow=Array.from(held).map(name);out.last=last;out.sinceLastMs=lastAt?Math.round(now-lastAt):null;
   out.anomalies=anomalies.filter(function(a){return a.id>(after|0);});if(reset){stats=counters();}return out;}};
 // 9 or 14 hotbar buttons, as many as the game's hotbar has.
 function syncSlots(){if(!slotBar)return;var w=window.JasprWideBridge,n=w?w.slots():9;n=n===14?'14':'9';if(slotBar.dataset.slots!==n)slotBar.dataset.slots=n;}
 // The Expand / Contract button: shown in a menu over an inventory window; Expand counts the stacks the compact view hides.
 function syncWide(){if(!host||!wideButton)return;var w=window.JasprWideBridge,v=w&&w.view?w.view():null,can=v&&v.can?'1':'0';
  if(host.dataset.wideview!==can)host.dataset.wideview=can;if(!v)return;
  var label=v.expanded?'Contract':'Expand'+(v.hidden?' (+'+v.hidden+')':'');if(label!==wideLabel){wideLabel=label;wideButton.textContent=label;wideButton.setAttribute('aria-label',v.expanded?'Contract the inventory':'Expand the inventory');}}
 // A key still held 2 s after the last finger left the screen is stuck (a touch end the controls never got): release it.
 function watchdog(){if(!touchSeen||!held.size||fingers>0){stuckSince=0;return;}var now=performance.now();if(!stuckSince){stuckSince=now;return;}
  if(now-stuckSince<2000)return;var keys=Array.from(held).map(name);stats.stuckReleases++;anomaly('stuck-release',{keys:keys.slice(0,6)});release();}
 // Small bounded bootstrap; after initialization the engine owns updates.
 var attempts=0,timer=setInterval(function(){window.JasprMobile.sync();if(bridge()||++attempts>120)clearInterval(timer);},250);
})();
