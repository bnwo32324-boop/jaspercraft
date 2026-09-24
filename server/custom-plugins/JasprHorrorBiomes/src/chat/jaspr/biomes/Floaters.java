package chat.jaspr.biomes;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;

/**
 * Anything the ground is not actually holding up.
 *
 * A carve decides block by block whether rock stays, and a noise field asked that
 * question about a million blocks will always answer yes somewhere it shouldn't:
 * a lone cube in the middle of the air, a shelf of turf whose hill was taken out
 * from under it, a tree standing on a hole. None of it was placed deliberately and
 * all of it reads as broken, so rather than trying to make the carve never make a
 * mistake, the chunk is asked one question at the end -- what is still connected to
 * the ground? -- and everything else goes.
 *
 * Connection is ordinary six-way adjacency through solid rock down to bedrock.
 * Arches, overhangs, ceilings, land bridges and cliffs are all connected and all
 * survive; only genuinely orphaned material does not. Blocks running off the side
 * of the chunk count as held, because the mass they belong to continues into
 * terrain this chunk cannot see and must not be judged on what it cannot see.
 */
public final class Floaters {
    private Floaters() {}

    /** How far up the chunk is examined. Nothing this generator builds reaches it. */
    private static final int SPAN = 200;

    /**
     * What stands just past the chunk's edge. Asked only for positions with x or z
     * outside 0..15. The generator answers from the padded lattice, which gives the
     * exact block the neighbouring chunk will build; the repair answers from the
     * neighbouring chunk itself when it is loaded, and says "rock" when it is not,
     * because the cost of a wrong "air" is a hole torn along a chunk seam and the
     * cost of a wrong "rock" is one floater left for the next pass.
     */
    public interface Apron { boolean rock(int x, int y, int z); }

    /** Everything held, for chunks with nothing to consult. */
    public static final Apron SOLID_EDGE = new Apron() { public boolean rock(int x, int y, int z) { return true; } };

    /** A tree's wood and leaves: allowed to reach across a chunk line unsupported. */
    static boolean canopy(int id) { return id == 17 || id == 162 || id == 18 || id == 161; }

    /**
     * Blocks that hang off other blocks rather than hold them up. Air and liquid
     * obviously, but also every plant and fitting: a flower is not support, and a
     * column that ends in one has still ended.
     */
    public static boolean loose(int id) {
        switch (id) {
            case 0: case 8: case 9: case 10: case 11:            // air, water, lava
            case 6: case 30: case 31: case 32: case 37: case 38: // sapling, web, grass, bush, flowers
            case 39: case 40: case 50: case 51: case 55:         // mushrooms, torch, fire, redstone
            case 59: case 63: case 64: case 65: case 66:         // wheat, signs, door, ladder, rail
            case 68: case 69: case 70: case 71: case 72:
            case 75: case 76: case 77: case 78: case 83:         // torches, button, snow layer, reeds
            case 92: case 104: case 105: case 106: case 111:     // cake, stems, vines, lily
            case 115: case 131: case 132: case 141: case 142:
            case 143: case 147: case 148: case 149: case 150:
            case 157: case 171: case 175: case 176: case 177:
                return true;
            default:
                return false;
        }
    }

    // -- generation side --------------------------------------------------------

    /** How far up despeckling looks. Above this there is only sky. */
    private static final int REACH = 160;

    /**
     * Takes out the grit the carve threshold leaves along its own boundary.
     *
     * Where the density field runs flat near zero -- which is most of a large
     * chamber's ceiling -- the sign flips block by block, and what should have been
     * one smooth surface comes out as a field of one-block-square prisms hanging in
     * the air on a four-block grid. They read as a checkerboard because that is
     * literally what they are: the lattice showing through.
     *
     * A block with no rock beside it in any of the four horizontal directions is not
     * part of a wall, a floor or a ceiling. It is a needle, and the whole needle goes
     * in one pass, because every block in a one-wide column fails the same test. A
     * second rule clears the ends of horizontal rods and any block left with a single
     * neighbour. Walls one block thick keep two neighbours each and are untouched, and
     * so is anything at the chunk edge, where the neighbouring rock cannot be seen and
     * is assumed to be there.
     */
    public static void despeckle(ChunkGenerator.ChunkData d) { despeckle(d, SOLID_EDGE); }

