package chat.jaspr.atlas;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

/**
 * What is in Atlas's chests: the stuff of lives (bread in larders, paper in studies, tools in workshops, arms on the Line)
 * and of the Dominion (rations, trophies, ash-iron, orders), plus books from the chest's collection ("book:&lt;name&gt;"
 * extras), and the keys of knowledge in their shrines. Nothing valuable in bulk: the Concord's wealth is not in chests.
 */
final class Loot {
    private Loot() {}

    static void fill(AtlasPlugin plugin, Inventory inv, String key, String extras, Random r) {
        String k = key.startsWith("atlas:") ? key.substring(6) : key;
        switch (k) {
            case "larder": case "wayhouse": case "house_of_return": case "xenon":
                add(inv, r, Material.BREAD, 2, 6); add(inv, r, Material.APPLE, 1, 4); add(inv, r, Material.BAKED_POTATO, 0, 4); add(inv, r, Material.COOKED_FISH, 0, 3); add(inv, r, Material.MELON, 0, 5); break;
            case "granary": add(inv, r, Material.WHEAT, 6, 20); add(inv, r, Material.SEEDS, 4, 12); add(inv, r, Material.BREAD, 1, 3); break;
            case "household": add(inv, r, Material.STRING, 0, 5); add(inv, r, Material.PAPER, 0, 4); add(inv, r, Material.BREAD, 1, 3); add(inv, r, Material.TORCH, 0, 6); add(inv, r, Material.BOWL, 0, 2); break;
            case "study": case "scriptorium": case "school": case "synedrion": add(inv, r, Material.PAPER, 2, 8); add(inv, r, Material.FEATHER, 0, 3); add(inv, r, Material.INK_SACK, 0, 2); add(inv, r, Material.BOOK, 0, 2); break;
            case "depot": case "soldier": add(inv, r, Material.ARROW, 4, 16); add(inv, r, Material.BREAD, 1, 4); add(inv, r, Material.LEATHER, 0, 3); add(inv, r, Material.TORCH, 2, 8); break;
            case "workshop": case "smithy": case "mechaneion": add(inv, r, Material.IRON_NUGGET, 2, 9); add(inv, r, Material.COAL, 1, 6); add(inv, r, Material.FLINT, 0, 3); add(inv, r, Material.QUARTZ, 0, 6); break;
            case "apothecary": case "asklepieion": case "bath": potion(inv, r, PotionType.INSTANT_HEAL, 1, 2); potion(inv, r, PotionType.REGEN, 0, 1); add(inv, r, Material.GLASS_BOTTLE, 0, 3); add(inv, r, Material.SPECKLED_MELON, 0, 1); break;
            case "lumen": case "temple": case "hearth": add(inv, r, Material.GLOWSTONE_DUST, 2, 8); add(inv, r, Material.PRISMARINE_CRYSTALS, 0, 4); add(inv, r, Material.TORCH, 0, 6); break;
            case "weaver": add(inv, r, Material.WOOL, 2, 8); add(inv, r, Material.STRING, 2, 6); break;
            case "maps": case "map_room": add(inv, r, Material.PAPER, 2, 6); add(inv, r, Material.EMPTY_MAP, 1, 2); add(inv, r, Material.COMPASS, 0, 1); break;
            case "grocer": case "bakery": add(inv, r, Material.BREAD, 3, 8); add(inv, r, Material.CAKE, 0, 1); add(inv, r, Material.PUMPKIN_PIE, 0, 3); add(inv, r, Material.CARROT_ITEM, 0, 6); break;
            case "armoury_line": add(inv, r, Material.ARROW, 8, 24); add(inv, r, Material.SHIELD, 0, 1); add(inv, r, Material.STONE_SWORD, 0, 1); add(inv, r, Material.LEATHER_CHESTPLATE, 0, 1); add(inv, r, Material.BREAD, 2, 5); break;
            case "library_desk": case "oath_shrine": case "charter_vault": add(inv, r, Material.PAPER, 1, 4); break;
            // The Dominion.
            case "orc_stash": add(inv, r, Material.ROTTEN_FLESH, 2, 8); add(inv, r, Material.BONE, 1, 5); trophies(inv, r, 1, 2); add(inv, r, Material.STONE_AXE, 0, 1); break;
            case "dominion_supplies": case "wagon": add(inv, r, Material.BREAD, 1, 4); add(inv, r, Material.COAL, 2, 8); add(inv, r, Material.ARROW, 2, 10); ashIron(inv, r, 0, 3); emeralds(inv, r, 0, 2); break;
            case "kelani_hearth": add(inv, r, Material.LEATHER, 1, 4); add(inv, r, Material.BONE, 0, 3); add(inv, r, Material.STRING, 1, 4); add(inv, r, Material.SADDLE, 0, 1); break;
            case "forge": case "engine": case "forge_of_crowns": add(inv, r, Material.COAL, 3, 12); ashIron(inv, r, 1, 5); add(inv, r, Material.IRON_NUGGET, 1, 6); emeralds(inv, r, 0, 2); break;
            case "ruin_house": case "burnt_farm": case "outpost_ruin": case "pharos_ruin": add(inv, r, Material.PAPER, 0, 3); charcoal(inv, r, 1, 4); add(inv, r, Material.SEEDS, 0, 4); add(inv, r, Material.BOWL, 0, 1); emeralds(inv, r, 0, 1); break;
            case "sworn_desk": case "ledgers": case "edicts": case "orders": case "marshal": case "rider":
                add(inv, r, Material.PAPER, 2, 6); trophies(inv, r, 1, 2); emeralds(inv, r, 1, 3); break;
            case "ash_library": case "library_of_ash": add(inv, r, Material.PAPER, 0, 3); charcoal(inv, r, 0, 3); break;
            case "armoury": case "black_guard": add(inv, r, Material.ARROW, 6, 18); add(inv, r, Material.BOW, 0, 1); add(inv, r, Material.STONE_SWORD, 0, 1); trophies(inv, r, 1, 3); break;
            case "fort_hoard": ashIron(inv, r, 2, 6); trophies(inv, r, 2, 4); emeralds(inv, r, 2, 6); add(inv, r, Material.GOLDEN_APPLE, 0, 1); break;
            case "stilling": case "priest_cache": potion(inv, r, PotionType.REGEN, 0, 1); add(inv, r, Material.GLASS_BOTTLE, 1, 4); add(inv, r, Material.BLAZE_POWDER, 0, 2); trophies(inv, r, 0, 2); break;
            default: add(inv, r, Material.BREAD, 1, 3); add(inv, r, Material.PAPER, 0, 3);
        }
        if (extras == null) return;
        for (String part : extras.split(";")) {
            if (!part.startsWith("book:")) continue;
            String name = part.substring(5);
            ItemStack keyItem = Items.key(name);
            if (keyItem != null) { put(inv, r, keyItem); continue; }
            String coll = name.equals("orders") ? "orders" : name;
            int n = name.equals("great") || name.equals("archive") ? 2 : 1;
            for (int i = 0; i < n; i++) {
                LoreBooks.Book b = LoreBooks.pick(coll, r);
                if (b != null) put(inv, r, b.item());
            }
        }
    }

