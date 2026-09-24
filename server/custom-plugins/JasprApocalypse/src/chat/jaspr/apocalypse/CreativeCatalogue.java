package chat.jaspr.apocalypse;

import java.util.Map;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;

/** Turns harmless browser menu templates into fresh, server-authored custom items. */
public final class CreativeCatalogue implements Listener {
    private static final String REQUEST_TAG = "JasprCreative";
    private final ApocalypsePlugin plugin;
    private long issued;
    private long rejected;

    CreativeCatalogue(ApocalypsePlugin plugin) { this.plugin = plugin; }

    void start() { plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    void stop() { HandlerList.unregisterAll(this); }

    static String requestId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        if (!nms.hasTag()) return "";
        NBTTagCompound root = nms.getTag();
        if (!root.hasKeyOfType(REQUEST_TAG, 10)) return "";
        return root.getCompound(REQUEST_TAG).getString("id");
    }

    public static ItemStack issue(ItemStack template) {
        String id = requestId(template);
        Map<String, String> catalogue = ApocalypseItems.catalogue();
        return catalogue.containsKey(id) ? ApocalypseItems.expedition(id, 5) : null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        String requested = requestId(event.getCursor());
        if (requested.isEmpty()) return;
        Player player = (Player) event.getWhoClicked();
        if (player.getGameMode() != GameMode.CREATIVE || !plugin.authenticated(player)) {
            rejected++;
            event.setCancelled(true);
            event.setCursor(new ItemStack(Material.AIR));
            plugin.getLogger().warning("CREATIVE_CATALOGUE_REJECT player=" + player.getName() + " id=" + requested
                + " reason=" + (player.getGameMode() != GameMode.CREATIVE ? "not-creative" : "not-authenticated"));
            return;
        }
        ItemStack exact = issue(event.getCursor());
        if (exact == null) {
            rejected++;
            event.setCancelled(true);
            event.setCursor(new ItemStack(Material.AIR));
            plugin.getLogger().warning("CREATIVE_CATALOGUE_REJECT player=" + player.getName() + " id=" + requested + " reason=unknown-id");
            return;
        }
        event.setCursor(exact);
        issued++;
        plugin.getLogger().info("CREATIVE_CATALOGUE_ISSUE player=" + player.getName() + " id=" + requested);
    }

    String metrics() { return "creativeCatalogue=" + ApocalypseItems.catalogue().size() + ",issued=" + issued + ",rejected=" + rejected; }
}
