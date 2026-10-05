package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The twenty great structures of epoch 6 (owner, 2026-10-04: "In the Drown Hollow dimension, make 20 new big structures
 * there as well. They all should be unique and have bosses, and they should include all types of mobs and custom mobs"):
 * their designs, their layouts (made once per site), the drawing, and where their bosses and garrisons stand in the world.
 */
final class Greats {
    private Greats() {}

    static final GreatDesign[] DESIGNS = new GreatDesign[Plans.Great.values().length];
    static {
        DESIGNS[Plans.Great.NECROPOLIS.ordinal()] = new GreatNecropolis();
        DESIGNS[Plans.Great.CATHEDRAL.ordinal()] = new GreatCathedral();
        DESIGNS[Plans.Great.SLEEPER.ordinal()] = new GreatSleeper();
        DESIGNS[Plans.Great.STAR_TOWER.ordinal()] = new GreatStarTower();
        DESIGNS[Plans.Great.SHOGGOTH_VATS.ordinal()] = new GreatShoggothVats();
        DESIGNS[Plans.Great.DREADNOUGHT.ordinal()] = new GreatDreadnought();
        DESIGNS[Plans.Great.MIGO_HIVE.ordinal()] = new GreatMigoHive();
        DESIGNS[Plans.Great.TINDALOS.ordinal()] = new GreatTindalos();
        DESIGNS[Plans.Great.BLACK_GOAT.ordinal()] = new GreatBlackGoat();
        DESIGNS[Plans.Great.BEACON.ordinal()] = new GreatBeacon();
        DESIGNS[Plans.Great.VIADUCT.ordinal()] = new GreatViaduct();
        DESIGNS[Plans.Great.CELAENO.ordinal()] = new GreatCelaeno();
        DESIGNS[Plans.Great.TERRACES.ordinal()] = new GreatTerraces();
        DESIGNS[Plans.Great.BASTION.ordinal()] = new GreatBastion();
        DESIGNS[Plans.Great.SILVER_GATE.ordinal()] = new GreatSilverGate();
        DESIGNS[Plans.Great.LENG.ordinal()] = new GreatLeng();
        DESIGNS[Plans.Great.ELDER_VAULT.ordinal()] = new GreatElderVault();
        DESIGNS[Plans.Great.CISTERN.ordinal()] = new GreatCistern();
        DESIGNS[Plans.Great.ORRERY.ordinal()] = new GreatOrrery();
        DESIGNS[Plans.Great.GOLGOTHA.ordinal()] = new GreatGolgotha();
    }

    static GreatDesign design(Plans.Great k) { return DESIGNS[k.ordinal()]; }

    /** The site's layout, made once from its own Random; every garrison completed to the whole roster. */
    static GreatDesign.Layout plan(Plans.GreatSite s) {
        Object p = s.plan;
        if (p != null) return (GreatDesign.Layout) p;
        synchronized (s) {
            if (s.plan != null) return (GreatDesign.Layout) s.plan;
            GreatDesign.Layout l = design(s.kind).plan(s, new Random(s.hash));
            if (l.boss == null) throw new IllegalStateException("no boss point in " + s.kind);
            GreatDesign.complete(l.garrisons);
            s.plan = l;
            return l;
        }
    }

    /** Draws the part of a great structure inside the canvas' chunk. */
    static void draw(Plans.GreatSite s, Canvas c) {
        int r = s.kind.radius + 1;
        if (!c.touches(s.x - r, s.z - r, s.x + r, s.z + r)) return;
        design(s.kind).draw(s, plan(s), c);
    }

    /** Where the boss wakes, in world coordinates. */
    static int[] boss(Plans.GreatSite s) {
        int[] b = plan(s).boss;
        Frame f = new Frame(null, s.x, s.z, s.base, s.rot);
        return new int[] {f.wx(b[0], b[2]), s.base + b[1], f.wz(b[0], b[2])};
    }

    /** One garrison point in world coordinates: x, y, z, and its pack. */
    static final class Point {
        final int x, y, z; final String pack;
        Point(int x, int y, int z, String pack) { this.x = x; this.y = y; this.z = z; this.pack = pack; }
    }

    static List<Point> garrisons(Plans.GreatSite s) {
        Frame f = new Frame(null, s.x, s.z, s.base, s.rot);
        List<Point> out = new ArrayList<>();
        for (GreatDesign.Garrison g : plan(s).garrisons) out.add(new Point(f.wx(g.u, g.v), s.base + g.y, f.wz(g.u, g.v), g.pack));
        return out;
    }

    /** The arena key of a great structure (its boss's cooldown and the tags of its boss). */
    static String arena(Plans.GreatSite s) { return "great_" + s.i + "_" + s.j; }
}
