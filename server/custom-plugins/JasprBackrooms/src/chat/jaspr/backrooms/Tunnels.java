package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 3, the Maintenance Tunnels: a maze of brick tunnels three wide and three tall on an eight-block {@link Grid},
 * dark and hot: pipes run under the ceiling, magma vents sit in the floor, lava glows behind iron bars in the walls,
 * and now and then a junction opens into a boiler room. The arena is the Boiler Room.
 */
final class Tunnels implements Style {
    static final int P = 8, AIR_H = 3, F = Level.FLOOR, W = Level.WALK, ROOM_AIR = 5;

    @Override public int floorId() { return STONE_BRICK; }
    @Override public int floorMeta() { return 0; }
    @Override public int wallId() { return BRICKS; }
    @Override public int wallMeta() { return 0; }
    @Override public int ceilId() { return STONE_BRICK; }
    @Override public int ceilMeta() { return 0; }
    @Override public int lightId() { return GLOWSTONE; }

    private Grid grid;

    Grid grid(Level lv, long seed) {
        Grid g = grid;
        if (g == null || g.seed != seed) grid = g = new Grid(lv, seed, 301, P, 1, 0.6, 0.55);
        return g;
    }

    /** A boiler room fills the grid cell around a junction. */
    boolean boilerRoom(Level lv, long seed, int i, int j) {
        Grid g = grid(lv, seed);
        return Hash.unit(seed, 303, i, j) < 0.07 && g.inGrid(i, j) && g.junction(i, j);
    }

    boolean passable(Level lv, long seed, int x, int z) {
        return boilerRoom(lv, seed, Math.floorDiv(x, P), Math.floorDiv(z, P)) || grid(lv, seed).open(x, z);
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) { return !passable(lv, seed, x, z); }

