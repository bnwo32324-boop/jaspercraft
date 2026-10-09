/* item/*.java on the client and their registrations (RegistryHandler.registerItems, ClientEventHandler.onModelRegistry,
 * MBTileEntityItemStackRenderer, MutantSkeletonArmorItem.getArmorModel): fixed item ids 4000-4014 (MUTANTS_PROTOCOL.md
 * 1.3), translation keys item.mutantbeasts.<name>, and the client paths of every item: rarity, glint, weapon attribute
 * modifiers (tooltips, attack cooldown), right-click and use-on-block prediction, hit effects, armour model and
 * texture, the Endersoul Hand's 3D hand model with its flat icon in GUI, on the ground and in frames.
 * Engine: Item = Cl (ctor Bm$; fields i8 maxStackSize, bzP maxDamage, bPB unlocalizedName, hq tab), ItemArmor = F6 (ctor
 * BR$), ArmorMaterial = Ra (LbF), virtuals o9 onItemRightClick, oq onItemUse, cPY hitEntity, cCw onBlockDestroyed,
 * bkt hasEffect, cMA getRarity, bEP getItemEnchantability, b6s getItemAttributeModifiers, d$n onUpdate.
 * Models: the items load minecraft:item/jaspr_mutants/<name> (ModelBakery.getVariantNames hook) and are registered in
 * the ItemModelMesher with the same names (ModelLoader.setCustomModelResourceLocation). Creative tab: the mod's
 * "mutantbeasts" tab object; the items are listed in Search, as Forge lists every tab's items there. */
