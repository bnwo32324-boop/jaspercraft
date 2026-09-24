'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..');

test('liminal dimension boots and reloads on isolated Paper 1.12.2',{skip:process.env.JASPR_LIMINAL_PAPER_SMOKE!=='1',timeout:90000},()=>{
  const scratch=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-liminal-paper-')),classes=path.join(scratch,'classes'),server=path.join(scratch,'server'),descriptor=path.join(scratch,'descriptor');
  for(const directory of [classes,server,descriptor,path.join(server,'plugins')])fs.mkdirSync(directory,{recursive:true});
  const javaHome='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot',api=path.join(root,'server/cache/patched_1.12.2.jar');
  const executable=name=>path.join(javaHome,'bin',name+'.exe');
  const source=path.join(root,'server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes');
  const sources=fs.readdirSync(source).filter(name=>name.endsWith('.java')).map(name=>path.join(source,name));sources.push(path.join(root,'tests/LiminalPaperProbe.java'));
  function run(name,args,timeout=45000){const result=cp.spawnSync(executable(name),args,{cwd:server,encoding:'utf8',timeout,maxBuffer:6*1024*1024});assert.ifError(result.error);assert.equal(result.status,0,result.stdout+'\n'+result.stderr);return result.stdout+'\n'+result.stderr;}
  run('javac',['--release','8','-encoding','UTF-8','-cp',api,'-d',classes,...sources]);
  fs.writeFileSync(path.join(descriptor,'plugin.yml'),'name: LiminalPaperProbe\nversion: 1.0\nmain: chat.jaspr.biomes.LiminalPaperProbe\nload: STARTUP\n');
  run('jar',['--create','--file',path.join(server,'plugins/LiminalPaperProbe.jar'),'-C',classes,'.','-C',descriptor,'plugin.yml']);
  fs.writeFileSync(path.join(server,'eula.txt'),'eula=true\n');
  fs.writeFileSync(path.join(server,'server.properties'),'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=world\nlevel-type=FLAT\nallow-nether=false\ngenerate-structures=false\nspawn-npcs=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=2\nmax-tick-time=-1\n');
  fs.writeFileSync(path.join(server,'bukkit.yml'),'settings:\n  allow-end: false\n');
  function boot(){const output=run('java',['-Djaspr.biomes.fixture=true','-DPaper.IgnoreJavaVersion=true','-Xms256M','-Xmx768M','-jar',api,'nogui']);assert.match(output,/LIMINAL_PAPER_OK rooms=16 signs=64 lights=16/,output);assert.doesNotMatch(output,/LIMINAL_PAPER_FAILED/,output);process.stdout.write(output.split(/\r?\n/).filter(line=>line.includes('LIMINAL_PAPER_')).join('\n')+'\n');}
  boot();const uid=fs.readFileSync(path.join(server,'jaspr_backrooms/uid.dat'));boot();assert.deepEqual(fs.readFileSync(path.join(server,'jaspr_backrooms/uid.dat')),uid,'restart must preserve actual world UUID');
});
