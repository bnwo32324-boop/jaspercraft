'use strict';
/* Adds the Survivor Gear panel, keybinds, plugin channel and Creative catalogue entries to the
 * deployed TeaVM client. Writes only candidate/gear-client/ (or --out <file>).
 *
 * Binary-safe: the bundle is processed as latin1 so every byte round-trips unchanged; everything
 * inserted is pure ASCII. Each anchor must occur exactly once and be consumed by its own edit;
 * the fenced blocks (JASPR_GEAR_V1 and JASPR_GEAR_CAT) are removable by marker. A bundle that
 * already carries this extension is unpatched first, then regenerated, so the builder can be
 * re-run on the live client after other features changed it. Reversal must restore the input
 * byte for byte, and the result must parse.
 */
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), vm = require('node:vm');
const ROOT = path.join(__dirname, '..');
const SOURCE = process.env.GEAR_CLIENT_SOURCE || path.join(ROOT, 'site', 'classes.js');
const MODULE = path.join(ROOT, 'client-mods', 'gear-teavm.js');
const CATALOG = path.join(ROOT, 'candidate', 'gear', 'gear-catalog.json');
const BEGIN = '/* JASPR_GEAR_V1_BEGIN */', END = '/* JASPR_GEAR_V1_END */';
const CAT_BEGIN = '/*JASPR_GEAR_CAT_BEGIN*/', CAT_END = '/*JASPR_GEAR_CAT_END*/';
const RB_BEGIN = '/*JASPR_GEAR_RB_BEGIN*/', RB_END = '/*JASPR_GEAR_RB_END*/';
const sha = s => crypto.createHash('sha256').update(Buffer.from(s, 'latin1')).digest('hex');
const ascii = s => s.replace(/[\u0080-￿]/g, ch => '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0'));

function count(haystack, needle) { let n = 0, i = -1; while ((i = haystack.indexOf(needle, i + 1)) !== -1) n++; return n; }
function replaceOnce(text, from, to, label) {
  const found = count(text, from);
  if (found !== 1) throw new Error(label + ': expected 1 anchor, got ' + found);
  const out = text.split(from).join(to);
  if (count(out, from) !== 0 && !to.includes(from)) throw new Error(label + ': anchor survived');
  return out;
}

const E3X_HEAD = 'function E3x(a,b,c){var d,e,f,g,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){case 0:$p=90;case 90:JasprRecipeBookDraw(a,b,c);if(B()){break _;}$p=91;';

