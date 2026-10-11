/* Native TeaVM bridges for the first-person melee motion v5 (TeaVM fiber functions: item lookups and attribute reads may
 * suspend, so these keep the engine's own resumable state machines). Personal and server-gated: only while JasperCraft
 * Better Combat is active, its "melee animations" option is on and the hand's attacks are enabled. Never driven by a JS
 * timer, never changes attack energy, damage, reach or packets. The third-person body is Mo' Bends' job.
 * Signatures are fixed by the engine call sites (ItemRenderer Dk0 / C45) and JasprCombatTransform.
 */
// Vanilla lowers the main grip with attackStrength^3, which would hide most of a swing below the screen. Native equip
// transitions stay for real item, model or NBT changes, but not for the server's replacement stack of the same weapon.
function JasprEpicEquipFull(a,b){
 var s=JasprBetterCombat.settings();
 return JasprBetterCombat.active(a.kD)&&s&&s.motion&&s.main&&b===a.bFQ&&JasprMeleeMotion.equipFull(s.mainItem);
}
function JasprEpicEquipMatch(a,b,c,d){
 var e,f,g,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:$p=1;
 case 1:$z=HhL(b,c);if(B())break _;e=$z;if(e)return e;
  f=JasprBetterCombat.settings();if(!JasprBetterCombat.active(a.kD)||!f||!f.motion||!(d?f.main:f.off)||!b||!c||!b.rA||b.rA!==c.rA||b.PD!==c.PD)return e;$p=2;
 case 2:$z=C_q(c.rA);if(B())break _;f=$z;if(!JasprMeleeMotion.family(f))return e;$p=3;
 case 3:$z=HhL(b.bV,c.bV);if(B())break _;g=$z;if(!g)return e;
  if(b.bK!==c.bK&&b.bV){$p=4;continue _;}$p=5;continue _;
 case 4:$z=DaN(b.bV,$rt_str('Unbreakable'));if(B())break _;g=$z;if(g)return e;$p=5;
 case 5:if(d)a.bFQ=c;else a.bpO=c;return 1;
 default:FT();}}Ds().s(a,b,c,d,e,f,g,$p);
}
// The item's weapon class: id, damage and NBT text of the real stack (cached per stack object).
function JasprMeleeDescriptor(a){
 var b,c,d,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:if(!a||!a.rA||a.bg_)return JasprMeleeMotion.describe(0,null,0);
  b=JasprMeleeMotion.descriptors.get(a);if(b&&b.tag===a.bV&&b.damage===a.bK&&b.item===a.rA)return b.value;$p=1;
 case 1:$z=C_q(a.rA);if(B())break _;c=$z;if(!a.bV){d=JasprMeleeMotion.describe(c,null,a.bK);$p=3;continue _;}$p=2;
 case 2:$z=Ent(a.bV);if(B())break _;d=JasprMeleeMotion.describe(c,$rt_ustr($z).slice(0,8192),a.bK);$p=3;
 case 3:JasprMeleeMotion.descriptors.set(a,{tag:a.bV,damage:a.bK,item:a.rA,value:d});return d;
 default:FT();}}Ds().s(a,b,c,d,$p);
}
// The off hand holds a weapon that attacks: the head's live check (bettercombat9 offReady) or the server's flag.
function JasprMeleeOffReady(g){return typeof JasprBetterCombat.offReady==='function'?!!JasprBetterCombat.offReady():!!(g&&g.off);}
// The pose for this hand (a swing of its own, or its answer to the other hand's swing), or null for the vanilla path.
// Once per frame it also samples what the motion needs from the player: the main hand's attack energy and cooldown
// period, whether the player is sprinting or falling (sprint and jump variants).
function JasprEpicPose(a,b,c,d,e){
 var f,g,h,i,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:g=JasprBetterCombat.settings();if(!JasprBetterCombat.active(a.kD)||!g.motion||!(e?g.main:JasprMeleeOffReady(g))||!JasprMeleeMotion.knobs.motion)return null;$p=1;
 case 1:$z=JasprCombatSpecial(d);if(B())break _;if($z){JasprMeleeMotion.bind(e?0:1,null,b===Kua,Date.now(),JasprMeleeOffReady(g));return null;}$p=2;
 case 2:$z=JasprMeleeDescriptor(d);if(B())break _;f=$z;h=a.kD.v;if(!h)return null;i=h.cv+JasprMeleeMotion.partial;
  if(!e||JasprMeleeMotion.sampledAt===i){$p=5;continue _;}JasprMeleeMotion.sampledAt=i;$p=3;
 case 3:$z=FiS(h);if(B())break _;JasprMeleeMotion.sample(0,(h.bDd+JasprMeleeMotion.partial)/Math.max(1,$z),$z);$p=4;
 case 4:$z=EuW(h,3);if(B())break _;JasprMeleeMotion.samplePlayer($z,h.bQ,h.p<0,Date.now());$p=5;
 case 5:if(!e)JasprMeleeMotion.sample(1,1-JasprBetterCombat.remaining()/Math.max(1,g.period),g.period);
  JasprMeleeMotion.bind(e?0:1,f,b===Kua,Date.now(),JasprMeleeOffReady(g));JasprMeleeMotion.frame(i,Date.now());
  h=JasprMeleeMotion.poseFor(e?0:1,i,b===Kua);if(!h)return null;
  return {pose:h,main:e,id:f.id,sequence:JasprMeleeMotion.sequence(e?0:1)};
 default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,$p);
}
// Cancels only the classic swing's bob offset while a melee pose is drawn; the native grip/equip/sneak transform stays.
function JasprEpicSwingOffset(a,b,c,d,e,f,g,h){
 var i,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:$p=1;
 case 1:$z=JasprEpicPose(a,b,c,d,e);if(B())break _;i=$z;if(i)return;$p=2;
 case 2:DPm(f,g,h);if(B())break _;return;
 default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,$p);
}
// Unarmed attacks keep the vanilla arm (fists have no first-person melee motion); the hook keeps the arm path intact.
function JasprMeleeEmptyArm(a,b,c,d){
 var e,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:$p=1;
 case 1:$z=JasprEpicPose(a,d,c,null,true);if(B())break _;e=$z;if(!e){$p=5;continue _;}
  JasprMeleeMotion.record(e.pose,true,0,e.sequence);JasprBetterCombat.visual.motionDraws++;$p=2;
 case 2:DPm(e.pose.tx,e.pose.ty,e.pose.tz);if(B())break _;$p=3;
 case 3:Gc9(e.pose.rotation.angle,e.pose.rotation.x,e.pose.rotation.y,e.pose.rotation.z);if(B())break _;$p=4;
 case 4:FMS(a,b,0,d);if(B())break _;return;
 case 5:FMS(a,b,c,d);if(B())break _;return;
 default:FT();}}Ds().s(a,b,c,d,e,$p);
}
// Read-only diagnostics of the first-person melee module (counters, current classes and moves; no positions or names).
$rt_globals.JasprMeleeDiagnostics=Object.freeze({status:function(){try{return JasprMeleeMotion.status();}catch(e){return {error:String(e&&e.message||e).slice(0,180)};}}});
