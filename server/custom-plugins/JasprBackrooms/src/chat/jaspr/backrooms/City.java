package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 6, the Endless City: it seems normal. A grid of streets 64 blocks apart (the avenue runs east along z = 0 from
 * the subway landing to the City Hall plaza), roads with lane markings, crosswalks and parked cars, sidewalks with
 * street lights, trees, benches and hydrants. Each 50-block lot holds one, two or four buildings (or a small park)
 * of brick, stone, quartz, concrete or terracotta, 12 to 80 blocks tall (taller deeper in), with rows of windows (a
 * few lit) and one lobby at street level that can be entered. Nobody is home. Under a sky that is always dusk, walled
 * in by endless facades. The arena is the City Hall plaza with its fountain; the exit is the subway under City Hall.
 */
final class City implements Style {
    static final int B = 64, STREET = 14, F = Level.FLOOR, W = Level.WALK, PLAZA_HALF = 36;

    @Override public int floorId() { return CONCRETE; }
    @Override public int floorMeta() { return BLACK; }
    @Override public int wallId() { return STONE_BRICK; }
    @Override public int wallMeta() { return 0; }
    @Override public int ceilId() { return STONE; }
    @Override public int ceilMeta() { return 0; }
    @Override public int lightId() { return GLOWSTONE; }

    private static final int[][] FACADES = {{BRICKS, 0}, {STONE_BRICK, 0}, {QUARTZ, 0}, {SANDSTONE, 2}, {CONCRETE, WHITE}, {CONCRETE, GRAY},
        {CONCRETE, SILVER}, {CONCRETE, CYAN}, {CONCRETE, BROWN}, {CLAY, WHITE}, {CLAY, SILVER}, {CLAY, BROWN}, {CLAY, ORANGE}, {STONE, 6},
        {STONE, 4}, {STONE, 2}, {BRICKS, 0}, {CLAY, GRAY}};

    // ---- the grid -----------------------------------------------------------------------------------------------------
    /** Position across a street running along x (0..13 on a street), and across a street running along z. */
    static int acrossX(int z) { return Math.floorMod(z + 7, B); }
    static int acrossZ(Level lv, int x) { return Math.floorMod(x - lv.x0() - 38, B); }
    static boolean streetX(int z) { return acrossX(z) < STREET; }
    static boolean streetZ(Level lv, int x) { return acrossZ(lv, x) < STREET && x - lv.x0() >= 38; }

    /** A lot's parcel: bounds in world coordinates, height, facade, and the side its lobby opens on (-1 for a park). */
    static final class Parcel {
        int x0, x1, z0, z1, height, facade, lobby;   // lobby: 0 west, 1 east, 2 north, 3 south, -1 park, -2 no lobby
        boolean[] street = new boolean[4];
    }

    static Parcel parcel(Level lv, long seed, int x, int z) {
        int lx = x - lv.x0();
        int a = Math.floorDiv(lx - 52, B), b = Math.floorDiv(z - 7, B);
        int u = lx - (52 + B * a), v = z - (7 + B * b);
        if (u >= 50 || v >= 50) return null;   // a street
        int split = Hash.range(seed, 601, a, b, 4);
        boolean splitU = split == 1 || split == 3, splitV = split == 2 || split == 3;
        if (splitU && (u == 24 || u == 25) || splitV && (v == 24 || v == 25)) return null;   // an alley
        int pu = splitU ? (u < 24 ? 0 : 1) : 0, pv = splitV ? (v < 24 ? 0 : 1) : 0;
        Parcel p = new Parcel();
        int ux0 = splitU ? pu * 26 : 0, ux1 = splitU ? ux0 + 23 : 49, vz0 = splitV ? pv * 26 : 0, vz1 = splitV ? vz0 + 23 : 49;
        p.x0 = lv.x0() + 52 + B * a + ux0; p.x1 = lv.x0() + 52 + B * a + ux1;
        p.z0 = 7 + B * b + vz0; p.z1 = 7 + B * b + vz1;
        p.street[0] = ux0 == 0; p.street[1] = ux1 == 49; p.street[2] = vz0 == 0; p.street[3] = vz1 == 49;
        long id = a * 4L + pu * 2 + pv;
        double d = lv.progress((p.x0 + p.x1) / 2.0);
        p.height = Math.min(80, 12 + Hash.range(seed, 602, id, b, 44) + (int) (24 * d));
        p.facade = Hash.range(seed, 603, id, b, FACADES.length);
        if (Hash.unit(seed, 604, id, b) < 0.08) p.lobby = -1;
        else {
            int sides = 0;
            for (boolean s : p.street) if (s) sides++;
            if (sides == 0) p.lobby = -2;
            else {
                int pick = Hash.range(seed, 605, id, b, sides);
                for (int s = 0; s < 4; s++) if (p.street[s] && pick-- == 0) { p.lobby = s; break; }
            }
        }
        return p;
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) {
        Parcel p = parcel(lv, seed, x, z);
        return p != null && p.lobby != -1;
    }

