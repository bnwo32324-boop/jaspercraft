package chat.jaspr.graves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The window onto a grave. One viewer at a time: two players sharing a view of
 * the same list is how duplication bugs are born, so the second one is turned
 * away rather than handed a stale copy.
 */
final class GraveMenu {
    static final int PAGE = 54;

    static final class Holder implements InventoryHolder {
        final UUID graveId;
        Inventory inventory;
        Holder(UUID graveId) { this.graveId = graveId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GravesPlugin plugin;
    private final Map<UUID, UUID> viewers = new HashMap<UUID, UUID>();

    GraveMenu(GravesPlugin plugin) { this.plugin = plugin; }

    boolean busy(Grave grave) {
        UUID viewer = viewers.get(grave.id);
        if (viewer == null) return false;
        Player player = Bukkit.getPlayer(viewer);
        if (player == null || !player.isOnline()) { viewers.remove(grave.id); return false; }
        return true;
    }

    String viewerName(Grave grave) {
        UUID viewer = viewers.get(grave.id);
        Player player = viewer == null ? null : Bukkit.getPlayer(viewer);
        return player == null ? "Someone" : player.getName();
    }

    void open(Player player, Grave grave) {
        Holder holder = new Holder(grave.id);
        String title = ChatColor.DARK_GRAY + "Grave of " + grave.ownerName;
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, PAGE, title);
        holder.inventory = inventory;
        int shown = Math.min(PAGE, grave.contents.size());
        for (int i = 0; i < shown; i++) inventory.setItem(i, grave.contents.get(i));
        viewers.put(grave.id, player.getUniqueId());
        player.openInventory(inventory);
        if (grave.experience > 0) {
            int experience = grave.experience;
            grave.experience = 0;
            player.giveExp(experience);
            plugin.store().touch();
            player.sendMessage(ChatColor.GREEN + "Recovered " + experience + " experience.");
        }
        if (grave.contents.size() > PAGE) {
            player.sendMessage(ChatColor.GRAY + "This grave is deep: "
                + (grave.contents.size() - PAGE) + " more stacks move up as you clear space.");
        }
    }

    /** Writes the window back onto the grave. The grave, not the window, is the record. */
    void sync(Holder holder) {
        Grave grave = plugin.store().byId(holder.graveId);
        if (grave == null || holder.inventory == null) return;
        List<ItemStack> left = new ArrayList<ItemStack>();
        for (ItemStack item : holder.inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) left.add(item);
        }
        // Anything past the first page was never on screen and cannot have been touched.
        if (grave.contents.size() > PAGE) left.addAll(grave.contents.subList(PAGE, grave.contents.size()));
        grave.contents.clear();
        grave.contents.addAll(left);
        plugin.store().touch();
    }

    void close(Player player, Holder holder) {
        Grave grave = plugin.store().byId(holder.graveId);
        viewers.remove(holder.graveId);
        if (grave == null) return;
        sync(holder);
        if (grave.empty()) {
            plugin.retire(grave);
            if (player != null) player.sendMessage(ChatColor.GRAY + "The grave is empty. It crumbles away.");
        }
        plugin.store().save(false);
    }

    void closeAll() {
        for (UUID viewer : new ArrayList<UUID>(viewers.values())) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) try { player.closeInventory(); } catch (RuntimeException ignored) { }
        }
        viewers.clear();
    }

    void forget(Grave grave) { viewers.remove(grave.id); }
}
