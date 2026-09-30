package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 7, the Poolrooms: white tile everywhere, bright and warm and wrong. Rooms sixteen blocks square with arched
 * openings between them; most rooms are flooded ankle deep (water over the tiles), some are dry (a step up), some hold
 * a deep pool lit from below, some are pillared halls, some have stairs climbing to nowhere. Skylights glow through
 * the ceiling. Water never flows: every flooded block is walled by water or tile. The arena is the Deep End, a vast
 * hall around a pool where the Lifeguard waits.
 */
final class Pools implements Style {
    static final int R = 16, AIR_H = 8, F = Level.FLOOR, W = Level.WALK, CEIL = F + AIR_H + 1, POOL_FLOOR = F - 7;
    enum Kind { FLOODED, DRY, POOL, PILLARS, STAIRS }

    @Override public int floorId() { return CONCRETE; }
    @Override public int floorMeta() { return WHITE; }
    @Override public int wallId() { return CONCRETE; }
    @Override public int wallMeta() { return WHITE; }
    @Override public int ceilId() { return CONCRETE; }
    @Override public int ceilMeta() { return WHITE; }
    @Override public int lightId() { return SEA_LANTERN; }

    static Kind kind(long seed, int i, int j) {
        double r = Hash.unit(seed, 701, i, j);
        return r < 0.42 ? Kind.FLOODED : r < 0.6 ? Kind.DRY : r < 0.8 ? Kind.POOL : r < 0.9 ? Kind.PILLARS : Kind.STAIRS;
    }

    static boolean wet(Kind k) { return k == Kind.FLOODED || k == Kind.POOL || k == Kind.PILLARS; }

    /** Walls between rooms: present or not, most present ones with an arch three wide and five tall; the spine passes. */
    private static boolean wallV(Level lv, long seed, int i, int j, int z) {
        if (Hash.unit(seed, 702, i, j) >= 0.58) return false;
        int m = Math.floorMod(z, R);
        return !(Hash.unit(seed, 703, i, j) < 0.78 && m >= 7 && m <= 9);
    }

    private static boolean wallH(Level lv, long seed, int i, int j, int x) {
        if (Hash.unit(seed, 704, i, j) >= 0.58) return false;
        int m = Math.floorMod(x, R);
        return !(Hash.unit(seed, 705, i, j) < 0.78 && m >= 7 && m <= 9);
    }

