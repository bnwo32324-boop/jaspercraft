package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;

/**
 * Pure body sizes (owner 2026-10-05: "scale up the bosses ... literally make them a larger size and have a larger hitbox").
 * A mob's scale grows with the room it stands in and with its own health: a boss is a towering figure in a great hall, an
 * ordinary mob only a little larger than vanilla in a big room. Everything is bounded by the room (the roof, the width of
 * the lanes the mobs walk) and by the server's reach rule (a melee hit lands from within six cells of the body's centre, so
 * no body is wider than five cells). No Bukkit, no clocks, no random state.
 */
public final class Scaling {
    private Scaling() {}
    public static final double MAX_SCALE = 5.0, BOSS_MAX_WIDTH = 5.0, MINION_MAX_WIDTH = 2.4, HEADROOM = 2.0, STEP = .05;
    /** A slime's box is 0.51 cells per size step (EntitySlime). */
    public static final double CUBE = .51;

    /** Air cells between the floor and the roof of a room. */
    public static int clearHeight(Layout.Room r) { return r.roof() - (Layout.FLOOR + 1); }
    /** 0 at 20 health, one more per doubling, capped at 4 (320 health). */
    static double healthTerm(double hp) { return Math.max(0, Math.min(4, Math.log(Math.max(10, hp) / 20) / Math.log(2))); }
    /** 0 in a 32 x 32 room, 1 in a 128 x 128 one. */
    static double spanTerm(Layout.Room r) { return Math.max(0, Math.min(1, (Math.sqrt((double) r.w * r.d) - 32) / 96)); }
    /** Species whose bodies the server can resize in place. A slime carries its own size; a shulker rebuilds its box every tick. */
    public static boolean resizable(Species s) { return s != Species.SHULKER && !sized(s) && !s.mutant(); }
    /** Slimes and magma cubes grow through their native size, which the client already draws and the server already measures;
     *  so do custom species on their bodies (the Magma Lurker), which Encounters would otherwise give slime size 0. */
    public static boolean sized(Species s) { return "SLIME".equals(s.base) || "MAGMA_CUBE".equals(s.base); }

    private static double wanted(Layout.Room r, boolean boss, double hp) {
        return boss ? 2.0 + .40 * healthTerm(hp) + 1.60 * spanTerm(r) : 1.0 + .10 * healthTerm(hp) + .45 * spanTerm(r);
    }
    /** The factor applied to the species' vanilla width and height: 1 for a vanilla body, at most {@value #MAX_SCALE}. */
    public static double scale(Layout.Room r, Species s, boolean boss, double hp) {
        if (!resizable(s)) return 1;
        double cap = Math.min(MAX_SCALE, Math.min((clearHeight(r) - HEADROOM) / s.height, (boss ? BOSS_MAX_WIDTH : MINION_MAX_WIDTH) / s.width));
        double v = Math.max(1, Math.min(wanted(r, boss, hp), cap));
        return Math.floor(v / STEP + 1e-9) * STEP;
    }
    /** The native size of a slime or magma cube: 2 is a vanilla-sized minion, 4 the old boss. */
    public static int slimeSize(Layout.Room r, boolean boss, double hp) {
        double cap = Math.min((clearHeight(r) - HEADROOM) / CUBE, (boss ? BOSS_MAX_WIDTH : MINION_MAX_WIDTH) / CUBE);
        return (int) Math.max(boss ? 4 : 2, Math.min(Math.floor(wanted(r, boss, hp) * 2), Math.floor(cap)));
    }

    /** What a spawned mob will be: its scale (1 for vanilla bodies and for slimes), its slime size (0 otherwise) and the bounds placement must clear. */
    public static final class Body {
        public final double scale, width, height;
        public final int size;
        Body(double scale, int size, double width, double height) { this.scale = scale; this.size = size; this.width = width; this.height = height; }
        public boolean big() { return scale > 1.0001 || size > 2; }
    }
    /** Conservative bounds (the species' placement bounds times the scale) so a body that fits its station never touches a wall or the roof. */
    public static Body body(Layout.Room r, Species s, boolean boss, double hp) {
        if (sized(s)) {
            int n = slimeSize(r, boss, hp);
            return new Body(1, n, CUBE * n + .05, CUBE * n + .05);
        }
        double k = scale(r, s, boss, hp);
        return new Body(k, 0, s.width * k, s.height * k);
    }
    /**
     * A vanilla stand-in's own size {width, height}: without JasprMutants a mutant spawns as its fallback (MUTANT_ZOMBIE as a zombie ...),
     * and then it should look and measure like that vanilla mob, scaled like any other (2026-10-08: a floor guardian standing in for a
     * mutant was a plain-size zombie with a mutant's hitbox).
     */
    public static double[] standInSize(String type) {
        switch (type == null ? "" : type) {
            case "SKELETON": return new double[]{.6, 1.99};
            case "CREEPER": return new double[]{.6, 1.7};
            case "ENDERMAN": return new double[]{.6, 2.9};
            case "SPIDER": return new double[]{1.4, .9};
            case "SNOWMAN": return new double[]{.7, 1.9};
            default: return new double[]{.6, 1.95};
        }
    }
    /** A vanilla stand-in's body: scaled by its room and health like any resizable mob of that size. */
    public static Body standIn(Layout.Room r, double[] size, boolean boss, double hp) {
        double cap = Math.min(MAX_SCALE, Math.min((clearHeight(r) - HEADROOM) / size[1], (boss ? BOSS_MAX_WIDTH : MINION_MAX_WIDTH) / size[0]));
        double v = Math.max(1, Math.min(wanted(r, boss, hp), cap));
        double k = Math.floor(v / STEP + 1e-9) * STEP;
        return new Body(k, 0, size[0] * k, size[1] * k);
    }
    /** A stand-in's body read back from its journal (its scale tag). */
    public static Body knownStandIn(double[] size, double scale) {
        double k = Math.max(1, scale);
        return new Body(k, 0, size[0] * k, size[1] * k);
    }
    /** A body read back from a spawned mob's journal (its scale tag, or its slime size). */
    public static Body known(Species s, double scale, int size) {
        if (sized(s)) return new Body(1, Math.max(1, size), CUBE * Math.max(1, size) + .05, CUBE * Math.max(1, size) + .05);
        double k = Math.max(1, scale);
        return new Body(k, 0, s.width * k, s.height * k);
    }
}
