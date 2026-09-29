package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Chest loot ported from the tables the two mods place: vanilla chests/nether_bridge (BetterNether cave room, and the
 * first pool of NetherEx temple_rare), NetherEx chest/base_temple (second pool of temple_rare) and chest/base_village;
 * and JasperCraft's own tables for the five mega structures (jaspr:mega/*, each with a vault table that always holds
 * that structure's progression prize) and the wonders (jaspr:wonder/*). Only Nether and expedition goods, graded by
 * how deep in the Nether's progression the structure sits: Bazaar (amethyst, gold) < Pyramid and Forge (wither bone,
 * hides, blaze stock) < Cathedral (the Sorrow brew for the Ghast Queen) < Citadel (rime, frost, the richest vault).
 * Filled at generation time into the chest's live inventory (never followed by BlockState.update()).
 */
final class Loot {
    private Loot() {}

    interface Maker { ItemStack make(Random r); }

    static final class Entry {
        final int weight; final Maker maker;
        Entry(int weight, Maker maker) { this.weight = weight; this.maker = maker; }
    }

    static final class Pool {
        final int min, max; final List<Entry> entries = new ArrayList<>(); int total;
        Pool(int min, int max) { this.min = min; this.max = max; }
        Pool add(int weight, Maker m) { entries.add(new Entry(weight, m)); total += weight; return this; }
        void roll(Random r, List<ItemStack> out) {
            int rolls = min + r.nextInt(max - min + 1);
            for (int i = 0; i < rolls; i++) {
                int pick = r.nextInt(total);
                for (Entry e : entries) { pick -= e.weight; if (pick < 0) { ItemStack s = e.maker.make(r); if (s != null) out.add(s); break; } }
            }
        }
    }

    private static Maker v(Material m, int min, int max) { return r -> new ItemStack(m, min + r.nextInt(max - min + 1)); }
    private static Maker v(Material m, int data, int min, int max) { return r -> new ItemStack(m, min + r.nextInt(max - min + 1), (short) data); }
    private static Maker nx(String id, int min, int max) { return r -> Items.create(id, min + r.nextInt(max - min + 1)); }
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    /** One random piece of a JasprNether armour set. */
    static Maker armour(String set) { return r -> Items.create(set + "_" + SLOTS[r.nextInt(4)], 1); }
    private static Maker potion(org.bukkit.potion.PotionType type) {
        return r -> {
            ItemStack s = new ItemStack(Material.POTION);
            org.bukkit.inventory.meta.PotionMeta m = (org.bukkit.inventory.meta.PotionMeta) s.getItemMeta();
            m.setBasePotionData(new org.bukkit.potion.PotionData(type));
            s.setItemMeta(m);
            return s;
        };
    }
    /** An enchanted book of a modest level (never above III). */
    private static Maker book() {
        return r -> {
            org.bukkit.enchantments.Enchantment[] pool = {org.bukkit.enchantments.Enchantment.PROTECTION_FIRE, org.bukkit.enchantments.Enchantment.PROTECTION_EXPLOSIONS,
                org.bukkit.enchantments.Enchantment.PROTECTION_PROJECTILE, org.bukkit.enchantments.Enchantment.DURABILITY, org.bukkit.enchantments.Enchantment.DAMAGE_ALL,
                org.bukkit.enchantments.Enchantment.DIG_SPEED, org.bukkit.enchantments.Enchantment.FIRE_ASPECT, org.bukkit.enchantments.Enchantment.ARROW_FIRE,
                org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, org.bukkit.enchantments.Enchantment.PROTECTION_FALL};
            org.bukkit.enchantments.Enchantment e = pool[r.nextInt(pool.length)];
            ItemStack s = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta m = (org.bukkit.inventory.meta.EnchantmentStorageMeta) s.getItemMeta();
            m.addStoredEnchant(e, 1 + r.nextInt(Math.min(3, e.getMaxLevel())), true);
            s.setItemMeta(m);
            return s;
        };
    }

