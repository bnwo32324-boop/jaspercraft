package chat.jaspr.dungeon;

/**
 * Generation 7: the floors' guardians. The Descent of Floor I and of Floor II is held by its floor's guardian, the Throne of
 * Floor III by its sovereign: "some bosses to be legitimately incredibly powerful" (owner 2026-10-05). Pure.
 */
public final class Guardians {
    private Guardians() {}
    public static final String[] NAMES = {"The Gaoler of Mercy", "The Deep Tyrant", "The Abyssal Sovereign"};
    public static final String[] EPITHETS = {
        "who keeps the stair to the Underworks",
        "who keeps the stair to the Abyssal Citadel",
        "upon the Throne of the Abyss"
    };
    /** The guardian of a finale room (the Descent or the Throne), by its floor. */
    public static String name(Layout.Room r) { return NAMES[Math.max(1, Math.min(3, r.floor)) - 1]; }
    public static String epithet(Layout.Room r) { return EPITHETS[Math.max(1, Math.min(3, r.floor)) - 1]; }
}
