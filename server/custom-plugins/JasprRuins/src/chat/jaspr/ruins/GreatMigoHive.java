package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Hive of the Mi-Go (epoch 6): the fungi from Yuggoth grew it out of the rock, a cluster of twisted spires of purpur,
 * end stone, obsidian and purple clay over domed chambers joined by ribbed tubes. In the middle the Overseer's chamber
 * (a dome thirty across, pillars of control, the minds it keeps) where the Mi-Go Overseer waits; round it the antechamber
 * behind the fanged mouth, the hall of brain cylinders, the surgery chambers (a hub and three cells), the crystal gardens
 * of fungus and prismarine, the roost (a hollow spire with a spiral ramp to its nests) and the landing spire, ninety
 * blocks high, with its landing platform at fifty and the burrow down to the brood chamber beneath it. Lesser spires,
 * crystal outcrops and spore pods stand about on the mineral crust.
 */
final class GreatMigoHive extends GreatDesign {
    private static final int MAS = -1, RUB = -3, CRUST = -11, HIVE = -12, BAND = -13, PURP = -14;
    private static final int PURPUR = 201, PURPUR_PILLAR = 202, PURPUR_SLAB = 205, END_STONE = 121, END_BRICKS = 206, END_ROD = 198, TINT = 95,
        QUARTZ_B = 155, STAIRS = 203, WOOL = 35;
    private static final int E = 43;                                                   // the levelled crust's half-width

    // the chambers: centre u, v, radius, height
    private static final int[] OVERSEER = {0, 0, 15, 13}, ANTE = {0, -29, 7, 7}, BRAINS = {-26, -14, 12, 10}, HUB = {-27, 15, 6, 6},
        CELL1 = {-37, 7, 5, 5}, CELL2 = {-37, 23, 5, 5}, CELL3 = {-24, 30, 5, 5}, GARDEN = {26, 17, 10, 9};
    private static final int[][] DOMES = {OVERSEER, ANTE, BRAINS, HUB, CELL1, CELL2, CELL3, GARDEN};
    private static final int RX = 26, RZ = -15, LX = 0, LZ = 30, LTOP = 52;           // the roost and the landing spire
    // the tubes: from u, v to u, v, and whether the far end opens to the outside
    private static final int[][] TUBES = {
        {0, -41, 0, -29, 1}, {0, -29, 0, 0, 0}, {0, 0, -26, -14, 0}, {0, 0, RX, RZ, 0}, {0, 0, -27, 15, 0}, {0, 0, 26, 17, 0}, {0, 0, LX, LZ, 0},
        {0, -29, -26, -14, 0}, {0, -29, RX, RZ, 0}, {-27, 15, -37, 7, 0}, {-27, 15, -37, 23, 0}, {-27, 15, -24, 30, 0}, {-26, -14, -27, 15, 0},
        {RX, RZ, 26, 17, 0}, {26, 17, LX, LZ, 0}, {-24, 30, LX, LZ, 0}, {41, 24, 26, 17, 1}, {-41, -24, -26, -14, 1}};

    /** A spire: its radius at each height (with its swellings), and the twist of its ribs. */
    static final class Spire {
        final int u, v, h, hollow;
        final double ph;
        final double[] rad, cb, sb;
        Spire(int u, int v, double r0, int hs, int h, int hollow, double ph) {
            this.u = u; this.v = v; this.h = h; this.hollow = hollow; this.ph = ph;
            rad = new double[h + 1]; cb = new double[h + 1]; sb = new double[h + 1];
            for (int y = 0; y <= h; y++) {
                double g = y <= hs ? 1 - 0.25 * y / Math.max(1, hs) : 0.75 * Math.pow(Math.max(0, 1 - (y - hs) / (double) (h - hs)), 0.9);
                if (hs == 0) g = Math.pow(Math.max(0, 1 - y / (double) h), 0.8);
                rad[y] = r0 * g * (1 + 0.12 * Math.sin(y * 0.45 + ph));
                cb[y] = Math.cos(y * 0.16 + ph);
                sb[y] = Math.sin(y * 0.16 + ph);
            }
        }
        int reach() { double m = 0; for (double r : rad) m = Math.max(m, r); return (int) Math.ceil(m * 1.1) + 1; }
    }

