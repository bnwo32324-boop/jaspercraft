package chat.jaspr.finder;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.ShulkerBox;

/**
 * One "find" request: from the recipe panel, "find &lt;item id&gt; &lt;damage&gt; &lt;exact&gt; &lt;title...&gt;"; or, for an item in the
 * player's own window (Shift + right click on a slot), built from the server's copy of that stack by {@link #of}.
 *
 * Matching follows what the panel shows. A JasperCraft item (exact = 1: an unbreakable item whose damage value picks its
 * model, like Survivor Gear or a realm armoury piece) matches only that exact item and damage. A vanilla tool or armour
 * piece matches whatever its wear, but never a JasperCraft item built on the same base. Anything else (planks, dyes,
 * wool ...) matches its exact damage value, which is how 1.12 tells those kinds apart. A shulker box carried in a chest is
 * searched too.
 */
final class FindRequest {
    static final int MAX_TITLE = 48;

    final Material material;
    final int damage;
    final boolean exact;
    final String title;

    FindRequest(Material material, int damage, boolean exact, String title) {
        this.material = material; this.damage = damage; this.exact = exact; this.title = title;
    }

    /** Parses the panel's message, or null when it is not a well-formed request for a real item. */
    static FindRequest parse(String text) {
        if (text == null || !text.startsWith("find ") || text.length() > 200) return null;
        String[] parts = text.split(" ", 5);
        if (parts.length < 4) return null;
        Material material = material(parts[1]);
        if (material == null || material == Material.AIR) return null;
        int damage;
        try { damage = Integer.parseInt(parts[2]); } catch (NumberFormatException bad) { return null; }
        if (damage < 0 || damage > 32767) return null;
        boolean exact = "1".equals(parts[3]);
        String title = parts.length > 4 ? clean(parts[4]) : "";
        if (title.isEmpty()) title = pretty(material);
        return new FindRequest(material, damage, exact, title);
    }

    /**
     * The request for a stack the player points at in their own window (Shift + right click, Find): the same kind of match
     * the crafting panel asks for. An unbreakable item is a JasperCraft model item and matches that exact item and damage; a
     * vanilla tool or armour piece matches whatever its wear; anything else matches its damage value. The title is what the
     * player's client calls the item (colour codes and control characters stripped), else the item's own name, else the
     * material. Null for an empty stack.
     */
    @SuppressWarnings("deprecation")
    static FindRequest of(ItemStack stack, String clientTitle) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return null;
        Material material = stack.getType();
        boolean unbreakable = false;
        String named = "";
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                unbreakable = meta.isUnbreakable();
                if (meta.hasDisplayName()) named = clean(meta.getDisplayName());
            }
        }
        boolean wears = material.getMaxDurability() > 0 && !unbreakable;
        int damage = wears ? 0 : Math.max(0, Math.min(32767, (int) stack.getDurability()));
        String title = clientTitle == null ? "" : clean(clientTitle);
        if (title.isEmpty()) title = named;
        if (title.isEmpty()) title = pretty(material);
        return new FindRequest(material, damage, unbreakable, title);
    }

    /** A registry id ("minecraft:diamond_pickaxe") as its Bukkit material, or null. */
    @SuppressWarnings("deprecation")
    static Material material(String id) {
        if (id == null || id.length() > 64 || !id.matches("[a-z0-9_]+:[a-z0-9_./]+")) return null;
        try {
            net.minecraft.server.v1_12_R1.Item item = net.minecraft.server.v1_12_R1.Item.REGISTRY.get(new net.minecraft.server.v1_12_R1.MinecraftKey(id));
            if (item == null) return null;
            return org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers.getMaterial(item);
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
    }

    /** Printable title: no colour codes, no control characters, bounded. */
    static String clean(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < MAX_TITLE; i++) {
            char c = raw.charAt(i);
            if (c == '§') { i++; continue; }
            if (c >= 32 && c != 127) out.append(c);
        }
        return out.toString().trim();
    }

    static String pretty(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) { if (out.length() > 0) out.append(' '); out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)); }
        return out.toString();
    }

    /** True when this stack is (or, for a shulker box, holds) the requested item. */
    boolean matches(ItemStack stack) {
        return matches(stack, 0);
    }

    @SuppressWarnings("deprecation")
    private boolean matches(ItemStack stack, int depth) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return false;
        if (stack.getType() == material && sameKind(stack)) return true;
        if (depth == 0 && stack.getType().name().endsWith("SHULKER_BOX") && stack.hasItemMeta() && stack.getItemMeta() instanceof BlockStateMeta) {
            try {
                BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
                if (meta.hasBlockState() && meta.getBlockState() instanceof ShulkerBox)
                    for (ItemStack inner : ((ShulkerBox) meta.getBlockState()).getInventory().getContents())
                        if (matches(inner, depth + 1)) return true;
            } catch (RuntimeException ignored) {
                // an unreadable box is simply not a match
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean sameKind(ItemStack stack) {
        boolean unbreakable = false;
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            unbreakable = meta != null && meta.isUnbreakable();
        }
        if (exact) return unbreakable && stack.getDurability() == damage;
        if (material.getMaxDurability() > 0) return !unbreakable;   // any wear, but never a JasperCraft item on this base
        return stack.getDurability() == damage;
    }
}
