package chat.jaspr.enchant.fx;

import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** SME UpgradeRecipe + ConfigProvider upgrade tables with the UpgradeConfig defaults. */
public final class Upgrading {
    private Upgrading() {}

    public static final int DENY = -99;

    // UpgradeConfig defaults
    public static final Item TOKEN = Items.PRISMARINE_SHARD; // "minecraft:prismarine_shard", any metadata
    public static final int TOKEN_LEVEL = 1, TOKEN_TIER = 8;
    public static final int LEVEL_COST_MODE = 1;
    public static final float LEVEL_COST_MULTIPLIER = 2.0F;
    public static final boolean ONLY_BOOKS = false, ONLY_COMPATIBLE = true;
    public static final float FAIL_TIER = 0.1F, FAIL_LEVEL = 0.0F;
    public static final boolean FAIL_REMOVES_ORIGINAL = false;
    public static final int TIER_LEVEL_MODE = 1, TIER_LEVEL_REDUCTION = 1;
    public static final boolean ALLOW_TIER = true, ALLOW_LEVEL = true;
    public static final int ANVIL_REPAIR_MODE = 2;
    public static final float ANVIL_REPAIR_AMOUNT = 10.0F;
    public static final int BOOKSHELVES_NEEDED = 30;
    public static final boolean CLUES = true;

    private static final String[] ORDER = {
            "somanyenchantments:lessersharpness, minecraft:sharpness, somanyenchantments:advancedsharpness, somanyenchantments:supremesharpness",
            "somanyenchantments:lessersmite, minecraft:smite, somanyenchantments:advancedsmite, somanyenchantments:supremesmite",
            "somanyenchantments:lesserbaneofarthropods, minecraft:bane_of_arthropods, somanyenchantments:advancedbaneofarthropods, somanyenchantments:supremebaneofarthropods",
            "somanyenchantments:lesserfireaspect, minecraft:fire_aspect, somanyenchantments:advancedfireaspect, somanyenchantments:supremefireaspect",
            "minecraft:knockback, somanyenchantments:advancedknockback",
            "minecraft:looting, somanyenchantments:advancedlooting",
            "minecraft:efficiency, somanyenchantments:advancedefficiency",
            "minecraft:luck_of_the_sea, somanyenchantments:advancedluckofthesea",
            "minecraft:lure, somanyenchantments:advancedlure",
            "minecraft:mending, somanyenchantments:advancedmending",
            "somanyenchantments:lesserflame, minecraft:flame, somanyenchantments:advancedflame, somanyenchantments:supremeflame",
            "minecraft:punch, somanyenchantments:advancedpunch",
            "minecraft:power, somanyenchantments:advancedpower",
            "minecraft:feather_falling, somanyenchantments:advancedfeatherfalling",
            "minecraft:blast_protection, somanyenchantments:advancedblastprotection",
            "minecraft:fire_protection, somanyenchantments:advancedfireprotection",
            "minecraft:projectile_protection, somanyenchantments:advancedprojectileprotection",
            "minecraft:protection, somanyenchantments:advancedprotection",
            "minecraft:thorns, somanyenchantments:burningthorns, somanyenchantments:advancedthorns"
    };

    private static final String[] CURSING = {
            "somanyenchantments:lessersharpness, minecraft:sharpness, somanyenchantments:advancedsharpness, somanyenchantments:supremesharpness, somanyenchantments:bluntness",
            "somanyenchantments:lessersmite, minecraft:smite, somanyenchantments:advancedsmite, somanyenchantments:supremesmite, somanyenchantments:bluntness",
            "somanyenchantments:lesserbaneofarthropods, minecraft:bane_of_arthropods, somanyenchantments:advancedbaneofarthropods, somanyenchantments:supremebaneofarthropods, somanyenchantments:bluntness",
            "somanyenchantments:lesserfireaspect, minecraft:fire_aspect, somanyenchantments:advancedfireaspect, somanyenchantments:supremefireaspect, somanyenchantments:extinguish",
            "minecraft:knockback, somanyenchantments:advancedknockback, somanyenchantments:dragging",
            "minecraft:looting, somanyenchantments:advancedlooting, somanyenchantments:ascetic",
            "minecraft:efficiency, somanyenchantments:advancedefficiency, somanyenchantments:inefficient",
            "minecraft:luck_of_the_sea, somanyenchantments:advancedluckofthesea, somanyenchantments:ascetic",
            "minecraft:lure, somanyenchantments:advancedlure, none",
            "minecraft:mending, somanyenchantments:advancedmending, somanyenchantments:rusted",
            "somanyenchantments:lesserflame, minecraft:flame, somanyenchantments:advancedflame, somanyenchantments:supremeflame, somanyenchantments:extinguish",
            "minecraft:punch, somanyenchantments:advancedpunch, somanyenchantments:dragging",
            "minecraft:power, somanyenchantments:advancedpower, somanyenchantments:powerless",
            "minecraft:feather_falling, somanyenchantments:advancedfeatherfalling, somanyenchantments:heavyweight",
            "somanyenchantments:lightweight, somanyenchantments:evasion, somanyenchantments:heavyweight",
            "minecraft:blast_protection, somanyenchantments:advancedblastprotection, somanyenchantments:breachedplating",
            "minecraft:fire_protection, somanyenchantments:advancedfireprotection, somanyenchantments:breachedplating",
            "minecraft:projectile_protection, somanyenchantments:advancedprojectileprotection, somanyenchantments:breachedplating",
            "minecraft:protection, somanyenchantments:advancedprotection, somanyenchantments:supremeprotection, somanyenchantments:breachedplating",
            "minecraft:thorns, somanyenchantments:burningthorns, somanyenchantments:advancedthorns, somanyenchantments:meltdown",
            "somanyenchantments:luckmagnification, somanyenchantments:adept, mujmajnkraftsbettersurvival:education, somanyenchantments:ascetic"
    };

