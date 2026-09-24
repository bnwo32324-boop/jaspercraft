'use strict';
// Build a complete, testable site candidate; do not alter live assets or world data.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),sha=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const cache='20260914-dlights10',release=path.join(root,'candidate','survivor-release-'+crypto.randomUUID());
fs.mkdirSync(release);fs.cpSync(path.join(root,'site'),path.join(release,'site'),{recursive:true});
const mapping=[
 ['candidate/stats-client/classes.js','site/classes.js'],['candidate/apocalypse/assets.epk','site/assets.epk'],
 ['candidate/apocalypse/JasprApocalypse.jar','server/plugins/JasprApocalypse.jar'],['candidate/horror-biomes/JasprHorrorBiomes.jar','server/plugins/JasprHorrorBiomes.jar']
];
for(const [source,target]of mapping){const to=path.join(release,target);fs.mkdirSync(path.dirname(to),{recursive:true});fs.copyFileSync(path.join(root,source),to);}
const wrappers=['index.html','client.html','jaspr-sso.js','jaspr-client.js'];
for(const name of wrappers){
 const file=path.join(release,'site',name),before=fs.readFileSync(file,'utf8');
 assert.match(before,/202609\d{2}-[A-Za-z0-9-]+/,'Cache-buster for '+name);
 fs.writeFileSync(file,before.replace(/202609\d{2}-[A-Za-z0-9-]+/g,cache));
 mapping.push(['site/'+name,'site/'+name]);
}
const stats=JSON.parse(fs.readFileSync(path.join(root,'candidate/stats-qa-report.json'),'utf8'));assert.equal(stats.success,true);
for(const [file,hash]of Object.entries(stats.sourceHashes))if(!file.startsWith('test/'))assert.equal(sha(path.join(root,'server/custom-plugins/JasprApocalypse',file)),hash,'QA covers current source '+file);
const plan={release: path.relative(root,release).replaceAll('\\','/'),stagedAt:new Date().toISOString(),cache,worldReset:false,artifacts:mapping.map(([,target])=>({target,before:sha(path.join(root,target)),sha256:sha(path.join(release,target))}))};
fs.writeFileSync(path.join(release,'plan.json'),JSON.stringify(plan,null,2)+'\n',{flag:'wx'});
fs.writeFileSync(path.join(root,'candidate/survivor-release-plan.json'),JSON.stringify(plan,null,2)+'\n');
console.log(JSON.stringify(plan,null,2));
