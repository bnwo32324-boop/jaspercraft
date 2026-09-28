package chat.jaspr.atlas;

/** Deterministic, allocation-free hashing and value noise: every structure in Atlas is a pure function of the seed. */
final class Hash {
    private Hash() {}

    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    static long of(long seed, long a, long b) { return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L ^ mix(b + 0x632BE59BD9B4E019L))); }

    static long of(long seed, long a, long b, long c) { return mix(of(seed, a, b) ^ mix(c * 0xD6E8FEB86659FD93L)); }

    /** Uniform in [0, 1). */
    static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }

    static double unit(long seed, long a, long b, long c) { return unit(of(seed, a, b, c)); }

    static int range(long h, int lo, int hi) { return lo + (int) ((h >>> 1) % (hi - lo + 1)); }

    /** Smooth 2D value noise in [0, 1) with the given cell size. */
    static double noise(long seed, double x, double z, double cell) {
        double fx = x / cell, fz = z / cell;
        long ix = (long) Math.floor(fx), iz = (long) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = unit(of(seed, ix, iz)), b = unit(of(seed, ix + 1, iz)), c = unit(of(seed, ix, iz + 1)), d = unit(of(seed, ix + 1, iz + 1));
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }
}