    static final Pool NETHER_BRIDGE = new Pool(2, 4)
        .add(5, v(Material.DIAMOND, 1, 3)).add(5, v(Material.IRON_INGOT, 1, 5)).add(15, v(Material.GOLD_INGOT, 1, 3))
        .add(5, v(Material.GOLD_SWORD, 1, 1)).add(5, v(Material.GOLD_CHESTPLATE, 1, 1)).add(5, v(Material.FLINT_AND_STEEL, 1, 1))
        .add(5, v(Material.NETHER_STALK, 3, 7)).add(10, v(Material.SADDLE, 1, 1)).add(8, v(Material.GOLD_BARDING, 1, 1))
        .add(5, v(Material.IRON_BARDING, 1, 1)).add(3, v(Material.DIAMOND_BARDING, 1, 1)).add(2, v(Material.OBSIDIAN, 2, 4));

    static final Pool BASE_TEMPLE = new Pool(2, 7)
        .add(3, v(Material.GOLD_INGOT, 1, 2)).add(10, v(Material.DIAMOND, 1, 1)).add(5, v(Material.EMERALD, 1, 1))
        .add(10, v(Material.QUARTZ, 1, 10)).add(10, v(Material.GOLD_NUGGET, 1, 16)).add(5, v(Material.GHAST_TEAR, 1, 1))
        .add(1, nx("wither_bone", 1, 1)).add(5, v(Material.OBSIDIAN, 1, 6)).add(10, v(Material.FLINT, 1, 1))
        .add(3, v(Material.GLOWSTONE, 1, 1)).add(10, v(Material.IRON_INGOT, 1, 1)).add(1, v(Material.GOLD_PICKAXE, 1, 1))
        // JasprNether: the shrine is where the Queen is summoned, so its chests may hold the summoning potion.
        .add(4, r -> Items.create("potion_sorrow", 1));

    static final Pool BASE_VILLAGE = new Pool(3, 7)
        .add(1, v(Material.NETHER_STALK, 1, 2)).add(1, v(Material.GOLD_INGOT, 1, 2)).add(10, v(Material.ROTTEN_FLESH, 1, 5))
        .add(5, v(Material.GOLD_NUGGET, 1, 4)).add(10, v(Material.NETHER_BRICK_ITEM, 1, 4)).add(5, v(Material.NETHER_WART_BLOCK, 1, 1))
        .add(1, v(Material.MAGMA_CREAM, 1, 1)).add(1, v(Material.BLAZE_POWDER, 1, 1)).add(1, v(Material.DIAMOND, 1, 1))
        .add(1, v(Material.EMERALD, 1, 1)).add(1, v(Material.GOLD_SPADE, 1, 1)).add(5, v(Material.STAINED_CLAY, 15, 1, 6))
        .add(1, v(Material.FLINT, 1, 1)).add(1, v(Material.GLOWSTONE, 1, 1)).add(1, v(Material.QUARTZ, 1, 1))
        .add(2, nx("amethyst_crystal", 1, 3));

