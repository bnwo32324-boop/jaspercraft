package local.eagler.testserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagDouble;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Gets players out of a spot where their browser game keeps freezing. A player who leaves while a keepalive has gone
 * unanswered for seconds (the game stopped ticking: timed out, or the page was reloaded) has "stalled" there. Two
 * stalls within 15 minutes and 24 blocks of each other mean the spot itself freezes that client, and rejoining would
 * put them straight back into it (on the same boat): so, one tick after they leave, their saved player file is moved
 * to the world spawn with no vehicle, before they can load it again. {@code /unstick <player>} does the same on
 * request, immediately for an online player. Logged as JASPR_NET events; no addresses or identifiers beyond the name.
 */
final class StallRescue {
    static final long WINDOW_MILLIS = 15L * 60L * 1000L;
    static final double NEAR_BLOCKS = 24.0;
    static final long STALL_MILLIS = 5_000L;

    /** Where and when a player's client stalled. */
    static final class Stall {
        final String world;
        final double x, y, z;
        final long at;
        Stall(String world, double x, double y, double z, long at) { this.world = world; this.x = x; this.y = y; this.z = z; this.at = at; }
        boolean near(Stall o) {
            double dx = x - o.x, dz = z - o.z;
            return world.equals(o.world) && dx * dx + dz * dz <= NEAR_BLOCKS * NEAR_BLOCKS;
        }
        String where() { return world + ":" + (int) Math.floor(x) + "," + (int) Math.floor(y) + "," + (int) Math.floor(z); }
    }

    /** Pure bookkeeping (testable without a server): remembers recent stalls and says when one repeats. */
    static final class Tracker {
        private final Map<UUID, Deque<Stall>> recent = new HashMap<>();

        /** Records a stall; true when an earlier stall of the same player lies near it within the window. */
        boolean record(UUID id, Stall stall) {
            Deque<Stall> list = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
            boolean repeated = false;
            for (Iterator<Stall> it = list.iterator(); it.hasNext(); ) {
                Stall s = it.next();
                if (stall.at - s.at > WINDOW_MILLIS) it.remove();
                else if (s.near(stall)) repeated = true;
            }
            list.addLast(stall);
            while (list.size() > 8) list.removeFirst();
            if (repeated) list.clear();   // rescued: start counting afresh
            return repeated;
        }

        int size(UUID id) { Deque<Stall> d = recent.get(id); return d == null ? 0 : d.size(); }
    }

    /** A stall: a keepalive is still waiting for its answer after STALL_MILLIS (healthy clients answer in well under 1 s). */
    static boolean stalled(boolean pending, long sinceKeepaliveMillis) { return pending && sinceKeepaliveMillis >= STALL_MILLIS; }

    private final JavaPlugin plugin;
    private final Tracker tracker = new Tracker();
    private final Map<UUID, String> notices = new HashMap<>();
    private Method handleMethod, pendingMethod, lastPingMethod;
    private java.lang.reflect.Field connectionField;
    private boolean reflectionFailed;

    StallRescue(JavaPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ detection

    void playerQuit(Player player) {
        long since = sinceKeepalive(player);
        if (since < 0 || !stalled(true, since)) return;
        Location l = player.getLocation();
        Stall stall = new Stall(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), System.currentTimeMillis());
        boolean repeated = tracker.record(player.getUniqueId(), stall);
        plugin.getLogger().warning("JASPR_NET event=client_stall player=" + player.getName() + " at=" + stall.where()
            + " unansweredMs=" + since + " vehicle=" + (player.getVehicle() != null) + " repeated=" + repeated);
        if (!repeated) return;
        UUID id = player.getUniqueId();
        String name = player.getName();
        // After the quit has saved the player file (this same tick), before any rejoin can load it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> rescueOffline(id, name, "repeated-stall", stall.where()), 1L);
    }