// [from, to] pairs; every "to" contains a marker so reversal is unambiguous.
const EDITS = [
  ['JasprZoomInstall(a);if(B()){break _;}$p=38;case 38:DBw(a);',
   'JasprZoomInstall(a);if(B()){break _;}$p=96;case 96:JasprGearInstall(a);if(B()){break _;}$p=38;case 38:DBw(a);'],
  ['case 0:if(b!==null&&b===JasprDynamicLightsKeyDescription)return JasprDynamicLightsKeyLabel;',
   'case 0:if(b!==null&&b.$jasprGearLabel)return b.$jasprGearLabel;if(b!==null&&b===JasprDynamicLightsKeyDescription)return JasprDynamicLightsKeyLabel;'],
  ['JasprWaypointTabPoll(a,b,ANI(),HFV!==null&&HFV.cX2===2);',
   'JasprWaypointTabPoll(a,b,ANI(),HFV!==null&&HFV.cX2===2);JasprGearKeys.key(a,b,ANI(),HFV!==null&&HFV.cX2===2);'],
  ['if(c>=0)JasprWaypointTabPoll(a,b,d,false);',
   'if(c>=0)JasprWaypointTabPoll(a,b,d,false);if(c>=0)JasprGearKeys.key(a,b,d,false);'],
  ['JasprZoomTick(a);if(B()){break _;}b=a.G.ckx;',
   'JasprZoomTick(a);if(B()){break _;}$p=97;case 97:JasprGearTick(a);if(B()){break _;}b=a.G.ckx;'],
  ['if($rt_ustr(b.S$)==="JASPR|World"){$p=101;continue _;}',
   'if($rt_ustr(b.S$)==="JASPR|World"){$p=101;continue _;}if($rt_ustr(b.S$)==="jaspr:gear"){$p=102;continue _;}'],
  ['JasprDH.context($rt_ustr($z));return;default:FT();}',
   'JasprDH.context($rt_ustr($z));return;case 102:$z=CRh(b.Wm,32767);if(B()){break _;}JasprGear.receive($rt_ustr($z));return;default:FT();}'],
  [E3X_HEAD, E3X_HEAD.replace('$p=91;', '$p=93;case 93:JasprGearDraw(a,b,c);if(B()){break _;}$p=91;')],
  ['a.dM3=b;a.dM5=c;return;case 9:CEP(a,d,b,c);',
   'a.dM3=b;a.dM5=c;$p=97;case 97:JasprGearTooltip(a,b,c);if(B()){break _;}return;case 9:CEP(a,d,b,c);'],
  ['case 0:$p=92;case 92:JasprRecipeBookClick(a,b,c,d);',
   'case 0:if(JasprGearMouseDown(a,b,c,d))return;$p=92;case 92:JasprRecipeBookClick(a,b,c,d);'],
  ['switch($p){case 0:if(JasprUiRepairDue()){$p=991;continue _;}$p=1;case 1:$z=FBP(a,b,c);',
   'switch($p){case 0:if(JasprGearMouseUp(a,b,c,d))return;if(JasprUiRepairDue()){$p=991;continue _;}$p=1;case 1:$z=FBP(a,b,c);'],
  // Phase 2 HUD: GuiIngame.renderGameOverlay, after renderPotionEffects (font f, scaled d x e).
  ['GEJ(a,c);if(B()){break _;}k=a.dfA;$p=43;',
   'GEJ(a,c);if(B()){break _;}$p=190;case 190:JasprGearHud(a,d,e,f);if(B()){break _;}k=a.dfA;$p=43;'],
  // Phase 3: worn gear on player models (LayerCustomHead.doRenderLayer, before the head item).
  ['case 0:Dt();j=KtI;$p=1;case 1:$z=b.yI(j);',
   'case 0:$p=95;case 95:JasprGearWorn(a,b);if(B()){break _;}Dt();j=KtI;$p=1;case 1:$z=b.yI(j);'],
  // Phase 3: the gear column on the Creative inventory tab (foreground layer; tooltip after vanilla's).
  ['case 0:$p=1;case 1:CD();if(B()){break _;}d=KZU.data;$p=2;case 2:Qu();if(B()){break _;}e=d[KWg];if(!e.b9W)return;',
   'case 0:$p=90;case 90:JasprGearDraw(a,b,c);if(B()){break _;}$p=1;case 1:CD();if(B()){break _;}d=KZU.data;$p=2;case 2:Qu();if(B()){break _;}e=d[KWg];if(!e.b9W)return;'],
  ['case 10:Fog(a,b,c);if(B()){break _;}return;',
   'case 10:Fog(a,b,c);if(B()){break _;}$p=97;case 97:JasprGearTooltip(a,b,c);if(B()){break _;}return;'],
  // Phase 3: EasierCrafting groups gear recipes under their own heading.
  ['supply: "Supplies", artifact: "Relics"',
   'supply: "Supplies", artifact: "Relics", gear: "Survivor Gear" /*JASPR_GEAR_RB_LABEL*/'],
  ['function JasprCreativeTabAllows(a,b){',
   'function JasprCreativeTabAllows(a,b){if(b===\'gear\')return a===KQL;'],
  ['/* JASPR_STATS_KEYBIND_BEGIN */', null], // module insertion point (handled below)
];