    // ---- JasperCraft mega structures ------------------------------------------------------------------------------
    /** The Golden Bazaar: Pigtificate wares, gold and the amethyst currency. */
    static final Pool BAZAAR = new Pool(4, 8)
        .add(10, nx("amethyst_crystal", 1, 4)).add(8, v(Material.GOLD_NUGGET, 3, 12)).add(5, v(Material.GOLD_INGOT, 1, 3))
        .add(6, v(Material.NETHER_STALK, 2, 6)).add(4, v(Material.MAGMA_CREAM, 1, 3)).add(4, nx("congealed_magma_cream", 1, 2))
        .add(5, nx("cincinnasite", 1, 4)).add(4, v(Material.QUARTZ, 2, 8)).add(3, v(Material.GLOWSTONE_DUST, 2, 6))
        .add(3, nx("ghast_meat_cooked", 1, 3)).add(3, nx("brown_elder_mushroom", 1, 2)).add(2, nx("red_elder_mushroom", 1, 2))
        .add(2, v(Material.BLAZE_POWDER, 1, 2)).add(2, v(Material.GOLD_SWORD, 1, 1)).add(1, v(Material.SADDLE, 1, 1)).add(1, v(Material.GOLD_BARDING, 1, 1));
    static final Pool BAZAAR_VAULT = new Pool(4, 8)
        .add(10, nx("amethyst_crystal", 3, 8)).add(8, v(Material.GOLD_INGOT, 2, 6)).add(3, v(Material.DIAMOND, 1, 2)).add(4, v(Material.GHAST_TEAR, 1, 2))
        .add(2, nx("amethyst_block", 1, 1)).add(2, nx("dull_mirror", 1, 1)).add(4, nx("cincinnasite_forged", 2, 4))
        .add(3, potion(org.bukkit.potion.PotionType.FIRE_RESISTANCE)).add(2, v(Material.GOLDEN_APPLE, 1, 1)).add(2, book());

    /** The Soul Pyramid: wither bones and temple treasure. */
    static final Pool PYRAMID = new Pool(3, 7)
        .add(10, nx("wither_bone", 1, 2)).add(8, nx("wither_dust", 2, 5)).add(8, v(Material.BONE, 2, 6)).add(6, v(Material.QUARTZ, 3, 10))
        .add(6, v(Material.GOLD_NUGGET, 2, 9)).add(4, v(Material.GOLD_INGOT, 1, 3)).add(4, v(Material.SOUL_SAND, 2, 6)).add(3, v(Material.NETHER_STALK, 2, 5))
        .add(3, v(Material.SADDLE, 1, 1)).add(2, v(Material.GOLD_BARDING, 1, 1)).add(2, v(Material.IRON_BARDING, 1, 1)).add(2, nx("potion_sorrow", 1, 1))
        .add(2, v(Material.OBSIDIAN, 1, 4)).add(1, v(Material.DIAMOND, 1, 2));
    static final Pool PYRAMID_VAULT = new Pool(4, 7)
        .add(10, nx("wither_bone", 2, 5)).add(5, armour("wither_bone")).add(4, nx("blazed_wither_bone", 1, 1)).add(5, v(Material.DIAMOND, 1, 3))
        .add(6, v(Material.GOLD_INGOT, 2, 6)).add(3, v(Material.GOLDEN_APPLE, 1, 2)).add(3, book()).add(3, nx("potion_sorrow", 1, 1))
        .add(2, v(Material.DIAMOND_BARDING, 1, 1));

    /** The Cinder Forge: blaze stock, magma, salamander hides and cincinnasite. */
    static final Pool FORGE = new Pool(3, 7)
        .add(8, v(Material.BLAZE_POWDER, 2, 6)).add(6, v(Material.BLAZE_ROD, 1, 3)).add(6, v(Material.MAGMA_CREAM, 2, 5)).add(4, nx("congealed_magma_cream", 1, 2))
        .add(6, nx("orange_salamander_hide", 1, 3)).add(2, nx("black_salamander_hide", 1, 1)).add(6, v(Material.IRON_INGOT, 2, 6)).add(5, v(Material.GOLD_INGOT, 1, 4))
        .add(6, nx("cincinnasite", 2, 6)).add(4, nx("cincinnasite_forged", 1, 3)).add(4, v(Material.OBSIDIAN, 2, 5)).add(3, v(Material.FLINT_AND_STEEL, 1, 1))
        .add(4, v(Material.FIREBALL, 2, 5)).add(3, nx("nether_reed", 2, 4));
    static final Pool FORGE_VAULT = new Pool(4, 7)
        .add(5, armour("orange_salamander_hide")).add(3, nx("cincinnasite_pickaxe", 1, 1)).add(3, nx("cincinnasite_axe", 1, 1)).add(5, nx("blazed_wither_bone", 1, 1))
        .add(6, v(Material.IRON_INGOT, 4, 10)).add(5, v(Material.GOLD_INGOT, 3, 7)).add(4, v(Material.DIAMOND, 1, 3)).add(5, v(Material.BLAZE_ROD, 3, 6)).add(3, book());

