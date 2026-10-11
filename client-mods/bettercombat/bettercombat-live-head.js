/* JASPR_BETTERCOMBAT_V1_BEGIN */
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
    if(net!==connection){connection=net;settings=null;tries=0;sweeps=0;JasprMeleeMotion.clear();helloAt=Date.now()+500;}
    if(!net||net.bkf||!mc.X)return null;
    if(!settings&&tries<3&&Date.now()>=helloAt){tries++;helloAt=Date.now()+5000;return net;}
    return null;
  }
  function empty(s){return !s||s===Ktg||!s.rA||!!s.bg_;}
  function blocks(mc){return mc.h4&&mc.h4.ks===null&&mc.h4.mh!==null&&mc.h4.kE.d===1;}
  return {receive:receive,outgoing:outgoing,active:active,empty:empty,blocks:blocks,
    takeSweep:function(mc){if(!active(mc)||!sweeps)return false;sweeps--;return true;},
    visual:visual,
    markSwing:function(off){var s=swings[off?1:0];s.at=Date.now();s.sequence++;JasprMeleeMotion.mark(off,settings?(off?settings.offItem:settings.mainItem):0,s.at,typeof HEH!=='undefined'&&HEH&&HEH.v?HEH.v.cv:null);visual.attacks++;},
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
  host.setAttribute('data-jaspr-combat',JSON.stringify({build:'bettercombat8',ticks:JasprCombatTicks,hellos:JasprCombatHellos,
    seen:JasprBetterCombat.status().seen,packets:JasprBetterCombat.status().packets,active:JasprBetterCombat.active(mc),
    inGame:!!(mc&&mc.v&&mc.X),main:JasprBetterCombat.settings()?JasprBetterCombat.settings().main:null,
    off:JasprBetterCombat.settings()?JasprBetterCombat.settings().off:null,motion:JasprBetterCombat.visual.motionDraws,
    attacks:JasprBetterCombat.visual.attacks,sweeps:JasprBetterCombat.visual.spawned}));
}
if(typeof document!=='undefined'&&document.getElementById('game_frame'))document.getElementById('game_frame').setAttribute('data-jaspr-combat-build','bettercombat8');
// Read-only diagnostics; deliberately no remote attack/debug-command entry point.
$rt_globals.JasprBetterCombatStatus=function(){var mc=typeof HEH==='undefined'?null:HEH;return {combat:JasprBetterCombat.status(),
  active:JasprBetterCombat.active(mc),inGame:!!(mc&&mc.v&&mc.X),focus:mc?!!mc.uE:false,
  screen:mc&&mc.cj?String(mc.cj.constructor.name):null,
  swing:mc&&mc.v?{active:!!mc.v.G1,hand:mc.v.crX===HFd?'M':'O',tick:mc.v.a2f,progress:mc.v.bYC,view:mc.G.lv}:null,
  visual:JasprBetterCombat.visual,epic:JasprMeleeMotion.stats};};

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