function catalogEntries(catalog) {
  const supplies = (catalog.consumables || []).map(item => {
    const color = '§' + ({1: 'a', 2: 'a', 3: 'b', 4: 'd', 5: '6'})[item.rarity || 1];
    return ascii(JSON.stringify({id: 'gear_' + item.id, title: item.title, category: 'gear', material: 'minecraft:stone_hoe',
      model: item.model, color: color, snbt: item.snbt,
      search: ['gear supply consumable survivor adrenaline', item.id.replace(/_/g, ' '), item.title, item.inspiredBy].join(' ').toLowerCase()}));
  });
  return catalog.items.map(item => {
    const color = '§' + ({1: 'a', 2: 'a', 3: 'b', 4: 'd', 5: '6'})[item.rank || 1];
    return ascii(JSON.stringify({id: 'gear_' + item.id, title: item.title, category: 'gear', material: 'minecraft:stone_hoe',
      model: item.model, color: color, snbt: item.snbt,
      search: ['gear trinket bauble survivor', item.id.replace(/_/g, ' '), item.title, item.type.toLowerCase(), item.inspiredBy].join(' ').toLowerCase()}));
  }).concat(supplies);
}

function moduleBlock(catalog) {
  const icons = catalog.icons.slice().sort((a, b) => a.slot - b.slot).map(i => i.snbt);
  if (icons.length !== 7) throw new Error('need 7 slot icons');
  const source = fs.readFileSync(MODULE, 'utf8');
  if (count(source, '__JASPR_GEAR_ICONS__') !== 1) throw new Error('icon placeholder');
  const body = source.replace('__JASPR_GEAR_ICONS__', ascii(JSON.stringify(icons))).replace(/\r\n/g, '\n');
  if (/[^\x00-\x7f]/.test(body)) throw new Error('module must be ASCII');
  return BEGIN + '\n' + body + (body.endsWith('\n') ? '' : '\n') + END + '\n';
}

function strip(text) {
  // Remove a previous build of this extension (any version of the module/catalogue content).
  let out = text;
  const b = out.indexOf(BEGIN);
  if (b >= 0) {
    const e = out.indexOf(END, b);
    if (e < 0 || count(out, BEGIN) !== 1) throw new Error('corrupt previous gear block');
    let end = e + END.length;
    if (out[end] === '\n') end++;
    out = out.slice(0, b) + out.slice(end);
  }
  const rb = out.indexOf(RB_BEGIN);
  if (rb >= 0) {
    const re = out.indexOf(RB_END, rb);
    if (re < 0 || count(out, RB_BEGIN) !== 1) throw new Error('corrupt previous recipe block');
    out = out.slice(0, rb) + out.slice(re + RB_END.length);
  }
  const cb = out.indexOf(CAT_BEGIN);
  if (cb >= 0) {
    const ce = out.indexOf(CAT_END, cb);
    if (ce < 0 || count(out, CAT_BEGIN) !== 1) throw new Error('corrupt previous catalogue block');
    out = out.slice(0, cb) + out.slice(ce + CAT_END.length);
  }
  for (const [from, to] of EDITS) if (to && count(out, to) === 1) out = out.split(to).join(from);
  return out;
}