    @Override public byte open(Level lv, long seed, int x, int z) {
        Parcel p = parcel(lv, seed, x, z);
        if (p != null && p.lobby == -1) return Styles.PARK_INK;
        int m = streetX(z) ? acrossX(z) : streetZ(lv, x) ? acrossZ(lv, x) : 0;
        return m >= 3 && m <= 10 ? Styles.ROAD_INK : Styles.FLOOR_INK;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        Parcel p = parcel(lv, seed, x, z);
        if (p == null) { street(c, lv, seed, x, z); return; }
        if (p.lobby == -1) { park(c, seed, x, z); return; }
        building(c, lv, seed, p, x, z);
    }

    private static void street(Canvas c, Level lv, long seed, int x, int z) {
        boolean sx = streetX(z), sz = streetZ(lv, x);
        if (!sx && !sz) {   // an alley between two buildings of a lot
            c.set(x, F, z, CONCRETE, GRAY);
            if (Hash.unit(seed, 606, x >> 2, z >> 2) < 0.06 && Hash.unit(seed, 607, x, 0, z) < 0.5) c.set(x, W, z, CONCRETE, GREEN);   // dumpsters
            return;
        }
        int m = sx ? acrossX(z) : acrossZ(lv, x), along = sx ? x - lv.x0() : z;
        boolean cross = sx && sz;
        boolean sidewalk = !cross && (m <= 2 || m >= 11);
        if (sidewalk) {
            c.set(x, F, z, m == 2 || m == 11 ? STONE_BRICK : DOUBLE_SLAB, m == 2 || m == 11 ? 0 : 8);
            int row = m <= 2 ? 1 : 12, s = Math.floorMod(along, 16);
            boolean nearTrunk = (s == 15 || s <= 1) && Math.abs(m - row) <= 1;
            if (m == row && s == 8) { c.column(x, z, W, W + 3, FENCE, 0); c.set(x, W + 4, z, GLOWSTONE); }   // street light
            else if (m == row && s == 0) { c.column(x, z, W, W + 2, LOG, 0); c.column(x, z, W + 3, W + 4, LEAVES, 4); }   // tree
            else if (nearTrunk) c.column(x, z, W + 2, W + 3, LEAVES, 4);   // its crown
            if ((m == 2 || m == 11) && Math.floorMod(along, 16) >= 4 && Math.floorMod(along, 16) <= 5) c.set(x, W, z, OAK_STAIRS, benchFacing(sx, m));
            if ((m == 2 || m == 11) && Math.floorMod(along, 32) == 12) c.set(x, W, z, 215, 0);   // a hydrant (red nether brick)
            return;
        }
        c.set(x, F, z, CONCRETE, BLACK);
        if (cross) return;
        boolean edgeOfCrossing = sx ? streetZ(lv, x - 2) || streetZ(lv, x + 2) : streetX(z - 2) || streetX(z + 2);
        if (edgeOfCrossing && Math.floorMod(m, 2) == 0) { c.set(x, F, z, CONCRETE, WHITE); return; }   // crosswalk
        if ((m == 6 || m == 7) && Math.floorMod(along, 6) < 3) c.set(x, F, z, CONCRETE, YELLOW);
        // Parked cars along both curbs, four blocks long.
        boolean lane = m == 3 || m == 4 || m == 9 || m == 10;
        int slot = Math.floorDiv(along, 8), in = Math.floorMod(along, 8);
        int street = sx ? Math.floorDiv(z + 7, B) : Math.floorDiv(x - lv.x0() - 38, B) + 1000;
        if (lane && in >= 1 && in <= 4 && Hash.unit(seed, 608, slot, m < 7 ? 0 : 1, street) < 0.22) {
            int colour = Hash.range(seed, 609, slot, m < 7 ? 0 : 1, street, 16);
            c.set(x, W, z, CONCRETE, colour);
            c.set(x, W + 1, z, in == 2 || in == 3 ? GLASS : CONCRETE, in == 2 || in == 3 ? 0 : colour);
        }
    }

    private static int benchFacing(boolean alongX, int m) {
        if (alongX) return m <= 2 ? 3 : 2;
        return m <= 2 ? 1 : 0;
    }

