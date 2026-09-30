package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 1, the Yellow Rooms: mono-yellow wallpaper, damp yellow carpet and a low ceiling of fluorescent panels, in an
 * irregular lattice of rooms (walls on jittered lines about nine blocks apart, each wall segment present or not, most
 * with a doorway). Now and then the walls give way to a pillared hall. The lights fail more often the deeper one goes,
 * until whole patches are dark. The level's spine is always open, so the arena can always be reached. The arena is
 * the Dark Room: taller, pillared, almost unlit.
 */
final class YellowRooms implements Style {
    static final int PITCH = 9, AIR_H = 4, F = Level.FLOOR, W = Level.WALK, CEIL = F + AIR_H + 1;

    @Override public int floorId() { return SANDSTONE; }
    @Override public int floorMeta() { return 2; }
    @Override public int wallId() { return SANDSTONE; }
    @Override public int wallMeta() { return 2; }
    @Override public int ceilId() { return WOOL; }
    @Override public int ceilMeta() { return WHITE; }
    @Override public int lightId() { return GLOWSTONE; }

    // ---- the lattice ------------------------------------------------------------------------------------------------
    static int lineX(long seed, int k) { return k * PITCH + Hash.range(seed, 101, k, 0, 5) - 2; }
    static int lineZ(long seed, int m) { return m * PITCH + Hash.range(seed, 102, m, 0, 5) - 2; }

    /** Index k of the lattice line at or just west of x. */
    static int cellX(long seed, int x) {
        int k = Math.floorDiv(x, PITCH) + 1;
        while (lineX(seed, k) > x) k--;
        return k;
    }

    static int cellZ(long seed, int z) {
        int m = Math.floorDiv(z, PITCH) + 1;
        while (lineZ(seed, m) > z) m--;
        return m;
    }

    /** A pillared hall: a 45-block region with no walls, only pillars. */
    static boolean hall(long seed, int x, int z) { return Hash.unit(seed, 103, Math.floorDiv(x, 45), Math.floorDiv(z, 45)) < 0.12; }

    private static boolean presentV(long seed, int k, int m) { return Hash.unit(seed, 104, k, m) < 0.58; }
    private static boolean presentH(long seed, int k, int m) { return Hash.unit(seed, 107, k, m) < 0.58; }

    /** A doorway two blocks wide somewhere along most wall segments. */
    private static boolean doorV(long seed, int k, int m, int z) {
        int z0 = lineZ(seed, m), len = lineZ(seed, m + 1) - z0 - 1;
        if (len < 3 || Hash.unit(seed, 105, k, m) >= 0.74) return false;
        int door = z0 + 1 + Hash.range(seed, 106, k, m, len - 1);
        return z == door || z == door + 1;
    }

    private static boolean doorH(long seed, int k, int m, int x) {
        int x0 = lineX(seed, k), len = lineX(seed, k + 1) - x0 - 1;
        if (len < 3 || Hash.unit(seed, 108, k, m) >= 0.74) return false;
        int door = x0 + 1 + Hash.range(seed, 109, k, m, len - 1);
        return x == door || x == door + 1;
    }

