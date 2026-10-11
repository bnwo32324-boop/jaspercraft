'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict'),crypto=require('crypto');
const root=path.resolve(__dirname,'..');
const once=(s,a,b)=>{assert.equal(s.split(a).length-1,1,'anchor: '+a.slice(0,90));return s.replace(a,()=>b);};
const previousEdits=[
 ['case 0:if(a.wO<=0){b=a.h4;', 'case 0:$p=191;case 191:$z=JasprCombatAction(a,false);if(B()){break _;}if($z)return;if(a.wO<=0){b=a.h4;'],
 ['case 0:if(!a.dw.NS){a.bTh=4;', 'case 0:$p=191;case 191:$z=JasprCombatAction(a,true);if(B()){break _;}if($z)return;if(!a.dw.NS){a.bTh=4;'],
 ['JasprGearTick(a);if(B()){break _;}b=a.G.ckx;', 'JasprGearTick(a);if(B()){break _;}$p=198;case 198:JasprCombatTick(a);if(B()){break _;}b=a.G.ckx;'],
 ['if($rt_ustr(b.S$)==="jaspr:gear"){$p=102;continue _;}', 'if($rt_ustr(b.S$)==="jaspr:gear"){$p=102;continue _;}if($rt_ustr(b.S$)==="jaspr:combat"){$p=198;continue _;}'],
 ['JasprGear.receive($rt_ustr($z));return;default:FT();}', 'JasprGear.receive($rt_ustr($z));return;case 198:$z=CRh(b.Wm,32767);if(B()){break _;}JasprBetterCombat.receive($rt_ustr($z));return;default:FT();}'],
 ['FYu(a,h,i,n,l,m,j);if(B()){break _;}c=a.ds;if(c.G.o4!=1)return;',
  'FYu(a,h,i,n,l,m,j);if(B()){break _;}$p=198;case 198:$z=JasprCombatIndicators(a,b,c);if(B()){break _;}if($z)return;c=a.ds;if(c.G.o4!=1)return;']
];
const parityEdits=previousEdits.concat([
 ['case 25:Y(b,g);if(B()){break _;}return;case 26:$z=GlD(a,b);',
  'case 25:Y(b,g);if(B()){break _;}$p=1956;case 1956:JasprCombatOptionsButton(a);if(B()){break _;}return;case 26:$z=GlD(a,b);'],
 ['case 0:if(b.bS){if(b.bF<100&&b instanceof I3){c=b.bsy;d=a.Tz;',
  'case 0:if(b.bS&&b.bF===1956){$p=1956;continue _;}if(b.bS){if(b.bF<100&&b instanceof I3){c=b.bsy;d=a.Tz;'],
 ['case 35:GGw(b,d);if(B()){break _;}return;default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,k,l,m,$p);}',
  'case 35:GGw(b,d);if(B()){break _;}return;case 1956:JasprCombatOpenOptions(a);if(B()){break _;}return;default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,j,k,l,m,$p);}']
]);
const edits=parityEdits.concat([
 ['case 39:FX2(a,j,f);if(B()){break _;}if(!k){H_();e=Lkf;}',
  'case 39:JasprCombatTransform(a,j,f,g,i);if(B()){break _;}if(!k){H_();e=Lkf;}']
]);
const begin='/* JASPR_BETTERCOMBAT_V1_BEGIN */',end='/* JASPR_BETTERCOMBAT_V1_END */';
function strip(s){let at=s.indexOf(begin);if(at<0)return s;let stop=s.indexOf(end,at);assert.ok(stop>at);const module=s.slice(at,stop),current=module.includes('function JasprCombatSweep('),motion=module.includes('function JasprCombatTransform(');s=s.slice(0,at)+s.slice(stop+end.length+1);for(const [a,b]of motion?edits:current?parityEdits:previousEdits)s=once(s,b,a);return s;}
function fn(s,name){const at=s.indexOf('function '+name+'(');assert.ok(at>=0);const end=s.indexOf('function ',at+9);assert.ok(end>at);return s.slice(at,end).trim();}
function build(source){
 const base=strip(source);let out=base;
 // Audit the actual compiled NBT method, not a stale source-map name or a mock.
 const tagHasKey=fn(base,'DaN');
 assert.ok(tagHasKey.includes('case 0:c=a.hs;')&&tagHasKey.includes('$z=D$G(c,b);'),'NBT hasKey mapping changed');
 assert.ok(fn(base,'CDe').includes('a.G1=1;a.crX=b;')&&fn(base,'GAo').includes('a.bYC=a.a2f/b'),'hand animation mapping changed');
 assert.ok(base.includes('a.bI=0.6000000238418579;a.bZ=1.7999999523162842;'),'entity height mapping changed');
 assert.ok(fn(base,'CTM').includes('k.cIE=4;')&&fn(base,'CTM').includes('k.c9V=1.0-g*0.5;'),'particle constructor mapping changed');
 assert.ok(fn(base,'CIb').includes('c=a.bop;')&&fn(base,'Ctb').includes('e=e.it;'),'particle manager mapping changed');
 let pick=fn(base,'Ev$').replace('function Ev$(','function JasprCombatPick(');
 pick=once(pick,'e=DYy(c.dw);','e=JasprBetterCombat.settings().range;');
 pick=once(pick,'if(GFy(a.bu.dw)){e=6.0;h=e;}else if(e<=3.0)h\r\n=e;else{g=1;h=e;}', 'h=e;');
 // Bounding boxes are expanded horizontally only, matching the supplied release.
 pick=once(pick,'c=De(c,e,e,e);','c=JasprCombatBox(c);');
 let module=fs.readFileSync(path.join(root,'client/bettercombat.js'),'utf8');
 // Direct translation of EntitySweepAttack2FX.renderParticle. Keep the native
 // TeaVM resumable GL calls, changing precisely the original's UV/scale formulas.
 let sweep=fn(base,'Dr7').replace('function Dr7(','function JasprCombatSweepRender(');
 sweep=once(sweep,'m=l+0.24975000321865082;n=(j/2|0)/2.0;o=n+0.49950000643730164;p=1.0*a.c9V;',
  'm=l-0.24975000321865082;n=j/2.0/2.0;o=n-0.49950000643730164;p=-1.0*a.c9V;');
 sweep=once(sweep,'case 0:j=(a.cHo+d)*3.0/a.cIE|0;',
  'case 0:JasprBetterCombat.visual.rendered++;JasprBetterCombat.visual.last={life:a.cHo,partial:d,lifetime:a.cIE,size:a.c9V,x:a.d1,y:a.db,z:a.d0};j=(a.cHo+d)*3.0/a.cIE|0;');
 module+='\n'+sweep+'\n';
 module+='\n'+pick+'\n';
 // AABB field mapping is asserted/tested against the bundled constructor below.
 module+='function JasprCombatBox(b){var w=(b.cH-b.ct)*JasprBetterCombat.settings().width,x=(b.cH+b.ct)/2,z=(b.cK+b.cz)/2;return BH(x-w,b.bv,z-w,x+w,b.cl,z+w);}\n';
 for(const [a,b]of edits)out=once(out,a,b);
 out=once(out,'/* JASPR_STATS_KEYBIND_BEGIN */',begin+'\n'+module+end+'\n/* JASPR_STATS_KEYBIND_BEGIN */');
 assert.equal(strip(out),base,'byte-exact reversal');new vm.Script(out);return out;
}
if(require.main===module){
 const source=fs.readFileSync(process.argv[2]||path.join(root,'baseline/classes.js'),'latin1');
 const output=build(source);fs.mkdirSync(path.join(root,'build/client'),{recursive:true});
 fs.writeFileSync(path.join(root,'build/client/classes.js'),output,'latin1');
 console.log(JSON.stringify({bytes:Buffer.byteLength(output,'latin1'),sha256:crypto.createHash('sha256').update(Buffer.from(output,'latin1')).digest('hex')}));
}
module.exports={build,strip,fn};
