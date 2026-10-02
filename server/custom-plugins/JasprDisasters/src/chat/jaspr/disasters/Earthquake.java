package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * An earthquake under one player.
 *
 * The quake builds, peaks halfway through and dies away. The ground shakes in jolts that shove everyone
 * standing near the target, rocks shake loose from overhead (cave ceilings, overhangs, roofs) and come
 * crashing down, and fissures tear open across the ground, the deepest ones glowing with lava at the
 * bottom. Fissures carve only ordinary terrain: protected blocks, liquids and obsidian are left alone,
 * and with break-blocks off the quake keeps its shaking and falling rocks but changes no terrain.
 */
final class Earthquake implements Disaster {
    static final String ROCK_KEY = "jaspr_quake_rock";

    private final Plugin plugin;
    private final DisasterConfig settings;
    private final Random random;
    private final String targetName;
    private final String worldName;
    private final long startTick;
    private final long endTick;
    private final Map<UUID, Rock> rocks = new HashMap<UUID, Rock>();
    private final List<Fissure> opening = new ArrayList<Fissure>();

    private long nextJoltTick;
    private long nextRockTick;
    private long nextFissureTick;
    private long nextRumbleTick;
    private int rocksLeft;
    private int fissuresLeft;
    private boolean quiet;
    private boolean finished;

    /** A rock in the air, held directly so nothing has to scan the world for it. */
    private static final class Rock {
        final FallingBlock entity;
        final long deadlineTick;
        Rock(FallingBlock entity, long deadlineTick) { this.entity = entity; this.deadlineTick = deadlineTick; }
    }

    /** A crack racing across the ground: a couple of columns are torn open every step. */
    private static final class Fissure {
        final World world;
        final double x;
        final double z;
        final double dx;
        final double dz;
        final int length;
        final int depth;
        final boolean lava;
        int at;
        Fissure(World world, double x, double z, double angle, int length, int depth, boolean lava) {
            this.world = world; this.x = x; this.z = z; this.dx = Math.cos(angle); this.dz = Math.sin(angle);
            this.length = length; this.depth = depth; this.lava = lava;
        }
    }

    Earthquake(Plugin plugin, DisasterConfig settings, Random random, Player target, long nowTicks) {
        this.plugin = plugin;
        this.settings = settings;
        this.random = random;
        this.targetName = target.getName();
        this.worldName = target.getWorld().getName();
        this.startTick = nowTicks;
        this.endTick = nowTicks + settings.quakeDurationTicks;
        // A short grace period so the warning lands before the ground moves.
        this.nextJoltTick = nowTicks + 30L;
        this.nextRumbleTick = nowTicks;
        this.nextRockTick = nowTicks + 60L;
        this.rocksLeft = settings.quakeRockfall;
        this.fissuresLeft = settings.quakeBreakBlocks ? settings.quakeFissures : 0;
        this.nextFissureTick = nowTicks + settings.quakeDurationTicks / 6L;
        announce(target);
    }

    @Override public String targetName() { return targetName; }

    @Override public boolean isFinished() { return finished; }

