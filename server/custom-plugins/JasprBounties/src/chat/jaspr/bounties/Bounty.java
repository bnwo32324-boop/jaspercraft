package chat.jaspr.bounties;

import org.bukkit.Material;

/** One bounty on a player's board: its template, how much is needed, how much is done, and what it pays. */
final class Bounty {
    enum State { OPEN, DONE, CLAIMED }

    final String id;
    final int need;
    int have;
    State state;
    /** For travel bounties: the player's distance statistic (cm) when the bounty was posted. */
    long base;
    /** Whether the halfway notice has been sent. */
    boolean half;
    final int xp;
    final Material item;
    final int amount;
    /** Chance (0..1) of a trinket on claiming. */
    final double trinket;

    Bounty(String id, int need, int have, State state, long base, boolean half, int xp, Material item, int amount, double trinket) {
        this.id = id; this.need = need; this.have = have; this.state = state; this.base = base; this.half = half;
        this.xp = xp; this.item = item; this.amount = amount; this.trinket = trinket;
    }

    Template template() { return Template.get(id); }

    boolean open() { return state == State.OPEN; }

    /** Adds progress; true when this call completed the bounty. */
    boolean add(int n) {
        if (state != State.OPEN || n <= 0) return false;
        have = Math.min(need, have + n);
        if (have >= need) { state = State.DONE; return true; }
        return false;
    }

    /** Sets absolute progress (travel); true when this call completed the bounty. */
    boolean progress(int value) {
        if (state != State.OPEN) return false;
        int v = Math.max(0, Math.min(need, value));
        if (v <= have) return false;
        have = v;
        if (have >= need) { state = State.DONE; return true; }
        return false;
    }

    String title() {
        Template t = template();
        return t == null ? id : t.title(need);
    }
}
