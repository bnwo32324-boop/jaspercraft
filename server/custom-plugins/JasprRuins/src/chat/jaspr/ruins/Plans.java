package chat.jaspr.ruins;

import chat.jaspr.biomes.Terrain;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the original ruins go: one possible old city per 320-block cell and one candidate ruin per 40-block cell, each a
 * pure function of the seed and the terrain. Anything that would touch land the Lost Cities build (their cities, highways
 * and the ring around them) is left out, so the two kinds of ruins never overlap.
 *
 * Epoch 5 (owner 2026-10-04: "more dense with dungeons and structures. make new ones. 2x it"): eleven new kinds (four
 * of them out in the drowned shallows, three of them dungeons), and twice the ruins of epoch 4 per square kilometre.
 * Epoch 4 kept ruins apart with wide cell margins (56-block cells, 23-block margins), which caps how closely they can
 * stand; now every 40-block cell holds a candidate, dry land takes a land kind and shallow water a drowned one, and a
 * candidate whose footprint (plus a {@link #SITE_PAD}-block gap) meets a neighbour's yields to the one that ranks first
 * (Warden arenas, then the wider ruin, then by hash), so no two ruins ever overlap.
 *
 * Epoch 6 (owner 2026-10-04: "In the Drown Hollow dimension, make 20 new big structures there as well. They all should be
 * unique and have bosses, and they should include all types of mobs and custom mobs"): the great structures, one
 * candidate per 256-block cell ({@link Great}, drawn by {@link Greats}), each up to some 110 blocks across with its own
 * boss (see {@link Bosses}) and garrisons of every horror and every kind of monster (see {@link Garrisons}). They are
 * planned before the ruins and the field, which keep clear of them.
 */
final class Plans {
    /** Whether a block footprint touches land the Lost Cities build (CityApi.reserved in the plugin). */
    interface Reserved { boolean test(int blockX, int blockZ, int width, int depth); }

    static final int SEA = 62;
    static final int CITY_GRID = 320, CITY_MARGIN = 112, BLEND = 20;
    static final double CITY_CHANCE = 0.45;
    static final int SITE_GRID = 40, SITE_MARGIN = 8, SITE_PAD = 2;
    static final double SITE_CHANCE = 1.0;
    /** Drowned ruins stand where the sea floor at their centre lies this many blocks under the surface. */
    static final int WET_MIN = 3, WET_MAX = 24;
    static final int CELL = 24, DOOR_RADIUS = 48, DISTRICT = 128;
    private static final long CITY_SALT = 0x43697479L, SITE_SALT = 0x53697465L;
    private static final int CACHE = 8192;

    static final int CULT = 1, NAMED = 2, DUNGEON = 4, WET = 8;

    /**
     * The twenty great structures of epoch 6, in their lattice order (a cell's preferred kind is its slot (i + 5j) mod 20,
     * turned by the seed, so any five cells in a row, or four in a column, prefer twenty different kinds). Each names its
     * boss (a {@link Bosses.Boss}); a spot of the wrong kind of ground takes a kind of that ground. Four stand in the shallows or on the shores; the Weeping
     * Cistern floods itself underground.
     */
    enum Great {
        NECROPOLIS("The Necropolis of the Ghoul-Kings", 52, false, "GHOUL_KING"),
        CATHEDRAL("The Drowned Cathedral", 48, true, "DROWNED_BISHOP"),
        SLEEPER("The Fallen Sleeper", 50, false, "SLEEPERS_AVATAR"),
        STAR_TOWER("The Tower of Silent Stars", 36, false, "STAR_PRIEST"),
        SHOGGOTH_VATS("The Shoggoth Vats", 46, false, "ELDER_SHOGGOTH"),
        DREADNOUGHT("The Dreadnought", 56, true, "DROWNED_ADMIRAL"),
        MIGO_HIVE("The Hive of the Mi-Go", 44, false, "MIGO_OVERSEER"),
        TINDALOS("The Angles of Tindalos", 46, false, "TINDALOS_ALPHA"),
        BLACK_GOAT("The Temple of the Black Goat", 50, false, "DARK_YOUNG"),
        BEACON("The Beacon of R'lyeh", 40, true, "LAMPLIGHTER"),
        VIADUCT("The Viaduct of the Drowned Kings", 56, false, "TOLL_KEEPER"),
        CELAENO("The Library of Celaeno", 42, false, "LIBRARIAN"),
        TERRACES("The Terraces of the Drowned Queen", 50, false, "DROWNED_QUEEN"),
        BASTION("Y'ha-nthlei, Bastion of the Deep", 52, true, "DEEP_WARLORD"),
        SILVER_GATE("The Gate of the Silver Key", 46, false, "GATE_GUARDIAN"),
        LENG("The Monastery of Leng", 50, false, "HIGH_PRIEST"),
        ELDER_VAULT("The Vault of the Elder Sign", 44, false, "ELDER_THING"),
        CISTERN("The Weeping Cistern", 44, false, "CISTERN_GORGON"),
        ORRERY("The Orrery of Aeons", 42, false, "KEEPER_OF_AEONS"),
        GOLGOTHA("Golgotha, the Skull Keep", 46, false, "BONE_TYRANT");
        final String title, boss;
        /** Half the footprint (a square): nothing is drawn farther than this from the centre, along either axis. */
        final int radius;
        final boolean wet;
        Great(String title, int radius, boolean wet, String boss) { this.title = title; this.radius = radius; this.wet = wet; this.boss = boss; }
    }
    static final int GREAT_GRID = 256;
    static final int GREAT_MAX = java.util.Arrays.stream(Great.values()).mapToInt(g -> g.radius).max().getAsInt();
    /** A great structure's centre keeps this far from its cell's edges, so it never reaches into a neighbouring cell. */
    static final int GREAT_MARGIN = GREAT_MAX + 4;
    private static final long GREAT_SALT = 0x47726561744CL;

    /** A planned great structure. {@code base} is its ground floor (the sea floor for the drowned ones). */
    static final class GreatSite {
        final Great kind;
        final int i, j, x, z, base, rot;
        final long hash;
        final Plans plans;
        /** The design's layout (see {@link Greats#plan}), made once from the site's hash. */
        volatile Object plan;
        GreatSite(Great kind, int i, int j, int x, int z, int base, int rot, long hash, Plans plans) {
            this.kind = kind; this.i = i; this.j = j; this.x = x; this.z = z; this.base = base; this.rot = rot; this.hash = hash; this.plans = plans;
        }
        /** Whether a column lies within the footprint plus {@code pad}. */
        boolean covers(int wx, int wz, int pad) { return Math.abs(wx - x) <= kind.radius + pad && Math.abs(wz - z) <= kind.radius + pad; }
        /** The land's height at any column (a pure function of the seed, unlike Canvas.ground, which only knows its chunk). */
        int surface(int wx, int wz) { return plans.surface(wx, wz); }
        /** How deep the sea lies over the floor at the centre (0 on land). */
        int sea() { return Math.max(0, SEA - base); }
        String name() { return kind.title; }
    }

    enum Kind {
        TEMPLE("Temple", 17), COLONNADE("Colonnade", 20), ZIGGURAT("Ziggurat", 15, DUNGEON), WATCHTOWER("Watchtower", 6),
        AQUEDUCT("Aqueduct", 25), AMPHITHEATER("Amphitheater", 17), STONES("Stone Circle", 10), CRYPT("Crypt", 8, DUNGEON),
        GATEHOUSE("Gatehouse", 13), COLOSSUS("Colossus", 11),
        // The cult arenas, each guarded by a Warden (a mini-boss holding one of the Door's Seals).
        SANCTUM("Sanctum of the Drowned Star", 18, CULT), MONOLITHS("Circle of the Watchers", 20, CULT),
        PIT("Pit of Offerings", 14, CULT), POOL("Spawning Pool", 14, CULT), CHAPEL("Chapel of the Faceless", 13, CULT),
        // Greater ruins: dungeons above ground, temples and monuments.
        FORTRESS("Bastion of the Choir", 22, NAMED | DUNGEON), LABYRINTH("Labyrinth of Angles", 21, NAMED | DUNGEON),
        OSSUARY("Ossuary Temple", 14, NAMED | DUNGEON), DEEP_TEMPLE("Temple of the Deep", 16, NAMED | DUNGEON),
        OBSERVATORY("Star-Watcher's Spire", 12, NAMED), GREAT_IDOL("Great Idol of Ythaqqua", 12, NAMED),
        // Epoch 5: four more ruins, three more dungeons (Undercrofts) ...
        BELFRY("Belfry", 8), CLOISTER("Cloister", 15), NECROPOLIS("Necropolis", 15, DUNGEON), SCRIPTORIUM("Scriptorium", 11),
        UNDERCROFT("Undercroft of the Choir", 13, NAMED | DUNGEON), OUBLIETTE("Oubliette of the Choir", 12, NAMED | DUNGEON),
        KINGS_HALL("Hall of the Drowned Kings", 18, NAMED | DUNGEON),
        // ... and four out in the drowned shallows (Shallows).
        SUNKEN_TEMPLE("Sea Temple", 13, WET | DUNGEON), WRECK("Barge", 12, WET), LIGHTHOUSE("Lighthouse", 7, WET),
        TIDE_SHRINE("Tide Shrine", 10, WET);
        final String noun;
        final int radius;
        final boolean cult, named, dungeon, wet;
        Kind(String noun, int radius) { this(noun, radius, 0); }
        Kind(String noun, int radius, int flags) {
            this.noun = noun; this.radius = radius;
            cult = (flags & CULT) != 0; named = cult || (flags & NAMED) != 0; dungeon = cult || (flags & DUNGEON) != 0; wet = (flags & WET) != 0;
        }
    }
    private static final Kind[] LAND = java.util.Arrays.stream(Kind.values()).filter(k -> !k.wet).toArray(Kind[]::new);
    private static final Kind[] DROWNED = java.util.Arrays.stream(Kind.values()).filter(k -> k.wet).toArray(Kind[]::new);
    /** The widest ruin's radius: how far a site may reach past its own cell's margin (RuinsGenerator.SITE_REACH). */
    static final int MAX_RADIUS = java.util.Arrays.stream(Kind.values()).mapToInt(k -> k.radius).max().getAsInt();

    /**
     * The field: every free 24-block cell holds one monument -- or, since epoch 5, one of the lesser ruins (Lesser: a cellar,
     * a tomb, a well, a fallen hut, a spider den, an offering stone), each with a chest or a spawner.
     */
    enum Filler { GIANT_PILLAR, OBELISK, PILLAR_GATE, CYCLOPEAN_WALL, STAIR_TO_NOWHERE, SUNKEN_PLAZA, ARCHWAY, IDOL, CULT_ALTAR,
        SPIRE_CLUSTER, COLONNADE_ROW, CYCLOPEAN_BLOCKS, SHRINE_TEMPLE, CATACOMB_GATE, WATCHER_STATUE, OBELISK_GROVE, GIBBETS,
        CELLAR, TOMB, WELL, HUT, SPIDER_DEN, OFFERING_STONE;
        boolean lesser() { return ordinal() >= CELLAR.ordinal(); }
    }
    private static final int[] FILLER_WEIGHTS = {16, 9, 9, 9, 5, 6, 6, 5, 8, 6, 6, 5, 10, 22, 6, 6, 5, 17, 15, 11, 17, 10, 10};
    private static final int FILLER_TOTAL = java.util.Arrays.stream(FILLER_WEIGHTS).sum();

    static final class Cell {
        final Filler type;
        final int x, z, base, rot, sea;   // sea: depth of water over the cell's floor (0 on land)
        final long hash;
        Cell(Filler type, int x, int z, int base, int rot, int sea, long hash) { this.type = type; this.x = x; this.z = z; this.base = base; this.rot = rot; this.sea = sea; this.hash = hash; }
    }

    /** The Great Door citadel, home of the final boss. */
    static final class Door {
        final int x, z, base;
        Door(int x, int z, int base) { this.x = x; this.z = z; this.base = base; }
        boolean near(int wx, int wz, int pad) { return Math.abs(wx - x) <= DOOR_RADIUS + pad && Math.abs(wz - z) <= DOOR_RADIUS + pad; }
    }

    /** An original old city: a walled square of streets and ruined houses on levelled ground. */
    static final class City {
        final int x, z, half, ground;
        final long hash;
        final String name;
        City(int x, int z, int half, int ground, long hash, String name) {
            this.x = x; this.z = z; this.half = half; this.ground = ground; this.hash = hash; this.name = name;
        }
        /** Chebyshev distance outside the walls (0 or less inside). */
        int outside(int wx, int wz) { return Math.max(Math.abs(wx - x), Math.abs(wz - z)) - half; }
    }

    /** A wilderness ruin. {@code base} is its floor level; {@code rot} turns it in 90 degree steps. */
    static final class Site {
        final Kind kind;
        final int x, z, base, rot;
        final long hash;
        final String name;
        Site(Kind kind, int x, int z, int base, int rot, long hash, String name) {
            this.kind = kind; this.x = x; this.z = z; this.base = base; this.rot = rot; this.hash = hash; this.name = name;
        }
    }

    final long seed;
    final Terrain terrain;
    private final Reserved reserved;
    private final ConcurrentHashMap<Long, Object> cities = new ConcurrentHashMap<>(), sites = new ConcurrentHashMap<>(), candidates = new ConcurrentHashMap<>(),
        cells = new ConcurrentHashMap<>(), greats = new ConcurrentHashMap<>();
    private volatile Door door;
    private final ConcurrentHashMap<Long, Integer> depths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> reservedChunks = new ConcurrentHashMap<>();

    /** Floor level of the catacombs under a column: two dozen blocks below the lowest ground of its 128-block district. */
    int depth(int wx, int wz) {
        int di = Math.floorDiv(wx, DISTRICT), dj = Math.floorDiv(wz, DISTRICT);
        return depths.computeIfAbsent(key(di, dj), k -> {
            int min = 255;
            for (int a = 0; a <= 8; a++)
                for (int b = 0; b <= 8; b++) min = Math.min(min, terrain.sample(di * DISTRICT + a * 16, dj * DISTRICT + b * 16).y);
            return Math.max(8, Math.min(70, min - 24));
        });
    }

    /** Whether the Lost Cities own a chunk (cached; the catacombs keep out from under them). */
    boolean reservedChunk(int cx, int cz) {
        if (reservedChunks.size() > CACHE * 4) reservedChunks.clear();
        return reservedChunks.computeIfAbsent(key(cx, cz), k -> reserved.test(cx * 16, cz * 16, 16, 16));
    }
    private static final Object NONE = new Object();

    Plans(long seed, Reserved reserved) {
        this.seed = seed;
        this.terrain = new Terrain(seed);
        this.reserved = reserved;
    }

    private static long key(int i, int j) { return (long) i << 32 ^ (j & 0xffffffffL); }

    /**
     * Forgets every cached answer. Creating the world asks for its spawn (and so for the cities, sites and the Door) before
     * the Lost Cities attach to it, when nothing is reserved yet; the plugin calls this once the world is open so every
     * plan from then on keeps out of the Lost Cities (the epoch-3 world's first session put the Door at 704,-704 and every
     * later one at 896,-896).
     */
    void forget() {
        cities.clear(); sites.clear(); candidates.clear(); cells.clear(); greats.clear(); reservedChunks.clear();
        synchronized (this) { door = null; }
    }

    // ------------------------------------------------------------------ cities

    City city(int i, int j) {
        long k = key(i, j);
        Object o = cities.get(k);
        if (o == null) {
            City c = computeCity(i, j);
            if (cities.size() > CACHE) cities.clear();
            cities.put(k, c == null ? NONE : c);
            return c;
        }
        return o == NONE ? null : (City) o;
    }

    /** The city whose walls or levelled surroundings contain this column, if any. */
    City cityNear(int wx, int wz) {
        City c = city(Math.floorDiv(wx, CITY_GRID), Math.floorDiv(wz, CITY_GRID));
        return c != null && c.outside(wx, wz) < BLEND ? c : null;
    }

    private City computeCity(int i, int j) {
        long h = Hash.of(seed ^ CITY_SALT, i, j);
        if (Hash.unit(h) >= CITY_CHANCE) return null;
        int half = 48 + 8 * Hash.range(Hash.mix(h ^ 1), 0, 4);
        int span = CITY_GRID - 2 * CITY_MARGIN;
        int x = i * CITY_GRID + CITY_MARGIN + Hash.range(Hash.mix(h ^ 2), 0, span);
        int z = j * CITY_GRID + CITY_MARGIN + Hash.range(Hash.mix(h ^ 3), 0, span);
        int[] ys = new int[49];
        int n = 0, wet = 0;
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                int y = terrain.sample(x + a * half / 3, z + b * half / 3).y;
                ys[n++] = y;
                if (y <= SEA) wet++;
            }
        if (wet > 8) return null;                                  // a lake or coast: no level ground
        Arrays.sort(ys);
        if (ys[44] - ys[4] > 40) return null;                      // mountains: levelling would leave cliffs
        int ground = Math.max(SEA + 2, Math.min(118, ys[24]));
        int reach = half + BLEND;
        Door d = door();
        if (d.near(x, z, reach + 8)) return null;
        if (reserved.test(x - reach, z - reach, 2 * reach + 1, 2 * reach + 1)) return null;
        return new City(x, z, half, ground, h, Names.city(Hash.mix(h ^ 4)));
    }

    // ------------------------------------------------------------------ sites

    /** The ruin of a site cell, or null: its candidate, unless that yields to a neighbour that ranks first. */
    Site site(int i, int j) {
        long k = key(i, j);
        Object o = sites.get(k);
        if (o == null) {
            Site c = candidate(i, j);
            Site s = c != null && yields(c, i, j) ? null : c;
            if (sites.size() > CACHE) sites.clear();
            sites.put(k, s == null ? NONE : s);
            return s;
        }
        return o == NONE ? null : (Site) o;
    }

    /** A cell's ruin before its neighbours have their say (cached apart from the final answer). */
    Site candidate(int i, int j) {
        long k = key(i, j);
        Object o = candidates.get(k);
        if (o == null) {
            Site s = computeCandidate(i, j);
            if (candidates.size() > CACHE) candidates.clear();
            candidates.put(k, s == null ? NONE : s);
            return s;
        }
        return o == NONE ? null : (Site) o;
    }

    /**
     * Whether a cell's candidate gives way: a neighbouring ruin that ranks first, and is itself built, stands within its
     * footprint plus the pad. Only the eight neighbours can reach (two cells apart, centres are at least 56 blocks apart,
     * more than two of the widest footprints and the pad). Rank strictly falls along every chain of yields, so the
     * recursion ends, and every answer is a pure function of the seed: the caches only remember it.
     */
    private boolean yields(Site c, int i, int j) {
        for (int a = i - 1; a <= i + 1; a++)
            for (int b = j - 1; b <= j + 1; b++) {
                if (a == i && b == j) continue;
                Site d = candidate(a, b);
                if (d == null || !outranks(d, c) || !clash(c, d)) continue;
                if (site(a, b) != null) return true;
            }
        return false;
    }

    /**
     * Warden arenas first (the Seals must be won somewhere), then the wider ruin first (the big ones are the hardest to fit,
     * and the small ones then fill the gaps round them); then by hash, then position.
     */
    static boolean outranks(Site d, Site c) {
        int rd = rank(d.kind), rc = rank(c.kind);
        if (rd != rc) return rd > rc;
        if (d.kind.radius != c.kind.radius) return d.kind.radius > c.kind.radius;
        if (d.hash != c.hash) return Long.compareUnsigned(d.hash, c.hash) > 0;
        return d.x != c.x ? d.x > c.x : d.z > c.z;
    }

    private static int rank(Kind k) { return k.cult ? 1 : 0; }

    /** Whether two footprints (radius plus the one-block frame each) come within the pad of each other. */
    static boolean clash(Site a, Site b) {
        int reach = a.kind.radius + b.kind.radius + 2 + SITE_PAD;
        return Math.abs(a.x - b.x) <= reach && Math.abs(a.z - b.z) <= reach;
    }

    private Site computeCandidate(int i, int j) {
        long h = Hash.of(seed ^ SITE_SALT, i, j);
        if (Hash.unit(h) >= SITE_CHANCE) return null;
        int span = SITE_GRID - 2 * SITE_MARGIN;
        int x = i * SITE_GRID + SITE_MARGIN + Hash.range(Hash.mix(h ^ 6), 0, span);
        int z = j * SITE_GRID + SITE_MARGIN + Hash.range(Hash.mix(h ^ 7), 0, span);
        int center = terrain.sample(x, z).y;
        // Dry land takes a land ruin, a sea floor WET_MIN..WET_MAX blocks down a drowned one; the shoreline and the deep
        // water between take nothing.
        boolean wet = center <= SEA - WET_MIN;
        if (!wet && center <= SEA || center < SEA - WET_MAX) return null;
        Kind[] pool = wet ? DROWNED : LAND;
        Kind kind = pool[Hash.range(Hash.mix(h ^ 5), 0, pool.length - 1)];
        int r = kind.radius;
        int wetRing = 0, sum = center, top = center, low = center;
        for (int a = 0; a < 8; a++) {
            double t = a * Math.PI / 4;
            int y = terrain.sample(x + (int) Math.round(Math.cos(t) * r * 0.75), z + (int) Math.round(Math.sin(t) * r * 0.75)).y;
            if (y <= SEA - 1) wetRing++;
            sum += y;
            top = Math.max(top, y);
            low = Math.min(low, y);
        }
        if (wet ? wetRing < 5 || low < SEA - WET_MAX - 6 : wetRing > 2) return null;
        // Keep clear of the old cities (in this and the neighbouring city cells).
        int ci = Math.floorDiv(x, CITY_GRID), cj = Math.floorDiv(z, CITY_GRID);
        for (int a = ci - 1; a <= ci + 1; a++)
            for (int b = cj - 1; b <= cj + 1; b++) {
                City c = city(a, b);
                if (c != null && c.outside(x, z) < BLEND + r + 3) return null;
            }
        if (door().near(x, z, r + 8)) return null;
        if (greatNear(x, z, r + SITE_PAD + 3)) return null;
        if (reserved.test(x - r, z - r, 2 * r + 1, 2 * r + 1)) return null;
        int base = wet ? center : kind == Kind.AQUEDUCT ? Math.min(150, top + 7) : Math.max(SEA + 1, (int) Math.round(sum / 9.0));
        int rot = Hash.range(Hash.mix(h ^ 8), 0, 3);
        return new Site(kind, x, z, base, rot, h, kind.named ? "The " + kind.noun : Names.site(Hash.mix(h ^ 9), kind.noun));
    }

    /** The site nearest to a point within its radius (for titles), or null. */
    Site siteAt(int wx, int wz) {
        int i = Math.floorDiv(wx, SITE_GRID), j = Math.floorDiv(wz, SITE_GRID);
        for (int a = i - 1; a <= i + 1; a++)
            for (int b = j - 1; b <= j + 1; b++) {
                Site s = site(a, b);
                if (s != null && Math.abs(wx - s.x) <= s.kind.radius && Math.abs(wz - s.z) <= s.kind.radius) return s;
            }
        return null;
    }

    /** Whether a column lies within {@code pad} blocks of any site's (or great structure's) footprint. */
    boolean siteNear(int wx, int wz, int pad) {
        if (greatNear(wx, wz, pad)) return true;
        int i = Math.floorDiv(wx, SITE_GRID), j = Math.floorDiv(wz, SITE_GRID);
        for (int a = i - 1; a <= i + 1; a++)
            for (int b = j - 1; b <= j + 1; b++) {
                Site s = site(a, b);
                if (s != null && Math.abs(wx - s.x) <= s.kind.radius + pad && Math.abs(wz - s.z) <= s.kind.radius + pad) return true;
            }
        return false;
    }

    /** Surface height with the old cities' levelling: flat inside the walls, easing back to the land over 20 blocks. */
    int surface(int wx, int wz) {
        int t = terrain.sample(wx, wz).y;
        City c = cityNear(wx, wz);
        if (c == null) return t;
        int d = c.outside(wx, wz);
        if (d <= 0) return c.ground;
        double s = d / (double) BLEND;
        s = s * s * (3 - 2 * s);
        return (int) Math.round(c.ground + (t - c.ground) * s);
    }

    /** Whether a column is clear of every original ruin (for trees). */
    boolean open(int wx, int wz, int pad) {
        City c = cityNear(wx, wz);
        if (c != null && c.outside(wx, wz) < pad) return false;
        if (greatNear(wx, wz, pad)) return false;
        int i = Math.floorDiv(wx, SITE_GRID), j = Math.floorDiv(wz, SITE_GRID);
        for (int a = i - 1; a <= i + 1; a++)
            for (int b = j - 1; b <= j + 1; b++) {
                Site s = site(a, b);
                if (s != null && Math.abs(wx - s.x) <= s.kind.radius + pad && Math.abs(wz - s.z) <= s.kind.radius + pad) return false;
            }
        return true;
    }

    // ------------------------------------------------------------------ the Great Door

    /**
     * Where the Great Door stands: the first spot, ring by ring outward from (704, -704), on dry land that the Lost Cities
     * leave alone. Pure seed and plan math, so every chunk, the lore and the bosses agree.
     */
    Door door() {
        Door d = door;
        if (d != null) return d;
        synchronized (this) {
            if (door != null) return door;
            int sx = 704, sz = -704, pick = -1, pz = 0;
            search:
            for (int ring = 0; ring <= 40; ring++)
                for (int a = -ring; a <= ring; a++)
                    for (int b = -ring; b <= ring; b++) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                        int x = sx + a * 32, z = sz + b * 32, y = terrain.sample(x, z).y;
                        if (y <= SEA + 1) continue;
                        if (terrain.sample(x + 30, z).y <= SEA || terrain.sample(x - 30, z).y <= SEA || terrain.sample(x, z + 30).y <= SEA || terrain.sample(x, z - 30).y <= SEA) continue;
                        if (reserved.test(x - DOOR_RADIUS - 4, z - DOOR_RADIUS - 4, 2 * DOOR_RADIUS + 9, 2 * DOOR_RADIUS + 9)) continue;
                        pick = x; pz = z;
                        break search;
                    }
            if (pick == -1) { pick = sx; pz = sz; }
            door = new Door(pick, pz, Math.max(SEA + 2, Math.min(140, terrain.sample(pick, pz).y)));
            return door;
        }
    }

    // ------------------------------------------------------------------ field cells

    /** The monument of a field cell, or null where a city, site, the Door or the Lost Cities already fill the land. */
    Cell cell(int i, int j) {
        long k = key(i, j);
        Object o = cells.get(k);
        if (o == null) {
            Cell c = computeCell(i, j);
            if (cells.size() > CACHE * 2) cells.clear();
            cells.put(k, c == null ? NONE : c);
            return c;
        }
        return o == NONE ? null : (Cell) o;
    }

    /** Whether a cell's land is left to the field (no city, site, Door or Lost City on it). */
    boolean fieldCell(int i, int j) { return cell(i, j) != null; }

    /**
     * A field cell: null where the Door or the Lost Cities own the land or an old city's walls enclose it; otherwise
     * the cell gets the ruin floor, and a monument ({@code type} non-null) unless a city or site stands too close.
     */
    private Cell computeCell(int i, int j) {
        int x0 = i * CELL, z0 = j * CELL, cx = x0 + CELL / 2, cz = z0 + CELL / 2;
        if (door().near(cx, cz, CELL / 2 + 2)) return null;
        if (reserved.test(x0, z0, CELL, CELL)) return null;
        City c = cityNear(cx, cz);
        if (c != null && c.outside(cx, cz) < -CELL / 2) return null;
        if (greatNear(cx, cz, 0)) return null;                       // a great structure lays its own ground
        boolean crowded = c != null && c.outside(cx, cz) < BLEND + CELL / 2 || greatNear(cx, cz, CELL / 2 + 2);
        int si = Math.floorDiv(cx, SITE_GRID), sj = Math.floorDiv(cz, SITE_GRID);
        for (int a = si - 1; a <= si + 1 && !crowded; a++)
            for (int b = sj - 1; b <= sj + 1; b++) {
                Site s = site(a, b);
                if (s != null && Math.abs(cx - s.x) <= s.kind.radius + CELL / 2 + 1 && Math.abs(cz - s.z) <= s.kind.radius + CELL / 2 + 1) { crowded = true; break; }
            }
        long h = Hash.of(seed ^ 0x43656C6CL, i, j);
        int ground = terrain.sample(cx, cz).y, sea = Math.max(0, SEA - ground);
        if (crowded) return new Cell(null, cx, cz, ground, 0, sea, h);
        Filler type;
        if (sea > 0) {
            Filler[] wet = {Filler.GIANT_PILLAR, Filler.GIANT_PILLAR, Filler.OBELISK, Filler.SPIRE_CLUSTER, Filler.PILLAR_GATE};
            type = wet[Hash.range(Hash.mix(h ^ 1), 0, wet.length - 1)];
        } else {
            int roll = Hash.range(Hash.mix(h ^ 1), 0, FILLER_TOTAL - 1), acc = 0;
            type = Filler.GIANT_PILLAR;
            for (int t = 0; t < FILLER_WEIGHTS.length; t++) { acc += FILLER_WEIGHTS[t]; if (roll < acc) { type = Filler.values()[t]; break; } }
        }
        int jx = Hash.range(Hash.mix(h ^ 2), -2, 2), jz = Hash.range(Hash.mix(h ^ 3), -2, 2);
        return new Cell(type, cx + jx, cz + jz, ground, Hash.range(Hash.mix(h ^ 4), 0, 3), sea, h);
    }

    // ------------------------------------------------------------------ the great structures (epoch 6)

    /** The great structure of a 256-block cell, or null. */
    GreatSite great(int i, int j) {
        long k = key(i, j);
        Object o = greats.get(k);
        if (o == null) {
            GreatSite g = computeGreat(i, j);
            if (greats.size() > CACHE) greats.clear();
            greats.put(k, g == null ? NONE : g);
            return g;
        }
        return o == NONE ? null : (GreatSite) o;
    }

    /** The great structure whose footprint (plus pad) holds a column, or null. */
    GreatSite greatAt(int wx, int wz, int pad) {
        int i = Math.floorDiv(wx, GREAT_GRID), j = Math.floorDiv(wz, GREAT_GRID);
        for (int a = i - 1; a <= i + 1; a++)
            for (int b = j - 1; b <= j + 1; b++) {
                GreatSite g = great(a, b);
                if (g != null && g.covers(wx, wz, pad)) return g;
            }
        return null;
    }

    GreatSite greatAt(int wx, int wz) { return greatAt(wx, wz, 0); }

    boolean greatNear(int wx, int wz, int pad) { return greatAt(wx, wz, pad) != null; }

    /** The great structures within {@code cells} cells of a column, nearest first (for the bosses, garrisons and guides). */
    java.util.List<GreatSite> greatsNear(int wx, int wz, int cells) {
        int i = Math.floorDiv(wx, GREAT_GRID), j = Math.floorDiv(wz, GREAT_GRID);
        java.util.List<GreatSite> out = new java.util.ArrayList<>();
        for (int a = i - cells; a <= i + cells; a++)
            for (int b = j - cells; b <= j + cells; b++) { GreatSite g = great(a, b); if (g != null) out.add(g); }
        out.sort((p, q) -> Long.compare((long) (p.x - wx) * (p.x - wx) + (long) (p.z - wz) * (p.z - wz), (long) (q.x - wx) * (q.x - wx) + (long) (q.z - wz) * (q.z - wz)));
        return out;
    }

    /** A cell's preferred kind on the lattice (turned by the seed). */
    Great greatSlot(int i, int j) {
        Great[] all = Great.values();
        int turn = (int) Math.floorMod(Hash.mix(seed ^ GREAT_SALT), (long) all.length);
        return all[Math.floorMod(i + 5 * j + turn, all.length)];
    }

    private static final Great[] GREAT_LAND = java.util.Arrays.stream(Great.values()).filter(g -> !g.wet).toArray(Great[]::new);
    private static final Great[] GREAT_WET = java.util.Arrays.stream(Great.values()).filter(g -> g.wet).toArray(Great[]::new);

    /**
     * Up to eight spots in the cell, first only those whose ground suits the cell's preferred kind (dry land, or a sea floor
     * 3 to 24 blocks down for the drowned ones), then any: a spot of the other ground takes a kind of its own ground, chosen
     * by the spot's hash. A spot must be clear of the Great Door, the old cities and the Lost Cities, and not too steep.
     */
    private GreatSite computeGreat(int i, int j) {
        long h = Hash.of(seed ^ GREAT_SALT, i, j);
        Great preferred = greatSlot(i, j);
        int span = GREAT_GRID - 2 * GREAT_MARGIN;
        for (int t = 0; t < 16; t++) {
            boolean strict = t < 8;
            long ht = Hash.of(h, t & 7, 17);
            int x = i * GREAT_GRID + GREAT_MARGIN + Hash.range(Hash.mix(ht ^ 1), 0, span);
            int z = j * GREAT_GRID + GREAT_MARGIN + Hash.range(Hash.mix(ht ^ 2), 0, span);
            int center = terrain.sample(x, z).y;
            if (center < SEA - 30) continue;                                           // deep water: no footing
            int r = preferred.radius, wetRing = 0, top = center, low = center;
            int[] ys = new int[17];
            ys[0] = center;
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8, rr = r * (a % 2 == 0 ? 0.85 : 0.5);
                int y = terrain.sample(x + (int) Math.round(Math.cos(ang) * rr), z + (int) Math.round(Math.sin(ang) * rr)).y;
                ys[a + 1] = y;
                if (y <= SEA - 1) wetRing++;
                top = Math.max(top, y);
                low = Math.min(low, y);
            }
            // a drowned spot: shallow sea (3 to 30 blocks down) mostly round about, or a shore with water on at least six sides
            boolean wet = center <= SEA - WET_MIN ? wetRing >= 9 : center <= SEA + 3 && wetRing >= 6;
            boolean dry = center > SEA + 1 && wetRing <= 1 && top - low <= 30;
            if (!wet && !dry) continue;
            if (strict && wet != preferred.wet) continue;
            Great[] pool = wet ? GREAT_WET : GREAT_LAND;
            Great kind = wet == preferred.wet ? preferred : pool[Hash.range(Hash.mix(ht ^ 4), 0, pool.length - 1)];
            r = kind.radius;
            if (door().near(x, z, r + 24)) continue;
            boolean nearCity = false;
            int ci = Math.floorDiv(x, CITY_GRID), cj = Math.floorDiv(z, CITY_GRID);
            for (int a = ci - 1; a <= ci + 1 && !nearCity; a++)
                for (int b = cj - 1; b <= cj + 1; b++) {
                    City c = city(a, b);
                    if (c != null && c.outside(x, z) < BLEND + r + 8) { nearCity = true; break; }
                }
            if (nearCity) continue;
            if (reserved.test(x - r - 4, z - r - 4, 2 * r + 9, 2 * r + 9)) continue;
            Arrays.sort(ys);
            int base = wet ? center : Math.max(SEA + 2, Math.min(150, ys[8]));
            int rot = Hash.range(Hash.mix(ht ^ 3), 0, 3);
            return new GreatSite(kind, i, j, x, z, base, rot, ht, this);
        }
        return null;
    }

    int cachedCities() { return cities.size(); }
    int cachedSites() { return sites.size(); }
}
