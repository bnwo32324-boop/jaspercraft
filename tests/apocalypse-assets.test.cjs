'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {decode,merge}=require('../scripts/merge-apocalypse-assets.cjs');
const {validate,heldVector}=require('../scripts/build-apocalypse-pack.cjs');
const expansion=require('../apocalypse-pack/arsenal-expansion.cjs');
const root=path.resolve(__dirname,'..');

test('arsenal models pass every durability and held-hand orientation case',()=>{
  const result=validate();
  assert.equal(result.models,111);
  assert.equal(result.checked,9886);
  assert.equal(result.heldOrientationCases,160);
  assert.equal(result.meleeOrientationCases,160);
});

test('sentry head uses the six-times effective, flush body-top placement transform',()=>{
  const model=JSON.parse(fs.readFileSync(path.join(root,'apocalypse-pack','assets','minecraft','models','item','apocalypse_sentry_turret.json'),'utf8'));
  assert.deepEqual(model.display.head.rotation,[0,0,0]);
  assert.deepEqual(model.display.head.translation,[0,0,0]);
  // Vanilla 1.12.2 clamps item display transforms to 4. The asset bakes the
  // remaining 1.5x into every cuboid, so the rendered result is six-times.
  assert.deepEqual(model.display.head.scale,[4,4,4]);
  const base=model.elements.find(e=>e.__comment==='base plate');
  assert.deepEqual(base.from,[1.25,-3.5,1.25]);
  assert.deepEqual(base.to,[14.75,-0.5,14.75]);
  const source=fs.readFileSync(path.join(root,'server','custom-plugins','JasprApocalypse','src','chat','jaspr','apocalypse','SentryTurret.java'),'utf8');
  assert.match(source,/HEAD_SCALE = 6\.0/);
  assert.match(source,/HEAD_DISPLAY_SCALE = 4\.0/);
  assert.match(source,/HEAD_ANCHOR_OFFSET = 1\.1/);
  assert.match(source,/turret\.y \+ HEAD_ANCHOR_OFFSET/);
  assert.match(source,/HEAD_CLEARANCE = 7/);
  assert.match(source,/yaw = yawTo\(muzzle, aim\)/);
  assert.match(source,/stand\.setSmall\(true\)/);
});

test('client EPK merge resolves actual resources, changes only item models, and is idempotent',()=>{
  const input=fs.readFileSync(path.join(root,'site','assets.epk'));
  const result=merge(input,path.join(root,'apocalypse-pack'));
  assert.equal(result.models.length,111);
  assert.ok(result.unchangedEntries>=5750);
  const repeated=merge(result.output,path.join(root,'apocalypse-pack'));
  assert.deepEqual(repeated.output,result.output);
  const decoded=decode(result.output);
  for(const name of result.models){
    assert.match(name,/^assets\/minecraft\/models\/item\//);
    assert.deepEqual(decoded.entries.find(e=>e.name===name).value,
      fs.readFileSync(path.join(root,'apocalypse-pack',name)));
  }
  assert.ok(result.output.length-input.length<120000,'Fifty-six cuboid weapons add less than 120 KB compressed client payload');
});

test('all pre-expansion weapon stats and 45 non-selector model files are byte-preserved',()=>{
  const legacy=require('../apocalypse-pack/arsenal-legacy-contract.json');
  for(const [file,sha] of Object.entries(legacy.models)) {
    assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'apocalypse-pack/assets/minecraft/models/item',file))).digest('hex'),sha,file);
  }
  const java=fs.readFileSync(path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/Arsenal.java'),'utf8');
  for(const line of legacy.guns)assert.ok(java.includes(line),'Legacy gun unchanged: '+line);
  const melee=fs.readFileSync(path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'),'utf8');
  for(const line of legacy.melee)assert.ok(melee.includes(line),'Legacy melee unchanged: '+line);
});