(function (M) {
  "use strict";
  var MH = M.MH, R = M.R;
  var ITEM_NAMES = ["chemical_x", "creeper_minion_tracker", "creeper_shard", "endersoul_hand", "hulk_hammer", "mutant_skeleton_arms",
    "mutant_skeleton_limb", "mutant_skeleton_shoulder_pad", "mutant_skeleton_rib", "mutant_skeleton_rib_cage", "mutant_skeleton_pelvis",
    "mutant_skeleton_skull", "mutant_skeleton_chestplate", "mutant_skeleton_leggings", "mutant_skeleton_boots"];
  M.ITEM_NAMES = ITEM_NAMES;
  M.ITEM_BASE_ID = 4000;
  M.ITEMS_MB = {};

  function upper(n) { return n.toUpperCase(); }
  function ar(type, stack) { var r = new DL(); DT(r, type, stack); return r; }      // new ActionResult(type, stack)
  function slot(name) { Dt(); var v = Lan.data; for (var i = 0; i < v.length; i++) if (M.ustr(v[i].FM) === name) return v[i]; throw new Error("slot " + name); }
  function enumName(e) { return e === null || e === undefined ? null : M.ustr(e.FM); }
  M.enumName = enumName;
  function isCreative(p) { return !!p.isCreative(); }
  function setCooldown(player, item, ticks) { Cqz(Dk6(player), item, ticks); }  // getCooldownTracker().setCooldown
  // Item.getItemAttributeModifiers + the weapon modifiers (ATTACK_DAMAGE_MODIFIER / ATTACK_SPEED_MODIFIER, "Weapon modifier")
  function weaponModifiers(item, slotIn, damage, speed) {
    var multimap = CpT(item, slotIn);
    if (slotIn === slot("MAINHAND")) {
      Dh(); FM();
      var a = new H5(); Dg9(a, Kwo, M.JS("Weapon modifier"), damage, 0); Cbm(multimap, Kut.xK, a);
      var b = new H5(); Dg9(b, Kwp, M.JS("Weapon modifier"), speed, 0); Cbm(multimap, Kuu.xK, b);
    }
    return multimap;
  }
  // EndersoulHandItem.rayTrace(player, distance)
  function handRayTrace(player, dist) {
    var v = DWa(player, 1.0), l = player.bWS(1.0);
    var end = Mc(v, l.bh * dist, l.bq * dist, l.bi * dist);
    return DJx(player.world, v, end, 0, 1, 0);
  }
  function canCarry(world, pos, state) { return !!Dd9(state) && DM1(state, world, pos) > -1.0; }   // isOpaqueCube && hardness > -1
  var TEXT_UNABLE = null;
  // MutantSkeletonArmorItem.onArmorTick (Forge runs it on both sides; the client's own copy keeps its movement prediction)
  function armorTick(item, world, player, stack) {
    var type = enumName(item.a6G);                            // ItemArmor.armorType
    if (type === "LEGS" && !player.isPotionActive(M.potion("speed")) && M.CFG.ITEMS.mutantSkeletonLeggingsSpeed) {
      player.dv$(Lcs(M.potion("speed"), 1, 1, 0, 0));
    }
    if (type === "FEET" && !player.isPotionActive(M.potion("jump_boost")) && M.CFG.ITEMS.mutantSkeletonBootsJumpBoost) {
      player.dv$(Lcs(M.potion("jump_boost"), 1, CBf(player) ? 1 : 0, 0, 0));      // isSprinting
    }
  }

  M.defineItems = function () {
    var CFG = M.CFG, SND = M.SND, ENUM = M.ENUM, T = M.T;
    var Item = Cl, ItemArmor = F6;
    // each override falls back to the vanilla method of its base class (Item or ItemArmor) while "items" is off or after it threw
    function defineItem(name, extend, virtuals) {
      var base = (extend || Item).prototype, out = {};
      for (var v in virtuals) out[v] = M.guardVirtual("items", v, virtuals[v], (function (f) { return function () { return f.apply(this, arguments); }; })(base[v]));
      return M.defineClass({ name: "chumbanotz.mutantbeasts.item." + name, extend: extend || Item, virtuals: out });
    }

    // ---------------- ChemicalXItem.java
    var ChemicalXItem = defineItem("ChemicalXItem", Item, {
      cMA: function () { return ENUM.Rarity.EPIC; },
      o9: function (worldIn, playerIn, handIn) {
        var itemstack = playerIn.getHeldItem(handIn);
        M.W.playSound(worldIn, null, playerIn.posX, playerIn.posY, playerIn.posZ, SND.vanilla("entity.splash_potion.throw"), ENUM.SoundCategory.PLAYERS, 0.5, 0.4 / (R.nextFloat(KN9) * 0.4 + 0.8));
        if (!isCreative(playerIn)) M.stack.shrink(itemstack, 1);
        return ar(ENUM.ActionResult.SUCCESS, itemstack);
      }
    });
    // ---------------- CreeperShardItem.java
    var CreeperShardItem = defineItem("CreeperShardItem", Item, {
      bkt: function (stack) { return GvX(this, stack) || M.stack.getItemDamage(stack) <= 0 ? 1 : 0; },
      cMA: function () { return ENUM.Rarity.UNCOMMON; },
      cPY: function (stack, target, attacker) {
        var player = attacker, damage = M.stack.getItemDamage(stack);
        if (damage > 0) {
          M.stack.setItemDamage(stack, damage - 1);
          if (!isCreative(player) && R.nextInt(player.getRNG(), 4) === 0) {
            player.dv$(Lcq(M.POTIONS.POISON, 80 + R.nextInt(player.getRNG(), 40)));   // addPotionEffect(new PotionEffect(POISON, n))
          }
        }
        target.knockBack(player, 0.9, player.posX - target.posX, player.posZ - target.posZ);
        return 1;
      },
      o9: function (worldIn, playerIn, handIn) {
        var stack = playerIn.getHeldItem(handIn), maxDmg = M.stack.getMaxDamage(stack), dmg = M.stack.getItemDamage(stack);
        if (!isCreative(playerIn)) M.stack.setItemDamage(stack, maxDmg);
        playerIn.swingArm(handIn);
        setCooldown(playerIn, this, (maxDmg - dmg) * 2);
        return ar(ENUM.ActionResult.SUCCESS, stack);
      },
      b6s: function (slotIn) { return weaponModifiers(this, slotIn, 2.0, -2.0); }
    });
    // ---------------- EndersoulHandItem.java
    var EndersoulHandItem = defineItem("EndersoulHandItem", Item, {
      cMA: function () { return ENUM.Rarity.EPIC; },
      cPY: function (stack, target, attacker) { M.stack.damageItem(stack, 1, attacker); return 1; },
      bEP: function () { return CFG.ITEMS.endersoulHandEnchantability; },
      oq: function (player, worldIn, pos, hand, facing) {
        var blockState = M.W.getBlockState(worldIn, pos);
        if (player.isSneaking()) return ENUM.ActionResult.PASS;
        if (!canCarry(worldIn, pos, blockState)) return ENUM.ActionResult.FAIL;
        // worldIn.canMineBlockBody(player, pos): true on a client world
        if (!EIK(player, pos, facing, player.getHeldItem(hand))) return ENUM.ActionResult.FAIL;      // canPlayerEdit
        if (CZp(worldIn, pos) !== null) return ENUM.ActionResult.FAIL;                                // getTileEntity
        return ENUM.ActionResult.SUCCESS;                     // !world.isRemote: the throwable block and setBlockToAir
      },
      o9: function (worldIn, playerIn, handIn) {
        var stack = playerIn.getHeldItem(handIn);
        if (!playerIn.isSneaking() || !CFG.ITEMS.endersoulHandTeleports) return ar(ENUM.ActionResult.PASS, stack);
        var result = handRayTrace(playerIn, CFG.ITEMS.endersoulHandTeleportationRadius);
        if (result === null || enumName(result.kE) !== "BLOCK") {
          if (TEXT_UNABLE === null) { TEXT_UNABLE = new BM(); B9y(TEXT_UNABLE, M.JS("Unable to teleport to location"), G(D, 0)); }
          playerIn.ebQ(TEXT_UNABLE, 1);                       // sendStatusMessage(component, true)
          return ar(ENUM.ActionResult.FAIL, stack);
        }
        playerIn.fallDistance = 0.0;
        playerIn.swingArm(handIn);
        return ar(ENUM.ActionResult.SUCCESS, stack);
      },
      b6s: function (slotIn) { return weaponModifiers(this, slotIn, CFG.ITEMS.endersoulHandDamage - 1.0, CFG.ITEMS.endersoulHandAttackSpeed - 4.0); }
    });
    // ---------------- HulkHammerItem.java
    var HulkHammerItem = defineItem("HulkHammerItem", Item, {
      cMA: function () { return ENUM.Rarity.UNCOMMON; },
      cPY: function (stack, target, attacker) { M.stack.damageItem(stack, 1, attacker); return 1; },
      cCw: function (stack, worldIn, state, pos, entityLiving) {
        if (DM1(state, worldIn, pos) !== 0.0) M.stack.damageItem(stack, 2, entityLiving);
        return 1;
      },
      bEP: function () { return CFG.ITEMS.hulkHammerEnchantability; },
      o9: function (worldIn, playerIn, handIn) {
        var heldItemStack = playerIn.getHeldItem(handIn);
        var result = CCT(this, worldIn, playerIn, 1);          // Item.rayTrace(world, player, true)
        if (result === null || enumName(result.kE) !== "BLOCK" || enumName(result.q3) !== "UP") return ar(ENUM.ActionResult.PASS, heldItemStack);
        var pos = playerIn.getPosition();
        M.W.playSound(worldIn, playerIn, pos.m + 0.5, pos.i + 0.5, pos.l + 0.5, SND.vanilla("entity.generic.explode"), ENUM.SoundCategory.BLOCKS, 0.8, 0.8 + R.nextFloat(playerIn.getRNG()) * 0.4);
        if (CFG.ITEMS.hulkHammerCooldown > 0) setCooldown(playerIn, this, CFG.ITEMS.hulkHammerCooldown);
        playerIn.swingArm(handIn);
        M.stack.damageItem(heldItemStack, 1, playerIn);
        return ar(ENUM.ActionResult.SUCCESS, heldItemStack);
      },
      b6s: function (slotIn) { return weaponModifiers(this, slotIn, CFG.ITEMS.hulkHammerDamage - 1.0, CFG.ITEMS.hulkHammerAttackSpeed - 4.0); }
    });
    // ---------------- MutantSkeletonArmorItem.java
    var MutantSkeletonArmorItem = defineItem("MutantSkeletonArmorItem", ItemArmor, {
      cMA: function () { return ENUM.Rarity.UNCOMMON; },
      d$n: function (stack, worldIn, entityIn, itemSlot) {    // onUpdate: Forge's onArmorTick for worn armour (the client half)
        if (entityIn instanceof Cb && itemSlot >= 0 && itemSlot < 4 && M.listGet(entityIn.bx.rJ, itemSlot) === stack) armorTick(this, worldIn, entityIn, stack);
      }
    });
    // ArmorMaterial "mutant_skeleton" (EnumHelper.addArmorMaterial). Ordinal 2 (IRON's): this client's armour layer switches
    // over the material ordinal and draws only LEATHER/CHAIN/IRON/GOLD/DIAMOND (Forge draws every material as these).
    var I = CFG.ITEMS;
    ADR();
    var MATERIAL = LbF(M.JS("JASPR_MUTANT_SKELETON"), 2, M.JS("jaspr_mutants_mutant_skeleton"), I.mutantSkeletonArmorDurability,
      M.intArrayOf([I.mutantSkeletonArmorProtectionBoots, I.mutantSkeletonArmorProtectionLeggings, I.mutantSkeletonArmorProtectionChestplate, I.mutantSkeletonArmorProtectionHelmet]),
      I.mutantSkeletonArmorEnchantability, SND.vanilla("entity.skeleton.step"), I.mutantSkeletonArmorToughness);

    // ---------------- RegistryHandler.registerItems
    FM();
    var TAB = new GQ();                                        // MutantBeasts.CREATIVE_TAB ("mutantbeasts")
    M.CREATIVE_TAB = TAB;
    function plain(cls) { var it = new cls(); Bm$(it); return it; }
    function armor(type) { var it = new MutantSkeletonArmorItem(); BR$(it, MATERIAL, 0, slot(type)); return it; }
    var made = {
      chemical_x: plain(ChemicalXItem), creeper_minion_tracker: plain(Item), creeper_shard: plain(CreeperShardItem),
      endersoul_hand: plain(EndersoulHandItem), hulk_hammer: plain(HulkHammerItem), mutant_skeleton_arms: plain(Item),
      mutant_skeleton_limb: plain(Item), mutant_skeleton_shoulder_pad: plain(Item), mutant_skeleton_rib: plain(Item),
      mutant_skeleton_rib_cage: plain(Item), mutant_skeleton_pelvis: plain(Item), mutant_skeleton_skull: armor("HEAD"),
      mutant_skeleton_chestplate: armor("CHEST"), mutant_skeleton_leggings: armor("LEGS"), mutant_skeleton_boots: armor("FEET")
    };
    made.chemical_x.i8 = 1;
    made.creeper_minion_tracker.i8 = 1;
    made.creeper_shard.i8 = 1; Fe4(made.creeper_shard, I.creeperShardCharges);
    made.endersoul_hand.i8 = 1; Fe4(made.endersoul_hand, I.endersoulHandDurability);
    made.hulk_hammer.i8 = 1; Fe4(made.hulk_hammer, I.hulkHammerDurability);
    M.ITEM_CLASSES = { ChemicalXItem: ChemicalXItem, CreeperShardItem: CreeperShardItem, EndersoulHandItem: EndersoulHandItem, HulkHammerItem: HulkHammerItem, MutantSkeletonArmorItem: MutantSkeletonArmorItem };
    for (var i = 0; i < ITEM_NAMES.length; i++) {
      var n = ITEM_NAMES[i], it = made[n];
      EfG(it, M.jstr("mutantbeasts." + n));                  // setTranslationKey("mutantbeasts." + path)
      it.hq = TAB;                                            // setCreativeTab(MutantBeasts.CREATIVE_TAB)
      it.$jmVariants = M.variantList(n === "endersoul_hand" ? ["jaspr_mutants/endersoul_hand_model", "jaspr_mutants/endersoul_hand_gui"] : ["jaspr_mutants/" + n]);
      M.ITEMS_MB[upper(n)] = it;
    }
  };
  M.itemTranslationKey = function (item) { return item.bPx(); };     // Item.getTranslationKey (getUnlocalizedName): "item." + name
  M.variantList = function (names) { var l = Bq(); for (var i = 0; i < names.length; i++) Y(l, M.jstr("minecraft:" + names[i])); return l; };
  M.registerItems = function () {
    FM();
    for (var i = 0; i < ITEM_NAMES.length; i++) DKx(HEO, M.ITEM_BASE_ID + i, M.rl("mutantbeasts:" + ITEM_NAMES[i]), M.ITEMS_MB[upper(ITEM_NAMES[i])]);
  };
  // ClientEventHandler.onModelRegistry: ModelLoader.setCustomModelResourceLocation(item, 0, <variant>, "inventory")
  M.registerItemModels = function (renderItem) {
    for (var i = 0; i < ITEM_NAMES.length; i++) {
      var n = ITEM_NAMES[i];
      F3D(renderItem, M.ITEMS_MB[upper(n)], 0, M.jstr("minecraft:jaspr_mutants/" + (n === "endersoul_hand" ? "endersoul_hand_model" : n)));
    }
    return ITEM_NAMES.length;
  };

  // ---------------------------------------------------------------- MBTileEntityItemStackRenderer + EndersoulHandModel.Baked
  var lastTransform = null, handModel = null, guiModel = null;
  M.noteTransform = function (type) { lastTransform = type; };    // ItemCameraTransforms.applyTransform(type)
  M.teisr = function (stack) {
    if (M.itemOf(stack) !== M.ITEMS_MB.ENDERSOUL_HAND) return false;
    M.stats.teisr++;
    var t = enumName(lastTransform);
    if (t === "GUI" || t === "GROUND" || t === "FIXED") {
      // EndersoulHandModel.Baked.handlePerspective: these perspectives use the flat endersoul_hand_gui model and its transforms
      if (guiModel === null) guiModel = D8D(HEH.u4.we.Nd, LvB(M.jstr("minecraft:jaspr_mutants/endersoul_hand_gui"), M.JS("inventory")));
      DPm(0.5, 0.5, 0.5);
      FtF(guiModel.w9(), lastTransform);
      FVg(HEH.u4, stack, guiModel);                           // RenderItem.renderItem(stack, model): translate(-0.5) and quads
      return true;
    }
    if (!M.texturesReady()) return true;
    if (handModel === null) handModel = M.MODELS.EndersoulHandModel.create();
    Eu0();
    var player = HEH.v;
    var pt = HEH.cp ? HEH.buv : HEH.J4.UM;
    M.endersoulRender(player, M.entityTexture("endersoul_hand"), 0.0, 0.0, player.ticksExisted + pt, 0.0, 0.0, 0.0, handModel, 1.0);
    ECi();
    return true;
  };

  // ---------------------------------------------------------------- MutantSkeletonArmorItem.getArmorModel (Forge hook)
  var skullModel = null;
  M.armorModel = function (entity, stack, slotIn, model) {
    if (M.itemOf(stack) === M.ITEMS_MB.MUTANT_SKELETON_SKULL && enumName(slotIn) === "HEAD") {
      if (skullModel === null) skullModel = M.MODELS.MutantSkeletonArmorModel.create();     // ClientProxy.MUTANT_SKELETON_ARMOR_MODEL
      M.stats.armor++;
      return skullModel;
    }
    return model;
  };
})(JasprMutants);
