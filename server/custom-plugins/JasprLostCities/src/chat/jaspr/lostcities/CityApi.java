package chat.jaspr.lostcities;

import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.World;

/**
 * Public API for other generators that must keep out of the Lost Cities.
 *
 * <ul>
 * <li>{@link #cityRegion(long, int, int)} is pure: whether a chunk lies inside a city region of that seed
 *     (region geometry only; it ignores the pre-existing-chunk boundary and the sanctuary exclusion).</li>
 * <li>{@link #reserved(World, int, int, int, int)} is true when any chunk of the footprint, grown by one chunk,
 *     is land this plugin builds on: a city region chunk, the one-chunk border ring around it, a highway between
 *     cities (with its ring) or a subway tunnel leading out of a city -- and never a chunk that existed before the
 *     plugin first ran, nor a (possible) HorrorBiomes sanctuary or its 3-chunk halo. A footprint lying wholly on
 *     chunks that existed before the plugin first ran is never reserved (that land is never written here, so what
 *     HorrorBiomes built there stays recognised). The answer is a fixed plan: it does not change as chunks are
 *     built, nor if generation is stopped. Thread-safe; results are cached; about 2 microseconds a call.</li>
 * </ul>
 * Neither method calls HorrorBiomes placement code (the sanctuary test is seed math on HorrorBiomes' lattice), so
 * HorrorBiomes may call them from its own placement code. Outside the "world" overworld nothing is reserved.
 */
public final class CityApi {
    private CityApi() {}

    static final AtomicLong CALLS = new AtomicLong(), RESERVED = new AtomicLong();

    /** Whether the chunk lies inside a city region of that seed (pure; ignores the boundary). */
    public static boolean cityRegion(long worldSeed, int chunkX, int chunkZ) {
        return Regions.inRegion(worldSeed, chunkX, chunkZ);
    }

    /** Whether a block footprint (grown by one chunk) touches land the Lost Cities will build. */
    public static boolean reserved(World world, int blockX, int blockZ, int width, int depth) {
        if (world == null) return false;
        CityWorld w = LostCitiesPlugin.cityWorld(world);
        if (w == null) return false;
        CALLS.incrementAndGet();
        int c0 = Math.floorDiv(blockX, 16) - 1, c1 = Math.floorDiv(blockX + Math.max(1, width) - 1, 16) + 1;
        int d0 = Math.floorDiv(blockZ, 16) - 1, d1 = Math.floorDiv(blockZ + Math.max(1, depth) - 1, 16) + 1;
        // Old land is never reserved: a footprint lying wholly on chunks that existed before this plugin is left to
        // HorrorBiomes (nothing here ever writes them), so whatever already stands there stays recognised.
        boolean old = true;
        for (int cx = c0 + 1; cx <= c1 - 1 && old; cx++)
            for (int cz = d0 + 1; cz <= d1 - 1 && old; cz++)
                if (!w.preexisting(cx, cz)) old = false;
        if (old) return false;
        for (int cx = c0; cx <= c1; cx++)
            for (int cz = d0; cz <= d1; cz++)
                if (w.managed(cx, cz)) { RESERVED.incrementAndGet(); return true; }
        return false;
    }

    /** Name of the city region at a chunk, or null. */
    public static String cityName(long worldSeed, int chunkX, int chunkZ) {
        Regions.Region r = Regions.regionAt(worldSeed, chunkX, chunkZ);
        return r == null ? null : r.name;
    }
}