    private static Enchantment ench(String s) {
        return Enchantment.enchantments.get(new MinecraftKey(s.trim()));
    }

    /** ConfigProvider.getUpgradedEnchantFor */
    static Enchantment upgradeOf(Enchantment e) {
        for (String line : ORDER) {
            List<Enchantment> l = new ArrayList<>();
            for (String a : line.split(",")) {
                Enchantment x = ench(a);
                if (x != null) l.add(x);
            }
            int i = l.indexOf(e);
            if (i != -1 && i + 1 < l.size()) return l.get(i + 1);
        }
        return null;
    }

    /** ConfigProvider.getFailureEnchantFor ("none" -> null; unknown enchant not listed -> null) */
    static Enchantment failureOf(Enchantment e) {
        for (String line : CURSING) {
            String[] args = line.split(",");
            List<Enchantment> list = new ArrayList<>();
            Enchantment curse = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i].trim();
                if (a.isEmpty()) continue;
                if (i == args.length - 1) curse = "none".equals(a) ? null : ench(a);
                else {
                    Enchantment x = ench(a);
                    if (x != null) list.add(x);
                }
            }
            if (list.contains(e)) return curse;
        }
        return null;
    }

    public static final class Recipe {
        public final Enchantment in, out;
        final boolean levelUpgrade;
        public final int tokenCount;
        public Recipe cursing;
        public float curseChance;
        public boolean isCursing;

        Recipe(Enchantment in, Enchantment out, int tokenCount) {
            this.in = in;
            this.out = out;
            this.levelUpgrade = in == out;
            this.tokenCount = tokenCount;
        }

        public int outputLevel(int lvlIn) {
            if (isCursing) return 1;
            if (levelUpgrade) return lvlIn < in.getMaxLevel() ? lvlIn + 1 : DENY;
            if (TIER_LEVEL_MODE == 0) {
                int reduction = Math.min(TIER_LEVEL_REDUCTION, in.getMaxLevel() - out.getStartLevel());
                int n = lvlIn - reduction;
                return n >= out.getStartLevel() ? Math.min(n, out.getMaxLevel()) : DENY;
            }
            return lvlIn >= in.getMaxLevel() ? out.getStartLevel() : DENY;
        }

        public Recipe used(Random rand) {
            if (cursing != null && rand.nextFloat() < curseChance) return cursing;
            return this;
        }

        public boolean tokenValid(ItemStack token) {
            return !token.isEmpty() && token.getItem() == TOKEN && token.getCount() >= tokenCount;
        }

        /** UpgradeRecipe.canUpgrade(ItemStack) */
        public int canUpgrade(ItemStack stack) {
            Map<Enchantment, Integer> cur = EnchantmentManager.a(stack);
            outer:
            for (Map.Entry<Enchantment, Integer> en : cur.entrySet()) {
                int up = en.getKey() == in ? outputLevel(en.getValue()) : DENY;
                if (up == DENY) continue;
                if (stack.getItem() != Items.ENCHANTED_BOOK) {
                    if (!out.canEnchant(stack)) continue;
                } else if (!Enchanting.allowedOnBooks(out)) continue;
                if (ONLY_COMPATIBLE) {
                    for (Enchantment e : cur.keySet()) if (e != en.getKey() && !out.c(e)) continue outer;
                }
                return up;
            }
            return DENY;
        }

        /** UpgradeRecipe.getOutputStackEnchants */
        public Map<Enchantment, Integer> output(ItemStack stack) {
            Map<Enchantment, Integer> enchants = EnchantmentManager.a(stack);
            Map<Enchantment, Integer> out2 = new LinkedHashMap<>();
            boolean done = false;
            for (Map.Entry<Enchantment, Integer> en : enchants.entrySet()) {
                if (!done && in.equals(en.getKey())) {
                    if (!isCursing) out2.put(out, outputLevel(en.getValue()));
                    else if (!FAIL_REMOVES_ORIGINAL) out2.put(en.getKey(), en.getValue());
                    done = true;
                } else out2.put(en.getKey(), en.getValue());
            }
            if (isCursing) {
                int min = out.getStartLevel();
                if (out2.containsKey(out)) out2.put(out, MathHelper.clamp(out2.get(out) + 1, min, out.getMaxLevel()));
                else out2.put(out, min);
            }
            return out2;
        }
    }

    public static final List<Recipe> RECIPES = new ArrayList<>();

    /** UpgradeRecipe.initUpgradeRecipes */
    public static void init() {
        RECIPES.clear();
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Enchantment.enchantments) all.add(e);
        all.sort(Comparator.comparingInt(Enchantment::getId));
        for (Enchantment e : all) {
            Enchantment curse = failureOf(e);
            if (ALLOW_LEVEL && !e.isCursed() && e.getMaxLevel() > e.getStartLevel()) {
                Recipe r = new Recipe(e, e, TOKEN_LEVEL);
                setCurse(r, curse, FAIL_LEVEL);
                RECIPES.add(r);
            }
            if (ALLOW_TIER) {
                Enchantment up = upgradeOf(e);
                if (up != null) {
                    Recipe r = new Recipe(e, up, TOKEN_TIER);
                    setCurse(r, curse, FAIL_TIER);
                    RECIPES.add(r);
                }
            }
        }
    }

    private static void setCurse(Recipe r, Enchantment curse, float chance) {
        if (curse == null) return;
        Recipe c = new Recipe(r.in, curse, r.tokenCount);
        c.isCursing = true;
        r.cursing = c;
        r.curseChance = chance;
    }

    /** ContainerEnchantmentMixin: vanilla anvil level-cost multipliers by rarity */
    public static int rarityMultiplier(Enchantment.Rarity rarity, boolean book) {
        switch (rarity) {
            case COMMON: return 1;
            case UNCOMMON: return book ? 1 : 2;
            case RARE: return book ? 2 : 4;
            case VERY_RARE: return book ? 4 : 8;
            default: return 0;
        }
    }

    public static final class Option {
        public final Recipe recipe;
        public final int level, cost;
        public final boolean tokenOk;

        Option(Recipe recipe, int level, int cost, boolean tokenOk) {
            this.recipe = recipe;
            this.level = level;
            this.cost = cost;
            this.tokenOk = tokenOk;
        }
    }

    /** onCraftMatrixChanged upgrading branch: up to 3 distinct random recipes seeded with xpSeed */
    public static Option[] options(ItemStack target, ItemStack token, int xpSeed) {
        Option[] res = new Option[3];
        if (target.isEmpty()) return res;
        boolean enchantable = target.canEnchant();
        boolean upgradeable = (target.hasEnchantments() || target.getItem() == Items.ENCHANTED_BOOK) && (ALLOW_LEVEL || ALLOW_TIER);
        if (enchantable || !upgradeable) return res; // enchantable items use the normal enchanting table
        if (ONLY_BOOKS && target.getItem() != Items.ENCHANTED_BOOK) return res;
        List<Recipe> rs = new ArrayList<>();
        List<Integer> lv = new ArrayList<>();
        for (Recipe r : RECIPES) {
            int l = r.canUpgrade(target);
            if (l != DENY) {
                rs.add(r);
                lv.add(l);
            }
        }
        if (rs.isEmpty()) return res;
        Random rand = new Random((long) xpSeed);
        boolean book = target.getItem() == Items.ENCHANTED_BOOK;
        for (int i = 0; i < 3; i++) {
            if (rs.isEmpty()) break;
            int idx = rand.nextInt(rs.size());
            Recipe r = rs.remove(idx);
            int level = lv.remove(idx);
            int cost;
            switch (LEVEL_COST_MODE) {
                case 0: cost = 1; break;
                case 2: cost = r.out.a(level); break;
                default: cost = level * rarityMultiplier(r.out.e(), book);
            }
            cost = (int) (LEVEL_COST_MULTIPLIER * cost);
            res[i] = new Option(r, level, cost, r.tokenValid(token));
        }
        return res;
    }

    public static int repairCostAfter(int repairCost) {
        switch (ANVIL_REPAIR_MODE) {
            case 0: return repairCost;
            case 2: return (int) (repairCost + ANVIL_REPAIR_AMOUNT);
            case 3: return (int) (repairCost * ANVIL_REPAIR_AMOUNT);
            default: return 1 + 2 * repairCost;
        }
    }
}