    static final class L extends Layout {
        long h;
        final List<Spire> spires = new ArrayList<>();
        final List<int[]> crystals = new ArrayList<>();   // {u, v, height, lean u, lean v, kind}
        final List<int[]> pods = new ArrayList<>();       // {u, v, radius}
        final List<int[]> fungi = new ArrayList<>();      // {u, v, stalk height, cap radius, kind} in the gardens
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        l.h = s.hash;
        l.spires.add(new Spire(LX, LZ, 8.5, 56, 90, LTOP + 3, r.nextDouble() * 6));      // the landing spire
        l.spires.add(new Spire(RX, RZ, 8, 42, 60, 46, r.nextDouble() * 6));              // the roost
        l.spires.add(new Spire(0, 0, 6.5, 0, 38, 0, r.nextDouble() * 6));                // the crown of the Overseer's dome
        l.spires.add(new Spire(-26, -14, 5, 0, 36, 0, r.nextDouble() * 6));              // over the brains
        l.spires.add(new Spire(26, 17, 4.5, 0, 30, 0, r.nextDouble() * 6));              // over the gardens
        l.spires.add(new Spire(-27, 15, 3.5, 0, 22, 0, r.nextDouble() * 6));             // over the surgery
        // lesser spires, outcrops and pods on the crust, clear of the chambers and tubes
        for (int n = 0, tries = 0; n < 9 && tries < 400; tries++) {
            int u = -38 + r.nextInt(77), v = -38 + r.nextInt(77);
            double r0 = 2.5 + r.nextDouble() * 2;
            if (!clear(u, v, r0 + 3)) continue;
            l.spires.add(new Spire(u, v, r0, 0, 12 + r.nextInt(20), 0, r.nextDouble() * 6));
            n++;
        }
        for (int n = 0, tries = 0; n < 12 && tries < 400; tries++) {
            int u = -40 + r.nextInt(81), v = -40 + r.nextInt(81);
            if (!clear(u, v, 4)) continue;
            int k = 3 + r.nextInt(3);
            for (int j = 0; j < k; j++) l.crystals.add(new int[] {u + r.nextInt(5) - 2, v + r.nextInt(5) - 2, 3 + r.nextInt(7), r.nextInt(3) - 1, r.nextInt(3) - 1, r.nextInt(3)});
            n++;
        }
        for (int n = 0, tries = 0; n < 7 && tries < 300; tries++) {
            int u = -40 + r.nextInt(81), v = -40 + r.nextInt(81), pr = 2 + r.nextInt(2);
            if (!clear(u, v, pr + 2)) continue;
            l.pods.add(new int[] {u, v, pr});
            n++;
        }
        for (int n = 0, tries = 0; n < 9 && tries < 300; tries++) {
            int du = r.nextInt(15) - 7, dv = r.nextInt(15) - 7;
            if (du * du + dv * dv > 50 || du * du + dv * dv < 9 || Math.abs(du + 4) <= 1 && Math.abs(dv + 5) <= 1) continue;
            if (Math.abs(du - 3) <= 2 && Math.abs(dv + 5) <= 2 || Math.abs(du - 2) <= 2 && Math.abs(dv - 6) <= 2 || Math.abs(du) <= 1 && Math.abs(dv - 6) <= 1 || Math.abs(du - 5) <= 1 && Math.abs(dv + 3) <= 1) continue;
            boolean nearTube = false;
            int u = GARDEN[0] + du, v = GARDEN[1] + dv;
            for (int[] t : TUBES) if (tubeDist(t, u, v) < 3.6) nearTube = true;
            if (nearTube) continue;
            l.fungi.add(new int[] {u, v, 2 + r.nextInt(4), 1 + r.nextInt(2), r.nextInt(3)});
            n++;
        }
        l.boss = new int[] {0, 1, 0};
        int[][] pts = {
            {0, 1, -29}, {0, 1, -38}, {8, 1, 4}, {-8, 1, -5}, {-26, 1, -14}, {-26, 1, -24}, {-27, 1, 15}, {-37, 1, 4}, {-37, 1, 20},
            {27, 1, 17}, {22, 1, 12}, {RX, 1, RZ}, {RX, 41, RZ}, {-2, 1, 27}, {3, 27, 28}, {5, LTOP + 1, 39}, {-4, -9, 28}, {5, -9, 34},
            {13, 1, 9}, {-10, 1, -40}, {30, 1, -36}, {-36, 1, 36}};
        String[] packs = {
            "cult_zealot+vindicator", "mi_go+endermite", "!mi_go+enderman", "cult_adept+evoker", "illusioner+witch", "mi_go+zombie_villager",
            "cult_adept+witch", "zombie+husk", "skeleton+stray", "star_spawn+silverfish", "deep_one+spider", "mi_go+enderman", "nightgaunt",
            "ghoul+cave_spider", "wither_skeleton+creeper", "nightgaunt+creeper", "tomb_crawler+endermite", "shoggoth+slime", "hound+spider",
            "ghoul+zombie", "hound+creeper", "deep_one+husk"};
        for (int k = 0; k < pts.length; k++) l.garrisons.add(g(pts[k][0], pts[k][1], pts[k][2], packs[k]));
        return l;
    }

    /** Whether a spot on the crust keeps clear of every chamber, tube and great spire by {@code pad}. */
    private static final int[][] POSTS = {{-10, -40}, {30, -36}, {-36, 36}};

    private static boolean clear(int u, int v, double pad) {
        if (Math.abs(u) > 41 - pad / 2 || Math.abs(v) > 41 - pad / 2) return false;
        for (int[] q : POSTS) if (Math.hypot(u - q[0], v - q[1]) < 3 + pad) return false;
        for (int[] d : DOMES) if (Math.hypot(u - d[0], v - d[1]) < d[2] + 2 + pad) return false;
        for (int[] t : TUBES) if (tubeDist(t, u, v) < 4.5 + pad) return false;
        if (Math.hypot(u - RX, v - RZ) < 11 + pad || Math.hypot(u - LX, v - LZ) < 14 + pad) return false;
        return true;
    }