    /** Whether the column is wall at walking height (arches are open), and whether it is on a room boundary at all. */
    static boolean wall(Level lv, long seed, int x, int z) {
        if (lv.onSpine(seed, x, z, 1.6)) return false;
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R), i = Math.floorDiv(x, R), j = Math.floorDiv(z, R);
        if (mx == 0 && mz == 0) return true;   // a pier where walls meet
        if (mx == 0) return wallV(lv, seed, i, j, z);
        if (mz == 0) return wallH(lv, seed, i, j, x);
        return false;
    }

    /** The water or tile at walking height between two rooms (in an arch or where no wall stands). */
    private static boolean wetBoundary(long seed, int x, int z) {
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R), i = Math.floorDiv(x, R), j = Math.floorDiv(z, R);
        if (mx == 0) return wet(kind(seed, i - 1, j)) && wet(kind(seed, i, j));
        return wet(kind(seed, i, j - 1)) && wet(kind(seed, i, j));
    }

    /** Inside a room's deep pool (an 8x8 basin in its middle). */
    static boolean pool(long seed, int x, int z) {
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R);
        return mx >= 4 && mx <= 11 && mz >= 4 && mz <= 11 && kind(seed, Math.floorDiv(x, R), Math.floorDiv(z, R)) == Kind.POOL;
    }

    /** A room's pillars (2x2, on a five-block grid) in pillared halls. */
    private static boolean pillar(long seed, int x, int z) {
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R);
        return kind(seed, Math.floorDiv(x, R), Math.floorDiv(z, R)) == Kind.PILLARS && (mx == 4 || mx == 5 || mx == 11 || mx == 12) && (mz == 4 || mz == 5 || mz == 11 || mz == 12);
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) { return wall(lv, seed, x, z) || pillar(seed, x, z) && !lv.onSpine(seed, x, z, 1.6); }

    @Override public byte open(Level lv, long seed, int x, int z) {
        if (x - lv.entryEnd() < 10 || lv.arenaStart() - x <= 10) return Styles.FLOOR_INK;
        if (pool(seed, x, z)) return Styles.DEEP_INK;
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R);
        boolean wetHere = mx == 0 || mz == 0 ? wetBoundary(seed, x, z) : wet(kind(seed, Math.floorDiv(x, R), Math.floorDiv(z, R)));
        return wetHere ? Styles.WATER_INK : Styles.FLOOR_INK;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        int mx = Math.floorMod(x, R), mz = Math.floorMod(z, R), i = Math.floorDiv(x, R), j = Math.floorDiv(z, R);
        tile(c, x, F, z);
        c.column(x, z, CEIL, CEIL + 1, CONCRETE, WHITE);
        boolean skylight = Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4;
        if (skylight) { c.set(x, CEIL, z, GLASS); c.set(x, CEIL + 1, z, GLOWSTONE); }
        boolean boundary = mx == 0 || mz == 0;
        if (wall(lv, seed, x, z)) {
            c.set(x, W, z, CONCRETE, LIGHT_BLUE);
            c.column(x, z, W + 1, CEIL - 1, CONCRETE, WHITE);
            if (Math.floorMod(x + z, 6) == 0) c.set(x, W + 4, z, SEA_LANTERN);
            return;
        }
        Kind kind = kind(seed, i, j);
        // Near both ends of the level everything is dry, so no water ever meets the vestibules' open doorways.
        boolean nearEnds = x - lv.entryEnd() < 10 || lv.arenaStart() - x <= 10;
        boolean wetHere = !nearEnds && (boundary ? wetBoundary(seed, x, z) : wet(kind));
        if (boundary) {   // an arch or an open boundary: water continues only between two wet rooms
            c.set(x, W, z, wetHere ? WATER : CONCRETE, wetHere ? 0 : WHITE);
            if (!(mx == 0 ? wallV(lv, seed, i, j, z) : wallH(lv, seed, i, j, x))) return;   // no wall at all here
            c.column(x, z, W + 6, CEIL - 1, QUARTZ, 0);   // the arch's crown
            return;
        }
        if (pool(seed, x, z) && !nearEnds) {
            c.set(x, POOL_FLOOR, z, Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0 ? SEA_LANTERN : CONCRETE, Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0 ? 0 : LIGHT_BLUE);
            c.column(x, z, POOL_FLOOR + 1, W, WATER, 0);
            if (mx == 4 && mz == 4 && Hash.unit(seed, 706, i, j) < 0.35 + 0.3 * d) c.chest(x, POOL_FLOOR + 1, z, 3, lv, lv.danger(x, z), "pool");
            return;
        }
        if (pillar(seed, x, z) && !lv.onSpine(seed, x, z, 1.6)) { c.column(x, z, W, CEIL - 1, QUARTZ, 2); return; }
        if (!wetHere) {
            tile(c, x, W, z);   // dry rooms are a step up
            if (poolLight(seed, x, z, d)) c.set(x, W, z, SEA_LANTERN);
            if (kind == Kind.STAIRS && mz >= 2 && mz <= 3 && mx >= 3 && mx <= 12) {
                int step = mx - 3, y = W + 1 + step / 2;
                if (y < CEIL - 1) { c.column(x, z, W + 1, y - 1, QUARTZ, 0); c.set(x, y, z, QUARTZ_STAIRS, 0); }
            }
            if ((mx == 2 || mx == 14) && (mz == 2 || mz == 14) && kind == Kind.DRY && Hash.unit(seed, 707, i, j) < 0.25 + 0.25 * d)
                c.chest(x, W + 1, z, mz == 2 ? 3 : 2, lv, lv.danger(x, z), "pool");
            return;
        }
        if (poolLight(seed, x, z, d)) c.set(x, F, z, SEA_LANTERN);
        c.set(x, W, z, WATER);
    }

    /** A light set into the middle of each four-block tile square (under the water in flooded rooms); a few are dead deeper in. */
    static boolean poolLight(long seed, int x, int z, double d) {
        return Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2 && Hash.unit(seed, 708, x, 7, z) >= 0.04 + 0.2 * d;
    }

    /** White tiles with grey grout every four blocks. */
    private static void tile(Canvas c, int x, int y, int z) {
        boolean grout = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
        c.set(x, y, z, CONCRETE, grout ? SILVER : WHITE);
    }

    // ---- the Deep End -------------------------------------------------------------------------------------------------
    /** The Deep End's pool: x from ax 14 to arenaLength - 22, |z| <= 16; its floor six blocks down. */
    static boolean deepEnd(Level lv, int x, int z) {
        int ax = x - lv.arenaStart();
        return ax >= 14 && ax <= lv.arenaLength - 22 && Math.abs(z) <= 16;
    }

    static final int DEEP_FLOOR = F - 5;

    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1, ax = x - lv.arenaStart(), az = Math.abs(z);
        tile(c, x, F, z);
        c.column(x, z, ceil, ceil + 1, CONCRETE, WHITE);
        if (Math.floorMod(ax, 6) == 3 && Math.floorMod(z, 6) == 3) { c.set(x, ceil, z, GLASS); c.set(x, ceil + 1, z, GLOWSTONE); }
        if (deepEnd(lv, x, z)) {
            boolean lamp = Math.floorMod(ax, 4) == 0 && Math.floorMod(z, 4) == 0;
            c.set(x, DEEP_FLOOR, z, lamp ? SEA_LANTERN : CONCRETE, lamp ? 0 : LIGHT_BLUE);
            c.column(x, z, DEEP_FLOOR + 1, W, WATER, 0);
            return;
        }
        // The deck, a step up from the pool's water line, lit from its tiles; quartz pillars; the lifeguard's chair.
        tile(c, x, W, z);
        if (Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2) c.set(x, W, z, SEA_LANTERN);
        boolean pillar = (Math.floorMod(ax, 12) == 6 || Math.floorMod(ax, 12) == 7) && (az == 26 || az == 27);
        if (pillar) { c.column(x, z, W + 1, ceil - 1, QUARTZ, 2); return; }
        int chairX = lv.arenaLength - 20;
        if (ax == chairX && az == 18) { c.column(x, z, W + 1, W + 4, QUARTZ, 2); c.set(x, W + 5, z, QUARTZ_STAIRS, z > 0 ? 2 : 3); }
        // Diving boards over the pool's long sides.
        if (az == 17 && Math.floorMod(ax, 16) == 8) c.set(x, W + 1, z, SLAB, 7);
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX(), DEEP_FLOOR + 1, 0}; }
}