    /** Whether (x, z) is wall at walking height in the level's main space. */
    static boolean wall(Level lv, long seed, int x, int z) {
        if (lv.onSpine(seed, x, z, 1.1)) return false;
        int k = cellX(seed, x), m = cellZ(seed, z);
        boolean onX = lineX(seed, k) == x, onZ = lineZ(seed, m) == z;
        if (!onX && !onZ) return false;
        if (hall(seed, x, z)) return onX && onZ && Hash.unit(seed, 110, k, m) < 0.7;   // pillars only
        if (onX && onZ) return presentV(seed, k, m) || presentV(seed, k, m - 1) || presentH(seed, k, m) || presentH(seed, k - 1, m);
        if (onX) return presentV(seed, k, m) && !doorV(seed, k, m, z);
        return presentH(seed, k, m) && !doorH(seed, k, m, x);
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) { return wall(lv, seed, x, z); }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        c.set(x, F, z, SANDSTONE, 2);
        c.set(x, CEIL + 1, z, SANDSTONE, 2);
        if (wall(lv, seed, x, z)) {
            c.set(x, W, z, SANDSTONE, 0);   // baseboard
            for (int y = W + 1; y < CEIL; y++) {
                boolean stain = stain(seed, x, y, z);
                c.set(x, y, z, stain ? CLAY : SANDSTONE, stain ? YELLOW : 2);
            }
            c.set(x, CEIL, z, WOOL, WHITE);
            return;
        }
        if (underLight(seed, x, z, d)) c.set(x, F, z, GLOWSTONE, 0);
        c.set(x, W, z, CARPET, carpet(seed, x, z, d));
        c.column(x, z, W + 1, CEIL - 1, AIR, 0);
        c.set(x, CEIL, z, lamp(seed, x, z, d) ? GLOWSTONE : WOOL, 0);
        // A chest just inside a room's north-west corner, now and then (more of them deeper in).
        int k = cellX(seed, x), m = cellZ(seed, z);
        if (x == lineX(seed, k) + 1 && z == lineZ(seed, m) + 1 && !hall(seed, x, z) && !lv.onSpine(seed, x, z, 2.5)
            && Hash.unit(seed, 111, k, m) < 0.05 + 0.07 * d) c.chest(x, W, z, 3, lv, lv.danger(x, z), "room");
    }

    /** Old water stains on the wallpaper. */
    private static boolean stain(long seed, int x, int y, int z) {
        return y <= W + 2 && Hash.unit(seed, 112, x >> 1, z >> 1) < 0.06 && Hash.unit(seed, 113, x, y, z) < 0.7;
    }

    /** Damp carpet: yellow, with brown and orange stains that come in patches. */
    private static int carpet(long seed, int x, int z, double d) {
        double patch = Hash.unit(seed, 114, x >> 2, z >> 2), spot = Hash.unit(seed, 115, x, 0, z);
        if (patch < 0.05 + 0.08 * d && spot < 0.6) return BROWN;
        if (patch > 0.93 && spot < 0.5) return ORANGE;
        return YELLOW;
    }

    /** Fluorescent panels (two blocks along x) every six blocks; deeper in more fail, and whole patches go dark. */
    static boolean lamp(long seed, int x, int z, double d) {
        if (Math.floorMod(z, 6) != 3 || Math.floorMod(x, 6) > 1) return false;
        if (d > 0.3 && Hash.unit(seed, 116, x >> 4, z >> 4) < 0.35 * d) return false;   // a dark patch
        return Hash.unit(seed, 117, Math.floorDiv(x, 6), z) >= 0.05 + 0.45 * d;
    }

    /**
     * Light hidden under the carpet on a three-block grid (the panels alone leave the floor dim): as bright as the
     * Threshold near the start; deeper in more of it fails, and none of it is in the panels' dark patches.
     */
    static boolean underLight(long seed, int x, int z, double d) {
        if (Math.floorMod(x, 3) != 1 || Math.floorMod(z, 3) != 1) return false;
        if (d > 0.3 && Hash.unit(seed, 116, x >> 4, z >> 4) < 0.35 * d) return false;
        return Hash.unit(seed, 119, x, 6, z) >= 0.08 + 0.62 * d;
    }

    // ---- the Dark Room ----------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1;
        c.set(x, F, z, SANDSTONE, 2);
        c.set(x, ceil, z, WOOL, WHITE);
        c.column(x, z, ceil + 1, ceil + 2, SANDSTONE, 2);
        int ax = x - lv.arenaStart();
        int az = Math.abs(z);   // the middle lane (|z| <= 5) stays clear for the doors and the boss
        boolean pillar = Math.floorMod(ax, 12) >= 5 && Math.floorMod(ax, 12) <= 6 && (az == 7 || az == 8 || az == 15 || az == 16);
        if (pillar) { c.column(x, z, W, ceil - 1, SANDSTONE, 1); return; }
        c.set(x, W, z, CARPET, Hash.unit(c.seed, 118, x >> 1, z >> 1) < 0.35 ? BROWN : YELLOW);
        c.column(x, z, W + 1, ceil - 1, AIR, 0);
        // Almost no light: one panel over the middle of the room.
        if (Math.abs(x - lv.arenaCentreX()) <= 1 && z == 0) c.set(x, ceil, z, GLOWSTONE);
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 6, W, 0}; }
}
