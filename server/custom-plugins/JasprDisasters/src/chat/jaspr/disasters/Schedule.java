package chat.jaspr.disasters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * When the next disaster comes, and which kind. Pure arithmetic, so it is tested without a server.
 *
 * Every kind shares one timer: after each disaster the next one is due somewhere between the configured
 * minimum and maximum number of Minecraft days later, and its kind is drawn at random from the enabled
 * kinds, never the same kind twice in a row when there is a choice. Adding kinds therefore adds variety,
 * not frequency. (Until 1.3.0 each kind kept its own 1-14 day timer, so two kinds meant a disaster every
 * four days or so on average; the shipped 2-13 day window makes them twice as rare, about every eight.)
 */
final class Schedule {
    private Schedule() {}

    /** The next due time: a whole number of days in [minDays, maxDays] plus a random part of one more. */
    static long next(long now, int minDays, int maxDays, Random random) {
        int low = Math.min(minDays, maxDays);
        int high = Math.max(minDays, maxDays);
        int days = low + (high > low ? random.nextInt(high - low + 1) : 0);
        // Spread inside the chosen day too, so events do not land on exact day boundaries.
        long jitter = (long) (random.nextDouble() * DisasterConfig.MILLIS_PER_MC_DAY);
        return now + days * DisasterConfig.MILLIS_PER_MC_DAY + jitter;
    }

    /** Average gap between disasters, in Minecraft days, for a schedule window. */
    static double meanDays(int minDays, int maxDays) {
        return (Math.min(minDays, maxDays) + Math.max(minDays, maxDays)) / 2.0d + 0.5d;
    }

    /**
     * The enabled kinds in the order to try them: shuffled, with the previous kind moved to the back when
     * there is any other. The first kind that has an eligible player runs; the rest wait for next time.
     */
    static <K> List<K> order(List<K> enabled, K last, Random random) {
        List<K> pool = new ArrayList<K>(enabled);
        Collections.shuffle(pool, random);
        if (pool.size() > 1 && last != null && pool.remove(last)) pool.add(last);
        return pool;
    }
}
