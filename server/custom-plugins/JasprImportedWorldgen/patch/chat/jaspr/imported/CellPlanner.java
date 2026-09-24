/*
 * JasprImportedWorldgen 1.3.0 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152 decompilation of
 * the 1.2.0 CellPlanner, cleaned up, with one change: a third placement grid (lattice 2, "grid 3": CELL3_CHUNKS,
 * SALT3, TERTIARY_CODEX, TERTIARY_WEIGHTS, chooseTertiary; big designs search their whole cell as on grid 2).
 * Grids 1 and 2 choose exactly as before. Apply the same change to the PC source.
 * Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class CellPlanner {
    public static final int CELL_CHUNKS = 48;
    public static final int EDGE_MARGIN_CHUNKS = 2;
    public static final double INTENT_CHANCE = 0.85;
    private static final long SALT = 6504695737208287281L;
    public static final int CELL2_CHUNKS = 41;
    private static final long SALT2 = 6504695737208287282L;
    static final double SECONDARY_CODEX = 0.7367;
    static final String SECONDARY_WEIGHTS = "codex:cbd_001=0.287,codex:cbd_002=0.268,codex:cbd_003=0.879,codex:cbd_004=0.225,codex:cbd_005=0.236,codex:cbd_007=0.375,codex:cbd_008=0.348,codex:cbd_009=0.554,codex:cbd_010=0.304,codex:cbd_011=0.861,codex:cbd_012=0.198,codex:cbd_013=0.232,codex:cbd_014=1.059,codex:cbd_015=0.542,codex:cbd_016=0.76,codex:cbd_017=0.639,codex:cbd_018=2.341,codex:cbd_019=0.672,codex:cbd_020=0.648,codex:cbd_021=0.336,codex:cbd_022=0.206,codex:cbd_023=0.24,codex:cbd_024=2.227,codex:cbd_025=0.384,codex:cbd_026=0.302,codex:cbd_027=0.189,codex:cbd_028=0.379,codex:cbd_029=0.304,codex:cbd_030=0.05,codex:cbd_031=2.7,codex:cbd_032=0.237,codex:cbd_033=0.183,codex:cbd_034=0.483,codex:cbd_035=0.635,codex:cbd_036=0.418,codex:cbd_037=0.393,codex:cbd_038=1.437,codex:cbd_039=0.64,codex:cbd_040=0.417,codex:cbd_041=2.78,codex:cbd_042=0.986,codex:cbd_043=0.271,codex:cbd_044=0.327,codex:cbd_045=1.967,codex:cbd_046=0.579,codex:cbd_047=1.638,codex:cbd_048=3.141,codex:cbd_049=2.51,codex:cbd_050=0.32,codex:cbd_051=0.774,codex:cbd_052=2.529,codex:cbd_053=1.521,codex:cbd_054=0.05,codex:cbd_055=1.519,codex:cbd_056=1.927,codex:cbd_057=2.421,codex:cbd_058=0.527,codex:cbd_059=0.31,codex:cbd_060=0.98,codex:cbd_061=1.312,codex:cbd_062=1.962,codex:cbd_063=0.601,codex:cbd_064=0.934,codex:cbd_065=0.05,codex:cbd_066=1.641,codex:cbd_067=1.491,codex:cbd_068=0.491,codex:cbd_069=3.041,codex:cbd_070=1.053,codex:cbd_071=0.519,codex:cbd_072=0.345,codex:cbd_073=1.521,codex:cbd_074=0.242,codex:cbd_075=1.643,codex:cbd_076=1.267,codex:cbd_077=2.415,codex:cbd_078=1.651,codex:cbd_079=0.78,codex:cbd_080=1.338,codex:cbd_081=0.388,codex:cbd_082=2.629,codex:cbd_083=0.74,codex:cbd_084=0.84,codex:cbd_085=0.218,codex:cbd_086=2.597,codex:cbd_087=1.959,codex:cbd_088=0.651,codex:cbd_089=1.273,codex:cbd_090=1.445,codex:cbd_091=0.949,codex:cbd_093=0.737,codex:cbd_094=0.415,codex:cbd_095=0.264,codex:cbd_096=0.509,codex:cbd_097=0.695,codex:cbd_100=0.609,codex:cbd_101=0.058,glm:B01=0.941,glm:B04=0.734,glm:B05=0.389,glm:B06=1.187,glm:B07=2.429,glm:B10=1.393,glm:B13=2.477,glm:B14=0.848,glm:B15=6.11,glm:B16=0.836,glm:B19=0.925,glm:B20=1.209,glm:B21=0.869,glm:B24=0.853,glm:B25=0.978,glm:B26=0.887,glm:B27=0.888,glm:B30=0.853,glm:B31=0.702,glm:B32=0.65,glm:B33=4.492,glm:B35=0.457,glm:B36=2.251,glm:B37=0.893,glm:B38=0.827,glm:B39=0.961,glm:B40=2.397,glm:B49=1.361,glm:B50=0.714,glm:B51=1.303,glm:B53=2.469,glm:B55=0.759,glm:B56=1.164,glm:B57=1.139,glm:B61=1.507,glm:B62=0.957,glm:B67=1.703,glm:B73=0.664,glm:B74=0.745,glm:B75=0.77,glm:B76=1.32,glm:B77=0.853,glm:B78=0.668";
    private static final Map<String, Double> SECONDARY = new HashMap<String, Double>();
    public static final int SECONDARY_ATTEMPTS = 32;
    /*
     * 1.3.0 (owner: "all structures universally should spawn 1.5x whatever their current spawn rate is"): grid 3,
     * a third lattice of CELL3_CHUNKS-chunk cells with a salt of its own, planned like grid 2 (same attempts, big
     * designs search the whole cell) on ground new in 1.3.0 only (jaspr-imported-v3.boundary), yielding to every
     * grid-1 and grid-2 plan and to every HorrorBiomes site of every tier. TERTIARY_CODEX and TERTIARY_WEIGHTS are
     * calibrated so that on fresh ground grid 3 adds half of what grids 1+2 build, per collection and per design
     * as far as the probe resolves it.
     */
    public static final int CELL3_CHUNKS = 40;
    private static final long SALT3 = 6504695737208287283L;
    static final double TERTIARY_CODEX = 0.7367;
    static final String TERTIARY_WEIGHTS = "codex:cbd_001=0.287,codex:cbd_002=0.268,codex:cbd_003=0.879,codex:cbd_004=0.225,codex:cbd_005=0.236,codex:cbd_007=0.375,codex:cbd_008=0.348,codex:cbd_009=0.554,codex:cbd_010=0.304,codex:cbd_011=0.861,codex:cbd_012=0.198,codex:cbd_013=0.232,codex:cbd_014=1.059,codex:cbd_015=0.542,codex:cbd_016=0.76,codex:cbd_017=0.639,codex:cbd_018=2.341,codex:cbd_019=0.672,codex:cbd_020=0.648,codex:cbd_021=0.336,codex:cbd_022=0.206,codex:cbd_023=0.24,codex:cbd_024=2.227,codex:cbd_025=0.384,codex:cbd_026=0.302,codex:cbd_027=0.189,codex:cbd_028=0.379,codex:cbd_029=0.304,codex:cbd_030=0.05,codex:cbd_031=2.7,codex:cbd_032=0.237,codex:cbd_033=0.183,codex:cbd_034=0.483,codex:cbd_035=0.635,codex:cbd_036=0.418,codex:cbd_037=0.393,codex:cbd_038=1.437,codex:cbd_039=0.64,codex:cbd_040=0.417,codex:cbd_041=2.78,codex:cbd_042=0.986,codex:cbd_043=0.271,codex:cbd_044=0.327,codex:cbd_045=1.967,codex:cbd_046=0.579,codex:cbd_047=1.638,codex:cbd_048=3.141,codex:cbd_049=2.51,codex:cbd_050=0.32,codex:cbd_051=0.774,codex:cbd_052=2.529,codex:cbd_053=1.521,codex:cbd_054=0.05,codex:cbd_055=1.519,codex:cbd_056=1.927,codex:cbd_057=2.421,codex:cbd_058=0.527,codex:cbd_059=0.31,codex:cbd_060=0.98,codex:cbd_061=1.312,codex:cbd_062=1.962,codex:cbd_063=0.601,codex:cbd_064=0.934,codex:cbd_065=0.05,codex:cbd_066=1.641,codex:cbd_067=1.491,codex:cbd_068=0.491,codex:cbd_069=3.041,codex:cbd_070=1.053,codex:cbd_071=0.519,codex:cbd_072=0.345,codex:cbd_073=1.521,codex:cbd_074=0.242,codex:cbd_075=1.643,codex:cbd_076=1.267,codex:cbd_077=2.415,codex:cbd_078=1.651,codex:cbd_079=0.78,codex:cbd_080=1.338,codex:cbd_081=0.388,codex:cbd_082=2.629,codex:cbd_083=0.74,codex:cbd_084=0.84,codex:cbd_085=0.218,codex:cbd_086=2.597,codex:cbd_087=1.959,codex:cbd_088=0.651,codex:cbd_089=1.273,codex:cbd_090=1.445,codex:cbd_091=0.949,codex:cbd_093=0.737,codex:cbd_094=0.415,codex:cbd_095=0.264,codex:cbd_096=0.509,codex:cbd_097=0.695,codex:cbd_100=0.609,codex:cbd_101=0.058,glm:B01=0.941,glm:B04=0.734,glm:B05=0.389,glm:B06=1.187,glm:B07=2.429,glm:B10=1.393,glm:B13=2.477,glm:B14=0.848,glm:B15=6.11,glm:B16=0.836,glm:B19=0.925,glm:B20=1.209,glm:B21=0.869,glm:B24=0.853,glm:B25=0.978,glm:B26=0.887,glm:B27=0.888,glm:B30=0.853,glm:B31=0.702,glm:B32=0.65,glm:B33=4.492,glm:B35=0.457,glm:B36=2.251,glm:B37=0.893,glm:B38=0.827,glm:B39=0.961,glm:B40=2.397,glm:B49=1.361,glm:B50=0.714,glm:B51=1.303,glm:B53=2.469,glm:B55=0.759,glm:B56=1.164,glm:B57=1.139,glm:B61=1.507,glm:B62=0.957,glm:B67=1.703,glm:B73=0.664,glm:B74=0.745,glm:B75=0.77,glm:B76=1.32,glm:B77=0.853,glm:B78=0.668";
    private static final Map<String, Double> TERTIARY = new HashMap<String, Double>();
    private final List<SiteSpec> glm;
    private final List<SiteSpec> codex;

    public CellPlanner(SiteCatalog catalog) {
        this.glm = catalog.glm();
        this.codex = catalog.codex();
    }

    /** Cell size in chunks of lattice 0 (grid 1), 1 (grid 2) or 2 (grid 3). */
    public static int cellChunks(int lattice) {
        return lattice == 0 ? CELL_CHUNKS : lattice == 1 ? CELL2_CHUNKS : CELL3_CHUNKS;
    }

    public Plan choose(long seed, int cellX, int cellZ, Ground ground) {
        return this.choose(seed, cellX, cellZ, ground, CELL_CHUNKS, SALT, 0, 8);
    }

    public Plan chooseSecondary(long seed, int cellX, int cellZ, Ground ground) {
        return this.choose(seed, cellX, cellZ, ground, CELL2_CHUNKS, SALT2, 1, SECONDARY_ATTEMPTS);
    }

    public Plan chooseTertiary(long seed, int cellX, int cellZ, Ground ground) {
        return this.choose(seed, cellX, cellZ, ground, CELL3_CHUNKS, SALT3, 2, SECONDARY_ATTEMPTS);
    }

    /** The plan of lattice 0, 1 or 2. */
    public Plan choose(long seed, int cellX, int cellZ, Ground ground, int lattice) {
        return lattice == 0 ? this.choose(seed, cellX, cellZ, ground) : lattice == 1 ? this.chooseSecondary(seed, cellX, cellZ, ground) : this.chooseTertiary(seed, cellX, cellZ, ground);
    }

    private Plan choose(long seed, int cellX, int cellZ, Ground ground, int cell, long salt, int lattice, int attempts) {
        long mixed = CellPlanner.mix(seed ^ salt ^ (long)cellX * 341873128712L ^ (long)cellZ * 132897987541L);
        Random random = new Random(mixed);
        if (random.nextDouble() >= 0.85) {
            return null;
        }
        SiteSpec site = lattice == 0 ? CellPlanner.weighted(random.nextBoolean() ? this.glm : this.codex, random)
            : lattice == 1 ? CellPlanner.weighted(random.nextDouble() < SECONDARY_CODEX ? this.codex : this.glm, random, SECONDARY)
            : CellPlanner.weighted(random.nextDouble() < TERTIARY_CODEX ? this.codex : this.glm, random, TERTIARY);
        int spanX = cell - 4 - site.widthChunks();
        int spanZ = cell - 4 - site.depthChunks();
        if (spanX < 0 || spanZ < 0) {
            throw new IllegalStateException("Catalog entry exceeds cell: " + site.id);
        }
        for (int i = 0; i < attempts; ++i) {
            int chunkX = cellX * cell + 2 + random.nextInt(spanX + 1);
            int x = chunkX * 16;
            int chunkZ = cellZ * cell + 2 + random.nextInt(spanZ + 1);
            int z = chunkZ * 16;
            int y = this.originY(site, x, z, ground);
            if (y < 1 || y + site.dimensions[1] > 256 || !ground.permits(x, y, z, site)) continue;
            return new Plan(site, cellX, cellZ, x, y, z, lattice);
        }
        if (lattice == 0 || !Occupancy.big(site)) {
            return null;
        }
        int[] order = new int[(spanX + 1) * (spanZ + 1)];
        for (int i = 0; i < order.length; ++i) {
            order[i] = i;
        }
        for (int i = order.length - 1; i > 0; --i) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        for (int index : order) {
            int x = (cellX * cell + 2 + index / (spanZ + 1)) * 16;
            int z = (cellZ * cell + 2 + index % (spanZ + 1)) * 16;
            int y = this.originY(site, x, z, ground);
            if (y < 1 || y + site.dimensions[1] > 256 || !ground.permits(x, y, z, site)) continue;
            return new Plan(site, cellX, cellZ, x, y, z, lattice);
        }
        return null;
    }

    private int originY(SiteSpec site, int x, int z, Ground ground) {
        int sizeX = site.dimensions[0];
        int sizeZ = site.dimensions[2];
        int[] samples = new int[]{ground.surface(x, z), ground.surface(x + sizeX - 1, z), ground.surface(x, z + sizeZ - 1), ground.surface(x + sizeX - 1, z + sizeZ - 1), ground.surface(x + sizeX / 2, z + sizeZ / 2)};
        int lo = 256;
        int hi = 0;
        int wet = 0;
        for (int sample : samples) {
            lo = Math.min(lo, sample);
            hi = Math.max(hi, sample);
            if (sample >= 62) continue;
            ++wet;
        }
        String habitat = site.habitat;
        if (habitat.equals("ocean_surface") || habitat.equals("afloat") || habitat.equals("half_submerged") || habitat.equals("submerged")) {
            if (site.waterline == null || wet < 4 || hi > 59) {
                return -1;
            }
            if ("deep_ocean_basin".equals(site.terrainAdaptation)) {
                return 1;
            }
            return 63 - site.waterline;
        }
        if (habitat.equals("shore")) {
            if (site.waterline == null || wet == 0 || wet == samples.length) {
                return -1;
            }
            return 63 - site.waterline;
        }
        if (habitat.equals("underground")) {
            int y = 61 - site.dimensions[1];
            return y >= 1 && lo > y + site.dimensions[1] + 4 ? y : -1;
        }
        if (habitat.equals("sky")) {
            return Math.max(hi + 20, 120);
        }
        if (site.surfaceAnchor == null || wet != 0) {
            return -1;
        }
        if (habitat.equals("land") && hi - lo > (site.codex() ? 5 : 8)) {
            return -1;
        }
        if (habitat.equals("embedded") && hi - lo > 8) {
            return -1;
        }
        int centre = samples[4];
        int y = centre + 1 - site.surfaceAnchor - (habitat.equals("embedded") ? 3 : 0);
        int top;
        if ("tall_castle_embed".equals(site.terrainAdaptation) && y > (top = 256 - site.dimensions[1])) {
            return y - top <= 12 ? top : -1;
        }
        return y;
    }

    private static SiteSpec weighted(List<SiteSpec> list, Random random, Map<String, Double> factors) {
        double total = 0.0;
        for (SiteSpec site : list) {
            total += site.weight * factors.getOrDefault(site.id, 1.0);
        }
        double pick = random.nextDouble() * total;
        for (SiteSpec site : list) {
            if ((pick -= site.weight * factors.getOrDefault(site.id, 1.0)) < 0.0) {
                return site;
            }
        }
        return list.get(list.size() - 1);
    }

    private static SiteSpec weighted(List<SiteSpec> list, Random random) {
        double total = 0.0;
        for (SiteSpec site : list) {
            total += site.weight;
        }
        double pick = random.nextDouble() * total;
        for (SiteSpec site : list) {
            if ((pick -= site.weight) < 0.0) {
                return site;
            }
        }
        return list.get(list.size() - 1);
    }

    private static long mix(long l) {
        l = (l ^ l >>> 30) * -4658895280553007687L;
        l = (l ^ l >>> 27) * -7723592293110705685L;
        return l ^ l >>> 31;
    }

    static {
        for (String entry : SECONDARY_WEIGHTS.split(",")) {
            int eq = entry.indexOf(61);
            SECONDARY.put(entry.substring(0, eq), Double.valueOf(entry.substring(eq + 1)));
        }
        for (String entry : TERTIARY_WEIGHTS.split(",")) {
            int eq = entry.indexOf(61);
            TERTIARY.put(entry.substring(0, eq), Double.valueOf(entry.substring(eq + 1)));
        }
    }

    public static interface Ground {
        public int surface(int x, int z);

        public boolean permits(int x, int y, int z, SiteSpec site);
    }

    public static final class Plan {
        public final SiteSpec site;
        public final int cellX;
        public final int cellZ;
        public final int x;
        public final int y;
        public final int z;
        public final int lattice;

        Plan(SiteSpec site, int cellX, int cellZ, int x, int y, int z) {
            this(site, cellX, cellZ, x, y, z, 0);
        }

        Plan(SiteSpec site, int cellX, int cellZ, int x, int y, int z, int lattice) {
            this.site = site;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lattice = lattice;
        }

        public boolean intersects(int chunkX, int chunkZ) {
            int bx = chunkX * 16;
            int bz = chunkZ * 16;
            return (long)bx < (long)this.x + (long)this.site.dimensions[0] && (long)bx + 16L > (long)this.x && (long)bz < (long)this.z + (long)this.site.dimensions[2] && (long)bz + 16L > (long)this.z;
        }
    }
}
