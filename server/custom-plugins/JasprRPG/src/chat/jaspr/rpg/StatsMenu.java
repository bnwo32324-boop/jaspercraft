package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The upgrade sheet, drawn as a chest.
 *
 * A vanilla container is the only interface the browser client can render, which turns out to suit
 * this well: one slot per stat, the cost written on it in experience levels, and a click to buy.
 * Since 2026-10-02 there are too many stats for one chest, so the bottom row holds five tabs (Combat,
 * Defense, Survival, Gathering, Movement) and the stats of the open tab fill the rows above.
 */
final class StatsMenu implements Listener {
    static final int SIZE = 54;
    /** Stat slots of a tab: rows 1-3, columns 1-7 (up to 21 stats a tab). */
    static final int[] STAT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int HEADER = 4, PURSE = 50, HELP = 52, CLOSE = 53, FIRST_TAB = 45;
    private static final String TITLE = ChatColor.DARK_GREEN + "Stats";

    /** Marks a chest as this sheet and remembers which tab it shows. */
    static final class Holder implements InventoryHolder {
        final UUID owner;
        StatType.Category tab;
        final StatType[] slots = new StatType[SIZE];
        Inventory inventory;
        Holder(UUID owner, StatType.Category tab) { this.owner = owner; this.tab = tab; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final RpgPlugin plugin;
    private final Map<UUID, StatType.Category> lastTab = new HashMap<UUID, StatType.Category>();

    StatsMenu(RpgPlugin plugin) { this.plugin = plugin; }

    void open(Player player) {
        StatType.Category tab = lastTab.get(player.getUniqueId());
        Holder holder = new Holder(player.getUniqueId(), tab == null ? StatType.Category.COMBAT : tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    /** The slot a stat sits in on its tab (for tests and tools), or -1. */
    static int slotOf(StatType stat) {
        List<StatType> tab = stat.category.stats();
        int i = tab.indexOf(stat);
        return i < 0 || i >= STAT_SLOTS.length ? -1 : STAT_SLOTS[i];
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        java.util.Arrays.fill(holder.slots, null);
        PlayerStats stats = plugin.stats().get(player);
        RpgConfig settings = plugin.settings();

        ItemStack pane = named(new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7), " ", null);
        for (int i = 36; i < 45; i++) inventory.setItem(i, pane);

        List<String> head = new ArrayList<String>();
        head.add(ChatColor.GRAY + holder.tab.blurb);
        head.add("");
        head.add(ChatColor.DARK_GRAY + "Levels 1-10 are ordinary ranks; levels 11-15");
        head.add(ChatColor.DARK_GRAY + "are mastery ranks: half the gain, "
            + StatCosts.cost(StatType.ORDINARY, settings.costMultiplier) + " levels each.");
        inventory.setItem(HEADER, named(new ItemStack(holder.tab.icon), ChatColor.GOLD + holder.tab.title, head));

        List<StatType> list = holder.tab.stats();
        for (int i = 0; i < list.size() && i < STAT_SLOTS.length; i++) {
            StatType stat = list.get(i);
            inventory.setItem(STAT_SLOTS[i], icon(stat, stats, settings, player));
            holder.slots[STAT_SLOTS[i]] = stat;
        }

        StatType.Category[] tabs = StatType.Category.values();
        for (int i = 0; i < tabs.length; i++) {
            StatType.Category c = tabs[i];
            boolean open = c == holder.tab;
            int trained = 0;
            for (StatType s : c.stats()) trained += stats.level(s);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + c.blurb);
            lore.add(ChatColor.DARK_GRAY + "" + c.stats().size() + " stats, " + trained + " level(s) trained");
            lore.add(open ? ChatColor.GREEN + "Open" : ChatColor.YELLOW + "Click to open");
            ItemStack tab = named(new ItemStack(c.icon), (open ? ChatColor.GREEN : ChatColor.WHITE) + c.title, lore);
            if (open) glow(tab);
            inventory.setItem(FIRST_TAB + i, tab);
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
        inventory.setItem(PURSE, purse);

        List<String> help = new ArrayList<String>();
        help.add(ChatColor.GRAY + "Pick a tab below, then click a stat to");
        help.add(ChatColor.GRAY + "raise it by one level.");
        help.add(ChatColor.GRAY + "Hold a weapon or armour and press K for");
        help.add(ChatColor.GRAY + "its own upgrade sheet instead.");
        help.add(ChatColor.GRAY + "/stats top: the most trained survivors.");
        inventory.setItem(HELP, named(new ItemStack(Material.BOOK), ChatColor.YELLOW + "How stats work", help));
        inventory.setItem(CLOSE, named(new ItemStack(Material.BARRIER), ChatColor.RED + "Close", null));
    }

    private static ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static void glow(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private ItemStack icon(StatType stat, PlayerStats stats, RpgConfig settings, Player player) {
        int level = stats.level(stat);
        int cap = settings.capFor(stat);
        boolean maxed = level >= cap;
        int cost = StatCosts.cost(level, settings.costMultiplier);

        ItemStack item = new ItemStack(stat.icon, Math.max(1, Math.min(64, level == 0 ? 1 : level)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((maxed ? ChatColor.GOLD : level > StatType.ORDINARY ? ChatColor.LIGHT_PURPLE : ChatColor.YELLOW) + stat.display
                + ChatColor.GRAY + "  " + level + "/" + cap);

        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + stat.description);
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Now: " + ChatColor.WHITE + format(stat, level, cap, settings));
        if (!maxed) {
            lore.add(ChatColor.DARK_GRAY + "Next: " + ChatColor.WHITE + format(stat, level + 1, cap, settings)
                + (level + 1 > StatType.ORDINARY ? ChatColor.LIGHT_PURPLE + " (mastery)" : ""));
            lore.add("");
            boolean affordable = player.getLevel() >= cost;
            lore.add((affordable ? ChatColor.GREEN : ChatColor.RED) + "Cost: " + cost + " experience level(s)");
            lore.add(affordable ? ChatColor.GRAY + "Click to buy." : ChatColor.DARK_GRAY + "Not enough experience.");
        } else {
            lore.add("");
            lore.add(ChatColor.GOLD + "Fully trained.");
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
        item.setItemMeta(meta);
        return item;
    }

    /** What a stat is worth at a level, in words. */
    static String format(StatType stat, int level, int cap, RpgConfig settings) {
        if (level <= 0) return "no bonus";
        double eff = StatType.effective(level, cap) * settings.bonusMultiplier;
        double mult = 1.0d + (stat.multiplier(level, cap) - 1.0d) * settings.bonusMultiplier;
        switch (stat) {
            case HEALTH: return "+" + trim(StatType.effective(level, cap) * settings.healthPerLevel / 2.0d) + " heart(s)";
            case TREASURE: return trim(eff * 4) + "% chance";
            case MAGICIAN: return trim(eff * 5) + "% chance of a second haul";
            case STEALTH: return trim(StatEffects.stealthBlind(level)) + " blocks less notice";
            case PRECISION: return trim(Math.min(StatEffects.CRIT_CHANCE_CAP, stat.perLevel * eff) * 100) + "% critical chance";
            case FEROCITY: return "criticals +" + trim((StatEffects.CRIT_BASE + stat.perLevel * eff) * 100) + "%";
            case BLOODTHIRST: return trim(Math.min(0.25d, stat.perLevel * eff) * 100) + "% of melee damage healed";
            case EVASION: return trim(Math.min(StatEffects.EVASION_CAP, stat.perLevel * eff) * 100) + "% dodge chance";
            case STEADFAST: return "+" + trim(Math.min(0.6d, stat.perLevel * eff) * 100) + "% knockback resistance";
            case SECOND_WIND: return "once every " + (StatEffects.secondWindCooldown(eff) / 1000L) + "s";
            case LUCK: return "+" + trim(Math.min(8.0d, stat.perLevel * eff)) + " luck";
            case SCAVENGER: return trim(Math.min(0.5d, stat.perLevel * eff) * 100) + "% chance per drop";
            case ANGLER: return trim(Math.min(0.75d, stat.perLevel * eff) * 100) + "% double catch";
            case GREEN_THUMB: return trim(Math.min(0.75d, stat.perLevel * eff) * 100) + "% extra produce";
            case LUMBERJACK: return trim(Math.min(0.6d, stat.perLevel * eff) * 100) + "% extra log";
            case FLEET_FOOT: return "+" + trim(Math.min(0.25d, stat.perLevel * eff) * 100) + "% speed";
            default: return "+" + trim((mult - 1.0d) * 100.0d) + "%";
        }
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int slot : event.getRawSlots()) if (slot < SIZE) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null || !(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;

        Player player = (Player) event.getWhoClicked();
        Holder holder = (Holder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        if (slot == CLOSE) { player.closeInventory(); return; }
        if (slot >= FIRST_TAB && slot < FIRST_TAB + StatType.Category.values().length) {
            holder.tab = StatType.Category.values()[slot - FIRST_TAB];
            lastTab.put(player.getUniqueId(), holder.tab);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            render(player, holder);
            return;
        }

        StatType stat = holder.slots[slot];
        if (stat == null) return;
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
        plugin.getLogger().info("RPG_STAT_UP player=" + player.getName() + " stat=" + stat.key() + " level=" + (level + 1) + " cost=" + cost);
        render(player, holder);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { lastTab.remove(event.getPlayer().getUniqueId()); }
}
