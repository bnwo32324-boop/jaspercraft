package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/** Tunables. Kept deliberately small so a busy server stays predictable. */
final class DisasterConfig {
    /** One Minecraft day is 24000 ticks, which is 20 real minutes. */
    static final long MILLIS_PER_MC_DAY = 24000L * 50L;

    final boolean enabled;
    /** Quiet period after any disaster ends, so two never run back to back. */
    final long cooldownMillis;
    /** One shared timer for every kind: the next disaster is due this many Minecraft days after the last. */
    final int scheduleMinDays;
    final int scheduleMaxDays;

    // ------------------------------------------------------------------ meteor shower
    final boolean meteorEnabled;
    final boolean broadcast;
    final boolean skipCreative;
    final List<String> worlds;

    final int meteorCount;
    final int spawnRadius;
    final int fallHeight;
    final long meteorSpacingTicks;
    final long meteorTimeoutTicks;

    final float explosionPower;
    final boolean breakBlocks;
    final int scatterRadius;
    final int magmaPerImpact;
    final int firePerImpact;

    // ------------------------------------------------------------------ thunder-hell storm
    final boolean stormEnabled;
    final boolean stormBroadcast;
    final boolean stormSkipCreative;
    final List<String> stormWorlds;

    final long stormDurationTicks;
    final int stormRadius;
    final int stormMinRadius;
    final long stormBoltSpacingTicks;
    final int stormBoltJitterTicks;
    final int stormSuperEvery;
    final int stormHunterEvery;
    final int stormScorchPercent;

    final float stormBoltPower;
    final boolean stormBreakBlocks;
    final int stormCraterRadius;
    final int stormCraterFire;
    final int stormCraterMagma;
    final boolean stormNetherrackFloor;

    final int stormBlastRadius;
    final int stormIgniteTicks;
    final int stormWitherTicks;
    final boolean stormMarkTarget;

    // ------------------------------------------------------------------ earthquake
    final boolean quakeEnabled;
    final boolean quakeBroadcast;
    final boolean quakeSkipCreative;
    final List<String> quakeWorlds;
    final long quakeDurationTicks;
    final int quakeRadius;
    final long quakeJoltEveryTicks;
    final double quakeJoltStrength;
    final boolean quakeNausea;
    final int quakeRockfall;
    final boolean quakeBreakBlocks;
    final int quakeFissures;
    final int quakeFissureLength;
    final int quakeFissureDepth;
    final int quakeLavaPercent;

    // ------------------------------------------------------------------ tornado
    final boolean tornadoEnabled;
    final boolean tornadoBroadcast;
    final boolean tornadoSkipCreative;
    final List<String> tornadoWorlds;
    final long tornadoDurationTicks;
    final double tornadoSpawnDistance;
    final double tornadoSpeed;
    final double tornadoHeight;
    final double tornadoPullRadius;
    final double tornadoLift;
    final boolean tornadoBreakBlocks;
    final double tornadoRipPerSecond;
    final int tornadoMaxDebris;

    // ------------------------------------------------------------------ blizzard
    final boolean blizzardEnabled;
    final boolean blizzardBroadcast;
    final boolean blizzardSkipCreative;
    final List<String> blizzardWorlds;
    final long blizzardDurationTicks;
    final int blizzardRadius;
    final boolean blizzardBreakBlocks;
    final double blizzardSnowPerSecond;
    final int blizzardMaxSnow;
    final boolean blizzardFreezeWater;
    final int blizzardMaxIce;
    final boolean blizzardThaw;
    final int blizzardFreezeTicks;
    final double blizzardFrostDamage;
    final int blizzardWarmLight;

    DisasterConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.cooldownMillis = clamp(config.getInt("cooldown-seconds", 120), 0, 3600, 120) * 1000L;
        int low = clamp(config.getInt("schedule.min-days", 2), 1, 365, 2);
        int high = clamp(config.getInt("schedule.max-days", 13), 1, 365, 13);
        this.scheduleMinDays = Math.min(low, high);
        this.scheduleMaxDays = Math.max(low, high);

        // -------------------------------------------------------------- meteor shower
        this.meteorEnabled = config.getBoolean("meteor-shower.enabled", true);
        this.broadcast = config.getBoolean("meteor-shower.broadcast", true);
        this.skipCreative = config.getBoolean("meteor-shower.skip-creative", true);
        this.worlds = lowercased(config.getStringList("meteor-shower.worlds"));

