/* JASPR_COMBATFX engine glue. Hooks (anchors are original client text no other stage replaced):
 *   EntityRenderer.hurtCameraEffect (FN4), at its start: JasprCameraFXApply - the camera kicks, for the world camera and
 *     the first-person hand pass alike (the hand pass is flagged by the renderHand hook so a push back moves only the
 *     world), plus once per frame the impact intents of the melee motion (kicks and sparks);
 *   EntityRenderer.renderHand (Ddz): the hand-pass flag around its hurtCameraEffect call, and after both first-person
 *     hands are drawn (same matrices as the weapons) JasprCombatFXHand - the blade trails.
 * Plain JS on completed renderer fiber boundaries: only GlStateManager, Tessellator, World.spawnParticle and entity
 * reads, none of which can suspend. Any error disables only its own part and is reported once (bounded diagnostics).
 */
function JasprCameraFXApply(a,b){
 try{
  var mc=a.bu,p=mc?mc.v:null;if(!p)return;
  var T=p.cv+b,third=!!(mc.G&&mc.G.lv);
  if(JasprCameraFX.pass===0)JasprCombatFXFrame(mc,T);
  var o=JasprCameraFX.offsets(T,third);if(!o)return;
  if(o.roll)Gc9(o.roll,0.0,0.0,1.0);
  if(o.pitch)Gc9(-o.pitch,1.0,0.0,0.0);
  if(o.yaw)Gc9(o.yaw,0.0,1.0,0.0);
  if(o.back&&JasprCameraFX.pass===0)DPm(0.0,0.0,-o.back);
  JasprCombatFXSeen('camera');
 }catch(e){JasprCameraFX.enabled=false;JasprCombatFXReport('camera',e);}
}
// once per rendered frame: the melee impacts become kicks and sparks at the struck entity
function JasprCombatFXFrame(mc,T){
 var list;
 try{list=JasprCombatFX.frame(T,typeof JasprMeleeMotion!=='undefined'?JasprMeleeMotion:null);}catch(e){JasprCombatFX.fail('kicks',e);JasprCombatFXReport('kicks',e);return;}
 if(!list)return;
 try{
  var w=mc.X,p=mc.v;if(!w||!p)return;
  for(var i=0;i<list.length;i++){
   var e=list[i],t=w.baK(e.target);if(!t||t.ed||!t.bc)continue;
   var bb=t.bc,plan=JasprCombatFX.sparkPlan(e,{x0:bb.ct,y0:bb.bv,z0:bb.cz,x1:bb.cH,y1:bb.cl,z1:bb.cK},[p.b,p.f+1.62,p.c],p.C,p.bd,Math.random,T);
   for(var j=0;j<plan.length;j++){var s=plan[j];Efj(w,s.id,0,s.x,s.y,s.z,s.vx,s.vy,s.vz,Bh(0));}
   if(plan.length)JasprCombatFXSeen('sparks');
  }
 }catch(e){JasprCombatFX.fail('sparks',e);JasprCombatFXReport('sparks',e);}
}
// after both first-person hands: the blade trails, in the hands' own matrices
function JasprCombatFXHand(a,b){
 try{
  if(!JasprCombatFX.on('trails')||typeof JasprMeleeMotion==='undefined'||!JasprMeleeMotion.trail)return;
  var mc=a.bu,p=mc?mc.v:null;if(!p)return;
  if(mc.G&&mc.G.QV===2)return;                                                     // particles: minimal
  if(typeof JasprVideoState!=='undefined'&&JasprVideoState&&JasprVideoState.value('particleEffects')===false)return;   // the video settings' particle switch
  var T=p.cv+b,v=[];
  for(var h=0;h<2;h++)JasprCombatFX.trailVertices(JasprMeleeMotion.trail(h,T),v);
  if(!v.length)return;
  JasprCombatFXDraw(v);JasprCombatFXSeen('trails');
 }catch(e){JasprCombatFX.fail('trails',e);JasprCombatFXReport('trails',e);}
}
// coloured quads (POSITION_COLOR), alpha-blended over the hands, depth-tested but not written; every touched GL state
// is put back the way it was
function JasprCombatFXDraw(v){
 var s={blend:HHV,cull:HHT,lighting:Kri,tex:Krr.data[0],active:HEo,src:Krc&65535,dst:Krd&65535,color:[HKI,HKJ,HKK,HKL],mask:KqX,func:KqW,alpha:Krf};
 try{
  GnI(33984);DCQ();DFk();F1Q();Db2();CyM();Fb_(770,771);EFX(0);CcT(515);
  var t=GdM(),buf=t.dy;C5();Ep0(buf,7,HLn);
  for(var i=0;i<v.length;i+=7){CUb(buf,v[i],v[i+1],v[i+2]);Eip(buf,v[i+3],v[i+4],v[i+5],v[i+6]);E74(buf);}
  FE$(t);
 }finally{
  GnI(33984);if(s.tex)CQ6();else DCQ();
  if(s.lighting)D75();else DFk();
  if(s.cull)Ggy();else F1Q();
  if(s.alpha)D6M();else Db2();
  Fb_(s.src,s.dst);if(s.blend)CyM();else CTP();
  // CcT maps the vanilla constant onto the reversed depth buffer's (515 LEQUAL -> 518); hand back the vanilla one
  EFX(s.mask?1:0);CcT(s.func===518?515:s.func===516?513:s.func===513?516:s.func===515?518:s.func);
  CFh(s.color[0],s.color[1],s.color[2],s.color[3]);GnI(33984+s.active);
 }
}
// ---------------------------------------------------------------- diagnostics (bounded, same-origin, no positions or names)
var JasprCombatFXDiag={sent:0,page:'combatfx-'+Date.now().toString(36),seen:{}};
function JasprCombatFXSend(event,details){
 try{
  var loc=$rt_globals.location;
  if(JasprCombatFXDiag.sent>=12||!loc||String(loc.pathname).indexOf('/jaspercraft/')!==0||typeof $rt_globals.fetch!=='function')return;
  JasprCombatFXDiag.sent++;
  $rt_globals.fetch('/api/diagnostics/events',{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json','X-Jaspergers-Client':'web-v1'},
   body:JSON.stringify({events:[{event:event,pageSessionId:JasprCombatFXDiag.page,at:new Date().toISOString(),details:details}]})}).catch(function(){});
 }catch(e){}
}
function JasprCombatFXSeen(part){
 if(JasprCombatFXDiag.seen[part])return;JasprCombatFXDiag.seen[part]=true;
 JasprCombatFXSend('jaspercraft.combatfx.state',{part:part,state:'active'});
}
function JasprCombatFXReport(part,e){
 var msg=String(e&&e.message||e).replace(/[\r\n]+/g,' ').slice(0,180);
 if($rt_globals.console)$rt_globals.console.warn('[JasperCraft combat FX] '+part+': '+msg);
 JasprCombatFXSend('jaspercraft.combatfx.error',{part:String(part).slice(0,20),error:msg});
}
$rt_globals.JasprCombatFXDiagnostics=Object.freeze({status:function(){return {camera:JasprCameraFX.status(),fx:JasprCombatFX.status(),sent:JasprCombatFXDiag.sent};}});
