package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Infernal Colosseum (owner, 2026-10-04: "Add 10 new structures to the Nether ... I want them to be huge
 * structures"): an elliptical amphitheatre 220 blocks by 170 and forty high on a stepped base, in a cavern of scorched
 * rock. Built column by column from its ellipse (the cavea's coordinate is solved for every column, its bays measured
 * along the outer wall):
 * <ul>
 *   <li>outside, four tiers of eighty arches of red nether brick between quartz half-columns, magma lamps in their
 *       keystones and statues in the upper arcades, the attic with its velarium masts; four grand gates (the Triumphal
 *       Gate with its avenue of statues, the Gate of Death, the Gladiators' Gate, the Emperor's Gate), barred and
 *       trapped;</li>
 *   <li>within, three stacked ambulatories behind the arches and four ladder wells up to the top gallery under the striped
 *       velarium; the cavea of red and black rows cut by quartz aisles; the podium's marble terrace, the corridor behind
 *       it and the beast doors; the Emperor's Box over the arena, his treasury beneath it behind a grate that opens only
 *       when the Undying Gladiator falls;</li>
 *   <li>the arena, ninety by sixty of soul sand and gravel: pillars hung with chains and statues for cover, flame vents,
 *       pits with ladders down into the hypogeum (the gladiators' cells above, the beast pens below, the tunnel from the
 *       ludus); the Gladiator fights in the middle, in sight of every gate;</li>
 *   <li>outer works: the ludus (the gladiators' barracks round its practice ring), the forge and the spoliarium by the
 *       Gate of Death, the Emperor's Way, a training yard, the beast stables and the gladiators' graves.</li>
 * </ul>
 */
final class ColossusColosseum extends ColossusDesign {
    static final double A = 110, B = 85, A0 = 46, B0 = 31, DA = A - A0, DB = B - B0;   // the outer and the arena's half axes
    static final double HA = 44, HB = 29;                                             // the hypogeum's
    static final int NB = 80, NT = 7200;                                              // bays round; the arc table's steps
    static final int G = 5;                                                           // the ground floor above s.y
    static final int[] CROSS = {-30, -15, 0, 15, 30};                                 // the hypogeum's cross corridors
    static final String LOOT = "jaspr:colossus/colosseum";

    // ---- palette --------------------------------------------------------------------------------------------------------
    private static final int BRICK = b(112), RED_NB = b(215), FENCE_NB = b(113), QUARTZ = b(155, 0), CHISELED = b(155, 1), QP_Y = b(155, 2);
    private static final int RED_T = b(159, 14), ORANGE_T = b(159, 1), BLACK_T = b(159, 15), WHITE_T = b(159, 0), BROWN_T = b(159, 12);
    private static final int YELLOW_C = b(251, 4), WHITE_C = b(251, 0), RED_C = b(251, 14);
    private static final int SOUL = b(88), RACK = b(87), FIRE = b(51), GLOW = b(89), MAGMA = b(213), GRAVEL = b(13), LAVA = b(11);
    private static final int IRON = b(101), WEB = b(30), CAULDRON = b(118), JACK = b(91), HAY = b(170), PUMPKIN = b(86), BONE_Y = b(216, 0);
    private static final int NBSLAB = b(44, 6), NBSLAB_TOP = b(44, 14), QSLAB = b(44, 7), STONE_SLAB = b(44, 0), CHISELED_SB = b(98, 3);
    private static final int PLANK = b(5, 5), FENCE = b(191), LOG = b(162, 1), WOOL_RED = b(35, 14), WOOL_WHITE = b(35, 0), CARPET_RED = b(171, 14);
    private static final int SEAL = b(101);              // the Emperor's treasury's grate
    private static final int TRAPFLOOR = b(44, 14);      // the hypogeum's floors that give way: nether brick top slabs
    private static final Draw.Mat FACADE = Draw.mix(RED_NB, RED_T, 0.22, 6101), ATTIC = Draw.mix(RED_NB, BRICK, 0.35, 6102);

    // ---- the plan -------------------------------------------------------------------------------------------------------
    static final class Plan {
        int rot;
        final double[] arc = new double[NT + 1];      // the outer ellipse's arc length from its +u end, at NT steps of the angle
        double perim, p0, p1;
        final List<int[]> wells = new ArrayList<>();  // the ladder wells: centre u, v; the doorways' direction du, dv
        final List<int[]> traps = new ArrayList<>();  // kind, box (local), emitter box or gun, block; frame turn
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    static double sq(double v) { return v * v; }
    static double perimeter(double a, double b) { return Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b))); }
    static double ell(double u, double v, double s) { double a = A0 + DA * s, b = B0 + DB * s; return sq(u / a) + sq(v / b) - 1; }

    /** The cavea's coordinate of a column: 0 on the arena's edge, 1 on the outer face (more outside); -1 in the arena. */
    static double sOf(double u, double v) {
        if (sq(u / A0) + sq(v / B0) <= 1) return -1;
        double lo = 0, hi = 1.6;
        if (ell(u, v, hi) > 0) return 9;
        for (int i = 0; i < 24; i++) { double m = (lo + hi) * 0.5; if (ell(u, v, m) > 0) lo = m; else hi = m; }
        return (lo + hi) * 0.5;
    }

    /** The bay coordinate (0..NB) of a parametric angle, measured along the outer wall. */
    static double bay(Plan p, double th) {
        double t = th < 0 ? th + 2 * Math.PI : th, fi = t / (2 * Math.PI) * NT;
        int i = Math.min((int) fi, NT - 1);
        return (p.arc[i] + (p.arc[i + 1] - p.arc[i]) * (fi - i)) / p.perim * NB;
    }

    /** The parametric angle of a bay coordinate. */
    static double angle(Plan p, double c) {
        double want = c / NB * p.perim;
        int lo = 0, hi = NT;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (p.arc[m] < want) lo = m; else hi = m; }
        return 2 * Math.PI * hi / NT;
    }

    static double bayWidth(Plan p, double s) { return (p.p0 + (p.p1 - p.p0) * s) / NB; }

    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        for (int i = 1; i <= NT; i++) {
            double t = 2 * Math.PI * (i - 0.5) / NT;
            p.arc[i] = p.arc[i - 1] + Math.hypot(A * Math.sin(t), B * Math.cos(t)) * (2 * Math.PI / NT);
        }
        p.perim = p.arc[NT];
        p.p0 = perimeter(A0, B0); p.p1 = perimeter(A, B);
        // the ladder wells on the four diagonals, behind the ambulatories; their doorways face out
        for (int k = 10; k < NB; k += 20) {
            double th = angle(p, k), dt = Math.hypot(DA * Math.cos(th), DB * Math.sin(th)), sc = 1 - 10.5 / dt;
            int cu = (int) Math.round((A0 + DA * sc) * Math.cos(th)), cv = (int) Math.round((B0 + DB * sc) * Math.sin(th));
            double gu = Math.cos(th) / A, gv = Math.sin(th) / B;
            int du = Math.abs(gu) >= Math.abs(gv) ? (int) Math.signum(gu) : 0, dv = du == 0 ? (int) Math.signum(gv) : 0;
            p.wells.add(new int[]{cu, cv, du, dv});
        }
        traps(p, s);
        p.garrisons = complete(garrisons(p, s));
        return p;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        // the amphitheatre and its plaza, column by column
        if (touches(f, -132, -106, 132, 106)) cols(f, -132, -106, 132, 106, (u, v, x, z) -> column(f, s, p, u, v, x, z));
        hypogeumWorks(f, s, p);
        for (int dir = 0; dir < 4; dir++) gate(f, s, p, dir);
        for (int[] w : p.wells) well(f, s, w);
        emperor(f, s, p);
        arenaWorks(f, s, p);
        avenue(f, s, p);
        ludus(f, s, p);
        forge(f, s, p);
        emperorsWay(f, s, p);
        yards(f, s, p);
        trapMarks(f, s, p);
        f.point(0, s.y + G + 1, 0, "lord:" + s.kind.lord);
        garrisons(f, p.garrisons);
    }

    static boolean touches(Draw.Frame f, int u0, int v0, int u1, int v1) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        return f.d.touches(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    /** One column of the amphitheatre (or its base and plaza), from the foundation to the masts. */
    private void column(Draw.Frame f, Colossi.Site s, Plan p, int u, int v, int x, int z) {
        Draw d = f.d;
        int y0 = s.y, gf = y0 + G;
        if (sq(u / A0) + sq(v / B0) <= 1) { arena(f, s, u, v, x, z); return; }
        double sc = sOf(u, v);
        if (sc > 1.5) return;
        double a = A0 + DA * sc, bb = B0 + DB * sc, th = Math.atan2(v / bb, u / a);
        double dt = Math.hypot(DA * Math.cos(th), DB * Math.sin(th)), depth = (1 - sc) * dt;
        if (depth < -15) return;
        double c = bay(p, th), cr = Math.rint(c);
        int k = Math.floorMod((int) cr, NB);
        double ob = (c - cr) * bayWidth(p, sc), aob = Math.abs(ob);
        int tile = ((x + z) & 1) == 0 ? RED_NB : BRICK;
        if (depth < 0) {
            if (depth < -6) {                 // the plaza round the base
                for (int y = y0 - 3; y < y0; y++) d.set(x, y, z, BRICK);
                double q = Draw.rnd(x, 3, z, s.salt + 5);
                d.set(x, y0, z, q < 0.45 ? GRAVEL : q < 0.75 ? BRICK : q < 0.9 ? RED_NB : MAGMA);
                for (int y = y0 + 1; y <= y0 + 5; y++) d.set(x, y, z, 0);
                return;
            }
            int top = gf - (int) Math.floor(-depth);       // the base's steps
            for (int y = y0 - 3; y <= top; y++) d.set(x, y, z, y == top ? tile : BRICK);
            for (int y = top + 1; y <= top + 5; y++) d.set(x, y, z, 0);
            if (depth >= -1) {                               // the facade's half-columns and cornices stand out on the top step
                boolean pier = aob > 2.4;
                for (int t = 0; t < 4; t++) {
                    int yb = gf + 10 * t;
                    if (pier) for (int y = yb + 1; y <= yb + 9; y++) d.set(x, y, z, t == 3 ? BRICK : y == yb + 9 ? CHISELED : QP_Y);
                    d.set(x, yb + 10, z, BRICK);
                }
            }
            return;
        }
        for (int y = y0 - 3; y <= gf; y++) d.set(x, y, z, BRICK);
        if (depth < 2) {                                     // the facade: three arcades and the attic
            for (int t = 0; t < 4; t++) {
                int yb = gf + 10 * t;
                for (int y = yb + 1; y <= yb + 9; y++) {
                    int hy = y - yb, val = t == 3 ? ATTIC.at(x, y, z) : FACADE.at(x, y, z);
                    if (t < 3 && aob <= 2.4 && hy <= 6 + Math.sqrt(Math.max(0, 6.25 - ob * ob))) {
                        val = 0;
                        if (t > 0 && hy == 1 && depth >= 1) val = FENCE_NB;
                        if (t > 0 && (k & 1) == 1 && aob < 0.5 && depth < 1 && hy <= 5) val = hy == 1 ? CHISELED : hy == 5 ? WHITE_C : QUARTZ;
                    } else if (t < 3 && aob < 0.5 && hy == 9) val = MAGMA;
                    else if (t == 3 && (k & 1) == 0 && aob <= 1.0 && hy >= 3 && hy <= 5) val = depth < 1 ? IRON : 0;
                    d.set(x, y, z, val);
                }
                d.set(x, yb + 10, z, BRICK);
            }
            if (Math.floorMod((int) Math.floor((c - cr + 0.5) * 8), 2) == 0) d.set(x, gf + 41, z, RED_NB);
            if (k % 4 == 2 && aob < 0.5 && depth < 1) {   // a velarium mast
                int mt = Math.min(gf + 50, s.ceilAt(x, z) - 2);
                for (int y = gf + 41; y <= mt; y++) d.set(x, y, z, y == mt ? FENCE : (y - gf == 45 || y - gf == 46) ? WOOL_RED : LOG);
            }
            return;
        }
        if (depth < 7) {                                     // the ambulatories, three stacked, and the top gallery's back
            for (int t = 0; t < 4; t++) {
                int yb = gf + 10 * t;
                boolean lamp = t > 0 && (k & 1) == 0 && aob < 0.5 && depth >= 4 && depth < 5;
                d.set(x, yb, z, lamp ? GLOW : tile);
                for (int y = yb + 1; y <= yb + (t == 3 ? 7 : 9); y++) d.set(x, y, z, 0);
            }
            d.set(x, gf + 38, z, (k & 1) == 0 && aob < 0.5 && depth >= 4 && depth < 5 ? GLOW : BRICK);
            awning(d, s, x, z, k, gf);
            return;
        }
        if (depth < 14) {                                    // the substructure under the top gallery; its colonnade
            for (int y = gf + 1; y <= gf + 29; y++) d.set(x, y, z, BRICK);
            d.set(x, gf + 30, z, tile);
            boolean col = depth >= 12.5 && aob < 0.6;
            for (int y = gf + 31; y <= gf + 37; y++) d.set(x, y, z, col ? QP_Y : 0);
            d.set(x, gf + 38, z, col ? CHISELED : BRICK);
            awning(d, s, x, z, k, gf);
            return;
        }
        boolean beastDoor = k % 20 == 10 && aob <= 1.0 && depth >= dt - 6;
        if (depth < dt - 4) {                                // the cavea: its rows, its aisles; the corridor under the lowest
            int h = gf + 6 + (int) Math.floor(24 * (dt - 4 - depth) / (dt - 18));
            for (int y = gf + 1; y < h; y++) d.set(x, y, z, BRICK);
            boolean aisle = k % 5 == 2 && aob <= 0.9;
            int dir = Math.abs(u / (a * a)) >= Math.abs(v / (bb * bb)) ? (u > 0 ? 0 : 1) : (v > 0 ? 2 : 3);
            d.set(x, h, z, aisle ? f.stair(156, dir, false) : ((h - gf) & 1) == 0 ? RED_T : BRICK);
            if (depth < 18) awning(d, s, x, z, k, gf);
            if (depth >= dt - 12 && depth < dt - 6) {
                d.set(x, gf, z, tile);
                for (int y = gf + 1; y <= gf + 5; y++) d.set(x, y, z, 0);
                if (k % 4 == 0 && aob < 0.5) d.set(x, gf + 6, z, GLOW);
            }
            if (beastDoor) for (int y = gf + 1; y <= gf + 3; y++) d.set(x, y, z, 0);
            return;
        }
        boolean wall = depth >= dt - 1.5;                    // the podium: its marble terrace, its wall and balustrade
        for (int y = gf + 1; y <= gf + 5; y++) d.set(x, y, z, beastDoor && y <= gf + 3 ? 0 : wall ? RED_NB : BRICK);
        d.set(x, gf + 6, z, wall ? CHISELED : QUARTZ);
        if (wall) d.set(x, gf + 7, z, FENCE_NB);
        if (beastDoor && wall && aob < 0.5) d.set(x, gf + 4, z, MAGMA);
    }

    /** Whether (u, |v|) lies in a gladiators' cell of the hypogeum: a cell is carved only where it fits whole (so has its doors). */
    static boolean roomAt(int u, int av) {
        if (av < 3 || av % 6 == 2) return false;
        int vfar = 6 * ((av - 3) / 6) + 7;
        for (int i = 0; i + 1 < CROSS.length; i++) {
            int lo = CROSS[i] + 3, hi = CROSS[i + 1] - 3;
            if (u >= lo && u <= hi) return sq(Math.max(Math.abs(lo), Math.abs(hi)) / HA) + sq(vfar / HB) < 0.74;
        }
        return false;
    }

    /** The velarium's remnant: striped canvas over the top gallery and the highest rows, torn in places. */
    private static void awning(Draw d, Colossi.Site s, int x, int z, int k, int gf) {
        if (Draw.rnd(k, 0, 0, s.salt + 7) < 0.14) return;
        d.set(x, gf + 39, z, (k & 1) == 0 ? WOOL_RED : WOOL_WHITE);
    }

    /** A column of the arena: its floor of sand and gravel over the hypogeum's two levels. */
    private void arena(Draw.Frame f, Colossi.Site s, int u, int v, int x, int z) {
        Draw d = f.d;
        int y0 = s.y, gf = y0 + G, low = y0 - 8, mid = y0 - 2, top = y0 + 4;
        double qa = sq(u / A0) + sq(v / B0), qh = sq(u / HA) + sq(v / HB);
        double r = Draw.rnd(x, 1, z, s.salt + 11), n = Draw.noise(x, z, 6, s.salt + 13);
        int floor = qa < 0.008 ? RED_NB : (qa > 0.31 && qa < 0.34) ? GRAVEL : n > 0.3 ? GRAVEL : r < 0.04 ? BROWN_T : r < 0.075 ? RED_T : SOUL;
        for (int y = low; y < top; y++) d.set(x, y, z, BRICK);
        d.set(x, top, z, BRICK);
        d.set(x, gf, z, floor);
        if (qh > 1) return;
        int au = Math.abs(u), av = Math.abs(v), dc = 99, cc = 0;
        for (int cx : CROSS) if (Math.abs(u - cx) < dc) { dc = Math.abs(u - cx); cc = cx; }
        // the upper level: the gladiators' cells along the corridors, the ring corridor, the lifts up to the pits
        boolean inner = qh < 0.93, ring = qh >= 0.78 && inner;
        boolean corridor = inner && (av <= 1 || dc <= 1);
        boolean room = roomAt(u, av);
        boolean door = dc == 2 && av % 6 == 5 && (roomAt(u + 1, av) || roomAt(u - 1, av));
        boolean lift = au >= 21 && au <= 23 && av >= 9 && av <= 12;
        if (corridor || ring || room || door || lift) {
            boolean lampU = corridor && av == 0 && Math.floorMod(u, 8) == 4;
            d.set(x, mid, z, room ? PLANK : ((x + z) & 1) == 0 ? BRICK : RED_NB);
            for (int y = mid + 1; y < top; y++) d.set(x, y, z, 0);
            if (lampU || (ring && Math.floorMod(u + v, 11) == 0)) d.set(x, top, z, GLOW);
            if (room && Draw.rnd(x, 2, z, s.salt + 15) < 0.05) d.set(x, mid + 1, z, Draw.rnd(x, 3, z, s.salt + 16) < 0.5 ? HAY : WEB);
        }
        if (lift) {
            d.set(x, top, z, 0); d.set(x, gf, z, 0);
            if (av == 9 && au == 22) for (int y = mid + 1; y <= gf; y++) d.set(x, y, z, b(65, f.facing(v > 0 ? Draw.SOUTH : Draw.NORTH)));
        }
        // the lower level: the beast pens either side of the long corridor, their barred fronts
        boolean lc = qh < 0.9 && av <= 2;
        boolean pen = qh < 0.85 && av >= 4 && Math.floorMod(u, 9) != 0;
        boolean front = qh < 0.85 && av == 3;
        boolean tunnel = u >= -1 && u <= 1 && v < 0;
        if (lc || pen || front || tunnel) {
            d.set(x, low, z, pen ? (r < 0.5 ? SOUL : GRAVEL) : BRICK);
            int fill = front && !tunnel && Math.floorMod(u, 9) != 4 && Math.floorMod(u, 9) != 5 ? IRON : 0;
            for (int y = low + 1; y < mid; y++) d.set(x, y, z, fill);
            if (pen && !tunnel && r > 0.97) d.set(x, low + 1, z, r > 0.985 ? HAY : BONE_Y);
            if (pen && Draw.rnd(x, 4, z, s.salt + 17) < 0.03) d.set(x, low, z, MAGMA);
            if (lc && av == 0 && Math.floorMod(u, 9) == 4) d.set(x, mid, z, GLOW);
        }
    }

    // ---- the hypogeum's stairs, the floors that give way, its keepers' things --------------------------------------------
    private void hypogeumWorks(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -46, -32, 46, 32)) return;
        int y0 = s.y, low = y0 - 8, mid = y0 - 2, top = y0 + 4;
        for (int sg = -1; sg <= 1; sg += 2)                 // two stairs from the cells down to the pens
            for (int k = 0; k <= 5; k++) {
                int u = sg * (4 + k), y = mid - k;
                for (int v = 0; v <= 1; v++) {
                    f.set(u, y, v, f.stair(114, sg > 0 ? 1 : 0, false));
                    f.box(u, y + 1, v, u, top - 1, v, Draw.AIR);
                }
            }
        for (int[] q : new int[][]{{15, 18}, {-15, -18}, {30, -15}}) f.box(q[0] - 1, mid, q[1] - 1, q[0] + 1, mid, q[1] + 1, Draw.of(TRAPFLOOR));
        f.chest(12, mid + 1, 4, Draw.WEST, LOOT + "_rich");          // the armoury of the cells
        f.box(10, mid + 1, 3, 10, mid + 3, 3, Draw.of(IRON)); f.box(8, mid + 1, 3, 8, mid + 3, 3, Draw.of(IRON));
        f.chest(-12, mid + 1, 10, Draw.EAST, LOOT);
        f.chest(27, mid + 1, -10, Draw.WEST, LOOT + "!trap");
        f.spawner(-13, low + 1, 15, "hellhound");
        f.wallSign(-20, mid + 3, 1, Draw.NORTH, "THE HYPOGEUM", "Cells above,", "beasts below", "");
    }

    // ---- the four grand gates --------------------------------------------------------------------------------------------
    /** A gate along an axis (0 +u, 1 +v, 2 -u, 3 -v), drawn in its own frame: al runs out from the arena, lat across. */
    private void gate(Draw.Frame f, Colossi.Site s, Plan p, int dir) {
        Draw.Frame g = new Draw.Frame(f.d, f.ox, f.oz, (p.rot + dir) & 3);
        int ein = (dir & 1) == 0 ? (int) A0 : (int) B0, eout = (dir & 1) == 0 ? (int) A : (int) B, other = (dir & 1) == 0 ? (int) B0 : (int) A0;
        if (!touches(g, ein - 5, -12, eout + 4, 12)) return;
        int gf = s.y + G, mid = s.y - 2;
        boolean emperor = dir == 1;
        // the tunnel, a barrel vault nine wide: lower under the ambulatories' upper floors and under the Emperor's Box
        for (int al = ein - 1; al <= eout; al++) {
            int depth = eout - al, cap = depth >= 2 && depth < 7 ? gf + 9 : emperor && al <= ein + 14 ? gf + 6 : gf + 12;
            for (int lat = -4; lat <= 4; lat++) {
                int topY = Math.min(cap, gf + 8 + (int) Math.round(Math.sqrt(20.25 - lat * lat)));
                g.box(al, gf + 1, lat, al, topY, lat, Draw.AIR);
                g.set(al, gf, lat, ((al + lat) & 1) == 0 ? RED_NB : BRICK);
            }
            if (Math.floorMod(al, 6) == 0 && depth >= 7) g.set(al, cap + 1, 0, GLOW);
            if (Math.floorMod(al, 6) == 3 && depth >= 7 && depth < eout - ein - 6) for (int sg = -1; sg <= 1; sg += 2) g.set(al, gf, sg * 4, MAGMA);
        }
        // the outer portal: twin columns, the lintel, the attic with the gate's name; the portcullis half raised
        int o = eout + 1;
        for (int sg = -1; sg <= 1; sg += 2) {
            g.box(o, gf + 1, sg * 5, o, gf + 15, sg * 6, Draw.of(QP_Y));
            g.set(o, gf + 15, sg * 5, CHISELED); g.set(o, gf + 15, sg * 6, CHISELED);
            g.box(o, gf + 16, sg * 7, o, gf + 19, sg * 7, Draw.of(BRICK));
        }
        g.box(o, gf + 13, -4, o, gf + 14, 4, Draw.of(CHISELED));
        g.box(o, gf + 16, -6, o, gf + 19, 6, Draw.of(RED_NB));
        g.box(o, gf + 20, -7, o, gf + 20, 7, Draw.of(BRICK));
        g.set(o, gf + 21, 0, MAGMA); g.set(o, gf + 22, 0, emperor ? YELLOW_C : RED_NB);
        for (int lat = -4; lat <= 4; lat++) g.box(eout, gf + 10, lat, eout, Math.min(gf + 12, gf + 8 + (int) Math.round(Math.sqrt(20.25 - lat * lat))), lat, Draw.of(IRON));
        String[][] names = {{"GATE OF DEATH", "The fallen are", "carried out", "this way"}, {"EMPEROR'S GATE", "Bow to the box", "above the sand", ""},
            {"THE INFERNAL", "COLOSSEUM", "Fight, and the", "crowd will roar"}, {"GLADIATORS'", "GATE", "The hypogeum", "lies below"}};
        g.wallSign(o + 1, gf + 17, 0, Draw.EAST, names[dir]);
        // the arena's portal: columns, a lintel and the bars of the raised portcullis (the Box itself frames the Emperor's)
        if (!emperor) {
            for (int sg = -1; sg <= 1; sg += 2) g.box(ein - 1, gf + 1, sg * 5, ein - 1, gf + 13, sg * 6, Draw.of(QP_Y));
            g.box(ein - 1, gf + 13, -6, ein - 1, gf + 14, 6, Draw.of(CHISELED));
            for (int lat = -4; lat <= 4; lat++) g.box(ein - 1, gf + 10, lat, ein - 1, Math.min(gf + 12, gf + 8 + (int) Math.round(Math.sqrt(20.25 - lat * lat))), lat, Draw.of(IRON));
            // ladders up the podium wall beside the gate, the balustrade opened over them
            for (int sg = -1; sg <= 1; sg += 2) {
                int lat = sg * 8, wallAl = ein;
                for (int al = ein - 6; al <= ein + 2; al++) if (sq(al / (double) ein) + sq(lat / (double) other) > 1) { wallAl = al; break; }
                for (int y = gf + 1; y <= gf + 6; y++) g.set(wallAl - 1, y, lat, b(65, g.facing(Draw.WEST)));
                g.set(wallAl, gf + 7, lat, 0);
            }
        } else {
            for (int lat = -4; lat <= 4; lat++) g.box(ein - 1, gf + 5, lat, ein - 1, gf + 6, lat, Draw.of(IRON));
        }
        // a niche with a chest in the long dark of the tunnel
        int nal = ein + 22;
        g.box(nal, gf + 1, 5, nal + 1, gf + 3, 5, Draw.AIR);
        if (dir == 2 || dir == 0) g.chest(nal, gf + 1, 5, Draw.NORTH, LOOT);
        // the Gladiators' Gate: a stair down beside the tunnel into the hypogeum's ring corridor
        if (dir == 3) {
            for (int k = 1; k <= 7; k++) {
                int al = 41 - k, y = gf - k;
                for (int lat = 2; lat <= 4; lat++) {
                    g.set(al, y, lat, g.stair(114, 0, false));
                    g.box(al, y + 1, lat, al, gf + 4, lat, Draw.AIR);
                }
            }
            g.box(27, mid + 1, 2, 33, mid + 4, 4, Draw.AIR);
            g.box(27, mid, 2, 33, mid, 4, Draw.of(BRICK));
            g.set(34, mid + 4, 3, GLOW);
            g.box(34, gf + 1, 1, 41, gf + 1, 1, Draw.of(FENCE_NB));
        }
    }

    // ---- the ladder wells from the ground to the top gallery ---------------------------------------------------------------
    private void well(Draw.Frame f, Colossi.Site s, int[] w) {
        int cu = w[0], cv = w[1], du = w[2], dv = w[3], gf = s.y + G, pu = dv, pv = du;
        if (!touches(f, cu - 11, cv - 11, cu + 11, cv + 11)) return;
        f.box(cu - 2, gf, cv - 2, cu + 2, gf + 30, cv + 2, Draw.of(BRICK));
        f.box(cu - 1, gf + 1, cv - 1, cu + 1, gf + 30, cv + 1, Draw.AIR);
        for (int t = 0; t < 3; t++) {
            int yb = gf + 10 * t;
            for (int k = 2; k <= 9; k++) for (int l = -1; l <= 1; l++)
                f.box(cu + du * k + pu * l, yb + 1, cv + dv * k + pv * l, cu + du * k + pu * l, yb + 3, cv + dv * k + pv * l, Draw.AIR);
        }
        int face = pu > 0 ? Draw.WEST : pu < 0 ? Draw.EAST : pv > 0 ? Draw.NORTH : Draw.SOUTH;
        for (int y = gf + 1; y <= gf + 30; y++) f.set(cu + du + pu, y, cv + dv + pv, b(65, f.facing(face)));
        f.set(cu - du, gf + 30, cv - dv, GLOW);
        int cf = du > 0 ? Draw.EAST : du < 0 ? Draw.WEST : dv > 0 ? Draw.SOUTH : Draw.NORTH;
        f.chest(cu - du - pu, gf + 1, cv - dv - pv, cf, LOOT);
    }

    // ---- the Emperor's Box and his treasury beneath it ------------------------------------------------------------------
    private void emperor(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -18, 27, 20, 48)) return;
        int gf = s.y + G, fl = gf + 11;
        f.box(-14, gf + 7, 31, 14, fl, 45, Draw.of(BRICK));
        f.box(-14, fl, 31, 14, fl, 45, (x, y, z) -> ((x + z) & 1) == 0 ? QUARTZ : CHISELED);
        f.box(-14, fl + 1, 32, 14, fl + 8, 45, Draw.AIR);
        f.box(-14, fl + 1, 31, -14, fl + 8, 45, ATTIC); f.box(14, fl + 1, 31, 14, fl + 8, 45, ATTIC); f.box(-14, fl + 1, 45, 14, fl + 8, 45, ATTIC);
        f.box(-13, fl + 1, 31, 13, fl + 1, 31, Draw.of(FENCE_NB));
        for (int u : new int[]{-14, -7, 7, 14}) { f.box(u, fl + 1, 31, u, fl + 8, 31, Draw.of(QP_Y)); f.set(u, fl + 8, 31, CHISELED); }
        for (int sg = -1; sg <= 1; sg += 2) f.box(sg * 14, fl + 3, 36, sg * 14, fl + 5, 40, Draw.of(IRON));
        // the roof, its gilded pediment over the sand
        f.box(-15, fl + 9, 30, 15, fl + 9, 46, Draw.of(RED_NB));
        f.box(-15, fl + 9, 30, 15, fl + 9, 30, Draw.of(YELLOW_C));
        for (int k = 0; k < 7; k++) f.box(-14 + 2 * k, fl + 10 + k, 30, 14 - 2 * k, fl + 10 + k, 32, Draw.of(k == 6 ? YELLOW_C : k % 2 == 0 ? RED_NB : CHISELED));
        f.box(-14, fl + 10, 33, 14, fl + 10, 46, Draw.of(NBSLAB));
        f.set(-6, fl + 8, 38, GLOW); f.set(6, fl + 8, 38, GLOW); f.set(0, fl + 8, 42, GLOW);
        // the throne and its carpet; braziers; the stair out to the rows behind
        f.box(-1, fl + 1, 32, 1, fl + 1, 40, Draw.of(CARPET_RED));
        f.set(0, fl + 1, 42, f.stair(156, 2, false));
        f.set(-1, fl + 1, 42, CHISELED); f.set(1, fl + 1, 42, CHISELED);
        f.box(0, fl + 1, 43, 0, fl + 4, 43, Draw.of(QP_Y)); f.set(0, fl + 5, 43, YELLOW_C);
        for (int sg = -1; sg <= 1; sg += 2) { f.set(sg * 10, fl + 1, 34, QUARTZ); f.set(sg * 10, fl + 2, 34, RACK); f.set(sg * 10, fl + 3, 34, FIRE); }
        for (int u = 6; u <= 8; u++) { f.set(u, fl + 1, 44, f.stair(156, 2, false)); f.set(u, fl + 2, 45, f.stair(156, 2, false)); f.box(u, fl + 3, 45, u, fl + 5, 45, Draw.AIR); }
        f.chest(-12, fl + 1, 44, Draw.NORTH, LOOT + "_rich");
        f.wallSign(3, fl + 4, 44, Draw.NORTH, "THE EMPEROR'S", "BOX", "His treasury", "lies beneath");
        // the treasury, east of the Emperor's Gate, its grate on the sand
        f.box(6, gf, 31, 17, gf + 6, 38, Draw.of(BRICK));
        f.box(7, gf + 1, 32, 16, gf + 5, 37, Draw.AIR);
        f.box(7, gf, 32, 16, gf, 37, Draw.of(CHISELED));
        f.box(10, gf + 1, 31, 12, gf + 3, 31, SEAL);
        f.set(11, gf + 6, 34, GLOW);
        f.set(7, gf + 1, 32, YELLOW_C); f.set(16, gf + 1, 32, YELLOW_C);
        for (int u = 8; u <= 14; u += 2) f.chest(u, gf + 1, 37, Draw.NORTH, LOOT + "_vault");
        f.chest(16, gf + 1, 34, Draw.WEST, LOOT + "_rich");
        f.wallSign(13, gf + 3, 30, Draw.NORTH, "THE EMPEROR'S", "TREASURY", "Opens when the", "Undying falls");
    }

    // ---- the sand: pillars hung with chains, statues, the flame vents ---------------------------------------------------
    private void arenaWorks(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -48, -33, 48, 33)) return;
        int gf = s.y + G;
        int[][] pillars = {{16, 14}, {-16, 14}, {16, -14}, {-16, -14}, {34, 13}, {-34, 13}, {34, -13}, {-34, -13}};
        for (int[] q : pillars) {
            f.box(q[0] - 1, gf + 1, q[1] - 1, q[0] + 1, gf + 9, q[1] + 1, Draw.mix(RED_NB, BRICK, 0.3, s.salt + 21));
            f.box(q[0] - 1, gf + 10, q[1] - 1, q[0] + 1, gf + 10, q[1] + 1, Draw.of(CHISELED));
            f.set(q[0], gf + 11, q[1], RACK); f.set(q[0], gf + 12, q[1], FIRE);
            for (int[] e : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) f.box(q[0] + e[0], gf + 4, q[1] + e[1], q[0] + e[0], gf + 9, q[1] + e[1], Draw.of(IRON));
        }
        for (int sg = -1; sg <= 1; sg += 2) {               // chains strung between the pillars
            f.box(sg * 16, gf + 9, -12, sg * 16, gf + 9, 12, Draw.of(IRON));
            f.box(sg * 18, gf + 9, sg * 14, sg * 32, gf + 9, sg * 14, Draw.of(IRON));
            f.box(-sg * 18, gf + 9, sg * 14, -sg * 32, gf + 9, sg * 14, Draw.of(IRON));
        }
        for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2) {
            statue(f, su * 8, sv * 21, gf, sv);
            f.box(su * 12 - 1, gf, sv * 6 - 1, su * 12 + 1, gf, sv * 6 + 1, Draw.of(MAGMA));   // a flame vent
        }
        f.box(-1, gf, -1, 1, gf, 1, (x, y, z) -> RED_NB);
    }

    /** A gladiator's statue on its plinth: shield on one arm, the sword raised in the other, a crested helm. */
    private void statue(Draw.Frame f, int u, int v, int y, int sg) {
        f.box(u - 1, y + 1, v - 1, u + 1, y + 2, v + 1, Draw.of(BRICK));
        f.box(u - 1, y + 3, v, u - 1, y + 5, v, Draw.of(RED_T)); f.box(u + 1, y + 3, v, u + 1, y + 5, v, Draw.of(RED_T));
        f.box(u - 1, y + 6, v, u + 1, y + 8, v, Draw.of(RED_T));
        f.set(u, y + 6, v, ORANGE_T);
        f.box(u - 2, y + 7, v, u - 2, y + 8, v, Draw.of(RED_T));
        f.box(u - 3, y + 5, v - 1, u - 3, y + 8, v + 1, Draw.of(BRICK));
        f.box(u + 2, y + 8, v, u + 2, y + 10, v, Draw.of(RED_T));
        f.box(u + 2, y + 11, v, u + 2, y + 13, v, Draw.of(IRON));
        f.set(u, y + 9, v, WHITE_T); f.set(u, y + 10, v, BRICK); f.set(u, y + 11, v, FENCE_NB);
        f.set(u, y + 9, v - sg, IRON);
    }

    // ---- the outer works ----------------------------------------------------------------------------------------------
    /** The Triumphal avenue from the cavern's edge to the main gate: statues, braziers, a triumphal arch. */
    private void avenue(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -162, -16, -112, 16)) return;
        cols(f, -158, -5, -124, 5, (u, v, x, z) -> {
            int y = s.floorAt(x, z);
            for (int yy = y - 2; yy < y; yy++) f.d.set(x, yy, z, BRICK);
            f.d.set(x, y, z, Math.abs(v) == 5 ? RED_NB : ((x + z) & 1) == 0 ? BRICK : CHISELED_SB);
            for (int yy = y + 1; yy <= y + 4; yy++) f.d.set(x, yy, z, 0);
        });
        for (int u = -128; u >= -152; u -= 12) for (int sg = -1; sg <= 1; sg += 2) {
            int y = s.floorAt(f.x(u, sg * 9), f.z(u, sg * 9));
            f.box(u - 2, y - 2, sg * 9 - 2, u + 2, y, sg * 9 + 2, Draw.of(BRICK));
            statue(f, u, sg * 9, y, -sg);
            int by = s.floorAt(f.x(u - 6, sg * 7), f.z(u - 6, sg * 7));
            f.set(u - 6, by + 1, sg * 7, BRICK); f.set(u - 6, by + 2, sg * 7, RACK); f.set(u - 6, by + 3, sg * 7, FIRE);
        }
        // the triumphal arch over the avenue
        int au = -146, ay = s.floorAt(f.x(au, 0), f.z(au, 0));
        for (int sg = -1; sg <= 1; sg += 2) {
            f.box(au - 2, ay - 2, sg * 6, au + 2, ay + 12, sg * 10, Draw.of(RED_NB));
            f.box(au - 2, ay + 1, sg * 7, au + 2, ay + 8, sg * 7, Draw.AIR);
            f.box(au - 3, ay + 1, sg * 10, au - 3, ay + 11, sg * 10, Draw.of(QP_Y));
            f.box(au + 3, ay + 1, sg * 10, au + 3, ay + 11, sg * 10, Draw.of(QP_Y));
        }
        f.box(au - 2, ay + 10, -6, au + 2, ay + 16, 6, Draw.of(RED_NB));
        for (int v = -5; v <= 5; v++) {
            int top = ay + 6 + (int) Math.round(Math.sqrt(Math.max(0, 30 - v * v)));
            f.box(au - 2, ay + 1, v, au + 2, Math.min(top, ay + 11), v, Draw.AIR);
        }
        f.box(au - 3, ay + 14, -6, au + 3, ay + 14, 6, Draw.of(CHISELED));
        f.box(au - 2, ay + 17, -4, au + 2, ay + 17, 4, Draw.of(BRICK));
        f.set(au, ay + 18, 0, MAGMA);
        f.wallSign(au + 3, ay + 12, 0, Draw.EAST, "TRIUMPH", "Walk the road", "of the victors", "to the sand");
        f.wallSign(au - 3, ay + 12, 0, Draw.WEST, "THE INFERNAL", "COLOSSEUM", "Glory waits", "within");
        f.chest(au + 2, ay + 1, 9, Draw.WEST, LOOT);
    }

    /** The ludus, the gladiators' barracks: walls round a practice ring, the cells, the master's house, the armoury, the tunnel. */
    private void ludus(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -32, -144, 32, -27)) return;
        int y0 = s.y;
        cols(f, -30, -142, 30, -106, (u, v, x, z) -> {
            for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, BRICK);
            double q = Draw.rnd(x, 5, z, s.salt + 31);
            f.d.set(x, y0, z, q < 0.45 ? GRAVEL : q < 0.85 ? SOUL : BROWN_T);
            for (int y = y0 + 1; y <= y0 + 10; y++) f.d.set(x, y, z, 0);
        });
        f.walls(-29, y0 + 1, -141, 29, y0 + 7, -107, Draw.mix(BRICK, RED_NB, 0.25, s.salt + 33));
        for (int u = -29; u <= 29; u += 2) { f.set(u, y0 + 8, -141, RED_NB); f.set(u, y0 + 8, -107, RED_NB); }
        for (int v = -141; v <= -107; v += 2) { f.set(-29, y0 + 8, v, RED_NB); f.set(29, y0 + 8, v, RED_NB); }
        f.box(-2, y0 + 1, -107, 2, y0 + 5, -107, Draw.AIR);
        for (int sg = -1; sg <= 1; sg += 2) { f.box(sg * 3, y0 + 1, -108, sg * 5, y0 + 10, -106, Draw.of(RED_NB)); f.set(sg * 4, y0 + 11, -107, MAGMA); }
        f.wallSign(-3, y0 + 4, -106, Draw.SOUTH, "LUDUS INFERNUS", "The school of", "the gladiators", "");
        // the practice ring and its posts
        cols(f, 2, -129, 22, -115, (u, v, x, z) -> {
            double q = sq((u - 12) / 9.5) + sq((v + 122) / 6.5);
            if (q > 1) return;
            if (q > 0.78 && Math.abs(v + 122) > 1) { f.d.set(x, y0 + 1, z, RED_NB); f.d.set(x, y0 + 2, z, FENCE_NB); }
            else f.d.set(x, y0, z, ((x * 3 + z) & 3) == 0 ? GRAVEL : SOUL);
        });
        for (int[] q : new int[][]{{-12, -116}, {-18, -116}, {-12, -128}, {-18, -128}, {-24, -122}, {18, -112}}) {
            f.set(q[0], y0 + 1, q[1], FENCE); f.set(q[0], y0 + 2, q[1], HAY); f.set(q[0], y0 + 3, q[1], PUMPKIN);
        }
        // the cells along the back wall
        f.box(-28, y0, -140, 28, y0 + 6, -133, Draw.of(BRICK));
        for (int i = 0; i < 8; i++) {
            int u0 = -27 + 7 * i;
            f.box(u0, y0 + 1, -139, u0 + 5, y0 + 4, -134, Draw.AIR);
            f.box(u0, y0, -139, u0 + 5, y0, -134, Draw.of(PLANK));
            f.box(u0 + 2, y0 + 1, -133, u0 + 2, y0 + 3, -133, Draw.AIR);
            f.box(u0 + 4, y0 + 2, -133, u0 + 4, y0 + 3, -133, Draw.of(IRON));
            f.set(u0, y0 + 1, -139, HAY); f.set(u0 + 1, y0 + 1, -139, HAY);
            f.set(u0 + 3, y0 + 4, -137, GLOW);
            if (i == 2) f.chest(u0 + 5, y0 + 1, -139, Draw.NORTH, LOOT);
            if (i == 6) f.chest(u0 + 5, y0 + 1, -139, Draw.NORTH, LOOT + "!trap");
        }
        // the master's house (two floors) and the armoury
        f.box(22, y0, -131, 28, y0 + 10, -109, Draw.of(RED_NB));
        f.box(23, y0 + 1, -130, 27, y0 + 4, -110, Draw.AIR);
        f.box(23, y0 + 5, -130, 27, y0 + 5, -110, Draw.of(PLANK));
        f.box(23, y0 + 6, -130, 27, y0 + 9, -110, Draw.AIR);
        f.box(22, y0 + 1, -121, 22, y0 + 3, -119, Draw.AIR);
        f.set(27, y0 + 5, -111, 0);
        for (int y = y0 + 1; y <= y0 + 5; y++) f.set(27, y, -111, b(65, f.facing(Draw.WEST)));
        f.box(22, y0 + 7, -126, 22, y0 + 8, -124, Draw.of(IRON));
        f.set(25, y0 + 9, -120, GLOW); f.set(25, y0 + 4, -125, GLOW);
        f.chest(23, y0 + 6, -129, Draw.EAST, LOOT + "_rich");
        f.box(-28, y0, -131, -22, y0 + 8, -109, Draw.of(RED_NB));
        f.box(-27, y0 + 1, -130, -23, y0 + 7, -110, Draw.AIR);
        f.box(-22, y0 + 1, -121, -22, y0 + 4, -119, Draw.AIR);
        for (int v = -129; v <= -111; v += 3) { f.set(-27, y0 + 2, v, FENCE); f.set(-27, y0 + 3, v, IRON); }
        f.set(-25, y0 + 7, -120, GLOW);
        f.spawner(-24, y0 + 1, -127, "pigman_berserker");
        f.chest(-27, y0 + 1, -114, Draw.EAST, LOOT);
        // the tunnel down to the hypogeum's pens
        for (int k = 0; k <= 7; k++) {
            int v = -126 + k, y = y0 - k;
            for (int u = -1; u <= 1; u++) f.set(u, y, v, f.stair(114, 3, false));
            f.box(-1, y + 1, v, 1, y + 4, v, Draw.AIR);
            f.set(-2, y0 + 1, v, FENCE_NB); f.set(2, y0 + 1, v, FENCE_NB);
        }
        f.box(-2, y0 - 8, -118, 2, y0 - 3, -29, Draw.of(BRICK));
        f.box(-1, y0 - 7, -118, 1, y0 - 4, -29, Draw.AIR);
        for (int v = -114; v <= -34; v += 8) f.set(0, y0 - 3, v, GLOW);
        f.box(3, y0 + 1, -126, 3, y0 + 2, -126, Draw.of(BRICK));
        f.wallSign(3, y0 + 2, -127, Draw.NORTH, "To the", "hypogeum", "and the sand", "");
    }

    /** By the Gate of Death: the spoliarium where the fallen are stripped, the forge of the arena's blades. */
    private void forge(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, 108, -28, 152, 32)) return;
        int y0 = s.y;
        cols(f, 116, -26, 148, 30, (u, v, x, z) -> {
            boolean road = Math.abs(v) <= 4 && u <= 150, spol = u >= 122 && u <= 142 && v >= -26 && v <= -10, forge = u >= 122 && u <= 146 && v >= 10 && v <= 30;
            if (!road && !spol && !forge) return;
            for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, BRICK);
            f.d.set(x, y0, z, road ? (((x + z) & 1) == 0 ? BRICK : BLACK_T) : GRAVEL);
            for (int y = y0 + 1; y <= y0 + 6; y++) f.d.set(x, y, z, 0);
        });
        // the spoliarium
        f.box(124, y0, -24, 140, y0 + 8, -12, Draw.of(BRICK));
        f.box(125, y0 + 1, -23, 139, y0 + 7, -13, Draw.AIR);
        f.box(125, y0, -23, 139, y0, -13, Draw.of(BLACK_T));
        f.box(124, y0 + 9, -24, 140, y0 + 9, -12, Draw.of(NBSLAB));
        f.box(130, y0 + 1, -12, 132, y0 + 3, -12, Draw.AIR);
        for (int u = 127; u <= 137; u += 5) {
            f.set(u, y0 + 1, -20, FENCE); f.set(u + 1, y0 + 1, -20, FENCE);
            f.box(u, y0 + 2, -20, u + 1, y0 + 2, -20, Draw.of(STONE_SLAB));
            f.skull(u, y0 + 3, -20, 0, 0);
        }
        f.set(126, y0 + 6, -22, WEB); f.set(138, y0 + 6, -14, WEB); f.set(132, y0 + 7, -18, GLOW);
        f.set(138, y0 + 1, -22, CAULDRON);
        f.chest(139, y0 + 1, -16, Draw.WEST, LOOT + "!trap");
        f.wallSign(129, y0 + 3, -11, Draw.SOUTH, "SPOLIARIUM", "Where the dead", "are stripped", "");
        // the forge: an open hall, three hearths of molten iron under their chimneys
        f.box(124, y0, 12, 144, y0 + 10, 28, Draw.of(RED_NB));
        f.box(125, y0 + 1, 12, 143, y0 + 9, 27, Draw.AIR);
        f.box(125, y0, 13, 143, y0, 27, (x, y, z) -> ((x + z) % 5) == 0 ? MAGMA : BRICK);
        for (int u = 124; u <= 144; u += 5) f.box(u, y0 + 1, 12, u, y0 + 9, 12, Draw.of(BRICK));
        f.box(124, y0 + 11, 12, 144, y0 + 11, 28, Draw.of(NBSLAB));
        for (int u = 128; u <= 140; u += 6) {
            f.box(u - 1, y0 + 1, 25, u + 1, y0 + 2, 27, Draw.of(BRICK));
            f.set(u, y0 + 2, 26, LAVA);
            f.box(u - 1, y0 + 6, 26, u + 1, y0 + 17, 27, Draw.of(BRICK));
            f.set(u, y0 + 18, 26, RACK); f.set(u, y0 + 19, 26, FIRE);
            f.set(u - 2, y0 + 1, 24, CAULDRON);
            f.set(u + 2, y0 + 1, 22, CHISELED_SB); f.set(u + 2, y0 + 2, 22, IRON);
        }
        for (int v = 15; v <= 24; v += 3) { f.set(143, y0 + 2, v, IRON); f.set(143, y0 + 3, v, IRON); f.set(125, y0 + 2, v, IRON); }
        f.spawner(134, y0 + 1, 19, "blaze");
        f.chest(143, y0 + 1, 18, Draw.WEST, LOOT + "_rich");
        f.wallSign(129, y0 + 4, 11, Draw.NORTH, "THE FORGE", "Blades for", "the sand", "");
    }

    /** The Emperor's Way before his gate: a paved road between two obelisks and their fires. */
    private void emperorsWay(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -14, 96, 14, 128)) return;
        cols(f, -4, 100, 4, 124, (u, v, x, z) -> {
            int y = s.floorAt(x, z);
            f.d.set(x, y - 1, z, BRICK);
            f.d.set(x, y, z, Math.abs(u) == 4 ? YELLOW_C : ((x + z) & 1) == 0 ? RED_NB : CHISELED_SB);
            for (int yy = y + 1; yy <= y + 4; yy++) f.d.set(x, yy, z, 0);
        });
        for (int sg = -1; sg <= 1; sg += 2) {
            int u = sg * 9, v = 106, y = s.floorAt(f.x(u, v), f.z(u, v));
            f.box(u - 2, y - 2, v - 2, u + 2, y + 1, v + 2, Draw.of(BRICK));
            f.box(u - 1, y + 2, v - 1, u + 1, y + 20, v + 1, (x, yy, z) -> (yy - y) % 6 == 0 ? MAGMA : RED_NB);
            f.box(u - 1, y + 21, v - 1, u + 1, y + 21, v + 1, Draw.of(QUARTZ));
            f.set(u, y + 22, v, QUARTZ); f.set(u, y + 23, v, YELLOW_C);
            int by = s.floorAt(f.x(sg * 6, 118), f.z(sg * 6, 118));
            f.set(sg * 6, by + 1, 118, QUARTZ); f.set(sg * 6, by + 2, 118, RACK); f.set(sg * 6, by + 3, 118, FIRE);
        }
    }

    /** At the diagonals: a training yard, the beast stables, the gladiators' graves, the Undying's colossus. */
    private void yards(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        int[][] at = {{-90, -76}, {-90, 76}, {90, -76}, {90, 76}};
        for (int i = 0; i < 4; i++) {
            int cu = at[i][0], cv = at[i][1];
            if (!touches(f, cu - 14, cv - 14, cu + 14, cv + 14)) continue;
            final int fi = i;
            cols(f, cu - 12, cv - 9, cu + 12, cv + 9, (u, v, x, z) -> {
                for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, BRICK);
                double q = Draw.rnd(x, 6, z, s.salt + 41 + fi);
                f.d.set(x, y0, z, fi == 2 ? (q < 0.6 ? SOUL : BROWN_T) : q < 0.5 ? GRAVEL : SOUL);
                for (int y = y0 + 1; y <= y0 + 8; y++) f.d.set(x, y, z, 0);
            });
            if (i != 3) {
                f.walls(cu - 12, y0 + 1, cv - 9, cu + 12, y0 + 2, cv + 9, Draw.of(i == 2 ? BRICK : FENCE));
                f.box(cu - 2, y0 + 1, cv - 9, cu + 2, y0 + 2, cv - 9, Draw.AIR);
                f.box(cu - 2, y0 + 1, cv + 9, cu + 2, y0 + 2, cv + 9, Draw.AIR);
            }
            if (i == 0) {               // the training yard: practice posts in rows, a weapon rack
                for (int u = cu - 8; u <= cu + 8; u += 4) for (int v = cv - 5; v <= cv + 5; v += 5) {
                    f.set(u, y0 + 1, v, FENCE); f.set(u, y0 + 2, v, HAY); f.set(u, y0 + 3, v, (u + v) % 3 == 0 ? JACK : PUMPKIN);
                }
                f.box(cu - 11, y0 + 1, cv - 3, cu - 11, y0 + 2, cv + 3, Draw.of(FENCE));
                f.box(cu - 11, y0 + 3, cv - 3, cu - 11, y0 + 3, cv + 3, Draw.of(IRON));
                f.chest(cu + 11, y0 + 1, cv, Draw.WEST, LOOT);
            } else if (i == 1) {        // the beast stables: barred pens round a yard
                for (int k = 0; k < 4; k++) {
                    int u0 = cu - 11 + k * 6;
                    f.box(u0, y0, cv + 3, u0 + 4, y0 + 4, cv + 8, Draw.of(BRICK));
                    f.box(u0 + 1, y0 + 1, cv + 4, u0 + 3, y0 + 3, cv + 7, Draw.AIR);
                    f.box(u0 + 1, y0 + 1, cv + 3, u0 + 3, y0 + 3, cv + 3, Draw.of(IRON));
                    f.set(u0 + 2, y0 + 1, cv + 3, 0); f.set(u0 + 2, y0 + 2, cv + 3, 0);
                    f.set(u0 + 1, y0 + 1, cv + 7, HAY); f.set(u0 + 2, y0 + 4, cv + 5, GLOW);
                }
                f.chest(cu - 9, y0 + 1, cv + 6, Draw.EAST, LOOT);
            } else if (i == 2) {        // the gladiators' graves
                for (int u = cu - 9; u <= cu + 9; u += 3) for (int v = cv - 6; v <= cv + 6; v += 4) {
                    f.set(u, y0 + 1, v, CHISELED_SB);
                    f.set(u, y0 + 2, v, STONE_SLAB);
                    if (Draw.rnd(u, 7, v, s.salt + 47) < 0.3) f.skull(u, y0 + 1, v + 1, 0, 0);
                }
                f.chest(cu, y0 + 1, cv + 8, Draw.NORTH, LOOT);
                f.set(cu - 11, y0 + 1, cv - 8, RACK); f.set(cu - 11, y0 + 2, cv - 8, FIRE);
            } else {                    // the Undying's colossus, his sword raised towards the arena
                int y = y0;
                f.box(cu - 6, y + 1, cv - 6, cu + 6, y + 3, cv + 6, Draw.of(BRICK));
                f.box(cu - 5, y + 4, cv - 5, cu + 5, y + 4, cv + 5, Draw.of(QUARTZ));
                f.box(cu - 3, y + 5, cv - 1, cu - 2, y + 12, cv + 1, Draw.of(RED_T));
                f.box(cu + 2, y + 5, cv - 1, cu + 3, y + 12, cv + 1, Draw.of(RED_T));
                f.box(cu - 3, y + 13, cv - 2, cu + 3, y + 20, cv + 2, Draw.of(RED_T));
                f.box(cu - 3, y + 13, cv - 2, cu + 3, y + 13, cv + 2, Draw.of(ORANGE_T));
                f.box(cu - 6, y + 14, cv - 2, cu - 4, y + 19, cv + 2, Draw.of(BRICK));
                f.box(cu + 4, y + 17, cv - 1, cu + 5, y + 22, cv + 1, Draw.of(RED_T));
                f.box(cu + 5, y + 23, cv - 1, cu + 5, y + 31, cv - 1, Draw.of(IRON));
                f.box(cu - 2, y + 21, cv - 2, cu + 2, y + 24, cv + 2, Draw.of(WHITE_T));
                f.box(cu - 2, y + 25, cv - 2, cu + 2, y + 25, cv + 2, Draw.of(BRICK));
                f.box(cu, y + 26, cv - 2, cu, y + 28, cv + 2, Draw.of(RED_NB));
                f.set(cu - 1, y + 23, cv - 3, MAGMA); f.set(cu + 1, y + 23, cv - 3, MAGMA);
                f.wallSign(cu, y + 2, cv - 7, Draw.NORTH, "THE UNDYING", "He fell a", "hundred times", "and rose");
            }
        }
    }

    private void trapMarks(Draw.Frame f, Colossi.Site s, Plan p) {}

    // ---- the garrisons and the traps -----------------------------------------------------------------------------------
    /** A garrison on the rows: bay k, depth d (blocks in from the outer face); its y is the row's top + 1. */
    private static Garrison seat(Plan p, int gf, int k, double d, String pack) {
        double th = angle(p, k), dt = Math.hypot(DA * Math.cos(th), DB * Math.sin(th)), sc = 1 - d / dt;
        int u = (int) Math.round((A0 + DA * sc) * Math.cos(th)), v = (int) Math.round((B0 + DB * sc) * Math.sin(th));
        int h = gf + 6 + (int) Math.floor(24 * (dt - 4 - d) / (dt - 18));
        return g(u, h + 1, v, pack);
    }

    private static Garrison ring(Plan p, int k, double d, int y, String pack) {
        double th = angle(p, k), dt = Math.hypot(DA * Math.cos(th), DB * Math.sin(th)), sc = 1 - d / dt;
        return g((int) Math.round((A0 + DA * sc) * Math.cos(th)), y, (int) Math.round((B0 + DB * sc) * Math.sin(th)), pack);
    }

    private List<Garrison> garrisons(Plan p, Colossi.Site s) {
        int y0 = s.y, gf = y0 + G, g1 = gf + 1;
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(-80, g1, 0, "!infernal_knight+wither_skeleton"));
        gs.add(g(80, g1, 0, "crypt_guard+lost_soul+wight"));
        gs.add(g(0, g1, -64, "ember_legionnaire+flame_adept"));
        gs.add(g(0, g1, 62, "royal_guard+royal_guard"));
        gs.add(seat(p, gf, 7, 30, "ashbone_archer+skeleton"));
        gs.add(seat(p, gf, 33, 26, "ashbone_archer+skeleton"));
        gs.add(seat(p, gf, 57, 30, "skeleton+ashbone_archer"));
        gs.add(ring(p, 15, 8, gf + 31, "wither_skeleton+skeleton"));
        gs.add(ring(p, 45, 8, gf + 31, "soul_wraith+shade"));
        gs.add(ring(p, 25, 4.5, gf + 1, "wight+shade"));
        gs.add(ring(p, 55, 4.5, gf + 11, "cinder_witch+charred_ghoul"));
        gs.add(g(0, gf + 12, 37, "!royal_guard+flame_adept"));
        gs.add(g(-22, y0 - 1, 0, "pigman_berserker+spinout"));
        gs.add(g(22, y0 - 1, 5, "charred_ghoul+mummy"));
        gs.add(g(-13, y0 - 7, 12, "hellhound+brute"));
        gs.add(g(13, y0 - 7, -12, "hellhound+brute+magma_hulk"));
        gs.add(g(31, y0 - 7, 0, "coolmar_spider+brimstone_spider+deep_crawler"));
        gs.add(g(-10, y0 + 1, -121, "!pigman_berserker+ember_legionnaire"));
        gs.add(g(-6, y0 + 1, -130, "salamander+nethermite"));
        gs.add(g(134, y0 + 1, 17, "pyre_warden+cinder_imp+ember"));
        gs.add(g(132, y0 + 1, -17, "tomb_guardian+asp+scarab"));
        gs.add(g(-136, s.floorAt(fr.x(-136, 0), fr.z(-136, 0)) + 1, 0, "cinder_witch+magma_cube"));
        gs.add(g(-90, y0 + 1, -76, "frost+mogus+spore"));
        gs.add(g(-90, y0 + 1, 74, "hellhound+dread_rider"));
        gs.add(g(90, y0 + 1, -76, "wight+lost_soul+spore_creeper"));
        gs.add(g(0, s.floorAt(fr.x(0, 112), fr.z(0, 112)) + 1, 112, "dread_rider+blaze"));
        // the great fliers in the open air over the sand and the avenue
        gs.add(g(22, gf + 24, 5, "ghast+ghastling"));
        gs.add(g(-22, gf + 26, -5, "ghast+ghastling"));
        gs.add(g(0, gf + 34, 0, "ghastling+ghastling"));
        gs.add(g(-140, gf + 20, 22, "ghast+ghastling"));
        gs.add(g(100, gf + 22, -60, "ghastling+blaze"));
        return gs;
    }

    /** The traps: kind 0 arrows, 1 collapse, 2 rubble, 3 flames, 4 gas, 5 cannon; a local box, an emitter (or gun), a block; the frame's turn. */
    private void traps(Plan p, Colossi.Site s) {
        int y0 = s.y, gf = y0 + G, mid = y0 - 2;
        List<int[]> t = p.traps;
        for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2)
            t.add(new int[]{3, su * 12 - 1, gf + 1, sv * 6 - 1, su * 12 + 1, gf + 3, sv * 6 + 1, 0, 0, 0, 0, 0, 0, 0, 0});
        for (int[] q : new int[][]{{15, 18}, {-15, -18}, {30, -15}})
            t.add(new int[]{1, q[0] - 1, mid, q[1] - 1, q[0] + 1, mid, q[1] + 1, 0, 0, 0, 0, 0, 0, TRAPFLOOR, 0});
        for (int dir = 0; dir < 4; dir++) {           // arrows in the gates' long dark
            int ein = (dir & 1) == 0 ? (int) A0 : (int) B0;
            t.add(new int[]{0, ein + 16, gf + 1, -4, ein + 30, gf + 4, 4, ein + 15, gf + 2, -5, ein + 31, gf + 4, 5, 0, dir});
        }
        for (int k : new int[]{35, 75}) {              // the upper ambulatory's crumbling vault
            double th = angle(p, k), dt = Math.hypot(DA * Math.cos(th), DB * Math.sin(th)), sc = 1 - 4.5 / dt;
            int u = (int) Math.round((A0 + DA * sc) * Math.cos(th)), v = (int) Math.round((B0 + DB * sc) * Math.sin(th));
            t.add(new int[]{2, u - 3, gf + 11, v - 3, u + 3, gf + 15, v + 3, 0, 0, 0, 0, 0, 0, RED_NB, 0});
        }
        t.add(new int[]{4, 125, y0 + 1, -23, 139, y0 + 4, -13, 0, 0, 0, 0, 0, 0, 0, 0});
        t.add(new int[]{5, -160, y0 - 2, -24, -112, gf + 24, 24, -112, gf + 21, 0, 0, 0, 0, 0, 0});
        t.add(new int[]{5, 112, y0 - 2, -24, 152, gf + 24, 24, 112, gf + 21, 0, 0, 0, 0, 0, 0});
    }

    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        for (int[] t : p.traps) {
            Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, (p.rot + t[14]) & 3);
            int[] box = fr.box(t[1], t[2], t[3], t[4], t[5], t[6]);
            switch (t[0]) {
                case 0: out.add(Ordeals.arrows(box, fr.box(t[7], t[8], t[9], t[10], t[11], t[12]))); break;
                case 1: out.add(Ordeals.collapse(box, t[13])); break;
                case 2: out.add(Ordeals.rubble(box, t[13])); break;
                case 3: out.add(Ordeals.flames(box, 60)); break;
                case 4: out.add(Ordeals.gas(box)); break;
                case 5: out.add(Ordeals.cannon(box, fr.at(t[7], t[8], t[9]))); break;
                default:
            }
        }
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        out.add(Ordeals.bossSeal(s.kind.id + "_vault", fr.box(10, s.y + G + 1, 31, 12, s.y + G + 3, 31), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern: scorched rock, ash and cinders --------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61), n = Draw.noise(x, z, 9, s.salt + 63);
        if (s.f(x, z) > 0.86) return q < 0.25 ? SOUL : RACK;
        if (n > 0.55) return q < 0.5 ? GRAVEL : BLACK_T;
        return q < 0.04 ? MAGMA : q < 0.16 ? SOUL : q < 0.26 ? BROWN_T : RACK;
    }

    @Override Draw.Mat under() { return Draw.mix(RACK, SOUL, 0.2, 8811); }

    @Override double lakeLevel() { return 0.36; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.004);
        if (lake) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.0015) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
        else if (q < 0.0022) { d.set(x, floor + 1, z, FENCE_NB); d.set(x, floor + 2, z, IRON); }
    }

    // ---- local columns ---------------------------------------------------------------------------------------------------
    interface Col { void at(int u, int v, int x, int z); }

    /** Calls c for every local column of the rectangle inside the draw box. */
    static void cols(Draw.Frame f, int u0, int v0, int u1, int v1, Col c) {
        Draw d = f.d;
        int[][] cs = {{d.x0, d.z0}, {d.x1, d.z0}, {d.x0, d.z1}, {d.x1, d.z1}};
        int ua = Integer.MAX_VALUE, ub = Integer.MIN_VALUE, va = Integer.MAX_VALUE, vb = Integer.MIN_VALUE;
        for (int[] q : cs) {
            int lu, lv;
            switch (f.rot) {
                case 1: lu = q[1] - f.oz; lv = f.ox - q[0]; break;
                case 2: lu = f.ox - q[0]; lv = f.oz - q[1]; break;
                case 3: lu = f.oz - q[1]; lv = q[0] - f.ox; break;
                default: lu = q[0] - f.ox; lv = q[1] - f.oz;
            }
            ua = Math.min(ua, lu); ub = Math.max(ub, lu); va = Math.min(va, lv); vb = Math.max(vb, lv);
        }
        ua = Math.max(ua, Math.min(u0, u1)); ub = Math.min(ub, Math.max(u0, u1));
        va = Math.max(va, Math.min(v0, v1)); vb = Math.min(vb, Math.max(v0, v1));
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) c.at(u, v, f.x(u, v), f.z(u, v));
    }
}
