package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 4, the Electrical Corridors: grey corridors three wide and four tall on a ten-block {@link Grid}, walls packed
 * with machinery (observers, dispensers, droppers, dead lamps, vents, speakers) that faces into the corridors, end-rod
 * lights down the middle, and live floor tiles of redstone ore that shock whoever steps on them (more of them deeper in).
 * Some junctions open into transformer rooms, a caged core humming in the middle. The arena is the Substation.
 */
final class Electrical implements Style {
    static final int P = 10, AIR_H = 4, F = Level.FLOOR, W = Level.WALK, ROOM_AIR = 6;

    @Override public int floorId() { return CONCRETE; }
    @Override public int floorMeta() { return GRAY; }
    @Override public int wallId() { return STONE; }
    @Override public int wallMeta() { return 6; }
    @Override public int ceilId() { return STONE; }
    @Override public int ceilMeta() { return 6; }
    @Override public int lightId() { return SEA_LANTERN; }

    private Grid grid;

    Grid grid(Level lv, long seed) {
        Grid g = grid;
        if (g == null || g.seed != seed) grid = g = new Grid(lv, seed, 401, P, 1, 0.58, 0.55);
        return g;
    }

    /** A transformer room fills the grid cell around a junction. */
    boolean transformer(Level lv, long seed, int i, int j) {
        Grid g = grid(lv, seed);
        return Hash.unit(seed, 403, i, j) < 0.08 && g.inGrid(i, j) && g.junction(i, j);
    }

    boolean passable(Level lv, long seed, int x, int z) {
        return transformer(lv, seed, Math.floorDiv(x, P), Math.floorDiv(z, P)) || grid(lv, seed).open(x, z);
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) { return !passable(lv, seed, x, z); }

    /** Live floor: redstone ore in a corridor (never on the transformer rooms' paths, rarer by the vestibules). */
    boolean live(Level lv, long seed, int x, int z) {
        return Hash.unit(seed, 404, x, 2, z) < 0.035 + 0.075 * lv.progress(x) && !grid(lv, seed).antechamber(x, z);
    }

