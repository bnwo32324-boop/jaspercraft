package chat.jaspr.revive;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** Gives browser terrain caches a real world identity, including multiple
 * overworlds. Never use a player's name, ticket, or session as a cache key. */
final class WorldContext implements Listener {
    private final Plugin plugin;
    WorldContext(Plugin plugin) { this.plugin = plugin; }
    @EventHandler public void join(PlayerJoinEvent event) { publishSoon(event.getPlayer()); }
    @EventHandler public void change(PlayerChangedWorldEvent event) { publishSoon(event.getPlayer()); }
    private void publishSoon(final Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> send(player), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> send(player), 40L);
    }
    private void send(Player player) {
        if (!player.isOnline()) return;
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("v", 1); root.addProperty("world", player.getWorld().getUID().toString());
        net.minecraft.server.v1_12_R1.PacketDataSerializer data = new net.minecraft.server.v1_12_R1.PacketDataSerializer(io.netty.buffer.Unpooled.buffer());
        data.a(root.toString());
        ((org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) player).getHandle().playerConnection.sendPacket(
            new net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload("JASPR|World", data));
    }
}
