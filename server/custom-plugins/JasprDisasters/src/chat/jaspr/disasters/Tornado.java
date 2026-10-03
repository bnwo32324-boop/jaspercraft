package chat.jaspr.disasters;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * A tornado that touches down near one player and churns across the land toward them.
 *
 * The funnel is drawn with particles that spiral up and widen with height. It wanders as it travels, so it
 * can be outrun but not ignored: anything that gets too close (players, mobs, animals, dropped items) is
 * pulled in, spun around the funnel, lifted, and flung out once it is carried high enough, which is where
 * the danger is. Players are reached at any height, from a mine under the funnel to a platform in the sky:
 * under a roof or underground the wind cannot lift you, so it drags you about and batters you instead. Only an
 * obsidian bunker (see Bunker) keeps it out.
 *
 * Blocks under the funnel are torn up and thrown out as debris that bursts where it comes down, a bounded number
 * per tornado. Where builds can be wrecked (build-damage-worlds) that is whatever is on top (roofs, walls, trees,
 * the ground), built blocks dropping as items for the wind to scatter; elsewhere only loose natural ground (grass,
 * dirt, sand, gravel, leaves, small plants). Obsidian, utility blocks, the block holding one up, portals and their
 * frames are never torn up, armor stands (sentry turrets' stands among them) are never moved, and with break-blocks
 * off the tornado moves things but changes no terrain.
 * The weather turns to rain for the duration and is handed back afterwards.
 */
final class Tornado implements Disaster {
    static final String DEBRIS_KEY = "jaspr_tornado_debris";

    private final Plugin plugin;
    private final DisasterConfig settings;
    private final Random random;
    private final String targetName;
    private final String worldName;
    private final long startTick;
    private final long endTick;
    private final WeatherHold weather;
    /** This world lets the tornado wreck builds; otherwise it keeps to loose natural ground. */
    private final boolean wreck;
    /** Debris in the air: it bursts where it comes down instead of landing on someone's roof or chest. */
    private final Set<UUID> debris = new HashSet<UUID>();

    private double x;
    private double z;
    private double groundY;
    private double heading;
    private int debrisLeft;
    private double ripCredit;
    private long nextSoundTick;
    private long nextBatterTick;
    private boolean finished;

    Tornado(Plugin plugin, DisasterConfig settings, Random random, Player target, long nowTicks) {
        this.plugin = plugin;
        this.settings = settings;
        this.random = random;
        this.targetName = target.getName();
        World world = target.getWorld();
        this.worldName = world.getName();
        this.startTick = nowTicks;
        this.endTick = nowTicks + settings.tornadoDurationTicks;
        this.debrisLeft = settings.tornadoBreakBlocks ? settings.tornadoMaxDebris : 0;
        this.wreck = settings.damagesBuilds(worldName);

        // Touches down on a random bearing, out at the configured distance, heading roughly at the target.
        Location at = target.getLocation();
        double bearing = random.nextDouble() * Math.PI * 2.0d;
        this.x = at.getX() + Math.cos(bearing) * settings.tornadoSpawnDistance;
        this.z = at.getZ() + Math.sin(bearing) * settings.tornadoSpawnDistance;
        this.heading = Math.atan2(at.getZ() - z, at.getX() - x);
        this.groundY = at.getY();
        // Touch down on the ground itself, even when the target is down a mine or up in the sky.
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (world.isChunkLoaded(bx >> 4, bz >> 4)) {
            int y = world.getHighestBlockYAt(bx, bz);
            if (y > 0 && y < 255) this.groundY = y;
        }

        this.weather = new WeatherHold(world, false, settings.tornadoDurationTicks);
        announce(target, world);
    }

    @Override public String targetName() { return targetName; }

    @Override public boolean isFinished() { return finished; }

    private void announce(Player target, World world) {
        target.sendMessage(ChatColor.DARK_AQUA + "The wind screams and the clouds start to turn. " + ChatColor.GRAY + "A tornado has touched down.");
        try {
            target.sendTitle(ChatColor.AQUA + "Tornado", ChatColor.GRAY + "Run, or seal yourself in obsidian", 10, 60, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        world.playSound(target.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 1.6f, 0.4f);
        if (settings.tornadoBroadcast) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "☁ " + ChatColor.AQUA + "A tornado is tearing toward "
                    + ChatColor.WHITE + targetName + ChatColor.AQUA + "!");
        }
    }

    /** Ramps in over three seconds, full strength, then dies away over the last four. */
    private double envelope(long nowTicks) {
        double in = Math.min(1.0d, (nowTicks - startTick) / 60.0d);
        double out = Math.min(1.0d, Math.max(0.0d, (endTick - nowTicks) / 80.0d));
        return Math.max(0.0d, Math.min(in, out));
    }

    @Override public void tick(long nowTicks) {
        if (finished) return;
        Player target = Bukkit.getPlayerExact(targetName);
        World world = target == null ? null : target.getWorld();
        boolean lost = target == null || !target.isOnline() || target.isDead() || world == null || !world.getName().equals(worldName);
        if (lost || nowTicks >= endTick) {
            end(lost ? null : target);
            return;
        }

        weather.hold(world, nowTicks, endTick);
        double strength = envelope(nowTicks);
        steer(target, nowTicks);
        travel(world);
        drawFunnel(world, nowTicks, strength);
        pull(world, strength, nowTicks);
        ripGround(world, strength);
        if (nowTicks >= nextSoundTick) {
            nextSoundTick = nowTicks + 20L;
            Location base = new Location(world, x, groundY, z);
            world.playSound(base, Sound.ENTITY_ENDERDRAGON_FLAP, 3.0f, 0.5f);
            if (random.nextInt(2) == 0) world.playSound(base, Sound.WEATHER_RAIN_ABOVE, 2.5f, 0.6f);
            if (target.getLocation().distanceSquared(base) < 18.0d * 18.0d) {
                target.playSound(target.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.8f, 0.7f);
            }
        }
    }

    // ------------------------------------------------------------------ movement

    /**
     * Turns toward the target a little at a time, with a slow weave on top. Close in, it stops turning and
     * passes straight through, then swings round again, so standing still is the worst thing to do.
     */
    private void steer(Player target, long nowTicks) {
        Location at = target.getLocation();
        double ex = at.getX() - x;
        double ez = at.getZ() - z;
        if (ex * ex + ez * ez > 64.0d) {
            double wanted = Math.atan2(ez, ex);
            double turn = Math.atan2(Math.sin(wanted - heading), Math.cos(wanted - heading));
            double limit = Math.toRadians(7.0d);
            heading += Math.max(-limit, Math.min(limit, turn));
        }
        heading += Math.sin(nowTicks * 0.025d) * Math.toRadians(4.0d);
    }

    private void travel(World world) {
        double step = settings.tornadoSpeed * 5.0d;   // the shared loop runs every five ticks
        double nx = x + Math.cos(heading) * step;
        double nz = z + Math.sin(heading) * step;
        if (!world.isChunkLoaded((int) Math.floor(nx) >> 4, (int) Math.floor(nz) >> 4)) {
            heading += Math.PI;   // the edge of the loaded world: turn back rather than load chunks
            return;
        }
        x = nx;
        z = nz;
        settle(world);
    }

    /** Follows the terrain: the funnel's foot sits on whatever ground is under it. */
    private void settle(World world) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return;
        int y = world.getHighestBlockYAt(bx, bz);
        if (y > 0 && y < 255) groundY += (y - groundY) * 0.5d;
    }

    // ------------------------------------------------------------------ the funnel

    private double radiusAt(double height, double strength) {
        double top = settings.tornadoHeight;
        return (1.2d + (settings.tornadoPullRadius - 1.2d) * Math.max(0.0d, Math.min(1.0d, height / top))) * (0.4d + 0.6d * strength);
    }

    private void drawFunnel(World world, long nowTicks, double strength) {
        double spin = nowTicks * 0.45d;
        int rings = 16;
        try {
            for (int i = 0; i < rings; i++) {
                double h = settings.tornadoHeight * i / (double) rings;
                double r = radiusAt(h, strength);
                for (int k = 0; k < 3; k++) {
                    double a = spin + i * 0.9d + k * (Math.PI * 2.0d / 3.0d);
                    world.spawnParticle(Particle.CLOUD, x + Math.cos(a) * r, groundY + h, z + Math.sin(a) * r, 1, 0.25d, 0.25d, 0.25d, 0.01d);
                }
                if (i % 4 == 0) world.spawnParticle(Particle.SMOKE_LARGE, x, groundY + h, z, 1, r * 0.4d, 0.6d, r * 0.4d, 0.01d);
            }
            // Dust and debris whirling at the foot.
            world.spawnParticle(Particle.SMOKE_NORMAL, x, groundY + 0.5d, z, 8, 2.2d, 0.3d, 2.2d, 0.05d);
            world.spawnParticle(Particle.EXPLOSION_NORMAL, x, groundY + 1.0d, z, 2, 1.5d, 0.4d, 1.5d, 0.05d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    // ------------------------------------------------------------------ wind

    /**
     * Pulls everything near the funnel inward, spins it round, lifts it, and throws it out from high up.
     * The shared loop runs every five ticks, so each push is sized to carry an entity until the next one.
     *
     * Players are reached anywhere in the funnel's column, from bedrock to the sky; only an obsidian bunker keeps
     * the wind out. Under a roof or underground it cannot lift you out, so it drags you about and batters you.
     */
    private void pull(World world, double strength, long nowTicks) {
        if (strength <= 0.05d) return;
        double reach = settings.tornadoPullRadius + 2.0d;
        double height = settings.tornadoHeight;
        boolean batterTurn = nowTicks >= nextBatterTick;
        if (batterTurn) nextBatterTick = nowTicks + 10L;
        Location column = new Location(world, x, 128.0d, z);
        int moved = 0;
        for (Entity entity : world.getNearbyEntities(column, reach, 130.0d, reach)) {
            if (!(entity instanceof LivingEntity || entity instanceof Item || entity instanceof FallingBlock)) continue;
            if (entity instanceof ArmorStand) continue;   // stands (and sentry turrets on them) stay put
            boolean player = entity instanceof Player;
            if (player) {
                GameMode mode = ((Player) entity).getGameMode();
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) continue;
            } else if (moved >= 60) {
                continue;
            }
            Location at = entity.getLocation();
            double ex = at.getX() - x;
            double ez = at.getZ() - z;
            double distance = Math.sqrt(ex * ex + ez * ez);
            double above = at.getY() - groundY;
            // Mobs and loose items only around the funnel itself; players wherever they are in its column.
            if (!player && (above < -2.0d || above > height)) continue;
            double radius = radiusAt(Math.max(0.0d, Math.min(height, above)), strength) + 1.5d;
            if (distance > radius) continue;
            if (player && Bunker.inside((Player) entity)) continue;   // the one place the wind cannot reach
            if (!player) moved++;

            double inX = -ex / Math.max(0.4d, distance);
            double inZ = -ez / Math.max(0.4d, distance);
            Vector velocity;
            if (player && Impacts.roofed(((Player) entity).getEyeLocation())) {
                // Under a roof or underground: dragged about where you are, and battered by debris.
                velocity = entity.getVelocity();
                velocity.setX(velocity.getX() * 0.5d + inX * 0.08d - inZ * 0.25d * strength);
                velocity.setZ(velocity.getZ() * 0.5d + inZ * 0.08d + inX * 0.25d * strength);
                if (batterTurn) batter((Player) entity, world);
            } else if (above > height * 0.55d) {
                // Carried high enough: thrown clear of the funnel, to fall wherever it lands.
                velocity = new Vector(-inX * 1.1d - inZ * 0.4d, 0.35d, -inZ * 1.1d + inX * 0.4d);
            } else {
                double core = 1.0d - Math.min(1.0d, distance / radius);
                double spin = 0.55d * strength;
                double draw = 0.16d * strength;
                double lift = settings.tornadoLift * strength * (0.35d + 0.65d * core);
                velocity = entity.getVelocity().multiply(0.35d).add(new Vector(
                        inX * draw - inZ * spin, lift + 0.18d, inZ * draw + inX * spin));
            }
            double speed = velocity.length();
            if (speed > 1.6d) velocity.multiply(1.6d / speed);
            entity.setVelocity(velocity);
        }
    }

    /** Debris hammering someone the funnel is on top of under a roof or underground: every half second. */
    private void batter(Player player, World world) {
        if (settings.tornadoShelterDamage <= 0.0d) return;
        player.damage(settings.tornadoShelterDamage / 2.0d);
        world.playSound(player.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.6f);
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                    ChatColor.AQUA + "☁ The tornado is on top of you" + ChatColor.GRAY + "  only obsidian keeps it out"));
        } catch (Throwable olderApi) {
            // The action bar is a convenience; the damage already says it.
        }
    }

    /**
     * Tears blocks out from under the funnel and flings them: a few a second, a bounded number per tornado. Where
     * builds can be wrecked that is whatever is on top of the column (roofs, walls, trees, the ground); elsewhere
     * only the soft natural surface. Never obsidian, a bunker's iron door, a utility block, the block holding one
     * up, or anything in a portal's frame.
     */
    @SuppressWarnings("deprecation")
    private void ripGround(World world, double strength) {
        if (debrisLeft <= 0 || strength < 0.5d) return;
        ripCredit += settings.tornadoRipPerSecond * 0.25d;   // five ticks is a quarter of a second
        while (ripCredit >= 1.0d && debrisLeft > 0) {
            ripCredit -= 1.0d;
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = random.nextDouble() * 3.0d;
            int bx = (int) Math.floor(x + Math.cos(angle) * distance);
            int bz = (int) Math.floor(z + Math.sin(angle) * distance);
            Block top = Impacts.topBlock(world, bx, bz);   // null when the chunk is not loaded
            if (top == null || top.getY() < 2) continue;
            if (Impacts.nearPortal(world, bx, top.getY(), bz)) continue;
            if (isLoosePlant(top.getType())) {
                top.setType(Material.AIR, false);
                top = top.getRelative(0, -1, 0);
            }
            Material type = top.getType();
            Material thrown;
            if (wreck) {
                if (!tearable(type) || !Impacts.mayWreck(top)) continue;
                thrown = type == Material.GRASS || type == Material.MYCEL ? Material.DIRT : type;
            } else {
                if (Impacts.offLimits(top)) continue;   // never the ground under a chest, bed, turret or anvil
                thrown = rippable(type);
                if (thrown == null) continue;
            }
            byte data = thrown == type ? top.getData() : (byte) 0;
            if (wreck) Impacts.wreck(top); else top.setType(Material.AIR, false);
            debrisLeft--;
            try {
                FallingBlock chunk = world.spawnFallingBlock(new Location(world, bx + 0.5d, top.getY(), bz + 0.5d), thrown, data);
                chunk.setDropItem(false);
                chunk.setHurtEntities(true);
                chunk.setMetadata(DEBRIS_KEY, new FixedMetadataValue(plugin, Boolean.TRUE));
                debris.add(chunk.getUniqueId());
                double ox = (bx + 0.5d - x), oz = (bz + 0.5d - z), len = Math.max(0.5d, Math.sqrt(ox * ox + oz * oz));
                chunk.setVelocity(new Vector(ox / len * 0.35d - oz / len * 0.7d, 0.75d + random.nextDouble() * 0.3d, oz / len * 0.35d + ox / len * 0.7d));
            } catch (Throwable blocked) {
                // The ground is already gone; only the flying chunk is missing.
            }
        }
    }

    /** What a torn-up surface block flies off as, or null if this one stays put. */
    private static Material rippable(Material type) {
        switch (type) {
            case GRASS:
            case DIRT:
            case MYCEL:
                return Material.DIRT;
            case SAND:
                return Material.SAND;
            case GRAVEL:
                return Material.GRAVEL;
            case LEAVES:
            case LEAVES_2:
                return type;
            default:
                return null;
        }
    }

    /** Glass, slabs, stairs, fences and the like: see-through building blocks a tornado tears loose. */
    private static final Set<Material> SEE_THROUGH = EnumSet.of(Material.GLASS, Material.STAINED_GLASS, Material.THIN_GLASS,
            Material.STAINED_GLASS_PANE, Material.LEAVES, Material.LEAVES_2, Material.STEP, Material.WOOD_STEP, Material.STONE_SLAB2,
            Material.PURPUR_SLAB, Material.FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
            Material.DARK_OAK_FENCE, Material.ACACIA_FENCE, Material.NETHER_FENCE, Material.IRON_FENCE, Material.COBBLE_WALL,
            Material.WOOD_STAIRS, Material.COBBLESTONE_STAIRS, Material.BRICK_STAIRS, Material.SMOOTH_STAIRS,
            Material.NETHER_BRICK_STAIRS, Material.SANDSTONE_STAIRS, Material.SPRUCE_WOOD_STAIRS, Material.BIRCH_WOOD_STAIRS,
            Material.JUNGLE_WOOD_STAIRS, Material.QUARTZ_STAIRS, Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS,
            Material.RED_SANDSTONE_STAIRS, Material.PURPUR_STAIRS, Material.GLOWSTONE, Material.SEA_LANTERN, Material.ICE);

    /** What a tornado can tear off the top of a column where builds can be wrecked: plain blocks, nothing with contents. */
    private static boolean tearable(Material type) {
        return !Impacts.isLiquid(type) && (type.isOccluding() || SEE_THROUGH.contains(type));
    }

    private static boolean isLoosePlant(Material type) {
        return type == Material.LONG_GRASS || type == Material.YELLOW_FLOWER || type == Material.RED_ROSE
                || type == Material.DOUBLE_PLANT || type == Material.DEAD_BUSH || type == Material.SNOW;
    }

    boolean owns(UUID id) { return debris.contains(id); }

    /** A torn-up chunk came down: it bursts into dust where it lands and leaves no block behind. */
    void onDebrisLanded(UUID id, Location where) {
        if (!debris.remove(id)) return;
        World world = where.getWorld();
        if (world == null) return;
        world.playSound(where, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.8f);
        try {
            world.spawnParticle(Particle.SMOKE_NORMAL, where, 8, 0.4d, 0.2d, 0.4d, 0.02d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    // ------------------------------------------------------------------ ending

    private void end(Player target) {
        if (finished) return;
        weather.restore();
        finished = true;
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.GRAY + "The funnel lifts back into the clouds and the wind drops.");
        }
    }

    @Override public void cancel() {
        if (finished) return;
        weather.restore();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof FallingBlock && debris.contains(entity.getUniqueId())) entity.remove();
            }
        }
        debris.clear();
        finished = true;
    }
}
