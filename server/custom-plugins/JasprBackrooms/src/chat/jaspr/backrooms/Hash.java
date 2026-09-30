package chat.jaspr.backrooms;

/** Deterministic, order-free hashing: every generated detail is a pure function of the seed and world coordinates. */
final class Hash {
    private Hash() {}

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static long of(long seed, long a, long b, long c) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L ^ mix(b * 0xC2B2AE3D27D4EB4FL ^ mix(c * 0x165667B19E3779F9L))));
    }

    /** Uniform in [0, 1). */
    static double unit(long seed, long a, long b, long c) { return (of(seed, a, b, c) >>> 11) * 0x1.0p-53; }

    /** Uniform in [0, n). */
    static int range(long seed, long a, long b, long c, int n) { return (int) Math.floorMod(of(seed, a, b, c), (long) n); }

    /** Four-coordinate forms: the first coordinate is usually a salt naming what is being decided. */
    static long of(long seed, long a, long b, long c, long d) { return of(seed ^ mix(a * 0xD6E8FEB86659FD93L + 0x632BE59BD9B4E019L), b, c, d); }
    static double unit(long seed, long a, long b, long c, long d) { return (of(seed, a, b, c, d) >>> 11) * 0x1.0p-53; }
    static int range(long seed, long a, long b, long c, long d, int n) { return (int) Math.floorMod(of(seed, a, b, c, d), (long) n); }
}
