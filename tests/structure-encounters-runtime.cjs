'use strict';
// Build from current sources, then run two isolated Paper processes to verify real restart persistence.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),jdk='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-encounter-runtime-')),server=path.join(fixture,'server');
const api=path.join(root,'server/cache/patched_1.12.2.jar'),classes=path.join(fixture,'classes'),probe=path.join(fixture,'probe');
function write(file,data){fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,data);}
function run(tool,args){const r=spawnSync(path.join(jdk,tool+'.exe'),args,{cwd:fixture,encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);if(r.stdout)process.stdout.write(r.stdout);}
function sources(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(f=>f.isDirectory()?sources(path.join(dir,f.name)):f.name.endsWith('.java')?[path.join(dir,f.name)]:[]);}
async function freePort(){const socket=net.createServer();await new Promise(resolve=>socket.listen(0,'127.0.0.1',resolve));const port=socket.address().port;await new Promise(resolve=>socket.close(resolve));return port;}
async function boot(phase){
 const log=fs.createWriteStream(path.join(fixture,`paper-${phase}.log`));let tail='',passed=false;
 const child=spawn(path.join(jdk,'java.exe'),['-Xms128M','-Xmx768M','-XX:ActiveProcessorCount=2','-Djaspr.biomes.fixture=true','-Djaspr.encounters.fixture=true','-Djava.awt.headless=true','-Dcom.mojang.eula.agree=true','-jar',api,'--nojline'],{cwd:server,windowsHide:true,stdio:['pipe','pipe','pipe']});
 const timeout=setTimeout(()=>{child.kill();},180000);
 const out=data=>{log.write(data);tail=(tail+data).slice(-24000);if(String(data).includes(`ENCOUNTER_RUNTIME_PHASE${phase}_PASS`))passed=true;if(/ENCOUNTER_|ERROR|Exception|Done \(/.test(String(data)))process.stdout.write(data);};
 child.stdout.on('data',out);child.stderr.on('data',out);
 const code=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',resolve);});clearTimeout(timeout);log.end();
 if(code!==0||!passed||tail.includes('ENCOUNTER_RUNTIME_FAIL'))throw Error(`Phase ${phase} failed; fixture=${fixture}\n${tail}`);
}
(async()=>{
 fs.mkdirSync(classes,{recursive:true});fs.mkdirSync(probe,{recursive:true});fs.mkdirSync(path.join(server,'plugins'),{recursive:true});
 const src=path.join(root,'server/custom-plugins/JasprHorrorBiomes');
 run('javac',['--release','8','-encoding','UTF-8','-cp',api,'-d',classes,...sources(path.join(src,'src')),path.join(root,'tests/java/chat/jaspr/biomes/EncounterJournalContractTest.java'),path.join(root,'tests/java/chat/jaspr/biomes/EncounterRuntimeProbe.java')]);
 run('java',['-cp',classes,'chat.jaspr.biomes.EncounterJournalContractTest']);
 fs.cpSync(path.join(src,'resources'),classes,{recursive:true});
 const hostJar=path.join(server,'plugins/JasprHorrorBiomes.jar');run('jar',['--create','--file',hostJar,'-C',classes,'.']);
 run('javac',['--release','8','-encoding','UTF-8','-cp',api+';'+classes,'-d',probe,path.join(root,'tests/java/chat/jaspr/biomes/EncounterRuntimeBootstrap.java')]);
 write(path.join(probe,'plugin.yml'),'name: EncounterRuntimeProbe\nmain: chat.jaspr.biomes.EncounterRuntimeBootstrap\nversion: 1\ndepend: [JasprHorrorBiomes]\n');
 run('jar',['--create','--file',path.join(server,'plugins/EncounterRuntimeProbe.jar'),'-C',probe,'.']);
 write(path.join(server,'eula.txt'),'eula=true\n');
 write(path.join(server,'server.properties'),`server-ip=127.0.0.1\nserver-port=${await freePort()}\nonline-mode=false\nlevel-name=world\nlevel-seed=691208467\nlevel-type=FLAT\nspawn-protection=0\nview-distance=3\ngenerate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=true\nspawn-npcs=false\nmax-players=1\nenable-rcon=false\nenable-query=false\n`);
 write(path.join(server,'bukkit.yml'),'settings:\n  allow-end: false\n');
 write(path.join(server,'paper.yml'),'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n');
 write(path.join(server,'spigot.yml'),'config-version: 11\nsettings:\n  late-bind: true\n');
 write(path.join(server,'plugins/bStats/config.yml'),'enabled: false\n');
 write(path.join(server,'plugins/JasprHorrorBiomes/terrain-epoch.txt'),'details-v3\n');
 console.log('Encounter fixture: '+fixture);await boot(1);await boot(2);console.log('ENCOUNTER_RUNTIME_ALL_PASS '+fixture);
})().catch(error=>{console.error(error.stack);process.exitCode=1;});
