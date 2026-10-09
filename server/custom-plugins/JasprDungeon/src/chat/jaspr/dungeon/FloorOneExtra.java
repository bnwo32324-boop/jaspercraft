package chat.jaspr.dungeon;

/**
 * Generation 7 (owner 2026-10-05: "2x the amount of content in the early and late game ... rooms, bosses, custom items, custom
 * mobs. Have them be totally original, and you can mix those in with the ChatGPT dungeons we have now"): Floor I's eighteen
 * new themes (indices 36..53; Layout.THEMES names them). Pure, like everything the generator calls. Each theme has
 *   - a palette (wall, floor, trim, timber, glass),
 *   - two signature set pieces (FloorOnePieces) drawn in some of a room's 7x7 bays, the rest keeping the room's motif,
 *   - an encounter entry (its boss, its pool, its signature pattern) and three favoured dangers (FloorOneEntries),
 *   - for a few themes (and a few of generation 6's fiery and ruined ones) a physical peril drawn into the room (FloorOnePerils).
 *
 * The generator calls block() from DungeonGenerator.classic for exactly the rooms draws() names; everything this class does not
 * return (-1) is drawn as the House of Mercy always drew it. Bays obey the contract of the motifs: a piece stays inside its
 * 7x7 bay, never touches a lane, the chest cell or a door, and rests on the floor or hangs from the roof. A room's first bay
 * (the one at tile cell 7,7) always keeps the room's motif, so the audits' reference silhouette is the same for every theme,
 * and one of the next three is always a piece, so every room shows its theme.
 */
final class FloorOneExtra {
    private FloorOneExtra() {}
    /** How many of the 18 new Floor I themes are ready. */
    static final int COUNT = 18;
    static final int BASE = Floors.FLOOR_ONE_EXTRA_BASE;
    /** The Unlit Nave (the last new theme): its roof, walls and chandeliers carry no lamps. */
    static final int UNLIT = BASE + 17;
    /** What a bay holds: the room's own motif, one of the theme's two signature pieces, or a peril (a lava pit, fractured flagstones). */
    static final int MOTIF = 0, PIECE_A = 1, PIECE_B = 2, PERIL = 3;

    private static int d(int id, int data) { return id | (data << 12); }
    /** Wall, floor, trim, timber, glass (packed legacy id | data << 12). Ordinary building stones, clays and woods: no metal, ore or gem blocks. */
    private static final int[][] PALETTES = {
        {4, d(1, 5), d(159, 1), d(5, 5), d(95, 1)},                  // 36 Bellfounder's Crypt: cobbled foundry, bronze-orange clay
        {d(159, 0), d(24, 2), d(159, 12), d(5, 2), d(95, 0)},        // 37 Moth Sanctum: dusty cream clay, brown wing-dust trim
        {d(5, 4), d(5, 2), d(159, 0), d(5, 1), d(95, 4)},            // 38 Candlewright Hall: panelled wax-white and honey
        {d(1, 6), d(5, 0), d(159, 15), d(5, 5), d(95, 7)},           // 39 Chained Library: polished andesite, black clay, dark oak
        {d(159, 3), d(155, 0), d(168, 1), d(5, 2), d(95, 3)},        // 40 Penitent Bathhouse: pale blue tile and quartz
        {d(1, 3), 98, 216, d(5, 2), 95},                             // 41 Hall of Effigies: diorite, stone brick, bone
        {48, d(3, 1), d(159, 14), d(5, 5), d(95, 14)},               // 42 Grave Market: mossy walls, trampled mud, red awning clay
        {d(159, 13), d(3, 2), d(17, 1), d(5, 3), d(95, 5)},          // 43 Weeping Orchard: olive clay, podzol, spruce logs
        {172, 4, d(159, 14), d(5, 5), d(95, 12)},                    // 44 Rusted Reliquary: terracotta, cobble, rust-red clay
        {d(159, 7), d(1, 5), d(159, 4), d(5, 1), d(95, 4)},          // 45 Lamplighter's Rest: gray street clay, lamp-yellow trim
        {45, d(1, 6), d(159, 7), d(5, 5), d(95, 14)},                // 46 Ashen Kitchens: soot-brick, ash-gray floor
        {d(159, 15), d(5, 5), d(159, 14), d(5, 1), d(95, 14)},       // 47 Mute Theatre: black walls, stage boards, velvet-red trim
        {48, d(98, 1), d(168, 2), d(5, 1), d(95, 9)},                // 48 Gutter Abbey: mossy cobble, mossy brick, dark prismarine
        {d(159, 10), d(155, 0), 201, d(5, 4), d(95, 10)},            // 49 Thurible Gallery: incense purple, quartz, purpur
        {d(159, 5), d(98, 1), d(17, 2), d(5, 2), d(95, 5)},          // 50 Hanging Gardens of Mercy: lime clay, mossy brick, birch
        {d(5, 0), d(24, 0), d(17, 0), d(5, 1), 95},                  // 51 Pilgrim's Hostel: oak boards, sandstone, oak logs
        {d(5, 1), d(1, 5), d(162, 1), d(5, 5), d(95, 12)},           // 52 Sexton's Workshop: spruce boards, andesite, dark oak logs
        {d(159, 15), d(159, 7), d(98, 3), d(5, 5), d(95, 11)}        // 53 The Unlit Nave: black clay, gray clay, carved bricks
    };

