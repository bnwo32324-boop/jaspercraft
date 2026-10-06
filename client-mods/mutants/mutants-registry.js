/* Registries and shared engine helpers: RegistryHandler.java / MBSoundEvents.java / MBParticles.java on the client.
 * Fixed id tables of MUTANTS_PROTOCOL.md 1.1 (entities 210-224), 1.3 (items 4000-4014), 1.4 (sound events
 * 1000-1042), 1.5 (particles 100, 101). Runs once from the end of Bootstrap.register (after vanilla registration,
 * before models, renderers, item colours and the resource reload), like Forge's registry events. */
(function (M) {
  "use strict";
  var R = M.R;

  // ---------------------------------------------------------------- enums, interfaces, statics
  M.ENUM = {};
  M.IFACE = {};
  M.initEnums = function () {
    BT(); M.ENUM.ActionResult = { SUCCESS: HFg, PASS: Ks1, FAIL: KwS };               // EnumActionResult
    Hc(); M.ENUM.Hand = { MAIN_HAND: HFd, OFF_HAND: Kt_ };                            // EnumHand
    Wq(); M.ENUM.Action = { NONE: KN_, EAT: Kui, DRINK: Kuh, BLOCK: Kul, BOW: Lbp };  // EnumAction
    K4(); M.ENUM.CreatureAttribute = { UNDEFINED: Kt8, UNDEAD: KtD, ARTHROPOD: Kx6, ILLAGER: KAt };
    DZ(); M.ENUM.PathNodeType = { WATER: KxH };
    ALq(); M.ENUM.Rarity = { COMMON: KOa, UNCOMMON: LcW, RARE: KOb, EPIC: LbR };
    Cx(); M.ENUM.SoundCategory = { MASTER: Lnd, MUSIC: LnN, RECORDS: K1p, WEATHER: LnO, BLOCKS: KvZ, HOSTILE: KxK, NEUTRAL: Ks5, PLAYERS: KuC, AMBIENT: KAP, VOICE: LnP };
    M.IFACE = { IMob: O6, IAnimals: W2, IEntityOwnable: AWL, IProjectile: A0Z, IJumpingMount: AP8, IRangedAttackMob: AGz, Predicate: Dp };
  };
  // ResourceLocation (domain defaults to minecraft, like new ResourceLocation(String))
  M.rl = function (s) { var r = new Bb(); Gp9(r, M.jstr(s)); return r; };
  M.rlString = function (r) { return M.ustr(r.m2) + ":" + M.ustr(r.iX); };

  // ---------------------------------------------------------------- attributes (SharedMonsterAttributes)
  var SWIM_SPEED = null;                                       // Forge's EntityLivingBase.SWIM_SPEED
  function attribute(name) {
    Dh();
    switch (name) {
      case "MAX_HEALTH": return Kth; case "FOLLOW_RANGE": return Kxc; case "KNOCKBACK_RESISTANCE": return Kti;
      case "MOVEMENT_SPEED": return Ktj; case "ATTACK_DAMAGE": return Kut; case "ATTACK_SPEED": return Kuu;
      case "ARMOR": return Ktk; case "ARMOR_TOUGHNESS": return Ktl; case "LUCK": return Kuv;
      case "SWIM_SPEED":
        if (SWIM_SPEED === null) { SWIM_SPEED = Vu(null, M.jstr("forge.swimSpeed"), 1.0, 0.0, 1024.0); SWIM_SPEED.a0j = 1; }
        return SWIM_SPEED;
    }
    throw new Error("unknown attribute " + name);
  }
  M.attribute = attribute;
  // Forge registers SWIM_SPEED for every living entity; vanilla classes of this client have none, so the mod's
  // classes register it themselves (setBase is reached through getEntityAttribute like the mod's code).
  function instanceOf(e, name) {
    var map = EXX(e), a = attribute(name), inst = EAj(e, a);
    if (inst === null && name === "SWIM_SPEED") inst = GdL(map, a);
    return inst;
  }
  M.setBase = function (e, name, v) { var inst = instanceOf(e, name); if (inst === null) throw new Error("attribute " + name + " not registered"); Gtv(inst, v); };
  M.registerAttribute = function (e, name, v) { var inst = GdL(EXX(e), attribute(name)); Gtv(inst, v); return inst; };
  M.attributeValue = function (e, name) { return F7V(instanceOf(e, name)); };

  // ---------------------------------------------------------------- items, stacks, potions (vanilla objects by registry name)
  var vanillaItems = Object.create(null);
  M.vanillaItem = function (name) {
    var it = vanillaItems[name];
    if (it === undefined) { FM(); it = E2Z(HEO, M.rl(name)); vanillaItems[name] = it; }
    return it;
  };
  M.ITEMS = {};
  M.initVanillaItems = function () {
    ["saddle", "gunpowder", "flint_and_steel", "snowball", "carrot", "potato", "beetroot", "porkchop", "spider_eye",
      "spawn_egg", "lead", "fermented_spider_eye", "ender_eye", "bow", "arrow"].forEach(function (n) { M.ITEMS[n.toUpperCase()] = M.vanillaItem(n); });
    M.ITEMS.TNT = DJQ(M.block("tnt"));                       // Item.getItemFromBlock(Blocks.TNT)
  };
  M.block = function (name) { return E2Z(M.BLOCK_REGISTRY(), M.rl(name)); };
  M.BLOCK_REGISTRY = function () { CZ(); return HFK; };       // Block.REGISTRY
  M.itemOf = function (stack) { return C52(stack); };          // ItemStack.getItem
  M.stack = {
    isEmpty: function (s) { return !!CCH(s); }, shrink: function (s, n) { CYj(s, n); }, grow: function (s, n) { CaT(s, n); },
    getCount: function (s) { return CRE(s); }, damageItem: function (s, n, e) { Gql(s, n, e); },
    interactWithEntity: function (s, player, e, hand) { return !!FVw(s, player, e, hand); },
    getItemDamage: function (s) { return EHb(s); }, setItemDamage: function (s, d) { EKS(s, d); },
    getMaxDamage: function (s) { return EjU(s); }, isItemDamaged: function (s) { return !!CU1(s); },
    getSubCompound: function (s, key) { return GaK(s, M.jstr(key)); }
  };
  M.foodHealAmount = function (item, stack) { return item.cJk(stack); };   // ItemFood.getHealAmount
  var potions = Object.create(null);
  M.potion = function (name) { var p = potions[name]; if (p === undefined) { p = B$K(M.jstr(name)); potions[name] = p; } return p; };
  M.POTIONS = {};
  M.initPotions = function () { M.POTIONS.POISON = M.potion("poison"); M.POTIONS.UNLUCK = M.potion("unluck"); };
  M.potionOf = function (effect) { return Cu5(effect); };    // PotionEffect.getPotion
  M.SPIDER_PIG_TEMPTATION_ITEMS = function () { var I = M.ITEMS; return [I.CARROT, I.POTATO, I.BEETROOT, I.PORKCHOP, I.SPIDER_EYE]; };
  M.optionalOrNull = function (opt) { return opt.bc0(); };     // com.google.common.base.Optional.orNull
  M.TAMED_KEY = function () { ACw(); return KCY; };           // EntityTameable.TAMED
  M.initEntityCreature = function (e, world) { Bzx(e, world); };
  M.setPathPriority = function (e, node, v) { CUc(e, M.ENUM.PathNodeType[node], v); };
  M.I18n = { translateToLocal: function (key) { return Fg2(M.jstr(key)); } };
  M.MOVER_SELF = function () { F1(); return Kst; };           // MoverType.SELF
  M.defaultState = function (block) { return block.c9H(); };  // Block.getDefaultState
  M.aiSitSetSitting = function () {};                          // EntityAISit exists only on the server side of the mod
  // AxisAlignedBB (grow = symmetric, expand = directional, as in 1.12.2 MCP)
  M.aabb = {
    grow: function (bb, d) { return De(bb, d, d, d); },
    grow3: function (bb, x, y, z) { return De(bb, x, y, z); },
    expand: function (bb, x, y, z) { return M.aabbExpand(bb, x, y, z); },
    intersects: function (a, b) { return a.ct < b.cH && a.cH > b.ct && a.bv < b.cl && a.cl > b.bv && a.cz < b.cK && a.cK > b.cz; }
  };
  M.aabbExpand = function (bb, x, y, z) {               // AxisAlignedBB.expand (old addCoord)
    var d0 = bb.ct, d1 = bb.bv, d2 = bb.cz, d3 = bb.cH, d4 = bb.cl, d5 = bb.cK;
    if (x < 0.0) d0 += x; else if (x > 0.0) d3 += x;
    if (y < 0.0) d1 += y; else if (y > 0.0) d4 += y;
    if (z < 0.0) d2 += z; else if (z > 0.0) d5 += z;
    var r = new DN(); ED(r, d0, d1, d2, d3, d4, d5); return r;
  };

  // ---------------------------------------------------------------- MBSoundEvents.java (FIELD declaration order)
  var SOUND_NAMES = [
    "entity.creeper_minion.ambient", "entity.creeper_minion.death", "entity.creeper_minion.hurt", "entity.creeper_minion.primed",
    "entity.creeper_minion_egg.hatch", "entity.endersoul_clone.death", "entity.endersoul_clone.teleport",
    "entity.endersoul_fragment.explode", "entity.mutant_creeper.ambient", "entity.mutant_creeper.charge",
    "entity.mutant_creeper.death", "entity.mutant_creeper.hurt", "entity.mutant_enderman.ambient",
    "entity.mutant_enderman.death", "entity.mutant_enderman.hurt", "entity.mutant_enderman.morph",
    "entity.mutant_enderman.scream", "entity.mutant_enderman.stare", "entity.mutant_enderman.teleport",
    "entity.mutant_skeleton.ambient", "entity.mutant_skeleton.ambient.legacy", "entity.mutant_skeleton.bite",
    "entity.mutant_skeleton.bow_draw", "entity.mutant_skeleton.bow_shoot", "entity.mutant_skeleton.death",
    "entity.mutant_skeleton.death.legacy", "entity.mutant_skeleton.hurt", "entity.mutant_skeleton.hurt.legacy",
    "entity.mutant_skeleton.jump", "entity.mutant_skeleton.punch", "entity.mutant_skeleton.step",
    "entity.mutant_skeleton.step.legacy", "entity.mutant_snow_golem.death", "entity.mutant_snow_golem.hurt",
    "entity.mutant_zombie.ambient", "entity.mutant_zombie.attack", "entity.mutant_zombie.death",
    "entity.mutant_zombie.grunt", "entity.mutant_zombie.hurt", "entity.mutant_zombie.roar",
    "entity.spider_pig.ambient", "entity.spider_pig.death", "entity.spider_pig.hurt"
  ];
  M.SOUND_NAMES = SOUND_NAMES;
  M.SOUND_BASE_ID = 1000;
  function fieldName(n) { return n.replace(/[.]/g, "_").toUpperCase(); }   // entity.mutant_zombie.roar -> ENTITY_MUTANT_ZOMBIE_ROAR
  M.SND = { byId: Object.create(null) };
  var vanillaSounds = Object.create(null);
  M.SND.vanilla = function (name) {
    var ev = vanillaSounds[name];
    if (ev === undefined) { AIX(); ev = E2Z(KTr, M.rl(name)); vanillaSounds[name] = ev; }
    return ev;
  };
  M.registerSounds = function () {
    AIX();
    for (var i = 0; i < SOUND_NAMES.length; i++) {
      var name = "jaspr.mutants." + SOUND_NAMES[i], rl = M.rl(name);
      var ev = new AT5(); ev.WL = rl;                         // new SoundEvent(minecraft:jaspr.mutants.<event>)
      DKx(KTr, M.SOUND_BASE_ID + i, rl, ev);                  // SoundEvent.REGISTRY.register(id, name, event)
      M.SND[fieldName(SOUND_NAMES[i])] = ev;
      M.SND.byId[M.SOUND_BASE_ID + i] = ev;
    }
  };

  // ---------------------------------------------------------------- MBParticles.java
  M.MBParticles = {};
  M.registerParticleTypes = function () {
    CC();
    var defs = [["mutantbeasts:endersoul", M.CFG.GENERAL.endersoulParticleID, "ENDERSOUL"], ["mutantbeasts:skull_spirit", M.CFG.GENERAL.skullSpiritParticleID, "SKULL_SPIRIT"]];
    var values = Array.prototype.slice.call(LvQ.data);
    for (var i = 0; i < defs.length; i++) {
      if (D2a(defs[i][1]) !== null) throw new Error("The particle " + defs[i][0] + " has the same ID as the particle " + M.ustr(D2a(defs[i][1]).cRV));
      var p = new Dl(), name = M.jstr(defs[i][0]);
      BAY(p, name, values.length, name, defs[i][1], 1, 0);     // EnumHelper.addEnum(name, id, shouldIgnoreRange true, 0 args)
      values.push(p);
      D01(LvP, M.boxInt(defs[i][1]), p);                       // PARTICLES.put(id, type)
      D01(Lp3, name, p);                                       // BY_NAME.put(name, type)
      M.MBParticles[defs[i][2]] = p;
    }
    LvQ = T(Dl, values);                                       // EnumParticleTypes.$VALUES, as EnumHelper extends it
  };

  // ---------------------------------------------------------------- RegistryHandler.onEntityEntryRegistry
  var ctorClass = null;
  function entityConstructor(create) {                       // net.peyton.eagler.minecraft.EntityConstructor
    if (!ctorClass) {
      ctorClass = function () { D.call(this); this.$create = null; };
      $rt_metadata([ctorClass, "chumbanotz.mutantbeasts.JasprEntityConstructor", -1, D, [], 0, 3, 0, 0, 0]);
      ctorClass.prototype.du = function (world) { return this.$create(world); };
    }
    var c = new ctorClass(); c.$create = create; return c;
  }
  M.registerEntities = function () {
    I$();
    for (var i = 0; i < M.ENTITY_TYPES.length; i++) {
      var t = M.ENTITY_TYPES[i];
      // EntityEntryBuilder.id(mutantbeasts:<name>, index).name("mutantbeasts." + name) -> EntityList id 210 + index
      D6a(t.id, M.jstr("mutantbeasts:" + t.name), E(t.cls), entityConstructor(t.cls.create), M.jstr("mutantbeasts." + t.name));
      if (t.egg) GeT(M.jstr("mutantbeasts:" + t.name), t.egg[0], t.egg[1]);     // EntityList.ENTITY_EGGS
      t.cls.$jmType = t;
    }
  };
  M.typeById = function (id) { for (var i = 0; i < M.ENTITY_TYPES.length; i++) if (M.ENTITY_TYPES[i].id === id) return M.ENTITY_TYPES[i]; return null; };

  // ---------------------------------------------------------------- install (registry hook)
  M.installed = false;
  M.install = function () {
    if (M.installed) return;
    M.installAliases(M.FIELDS, M.METHODS, M.CLASSES);
    M._markAliases();
    M.initEnums();
    M.initPotions();
    M.registerSounds();
    M.registerParticleTypes();
    M.defineEntities();
    M.registerEntities();
    if (M.defineItems) { M.defineItems(); M.registerItems(); }
    M.initVanillaItems();
    M.installed = true;
    M.stats.installs++;
  };
})(JasprMutants);
