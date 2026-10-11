/* JASPR_BETTERCOMBAT_V1_BEGIN */
/* Better Combat Rebirth 1.5.6 browser adapter (client build bettercombat9). Server-authoritative, connection scoped.
 * Uses the existing TeaVM hand animations and real world/entity ray collision code.
 * Plain JS never invokes a potentially suspending TeaVM method.
 * Source: client-mods/bettercombat/bettercombat-head.js; builder scripts/build-bettercombat-client.cjs replaces only
 * this head (up to the nested first-person melee module) and keeps the engine-derived functions at its end verbatim.
 */
// The combat feed (combat rework contract, workstream A): the last <= 64 attack events, oldest first, append-only.
// 'swing'  the client accepted an attack click for that hand and sent it (target = the entity it aimed at, or -1)
// 'hit' / 'blocked' / 'miss'  the server's verdict on that swing (crit / sweep / fatal from the server)
// 'denied' the click was consumed without an attack (recharging, a repeat in one tick, or refused by the server)
// event = {seq, at, hand:'M'|'O', kind, item, target, crit, sweep, fatal, pair:{main, off}, ref, tick}; additions to the
// contract: ref = seq of the 'swing' a server verdict answers (-1 otherwise), tick = the player's tick count then.
var JasprCombatFeed={seq:0,events:[],since:function(n){
  var out=[],list=JasprCombatFeed.events,i;n=Number(n);if(!(n>=0))n=0;
  for(i=0;i<list.length;i++)if(list[i].seq>n)out.push(list[i]);
  return out;
}};
function JasprCombatFeedPush(e){
  var f=JasprCombatFeed;f.seq++;e.seq=f.seq;f.events.push(e);while(f.events.length>64)f.events.shift();return e;
}
var JasprBetterCombat=(function(){
  'use strict';
  var BUILD='bettercombat9',STALE_MS=15000,REHELLO_MS=5000,RESULT_MS=4000,MAX_PENDING=16;
  var connection=null,settings=null,cfg=null,helloAt=0,tries=0,receivedAt=0,heardAt=0,packets=0,sweeps=0;
  var visual={spawned:0,rendered:0,last:null,motionDraws:0,attacks:0};
  var swings=[{at:0,sequence:0},{at:0,sequence:0}];
  // ticks: client ticks seen by JasprCombatTick; presses: use-key presses still pending in this tick (fresh clicks)
  var ticks=0,presses=0,lastAttack=[-1,-1],nextAttack=0,pending=[],offPredict=null,mainReset=0,offHud=false,offCache=null;
  var stats={sent:[0,0],results:{hit:0,blocked:0,miss:0,denied:0},refused:{},local:{},vanilla:{},used:0,rehello:0,lost:0,corrected:0,errors:0};
  var diag={sent:0,page:'bettercombat-'+Date.now().toString(36),lastError:'',state:null};
  // Bounded, same-origin, through the existing diagnostics route: counters and state names only, never names or positions.
  function report(event,details){
    try{
      var loc=$rt_globals.location;
      if(diag.sent>=8||!loc||String(loc.pathname).indexOf('/jaspercraft/')!==0||typeof $rt_globals.fetch!=='function')return;
      diag.sent++;
      $rt_globals.fetch('/api/diagnostics/events',{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json','X-Jaspergers-Client':'web-v1'},
        body:JSON.stringify({events:[{event:event,pageSessionId:diag.page,at:new Date().toISOString(),details:details}]})}).catch(function(){});
    }catch(ignore){}
  }
  function summary(){return {sent:stats.sent.slice(),results:copy(stats.results),refused:copy(stats.refused),local:copy(stats.local),
    vanilla:copy(stats.vanilla),used:stats.used,rehello:stats.rehello,lost:stats.lost,corrected:stats.corrected,errors:stats.errors};}
  function copy(o){var r={},k;for(k in o)r[k]=o[k];return r;}
  function fail(stage,error){
    stats.errors++;diag.lastError=String(error&&error.message||error).replace(/[\r\n]+/g,' ').slice(0,160);
    if(stats.errors<=3)report('jaspercraft.bettercombat.error',{stage:String(stage).slice(0,32),error:diag.lastError,build:BUILD});
  }
  function phase(name){if(diag.state===name)return;diag.state=name;report('jaspercraft.bettercombat.state',{state:name,build:BUILD,proto:cfg?cfg.proto:0,stats:summary()});}
  function active(mc){return !!(settings&&settings.enabled&&mc&&mc.v&&mc.X&&mc.v.d_&&mc.v.d_.qf===connection&&Date.now()-heardAt<STALE_MS);}
  function ids(list,max){
    var out=Object.create(null),n=0,i,v;
    if(Array.isArray(list))for(i=0;i<list.length&&n<max;i++){v=list[i];if(Number.isSafeInteger(v)&&v>=0&&v<65536){out[v]=1;n++;}}
    return out;
  }
  function configure(c){
    if(!Number.isSafeInteger(c.proto)||c.proto<2)return;
    cfg={proto:c.proto,off:ids(c.offIds,4096),noAttack:ids(c.noAttack,1024),players:c.players===true};offCache=null;
  }
  // Item ids as the contract has them: an empty hand is -1 (the engine and the server say 0, which is air).
  function itemId(id){return id>0?id:-1;}
  function feed(hand,kind,item,target,crit,sweep,fatal,pair,ref){
    var pl=typeof HEH!=='undefined'&&HEH&&HEH.v?HEH.v:null;
    return JasprCombatFeedPush({seq:0,at:Date.now(),hand:hand?'O':'M',kind:kind,item:itemId(item),target:target,crit:!!crit,sweep:!!sweep,
      fatal:!!fatal,pair:{main:itemId(pair.main),off:itemId(pair.off)},ref:ref,tick:pl?pl.cv:-1});
  }
  // The server's verdict on one attack message (protocol 2): feed it, and keep the main-hand energy in step.
  function result(r){
    var seq=r.s,i,p=null,kind,target,item,used,mc,pl,why;
    if(!Number.isSafeInteger(seq))return;
    for(i=0;i<pending.length;i++)if(pending[i].seq===seq){p=pending[i];pending.splice(i,1);break;}
    if(!p)return;
    kind=r.k==='hit'||r.k==='blocked'||r.k==='miss'||r.k==='denied'?r.k:null;if(!kind)return;
    stats.results[kind]++;
    if(kind==='denied'){why=typeof r.r==='string'?r.r.slice(0,16):'?';stats.refused[why]=(stats.refused[why]||0)+1;}
    target=Number.isSafeInteger(r.t)&&r.t>=0?r.t:-1;item=Number.isSafeInteger(r.i)?r.i:p.item;used=r.e===1;
    feed(p.hand,kind,item,target,r.c===1,r.w===1,r.f===1,p.pair,p.ref);
    if(p.hand===1){if(!used&&offPredict&&offPredict.seq===seq)offPredict=null;return;}
    // A click the client counted as spent that the server did not spend (a refunded miss, a refusal) gives the energy
    // back; a hit the client did not foresee spends it. Vanilla's cooldown field is ticksSinceLastSwing (bDd).
    mc=typeof HEH!=='undefined'?HEH:null;pl=mc&&mc.v;
    if(!pl||pl!==p.player)return;
    if(!used&&p.spent&&mainReset===seq){pl.bDd=Math.max(pl.bDd,p.before+Math.max(0,pl.cv-p.tick));stats.corrected++;}
    else if(used&&!p.spent){pl.bDd=0;mainReset=seq;stats.corrected++;}
  }
  function receive(text){
    try{
      var s=JSON.parse(text),now=Date.now();
      if(!s||s.v!==1)return;
      if(s.res&&typeof s.res==='object'){heardAt=now;result(s.res);return;}
      if(s.cfg&&typeof s.cfg==='object'){heardAt=now;configure(s.cfg);return;}
      if(!Number.isFinite(s.range)||s.range<0||s.range>5||!Number.isFinite(s.width)||s.width<0||s.width>64||!Number.isFinite(s.remaining)||!Number.isFinite(s.period))return;
      // The original ScheduledPacketTask creates one custom sweep when the server
      // sets the off-hand cooldown. Never infer a swing from a periodic timer update.
      if(settings&&Number.isSafeInteger(s.offSwing)&&s.offSwing!==settings.offSwing&&s.offSwing>0)sweeps=Math.min(4,sweeps+1);
      settings=s;receivedAt=heardAt=now;packets++;
      phase(!s.enabled?'disabled':cfg&&s.proto>=2?'ready':'legacy');
    }catch(e){fail('receive',e);}
  }
  function outgoing(mc){
    var net=mc&&mc.v&&mc.v.d_?mc.v.d_.qf:null,now=Date.now();
    if(net!==connection){
      connection=net;settings=null;cfg=null;tries=0;sweeps=0;pending.length=0;offPredict=null;offHud=false;offCache=null;helloAt=now+500;
      try{if(typeof JasprMeleeMotion!=='undefined'&&JasprMeleeMotion&&JasprMeleeMotion.clear)JasprMeleeMotion.clear();}catch(e){fail('clear',e);}
    }
    if(!net||net.bkf||!mc.X)return null;
    if(!settings){if(tries<3&&now>=helloAt){tries++;helloAt=now+5000;return net;}return null;}
    // A silent server (the plugin reloaded, or a long stall): ask again every few seconds; it answers at most once a second.
    if(now-heardAt>=REHELLO_MS&&now>=helloAt){helloAt=now+REHELLO_MS;stats.rehello++;if(now-heardAt>=STALE_MS)phase('silent');return net;}
    return null;
  }
  // Once per client tick, before the engine handles this tick's key presses (start of processKeyBinds).
  function tick(mc){
    try{
      var now=Date.now();ticks++;
      presses=mc&&mc.G&&mc.G.Nc?mc.G.Nc.bSp|0:0;
      while(pending.length&&now-pending[0].at>RESULT_MS){pending.shift();stats.lost++;}
      if(offPredict&&now-offPredict.at>=offPredict.ms)offPredict=null;
    }catch(e){fail('tick',e);}
  }
  // rightClickMouse runs once per press of the use key, then every four ticks while it is held. Only a press attacks
  // (like Forge's MouseEvent in the original and like the main hand); the repeats stay vanilla.
  function freshUse(mc){var n=mc&&mc.G&&mc.G.Nc?mc.G.Nc.bSp|0:0,fresh=presses>n;presses=n;return fresh;}
  function skip(reason){stats.vanilla[reason]=(stats.vanilla[reason]||0)+1;return false;}
  function begin(mc,off){
    var fresh=off?freshUse(mc):true;
    if(!active(mc)||mc.cj!==null||!mc.uE||mc.v.bBT)return null;
    if(off){if(mc.dw.NS||!settings.dual)return null;if(!fresh){skip('held');return null;}}
    return {s:settings,off:!!off,proto:!!(cfg&&settings.proto>=2),mainId:0,offId:0};
  }
  // Is item id an off-hand weapon? Protocol 2: the server's list, so a hotbar switch or F needs no round trip.
  // An older server: its per-item flag, valid only for the item it last saw.
  function offItem(id){return cfg&&settings&&settings.proto>=2?!!cfg.off[id]:!!(settings&&settings.off&&id===settings.offItem);}
  function deny(ctx,reason){
    stats.local[reason]=(stats.local[reason]||0)+1;
    feed(ctx.off?1:0,'denied',ctx.off?ctx.offId:ctx.mainId,-1,false,false,false,{main:ctx.mainId,off:ctx.offId},-1);
    return true;
  }
  // The click becomes an attack message: one per hand and tick (the server's rule), fed as 'swing', its verdict pending.
  function attack(mc,ctx,h){
    var hand=ctx.off?1:0,pl=mc.v,now=Date.now(),pair={main:ctx.mainId,off:ctx.offId},item=hand?ctx.offId:ctx.mainId,seq,spent,ev;
    if(lastAttack[hand]===ticks){deny(ctx,'repeat');return null;}
    lastAttack[hand]=ticks;
    seq=nextAttack=nextAttack>=2147483646?1:nextAttack+1;
    spent=!!(h||!ctx.s.refund);
    ev=feed(hand,'swing',item,h?h.cu:-1,false,false,false,pair,-1);
    if(ctx.proto){
      pending.push({seq:seq,hand:hand,ref:ev.seq,pair:pair,item:item,at:now,tick:pl.cv,player:pl,before:pl.bDd,spent:spent});
      if(pending.length>MAX_PENDING){pending.shift();stats.lost++;}
    }
    if(!hand&&spent)mainReset=seq;
    // The off hand's bar reacts at the click, like vanilla's main hand, until the server's own timer arrives.
    if(hand&&spent&&ctx.s.offItem===ctx.offId&&ctx.s.period>0)offPredict={seq:seq,at:now,ms:ctx.s.period*50};
    stats.sent[hand]++;
    return 'attack '+(hand?'O':'M')+(ctx.proto?' '+seq:'');
  }
  function empty(s){return !s||s===Ktg||!s.rA||!!s.bg_;}
  function blocks(mc){return mc.h4&&mc.h4.ks===null&&mc.h4.mh!==null&&mc.h4.kE.d===1;}
  return {receive:receive,outgoing:outgoing,active:active,empty:empty,blocks:blocks,tick:tick,begin:begin,offItem:offItem,
    takeSweep:function(mc){if(!active(mc)||!sweeps)return false;sweeps--;return true;},
    visual:visual,
    // Legacy servers: the main hand's special flag and item id must match what the server last saw.
    mainOk:function(ctx){return ctx.proto||!!(ctx.s.main&&ctx.mainId===ctx.s.mainItem);},
    // Villagers, horses, armor stands ... (the server's off-hand blacklist) keep their right-click interaction.
    attackable:function(e,type){if(!cfg)return true;if(e instanceof Cb)return !cfg.players;return !(type>=0&&cfg.noAttack[type]);},
    attack:function(mc,ctx,h){try{return attack(mc,ctx,h);}catch(e){fail('attack',e);return null;}},
    deny:function(ctx,reason){try{return deny(ctx,reason);}catch(e){fail('deny',e);return true;}},
    skip:skip,used:function(){stats.used++;return true;},
    // Per-stack memo of the off-hand weapon check (protocol 2 only: an older server's answer changes with its packets).
    offCached:function(s){var c=offCache;return cfg&&c&&c.stack===s&&c.tag===s.bV&&c.item===s.rA&&c.cfg===cfg?c.value:null;},
    offCache:function(s,v){offCache=cfg?{stack:s,tag:s.bV,item:s.rA,cfg:cfg,value:!!v}:null;},
    wantsHands:function(mc){return !!(cfg&&active(mc)&&settings.dual&&mc.v);},
    hud:function(v){offHud=!!v;},
    offReady:function(){return cfg&&settings&&settings.proto>=2?offHud:!!(settings&&settings.off);},
    markSwing:function(off,id){
      var s=swings[off?1:0];s.at=Date.now();s.sequence++;visual.attacks++;
      if(id===undefined)id=settings?(off?settings.offItem:settings.mainItem):0;
      try{if(typeof JasprMeleeMotion!=='undefined'&&JasprMeleeMotion&&JasprMeleeMotion.mark)JasprMeleeMotion.mark(off,id,s.at,typeof HEH!=='undefined'&&HEH&&HEH.v?HEH.v.cv:null);}
      catch(e){fail('mark',e);}
    },
    motion:function(main){var s=swings[main?0:1];return Date.now()-s.at<700?s:null;},
    settings:function(){return settings;},
    remaining:function(){
      var now=Date.now(),r=settings?Math.max(0,settings.remaining-(now-receivedAt)/50):0,q;
      if(offPredict){q=(offPredict.ms-(now-offPredict.at))/50;if(q>r)r=q;}
      return r;
    },
    status:function(){return {seen:!!settings,packets:packets,settings:settings,build:BUILD,proto:cfg?cfg.proto:0,pending:pending.length,
      feed:JasprCombatFeed.seq,stats:summary(),lastError:diag.lastError};}};
}());
// Read-only DOM telemetry lets an official account-frame test inspect activation
// without injecting attacks or exposing the TeaVM runtime through the browser API.
var JasprCombatObservedAt=0,JasprCombatTicks=0,JasprCombatHellos=0;
function JasprCombatObserve(mc){
  JasprCombatTicks++;
  if(typeof document==='undefined'||Date.now()-JasprCombatObservedAt<1000)return;
  var host=document.getElementById('game_frame');if(!host)return;
  JasprCombatObservedAt=Date.now();
  host.setAttribute('data-jaspr-combat',JSON.stringify({build:'bettercombat9',ticks:JasprCombatTicks,hellos:JasprCombatHellos,
    seen:JasprBetterCombat.status().seen,packets:JasprBetterCombat.status().packets,active:JasprBetterCombat.active(mc),
    inGame:!!(mc&&mc.v&&mc.X),main:JasprBetterCombat.settings()?JasprBetterCombat.settings().main:null,
    off:JasprBetterCombat.settings()?JasprBetterCombat.settings().off:null,motion:JasprBetterCombat.visual.motionDraws,
    attacks:JasprBetterCombat.visual.attacks,sweeps:JasprBetterCombat.visual.spawned,feed:JasprCombatFeed.seq}));
}
if(typeof document!=='undefined'&&document.getElementById('game_frame'))document.getElementById('game_frame').setAttribute('data-jaspr-combat-build','bettercombat9');
// Read-only diagnostics; deliberately no remote attack/debug-command entry point.
$rt_globals.JasprBetterCombatStatus=function(){var mc=typeof HEH==='undefined'?null:HEH;return {combat:JasprBetterCombat.status(),
  active:JasprBetterCombat.active(mc),inGame:!!(mc&&mc.v&&mc.X),focus:mc?!!mc.uE:false,
  screen:mc&&mc.cj?String(mc.cj.constructor.name):null,
  swing:mc&&mc.v?{active:!!mc.v.G1,hand:mc.v.crX===HFd?'M':'O',tick:mc.v.a2f,progress:mc.v.bYC,view:mc.G.lv}:null,
  visual:JasprBetterCombat.visual,epic:typeof JasprMeleeMotion!=='undefined'&&JasprMeleeMotion?JasprMeleeMotion.stats:null};};
