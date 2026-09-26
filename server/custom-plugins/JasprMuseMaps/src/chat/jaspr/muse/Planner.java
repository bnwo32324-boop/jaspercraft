package chat.jaspr.muse;

import java.util.List;
import java.util.Random;

/**
 * Pure, repeatable Muse+GLM_Maps placement intent on its own 32-chunk grid (never loads chunks).
 *
 * Owner brief: "just as likely to spawn as any other structure ... an average spawn rate". One cell in
 * INTENT_CHANCE picks one of the 139 designs with equal weight, then tries positions; big designs that find no
 * spot in the usual attempts search the whole cell (as the imported grid does), so every design lands about as
 * often as any other instead of the small ones crowding out the castles. Measured on the test server; see MAPS.md.
 */
public final class Planner {
    public static final int CELL_CHUNKS = 32, EDGE_MARGIN_CHUNKS = 2, ATTEMPTS = 24;
    public static final double INTENT_CHANCE = 0.8;
    public static final long BIG_AREA = 12000;
    private static final long SALT = 0x4D5553452B474C4DL; // "MUSE+GLM"

    public interface Ground {
        int surface(int worldX, int worldZ);
        boolean permits(int x, int y, int z, Catalog.Design design);
    }

    public static final class Plan {
        public final Catalog.Design design;
        public final int cellX, cellZ, x, y, z;
        Plan(Catalog.Design design, int cellX, int cellZ, int x, int y, int z) {
            this.design = design; this.cellX = cellX; this.cellZ = cellZ; this.x = x; this.y = y; this.z = z;
        }
        public boolean intersects(int chunkX, int chunkZ) {
            int wx = chunkX * 16, wz = chunkZ * 16;
            return wx < (long) x + design.width() && wx + 16L > x && wz < (long) z + design.depth() && wz + 16L > z;
        }
        public boolean contains(int bx, int by, int bz, int margin) {
            return bx >= x - margin && bx < x + design.width() + margin && bz >= z - margin && bz < z + design.depth() + margin
                && by >= y - margin && by < y + design.height() + margin;
        }
        public String key() { return cellX + "_" + cellZ; }
    }

    private final List<Catalog.Design> designs;
    public Planner(Catalog catalog) { designs = catalog.all(); }

    public static boolean big(Catalog.Design d) { return d.area() >= BIG_AREA; }

    public Plan choose(long worldSeed, int cellX, int cellZ, Ground ground) {
        Random random = new Random(mix(worldSeed ^ SALT ^ ((long) cellX * 341873128712L) ^ ((long) cellZ * 132897987541L)));
        if (random.nextDouble() >= INTENT_CHANCE) return null;
        return place(random, pick(random), cellX, cellZ, ground);
    }

    /** Fixture calibration: where would design d go in this cell (same attempts as a real pick), or null. */
    public Plan tryDesign(long worldSeed, int cellX, int cellZ, Catalog.Design d, Ground ground) {
        Random random = new Random(mix(worldSeed ^ SALT ^ 0x43414C4942L ^ ((long) cellX * 341873128712L) ^ ((long) cellZ * 132897987541L)));
        return place(random, d, cellX, cellZ, ground);
    }

