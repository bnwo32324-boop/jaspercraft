/* JasperCraft first-person melee v5: which weapon class an ItemStack animates as, from what the client sees on it
 * (numeric item id, damage value and the NBT compound as text), plus the item's display frame for 3D models.
 * Inventory (2026-10-11): vanilla swords/axes/pickaxes/shovels/hoes; JasprApocalypse expedition melee (40, 3D cuboid
 * models on diamond_sword damage bands 1140-1530); JasprDungeon armoury (JasprPenitentArmory ids) and trophies
 * (JasprPenitentRelic kind "trophy", stone_sword 74-76); the realm armouries (JasprArmory set/piece); JasprNether and
 * JasprBackrooms weapons; Mutant Creatures' Hulk Hammer (4004), Endersoul Hand (4003) and Creeper Shard (4002).
 * Not weapons (keep the vanilla path): trinket icons on unbreakable stone swords/shovels, the sentry turret, guns,
 * gadgets, bows and shields. Classification only picks an animation; it never decides what can attack.
 */
var JasprMeleeItems = (function () {
  'use strict';
  var VANILLA = {};
  [267, 268, 272, 276, 283].forEach(function (i) { VANILLA[i] = 'sword'; });
  [258, 271, 275, 279, 286].forEach(function (i) { VANILLA[i] = 'axe'; });
  [257, 270, 274, 278, 285].forEach(function (i) { VANILLA[i] = 'pickaxe'; });
  [256, 269, 273, 277, 284].forEach(function (i) { VANILLA[i] = 'shovel'; });
  [290, 291, 292, 293, 294].forEach(function (i) { VANILLA[i] = 'hoe'; });
  // ids that only become weapons through a plugin tag (stick, sign, shears, blaze rod, prismarine shard) or by themselves (mutants)
  var MUTANTS = { 4004: 'hammer', 4003: 'claw', 4002: 'dagger' };
  var TAGGED_BASES = { 280: 1, 323: 1, 359: 1, 369: 1, 370: 1, 409: 1 };

  var APOCALYPSE = {
    trench_blade: 'dagger', gravespike: 'dagger', vesper_dagger: 'dagger', silkfang_dagger: 'dagger', hunters_kukri: 'dagger',
    thermal_machete: 'sword', cautery_sabre: 'sword', ember_falchion: 'sword', champions_gladius: 'sword', high_roller_sabre: 'sword',
    brinehook_cutlass: 'sword', shock_baton: 'sword',
    mono_katana: 'katana', voidsteel_katana: 'katana', verdict_of_rime: 'katana',
    glass_rapier: 'rapier', starmetal_rapier: 'rapier',
    rebar_sword: 'greatsword', execution_sword: 'greatsword', skybreaker_blade: 'greatsword',
    breacher_axe: 'axe', wardcleaver: 'axe', bone_reaver: 'axe', railpick: 'axe',
    magmaforged_greataxe: 'greataxe',
    gravity_maul: 'hammer', tollhammer: 'hammer', altar_mallet: 'hammer', censer_mace: 'hammer', obsidian_warhammer: 'hammer',
    sentinel_spear: 'spear', pilgrim_lance: 'spear', tidecaller_spear: 'spear',
    hollow_halberd: 'glaive', mourning_glaive: 'glaive',
    reaper_scythe: 'scythe', suture_sickle: 'scythe', nightfall_scythe: 'scythe',
    ossuary_flail: 'flail', wire_whip: 'flail'
  };
  var DUNGEON = { WAX_SCALPEL: 'dagger', HOSPICE_LANCET: 'dagger', VELVET_RAPIER: 'rapier', BASILICA_CLEAVER: 'axe', CLOISTER_FLAIL: 'flail',
    INQUISITOR_MAUL: 'hammer', SILENT_MALLET: 'hammer', AMBER_GLAIVE: 'glaive',
    // the arsenal's magic items cancel melee damage but are still swung when a click attacks
    CARRION_CENSER: 'staff', OPAL_PRISM: 'staff', SUNLESS_BRAND: 'staff', PALE_LANTERN: 'staff' };
  var TROPHY = { MERCYS_LAST_KEY: 'odd', DEEPBREAKER: 'hammer', ABYSSAL_SCEPTRE: 'staff' };
  var NETHER = {
    infernal_blade: 'sword', deathwing_talon: 'sword', khopesh: 'sword', fire_nation_dao: 'sword', gladiator_gladius: 'sword',
    cursed_katana: 'katana', wyrmbone_blade: 'greatsword', bone_reaver: 'axe', cincinnasite_axe: 'axe', cincinnasite_axe_diamond: 'axe',
    pit_lord_cleaver: 'greataxe', gatekeeper_cleaver: 'greataxe', magma_maul: 'hammer', colossus_maul: 'hammer',
    bloodfang_dagger: 'dagger', serpent_fang: 'dagger', soulreaper_scythe: 'scythe', titan_chain: 'flail',
    pharaoh_crook: 'staff', rime_scepter: 'staff', dread_staff: 'staff', sovereign_flame: 'staff', archon_wand: 'staff',
    cincinnasite_pickaxe: 'pickaxe', cincinnasite_pickaxe_diamond: 'pickaxe'
  };
  var NETHER_NOT = { ember_bow: 1, wyrmfire_bow: 1, obsidian_aegis: 1 };
  var BACKROOMS = { rebar: 'hammer', pipe_wrench: 'hammer', stop_sign: 'hammer', utility_knife: 'dagger', letter_opener: 'dagger',
    wire_cutters: 'dagger', crowbar: 'axe', arc_baton: 'sword', tidebreaker: 'sword', pallet_hook: 'pickaxe', jackhammer: 'pickaxe',
    valve_key: 'odd' };
  var SETS = { emerald: [0.4, 1, 0.55], blazeforged: [1, 0.6, 0.25], abyssal: [0.3, 0.85, 0.95], titan: [1, 0.85, 0.4], liminal: [1, 0.97, 0.78], void: [0.78, 0.5, 1] };
  var ACCENT = { dark: [0.62, 0.6, 0.75], steel: [0.9, 0.94, 1], red: [1, 0.5, 0.38], gold: [1, 0.86, 0.45], energy: [0.55, 0.95, 1], white: [1, 1, 1], green: [0.55, 1, 0.78] };
  var APOC_TINT = { trench_blade: 'steel', breacher_axe: 'red', mono_katana: 'energy', shock_baton: 'energy', gravity_maul: 'energy',
    reaper_scythe: 'dark', thermal_machete: 'red', sentinel_spear: 'gold' };
  // Server-enforced recovery (ms) of weapons without attack-speed attributes (JasprApocalypse ExpeditionEquipment,
  // JasprDungeon Arsenal): their real cooldown, so the motion paces them like the server does.
  var RECOVERY = { trench_blade: 350, breacher_axe: 1100, mono_katana: 550, shock_baton: 650, gravity_maul: 1800, reaper_scythe: 1250,
    thermal_machete: 800, sentinel_spear: 1000, gravespike: 450, railpick: 1050, wardcleaver: 850, pilgrim_lance: 1250, cautery_sabre: 950,
    suture_sickle: 700, tollhammer: 2000, rebar_sword: 1350, vesper_dagger: 400, hollow_halberd: 1500, ossuary_flail: 1400,
    ember_falchion: 1150, mourning_glaive: 1100, altar_mallet: 1700, execution_sword: 1800, wire_whip: 600, nightfall_scythe: 1300,
    glass_rapier: 400, magmaforged_greataxe: 1800, skybreaker_blade: 900, verdict_of_rime: 1100, champions_gladius: 700,
    high_roller_sabre: 800, censer_mace: 1500, brinehook_cutlass: 650, silkfang_dagger: 380, tidecaller_spear: 1100,
    starmetal_rapier: 450, obsidian_warhammer: 1900, hunters_kukri: 500, voidsteel_katana: 600, bone_reaver: 1000,
    WAX_SCALPEL: 650, CLOISTER_FLAIL: 900, BASILICA_CLEAVER: 1000, HOSPICE_LANCET: 700, INQUISITOR_MAUL: 1200, VELVET_RAPIER: 650,
    AMBER_GLAIVE: 1000, SILENT_MALLET: 950 };
  function rec(v) { return RECOVERY[v] ? RECOVERY[v] / 50 : 0; }

  // ---------------------------------------------------------------- NBT text (1.12 NBTTagCompound.toString)
  // the body of compound `key` (balanced braces, quoted strings respected), or null
  function compound(nbt, key) {
    if (typeof nbt !== 'string') return null;
    var re = new RegExp('(?:^|[,{])"?' + key + '"?:\\{'), m = re.exec(nbt);
    if (!m) return null;
    var i = m.index + m[0].length, depth = 1, start = i, quoted = false;
    for (; i < nbt.length && depth > 0; i++) {
      var ch = nbt.charAt(i);
      if (quoted) { if (ch === '\\') i++; else if (ch === '"') quoted = false; continue; }
      if (ch === '"') quoted = true; else if (ch === '{') depth++; else if (ch === '}') depth--;
    }
    return depth === 0 ? nbt.slice(start, i - 1) : null;
  }
  // a top-level field of a compound body: string or bare value
  function field(body, key) {
    if (body == null) return null;
    var depth = 0, quoted = false, i = 0, at = 0, n = body.length;
    for (; i <= n; i++) {
      var ch = i < n ? body.charAt(i) : ',';
      if (quoted) { if (ch === '\\') i++; else if (ch === '"') quoted = false; continue; }
      if (ch === '"') quoted = true;
      else if (ch === '{' || ch === '[') depth++;
      else if (ch === '}' || ch === ']') depth--;
      else if (ch === ',' && depth === 0) {
        var part = body.slice(at, i), c = part.indexOf(':');
        if (c > 0) {
          var k = part.slice(0, c).replace(/^"|"$/g, ''), v = part.slice(c + 1);
          if (k === key) return v.charAt(0) === '"' ? v.slice(1, -1).replace(/\\(.)/g, '$1') : v;
        }
        at = i + 1;
      }
    }
    return null;
  }
  function unbreakable(nbt) { return typeof nbt === 'string' && /(?:^|[,{])"?Unbreakable"?:1b/.test(nbt); }

  // ---------------------------------------------------------------- 3D model frames (JasprApocalypse melee)
  // Filled from the resource pack by the builder (melee-models.js); a model's frame maps its own rest axes (blade along
  // model +Y, cutting side +/-X) onto the canonical sprite frame the moves are authored in.
  var MODEL_TABLE = typeof JasprMeleeModelTable !== 'undefined' ? JasprMeleeModelTable : (typeof require === 'function' ? safeRequire('./melee-models.js') : null);
  function safeRequire(p) { try { return require(p); } catch (e) { return null; } }

  function classify(id, nbt, damage) {
    var tag, v;
    if ((tag = compound(nbt, 'JasprApocalypse'))) {
      v = field(tag, 'id');
      return APOCALYPSE[v] ? { cls: APOCALYPSE[v], key: 'apocalypse:' + v, model: 'apocalypse_' + v, tint: ACCENT[APOC_TINT[v] || accentOf(v)], period: rec(v) } : null;
    }
    if ((tag = compound(nbt, 'JasprCreative'))) {
      v = field(tag, 'id');
      if (APOCALYPSE[v]) return { cls: APOCALYPSE[v], key: 'apocalypse:' + v, model: 'apocalypse_' + v, tint: ACCENT[APOC_TINT[v] || accentOf(v)], period: rec(v) };
    }
    if ((tag = compound(nbt, 'JasprPenitentArmory'))) {
      v = field(tag, 'id');
      return DUNGEON[v] ? { cls: DUNGEON[v], key: 'dungeon:' + v, period: rec(v) } : null;
    }
    if ((tag = compound(nbt, 'JasprPenitentRelic'))) {
      v = field(tag, 'id');
      return field(tag, 'kind') === 'trophy' && TROPHY[v] ? { cls: TROPHY[v], key: 'trophy:' + v, tint: [1, 0.82, 0.4] } : null;
    }
    if ((tag = compound(nbt, 'JasprDungeonCreative'))) {
      v = String(field(tag, 'id') || '');
      var m = v.match(/^penitent_(armory|trophy)_([a-z0-9_]+)$/);
      if (m) { var up = m[2].toUpperCase(), c = m[1] === 'armory' ? DUNGEON[up] : TROPHY[up]; if (c) return { cls: c, key: (m[1] === 'armory' ? 'dungeon:' : 'trophy:') + up }; }
    }
    if ((tag = compound(nbt, 'JasprArmory'))) {
      var piece = field(tag, 'piece'), set = field(tag, 'set');
      return piece && /^(sword|axe|pickaxe|shovel|hoe)$/.test(piece) ? { cls: piece, key: 'armory:' + set + ':' + piece, tint: SETS[set] } : null;
    }
    if ((tag = compound(nbt, 'JasprNether'))) {
      v = String(field(tag, 'id') || '');
      if (NETHER_NOT[v]) return null;
      var nc = NETHER[v] || (/_amedian_hammer$/.test(v) ? 'hammer' : (v.match(/_amedian_(sword|axe|pickaxe|shovel|hoe)$/) || [])[1]);
      if (nc) return { cls: nc, key: 'nether:' + v, tint: /^blazed|infernal|magma|flame|fire/.test(v) ? [1, 0.55, 0.25] : /^frosted|rime/.test(v) ? [0.6, 0.85, 1] : /^withered|soul|dread|void/.test(v) ? [0.7, 0.55, 0.85] : null };
    }
    if ((tag = compound(nbt, 'JasprBackrooms'))) {
      v = field(tag, 'id');
      return BACKROOMS[v] ? { cls: BACKROOMS[v], key: 'backrooms:' + v } : null;
    }
    if (MUTANTS[id]) return { cls: MUTANTS[id], key: 'mutants:' + id, tint: id === 4003 ? [0.75, 0.45, 1] : id === 4004 ? [0.6, 0.95, 0.55] : [0.5, 1, 0.5] };
    if (!VANILLA[id]) return null;
    // trinket icons on unbreakable stone swords and shovels (damage bands) are not weapons
    if ((id === 272 || id === 273) && damage > 0 && unbreakable(nbt)) return null;
    var cls = VANILLA[id], name = displayName(nbt);
    // a few named relics on vanilla tools (JasprMuseMaps "Scythe-Hoe", "Automated Rake")
    if (cls === 'hoe' && /scythe/i.test(name)) cls = 'scythe';
    return { cls: cls, key: 'vanilla:' + id };
  }
  function accentOf(v) { var t = MODEL_TABLE && MODEL_TABLE[v]; return t && t.accent || 'steel'; }
  function displayName(nbt) { var d = compound(nbt, 'display'); return d ? String(field(d, 'Name') || '') : ''; }

  // Public: the descriptor JasprMeleeDescriptor caches per ItemStack. cls null = not a melee weapon (vanilla path).
  function describe(id, nbt, damage) {
    var c = null;
    try { c = classify(id, typeof nbt === 'string' ? nbt : null, damage | 0); } catch (e) { c = null; }
    if (!c) return { cls: null, key: 'none:' + id, id: id, family: null };
    c.id = id; c.family = c.cls;
    var t = c.model && MODEL_TABLE ? MODEL_TABLE[c.model.replace(/^apocalypse_/, '')] : null;
    if (t) c.frame = t;
    return c;
  }
  // A coarse hint from the numeric id alone (the server's settings carry only ids): could this be a melee weapon?
  function family(id) { return VANILLA[id] || MUTANTS[id] || (TAGGED_BASES[id] ? 'tagged' : null); }

  return { describe: describe, family: family, compound: compound, field: field, APOCALYPSE: APOCALYPSE, DUNGEON: DUNGEON, TROPHY: TROPHY,
    NETHER: NETHER, BACKROOMS: BACKROOMS, MUTANTS: MUTANTS, VANILLA: VANILLA, SETS: SETS, ACCENT: ACCENT };
}());
if (typeof module !== 'undefined' && module.exports) module.exports = JasprMeleeItems;