    private static final int[][] SIDES = {{1, 0, 5}, {-1, 0, 4}, {0, 1, 3}, {0, -1, 2}};

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        int top = Rooms.top(lv, this) + 1, i = Math.floorDiv(x, P), j = Math.floorDiv(z, P);
        if (!passable(lv, seed, x, z)) {
            c.column(x, z, F, top, STONE, 6);
            // Machinery set into a wall that faces a corridor.
            for (int[] k : SIDES) {
                if (!passable(lv, seed, x + k[0], z + k[1])) continue;
                double r = Hash.unit(seed, 405, x, 0, z);
                int face = k[2];
                if (r < 0.10) c.set(x, W, z, OBSERVER, face);
                else if (r < 0.18) c.set(x, W + 1, z, DISPENSER, face);
                else if (r < 0.24) c.set(x, W + 1, z, DROPPER, face);
                else if (r < 0.32) c.set(x, W + 2, z, LAMP_OFF);
                else if (r < 0.38) c.set(x, W + 1, z, IRON_BARS);
                else if (r < 0.42) c.set(x, W, z, NOTE);
                else if (r < 0.46) c.set(x, W + 2, z, IRON_TRAPDOOR, 8 | (face == 2 ? 1 : face == 3 ? 0 : face == 4 ? 3 : 2));
                else if (r < 0.52) c.set(x, W + 1, z, HOPPER, 0);
                break;
            }
            return;
        }
        boolean room = transformer(lv, seed, i, j);
        int air = room ? ROOM_AIR : AIR_H, ceil = F + air + 1;
        c.column(x, z, ceil, top, STONE, 6);
        int mx = Math.floorMod(x, P), mz = Math.floorMod(z, P);
        boolean centre = mx == 5 || mz == 5;
        boolean stripe = !room && !centre && Hash.unit(seed, 406, x >> 2, z >> 2) < 0.2;
        c.set(x, F, z, live(lv, seed, x, z) ? REDSTONE_ORE : CONCRETE, stripe ? (Math.floorMod(x + z, 2) == 0 ? YELLOW : BLACK) : GRAY);
        c.set(x, ceil, z, STONE, 6);
        if (room) { transformerColumn(c, lv, seed, x, z, mx, mz, ceil, d); return; }
        // End rods down the middle of each corridor, every five blocks; some are dead deeper in.
        boolean rod = (mz == 5 && Math.floorMod(x, 5) == 0 || mx == 5 && Math.floorMod(z, 5) == 0) && Hash.unit(seed, 407, x, 3, z) >= 0.1 + 0.45 * d;
        if (rod) c.set(x, ceil - 1, z, END_ROD, 0);
        Grid g = grid(lv, seed);
        if (mx == 5 && mz == 5 && g.inGrid(i, j) && g.links(i, j) == 1 && Hash.unit(seed, 408, i, j) < 0.18 + 0.2 * d)
            c.chest(x, W, z, 2, lv, lv.danger(x, z), "panel");
    }

    /** A transformer room: a caged sea-lantern core ringed with live floor, machinery around the walls. */
    private static void transformerColumn(Canvas c, Level lv, long seed, int x, int z, int mx, int mz, int ceil, double d) {
        int dx = mx - 5, dz = mz - 5, r = Math.max(Math.abs(dx), Math.abs(dz));
        if (r == 0) { c.column(x, z, Level.WALK, ceil - 1, SEA_LANTERN, 0); return; }
        if (r == 1) { c.column(x, z, Level.WALK, ceil - 2, IRON_BARS, 0); return; }
        if (r == 2 && Hash.unit(seed, 409, x, 4, z) < 0.5 + 0.4 * d) c.set(x, F, z, REDSTONE_ORE);
        if (r == 4 && !(Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) {
            double q = Hash.unit(seed, 410, x, 0, z);
            c.set(x, Level.WALK, z, q < 0.4 ? OBSERVER : q < 0.7 ? DISPENSER : DROPPER, dx > 0 && Math.abs(dx) >= Math.abs(dz) ? 4 : dx < 0 && Math.abs(dx) >= Math.abs(dz) ? 5 : dz > 0 ? 2 : 3);
        }
        if (r == 3 && Math.floorMod(mx + mz, 3) == 0) c.set(x, ceil - 1, z, END_ROD, 0);
        if (dx == 3 && dz == 3 && Hash.unit(seed, 411, x, 0, z) < 0.35) c.chest(x, Level.WALK, z, 4, lv, lv.danger(x, z), "panel");
    }

    /** Centres of the transformer rooms near a spot (for the arcs that jump from their cores). */
    java.util.List<int[]> transformersNear(Level lv, long seed, int x, int z, int range) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = Math.floorDiv(x - range, P); i <= Math.floorDiv(x + range, P); i++)
            for (int j = Math.floorDiv(z - range, P); j <= Math.floorDiv(z + range, P); j++)
                if (transformer(lv, seed, i, j)) out.add(new int[] {i * P + 5, W, j * P + 5});
        return out;
    }

    // ---- the Substation -----------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1, ax = x - lv.arenaStart();
        c.column(x, z, ceil, ceil + 1, STONE, 6);
        c.set(x, F, z, CONCRETE, Math.floorMod(ax + z, 8) == 0 ? YELLOW : GRAY);
        // Four caged cores, off the middle lane, and rods overhead.
        int cx = Math.floorMod(ax, 16) - 8, cz = Math.abs(z) - 12;
        int r = Math.max(Math.abs(cx), Math.abs(cz));
        if (ax > 6 && ax < lv.arenaLength - 8) {
            if (r == 0) { c.column(x, z, W, ceil - 1, SEA_LANTERN, 0); return; }
            if (r == 1) { c.column(x, z, W, W + 3, IRON_BARS, 0); return; }
            if (r == 2) c.set(x, F, z, REDSTONE_ORE);
        }
        if (Math.floorMod(ax, 6) == 3 && Math.floorMod(z, 6) == 3) c.set(x, ceil - 1, z, END_ROD, 0);
    }

    /** The Substation's cores (for the arcs in the boss fight). */
    static java.util.List<int[]> cores(Level lv) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int ax = 8; ax < lv.arenaLength - 8; ax += 16) for (int s = -1; s <= 1; s += 2) out.add(new int[] {lv.arenaStart() + ax, W, s * 12});
        return out;
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 6, W, 0}; }
}
