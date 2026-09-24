package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;

/**
 * Waterfalls, already fallen.
 *
 * The obvious way to get a waterfall is to drop a spring into a cave wall and let
 * the game's own fluid rules run. It works, and it looks wrong: a chunk is only
 * ticked once someone is near it, so the water starts moving at the moment you
 * arrive. You walk into a ravine and watch it fill, which is a thing that never
 * happens in Minecraft and reads instantly as machinery.
 *
 * So the flow is worked out here instead, at generation, and what gets written is
 * where the water ends up rather than where it starts: a source in the wall, a
 * column of falling water, and the splash it makes at the bottom. It is the same
 * arithmetic the game would have done -- fall if there is nothing underneath,
 * otherwise spread one level weaker in the directions that lead to a drop -- run
 * to completion before anyone can see it. Nothing is left to tick, so the chunk
 * arrives finished.
 *
 * The spread is held to five blocks and springs are kept to the middle of the
 * chunk, because a chunk is generated alone and water that would have run past
 * its edge has nowhere to go: better a slightly smaller splash than one that
 * stops dead along a chunk line.
 */
public final class CaveSprings {
    private CaveSprings() {}

    private static final int FLOWING = 8, STILL = 9, STONE = 1, FALLING = 8;
    /** Furthest a splash spreads. Vanilla allows seven; five keeps it off the seams. */
    private static final int REACH = 5;
    private static final int[][] SIDE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /** Where the water is written, so the same solver serves generation and repair. */
    private interface Sink {
        int id(int x, int y, int z);
        void set(int x, int y, int z, int id, int data);
        int floor();
        int ceiling();
    }

    private static final class Data implements Sink {
        private final ChunkGenerator.ChunkData d;
        Data(ChunkGenerator.ChunkData d) { this.d = d; }
        public int id(int x, int y, int z) { return d.getTypeId(x, y, z); }
        public void set(int x, int y, int z, int id, int data) { d.setBlock(x, y, z, id, (byte) data); }
        public int floor() { return 2; }
        public int ceiling() { return Math.min(d.getMaxHeight() - 1, 200); }
    }

    private static final class Live implements Sink {
        private final Chunk c;
        Live(Chunk c) { this.c = c; }
        public int id(int x, int y, int z) { return c.getBlock(x, y, z).getTypeId(); }
        public void set(int x, int y, int z, int id, int data) {
            // No physics: the state being written is already the finished one, and a
            // notified neighbour would only start the flow this exists to avoid.
            c.getBlock(x, y, z).setTypeIdAndData(id, (byte) data, false);
        }
        public int floor() { return 2; }
        public int ceiling() { return 200; }
    }

    // -- generation -------------------------------------------------------------

    /**
     * Vanilla's own spring test -- an air pocket roofed in stone with exactly one
     * open face -- finds nothing here, because it is a test for the thin seams
     * between vanilla's narrow tunnels and these caves are halls. The equivalent
     * shape in a hall is the wall itself: rock with rock above, below and on three
     * sides, open air on the fourth, and a drop behind it.
     */
    public static void pour(ChunkGenerator.ChunkData d, Terrain t, int cx, int cz) {
        Random r = new Random(Terrain.mix(t.seed + cx * 341873128712L + cz * 132897987541L + 7717L));
        Sink sink = new Data(d);
        boolean[] seen = new boolean[16 * 208 * 16];
        int placed = 0;
        for (int i = 0; i < 140 && placed < 3; i++) {
            // Kept to the middle four columns so a five-block splash cannot reach an edge.
            int x = 6 + r.nextInt(4), z = 6 + r.nextInt(4), y = 16 + r.nextInt(74);
            if (spring(sink, seen, x, y, z)) placed++;
        }
    }

    private static boolean spring(Sink s, boolean[] seen, int x, int y, int z) {
        if (s.id(x, y, z) != STONE) return false;
        if (s.id(x, y + 1, z) != STONE) return false;
        if (s.id(x, y - 1, z) != STONE) return false;
        int open = -1;
        for (int i = 0; i < 4; i++) {
            int id = s.id(x + SIDE[i][0], y, z + SIDE[i][1]);
            if (id == STONE) continue;
            if (id != 0 || open >= 0) return false;
            open = i;
        }
        if (open < 0) return false;
        // The open face has to give onto a drop. A spring that only wets the wall
        // beside it is a damp patch; four blocks of nothing underneath is a fall.
        int ax = x + SIDE[open][0], az = z + SIDE[open][1];
        for (int k = 1; k <= 4; k++) if (s.id(ax, y - k, az) != 0) return false;
        s.set(x, y, z, STILL, 0);
        solve(s, seen, x, y, z, 0, 600);
        return true;
    }

