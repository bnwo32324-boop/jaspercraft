package chat.jaspr.rpg;

/**
 * The upgrade price of a stat level, kept faithful to GokiStats.
 *
 * The original charges experience levels on a curve that starts gentle and steepens: the cost of
 * buying the level after {@code current} is {@code (current^1.6 + 6 + current)}, truncated to an
 * integer, then scaled by a server-wide multiplier. Level 0 to 1 costs 6, and by level 9 a single
 * step costs 48, which is what gives the system its shape. Mastery ranks (levels 11-15, added
 * 2026-10-02) cost a flat {@link #MASTERY} levels each instead of climbing on past 50.
 */
final class StatCosts {
    /** Experience levels for each mastery rank (buying level 11, 12, ... 15). */
    static final int MASTERY = 50;

    private StatCosts() {}

    /** Experience levels needed to go from {@code currentLevel} to the next one. */
    static int cost(int currentLevel, double multiplier) {
        double raw = currentLevel >= StatType.ORDINARY ? MASTERY : Math.pow(currentLevel, 1.6d) + 6.0d + currentLevel;
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
