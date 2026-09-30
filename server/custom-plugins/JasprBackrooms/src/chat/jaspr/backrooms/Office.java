package chat.jaspr.backrooms;

import static chat.jaspr.backrooms.Canvas.*;

/**
 * Level 5, the Abandoned Office: a grid of carpeted hallways three wide around 24-block sections, each section walled
 * in light grey with doorways and fitted out as cubicles, private offices behind glass, a meeting room, a break room,
 * storage, an open floor, or a dark floor where the lights are dead. A drop ceiling of white panels and sea-lantern
 * lights, more of them dead deeper in. Some hallway segments are blocked by fallen ceiling. The spine runs through
 * everything as an open runner of carpet. The arena is the Boardroom.
 */
final class Office implements Style {
    static final int S = 24, AIR_H = 4, F = Level.FLOOR, W = Level.WALK, CEIL = F + AIR_H + 1;
    enum Kind { CUBICLES, OFFICES, MEETING, BREAK, OPEN, STORAGE, DARK }

    @Override public int floorId() { return WOOL; }
    @Override public int floorMeta() { return SILVER; }
    @Override public int wallId() { return CONCRETE; }
    @Override public int wallMeta() { return SILVER; }
    @Override public int ceilId() { return CONCRETE; }
    @Override public int ceilMeta() { return WHITE; }
    @Override public int lightId() { return SEA_LANTERN; }

    static Kind kind(long seed, int i, int j) {
        double r = Hash.unit(seed, 501, i, j);
        return r < 0.36 ? Kind.CUBICLES : r < 0.54 ? Kind.OFFICES : r < 0.63 ? Kind.MEETING : r < 0.71 ? Kind.BREAK
            : r < 0.83 ? Kind.OPEN : r < 0.92 ? Kind.STORAGE : Kind.DARK;
    }

    /** Hallways run on every section boundary: three blocks wide. */
    static boolean hallway(int m) { return m <= 2; }

    /** A blocked hallway segment (fallen ceiling): the stretch of hallway between two crossings along x or z. */
    private static boolean blocked(Level lv, long seed, int x, int z) {
        if (lv.onSpine(seed, x, z, 2.5)) return false;
        int mx = Math.floorMod(x, S), mz = Math.floorMod(z, S);
        if (hallway(mx) && hallway(mz)) return false;   // crossings stay clear
        int i = Math.floorDiv(x, S), j = Math.floorDiv(z, S);
        if (hallway(mz)) return Math.abs(mx - 12) <= 1 && Hash.unit(seed, 502, i, j) < 0.18;
        return Math.abs(mz - 12) <= 1 && Hash.unit(seed, 503, i, j) < 0.18;
    }

    /** Section walls: a ring one block thick inside the hallways, with a doorway two wide in the middle of most sides. */
    private static boolean sectionWall(Level lv, long seed, int x, int z) {
        int mx = Math.floorMod(x, S), mz = Math.floorMod(z, S), i = Math.floorDiv(x, S), j = Math.floorDiv(z, S);
        boolean ring = (mx == 3 || mx == 23) && mz >= 3 || (mz == 3 || mz == 23) && mx >= 3;
        if (!ring || lv.onSpine(seed, x, z, 1.5)) return false;
        int side = mx == 3 ? 0 : mx == 23 ? 1 : mz == 3 ? 2 : 3;
        int along = side < 2 ? mz : mx;
        boolean door = (along == 12 || along == 13) && (Hash.unit(seed, 504, i * 4 + side, j) < 0.8 || side == 2);
        return !door;
    }

    @Override public boolean solid(Level lv, long seed, int x, int z) {
        int mx = Math.floorMod(x, S), mz = Math.floorMod(z, S);
        if (hallway(mx) || hallway(mz)) return blocked(lv, seed, x, z);
        return sectionWall(lv, seed, x, z) || furniture(lv, seed, x, z) != null;
    }