    private static void park(Canvas c, long seed, int x, int z) {
        boolean path = Math.floorMod(x, 7) == 3 || Math.floorMod(z, 7) == 3;
        c.set(x, F, z, path ? 208 : GRASS, 0);
        if (!path && Hash.unit(seed, 610, x, 0, z) < 0.04) { c.column(x, z, W, W + 3, LOG, 0); c.column(x, z, W + 4, W + 5, LEAVES, 4); }
        else if (!path && Hash.unit(seed, 611, x, 1, z) < 0.12) c.set(x, W, z, LEAVES, 4);
    }

    private void building(Canvas c, Level lv, long seed, Parcel p, int x, int z) {
        int top = F + p.height, id = FACADES[p.facade][0], meta = FACADES[p.facade][1];
        int dw = x - p.x0, de = p.x1 - x, dn = z - p.z0, ds = p.z1 - z;
        int e = Math.min(Math.min(dw, de), Math.min(dn, ds));
        int side = e == dw ? 0 : e == de ? 1 : e == dn ? 2 : 3;
        int along = side < 2 ? z - p.z0 : x - p.x0, len = side < 2 ? p.z1 - p.z0 + 1 : p.x1 - p.x0 + 1;
        boolean corner = (dw == e || de == e) && (dn == e || ds == e);
        c.set(x, F, z, id, meta);
        if (e == 0) {
            c.column(x, z, W, top + 1, id, meta);   // facade and parapet
            if (!corner) {
                for (int y = F + 5; y <= top - 2; y++)
                    if (Math.floorMod(y - F - 1, 4) >= 1 && Math.floorMod(y - F - 1, 4) <= 2 && Math.floorMod(along, 3) != 0) c.set(x, y, z, STAINED_PANE, window(seed, x, y, z));
                if (p.street[side]) {
                    if (side == p.lobby && Math.abs(along - len / 2) <= 1 && along != len / 2 + 1) c.column(x, z, W, W + 2, AIR, 0);   // the door
                    else if (Math.floorMod(along, 4) == 1 || Math.floorMod(along, 4) == 2) c.column(x, z, W + 1, W + 2, PANE, 0);   // shop windows
                }
            }
            return;
        }
        c.column(x, z, W, top, STONE, 0);
        c.set(x, top, z, id, meta);
        // Rooftop clutter: air-conditioning boxes and vents.
        if (e >= 2 && Hash.unit(seed, 612, x >> 1, z >> 1) < 0.05) c.set(x, top + 1, z, Hash.unit(seed, 613, x, 0, z) < 0.5 ? IRON_BARS : NOTE);
        // The rooms behind the windows: dark, a few lit.
        if (e == 1 && !corner) {
            for (int y = F + 5; y <= top - 2; y++)
                if (Math.floorMod(y - F - 1, 4) >= 1 && Math.floorMod(y - F - 1, 4) <= 2 && Math.floorMod(along, 3) != 0)
                    c.set(x, y, z, Hash.unit(seed, 614, x >> 1, y >> 2, z >> 1) < 0.09 ? GLOWSTONE : CONCRETE, BLACK);
            if (p.street[side] && side != p.lobby) c.column(x, z, W + 1, W + 2, CONCRETE, BLACK);
        }
        // The lobby behind the door: six blocks deep, quartz floor and ceiling, a reception desk.
        if (p.lobby >= 0) {
            int depth = p.lobby == 0 ? dw : p.lobby == 1 ? de : p.lobby == 2 ? dn : ds;
            int lAlong = p.lobby < 2 ? z - p.z0 : x - p.x0, lLen = p.lobby < 2 ? p.z1 - p.z0 + 1 : p.x1 - p.x0 + 1;
            if (depth >= 1 && depth <= 6 && lAlong >= 2 && lAlong <= lLen - 3) {
                c.set(x, F, z, QUARTZ);
                c.column(x, z, W, W + 3, AIR, 0);
                c.set(x, W + 4, z, depth == 3 && Math.floorMod(lAlong, 4) == 0 ? GLOWSTONE : QUARTZ, 0);
                if (depth == 5 && Math.abs(lAlong - lLen / 2) <= 2) c.set(x, W, z, SLAB, 15);   // reception desk
                if (depth == 6 && (lAlong == 2 || lAlong == lLen - 3)) c.set(x, W, z, LEAVES, 4);
                if (depth == 6 && lAlong == 3 && Hash.unit(seed, 615, p.x0, 0, p.z0) < 0.35 + 0.3 * lv.progress(x)) c.chest(x, W, z, facingInto(p.lobby), lv, lv.danger(x, z), "lobby");
            }
        }
    }

