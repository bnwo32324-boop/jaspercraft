'use strict';
// Disposable, loopback-only browser/Paper integration fixture. No production writes.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),java='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
const fixture=path.join(root,'candidate/gore-preview-'+crypto.randomUUID()),server=path.join(fixture,'server');
fs.mkdirSync(path.join(server,'plugins'),{recursive:true});
function write(file,data){const p=path.join(fixture,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,data);}
function run(exe,args){const r=spawnSync(path.join(java,exe+'.exe'),args,{cwd:fixture,encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error(r.stderr||r.stdout);}
async function port(){const s=net.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
(async()=>{
  const socketPort=await port(),webPort=await port();
  const classes=path.join(fixture,'classes');fs.mkdirSync(classes);
  const api=path.join(root,'server/cache/patched_1.12.2.jar');
  run('javac',['--release','8','-encoding','UTF-8','-proc:none','-cp',api,'-d',classes,path.join(root,'tests/java/chat/jaspr/gore/GorePreview.java')]);
  write('classes/plugin.yml','name: GorePreview\nmain: chat.jaspr.gore.GorePreview\nversion: 1\ncommands:\n  gorepreview:\n    description: Isolated renderer fixture\n');
  run('jar',['--create','--file',path.join(server,'plugins/GorePreview.jar'),'-C',classes,'.']);
  fs.copyFileSync(api,path.join(server,'paper.jar'));
  for(const file of ['EaglerXServer.jar','EaglerXRewind.jar','ViaVersion.jar','ViaBackwards.jar','ViaRewind.jar','ViaRewind-Legacy-Support.jar'])fs.copyFileSync(path.join(root,'server/plugins',file),path.join(server,'plugins',file));
  write('server/eula.txt','eula=true\n');
  write('server/server.properties',`server-ip=127.0.0.1\nserver-port=${socketPort}\nonline-mode=false\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;\nlevel-name=world\nspawn-protection=0\nview-distance=2\ngenerate-structures=false\nallow-nether=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\nmax-players=2\nnetwork-compression-threshold=-1\nenable-rcon=false\nenable-query=false\ngamemode=1\n`);
  write('server/bukkit.yml','settings:\n  allow-end: false\n');
  write('server/paper.yml','config-version: 13\nworld-settings:\n  default:\n    keep-spawn-loaded: false\n');
  write('server/spigot.yml','config-version: 11\nsettings:\n  late-bind: true\n');
  write('server/plugins/bStats/config.yml','enabled: false\nserverUuid: "'+crypto.randomUUID()+'"\n');
  write('server/plugins/EaglercraftXServer/listener.yml','dual_stack: true\nforward_ip: false\nforward_secret: false\ntls_config:\n  enable_tls: false\n  require_tls: false\nratelimit:\n  disable_ratelimit: [127.0.0.0/8]\n');
  write('server/plugins/EaglercraftXServer/settings.yml','server_name: Gore render fixture\nserver_uuid: "'+crypto.randomUUID()+'"\nprotocols:\n  max_minecraft_protocol: 340\n  eaglerxrewind_allowed: true\n  protocol_v3_allowed: true\n  protocol_v4_allowed: true\nvoice_service:\n  enable_voice_service: false\nupdate_checker:\n  enable_update_checker: false\nupdate_service:\n  enable_update_system: false\n  download_latest_certs: false\n');
  const html=`<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JasperCraft gore renderer fixture</title><style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:black}#status{position:fixed;top:0;left:0;z-index:9;font:12px monospace;color:white;background:#000a;pointer-events:none}</style><script src="/classes.js"></script><script src="/jaspr-profile.js"></script></head><body><div id="game_frame"></div><output id="status">Native gore renderer fixture</output><script>JasprProfile.prepare('GoreProbe',localStorage).then(function(){window.eaglercraftXOpts={container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_tailscale_ui2',localStorageLoaded:function(k){return localStorage.getItem(k.startsWith('_eaglercraft_')?k:'_eaglercraft_1122_tailscale_ui2.'+k);},worldsDB:'gore_preview_worlds',resourcePacksDB:'gore_preview_packs',joinServer:'ws://127.0.0.1:${socketPort}/',servers:[],relays:[],crashOnUncaughtExceptions:true};main();});setInterval(function(){if(window.JasprGoreDiagnostics){var s=JasprGoreDiagnostics.status();document.querySelector('#status').textContent=JSON.stringify(s);}},500);</script></body></html>`;
  const web=http.createServer((req,res)=>{
    if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);return;}
    const key=req.url.split('?')[0],allowed={'/classes.js':'candidate/gore-client/classes.js','/assets.epk':'site/assets.epk','/jaspr-profile.js':'site/jaspr-profile.js'};
    if(!allowed[key]){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',key.endsWith('.js')?'application/javascript; charset=utf-8':'application/octet-stream');res.setHeader('Cache-Control','no-store');fs.createReadStream(path.join(root,allowed[key])).pipe(res);
  });web.listen(webPort,'127.0.0.1');
  const log=fs.createWriteStream(path.join(fixture,'paper.log'));
  const child=spawn(path.join(java,'java.exe'),['-Xms128M','-Xmx768M','-XX:ActiveProcessorCount=2','-Djaspr.gore.preview=true','-Djava.awt.headless=true','-Dcom.mojang.eula.agree=true','-jar','paper.jar','--nojline'],{cwd:server,windowsHide:true,stdio:['pipe','pipe','pipe']});
  const output=data=>{log.write(data);if(/GORE_|Done \(|ERROR|SEVERE|logged in/.test(data.toString()))process.stdout.write(data);};
  child.stdout.on('data',output);child.stderr.on('data',output);
  process.stdin.on('data',data=>child.stdin.write(data));
  let stopping=false;function stop(){if(stopping)return;stopping=true;child.stdin.write('stop\n');setTimeout(()=>{if(child.exitCode===null)child.kill();},20000).unref();}
  process.on('SIGINT',stop);process.on('SIGTERM',stop);
  const timeout=setTimeout(stop,45*60*1000);
  child.on('exit',()=>{clearTimeout(timeout);web.close();log.end();process.exit();});
  process.on('exit',()=>{if(child.exitCode===null)child.kill();});
  write('fixture.json',JSON.stringify({fixture,webPort,socketPort,pid:child.pid},null,2));
  console.log('Preview URL: http://127.0.0.1:'+webPort+'/');console.log('Fixture: '+fixture);console.log('Commands: gorepreview <mob|hit damage|night|day|list>, stop');
})();
