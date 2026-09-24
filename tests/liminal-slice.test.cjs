'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..');

test('liminal slice: real 1.12.2 chunk geometry, door travel, auth, persistence and recovery',{timeout:90000},()=>{
  const scratch=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-liminal-test-'));
  const api=path.join(root,'server/cache/patched_1.12.2.jar');
  assert.ok(fs.existsSync(api),'Local 1.12.2 API jar is required');
  const javaHome=process.env.JAVA_HOME||'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot';
  const executable=name=>path.join(javaHome,'bin',name+(process.platform==='win32'?'.exe':''));
  const sourceRoot=path.join(root,'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes');
  const sources=fs.readdirSync(sourceRoot).filter(name=>name.endsWith('.java')).map(name=>path.join(sourceRoot,name));
  sources.push(path.join(root,'tests/LiminalSliceTest.java'),path.join(root,'tests/liminal-auth/fr/xephi/authme/api/v3/AuthMeApi.java'));
  function run(command,args){const result=cp.spawnSync(command,args,{cwd:scratch,encoding:'utf8',timeout:60000,maxBuffer:4*1024*1024});assert.ifError(result.error);assert.equal(result.status,0,result.stdout+'\n'+result.stderr);return result.stdout;}
  const dependencies=api;
  run(executable('javac'),['--release','8','-encoding','UTF-8','-cp',dependencies,'-d',scratch,...sources]);
  assert.equal(fs.readFileSync(path.join(scratch,'chat/jaspr/biomes/LiminalWorld.class')).readUInt16BE(6),52,'Java 8 bytecode');
  const output=run(executable('java'),['-ea','-cp',[scratch,dependencies].join(path.delimiter),'chat.jaspr.biomes.LiminalSliceTest',scratch]);
  assert.match(output,/LIMINAL_TESTS_OK/);
  process.stdout.write(output);
});