    public static void despeckle(ChunkGenerator.ChunkData d, Apron apron) {
        boolean[] rock = new boolean[16 * REACH * 16];
        for (int y = 1; y < REACH; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            if (!loose(d.getTypeId(x, y, z))) rock[(y << 8) | (x << 4) | z] = true;

        for (int pass = 0; pass < 2; pass++) {
            boolean[] cut = new boolean[rock.length];
            boolean any = false;
            for (int y = 2; y < REACH - 1; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int i = (y << 8) | (x << 4) | z;
                if (!rock[i]) continue;
                int side = 0;
                if (x > 0 ? rock[i - 16] : apron.rock(-1, y, z)) side++;
                if (x < 15 ? rock[i + 16] : apron.rock(16, y, z)) side++;
                if (z > 0 ? rock[i - 1] : apron.rock(x, y, -1)) side++;
                if (z < 15 ? rock[i + 1] : apron.rock(x, y, 16)) side++;
                int all = side + (rock[i - 256] ? 1 : 0) + (rock[i + 256] ? 1 : 0);
                if (side == 0 || all <= 1) { cut[i] = true; any = true; }
            }
            if (!any) break;
            for (int i = 0; i < rock.length; i++) if (cut[i]) {
                rock[i] = false;
                d.setBlock((i >> 4) & 15, i >> 8, i & 15, 0, (byte) 0);
            }
        }
    }

    /**
     * Prunes the chunk and reports the true top of every column afterwards, which is
     * the other half of the same fault: whatever is planted next -- trees, ground
     * cover, litter -- is planted on a height the carve may since have removed.
     */
    public static void prune(ChunkGenerator.ChunkData d, int[][] heights) { prune(d, heights, SOLID_EDGE); }

    public static void prune(ChunkGenerator.ChunkData d, int[][] heights, Apron apron) {
        boolean[] rock = new boolean[16 * SPAN * 16];
        short[] ids = new short[rock.length];
        for (int y = 0; y < SPAN; y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int id = d.getTypeId(x, y, z);
                    ids[at(x, y, z)] = (short) id;
                    if (!loose(id)) rock[at(x, y, z)] = true;
                }

        boolean[] held = new boolean[rock.length];
        int[] stack = new int[rock.length];
        spread(rock, held, stack, seed(rock, ids, held, stack, apron));
        judge(rock, ids, held, stack, apron);

        for (int y = 1; y < SPAN; y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int i = at(x, y, z);
                    if (rock[i] && !held[i]) { d.setBlock(x, y, z, 0, (byte) 0); rock[i] = false; }
                }

        // A plant whose block just went is now floating in its own right.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int top = 0;
            for (int y = SPAN - 1; y > 0; y--) {
                int id = d.getTypeId(x, y, z);
                if (id == 0) continue;
                if (loose(id)) {
                    if (id != 8 && id != 9 && id != 10 && id != 11 && d.getTypeId(x, y - 1, z) == 0)
                        d.setBlock(x, y, z, 0, (byte) 0);
                    continue;
                }
                if (top == 0) top = y;
            }
            heights[x][z] = Math.max(1, top);
        }
    }

    /** A cluster this large that reaches the chunk's edge is a lid, not a floater. */
    private static final int LID = 256;

