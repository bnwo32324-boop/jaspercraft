package chat.jaspr.dungeon;

import static chat.jaspr.dungeon.FloorTwoThemes.*;

/**
 * Generation 7, Floor II (The Underworks): the caverns' blocks. Pure: a block depends on the room and its coordinates alone
 * (no world reads, no random state, no clocks), so chunk order never matters.
 *
 * Every column (x, z) is worked out once into a profile (floor, ceiling height, stacked segments, its peril) and each y of
 * the column is answered from it. DungeonGenerator asks for a column's 45 blocks one after another, so a per-thread memo of
 * the last column makes them cost one profile; the memo is keyed by every room field the profile reads and the column, so
 * it never changes a result (FloorTwoAuditTest checks shuffled and interleaved orders).
 *
 * A room is carved rock between bedrock at y 56 and y 100: walls two thick with the House of Mercy's barred doorways, the
 * clear lanes (cells 13..19 of every tile and the centre cross) walkable at y 64 under a rough ceiling no lower than the
 * room's roof, and between the lanes the bays, where the cavern lives: wall bulges and overhangs, a set piece per bay cell
 * (the theme's outcrops, pillars, pools, mushrooms, crystals, geodes, timbering, rails ...), scattered stubs, lanterns at
 * the lane edges and hanging from the roof, and the physical perils (PerilMarks). Obstacles never touch one another unless
 * they are one simply connected shape, so no open floor is ever walled off.
 */
final class FloorTwoCaves {
    private FloorTwoCaves() {}
    static final int BOTTOM = 56, TOP = 100, FLOOR = 64, MAX = 10;
    static final int WALL = 1, LANE = 2, BAY = 3;
    /** Segment block standing for the column's own stone at that height (its strata). */
    static final int ROCK = -1;
    static final int BEDROCK = 7, BARS = 101, CHEST = b(54, 2), MAGMA = 213, LAVA = 11, WATER = 9, CRACKED = b(98, 2),
        WEB_BLOCK = 30, RAIL = 66, GRAVEL = 13, POD = b(100, 14), SOUL_SAND = 88, CHAIN = 101, CLAY = 82, OBSIDIAN = 49;
    static final int P_STALACTITE = PerilMarks.Kind.STALACTITE.ordinal() + 1, P_LAVA = PerilMarks.Kind.LAVA.ordinal() + 1,
        P_CRUMBLE = PerilMarks.Kind.CRUMBLE.ordinal() + 1, P_GEYSER = PerilMarks.Kind.GEYSER.ordinal() + 1,
        P_FALL = PerilMarks.Kind.LAVA_FALL.ordinal() + 1;
    private static final long K1 = 0x9e3779b97f4a7c15L, K2 = 0xc2b2ae3d27d4eb4fL;
    private static final long S_WAVE = 0x7761766573L, S_BUMP = 0x62756d70L, S_PATCH = 0x7061746368L, S_HANG = 0x68616e67L,
        S_BULGE = 0x62756c6765L, S_CELL = 0x63656c6cL, S_STAL = 0x7374616cL, S_VENT = 0x76656e74L, S_CRUMBLE = 0x6372756dL,
        S_SCATTER = 0x73636174L, S_CEIL = 0x6365696cL, S_LANTERN = 0x6c616e74L, S_SHIFT = 0x7368696674L, S_RIVER = 0x7269766572L,
        S_CRATE = 0x6372617465L, S_WEB = 0x776562L, S_QUARRY = 0x7175617272L, S_STONE = 0x73746f6e65L, S_DOME = 0x646f6d65L,
        S_SPLIT = 0x73706c6974L;

    /** The profile of one column, and the memo key it was built for. */
    static final class Col {
        boolean valid; int kx, kz;
        int rx0, rz0, rw, rd, theme = Integer.MIN_VALUE, motif, tier, kind; long hash;
        Look look; int roof, shift, stal, vent, crumble, dome, hang; boolean darts, spores, riverX; double riverAmp, riverWave, riverPhase;
        int type, floor, sub, deep, ceil, ceilMark, wave, peril, tip, piece;
        boolean opening, door, face, faceLamp, free, ground, noStal, crumbleHere, plainFloor;
        int n; final int[] lo = new int[MAX], hi = new int[MAX], blk = new int[MAX];
        int inlays; final int[] iy = new int[4], ib = new int[4];
        boolean same(Layout.Room r) {
            return theme == r.theme && rx0 == r.x && rz0 == r.z && rw == r.w && rd == r.d && motif == r.motif && tier == r.tier
                && kind == r.kind.ordinal() && hash == r.hash;
        }
        /** A run of blocks between the floor and the ceiling; the first segment covering a height wins. */
        void add(int a, int b, int block) {
            if (a < FLOOR + 1) a = FLOOR + 1;
            if (b > ceil - 1) b = ceil - 1;
            if (a > b || n >= MAX) return;
            lo[n] = a; hi[n] = b; blk[n] = block; n++;
        }
        /** A block set into stone (or into a segment) at height y; inlays win over everything above the floor. */
        void inlay(int y, int block) { if (inlays < iy.length) { iy[inlays] = y; ib[inlays] = block; inlays++; } }
        void solid() { ceil = FLOOR + 1; n = 0; }
    }
    private static final ThreadLocal<Col> MEMO = ThreadLocal.withInitial(Col::new);

    // ---------------------------------------------------------------- entry points
    static int block(Layout.Room r, int x, int y, int z) {
        if (y < BOTTOM || y > TOP) return 0;
        if (y == BOTTOM || y == TOP) return BEDROCK;
        Col c = column(r, x, z);
        if (c.type == WALL) return wall(c, r, x, y, z);
        if (y < FLOOR - 2) return rock(c, y);
        if (y == FLOOR - 2) return c.deep != 0 ? c.deep : rock(c, y);
        if (y == FLOOR - 1) return c.sub;
        if (y == FLOOR) return c.floor;
        for (int i = 0; i < c.inlays; i++) if (c.iy[i] == y) return c.ib[i];
        if (y >= c.ceil) return y == c.ceil && c.ceilMark != 0 ? c.ceilMark : rock(c, y);
        for (int i = 0; i < c.n; i++) if (y >= c.lo[i] && y <= c.hi[i]) { int s = c.blk[i]; return s == ROCK ? rock(c, y) : s; }
        return 0;
    }
    /** The peril drawn at (x, z) as PerilMarks.Kind ordinal + 1, or 0. */
    static int peril(Layout.Room r, int x, int z) { Col c = column(r, x, z); return c.type == BAY ? c.peril : 0; }
    /** The lowest block of the stalactite hanging over (x, z), or -1 where there is none. */
    static int tip(Layout.Room r, int x, int z) { Col c = column(r, x, z); return c.type == BAY && c.peril == P_STALACTITE ? c.tip : -1; }
    /** Diagnostics for the audit: the set piece of a bay column (FloorTwoThemes ids), or a negative code. */
    static int piece(Layout.Room r, int x, int z) { Col c = column(r, x, z); return c.type == BAY ? c.piece : -10 - c.type; }

    static Col column(Layout.Room r, int x, int z) {
        Col c = MEMO.get();
        if (c.valid && c.kx == x && c.kz == z && c.same(r)) return c;
        if (!c.same(r)) room(c, r);
        build(c, r, x, z);
        return c;
    }
    private static int rock(Col c, int y) { return c.look.strata[(y + c.wave + c.shift) & 63]; }
    private static int wall(Col c, Layout.Room r, int x, int y, int z) {
        if (y < FLOOR) return rock(c, y);
        if (y == FLOOR) return c.opening ? c.floor : rock(c, y);
        // Exactly the House of Mercy's doorways: bars on the outer cell, open inner cell, a lamp over both. DungeonPlugin.walk does the rest.
        if (c.opening && y <= 69) return y == 69 ? GLOWSTONE : c.door ? BARS : 0;
        if (c.face && y >= 66 && y <= 68) {
            int mark = HazardCatalog.wall(r, x, y, z);
            if (mark != 0) return mark;
            if (y == 68 && c.faceLamp) return c.look.solidLight();
        }
        return rock(c, y);
    }

