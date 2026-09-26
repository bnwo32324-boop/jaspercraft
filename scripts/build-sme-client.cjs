'use strict';
/* So Many Enchantments client stage (JasprEnchantments). Registers SME's 130 enchantments in the TeaVM client's
 * enchantment registry at the server's fixed ids (resources/enchantments.tsv), right after vanilla's own
 * registerEnchantments (ExK) -- so item tooltips, enchanting-table hints, anvils and the Creative enchanted-book lists
 * know them. Names come from the lang block (scripts/build-sme-assets.cjs); curses are red through the client's own
 * isCurse; tooltip attack damage includes SME's flat bonuses (sharpness tiers, reinforced sharpness, bluntness,
 * jagged rake), as SME's calcDamageByCreature does.
 *   SME_CLIENT_SOURCE=<classes.js> node scripts/build-sme-client.cjs
 * Output: candidate/sme-client/classes.js. Fenced (JASPR_SME_BEGIN/END + one hooked line), reversible, CR-preserving.
 */
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), assert = require('node:assert/strict');
const ROOT = path.join(__dirname, '..');
const SOURCE = process.env.SME_CLIENT_SOURCE || path.join(ROOT, 'site', 'classes.js');
const TSV = path.join(ROOT, 'server', 'custom-plugins', 'JasprEnchantments', 'resources', 'enchantments.tsv');
const BEGIN = '/* JASPR_SME_BEGIN */', END = '/* JASPR_SME_END */';
// One-line hooks into the client, each anchored exactly once (reversed by strip()):
const HOOKS = [
  // Enchantment.registerEnchantments (ExK): register SME after vanilla's last entry.
  ['case 91:DKx(g,e,i,d);if(B()){break _;}return;default:FT();}}Ds().s(',
   'case 91:DKx(g,e,i,d);if(B()){break _;}$p=92;case 92:JasprSmeRegister();if(B()){break _;}return;default:FT();}}Ds().s('],
  // EntityPlayer.getDigSpeed (EFb): Advanced Efficiency adds floor(2.5L) efficiency levels; Inefficient divides the
  // final speed by L*L+1 (SME's getEfficiencyModifier mixin and BreakSpeed handler).
  ['case 6:$z=FrL(a);if(B()){break _;}f=$z;$p=7;', 'case 6:$z=FrL(a);if(B()){break _;}f=$z+JasprSmeEff(a);$p=7;'],
  ['if(!f){if(!a.bQ)d=d/5.0;return d;}$p=16;', 'if(!f){if(!a.bQ)d=d/5.0;return d/JasprSmeIneff(a);}$p=16;'],
  ['(!a.bQ)d=d/5.0;return d;default:FT();}}Ds().s(a,b,c,d,e,f,g,h,$p);}', '(!a.bQ)d=d/5.0;return d/JasprSmeIneff(a);default:FT();}}Ds().s(a,b,c,d,e,f,g,h,$p);}'],
  // EntityLivingBase.jump (D73): Light Weight / Heavy Weight scale the finished jump (SME's LivingJumpEvent).
  ['case 4:$z=CBf(a);if(B()){break _;}d=$z;if(!d){a.pU=1;return;}', 'case 4:$z=CBf(a);if(B()){break _;}d=$z;if(!d){a.p=a.p*JasprSmeJump(a);a.pU=1;return;}'],
  ['a.t=e+b*0.20000000298023224;a.pU=1;return;default:FT();}}Ds().s(a,b,c,d,e,f,g,$p);}', 'a.t=e+b*0.20000000298023224;a.p=a.p*JasprSmeJump(a);a.pU=1;return;default:FT();}}Ds().s(a,b,c,d,e,f,g,$p);}'],
  // EntityLivingBase.travel, water branch: Swift Swimming's swim-speed attribute (Forge SWIM_SPEED, op 1).
  ['case 27:DzX(a,b,c,d,p);if(B()){break _;}', 'case 27:DzX(a,b,c,d,p*JasprSmeSwim(a));if(B()){break _;}'],
  // EntityLivingBase.updateActiveHand: Strafe shortens the draw (SME's LivingEntityUseItemEvent.Tick).
  ['if(c!==d){$p=4;continue _;}e=a.wS;if(e<=25&&!(e%4|0)){', 'if(c!==d){$p=4;continue _;}a.wS=JasprSmeStrafe(d,a.wS);e=a.wS;if(e<=25&&!(e%4|0)){'],
];
const HOOK_FROM = HOOKS[0][0];

