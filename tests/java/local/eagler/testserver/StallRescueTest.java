package local.eagler.testserver;

import java.util.UUID;

/** Stall rescue bookkeeping: what counts as a stall and when a stall repeats at the same spot. */
public final class StallRescueTest {
    public static void main(String[] args) {
        check(!StallRescue.stalled(false, 60_000L), "an answered keepalive is never a stall");
        check(!StallRescue.stalled(true, 900L), "a keepalive still in flight for under a second is healthy");
        check(StallRescue.stalled(true, 5_000L), "five seconds without an answer is a stall");
        check(StallRescue.stalled(true, 30_000L), "a keepalive timeout is a stall");

        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        StallRescue.Tracker t = new StallRescue.Tracker();
        long now = 1_000_000L;
        check(!t.record(a, new StallRescue.Stall("world", -16, 62, 160, now)), "one stall alone moves nobody");
        check(!t.record(b, new StallRescue.Stall("world", -16, 62, 160, now + 1000)), "another player's stall does not count");
        check(!t.record(a, new StallRescue.Stall("world", 400, 70, 400, now + 60_000)), "a stall far away is a different spot");
        check(!t.record(a, new StallRescue.Stall("world_nether", -16, 62, 160, now + 70_000)), "another world is a different spot");
        check(t.record(a, new StallRescue.Stall("world", -2, 59, 170, now + 120_000)), "a second stall within 24 blocks and 15 minutes rescues");
        check(t.size(a) == 0, "after a rescue the count starts afresh");

        StallRescue.Tracker late = new StallRescue.Tracker();
        check(!late.record(a, new StallRescue.Stall("world", 0, 64, 0, now)), "first");
        check(!late.record(a, new StallRescue.Stall("world", 0, 64, 0, now + StallRescue.WINDOW_MILLIS + 1)), "a stall older than 15 minutes has expired");
        System.out.println("STALL_RESCUE_OK");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
