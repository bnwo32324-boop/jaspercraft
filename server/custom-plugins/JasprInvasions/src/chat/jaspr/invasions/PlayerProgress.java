package chat.jaspr.invasions;

import java.util.UUID;

/**
 * One player's standing with the invasions.
 *
 * Progression is per player and always has been the point of this system: two people standing in
 * the same base on the same night should not face the same thing if one of them joined an hour ago
 * and the other has been here for weeks. The clock starts the first time a player is seen, so
 * everybody on the server begins at the bottom of the curve together.
 */
final class PlayerProgress {
    final UUID id;
    String name;
    /** Ticks spent online in a mode where invasions can find you. */
    long playedTicks;
    int invasionsSurvived;
    int invasionsFaced;
    long lastInvasionDay = -1L;
    /** Set once the player has been told an invasion is coming tonight. */
    transient boolean warnedThisNight;

    PlayerProgress(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Days played, where one day is a Minecraft day of real time: twenty minutes. */
    double daysPlayed() {
        return (double) playedTicks / (double) InvasionConfig.TICKS_PER_DAY;
    }

    /** True once this player has been around long enough for the invasions to notice them. */
    boolean eligible(InvasionConfig settings) {
        return daysPlayed() >= settings.minDaysPlayed;
    }

    /**
     * Where this player sits on the curve, from 0 at their first eligible night to 1 once they
     * have played long enough to have earned the worst of it.
     */
    double difficulty(InvasionConfig settings) {
        double span = settings.fullDifficultyDaysPlayed - settings.minDaysPlayed;
        if (span <= 0.0d) return 1.0d;
        double raw = (daysPlayed() - settings.minDaysPlayed) / span;
        if (raw < 0.0d) return 0.0d;
        if (raw > 1.0d) return 1.0d;
        return raw;
    }

    /** How long until this player's first invasion, in real minutes. Zero once they are eligible. */
    long minutesUntilEligible(InvasionConfig settings) {
        double remainingDays = settings.minDaysPlayed - daysPlayed();
        if (remainingDays <= 0.0d) return 0L;
        return (long) Math.ceil(remainingDays * 20.0d);
    }
}
