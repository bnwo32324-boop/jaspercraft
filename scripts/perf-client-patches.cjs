'use strict';
// JasperCraft client performance patches (JASPR_PERF_V1), applied in place to site/classes.js.
//   node scripts/perf-client-patches.cjs [path/to/classes.js] [--check]
// Five small, fenced patches, each inspired by a desktop optimization mod and measured in the performance sandbox
// (PERFORMANCE_UPDATE.md): glState (BadOptimizations), particleCull (Sodium), particleLight (Sodium/Lithium),
// nameCheck (profiler finding) and occlusion (EntityCulling). Every anchor must occur exactly once or nothing is
// written, so a later client rebuild that moved the code is detected instead of mis-patched. Each patch can be turned
// off per browser: localStorage.setItem('jaspr.perf.v1', '{"occlusion":false}'). Counters: JasprPerf.stats().
const fs = require('node:fs');
const path = require('node:path');

function once(src, anchor, replacement, label) {
  const at = src.indexOf(anchor);
  if (at < 0 || src.indexOf(anchor, at + 1) >= 0) throw new Error(`${label}: anchor must occur exactly once (found ${at < 0 ? 0 : 'several'})`);
  return src.slice(0, at) + replacement + src.slice(at + anchor.length);
}

const RUNTIME = `/*JASPR_PERF_V1_BEGIN runtime*/
var JasprPerfFlags=(function(){var f={};try{var s=$rt_globals.localStorage&&$rt_globals.localStorage.getItem('jaspr.perf.v1');if(s)f=JSON.parse(s)||{};}catch(e){f={};}return f;})();
function JasprPerfOn(name){return JasprPerfFlags[name]!==false;}
var JasprPerfTick=0,JasprPerfPlanes=new Float32Array(20),JasprPerfPlanesOk=false,JasprPerfMat=new Float32Array(16),JasprPerfProj=new Float32Array(16),JasprPerfClip=new Float32Array(16);
var JasprPerfFields=['h_','h$','ia','g4','h7','h9','h8','g3','h5','hy','h6','gy','lB','lD','lC','jU'];
var JasprPerfStats={particlesSeen:0,particlesCulled:0,lightHits:0,lightMisses:0,nameSkips:0,glStateFast:0,glStateSlow:0};
$rt_globals.JasprPerf={flags:function(){return JasprPerfFlags;},stats:function(){return JasprPerfStats;}};
/*JASPR_PERF_V1_END runtime*/
`;

