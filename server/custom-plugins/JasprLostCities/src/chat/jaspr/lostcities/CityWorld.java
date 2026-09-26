package chat.jaspr.lostcities;

import chat.jaspr.biomes.Terrain;
import java.util.Random;
import java.util.function.BiPredicate;
import net.minecraft.server.v1_12_R1.NoiseGenerator3;
import net.minecraft.server.v1_12_R1.NoiseGeneratorOctaves;

/**
 * The per-world "provider" of the port (the role of LostCityChunkGenerator): seed, assets, the HorrorBiomes terrain
 * it reads heights and biomes from, the pre-existing-chunk boundary, and the bounded caches of everything the mod
 * computed per chunk. Everything here is deterministic from the seed, the boundary and the sanctuary rule, and the
 * query side (managed/reserved) is thread-safe.
 */
final class CityWorld {
    final long seed;
    final Assets assets;
    final Assets.WorldStyle worldStyle;
    final Terrain terrain;
    private final BiPredicate<Integer, Integer> preexisting;   // the Anvil boundary (chunks that existed first)
    private final Sanctuaries sanctuaries;                     // may be null (offline tests)
    final Highway highway;
    final Railway railway;
    final boolean explosionChances;                            // any citystyle with an explosion chance

    // Noise generators, created in the order the mod created them from provider.rand.
    final NoiseGenerator3 rubbleNoise, leavesNoise, ruinNoise, surfaceNoise;

    /** Per-chunk terrain facts from HorrorBiomes: raw city level, water. */
    static final class Facts {
        final int rawLevel;
        final int avgHeight;
        final boolean water;
        volatile String[] biomes;      // the mod's five biome samples, lazily
        volatile int[] heights;        // 5x5 heights for the highway tunnel test, lazily
        Facts(int rawLevel, int avgHeight, boolean water) { this.rawLevel = rawLevel; this.avgHeight = avgHeight; this.water = water; }
    }

    private final Lru<Long, Facts> facts = new Lru<>(65536);
    private final Lru<Long, Integer> levels = new Lru<>(65536);
    private final Lru<Long, Boolean> managedCache = new Lru<>(65536);
    final Lru<Long, BuildingInfo.Characteristics> characteristics = new Lru<>(32768);
    final Lru<Long, BuildingInfo> buildingInfos = new Lru<>(6144);

    CityWorld(long seed, Assets assets, Terrain terrain, BiPredicate<Integer, Integer> preexisting, Sanctuaries sanctuaries) {
        this.seed = seed;
        this.assets = assets;
        this.worldStyle = assets.worldStyles.get(Profile.WORLD_STYLE);
        if (worldStyle == null) throw new IllegalStateException("Unknown worldstyle '" + Profile.WORLD_STYLE + "'!");
        this.terrain = terrain;
        this.preexisting = preexisting;
        this.sanctuaries = sanctuaries;
        boolean chances = false;
        for (Assets.CityStyle cs : assets.cityStyles.values()) if (cs.explosionChance != null) chances = true;
        this.explosionChances = chances;
        Random rand = new Random((seed + 516) * 314);
        rubbleNoise = new NoiseGenerator3(rand, 4);
        leavesNoise = new NoiseGenerator3(rand, 4);
        ruinNoise = new NoiseGenerator3(rand, 4);
        new NoiseGeneratorOctaves(rand, 16);
        new NoiseGeneratorOctaves(rand, 16);
        new NoiseGeneratorOctaves(rand, 8);
        surfaceNoise = new NoiseGenerator3(rand, 4);
        highway = new Highway(this);
        railway = new Railway(this);
    }

    static long key(int x, int z) { return (((long) x) << 32) ^ (z & 0xffffffffL); }

    // ------------------------------------------------------------------ terrain facts

    static int levelForHeight(int height) {
        if (height < Profile.CITY_LEVEL0_HEIGHT) return 0;
        if (height < Profile.CITY_LEVEL1_HEIGHT) return 1;
        if (height < Profile.CITY_LEVEL2_HEIGHT) return 2;
        if (height < Profile.CITY_LEVEL3_HEIGHT) return 3;
        return 4;
    }

    Facts facts(int cx, int cz) {
        long k = key(cx, cz);
        Facts f = facts.get(k);
        if (f != null) return f;
        int sum = 0, submerged = 0;
        for (int i = 2; i <= 14; i += 6)
            for (int j = 2; j <= 14; j += 6) {
                int y = terrain.sample(cx * 16 + i, cz * 16 + j).y;
                sum += y;
                if (y < 62) submerged++;
            }
        int avg = Math.round(sum / 9.0f);
        f = new Facts(levelForHeight(avg), avg, submerged >= 5);
        facts.put(k, f);
        return f;
    }

