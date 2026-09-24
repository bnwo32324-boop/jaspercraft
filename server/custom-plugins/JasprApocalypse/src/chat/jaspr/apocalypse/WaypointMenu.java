package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest GUI for the waypoint list. Pure builders; Waypoints owns the listeners. */
public final class WaypointMenu {
    private WaypointMenu() { }
    static final String TITLE = ChatColor.AQUA + "Waypoints";
    static final int SIZE = 54;
    static final int CREATE_SLOT = 45;
    static final int NEAREST_SLOT = 49;
    static final int CLOSE_SLOT = 53;

    public static final class Menu implements InventoryHolder {
        final Waypoints manager;
        final Inventory inventory;
        public Menu(Waypoints manager, Player player) {
            this.manager = manager;
            this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
            paint(player);
        }
        @Override public Inventory getInventory() { return inventory; }
        void paint(Player player) {
            inventory.clear();
            List<Waypoint> list = ordered(manager, player.getUniqueId());
            Integer activeSlot = manager.activeSlot(player.getUniqueId());
            int index = 0;
            for (Waypoint waypoint : list) {
                if (index >= 45) break;
                boolean isActive = activeSlot != null && activeSlot == waypoint.slot;
                ItemStack icon = new ItemStack(waypoint.death ? Material.SKULL_ITEM : Material.WOOL, 1,
                    (short) (waypoint.death ? 0 : waypoint.color));
                ItemMeta meta = icon.getItemMeta();
                meta.setDisplayName((isActive ? ChatColor.GOLD + "\u25b6 " : Waypoint.CHAT[waypoint.color]) + waypoint.name);
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + waypoint.world + "  " + waypoint.x + ", " + waypoint.y + ", " + waypoint.z);
                lore.add(ChatColor.GRAY + Waypoint.COLOR_NAMES[waypoint.color] + (waypoint.death ? " \u00b7 deathpoint" : "")
                    + (isActive ? " \u00b7 tracking" : ""));
                int distance = distanceTo(player, waypoint);
                lore.add(ChatColor.GRAY + (distance >= 0 ? Waypoint.formatDistance(distance) + " away" : "another world"));
                lore.add(ChatColor.DARK_GRAY + (player.getGameMode() == GameMode.CREATIVE ? "Left: teleport" : "Left: track")
                    + " \u00b7 Right: recolor \u00b7 Shift+Left: delete");
                meta.setLore(lore);
                if (isActive) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                icon.setItemMeta(meta);
                inventory.setItem(index++, icon);
            }
            ItemStack create = named(Material.EMERALD, (short) 0, ChatColor.GREEN + "Create waypoint here",
                ChatColor.GRAY + "Close and type a name in chat.");
            inventory.setItem(CREATE_SLOT, create);
            ItemStack nearest = named(Material.COMPASS, (short) 0, ChatColor.AQUA + "Track nearest",
                ChatColor.GRAY + "Point the compass at the closest waypoint.");
            inventory.setItem(NEAREST_SLOT, nearest);
            ItemStack close = named(Material.BARRIER, (short) 0, ChatColor.RED + "Close", new String[0]);
            inventory.setItem(CLOSE_SLOT, close);
            ItemStack filler = named(Material.STAINED_GLASS_PANE, (short) 7, " ", new String[0]);
            for (int slot : new int[]{46, 47, 48, 50, 51, 52}) inventory.setItem(slot, filler);
        }
    }

    private static ItemStack named(Material material, short data, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lines = new ArrayList<String>();
        for (String line : lore) lines.add(line);
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material material, short data, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static int distanceTo(Player player, Waypoint waypoint) {
        if (!player.getWorld().getName().equals(waypoint.world)) return -1;
        Location eye = player.getEyeLocation();
        double dx = waypoint.x - eye.getX(), dy = waypoint.y - eye.getY(), dz = waypoint.z - eye.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public static void open(Waypoints manager, Player player) {
        player.openInventory(new Menu(manager, player).inventory);
    }

    static Menu menuOf(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTopInventory() == null) return null;
        return event.getView().getTopInventory().getHolder() instanceof Menu
            ? (Menu) event.getView().getTopInventory().getHolder() : null;
    }

    static boolean menuDragged(InventoryDragEvent event) {
        try {
            if (event.getView() == null || event.getView().getTopInventory() == null) return false;
            return event.getView().getTopInventory().getHolder() instanceof Menu;
        } catch (RuntimeException e) { return false; }
    }

    /** Shared click logic; the thin event wrapper lives on Waypoints. Public for fixtures. */
    public static void click(Waypoints manager, Menu menu, Player player, int slot,
            org.bukkit.event.inventory.ClickType click) {
        if (slot == CLOSE_SLOT) { player.closeInventory(); return; }
        if (slot == CREATE_SLOT) { manager.beginCreate(player); return; }
        if (slot == NEAREST_SLOT) { trackNearest(manager, player); menu.paint(player); return; }
        Waypoint waypoint = at(manager, player, slot);
        if (waypoint == null) return;
        switch (click) {
            case LEFT:
                manager.setActive(player.getUniqueId(), waypoint.slot);
                if (player.getGameMode() == GameMode.CREATIVE) { teleport(player, waypoint); return; }
                player.sendMessage(ChatColor.GREEN + "Tracking '" + waypoint.name + "'.");
                break;
            case RIGHT:
                manager.recolor(player.getUniqueId(), waypoint.slot);
                manager.sync(player);
                break;
            case SHIFT_LEFT: {
                Waypoint current = manager.bySlot(player.getUniqueId(), waypoint.slot);
                if (current == null) return;
                if (manager.armDelete(player.getUniqueId(), waypoint.slot)) {
                    manager.delete(player.getUniqueId(), waypoint.slot);
                    manager.sync(player);
                    player.sendMessage(ChatColor.GRAY + "Deleted '" + current.name + "'.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Shift-click '" + current.name + "' again to delete it.");
                    return;
                }
                break;
            }
            default:
                return;
        }
        menu.paint(player);
    }

    /** Creative only (the caller checks the game mode on the server): jump to the waypoint's block. */
    private static void teleport(Player player, Waypoint waypoint) {
        World world = Bukkit.getWorld(waypoint.world);
        if (world == null) { player.sendMessage(ChatColor.RED + "World '" + waypoint.world + "' is not loaded."); return; }
        Location here = player.getLocation();
        Location to = new Location(world, waypoint.x + 0.5, waypoint.y, waypoint.z + 0.5, here.getYaw(), here.getPitch());
        player.closeInventory();
        if (player.teleport(to)) player.sendMessage(ChatColor.GREEN + "Teleported to '" + waypoint.name + "'.");
        else player.sendMessage(ChatColor.RED + "Could not teleport to '" + waypoint.name + "'.");
    }

    static List<Waypoint> ordered(Waypoints manager, UUID owner) {
        List<Waypoint> list = manager.visible(owner);
        list.sort((a, b) -> {
            if (a.death != b.death) return a.death ? 1 : -1;
            return Integer.compare(a.slot, b.slot);
        });
        return list;
    }

    private static Waypoint at(Waypoints manager, Player player, int index) {
        List<Waypoint> list = ordered(manager, player.getUniqueId());
        return index >= 0 && index < list.size() && index < 45 ? list.get(index) : null;
    }

    private static void trackNearest(Waypoints manager, Player player) {
        Waypoint best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Waypoint waypoint : manager.visible(player.getUniqueId())) {
            int distance = distanceTo(player, waypoint);
            if (distance >= 0 && distance < bestDistance) { bestDistance = distance; best = waypoint; }
        }
        if (best == null) { player.sendMessage(ChatColor.GRAY + "No waypoints in this world yet."); return; }
        manager.setActive(player.getUniqueId(), best.slot);
        player.sendMessage(ChatColor.GREEN + "Tracking '" + best.name + "' (" + Waypoint.formatDistance(bestDistance) + ").");
    }
}