    /**
     * Second opinion on everything the first pass left unheld. Anything that never
     * reaches the chunk's edge is floating, full stop. Anything that does reach it
     * might be held by ground the chunk cannot see: a wide cap over a cave, a
     * seabed, a cliff continuing next door. So a large edge-touching cluster is
     * kept -- if it were floating it would be a landmark, and the neighbour that
     * can see its far side makes the same call -- and a small one is kept only if
     * the rock across the seam from it is grounded, which the apron knows.
     */
    private static void judge(boolean[] rock, short[] ids, boolean[] held, int[] stack, Apron apron) {
        boolean[] seen = new boolean[rock.length];
        for (int start = 0; start < rock.length; start++) {
            if (!rock[start] || held[start] || seen[start]) continue;
            int sp = 0, n = 0;
            boolean edge = false, vouched = false;
            stack[sp++] = start; seen[start] = true;
            while (sp > 0) {
                int i = stack[--sp];
                n++;
                int z = i & 15, x = (i >> 4) & 15, y = i >> 8;
                boolean border = x == 0 || x == 15 || z == 0 || z == 15;
                if (border) {
                    edge = true;
                    if (!vouched && ((ids != null && canopy(ids[i]))
                        || (x == 0 && apron.rock(-1, y, z)) || (x == 15 && apron.rock(16, y, z))
                        || (z == 0 && apron.rock(x, y, -1)) || (z == 15 && apron.rock(x, y, 16)))) vouched = true;
                }
                if (x > 0)        sp = pushSeen(rock, held, seen, stack, sp, at(x - 1, y, z));
                if (x < 15)       sp = pushSeen(rock, held, seen, stack, sp, at(x + 1, y, z));
                if (z > 0)        sp = pushSeen(rock, held, seen, stack, sp, at(x, y, z - 1));
                if (z < 15)       sp = pushSeen(rock, held, seen, stack, sp, at(x, y, z + 1));
                if (y > 0)        sp = pushSeen(rock, held, seen, stack, sp, at(x, y - 1, z));
                if (y < SPAN - 1) sp = pushSeen(rock, held, seen, stack, sp, at(x, y + 1, z));
            }
            boolean keep = edge && (n >= LID || vouched);
            if (!keep) continue;
            // Mark the whole cluster held: walk it again.
            sp = 0; stack[sp++] = start; held[start] = true;
            while (sp > 0) {
                int i = stack[--sp];
                int z = i & 15, x = (i >> 4) & 15, y = i >> 8;
                if (x > 0)        sp = push(rock, held, stack, sp, at(x - 1, y, z));
                if (x < 15)       sp = push(rock, held, stack, sp, at(x + 1, y, z));
                if (z > 0)        sp = push(rock, held, stack, sp, at(x, y, z - 1));
                if (z < 15)       sp = push(rock, held, stack, sp, at(x, y, z + 1));
                if (y > 0)        sp = push(rock, held, stack, sp, at(x, y - 1, z));
                if (y < SPAN - 1) sp = push(rock, held, stack, sp, at(x, y + 1, z));
            }
        }
    }

    private static int pushSeen(boolean[] rock, boolean[] held, boolean[] seen, int[] stack, int sp, int i) {
        if (!rock[i] || held[i] || seen[i]) return sp;
        seen[i] = true; stack[sp++] = i;
        return sp;
    }

    /** Bedrock and the rock just above it hold everything up. The edge is judged later, per cluster. */
    private static int seed(boolean[] rock, short[] ids, boolean[] held, int[] stack, Apron apron) {
        int sp = 0;
        for (int y = 0; y <= 4; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int i = at(x, y, z);
            if (!rock[i]) continue;
            held[i] = true; stack[sp++] = i;
        }
        return sp;
    }

    private static int spread(boolean[] rock, boolean[] held, int[] stack, int sp) {
        while (sp > 0) {
            int i = stack[--sp];
            int z = i & 15, x = (i >> 4) & 15, y = i >> 8;
            if (x > 0)        sp = push(rock, held, stack, sp, at(x - 1, y, z));
            if (x < 15)       sp = push(rock, held, stack, sp, at(x + 1, y, z));
            if (z > 0)        sp = push(rock, held, stack, sp, at(x, y, z - 1));
            if (z < 15)       sp = push(rock, held, stack, sp, at(x, y, z + 1));
            if (y > 0)        sp = push(rock, held, stack, sp, at(x, y - 1, z));
            if (y < SPAN - 1) sp = push(rock, held, stack, sp, at(x, y + 1, z));
        }
        return sp;
    }

    private static int push(boolean[] rock, boolean[] held, int[] stack, int sp, int i) {
        if (!rock[i] || held[i]) return sp;
        held[i] = true; stack[sp++] = i;
        return sp;
    }

    private static int at(int x, int y, int z) { return (y << 8) | (x << 4) | z; }

    /** Counts unsupported blocks in a chunk that already exists, changing nothing. */
    public static int count(ChunkSnapshot snap) { return count(snap, SOLID_EDGE); }

    public static int count(ChunkSnapshot snap, Apron apron) {
        boolean[] rock = new boolean[16 * SPAN * 16];
        short[] ids = new short[rock.length];
        for (int y = 0; y < SPAN; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int id = snap.getBlockTypeId(x, y, z);
            ids[at(x, y, z)] = (short) id;
            if (!loose(id)) rock[at(x, y, z)] = true;
        }
        boolean[] held = new boolean[rock.length];
        int[] stack = new int[rock.length];
        spread(rock, held, stack, seed(rock, ids, held, stack, apron));
        judge(rock, ids, held, stack, apron);
        int n = 0;
        for (int i = 0; i < rock.length; i++) if (rock[i] && !held[i]) n++;
        return n;
    }

