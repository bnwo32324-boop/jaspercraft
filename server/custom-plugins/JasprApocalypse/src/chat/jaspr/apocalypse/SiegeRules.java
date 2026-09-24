package chat.jaspr.apocalypse;

import org.bukkit.Material;

/** Pure tuning decisions kept separate from entity/world mutation. */
public final class SiegeRules {
    private SiegeRules() {}
    private static volatile double cachedFactor=1.0;
    private static volatile long cachedAt=0L;
    private static org.bukkit.plugin.Plugin daylight;
    private static java.lang.reflect.Method factorMethod;
    /**
     * The server-wide daylight factor, owned by JasprDaylight and read reflectively
     * so this plugin keeps no compile-time dependency on it. Siege hunters steer
     * themselves with explicit velocities instead of pathfinding, so they cannot
     * inherit the slowdown from the movement-speed attribute the way a vanilla mob
     * does; they have to ask for it. Falls back to 1.0 (no change) when the plugin
     * is absent. Cached for half a second: this is read per hunter per tick, and
     * the value only ever moves at dawn and dusk.
     */
    public static double daylightFactor(org.bukkit.World world) {
        long now=System.currentTimeMillis();
        if(now-cachedAt<500L)return cachedFactor;
        cachedAt=now;
        double value=1.0;
        try {
            if(daylight==null||!daylight.isEnabled()) {
                daylight=org.bukkit.Bukkit.getPluginManager().getPlugin("JasprDaylight");
                factorMethod=daylight==null?null:daylight.getClass().getMethod("factorFor",org.bukkit.World.class);
            }
            if(daylight!=null&&daylight.isEnabled()&&factorMethod!=null)
                value=((Number)factorMethod.invoke(daylight,world)).doubleValue();
        } catch(Throwable ignored){ value=1.0; }
        if(!(value>0.0)||value>1.0)value=1.0;
        cachedFactor=value;
        return value;
    }
    public static int clamp(int value,int min,int max) { return Math.max(min,Math.min(max,value)); }
    public static double clamp(double value,double min,double max) { return Double.isFinite(value)?Math.max(min,Math.min(max,value)):min; }
    public static int breakPasses(Material type) {
        if (type == null || !type.isSolid()) return 0;
        String n = type.name();
        if (n.contains("BEDROCK") || n.contains("OBSIDIAN") || n.contains("PORTAL") || n.contains("COMMAND")
            || n.contains("STRUCTURE") || n.contains("BARRIER") || n.contains("SPAWNER") || n.contains("CHEST")
            || n.contains("SHULKER") || n.contains("FURNACE") || n.contains("HOPPER") || n.contains("DISPENSER")
            || n.contains("DROPPER") || n.contains("BEACON") || n.contains("SIGN") || n.contains("BED_BLOCK")) return 0;
        if (n.contains("IRON") || n.contains("DIAMOND") || n.contains("ANVIL")) return 12;
        if (n.contains("STONE") || n.contains("BRICK") || n.contains("CONCRETE") || n.contains("CLAY") || n.contains("ORE")) return 7;
        if (n.contains("LOG") || n.contains("WOOD") || n.contains("FENCE")) return 4;
        return 3;
    }
    public static boolean bloodMoon(long fullTime, int every) {
        long time = Math.floorMod(fullTime, 24000L);
        return every > 0 && time >= 13000 && time < 23000 && Math.floorMod(fullTime / 24000L + 1, every) == 0;
    }
    public static boolean inRange(double distanceSquared, double range) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= range * range;
    }
    public static String variant(int roll, boolean bloodMoon) {
        if (roll < (bloodMoon ? 4 : 1)) return "warden";
        if (roll < (bloodMoon ? 17 : 7)) return "revenant";
        if (roll < 16 + (bloodMoon ? 10 : 0)) return "tnt";
        if (roll < 30 + (bloodMoon ? 8 : 0)) return "brute";
        if (roll < 48) return "runner";
        if (roll < 64) return "climber";
        return "walker";
    }
}
