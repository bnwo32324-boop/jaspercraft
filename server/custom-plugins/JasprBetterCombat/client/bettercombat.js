/* Better Combat Rebirth 1.5.6 browser adapter. Server-authoritative, connection scoped.
 * Uses the existing TeaVM hand animations and real world/entity ray collision code.
 * Plain JS never invokes a potentially suspending TeaVM method.
 */
var JasprBetterCombat=(function(){
  'use strict';
  var connection=null, settings=null, helloAt=0, tries=0, receivedAt=0, packets=0, sweeps=0;
  var visual={spawned:0,rendered:0,last:null,motionDraws:0,attacks:0};
  var swings=[{at:0,sequence:0},{at:0,sequence:0}];
  function active(mc){return !!(settings&&settings.enabled&&mc&&mc.v&&mc.X&&mc.v.d_&&mc.v.d_.qf===connection&&Date.now()-receivedAt<3000);}
  function receive(text){
    try{var s=JSON.parse(text);if(s.v!==1||!Number.isFinite(s.range)||s.range<0||s.range>5||
      !Number.isFinite(s.width)||s.width<0||s.width>64||!Number.isFinite(s.remaining)||!Number.isFinite(s.period))return;
      // The original ScheduledPacketTask creates one custom sweep when the server
      // sets the off-hand cooldown. Never infer a swing from a periodic timer update.
      if(settings&&Number.isSafeInteger(s.offSwing)&&s.offSwing!==settings.offSwing&&s.offSwing>0)sweeps=Math.min(4,sweeps+1);
      settings=s;receivedAt=Date.now();packets++;}catch(ignore){}
  }
  function outgoing(mc){
    var net=mc&&mc.v&&mc.v.d_?mc.v.d_.qf:null;
    if(net!==connection){connection=net;settings=null;tries=0;sweeps=0;helloAt=Date.now()+500;}
    if(!net||net.bkf||!mc.X)return null;
    if(!settings&&tries<3&&Date.now()>=helloAt){tries++;helloAt=Date.now()+5000;return net;}
    return null;
  }
  function empty(s){return !s||s===Ktg||!s.rA||!!s.bg_;}
  function blocks(mc){return mc.h4&&mc.h4.ks===null&&mc.h4.mh!==null&&mc.h4.kE.d===1;}
  return {receive:receive,outgoing:outgoing,active:active,empty:empty,blocks:blocks,
    takeSweep:function(mc){if(!active(mc)||!sweeps)return false;sweeps--;return true;},
    visual:visual,
    markSwing:function(off){var s=swings[off?1:0];s.at=Date.now();s.sequence++;visual.attacks++;},
    motion:function(main){var s=swings[main?0:1];return Date.now()-s.at<700?s:null;},
    settings:function(){return settings;},
    remaining:function(){return settings?Math.max(0,settings.remaining-(Date.now()-receivedAt)/50):0;},
    status:function(){return {seen:!!settings,packets:packets,settings:settings};}};
}());
// Read-only DOM telemetry lets an official account-frame test inspect activation
// without injecting attacks or exposing the TeaVM runtime through the browser API.
var JasprCombatObservedAt=0,JasprCombatTicks=0,JasprCombatHellos=0;
function JasprCombatObserve(mc){
  JasprCombatTicks++;
  if(typeof document==='undefined'||Date.now()-JasprCombatObservedAt<1000)return;
  var host=document.getElementById('game_frame');if(!host)return;
  JasprCombatObservedAt=Date.now();
  host.setAttribute('data-jaspr-combat',JSON.stringify({build:'bettercombat4',ticks:JasprCombatTicks,hellos:JasprCombatHellos,
    seen:JasprBetterCombat.status().seen,packets:JasprBetterCombat.status().packets,active:JasprBetterCombat.active(mc),
    inGame:!!(mc&&mc.v&&mc.X),main:JasprBetterCombat.settings()?JasprBetterCombat.settings().main:null,
    off:JasprBetterCombat.settings()?JasprBetterCombat.settings().off:null,motion:JasprBetterCombat.visual.motionDraws,
    attacks:JasprBetterCombat.visual.attacks,sweeps:JasprBetterCombat.visual.spawned}));
}
if(typeof document!=='undefined'&&document.getElementById('game_frame'))document.getElementById('game_frame').setAttribute('data-jaspr-combat-build','bettercombat4');
// Read-only diagnostics; deliberately no remote attack/debug-command entry point.
$rt_globals.JasprBetterCombatStatus=function(){var mc=typeof HEH==='undefined'?null:HEH;return {combat:JasprBetterCombat.status(),
  active:JasprBetterCombat.active(mc),inGame:!!(mc&&mc.v&&mc.X),focus:mc?!!mc.uE:false,
  screen:mc&&mc.cj?String(mc.cj.constructor.name):null,
  swing:mc&&mc.v?{active:!!mc.v.G1,hand:mc.v.crX===HFd?'M':'O',tick:mc.v.a2f,progress:mc.v.bYC,view:mc.G.lv}:null,
  visual:JasprBetterCombat.visual};};

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
function JasprCombatTick(a){
  var b,$p=0;
  if(FX()){var $T=Ds();$p=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:JasprCombatObserve(a);b=JasprBetterCombat.outgoing(a);if(!b){$p=2;continue _;}$p=1;
  case 1:JasprCombatSend(b,'hello 1');if(B())break _;JasprCombatHellos++;$p=2;
  case 2:if(!JasprBetterCombat.takeSweep(a))return;$p=3;
  case 3:JasprCombatSweep(a);if(B())break _;return;
  default:FT();}}Ds().s(a,b,$p);
}

