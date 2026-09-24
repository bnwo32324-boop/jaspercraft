'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),plugin=path.join(root,'server/custom-plugins/JasprHorrorBiomes'),api=path.join(root,'server/cache/patched_1.12.2.jar');
const jdk='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
function run(command,args){const r=cp.spawnSync(path.join(jdk,command+'.exe'),args,{encoding:'utf8',windowsHide:true,timeout:220000,maxBuffer:4*1024*1024});assert.equal(r.status,0,(r.error||'')+'\n'+r.stdout+'\n'+r.stderr);return r.stdout;}
test('sparse planner retains a deterministic 10% subset of prior sites and preserves each admitted layout',{timeout:300000},()=>{
 const output=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-expansion-jvm-')),baseline=path.join(output,'legacy');fs.mkdirSync(baseline);
 const spec=path.join(__dirname,'java/chat/jaspr/biomes/LegacyPlanFingerprint.java'),oldJar=path.join(root,'candidate/details-v3-baseline/JasprHorrorBiomes.jar');
 assert.ok(fs.existsSync(oldJar),'Exact previous deployed plugin snapshot required');
 run('javac',['--release','8','-encoding','UTF-8','-cp',[oldJar,api].join(path.delimiter),'-d',baseline,spec]);
 const old=run('java',['-cp',[baseline,oldJar,api].join(path.delimiter),'chat.jaspr.biomes.LegacyPlanFingerprint','--rows']);
 const sources=fs.readdirSync(path.join(plugin,'src/chat/jaspr/biomes')).filter(n=>n.endsWith('.java')).map(n=>path.join(plugin,'src/chat/jaspr/biomes',n));
 run('javac',['--release','8','-encoding','UTF-8','-cp',api,'-d',output,...sources,spec,path.join(__dirname,'java/chat/jaspr/biomes/WorldgenExpansionTest.java')]);
 const classpath=[output,api,path.join(plugin,'resources')].join(path.delimiter);
 const current=run('java',['-cp',classpath,'chat.jaspr.biomes.LegacyPlanFingerprint','--rows']);
 const siteRows=text=>text.split(/\r?\n/).filter(line=>line.startsWith('SITE '));
 const oldRows=new Set(siteRows(old)),currentRows=siteRows(current);
 assert.ok(currentRows.length>0,'Sparse subset must remain discoverable');
 for(const row of currentRows)assert.ok(oldRows.has(row),'Every retained site keeps its exact anchor, design, loot and boss ordinals');
 const ratio=currentRows.length/oldRows.size;assert.ok(ratio>=.07&&ratio<=.13,`Expected approximately 10% of prior sites, got ${(ratio*100).toFixed(2)}%`);
 process.stdout.write(current.split(/\r?\n/).filter(line=>!line.startsWith('SITE ')).join('\n')+'\n');
 const result=run('java',['-Xmx768m','-cp',classpath,'chat.jaspr.biomes.WorldgenExpansionTest']);assert.match(result,/WORLDGEN_EXPANSION_PASS/);process.stdout.write(result);
});
