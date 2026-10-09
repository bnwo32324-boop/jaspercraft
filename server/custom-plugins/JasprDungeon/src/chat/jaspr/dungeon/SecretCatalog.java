package chat.jaspr.dungeon;

import java.util.*;

/**
 * Generation 7 (owner 2026-10-05: "I also want surprises and Easter eggs to make exploring more fun."): which secrets a room
 * hides, and where. Pure (no Bukkit, no clocks, no random state): everything comes from the room's hash, so a seed always hides
 * the same things and the order rooms are generated or visited in never matters. Secrets makes them happen at runtime.
 *
 * Secrets: a loose stone with a small cache (about one room in twelve), a mimic in the reliquary (one treasure room in six), a
 * gilded thief who runs from you (one battle room in twenty, a little more on the lower floors), a lost peddler (one small room
 * in forty on Floors II and III) and rare one-off Easter egg rooms (eight kinds, about 3.5 rooms in a hundred, so roughly one
 * a run). Never in a refuge (the arrival circle must stay safe and empty), never in a Descent or the Throne, and well under half
 * as often in Floor I's first parcel, where nobody has had time to be afraid yet.
 *
 * Where: every secret that builds something takes a bay, one of the 7 x 7 cells between a room's lanes (the generator's
 * furnishing cells; lanes, the reliquary chest, doors and spawn stations are never inside one). A bay is "free" when the pure
 * generator gives it a solid floor and no physical peril of the floor marks it; the runtime then checks the real world too.
 */
public final class SecretCatalog {
    private SecretCatalog() {}

    public enum Kind {
        CACHE("secret cache"), MIMIC("mimic reliquary"), THIEF("gilded thief"), PEDDLER("lost peddler"),
        // one-off Easter egg rooms, in selection order
        DUCK("rubber duck shrine"), BASEMENT("Isaac's basement"), CAKE("cake chamber"), CRYPT("disc 13 crypt"),
        DESK("developers' desk"), JEB("jeb_ sheep"), DINNERBONE("Dinnerbone's guard"), SWORD("sword in the stone");
        public final String title;
        Kind(String title) { this.title = title; }
        public boolean egg() { return ordinal() >= DUCK.ordinal(); }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public static final int EGGS = 8;
        public static Kind egg(int n) { return values()[DUCK.ordinal() + Math.floorMod(n, EGGS)]; }
    }

    // ---------------------------------------------------------------- rates (per ten thousand rooms of the kind)
    /** One room in twelve. */
    public static final int CACHE_RATE = 833;
    /** One treasure room in six: the reliquary bites. */
    public static final int MIMIC_RATE = 1667;
    /** One battle room in twenty on Floor I; a little more below. */
    public static final int THIEF_RATE = 500, THIEF_RATE_DEEP = 650;
    /** One small room in forty on Floors II and III. */
    public static final int PEDDLER_RATE = 250;
    /** Any room, one of the eight Easter egg rooms: 3.5 in a hundred, so about one egg for every thirty rooms of a run. */
    public static final int EGG_RATE = 350;
    /** Thousandths of a rate left in Floor I's first parcel (the origin parcel). */
    public static final int FIRST_PARCEL_PERMILLE = 400;
    /** The thief runs for this long, then laughs and is gone (25 seconds). */
    public static final int THIEF_LIFE_TICKS = 500, THIEF_DELAY_TICKS = 60;
    /** A small room: one or two tiles of floor (the Citadel's rooms are never under 64 x 64, so there its smallest, 4096, are small). */
    public static final int SMALL_AREA = 2048, SMALL_AREA_CITADEL = 4096;

    static final long S_CACHE = 0x5365637265744341L, S_MIMIC = 0x4d696d6963526571L, S_THIEF = 0x4769446c64546866L, S_PEDDLER = 0x5065646c6572526dL,
        S_EGG = 0x4561737465724567L, S_EGG_KIND = 0x4567674b696e6421L, S_LORE = 0x4c6f72654c696e65L,
        S_BAY_EGG = 0x4261794567676b73L, S_BAY_CAMP = 0x4261794361657270L, S_BAY_STONE = 0x4261795374306e65L, S_CELL = 0x43656c6c4f726472L, S_RELIC = 0x52656c6963506b21L;