        this.meteorCount = clamp(config.getInt("meteor-shower.meteors", 20), 1, 60, 20);
        this.spawnRadius = clamp(config.getInt("meteor-shower.spawn-radius", 28), 2, 64, 28);
        this.fallHeight = clamp(config.getInt("meteor-shower.fall-height", 45), 10, 120, 45);
        this.meteorSpacingTicks = clamp(config.getInt("meteor-shower.ticks-between-meteors", 30), 5, 400, 30);
        this.meteorTimeoutTicks = clamp(config.getInt("meteor-shower.meteor-timeout-ticks", 200), 40, 1200, 200);

        this.explosionPower = power(config.getDouble("meteor-shower.explosion-power", 3.0d), 3.0f);
        this.breakBlocks = config.getBoolean("meteor-shower.break-blocks", true);
        this.scatterRadius = clamp(config.getInt("meteor-shower.scatter-radius", 3), 1, 10, 3);
        this.magmaPerImpact = clamp(config.getInt("meteor-shower.magma-per-impact", 6), 0, 40, 6);
        this.firePerImpact = clamp(config.getInt("meteor-shower.fire-per-impact", 8), 0, 40, 8);

        // -------------------------------------------------------------- thunder-hell storm
        this.stormEnabled = config.getBoolean("thunder-storm.enabled", true);
        this.stormBroadcast = config.getBoolean("thunder-storm.broadcast", true);
        this.stormSkipCreative = config.getBoolean("thunder-storm.skip-creative", true);
        this.stormWorlds = lowercased(config.getStringList("thunder-storm.worlds"));

        this.stormDurationTicks = clamp(config.getInt("thunder-storm.duration-seconds", 75), 10, 600, 75) * 20L;
        int radius = clamp(config.getInt("thunder-storm.radius", 40), 8, 96, 40);
        int minRadius = clamp(config.getInt("thunder-storm.min-radius", 4), 0, 64, 4);
        this.stormRadius = radius;
        this.stormMinRadius = Math.min(minRadius, radius - 2);
        this.stormBoltSpacingTicks = clamp(config.getInt("thunder-storm.ticks-between-bolts", 45), 10, 400, 45);
        this.stormBoltJitterTicks = clamp(config.getInt("thunder-storm.bolt-jitter-ticks", 20), 0, 200, 20);
        this.stormSuperEvery = clamp(config.getInt("thunder-storm.super-bolt-every", 5), 0, 50, 5);
        this.stormHunterEvery = clamp(config.getInt("thunder-storm.hunter-bolt-every", 4), 0, 50, 4);
        this.stormScorchPercent = clamp(config.getInt("thunder-storm.scorch-chance-percent", 35), 0, 100, 35);

        this.stormBoltPower = power(config.getDouble("thunder-storm.super-bolt-power", 4.0d), 4.0f);
        this.stormBreakBlocks = config.getBoolean("thunder-storm.break-blocks", true);
        this.stormCraterRadius = clamp(config.getInt("thunder-storm.crater-radius", 3), 1, 8, 3);
        this.stormCraterFire = clamp(config.getInt("thunder-storm.crater-fire", 12), 0, 60, 12);
        this.stormCraterMagma = clamp(config.getInt("thunder-storm.crater-magma", 7), 0, 60, 7);
        this.stormNetherrackFloor = config.getBoolean("thunder-storm.netherrack-floor", true);

        this.stormBlastRadius = clamp(config.getInt("thunder-storm.super-bolt-effect-radius", 6), 1, 24, 6);
        this.stormIgniteTicks = clamp(config.getInt("thunder-storm.ignite-seconds", 5), 0, 60, 5) * 20;
        this.stormWitherTicks = clamp(config.getInt("thunder-storm.wither-seconds", 4), 0, 60, 4) * 20;
        this.stormMarkTarget = config.getBoolean("thunder-storm.mark-target", true);

        // -------------------------------------------------------------- earthquake
        this.quakeEnabled = config.getBoolean("earthquake.enabled", true);
        this.quakeBroadcast = config.getBoolean("earthquake.broadcast", true);
        this.quakeSkipCreative = config.getBoolean("earthquake.skip-creative", true);
        this.quakeWorlds = lowercased(config.getStringList("earthquake.worlds"));
        this.quakeDurationTicks = clamp(config.getInt("earthquake.duration-seconds", 40), 10, 300, 40) * 20L;
        this.quakeRadius = clamp(config.getInt("earthquake.radius", 24), 6, 64, 24);
        this.quakeJoltEveryTicks = clamp(config.getInt("earthquake.jolt-every-ticks", 10), 5, 100, 10);
        this.quakeJoltStrength = fraction(config.getDouble("earthquake.jolt-strength", 0.22d), 0.0d, 1.0d, 0.22d);
        this.quakeNausea = config.getBoolean("earthquake.nausea", true);
        this.quakeRockfall = clamp(config.getInt("earthquake.rockfall", 18), 0, 80, 18);
        this.quakeBreakBlocks = config.getBoolean("earthquake.break-blocks", true);
        this.quakeFissures = clamp(config.getInt("earthquake.fissures", 4), 0, 12, 4);
        this.quakeFissureLength = clamp(config.getInt("earthquake.fissure-length", 12), 4, 32, 12);
        this.quakeFissureDepth = clamp(config.getInt("earthquake.fissure-depth", 5), 1, 12, 5);
        this.quakeLavaPercent = clamp(config.getInt("earthquake.fissure-lava-percent", 25), 0, 100, 25);

