'use strict';
// Compile only the owned arsenal classes into an isolated copy of the existing release candidate.
// Never publishes the shared candidate, touches live files, starts the gateway, or resets a world.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {spawnSync,spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'),jdk='C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.8-hotspot';
const fixture=path.join(root,'candidate','arsenal-expansion-build-'+crypto.randomUUID());
fs.mkdirSync(fixture);const classes=path.join(fixture,'classes');fs.mkdirSync(classes);
const jar=path.join(fixture,'JasprApocalypse.jar');
fs.copyFileSync(path.join(root,'candidate/apocalypse/JasprApocalypse.jar'),jar);
function run(name,args) {
  const result=spawnSync(path.join(jdk,'bin',name+'.exe'),args,{cwd:fixture,encoding:'utf8',windowsHide:true,timeout:90000});
  if(result.error||result.status!==0)throw result.error||new Error(result.stderr||result.stdout);
}
run('javac',['--release','8','-encoding','UTF-8','-proc:none','-cp',
  [path.join(root,'server/cache/patched_1.12.2.jar'),path.join(root,'server/plugins/AuthMe.jar'),jar].join(path.delimiter),
  '-sourcepath',path.join(fixture,'empty-sourcepath'),'-d',classes,
  ...['Arsenal.java','ApocalypseItems.java','Blueprints.java','ExpeditionEquipment.java'].map(f=>path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse',f))]);
run('jar',['--update','--file',jar,'-C',classes,'.']);
console.log(JSON.stringify({fixture,jar,sha256:crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex')}));
const child=spawn(process.execPath,[path.join(root,'tests/equipment-smoke.cjs'),'--paper','--java-home='+jdk,'--wait-seconds=0','--timeout-seconds=240'],
  {cwd:root,env:{...process.env,JASPR_APOCALYPSE_TEST_JAR:jar},windowsHide:true,stdio:'inherit'});
child.on('error',error=>{console.error(error);process.exitCode=1;});
child.on('exit',code=>{process.exitCode=code===0?0:1;});