    /** One independent decision stream per salt, from the room's hash alone. */
    static long stream(Layout.Room r, long salt) { return Layout.mix(r.hash ^ salt); }
    private static boolean roll(Layout.Room r, long salt, int perTenThousand) { return Math.floorMod(stream(r, salt), 10000L) < perTenThousand; }

    // ---------------------------------------------------------------- who may hide anything
    /** Never a refuge (its arrival circle is safe and empty), never a Descent or the Throne (their guardians are the whole show). */
    public static boolean eligible(Layout.Room r) { return r != null && r.kind != Layout.Kind.REFUGE && !r.finale(); }
    /** Floor I's origin parcel, where a run begins. */
    public static boolean firstParcel(Layout.Room r) { return r.floor == 1 && Math.floorDiv(r.x, Layout.PARCEL) == 0 && Math.floorDiv(r.z, Layout.PARCEL) == 0; }
    public static boolean small(Layout.Room r) { return r.w * r.d <= (r.floor >= 3 ? SMALL_AREA_CITADEL : SMALL_AREA); }
    private static boolean peddlerKind(Layout.Kind k) { return k == Layout.Kind.BATTLE || k == Layout.Kind.GAUNTLET || k == Layout.Kind.SHRINE || k == Layout.Kind.TREASURE; }

    /** The chance (per ten thousand) that this room has this secret, after the first-parcel discount and the tuning scale. Eggs: see eggRate. */
    public static int rate(Kind k, Layout.Room r, double scale) {
        if (!eligible(r) || k == null) return 0;
        int base;
        switch (k) {
            case CACHE: base = CACHE_RATE; break;
            case MIMIC: base = r.kind == Layout.Kind.TREASURE ? MIMIC_RATE : 0; break;
            case THIEF: base = r.kind == Layout.Kind.BATTLE ? (r.floor == 1 ? THIEF_RATE : THIEF_RATE_DEEP) : 0; break;
            case PEDDLER: base = r.floor >= 2 && small(r) && peddlerKind(r.kind) ? PEDDLER_RATE : 0; break;
            default: base = EGG_RATE / Kind.EGGS;
        }
        return tune(base, r, scale);
    }
    public static int rate(Kind k, Layout.Room r) { return rate(k, r, 1); }
    /** The chance that this room is an Easter egg room of any kind. */
    public static int eggRate(Layout.Room r, double scale) { return eligible(r) ? tune(EGG_RATE, r, scale) : 0; }
    private static int tune(int base, Layout.Room r, double scale) {
        double v = base * (firstParcel(r) ? FIRST_PARCEL_PERMILLE / 1000.0 : 1) * Math.max(0, scale);
        return (int) Math.min(10000, Math.round(v));
    }

