package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Chest loot ported from the tables the two mods place: vanilla chests/nether_bridge (BetterNether cave room, and the
 * first pool of NetherEx temple_rare), NetherEx chest/base_temple (second pool of temple_rare) and chest/base_village.
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

    static List<ItemStack> roll(String table, Random r) {
        List<ItemStack> out = new ArrayList<>();
        switch (table) {
            case "netherex:chest/temple_rare": NETHER_BRIDGE.roll(r, out); BASE_TEMPLE.roll(r, out); break;
            case "netherex:chest/base_temple": BASE_TEMPLE.roll(r, out); break;
            case "netherex:chest/base_village": BASE_VILLAGE.roll(r, out); break;
            default: NETHER_BRIDGE.roll(r, out);
        }
        return out;
    }

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
