package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 2, the Warehouse: concrete floors under a roof fourteen blocks up, iron beams, lamps hanging high under the roof
 * and work lamps on long chains over the aisles, stone-brick
 * pillars every sixteen blocks, and endless pallet racks (three shelves of crates) with nine-block aisles between them
 * and cross aisles every 24 blocks. Partition walls split it into halls; each has two wide gates and the spine always
 * gets through. The lamps fail more often deeper in. Wet patches on the floor. The arena is the Loading Bay.
 */
final class Warehouse implements Style {
    static final int AIR_H = 14, F = Level.FLOOR, W = Level.WALK, CEIL = F + AIR_H + 1;
    static final int HALL_X = 96, HALL_Z = 80;

    @Override public int floorId() { return CONCRETE; }
    @Override public int floorMeta() { return SILVER; }
    @Override public int wallId() { return STONE_BRICK; }
    @Override public int wallMeta() { return 0; }
    @Override public int ceilId() { return CONCRETE; }
    @Override public int ceilMeta() { return GRAY; }
    @Override public int lightId() { return GLOWSTONE; }

    // ---- the plan -----------------------------------------------------------------------------------------------------
    /** A partition wall two blocks thick every HALL_X along x (a wall across the level) and every HALL_Z along z. */
    static boolean partitionX(Level lv, int x) { return x > lv.entryEnd() + 20 && x < lv.arenaStart() - 20 && Math.floorMod(x - lv.x0(), HALL_X) < 2; }
    static boolean partitionZ(int z) { return Math.floorMod(z, HALL_Z) < 2; }

    /** Gates in the partition across the level at x: two eight-block openings, plus the spine. */
    static boolean gateX(Level lv, long seed, int x, int z) {
        if (lv.onSpine(seed, x, z, 3.5)) return true;
        int wall = Math.floorDiv(x - lv.x0(), HALL_X), span = lv.width - 40;
        for (int g = 0; g < 2; g++) {
            int at = lv.zMin() + 18 + Hash.range(seed, 201, wall, g, span);
            if (z >= at && z < at + 8) return true;
        }
        return false;
    }

    /** Gates in a partition along the level: one eight-block opening per hall, plus the spine. */
    static boolean gateZ(Level lv, long seed, int x, int z) {
        if (lv.onSpine(seed, x, z, 3.5)) return true;
        int hall = Math.floorDiv(x - lv.x0(), HALL_X), row = Math.floorDiv(z, HALL_Z);
        int at = lv.x0() + hall * HALL_X + 10 + Hash.range(seed, 202, hall, row, HALL_X - 28);
        return x >= at && x < at + 8;
    }

    static boolean pillar(int x, int z) { return Math.floorMod(x, 16) < 2 && Math.floorMod(z, 16) < 2; }

    /** Rack rows: three blocks deep every twelve, broken by a cross aisle every 24 blocks and wherever the spine runs. */
    static boolean rack(Level lv, long seed, int x, int z) {
        int rz = Math.floorMod(z, 12);
        if (rz < 3 || rz > 5 || Math.floorMod(x, 24) < 4) return false;
        if (partitionZ(z + 2) || partitionZ(z - 2) || partitionX(lv, x + 3) || partitionX(lv, x - 3)) return false;
        return !lv.onSpine(seed, x, z, 3.5);
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) {
        if (partitionX(lv, x)) return !gateX(lv, seed, x, z);
        if (partitionZ(z)) return !gateZ(lv, seed, x, z);
        return pillar(x, z) || rack(lv, seed, x, z);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        c.set(x, CEIL + 1, z, STONE);
        boolean px = partitionX(lv, x), pz = partitionZ(z);
        if (px && !gateX(lv, seed, x, z) || pz && !px && !gateZ(lv, seed, x, z)) {
            c.column(x, z, F, W + 3, STONE_BRICK, Hash.unit(seed, 203, x, 0, z) < 0.15 ? 2 : 0);
            c.column(x, z, W + 4, CEIL, CONCRETE, GRAY);
            return;
        }
        floor(c, seed, x, z, d);
        c.set(x, CEIL, z, CONCRETE, GRAY);
        if (px || pz) {   // a gate: a lintel of concrete above the opening
            c.column(x, z, W + 8, CEIL - 1, CONCRETE, GRAY);
            if (Math.floorMod(x + z, 2) == 0) c.set(x, W + 7, z, CONCRETE, YELLOW);
            return;
        }
        if (pillar(x, z)) { c.column(x, z, W, CEIL - 1, STONE_BRICK, 0); return; }
        // Beams across the roof and lamps hanging on chains.
        if (Math.floorMod(x, 16) == 0) c.set(x, CEIL - 2, z, IRON_BARS);
        if (Math.floorMod(x, 16) == 8 && Math.floorMod(z, 16) == 8) {
            c.column(x, z, CEIL - 3, CEIL - 1, FENCE, 0);
            boolean broken = Hash.unit(seed, 204, x, 1, z) < 0.08 + 0.5 * d;
            c.set(x, CEIL - 4, z, broken ? COAL_BLOCK : GLOWSTONE);
        }
        // Work lamps on long chains down the middle of each aisle, five blocks over the floor.
        if (Math.floorMod(x, 6) == 3 && Math.floorMod(z, 12) == 10) {
            c.column(x, z, W + 6, CEIL - 1, FENCE, 0);
            boolean broken = Hash.unit(seed, 213, x, 1, z) < 0.08 + 0.5 * d;
            c.set(x, W + 5, z, broken ? COAL_BLOCK : GLOWSTONE);
        }
        if (rack(lv, seed, x, z)) rackColumn(c, lv, seed, x, z, d);
    }

