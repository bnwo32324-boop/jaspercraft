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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Adds the second diamond vein to terrain that was generated before there was one.
 *
 * The ore table went from one diamond vein per chunk to two. New chunks pick that
 * up for free; everywhere already explored would have kept the old density forever,
 * which in a world this far along is most of the map players actually reach.
 *
 * A chunk's ore is not a set of independent rolls -- every vein draws from one
 * shared Random, in table order -- so the added vein cannot be computed on its own.
 * OreVeins.replay runs the whole stream again and writes only the one vein that is
 * new, dry-running the rest. The veins already in the ground drew their numbers
 * first and are reproduced exactly, so nothing that is already there is disturbed.
 *
 * Only plain stone is ever replaced, so this cannot eat a cave, a player's chest or
 * anything that is not stone. It can speckle a stone wall somebody built between
 * y4 and y17; that was accepted deliberately as the price of retrofitting.
 *
 * Chunks come from the region files' own headers rather than from a coordinate
 * sweep, so this only ever touches ground that already exists and never causes a
 * single chunk to generate. Progress is recorded per region file, so a restart
 * halfway through resumes instead of starting over, and a finished world never
 * runs it twice.
 */
public final class DiamondRetrofit {
    private static final int DIAMOND = 56;
    /** The vein count the table used to carry. Anything at or past this index is new. */
    private static final int PREVIOUS_BASE_VEINS = 1;
    private static final String MARKER = "diamond-retrofit-v1";
    /** Chunks per pass. Each is one eight-step vein; the cost is chunk loading, not maths. */
    private static final int PER_PASS = 4;
    private static final long PERIOD_TICKS = 1L;

    private final Plugin plugin;
    private final World world;
    private final File progressFile;
    private final Set<String> doneRegions = new HashSet<String>();
    private final Deque<File> regions = new ArrayDeque<File>();
    private final Deque<long[]> pending = new ArrayDeque<long[]>();
    private Set<Long> existing = new HashSet<Long>();
    private String currentRegion;
    private BukkitTask task;
    private long chunksDone, blocksAdded, regionsDone;
    private Terrain terrain;
    private Caves caves;

    public DiamondRetrofit(Plugin plugin, World world) {
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
            if (!f.getName().endsWith(".mca")) continue;
            if (doneRegions.contains(f.getName())) continue;
            regions.add(f);
            queued++;
        }
        if (queued == 0) {
            plugin.getLogger().info("DIAMOND_RETROFIT complete regions=" + doneRegions.size() + " nothingToDo=true");
            return;
        }
        terrain = new Terrain(world.getSeed());
        caves = new Caves(terrain);
        plugin.getLogger().info("DIAMOND_RETROFIT start regions=" + queued + " alreadyDone=" + doneRegions.size());
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { pass(); }
        }, 40L, PERIOD_TICKS);
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
            plugin.getLogger().warning("DIAMOND_RETROFIT progress write failed " + e);
        }
    }

    private void pass() {
        for (int i = 0; i < PER_PASS; i++) {
            if (pending.isEmpty() && !nextRegion()) {
                plugin.getLogger().info("DIAMOND_RETROFIT complete regions=" + regionsDone
                        + " chunks=" + chunksDone + " diamondBlocksAdded=" + blocksAdded);
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
                plugin.getLogger().info("DIAMOND_RETROFIT region=" + currentRegion
                        + " chunks=" + chunksDone + " diamondBlocksAdded=" + blocksAdded
                        + " remaining=" + regions.size());
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
            Set<Long> present = new HashSet<Long>();
            RandomAccessFile in = null;
            try {
                in = new RandomAccessFile(file, "r");
                if (in.length() < 4096) { markDone(name); continue; }
                byte[] header = new byte[4096];
                in.readFully(header);
                for (int index = 0; index < 1024; index++) {
                    int at = index * 4;
                    int offset = ((header[at] & 0xff) << 16) | ((header[at + 1] & 0xff) << 8) | (header[at + 2] & 0xff);
                    int sectors = header[at + 3] & 0xff;
                    if (offset == 0 || sectors == 0) continue;
                    int cx = rx * 32 + (index & 31), cz = rz * 32 + (index >> 5);
                    found.add(new long[]{cx, cz});
                    present.add(key(cx, cz));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("DIAMOND_RETROFIT unreadable region " + name + " " + e);
                markDone(name);
                continue;
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignored) { }
            }
            if (found.isEmpty()) { markDone(name); continue; }
            existing = present;
            currentRegion = name;
            pending.addAll(found);
            return true;
        }
        return false;
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private void retrofit(int cx, int cz) {
        // Index of the vein the new table adds: everything below it is already down there.
        int added = OreVeins.veinCount(caves, cx, cz, DIAMOND, PREVIOUS_BASE_VEINS);
        WorldSink sink = new WorldSink(cx, cz);
        try {
            OreVeins.replay(terrain, caves, cx, cz, sink, DIAMOND, added);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("DIAMOND_RETROFIT chunk=" + cx + "," + cz + " " + e);
        }
        blocksAdded += sink.written;
        for (long k : sink.loaded) {
            int lx = (int) (k >> 32), lz = (int) k;
            world.unloadChunkRequest(lx, lz);
        }
    }

    /** Writes straight into the world, but only into chunks that already exist. */
    private final class WorldSink implements OreVeins.Sink {
        private final int lowX, highX, lowZ, highZ;
        private final List<Long> loaded = new ArrayList<Long>();
        private int written;

        WorldSink(int cx, int cz) {
            // A vein reaches a little past its own chunk; nothing reaches further than this.
            this.lowX = cx * 16 - 8; this.highX = cx * 16 + 23;
            this.lowZ = cz * 16 - 8; this.highZ = cz * 16 + 23;
        }

        public int minX() { return lowX; }
        public int maxX() { return highX; }
        public int minZ() { return lowZ; }
        public int maxZ() { return highZ; }

        private boolean reachable(int x, int z) {
            int bx = x >> 4, bz = z >> 4;
            if (!existing.contains(key(bx, bz))) return false;
            if (!world.isChunkLoaded(bx, bz)) {
                world.getChunkAt(bx, bz);
                loaded.add(key(bx, bz));
            }
            return true;
        }

        public int idAt(int x, int y, int z) {
            if (!reachable(x, z)) return -1;
            return world.getBlockAt(x, y, z).getTypeId();
        }

        @SuppressWarnings("deprecation")
        public void set(int x, int y, int z, int id, byte data) {
            if (!reachable(x, z)) return;
            Block block = world.getBlockAt(x, y, z);
            block.setTypeIdAndData(id, data, false);
            written++;
        }
    }
}
