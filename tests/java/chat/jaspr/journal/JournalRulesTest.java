package chat.jaspr.journal;

import java.util.Random;

/** Pure checks of the Field Journal's arithmetic. Prints JOURNAL_RULES_PASS and the sample payloads the node test parses. */
public final class JournalRulesTest {
    private static int checks;
    private static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    /** The siege rule, as JasprApocalypse SiegeRules.bloodMoon writes it (the node test pins this text against the real source). */
    static boolean siege(long fullTime, int every) {
        long time = Math.floorMod(fullTime, 24000L);
        return every > 0 && time >= 13000 && time < 23000 && Math.floorMod(fullTime / 24000L + 1, every) == 0;
    }

    public static void main(String[] args) {
        phases();
        bloodMoon();
        invasion();
        disasters();
        effects();
        payload();
        System.out.println("JOURNAL_RULES_PASS checks=" + checks);
    }

    static void phases() {
        check("Morning".equals(JournalRules.phase(0)) && "Morning".equals(JournalRules.phase(5999)), "the first half of the day is morning");
        check("Midday".equals(JournalRules.phase(6000)) && "Midday".equals(JournalRules.phase(11999)), "then midday, up to sunset");
        check("Dusk".equals(JournalRules.phase(12000)) && "Dusk".equals(JournalRules.phase(12999)), "dusk");
        check("Night".equals(JournalRules.phase(13000)) && "Night".equals(JournalRules.phase(22999)), "night");
        check("Dawn".equals(JournalRules.phase(23000)) && "Dawn".equals(JournalRules.phase(23999)), "dawn");
        check("Morning".equals(JournalRules.phase(24000)), "the next day starts");
        check("Morning".equals(JournalRules.phase(24000L * 7 + 5)), "any day");
        check("Dawn".equals(JournalRules.phase(-1)), "negative times wrap");
        check(JournalRules.day(0) == 0 && JournalRules.day(23999) == 0 && JournalRules.day(24000) == 1, "day numbers start at 0 like /invasion status");
    }

    /** Reference by simulation: step the clock until the siege rule first holds, count the night starts passed on the way. */
    static int reference(long now, int every) {
        long tod = Math.floorMod(now, 24000L);
        if (siege(now, every)) return JournalRules.NOW;
        int bias = tod >= 13000 && tod < 23000 ? 1 : 0;
        long start = -1;
        for (long t = now + 1; t < now + 24000L * 400; t++) {
            if (Math.floorMod(t, 100L) != 0) { t += 100 - Math.floorMod(t, 100L) - 1; continue; }
            if (siege(t, every)) { start = t; break; }
        }
        if (start < 0) return -1;
        // the blood moon begins at the start of its night; count night starts in (now, start]
        long starts = 0;
        for (long k = Math.floorDiv(now, 24000L); k <= Math.floorDiv(start, 24000L) + 1; k++) {
            long s = k * 24000L + 13000L;
            if (s > now && s <= start) starts++;
        }
        return (int) (starts - 1 + bias);
    }

    static void bloodMoon() {
        for (int every : new int[] {1, 2, 3, 4, 7}) {
            final int e = every;
            JournalRules.BloodMoonRule rule = new JournalRules.BloodMoonRule() { public boolean at(long t) { return siege(t, e); } };
            Random random = new Random(every * 977L);
            for (int i = 0; i < 400; i++) {
                long now = (long) (random.nextDouble() * 24000L * 60);
                if (i < 24) now = (long) i * 1000L + 24000L * 3L;           // every hour of the day, including the edges
                if (i == 24) now = 13000L; if (i == 25) now = 22999L; if (i == 26) now = 23000L; if (i == 27) now = 12999L;
                int got = JournalRules.bloodMoonNights(now, rule), want = reference(now, every);
                check(got == want, "every=" + every + " now=" + now + " got=" + got + " want=" + want);
            }
        }
        // Worked examples with the shipped interval 3 (Blood Moon on the nights of days 2, 5, 8, ...).
        JournalRules.BloodMoonRule three = new JournalRules.BloodMoonRule() { public boolean at(long t) { return siege(t, 3); } };
        check(JournalRules.bloodMoonNights(0, three) == 2, "morning of day 0: two nights away");
        check(JournalRules.bloodMoonNights(24000L * 2, three) == 0, "morning of day 2: tonight");
        check(JournalRules.bloodMoonNights(24000L * 2 + 13000, three) == JournalRules.NOW, "dusk falls on the Blood Moon");
        check(JournalRules.bloodMoonNights(24000L * 2 + 22999, three) == JournalRules.NOW, "still under way before dawn");
        check(JournalRules.bloodMoonNights(24000L * 2 + 23000, three) == 2, "dawn after it: the next is two nights on");
        check(JournalRules.bloodMoonNights(24000L * 4 + 14000, three) == 0 || JournalRules.bloodMoonNights(24000L * 4 + 14000, three) == 1, "mid-night counts the next one");
        check(JournalRules.bloodMoonNights(24000L * 4 + 14000, three) == 1, "the night of day 4 is not a Blood Moon; day 5's is tomorrow night");
        check(JournalRules.bloodMoonNights(0, null) == -1, "no rule: none");
        JournalRules.BloodMoonRule never = new JournalRules.BloodMoonRule() { public boolean at(long t) { return false; } };
        check(JournalRules.bloodMoonNights(5000, never) == -1, "a rule that never holds ends the look-ahead");
    }

