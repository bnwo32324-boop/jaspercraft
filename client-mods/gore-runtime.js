/* JasperCraft native, client-only dismemberment. No entities, packets, or world edits. */
function createJasprGore(api, options) {
  "use strict";
  options = options || {};
  var limits = { pieces: options.mobile ? 48 : 96, blood: options.mobile ? 48 : 120,
    stains: options.mobile ? 32 : 72, states: 256, range: 64, physics: options.mobile ? 48 : 96 };
  var world = null, camera = [0,0,0], view = identity(), inverseView = identity();
  var states = new Map(), models = new WeakMap(), geometries = new WeakMap();
  var pieces = [], drops = [], stains = [], context = null, now = 0, lastStep = 0, cursor = 0;
  var stats = { hits:0, amputations:0, deaths:0, piecesSpawned:0, visibilityGuards:0, errors:0, frames:0 };
  var enabled = true, lastError = "", nextPartId = 1, partIds = new WeakMap(), coverage=new Map();

  function identity() { return [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]; }
  function multiply(a,b) {
    var c = new Array(16);
    for(var col=0;col<4;col++) for(var row=0;row<4;row++) {
      var n=0; for(var k=0;k<4;k++) n+=a[k*4+row]*b[col*4+k]; c[col*4+row]=n;
    }
    return c;
  }
  // Camera/model matrices are affine. Inverting the 3x3 also handles view bobbing/scaling.
  function invert(a) {
    var x=a[0],y=a[4],z=a[8],u=a[1],v=a[5],w=a[9],p=a[2],q=a[6],r=a[10];
    var det=x*(v*r-w*q)-y*(u*r-w*p)+z*(u*q-v*p);
    if(Math.abs(det)<1e-10) throw new Error("Singular world view matrix");
    var b=[(v*r-w*q)/det,(w*p-u*r)/det,(u*q-v*p)/det,0,
      (z*q-y*r)/det,(x*r-z*p)/det,(y*p-x*q)/det,0,
      (y*w-z*v)/det,(z*u-x*w)/det,(x*v-y*u)/det,0,0,0,0,1];
    for(var i=0;i<3;i++) b[12+i]=-(b[i]*a[12]+b[4+i]*a[13]+b[8+i]*a[14]);
    return b;
  }
  function point(m,p) { return [m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]]; }
  function id(part) { if(!partIds.has(part)) partIds.set(part,nextPartId++); return partIds.get(part); }
  function rand(seed) { var x=seed|0; return function(){ x^=x<<13;x^=x>>>17;x^=x<<5;return (x>>>0)/4294967296; }; }
  function palette(name) {
    if(/Skeleton|Stray|Wither/.test(name)) return [0.64,0.58,0.44];
    if(/Slime|Creeper/.test(name)) return [0.19,0.39,0.06];
    if(/Ender|Shulker|Vex/.test(name)) return [0.39,0.055,0.56];
    if(/Blaze|Magma/.test(name)) return [0.68,0.17,0.025];
    if(/IronGolem|Snowman/.test(name)) return [0.37,0.31,0.26];
    return [0.53,0.018,0.025];
  }
  function distance2(a,b) { var x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return x*x+y*y+z*z; }
  function boundedPush(list,value,cap) { if(list.length>=cap)list.shift();list.push(value); }
  function clear() { states.clear();pieces.length=drops.length=stains.length=0;context=null;lastStep=0;world=null; }
  function fail(error) {
    stats.errors++;lastError=String(error && error.message || error).slice(0,200);enabled=false;clear();
    if(api.report)api.report("disabled",lastError);
  }
  function guarded(fn,fallback) { return function(){ if(!enabled)return fallback;try{return fn.apply(null,arguments);}catch(e){fail(e);return fallback;} }; }
  function definitions(model) {
    if(models.has(model))return models.get(model);
    var parts=api.parts(model).filter(function(p){return api.hasGeometry(p);});
    var defs=parts.map(function(p){
      var b=api.bounds(p),pivot=api.pivot(p),volume=Math.max(.01,(b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2]));
      return { part:p,id:id(p),volume:volume,limb:Math.abs(pivot[0])>.5,bounds:b };
    });
    var geometryParts=new Set(defs.map(function(d){return d.part;}));
    function branchSize(part,seen){
      if(seen.has(part))return 0;seen.add(part);
      var count=geometryParts.has(part)?1:0;
      api.children(part).forEach(function(child){count+=branchSize(child,seen);});
      return count;
    }
    defs.forEach(function(d){d.branch=branchSize(d.part,new Set());});
    // Prefer peripheral leaves. A parent branch is allowed only when its total hidden
    // geometry fits the living-mob visibility budget calculated below.
    defs.sort(function(a,b){return Number(b.limb)-Number(a.limb)||a.branch-b.branch||a.volume-b.volume||a.id-b.id;});
    models.set(model,defs);return defs;
  }
  function geometry(part,scale) {
    var cached=geometries.get(part);
    if(!cached || cached.scale!==scale){cached={scale:scale,mesh:api.geometry(part,scale)};geometries.set(part,cached);}
    return cached.mesh;
  }
  function descendants(part,set) { if(set.has(part))return;set.add(part);api.children(part).forEach(function(p){descendants(p,set);}); }
  function livingCuts(defs,amount) {
    var chosen=new Set(),picked=0;
    // At least half of a living model's geometry, including a complete native root
    // path, must remain. Death is the only state permitted to suppress everything.
    var maximum=Math.max(1,Math.min(defs.length-1,Math.floor(defs.length*.5)));
    for(var i=0;i<defs.length&&picked<amount;i++){
      var branch=new Set();descendants(defs[i].part,branch);
      var merged=new Set(chosen);branch.forEach(function(part){merged.add(part);});
      var hidden=defs.reduce(function(total,d){return total+(merged.has(d.part)?1:0);},0);
      if(hidden>maximum||hidden>=defs.length)continue;
      chosen=merged;picked++;
    }
    var totalHidden=defs.reduce(function(total,d){return total+(chosen.has(d.part)?1:0);},0);
    if(totalHidden>=defs.length){stats.visibilityGuards++;return new Set();}
    return chosen;
  }
  function frame(nextWorld,nextCamera,nextView,time) {
    context=null;stats.frames++;now=time;
    if(world!==nextWorld){clear();world=nextWorld;}
    camera=nextCamera;view=nextView;inverseView=invert(view);
    if(!lastStep)lastStep=now;
    states.forEach(function(s,e){if(now-s.seen>30000)states.delete(e);});
  }
  function begin(entity,model,snapshot) {
    context=null;
    if(!snapshot || !snapshot.mob || !model || distance2(snapshot.pos,camera)>limits.range*limits.range)return;
    var s=states.get(entity),defs=definitions(model);
    if(!defs.length)return;
    if(!s){
      if(states.size>=limits.states)states.delete(states.keys().next().value);
      var type=String(snapshot.type).slice(0,100);
      if(!coverage.has(type)&&coverage.size<96)coverage.set(type,{seen:0,deaths:0,pieces:0});
      var covered=coverage.get(type);if(covered)covered.seen++;
      s={seen:now,health:snapshot.health,cut:new Set(),detached:new Set(),death:false,random:rand((snapshot.id+1)*2654435761),color:palette(snapshot.type),covered:covered};
      states.set(entity,s);
    }
    s.seen=now;
    var dead=snapshot.dead || snapshot.health<=0,ratio=Math.max(0,Math.min(1,snapshot.health/Math.max(1,snapshot.maxHealth)));
    var stage=ratio<=.18?3:ratio<=.42?2:ratio<=.72?1:0;
    var amount=dead?defs.length:Math.min(stage,Math.max(1,Math.floor(defs.length*.5)));
    // A one-part mob is chipped into quarters, not made invisible by its first injury.
    var chip=defs.length<=2;
    var chosen=new Set();
    if(dead)for(var n=0;n<amount;n++)descendants(defs[n].part,chosen);
    else if(!chip)chosen=livingCuts(defs,amount);
    if(snapshot.health>s.health+.1){
      // Only a part that actually grew back can detach again. Regeneration inside the
      // same damage band must not repeatedly emit duplicate copies of missing limbs.
      s.detached.forEach(function(key){
        var fields=key.split(":"),definition=defs.find(function(d){return d.id===Number(fields[0]);});
        if(chip?Number(fields[2])>=stage:!definition||!chosen.has(definition.part))s.detached.delete(key);
      });
      if(!dead)s.death=false;
    }
    if(snapshot.health<s.health-.01){stats.hits++;spray(snapshot.pos.map(function(x,i){return x+(i===1?snapshot.height*.55:0);}),s,dead?16:7,snapshot.light);}
    if(dead&&!s.death){stats.deaths++;if(s.covered)s.covered.deaths++;spray([snapshot.pos[0],snapshot.pos[1]+snapshot.height*.55,snapshot.pos[2]],s,options.mobile?18:30,snapshot.light);}
    s.cut=chosen;s.health=snapshot.health;
    // A layer is scoped by the generated layer renderer hook below. Keeping the
    // identity, rather than matching any model by pivot alone, makes a stale
    // mob render context fail open for players and other unrelated renderers.
    context={state:s,entity:entity,model:model,snapshot:snapshot,defs:defs,dead:dead,chip:chip,stage:stage,occurrences:new Map(),layerModel:null,spawned:0};
  }
  function end(){if(context&&context.dead)context.state.death=true;context=null;}
  function worldMatrix(nativeMatrix){var m=multiply(inverseView,nativeMatrix);m[12]+=camera[0];m[13]+=camera[1];m[14]+=camera[2];return m;}
  function spray(pos,s,count,light) {
    for(var i=0;i<count;i++){
      var r=s.random;
      boundedPush(drops,{p:pos.slice(),v:[(r()-.5)*4,1+r()*3,(r()-.5)*4],size:.018+r()*.035,
        color:s.color,born:now,life:1700+r()*800,light:api.light?api.light(world,pos):light,random:r},limits.blood);
    }
  }
  function spawn(mesh,matrix,s,texture,light,death) {
    if(!mesh.length)return;
    var center=[0,0,0],count=mesh.length/8;
    for(var i=0;i<mesh.length;i+=8){center[0]+=mesh[i];center[1]+=mesh[i+1];center[2]+=mesh[i+2];}
    center=center.map(function(n){return n/count;});
    var origin=point(matrix,center),local=mesh.slice(),r=s.random;
    // Bake the animated model basis once. Pieces subsequently spin about their own centers.
    for(var k=0;k<mesh.length;k+=8){
      var p=point(matrix,[mesh[k],mesh[k+1],mesh[k+2]]);
      for(var j=0;j<3;j++){local[k+j]=p[j]-origin[j];local[k+5+j]=matrix[j]*mesh[k+5]+matrix[4+j]*mesh[k+6]+matrix[8+j]*mesh[k+7];}
    }
    boundedPush(pieces,{mesh:local,p:origin,v:[(r()-.5)*(death?5:3),2+r()*3,(r()-.5)*(death?5:3)],
      angle:[0,0,0],spin:[(r()-.5)*5,(r()-.5)*5,(r()-.5)*5],texture:texture,light:api.light?api.light(world,origin):light,
      born:now,life:7000+r()*2000,color:[1,1,1],blood:s.color,random:r},limits.pieces);
    stats.piecesSpawned++;
    if(s.covered)s.covered.pieces++;
  }
  function bounds(mesh){var b=[Infinity,Infinity,Infinity,-Infinity,-Infinity,-Infinity];for(var i=0;i<mesh.length;i+=8)for(var j=0;j<3;j++){b[j]=Math.min(b[j],mesh[i+j]);b[j+3]=Math.max(b[j+3],mesh[i+j]);}return b;}
  // Clip box faces on their longest local axis, retaining interpolated UVs/normals.
  function sliceMesh(mesh,axis,lo,hi) {
    var out=[];
    for(var i=0;i<mesh.length;i+=32){
      var poly=[];for(var v=0;v<4;v++)poly.push(mesh.slice(i+v*8,i+v*8+8));
      [[lo,1],[hi,-1]].forEach(function(plane){
        var next=[];for(var j=0;j<poly.length;j++){
          var a=poly[j],b=poly[(j+1)%poly.length],aa=(a[axis]-plane[0])*plane[1]>=-1e-7,bb=(b[axis]-plane[0])*plane[1]>=-1e-7;
          if(aa)next.push(a);
          if(aa!==bb){var t=(plane[0]-a[axis])/(b[axis]-a[axis]);next.push(a.map(function(n,k){return n+(b[k]-n)*t;}));}
        }poly=next;
      });
      if(poly.length===4)poly.forEach(function(v){out.push.apply(out,v);});
      else if(poly.length>=3)for(var t=1;t<poly.length-1;t++)[poly[0],poly[t],poly[t+1],poly[t+1]].forEach(function(v){out.push.apply(out,v);});
    }
    return out;
  }
  function drawPart(part,scale,nativeMatrix,texture,light) {
    var c=context;
    if(!c)return false;
    var owner=api.owner(part),main=owner===c.model;
    // Layer models share pivots with the main anatomy; never mutate shared
    // showModel flags. Only the layer renderer active for this mob may use the
    // pivot fallback. An unrelated model must always take the native path.
    if(!main){
      if(owner!==c.layerModel)return false;
      if(c.dead)return true;
      var pivot=api.pivot(part);
      return c.defs.some(function(d){var p=api.pivot(d.part);return c.state.cut.has(d.part)&&distance2(p,pivot)<.01;});
    }
    var occurrence=c.occurrences.get(part)||0;c.occurrences.set(part,occurrence+1);
    var key=id(part)+":"+occurrence,s=c.state,cut=s.cut.has(part);
    if(!cut&&!(c.chip&&c.stage>0))return false;
    var mesh=geometry(part,scale);
    if(!mesh.length)return false;
    if(c.chip){
      var b=bounds(mesh),axis=0;for(var n=1;n<3;n++)if(b[n+3]-b[n]>b[axis+3]-b[axis])axis=n;
      var step=(b[axis+3]-b[axis])/4,remaining=[];
      for(var i=0;i<4;i++){
        var segment=sliceMesh(mesh,axis,b[axis]+step*i,b[axis]+step*(i+1)),segmentKey=key+":"+i;
        if(c.dead||i<c.stage){
          if(!s.detached.has(segmentKey)&&!s.death&&c.spawned<32){spawn(segment,worldMatrix(nativeMatrix),s,texture,light,c.dead);s.detached.add(segmentKey);c.spawned++;}
        }else remaining.push.apply(remaining,segment);
      }
      if(remaining.length)api.drawLocal(remaining,texture,[1,1,1]);
      if(!c.dead)api.stump(b,axis,b[axis]+step*c.stage,s.color);
      return true;
    }
    if(!s.detached.has(key)&&!s.death&&c.spawned<32){
      spawn(mesh,worldMatrix(nativeMatrix),s,texture,light,c.dead);s.detached.add(key);c.spawned++;
      if(!c.dead){stats.amputations++;spray(point(worldMatrix(nativeMatrix),[0,0,0]),s,8,light);}
    }
    if(!c.dead){var box=bounds(mesh);api.stump(box,1,Math.max(box[1],Math.min(box[4],0)),s.color);}
    return true;
  }
  function physics(body,dt,isBlood) {
    if(api.light)body.light=api.light(world,body.p);
    if(body.rest)return;
    body.v[1]-=11*dt;
    var next=body.p.map(function(x,i){return x+body.v[i]*dt;});
    var floorOffset=0;
    if(body.mesh){
      var x=body.angle[0],y=body.angle[1],z=body.angle[2],sx=Math.sin(x),cx=Math.cos(x),sy=Math.sin(y),cy=Math.cos(y),sz=Math.sin(z),cz=Math.cos(z);
      var row=[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx];
      for(var vertex=0;vertex<body.mesh.length;vertex+=8)floorOffset=Math.min(floorOffset,row[0]*body.mesh[vertex]+row[1]*body.mesh[vertex+1]+row[2]*body.mesh[vertex+2]);
    }
    var from=body.p.slice(),to=next.slice();from[1]+=floorOffset;to[1]+=floorOffset;
    var hit=api.raycast(world,from,to);
    if(hit){
      body.p=hit.p.map(function(x,i){return x+hit.n[i]*.025-(i===1?floorOffset:0);});
      if(isBlood){
        boundedPush(stains,{p:body.p.slice(),n:hit.n,color:body.color,size:.06+body.random()*.16,born:now,life:11000,light:body.light},limits.stains);
        body.rest=true;body.life=0;return;
      }
      var dot=body.v[0]*hit.n[0]+body.v[1]*hit.n[1]+body.v[2]*hit.n[2];
      body.v=body.v.map(function(x,i){return (x-1.25*dot*hit.n[i])*.48;});
      body.spin=body.spin.map(function(x){return x*.45;});
      if(hit.n[1]>.5&&Math.abs(body.v[1])<.65){body.rest=true;}
    }else body.p=next;
    if(body.angle&&!body.rest)for(var j=0;j<3;j++)body.angle[j]+=body.spin[j]*dt;
  }
  function render(time) {
    context=null;now=time;
    var active=pieces.concat(drops);
    if(now-lastStep>=50){
      var dt=Math.min(.1,(now-lastStep)/1000);lastStep=now;
      var count=Math.min(active.length,limits.physics);
      for(var i=0;i<count;i++){var body=active[(cursor+i)%active.length];physics(body,dt,!body.mesh);}
      cursor=(cursor+count)%Math.max(1,active.length);
    }
    function alive(p){return now-p.born<p.life&&distance2(p.p,camera)<limits.range*limits.range;}
    pieces=pieces.filter(alive);drops=drops.filter(alive);stains=stains.filter(alive);
    if(!pieces.length&&!drops.length&&!stains.length)return;
    api.startEffects();
    try{
      pieces.forEach(function(p){api.drawPiece(p,camera,Math.min(1,(p.life-(now-p.born))/500));});
      api.drawBlood(drops,stains,camera);
    }finally{api.endEffects();}
  }
  return {frame:guarded(frame),begin:guarded(begin),end:guarded(end),part:guarded(drawPart,false),render:guarded(render),
    layer:guarded(function(model){if(context)context.layerModel=model||null;return !!(context&&context.dead);},false),
    hand:guarded(function(sign){return !!(context&&context.defs.some(function(d){var p=api.pivot(d.part);return context.state.cut.has(d.part)&&p[0]*sign>.5&&p[1]<8;}));},false),
    active: function(){return enabled&&!!context&&(context.dead||context.state.cut.size>0||context.chip&&context.stage>0);},
    status:function(){return {enabled:enabled,pieces:pieces.length,blood:drops.length,stains:stains.length,states:states.size,limits:limits,stats:Object.assign({},stats),coverage:Array.from(coverage,function(pair){return {type:pair[0],seen:pair[1].seen,deaths:pair[1].deaths,pieces:pair[1].pieces};}),lastError:lastError};},
    clear:clear,_math:{multiply:multiply,invert:invert,point:point,sliceMesh:sliceMesh,palette:palette}};
}
if(typeof module!=="undefined"&&module.exports)module.exports={createJasprGore:createJasprGore};