function JasprCombatSweepRender(a,b,c,d,e,f,g,h,i){var j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,ba,bb,bc,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();bc=$T.l();bb=$T.l();ba=$T.l();z=$T.l();y=$T.l();x=$T.l();w=$T.l();v=$T.l();u=$T.l();t=$T.l();s=$T.l();r=$T.l();q=$T.l();p=$T.l();o=$T.l();n=$T.l();m=$T.l();l=$T.l();k=$T.l();j=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:JasprBetterCombat.visual.rendered++;JasprBetterCombat.visual.last={life:a.cHo,partial:d,lifetime:a.cIE,size:a.c9V,x:a.d1,y:a.db,z:a.d0};j=(a.cHo+d)*3.0/a.cIE|0;if(j>7)return;c=a.de9;$p=1;case 1:Ha8();if(B()){break _;}k=LFC;$p=2;case 2:D17(c,
k);if(B()){break _;}l=(j%4|0)/4.0;m=l-0.24975000321865082;n=j/2.0/2.0;o=n-0.49950000643730164;p=-1.0*a.c9V;q=a.kX;r=a.d1-q;s=d;t=q+r*s-LoV;q=a.iR;u=q+(a.db-q)*s-LoW;q=a.kW;v=q+(a.d0-q)*s-LoX;w=1.0;x=1.0;y=1.0;d=1.0;$p=3;case 3:CFh(w,x,y,d);if(B()){break _;}$p=4;case 4:DFk();if(B()){break _;}$p=5;case 5:DnA();if(B()){break _;}z=7;C5();c=Lou;$p=6;case 6:Ep0(b,z,c);if(B()){break _;}d=e*p;e=t-d;h=h*p;q=e-h;x=f*p*0.5;ba=u-x;w=g*p;y=v-w;p=i*p;r=y-p;$p=7;case 7:$z=CUb(b,q,ba,r);if(B()){break _;}c=$z;s=m;bb=o;$p=8;case 8:$z
=EpJ(c,s,bb);if(B()){break _;}c=$z;f=a.eC;g=a.ey;i=a.eu;o=1.0;$p=9;case 9:$z=Eip(c,f,g,i,o);if(B()){break _;}c=$z;j=0;z=240;$p=10;case 10:$z=Do_(c,j,z);if(B()){break _;}c=$z;f=0.0;g=1.0;i=0.0;$p=11;case 11:$z=Gu1(c,f,g,i);if(B()){break _;}c=$z;$p=12;case 12:E74(c);if(B()){break _;}bc=e+h;q=u+x;r=y+p;$p=13;case 13:$z=CUb(b,bc,q,r);if(B()){break _;}c=$z;r=n;$p=14;case 14:$z=EpJ(c,s,r);if(B()){break _;}c=$z;e=a.eC;f=a.ey;g=a.eu;i=1.0;$p=15;case 15:$z=Eip(c,e,f,g,i);if(B()){break _;}c=$z;z=0;j=240;$p=16;case 16:$z
=Do_(c,z,j);if(B()){break _;}c=$z;e=0.0;f=1.0;g=0.0;$p=17;case 17:$z=Gu1(c,e,f,g);if(B()){break _;}c=$z;$p=18;case 18:E74(c);if(B()){break _;}d=t+d;s=d+h;e=v+w;bc=e+p;$p=19;case 19:$z=CUb(b,s,q,bc);if(B()){break _;}c=$z;q=l;$p=20;case 20:$z=EpJ(c,q,r);if(B()){break _;}c=$z;f=a.eC;g=a.ey;i=a.eu;n=1.0;$p=21;case 21:$z=Eip(c,f,g,i,n);if(B()){break _;}c=$z;z=0;j=240;$p=22;case 22:$z=Do_(c,z,j);if(B()){break _;}c=$z;f=0.0;g=1.0;i=0.0;$p=23;case 23:$z=Gu1(c,f,g,i);if(B()){break _;}c=$z;$p=24;case 24:E74(c);if(B())
{break _;}r=d-h;s=e-p;$p=25;case 25:$z=CUb(b,r,ba,s);if(B()){break _;}b=$z;$p=26;case 26:$z=EpJ(b,q,bb);if(B()){break _;}b=$z;d=a.eC;e=a.ey;f=a.eu;g=1.0;$p=27;case 27:$z=Eip(b,d,e,f,g);if(B()){break _;}b=$z;z=0;j=240;$p=28;case 28:$z=Do_(b,z,j);if(B()){break _;}b=$z;d=0.0;e=1.0;f=0.0;$p=29;case 29:$z=Gu1(b,d,e,f);if(B()){break _;}b=$z;$p=30;case 30:E74(b);if(B()){break _;}$p=31;case 31:$z=GdM();if(B()){break _;}b=$z;$p=32;case 32:FE$(b);if(B()){break _;}$p=33;case 33:D75();if(B()){break _;}return;default:FT();}}Ds().s(a,
b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,ba,bb,bc,$p);}