    static void invasion() {
        long day = 24000L;
        int[] none = JournalRules.invasion(false, -1L, 5 * day, 7);
        check(none[0] == JournalRules.INV_NONE, "nothing marked");
        int[] waiting = JournalRules.invasion(false, 3 * day + 500, 5 * day, 7);
        check(waiting[0] == JournalRules.INV_WAITING && waiting[1] == 10, "slept on day 3: from day 10 (like the status line)");
        int[] due = JournalRules.invasion(false, 3 * day, 10 * day, 7);
        check(due[0] == JournalRules.INV_DUE, "the wait is over");
        check(JournalRules.invasion(false, 3 * day, 10 * day - 1, 7)[0] == JournalRules.INV_WAITING, "one tick short");
        check(JournalRules.invasion(true, -1L, 5 * day, 7)[0] == JournalRules.INV_UNDERWAY, "under way beats the mark");
        check(JournalRules.invasion(true, 3 * day, 99 * day, 7)[0] == JournalRules.INV_UNDERWAY, "under way beats a due mark");
    }

    static void disasters() {
        check(JournalRules.disasterState(null) == -1, "no plugin");
        check(JournalRules.disasterState("quiet") == 0 && JournalRules.disasterState("brewing") == 1, "quiet and brewing");
        check(JournalRules.disasterState("active:Tornado") == 2 && "Tornado".equals(JournalRules.disasterKind("active:Tornado")), "active with its kind");
        check(JournalRules.disasterKind("quiet").isEmpty() && JournalRules.disasterKind(null).isEmpty(), "no kind when none runs");
        check(JournalRules.disasterState("surprise") == 0, "anything unknown reads as quiet: no exact time can leak");
        check(JournalRules.disasterKind("active:\"><script>").indexOf('"') < 0, "kind is cleaned");
    }

    static void effects() {
        check("Water Breathing".equals(JournalRules.effectName("WATER_BREATHING", 0)), "water breathing");
        check("Haste II".equals(JournalRules.effectName("FAST_DIGGING", 1)), "haste");
        check("Resistance IV".equals(JournalRules.effectName("DAMAGE_RESISTANCE", 3)), "resistance");
        check("Jump Boost 11".equals(JournalRules.effectName("JUMP", 10)), "past the table of numerals");
        check("Some Future Thing".equals(JournalRules.effectName("SOME_FUTURE_THING", 0)), "title case fallback");
        check("?".equals(JournalRules.titleCase("")) && "?".equals(JournalRules.titleCase("__")), "empty names");
        check(JournalRules.shownSeconds(70) == 0 && JournalRules.shownSeconds(100) == 0, "item-kept effects show no time");
        check(JournalRules.shownSeconds(101) == 5 && JournalRules.shownSeconds(2400) == 120, "a timed effect shows seconds");
        check(JournalRules.shownSeconds(Integer.MAX_VALUE) == 3599, "capped");
    }

    static void payload() {
        JournalRules.Snapshot s = new JournalRules.Snapshot();
        s.day = 12; s.phase = "Dusk"; s.moon = Integer.valueOf(2); s.invasion = new int[] {1, 15};
        s.disaster = Integer.valueOf(1); s.level = 37; s.xp = 20; s.rpg = new int[] {14, 9, 45, 3, 11};
        s.effect("Water Breathing", 0); s.effect("Haste II", 23);
        String full = s.json(true), key = s.json(false);
        check(full.equals("{\"v\":1,\"d\":12,\"ph\":\"Dusk\",\"bm\":2,\"iv\":[1,15],\"dz\":[1,\"\"],\"lv\":37,\"xp\":20,\"rp\":[14,9,45,3,11],\"fx\":[[\"Water Breathing\",0],[\"Haste II\",23]]}"), full);
        check(key.contains("[\"Haste II\",0]") && !key.contains(",23]"), "the change key leaves the countdowns out");
        s.effect("Haste II", 22);
        check(!s.json(true).equals(full) && s.json(false).contains("Haste II\",0],[\"Haste II\""), "another effect changes the key");
        System.out.println("SAMPLE " + full);
        // Parts that do not exist are left out, so the client hides their rows.
        JournalRules.Snapshot bare = new JournalRules.Snapshot();
        bare.phase = "Night";
        String b = bare.json(true);
        check(b.equals("{\"v\":1,\"d\":0,\"ph\":\"Night\",\"lv\":0,\"xp\":0,\"fx\":[]}"), b);
        System.out.println("SAMPLE " + b);
        // A hostile name cannot break the payload.
        JournalRules.Snapshot evil = new JournalRules.Snapshot();
        evil.effect("A\"B\\C\nDé" + "x", 5);
        evil.disaster = Integer.valueOf(2); evil.disasterKind = "T\"o\\r";
        String e = evil.json(true);
        check(e.indexOf('\n') < 0 && e.contains("\"ABCDx\"") && e.contains("\"dz\":[2,\"Tor\"]"), e);
        System.out.println("SAMPLE " + e);
        // At most MAX_EFFECTS names, however many effects there are.
        JournalRules.Snapshot many = new JournalRules.Snapshot();
        for (int i = 0; i < 40; i++) many.effect("E" + i, 0);
        check(many.effectNames.size() == JournalRules.MAX_EFFECTS, "bounded");
        check(many.json(true).length() < 1024, "small payload");
    }
}
