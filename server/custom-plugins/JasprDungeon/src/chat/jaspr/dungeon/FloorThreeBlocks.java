package chat.jaspr.dungeon;

import static chat.jaspr.dungeon.FloorThreeStyle.*;

/**
 * Generation 7, Floor III (The Abyssal Citadel): the blocks of its rooms. Pure, allocation-free and fast (block() runs for
 * every block of every generated chunk): no world reads, no random state, every decision from the room's hash.
 *
 * A citadel over a lava sea. The walkable plane is y 64. The lanes and the centre cross (Layout.Room.clearLane) are 7-wide
 * causeways, solid at y 64 and clear to the roof, so stations, doorways and the walk between them never change. Between them
 * lies the sea: lava at y 63 over a solid bed (or soul sand pits in the gardens and catacombs), one block below the
 * platforms, so a fall burns but never drops into a void; rocks break its surface every four blocks to climb out on. The
 * 7 x 7 bays are islands (towers and pillars to the roof, forges, thrones, cages ...), joined to the causeways by short
 * bridges; columns rise from the sea where four tiles meet; chains hang over the sea. Walls are two thick with the barred
 * doorways of DungeonGenerator.classic, so DungeonPlugin.walk works unchanged. The refuges (and the arrival room with its
 * return gate) are the House of Mercy's own rooms in the citadel's stone.
 *
 * Physical perils (PerilMarks) are drawn exactly where peril() says (owner 2026-10-05: "physical dangers as well, such as
 * lava pools, falling stalactites, and other things of that nature"): LAVA (the sea and the pools in some bays), CRUMBLE
 * (cracked bridge spans over a magma pit: y 64 cracked bricks, y 63 air, y 62 magma), GEYSER (magma vents at the corners of
 * some brazier daises), LAVA_FALL (open lava from the roof at a bay's corner) and STALACTITE (netherrack points over the
 * bays of the tall halls). None ever on a lane, a station core or under the chest; more of them deeper and in gauntlets.
 */
final class FloorThreeBlocks {
    private FloorThreeBlocks() {}
    /** Interior column kinds. */
    static final int K_LANE = 0, K_SEA = 1, K_SHOAL = 2, K_FALL = 3, K_BRIDGE = 4, K_CRUMBLE = 5, K_BAY = 6, K_POOL = 7, K_COLUMN = 8, K_LEDGE = 9, K_POST = 10;
    static final int GEYSER_BIT = 1 << 15, STAL_BIT = 1 << 16, LONG_BIT = 1 << 17;
    static final int LAVA = 11, CRACKED = 98 | (2 << 12), MAGMA = 213, BARS = 101, FOUNDATION = 87, CHEST = 54 | (2 << 12), SOUL = 88,
        LANTERN = 169, ROD = 198 | (1 << 12), FLOWER = 200 | (5 << 12), POD = 100 | (14 << 12), SKY_GLASS = 95 | (3 << 12), TIP = 113;
    /** The refuges keep the House of Mercy's furnishings in the citadel's stone: nether brick, quartz, red nether brick. */
    static final int[] REFUGE = {112, 155, 215, 5 | (5 << 12), 95 | (14 << 12)};

