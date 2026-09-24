package chat.jaspr.disasters;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A thunder-hell storm, aimed at one player.
 *
 * The world gets real weather - cloud cover and rain everywhere, because Minecraft weather is
 * world-wide and that is exactly the "clouds for miles" effect we want. Everything violent stays
 * local: bolts come down inside a radius that follows the target, and every few bolts one of them
 * is a hell bolt that blows a burning crater into the ground.
 *
 * The prior weather is captured on the way in and put back on the way out, including when the
 * plugin is disabled mid-storm, so a storm never leaves the world permanently raining.
 */
final class ThunderHellStorm implements Disaster {
    private final DisasterConfig settings;
    private final Random random;
    private final String targetName;
    private final String worldName;
    private final long endTick;

    private final boolean priorStorm;
    private final boolean priorThunder;
    private final int priorWeatherDuration;
    private final int priorThunderDuration;

    private long nextStrikeTick;
    private long nextAmbienceTick;
    private long nextHoldTick;
    private int bolts;
    private int ordinary;
    private int hellBolts;
    private Location lastKnown;
    private Location trailing;
    private boolean finished;
    private boolean restored;

    ThunderHellStorm(DisasterConfig settings, Random random, Player target, long nowTicks) {
        this.settings = settings;
        this.random = random;
        this.targetName = target.getName();

        World world = target.getWorld();
        this.worldName = world.getName();
        this.priorStorm = world.hasStorm();
        this.priorThunder = world.isThundering();
        this.priorWeatherDuration = world.getWeatherDuration();
        this.priorThunderDuration = world.getThunderDuration();

        this.endTick = nowTicks + settings.stormDurationTicks;
        // A short grace period so the warning lands before the first bolt does.
        this.nextStrikeTick = nowTicks + 50L;
        this.nextAmbienceTick = nowTicks;
        this.nextHoldTick = nowTicks;

        openTheSky(world);
        announce(target, world);
    }

    @Override public String targetName() { return targetName; }

    @Override public boolean isFinished() { return finished; }

    // ------------------------------------------------------------------ lifecycle

    private void openTheSky(World world) {
        int span = (int) settings.stormDurationTicks + 400;
        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(span);
        world.setThunderDuration(span);
    }

