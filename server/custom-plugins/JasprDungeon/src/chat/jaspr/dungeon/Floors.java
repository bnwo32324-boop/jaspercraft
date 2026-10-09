package chat.jaspr.dungeon;

/**
 * Generation 7 (owner 2026-10-05): "you should be able to go to different tiers, just like in The Binding of Isaac ...
 * three tiers in total, and each tier should have completely different room layouts, setups, and themes for their rooms.
 * Each tier progressively gets harder." Pure (no Bukkit): which world slot holds which floor, their names and seeds, the
 * themes each floor draws from, where each floor's Descent (or the Throne) lies, how many absolved rooms open it, and how
 * much harder every floor is.
 *
 * World slots of a run: 0 is Floor I (the run's own world), 1..3 are Floor I's rift pockets (generation 6, unchanged),
 * 4 is Floor II and 5 is Floor III. A floor's world is made the first time someone descends; floors are one-way.
 */
public final class Floors {
    private Floors() {}
    public static final int FLOOR_TWO = 4, FLOOR_THREE = 5, SLOTS = 6;
    /** Theme indices: Floor I keeps generation 6's 36 (0..35) and adds 18 (36..53); Floor II 54..77; Floor III 78..101. */
    public static final int FLOOR_ONE_EXTRA_BASE = 36, FLOOR_TWO_BASE = 54, FLOOR_THREE_BASE = 78, THEME_TOTAL = 102;
    public static final int FLOOR_TWO_THEMES = 24, FLOOR_THREE_THEMES = 24;
    /** The Descent (Floors I and II) and the Throne (Floor III): a whole parcel this far from the origin parcel (Chebyshev). */
    public static final int DESCENT_DISTANCE = 3;

    /** 1, 2 or 3 for a world slot (Floor I's rift pockets belong to Floor I). */
    public static int floor(int realm) { return realm == FLOOR_TWO ? 2 : realm == FLOOR_THREE ? 3 : 1; }
    /** The world slot of a floor. */
    public static int slot(int floor) { return floor == 2 ? FLOOR_TWO : floor == 3 ? FLOOR_THREE : 0; }
    public static boolean rift(int realm) { return realm >= 1 && realm <= RiftCatalog.COUNT; }
    public static boolean floorSlot(int realm) { return realm == 0 || realm == FLOOR_TWO || realm == FLOOR_THREE; }
    public static String worldName(String root, int realm) {
        if (realm == FLOOR_TWO) return root + "_f2";
        if (realm == FLOOR_THREE) return root + "_f3";
        return RiftCatalog.worldName(root, realm);
    }
    public static long seed(long root, int realm) {
        if (realm == 0) return root;
        if (rift(realm)) return RiftCatalog.seed(root, realm);
        if (realm == FLOOR_TWO || realm == FLOOR_THREE) return Layout.mix(root ^ (0x464c4f4f52L * realm) ^ 0x7f4a7c159e3779b9L);
        throw new IllegalArgumentException("Unknown world slot: " + realm);
    }
    public static String title(int floor) {
        return floor == 2 ? "The Underworks" : floor == 3 ? "The Abyssal Citadel" : "The House of Mercy";
    }
    public static String numeral(int floor) { return floor == 2 ? "II" : floor == 3 ? "III" : "I"; }

    // ---------------------------------------------------------------- themes
    /** How many themes Floor I draws from: generation 6's 36 plus the new ones that exist (FloorOneExtra.COUNT). */
    public static int floorOneThemes() { return FLOOR_ONE_EXTRA_BASE + FloorOneExtra.COUNT; }
    public static int themeBase(int floor) { return floor == 2 ? FLOOR_TWO_BASE : floor == 3 ? FLOOR_THREE_BASE : 0; }
    public static int themeCount(int floor) { return floor == 2 ? FLOOR_TWO_THEMES : floor == 3 ? FLOOR_THREE_THEMES : floorOneThemes(); }
    /** The floor a theme index belongs to. */
    public static int floorOfTheme(int theme) { return theme >= FLOOR_THREE_BASE ? 3 : theme >= FLOOR_TWO_BASE ? 2 : 1; }

    // ---------------------------------------------------------------- the Descent and the Throne
    /** The parcel (px, pz) of this floor's Descent or Throne: one of the 24 parcels on the ring at distance 3, by seed. */
    public static int[] descentParcel(long layoutSeed, int floor) {
        int pick = (int) Math.floorMod(Layout.mix(layoutSeed ^ 0x44657363656e74L ^ (floor * 0x9e3779b97f4a7c15L)), 24);
        int side = pick / 6, along = pick % 6 - 3; // along: -3..2, then shifted so the corners are shared fairly
        int d = DESCENT_DISTANCE;
        switch (side) {
            case 0: return new int[]{d, along + 1};
            case 1: return new int[]{-d, along};
            case 2: return new int[]{along, d};
            default: return new int[]{along + 1, -d};
        }
    }
    public static boolean descentParcel(long layoutSeed, int floor, int px, int pz) {
        int[] p = descentParcel(layoutSeed, floor);
        return p[0] == px && p[1] == pz;
    }
    /** Absolved rooms on a floor before its Descent (or the Throne) lets anyone in. */
    public static int clearsRequired(int floor) { return floor == 2 ? 6 : floor == 3 ? 7 : 5; }
    /** "north-east", "south" ... from (fromX, fromZ) toward (toX, toZ); north is -z. */
    public static String direction(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        if (Math.abs(dx) < 16 && Math.abs(dz) < 16) return "here";
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
    }

    // ---------------------------------------------------------------- difficulty
    /** 0 at the origin parcel, 1 at the Descent's distance and beyond. */
    public static double depth(int px, int pz) { return Math.min(1, Math.max(Math.abs(px), Math.abs(pz)) / (double) DESCENT_DISTANCE); }
    public static double depth(Layout.Room r) {
        return depth(Math.floorDiv(r.x + r.w / 2, Layout.PARCEL), Math.floorDiv(r.z + r.d / 2, Layout.PARCEL));
    }
    /** Health of everything on a floor, and its blows. */
    public static double health(int floor) { return floor == 2 ? 1.8 : floor == 3 ? 3.0 : 1.0; }
    public static double damage(int floor) { return floor == 2 ? 1.4 : floor == 3 ? 1.9 : 1.0; }
    /** Extra mobs in every room that has any. */
    public static int extraMobs(int floor) { return floor == 2 ? 2 : floor == 3 ? 4 : 0; }
    /** Boss ability cadence: a cooldown is multiplied by this. */
    public static double cadence(int floor) { return floor == 2 ? 0.8 : floor == 3 ? 0.65 : 1.0; }
    /** Loot budget multiplier. */
    public static double loot(int floor) { return floor == 2 ? 1.5 : floor == 3 ? 2.2 : 1.0; }
}
