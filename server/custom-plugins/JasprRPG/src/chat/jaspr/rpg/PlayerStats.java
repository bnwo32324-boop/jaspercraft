package chat.jaspr.rpg;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** One player's stat levels. Everything resets to nothing when they die. */
final class PlayerStats {
    final UUID id;
    String name;
    private final Map<StatType, Integer> levels = new EnumMap<StatType, Integer>(StatType.class);

    PlayerStats(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    int level(StatType stat) {
        Integer value = levels.get(stat);
        return value == null ? 0 : value.intValue();
    }

    void set(StatType stat, int level) {
        if (level <= 0) levels.remove(stat);
        else levels.put(stat, Integer.valueOf(level));
    }

    /** The multiplier this player currently has for a stat, with the server bonus scale applied. */
    double multiplier(StatType stat, RpgConfig settings) {
        double raw = stat.multiplier(level(stat));
        if (settings.bonusMultiplier == 1.0d) return raw;
        return 1.0d + (raw - 1.0d) * settings.bonusMultiplier;
    }

    int totalLevels() {
        int total = 0;
        for (Integer value : levels.values()) total += value.intValue();
        return total;
    }

    boolean isEmpty() { return levels.isEmpty(); }

    /** Death wipes the sheet. Everything you bought is gone and paid for again from scratch. */
    void clear() { levels.clear(); }

    Map<StatType, Integer> view() { return levels; }
}