const PATCHES = {
  // BadOptimizations-style: the DH distant-terrain pass saved GL state with getParameter every frame. DEPTH_FUNC and
  // DEPTH_WRITEMASK are synchronous GPU-process round trips in Chrome (9-11% of main-thread time in profiles). The engine's
  // GlStateManager already mirrors both (KqW/KqX; only it and this pass ever change them), so read the mirror instead.
  glState(src) {
    src = once(src,
      'mask:gl.getParameter(gl.DEPTH_WRITEMASK),func:gl.getParameter(gl.DEPTH_FUNC),',
      '/*JASPR_PERF_V1_BEGIN glState*/mask:JasprPerfDepthMask(gl),func:JasprPerfDepthFunc(gl),/*JASPR_PERF_V1_END glState*/', 'glState');
    return [src, `
/*JASPR_PERF_V1_BEGIN glState-helpers*/
function JasprPerfDepthFunc(gl){if(JasprPerfOn('glState')&&KqW>=512&&KqW<=519){JasprPerfStats.glStateFast++;return KqW;}JasprPerfStats.glStateSlow++;return gl.getParameter(gl.DEPTH_FUNC);}
function JasprPerfDepthMask(gl){if(JasprPerfOn('glState')&&(KqX===0||KqX===1||KqX===true||KqX===false))return !!KqX;return gl.getParameter(gl.DEPTH_WRITEMASK);}
/*JASPR_PERF_V1_END glState-helpers*/`];
  },

  // Sodium-style particle frustum culling: vanilla renders every particle, including the ones behind the camera.
  // A particle is skipped only when its whole billboard (plus a margin) lies outside a side plane or behind the camera.
  particleCull(src) {
    src = once(src,
      'case 0:d=LoQ;e=LoR;f=LoS;g=LoT;h=LoU;i=b.fj;j=b.b-i;k=c;LoV=i+j*k;j=b.e2;LoW=j+(b.f-j)*k;i=b.fk;LoX=i+(b.c-i)*k;$p=1;',
      'case 0:d=LoQ;e=LoR;f=LoS;g=LoT;h=LoU;i=b.fj;j=b.b-i;k=c;LoV=i+j*k;j=b.e2;LoW=j+(b.f-j)*k;i=b.fk;LoX=i+(b.c-i)*k;/*JASPR_PERF_V1_BEGIN particleCull*/JasprPerfParticleFrame();/*JASPR_PERF_V1_END particleCull*/$p=1;',
      'particleCull-frame');
    src = once(src,
      'case 22:a:{try{v.qy(u,b,c,d,h,e,f,g);if(B()){break _;}}',
      'case 22:a:{try{/*JASPR_PERF_V1_BEGIN particleCull*/if(JasprPerfParticleVisible(v,c)){/*JASPR_PERF_V1_END particleCull*/v.qy(u,b,c,d,h,e,f,g);if(B()){break _;}/*JASPR_PERF_V1_BEGIN particleCull*/}/*JASPR_PERF_V1_END particleCull*/}',
      'particleCull-loop');
    return [src, `
/*JASPR_PERF_V1_BEGIN particleCull-helpers*/
function JasprPerfParticleFrame(){
  JasprPerfTick=Math.floor($rt_globals.performance.now()/50);
  JasprPerfPlanesOk=false;if(!JasprPerfOn('particleCull'))return;
  try{var mv=HKM.data[HKD],pr=HKQ.data[HKF],i,c,r,k,t,p,n;
    for(i=0;i<16;i++){JasprPerfMat[i]=mv[JasprPerfFields[i]];JasprPerfProj[i]=pr[JasprPerfFields[i]];}
    for(c=0;c<4;c++)for(r=0;r<4;r++){t=0;for(k=0;k<4;k++)t+=JasprPerfProj[k*4+r]*JasprPerfMat[c*4+k];JasprPerfClip[c*4+r]=t;}
    // planes: left,right,bottom,top (row3 +/- row0/row1) and 'in front of the camera' (row3: w>0, valid for any depth
    // convention -- Eaglercraft uses reversed depth); normalised so the margin is in blocks
    for(p=0;p<5;p++){for(c=0;c<4;c++)JasprPerfPlanes[p*4+c]=JasprPerfClip[c*4+3]+(p<4?(p%2?-1:1)*JasprPerfClip[c*4+(p>>1)]:0);
      n=Math.sqrt(JasprPerfPlanes[p*4]*JasprPerfPlanes[p*4]+JasprPerfPlanes[p*4+1]*JasprPerfPlanes[p*4+1]+JasprPerfPlanes[p*4+2]*JasprPerfPlanes[p*4+2]);
      if(!(n>1e-9))return;for(c=0;c<4;c++)JasprPerfPlanes[p*4+c]/=n;}
    JasprPerfPlanesOk=true;
  }catch(e){JasprPerfPlanesOk=false;}
}
function JasprPerfParticleVisible(v,pt){
  if(!JasprPerfPlanesOk)return true;
  JasprPerfStats.particlesSeen++;
  var x=v.kX+(v.d1-v.kX)*pt-LoV,y=v.iR+(v.db-v.iR)*pt-LoW,z=v.kW+(v.d0-v.kW)*pt-LoX;
  if(!(x===x&&y===y&&z===z))return true;
  var margin=0.5+0.15*Math.abs(v.er||1),P=JasprPerfPlanes;
  for(var i=0;i<20;i+=4)if(P[i]*x+P[i+1]*y+P[i+2]*z+P[i+3]<-margin){JasprPerfStats.particlesCulled++;return false;}
  return true;
}
/*JASPR_PERF_V1_END particleCull-helpers*/`];
  },

  // Sodium/Lithium-style light cache: Particle.getBrightnessForRender allocated a BlockPos and queried world light for every
  // particle on every frame. The value is kept for one 50 ms game tick per particle (vanilla light itself changes per tick).
  particleLight(src) {
    src = once(src, 'function DXe(a,b){', '/*JASPR_PERF_V1_BEGIN particleLight*/function DXe(a,b){if(JasprPerfOn(\'particleLight\')&&a.$jpLt===JasprPerfTick){JasprPerfStats.lightHits++;return a.$jpL;}var v=JasprPerfOrigDXe(a,b);if(!B()&&JasprPerfOn(\'particleLight\')){JasprPerfStats.lightMisses++;a.$jpLt=JasprPerfTick;a.$jpL=v;}return v;}\n/*JASPR_PERF_V1_END particleLight*/function JasprPerfOrigDXe(a,b){', 'particleLight');
    return [src, ''];
  },

  // RenderLivingBase.applyRotations built every living entity's translated name every frame only to compare it with the
  // "Dinnerbone"/"Grumm" easter egg. Without a custom name tag a non-player's name is its translated type name, which never
  // equals either (checked against the shipped language files), so the name is only built for players and named mobs.
  nameCheck(src) {
    src = once(src,
      'case 2:$z=b.b1();if(B()){break _;}i=$z;$p=3;case 3:$z=GjV(i);if(B()){break _;}i=$z;if(i===null)return;j=C(7576);$p=5;continue _;',
      'case 2:/*JASPR_PERF_V1_BEGIN nameCheck*/if(JasprPerfOn(\'nameCheck\')&&!(b instanceof Cb)){$p=13;continue _;}/*JASPR_PERF_V1_END nameCheck*/$z=b.b1();if(B()){break _;}i=$z;$p=3;case 3:$z=GjV(i);if(B()){break _;}i=$z;if(i===null)return;j=C(7576);$p=5;continue _;',
      'nameCheck-branch');
    src = once(src,
      'c=0.0;d=b.bZ+0.10000000149011612;e=0.0;$p=9;continue _;default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,$p);}',
      'c=0.0;d=b.bZ+0.10000000149011612;e=0.0;$p=9;continue _;/*JASPR_PERF_V1_BEGIN nameCheck*/case 13:$z=F4L(b);if(B()){break _;}if(!$z){JasprPerfStats.nameSkips++;return;}$p=14;case 14:$z=b.b1();if(B()){break _;}i=$z;$p=3;continue _;/*JASPR_PERF_V1_END nameCheck*/default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,$p);}',
      'nameCheck-cases');
    return [src, ''];
  },

  // EntityCulling-style occlusion: vanilla only culls whole 16^3 sections, so mobs, spawner cages, chests and signs behind
  // solid terrain are still drawn whenever their section is reachable through caves. Each candidate that already passed the
  // distance and frustum tests is ray-cast from the exact camera position (recovered from the modelview matrix) to 15 points
  // on its box; it is skipped only if every ray hits a full opaque cube (Block.fullBlock && lightOpacity 255 -- never
  // leaves, glass, slabs or stairs). Players, anything within 8 blocks, third-person view and far-rendering block entities
  // (beacons) are never culled. Results are cached for a few frames under a per-frame ray budget.
  occlusion(src) {
    src = once(src,
      'LoE=f;LoF=p;LoG=h;BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);',
      'LoE=f;LoF=p;LoG=h;/*JASPR_PERF_V1_BEGIN occlusion*/JasprPerfOccFrame();/*JASPR_PERF_V1_END occlusion*/BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);',
      'occlusion-frame');
    src = once(src,
      'case 2:$z=g.b2r(b,c,d,e,f);if(B()){break _;}h=$z;return !h?0:1;',
      'case 2:$z=g.b2r(b,c,d,e,f);if(B()){break _;}h=$z;return !h?0:/*JASPR_PERF_V1_BEGIN occlusion*/(JasprPerfEntityHidden(b)?0:1)/*JASPR_PERF_V1_END occlusion*/;',
      'occlusion-entity');
    src = once(src,
      'case 2:$z=b.dY$();if(B()){break _;}f=$z;if(e>=f)return;$p=3;',
      'case 2:$z=b.dY$();if(B()){break _;}f=$z;if(e>=f)return;/*JASPR_PERF_V1_BEGIN occlusion*/if(f<=4096.0&&JasprPerfTileHidden(b))return;/*JASPR_PERF_V1_END occlusion*/$p=3;',
      'occlusion-tile');
    return [src, `
/*JASPR_PERF_V1_BEGIN occlusion-helpers*/
var JasprPerfOccOk=false,JasprPerfOccFrameNo=0,JasprPerfOccBudget=0,JasprPerfOccBroken=false,JasprPerfEyeX=0,JasprPerfEyeY=0,JasprPerfEyeZ=0,
    JasprPerfOccWorld=null,JasprPerfCX=0x7fffffff,JasprPerfCZ=0x7fffffff,JasprPerfCh=null,JasprPerfOccMat=new Float32Array(16);
JasprPerfStats.occHidden=0;JasprPerfStats.occShown=0;JasprPerfStats.occRays=0;
function JasprPerfOccFrame(){
  JasprPerfOccFrameNo++;JasprPerfOccBudget=24;JasprPerfOccOk=false;JasprPerfCX=JasprPerfCZ=0x7fffffff;JasprPerfCh=null;
  if(JasprPerfOccBroken||!JasprPerfOn('occlusion'))return;
  try{
    if(!HEH||!HEH.X||!HEH.G||HEH.G.lv)return;                       // third person: the camera is not at the eye
    var mv=HKM.data[HKD],m=JasprPerfOccMat,i;for(i=0;i<16;i++)m[i]=mv[JasprPerfFields[i]];
    var tx=m[12],ty=m[13],tz=m[14];                                   // camera = -R^T t, relative to the render position
    JasprPerfEyeX=LoE-(m[0]*tx+m[1]*ty+m[2]*tz);JasprPerfEyeY=LoF-(m[4]*tx+m[5]*ty+m[6]*tz);JasprPerfEyeZ=LoG-(m[8]*tx+m[9]*ty+m[10]*tz);
    if(!(JasprPerfEyeX===JasprPerfEyeX&&JasprPerfEyeY===JasprPerfEyeY&&JasprPerfEyeZ===JasprPerfEyeZ))return;
    JasprPerfOccWorld=HEH.X;JasprPerfOccOk=true;
  }catch(e){JasprPerfOccOk=false;}
}
function JasprPerfSolid(x,y,z){
  if(y<0||y>255)return false;
  var cx=x>>4,cz=z>>4,ch;
  if(cx===JasprPerfCX&&cz===JasprPerfCZ)ch=JasprPerfCh;else{ch=Eqd(JasprPerfOccWorld,cx,cz);if(B()){JasprPerfOccBroken=true;return false;}JasprPerfCX=cx;JasprPerfCZ=cz;JasprPerfCh=ch;}
  if(!ch)return false;
  var st=FaX(ch,x,y,z);if(B()){JasprPerfOccBroken=true;return false;}
  var bl=st&&st.n;return !!(bl&&bl.cky&&bl.NO>=255);
}
function JasprPerfRay(ex,ey,ez,tx,ty,tz){
  JasprPerfStats.occRays++;
  var dx=tx-ex,dy=ty-ey,dz=tz-ez,x=Math.floor(ex),y=Math.floor(ey),z=Math.floor(ez),X=Math.floor(tx),Y=Math.floor(ty),Z=Math.floor(tz);
  var sx=dx>0?1:dx<0?-1:0,sy=dy>0?1:dy<0?-1:0,sz=dz>0?1:dz<0?-1:0;
  var ddx=sx?Math.abs(1/dx):Infinity,ddy=sy?Math.abs(1/dy):Infinity,ddz=sz?Math.abs(1/dz):Infinity;
  var mx=sx>0?(x+1-ex)/dx:sx<0?(x-ex)/dx:Infinity,my=sy>0?(y+1-ey)/dy:sy<0?(y-ey)/dy:Infinity,mz=sz>0?(z+1-ez)/dz:sz<0?(z-ez)/dz:Infinity;
  for(var n=0;n<256;n++){
    if(x===X&&y===Y&&z===Z)return true;
    if(mx<my&&mx<mz){if(mx>1)return true;x+=sx;mx+=ddx;}
    else if(my<mz){if(my>1)return true;y+=sy;my+=ddy;}
    else{if(mz>1)return true;z+=sz;mz+=ddz;}
    if(x===X&&y===Y&&z===Z)return true;                               // the target's own block never hides it
    if(JasprPerfSolid(x,y,z))return false;
  }
  return true;
}
function JasprPerfBoxVisible(x0,y0,z0,x1,y1,z1){
  var ex=JasprPerfEyeX,ey=JasprPerfEyeY,ez=JasprPerfEyeZ,xm=(x0+x1)/2,ym=(y0+y1)/2,zm=(z0+z1)/2;
  x0+=0.05;y0+=0.05;z0+=0.05;x1-=0.05;y1-=0.05;z1-=0.05;
  if(JasprPerfRay(ex,ey,ez,xm,ym,zm))return true;
  if(JasprPerfRay(ex,ey,ez,x0,y0,z0)||JasprPerfRay(ex,ey,ez,x1,y0,z0)||JasprPerfRay(ex,ey,ez,x0,y1,z0)||JasprPerfRay(ex,ey,ez,x1,y1,z0)||
     JasprPerfRay(ex,ey,ez,x0,y0,z1)||JasprPerfRay(ex,ey,ez,x1,y0,z1)||JasprPerfRay(ex,ey,ez,x0,y1,z1)||JasprPerfRay(ex,ey,ez,x1,y1,z1))return true;
  return JasprPerfRay(ex,ey,ez,x0,ym,zm)||JasprPerfRay(ex,ey,ez,x1,ym,zm)||JasprPerfRay(ex,ey,ez,xm,y0,zm)||JasprPerfRay(ex,ey,ez,xm,y1,zm)||
         JasprPerfRay(ex,ey,ez,xm,ym,z0)||JasprPerfRay(ex,ey,ez,xm,ym,z1);
}
function JasprPerfHidden(o,x0,y0,z0,x1,y1,z1){
  if(!JasprPerfOccOk||JasprPerfOccBroken)return false;
  var dx=(x0+x1)/2-JasprPerfEyeX,dy=(y0+y1)/2-JasprPerfEyeY,dz=(z0+z1)/2-JasprPerfEyeZ;
  if(dx*dx+dy*dy+dz*dz<64)return false;                                // within 8 blocks: always drawn
  var last=o.$jpOf;
  if(last!==undefined&&JasprPerfOccFrameNo-last<(o.$jpO?2:6))return o.$jpO;  // hidden: re-check soon; visible: less often
  if(JasprPerfOccBudget<=0)return last!==undefined?o.$jpO:false;
  JasprPerfOccBudget--;
  var hidden=!JasprPerfBoxVisible(x0,y0,z0,x1,y1,z1);
  o.$jpOf=JasprPerfOccFrameNo;o.$jpO=hidden;
  if(hidden)JasprPerfStats.occHidden++;else JasprPerfStats.occShown++;
  return hidden;
}
function JasprPerfEntityHidden(e){
  if(!JasprPerfOccOk||e instanceof Cb)return false;
  var w=e.bI>0?e.bI:0.6,h=e.bZ>0?e.bZ:1.8,x=e.b,y=e.f,z=e.c;
  if(!(x===x&&y===y&&z===z))return false;
  return JasprPerfHidden(e,x-w/2-0.1,y-0.1,z-w/2-0.1,x+w/2+0.1,y+h+0.1,z+w/2+0.1);
}
function JasprPerfTileHidden(t){
  if(!JasprPerfOccOk)return false;var p=t.bW;if(!p)return false;
  return JasprPerfHidden(t,p.m,p.i,p.l,p.m+1,p.i+1,p.l+1);
}
/*JASPR_PERF_V1_END occlusion-helpers*/`];
  },
};