    private static double tubeDist(int[] t, double u, double v) {
        double dx = t[2] - t[0], dz = t[3] - t[1], len2 = dx * dx + dz * dz;
        double s = Math.max(0, Math.min(1, ((u - t[0]) * dx + (v - t[1]) * dz) / len2));
        double ex = t[0] + s * dx - u, ez = t[1] + s * dz - v;
        return Math.sqrt(ex * ex + ez * ez);
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        L l = (L) plan;
        P p = new P(s, c);
        if (!p.hit(-44, -44, 44, 44)) return;
        crust(p);
        // the solids first (shells of chambers and tubes, the spires, pods and crystals), then everything hollowed out of them
        for (int[] d : DOMES) dome(p, d, true);
        for (int[] t : TUBES) tube(p, t, true);
        for (Spire sp : l.spires) spire(p, sp, true);
        brood(p, true);
        for (int[] pd : l.pods) pod(p, pd);
        for (int[] cr : l.crystals) crystal(p, cr);
        for (int[] d : DOMES) dome(p, d, false);
        for (int[] t : TUBES) tube(p, t, false);
        for (Spire sp : l.spires) spire(p, sp, false);
        brood(p, false);
        overseer(p);
        brains(p);
        surgery(p);
        garden(p, l);
        roost(p, l.spires.get(1));
        landing(p);
        mouth(p);
        tiles(p, l);
    }

    // ------------------------------------------------------------------------------------------------ the crust

    private static void crust(P p) {
        for (int u = p.ua(-E); u <= p.ub(E); u++)
            for (int v = p.va(-E); v <= p.vb(E); v++) {
                int g = p.ground(u, v);
                for (int y = Math.max(-12, g - 1); y < 0; y++) p.set(u, y, v, RUB, 0);
                p.set(u, 0, v, CRUST, 0);
                if (g >= 0) p.clear(u, v, 1, g + 1);
            }
    }

    // ------------------------------------------------------------------------------------------------ chambers, tubes, spires

    /** A domed chamber: its shell (solid pass) or its hollow and floor (the second pass). */
    private static void dome(P p, int[] d, boolean solid) {
        int cu = d[0], cv = d[1], rr = d[2], hh = d[3], ro = rr + 2;
        if (!p.hit(cu - ro, cv - ro, cu + ro, cv + ro)) return;
        for (int u = p.ua(cu - ro); u <= p.ub(cu + ro); u++)
            for (int v = p.va(cv - ro); v <= p.vb(cv + ro); v++) {
                double q = (u - cu) * (u - cu) + (v - cv) * (v - cv);
                double qo = q / ((rr + 1.5) * (rr + 1.5));
                if (qo >= 1) continue;
                int yOut = (int) Math.floor((hh + 1.5) * Math.sqrt(1 - qo));
                int yIn = q < rr * rr ? (int) Math.floor(hh * Math.sqrt(1 - q / (rr * rr))) : 0;
                if (solid) {
                    double ang = Math.atan2(v - cv, u - cu);
                    boolean rib = Math.abs(Math.sin(ang * 4)) < 0.12;
                    for (int y = Math.max(1, yIn + 1); y <= yOut; y++) p.set(u, y, v, rib ? OBSIDIAN : HIVE, 0);
                } else if (yIn >= 1) {
                    p.set(u, 0, v, PURP, 0);
                    p.clear(u, v, 1, yIn);
                }
            }
    }

    /** A ribbed tube: an elliptical vault, ribbed in obsidian every four blocks; open ends reach past their shells. */
    private static void tube(P p, int[] t, boolean solid) {
        double w = 2.6, hh = 5.5, dx = t[2] - t[0], dz = t[3] - t[1], len = Math.sqrt(dx * dx + dz * dz);
        double ex = t[4] == 1 ? 2.5 : 0;   // an open start reaches past its shell
        int a0 = Math.min(t[0], t[2]) - 5, a1 = Math.max(t[0], t[2]) + 5, b0 = Math.min(t[1], t[3]) - 5, b1 = Math.max(t[1], t[3]) + 5;
        if (!p.hit(a0, b0, a1, b1)) return;
        for (int u = p.ua(a0); u <= p.ub(a1); u++)
            for (int v = p.va(b0); v <= p.vb(b1); v++) {
                double sRaw = ((u - t[0]) * dx + (v - t[1]) * dz) / len;
                if (Math.abs(u) > 44 || Math.abs(v) > 44) continue;
                double s = Math.max(-ex, Math.min(len, sRaw));
                double cx = t[0] + s / len * dx - u, cz = t[1] + s / len * dz - v, d = Math.sqrt(cx * cx + cz * cz);
                if (solid) {
                    double so = Math.max(0, Math.min(len, sRaw));
                    double ox = t[0] + so / len * dx - u, oz = t[1] + so / len * dz - v, dd = Math.sqrt(ox * ox + oz * oz);
                    if (dd > w + 1.3) continue;
                    int yOut = (int) Math.floor(0.5 + (hh + 1.3) * Math.sqrt(1 - dd * dd / ((w + 1.3) * (w + 1.3))));
                    boolean rib = Math.floorMod((int) Math.round(so), 4) == 0;
                    for (int y = 1; y <= yOut; y++) p.set(u, y, v, rib ? OBSIDIAN : HIVE, 0);
                } else {
                    if (d >= w) continue;
                    int yIn = (int) Math.floor(0.5 + hh * Math.sqrt(1 - d * d / (w * w)));
                    p.set(u, 0, v, PURP, 0);
                    p.clear(u, v, 1, yIn);
                    if (yIn >= 5 && Math.floorMod((int) Math.round(s), 16) == 8 && d < 0.6 && len > 20) p.set(u, yIn + 1, v, SEA_LANTERN, 0);
                }
            }
    }

