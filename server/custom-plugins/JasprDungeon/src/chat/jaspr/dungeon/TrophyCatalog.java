package chat.jaspr.dungeon;

import java.util.*;

/**
 * Generation 7 (owner 2026-10-05: "add: rooms, bosses, custom items, custom mobs. Have them be totally original"): each Floor
 * Guardian leaves a trophy weapon to everyone who stood in its arena when it fell, and conquering the Abyssal Citadel earns the
 * victor's laurel, a relic for the pouch (LootCatalog.Bauble.VICTORS_LAUREL). Pure: names, stats, abilities and their bounds;
 * Trophies makes and drives them. The weapons are strong but bounded: unbreakable like the armory's, their power answers only
 * monsters (never a player), they dig nothing and take no enchantment, and every ability has a cooldown, a range and a cap.
 */
public final class TrophyCatalog {
    private TrophyCatalog() {}
    public enum Kind { WEAPON, RELIC }
    /** Upper bounds every trophy keeps (a diamond sword hits for 7, with Sharpness V for 10). */
    public static final double MAX_DAMAGE = 12, MAX_POWER = 8, MAX_RADIUS = 6;
    public static final int MAX_TARGETS = 6;
    public static final long MIN_COOLDOWN = 3000;
    public enum Trophy {
        /** Floor I. Its band is on the stone sword (scripts/trinket-art/catalog.cjs DUNGEON7_BANDS). */
        MERCYS_LAST_KEY(74, 1, Kind.WEAPON, "Mercy's Last Key", "The Gaoler of Mercy", 8, 1.6, 6000, 2, 0, 1,
            "The key to every cell of the House of Mercy,", "never once turned to open one.",
            "Each blow locks a monster in place (Slowness III", "for 1.5s; a boss, Slowness I), once per monster",
            "every 6 seconds. Kills restore 1 heart (3s)."),
        /** Floor II. */
        DEEPBREAKER(75, 2, Kind.WEAPON, "Deepbreaker", "The Deep Tyrant", 10, 1.0, 5000, 4, 3.5, 5,
            "The Deep Tyrant's maul, that broke the Underworks", "open and every digger in them.",
            "Every third blow within 5 seconds sends a tremor:", "up to 5 other monsters within 3.5 blocks take",
            "4 damage and are thrown back."),
        /** Floor III. */
        ABYSSAL_SCEPTRE(76, 3, Kind.WEAPON, "Sceptre of the Abyss", "The Abyssal Sovereign", 11, 1.2, 20000, 8, 6, 6,
            "The Sovereign ruled with it, and the Abyss obeyed", "whoever held it. Now it obeys you.",
            "Right-click: the Abyssal Tide strikes up to 6", "monsters in sight within 6 blocks for 8 damage",
            "and withers them for 3s; you gain Resistance I", "for 3s. 20-second cooldown; needs a monster in sight."),
        /** The victor's reward: a relic whose band is Relics.Type.VICTORS_LAUREL's. */
        VICTORS_LAUREL(0, 0, Kind.RELIC, "Laurel of the Unbowed", "the Abyssal Citadel", 0, 0, 180000, 0, 0, 0,
            "Awarded for conquering all three floors.");
        public final int band, floor;
        public final Kind kind;
        public final String title, from;
        /** A weapon's attack damage (hearts x 2) and blows a second; an ability's cooldown, damage (or healing), reach and targets. */
        public final double damage, speed, power, radius;
        public final long cooldownMillis;
        public final int targets;
        public final List<String> lines;
        Trophy(int band, int floor, Kind kind, String title, String from, double damage, double speed, long cooldown, double power, double radius, int targets, String... lines) {
            this.band = band; this.floor = floor; this.kind = kind; this.title = title; this.from = from; this.damage = damage; this.speed = speed;
            cooldownMillis = cooldown; this.power = power; this.radius = radius; this.targets = targets; this.lines = Collections.unmodifiableList(Arrays.asList(lines));
        }
        public boolean weapon() { return kind == Kind.WEAPON; }
        /** The relic a RELIC trophy is (its bauble), or null for a weapon. */
        public LootCatalog.Bauble relic() { return kind == Kind.RELIC ? LootCatalog.Bauble.valueOf(name()) : null; }
    }
    /** The trophy of a floor's guardian (1, 2 or 3: the Throne's sovereign is Floor III's), or null. */
    public static Trophy guardian(int floor) {
        for (Trophy t : Trophy.values()) if (t.weapon() && t.floor == floor) return t;
        return null;
    }
    public static Trophy victory() { return Trophy.VICTORS_LAUREL; }
    public static List<String> audit() {
        List<String> errors = new ArrayList<>();
        Set<Integer> bands = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (int floor = 1; floor <= 3; floor++) if (guardian(floor) == null) errors.add("No trophy for floor " + floor);
        if (victory() == null || victory().kind != Kind.RELIC || !victory().relic().trophy() || victory().relic().rank != 5) errors.add("Victory must be a rank 5 trophy relic");
        for (Trophy t : Trophy.values()) {
            if (!titles.add(t.title) || t.lines.isEmpty()) errors.add("Title/lines: " + t);
            if (t.weapon()) {
                if (t.band < 74 || t.band > 76 || !bands.add(t.band)) errors.add("Band: " + t);
                if (t.damage <= 7 || t.damage > MAX_DAMAGE || t.speed < .8 || t.speed > 2) errors.add("Weapon bounds: " + t);
                if (t.power <= 0 || t.power > MAX_POWER || t.radius < 0 || t.radius > MAX_RADIUS || t.targets < 1 || t.targets > MAX_TARGETS || t.cooldownMillis < MIN_COOLDOWN) errors.add("Ability bounds: " + t);
            } else if (t.band != 0 || t.relic() == null) errors.add("Relic trophy: " + t);
            for (String line : t.lines) if (line.toLowerCase(Locale.ROOT).matches(".*(night vision|glow|flight).*")) errors.add("Darkness/flight contract: " + t);
        }
        // Deeper guardians leave stronger weapons.
        for (int floor = 2; floor <= 3; floor++) if (guardian(floor).damage <= guardian(floor - 1).damage) errors.add("Trophy damage must grow with the floor");
        return Collections.unmodifiableList(errors);
    }
}
