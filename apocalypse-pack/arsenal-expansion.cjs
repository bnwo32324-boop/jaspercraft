'use strict';
// Code-native cuboid source for the forty appended weapons. No textures, entity effects or I/O.
// The release builder validates the checked-in JSON against these deterministic definitions.
const gunSpecs = [
  // id, band, archetype, muzzle Z, receiver half-width, stock, accent
  ['sepulcher',1450,'pistol',-1,1.35,'none','steel'],
  ['vesper',1440,'suppressed',-7,1.15,'none','white'],
  ['ossuary',1430,'revolver',-4,1.65,'none','gold'],
  ['turnstile',1420,'revolver',1,1.45,'none','wood'],
  ['cinder',1410,'machine',-2,1.3,'wire','red'],
  ['tunnelrat',1400,'carbine',-6,1.5,'skeletal','wood'],
  ['blackbox',1390,'bullpup',-5,1.75,'compact','energy'],
  ['quarantine',1380,'carbine',-8,1.6,'solid','gold'],
  ['signal',1370,'marksman',-9,1.3,'skeletal','energy'],
  ['gallows',1360,'bolt',-11,1.2,'wooden','wood'],
  ['watchtower',1350,'antimateriel',-13,1.8,'solid','gold'],
  ['whiteout',1340,'covert',-12,1.25,'compact','white'],
  ['bellringer',1330,'double',-6,1.7,'wooden','gold'],
  ['lockjaw',1320,'pump',-8,1.8,'wire','red'],
  ['choir',1310,'triple',-5,2,'compact','white'],
  ['ashfall',1300,'drum',-7,1.9,'skeletal','dark'],
  ['nullpoint',1290,'needle',-12,1.1,'skeletal','energy'],
  ['cenotaph',1280,'rail',-14,2.2,'solid','white'],
  ['witchlight',1270,'arc',-6,1.8,'compact','green'],
  ['stormcoil',1260,'coil',-10,1.7,'wire','energy'],
  ['hexbreaker',1250,'reliquary',-8,2.1,'wooden','red'],
  ['pallbearer',1240,'belt',-9,2,'solid','steel'],
  ['ironpsalm',1230,'rotary',-7,2.25,'solid','gold'],
  ['deadfrequency',1220,'pulse',-10,1.6,'compact','green'],
];
const meleeSpecs = [
  ['gravespike',1450,'dirk',22,'dark'], ['railpick',1440,'pick',25,'steel'],
  ['wardcleaver',1430,'cleaver',23,'red'], ['pilgrim_lance',1420,'lance',31,'gold'],
  ['cautery_sabre',1410,'sabre',28,'energy'], ['suture_sickle',1400,'sickle',23,'white'],
  ['tollhammer',1390,'bell',24,'gold'], ['rebar_sword',1380,'rebar',29,'dark'],
  ['vesper_dagger',1370,'dagger',20,'green'], ['hollow_halberd',1360,'halberd',31,'steel'],
  ['ossuary_flail',1350,'flail',27,'white'], ['ember_falchion',1340,'falchion',26,'red'],
  ['mourning_glaive',1330,'glaive',30,'energy'], ['altar_mallet',1320,'mallet',25,'green'],
  ['execution_sword',1310,'execution',30,'white'], ['wire_whip',1300,'scourge',26,'steel'],
];
const textures = {particle:'blocks/iron_block',steel:'blocks/iron_block',dark:'blocks/coal_block',
  gold:'blocks/gold_block',wood:'blocks/planks_big_oak',energy:'blocks/diamond_block',
  red:'blocks/redstone_block',white:'blocks/quartz_block_side',green:'blocks/prismarine_rough'};
