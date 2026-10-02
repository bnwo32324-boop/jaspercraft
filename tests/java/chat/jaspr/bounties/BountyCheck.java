package chat.jaspr.bounties;

import java.io.File;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Offline check of the bounty board (tests/bounties.test.cjs): the bounty list, rolling boards (deterministic, one of
 * each daily tier, distinct, realm bounties only after a visit), progress, reward text and the player file round trip.
 * Prints BOUNTIES_OK when everything holds.
 */
public final class BountyCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        // the list
        int[] perTier = new int[Template.Tier.values().length];
        Set<String> titles = new HashSet<>();
        for (Template t : Template.ALL.values()) {
            check(t.min >= 1 && t.max >= t.min, "range " + t.id);
            check(t.id.startsWith(t.tier == Template.Tier.EASY ? "e_" : t.tier == Template.Tier.MEDIUM ? "m_" : t.tier == Template.Tier.HARD ? "h_" : "w_"), "id prefix " + t.id);
            check(t.title.contains("%d") || (t.min == 1 && t.max == 1), "amount in the title " + t.id);
            check(titles.add(t.id), "unique " + t.id);
            switch (t.goal) {
                case KILL: case BOSS: check(t.mob != null, "mob test " + t.id); break;
                case MINE: case CHOP: case HARVEST: check(t.block != null, "block test " + t.id); break;
                case SMELT: check(t.items != null && !t.items.isEmpty(), "items " + t.id); break;
                default: break;
            }
            check(t.icon != null && t.hint != null && !t.hint.isEmpty(), "icon and hint " + t.id);
            perTier[t.tier.ordinal()]++;
        }
        for (Template.Tier tier : Template.Tier.values()) {
            check(Template.offer(tier, EnumSet.of(Template.Realm.OVERWORLD)).size() >= 4, "overworld bounties of every tier: " + tier);
            System.out.println("tier " + tier + " " + perTier[tier.ordinal()]);
        }
        for (Template.Realm r : Template.Realm.values()) {
            if (r == Template.Realm.OVERWORLD) continue;
            int n = 0;
            for (Template t : Template.ALL.values()) if (t.realm == r) n++;
            check(n >= 3, "realm bounties for " + r + ": " + n);
            check(Template.Realm.ofWorld(r.world) == r, "world name " + r.world);
        }

        // rolling: deterministic per player and day, one of each daily tier, distinct
        int[] xp = {150, 350, 700, 2000};
        double[] trinket = {0, 0, 0.1, 0.5};
        Rewards rewards = new Rewards(null, xp, trinket, 100);
        UUID id = UUID.fromString("38c37b8b-310b-3191-8547-98d7a6166443");
        long day = 20728, week = 20727;
        PlayerBoard a = new PlayerBoard(id, "Tester"), b = new PlayerBoard(id, "Tester");
        for (int i = 0; i < PlayerBoard.DAILY; i++) { a.daily[i] = a.rollDaily(i, day, 0, rewards); b.daily[i] = b.rollDaily(i, day, 0, rewards); }
        for (int i = 0; i < PlayerBoard.WEEKLY; i++) { a.weekly[i] = a.rollWeekly(i, week, rewards); b.weekly[i] = b.rollWeekly(i, week, rewards); }
        for (int i = 0; i < PlayerBoard.DAILY; i++) {
            check(a.daily[i].id.equals(b.daily[i].id) && a.daily[i].need == b.daily[i].need, "deterministic daily " + i);
            check(a.daily[i].template().tier == PlayerBoard.DAILY_TIERS[i], "daily tier " + i);
            check(a.daily[i].xp == xp[i], "daily xp " + i);
        }
        check(!a.daily[0].id.equals(a.daily[1].id) && !a.daily[1].id.equals(a.daily[2].id), "distinct dailies");
        check(!a.weekly[0].id.equals(a.weekly[1].id), "distinct weeklies");
        check(a.weekly[0].trinket == 0.5 && a.weekly[0].xp == 2000, "weekly prize");
        // over many players and days: realm bounties only for those who went there, and every template turns up
        Set<String> seen = new HashSet<>();
        Random r = new Random(7);
        for (int n = 0; n < 4000; n++) {
            PlayerBoard x = new PlayerBoard(new UUID(r.nextLong(), r.nextLong()), "p" + n);
            boolean traveller = n % 2 == 0;
            if (traveller) x.realms.addAll(EnumSet.allOf(Template.Realm.class));
            for (int i = 0; i < PlayerBoard.DAILY; i++) x.daily[i] = x.rollDaily(i, day + n % 30, 0, rewards);
            for (int i = 0; i < PlayerBoard.WEEKLY; i++) x.weekly[i] = x.rollWeekly(i, week + 7 * (n % 10), rewards);
            for (Bounty y : x.all()) {
                seen.add(y.id);
                if (!traveller) check(y.template().realm == Template.Realm.OVERWORLD, "no realm bounty before a visit: " + y.id);
                check(y.need >= y.template().min && y.need <= Math.max(y.template().max, 100), "amount in range " + y.id + " " + y.need);
            }
            // a swap never gives back the same bounty
            Bounty old = x.daily[1];
            Bounty swapped = x.rollDaily(1, day + n % 30, 1, rewards);
            check(!swapped.id.equals(old.id), "swap differs " + old.id);
        }
        check(seen.size() == Template.ALL.size(), "every bounty appears: " + seen.size() + "/" + Template.ALL.size());

        // progress
        Bounty k = new Bounty("e_zombie", 3, 0, Bounty.State.OPEN, 0, false, 150, org.bukkit.Material.IRON_INGOT, 6, 0);
        check(!k.add(2) && k.have == 2 && k.open(), "partial progress");
        check(k.add(5) && k.have == 3 && k.state == Bounty.State.DONE, "completion caps at need");
        check(!k.add(1), "no progress after completion");
        Bounty t = new Bounty("e_travel", 1000, 0, Bounty.State.OPEN, 0, false, 150, null, 0, 0);
        check(!t.progress(400) && t.have == 400 && !t.progress(300) && t.have == 400 && t.progress(1200) && t.have == 1000, "travel progress");
        check(Rewards.describe(k).equals("150 XP + 6 Iron ingot"), "reward text: " + Rewards.describe(k));
        check(Rewards.describe(a.weekly[0]).endsWith("50% trinket chance"), "weekly reward text: " + Rewards.describe(a.weekly[0]));
        check(BoardMenu.bar(k).replaceAll("§.", "").equals("||||||||||"), "progress bar");

        // the player file round trip
        File dir = Files.createTempDirectory("bounties").toFile();
        Store store = new Store(dir);
        a.day = day; a.week = week; a.streak = 3; a.rerolls = 1; a.realms.add(Template.Realm.NETHER); a.owed.add("xp:150");
        a.daily[0].add(1);
        a.dirty = true;
        store.save(a);
        PlayerBoard back = store.read(id, null);
        check(back.name.equals("Tester") && back.day == day && back.week == week && back.streak == 3 && back.rerolls == 1, "board fields");
        check(back.realms.contains(Template.Realm.NETHER) && back.owed.contains("xp:150"), "realms and owed rewards");
        for (int i = 0; i < PlayerBoard.DAILY; i++)
            check(back.daily[i].id.equals(a.daily[i].id) && back.daily[i].have == a.daily[i].have && back.daily[i].item == a.daily[i].item, "daily round trip " + i);
        store.countDone("Tester", week);
        store.saveBoard();
        Store again = new Store(dir);
        again.loadBoard();
        check(again.weekDone.get("Tester") == 1 && again.weekDoneKey == week, "leaderboard round trip");
        System.out.println("BOUNTIES_OK templates=" + Template.ALL.size() + " seen=" + seen.size());
    }
}
