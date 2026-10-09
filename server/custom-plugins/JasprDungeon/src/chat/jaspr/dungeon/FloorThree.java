package chat.jaspr.dungeon;

/**
 * Generation 7, Floor III: The Abyssal Citadel (themes 78..101), the late game and the hardest floor. Pure: the parcel
 * partition and the rooms' themes, kinds and threat; the blocks are FloorThreeBlocks (a citadel over a lava sea), the
 * looks FloorThreeStyle and the encounters FloorThreeCatalog.
 *
 * Owner 2026-10-05: "each tier should have completely different room layouts, setups, and themes for their rooms. Each
 * tier progressively gets harder." Floor I cuts its parcels into small crypts; Floor III raises great halls (4 x 4),
 * rampart strips (4 x 1 and 1 x 4), tower pairs (2 x 2) and keeps (3 x 3) with ramparts around them, and gives each shape
 * the themes that suit it (a hall for the colosseum, a strip for the throne approach, a tower for the spire). Every room
 * keeps the engine's contract: tiles of 32, doorways on the lanes, walkable floor at y 64. The arrival parcel holds the
 * four small refuges at tiles (0..1, 0..1) and three 2 x 2 rooms. Threat starts at 3 and climbs to 5 toward the Throne.
 */
final class FloorThree {
    private FloorThree() {}
    static final int BASE = Floors.FLOOR_THREE_BASE, COUNT = Floors.FLOOR_THREE_THEMES;

    /** Parcel partitions: rectangles {x, z, w, d} in tiles covering the 4 x 4 parcel exactly. */
    static final int[][] PARTITIONS = {
        {0, 0, 4, 4},                                          // a great hall
        {0, 0, 4, 4},
        {0, 0, 4, 1, 0, 1, 4, 1, 0, 2, 4, 1, 0, 3, 4, 1},      // ramparts running east-west
        {0, 0, 1, 4, 1, 0, 1, 4, 2, 0, 1, 4, 3, 0, 1, 4},      // ramparts running north-south
        {0, 0, 2, 2, 2, 0, 2, 2, 0, 2, 2, 2, 2, 2, 2, 2},      // two tower pairs
        {0, 0, 2, 2, 2, 0, 2, 2, 0, 2, 2, 2, 2, 2, 2, 2},
        {0, 0, 3, 3, 3, 0, 1, 4, 0, 3, 3, 1},                  // a keep in the north-west, ramparts east and south
        {1, 1, 3, 3, 0, 0, 1, 4, 1, 0, 3, 1},                  // a keep in the south-east
        {1, 0, 3, 3, 0, 0, 1, 4, 1, 3, 3, 1},                  // a keep in the north-east
        {0, 1, 3, 3, 0, 0, 4, 1, 3, 1, 1, 3},                  // a keep in the south-west
        {0, 0, 4, 2, 0, 2, 2, 2, 2, 2, 2, 2},                  // a hall over two towers
        {0, 0, 2, 2, 2, 0, 2, 2, 0, 2, 4, 2},                  // two towers over a hall
        {0, 0, 1, 4, 1, 0, 2, 4, 3, 0, 1, 4},                  // a gatehouse: a long hall between two ramparts
        {0, 0, 4, 1, 0, 1, 4, 2, 0, 3, 4, 1}                   // a bailey: a hall between two ramparts
    };
    /** Themes that suit a room's shape (two rooms in three take one of these; the third any of the 24). */
    static final int[] HALLS = {81, 84, 85, 86, 87, 91, 92, 94, 95, 96}, STRIPS = {78, 79, 80, 84, 88, 89, 98, 101},
        TOWERS = {82, 83, 88, 90, 93, 97, 99, 100};

    static Layout.Room at(Layout l, int x, int z) {
        int tx = Math.floorDiv(x, Layout.TILE), tz = Math.floorDiv(z, Layout.TILE), px = Math.floorDiv(tx, 4), pz = Math.floorDiv(tz, 4);
        int lx = Math.floorMod(tx, 4), lz = Math.floorMod(tz, 4), ax, az, w, d;
        boolean refuge = px == 0 && pz == 0 && lx < 2 && lz < 2;
        if (refuge) { ax = tx; az = tz; w = d = 1; }
        else if (px == 0 && pz == 0) { ax = (lx / 2) * 2; az = (lz / 2) * 2; w = d = 2; }
        else {
            int[] cut = PARTITIONS[(int) Math.floorMod(l.hash(px, pz, 0x7703L), PARTITIONS.length)];
            int i = 0;
            while (i + 4 < cut.length && !(lx >= cut[i] && lx < cut[i] + cut[i + 2] && lz >= cut[i + 1] && lz < cut[i + 1] + cut[i + 3])) i += 4;
            ax = px * 4 + cut[i]; az = pz * 4 + cut[i + 1]; w = cut[i + 2]; d = cut[i + 3];
        }
        long h = l.hash(ax, az, 19);
        int tiles = w * d, theme = refuge ? BASE + (int) Math.floorMod(h, COUNT) : theme(h, w, d);
        int roll = (int) Math.floorMod(h >>> 8, 100);
        Layout.Kind kind = roll < 8 ? Layout.Kind.TREASURE : roll < 14 ? Layout.Kind.SHRINE : roll < 34 ? Layout.Kind.GAUNTLET : Layout.Kind.BATTLE;
        // Floor III fights more bosses: a third of its rooms of four tiles or more.
        if (tiles >= 4 && roll >= 34 && roll < 66) kind = Layout.Kind.BOSS;
        if (refuge) kind = Layout.Kind.REFUGE;
        int distance = (int) Math.floor(3 * Floors.depth(Math.floorDiv(ax + w / 2, 4), Math.floorDiv(az + d / 2, 4)));
        int tier = kind == Layout.Kind.REFUGE ? 0
            : Math.min(5, 3 + (distance >= 3 ? 2 : distance >= 1 ? 1 : 0) + (kind == Layout.Kind.GAUNTLET || tiles >= 9 ? 1 : 0));
        return new Layout.Room(ax * Layout.TILE, az * Layout.TILE, w * Layout.TILE, d * Layout.TILE, theme,
            (int) Math.floorMod(h >>> 16, Layout.MOTIF_COUNT), tier, kind, h, 3);
    }
    /** A room's theme: two times in three one that suits its shape (strip, tower or hall), otherwise any of the 24. */
    static int theme(long h, int w, int d) {
        if (Math.floorMod(h >>> 32, 3) == 0) return BASE + (int) Math.floorMod(h, COUNT);
        int[] group = Math.min(w, d) == 1 ? STRIPS : w == d && w <= 3 ? TOWERS : HALLS;
        return group[(int) Math.floorMod(h >>> 40, group.length)];
    }

    static int block(Layout.Room r, int x, int y, int z) { return FloorThreeBlocks.block(r, x, y, z); }
    static EncounterCatalog.Entry entry(int theme) { return FloorThreeCatalog.entry(theme); }
    static HazardCatalog.Type[] favoured(int theme) { return FloorThreeCatalog.favoured(theme); }
    /** Where this floor draws each physical peril (PerilMarks): exactly the columns FloorThreeBlocks draws it in. */
    static boolean peril(PerilMarks.Kind k, Layout.Room r, int x, int z) { return FloorThreeBlocks.peril(k, r, x, z); }
}
