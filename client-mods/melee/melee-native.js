/* Native TeaVM bridges for the first-person melee motion (TeaVM fiber functions: item lookups and matrix calls may
 * suspend, so these keep the engine's own resumable state machines). Personal, server-gated: only while JasperCraft
 * BetterCombat is active, its "melee animations" option is on and the hand's attacks are enabled. Never driven by a
 * JS timer and never changes attack energy, damage, reach or packets. The third-person body is not touched here:
 * third-person attack animation belongs to Mo' Bends.
 */
// Vanilla lowers the main grip with attackStrength^3, which would hide most of a swing below the screen. Native equip
// transitions stay for real item, model or NBT changes, but not for the server's replacement stack of the same weapon.
function JasprEpicEquipFull(a,b){
 var s=JasprBetterCombat.settings();
 return JasprBetterCombat.active(a.kD)&&s&&s.motion&&s.main&&b===a.bFQ&&JasprMeleeMotion.family(s.mainItem);
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
// The item's motion family: vanilla id, refined by real JasprApocalypse custom-item metadata (cached per stack).
function JasprMeleeDescriptor(a){
 var b,c,d,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:if(!a||!a.rA||a.bg_)return JasprMeleeMotion.describe(0,null);
  b=JasprMeleeMotion.descriptors.get(a);if(b&&b.tag===a.bV&&b.damage===a.bK&&b.item===a.rA)return b.value;$p=1;
 case 1:$z=C_q(a.rA);if(B())break _;c=$z;if(!a.bV||!JasprMeleeMotion.family(c)){d=JasprMeleeMotion.describe(c,null);$p=3;continue _;}$p=2;
 case 2:$z=Ent(a.bV);if(B())break _;d=JasprMeleeMotion.describe(c,$rt_ustr($z).slice(0,8192));$p=3;
 case 3:JasprMeleeMotion.descriptors.set(a,{tag:a.bV,damage:a.bK,item:a.rA,value:d});return d;
 default:FT();}}Ds().s(a,b,c,d,$p);
}
// The pose for this hand's current melee attack, or null to keep the vanilla first-person path.
function JasprEpicPose(a,b,c,d,e){
 var f,g,h,$p=0,$z;
 if(FX()){var $T=Ds();$p=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}
 _:while(true){switch($p){
 case 0:if(!JasprBetterCombat.active(a.kD)||!JasprBetterCombat.settings().motion||!(e?JasprBetterCombat.settings().main:JasprBetterCombat.settings().off))return null;$p=1;
 case 1:$z=JasprCombatSpecial(d);if(B())break _;if($z)return null;$p=2;
 case 2:$z=JasprMeleeDescriptor(d);if(B())break _;f=$z;g=JasprMeleeMotion.restyle(JasprMeleeMotion.current(e,f.id,Date.now()),f);if(!g)return null;
  h=JasprMeleeMotion.phase(g,a.kD.v?a.kD.v.cv+JasprMeleeMotion.partial:NaN);if(h===null)h=c;if(h<0||h>=1)return null;
  return {pose:JasprMeleeMotion.sample(g,h,b===Kua),main:e,id:f.id,sequence:g.sequence};
 default:FT();}}Ds().s(a,b,c,d,e,f,g,h,$p);
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
// Unarmed attacks keep the vanilla arm until a fist motion exists; the hook stays so the arm path is unchanged.
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
