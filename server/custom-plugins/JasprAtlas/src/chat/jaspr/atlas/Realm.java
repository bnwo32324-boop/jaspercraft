package chat.jaspr.atlas;

/**
 * The shape of Atlas, as pure functions of world coordinates (no seed: the realm's map is its history).
 *
 * <p>The Concord holds the west, the Dominion the east. The frontier wanders north-south near x = 0: west of it the
 * Lampwall (the Pharos Line) and the Concord's heartland; east of it the Wound, then the Dominion's provinces, laid out
 * around Anthrakion at (620, 0): the Ashen Marches outside the Teeth (a ring wall of radius 360), and inside it the
 * Petrified Weald (north), the Scorched Forges (south), the Fallen Cities (east), the inner Gate Road (west) and the
 * Plateau of Cinders at the centre. The Rim (the world border) is at +-1024.
 */
final class Realm {
    private Realm() {}

    static final int BORDER = 1024;
    static final int TX = 620, TZ = 0;             // Anthrakion
    static final int TEETH_R = 360, TEETH_W = 7;   // the ring wall's inner radius and thickness
    static final int PLATEAU_R = 110;
    static final int WOUND_EAST = 60;              // the Wound runs from the Lampwall to frontier + 60

    /** A province of the Dominion (liberation is tracked per province; bits for the liberation mask). */
    enum Province {
        MARCHES("the Ashen Marches", 1), WEALD("the Petrified Weald", 2), FORGES("the Scorched Forges", 4),
        FALLEN("the Fallen Cities", 8), PLATEAU("the Plateau of Cinders", 16);
        final String title;
        final int bit;
        Province(String title, int bit) { this.title = title; this.bit = bit; }
    }

    static final int ALL_LIBERATED = 31;

    enum Zone {
        CONCORD(null, true), LINE(null, true), WOUND(null, false), MARCHES(Province.MARCHES, false), TEETH(Province.MARCHES, false),
        GATE_ROAD(Province.MARCHES, false), WEALD(Province.WEALD, false), FORGES(Province.FORGES, false), FALLEN(Province.FALLEN, false),
        PLATEAU(Province.PLATEAU, false), RIM(null, false);
        final Province province;
        final boolean concord;
        Zone(Province province, boolean concord) { this.province = province; this.concord = concord; }
        boolean dominion() { return province != null; }
    }

    /** The frontier's centre line (x) at a given z: it wanders, but never far. */
    static double frontier(int z) { return 10 + 24 * Math.sin(z / 173.0) + 11 * Math.sin(z / 61.0 + 1.3); }

    /** The Lampwall's west face at z. */
    static int lineX(int z) { return (int) Math.round(frontier(z)) - 48; }

    static double teethDistance(int x, int z) { double dx = x - TX, dz = z - TZ; return Math.sqrt(dx * dx + dz * dz); }

    /** Angle around Anthrakion in degrees: 0 east, 90 south (+z), -90 north (-z), 180 west. */
    static double angle(int x, int z) { return Math.toDegrees(Math.atan2(z - TZ, x - TX)); }

    static Zone zone(int x, int z) {
        if (Math.abs(x) > BORDER || Math.abs(z) > BORDER) return Zone.RIM;
        int line = lineX(z);
        if (x < line - 3) return Zone.CONCORD;
        if (x <= line + 7) return Zone.LINE;
        double f = frontier(z);
        if (x < f + WOUND_EAST) return Zone.WOUND;
        double r = teethDistance(x, z);
        if (r < PLATEAU_R) return Zone.PLATEAU;
        if (r >= TEETH_R && r < TEETH_R + TEETH_W) return Zone.TEETH;
        if (r >= TEETH_R + TEETH_W) return Zone.MARCHES;
        // Inside the Teeth: sectors, their borders softened by a slow wobble so they are not ruler-straight.
        double a = angle(x, z) + 9 * Math.sin(r / 37.0) + 5 * Math.sin((x + z) / 53.0);
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        if (a >= -135 && a < -45) return Zone.WEALD;
        if (a >= 45 && a < 135) return Zone.FORGES;
        if (a >= -45 && a < 45) return Zone.FALLEN;
        return Zone.GATE_ROAD;
    }

    /** How far into the Wound (0 at the Lampwall, 1 at the Dominion's edge), for the grass-to-ash gradient. */
    static double woundDepth(int x, int z) {
        double from = lineX(z) + 7, to = frontier(z) + WOUND_EAST;
        return Math.max(0, Math.min(1, (x - from) / (to - from)));
    }

    // ------------------------------------------------------------------ fixed places (the story's map)

    /** A named place with a fixed centre: cities, strongholds and landmarks. */
    enum Place {
        GATE_OF_STRANGERS("the Gate of Strangers", -718, 0, 16, null),
        ASTREION("Astreion, the White City", -600, 0, 100, null),
        LAMPSA("Lampsa of the Lamps", -420, -380, 70, null),
        HIERANTHE("Hieranthe of the Gardens", -420, 380, 70, null),
        MNEMEIA("Mnemeia, the City of Memory", -840, -420, 64, null),
        LAST_WATCH("the Last Watch", -62, 0, 30, null),
        PYLON("the Pylon of Teeth", 260, 0, 34, Province.MARCHES),
        WARCAMP("Gorvash's War-Camp", 120, -430, 40, Province.MARCHES),
        RIDER_TOWER("the Rider's Tower", 170, 440, 22, Province.MARCHES),
        STILLED_GARDEN("the Stilled Garden", 620, -230, 40, Province.WEALD),
        SALLOW_HOUSE("Mother Sallow's House of Stilling", 520, -300, 22, Province.WEALD),
        GREAT_ENGINE("the Great Engine", 620, 230, 42, Province.FORGES),
        SLAG_QUARRY("the Slag Quarry", 720, 290, 30, Province.FORGES),
        FIEND_PIT("the Pit of the Hollow Fiend", 520, 290, 20, Province.FORGES),
        PELLENE("Pellene, the Silenced City", 850, 0, 80, Province.FALLEN),
        AIGAI("Aigai, the Emptied City", 820, -125, 44, Province.FALLEN),
        ANTHRAKION("Anthrakion", TX, TZ, 40, Province.PLATEAU),
        UZGAR_HIDE("Uzgar's Hide", 26, 44, 9, null),
        VESK_BURROW("Vesk's Burrow", 150, -250, 9, Province.MARCHES);
        final String title;
        final int x, z, radius;
        final Province province;
        Place(String title, int x, int z, int radius, Province province) { this.title = title; this.x = x; this.z = z; this.radius = radius; this.province = province; }
        boolean near(int wx, int wz, int pad) { return Math.abs(wx - x) <= radius + pad && Math.abs(wz - z) <= radius + pad; }
    }

    /** The Ward of each Ash-Crowned around Anthrakion (west Kallias, north Melaina, south Daidaros, east Keleos). */
    static final int WARD_R = 72;
    static int wardX(Province p) { return p == Province.MARCHES ? TX - WARD_R : p == Province.FALLEN ? TX + WARD_R : TX; }
    static int wardZ(Province p) { return p == Province.WEALD ? TZ - WARD_R : p == Province.FORGES ? TZ + WARD_R : TZ; }

    /** The place whose footprint covers (x, z), if any (for titles and protection). */
    static Place placeAt(int x, int z) {
        for (Place p : Place.values()) if (p.near(x, z, 0)) return p;
        return null;
    }
}
