'use strict';
const fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../site/classes.js'),'utf8');
const map=JSON.parse(fs.readFileSync(path.join(__dirname,'../site/classes.js.map'),'utf8'));
const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const values=Object.fromEntries([...alphabet].map((c,i)=>[c,i]));
function vlq(s){let n=0,shift=0,result=[];for(const c of s){const v=values[c];n+=(v&31)*Math.pow(2,shift);if(v&32)shift+=5;else{result.push((n&1)?-(n>>>1):(n>>>1));n=0;shift=0;}}return result;}
const offsets=[0];for(let i=0;i<source.length;i++)if(source[i]==='\n')offsets.push(i+1);
const functions=[...source.matchAll(/function ([A-Za-z_$][\w$]*)\(/g)].map(m=>({name:m[1],offset:m.index}));
function enclosing(offset){let low=0,high=functions.length-1;while(low<high){const mid=Math.ceil((low+high)/2);if(functions[mid].offset<=offset)low=mid;else high=mid-1;}return functions[low];}
let file=0,line=0,column=0;const result=new Map();
const wanted=new Set(map.sources.map((s,i)=>process.argv.slice(2).some(n=>s.endsWith('/'+n+'.java'))?i:-1));
map.mappings.split(';').forEach((encoded,generatedLine)=>{let generatedColumn=0;for(const segment of encoded.split(',')){if(!segment)continue;const data=vlq(segment);generatedColumn+=data[0];if(data.length<4)continue;file+=data[1];line+=data[2];column+=data[3];if(!wanted.has(file))continue;const f=enclosing((offsets[generatedLine]||0)+generatedColumn);const key=map.sources[file]+' '+f.name;if(!result.has(key))result.set(key,{at:f.offset,min:line+1,max:line+1,count:0});const r=result.get(key);r.min=Math.min(r.min,line+1);r.max=Math.max(r.max,line+1);r.count++;}});
for(const [key,r]of result)console.log(key+' '+JSON.stringify(r));
