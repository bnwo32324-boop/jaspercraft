package chat.jaspr.dungeon;

/**
 * Generation 7 (owner 2026-10-05): "physical dangers as well, such as lava pools, falling stalactites, and other things of
 * that nature." The pure half of Perils: every timing, cap, radius and damage, and the one rule every target obeys (never a
 * cell of the arrival circle, a lane core, the reliquary's approach, or outside the room's interior). No Bukkit, no clocks and
 * no random state, so PerilsAuditTest proves all of it without a server.
 */
final class PerilRules {
    private PerilRules() {}

    /** Block values as DungeonGenerator draws them (packed legacy id | data << 12). */
    interface Blocks { int block(Layout.Room r, int x, int y, int z); }
    /** Every restoration reads the generator itself, so a mended floor and a regrown stalactite are exactly what was drawn. */
    static final Blocks GENERATOR = DungeonGenerator::block;

    /** The walking floor: its block is y 64, and mobs and players stand at 65. */
    static final int FLOOR = Layout.FLOOR;
    /** Phases the audit reads from PerilStage.note. */
    static final String WARN = "warn", STRIKE = "strike", RESTORE = "restore";

    // ---------------------------------------------------------------- one rule for every warning
    /** Every peril shows its warning at least this many ticks before it can hurt anyone: a danger must be dodgeable. */
    static final int MIN_TELEGRAPH = 15;

    // ---------------------------------------------------------------- falling stalactites
    /** Someone this close (cells' centres, blocks) under a marked point loosens it; only the column directly beneath is hit. */
    static final double STALACTITE_RADIUS = 2.0, STALACTITE_HIT = .8;
    /** One second of falling dust and a crack before it drops; at most this many blocks fall from one point (the tip and what hangs above it); at most MAX_COLUMN are read. */
    static final int STALACTITE_TELEGRAPH = 20, FALL_SEGMENT = 3, MAX_COLUMN = 10;
    /** At most this many falls at once in a room, started at least this many ticks apart; a lost block is given up on after its fall plus the slack. */
    static final int MAX_FALLS = 3, FALL_SPACING = 6, FALL_SLACK = 30;
    /** A fallen stalactite regrows after 30 to 50 seconds, and may loosen again only a few seconds later. */
    static final int REGROW_MIN = 600, REGROW_SPREAD = 400, STALACTITE_COOLDOWN = 100;

    // ---------------------------------------------------------------- collapsing floors
    /** Cracked floor gives way this long after a player stands on it, stays open for 3 to 5 seconds (HOLE_MIN .. HOLE_MIN + HOLE_SPREAD - 1), then mends. */
    static final int CRUMBLE_WARN = 16, HOLE_MIN = 60, HOLE_SPREAD = 41, CRUMBLE_COOLDOWN = 40, MAX_CRUMBLES = 48;
    /**
     * A mend that finds someone in the hole tries again this often. A player still there this long after the mend was due is
     * lifted out onto the floor as it returns: Floors II and III dig their pits two blocks deep with magma at the bottom,
     * which nobody can climb out of and which burns, so the time in the pit is the designed three to five seconds and a
     * moment, never longer. Another kind of body (a mob that fell in) is given this long before the floor returns around it.
     */
    static final int MEND_RETRY = 5, PATIENCE = 10, MOB_PATIENCE = 60;
    /** Half a player's width: the floor cells a player's feet overlap. */
    static final double CRUMBLE_REACH = .3;

    // ---------------------------------------------------------------- geysers
    /**
     * A vent wakes when someone is this near and sleeps again when nobody is within the range; it warns, erupts, and rests.
     * An eruption reaches a player whose feet overlap the vent's cell (half a cell plus half a player: .8), so someone on the
     * edge of a lane beside a vent is only hit when they really stand on it.
     */
    static final double GEYSER_ARM = 6.0, GEYSER_RANGE = 10.0, GEYSER_HIT = .8, GEYSER_LIFT = .75;
    static final int GEYSER_TELEGRAPH = 22, GEYSER_BURST = 10, GEYSER_REST_MIN = 60, GEYSER_REST_SPREAD = 80, MAX_VENTS = 12;

    // ---------------------------------------------------------------- lava falls
    /** A molten fall spits and splashes in pulses: it warns, then burns whoever stands within the radius, then rests. */
    static final double SPOUT_ARM = 7.0, SPOUT_RANGE = 11.0, SPOUT_RADIUS = 1.5;
    static final int SPOUT_TELEGRAPH = 22, SPOUT_BURST = 6, SPOUT_REST_MIN = 50, SPOUT_REST_SPREAD = 70, MAX_SPOUTS = 12;

    // ---------------------------------------------------------------- bookkeeping
    /** A room's marks are read this many cells a tick (a 128 x 128 room is ready in about 1.6 s); vents and falls look for newcomers this often. */
    static final int BUILD_BUDGET = 512, ARM_EVERY = 5;
    /** Ambient lava pops, drips and wisps: every few ticks, a few tries around each player. */
    static final int AMBIENT_EVERY = 8, AMBIENT_RADIUS = 9, AMBIENT_TRIES = 6;

