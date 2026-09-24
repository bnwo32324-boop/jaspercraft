'use strict';
// Final reversible stage after gore + biome presentation. Candidate output ONLY.
// This module does not import either earlier builder: their keep/reapply path stays acyclic.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),vm=require('node:vm');
const creative=require('./creative-catalog.cjs');
const root=path.resolve(__dirname,'..');
// The original base remains the public stage identity; the current biome input
// is accepted during this one-stage migration so old and new candidates remain
// reversible and hash-pinned.
const BASE='fd93f793135fcf3ef05ad3b0b824aaba4a38caa3edd50167c8dc9f338caff4b0',CURRENT_BASE='5d2da1368a6c887a17f692f86d4aff80b3765e364ff5dcaada4e5b82931d16b2';
const acceptedBase=hash=>hash===BASE||hash===CURRENT_BASE;
const begin='/* JASPR_STATS_KEYBIND_BEGIN */',end='/* JASPR_STATS_KEYBIND_END */';
const SKIP_IF_ABSENT=true;
const hooks=[
  ['B$i','a.ni=4;$p=38;case 38:DBw(a);','a.ni=4;$p=40;case 40:JasprStatsInstall(a);if(B()){break _;}$p=38;case 38:DBw(a);'],
  ['GWe','case 0:d=LnF.bbE;','case 0:if(b!==null&&b===JasprStatsKeyDescription)return JasprStatsKeyLabel;d=LnF.bbE;'],
  ['CFB','case 0:b=AQp()?AQp():BsJ()+256|0;','case 0:b=AQp()?AQp():BsJ()+256|0;JasprStatsBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);'],
  ['Dka','c=A$O();b=c-100|0;d=W6();$p=2;','c=A$O();b=c-100|0;d=W6();if(c>=0)JasprStatsBridge.key(a,b,d,false);$p=2;'],
  ['DRw','case 0:b=a.G.ckx;','case 0:$p=49;case 49:JasprStatsTick(a);if(B()){break _;}b=a.G.ckx;'],
  ['GGw','case 0:c=a.cj;','case 0:JasprStatsBridge.invalidate(a);c=a.cj;'],
  ['Emm','case 0:if(!a.uE)return;','case 0:JasprStatsBridge.invalidate(a);if(!a.uE)return;'],
  ['Gsc','case 0:JasprGoreBridge.reset();','case 0:JasprStatsBridge.invalidate(a);JasprGoreBridge.reset();'],
  // Waypoint additions are separate entries so earlier stages keep reversing exactly.
  ['B$i','$p=40;case 40:','$p=40;case 40:JasprWaypointInstall(a);if(B()){break _;}',SKIP_IF_ABSENT],
  ['GWe','case 0:if(b!==null&&b===JasprStatsKeyDescription)','case 0:if(b!==null&&b===JasprWaypointKeyDescription)return JasprWaypointKeyLabel;if(b!==null&&b===JasprStatsKeyDescription)',SKIP_IF_ABSENT],
  ['CFB','JasprStatsBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);','JasprStatsBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprWaypointBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);',SKIP_IF_ABSENT],
  ['Dka','if(c>=0)JasprStatsBridge.key(a,b,d,false);','if(c>=0)JasprStatsBridge.key(a,b,d,false);if(c>=0)JasprWaypointBridge.key(a,b,d,false);',SKIP_IF_ABSENT],
  ['DRw','case 49:JasprStatsTick(a);if(B()){break _;}','case 49:JasprStatsTick(a);if(B()){break _;}JasprWaypointTick(a);if(B()){break _;}',SKIP_IF_ABSENT],
  ['GGw','case 0:JasprStatsBridge.invalidate(a);c=a.cj;','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);c=a.cj;',SKIP_IF_ABSENT],
  ['Emm','case 0:JasprStatsBridge.invalidate(a);if(!a.uE)return;','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);if(!a.uE)return;',SKIP_IF_ABSENT],
  ['Gsc','case 0:JasprStatsBridge.invalidate(a);JasprGoreBridge.reset();','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointMarkers.reset();JasprGoreBridge.reset();',SKIP_IF_ABSENT],
  ['DbP','case 42:JasprGoreBridge.render();GdY(a);','case 42:JasprWaypointMarkers.draw(a);JasprGoreBridge.render();GdY(a);',SKIP_IF_ABSENT],
  ['DbP','case 42:JasprWaypointMarkers.draw(a);JasprGoreBridge.render();GdY(a);','case 42:JasprWaypointMarkers.draw(a);JasprGoreBridge.render();GdY(a);JasprDynamicLightsFrame(a);if(B()){break _;}',SKIP_IF_ABSENT],
  // Dynamic lights: DtH is RegionRenderCache.getCombinedLight, the sole mesher
  // sampler (CsD flat + EpH smooth). Renamed by renameSampler below, not by the
  // generic hooks: after the adapter strip neither its from-text (shadowed by
  // the wrapper) nor a stable anchor exists for the shared skip logic.
  ['B$i','case 40:JasprWaypointInstall(a);if(B()){break _;}','case 40:JasprWaypointInstall(a);if(B()){break _;}JasprDynamicLightsInstall(a);if(B()){break _;}',SKIP_IF_ABSENT],
  ['GWe','case 0:if(b!==null&&b===JasprWaypointKeyDescription)','case 0:if(b!==null&&b===JasprDynamicLightsKeyDescription)return JasprDynamicLightsKeyLabel;if(b!==null&&b===JasprWaypointKeyDescription)',SKIP_IF_ABSENT],
  ['CFB','JasprWaypointBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);','JasprWaypointBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprDynamicLightsKey.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);',SKIP_IF_ABSENT],
  ['Dka','if(c>=0)JasprWaypointBridge.key(a,b,d,false);','if(c>=0)JasprWaypointBridge.key(a,b,d,false);if(c>=0)JasprDynamicLightsKey.key(a,b,d,false);',SKIP_IF_ABSENT],
  ['Gsc','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointMarkers.reset();JasprGoreBridge.reset();','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointMarkers.reset();JasprDynamicLights.reset();JasprGoreBridge.reset();',SKIP_IF_ABSENT],
  // Tab-held waypoint readout: edges tracked at the input poll (existing Tab
  // binding, no new key), heartbeat + clear on the input tick, beams gated in
  // the render pass. Same fiber/reversibility discipline as every stage.
  ['CFB','JasprWaypointBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprDynamicLightsKey.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);','JasprWaypointBridge.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprDynamicLightsKey.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprWaypointTabPoll(a,b,ANI(),HFV!==null&&HFV.cX2===2);',SKIP_IF_ABSENT],
  ['Dka','if(c>=0)JasprWaypointBridge.key(a,b,d,false);if(c>=0)JasprDynamicLightsKey.key(a,b,d,false);','if(c>=0)JasprWaypointBridge.key(a,b,d,false);if(c>=0)JasprDynamicLightsKey.key(a,b,d,false);if(c>=0)JasprWaypointTabPoll(a,b,d,false);',SKIP_IF_ABSENT],
  ['DRw','case 49:JasprStatsTick(a);if(B()){break _;}JasprWaypointTick(a);if(B()){break _;}','case 49:JasprStatsTick(a);if(B()){break _;}JasprWaypointTick(a);if(B()){break _;}JasprWaypointTabTick(a);if(B()){break _;}',SKIP_IF_ABSENT],
  ['GGw','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);c=a.cj;','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointTab.invalidate(a);c=a.cj;',SKIP_IF_ABSENT],
  ['Emm','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);if(!a.uE)return;','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointTab.invalidate(a);if(!a.uE)return;',SKIP_IF_ABSENT],
  ['Gsc','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointMarkers.reset();JasprDynamicLights.reset();JasprGoreBridge.reset();','case 0:JasprStatsBridge.invalidate(a);JasprWaypointBridge.invalidate(a);JasprWaypointTab.invalidate(a);JasprWaypointMarkers.reset();JasprDynamicLights.reset();JasprGoreBridge.reset();',SKIP_IF_ABSENT],
  ['DbP','case 42:JasprWaypointMarkers.draw(a);JasprGoreBridge.render();GdY(a);JasprDynamicLightsFrame(a);if(B()){break _;}','case 42:if(JasprWaypointTabHeld(a))JasprWaypointMarkers.draw(a);JasprGoreBridge.render();GdY(a);JasprDynamicLightsFrame(a);if(B()){break _;}',SKIP_IF_ABSENT],
  // Shader packs: the selector is a native Video Settings scroll row, and
  // opens a native picker screen instead of cycling through packs in place.
  // World-pass reroute (Fjm single pass) keeps the graded blit unchanged.
  ['DaB','d=new B3;e=200;f=(a.q/2|0)-100|0;g=a.L-27|0;','d=new B3;e=200;f=(a.q/2|0)-205|0;g=a.L-27|0;',SKIP_IF_ABSENT],
  ['DaB','case 5:Y(b,d);if(B()){break _;}b=new Bpd;','case 5:Y(b,d);if(B()){break _;}d=new B3;e=901;f=(a.q/2|0)+5|0;g=a.L-27|0;h=JasprShaders.label();$p=10;continue _;case 10:B4K(d,e,f,g,h);if(B()){break _;}$p=11;continue _;case 11:Y(b,d);if(B()){break _;}b=new Bpd;',SKIP_IF_ABSENT],
  ['CeF','case 0:if(b.bS&&b.bF==200){','case 0:if(b.bS&&b.bF===901){JasprShaders.click(b);return;}if(b.bS&&b.bF==200){',SKIP_IF_ABSENT],
  // Remove the former fixed bottom row (including the old split-row variant)
  // before laying the selector into GuiOptionsRowList.
  ['DaB','d=new B3;e=200;f=(a.q/2|0)-205|0;g=a.L-27|0;','d=new B3;e=200;f=(a.q/2|0)-100|0;g=a.L-27|0;',SKIP_IF_ABSENT],
  ['DaB','case 5:Y(b,d);if(B()){break _;}d=new B3;e=901;f=(a.q/2|0)+5|0;g=a.L-27|0;h=JasprShaders.label();$p=10;continue _;case 10:B4K(d,e,f,g,h);if(B()){break _;}$p=11;continue _;case 11:Y(b,d);if(B()){break _;}b=new Bpd;','case 5:Y(b,d);if(B()){break _;}b=new Bpd;',SKIP_IF_ABSENT],
  // Reuse the native GuiVideoSettings screen as a shader-pack picker. The
  // mode flag is read while DaB builds the same scrollable row list.
  ['DaB','a.cGa=b;b=a.be;','a.cGa=JasprShadersPickerMode?JasprShadersPickerTitle():b;b=a.be;',SKIP_IF_ABSENT],
  ['DaB','f=0;j=c.data;e=j.length;if(f>=e){','f=0;j=c.data;e=JasprShadersPickerMode?4:j.length;if(f>=e){',SKIP_IF_ABSENT],
  ['DaB','f=f+2|0;j=c.data;e=j.length;if(f>=e){a.bGZ\r\n=b;return;}','f=f+2|0;j=c.data;e=JasprShadersPickerMode?4:j.length;if(f>=e){if(JasprShadersPickerMode){a.bGZ\r\n=b;return;}$p=22;continue _;}',SKIP_IF_ABSENT],
  ['DaB','case 6:$z=CEU(b,d,g,e,h);if(B()){break _;}l=$z;e=g+160|0;g=0;$p=7;','case 6:if(JasprShadersPickerMode){$p=20;continue _;}$z=CEU(b,d,g,e,h);if(B()){break _;}l=$z;e=g+160|0;g=0;$p=7;continue _;case 20:$z=JasprShadersPickerButton(910+(f|0),g,JasprShadersPickerLabel(f));if(B()){break _;}l=$z;e=g+160|0;g=0;$p=7;continue _;',SKIP_IF_ABSENT],
  ['DaB','case 7:$z=CEU(b,d,e,g,k);if(B()){break _;}k=$z;m=b.csQ;n=new Bv1;$p=8;','case 7:if(JasprShadersPickerMode){$p=21;continue _;}$z=CEU(b,d,e,g,k);if(B()){break _;}k=$z;m=b.csQ;n=new Bv1;$p=8;continue _;case 21:$z=JasprShadersPickerButton(911+(f|0),e,JasprShadersPickerLabel(f+1));if(B()){break _;}k=$z;m=b.csQ;n=new Bv1;$p=8;continue _;',SKIP_IF_ABSENT],
  // Picker migration: retain the v5 hooks above so an already deployed
  // candidate reverses cleanly, then expand its list for Sildur's entry.
  ['DaB','f=0;j=c.data;e=JasprShadersPickerMode?4:j.length;if(f>=e){','f=0;j=c.data;e=JasprShadersPickerMode?SHADER_PACK_COUNT:j.length;if(f>=e){',SKIP_IF_ABSENT],
  ['DaB','f=f+2|0;j=c.data;e=JasprShadersPickerMode?4:j.length;if(f>=e){if(JasprShadersPickerMode){a.bGZ\r\n=b;return;}$p=22;continue _;}','f=f+2|0;j=c.data;e=JasprShadersPickerMode?SHADER_PACK_COUNT:j.length;if(f>=e){if(JasprShadersPickerMode){a.bGZ\r\n=b;return;}$p=22;continue _;}',SKIP_IF_ABSENT],
  ['DaB','case 21:$z=JasprShadersPickerButton(911+(f|0),e,JasprShadersPickerLabel(f+1));if(B()){break _;}k=$z;m=b.csQ;n=new Bv1;$p=8;continue _;','case 21:if((f+1|0)>=SHADER_PACK_COUNT){$p=8;continue _;}$z=JasprShadersPickerButton(911+(f|0),e,JasprShadersPickerLabel(f+1));if(B()){break _;}k=$z;m=b.csQ;n=new Bv1;$p=8;continue _;',SKIP_IF_ABSENT],
  ['DaB','default:FT();}}','case 22:$z=JasprShadersAppendVideoRow(a,b,i);if(B()){break _;}return;default:FT();}}',SKIP_IF_ABSENT],
  ['CeF','case 0:if(b.bS&&b.bF===901){JasprShaders.click(b);return;}if(b.bS&&b.bF==200){','case 0:if(b.bS&&b.bF===901){JasprShaders.click(b);return;}if(b.bS&&b.bF==200){JasprShadersPickerMode=0;',SKIP_IF_ABSENT],
  ['Cay','h=a.bEa;if(!(h instanceof I3))return 1;i=a.Wy.G;h=h.bsy;b=1;$p=3;continue _;','h=a.bEa;if(h.bF===901){i=a.Wy;$p=7;continue _;}if(h.bF>=910&&h.bF<=913){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h=h.bsy;b=1;$p=3;continue _;',SKIP_IF_ABSENT],
  ['Cay','h=a.bdH;if(!(h instanceof I3))return 1;i=a.Wy.G;h','h=a.bdH;if(h.bF>=910&&h.bF<=913){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h',SKIP_IF_ABSENT],
  ['Cay','case 5:FXE(i,h,b);if(B()){break _;}h=a.bdH;i=a.Wy.G;j=AOX(h.bF);$p=6;case 6:$z=GyG(i,j);if(B()){break _;}i=$z;h.dd=i;return 1;default:FT();}}','case 5:FXE(i,h,b);if(B()){break _;}h=a.bdH;i=a.Wy.G;j=AOX(h.bF);$p=6;case 6:$z=GyG(i,j);if(B()){break _;}i=$z;h.dd=i;return 1;case 7:JasprShadersPickerMode=1;i=a.Wy;j=new A$Z;$p=9;continue _;case 9:He_();if(B()){break _;}$p=10;continue _;case 10:BGm(j);if(B()){break _;}$p=11;continue _;case 11:j.cGa=JasprShadersPickerTitle();j.dKU=i.cj;if(i.cj!==null)j.bPI=i.cj.bPI;GGw(i,j);if(B()){break _;}return 1;case 8:JasprShaders.choose(h.bF-910);JasprShadersPickerMode=0;j=i.cj;if(j===null)return 1;h=j.dKU;if(h===null)return 1;$p=12;continue _;case 12:GGw(i,h);if(B()){break _;}return 1;default:FT();}}',SKIP_IF_ABSENT],
  // The v5 picker accepted IDs through 913. Keep that hook above for clean
  // rollback, then widen the hit-test range for Sildur's ID 914.
  ['Cay','h=a.bEa;if(h.bF===901){i=a.Wy;$p=7;continue _;}if(h.bF>=910&&h.bF<=913){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h=h.bsy;b=1;$p=3;continue _;','h=a.bEa;if(h.bF===901){i=a.Wy;$p=7;continue _;}if(h.bF>=910&&h.bF<=914){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h=h.bsy;b=1;$p=3;continue _;',SKIP_IF_ABSENT],
  ['Cay','h=a.bdH;if(h.bF>=910&&h.bF<=913){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h','h=a.bdH;if(h.bF>=910&&h.bF<=914){i=a.Wy;$p=8;continue _;}if(!(h instanceof I3))return 1;i=a.Wy.G;h',SKIP_IF_ABSENT],
  // Two more packs, and the picker builds its own rows. The native two-column
  // loop pairs options per row and carries the pair through a fiber that may
  // suspend between columns, which is what duplicated one pack and dropped
  // another; JasprShadersBuildPicker lays out one pack per row instead.
  ['Cay','h=a.bEa;if(h.bF===901){i=a.Wy;$p=7;continue _;}if(h.bF>=910&&h.bF<=914){i=a.Wy;$p=8;continue _;}','h=a.bEa;if(h.bF===901){i=a.Wy;$p=7;continue _;}if(h.bF>=910&&h.bF<=916){i=a.Wy;$p=8;continue _;}',SKIP_IF_ABSENT],
  ['Cay','h=a.bdH;if(h.bF>=910&&h.bF<=914){i=a.Wy;$p=8;continue _;}','h=a.bdH;if(h!==null&&h.bF>=910&&h.bF<=916){i=a.Wy;$p=8;continue _;}',SKIP_IF_ABSENT],
  ['DaB','b.csQ=Bq();b.ctz=0;f=0;','b.csQ=Bq();b.ctz=0;if(JasprShadersPickerMode){$p=23;continue _;}f=0;',SKIP_IF_ABSENT],
  ['DaB','case 22:$z=JasprShadersAppendVideoRow(a,b,i);if(B()){break _;}return;default:FT();}}','case 22:$z=JasprShadersAppendVideoRow(a,b,i);if(B()){break _;}return;case 23:$z=JasprShadersBuildPicker(a,b,i);if(B()){break _;}return;default:FT();}}',SKIP_IF_ABSENT],
  ['Fjm','case 7:Ctb(a,f,b,c);if(B()){break _;}return;','case 7:JasprShadersPass(a,f,b,c);if(B()){break _;}return;',SKIP_IF_ABSENT],
  // With shaders OFF, stay entirely on the native fiber path. The shader
  // adapter is entered only after the enabled flag is true.
  ['Fjm','case 7:JasprShadersPass(a,f,b,c);if(B()){break _;}return;','case 7:if(!JasprShadersEnabled){Ctb(a,f,b,c);if(B()){break _;}return;}JasprShadersPass(a,f,b,c);if(B()){break _;}return;',SKIP_IF_ABSENT],
  // Vanilla 1.12.2 multiplies every source by MASTER and then by its own category.
  // This port skipped MASTER for every non-zero value, reducing its slider to mute/on.
  ['FyU','case 0:c=Mx(b);d=b.yo;','case 0:c=Mx(b);d=b.yo;e=a.U7;$p=4;continue _;case 4:$z=D9S(e,Lnd);if(B()){break _;}e=$z;c=c*e;'],
  ['Coc','case 5:b.eee(e);if(B()){break _;}e=KWh;','case 5:b.eee(e);if(B()){break _;}$p=29;case 29:JasprCreativeAppend(b,e,0);if(B()){break _;}e=KWh;'],
  ['E1w','case 7:E4J(b,f);if(B()){break _;}return;','case 7:$p=12;case 12:JasprCreativeAppend(a.za.cA,b.v2,1);if(B()){break _;}$p=13;case 13:E4J(b,f);if(B()){break _;}return;'],
  // FQM is the native Tab-list ping-bar helper. Its e.bzW value is the
  // response-time value already used by vanilla; the adapter only draws the
  // matching numeric label beside the unchanged bars.
  ['FQM','a.dz=a.dz-100.0;return;','a.dz=a.dz-100.0;JasprPingOverlay.draw(a,b,c,d,e);return;']
];
function sha(s){return crypto.createHash('sha256').update(s).digest('hex');}
function contains(s){return s.includes(begin);}
function renameSampler(s,reverse){
  // DtH is RegionRenderCache.getCombinedLight (chunk mesher); DQP is
  // World.getCombinedLight (entities, particles, hand, drops, tile entities).
  // GyZ rebuilds the 16x16 lightmap texture; GmS computes fog color.
  const pairs=reverse?[['function DtH_orig(','function DtH('],['function DQP_orig(','function DQP('],['function GyZ_orig(','function GyZ('],['function GmS_orig(','function GmS(']]
    :[['function DtH(','function DtH_orig('],['function DQP(','function DQP_orig('],['function GyZ(','function GyZ_orig('],['function GmS(','function GmS_orig(']];
  for(const [from,to]of pairs){
    if(reverse&&s.indexOf(from)<0)return s;
    const first=s.indexOf(from);
    if(first<0||s.indexOf(from,first+1)>=0)throw Error('Light sampler anchor changed: re-audit dynamic lights before building ('+from+')');
    s=s.slice(0,first)+to+s.slice(first+from.length);
  }
  return s;
}
function nativeFunction(s,name){
  const token='function '+name+'(',at=s.indexOf(token),to=s.indexOf('\nfunction ',at+token.length);
  if(at<0||to<0||s.indexOf(token,at+1)!==-1)throw Error('Missing/duplicate native function '+name);
  return {at,to,body:s.slice(at,to)};
}
function replaceIn(s,name,from,to){
  const f=nativeFunction(s,name),count=f.body.split(from).length-1;
  if(count!==1)throw Error(name+': expected 1 stats hook anchor, got '+count);
  return s.slice(0,f.at)+f.body.replace(from,to)+s.slice(f.to);
}
function unpatch(s){
  if(!contains(s)){
    if(s.includes(end))throw Error('Orphaned stats keybind footer');
    return s;
  }
  if(s.split(begin).length!==2||s.split(end).length!==2)throw Error('Incomplete/duplicate stats keybind extension');
  const legacyCreative=!s.includes('var JasprCreativeCatalog=');
  const a=s.indexOf(begin),b=s.indexOf(end,a);
  if(b<a)throw Error('Incomplete stats keybind extension');
  s=s.slice(0,a)+s.slice(b+end.length);
  s=renameSampler(s,true);
  // The currently deployed shader stage used a fixed row above Done. Remove
  // that legacy row before any newer DaB picker hooks are examined, otherwise
  // a later hook can mistake the old state-machine shape for a partial stage.
  try{
    const f0=nativeFunction(s,'DaB').body;
    const legacy='case 5:Y(b,d);if(B()){break _;}d=new B3;e=901;f=(a.q/2|0)-100|0;g=a.L-52|0;h=JasprShaders.label();$p=10;continue _;case 10:B4K(d,e,f,g,h);if(B()){break _;}$p=11;continue _;case 11:Y(b,d);if(B()){break _;}b=new Bpd;';
    const nativeRow='case 5:Y(b,d);if(B()){break _;}b=new Bpd;';
    if(f0.split(legacy).length===2)s=replaceIn(s,'DaB',legacy,nativeRow);
  }catch(e){/* fall through to the normal reversible hooks */}
  for(const [name,from,to,skipAbsent]of [...hooks].reverse()){
    if(skipAbsent){
      try{
        const f=nativeFunction(s,name).body;
        if(!f.includes(to)&&f.split(from).length===2)continue;
      }catch(e){continue;}
    }
    // One-time migration from the deployed stats/audio stage, before Creative
    // catalogue hooks were part of this same final reversible layer.
    if(legacyCreative&&(name==='Coc'||name==='E1w')){
      const f=nativeFunction(s,name).body;
      if(!f.includes(to)&&f.split(from).length===2)continue;
    }
    // Accept the deployed v1 stats stage once so it can migrate to v2. All v2
    // candidates still have to reverse exactly, including the MASTER gain hook.
    if(name==='FyU'){
      const f=nativeFunction(s,name).body;
      if(!f.includes(to)&&f.split(from).length===2)continue;
    }
    // The numeric Tab ping label was added after the currently deployed stats
    // stage. Accept that older stage once during migration.
    if(name==='FQM'){
      const f=nativeFunction(s,name).body;
      if(!f.includes(to)&&f.split(from).length===2)continue;
    }
    // The dlights10 shader row sat full-width above Done; the split bottom
    // row replaced it. Accept that deployed form once during migration.
    if(name==='DaB'&&from==='case 5:Y(b,d);if(B()){break _;}b=new Bpd;'){
      try {
        const f0=nativeFunction(s,name).body;
        if(!f0.includes(to)){
          const legacy='case 5:Y(b,d);if(B()){break _;}d=new B3;e=901;f=(a.q/2|0)-100|0;g=a.L-52|0;h=JasprShaders.label();$p=10;continue _;case 10:B4K(d,e,f,g,h);if(B()){break _;}$p=11;continue _;case 11:Y(b,d);if(B()){break _;}b=new Bpd;';
          if(f0.split(legacy).length===2){
            s=replaceIn(s,name,legacy,from);
            continue;
          }
        }
      }catch(e){/* fall through to normal handling */}
    }
    s=replaceIn(s,name,to,from);
  }
  return s;
}
function build(s){
  s=unpatch(s);
  if(!acceptedBase(sha(s)))throw Error('Native client changed: re-audit stats adapter before building. Got '+sha(s));
  s=renameSampler(s,false);
  for(const [name,from,to]of hooks)s=replaceIn(s,name,from,to);
  const runtime=fs.readFileSync(path.join(root,'client-mods/stats-keybind.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const adapter=fs.readFileSync(path.join(root,'client-mods/stats-keybind-teavm.js'),'utf8');
  const creativeAdapter=fs.readFileSync(path.join(root,'client-mods/creative-items-teavm.js'),'utf8');
  const pingAdapter=fs.readFileSync(path.join(root,'client-mods/ping-overlay-teavm.js'),'utf8');
  const waypointRuntime=fs.readFileSync(path.join(root,'client-mods/waypoint-keybind.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const waypointAdapter=fs.readFileSync(path.join(root,'client-mods/waypoint-keybind-teavm.js'),'utf8');
  const waypointCodec=fs.readFileSync(path.join(root,'client-mods/waypoint-codec.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const waypointMarkersRuntime=fs.readFileSync(path.join(root,'client-mods/waypoint-markers.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const waypointMarkers=fs.readFileSync(path.join(root,'client-mods/waypoint-markers-teavm.js'),'utf8');
  const waypointTabRuntime=fs.readFileSync(path.join(root,'client-mods/waypoint-tab.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const waypointTab=fs.readFileSync(path.join(root,'client-mods/waypoint-tab-teavm.js'),'utf8');
  const dynamicLightsRuntime=fs.readFileSync(path.join(root,'client-mods/dynamic-lights.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const dynamicLights=fs.readFileSync(path.join(root,'client-mods/dynamic-lights-teavm.js'),'utf8');
  const shaderPacksRuntime=fs.readFileSync(path.join(root,'client-mods/shader-packs.js'),'utf8')
    .replace(/\nif \(typeof module[^\n]+\n?$/,'\n');
  const shaderPacks=fs.readFileSync(path.join(root,'client-mods/shader-packs-teavm.js'),'utf8');
  const greenWater=fs.readFileSync(path.join(root,'client-mods/green-water-teavm.js'),'utf8');
  const creativeCatalog='var JasprCreativeCatalog='+JSON.stringify(creative.catalogue())+';';
  const at=s.lastIndexOf('}));');
  if(at<0)throw Error('Missing native closure footer');
  s=s.slice(0,at)+begin+'\n'+runtime+'\n'+adapter+'\n'+creativeCatalog+'\n'+creativeAdapter+'\n'+pingAdapter+'\n'
    +waypointRuntime+'\n'+waypointAdapter+'\n'+waypointCodec+'\n'+waypointMarkersRuntime+'\n'+waypointMarkers+'\n'
    +waypointTabRuntime+'\n'+waypointTab+'\n'
    +dynamicLightsRuntime+'\n'+dynamicLights+'\n'
    +shaderPacksRuntime+'\n'+shaderPacks+'\n'+greenWater+'\n'+end+s.slice(at);
  new vm.Script(s,{filename:'candidate/stats-client/classes.js'});
  if(!acceptedBase(sha(unpatch(s))))throw Error('Stats reversal failed');
  return s;
}
if(require.main===module){
  const input=fs.readFileSync(path.join(root,'site/classes.js'),'utf8'),result=build(input);
  const dir=path.join(root,'candidate/stats-client');fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(path.join(dir,'classes.js'),result);
  fs.writeFileSync(path.join(dir,'manifest.json'),JSON.stringify({
    stage:'stats-keybind-master-gain-and-creative-catalogue-waypoints-shader-picker-v10-native-off-branch',baseSHA256:BASE,inputSHA256:sha(input),sha256:sha(result),
    bytes:Buffer.byteLength(result),addedBytes:Buffer.byteLength(result)-Buffer.byteLength(unpatch(input)),
    defaultKey:'K',nativeKeyCode:37,controlsLabel:'Upgrade Stats',optionKey:'key_key.jaspr.stats',
    command:'/stats',cooldownMs:750,masterVolumeScaling:true,creativeCatalogueItems:creative.catalogue().length,
    pingOverlay:true,pingOverlayField:'NetworkPlayerInfo.bzW',pingOverlayKeepsNativeBars:true,hooks:hooks.map(h=>h[0]),
    waypointKey:{defaultKey:'M',nativeKeyCode:50,controlsLabel:'Waypoints Menu',optionKey:'key_key.jaspr.waypoints',command:'/waypoints',cooldownMs:750},
    waypointMarkers:{scoreboard:'jwp',maxMarkers:24,beamBelow:4,beamAbove:72,hook:'DbP case 42'},
    waypointTab:{display:'tab-held only',beamGate:'DbP case 42',commands:'/wp compass|compassoff'},
    dynamicLights:{scoreboard:'jdl',hook:'DtH+DQP wrap + DbP case 42 frame',key:{defaultKey:'L',nativeKeyCode:38,controlsLabel:'Dynamic Lights',optionKey:'key.jaspr.dynamiclights'},modes:'Fast(default)/Smooth/Off'},
    shaderPacks:{hook:'DaB native scroll row + Cay native picker + Fjm native OFF branch/reroute + GyZ/GmS wrap',selection:'Video Settings native row opens Shader Packs screen, OFF default',picker:'One pack per row, built by JasprShadersBuildPicker; the native two-column pair loop is bypassed',packs:['MakeUp UltraFast','Chocapic13 Toaster','Miniature',"Sildur's Vibrant Lite",'BSL','Complementary Unbound'],selectorLabel:'compact 150px-safe label',pickerLabelsUnique:true,offPath:'native Fjm render branch plus lightmap/fog short-circuits; no shader fiber, GL allocation, or source assembly',lifecycle:'GPU targets/program/VBO/VAO and lazy GLSL sources released when OFF',sildurProfile:'renderer-adapted exposure 0.72 / contrast-curve 1.30 / vignette 0.14'},
    greenWater:{tint:'0x70FF14',mechanism:'JasprBiomeStyle redeclared later in the same closure; forces Biome.waterColorMultiplier for every biome'},
    previousStages:['native-startup-audio-mobile-auth-settings-fixes','gore','biomes'],
    serverChanges:false,liveFilesChanged:false,builtAt:new Date().toISOString()
  },null,2)+'\n');
  console.log('Candidate only: '+path.join(dir,'classes.js')+'\nSHA256 '+sha(result));
}
module.exports={build,unpatch,contains,sha,BASE,CURRENT_BASE,hooks,nativeFunction,begin,end};
