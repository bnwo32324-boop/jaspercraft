'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),plugin=path.join(root,'server/custom-plugins/JasprHorrorBiomes'),api=path.join(root,'server/cache/patched_1.12.2.jar');
function run(command,args,options={}){const result=cp.spawnSync(command,args,{encoding:'utf8',windowsHide:true,timeout:180000,maxBuffer:8*1024*1024,...options});assert.equal(result.status,0,String(result.error||'')+(result.stdout||'')+(result.stderr||''));return result.stdout;}
test('both structure grids retain ten percent globally and exclude the first 3,072 blocks',{timeout:240000},()=>{
 const output=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-rarity-audit-'));
 const source=path.join(plugin,'src/chat/jaspr/biomes'),sources=fs.readdirSync(source).filter(name=>name.endsWith('.java')).map(name=>path.join(source,name));
 run('javac',['--release','8','-encoding','UTF-8','-cp',api,'-d',output,...sources,path.join(root,'tests/java/chat/jaspr/biomes/StructureRarityAudit.java')]);
 const report=run('java',['-Xmx768m','-cp',[output,api,path.join(plugin,'resources')].join(path.delimiter),'chat.jaspr.biomes.StructureRarityAudit']);
 process.stdout.write(report);assert.match(report,/STRUCTURE_RARITY_PASS expectedRetention=10\.000%/);
 assert.match(report,/SEED_RARITY seed=4696544777213599765 .*nearestStructureEdge=(?:3\d{3}|[4-9]\d{3}|\d{5,})\.\d/);
});