    /** The Spore Cathedral: spores and elder mushrooms, ghast meat for the Sorrow brew. */
    static final Pool CATHEDRAL = new Pool(3, 7)
        .add(8, nx("spore", 1, 4)).add(6, nx("brown_elder_mushroom", 1, 3)).add(6, nx("red_elder_mushroom", 1, 3)).add(5, nx("enoki_mushroom", 2, 5))
        .add(6, nx("ghast_meat_raw", 1, 3)).add(4, v(Material.GHAST_TEAR, 1, 2)).add(5, v(Material.GLOWSTONE_DUST, 2, 6)).add(4, v(Material.NETHER_STALK, 2, 5))
        .add(3, nx("potion_dispersal", 1, 1)).add(3, nx("wither_dust", 1, 3)).add(4, v(Material.GOLD_NUGGET, 2, 8)).add(2, nx("potion_sorrow", 1, 1));
    static final Pool CATHEDRAL_VAULT = new Pool(3, 6)
        .add(8, nx("ghast_meat_raw", 2, 4)).add(6, v(Material.GHAST_TEAR, 1, 3)).add(4, nx("potion_sorrow", 1, 1)).add(1, nx("withered_amedian_sword", 1, 1))
        .add(3, nx("dull_mirror", 1, 1)).add(4, v(Material.DIAMOND, 1, 2)).add(6, nx("amethyst_crystal", 3, 8)).add(3, book());

    /** The Frozen Citadel: rime and frost, and the Nether's richest vault. */
    static final Pool CITADEL = new Pool(3, 7)
        .add(8, nx("rime_crystal", 2, 5)).add(5, nx("frost_rod", 1, 2)).add(5, nx("frost_powder", 2, 4)).add(5, nx("frost_fang", 1, 3))
        .add(3, nx("potion_freezing", 1, 1)).add(3, nx("potion_frigid_health", 1, 1)).add(3, v(Material.PACKED_ICE, 4, 10)).add(4, v(Material.BONE, 2, 5))
        .add(3, v(Material.DIAMOND, 1, 2)).add(4, v(Material.GOLD_INGOT, 1, 3)).add(4, v(Material.IRON_INGOT, 2, 5)).add(4, nx("amethyst_crystal", 1, 3));
    static final Pool CITADEL_VAULT = new Pool(5, 9)
        .add(8, nx("rime_crystal", 4, 8)).add(4, nx("rime_and_steel", 1, 1)).add(4, nx("rime_block", 1, 2)).add(6, v(Material.DIAMOND, 2, 4))
        .add(6, nx("amethyst_crystal", 4, 10)).add(1, nx("frosted_amedian_sword", 1, 1)).add(1, nx("frosted_amedian_pickaxe", 1, 1))
        .add(3, v(Material.GOLDEN_APPLE, 1, 2)).add(4, book()).add(3, nx("frost_rod", 2, 4));

