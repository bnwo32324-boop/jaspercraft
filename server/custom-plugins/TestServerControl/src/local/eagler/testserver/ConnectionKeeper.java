package local.eagler.testserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps slow-but-alive clients from being disconnected for a keepalive timeout.
 *
 * <p>Paper sends a keepalive every 15 seconds and, if the previous one is still
 * unanswered after {@code paper.playerconnection.keepalive} seconds (30 by
 * default), disconnects the player with "Timed out" -- which Eaglercraft shows
 * as "connection lost, end of stream." A weak client that freezes for tens of
 * seconds while meshing spawn chunks misses those keepalives and is kicked even
 * though its socket is perfectly healthy. For a protected player we simply clear
 * the pending-ping flag before the deadline is reached, so the kick condition is
 * never met. A genuinely dead socket is still removed by the normal channel-close
 * path, so this does not create ghost players.
 *
 * <p>It also emits verbose, per-player connection diagnostics for named debug
 * players so this individual's stalls can be watched directly in the server log.
 */
final class ConnectionKeeper {
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final long joinGraceMillis;
    private final int relaxPingMillis;
    private final Set<String> alwaysRelax;
    private final Set<String> debugPlayers;
    private final long debugSampleMillis;
    private final long keepaliveLimitMillis;

    private final Map<UUID, Long> joinedAtMillis = new HashMap<>();
    private final Map<UUID, Integer> keepaliveSaves = new HashMap<>();
    private final Map<UUID, Long> lastDebugLogMillis = new HashMap<>();
    private final Map<UUID, Long> lastSaveLogMillis = new HashMap<>();

    private BukkitTask task;
    private boolean reflectionDisabled;
    private boolean reflectionFailureLogged;

    // Reflection handles, resolved lazily from the first live player and cached.
    private Method getHandleMethod;
    private Field playerConnectionField;
    private Field pingField;
    private Method isPendingPingMethod;
    private Method setPendingPingMethod;
    private Method getLastPingMethod;
    private Class<?> craftPlayerClass;
    private Class<?> handleClass;
    private Class<?> connectionClass;