// Vanilla enchantment whose rarity / item type / slots an SME enchantment borrows on the client (tabs, anvil preview).
const TEMPLATE = {
  0: 'advancedprotection advancedblastprotection advancedfireprotection advancedprojectileprotection magicprotection physicalprotection supremeprotection breachedplating curseofvulnerability evasion',
  2: 'advancedfeatherfalling lightweight magmawalker swiftswimming',
  5: 'combatmedic',
  7: 'advancedthorns burningthorns meltdown innerberserk strengthenedvitality',
  48: 'advancedpower advancedpunch pushing splitshot strafe lesserflame advancedflame supremeflame rune_arrowpiercing powerless dragging',
  61: 'advancedluckofthesea advancedlure',
  32: 'advancedefficiency inefficient reinforcedsharpness smelter',
  34: 'jaggedrake moisturized plowing burningshield empowereddefence naturalblocking rune_resurrection rune_revival rusted',
  70: 'advancedmending curseofdecay curseofholding curseofpossession pandorascurse upgradedpotentials',
};
// Enchantments whose equipment slots differ from their template's (read with DKS): Heavy Weight counts every slot
// (Mending's slot list is all six), Strafe both hands (Unbreaking's list includes them).
const SLOTS = {heavyweight: 70, curseofholding: 70};
// SME calcDamageByCreature for the tooltip's creature type (UNDEFINED): a + b * level.
const DAMAGE = {lessersharpness: [0.25, 0.25], advancedsharpness: [1.25, 0.95], supremesharpness: [4.0, 1.6],
  reinforcedsharpness: [2.0, 1.3], bluntness: [0, -1.0], jaggedrake: [1.0, 0.55]};

function defs() {
  const rows = fs.readFileSync(TSV, 'utf8').trim().split(/\r?\n/);
  const head = rows.shift().split('\t');
  const col = n => { const i = head.indexOf(n); assert.ok(i >= 0, 'tsv column ' + n); return i; };
  const template = new Map();
  for (const [id, names] of Object.entries(TEMPLATE)) for (const n of names.split(' ')) template.set(n, +id);
  const out = rows.map(r => r.split('\t')).map(r => {
    const reg = r[col('regname')], dmg = DAMAGE[reg] || null;
    return [+r[col('id')], reg, +r[col('maxLevel')], +r[col('curse')], +r[col('treasure')], template.get(reg) ?? 16, dmg, SLOTS[reg] ?? null];
  });
  assert.equal(out.length, 130, 'SME defines 130 enchantments');
  assert.deepEqual(out.map(d => d[0]), Array.from({length: 130}, (_, i) => 72 + i), 'ids 72..201');
  return out;
}

