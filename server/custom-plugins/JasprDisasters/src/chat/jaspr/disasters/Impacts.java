package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Everything that happens where a meteor lands or a hell bolt comes down, and the rules every disaster obeys
 * wherever it changes the world.
 *
 * Off limits to every disaster, always: obsidian; utility blocks (chests, furnaces, anvils, beds, crafting and
 * enchanting tables, bookshelves, hoppers, droppers, dispensers - which is what a sentry turret's body is -
 * brewing stands, cauldrons, jukeboxes, note blocks, beacons, spawners, shulker boxes and the like); the block
 * directly under any of those, so nothing is left hanging or dropped (anvils fall and chip); and every portal
 * together with everything within PORTAL_MARGIN of it, so a portal's frame survives whatever it is made of
 * (obsidian for the Nether, mossy cobblestone, sandstone and the rest for the realm gates). Disaster explosions
 * have these taken out of their block lists before any other plugin sees them, and disaster fire is never lit
 * beside them. Other building blocks can still be damaged; these are the exceptions.
 */
final class Impacts {
    /** Blocks a disaster will never overwrite, so a strike cannot quietly eat storage, utility blocks or bedrock. */
    private static final Set<Material> PROTECTED = EnumSet.of(
            Material.BEDROCK, Material.OBSIDIAN, Material.BARRIER, Material.ENDER_PORTAL, Material.ENDER_PORTAL_FRAME,
            Material.PORTAL, Material.END_GATEWAY, Material.ENDER_CHEST, Material.CHEST, Material.TRAPPED_CHEST,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER, Material.FURNACE,
            Material.BURNING_FURNACE, Material.BREWING_STAND, Material.BEACON, Material.MOB_SPAWNER,
            Material.ENCHANTMENT_TABLE, Material.ANVIL, Material.COMMAND,
            Material.COMMAND_CHAIN, Material.COMMAND_REPEATING, Material.STRUCTURE_BLOCK,
            Material.BED_BLOCK, Material.WORKBENCH, Material.BOOKSHELF, Material.CAULDRON, Material.JUKEBOX,
            Material.NOTE_BLOCK, Material.SKULL, Material.DRAGON_EGG);

    /** Everything this close to a portal block counts as its frame. */
    static final int PORTAL_MARGIN = 2;

    /** Depth of disaster explosions in progress (they fire their events synchronously, inside createExplosion). */
    private static int explosionDepth;

    private Impacts() {}

    static boolean isPortal(Material material) {
        return material == Material.PORTAL || material == Material.ENDER_PORTAL || material == Material.END_GATEWAY;
    }

