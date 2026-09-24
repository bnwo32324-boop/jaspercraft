'use strict';
// Read-only introspection of the pinned TeaVM bundle in a sandbox with no browser/network APIs.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function inspect(file = path.join(__dirname, '../site/classes.js')) {
  const source = fs.readFileSync(file, 'utf8');
  const names = [...new Set([...source.matchAll(/\bfunction ([A-Za-z_$][\w$]*)\(/g)].map(m => m[1])
    .concat([...source.matchAll(/(?:var |;|,)([A-Z][\w$]*)\s*=/g)].map(m=>m[1])))];
  const exported = '\n$rt_exports.inspectFunctions={' + names.filter(n => !n.startsWith('$')).map(n => JSON.stringify(n)+':typeof '+n+'!=="undefined"?'+n+':null').join(',') + '};\n';
  const end = source.lastIndexOf('}));');
  if (end < 0) throw new Error('Unknown TeaVM module wrapper');
  const context = {exports:{},console,Math,Date,Array,ArrayBuffer,Int8Array,Uint8Array,Int16Array,Uint16Array,Int32Array,Uint32Array,Float32Array,Float64Array,BigInt64Array,BigUint64Array,BigInt,DataView,WebAssembly,TextEncoder,TextDecoder};
  context.global=context;
  vm.runInNewContext(source.slice(0,end)+exported+source.slice(end),context,{timeout:15000});
  const functions=context.exports.inspectFunctions;
  const types=Object.fromEntries(Object.entries(functions).filter(([,v])=>v&&v.$meta&&v.$meta.name).map(([key,value])=>[value.$meta.name,{key,value}]));
  return {types,functions};
}
if(require.main===module){
  const data=inspect();
  for(const query of process.argv.slice(2)){
    if(data.functions[query]){console.log(query+': '+data.functions[query]);continue;}
    for(const [name,{key,value}]of Object.entries(data.types))if(name.endsWith('.'+query)||name===query){
      console.log(name+' => '+key+' '+value);
      console.log(Object.getOwnPropertyNames(value.prototype).filter(p=>p!=='constructor').map(p=>p+': '+String(value.prototype[p]).slice(0,220)).join('\n'));
    }
  }
}
module.exports={inspect};
