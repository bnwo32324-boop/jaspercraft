package chat.jaspr.dungeon;

/**
 * Generation 7 (owner 2026-10-05: "physical dangers as well, such as lava pools, falling stalactites, and other things of that
 * nature"; Floor I only in its "fiery and ruined themes"): where Floor I draws its physical perils, and what they look like.
 * One predicate answers both questions: PerilMarks.at(kind, room, x, z) says where a peril is, the generator draws exactly those
 * cells, and Perils makes them act from the same answer.
 *
 *   LAVA        a casting pit in a bay: lava one block deep in the floor (y 64) inside a low rim of slabs. Bellfounder's Crypt,
 *               and generation 6's Cinder Chapel and Sunless Foundry.
 *   CRUMBLE     fractured flagstones in a bay: cracked bricks (y 64) over a shallow magma pit (magma at y 63; Floor I has only
 *               stone below its floor and bedrock at y 62). Rusted Reliquary, and Shattered Basilica.
 *   GEYSER      an oven vent in the floor between the bays: a netherrack grate (y 64). Ashen Kitchens.
 *   STALACTITE  a point of rock hanging from the roof between the bays, one to three blocks long. Weeping Orchard, Gutter Abbey.
 *
 * Never inside the arrival room or any refuge, never on a lane core (the cells mobs spawn on and doors open onto), never on the
 * eight-block lamp lattice or on a mark of the room's own dangers (HazardCatalog), never beside the reliquary chest, and never
 * where a pillar, prop or chandelier stands. Lava is never beside anything that burns (the dungeon worlds also turn doFireTick off).
 */
final class FloorOnePerils {
    private FloorOnePerils() {}
    static final int LAVA = 1 << PerilMarks.Kind.LAVA.ordinal(), CRUMBLE = 1 << PerilMarks.Kind.CRUMBLE.ordinal(),
        GEYSER = 1 << PerilMarks.Kind.GEYSER.ordinal(), STALACTITE = 1 << PerilMarks.Kind.STALACTITE.ordinal();
    private static final int CRACKED = 98 | (2 << 12), RIM = 44 | (5 << 12), RIM_NETHER = 44 | (6 << 12), MAGMA = 213, LAVA_ID = 11, VENT = 87;

    /** The kinds of peril a Floor I theme carries (bits of PerilMarks.Kind ordinals), 0 for most. */
    static int mask(int theme) {
        switch (theme) {
            case 3: case 28: case 36: return LAVA;            // Cinder Chapel, Sunless Foundry, Bellfounder's Crypt
            case 20: case 44: return CRUMBLE;                 // Shattered Basilica, Rusted Reliquary
            case 46: return GEYSER;                           // Ashen Kitchens
            case 43: case 48: return STALACTITE;              // Weeping Orchard, Gutter Abbey
            default: return 0;
        }
    }

    static boolean at(PerilMarks.Kind k, Layout.Room r, int x, int z) {
        if (r.kind == Layout.Kind.REFUGE || (mask(r.theme) & (1 << k.ordinal())) == 0 || !r.inner(x + .5, z + .5)) return false;
        switch (k) {
            case LAVA: return lava(r, x, z);
            case CRUMBLE: return crumble(r, x, z);
            case GEYSER: return geyser(r, x, z);
            case STALACTITE: return stalactite(r, x, z);
            default: return false;
        }
    }

