package chat.jaspr.biomes;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Wakes water that was built, not poured.
 *
 * The generator writes into a chunk's block array, and a block written that way
 * is never announced: no neighbour is notified, no fluid tick is scheduled. Water
 * left in the wrong place by an earlier build therefore stays exactly where it
 * was put, for good, however obviously it ought to be falling. Terrain generated
 * from now on has the fault fixed at the source; terrain that already exists has
 * to be told.
 *
 * Telling it is a single move: turn the still block back into its flowing form
 * with physics applied. That gives the block the update it never got, and from
 * there Minecraft's own fluid rules take over and do the right thing -- a
 * stranded sheet falls and drains, a wall pours into the ravine beside it, a pool
 * that was fine all along restabilises as still water within a tick or two.
 *
 * Every chunk is woken once and remembered, because the check costs a scan and a
 * chunk the player revisits has nothing left to find.
 */
public final class WaterRepair implements Listener {
    private final Plugin plugin;
    private final Set<Long> done = new HashSet<Long>();
    private final ArrayDeque<Long> queue = new ArrayDeque<Long>();
    private final Set<Long> queued = new HashSet<Long>();
    /** Chunks that have had their seam pass with neighbours present. Persisted with done. */
    private final Set<Long> seamed = new HashSet<Long>();
    private final File store;
    private World target;
    private int woken, chunks, cleared;
    private boolean dirty;