    private static int threat(Layout.Room r) { return Math.max(0, Math.min(2, r.tier - 3)); }
    private static boolean finale(Layout.Room r) { return r.kind == Layout.Kind.THRONE || r.kind == Layout.Kind.DESCENT; }
    private static boolean gauntlet(Layout.Room r) { return r.kind == Layout.Kind.GAUNTLET; }
    /** Stalactites hang only in the tall halls (roof 88 and up). */
    static boolean tall(Layout.Room r) { return r.roof() >= 88; }
    /** One stream of decisions per bay, from the room's hash and the bay's centre column. */
    static long bay(Layout.Room r, int bx, int bz) { return Layout.mix(r.hash ^ (bx * 0x632be59bd9b4e019L) ^ (bz * 0x9e3779b97f4a7c15L) ^ 0x436974616465L); }
    /** The structure a bay raises: lava pools, open sea and braziers by threat, otherwise one of the theme's own. */
    static int structure(Layout.Room r, long v) {
        int roll = (int) ((v >>> 1) % 100), pools = 8 + 5 * threat(r) + (gauntlet(r) ? 10 : 0) + (finale(r) ? 6 : 0);
        if (roll < pools) return POOL;
        if (roll < pools + 10) return SEA;
        if (roll < pools + 24) return DAIS;
        return (finale(r) ? THRONE_STRUCTURES : STRUCTURES_OF[index(r.theme)])[(int) ((v >>> 40) & 3)];
    }
    static int crumbleChance(Layout.Room r) { return 18 + 8 * threat(r) + (gauntlet(r) ? 30 : 0) + (finale(r) ? 15 : 0); }
    static int fallChance(Layout.Room r) { return FALLS[index(r.theme)] + 5 * threat(r) + (finale(r) ? 15 : 0); }
    static int geyserChance(Layout.Room r) { return 35 + 10 * threat(r) + (gauntlet(r) ? 25 : 0) + (finale(r) ? 20 : 0); }

    /**
     * What stands in the interior column (x, z) (relative 2..w-3, 2..d-3): the kind in the low four bits, then the bay's
     * structure (bits 4..8), its offsets from the bay centre plus three (bits 9..11 and 12..14) and the peril bits.
     */
    static int column(Layout.Room r, int x, int z) {
        if (r.clearLane(x, z)) return K_LANE;
        int rx = x - r.x, rz = z - r.z, tx = rx & 31, tz = rz & 31, da = tx - (tx < 16 ? 7 : 25), db = tz - (tz < 16 ? 7 : 25);
        // Lamp posts rise from the sea at the four outer corners of every causeway crossing, where the lava is farthest.
        if ((band(rx - 1, r.w) || band(rx + 1, r.w)) && (band(rz - 1, r.d) || band(rz + 1, r.d))) return K_POST;
        if (r.hazards.length > 0 && HazardCatalog.has(r, HazardCatalog.Type.BLAST_SPORES) && HazardCatalog.pod(r, x, z)) return K_LEDGE;
        boolean inX = da >= -3 && da <= 3, inZ = db >= -3 && db <= 3;
        if (inX && inZ) {
            long v = bay(r, x - da, z - db);
            int s = structure(r, v), a = Math.abs(da), b = Math.abs(db), m = Math.max(a, b), code = (s << 4) | ((da + 3) << 9) | ((db + 3) << 12);
            if (s == POOL && m <= 2) {
                if (a == 2 && b == 2) return K_SHOAL | code;
                return (da == 0 && db == 0 && ((v >>> 36) & 1) == 0 ? K_COLUMN : K_POOL) | code;
            }
            if (s != SEA) {
                int flags = 0;
                if (s == DAIS && a == 2 && b == 2 && (v >>> 48) % 100 < geyserChance(r)) flags |= GEYSER_BIT;
                if (tall(r) && (v >>> 52) % 100 < 50 && (a == 0 || b == 0) && (s == DAIS && m == 2 || s == POOL && m == 3))
                    flags |= STAL_BIT | (((v >>> 58) & 1) != 0 ? LONG_BIT : 0);
                return K_BAY | code | flags;
            }
            if (da == 0 && db == 0) return K_COLUMN | code;
            // the rest of an open-sea bay is sea
        }
        // Columns rise from the sea where four tiles meet (never on the centre cross: the lane test came first).
        if (((rx + 1) & 31) <= 2 && ((rz + 1) & 31) <= 2 && rx >= 31 && rz >= 31 && rx <= r.w - 31 && rz <= r.d - 31) return K_COLUMN;
        // Bridges: three wide across the moat between a bay and its tile's lane; some are cracked and give way.
        if (!inX && inZ && db >= -1 && db <= 1 && (tx == 11 || tx == 12 || tx == 20 || tx == 21)) {
            long v = bay(r, x - tx + (tx < 16 ? 7 : 25), z - db);
            if (structure(r, v) != SEA && (v >>> 8) % 3 != 0) return (v >>> 12) % 100 < crumbleChance(r) ? K_CRUMBLE : K_BRIDGE;
        }
        if (inX && !inZ && da >= -1 && da <= 1 && (tz == 11 || tz == 12 || tz == 20 || tz == 21)) {
            long v = bay(r, x - da, z - tz + (tz < 16 ? 7 : 25));
            if (structure(r, v) != SEA && (v >>> 10) % 3 != 0) return (v >>> 19) % 100 < crumbleChance(r) ? K_CRUMBLE : K_BRIDGE;
        }
        // Lava falls: open lava from the roof, diagonal to a bay's corner, never beside a lane.
        if ((tx == 3 || tx == 11 || tx == 21 || tx == 29) && (tz == 3 || tz == 11 || tz == 21 || tz == 29)) {
            int bx = x - tx + (tx < 16 ? 7 : 25), bz = z - tz + (tz < 16 ? 7 : 25);
            long v = bay(r, bx, bz);
            int corner = (int) ((v >>> 33) & 3);
            if (x - bx == ((corner & 1) == 0 ? -4 : 4) && z - bz == ((corner & 2) == 0 ? -4 : 4) && (v >>> 26) % 100 < fallChance(r)
                && !r.clearLane(x + 1, z) && !r.clearLane(x - 1, z) && !r.clearLane(x, z + 1) && !r.clearLane(x, z - 1)) return K_FALL;
        }
        return (x & 3) == 0 && (z & 3) == 0 ? K_SHOAL : K_SEA;
    }