    // ---------------------------------------------------------------- the bays: casting pits and fractured flagstones
    private static boolean lattice(int x, int z) { return Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4; }
    /** The bay anchor (tile cell 7 or 25) of a column, in world coordinates; the offsets from it are dx = x - anchor. */
    private static int anchor(int n, int origin) { int l = n - origin; return origin + (l / 32) * 32 + (l % 32 < 16 ? 7 : 25); }
    /** Shape number (0..2) of a peril bay, from its hash. */
    private static int shape(long bayHash) { return (int) Math.floorMod(bayHash >>> 8, 3); }
    private static boolean turned(long bayHash) { return ((bayHash >>> 16) & 1) != 0; }
    /** The pit's cells (dx, dz relative to the bay centre). Lava pits are small and sit at the centre; the crumbling field is five by five. */
    private static boolean pit(int shape, boolean turned, int dx, int dz) {
        int u = turned ? dz : dx, v = turned ? dx : dz, a = Math.abs(u), b = Math.abs(v);
        switch (shape) {
            case 0: return a <= 1 && b <= 1;                          // a three by three pit
            case 1: return a == 0 && b <= 2 || b == 0 && a <= 2;       // a cross with arms two long
            default: return a <= 1 && b <= 2;                         // a three by five trough
        }
    }
    private static boolean field(int shape, boolean turned, int dx, int dz, int salt) {
        int u = turned ? dz : dx, v = turned ? dx : dz, a = Math.abs(u), b = Math.abs(v);
        if (a > 2 || b > 2) return false;
        switch (shape) {
            case 0: return true;                                      // the whole five by five
            case 1: return (a + b & 1) == 0;                          // a checker of cracked stones
            default: return a <= 1 || b <= 1;                         // a plus-shaped band
        }
    }
    /** True when the bay's pit or field is clear of every lamp of the lattice and every mark of the room's own dangers. */
    static boolean usable(Layout.Room r, int ax, int az, long bayHash) {
        int pm = mask(r.theme);
        boolean lava = (pm & LAVA) != 0;
        int shape = shape(bayHash);
        boolean turned = turned(bayHash);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!(lava ? pit(shape, turned, dx, dz) : field(shape, turned, dx, dz, 0))) continue;
            int x = ax + dx, z = az + dz;
            if (lattice(x, z) || HazardCatalog.floor(r, x, z) != 0 || PerilMarks.core(r, x, z)) return false;
        }
        return true;
    }
    private static boolean inPeril(Layout.Room r, int x, int z, boolean lava) {
        int ax = anchor(x, r.x), az = anchor(z, r.z), dx = x - ax, dz = z - az;
        if (Math.abs(dx) > 3 || Math.abs(dz) > 3 || FloorOneExtra.bayKind(r, ax, az) != FloorOneExtra.PERIL) return false;
        long h = FloorOneExtra.bayHash(r, ax, az);
        if (lava) return pit(shape(h), turned(h), dx, dz);
        // The field is whole but for a stable stone here and there, so a walker can read where the floor holds.
        return field(shape(h), turned(h), dx, dz, 0) && Math.floorMod(Layout.mix(r.hash ^ (x * 0x9e3779b1L) ^ (z * 0x85ebca77L) ^ 0x63727562L), 10) >= 2;
    }
    static boolean lava(Layout.Room r, int x, int z) { return inPeril(r, x, z, true); }
    static boolean crumble(Layout.Room r, int x, int z) { return inPeril(r, x, z, false); }

    /** The block of a casting pit's low rim (y 65) or air elsewhere in a peril bay. */
    static int bayBlock(Layout.Room r, int ax, int az, long bayHash, int dx, int dz, int y) {
        if (y != 65 || (mask(r.theme) & LAVA) == 0) return 0;
        int shape = shape(bayHash);
        boolean turned = turned(bayHash);
        if (pit(shape, turned, dx, dz)) return 0;
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) if (pit(shape, turned, dx + ox, dz + oz)) return r.theme == 36 || r.theme == 3 || r.theme == 28 ? RIM_NETHER : RIM;
        return 0;
    }

    // ---------------------------------------------------------------- between the bays: vents and stalactites
    private static boolean gap(int t) { return t == 11 || t == 12 || t == 20 || t == 21; }
    private static boolean span(int t) { return t >= 4 && t <= 10 || t >= 22 && t <= 28; }
    /** The two free cells beside each lane edge, along the bays and at the lane crossings: where a vent or a stalactite may be. */
    private static boolean zone(Layout.Room r, int x, int z) {
        int tx = Math.floorMod(x - r.x, 32), tz = Math.floorMod(z - r.z, 32);
        return gap(tx) && (span(tz) || gap(tz)) || gap(tz) && span(tx);
    }
    private static long cell(Layout.Room r, int x, int z, long salt) { return Layout.mix(r.hash ^ (x * 0x9e3779b97f4a7c15L) ^ (z * 0xc2b2ae3d27d4eb4fL) ^ salt); }
    static boolean geyser(Layout.Room r, int x, int z) {
        return zone(r, x, z) && HazardCatalog.open(r, x, z) && !PerilMarks.core(r, x, z) && HazardCatalog.floor(r, x, z) == 0
            && Math.floorMod(cell(r, x, z, 0x6765797365L), 22) == 0;
    }
    static boolean stalactite(Layout.Room r, int x, int z) {
        int tx = Math.floorMod(x - r.x, 32), tz = Math.floorMod(z - r.z, 32);
        // A chandelier hangs at each lane crossing's corner cells, and the roof's own marks stay clear of the points.
        return zone(r, x, z) && !((tx == 11 || tx == 20) && (tz == 11 || tz == 20)) && !PerilMarks.core(r, x, z)
            && Math.floorMod(cell(r, x, z, 0x7374616c6163L), 16) == 0;
    }
    /** How many blocks (1..3) the stalactite at this point hangs below the roof. */
    static int length(Layout.Room r, int x, int z) { return 1 + (int) Math.floorMod(cell(r, x, z, 0x6c656e677468L) >>> 8, 3); }

    // ---------------------------------------------------------------- drawing
    /** The perils' blocks of one voxel (floor, underfloor and roof; the bays' rim is bayBlock), or -1. */
    static int block(Layout.Room r, int x, int y, int z, int mask) {
        if (r.kind == Layout.Kind.REFUGE) return -1;
        if (y == 64) {
            if ((mask & LAVA) != 0 && lava(r, x, z)) return LAVA_ID;
            if ((mask & CRUMBLE) != 0 && crumble(r, x, z)) return CRACKED;
            if ((mask & GEYSER) != 0 && geyser(r, x, z)) return VENT;
        } else if (y == 63) {
            if ((mask & CRUMBLE) != 0 && crumble(r, x, z)) return MAGMA;
        } else if ((mask & STALACTITE) != 0 && y < r.roof() && y >= r.roof() - 3 && r.inner(x + .5, z + .5) && stalactite(r, x, z)) {
            int hang = r.roof() - y;      // 1 at the roof itself
            if (hang > length(r, x, z)) return -1;
            // A broad head of stone, then thin points of mossy or plain cobblestone wall.
            return hang == 1 ? (r.theme == 48 ? 48 : 1 | (5 << 12)) : 139 | ((r.theme == 48 ? 1 : 0) << 12);
        }
        return -1;
    }
}
