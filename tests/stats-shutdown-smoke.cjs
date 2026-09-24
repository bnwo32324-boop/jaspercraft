'use strict';
// Independent online CraftPlayer shutdown/save/restart/manual-disable regression, across three JVMs.
// Production is snapshotted read-only; the third JVM exercises Bukkit's real plugin-disable API.
// Owns only StatsShutdownProbe.java and this runner; shared QA fixtures are not modified.
// No shared candidate, live file, launcher, gateway, production identity, or public port is used.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),net=require('node:net'),crypto=require('node:crypto');
const {spawn,spawnSync}=require('node:child_process');
const jvmHook=process.argv.includes('--jvm-hook');
if(process.argv.slice(2).some(arg=>arg!=='--jvm-hook'))throw new Error('Usage: node tests/stats-shutdown-smoke.cjs [--jvm-hook]');
const root=path.resolve(__dirname,'..'),jdk=process.env.JAVA17_HOME?path.join(process.env.JAVA17_HOME,'bin'):'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-stats-shutdown-')),server=path.join(fixture,'server'),classes=path.join(fixture,'classes'),bootstrap=path.join(fixture,'bootstrap');
const api=path.join(root,'server/cache/patched_1.12.2.jar'),auth=path.join(root,'server/plugins/AuthMe.jar'),subject=path.join(root,'server/custom-plugins/JasprApocalypse');
const report={fixture,source:subject,shutdownPath:jvmHook?'JVM shutdown hook':'normal server loop',started:new Date().toISOString(),success:false,phases:[],sourceHashes:{},untested:['Real network client transport is stubbed; native CraftPlayer is present in actual Paper online list','Physical disk power loss; actual orderly server shutdown/save and separate-JVM restart are tested']};
function hash(file){return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');}
function write(file,data){fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,data);}
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(path.join(dir,entry.name)):[path.join(dir,entry.name)]).sort();}
function run(tool,args){const r=spawnSync(path.join(jdk,tool+'.exe'),args,{cwd:fixture,encoding:'utf8',windowsHide:true,timeout:90000,maxBuffer:8*1024*1024});fs.appendFileSync(path.join(fixture,'build.log'),[tool+' '+args.join(' '),r.stdout,r.stderr].join('\n'));if(r.error)throw r.error;if(r.status!==0)throw Error(r.stderr||r.stdout);}
async function port(){const s=net.createServer();await new Promise((resolve,reject)=>{s.once('error',reject);s.listen(0,'127.0.0.1',resolve);});const n=s.address().port;await new Promise(resolve=>s.close(resolve));return n;}
async function boot(phase){
 const log=fs.createWriteStream(path.join(fixture,`paper-${phase}.log`));let line='',result,tail='',exited=false;const errors=[];
 const child=spawn(path.join(jdk,'java.exe'),['-Xms128M','-Xmx768M','-XX:ActiveProcessorCount=2','-Djaspr.stats.shutdown.fixture=true',`-Djaspr.stats.shutdown.jvmHook=${jvmHook}`,`-Djaspr.stats.shutdown.phase=${phase}`,'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dcom.mojang.eula.agree=true','-jar',api,'--nojline'],{cwd:server,windowsHide:true,stdio:['pipe','pipe','pipe']});
 const timer=setTimeout(()=>{child.stdin.write('stop\n');setTimeout(()=>{if(!exited)child.kill();},5000).unref();},150000);
 const output=chunk=>{log.write(chunk);tail=(tail+chunk).slice(-22000);line+=chunk;let i;while((i=line.indexOf('\n'))>=0){const text=line.slice(0,i);line=line.slice(i+1);if(/\b(?:ERROR|SEVERE)\b/.test(text))errors.push(text);const marker=text.indexOf('STATS_SHUTDOWN_RESULT ');if(marker>=0){try{result=JSON.parse(text.slice(marker+'STATS_SHUTDOWN_RESULT '.length));}catch(error){process.stderr.write('Probe result parse error '+error+'\n');}}if(/STATS_SHUTDOWN_|STATS_SHUTDOWN_RESULT|Done \(|Error occurred while enabling|Could not load/.test(text))process.stdout.write(text+'\n');}};
 child.stdout.on('data',output);child.stderr.on('data',output);child.stdin.on('error',()=>{});
 const cleanup=()=>{if(!exited)child.kill();};process.once('exit',cleanup);
 const code=await new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',code=>{exited=true;resolve(code);});});clearTimeout(timer);process.removeListener('exit',cleanup);log.end();
 const persisted=path.join(server,`stats-shutdown-phase-${phase}.json`);
 if(fs.existsSync(persisted)){result=JSON.parse(fs.readFileSync(persisted,'utf8'));process.stdout.write('STATS_SHUTDOWN_PERSISTED '+JSON.stringify(result)+'\n');}
 if(result){result.serverErrors=errors;if(errors.length){result.success=false;result.failures.push('Unexpected server errors during online lifecycle');}}
 report.phases.push(result||{phase,success:false,failures:['Missing probe result; exit '+code],tail});
 if(!result)throw Error('Missing phase '+phase+' probe result. '+fixture+'\n'+tail);
 if(code!==0)throw Error('Paper phase '+phase+' exit '+code);
}
(async()=>{
 console.log('Stats fixture: '+fixture);
 const snapshot=path.join(fixture,'subject');fs.cpSync(subject,snapshot,{recursive:true});
 for(const file of files(snapshot))if(/\.java$|\.yml$/.test(file))report.sourceHashes[path.relative(snapshot,file).replaceAll('\\','/')]=hash(file);
 fs.mkdirSync(classes);fs.mkdirSync(bootstrap);fs.mkdirSync(path.join(server,'plugins'),{recursive:true});
 run('javac',['--release','8','-encoding','UTF-8','-proc:none','-cp',api+';'+auth,'-d',classes,...files(path.join(snapshot,'src')).filter(f=>f.endsWith('.java'))]);
 fs.cpSync(path.join(snapshot,'resources'),classes,{recursive:true});
 const jar=path.join(server,'plugins/JasprApocalypse.jar');run('jar',['--create','--file',jar,'-C',classes,'.']);report.jarSha256=hash(jar);
 run('javac',['--release','8','-encoding','UTF-8','-cp',api+';'+auth+';'+classes,'-d',bootstrap,path.join(__dirname,'java/chat/jaspr/apocalypse/StatsShutdownProbe.java')]);
 write(path.join(bootstrap,'plugin.yml'),'name: StatsShutdownProbe\nversion: 1\nmain: chat.jaspr.apocalypse.StatsShutdownProbe\ndepend: [JasprApocalypse, AuthMe]\n');run('jar',['--create','--file',path.join(server,'plugins/StatsShutdownProbe.jar'),'-C',bootstrap,'.']);
 fs.copyFileSync(auth,path.join(server,'plugins/AuthMe.jar'));
 write(path.join(server,'eula.txt'),'eula=true\n');report.port=await port();
 write(path.join(server,'server.properties'),`server-ip=127.0.0.1\nserver-port=${report.port}\nonline-mode=false\nlevel-name=world\nlevel-seed=5117119\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\nspawn-protection=0\nview-distance=2\ngenerate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=1\nenable-rcon=false\nenable-query=false\nmax-tick-time=60000\n`);
 write(path.join(server,'bukkit.yml'),'settings:\n  allow-end: false\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  ambient: 0\n');
 write(path.join(server,'paper.yml'),'config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n');
 write(path.join(server,'spigot.yml'),'config-version: 11\nsettings:\n  late-bind: true\n');write(path.join(server,'plugins/bStats/config.yml'),'enabled: false\n');
 write(path.join(server,'plugins/AuthMe/config.yml'),'DataSource:\n  backend: SQLITE\nsettings:\n  sessions:\n    enabled: false\n  registration:\n    enabled: true\n    force: true\n  restrictions:\n    ForceSingleSession: true\n    kickNonRegistered: false\n    ProtectInventoryBeforeLogIn: false\n  updates:\n    checkForUpdates: false\nProtection:\n  geoIpDatabase:\n    enabled: false\n');
 write(path.join(server,'plugins/JasprApocalypse/config.yml'),'world: world\nsiege:\n  max-active-zombies: 0\nruins:\n  enabled: false\nresource-pack:\n  url: ""\n  sha1: ""\n');
 await boot(1);await boot(2);await boot(3);report.success=report.phases.length===3&&report.phases.every(p=>p.success);
 report.changedSinceBuild=Object.entries(report.sourceHashes).filter(([file,value])=>hash(path.join(subject,file))!==value).map(([file])=>file);
 if(report.changedSinceBuild.length)console.log('Source changed during fixture; tested snapshot is recorded: '+report.changedSinceBuild.join(', '));
 if(!report.success)process.exitCode=1;console.log(report.success?'STATS_SHUTDOWN_ALL_PASS':'STATS_SHUTDOWN_FAILED',fixture);
})().catch(error=>{report.error=error.stack;console.error(error.stack);process.exitCode=1;}).finally(()=>{report.finished=new Date().toISOString();write(path.join(fixture,'result.json'),JSON.stringify(report,null,2)+'\n');console.log('Stats report: '+path.join(fixture,'result.json'));});