    /** A room-relative row or column inside a causeway band (Layout.Room.clearLane: tile cells 13..19 or the centre +-3). */
    private static boolean band(int relative, int span) { int a = relative & 31; return a >= 13 && a <= 19 || Math.abs(relative - span / 2) <= 3; }

    /** The packed legacy block (id | data << 12) at (x, y, z) of a Floor III room. */
    static int block(Layout.Room r, int x, int y, int z) {
        if (y < 56 || y > 100) return 0;
        if (y == 56 || y == 100) return 7;
        if (r.kind == Layout.Kind.REFUGE) return DungeonGenerator.classic(r, x, y, z, REFUGE, 56, 100);
        int roof = r.roof(), t = index(r.theme);
        int[] p = PALETTES[t];
        if (y > roof) return SKY[t] && y == roof + 1 ? LANTERN : FOUNDATION;
        if (y < 62) return FOUNDATION;
        int rx = x - r.x, rz = z - r.z;
        if (rx < 2 || rz < 2 || rx >= r.w - 2 || rz >= r.d - 2) return wall(r, x, y, z, rx, rz, roof, p);
        int c = column(r, x, z), k = c & 15;
        if (y == roof) return ceiling(r, x, z, k, p, t);
        switch (k) {
            case K_LANE:
                if (y < 64) return FOUNDATION;
                if (y == 64) return deck(r, x, z, p);
                return y == 65 && x == r.cx() && z == r.cz() + 4 ? CHEST : 0;
            case K_BAY: return bay(r, x, y, z, c, roof, p);
            case K_BRIDGE: return y < 64 ? FOUNDATION : y == 64 ? mark(r, x, z, p[EDGE]) : 0;
            case K_CRUMBLE: return y == 62 ? MAGMA : y == 64 ? CRACKED : 0;
            case K_FALL: return y == 62 ? p[BED] : LAVA;
            case K_POOL: return y == 62 ? p[BED] : y == 63 ? LAVA : 0;
            case K_COLUMN: return y < 64 ? p[TOWER] : y == 64 || y == 65 || y == roof - 1 ? p[TRIM] : (y - 65) % 8 == 4 ? p[LIGHT] : p[TOWER];
            case K_LEDGE: return y < 64 ? FOUNDATION : y == 64 ? p[EDGE] : y == 65 ? POD : 0;
            case K_POST: return y <= 64 ? p[TOWER] : y == 65 || y == 67 ? p[TRIM] : y == 66 ? p[LIGHT] : 0;
            default: // the sea and its rocks
                if (y == 62) return p[BED];
                if (y == 63) return k == K_SHOAL ? p[BED] : SOUL_SEA[t] ? (Math.floorMod(x + 2 * z, 5) == 0 ? LANTERN : SOUL) : LAVA;
                return y == 64 ? 0 : chain(r, x, y, z, roof, p, t);
        }
    }
    /** A room's danger mark (HazardCatalog) where one lies on this solid surface, else the surface itself. */
    private static int mark(Layout.Room r, int x, int z, int surface) { int m = HazardCatalog.floor(r, x, z); return m != 0 ? m : surface; }
    /** Causeway surface: framed edges, a dashed runner down each lane, an inlaid medallion at each crossing. */
    private static int deck(Layout.Room r, int x, int z, int[] p) {
        int m = HazardCatalog.floor(r, x, z);
        if (m != 0) return m;
        if (!r.clearLane(x + 1, z) || !r.clearLane(x - 1, z) || !r.clearLane(x, z + 1) || !r.clearLane(x, z - 1)) return p[EDGE];
        int tx = (x - r.x) & 31, tz = (z - r.z) & 31, cx = r.cx(), cz = r.cz();
        if (Math.abs(tx - 16) <= 1 && Math.abs(tz - 16) <= 1 || Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) return p[INLAY];
        if ((tx == 16 || x == cx) && (z & 1) == 0 || (tz == 16 || z == cz) && (x & 1) == 0) return p[INLAY];
        return p[DECK];
    }
    /** Ribs over the causeways with lamps every eight blocks, the theme's vault (a glass sky, or stars) elsewhere. */
    private static int ceiling(Layout.Room r, int x, int z, int k, int[] p, int t) {
        int mark = HazardCatalog.roof(r, x, z);
        if (mark != 0) return mark;
        if (k == K_FALL) return MAGMA;
        int tx = (x - r.x) & 31, tz = (z - r.z) & 31;
        if (tx == 16 || tz == 16 || x == r.cx() || z == r.cz()) return (x & 7) == 0 && (z & 7) == 0 ? p[LIGHT] : p[RIB];
        if (SKY[t]) return SKY_GLASS;
        if (STARS[t] && (Layout.mix(r.hash ^ (x * 0x9e3779b1L) ^ (z * 0x85ebca77L)) & 31) == 0) return LANTERN;
        return p[CEIL];
    }
    /** Chains over the open sea, each ending in a lamp above head height. */
    private static int chain(Layout.Room r, int x, int y, int z, int roof, int[] p, int t) {
        boolean dense = CHAINS[t];
        if (dense ? (x & 3) != 1 || (z & 3) != 1 : (x & 7) != 3 || (z & 7) != 5) return 0;
        long h = Layout.mix(r.hash ^ (x * 0x2545f4914f6cdd1dL) ^ (z * 0x9e3779b97f4a7c15L) ^ 0x436861696eL);
        if ((h >>> 3) % (dense ? 2 : 3) != 0) return 0;
        int bottom = Math.max(69, roof - 3 - (int) ((h >>> 9) % Math.max(1, roof - 74)));
        return y > bottom ? BARS : y == bottom ? p[LIGHT] : 0;
    }
    /**
     * Two-thick walls exactly like DungeonGenerator.classic at the doorways (bars in the outer ring at y 65..68, open inner
     * ring, a glowstone lintel at 69), the room's dart slits and ember sockets on the inner face, and the citadel's face
     * elsewhere: trim courses and pilasters, lit stained-glass windows high up.
     */
    private static int wall(Layout.Room r, int x, int y, int z, int rx, int rz, int roof, int[] p) {
        if (y < 64) return FOUNDATION;
        if (y == 64) return p[TRIM];
        if (y == roof) return p[WALL];
        boolean we = rx < 2 || rx >= r.w - 2, ns = rz < 2 || rz >= r.d - 2;
        boolean opening = Layout.Room.lane(z) && we || Layout.Room.lane(x) && ns;
        if (opening && y <= 68) return r.door(x, z) ? BARS : 0;
        if (opening && y == 69) return 89;
        if (y == 66 || y == 67) { int m = HazardCatalog.wall(r, x, y, z); if (m != 0) return m; }
        if (y == 65 || y == roof - 1 || we && ns || (y - 64) % 7 == 0) return p[TRIM];
        int along = we ? rz : rx;
        if ((along & 7) == 0) return p[TRIM];
        int m = along & 31;
        if (y >= 71 && y <= Math.min(74, roof - 3) && (m >= 5 && m <= 7 || m >= 25 && m <= 27)) {
            boolean inner = we ? rx == 1 || rx == r.w - 2 : rz == 1 || rz == r.d - 2;
            return inner ? p[GLASS] : p[LIGHT];
        }
        return p[WALL];
    }
    /** A bay: its island surface (or a vent), a stalactite over it in the tall halls, and its structure. */
    private static int bay(Layout.Room r, int x, int y, int z, int c, int roof, int[] p) {
        int s = (c >>> 4) & 31, da = ((c >>> 9) & 7) - 3, db = ((c >>> 12) & 7) - 3;
        if (y < 64) return FOUNDATION;
        if (y == 64) return (c & GEYSER_BIT) != 0 ? MAGMA : mark(r, x, z, s == CHORUS ? 121 : p[ISLAND]);
        if ((c & STAL_BIT) != 0) {
            int tip = roof - ((c & LONG_BIT) != 0 ? 4 : 3);
            if (y > tip) return FOUNDATION;
            if (y == tip) return TIP;
        }
        return structure(s, da, db, y - 65, roof - 66, p, bay(r, x - da, z - db));
    }

