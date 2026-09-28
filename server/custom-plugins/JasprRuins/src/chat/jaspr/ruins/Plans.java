package chat.jaspr.ruins;

import chat.jaspr.biomes.Terrain;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the original ruins go: one possible old city per 320-block cell and one possible wilderness site per 96-block
 * cell, each a pure function of the seed and the terrain. Anything that would touch land the Lost Cities build (their
 * cities, highways and the ring around them) or stand in water is left out, so the two kinds of ruins never overlap.
 */
final class Plans {
    /** Whether a block footprint touches land the Lost Cities build (CityApi.reserved in the plugin). */
    interface Reserved { boolean test(int blockX, int blockZ, int width, int depth); }

    static final int SEA = 62;
    static final int CITY_GRID = 320, CITY_MARGIN = 112, BLEND = 20;
    static final double CITY_CHANCE = 0.45;
    static final int SITE_GRID = 96, SITE_MARGIN = 26;
    static final double SITE_CHANCE = 0.8;
    static final int CELL = 24, DOOR_RADIUS = 48;
    private static final long CITY_SALT = 0x43697479L, SITE_SALT = 0x53697465L;
    private static final int CACHE = 8192;

    enum Kind {
        TEMPLE("Temple", 17), COLONNADE("Colonnade", 20), ZIGGURAT("Ziggurat", 15), WATCHTOWER("Watchtower", 6),
        AQUEDUCT("Aqueduct", 25), AMPHITHEATER("Amphitheater", 17), STONES("Stone Circle", 10), CRYPT("Crypt", 8),
        GATEHOUSE("Gatehouse", 13), COLOSSUS("Colossus", 11),
        // The cult arenas, each guarded by a Warden (a mini-boss holding one of the Door's Seals).
        SANCTUM("Sanctum of the Drowned Star", 18, true), MONOLITHS("Circle of the Watchers", 20, true),
        PIT("Pit of Offerings", 14, true), POOL("Spawning Pool", 14, true), CHAPEL("Chapel of the Faceless", 13, true);
        final String noun;
        final int radius;
        final boolean cult;
        Kind(String noun, int radius) { this(noun, radius, false); }
        Kind(String noun, int radius, boolean cult) { this.noun = noun; this.radius = radius; this.cult = cult; }
    }

    /** The field: every free 24-block cell holds one monument. */
    enum Filler { GIANT_PILLAR, OBELISK, PILLAR_GATE, CYCLOPEAN_WALL, STAIR_TO_NOWHERE, SUNKEN_PLAZA, ARCHWAY, IDOL, CULT_ALTAR,
        SPIRE_CLUSTER, COLONNADE_ROW, CYCLOPEAN_BLOCKS }
    private static final int[] FILLER_WEIGHTS = {18, 10, 10, 10, 6, 7, 7, 5, 8, 7, 7, 5};

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
    private final ConcurrentHashMap<Long, Object> cities = new ConcurrentHashMap<>(), sites = new ConcurrentHashMap<>(), cells = new ConcurrentHashMap<>();
    private volatile Door door;
    private static final Object NONE = new Object();

    Plans(long seed, Reserved reserved) {
        this.seed = seed;
        this.terrain = new Terrain(seed);
        this.reserved = reserved;
    }

    private static long key(int i, int j) { return (long) i << 32 ^ (j & 0xffffffffL); }

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

    Site site(int i, int j) {
        long k = key(i, j);
        Object o = sites.get(k);
        if (o == null) {
            Site s = computeSite(i, j);
            if (sites.size() > CACHE) sites.clear();
            sites.put(k, s == null ? NONE : s);
            return s;
        }
        return o == NONE ? null : (Site) o;
    }

    private Site computeSite(int i, int j) {
        long h = Hash.of(seed ^ SITE_SALT, i, j);
        if (Hash.unit(h) >= SITE_CHANCE) return null;
        Kind[] kinds = Kind.values();
        Kind kind = kinds[Hash.range(Hash.mix(h ^ 5), 0, kinds.length - 1)];
        int span = SITE_GRID - 2 * SITE_MARGIN;
        int x = i * SITE_GRID + SITE_MARGIN + Hash.range(Hash.mix(h ^ 6), 0, span);
        int z = j * SITE_GRID + SITE_MARGIN + Hash.range(Hash.mix(h ^ 7), 0, span);
        int r = kind.radius;
        int center = terrain.sample(x, z).y;
        if (center <= SEA) return null;
        int wet = 0, sum = center, top = center;
        for (int a = 0; a < 8; a++) {
            double t = a * Math.PI / 4;
            int y = terrain.sample(x + (int) Math.round(Math.cos(t) * r * 0.75), z + (int) Math.round(Math.sin(t) * r * 0.75)).y;
            if (y <= SEA - 1) wet++;
            sum += y;
            top = Math.max(top, y);
        }
        if (wet > 2) return null;
        // Keep clear of the old cities (in this and the neighbouring city cells).
        int ci = Math.floorDiv(x, CITY_GRID), cj = Math.floorDiv(z, CITY_GRID);
        for (int a = ci - 1; a <= ci + 1; a++)
            for (int b = cj - 1; b <= cj + 1; b++) {
                City c = city(a, b);
                if (c != null && c.outside(x, z) < BLEND + r + 6) return null;
            }
        if (door().near(x, z, r + 8)) return null;
        if (reserved.test(x - r, z - r, 2 * r + 1, 2 * r + 1)) return null;
        int base = kind == Kind.AQUEDUCT ? Math.min(150, top + 7) : Math.max(SEA + 1, (int) Math.round(sum / 9.0));
        int rot = Hash.range(Hash.mix(h ^ 8), 0, 3);
        return new Site(kind, x, z, base, rot, h, kind.cult ? "The " + kind.noun : Names.site(Hash.mix(h ^ 9), kind.noun));
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

    /** Whether a column lies within {@code pad} blocks of any site's footprint. */
    boolean siteNear(int wx, int wz, int pad) {
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
        boolean crowded = c != null && c.outside(cx, cz) < BLEND + CELL / 2;
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
            int roll = Hash.range(Hash.mix(h ^ 1), 0, 99), acc = 0;
            type = Filler.GIANT_PILLAR;
            for (int t = 0; t < FILLER_WEIGHTS.length; t++) { acc += FILLER_WEIGHTS[t]; if (roll < acc) { type = Filler.values()[t]; break; } }
        }
        int jx = Hash.range(Hash.mix(h ^ 2), -2, 2), jz = Hash.range(Hash.mix(h ^ 3), -2, 2);
        return new Cell(type, cx + jx, cz + jz, ground, Hash.range(Hash.mix(h ^ 4), 0, 3), sea, h);
    }

    int cachedCities() { return cities.size(); }
    int cachedSites() { return sites.size(); }
}
