'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),plugin=path.join(root,'server/custom-plugins/JasprHorrorBiomes');
test('expanded catalog keeps legacy layouts and adds connected biome-specific dungeons',()=>{
  const rows=fs.readFileSync(path.join(plugin,'resources/structures/catalog-v1.tsv'),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>l.split('|'));
  assert.ok(rows.length>=204);assert.equal(new Set(rows.map(r=>r[0])).size,rows.length);
  for(const r of rows){assert.equal(r.length,8,r[0]);const cells=new Set(),lines=r[7].split('/');for(let z=0;z<lines.length;z++)for(let x=0;x<lines[z].length;x++)if(lines[z][x]!=='.')cells.add(`${x},${z}`);
    const queue=[cells.values().next().value],seen=new Set(queue);while(queue.length){const [x,z]=queue.pop().split(',').map(Number);for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const k=`${x+dx},${z+dz}`;if(cells.has(k)&&!seen.has(k)){seen.add(k);queue.push(k);}}}assert.equal(seen.size,cells.size,r[0]);
  }
  for(let biome=0;biome<62;biome++){const choices=rows.filter(r=>r[4].split(',').map(Number).includes(biome));assert.ok(choices.filter(r=>r[5]==='true').length>=2,`two exclusive ${biome}`);assert.ok(choices.some(r=>r[5]==='false'),`shared ${biome}`);}
});
test('real 1.12.2 ChunkData: geometry, marker reachability, order, spacing, reservation and bounded cache',{timeout:240000},()=>{
  const output=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-structure-jvm-'));
  const sources=fs.readdirSync(path.join(plugin,'src/chat/jaspr/biomes')).filter(n=>n.endsWith('.java')).map(n=>path.join(plugin,'src/chat/jaspr/biomes',n));
  const jar=path.join(root,'server/cache/patched_1.12.2.jar'),spec=path.join(__dirname,'java/chat/jaspr/biomes/StructureArchitectureTest.java');
  const compile=cp.spawnSync('javac',['--release','8','-encoding','UTF-8','-cp',jar,'-d',output,...sources,spec],{encoding:'utf8'});
  assert.equal(compile.status,0,compile.stdout+compile.stderr);
  const run=cp.spawnSync('java',['-Xmx1536m','-cp',[output,jar,path.join(plugin,'resources')].join(path.delimiter),'chat.jaspr.biomes.StructureArchitectureTest'],{encoding:'utf8',timeout:220000,maxBuffer:4*1024*1024});
  process.stdout.write(run.stdout||'');assert.equal(run.status,0,(run.error?String(run.error):'')+(run.stderr||''));assert.match(run.stdout,/STRUCTURE_ARCHITECTURE_PASS/);
});