// Returns true only when the custom combat action consumed this click.
function JasprCombatAction(a,b){
  var c,d,e,f,g,h,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:
    if(!JasprBetterCombat.active(a)||a.cj!==null||!a.uE||a.v.bBT)return false;
    c=JasprBetterCombat.settings();
    if(!c.main||b&&!c.off)return false;
    // Keep ordinary block interactions, shields, food and other active-use items intact.
    if(b&&JasprBetterCombat.blocks(a))return false;
    d=a.v;$p=1;
  case 1:$z=EZ5(d);if(B())break _;e=$z;$p=2;
  case 2:$z=EjD(d);if(B())break _;f=$z;
    if(b&&c.use)return false;
    $p=11;
  case 11:$z=C_q(JasprBetterCombat.empty(e)?null:e.rA);if(B())break _;
    if($z!==c.mainItem)return false;$p=12;
  case 12:$z=JasprCombatSpecial(e);if(B())break _;if($z)return false;
    if(!b){$p=4;continue _;}$p=13;
  case 13:$z=C_q(JasprBetterCombat.empty(f)?null:f.rA);if(B())break _;
    if($z!==c.offItem)return false;$p=14;
  case 14:$z=JasprCombatSpecial(f);if(B())break _;if($z)return false;
    $p=4;continue _;
  case 4:
    // A private renderer state prevents longer combat reach changing block-mining reach.
    g={bu:{hi:d,v:d,X:a.X,dw:a.dw,h4:null,TE:null},uf:null};$p=5;
  case 5:JasprCombatPick(g,1);if(B())break _;
    h=g.bu.h4&&g.bu.h4.ks;
    if(!h&&JasprBetterCombat.blocks(a))return false;
    if(c.full){if(b&&JasprBetterCombat.remaining()>0)return true;if(!b){$p=6;continue _;}}
    $p=7;continue _;
  case 6:$z=DOj(d,.5);if(B())break _;if($z<1)return true;$p=7;
  case 7:JasprCombatSend(d.d_.qf,b?'attack O':'attack M');if(B())break _;
    if(!b&&(h||!c.refund)){$p=8;continue _;}
    $p=9;continue _;
  case 8:EQX(d);if(B())break _;$p=9;
  case 9:Hc();e=b?HFf.data[1]:HFd;$p=10;
  // EventHandlersClient / DefaultImplOffHandAttack set these two fields directly.
  // Vanilla swingArm (CDe) ignores hand changes during the first half of a swing.
  // Preserve its normal miss animation, but do not discard rapid alternating hits.
  case 10:if(b||h){d.G1=1;d.crX=e;}else{CDe(d,e);if(B())break _;}JasprBetterCombat.markSwing(b);if(b)a.bTh=4;return true;
  default:FT();}}
  Ds().s(a,b,c,d,e,f,g,h,$p);
}

// OPTIONAL JasperCraft enhancement, not claimed as upstream Better Combat code.
// Apply after the original native transform, only to a consumed melee attack.
// Shields, guns, food, bows, maps and normal mining keep their existing renderer.
// No camera shake, frame/tick slowdown, damage change or additional render target.
function JasprCombatTransform(a,b,c,d,e){
 var f,g,h,i,j,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();j=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:$p=1;
 case 1:FX2(a,b,c);if(B())break _;
   if(!JasprBetterCombat.active(a.kD)||!JasprBetterCombat.settings().motion||c<=.001||c>=1)return;
   f=JasprBetterCombat.motion(e);if(!f)return;$p=2;
 case 2:$z=C_q(d.rA);if(B())break _;
   if([256,257,258,267,268,269,270,271,272,273,274,275,276,277,278,279,283,284,285,286].indexOf($z)<0)return;
   g=Math.sin(Math.PI*c);h=b===Kua?1:-1;i=(f.sequence-1)%3;
   j=i===0?-58*g:i===1?46*g:12*g;
   JasprBetterCombat.visual.motionDraws++;$p=3;
 case 3:Gc9(h*j,0,0,1);if(B())break _;$p=4;
 case 4:Gc9(i===1?-36*g:-16*g,1,0,0);if(B())break _;$p=5;
 case 5:DPm(-h*g*(i===2?.08:.20),g*(i===1?.07:.12),-g*(i===2?.26:.07));if(B())break _;return;
 default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,$p);
}

// ClientProxy.spawnSweep, using the existing native particle constructor/manager.
// Only this instance receives EntitySweepAttack2FX's renderer. Vanilla effects
// and other agents' particle performance settings are not globally replaced.
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
function JasprCombatIndicators(a,b,c){
  var d,e,f,g,h,$p=0,$z;
  if(FX()){var $T=Ds();$p=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){
  case 0:
    if(!JasprBetterCombat.active(a.ds)||!JasprBetterCombat.settings().dual)return false;
    if(a.ds.G.o4!==1)return true;
    d=(c.f7/2|0)-7;e=(c.ff/2|0)+9;g=JasprBetterCombat.settings().off;h=a.ds.v;$p=1;
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