    private static void add(Inventory inv, Random r, Material m, int min, int max) {
        int n = min + (max > min ? r.nextInt(max - min + 1) : 0);
        if (n > 0) put(inv, r, new ItemStack(m, n));
    }

    @SuppressWarnings("deprecation")
    private static void potion(Inventory inv, Random r, PotionType type, int min, int max) {
        int n = min + (max > min ? r.nextInt(max - min + 1) : 0);
        for (int i = 0; i < n; i++) {
            ItemStack p = new ItemStack(Material.POTION);
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) p.getItemMeta();
            meta.setBasePotionData(new org.bukkit.potion.PotionData(type));
            p.setItemMeta(meta);
            put(inv, r, p);
        }
    }

    private static void charcoal(Inventory inv, Random r, int min, int max) { int n = min + r.nextInt(max - min + 1); if (n > 0) put(inv, r, new ItemStack(Material.COAL, n, (short) 1)); }
    private static void trophies(Inventory inv, Random r, int min, int max) { int n = min + r.nextInt(max - min + 1); for (int i = 0; i < n; i++) put(inv, r, Items.trophy(r)); }
    private static void ashIron(Inventory inv, Random r, int min, int max) { int n = min + r.nextInt(max - min + 1); if (n > 0) put(inv, r, Items.ashIron(n)); }
    private static void emeralds(Inventory inv, Random r, int min, int max) { int n = min + r.nextInt(max - min + 1); if (n > 0) put(inv, r, new ItemStack(Material.EMERALD, n)); }

    /** Into a random free slot (so chests look filled by hand, not stacked in a corner). */
    static void put(Inventory inv, Random r, ItemStack item) {
        int slot = r.nextInt(inv.getSize());
        for (int k = 0; k < inv.getSize() && inv.getItem(slot) != null; k++) slot = (slot + 1) % inv.getSize();
        if (inv.getItem(slot) == null) inv.setItem(slot, item);
    }
}
