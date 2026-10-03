package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * What the Backrooms hide. Loot grows better the further in it lies ({@link Level#danger}, 0 at the Threshold to 1 at
 * the Lifeguard): more and rarer supplies, more valuables, and a growing chance of the level's own gear (armour pieces,
 * tools and weapons, trinkets). Almond Water turns up everywhere. The table key ("room", "crate", "tunnel", "panel",
 * "desk", "lobby", "pool") only flavours what else a chest holds.
 */
final class Loot {
    private Loot() {}

    static void fill(Inventory inv, int levelNumber, double danger, String table, Random r) {
        Level lv = Level.of(levelNumber);
        if (lv == null) return;
        List<ItemStack> out = roll(lv, danger, table, r);
        int size = inv.getSize();
        for (ItemStack s : out) {
            for (int tries = 0; tries < 8; tries++) {
                int slot = r.nextInt(size);
                if (inv.getItem(slot) == null) { inv.setItem(slot, s); break; }
            }
        }
        ArmoryLoot.add(inv, r, "jaspr_levels", (int) Math.round(Math.max(0.0, Math.min(1.0, danger)) * 5));
    }

    /** A chest's contents (pure: the same danger and random give the same items). */
    static List<ItemStack> roll(Level lv, double g, String table, Random r) {
        List<ItemStack> out = new ArrayList<>();
        int rolls = 3 + (int) Math.round(4 * g) + r.nextInt(3);
        for (int i = 0; i < rolls; i++) out.add(supply(lv, g, table, r));
        if (r.nextDouble() < 0.45 + 0.3 * g) out.add(Items.make("almond_water"));
        if (r.nextDouble() < 0.1 + 0.55 * g) out.add(valuable(g, r));
        // The level's own gear: rare near the Threshold, common at the far end.
        double gear = 0.05 + 0.4 * g;
        if (r.nextDouble() < gear) out.add(gear(lv, r, g));
        if (r.nextDouble() < 0.04 + 0.2 * g) out.add(book(g, r));
        return out;
    }

    static ItemStack gear(Level lv, Random r, double g) {
        List<Items.Def> defs = Items.of(lv);
        // Trinkets and weapons are rarer than armour and tools.
        for (int tries = 0; tries < 4; tries++) {
            Items.Def d = defs.get(r.nextInt(defs.size()));
            boolean rare = d.kind == Items.Kind.TRINKET || d.kind == Items.Kind.WEAPON;
            if (!rare || r.nextDouble() < 0.45 + 0.4 * g) return Items.make(d);
        }
        return Items.make(defs.get(0));
    }

    private static ItemStack supply(Level lv, double g, String table, Random r) {
        double x = r.nextDouble();
        switch (table) {
            case "crate":
                if (x < 0.3) return new ItemStack(Material.STRING, 2 + r.nextInt(6));
                if (x < 0.5) return new ItemStack(Material.PAPER, 3 + r.nextInt(8));
                break;
            case "tunnel":
                if (x < 0.3) return new ItemStack(Material.COAL, 3 + r.nextInt(8));
                if (x < 0.45) return new ItemStack(Material.IRON_NUGGET, 4 + r.nextInt(10));
                break;
            case "panel":
                if (x < 0.3) return new ItemStack(Material.REDSTONE, 4 + r.nextInt(10));
                if (x < 0.45) return new ItemStack(Material.GLOWSTONE_DUST, 2 + r.nextInt(6));
                break;
            case "desk":
                if (x < 0.25) return new ItemStack(Material.PAPER, 4 + r.nextInt(10));
                if (x < 0.4) return new ItemStack(Material.BOOK, 1 + r.nextInt(3));
                break;
            case "lobby":
                if (x < 0.25) return new ItemStack(Material.COOKED_BEEF, 2 + r.nextInt(4));
                if (x < 0.4) return new ItemStack(Material.GOLD_NUGGET, 4 + r.nextInt(10));
                break;
            case "pool":
                if (x < 0.25) return new ItemStack(Material.PRISMARINE_SHARD, 2 + r.nextInt(6));
                if (x < 0.4) return new ItemStack(Material.PRISMARINE_CRYSTALS, 2 + r.nextInt(5));
                break;
            default:
                break;
        }
        double y = r.nextDouble();
        if (y < 0.18) return new ItemStack(Material.BREAD, 2 + r.nextInt(4));
        if (y < 0.30) return new ItemStack(Material.TORCH, 4 + r.nextInt(12));
        if (y < 0.40) return new ItemStack(Material.ARROW, 6 + r.nextInt(12));
        if (y < 0.50) return new ItemStack(Material.COOKED_CHICKEN, 1 + r.nextInt(4));
        if (y < 0.58) return new ItemStack(Material.IRON_INGOT, 1 + r.nextInt(3 + (int) (4 * g)));
        if (y < 0.64) return new ItemStack(Material.GOLD_INGOT, 1 + r.nextInt(2 + (int) (4 * g)));
        if (y < 0.70) return new ItemStack(Material.EXP_BOTTLE, 1 + r.nextInt(3 + (int) (6 * g)));
        if (y < 0.76) return new ItemStack(Material.GOLDEN_CARROT, 1 + r.nextInt(3));
        if (y < 0.82) return new ItemStack(Material.ENDER_PEARL, 1 + r.nextInt(2));
        if (y < 0.88) return potion(g, r);
        if (y < 0.94) return new ItemStack(Material.GRILLED_PORK, 2 + r.nextInt(3));
        return new ItemStack(Material.SNOW_BALL, 4 + r.nextInt(8));
    }