    private void announce(Player target, World world) {
        target.sendMessage(ChatColor.DARK_RED + "The clouds boil black from horizon to horizon. "
                + ChatColor.GRAY + "A thunder-hell storm has chosen you.");
        try {
            target.sendTitle(ChatColor.DARK_RED + "Thunder-Hell Storm",
                    ChatColor.GRAY + "The sky is hunting you", 10, 60, 20);
        } catch (Throwable olderApi) {
            // Title is decoration; the chat warning already went out.
        }
        world.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.5f);
        world.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_THUNDER, 4.0f, 0.55f);
        if (settings.stormBroadcast) {
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚡ " + ChatColor.RED
                    + "A thunder-hell storm is breaking over " + ChatColor.WHITE + targetName + ChatColor.RED + "!");
        }
    }

    @Override public void tick(long nowTicks) {
        if (finished) return;

        Player target = Bukkit.getPlayerExact(targetName);
        World world = target == null ? null : target.getWorld();
        boolean lost = target == null || !target.isOnline() || target.isDead()
                || world == null || !world.getName().equals(worldName);

        if (lost || nowTicks >= endTick) {
            end(lost ? null : target);
            return;
        }

        holdTheWeather(world, nowTicks);
        ambience(target, world, nowTicks);

        if (nowTicks >= nextStrikeTick) {
            bolts++;
            boolean hell = settings.stormSuperEvery > 0 && bolts % settings.stormSuperEvery == 0;
            if (hell) {
                Location where = pickStrikeSpot(target, world, true);
                if (where != null) {
                    hellBolts++;
                    hellBolt(world, where, target);
                }
            } else {
                ordinary++;
                boolean hunter = settings.stormHunterEvery > 0 && ordinary % settings.stormHunterEvery == 0;
                Location where = hunter ? trailingSpot(world) : null;
                if (where == null) where = pickStrikeSpot(target, world, false);
                if (where != null) ordinaryBolt(world, where);
            }
            long jitter = settings.stormBoltJitterTicks <= 0 ? 0L : random.nextInt(settings.stormBoltJitterTicks + 1);
            nextStrikeTick = nowTicks + settings.stormBoltSpacingTicks + jitter;
        }
    }

    @Override public void cancel() {
        if (finished) return;
        restoreTheSky();
        finished = true;
    }

    private void end(Player target) {
        if (finished) return;
        restoreTheSky();
        finished = true;
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.GRAY + "The storm moves on. The ground is still burning.");
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.8f);
        }
    }

    private void restoreTheSky() {
        if (restored) return;
        restored = true;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        world.setStorm(priorStorm);
        world.setThundering(priorThunder);
        world.setWeatherDuration(Math.max(600, priorWeatherDuration));
        world.setThunderDuration(Math.max(600, priorThunderDuration));
    }

    /** Someone running /weather clear mid-storm does not get to call it off. */
    private void holdTheWeather(World world, long nowTicks) {
        if (nowTicks < nextHoldTick) return;
        nextHoldTick = nowTicks + 60L;
        if (!world.hasStorm() || !world.isThundering()) openTheSky(world);
        long remaining = Math.max(200L, endTick - nowTicks + 200L);
        if (world.getWeatherDuration() < remaining) world.setWeatherDuration((int) remaining);
        if (world.getThunderDuration() < remaining) world.setThunderDuration((int) remaining);
    }

    // ------------------------------------------------------------------ atmosphere

    private void ambience(Player target, World world, long nowTicks) {
        if (nowTicks < nextAmbienceTick) return;
        nextAmbienceTick = nowTicks + 20L;

        Location at = target.getLocation();
        // Remember where they were a second ago. Hunter bolts land there, so a player who stands
        // still during the storm is the one who gets hit, and a player who keeps moving does not.
        trailing = lastKnown;
        lastKnown = at.clone();

        try {
            // Ash and embers falling out of the cloud deck.
            world.spawnParticle(Particle.SMOKE_LARGE, at.clone().add(0.0d, 6.0d, 0.0d), 26, 9.0d, 3.0d, 9.0d, 0.01d);
            world.spawnParticle(Particle.DRIP_LAVA, at.clone().add(0.0d, 8.0d, 0.0d), 12, 8.0d, 2.0d, 8.0d, 0.0d);
            world.spawnParticle(Particle.CLOUD, at.clone().add(0.0d, 10.0d, 0.0d), 20, 12.0d, 1.0d, 12.0d, 0.02d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }

        // Sheet lightning out toward the horizon, so the storm reads as far bigger than its radius.
        if (random.nextInt(3) == 0) {
            Location far = offset(at, 46.0d + random.nextDouble() * 34.0d);
            if (far != null) world.strikeLightningEffect(far);
        }

        if (random.nextInt(4) == 0) {
            world.playSound(at, Sound.ENTITY_LIGHTNING_THUNDER, 1.4f, 0.5f + random.nextFloat() * 0.3f);
        }

        if (settings.stormMarkTarget) {
            // Marked by the storm: visible to everyone, and unmistakably the one being hunted.
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false), true);
        }
    }

    // ------------------------------------------------------------------ bolts

    /** A point on the ground inside the storm radius, ignoring columns whose chunk is not loaded. */
    private Location pickStrikeSpot(Player target, World world, boolean hell) {
        Location base = target.getLocation();
        double min = hell ? Math.max(6.0d, settings.stormMinRadius) : Math.max(2.0d, settings.stormMinRadius);
        double max = hell ? Math.max(min + 4.0d, settings.stormRadius * 0.6d) : settings.stormRadius;

        for (int attempt = 0; attempt < 6; attempt++) {
            Location candidate = offset(base, min + (max - min) * Math.sqrt(random.nextDouble()));
            if (candidate != null) return candidate;
        }
        return null;
    }

    /** Projects a point at the given distance on a random bearing and drops it onto the surface. */
    private Location offset(Location base, double distance) {
        World world = base.getWorld();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getHighestBlockYAt(x, z);
        if (y < 1 || y > 254) return null;
        return new Location(world, x + 0.5d, y, z + 0.5d);
    }

    /** Where the target stood a moment ago, snapped to the surface. Null until the storm warms up. */
    private Location trailingSpot(World world) {
        if (trailing == null || trailing.getWorld() == null || !trailing.getWorld().equals(world)) return null;
        int x = trailing.getBlockX();
        int z = trailing.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getHighestBlockYAt(x, z);
        if (y < 1 || y > 254) return null;
        return new Location(world, x + 0.5d, y, z + 0.5d);
    }

    private void ordinaryBolt(World world, Location where) {
        world.strikeLightning(where);
        if (settings.stormScorchPercent > 0 && random.nextInt(100) < settings.stormScorchPercent) {
            Impacts.scorch(where);
        }
        try {
            world.spawnParticle(Particle.FLAME, where, 18, 0.6d, 0.4d, 0.6d, 0.04d);
        } catch (Throwable cosmeticOnly) {
            // Decoration only.
        }
    }

    /**
     * A hell bolt: three flashes down the same channel, then a blast that scoops a crater and
     * leaves it burning. Anything caught in the blast radius is set alight and withered.
     */
    private void hellBolt(World world, Location where, Player target) {
        world.strikeLightning(where);
        world.strikeLightningEffect(where);
        world.strikeLightningEffect(where.clone().add(0.6d, 0.0d, -0.6d));
        world.playSound(where, Sound.ENTITY_WITHER_SPAWN, 2.2f, 0.6f);

        Impacts.hellCrater(settings, where, random);

        double radius = settings.stormBlastRadius;
        for (Entity entity : world.getNearbyEntities(where, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (settings.stormIgniteTicks > 0) living.setFireTicks(Math.max(living.getFireTicks(), settings.stormIgniteTicks));
            if (settings.stormWitherTicks > 0) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, settings.stormWitherTicks, 0, true, true), true);
            }
        }

        // Standing near the flash is blinding, even when the blast itself misses.
        if (target.isOnline() && target.getWorld().equals(world)) {
            double distanceSquared = target.getLocation().distanceSquared(where);
            if (distanceSquared <= 196.0d) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 0, true, false), true);
                target.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_THUNDER, 5.0f, 0.5f);
            }
        }
    }

    // ------------------------------------------------------------------ reporting

    int boltsFired() { return bolts; }

    int hellBoltsFired() { return hellBolts; }

    long ticksLeft(long nowTicks) { return Math.max(0L, endTick - nowTicks); }
}