    // ---- furniture: {block id, meta, height} at walking level, or null -------------------------------------------------
    /** The furnishing of a section column (u, v in 0..18 inside the section walls). */
    static int[] furniture(Level lv, long seed, int x, int z) {
        int mx = Math.floorMod(x, S), mz = Math.floorMod(z, S);
        if (mx < 4 || mz < 4 || mx > 22 || mz > 22 || lv.onSpine(seed, x, z, 1.5)) return null;
        int u = mx - 4, v = mz - 4, i = Math.floorDiv(x, S), j = Math.floorDiv(z, S);
        boolean aisleU = u == 8 || u == 9 || u == 10, aisleV = v == 8 || v == 9 || v == 10;   // the section's cross aisle
        switch (kind(seed, i, j)) {
            case CUBICLES: {
                if (aisleU || aisleV) return null;
                int a = u % 6, b = v % 6;
                if (a == 0 && b != 5) return new int[] {WOOL, SILVER, 2};               // partitions
                if (b == 0 && a <= 3) return new int[] {WOOL, GRAY, 2};
                if (a == 1 && (b == 2 || b == 3)) return new int[] {WOOD_SLAB, 8, 1};   // desk (the computer sits on it)
                if (a == 2 && b == 2) return new int[] {OAK_STAIRS, 1, 1};              // chair
                return null;
            }
            case OFFICES: {
                if (aisleU || aisleV) return null;
                boolean glass = u == 7 || u == 11 || v == 7 || v == 11;
                if (glass) return (u == 7 || u == 11) && v % 11 == 3 || (v == 7 || v == 11) && u % 11 == 3 ? null : new int[] {PANE, 0, 3};
                int a = u > 10 ? u - 12 : u, b = v > 10 ? v - 12 : v;   // 0..6 inside each office
                if (a == 1 && b >= 2 && b <= 4) return new int[] {WOOD_SLAB, 13, 1};   // desk (dark oak)
                if (a == 2 && b == 3) return new int[] {OAK_STAIRS, 1, 1};
                if (a == 6 && b >= 1 && b <= 5 || b == 0 && a >= 1 && a <= 5 && Hash.unit(seed, 505, x, 0, z) < 0.5) return new int[] {BOOKSHELF, 0, 2};
                return null;
            }
            case MEETING: {
                if (u >= 5 && u <= 13 && v >= 7 && v <= 11) return new int[] {WOOD_SLAB, 9, 1};   // the table
                if (u >= 5 && u <= 13 && (v == 6 || v == 12) && u % 2 == 1) return new int[] {OAK_STAIRS, v == 6 ? 2 : 3, 1};
                return null;
            }
            case BREAK: {
                if (v == 0 && u >= 2 && u <= 16) return u == 8 ? new int[] {CAULDRON, 3, 1} : new int[] {SLAB, 15, 1};   // counter and sink
                if (u == 18 && v >= 3 && v <= 9 && v % 3 != 2) return new int[] {CONCRETE, RED, 2};                // vending machines
                if (!aisleU && !aisleV && u % 5 == 2 && v % 5 == 2) return new int[] {WOOD_SLAB, 9, 1};              // tables
                if (u == 1 && v == 17) return new int[] {CAULDRON, 3, 1};                                          // water cooler
                return null;
            }
            case STORAGE: {
                if (aisleV) return null;
                if (u % 4 == 1 && v >= 1 && v <= 17) return new int[] {BOOKSHELF, 0, 3};
                if (u % 4 == 2 && (v == 1 || v == 17)) return new int[] {DROPPER, 1, 2};
                return null;
            }
            default: {   // OPEN and DARK: a few abandoned desks and fallen chairs
                double r = Hash.unit(seed, 506, x, 0, z);
                if (r < 0.025) return new int[] {WOOD_SLAB, 8, 1};
                if (r < 0.04) return new int[] {OAK_STAIRS, Hash.range(seed, 507, x, 0, z, 4), 1};
                return null;
            }
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override public void body(Canvas c, Level lv, int x, int z) {
        long seed = c.seed;
        double d = lv.progress(x);
        int mx = Math.floorMod(x, S), mz = Math.floorMod(z, S), i = Math.floorDiv(x, S), j = Math.floorDiv(z, S);
        boolean hall = hallway(mx) || hallway(mz);
        Kind kind = hall ? null : kind(seed, i, j);
        c.set(x, F, z, WOOL, SILVER);
        c.set(x, CEIL + 1, z, CONCRETE, SILVER);
        if (hall ? blocked(lv, seed, x, z) : sectionWall(lv, seed, x, z)) {
            if (hall) {   // fallen ceiling: panels and rubble, not a clean wall
                c.column(x, z, W, CEIL, CONCRETE, WHITE);
                if (Hash.unit(seed, 508, x, 0, z) < 0.4) c.set(x, W + Hash.range(seed, 513, x, 0, z, 3), z, COBBLE);
            } else c.column(x, z, W, CEIL, CONCRETE, SILVER);
            return;
        }
        boolean runner = lv.onSpine(seed, x, z, 1.5);
        c.set(x, W, z, CARPET, runner ? BLUE : hall ? GRAY : kind == Kind.DARK ? BLACK : SILVER);
        boolean dark = kind == Kind.DARK || d > 0.35 && Hash.unit(seed, 509, x >> 4, z >> 4) < 0.3 * d;
        boolean lamp = !dark && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2 && Hash.unit(seed, 510, x, 1, z) >= 0.06 + 0.5 * d;
        c.set(x, CEIL, z, lamp ? SEA_LANTERN : CONCRETE, lamp ? 0 : WHITE);
        if (hall) return;
        int[] f = furniture(lv, seed, x, z);
        if (f != null) {
            for (int y = W; y < W + f[2]; y++) c.set(x, y, z, f[0], f[1]);
            // A computer on some desks.
            if (f[0] == WOOD_SLAB && (kind == Kind.CUBICLES || kind == Kind.OFFICES) && Hash.unit(seed, 511, x, 0, z) < 0.6) c.set(x, W + 1, z, DISPENSER, 5);
            if (f[0] == CAULDRON && Math.floorMod(x + z, 7) == 0) c.set(x, W + 1, z, GLASS);
            return;
        }
        // A projector hanging over the meeting table; loot in some offices and store rooms.
        int u = mx - 4, v = mz - 4;
        if (kind == Kind.MEETING && u == 9 && v == 9) c.set(x, CEIL - 1, z, DISPENSER, 0);
        if ((kind == Kind.OFFICES && (u == 5 || u == 17) && (v == 5 || v == 17) || kind == Kind.STORAGE && u % 4 == 3 && (v == 1 || v == 17))
            && Hash.unit(seed, 512, x, 0, z) < 0.18 + 0.2 * d) c.chest(x, W, z, v < 9 ? 3 : 2, lv, lv.danger(x, z), "desk");
    }

    // ---- the Boardroom ------------------------------------------------------------------------------------------------
    @Override public void arena(Canvas c, Level lv, int x, int z) {
        if (!Rooms.arenaShell(c, lv, this, x, z)) return;
        int air = Rooms.arenaAir(lv), ceil = F + air + 1, half = lv.arenaWidth / 2, ax = x - lv.arenaStart();
        c.set(x, F, z, WOOL, GRAY);
        boolean lamp = Math.floorMod(ax, 4) == 2 && Math.floorMod(z, 4) == 2;
        c.set(x, ceil, z, lamp ? SEA_LANTERN : CONCRETE, lamp ? 0 : WHITE);
        c.set(x, ceil + 1, z, CONCRETE, SILVER);
        // Windows to nowhere along both long walls (black behind the glass).
        if (Math.abs(z) == half - 1 && Math.floorMod(ax, 5) != 0 && ax > 2 && ax < lv.arenaLength - 3) { c.column(x, z, W + 1, W + 4, STAINED_GLASS, BLACK); c.set(x, W, z, CARPET, RED); return; }
        c.set(x, W, z, CARPET, RED);
        // The long table (a gap down the middle lane is left for the doors), its chairs.
        boolean table = ax >= 12 && ax <= lv.arenaLength - 16 && Math.abs(z) >= 3 && Math.abs(z) <= 5;
        if (table) { c.set(x, W, z, WOOD_SLAB, 13); return; }
        if (ax >= 12 && ax <= lv.arenaLength - 16 && (Math.abs(z) == 2 || Math.abs(z) == 6) && ax % 2 == 0) c.set(x, W, z, OAK_STAIRS, z > 0 ? (Math.abs(z) == 2 ? 3 : 2) : (Math.abs(z) == 2 ? 2 : 3));
    }

    @Override public int[] bossSpot(Level lv) { return new int[] {lv.arenaStart() + lv.arenaLength - 10, W, 0}; }
}