    // ---------------------------------------------------------------- the room and the column
    private static void room(Col c, Layout.Room r) {
        c.valid = false;
        c.rx0 = r.x; c.rz0 = r.z; c.rw = r.w; c.rd = r.d; c.theme = r.theme; c.motif = r.motif; c.tier = r.tier; c.kind = r.kind.ordinal(); c.hash = r.hash;
        Look L = c.look = FloorTwoThemes.look(r.theme);
        c.roof = r.roof();
        c.shift = (int) (Layout.mix(r.hash ^ S_SHIFT) & 63);
        c.darts = HazardCatalog.has(r, HazardCatalog.Type.DART_SLITS);
        c.spores = HazardCatalog.has(r, HazardCatalog.Type.BLAST_SPORES);
        // Owner 2026-10-05: "each tier progressively gets harder" -- gauntlets and the Descent are thick with perils, deeper rooms more.
        double kind = r.kind == Layout.Kind.GAUNTLET ? 1.5 : r.kind == Layout.Kind.DESCENT ? 1.3 : r.kind == Layout.Kind.BOSS ? 1.1 : r.dormant() ? .8 : 1;
        double threat = .8 + .06 * r.tier;
        // "many in the tall caverns": hanging points grow with the roof.
        double tall = c.roof >= 95 ? 1.6 : c.roof >= 92 ? 1.4 : c.roof >= 88 ? 1.2 : c.roof >= 84 ? .9 : .6;
        c.stal = (int) Math.min(950, L.stalactites * kind * threat * tall);
        c.vent = (int) Math.min(900, L.geysers * kind * threat);
        c.crumble = (int) Math.min(900, L.crumbles * kind * threat);
        c.dome = 2 + (c.roof - 79) / 3;
        c.hang = 2 + (c.roof - 79) / 2;
        c.riverX = r.w >= r.d;
        int span = c.riverX ? r.d : r.w;
        c.riverAmp = Math.min(span / 2.0 - 7, 9);
        c.riverWave = 36 + (Layout.mix(r.hash ^ S_RIVER) & 31);
        c.riverPhase = unit(r.hash ^ S_RIVER, 1) * Math.PI * 2;
    }
    private static void build(Col c, Layout.Room r, int x, int z) {
        c.valid = false; c.kx = x; c.kz = z;
        Look L = c.look;
        c.n = 0; c.inlays = 0; c.peril = 0; c.tip = -1; c.deep = 0; c.ceilMark = 0; c.piece = -1;
        c.opening = c.door = c.face = c.faceLamp = false; c.free = c.ground = true; c.noStal = c.crumbleHere = false; c.plainFloor = false;
        int rx = x - r.x, rz = z - r.z, w = r.w, d = r.d;
        c.wave = (int) (noise(r.hash ^ S_WAVE, rx, rz, 11) * 6);
        c.sub = L.sub;
        boolean wx = rx < 2 || rx >= w - 2, wz = rz < 2 || rz >= d - 2;
        if (wx || wz) {
            c.type = WALL;
            c.opening = Layout.Room.lane(z) && wx || Layout.Room.lane(x) && wz;
            c.door = r.door(x, z);
            int along = HazardCatalog.along(r, x, z);
            c.face = along >= 0;
            // Lamps in the wall beside every doorway, and beside the mouths of the centre cross, which has no doorway in a room an even number of tiles wide.
            int mid = rx < 2 || rx >= w - 2 ? d / 2 : w / 2;
            c.faceLamp = c.face && ((along & 31) == 13 || (along & 31) == 19 || Math.abs(along - mid) == 3);
            c.floor = caveFloor(r, L, rx, rz, true);
            c.ceil = FLOOR + 1;
        } else {
            // The ceiling: rough everywhere, and vaulted where a low room has the height to spare (never below the roof over a lane).
            int bump = (int) (noise(r.hash ^ S_BUMP, rx, rz, 6) * 4), vault = Math.max(0, Math.min(10, 95 - c.roof));
            if (vault > 0) bump += (int) (Math.max(0, noise(r.hash ^ S_DOME, rx, rz, 16) - .3) / .7 * vault);
            if (r.clearLane(x, z)) lane(c, r, L, x, z, rx, rz, bump); else bay(c, r, L, x, z, rx, rz, bump);
        }
        c.valid = true;
    }
    /** A clear lane: walkable lane-safe floor, nothing between the floor and a ceiling at or above the roof, the reliquary chest. */
    private static void lane(Col c, Layout.Room r, Look L, int x, int z, int rx, int rz, int bump) {
        c.type = LANE;
        c.floor = caveFloor(r, L, rx, rz, true);
        int mark = HazardCatalog.floor(r, x, z);
        if (mark != 0) c.floor = mark;
        c.ceil = Math.min(TOP - 2, c.roof + bump);
        // The underground river runs on under every lane, a culvert beneath the lane's bridge of floor.
        if (L.river && river(c, r, rx, rz) <= 2.5) { c.sub = WATER; c.deep = GRAVEL; }
        if (x == r.cx() && z == r.cz() + 4) c.add(FLOOR + 1, FLOOR + 1, CHEST);
        ceiling(c, r, L, x, z, rx, rz, true);
    }

    /** One cave floor across lanes and bays alike: the theme's floor, patches of its second floor and of its bay stone. */
    private static int caveFloor(Layout.Room r, Look L, int rx, int rz, boolean lane) {
        if (noise(r.hash ^ S_STONE, rx, rz, 9) > .64 && (!lane || footing(L.bay))) return L.bay;
        return noise(r.hash ^ S_PATCH, rx, rz, 5) > .62 ? L.patch : L.floor;
    }

