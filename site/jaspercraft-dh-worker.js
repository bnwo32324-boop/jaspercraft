/* Browser worker for the LGPL-3.0 DH adaptation; all decoding/meshing/storage
 * runs here. The caller allows at most one job and a bounded pending queue. */
'use strict';
importScripts('jaspercraft-dh-core.js?build=20260920-dh1');
var database=null,openPromise=null,storageFailed=false;
function request(r){return new Promise(function(ok,no){r.onsuccess=function(){ok(r.result);};r.onerror=function(){no(r.error);};});}
function opened(){
 if(storageFailed)return Promise.resolve(null);
 if(!openPromise)openPromise=new Promise(function(ok){
  try{var r=indexedDB.open('jaspr.distant-terrain.v1',1);r.onupgradeneeded=function(){var s=r.result.createObjectStore('terrain',{keyPath:'key'});s.createIndex('world','world');s.createIndex('coords',['world','x','z']);s.createIndex('used',['used','bytes']);};r.onsuccess=function(){database=r.result;database.onversionchange=function(){database.close();database=null;openPromise=null;};ok(database);};r.onerror=function(){storageFailed=true;ok(null);};r.onblocked=function(){storageFailed=true;ok(null);};}catch(_){storageFailed=true;ok(null);}
 });return openPromise;
}
async function save(db,item,limit){
 if(!db||limit===0)return;
 try{
  var tx=db.transaction('terrain','readwrite'),store=tx.objectStore('terrain');store.put(item);
  // Bound the entire cache, across worlds and all presets, not just this session.
  // Key cursors never deserialize every cached chunk just to count their bytes.
  var rows=[],total=0,cursor=store.index('used').openKeyCursor();
  await new Promise(function(ok,no){cursor.onsuccess=function(){var c=cursor.result;if(c){total+=c.key[1];rows.push([c.primaryKey,c.key[1]]);c.continue();}else{for(var i=0;total>limit&&i<rows.length;i++){total-=rows[i][1];store.delete(rows[i][0]);}ok();}};cursor.onerror=no;});
 }catch(_){storageFailed=true;}
}
var chain=Promise.resolve();
onmessage=function(event){chain=chain.then(async function(){
 var m=event.data,started=performance.now(),db=await opened();
 try{
  if(m.op==='keys'){
   var keys=[];if(db){var cursor=db.transaction('terrain').objectStore('terrain').index('coords').openKeyCursor(IDBKeyRange.bound([m.world,-Infinity,-Infinity],[m.world,Infinity,Infinity]));await new Promise(function(ok,no){cursor.onsuccess=function(){var c=cursor.result;if(c&&keys.length<4096){keys.push([c.key[1],c.key[2]]);c.continue();}else ok();};cursor.onerror=no;});}
   postMessage({op:m.op,epoch:m.epoch,keys:keys,memoryOnly:!db});return;
  }
  if(m.op==='clear'){
   if(db){var tx=db.transaction('terrain','readwrite'),s=tx.objectStore('terrain'),cursor=s.index('world').openCursor(m.world);await new Promise(function(ok,no){cursor.onsuccess=function(){var c=cursor.result;if(c){c.delete();c.continue();}else ok();};cursor.onerror=no;});}
   postMessage({op:m.op,epoch:m.epoch});return;
  }
  if(m.op==='invalidate'){
   if(db)db.transaction('terrain','readwrite').objectStore('terrain').delete(m.world+'/'+m.x+','+m.z);
   postMessage({op:m.op,epoch:m.epoch,x:m.x,z:m.z});return;
  }
  var item=m.op==='load'?(db?await request(db.transaction('terrain').objectStore('terrain').get(m.world+'/'+m.x+','+m.z)):null):{key:m.world+'/'+m.x+','+m.z,world:m.world,x:m.x,z:m.z,raw:m.raw,bytes:m.raw.byteLength,mask:m.mask,sky:m.sky,used:Date.now()};
  if(!item){postMessage({op:'missing',epoch:m.epoch,x:m.x,z:m.z});return;}
  var decoded=JasprDHCore.decode(item.raw,item.mask,item.sky),data=JasprDHCore.columns(decoded,m.palette,m.stride,m.caves),mesh=JasprDHCore.mesh(data);
  if(mesh.byteLength>4*1024*1024)throw Error('Mesh exceeds per-chunk limit');
  if(m.op==='build')await save(db,item,m.cacheMB*1024*1024);
  var minY=256,maxY=0;for(var i=1;i<mesh.length;i+=7){minY=Math.min(minY,mesh[i]);maxY=Math.max(maxY,mesh[i]);}
  postMessage({op:'mesh',epoch:m.epoch,x:m.x,z:m.z,version:m.version||0,stride:m.stride,minY:minY,maxY:maxY,mesh:mesh.buffer,ms:performance.now()-started,memoryOnly:!db},[mesh.buffer]);
 }catch(e){postMessage({op:'error',epoch:m.epoch,x:m.x,z:m.z,error:String(e.message||e).slice(0,160)});}
 }).catch(function(){});};
