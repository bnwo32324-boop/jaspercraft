/* Version-pinned adapter for JasperCraft's 1.12.2 TeaVM client.
 * Native names are intentionally isolated here. See scripts/build-gore-client.cjs.
 * The hooks run at completed renderer fiber boundaries; game/auth/network code is untouched.
 */
var JasprGoreBridge = (function(){
  "use strict";
  var runtime=null, effectState=null, protectedModels=new WeakSet();
  var matrixFields=["h_","h$","ia","g4","h7","h9","h8","g3","h5","hy","h6","gy","lB","lD","lC","jU"];
  function matrix(){var m=HKM.data[HKD];return matrixFields.map(function(k){return m[k];});}
  function array(list){return list&&list.qN?Array.prototype.slice.call(list.qN.data,0,list.g):[];}
  function parts(model){return array(model.cJ9);}
  function children(part){return array(part.OS);}
  function geometry(part,scale){
    var mesh=[];
    array(part.a6Y).forEach(function(box){
      Array.prototype.forEach.call(box.a4t.data,function(quad){
        var vertices=quad.a4R.data,a=vertices[1].Kj,b=vertices[0].Kj,c=vertices[2].Kj;
        var u=[a.bh-b.bh,a.bq-b.bq,a.bi-b.bi],v=[a.bh-c.bh,a.bq-c.bq,a.bi-c.bi];
        var n=[v[1]*u[2]-v[2]*u[1],v[2]*u[0]-v[0]*u[2],v[0]*u[1]-v[1]*u[0]];
        var length=Math.hypot(n[0],n[1],n[2])||1,sign=quad.ecB?-1:1;
        for(var i=0;i<4;i++){var p=vertices[i],q=p.Kj;mesh.push(q.bh*scale,q.bq*scale,q.bi*scale,p.cDP,p.cDQ,n[0]/length*sign,n[1]/length*sign,n[2]/length*sign);}
      });
    });return mesh;
  }
  function bounds(part){
    var b=[Infinity,Infinity,Infinity,-Infinity,-Infinity,-Infinity];
    array(part.a6Y).forEach(function(box){
      Array.prototype.forEach.call(box.a4t.data,function(q){Array.prototype.forEach.call(q.a4R.data,function(v){var p=v.Kj,a=[p.bh,p.bq,p.bi];for(var j=0;j<3;j++){b[j]=Math.min(b[j],a[j]);b[j+3]=Math.max(b[j+3],a[j]);}});});
    });return b;
  }
  function draw(mesh){
    if(!mesh.length)return;
    var t=GdM(),buffer=t.dy;
    Ep0(buffer,7,Lq0); // GL_QUADS / POSITION_TEX_NORMAL, identical to native ModelRenderer.
    for(var i=0;i<mesh.length;i+=8){CUb(buffer,mesh[i],mesh[i+1],mesh[i+2]);EpJ(buffer,mesh[i+3],mesh[i+4]);Gu1(buffer,mesh[i+5],mesh[i+6],mesh[i+7]);E74(buffer);}
    FE$(t);
  }
  function saveState(){return {mode:KrH,matrix:matrix(),texture:HEn.data[0],active:HEo,tex0:Krr.data[0],tex1:Krr.data[1],lighting:Krh,
    cull:HHT,color:[HKI,HKJ,HKK,HKL],light:[KrK.data[1],KrL.data[1]]};}
  function restore(s){
    GnI(33984);FUe(s.texture);if(s.tex0)CQ6();else DCQ();
    GnI(33985);if(s.tex1)CQ6();else DCQ();G0W(33985,s.light[0],s.light[1]);
    if(s.lighting)ElS();else Fpb();if(s.cull)Ggy();else F1Q();
    CFh(s.color[0],s.color[1],s.color[2],s.color[3]);GnI(33984+s.active);DSz(s.mode);
  }
  function drawLocal(mesh,texture,color){
    var s=saveState();
    try{GnI(33984);if(texture===null)DCQ();else{CQ6();FUe(texture);}CFh(color[0],color[1],color[2],1);draw(mesh);}finally{restore(s);}
  }
  function quad(points,normal){var m=[];points.forEach(function(p){m.push(p[0],p[1],p[2],0,0,normal[0],normal[1],normal[2]);});return m;}
  function stump(b,axis,at,color){
    var axes=[0,1,2].filter(function(n){return n!==axis;}),u=axes[0],v=axes[1],normal=[0,0,0];normal[axis]=-1;
    var p=[[],[],[],[]];
    for(var i=0;i<4;i++){p[i][axis]=at-.001;p[i][u]=b[u+(i===1||i===2?3:0)];p[i][v]=b[v+(i>=2?3:0)];}
    drawLocal(quad(p,normal),null,color);
  }
  function begin(entity,renderer){
    if(!runtime)return;
    var model=renderer&&renderer.iK;
    if(!(entity instanceof Co)||entity instanceof Cb||entity instanceof HC){
      // RenderPlayer and armor stands share the living-render path with mobs.
      // Clear any prior mob scope and remember their model as a native-only
      // path so a leaked cosmetic context can never hide them.
      runtime.end();
      if(model)protectedModels.add(model);
      return;
    }
    if(model)protectedModels.delete(model);
    var meta=entity.constructor.$meta,name=meta&&meta.name||"mob";
    runtime.begin(entity,renderer.iK,{mob:true,id:entity.cu,type:name,pos:[entity.b,entity.f,entity.c],height:entity.bZ,
      health:ENU(entity),maxHealth:Crp(entity),dead:entity.uS>0,light:[KrK.data[1],KrL.data[1]]});
  }
  function raycast(w,from,to){
    var hit=DJx(w,Cq(from[0],from[1],from[2]),Cq(to[0],to[1],to[2]),0,1,0);
    if(!hit||!hit.pN||!hit.q3)return null;
    var face=hit.q3,n=face===KsS?[0,1,0]:face===HFo?[0,-1,0]:face===KsU?[1,0,0]:face===KsT?[-1,0,0]:face===KsW?[0,0,1]:[0,0,-1];
    return {p:[hit.pN.bh,hit.pN.bq,hit.pN.bi],n:n};
  }
  function lightAt(p,fallback){
    // UVs come from loaded world blocks. The native lightmap applies day/night normally.
    G0W(33985,fallback[0],fallback[1]);
  }
  function light(w,p){
    var at=Dy(Math.floor(p[0]),Math.max(0,Math.min(255,Math.floor(p[1]))),Math.floor(p[2]));
    if(!CmD(w,at))return [0,0];
    var packed=DQP(w,at,0);return [packed&65535,packed>>>16];
  }
  function drawPiece(p,camera,fade){
    Eu0();
    try{
      DPm(p.p[0]-camera[0],p.p[1]-camera[1],p.p[2]-camera[2]);
      GoB(p.angle[0],p.angle[1],p.angle[2]);
      // Shrink out at the very end instead of sorting dozens of translucent model faces.
      if(fade<1)FWM(fade,fade,fade);
      GnI(33984);CQ6();FUe(p.texture);lightAt(p.p,p.light);CFh(1,1,1,1);draw(p.mesh);
    }finally{ECi();}
  }
  function drawBlood(drops,stains,camera){
    GnI(33984);DCQ();
    // Batch small debris by color/light. Native depth testing and the world lightmap remain on.
    var batches=new Map();
    function append(p,mesh){var key=p.color.join(",")+":"+p.light.join(","),b=batches.get(key);if(!b){b={mesh:[],color:p.color,light:p.light};batches.set(key,b);}b.mesh.push.apply(b.mesh,mesh);}
    drops.forEach(function(p){
      var x=p.p[0]-camera[0],y=p.p[1]-camera[1],z=p.p[2]-camera[2],s=p.size;
      append(p,quad([[x-s,y-s,z],[x+s,y-s,z],[x+s,y+s,z],[x-s,y+s,z]],[0,0,1]));
      append(p,quad([[x,y-s,z-s],[x,y-s,z+s],[x,y+s,z+s],[x,y+s,z-s]],[1,0,0]));
    });
    stains.forEach(function(p){
      var axes=p.n[1]?[0,2]:p.n[0]?[1,2]:[0,1],vertices=[];
      for(var i=0;i<4;i++){var a=p.p.map(function(v,j){return v-camera[j];});a[axes[0]]+=(i===1||i===2?1:-1)*p.size;a[axes[1]]+=(i>=2?1:-1)*p.size;vertices.push(a);}
      append(p,quad(vertices,p.n));
    });
    batches.forEach(function(b){G0W(33985,b.light[0],b.light[1]);CFh(b.color[0],b.color[1],b.color[2],1);draw(b.mesh);});
  }
  function create(){
    runtime=createJasprGore({parts:parts,children:children,hasGeometry:function(p){return p.a6Y&&p.a6Y.g>0;},bounds:bounds,
      pivot:function(p){return [p.cD,p.bs,p.bA];},owner:function(p){return p.ddy;},geometry:geometry,drawLocal:drawLocal,stump:stump,raycast:raycast,light:light,
      startEffects:function(){effectState=saveState();DSz(5888);Eu0();GnI(33985);CQ6();GnI(33984);F1Q();ElS();},
      endEffects:function(){ECi();restore(effectState);effectState=null;},drawPiece:drawPiece,drawBlood:drawBlood,
      report:function(event,message){if($rt_globals.console)$rt_globals.console.warn("[JasperCraft gore] "+event+": "+message);}
    },{mobile:typeof $rt_globals.matchMedia==="function"&&$rt_globals.matchMedia("(pointer: coarse)").matches});
    $rt_globals.JasprGoreDiagnostics=Object.freeze({status:function(){return runtime.status();}});
  }
  return {
    frame:function(w,manager){if(!runtime)create();runtime.frame(w,[manager.bP0,manager.bP1,manager.bPZ],matrix(),Date.now());},
    reset:function(){if(runtime)runtime.clear();protectedModels=new WeakSet();},
    begin:begin,end:function(){if(runtime)runtime.end();},
    // renderWithRotation reuses its scale parameter as a translated Z coordinate.
    // Record the scale at compileDisplayList, never infer it from that reused local.
    part:function(p,scale){
      if(p&&protectedModels.has(p.ddy))return false;
      return runtime&&runtime.active()?runtime.part(p,p.$jasprGoreScale===undefined?scale:p.$jasprGoreScale,matrix(),HEn.data[0],[KrK.data[1],KrL.data[1]]):false;
    },
    layer:function(model){return runtime&&runtime.layer(model);},
    hand:function(side){return runtime&&runtime.hand(side===Kua?-1:1);},
    render:function(){if(runtime)runtime.render(Date.now());}
  };
}());

// A real TeaVM fiber wrapper: if the normal display-list draw suspends, resume that draw
// without running the side-effecting dismemberment hook a second time.
function JasprGoreDraw(a,b,c){var $p=0;if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}
  _:while(true){switch($p){case 0:if(JasprGoreBridge.part(a,b))return;$p=1;case 1:Dle(c);if(B())break _;return;default:FT();}}
  Ds().s(a,b,c,$p);
}