    /** A twisted spire: banded, ribbed, swelling and narrowing; hollow ones are carved in the second pass. */
    private static void spire(P p, Spire sp, boolean solid) {
        int rr = sp.reach();
        if (!p.hit(sp.u - rr, sp.v - rr, sp.u + rr, sp.v + rr)) return;
        if (!solid && sp.hollow == 0) return;
        for (int u = p.ua(sp.u - rr); u <= p.ub(sp.u + rr); u++)
            for (int v = p.va(sp.v - rr); v <= p.vb(sp.v + rr); v++) {
                double du = u - sp.u, dv = v - sp.v, d = Math.sqrt(du * du + dv * dv);
                double a = Math.atan2(dv, du), c5 = Math.cos(5 * a), s5 = Math.sin(5 * a);
                for (int y = 1; y <= sp.h; y++) {
                    double rib = c5 * sp.cb[y] - s5 * sp.sb[y];
                    double r = sp.rad[y] * (1 + 0.09 * rib);
                    if (d > r) continue;
                    boolean dark = d > r - 1.2 && rib > 0.7 || y == sp.h || r < 0.9;
                    if (solid) p.set(u, y, v, dark ? OBSIDIAN : BAND, dark ? 0 : y);
                    else if (y <= sp.hollow && d < r - 1.6) p.set(u, y, v, AIR, 0);
                }
                if (solid && d < 0.6) p.set(u, sp.h + 1, v, END_ROD, 1);
            }
    }

    /** The brood chamber under the landing spire: a low dome sunk ten blocks, reached by the burrow stair. */
    private static void brood(P p, boolean solid) {
        if (!p.hit(LX - 12, LZ - 12, LX + 12, LZ + 12)) return;
        for (int u = p.ua(LX - 12); u <= p.ub(LX + 12); u++)
            for (int v = p.va(LZ - 12); v <= p.vb(LZ + 12); v++) {
                double q = (u - LX) * (u - LX) + (v - LZ) * (v - LZ);
                if (q >= 11.5 * 11.5) continue;
                int yOut = -11 + (int) Math.floor(9 * Math.sqrt(1 - q / (11.5 * 11.5)));
                int yIn = q < 100 ? -10 + (int) Math.floor(7 * Math.sqrt(1 - q / 100)) : -11;
                if (solid) { for (int y = -12; y <= Math.max(yOut, -1); y++) if (y <= -11 || y > yIn) p.set(u, y, v, y <= -11 ? RUB : HIVE, 0); }
                else if (yIn >= -9) { p.set(u, -10, v, PURP, 0); for (int y = -9; y <= yIn; y++) p.set(u, y, v, AIR, 0); }
            }
    }

    /** A spore pod half sunk in the crust: a sphere of purple glass round a magenta heart. */
    private static void pod(P p, int[] pd) {
        int r = pd[2];
        if (!p.hit(pd[0] - r, pd[1] - r, pd[0] + r, pd[1] + r)) return;
        for (int u = p.ua(pd[0] - r); u <= p.ub(pd[0] + r); u++)
            for (int v = p.va(pd[1] - r); v <= p.vb(pd[1] + r); v++)
                for (int y = 0; y <= r; y++) {
                    double q = (u - pd[0]) * (u - pd[0]) + (v - pd[1]) * (v - pd[1]) + (y - 0.5) * (y - 0.5);
                    if (q > (r + 0.5) * (r + 0.5)) continue;
                    p.set(u, y, v, q < (r - 0.6) * (r - 0.6) ? (q < 1.6 ? CLAY : AIR) : TINT, q < 1.6 ? 2 : 10);
                }
    }

    /** A crystal of prismarine, purpur or quartz, leaning, one to a column per step. */
    private static void crystal(P p, int[] cr) {
        if (!p.hit(cr[0] - 4, cr[1] - 4, cr[0] + 4, cr[1] + 4)) return;
        for (int y = 1; y <= cr[2]; y++) {
            int u = cr[0] + cr[3] * (y / 3), v = cr[1] + cr[4] * (y / 3);
            int id = cr[5] == 0 ? PRISMARINE : cr[5] == 1 ? PURPUR_PILLAR : QUARTZ_B, meta = cr[5] == 0 ? (y % 2) : cr[5] == 2 ? 2 : 0;
            p.set(u, y, v, y == cr[2] && cr[5] == 0 && cr[2] >= 8 ? SEA_LANTERN : id, meta);
        }
    }