    /**
     * One bay structure, by its offsets from the bay centre (da, db in -3..3), its height above the platform (h, 0 at y 65)
     * and the last height below the roof (top). Each stands whole on its island; nothing here is lava or a floor vent.
     */
    static int structure(int s, int da, int db, int h, int top, int[] p, long v) {
        int a = Math.abs(da), b = Math.abs(db), m = Math.max(a, b);
        switch (s) {
            case TOWER_KEEP: // a keep tower to the roof: corner buttresses, trim courses, windows lit from within
                if (m > 2) return 0;
                if (h == 0 || h == top || h % 6 == 5 || a == 2 && b == 2) return p[TRIM];
                if (h % 6 == 2 || h % 6 == 3) { if (m == 2 && (a == 0 || b == 0)) return p[GLASS]; if (m <= 1) return p[LIGHT]; }
                return p[TOWER];
            case PILLAR: // a banded pillar to the roof on a plinth, with a capital and lamps at its foot
                if (m <= 1) return h % 6 == 0 ? p[TRIM] : p[TOWER];
                if (m == 2 && (h == 0 || h == top)) return p[TRIM];
                if (a == 2 && b == 2 && h == 1) return p[LIGHT];
                if (m == 2 && (a == 0 || b == 0) && h == top - 1) return p[TRIM];
                return 0;
            case DAIS: // a brazier on a low dais, lamp posts at the island's corners
                if (da == 0 && db == 0) return h == 0 ? MAGMA : h == 1 ? p[LIGHT] : 0;
                if (m <= 1) return h == 0 ? p[TRIM] : 0;
                if (a == 3 && b == 3) return h <= 1 ? p[TOWER] : h == 2 ? p[LIGHT] : 0;
                return 0;
            case POOL: // the rim of a lava pool, lamps at its corners
                return a == 3 && b == 3 ? h == 0 ? p[TRIM] : h == 1 ? p[LIGHT] : 0 : 0;
            case OBELISK: { // a tall needle with a lit crown on a buttressed base
                int tall = Math.min(top - 3, 8 + (int) ((v >>> 44) % 6));
                if (da == 0 && db == 0) return h == 0 ? p[TRIM] : h <= tall ? p[TOWER] : h == tall + 1 ? p[LIGHT] : 0;
                if (m <= 1) return h == 0 ? p[TRIM] : h == 1 && a + b == 1 ? p[TOWER] : 0;
                if (a == 2 && b == 2) return h == 0 ? p[TRIM] : h == 1 ? p[LIGHT] : 0;
                return 0;
            }
            case CAGE: // a barred cage with a captive light, hung from the roof by a chain
                if (da == 0 && db == 0 && h >= 5) return BARS;
                if (m > 2) return 0;
                if (h == 0 || h == 4) return p[TRIM];
                if (m == 2 && h <= 3) return da == 0 && db == -2 && h <= 2 ? 0 : BARS;
                return da == 0 && db == 0 && h == 1 ? p[LIGHT] : 0;
            case GALLOWS: // two posts, a beam, a short chain over a scaffold
                if (db == 0 && a == 2 && h <= 5) return p[TOWER];
                if (db == 0 && a <= 2 && h == 6) return p[TOWER];
                if (db == 0 && da == 0 && (h == 4 || h == 5)) return BARS;
                if (db == 0 && a == 2 && h == 7) return p[LIGHT];
                return a <= 1 && b <= 1 && h == 0 ? p[TRIM] : 0;
            case BOOKS: // two book walls under a lintel, end rods on top, a lit reading desk between them
                if (a == 2 && b <= 2) return h <= 3 ? 47 : h == 4 ? p[TRIM] : b == 2 && h == 5 ? ROD : 0;
                if (b == 2 && a <= 1 && h == 4) return p[TRIM];
                return da == 0 && db == 0 ? h == 0 ? p[TOWER] : h == 1 ? p[LIGHT] : 0 : 0;
            case CHORUS: { // chorus plants on end stone, end rods at the corners (fully grown flowers never spread)
                int stalk = da == -1 && db == -1 ? 0 : da == 2 && db == 1 ? 1 : da == -2 && db == 2 ? 2 : -1;
                if (stalk >= 0) { int tall = 2 + (int) ((v >>> (44 + 3 * stalk)) & 3); return h < tall ? 199 : h == tall ? FLOWER : 0; }
                if (a == 3 && b == 3) return h == 0 ? ROD : 0;
                return da == 1 && db == -2 && h == 0 ? p[LIGHT] : 0;
            }
            case THRONE: // a throne on a dais: high back with a lit crown, seat and arms, braziers before it
                if (m <= 2 && h == 0) return p[TRIM];
                if (db == -2 && a <= 1 && h >= 1 && h <= 4) return p[TOWER];
                if (da == 0 && db == -2 && h == 5) return p[LIGHT];
                if (da == 0 && db == -1 && h == 1) return p[ACCENT];
                if (a == 1 && db == -1 && (h == 1 || h == 2)) return p[TRIM];
                return a == 2 && db == 1 && h == 1 ? p[LIGHT] : 0;
            case PYRE: // a stepped pyre with molten coals and a flame, the bones of kings at its corners
                if (m <= 2 && h == 0) return p[TRIM];
                if (m <= 1 && h == 1) return da == 0 && db == 0 ? MAGMA : p[TOWER];
                if (da == 0 && db == 0) return h == 2 ? MAGMA : h == 3 ? p[LIGHT] : 0;
                return a == 2 && b == 2 && h == 1 ? 216 : 0;
            case MIRRORS: // three tall glass screens in their frames
                if ((da == -2 || da == 0 || da == 2) && b <= 2 && h <= 4) return b == 2 || h == 0 || h == 4 ? p[TRIM] : p[GLASS];
                return da == 0 && db == 0 && h == 5 ? p[LIGHT] : 0;
            case HOARD: { // a mound of "coins" and coffers: yellow terracotta, orange glaze, quartz, glowstone gems; no gold
                int s2 = a + b, crest = s2 == 0 ? 3 : s2 == 1 ? 2 : s2 == 2 ? 1 : s2 == 3 ? 0 : -1;
                if (h > crest) return s2 == 0 && h == crest + 1 ? p[LIGHT] : 0;
                int k = (int) ((v >>> 20) + da * 7 + db * 13 + h * 5) & 7;
                return k == 0 ? 89 : k == 1 ? 236 : k == 2 ? 155 : 159 | (4 << 12);
            }
            case FORGE: // a crucible of molten magma under a chain hood
                if (da == 0 && db == 0 && h >= 6) return BARS;
                if (m > 2) return 0;
                if (h == 0) return p[TOWER];
                if (m == 2 && h <= 2) return a == 2 && b == 2 && h == 2 ? p[LIGHT] : p[TRIM];
                return m <= 1 && h == 1 ? MAGMA : 0;
            case RUIN: { // a broken tower: walls of uneven height with cracked tops, rubble on its island
                if (m == 3) return h == 0 && ((v >>> ((da * 3 + db * 7 + 30) & 63)) & 7) == 0 ? CRACKED : 0;
                if (m <= 1) return h <= 1 ? p[TOWER] : da == 0 && db == 0 && h == 2 ? p[LIGHT] : 0;
                int height = 3 + (int) ((Layout.mix(v ^ (da * 31 + db)) >>> 5) % Math.max(1, top / 2));
                return h < height ? p[TOWER] : h == height ? CRACKED : 0;
            }
            case GATE: // an obsidian frame around a pane of the abyss, unlit end portal frames before it
                if (db == 0 && a <= 2 && h <= 6) return a == 2 || h == 0 || h == 6 ? 49 : p[GLASS];
                if (b == 2 && (a == 0 || a == 2) && h == 0) return 120 | ((db < 0 ? 0 : 2) << 12);
                return a == 3 && b == 3 && h <= 1 ? h == 0 ? p[TRIM] : p[LIGHT] : 0;
            case OSSUARY: // bone arches on four bone posts over a lit ossuary
                if (a == 2 && b == 2) return h <= 5 ? 216 : 0;
                if (m == 2 && h == 5) return b == 2 ? 216 | (4 << 12) : 216 | (8 << 12);
                if (da == 0 && db == 0) return h == 0 ? 216 : h == 1 ? p[LIGHT] : 0;
                return a == 1 && b == 1 && h == 0 ? 216 : 0;
            case BUNKS: // two double bunks on posts, a lit weapon post between them
                if (a == 2 && b == 2) return h <= 4 ? p[TOWER] : 0;
                if (b == 2 && a <= 1) return h == 0 || h == 3 ? p[TRIM] : h == 1 || h == 4 ? p[ACCENT] : 0;
                return da == 0 && db == 0 ? h <= 1 ? p[TOWER] : h == 2 ? p[LIGHT] : 0 : 0;
            case ORRERY: // a lit sphere on a pedestal inside a glass ring, end rods at its corners
                if (m <= 1 && h == 0) return p[TRIM];
                if (da == 0 && db == 0) return h <= 3 ? p[TOWER] : h == 4 ? p[LIGHT] : 0;
                if (db == 0 && h >= 1) { int ring = a + Math.abs(h - 4); if (ring >= 2 && ring <= 3) return p[GLASS]; }
                return a == 2 && b == 2 && h == 0 ? ROD : 0;
            default: return 0;
        }
    }

    /** Where Floor III draws each physical peril: exactly the columns block() draws it in (see the class comment). */
    static boolean peril(PerilMarks.Kind k, Layout.Room r, int x, int z) {
        if (r == null || r.kind == Layout.Kind.REFUGE || k == null) return false;
        int rx = x - r.x, rz = z - r.z;
        if (rx < 2 || rz < 2 || rx >= r.w - 2 || rz >= r.d - 2) return false;
        int c = column(r, x, z), kind = c & 15;
        switch (k) {
            case LAVA: return kind == K_POOL || kind == K_SEA && !SOUL_SEA[index(r.theme)];
            case CRUMBLE: return kind == K_CRUMBLE;
            case GEYSER: return kind == K_BAY && (c & GEYSER_BIT) != 0;
            case LAVA_FALL: return kind == K_FALL;
            case STALACTITE: return kind == K_BAY && (c & STAL_BIT) != 0;
            default: return false;
        }
    }
}
