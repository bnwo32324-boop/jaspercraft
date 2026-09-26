package chat.jaspr.lostcities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The mod's own loot tables (lostcities:chests/lostcitychest and raildungeonchest), ported to Bukkit items with the
 * vanilla LootTable semantics: uniform rolls, weighted entries, set_count, and vanilla's spread of the result
 * into random empty slots. Vanilla tables (minecraft:chests/...) are not ported: the chest is given the table
 * itself, exactly as the mod did, and vanilla fills it when first opened.
 */
final class Loot {
    private Loot() {}

    private static final class Entry {
        final Material item; final int weight, min, max;
        Entry(Material item, int weight, int min, int max) { this.item = item; this.weight = weight; this.min = min; this.max = max; }
    }

    private static final Entry[] LOST_CITY_CHEST = {
        new Entry(Material.DIAMOND_SWORD, 2, 0, 1),
        new Entry(Material.GOLD_INGOT, 5, 1, 5),
        new Entry(Material.DIAMOND, 5, 1, 5),
        new Entry(Material.EMERALD, 5, 1, 3),
    };

    private static final Entry[] RAIL_DUNGEON_CHEST = {
        new Entry(Material.DIAMOND_AXE, 2, 0, 1),
        new Entry(Material.GOLD_INGOT, 5, 1, 5),
        new Entry(Material.IRON_INGOT, 5, 3, 10),
        new Entry(Material.DIAMOND, 5, 2, 9),
        new Entry(Material.EMERALD, 5, 2, 5),
    };

    static boolean isOwnTable(String table) {
        return "lostcities:chests/lostcitychest".equals(table) || "lostcities:chests/raildungeonchest".equals(table);
    }

    /** Fill a live inventory from one of the mod's tables. Returns the number of stacks placed. */
    static int fill(Inventory inv, String table, Random rand) {
        Entry[] entries = "lostcities:chests/raildungeonchest".equals(table) ? RAIL_DUNGEON_CHEST : LOST_CITY_CHEST;
        List<ItemStack> items = new ArrayList<>();
        int rolls = 2 + rand.nextInt(3);   // "rolls": {"min": 2, "max": 4}
        int total = 0;
        for (Entry e : entries) total += e.weight;
        for (int r = 0; r < rolls; r++) {
            int pick = rand.nextInt(total);
            for (Entry e : entries) {
                pick -= e.weight;
                if (pick < 0) {
                    int count = e.min + rand.nextInt(e.max - e.min + 1);
                    if (count > 0) items.add(new ItemStack(e.item, count));
                    break;
                }
            }
        }
        return spread(inv, items, rand);
    }

    /** LootTable.fillInventory: shuffle into random empty slots, splitting stacks to use spare slots. */
    private static int spread(Inventory inv, List<ItemStack> stacks, Random rand) {
        List<Integer> empty = new ArrayList<>();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) if (contents[i] == null || contents[i].getType() == Material.AIR) empty.add(i);
        Collections.shuffle(empty, rand);
        List<ItemStack> splittable = new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.getAmount() > 1) splittable.add(s); else result.add(s);
        }
        int slots = empty.size() - stacks.size();
        while (slots > 0 && !splittable.isEmpty()) {
            ItemStack s = splittable.remove(rand.nextInt(splittable.size()));
            int half = 1 + rand.nextInt(s.getAmount() / 2);
            ItemStack part = s.clone();
            part.setAmount(half);
            s.setAmount(s.getAmount() - half);
            if (half > 1 && rand.nextBoolean()) splittable.add(part); else result.add(part);
            if (s.getAmount() > 1 && rand.nextBoolean()) splittable.add(s); else result.add(s);
            slots--;
        }
        result.addAll(splittable);
        Collections.shuffle(result, rand);
        int placed = 0;
        for (ItemStack s : result) {
            if (empty.isEmpty()) break;
            inv.setItem(empty.remove(empty.size() - 1), s);   // live inventory: never BlockState.update() after this
            placed++;
        }
        return placed;
    }
}