    static int[] palette(int theme) { int i = theme - BASE; return i >= 0 && i < COUNT ? PALETTES[i] : null; }
    static EncounterCatalog.Entry entry(int theme) { return FloorOneEntries.entry(theme); }
    static HazardCatalog.Type[] favoured(int theme) { return FloorOneEntries.favoured(theme); }
    /** Where this floor draws each physical peril (PerilMarks): the new fiery and ruined themes and a few of generation 6's. */
    static boolean peril(PerilMarks.Kind k, Layout.Room r, int x, int z) { return FloorOnePerils.at(k, r, x, z); }

    // ---------------------------------------------------------------- the generator's hook
    /** True when DungeonGenerator.classic must ask block() about this Floor I room: a new theme, or an old one with a peril. */
    static boolean draws(Layout.Room r) { return r.theme >= BASE && r.theme < BASE + COUNT || FloorOnePerils.mask(r.theme) != 0; }

    /**
     * The packed block this Floor I room has at (x, y, z) instead of the House's own, or -1 when the House's own stands.
     * The arrival room keeps its open floor and its light; refuges and every other room get the theme's pieces.
     */
    static int block(Layout.Room r, int x, int y, int z, int[] p) {
        if (r.kind == Layout.Kind.REFUGE && r.x == 0 && r.z == 0) return -1;
        int t = r.theme, pm = FloorOnePerils.mask(t);
        if (pm != 0) { int v = FloorOnePerils.block(r, x, y, z, pm); if (v >= 0) return v; }
        boolean extra = t >= BASE && t < BASE + COUNT;
        if ((extra || (pm & (FloorOnePerils.LAVA | FloorOnePerils.CRUMBLE)) != 0) && y >= 64 && y < r.roof()) {
            int lx = x - r.x, lz = z - r.z;
            if (lx >= 0 && lz >= 0) {
                int ax = r.x + (lx / 32) * 32 + (lx % 32 < 16 ? 7 : 25), az = r.z + (lz / 32) * 32 + (lz % 32 < 16 ? 7 : 25);
                int dx = x - ax, dz = z - az;
                if (dx >= -3 && dx <= 3 && dz >= -3 && dz <= 3) {
                    int kind = bayKind(r, ax, az);
                    if (kind != MOTIF) {
                        long h = bayHash(r, ax, az);
                        if (y == 64) {
                            // Accents painted into the floor never take a lamp of the lattice or a danger's mark.
                            if (kind == PERIL || !extra) return -1;
                            int f = FloorOnePieces.floor(t, kind - 1, orient(h), dx, dz, p);
                            return f != 0 && paintable(r, x, z) ? f : -1;
                        }
                        if (kind == PERIL) return FloorOnePerils.bayBlock(r, ax, az, h, dx, dz, y);
                        return FloorOnePieces.block(t, kind - 1, orient(h), dx, dz, y - 65, r.roof() - 66, p);
                    }
                }
            }
        }
        if (t == UNLIT) return dim(r, x, y, z, p);
        return -1;
    }

    /** A floor cell an accent may take: not a lamp of the eight-block lattice, not a danger's mark. */
    static boolean paintable(Layout.Room r, int x, int z) {
        return !(Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4) && HazardCatalog.floor(r, x, z) == 0;
    }

