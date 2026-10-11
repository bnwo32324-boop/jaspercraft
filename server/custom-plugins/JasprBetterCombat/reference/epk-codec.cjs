'use strict';
// EPK v2 wire format documented by lax1dude/eagler-binary-tools (EPKCompiler / EPKDecompilerSP).
// Merge only code-native models; all other packaged resources must remain byte-identical.
const fs=require('node:fs');
const path=require('node:path');
const zlib=require('node:zlib');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const table=Array.from({length:256},(_,n)=>{for(let k=0;k<8;k++)n=n&1?0xedb88320^(n>>>1):n>>>1;return n>>>0;});
function crc(bytes){let c=0xffffffff;for(const b of bytes)c=table[(c^b)&255]^(c>>>8);return(c^0xffffffff)>>>0;}
function decode(bytes){
  assert.equal(bytes.subarray(0,8).toString(),'EAGPKG$$');assert.equal(bytes.subarray(-8).toString(),':::YEE:>');
  let at=8;const version=bytes.subarray(at+1,at+1+bytes[at]).toString();assert.equal(version,'ver2.0');at+=1+bytes[at];
  at+=1+bytes[at];at+=2+bytes.readUInt16BE(at);at+=8;
  const countOffset=at,count=bytes.readUInt32BE(at);at+=4;const compression=String.fromCharCode(bytes[at++]);
  const compressed=bytes.subarray(at,-8);
  const data=compression==='G'?zlib.gunzipSync(compressed):compression==='Z'?zlib.inflateSync(compressed):compressed;
  assert.ok(['G','Z','0'].includes(compression));let p=0;const entries=[];
  for(let i=0;i<count;i++){
    const begin=p,type=data.subarray(p,p+4).toString();p+=4;
    const length=data[p++],name=data.subarray(p,p+length).toString();p+=length;
    const size=data.readUInt32BE(p);p+=4;assert.ok(size<100*1024*1024);
    let value;
    if(type==='FILE'){
      assert.ok(size>=5);const check=data.readUInt32BE(p);p+=4;value=data.subarray(p,p+size-5);p+=size-5;
      assert.equal(crc(value),check,name);assert.equal(data[p++],58);
    }else{value=data.subarray(p,p+size);p+=size;}
    assert.equal(data[p++],62);entries.push({type,name,value,raw:data.subarray(begin,p)});
  }
  assert.equal(data.subarray(p).toString(),'END$');
  return {header:bytes.subarray(0,at),countOffset,compression,entries};
}
function fileEntry(name,value){
  assert.ok(Buffer.byteLength(name)<256);const len=Buffer.alloc(4),check=Buffer.alloc(4);len.writeUInt32BE(value.length+5);check.writeUInt32BE(crc(value));
  return Buffer.concat([Buffer.from('FILE'),Buffer.from([Buffer.byteLength(name)]),Buffer.from(name),len,check,value,Buffer.from(':>')]);
}
function merge(input,packDir){
  const parsed=decode(input),changed=new Map();
  function scan(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())scan(p);else{
    const name=path.relative(packDir,p).replaceAll('\\','/');
    if(!/^assets\/minecraft\/models\/item\/[a-z0-9_/-]+\.json$/.test(name))continue;
    const value=fs.readFileSync(p);JSON.parse(value);changed.set(name,value);
  }}}
  scan(packDir);assert.ok(changed.size>=4,'Three guns and base item override are required.');
  // Eagler's resource archive removes the outer assets/ prefix.
  const prefix=parsed.entries.some(e=>e.name==='assets/minecraft/models/item/diamond_hoe.json')?'':'assets/';
  const replacements=new Map([...changed].map(([n,v])=>[prefix?n.slice(prefix.length):n,v]));
  const outputEntries=parsed.entries.map(e=>replacements.has(e.name)?{...e,value:replacements.get(e.name),raw:fileEntry(e.name,replacements.get(e.name))}:e);
  for(const [name,value] of replacements)if(!parsed.entries.some(e=>e.name===name))outputEntries.push({type:'FILE',name,value,raw:fileEntry(name,value)});
  const header=Buffer.from(parsed.header);header.writeUInt32BE(outputEntries.length,parsed.countOffset);
  const payload=Buffer.concat([...outputEntries.map(e=>e.raw),Buffer.from('END$')]);
  const compressed=parsed.compression==='G'?zlib.gzipSync(payload,{level:9}):parsed.compression==='Z'?zlib.deflateSync(payload,{level:9}):payload;
  const output=Buffer.concat([header,compressed,Buffer.from(':::YEE:>')]);
  const verified=decode(output),byName=new Map(verified.entries.map(e=>[e.name,e]));
  for(const before of parsed.entries)if(!replacements.has(before.name))assert.deepEqual(byName.get(before.name).raw,before.raw,before.name);
  for(const [name,value]of replacements)assert.deepEqual(byName.get(name).value,value,name);
  // Resolve against the real client archive, not an assumed list of modern texture names.
  // Minecraft 1.12 calls dark-oak planks "planks_big_oak", for example.
  const resourcePath=(reference,type,extension)=>{
    const [namespace,local]=reference.includes(':')?reference.split(':'):['minecraft',reference];
    return (prefix?'':'assets/')+namespace+'/'+type+'/'+local+extension;
  };
  for(const [name,value]of replacements){
    const model=JSON.parse(value);
    for(const texture of Object.values(model.textures||{}))if(!texture.startsWith('#'))
      assert.ok(byName.has(resourcePath(texture,'textures','.png')),name+': missing texture '+texture);
    if(model.parent&&!model.parent.startsWith('builtin/'))
      assert.ok(byName.has(resourcePath(model.parent,'models','.json')),name+': missing parent '+model.parent);
    for(const override of model.overrides||[])
      assert.ok(byName.has(resourcePath(override.model,'models','.json')),name+': missing override '+override.model);
  }
  return {output,models:[...replacements.keys()],unchangedEntries:parsed.entries.length-[...replacements.keys()].filter(n=>parsed.entries.some(e=>e.name===n)).length};
}
if(require.main===module){
  const input=fs.readFileSync(path.join(root,'site','assets.epk'));
  const result=merge(input,path.join(root,'apocalypse-pack'));
  const target=path.join(root,'candidate','apocalypse');fs.mkdirSync(target,{recursive:true});
  fs.writeFileSync(path.join(target,'assets.epk'),result.output);
  const report={models:result.models,unchangedEntries:result.unchangedEntries,beforeBytes:input.length,afterBytes:result.output.length,
    sha256:crypto.createHash('sha256').update(result.output).digest('hex')};
  fs.writeFileSync(path.join(target,'asset-merge-report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}
module.exports={decode,merge};