const transform = (rotation,translation,scale) => ({rotation,translation,scale:[scale,scale,scale]});
function display(gun, long) {
  const result = {
    thirdperson_righthand:transform(gun?[90,0,0]:[0,90,0],[0,1,0],gun?.6:.55),
    thirdperson_lefthand:transform(gun?[90,0,0]:[0,90,0],[0,1,0],gun?.6:.55),
    firstperson_righthand:transform(gun?[6,0,-3]:[0,-90,25],[1,gun?0:1,-1],long?.53:.6),
    firstperson_lefthand:transform(gun?[6,0,3]:[0,-90,25],[1,gun?0:1,-1],long?.53:.6),
    gui:transform([25,140,-20],[0,0,0],long?.4:.48),
    ground:transform([0,0,90],[0,2,0],.35), fixed:transform([0,90,-35],[0,0,0],.45),
  };
  return result;
}
function box(name,from,to,texture='steel') {
  return {__comment:name,from,to,faces:Object.fromEntries(['north','south','east','west','up','down']
    .map(face=>[face,{uv:[0,0,16,16],texture:'#'+texture}]))};
}
function gunModel([id,band,type,z,w,stock,accent]) {
  const p = [], add=(...args)=>p.push(box(...args));
  const pistol=['pistol','suppressed','revolver','machine'].includes(type), rear=pistol?12:15;
  const muzzleWidth=['double','triple','drum','rotary'].includes(type)?2:Math.min(w,1.3);
  add('receiver',[8-w,9,4],[8+w,12,rear],'dark');
  add('receiver upper rail',[7.5,12,3],[8.5,12.5,rear],accent);
  add('barrel',[7.5,10,z+1],[8.5,11,6]);
  add('muzzle housing',[8-muzzleWidth,9.5,z],[8+muzzleWidth,11.5,z+1.5]);
  add('muzzle bore facing negative Z',[7.6,10.1,z-.05],[8.4,10.9,z+.05],'dark');
  add('grip below receiver',[7,3.5,rear-3],[9,9,rear],'wood');
  add('grip heel',[6.8,3,rear-3.1],[9.2,3.8,rear+.2],accent);
  add('trigger guard foot',[7.5,6.8,rear-6],[8.5,7.4,rear-2],'dark');
  add('trigger guard front',[7.5,7.2,rear-6],[8.5,9,rear-5.4],'dark');
  add('front sight above bore',[7.65,11.5,z+.8],[8.35,13,z+1.3],accent);
  add('rear sight left',[6.9,12.5,rear-1],[7.4,13.4,rear-.3],'steel');
  add('rear sight right',[8.6,12.5,rear-1],[9.1,13.4,rear-.3],'steel');
  if (stock !== 'none') {
    const len=stock==='compact'?4:stock==='wire'?7:9;
    add('stock spine',[7,10,rear],[9,11.5,rear+len],stock==='wooden'?'wood':'dark');
    add('shoulder heel',[6.5,7,rear+len-1],[9.5,12.3,rear+len+1],accent);
    if(stock==='solid'||stock==='wooden')add('cheek rest',[6.8,8.5,rear+2],[9.2,12,rear+len-1],stock==='wooden'?'wood':'dark');
    if(stock==='skeletal')add('stock lower strut',[7.4,7.5,rear],[8.6,8.5,rear+len-1]);
  }
  if(type==='revolver') {
    add('six shot cylinder',[5.4,8.8,2],[10.6,12,6.2],accent);
    add('cylinder pin',[7.65,8.2,1.8],[8.35,8.8,6.6]);
    add('cocked hammer',[7.6,12,10],[8.4,14,12],'dark');
  } else if(type==='suppressed'||type==='covert') {
    add('long suppressor',[6.7,9.2,z],[9.3,11.8,z+6],'dark');
    add('suppressor ring',[6.5,9,z+4.4],[9.5,12,z+5],accent);
  } else if(type==='double'||type==='triple') {
    add('left shotgun tube',[5.8,9.3,z],[7.5,11.3,6],'dark');
    add('right shotgun tube',[8.5,9.3,z],[10.2,11.3,6],'dark');
    if(type==='triple')add('lower third tube',[7.1,7.8,z],[8.9,9.5,6],accent);
    add('hinge catch',[6.1,8.5,5],[9.9,10,7],accent);
  } else if(type==='rotary') {
    for(const [x,y] of [[6,10],[8,12],[10,10],[8,8]])add('rotary tube '+x+'/'+y,[x-.6,y-.6,z],[x+.6,y+.6,4],'dark');
    add('rotary front clamp',[5,7,z+.5],[11,13,z+1.5],accent);
  } else if(['needle','rail','arc','coil','reliquary','pulse'].includes(type)) {
    add('left accelerator rail',[5.5,10,z],[6.7,12,6],accent);
    add('right accelerator rail',[9.3,10,z],[10.5,12,6],accent);
    add('insulated power chamber',[6.2,8,4.5],[9.8,11,9],accent);
    for(let i=0;i<3;i++)add('coil band '+i,[5.8,9,z+2+i*2.5],[10.2,12,z+2.6+i*2.5],'dark');
    if(type==='reliquary') {
      add('relic cruciform vertical',[7.4,12.5,7],[8.6,17,8],accent);
      add('relic cruciform arms',[5.5,14.5,7],[10.5,15.5,8],accent);
    }
  }
  if(!pistol) {
    if(['marksman','bolt','antimateriel','covert'].includes(type)) {
      add('scope mount',[7.4,12.5,7],[8.6,14,12],'dark');
      add('scope tube',[6.8,14,4],[9.2,16.4,13],'dark');
      add('scope objective',[6.5,13.7,3],[9.5,16.7,5],accent);
      add('bolt handle',[9,10,10],[12,11,11]);
    } else if(type==='bullpup') {
      add('carry handle bridge',[6.6,14,4],[9.4,15,14],'dark');
      add('carry handle upright',[6.6,12,4],[9.4,14,5],accent);
    }
    if(type==='belt'||type==='rotary') {
      add('ammunition box',[3,4.5,4],[7,9,10],'dark');
      for(let i=0;i<3;i++)add('belt cartridge '+i,[3+i,9.1,6],[3.7+i,10,8],'gold');
    } else if(type==='drum') {
      add('wide rotary magazine',[4.5,3,5],[11.5,8.5,10],'dark');
      add('drum hub',[4,4.5,6.5],[12,7.5,8.5],accent);
    } else if(!['double','triple'].includes(type)) {
      const mz=type==='bullpup'?12:4;
      add('box magazine',[7,3.7,mz],[9,9,mz+3],accent);
    }
    if(type==='pump')add('pump handguard',[6.2,7.8,z+2.5],[9.8,10,z+7.5],'wood');
    if(type==='antimateriel'||type==='belt') {
      add('folded bipod left',[5.8,7,z+2],[6.5,9.3,2]);
      add('folded bipod right',[9.5,7,z+2],[10.2,9.3,2]);
    }
  } else if(type==='machine')add('extended pistol magazine',[7,0,rear-3],[9,3.5,rear],accent);
  if(type==='pistol')add('slide side inlay',[6.55,10.3,5],[9.45,11.3,10],accent);
  return {__comment:id+' / stable band '+band+' / muzzle -Z, sights +Y, grip -Y',ambientocclusion:true,
    textures,display:display(true,z<=-9),elements:p};
}
function meleeModel([id,band,type,top,accent]) {
  const p=[],add=(...args)=>p.push(box(...args)), pole=['lance','halberd','glaive'].includes(type);
  add('handle',[7,pole?-7:0,7],[9,pole?22:9,9],'wood');
  add('grip wrap',[6.7,1,6.7],[9.3,6,9.3],'dark');
  add('pommel',[6.5,pole?-8:-1,6.5],[9.5,pole?-6:1,9.5],accent);
  add('guard',[5.5,6.5,6.5],[10.5,7.5,9.5],accent);
  if(['dirk','dagger','rebar','execution','sabre','falchion','glaive','lance'].includes(type)) {
    const wide=['execution','rebar','falchion'].includes(type), start=pole?20:8;
    add('blade spine',[wide?5.5:7,start,7],[wide?10.5:9,top-3,9],'steel');
    add('blade point',[7.2,top-3,7.2],[8.8,top,8.8],accent);
    if(['sabre','falchion','glaive'].includes(type)) {
      add('curved cutting edge',[9,start+2,7.2],[11,top-3,8.8],accent);
      add('swept tip',[8.5,top-4,7.2],[10.5,top-1,8.8],accent);
    }
    if(type==='dagger')add('ritual double guard',[4,10,6.7],[12,11,9.3],'green');
    if(type==='lance')add('lance pennant',[3,18,7.5],[7,23,8.5],accent);
    if(type==='rebar')for(let i=0;i<4;i++)add('welded tooth '+i,[10.5,11+i*3,7.5],[12.5,12+i*3,8.5],'dark');
    if(type==='execution')add('squared execution tip',[5.5,top-3,7],[10.5,top,9],'dark');
  } else if(type==='pick'||type==='halberd') {
    if(type==='pick')add('pick upper haft',[7,8,7],[9,18,9],'wood');
    add('socket',[6.5,16,6.5],[9.5,top-3,9.5],'dark');
    add('left axe blade',[2.5,top-9,7],[7,top-2,9],accent);
    add('right armor spike',[9,top-5,7.5],[15,top-3,8.5]);
    add('tip',[7.2,top-4,7.2],[8.8,top,8.8],accent);
  } else if(type==='cleaver') {
    add('rectangular cutting plate',[5.5,9,7],[12,top-1,9]);
    add('weighted spine',[4.5,9,7],[6,top,9],'dark');
    add('sharpened edge',[11.5,10,7.5],[12.8,top-2,8.5],accent);
  } else if(type==='sickle') {
    add('sickle neck',[7,8,7],[9,top,9]);
    add('hook top',[2,top-2,7.4],[9,top,8.6],accent);
    add('hook return',[1,top-6,7.4],[3,top-1,8.6],accent);
    add('inward fang',[2,top-6,7.4],[5,top-4.8,8.6]);
  } else if(type==='bell'||type==='mallet') {
    add('haft upper',[7,8,7],[9,top-2,9],'wood');
    add('weighted head',[3,top-7,4.5],[13,top-1,11.5],accent);
    add('head cap',[4,top-1,5.5],[12,top,10.5],'dark');
    if(type==='bell')add('bell lip',[2,top-8,3.5],[14,top-6,12.5],'gold');
    else add('altar binding',[6.8,top-7.3,4.2],[9.2,top-.5,11.8],'white');
  } else if(type==='flail') {
    add('flail haft',[7,8,7],[9,20,9]);
    for(let i=0;i<4;i++)add('chain link '+i,[8+i,19.5+i,7.5],[10+i,21+i,8.5],'dark');
    add('bone flail head',[11,top-5,5.5],[16,top,10.5],accent);
    add('flail spikes',[10,top-3.5,6.5],[17,top-1.5,9.5],'steel');
  } else if(type==='scourge') {
    for(let strand=0;strand<3;strand++)for(let link=0;link<4;link++) {
      const x=5+strand*2.5+(link%2)*.5,y=8+link*4.2;
      add('razor strand '+strand+'/'+link,[x,y,7.5],[x+.65,y+4.5,8.5],link%2?accent:'dark');
    }
    add('razor barbs',[3.8,top-2,7.5],[12,top-1,8.5],accent);
  }
  return {__comment:id+' / stable band '+band+' / working end +Y; original upright blade transforms',ambientocclusion:true,
    textures,display:display(false,pole||top>=28),elements:p};
}
function models() {
  return new Map([...gunSpecs.map(spec=>['apocalypse_'+spec[0]+'.json',gunModel(spec)]),
    ...meleeSpecs.map(spec=>['apocalypse_'+spec[0]+'.json',meleeModel(spec)])]);
}
module.exports={gunSpecs,meleeSpecs,models};
