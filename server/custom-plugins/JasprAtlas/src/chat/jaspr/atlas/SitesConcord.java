package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/** The Concord's demes, villas, sanctuaries, farmsteads, gymnasia, schools, aqueducts and watch posts. */
final class SitesConcord {
    private SitesConcord() {}

    static void draw(Plans plans, Frame f, Plans.Site s) {
        switch (s.kind) {
            case DEME: deme(f, s); break;
            case VILLA: villa(f, s); break;
            case SANCTUARY: sanctuary(f, s); break;
            case FARMSTEAD: farmstead(f, s); break;
            case GYMNASIUM: gymnasium(f, s); break;
            case LYCEUM_ANNEX: lyceumAnnex(f, s); break;
            case AQUEDUCT: aqueductLine(f, s); break;
            case WATCH_OUTPOST: watchOutpost(f, s); break;
            default: break;
        }
    }

    /**
     * A deme: a village on a grid, its agora at the centre (a fountain, a heliodrome stone, a notice board), a temple,
     * a school or a bath, a stoa of shops, courtyard houses and townhouses, and gardens and fields round the edge.
     */
    private static void deme(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 44, 0);
        // Streets: a cross of paved streets and a ring.
        for (int a = -44; a <= 44; a++)
            for (int b = -2; b <= 2; b++) { f.pave(a, 0, b); f.pave(b, 0, a); }
        // The agora.
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) f.pave(x, 0, z);
        Frame c = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), f.base, f.rot);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) { if (x * x + z * z <= 5) { c.set(x, 1, z, x * x + z * z <= 2 ? WATER : QUARTZ, 0); } }
        c.set(0, 2, 0, QUARTZ, 2); c.set(0, 3, 0, WATER);
        Build.lamp(c, 5, 1, 5); Build.lamp(c, -5, 1, -5); Build.lamp(c, 5, 1, -5); Build.lamp(c, -5, 1, 5);
        c.post(-4, 1, 0, 1, 0, s.name.toUpperCase().replace("THE ", "") + "\nA DEME OF\nTHE CONCORD\nWELCOME");
        c.npc(4, 1, 3, -1, 0, "merchant:grocer", null);
        c.npc(-3, 1, 4, 1, 0, "citizen:elder", null);
        c.npc(2, 1, -5, 0, 1, "citizen:child", null);
        // Quarters: each of the four quadrants holds buildings on lots of 18.
        int lot = 0;
        for (int qx = -1; qx <= 1; qx += 2)
            for (int qz = -1; qz <= 1; qz += 2)
                for (int row = 0; row < 2; row++)
                    for (int col = 0; col < 2; col++) {
                        int lx = qx * (16 + col * 18), lz = qz * (16 + row * 18);
                        long lh = Hash.of(h, lot, 11);
                        lot++;
                        Frame b = new Frame(f.c, f.wx(lx, lz), f.wz(lx, lz), f.base, (f.rot + (qz < 0 ? 2 : 0)) & 3);
                        if (!b.touches(-9, -9, 9, 9)) continue;
                        int kind = lot == 1 ? 0 : lot == 6 ? 1 : lot == 11 ? 2 : (int) (Hash.unit(lh) * 6) + 3;
                        switch (kind) {
                            case 0: Houses.temple(b, lh, 7, 8, "the Lamp"); break;
                            case 1: if (Hash.unit(lh ^ 3) < 0.5) Houses.school(b, lh); else Houses.bath(b, lh); break;
                            case 2: Houses.stoa(b, lh, 8); break;
                            case 3: case 4: Houses.courtyardHouse(b, lh, 7, 6); break;
                            case 5: case 6: Houses.townhouse(b, lh, Houses.trade(lh)); break;
                            case 7: Houses.smithy(b, lh); break;
                            default: CellsConcord.draw(null, b, new Plans.Cell(0, 0, 0, 0, f.base, 0, lh, Realm.Zone.CONCORD, "garden", false));
                        }
                    }
    }

    /**
     * A villa: a rich family's country house with a peristyle garden, a private library room, a small bath, a dining
     * terrace over the fields, and their workers' quarters.
     */
    private static void villa(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 20, 0);
        Houses.courtyardHouse(f, h, 10, 8);
        // A library wing behind the house (every shelf readable).
        Frame lib = new Frame(f.c, f.wx(0, 14), f.wz(0, 14), f.base, f.rot);
        Houses.library(lib, h ^ 5, 7, 5, "villa");
        // A garden in front with a fountain and statues.
        Build.flowers(f, -18, -19, 18, -11, 0.3);
        Build.statue(f, -6, 1, -15, 0, -1, 4);
        Build.statue(f, 6, 1, -15, 0, -1, 4);
        Build.lamp(f, -3, 1, -11); Build.lamp(f, 3, 1, -11);
        f.npc(0, 1, -13, 0, 1, "citizen:villa_owner", null);
        Build.field(f, -19, 12, -9, 19, CROPS, false);
        Build.field(f, 9, 12, 19, 19, CARROTS, false);
        for (int z = -18; z <= 18; z += 6) { Build.cypress(f, -19, z); Build.cypress(f, 19, z); }
    }

    /** A sanctuary: a temple in a sacred grove, an altar of offerings, votive statues and a priests' house. */
    private static void sanctuary(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 22, 0);
        Houses.temple(f, h, 8, 11, Hash.unit(h) < 0.5 ? "the Lamp" : Hash.unit(h) < 0.75 ? "Theano" : "the Stranger");
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) Math.round(Math.cos(a) * 17), z = (int) Math.round(Math.sin(a) * 17);
            if (k % 2 == 0) Build.olive(f, x, z); else Build.cypress(f, x, z);
        }
        // The great altar before the temple.
        for (int x = -2; x <= 2; x++) f.ashlar(x, 1, -16);
        f.set(0, 2, -16, SEA_LANTERN); f.set(0, 3, -16, END_ROD, 1);
        for (int x = -4; x <= 4; x += 8) Build.statue(f, x, 1, -18, 0, -1, 4);
        Frame hs = new Frame(f.c, f.wx(15, 12), f.wz(15, 12), f.base, (f.rot + 3) & 3);
        Houses.courtyardHouse(hs, h ^ 9, 6, 5);
        f.npc(0, 1, -18, 0, 1, "citizen:pilgrim", null);
    }

    /** A farmstead: a farmhouse, a barn, pens of animals and fields to the edge of the site. */
    private static void farmstead(Frame f, Plans.Site s) {
        long h = s.hash;
        Build.field(f, -21, -21, 21, -8, CROPS, true);
        Build.field(f, -21, 10, -4, 21, POTATOES, false);
        Frame house = new Frame(f.c, f.wx(-8, 0), f.wz(-8, 0), f.base, f.rot);
        Houses.courtyardHouse(house, h, 7, 6);
        // The barn.
        Frame barn = new Frame(f.c, f.wx(10, 0), f.wz(10, 0), f.base, f.rot);
        barn.footings(-5, -4, 5, 4, 0, true);
        Build.floor(barn, -5, -4, 5, 4, 0, DOUBLE_SLAB, 8);
        Build.walls(barn, -5, -4, 5, 4, 1, 4, 3);
        Build.hollow(barn, -4, -3, 4, 3, 1, 4);
        Build.gable(barn, -5, -4, 5, 4, 5, false, 0);
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) barn.set(x, y, -4, AIR);
        for (int x = -4; x <= 4; x++) { barn.set(x, 1, 3, HAY); barn.set(x, 2, 3, x % 2 == 0 ? HAY : AIR); }
        barn.npc(0, 1, 0, 0, -1, "animal:cow", null);
        barn.npc(2, 1, -1, 0, -1, "animal:cow", null);
        Build.fence(f, 4, 10, 21, 21, FENCE, 0, 12, 10);
        for (int k = 0; k < 4; k++) f.npc(8 + 3 * k, CellsConcord.dy(f, 8 + 3 * k, 15) + 1, 15, 0, 1, "animal:sheep", null);
        f.npc(0, CellsConcord.dy(f, 0, 9) + 1, 9, 1, 0, "citizen:farmer", null);
    }

    /** A gymnasium: a colonnaded palaestra round a sand court, a running track, a bath, athletes and a trainer. */
    private static void gymnasium(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 25, 2);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) { f.set(x, 0, z, SAND); f.clear(x, z, 1, 6); }
        for (int x = -12; x <= 12; x += 3) { Build.column(f, x, -12, 1, 5); Build.column(f, x, 12, 1, 5); }
        for (int z = -12; z <= 12; z += 3) { Build.column(f, -12, z, 1, 5); Build.column(f, 12, z, 1, 5); }
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) if (Math.abs(x) >= 11 || Math.abs(z) >= 11) f.set(x, 6, z, DOUBLE_SLAB, 7);
        // The running track round the outside.
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) if (Math.max(Math.abs(x), Math.abs(z)) >= 21) f.set(x, 0, z, PATH);
        for (int k = 0; k < 3; k++) f.stand(-6 + k * 6, 1, 6, 0, -1, "target");
        f.npc(0, 1, 0, 0, 1, "citizen:trainer", null);
        f.npc(-4, 1, -3, 1, 0, "citizen:athlete", null);
        f.npc(4, 1, -3, -1, 0, "citizen:athlete", null);
        f.npc(18, 1, 22, -1, 0, "citizen:athlete", null);
        Frame bath = new Frame(f.c, f.wx(0, 17), f.wz(0, 17), f.base, f.rot);
        if (bath.touches(-9, -7, 9, 7)) Houses.bath(bath, h ^ 3);
    }

    /** A school of the Lyceum: two classrooms, a library and an orrery court. */
    private static void lyceumAnnex(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 22, 0);
        Frame a = new Frame(f.c, f.wx(-11, -8), f.wz(-11, -8), f.base, f.rot);
        Houses.school(a, h);
        Frame b = new Frame(f.c, f.wx(11, -8), f.wz(11, -8), f.base, f.rot);
        Houses.library(b, h ^ 7, 8, 6, "lyceum");
        Frame o = new Frame(f.c, f.wx(0, 11), f.wz(0, 11), f.base, f.rot);
        CellsConcord.draw(null, o, new Plans.Cell(0, 0, 0, 0, f.base, 0, h ^ 9, Realm.Zone.CONCORD, "orrery", false));
    }

    /** A line of the aqueduct crossing the whole site, with a castellum (distribution tank) at the middle. */
    private static void aqueductLine(Frame f, Plans.Site s) {
        long h = s.hash;
        for (int k = -1; k <= 1; k++) {
            Frame span = new Frame(f.c, f.wx(0, k * 24), f.wz(0, k * 24), f.base, f.rot);
            CellsConcord.draw(null, span, new Plans.Cell(0, 0, 0, 0, f.base, 0, h ^ k, Realm.Zone.CONCORD, "aqueduct", false));
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) { f.footing(x, z, 0, true); for (int y = 0; y <= 11; y++) { boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4; f.set(x, y, z, shell ? QUARTZ : y >= 9 ? WATER : AIR, 0); } }
        f.sign(0, 2, -5, 0, -1, "CASTELLUM OF\n" + Names.deme(h).toUpperCase() + "\nWATER FOR\nALL, FREELY");
        f.npc(-6, 1, 0, 1, 0, "citizen:engineer", null);
    }

    /** A watch post of the Line: a small tower and yard, a Talos, a sentry and a signal lamp. */
    private static void watchOutpost(Frame f, Plans.Site s) {
        long h = s.hash;
        Sites.pad(f, 16, 2);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            boolean wall = Math.max(Math.abs(x), Math.abs(z)) == 12;
            if (!wall) continue;
            for (int y = 1; y <= 4; y++) f.ashlar(x, y, z);
            if (Math.floorMod(x + z, 2) == 0) f.set(x, 5, z, SLAB, 7);
        }
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) f.set(x, y, -12, AIR);
        Frame t = new Frame(f.c, f.wx(0, 4), f.wz(0, 4), f.base, f.rot);
        CellsConcord.draw(null, t, new Plans.Cell(0, 0, 0, 0, f.base, 0, h, Realm.Zone.CONCORD, "signal_tower", false));
        f.npc(-6, 1, -6, 0, -1, "talos", null);
        f.npc(6, 1, -6, 0, -1, "citizen:hoplite", null);
        f.chest(-10, 1, 10, 1, 0, "atlas:depot", null);
    }
}
