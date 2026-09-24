package chat.jaspr.rpg;

import java.util.Locale;

/**
 * The stat roster, rebuilt from the GokiStats design.
 *
 * Each stat carries the three numbers that define it: how many levels it can reach, what one level
 * is worth, and what it actually does. The growth base is geometric - a stat at level n is worth
 * base^n - which is what makes early levels cheap and meaningful and late levels a real investment.
 * Upgrade cost is shared by every stat and lives in {@link StatCosts}.
 */
enum StatType {
    MINING       ("Mining",          10, 1.3000d, "Break stone and ore faster"),
    DIGGING      ("Digging",         10, 1.3000d, "Break earth faster"),
    CHOPPING     ("Chopping",        10, 1.3000d, "Break wood faster"),
    TRIMMING     ("Trimming",        10, 1.3000d, "Break foliage faster"),
    PROTECTION   ("Protection",      10, 1.0400d, "Take less damage while armoured"),
    TEMPERING    ("Tempering",       10, 1.0600d, "Your gear wears out more slowly"),
    TOUGH_SKIN   ("Tough Skin",      10, 1.0300d, "Take less damage from everything"),
    FEATHER_FALL ("Feather Fall",    10, 1.1200d, "Take less falling damage"),
    LEAPER_H     ("Leaper",          10, 1.0650d, "Jump further forward"),
    LEAPER_V     ("High Leaper",     10, 1.0650d, "Jump higher"),
    SWIMMING     ("Swimming",        10, 1.1000d, "Swim faster"),
    CLIMBING     ("Climbing",        10, 1.1000d, "Climb ladders and vines faster"),
    PUGILISM     ("Pugilism",        10, 1.0300d, "Hit harder with bare hands"),
    SWORDSMANSHIP("Swordsmanship",   10, 1.0895d, "Hit harder with a sword"),
    BOWMANSHIP   ("Bowmanship",      10, 1.0895d, "Shoot harder with a bow"),
    REAPER       ("Reaper",          10, 1.0768d, "Gain more experience from kills"),
    FURNACE      ("Furnace Finesse", 10, 1.1000d, "Your furnaces smelt faster"),
    TREASURE     ("Treasure Finder",  3, 1.0000d, "Find treasure in grass, dirt, sand and leaves"),
    STEALTH      ("Stealth",         10, 1.3416d, "Monsters notice you from further away"),
    STEADY_GUARD ("Steady Guard",    10, 1.3615d, "Blocking absorbs far more"),
    MAGICIAN     ("Mining Magician", 10, 1.1000d, "Ores sometimes yield more"),
    HEALTH       ("Health",          10, 1.0000d, "Raises your maximum health"),
    ROLL         ("Roll",            10, 1.1000d, "Roll with a blow and take less of it");

    final String display;
    final int cap;
    final double base;
    final String description;

    StatType(String display, int cap, double base, String description) {
        this.display = display;
        this.cap = cap;
        this.base = base;
        this.description = description;
    }

    String key() { return name().toLowerCase(Locale.ROOT); }

    /**
     * What this stat is worth at the given level. Geometric growth, so the value at level n is
     * base to the power n, and level 0 is always exactly 1.0 - no bonus, no penalty.
     */
    double multiplier(int level) {
        if (level <= 0) return 1.0d;
        return Math.pow(base, Math.min(level, cap));
    }

    /** The same growth expressed as a fraction, for stats that read better as "+18%". */
    double bonusFraction(int level) {
        return multiplier(level) - 1.0d;
    }

    static StatType byKey(String key) {
        if (key == null) return null;
        for (StatType stat : values()) if (stat.key().equals(key.toLowerCase(Locale.ROOT))) return stat;
        return null;
    }
}
