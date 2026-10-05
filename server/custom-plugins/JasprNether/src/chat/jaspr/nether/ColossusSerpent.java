package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Coil of the World Serpent (2026-10-04, owner: "big, big, big structures"): a stone serpent wound round a pillar of
 * basalt and magma that rises from the cavern floor into the roof. Its body is hollow: a tunnel you climb inside it.
 * <ul>
 *   <li>the Serpent Temple at the cavern's edge, a stepped pyramid with serpent balustrades and rearing cobras: the
 *       Priests' Hall (falling stones), the two Egg Chambers below (poison), the shrine on its crown, and the Tail
 *       Gate, where the serpent's tail ends in the temple's wall;</li>
 *   <li>the tail across the floor, arching out of the ground twice (falling floors, arrow slits), then three coils up
 *       round the pillar: windows in its flanks, flame vents, the Shedding Hall, the Venom Cistern and the Brood
 *       Gallery where the body swells, and four shrines on platforms where the coils turn;</li>
 *   <li>the neck and the head under the roof, its jaws open, bone fangs, eyes that burn and spit fire: inside, the
 *       Queen's Lair and her nest, and above it, in the crest, the Crown Vault, sealed until the Serpent Queen falls;</li>
 *   <li>the egg field round the pillar's foot, the shed tail's rattle, ghasts circling the coils.</li>
 * </ul>
 */