function JasprCombatPick(a,b){var c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();q=$T.l();p=$T.l();o=$T.l();n=$T.l();m=$T.l();l=$T.l();k=$T.l();j=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:c=a.bu;d=c.hi;if(d!==null&&c.X!==null){c.TE=null;e=JasprBetterCombat.settings().range;f=a.bu;$p=1;continue _;}return;case 1:$z=Gwm(d,e,b);if(B()){break _;}c=$z;f.h4=c;$p=2;case 2:$z=DWa(d,b);if(B()){break _;}f=$z;g=0;h=e;c=a.bu.h4;if(c!==null){c=c.pN;$p=3;continue _;}b=1.0;$p=4;continue _;case 3:$z=GoX(c,f);if(B()){break _;}h=$z;b=1.0;$p=4;case 4:$z=d.bWS(b);if(B()){break _;}i=$z;j=Mc(f,i.bh*e,i.bq*e,i.bi*e);a.uf=null;k=null;l=a.bu.X;c=De(Mw(d.bc,i.bh*e,i.bq*e,i.bi*e),1.0,1.0,1.0);i=Kw_;m=new BBY;m.elw=a;i=AAu(i,m);$p=5;case 5:$z=EcF(l,d,c,i);if(B()){break _;}l=$z;n=0;o=h;if(n<l.g){$p=6;continue _;}if(a.uf!==null&&g){$p=9;continue _;}f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)
&&!(f instanceof Ih)))a.bu.TE=f;}return;case 6:$z=Bm(l,n);if(B()){break _;}c=$z;m=c;c=m.bc;$p=7;case 7:$z=m.cNd();if(B()){break _;}b=$z;e=b;c=JasprCombatBox(c);$p=8;case 8:$z=DUG(c,f,j);if(B()){break _;}p=$z;if(Bgh(c,f)){if(o>=0.0){a.uf=m;k=p!==null?p.pN:f;o=0.0;}}else if(p!==null){c=p.pN;$p=10;continue _;}n=n+1|0;if(n<l.g){$p=6;continue _;}if(a.uf!==null&&g){$p=9;continue _;}f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)&&!(f instanceof Ih)))a.bu.TE=f;}return;case 9:$z
=GoX(f,k);if(B()){break _;}e=$z;if(e<=3.0){f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)&&!(f instanceof Ih)))a.bu.TE=f;}return;}a.uf=null;f=a.bu;c=new ASC;HP();d=HFD;i=null;j=new Ba;$p=11;continue _;case 10:$z=GoX(f,c);if(B()){break _;}q=$z;if(q>=o&&o!==0.0){n=n+1|0;if(n<l.g){$p=6;continue _;}if(a.uf!==null&&g){$p=9;continue _;}f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)&&!(f instanceof Ih)))a.bu.TE=f;}return;}$p
=12;continue _;case 11:BsV(j,k);if(B()){break _;}AZR(c,d,k,i,j);f.h4=c;f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)&&!(f instanceof Ih)))a.bu.TE=f;}return;case 12:$z=E1d(m);if(B()){break _;}c=$z;$p=13;case 13:$z=E1d(d);if(B()){break _;}i=$z;if(c!==i){a.uf=m;k=p.pN;o=q;}else if(o===0.0){a.uf=m;k=p.pN;}n=n+1|0;if(n<l.g){$p=6;continue _;}if(a.uf!==null&&g){$p=9;continue _;}f=a.uf;if(f!==null&&!(o>=h&&a.bu.h4!==null)){a.bu.h4=A0d(f,k);f=a.uf;if(!(!(f instanceof Co)
&&!(f instanceof Ih)))a.bu.TE=f;}return;default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,$p);}
function JasprCombatBox(b){var w=(b.cH-b.ct)*JasprBetterCombat.settings().width,x=(b.cH+b.ct)/2,z=(b.cK+b.cz)/2;return BH(x-w,b.bv,z-w,x+w,b.cl,z+w);}