    /** Milliseconds since the pending keepalive was sent, or -1 if none is pending or the server hides it. */
    private long sinceKeepalive(Player player) {
        if (reflectionFailed) return -1;
        try {
            if (handleMethod == null) handleMethod = player.getClass().getMethod("getHandle");
            Object handle = handleMethod.invoke(player);
            if (connectionField == null) connectionField = handle.getClass().getField("playerConnection");
            Object connection = connectionField.get(handle);
            if (connection == null) return -1;
            if (pendingMethod == null) {
                pendingMethod = connection.getClass().getDeclaredMethod("isPendingPing");
                pendingMethod.setAccessible(true);
                lastPingMethod = connection.getClass().getDeclaredMethod("getLastPing");
                lastPingMethod.setAccessible(true);
            }
            if (!(Boolean) pendingMethod.invoke(connection)) return -1;
            return Math.max(0L, System.currentTimeMillis() - (Long) lastPingMethod.invoke(connection));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            reflectionFailed = true;
            plugin.getLogger().warning("JASPR_NET event=stall_detection_unavailable reason=" + failed.getClass().getSimpleName());
            return -1;
        }
    }

    // ------------------------------------------------------------------ rescue

    void playerJoined(Player player) {
        String notice = notices.remove(player.getUniqueId());
        if (notice != null) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.sendMessage(ChatColor.GOLD + notice);
        }, 60L);
    }

    /** /unstick: online players are dismounted and moved now; offline ones have their saved file moved. */
    String unstick(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            Location from = online.getLocation();
            if (online.isInsideVehicle()) online.leaveVehicle();
            Location to = safeSpawn();
            online.setFallDistance(0f);
            online.setVelocity(new Vector());
            boolean ok = online.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
            plugin.getLogger().info("JASPR_NET event=stall_rescue player=" + online.getName() + " reason=command from=" + where(from) + " to=" + where(to) + " ok=" + ok);
            if (ok) online.sendMessage(ChatColor.GOLD + "You were moved to spawn to get you unstuck.");
            return ok ? online.getName() + " was moved to spawn." : "The teleport was refused; try again in a moment.";
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline == null || !offline.hasPlayedBefore()) return "No player called " + name + " has played here.";
        return rescueOffline(offline.getUniqueId(), offline.getName(), "command", "unknown")
            ? offline.getName() + " will join at spawn, off any vehicle." : "Could not update " + name + "'s saved position (see the log).";
    }

    /** Rewrites an offline player's saved file: no vehicle, standing at the world spawn, not moving or falling. */
    private boolean rescueOffline(UUID id, String name, String reason, String from) {
        if (Bukkit.getPlayer(id) != null) return false;   // back already: their live state wins
        World world = Bukkit.getWorlds().get(0);
        File file = new File(world.getWorldFolder(), "playerdata" + File.separator + id + ".dat");
        Location to = safeSpawn();
        try {
            NBTTagCompound root;
            try (InputStream in = new FileInputStream(file)) { root = NBTCompressedStreamTools.a(in); }
            boolean vehicle = root.hasKey("RootVehicle");
            root.remove("RootVehicle");
            root.set("Pos", doubles(to.getX(), to.getY(), to.getZ()));
            root.set("Motion", doubles(0, 0, 0));
            root.setFloat("FallDistance", 0f);
            root.setBoolean("OnGround", true);
            root.setShort("Air", (short) 300);
            root.setInt("Dimension", 0);
            root.setLong("WorldUUIDMost", world.getUID().getMostSignificantBits());
            root.setLong("WorldUUIDLeast", world.getUID().getLeastSignificantBits());
            File tmp = new File(file.getPath() + ".unstick");
            try (OutputStream out = new FileOutputStream(tmp)) { NBTCompressedStreamTools.a(root, out); }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            notices.put(id, "Your game kept freezing near " + from.replaceFirst("^[^:]*:", "") + ", so you were moved to spawn"
                + (vehicle ? " and off your vehicle." : "."));
            plugin.getLogger().warning("JASPR_NET event=stall_rescue player=" + name + " reason=" + reason + " from=" + from + " to=" + where(to) + " vehicleRemoved=" + vehicle + " ok=true");
            return true;
        } catch (Exception failed) {
            plugin.getLogger().warning("JASPR_NET event=stall_rescue player=" + name + " reason=" + reason + " ok=false error=" + failed.getClass().getSimpleName());
            return false;
        }
    }

    private static NBTTagList doubles(double... values) {
        NBTTagList list = new NBTTagList();
        for (double v : values) list.add(new NBTTagDouble(v));
        return list;
    }

    private static Location safeSpawn() {
        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX(), z = spawn.getBlockZ();
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 0.1, z + 0.5);
    }

    private static String where(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

}
