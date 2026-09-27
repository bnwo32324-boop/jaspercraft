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
    static final double SITE_CHANCE = 0.55;
    private static final long CITY_SALT = 0x43697479L, SITE_SALT = 0x53697465L;
    private static final int CACHE = 8192;

    enum Kind {
        TEMPLE("Temple", 17), COLONNADE("Colonnade", 20), ZIGGURAT("Ziggurat", 15), WATCHTOWER("Watchtower", 6),
        AQUEDUCT("Aqueduct", 25), AMPHITHEATER("Amphitheater", 17), STONES("Stone Circle", 10), CRYPT("Crypt", 8),
        GATEHOUSE("Gatehouse", 13), COLOSSUS("Colossus", 11);
        final String noun;
        final int radius;
        Kind(String noun, int radius) { this.noun = noun; this.radius = radius; }
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
    private final ConcurrentHashMap<Long, Object> cities = new ConcurrentHashMap<>(), sites = new ConcurrentHashMap<>();
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
        if (reserved.test(x - r, z - r, 2 * r + 1, 2 * r + 1)) return null;
        int base = kind == Kind.AQUEDUCT ? Math.min(150, top + 7) : Math.max(SEA + 1, (int) Math.round(sum / 9.0));
        int rot = Hash.range(Hash.mix(h ^ 8), 0, 3);
        return new Site(kind, x, z, base, rot, h, Names.site(Hash.mix(h ^ 9), kind.noun));
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

    int cachedCities() { return cities.size(); }
    int cachedSites() { return sites.size(); }
}
