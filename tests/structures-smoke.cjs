'use strict';
// Real, isolated Paper / browser fixture. Production data is never copied or mounted.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),net=require('node:net'),http=require('node:http');
const {spawn,spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),jdk='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture=path.join(root,'candidate/structures-smoke-'+crypto.randomUUID()),server=path.join(fixture,'server');
fs.mkdirSync(path.join(server,'plugins'),{recursive:true});
function write(file,data){const p=path.join(fixture,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,data);}
function run(name,args){const r=spawnSync(path.join(jdk,name+'.exe'),args,{cwd:fixture,encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);}
async function port(){const s=net.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
(async()=>{
 const socket=await port(),webPort=await port(),api=path.join(root,'server/cache/patched_1.12.2.jar'),jar=path.join(root,'candidate/horror-biomes/JasprHorrorBiomes.jar');
 const classes=path.join(fixture,'classes');fs.mkdirSync(classes);
 run('javac',['--release','8','-encoding','UTF-8','-cp',api+';'+jar,'-d',classes,path.join(root,'tests/java/chat/jaspr/biomes/StructuresProbe.java'),path.join(root,'tests/java/chat/jaspr/biomes/WeaponLootProbe.java')]);
 write('classes/plugin.yml','name: StructuresProbe\nmain: chat.jaspr.biomes.StructuresProbe\nversion: 1\ndepend: [JasprHorrorBiomes, JasprApocalypse, AuthMe]\ncommands:\n  structuressmoke:\n    description: Isolated structures fixture\n');
 run('jar',['--create','--file',path.join(server,'plugins/StructuresProbe.jar'),'-C',classes,'.']);
 fs.copyFileSync(path.join(root,'server/plugins/AuthMe.jar'),path.join(server,'plugins/AuthMe.jar'));fs.copyFileSync(path.join(root,'candidate/apocalypse/JasprApocalypse.jar'),path.join(server,'plugins/JasprApocalypse.jar'));
 fs.copyFileSync(api,path.join(server,'paper.jar'));fs.copyFileSync(jar,path.join(server,'plugins/JasprHorrorBiomes.jar'));
 fs.copyFileSync(path.join(root,'server/server-icon.png'),path.join(server,'server-icon.png'));
 for(const file of ['EaglerXServer.jar','EaglerXRewind.jar','ViaVersion.jar','ViaBackwards.jar','ViaRewind.jar','ViaRewind-Legacy-Support.jar'])fs.copyFileSync(path.join(root,'server/plugins',file),path.join(server,'plugins',file));
 write('server/plugins/AuthMe/config.yml',"DataSource:\n  backend: SQLITE\nsettings:\n  updates:\n    checkForUpdates: false\n  restrictions:\n    ProtectInventoryBeforeLogIn: false\nProtection:\n  geoIpDatabase:\n    enabled: false\n");
 write('server/plugins/JasprHorrorBiomes/terrain-epoch.txt','surface-v4\n');
 write('server/eula.txt','eula=true\n');
 write('server/server.properties',`server-ip=127.0.0.1\nserver-port=${socket}\nonline-mode=false\nlevel-name=world\nlevel-seed=691208467\nspawn-protection=0\nview-distance=3\ngenerate-structures=true\nallow-nether=true\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=2\nnetwork-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=0\n`);
 write('server/bukkit.yml','settings:\n  allow-end: true\nworlds:\n  world:\n    generator: JasprHorrorBiomes\n');
 write('server/paper.yml','config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n');
 write('server/spigot.yml','config-version: 11\nsettings:\n  late-bind: true\n');
 write('server/plugins/bStats/config.yml','enabled: false\nserverUuid: "'+crypto.randomUUID()+'"\n');
 write('server/plugins/EaglercraftXServer/listener.yml','dual_stack: true\nforward_ip: false\nforward_secret: false\ntls_config:\n  enable_tls: false\n  require_tls: false\nratelimit:\n  disable_ratelimit: [127.0.0.0/8]\n');
 write('server/plugins/EaglercraftXServer/settings.yml','server_name: Structures fixture\nserver_uuid: "'+crypto.randomUUID()+'"\nprotocols:\n  max_minecraft_protocol: 340\n  eaglerxrewind_allowed: true\n  protocol_v3_allowed: true\n  protocol_v4_allowed: true\nvoice_service:\n  enable_voice_service: false\nupdate_checker:\n  enable_update_checker: false\nupdate_service:\n  enable_update_system: false\n  download_latest_certs: false\n');
 const html=`<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JasperCraft structures fixture</title><style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:black}</style><script src="/classes.js"></script><script src="/jaspr-profile.js"></script></head><body><div id="game_frame"></div><script>JasprProfile.prepare('StructuresProbe',localStorage).then(function(){window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_tailscale_ui2',localStorageLoaded:function(k){return localStorage.getItem(k.startsWith('_eaglercraft_')?k:'_eaglercraft_1122_tailscale_ui2.'+k);},worldsDB:'biome_preview',resourcePacksDB:'biome_packs',joinServer:'ws://127.0.0.1:${socket}/',servers:[],relays:[],crashOnUncaughtExceptions:true};main();});</script></body></html>`;
 const web=http.createServer((req,res)=>{if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);return;}const file={'/classes.js':process.env.JASPR_FIXTURE_CLIENT||'candidate/stats-client/classes.js','/assets.epk':'candidate/apocalypse/assets.epk','/jaspr-profile.js':'site/jaspr-profile.js'}[req.url.split('?')[0]];if(!file){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript; charset=utf-8':'application/octet-stream');res.setHeader('Cache-Control','no-store');fs.createReadStream(path.join(root,file)).pipe(res);});web.listen(webPort,'127.0.0.1');
 const log=fs.createWriteStream(path.join(fixture,'paper.log'));const child=spawn(path.join(jdk,'java.exe'),['-Xms128M','-Xmx1024M','-XX:ActiveProcessorCount=2','-Djaspr.biomes.fixture=true','-Djava.awt.headless=true','-Dcom.mojang.eula.agree=true','-jar','paper.jar','--nojline'],{cwd:server,windowsHide:true,stdio:['pipe','pipe','pipe']});
 let started=false,tail='';function out(data){log.write(data);tail=(tail+data).slice(-16000);if(/STRUCTURE_|BIOME_|HORROR_|Done \(|ERROR|SEVERE/.test(String(data)))process.stdout.write(data);if(!started&&tail.includes('Done (')){started=true;child.stdin.write('structuressmoke\n');}}
 child.stdout.on('data',out);child.stderr.on('data',out);process.stdin.on('data',b=>child.stdin.write(b));
 let stopping=false;function stop(){if(stopping)return;stopping=true;child.stdin.write('stop\n');setTimeout(()=>{if(child.exitCode===null)child.kill();},20000).unref();}
 process.on('SIGINT',stop);process.on('SIGTERM',stop);const timeout=setTimeout(stop,45*60000);
 child.on('exit',()=>{clearTimeout(timeout);web.close();log.end();process.exit();});process.on('exit',()=>{if(child.exitCode===null)child.kill();});
 write('fixture.json',JSON.stringify({fixture,socket,webPort,pid:child.pid},null,2));console.log('Structures preview URL: http://127.0.0.1:'+webPort+'/');console.log('Fixture: '+fixture);console.log('Commands: structuressmoke, structuressmoke show 0..61, stop');
})();
