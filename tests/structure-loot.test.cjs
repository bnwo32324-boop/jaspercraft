'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),jdk='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin';
test('loot write-ahead claims survive restart, reject duplicates and fail closed on corruption',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-loot-probe-'));
 let p=spawnSync(path.join(jdk,'javac.exe'),['--release','8','-d',dir,path.join(root,'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/LootJournal.java'),path.join(root,'tests/java/chat/jaspr/biomes/LootJournalProbe.java')],{encoding:'utf8',windowsHide:true});assert.equal(p.status,0,p.stderr);
 p=spawnSync(path.join(jdk,'java.exe'),['-cp',dir,'chat.jaspr.biomes.LootJournalProbe',dir],{encoding:'utf8',windowsHide:true});assert.equal(p.status,0,p.stderr);assert.match(p.stdout,/LOOT_JOURNAL_PASS/);
});
test('legacy generator cannot repopulate empty cache inventories',()=>{const src=fs.readFileSync(path.join(root,'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/RuinSupplies.java'),'utf8');assert.ok(!src.includes('getBlockInventory'));assert.ok(src.includes('ChunkLight.initialize'));const config=fs.readFileSync(path.join(root,'server/custom-plugins/JasprApocalypse/resources/config.yml'),'utf8');assert.match(config,/ruins:\s*#[^\n]*\s*enabled: false/);});
