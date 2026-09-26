package chat.jaspr.lostcities;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The "Lost Cities biome": deterministic city regions from the world seed alone. The world is cut into cells of
 * 64x64 chunks; a cell holds one city with probability 0.55. The city is a blob of radius 12-24 chunks at a random
 * spot inside the cell (kept clear of the cell edge), with a wobbly outline from four low-frequency harmonics.
 * Pure and thread-safe.
 */
final class Regions {
    private Regions() {}

    static final int GRID = 64;
    static final double CHANCE = 0.55;
    static final int MIN_RADIUS = 12, MAX_RADIUS = 24;
    static final double WOBBLE = 0.2;
    static final int EDGE_MARGIN = 2;

    static final class Region {
        final int gx, gz;
        final int centerX, centerZ;      // chunk coordinates
        final int radius;                // chunks
        final double[] amp = new double[4], phase = new double[4];
        final String name;
        final int minX, maxX, minZ, maxZ; // chunk bounding box of the blob

        Region(long seed, int gx, int gz, long h) {
            this.gx = gx; this.gz = gz;
            radius = MIN_RADIUS + (int) Math.floor(u(mix(h + 2)) * (MAX_RADIUS - MIN_RADIUS + 1));
            int m = (int) Math.ceil(radius * (1 + WOBBLE)) + EDGE_MARGIN;
            int span = Math.max(0, GRID - 2 * m);
            centerX = gx * GRID + m + (int) Math.floor(u(mix(h + 3)) * (span + 1));
            centerZ = gz * GRID + m + (int) Math.floor(u(mix(h + 4)) * (span + 1));
            double total = 0;
            for (int k = 0; k < 4; k++) { amp[k] = 0.25 + u(mix(h + 10 + k)); total += amp[k]; }
            for (int k = 0; k < 4; k++) { amp[k] /= total; phase[k] = u(mix(h + 20 + k)) * Math.PI * 2; }
            int ext = (int) Math.ceil(radius * (1 + WOBBLE)) + 1;
            minX = centerX - ext; maxX = centerX + ext; minZ = centerZ - ext; maxZ = centerZ + ext;
            name = Names.city(mix(h + 99));
        }

        double edge(double theta) {
            double w = 0;
            for (int k = 0; k < 4; k++) w += amp[k] * Math.sin((k + 2) * theta + phase[k]);
            return radius * (1 + WOBBLE * w);
        }

        boolean contains(int cx, int cz) {
            if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) return false;
            double dx = cx - centerX, dz = cz - centerZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= radius * (1 - WOBBLE)) return true;
            if (d > radius * (1 + WOBBLE)) return false;
            return d <= edge(Math.atan2(dz, dx));
        }

        String key() { return gx + "," + gz; }
    }

    private static final Region NONE = null;
    private static final ConcurrentHashMap<Long, Object> CACHE = new ConcurrentHashMap<>();
    private static final Object EMPTY = new Object();

    static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    static double u(long h) { return (h >>> 11) * 0x1.0p-53; }

    /** The region of cell (gx, gz), or null if the cell has no city. */
    static Region cell(long seed, int gx, int gz) {
        long key = mix(seed ^ 0x4C4F535443495459L) ^ (((long) gx) << 32 ^ (gz & 0xffffffffL));
        Object o = CACHE.get(key);
        if (o == null) {
            long h = mix(seed * 0x9E3779B97F4A7C15L + gx * 341873128712L + gz * 132897987541L + 0x4C4F5354L);
            o = u(mix(h + 1)) < CHANCE ? new Region(seed, gx, gz, h) : EMPTY;
            if (CACHE.size() > 20000) CACHE.clear();
            CACHE.put(key, o);
        }
        return o == EMPTY ? NONE : (Region) o;
    }

    static Region regionAt(long seed, int cx, int cz) {
        Region r = cell(seed, Math.floorDiv(cx, GRID), Math.floorDiv(cz, GRID));
        return r != null && r.contains(cx, cz) ? r : null;
    }

    static boolean inRegion(long seed, int cx, int cz) { return regionAt(seed, cx, cz) != null; }

    /** Nearest region (by centre) to a chunk, searching the surrounding cells. */
    static Region nearest(long seed, int cx, int cz, int cellRadius) {
        int gx = Math.floorDiv(cx, GRID), gz = Math.floorDiv(cz, GRID);
        Region best = null;
        long bd = Long.MAX_VALUE;
        for (int a = gx - cellRadius; a <= gx + cellRadius; a++)
            for (int b = gz - cellRadius; b <= gz + cellRadius; b++) {
                Region r = cell(seed, a, b);
                if (r == null) continue;
                long dx = r.centerX - cx, dz = r.centerZ - cz, d = dx * dx + dz * dz;
                if (d < bd) { bd = d; best = r; }
            }
        return best;
    }

    /** City names: "The Lost City of <name>". */
    static final class Names {
        private static final String[] A = {"Ash", "Grey", "Hollow", "Rust", "Cinder", "Mourn", "Dun", "Wither", "Black",
            "Silt", "Pale", "Iron", "Low", "Cold", "Salt", "Dusk", "Vesper", "Marrow", "Gall", "Hush", "Soot", "Bleak",
            "Raven", "Ember", "Lorn", "Thorn", "Fallow", "Stark", "Wan", "Gloam", "Sorrow", "Carrion"};
        private static final String[] Z = {"haven", "moor", "reach", "gate", "ford", "wick", "holm", "mere", "ton", "crest",
            "fall", "vale", "bury", "stead", "port", "field", "wall", "spire", "deep", "hollow", "mouth", "cross", "march",
            "brook", "well", "worth", "ridge", "barrow"};

        static String city(long h) {
            String a = A[(int) Math.floorMod(h, (long) A.length)];
            String z = Z[(int) Math.floorMod(mix(h + 7), (long) Z.length)];
            return a + z;
        }
    }
}