    /**
     * City level from the HorrorBiomes height with the mod's thresholds, smoothed so neighbouring chunks (including
     * diagonals) never differ by more than one level: the largest 1-Lipschitz function below the raw levels.
     * Streets then meet as terraces joined by the mod's stairs.
     */
    int cityLevel(int cx, int cz) {
        long k = key(cx, cz);
        Integer l = levels.get(k);
        if (l != null) return l;
        int best = facts(cx, cz).rawLevel;
        for (int dx = -4; dx <= 4 && best > 0; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d == 0 || d >= best) continue;
                int v = facts(cx + dx, cz + dz).rawLevel + d;
                if (v < best) best = v;
            }
        levels.put(k, best);
        return best;
    }

    /** The mod's five biome samples (grid indices 55, 54, 56, 5, 95, including its one-chunk westward offset). */
    String[] biomes(int cx, int cz) {
        Facts f = facts(cx, cz);
        String[] b = f.biomes;
        if (b == null) {
            int[][] at = {{-2, 14}, {-6, 14}, {2, 14}, {-2, -6}, {-2, 30}};
            b = new String[5];
            for (int i = 0; i < 5; i++) b[i] = terrain.sample(cx * 16 + at[i][0], cz * 16 + at[i][1]).profile.slot.name().toLowerCase();
            f.biomes = b;
        }
        return b;
    }

    /** Terrain heights at the mod's highway-tunnel sample points (x, z in 2, 5, 8, 11, 14). */
    int[] tunnelHeights(int cx, int cz) {
        Facts f = facts(cx, cz);
        int[] h = f.heights;
        if (h == null) {
            h = new int[25];
            int i = 0;
            for (int x = 2; x < 16; x += 3) for (int z = 2; z < 16; z += 3) h[i++] = terrain.sample(cx * 16 + x, cz * 16 + z).y;
            f.heights = h;
        }
        return h;
    }

    boolean isOcean(int cx, int cz) { return facts(cx, cz).water; }

    boolean isWaterBiome(int cx, int cz) { return facts(cx, cz).water; }

    /** Widest water (in chunks) that stays open inside a city: the width the mod's bridges span. */
    static final int CHANNEL_WIDTH = 4;

    /**
     * A water chunk that belongs to a narrow body of water: a run of at most CHANNEL_WIDTH water chunks across it
     * along X or along Z. HorrorBiomes has no river biome, so this is what plays the mod's rivers.
     */
    boolean channel(int cx, int cz) {
        if (!facts(cx, cz).water) return false;
        int run = 1;
        for (int d = 1; d <= CHANNEL_WIDTH && facts(cx - d, cz).water; d++) run++;
        for (int d = 1; d <= CHANNEL_WIDTH && facts(cx + d, cz).water; d++) run++;
        if (run <= CHANNEL_WIDTH) return true;
        run = 1;
        for (int d = 1; d <= CHANNEL_WIDTH && facts(cx, cz - d).water; d++) run++;
        for (int d = 1; d <= CHANNEL_WIDTH && facts(cx, cz + d).water; d++) run++;
        return run <= CHANNEL_WIDTH;
    }

    // ------------------------------------------------------------------ where the city may be

    /** A chunk that existed before the plugin first ran (the Anvil boundary snapshot). */
    boolean preexisting(int cx, int cz) { return preexisting != null && preexisting.test(cx, cz); }

    /** Land we may never write: chunks that existed before the plugin, HorrorBiomes sanctuaries and their halo. */
    boolean blocked(int cx, int cz) {
        if (preexisting != null && preexisting.test(cx, cz)) return true;
        return sanctuaries != null && sanctuaries.excluded(cx, cz);
    }

    boolean nearBlocked(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (blocked(cx + dx, cz + dz)) return true;
        return false;
    }

    /**
     * The mod's isCityRaw (cityFactor > threshold). Inside a region the factor is 1 ("onlycities": one continuous
     * city, lakes and sea included -- the mod's ocean factor .7 still leaves such a factor far above the threshold),
     * except where the mod's rules make a chunk non-city: narrow water, its rivers (river factor 0 in the default
     * profile), which keeps them open and crossed by its bridges; and chunks touching land we may not write, which
     * become the blended border instead.
     */
    boolean isCityRaw(int cx, int cz) {
        if (!Regions.inRegion(seed, cx, cz)) return false;
        if (nearBlocked(cx, cz)) return false;
        return !channel(cx, cz);
    }

    boolean regionOrRing(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (Regions.inRegion(seed, cx + dx, cz + dz)) return true;
        return false;
    }

    /**
     * Chunks this plugin generates: city regions, the one-chunk border ring the mod flattens towards the city,
     * highways between cities with their ring, and subway tunnels leading out of cities -- never blocked land.
     */
    boolean managed(int cx, int cz) {
        long k = key(cx, cz);
        Boolean m = managedCache.get(k);
        if (m != null) return m;
        boolean r = !blocked(cx, cz) && (regionOrRing(cx, cz) || highwayNear(cx, cz)
            || railway.getRailChunkType(cx, cz).type != RailChunkType.NONE);
        managedCache.put(k, r);
        return r;
    }

    private boolean highwayNear(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx, z = cz + dz;
                if ((z & Profile.HIGHWAY_DISTANCE_MASK) == 0 && highway.getXHighwayLevel(x, z) >= 0) return true;
                if ((x & Profile.HIGHWAY_DISTANCE_MASK) == 0 && highway.getZHighwayLevel(x, z) >= 0) return true;
            }
        return false;
    }

    BuildingInfo info(int cx, int cz) { return BuildingInfo.get(this, cx, cz); }

    /** Everything above is pure (seed, the fixed boundary snapshot, seed-only sanctuary lattice), so caches never go stale. */
    void setSanctuariesStable() { }

    String cacheStats() {
        return "facts=" + facts.stats() + " levels=" + levels.stats() + " managed=" + managedCache.stats()
            + " chars=" + characteristics.stats() + " infos=" + buildingInfos.stats() + " " + highway.stats() + " " + railway.stats();
    }
}
