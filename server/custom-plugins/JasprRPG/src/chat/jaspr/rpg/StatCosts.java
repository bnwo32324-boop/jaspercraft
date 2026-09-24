package chat.jaspr.rpg;

/**
 * The upgrade price of a stat level, kept faithful to GokiStats.
 *
 * The original charges experience levels on a curve that starts gentle and steepens: the cost of
 * buying the level after {@code current} is {@code (current^1.6 + 6 + current)}, truncated to an
 * integer, then scaled by a server-wide multiplier. Level 0 to 1 costs 6, and by level 9 a single
 * step costs 51, which is what gives the system its shape. This is deliberately the one number in
 * the plugin that is not casually tunable per stat.
 */
final class StatCosts {
    private StatCosts() {}

    /** Experience levels needed to go from {@code currentLevel} to the next one. */
    static int cost(int currentLevel, double multiplier) {
        double raw = Math.pow(currentLevel, 1.6d) + 6.0d + currentLevel;
        int scaled = (int) (raw * multiplier);
        return Math.max(1, scaled);
    }

    /** Total experience levels spent to reach {@code level} from nothing. */
    static int totalSpent(int level, double multiplier) {
        int total = 0;
        for (int step = 0; step < level; step++) total += cost(step, multiplier);
        return total;
    }
}
