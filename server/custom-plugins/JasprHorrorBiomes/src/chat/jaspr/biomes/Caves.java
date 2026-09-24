package chat.jaspr.biomes;

/**
 * The underground, as five regions with genuinely different geometry.
 *
 * Every style is a scalar field sampled on a coarse lattice and interpolated,
 * rather than a per-block test. That is what makes the caves wide: a per-block
 * threshold on high-frequency noise can only ever produce speckle and ribbons,
 * while a smooth field crossing zero produces surfaces, and surfaces enclose
 * volume. Raising the threshold widens every passage at once.
 *
 * Regions are picked on a warped 768-block lattice, so a single cave system is
 * tens of chunks across and you walk a long way before the character changes.
 */
public final class Caves {
    public static final int WARRENS = 0, CATHEDRAL = 1, EMBERVEINS = 2, FROSTBORE = 3, FUNGAL = 4;
    public static final String[] NAMES = {"Hollow Warrens", "Sunken Cathedral", "Emberveins", "Frostbore", "Fungal Deeps"};

    /** Region size in blocks. 768 is 48 chunks: large in scale, as asked. */
    public static final int REGION = 768;
    /** Lattice spacing. Four horizontally and four vertically keeps 475 samples a chunk. */
    public static final int STEP_XZ = 4, STEP_Y = 4;
    /** Vertical span the field is evaluated over. */
    public static final int FLOOR = 5, CEILING = 76;

    private final Terrain terrain;

    public Caves(Terrain terrain) { this.terrain = terrain; }

    /** Which cave region owns this column. Warped so the borders are not straight. */
    public int region(int x, int z) {
        double wx = x + terrain.noise(x / 340.0, z / 340.0, 61) * 190;
        double wz = z + terrain.noise(x / 340.0, z / 340.0, 62) * 190;
        int cx = (int) Math.floor(wx / REGION), cz = (int) Math.floor(wz / REGION);
        return (int) Math.floorMod(Terrain.mix(terrain.seed + 7717L * cx + 3313L * cz + 991L), 5L);
    }

    /**
     * Carve strength at a point: positive is open air. Each style is shaped so that
     * zero sits at the cave wall, which lets the same interpolation serve them all.
     */
    public double density(int style, double x, double y, double z) {
        switch (style) {
            case CATHEDRAL: {
                // One very low frequency field. Big wavelength, gentle gradient, so the
                // zero surface encloses caverns tens of blocks across rather than tunnels.
                double vault = terrain.noise3(x / 96.0, y / 42.0, z / 96.0, 71);
                double detail = terrain.noise3(x / 31.0, y / 25.0, z / 31.0, 72) * 0.20;
                double open = (vault + detail - 0.262) * 3.0;
                // Free-standing columns: ridges of a 2D field left uncarved floor to ceiling.
                double pillar = 1.0 - Math.abs(terrain.noise(x / 11.0, z / 11.0, 73));
                if (pillar > 0.91) open -= (pillar - 0.91) * 26.0;
                return open;
            }
            case EMBERVEINS: {
                // Ridge lines of a purely 2D field, extruded down the whole column: the
                // result is a slot canyon, which is the one shape the other four cannot make.
                double line = 1.0 - Math.abs(terrain.noise(x / 60.0, z / 60.0, 74));
                double width = 0.9295 + 0.020 * Math.sin(y * 0.09);
                double fissure = (line - width) * 11.0;
                // A sparse tube network so the fissures are not sealed off from each other.
                double link = 1.0 - Math.abs(terrain.noise3(x / 42.0, y / 13.0, z / 42.0, 75));
                return Math.max(fissure, (link - 0.9395) * 3.4);
            }
            case FROSTBORE: {
                // The same ridged network, but with y compressed hard. Squashing the
                // vertical axis turns tubes into broad flat galleries.
                double gallery = 1.0 - Math.abs(terrain.noise3(x / 54.0, y / 5.6, z / 54.0, 76));
                double open = (gallery - 0.9165) * 4.6;
                double swell = terrain.noise3(x / 80.0, y / 32.0, z / 80.0, 77);
                return open + Math.max(0, swell - 0.62) * 1.8;
            }
            case FUNGAL: {
                // Rounded pockets threaded onto a thin tube network: bulbs on a string.
                double pocket = (terrain.noise3(x / 40.0, y / 28.0, z / 40.0, 78) - 0.415) * 3.2;
                double link = (1.0 - Math.abs(terrain.noise3(x / 29.0, y / 24.0, z / 29.0, 79)) - 0.9365) * 3.4;
                return Math.max(pocket, link);
            }
            default: {
                // Warrens: a wide ridged tunnel network, with a second low frequency
                // field blowing occasional stretches out into rooms.
                double tube = 1.0 - Math.abs(terrain.noise3(x / 36.0, y / 20.0, z / 36.0, 80));
                double open = (tube - 0.9145) * 4.4;
                double room = terrain.noise3(x / 72.0, y / 36.0, z / 72.0, 81);
                return open + Math.max(0, room - 0.50) * 2.4;
            }
        }
    }

