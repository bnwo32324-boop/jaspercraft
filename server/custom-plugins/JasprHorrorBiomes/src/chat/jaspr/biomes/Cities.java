package chat.jaspr.biomes;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Lost Cities reservation (2026-09-26, owner: "a lost cities biome where it's only one big city ... spawn frequently").
 * JasprLostCities builds one-big-city regions into NEW overworld chunks. Every HorrorBiomes placement asks here first,
 * so no set piece, room, catalogue site or dungeon is placed -- or recognised -- where a city (or its blended border)
 * will stand. The answer is JasprLostCities' CityApi.reserved(world, x, z, width, depth); with that plugin absent
 * nothing is reserved and HorrorBiomes behaves exactly as before. Sanctuaries are never asked (cities avoid them).
 */
public final class Cities {
    private Cities() {}
    private static volatile Method reserved;
    private static volatile ClassLoader loader;
    private static volatile boolean warned;

    /** True when a Lost City will be built on any chunk of this footprint (grown by one chunk). */
    public static boolean reserved(long seed, int x, int z, int width, int depth) {
        if (Bukkit.getServer() == null) return false;   // standalone planner tests: no server, no cities
        World w = Bukkit.getWorld("world");
        if (w == null || w.getSeed() != seed) return false;
        Method m = method();
        if (m == null) return false;
        try { return Boolean.TRUE.equals(m.invoke(null, w, x, z, width, depth)); }
        catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) { warned = true; Bukkit.getLogger().warning("[JasprHorrorBiomes] CITY_RESERVATION_FAILED " + e.getClass().getSimpleName()); }
            return false;
        }
    }

    private static Method method() {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprLostCities");
        if (p == null) return null;
        ClassLoader cl = p.getClass().getClassLoader();
        Method m = reserved;
        if (m != null && loader == cl) return m;
        try {
            m = Class.forName("chat.jaspr.lostcities.CityApi", true, cl)
                .getMethod("reserved", World.class, int.class, int.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!warned) { warned = true; Bukkit.getLogger().warning("[JasprHorrorBiomes] CITY_API_MISSING " + e.getClass().getSimpleName()); }
            return null;
        }
        reserved = m; loader = cl;
        return m;
    }
}
