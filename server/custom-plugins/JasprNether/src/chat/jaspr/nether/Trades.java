package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * NetherEx Pigtificate professions/careers and their trade lists (catalogue section 7.4; currency = Nether Amethyst
 * Crystal). Level 1 offers two random level-1 trades, each level-up adds one trade of the next level (max 3), as in
 * EntityPigtificate.populateTradeList. Careers pick a vanilla villager look.
 */
final class Trades {
    private Trades() {}

    interface Stack { ItemStack make(Random r); }

    static final class Trade {
        final Stack give, pay; final int minUses, maxUses, level;
        Trade(int level, Stack give, Stack pay, int minUses, int maxUses) { this.level = level; this.give = give; this.pay = pay; this.minUses = minUses; this.maxUses = maxUses; }
    }

    private static Stack v(Material m, int min, int max) { return r -> new ItemStack(m, min + r.nextInt(max - min + 1)); }
    private static Stack v(Material m, int data, int min, int max) { return r -> new ItemStack(m, min + r.nextInt(max - min + 1), (short) data); }
    private static Stack x(String id, int min, int max) { return r -> Items.create(id, min + r.nextInt(max - min + 1)); }
    private static Stack ac(int min, int max) { return x("amethyst_crystal", min, max); }
    private static Trade t(int level, Stack give, Stack pay, int minUses, int maxUses) { return new Trade(level, give, pay, minUses, maxUses); }

    private static Stack book(int level) {
        return r -> {
            Enchantment[] pool = {Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_FALL, Enchantment.DAMAGE_ALL,
                Enchantment.DIG_SPEED, Enchantment.DURABILITY, Enchantment.ARROW_DAMAGE, Enchantment.LOOT_BONUS_BLOCKS, Enchantment.LOOT_BONUS_MOBS,
                Enchantment.FIRE_ASPECT, Enchantment.KNOCKBACK, Enchantment.OXYGEN, Enchantment.THORNS};
            Enchantment en = pool[r.nextInt(pool.length)];
            int lvl = Math.max(1, Math.min(en.getMaxLevel(), 1 + r.nextInt(Math.max(1, level / 3))));
            ItemStack s = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta m = (EnchantmentStorageMeta) s.getItemMeta();
            m.addStoredEnchant(en, lvl, true);
            s.setItemMeta(m);
            return s;
        };
    }

    private static Stack potion(int min, int max) {
        return r -> {
            PotionType[] pool = {PotionType.FIRE_RESISTANCE, PotionType.REGEN, PotionType.SPEED, PotionType.STRENGTH, PotionType.INSTANT_HEAL,
                PotionType.WATER_BREATHING, PotionType.JUMP, PotionType.SLOWNESS}; // never night vision (owner rule)
            ItemStack s = new ItemStack(Material.POTION, min + r.nextInt(max - min + 1));
            PotionMeta m = (PotionMeta) s.getItemMeta();
            m.setBasePotionData(new PotionData(pool[r.nextInt(pool.length)]));
            s.setItemMeta(m);
            return s;
        };
    }

    static final String[] CAREERS = {"nincompoop", "hunter", "gatherer", "scavenger", "armorsmith", "toolsmith", "enchanter", "brewer"};

