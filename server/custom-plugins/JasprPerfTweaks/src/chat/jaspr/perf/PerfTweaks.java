package chat.jaspr.perf;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.PathfinderGoal;
import net.minecraft.server.v1_12_R1.PathfinderGoalInteract;
import net.minecraft.server.v1_12_R1.PathfinderGoalLookAtPlayer;
import net.minecraft.server.v1_12_R1.PathfinderGoalLookAtTradingPlayer;
import net.minecraft.server.v1_12_R1.PathfinderGoalRandomLookaround;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * JasperCraft performance tweaks. Measured in the performance sandbox (JasperCraft-PerfSandbox, PERFORMANCE_UPDATE.md).
 *
 * 1. mob-ai.trim-idle-look (the "AI Improvements" idea): removes the purely cosmetic idle-look goals
 *    (RandomLookaround, LookAtPlayer, villager Interact) from mobs. Movement, targeting, attacking and
 *    pathfinding are untouched; mobs simply stop turning their heads around while idle.
 * 2. mob-ai.distance-throttle (Pufferfish/Airplane "DAB", Lithium): mobs far from every player re-evaluate which goal
 *    to start less often (vanilla every 3 ticks, up to every 10 far away); running goals still update every tick.
 * 3. network.flush-consolidation (Krypton): Netty's FlushConsolidationHandler merges the socket flushes queued in one
 *    event-loop turn; packet contents and order are unchanged.
 * 4. /jprgen (the "Chunk-Pregenerator" idea): generates terrain ahead of time inside a time budget per tick,
 *    pausing while more than pregen.max-players are online, so exploring players load chunks from disk
 *    instead of generating them on the main thread. Only runs when an operator starts it.
 */
public final class PerfTweaks extends JavaPlugin implements Listener {
    private Field goalItems, itemGoal;
    private boolean trimLook;
    private long trimmedGoals, trimmedMobs;
    private Pregen pregen;

