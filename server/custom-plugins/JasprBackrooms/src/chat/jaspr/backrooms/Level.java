package chat.jaspr.backrooms;

import org.bukkit.ChatColor;

/**
 * The seven liminal spaces, in the order they are crossed (owner, 2026-09-30). Each level is a closed zone of the
 * Backrooms world: level n spans x from (n-1)*SPACING (its entry room) eastwards to its exit, and z around 0. A player
 * enters a level in its entry room (the Threshold for level 1, a landing for the others), crosses it eastwards, beats
 * the level's boss in the arena at the far end, and walks through the exit into the next level. Danger grows with the
 * level's number and with the distance crossed inside it ({@link #progress}).
 */
enum Level {
    YELLOW(1, "The Yellow Rooms", "Mono-yellow wallpaper, damp carpet, the hum of the lights.", ChatColor.YELLOW,
        416, 224, 4, 48, 40, "The Smiler", "smiler"),
    WAREHOUSE(2, "The Warehouse", "Endless aisles of racks under a roof you cannot see.", ChatColor.GOLD,
        480, 256, 14, 56, 56, "The Foreman", "foreman"),
    TUNNELS(3, "The Maintenance Tunnels", "Hot, narrow, and the pipes are never quiet.", ChatColor.RED,
        512, 224, 3, 48, 40, "The Stoker", "stoker"),
    ELECTRICAL(4, "The Electrical Corridors", "Live wires, humming panels, sparks in the dark.", ChatColor.AQUA,
        512, 224, 4, 52, 44, "The Live Wire", "live_wire"),
    OFFICE(5, "The Abandoned Office", "Cubicles, cold coffee and nobody at the desks.", ChatColor.GRAY,
        544, 240, 4, 56, 44, "The Manager", "manager"),
    CITY(6, "The Endless City", "It seems normal. It is not.", ChatColor.BLUE,
        640, 336, 0, 80, 72, "The Stranger", "stranger"),
    POOLS(7, "The Poolrooms", "White tiles, warm water, no way back up.", ChatColor.DARK_AQUA,
        704, 272, 8, 88, 72, "The Lifeguard", "lifeguard");

    /** Distance between the levels' origins along x; nothing is built between zones. */
    static final int SPACING = 4096;
    /** Floor blocks sit at FLOOR; players walk at FLOOR + 1. */
    static final int FLOOR = 40, WALK = FLOOR + 1;
    /** Entry room lengths (the Threshold in level 1, a landing elsewhere) and the exit corridor at the east end. */
    static final int HUB_LEN = 48, LANDING_LEN = 30, EXIT_LEN = 14, WALL = 2;
    static final Level[] ALL = values();

    final int number, length, width, air, arenaLength, arenaWidth;
    final String title, blurb, boss, bossKey;
    final ChatColor colour;

    Level(int number, String title, String blurb, ChatColor colour, int length, int width, int air, int arenaLength, int arenaWidth, String boss, String bossKey) {
        this.number = number; this.title = title; this.blurb = blurb; this.colour = colour; this.length = length; this.width = width;
        this.air = air; this.arenaLength = arenaLength; this.arenaWidth = arenaWidth; this.boss = boss; this.bossKey = bossKey;
    }

    int index() { return number - 1; }
    String label() { return "Level " + number + ": " + title; }
    Level next() { return number < ALL.length ? ALL[number] : null; }
    static Level of(int number) { return number >= 1 && number <= ALL.length ? ALL[number - 1] : null; }
    boolean last() { return number == ALL.length; }

    // ---- the zone ---------------------------------------------------------------------------------------------------
    int x0() { return index() * SPACING; }
    int xEnd() { return x0() + length; }
    int zMin() { return -width / 2; }
    int zMax() { return width / 2; }   // exclusive
    int entryEnd() { return x0() + (number == 1 ? HUB_LEN : LANDING_LEN); }
    int exitStart() { return xEnd() - WALL - EXIT_LEN; }
    int arenaStart() { return exitStart() - arenaLength; }
    int arenaCentreX() { return arenaStart() + arenaLength / 2; }
    boolean contains(double x, double z) { return x >= x0() && x < xEnd() && z >= zMin() && z < zMax(); }
    boolean inBody(int x) { return x >= entryEnd() && x < arenaStart(); }
    boolean inArena(double x, double z) { return x >= arenaStart() && x < exitStart() && Math.abs(z) <= arenaWidth / 2.0; }

    /** The level whose zone holds (x, z), or null (between zones there is nothing). */
    static Level at(double x, double z) {
        int i = (int) Math.floor(x / SPACING);
        if (i < 0 || i >= ALL.length) return null;
        return ALL[i].contains(x, z) ? ALL[i] : null;
    }

    /** How far into the level a spot lies: 0 at the end of its entry room, 1 at its arena. */
    double progress(double x) {
        return Math.max(0, Math.min(1, (x - entryEnd()) / (double) (arenaStart() - entryEnd())));
    }

    /**
     * Danger across the whole Backrooms, 0 at the Threshold to 1 at the Lifeguard: each level adds a seventh, and the
     * way across a level adds that level's seventh gradually. The arena counts as the level's far end.
     */
    double danger(double x, double z) {
        double d = inArena(x, z) || x >= exitStart() ? 1 : progress(x);
        return (index() + d) / ALL.length;
    }

    /** The meandering path that is always open from the entry room to the arena (blended to z = 0 at both ends). */
    double spineZ(long seed, double x) {
        double a = entryEnd(), b = arenaStart();
        if (x <= a || x >= b) return 0;
        double t = (x - a) / (b - a), fade = Math.min(1, Math.min(t, 1 - t) * 6);
        double amp = width / 2.0 - 22, p1 = Hash.unit(seed, number, 11, 0) * 6.283, p2 = Hash.unit(seed, number, 12, 0) * 6.283;
        double z = amp * (0.62 * Math.sin(x / 57.0 + p1) + 0.38 * Math.sin(x / 23.0 + p2));
        return z * fade;
    }

    /**
     * Whether (x, z) lies on the spine's band of half-width r. The band covers the whole stretch of the curve between
     * x - 0.5 and x + 0.5, so consecutive columns always overlap and the path stays walkable where it turns steeply.
     */
    boolean onSpine(long seed, int x, int z, double r) {
        double a = spineZ(seed, x - 0.5), b = spineZ(seed, x + 0.5);
        return z >= Math.min(a, b) - r && z <= Math.max(a, b) + r;
    }
}