    static List<Trade> trades(String career) {
        List<Trade> l = new ArrayList<>();
        switch (career) {
            case "hunter":
                l.add(t(1, v(Material.ROTTEN_FLESH, 9, 15), ac(1, 1), 4, 16)); l.add(t(1, v(Material.SPIDER_EYE, 4, 6), ac(1, 1), 2, 8));
                l.add(t(1, v(Material.MAGMA_CREAM, 1, 2), ac(1, 1), 1, 8)); l.add(t(2, x("ghast_meat_cooked", 3, 7), ac(1, 2), 2, 4));
                l.add(t(2, ac(2, 2), x("ghast_meat_raw", 1, 1), 2, 8)); l.add(t(3, v(Material.ENDER_PEARL, 2, 4), x("ghast_meat_raw", 4, 6), 1, 8));
                l.add(t(3, v(Material.BLAZE_ROD, 2, 4), x("ghast_meat_raw", 4, 6), 1, 8));
                break;
            case "gatherer":
                l.add(t(1, x("congealed_magma_cream", 2, 2), ac(1, 1), 1, 8)); l.add(t(1, x("brown_elder_mushroom", 2, 4), ac(1, 3), 2, 8));
                l.add(t(1, x("red_elder_mushroom", 2, 4), ac(1, 3), 2, 8)); l.add(t(1, ac(1, 1), x("wither_bone", 32, 64), 1, 4));
                l.add(t(2, x("enoki_mushroom", 3, 3), ac(2, 4), 2, 8)); l.add(t(2, ac(3, 3), v(Material.QUARTZ, 12, 16), 1, 3));
                l.add(t(3, v(Material.NETHER_STALK, 3, 5), ac(3, 4), 2, 6));
                break;
            case "scavenger":
                l.add(t(1, v(Material.COBBLESTONE, 4, 16), ac(1, 2), 4, 16)); l.add(t(1, ac(1, 2), v(Material.STONE, 16, 32), 1, 4));
                l.add(t(1, v(Material.DIRT, 1, 4), ac(1, 2), 4, 16)); l.add(t(1, v(Material.GRAVEL, 8, 16), ac(1, 2), 4, 16));
                l.add(t(2, v(Material.LOG, 0, 1, 4), ac(3, 8), 2, 8)); l.add(t(2, v(Material.IRON_INGOT, 1, 3), ac(1, 2), 1, 8));
                l.add(t(2, v(Material.COAL, 3, 7), ac(1, 2), 2, 4)); l.add(t(2, v(Material.COAL, 1, 3, 7), ac(1, 2), 2, 4));
                l.add(t(3, v(Material.DIAMOND, 1, 2), ac(4, 8), 1, 3)); l.add(t(3, v(Material.BOOK, 1, 3), ac(2, 4), 1, 4));
                break;
            case "armorsmith":
                l.add(t(1, x("orange_salamander_hide", 1, 3), ac(3, 5), 2, 8)); l.add(t(1, x("black_salamander_hide", 1, 3), ac(3, 5), 2, 8));
                l.add(t(1, ac(1, 2), x("wither_bone", 5, 20), 1, 4)); l.add(t(1, x("wither_bone", 1, 4), ac(2, 4), 1, 4));
                for (String s : new String[]{"helmet", "chestplate", "leggings", "boots"}) l.add(t(2, x("orange_salamander_hide_" + s, 1, 1), ac(1, 3), 1, 2));
                l.add(t(2, x("black_salamander_hide_helmet", 1, 1), ac(1, 3), 1, 2)); l.add(t(2, x("black_salamander_hide_chestplate", 1, 1), ac(1, 3), 1, 2));
                l.add(t(2, x("black_salamander_hide_leggings", 1, 1), ac(2, 4), 1, 2)); l.add(t(2, x("black_salamander_hide_boots", 1, 1), ac(2, 4), 1, 2));
                for (String s : new String[]{"helmet", "chestplate", "leggings", "boots"}) l.add(t(3, x("wither_bone_" + s, 1, 1), ac(4, 6), 1, 3));
                break;
            case "toolsmith":
                l.add(t(1, ac(2, 4), v(Material.GOLD_INGOT, 2, 4), 2, 8)); l.add(t(1, v(Material.STONE_SWORD, 1, 1), ac(2, 5), 2, 4));
                l.add(t(1, v(Material.STONE_PICKAXE, 1, 1), ac(2, 4), 2, 4)); l.add(t(1, v(Material.STONE_SPADE, 1, 1), ac(2, 3), 2, 4));
                l.add(t(1, v(Material.STONE_HOE, 1, 1), ac(1, 2), 2, 4)); l.add(t(2, v(Material.DIAMOND, 1, 1), ac(2, 4), 2, 4));
                l.add(t(2, v(Material.IRON_SWORD, 1, 1), ac(3, 6), 2, 4)); l.add(t(2, v(Material.IRON_PICKAXE, 1, 1), ac(3, 5), 2, 4));
                l.add(t(2, v(Material.IRON_SPADE, 1, 1), ac(3, 4), 2, 4)); l.add(t(2, v(Material.IRON_HOE, 1, 1), ac(2, 3), 2, 4));
                for (String h : new String[]{"withered", "blazed", "frosted"}) {
                    l.add(t(3, x(h + "_amedian_sword", 1, 1), ac(4, 7), 2, 4)); l.add(t(3, x(h + "_amedian_pickaxe", 1, 1), ac(4, 6), 2, 4));
                    l.add(t(3, x(h + "_amedian_shovel", 1, 1), ac(4, 5), 2, 4)); l.add(t(3, x(h + "_amedian_hoe", 1, 1), ac(3, 4), 2, 4));
                    l.add(t(3, x(h + "_amedian_hammer", 1, 1), ac(5, 7), 2, 4));
                }
                break;
            case "enchanter":
                l.add(t(1, v(Material.BOOK, 1, 1), ac(5, 10), 2, 8)); l.add(t(1, v(Material.GLOWSTONE_DUST, 3, 6), ac(1, 1), 4, 8));
                l.add(t(1, ac(1, 2), v(Material.BLAZE_ROD, 4, 8), 1, 4)); l.add(t(1, book(6), ac(4, 16), 1, 2));
                l.add(t(2, book(8), ac(8, 32), 1, 4)); l.add(t(2, book(8), ac(8, 32), 1, 4));
                l.add(t(3, v(Material.EXP_BOTTLE, 1, 4), ac(6, 16), 1, 2));
                for (int i = 0; i < 3; i++) l.add(t(3, book(10), ac(12, 48), 1, 6));
                break;
            case "brewer":
                l.add(t(1, x("spore", 2, 5), ac(1, 1), 1, 8)); l.add(t(1, v(Material.MAGMA_CREAM, 2, 5), ac(1, 2), 2, 4));
                l.add(t(1, v(Material.GLASS_BOTTLE, 3, 6), ac(1, 1), 4, 8)); l.add(t(1, potion(1, 1), ac(3, 4), 1, 4));
                l.add(t(2, v(Material.BLAZE_POWDER, 1, 2), ac(6, 12), 1, 4)); l.add(t(2, potion(1, 2), ac(4, 5), 1, 2));
                l.add(t(3, potion(1, 3), ac(5, 6), 1, 2));
                break;
            default:
        }
        return l;
    }

