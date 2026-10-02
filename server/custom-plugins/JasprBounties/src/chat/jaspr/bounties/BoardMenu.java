package chat.jaspr.bounties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The bounty board as a chest (the browser client draws chests on every device): the three dailies in the middle row,
 * the two weeklies below with the streak between them, and buttons to claim everything, swap a daily and switch the
 * reminders. Clicking a finished bounty claims it.
 */
final class BoardMenu implements Listener {
    static final int SIZE = 36;
    static final int[] DAILY_SLOTS = {11, 13, 15};
    static final int[] WEEKLY_SLOTS = {20, 24};
    static final int INFO = 4, STREAK = 22, HELP = 27, SWAP = 30, CLAIM = 31, REMIND = 32, CLOSE = 35;
    static final String TITLE = ChatColor.DARK_GREEN + "Bounty Board";

    static final class Holder implements InventoryHolder {
        final UUID owner;
        boolean swapping;
        Inventory inventory;
        Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final BountiesPlugin plugin;

    BoardMenu(BountiesPlugin plugin) { this.plugin = plugin; }

    void open(Player p) {
        Holder h = new Holder(p.getUniqueId());
        h.inventory = Bukkit.createInventory(h, SIZE, TITLE);
        render(p, h);
        p.openInventory(h.inventory);
    }

    private void render(Player p, Holder h) {
        PlayerBoard b = plugin.board(p);
        Inventory inv = h.inventory;
        inv.clear();
        ItemStack pane = item(Material.STAINED_GLASS_PANE, 15, " ", null);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane);

        List<String> info = new ArrayList<>();
        info.add(ChatColor.GRAY + "Dailies reset in " + ChatColor.WHITE + plugin.untilReset(false));
        info.add(ChatColor.GRAY + "Weeklies reset in " + ChatColor.WHITE + plugin.untilReset(true));
        info.add(ChatColor.GRAY + "Bounties finished: " + ChatColor.WHITE + b.lifetime);
        info.add("");
        info.add(ChatColor.DARK_GRAY + "Rewards are paid in experience - spend");
        info.add(ChatColor.DARK_GRAY + "it on your stats (K or /stats).");
        inv.setItem(INFO, item(Material.SIGN, 0, ChatColor.GOLD + "Bounty Board", info));

        for (int i = 0; i < PlayerBoard.DAILY; i++) inv.setItem(DAILY_SLOTS[i], bounty(b.daily[i], "Daily", h.swapping));
        for (int i = 0; i < PlayerBoard.WEEKLY; i++) inv.setItem(WEEKLY_SLOTS[i], bounty(b.weekly[i], "Weekly", false));

        List<String> streak = new ArrayList<>();
        boolean today = b.bonusDay == b.day;
        streak.add(ChatColor.GRAY + "Claim all three dailies in a day to");
        streak.add(ChatColor.GRAY + "grow your streak: +" + plugin.rewards().streakXp + " XP per day of it");
        streak.add(ChatColor.GRAY + "(up to 7), and a trinket every 7th day.");
        streak.add("");
        streak.add(ChatColor.WHITE + "Streak: " + b.streak + " day(s)" + (today ? ChatColor.GREEN + " - today done" : ""));
        ItemStack s = item(today ? Material.NETHER_STAR : Material.CHEST, 0, ChatColor.LIGHT_PURPLE + "Daily streak", streak);
        inv.setItem(STREAK, s);

        List<String> help = new ArrayList<>();
        help.add(ChatColor.GRAY + "Kills count when you land the last blow,");
        help.add(ChatColor.GRAY + "your turret does, or you fought the boss.");
        help.add(ChatColor.GRAY + "Blocks you placed and silk touch don't count.");
        help.add(ChatColor.GRAY + "A realm's bounties appear once you've been there.");
        help.add(ChatColor.GRAY + "Make a sign that says " + ChatColor.WHITE + "[Bounties]" + ChatColor.GRAY + " for a board.");
        inv.setItem(HELP, item(Material.BOOK, 0, ChatColor.YELLOW + "How bounties work", help));

        List<String> swap = new ArrayList<>();
        swap.add(b.rerolls >= 1 ? ChatColor.RED + "Used today." : ChatColor.GRAY + "Once a day: click this, then an open daily.");
        if (h.swapping) swap.add(ChatColor.GREEN + "Now click the daily to swap.");
        ItemStack sw = item(Material.PAPER, 0, ChatColor.YELLOW + "Swap a daily", swap);
        if (h.swapping) glow(sw);
        inv.setItem(SWAP, sw);

