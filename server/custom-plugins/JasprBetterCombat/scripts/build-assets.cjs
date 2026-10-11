'use strict';
const fs=require('fs'),path=require('path'),zlib=require('zlib'),assert=require('assert/strict');
const {decode}=require('../reference/epk-codec.cjs');
const root=path.resolve(__dirname,'..');
function build(input,files){
 const data=decode(input),entries=data.entries.map(e=>e);
 const table=new Uint32Array(256).map((_,n)=>{let c=n;for(let i=0;i<8;i++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
 for(const [name,value] of files){
  let crc=0xffffffff;for(const b of value)crc=table[(crc^b)&255]^(crc>>>8);
  const size=Buffer.alloc(4),check=Buffer.alloc(4);size.writeUInt32BE(value.length+5);check.writeUInt32BE((crc^0xffffffff)>>>0);
  const e={name,type:'FILE',value,raw:Buffer.concat([Buffer.from('FILE'),Buffer.from([Buffer.byteLength(name)]),Buffer.from(name),size,check,value,Buffer.from(':>')])};
  let i=entries.findIndex(e=>e.name===name);if(i<0)entries.push(e);else entries[i]=e;
 }
 const header=Buffer.from(data.header);header.writeUInt32BE(entries.length,data.countOffset);
 const body=Buffer.concat([...entries.map(e=>e.raw),Buffer.from('END$')]);
 const output=Buffer.concat([header,data.compression==='G'?zlib.gzipSync(body):data.compression==='Z'?zlib.deflateSync(body):body,Buffer.from(':::YEE:>')]);
 const after=new Map(decode(output).entries.map(e=>[e.name,e]));
 for(const e of data.entries)if(!files.has(e.name))assert.deepEqual(after.get(e.name).raw,e.raw,e.name);
 for(const [name,value]of files)assert.deepEqual(after.get(name).value,value,name);
 return {output,unchanged:data.entries.filter(e=>!files.has(e.name)).length};
}
function combatFiles(input){
 const dir=path.join(root,'reference/jar-assets/assets/bettercombatmod');
 const files=new Map();
 for(const f of ['sounds.json','sounds/swordslash.ogg','sounds/criticalstrike.ogg'])files.set('assets/bettercombatmod/'+f,fs.readFileSync(path.join(dir,f)));
 // This browser's built-in resource pack only enumerates its built-in domains.
 // Register scoped aliases there; retain the release files and OGG bytes unchanged.
 const original=decode(input).entries.find(e=>e.name==='assets/minecraft/sounds.json');
 assert.ok(original,'native sound registry');const registry=JSON.parse(original.value);
 for(const name of ['swordslash','criticalstrike']){
  const key='jaspr.bettercombat.player.'+name;
  const event={sounds:[{name:'minecraft:jaspr/bettercombat/'+name,stream:false}]};
  if(registry[key])assert.deepEqual(registry[key],event,'conflicting sound alias '+key);
  registry[key]=event;
  files.set('assets/minecraft/sounds/jaspr/bettercombat/'+name+'.ogg',files.get('assets/bettercombatmod/sounds/'+name+'.ogg'));
 }
 files.set('assets/minecraft/sounds.json',Buffer.from(JSON.stringify(registry)));
 return files;
}
if(require.main===module){
 const input=fs.readFileSync(process.argv[2]||path.join(root,'baseline/assets.epk'));
 const r=build(input,combatFiles(input));
 fs.mkdirSync(path.join(root,'build/client'),{recursive:true});fs.writeFileSync(path.join(root,'build/client/assets.epk'),r.output);
 console.log(JSON.stringify({added:5,mergedSoundRegistry:true,unchanged:r.unchanged,bytes:r.output.length}));
}
module.exports={build,combatFiles};
