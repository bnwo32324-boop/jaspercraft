package chat.jaspr.rpg;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables for both halves of the system. The cost curve itself lives in {@link StatCosts}. */
final class RpgConfig {
    // ------------------------------------------------------------------ stats
    final boolean statsEnabled;
    final double costMultiplier;
    final double bonusMultiplier;
    final double capMultiplier;
    final boolean resetOnDeath;
    final double healthPerLevel;

    // ------------------------------------------------------------------ armaments
    final boolean armamentsEnabled;
    final int maxLevel;
    final int level1Experience;
    final double experienceMultiplier;
    final int tokensPerLevel;
    final double enchantChance;
    final double creativeChance;
    final boolean armorEnabled;

    RpgConfig(FileConfiguration config) {
        this.statsEnabled = config.getBoolean("stats.enabled", true);
        this.costMultiplier = ratio(config.getDouble("stats.cost-multiplier", 1.0d), 0.05d, 20.0d, 1.0d);
        this.bonusMultiplier = ratio(config.getDouble("stats.bonus-multiplier", 1.0d), 0.0d, 10.0d, 1.0d);
        this.capMultiplier = ratio(config.getDouble("stats.cap-multiplier", 1.0d), 0.1d, 10.0d, 1.0d);
        this.resetOnDeath = config.getBoolean("stats.reset-on-death", true);
        this.healthPerLevel = ratio(config.getDouble("stats.health-per-level", 2.0d), 0.0d, 10.0d, 2.0d);

        this.armamentsEnabled = config.getBoolean("armaments.enabled", true);
        this.maxLevel = clamp(config.getInt("armaments.max-level", 10), 1, 100, 10);
        this.level1Experience = clamp(config.getInt("armaments.level-1-experience", 50), 1, 100000, 50);
        this.experienceMultiplier = ratio(config.getDouble("armaments.experience-multiplier", 1.6d), 1.01d, 10.0d, 1.6d);
        this.tokensPerLevel = clamp(config.getInt("armaments.tokens-per-level", 1), 0, 10, 1);
        this.enchantChance = ratio(config.getDouble("armaments.enhance-chance", 0.35d), 0.0d, 1.0d, 0.35d);
        // Creative is where gear gets spawned for testing, so by default everything pulled from
        // the creative menu is enhanced rather than rolled.
        this.creativeChance = ratio(config.getDouble("armaments.enhance-chance-creative", 1.0d), 0.0d, 1.0d, 1.0d);
        this.armorEnabled = config.getBoolean("armaments.armor", true);
    }

    /** Effective cap for a stat once the server-wide cap multiplier is applied. */
    int capFor(StatType stat) {
        int scaled = (int) Math.round(stat.cap * capMultiplier);
        return Math.max(1, scaled);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static double ratio(double value, double min, double max, double fallback) {
        return value < min || value > max || Double.isNaN(value) ? fallback : value;
    }
}
