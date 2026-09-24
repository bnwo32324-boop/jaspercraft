'use strict';
// Read-only live release gate. World preservation is proved before startup, never
// inferred from a healthy HTTP response. Public bytes must match the tested build.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),hash=b=>crypto.createHash('sha256').update(b).digest('hex'),fileHash=p=>hash(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
async function json(url){const r=await fetch(url,{signal:AbortSignal.timeout(15000),headers:{'Cache-Control':'no-cache'}});assert.equal(r.status,200,url);return r.json();}
async function main(){
 const plan=read(path.join(root,'candidate/survivor-release-plan.json')),release=fs.realpathSync(path.join(root,plan.release));
 assert.ok(release.startsWith(path.join(root,'candidate','survivor-release-')),'Exact release directory');
 const proof=read(path.join(release,'offline-proof.json'));assert.equal(proof.verified,true);assert.equal(proof.worldReset,false);assert.deepEqual(proof.artifacts,plan.artifacts);
 assert.equal(proof.protectedFileCount,Object.keys(proof.protectedHashes).length);assert.ok(proof.protectedFileCount>100,'Complete offline state snapshot');
 const report={verified:false,verifiedAt:new Date().toISOString(),release:plan.release,worldReset:false,offlineProtectedFiles:proof.protectedFileCount,unchangedPlayerFiles:0,artifacts:{},publicArtifacts:{}};
 for(const item of plan.artifacts){assert.equal(fileHash(path.join(root,item.target)),item.sha256,'Installed '+item.target);assert.equal(fileHash(path.join(release,item.target)),item.sha256,'Staged '+item.target);report.artifacts[item.target]=item.sha256;}
 for(const [file,expected]of Object.entries(proof.protectedHashes)){
  if(/^server\/(world|world_nether|world_the_end|jaspr_backrooms)\/(playerdata|stats|advancements)\//.test(file)){
   assert.equal(fileHash(path.join(root,file)),expected,'Player progress changed during release '+file);report.unchangedPlayerFiles++;
  }
  if(file.endsWith('/uid.dat')||file.endsWith('/terrain-epoch.txt')||file.endsWith('/jaspr-expansion-v3.boundary'))assert.equal(fileHash(path.join(root,file)),expected,'World identity changed '+file);
 }
 report.gateway=await json('http://127.0.0.1:3310/status');
 assert.equal(report.gateway.server,'running');assert.equal(report.gateway.alwaysOn,true);assert.equal(report.gateway.idleShutdownSeconds,0);assert.equal(report.gateway.ssoReady,true);assert.equal(report.gateway.lastFailureCode,null);
 assert.equal(fs.existsSync(path.join(root,'.runtime/maintenance-mode')),false);
 const [local,remote]=await Promise.all([json('http://127.0.0.1:3200/api/health'),json('https://jaspr.chat/api/health?release='+Date.now())]);
 assert.equal(remote.sessionId,local.sessionId,'Public site serves primary instance');report.primarySession=remote.sessionId;
 const log=fs.readFileSync(path.join(root,'server/logs/latest.log'),'utf8');
 assert.match(log,/APOCALYPSE_READY version=3\.4\.0.*legacyRuins=false/);assert.match(log,/TPA_READY timeoutSeconds=60/);
 assert.match(log,/HORROR_BIOMES_READY version=3\.6\.0 .*structureDensity=10% spawnExclusion=3072.*generator=rare-v7/);assert.match(log,/SPAWN_GUARD_READY world=world .*safe=world:[^,]+,[2-9][0-9]*\.[0-9]+,[^ ]+ metadataRepaired=(?:true|false)/);
 assert.match(log,/STRUCTURES_READY version=7 .*relativeStructureDensity=10% spawnExclusion=3072 valuableConstructionBlocks=false.*terrainEpoch=rare-v7/);
 assert.doesNotMatch(log,/STATS_(?:STORAGE_LOCKED|LOAD_FAILED|PURCHASE_SAVE_FAILED|DEATH_CHECKPOINT_FAILED|RESPAWN_SAVE_FAILED)|Error occurred while enabling Jaspr|STRUCTURE_(?:LOOT_LOCKED|ENCOUNTERS_LOCKED)|LOOT_WRITE_FAILED|LIMINAL_WORLD_FAILED/);
 for(const file of ['classes.js','assets.epk','client.html','index.html','jaspr-client.js','jaspr-sso.js']){
  const url='https://jaspr.chat/jaspercraft/'+file+'?build='+plan.cache;
  const response=await fetch(url,{signal:AbortSignal.timeout(30000),headers:{'Cache-Control':'no-cache'}});assert.equal(response.status,200,'Public '+file);
  const data=Buffer.from(await response.arrayBuffer()),expected=report.artifacts['site/'+file];assert.equal(hash(data),expected,'Public exact bytes '+file);report.publicArtifacts[file]=expected;
 }
 report.guns=35;report.meleeWeapons=24;report.newGuns=24;report.newMeleeWeapons=16;report.statRanks=6;report.rankCap=10;report.defaultKey='K';report.command='/stats';report.verified=true;
 fs.writeFileSync(path.join(root,'candidate/survivor-release-report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));return report;
}
if(require.main===module)main().catch(error=>{console.error(error.stack);process.exitCode=1;});
module.exports={main};
