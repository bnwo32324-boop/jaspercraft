package chat.jaspr.lostcities;

import java.util.Random;
import net.minecraft.server.v1_12_R1.NoiseGenerator3;

/** Port of lost.Highway: straight highways on every 8th chunk row/column where perlin noise is high. */
final class Highway {
    /** Bound on the run scan (the mod had none). A longer run is treated as no highway. */
    static final int MAX_RUN = 256;

    private final CityWorld w;
    private final NoiseGenerator3 perlinX, perlinZ;
    private final Lru<Long, Integer> xCache = new Lru<>(65536), zCache = new Lru<>(65536);

    Highway(CityWorld w) {
        this.w = w;
        perlinX = new NoiseGenerator3(new Random(w.seed), 4);
        perlinZ = new NoiseGenerator3(new Random(w.seed ^ 879190747L), 4);
    }

    /** -1 if no X highway goes through this chunk, else its city level. */
    int getXHighwayLevel(int cx, int cz) { return level(xCache, Orientation.X, cx, cz); }

    /** -1 if no Z highway goes through this chunk, else its city level. */
    int getZHighwayLevel(int cx, int cz) { return level(zCache, Orientation.Z, cx, cz); }

    private boolean has(Orientation o, int cx, int cz) {
        if (o == Orientation.X)
            return perlinX.a(cx / Profile.HIGHWAY_MAINPERLIN_SCALE, cz / Profile.HIGHWAY_SECONDARYPERLIN_SCALE) > Profile.HIGHWAY_PERLIN_FACTOR;
        return perlinZ.a(cx / Profile.HIGHWAY_SECONDARYPERLIN_SCALE, cz / Profile.HIGHWAY_MAINPERLIN_SCALE) > Profile.HIGHWAY_PERLIN_FACTOR;
    }

    private int level(Lru<Long, Integer> cache, Orientation o, int cx, int cz) {
        long key = CityWorld.key(cx, cz);
        Integer c = cache.get(key);
        if (c != null) return c;
        int mask = Profile.HIGHWAY_DISTANCE_MASK;
        int opposite = o == Orientation.X ? cz : cx;
        if (mask <= 0 || (opposite & mask) != 0) { cache.put(key, -1); return -1; }
        if (!has(o, cx, cz)) { cache.put(key, -1); return -1; }
        int along = o == Orientation.X ? cx : cz;
        int lower = along - 1, steps = 0;
        while (steps < MAX_RUN && hasAt(o, lower, opposite)) { lower--; steps++; }
        boolean bounded = steps < MAX_RUN;
        lower++;
        int higher = along + 1;
        steps = 0;
        while (steps < MAX_RUN && hasAt(o, higher, opposite)) { higher++; steps++; }
        bounded &= steps < MAX_RUN;
        higher--;
        // Deviation for finite cities: the mod required both ends of a noise run to be city chunks (always true in
        // an "onlycities" world). A region is a finite piece of such a world, so the run is clipped to its first and
        // last city chunk: a highway crosses the city from edge to edge, and joins two cities when the run spans both.
        int level = -1, first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
        if (bounded) {
            for (int a = lower; a <= higher; a++) {
                int x = o == Orientation.X ? a : opposite, z = o == Orientation.X ? opposite : a;
                if (w.isCityRaw(x, z)) { if (a < first) first = a; last = a; }
            }
            if (first != Integer.MAX_VALUE && last - first >= 5) {
                boolean valid = true;
                // A highway that would cross land we may not write (pre-existing chunks, sanctuaries) is not built.
                for (int a = first; a <= last && valid; a++) {
                    int x = o == Orientation.X ? a : opposite, z = o == Orientation.X ? opposite : a;
                    if (w.blocked(x, z)) valid = false;
                }
                if (valid) {
                    int lx = o == Orientation.X ? first : opposite, lz = o == Orientation.X ? opposite : first;
                    int hx = o == Orientation.X ? last : opposite, hz = o == Orientation.X ? opposite : last;
                    switch (Profile.HIGHWAY_LEVEL_FROM_CITIES_MODE) {
                        case 0: level = w.cityLevel(lx, lz); break;
                        case 1: level = Math.min(w.cityLevel(lx, lz), w.cityLevel(hx, hz)); break;
                        case 2: level = Math.max(w.cityLevel(lx, lz), w.cityLevel(hx, hz)); break;
                        default: level = (w.cityLevel(lx, lz) + w.cityLevel(hx, hz)) / 2; break;
                    }
                }
            }
            for (int a = lower; a <= higher; a++) {
                int x = o == Orientation.X ? a : opposite, z = o == Orientation.X ? opposite : a;
                cache.put(CityWorld.key(x, z), (a >= first && a <= last) ? level : -1);
            }
        }
        int mine = (level >= 0 && along >= first && along <= last) ? level : -1;
        cache.put(key, mine);
        return mine;
    }

    private boolean hasAt(Orientation o, int along, int opposite) {
        return o == Orientation.X ? has(o, along, opposite) : has(o, opposite, along);
    }

    void clear() { xCache.clear(); zCache.clear(); }

    String stats() { return "hwX=" + xCache.stats() + " hwZ=" + zCache.stats(); }
}