    // -- the solver -------------------------------------------------------------

    private static final int FALL = -1;

    /**
     * Runs one body of water to rest. Falls where there is nothing underneath and
     * spreads where there is, one level weaker each step, preferring the directions
     * that lead to a drop -- which is why water finds the lip of a ledge instead of
     * puddling evenly around it.
     */
    private static int solve(Sink s, boolean[] seen, int sx, int sy, int sz, int level, int budget) {
        int[] queue = new int[Math.min(4096, budget * 2 + 64)];
        int[] lvl = new int[queue.length];
        int head = 0, tail = 0, written = 0;
        queue[tail] = pack(sx, sy, sz); lvl[tail++] = level;
        int top = s.ceiling(), bottom = s.floor();
        while (head < tail) {
            int p = queue[head]; int k = lvl[head++];
            int y = (p >> 8) & 0xFF, x = (p >> 4) & 15, z = p & 15;
            if (y - 1 >= bottom && s.id(x, y - 1, z) == 0) {
                s.set(x, y - 1, z, FLOWING, FALLING);
                written++;
                if (written >= budget || tail >= queue.length - 4) break;
                queue[tail] = pack(x, y - 1, z); lvl[tail++] = FALL;
                continue;                                  // falling water does not spread
            }
            int spread = k == FALL ? 1 : k + 1;
            if (spread > REACH) continue;
            int best = Integer.MAX_VALUE;
            int[] dist = new int[4];
            for (int i = 0; i < 4; i++) {
                int nx = x + SIDE[i][0], nz = z + SIDE[i][1];
                dist[i] = Integer.MAX_VALUE;
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
                if (s.id(nx, y, nz) != 0) continue;
                dist[i] = slope(s, nx, y, nz, bottom);
                if (dist[i] < best) best = dist[i];
            }
            if (best == Integer.MAX_VALUE) continue;
            for (int i = 0; i < 4; i++) {
                if (dist[i] != best) continue;
                int nx = x + SIDE[i][0], nz = z + SIDE[i][1];
                int q = pack(nx, y, nz);
                if (seen[q]) continue;
                seen[q] = true;
                s.set(nx, y, nz, FLOWING, spread);
                written++;
                if (written >= budget || tail >= queue.length - 4) return written;
                queue[tail] = q; lvl[tail++] = spread;
            }
            if (y > top) continue;
        }
        return written;
    }

    /** How many steps to the nearest place this water could fall, up to three. */
    private static int slope(Sink s, int x, int y, int z, int bottom) {
        if (y - 1 >= bottom && s.id(x, y - 1, z) == 0) return 0;
        for (int step = 1; step <= 3; step++) {
            for (int dx = -step; dx <= step; dx++) {
                int dz = step - Math.abs(dx);
                for (int sign = -1; sign <= 1; sign += 2) {
                    int nx = x + dx, nz = z + dz * sign;
                    if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
                    if (s.id(nx, y, nz) != 0) continue;
                    if (y - 1 >= bottom && s.id(nx, y - 1, nz) == 0) return step;
                    if (dz == 0) break;
                }
            }
        }
        return 9;
    }

    private static int pack(int x, int y, int z) { return ((y & 0xFF) << 8) | (x << 4) | z; }

    // -- repair of terrain that already exists ----------------------------------

    /**
     * Clears what the old scheme left behind in terrain that already exists.
     *
     * This one only takes away. A block of water written through the world -- as
     * opposed to into a chunk being generated -- is announced to the block it lands
     * in, which schedules its first fluid tick, which is the whole thing we are
     * trying not to do: the water would start moving the moment the chunk came into
     * view. So chunks made by the old scheme lose their unfinished waterfalls and the
     * springs that fed them, rather than gaining finished ones. New terrain arrives
     * with its water already at rest, and that is where the waterfalls now live.
     */
    public static int settleExisting(Chunk c, ChunkSnapshot snap) {
        Sink s = new Live(c);
        int cleared = 0;
        // Water caught mid-flow. All of it is a leftover; none of it is load-bearing.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            for (int y = 8; y <= 120; y++)
                if (snap.getBlockTypeId(x, y, z) == FLOWING) { s.set(x, y, z, 0, 0); cleared++; }

        // Liquid standing on air was never going to hold. Sweeping upward cascades.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            for (int y = 8; y <= 120; y++) {
                int id = s.id(x, y, z);
                if (id != STILL && id != 11) continue;
                if (s.id(x, y - 1, z) != 0) continue;
                s.set(x, y, z, 0, 0); cleared++;
            }

