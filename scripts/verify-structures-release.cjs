'use strict';
// Read-only release gate. Its JSON output can be saved as a release artifact by the caller.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const fileHash=p=>hash(fs.readFileSync(p));
function decodeNbt(data){let at=0;
 function take(n){assert.ok(n>=0&&at+n<=data.length,'NBT boundary');const b=data.subarray(at,at+n);at+=n;return b;}
 const byte=()=>take(1).readUInt8(),short=()=>take(2).readUInt16BE(),int=()=>take(4).readInt32BE();
 const string=()=>take(short()).toString('utf8');
 function length(){const n=int();assert.ok(n>=0&&n<=data.length,'NBT length');return n;}
 function value(type,depth=0){assert.ok(depth<128,'NBT nesting');switch(type){
  case 1:return take(1).readInt8();case 2:return take(2).readInt16BE();case 3:return int();case 4:return take(8).readBigInt64BE().toString();
  case 5:return take(4).readFloatBE();case 6:return take(8).readDoubleBE();case 7:return take(length()).toString('hex');case 8:return string();
  case 9:{const kind=byte(),n=length(),out=[];for(let i=0;i<n;i++)out.push(value(kind,depth+1));return out;}
  case 10:{const out={};for(let kind;(kind=byte())!==0;){const name=string();out[name]=value(kind,depth+1);}return out;}
  case 11:{const out=[];for(let n=length();n-->0;)out.push(int());return out;}
  case 12:{const out=[];for(let n=length();n-->0;)out.push(take(8).readBigInt64BE().toString());return out;}
  default:throw Error('Unsupported NBT type '+type);
 }}
 const type=byte();string();const out=value(type);assert.equal(at,data.length,'Complete NBT');return out;
}
function nbt(file){return decodeNbt(zlib.gunzipSync(fs.readFileSync(file)));}
function regionChunks(file){
 const region=fs.readFileSync(file);assert.ok(region.length>=8192&&region.length%4096===0,'Valid Anvil region length');
 const chunks={};
 for(let slot=0;slot<1024;slot++){
  const header=slot*4,sector=region.readUIntBE(header,3),count=region[header+3];
  if(sector===0){assert.equal(count,0,'Empty Anvil location');continue;}
  assert.ok(sector>=2&&count>0&&sector+count<=region.length/4096,'Bounded Anvil location');
  const start=sector*4096,length=region.readUInt32BE(start),compression=region[start+4];
  assert.ok(length>1&&length<=count*4096-4,'Bounded Anvil payload');
  const payload=region.subarray(start+5,start+4+length);
  let decoded;
  if(compression===1)decoded=zlib.gunzipSync(payload);
  else if(compression===2)decoded=zlib.inflateSync(payload);
  else if(compression===3)decoded=payload;
  else throw Error('Unsupported Anvil compression '+compression);
  chunks[slot]=decodeNbt(decoded);
 }
 return chunks;
}
function foldRegionEquivalent(beforeFile,afterFile){
 const before=regionChunks(beforeFile),after=regionChunks(afterFile);
 assert.deepEqual(Object.keys(after),Object.keys(before),'Fold chunk set must remain unchanged');
 for(const slot of Object.keys(before)){
  const a=before[slot],b=after[slot];
  // Bukkit legitimately advances this timestamp when the retained foyer chunk is
  // loaded and saved. Everything else, including blocks and tile entities, must match.
  if(a&&a.Level)delete a.Level.LastUpdate;if(b&&b.Level)delete b.Level.LastUpdate;
  assert.deepEqual(b,a,'Fold chunk changed beyond LastUpdate at slot '+slot);
 }
 return true;
}
function yamlSemantics(file){
 const entries=[],parents=[],listIndexes=new Map();
 for(const rawLine of fs.readFileSync(file,'utf8').split(/\r?\n/)){
  if(!rawLine.trim()||rawLine.trimStart().startsWith('#'))continue;
  const indent=rawLine.length-rawLine.trimStart().length,text=rawLine.trim();
  assert.ok(!rawLine.slice(0,indent).includes('\t'),'YAML indentation cannot contain tabs');
  // Bukkit emits valid indentless sequences (the dash aligns with the mapping
  // key). Keep that key as the list parent, but pop it for the next sibling map.
  const listItem=text.startsWith('- ');
  while(parents.length&&(listItem?parents.at(-1).indent>indent:parents.at(-1).indent>=indent))parents.pop();
  const parent=parents.map(item=>item.key).join('/');
  if(listItem){
   const index=listIndexes.get(parent)||0;listIndexes.set(parent,index+1);
   entries.push(parent+'['+index+']='+text.slice(2).trim());continue;
  }
  const colon=text.indexOf(':');assert.ok(colon>0,'Simple YAML mapping expected: '+text);
  const key=text.slice(0,colon).trim(),value=text.slice(colon+1).trim(),qualified=parent?parent+'/'+key:key;
  if(value==='')parents.push({indent,key});else entries.push(qualified+'='+value);
 }
 return entries.sort();
}
function propertiesSemantics(file){
 return fs.readFileSync(file,'utf8').split(/\r?\n/).map(line=>line.trim()).filter(line=>line&&!line.startsWith('#')&&!line.startsWith('!')).sort();
}
async function main({allowBlockedPublic=false}={}){
 const requested=process.argv[2];if(!requested)throw Error('Supply the completed structures reset archive directory');
 const archive=fs.realpathSync(path.resolve(root,requested));assert.ok(archive.startsWith(path.join(root,'world-resets')+path.sep),'Exact reset archive must be inside world-resets');
 const reset=JSON.parse(fs.readFileSync(path.join(archive,'reset-manifest.json')));assert.equal(reset.verified,true);assert.equal(reset.server,path.join(root,'server'));
 const offlineFile=path.join(archive,'offline-verification.json'),offline=fs.existsSync(offlineFile)?JSON.parse(fs.readFileSync(offlineFile)):null;
 if(offline){assert.equal(offline.verified,true);assert.equal(offline.resetManifestSha256,fileHash(path.join(archive,'reset-manifest.json')));assert.deepEqual(offline.protectedHashes,reset.protectedHashes);}
 const report={verifiedAt:new Date().toISOString(),epoch:reset.epoch||'structures-v2',archive:path.relative(root,archive).replaceAll('\\','/'),protectedFiles:0,byteIdenticalProtectedFiles:0,offlineVerifiedProtectedFiles:offline?Object.keys(offline.protectedHashes).length:0,lockedRuntimeFiles:[],runtimeMetadataChanges:[],appendOnlyLogs:[],playerRecords:0,statsFiles:0,advancementFiles:0,worlds:[],artifacts:{}};
 for(const [relative,expected]of Object.entries(reset.protectedHashes)){
  const current=path.resolve(root,'server',relative);assert.ok(current.startsWith(path.join(root,'server')+path.sep));
  let actual;
  try{actual=fileHash(current);}catch(error){
   const expectedRuntimeLock=relative==='plugins/JasprHorrorBiomes/structure-loot-v2.journal'
    ||relative==='plugins/AuthMe/authme.db'||relative==='plugins/AuthMe/authme.log'
    ||relative==='plugins/TestServerControl/network-diagnostics.jsonl'
    ||/^jaspr_backrooms\/region\/r\.-?\d+\.-?\d+\.mca$/.test(relative);
   if(offline&&expectedRuntimeLock&&['EBUSY','EACCES','EPERM'].includes(error.code)){
    // Windows can enforce active SQLite, append-log, region and journal locks.
    // Their exact pre-start bytes were already verified offline and remain in the
    // recovery archive; report the live lock instead of pretending to hash it.
    const saved=path.join(archive,'protected',relative);assert.equal(fileHash(saved),expected,'Verified locked-file recovery copy');
    const currentSize=fs.statSync(current).size,savedSize=fs.statSync(saved).size;assert.ok(currentSize>0,'Locked runtime file remains nonempty');
    if(relative.endsWith('.log')||relative.endsWith('.jsonl')||relative.endsWith('.journal'))assert.ok(currentSize>=savedSize,'Append-only locked runtime file cannot shrink');
    report.lockedRuntimeFiles.push(relative);report.protectedFiles++;continue;
   }throw error;
  }
  if((relative==='plugins/AuthMe/authme.log'||relative==='plugins/TestServerControl/network-diagnostics.jsonl') && actual!==expected){
   // Runtime diagnostics append after startup. No account/config/data file gets this exception.
   const before=fs.readFileSync(path.join(archive,'protected',relative)),after=fs.readFileSync(current);
   assert.equal(hash(before),expected,'Original append-log recovery copy');
   assert.ok(after.length>=before.length && after.subarray(0,before.length).equals(before),relative+' must remain append-only');
   report.appendOnlyLogs.push(relative);
  }else if(offline&&actual!==expected&&relative==='plugins/JasprApocalypse/turrets.yml'&&['sparse-v5','rare-v6','rare-v7'].includes(reset.epoch)){
   const before=fs.readFileSync(path.join(archive,'protected',relative),'utf8'),after=fs.readFileSync(current,'utf8');
   assert.match(before,/^turrets:/,'Archived terrain-linked turret registry');
   assert.equal(after.trim(),'turrets: []','Regenerated Overworld must retire every stale placed-turret record');
   report.runtimeMetadataChanges.push(relative+' (old-world placements retired)');
  }else if(offline&&actual!==expected&&/^jaspr_backrooms\/(session\.lock|level\.dat(?:_old)?)$/.test(relative)){
   if(relative.endsWith('session.lock'))assert.equal(fs.statSync(current).size,8,'Valid Fold session lock');
   else{const before=nbt(path.join(archive,'protected',relative)).Data,after=nbt(current).Data;for(const key of ['RandomSeed','GameType','GameRules'])assert.deepEqual(after[key],before[key],'Fold retained '+key);}
   report.runtimeMetadataChanges.push(relative);
  }else if(offline&&actual!==expected&&/^jaspr_backrooms\/region\/r\.-?\d+\.-?\d+\.mca$/.test(relative)){
   foldRegionEquivalent(path.join(archive,'protected',relative),current);
   report.runtimeMetadataChanges.push(relative+' (chunk LastUpdate only)');
  }else if(offline&&actual!==expected&&/^jaspr_backrooms\/data\/villages(?:_nether|_end)?\.dat$/.test(relative)){
   const before=nbt(path.join(archive,'protected',relative)),after=nbt(current);
   if(before&&before.data)delete before.data.Tick;if(after&&after.data)delete after.data.Tick;
   assert.deepEqual(after,before,'Fold village data changed beyond runtime Tick');
   report.runtimeMetadataChanges.push(relative+' (runtime Tick only)');
  }else if(offline&&actual!==expected&&/^(?:paper|bukkit|spigot)\.yml$/.test(relative)){
   assert.deepEqual(yamlSemantics(current),yamlSemantics(path.join(archive,'protected',relative)),relative+' changed semantically');
   report.runtimeMetadataChanges.push(relative+' (mapping order only)');
  }else if(offline&&actual!==expected&&relative==='plugins/SkinsRestorer/recommendations.json'){
   const normalize=file=>{const value=JSON.parse(fs.readFileSync(file,'utf8'));value.skins.sort((a,b)=>a.skinId.localeCompare(b.skinId));return value;};
   assert.deepEqual(normalize(current),normalize(path.join(archive,'protected',relative)),'Skin recommendations changed beyond ordering');
   report.runtimeMetadataChanges.push(relative+' (recommendation order only)');
  }else if(offline&&actual!==expected&&relative==='server.properties'){
   assert.deepEqual(propertiesSemantics(current),propertiesSemantics(path.join(archive,'protected',relative)),'Server properties changed semantically');
   report.runtimeMetadataChanges.push(relative+' (timestamp/order only)');
  }else {assert.equal(actual,expected,'Protected state changed: '+relative);report.byteIdenticalProtectedFiles++;}
  report.protectedFiles++;
  if(/^world\/playerdata\/.*\.dat$/.test(relative))report.playerRecords++;
  if(/^world\/stats\/.*\.json$/.test(relative))report.statsFiles++;
  if(/^world\/advancements\/.*\.json$/.test(relative))report.advancementFiles++;
 }
 for(const world of ['world','world_nether','world_the_end']){
  const old=nbt(path.join(archive,'metadata',world,'level.dat')).Data,current=nbt(path.join(root,'server',world,'level.dat')).Data;
  if(['rare-v6','rare-v7'].includes(reset.epoch))assert.equal(current.RandomSeed,reset.seed,world+' uses the requested new seed');
  else assert.deepEqual(current.RandomSeed,old.RandomSeed,world+' retained RandomSeed');
  for(const key of ['GameType','GameRules'])assert.deepEqual(current[key],old[key],world+' retained '+key);
  report.worlds.push({world,seed:current.RandomSeed,previousSeed:old.RandomSeed,defaultGameMode:current.GameType,gameRulesRetained:true});
 }
 for(const [name,source,dest]of [
  ['horrorBiomes','candidate/horror-biomes/JasprHorrorBiomes.jar','server/plugins/JasprHorrorBiomes.jar'],
  ['apocalypse','candidate/apocalypse/JasprApocalypse.jar','server/plugins/JasprApocalypse.jar'],
  ['assets','candidate/apocalypse/assets.epk','site/assets.epk']]){
  const expected=fileHash(path.join(root,source));assert.equal(fileHash(path.join(root,dest)),expected,'Deployed '+name);report.artifacts[name]=expected;
 }
 const status=await fetch('http://127.0.0.1:3310/status').then(r=>r.json());
 assert.equal(status.server,'running');assert.equal(status.alwaysOn,true);assert.equal(status.idleShutdownSeconds,0);assert.equal(status.ssoReady,true);assert.equal(status.lastFailureCode,null);report.gateway=status;
 // Paper rolls latest.log at midnight. Include only archives closed after this
 // terrain epoch began, so a long-running verified process remains provable.
 const logRoot=path.join(root,'server/logs'),releaseAt=Date.parse(reset.startedAt||reset.finishedAt||0);
 const logFiles=['latest.log',...fs.readdirSync(logRoot).filter(name=>name.endsWith('.log.gz')&&fs.statSync(path.join(logRoot,name)).mtimeMs>=releaseAt)];
 const log=logFiles.map(name=>name.endsWith('.gz')?zlib.gunzipSync(fs.readFileSync(path.join(logRoot,name))).toString('utf8'):fs.readFileSync(path.join(logRoot,name),'utf8')).join('\n');
 report.serverLogs=logFiles.sort();
 if(['details-v3','surface-v4','sparse-v5','rare-v6','rare-v7'].includes(reset.epoch)){
  assert.equal(fs.readFileSync(path.join(root,'server/plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8').trim(),reset.epoch);
  const catalog=fs.readFileSync(path.join(root,'server/custom-plugins/JasprHorrorBiomes/resources/structures/catalog-v1.tsv'),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>l.split('|'));
  report.structureDesigns=catalog.length;assert.ok(catalog.length>=204);report.biomeDetailProfiles=fs.readFileSync(path.join(root,'server/custom-plugins/JasprHorrorBiomes/resources/biome-details.tsv'),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).length;assert.equal(report.biomeDetailProfiles,62);
  assert.match(log,new RegExp('designs='+catalog.length+'\\b'));
  const boundary=fs.readFileSync(path.join(root,'server/world/jaspr-expansion-v3.boundary'));assert.equal(boundary.readInt32BE(12),0,'Regenerated terrain enables new structures everywhere');
  assert.ok(fs.existsSync(path.join(root,'server/plugins/JasprHorrorBiomes/structure-encounters-'+reset.epoch+'.bin')));
  if(reset.epoch==='surface-v4'||reset.epoch==='sparse-v5'||reset.epoch==='rare-v6'||reset.epoch==='rare-v7'){
   if(reset.epoch==='rare-v7'){
    assert.match(log,/STRUCTURES_READY version=7 .*relativeStructureDensity=10% spawnExclusion=3072 valuableConstructionBlocks=false.*terrainEpoch=rare-v7/);
    assert.match(log,/HORROR_BIOMES_READY version=3\.6\.0 .*structureDensity=10% spawnExclusion=3072.*generator=rare-v7/);
    assert.ok(reset.reseed&&reset.reseed.oldSeed!==reset.reseed.newSeed,'Manifest proves a different seed');
   }else if(reset.epoch==='rare-v6'){
    assert.match(log,/STRUCTURES_READY version=6 .*relativeStructureDensity=10% spawnExclusion=3072.*terrainEpoch=rare-v6/);
    assert.match(log,/HORROR_BIOMES_READY version=3\.5\.0 .*structureDensity=10% spawnExclusion=3072.*generator=rare-v6/);
    assert.ok(reset.reseed&&reset.reseed.oldSeed!==reset.reseed.newSeed,'Manifest proves a different seed');
   }else if(reset.epoch==='sparse-v5'){
    assert.match(log,/STRUCTURES_READY version=5 .*relativeStructureDensity=10%.*terrainEpoch=sparse-v5/);
    assert.match(log,/HORROR_BIOMES_READY version=3\.4\.0 .*structureDensity=10%.*generator=sparse-v5/);
   }else{
    assert.match(log,/STRUCTURES_READY version=5 .*relativeStructureDensity=10%.*terrainEpoch=surface-v4/);
    assert.match(log,/HORROR_BIOMES_READY version=3\.4\.0 .*structureDensity=10%.*generator=surface-v4/);
   }
   assert.equal(catalog.length,234);
   const details=fs.readFileSync(path.join(root,'server/custom-plugins/JasprHorrorBiomes/resources/biome-details.tsv'),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>l.split('|'));
   assert.ok(details.every(row=>row[9]==='0'),'Artificial biome clutter must remain disabled');
   report.artificialBiomeClutter=false;report.surfaceReservedCells='75%';
  }else{
   assert.match(log,/STRUCTURES_READY version=3.*terrainEpoch=details-v3/);assert.match(log,/HORROR_BIOMES_READY version=3\.0\.0/);
  }
 }else{assert.match(log,/STRUCTURES_READY version=2/);assert.match(log,/HORROR_BIOMES_READY version=2\.0\.0/);}
 assert.match(log,/APOCALYPSE_READY version=3\.4\.0.*legacyRuins=false/);assert.match(log,/TPA_READY timeoutSeconds=60/);
 assert.match(log,/SPAWN_GUARD_READY world=world .*safe=world:[^ ]+ metadataRepaired=(?:true|false)/);
 assert.doesNotMatch(log,/(?:STRUCTURE_TEST_FAIL|STRUCTURE_LOOT_LOCKED|STRUCTURE_ENCOUNTERS_LOCKED|EXPANSION_BOUNDARY_FAILED|LOOT_WRITE_FAILED|LIMINAL_WORLD_FAILED|LIMINAL_WORLD_CONFLICT|Error occurred while enabling Jaspr)/);
 report.localVerified=true;
 report.publicAssetsVerified=false;
 try {
  const localHealth=await fetch('http://127.0.0.1:3200/api/health').then(r=>{assert.equal(r.status,200);return r.json();});
  const publicHealth=await fetch('https://jaspr.chat/api/health?release='+Date.now(),{headers:{'Cache-Control':'no-cache'}}).then(r=>{assert.equal(r.status,200);return r.json();});
  report.siteSessions={local:localHealth.sessionId,public:publicHealth.sessionId};
  const html=await fetch('https://jaspr.chat/jaspercraft/client.html').then(r=>{report.publicClientStatus=r.status;assert.equal(r.status,200,'Public JasperCraft client must be available');return r.text();});assert.match(html,/20260912-spawn-guard1/);
  assert.equal(publicHealth.sessionId,localHealth.sessionId,'Public site must serve the verified primary session');
  const response=await fetch('https://jaspr.chat/jaspercraft/assets.epk?build=20260912-spawn-guard1');assert.equal(response.status,200);
  assert.equal(hash(Buffer.from(await response.arrayBuffer())),report.artifacts.assets,'Public site serves exact verified asset package');report.publicAssetsVerified=true;
 }catch(error){
  report.publicFailure=error.message;
  if(!allowBlockedPublic)throw error;
 }
 // A diagnostic report may record a blocked public route, but can never label it a successful release.
 report.verified=report.localVerified&&report.publicAssetsVerified;console.log(JSON.stringify(report,null,2));return report;
}
function verifyOfflineFiles(projectRoot,archive){
 const resetFile=path.join(archive,'reset-manifest.json'),reset=JSON.parse(fs.readFileSync(resetFile));
 assert.equal(reset.server,path.join(projectRoot,'server'));assert.equal(reset.verified,true);assert.ok(['details-v3','surface-v4','sparse-v5','rare-v6','rare-v7'].includes(reset.epoch));
 const server=path.join(projectRoot,'server');
 assert.ok(Array.isArray(reset.targets)&&reset.targets.length>0,'Terrain targets recorded');
 assert.equal(new Set(reset.targets).size,reset.targets.length,'Unique terrain targets');
 assert.deepEqual(reset.moved,reset.targets,'All planned terrain moves completed');
 const terrainArchive=path.join(archive,'terrain');
 for(const relative of reset.targets){
  const current=path.resolve(server,relative),saved=path.resolve(terrainArchive,relative);
  assert.ok(current.startsWith(server+path.sep)&&saved.startsWith(terrainArchive+path.sep),'Terrain target remains in its exact root');
  assert.ok(!fs.existsSync(current),'Old terrain removed: '+relative);
  assert.ok(fs.existsSync(saved),'Archived terrain missing: '+relative);
  assert.ok(!fs.lstatSync(saved).isSymbolicLink(),'Archived terrain cannot be a link: '+relative);
 }
 for(const [relative,expected]of Object.entries(reset.protectedHashes)){
  const file=path.resolve(server,relative);assert.ok(file.startsWith(server+path.sep));assert.equal(fileHash(file),expected,'Offline protected state: '+relative);
  assert.equal(fileHash(path.join(archive,'protected',relative)),expected,'Verified recovery copy: '+relative);
 }
 for(const world of ['world','world_nether','world_the_end']){
  const beforeFile=path.join(archive,'metadata',world,'level.dat'),afterFile=path.join(server,world,'level.dat');
  const before=nbt(beforeFile),after=nbt(afterFile);
  if(reset.epoch==='surface-v4'||reset.epoch==='sparse-v5')assert.equal(fileHash(afterFile),fileHash(beforeFile),reset.epoch+' preserves exact level metadata bytes in '+world);
  else{
   const expected=JSON.parse(JSON.stringify(before));
   if(['rare-v6','rare-v7'].includes(reset.epoch)){
    assert.ok(reset.reseed&&reset.reseed.newSeed===reset.seed&&reset.reseed.oldSeed!==reset.reseed.newSeed,'Verified reseed manifest');
    expected.Data.RandomSeed=reset.seed;
    if(world==='world'){expected.Data.SpawnX=reset.reseed.spawn.x;expected.Data.SpawnY=reset.reseed.spawn.y;expected.Data.SpawnZ=reset.reseed.spawn.z;}
   }else if(world==='world'){expected.Data.SpawnX=0;expected.Data.SpawnY=73;expected.Data.SpawnZ=0;}
   delete expected.Data.DragonFight;if(expected.Data.DimensionData?.['1'])delete expected.Data.DimensionData['1'].DragonFight;
   assert.deepEqual(after,expected,(['rare-v6','rare-v7'].includes(reset.epoch)?'Only authorized reseed/spawn/dragon metadata changed in ':'Only authorized spawn and dragon references changed in ')+world);
  }
 }
 assert.equal(fs.readFileSync(path.join(server,'plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8').trim(),reset.epoch);
 if(reset.epoch==='surface-v4'||reset.epoch==='sparse-v5'||reset.epoch==='rare-v6'||reset.epoch==='rare-v7'){
  const expectedPredecessor=reset.epoch==='surface-v4'?'details-v3':reset.epoch==='sparse-v5'?'surface-v4':reset.epoch==='rare-v6'?'sparse-v5':'rare-v6';assert.equal(reset.previousEpoch,expectedPredecessor);
  const predecessor=path.join(archive,'metadata/plugins/JasprHorrorBiomes/terrain-epoch.txt');
  assert.equal(fileHash(predecessor),reset.epochBeforeSha256,'Recoverable predecessor epoch marker');
  assert.equal(fs.readFileSync(predecessor,'utf8').trim(),expectedPredecessor);
 }
 for(const rel of ['world/region','world_nether/DIM-1/region','world_the_end/DIM1/region','world/jaspr-expansion-v3.boundary'])assert.ok(!fs.existsSync(path.join(server,rel)),'Old terrain removed: '+rel);
 return {verified:true,verifiedAt:new Date().toISOString(),epoch:reset.epoch,previousEpoch:reset.previousEpoch||null,resetManifestSha256:fileHash(resetFile),protectedHashes:reset.protectedHashes,metadataChanges:(reset.epoch==='surface-v4'||reset.epoch==='sparse-v5')?'none-byte-identical':['rare-v6','rare-v7'].includes(reset.epoch)?'seed-spawn-and-obsolete-dragon-references-only':'spawn-and-obsolete-dragon-references-only'};
}
async function offlineMain(){
 const archive=fs.realpathSync(path.resolve(root,process.argv[2]||''));assert.ok(archive.startsWith(path.join(root,'world-resets')+path.sep));
 const status=await fetch('http://127.0.0.1:3310/status').then(r=>r.json());assert.equal(status.server,'maintenance');assert.ok(fs.existsSync(path.join(root,'.runtime/maintenance-mode')));
 const pidFile=path.join(root,'.runtime/paper-server.pid');if(fs.existsSync(pidFile)){
  const pid=Number(fs.readFileSync(pidFile,'utf8'));assert.ok(Number.isInteger(pid)&&pid>0);let alive=true;try{process.kill(pid,0);}catch(error){if(error.code==='ESRCH')alive=false;else throw error;}assert.equal(alive,false,'Paper must be stopped');
 }
 const result=verifyOfflineFiles(root,archive);fs.writeFileSync(path.join(archive,'offline-verification.json'),JSON.stringify(result,null,2),{flag:'wx'});
 console.log(JSON.stringify({verified:result.verified,protectedFiles:Object.keys(result.protectedHashes).length,metadataChanges:result.metadataChanges},null,2));return result;
}
if(require.main===module)(process.argv.includes('--offline')?offlineMain():main()).catch(error=>{console.error(error.message);process.exitCode=1;});
module.exports={nbt,decodeNbt,regionChunks,foldRegionEquivalent,yamlSemantics,propertiesSemantics,main,verifyOfflineFiles};