    /** Counts, without changing anything. For working out which stage leaves what. */
    public static int audit(ChunkGenerator.ChunkData d) {
        boolean[] rock = new boolean[16 * SPAN * 16];
        short[] ids = new short[rock.length];
        for (int y = 0; y < SPAN; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int id = d.getTypeId(x, y, z);
            ids[at(x, y, z)] = (short) id;
            if (!loose(id)) rock[at(x, y, z)] = true;
        }
        boolean[] held = new boolean[rock.length];
        int[] stack = new int[rock.length];
        spread(rock, held, stack, seed(rock, ids, held, stack, SOLID_EDGE));
        judge(rock, ids, held, stack, SOLID_EDGE);
        int n = 0;
        for (int i = 0; i < rock.length; i++) if (rock[i] && !held[i]) n++;
        return n;
    }

    // -- repair side ------------------------------------------------------------

    /**
     * The same question asked of a chunk that already exists, which calls for more
     * care: whatever is floating out there now was put there by a generator, but
     * something floating might also have been put there by a player. So a cluster is
     * only taken down if it is small, made entirely of material the world makes on
     * its own, and touches nothing crafted. A build fails all three.
     */
    public static int prune(Chunk c, ChunkSnapshot snap, int maxCluster) { return prune(c, snap, maxCluster, SOLID_EDGE); }