$rt_globals.JasprBetterCombatDiagnostics={status:function(){var s=JasprBetterCombat.status();
  s.recent=JasprCombatFeed.events.slice(-8).map(function(e){return {seq:e.seq,hand:e.hand,kind:e.kind,item:e.item,target:e.target,crit:e.crit,sweep:e.sweep,fatal:e.fatal,ref:e.ref};});
  return s;}};

function JasprCombatSend(a,b){
  var c,d,e,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:c=new AKy;d=new Iu;$p=1;
  case 1:$z=Fru();if(B())break _;Lg(d,$z);e=$rt_str(b);$p=2;
  case 2:$z=FuF(d,e);if(B())break _;BgN(c,$rt_str('jaspr:combat'),$z);$p=3;
  case 3:a.wd(c);if(B())break _;return;
  default:FT();}}
  Ds().s(a,b,c,d,e,$p);
}
// Once per client tick at the start of processKeyBinds: press bookkeeping, hello, the off-hand bar's item check, sweeps.
function JasprCombatTick(a){
  var b,c,d,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:JasprCombatObserve(a);JasprBetterCombat.tick(a);b=JasprBetterCombat.outgoing(a);if(!b){$p=2;continue _;}$p=1;
  case 1:JasprCombatSend(b,'hello 1');if(B())break _;JasprCombatHellos++;$p=2;
  case 2:if(!JasprBetterCombat.wantsHands(a)){$p=5;continue _;}c=a.v;$p=3;
  case 3:$z=EjD(c);if(B())break _;d=$z;$p=4;
  case 4:$z=JasprCombatOffWeapon(d);if(B())break _;JasprBetterCombat.hud($z);$p=5;
  case 5:if(!JasprBetterCombat.takeSweep(a))return;$p=6;
  case 6:JasprCombatSweep(a);if(B())break _;return;
  default:FT();}}Ds().s(a,b,c,d,$p);
}
// Whether this stack is an off-hand weapon now: the server's item list plus the local special-item guard.
function JasprCombatOffWeapon(a){
  var b,c,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:if(JasprBetterCombat.empty(a))return false;c=JasprBetterCombat.offCached(a);if(c!==null)return c;$p=1;
  case 1:$z=C_q(a.rA);if(B())break _;b=$z;if(!JasprBetterCombat.offItem(b)){JasprBetterCombat.offCache(a,false);return false;}$p=2;
  case 2:$z=JasprCombatSpecial(a);if(B())break _;c=!$z;JasprBetterCombat.offCache(a,c);return c;
  default:FT();}}Ds().s(a,b,c,$p);
}
// Vanilla Minecraft.rightClickMouse for ONE hand e (the loop body of the engine's F$w, call for call): the entity under
// the crosshair, the block under it, then the item itself. Returns 1 when that hand did something (the right click is
// over), 0 when it passed. JasprCombatAction runs the main hand, then the off-hand attack, then the off hand.
function JasprCombatUse(a,e){
  var c,f,g,h,i,j,k,l,m,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();m=$T.l();l=$T.l();k=$T.l();j=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();c=$T.l();e=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:f=a.v;$p=1;
  case 1:$z=CjH(f,e);if(B())break _;h=$z;f=a.h4;if(f===null){$p=2;continue _;}$p=3;continue _;
  case 2:$z=CCH(h);if(B())break _;i=$z;if(i)return 0;f=a.dw;g=a.v;j=a.X;$p=4;
  case 4:$z=f.d8I(g,j,e);if(B())break _;f=$z;BT();if(f!==HFg)return 0;f=a.fU.bzi;$p=5;
  case 5:EsL(f,e);if(B())break _;return 1;
  case 3:EI1();if(B())break _;switch(HFe.data[f.kE.d]){case 1:g=a.dw;j=a.v;k=f.ks;$p=6;continue _;case 2:l=f.mh;f=a.X;$p=8;continue _;default:}$p=2;continue _;
  case 6:$z=GfQ(g,j,k,f,e);if(B())break _;f=$z;BT();if(f===HFg)return 1;f=a.dw;g=a.v;j=a.h4.ks;$p=7;
  case 7:$z=Fht(f,g,j,e);if(B())break _;f=$z;if(f===HFg)return 1;$p=2;continue _;
  case 8:$z=CZr(f,l);if(B())break _;f=$z;$p=9;
  case 9:$z=E0Z(f);if(B())break _;f=$z;$p=10;
  case 10:BF();if(B())break _;if(f===HFc){$p=2;continue _;}$p=11;
  case 11:$z=CRE(h);if(B())break _;i=$z;f=a.dw;g=a.v;j=a.X;k=a.h4;m=k.q3;k=k.pN;$p=12;
  case 12:$z=f.efH(g,j,l,m,k,e);if(B())break _;f=$z;BT();if(f!==HFg){$p=2;continue _;}f=a.v;$p=13;
  case 13:Eha(f,e);if(B())break _;$p=14;
  case 14:$z=CCH(h);if(B())break _;c=$z;if(c)return 1;$p=15;
  case 15:$z=CRE(h);if(B())break _;c=$z;if(c==i&&!ANt(a.dw))return 1;f=a.fU.bzi;$p=16;
  case 16:EsL(f,e);if(B())break _;return 1;
  default:FT();}}
  Ds().s(a,e,c,f,g,h,i,j,k,l,m,$p);
}

