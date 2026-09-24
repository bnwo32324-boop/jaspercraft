'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),vm=require('node:vm');
const patch=require('../scripts/biome-client-patch.cjs'),reset=require('../scripts/reset-horror-terrain.cjs');
const root=path.resolve(__dirname,'..');
test('62 unique, fully specified replacements and all nine circles',()=>{const rows=patch.catalog();assert.equal(rows.length,62);for(const column of [0,1])assert.equal(new Set(rows.map(r=>r[column])).size,62);for(const row of rows){assert.equal(row.length,12);assert.match(row[2],/Silent Hill|S\.T\.A\.L\.K\.E\.R\.|Fallout|Dark Souls|Evil Dead|Army of Darkness|Dante/);assert.match(row[11],/^[0-9a-f]{6}$/);}assert.equal(rows.filter(r=>r[2].startsWith('Dante')).length,9);assert.equal(rows[61][10],'snow');});
test('client biome names and climate patch is pinned, repeatable and reversible',()=>{const live=fs.readFileSync(path.join(root,'site/classes.js'),'utf8'),candidate=patch.build(live);assert.equal(patch.sha(patch.unpatch(candidate)),patch.BASE);assert.equal(patch.build(candidate),candidate);new vm.Script(candidate);for(const row of patch.catalog())assert.ok(candidate.includes(JSON.stringify(row[1])));assert.ok(candidate.includes('JasprGoreBridge.frame'));assert.ok(candidate.includes('function FKS(){}'));assert.throws(()=>patch.build(live+'\n'),/Client changed/);});
function fixture(){const base=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-biome-reset-')),server=path.join(base,'server');function write(rel,value){const p=path.join(server,rel);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,value);}for(const w of ['world','world_nether','world_the_end']){write(w+'/level.dat','metadata');write(w+'/uid.dat','same-world-uuid');}for(const p of reset.TERRAIN)write(p+'/r.0.0.mca','terrain');write('world/playerdata/test.dat','inventory+enderchest+xp+gamemode');write('world/stats/test.json','{"kills":52}');write('world/advancements/test.json','{"earned":true}');write('plugins/AuthMe/authme.db','account');write('plugins/TestServerControl/config.yml','auth-config');write('plugins/JasprApocalypse/survivors.yml','introduced');write('plugins/JasprApocalypse/stats/lives/player.life','life-generation-7');write('plugins/JasprApocalypse/ruins-ledger-v1.bin','old-ruins');write('plugins/JasprApocalypse/turrets.yml','turrets:\n- world: world\n  x: 3\n  y: 73\n  z: 3\n');write('plugins/SkinsRestorer/players/player.player','skin-state');write('server.properties','gamemode=0\nforce-gamemode=false\n');write('world/data/map_0.dat','keep-player-map');write('world/data/Village.dat','old-village');return {base,server,write};}
test('terrain reset preserves byte-identical player/account data and map items',()=>{const f=fixture(),p=reset.plan(f.server),r=reset.apply(f.server,path.join(f.base,'archive'));assert.ok(r.verified);assert.ok(p.terrainFiles>=5);for(const [rel,sha]of Object.entries(p.protectedHashes))assert.equal(reset.hash(path.join(f.server,rel)),sha);for(const target of p.targets){assert.ok(!fs.existsSync(path.join(f.server,target)));assert.ok(fs.existsSync(path.join(r.archive,'terrain',target)));}assert.equal(fs.readFileSync(path.join(f.server,'world/data/map_0.dat'),'utf8'),'keep-player-map');assert.throws(()=>reset.apply(f.server,r.archive),/Archive must be a new directory/);});
test('reset rejects missing player records and junction/symlink targets',()=>{const f=fixture();const region=path.join(f.server,'world/region'),outside=path.join(f.base,'outside');fs.mkdirSync(outside);fs.renameSync(region,region+'-original');fs.symlinkSync(outside,region,'junction');assert.throws(()=>reset.plan(f.server),/Symlink/);});
test('terrain migration cannot silently reuse a live expedition loot epoch',()=>{const f=fixture();f.write('plugins/JasprHorrorBiomes/structure-loot-v2.journal','claimed caches');assert.throws(()=>reset.plan(f.server),/Expedition terrain already initialized/);assert.equal(fs.readFileSync(path.join(f.server,'world/playerdata/test.dat'),'utf8'),'inventory+enderchest+xp+gamemode');});
test('explicit details-v3 regeneration preserves old journals and Fold claims, and cannot repeat',()=>{
 const f=fixture();f.write('plugins/JasprHorrorBiomes/structure-loot-v2.journal','world claims + fold-v1 claims');f.write('plugins/JasprHorrorBiomes/structure-encounters-v1.bin','defeated old bosses');
 f.write('plugins/JasprHorrorBiomes/liminal-returns-v1/player.json','return location');f.write('jaspr_backrooms/region/r.0.0.mca','retained Fold terrain');f.write('world/jaspr-expansion-v3.boundary','old boundary');
 const result=reset.apply(f.server,path.join(f.base,'details-reset'),{epoch:'details-v3'});assert.equal(result.epoch,'details-v3');assert.equal(result.verified,true);
 for(const [rel,sha]of Object.entries(result.protectedHashes))assert.equal(reset.hash(path.join(f.server,rel)),sha,rel);
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8'),'details-v3\n');
 assert.equal(fs.readFileSync(path.join(f.server,'jaspr_backrooms/region/r.0.0.mca'),'utf8'),'retained Fold terrain');assert.ok(!fs.existsSync(path.join(f.server,'world/jaspr-expansion-v3.boundary')));
 assert.throws(()=>reset.plan(f.server,{epoch:'details-v3'}),/already exists/);assert.throws(()=>reset.plan(f.server,{epoch:'other'}),/Unsupported/);
});
test('surface-v4 migration authorizes only the epoch marker and preserves all account, life, skin and journal state',()=>{
 const f=fixture();f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','details-v3\n');f.write('plugins/JasprHorrorBiomes/structure-loot-v2.journal','old world + Fold claims');
 f.write('plugins/JasprHorrorBiomes/structure-encounters-details-v3.bin','old defeated bosses');f.write('plugins/JasprHorrorBiomes/liminal-returns-v1/player.json','old return');
 f.write('jaspr_backrooms/region/r.0.0.mca','Fold terrain');f.write('world/jaspr-expansion-v3.boundary','old boundary');
 const before=reset.plan(f.server,{epoch:'surface-v4'});assert.equal(before.previousEpoch,'details-v3');
 for(const required of ['plugins/JasprApocalypse/stats/lives/player.life','plugins/SkinsRestorer/players/player.player','server.properties'])assert.ok(Object.hasOwn(before.protectedHashes,required),required);
 const result=reset.apply(f.server,path.join(f.base,'surface-reset'),{epoch:'surface-v4'});assert.equal(result.verified,true);assert.equal(result.previousEpoch,'details-v3');
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8'),'surface-v4\n');
 assert.equal(fs.readFileSync(path.join(result.archive,'metadata/plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8'),'details-v3\n');
 for(const [rel,sha]of Object.entries(result.protectedHashes)){assert.equal(reset.hash(path.join(f.server,rel)),sha,rel);assert.equal(reset.hash(path.join(result.archive,'protected',rel)),sha,rel);}
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprApocalypse/stats/lives/player.life'),'utf8'),'life-generation-7');
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprHorrorBiomes/structure-loot-v2.journal'),'utf8'),'old world + Fold claims');
 assert.ok(!fs.existsSync(path.join(f.server,'world/jaspr-expansion-v3.boundary')));
 assert.throws(()=>reset.plan(f.server,{epoch:'surface-v4'}),/exact details-v3 predecessor/);
});
test('sparse-v5 migration accepts only surface-v4 and preserves every account, player, Fold and journal byte',()=>{
 const f=fixture();f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','surface-v4\n');f.write('plugins/JasprHorrorBiomes/structure-loot-v2.journal','all prior world and Fold claims');
 f.write('plugins/JasprHorrorBiomes/structure-encounters-surface-v4.bin','prior defeated bosses');f.write('plugins/JasprHorrorBiomes/liminal-returns-v1/player.json','durable Fold return');
 f.write('jaspr_backrooms/region/r.0.0.mca','Fold terrain remains paired with claims');f.write('world/jaspr-expansion-v3.boundary','old generated boundary');
 const before=reset.plan(f.server,{epoch:'sparse-v5'});assert.equal(before.previousEpoch,'surface-v4');assert.equal(before.epoch,'sparse-v5');
 const result=reset.apply(f.server,path.join(f.base,'sparse-reset'),{epoch:'sparse-v5'});assert.equal(result.verified,true);assert.equal(result.previousEpoch,'surface-v4');
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8'),'sparse-v5\n');
 assert.equal(fs.readFileSync(path.join(result.archive,'metadata/plugins/JasprHorrorBiomes/terrain-epoch.txt'),'utf8'),'surface-v4\n');
 for(const [rel,sha]of Object.entries(result.protectedHashes)){assert.equal(reset.hash(path.join(f.server,rel)),sha,rel);assert.equal(reset.hash(path.join(result.archive,'protected',rel)),sha,rel);}
 assert.equal(fs.readFileSync(path.join(f.server,'world/playerdata/test.dat'),'utf8'),'inventory+enderchest+xp+gamemode');
 assert.equal(fs.readFileSync(path.join(f.server,'plugins/JasprHorrorBiomes/structure-loot-v2.journal'),'utf8'),'all prior world and Fold claims');
 assert.equal(fs.readFileSync(path.join(f.server,'jaspr_backrooms/region/r.0.0.mca'),'utf8'),'Fold terrain remains paired with claims');
 assert.ok(!Object.hasOwn(result.protectedHashes,'plugins/JasprApocalypse/turrets.yml'));
 assert.equal(fs.readFileSync(path.join(result.archive,'terrain/plugins/JasprApocalypse/turrets.yml'),'utf8'),'turrets:\n- world: world\n  x: 3\n  y: 73\n  z: 3\n');
 assert.ok(!fs.existsSync(path.join(f.server,'plugins/JasprApocalypse/turrets.yml')));
 assert.ok(!fs.existsSync(path.join(f.server,'world/jaspr-expansion-v3.boundary')));
 assert.throws(()=>reset.plan(f.server,{epoch:'sparse-v5'}),/exact surface-v4 predecessor/);
});
test('rare-v6 requires the exact sparse predecessor and an explicit different-seed migration',()=>{
 const f=fixture();f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','sparse-v5\n');
 assert.throws(()=>reset.plan(f.server,{epoch:'rare-v6'}),/explicit new seed/);
 const planned=reset.plan(f.server,{epoch:'rare-v6',seed:'4184677908398476141'});
 assert.equal(planned.previousEpoch,'sparse-v5');assert.equal(planned.seed,'4184677908398476141');
 assert.throws(()=>reset.plan(f.server,{epoch:'sparse-v5',seed:'1'}),/authorized rare-world epoch/);
 assert.throws(()=>reset.plan(f.server,{epoch:'rare-v6',seed:'9223372036854775808'}),/signed 64-bit range/);
 f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','surface-v4\n');
 assert.throws(()=>reset.plan(f.server,{epoch:'rare-v6',seed:'4184677908398476141'}),/exact sparse-v5 predecessor/);
});
test('rare-v7 requires rare-v6 and a fresh explicit seed while retaining the same sparse biome contract',()=>{
 const f=fixture();f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','rare-v6\n');
 assert.throws(()=>reset.plan(f.server,{epoch:'rare-v7'}),/explicit new seed/);
 const planned=reset.plan(f.server,{epoch:'rare-v7',seed:'-7357615461451535643'});
 assert.equal(planned.previousEpoch,'rare-v6');assert.equal(planned.seed,'-7357615461451535643');
 f.write('plugins/JasprHorrorBiomes/terrain-epoch.txt','sparse-v5\n');
 assert.throws(()=>reset.plan(f.server,{epoch:'rare-v7',seed:'-7357615461451535643'}),/exact rare-v6 predecessor/);
});
