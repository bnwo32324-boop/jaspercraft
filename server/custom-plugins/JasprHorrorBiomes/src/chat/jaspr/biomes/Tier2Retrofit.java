package chat.jaspr.biomes;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.RegionFileCache;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The tier-2 retrofit (3.29.0; owner 2026-10-04: "Don't regenerate the overworld, but do retrofit structures that would
 * normally spawn if it was regenerated"). Walks the chunks that existed when 3.29.0 first started (the v2 boundary):
 *  1. probes each with the populate builders in PROBE mode (no chunk is loaded) to learn which tier-2 sites a
 *     regeneration would put there;
 *  2. decides each such site once (Tier2 receipt): refused where any existing chunk under it (its box: the footprint
 *     and the margin it writes in) has an inhabited time over 1200 ticks -- somebody has spent time there -- or is guarded
 *     by an imported or Muse build, or where a JasprImportedWorldgen or JasprMuseMaps plan could reach it; deferred while
 *     a player is within 112 blocks; otherwise built into every populated chunk under it, one chunk per step, running the
 *     same builders in ONLY mode (only that site, only inside its box). Chunks under it that populate later build their
 *     share as they populate;
 *  3. gives every old populated chunk nobody has spent time in the extra vanilla-room attempts (Dungeons.plainExtras).
 * Never more than a few milliseconds a tick. Receipts make every step happen once across restarts; the probe is
 * repeated each start (it is cheap) and finds nothing left once all is decided.
 */
final class Tier2Retrofit {
    static final long INHABITED_LIMIT = 1200;
    static final int PLAYER_MARGIN = 112;
    private static final long BUDGET_NANOS = 4_000_000L, RETRY_MS = 60_000L;
    /** With players online, a chunk is built or given rooms at most once every this many ticks. */
    private static final int HEAVY_EVERY = 10;

    private static final class Build {
        final String key;
        final int x0, z0, x1, z1;
        final ArrayDeque<int[]> chunks;
        int done;
        long began;
        Build(String key, int[] box, List<int[]> chunks) {
            this.key = key; x0 = box[0]; z0 = box[1]; x1 = box[2]; z1 = box[3];
            this.chunks = new ArrayDeque<>(chunks);
        }
    }

    private final Plugin plugin;
    private final World world;
    private final long seed;
    private final Terrain terrain;
    private final Caves caves;
    private final File protectionRoot;
    private final ArrayDeque<int[]> probes = new ArrayDeque<>(), vanilla = new ArrayDeque<>();
    private final LinkedHashMap<String, int[]> sites = new LinkedHashMap<>();   // undecided: key -> box {x0, z0, x1, z1}
    private final ArrayDeque<String> decide = new ArrayDeque<>();
    private final ArrayDeque<Object[]> later = new ArrayDeque<>();               // {Long dueMs, String key | Build | int[] chunk}
    private final ArrayDeque<Build> builds = new ArrayDeque<>();
    private final Map<String, Integer> refusals = new TreeMap<>();
    private Field inhabitedField;
    private BukkitTask task;
    private int oldChunks, probed, probeFailures, offered, built, refused, deferred, builtChunks, vanillaBuilt, vanillaRefused, failures;
    private long lastProgress;
    private int tickNo, lastHeavy = -HEAVY_EVERY;

    Tier2Retrofit(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.seed = world.getSeed();
        this.terrain = new Terrain(seed);
        this.caves = new Caves(terrain);
        this.protectionRoot = new File(new File(plugin.getDataFolder(), "imported-protection-v1"), world.getUID().toString());
    }

    void start() {
        if (StructureRates.failedV2(seed) || Tier2.world(seed) == null) {
            plugin.getLogger().warning("TIER2_RETROFIT_OFF reason=" + (StructureRates.failedV2(seed) ? "boundary" : "ledger"));
            return;
        }
        try {
            inhabitedField = net.minecraft.server.v1_12_R1.Chunk.class.getDeclaredField("w");
            inhabitedField.setAccessible(true);
            if (inhabitedField.getType() != long.class) throw new NoSuchFieldException("Chunk inhabited-time field changed");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe("TIER2_RETROFIT_OFF reason=inhabited-time " + e.getMessage());
            return;
        }
        // The old chunks, and a ring of one chunk round them: a site just outside whose safety margin touches old ground is
        // left to the retrofit by populate (Tier2.allow), and only a probe of the chunks it builds in offers it.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<int[]> old = StructureRates.oldChunks2(seed);
        for (int[] c : old) { seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)); probes.add(c); vanilla.add(c); }
        for (int[] c : old)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (seen.add(((long) (c[0] + dx) << 32) ^ ((c[1] + dz) & 0xffffffffL))) probes.add(new int[]{c[0] + dx, c[1] + dz});
        oldChunks = old.size();
        plugin.getLogger().info("TIER2_RETROFIT_START oldChunks=" + oldChunks + " inhabitedLimit=" + INHABITED_LIMIT + " playerMargin=" + PLAYER_MARGIN
            + " " + Tier2.describe() + " " + PackPlans.state());
        lastProgress = System.currentTimeMillis();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    void stop() { if (task != null) { task.cancel(); task = null; } }

    // ---------------------------------------------------------------- the loop

    private void tick() {
        tickNo++;
        long until = System.nanoTime() + BUDGET_NANOS;
        try {
            while (System.nanoTime() < until && step()) { /* more work this tick */ }
        } catch (RuntimeException e) {
            failures++;
            plugin.getLogger().warning("TIER2_RETROFIT_STEP_FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (failures >= 50) { plugin.getLogger().severe("TIER2_RETROFIT_STOPPED after " + failures + " failures"); stop(); return; }
        }
        if (!pending()) { progress("TIER2_RETROFIT_COMPLETE"); stop(); return; }
        if (System.currentTimeMillis() - lastProgress > 60_000L) { lastProgress = System.currentTimeMillis(); progress("TIER2_RETROFIT_PROGRESS"); }
    }

    private boolean pending() { return !builds.isEmpty() || !probes.isEmpty() || !decide.isEmpty() || !vanilla.isEmpty() || !later.isEmpty(); }

    /** One bounded piece of work; false when nothing more can be done this tick (finished, waiting, or rate-limited). */
    private boolean step() {
        boolean heavy = Bukkit.getOnlinePlayers().isEmpty() || tickNo - lastHeavy >= HEAVY_EVERY;
        if (!builds.isEmpty()) {
            if (!heavy) return false;
            lastHeavy = tickNo;
            buildStep(builds.peek());
            return true;
        }
        if (!probes.isEmpty()) { probe(probes.poll()); return true; }
        if (!decide.isEmpty()) { decide(decide.poll()); return true; }
        if (!vanilla.isEmpty()) {
            int done = vanilla(vanilla.peek(), heavy);
            if (done == WAIT) return false;
            vanilla.poll();
            if (done == BUILT) lastHeavy = tickNo;
            return true;
        }
        if (!later.isEmpty()) {
            Object[] due = later.peek();
            if ((Long) due[0] > System.currentTimeMillis()) return false;
            later.poll();
            if (due[1] instanceof String) decide.add((String) due[1]);
            else if (due[1] instanceof Build) builds.add((Build) due[1]);
            else vanilla.add((int[]) due[1]);
            return true;
        }
        return false;
    }

    private void progress(String what) {
        plugin.getLogger().info(what + " oldChunks=" + oldChunks + " probed=" + probed + " sites=" + offered + " built=" + built + " refused=" + refused
            + refusals + " deferredNow=" + later.size() + " chunksBuilt=" + builtChunks + " vanillaChunks=" + vanillaBuilt + " vanillaRefused=" + vanillaRefused
            + " probeFailures=" + probeFailures + " failures=" + failures + " " + Tier2.describe());
    }

    // ---------------------------------------------------------------- 1. probing

    private void probe(int[] c) {
        probed++;
        final int cx = c[0], cz = c[1];
        if (Cities.reserved(seed, cx * 16, cz * 16, 16, 16)) return;    // a Lost City chunk builds nothing (RuinSupplies)
        final Chunk ghost = ghost(cx, cz);
        List<Tier2.Offer> offers;
        try {
            offers = Tier2.probe(() -> {
                StructurePlanner.stampTier2(world, ghost);
                Dungeons.build(world, ghost, terrain, caves, false);
            });
        } catch (RuntimeException e) {
            probeFailures++;
            if (probeFailures <= 5) plugin.getLogger().warning("TIER2_RETROFIT_PROBE_FAILED chunk=" + cx + "," + cz + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        for (Tier2.Offer o : offers) {
            if (Tier2.receipt(seed, o.key) != null) continue;
            int[] box = sites.get(o.key);
            if (box == null) {
                sites.put(o.key, new int[]{o.x0, o.z0, o.x1, o.z1});
                decide.add(o.key);
                offered++;
            } else {
                box[0] = Math.min(box[0], o.x0); box[1] = Math.min(box[1], o.z0); box[2] = Math.max(box[2], o.x1); box[3] = Math.max(box[3], o.z1);
            }
        }
    }

    /** A stand-in chunk for probing: its coordinates and world, nothing else (a probe reads no blocks). */
    private Chunk ghost(int cx, int cz) {
        return (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "getX": return cx;
                case "getZ": return cz;
                case "getWorld": return world;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                case "toString": return "Tier2Probe[" + cx + "," + cz + "]";
                default: throw new UnsupportedOperationException("a probe read the chunk: " + m.getName());
            }
        });
    }

    // ---------------------------------------------------------------- 2. deciding and building sites

    private void decide(String key) {
        int[] box = sites.get(key);
        if (box == null || Tier2.receipt(seed, key) != null) { sites.remove(key); return; }
        if (playerNear(box[0], box[1], box[2], box[3])) { defer(key); return; }
        int c0 = box[0] >> 4, d0 = box[1] >> 4, c1 = box[2] >> 4, d1 = box[3] >> 4;
        List<int[]> chunks = new ArrayList<>();
        String why = null;
        for (int cx = c0; cx <= c1 && why == null; cx++)
            for (int cz = d0; cz <= d1; cz++) {
                if (!world.isChunkGenerated(cx, cz)) continue;      // builds itself when it populates (receipt "built")
                if (guarded(cx, cz)) { why = "guarded"; break; }
                if (inhabited(cx, cz) > INHABITED_LIMIT) { why = "inhabited"; break; }
                if (populated(cx, cz)) chunks.add(new int[]{cx, cz});
            }
        if (why == null) why = Tier2.variety(key);                  // 3.30.0: as Tier2.allow decides new ground
        if (why == null) why = PackPlans.conflict(world, c0 - 1, d0 - 1, c1 + 1, d1 + 1);
        if (why != null) {
            Tier2.record(seed, key, false, why);
            sites.remove(key);
            refused++;
            refusals.merge(why, 1, Integer::sum);
            return;
        }
        if (!Tier2.record(seed, key, true, "retrofit")) { defer(key); return; }   // not on disk yet: try again later
        sites.remove(key);
        built++;
        Build b = new Build(key, box, chunks);
        b.began = System.nanoTime();
        if (chunks.isEmpty()) { logBuilt(b); return; }
        builds.add(b);
    }

    private void buildStep(Build b) {
        if (playerNear(b.x0, b.z0, b.x1, b.z1)) { builds.poll(); defer(b); return; }   // never while someone is close
        int[] c = b.chunks.poll();
        if (c != null) {
            boolean wasLoaded = world.isChunkLoaded(c[0], c[1]);
            final Chunk chunk = world.getChunkAt(c[0], c[1]);
            try {
                Tier2.only(b.key, b.x0, b.z0, b.x1, b.z1, () -> {
                    StructurePlanner.stampTier2(world, chunk);
                    Dungeons.build(world, chunk, terrain, caves, false);
                });
                ChunkLight.initialize(chunk);
                world.refreshChunk(c[0], c[1]);
                builtChunks++;
                b.done++;
            } catch (RuntimeException e) {
                failures++;
                plugin.getLogger().warning("TIER2_RETROFIT_BUILD_FAILED key=" + b.key + " chunk=" + c[0] + "," + c[1] + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (!wasLoaded) world.unloadChunkRequest(c[0], c[1]);
            }
        }
        if (b.chunks.isEmpty()) { builds.poll(); logBuilt(b); }
    }

    private void logBuilt(Build b) {
        plugin.getLogger().info("TIER2_RETROFIT_BUILT key=" + b.key + " box=" + b.x0 + "," + b.z0 + ".." + b.x1 + "," + b.z1 + " chunks=" + b.done
            + " ms=" + (System.nanoTime() - b.began) / 1_000_000L);
    }

    private void defer(Object what) {
        deferred++;
        later.add(new Object[]{System.currentTimeMillis() + RETRY_MS, what});
    }

    // ---------------------------------------------------------------- 3. vanilla rooms

    private static final int SKIPPED = 0, BUILT = 1, WAIT = 2;

    /** One old chunk's extra rooms: SKIPPED (decided, deferred or nothing to do), BUILT, or WAIT (needs a heavy step). */
    private int vanilla(int[] c, boolean heavy) {
        int cx = c[0], cz = c[1];
        String key = Tier2.vanillaKey(cx, cz);
        if (Tier2.receipt(seed, key) != null) return SKIPPED;
        if (!world.isChunkGenerated(cx, cz) || !populated(cx, cz)) return SKIPPED;   // populates later: its extras come then
        String why = Cities.reserved(seed, cx * 16, cz * 16, 16, 16) ? "city" : guarded(cx, cz) ? "guarded"
            : inhabited(cx, cz) > INHABITED_LIMIT ? "inhabited" : null;
        if (why != null) { Tier2.record(seed, key, false, why); vanillaRefused++; return SKIPPED; }
        if (playerNear(cx * 16, cz * 16, cx * 16 + 15, cz * 16 + 15)) { defer(c); return SKIPPED; }
        if (!heavy) return WAIT;
        if (!Tier2.record(seed, key, true, "retrofit")) { defer(c); return SKIPPED; }
        boolean wasLoaded = world.isChunkLoaded(cx, cz);
        Chunk chunk = world.getChunkAt(cx, cz);
        try {
            Dungeons.plainExtras(chunk, terrain, caves);
            ChunkLight.initialize(chunk);
            world.refreshChunk(cx, cz);
            vanillaBuilt++;
        } catch (RuntimeException e) {
            failures++;
            plugin.getLogger().warning("TIER2_RETROFIT_VANILLA_FAILED chunk=" + cx + "," + cz + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (!wasLoaded) world.unloadChunkRequest(cx, cz);
        }
        return BUILT;
    }

    // ---------------------------------------------------------------- the safety checks

    private boolean playerNear(int x0, int z0, int x1, int z1) {
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (l.getX() > x0 - PLAYER_MARGIN && l.getX() < x1 + PLAYER_MARGIN && l.getZ() > z0 - PLAYER_MARGIN && l.getZ() < z1 + PLAYER_MARGIN) return true;
        }
        return false;
    }

    private boolean guarded(int cx, int cz) {
        return new File(new File(protectionRoot, Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32)), cx + "_" + cz + ".guard").isFile();
    }

    /** Ticks players have spent near this chunk; unreadable counts as lived-in (never built on). */
    private long inhabited(int cx, int cz) {
        try {
            if (world.isChunkLoaded(cx, cz)) return inhabitedField.getLong(((CraftChunk) world.getChunkAt(cx, cz)).getHandle());
            NBTTagCompound root = RegionFileCache.d(world.getWorldFolder(), cx, cz);
            if (root == null || !root.hasKeyOfType("Level", 10)) return 0;
            return root.getCompound("Level").getLong("InhabitedTime");
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /** Whether this chunk has populated (its structures were laid); unreadable counts as not. */
    private boolean populated(int cx, int cz) {
        try {
            if (world.isChunkLoaded(cx, cz)) return ((CraftChunk) world.getChunkAt(cx, cz)).getHandle().isDone();
            NBTTagCompound root = RegionFileCache.d(world.getWorldFolder(), cx, cz);
            return root != null && root.hasKeyOfType("Level", 10) && root.getCompound("Level").getBoolean("TerrainPopulated");
        } catch (Exception e) {
            return false;
        }
    }
}