    // ------------------------------------------------------------------------------------------------ the chambers' furniture

    /** A brain cylinder: a quartz-ringed jar of purple glass, a pink mind floating in it, a wire up from its lid. */
    private static void cylinder(P p, int cu, int y0, int cv, boolean rod) {
        if (!p.hit(cu - 1, cv - 1, cu + 1, cv + 1)) return;
        for (int u = cu - 1; u <= cu + 1; u++)
            for (int v = cv - 1; v <= cv + 1; v++) {
                boolean mid = u == cu && v == cv;
                p.set(u, y0 + 1, v, mid ? END_BRICKS : QUARTZ_B, 0);
                p.set(u, y0 + 2, v, mid ? CLAY : TINT, mid ? 6 : 10);
                p.set(u, y0 + 3, v, mid ? WOOL : TINT, mid ? 6 : 2);
                p.set(u, y0 + 4, v, mid ? (rod ? END_ROD : IRON_BARS) : SLAB, mid ? (rod ? 1 : 0) : 7);
            }
    }

    private static final double[] PILLARS = {1.5, 61.5, 120.5, 179.5, 239, 300};

    /** The Overseer's chamber: rings in the floor, six pillars of control, its own minds in their cylinders, its hoard. */
    private static void overseer(P p) {
        if (!p.hit(-15, -15, 15, 15)) return;
        for (int u = p.ua(-14); u <= p.ub(14); u++)
            for (int v = p.va(-14); v <= p.vb(14); v++) {
                double d = Math.sqrt(u * u + v * v);
                if (d >= 15) continue;
                if (Math.abs(d - 7) < 0.6 || Math.abs(d - 11.5) < 0.5) p.set(u, 0, v, OBSIDIAN, 0);
                else if (d < 2.6) p.set(u, 0, v, d < 1 ? TINT : PURPUR, d < 1 ? 2 : 0);
            }
        for (int k = 0; k < PILLARS.length; k++) {
            double t = Math.toRadians(PILLARS[k]);
            int pu = (int) Math.round(Math.cos(t) * 10), pv = (int) Math.round(Math.sin(t) * 10);
            for (int y = 1; y <= 9; y++) p.set(pu, y, pv, y == 5 && k % 2 == 0 ? SEA_LANTERN : (y + k) % 3 == 0 ? OBSIDIAN : END_BRICKS, 0);
            int cu = (int) Math.round(Math.cos(t) * 12.6), cv = (int) Math.round(Math.sin(t) * 12.6);
            if (k != 5) cylinder(p, cu, 0, cv, k == 2);
        }
        // the hoard's dais behind the pillar at 300 degrees
        p.box(5, -12, 7, -11, 0, 0, OBSIDIAN, 0);
    }