    private static ItemStack valuable(double g, Random r) {
        double x = r.nextDouble();
        if (x < 0.35 - 0.2 * g) return new ItemStack(Material.EMERALD, 1 + r.nextInt(3 + (int) (5 * g)));
        if (x < 0.6) return new ItemStack(Material.DIAMOND, 1 + r.nextInt(1 + (int) (3 * g)));
        if (x < 0.75) return new ItemStack(Material.GOLDEN_APPLE, 1);
        if (x < 0.85 && g > 0.5) return new ItemStack(Material.GOLDEN_APPLE, 1, (short) 1);
        if (x < 0.92 && g > 0.35) return new ItemStack(Material.TOTEM);
        return new ItemStack(Material.LAPIS_BLOCK, 1 + r.nextInt(2));
    }

    private static ItemStack potion(double g, Random r) {
        org.bukkit.potion.PotionType[] kinds = {org.bukkit.potion.PotionType.INSTANT_HEAL, org.bukkit.potion.PotionType.REGEN,
            org.bukkit.potion.PotionType.STRENGTH, org.bukkit.potion.PotionType.FIRE_RESISTANCE, org.bukkit.potion.PotionType.SPEED};
        ItemStack s = new ItemStack(r.nextDouble() < 0.3 ? Material.SPLASH_POTION : Material.POTION);
        org.bukkit.inventory.meta.PotionMeta m = (org.bukkit.inventory.meta.PotionMeta) s.getItemMeta();
        org.bukkit.potion.PotionType type = kinds[r.nextInt(kinds.length)];
        m.setBasePotionData(new org.bukkit.potion.PotionData(type, false, type.isUpgradeable() && g > 0.6 && r.nextBoolean()));
        s.setItemMeta(m);
        return s;
    }

    private static ItemStack book(double g, Random r) {
        Enchantment[] pool = {Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.DAMAGE_ALL, Enchantment.DURABILITY, Enchantment.DIG_SPEED,
            Enchantment.ARROW_DAMAGE, Enchantment.PROTECTION_FALL, Enchantment.MENDING, Enchantment.LOOT_BONUS_MOBS, Enchantment.FIRE_ASPECT};
        Enchantment e = pool[r.nextInt(pool.length)];
        int level = Math.max(1, Math.min(e.getMaxLevel(), 1 + (int) Math.floor(g * e.getMaxLevel() + r.nextDouble())));
        ItemStack s = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta m = (EnchantmentStorageMeta) s.getItemMeta();
        m.addStoredEnchant(e, level, true);
        s.setItemMeta(m);
        return s;
    }

    /** A boss's hoard: two pieces of its level's gear (first win: three), valuables and supplies. */
    static List<ItemStack> hoard(Level lv, boolean first, Random r) {
        double g = (lv.index() + 1.0) / Level.ALL.length;
        List<ItemStack> out = new ArrayList<>();
        List<Items.Def> defs = Items.of(lv);
        List<Items.Def> pool = new ArrayList<>(defs);
        int pieces = first ? 3 : 2;
        for (int i = 0; i < pieces && !pool.isEmpty(); i++) out.add(Items.make(pool.remove(r.nextInt(pool.size()))));
        out.add(valuable(g, r));
        out.add(valuable(g, r));
        for (int i = 0; i < 2; i++) out.add(Items.make("almond_water"));
        out.add(new ItemStack(Material.EXP_BOTTLE, 4 + (int) (12 * g)));
        return out;
    }
}