    // ---- JasperCraft wonders --------------------------------------------------------------------------------------
    /** A lost expedition's supplies (the camp's journal is added by StructureOps). */
    static final Pool CAMP = new Pool(3, 6)
        .add(6, v(Material.BREAD, 2, 5)).add(4, v(Material.COOKED_BEEF, 1, 3)).add(5, v(Material.TORCH, 4, 10)).add(5, v(Material.GOLD_NUGGET, 2, 8))
        .add(3, v(Material.FLINT_AND_STEEL, 1, 1)).add(2, potion(org.bukkit.potion.PotionType.FIRE_RESISTANCE)).add(3, v(Material.ARROW, 4, 12))
        .add(3, v(Material.IRON_INGOT, 1, 3)).add(2, v(Material.OBSIDIAN, 1, 3)).add(2, nx("amethyst_crystal", 1, 2));
    /** A hanging cage's remains. */
    static final Pool CAGE = new Pool(2, 4)
        .add(6, v(Material.BONE, 1, 4)).add(5, v(Material.GOLD_NUGGET, 2, 6)).add(4, v(Material.ROTTEN_FLESH, 1, 3)).add(3, nx("amethyst_crystal", 1, 2))
        .add(3, v(Material.QUARTZ, 2, 5)).add(2, v(Material.GOLD_INGOT, 1, 2)).add(1, nx("wither_bone", 1, 1));
    /** A soul-sand grave. */
    static final Pool GRAVE = new Pool(2, 5)
        .add(8, v(Material.BONE, 2, 5)).add(5, nx("wither_dust", 1, 3)).add(5, v(Material.GOLD_NUGGET, 2, 7)).add(4, v(Material.QUARTZ, 2, 6))
        .add(2, nx("wither_bone", 1, 1)).add(2, v(Material.GOLD_INGOT, 1, 2)).add(1, v(Material.GOLDEN_APPLE, 1, 1));
    /** A frozen obelisk's offering. */
    static final Pool OBELISK = new Pool(2, 4)
        .add(6, nx("rime_crystal", 1, 3)).add(4, nx("frost_powder", 1, 3)).add(3, v(Material.PACKED_ICE, 2, 6)).add(3, v(Material.BONE, 1, 3))
        .add(2, nx("frost_fang", 1, 1)).add(2, v(Material.GOLD_NUGGET, 2, 6));