// Returns true only when the combat adapter consumed this click; the engine then skips its own handling.
// Left click (b false): a main-hand attack on the entity under the combat ray, or a miss swing in the air; a block under
// the crosshair with nothing to hit keeps vanilla mining.
// Right click (b true; a fresh press of the use key, never its held repeat): exactly like vanilla, the main hand gets the
// click first (use an item, a block, an entity); only if it passes does an eligible off-hand weapon attack the entity
// under the combat ray (even with a wall or the ground under the crosshair behind it) or swing in the air. With a block
// and nothing to hit, or an entity that keeps its interaction (villager, horse, armor stand, own pet), the off hand's own
// vanilla use runs (hoes, shovels, buckets ...). Guns, gadgets and portal devices keep all their clicks.
function JasprCombatAction(a,b){
  var c,d,e,f,g,h,i,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:
    c=JasprBetterCombat.begin(a,b);if(!c)return false;
    d=a.v;Fz();if(a.dw.jC===HNb)return JasprBetterCombat.skip('spectator');   // spectators keep vanilla (camera on an entity)
    $p=1;
  case 1:$z=EZ5(d);if(B())break _;e=$z;$p=2;
  case 2:$z=EjD(d);if(B())break _;f=$z;$p=3;
  case 3:$z=C_q(JasprBetterCombat.empty(e)?null:e.rA);if(B())break _;c.mainId=$z;$p=4;
  case 4:$z=C_q(JasprBetterCombat.empty(f)?null:f.rA);if(B())break _;c.offId=$z;$p=5;
  case 5:$z=JasprCombatSpecial(e);if(B())break _;if($z||!JasprBetterCombat.mainOk(c))return JasprBetterCombat.skip('main');
    if(!b){$p=20;continue _;}$p=6;
  case 6:$z=JasprCombatOffWeapon(f);if(B())break _;if(!$z)return JasprBetterCombat.skip('off');
    a.bTh=4;Hc();$p=7;
  case 7:$z=JasprCombatUse(a,HFd);if(B())break _;if($z)return JasprBetterCombat.used();$p=8;
  case 8:g={bu:{hi:d,v:d,X:a.X,dw:a.dw,h4:null,TE:null},uf:null};$p=9;
  case 9:JasprCombatPick(g,1);if(B())break _;h=g.bu.h4&&g.bu.h4.ks;
    if(!h){if(JasprBetterCombat.blocks(a)){$p=12;continue _;}$p=14;continue _;}
    $p=10;
  case 10:I$();if(B())break _;if(!JasprBetterCombat.attackable(h,Gi(KsX,BW(h)))){$p=12;continue _;}
    if(!(h instanceof S5)){$p=14;continue _;}$p=11;
  case 11:$z=F78(h,d);if(B())break _;if($z){$p=12;continue _;}$p=14;continue _;   // never the player's own pet
  case 12:Hc();$p=13;
  case 13:$z=JasprCombatUse(a,HFf.data[1]);if(B())break _;JasprBetterCombat.skip('use');return true;
  case 14:if(c.s.full&&JasprBetterCombat.remaining()>0)return JasprBetterCombat.deny(c,'energy');$p=30;continue _;
  case 20:g={bu:{hi:d,v:d,X:a.X,dw:a.dw,h4:null,TE:null},uf:null};$p=21;
  // A private renderer state prevents longer combat reach changing block-mining reach.
  case 21:JasprCombatPick(g,1);if(B())break _;h=g.bu.h4&&g.bu.h4.ks;
    if(!h&&JasprBetterCombat.blocks(a))return JasprBetterCombat.skip('block');
    if(!c.s.full){$p=30;continue _;}$p=22;
  case 22:$z=DOj(d,.5);if(B())break _;if($z<1)return JasprBetterCombat.deny(c,'energy');$p=30;
  case 30:i=JasprBetterCombat.attack(a,c,h);if(!i)return true;$p=31;
  case 31:GgT(a.dw);if(B())break _;$p=32;   // the held slot reaches the server first, as in vanilla's attackEntity
  case 32:JasprCombatSend(d.d_.qf,i);if(B())break _;if(!b&&(h||!c.s.refund)){$p=33;continue _;}$p=34;continue _;
  case 33:EQX(d);if(B())break _;$p=34;
  case 34:Hc();e=b?HFf.data[1]:HFd;$p=35;
  // EventHandlersClient / DefaultImplOffHandAttack set these two fields directly.
  // Vanilla swingArm (CDe) ignores hand changes during the first half of a swing.
  // Preserve its normal miss animation, but do not discard rapid alternating hits.
  case 35:if(b||h){d.G1=1;d.crX=e;}else{CDe(d,e);if(B())break _;}JasprBetterCombat.markSwing(b,b?c.offId:c.mainId);if(b)a.bTh=4;return true;
  default:FT();}}
  Ds().s(a,b,c,d,e,f,g,h,i,$p);
}

