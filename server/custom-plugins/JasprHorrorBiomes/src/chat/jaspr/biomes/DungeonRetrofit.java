package chat.jaspr.biomes;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Puts the tightened dungeon lattice, and the six new sites, into ground that already exists.
 *
 * Every site is a pure function of the seed and its lattice cell, so the same pass that runs
 * at generation can be run again later over old chunks. What it must not do is build the
 * rooms that are already down there: those would come out identical block for block, but
 * their chests would be refilled and anything a player had changed inside would be undone.
 * Dungeons.build in retrofit mode handles exactly that -- plain rooms resume at the attempt
 * the old count stopped at, and a built site is only raised where the old, wider lattice did
 * not already put one.
 *
 * Chunks come from the region files' own headers, so this only ever touches ground that
 * exists and never causes a chunk to generate. Progress is written per region file, so a
 * restart resumes rather than starting over and a finished world never runs it twice.
 *
 * Unlike the ore pass this one builds rooms, which means it replaces whatever was in their
 * footprint. Around the world spawn that is very likely to be somebody's base, so a guard
 * radius is skipped by default and can be widened from config.
 */
public final class DungeonRetrofit {
    /* Bumped when there is new work to do over old ground. v8 drops the spawn guard, so
     * the fifty-two chunks around spawn finally get the same treatment as everywhere
     * else and ground explored before any of this is now identical to ground generated
     * after it. Every region is walked again. The dungeon lattices already laid are
     * skipped by Dungeons.build's retrofit mode; the big set pieces are pure functions of
     * the seed, so re-running them reproduces what is already standing block for block
     * and raises anything new beside it. */
    private static final String MARKER = "dungeon-retrofit-v8";
    /** Rooms are far heavier than ore, so this walks a good deal more slowly. */
    private static final int PER_PASS = 2;

    private final Plugin plugin;
    private final World world;
    private final File progressFile;
    private final Set<String> doneRegions = new HashSet<String>();
    private final Deque<File> regions = new ArrayDeque<File>();
    private final Deque<long[]> pending = new ArrayDeque<long[]>();
    private String currentRegion;
    private BukkitTask task;
    private long chunksDone, regionsDone, skipped;
    private int guard;
    private int spawnX, spawnZ;
    private Terrain terrain;
    private Caves caves;

    public DungeonRetrofit(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.progressFile = new File(plugin.getDataFolder(), MARKER + ".txt");
    }