final class ColossusSerpent extends ColossusDesign {
    // ---- geometry (heights above the site's floor level s.y; local frame u east, v south before the site's turn) ----------
    static final int YB = 5, YE = 52, YN = 58;            // centre heights: the tail on the floor, the coil's end, the throat
    static final double R0 = 54, R1 = 30, TURNS = 2.625;  // the coil: radius at its start (west) and its end (north-east)
    static final double RB = 7, SHELL = 2.0;              // the body's radius and its shell
    static final int TV = -116;                           // the temple's centre
    static final int LAIR = 54;                           // the lair's floor block (above s.y)
    static final int HB = -38, HL = 46;                   // the head: its back (v) and its length
    static final int SPIRAL_V = -12;                      // the Queen's Spiral, a ladder shaft down the pillar
    static final int T_WINDOW = 1, T_TRAP = 2, T_VENT = 4, T_BULGE = 8;
    static final double[] BULGES = {0.2, 0.47, 0.76};     // where the body swells (share of the coil)
    static final double[] SHRINE_Q = {1.0 / TURNS, 1.5 / TURNS, 1.75 / TURNS, 2.25 / TURNS};
    static final int[][] SHRINE_DIR = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};   // west, east, north, south: outward
    static final String[] SHRINES = {"the Dawn Coil", "the Dusk Coil", "the Chin", "the High Coil"};
    static final String LOOT = "jaspr:colossus/serpent", RICH = "jaspr:colossus/serpent_rich", VAULT = "jaspr:colossus/serpent_vault";

    static final class Plan {
        int rot, n, coil0, neck0;
        double[] u, v, x, y, z, r, ri, fl, sx, sz, ux, uy, uz, tx, tz;
        int[] tag;
        int[][] traps, vents, arrows;
        int[] bulges, shrines, doors;
        double[][] skin;
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette ------------------------------------------------------------------------------------------------------------
    private static final int T_GREEN = b(159, 13), T_LIME = b(159, 5), T_BLACK = b(159, 15), T_GREY = b(159, 7), T_LGREY = b(159, 8), T_WHITE = b(159, 0);
    private static final int PRISM = b(168, 0), PRISM_BR = b(168, 1), PRISM_DK = b(168, 2), C_GREEN = b(251, 13), C_LIME = b(251, 5), C_BLACK = b(251, 15);
    private static final int NBRICK = b(112), RNBRICK = b(215), NB_FENCE = b(113), NB_SLAB = b(44, 6), BONE = b(216, 0), SLIME = b(165), WEB = b(30);
    private static final int OBSIDIAN = b(49), MAGMA = b(213), RACK = b(87), FIRE = b(51), GLOW = b(89), SEA = b(169), MOSS = b(48);
    private static final int G_LIME = b(95, 5), G_GREEN = b(95, 13), G_BLACK = b(95, 15), GLAZED = b(248, 0), GLAZED_LIME = b(240, 0);
    private static final int PSTAIR_DK = 168, NB_STAIR = 114;
    private static final int TRAP_FLOOR = T_GREY;        // the tunnel's crumbling floors
    private static final int SEAL = PRISM_DK;             // the Crown Vault's door (the Queen's seal)

    // ---- small geometry helpers --------------------------------------------------------------------------------------------
    static double sq(double a) { return a * a; }
    static double lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    static double lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }
    static double wxd(Colossi.Site s, int rot, double u, double v) { switch (rot & 3) { case 1: return s.x - v; case 2: return s.x - u; case 3: return s.x + v; default: return s.x + u; } }
    static double wzd(Colossi.Site s, int rot, double u, double v) { switch (rot & 3) { case 1: return s.z + u; case 2: return s.z - v; case 3: return s.z - u; default: return s.z + v; } }

    static int[] cols(Draw.Frame f, double u0, double v0, double u1, double v1) {
        double xa = f.xd(u0, v0), xb = f.xd(u1, v1), za = f.zd(u0, v0), zb = f.zd(u1, v1);
        int x0 = Math.max((int) Math.floor(Math.min(xa, xb)), f.d.x0), x1 = Math.min((int) Math.ceil(Math.max(xa, xb)), f.d.x1);
        int z0 = Math.max((int) Math.floor(Math.min(za, zb)), f.d.z0), z1 = Math.min((int) Math.ceil(Math.max(za, zb)), f.d.z1);
        return x0 > x1 || z0 > z1 ? null : new int[]{x0, z0, x1, z1};
    }

    /** The pillar's radius at height y (above s.y: h), before its roughness. */
    static double pillarR(double h) {
        if (h < 6) return 30 + (6 - h) * 0.6;
        if (h <= 62) return 30 - (h - 6) * 7 / 56.0;
        return 23 + sq(Math.max(0, h - 66) / 22.0) * 9;
    }

    private static double bump(double s, double c, double w, double h) { double t = (s - c) / w; return Math.abs(t) >= 1 ? 0 : h * (1 + Math.cos(Math.PI * t)) / 2; }

    private static void bezier(List<double[]> out, double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3) {
        for (int k = out.isEmpty() ? 0 : 1; k <= 600; k++) {
            double t = k / 600.0, a = (1 - t) * (1 - t) * (1 - t), bb = 3 * (1 - t) * (1 - t) * t, c = 3 * (1 - t) * t * t, d = t * t * t;
            out.add(new double[]{a * u0 + bb * u1 + c * u2 + d * u3, a * v0 + bb * v1 + c * v2 + d * v3});
        }
    }

    // ---- the plan -------------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        int y0 = s.y;
        // the path, densely: the tail across the floor (two humps), the coil, the neck
        List<double[]> tail = new ArrayList<>();
        bezier(tail, 0, TV + 12, 0, TV + 28, -30, -98, -60, -94);
        bezier(tail, -60, -94, -95.7, -89.3, -110, -50, -96, -28);
        bezier(tail, -96, -28, -84.1, -9.5, -54, -30, -54, 0);
        double h1 = 11 + r.nextDouble() * 1.5, h2 = 13 + r.nextDouble() * 1.5, h3 = 8 + r.nextDouble() * 1.5;
        List<double[]> dense = new ArrayList<>();
        double acc = 0;
        for (int i = 0; i < tail.size(); i++) {
            if (i > 0) acc += Math.hypot(tail.get(i)[0] - tail.get(i - 1)[0], tail.get(i)[1] - tail.get(i - 1)[1]);
            double hump = bump(acc, 44, 38, h1) + bump(acc, 126, 44, h2) + bump(acc, 190, 29, h3);
            dense.add(new double[]{tail.get(i)[0], y0 + YB + hump, tail.get(i)[1], Double.NaN});
        }
        double th0 = Math.PI, th1 = Math.PI - 2 * Math.PI * TURNS;
        for (double th = th0 - 0.002; th >= th1; th -= 0.002) {
            double q = (th0 - th) / (th0 - th1), rr = R0 + (R1 - R0) * q;
            dense.add(new double[]{Math.cos(th) * rr, y0 + YB + (YE - YB) * q, Math.sin(th) * rr, q});
        }
        double ue = Math.cos(th1) * R1, ve = Math.sin(th1) * R1, tu = Math.sin(th1), tv = -Math.cos(th1);
        List<double[]> neck = new ArrayList<>();
        bezier(neck, ue, ve, ue + tu * 10, ve + tv * 10, 0, -30, 0, HB - 2);
        for (int i = 1; i < neck.size(); i++) {
            double q = i / (double) (neck.size() - 1);
            dense.add(new double[]{neck.get(i)[0], y0 + YE + (YN - YE) * q, neck.get(i)[1], Double.NaN});
        }
        // resampled every block of its length
        List<double[]> pts = new ArrayList<>();
        pts.add(dense.get(0));
        double next = 1, len = 0;
        boolean coil = false;
        int coil0 = -1, neck0 = -1;
        for (int i = 1; i < dense.size(); i++) {
            double[] a = dense.get(i - 1), q = dense.get(i);
            double seg = Math.sqrt(sq(q[0] - a[0]) + sq(q[1] - a[1]) + sq(q[2] - a[2]));
            while (seg > 0 && len + seg >= next) {
                double t = (next - len) / seg;
                double qq = Double.isNaN(q[3]) ? Double.NaN : Double.isNaN(a[3]) ? q[3] : a[3] + (q[3] - a[3]) * t;
                if (!Double.isNaN(qq) && coil0 < 0) coil0 = pts.size();
                if (Double.isNaN(qq) && coil0 >= 0 && neck0 < 0) neck0 = pts.size();
                pts.add(new double[]{a[0] + (q[0] - a[0]) * t, a[1] + (q[1] - a[1]) * t, a[2] + (q[2] - a[2]) * t, qq});
                next += 1;
            }
            len += seg;
        }
        int n = pts.size();
        p.n = n; p.coil0 = coil0; p.neck0 = neck0;
        p.u = new double[n]; p.v = new double[n]; p.x = new double[n]; p.y = new double[n]; p.z = new double[n];
        p.r = new double[n]; p.ri = new double[n]; p.fl = new double[n]; p.tag = new int[n];
        p.sx = new double[n]; p.sz = new double[n]; p.ux = new double[n]; p.uy = new double[n]; p.uz = new double[n]; p.tx = new double[n]; p.tz = new double[n];
        double[] qOf = new double[n];
        for (int i = 0; i < n; i++) {
            double[] q = pts.get(i);
            p.u[i] = q[0]; p.y[i] = q[1]; p.v[i] = q[2]; qOf[i] = q[3];
            p.x[i] = wxd(s, p.rot, q[0], q[2]); p.z[i] = wzd(s, p.rot, q[0], q[2]);
            p.r[i] = i >= neck0 ? RB - 0.5 * Math.min(1, (i - neck0) / 10.0) : RB;
        }
        // the swellings: chambers inside the body
        p.bulges = new int[BULGES.length];
        for (int k = 0; k < BULGES.length; k++) {
            int at = sampleAt(qOf, coil0, neck0, BULGES[k]);
            p.bulges[k] = at;
            for (int i = Math.max(0, at - 9); i <= Math.min(n - 1, at + 9); i++) {
                double t = Math.abs(i - at) / 9.0;
                p.r[i] = Math.max(p.r[i], RB + 2.5 * (t < 0.45 ? 1 : (1 + Math.cos(Math.PI * (t - 0.45) / 0.55)) / 2));
                if (Math.abs(i - at) <= 6) p.tag[i] |= T_BULGE;
            }
        }
        for (int i = 0; i < n; i++) { p.ri[i] = p.r[i] - SHELL; p.fl[i] = p.y[i] - 3; }
        // tangents, and the side (outward on the coil) and up vectors, in world coordinates
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 2), c = Math.min(n - 1, i + 2);
            double dx = p.x[c] - p.x[a], dy = p.y[c] - p.y[a], dz = p.z[c] - p.z[a], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l; dy /= l; dz /= l;
            double sl = Math.hypot(dx, dz), sxx = -dz / sl, szz = dx / sl;
            p.sx[i] = sxx; p.sz[i] = szz; p.tx[i] = dx / sl; p.tz[i] = dz / sl;
            p.ux[i] = -szz * dy; p.uy[i] = szz * dx - sxx * dz; p.uz[i] = sxx * dy;       // side x tangent
        }
        // the shrines where the coil turns past the four winds
        p.shrines = new int[SHRINE_Q.length];
        for (int k = 0; k < SHRINE_Q.length; k++) p.shrines[k] = sampleAt(qOf, coil0, neck0, SHRINE_Q[k]);
        // traps: falling floors (held level over a pit), flame vents, arrow slits
        p.traps = new int[][]{{6, 10}, {coil0 + 5, coil0 + 9}, {p.bulges[0] - 2, p.bulges[0] + 2}};
        for (int[] t : p.traps) {
            double lo = 1e9;
            for (int i = t[0]; i <= t[1]; i++) lo = Math.min(lo, p.fl[i]);
            for (int i = t[0]; i <= t[1]; i++) { p.fl[i] = Math.floor(lo); p.tag[i] |= T_TRAP; }
        }
        p.vents = new int[3][];
        double[] ventQ = {0.31, 0.6, 0.92};
        for (int k = 0; k < 3; k++) { int at = sampleAt(qOf, coil0, neck0, ventQ[k]); p.vents[k] = new int[]{at - 2, at + 2}; for (int i = at - 2; i <= at + 2; i++) p.tag[i] |= T_VENT; }
        p.arrows = new int[4][];
        double[] arrowQ = {0.11, 0.42, 0.68, 0.97};
        p.arrows[0] = new int[]{60, 68};
        for (int k = 1; k < 4; k++) { int at = sampleAt(qOf, coil0, neck0, arrowQ[k]); p.arrows[k] = new int[]{at - 4, at + 4}; }
        // side doors where the tail touches the ground between its humps
        p.doors = new int[]{82, 165};
        // windows along the flanks, clear of the chambers, the shrines, the doors and the traps
        for (int i = 14; i < n - 10; i++) {
            if (i % 19 != 9 || (p.tag[i] & (T_BULGE | T_TRAP)) != 0) continue;
            boolean near = false;
            for (int sh : p.shrines) if (Math.abs(i - sh) < 6) near = true;
            for (int dr : p.doors) if (Math.abs(i - dr) < 5) near = true;
            if (!near) for (int k = -1; k <= 1; k++) p.tag[i + k] |= T_WINDOW;
        }
        // the shed skin she left lying across the floor (local u, y, v every block)
        List<double[]> skin2d = new ArrayList<>();
        bezier(skin2d, 12, 70, 34, 100, 70, 70, 100, 106);
        List<double[]> skin = new ArrayList<>();
        double sl = 0, nextSkin = 0;
        for (int i = 0; i < skin2d.size(); i++) {
            if (i > 0) sl += Math.hypot(skin2d.get(i)[0] - skin2d.get(i - 1)[0], skin2d.get(i)[1] - skin2d.get(i - 1)[1]);
            if (sl >= nextSkin) { skin.add(new double[]{skin2d.get(i)[0], y0 + 5 + bump(sl, 52, 28, 6), skin2d.get(i)[1]}); nextSkin += 1; }
        }
        p.skin = skin.toArray(new double[0][]);
        // the garrisons
        List<Garrison> gs = new ArrayList<>();
        int g0 = y0 + 2;
        gs.add(g(0, g0, TV - 30, "!infernal_knight+wither_skeleton"));
        gs.add(g(-9, g0, TV - 38, "royal_guard+skeleton"));
        gs.add(g(0, g0, TV - 4, "cinder_witch+flame_adept"));
        gs.add(g(-10, y0 - 6, TV - 2, "asp+scarab"));
        gs.add(g(10, y0 - 6, TV - 2, "brimstone_spider+coolmar_spider"));
        gs.add(g(3, y0 + 21, TV, "pyre_warden+ember"));
        gs.add(tunnelGarrison(p, 30, "deep_crawler+nethermite"));
        gs.add(tunnelGarrison(p, 112, "spinout+salamander"));
        gs.add(tunnelGarrison(p, 190, "wight+frost"));
        gs.add(tunnelGarrison(p, sampleAt(qOf, coil0, neck0, 0.08), "wither_skeleton+zombie_pigman"));
        gs.add(tunnelGarrison(p, p.bulges[0] + 5, "mummy+charred_ghoul"));
        gs.add(tunnelGarrison(p, p.bulges[1] - 4, "spore+spore_creeper+mogus"));
        gs.add(tunnelGarrison(p, p.bulges[2] + 4, "brimstone_spider+asp"));
        gs.add(tunnelGarrison(p, sampleAt(qOf, coil0, neck0, 0.9), "!pigman_berserker+brute"));
        String[] shrinePacks = {"hellhound+dread_rider", "magma_cube+magma_hulk", "infernal_knight+ashbone_archer", "lost_soul+shade+soul_wraith"};
        for (int k = 0; k < 4; k++) {
            int i = p.shrines[k];
            double rr = Math.hypot(p.u[i], p.v[i]);
            int lvl = level(p.fl[i]);
            gs.add(g((int) Math.round(SHRINE_DIR[k][0] * (rr + 10) + SHRINE_DIR[k][1] * 2), lvl, (int) Math.round(SHRINE_DIR[k][1] * (rr + 10) - SHRINE_DIR[k][0] * 2), shrinePacks[k]));
            gs.add(g((int) Math.round(SHRINE_DIR[k][0] * (rr + 34)), lvl + 6, (int) Math.round(SHRINE_DIR[k][1] * (rr + 34)), k % 2 == 0 ? "ghast+ghastling" : "ghastling+blaze"));
        }
        gs.add(g(8, y0 + LAIR + 1, HB - 6, "asp+scarab"));
        gs.add(g(41, gy(s, p.rot, 41, 12), 12, "scarab+nethermite"));
        gs.add(g(94, gy(s, p.rot, 94, -30), -30, "charred_ghoul+mummy"));
        gs.add(g(-72, gy(s, p.rot, -72, 70), 70, "magma_cube+salamander"));
        gs.add(g(-60, y0 + 42, -60, "ghast+ghastling"));
        gs.add(g(26, y0 + 12, 26, "ghastling+ghastling"));
        gs.add(g(-24, y0 + 10, 30, "ghastling+blaze"));
        p.garrisons = complete(gs);
        return p;
    }

    /** The walking level on the cavern floor as it lies (a garrison's y there). */
    private static int gy(Colossi.Site s, int rot, int u, int v) { return s.floorAt((int) Math.round(wxd(s, rot, u, v)), (int) Math.round(wzd(s, rot, u, v))) + 1; }

    /** The first coil sample at share q of the coil. */
    private static int sampleAt(double[] qOf, int coil0, int neck0, double q) {
        for (int i = coil0; i < neck0; i++) if (qOf[i] >= q) return i;
        return neck0 - 1;
    }

    /** The walking level (a garrison's y) at a tunnel floor S. */
    static int level(double S) { double s2 = Math.round(S * 2) / 2.0; int n = (int) Math.floor(s2); return s2 > n ? n + 1 : n; }

    private static Garrison tunnelGarrison(Plan p, int i, String pack) { return g((int) Math.round(p.u[i]), level(p.fl[i]), (int) Math.round(p.v[i]), pack); }

    // ---- drawing ------------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        pillar(f, s);
        eggField(f, s);
        skin(f, s, p);
        ribs(f, s);
        pools(f, s);
        temple(f, s);
        rattle(f, s);
        hood(f, s);
        body(f, s, p);
        tailGate(f, s);
        spines(f, s, p);
        for (int k = 0; k < p.shrines.length; k++) shrine(f, s, p, k);
        for (int dr : p.doors) sideDoor(f, s, p, dr);
        chambers(f, s, p);
        pits(f, s, p);
        head(f, s);
        lair(f, s);
        spiral(f, s);
        garrisons(f, p.garrisons);
    }

    // ---- outer works: the shed skin, the bones of her prey, pools of venom ------------------------------------------------------
    /** Her shed skin across the floor: a ghostly tube of pale glass and bone-white scales, torn here and there. */
    private void skin(Draw.Frame f, Colossi.Site s, Plan p) {
        if (cols(f, -4, 54, 116, 122) == null) return;
        int g = s.y + 2;
        Draw.Mat shell = (x, y, z) -> {
            if (y < g) return -1;
            double q = Draw.rnd(x, y, z, s.salt + 801);
            if (q < 0.13) return -1;                                     // torn
            int band = Math.floorMod(x + z + y * 2, 7);
            return band == 0 ? T_WHITE : q < 0.55 ? b(95, 0) : b(95, 8);
        };
        Draw.Mat hollow = (x, y, z) -> y < g ? -1 : 0;
        double[][] k = p.skin;
        for (int i = 0; i + 1 < k.length; i++) f.line(k[i][0], k[i][1], k[i][2], k[i + 1][0], k[i + 1][1], k[i + 1][2], 6.5, shell);
        for (int i = 0; i + 1 < k.length; i++) f.line(k[i][0], k[i][1], k[i][2], k[i + 1][0], k[i + 1][1], k[i + 1][2], 5.5, hollow);
        f.chest((int) Math.round(k[k.length / 2][0]), g, (int) Math.round(k[k.length / 2][2]), Draw.NORTH, LOOT);
    }

    /** The ribcages of what she ate: arches of bone over a spine. */
    private void ribs(Draw.Frame f, Colossi.Site s) {
        int g = s.y + 1;
        int[][] cages = {{95, -40, 0}, {-80, 72, 1}};
        for (int[] cg : cages) {
            int cu = cg[0], cv = cg[1];
            boolean alongV = cg[2] == 0;
            if (cols(f, cu - 22, cv - 22, cu + 22, cv + 22) == null) continue;
            for (int k = -18; k <= 18; k++) {
                int u = alongV ? cu : cu + k, v = alongV ? cv + k : cv;
                f.box(u, g, v, u, g + 1, v, Draw.of(BONE));
            }
            for (int k = -15; k <= 15; k += 5) {
                double hw = 9 - Math.abs(k) * 0.25;
                double ax = alongV ? f.xd(cu - hw, cv + k) : f.xd(cu + k, cv - hw), az = alongV ? f.zd(cu - hw, cv + k) : f.zd(cu + k, cv - hw);
                double bx = alongV ? f.xd(cu + hw, cv + k) : f.xd(cu + k, cv + hw), bz = alongV ? f.zd(cu + hw, cv + k) : f.zd(cu + k, cv + hw);
                f.d.arch(ax, az, bx, bz, g, 11 - Math.abs(k) * 0.3, 0.6, 1.5, Draw.of(BONE));
            }
            int hu = alongV ? cu : cu - 20, hv = alongV ? cv - 20 : cv;
            f.box(hu - 2, g, hv - 2, hu + 2, g + 3, hv + 2, Draw.of(BONE));
            if (alongV) { f.set(hu - 1, g + 2, hv - 2, C_BLACK); f.set(hu + 1, g + 2, hv - 2, C_BLACK); }
            else { f.set(hu - 2, g + 2, hv - 1, C_BLACK); f.set(hu - 2, g + 2, hv + 1, C_BLACK); }
        }
    }

    /** Pools of glowing venom in rims of green stone. */
    private void pools(Draw.Frame f, Colossi.Site s) {
        int y = s.y + 2;
        int[][] at = {{58, 36, 6}, {-38, 98, 5}, {112, -86, 7}};
        for (int[] q : at) {
            if (cols(f, q[0] - 9, q[1] - 9, q[0] + 9, q[1] + 9) == null) continue;
            f.cyl(q[0], q[1], q[2] + 1.2, y - 4, y - 1, Draw.of(NBRICK));
            f.ring(q[0], q[1], q[2] + 1.2, 1.2, y, y, Draw.of(T_GREEN));
            f.disk(q[0], q[1], q[2], y - 1, Draw.of(SEA));
            f.disk(q[0], q[1], q[2], y, Draw.of(G_LIME));
            f.cyl(q[0], q[1], q[2] + 1.2, y + 1, y + 3, Draw.AIR);
        }
    }

    /** The cobra's hood, spread behind the head: a fan of scales with two great eye-spots on its back. */
    private void hood(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (cols(f, -30, HB + 1, 30, HB + 4) == null) return;
        for (int y = y0 + 46; y <= y0 + 78; y++) {
            double t = (y - (y0 + 46)) / 32.0, w = 27 * Math.sin(Math.PI * t);
            for (int u = (int) -Math.floor(w); u <= (int) Math.floor(w); u++) for (int v = HB + 2; v <= HB + 3; v++) {
                double eye = Math.hypot(Math.abs(u) - 13, y - (y0 + 62));
                int val;
                if (v == HB + 3 && eye < 3.6) val = eye < 1.8 ? C_BLACK : T_WHITE;
                else if (Math.abs(u) > w - 1.5) val = T_BLACK;
                else val = (Math.floorMod(u + y, 5) == 0 || Math.floorMod(u - y, 5) == 0) ? T_BLACK : (y + u & 1) == 0 ? T_GREEN : PRISM_DK;
                f.set(u, y, v, val);
            }
        }
    }

    /** A side door: a way in through the tail's flank where it lies on the ground. */
    private void sideDoor(Draw.Frame f, Colossi.Site s, Plan p, int i) {
        Draw d = f.d;
        if (p.x[i] + 14 < d.x0 || p.x[i] - 14 > d.x1 || p.z[i] + 14 < d.z0 || p.z[i] - 14 > d.z1) return;
        int lvl = level(p.fl[i]);
        for (double a = -1.2; a <= 1.2; a += 0.4) for (double w = p.ri[i] - 1; w <= p.r[i] + 2.5; w += 0.4) {
            int x = (int) Math.round(p.x[i] + p.tx[i] * a + p.sx[i] * w), z = (int) Math.round(p.z[i] + p.tz[i] * a + p.sz[i] * w);
            d.set(x, lvl - 1, z, NBRICK);
            for (int y = lvl; y <= lvl + 2; y++) d.set(x, y, z, 0);
        }
        for (double a = -2.2; a <= 2.2; a += 4.4) {
            int x = (int) Math.round(p.x[i] + p.tx[i] * a + p.sx[i] * (p.r[i] + 0.5)), z = (int) Math.round(p.z[i] + p.tz[i] * a + p.sz[i] * (p.r[i] + 0.5));
            for (int y = lvl; y <= lvl + 3; y++) d.set(x, y, z, BONE);
        }
    }

    /**
     * The Queen's Spiral: from the Crown Vault a covered bridge to the pillar and a ladder down its heart to a door at its
     * foot, sealed like the vault until the Queen falls (the way home for whoever robs her).
     */
    private void spiral(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, vf = y0 + LAIR + 12;                   // the vault's floor block
        if (cols(f, -4, HB - 6, 4, -6) == null && cols(f, -4, -36, 4, -6) == null) return;
        // the bridge from the vault's south door to the pillar
        f.box(-2, vf, HB - 4, 2, vf + 4, SPIRAL_V - 2, Draw.of(PRISM_DK));
        f.box(-1, vf + 1, HB - 4, 1, vf + 3, SPIRAL_V - 2, Draw.AIR);
        f.box(-1, vf, HB - 4, 1, vf, SPIRAL_V - 2, Draw.of(NBRICK));
        for (int v = HB; v <= SPIRAL_V - 4; v += 4) { f.set(-2, vf + 2, v, G_LIME); f.set(2, vf + 2, v, G_LIME); }
        // the shaft and its ladder, the passage out at the foot
        int g = y0 + 1;
        f.box(-2, g - 1, SPIRAL_V - 2, 2, vf + 4, SPIRAL_V + 2, Draw.of(NBRICK));
        f.box(-1, g + 1, SPIRAL_V - 1, 1, vf + 3, SPIRAL_V + 1, Draw.AIR);
        f.box(-1, g, SPIRAL_V - 1, 1, g, SPIRAL_V + 1, Draw.of(PRISM_DK));
        f.box(-1, vf, SPIRAL_V - 1, 1, vf, SPIRAL_V + 1, Draw.of(NBRICK));           // the landing at its head, a hatch to the ladder
        for (int y = g + 1; y <= vf + 1; y++) f.set(0, y, SPIRAL_V + 1, b(65, f.facing(Draw.NORTH)));
        for (int y = g + 6; y <= vf; y += 8) f.set(0, y, SPIRAL_V - 2, GLOW);
        f.box(-1, g + 1, -34, 1, g + 3, SPIRAL_V - 2, Draw.AIR);
        f.box(-1, g, -34, 1, g, SPIRAL_V - 2, Draw.of(NBRICK));
        f.box(-1, g + 1, -32, 1, g + 3, -32, Draw.of(SEAL));
        f.box(-2, g + 4, -33, 2, g + 4, -33, Draw.of(BONE));
        f.wallSign(2, g + 2, -33, Draw.NORTH, "THE QUEEN'S", "SPIRAL", "Opens only", "when she dies");
        f.set(2, g + 2, -32, PRISM_DK);
    }

    // ---- the pillar --------------------------------------------------------------------------------------------------------------
    /** Basalt and magma from below the floor up into the roof, flaring at its foot and its capital. */
    private void pillar(Draw.Frame f, Colossi.Site s) {
        int[] c = cols(f, -40, -40, 40, 40);
        if (c == null) return;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), rr = Math.hypot(u, v);
            if (rr > 38) continue;
            int top = Math.min(122, s.ceilAt(x, z) + 2);
            for (int y = s.y - 6; y <= top; y++) {
                double rp = pillarR(y - s.y);
                if (rr < rp - 2.2) { f.d.set(x, y, z, basalt(x, y, z, s.salt, false)); continue; }
                if (rr > rp + 2.2) continue;
                double rough = rp + 1.6 * Draw.noise(x + y * 0.7, z - y * 0.6, 7, s.salt + 3) + 0.6 * Draw.noise(x - y * 0.3, z + y * 0.8, 3, s.salt + 4);
                if (rr <= rough) f.d.set(x, y, z, basalt(x, y, z, s.salt, rr > rough - 1.2));
            }
        }
    }

    private static int basalt(int x, int y, int z, int salt, boolean face) {
        if (face) {
            double vein = Draw.noise(x * 0.9 + y * 0.05, z * 0.9 - y * 0.04, 5, salt + 11);
            if (Math.abs(vein) < 0.1) return MAGMA;
            if (Math.floorMod(y + (int) (Draw.noise(x, z, 9, salt + 12) * 3), 9) == 0) return T_GREY;
        }
        double q = Draw.rnd(x, y, z, salt + 13);
        return q < 0.62 ? T_BLACK : q < 0.78 ? C_BLACK : q < 0.9 ? OBSIDIAN : q < 0.96 ? T_GREY : MAGMA;
    }

    // ---- the body ------------------------------------------------------------------------------------------------------------------
    /**
     * The serpent's body: every block near its spine takes the nearest of the spine's samples (by distance to that sample's
     * sphere), then becomes tunnel (air above a level floor), lining, or scales, after where it lies round the body.
     */
    private void body(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw d = f.d;
        int[] cand = new int[p.n];
        int m = 0;
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
        for (int i = 0; i < p.n; i++) {
            double rr = p.r[i] + 1;
            if (p.x[i] + rr < d.x0 || p.x[i] - rr > d.x1 || p.z[i] + rr < d.z0 || p.z[i] - rr > d.z1) continue;
            cand[m++] = i;
            minX = Math.min(minX, p.x[i] - rr); maxX = Math.max(maxX, p.x[i] + rr);
            minZ = Math.min(minZ, p.z[i] - rr); maxZ = Math.max(maxZ, p.z[i] + rr);
            minY = Math.min(minY, p.y[i] - rr); maxY = Math.max(maxY, p.y[i] + rr);
        }
        if (m == 0) return;
        int X0 = Math.max(d.x0, (int) Math.floor(minX)), X1 = Math.min(d.x1, (int) Math.ceil(maxX));
        int Z0 = Math.max(d.z0, (int) Math.floor(minZ)), Z1 = Math.min(d.z1, (int) Math.ceil(maxZ));
        int Y0 = Math.max(24, (int) Math.floor(minY)), Y1 = Math.min(122, (int) Math.ceil(maxY));
        if (X0 > X1 || Z0 > Z1 || Y0 > Y1) return;
        int NZ = Z1 - Z0 + 1, NY = Y1 - Y0 + 1, size = (X1 - X0 + 1) * NZ * NY;
        float[] best = BEST.get();
        if (best.length < size) { best = new float[size]; BEST.set(best); WHO.set(new int[size]); }
        int[] who = WHO.get();
        Arrays.fill(best, 0, size, 9f);
        for (int k = 0; k < m; k++) {
            int i = cand[k];
            double cx = p.x[i], cy = p.y[i], cz = p.z[i], rr = p.r[i] + 0.35;
            int xa = Math.max(X0, (int) Math.floor(cx - rr)), xb = Math.min(X1, (int) Math.ceil(cx + rr));
            int za = Math.max(Z0, (int) Math.floor(cz - rr)), zb = Math.min(Z1, (int) Math.ceil(cz + rr));
            int ya = Math.max(Y0, (int) Math.floor(cy - rr)), yb = Math.min(Y1, (int) Math.ceil(cy + rr));
            for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
                double h2 = sq(x - cx) + sq(z - cz);
                if (h2 > rr * rr) continue;
                int base = ((x - X0) * NZ + (z - Z0)) * NY - Y0;
                for (int y = ya; y <= yb; y++) {
                    double dd = Math.sqrt(h2 + sq(y - cy)) - p.r[i];
                    if (dd > 0.35) continue;
                    int idx = base + y;
                    if (dd < best[idx]) { best[idx] = (float) dd; who[idx] = i; }
                }
            }
        }
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int base = ((x - X0) * NZ + (z - Z0)) * NY - Y0;
            for (int y = Y0; y <= Y1; y++) {
                int idx = base + y;
                if (best[idx] > 0.35f) continue;
                int i = who[idx];
                d.set(x, y, z, block(p, i, x, y, z, s.salt));
            }
        }
    }

    private static final ThreadLocal<float[]> BEST = ThreadLocal.withInitial(() -> new float[0]);
    private static final ThreadLocal<int[]> WHO = ThreadLocal.withInitial(() -> new int[0]);

    /** What the body is at a block whose nearest spine sample is i. */
    private static int block(Plan p, int i, int x, int y, int z, int salt) {
        double dx = x - p.x[i], dy = y - p.y[i], dz = z - p.z[i], dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double side = dx * p.sx[i] + dz * p.sz[i], up = dx * p.ux[i] + dy * p.uy[i] + dz * p.uz[i];
        double alpha = Math.atan2(up, side);                 // 0 outward, +pi/2 the back, -pi/2 the belly
        double s2 = Math.round(p.fl[i] * 2) / 2.0;
        int nn = (int) Math.floor(s2);
        boolean half = s2 > nn;
        int tg = p.tag[i];
        if (dist < p.ri[i]) {                                // the tunnel
            if (y < nn - 1) return NBRICK;
            if (y == nn - 1) {
                if ((tg & T_TRAP) != 0) return TRAP_FLOOR;
                if ((tg & T_VENT) != 0 && ((x + z) & 1) == 0) return MAGMA;
                return Math.abs(side) < 1.0 ? RNBRICK : NBRICK;
            }
            if (y == nn && half) return NB_SLAB;
            return 0;
        }
        if ((tg & T_WINDOW) != 0 && Math.abs(alpha) < 0.42 && y >= nn + 1 && y <= nn + 3) return G_LIME;
        if (dist < p.ri[i] + 1.05) {                         // the lining: ribs and the glow of its lamps
            if (i % 8 == 4 && (Math.abs(alpha - 0.8) < 0.28 || Math.abs(alpha - 2.35) < 0.28)) return GLOW;
            return i % 6 == 0 ? RNBRICK : NBRICK;
        }
        return scale(i, alpha, p.r[i]);
    }

    /** The scales: a diamond pattern on the flanks, plates on the belly, a banded stripe down the back. */
    private static int scale(int i, double alpha, double r) {
        double back = Math.abs(alpha - Math.PI / 2), belly = Math.abs(alpha + Math.PI / 2);
        if (belly < 0.8) return i % 3 == 0 ? PRISM_BR : T_LGREY;
        if (back < 0.3) return (i / 3) % 2 == 0 ? T_BLACK : C_GREEN;
        double p1 = i * 0.42 + alpha * r * 0.42, p2 = i * 0.42 - alpha * r * 0.42;
        double f1 = p1 - Math.floor(p1), f2 = p2 - Math.floor(p2);
        if (f1 < 0.15 || f2 < 0.15) return T_BLACK;
        int h = Draw.hash((int) Math.floor(p1), (int) Math.floor(p2), 7, 991);
        int k = (h >>> 8) & 7;
        return k < 4 ? T_GREEN : k < 6 ? PRISM_DK : k < 7 ? T_LIME : PRISM;
    }

    /** Bone spines along the back. */
    private void spines(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int i = 16; i < p.n - 6; i += 4) {
            double tx = p.x[i] + p.ux[i] * (p.r[i] - 0.3), ty = p.y[i] + p.uy[i] * (p.r[i] - 0.3), tz = p.z[i] + p.uz[i] * (p.r[i] - 0.3);
            int x = (int) Math.round(tx), z = (int) Math.round(tz), y = (int) Math.floor(ty);
            if (!f.d.in(x, z)) continue;
            int h = i % 12 == 0 ? 3 : 2;
            for (int k = 1; k <= h; k++) f.d.set(x, y + k, z, BONE);
        }
    }

    /** The falling floors' pits: three blocks down to embers under the crumbling stones. */
    private void pits(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int[] t : p.traps) {
            int nn = (int) Math.floor(p.fl[t[0]]);
            for (int i = t[0]; i <= t[1]; i++) {
                double cx = p.x[i], cz = p.z[i];
                for (int x = (int) Math.floor(cx - 3); x <= (int) Math.ceil(cx + 3); x++) for (int z = (int) Math.floor(cz - 3); z <= (int) Math.ceil(cz + 3); z++) {
                    if (!f.d.in(x, z)) continue;
                    double along = (x - cx) * p.tx[i] + (z - cz) * p.tz[i], across = (x - cx) * p.sx[i] + (z - cz) * p.sz[i];
                    if (Math.abs(along) > 0.6 || Math.abs(across) > 2.6) continue;
                    f.d.set(x, nn - 2, z, 0);
                    f.d.set(x, nn - 3, z, 0);
                    f.d.set(x, nn - 4, z, MAGMA);
                    f.d.set(x, nn - 1, z, TRAP_FLOOR);
                }
            }
        }
    }

    // ---- the chambers inside the body, the shrines on it ----------------------------------------------------------------------
    /** World block at a sample, moved along its tangent and its side. */
    private static int[] at(Plan p, int i, double along, double side) {
        return new int[]{(int) Math.round(p.x[i] + p.tx[i] * along + p.sx[i] * side), (int) Math.round(p.z[i] + p.tz[i] * along + p.sz[i] * side)};
    }

    private static int facing(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Draw.EAST : Draw.WEST;
        return dz > 0 ? Draw.SOUTH : Draw.NORTH;
    }

    /** The Shedding Hall, the Venom Cistern and the Brood Gallery where the body swells. */
    private void chambers(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw d = f.d;
        String[][] names = {{"THE SHEDDING", "HALL", "Here she leaves", "her old skins"}, {"THE VENOM", "CISTERN", "Do not breathe", "too deep"},
            {"THE BROOD", "GALLERY", "Her children", "hunger"}};
        for (int k = 0; k < p.bulges.length; k++) {
            int i = p.bulges[k];
            if (p.x[i] + 14 < d.x0 || p.x[i] - 14 > d.x1 || p.z[i] + 14 < d.z0 || p.z[i] - 14 > d.z1) continue;
            int lvl = level(p.fl[i]);
            // a pillar of the body's own stone carries the chamber's sign, a chest at its foot
            int[] post = at(p, i - 6, 0, 5.2);
            d.box(post[0], lvl, post[1], post[0], lvl + 2, post[1], NBRICK);
            int in = facing(-p.sx[i - 6], -p.sz[i - 6]);
            int sx = post[0] + (in == Draw.EAST ? 1 : in == Draw.WEST ? -1 : 0), sz = post[1] + (in == Draw.SOUTH ? 1 : in == Draw.NORTH ? -1 : 0);
            d.sign(sx, lvl + 1, sz, b(68, in), names[k]);
            int[] ch = at(p, i + 6, 0, -4.4);
            d.chest(ch[0], lvl, ch[1], facing(p.sx[i], p.sz[i]), k == 2 ? RICH : LOOT);
            if (k == 0) {                       // shed skins draped along the walls
                for (int a = -5; a <= 5; a += 2) { int[] q = at(p, i + a, 0, 5.0); d.box(q[0], lvl, q[1], q[0], lvl + 3, q[1], T_WHITE); }
            } else if (k == 1) {                // a basin of glowing venom
                for (int a = -3; a <= 3; a++) for (int w = -2; w <= 2; w++) {
                    int[] q = at(p, i + a, 0, w);
                    d.set(q[0], lvl - 1, q[1], G_LIME);
                    d.set(q[0], lvl - 2, q[1], SEA);
                }
                int[] c2 = at(p, i - 5, 0, -4.4);
                d.chest(c2[0], lvl, c2[1], facing(p.sx[i], p.sz[i]), LOOT);
            } else {                            // eggs and webs, a spawner of her brood
                for (int a = -4; a <= 4; a += 4) for (int w = -3; w <= 3; w += 6) {
                    int[] q = at(p, i + a, 0, w);
                    d.box(q[0], lvl, q[1], q[0], lvl + 1, q[1], SLIME);
                    int[] wq = at(p, i + a + 1, 0, w * 0.6);
                    d.set(wq[0], lvl + 4, wq[1], WEB);
                }
                int[] sp = at(p, i, 0, 0);
                d.spawner(sp[0], lvl, sp[1], "brimstone_spider");
            }
        }
    }

    /** A coil shrine: a door out through the flank to a platform with an idol, braziers and an offering. */
    private void shrine(Draw.Frame f, Colossi.Site s, Plan p, int k) {
        int i = p.shrines[k];
        int du = SHRINE_DIR[k][0], dv = SHRINE_DIR[k][1], wu = -dv, wv = du;
        double rr = Math.hypot(p.u[i], p.v[i]);
        int lvl = level(p.fl[i]), R = (int) Math.round(rr);
        if (cols(f, du * R - 20, dv * R - 20, du * R + 20, dv * R + 20) == null) return;
        int ou = (int) Math.round(du == 0 ? p.u[i] : 0), ov = (int) Math.round(dv == 0 ? p.v[i] : 0);
        // the door through the flank
        for (int t = R + 1; t <= R + 8; t++) for (int w = -1; w <= 1; w++) {
            int u = ou + du * t + wu * w, v = ov + dv * t + wv * w;
            f.set(u, lvl - 1, v, NBRICK);
            f.box(u, lvl, v, u, lvl + 2, v, Draw.AIR);
        }
        // the platform
        for (int t = R + 6; t <= R + 15; t++) for (int w = -5; w <= 5; w++) {
            int u = ou + du * t + wu * w, v = ov + dv * t + wv * w;
            boolean rim = t == R + 15 || Math.abs(w) == 5;
            f.set(u, lvl - 1, v, rim ? PRISM_DK : ((t + w) & 1) == 0 ? PRISM_BR : PRISM);
            f.set(u, lvl - 2, v, NBRICK);
            if (t < R + 12) f.set(u, lvl - 3, v, NBRICK);
            f.box(u, lvl, v, u, lvl + 4, v, Draw.AIR);
            if (rim && t > R + 7) f.set(u, lvl, v, NB_FENCE);
        }
        // the idol: a coiled serpent of green stone with burning eyes
        int iu = ou + du * (R + 12), iv = ov + dv * (R + 12);
        f.box(iu, lvl, iv, iu, lvl + 3, iv, Draw.of(T_GREEN));
        for (int a = -1; a <= 1; a++) for (int b2 = -1; b2 <= 1; b2++) if (a != 0 || b2 != 0) f.set(iu + a, lvl, iv + b2, (a + b2 & 1) == 0 ? T_LIME : T_GREEN);
        f.set(iu - du, lvl + 4, iv - dv, T_LIME);
        f.set(iu, lvl + 4, iv, T_GREEN);
        f.set(iu - du + wu, lvl + 4, iv - dv + wv, GLOW);
        f.set(iu - du - wu, lvl + 4, iv - dv - wv, GLOW);
        f.wallSign(iu - du, lvl + 2, iv - dv, facingLocal(-du, -dv), "SHRINE OF", SHRINES[k].toUpperCase().length() > 15 ? SHRINES[k] : SHRINES[k].toUpperCase(), "Bow, and climb", "on to the head");
        for (int w = -3; w <= 3; w += 6) {
            int bu = ou + du * (R + 9) + wu * w, bv = ov + dv * (R + 9) + wv * w;
            f.set(bu, lvl, bv, PRISM_BR); f.set(bu, lvl + 1, bv, RACK); f.set(bu, lvl + 2, bv, FIRE);
        }
        f.chest(ou + du * (R + 13) + wu * 3, lvl, ov + dv * (R + 13) + wv * 3, facingLocal(-du, -dv), k % 2 == 1 ? RICH : LOOT);
    }

    private static int facingLocal(int du, int dv) { return du > 0 ? Draw.EAST : du < 0 ? Draw.WEST : dv > 0 ? Draw.SOUTH : Draw.NORTH; }

    // ---- the temple --------------------------------------------------------------------------------------------------------------
    /** The Serpent Temple: four tiers, the grand stair between serpent balustrades, the hall, the egg chambers, the shrine. */
    private void temple(Draw.Frame f, Colossi.Site s) {
        int g = s.y + 1;
        if (cols(f, -30, TV - 38, 30, TV + 18) == null) return;
        f.box(-23, 24, TV - 17, 23, g - 1, TV + 17, Draw.of(NBRICK));
        // the tiers, each with its trim and a frieze of serpents
        for (int k = 0; k < 4; k++) {
            int wu = 22 - 4 * k, wv = 16 - 4 * k, ya = g + 5 * k, yb = ya + 4;
            final int fwu = wu, fwv = wv, fya = ya;
            f.box(-wu, ya, TV - wv, wu, yb, TV + wv, (x, y, z) -> {
                double u = lu(f, x, z), v = lv(f, x, z) - TV;
                boolean face = Math.abs(Math.abs(u) - fwu) < 0.5 || Math.abs(Math.abs(v) - fwv) < 0.5;
                if (face && y == fya + 2 + (int) Math.round(Math.sin((u + v) * 0.55))) return T_GREEN;
                if (y == fya + 4) return PRISM_BR;
                return Draw.rnd(x, y, z, 731) < 0.3 ? NBRICK : T_BLACK;
            });
            for (int a = -1; a <= 1; a += 2) for (int c = -1; c <= 1; c += 2) f.set(a * wu, yb + 1, TV + c * wv, GLOW);
        }
        // the Priests' Hall
        int hb = g + 1, ht = g + 9;
        f.box(-17, g, TV - 11, 17, ht, TV + 11, Draw.of(NBRICK));
        f.box(-16, hb, TV - 10, 16, ht - 1, TV + 10, Draw.AIR);
        f.box(-16, g, TV - 10, 16, g, TV + 10, (x, y, z) -> ((x + z) & 1) == 0 ? PRISM_BR : PRISM_DK);
        f.box(-16, ht, TV - 10, 16, ht, TV + 10, Draw.mix(NBRICK, SEA, 0.04, 733));
        for (int u = -8; u <= 8; u += 16) for (int v = TV - 8; v <= TV + 8; v += 4) {
            f.box(u, hb, v, u, ht - 1, v, Draw.of(PRISM));
            f.set(u, ht - 1, v, SEA);
        }
        f.box(-1, g, TV - 10, 1, g, TV + 10, Draw.of(GLAZED));
        // doors east and west (the grand stair climbs the north face to the shrine)
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 17, hb, TV - 2, side * 23, hb + 3, TV + 2, Draw.AIR);
            f.box(side * 17, g, TV - 2, side * 23, g, TV + 2, Draw.of(PRISM_BR));
        }
        // the egg chambers below, and their stairs
        for (int side = -1; side <= 1; side += 2) {
            int ua = side * 4, ub = side * 16;
            f.box(Math.min(ua, ub) - 1, s.y - 8, TV - 10, Math.max(ua, ub) + 1, g - 1, TV + 8, Draw.of(NBRICK));
            f.box(Math.min(ua, ub), s.y - 6, TV - 9, Math.max(ua, ub), g - 2, TV + 7, Draw.AIR);
            f.box(Math.min(ua, ub), s.y - 7, TV - 9, Math.max(ua, ub), s.y - 7, TV + 7, (x, y, z) -> Draw.rnd(x, y, z, 741) < 0.3 ? MOSS : NBRICK);
            int su = side * 14;
            for (int k = 0; k <= 8; k++) {
                int v = TV + 8 - k, y = g - k;
                f.box(su - 1, y + 1, v, su + 1, y + 4, v, Draw.AIR);
                for (int a = -1; a <= 1; a++) f.set(su + a, y, v, f.stair(NB_STAIR, 2, false));
            }
            for (int v = TV; v <= TV + 8; v++) { f.set(su - 2, hb, v, NB_FENCE); f.set(su + 2, hb, v, NB_FENCE); }
            // eggs in nests of bone and web
            for (int e = 0; e < 5; e++) {
                int eu = side * (6 + (e * 7) % 9), ev = TV - 7 + e * 3;
                f.box(eu, s.y - 6, ev, eu + 1, s.y - 4, ev + 1, Draw.of(SLIME));
                f.set(eu - 1, s.y - 6, ev, BONE);
                f.set(eu + 2, s.y - 6, ev + 1, WEB);
            }
            f.set(side * 10, g - 2, TV - 4, GLOW);
            f.set(side * 10, g - 2, TV + 4, GLOW);
            f.spawner(side * 12, s.y - 6, TV - 7, "asp");
            f.chest(side * 15, s.y - 6, TV - 8, Draw.SOUTH, side < 0 ? LOOT : RICH);
        }
        f.wallSign(-6, s.y - 4, TV - 9, Draw.SOUTH, "THE EGG", "CHAMBERS", "Tread softly.", "They hatch.");
        // the grand stair up the north face, its serpent balustrades and their heads
        for (int k = 0; k <= 19; k++) {
            int v = TV - 25 + k, y = g + k;
            f.box(-4, g, v, 4, y - 1, v, Draw.of(NBRICK));
            for (int u = -4; u <= 4; u++) f.set(u, y, v, f.stair(NB_STAIR, 2, false));
            f.box(-4, y + 1, v, 4, y + 5, v, Draw.AIR);
            for (int side = -1; side <= 1; side += 2) {
                f.box(side * 5, g, v, side * 5, y + 1, v, Draw.of(NBRICK));
                f.set(side * 5, y + 1, v, k % 3 == 0 ? T_LIME : T_GREEN);
                f.set(side * 5, y + 2, v, (k & 1) == 0 ? T_GREEN : PRISM_DK);
            }
        }
        f.box(-5, g + 19, TV - 5, 5, g + 19, TV - 5, Draw.of(NBRICK));
        f.box(-4, g + 20, TV - 5, 4, g + 24, TV - 5, Draw.AIR);
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 5 - 1, g, TV - 28, side * 5 + 1, g + 3, TV - 26, Draw.of(T_GREEN));
            f.box(side * 5 - 1, g + 1, TV - 29, side * 5 + 1, g + 1, TV - 29, Draw.of(BONE));
            f.set(side * 5 - 1, g + 3, TV - 28, GLOW); f.set(side * 5 + 1, g + 3, TV - 28, GLOW);
            f.box(side * 5 - 1, g + 2, TV - 29, side * 5 + 1, g + 2, TV - 29, Draw.AIR);
        }
        // the plaza, its rearing cobras and the stele that names the place
        f.box(-14, g - 1, TV - 40, 14, g, TV - 26, (x, y, z) -> y < g ? NBRICK : ((x + z) & 3) == 0 ? PRISM_DK : T_BLACK);
        f.box(-14, g + 1, TV - 40, 14, g + 6, TV - 30, Draw.AIR);
        for (int side = -1; side <= 1; side += 2) cobra(f, side * 11, TV - 34, g + 1, side);
        f.box(0, g + 1, TV - 32, 0, g + 3, TV - 32, Draw.of(PRISM_DK));
        f.set(0, g + 4, TV - 32, GLOW);
        f.wallSign(0, g + 2, TV - 33, Draw.NORTH, "THE COIL OF THE", "WORLD SERPENT", "Doors east and", "west. Her tail.");
        // the shrine on the crown
        int sb = g + 20;
        f.box(-6, sb - 1, TV - 3, 6, sb + 5, TV + 3, Draw.of(PRISM_DK));
        f.box(-5, sb, TV - 2, 5, sb + 4, TV + 2, Draw.AIR);
        f.box(-5, sb - 1, TV - 2, 5, sb - 1, TV + 2, Draw.of(GLAZED_LIME));
        f.box(-1, sb, TV - 3, 1, sb + 2, TV - 3, Draw.AIR);
        for (int k = 0; k < 4; k++) f.box(-6 + k, sb + 5 + k, TV - 3 + k, 6 - k, sb + 5 + k, TV + 3 - k, Draw.of(k == 3 ? GLOW : PRISM_DK));
        for (int u = -4; u <= 4; u += 8) { f.set(u, sb, TV + 1, PRISM_BR); f.set(u, sb + 1, TV + 1, RACK); f.set(u, sb + 2, TV + 1, FIRE); }
        f.box(-1, sb, TV + 2, 1, sb + 1, TV + 2, Draw.of(T_GREEN));
        f.set(0, sb + 2, TV + 2, T_LIME);
        f.chest(2, sb, TV + 2, Draw.NORTH, RICH);
        f.set(0, sb + 3, TV, GLOW);
        f.chest(-15, hb, TV - 9, Draw.SOUTH, LOOT);
        f.chest(15, hb, TV + 9, Draw.NORTH, LOOT);
        f.chest(-15, hb, TV + 9, Draw.NORTH, RICH);
        f.wallSign(4, hb + 2, TV - 10, Draw.SOUTH, "THE PRIESTS'", "HALL", "They fed her", "their own");
        f.wallSign(-4, hb + 2, TV - 10, Draw.SOUTH, "South: her tail.", "Climb her coils", "to the Queen.", "Do not look up.");
    }

    /** A rearing cobra: a coiled base, its neck, a spread hood and a head with burning eyes, facing north. */
    private void cobra(Draw.Frame f, int u, int v, int y, int side) {
        f.ring(u, v, 3.2, 1.6, y, y + 1, Draw.of(T_GREEN));
        f.box(u - 1, y, v - 1, u + 1, y + 10, v + 1, (x, yy, z) -> (yy & 1) == 0 ? T_GREEN : PRISM_DK);
        for (int yy = y + 6; yy <= y + 12; yy++) {
            int w = yy < y + 9 ? 3 + (yy - y - 6) : 5 - (yy - y - 9);
            f.box(u - w, yy, v + 1, u + w, yy, v + 1, Draw.of(T_GREEN));
            f.set(u, yy, v + 1, T_LIME);
        }
        f.box(u - 1, y + 11, v - 2, u + 1, y + 13, v + 1, Draw.of(T_GREEN));
        f.set(u - 1, y + 12, v - 2, GLOW);
        f.set(u + 1, y + 12, v - 2, GLOW);
        f.set(u, y + 11, v - 3, BONE);
    }

    /** The Tail Gate: where the tail ends in the hall's south wall, an opening into it framed in bone. */
    private void tailGate(Draw.Frame f, Colossi.Site s) {
        int g = s.y + 1;
        if (cols(f, -8, TV + 4, 8, TV + 16) == null) return;
        f.box(-3, g + 1, TV + 6, 3, g + 5, TV + 16, Draw.AIR);
        f.box(-3, g, TV + 6, 3, g, TV + 16, Draw.of(NBRICK));
        for (int k = -4; k <= 4; k++) {
            f.set(k, g + 6, TV + 10, BONE);
            if (Math.abs(k) == 4) f.box(k, g + 1, TV + 10, k, g + 5, TV + 10, Draw.of(BONE));
        }
        f.wallSign(-4, g + 3, TV + 9, Draw.NORTH, "THE TAIL GATE", "Her body is", "your road. It", "climbs far.");
    }

    /** The rattle at the tip of her tail, which curls out of the temple's west side. */
    private void rattle(Draw.Frame f, Colossi.Site s) {
        if (cols(f, -64, TV - 14, -14, TV + 30) == null) return;
        double[][] pts = {{-18, TV - 2}, {-30, TV - 6}, {-42, TV - 2}, {-50, TV + 8}, {-48, TV + 18}, {-40, TV + 22}, {-33, TV + 18}};
        double[] rad = {4.6, 4.2, 3.7, 3.1, 2.5, 2.0, 1.6};
        for (int k = 0; k + 1 < pts.length; k++) {
            double ra = rad[k], rb = rad[k + 1];
            int ka = k;
            f.line(pts[k][0], s.y + 1 + ra * 0.8, pts[k][1], pts[k + 1][0], s.y + 1 + rb * 0.8, pts[k + 1][1], (ra + rb) / 2,
                (x, y, z) -> ka >= 4 ? (((x + y + z) & 1) == 0 ? BONE : T_LGREY) : y > s.y + 1 + ra * 1.2 ? (Draw.rnd(x, y, z, 751) < 0.3 ? T_BLACK : T_GREEN) : PRISM_BR);
        }
    }

    /** The egg field round the pillar's foot: her clutch among bones and shed skin. */
    private void eggField(Draw.Frame f, Colossi.Site s) {
        if (cols(f, -48, -48, 48, 48) == null) return;
        int g = s.y + 1;
        for (int k = 0; k < 22; k++) {
            double a = k * 2.399 + 0.3, rr = 37 + (k * 37 % 11);
            int u = (int) Math.round(Math.cos(a) * rr), v = (int) Math.round(Math.sin(a) * rr);
            if (u < -30 && v < 10 && v > -30) continue;        // where the tail comes in
            int h = 2 + k % 3;
            f.box(u, g, v, u + 1, g + h - 1, v + 1, Draw.of(k % 4 == 0 ? T_WHITE : SLIME));
            f.set(u + 2, g, v, BONE);
            if (k % 3 == 0) f.set(u - 1, g, v + 1, T_LGREY);
        }
        f.chest(41, g + 1, -2, Draw.WEST, LOOT);
    }

    // ---- the head ---------------------------------------------------------------------------------------------------------------
    /** The head's half width at t (0 at its back, 1 at the snout tip). */
    static double headW(double t) { return 18 * (1 - 0.5 * t * t); }
    static double headT(double v) { return (HB - v) / HL; }

    /**
     * The head under the roof, facing north over its temple: a broad skull narrowing to the snout, jaws gaping at the front
     * with bone teeth and two great fangs, glowing slit eyes, a crest of spines; inside, the mouth is the Queen's Lair.
     */
    private void head(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        int[] c = cols(f, -19, HB - HL - 1, 19, HB + 1);
        if (c == null) return;
        int floor = y0 + LAIR;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), t = headT(v);
            if (t < 0 || t > 1) continue;
            double W = headW(t);
            if (Math.abs(u) > W + 0.3) continue;
            double q = Math.min(1, Math.abs(u) / W), round = 1 - Math.sqrt(1 - q * q);
            int top = (int) Math.round(y0 + 74 - 8 * t - round * 8), bottom = (int) Math.round(y0 + 48 + 3 * t + round * 4);
            int palate = (int) Math.round(y0 + 65 - 3 * t);
            boolean mouth = Math.abs(u) < W - 3 && t > 0.06 && t < 0.92, gape = t > 0.6;
            for (int y = bottom; y <= top; y++) {
                int val;
                if ((mouth || gape) && y > floor && y < palate) val = 0;
                else if (mouth && y == floor) val = Math.abs(u) < 1.5 ? RNBRICK : b(35, 14);
                else if (y >= top - 1) val = Math.abs(u) < 1.5 ? T_BLACK : headScale(x, y, z);
                else val = y < floor && Math.abs(u) > W - 3 ? T_LGREY : headScale(x, y, z);
                f.d.set(x, y, z, val);
            }
            // teeth along the gaping jaws, the lip of the lower jaw
            if (gape && Math.abs(Math.abs(u) - (W - 1.5)) < 0.5) {             // a fence of teeth along both jaws
                f.d.set(x, floor + 1, z, BONE);
                if (((int) Math.floor(v) & 1) == 0) {
                    f.d.set(x, floor + 2, z, BONE);
                    f.d.set(x, palate - 1, z, BONE);
                    if (((int) Math.floor(v) & 3) == 0) f.d.set(x, palate - 2, z, BONE);
                }
            }
            if (t > 0.92 && Math.abs(u) < W - 1) f.d.set(x, floor + 1, z, T_GREEN);
        }
        // the fangs
        for (int side = -1; side <= 1; side += 2) {
            double t = 0.84;
            int u = side * (int) Math.round(headW(t) - 3), v = (int) Math.round(HB - HL * t), pal = (int) Math.round(y0 + 65 - 3 * t);
            f.box(u, floor + 2, v, u, pal - 1, v, Draw.of(BONE));
            f.box(u, pal - 2, v + 1, u, pal - 1, v + 1, Draw.of(BONE));
        }
        // the eyes, glowing, with a black slit, under a brow
        for (int side = -1; side <= 1; side += 2) {
            int[] e = eye(y0, side);
            f.box(e[0], e[1], e[2] - 1, e[0], e[1] + 1, e[2] + 1, Draw.of(GLOW));
            f.box(e[0], e[1], e[2], e[0], e[1] + 1, e[2], Draw.of(C_BLACK));
            f.box(e[0], e[1] + 2, e[2] - 2, e[0], e[1] + 2, e[2] + 2, Draw.of(PRISM_DK));
            f.box(e[0] + side, e[1] + 2, e[2] - 2, e[0] + side, e[1] + 2, e[2] + 2, Draw.of(PRISM_DK));
        }
        // the crest of spines, the nostrils
        for (int v = HB - 1; v >= HB - 28; v -= 3) {
            int top = (int) Math.round(y0 + 74 - 8 * headT(v));
            f.box(0, top + 1, v, 0, top + (v > HB - 14 ? 3 : 2), v, Draw.of(BONE));
        }
        int nv = (int) Math.round(HB - HL * 0.95), ntop = (int) Math.round(y0 + 74 - 8 * 0.95);
        for (int side = -1; side <= 1; side += 2) { f.set(side * 3, ntop, nv, C_BLACK); f.set(side * 3, ntop - 1, nv, C_BLACK); }
    }

    /** An eye's block (u, y, v): on the skull's flank, two blocks under its rounded top. */
    static int[] eye(int y0, int side) {
        double t = 0.42, W = headW(t);
        int u = (int) Math.round(W - 0.5), v = (int) Math.round(HB - HL * t);
        double q = Math.min(1, u / W), round = 1 - Math.sqrt(1 - q * q);
        int top = (int) Math.round(y0 + 74 - 8 * t - round * 8);
        return new int[]{side * u, top - 2, v};
    }

    private static int headScale(int x, int y, int z) {
        double p1 = (z * 0.5 + y * 0.5), p2 = (x * 0.5 - y * 0.5);
        double f1 = p1 - Math.floor(p1), f2 = p2 - Math.floor(p2);
        if (f1 < 0.2 || f2 < 0.2) return T_BLACK;
        int h = Draw.hash((int) Math.floor(p1), (int) Math.floor(p2), 11, 993) >>> 8 & 7;
        return h < 4 ? T_GREEN : h < 6 ? PRISM_DK : h < 7 ? T_LIME : PRISM;
    }

    /** The Queen's Lair in the mouth: her nest at the back, the throat from the neck, the stair to the Crown Vault above. */
    private void lair(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + LAIR, vf = fl + 12;          // the lair's floor block, the vault's
        if (cols(f, -19, HB - HL - 1, 19, HB + 8) == null) return;
        // the throat, from the neck's tunnel into the lair
        f.box(-3, fl + 1, HB - 6, 3, fl + 5, HB + 7, Draw.AIR);
        f.box(-3, fl, HB - 6, 3, fl, HB + 7, Draw.of(RNBRICK));
        f.point(0, fl + 1, HB - 18, "lord:" + s.kind.lord);
        // her nest: webs, bones and eggs at the back, either side of the throat
        int[][] nest = {{6, -2}, {10, -5}, {12, -2}, {7, -8}, {-6, -3}, {-9, -6}, {11, -9}};
        for (int k = 0; k < nest.length; k++) {
            int u = nest[k][0], v = HB - 3 + nest[k][1];
            if (k % 3 == 0) f.box(u, fl + 1, v, u + 1, fl + 2, v + 1, Draw.of(SLIME));
            else { f.set(u, fl + 1, v, BONE); f.set(u, fl + 2, v, WEB); }
        }
        f.chest(13, fl + 1, HB - 5, Draw.WEST, RICH);
        f.wallSign(5, fl + 3, HB - 3, Draw.NORTH, "THE QUEEN'S", "LAIR", "None who came", "went home");
        f.set(5, fl + 3, HB - 2, PRISM_DK);
        // the stair up the west wall to the landing before the Crown Vault
        for (int k = 0; k <= 11; k++) {
            int v = HB - 24 + k, y = fl + 1 + k;
            f.box(-13, y + 1, v, -11, y + 4, v, Draw.AIR);
            for (int u = -13; u <= -11; u++) f.set(u, y, v, f.stair(NB_STAIR, 2, false));
            f.box(-13, fl + 1, v, -11, y - 1, v, Draw.of(NBRICK));
        }
        f.box(-13, vf, HB - 13, -9, vf, HB - 8, Draw.of(NBRICK));
        f.box(-13, vf + 1, HB - 13, -9, vf + 4, HB - 8, Draw.AIR);
        // the Crown Vault in the crest
        f.box(-8, vf, HB - 14, 8, vf + 5, HB - 4, Draw.of(OBSIDIAN));
        f.box(-7, vf + 1, HB - 13, 7, vf + 4, HB - 5, Draw.AIR);
        f.box(-7, vf, HB - 13, 7, vf, HB - 5, (x, y, z) -> ((x + z) & 1) == 0 ? GLAZED_LIME : PRISM_DK);
        f.box(-8, vf + 1, HB - 12, -8, vf + 3, HB - 10, Draw.of(SEAL));
        for (int k = 0; k < 3; k++) f.chest(7, vf + 1, HB - 12 + k * 3, Draw.WEST, VAULT);
        f.chest(-6, vf + 1, HB - 5, Draw.NORTH, RICH);
        f.chest(-6, vf + 1, HB - 13, Draw.SOUTH, RICH);
        for (int u = -4; u <= 4; u += 4) f.set(u, vf + 5, HB - 9, SEA);
        f.wallSign(-9, vf + 3, HB - 8, Draw.WEST, "THE CROWN", "VAULT", "Sealed until", "the Queen falls");
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, g = y0 + 1, fl = y0 + LAIR;
        // the tunnel's falling floors
        for (int[] t : p.traps) out.add(Ordeals.collapse(box(p, t[0], t[1], 3, (int) Math.floor(p.fl[t[0]]) - 1, (int) Math.floor(p.fl[t[0]]) - 1), TRAP_FLOOR));
        // flame vents and arrow slits along it
        for (int[] t : p.vents) { int lv = level(p.fl[t[0]]); out.add(Ordeals.flames(box(p, t[0], t[1], 3, lv, lv + 2), 70)); }
        for (int[] t : p.arrows) {
            int lv = level(p.fl[(t[0] + t[1]) / 2]);
            out.add(Ordeals.arrows(box(p, t[0], t[1], 3, lv, lv + 2), box(p, t[0], t[1], 5, lv + 1, lv + 2)));
        }
        // the Venom Cistern's air, the west egg chamber's
        int vb = p.bulges[1], vl = level(p.fl[vb]);
        out.add(Ordeals.gas(box(p, vb - 4, vb + 4, 5, vl, vl + 3)));
        out.add(Ordeals.gas(fr.box(-16, y0 - 6, TV - 9, -4, y0 - 3, TV + 7)));
        // stones falling in the Priests' Hall
        out.add(Ordeals.rubble(fr.box(-14, g + 1, TV - 8, 14, g + 7, TV + 4), NBRICK));
        // her eyes spit fire at whoever climbs her coils
        for (int side = -1; side <= 1; side += 2) {
            int[] e = eye(y0, side);
            int u = e[0] + 2 * side;
            out.add(Ordeals.cannon(fr.box(u - 34, y0 + 24, e[2] - 32, u + 34, e[1] + 4, e[2] + 34), fr.at(u, e[1], e[2])));
        }
        // the Crown Vault's door and the Queen's Spiral's foot, which her fall opens
        int vf = fl + 12;
        out.add(Ordeals.bossSeal(s.kind.id + "_vault", fr.box(-8, vf + 1, HB - 12, -8, vf + 3, HB - 10), SEAL, s.kind.lord));
        out.add(Ordeals.bossSeal(s.kind.id + "_spiral", fr.box(-1, g + 1, -32, 1, g + 3, -32), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    /** A world box round samples a..b (padded by pad across), between heights y1 and y2. */
    private static int[] box(Plan p, int a, int b2, int pad, int y1, int y2) {
        double x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
        for (int i = a; i <= b2; i++) { x0 = Math.min(x0, p.x[i]); x1 = Math.max(x1, p.x[i]); z0 = Math.min(z0, p.z[i]); z1 = Math.max(z1, p.z[i]); }
        return new int[]{(int) Math.floor(x0 - pad), y1, (int) Math.floor(z0 - pad), (int) Math.ceil(x1 + pad), y2, (int) Math.ceil(z1 + pad)};
    }

    // ---- the cavern -------------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61);
        if (s.f(x, z) > 0.88) return q < 0.5 ? RACK : T_BLACK;
        double n = Draw.fbm(x, z, 26, s.salt + 62);
        if (n > 0.3 && q < 0.5) return MOSS;
        return q < 0.45 ? T_BLACK : q < 0.65 ? T_GREY : q < 0.85 ? RACK : q < 0.93 ? C_BLACK : MAGMA;
    }

    @Override Draw.Mat under() { return Draw.mix(b(87), T_BLACK, 0.4, 5531); }

    @Override boolean dry(Colossi.Site s, int x, int z) {
        if (s.dist(x, z) < 126) return true;
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(null, s.x, s.z, p.rot);
        return Math.abs(lu(f, x, z)) < 32 && lv(f, x, z) < -90;
    }

    @Override double lakeLevel() { return 0.4; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.003);
        double q = Draw.rnd(x, 7, z, s.salt + 53);
        if (q < 0.005 && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {           // basalt teeth from the roof
            int len = 2 + (int) (Draw.rnd(x, 8, z, s.salt + 53) * 6);
            for (int k = 0; k < len && ceil - k > floor + 6; k++) d.set(x, ceil - k, z, k == len - 1 ? MAGMA : T_BLACK);
        }
        if (lake || s.dist(x, z) < 50) return;
        double r = Draw.rnd(x, 9, z, s.salt + 54);
        if (r < 0.004) { d.set(x, floor + 1, z, BONE); if (r < 0.0015) d.set(x, floor + 2, z, BONE); }
        else if (r < 0.006) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
    }


}