    /** A portal block within the margin of this spot, looking only at loaded chunks (never loads one). */
    static boolean nearPortal(World world, int x, int y, int z) {
        int r = PORTAL_MARGIN;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy > 255) continue;
                    if (isPortal(world.getBlockAt(x + dx, yy, z + dz).getType())) return true;
                }
            }
        }
        return false;
    }

    /**
     * True when a disaster must leave this block exactly as it is: it is protected itself, it holds up a
     * protected block, or it is part of (or right beside) a portal's frame.
     */
    static boolean offLimits(Block block) {
        if (isProtected(block.getType())) return true;
        if (block.getY() < 255 && isProtected(block.getRelative(BlockFace.UP).getType())) return true;
        return nearPortal(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /** Disaster fire may be lit in this air block: nothing protected touching it and no portal close by. */
    static boolean mayIgnite(Block air) {
        World world = air.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded((air.getX() + dx) >> 4, (air.getZ() + dz) >> 4)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int y = air.getY() + dy;
                    if (y < 0 || y > 255) continue;
                    if (isProtected(world.getBlockAt(air.getX() + dx, y, air.getZ() + dz).getType())) return false;
                }
            }
        }
        return !nearPortal(world, air.getX(), air.getY(), air.getZ());
    }

    /** Nothing protected and no portal within the given distance: somewhere lava may be left behind. */
    static boolean clearOfValuables(Block block, int distance) {
        World world = block.getWorld();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                if (!world.isChunkLoaded((block.getX() + dx) >> 4, (block.getZ() + dz) >> 4)) return false;
                for (int dy = -distance; dy <= distance; dy++) {
                    int y = block.getY() + dy;
                    if (y < 0 || y > 255) continue;
                    if (isProtected(world.getBlockAt(block.getX() + dx, y, block.getZ() + dz).getType())) return false;
                }
            }
        }
        return !nearPortal(world, block.getX(), block.getY(), block.getZ());
    }

    // ------------------------------------------------------------------ disaster explosions

    /**
     * Every disaster explosion goes through here. While it runs, the plugin's explosion listener knows the blast
     * is ours and strips everything off limits out of it (see spare) before any other plugin reads the list.
     */
    static void explode(World world, double x, double y, double z, float power, boolean fire, boolean breakBlocks) {
        explosionDepth++;
        try {
            world.createExplosion(x, y, z, power, fire, breakBlocks);
        } finally {
            explosionDepth--;
        }
    }

    static boolean inDisasterExplosion() { return explosionDepth > 0; }

    /**
     * Takes every off-limits block out of a disaster explosion's block list. Portals are found with one scan of
     * the blast's bounding box (plus the margin), so a large blast costs one sweep, not one per block.
     */
    static int spare(List<Block> blocks) {
        if (blocks.isEmpty()) return 0;
        World world = blocks.get(0).getWorld();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block block : blocks) {
            minX = Math.min(minX, block.getX()); maxX = Math.max(maxX, block.getX());
            minY = Math.min(minY, block.getY()); maxY = Math.max(maxY, block.getY());
            minZ = Math.min(minZ, block.getZ()); maxZ = Math.max(maxZ, block.getZ());
        }
        List<int[]> portals = new ArrayList<int[]>();
        int r = PORTAL_MARGIN;
        for (int x = minX - r; x <= maxX + r; x++) {
            for (int z = minZ - r; z <= maxZ + r; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(0, minY - r); y <= Math.min(255, maxY + r); y++) {
                    if (isPortal(world.getBlockAt(x, y, z).getType())) portals.add(new int[] {x, y, z});
                }
            }
        }
        int removed = 0;
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block block = it.next();
            boolean keep = isProtected(block.getType())
                    || (block.getY() < 255 && isProtected(block.getRelative(BlockFace.UP).getType()));
            for (int i = 0; !keep && i < portals.size(); i++) {
                int[] p = portals.get(i);
                keep = Math.abs(p[0] - block.getX()) <= r && Math.abs(p[1] - block.getY()) <= r && Math.abs(p[2] - block.getZ()) <= r;
            }
            if (keep) { it.remove(); removed++; }
        }
        return removed;
    }

    /** Shulker boxes keep their contents, and 1.12.2 spells them one enum per colour. */
    static boolean isProtected(Material material) {
        return PROTECTED.contains(material) || material.name().endsWith("SHULKER_BOX");
    }

    static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA;
    }

    /** Terrain a disaster may tear open: never air or liquid, never protected blocks (obsidian included). */
    static boolean canBreak(Material material) {
        return material != Material.AIR && !isLiquid(material) && !isProtected(material);
    }

    /** Ground that is plainly the world's own: what fissures split and tornadoes tear up. */
    private static final Set<Material> NATURAL_GROUND = EnumSet.of(
            Material.GRASS, Material.DIRT, Material.STONE, Material.SAND, Material.SANDSTONE, Material.RED_SANDSTONE,
            Material.GRAVEL, Material.CLAY, Material.HARD_CLAY, Material.STAINED_CLAY, Material.MYCEL, Material.SNOW_BLOCK,
            Material.NETHERRACK, Material.SOUL_SAND, Material.ENDER_STONE, Material.MAGMA, Material.COAL_ORE, Material.IRON_ORE);

    static boolean isNaturalGround(Material material) { return NATURAL_GROUND.contains(material); }

    /**
     * The top natural ground block of a column, looking down through air, plants, snow and trees, or null when
     * the first solid thing in the way is something else (a roof, a road, a farm): disasters that split or rip
     * the ground leave built columns alone.
     */
    static Block naturalGround(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int y = Math.min(254, world.getHighestBlockYAt(x, z)); y > 1; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR || !type.isSolid() || type == Material.LEAVES || type == Material.LEAVES_2
                    || type == Material.LOG || type == Material.LOG_2) continue;
            return isNaturalGround(type) ? block : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ meteors

    static void strike(DisasterConfig settings, Location at, Random random) {
        World world = at.getWorld();
        if (world == null) return;

        explode(world, at.getX(), at.getY(), at.getZ(), settings.explosionPower, true, settings.breakBlocks);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        try {
            world.spawnParticle(Particle.EXPLOSION_HUGE, at, 1);
            world.spawnParticle(Particle.LAVA, at, 24, 1.5d, 1.0d, 1.5d, 0.0d);
        } catch (Throwable cosmeticOnly) {
            // Particles are decoration; never let them break a strike.
        }

        scatter(at, random, Material.MAGMA, settings.magmaPerImpact, settings.scatterRadius, 5);
        scatter(at, random, Material.FIRE, settings.firePerImpact, settings.scatterRadius, 5);
    }

    // ------------------------------------------------------------------ hell bolts

    /**
     * A super bolt touching down: a blast, a scooped crater, and a floor of fire left burning in
     * the hole. The crater is only carved when block damage is switched on, so a server that runs
     * the storm purely as a spectacle still gets the flames without losing terrain.
     */
    static void hellCrater(DisasterConfig settings, Location at, Random random) {
        World world = at.getWorld();
        if (world == null) return;

        explode(world, at.getX(), at.getY(), at.getZ(), settings.stormBoltPower, true, settings.stormBreakBlocks);

        int radius = settings.stormCraterRadius;
        if (settings.stormBreakBlocks) carve(world, at, radius);

        int depth = radius + 4;
        placeCraterFloor(settings, at, random, depth);
        scatter(at, random, Material.MAGMA, settings.stormCraterMagma, radius + 1, depth);

        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
        try {
            world.spawnParticle(Particle.EXPLOSION_HUGE, at, 2, 1.0d, 0.5d, 1.0d, 0.0d);
            world.spawnParticle(Particle.FLAME, at, 70, 2.2d, 1.2d, 2.2d, 0.06d);
            world.spawnParticle(Particle.SMOKE_LARGE, at, 45, 2.2d, 1.6d, 2.2d, 0.05d);
            world.spawnParticle(Particle.LAVA, at, 30, 2.0d, 1.0d, 2.0d, 0.0d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    /** Scoops a rough bowl out of the ground, leaving protected blocks and liquids alone. */
    private static void carve(World world, Location at, int radius) {
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        int limit = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= 1; dy++) {
                    if (dx * dx + dy * dy + dz * dz > limit) continue;
                    int y = cy + dy;
                    if (y < 1 || y > 254) continue;
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    Material type = block.getType();
                    if (type == Material.AIR || isLiquid(type) || offLimits(block)) continue;
                    // No physics update: this is a hole being blown open, not a block being mined.
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    /** Lines the bottom of the crater with burning ground. Netherrack keeps the fire alive. */
    private static void placeCraterFloor(DisasterConfig settings, Location at, Random random, int depth) {
        World world = at.getWorld();
        int radius = settings.stormCraterRadius;
        int placed = 0;
        int attempts = 0;
        int wanted = settings.stormCraterFire;
        int maxAttempts = Math.max(8, wanted * 8);

        while (placed < wanted && attempts++ < maxAttempts) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;

            Block ground = surfaceNear(world, at.getBlockX() + dx, at.getBlockY(), at.getBlockZ() + dz, depth);
            if (ground == null) continue;
            Block above = ground.getRelative(BlockFace.UP);
            if (above.getType() != Material.AIR || above.getY() > 254) continue;
            if (!mayIgnite(above)) continue;

            if (settings.stormNetherrackFloor && !isLiquid(ground.getType()) && !offLimits(ground)) {
                ground.setType(Material.NETHERRACK, false);
            }
            above.setType(Material.FIRE, false);
            placed++;
        }
    }

    /** A single scorch mark where an ordinary bolt came down. */
    static void scorch(Location at) {
        World world = at.getWorld();
        if (world == null) return;
        Block ground = surfaceNear(world, at.getBlockX(), at.getBlockY(), at.getBlockZ(), 4);
        if (ground == null) return;
        Block above = ground.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR || above.getY() > 254 || !mayIgnite(above)) return;
        above.setType(Material.FIRE, false);
    }

    // ------------------------------------------------------------------ shared placement

    /**
     * Places blocks on the surface around a point. Solid materials replace the ground itself; fire
     * is placed in the air directly above it. Both are capped and skip protected blocks.
     */
    private static void scatter(Location at, Random random, Material what, int count, int radius, int depth) {
        World world = at.getWorld();
        if (world == null || count <= 0) return;
        int placed = 0;
        int attempts = 0;
        int maxAttempts = count * 6;

        while (placed < count && attempts++ < maxAttempts) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;

            Block ground = surfaceNear(world, at.getBlockX() + dx, at.getBlockY(), at.getBlockZ() + dz, depth);
            if (ground == null) continue;

            if (what == Material.FIRE) {
                Block above = ground.getRelative(BlockFace.UP);
                if (above.getType() != Material.AIR || above.getY() > 254 || !mayIgnite(above)) continue;
                above.setType(Material.FIRE, false);
                placed++;
            } else {
                if (offLimits(ground)) continue;
                if (ground.getY() < 1 || ground.getY() > 254) continue;
                ground.setType(what, false);
                placed++;
            }
        }
    }

    /** Finds the solid block nearest the impact height in this column, searching a short span. */
    static Block surfaceNear(World world, int x, int impactY, int z, int depth) {
        int top = Math.min(254, impactY + 3);
        int bottom = Math.max(1, impactY - depth);
        for (int y = top; y >= bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) continue;
            if (!block.getType().isSolid()) continue;
            return block;
        }
        return null;
    }
}