    public WaterRepair(Plugin plugin) {
        this.plugin = plugin;
        // Versioned, so a pass that learned to look for something new is not told every
        // chunk was already dealt with by the pass that could not see it.
        this.store = new File(plugin.getDataFolder(), "terrain-repair-v4.dat");
    }

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // The overworld does not exist yet -- plugins enable first, precisely so this
        // one can hand the server its generator -- so the world is taken from the
        // first chunk that loads in it rather than looked up here and found null.
        Bukkit.getScheduler().runTask(plugin, new Runnable() { public void run() {
            World w = Bukkit.getWorld("world");
            if (w == null) return;
            target = w;
            for (Chunk c : w.getLoadedChunks()) offer(c.getX(), c.getZ());
        }});
        for (int i = 0; i < 4; i++) Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 60L + i, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 2400L, 2400L);
    }

    public void stop() {
        plugin.getLogger().info("TERRAIN_REPAIR_STOP chunks=" + chunks + " settledWater=" + woken + " prunedFloaters=" + cleared + " pending=" + queue.size() + " remembered=" + done.size() + " seamed=" + seamed.size());
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent e) {
        if (!"world".equals(e.getWorld().getName())) return;
        if (target == null) target = e.getWorld();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        long key = key(cx, cz);
        if (imported(e.getChunk())) {
            if (done.add(key) | seamed.add(key)) dirty = true;
            return;
        }
        if (e.isNewChunk()) {
            // Recorded as done at once: the full pass is for terrain the old generator
            // left, and a chunk this generator built must never receive it -- it would
            // strip the waterfalls that were written into it on purpose.
            if (done.add(key)) dirty = true;
            // The generator cleans a chunk as far as a chunk can be cleaned alone. What
            // it cannot settle is the seam, because the far side does not exist yet.
            // So a fresh chunk is owed one more look once it has neighbours -- and its
            // neighbours are owed another, since the seam they could not judge before
            // can be judged from both sides now.
            for (int[] n : new int[][]{{cx, cz}, {cx - 1, cz}, {cx + 1, cz}, {cx, cz - 1}, {cx, cz + 1}}) {
                if (seamed.remove(key(n[0], n[1]))) dirty = true;
                offer(n[0], n[1]);
            }
            return;
        }
        offer(cx, cz);
    }

    private void offer(int cx, int cz) {
        long key = key(cx, cz);
        if (done.contains(key) && seamed.contains(key)) return;   // nothing owed
        if (!queued.add(key)) return;
        queue.add(key);
    }

    /**
     * A few chunks a tick. A chunk not yet in the ledger gets the full pass (it was
     * built by an older generator) and is then queued again for its seam pass; a
     * chunk in the ledger but not yet seamed gets the seam pass. A chunk that is not
     * loaded when its turn comes keeps whatever it is owed, and the next load of it
     * queues it again.
     */
    private void drain() {
        if (target == null || queue.isEmpty()) return;
        long key = queue.poll();
        queued.remove(key);
        int cx = (int) (key >> 32), cz = (int) key;
        if (!target.isChunkLoaded(cx, cz)) return;
        Chunk c = target.getChunkAt(cx, cz);
        if (imported(c)) {
            if (done.add(key) | seamed.add(key)) dirty = true;
            return;
        }
        if (!done.contains(key)) {
            done.add(key); dirty = true;
            woken += repair(c);
            chunks++;
            offer(cx, cz);                                       // now owed its seam pass
        } else if (!seamed.contains(key)) {
            seamed.add(key); dirty = true;
            cleared += seam(c);
            chunks++;
        }
        if (chunks % 250 == 0)
            plugin.getLogger().info("TERRAIN_REPAIR chunks=" + chunks + " settledWater=" + woken + " prunedFloaters=" + cleared + " pending=" + queue.size());
    }

    /**
     * One pass over a chunk that already exists, reading from a snapshot -- a plain
     * array read rather than sixty thousand block objects -- and writing only where
     * something is actually wrong.
     */
    private int repair(Chunk c) {
        ChunkSnapshot snap = c.getChunkSnapshot(false, false, false);
        int woke = 0;
        try {
            woke = CaveSprings.settleExisting(c, snap);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("WATER_SETTLE_FAILED chunk=" + c.getX() + "," + c.getZ() + " " + ex);
        }
        // Orphaned rock and stranded trees, taken down under the guard rails in Floaters.
        try {
            cleared += Floaters.prune(c, snap, 64, Floaters.worldApron(c.getWorld(), c.getX(), c.getZ()));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("FLOATER_PRUNE_FAILED chunk=" + c.getX() + "," + c.getZ() + " " + ex);
        }
        return woke;
    }

    /** The seam pass: floaters only, with the neighbours consulted. */
    private int seam(Chunk c) {
        try {
            return Floaters.prune(c, c.getChunkSnapshot(false, false, false), 64,
                Floaters.worldApron(c.getWorld(), c.getX(), c.getZ()));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("FLOATER_PRUNE_FAILED chunk=" + c.getX() + "," + c.getZ() + " " + ex);
            return 0;
        }
    }

    private static boolean liquid(int id) { return id == 8 || id == 9 || id == 10 || id == 11; }

    /** Persistently protect imported chunks even if the importing plugin cannot restart. */
    private boolean imported(Chunk c) {
        if (c.getBlock(0, 0, 0).hasMetadata("jaspr-imported-v1")) return true;
        int cx = c.getX(), cz = c.getZ();
        File root = new File(plugin.getDataFolder(), "imported-protection-v1");
        File world = new File(root, c.getWorld().getUID().toString());
        File region = new File(world, Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32));
        return new File(region, cx + "_" + cz + ".guard").isFile();
    }

    private static long key(int cx, int cz) { return (((long) cx) << 32) | (cz & 0xFFFFFFFFL); }

    private void load() {
        if (!store.isFile()) return;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(store)));
            int n = in.readInt();
            for (int i = 0; i < n; i++) done.add(in.readLong());
            if (in.available() >= 4) {
                int m = in.readInt();
                for (int i = 0; i < m; i++) seamed.add(in.readLong());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("TERRAIN_REPAIR_LOAD_FAILED " + ex.getClass().getSimpleName());
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private void save() {
        if (!dirty) return;
        DataOutputStream out = null;
        try {
            plugin.getDataFolder().mkdirs();
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(store)));
            out.writeInt(done.size());
            for (Long k : done) out.writeLong(k.longValue());
            out.writeInt(seamed.size());
            for (Long k : seamed) out.writeLong(k.longValue());
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("WATER_REPAIR_SAVE_FAILED " + ex.getClass().getSimpleName());
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }
}