    // ---- the GLM strongholds (glm:<tier>:<theme>) --------------------------------------------------------------------
    static final String[] GEAR = {"hellforged_helmet", "hellforged_chestplate", "hellforged_leggings", "hellforged_boots", "soulweave_helmet",
        "soulweave_chestplate", "soulweave_leggings", "soulweave_boots"};
    static final String[] WEAPONS = {"infernal_blade", "soulreaper_scythe", "magma_maul", "ember_bow"};
    static final String[] TRINKETS = {"brimstone_idol", "heart_of_cinders", "hellhound_collar", "obsidian_aegis", "ember_heart", "wither_ward",
        "magma_band", "ghastly_pendant"};
    /** Odds and ends: every chest of a stronghold is worth opening. */
    static final Pool GLM_SCRAPS = new Pool(1, 3)
        .add(8, v(Material.GOLD_NUGGET, 1, 5)).add(6, v(Material.BONE, 1, 3)).add(5, v(Material.ARROW, 2, 6)).add(4, v(Material.STRING, 1, 3))
        .add(4, v(Material.SULPHUR, 1, 2)).add(4, v(Material.BREAD, 1, 2)).add(4, v(Material.NETHER_STALK, 1, 3)).add(4, v(Material.COAL, 1, 3))
        .add(4, v(Material.QUARTZ, 1, 4)).add(4, v(Material.IRON_NUGGET, 1, 5)).add(3, v(Material.GLOWSTONE_DUST, 1, 3)).add(3, v(Material.TORCH, 2, 6))
        .add(3, nx("hellforged_shard", 1, 1)).add(2, nx("charred_bone", 1, 2)).add(2, nx("brimstone", 1, 2)).add(1, nx("amethyst_crystal", 1, 1));
    /** A stronghold's ordinary chests. */
    static final Pool GLM_COMMON = new Pool(3, 6)
        .add(8, v(Material.GOLD_INGOT, 1, 3)).add(7, v(Material.IRON_INGOT, 1, 4)).add(6, nx("amethyst_crystal", 1, 3)).add(6, nx("hellforged_shard", 1, 3))
        .add(5, v(Material.QUARTZ, 2, 8)).add(4, v(Material.BLAZE_POWDER, 1, 3)).add(4, v(Material.MAGMA_CREAM, 1, 3)).add(3, potion(org.bukkit.potion.PotionType.FIRE_RESISTANCE))
        .add(4, v(Material.ARROW, 4, 12)).add(3, v(Material.GOLDEN_CARROT, 1, 3)).add(3, v(Material.COOKED_BEEF, 2, 4)).add(2, book())
        .add(2, v(Material.OBSIDIAN, 1, 3)).add(2, nx("soul_essence", 1, 1)).add(2, nx("molten_core", 1, 1))
        .add(1, r -> Items.create(GEAR[r.nextInt(GEAR.length)], 1)).add(1, r -> Items.create(TRINKETS[r.nextInt(TRINKETS.length)], 1));
    /** The chests of great buildings and Lords' strongholds. */
    static final Pool GLM_RICH = new Pool(4, 7)
        .add(8, nx("hellforged_shard", 2, 5)).add(4, nx("hellforged_ingot", 1, 2)).add(7, v(Material.GOLD_INGOT, 2, 5)).add(6, nx("amethyst_crystal", 2, 5))
        .add(4, v(Material.IRON_INGOT, 2, 6)).add(3, v(Material.DIAMOND, 1, 2)).add(4, book()).add(3, v(Material.GOLDEN_APPLE, 1, 1))
        .add(4, potion(org.bukkit.potion.PotionType.FIRE_RESISTANCE)).add(2, potion(org.bukkit.potion.PotionType.STRENGTH)).add(3, nx("soul_essence", 1, 2))
        .add(3, nx("molten_core", 1, 2)).add(3, nx("void_shard", 1, 2)).add(4, r -> Items.create(GEAR[r.nextInt(GEAR.length)], 1))
        .add(3, r -> Items.create(WEAPONS[r.nextInt(WEAPONS.length)], 1)).add(3, r -> Items.create(TRINKETS[r.nextInt(TRINKETS.length)], 1));
    /** Vaults: the deepest chest of a building; always a prize. */
    static final Pool GLM_VAULT = new Pool(5, 8)
        .add(8, v(Material.GOLD_INGOT, 3, 7)).add(7, nx("amethyst_crystal", 3, 8)).add(5, v(Material.DIAMOND, 1, 3)).add(5, nx("hellforged_ingot", 1, 3))
        .add(4, book()).add(4, v(Material.GOLDEN_APPLE, 1, 2)).add(4, nx("molten_core", 1, 2)).add(4, nx("soul_essence", 1, 3))
        .add(5, r -> Items.create(GEAR[r.nextInt(GEAR.length)], 1)).add(4, r -> Items.create(WEAPONS[r.nextInt(WEAPONS.length)], 1))
        .add(4, r -> Items.create(TRINKETS[r.nextInt(TRINKETS.length)], 1)).add(1, v(Material.GHAST_TEAR, 1, 2));
    /** Each theme's own goods, one or two rolls on top of the tier. */
    static final Map<String, Pool> THEME = new java.util.HashMap<>();
    static {
        THEME.put("bastion", new Pool(1, 2).add(6, v(Material.GOLD_INGOT, 2, 5)).add(5, v(Material.GOLD_NUGGET, 6, 18)).add(3, nx("imp_horn", 1, 2)).add(2, v(Material.GOLD_SWORD, 1, 1)));
        THEME.put("fortress", new Pool(1, 2).add(5, nx("wither_bone", 1, 2)).add(5, nx("ashbone", 1, 3)).add(4, v(Material.BLAZE_ROD, 1, 2)).add(3, nx("hellforged_shard", 1, 3)));
        THEME.put("castle", new Pool(1, 2).add(5, v(Material.IRON_INGOT, 2, 5)).add(4, v(Material.ARROW, 6, 16)).add(3, nx("hellforged_shard", 1, 3)).add(3, book()));
        THEME.put("crypt", new Pool(1, 2).add(5, v(Material.BONE, 2, 6)).add(5, nx("charred_bone", 1, 3)).add(4, nx("soul_essence", 1, 2)).add(3, nx("wither_dust", 1, 3)));
        THEME.put("arcane", new Pool(1, 2).add(5, nx("hex_ember", 1, 3)).add(5, nx("pyre_ember", 1, 3)).add(4, book()).add(3, v(Material.GHAST_TEAR, 1, 1))
            .add(2, potion(org.bukkit.potion.PotionType.STRENGTH)));
        THEME.put("volcanic", new Pool(1, 2).add(6, nx("molten_core", 1, 2)).add(5, v(Material.MAGMA_CREAM, 2, 5)).add(4, nx("pyre_ember", 1, 3)).add(3, v(Material.OBSIDIAN, 2, 5)));
        THEME.put("void", new Pool(1, 2).add(6, nx("void_shard", 1, 3)).add(4, nx("soul_essence", 1, 2)).add(3, v(Material.ENDER_PEARL, 1, 2)).add(3, v(Material.CHORUS_FRUIT, 2, 5)));
        THEME.put("temple", new Pool(1, 2).add(5, nx("brimstone", 2, 5)).add(5, nx("hex_ember", 1, 2)).add(4, v(Material.GOLD_NUGGET, 4, 12)).add(3, v(Material.GLOWSTONE_DUST, 3, 8)));
        THEME.put("dungeon", new Pool(1, 2).add(5, nx("brimstone", 1, 4)).add(5, v(Material.STRING, 2, 6)).add(4, nx("charred_bone", 1, 3)).add(4, v(Material.IRON_NUGGET, 4, 12)));
        THEME.put("hub", new Pool(1, 2).add(5, v(Material.IRON_INGOT, 2, 5)).add(5, v(Material.QUARTZ, 4, 12)).add(4, v(Material.GOLD_NUGGET, 4, 12)).add(3, v(Material.OBSIDIAN, 2, 6)));
        THEME.put("farm", new Pool(1, 2).add(6, v(Material.NETHER_STALK, 4, 12)).add(5, v(Material.MAGMA_CREAM, 1, 3)).add(4, v(Material.GRILLED_PORK, 2, 5)).add(3, nx("imp_horn", 1, 2)));
        THEME.put("vault", new Pool(1, 3).add(6, v(Material.GOLD_INGOT, 2, 6)).add(5, nx("amethyst_crystal", 2, 6)).add(2, v(Material.DIAMOND, 1, 2)).add(3, nx("hellforged_ingot", 1, 1)));
        THEME.put("cathedral", new Pool(1, 2).add(5, nx("soul_essence", 1, 3)).add(5, nx("hex_ember", 1, 2)).add(4, book()).add(3, v(Material.GOLD_NUGGET, 4, 10)));
        THEME.put("ruin", new Pool(1, 2).add(5, nx("charred_bone", 1, 3)).add(5, nx("hellhound_fang", 1, 2)).add(4, v(Material.COOKED_BEEF, 2, 4)).add(3, v(Material.ARROW, 4, 10)));
    }

