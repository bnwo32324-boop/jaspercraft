package chat.jaspr.invasions;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Tunables.
 *
 * The tier tables themselves live in code, because they are a progression curve rather than a
 * setting; what lives here is everything an operator realistically wants to move - how often
 * invasions happen, how long a player must have played before one finds them, how many invaders
 * the server is willing to carry, and how much of a base they are allowed to chew through.
 */
final class InvasionConfig {
    /** One Minecraft day is 24000 ticks, which is 20 real minutes. */
    static final long TICKS_PER_DAY = 24000L;

    final boolean enabled;

    // -------------------------------------------------------------- schedule
    final int firstInvasionDay;
    final int invadeEveryDays;
    final boolean broadcast;
    final List<String> worlds;

    // -------------------------------------------------------------- progression
    final double minDaysPlayed;
    final double fullDifficultyDaysPlayed;
    final boolean countCreativeTime;

    // -------------------------------------------------------------- spawning
    final int spawnRangeMin;
    final int spawnRangeMax;
    final int attemptsPerSpawn;
    final int maxLightForSpawn;
    final int failedTriesBeforeLitSpawns;
    final int maxConcurrentPerPlayer;
    final int maxConcurrentGlobal;
    final double countMultiplier;
    final long waveSpacingTicks;

    // -------------------------------------------------------------- behaviour
    final int aiTickRate;
    final int omniscienceRange;
    final int leashRange;
    final double followRange;
    final boolean minersEnabled;
    final boolean soldiersEnabled;
    final long minerCooldownTicks;
    final long soldierCooldownTicks;
    final int maxPillarHeight;
    final double sunriseDamagePerSecond;
    final boolean preventSleep;
    final double gearDropChance;
    final boolean nameInvaders;

    // -------------------------------------------------------------- base damage
    final boolean breakBlocks;
    final long repairDelayTicks;
    final int maxRecordedBlocks;
    final int repairBatchSize;
    final Set<Material> unbreakable;

    InvasionConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);

        this.firstInvasionDay = clamp(config.getInt("schedule.first-invasion-day", 3), 0, 10000, 3);
        this.invadeEveryDays = clamp(config.getInt("schedule.invade-every-days", 3), 1, 100, 3);
        this.broadcast = config.getBoolean("schedule.broadcast", true);
        this.worlds = lowercased(config.getStringList("schedule.worlds"));

        this.minDaysPlayed = positive(config.getDouble("progression.min-days-played", 3.0d), 3.0d);
        double full = positive(config.getDouble("progression.full-difficulty-days-played", 25.0d), 25.0d);
        this.fullDifficultyDaysPlayed = Math.max(full, this.minDaysPlayed + 1.0d);
        this.countCreativeTime = config.getBoolean("progression.count-creative-time", false);

        int min = clamp(config.getInt("spawning.range-min", 18), 4, 96, 18);
        int max = clamp(config.getInt("spawning.range-max", 40), 6, 128, 40);
        this.spawnRangeMin = Math.min(min, max);
        this.spawnRangeMax = Math.max(min, max);
        this.attemptsPerSpawn = clamp(config.getInt("spawning.attempts-per-spawn", 14), 1, 80, 14);
        this.maxLightForSpawn = clamp(config.getInt("spawning.max-light", 7), 0, 15, 7);
        this.failedTriesBeforeLitSpawns = clamp(config.getInt("spawning.failed-tries-before-lit-spawns", 8), 1, 200, 8);
        this.maxConcurrentPerPlayer = clamp(config.getInt("spawning.max-concurrent-per-player", 14), 1, 60, 14);
        this.maxConcurrentGlobal = clamp(config.getInt("spawning.max-concurrent-global", 50), 1, 300, 50);
        this.countMultiplier = ratio(config.getDouble("spawning.count-multiplier", 1.0d), 0.1d, 4.0d, 1.0d);
        this.waveSpacingTicks = clamp(config.getInt("spawning.ticks-between-waves", 600), 40, 12000, 600);

        this.aiTickRate = clamp(config.getInt("behaviour.ai-tick-rate", 10), 2, 100, 10);
        this.omniscienceRange = clamp(config.getInt("behaviour.omniscience-range", 72), 8, 160, 72);
        this.leashRange = clamp(config.getInt("behaviour.leash-range", 96), 16, 256, 96);
        this.followRange = clamp(config.getInt("behaviour.follow-range", 72), 16, 160, 72);
        this.minersEnabled = config.getBoolean("behaviour.miners", true);
        this.soldiersEnabled = config.getBoolean("behaviour.soldiers", true);
        this.minerCooldownTicks = clamp(config.getInt("behaviour.miner-cooldown-ticks", 25), 5, 400, 25);
        this.soldierCooldownTicks = clamp(config.getInt("behaviour.soldier-cooldown-ticks", 30), 5, 400, 30);
        this.maxPillarHeight = clamp(config.getInt("behaviour.max-pillar-height", 12), 1, 40, 12);
        this.sunriseDamagePerSecond = ratio(config.getDouble("behaviour.sunrise-damage-per-second", 4.0d), 0.0d, 100.0d, 4.0d);
        this.preventSleep = config.getBoolean("behaviour.prevent-sleep", true);
        this.gearDropChance = ratio(config.getDouble("behaviour.gear-drop-chance", 0.0d), 0.0d, 1.0d, 0.0d);
        this.nameInvaders = config.getBoolean("behaviour.name-invaders", false);

        this.breakBlocks = config.getBoolean("base-damage.break-blocks", true);
        this.repairDelayTicks = clamp(config.getInt("base-damage.repair-delay-seconds", 300), 5, 7200, 300) * 20L;
        this.maxRecordedBlocks = clamp(config.getInt("base-damage.max-recorded-blocks", 20000), 100, 200000, 20000);
        this.repairBatchSize = clamp(config.getInt("base-damage.repair-batch-size", 60), 1, 2000, 60);

        Set<Material> blocked = EnumSet.noneOf(Material.class);
        List<String> names = config.getStringList("base-damage.unbreakable");
        if (names == null || names.isEmpty()) {
            names = defaultUnbreakable();
        }
        for (String name : names) {
            if (name == null) continue;
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null) blocked.add(material);
        }
        this.unbreakable = blocked;
    }

    private static List<String> defaultUnbreakable() {
        List<String> names = new ArrayList<String>();
        names.add("BEDROCK");
        names.add("BARRIER");
        names.add("OBSIDIAN");
        names.add("ENDER_PORTAL");
        names.add("ENDER_PORTAL_FRAME");
        names.add("PORTAL");
        names.add("END_GATEWAY");
        names.add("COMMAND");
        names.add("COMMAND_CHAIN");
        names.add("COMMAND_REPEATING");
        names.add("STRUCTURE_BLOCK");
        names.add("MOB_SPAWNER");
        names.add("BEACON");
        names.add("WATER");
        names.add("STATIONARY_WATER");
        names.add("LAVA");
        names.add("STATIONARY_LAVA");
        return names;
    }

    boolean allowsWorld(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName.toLowerCase(Locale.ROOT));
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

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static double positive(double value, double fallback) {
        return value <= 0.0d || Double.isNaN(value) ? fallback : value;
    }

    private static double ratio(double value, double min, double max, double fallback) {
        return value < min || value > max || Double.isNaN(value) ? fallback : value;
    }
}
