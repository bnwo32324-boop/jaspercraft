package local.eagler.testserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Server-authoritative latency diagnostics without addresses or credentials. */
final class NetworkDiagnostics {
    private static final int TICK_WINDOW = 400;
    private static final int MAX_SESSION_PING_SAMPLES = 720;

    private final JavaPlugin plugin;
    private final File logFile;
    private final double[] tickDurations = new double[TICK_WINDOW];
    private final Map<UUID, SessionStats> sessions = new HashMap<>();
    private final int sampleTicks;
    private final int alertPingMillis;
    private final int recoveryPingMillis;
    private final long maxLogBytes;
    private int tickCount;
    private int tickCursor;
    private int ticksUntilSample;
    private long previousTickNanos;
    private BufferedWriter writer;
    private BukkitTask task;
    private Method getHandleMethod;
    private Field pingField;
    private Class<?> craftPlayerClass;
    private Class<?> handleClass;
    private boolean writeFailureLogged;

    NetworkDiagnostics(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "network-diagnostics.jsonl");
        this.sampleTicks = Math.max(20, plugin.getConfig().getInt("network-diagnostics.sample-seconds", 5) * 20);
        this.alertPingMillis = Math.max(100, plugin.getConfig().getInt("network-diagnostics.alert-ping-ms", 250));
        this.recoveryPingMillis = Math.max(50, Math.min(alertPingMillis,
                plugin.getConfig().getInt("network-diagnostics.recovery-ping-ms", 150)));
        this.maxLogBytes = Math.max(1024L * 1024L,
                plugin.getConfig().getLong("network-diagnostics.max-log-bytes", 16L * 1024L * 1024L));
        this.ticksUntilSample = sampleTicks;
    }

    void start() {
        if (!plugin.getConfig().getBoolean("network-diagnostics.enabled", true)) return;
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data directory");
            }
            rotateIfNeeded();
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8));
            writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.monitor_started\",\"sampleSeconds\":"
                    + (sampleTicks / 20) + ",\"alertPingMs\":" + alertPingMillis + "}");
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (IOException failed) {
            plugin.getLogger().warning("JASPR_NET event=monitor_start_failed reason=log_unavailable");
        }
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        if (writer != null) {
            writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.monitor_stopped\"}");
            try { writer.close(); } catch (IOException ignored) { }
            writer = null;
        }
        sessions.clear();
    }

    void playerJoined(Player player) {
        sessions.put(player.getUniqueId(), new SessionStats(System.currentTimeMillis()));
        writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.player_joined\",\"player\":\""
                + json(player.getName()) + "\",\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size() + "}");
    }

    void playerQuit(Player player) {
        SessionStats stats = sessions.remove(player.getUniqueId());
        if (stats == null) return;
        long durationMillis = Math.max(0L, System.currentTimeMillis() - stats.joinedAtMillis);
        writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.player_session\",\"player\":\""
                + json(player.getName()) + "\",\"durationMs\":" + durationMillis
                + ",\"samples\":" + stats.pingSamples.size()
                + ",\"meanPingMs\":" + (stats.sampleCount == 0 ? 0 : Math.round((double) stats.pingSum / stats.sampleCount))
                + ",\"p95PingMs\":" + percentile(stats.pingSamples, 0.95) + ",\"maxPingMs\":" + stats.maxPing
                + ",\"highPingSamples\":" + stats.highPingSamples + "}");
    }

    String status(Player player) {
        TickStats ticks = tickStats();
        return "Network: " + player.getName() + " ping " + readPing(player) + " ms; server tick mean "
                + Math.round(ticks.mean) + " ms, p95 " + Math.round(ticks.p95) + " ms, max "
                + Math.round(ticks.max) + " ms.";
    }

    private void tick() {
        long now = System.nanoTime();
        if (previousTickNanos != 0L) {
            double elapsedMillis = (now - previousTickNanos) / 1_000_000.0;
            tickDurations[tickCursor] = Math.max(0.0, Math.min(60_000.0, elapsedMillis));
            tickCursor = (tickCursor + 1) % tickDurations.length;
            if (tickCount < tickDurations.length) tickCount++;
        }
        previousTickNanos = now;
        if (--ticksUntilSample > 0) return;
        ticksUntilSample = sampleTicks;
        samplePlayers();
    }

    private void samplePlayers() {
        TickStats ticks = tickStats();
        int online = Bukkit.getOnlinePlayers().size();
        for (Player player : Bukkit.getOnlinePlayers()) {
            int ping = readPing(player);
            SessionStats stats = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new SessionStats(System.currentTimeMillis()));
            stats.record(ping, alertPingMillis);
            String common = "\"player\":\"" + json(player.getName()) + "\",\"pingMs\":" + ping
                    + ",\"tickMeanMs\":" + Math.round(ticks.mean)
                    + ",\"tickP95Ms\":" + Math.round(ticks.p95)
                    + ",\"tickMaxMs\":" + Math.round(ticks.max)
                    + ",\"onlinePlayers\":" + online;
            writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.sample\"," + common + "}");
            if (!stats.alerted && stats.consecutiveHigh >= 2) {
                stats.alerted = true;
                writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.high_ping\"," + common + "}");
                plugin.getLogger().warning("JASPR_NET event=high_ping player=" + player.getName()
                        + " pingMs=" + ping + " tickP95Ms=" + Math.round(ticks.p95));
            } else if (stats.alerted && ping <= recoveryPingMillis) {
                stats.alerted = false;
                stats.consecutiveHigh = 0;
                writeLine("{\"at\":\"" + Instant.now() + "\",\"event\":\"network.ping_recovered\"," + common + "}");
                plugin.getLogger().info("JASPR_NET event=ping_recovered player=" + player.getName() + " pingMs=" + ping);
            }
        }
    }

    private int readPing(Player player) {
        try {
            Class<?> currentCraftClass = player.getClass();
            if (getHandleMethod == null || craftPlayerClass != currentCraftClass) {
                getHandleMethod = currentCraftClass.getMethod("getHandle");
                getHandleMethod.setAccessible(true);
                craftPlayerClass = currentCraftClass;
            }
            Object handle = getHandleMethod.invoke(player);
            Class<?> currentHandleClass = handle.getClass();
            if (pingField == null || handleClass != currentHandleClass) {
                pingField = currentHandleClass.getField("ping");
                pingField.setAccessible(true);
                handleClass = currentHandleClass;
            }
            return Math.max(0, pingField.getInt(handle));
        } catch (Exception unavailable) {
            return -1;
        }
    }

    private TickStats tickStats() {
        if (tickCount == 0) return new TickStats(0.0, 0.0, 0.0);
        double[] values = Arrays.copyOf(tickDurations, tickCount);
        Arrays.sort(values);
        double sum = 0.0;
        for (double value : values) sum += value;
        int p95Index = Math.min(values.length - 1, (int) Math.ceil(values.length * 0.95) - 1);
        return new TickStats(sum / values.length, values[Math.max(0, p95Index)], values[values.length - 1]);
    }

    private void rotateIfNeeded() throws IOException {
        if (!logFile.isFile() || logFile.length() < maxLogBytes) return;
        Files.move(logFile.toPath(), new File(logFile.getParentFile(), "network-diagnostics.previous.jsonl").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeLine(String line) {
        if (writer == null) return;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException failed) {
            if (!writeFailureLogged) {
                writeFailureLogged = true;
                plugin.getLogger().warning("JASPR_NET event=diagnostic_write_failed reason=io_error");
            }
        }
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') escaped.append('\\').append(c);
            else if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.toString();
    }

    private static int percentile(List<Integer> samples, double percentile) {
        if (samples.isEmpty()) return 0;
        List<Integer> sorted = new ArrayList<>(samples);
        sorted.sort(Integer::compareTo);
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1));
        return sorted.get(index);
    }

    private static final class TickStats {
        final double mean;
        final double p95;
        final double max;
        TickStats(double mean, double p95, double max) { this.mean = mean; this.p95 = p95; this.max = max; }
    }

    private static final class SessionStats {
        final long joinedAtMillis;
        final List<Integer> pingSamples = new ArrayList<>();
        long pingSum;
        int sampleCount;
        int maxPing;
        int highPingSamples;
        int consecutiveHigh;
        boolean alerted;
        SessionStats(long joinedAtMillis) { this.joinedAtMillis = joinedAtMillis; }
        void record(int ping, int alertThreshold) {
            if (ping < 0) return;
            pingSamples.add(ping);
            if (pingSamples.size() > MAX_SESSION_PING_SAMPLES) pingSamples.remove(0);
            pingSum += ping;
            sampleCount++;
            maxPing = Math.max(maxPing, ping);
            if (ping >= alertThreshold) { highPingSamples++; consecutiveHigh++; }
            else consecutiveHigh = 0;
        }
    }
}