    static List<ItemStack> roll(String table, Random r) {
        List<ItemStack> out = new ArrayList<>();
        if (table.startsWith("glm:")) {
            String[] p = table.split(":");
            String tier = p.length > 1 ? p[1] : "common", theme = p.length > 2 ? p[2] : "";
            switch (tier) {
                case "scraps": GLM_SCRAPS.roll(r, out); return out;
                case "rich": GLM_RICH.roll(r, out); break;
                case "vault":
                    GLM_VAULT.roll(r, out);
                    String[] prize = r.nextInt(3) == 0 ? WEAPONS : r.nextBoolean() ? GEAR : TRINKETS;
                    out.add(Items.create(prize[r.nextInt(prize.length)], 1));
                    break;
                default: GLM_COMMON.roll(r, out);
            }
            Pool t = THEME.get(theme);
            if (t != null) t.roll(r, out);
            return out;
        }
        switch (table) {
            case "netherex:chest/temple_rare": NETHER_BRIDGE.roll(r, out); BASE_TEMPLE.roll(r, out); break;
            case "netherex:chest/base_temple": BASE_TEMPLE.roll(r, out); break;
            case "netherex:chest/base_village": BASE_VILLAGE.roll(r, out); break;
            case "jaspr:mega/bazaar": BAZAAR.roll(r, out); break;
            case "jaspr:mega/bazaar_vault": BAZAAR_VAULT.roll(r, out); out.add(Items.create("amethyst_crystal", 6 + r.nextInt(7))); break;
            case "jaspr:mega/pyramid": PYRAMID.roll(r, out); break;
            case "jaspr:mega/pyramid_vault": PYRAMID_VAULT.roll(r, out); out.add(armour("wither_bone").make(r)); break;
            case "jaspr:mega/forge": FORGE.roll(r, out); break;
            case "jaspr:mega/forge_vault": FORGE_VAULT.roll(r, out); out.add(armour("orange_salamander_hide").make(r)); break;
            case "jaspr:mega/cathedral": CATHEDRAL.roll(r, out); break;
            case "jaspr:mega/cathedral_vault": CATHEDRAL_VAULT.roll(r, out); out.add(Items.create("potion_sorrow", 1)); break;
            case "jaspr:mega/citadel": CITADEL.roll(r, out); break;
            case "jaspr:mega/citadel_vault": CITADEL_VAULT.roll(r, out); out.add(Items.create("frosted_wither_bone", 1)); break;
            case "jaspr:wonder/camp": CAMP.roll(r, out); break;
            case "jaspr:wonder/cage": CAGE.roll(r, out); break;
            case "jaspr:wonder/grave": GRAVE.roll(r, out); break;
            case "jaspr:wonder/obelisk": OBELISK.roll(r, out); break;
            default: NETHER_BRIDGE.roll(r, out);
        }
        return out;
    }