    private static void floor(Canvas c, long seed, int x, int z, double d) {
        int rz = Math.floorMod(z, 12);
        boolean stripe = (rz == 2 || rz == 6) && Math.floorMod(x, 24) >= 4;
        if (Hash.unit(seed, 205, x >> 2, z >> 2) < 0.025 + 0.02 * d && Hash.unit(seed, 206, x, 0, z) < 0.65 && !stripe) {
            c.set(x, F, z, WATER);   // a wet patch, flush with the floor
            return;
        }
        int ink = stripe ? YELLOW : Hash.unit(seed, 207, x >> 1, z >> 1) < 0.08 ? GRAY : SILVER;
        c.set(x, F, z, CONCRETE, ink);
    }

    /** One column of a rack: spruce posts on its edges, shelves at three heights, crates between them. */
    private static void rackColumn(Canvas c, Level lv, long seed, int x, int z, double d) {
        int rz = Math.floorMod(z, 12);
        boolean post = Math.floorMod(x, 4) == 0 && (rz == 3 || rz == 5);
        for (int shelf = 0; shelf < 3; shelf++) {
            int y0 = W + shelf * 4;
            if (post) c.column(x, z, y0, y0 + 2, SPRUCE_FENCE, 0);
            else for (int y = y0; y <= y0 + 2; y++) crate(c, lv, seed, x, y, z, d);
            c.set(x, y0 + 3, z, PLANKS, 1);
        }
    }

    private static void crate(Canvas c, Level lv, long seed, int x, int y, int z, double d) {
        double r = Hash.unit(seed, 208, x, y, z);
        if (r < 0.26) return;   // a gap on the shelf
        if (y == W && r > 0.994 - 0.004 * d) { c.chest(x, y, z, Math.floorMod(z, 12) == 3 ? 2 : 3, lv, lv.danger(x, z), "crate"); return; }
        int kind = Hash.range(seed, 209, x >> 1, y, z, 8);
        switch (kind) {
            case 0: c.set(x, y, z, PLANKS, 1); break;
            case 1: c.set(x, y, z, LOG, 0); break;
            case 2: c.set(x, y, z, HAY); break;
            case 3: c.set(x, y, z, WOOL, BROWN); break;
            case 4: c.set(x, y, z, NOTE); break;
            case 5: c.set(x, y, z, BOOKSHELF); break;
            case 6: c.set(x, y, z, WORKBENCH); break;
            default: c.set(x, y, z, PLANKS, 2); break;
        }
    }

    // ---- the Loading Bay ----------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1, half = lv.arenaWidth / 2, ax = x - lv.arenaStart();
        c.set(x, ceil, z, CONCRETE, GRAY);
        c.set(x, ceil + 1, z, STONE);
        boolean edge = Math.abs(z) >= half - 3 || ax <= 2 || ax >= lv.arenaLength - 4;
        c.set(x, F, z, CONCRETE, edge ? (Math.floorMod(x + z, 2) == 0 ? YELLOW : BLACK) : SILVER);
        // Crate stacks along the walls (never in front of the two doors), lamps on a grid.
        boolean door = Math.abs(z) <= 4 && (ax <= 3 || ax >= lv.arenaLength - 5);
        if (!door && (Math.abs(z) >= half - 2 || ax <= 1 || ax >= lv.arenaLength - 3) && Hash.unit(c.seed, 210, x >> 1, z >> 1) < 0.7) {
            int h = 1 + Hash.range(c.seed, 211, x >> 1, 0, z >> 1, 4);
            c.column(x, z, W, W + h - 1, Hash.unit(c.seed, 212, x, 0, z) < 0.5 ? PLANKS : WOOL, Hash.unit(c.seed, 212, x, 0, z) < 0.5 ? 1 : BROWN);
        }
        // Lamps on long chains, six blocks over the floor (the roof is too high to light the fight).
        if (Math.floorMod(ax, 10) == 5 && Math.floorMod(z, 10) == 5) { c.column(x, z, W + 7, ceil - 1, FENCE, 0); c.set(x, W + 6, z, GLOWSTONE); }
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 8, W, 0}; }
}
