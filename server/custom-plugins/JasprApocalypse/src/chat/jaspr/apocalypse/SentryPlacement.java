package chat.jaspr.apocalypse;

/**
 * Where a sentry may stand (owner 2026-10-05: "placed down anywhere as long as it doesn't clip through the ceiling, so about
 * two blocks ... get rid of the logic that says they need space horizontally"). The only things that refuse a mount are a
 * solid block in the way, a ceiling within two blocks above it, and a player whose body is really inside the block: standing
 * beside it, on the next block over, or against the same wall never refuses it.
 */
final class SentryPlacement {
    private SentryPlacement() {}

    /** Half the width and the height of a standing player's body. */
    static final double HALF_WIDTH = 0.3, HEIGHT = 1.8;

    /** Whether a player whose feet are at (px, py, pz) occupies any of the unit block at (bx, by, bz). */
    static boolean overlaps(double px, double py, double pz, int bx, int by, int bz) {
        return px + HALF_WIDTH > bx && px - HALF_WIDTH < bx + 1
            && pz + HALF_WIDTH > bz && pz - HALF_WIDTH < bz + 1
            && py + HEIGHT > by && py < by + 1;
    }
}
