package chat.jaspr.apocalypse;

/** Owner 2026-10-05: a sentry needs no room beside it; only a body really inside the block refuses the mount. */
public final class SentryPlacementTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }

    public static void main(String[] args) {
        // Standing in the block, or with any part of the body in it, refuses the mount.
        check(SentryPlacement.overlaps(10.5, 64, 10.5, 10, 64, 10), "inside the block");
        check(SentryPlacement.overlaps(10.5, 63.5, 10.5, 10, 64, 10), "head inside the block from below (feet half a block down)");
        check(SentryPlacement.overlaps(10.5, 64.5, 10.5, 10, 64, 10), "feet inside the upper half of the block");
        check(SentryPlacement.overlaps(10.05, 64, 10.5, 10, 64, 10), "body edge across the block's side");
        check(SentryPlacement.overlaps(9.8, 64, 10.5, 10, 64, 10), "body edge just across the boundary (0.3 wide)");
        // Next to it: standing on the neighbouring block, against the wall, in the corner, never refuses.
        check(!SentryPlacement.overlaps(9.5, 64, 10.5, 10, 64, 10), "centred on the block beside it");
        check(!SentryPlacement.overlaps(11.5, 64, 10.5, 10, 64, 10), "centred on the block on the other side");
        check(!SentryPlacement.overlaps(10.5, 64, 9.5, 10, 64, 10), "centred on the block behind it");
        check(!SentryPlacement.overlaps(9.69, 64, 10.5, 10, 64, 10), "pressed against its wall (body edge touches, does not enter)");
        check(!SentryPlacement.overlaps(9.5, 64, 9.5, 10, 64, 10), "diagonal neighbour");
        check(!SentryPlacement.overlaps(10.5, 62, 10.5, 10, 64, 10), "head below the block (feet 2 blocks down)");
        check(!SentryPlacement.overlaps(10.5, 65, 10.5, 10, 64, 10), "standing on top of the block");
        check(!SentryPlacement.overlaps(10.5, 65.5, 10.5, 10, 64, 10), "above the block");
        // Exhaustive: only the one block's column of positions refuses, nothing a whole block over.
        for (double x = 5; x <= 15; x += .05) for (double z = 5; z <= 15; z += .05) {
            boolean inside = SentryPlacement.overlaps(x, 64, z, 10, 64, 10);
            double dx = Math.max(10 - x, x - 11), dz = Math.max(10 - z, z - 11);
            check(inside == (dx < SentryPlacement.HALF_WIDTH && dz < SentryPlacement.HALF_WIDTH), "overlap only within the body's reach of the block at " + x + "," + z);
            if (Math.max(Math.abs(x - 10.5), Math.abs(z - 10.5)) >= 1.0 + 1e-9) check(!inside, "a whole block over is never refused");
        }
        System.out.println("SentryPlacementTest PASS checks=" + checks);
    }
}