// OPTIONAL JasperCraft enhancement, not claimed as upstream Better Combat code.
// Apply after the original native transform, only to a consumed melee attack.
// Shields, guns, food, bows, maps and normal mining keep their existing renderer.
// No camera shake, frame/tick slowdown, damage change or additional render target.
function JasprCombatTransform(a,b,c,d,e){
 var f,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:$p=1;
 case 1:$z=JasprEpicPose(a,b,c,d,e);if(B())break _;f=$z;$p=2;
 case 2:FX2(a,b,f?0:c);if(B())break _;if(!f)return;
  JasprMeleeMotion.record(f.pose,e,f.id,f.sequence);JasprBetterCombat.visual.motionDraws++;$p=3;
 case 3:DPm(f.pose.tx,f.pose.ty,f.pose.tz);if(B())break _;$p=4;
 case 4:Gc9(f.pose.rotation.angle,f.pose.rotation.x,f.pose.rotation.y,f.pose.rotation.z);if(B())break _;return;
 default:FT();}}Ds().s(a,b,c,d,e,f,$p);
}

// Unarmed attacks use the actual skin arm, not a fake held vanilla item.
function JasprCombatSweep(a){
  var b,c,d,e,f,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:b=a.v;c=b.b-Math.sin(b.C*Math.PI/180);d=b.f+b.bZ*.5;e=b.c+Math.cos(b.C*Math.PI/180);$p=1;
  case 1:$z=CTM(null,0,a.X,c,d,e,0,0,0,null);if(B())break _;f=$z;
    f.qy=function(b,c,d,e,f,g,h,i){JasprCombatSweepRender(this,b,c,d,e,f,g,h,i);};$p=2;
  case 2:CIb(a.it,f);if(B())break _;JasprBetterCombat.visual.spawned++;return;
  default:FT();}}Ds().s(a,b,c,d,e,f,$p);
}

