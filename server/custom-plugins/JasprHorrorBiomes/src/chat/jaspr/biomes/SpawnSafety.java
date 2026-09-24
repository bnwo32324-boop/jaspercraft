package chat.jaspr.biomes;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Bounded, server-authoritative landing selection for the regenerated overworld. */
public final class SpawnSafety {
    static final int SEARCH_RADIUS = 24;

    private SpawnSafety() { }

    public static Location resolve(World world, int centerX, int centerZ) {
        if (world == null) return null;
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Location result = column(world, centerX + dx, centerZ + dz);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static Location column(World world, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            Chunk chunk = world.getChunkAt(cx, cz);
            if (chunk == null || !chunk.load()) return null;
        }
        int top = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 1);
        for (int feet = top; feet >= 2; feet--) {
            Location candidate = new Location(world, x + 0.5, feet, z + 0.5);
            if (standable(candidate)) return candidate;
        }
        return null;
    }

    public static boolean standable(Location location) {
        if (voidRisk(location)) return false;
        World world = location.getWorld();
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        Material floor = world.getBlockAt(x, y - 1, z).getType();
        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        return floor.isSolid() && !hazard(floor) && passable(feet) && passable(head);
    }

    public static boolean voidRisk(Location location) {
        if (location == null || location.getWorld() == null) return true;
        double x = location.getX(), y = location.getY(), z = location.getZ();
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
            || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)
            || y < 2.0 || y >= location.getWorld().getMaxHeight() - 1;
    }

    private static boolean passable(Material material) {
        return material == Material.AIR || !material.isSolid() && !hazard(material);
    }

    private static boolean hazard(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER
            || material == Material.LAVA || material == Material.STATIONARY_LAVA
            || material == Material.FIRE || material == Material.CACTUS
            || material == Material.MAGMA || material == Material.PORTAL
            || material == Material.ENDER_PORTAL;
    }
}
