package chat.jaspr.disasters;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Owner 2026-10-02: the only place the newest disasters (earthquake, tornado, blizzard) cannot reach is an
 * obsidian bunker. Underground, up in the sky and inside an ordinary base are all in reach.
 *
 * A bunker is obsidian below you, above your head, and on all four sides at both feet and head height, each
 * within REACH blocks. An iron door or iron trapdoor counts as part of the shell, since a bunker needs a way in.
 * What is inside does not matter: furniture (chests, beds, furnaces...), torches, signs, carpets and portals are
 * all fine. Anything else in the way (stone, wood, glass, a wooden door) is a gap in the shell. These disasters
 * never break a shell block, so a bunker stays a bunker while one rages outside.
 */
final class Bunker {
    /** How far from you each wall, the floor and the ceiling may be: rooms up to about 23 blocks across. */
    static final int REACH = 12;

    private static final Set<Material> SHELL = EnumSet.of(Material.OBSIDIAN, Material.BEDROCK, Material.BARRIER,
            Material.IRON_DOOR_BLOCK, Material.IRON_TRAPDOOR);

    /** Thin things that count as solid in Bukkit but are only decoration inside a room. */
    private static final Set<Material> DECORATION = EnumSet.of(Material.SIGN_POST, Material.WALL_SIGN,
            Material.STANDING_BANNER, Material.WALL_BANNER, Material.STONE_PLATE, Material.WOOD_PLATE,
            Material.GOLD_PLATE, Material.IRON_PLATE, Material.CAKE_BLOCK, Material.FLOWER_POT);

    private Bunker() {}

    static boolean isShell(Material material) { return SHELL.contains(material); }

    /** Open space, things you walk through, decoration and furniture: none of it is a gap in the shell. */
    private static boolean isInterior(Material material) {
        return material == Material.AIR || !material.isSolid() || DECORATION.contains(material)
                || Impacts.isPortal(material) || Impacts.isProtected(material);
    }

    static boolean inside(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) return false;
        return inside(world, at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    /** Whether someone with their feet in this block is sealed in obsidian. */
    static boolean inside(World world, int x, int y, int z) {
        return shell(world, x, y - 1, z, 0, -1, 0) && shell(world, x, y + 2, z, 0, 1, 0)
                && shell(world, x + 1, y, z, 1, 0, 0) && shell(world, x - 1, y, z, -1, 0, 0)
                && shell(world, x, y, z + 1, 0, 0, 1) && shell(world, x, y, z - 1, 0, 0, -1)
                && shell(world, x + 1, y + 1, z, 1, 0, 0) && shell(world, x - 1, y + 1, z, -1, 0, 0)
                && shell(world, x, y + 1, z + 1, 0, 0, 1) && shell(world, x, y + 1, z - 1, 0, 0, -1);
    }

    /** Walks outward from a spot: the first thing that is not interior must be shell, within reach. */
    private static boolean shell(World world, int x, int y, int z, int dx, int dy, int dz) {
        for (int i = 0; i < REACH; i++, x += dx, y += dy, z += dz) {
            if (y < 0 || y > 255) return false;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            Material material = world.getBlockAt(x, y, z).getType();
            if (isShell(material)) return true;
            if (!isInterior(material)) return false;
        }
        return false;
    }
}
