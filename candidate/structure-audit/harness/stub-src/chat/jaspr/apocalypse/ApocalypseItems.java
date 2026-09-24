package chat.jaspr.apocalypse;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Capture-harness "stub" shim: stands in for the JasprApocalypse item factory (placeholder items). */
public final class ApocalypseItems {
    private ApocalypseItems() { }
    public static ItemStack expedition(String id, int tier) {
        ItemStack item = new ItemStack(Material.STICK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("[stub " + id + " t" + tier + "]");
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack gear(String id) { return expedition(id, 1); }
    public static java.util.Map<String, String> catalogue(String category) { return java.util.Collections.emptyMap(); }
}
