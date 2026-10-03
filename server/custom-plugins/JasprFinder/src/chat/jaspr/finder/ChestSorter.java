package chat.jaspr.finder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The chest Sort button (owner, 2026-10-03: "a sorting button for chests that auto-organizes everything").
 *
 * Merges partial stacks of the same item (same type, damage and data, as Bukkit's isSimilar) and orders what is left by
 * creative tab, in the creative screen's order -- building blocks, decorations, redstone, transportation, miscellaneous (in
 * 1.12 that includes materials: ingots, diamonds, sticks), food, tools, combat, brewing, then anything without a tab -- then by item id, vanilla before a JasperCraft item made on the same base,
 * damage (variant, or wear: the freshest tool first), name, and the biggest stack first. Empty slots go to the end.
 * Nothing is ever split: a stack above its normal size (a plugin can make one) stays whole, so the result always fits.
 * The result is checked to hold exactly the same items before it is used; null means "leave the chest alone".
 */
final class ChestSorter {
    private static final Map<Material, Integer> TABS = new EnumMap<Material, Integer>(Material.class);
    private static Object[] order;

    private ChestSorter() { }

    /** building blocks, decorations, redstone, transportation, miscellaneous (l, with the materials), food, tools, combat,
     * brewing; f is a second "misc" tab 1.12 keeps but hardly uses. */
    private static Object[] order() {
        if (order == null) {
            order = new Object[]{net.minecraft.server.v1_12_R1.CreativeModeTab.b, net.minecraft.server.v1_12_R1.CreativeModeTab.c,
                net.minecraft.server.v1_12_R1.CreativeModeTab.d, net.minecraft.server.v1_12_R1.CreativeModeTab.e,
                net.minecraft.server.v1_12_R1.CreativeModeTab.l, net.minecraft.server.v1_12_R1.CreativeModeTab.h,
                net.minecraft.server.v1_12_R1.CreativeModeTab.i, net.minecraft.server.v1_12_R1.CreativeModeTab.j,
                net.minecraft.server.v1_12_R1.CreativeModeTab.k, net.minecraft.server.v1_12_R1.CreativeModeTab.f};
        }
        return order;
    }

    /** The material's place in the creative tab order (10: no tab). */
    static int tab(Material m) {
        Integer known = TABS.get(m);
        if (known != null) return known;
        int rank = 10;
        try {
            net.minecraft.server.v1_12_R1.Item item = org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers.getItem(m);
            Object tab = item == null ? null : item.b();
            Object[] tabs = order();
            for (int i = 0; i < tabs.length; i++) if (tabs[i] == tab) { rank = i; break; }
        } catch (RuntimeException | LinkageError unknown) {
            rank = 10;
        }
        TABS.put(m, rank);
        return rank;
    }

    static boolean custom(ItemStack s) {
        if (!s.hasItemMeta()) return false;
        ItemMeta meta = s.getItemMeta();
        return meta != null && meta.isUnbreakable();
    }

    static String name(ItemStack s) {
        if (!s.hasItemMeta()) return "";
        ItemMeta meta = s.getItemMeta();
        return meta != null && meta.hasDisplayName() ? FindRequest.clean(meta.getDisplayName()).toLowerCase(java.util.Locale.ROOT) : "";
    }

    @SuppressWarnings("deprecation")
    static final Comparator<ItemStack> ORDER = new Comparator<ItemStack>() {
        @Override public int compare(ItemStack a, ItemStack b) {
            int c = Integer.compare(tab(a.getType()), tab(b.getType()));
            if (c == 0) c = Integer.compare(a.getTypeId(), b.getTypeId());
            if (c == 0) c = Boolean.compare(custom(a), custom(b));
            if (c == 0) c = Integer.compare(a.getDurability(), b.getDurability());
            if (c == 0) c = name(a).compareTo(name(b));
            if (c == 0) c = Integer.compare(b.getAmount(), a.getAmount());
            return c;
        }
    };

    static boolean empty(ItemStack s) { return s == null || s.getType() == Material.AIR || s.getAmount() <= 0; }

    /** The sorted contents, the same length as the input, or null when they cannot be sorted safely. */
    static ItemStack[] sorted(ItemStack[] contents) {
        if (contents == null) return null;
        List<ItemStack> merged = new ArrayList<ItemStack>();
        for (ItemStack s : contents) {
            if (empty(s)) continue;
            int left = s.getAmount(), max = Math.max(1, s.getMaxStackSize());
            for (ItemStack m : merged) {
                if (left <= 0) break;
                if (!m.isSimilar(s)) continue;
                int room = max - m.getAmount();
                if (room <= 0) continue;
                int move = Math.min(room, left);
                m.setAmount(m.getAmount() + move);
                left -= move;
            }
            if (left > 0) {
                ItemStack rest = s.clone();
                rest.setAmount(left);
                merged.add(rest);
            }
        }
        if (merged.size() > contents.length) return null;
        Collections.sort(merged, ORDER);   // stable: equal items keep their order
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < merged.size(); i++) out[i] = merged.get(i);
        return same(contents, out) ? out : null;
    }

    /** True when both hold exactly the same items in the same amounts. */
    static boolean same(ItemStack[] a, ItemStack[] b) {
        List<ItemStack> kinds = new ArrayList<ItemStack>();
        List<long[]> totals = new ArrayList<long[]>();
        for (int side = 0; side < 2; side++) {
            for (ItemStack s : side == 0 ? a : b) {
                if (empty(s)) continue;
                int at = -1;
                for (int k = 0; k < kinds.size(); k++) if (kinds.get(k).isSimilar(s)) { at = k; break; }
                if (at < 0) { kinds.add(s); totals.add(new long[2]); at = kinds.size() - 1; }
                totals.get(at)[side] += s.getAmount();
            }
        }
        for (long[] t : totals) if (t[0] != t[1]) return false;
        return true;
    }
}