        int done = b.count(Bounty.State.DONE);
        List<String> claim = new ArrayList<>();
        claim.add(done > 0 ? ChatColor.GREEN + "" + done + " reward(s) ready" : ChatColor.GRAY + "Nothing to claim yet.");
        inv.setItem(CLAIM, item(done > 0 ? Material.EMERALD : Material.EMPTY_MAP, 0, ChatColor.GREEN + "Claim all", claim));

        List<String> remind = new ArrayList<>();
        remind.add(ChatColor.GRAY + "Reminders in chat now and then: " + (b.remind ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
        remind.add(ChatColor.DARK_GRAY + "Finished bounties are always announced.");
        inv.setItem(REMIND, item(Material.WATCH, 0, ChatColor.YELLOW + "Reminders: " + (b.remind ? "on" : "off"), remind));
        inv.setItem(CLOSE, item(Material.BARRIER, 0, ChatColor.RED + "Close", null));
    }

    private ItemStack bounty(Bounty x, String kind, boolean swapping) {
        if (x == null) return item(Material.BARRIER, 0, ChatColor.GRAY + "No bounty", null);
        Template t = x.template();
        Template.Tier tier = t == null ? Template.Tier.EASY : t.tier;
        List<String> lore = new ArrayList<>();
        lore.add(tier.colour + kind + " - " + tier.label + (t != null && t.realm != Template.Realm.OVERWORLD ? ChatColor.GRAY + " - " + t.realm.title : ""));
        lore.add(ChatColor.GRAY + bar(x) + " " + ChatColor.WHITE + x.have + "/" + x.need);
        if (t != null) lore.add(ChatColor.DARK_GRAY + t.hint);
        lore.add("");
        lore.add(ChatColor.GOLD + "Reward: " + ChatColor.WHITE + Rewards.describe(x));
        if (x.state == Bounty.State.DONE) lore.add(ChatColor.GREEN + "Done! Click to claim.");
        else if (x.state == Bounty.State.CLAIMED) lore.add(ChatColor.DARK_GRAY + "Claimed.");
        else if (swapping && kind.equals("Daily")) lore.add(ChatColor.YELLOW + "Click to swap this bounty.");
        Material icon = t == null ? Material.PAPER : t.icon;
        ItemStack it = item(x.state == Bounty.State.CLAIMED ? Material.STAINED_GLASS_PANE : icon, x.state == Bounty.State.CLAIMED ? 5 : 0,
            (x.state == Bounty.State.CLAIMED ? ChatColor.DARK_GRAY : tier.colour) + x.title(), lore);
        if (x.state == Bounty.State.DONE) glow(it);
        return it;
    }

    /** "||||||....": ten bars of progress. */
    static String bar(Bounty x) {
        int full = x.need <= 0 ? 10 : (int) Math.min(10, (x.have * 10L) / x.need);
        StringBuilder s = new StringBuilder(ChatColor.GREEN.toString());
        for (int i = 0; i < 10; i++) { if (i == full) s.append(ChatColor.DARK_GRAY); s.append('|'); }
        return s.toString();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack item(Material m, int data, String name, List<String> lore) {
        ItemStack it = new ItemStack(m, 1, (short) data);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static void glow(ItemStack it) {
        it.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); it.setItemMeta(meta); }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int slot : e.getRawSlots()) if (slot < SIZE) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Holder h = (Holder) e.getView().getTopInventory().getHolder();
        if (!h.owner.equals(p.getUniqueId())) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        PlayerBoard b = plugin.board(p);
        if (slot == CLOSE) { p.closeInventory(); return; }
        if (slot == CLAIM) {
            if (plugin.claimAll(p, b) == 0) p.sendMessage(ChatColor.GRAY + "No finished bounties to claim yet.");
        } else if (slot == SWAP) {
            if (b.rerolls >= 1) p.sendMessage(ChatColor.RED + "You have already swapped a bounty today.");
            else h.swapping = !h.swapping;
        } else if (slot == REMIND) {
            b.remind = !b.remind;
            b.dirty = true;
        } else {
            Bounty x = null;
            int daily = -1;
            for (int i = 0; i < DAILY_SLOTS.length; i++) if (DAILY_SLOTS[i] == slot) { x = b.daily[i]; daily = i; }
            for (int i = 0; i < WEEKLY_SLOTS.length; i++) if (WEEKLY_SLOTS[i] == slot) x = b.weekly[i];
            if (x == null) return;
            if (x.state == Bounty.State.DONE) plugin.claim(p, b, x);
            else if (h.swapping && daily >= 0) { plugin.reroll(p, b, daily); h.swapping = false; }
            else return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        render(p, h);
    }
}