// Entry into the server-authoritative equivalent of Forge's mod configuration GUI.
function JasprCombatOptionsButton(a){
 var b,c,$p=0;
 if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
  case 0:if(!JasprBetterCombat.status().seen||!a.j.X)return;b=new B3;c=$rt_str('Better Combat...');
    JasprBetterCombat.visual.menuEntry={x:(a.q/2|0)+5,y:(a.L/6|0)+114,w:150,h:20,screenWidth:a.q,screenHeight:a.L};$p=1;
 case 1:Bq3(b,1956,(a.q/2|0)+5,(a.L/6|0)+114,150,20,c);if(B())break _;$p=2;
 case 2:Y(a.be,b);if(B())break _;return;
 default:FT();}}Ds().s(a,b,c,$p);
}
function JasprCombatOpenOptions(a){
 var b,$p=0;
 if(FX()){var $T=Ds();$p=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:if(!a.j.v||!a.j.v.d_)return;b=a.j.v.d_.qf;$p=1;
 case 1:GGw(a.j,null);if(B())break _;$p=2;
 case 2:JasprCombatSend(b,'config open');if(B())break _;return;
 default:FT();}}Ds().s(a,b,$p);
}

// A local guard covers item switches before the next server settings packet arrives.
// DaN is NBTTagCompound.hasKey (map field hs). Fbd is JSONObject.has (field Oj)
// and MUST NOT receive item NBT. No name/lore heuristics or unbreakable ban.
function JasprCombatSpecial(a){
  var b,c,d,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:
    if(!a||!a.bV)return false;b=a.bV;c=0;
    d=['JasprGear','JasprGearUse','JasprGearPack','JasprGearIcon','JasprPortalGun'];$p=1;
  case 1:$z=DaN(b,$rt_str(d[c]));if(B())break _;if($z)return true;
    if(++c<d.length){$p=1;continue _;}$p=2;
  case 2:$z=DaN(b,$rt_str('JasprApocalypse'));if(B())break _;if(!$z)return false;$p=3;
  case 3:$z=C_q(a.rA);if(B())break _;
    // Expedition melee weapons keep the combat upgrade; gun/gadget hoes and supplies do not.
    return [256,257,258,267,268,269,270,271,272,273,274,275,276,277,278,279,283,284,285,286].indexOf($z)<0;
  default:FT();}}Ds().s(a,b,c,d,$p);
}

