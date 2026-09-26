package chat.jaspr.biomes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Enchanted books in loot always carry an enchantment (owner, 2026-09-25: "they can't be empty").
 *
 * Dungeons.roll() used to hand out a bare ENCHANTED_BOOK. It now calls {@link #random}, which draws from its own
 * source so the chest's Random -- possibly a builder stream -- is never advanced. Books that were already rolled
 * blank (or come from anywhere else, e.g. imported designs) are enchanted the first time the container is opened,
 * seeded by the container's position so the same chest always yields the same book.
 */
final class LootBooks implements Listener {
    /** Every non-curse enchantment, in a stable order. */
    private static final Enchantment[] POOL;
    static {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Enchantment.values()) {
            // Curses (vanilla and So Many Enchantments) and SME's own loot blacklist never appear as loot books.
            if (e == null || e.isCursed() || SmeLoot.blacklisted(e)) continue;
            all.add(e);
        }
        all.sort(Comparator.comparing(Enchantment::getName));
        POOL = all.toArray(new Enchantment[0]);
    }

    private final Plugin plugin;
    private long fixed;

    LootBooks(Plugin plugin) { this.plugin = plugin; }

    void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    void stop() { HandlerList.unregisterAll(this); }

    /** A fresh book with one random stored enchantment at a random valid level. */
    static ItemStack random(Random r) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        enchant(book, r);
        return book;
    }

    static boolean blank(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return !(meta instanceof EnchantmentStorageMeta) || !((EnchantmentStorageMeta) meta).hasStoredEnchants();
    }

    static void enchant(ItemStack book, Random r) {
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta) || POOL.length == 0) return;
        Enchantment e = POOL[r.nextInt(POOL.length)];
        ((EnchantmentStorageMeta) meta).addStoredEnchant(e, 1 + r.nextInt(Math.max(1, e.getMaxLevel())), false);
        book.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        if (inv == null || inv.getHolder() instanceof HumanEntity) return; // only containers, never a player's own items
        ItemStack[] items = inv.getContents();
        Location at = inv.getLocation();
        long seed = at == null ? 0L : (at.getBlockX() * 3129871L) ^ (at.getBlockZ() * 116129781L) ^ (long) at.getBlockY();
        int n = 0;
        for (int slot = 0; slot < items.length; slot++) {
            if (!blank(items[slot])) continue;
            enchant(items[slot], new Random(seed * 31L + slot));
            inv.setItem(slot, items[slot]);
            n++;
        }
        if (n > 0) {
            fixed += n;
            plugin.getLogger().info("LOOT_BOOKS_FIXED books=" + n + " total=" + fixed);
        }
    }
}
