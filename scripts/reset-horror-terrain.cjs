'use strict';
// Narrow, recoverable terrain reset. Never removes a world root, player file or account database.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),os=require('node:os'),cp=require('node:child_process');
const ROOT=path.resolve(__dirname,'..');
const TERRAIN=['world/region','world_nether/DIM-1/region','world_the_end/DIM1/region'];
const TERRAIN_PLUGIN_STATE=['plugins/JasprApocalypse/ruins-ledger-v1.bin','plugins/JasprApocalypse/turrets.yml'];
const STRUCTURES=['Village.dat','villages.dat','villages_nether.dat','villages_end.dat','Fortress.dat','EndCity.dat','Mineshaft.dat','Monument.dat','Stronghold.dat','Temple.dat'];
function hash(p){return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');}
function normalizeSeed(seed){
 if(seed===null||seed===undefined)return null;const text=String(seed);
 if(!/^-?(?:0|[1-9]\d*)$/.test(text))throw Error('Seed must be a canonical signed 64-bit integer');
 const value=BigInt(text);if(value<-(1n<<63n)||value>(1n<<63n)-1n)throw Error('Seed is outside the signed 64-bit range');return text;
}
function javaTool(name){const fixed=path.join('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin',name+'.exe');return fs.existsSync(fixed)?fixed:name;}
function reseedMetadata(server,seed){
 const scratch=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-world-reseed-'));
 try{
  const source=path.join(ROOT,'scripts/java/chat/jaspr/biomes/ReseedMetadata.java');
  const api=path.join(ROOT,'server/cache/patched_1.12.2.jar'),plugin=path.join(ROOT,'server/plugins/JasprHorrorBiomes.jar');
  for(const required of [source,api,plugin])if(!fs.existsSync(required))throw Error('Reseed dependency missing: '+required);
  let run=cp.spawnSync(javaTool('javac'),['--release','8','-encoding','UTF-8','-cp',[api,plugin].join(path.delimiter),'-d',scratch,source],{encoding:'utf8',windowsHide:true});
  if(run.status!==0)throw Error('Reseed helper compilation failed: '+String(run.stderr||run.stdout||run.error));
  run=cp.spawnSync(javaTool('java'),['-cp',[scratch,api,plugin].join(path.delimiter),'chat.jaspr.biomes.ReseedMetadata',server,seed],{encoding:'utf8',windowsHide:true});
  if(run.status!==0)throw Error('World metadata reseed failed: '+String(run.stderr||run.stdout||run.error));
  const match=String(run.stdout).match(/WORLD_RESEED_COMPLETE oldSeed=(-?\d+) newSeed=(-?\d+) spawn=(-?\d+),(-?\d+),(-?\d+) worlds=(\d+)/);
  if(!match||match[2]!==seed||match[6]!=='3')throw Error('Unverified reseed helper output: '+String(run.stdout));
  return {oldSeed:match[1],newSeed:match[2],spawn:{x:Number(match[3]),y:Number(match[4]),z:Number(match[5])},worlds:Number(match[6])};
 }finally{fs.rmSync(scratch,{recursive:true,force:true});}
}
function safe(base,relative){const p=path.resolve(base,relative);if(!p.startsWith(path.resolve(base)+path.sep))throw Error('Path escapes server root');let at=path.resolve(base);if(fs.lstatSync(at).isSymbolicLink())throw Error('Symlink root');for(const part of path.relative(base,p).split(path.sep)){at=path.join(at,part);if(fs.existsSync(at)&&fs.lstatSync(at).isSymbolicLink())throw Error('Symlink/junction is not a reset target: '+at);}return p;}
function files(base,relative){const p=safe(base,relative);if(!fs.existsSync(p))return [];if(fs.statSync(p).isFile())return [relative.replaceAll('\\','/')];return fs.readdirSync(p).flatMap(name=>files(base,path.join(relative,name)));}
function protectedFiles(server){const list=[];for(const w of ['world','world_nether','world_the_end'])for(const name of ['playerdata','stats','advancements','uid.dat'])list.push(...files(server,w+'/'+name));
 for(const w of ['world','world_nether','world_the_end'])list.push(...files(server,w+'/data').filter(p=>!STRUCTURES.includes(path.basename(p))));
 // The Fold is not regenerated; its terrain, containers, UUID and progress stay paired with its claims.
 list.push(...files(server,'jaspr_backrooms'));
 // Preserve every plugin data directory. Apocalypse life checkpoints must stay paired with
 // ranks in player NBT; AuthMe/skin/SSO state must stay paired with the same account identity.
 const pluginRoot=safe(server,'plugins');if(fs.existsSync(pluginRoot))for(const name of fs.readdirSync(pluginRoot)){
  const absolute=safe(server,'plugins/'+name);if(fs.statSync(absolute).isDirectory())list.push(...files(server,'plugins/'+name));
 }
 for(const p of ['server.properties','bukkit.yml','spigot.yml','paper.yml','permissions.yml','ops.json','whitelist.json','banned-players.json','banned-ips.json','usercache.json'])list.push(...files(server,p));
 return [...new Set(list)].filter(p=>!TERRAIN_PLUGIN_STATE.includes(p)).sort();}
function plan(server,{epoch=null,seed=null}={}){server=fs.realpathSync(server);for(const w of ['world','world_nether','world_the_end'])if(!fs.existsSync(safe(server,w+'/level.dat')))throw Error('Required world missing: '+w);
 seed=normalizeSeed(seed);if(epoch!==null&&!['details-v3','surface-v4','sparse-v5','rare-v6','rare-v7'].includes(epoch))throw Error('Unsupported reset epoch');
 const epochFile='plugins/JasprHorrorBiomes/terrain-epoch.txt';
 const epochPath=safe(server,epochFile),previousEpoch=fs.existsSync(epochPath)?fs.readFileSync(epochPath,'utf8').trim():null;
 if(!['rare-v6','rare-v7'].includes(epoch)&&seed!==null)throw Error('Only an authorized rare-world epoch may change the world seed');
 if(epoch==='details-v3'&&previousEpoch!==null)throw Error('Terrain epoch already exists; never silently repeat a live reset');
 if(epoch==='surface-v4'&&previousEpoch!=='details-v3')throw Error('surface-v4 requires the exact details-v3 predecessor');
 if(epoch==='sparse-v5'&&previousEpoch!=='surface-v4')throw Error('sparse-v5 requires the exact surface-v4 predecessor');
 if(epoch==='rare-v6'&&previousEpoch!=='sparse-v5')throw Error('rare-v6 requires the exact sparse-v5 predecessor');
 if(epoch==='rare-v6'&&seed===null)throw Error('rare-v6 requires an explicit new seed');
 if(epoch==='rare-v7'&&previousEpoch!=='rare-v6')throw Error('rare-v7 requires the exact rare-v6 predecessor');
 if(epoch==='rare-v7'&&seed===null)throw Error('rare-v7 requires an explicit new seed');
 // An unversioned reset is only valid before expedition terrain exists. Versioned
 // migrations require an exact predecessor and never reset the Fold's shared claims.
 for(const journal of ['structure-loot-v2.journal','structure-encounters-v1.bin'])
  if(!epoch&&fs.existsSync(safe(server,'plugins/JasprHorrorBiomes/'+journal)))throw Error('Expedition terrain already initialized; pair its journals with a new terrain epoch before another reset');
 const targets=TERRAIN.filter(p=>fs.existsSync(safe(server,p)));for(const w of ['world','world_nether','world_the_end'])for(const name of STRUCTURES){const p=w+'/data/'+name;if(fs.existsSync(safe(server,p)))targets.push(p);}
 // Registries whose entries point at blocks in the discarded Overworld must travel
 // with that terrain. Keeping them would either create ghost state or delete them
 // piecemeal as regenerated spawn chunks load.
 for(const state of TERRAIN_PLUGIN_STATE)if(fs.existsSync(safe(server,state)))targets.push(state);
 const boundary='world/jaspr-expansion-v3.boundary';if(epoch&&fs.existsSync(safe(server,boundary)))targets.push(boundary);
 const protectedHashes=Object.fromEntries(protectedFiles(server).filter(p=>!(epoch&&p===epochFile)).map(p=>[p,hash(safe(server,p))]));
 if(!Object.keys(protectedHashes).some(p=>p.startsWith('world/playerdata/')))throw Error('No saved player data found; inspect before resetting');
 return {server,epoch,seed,previousEpoch,epochFile,epochBeforeSha256:previousEpoch===null?null:hash(epochPath),targets,protectedHashes,terrainFiles:targets.reduce((n,p)=>n+files(server,p).length,0)};
}
function apply(server,archive,options={}){const p=plan(server,options);archive=path.resolve(archive);if(fs.existsSync(archive))throw Error('Archive must be a new directory');if(archive.startsWith(p.server+path.sep))throw Error('Archive cannot be inside live server');
 const workspace=path.dirname(p.server);safe(workspace,path.relative(workspace,archive));
 fs.mkdirSync(archive,{recursive:true});const report={...p,archive,startedAt:new Date().toISOString(),moved:[],verified:false};const save=()=>fs.writeFileSync(path.join(archive,'reset-manifest.json'),JSON.stringify(report,null,2));save();
 // Inventory/account recovery copies before any terrain is moved; fail closed if a copy is incomplete.
 for(const [rel,sha]of Object.entries(p.protectedHashes)){const out=path.join(archive,'protected',rel);fs.mkdirSync(path.dirname(out),{recursive:true});fs.copyFileSync(safe(server,rel),out);if(hash(out)!==sha)throw Error('Protected copy verification failed');}
 if(p.previousEpoch!==null){const source=safe(server,p.epochFile),out=path.join(archive,'metadata',p.epochFile);fs.mkdirSync(path.dirname(out),{recursive:true});fs.copyFileSync(source,out);if(hash(out)!==p.epochBeforeSha256)throw Error('Terrain epoch recovery copy verification failed');}
 for(const w of ['world','world_nether','world_the_end'])for(const f of ['level.dat','level.dat_old']){const src=safe(server,w+'/'+f);if(fs.existsSync(src)){const out=path.join(archive,'metadata',w,f);fs.mkdirSync(path.dirname(out),{recursive:true});fs.copyFileSync(src,out);}}
 for(const target of p.targets){const src=safe(server,target),dest=path.join(archive,'terrain',target);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.renameSync(src,dest);report.moved.push(target);save();}
 if(p.seed!==null){report.reseed=reseedMetadata(server,p.seed);save();}
 // Old journals and Fold claims remain byte-identical. Only the regenerated Overworld
 // uses a fresh loot namespace, encounter journal and one-time relocation file.
 if(p.epoch){const epochPath=safe(server,p.epochFile);fs.mkdirSync(path.dirname(epochPath),{recursive:true});const fd=fs.openSync(epochPath,p.previousEpoch===null?'wx':'w');try{fs.writeFileSync(fd,p.epoch+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}if(fs.readFileSync(epochPath,'utf8')!==p.epoch+'\n')throw Error('Terrain epoch commit failed');}
 for(const [rel,sha]of Object.entries(p.protectedHashes))if(hash(safe(server,rel))!==sha)throw Error('Protected data changed: '+rel);
 report.verified=true;report.finishedAt=new Date().toISOString();save();return report;
}
if(require.main===module)(async()=>{
 const requested=['details-v3','surface-v4','sparse-v5','rare-v6','rare-v7'].filter(name=>process.argv.includes('--'+name));if(requested.length>1)throw Error('Choose one terrain epoch');
 const seedArg=process.argv.find(value=>value.startsWith('--seed='));
 const server=path.join(ROOT,'server'),options={epoch:requested[0]||null,seed:seedArg?seedArg.slice(7):null};if(!process.argv.includes('--apply')){const p=plan(server,options);console.log(JSON.stringify({epoch:p.epoch,seed:p.seed,previousEpoch:p.previousEpoch,targets:p.targets,protectedFiles:Object.keys(p.protectedHashes).length,terrainFiles:p.terrainFiles},null,2));return;}
 const status=await fetch('http://127.0.0.1:3310/status').then(r=>r.json());if(!['maintenance','stopped'].includes(status.server)||!fs.existsSync(path.join(ROOT,'.runtime/maintenance-mode')))throw Error('Graceful maintenance shutdown required');
 const pidFile=path.join(ROOT,'.runtime/paper-server.pid');if(fs.existsSync(pidFile)){const pid=Number(fs.readFileSync(pidFile,'utf8'));if(!Number.isInteger(pid)||pid<1)throw Error('Invalid Paper PID');let alive=true;try{process.kill(pid,0);}catch(e){if(e.code==='ESRCH')alive=false;else throw e;}if(alive)throw Error('Paper PID is still alive; refusing reset');}
 const archive=path.join(ROOT,'world-resets',(options.epoch||'structures-v2')+'-'+new Date().toISOString().replace(/[:.]/g,'-'));const r=apply(server,archive,options);console.log(JSON.stringify({archive:r.archive,terrainTargets:r.moved.length,protectedFiles:Object.keys(r.protectedHashes).length,verified:r.verified},null,2));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
module.exports={plan,apply,hash,TERRAIN,TERRAIN_PLUGIN_STATE,protectedFiles,normalizeSeed,reseedMetadata};