    /** Every table a structure may name (the self-test rolls each one). */
    static final String[] TABLES = {"minecraft:chests/nether_bridge", "netherex:chest/temple_rare", "netherex:chest/base_temple", "netherex:chest/base_village",
        "jaspr:mega/bazaar", "jaspr:mega/bazaar_vault", "jaspr:mega/pyramid", "jaspr:mega/pyramid_vault", "jaspr:mega/forge", "jaspr:mega/forge_vault",
        "jaspr:mega/cathedral", "jaspr:mega/cathedral_vault", "jaspr:mega/citadel", "jaspr:mega/citadel_vault",
        "jaspr:wonder/camp", "jaspr:wonder/cage", "jaspr:wonder/grave", "jaspr:wonder/obelisk",
        "glm:scraps:castle", "glm:common:bastion", "glm:rich:crypt", "glm:vault:volcanic", "glm:vault:arcane", "glm:rich:void", "glm:common:farm"};

    /** Vanilla-style fill: each rolled stack goes to a random empty slot (large stacks may be split in two). */
    static int fill(Inventory inv, String table, Random r) {
        List<ItemStack> items = roll(table, r);
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) empty.add(i);
        java.util.Collections.shuffle(empty, r);
        List<ItemStack> split = new ArrayList<>();
        for (ItemStack s : items) {
            if (s.getAmount() > 1 && r.nextInt(3) == 0 && split.size() + items.size() < empty.size()) {
                int part = 1 + r.nextInt(s.getAmount() - 1);
                ItemStack other = s.clone();
                other.setAmount(part);
                s.setAmount(s.getAmount() - part);
                split.add(other);
            }
            split.add(s);
        }
        int n = 0;
        for (ItemStack s : split) {
            if (n >= empty.size()) break;
            inv.setItem(empty.get(n++), s);
        }
        return n;
    }
}