    /** Columns of the maze proper (not the entry, the vestibules, the arena or the outer walls). */
    private static boolean domain(Level lv, int x, int z) {
        if (x < lv.entryEnd() || x >= lv.arenaStart() || z < lv.zMin() + Level.WALL || z >= lv.zMax() - Level.WALL) return false;
        return !(Math.abs(z) <= 3 && (x - lv.entryEnd() < Rooms.VESTIBULE || lv.arenaStart() - x <= Rooms.VESTIBULE));
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** A wall column with lava behind bars: only where every neighbour is maze, one of them a tunnel and none a boiler room. */
    boolean pocket(Level lv, long seed, int x, int z) {
        if (Hash.unit(seed, 304, x, 0, z) >= 0.05 + 0.04 * lv.progress(x) || !domain(lv, x, z) || passable(lv, seed, x, z)) return false;
        boolean tunnel = false;
        for (int[] k : SIDES) {
            int nx = x + k[0], nz = z + k[1];
            if (!domain(lv, nx, nz)) return false;
            if (passable(lv, seed, nx, nz)) {
                if (boilerRoom(lv, seed, Math.floorDiv(nx, P), Math.floorDiv(nz, P))) return false;
                tunnel = true;
            }
        }
        return tunnel;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        int top = Rooms.top(lv, this) + 1, i = Math.floorDiv(x, P), j = Math.floorDiv(z, P);
        if (!passable(lv, seed, x, z)) {
            c.column(x, z, F, top, BRICKS, 0);
            if (pocket(lv, seed, x, z)) c.set(x, W + 1, z, LAVA);
            return;
        }
        boolean room = boilerRoom(lv, seed, i, j);
        int air = room ? ROOM_AIR : AIR_H, ceil = F + air + 1;
        c.column(x, z, ceil, top, BRICKS, 0);
        int mx = Math.floorMod(x, P), mz = Math.floorMod(z, P);
        boolean centre = mx == 4 || mz == 4;
        // Floor: stone bricks, cracked in places; magma vents on the centre line, more of them deeper in.
        boolean vent = centre && !room && Hash.unit(seed, 305, x, 1, z) < 0.025 + 0.05 * d && !lv.onSpine(seed, x, z, 0.5);
        c.set(x, F, z, vent ? MAGMA : STONE_BRICK, vent ? 0 : Hash.unit(seed, 306, x, 0, z) < 0.25 ? 2 : 0);
        // Lamps at some junctions only, fewer deeper in.
        boolean lamp = mx == 4 && mz == 4 && Hash.unit(seed, 307, i, j) < 0.55 - 0.3 * d;
        c.set(x, ceil, z, lamp ? GLOWSTONE : STONE_BRICK, 0);
        // Iron bars in front of any lava pocket beside this tunnel.
        for (int[] k : SIDES) if (pocket(lv, seed, x + k[0], z + k[1])) { c.set(x, W + 1, z, IRON_BARS); break; }
        if (room) { boilerRoomColumn(c, seed, x, z, mx, mz, ceil); return; }
        // Pipes under the ceiling along the tunnel's two edges.
        boolean bandH = mz >= 3 && mz <= 5, bandV = mx >= 3 && mx <= 5;
        if (bandH && !bandV && (mz == 3 || mz == 5) || bandV && !bandH && (mx == 3 || mx == 5)) c.set(x, ceil - 1, z, NETHER_FENCE);
        // A loot chest in a dead end now and then.
        Grid g = grid(lv, seed);
        if (mx == 4 && mz == 4 && g.inGrid(i, j) && g.links(i, j) == 1 && Hash.unit(seed, 308, i, j) < 0.18 + 0.2 * d)
            c.chest(x, W, z, 2, lv, lv.danger(x, z), "tunnel");
    }

    /** A boiler room: lit furnaces and cauldrons against the walls, a pipe ring under the ceiling. */
    private static void boilerRoomColumn(Canvas c, long seed, int x, int z, int mx, int mz, int ceil) {
        boolean wallSide = mx == 0 || mx == 7 || mz == 0 || mz == 7;
        if (!wallSide) return;
        if (mx >= 3 && mx <= 5 || mz >= 3 && mz <= 5) return;   // the doorways
        c.set(x, ceil - 1, z, NETHER_FENCE);
        double r = Hash.unit(seed, 309, x, 0, z);
        if (r < 0.35) c.set(x, W, z, FURNACE_LIT, mx == 0 ? 5 : mx == 7 ? 4 : mz == 0 ? 3 : 2);
        else if (r < 0.5) c.set(x, W, z, CAULDRON, 3);
        else if (r < 0.6) c.set(x, W, z, IRON_BARS);
    }

    // ---- the Boiler Room ----------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1, half = lv.arenaWidth / 2, ax = x - lv.arenaStart();
        c.column(x, z, ceil, ceil + 1, BRICKS, 0);
        boolean grid = Math.floorMod(ax, 6) == 3 && Math.floorMod(z, 6) == 3 && Math.abs(z) > 3;
        c.set(x, F, z, grid ? MAGMA : STONE_BRICK, grid ? 0 : 2);
        if (Math.floorMod(ax, 8) == 4 && Math.floorMod(z, 8) == 4) c.set(x, ceil, z, GLOWSTONE);
        // Furnaces along the side walls, pipes overhead.
        if (Math.abs(z) == half - 1 && ax > 3 && ax < lv.arenaLength - 4) {
            c.set(x, W, z, FURNACE_LIT, z > 0 ? 2 : 3);
            if (Math.floorMod(ax, 3) == 0) c.column(x, z, W + 1, ceil - 1, IRON_BARS, 0);
        }
        if (Math.abs(z) == half - 2 || Math.floorMod(ax, 12) == 6) c.set(x, ceil - 1, z, NETHER_FENCE);
        // The great boiler at the back: a brick drum with lava inside, off the middle lane.
        int dx = ax - (lv.arenaLength - 12), dz = Math.abs(z) - 9;
        if (dx * dx + dz * dz <= 12) {
            boolean shell = dx * dx + dz * dz >= 5;
            c.column(x, z, W, W + 5, shell ? BRICKS : LAVA, 0);
            c.set(x, W + 6, z, BRICKS);
        }
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 4, W, 0}; }
}