        // -------------------------------------------------------------- tornado
        this.tornadoEnabled = config.getBoolean("tornado.enabled", true);
        this.tornadoBroadcast = config.getBoolean("tornado.broadcast", true);
        this.tornadoSkipCreative = config.getBoolean("tornado.skip-creative", true);
        this.tornadoWorlds = lowercased(config.getStringList("tornado.worlds"));
        this.tornadoDurationTicks = clamp(config.getInt("tornado.duration-seconds", 50), 10, 300, 50) * 20L;
        this.tornadoSpawnDistance = clamp(config.getInt("tornado.spawn-distance", 24), 8, 48, 24);
        this.tornadoSpeed = fraction(config.getDouble("tornado.speed", 0.18d), 0.02d, 0.6d, 0.18d);
        this.tornadoHeight = clamp(config.getInt("tornado.height", 26), 8, 64, 26);
        this.tornadoPullRadius = clamp(config.getInt("tornado.pull-radius", 7), 2, 16, 7);
        this.tornadoLift = fraction(config.getDouble("tornado.lift", 0.35d), 0.0d, 1.0d, 0.35d);
        this.tornadoBreakBlocks = config.getBoolean("tornado.break-blocks", true);
        this.tornadoRipPerSecond = fraction(config.getDouble("tornado.rip-blocks-per-second", 3.0d), 0.0d, 20.0d, 3.0d);
        this.tornadoMaxDebris = clamp(config.getInt("tornado.max-debris", 60), 0, 300, 60);

        // -------------------------------------------------------------- blizzard
        this.blizzardEnabled = config.getBoolean("blizzard.enabled", true);
        this.blizzardBroadcast = config.getBoolean("blizzard.broadcast", true);
        this.blizzardSkipCreative = config.getBoolean("blizzard.skip-creative", true);
        this.blizzardWorlds = lowercased(config.getStringList("blizzard.worlds"));
        this.blizzardDurationTicks = clamp(config.getInt("blizzard.duration-seconds", 90), 15, 600, 90) * 20L;
        this.blizzardRadius = clamp(config.getInt("blizzard.radius", 28), 6, 64, 28);
        this.blizzardBreakBlocks = config.getBoolean("blizzard.break-blocks", true);
        this.blizzardSnowPerSecond = fraction(config.getDouble("blizzard.snow-per-second", 6.0d), 0.0d, 40.0d, 6.0d);
        this.blizzardMaxSnow = clamp(config.getInt("blizzard.max-snow", 320), 0, 2000, 320);
        this.blizzardFreezeWater = config.getBoolean("blizzard.freeze-water", true);
        this.blizzardMaxIce = clamp(config.getInt("blizzard.max-ice", 80), 0, 500, 80);
        this.blizzardThaw = config.getBoolean("blizzard.thaw", true);
        this.blizzardFreezeTicks = clamp(config.getInt("blizzard.freeze-seconds", 12), 2, 120, 12) * 20;
        this.blizzardFrostDamage = fraction(config.getDouble("blizzard.frost-damage", 1.0d), 0.0d, 10.0d, 1.0d);
        this.blizzardWarmLight = clamp(config.getInt("blizzard.warm-light", 11), 1, 15, 11);
    }

    /** Whether a world is on a kind's list; an empty list means every world. */
    static boolean allows(List<String> worlds, String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    boolean allowsWorld(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    boolean stormAllowsWorld(String worldName) {
        return stormWorlds.isEmpty() || stormWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private static List<String> lowercased(List<String> configured) {
        List<String> lowered = new ArrayList<String>();
        if (configured != null) {
            for (String name : configured) {
                if (name != null && !name.isEmpty()) lowered.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return lowered;
    }

    private static double fraction(double value, double min, double max, double fallback) {
        return value < min || value > max || Double.isNaN(value) ? fallback : value;
    }

    private static float power(double value, float fallback) {
        return (float) (value < 0.5d || value > 10.0d || Double.isNaN(value) ? fallback : value);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
