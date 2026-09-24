package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The upgrade sheet, drawn as a chest.
 *
 * A vanilla container is the only interface the browser client can render, which turns out to suit
 * this well: one slot per stat, the cost written on it in experience levels, and a click to buy.
 */
final class StatsMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_GREEN + "Stats";
    private static final Material[] ICONS = {
            Material.DIAMOND_PICKAXE, Material.DIAMOND_SPADE, Material.DIAMOND_AXE, Material.SHEARS,
            Material.DIAMOND_CHESTPLATE, Material.ANVIL, Material.LEATHER_CHESTPLATE, Material.FEATHER,
            Material.GOLD_BOOTS, Material.RABBIT_FOOT, Material.WATER_BUCKET, Material.LADDER,
            Material.SKULL_ITEM, Material.DIAMOND_SWORD, Material.BOW, Material.ROTTEN_FLESH,
            Material.FURNACE, Material.CHEST, Material.EYE_OF_ENDER, Material.SHIELD,
            Material.EMERALD, Material.GOLDEN_APPLE, Material.LEATHER_BOOTS
    };

    private final RpgPlugin plugin;

    StatsMenu(RpgPlugin plugin) { this.plugin = plugin; }

    void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 36, TITLE);
        render(player, inventory);
        player.openInventory(inventory);
    }

    private void render(Player player, Inventory inventory) {
        PlayerStats stats = plugin.stats().get(player);
        RpgConfig settings = plugin.settings();
        StatType[] all = StatType.values();

        for (int i = 0; i < all.length && i < 27; i++) {
            inventory.setItem(i, icon(all[i], ICONS[i % ICONS.length], stats, settings, player));
        }

        ItemStack purse = new ItemStack(Material.EXP_BOTTLE);
        ItemMeta meta = purse.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "You have " + ChatColor.WHITE + player.getLevel()
                + ChatColor.GREEN + " experience level(s)");
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Upgrades are paid for in experience levels.");
        lore.add(ChatColor.GRAY + "Total levels bought: " + ChatColor.WHITE + stats.totalLevels());
        if (settings.resetOnDeath) {
            lore.add(ChatColor.DARK_RED + "Everything resets when you die.");
        }
        meta.setLore(lore);
        purse.setItemMeta(meta);
        inventory.setItem(31, purse);
    }

    private ItemStack icon(StatType stat, Material material, PlayerStats stats, RpgConfig settings, Player player) {
        int level = stats.level(stat);
        int cap = settings.capFor(stat);
        boolean maxed = level >= cap;
        int cost = StatCosts.cost(level, settings.costMultiplier);

        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, level == 0 ? 1 : level)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((maxed ? ChatColor.GOLD : ChatColor.YELLOW) + stat.display
                + ChatColor.GRAY + "  " + level + "/" + cap);

        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + stat.description);
        lore.add("");
        double current = stats.multiplier(stat, settings);
        lore.add(ChatColor.DARK_GRAY + "Now: " + ChatColor.WHITE + format(stat, level, current));
        if (!maxed) {
            double next = 1.0d + (stat.multiplier(level + 1) - 1.0d) * settings.bonusMultiplier;
            lore.add(ChatColor.DARK_GRAY + "Next: " + ChatColor.WHITE + format(stat, level + 1, next));
            lore.add("");
            boolean affordable = player.getLevel() >= cost;
            lore.add((affordable ? ChatColor.GREEN : ChatColor.RED) + "Cost: " + cost + " experience level(s)");
            lore.add(affordable ? ChatColor.GRAY + "Click to buy." : ChatColor.DARK_GRAY + "Not enough experience.");
        } else {
            lore.add("");
            lore.add(ChatColor.GOLD + "Fully trained.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String format(StatType stat, int level, double multiplier) {
        if (stat == StatType.HEALTH) {
            double hearts = level * plugin.settings().healthPerLevel / 2.0d;
            return level == 0 ? "no bonus" : "+" + trim(hearts) + " heart(s)";
        }
        if (stat == StatType.TREASURE) {
            return level == 0 ? "no bonus" : (level * 4) + "% chance";
        }
        if (level == 0) return "no bonus";
        return "+" + trim((multiplier - 1.0d) * 100.0d) + "%";
    }

    private String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null || !TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        StatType[] all = StatType.values();
        if (slot < 0 || slot >= all.length) return;

        StatType stat = all[slot];
        PlayerStats stats = plugin.stats().get(player);
        RpgConfig settings = plugin.settings();

        int level = stats.level(stat);
        if (level >= settings.capFor(stat)) {
            player.sendMessage(ChatColor.GOLD + stat.display + " is already fully trained.");
            return;
        }

        int cost = StatCosts.cost(level, settings.costMultiplier);
        if (player.getLevel() < cost) {
            player.sendMessage(ChatColor.RED + "You need " + cost + " experience levels to raise "
                    + stat.display + ". You have " + player.getLevel() + ".");
            return;
        }

        player.setLevel(player.getLevel() - cost);
        stats.set(stat, level + 1);
        plugin.stats().markDirty();
        plugin.effects().refresh(player);

        player.sendMessage(ChatColor.GREEN + stat.display + ChatColor.GRAY + " is now level "
                + ChatColor.WHITE + (level + 1) + ChatColor.GRAY + ". Spent " + cost + " level(s).");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        render(player, event.getInventory());
    }
}
