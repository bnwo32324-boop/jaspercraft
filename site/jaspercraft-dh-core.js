/* Browser adaptation of Distant Horizons data/column geometry, LGPL-3.0.
 * Copyright (C) 2020 James Seibel and contributors.
 * Pinned upstream core: 431aa084d28c8cf8d6191a0d597d23ee75323d6f (3.1.2b).
 * RenderDataPointUtil's bit layout/accessors are retained exactly using two
 * uint32 words instead of Java long; this avoids BigInt allocation in workers.
 * ColumnBox's touching-top/bottom and adjacent-interval occlusion rules are
 * adapted to typed-array WebGL meshes. This is NOT the unmodified Forge mod.
 * Original sources, licenses and compatibility differences accompany the port.
 */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory();else root.JasprDHCore=factory();})(typeof globalThis==='object'?globalThis:this,function(){
 'use strict';
 function pack(alpha,r,g,b,top,bottom,sky,light,material){return [((top&4095)<<20|(bottom&4095)<<8|(light&15)<<4|sky&15)>>>0,((material&15)<<28|(alpha>>>4)<<24|(r&255)<<16|(g&255)<<8|b&255)>>>0];}
 function unpack(lo,hi){return {top:(lo>>>20)&4095,bottom:(lo>>>8)&4095,sky:lo&15,light:lo>>>4&15,alpha:((hi>>>24&15)<<4)|15,r:hi>>>16&255,g:hi>>>8&255,b:hi&255,material:hi>>>28};}
 function decode(bytes,mask,sky){
  bytes=new Uint8Array(bytes);var view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength),p=0,blocks=new Uint16Array(65536),lights=new Uint8Array(65536);
  if(sky)lights.fill(15); // Missing sections above terrain are sunlit air.
  function need(n){if(p+n>bytes.length)throw Error('Truncated chunk');}
  function byte(){need(1);return bytes[p++];}
  function vint(){var n=0;for(var j=0;j<5;j++){var b=byte();n|=(b&127)<<(7*j);if(!(b&128))return n>>>0;}throw Error('Oversized VarInt');}
  for(var section=0;section<16;section++){
   if(!(mask&1<<section))continue;
   var bits=byte();if(bits<4)bits=4;if(bits>8)bits=13;
   var count=vint();if(count>4096)throw Error('Invalid palette');var palette=new Uint16Array(count);for(var j=0;j<count;j++)palette[j]=vint();
   var longs=vint(),expected=Math.ceil(4096*bits/64);if(longs!==expected)throw Error('Invalid packed array');need(longs*8);
   var words=new Uint32Array(longs*2);for(var j=0;j<longs;j++){words[j*2+1]=view.getUint32(p);words[j*2]=view.getUint32(p+4);p+=8;}
   var base=section*4096,bitmask=(1<<bits)-1;
   for(var j=0;j<4096;j++){var start=j*bits,w=start>>>5,shift=start&31,value=words[w]>>>shift;if(shift+bits>32)value|=words[w+1]<<(32-shift);value&=bitmask;if(bits<=8){if(value>=palette.length)throw Error('Invalid palette index');value=palette[value];}blocks[base+j]=value;}
   need(2048);for(var j=0;j<4096;j++)lights[base+j]=((bytes[p+(j>>>1)]>>>((j&1)*4))&15)<<4;p+=2048;
   if(sky){need(2048);for(var j=0;j<4096;j++)lights[base+j]|=(bytes[p+(j>>>1)]>>>((j&1)*4))&15;p+=2048;}
  }
  return {blocks:blocks,lights:lights};
 }
 // Colors come from the running client's block material palette, not a second
 // hard-coded block registry. Vertical air gaps are kept (bridges/caves/arches).
 function columns(decoded,palette,stride,cullCaves){
  var result=[],n=16/stride,blocks=decoded.blocks,lights=decoded.lights;
  for(var z=0;z<16;z+=stride)for(var x=0;x<16;x+=stride){
   var list=[],last=null;
   for(var y=0;y<256;y++){
    var count=0,r=0,g=0,b=0,sky=0,light=0,water=0;
    for(var dz=0;dz<stride;dz++)for(var dx=0;dx<stride;dx++){
     var index=y*256+(z+dz)*16+x+dx,id=blocks[index]>>>4;if(!id)continue;
     var rgb=palette[id]===undefined?0x888888:palette[id];if(id===8||id===9)water++;
     // Use exposed light, not the zero skylight inside an opaque solid block.
     var exposed=y<255?lights[index+256]:15;
     count++;r+=(rgb>>>16&255)**2;g+=(rgb>>>8&255)**2;b+=(rgb&255)**2;sky=Math.max(sky,lights[index]&15,exposed&15);light=Math.max(light,lights[index]>>>4,exposed>>>4);
    }
    if(!count||cullCaves&&sky===0&&y<56){last=null;continue;}
    // Browser-side RMS material-color average; upstream texture sampling is unavailable here.
    var color=((Math.sqrt(r/count)|0)>>>3)<<19|((Math.sqrt(g/count)|0)>>>3)<<11|((Math.sqrt(b/count)|0)>>>3)<<3;
    var material=water===count?1:0;
    if(last&&last.top===y&&last.color===color&&last.material===material&&last.sky===sky&&last.light===light)last.top++;
    else {last={bottom:y,top:y+1,color:color,material:material,sky:sky,light:light};list.push(last);}
   }
   result.push(list.map(function(s){return pack(255,s.color>>>16&255,s.color>>>8&255,s.color&255,s.top,s.bottom,s.sky,s.light,s.material);}));
  }
  return {columns:result,n:n,stride:stride};
 }
 function subtract(bottom,top,neighbors){
  var out=[],cursor=bottom;
  for(var i=0;i<neighbors.length;i++){var n=neighbors[i];if(n.top<=cursor||n.material)continue;if(n.bottom>=top)break;if(n.bottom>cursor)out.push([cursor,Math.min(n.bottom,top)]);cursor=Math.max(cursor,n.top);if(cursor>=top)return out;}
  if(cursor<top)out.push([cursor,top]);return out;
 }
 function mesh(data){
  var out=[],n=data.n,step=data.stride,all=data.columns.map(function(col){return col.map(function(p){var s=unpack(p[0],p[1]);s.color=s.r<<16|s.g<<8|s.b;return s;});});
  function face(points,s,shade){var rgb=s.color,light=Math.max(.12,s.sky/15,s.light/15),c=[(rgb>>>16&255)/255*shade*light,(rgb>>>8&255)/255*shade*light,(rgb&255)/255*shade*light];
   if(out.length+42>1048576)throw Error('Mesh exceeds per-chunk limit');
   for(var j of [0,1,2,0,2,3])out.push(points[j][0],points[j][1],points[j][2],c[0],c[1],c[2],s.material);
  }
  for(var z=0;z<n;z++)for(var x=0;x<n;x++){
   var col=all[z*n+x],x0=x*step,x1=x0+step,z0=z*step,z1=z0+step;
   for(var i=0;i<col.length;i++){
    var s=col[i],y0=s.bottom,y1=s.top;
    // ColumnBox: suppress touching opaque top/bottom faces.
    if(!col[i+1]||col[i+1].bottom!==y1||col[i+1].material)face([[x0,y1,z0],[x0,y1,z1],[x1,y1,z1],[x1,y1,z0]],s,1);
    if(!col[i-1]||col[i-1].top!==y0||col[i-1].material)face([[x0,y0,z1],[x0,y0,z0],[x1,y0,z0],[x1,y0,z1]],s,.5);
    var neighbors=[z>0?all[(z-1)*n+x]:[],z+1<n?all[(z+1)*n+x]:[],x>0?all[z*n+x-1]:[],x+1<n?all[z*n+x+1]:[]];
    for(var side=0;side<4;side++)for(var span of subtract(y0,y1,neighbors[side])){
     var a=span[0],b=span[1],p=side===0?[[x1,a,z0],[x0,a,z0],[x0,b,z0],[x1,b,z0]]:side===1?[[x0,a,z1],[x1,a,z1],[x1,b,z1],[x0,b,z1]]:side===2?[[x0,a,z0],[x0,a,z1],[x0,b,z1],[x0,b,z0]]:[[x1,a,z1],[x1,a,z0],[x1,b,z0],[x1,b,z1]];
     face(p,s,side<2?.8:.6);
    }
   }
  }
  return new Float32Array(out);
 }
 return {pack:pack,unpack:unpack,decode:decode,columns:columns,subtract:subtract,mesh:mesh};
});
