package chat.jaspr.disasters;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Owner 2026-10-02: three more kinds of natural disaster, and disasters in general twice as rare.
 *
 * Shipped config: one shared timer for every kind. Measured here against the schedule it replaces (two
 * kinds, each on its own independent 1-14 day timer), the average gap between disasters doubles, and adding
 * kinds no longer adds frequency. Kinds are drawn at random and never repeat back to back.
 */
public final class DisasterScheduleTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        DisasterConfig shipped = new DisasterConfig(YamlConfiguration.loadConfiguration(new File(args[0])));
        check(shipped.scheduleMinDays == 2 && shipped.scheduleMaxDays == 13, "shipped schedule window 2-13 days");
        check(shipped.meteorEnabled && shipped.stormEnabled && shipped.quakeEnabled && shipped.tornadoEnabled && shipped.blizzardEnabled, "all five kinds on");
        check(shipped.quakeBreakBlocks && shipped.quakeFissures == 4 && shipped.quakeRockfall == 18, "earthquake defaults");
        check(shipped.tornadoMaxDebris == 60 && shipped.tornadoSpeed < 0.28d, "tornado defaults (slower than a sprint)");
        check(shipped.blizzardThaw && shipped.blizzardMaxSnow == 320 && shipped.blizzardFreezeTicks == 240, "blizzard defaults (thaws afterwards)");

        // A live config from before the shared timer has no schedule section: it gets the same rarer default.
        YamlConfiguration old = new YamlConfiguration();
        old.set("meteor-shower.min-days", 1);
        old.set("meteor-shower.max-days", 14);
        DisasterConfig upgraded = new DisasterConfig(old);
        check(upgraded.scheduleMinDays == 2 && upgraded.scheduleMaxDays == 13, "old config falls back to the new schedule");

        Random random = new Random(20261002L);
        long day = DisasterConfig.MILLIS_PER_MC_DAY;

        // The new schedule: gaps between consecutive disasters.
        int samples = 200000;
        double sum = 0.0d;
        long lowest = Long.MAX_VALUE, highest = 0L;
        for (int i = 0; i < samples; i++) {
            long gap = Schedule.next(0L, shipped.scheduleMinDays, shipped.scheduleMaxDays, random);
            sum += gap;
            lowest = Math.min(lowest, gap);
            highest = Math.max(highest, gap);
        }
        double newMean = sum / samples / day;
        check(lowest >= 2L * day && highest < 14L * day, "every gap inside 2-14 days");
        check(Math.abs(newMean - Schedule.meanDays(2, 13)) < 0.05d && Math.abs(newMean - 8.0d) < 0.05d, "mean gap 8 days, got " + newMean);

        // The schedule it replaces: two kinds, each re-rolled 1-14 days (+ part of a day) after its own event.
        long[] due = { oldRoll(0L, random), oldRoll(0L, random) };
        long now = 0L, last = -1L;
        double gaps = 0.0d;
        int events = 0;
        while (events < samples) {
            int k = due[0] <= due[1] ? 0 : 1;
            now = due[k];
            if (last >= 0L) { gaps += now - last; events++; }
            last = now;
            due[k] = oldRoll(now, random);
        }
        double oldMean = gaps / events / day;
        double ratio = newMean / oldMean;
        check(ratio > 1.9d && ratio < 2.1d, "twice as rare as before: old mean " + oldMean + " days, new " + newMean + ", ratio " + ratio);

        // Kind choice: random, never the previous kind first when there is another, and fair over time.
        List<String> kinds = Arrays.asList("meteor", "storm", "quake", "tornado", "blizzard");
        Map<String, Integer> firsts = new HashMap<String, Integer>();
        String previous = null;
        for (int i = 0; i < 50000; i++) {
            List<String> order = Schedule.order(kinds, previous, random);
            check(order.size() == kinds.size() && order.containsAll(kinds), "every enabled kind is tried");
            check(previous == null || !order.get(0).equals(previous), "no kind twice in a row");
            if (previous != null) check(order.get(order.size() - 1).equals(previous), "the previous kind waits at the back");
            String first = order.get(0);
            firsts.put(first, firsts.containsKey(first) ? firsts.get(first) + 1 : 1);
            previous = first;
        }
        for (String kind : kinds) {
            double share = firsts.containsKey(kind) ? firsts.get(kind) / 50000.0d : 0.0d;
            check(share > 0.17d && share < 0.23d, kind + " drawn " + share + " of the time");
        }
        List<String> alone = Schedule.order(new ArrayList<String>(Arrays.asList("meteor")), "meteor", random);
        check(alone.size() == 1 && alone.get(0).equals("meteor"), "a single enabled kind still runs");

        System.out.println("DISASTER_SCHEDULE_OK old=" + String.format("%.2f", oldMean) + "d new=" + String.format("%.2f", newMean) + "d ratio=" + String.format("%.2f", ratio));
    }

    private static long oldRoll(long now, Random random) {
        int days = 1 + random.nextInt(14);
        return now + days * DisasterConfig.MILLIS_PER_MC_DAY + (long) (random.nextDouble() * DisasterConfig.MILLIS_PER_MC_DAY);
    }
}