    @Override public void onEnable() {
        saveDefaultConfig();
        trimLook = getConfig().getBoolean("mob-ai.trim-idle-look", true);
        try {
            goalItems = PathfinderGoalSelector.class.getDeclaredField("b");
            goalItems.setAccessible(true);
            Class<?> item = Class.forName("net.minecraft.server.v1_12_R1.PathfinderGoalSelector$PathfinderGoalSelectorItem");
            itemGoal = item.getDeclaredField("a");
            itemGoal.setAccessible(true);
        } catch (Exception e) {
            trimLook = false;
            getLogger().warning("PERF_TWEAKS mob-ai disabled: goal selector layout not recognised (" + e + ")");
        }
        getServer().getPluginManager().registerEvents(this, this);
        if (trimLook) for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) trim(e);
        if (getConfig().getBoolean("mob-ai.distance-throttle.enabled", false)) startThrottle();
        flushConsolidation = getConfig().getBoolean("network.flush-consolidation", false);
        if (flushConsolidation) for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) consolidate(p);
        getLogger().info("PERF_TWEAKS_READY trimIdleLook=" + trimLook + " trimmedMobs=" + trimmedMobs + " flushConsolidation=" + flushConsolidation);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (trimmedMobs > 0 || throttled > 0) getLogger().info("PERF_TWEAKS mobs=" + trimmedMobs + " goalsRemoved=" + trimmedGoals + " rateChanges=" + throttled);
        }, 6000L, 6000L);
    }

    @Override public void onDisable() { if (pregen != null) pregen.stop("disabled"); }

    // ---------------- mob-ai.distance-throttle (Pufferfish/Airplane "DAB", Lithium goal-selector interval) ----------------
    // Vanilla re-evaluates which goals a mob should start every 3 ticks. For mobs further from every player that interval
    // grows by one tick per 8 blocks beyond `start-blocks`, up to `max-interval`. Running goals (chasing, attacking) still
    // update every tick; only picking a new goal is delayed, by at most a fraction of a second at the distances involved.
    private Field selectorRate;
    private int throttleStart, throttleMax;
    private boolean throttleTargets;
    private long throttled;

    private void startThrottle() {
        try {
            selectorRate = PathfinderGoalSelector.class.getDeclaredField("f");
            selectorRate.setAccessible(true);
        } catch (Exception e) {
            getLogger().warning("PERF_TWEAKS distance-throttle disabled: " + e);
            return;
        }
        throttleStart = getConfig().getInt("mob-ai.distance-throttle.start-blocks", 16);
        throttleMax = getConfig().getInt("mob-ai.distance-throttle.max-interval", 10);
        throttleTargets = getConfig().getBoolean("mob-ai.distance-throttle.target-selector", false);
        getServer().getScheduler().runTaskTimer(this, this::throttleTick, 20L, 10L);
        getLogger().info("PERF_TWEAKS distance-throttle start=" + throttleStart + " max=" + throttleMax + " targets=" + throttleTargets);
    }

    private int intervalFor(double distanceSquared) {
        if (distanceSquared <= (double) throttleStart * throttleStart) return 3;
        return Math.min(throttleMax, 3 + (int) ((Math.sqrt(distanceSquared) - throttleStart) / 8.0));
    }

    private void setRate(PathfinderGoalSelector selector, int rate) throws IllegalAccessException {
        int current = selectorRate.getInt(selector);
        if (current >= 3 && current != rate) { selectorRate.setInt(selector, rate); throttled++; } // leave custom (<3) rates alone
    }

    private void throttleTick() {
        for (World w : Bukkit.getWorlds()) {
            List<org.bukkit.entity.Player> players = w.getPlayers();
            if (players.isEmpty()) continue;
            int n = players.size();
            double[] px = new double[n], py = new double[n], pz = new double[n];
            for (int i = 0; i < n; i++) { org.bukkit.Location l = players.get(i).getLocation(); px[i] = l.getX(); py[i] = l.getY(); pz[i] = l.getZ(); }
            for (org.bukkit.entity.LivingEntity le : w.getLivingEntities()) {
                if (!(le instanceof CraftLivingEntity)) continue;
                EntityLiving h = ((CraftLivingEntity) le).getHandle();
                if (!(h instanceof EntityInsentient)) continue;
                double best = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) { double dx = h.locX - px[i], dy = h.locY - py[i], dz = h.locZ - pz[i]; best = Math.min(best, dx * dx + dy * dy + dz * dz); }
                int rate = intervalFor(best);
                try {
                    setRate(((EntityInsentient) h).goalSelector, rate);
                    if (throttleTargets) setRate(((EntityInsentient) h).targetSelector, rate);
                } catch (IllegalAccessException ignored) { }
            }
        }
    }

    // ---------------- network.flush-consolidation (Krypton) ----------------
    // Every packet the server sends is its own flush -- its own socket write. Netty's FlushConsolidationHandler merges the
    // flushes queued in one event-loop turn into one write. Packet contents and order are unchanged.
    private boolean flushConsolidation;

    private void consolidate(org.bukkit.entity.Player p) {
        try {
            final io.netty.channel.Channel ch = ((org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) p).getHandle().playerConnection.networkManager.channel;
            if (ch == null) return;
            ch.eventLoop().execute(() -> {
                if (ch.isOpen() && ch.pipeline().get("jaspr_flush") == null)
                    ch.pipeline().addFirst("jaspr_flush", new io.netty.handler.flush.FlushConsolidationHandler(
                            256, true)); // 256 = netty default explicit-flush threshold
            });
        } catch (Exception e) {
            getLogger().warning("PERF_TWEAKS flush-consolidation failed for a player: " + e.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) { if (flushConsolidation) consolidate(event.getPlayer()); }

    private static boolean cosmetic(PathfinderGoal goal) {
        Class<?> c = goal.getClass();
        // Trading villagers keep looking at their customer; only idle glances are removed.
        return c == PathfinderGoalRandomLookaround.class || c == PathfinderGoalLookAtPlayer.class || c == PathfinderGoalInteract.class
                && !(goal instanceof PathfinderGoalLookAtTradingPlayer);
    }

    private void trim(Entity entity) {
        if (!trimLook || !(entity instanceof CraftLivingEntity)) return;
        EntityLiving handle = ((CraftLivingEntity) entity).getHandle();
        if (!(handle instanceof EntityInsentient)) return;
        PathfinderGoalSelector selector = ((EntityInsentient) handle).goalSelector;
        try {
            List<PathfinderGoal> remove = new ArrayList<>(2);
            for (Object item : (Set<?>) goalItems.get(selector)) {
                PathfinderGoal goal = (PathfinderGoal) itemGoal.get(item);
                if (cosmetic(goal)) remove.add(goal);
            }
            if (remove.isEmpty()) return;
            for (PathfinderGoal goal : remove) selector.a(goal); // vanilla removeTask: also resets it if running
            trimmedGoals += remove.size();
            trimmedMobs++;
        } catch (IllegalAccessException ignored) { }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) { trim(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!trimLook) return;
        for (Entity e : event.getChunk().getEntities()) trim(e);
    }

    // ---------------- pregenerator ----------------

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(pregen == null ? "No pregeneration running." : pregen.status());
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (pregen != null) pregen.stop("command");
            return true;
        }
        if (args[0].equalsIgnoreCase("start") && args.length >= 3) {
            World world = Bukkit.getWorld(args[1]);
            if (world == null) { sender.sendMessage("Unknown world " + args[1]); return true; }
            int radius = Integer.parseInt(args[2]);
            int cx = args.length >= 5 ? Integer.parseInt(args[3]) : world.getSpawnLocation().getBlockX() >> 4;
            int cz = args.length >= 5 ? Integer.parseInt(args[4]) : world.getSpawnLocation().getBlockZ() >> 4;
            if (radius < 1 || radius > 400) { sender.sendMessage("radius must be 1..400 chunks"); return true; }
            if (pregen != null) pregen.stop("replaced");
            pregen = new Pregen(world, cx, cz, radius);
            sender.sendMessage("Pregenerating " + world.getName() + " radius " + radius + " chunks around " + cx + "," + cz);
            return true;
        }
        return false;
    }

    /** Square spiral, ring by ring; chunks are released once every neighbour has been generated, so all get populated. */
    private final class Pregen implements Runnable {
        final World world; final int cx, cz, radius;
        final long budgetNanos = getConfig().getLong("pregen.tick-budget-ms", 30L) * 1_000_000L;
        final int maxPlayers = getConfig().getInt("pregen.max-players", 0);
        final ArrayDeque<List<long[]>> heldRings = new ArrayDeque<>();
        final BukkitTask task;
        final long startedAt = System.nanoTime();
        int ring = 0, pos = 0; List<long[]> current = new ArrayList<>();
        long generated, skipped, pausedTicks; boolean done;

        Pregen(World world, int cx, int cz, int radius) {
            this.world = world; this.cx = cx; this.cz = cz; this.radius = radius;
            task = getServer().getScheduler().runTaskTimer(PerfTweaks.this, this, 1L, 1L);
            getLogger().info("JASPR_PREGEN event=start world=" + world.getName() + " center=" + cx + "," + cz + " radius=" + radius
                    + " budgetMs=" + budgetNanos / 1_000_000L + " maxPlayers=" + maxPlayers);
        }

        int ringSize(int r) { return r == 0 ? 1 : 8 * r; }

        int[] ringCell(int r, int i) {
            if (r == 0) return new int[] {0, 0};
            int side = 2 * r, s = i / side, o = i % side;
            switch (s) {
                case 0: return new int[] {-r + o, -r};
                case 1: return new int[] {r, -r + o};
                case 2: return new int[] {r - o, r};
                default: return new int[] {-r, r - o};
            }
        }

        @Override public void run() {
            if (done) return;
            if (Bukkit.getOnlinePlayers().size() > maxPlayers) { pausedTicks++; return; }
            long end = System.nanoTime() + budgetNanos;
            while (System.nanoTime() < end) {
                if (pos >= ringSize(ring)) {
                    heldRings.addLast(current);
                    current = new ArrayList<>(ringSize(ring + 1));
                    while (heldRings.size() > 2) release(heldRings.removeFirst());
                    ring++; pos = 0;
                    if (ring > radius) { finish(); return; }
                    if (ring % 10 == 0) getLogger().info("JASPR_PREGEN event=progress " + status());
                }
                int[] c = ringCell(ring, pos++);
                int x = cx + c[0], z = cz + c[1];
                boolean existed = world.isChunkGenerated(x, z);
                boolean wasLoaded = world.isChunkLoaded(x, z);
                if (existed) skipped++; else generated++;
                world.loadChunk(x, z, true);
                if (!wasLoaded) current.add(new long[] {x, z});
            }
        }

        void release(List<long[]> chunks) { for (long[] c : chunks) world.unloadChunkRequest((int) c[0], (int) c[1]); }

        void finish() {
            while (!heldRings.isEmpty()) release(heldRings.removeFirst());
            release(current);
            done = true; task.cancel();
            getLogger().info("JASPR_PREGEN event=done " + status());
        }

        void stop(String why) {
            if (done) return;
            while (!heldRings.isEmpty()) release(heldRings.removeFirst());
            release(current);
            done = true; task.cancel();
            getLogger().info("JASPR_PREGEN event=stopped reason=" + why + " " + status());
        }

        String status() {
            long total = (2L * radius + 1) * (2L * radius + 1);
            long doneCells = generated + skipped;
            double secs = (System.nanoTime() - startedAt) / 1e9;
            return "world=" + world.getName() + " ring=" + ring + "/" + radius + " cells=" + doneCells + "/" + total
                    + " generated=" + generated + " existing=" + skipped + " pausedTicks=" + pausedTicks
                    + String.format(" seconds=%.0f rate=%.1f/s", secs, generated / Math.max(1e-9, secs));
        }
    }
}
