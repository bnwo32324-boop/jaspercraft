package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Rime Bastion (owner, 2026-10-04: "Add 10 new structures to the Nether ... I want them to be huge structures"): a
 * star fort of packed ice and snow some 230 blocks across in a frozen cavern. Its five bastions carry icicle spires,
 * a moat of stilled lava runs at its feet, and at its heart a round keep on a star podium rises to a crystal dome where
 * the Rime Lich keeps his throne.
 * <ul>
 *   <li>the approach: a causeway between ice spikes and frozen lava lakes to the ravelin (arrow slits, a frost cannon),
 *       the bridge over the moat and the Glacier Gate between its two spired towers (more arrows);</li>
 *   <li>the five bastions, each with a vaulted chamber, a spiral stair to its terrace and an icicle spire over a lantern
 *       room; turrets hang at their shoulders and the wall walk runs all round the star;</li>
 *   <li>the parade ground, where the Frozen Legion still stands in ranks, and the barracks in two curtains;</li>
 *   <li>the podium with the Crypt of the Frost-Dead inside it (thin ice over pits, falling ice, a tomb of freezing mist,
 *       a floor of thin tiles);</li>
 *   <li>the keep: the Hall of Frozen Kings, the Ice Library and the Armoury above it, the Ice Bridge from the library to
 *       the west bastion over the Frozen Falls (lava frozen mid-pour from the keep's gargoyle), the stair tower to the
 *       roof;</li>
 *   <li>the Throne of the Rime Lich under the crystal dome, his hoard sealed beneath it until he falls.</li>
 * </ul>
 */
final class ColossusRime extends ColossusDesign {
    // ---- the star fort: five bastions (their axes from -54 degrees), the gate in the north curtain (v < 0) ---------------
    static final int NB = 5;
    static final double SECTOR = 2 * Math.PI / NB, AXIS0 = Math.toRadians(-54);
    static final double R_TIP = 116, R_SH = 94, R_FL = 84, A_SH = Math.toRadians(16), A_FL = Math.toRadians(21);
    static final double SX = R_SH * Math.cos(A_SH), SY = R_SH * Math.sin(A_SH), FX = R_FL * Math.cos(A_FL), FY = R_FL * Math.sin(A_FL);
    static final double MX = R_FL * Math.cos(SECTOR - A_FL), MY = R_FL * Math.sin(SECTOR - A_FL);
    static final double[] EDGES = {R_TIP, 0, SX, SY, SX, SY, FX, FY, FX, FY, MX, MY};
    static final double CURTAIN = R_FL * Math.cos(SECTOR / 2 - A_FL);      // the curtains' distance from the centre (81)
    static final int WALL = 22, THICK = 12, DITCH = 9, GLACIS = 26;
    // ---- the podium (a star whose points face the curtains), the keep, its floors, the throne hall ----------------------
    static final double P_TIP = 48, P_IN = 37, P_AX0 = Math.toRadians(-90);
    static final double PIX = P_IN * Math.cos(SECTOR / 2), PIY = P_IN * Math.sin(SECTOR / 2);
    static final int POD = 10, KR = 30, UP = 22, ROOF = 34, DR = 24, SPRING = 48, TOWER = 50;
    static final String[] WINDS = {"the North Wind", "the Hoarfrost", "the Long Night", "the White Hush", "the Last Snow"};

    static final class Plan {
        int rot;
        final List<int[]> spikes = new ArrayList<>();      // u, v, radius, height: ice spikes on the cavern floor
        final List<int[]> soldiers = new ArrayList<>();    // u, v, facing (0 east 1 west 2 south 3 north), officer
        final List<int[]> ghasts = new ArrayList<>();      // u, v, facing: frozen ghasts fallen on the cavern floor
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette ----------------------------------------------------------------------------------------------------------
    private static final int PACKED = b(174), SNOW = b(80), QUARTZ = b(155, 0), QCHISEL = b(155, 1), QPILLAR = b(155, 2);
    private static final int PRIS = b(168, 0), PBRICK = b(168, 1), PDARK = b(168, 2), LANTERN = b(169);
    private static final int LBLUE = b(251, 3), BLUE = b(251, 11), WHITE = b(251, 0), CYAN = b(251, 9), BLACK = b(251, 15);
    private static final int ICY = b(159, 3), GOLDISH = b(159, 4);
    private static final int GLASS = b(95, 3), GLASS_W = b(95, 0), GLASS_C = b(95, 9), PANE = b(160, 3);
    private static final int BARS = b(101), ROD = b(198, 1), MAGMA = b(213), OBSIDIAN = b(49), BONE = b(216, 0);
    private static final int WEB = b(30), SHELF = b(47), QSLAB_TOP = b(44, 15), CARPET = b(171, 3), CARPET_W = b(171, 0);
    private static final int QSTAIR = 156, SEAL = PDARK;

    // ---- geometry -----------------------------------------------------------------------------------------------------------
    /** The fort's outline at local (u, v): g[0] signed distance (negative inside), g[1] along the nearest edge, g[2] 1 in a bastion's body. */
    static void star(double u, double v, double[] g) {
        double r = Math.sqrt(u * u + v * v), rel = Math.atan2(v, u) - AXIS0;
        long k = Math.round(rel / SECTOR);
        double a = Math.abs(rel - k * SECTOR), px = r * Math.cos(a), py = r * Math.sin(a);
        double best = Double.MAX_VALUE, along = 0;
        for (int i = 0; i < 3; i++) {
            double ax = EDGES[i * 4], ay = EDGES[i * 4 + 1], vx = EDGES[i * 4 + 2] - ax, vy = EDGES[i * 4 + 3] - ay;
            double l2 = vx * vx + vy * vy, q = Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / l2));
            double qx = ax + vx * q - px, qy = ay + vy * q - py, dd = qx * qx + qy * qy;
            if (dd < best) { best = dd; along = q * Math.sqrt(l2) + i * 64; }
        }
        int e = a <= A_SH ? 0 : a <= A_FL ? 1 : 2;
        double ax = EDGES[e * 4], ay = EDGES[e * 4 + 1], bx = EDGES[e * 4 + 2], by = EDGES[e * 4 + 3];
        boolean inside = (bx - ax) * (py - ay) - (by - ay) * (px - ax) > 0;
        double dist = Math.sqrt(best);
        g[0] = inside ? -dist : dist;
        g[1] = along;
        g[2] = inside && px > FX ? 1 : 0;
    }

    /** The podium's outline at local (u, v): the signed distance (negative inside); g[1] along its nearest edge. */
    static double podStar(double u, double v, double[] g) {
        double r = Math.sqrt(u * u + v * v), rel = Math.atan2(v, u) - P_AX0;
        long k = Math.round(rel / SECTOR);
        double a = Math.abs(rel - k * SECTOR), px = r * Math.cos(a), py = r * Math.sin(a);
        double vx = PIX - P_TIP, vy = PIY, l2 = vx * vx + vy * vy, q = Math.max(0, Math.min(1, ((px - P_TIP) * vx + py * vy) / l2));
        double qx = P_TIP + vx * q - px, qy = vy * q - py, dist = Math.sqrt(qx * qx + qy * qy);
        g[1] = q * Math.sqrt(l2);
        return vx * py - vy * (px - P_TIP) > 0 ? -dist : dist;
    }

    static int lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    static int lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }
    static int wx(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.x - v; case 2: return s.x - u; case 3: return s.x + v; default: return s.x + u; } }
    static int wz(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.z + u; case 2: return s.z - v; case 3: return s.z - u; default: return s.z + v; } }
    static int ri(double v) { return (int) Math.round(v); }
    /** The local facing (chests, signs) nearest to the direction (du, dv). */
    static int face(double du, double dv) { return Math.abs(du) >= Math.abs(dv) ? (du > 0 ? Draw.EAST : Draw.WEST) : (dv > 0 ? Draw.SOUTH : Draw.NORTH); }
    /** The stair direction (0 east 1 west 2 south 3 north) nearest to (du, dv). */
    static int dirOf(double du, double dv) { return Math.abs(du) >= Math.abs(dv) ? (du > 0 ? 0 : 1) : (dv > 0 ? 2 : 3); }

    static boolean touches(Draw.Frame f, double u0, double v0, double u1, double v1) {
        double xa = f.xd(u0, v0), za = f.zd(u0, v0), xb = f.xd(u1, v1), zb = f.zd(u1, v1);
        return f.d.touches((int) Math.floor(Math.min(xa, xb)), (int) Math.floor(Math.min(za, zb)), (int) Math.ceil(Math.max(xa, xb)), (int) Math.ceil(Math.max(za, zb)));
    }

    interface Col { void at(int x, int z, int u, int v); }

    /** Every column of the clip whose local position lies in [ua, ub] x [va, vb]. */
    static void columns(Draw.Frame f, double ua, double va, double ub, double vb, Col c) {
        Draw d = f.d;
        double x0 = f.xd(ua, va), x1 = f.xd(ub, vb), z0 = f.zd(ua, va), z1 = f.zd(ub, vb);
        int xa = Math.max(d.x0, (int) Math.floor(Math.min(x0, x1))), xb = Math.min(d.x1, (int) Math.ceil(Math.max(x0, x1)));
        int za = Math.max(d.z0, (int) Math.floor(Math.min(z0, z1))), zb = Math.min(d.z1, (int) Math.ceil(Math.max(z0, z1)));
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) c.at(x, z, lu(f, x, z), lv(f, x, z));
    }

    /** A passage of half width hw from (u0, v0) to (u1, v1) at any angle: its floor at yf (unless null), air h blocks over it. */
    static void walk(Draw.Frame f, double u0, double v0, double u1, double v1, int yf, double hw, int h, Draw.Mat floor) {
        double m = hw + 1;
        double ua = Math.min(u0, u1) - m, ub = Math.max(u0, u1) + m, va = Math.min(v0, v1) - m, vb = Math.max(v0, v1) + m;
        if (!touches(f, ua, va, ub, vb)) return;
        double du = u1 - u0, dv = v1 - v0, l2 = du * du + dv * dv;
        columns(f, ua, va, ub, vb, (x, z, u, v) -> {
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((u - u0) * du + (v - v0) * dv) / l2));
            double qu = u0 + du * t - u, qv = v0 + dv * t - v, dd = Math.sqrt(qu * qu + qv * qv);
            if (dd > hw + 0.3) return;
            if (floor != null) f.d.set(x, yf, z, floor.at(x, yf, z));
            int top = yf + h - (dd > hw - 0.7 && h > 3 ? 1 : 0);
            for (int y = yf + 1; y <= top; y++) f.d.set(x, y, z, 0);
        });
    }

    /** A spiral stair round a newel in a 3x3 well (walls of wall round it), from ya up to yb; open at the top. */
    static void spiral(Draw.Frame f, int u, int v, int ya, int yb, Draw.Mat wall, int step) {
        f.box(u - 2, ya, v - 2, u + 2, yb, v + 2, wall);
        f.box(u - 1, ya, v - 1, u + 1, yb, v + 1, Draw.AIR);
        f.box(u, ya, v, u, yb - 1, v, Draw.of(QPILLAR));
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = ya; y <= yb; y++) { int[] q = ring[Math.floorMod(y, 8)]; f.set(u + q[0], y, v + q[1], step); }
    }

    // ---- plan ---------------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        double[] g = new double[3];
        // ice spikes on the cavern floor beyond the glacis
        for (int t = 0; t < 400 && p.spikes.size() < 30; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rr = 120 + r.nextDouble() * 40;
            int u = ri(Math.cos(a) * rr), v = ri(Math.sin(a) * rr), rad = 2 + r.nextInt(4), h = 10 + r.nextInt(24);
            if (Math.abs(u) < 14 && v < 0) continue;                            // the causeway
            if (Math.abs(u) < 32 && v < -86) continue;                          // the ravelin
            star(u, v, g);
            if (g[0] < GLACIS + rad + 3) continue;
            if (s.f(wx(s, p.rot, u, v), wz(s, p.rot, u, v)) > 0.86) continue;
            boolean clash = false;
            for (int[] q : p.spikes) if (Math.abs(q[0] - u) + Math.abs(q[1] - v) < 13) clash = true;
            if (!clash) p.spikes.add(new int[]{u, v, rad, h});
        }
        // two frozen ghasts fallen before the curtains
        for (int j = 0; j < 2; j++) {
            double a = AXIS0 + SECTOR * (1.5 + 2 * j) + (r.nextDouble() - 0.5) * 0.25, rr = 128 + r.nextDouble() * 6;
            p.ghasts.add(new int[]{ri(Math.cos(a) * rr), ri(Math.sin(a) * rr), r.nextInt(4)});
        }
        // the Frozen Legion: the forecourt's files, and ranks before the three curtains, all facing the keep
        for (int u = -22; u <= 22; u += 4) for (int v = -66; v <= -58; v += 4) {
            if (Math.abs(u) < 7) continue;
            p.soldiers.add(new int[]{u, v, 2, (u == -10 || u == 10) && v == -66 ? 1 : 0});
        }
        double[] wins = {AXIS0 + SECTOR / 2, AXIS0 + SECTOR * 1.5, AXIS0 + SECTOR * 2.5};
        for (int u = -68; u <= 68; u += 4) for (int v = -68; v <= 68; v += 4) {
            double rr = Math.sqrt(u * u + v * v);
            if (rr < 58 || rr > 66) continue;
            double ang = Math.atan2(v, u);
            boolean in = false;
            for (double w : wins) { double dA = Math.abs(Math.IEEEremainder(ang - w, 2 * Math.PI)); if (dA < Math.toRadians(13)) in = true; }
            if (!in) continue;
            star(u, v, g);
            if (-g[0] < THICK + 3 || g[2] > 0) continue;
            if (podStar(u, v, g) < 4) continue;
            // keep clear the lanes to the barracks' doors (curtains 0 and 2)
            boolean lane = false;
            for (int j = 0; j <= 2; j += 2) {
                double phi = AXIS0 + SECTOR / 2 + j * SECTOR, tx = -Math.sin(phi), tz = Math.cos(phi);
                for (int i = -1; i <= 1; i++) if (Math.abs((u * tx + v * tz) - 12 * i) < 2.5 && u * Math.cos(phi) + v * Math.sin(phi) > 0) lane = true;
            }
            if (lane) continue;
            p.soldiers.add(new int[]{u, v, dirOf(-u, -v), Math.floorMod(u * 7 + v * 3, 23) == 0 ? 1 : 0});
        }
        // the garrisons
        int y0 = s.y, top = y0 + WALL, fl = y0 + POD, up = y0 + UP, roof = y0 + ROOF;
        List<Garrison> gs = new ArrayList<>();
        int cw = Math.max(y0, s.floorAt(wx(s, p.rot, 0, -140), wz(s, p.rot, 0, -140)));
        gs.add(g(0, cw + 1, -140, "!wight+frost"));                                   // the causeway
        gs.add(g(0, y0 + 10, -104, "!wight+frost+skeleton"));                        // the ravelin's platform
        gs.add(g(7, y0 + 1, -108, "skeleton+ashbone_archer"));                       // its guard room
        gs.add(g(0, y0 + 1, -63, "!crypt_guard+wither_skeleton"));                   // behind the Glacier Gate
        gs.add(g(14, y0 + 1, -52, "wight+wight+spinout"));                           // the ring road before the podium
        String[] chambers = {"frost+coolmar_spider", "wight+brute", "skeleton+nethermite", "crypt_guard+lost_soul", "wither_skeleton+spinout"};
        String[] terraces = {"ashbone_archer+skeleton", "frost+ghastling", "skeleton+wight", "ashbone_archer+frost", "wither_skeleton+skeleton"};
        for (int k = 0; k < NB; k++) {
            double th = AXIS0 + k * SECTOR, cx = Math.cos(th), cz = Math.sin(th);
            gs.add(g(ri(90 * cx + 5.5 * cx - 3 * cz), y0 + 1, ri(90 * cz + 5.5 * cz + 3 * cx), chambers[k]));
            gs.add(g(ri(85 * cx), top + 1, ri(85 * cz), terraces[k].replace("+ghastling", "")));
        }
        for (int j = 0; j <= 2; j += 2) {
            double phi = AXIS0 + SECTOR / 2 + j * SECTOR, c = CURTAIN - THICK + 6.5;
            gs.add(g(ri(c * Math.cos(phi)), y0 + 1, ri(c * Math.sin(phi)), j == 0 ? "!infernal_knight+wither_skeleton" : "skeleton+ember_legionnaire"));
        }
        for (double w : wins) gs.add(g(ri(53 * Math.cos(w)), y0 + 1, ri(53 * Math.sin(w)), "wight+skeleton"));
        gs.add(g(0, y0 + 1, 7, "lost_soul+shade+crypt_guard"));                      // the ossuary
        gs.add(g(0, y0 + 1, 22, "deep_crawler+coolmar_spider"));                    // the crypt's ring
        for (int k = 1; k < NB; k += 2) {
            double a = P_AX0 + k * SECTOR;
            gs.add(g(ri(37 * Math.cos(a)), y0 + 1, ri(37 * Math.sin(a)), "mummy+crypt_guard"));
        }
        gs.add(g(0, fl + 1, -14, "!royal_guard+wight"));                             // the Hall of Frozen Kings
        gs.add(g(0, fl + 1, 16, "wither_skeleton+soul_wraith"));
        gs.add(g(-8, up + 1, -16, "cinder_witch+soul_wraith"));                      // the Ice Library
        gs.add(g(9, up + 1, -6, "infernal_knight+brute"));                           // the Armoury
        gs.add(g(-52, up + 1, 0, "wight+ashbone_archer"));                           // the Ice Bridge
        gs.add(g(-10, roof + 1, -26, "frost+blaze"));                                // the roof terrace
        gs.add(g(21, y0 + TOWER + 1, -24, "ashbone_archer+skeleton"));               // the stair tower's top
        gs.add(g(-10, roof + 1, -6, "!wight+frost+skeleton"));                       // the Lich's guards
        gs.add(g(10, roof + 1, -6, "crypt_guard+wither_skeleton"));
        complete(gs);
        // the great fliers in the open air over the parade ground and the glacis
        gs.add(g(42, y0 + 44, 34, "ghast+ghastling"));
        gs.add(g(-40, y0 + 46, -40, "ghastling+ghastling+ghast"));
        gs.add(g(ri(118 * Math.cos(AXIS0 + SECTOR * 1.5)), y0 + 30, ri(118 * Math.sin(AXIS0 + SECTOR * 1.5)), "ghast+ghastling"));
        p.garrisons = gs;
        return p;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        fort(f, s);
        approach(f, s);
        for (int k = 0; k < NB; k++) bastion(f, s, k);
        barracks(f, s, 0);
        barracks(f, s, 2);
        podium(f, s);
        crypt(f, s);
        keep(f, s);
        throne(f, s);
        falls(f, s);
        legion(f, s, p);
        lamps(f, s);
        cavernWorks(f, s, p);
        garrisons(f, p.garrisons);
    }

    private static int wallBlock(int x, int y, int z, int y0, double dm, int h) {
        if (y <= y0 + 2) return Draw.rnd(x, y, z, 7) < 0.35 ? PBRICK : PDARK;
        if (y == h) return dm < 4 ? SNOW : (Math.floorMod(x + z, 4) == 0 ? PACKED : SNOW);
        if (dm < 2 && y == y0 + WALL - 1) return LBLUE;
        if (dm < 2 && y == y0 + WALL) return QUARTZ;
        if (Math.floorMod(y - y0, 6) == 0) return SNOW;
        return PACKED;
    }

    /** The parade ground's paving: a ring road round the podium, roads to the bastions and the gate, a lattice of ice. */
    private static int road(int u, int v, double rr, int x, int z) {
        double w = Math.abs(rr - 53);
        if (Math.abs(u) <= 3 && v < -50) w = Math.min(w, Math.abs(u) - 0.5);
        if (rr > 36 && rr < 76) for (int k = 0; k < NB; k++) {
            double th = AXIS0 + k * SECTOR, along = u * Math.cos(th) + v * Math.sin(th);
            if (along > 0) w = Math.min(w, Math.abs(-u * Math.sin(th) + v * Math.cos(th)));
        }
        if (w <= 1.6) return PACKED;
        if (w <= 2.6) return LBLUE;
        return Math.floorMod(x + z, 9) == 0 || Math.floorMod(x - z, 9) == 0 ? PACKED : SNOW;
    }

    private static int frozen(int x, int z, int salt) {
        double q = Draw.rnd(x, 1, z, salt + 91);
        return q < 0.6 ? MAGMA : q < 0.86 ? PACKED : OBSIDIAN;
    }

    /** The star: ramparts and bastions, the frozen moat, the counterscarp, the glacis and the parade ground, column by column. */
    private void fort(Draw.Frame f, Colossi.Site s) {
        final int y0 = s.y, R = (int) R_TIP + GLACIS + 1;
        if (!touches(f, -R, -R, R, R)) return;
        final double[] g = new double[3];
        final Draw d = f.d;
        columns(f, -R, -R, R, R, (x, z, u, v) -> {
            double rr = Math.sqrt(u * (double) u + v * (double) v);
            if (rr > R) return;
            star(u, v, g);
            double sd = g[0];
            if (sd >= GLACIS) return;
            if (sd < 0) {
                double dm = -sd;
                boolean bast = g[2] > 0;
                if (dm < THICK || bast) {
                    boolean merlon = Math.floorMod((int) Math.floor(g[1]), 4) < 2;
                    int h = dm < 1 ? y0 + 3 : dm < 4 ? y0 + WALL + 3 + (merlon && dm < 2 ? 1 : 0) : dm >= THICK - 1 && !bast ? y0 + WALL + 1 : y0 + WALL;
                    for (int y = 24; y <= h; y++) d.set(x, y, z, wallBlock(x, y, z, y0, dm, h));
                } else {
                    for (int y = y0 - 3; y < y0; y++) d.set(x, y, z, PACKED);
                    d.set(x, y0, z, road(u, v, rr, x, z));
                    for (int y = y0 + 1; y <= y0 + 5; y++) d.set(x, y, z, 0);
                }
                return;
            }
            if (sd < DITCH) {
                int fl = y0 - 5;
                // steps of ice up the counterscarp at the curtains' middles (none at the gate)
                double c = Math.atan2(v, u) - (AXIS0 + SECTOR / 2);
                long ci = Math.round(c / SECTOR);
                boolean steps = Math.floorMod(ci, (long) NB) != 4 && Math.abs(Math.sin(c - ci * SECTOR)) * rr < 1.6 && sd >= 2;
                if (steps) fl = y0 - 5 + (int) Math.floor(sd - 2);
                for (int y = 24; y < fl; y++) d.set(x, y, z, PDARK);
                d.set(x, fl, z, steps ? PBRICK : frozen(x, z, s.salt));
                for (int y = fl + 1; y <= y0 + 5; y++) d.set(x, y, z, 0);
            } else if (sd < DITCH + 1) {
                for (int y = 24; y <= y0 + 1; y++) d.set(x, y, z, y == y0 + 1 ? SNOW : PBRICK);
                for (int y = y0 + 2; y <= y0 + 5; y++) d.set(x, y, z, 0);
            } else {
                int h = y0 + (int) Math.round(2.0 * (GLACIS - sd) / (GLACIS - DITCH - 1));
                for (int y = y0 - 3; y <= h; y++) d.set(x, y, z, y == h && Draw.fbm(x, z, 14, s.salt + 5) > 0.3 ? PACKED : SNOW);
                for (int y = h + 1; y <= y0 + 5; y++) d.set(x, y, z, 0);
            }
        });
    }

    // ---- the approach: the causeway, the ravelin, the bridge, the Glacier Gate ---------------------------------------------
    private void approach(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        // the causeway from the cavern's edge, lamps of ice along it
        if (touches(f, -8, -184, 8, -121)) for (int v = -184; v <= -121; v++) {
            int x = f.x(0, v), z = f.z(0, v);
            if (s.f(x, z) > 0.98) continue;
            int top = Math.max(y0, s.floorAt(x, z));
            f.box(-4, top - 4, v, 4, top - 1, v, Draw.of(PBRICK));
            for (int u = -3; u <= 3; u++) f.set(u, top, v, Math.floorMod(v, 6) == 0 || Math.abs(u) == 3 ? PACKED : SNOW);
            f.set(-4, top, v, PDARK); f.set(4, top, v, PDARK);
            f.set(-4, top + 1, v, SNOW); f.set(4, top + 1, v, SNOW);
            f.box(-3, top + 1, v, 3, top + 5, v, Draw.AIR);
            if (Math.floorMod(v, 12) == 0) for (int side = -1; side <= 1; side += 2) {
                f.box(side * 5, top - 2, v, side * 5, top + 3, v, Draw.of(PACKED));
                f.set(side * 5, top + 4, v, LANTERN);
                f.set(side * 5, top + 5, v, SNOW);
            }
        }
        // the ravelin: a triangle of ice before the gate, its passage on the axis
        final int rt = -124, rb = -95, rw = 22, rh = y0 + 9;
        if (touches(f, -rw - 1, rt - 1, rw + 1, rb + 1)) {
            final double len = Math.sqrt(rw * rw + (rb - rt) * (double) (rb - rt));
            columns(f, -rw, rt, rw, rb, (x, z, u, v) -> {
                double dIn = ((v - rt) * (double) rw - (rb - rt) * (double) Math.abs(u)) / len;
                if (dIn < 0) return;
                double t = (Math.abs(u) * (double) rw + (v - rt) * (double) (rb - rt)) / len;
                int h = dIn < 1 ? y0 + 2 : dIn < 3.5 ? rh + 3 + (Math.floorMod((int) t, 4) < 2 && dIn < 2 ? 1 : 0) : v >= rb - 1 ? rh + 1 : rh;
                for (int y = 24; y <= h; y++) f.d.set(x, y, z, wallBlock(x, y, z, y0, dIn < 3.5 ? dIn : 5, h));
            });
            f.box(-2, y0 + 1, rt - 1, 2, y0 + 5, rb + 1, Draw.AIR);
            f.box(-1, y0 + 6, rt - 1, 1, y0 + 6, rb + 1, Draw.AIR);
            f.box(-2, y0, rt - 1, 2, y0, rb + 1, Draw.of(PBRICK));
            for (int v = rt + 6; v <= rb - 4; v += 8) f.set(0, y0 + 7, v, LANTERN);
            // the guard room east of the passage, a ladder up to the platform
            f.box(4, y0 + 1, -114, 10, y0 + 5, -104, Draw.AIR);
            f.box(3, y0 + 1, -110, 3, y0 + 3, -108, Draw.AIR);
            f.box(9, y0 + 6, -114, 9, rh, -114, Draw.AIR);
            for (int y = y0 + 1; y <= rh; y++) f.set(9, y, -114, b(65, f.facing(Draw.SOUTH)));
            f.set(7, y0 + 6, -109, LANTERN);
            f.chest(5, y0 + 1, -114, Draw.SOUTH, "jaspr:colossus/rime");
            f.set(10, y0 + 5, -104, WEB);
            // the frost cannon on the platform, aimed down the causeway
            f.box(-1, rh + 1, -113, 1, rh + 2, -111, Draw.of(PDARK));
            f.line(0, rh + 3, -112, 0, rh + 4, -120, 0.7, Draw.of(BLUE));
            f.set(0, rh + 5, -112, LANTERN);
            f.wallSign(-2, y0 + 3, -118, Draw.EAST, "Go back.", "Nothing that", "enters here", "ever thaws");
        }
        // the bridge over the moat, its arch letting the frozen moat run under it
        if (touches(f, -4, -95, 4, -79)) for (int v = -94; v <= -80; v++) {
            f.box(-3, y0 - 5, v, 3, y0, v, Draw.of(PBRICK));
            if (v >= -90 && v <= -82) {
                int hv = (int) Math.round(3.4 * Math.sqrt(Math.max(0, 1 - Math.pow((v + 86) / 4.6, 2))));
                f.box(-3, y0 - 4, v, 3, y0 - 4 + hv, v, Draw.AIR);
            }
            f.box(-2, y0, v, 2, y0, v, Draw.of(v % 3 == 0 ? PACKED : SNOW));
            f.box(-3, y0 + 1, v, -3, y0 + 1, v, Draw.of(SNOW));
            f.box(3, y0 + 1, v, 3, y0 + 1, v, Draw.of(SNOW));
            f.box(-2, y0 + 1, v, 2, y0 + 5, v, Draw.AIR);
        }
        // the Glacier Gate: a vaulted passage through the curtain between two spired towers
        if (touches(f, -18, -92, 18, -64)) {
            f.box(-5, y0 + 1, -87, 5, y0 + WALL + 4, -76, (x, y, z) -> wallBlock(x, y, z, y0, 5, y0 + WALL + 4));
            f.box(-6, y0 + WALL + 2, -88, 6, y0 + WALL + 4, -88, Draw.of(SNOW));
            for (int u = -5; u <= 5; u += 2) f.set(u, y0 + WALL + 2, -88, 0);
            for (int side = -1; side <= 1; side += 2) {
                double tu = side * 9.5;
                f.cyl(tu, -83, 6.5, y0 - 5, y0 + WALL + 6, (x, y, z) -> y <= y0 + 2 ? PDARK : Math.floorMod(y - y0, 6) == 0 ? LBLUE : PACKED);
                f.ring(tu, -83, 7.3, 1.2, y0 + WALL + 5, y0 + WALL + 6, Draw.of(SNOW));
                spire(f, s, tu, -83, y0 + WALL + 7, 6.0, 30);
                f.cyl(tu, -83, 4.5, y0 + 1, y0 + 6, Draw.AIR);
                f.box(side * 3, y0 + 1, -84, side * 5, y0 + 3, -82, Draw.AIR);
                f.set(ri(tu), y0 + 7, -83, LANTERN);
                for (int a = 0; a < 4; a++) f.set(ri(tu + 6 * Math.cos(a * Math.PI / 2 + 0.4)), y0 + 4, ri(-83 + 6 * Math.sin(a * Math.PI / 2 + 0.4)), GLASS);
            }
            f.chest(-13, y0 + 1, -83, Draw.EAST, "jaspr:colossus/rime");
            f.chest(13, y0 + 1, -84, Draw.WEST, "jaspr:colossus/rime_rich");
            f.box(-2, y0 + 1, -88, 2, y0 + 6, -66, Draw.AIR);
            f.box(-1, y0 + 7, -88, 1, y0 + 7, -66, Draw.AIR);
            f.box(-2, y0, -88, 2, y0, -66, Draw.of(PBRICK));
            f.box(-2, y0 + 6, -80, 2, y0 + 7, -80, Draw.of(BARS));                    // the portcullis, raised
            for (int side = -1; side <= 1; side += 2) f.box(side * 3, y0 + 1, -89, side * 3, y0 + 8, -88, Draw.of(QPILLAR));
            f.box(-3, y0 + 8, -89, 3, y0 + 8, -88, Draw.of(QCHISEL));
            f.set(0, y0 + 7, -74, LANTERN);
            f.wallSign(0, y0 + 10, -88, Draw.NORTH, "THE RIME", "BASTION", "Five spires", "and one Lich");
        }
    }

    // ---- an icicle spire: a tapering cone of packed ice banded with snow ---------------------------------------------------
    private void spire(Draw.Frame f, Colossi.Site s, double u, double v, int yb, double r0, int want) {
        double x = f.xd(u, v), z = f.zd(u, v);
        if (!f.d.touches((int) Math.floor(x - r0 - 1), (int) Math.floor(z - r0 - 1), (int) Math.ceil(x + r0 + 1), (int) Math.ceil(z + r0 + 1))) return;
        int top = Math.min(yb + want, s.ceilAt((int) Math.round(x), (int) Math.round(z)) - 3);
        int h = top - yb;
        if (h < 6) return;
        Draw.Mat ice = (xx, yy, zz) -> Math.floorMod(yy - yb, 7) == 3 ? SNOW : yy - yb == 1 ? LBLUE : PACKED;
        for (int y = yb; y <= top; y++) {
            double t = (y - yb) / (double) h, rr = r0 * Math.pow(1 - t, 1.3) + 0.15;
            f.d.tube(x, z, rr, rr, 0, y, y, ice);
        }
    }

    // ---- the five bastions ---------------------------------------------------------------------------------------------------
    private void bastion(Draw.Frame f, Colossi.Site s, int k) {
        int y0 = s.y, top = y0 + WALL;
        double th = AXIS0 + k * SECTOR, cx = Math.cos(th), cz = Math.sin(th);
        if (!touches(f, 96 * cx - 44, 96 * cz - 44, 96 * cx + 44, 96 * cz + 44)) return;
        // the chamber, vaulted, with the spiral stair to the terrace in its middle
        double chu = 90 * cx, chv = 90 * cz;
        f.cyl(chu, chv, 8.5, y0 + 1, y0 + 7, Draw.AIR);
        f.d.ellipsoid(f.xd(chu, chv), y0 + 7, f.zd(chu, chv), 8.5, 3.5, 8.5, 0, Draw.AIR);
        f.disk(chu, chv, 8.5, y0, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? PBRICK : PDARK);
        walk(f, 72 * cx, 72 * cz, 84 * cx, 84 * cz, y0, 2.0, 5, Draw.of(PBRICK));
        int su = ri(chu), sv = ri(chv);
        spiral(f, su, sv, y0 + 1, top, Draw.of(PBRICK), PACKED);
        int door = face(-cx, -cz);
        int du = door == Draw.EAST ? 2 : door == Draw.WEST ? -2 : 0, dv = door == Draw.SOUTH ? 2 : door == Draw.NORTH ? -2 : 0;
        f.box(su + du, y0 + 1, sv + dv, su + du, y0 + 3, sv + dv, Draw.AIR);
        f.wallSign(su + du + Integer.signum(du), y0 + 5, sv + dv + Integer.signum(dv), door, "The Bastion of", WINDS[k], "", "Stair to spire");
        for (int q = 0; q < 4; q++) {
            int lu = q == 0 ? 2 : q == 1 ? -2 : 0, lv = q == 2 ? 2 : q == 3 ? -2 : 0;
            if (lu != du || lv != dv) f.set(su + lu, y0 + 4, sv + lv, LANTERN);
        }
        f.set(su, top, sv, LANTERN);
        // its chests, a spawner in two of them
        double bx = -cz, bz = cx;
        for (int side = -1; side <= 1; side += 2) {
            int cu = ri(chu + side * 7.4 * bx), cv = ri(chv + side * 7.4 * bz);
            f.chest(cu, y0 + 1, cv, face(-side * bx, -side * bz), k == 4 && side > 0 ? "jaspr:colossus/rime_rich" : "jaspr:colossus/rime");
        }
        if (k == 0) f.spawner(ri(chu + 6 * cx), y0 + 1, ri(chv + 6 * cz), "wight");
        if (k == 2) f.spawner(ri(chu + 6 * cx), y0 + 1, ri(chv + 6 * cz), "frost");
        f.set(ri(chu - 6 * bx + 3 * cx), y0 + 1, ri(chv - 6 * bz + 3 * cz), BONE);
        f.set(ri(chu + 4 * bx - 5 * cx), y0 + 5, ri(chv + 4 * bz - 5 * cz), WEB);
        // the spire over its point, with a lantern room at its foot, and icicles round it
        double spu = 103 * cx, spv = 103 * cz;
        spire(f, s, spu, spv, top + 1, 7.2, 54);
        f.cyl(spu, spv, 2.6, top + 1, top + 4, Draw.AIR);
        f.set(ri(spu), top + 5, ri(spv), LANTERN);
        walk(f, spu - 2 * cx, spv - 2 * cz, spu - 7.5 * cx, spv - 7.5 * cz, top, 0.8, 3, null);
        for (int q = 0; q < 4; q++) {
            double a = th + Math.PI / 4 + q * Math.PI / 2;
            f.set(ri(spu + 4.6 * Math.cos(a)), top + 3, ri(spv + 4.6 * Math.sin(a)), GLASS);
            f.set(ri(spu + 5.4 * Math.cos(a)), top + 3, ri(spv + 5.4 * Math.sin(a)), GLASS);
        }
        if (k == 1) f.chest(ri(spu + 1.6 * cx), top + 1, ri(spv + 1.6 * cz), face(-cx, -cz), "jaspr:colossus/rime_rich");
        for (int q = 0; q < 4; q++) {
            double a = th + (q < 2 ? -1 : 1) * (0.55 + 0.5 * (q & 1)), lean = 0.35;
            double x0 = spu + 6.8 * Math.cos(a), z0 = spv + 6.8 * Math.sin(a);
            double x1 = x0 + 3 * lean * Math.cos(a), z1 = z0 + 3 * lean * Math.sin(a), x2 = x1 + 3 * lean * Math.cos(a), z2 = z1 + 3 * lean * Math.sin(a);
            int hh = 8 + 3 * q % 7;
            f.line(x0, top + 1, z0, x1, top + hh / 2.0, z1, 1.3, Draw.of(PACKED));
            f.line(x1, top + hh / 2.0, z1, x2, top + hh, z2, 0.7, Draw.of(PACKED));
            f.line(x2, top + hh, z2, x2 + lean * Math.cos(a), top + hh + 3, z2 + lean * Math.sin(a), 0.3, Draw.of(SNOW));
        }
        // turrets hanging at its shoulders
        for (int side = -1; side <= 1; side += 2) {
            double a = th + side * A_SH;
            turret(f, R_SH * Math.cos(a), R_SH * Math.sin(a), top);
        }
    }

    private void turret(Draw.Frame f, double u, double v, int top) {
        double x = f.xd(u, v), z = f.zd(u, v);
        if (!f.d.touches((int) x - 9, (int) z - 9, (int) x + 9, (int) z + 9)) return;
        f.d.tube(x, z, 0.5, 2.7, 0, top - 7, top, Draw.of(PBRICK));
        f.d.tube(x, z, 2.7, 2.7, 1.2, top + 1, top + 4, Draw.of(SNOW));
        f.d.tube(x, z, 1.6, 1.6, 0, top + 1, top + 4, Draw.AIR);
        f.d.tube(x, z, 3.3, 0.2, 0, top + 5, top + 12, (xx, yy, zz) -> yy == top + 5 ? LBLUE : PACKED);
        f.d.set((int) Math.round(x), top + 4, (int) Math.round(z), LANTERN);
        double len = Math.sqrt(u * u + v * v);
        walk(f, u, v, u - 5.5 * u / len, v - 5.5 * v / len, top, 0.8, 3, null);
    }

    // ---- the barracks: vaulted galleries in two curtains ----------------------------------------------------------------------
    private void barracks(Draw.Frame f, Colossi.Site s, int j) {
        int y0 = s.y;
        double phi = AXIS0 + SECTOR / 2 + j * SECTOR, nx = Math.cos(phi), nz = Math.sin(phi), tx = -nz, tz = nx;
        double c = CURTAIN - THICK + 6.5;
        if (!touches(f, c * nx - 26, c * nz - 26, c * nx + 26, c * nz + 26)) return;
        walk(f, c * nx - 17 * tx, c * nz - 17 * tz, c * nx + 17 * tx, c * nz + 17 * tz, y0, 2.4, 6, Draw.of(PBRICK));
        for (int i = -1; i <= 1; i++)
            walk(f, (c - 8) * nx + 12 * i * tx, (c - 8) * nz + 12 * i * tz, c * nx + 12 * i * tx, c * nz + 12 * i * tz, y0, 1.4, 4, Draw.of(PBRICK));
        for (int i = -15; i <= 15; i += 3) {
            int bu = ri((c + 1.6) * nx + i * tx), bv = ri((c + 1.6) * nz + i * tz);
            f.set(bu, y0 + 1, bv, QSLAB_TOP);
            f.set(bu, y0 + 2, bv, Math.floorMod(i, 2) == 0 ? PACKED : CARPET_W);
        }
        for (int i = -12; i <= 12; i += 12) f.set(ri(c * nx + i * tx), y0 + 7, ri(c * nz + i * tz), LANTERN);
        for (int e = -1; e <= 1; e += 2) {
            int cu = ri(c * nx + e * 16.3 * tx), cv = ri(c * nz + e * 16.3 * tz);
            f.chest(cu, y0 + 1, cv, face(-e * tx, -e * tz), "jaspr:colossus/rime");
        }
        if (j == 0) f.spawner(ri((c + 1.8) * nx + 6 * tx), y0 + 1, ri((c + 1.8) * nz + 6 * tz), "ashbone_archer");
        f.set(ri((c - 1.5) * nx - 8 * tx), y0 + 5, ri((c - 1.5) * nz - 8 * tz), WEB);
        // the sign on the gallery's outer wall, by its middle door
        int sf = face(-nx, -nz);
        int sdu = sf == Draw.EAST ? 1 : sf == Draw.WEST ? -1 : 0, sdv = sf == Draw.SOUTH ? 1 : sf == Draw.NORTH ? -1 : 0;
        int sgu = ri((c + 1.9) * nx + 4 * tx), sgv = ri((c + 1.9) * nz + 4 * tz);
        f.box(sgu - sdu, y0 + 3, sgv - sdv, sgu - sdu, y0 + 3, sgv - sdv, Draw.of(PBRICK));
        f.wallSign(sgu, y0 + 3, sgv, sf, "The Barracks", "of the Frozen", "Legion", "Lights out.");
    }

    // ---- the podium: a five-pointed star of dark ice under the keep, its grand stair ----------------------------------------
    private void podium(Draw.Frame f, Colossi.Site s) {
        final int y0 = s.y, top = y0 + POD, R = (int) P_TIP + 1;
        if (!touches(f, -R, -R - 12, R, R)) return;
        final double[] g = new double[3];
        columns(f, -R, -R, R, R, (x, z, u, v) -> {
            double sd = podStar(u, v, g);
            if (sd > 0) return;
            double dm = -sd;
            int h = dm < 1.2 ? top + 1 + (Math.floorMod((int) Math.floor(g[1]), 4) == 0 ? 1 : 0) : top;
            for (int y = y0 - 2; y <= h; y++) {
                int bl;
                if (y > top) bl = y == top + 2 ? LANTERN : (Math.floorMod((int) Math.floor(g[1]), 4) == 0 ? QPILLAR : SNOW);
                else if (y == top) bl = dm < 1.2 ? QUARTZ : (Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? LBLUE : PACKED);
                else if (y == top - 1 && dm < 1.2) bl = QCHISEL;
                else if (y <= y0 + 1) bl = PDARK;
                else bl = Math.floorMod(y - y0, 4) == 0 ? LBLUE : BLUE;
                f.d.set(x, y, z, bl);
            }
        });
        // the grand stair up the north point
        for (int k = 0; k < POD; k++) {
            int v = -59 + k, y = y0 + 1 + k;
            f.box(-4, y0, v, 4, y - 1, v, Draw.of(BLUE));
            for (int u = -4; u <= 4; u++) f.set(u, y, v, f.stair(QSTAIR, 2, false));
            f.box(-4, y + 1, v, 4, y + 5, v, Draw.AIR);
            f.box(-5, y0, v, -5, y + 1, v, Draw.of(QUARTZ));
            f.box(5, y0, v, 5, y + 1, v, Draw.of(QUARTZ));
            if (k % 3 == 0) { f.set(-5, y + 2, v, LANTERN); f.set(5, y + 2, v, LANTERN); }
        }
        f.box(-5, y0, -49, 5, top, -44, Draw.of(BLUE));
        f.box(-4, top, -49, 4, top, -44, Draw.of(PACKED));
        f.box(-4, top + 1, -49, 4, top + 3, -42, Draw.AIR);
    }

    // ---- the Crypt of the Frost-Dead, inside the podium -----------------------------------------------------------------------
    static int[] pit(int k) { double a = P_AX0 + k * SECTOR; return new int[]{ri(29 * Math.cos(a)), ri(29 * Math.sin(a))}; }
    static int[] tombAt(int k) { double a = P_AX0 + k * SECTOR; return new int[]{ri(39 * Math.cos(a)), ri(39 * Math.sin(a))}; }

    private void crypt(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (!touches(f, -50, -50, 50, 50)) return;
        Draw.Mat floor = (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? PBRICK : PDARK;
        // the ossuary under the hall, its sarcophagi in a ring
        f.cyl(0, 0, 12, y0 + 1, y0 + 7, Draw.AIR);
        f.disk(0, 0, 12, y0, floor);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int u = ri(9.5 * Math.cos(a)), v = ri(9.5 * Math.sin(a));
            f.set(u, y0 + 1, v, QUARTZ);
            f.set(u, y0 + 2, v, i % 3 == 0 ? BONE : PACKED);
            f.set(ri(11.5 * Math.cos(a)), y0 + 8, ri(11.5 * Math.sin(a)), i % 2 == 0 ? LANTERN : PDARK);
        }
        // the ring passage and the radials: to the five tombs in the points, and to the five doors between them
        f.ring(0, 0, 23, 3, y0 + 1, y0 + 5, Draw.AIR);
        f.ring(0, 0, 23, 3, y0, y0, floor);
        for (int k = 0; k < NB; k++) {
            double a = P_AX0 + k * SECTOR, ca = Math.cos(a), sa = Math.sin(a);
            walk(f, 11 * ca, 11 * sa, 35 * ca, 35 * sa, y0, 1.4, 4, floor);
            double bb = a + SECTOR / 2, cb = Math.cos(bb), sb = Math.sin(bb);
            walk(f, 22 * cb, 22 * sb, 40 * cb, 40 * sb, y0, 1.4, 4, floor);
            f.set(ri(21.5 * cb), y0 + 6, ri(21.5 * sb), LANTERN);
            tomb(f, s, k);
            if (k > 0) {
                int[] q = pit(k);
                f.box(q[0] - 2, y0 - 4, q[1] - 2, q[0] + 2, y0 - 1, q[1] + 2, Draw.of(PDARK));
                f.box(q[0] - 1, y0 - 2, q[1] - 1, q[0] + 1, y0 - 1, q[1] + 1, Draw.AIR);
                f.box(q[0] - 1, y0 - 3, q[1] - 1, q[0] + 1, y0 - 3, q[1] + 1, Draw.of(MAGMA));
                f.box(q[0] - 1, y0, q[1] - 1, q[0] + 1, y0, q[1] + 1, Draw.of(GLASS));
                for (int y = y0 - 2; y <= y0 - 1; y++) f.set(q[0] - 1, y, q[1], b(65, f.facing(Draw.EAST)));
            }
        }
        // the stair up to the Hall of Frozen Kings
        spiral(f, 0, 0, y0 + 1, y0 + POD, Draw.of(QUARTZ), PACKED);
        f.box(-2, y0 + 1, 0, -2, y0 + 3, 0, Draw.AIR);
        f.box(2, y0 + 1, 0, 2, y0 + 3, 0, Draw.AIR);
        f.wallSign(0, y0 + 3, 3, Draw.SOUTH, "THE CRYPT OF", "THE FROST-DEAD", "Mind the thin", "ice, mortal");
        f.wallSign(0, y0 + 3, -3, Draw.NORTH, "Up: the Hall", "of Frozen", "Kings", "");
        f.chest(-11, y0 + 1, 0, Draw.EAST, "jaspr:colossus/rime_rich");
        f.spawner(8, y0 + 1, -6, "lost_soul");
        f.skull(-6, y0 + 1, -8, 0, 3);
        f.skull(7, y0 + 1, 7, 0, 11);
    }

    /** A tomb in one of the podium's points: a sarcophagus, a chest; the north one floored with thin tiles, one full of mist. */
    private void tomb(Draw.Frame f, Colossi.Site s, int k) {
        int y0 = s.y;
        int[] t = tombAt(k);
        int u = t[0], v = t[1];
        double a = P_AX0 + k * SECTOR, ca = Math.cos(a), sa = Math.sin(a);
        f.cyl(u, v, 5, y0 + 1, y0 + 6, Draw.AIR);
        f.disk(u, v, 5, y0, Draw.of(PDARK));
        f.set(u, y0 + 7, v, LANTERN);
        int cu = ri(u + 4 * ca), cv = ri(v + 4 * sa);
        f.chest(cu, y0 + 1, cv, face(-ca, -sa), k == 0 || k == 3 ? "jaspr:colossus/rime_rich" : "jaspr:colossus/rime");
        if (k == 0) {
            // thin tiles: the safe ones snow, the rest packed ice over a drop onto stilled lava
            Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, ((Plan) s.plan).rot);
            int[] box = fr.box(u - 4, y0, v - 3, u + 3, y0, v + 2);
            for (int x = box[0]; x <= box[3]; x++) for (int z = box[2]; z <= box[5]; z++) {
                boolean safe = Ordeals.safeTile(Math.floorDiv(x - box[0], 2), Math.floorDiv(z - box[2], 2), s.salt + 404);
                f.d.set(x, y0, z, safe ? SNOW : PACKED);
                f.d.set(x, y0 - 1, z, safe ? PDARK : MAGMA);
                f.d.set(x, y0 - 2, z, PDARK);
            }
            f.wallSign(1, y0 + 3, -30, Draw.WEST, "Beyond: thin", "tiles. Tread", "only on the", "snow");
            return;
        }
        f.box(u - 1, y0 + 1, v - 1, u + 1, y0 + 1, v + 1, Draw.of(QUARTZ));
        f.box(u - 1, y0 + 2, v - 1, u + 1, y0 + 2, v + 1, Draw.of(PACKED));
        f.skull(u, y0 + 3, v, 0, k * 3);
        f.set(ri(u - 3 * sa), y0 + 5, ri(v + 3 * ca), WEB);
        if (k == 3) f.spawner(ri(u + 3 * sa), y0 + 1, ri(v - 3 * ca), "wight");
    }

    // ---- the keep: the Hall of Frozen Kings, the library, the armoury, the five towers, the roof ------------------------------
    private static Draw.Mat keepWall() {
        return (x, y, z) -> Math.floorMod(y, 6) == 0 ? QUARTZ : Math.floorMod(y, 6) == 3 && Draw.rnd(x, y, z, 17) < 0.5 ? SNOW : PACKED;
    }

    private void keep(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + POD, up = y0 + UP, roof = y0 + ROOF;
        if (!touches(f, -KR - 8, -KR - 8, KR + 8, KR + 8)) return;
        f.cyl(0, 0, KR, fl, roof, keepWall());
        f.cyl(0, 0, KR - 2, fl + 1, up - 1, Draw.AIR);
        f.disk(0, 0, KR - 2, fl, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? QUARTZ : PACKED);
        f.disk(0, 0, KR - 2, up, (x, y, z) -> Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? LANTERN : PDARK);
        f.cyl(0, 0, KR - 2, up + 1, roof - 1, Draw.AIR);
        f.disk(0, 0, KR + 0.6, roof, Draw.of(SNOW));
        f.ring(0, 0, KR + 0.6, 1.3, roof + 1, roof + 1, Draw.of(QUARTZ));
        for (int i = 0; i < 64; i += 2) {
            double a = i * Math.PI / 32;
            f.set(ri((KR + 0.1) * Math.cos(a)), roof + 2, ri((KR + 0.1) * Math.sin(a)), SNOW);
        }
        // tall windows on both floors (none by the towers, the gates, the vault)
        for (int i = 0; i < 30; i++) {
            double a = (i + 0.5) * Math.PI / 15;
            boolean tower = false;
            for (int k = 0; k < NB; k++) if (Math.abs(Math.IEEEremainder(a - AXIS0 - k * SECTOR, 2 * Math.PI)) < 0.28) tower = true;
            if (tower || Math.abs(Math.IEEEremainder(a + Math.PI / 2, 2 * Math.PI)) < 0.15 || Math.abs(Math.IEEEremainder(a - Math.PI, 2 * Math.PI)) < 0.15) continue;
            boolean vault = Math.sin(a) > 0.75;
            for (double rr = 28.6; rr <= 30.1; rr += 0.75) {
                int u = ri(rr * Math.cos(a)), v = ri(rr * Math.sin(a));
                f.box(u, fl + 3, v, u, fl + 7, v, Draw.of(GLASS));
                if (!vault) f.box(u, up + 3, v, u, up + 7, v, Draw.of(GLASS));
            }
        }
        // the north gate
        f.box(-2, fl + 1, -KR - 1, 2, fl + 6, -KR + 3, Draw.AIR);
        f.box(-1, fl + 7, -KR - 1, 1, fl + 7, -KR + 3, Draw.AIR);
        for (int side = -1; side <= 1; side += 2) f.box(side * 3, fl + 1, -KR - 1, side * 3, fl + 8, -KR - 1, Draw.of(QPILLAR));
        f.box(-3, fl + 9, -KR - 1, 3, fl + 9, -KR - 1, Draw.of(QCHISEL));
        f.set(0, fl + 8, -KR - 1, LANTERN);
        // the Hall of Frozen Kings: a ring of pillars, four kings, the stairs up, the stair down to the crypt
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            int u = ri(20 * Math.cos(a)), v = ri(20 * Math.sin(a));
            f.box(u, fl + 1, v, u, up - 1, v, Draw.of(QPILLAR));
            f.set(u, fl + 1, v, PACKED);
            f.set(u, fl + 7, v, LANTERN);
            f.set(u, up - 1, v, QCHISEL);
        }
        double[] kings = {-18, 54, 126, 198};
        for (double kd : kings) {
            double a = Math.toRadians(kd);
            int u = ri(24 * Math.cos(a)), v = ri(24 * Math.sin(a));
            king(f, u, v, dirOf(-u, -v), fl);
        }
        f.wallSign(-6, fl + 4, -18, Draw.SOUTH, "The kings who", "knelt to the", "Lich: they", "kneel still");
        f.chest(ri(27.6 * Math.cos(Math.toRadians(54))), fl + 1, ri(27.6 * Math.sin(Math.toRadians(54))), Draw.NORTH, "jaspr:colossus/rime_rich");
        f.chest(ri(27.6 * Math.cos(Math.toRadians(126))), fl + 1, ri(27.6 * Math.sin(Math.toRadians(126))), Draw.NORTH, "jaspr:colossus/rime");
        for (int side = -1; side <= 1; side += 2) {
            int ua = side < 0 ? -14 : 11, ub = side < 0 ? -11 : 14;
            for (int k = 0; k < UP - POD; k++) {
                int v = -6 + k, y = fl + 1 + k;
                f.box(ua, fl + 1, v, ub, y - 1, v, Draw.of(QUARTZ));
                for (int u = ua; u <= ub; u++) f.set(u, y, v, f.stair(QSTAIR, 2, false));
            }
            f.box(ua, up, -6, ub, up, 4, Draw.AIR);
            f.box(ua - 1, up + 1, -7, ub + 1, up + 1, -7, Draw.of(BARS));
            f.box(ua - 1, up + 1, -7, ua - 1, up + 1, 5, Draw.of(BARS));
            f.box(ub + 1, up + 1, -7, ub + 1, up + 1, 5, Draw.of(BARS));
        }
        f.box(-2, fl + 1, -2, 2, fl + 1, 2, Draw.AIR);
        f.box(-2, fl + 1, -2, 2, fl + 1, -2, Draw.of(BARS));
        f.box(-2, fl + 1, 2, 2, fl + 1, 2, Draw.of(BARS));
        f.box(2, fl + 1, -1, 2, fl + 1, 1, Draw.of(BARS));
        // the upper floor: the library (west) and the armoury (east) either side of a hall; the vault (south) shut off
        Draw.Mat inner = Draw.of(PACKED);
        f.box(-3, up + 1, -27, -3, roof - 1, 12, inner);
        f.box(3, up + 1, -27, 3, roof - 1, 12, inner);
        f.box(-13, up + 1, 13, 13, roof - 1, 13, inner);
        f.box(-13, up + 1, 13, -13, roof - 1, 28, inner);
        f.box(13, up + 1, 13, 13, roof - 1, 28, inner);
        f.box(-3, up + 1, -1, -3, up + 3, 1, Draw.AIR);
        f.box(3, up + 1, -1, 3, up + 3, 1, Draw.AIR);
        f.box(-2, up, -26, 2, up, 12, Draw.of(CARPET));
        f.box(-2, up, -26, 2, up, 12, Draw.of(PDARK));
        for (int v = -24; v <= 10; v += 1) f.set(0, up + 1, v, CARPET);
        library(f, s);
        armoury(f, s);
        // the vault of the Lich's hoard (its stair comes down from behind the throne)
        for (int u = -6; u <= 6; u += 4) f.chest(u, up + 1, 27, Draw.NORTH, "jaspr:colossus/rime_vault");
        f.chest(-12, up + 1, 20, Draw.EAST, "jaspr:colossus/rime_vault");
        for (int side = -1; side <= 1; side += 2) {
            int pu = side * 8;
            f.set(pu, up + 1, 19, QPILLAR);
            f.box(pu - 1, up + 2, 18, pu + 1, up + 4, 20, Draw.of(GLASS));
            f.set(pu, up + 3, 19, LANTERN);
            f.skull(pu + side * 2, up + 1, 23, 0, side < 0 ? 4 : 12);
        }
        f.wallSign(5, up + 3, 14, Draw.SOUTH, "The hoard of", "the Rime Lich", "", "");
        // the five towers round the keep; the north-east one holds the stair to the roof and its own top
        for (int k = 0; k < NB; k++) {
            double th = AXIS0 + k * SECTOR, tu = KR * Math.cos(th), tv = KR * Math.sin(th);
            f.cyl(tu, tv, 6, fl, y0 + TOWER, (x, y, z) -> Math.floorMod(y, 5) == 0 ? LBLUE : SNOW);
            f.ring(tu, tv, 6.8, 1.0, y0 + TOWER, y0 + TOWER + 1, Draw.of(PACKED));
            f.disk(tu, tv, 5.5, y0 + TOWER, Draw.of(PACKED));
            for (int q = 0; q < 6; q++) {
                double a = q * Math.PI / 3 + th;
                if (Math.cos(a - th) < -0.6) continue;
                int wu = ri(tu + 5.9 * Math.cos(a)), wv = ri(tv + 5.9 * Math.sin(a));
                f.box(wu, y0 + 40, wv, wu, y0 + 44, wv, Draw.of(GLASS));
                f.set(ri(tu + 5 * Math.cos(a)), y0 + 42, ri(tv + 5 * Math.sin(a)), LANTERN);
            }
            if (k == 0) {
                int su = ri(tu), sv = ri(tv);
                spiral(f, su, sv, up + 1, y0 + TOWER, Draw.of(SNOW), PACKED);
                f.box(su, up + 1, sv + 2, su, up + 3, sv + 6, Draw.AIR);
                f.box(su, roof + 1, sv + 2, su, roof + 3, sv + 6, Draw.AIR);
                f.box(su - 1, up + 4, sv + 2, su + 1, up + 4, sv + 6, Draw.of(PACKED));
                f.wallSign(su, up + 4, sv + 7, Draw.SOUTH, "The stair to", "the roof and", "the Lich", "");
            } else spire(f, s, tu, tv, y0 + TOWER + 1, 5.6, 40);
        }
    }

    /** A frozen king: a robed figure on a plinth, crowned, a sword planted before him. */
    private static void king(Draw.Frame f, int u, int v, int dir, int y) {
        int fu = dir == 0 ? 1 : dir == 1 ? -1 : 0, fv = dir == 2 ? 1 : dir == 3 ? -1 : 0, su = -fv, sv = fu;
        f.box(u - 2, y + 1, v - 2, u + 2, y + 1, v + 2, Draw.of(QUARTZ));
        f.box(u - 1, y + 2, v - 1, u + 1, y + 4, v + 1, Draw.of(BLUE));
        f.box(u - 1, y + 5, v - 1, u + 1, y + 5, v + 1, Draw.of(LBLUE));
        f.set(u + 2 * su, y + 5, v + 2 * sv, PACKED); f.set(u - 2 * su, y + 5, v - 2 * sv, PACKED);
        f.set(u + 2 * su, y + 4, v + 2 * sv, PACKED); f.set(u - 2 * su, y + 4, v - 2 * sv, PACKED);
        f.set(u, y + 6, v, PACKED);
        f.set(u, y + 7, v, PACKED);
        f.set(u + su, y + 8, v + sv, GOLDISH); f.set(u - su, y + 8, v - sv, GOLDISH);
        f.set(u + fu, y + 8, v + fv, GOLDISH); f.set(u - fu, y + 8, v - fv, GOLDISH);
        f.set(u + 2 * fu, y + 2, v + 2 * fv, BARS); f.set(u + 2 * fu, y + 3, v + 2 * fv, BARS);
        f.set(u + 2 * fu, y + 4, v + 2 * fv, QCHISEL);
    }

    private void library(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, up = y0 + UP, roof = y0 + ROOF;
        for (int su : new int[]{-25, -21, -17}) {
            int half = (int) Math.floor(Math.sqrt(27 * 27 - su * su)) - 1;
            for (int v = -half; v <= Math.min(half, 11); v++) {
                if (Math.abs(v) <= 2) continue;
                f.box(su, up + 1, v, su, up + 3, v, (x, y, z) -> Draw.rnd(x, y, z, 23) < 0.18 ? PACKED : SHELF);
                f.set(su, up + 4, v, Draw.rnd(su, 4, v, 29) < 0.5 ? SNOW : PACKED);
            }
        }
        for (int v = -12; v <= -6; v += 6) {
            f.box(-9, up + 1, v, -6, up + 1, v + 2, Draw.of(PACKED));
            f.box(-9, up + 2, v, -6, up + 2, v + 2, Draw.of(QSLAB_TOP));
        }
        f.set(-20, roof, -8, LANTERN); f.set(-20, roof, 8, LANTERN); f.set(-9, roof, -2, LANTERN); f.set(-9, roof, 9, LANTERN);
        f.set(-24, up + 4, -6, WEB); f.set(-18, up + 4, 15, WEB);
        f.chest(-4, up + 1, -12, Draw.WEST, "jaspr:colossus/rime");
        f.chest(-4, up + 1, 8, Draw.WEST, "jaspr:colossus/rime_rich");
        f.spawner(-8, up + 1, 8, "coolmar_spider");
        f.wallSign(-4, up + 4, 0, Draw.WEST, "THE ICE", "LIBRARY", "Its pages froze", "mid-sentence");
        // the bridge door in the west wall
        f.box(-31, up + 1, -1, -27, up + 3, 1, Draw.AIR);
        f.box(-31, up + 4, 0, -27, up + 4, 0, Draw.AIR);
    }

    private void armoury(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, up = y0 + UP, roof = y0 + ROOF;
        for (int ru : new int[]{17, 21, 25}) {
            int half = (int) Math.floor(Math.sqrt(27 * 27 - ru * ru)) - 1;
            for (int v = -Math.min(half, 15); v <= Math.min(half, 11); v++) {
                if (Math.abs(v) <= 2) continue;
                f.set(ru, up + 1, v, PDARK);
                f.set(ru, up + 2, v, BARS);
                f.set(ru, up + 3, v, Math.floorMod(v, 3) == 0 ? ROD : BARS);
            }
        }
        for (int v = -14; v <= 10; v += 6) {
            if (Math.abs(v) <= 3) continue;
            f.set(8, up + 1, v, PACKED);
            f.set(8, up + 2, v, LBLUE);
            f.skull(8, up + 3, v, 0, 4);
            f.set(9, up + 2, v, PACKED); f.set(7, up + 2, v, PACKED);
        }
        f.set(20, roof, -8, LANTERN); f.set(20, roof, 8, LANTERN); f.set(9, roof, -2, LANTERN); f.set(9, roof, 9, LANTERN);
        f.chest(4, up + 1, -12, Draw.EAST, "jaspr:colossus/rime");
        f.chest(4, up + 1, 8, Draw.EAST, "jaspr:colossus/rime_rich");
        f.wallSign(4, up + 4, 0, Draw.EAST, "THE ARMOURY", "Blades that", "never warmed", "a hand");
    }

    // ---- the throne hall under the crystal dome --------------------------------------------------------------------------------
    private void throne(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + ROOF, sp = y0 + SPRING;
        if (!touches(f, -DR - 2, -DR - 2, DR + 2, DR + 2)) return;
        f.cyl(0, 0, DR, fl, sp, (x, y, z) -> Math.floorMod(y, 4) == 0 ? LBLUE : QUARTZ);
        f.cyl(0, 0, DR - 2, fl + 1, sp, Draw.AIR);
        final Draw.Frame ff = f;
        f.disk(0, 0, DR - 2, fl, (x, y, z) -> {
            int u = lu(ff, x, z), v = lv(ff, x, z);
            double r = Math.sqrt(u * u + v * v);
            if (r < 1.5) return LANTERN;
            if (Math.abs(r - 8) < 0.6 || Math.abs(r - 15) < 0.6) return BLUE;
            if (Math.abs(r - 11.5) < 0.5 && (u + v & 1) == 0) return LANTERN;
            return ((u + v) & 1) == 0 ? QUARTZ : PACKED;
        });
        // windows in the drum, the crystal dome over it, a chandelier of sea lanterns
        for (int i = 0; i < 16; i++) {
            double a = (i + 0.5) * Math.PI / 8;
            for (double rr = DR - 1.6; rr <= DR + 0.1; rr += 0.8) f.box(ri(rr * Math.cos(a)), fl + 6, ri(rr * Math.sin(a)), ri(rr * Math.cos(a)), sp - 2, ri(rr * Math.sin(a)), Draw.of(GLASS));
        }
        dome(f, sp);
        f.box(0, sp + 12, 0, 0, sp + DR - 1, 0, Draw.of(BARS));
        int crown = Math.min(sp + DR + 9, s.ceilAt(s.x, s.z) - 2);
        f.d.tube(f.xd(0, 0), f.zd(0, 0), 1.8, 0.2, 0, sp + DR + 1, crown, (x, y, z) -> y == sp + DR + 1 ? LANTERN : PACKED);
        f.box(-1, sp + 10, -1, 1, sp + 11, 1, Draw.of(LANTERN));
        for (int q = 0; q < 4; q++) f.set(q == 0 ? 2 : q == 1 ? -2 : 0, sp + 10, q == 2 ? 2 : q == 3 ? -2 : 0, b(198, 0));
        // the pillars (cover from his arrows)
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int u = ri(16 * Math.cos(a)), v = ri(16 * Math.sin(a));
            f.box(u, fl + 1, v, u, sp - 1, v, Draw.of(QPILLAR));
            f.set(u, fl + 1, v, PACKED);
            f.set(u, fl + 5, v, LANTERN);
            f.set(u, sp - 1, v, QCHISEL);
        }
        // the north door from the roof terrace
        f.box(-2, fl + 1, -DR - 1, 2, fl + 6, -DR + 3, Draw.AIR);
        f.box(-1, fl + 7, -DR - 1, 1, fl + 7, -DR + 3, Draw.AIR);
        for (int side = -1; side <= 1; side += 2) f.box(side * 3, fl + 1, -DR - 1, side * 3, fl + 8, -DR - 1, Draw.of(QPILLAR));
        f.wallSign(0, fl + 9, -DR - 1, Draw.NORTH, "THE RIME LICH", "Kneel, and be", "kept forever", "in the ice");
        // the dais and the throne
        f.box(-7, fl + 1, 2, 7, fl + 1, 13, Draw.of(PDARK));
        f.box(-5, fl + 2, 4, 5, fl + 2, 13, Draw.of(BLUE));
        f.box(-3, fl + 3, 6, 3, fl + 3, 13, Draw.of(QUARTZ));
        f.set(0, fl + 4, 11, f.stair(QSTAIR, 2, false));
        f.box(-1, fl + 4, 12, 1, fl + 10, 12, Draw.of(PACKED));
        f.box(0, fl + 6, 12, 0, fl + 8, 12, Draw.of(BLUE));
        f.box(-1, fl + 4, 11, -1, fl + 5, 11, Draw.of(PACKED));
        f.box(1, fl + 4, 11, 1, fl + 5, 11, Draw.of(PACKED));
        f.box(0, fl + 11, 12, 0, fl + 14, 12, Draw.of(PACKED));
        f.set(-1, fl + 11, 12, PACKED); f.set(1, fl + 11, 12, PACKED); f.set(-2, fl + 10, 12, PACKED); f.set(2, fl + 10, 12, PACKED);
        for (int side = -1; side <= 1; side += 2) { f.set(side * 4, fl + 3, 12, LANTERN); f.set(side * 6, fl + 2, 6, LANTERN); }
        f.point(0, fl + 4, 8, "lord:" + s.kind.lord);
        // behind the throne the floor is sealed over the stair down to his hoard
        for (int k = 0; k <= 10; k++) {
            int v = 15 + k, y = fl - 1 - k;
            f.box(-1, y + 1, v, 1, Math.min(fl - 1, y + 4), v, Draw.AIR);
            for (int u = -1; u <= 1; u++) f.set(u, y, v, f.stair(QSTAIR, 3, false));
        }
        f.box(-1, fl, 15, 1, fl, 19, Draw.of(SEAL));
        f.box(-2, fl + 1, 20, 2, fl + 1, 20, Draw.of(BARS));
    }

    /** The crystal dome: a hemisphere of ice-blue glass on prismarine ribs, glowing at its crown. */
    private void dome(Draw.Frame f, int sp) {
        final double R2 = DR + 0.4, ri = R2 - 1.6;
        columns(f, -DR - 1, -DR - 1, DR + 1, DR + 1, (x, z, u, v) -> {
            double rr = Math.sqrt(u * (double) u + v * (double) v);
            if (rr > R2) return;
            int hOut = (int) Math.round(Math.sqrt(Math.max(0, R2 * R2 - rr * rr)));
            int hIn = rr < ri ? (int) Math.floor(Math.sqrt(ri * ri - rr * rr)) : -1;
            double ang = Math.atan2(v, u);
            boolean rib = Math.abs(Math.sin(6 * ang)) * rr < 0.9;
            for (int y = sp + 1 + Math.max(hIn, 0); y <= sp + hOut; y++) {
                int h = y - sp, bl;
                if (rr < 1.5 && h >= DR - 1) bl = LANTERN;
                else if (rib || h == 1) bl = PBRICK;
                else if (h == 9 || h == 17) bl = GLASS_W;
                else bl = Draw.rnd(x, y, z, 47) < 0.12 ? GLASS_C : GLASS;
                f.d.set(x, y, z, bl);
            }
            for (int y = sp + 1; y <= sp + hIn; y++) f.d.set(x, y, z, 0);
        });
    }

    // ---- the Frozen Falls, the frozen river and the Ice Bridge (west) -----------------------------------------------------------
    private void falls(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, up = y0 + UP, deck = up;
        if (!touches(f, -82, -10, -26, 10)) return;
        final int salt = s.salt;
        Draw.Mat lavaIce = (x, y, z) -> { double q = Draw.rnd(x, y, z, salt + 93); return q < 0.62 ? MAGMA : q < 0.92 ? PACKED : OBSIDIAN; };
        // the river: from the foot of the falls west to a grated culvert under the rampart
        columns(f, -78, -10, -40, 10, (x, z, u, v) -> {
            double cv = 2.6 * Math.sin((u + 41) / 5.5), hw = 2.7 + 0.9 * Math.sin(u * 0.37 + 1);
            if (Math.abs(v - cv) > hw) return;
            f.d.set(x, y0 - 1, z, MAGMA);
            f.d.set(x, y0, z, lavaIce.at(x, y0, z));
            if (Draw.rnd(x, 2, z, salt + 95) < 0.12) f.d.set(x, y0 + 1, z, PACKED);
        });
        f.box(-82, y0 + 1, -2, -72, y0 + 3, 2, Draw.AIR);
        f.box(-82, y0 + 4, -1, -72, y0 + 4, 1, Draw.AIR);
        f.box(-82, y0, -2, -72, y0, 2, lavaIce);
        f.box(-82, y0 + 1, -2, -82, y0 + 4, 2, Draw.of(BARS));
        // the gargoyle under the bridge's keep end, and the lava frozen as it poured from its mouth
        f.box(-33, up - 5, -1, -30, up - 3, 1, Draw.of(PDARK));
        f.set(-33, up - 3, -1, MAGMA); f.set(-33, up - 3, 1, MAGMA);
        f.set(-34, up - 5, 0, MAGMA);
        int y1 = y0 + POD + 1, y2 = up - 6;
        for (int y = y1; y <= y2; y++) {
            double t = (y2 - y) / (double) (y2 - y1), cu = -34.5 - 2.4 * t * t, rr = 1.1 + 0.4 * Math.sin(y * 1.7) + 1.0 * t;
            f.d.tube(f.xd(cu, 0), f.zd(cu, 0), rr, rr, 0, y, y, lavaIce);
        }
        f.disk(-37, 0, 3.6, y0 + POD, lavaIce);
        f.box(-41, y0 + POD, -2, -36, y0 + POD, 2, lavaIce);
        for (int y = y0 + 1; y < y0 + POD; y++) {
            double t = (y0 + POD - y) / (double) POD;
            int hw = 2 + (int) Math.round(t * 2.2 + 0.6 * Math.sin(y * 1.3));
            f.box(-42 - (int) Math.round(t * 2), y, -hw, -40, y, hw, lavaIce);
        }
        f.d.ellipsoid(f.xd(-44, 0), y0 + 1, f.zd(-44, 0), 4.2, 1.6, 4.8, 0, lavaIce);
        for (int v = -2; v <= 2; v += 2) f.box(-40, y0 + POD - 2 - Math.abs(v), v, -40, y0 + POD - 1, v, Draw.of(PACKED));
        // the Ice Bridge, level with the library's floor and the wall walk
        f.box(-78, deck - 1, -2, -31, deck, 2, Draw.of(PACKED));
        f.box(-78, deck, -1, -31, deck, 1, (x, y, z) -> Math.floorMod(x + z, 3) == 0 ? LBLUE : SNOW);
        for (int side = -1; side <= 1; side += 2) {
            f.box(-71, deck - 1, side * 3, -31, deck + 1, side * 3, Draw.of(SNOW));
            for (int u = -71; u <= -32; u += 2) f.set(u, deck + 2, side * 3, PANE);
        }
        f.box(-80, deck + 1, -2, -31, deck + 5, 2, Draw.AIR);
        for (int pu : new int[]{-47, -61}) {
            f.box(pu - 2, y0 - 1, -3, pu + 2, deck - 2, 3, (x, y, z) -> y <= y0 + 2 ? PDARK : Math.floorMod(y, 5) == 0 ? LBLUE : PACKED);
            f.box(pu - 3, deck - 3, -3, pu + 3, deck - 2, 3, Draw.of(QUARTZ));
        }
        bridgeArch(f, -59, -49, y0 + 12, 7, deck);
        bridgeArch(f, -73, -63, y0 + 13, 6, deck);
        for (int u = -45; u >= -76; u -= 1) if (Draw.rnd(u, 3, 0, salt + 97) < 0.3 && (u > -45 || u < -49) && (u > -59 || u < -63))
            for (int v = -2; v <= 2; v += 4) f.box(u, deck - 2 - (int) (Draw.rnd(u, 4, v, salt + 98) * 3), v, u, deck - 2, v, Draw.of(PACKED));
        for (int u = -40; u >= -72; u -= 14) for (int side = -1; side <= 1; side += 2) {
            f.box(u, deck + 1, side * 3, u, deck + 3, side * 3, Draw.of(QPILLAR));
            f.set(u, deck + 4, side * 3, LANTERN);
        }
        f.wallSign(-69, deck + 2, 3, Draw.WEST, "THE ICE BRIDGE", "to the Library", "Mind the ice", "overhead");
    }

    /** An arch under the bridge between two piers (their faces at u0 and u1), springing at ys and rising rise. */
    private static void bridgeArch(Draw.Frame f, int u0, int u1, int ys, int rise, int deck) {
        double mid = (u0 + u1) / 2.0, half = (u1 - u0) / 2.0 + 1;
        for (int u = u0; u <= u1; u++) {
            double t = (u - mid) / half;
            int top = ys + (int) Math.round(rise * Math.sqrt(Math.max(0, 1 - t * t)));
            f.box(u, top - 1, -2, u, top, 2, Draw.of(QUARTZ));
            if (top + 1 <= deck - 2) f.box(u, top + 1, -2, u, deck - 2, 2, Draw.of(PACKED));
        }
    }

    /** Lamps of ice along the parade ground's roads. */
    private void lamps(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (!touches(f, -70, -70, 70, 70)) return;
        for (int k = 0; k < NB; k++) for (int side = -1; side <= 1; side += 2) {
            double th = AXIS0 + k * SECTOR, a = th + side * SECTOR / 4;
            lamp(f, ri(56.5 * Math.cos(a)), ri(56.5 * Math.sin(a)), y0);
            double px = -Math.sin(th) * side * 4, pz = Math.cos(th) * side * 4;
            lamp(f, ri(64 * Math.cos(th) + px), ri(64 * Math.sin(th) + pz), y0);
        }
        for (int side = -1; side <= 1; side += 2) lamp(f, side * 5, -62, y0);
    }

    private static void lamp(Draw.Frame f, int u, int v, int y0) {
        f.box(u, y0 + 1, v, u, y0 + 3, v, Draw.of(PACKED));
        f.set(u, y0 + 4, v, LANTERN);
        f.set(u, y0 + 5, v, SNOW);
    }

    // ---- the Frozen Legion ------------------------------------------------------------------------------------------------------
    private void legion(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -70, -70, 70, 70)) return;
        for (int[] q : p.soldiers) {
            if (!touches(f, q[0] - 2, q[1] - 2, q[0] + 2, q[1] + 2)) continue;
            soldier(f, q[0], q[1], q[2], s.y, q[3] == 1);
        }
    }

    private static void soldier(Draw.Frame f, int u, int v, int dir, int y, boolean officer) {
        int fu = dir == 0 ? 1 : dir == 1 ? -1 : 0, fv = dir == 2 ? 1 : dir == 3 ? -1 : 0, su = -fv, sv = fu;
        f.set(u, y + 1, v, PACKED);
        f.set(u, y + 2, v, PACKED);
        f.set(u, y + 3, v, officer ? BLUE : LBLUE);
        f.set(u - su, y + 3, v - sv, PACKED);
        f.set(u - su, y + 2, v - sv, officer ? QUARTZ : SNOW);
        for (int k = 1; k <= 5; k++) f.set(u + su, y + k, v + sv, k == 3 ? PACKED : BARS);
        f.set(u + su, y + 6, v + sv, officer ? ROD : BARS);
        if (officer) f.skull(u, y + 4, v, 0, dir == 0 ? 12 : dir == 1 ? 4 : dir == 2 ? 0 : 8);
        else f.set(u, y + 4, v, SNOW);
    }

    // ---- the cavern's own works: ice spikes, frozen ghasts ------------------------------------------------------------------------
    private void cavernWorks(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int[] q : p.spikes) {
            double x = f.xd(q[0], q[1]), z = f.zd(q[0], q[1]);
            if (!f.d.touches((int) x - 2 * q[2] - 5, (int) z - 2 * q[2] - 5, (int) x + 2 * q[2] + 5, (int) z + 2 * q[2] + 5)) continue;
            int wxi = f.x(q[0], q[1]), wzi = f.z(q[0], q[1]);
            int yb = s.floorAt(wxi, wzi), top = Math.min(yb + q[3], s.ceilAt(wxi, wzi) - 3);
            if (top - yb < 5) continue;
            f.d.tube(x, z, q[2] + 0.5, 0.2, 0, yb - 1, top, (xx, yy, zz) -> yy - yb < 2 ? SNOW : PACKED);
            double ox = x + q[2] + 1.5, oz = z - q[2] * 0.5;
            f.d.tube(ox, oz, q[2] * 0.5 + 0.4, 0.2, 0, yb - 1, yb + q[3] / 3, Draw.of(PACKED));
        }
        for (int[] q : p.ghasts) {
            if (!touches(f, q[0] - 8, q[1] - 8, q[0] + 8, q[1] + 8)) continue;
            int u = q[0], v = q[1], yb = s.floorAt(f.x(u, v), f.z(u, v));
            f.box(u - 2, yb, v - 2, u + 2, yb + 4, v + 2, Draw.of(SNOW));
            f.box(u - 2, yb + 4, v - 2, u + 2, yb + 4, v + 2, Draw.of(PACKED));
            int dir = q[2], fu = dir == 0 ? 1 : dir == 1 ? -1 : 0, fv = dir == 2 ? 1 : dir == 3 ? -1 : 0, su = -fv, sv = fu;
            f.set(u + 2 * fu + su, yb + 3, v + 2 * fv + sv, BLACK); f.set(u + 2 * fu - su, yb + 3, v + 2 * fv - sv, BLACK);
            f.set(u + 2 * fu, yb + 1, v + 2 * fv, BLACK); f.set(u + 2 * fu, yb + 2, v + 2 * fv, BLACK);
            for (int t = 0; t < 9; t++) {
                double a = t * Math.PI * 2 / 9;
                f.line(u + 2 * Math.cos(a), yb, v + 2 * Math.sin(a), u + (4 + t % 3) * Math.cos(a), yb, v + (4 + t % 3) * Math.sin(a), 0.5, Draw.of(PACKED));
            }
        }
    }

    // ---- the ordeals ---------------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, up = y0 + UP, fl = y0 + POD, roof = y0 + ROOF;
        // arrows in the ravelin's passage, the Glacier Gate and the keep's gate
        out.add(Ordeals.arrows(fr.box(-2, y0 + 1, -120, 2, y0 + 3, -100), fr.box(-3, y0 + 2, -120, 3, y0 + 2, -100)));
        out.add(Ordeals.arrows(fr.box(-2, y0 + 1, -80, 2, y0 + 3, -68), fr.box(-3, y0 + 2, -80, 3, y0 + 2, -68)));
        out.add(Ordeals.arrows(fr.box(-2, fl + 1, -31, 2, fl + 3, -27), fr.box(-3, fl + 2, -31, 3, fl + 2, -27)));
        // the frost cannon on the ravelin watches the causeway
        out.add(Ordeals.cannon(fr.box(-14, y0 - 2, -170, 14, y0 + 26, -125), fr.at(0, y0 + 13, -121)));
        // the crypt: thin ice over its pits, ice from its vaults, the mist tomb, the tiles of the north tomb
        for (int k = 1; k < NB; k++) {
            int[] q = pit(k);
            out.add(Ordeals.collapse(fr.box(q[0] - 1, y0, q[1] - 1, q[0] + 1, y0, q[1] + 1), GLASS));
        }
        out.add(Ordeals.rubble(fr.box(18, y0 + 1, -3, 24, y0 + 4, 3), SNOW));
        out.add(Ordeals.rubble(fr.box(-24, y0 + 1, -3, -18, y0 + 4, 3), SNOW));
        int[] mist = tombAt(2);
        out.add(Ordeals.gas(fr.box(mist[0] - 4, y0 + 1, mist[1] - 4, mist[0] + 4, y0 + 4, mist[1] + 4)));
        int[] tiles = tombAt(0);
        out.add(Ordeals.path(fr.box(tiles[0] - 4, y0, tiles[1] - 3, tiles[0] + 3, y0, tiles[1] + 2), PACKED, s.salt + 404));
        // ice from the cavern's roof on the Ice Bridge
        out.add(Ordeals.rubble(fr.box(-60, up + 1, -2, -38, up + 3, 2), SNOW));
        // the Lich's hoard, under the floor behind his throne
        out.add(Ordeals.bossSeal("rime_hoard", fr.box(-1, roof, 15, 1, roof, 19), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -------------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double n = Draw.fbm(x, z, 26, s.salt + 71);
        if (n > 0.22) return PACKED;
        if (n < -0.3 || s.f(x, z) > 0.88) return ICY;
        return Draw.rnd(x, 0, z, s.salt + 61) < 0.05 ? PACKED : SNOW;
    }

    @Override Draw.Mat under() {
        return (x, y, z) -> {
            int band = Math.floorMod(y + (int) Math.floor(Draw.noise(x, z, 23, 9151) * 3.5), 9);
            return band < 5 ? ICY : band < 8 ? PACKED : SNOW;
        };
    }

    @Override boolean dry(Colossi.Site s, int x, int z) {
        if (s.dist(x, z) < 128) return true;
        Plan p = (Plan) s.plan;
        if (p == null) return false;
        int u, v;
        switch (p.rot) { case 1: u = z - s.z; v = s.x - x; break; case 2: u = s.x - x; v = s.z - z; break; case 3: u = s.z - z; v = x - s.x; break; default: u = x - s.x; v = z - s.z; }
        return Math.abs(u) < 9 && v < 0;
    }

    @Override double lakeLevel() { return 0.3; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        if (lake) {
            if (f < 0.86) { d.set(x, floor, z, frozen(x, z, s.salt)); d.set(x, floor - 1, z, MAGMA); }
            return;
        }
        double q = Draw.rnd(x, 7, z, s.salt + 52);
        if (q < 0.016 && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {
            int len = 1 + (int) (Draw.rnd(x, 8, z, s.salt + 53) * 8);
            for (int k = 0; k < len && ceil - k > floor + 6; k++) d.set(x, ceil - k, z, PACKED);
        }
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.0025);
        if (s.dist(x, z) < 144) return;
        double w = Draw.rnd(x, 9, z, s.salt + 54);
        if (w < 0.012) d.set(x, floor + 1, z, SNOW);
        else if (w < 0.016) { d.set(x, floor + 1, z, PACKED); if (w < 0.014) d.set(x, floor + 2, z, PACKED); }
        else if (w < 0.0172) d.set(x, floor + 1, z, BONE);
    }
}
