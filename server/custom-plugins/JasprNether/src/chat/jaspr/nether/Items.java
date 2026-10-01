package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * BetterNether / NetherEx items as vanilla items carrying NBT {JasprNether:{id:"..."}}, a name, lore and (for special
 * items) a hidden Unbreaking I glint. Bases are chosen so the stand-in is harmless if used as its vanilla self.
 */
final class Items {
    private Items() {}

    static final class Def {
        final String id, name, mod; final Material mat; final short data; final boolean glint; final String[] lore;
        final int armor; final double toughness; final Color colour; final double attack, speed; final int efficiency;
        final List<Object[]> mods = new ArrayList<>();
        final Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new LinkedHashMap<>();
        boolean unbreakable;
        /** An extra attribute modifier (op 0 add, 1 multiply base) in a slot (mainhand, offhand, head, chest, legs, feet). */
        Def mod(String attr, double amount, int op, String slot) { mods.add(new Object[]{attr, amount, op, slot}); return this; }
        Def ench(org.bukkit.enchantments.Enchantment e, int level) { enchants.put(e, level); return this; }
        Def unbreakable() { unbreakable = true; return this; }
        Def(String id, Material mat, int data, String name, String mod, boolean glint, String... lore) {
            this(id, mat, data, name, mod, glint, 0, 0, null, 0, 0, 0, lore);
        }
        Def(String id, Material mat, int data, String name, String mod, boolean glint, int armor, double toughness, Color colour,
            double attack, double speed, int efficiency, String... lore) {
            this.id = id; this.mat = mat; this.data = (short) data; this.name = name; this.mod = mod; this.glint = glint;
            this.lore = lore; this.armor = armor; this.toughness = toughness; this.colour = colour; this.attack = attack; this.speed = speed;
            this.efficiency = efficiency;
        }
    }

    static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static void def(Def d) { DEFS.put(d.id, d); }

    static final Color HELLFORGED = Color.fromRGB(0x6A1208), SOULWEAVE = Color.fromRGB(0x3B2A5C), VOIDSTEP = Color.fromRGB(0x1E1030);
    static final String JC = "JasperCraft";
    static final String[] LORD_IDS = {"deathwing", "ignareth", "pit_lord", "ashen_wither", "cursed_king", "dread_sorcerer", "voidborn",
        "bone_colossus", "crimson_tyrant", "blood_count"};
    static final String[] LORD_NAMES = {"Deathwing", "Ignareth the Magma Wyrm", "the Pit Lord", "the Ashen Wither", "the Cursed King",
        "the Dread Sorcerer", "the Voidborn", "the Bone Colossus", "the Crimson Tyrant", "the Blood Count"};
    private static final Material[] DISCS = {Material.RECORD_3, Material.RECORD_4, Material.RECORD_5, Material.RECORD_6, Material.RECORD_7,
        Material.RECORD_8, Material.RECORD_9, Material.RECORD_10, Material.RECORD_11, Material.RECORD_12};

    static final Color WITHER_BONE = Color.fromRGB(0x3B3B3B), ORANGE_HIDE = Color.fromRGB(0xE0701B), BLACK_HIDE = Color.fromRGB(0x1A1A1A);