// Port of GuiCrosshairsBC: vanilla icons.png, main left / off right, bars only while charging.
// Called inside EyW after the native crosshair, preserving its debug/spectator/F1 conditions.
// The off-hand bar follows the item in the off hand now (checked every tick), not the last server packet.
function JasprCombatIndicators(a,b,c){
  var d,e,f,g,h,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:
    if(!JasprBetterCombat.active(a.ds)||!JasprBetterCombat.settings().dual)return false;
    if(a.ds.G.o4!==1)return true;
    d=(c.f7/2|0)-7;e=(c.ff/2|0)+9;g=JasprBetterCombat.offReady();h=a.ds.v;$p=1;
  case 1:$z=DOj(h,0);if(B())break _;f=$z;
    if(f>=1){$p=4;continue _;}
    d=d-(g?15:0);$p=2;
  case 2:FYu(a,d,e,36,94,16,4);if(B())break _;$p=3;
  case 3:FYu(a,d,e,52,94,Math.floor(f*17),4);if(B())break _;d=d+(g?15:0);$p=4;
  case 4:
    if(!g)return true;
    f=Math.max(0,Math.min(1,1-JasprBetterCombat.remaining()/Math.max(1,JasprBetterCombat.settings().period)));
    if(f>=1)return true;d=d+15;$p=5;
  case 5:FYu(a,d,e,36,94,16,4);if(B())break _;$p=6;
  case 6:FYu(a,d,e,52,94,Math.floor(f*17),4);if(B())break _;return true;
  default:FT();}}Ds().s(a,b,c,d,e,f,g,h,$p);
}