    /** Chest facing away from the lobby's back wall, towards the door side. */
    private static int facingInto(int lobby) { return lobby == 0 ? 4 : lobby == 1 ? 5 : lobby == 2 ? 2 : 3; }

    private static int window(long seed, int x, int y, int z) {
        double r = Hash.unit(seed, 616, x >> 2, y >> 3, z >> 2);
        return r < 0.4 ? GRAY : r < 0.75 ? BLACK : r < 0.9 ? LIGHT_BLUE : CYAN;
    }

    // ---- the walls of the city: endless facades ------------------------------------------------------------------------
    @Override public boolean boundary(Canvas c, Level lv, int x, int z) {
        c.column(x, z, F, Rooms.CITY_WALL_TOP, STONE_BRICK, 0);
        c.column(x, z, Rooms.CITY_WALL_TOP + 1, 255, BARRIER, 0);
        boolean inner = x == lv.x0() + 1 || x == lv.xEnd() - 2 || z == lv.zMin() + 1 || z == lv.zMax() - 2;
        int along = x == lv.x0() + 1 || x == lv.xEnd() - 2 ? z : x;
        if (inner) for (int y = F + 5; y < Rooms.CITY_WALL_TOP - 4; y++)
            if (Math.floorMod(y - F - 1, 4) >= 1 && Math.floorMod(y - F - 1, 4) <= 2 && Math.floorMod(along, 3) != 0) c.set(x, y, z, STAINED_PANE, BLACK);
        return true;
    }

    // ---- the City Hall plaza --------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        int ax = x - lv.arenaStart(), az = Math.abs(z), cx = lv.arenaCentreX();
        boolean west = x == lv.arenaStart(), east = x == lv.exitStart() - 1;
        if (az > PLAZA_HALF || west && az > 7) {   // the buildings around the plaza, windows facing it
            int top = F + 58;
            c.column(x, z, F, top, STONE_BRICK, 0);
            boolean face = az == PLAZA_HALF + 1 && !west || west && az <= PLAZA_HALF;
            int along = az == PLAZA_HALF + 1 ? x : z;
            if (face) for (int y = F + 5; y <= top - 2; y++)
                if (Math.floorMod(y - F - 1, 4) >= 1 && Math.floorMod(y - F - 1, 4) <= 2 && Math.floorMod(along, 3) != 0) c.set(x, y, z, STAINED_PANE, window(seed, x, y, z));
            return;
        }
        if (east) {   // City Hall: quartz, with the subway door
            c.column(x, z, F, F + 70, QUARTZ, 0);
            if (az <= 2) { c.column(x, z, W, F + 4, AIR, 0); return; }
            if (Math.floorMod(z, 4) == 0) c.column(x, z, W, F + 20, QUARTZ, 2);
            return;
        }
        // The square: stone paving in a checker, a fountain in the middle, trees and lamps in rings.
        c.set(x, F, z, Math.floorMod(ax + z, 2) == 0 ? STONE_BRICK : DOUBLE_SLAB, Math.floorMod(ax + z, 2) == 0 ? 0 : 8);
        int dx = x - cx, r2 = dx * dx + z * z;
        if (r2 < 49) {
            if (r2 >= 36) c.set(x, W, z, STONE_BRICK, 0);
            else if (r2 <= 2) c.column(x, z, W, W + 4, QUARTZ, 2);
            else c.set(x, W, z, WATER);
            return;
        }
        boolean ring = r2 >= 18 * 18 && r2 < 19 * 19;
        if (ring && Math.floorMod(ax + z * 3, 11) == 0) { c.column(x, z, W, W + 2, LOG, 0); c.column(x, z, W + 3, W + 4, LEAVES, 4); return; }
        if (Math.floorMod(ax, 12) == 6 && (az == 24 || az == 12) && Math.abs(dx) > 8) { c.column(x, z, W, W + 3, FENCE, 0); c.set(x, W + 4, z, GLOWSTONE); }
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaCentreX() + 12, W, 0}; }

    /** The roof height of the building at a spot, or -1 (for rooftop stalkers). */
    static int roof(Level lv, long seed, int x, int z) {
        Parcel p = parcel(lv, seed, x, z);
        if (p == null || p.lobby == -1) return -1;
        int dw = x - p.x0, de = p.x1 - x, dn = z - p.z0, ds = p.z1 - z;
        return Math.min(Math.min(dw, de), Math.min(dn, ds)) >= 2 ? F + p.height : -1;
    }
}