    // ---------------------------------------------------------------- where a peril may ever act
    /** The arrival refuge room: PerilMarks never marks it, and nothing here ever acts in it. */
    static boolean origin(Layout.Room r) { return r != null && r.kind == Layout.Kind.REFUGE && r.x == 0 && r.z == 0; }
    /**
     * The cells a peril may ever target: the room's interior, never the origin refuge or its arrival circle, never a lane core
     * (stations, doorways and the walk between them) or the reliquary's approach. The audit restates this independently.
     */
    static boolean allowed(Layout.Room r, int x, int z) {
        return r != null && !origin(r) && r.inner(x + .5, z + .5) && !PerilMarks.core(r, x, z) && !HazardCatalog.safe(r, x + .5, z + .5);
    }
    static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    /** One independent, repeatable stream per cell and purpose. */
    static long hash(Layout.Room r, int x, int z, long salt) { return Layout.mix(r.hash ^ ((long) x * 0x9e3779b97f4a7c15L) ^ ((long) z * 0x632be59bd9b4e019L) ^ salt); }
    static int spread(Layout.Room r, int x, int z, long salt, int n) { return (int) Math.floorMod(hash(r, x, z, salt), (long) n); }

    /**
     * What hangs over (x, z), tip first: {y, packed} for the lowest solid block above the walkable floor's head height and the
     * free-hanging blocks stacked above it (each has air beside it, so the ceiling's own rock is never taken), at most
     * MAX_COLUMN. It is read upward from the floor, so it is right whatever height a floor's ceiling stands at: Floor I's roof,
     * Floor III's tall halls, Floor II's domes above the roof layer. Empty when nothing hangs there, or when the first solid
     * block is a standing one (at head height or below: not a stalactite) or the roof itself.
     */
    static int[][] column(Blocks b, Layout.Room r, int x, int z) {
        int top = DungeonGenerator.shellTop(r.floor) - 1, y = FLOOR + 2;
        while (y <= top && b.block(r, x, y, z) == 0) y++;
        if (y == FLOOR + 2 || y > top) return new int[0][];
        int[][] tmp = new int[MAX_COLUMN][];int n = 0;
        while (n < MAX_COLUMN && y <= top && b.block(r, x, y, z) != 0 && hangs(b, r, x, y, z)) { tmp[n++] = new int[]{y, b.block(r, x, y, z)};y++; }
        return java.util.Arrays.copyOf(tmp, n);
    }
    /** A block hangs freely when at least one of its four sides is air. */
    static boolean hangs(Blocks b, Layout.Room r, int x, int y, int z) {
        return b.block(r, x + 1, y, z) == 0 || b.block(r, x - 1, y, z) == 0 || b.block(r, x, y, z + 1) == 0 || b.block(r, x, y, z - 1) == 0;
    }
    /** Ticks a block falls (as Minecraft's falling blocks do: gravity .04, drag .98) from its cell until its feet are 'height' lower. */
    static int fallTicks(double height) {
        double v = 0, y = 0;int n = 0;
        while (y > -height && n < 400) { v -= .04; y += v; v *= .98; n++; }
        return n;
    }

    // ---------------------------------------------------------------- damage
    /**
     * Before the floor's scaling: a stalactite 4 + threat (5 to 9 on Floor I), a scalding vent 2 + 0.6 x threat, a lava
     * splash 1 + 0.5 x threat. Floors.damage (x1, x1.4, x1.9) and a rift's danger multiplier then apply.
     */
    static double damage(PerilMarks.Kind k, int tier, int floor, double danger) {
        int t = EncounterCatalog.threat(tier);double base;
        switch (k) {
            case STALACTITE: base = 4 + t; break;
            case GEYSER: base = 2 + .6 * t; break;
            case LAVA_FALL: base = 1 + .5 * t; break;
            default: return 0;
        }
        return base * Floors.damage(floor) * Math.max(1, danger);
    }
    /** Floor III's vents are flame vents: they set fire where Floors I and II's steam throws. */
    static boolean flame(Layout.Room r) { return r.floor >= 3; }
    /** Ticks a player burns: a lava fall's splash (longer on deeper floors) and a flame vent's blast. */
    static int burn(PerilMarks.Kind k, int floor) {
        if (k == PerilMarks.Kind.LAVA_FALL) return floor >= 3 ? 120 : floor == 2 ? 80 : 60;
        return k == PerilMarks.Kind.GEYSER && floor >= 3 ? 80 : 0;
    }

    // ---------------------------------------------------------------- log lines
    /** DUNGEON_PERIL_FAILED: room key, kind and a short reason, printable ASCII only. */
    static String failure(String room, PerilMarks.Kind kind, String reason) {
        return "DUNGEON_PERIL_FAILED room=" + clean(room) + " kind=" + (kind == null ? "-" : kind.name()) + " reason=" + clean(reason);
    }
    static String clean(String s) {
        if (s == null) return "-";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 96; i++) { char c = s.charAt(i);b.append(c > 32 && c < 127 ? c : '_'); }
        return b.length() == 0 ? "-" : b.toString();
    }
}
