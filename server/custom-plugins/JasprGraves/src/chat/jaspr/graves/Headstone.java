package chat.jaspr.graves;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.SkullType;

/**
 * The marker itself: a mossy cobblestone wall post with the dead player's head
 * on top, turned to face the way they were looking. Both blocks are vanilla, so
 * the browser client renders them without a resource pack.
 */
final class Headstone {
    /** Compass points in yaw order: yaw 0 faces south, and yaw climbs south -> west -> north -> east. */
    private static final BlockFace[] FACES = {
        BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST };

    static final byte MOSSY_WALL = 1;
    static final byte SKULL_ON_FLOOR = 1;

    private Headstone() { }

    static BlockFace facing(float yaw) {
        int index = Math.round(yaw / 22.5f) & 15;
        return FACES[index];
    }

    /** Idempotent: safe to call on a grave whose blocks are already correct. */
    static boolean raise(Grave grave) {
        Block base = grave.base();
        if (base == null) return false;
        try {
            if (base.getType() != Material.COBBLE_WALL || base.getData() != MOSSY_WALL) {
                base.setType(Material.COBBLE_WALL, false);
                base.setData(MOSSY_WALL, false);
            }
            Block head = base.getRelative(BlockFace.UP);
            boolean fresh = head.getType() != Material.SKULL;
            if (fresh) {
                head.setType(Material.SKULL, false);
                head.setData(SKULL_ON_FLOOR, false);
            }
            BlockState state = head.getState();
            if (state instanceof Skull) {
                Skull skull = (Skull) state;
                boolean changed = fresh;
                if (skull.getSkullType() != SkullType.PLAYER) { skull.setSkullType(SkullType.PLAYER); changed = true; }
                if (!skull.hasOwner()) {
                    try { skull.setOwningPlayer(Bukkit.getOfflinePlayer(grave.owner)); }
                    catch (Throwable t) { skull.setOwner(grave.ownerName); }
                    changed = true;
                }
                if (changed) {
                    skull.setRotation(facing(grave.yaw));
                    skull.update(true, false);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Takes the marker down without dropping the wall or the head as items. */
    static void lower(Grave grave) {
        Block base = grave.base();
        if (base == null) return;
        try {
            Block head = base.getRelative(BlockFace.UP);
            if (head.getType() == Material.SKULL) head.setType(Material.AIR, false);
            if (base.getType() == Material.COBBLE_WALL) base.setType(Material.AIR, false);
        } catch (RuntimeException ignored) { }
    }

    /** True when this block is part of a standing headstone, before any map lookup. */
    static boolean material(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.COBBLE_WALL || type == Material.SKULL || type == Material.AIR;
    }

    static boolean spaceFree(World world, int x, int y, int z) {
        if (y < 1 || y + 1 >= world.getMaxHeight()) return false;
        return soft(world.getBlockAt(x, y, z)) && soft(world.getBlockAt(x, y + 1, z));
    }

    /** Blocks a headstone may grow through without destroying anything a player built. */
    static boolean soft(Block block) {
        switch (block.getType()) {
            case AIR: case WATER: case STATIONARY_WATER: case LAVA: case STATIONARY_LAVA:
            case FIRE: case SNOW: case LONG_GRASS: case DEAD_BUSH: case VINE: case WEB:
            case RED_ROSE: case YELLOW_FLOWER: case DOUBLE_PLANT: case CROPS: case CARROT:
            case POTATO: case MELON_STEM: case PUMPKIN_STEM: case SUGAR_CANE_BLOCK:
            case NETHER_WARTS: case BROWN_MUSHROOM: case RED_MUSHROOM: case TORCH:
                return true;
            default:
                return false;
        }
    }

    static boolean solidFooting(Block block) {
        Material type = block.getType();
        return type.isSolid() && type != Material.LAVA && type != Material.STATIONARY_LAVA;
    }
}