const MARKER = '/*JASPR_PERF_V1_BEGIN runtime*/';

function apply(source) {
  if (source.includes(MARKER)) return { source, changed: false };
  let src = source, helpers = RUNTIME;
  for (const name of Object.keys(PATCHES)) { const [next, extra] = PATCHES[name](src); src = next; helpers += extra + '\n'; }
  const tail = src.lastIndexOf('}));');
  if (tail < 0) throw new Error('TeaVM closure end not found');
  return { source: src.slice(0, tail) + helpers + src.slice(tail), changed: true };
}

module.exports = { PATCHES, RUNTIME, MARKER, apply };

if (require.main === module) {
  const args = process.argv.slice(2);
  const file = path.resolve(args.find((a) => !a.startsWith('--')) || path.join(__dirname, '..', 'site', 'classes.js'));
  const text = fs.readFileSync(file, 'utf8');
  if (args.includes('--check')) {
    const n = (text.match(/\/\*JASPR_PERF_V1_BEGIN /g) || []).length;
    console.log(text.includes(MARKER) ? `JASPR_PERF_V1 present (${n} fenced blocks)` : 'JASPR_PERF_V1 missing');
    process.exitCode = text.includes(MARKER) ? 0 : 1;
  } else {
    const { source, changed } = apply(text);
    if (changed) fs.writeFileSync(file, source);
    console.log(changed ? `patched ${file}` : 'already patched');
  }
}