    /** The hall of brain cylinders: twelve minds in their jars round a cross of aisles. */
    private static void brains(P p) {
        int cu = BRAINS[0], cv = BRAINS[1];
        if (!p.hit(cu - 12, cv - 12, cu + 12, cv + 12)) return;
        int[][] at = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {8, 4}, {-8, 4}, {8, -4}, {-8, -4}, {4, 8}, {-4, 8}, {4, -8}, {-4, -8}};
        for (int k = 0; k < at.length; k++) cylinder(p, cu + at[k][0], 0, cv + at[k][1], k == 0 || k == 7);
        p.set(cu, 0, cv, OBSIDIAN, 0);
    }

    /** The surgery: tables of quartz with their straps in the three cells, a spawner's cage in the third. */
    private static void surgery(P p) {
        int[][] cells = {CELL1, CELL2};
        for (int[] c : cells) {
            if (!p.hit(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3)) continue;
            p.box(c[0], c[1], c[0], c[1] + 1, 1, 1, QUARTZ_B, 2);
            p.set(c[0], 2, c[1], SKULL, 1);
            p.set(c[0] - 1, 1, c[1], IRON_BARS, 0);
            p.set(c[0] + 1, 1, c[1] + 1, IRON_BARS, 0);
            p.box(c[0], c[1], c[0], c[1], 4, 5, IRON_BARS, 0);
            p.set(c[0] + 2, 1, c[1] - 1, CAULDRON, 2);
        }
        int[] c = CELL3;
        if (p.hit(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3)) {
            for (int u = c[0] - 1; u <= c[0] + 1; u++) for (int v = c[1] - 1; v <= c[1] + 1; v++) if (u != c[0] || v != c[1]) p.box(u, v, u, v, 1, 2, IRON_BARS, 0);
            p.box(c[0] - 1, c[1] - 1, c[0] + 1, c[1] + 1, 3, 3, PURPUR_SLAB, 0);
        }
    }

    /** The crystal gardens: fungi of purple clay on stalks of end stone, and crystals among them. */
    private static void garden(P p, L l) {
        if (!p.hit(GARDEN[0] - 10, GARDEN[1] - 10, GARDEN[0] + 10, GARDEN[1] + 10)) return;
        for (int[] fg : l.fungi) {
            for (int y = 1; y <= fg[2]; y++) p.set(fg[0], y, fg[1], END_BRICKS, 0);
            int cr = fg[3];
            for (int u = fg[0] - cr; u <= fg[0] + cr; u++)
                for (int v = fg[1] - cr; v <= fg[1] + cr; v++) {
                    if ((u - fg[0]) * (u - fg[0]) + (v - fg[1]) * (v - fg[1]) > cr * cr + 1) continue;
                    p.set(u, fg[2] + 1, v, CLAY, fg[4] == 0 ? 10 : fg[4] == 1 ? 2 : 11);
                }
            if (fg[4] == 0) p.set(fg[0], fg[2] + 2, fg[1], CLAY, 2);
        }
        crystal(p, new int[] {GARDEN[0], GARDEN[1] + 6, 6, 0, 0, 0});
        crystal(p, new int[] {GARDEN[0] + 5, GARDEN[1] - 3, 5, 0, 0, 1});
    }

    /** The roost: a spiral ramp of purpur slabs up the inside of its spire, to the nests at the top. */
    private static void roost(P p, Spire sp) {
        if (!p.hit(RX - 9, RZ - 9, RX + 9, RZ + 9)) return;
        for (int u = p.ua(RX - 9); u <= p.ub(RX + 9); u++)
            for (int v = p.va(RZ - 9); v <= p.vb(RZ + 9); v++) {
                double du = u - RX, dv = v - RZ, d = Math.sqrt(du * du + dv * dv);
                double a = Math.atan2(dv, du), c5 = Math.cos(5 * a), s5 = Math.sin(5 * a);
                if (a < 0) a += 2 * Math.PI;
                int k = (int) Math.round(a / (2 * Math.PI) * 20) % 20;
                for (int t = 0; t < 4; t++) {
                    int y2 = k + 20 * t;
                    if (y2 > 79) break;
                    int y = 1 + y2 / 2;
                    double rin = sp.rad[y] * (1 + 0.09 * (c5 * sp.cb[y] - s5 * sp.sb[y])) - 1.6;
                    if (d > rin - 0.1 || d < rin - 2.5) continue;
                    p.set(u, y, v, PURPUR_SLAB, (y2 & 1) == 0 ? 0 : 8);
                }
                double rin40 = sp.rad[40] * (1 + 0.09 * (c5 * sp.cb[40] - s5 * sp.sb[40])) - 1.6;
                if (d < rin40 - 2.4) p.set(u, 40, v, PURPUR, 0);
            }
        // the nests round the top
        p.set(RX + 1, 41, RZ + 1, CLAY, 10);
        p.set(RX - 1, 46, RZ, SEA_LANTERN, 0);
    }

    /** The landing spire: its ladder and floors, the burrow stair, and the landing platform at fifty-two on its corbels. */
    private static void landing(P p) {
        if (!p.hit(LX - 14, LZ - 14, LX + 14, LZ + 14)) return;
        for (int u = p.ua(LX - 13); u <= p.ub(LX + 13); u++)
            for (int v = p.va(LZ - 13); v <= p.vb(LZ + 13); v++) {
                double du = u - LX, dv = v - LZ, d = Math.sqrt(du * du + dv * dv);
                if (d > 13.4) continue;
                double rin = 8.5 * (1 - 0.25 * 13 / 54.0) - 2.2;
                if (d < rin) for (int y = 13; y < LTOP; y += 13) p.set(u, y, v, END_BRICKS, 0);
                if (d < 13.4) p.set(u, LTOP, v, d > 12.4 ? OBSIDIAN : ((int) d) % 3 == 0 ? PURPUR : END_BRICKS, 0);
                if (d > 12.4) {
                    double a = Math.atan2(dv, du);
                    boolean gap = Math.abs(Math.sin(2 * a)) < 0.13;
                    if (!gap) p.set(u, LTOP + 1, v, IRON_BARS, 0);
                }
                // the corbels: eight struts of obsidian from the spire out under the platform
                double a = Math.atan2(dv, du);
                if (Math.abs(Math.sin(4 * a)) < 0.15 && d >= 6 && d <= 12.4) {
                    int y = LTOP - 1 - (int) Math.round((12.4 - d) * 1.1);
                    for (int yy = y; yy < LTOP; yy++) p.set(u, yy, v, OBSIDIAN, 0);
                }
            }
        // the doors from the spire out onto the platform
        for (int y = LTOP + 1; y <= LTOP + 3; y++) {
            for (int k = -1; k <= 1; k++) {
                for (int d = 4; d <= 9; d++) {
                    p.set(LX + k, y, LZ + d, AIR, 0);
                    p.set(LX + k, y, LZ - d, AIR, 0);
                    p.set(LX + d, y, LZ + k, AIR, 0);
                    p.set(LX - d, y, LZ + k, AIR, 0);
                }
            }
        }
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            p.set(LX + (int) Math.round(Math.cos(a) * 12.4), LTOP + 1, LZ + (int) Math.round(Math.sin(a) * 12.4), END_ROD, 1);
        }
        // the pillar and its ladder, the burrow down to the brood
        for (int y = 1; y < LTOP; y++) p.set(LX, y, LZ, OBSIDIAN, 0);
        for (int y = 1; y <= LTOP; y++) p.set(LX + 1, y, LZ, LADDER, p.f.facing(1, 0));
        for (int k = 0; k <= 10; k++) {
            int u = LX - 5 + k, y = -k;
            for (int v = LZ + 2; v <= LZ + 3; v++) {
                for (int yy = -10; yy < y; yy++) p.set(u, yy, v, HIVE, 0);
                p.set(u, y, v, STAIRS, p.f.stairs(-1, 0, false));
                p.clear(u, v, y + 1, y + 3);
            }
        }
        p.box(LX - 4, LZ + 1, LX - 2, LZ + 1, 1, 1, IRON_BARS, 0);
        p.box(LX - 4, LZ + 4, LX - 2, LZ + 4, 1, 1, IRON_BARS, 0);
        // egg pods in the brood
        int[][] eggs = {{-6, 26}, {-5, 35}, {6, 27}, {7, 31}, {-1, 37}};
        for (int[] e : eggs) {
            p.set(e[0], -9, e[1], TINT, 10);
            p.set(e[0], -8, e[1], TINT, 10);
            p.set(e[0], -7, e[1], CLAY, 2);
        }
        p.set(LX, -4, LZ - 3, SEA_LANTERN, 0);
    }

    /** The mouth: the outer tube's opening ringed by curved obsidian mandibles. */
    private static void mouth(P p) {
        if (!p.hit(-8, -44, 8, -36)) return;
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6;
            for (int s = 0; s <= 6; s++) {
                double rr = 5.2 - s * 0.45;
                int u = (int) Math.round(Math.cos(a) * rr), y = 3 + (int) Math.round(Math.sin(a) * rr * 0.95), v = -42 - (s >= 3 ? 1 : 0) + (s >= 5 ? 1 : 0);
                if (y < 1) continue;
                p.set(u, y, v, s >= 5 ? QUARTZ_B : OBSIDIAN, 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ chests, spawners, carvings, darts

    private static void tiles(P p, L l) {
        Frame f = p.f;
        long h = l.h;
        // the Overseer's hoard
        f.chest(6, 1, -11, 0, 1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.7");
        f.chest(5, 1, -12, 0, 1, Sites.DESERT, "trinket:0.35");
        f.chest(7, 1, -12, 0, 1, "minecraft:chests/stronghold_library", "lore:" + Hash.range(Hash.mix(h ^ 1), 0, 99));
        f.sign(5, 3, 8, 0, -1, "IT WANTS\nYOUR MIND\nIN A JAR");
        // the mouth and the antechamber
        f.sign(2, 2, -40, -1, 0, "THE HIVE OF\nTHE MI-GO\nYOUR MIND\nIS WANTED");
        f.chest(4, 1, -31, -1, 0, Sites.CORRIDOR);
        int[][] traps = {{0, 0, -36}, {1, 0, -33}, {-1, 0, -21}, {-22, 0, 12}};
        for (int[] t : traps) { f.dispenser(t[0], t[1], t[2], 0, 0); f.set(t[0], t[1] + 1, t[2], PLATE); }
        // the brains
        f.chest(-30, 1, -14, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 2), 0, 99));
        f.chest(-36, 1, -14, 1, 0, Sites.DUNGEON);
        f.spawner(-19, 1, -14, "SKELETON");
        f.sign(-30, 2, -16, 0, 1, "WE KEEP\nTHEM ALIVE.\nWE KEEP\nTHEM TALKING");
        // the surgery
        f.chest(-23, 1, 16, -1, 0, Sites.SMITH);
        f.spawner(CELL3[0], 1, CELL3[1], "ZOMBIE");
        f.chest(CELL3[0] + 2, 1, CELL3[1] + 2, -1, 0, "minecraft:chests/stronghold_crossing", "trinket:0.2");
        f.sign(CELL1[0], 1, CELL1[1] - 1, 0, -1, "NO PAIN\nIS FELT BY\nA BRAIN IN\nA JAR");
        // the gardens, the roost, the landing spire and its platform, the brood
        f.chest(GARDEN[0] + 3, 1, GARDEN[1] - 5, -1, 0, Sites.JUNGLE, "trinket:0.2");
        f.spawner(GARDEN[0] + 2, 1, GARDEN[1] + 6, "SPIDER");
        f.chest(RX, 41, RZ - 1, 0, 1, "minecraft:chests/end_city_treasure");
        f.chest(LX - 3, 14, LZ - 2, 1, 0, Sites.DUNGEON);
        f.chest(LX + 3, 40, LZ + 2, -1, 0, "minecraft:chests/woodland_mansion", "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99));
        f.chest(LX - 3, LTOP + 1, LZ + 11, 1, 0, "minecraft:chests/end_city_treasure", "trinket:0.3");
        f.sign(LX + 1, LTOP + 2, LZ + 6, -1, 0, "TO YUGGOTH\nON THE RIM\nAND BEYOND");
        f.chest(-6, -9, 31, 1, 0, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h ^ 4), 0, 99));
        f.chest(4, -9, 26, -1, 0, "minecraft:chests/abandoned_mineshaft");
        f.spawner(0, -9, 36, "SILVERFISH");
    }

    // ------------------------------------------------------------------------------------------------ the painter

    /** A chunk-clipped painter in the structure's local frame (u across, v front to back, y above the base). */
    private static final class P {
        final Plans.GreatSite s;
        final Canvas c;
        final Frame f;
        final int u0, u1, v0, v1;
        private final int[] gnd = new int[256];

        P(Plans.GreatSite s, Canvas c) {
            this.s = s;
            this.c = c;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int ax = c.x0 - s.x, az = c.z0 - s.z, bx = ax + 15, bz = az + 15;
            switch (s.rot & 3) {
                case 1: u0 = az; u1 = bz; v0 = -bx; v1 = -ax; break;
                case 2: u0 = -bx; u1 = -ax; v0 = -bz; v1 = -az; break;
                case 3: u0 = -bz; u1 = -az; v0 = ax; v1 = bx; break;
                default: u0 = ax; u1 = bx; v0 = az; v1 = bz; break;
            }
            Arrays.fill(gnd, Integer.MIN_VALUE);
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }
        int ua(int a) { return Math.max(a, u0); }
        int ub(int b) { return Math.min(b, u1); }
        int va(int a) { return Math.max(a, v0); }
        int vb(int b) { return Math.min(b, v1); }

        /** The land's height above the base at a column of this chunk (from the site's plan, never from the canvas). */
        int ground(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v), i = (wx - c.x0) << 4 | (wz - c.z0);
            if (wx < c.x0 || wx > c.x0 + 15 || wz < c.z0 || wz > c.z0 + 15) return s.surface(wx, wz) - s.base;
            int g = gnd[i];
            if (g == Integer.MIN_VALUE) gnd[i] = g = s.surface(wx, wz) - s.base;
            return g;
        }

        void set(int u, int y, int v, int id, int meta) {
            if (id >= 0) { f.set(u, y, v, id, meta); return; }
            double r;
            switch (id) {
                case MAS: f.masonry(u, y, v); break;
                case RUB: f.rubble(u, y, v); break;
                case CRUST:
                    r = f.roll(u, y, v, 71);
                    if (r < 0.28) f.set(u, y, v, END_STONE);
                    else if (r < 0.46) f.set(u, y, v, CLAY, 10);
                    else if (r < 0.54) f.set(u, y, v, OBSIDIAN);
                    else if (r < 0.62) f.set(u, y, v, CLAY, 2);
                    else if (r < 0.80) f.set(u, y, v, STONE, 5);
                    else if (r < 0.90) f.set(u, y, v, CLAY, 11);
                    else f.set(u, y, v, PRISMARINE, 2);
                    break;
                case HIVE:
                    r = f.roll(u, y, v, 72);
                    if (r < 0.30) f.set(u, y, v, PURPUR);
                    else if (r < 0.55) f.set(u, y, v, CLAY, 10);
                    else if (r < 0.68) f.set(u, y, v, END_STONE);
                    else if (r < 0.78) f.set(u, y, v, CLAY, 2);
                    else if (r < 0.88) f.set(u, y, v, OBSIDIAN);
                    else f.set(u, y, v, PRISMARINE, 2);
                    break;
                case PURP:
                    r = f.roll(u, y, v, 73);
                    if (r < 0.45) f.set(u, y, v, PURPUR);
                    else if (r < 0.7) f.set(u, y, v, END_BRICKS);
                    else if (r < 0.85) f.set(u, y, v, CLAY, 10);
                    else f.set(u, y, v, OBSIDIAN);
                    break;
                case BAND: {
                    // meta carries the height: the spires' bands of colour climb in threes
                    int band = Math.floorMod(meta / 3 + (int) (f.roll(u, 0, v, 74) * 1.6), 5);
                    if (band == 0) f.set(u, y, v, PURPUR_PILLAR);
                    else if (band == 1) f.set(u, y, v, END_BRICKS);
                    else if (band == 2) f.set(u, y, v, CLAY, 10);
                    else if (band == 3) f.set(u, y, v, PURPUR);
                    else f.set(u, y, v, CLAY, 2);
                    break;
                }
                default: break;
            }
        }

        void box(int a0, int b0, int a1, int b1, int y0, int y1, int id, int meta) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, meta);
        }

        void clear(int u, int v, int y0, int y1) { if (in(u, v)) for (int y = y0; y <= y1; y++) f.set(u, y, v, AIR, 0); }
    }
}