    public static int prune(Chunk c, ChunkSnapshot snap, int maxCluster, Apron apron) {
        boolean[] rock = new boolean[16 * SPAN * 16];
        short[] ids = new short[rock.length];
        for (int y = 0; y < SPAN; y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int id = snap.getBlockTypeId(x, y, z);
                    ids[at(x, y, z)] = (short) id;
                    if (!loose(id)) rock[at(x, y, z)] = true;
                }

        boolean[] held = new boolean[rock.length];
        int[] stack = new int[rock.length];
        spread(rock, held, stack, seed(rock, ids, held, stack, apron));
        judge(rock, ids, held, stack, apron);

        boolean[] seen = new boolean[rock.length];
        int cap = Math.max(16, maxCluster) + 1;
        int[] cluster = new int[cap];
        int removed = 0, attempts = 0;
        for (int y = 1; y < SPAN && attempts < 512; y++)
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int i = at(x, y, z);
                if (!rock[i] || held[i] || seen[i]) continue;
                attempts++;
                int n = gather(rock, seen, snap, i, cluster);
                boolean cornered = n > 0 && corner(rock, held, cluster, n);
                if (Boolean.getBoolean("jaspr.audit")) org.bukkit.Bukkit.getLogger().info("[PRUNE] chunk " + c.getX() + "," + c.getZ()
                    + " cluster at " + (((i >> 4) & 15) + (c.getX() << 4)) + "," + (i >> 8) + "," + ((i & 15) + (c.getZ() << 4))
                    + " gathered=" + n + " cornered=" + cornered);
                if (cornered) n = 0;   // attached, if only just
                for (int k = 0; k < n; k++) {
                    int j = cluster[k];
                    c.getBlock((j >> 4) & 15, j >> 8, j & 15).setTypeIdAndData(0, (byte) 0, false);
                    removed++;
                }
            }
        return removed;
    }

    /**
     * Collects one orphaned cluster into the queue it is walking, and returns its
     * size -- or zero, meaning leave it alone, if it overflows the cap, contains
     * anything the world does not make by itself, or touches anything crafted.
     */
    private static int gather(boolean[] rock, boolean[] seen, ChunkSnapshot snap, int start, int[] q) {
        boolean keep = true;
        int head = 0, tail = 0;
        q[tail++] = start; seen[start] = true;
        while (head < tail) {
            int i = q[head++];
            int z = i & 15, x = (i >> 4) & 15, y = i >> 8;
            if (!natural(snap.getBlockTypeId(x, y, z))) keep = false;
            for (int s = 0; s < 6; s++) {
                int nx = x + (s == 0 ? -1 : s == 1 ? 1 : 0);
                int nz = z + (s == 2 ? -1 : s == 3 ? 1 : 0);
                int ny = y + (s == 4 ? -1 : s == 5 ? 1 : 0);
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 1 || ny >= SPAN) continue;
                int j = at(nx, ny, nz);
                if (!rock[j]) continue;
                if (!natural(snap.getBlockTypeId(nx, ny, nz))) keep = false;
                if (seen[j]) continue;
                if (tail >= q.length) { keep = false; continue; }
                seen[j] = true; q[tail++] = j;
            }
        }
        return keep ? tail : 0;
    }

    /**
     * Does any of this cluster touch held rock at a corner? Six-way adjacency is the
     * right test for what holds a chunk up, but it is too strict for what counts as
     * attached to the eye: a branch tip, a ledge, a spur of rock can meet the thing
     * it belongs to diagonally and still plainly belong to it. In a world that
     * already exists, anything arguable is left alone.
     */
    private static boolean corner(boolean[] rock, boolean[] held, int[] cluster, int n) {
        for (int k = 0; k < n; k++) {
            int i = cluster[k];
            int z = i & 15, x = (i >> 4) & 15, y = i >> 8;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx, ny = y + dy, nz = z + dz;
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 1 || ny >= SPAN) continue;
                int j = at(nx, ny, nz);
                if (rock[j] && held[j]) return true;
            }
        }
        return false;
    }

    /**
     * The apron for terrain that already exists: the neighbouring chunk itself,
     * when it is loaded. When it is not, everything past the edge is taken to be
     * rock, which leaves a floater on the seam for a later pass rather than tearing
     * a hole in ground that was fine.
     */
    public static Apron worldApron(final org.bukkit.World w, final int cx, final int cz) {
        return new Apron() {
            public boolean rock(int x, int y, int z) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                if (!w.isChunkLoaded(wx >> 4, wz >> 4)) return true;
                if (y < 0 || y > 255) return false;
                if (loose(w.getBlockAt(wx, y, wz).getTypeId())) return false;
                return grounded(w, wx, y, wz);
            }
        };
    }

    /**
     * Is this block of the neighbouring chunk standing on anything? Two halves of
     * one floater, one each side of a seam, will each answer "there is rock across
     * the line" about the other; the question has to be whether that rock reaches
     * the ground. Walked through the neighbouring chunk only, and bounded: a
     * cluster that runs past the bound is taken to be the terrain itself.
     */
    private static boolean grounded(org.bukkit.World w, int sx, int sy, int sz) {
        final int cap = 320;
        int ncx = sx >> 4, ncz = sz >> 4;
        java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
        java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<long[]>();
        queue.add(new long[]{sx, sy, sz}); seen.add(pack(sx, sy, sz));
        int[][] step = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty()) {
            long[] c = queue.poll();
            int x = (int) c[0], y = (int) c[1], z = (int) c[2];
            if (y <= 4) return true;
            if (seen.size() > cap) return true;
            for (int[] d : step) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (ny < 0 || ny > 255 || (nx >> 4) != ncx || (nz >> 4) != ncz) continue;
                long key = pack(nx, ny, nz);
                if (seen.contains(key)) continue;
                if (loose(w.getBlockAt(nx, ny, nz).getTypeId())) continue;
                seen.add(key); queue.add(new long[]{nx, ny, nz});
            }
        }
        return false;
    }

    private static long pack(int x, int y, int z) { return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFF) << 32) | (z & 0xFFFFFFFFL); }

    /** Material the world produces on its own, and nothing a player would build with. */
    private static boolean natural(int id) {
        switch (id) {
            case 1: case 2: case 3: case 12: case 13:            // stone, grass, dirt, sand, gravel
            case 14: case 15: case 16: case 21: case 56:         // ores
            case 73: case 74: case 129:
            case 24: case 179:                                   // sandstone
            case 48: case 87: case 88: case 110:                 // mossy cobble, netherrack, soul sand, mycelium
            case 79: case 80: case 82: case 174:                 // ice, snow, clay, packed ice
            case 17: case 162: case 18: case 161:                // logs and leaves
            case 86: case 91: case 99: case 100: case 103:       // pumpkin, lantern, mushroom blocks, melon
            case 121: case 172: case 159:                        // end stone, hardened clay
                return true;
            default:
                return false;
        }
    }
}