    private static MerchantRecipe recipe(Trade tr, Random r) {
        int uses = tr.minUses + r.nextInt(tr.maxUses - tr.minUses + 1);
        MerchantRecipe m = new MerchantRecipe(tr.give.make(r), 0, uses, true);
        m.addIngredient(tr.pay.make(r));
        return m;
    }

    static void setup(Mobs mobs, Villager v, Random r) {
        String career = CAREERS[r.nextInt(CAREERS.length)];
        Villager.Career c;
        switch (career) {
            case "hunter": c = Villager.Career.BUTCHER; break;
            case "gatherer": c = Villager.Career.FARMER; break;
            case "scavenger": c = Villager.Career.FISHERMAN; break;
            case "armorsmith": c = Villager.Career.ARMORER; break;
            case "toolsmith": c = Villager.Career.TOOL_SMITH; break;
            case "enchanter": c = Villager.Career.LIBRARIAN; break;
            case "brewer": c = Villager.Career.CLERIC; break;
            default: c = Villager.Career.NITWIT;
        }
        v.setProfession(c.getProfession());
        v.setCareer(c, false);
        v.addScoreboardTag("jn_career_" + career);
        v.addScoreboardTag("jn_lvl_1");
        v.setCustomName(ChatColor.RESET + "Pigtificate " + Character.toUpperCase(career.charAt(0)) + career.substring(1));
        List<Trade> l1 = new ArrayList<>();
        for (Trade t : trades(career)) if (t.level == 1) l1.add(t);
        Collections.shuffle(l1, r);
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (int i = 0; i < Math.min(2, l1.size()); i++) recipes.add(recipe(l1.get(i), r));
        v.setRecipes(recipes);
    }

    static void levelUp(Mobs mobs, Villager v, Random r) {
        String career = null;
        int level = 1;
        for (String tag : v.getScoreboardTags()) {
            if (tag.startsWith("jn_career_")) career = tag.substring(10);
            if (tag.startsWith("jn_lvl_")) level = Integer.parseInt(tag.substring(7));
        }
        if (career == null || level >= 3) return;
        List<Trade> next = new ArrayList<>();
        for (Trade t : trades(career)) if (t.level == level + 1) next.add(t);
        v.removeScoreboardTag("jn_lvl_" + level);
        v.addScoreboardTag("jn_lvl_" + (level + 1));
        if (next.isEmpty()) return;
        List<MerchantRecipe> recipes = new ArrayList<>(v.getRecipes());
        recipes.add(recipe(next.get(r.nextInt(next.size())), r));
        v.setRecipes(recipes);
        mobs.ability("pigtificate_level_up");
    }
}