    // ---------------------------------------------------------------- the bays
    static long bayHash(Layout.Room r, int ax, int az) {
        return Layout.mix(r.hash ^ (ax * 0x9e3779b97f4a7c15L) ^ (az * 0xc2b2ae3d27d4eb4fL) ^ 0x62617973L);
    }
    /** Orientation of a bay's piece: bit 0 swaps the axes, bit 1 flips x and bit 2 flips z (after the swap). */
    static int orient(long bayHash) { return (int) ((bayHash >>> 20) & 7); }
    /** The bay centred on (ax, az) (a tile cell 7 or 25, in both axes): its motif, a signature piece or a peril. Pure: from the room alone. */
    static int bayKind(Layout.Room r, int ax, int az) {
        int t = r.theme, pm = FloorOnePerils.mask(t) & (FloorOnePerils.LAVA | FloorOnePerils.CRUMBLE);
        boolean extra = t >= BASE && t < BASE + COUNT;
        if (!extra && pm == 0) return MOTIF;
        if (r.kind == Layout.Kind.REFUGE && r.x == 0 && r.z == 0) return MOTIF;
        int lx = ax - r.x, lz = az - r.z;
        // The room's first bay always keeps its motif (the audits read their reference silhouette there).
        if (lx == 7 && lz == 7) return MOTIF;
        // The same bay safety as the motifs: a bay that a future parcel or lane change would crowd stays empty.
        if (ax - 3 < r.x + 2 || ax + 3 >= r.x + r.w - 2 || az - 3 < r.z + 2 || az + 3 >= r.z + r.d - 2
            || r.clearLane(ax - 3, az - 3) || r.clearLane(ax - 3, az + 3) || r.clearLane(ax + 3, az - 3) || r.clearLane(ax + 3, az + 3)) return MOTIF;
        long h = bayHash(r, ax, az);
        int roll = (int) Math.floorMod(h >>> 8, 100), kind;
        if (extra) {
            // Every room shows its theme: one of the three other bays of its first tile is always a piece.
            int forced = lx < 32 && lz < 32 ? (int) Math.floorMod(Layout.mix(r.hash ^ 0x666f726365L), 3) : -1;
            int slot = lx == 25 && lz == 7 ? 0 : lx == 7 && lz == 25 ? 1 : lx == 25 && lz == 25 ? 2 : -1;
            if (slot >= 0 && slot == forced) kind = ((h >>> 40) & 1) == 0 ? PIECE_A : PIECE_B;
            else if (pm != 0) kind = roll < 30 ? MOTIF : roll < 55 ? PIECE_A : roll < 80 ? PIECE_B : PERIL;
            else kind = roll < 35 ? MOTIF : roll < 68 ? PIECE_A : PIECE_B;
        } else kind = roll < 80 ? MOTIF : PERIL;
        // A refuge is safe: no pit, no fractured floor (and no rim round a pit that is not there).
        return kind == PERIL && (r.kind == Layout.Kind.REFUGE || !FloorOnePerils.usable(r, ax, az, h)) ? MOTIF : kind;
    }
    /** True when the bay centred on (ax, az) is not drawn with the room's motif. */
    static boolean special(Layout.Room r, int ax, int az) { return bayKind(r, ax, az) != MOTIF; }
    /** True when this column is drawn by this class (inside a special bay, or carrying a peril): the audits mask it when comparing with the House. */
    static boolean touched(Layout.Room r, int x, int z) {
        int lx = x - r.x, lz = z - r.z;
        if (lx >= 0 && lz >= 0) {
            int ax = r.x + (lx / 32) * 32 + (lx % 32 < 16 ? 7 : 25), az = r.z + (lz / 32) * 32 + (lz % 32 < 16 ? 7 : 25);
            if (Math.abs(x - ax) <= 3 && Math.abs(z - az) <= 3 && special(r, ax, az)) return true;
        }
        for (PerilMarks.Kind k : PerilMarks.Kind.values()) if (FloorOnePerils.at(k, r, x, z)) return true;
        return false;
    }

    // ---------------------------------------------------------------- the Unlit Nave
    /**
     * Owner's name for the last theme: no lamp hangs in its roof, walls, pillars or chandeliers. Its floor keeps the eight-block
     * lattice of lamps (every room has it) and its doorways their lintel lamps; the only other lights are the candles of its pews.
     */
    private static int dim(Layout.Room r, int x, int y, int z, int[] p) {
        int roof = r.roof(), rx = x - r.x, rz = z - r.z;
        if (y == roof) {
            int mark = HazardCatalog.roof(r, x, z);
            if (mark != 0) return mark;
            return Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0 ? p[2] : p[0];
        }
        if (y > roof) return -1;
        int tx = Math.floorMod(rx, 32), tz = Math.floorMod(rz, 32);
        if (y >= roof - 3 && (tx == 11 || tx == 20) && (tz == 11 || tz == 20) && !r.clearLane(x, z) && r.inner(x + .5, z + .5)) return 101;
        if (y == 67) {
            int along = HazardCatalog.along(r, x, z);
            if (along >= 0 && Math.floorMod(along, 8) == 0 && HazardCatalog.wall(r, x, y, z) == 0) return p[0];
        }
        // The pillars along the walls carry a lamp at head height.
        if (y == 68 && r.inner(x + .5, z + .5) && HazardCatalog.pillar(r, x, z)) return p[0];
        return -1;
    }
}
