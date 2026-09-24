'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..');

test('spawn safety rejects stale Y=0 metadata and chooses a bounded supported landing',()=>{
 const scratch=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-spawn-safety-'));
 const javaHome=process.env.JAVA_HOME||'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot';
 const tool=name=>path.join(javaHome,'bin',name+'.exe'),api=path.join(root,'server/cache/patched_1.12.2.jar');
 const sources=[path.join(root,'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/SpawnSafety.java'),path.join(root,'tests/SpawnSafetyTest.java')];
 const run=(name,args)=>{const result=cp.spawnSync(tool(name),args,{cwd:scratch,encoding:'utf8',timeout:60000,maxBuffer:1024*1024});assert.ifError(result.error);assert.equal(result.status,0,result.stdout+'\n'+result.stderr);return result.stdout;};
 run('javac',['--release','8','-encoding','UTF-8','-cp',api,'-d',scratch,...sources]);
 const output=run('java',['-ea','-cp',[scratch,api].join(path.delimiter),'chat.jaspr.biomes.SpawnSafetyTest']);
 assert.match(output,/SPAWN_SAFETY_OK assertions=10/);process.stdout.write(output);
});
