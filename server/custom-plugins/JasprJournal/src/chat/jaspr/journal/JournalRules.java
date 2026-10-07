package chat.jaspr.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The arithmetic of the Field Journal, free of the server so it is tested on its own: the phase of the day, the nights until the
 * next Blood Moon (by asking the siege's own rule, never copying it), how the invasion and disaster states are put to the player,
 * the names of item effects, and the payload that goes to the browser client.
 */
final class JournalRules {
    static final long DAY = 24000L;
    static final long NIGHT_START = 13000L, NIGHT_END = 23000L;
    /** A candidate time inside any night, used to ask the siege rule about that night. */
    private static final long NIGHT_PROBE = 18000L;
    /** How far ahead the Blood Moon is looked for before it counts as "none" (a mis-set interval must not loop forever). */
    static final int LOOKAHEAD_DAYS = 400;
    /** Longest list of item effects sent. */
    static final int MAX_EFFECTS = 12;
    /** Effects kept alive by an item are refreshed every second as a 70 tick effect; anything shorter than this is "while worn". */
    static final int MAINTAINED_TICKS = 100;

    private JournalRules() {}

    /** The siege rule: is this a Blood Moon at this time? (JasprApocalypse SiegeRules.bloodMoon with the configured interval.) */
    interface BloodMoonRule { boolean at(long fullTime); }

    /** "Morning", "Midday", "Dusk", "Night" or "Dawn" for a time of day (tick 0 is sunrise, 6000 noon, 12000 sunset). */
    static String phase(long fullTime) {
        long t = Math.floorMod(fullTime, DAY);
        if (t < 6000L) return "Morning";
        if (t < 12000L) return "Midday";
        if (t < NIGHT_START) return "Dusk";
        if (t < NIGHT_END) return "Night";
        return "Dawn";
    }

    /** The day number as the invasion status command prints it (0 on the first day). */
    static long day(long fullTime) { return Math.floorDiv(fullTime, DAY); }

    /**
     * Nights until the next Blood Moon, counted like a player would: 0 is the night that is coming ("tonight"), 1 the one after
     * ("tomorrow night"). A Blood Moon that is under way returns {@link #NOW}; -1 means none within {@link #LOOKAHEAD_DAYS}.
     */
    static final int NOW = -2;
    static int bloodMoonNights(long fullTime, BloodMoonRule rule) {
        if (rule == null) return -1;
        long d = day(fullTime), t = Math.floorMod(fullTime, DAY);
        if (rule.at(fullTime)) return NOW;
        boolean midNight = t >= NIGHT_START && t < NIGHT_END;
        long first = t < NIGHT_START ? d : d + 1;
        int bias = midNight ? 1 : 0;
        for (int k = 0; k <= LOOKAHEAD_DAYS; k++) {
            long target = first + k;
            if (rule.at(target * DAY + NIGHT_PROBE)) return k + bias;
        }
        return -1;
    }

    /** Invasion states put to the player. */
    static final int INV_NONE = 0, INV_WAITING = 1, INV_DUE = 2, INV_UNDERWAY = 3;
    /** {state, dueDay}: dueDay is the first day an invasion can come (only meaningful for {@link #INV_WAITING}). */
    static int[] invasion(boolean underway, long sleptAt, long fullTime, int daysAfterSleep) {
        if (underway) return new int[] {INV_UNDERWAY, 0};
        if (sleptAt < 0L) return new int[] {INV_NONE, 0};
        long due = sleptAt + (long) daysAfterSleep * DAY;
        if (fullTime >= due) return new int[] {INV_DUE, 0};
        return new int[] {INV_WAITING, (int) Math.min(Integer.MAX_VALUE, due / DAY)};
    }

    /** Disaster states put to the player: 0 quiet, 1 brewing, 2 under way (with the kind's name). */
    static int disasterState(String source) {
        if (source == null) return -1;
        if (source.startsWith("active:")) return 2;
        return "brewing".equals(source) ? 1 : 0;
    }
    static String disasterKind(String source) {
        return source != null && source.startsWith("active:") ? clean(source.substring(7), 24) : "";
    }

    // ------------------------------------------------------------------------------------------------------ item effects
    /** The effect names players know; anything else is Title Cased from its constant. */
    private static final String[][] NAMES = {
        {"SPEED", "Speed"}, {"SLOW", "Slowness"}, {"FAST_DIGGING", "Haste"}, {"SLOW_DIGGING", "Mining Fatigue"},
        {"INCREASE_DAMAGE", "Strength"}, {"HEAL", "Instant Health"}, {"HARM", "Instant Damage"}, {"JUMP", "Jump Boost"},
        {"CONFUSION", "Nausea"}, {"REGENERATION", "Regeneration"}, {"DAMAGE_RESISTANCE", "Resistance"},
        {"FIRE_RESISTANCE", "Fire Resistance"}, {"WATER_BREATHING", "Water Breathing"}, {"INVISIBILITY", "Invisibility"},
        {"BLINDNESS", "Blindness"}, {"NIGHT_VISION", "Night Vision"}, {"HUNGER", "Hunger"}, {"WEAKNESS", "Weakness"},
        {"POISON", "Poison"}, {"WITHER", "Wither"}, {"HEALTH_BOOST", "Health Boost"}, {"ABSORPTION", "Absorption"},
        {"SATURATION", "Saturation"}, {"GLOWING", "Glowing"}, {"LEVITATION", "Levitation"}, {"LUCK", "Luck"}, {"UNLUCK", "Bad Luck"}};
    private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** "Haste II" for the effect constant FAST_DIGGING at amplifier 1. */
    static String effectName(String constant, int amplifier) {
        String base = null;
        for (String[] pair : NAMES) if (pair[0].equals(constant)) { base = pair[1]; break; }
        if (base == null) base = titleCase(constant == null ? "?" : constant);
        int a = Math.max(0, amplifier);
        String level = a < ROMAN.length ? ROMAN[a] : String.valueOf(a + 1);
        return level.isEmpty() ? base : base + " " + level;
    }
    static String titleCase(String constant) {
        StringBuilder out = new StringBuilder();
        for (String part : constant.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? "?" : out.toString();
    }
    /** Seconds shown beside an effect: 0 while an item keeps it up, else the time left (capped so the text stays short). */
    static int shownSeconds(int ticksLeft) {
        return ticksLeft <= MAINTAINED_TICKS ? 0 : Math.min(3599, ticksLeft / 20);
    }

    // ----------------------------------------------------------------------------------------------------- the payload
    /** What one player's panel shows. Absent parts are null and are left out of the payload (the client hides their rows). */
    static final class Snapshot {
        long day;
        String phase = "Morning";
        Integer moon;             // nights until the Blood Moon, NOW, or -1; null when the siege is not running
        int[] invasion;           // {state, dueDay}
        Integer disaster;         // 0 quiet, 1 brewing, 2 under way
        String disasterKind = "";
        int level;
        int xp;                   // 0..40: how full the experience bar is
        int[] rpg;                // {ranks, raised, total, affordable, cheapest}
        final List<String> effectNames = new ArrayList<String>();
        final List<Integer> effectSeconds = new ArrayList<Integer>();

        void effect(String name, int seconds) {
            if (effectNames.size() >= MAX_EFFECTS) return;
            effectNames.add(clean(name, 28));
            effectSeconds.add(Integer.valueOf(Math.max(0, seconds)));
        }

        /** The payload. {@code withTimes} false leaves the countdowns out, so two payloads that differ only in them compare equal. */
        String json(boolean withTimes) {
            StringBuilder b = new StringBuilder(256);
            b.append("{\"v\":1,\"d\":").append(day).append(",\"ph\":\"").append(phase).append('"');
            if (moon != null) b.append(",\"bm\":").append(moon.intValue());
            if (invasion != null) b.append(",\"iv\":[").append(invasion[0]).append(',').append(invasion[1]).append(']');
            if (disaster != null) {
                b.append(",\"dz\":[").append(disaster.intValue()).append(",\"").append(escape(disasterKind)).append("\"]");
            }
            b.append(",\"lv\":").append(level).append(",\"xp\":").append(xp);
            if (rpg != null) {
                b.append(",\"rp\":[");
                for (int i = 0; i < rpg.length; i++) b.append(i == 0 ? "" : ",").append(rpg[i]);
                b.append(']');
            }
            b.append(",\"fx\":[");
            for (int i = 0; i < effectNames.size(); i++) {
                if (i > 0) b.append(',');
                b.append("[\"").append(escape(effectNames.get(i))).append("\",").append(withTimes ? effectSeconds.get(i).intValue() : 0).append(']');
            }
            return b.append("]}").toString();
        }
    }

    /** Bounded plain text for the payload: control characters and quotes dropped, at most {@code max} characters. */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length() && out.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127 && c != '"' && c != '\\') out.append(c);
        }
        return out.toString();
    }
    static String escape(String s) { return clean(s, 64); }
}
