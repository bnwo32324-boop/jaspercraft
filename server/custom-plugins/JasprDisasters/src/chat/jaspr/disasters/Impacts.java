package chat.jaspr.disasters;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Everything that happens where a meteor lands or a hell bolt comes down. */
final class Impacts {
    /** Blocks a disaster will never overwrite, so a strike cannot quietly eat storage or bedrock. */
    private static final Set<Material> PROTECTED = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.ENDER_PORTAL, Material.ENDER_PORTAL_FRAME,
            Material.PORTAL, Material.ENDER_CHEST, Material.CHEST, Material.TRAPPED_CHEST,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER, Material.FURNACE,
            Material.BURNING_FURNACE, Material.BREWING_STAND, Material.BEACON, Material.MOB_SPAWNER,
            Material.ENCHANTMENT_TABLE, Material.ANVIL, Material.COMMAND,
            Material.COMMAND_CHAIN, Material.COMMAND_REPEATING, Material.STRUCTURE_BLOCK);

    private Impacts() {}

    /** Shulker boxes keep their contents, and 1.12.2 spells them one enum per colour. */
    private static boolean isProtected(Material material) {
        return PROTECTED.contains(material) || material.name().endsWith("SHULKER_BOX");
    }

    private static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA;
    }

    // ------------------------------------------------------------------ meteors

    static void strike(DisasterConfig settings, Location at, Random random) {
        World world = at.getWorld();
        if (world == null) return;

        world.createExplosion(at.getX(), at.getY(), at.getZ(), settings.explosionPower, true, settings.breakBlocks);
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

        world.createExplosion(at.getX(), at.getY(), at.getZ(), settings.stormBoltPower, true, settings.stormBreakBlocks);

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
                    if (type == Material.AIR || isLiquid(type) || isProtected(type)) continue;
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

            if (settings.stormNetherrackFloor && !isProtected(ground.getType()) && !isLiquid(ground.getType())) {
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
        if (above.getType() != Material.AIR || above.getY() > 254) return;
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
                if (above.getType() != Material.AIR || above.getY() > 254) continue;
                above.setType(Material.FIRE, false);
                placed++;
            } else {
                if (isProtected(ground.getType())) continue;
                if (ground.getY() < 1 || ground.getY() > 254) continue;
                ground.setType(what, false);
                placed++;
            }
        }
    }

    /** Finds the solid block nearest the impact height in this column, searching a short span. */
    private static Block surfaceNear(World world, int x, int impactY, int z, int depth) {
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
