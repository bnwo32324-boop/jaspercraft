'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),jdk=process.env.JAVA17_HOME?path.join(process.env.JAVA17_HOME,'bin'):'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-stats-unit-'));
const src=path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse'),tests=path.join(__dirname,'java/chat/jaspr/apocalypse');
function run(tool,args){const result=spawnSync(path.join(jdk,tool+'.exe'),args,{encoding:'utf8',windowsHide:true,cwd:fixture,timeout:90000,maxBuffer:8*1024*1024});assert.ifError(result.error);assert.equal(result.status,0,result.stderr+'\n'+result.stdout);return result.stdout;}
test('current pure stat rules compile; exhaustive XP conservation and six caps',()=>{
  run('javac',['--release','8','-encoding','UTF-8','-d',fixture,path.join(src,'StatRules.java'),path.join(src,'StatLifeStore.java'),path.join(tests,'StatsRulesTest.java'),path.join(tests,'StatsLifeStoreTest.java')]);
  const output=run('java',['-cp',fixture,'chat.jaspr.apocalypse.StatsRulesTest']);process.stdout.write(output);assert.match(output,/STATS_RULES_PASS/);
});
test('durable life checkpoints roundtrip and corrupt/unwritable storage fails closed',()=>{
  const output=run('java',['-cp',fixture,'chat.jaspr.apocalypse.StatsLifeStoreTest',fixture]);process.stdout.write(output);assert.match(output,/STATS_LIFE_PASS/);
});
