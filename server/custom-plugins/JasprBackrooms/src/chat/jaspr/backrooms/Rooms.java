package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * The pieces every level shares: the solid ground and the zone's outer walls, the entry room (the Threshold with the
 * gate and the level doors in level 1, a landing elsewhere), the short vestibules that join the entry room and the
 * arena to the level's main space, and the exit corridor behind the arena. Also the fixed coordinates the plugin needs
 * (gate, arrival points, door alcoves, exit), so generation and travel always agree.
 */
final class Rooms {
    private Rooms() {}

    static final int FLOOR = Level.FLOOR, WALK = Level.WALK;
    /** The Threshold: interior half-width (z) and height; the gate stands at x0 + GATE_X, facing east. */
    static final int HUB_HALF = 17, HUB_AIR = 7, GATE_X = 22;
    /** Level doors in the Threshold: x offsets of the alcoves along the north wall (levels 2-4) and south wall (5-7). */
    static final int[] DOOR_X = {10, 20, 30};
    static final int LANDING_HALF = 8, VESTIBULE = 6;
    /** Top of the solid walls around the city's zone; barrier blocks close the sky above it. */
    static final int CITY_WALL_TOP = 124;

    // ---- fixed places -------------------------------------------------------------------------------------------------
    /** Where a player stands after coming through the gate (in front of it, facing east): x, y, z, yaw. */
    static double[] threshold() { return new double[] {Level.YELLOW.x0() + GATE_X + 2.5, WALK, 0.0, -90}; }
    /** Arrival point of a level's entry room (the Threshold for level 1): x, y, z, yaw. */
    static double[] arrival(Level lv) {
        if (lv.number == 1) return threshold();
        return new double[] {lv.x0() + 8.5, WALK, 0.5, -90};
    }
    /** The Threshold's door to level n (2..7): x and z of the first of its 2x2 trigger blocks. */
    static int[] doorAlcove(int level) {
        int i = level - 2, x = Level.YELLOW.x0() + DOOR_X[i % 3];
        return i < 3 ? new int[] {x, -HUB_HALF - 3} : new int[] {x, HUB_HALF + 2};
    }
    /** Whether a spot lies in the exit's trigger area (the last four blocks of the exit corridor). */
    static boolean inExit(Level lv, double x, double y, double z) {
        return x >= lv.xEnd() - Level.WALL - 4 && x < lv.xEnd() - Level.WALL && z >= -2 && z < 3 && y >= FLOOR && y < FLOOR + 5;
    }
    /** Whether a spot lies in a landing's return alcove (back to the Threshold). */
    static boolean inReturn(Level lv, double x, double y, double z) {
        return lv.number > 1 && x >= lv.x0() + 2 && x < lv.x0() + 4 && z >= -1 && z < 1 && y >= FLOOR && y < FLOOR + 4;
    }
    /** The level (2..7) whose door alcove in the Threshold holds the spot, or 0. */
    static int inDoor(double x, double y, double z) {
        if (y < FLOOR || y >= FLOOR + 4) return 0;
        for (int n = 2; n <= 7; n++) {
            int[] a = doorAlcove(n);
            if (x >= a[0] && x < a[0] + 2 && z >= a[1] && z < a[1] + 2) return n;
        }
        return 0;
    }
    /** The gate's portal blocks: x, lowest y, lowest z (two wide along z, three tall). */
    static int[] gate() { return new int[] {Level.YELLOW.x0() + GATE_X, WALK, -1}; }
    /** Top of the level's enclosed space (the ceiling block), for boundaries and rescue checks. */
    static int top(Level lv, Style s) { return lv == Level.CITY ? CITY_WALL_TOP : FLOOR + Math.max(Math.max(lv.air, s.roomAir()), arenaAir(lv)) + 1; }
    /** Air height of a level's arena (arenas are taller than the rooms around them). */
    static int arenaAir(Level lv) {
        switch (lv) {
            case YELLOW: return 7;
            case WAREHOUSE: return 16;
            case TUNNELS: return 8;
            case ELECTRICAL: return 9;
            case OFFICE: return 7;
            case CITY: return 0;
            default: return 22;
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    static void column(Canvas c, Level lv, Style s, int x, int z) {
        base(c, x, z);
        boolean edge = x < lv.x0() + Level.WALL || x >= lv.xEnd() - Level.WALL || z < lv.zMin() + Level.WALL || z >= lv.zMax() - Level.WALL;
        if (edge) { boundary(c, lv, s, x, z); return; }
        if (x < lv.entryEnd()) { if (lv.number == 1) hub(c, lv, s, x, z); else landing(c, lv, s, x, z); return; }
        if (x >= lv.exitStart()) { exit(c, lv, s, x, z); return; }
        if (x >= lv.arenaStart()) {
            s.arena(c, lv, x, z);
            if (x == lv.exitStart() - 2 && z == 0)
                c.sign(x, FLOOR + 5, z, 4, "§4§lEXIT\n" + (lv.last() ? "Out of the\nBackrooms" : "to " + lv.next().title.replace("The ", "the ")));
            return;
        }
        if (lv != Level.CITY && Math.abs(z) <= 3 && (x - lv.entryEnd() < VESTIBULE || lv.arenaStart() - x <= VESTIBULE)) {
            solid(c, s, x, z, top(lv, s) + 1);
            room(c, s, x, z, 4, true);
            return;
        }
        s.body(c, lv, x, z);
    }

    static void base(Canvas c, int x, int z) {
        c.set(x, 0, z, BEDROCK);
        c.column(x, z, 1, FLOOR - 1, STONE, 0);
    }

    /** Solid wall material from the floor up to y (inclusive). */
    static void solid(Canvas c, Style s, int x, int z, int top) {
        c.column(x, z, FLOOR, top, s.wallId(), s.wallMeta());
    }

    private static void boundary(Canvas c, Level lv, Style s, int x, int z) {
        if (s.boundary(c, lv, x, z)) return;
        if (lv == Level.CITY) {
            c.column(x, z, FLOOR, CITY_WALL_TOP, s.wallId(), s.wallMeta());
            c.column(x, z, CITY_WALL_TOP + 1, 255, BARRIER, 0);
        } else solid(c, s, x, z, top(lv, s) + 1);
    }

    /**
     * The arena's shell for a column: solid outside the arena and on its walls, with a doorway four blocks tall in the
     * west wall (where the spine arrives) and in the east wall (into the exit corridor). Returns true for an interior
     * column, which the style fills (nothing is drawn there yet).
     */
    static boolean arenaShell(Canvas c, Level lv, Style s, int x, int z) {
        int half = lv.arenaWidth / 2, top = lv == Level.CITY ? CITY_WALL_TOP : top(lv, s) + 1;
        boolean west = x == lv.arenaStart(), east = x == lv.exitStart() - 1;
        if (Math.abs(z) < half && !west && !east) return true;
        solid(c, s, x, z, top);
        if (lv == Level.CITY) c.column(x, z, CITY_WALL_TOP + 1, 255, BARRIER, 0);
        if (Math.abs(z) < half && (west && Math.abs(z) <= 3 || east && Math.abs(z) <= 2)) {
            c.set(x, FLOOR, z, s.floorId(), s.floorMeta());
            c.column(x, z, WALK, FLOOR + 4, AIR, 0);
        }
        return false;
    }

    /** Floor, air up to {@code air} blocks, the ceiling (a lamp every four blocks when lit): an enclosed room column. */
    static void room(Canvas c, Style s, int x, int z, int air, boolean lit) {
        c.set(x, FLOOR, z, s.floorId(), s.floorMeta());
        c.column(x, z, WALK, FLOOR + air, AIR, 0);
        int ceil = FLOOR + air + 1;
        boolean lamp = lit && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0;
        c.set(x, ceil, z, lamp ? s.lightId() : s.ceilId(), lamp ? s.lightMeta() : s.ceilMeta());
    }

    // ---- the Threshold (level 1's entry) ------------------------------------------------------------------------------
    private static void hub(Canvas c, Level lv, Style s, int x, int z) {
        int hx = x - lv.x0(), top = FLOOR + HUB_AIR + 2;
        boolean inside = hx >= 4 && hx <= 45 && Math.abs(z) <= HUB_HALF;
        boolean eastDoor = hx >= 44 && z >= -2 && z <= 1;
        int alcove = 0;
        for (int n = 2; n <= 7 && alcove == 0; n++) {
            int[] a = doorAlcove(n);
            boolean north = n <= 4;
            int zLo = north ? -HUB_HALF - 3 : HUB_HALF + 1, zHi = north ? -HUB_HALF - 1 : HUB_HALF + 3;
            if (x >= a[0] && x <= a[0] + 1 && z >= zLo && z <= zHi) alcove = n;
        }
        solid(c, s, x, z, top);
        if (alcove != 0) {
            c.set(x, FLOOR, z, WOOL, woolOf(Level.of(alcove)));
            c.column(x, z, WALK, WALK + 2, AIR, 0);
            c.set(x, WALK + 3, z, GLOWSTONE);
            return;
        }
        if (!inside) {
            if (eastDoor) { c.set(x, FLOOR, z, SANDSTONE, 2); c.column(x, z, WALK, WALK + 3, AIR, 0); }
            return;
        }
        // The room: warm sandstone, yellow carpet, a bright grid of lamps; pilasters on the walls. Around the gate a bare
        // sandstone dais (the guide stands on bare floor), edged with chiseled sandstone.
        int gx0 = lv.x0() + GATE_X;
        boolean dais = x >= gx0 - 3 && x <= gx0 + 8 && Math.abs(z) <= 5;
        boolean daisEdge = dais && (x == gx0 - 3 || x == gx0 + 8 || Math.abs(z) == 5);
        boolean edge = hx == 4 || hx == 45 || Math.abs(z) == HUB_HALF;
        // The ceiling is too high to light the floor alone: light hidden under the carpet keeps the Threshold bright.
        boolean underLight = !dais && !edge && Math.floorMod(hx, 3) == 1 && Math.floorMod(z, 3) == 1;
        c.set(x, FLOOR, z, underLight ? GLOWSTONE : SANDSTONE, underLight ? 0 : daisEdge ? 1 : 2);
        if (!dais) c.set(x, WALK, z, CARPET, YELLOW);
        c.column(x, z, dais ? WALK : WALK + 1, FLOOR + HUB_AIR, AIR, 0);
        boolean lamp = Math.floorMod(hx, 4) == 2 && Math.floorMod(z, 4) == 2;
        c.set(x, FLOOR + HUB_AIR + 1, z, lamp ? GLOWSTONE : WOOL, 0);
        boolean corner = (hx == 4 || hx == 45) && Math.abs(z) == HUB_HALF;
        if (edge && (corner || Math.abs(z) == HUB_HALF && Math.floorMod(hx, 10) == 5)) c.column(x, z, WALK, FLOOR + HUB_AIR, SANDSTONE, 1);
        // The gate: yellow glazed terracotta around a portal two wide and three tall, ringed with orange carpet.
        int gx = lv.x0() + GATE_X;
        if (x == gx && z >= -2 && z <= 1) {
            if (z == -2 || z == 1) c.column(x, z, FLOOR, FLOOR + 4, YELLOW_GLAZED, 0);
            else { c.set(x, FLOOR, z, YELLOW_GLAZED, 0); c.column(x, z, WALK, WALK + 2, PORTAL, 2); c.set(x, FLOOR + 4, z, YELLOW_GLAZED, 0); }
        }
        // Signs: the level doors (above each alcove) and a greeting by the gate.
        for (int n = 2; n <= 7; n++) {
            int[] a = doorAlcove(n);
            boolean north = n <= 4;
            if (x == a[0] && z == (north ? -HUB_HALF : HUB_HALF))
                c.sign(x, WALK + 3, z, north ? 3 : 2, "§lLevel " + n + "\n" + Level.of(n).title.replace("The ", "") + "\n§8opens once\n§8you reach it");
        }
        if (x == gx + 2 && z == HUB_HALF) c.sign(x, WALK + 2, z, 2, "§lTHE THRESHOLD\nThe gate home\nis behind you.\nGo east.");
        if (x == lv.x0() + 45 && z == 3) c.sign(x, WALK + 2, z, 4, "§lLevel 1\nThe Yellow\nRooms\n→ east →");
    }

    static int woolOf(Level lv) {
        switch (lv) {
            case YELLOW: return YELLOW;
            case WAREHOUSE: return ORANGE;
            case TUNNELS: return RED;
            case ELECTRICAL: return LIGHT_BLUE;
            case OFFICE: return SILVER;
            case CITY: return BLUE;
            default: return CYAN;
        }
    }

    // ---- a landing (levels 2-7) ---------------------------------------------------------------------------------------
    private static void landing(Canvas c, Level lv, Style s, int x, int z) {
        int hx = x - lv.x0(), air = s.roomAir(), top = FLOOR + air + 2;
        boolean inside = hx >= 4 && hx <= 27 && Math.abs(z) <= LANDING_HALF;
        boolean eastDoor = hx >= 28 && z >= -3 && z <= 2;
        boolean back = hx >= 2 && hx <= 3 && z >= -1 && z <= 0;
        solid(c, s, x, z, lv == Level.CITY ? CITY_WALL_TOP : top);
        if (lv == Level.CITY) c.column(x, z, CITY_WALL_TOP + 1, 255, BARRIER, 0);
        if (back) {
            c.set(x, FLOOR, z, WOOL, woolOf(Level.YELLOW));
            c.column(x, z, WALK, WALK + 2, AIR, 0);
            c.set(x, WALK + 3, z, GLOWSTONE);
            return;
        }
        if (!inside && !eastDoor) return;
        // A landing is a safe room, and bright: lamps set into the floor between the ceiling's.
        boolean floorLamp = inside && hx > 4 && hx < 27 && Math.abs(z) < LANDING_HALF && Math.floorMod(hx, 4) == 0 && Math.floorMod(z, 4) == 0;
        c.set(x, FLOOR, z, floorLamp ? SEA_LANTERN : s.floorId(), floorLamp ? 0 : s.floorMeta());
        c.column(x, z, WALK, FLOOR + (inside ? air : 4), AIR, 0);
        if (inside) {
            boolean lamp = Math.floorMod(hx, 4) == 2 && Math.floorMod(z, 4) == 2;
            c.set(x, FLOOR + air + 1, z, lamp ? GLOWSTONE : s.ceilId(), lamp ? 0 : s.ceilMeta());
            if (hx == 4 && z == 2) c.sign(x, WALK + 2, z, 5, "§lBack to the\n§lThreshold\n← walk in");
            if (hx == 27 && z == 4) c.sign(x, WALK + 2, z, 4, "§l" + lv.label().replace(": ", "\n") + "\n→ east →");
        }
    }

    // ---- the exit corridor ----------------------------------------------------------------------------------------------
    private static void exit(Canvas c, Level lv, Style s, int x, int z) {
        solid(c, s, x, z, top(lv, s) + 1);
        if (lv == Level.CITY) c.column(x, z, top(lv, s) + 2, 255, BARRIER, 0);
        if (Math.abs(z) > 2) return;
        int left = lv.xEnd() - Level.WALL - x;   // EXIT_LEN .. 1
        boolean glow = left <= 4;
        c.set(x, FLOOR, z, glow ? QUARTZ : s.floorId(), glow ? 0 : s.floorMeta());
        c.column(x, z, WALK, FLOOR + 4, AIR, 0);
        boolean lamp = glow || Math.floorMod(x, 3) == 0 && z == 0;
        c.set(x, FLOOR + 5, z, lamp ? SEA_LANTERN : s.ceilId(), lamp ? 0 : s.ceilMeta());
        if (left == 1) c.column(x, z, WALK, FLOOR + 4, STAINED_GLASS, WHITE);
    }
}
