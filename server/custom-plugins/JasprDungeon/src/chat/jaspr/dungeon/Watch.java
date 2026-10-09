package chat.jaspr.dungeon;

/**
 * Generation 7 polish (owner, 2026-10-08: "the mobs should be more spread out. They're all clustered in the center of giant rooms
 * when they could be more spread out in the rooms"). Layout.Stations already spreads a room's mobs over its whole floor; this keeps
 * them there. A mob holds its station (wandering a little, walked back when it strays) until a player comes near enough, is in
 * sight within range, strikes it, or a neighbour drawn into the fight calls it. From then on it hunts as before, so a great hall is
 * a series of fights across the room instead of one crowd that runs at the door. Boss rooms, the guardians' arenas and woken
 * treasure or shrine guardians fight at once, as before. Deeper floors watch farther. Pure rules; Encounters applies them.
 */
public final class Watch {
    private Watch() {}

    /** Always noticed this close, seen or not (blocks). */
    public static final double NEAR = 10;
    /** A mob drawn into a fight calls every room-mate this close (blocks). */
    public static final double CALL = 12;
    /** An idle mob further than this from its station walks back to it (blocks). */
    public static final double POST = 5;

    /** How far a mob sees a player and comes for them: 18 blocks on Floor I, 22 on Floor II, 26 on Floor III. */
    public static double sight(int floor) { return 18 + 4 * (Math.max(1, Math.min(3, floor)) - 1); }

    /** Everyone in the room fights at once: a boss and its escort, a floor guardian's or the Throne's arena, woken guardians. */
    public static boolean allAtOnce(Layout.Room r, boolean woken) {
        return r == null || r.bossRoom() || (r.dormant() && woken);
    }

    /** Whether an idle mob notices a player at this squared distance (seen: in its line of sight). */
    public static boolean notices(int floor, double distanceSquared, boolean seen) {
        if (distanceSquared <= NEAR * NEAR) return true;
        double s = sight(floor);
        return seen && distanceSquared <= s * s;
    }

    /** Whether the line of sight is worth asking for (it costs a ray): only inside the sight range and beyond the near ring. */
    public static boolean looks(int floor, double distanceSquared) {
        double s = sight(floor);
        return distanceSquared > NEAR * NEAR && distanceSquared <= s * s;
    }

    /** Whether a room-mate this far from a mob drawn into the fight is called into it too. */
    public static boolean called(double distanceSquared) { return distanceSquared <= CALL * CALL; }

    /** Whether an idle mob this far from its station should walk back. */
    public static boolean astray(double distanceSquared) { return distanceSquared > POST * POST; }
}
