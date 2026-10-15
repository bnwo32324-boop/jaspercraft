package chat.jaspr.lairs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * What the lairs' chests hold. Supply chests (tents, caches, digs, the armoury) carry the lair's theme and plain survival
 * goods; the vault behind the seal carries the reward for the boss: gems, gold, a strong enchanted book or two, an
 * enchanted weapon or tool of the mutant's kind, experience, and the lair's own written account. Nothing is placed as a
 * block of value (owner rule: no valuable blocks in structures).
 */
final class Loot {
    private Loot() {}

    static void fill(Inventory inv, String table, Design d, Random r) {
        List<ItemStack> items = table.endsWith("_vault") ? vault(d, r) : supplies(table, d, r);
        int size = inv.getSize();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) slots.add(i);
        java.util.Collections.shuffle(slots, r);
        int k = 0;
        for (ItemStack it : items) { if (k >= slots.size()) break; inv.setItem(slots.get(k++), it); }
    }

    private static ItemStack it(Material m, int n) { return new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n))); }
    private static ItemStack it(Material m, int n, int data) { ItemStack s = new ItemStack(m, Math.max(1, n), (short) data); return s; }
    private static int between(Random r, int a, int b) { return a + r.nextInt(b - a + 1); }

    static List<ItemStack> supplies(String table, Design d, Random r) {
        List<ItemStack> out = new ArrayList<>();
        out.add(it(Material.BREAD, between(r, 1, 4)));
        if (r.nextBoolean()) out.add(it(Material.TORCH, between(r, 4, 12)));
        if (r.nextInt(3) == 0) out.add(it(Material.IRON_INGOT, between(r, 1, 4)));
        if (r.nextInt(3) == 0) out.add(it(Material.GOLD_NUGGET, between(r, 3, 12)));
        if (r.nextInt(4) == 0) out.add(it(Material.EMERALD, between(r, 1, 3)));
        if (r.nextInt(4) == 0) out.add(potion(r.nextBoolean() ? PotionType.INSTANT_HEAL : PotionType.REGEN));
        if (r.nextInt(6) == 0) out.add(it(Material.EXP_BOTTLE, between(r, 2, 6)));
        if (r.nextInt(8) == 0) out.add(book(r, false));
        if (r.nextInt(10) == 0) out.add(it(Material.GOLDEN_APPLE, 1));
        if (r.nextInt(10) == 0) out.add(it(Material.NAME_TAG, 1));
        if (r.nextInt(10) == 0) out.add(it(Material.SADDLE, 1));
        switch (d.mutant) {
            case "mutant_zombie":
                out.add(it(Material.ROTTEN_FLESH, between(r, 3, 12)));
                if (r.nextBoolean()) out.add(it(Material.PAPER, between(r, 2, 6)));
                if (r.nextInt(3) == 0) out.add(it(Material.COOKED_BEEF, between(r, 2, 5)));
                if (table.contains("tent") && r.nextBoolean()) out.add(it(Material.GLASS_BOTTLE, between(r, 2, 5)));
                break;
            case "mutant_skeleton":
                out.add(it(Material.BONE, between(r, 4, 14)));
                out.add(it(Material.ARROW, between(r, 8, 24)));
                if (r.nextInt(3) == 0) out.add(it(Material.BOW, 1));
                if (table.contains("armory")) { out.add(it(Material.IRON_SWORD, 1)); if (r.nextBoolean()) out.add(it(Material.CHAINMAIL_CHESTPLATE, 1)); if (r.nextBoolean()) out.add(it(Material.SHIELD, 1)); }
                if (table.contains("dig")) { out.add(it(Material.IRON_PICKAXE, 1)); out.add(it(Material.IRON_SPADE, 1)); }
                break;
            case "mutant_creeper":
                out.add(it(Material.SULPHUR, between(r, 3, 10)));
                if (r.nextBoolean()) out.add(it(Material.FLINT_AND_STEEL, 1));
                if (r.nextInt(3) == 0) out.add(it(Material.SEEDS, between(r, 4, 12)));
                if (r.nextInt(3) == 0) out.add(it(Material.MELON_SEEDS, between(r, 2, 6)));
                if (r.nextInt(4) == 0) out.add(it(Material.TNT, between(r, 1, 3)));
                break;
            default:
                out.add(it(Material.ENDER_PEARL, between(r, 1, 3)));
                if (r.nextBoolean()) out.add(it(Material.BOOK, between(r, 1, 4)));
                if (r.nextInt(3) == 0) out.add(it(Material.CHORUS_FRUIT, between(r, 2, 6)));
                if (r.nextInt(4) == 0) out.add(it(Material.PAPER, between(r, 3, 9)));
                break;
        }
        return out;
    }

    static List<ItemStack> vault(Design d, Random r) {
        List<ItemStack> out = new ArrayList<>();
        out.add(it(Material.DIAMOND, between(r, 2, 5)));
        out.add(it(Material.EMERALD, between(r, 4, 10)));
        out.add(it(Material.GOLD_INGOT, between(r, 3, 9)));
        out.add(it(Material.EXP_BOTTLE, between(r, 8, 16)));
        out.add(it(Material.GOLDEN_APPLE, between(r, 1, 3)));
        if (r.nextInt(8) == 0) out.add(it(Material.GOLDEN_APPLE, 1, 1));
        if (r.nextInt(6) == 0) out.add(it(Material.TOTEM, 1));
        out.add(book(r, true));
        if (r.nextBoolean()) out.add(book(r, true));
        out.add(potion(PotionType.INSTANT_HEAL));
        out.add(weapon(d, r));
        out.add(lore(d));
        switch (d.mutant) {
            case "mutant_zombie": out.add(it(Material.ROTTEN_FLESH, between(r, 8, 20))); out.add(it(Material.IRON_INGOT, between(r, 4, 10))); break;
            case "mutant_skeleton": out.add(it(Material.SPECTRAL_ARROW, between(r, 8, 24))); out.add(it(Material.BONE_BLOCK, between(r, 2, 5))); break;
            case "mutant_creeper": out.add(it(Material.TNT, between(r, 3, 8))); out.add(it(Material.SULPHUR, between(r, 8, 20))); break;
            default: out.add(it(Material.ENDER_PEARL, between(r, 4, 10))); out.add(it(Material.EYE_OF_ENDER, between(r, 1, 3))); break;
        }
        return out;
    }

    private static ItemStack potion(PotionType type) {
        ItemStack p = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) p.getItemMeta();
        m.setBasePotionData(new PotionData(type, false, type == PotionType.INSTANT_HEAL));
        p.setItemMeta(m);
        return p;
    }

    private static final List<Enchantment> STRONG = Arrays.asList(Enchantment.DAMAGE_ALL, Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.ARROW_DAMAGE,
        Enchantment.DURABILITY, Enchantment.DIG_SPEED, Enchantment.LOOT_BONUS_MOBS, Enchantment.LOOT_BONUS_BLOCKS, Enchantment.PROTECTION_PROJECTILE,
        Enchantment.FIRE_ASPECT, Enchantment.ARROW_INFINITE, Enchantment.SWEEPING_EDGE, Enchantment.MENDING, Enchantment.DAMAGE_UNDEAD, Enchantment.PROTECTION_EXPLOSIONS);

    private static ItemStack book(Random r, boolean strong) {
        ItemStack b = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta m = (EnchantmentStorageMeta) b.getItemMeta();
        Enchantment e = STRONG.get(r.nextInt(STRONG.size()));
        int level = strong ? Math.max(1, e.getMaxLevel() - r.nextInt(2)) : 1 + r.nextInt(Math.max(1, e.getMaxLevel() / 2));
        m.addStoredEnchant(e, Math.min(level, e.getMaxLevel()), true);
        b.setItemMeta(m);
        return b;
    }

    /** An enchanted weapon or tool suited to the mutant: an axe from the zombie, a bow from the skeleton ... */
    private static ItemStack weapon(Design d, Random r) {
        ItemStack w;
        switch (d.mutant) {
            case "mutant_zombie":
                w = new ItemStack(r.nextBoolean() ? Material.DIAMOND_AXE : Material.DIAMOND_SWORD);
                w.addUnsafeEnchantment(Enchantment.DAMAGE_UNDEAD, 3 + r.nextInt(3));
                w.addUnsafeEnchantment(Enchantment.DURABILITY, 2 + r.nextInt(2));
                break;
            case "mutant_skeleton":
                w = new ItemStack(Material.BOW);
                w.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 3 + r.nextInt(3));
                w.addUnsafeEnchantment(r.nextBoolean() ? Enchantment.ARROW_KNOCKBACK : Enchantment.ARROW_FIRE, 1 + r.nextInt(2));
                w.addUnsafeEnchantment(Enchantment.DURABILITY, 2 + r.nextInt(2));
                break;
            case "mutant_creeper":
                w = new ItemStack(r.nextBoolean() ? Material.DIAMOND_CHESTPLATE : Material.DIAMOND_LEGGINGS);
                w.addUnsafeEnchantment(Enchantment.PROTECTION_EXPLOSIONS, 3 + r.nextInt(2));
                w.addUnsafeEnchantment(Enchantment.DURABILITY, 2 + r.nextInt(2));
                break;
            default:
                w = new ItemStack(r.nextBoolean() ? Material.DIAMOND_SWORD : Material.DIAMOND_PICKAXE);
                w.addUnsafeEnchantment(w.getType() == Material.DIAMOND_SWORD ? Enchantment.DAMAGE_ALL : Enchantment.DIG_SPEED, 3 + r.nextInt(3));
                w.addUnsafeEnchantment(Enchantment.DURABILITY, 2 + r.nextInt(2));
                if (r.nextInt(3) == 0) w.addUnsafeEnchantment(Enchantment.MENDING, 1);
                break;
        }
        return w;
    }

    /** The lair's written account, signed by nobody. */
    static ItemStack lore(Design d) {
        ItemStack b = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) b.getItemMeta();
        m.setTitle(d.title.length() > 32 ? d.title.substring(0, 32) : d.title);
        m.setAuthor("Unknown");
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder(d.title + "\n\n");
        for (String sign : d.lore.split("~")) page.append(sign.replace('|', ' ')).append("\n\n");
        pages.add(page.toString());
        pages.add("Whatever lived here was once a " + d.mutantName().replace("Mutant ", "").toLowerCase() + ". Then it drank or breathed something that should "
            + "not exist, and it grew. They called it " + d.boss + ".\n\nIt is quiet now. For a while.");
        m.setPages(pages);
        b.setItemMeta(m);
        return b;
    }
}