    public void start() {
        File dir = new File(world.getWorldFolder(), "region");
        if (!dir.isDirectory()) return;
        load();
        File[] files = dir.listFiles();
        if (files == null) return;
        int queued = 0;
        for (File f : files) {
            if (!f.getName().endsWith(".mca") || doneRegions.contains(f.getName())) continue;
            regions.add(f);
            queued++;
        }
        if (queued == 0) {
            plugin.getLogger().info("DUNGEON_RETROFIT complete regions=" + doneRegions.size() + " nothingToDo=true");
            return;
        }
        // Off by default, because a guard is the one thing that makes old ground different
        // from new: a chunk generated today gets every structure that reaches it, guard or
        // no guard, so skipping chunks near spawn leaves exactly the structures that
        // straddle it half built. The ground inside the old sixty-four block radius was
        // checked and holds nothing anybody built. Set this in config to put it back.
        guard = Math.max(0, plugin.getConfig().getInt("dungeon-retrofit.spawn-guard-blocks", 0));
        Location spawn = world.getSpawnLocation();
        spawnX = spawn.getBlockX();
        spawnZ = spawn.getBlockZ();
        terrain = new Terrain(world.getSeed());
        caves = new Caves(terrain);
        plugin.getLogger().info("DUNGEON_RETROFIT start regions=" + queued + " alreadyDone=" + doneRegions.size()
                + " spawnGuard=" + guard);
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { pass(); }
        }, 200L, 1L);
    }

    public void stop() { if (task != null) { task.cancel(); task = null; } }

    private void load() {
        try {
            if (!progressFile.isFile()) return;
            for (String line : new String(Files.readAllBytes(progressFile.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String name = line.trim();
                if (!name.isEmpty() && name.endsWith(".mca")) doneRegions.add(name);
            }
        } catch (IOException ignored) { }
    }

    private void markDone(String region) {
        doneRegions.add(region);
        try {
            File parent = progressFile.getParentFile();
            if (parent != null) parent.mkdirs();
            StringBuilder out = new StringBuilder();
            for (String name : doneRegions) out.append(name).append('\n');
            Files.write(progressFile.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("DUNGEON_RETROFIT progress write failed " + e);
        }
    }

    private void pass() {
        for (int i = 0; i < PER_PASS; i++) {
            if (pending.isEmpty() && !nextRegion()) {
                plugin.getLogger().info("DUNGEON_RETROFIT complete regions=" + regionsDone
                        + " chunks=" + chunksDone + " skippedNearSpawn=" + skipped);
                stop();
                return;
            }
            if (pending.isEmpty()) return;
            long[] chunk = pending.poll();
            retrofit((int) chunk[0], (int) chunk[1]);
            chunksDone++;
            if (pending.isEmpty() && currentRegion != null) {
                markDone(currentRegion);
                regionsDone++;
                plugin.getLogger().info("DUNGEON_RETROFIT region=" + currentRegion + " chunks=" + chunksDone
                        + " skippedNearSpawn=" + skipped + " remaining=" + regions.size());
                currentRegion = null;
            }
        }
    }

    /** Reads a region file's 4KiB header: a non-zero entry means that chunk exists. */
    private boolean nextRegion() {
        while (!regions.isEmpty()) {
            File file = regions.poll();
            String name = file.getName();
            String[] parts = name.split("\\.");
            if (parts.length < 4) continue;
            int rx, rz;
            try { rx = Integer.parseInt(parts[1]); rz = Integer.parseInt(parts[2]); }
            catch (NumberFormatException bad) { continue; }
            List<long[]> found = new ArrayList<long[]>();
            RandomAccessFile in = null;
            try {
                in = new RandomAccessFile(file, "r");
                if (in.length() < 4096) { markDone(name); continue; }
                byte[] header = new byte[4096];
                in.readFully(header);
                for (int index = 0; index < 1024; index++) {
                    int at = index * 4;
                    int offset = ((header[at] & 0xff) << 16) | ((header[at + 1] & 0xff) << 8) | (header[at + 2] & 0xff);
                    if (offset == 0 || (header[at + 3] & 0xff) == 0) continue;
                    found.add(new long[]{rx * 32 + (index & 31), rz * 32 + (index >> 5)});
                }
            } catch (IOException e) {
                plugin.getLogger().warning("DUNGEON_RETROFIT unreadable region " + name + " " + e);
                markDone(name);
                continue;
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignored) { }
            }
            if (found.isEmpty()) { markDone(name); continue; }
            currentRegion = name;
            pending.addAll(found);
            return true;
        }
        return false;
    }

    private void retrofit(int cx, int cz) {
        // A room dropped on the world spawn would land on whatever has been built there.
        if (guard > 0) {
            int dx = cx * 16 + 8 - spawnX, dz = cz * 16 + 8 - spawnZ;
            if (dx * dx + dz * dz <= guard * guard) { skipped++; return; }
        }
        boolean loaded = world.isChunkLoaded(cx, cz);
        try {
            Chunk chunk = world.getChunkAt(cx, cz);
            ChunkLight.initialize(chunk);
            Dungeons.build(world, chunk, terrain, caves, true);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("DUNGEON_RETROFIT chunk=" + cx + "," + cz + " " + e);
        } finally {
            if (!loaded) world.unloadChunkRequest(cx, cz);
        }
    }
}
