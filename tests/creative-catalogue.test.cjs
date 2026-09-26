'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),os=require('node:os');
const {spawnSync}=require('node:child_process');
const creative=require('../scripts/creative-catalog.cjs');
const patch=require('../scripts/build-stats-client.cjs');
const root=path.resolve(__dirname,'..');
const live=fs.readFileSync(path.join(root,'site/classes.js'),'utf8');
const base=patch.unpatch(live),candidate=patch.build(live),catalogue=creative.catalogue();
const adapter=fs.readFileSync(path.join(root,'client-mods/creative-items-teavm.js'),'utf8');

test('all server-defined custom gameplay items have unique, valid creative templates',()=>{
  assert.equal(catalogue.length,94);
  assert.deepEqual(Object.fromEntries(['gun','melee','armor','material','consumable','supply','artifact','block']
    .map(category=>[category,catalogue.filter(item=>item.category===category).length])),
    {gun:40,melee:24,armor:16,material:4,consumable:4,supply:2,artifact:2,block:1});
  assert.equal(new Set(catalogue.map(item=>item.id)).size,94);
  for(const item of catalogue){
    assert.match(item.material,/^minecraft:[a-z0-9_]+$/);
    assert.match(item.snbt,new RegExp('JasprCreative:\\{id:'+JSON.stringify(item.id).replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'\\}'));
    assert.ok(item.search.includes(item.id.replace(/_/g,' '))&&item.search.includes(item.title.toLowerCase()));
    if(['gun','melee','armor'].includes(item.category))assert.match(item.snbt,/Unbreakable:1b/);
  }
});

function fixture(suspendName){
  const stack=[],calls={parse:0,load:0,add:0},tabs={combat:{},food:{},misc:{},blocks:{},other:{}};
  let resuming=false,suspended=false,pending=suspendName;
  const ctx={console,JasprCreativeCatalog:catalogue,Bk:function(){},KQ5:tabs.combat,KQO:tabs.food,KQL:tabs.misc,KIY:tabs.blocks,
    $rt_str:text=>({text}),$rt_ustr:value=>value.text,FX:()=>resuming,B:()=>suspended,FT:()=>{throw Error('bad fiber state');},
    Ds:()=>({s:(...values)=>stack.push(...values),l:()=>{assert.ok(stack.length,'fiber underflow');const value=stack.pop();if(!stack.length)resuming=false;return value;}})
  };
  const child=(name,finish)=>function(...args){
    if(resuming){assert.equal(ctx.Ds().l(),name,'wrong child resumed');}
    else if(pending===name){pending=null;stack.push(name);suspended=true;return;}
    return finish(...args);
  };
  ctx.E0F=child('parse',value=>{calls.parse++;return {snbt:value.text};});
  ctx.BH8=child('load',(item,tag)=>{calls.load++;item.tag=tag;});
  ctx.Ghp=child('add',(list,item)=>{calls.add++;list.push(item);return 1;});
  vm.createContext(ctx);vm.runInContext(adapter,ctx);
  function append(tabOrQuery,list,search){
    do{suspended=false;resuming=stack.length>0;ctx.JasprCreativeAppend(tabOrQuery,list,search?1:0);}while(stack.length);
    return list;
  }
  return {ctx,tabs,calls,append,get suspended(){return suspended;}};
}

test('native adapter places entries in Combat, Food, Misc, Blocks and searchable All Items',()=>{
  const f=fixture();
  assert.equal(f.append(f.tabs.combat,[],false).length,80);
  assert.equal(f.append(f.tabs.food,[],false).length,4);
  assert.equal(f.append(f.tabs.misc,[],false).length,9);
  assert.equal(f.append(f.tabs.blocks,[],false).length,1);
  assert.equal(f.append(f.tabs.other,[],false).length,0);
  assert.equal(f.append({text:''},[],true).length,94);
  assert.equal(f.append({text:'exoskeleton'},[],true).length,16);
  assert.equal(f.append({text:'gun'},[],true).length,40);
  assert.equal(f.append({text:'last broadcast'},[],true).length,1);
  assert.equal(f.append({text:'turret'},[],true).length,1);
  assert.equal(f.append({text:'sentry'},[],true).length,1);
  assert.equal(f.append({text:'sanitized'},[],true).length,1);
});

test('each parser/load/list suspension resumes at the exact item without duplicates',()=>{
  for(const child of ['parse','load','add']){
    const f=fixture(child),items=f.append(f.tabs.food,[],false);
    assert.equal(items.length,4,child);assert.deepEqual(f.calls,{parse:4,load:4,add:4},child);
  }
});

test('compiled Creative tab and search hooks are reversible, pinned and ordered safely',()=>{
  assert.equal(patch.sha(base),patch.BASE);assert.equal(patch.unpatch(candidate),base);assert.equal(patch.build(candidate),candidate);
  const tabs=patch.nativeFunction(candidate,'Coc').body,search=patch.nativeFunction(candidate,'E1w').body;
  assert.match(tabs,/b\.eee\(e\).*JasprCreativeAppend\(b,e,0\).*e=KWh/s);
  assert.match(search,/JasprCreativeAppend\(a\.za\.cA,b\.v2,1\).*E4J\(b,f\)/s);
});

test('Minecraft 1.12.2 Mojangson parser accepts every generated creative template',()=>{
  const jdk='C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.8-hotspot\\bin';
  const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-creative-nbt-'));
  try{
    const source=path.join(root,'tests/java/chat/jaspr/apocalypse/CreativeTemplateNbtProbe.java');
    const paper=path.join(root,'server/cache/patched_1.12.2.jar');
    const rows=catalogue.map(item=>[item.id,item.model,item.snbt].join('\t')).join('\n')+'\n';
    const input=path.join(fixture,'catalogue.tsv');fs.writeFileSync(input,rows,'utf8');
    const compile=spawnSync(path.join(jdk,'javac.exe'),['--release','8','-encoding','UTF-8','-cp',paper,'-d',fixture,source],{encoding:'utf8',windowsHide:true});
    assert.equal(compile.status,0,compile.stderr||compile.stdout);
    const run=spawnSync(path.join(jdk,'java.exe'),['-cp',[fixture,paper].join(path.delimiter),'chat.jaspr.apocalypse.CreativeTemplateNbtProbe',input],{encoding:'utf8',windowsHide:true});
    assert.equal(run.status,0,run.stderr||run.stdout);
    assert.match(run.stdout,/CREATIVE_TEMPLATE_NBT_PASS count=89/);
  }finally{fs.rmSync(fixture,{recursive:true,force:true});}
});