    static {
        String nx = "NetherEx", bn = "BetterNether";
        def(new Def("amethyst_crystal", Material.CHORUS_FRUIT_POPPED, 0, "Nether Amethyst Crystal", nx, false, "Pigtificate currency"));
        def(new Def("amethyst_block", Material.PURPUR_BLOCK, 0, "Nether Amethyst Block", nx, true, "Crowns a Gold Golem:", "place it on a T of gold blocks"));
        def(new Def("rime_crystal", Material.SUGAR, 0, "Nether Rime Crystal", nx, true, "Brew into an Awkward Potion: Freezing"));
        def(new Def("rime_block", Material.SEA_LANTERN, 0, "Nether Rime Block", nx, true, "Freezes water, lava and nearby mobs"));
        def(new Def("wither_bone", Material.BONE, 0, "Wither Bone", nx, false));
        def(new Def("wither_dust", Material.INK_SACK, 8, "Wither Dust", nx, false, "Use on a mushroom to grow an Elder Mushroom"));
        def(new Def("blazed_wither_bone", Material.BONE, 0, "Blazed Wither Bone", nx, true));
        def(new Def("frosted_wither_bone", Material.BONE, 0, "Frosted Wither Bone", nx, true));
        def(new Def("frost_rod", Material.STICK, 0, "Frost Rod", nx, true));
        def(new Def("frost_powder", Material.CLAY_BALL, 0, "Frost Powder", nx, false));
        def(new Def("orange_salamander_hide", Material.LEATHER, 0, "Orange Salamander Hide", nx, false));
        def(new Def("black_salamander_hide", Material.LEATHER, 0, "Black Salamander Hide", nx, false));
        def(new Def("spore", Material.SULPHUR, 0, "Spore", nx, false, "Brew into an Awkward Potion: Dispersal"));
        def(new Def("frost_fang", Material.RABBIT_FOOT, 0, "Frost Fang", nx, false, "Brew into an Awkward Potion: Frigid Health"));
        def(new Def("ghast_queen_tear", Material.GHAST_TEAR, 0, "Ghast Queen Tears", nx, true, "Cures a weakened Zombie Pigman", "into a Pigtificate"));
        def(new Def("ghast_meat_raw", Material.SPIDER_EYE, 0, "Raw Ghast Meat", nx, false, "Levitation II (5s)", "Brew into an Awkward Potion: Sorrow"));
        def(new Def("ghast_meat_cooked", Material.COOKED_MUTTON, 0, "Cooked Ghast Meat", nx, false, "Levitation II (10s)"));
        def(new Def("congealed_magma_cream", Material.BEETROOT, 0, "Congealed Magma Cream", nx, false, "Fire Resistance II (10s)"));
        def(new Def("enoki_mushroom", Material.CARROT_ITEM, 0, "Enoki Mushroom", nx, false));
        def(new Def("brown_elder_mushroom", Material.BREAD, 0, "Brown Elder Mushroom", nx, false, "Eat: fills hunger but hurts (4-8)"));
        def(new Def("red_elder_mushroom", Material.COOKIE, 0, "Red Elder Mushroom", nx, false, "Eat: full health, empties hunger"));
        def(new Def("dull_mirror", Material.WATCH, 0, "Dull Mirror", nx, true, "Sneak + right-click: anchor your respawn here", "Kept on death; 5 anchored respawns"));
        def(new Def("rime_and_steel", Material.FLINT_AND_STEEL, 0, "Rime and Steel", nx, true, "Lights blue fire"));
        String[] handles = {"withered", "blazed", "frosted"};
        String[] handleNames = {"Withered", "Blazed", "Frosted"};
        for (int h = 0; h < 3; h++) {
            def(new Def(handles[h] + "_amedian_sword", Material.DIAMOND_SWORD, 0, handleNames[h] + " Amedian Sword", nx, false, 0, 0, null, 5, 1.6, 0,
                h == 0 ? "Double damage to spores" : "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_pickaxe", Material.DIAMOND_PICKAXE, 0, handleNames[h] + " Amedian Pickaxe", nx, false, 0, 0, null, 3, 1.2, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_shovel", Material.DIAMOND_SPADE, 0, handleNames[h] + " Amedian Shovel", nx, false, 0, 0, null, 3.5, 1.0, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_axe", Material.DIAMOND_AXE, 0, handleNames[h] + " Amedian Axe", nx, false, 0, 0, null, 10, 1.2, 1, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_hoe", Material.DIAMOND_HOE, 0, handleNames[h] + " Amedian Hoe", nx, false, 0, 0, null, 1, 2.0, 0, "Amedian: harvest 4, 2250 uses"));
            def(new Def(handles[h] + "_amedian_hammer", Material.DIAMOND_PICKAXE, 0, handleNames[h] + " Amedian Hammer", nx, true, 0, 0, null, 6, 0.9, 0, "Mines 3x3", "Amedian: harvest 4, 2250 uses"));
        }
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        int[] bone = {2, 4, 3, 2}, hide = {2, 5, 4, 2};
        String[] slotNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int s = 0; s < 4; s++) {
            def(new Def("wither_bone_" + slots[s], leather[s], 0, "Wither Bone " + slotNames[s], nx, false, bone[s], 0.5, WITHER_BONE, 0, 0, 0, "Full set: skeletons ignore you"));
            def(new Def("orange_salamander_hide_" + slots[s], leather[s], 0, "Orange Salamander Hide " + slotNames[s], nx, false, hide[s], 1.0, ORANGE_HIDE, 0, 0, 0, "Full set: fire and lava immunity"));
            def(new Def("black_salamander_hide_" + slots[s], leather[s], 0, "Black Salamander Hide " + slotNames[s], nx, false, hide[s], 1.0, BLACK_HIDE, 0, 0, 0));
        }
        def(new Def("potion_freezing", Material.POTION, 0, "Potion of Freezing", nx, false, "Frozen (0:30)"));
        def(new Def("potion_frigid_health", Material.POTION, 0, "Potion of Frigid Health", nx, false, "Frostbitten (0:30)"));
        def(new Def("potion_dispersal", Material.POTION, 0, "Potion of Dispersal", nx, false, "Infested (0:30)"));
        def(new Def("potion_sorrow", Material.POTION, 0, "Potion of Sorrow", nx, false, "Crying (0:30)", "Fills an Urn of Sorrow"));
        // BetterNether
        def(new Def("cincinnasite", Material.INK_SACK, 11, "Cincinnasite", bn, false));
        def(new Def("cincinnasite_block", Material.SANDSTONE, 2, "Cincinnasite Block", bn, false, "Smelt into Forged Cincinnasite"));
        def(new Def("cincinnasite_forged", Material.STAINED_CLAY, 4, "Forged Cincinnasite", bn, false));
        def(new Def("cincinnasite_pickaxe", Material.IRON_PICKAXE, 0, "Cincinnasite Pickaxe", bn, false, 0, 0, null, 4, 1.2, 4, "512 uses, very fast"));
        def(new Def("cincinnasite_axe", Material.IRON_AXE, 0, "Cincinnasite Axe", bn, false, 0, 0, null, 9, 0.9, 4, "512 uses, very fast"));
        def(new Def("cincinnasite_pickaxe_diamond", Material.DIAMOND_PICKAXE, 0, "Cincinnasite-Diamond Pickaxe", bn, false, 0, 0, null, 5, 1.2, 2, "2048 uses"));
        def(new Def("cincinnasite_axe_diamond", Material.DIAMOND_AXE, 0, "Cincinnasite-Diamond Axe", bn, false, 0, 0, null, 9, 1.0, 2, "2048 uses"));
        def(new Def("black_apple", Material.APPLE, 0, "Black Apple", bn, false, "Regeneration IV (2s)"));
        def(new Def("nether_reed", Material.SUGAR_CANE, 0, "Nether Reed", bn, false));
        def(new Def("stalagnate", Material.LOG, 2, "Stalagnate", bn, false));
        def(new Def("stalagnate_bowl", Material.BOWL, 0, "Stalagnate Bowl", bn, false));
        def(new Def("stalagnate_bowl_wart", Material.BEETROOT_SOUP, 0, "Wart Soup", bn, false));
        def(new Def("stalagnate_bowl_mushroom", Material.MUSHROOM_SOUP, 0, "Mushroom Stew", bn, false));
        def(new Def("stalagnate_bowl_apple", Material.RABBIT_STEW, 0, "Black Apple Stew", bn, false));
        glm();
        colossal();
    }

    // ---- the GLM strongholds' loot (owner, 2026-09-29: "a lot of custom loot that has a Nether theme ... new trinkets
    // ... All of the new items, trinkets, weapons, armor, etc., should all have a nether theme") ------------------------
    private static void glm() {
        // materials, dropped by the strongholds' creatures
        def(new Def("hellforged_shard", Material.NETHER_BRICK_ITEM, 0, "Hellforged Shard", JC, false, "Four make a Hellforged Ingot"));
        def(new Def("hellforged_ingot", Material.CLAY_BRICK, 0, "Hellforged Ingot", JC, true, "Forge Hellforged armour and blades"));
        def(new Def("ashbone", Material.BONE, 0, "Ashbone", JC, false, "From Ashbone Archers"));
        def(new Def("hellhound_fang", Material.PRISMARINE_SHARD, 0, "Hellhound Fang", JC, false, "From Hellhounds"));
        def(new Def("imp_horn", Material.FLINT, 0, "Imp Horn", JC, false, "From Cinder Imps"));
        def(new Def("soul_essence", Material.FIREWORK_CHARGE, 0, "Soul Essence", JC, true, "From Soul Wraiths", "Weave it into Soulweave armour"));
        def(new Def("molten_core", Material.MAGMA_CREAM, 0, "Molten Core", JC, true, "From Magma Hulks"));
        def(new Def("pyre_ember", Material.BLAZE_POWDER, 0, "Pyre Ember", JC, false, "From Pyre Wardens"));
        def(new Def("void_shard", Material.PRISMARINE_CRYSTALS, 0, "Void Shard", JC, true, "From Shades"));
        def(new Def("brimstone", Material.GLOWSTONE_DUST, 0, "Brimstone", JC, false, "From Brimstone Spiders"));
        def(new Def("charred_bone", Material.COAL, 1, "Charred Bone", JC, false, "From Charred Ghouls"));
        def(new Def("hex_ember", Material.INK_SACK, 5, "Hex Ember", JC, true, "From Cinder Witches"));
        // weapons
        def(new Def("infernal_blade", Material.DIAMOND_SWORD, 0, "Infernal Blade", JC, true, 0, 0, null, 8, 1.6, 0, "Sets its foes ablaze"));
        def(new Def("soulreaper_scythe", Material.DIAMOND_HOE, 0, "Soulreaper Scythe", JC, true, 0, 0, null, 9, 1.1, 0, "Heals you for a fifth of its damage"));
        def(new Def("magma_maul", Material.DIAMOND_AXE, 0, "Magma Maul", JC, true, 0, 0, null, 12, 0.8, 0, "Its blows burst in flame around the foe"));
        def(new Def("ember_bow", Material.BOW, 0, "Ember Bow", JC, false, "Burning arrows").ench(org.bukkit.enchantments.Enchantment.ARROW_FIRE, 1)
            .ench(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 2));
        // armour sets
        String[] slots = {"helmet", "chestplate", "leggings", "boots"}, slotNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        int[] hell = {3, 7, 5, 2}, soul = {2, 6, 5, 2};
        for (int i = 0; i < 4; i++) {
            def(new Def("hellforged_" + slots[i], leather[i], 0, "Hellforged " + slotNames[i], JC, false, hell[i], 2, HELLFORGED, 0, 0, 0, "Full set: four more hearts"));
            def(new Def("soulweave_" + slots[i], leather[i], 0, "Soulweave " + slotNames[i], JC, false, soul[i], 1, SOULWEAVE, 0, 0, 0, "Full set: the wither cannot touch you"));
        }
        // trinkets: carried in the off hand (their power shows in the off hand) or anywhere in the inventory
        def(new Def("brimstone_idol", Material.QUARTZ, 0, "Brimstone Idol", JC, true, "In the off hand: +2 attack damage").mod("generic.attackDamage", 2, 0, "offhand"));
        def(new Def("heart_of_cinders", Material.FERMENTED_SPIDER_EYE, 0, "Heart of Cinders", JC, true, "In the off hand: two more hearts").mod("generic.maxHealth", 4, 0, "offhand"));
        def(new Def("hellhound_collar", Material.RABBIT_HIDE, 0, "Hellhound Collar", JC, true, "In the off hand: run 15% faster").mod("generic.movementSpeed", 0.15, 1, "offhand"));
        def(new Def("obsidian_aegis", Material.SHIELD, 0, "Obsidian Aegis", JC, true, "A shield of the Nether's black glass").mod("generic.armor", 3, 0, "offhand")
            .mod("generic.armorToughness", 2, 0, "offhand"));
        def(new Def("ember_heart", Material.SPECKLED_MELON, 0, "Ember Heart", JC, true, "In the off hand: fire cannot burn you"));
        def(new Def("wither_ward", Material.INK_SACK, 0, "Wither Ward", JC, true, "Carried: the wither cannot touch you"));
        def(new Def("magma_band", Material.GOLD_NUGGET, 0, "Magma Band", JC, true, "Carried: magma cannot burn your feet", "and whoever strikes you catches fire"));
        def(new Def("ghastly_pendant", Material.GHAST_TEAR, 0, "Ghastly Pendant", JC, true, "Carried: when you are near death,", "it mends you (once a minute)"));
        // the Nether Lords' relics (half the time in a Lord's hoard) and sigils (to everyone who conquers one)
        def(new Def("deathwing_talon", Material.DIAMOND_SWORD, 0, "Deathwing's Talon", JC, true, 0, 0, null, 11, 1.6, 0, "Relic of Deathwing", "Burns and hurls its foes").unbreakable());
        def(new Def("wyrmfire_bow", Material.BOW, 0, "Wyrmfire Bow", JC, true, "Relic of Ignareth", "Its arrows burst in flame").unbreakable()
            .ench(org.bukkit.enchantments.Enchantment.ARROW_FIRE, 1).ench(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 3));
        def(new Def("pit_lord_cleaver", Material.DIAMOND_AXE, 0, "Pit Lord's Cleaver", JC, true, 0, 0, null, 14, 0.8, 0, "Relic of the Pit Lord", "Burns and slows its foes").unbreakable());
        def(new Def("ashen_crown", Material.GOLD_HELMET, 0, "Ashen Crown", JC, true, 4, 2, null, 0, 0, 0, "Relic of the Ashen Wither", "Worn: the wither cannot touch you").unbreakable());
        def(new Def("cursed_katana", Material.DIAMOND_SWORD, 0, "Cursed Katana", JC, true, 0, 0, null, 10, 1.9, 0, "Relic of the Cursed King", "Withers its foes").unbreakable());
        def(new Def("dread_staff", Material.BLAZE_ROD, 0, "Dread Staff", JC, true, "Relic of the Dread Sorcerer", "Right-click: hurl a fireball"));
        def(new Def("voidstep_boots", Material.LEATHER_BOOTS, 0, "Voidstep Boots", JC, true, 3, 2, VOIDSTEP, 0, 0, 0, "Relic of the Voidborn", "Worn: no fall damage, 10% faster")
            .mod("generic.movementSpeed", 0.1, 1, "feet").unbreakable());
        def(new Def("colossus_maul", Material.DIAMOND_SPADE, 0, "Colossus Maul", JC, true, 0, 0, null, 15, 0.7, 0, "Relic of the Bone Colossus", "Hurls its foes into the air").unbreakable());
        def(new Def("tyrant_heart", Material.INK_SACK, 1, "Tyrant's Heart", JC, true, "Relic of the Crimson Tyrant", "In the off hand: fire cannot burn you,", "and you take less harm"));
        def(new Def("bloodfang_dagger", Material.IRON_SWORD, 0, "Bloodfang Dagger", JC, true, 0, 0, null, 7, 2.2, 0, "Relic of the Blood Count", "Drinks a quarter of the harm it deals").unbreakable());
        for (int i = 0; i < LORD_IDS.length; i++)
            def(new Def("sigil_" + LORD_IDS[i], DISCS[i], 0, "Sigil of " + Character.toUpperCase(LORD_NAMES[i].charAt(0)) + LORD_NAMES[i].substring(1), JC, true,
                "Proof that " + LORD_NAMES[i] + " fell to you", "Three Lords conquered open the Urn of Sorrow"));
    }

    // ---- the colossal structures' and the Catacombs' treasure (owner, 2026-10-01: "Add loot ... and mini-bosses") ------------
    static final Color PHARAOH = Color.fromRGB(0xD9B44A), EMBER_GUARD = Color.fromRGB(0x8E1111), DEEPWARDEN = Color.fromRGB(0x45454F);
    private static void colossal() {
        // the keys the champions keep (never used up by the seals they open)
        def(new Def("canopic_jar_imsety", Material.SHULKER_SHELL, 0, "Canopic Jar of Imsety", JC, true, "Kept by Vizier Hekkat", "One of four for the Canopic Seal", "of the Great Pyramid"));
        def(new Def("canopic_jar_hapy", Material.SHULKER_SHELL, 0, "Canopic Jar of Hapy", JC, true, "Kept in the Hall of Stars", "One of four for the Canopic Seal", "of the Great Pyramid"));
        def(new Def("canopic_jar_duamutef", Material.SHULKER_SHELL, 0, "Canopic Jar of Duamutef", JC, true, "Kept by the Sphinx Sentinel", "One of four for the Canopic Seal", "of the Great Pyramid"));
        def(new Def("canopic_jar_qebehsenuef", Material.SHULKER_SHELL, 0, "Canopic Jar of Qebehsenuef", JC, true, "Kept by the Scarab Matriarch", "One of four for the Canopic Seal", "of the Great Pyramid"));
        def(new Def("sun_seal", Material.IRON_NUGGET, 0, "Seal of the Sun", JC, true, "Kept by the High Fire Sage", "One of three for the Throne Gate", "of the Caldera Citadel"));
        def(new Def("admiral_seal", Material.IRON_NUGGET, 0, "Seal of the Admiral", JC, true, "Kept by the Blazing Admiral", "One of three for the Throne Gate", "of the Caldera Citadel"));
        def(new Def("warden_seal", Material.IRON_NUGGET, 0, "Seal of the Warden", JC, true, "Kept by the Warden of the Boiling Keep", "One of three for the Throne Gate", "of the Caldera Citadel"));
        def(new Def("warden_key_gaol", Material.BONE, 0, "Key of the Gaol", JC, true, "Kept by the Gaoler", "One of four for the Heart's gates", "in the Endless Catacombs"));
        def(new Def("warden_key_ossuary", Material.BONE, 0, "Key of the Bone Harrow", JC, true, "Kept by the Bone Harrower", "One of four for the Heart's gates", "in the Endless Catacombs"));
        def(new Def("warden_key_gallery", Material.BONE, 0, "Key of the Weeping Gallery", JC, true, "Kept by the Weeping Shade", "One of four for the Heart's gates", "in the Endless Catacombs"));
        def(new Def("warden_key_pits", Material.BONE, 0, "Key of the Rot Pits", JC, true, "Kept by the Rot Mother", "One of four for the Heart's gates", "in the Endless Catacombs"));
        def(new Def("hollow_reliquary", Material.SHULKER_SHELL, 0, "Hollow Reliquary", JC, true, "A puzzle's prize from the Catacombs", "Right-click: open it"));
        // weapons and trinkets
        def(new Def("khopesh", Material.DIAMOND_SWORD, 0, "Khopesh of the Necropolis", JC, true, 0, 0, null, 8, 1.6, 0, "Its blows poison"));
        def(new Def("fire_nation_dao", Material.DIAMOND_SWORD, 0, "Dao of the Fire Nation", JC, true, 0, 0, null, 8, 1.8, 0, "Burns its foes and quickens you"));
        def(new Def("bone_reaver", Material.DIAMOND_AXE, 0, "Bone Reaver", JC, true, 0, 0, null, 11, 0.9, 0, "Its blows weaken"));
        def(new Def("scarab_amulet", Material.GOLD_NUGGET, 0, "Scarab Amulet", JC, true, "In the off hand: poison cannot touch you"));
        def(new Def("phoenix_feather", Material.FEATHER, 0, "Phoenix Feather", JC, true, "Carried: when you are near death,", "you rise in flame (once in five minutes)"));
        // armour sets
        String[] slots = {"helmet", "chestplate", "leggings", "boots"}, slotNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        int[] ph = {3, 7, 5, 3}, eg = {3, 7, 6, 3}, dw = {3, 8, 6, 3};
        for (int i = 0; i < 4; i++) {
            def(new Def("pharaoh_" + slots[i], leather[i], 0, "Pharaoh's " + slotNames[i], JC, false, ph[i], 2, PHARAOH, 0, 0, 0, "Full set: no hunger, and no sand blinds you"));
            def(new Def("ember_guard_" + slots[i], leather[i], 0, "Ember Guard " + slotNames[i], JC, false, eg[i], 2, EMBER_GUARD, 0, 0, 0, "Full set: fire cannot burn you"));
            def(new Def("deepwarden_" + slots[i], leather[i], 0, "Deepwarden " + slotNames[i], JC, false, dw[i], 3, DEEPWARDEN, 0, 0, 0, "Full set: the wither and poison cannot touch you"));
        }
        // the colossal Lords' relics (always in their hoard) and sigils
        def(new Def("pharaoh_crook", Material.GOLD_HOE, 0, "Crook of the Sunless Pharaoh", JC, true, 0, 0, null, 9, 1.4, 0, "Relic of the Sunless Pharaoh", "Its blows blind and slow").unbreakable());
        def(new Def("sovereign_flame", Material.BLAZE_ROD, 0, "Sovereign's Flame", JC, true, "Relic of the Ember Sovereign", "Right-click: lash a whip of fire"));
        def(new Def("hollow_crown", Material.GOLD_HELMET, 0, "Hollow Crown", JC, true, 4, 2, null, 0, 0, 0, "Relic of the Hollow King", "Worn: your blows drink life,", "and the wither cannot touch you").unbreakable());
        def(new Def("sigil_sunless_pharaoh", Material.GOLD_RECORD, 0, "Sigil of the Sunless Pharaoh", JC, true, "Proof that the Sunless Pharaoh fell to you", "Three Lords conquered open the Urn of Sorrow"));
        def(new Def("sigil_ember_sovereign", Material.RECORD_4, 0, "Sigil of the Ember Sovereign", JC, true, "Proof that the Ember Sovereign fell to you", "Three Lords conquered open the Urn of Sorrow"));
        def(new Def("sigil_hollow_king", Material.GREEN_RECORD, 0, "Sigil of the Hollow King", JC, true, "Proof that the Hollow King fell to you", "Three Lords conquered open the Urn of Sorrow"));
    }

    /** Items made only from JasperCraft's GLM loot: a vanilla recipe must never take them as plain materials. */
    static boolean guarded(String id) { Def d = id == null ? null : DEFS.get(id); return d != null && JC.equals(d.mod); }

    static ItemStack create(String id, int amount) {
        Def d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown item " + id);
        ItemStack s = new ItemStack(d.mat, Math.max(1, amount), d.data);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(ChatColor.RESET + (d.glint ? ChatColor.LIGHT_PURPLE.toString() : ChatColor.WHITE.toString()) + d.name);
        List<String> lore = new ArrayList<>();
        for (String l : d.lore) lore.add(ChatColor.GRAY + l);
        lore.add(ChatColor.DARK_GRAY + d.mod);
        m.setLore(lore);
        if (d.glint) { m.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        if (d.efficiency > 0) { m.addEnchant(org.bukkit.enchantments.Enchantment.DIG_SPEED, d.efficiency, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> en : d.enchants.entrySet()) m.addEnchant(en.getKey(), en.getValue(), true);
        if (!d.enchants.isEmpty() && d.glint) m.removeEnchant(org.bukkit.enchantments.Enchantment.DURABILITY);
        if (!d.enchants.isEmpty()) m.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (d.unbreakable) { m.setUnbreakable(true); m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE); }
        if (m instanceof LeatherArmorMeta && d.colour != null) ((LeatherArmorMeta) m).setColor(d.colour);
        if (d.mat == Material.POTION) m.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        s.setItemMeta(m);
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
        NBTTagCompound tag = n.hasTag() ? n.getTag() : new NBTTagCompound();
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("id", id);
        tag.set("JasprNether", mine);
        if (d.armor > 0 || d.attack > 0 || !d.mods.isEmpty()) {
            NBTTagList mods = new NBTTagList();
            String slot = slotOf(d.mat);
            if (d.armor > 0) {
                mods.add(modifier("generic.armor", d.armor, slot, 1));
                if (d.toughness > 0) mods.add(modifier("generic.armorToughness", d.toughness, slot, 2));
            } else if (d.attack > 0) {
                mods.add(modifier("generic.attackDamage", d.attack - 1, "mainhand", 3));
                mods.add(modifier("generic.attackSpeed", d.speed - 4, "mainhand", 4));
            }
            int k = 5;
            for (Object[] o : d.mods) {
                NBTTagCompound c = modifier((String) o[0], (Double) o[1], (String) o[3], k++);
                c.setInt("Operation", (Integer) o[2]);
                mods.add(c);
            }
            tag.set("AttributeModifiers", mods);
            tag.setInt("HideFlags", tag.getInt("HideFlags") | 2 | (d.glint || d.efficiency > 0 ? 1 : 0));
        }
        if (d.mat == Material.POTION) {
            tag.setString("Potion", "minecraft:awkward");
            tag.setInt("CustomPotionColor", potionColour(id));
        }
        n.setTag(tag);
        return CraftItemStack.asBukkitCopy(n);
    }

    static int potionColour(String id) {
        switch (id.replace("potion_", "")) {
            case "freezing": return (93 << 16) | (188 << 8) | 210;
            case "frigid_health": return (19 << 16) | (226 << 8) | 255;
            case "dispersal": return (142 << 16) | (96 << 8) | 40;
            default: return (103 << 16) | (62 << 8) | 124;
        }
    }

    /** A potion of one of the NetherEx effects in drinkable, splash or lingering form. */
    static ItemStack potion(String effect, Material form) {
        ItemStack s = create("potion_" + effect, 1);
        if (form != Material.POTION) {
            s.setType(form);
            ItemMeta m = s.getItemMeta();
            String prefix = form == Material.SPLASH_POTION ? "Splash " : "Lingering ";
            m.setDisplayName(m.getDisplayName().replace("Potion of", prefix + "Potion of"));
            s.setItemMeta(m);
        }
        return s;
    }

    private static String slotOf(Material m) {
        String n = m.name();
        if (n.endsWith("HELMET")) return "head";
        if (n.endsWith("CHESTPLATE")) return "chest";
        if (n.endsWith("LEGGINGS")) return "legs";
        if (n.endsWith("BOOTS")) return "feet";
        return "mainhand";
    }

    private static NBTTagCompound modifier(String attr, double amount, String slot, int n) {
        NBTTagCompound c = new NBTTagCompound();
        c.setString("AttributeName", attr);
        c.setString("Name", "jasprnether");
        c.setDouble("Amount", amount);
        c.setInt("Operation", 0);
        c.setString("Slot", slot);
        c.setLong("UUIDMost", 0x4A4E0000L + attr.hashCode() + slot.hashCode());
        c.setLong("UUIDLeast", 0x5245000000L + n);
        return c;
    }

    /** The JasprNether id of an item, or null. */
    static String id(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
            if (!n.hasTag()) return null;
            NBTTagCompound t = n.getTag();
            if (!t.hasKeyOfType("JasprNether", 10)) return null;
            String id = t.getCompound("JasprNether").getString("id");
            return id.isEmpty() ? null : id;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean is(ItemStack s, String id) { return id.equals(id(s)); }

    /** The display name of an item id (the id itself when unknown). */
    static String name(String id) { Def d = DEFS.get(id); return d == null ? id : d.name; }

    static List<String> ids() { return new ArrayList<>(DEFS.keySet()); }

    static boolean isArmorOf(String id, String set) { return id != null && id.startsWith(set + "_") && Arrays.asList("helmet", "chestplate", "leggings", "boots").contains(id.substring(set.length() + 1)); }
}