    /**
     * One lattice cell of margin on every side. A chunk is generated alone, and the
     * clean-up passes that follow the carve need to know what stands just past its
     * edge: a block on the border is only floating if the block beyond it is air
     * too, and the block beyond it belongs to a chunk that may not exist yet. Since
     * the lattice is on a global four-block grid, sampling one cell further out
     * gives exactly the values the neighbour will compute for itself, so the answer
     * this chunk arrives at and the answer its neighbour arrives at are the same.
     */
    public static final int PAD = 1;
    public static final int LAT_XZ = 16 / STEP_XZ + 1 + 2 * PAD;
    public static final int LAT_Y = (CEILING - FLOOR) / STEP_Y + 1;
    /** Lowest and highest block offset the padded lattice can be read at. */
    public static final int APRON_MIN = -PAD * STEP_XZ, APRON_MAX = 16 + PAD * STEP_XZ - 1;

    /**
     * Fills one chunk's lattice, margin included. Every lattice point lies on the
     * global four-block grid, so two chunks agree exactly wherever they overlap and
     * no seam can appear.
     */
    public void sampleChunk(int cx, int cz, double[][][] field, int[][] style) {
        for (int i = 0; i < LAT_XZ; i++) {
            for (int k = 0; k < LAT_XZ; k++) {
                int wx = cx * 16 + (i - PAD) * STEP_XZ, wz = cz * 16 + (k - PAD) * STEP_XZ;
                int s = region(wx, wz);
                style[i][k] = s;
                for (int j = 0; j < LAT_Y; j++) field[i][k][j] = density(s, wx, FLOOR + j * STEP_Y, wz);
            }
        }
    }

    /** Trilinear read of the lattice at a block position inside the chunk. */
    public static double at(double[][][] field, int x, int y, int z) {
        double fx = (x + PAD * STEP_XZ) / (double) STEP_XZ, fz = (z + PAD * STEP_XZ) / (double) STEP_XZ,
            fy = (y - FLOOR) / (double) STEP_Y;
        int i = (int) fx, k = (int) fz, j = (int) fy;
        if (i < 0) i = 0;
        if (k < 0) k = 0;
        if (i >= LAT_XZ - 1) i = LAT_XZ - 2;
        if (k >= LAT_XZ - 1) k = LAT_XZ - 2;
        if (j < 0) j = 0;
        if (j >= LAT_Y - 1) j = LAT_Y - 2;
        double u = fx - i, v = fy - j, w = fz - k;
        double c00 = field[i][k][j] + u * (field[i + 1][k][j] - field[i][k][j]);
        double c10 = field[i][k][j + 1] + u * (field[i + 1][k][j + 1] - field[i][k][j + 1]);
        double c01 = field[i][k + 1][j] + u * (field[i + 1][k + 1][j] - field[i][k + 1][j]);
        double c11 = field[i][k + 1][j + 1] + u * (field[i + 1][k + 1][j + 1] - field[i][k + 1][j + 1]);
        double y0 = c00 + v * (c10 - c00), y1 = c01 + v * (c11 - c01);
        return y0 + w * (y1 - y0);
    }

    /** The style governing a block, read off the same lattice as the field. */
    public static int styleAt(int[][] style, int x, int z) {
        int i = Math.max(0, Math.min(LAT_XZ - 1, (x + PAD * STEP_XZ + STEP_XZ / 2) / STEP_XZ));
        int k = Math.max(0, Math.min(LAT_XZ - 1, (z + PAD * STEP_XZ + STEP_XZ / 2) / STEP_XZ));
        return style[i][k];
    }

    /**
     * How willing the surface is to open here, 0 to 1. The old damping sealed the
     * top fourteen blocks unconditionally, which is exactly why no cave ever showed
     * itself from above: not a shortage of caves, a lid over all of them. This
     * lifts the lid in bands rather than everywhere, so mouths are found rather
     * than tripped over.
     */
    public double breach(int x, int z) {
        double broad = terrain.noise(x / 205.0, z / 205.0, 66);
        double fine = terrain.noise(x / 58.0, z / 58.0, 67) * 0.40;
        double v = (broad + fine - 0.26) * 2.6;
        return v <= 0 ? 0 : (v >= 1 ? 1 : v);
    }

    /** Lava fills the bottom of a region up to here. Emberveins run hot. */
    public static int lavaLevel(int style) { return style == EMBERVEINS ? 16 : 11; }

    /**
     * How much the field is damped near the surface. Full strength deep down,
     * fading out over the last stretch so cave mouths are occasional rather than
     * the roof falling in across the whole map.
     */
    public static double surfaceDamping(int y, int ground, double breach) {
        int headroom = ground - y;
        if (headroom >= 14) return 1.0;
        if (headroom < 0) return 0.0;
        double natural = headroom <= 2 ? 0.0 : Math.pow((headroom - 2) / 12.0, 2);
        if (breach <= 0) return natural;
        // Inside a breach band the cave is allowed all the way up to daylight.
        double open = breach * (0.62 + 0.38 * Math.min(1.0, headroom / 12.0));
        return Math.max(natural, open);
    }
}