    // ---------------------------------------------------------------- the plan
    /** What one room hides. Equal plans are equal values (determinism is audited). */
    public static final class Plan {
        public final boolean cache, mimic, thief, peddler;
        /** One of the eight Easter egg kinds, or null. */
        public final Kind egg;
        Plan(boolean cache, boolean mimic, boolean thief, boolean peddler, Kind egg) { this.cache = cache; this.mimic = mimic; this.thief = thief; this.peddler = peddler; this.egg = egg; }
        public boolean any() { return cache || mimic || thief || peddler || egg != null; }
        public boolean has(Kind k) { return k == Kind.CACHE ? cache : k == Kind.MIMIC ? mimic : k == Kind.THIEF ? thief : k == Kind.PEDDLER ? peddler : egg == k; }
        public List<Kind> kinds() {
            List<Kind> out = new ArrayList<Kind>();
            if (cache) out.add(Kind.CACHE); if (mimic) out.add(Kind.MIMIC); if (thief) out.add(Kind.THIEF); if (peddler) out.add(Kind.PEDDLER); if (egg != null) out.add(egg);
            return out;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Plan)) return false;
            Plan p = (Plan) o;
            return cache == p.cache && mimic == p.mimic && thief == p.thief && peddler == p.peddler && egg == p.egg;
        }
        @Override public int hashCode() { return (cache ? 1 : 0) | (mimic ? 2 : 0) | (thief ? 4 : 0) | (peddler ? 8 : 0) | ((egg == null ? 0 : egg.ordinal() + 1) << 4); }
        @Override public String toString() { List<Kind> k = kinds(); if (k.isEmpty()) return "none"; StringBuilder b = new StringBuilder(); for (Kind x : k) { if (b.length() > 0) b.append(','); b.append(x.id()); } return b.toString(); }
    }
    static final Plan NONE = new Plan(false, false, false, false, null);

    public static Plan plan(Layout.Room r) { return plan(r, 1); }
    /** scale tunes every rate at once (config secrets-rate: 0 hides nothing, 1 is the design). */
    public static Plan plan(Layout.Room r, double scale) {
        if (!eligible(r) || scale <= 0) return NONE;
        boolean cache = roll(r, S_CACHE, rate(Kind.CACHE, r, scale)), mimic = roll(r, S_MIMIC, rate(Kind.MIMIC, r, scale)),
            thief = roll(r, S_THIEF, rate(Kind.THIEF, r, scale)), peddler = roll(r, S_PEDDLER, rate(Kind.PEDDLER, r, scale));
        Kind egg = roll(r, S_EGG, eggRate(r, scale)) ? Kind.egg((int) Math.floorMod(stream(r, S_EGG_KIND), (long) Kind.EGGS)) : null;
        // An egg's build, the peddler's camp and the loose stone each need a whole free bay of their own, claimed in the order the room
        // places them (Secrets.place): a room that runs out of bays (a Floor III platform over the lava sea) never promises the rest.
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        if (egg != null) { int b = pick(r, bayOrder(r, egg), used); if (b < 0) egg = null; else used.add(b); }
        if (peddler) { int b = pick(r, bayOrder(r, Kind.PEDDLER), used); if (b < 0) peddler = false; else used.add(b); }
        if (cache) { int b = pick(r, bayOrder(r, Kind.CACHE), used); if (b < 0) cache = false; else used.add(b); }
        return cache || mimic || thief || peddler || egg != null ? new Plan(cache, mimic, thief, peddler, egg) : NONE;
    }

    /**
     * The nearest room (by rings of tiles around the origin, within maxTiles) of this layout that hides this kind of secret, or null.
     * For a live test or a probe that wants to walk to one; the first parcel is passed over (its secrets are rare by design).
     */
    public static Layout.Room nearest(Layout layout, Kind kind, int maxTiles) {
        for (int n = 0; n <= maxTiles; n++) for (int x = -n; x <= n; x++) for (int z = -n; z <= n; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != n) continue;
            Layout.Room r = layout.at(x * Layout.TILE + Layout.TILE / 2, z * Layout.TILE + Layout.TILE / 2);
            if (eligible(r) && !firstParcel(r) && plan(r).has(kind)) return r;
        }
        return null;
    }

    // ---------------------------------------------------------------- bays: the 7 x 7 cells between the lanes
    /** A bay is this many cells either side of its centre. */
    public static final int HALF = SecretBuilds.HALF;
    /** How many bays a room has: four to a tile, in the generator's own grid (DungeonGenerator.decoration). */
    public static int bays(Layout.Room r) { return r.w / Layout.TILE * (r.d / Layout.TILE) * 4; }
    /** The centre cell {x, z} of bay i: tiles in x-major order, four bays to a tile (centres 7 and 25 of the tile). */
    public static int[] bay(Layout.Room r, int i) {
        int nz = r.d / Layout.TILE, tile = i / 4, q = i % 4, tx = tile / nz, tz = tile % nz;
        return new int[]{r.x + tx * Layout.TILE + ((q & 1) == 0 ? 7 : 25), r.z + tz * Layout.TILE + ((q & 2) == 0 ? 7 : 25)};
    }
    /** A permutation of 0..n-1, the same for the same seed. */
    static int[] permutation(long seed, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = i;
        long s = seed;
        for (int i = n - 1; i > 0; i--) {
            s = Layout.mix(s + 0x9e3779b97f4a7c15L);
            int j = (int) Math.floorMod(s, (long) (i + 1)), t = out[i]; out[i] = out[j]; out[j] = t;
        }
        return out;
    }
    /** The order in which a secret tries the room's bays. */
    public static int[] order(Layout.Room r, long salt) { return permutation(stream(r, salt), bays(r)); }
    public static int[] bayOrder(Layout.Room r, Kind k) { return order(r, k == Kind.CACHE ? S_BAY_STONE : k == Kind.PEDDLER ? S_BAY_CAMP : S_BAY_EGG); }
    /** The order in which a loose stone tries the 49 cells of its bay (cell c is dx = c % 7 - 3, dz = c / 7 - 3). */
    public static int[] cells(Layout.Room r) { return permutation(stream(r, S_CELL), 49); }
    /** How many quarter turns a build takes so that its front faces the lanes: bays in the south half of their tile face north. */
    public static int turn(Layout.Room r, int bayZ) { return Math.floorMod(bayZ - r.z, Layout.TILE) >= 16 ? 2 : 0; }

    /** True for a floor block a bay may stand on: solid, not a liquid, magma, soul sand or a cactus. */
    static boolean solid(int packed) {
        int id = packed & 4095;
        return id != 0 && id != 8 && id != 9 && id != 10 && id != 11 && id != 81 && id != 88 && id != 213 && id != 78 && id != 171 && id != 31 && id != 175;
    }
    /**
     * A bay no peril or hole spoils: all 49 columns inside the room, off every lane, outside the arrival circle, standing on a solid
     * floor in the pure generator, and not under or in any physical peril of the floor (PerilMarks).
     */
    public static boolean free(Layout.Room r, int ax, int az) {
        if (r == null || ax - HALF < r.x + 2 || ax + HALF >= r.x + r.w - 2 || az - HALF < r.z + 2 || az + HALF >= r.z + r.d - 2) return false;
        for (int x = ax - HALF; x <= ax + HALF; x++) for (int z = az - HALF; z <= az + HALF; z++) {
            if (r.clearLane(x, z) || HazardCatalog.safe(r, x + .5, z + .5)) return false;
            if (!solid(DungeonGenerator.block(r, x, Layout.FLOOR, z))) return false;
            for (PerilMarks.Kind k : PerilMarks.Kind.values()) if (PerilMarks.at(k, r, x, z)) return false;
        }
        return true;
    }
    /** The first free bay of an order that is not in used, or -1. */
    public static int pick(Layout.Room r, int[] order, Set<Integer> used) {
        for (int i : order) { if (used.contains(i)) continue; int[] b = bay(r, i); if (free(r, b[0], b[1])) return i; }
        return -1;
    }

    // ---------------------------------------------------------------- the thief's running (pure, so the audit can check where it goes)
    /** How many of the room's spawn stations (spread over the lanes, the first ones farthest apart) the thief considers. */
    public static final int THIEF_STATIONS = 32;
    /** Never run to a station this close to any player, and never further than this from where the thief stands. */
    public static final double THIEF_SAFE_DISTANCE = 7, THIEF_REACH = 28;
    /** The station {x, z} where a thief starts: the one farthest from every player (null when there are no players). */
    public static double[] thiefStart(Layout.Room r, double[][] players) {
        if (players == null || players.length == 0) return null;
        double[] best = null; double far = -1;
        for (int slot = 0; slot < THIEF_STATIONS; slot++) {
            double[] c = EncounterCatalog.spawnPoint(r, slot);
            double near = nearest(players, c[0], c[2]);
            if (near > far) { far = near; best = new double[]{c[0], c[2]}; }
        }
        return best;
    }
    /**
     * The station {x, z} the thief runs for from (mx, mz): reachable (within THIEF_REACH), at least THIEF_SAFE_DISTANCE from every
     * player, preferring the farthest from the players and, among equals, the nearest to itself. Null when none qualifies.
     */
    public static double[] thiefGoal(Layout.Room r, double mx, double mz, double[][] players) {
        double[] best = null; double score = -1e9;
        for (int slot = 0; slot < THIEF_STATIONS; slot++) {
            double[] c = EncounterCatalog.spawnPoint(r, slot);
            double dm = Math.hypot(c[0] - mx, c[2] - mz);
            if (dm > THIEF_REACH || dm < 2) continue;
            double dp = nearest(players, c[0], c[2]);
            if (dp < THIEF_SAFE_DISTANCE) continue;
            double v = dp - .25 * dm;
            if (v > score) { score = v; best = new double[]{c[0], c[2]}; }
        }
        return best;
    }
    private static double nearest(double[][] players, double x, double z) {
        double d = 1e9;
        for (double[] p : players) d = Math.min(d, Math.hypot(p[0] - x, p[1] - z));
        return d;
    }

    // ---------------------------------------------------------------- numbers (health and damage are final values, like Encounters.summon's)
    private static int threat(Layout.Room r) { return EncounterCatalog.threat(r.tier); }
    public static double mimicHealth(Layout.Room r) { return (36 + threat(r) * 12) * Floors.health(r.floor); }
    public static double mimicDamage(Layout.Room r) { return (2.5 + threat(r) * .7) * Floors.damage(r.floor); }
    /** Monstro is a giant slime: a mini-boss that comes out of the Basement's hatch. */
    public static double monstroHealth(Layout.Room r) { return (54 + threat(r) * 16) * Floors.health(r.floor); }
    public static double monstroDamage(Layout.Room r) { return (3 + threat(r) * .8) * Floors.damage(r.floor); }
    public static final int MONSTRO_SIZE = 4;
    public static double guardHealth(Layout.Room r) { return (30 + threat(r) * 10) * Floors.health(r.floor); }
    public static double guardDamage(Layout.Room r) { return (3 + threat(r) * .75) * Floors.damage(r.floor); }
    /** The thief is fragile (it is quick, not tough) and harmless. */
    public static double thiefHealth(Layout.Room r) { return 16 + 4 * r.floor; }

    // ---------------------------------------------------------------- the peddler's wares (prices in emeralds and bottles o' enchanting)
    public static final class Trade {
        public final String id; public final int emeralds, bottles, gets, uses;
        Trade(String id, int emeralds, int bottles, int gets, int uses) { this.id = id; this.emeralds = emeralds; this.bottles = bottles; this.gets = gets; this.uses = uses; }
    }
    /** Healing, arrows, gun ammunition (iron nuggets), and a relic for emeralds and experience bottles. */
    public static final Trade[] TRADES = {
        new Trade("healing", 3, 0, 1, 3), new Trade("arrows", 1, 0, 16, 4), new Trade("ammo", 1, 0, 32, 4), new Trade("relic", 8, 10, 1, 1)
    };
    /** The peddler on a deeper floor has a little more of everything but the relic. */
    public static int uses(Trade t, int floor) { return t.id.equals("relic") ? t.uses : t.uses + (floor >= 3 ? 1 : 0); }

    // ---------------------------------------------------------------- lore (one line with a cache)
    static final String[] LORE_1 = {
        "Scratched into the stone: 'The keepers counted prayers. They never counted the ones who prayed.'",
        "A child's tally marks, hundreds of them, and one name scratched out and written again.",
        "Behind the loose stone, a note: 'Mercy is a door. Someone has to hold it open.'",
        "A candle stub, still warm. Whoever left it was here a moment ago, or never left.",
        "Pressed between two bricks, a ribbon and a line of text: 'We were not the ones who locked it.'",
        "Someone hid this for a friend. The friend's name is worn away; the care is not.",
        "A pilgrim's mark, three strokes and a circle. It means 'rest here, and go on'.",
        "The mortar is soft here. Someone dug this alcove with a spoon, patiently, for years.",
        "A scrap of hymn: 'Open the hand, open the door, open the ledger of the poor.'",
        "The keepers sealed the chapel. A cook left one stone loose behind it, and bread inside."
    };
    static final String[] LORE_2 = {
        "A miner's tally: 'Day 41. The lamps still burn. Nobody asked what they burn on.'",
        "A hollow behind the rock and a flask of oil: a lamplighter's emergency stash.",
        "Scratched beside the stone: 'Ore is a rumor down here. The echoes are honest.'",
        "A chalk arrow pointing back the way you came, and the word 'later'.",
        "Someone cached this before the lower galleries collapsed. They never came back for it.",
        "A snapped pickaxe haft and a note: 'The deeper the quiet, the louder the dark.'",
        "A foreman's dusty reminder: 'Count your crew when you surface. Count again.'",
        "A crude map pinned under the stone: every tunnel leads to the same wide room.",
        "Rations in a tin and a message: 'If you find this, you are further than I got.'",
        "A cave-in kept this safe. The next one will not, so take it."
    };
    static final String[] LORE_3 = {
        "Etched in ash: 'The Sovereign never sleeps. The Sovereign only waits.'",
        "A soldier's keepsake wedged in the rampart: 'Tell them the wall held. Tell them anyway.'",
        "A sliver of the abyss gleams behind the stone. Whoever hid this looked away from it.",
        "Written in soot: 'They crowned the fire. The fire did not ask.'",
        "A deserter's pouch and a note: 'I ran. Use what I could not carry.'",
        "Cut into the brick: 'Forty steps to the gate. Forty-one coming back.'",
        "The abyss hums behind this stone. Whoever hid the cache used the hum to cover their steps.",
        "A herald's last message: 'The throne is not the end. The throne is the door.'",
        "Someone scorched a smile into the mortar. It is the only kind thing in this hall.",
        "A knight's oath, half burned: 'I will hold until relieved. Relieve me.'"
    };
    public static String[] loreSet(int floor) { return floor >= 3 ? LORE_3 : floor == 2 ? LORE_2 : LORE_1; }
    public static String lore(Layout.Room r) { String[] set = loreSet(r.floor); return set[(int) Math.floorMod(stream(r, S_LORE), (long) set.length)]; }
    /** Which bauble of the room's pool a cache, purse or peddler hands over (a number to take modulo the pool's size). */
    public static long relicDraw(Layout.Room r, int n) { return stream(r, S_RELIC + n); }

    // ---------------------------------------------------------------- diagnostics (pure, so the audit can trust them)
    /** One log line: "DUNGEON_SECRET_<EVENT> kind=... room=... floor=..." plus a player's name and extras when given. Never more than ids, floors and names. */
    public static String line(String event, Kind k, String room, int floor, String player, String extra) {
        StringBuilder b = new StringBuilder(event).append(" kind=").append(k == null ? "-" : k.id()).append(" room=").append(room).append(" floor=").append(floor);
        if (player != null) b.append(" player=").append(player);
        if (extra != null && !extra.isEmpty()) b.append(' ').append(extra);
        return b.toString();
    }
    /** The line logged when the plugin closes: counts per kind of what was planned, placed, found and used. */
    public static String metrics(int rooms, int[] planned, int[] placed, int[] found, int[] used, String tail) {
        StringBuilder p = new StringBuilder(), l = new StringBuilder(), f = new StringBuilder(), u = new StringBuilder();
        for (Kind k : Kind.values()) {
            int i = k.ordinal();
            if (i > 0) { p.append(','); l.append(','); f.append(','); u.append(','); }
            p.append(k.id()).append(':').append(planned[i]); l.append(k.id()).append(':').append(placed[i]); f.append(k.id()).append(':').append(found[i]); u.append(k.id()).append(':').append(used[i]);
        }
        return "DUNGEON_SECRET_METRICS rooms=" + rooms + " planned=" + p + " placed=" + l + " found=" + f + " used=" + u + (tail == null || tail.isEmpty() ? "" : " " + tail);
    }
}