    // ---------------------------------------------------------------- the bays
    /** First bay cell of the run of non-lane cells holding n (room-relative), on an axis of this span. */
    static int bayStart(int n, int span) {
        int s = 2, k = Math.floorDiv(n - 20, 32), c = span / 2 + 3;
        if (k >= 0) s = Math.max(s, 32 * k + 20);
        if (c < n) s = Math.max(s, c + 1);
        return s;
    }
    static int bayEnd(int n, int span) {
        int e = Math.min(span - 3, 32 * (Math.floorDiv(n - 13, 32) + 1) + 12), c = span / 2 - 3;
        if (c > n) e = Math.min(e, c - 1);
        return e;
    }
    private static void bay(Col c, Layout.Room r, Look L, int x, int z, int rx, int rz, int bump) {
        c.type = BAY;
        int w = r.w, d = r.d;
        int bx0 = bayStart(rx, w), bx1 = bayEnd(rx, w), bz0 = bayStart(rz, d), bz1 = bayEnd(rz, d);
        c.floor = caveFloor(r, L, rx, rz, false);
        c.plainFloor = true;
        // Rough walls: a bulge of rock (0..2 cells) along the wall this bay touches, an overhanging lip, and a roof that curves
        // down to it. Bulges grow from one wall at a time (always thinner further out, so they never close a cell in); where two
        // walls meet, a plain fillet rounds the corner instead.
        int dW = bx0 == 2 ? rx - 2 : 99, dE = bx1 == w - 3 ? w - 3 - rx : 99, dN = bz0 == 2 ? rz - 2 : 99, dS = bz1 == d - 3 ? d - 3 - rz : 99;
        int depthX = Math.min(dW, dE), depthZ = Math.min(dN, dS), dome = 0;
        boolean bulge, lip = false;
        if (depthX < 4 && depthZ < 4) {
            bulge = depthX + depthZ < 2;
            dome = c.dome - 2 * Math.min(depthX, depthZ);
        } else {
            int depth = Math.min(depthX, depthZ), t = depth >= 99 ? 0 : depthX < depthZ ? thickness(c, r, dW <= dE ? 0 : 1, rz) : thickness(c, r, dN <= dS ? 2 : 3, rx);
            bulge = depth < t;
            lip = depth == t && t > 0;
            if (depth < 99) dome = c.dome - 2 * (depth - t);
        }
        int hang = (int) Math.round(Math.max(0, noise(r.hash ^ S_HANG, rx, rz, 6) - .45) / .55 * c.hang);
        c.ceil = Math.max(70, c.roof + bump - Math.max(hang, dome));
        if (lip) c.ceil = Math.min(c.ceil, 72);
        // Lanterns line the lanes: every eight cells on each bay edge that faces a lane, staggered across the lane. A lantern
        // stands even where the wall bulges, so a lane beside a wall is never left dark.
        boolean lamp = lampSpot(rx, rz, bx0, bx1, bz0, bz1, w, d);
        if (bulge && !lamp) { c.solid(); c.piece = -2; return; }
        // The river: water one block below the floor, under the open air of the bays.
        double river = L.river ? river(c, r, rx, rz) : 99;
        // A lantern on the river's line stands on a rock in the stream.
        if (river <= 2.5 && !lamp) { c.floor = 0; c.sub = WATER; c.deep = GRAVEL; c.plainFloor = false; c.piece = -3; ceiling(c, r, L, x, z, rx, rz, false); return; }
        if (river <= 3.6) { c.floor = CLAY; c.plainFloor = false; }
        // Cells the room's traps fire from or burst at stay open: the dart slits' fronts, the spore pods and the cell before them.
        boolean front = c.darts && (rx == 2 && HazardCatalog.slit(r, x - 1, z) || rx == w - 3 && HazardCatalog.slit(r, x + 1, z)
            || rz == 2 && HazardCatalog.slit(r, x, z - 1) || rz == d - 3 && HazardCatalog.slit(r, x, z + 1));
        boolean pod = c.spores && HazardCatalog.pod(r, x, z);
        boolean burst = c.spores && (rx == 3 && HazardCatalog.pod(r, x - 1, z) || rz == 3 && HazardCatalog.pod(r, x, z - 1));
        if (front || pod || burst) {
            if (pod) c.add(FLOOR + 1, FLOOR + 1, POD);
            marks(c, r, x, z);
            c.piece = -4;
            ceiling(c, r, L, x, z, rx, rz, false);
            return;
        }
        if (lamp) { lamp(c, L); c.piece = -5; ceiling(c, r, L, x, z, rx, rz, false); return; }
        // The bay cell: a run of 20 or more is usually halved, so a great bay holds two or four set pieces, or one great one.
        long split = Layout.mix(r.hash ^ (long) bx0 * K1 ^ (long) bz0 * K2 ^ S_SPLIT);
        int sx0 = bx0, sx1 = bx1, sz0 = bz0, sz1 = bz1;
        if (bx1 - bx0 >= 19 && pick(split, 0, 3) > 0) { int mid = bx0 + (bx1 - bx0 + 1) / 2; if (rx < mid) sx1 = mid - 1; else sx0 = mid; }
        if (bz1 - bz0 >= 19 && pick(split, 1, 3) > 0) { int mid = bz0 + (bz1 - bz0 + 1) / 2; if (rz < mid) sz1 = mid - 1; else sz0 = mid; }
        int sw = sx1 - sx0 + 1, sd = sz1 - sz0 + 1, px = rx - sx0, pz = rz - sz0;
        // Set pieces keep one cell from lanes and neighbouring cells and four from a wall (clear of any bulge).
        int ux0 = sx0 == 2 ? 4 : 1, ux1 = sw - 1 - (sx1 == w - 3 ? 4 : 1), uz0 = sz0 == 2 ? 4 : 1, uz1 = sd - 1 - (sz1 == d - 3 ? 4 : 1);
        long sh = Layout.mix(r.hash ^ (long) sx0 * K1 ^ (long) sz0 * K2 ^ S_CELL);
        // Each piece takes a part of that rectangle of its own size and place, so no two cells look alike.
        int cutX = ux1 - ux0 > 5 ? pick(sh, 61, (ux1 - ux0 - 4) * 2 / 3 + 1) : 0, cutZ = uz1 - uz0 > 5 ? pick(sh, 62, (uz1 - uz0 - 4) * 2 / 3 + 1) : 0;
        int fx0 = ux0 + pick(sh, 63, cutX + 1), fz0 = uz0 + pick(sh, 64, cutZ + 1), fx1 = fx0 + (ux1 - ux0 - cutX), fz1 = fz0 + (uz1 - uz0 - cutZ);
        int piece = river <= 4.6 ? OPEN : L.piece(sh >>> 8), ceil = c.ceil;
        c.piece = piece;
        switch (piece) {
            case OUTCROP: outcrop(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case PILLARS: pillars(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case STALAGMITES: stalagmites(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case POOL: pool(c, sh, px, pz, fx0, fx1, fz0, fz1, L.liquid == LAVA ? LAVA : WATER, L.liquid == LAVA ? MAGMA : CLAY, L.rim); break;
            case GLOWPOOL: pool(c, sh, px, pz, fx0, fx1, fz0, fz1, WATER, (colHash(sh, px, pz) & 1) != 0 ? SEA_LANTERN : CLAY, L.rim); break;
            case MUSHROOM: mushroom(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case CRYSTALS: crystals(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case GEODE: geode(c, r, L, sh, px, pz, fx0, fx1, fz0, fz1, sx0, sz0); break;
            case TIMBERS: timbers(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case CRATES: crates(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case BONES: bones(c, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case WEBS: webs(c, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case ROOTS: roots(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case MAZE: maze(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case FORGE: forge(c, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case TERRACES: terraces(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case SPRINGS: springs(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case RAILS: rails(c, L, sh, px, pz, fx0, fx1, fz0, fz1, sw, sd); break;
            case COLLAPSE: collapse(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            case FALLS: falls(c, L, px, pz, fx0, fx1, fz0, fz1); break;
            case CHANNEL: channel(c, L, sh, px, pz, fx0, fx1, fz0, fz1, sw, sd, sx0 == 2 ? ux0 : 0, sx1 == w - 3 ? ux1 : sw - 1, sz0 == 2 ? uz0 : 0, sz1 == d - 3 ? uz1 : sd - 1); break;
            case QUARRY: quarry(c, L, sh, px, pz, fx0, fx1, fz0, fz1); break;
            default: break;
        }
        if (river <= 4.6) c.ground = false;
        // Nothing stands beside a lantern (a lava fall's glass excepted: falls sit two cells in), so a lantern never seals a gap.
        if (piece != FALLS && (c.ceil <= FLOOR + 2 || c.n > 0 && c.lo[0] <= FLOOR + 2) && lampNear(rx, rz, bx0, bx1, bz0, bz1, w, d)) {
            c.n = 0; c.inlays = 0; c.ceil = ceil; c.free = false;
        }
        boolean inside = in(px, pz, ux0, ux1, uz0, uz1);
        // Perils (PerilMarks) and the scattered stubs, on open floor only.
        if (open(c) && !c.noStal) stalactite(c, r, L, rx, rz);
        if (open(c) && (c.ground || c.crumbleHere)) ground(c, r, rx, rz);
        if (open(c) && c.free && inside) scatter(c, r, L, rx, rz);
        marks(c, r, x, z);
        if (c.n == 0 && c.ceil > FLOOR + 1) lantern(c, r, L, rx, rz);
        ceiling(c, r, L, x, z, rx, rz, false);
    }
    /** A lantern's place: every eight cells along each bay edge that faces a lane, staggered across the lane. */
    static boolean lampSpot(int rx, int rz, int bx0, int bx1, int bz0, int bz1, int w, int d) {
        return rx == bx0 && bx0 > 2 && Math.floorMod(rz, 8) == 4 || rx == bx1 && bx1 < w - 3 && Math.floorMod(rz, 8) == 0
            || rz == bz0 && bz0 > 2 && Math.floorMod(rx, 8) == 4 || rz == bz1 && bz1 < d - 3 && Math.floorMod(rx, 8) == 0;
    }
    private static boolean lampNear(int rx, int rz, int bx0, int bx1, int bz0, int bz1, int w, int d) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int nx = rx + dx, nz = rz + dz;
            if ((dx != 0 || dz != 0) && nx >= bx0 && nx <= bx1 && nz >= bz0 && nz <= bz1 && lampSpot(nx, nz, bx0, bx1, bz0, bz1, w, d)) return true;
        }
        return false;
    }
    private static boolean in(int px, int pz, int ux0, int ux1, int uz0, int uz1) { return px >= ux0 && px <= ux1 && pz >= uz0 && pz <= uz1; }
    /** Open floor: nothing standing on it, no peril yet, solid footing and headroom to fall from. */
    private static boolean open(Col c) { return c.n == 0 && c.peril == 0 && c.ceil >= FLOOR + 6 && c.sub != 0 && footing(c.floor); }
    static boolean footing(int f) {
        int id = f & 4095;
        return id != 0 && id != 9 && id != 11 && id != 213 && id != 88 && id != 89 && id != 169 && id != 95 && id != 20;
    }
    /** The room's trap marks (HazardCatalog) on plain floor, as the House of Mercy draws them. */
    private static void marks(Col c, Layout.Room r, int x, int z) {
        if (!c.plainFloor || c.peril != 0 && c.peril != P_STALACTITE) return;
        int mark = HazardCatalog.floor(r, x, z);
        if (mark != 0) c.floor = mark;
    }
    /** Bulge thickness 0..2 along one wall; never where the room's dart slits shoot or its spore pods sit. */
    private static int thickness(Col c, Layout.Room r, int side, int along) {
        int m = along & 31;
        if (c.darts && (m <= 4 || m >= 28)) return 0;
        if (c.spores && side != 1 && side != 3 && (m >= 5 && m <= 9 || m >= 23 && m <= 27)) return 0;
        return (int) (noise1(r.hash ^ S_BULGE ^ side * K2, along, 5) * 3);
    }
    private static double river(Col c, Layout.Room r, int rx, int rz) {
        int a = c.riverX ? rx : rz, b = c.riverX ? rz : rx, span = c.riverX ? r.d : r.w;
        double centre = span / 2.0 + c.riverAmp * Math.sin(a * 2 * Math.PI / c.riverWave + c.riverPhase);
        return Math.abs(b + .5 - centre);
    }
    private static void lamp(Col c, Look L) {
        switch (L.lamp) {
            case POST: c.add(65, 66, L.fence); c.add(67, 67, L.light); break;
            case CRYSTAL: c.add(65, 65, L.lampBase); c.add(66, 66, L.solidLight()); c.add(67, 67, L.glass); c.add(68, 68, L.glass2); break;
            case BRAZIER: c.add(65, 65, L.lampBase); c.add(66, 66, L.solidLight()); c.add(67, 67, 113); break;
            case SHROOM: c.add(65, 66, b(99, 10)); c.add(67, 67, L.solidLight()); c.add(68, 68, b(100, 14)); break;
            default:
                c.add(65, 65, L.lampBase);
                if (L.light == TORCH) c.add(66, 66, TORCH);
                else { c.add(66, 66, L.light); c.add(67, 67, L.lampBase); }
        }
    }
    /** Ceiling lamps set into the rock (glowworms in the Glowworm Caves, with threads), or the room's ceiling trap marks. */
    private static void ceiling(Col c, Layout.Room r, Look L, int x, int z, int rx, int rz, boolean lane) {
        if (c.ceil <= FLOOR + 1) return;
        int mark = HazardCatalog.roof(r, x, z);
        if (mark != 0) { c.ceilMark = mark; return; }
        int gx = Math.floorDiv(rx, 7), gz = Math.floorDiv(rz, 7);
        long h = Layout.mix(r.hash ^ S_CEIL ^ gx * K1 ^ gz * K2);
        if (pick(h, 0, 1000) >= L.ceilingLights || rx != gx * 7 + 1 + pick(h, 1, 5) || rz != gz * 7 + 1 + pick(h, 2, 5)) return;
        c.ceilMark = L.solidLight();
        if (L.threads > 0 && pick(h, 3, 1000) < L.threads && c.n == 0 && (!lane || c.ceil - 1 >= c.roof)) c.add(c.ceil - 1, c.ceil - 1, b(198, 0));
    }
    /** A lantern on a chain over open bay floor, low enough to light it. */
    private static void lantern(Col c, Layout.Room r, Look L, int rx, int rz) {
        if (c.ceil - FLOOR < 12) return;
        int gx = Math.floorDiv(rx, 11), gz = Math.floorDiv(rz, 11);
        long h = Layout.mix(r.hash ^ S_LANTERN ^ gx * K1 ^ gz * K2);
        if (pick(h, 0, 1000) >= L.lanterns || rx != gx * 11 + 3 + pick(h, 1, 5) || rz != gz * 11 + 3 + pick(h, 2, 5)) return;
        int y = Math.max(FLOOR + 7, Math.min(c.ceil - 3, FLOOR + 10));
        c.add(y, y, L.solidLight());
        c.add(y + 1, c.ceil - 1, L.light == JACK || L.light == TORCH ? L.fence : CHAIN);
    }

    // ---------------------------------------------------------------- perils and scatter
    /** STALACTITE: a hanging point (a thin tip under stone) over open floor; at most one in every 5 x 5 cells, never touching. */
    private static void stalactite(Col c, Layout.Room r, Look L, int rx, int rz) {
        if (c.stal <= 0) return;
        int gx = Math.floorDiv(rx, 5), gz = Math.floorDiv(rz, 5);
        long h = Layout.mix(r.hash ^ S_STAL ^ gx * K1 ^ gz * K2);
        if (pick(h, 0, 1000) >= c.stal || rx != gx * 5 + 1 + pick(h, 1, 3) || rz != gz * 5 + 1 + pick(h, 2, 3)) return;
        int tip = c.ceil - 2 - pick(h, 3, c.roof >= 88 ? 4 : 2);
        if (tip < 71) return;
        c.peril = P_STALACTITE;
        c.tip = tip;
        c.add(tip, tip, L.tip);
        c.add(tip + 1, c.ceil - 1, ROCK);
    }
    /** GEYSER vents (a magma block in the floor, one in every 9 x 9 cells) and CRUMBLE patches (cracked bricks over a magma pit). */
    private static void ground(Col c, Layout.Room r, int rx, int rz) {
        if (c.ground && c.vent > 0) {
            int gx = Math.floorDiv(rx, 9), gz = Math.floorDiv(rz, 9);
            long h = Layout.mix(r.hash ^ S_VENT ^ gx * K1 ^ gz * K2);
            if (pick(h, 0, 1000) < c.vent && rx == gx * 9 + 2 + pick(h, 1, 5) && rz == gz * 9 + 2 + pick(h, 2, 5)) {
                c.peril = P_GEYSER; c.floor = MAGMA; c.plainFloor = false; return;
            }
        }
        boolean crack = c.crumbleHere;
        if (!crack && c.ground && c.crumble > 0) {
            int gx = Math.floorDiv(rx, 10), gz = Math.floorDiv(rz, 10);
            long h = Layout.mix(r.hash ^ S_CRUMBLE ^ gx * K1 ^ gz * K2);
            if (pick(h, 0, 1000) < c.crumble) {
                double cx = gx * 10 + 3 + pick(h, 1, 4) + .5, cz = gz * 10 + 3 + pick(h, 2, 4) + .5, radius = 1 + unit(h, 3) * 1.3;
                crack = Math.hypot(rx + .5 - cx, rz + .5 - cz) <= radius;
            }
        }
        if (crack) { c.peril = P_CRUMBLE; c.floor = CRACKED; c.sub = 0; c.deep = MAGMA; c.plainFloor = false; }
    }
    private static void scatter(Col c, Layout.Room r, Look L, int rx, int rz) {
        if (L.scatter <= 0) return;
        int gx = Math.floorDiv(rx, 4), gz = Math.floorDiv(rz, 4);
        long h = Layout.mix(r.hash ^ S_SCATTER ^ gx * K1 ^ gz * K2);
        if (pick(h, 0, 1000) >= L.scatter || rx != gx * 4 + 1 + pick(h, 1, 2) || rz != gz * 4 + 1 + pick(h, 2, 2)) return;
        int size = pick(h, 3, 3);
        switch (L.scatterKind) {
            case BOULDER: c.add(65, 65 + (size == 2 ? 1 : 0), ROCK); break;
            case BONE: c.add(65, 65, b(216, 4 * size)); break;
            case SHARD: if (size == 0) { c.add(65, 65, L.solidLight()); c.add(66, 66, L.glass); } else c.add(65, 64 + size, size == 1 ? L.glass : L.glass2); break;
            case CRATE: c.add(65, 65, size == 0 ? 170 : L.wood); break;
            case STUMP: c.add(65, 65 + (size == 2 ? 1 : 0), logAxis(L.log, 0)); break;
            case WEB: c.add(65, 65, WEB_BLOCK); break;
            default: if (size == 0) c.add(65, 65, ROCK); else { c.add(65, 64 + size, ROCK); c.add(65 + size, 65 + size, L.tip); }
        }
    }

    // ---------------------------------------------------------------- set pieces (px, pz: within the bay cell; u..: its usable rectangle)
    private static void outcrop(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        double cx = (ux0 + ux1) / 2.0 + unit(sh, 1) - .5, cz = (uz0 + uz1) / 2.0 + unit(sh, 2) - .5;
        double radius = Math.max(1.5, Math.min(6.5, Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) / 2.0 - .4));
        double dx = px - cx, dz = pz - cz, dist = Math.sqrt(dx * dx + dz * dz);
        c.free = dist > radius * 1.35 + 2;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double e = edge(sh, dx, dz, radius, .35);
        if (dist > e) return;
        c.ground = false;
        long ch = colHash(sh, px, pz);
        if (c.roof >= 84 && (sh & 0x100) != 0) {
            // A great mass into the roof, with light in its face where the theme has it.
            c.solid();
            if (L.veins > 0 && dist > e - 1.3 && pick(ch, 0, 1000) < L.veins) c.inlay(66 + pick(ch, 1, 4), L.solidLight());
            return;
        }
        int top = Math.min(FLOOR + Math.max(1, (int) Math.round((1 - dist / e) * (3 + pick(sh, 3, 5)) + .5)), c.ceil - 3);
        c.add(FLOOR + 1, top, ROCK);
        if (dist < .8 && (sh & 0x200) != 0) c.inlay(top, L.solidLight());
    }
    private static void pillars(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        int uw = ux1 - ux0 + 1, ud = uz1 - uz0 + 1;
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0;
        // Several pillars only where their flared feet stay two cells apart (they would otherwise close a cell in).
        int n = Math.min(uw, ud) >= 12 ? 1 + pick(sh, 1, 3) : Math.max(uw, ud) >= 12 ? 1 + pick(sh, 1, 2) : 1;
        boolean alongX = uw >= ud;
        double best = 1e9;
        for (int i = 0; i < n; i++) {
            double qx = cx, qz = cz;
            if (n == 2) { double off = (alongX ? uw : ud) / 4.0 * (i == 0 ? -1 : 1); if (alongX) qx += off; else qz += off; }
            else if (n == 3) { if (i < 2) { qx += uw / 4.0 * (i == 0 ? -1 : 1); qz -= ud / 4.0; } else qz += ud / 4.0; }
            double ri = n == 1 ? .9 + .35 * pick(sh, 10 + i, 3) : .9;
            best = Math.min(best, Math.hypot(px - qx, pz - qz) - ri);
        }
        c.free = best > 3;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        long ch = colHash(sh, px, pz);
        if (best <= 0) {
            c.solid();
            if (L.veins > 0 && best > -1 && pick(ch, 0, 1000) < L.veins) c.inlay(67 + pick(ch, 1, 6), L.solidLight());
            return;
        }
        if (best <= 1) {
            // The column flares into the floor and into the roof.
            c.ground = false;
            c.ceil = Math.max(FLOOR + 6, c.ceil - 2);
            c.add(FLOOR + 1, FLOOR + 1 + pick(ch, 2, 2), ROCK);
        }
    }
    private static void stalagmites(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int mx = Math.floorDiv(px - ux0, 4), mz = Math.floorDiv(pz - uz0, 4);
        long h = Layout.mix(sh ^ mx * K1 ^ mz * K2);
        if (pick(h, 0, 10) >= 7) return;
        int qx = ux0 + 4 * mx + 1 + pick(h, 1, 2), qz = uz0 + 4 * mz + 1 + pick(h, 2, 2);
        if (px != qx || pz != qz) return;
        int top = Math.min(FLOOR + 2 + pick(h, 3, 4) + (c.roof >= 88 ? 2 : 0), c.ceil - 3);
        c.ground = false;
        c.add(FLOOR + 1, top - 1, ROCK);
        c.add(top, top, L.tip);
    }
    private static void pool(Col c, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1, int liquid, int bed, int rim) {
        c.ground = false; c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double cx = (ux0 + ux1) / 2.0 + (unit(sh, 4) - .5) * .8, cz = (uz0 + uz1) / 2.0 + (unit(sh, 5) - .5) * .8;
        double radius = Math.max(1.2, Math.min(7, Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) / 2.0 - 1.1));
        double dx = px - cx, dz = pz - cz, dist = Math.hypot(dx, dz), e = edge(sh, dx, dz, radius, .3);
        if (dist <= e) {
            c.floor = liquid; c.sub = bed; c.plainFloor = false;
            if (liquid == LAVA) c.peril = P_LAVA;
        } else if (dist <= e + 1.25) { c.floor = rim; c.plainFloor = false; }
    }
    private static void mushroom(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        int uw = ux1 - ux0 + 1, ud = uz1 - uz0 + 1;
        boolean two = Math.max(uw, ud) >= 9, alongX = uw >= ud;
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0, near = 1e9;
        int stemX = 0, stemZ = 0, which = 0;
        for (int i = 0; i < (two ? 2 : 1); i++) {
            double qx = cx, qz = cz;
            if (two) { double off = (alongX ? uw : ud) / 4.0 * (i == 0 ? -1 : 1); if (alongX) qx += off; else qz += off; }
            int ix = (int) Math.floor(qx), iz = (int) Math.floor(qz);
            double dd = Math.hypot(px - ix, pz - iz);
            if (dd < near) { near = dd; stemX = ix; stemZ = iz; which = i; }
        }
        c.free = near > 4.2;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int capY = Math.min(FLOOR + 4 + pick(sh, 20 + which, 3) + (c.roof >= 88 ? 2 : 0), c.ceil - 3);
        if (capY < FLOOR + 4) return;
        double rc = 2.5 + .5 * pick(sh, 30 + which, 3);
        int cap = (sh & 0x400) != 0 ? b(100, 14) : b(99, 14);
        if (px == stemX && pz == stemZ) { c.ground = false; c.add(FLOOR + 1, capY, b(99, 10)); c.add(capY + 1, capY + 1, cap); return; }
        if (near > rc) return;
        if (near <= 1.01) c.add(capY - 1, capY - 1, L.solidLight());   // glowing gills beside the stem
        else if (near > rc - 1.1) c.add(capY - 1, capY - 1, cap);     // the brim hangs down
        c.add(capY, capY, cap);
        if (near <= rc - 1) c.add(capY + 1, capY + 1, cap);
    }
    private static void crystals(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        int qx = Math.max(ux0 + 2, Math.min(ux1 - 2, (ux0 + ux1) / 2 + pick(sh, 5, 3) - 1));
        int qz = Math.max(uz0 + 2, Math.min(uz1 - 2, (uz0 + uz1) / 2 + pick(sh, 6, 3) - 1));
        int ax = Math.abs(px - qx), az = Math.abs(pz - qz), cheb = Math.max(ax, az);
        c.free = cheb > 4; c.ground = cheb > 3;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        long ch = colHash(sh, px, pz);
        int glass = (ch & 1) != 0 ? L.glass : L.glass2;
        if (cheb == 0) { c.add(65, 65, L.solidLight()); c.add(66, Math.min(FLOOR + 3 + pick(sh, 7, 4), c.ceil - 2), L.glass); }
        else if (cheb == 1) c.add(65, Math.min(FLOOR + 1 + pick(ch, 1, 4), c.ceil - 2), glass);
        else if (ax + az == 2 && (ax == 0 || az == 0)) c.add(65, FLOOR + 1 + pick(ch, 2, 2), glass);
        // Crystals hang from the roof over the cluster too.
        if (cheb <= 1 && c.ceil >= 75) c.add(c.ceil - 1 - pick(ch, 3, 3), c.ceil - 1, glass);
    }
    private static void geode(Col c, Layout.Room r, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1, int sx0, int sz0) {
        int uw = ux1 - ux0 + 1, ud = uz1 - uz0 + 1;
        double rg = Math.min(6, Math.min(uw, ud) / 2.0 + .5);
        if (rg < 4) { crystals(c, L, sh, px, pz, ux0, ux1, uz0, uz1); return; }
        c.ground = false; c.free = false; c.noStal = true;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0, hx = px - cx, hz = pz - cz, hd = Math.hypot(hx, hz);
        if (hd > rg) return;
        if (hd < .75) { c.floor = L.solidLight(); c.plainFloor = false; }   // the geode's glowing heart
        // Its mouth faces the room's middle, where the lanes are.
        double mouth = Math.atan2(r.d / 2.0 - (sz0 + cz), r.w / 2.0 - (sx0 + cx)), ang = Math.atan2(hz, hx);
        if (hd > 1.2 && Math.abs(Math.IEEEremainder(ang - mouth, 2 * Math.PI)) < .95) return;
        long ch = colHash(sh, px, pz);
        shell(c, hd, rg - 2.4, rg - 1.6, (ch & 1) != 0 ? L.glass : L.glass2);
        shell(c, hd, rg - 1.6, rg - .8, b(1, 4));
        shell(c, hd, rg - .8, rg, ROCK);
    }
    /** One layer of a geode's dome (a little taller than round): the heights where the layer crosses this column. */
    private static void shell(Col c, double h, double a, double b2, int block) {
        if (h >= b2) return;
        double s = .65;
        int lo = FLOOR + Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(0, a * a - h * h)) / s - 1e-9));
        int hi = FLOOR + (int) Math.ceil(Math.sqrt(b2 * b2 - h * h) / s - 1e-9) - 1;
        c.add(lo, hi, block);
    }
    private static void timbers(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        boolean alongX = ux1 - ux0 >= uz1 - uz0;   // frames repeat along the long axis, beams span the short one
        int a = alongX ? px : pz, b = alongX ? pz : px, a0 = alongX ? ux0 : uz0, a1 = alongX ? ux1 : uz1, b0 = alongX ? uz0 : ux0, b1 = alongX ? uz1 : ux1;
        if (b1 - b0 < 4) { crates(c, L, sh, px, pz, ux0, ux1, uz0, uz1); return; }
        c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int post = Math.min(5, c.ceil - FLOOR - 3);
        if (post < 3) return;
        int beam = FLOOR + post + 1, bc = (b0 + b1) / 2, across = logAxis(L.log, alongX ? 8 : 4);
        boolean frame = (a - a0) % 4 == 1 && a < a1;
        if (frame && (b == b0 || b == b1)) { c.ground = false; c.add(FLOOR + 1, beam - 1, logAxis(L.log, 0)); c.add(beam, beam, across); return; }
        if (frame) { c.add(beam, beam, across); if (b == bc) c.add(beam - 1, beam - 1, L.solidLight()); }
        if (b == bc) { c.ground = false; c.floor = GRAVEL; c.plainFloor = false; c.add(FLOOR + 1, FLOOR + 1, b(RAIL, alongX ? 1 : 0)); }
    }
    private static void crates(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int size = Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) >= 6 ? 2 : 1, step = size + 1;
        int i = (px - ux0) / step, j = (pz - uz0) / step, ox = (px - ux0) % step, oz = (pz - uz0) % step;
        if (ox >= size || oz >= size || ux0 + i * step + size - 1 > ux1 || uz0 + j * step + size - 1 > uz1) return;
        long h = Layout.mix(sh ^ i * K1 ^ j * K2 ^ S_CRATE);
        if (pick(h, 0, 4) == 0) return;
        int kind = pick(h, 1, 4), wool = pick(h, 3, 3);
        int block = kind == 0 ? L.wood : kind == 1 ? 170 : kind == 2 ? b(35, wool == 0 ? 12 : wool == 1 ? 7 : 8) : logAxis(L.log, 12);
        int top = Math.min(FLOOR + 1 + pick(h, 2, size == 2 ? 2 : 3), c.ceil - 3);
        c.ground = false;
        c.add(FLOOR + 1, top, block);
        if (ox == 0 && oz == 0 && pick(h, 4, 3) == 0) c.add(top + 1, top + 1, L.light);
    }
    private static void bones(Col c, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0;
        double radius = Math.max(1.5, Math.min(6, Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) / 2.0 - .8));
        double dx = px - cx, dz = pz - cz, dist = Math.hypot(dx, dz);
        c.free = dist > radius * 1.3 + 2;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double e = edge(sh, dx, dz, radius, .3);
        long ch = colHash(sh, px, pz);
        if (dist <= e) {
            c.ground = false;
            int top = FLOOR + Math.max(1, (int) Math.round((1 - dist / e) * 3));
            c.add(FLOOR + 1, top, b(216, 4 * pick(ch, 0, 3)));
            // Ribs rise from the mound's rim, six of them.
            double ang = Math.atan2(dz, dx) + Math.PI;
            int k = (int) Math.round(ang / (Math.PI / 3));
            if (dist > e - 1.3 && Math.abs(ang - k * Math.PI / 3) * dist < .6) c.add(top + 1, Math.min(FLOOR + 3 + pick(sh, 40 + k % 6, 3), c.ceil - 2), b(216, 0));
        } else if (dist <= e + 2.5 && noise(sh, px, pz, 2) > .55) { c.floor = SOUL_SAND; c.plainFloor = false; }
    }
    private static void webs(Col c, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0, dist = Math.hypot(px - cx, pz - cz);
        c.free = dist > 3.5;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        if (dist <= 1.3) { c.ground = false; c.free = false; c.add(FLOOR + 1, FLOOR + 2, b(35, 0)); c.add(FLOOR + 3, FLOOR + 3, WEB_BLOCK); return; }
        double n = noise(sh ^ S_WEB, px, pz, 3);
        // Curtains of web hang from the roof, above head height; a few lie on the floor.
        if (n > .5 && c.ceil - FLOOR >= 8) { c.add(Math.max(FLOOR + 4, c.ceil - 2 - (int) ((n - .5) * 9)), c.ceil - 1, WEB_BLOCK); c.noStal = true; }
        if (n > .78) { c.ground = false; c.add(FLOOR + 1, FLOOR + 1, WEB_BLOCK); }
    }
    private static void roots(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        boolean big = Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) >= 8;
        int tx = (ux0 + ux1) / 2, tz = (uz0 + uz1) / 2, ex = big ? tx + 1 : tx, ez = big ? tz + 1 : tz;
        int dx = px < tx ? tx - px : px > ex ? px - ex : 0, dz = pz < tz ? tz - pz : pz > ez ? pz - ez : 0;
        c.free = dx >= 3 && dz >= 3;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        long ch = colHash(sh, px, pz);
        if (dx == 0 && dz == 0) {
            // The trunk, floor to roof, with a lamp grown into it.
            c.ground = false;
            c.add(FLOOR + 1, c.ceil - 1, logAxis(L.log, 0));
            if (px == tx && pz == tz && (sh & 0x800) != 0) c.inlay(FLOOR + 4, L.solidLight());
            return;
        }
        int dir = dz == 0 ? (px < tx ? 2 : 3) : (pz < tz ? 0 : 1), reach = 2 + pick(sh, 50 + dir, 3);
        if (dx == 0 && dz <= reach || dz == 0 && dx <= reach) {
            c.ground = false;
            c.add(FLOOR + 1, FLOOR + 1 + (Math.max(dx, dz) <= 1 ? 1 : 0), logAxis(L.log, dz == 0 ? 4 : 8));
        }
        int spread = Math.max(dx, dz);
        if (spread <= 3 && c.ceil - FLOOR >= 9) {
            // The crown spreads under the roof in boughs and leaves.
            if (dx == 0 || dz == 0) c.add(c.ceil - 1, c.ceil - 1, logAxis(L.log, dz == 0 ? 4 : 8));
            else if (pick(ch, 1, 3) > 0) c.add(c.ceil - 1 - pick(ch, 2, 2), c.ceil - 1, L.leaves);
        } else if (spread <= 5 && pick(ch, 3, 7) == 0 && c.ceil - FLOOR >= 10) c.add(c.ceil - 2 - pick(ch, 4, 3), c.ceil - 1, L.fence);
    }
    private static void maze(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        boolean alongX = (sh & 1) != 0;
        int a = alongX ? px : pz, b = alongX ? pz : px, a0 = alongX ? ux0 : uz0, a1 = alongX ? ux1 : uz1, b0 = alongX ? uz0 : ux0, b1 = alongX ? uz1 : ux1;
        // Knee-high walls in rows three apart, each open at both ends and broken by a gap: every corridor leads out.
        if ((b - b0) % 3 != 1 || b > b1 - 1 || a < a0 + 1 || a > a1 - 1) return;
        long h = Layout.mix(sh ^ (b - b0) / 3 * K1);
        int gap = a0 + 2 + pick(h, 0, Math.max(1, a1 - a0 - 4));
        if (a == gap || a == gap + 1) return;
        long ch = colHash(sh, px, pz);
        c.ground = false;
        int v = pick(ch, 0, 6);
        c.add(FLOOR + 1, FLOOR + 3, v == 0 ? CRACKED : v == 1 ? b(98, 1) : ROCK);
        if (pick(ch, 1, 9) == 0) c.add(FLOOR + 4, FLOOR + 4, L.light == TORCH ? TORCH : L.solidLight());
    }
    private static void forge(Col c, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.ground = false; c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int qx = (ux0 + ux1) / 2, qz = (uz0 + uz1) / 2, r0 = Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) >= 7 ? 1 : 0;
        int ax = Math.abs(px - qx), az = Math.abs(pz - qz), cheb = Math.max(ax, az);
        int hood = Math.min(FLOOR + 5, c.ceil - 3);
        boolean hooded = hood >= FLOOR + 3;
        if (cheb <= r0) {
            // The quenching basin (lava, a peril) under a hood and chimney.
            c.floor = LAVA; c.sub = MAGMA; c.peril = P_LAVA; c.plainFloor = false;
            if (hooded) { c.add(hood, hood, 112); c.add(hood + 1, c.ceil - 1, 112); }
        } else if (cheb == r0 + 1) {
            c.floor = 112; c.plainFloor = false;
            if (hooded) { c.add(hood, hood, 112); if (ax == az) c.add(FLOOR + 1, hood - 1, 113); }
        } else if (cheb == r0 + 3 && (ax == 0 || az == 0)) c.add(FLOOR + 1, FLOOR + 1, 118);
    }
    private static void terraces(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.free = false; c.ground = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        // Stepped quarry terraces: one block up every two cells, climbable from every side.
        int ring = Math.min(Math.min(px - ux0, ux1 - px), Math.min(pz - uz0, uz1 - pz));
        int height = Math.min(Math.min(5, c.ceil - FLOOR - 4), 1 + ring / 2);
        if (height < 1) return;
        c.add(FLOOR + 1, FLOOR + height, ROCK);
        if (ring == Math.min(ux1 - ux0, uz1 - uz0) / 2 && (sh & 0x1000) != 0) c.inlay(FLOOR + height, L.solidLight());
    }
    private static void springs(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.ground = false; c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0;
        double radius = Math.max(1.2, Math.min(6, Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) / 2.0 - 1.6));
        double dx = px - cx, dz = pz - cz, dist = Math.hypot(dx, dz), e = edge(sh, dx, dz, radius, .3);
        if (dist <= e) { c.floor = WATER; c.sub = b(24, 0); c.plainFloor = false; return; }
        if (dist > e + 1.3) return;
        c.floor = L.rim; c.plainFloor = false;
        double ang = Math.atan2(dz, dx), base = unit(sh, 9) * Math.PI * 2;
        // Sulfur crystals at four points of the shore, a steam vent (a peril) between two of them.
        if (Math.abs(Math.IEEEremainder(ang - base, Math.PI / 2)) * dist < .55) { c.add(FLOOR + 1, FLOOR + 1 + pick(colHash(sh, px, pz), 0, 2), L.glass); return; }
        if (dist > e + .4 && Math.abs(Math.IEEEremainder(ang - base - Math.PI / 4, 2 * Math.PI)) * dist < .55) { c.floor = MAGMA; c.peril = P_GEYSER; }
    }
    private static void rails(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1, int sw, int sd) {
        boolean alongX = sw >= sd;
        int a = alongX ? px : pz, b = alongX ? pz : px, span = alongX ? sw : sd, b0 = alongX ? uz0 : ux0, b1 = alongX ? uz1 : ux1;
        int t1 = Math.max(b0, Math.min(b1, (b0 + b1) / 2 - 1 + pick(sh, 11, 3))), t2 = b1 - b0 >= 8 && t1 + 3 <= b1 ? t1 + 3 : -99;
        int mid = span / 2;
        boolean track = b == t1 || b == t2, run = a >= 1 && a <= span - 2;
        c.free = Math.abs(b - t1) >= 3 && (t2 < 0 || Math.abs(b - t2) >= 3) && Math.abs(a - mid) >= 3;
        if (track && run) { c.ground = false; c.floor = GRAVEL; c.plainFloor = false; c.add(FLOOR + 1, FLOOR + 1, b(RAIL, alongX ? 1 : 0)); }
        else if (run && (Math.abs(b - t1) == 1 || t2 >= 0 && Math.abs(b - t2) == 1)) { c.floor = GRAVEL; c.plainFloor = false; }
        // A signal gantry over the tracks at mid-run.
        int lo = t1 - 2, hi = (t2 >= 0 ? t2 : t1) + 2, post = Math.min(4, c.ceil - FLOOR - 3);
        if (a != mid || post < 3 || lo < b0 || hi > b1 || b < lo || b > hi) return;
        int beam = FLOOR + post + 1, across = logAxis(L.log, alongX ? 8 : 4);
        if (b == lo || b == hi) { c.ground = false; c.add(FLOOR + 1, beam - 1, logAxis(L.log, 0)); c.add(beam, beam, across); }
        else { c.add(beam, beam, across); if (track) c.add(beam - 1, beam - 1, L.solidLight()); }
    }
    private static void collapse(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.ground = false; c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        double cx = (ux0 + ux1) / 2.0, cz = (uz0 + uz1) / 2.0, side = unit(sh, 12) * Math.PI * 2, sx = Math.cos(side), sz = Math.sin(side);
        double radius = Math.max(1.4, Math.min(ux1 - ux0 + 1, uz1 - uz0 + 1) * .28);
        double mx = cx + sx * radius * .6, mz = cz + sz * radius * .6, dx = px - mx, dz = pz - mz, dist = Math.hypot(dx, dz);
        double e = edge(sh, dx, dz, radius, .3);
        long ch = colHash(sh, px, pz);
        if (dist <= e) {
            // A heap of fallen rock, gravel on top.
            int top = FLOOR + 1 + (int) ((1 - dist / e) * 2.5), cap = pick(ch, 0, 4);
            c.add(FLOOR + 1, top - 1, ROCK);
            c.add(top, top, cap == 0 ? GRAVEL : cap == 1 ? 48 : cap == 2 ? 4 : ROCK);
            return;
        }
        // A fallen beam from the heap's foot, and the cracked floor the fall left on the far side (CRUMBLE).
        double t = dx * -sz + dz * sx, p = Math.abs(dx * sx + dz * sz);
        if (t >= e - .5 && t <= e + 3.5 && p < .5) { c.add(FLOOR + 1, FLOOR + 1, logAxis(L.log, Math.abs(sz) > Math.abs(sx) ? 4 : 8)); return; }
        double fx = cx - sx * (radius + 2.6), fz = cz - sz * (radius + 2.6);
        if (Math.hypot(px - fx, pz - fz) <= 1.7) c.crumbleHere = true;
    }
    private static void falls(Col c, Look L, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.ground = false; c.free = false; c.noStal = true;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int uw = ux1 - ux0 + 1, ud = uz1 - uz0 + 1, qx = (ux0 + ux1) / 2, qz = (uz0 + uz1) / 2;
        // A long cell holds two falls, a quarter of its length either side of the middle.
        if (uw >= 14 && uw >= ud) qx += (px < qx ? -1 : 1) * uw / 4;
        else if (ud >= 14) qz += (pz < qz ? -1 : 1) * ud / 4;
        int cheb = Math.max(Math.abs(px - qx), Math.abs(pz - qz));
        boolean moat = Math.min(uw, ud) >= 7;
        // LAVA_FALL: a column of lava from the roof, sealed in glass; around it a moat of lava (LAVA) and a rim.
        if (cheb == 0) { c.peril = P_FALL; c.floor = LAVA; c.sub = MAGMA; c.plainFloor = false; c.add(FLOOR + 1, c.ceil - 1, LAVA); return; }
        if (cheb == 1) { c.add(FLOOR + 1, c.ceil - 1, L.glass); return; }
        if (moat && cheb == 2) { c.floor = LAVA; c.sub = MAGMA; c.peril = P_LAVA; c.plainFloor = false; return; }
        if (cheb == (moat ? 3 : 2)) { c.floor = L.rim; c.plainFloor = false; }
    }
    private static void channel(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1, int sw, int sd, int ax0, int ax1, int az0, int az1) {
        c.ground = false; c.free = false;
        boolean alongX = sw >= sd;
        int a = alongX ? px : pz, b = alongX ? pz : px, b0 = alongX ? uz0 : ux0, b1 = alongX ? uz1 : ux1;
        // A winding channel across the bay cell: its ends meet lane floor or a neighbour's floor, never open air, and it stops
        // short of a wall (a channel against a wall's bulges would wall cells off).
        if (alongX ? px < ax0 || px > ax1 : pz < az0 || pz > az1) return;
        int centre = (b0 + b1) / 2 + (int) Math.round(Math.sin(a * .45 + unit(sh, 13) * Math.PI * 2) * 1.2);
        int liquid = L.liquid == WATER ? WATER : LAVA;
        if (b == centre || b == centre + 1) { c.floor = liquid; c.sub = liquid == LAVA ? OBSIDIAN : CLAY; c.plainFloor = false; if (liquid == LAVA) c.peril = P_LAVA; }
        else if (b == centre - 1 || b == centre + 2) { c.floor = L.rim; c.plainFloor = false; }
    }
    private static void quarry(Col c, Look L, long sh, int px, int pz, int ux0, int ux1, int uz0, int uz1) {
        c.free = false;
        if (!in(px, pz, ux0, ux1, uz0, uz1)) return;
        int midX = (ux0 + ux1 + 1) / 2, midZ = (uz0 + uz1 + 1) / 2, qi = px < midX ? 0 : 1, qj = pz < midZ ? 0 : 1;
        // Cut blocks in four quadrants, two cells apart.
        int x0 = qi == 0 ? ux0 : midX + 1, x1 = qi == 0 ? midX - 2 : ux1, z0 = qj == 0 ? uz0 : midZ + 1, z1 = qj == 0 ? midZ - 2 : uz1;
        if (px < x0 || px > x1 || pz < z0 || pz > z1) return;
        long h = Layout.mix(sh ^ (qi * 2 + qj) * K1 ^ S_QUARRY);
        if (pick(h, 0, 4) == 0) return;
        int height = 1 + pick(h, 1, 3);
        if ((h & 0x10) != 0 && px > (x0 + x1) / 2) height = Math.max(1, height - 1);   // half cut away
        c.ground = false;
        c.add(FLOOR + 1, Math.min(FLOOR + height, c.ceil - 3), L.rock);
    }

    // ---------------------------------------------------------------- noise and hashes (pure)
    static double unit(long h, int k) { return (Layout.mix(h + k * K1) >>> 11) * 0x1.0p-53; }
    static int pick(long h, int k, int n) { return (int) ((Layout.mix(h + k * K1) >>> 33) % n); }
    static long colHash(long sh, int px, int pz) { return Layout.mix(sh ^ px * K1 ^ pz * K2 ^ 0x636f6cL); }
    static int logAxis(int log, int axis) { return (log & 4095) | (((log >>> 12) & 3) | axis) << 12; }
    private static double lattice(long seed, int gx, int gz) { return (Layout.mix(seed ^ gx * K1 ^ gz * K2) >>> 11) * 0x1.0p-53; }
    /** Smooth value noise in [0, 1) on a lattice of the given cell size. */
    static double noise(long seed, int x, int z, int s) {
        int gx = Math.floorDiv(x, s), gz = Math.floorDiv(z, s);
        double fx = (x - gx * s) / (double) s, fz = (z - gz * s) / (double) s, u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = lattice(seed, gx, gz), b = lattice(seed, gx + 1, gz), c = lattice(seed, gx, gz + 1), d = lattice(seed, gx + 1, gz + 1);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
    static double noise1(long seed, int x, int s) {
        int g = Math.floorDiv(x, s);
        double f = (x - g * s) / (double) s, u = f * f * (3 - 2 * f), a = lattice(seed, g, 0);
        return a + (lattice(seed, g + 1, 0) - a) * u;
    }
    /** A star-shaped outline: the radius in the direction (dx, dz), varied by eight hashed spokes. Never encloses a hole. */
    static double edge(long h, double dx, double dz, double radius, double rough) {
        double a = (Math.atan2(dz, dx) + Math.PI) * (4 / Math.PI);
        int i = (int) a;
        double f = a - i, t = f * f * (3 - 2 * f), u = unit(h, 100 + (i & 7)), v = unit(h, 100 + ((i + 1) & 7));
        return radius * (1 + rough * (2 * (u + (v - u) * t) - 1));
    }
}