    ConnectionKeeper(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("connection-keeper.enabled", true);
        long graceSeconds = plugin.getConfig().getLong("connection-keeper.join-grace-seconds", 300L);
        this.joinGraceMillis = graceSeconds <= 0L ? 0L : graceSeconds * 1000L;
        this.relaxPingMillis = Math.max(0, plugin.getConfig().getInt("connection-keeper.relax-ping-ms", 1500));
        this.alwaysRelax = lowerSet(plugin.getConfig().getStringList("connection-keeper.always-relax"));
        this.debugPlayers = lowerSet(plugin.getConfig().getStringList("connection-keeper.debug-players"));
        this.debugSampleMillis = Math.max(2L, plugin.getConfig().getLong("connection-keeper.debug-sample-seconds", 10L)) * 1000L;
        // Mirror the server's own kick threshold so "would have been kicked" logging is accurate.
        this.keepaliveLimitMillis = Math.max(1L, Long.getLong("paper.playerconnection.keepalive", 30L)) * 1000L;
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values != null) for (String value : values) if (value != null) out.add(value.toLowerCase());
        return out;
    }

    void start() {
        if (!enabled) {
            plugin.getLogger().info("JASPR_KEEP event=disabled");
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("JASPR_KEEP event=started graceSeconds=" + (joinGraceMillis / 1000L)
                + " relaxPingMs=" + relaxPingMillis + " keepaliveLimitSeconds=" + (keepaliveLimitMillis / 1000L)
                + " debugPlayers=" + debugPlayers.size());
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    void playerJoined(Player player) {
        long now = System.currentTimeMillis();
        joinedAtMillis.put(player.getUniqueId(), now);
        keepaliveSaves.put(player.getUniqueId(), 0);
        if (isDebug(player)) {
            plugin.getLogger().info("JASPR_KEEP event=join player=" + player.getName()
                    + " pingMs=" + safePing(player) + " onlinePlayers=" + Bukkit.getOnlinePlayers().size());
        }
    }

    void playerQuit(Player player) {
        UUID id = player.getUniqueId();
        Long joined = joinedAtMillis.remove(id);
        Integer saves = keepaliveSaves.remove(id);
        lastDebugLogMillis.remove(id);
        lastSaveLogMillis.remove(id);
        if (isDebug(player)) {
            long sessionSeconds = joined == null ? 0L : Math.max(0L, System.currentTimeMillis() - joined) / 1000L;
            plugin.getLogger().info("JASPR_KEEP event=quit player=" + player.getName()
                    + " sessionSec=" + sessionSeconds + " keepaliveSaves=" + (saves == null ? 0 : saves)
                    + " pingMs=" + safePing(player));
        }
    }

    private void tick() {
        if (reflectionDisabled) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object connection = connectionFor(player);
            if (connection == null) continue;
            int ping = safePing(player);
            boolean pending;
            long msSinceKeepalive;
            try {
                pending = (Boolean) isPendingPingMethod.invoke(connection);
                long lastPing = (Long) getLastPingMethod.invoke(connection);
                msSinceKeepalive = Math.max(0L, now - lastPing);
            } catch (Exception failed) {
                noteReflectionFailure(failed);
                return;
            }

            boolean protect = isProtected(player, ping, now);
            boolean rescued = false;
            if (protect && pending) {
                try {
                    setPendingPingMethod.invoke(connection, Boolean.FALSE);
                    rescued = true;
                    keepaliveSaves.merge(player.getUniqueId(), 1, Integer::sum);
                } catch (Exception failed) {
                    noteReflectionFailure(failed);
                    return;
                }
            }

            // A rescue that happened past the real deadline means the player WAS about to be
            // dropped; surface that at WARNING for debug players (rate limited) -- it is the
            // clearest possible signal of a multi-second client stall.
            if (rescued && msSinceKeepalive >= keepaliveLimitMillis && isDebug(player)) {
                Long last = lastSaveLogMillis.get(player.getUniqueId());
                if (last == null || now - last >= 15_000L) {
                    lastSaveLogMillis.put(player.getUniqueId(), now);
                    plugin.getLogger().warning("JASPR_KEEP event=kept_alive player=" + player.getName()
                            + " unresponsiveMs=" + msSinceKeepalive + " pingMs=" + ping
                            + " keepaliveSaves=" + keepaliveSaves.getOrDefault(player.getUniqueId(), 0)
                            + " (client stalled past the " + (keepaliveLimitMillis / 1000L)
                            + "s keepalive limit; disconnect suppressed)");
                }
            }

            if (isDebug(player)) maybeLogHealth(player, now, ping, pending, msSinceKeepalive, protect);
        }
    }

    private void maybeLogHealth(Player player, long now, int ping, boolean pending, long msSinceKeepalive, boolean protect) {
        UUID id = player.getUniqueId();
        Long last = lastDebugLogMillis.get(id);
        if (last != null && now - last < debugSampleMillis) return;
        lastDebugLogMillis.put(id, now);
        Long joined = joinedAtMillis.get(id);
        long sessionSeconds = joined == null ? 0L : Math.max(0L, now - joined) / 1000L;
        String loc = "unknown";
        try {
            Location l = player.getLocation();
            loc = l.getWorld().getName() + "/" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ()
                    + " chunk " + (l.getBlockX() >> 4) + "," + (l.getBlockZ() >> 4);
        } catch (Exception ignored) { }
        plugin.getLogger().info("JASPR_KEEP event=health player=" + player.getName()
                + " pingMs=" + ping
                + " unresponsiveMs=" + msSinceKeepalive
                + " pendingKeepalive=" + pending
                + " protected=" + protect
                + " keepaliveSaves=" + keepaliveSaves.getOrDefault(id, 0)
                + " sessionSec=" + sessionSeconds
                + " tps=" + String.format(java.util.Locale.ROOT, "%.1f", currentTps())
                + " at " + loc);
    }

    private boolean isProtected(Player player, int ping, long now) {
        if (alwaysRelax.contains(player.getName().toLowerCase())) return true;
        if (joinGraceMillis > 0L) {
            Long joined = joinedAtMillis.get(player.getUniqueId());
            if (joined != null && now - joined < joinGraceMillis) return true;
        }
        return ping >= 0 && relaxPingMillis > 0 && ping >= relaxPingMillis;
    }

    private boolean isDebug(Player player) {
        return debugPlayers.contains(player.getName().toLowerCase());
    }

    /** Resolve (and cache) the NMS PlayerConnection behind a Bukkit player. */
    private Object connectionFor(Player player) {
        try {
            Class<?> craftClass = player.getClass();
            if (getHandleMethod == null || craftPlayerClass != craftClass) {
                getHandleMethod = craftClass.getMethod("getHandle");
                getHandleMethod.setAccessible(true);
                craftPlayerClass = craftClass;
                handleClass = null;
            }
            Object handle = getHandleMethod.invoke(player);
            if (handle == null) return null;
            Class<?> hClass = handle.getClass();
            if (playerConnectionField == null || handleClass != hClass) {
                playerConnectionField = hClass.getField("playerConnection");
                playerConnectionField.setAccessible(true);
                pingField = hClass.getField("ping");
                pingField.setAccessible(true);
                handleClass = hClass;
                connectionClass = null;
            }
            Object connection = playerConnectionField.get(handle);
            if (connection == null) return null;
            Class<?> cClass = connection.getClass();
            if (isPendingPingMethod == null || connectionClass != cClass) {
                isPendingPingMethod = cClass.getDeclaredMethod("isPendingPing");
                isPendingPingMethod.setAccessible(true);
                setPendingPingMethod = cClass.getDeclaredMethod("setPendingPing", boolean.class);
                setPendingPingMethod.setAccessible(true);
                getLastPingMethod = cClass.getDeclaredMethod("getLastPing");
                getLastPingMethod.setAccessible(true);
                connectionClass = cClass;
            }
            return connection;
        } catch (Exception failed) {
            noteReflectionFailure(failed);
            return null;
        }
    }

    private int safePing(Player player) {
        try {
            if (pingField != null && handleClass != null && getHandleMethod != null) {
                Object handle = getHandleMethod.invoke(player);
                if (handle != null && handle.getClass() == handleClass) return Math.max(-1, pingField.getInt(handle));
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private double currentTps() {
        // Best-effort: Paper/Spigot expose recent TPS on the server object. Reflection keeps
        // this compiling against the plain Bukkit API.
        try {
            Object server = Bukkit.getServer();
            Object tps = server.getClass().getMethod("getTPS").invoke(server);
            if (tps instanceof double[] && ((double[]) tps).length > 0) return ((double[]) tps)[0];
        } catch (Exception ignored) { }
        return 0.0;
    }

    private void noteReflectionFailure(Exception failed) {
        if (reflectionFailureLogged) return;
        reflectionFailureLogged = true;
        reflectionDisabled = true;
        plugin.getLogger().warning("JASPR_KEEP event=reflection_unavailable reason="
                + failed.getClass().getSimpleName() + "; keepalive relaxation is off for this run.");
    }
}