function apply(input, catalog) {
  let out = input;
  for (let i = 0; i < EDITS.length; i++) {
    const [from, to] = EDITS[i];
    if (to === null) out = replaceOnce(out, from, moduleBlock(catalog) + from, 'module');
    else out = replaceOnce(out, from, to, 'edit#' + i);
  }
  // Creative catalogue: append inside the existing array literal, fenced by comments.
  const start = out.indexOf('var JasprCreativeCatalog=[');
  if (start < 0 || count(out, 'var JasprCreativeCatalog=[') !== 1) throw new Error('catalogue anchor');
  const close = out.indexOf('}];', start);
  const lineEnd = out.indexOf('\n', start);
  if (close < 0 || close > lineEnd) throw new Error('catalogue must end on its own line');
  const entries = catalogEntries(catalog);
  out = out.slice(0, close + 1) + CAT_BEGIN + ',' + entries.join(',') + CAT_END + out.slice(close + 1);
  // Phase 3: gear recipes in the EasierCrafting table (same fenced-literal technique).
  const tstart = out.indexOf('var JasprBlueprintTable = [');
  if (tstart < 0 || count(out, 'var JasprBlueprintTable = [') !== 1) throw new Error('recipe table anchor');
  const tclose = out.indexOf('}];', tstart), tlineEnd = out.indexOf('\n', tstart);
  if (tclose < 0 || tclose > tlineEnd) throw new Error('recipe table must end on its own line');
  const recipes = (catalog.recipes || []).map(r => ascii(JSON.stringify(r)));
  if (!recipes.length) throw new Error('catalog has no recipes (rebuild the plugin: GearExport)');
  out = out.slice(0, tclose + 1) + RB_BEGIN + ',' + recipes.join(',') + RB_END + out.slice(tclose + 1);
  return out;
}

function build() {
  const raw = fs.readFileSync(SOURCE, 'latin1');
  const catalog = JSON.parse(fs.readFileSync(CATALOG, 'utf8'));
  const base = strip(raw);
  if (base.includes('JasprGear')) throw new Error('unexpected JasprGear residue after strip');
  const result = apply(base, catalog);
  const restored = strip(result);
  if (restored !== base) throw new Error('reversal did not restore the unpatched client byte for byte');
  if (strip(apply(result === base ? base : restored, catalog)) !== base) throw new Error('rebuild not stable');
  new vm.Script(Buffer.from(result, 'latin1').toString('utf8'), {filename: 'classes.js'}); // parses
  // The EasierCrafting table must still be one valid literal carrying every gear recipe.
  const tableLine = line => { const i = line.indexOf('var JasprBlueprintTable = ['); return line.slice(i + 26, line.indexOf('\n', i)).replace(/;\s*$/, ''); };
  const table = JSON.parse(tableLine(result).split(RB_BEGIN).join('').split(RB_END).join(''));
  const baseTable = JSON.parse(tableLine(base));
  if (table.length !== baseTable.length + (catalog.recipes || []).length) throw new Error('recipe table size');
  for (const r of catalog.recipes || []) {
    const got = table.find(t => t.id === r.id);
    if (!got || JSON.stringify(got) !== JSON.stringify(r)) throw new Error('recipe missing or altered: ' + r.id);
    for (const row of r.shape) for (const ch of row) if (ch !== '.' && !r.keys[ch]) throw new Error('recipe key ' + ch + ' ' + r.id);
  }
  return {raw, base, result, catalog};
}

if (require.main === module) {
  const {raw, base, result, catalog} = build();
  const outIndex = process.argv.indexOf('--out');
  const target = outIndex > 0 ? process.argv[outIndex + 1] : path.join(ROOT, 'candidate', 'gear-client', 'classes.js');
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, Buffer.from(result, 'latin1'));
  const manifest = {stage: 'survivor-gear-v3', source: path.relative(ROOT, SOURCE), sourceSha256: sha(raw),
    unpatchedSha256: sha(base), sha256: sha(result), bytes: Buffer.byteLength(result, 'latin1'),
    addedBytes: Buffer.byteLength(result, 'latin1') - Buffer.byteLength(base, 'latin1'), edits: EDITS.length + 1,
    catalogueEntries: catalog.items.length + (catalog.consumables || []).length, recipes: (catalog.recipes || []).length,
    hud: 'Ewc state 190 (JasprGearHud)', worn: 'Eyq state 95 (JasprGearWorn)', creative: 'Gzj state 90, Chu state 97',
    keys: {arc: 'G (34)', dodge: 'H (35)', magnet: 'J (36)', mutate: 'R (19)'}, channel: catalog.channel};
  fs.writeFileSync(path.join(path.dirname(target), 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest, null, 2));
}
module.exports = {build, strip, apply, EDITS};
