package chat.jaspr.rpg;

import java.util.UUID;

/**
 * The little other plugins may ask of the stat sheet (called through reflection, so nobody needs this plugin at
 * compile time). JasprApocalypse's sentry turrets read their owner's Engineering here, even while the owner is away.
 */
public final class RpgApi {
    private static volatile RpgPlugin plugin;

    private RpgApi() {}

    static void bind(RpgPlugin p) { plugin = p; }

    /** A player's level in a stat by key ("engineering", "health", ...); 0 when unknown. */
    public static int level(UUID player, String stat) {
        RpgPlugin p = plugin;
        StatType type = StatType.byKey(stat);
        if (p == null || type == null || player == null || p.stats() == null) return 0;
        PlayerStats sheet = p.stats().find(player);
        return sheet == null ? 0 : sheet.level(type);
    }

    /**
     * A read-only summary of a player's stat sheet for the Field Journal (JasprJournal calls this through reflection):
     * {total ranks bought, stats with at least one rank, number of stats, stats that can be raised right now with
     * {@code xpLevels} experience levels, the cheapest next price in levels (-1 when every stat is at its cap)}.
     * Never changes anything.
     */
    public static int[] summary(UUID player, int xpLevels) {
        RpgPlugin p = plugin;
        if (p == null || player == null || p.stats() == null) return null;
        PlayerStats sheet = p.stats().find(player);
        RpgConfig settings = p.settings();
        int ranks = 0, raised = 0, total = 0, affordable = 0, cheapest = -1;
        for (StatType stat : StatType.values()) {
            total++;
            int level = sheet == null ? 0 : sheet.level(stat);
            if (level > 0) { ranks += level; raised++; }
            if (level >= settings.capFor(stat)) continue;
            int cost = StatCosts.cost(level, settings.costMultiplier);
            if (cheapest < 0 || cost < cheapest) cheapest = cost;
            if (xpLevels >= cost) affordable++;
        }
        return new int[] {ranks, raised, total, affordable, cheapest};
    }

    /** Damage multiplier a player's sentry turrets get from Engineering (1.0 without it). */
    public static double turretMultiplier(UUID owner) {
        RpgPlugin p = plugin;
        if (p == null || owner == null || p.stats() == null) return 1.0d;
        PlayerStats sheet = p.stats().find(owner);
        return sheet == null ? 1.0d : sheet.multiplier(StatType.ENGINEERING, p.settings());
    }
}