    private Plan place(Random random, Catalog.Design d, int cellX, int cellZ, Ground ground) {
        int maxX = CELL_CHUNKS - 2 * EDGE_MARGIN_CHUNKS - d.widthChunks();
        int maxZ = CELL_CHUNKS - 2 * EDGE_MARGIN_CHUNKS - d.depthChunks();
        if (maxX < 0 || maxZ < 0) throw new IllegalStateException("Design exceeds cell: " + d.id);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = (cellX * CELL_CHUNKS + EDGE_MARGIN_CHUNKS + random.nextInt(maxX + 1)) * 16;
            int z = (cellZ * CELL_CHUNKS + EDGE_MARGIN_CHUNKS + random.nextInt(maxZ + 1)) * 16;
            Plan p = attempt(d, cellX, cellZ, x, z, ground);
            if (p != null) return p;
        }
        if (!big(d)) return null;
        int[] order = new int[(maxX + 1) * (maxZ + 1)];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = order.length - 1; i > 0; i--) { int j = random.nextInt(i + 1), k = order[i]; order[i] = order[j]; order[j] = k; }
        for (int i : order) {
            int x = (cellX * CELL_CHUNKS + EDGE_MARGIN_CHUNKS + i / (maxZ + 1)) * 16;
            int z = (cellZ * CELL_CHUNKS + EDGE_MARGIN_CHUNKS + i % (maxZ + 1)) * 16;
            Plan p = attempt(d, cellX, cellZ, x, z, ground);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Designs that fit fewer places are picked more often, so each of the 139 lands about equally often in the
     * world: a design's catalogue weight is 1 / its measured admission rate (scripts/muse-glm calibration from the
     * test-server fixture "calibrate", normalised and clamped). Chance of being chosen = weight / sum of weights.
     */
    static double weight(Catalog.Design d) { return d.weight <= 0 ? 1 : d.weight; }
    private Catalog.Design pick(Random random) {
        double sum = 0;
        for (Catalog.Design d : designs) sum += weight(d);
        double r = random.nextDouble() * sum;
        for (Catalog.Design d : designs) if ((r -= weight(d)) < 0) return d;
        return designs.get(designs.size() - 1);
    }

    private Plan attempt(Catalog.Design d, int cellX, int cellZ, int x, int z, Ground ground) {
        int y = originY(d, x, z, ground);
        if (y < 1 || y + d.height() > 256) return null;
        return ground.permits(x, y, z, d) ? new Plan(d, cellX, cellZ, x, y, z) : null;
    }

    /** Terrain fit by habitat, from nine surface samples (corners, edge midpoints, centre). */
    static int originY(Catalog.Design d, int x, int z, Ground ground) {
        int sx = d.width(), sz = d.depth();
        int[][] at = {{0, 0}, {sx - 1, 0}, {0, sz - 1}, {sx - 1, sz - 1}, {sx / 2, 0}, {sx / 2, sz - 1}, {0, sz / 2}, {sx - 1, sz / 2}, {sx / 2, sz / 2}};
        int lo = 256, hi = 0, wet = 0, mid = 0;
        for (int i = 0; i < at.length; i++) {
            int h = ground.surface(x + at[i][0], z + at[i][1]);
            lo = Math.min(lo, h); hi = Math.max(hi, h);
            if (h < 62) wet++;
            if (i == at.length - 1) mid = h;
        }
        int n = at.length, height = d.height();
        switch (d.habitat) {
            case "ocean_surface":
                if (d.waterline == null || wet < n || hi > 59) return -1;
                return 63 - d.waterline;
            case "shore":
                if (d.waterline == null || wet == 0 || wet == n || hi > 70) return -1;
                return 63 - d.waterline;
            case "underground_lid":
                if (wet != 0 || hi - lo > 5) return -1;
                return lo - (height - 1);
            case "sky":
                if (wet > n / 2) return -1;
                return Math.max(hi + 24, 110);
            case "bedrock":
                if (wet > 1 || hi - lo > 24) return -1;
                return 1;
            case "tall": {
                if (wet != 0 || hi - lo > 10) return -1;
                int origin = mid + 1 - d.surfaceAnchor, roof = 256 - height;
                return origin <= roof ? origin : origin - roof <= 16 ? roof : -1;
            }
            default: // land
                // Ground fit flattens and founds the footprint, so the tolerance grows with size (a 200-block
                // design needs no flatter land, relative to its span, than a 20-block cabin).
                if (wet != 0 || hi - lo > (big(d) ? Math.min(20, 10 + (int) (Math.sqrt(d.area()) / 20)) : 8)) return -1;
                return mid + 1 - d.surfaceAnchor;
        }
    }

    static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
}
