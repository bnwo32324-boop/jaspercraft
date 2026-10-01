'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),game=process.env.JASPR_NETHER_TEST_GAME||root;
const source=process.env.JASPR_NETHER_TEST_SOURCE||path.join(root,'server/custom-plugins/JasprNether');
const jdk='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
test('Nether upgrade: native stair rotation, immutable old layouts, doubled density and safe planners',()=>{
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-nether-upgrade-')),classes=path.join(out,'classes');
 const paper=path.join(game,'server/cache/patched_1.12.2.jar'),src=path.join(source,'src/chat/jaspr/nether');
 const names=['StairRegression','LayoutRegression','DensityPreview'];
 try{
  const java=fs.readdirSync(src).filter(n=>n.endsWith('.java')).map(n=>path.join(src,n));
  java.push(...names.map(n=>path.join(root,'tests/java/chat/jaspr/nether',n+'.java')));
  const build=spawnSync(path.join(jdk,'javac.exe'),['--release','8','-encoding','UTF-8','-proc:none','-cp',paper,'-d',classes,...java],{encoding:'utf8',windowsHide:true});
  assert.equal(build.status,0,build.stderr);
  for(const name of names){
   const arg=name==='LayoutRegression'?path.join(out,'registry'):path.join(source,'resources');
   const run=spawnSync(path.join(jdk,'java.exe'),['-XX:ActiveProcessorCount=2','-Xmx1400m','-cp',classes+path.delimiter+paper,'chat.jaspr.nether.'+name,arg],{encoding:'utf8',windowsHide:true,timeout:120000,cwd:out});
   assert.equal(run.status,0,run.stderr+run.stdout.slice(-5000));
   if(name==='StairRegression')assert.match(run.stdout,/STAIRS_NATIVE_PASS builds=98/);
   if(name==='LayoutRegression')assert.match(run.stdout,/LAYOUT_PASS checks=13/);
   if(name==='DensityPreview'){
    const ratios=[...run.stdout.matchAll(/DENSITY_RATIO seed=(\d+) before=(\d+) after=(\d+) ratio=([\d.]+)/g)];
    assert.equal(ratios.length,3,'three unchanged seeds, identical clipped areas');
    for(const [,seed,,,ratio]of ratios)assert.ok(Number(ratio)>1.8&&Number(ratio)<2.3,'roughly doubled accepted distribution seed='+seed+' ratio='+ratio);
    assert.equal((run.stdout.match(/overlaps=0 megaHits=0 disabled=0/g)||[]).length,6);
   }
  }
 }finally{
  assert.equal(fs.realpathSync(out).toLowerCase(),path.resolve(out).toLowerCase(),'owned scratch directory is not a link');
  assert.equal(path.dirname(out).toLowerCase(),path.resolve(os.tmpdir()).toLowerCase());
  assert.ok(path.basename(out).startsWith('jaspr-nether-upgrade-'));
  fs.rmSync(out,{recursive:true,force:true});
 }
});
test('tpd public gate still delegates Creative-only permission to the command',()=>{
 const travel=fs.readFileSync(path.join(source,'src/chat/jaspr/nether/DimensionTravel.java'),'utf8');
 assert.match(travel,/getGameMode\(\) == GameMode\.CREATIVE/);
 assert.match(travel,/if \(!creative\(sender\)\)/);
 assert.doesNotMatch(travel,/setType\(|setBlockData\(|setOp\(/);
 assert.match(travel,/StandardCopyOption\.ATOMIC_MOVE/);
 assert.match(travel,/if \(pending\.containsKey\(player\.getUniqueId\(\)\)\)/);
 const control=process.env.JASPR_NETHER_TEST_CONTROL||path.join(root,'server/custom-plugins/TestServerControl');
 const policy=fs.readFileSync(path.join(control,'src/local/eagler/testserver/PlayerCommandPolicy.java'),'utf8');
 assert.match(policy,/"tpd",\s*"jasprnether:tpd"/);
});