function moduleText() {
  return BEGIN + '\n' +
`var JasprSmeDefs=${JSON.stringify(defs())};
function JasprSmeMake(d){var t=WY(KTJ,d[5]),o=new Ga();o.cWg=t.cWg;o.bfO=t.bfO;o.diD=t.diD;o.sW=$rt_str(d[1]);
var max=d[2],curse=d[3],treasure=d[4],dmg=d[6];o.ts=function(){return max;};if(curse)o.dIV=function(){return 1;};if(treasure)o.clX=function(){return 1;};
if(dmg)o.d_Y=function(level,creature){return dmg[0]+dmg[1]*level;};if(d[7]!==null){var s=WY(KTJ,d[7]);o.diD=s.diD;}o.jasprSme=d[1];return o;}
// Client-side movement effects. Levels are read with the client's own EnchantmentHelper (DKS over the enchantment's
// slots, GxH on one stack); every helper returns the neutral value if anything is missing.
var JasprSmeIds={};
function JasprSmeEnch(n){var id=JasprSmeIds[n];if(id===undefined){id=-1;for(var i=0;i<JasprSmeDefs.length;i++)if(JasprSmeDefs[i][1]===n)id=JasprSmeDefs[i][0];JasprSmeIds[n]=id;}return id<0?null:WY(KTJ,id);}
function JasprSmeLevel(n,entity){try{var e=JasprSmeEnch(n);return e?DKS(e,entity):0;}catch(x){return 0;}}
function JasprSmeEff(a){var l=JasprSmeLevel('advancedefficiency',a);return l>0?Math.floor(2.5*l):0;}
function JasprSmeIneff(a){var l=JasprSmeLevel('inefficient',a);return l>0?l*l+1:1;}
function JasprSmeJump(a){var f=1,l=JasprSmeLevel('lightweight',a),h=JasprSmeLevel('heavyweight',a);if(l>0)f=f*(1.05+0.15*l);if(h>0)f=f*(1-0.1*h);return f;}
function JasprSmeSwim(a){var l=JasprSmeLevel('swiftswimming',a);return l>0?1+1.3+0.4*l:1;}
function JasprSmeStrafe(stack,count){try{var e=JasprSmeEnch('strafe'),l=e?GxH(e,stack):0;if(l<=0)return count;if(l<4){if(!(count%(5-l)|0))count=count-1|0;}else count=count-(l-3)|0;return count;}catch(x){return count;}}
// Fail-safe: an enchantment that cannot be registered is skipped (shown by id only); the client always starts.
var JasprSmeFailures=0;
function JasprSmeFail(d,x){JasprSmeFailures++;try{console.error('[JasprSme] could not register '+d[1]+': '+x);}catch(y){}}
// Diagnostics only (read by support tooling): how many SME enchantments the registry now resolves.
function JasprSmeDone(){var ok=0,i,o;for(i=0;i<JasprSmeDefs.length;i++){try{o=WY(KTJ,JasprSmeDefs[i][0]);if(o&&o.jasprSme===JasprSmeDefs[i][1])ok++;}catch(x){}}
try{window.JasprSmeStatus={registered:ok,failed:JasprSmeFailures,expected:JasprSmeDefs.length};}catch(x){}}
function JasprSmeRegister(){var i,o,k,d,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();d=$T.l();k=$T.l();o=$T.l();i=$T.l();}_:while(true){switch($p){
case 0:i=0;$p=1;
case 1:if(i>=JasprSmeDefs.length){JasprSmeDone();return;}d=JasprSmeDefs[i];try{o=JasprSmeMake(d);k=new Bb;}catch(x){JasprSmeFail(d,x);i=i+1|0;continue _;}$p=2;
case 2:try{Gp9(k,$rt_str('somanyenchantments:'+d[1]));}catch(x){JasprSmeFail(d,x);i=i+1|0;$p=1;continue _;}if(B()){break _;}$p=3;
case 3:try{DKx(KTJ,d[0],k,o);}catch(x){JasprSmeFail(d,x);}if(B()){break _;}i=i+1|0;$p=1;continue _;
default:FT();}}Ds().s(i,o,k,d,$p);}
` + END + '\n';
}

const count = (h, n) => h.split(n).length - 1;
function strip(text) {
  let out = text;
  const a = out.indexOf(BEGIN);
  if (a >= 0) { const b = out.indexOf(END, a); assert.ok(b > a && count(out, BEGIN) === 1, 'SME fence damaged'); out = out.slice(0, a) + out.slice(b + END.length + 1); }
  for (const [from, to] of HOOKS) if (count(out, to) === 1) out = out.replace(to, from);
  return out;
}
function apply(base) {
  for (const [from] of HOOKS) assert.equal(count(base, from), 1, 'hook anchor: ' + from.slice(0, 60));
  const at = base.indexOf('function ExK(');
  assert.ok(at > 0 && count(base, 'function ExK(') === 1, 'ExK anchor');
  const lineStart = base.lastIndexOf('\n', at) + 1;
  // The module uses LF internally; it never introduces a CR, so the file's CR count is unchanged.
  let out = base.slice(0, lineStart) + moduleText() + base.slice(lineStart);
  for (const [from, to] of HOOKS) out = out.replace(from, to);
  return out;
}
function build() {
  const raw = fs.readFileSync(SOURCE, 'latin1');
  const base = strip(raw);
  const result = apply(base);
  assert.equal(strip(result), base, 'reversal restores the input byte for byte');
  assert.equal(count(result, '\r'), count(raw, '\r'), 'CR count unchanged');
  new vm.Script(Buffer.from(result, 'latin1').toString('utf8'), {filename: 'classes.js'});
  return {raw, result};
}
if (require.main === module) {
  const {result} = build();
  const dir = path.join(ROOT, 'candidate', 'sme-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), Buffer.from(result, 'latin1'));
  console.log(JSON.stringify({stage: 'sme-registry-v1', enchantments: 130, bytes: result.length}));
}
module.exports = {build, strip, apply, defs};
