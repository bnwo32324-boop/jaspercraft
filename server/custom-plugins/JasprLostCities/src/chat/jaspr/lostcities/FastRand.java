package chat.jaspr.lostcities;

/**
 * The mod's fastrand()/fastrand128() LCG. The mod kept one static seed for the whole game, so palette picks
 * depended on chunk generation order; here it is reseeded per chunk so a chunk always generates the same way.
 */
final class FastRand {
    private int seed = 123456789;

    void reseed(long s) { seed = (int) (s ^ (s >>> 32)); }

    int next() {
        seed = 214013 * seed + 2531011;
        return (seed >> 16) & 0x7FFF;
    }

    int next128() {
        seed = 214013 * seed + 2531011;
        return (seed >> 16) & 0x7F;
    }
}
