package chat.jaspr.rpg;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Offline check of the stat sheet (tests/rpg-stats.test.cjs): the roster, the mastery ranks, the price curve, the
 * menu layout and every stat's description at every level. Prints RPG_OK when everything holds.
 */
public final class RpgCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        RpgConfig config = new RpgConfig(new YamlConfiguration());
        StatType[] all = StatType.values();
        check(all.length == 45, "45 stats: " + all.length);
        Set<String> keys = new HashSet<String>();
        for (StatType s : all) {
            check(keys.add(s.key()), "unique key " + s.key());
            check(s.cap == (s == StatType.TREASURE ? 5 : 15), "cap " + s.key() + " " + s.cap);
            check(config.capFor(s) == s.cap, "cap multiplier 1 keeps the cap");
            check(s.isLinear() == (s.base == 1.0d && s != StatType.TREASURE && s != StatType.HEALTH), "geometric or linear " + s.key());
        }
        for (StatType.Category c : StatType.Category.values()) {
            int n = c.stats().size();
            check(n > 0 && n <= StatsMenu.STAT_SLOTS.length, "tab size " + c + " " + n);
            Set<Integer> used = new HashSet<Integer>();
            for (StatType s : c.stats()) check(StatsMenu.slotOf(s) >= 0 && used.add(StatsMenu.slotOf(s)), "slot " + s.key());
            System.out.println("tab " + c.title + " " + n);
        }
        // ordinary ranks count fully, mastery ranks half, nothing past the cap
        check(StatType.effective(10, 15) == 10.0d && StatType.effective(15, 15) == 12.5d && StatType.effective(20, 15) == 12.5d, "effective levels");
        check(StatType.effective(3, 5) == 3.0d && StatType.effective(0, 15) == 0.0d, "effective low levels");
        check(Math.abs(StatType.SWORDSMANSHIP.multiplier(15) - Math.pow(1.0895d, 12.5d)) < 1e-9, "mastery multiplier");
        check(StatType.HEALTH.multiplier(15) == 1.0d, "health is not geometric");
        // the price curve: GokiStats up to level 10, then a flat 50 per mastery rank
        int[] steps = {6, 8, 11, 14, 19, 24, 29, 35, 41, 48, 50, 50, 50, 50, 50};
        for (int i = 0; i < steps.length; i++) check(StatCosts.cost(i, 1.0d) == steps[i], "cost of level " + (i + 1) + " = " + StatCosts.cost(i, 1.0d));
        check(StatCosts.totalSpent(10, 1.0d) == 235 && StatCosts.totalSpent(15, 1.0d) == 485, "totals " + StatCosts.totalSpent(15, 1.0d));
        // standing effects keep growing through the mastery ranks
        check(StatEffects.hasteAmplifier(1) == 0 && StatEffects.hasteAmplifier(9) == 4 && StatEffects.hasteAmplifier(10) == 4, "haste ordinary");
        check(StatEffects.hasteAmplifier(11) == 5 && StatEffects.hasteAmplifier(13) == 6 && StatEffects.hasteAmplifier(15) == 6, "haste mastery");
        check(StatEffects.jumpAmplifier(10) == 3 && StatEffects.jumpAmplifier(12) == 3 && StatEffects.jumpAmplifier(13) == 4, "jump");
        check(StatEffects.stealthBlind(10) == 12.0d && StatEffects.stealthBlind(15) == 14.0d, "stealth " + StatEffects.stealthBlind(15));
        check(StatEffects.secondWindCooldown(1) == 172_000L && StatEffects.secondWindCooldown(12.5) == 80_000L, "second wind " + StatEffects.secondWindCooldown(12.5));
        // chance stats stay under their caps at full mastery
        check(StatType.PRECISION.linear(15, 15) == 0.25d && StatType.EVASION.linear(15, 15) <= StatEffects.EVASION_CAP, "chance caps");
        // every description reads at every level
        for (StatType s : all) for (int l = 0; l <= s.cap; l++) {
            String text = StatsMenu.format(s, l, s.cap, config);
            check(text != null && !text.isEmpty() && !text.contains("NaN"), "format " + s.key() + " " + l);
            if (l == s.cap) System.out.println("stat " + s.key() + " " + s.category + " max: " + text);
        }
        System.out.println("RPG_OK stats=" + all.length + " total15=" + StatCosts.totalSpent(15, 1.0d));
    }
}
