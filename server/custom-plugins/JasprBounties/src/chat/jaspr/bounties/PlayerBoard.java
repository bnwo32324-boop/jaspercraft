package chat.jaspr.bounties;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

/**
 * One player's bounty board: three dailies (easy, medium, hard), two weeklies, the daily streak and the realms they have
 * set foot in. Boards are rolled from the player's id and the date, so a lost file rolls the same bounties again.
 */
final class PlayerBoard {
    static final int DAILY = 3, WEEKLY = 2;

    final UUID id;
    String name;
    long day = Long.MIN_VALUE, week = Long.MIN_VALUE;
    final Bounty[] daily = new Bounty[DAILY];
    final Bounty[] weekly = new Bounty[WEEKLY];
    int rerolls;
    int streak;
    long streakDay = Long.MIN_VALUE;
    long bonusDay = Long.MIN_VALUE;
    boolean remind = true;
    final Set<Template.Realm> realms = EnumSet.of(Template.Realm.OVERWORLD);
    int lifetime;
    /** Rewards owed (rolled over while offline): "xp:N", "item:MATERIAL:N", "trinket". */
    final List<String> owed = new ArrayList<>();
    // not saved
    long nextReminder;
    long lateWarned = Long.MIN_VALUE;
    boolean dirty;

    PlayerBoard(UUID id, String name) { this.id = id; this.name = name; }

    List<Bounty> all() {
        List<Bounty> out = new ArrayList<>();
        for (Bounty b : daily) if (b != null) out.add(b);
        for (Bounty b : weekly) if (b != null) out.add(b);
        return out;
    }

    int count(Bounty.State state) {
        int n = 0;
        for (Bounty b : all()) if (b.state == state) n++;
        return n;
    }

    boolean dailiesClaimed() {
        for (Bounty b : daily) if (b == null || b.state != Bounty.State.CLAIMED) return false;
        return true;
    }

    // ---- rolling -----------------------------------------------------------------------------------------------------

    static final Template.Tier[] DAILY_TIERS = {Template.Tier.EASY, Template.Tier.MEDIUM, Template.Tier.HARD};

    private Random random(long period, int slot, int salt) {
        long seed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits()
            ^ period * 0xC2B2AE3D27D4EB4FL ^ (slot + 1) * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        return new Random(seed);
    }

    /** Posts a fresh daily in a slot, avoiding the goals already on the board. */
    Bounty rollDaily(int slot, long dayKey, int salt, Rewards rewards) {
        Template.Tier tier = DAILY_TIERS[slot];
        Set<String> taken = new HashSet<>();
        Set<Template.Goal> goals = EnumSet.noneOf(Template.Goal.class);
        for (int i = 0; i < DAILY; i++) if (i != slot && daily[i] != null && daily[i].template() != null) { taken.add(daily[i].id); goals.add(daily[i].template().goal); }
        return roll(tier, random(dayKey, slot, salt), taken, goals, rewards, salt > 0 && daily[slot] != null ? daily[slot].id : null);
    }

    Bounty rollWeekly(int slot, long weekKey, Rewards rewards) {
        Set<String> taken = new HashSet<>();
        Set<Template.Goal> goals = EnumSet.noneOf(Template.Goal.class);
        for (int i = 0; i < WEEKLY; i++) if (i != slot && weekly[i] != null && weekly[i].template() != null) { taken.add(weekly[i].id); goals.add(weekly[i].template().goal); }
        return roll(Template.Tier.WEEKLY, random(weekKey, 10 + slot, 0), taken, goals, rewards, null);
    }

    private Bounty roll(Template.Tier tier, Random r, Set<String> taken, Set<Template.Goal> goals, Rewards rewards, String avoid) {
        List<Template> pool = Template.offer(tier, realms);
        List<Template> fresh = new ArrayList<>();
        for (Template t : pool) if (!taken.contains(t.id) && !t.id.equals(avoid) && !goals.contains(t.goal)) fresh.add(t);
        if (fresh.isEmpty()) for (Template t : pool) if (!taken.contains(t.id) && !t.id.equals(avoid)) fresh.add(t);
        if (fresh.isEmpty()) fresh = pool;
        // Realm bounties are favoured a little once a player has been somewhere new: they are the reason to go back.
        List<Template> weighted = new ArrayList<>();
        for (Template t : fresh) { weighted.add(t); if (t.realm != Template.Realm.OVERWORLD) weighted.add(t); }
        Template t = weighted.get(r.nextInt(weighted.size()));
        int need = t.min + (t.max > t.min ? r.nextInt(t.max - t.min + 1) : 0);
        if (t.goal == Template.Goal.TRAVEL) need = Math.max(100, need / 100 * 100);
        Rewards.Prize prize = rewards.prize(tier, r);
        return new Bounty(t.id, need, 0, Bounty.State.OPEN, 0, false, prize.xp, prize.item, prize.amount, prize.trinket);
    }

    /** Material of an owed item reward, or null when it is not one. */
    static Material owedItem(String entry) {
        if (!entry.startsWith("item:")) return null;
        String[] p = entry.split(":");
        return p.length == 3 ? Material.getMaterial(p[1]) : null;
    }
}
