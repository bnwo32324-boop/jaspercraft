package chat.jaspr.invasions;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * JasprInvasions' sleep schedule (run: tests/invasions.test.cjs): sleeping marks a player, and their invasion is due only
 * on a night at least days-after-sleep (7) whole days later, never at the moment of the sleep. The shipped config lets
 * everyone sleep. Prints INVASION_SCHEDULE_OK.
 */
public final class InvasionScheduleTest {
    private static void ok(boolean c, String what) { if (!c) throw new AssertionError(what); }

    public static void main(String[] args) {
        long day = InvasionConfig.TICKS_PER_DAY;
        // Night runs from dusk (beds usable) to just before dawn, on any day.
        ok(!PlayerProgress.night(6000) && !PlayerProgress.night(12499) && PlayerProgress.night(12500) && PlayerProgress.night(18000)
            && PlayerProgress.night(23499) && !PlayerProgress.night(23500) && PlayerProgress.night(5 * day + 13000), "night window");
        // Slept on day 10 at 13000: nothing until seven whole days have passed, then on the first night.
        long slept = 10 * day + 13000;
        ok(!PlayerProgress.due(slept, slept, 13000, 7), "never at the moment you sleep");
        ok(!PlayerProgress.due(slept, slept + 101, 13101, 7), "not after the night is slept through");
        for (int d = 1; d < 7; d++) ok(!PlayerProgress.due(slept, slept + d * day, 13000, 7), "not after " + d + " days");
        ok(!PlayerProgress.due(slept, slept + 7 * day - 1, 12999, 7), "not a tick short of seven days");
        ok(PlayerProgress.due(slept, slept + 7 * day, 13000, 7), "due on the night seven days later");
        ok(!PlayerProgress.due(slept, slept + 7 * day + 11000, 0, 7), "not by day");
        ok(PlayerProgress.due(slept, 18 * day + 12600, 12600, 7), "or on a later night");
        ok(!PlayerProgress.due(-1L, 100 * day + 13000, 13000, 7), "never without a sleep");
        // Slept late at night: the next nights of the seventh day are too early, the one after is not.
        long late = 3 * day + 23000;
        ok(!PlayerProgress.due(late, 10 * day + 13000, 13000, 7) && PlayerProgress.due(late, 10 * day + 23100, 23100, 7), "late sleeper");
        // The shipped config: seven days, and sleeping always works.
        InvasionConfig shipped = new InvasionConfig(YamlConfiguration.loadConfiguration(new File(args[0])));
        ok(shipped.daysAfterSleep == 7, "days-after-sleep 7 (" + shipped.daysAfterSleep + ")");
        ok(!shipped.preventSleep, "sleep is never refused by the shipped config");
        InvasionConfig empty = new InvasionConfig(new YamlConfiguration());
        ok(empty.daysAfterSleep == 7 && !empty.preventSleep, "the same defaults without a config");
        System.out.println("INVASION_SCHEDULE_OK");
    }
}