    private void announce(Player target) {
        target.sendMessage(ChatColor.GOLD + "The ground lurches under your feet. " + ChatColor.GRAY + "Earthquake!");
        try {
            target.sendTitle(ChatColor.GOLD + "Earthquake", ChatColor.GRAY + "Get clear of cliffs and ceilings", 10, 60, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.2f);
        if (settings.quakeNausea) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 0, true, false), true);
        }
        if (settings.quakeBroadcast) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "≋ " + ChatColor.YELLOW + "An earthquake is shaking the ground under "
                    + ChatColor.WHITE + targetName + ChatColor.YELLOW + "!");
        }
    }

    /** 0 at the start and end of the quake, 1 at its peak halfway through. */
    private double intensity(long nowTicks) {
        double t = (nowTicks - startTick) / (double) Math.max(1L, endTick - startTick);
        if (t <= 0.0d || t >= 1.0d) return 0.0d;
        return Math.sqrt(Math.sin(Math.PI * t));
    }

    @Override public void tick(long nowTicks) {
        if (finished) return;
        expireOverdue(nowTicks);
        carveFissures();

        Player target = Bukkit.getPlayerExact(targetName);
        World world = target == null ? null : target.getWorld();
        boolean lost = target == null || !target.isOnline() || target.isDead() || world == null || !world.getName().equals(worldName);
        if (lost || nowTicks >= endTick) {
            // The ground settles; anything already falling or tearing open finishes on its own.
            if (!quiet && !lost) {
                quiet = true;
                target.sendMessage(ChatColor.GRAY + "The ground settles. Aftershocks fade into the distance.");
            }
            quiet = true;
            rocksLeft = 0;
            fissuresLeft = 0;
            if (rocks.isEmpty() && opening.isEmpty()) finished = true;
            return;
        }

        double strength = intensity(nowTicks);
        if (nowTicks >= nextJoltTick) {
            nextJoltTick = nowTicks + settings.quakeJoltEveryTicks;
            jolt(target, world, strength);
        }
        if (nowTicks >= nextRumbleTick) {
            nextRumbleTick = nowTicks + 20L;
            rumble(target, world, strength, nowTicks);
        }
        if (rocksLeft > 0 && nowTicks >= nextRockTick) {
            long spacing = Math.max(10L, settings.quakeDurationTicks / Math.max(1, settings.quakeRockfall + 2));
            nextRockTick = nowTicks + spacing;
            if (random.nextDouble() < 0.35d + 0.65d * strength) {
                dropRock(target, world, nowTicks);
                rocksLeft--;
            }
        }
        if (fissuresLeft > 0 && nowTicks >= nextFissureTick) {
            long spacing = Math.max(40L, (endTick - startTick) * 2L / 3L / Math.max(1, settings.quakeFissures));
            nextFissureTick = nowTicks + spacing;
            if (openFissure(target, world)) fissuresLeft--;
        }
    }

    // ------------------------------------------------------------------ shaking

    /** Everyone and everything on the ground near the target is shoved a little, harder at the peak. */
    private void jolt(Player target, World world, double strength) {
        double radius = settings.quakeRadius;
        double push = settings.quakeJoltStrength * (0.35d + 0.65d * strength);
        int moved = 0;
        for (Entity entity : world.getNearbyEntities(target.getLocation(), radius, 6.0d, radius)) {
            if (!(entity instanceof LivingEntity) || !entity.isOnGround()) continue;
            if (entity instanceof Player) {
                GameMode mode = ((Player) entity).getGameMode();
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) continue;
            } else if (moved >= 40) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2.0d;
            Vector velocity = entity.getVelocity();
            velocity.setX(velocity.getX() + Math.cos(angle) * push);
            velocity.setZ(velocity.getZ() + Math.sin(angle) * push);
            velocity.setY(Math.max(velocity.getY(), 0.10d + 0.12d * strength));
            entity.setVelocity(velocity);
            if (entity instanceof Player && strength > 0.5d) {
                ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 0, true, false), true);
            }
            moved++;
        }
    }

    private void rumble(Player target, World world, double strength, long nowTicks) {
        Location at = target.getLocation();
        world.playSound(at, Sound.ENTITY_LIGHTNING_THUNDER, 0.6f + 1.2f * (float) strength, 0.2f);
        if (random.nextInt(3) == 0) world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 2.0f, 0.5f);
        try {
            // Dust shaken up off the ground all around.
            world.spawnParticle(Particle.CLOUD, at.clone().add(0.0d, 0.2d, 0.0d), (int) (8 + 18 * strength), 7.0d, 0.15d, 7.0d, 0.01d);
            world.spawnParticle(Particle.SMOKE_NORMAL, at.clone().add(0.0d, 0.1d, 0.0d), (int) (6 + 10 * strength), 6.0d, 0.1d, 6.0d, 0.02d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
        // A second wave of nausea at the peak, so the strongest shaking is felt as well as heard.
        if (settings.quakeNausea && strength > 0.95d && nowTicks - startTick > 60L && random.nextInt(4) == 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 0, true, false), true);
        }
    }

    // ------------------------------------------------------------------ rockfall

    /**
     * A rock shakes loose overhead: from the ceiling of the cave, overhang or roof above a spot near the
     * target. Under open sky nothing falls. It shatters where it lands.
     */
    private void dropRock(Player target, World world, long nowTicks) {
        Location base = target.getLocation();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        double distance = 1.5d + random.nextDouble() * 8.0d;
        int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        int floorY = base.getBlockY();
        // Only where something overhead can shake loose: open sky drops no rocks.
        int spawnY = -1;
        for (int y = floorY + 2; y <= Math.min(254, floorY + 16); y++) {
            if (world.getBlockAt(x, y, z).getType() != Material.AIR) { spawnY = y - 1; break; }
        }
        if (spawnY <= floorY + 1 || world.getBlockAt(x, spawnY, z).getType() != Material.AIR) return;

        Location at = new Location(world, x + 0.5d, spawnY, z + 0.5d);
        FallingBlock rock;
        try {
            rock = world.spawnFallingBlock(at, random.nextBoolean() ? Material.COBBLESTONE : Material.GRAVEL, (byte) 0);
        } catch (Throwable blocked) {
            return;
        }
        rock.setDropItem(false);
        rock.setHurtEntities(true);
        rock.setMetadata(ROCK_KEY, new FixedMetadataValue(plugin, Boolean.TRUE));
        rock.setVelocity(new Vector((random.nextDouble() - 0.5d) * 0.1d, -0.2d, (random.nextDouble() - 0.5d) * 0.1d));
        rocks.put(rock.getUniqueId(), new Rock(rock, nowTicks + 160L));
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
    }

    boolean owns(UUID id) { return rocks.containsKey(id); }

    /** Called when one of our rocks touches down: it bursts into gravel dust instead of becoming a block. */
    void onRockLanded(UUID id, Location where) {
        if (rocks.remove(id) == null) return;
        shatter(where);
        if (quiet && rocks.isEmpty() && opening.isEmpty()) finished = true;
    }

    private void shatter(Location where) {
        World world = where.getWorld();
        if (world == null) return;
        world.playSound(where, Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);
        world.playSound(where, Sound.BLOCK_GRAVEL_BREAK, 2.0f, 0.7f);
        try {
            world.spawnParticle(Particle.SMOKE_NORMAL, where, 14, 0.5d, 0.3d, 0.5d, 0.02d);
            world.spawnParticle(Particle.CLOUD, where, 6, 0.4d, 0.2d, 0.4d, 0.01d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    /** A rock that never reports a landing (despawned, chunk unloaded) still shatters. */
    private void expireOverdue(long nowTicks) {
        if (rocks.isEmpty()) return;
        List<Rock> overdue = null;
        for (Iterator<Map.Entry<UUID, Rock>> it = rocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Rock> entry = it.next();
            if (nowTicks < entry.getValue().deadlineTick && entry.getValue().entity.isValid()) continue;
            if (overdue == null) overdue = new ArrayList<Rock>();
            overdue.add(entry.getValue());
            it.remove();
        }
        if (overdue == null) return;
        for (Rock rock : overdue) {
            if (!rock.entity.isValid()) continue;
            Location where = rock.entity.getLocation();
            rock.entity.remove();
            shatter(where);
        }
    }

    // ------------------------------------------------------------------ fissures

    /** Starts a crack a few blocks from the target, running off in a random direction. */
    private boolean openFissure(Player target, World world) {
        Location base = target.getLocation();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        double distance = 4.0d + random.nextDouble() * Math.max(2.0d, settings.quakeRadius * 0.55d - 4.0d);
        double x = base.getX() + Math.cos(angle) * distance;
        double z = base.getZ() + Math.sin(angle) * distance;
        if (!world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return false;
        int length = Math.max(4, (int) Math.round(settings.quakeFissureLength * (0.7d + random.nextDouble() * 0.6d)));
        boolean lava = settings.quakeLavaPercent > 0 && random.nextInt(100) < settings.quakeLavaPercent;
        opening.add(new Fissure(world, x, z, random.nextDouble() * Math.PI * 2.0d, length, settings.quakeFissureDepth, lava));
        world.playSound(new Location(world, x, base.getY(), z), Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 0.5f);
        return true;
    }

    /** Advances every opening crack by a couple of columns. Bounded: a fissure is at most a few dozen columns. */
    private void carveFissures() {
        if (opening.isEmpty()) return;
        for (Iterator<Fissure> it = opening.iterator(); it.hasNext(); ) {
            Fissure fissure = it.next();
            for (int step = 0; step < 2 && fissure.at < fissure.length; step++, fissure.at++) carveColumn(fissure, fissure.at);
            if (fissure.at >= fissure.length) it.remove();
        }
        if (quiet && rocks.isEmpty() && opening.isEmpty()) finished = true;
    }

    private void carveColumn(Fissure fissure, int index) {
        World world = fissure.world;
        // The crack wanders a little sideways instead of running ruler-straight.
        double wander = Math.sin(index * 0.55d) * 0.8d;
        int x = (int) Math.floor(fissure.x + fissure.dx * index - fissure.dz * wander);
        int z = (int) Math.floor(fissure.z + fissure.dz * index + fissure.dx * wander);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        double along = (index + 0.5d) / fissure.length;
        int depth = 1 + (int) Math.round((fissure.depth - 1) * Math.sin(Math.PI * along));
        boolean wide = depth >= 3;
        Block ground = Impacts.naturalGround(world, x, z);
        if (ground == null) return;   // a building, road or farm: the crack passes it by
        int top = ground.getY();
        if (top < 2) return;

        int bottom = top;
        for (int w = 0; w <= (wide ? 1 : 0); w++) {
            int cx = w == 0 ? x : x + (int) Math.round(-fissure.dz);
            int cz = w == 0 ? z : z + (int) Math.round(fissure.dx);
            for (int y = top; y > top - depth && y > 1; y--) {
                Block block = world.getBlockAt(cx, y, cz);
                // No physics update: the ground is splitting open, not being mined.
                if (Impacts.canBreak(block.getType())) block.setType(Material.AIR, false);
                bottom = Math.min(bottom, y);
            }
        }
        // The deepest stretch of a lava fissure glows: the magma below shows through.
        if (fissure.lava && depth >= 3 && along > 0.3d && along < 0.7d) {
            Block floor = world.getBlockAt(x, bottom, z);
            Block under = floor.getRelative(0, -1, 0);
            if (floor.getType() == Material.AIR && under.getType().isSolid() && !Impacts.isProtected(under.getType())) {
                floor.setType(Material.STATIONARY_LAVA, false);
            }
        }
        Location at = new Location(world, x + 0.5d, top + 0.5d, z + 0.5d);
        try {
            world.spawnParticle(Particle.SMOKE_LARGE, at, 4, 0.4d, 0.3d, 0.4d, 0.02d);
            world.spawnParticle(Particle.CLOUD, at, 3, 0.4d, 0.2d, 0.4d, 0.02d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
        if (index % 3 == 0) world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.6f, 0.5f);
    }

    /** Ends the quake now: rocks in the air vanish, cracks stop where they are. Safe to call twice. */
    @Override public void cancel() {
        for (Rock rock : new ArrayList<Rock>(rocks.values())) {
            if (rock.entity.isValid()) rock.entity.remove();
        }
        rocks.clear();
        opening.clear();
        rocksLeft = 0;
        fissuresLeft = 0;
        finished = true;
    }
}