        // And the springs themselves: a lone source in a wall above a drop, which
        // would pour again the first time anything near it was disturbed.
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++)
            for (int y = 10; y <= 110; y++) {
                if (s.id(x, y, z) != STILL) continue;
                if (!lip(s, x, y, z)) continue;
                if (company(s, x, y, z) > 2) continue;      // part of a body, not a spring
                s.set(x, y, z, 0, 0); cleared++;
            }
        return cleared;
    }

    /** Is this block of water standing at the edge of a drop? */
    private static boolean lip(Sink s, int x, int y, int z) {
        for (int i = 0; i < 4; i++) {
            int nx = x + SIDE[i][0], nz = z + SIDE[i][1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            if (s.id(nx, y, nz) == 0 && s.id(nx, y - 1, nz) == 0) return true;
        }
        return false;
    }

    /** How much water it is standing with. A pool has company; a spring does not. */
    private static int company(Sink s, int x, int y, int z) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int nx = x + SIDE[i][0], nz = z + SIDE[i][1];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            if (liquid(s.id(nx, y, nz))) n++;
        }
        if (liquid(s.id(x, y + 1, z))) n++;
        return n;
    }

    // -- chunk seams ------------------------------------------------------------

    /** Drop liquid standing along a chunk line whose support is in the next chunk. */
    public static void seams(World w, Chunk c) {
        int bx = c.getX() << 4, bz = c.getZ() << 4;
        for (int i = 0; i < 16; i++) {
            edge(w, c, 0, i, -1, 0, bx, bz);
            edge(w, c, 15, i, 1, 0, bx, bz);
            edge(w, c, i, 0, 0, -1, bx, bz);
            edge(w, c, i, 15, 0, 1, bx, bz);
        }
    }

    private static void edge(World w, Chunk c, int x, int z, int dx, int dz, int bx, int bz) {
        int ox = bx + x + dx, oz = bz + z + dz;
        if (!w.isChunkLoaded(ox >> 4, oz >> 4)) return;
        if (!populated(w, ox >> 4, oz >> 4)) return;   // judged later, from the neighbour's own pass
        for (int y = 14; y <= 70; y++) {
            int mine = c.getBlock(x, y, z).getTypeId();
            int theirs = w.getBlockAt(ox, y, oz).getTypeId();
            if (liquid(mine) && theirs == 0 && w.getBlockAt(ox, y - 1, oz).getTypeId() == 0) {
                for (int k = y; k <= 96; k++) {
                    Block b = c.getBlock(x, k, z);
                    if (!liquid(b.getTypeId())) break;
                    b.setTypeIdAndData(0, (byte) 0, false);
                }
                return;
            }
            if (liquid(theirs) && mine == 0 && c.getBlock(x, y - 1, z).getTypeId() == 0) {
                for (int k = y; k <= 96; k++) {
                    Block b = w.getBlockAt(ox, k, oz);
                    if (!liquid(b.getTypeId())) break;
                    b.setTypeIdAndData(0, (byte) 0, false);
                }
                return;
            }
        }
    }

    /**
     * Has the neighbouring chunk been populated yet? Set pieces are built by the populator one
     * chunk at a time, so an unpopulated neighbour still shows bare terrain where the rest of a
     * moat, pond or lava trench is about to go, and judging the seam against it drained liquid
     * that was never going to leak (structure audit 2026-09-22: still water and lava turned to air
     * along chunk lines in the temples, Ostrovets, Old Town, Site-19, The Pit, AM ...). When the
     * neighbour populates, its own pass judges the same seam, from its side, with both halves
     * built -- the two tests in edge() are mirror images, so nothing goes unchecked.
     */
    private static boolean populated(World w, int cx, int cz) {
        try {
            return ((org.bukkit.craftbukkit.v1_12_R1.CraftChunk) w.getChunkAt(cx, cz)).getHandle().isDone();
        } catch (RuntimeException | LinkageError e) {
            return true;                                // cannot tell: judge the seam now, as before
        }
    }

    private static boolean liquid(int id) { return id == 8 || id == 9 || id == 10 || id == 11; }
}