// 2026-09-26: five common sidearms (rapture, g18, magnum44, wingman, mozambique) append after the 24
// late-expedition guns. They are deliberately far weaker and are held to their own, lower bounds below.
const SIDEARMS=new Set(['rapture','g18','magnum44','wingman','mozambique']);
// 2026-09-26: sixteen Muse+GLM_Maps melee weapons (ten boss relics, six bench weapons) append after wire_whip.
test('exactly 29 guns (24 late-expedition + 5 common sidearms) and 32 melee append stable IDs with bounded profiles',()=>{
  assert.equal(expansion.gunSpecs.length,29);assert.equal(expansion.meleeSpecs.length,32);
  assert.equal(new Set([...expansion.gunSpecs,...expansion.meleeSpecs].map(s=>s[0])).size,61);
  for(const specs of [expansion.gunSpecs,expansion.meleeSpecs]) {
    assert.equal(new Set(specs.map(s=>s[1])).size,specs.length);
    assert.ok(specs.every(s=>s[1]>0&&s[1]<1460));
  }
  const java=fs.readFileSync(path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/Arsenal.java'),'utf8');
  const guns=[...java.matchAll(/^\s+[A-Z][A-Z0-9_]*\("([a-z0-9]+)", "([^"]+)", ChatColor\.\w+, ([\d., ]+)(?:, Pattern\.(\w+))?\)/gm)];
  assert.equal(guns.length,40);assert.equal(new Set(guns.map(g=>g[2])).size,40);
  assert.equal(guns.filter(g=>SIDEARMS.has(g[1])).length,5);
  const profiles=new Set();
  for(const match of guns) {
    const [band,capacity,cost,damage,range,pellets,penetration,spread,shot,reload]=match[3].split(',').map(Number);
    if(SIDEARMS.has(match[1])) {
      // Common sidearms: weaker than every late-expedition gun (<=12 per hit, <=15 per trigger), short to mid range.
      assert.ok(band>=1170&&band<=1210&&capacity>=2&&capacity<=28&&cost>=1&&cost<=2,match[1]);
      assert.ok(damage>=2&&damage<=12&&damage*pellets<=15&&range>=12&&range<=60,match[1]);
    } else {
      assert.ok(band>=1220&&band<=1560&&capacity>=2&&capacity<=28&&cost>=1&&cost<=8,match[1]);
      assert.ok(damage>=10&&damage<=100&&range>=22&&range<=112,match[1]);
    }
    assert.ok(pellets>=1&&pellets<=12&&penetration>=1&&penetration<=4&&spread>=0&&spread<=.14,match[1]);
    assert.ok(shot>=240&&shot<=2450&&reload>=1800&&reload<=5200,match[1]);
    assert.ok(damage*pellets<=156,'Bounded volley: '+match[1]);
    if(match[4]==='BURST')assert.ok(shot>=1600,'Burst finishes before recovery gate');
    profiles.add([damage,range,shot].join('/'));
  }
  assert.equal(profiles.size,40,'Forty mechanically different profiles');
  const equipment=fs.readFileSync(path.join(root,'server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/ExpeditionEquipment.java'),'utf8');
  const blades=[...equipment.matchAll(/melee\("([a-z_]+)", "([^"]+)", (\d+), (\d+), (\d+),/g)];
  assert.equal(blades.length,40);assert.equal(new Set(blades.map(b=>b[2])).size,40);
  for(const b of blades)assert.ok(+b[4]>=10&&+b[4]<=28&&+b[5]>=350&&+b[5]<=2000,b[1]);
  assert.doesNotMatch(java,/createExplosion|setHealth|setNoDamageTicks|setType\(/,'Firearms must not edit world or bypass native damage');
});

test('rotation regression detects backward muzzles and upside-down sights in each hand context',()=>{
  for(const [name,model] of expansion.models()) {
    const isGun=expansion.gunSpecs.some(s=>name==='apocalypse_'+s[0]+'.json');
    for(const hand of ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand']) {
      const left=hand.endsWith('lefthand'),third=hand.startsWith('third');
      if(isGun) {
        const t=model.display[hand],bad=JSON.parse(JSON.stringify(t));bad.rotation[1]+=180;
        assert.ok(heldVector([0,0,-1],t,left,third)[2]<-.98,name+'/'+hand);
        assert.ok(heldVector([0,0,-1],bad,left,third)[2]>.98,'Backward regression detected');
        const upside=JSON.parse(JSON.stringify(t));upside.rotation[2]+=180;
        assert.ok(heldVector([0,1,0],upside,left,third)[1]<-.98,'Upside-down regression detected');
      } else {
        const tip=heldVector([0,1,0],model.display[hand],left,third);
        assert.ok(third?tip[2]<-.99:tip[1]>.9&&tip[2]<-.4,name+'/'+hand);
      }
    }
  }
});

test('corrupt EPK input is rejected instead of replacing client assets',()=>{
  const bytes=Buffer.from(fs.readFileSync(path.join(root,'site','assets.epk')));
  bytes[0]=0;
  assert.throws(()=>merge(bytes,path.join(root,'apocalypse-pack')));
});
