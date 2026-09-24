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

    // ------------------------------------------------------------------ meteor shower
    final boolean meteorEnabled;
    final int meteorMinDays;
    final int meteorMaxDays;
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
    final int stormMinDays;
    final int stormMaxDays;
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

    DisasterConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.cooldownMillis = clamp(config.getInt("cooldown-seconds", 120), 0, 3600, 120) * 1000L;

        // -------------------------------------------------------------- meteor shower
        this.meteorEnabled = config.getBoolean("meteor-shower.enabled", true);
        int low = clamp(config.getInt("meteor-shower.min-days", 1), 1, 365, 1);
        int high = clamp(config.getInt("meteor-shower.max-days", 14), 1, 365, 14);
        this.meteorMinDays = Math.min(low, high);
        this.meteorMaxDays = Math.max(low, high);
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
        int stormLow = clamp(config.getInt("thunder-storm.min-days", 1), 1, 365, 1);
        int stormHigh = clamp(config.getInt("thunder-storm.max-days", 14), 1, 365, 14);
        this.stormMinDays = Math.min(stormLow, stormHigh);
        this.stormMaxDays = Math.max(stormLow, stormHigh);
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

    private static float power(double value, float fallback) {
        return (float) (value < 0.5d || value > 10.0d || Double.isNaN(value) ? fallback : value);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
