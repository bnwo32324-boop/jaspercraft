package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Amethyst Sanctum (2026-10-04, owner: "big, big, big structures"): a colossal geode, a faceted crystal ellipsoid
 * 108 blocks across and 62 high, half sunk in a cavern of purple stone, crystal spires jutting from its shell and from
 * the ground around it, a ring of floating crystal isles circling it.
 * <ul>
 *   <li>the Avenue of Prisms, between crystal obelisks, to the forecourt and the Crystal Gate (flame vents), over which a
 *       glowing fissure splits the geode;</li>
 *   <li>the undercroft: the Vestibule of Echoes (crystal shards fall), the Scriptorium of her prophecies, the Hall of
 *       Facets (only the opaque tiles hold), and the Rising Stair up into the Geode Chamber;</li>
 *   <li>the Geode Chamber, lined with crystal clusters, where the Amethyst Oracle waits on her dais inside a crown of
 *       crystals; behind her the Void Well drops to its foot and to the Deep Vault, sealed until she falls;</li>
 *   <li>the Spiral Galleries winding twice round the inner wall (arrow slits), with the Meditation Cells in crystal
 *       bubbles on the shell, the doors to the isles, and at the top the stair to the Prism Observatory on the crown;</li>
 *   <li>the eight Floating Isles: shrines, prism cannons, the Isle of Ascent with its stair from the cavern floor, narrow
 *       crystal bridges (two with falling spans over hanging cradles), ghasts drifting among them.</li>
 * </ul>
 */
final class ColossusSanctum extends ColossusDesign {
    // ---- geometry (heights above the site's floor level s.y; local frame u east, v south before the site's turn) ----------
    static final int GR = 54, GH = 50, GC = 12;     // the geode: outer radii (across, up) and the height of its centre
    static final int IR = 49, IH = 45;              // its hollow
    static final int FL = 14;                       // the Geode Chamber's floor block
    static final int TOP = 51;                      // the spiral galleries' last walking level
    static final int OBS = 62;                      // the observatory's floor block
    static final int FOOT = -6;                     // the Void Well's foot (floor block)
    static final int DAIS_V = 2, WELL_V = 27, WALK = 6;
    static final double PHI0 = -Math.PI / 2;         // the spiral galleries start (and end) at the north wall
    static final double[] PODS = {0.37, 0.62, 0.87, 1.12, 1.37, 1.62};
    static final double[] DOORS = {1.25, 1.5, 1.75};   // east, south, west on the upper turn: the bridges to the isles
    static final int[] ISLE_TOP = {0, 31, 0, 34, 0, 39, 28, 32};   // isle floor blocks above s.y (the door isles follow their doors)
    static final String LOOT = "jaspr:colossus/sanctum", RICH = "jaspr:colossus/sanctum_rich", VAULT = "jaspr:colossus/sanctum_vault";

    static final class Plan {
        int rot, pathSalt;
        final List<double[]> grove = new ArrayList<>();      // crystals on the cavern floor: u, y, v, du, dy, dv, length, radius, colour, spine
        final List<double[]> shell = new ArrayList<>();      // crystals jutting from the geode
        final List<double[]> clusters = new ArrayList<>();   // the chamber's crystals
        final List<double[]> hang = new ArrayList<>();       // crystals under the isles
        int[][] isles;                                       // u, top (floor block), v, radius
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette ------------------------------------------------------------------------------------------------------------
    private static final int PURPUR = b(201), PILLAR = b(202, 0), END_BRICK = b(206), OBSIDIAN = b(49), SEA = b(169), GLOW = b(89);
    private static final int G_PURPLE = b(95, 10), G_MAGENTA = b(95, 2), G_PINK = b(95, 6), G_BLUE = b(95, 11), G_LBLUE = b(95, 3), G_WHITE = b(95, 0), G_BLACK = b(95, 15);
    private static final int C_PURPLE = b(251, 10), C_MAGENTA = b(251, 2), C_PINK = b(251, 6), C_BLUE = b(251, 11), C_BLACK = b(251, 15);
    private static final int T_PURPLE = b(159, 10), T_MAGENTA = b(159, 2), T_BLACK = b(159, 15);
    private static final int MAGMA = b(213), RACK = b(87), FIRE = b(51), BOOKS = b(47), BARS = b(101), DARK_OAK = b(5, 5);
    private static final int SLAB = b(205, 0), CARPET = b(171, 10), CARPET_M = b(171, 2), CARPET_B = b(171, 15);
    private static final int ROD_UP = b(198, 1), ROD_DOWN = b(198, 0), PSTAIR = 203, GLAZED = b(237, 0), PANE = b(160, 2);
    private static final int TRAP = G_PINK;           // the thin crystal of the bridges' falling spans
    private static final int SEAL = G_MAGENTA;        // the Deep Vault's crystal door (the Oracle's seal)
    private static final int FALSE_TILE = G_PURPLE;   // the Hall of Facets' tiles that do not hold
    private static final int[] SKIN = {G_PURPLE, G_MAGENTA, G_PINK, G_PURPLE, G_BLUE};
    private static final int[] CORE = {C_PURPLE, C_MAGENTA, C_PINK, PURPUR, C_BLUE};

    private static int amethyst() { return Blocks.AMETHYST_BLOCK > 0 ? Blocks.AMETHYST_BLOCK : PURPUR; }

    // ---- shapes shared by the plan and the drawing ----------------------------------------------------------------------------
    static double sq(double a) { return a * a; }
    /** The hollow's radius at height y (0 above or below it). */
    static double rIn(double y, double cy) { double t = (y - cy) / IH; return Math.abs(t) >= 1 ? 0 : IR * Math.sqrt(1 - t * t); }
    /** The shell's outer radius at height y. */
    static double rOut(double y, double cy) { double t = (y - cy) / GH; return Math.abs(t) >= 1 ? 0 : GR * Math.sqrt(1 - t * t); }
    /** The spiral galleries' walking level at fraction fr (0 at the chamber floor, 2 at the top, one per turn). */
    static double walk(int y0, double fr) { return y0 + FL + 1 + fr * (TOP - FL - 1) / 2.0; }
    /** The galleries' outer edge at walking level S: the hollow's radius at head height. */
    static double wallAt(double S, double cy) { return rIn(S + 2.5, cy); }
    static double fracAt(double phi) { double a = (phi - PHI0) % (2 * Math.PI); if (a < 0) a += 2 * Math.PI; return a / (2 * Math.PI); }

    static int wx(Colossi.Site s, int rot, double u, double v) {
        switch (rot & 3) { case 1: return (int) Math.round(s.x - v); case 2: return (int) Math.round(s.x - u); case 3: return (int) Math.round(s.x + v); default: return (int) Math.round(s.x + u); }
    }
    static int wz(Colossi.Site s, int rot, double u, double v) {
        switch (rot & 3) { case 1: return (int) Math.round(s.z + u); case 2: return (int) Math.round(s.z - v); case 3: return (int) Math.round(s.z - u); default: return (int) Math.round(s.z + v); }
    }
    static double lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    static double lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }

    /** The world columns of a local box, clipped to the draw box (null when it misses). */
    static int[] cols(Draw.Frame f, double u0, double v0, double u1, double v1) {
        double xa = f.xd(u0, v0), xb = f.xd(u1, v1), za = f.zd(u0, v0), zb = f.zd(u1, v1);
        int x0 = Math.max((int) Math.floor(Math.min(xa, xb)), f.d.x0), x1 = Math.min((int) Math.ceil(Math.max(xa, xb)), f.d.x1);
        int z0 = Math.max((int) Math.floor(Math.min(za, zb)), f.d.z0), z1 = Math.min((int) Math.ceil(Math.max(za, zb)), f.d.z1);
        return x0 > x1 || z0 > z1 ? null : new int[]{x0, z0, x1, z1};
    }

    /** A local box in world coordinates (min corner first), for the ordeals and the path's tiles. */
    static int[] wbox(Colossi.Site s, int rot, int u0, int y0, int v0, int u1, int y1, int v1) {
        return new Ordeals.Frame(s.x, s.z, rot).box(u0, y0, v0, u1, y1, v1);
    }

    // ---- the plan -------------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        p.pathSalt = r.nextInt(1 << 20);
        int y0 = s.y, cy = y0 + GC, fl = y0 + FL;
        // the isles: east, south and west meet the galleries' doors at their level
        p.isles = new int[8][];
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + (i % 2 == 1 && i != 7 ? (r.nextDouble() - 0.5) * 0.12 : 0);
            int top = i == 0 || i == 2 || i == 4 ? doorLevel(y0, i / 2) - 1 : y0 + ISLE_TOP[i];
            p.isles[i] = new int[]{(int) Math.round(Math.cos(a) * 92), top, (int) Math.round(Math.sin(a) * 92), i == 7 ? 14 : 11 + r.nextInt(3)};
        }
        // the grove: great crystals on the cavern floor, a druse of small ones at each foot
        int big = 0;
        for (int t = 0; t < 500 && big < 16; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rr = 60 + r.nextDouble() * 96;
            double u = Math.cos(a) * rr, v = Math.sin(a) * rr;
            if (v < -30 && Math.abs(u) < 30) continue;                   // the avenue and the forecourt
            if (rr > 74 && rr < 112) continue;                           // under the isles
            boolean far = rr >= 112;
            boolean clash = false;
            for (double[] q : p.grove) if (q[7] > 2.2 && Math.hypot(q[0] - u, q[2] - v) < (far ? 24 : 16)) clash = true;
            if (clash) continue;
            double tilt = (far ? 0.25 : 0.12) + r.nextDouble() * (far ? 0.38 : 0.28), twist = (r.nextDouble() - 0.5) * 0.9;
            double du = Math.cos(a + twist) * Math.sin(tilt), dv = Math.sin(a + twist) * Math.sin(tilt), dy = Math.cos(tilt);
            double len = far ? 34 + r.nextDouble() * 30 : 15 + r.nextDouble() * 15, rad = far ? 3.8 + r.nextDouble() * 3.2 : 2.4 + r.nextDouble() * 1.6;
            double by = s.floorAt(wx(s, p.rot, u, v), wz(s, p.rot, u, v)) - 3;
            len = fit(s, p.rot, u, by, v, du, dy, dv, len, rad);
            if (len < 10) continue;
            int colour = pickColour(r);
            p.grove.add(new double[]{u, by, v, du, dy, dv, len, rad, colour, far ? 4 : 0});
            big++;
            int small = 3 + r.nextInt(3);
            for (int k = 0; k < small; k++) {
                double b2 = r.nextDouble() * 2 * Math.PI, off = rad + 1.5 + r.nextDouble() * 4;
                double su = u + Math.cos(b2) * off, sv = v + Math.sin(b2) * off, st = 0.35 + r.nextDouble() * 0.5;
                double sdu = Math.cos(b2) * Math.sin(st), sdv = Math.sin(b2) * Math.sin(st), sdy = Math.cos(st);
                double sy = s.floorAt(wx(s, p.rot, su, sv), wz(s, p.rot, su, sv)) - 1;
                p.grove.add(new double[]{su, sy, sv, sdu, sdy, sdv, 4 + r.nextDouble() * 7, 1.1 + r.nextDouble() * 0.9, colour, 0});
            }
        }
        // the two gate spires and the crossed crystals at the avenue's start
        for (int side = -1; side <= 1; side += 2) {
            double tilt = 0.32 + r.nextDouble() * 0.08;
            p.grove.add(new double[]{side * 19, y0 - 3, -62, side * Math.sin(tilt), Math.cos(tilt), -0.12, 50 + r.nextDouble() * 6, 5.2, 3, 4});
            p.grove.add(new double[]{side * 9, y0 - 2, -140, -side * 0.42, 0.9, 0, 22, 2.2, 1, 3});
        }
        // crystals jutting from the shell (never across the doors, the cells or the gate)
        double[] ang = {20, 68, 112, 158, 202, 248, 292, 338};
        for (int i = 0; i < 30; i++) {
            boolean great = i < ang.length;
            double a = Math.toRadians(great ? ang[i] + (r.nextDouble() - 0.5) * 10 : r.nextDouble() * 360);
            double yb = great ? cy + 8 + r.nextDouble() * 26 : cy - 4 + r.nextDouble() * 42;
            if (!great && !shellFree(y0, cy, a, yb)) continue;
            double rr = rOut(yb, cy) - (great ? 2.5 : 1.5);
            double u = Math.cos(a) * rr, v = Math.sin(a) * rr;
            double nx = Math.cos(a) * rr / (GR * GR), ny = (yb - cy) / (GH * GH), nz = Math.sin(a) * rr / (GR * GR);
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            ny += (great ? 0.35 : 0.1) + r.nextDouble() * 0.3;
            double len = great ? 20 + r.nextDouble() * 16 : 7 + r.nextDouble() * 10, rad = great ? 2.8 + r.nextDouble() * 1.8 : 1.4 + r.nextDouble() * 1.3;
            len = fit(s, p.rot, u, yb, v, nx, ny, nz, len, rad);
            double hl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            while (len > 6 && Math.hypot(u + nx / hl * len, v + nz / hl * len) > 72) len -= 1;     // inside the isles' ring
            p.shell.add(new double[]{u, yb, v, nx, ny, nz, len, rad, pickColour(r), great ? 4 : 0});
        }
        // the chamber: the crown of crystals round the dais
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8, tilt = 0.16 + r.nextDouble() * 0.12;
            p.clusters.add(new double[]{Math.cos(a) * 17, fl - 1, DAIS_V + Math.sin(a) * 17, Math.cos(a) * Math.sin(tilt), Math.cos(tilt), Math.sin(a) * Math.sin(tilt),
                15 + r.nextDouble() * 8, 2.2 + r.nextDouble() * 0.8, i % 2 == 0 ? 3 : 1, 3});
        }
        // crystals hanging from the roof (clear of the galleries, the observatory stair and the Oracle's air)
        int n = 0;
        for (int t = 0; t < 400 && n < 26; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rr = 3 + r.nextDouble() * 36;
            double u = Math.cos(a) * rr, v = Math.sin(a) * rr, yr = cy + IH * Math.sqrt(1 - sq(rr / IR)) + 1;
            double tilt = r.nextDouble() * 0.3;
            double du = -Math.cos(a) * Math.sin(tilt), dv = -Math.sin(a) * Math.sin(tilt), dy = -Math.cos(tilt);
            double len = 7 + r.nextDouble() * 12, rad = 1.4 + r.nextDouble() * 1.6;
            if (yr + dy * len < fl + 24) continue;
            if (!clearOfGalleries(y0, cy, u, yr, v, du, dy, dv, len, rad)) continue;
            p.clusters.add(new double[]{u, yr, v, du, dy, dv, len, rad, pickColour(r), len > 13 ? 3 : 0});
            n++;
        }
        // crystals on the inner wall between the turns of the galleries
        n = 0;
        for (int t = 0; t < 400 && n < 32; t++) {
            double fr = r.nextDouble() * 2, S = walk(y0, fr), phi = PHI0 + 2 * Math.PI * fr;
            double yb = S + 7 + r.nextDouble() * (fr < 1 ? 6 : 10);
            if (yb > cy + IH - 4) continue;
            boolean near = false;
            for (double q : PODS) if (Math.abs(q - fr) < 0.05) near = true;
            for (double q : DOORS) if (Math.abs(q - fr) < 0.05) near = true;
            if (near) continue;
            double rr = rIn(yb, cy) + 1.2, len = 4 + r.nextDouble() * 6, rad = 1.1 + r.nextDouble() * 1.1;
            double du = -Math.cos(phi), dv = -Math.sin(phi), dy = (r.nextDouble() - 0.3) * 0.5;
            if (yb + Math.min(0, dy) * len - rad < S + 5.5) continue;
            if (!clearOfGalleries(y0, cy, Math.cos(phi) * rr, yb, Math.sin(phi) * rr, du, dy, dv, len, rad)) continue;
            p.clusters.add(new double[]{Math.cos(phi) * rr, yb, Math.sin(phi) * rr, du, dy, dv, len, rad, pickColour(r), 0});
            n++;
        }
        // crystals on the chamber floor, near the wall
        n = 0;
        for (int t = 0; t < 400 && n < 18; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rr = 24 + r.nextDouble() * 20, u = Math.cos(a) * rr, v = Math.sin(a) * rr;
            if (Math.hypot(u, v - DAIS_V) < 24 || Math.hypot(u, v - WELL_V) < 12 || (Math.abs(u) < 9 && v < -12)) continue;
            if (rr > 33 && fracAt(Math.atan2(v, u)) < 0.4) continue;
            double s2 = (r.nextDouble() - 0.5) * 0.8;
            double du = Math.cos(a) * s2, dv = Math.sin(a) * s2, dy = 1;
            double len = 5 + r.nextDouble() * 8, rad = 1.3 + r.nextDouble() * 1.4;
            if (!clearOfGalleries(y0, cy, u, fl - 1, v, du, dy, dv, len, rad)) continue;
            p.clusters.add(new double[]{u, fl - 1, v, du, dy, dv, len, rad, pickColour(r), 0});
            n++;
        }
        // crystals under the isles
        for (int[] is : p.isles) {
            int k = 3 + r.nextInt(2);
            for (int j = 0; j < k; j++) {
                double a = r.nextDouble() * 2 * Math.PI, off = r.nextDouble() * is[3] * 0.45;
                double u = is[0] + Math.cos(a) * off, v = is[2] + Math.sin(a) * off;
                double tilt = r.nextDouble() * 0.35, len = is[3] * 1.1 + 4 + r.nextDouble() * 8;
                len = Math.min(len, (is[1] - 4 - (y0 + 14)) / Math.cos(tilt));
                p.hang.add(new double[]{u, is[1] - 4, v, Math.cos(a) * Math.sin(tilt), -Math.cos(tilt), Math.sin(a) * Math.sin(tilt),
                    len, 1.6 + r.nextDouble() * 1.6, pickColour(r), 0});
            }
        }
        // the garrisons
        List<Garrison> gs = new ArrayList<>();
        int g0 = y0 + 2;
        gs.add(g(0, g0, -126, "!infernal_knight+wither_skeleton"));
        gs.add(g(7, g0, -96, "skeleton+ashbone_archer"));
        gs.add(g(-14, g0, -66, "zombie_pigman+pigman_berserker"));
        gs.add(g(0, g0, -40, "!royal_guard+crypt_guard"));
        gs.add(g(26, g0, -1, "cinder_witch+flame_adept"));
        gs.add(g(-19, g0, -12, "nethermite+spinout"));
        gs.add(g(-12, fl + 1, -24, "shade+soul_wraith"));
        gs.add(g(30, fl + 1, 16, "brute+hellhound"));
        double[][] gal = {{0.5, 0}, {1.0, 0}, {1.6, 0}};
        String[] galPacks = {"wight+frost", "coolmar_spider+brimstone_spider", "!lost_soul+shade"};
        for (int i = 0; i < gal.length; i++) {
            double fr = gal[i][0], S = walk(y0, fr), phi = PHI0 + 2 * Math.PI * fr, rr = wallAt(S, cy) - 3;
            gs.add(g((int) Math.round(Math.cos(phi) * rr), level(S), (int) Math.round(Math.sin(phi) * rr), galPacks[i]));
        }
        gs.add(g(6, y0 + OBS + 1, 6, "blaze+ember"));
        gs.add(g(4, y0 + FOOT + 1, WELL_V + 4, "crypt_guard+deep_crawler"));
        String[] islePacks = {"magma_cube+salamander", "dread_rider+charred_ghoul", "mummy+tomb_guardian", "magma_hulk+pyre_warden",
            "!infernal_knight+royal_guard", "spore+spore_creeper+mogus", "cinder_imp+ember_legionnaire", "scarab+asp"};
        for (int i = 0; i < 8; i++) {
            int[] is = p.isles[i];
            if (i == 7) gs.add(g(is[0] - 10, is[1] + 1, is[2] + 2, islePacks[i]));
            else gs.add(g(is[0] + (is[0] > 0 ? -5 : 5), is[1] + 1, is[2] + (is[2] > 0 ? -5 : 5), islePacks[i]));
        }
        int gu = 126, gv = 34;
        gs.add(g(gu, s.floorAt(wx(s, p.rot, gu, gv), wz(s, p.rot, gu, gv)) + 1, gv, "hellhound+brute"));
        gu = -124; gv = -40;
        gs.add(g(gu, s.floorAt(wx(s, p.rot, gu, gv), wz(s, p.rot, gu, gv)) + 1, gv, "pigman_berserker+zombie_pigman"));
        // the great fliers in the open air among the isles and over the avenue
        gs.add(g(104, y0 + 45, 34, "ghast+ghastling"));
        gs.add(g(-102, y0 + 45, 40, "ghast+ghastling"));
        gs.add(g(30, y0 + 44, -108, "ghastling+ghastling"));
        gs.add(g(-55, y0 + 50, -30, "ghast+blaze"));
        p.garrisons = complete(gs);
        return p;
    }

    private static int pickColour(Random r) { int k = r.nextInt(12); return k < 5 ? 0 : k < 8 ? 3 : k < 10 ? 1 : k < 11 ? 2 : 4; }

    /** The walking level (garrison y) at a gallery's surface S. */
    static int level(double S) { double s2 = Math.round(S * 2) / 2.0; int n = (int) Math.floor(s2); return s2 > n ? n + 1 : n; }

    /** The doors' walking level (east, south, west). */
    static int doorLevel(int y0, int k) { return (int) Math.round(walk(y0, DOORS[k])); }

    /** A crystal's length shortened until its tip keeps under the cavern's roof and inside the site's reach. */
    private static double fit(Colossi.Site s, int rot, double u, double y, double v, double du, double dy, double dv, double len, double rad) {
        double n = Math.sqrt(du * du + dy * dy + dv * dv);
        du /= n; dy /= n; dv /= n;
        while (len > 8) {
            double tu = u + du * len, tv = v + dv * len, ty = y + dy * len;
            if (Math.hypot(tu, tv) < 176 - rad && ty < s.ceilAt(wx(s, rot, tu, tv), wz(s, rot, tu, tv)) - 3 - rad && ty < 120) break;
            len -= 2;
        }
        return len;
    }

    /** Whether a point of the shell (angle a, height y) is clear of the gate and its fissure, the doors and the cells. */
    private static boolean shellFree(int y0, double cy, double a, double y) {
        if (Math.abs(angle(a, -Math.PI / 2)) < 0.38 || y > cy + 40) return false;
        for (int k = 0; k < 3; k++) if (Math.abs(angle(a, k * Math.PI / 2)) < 0.3 && Math.abs(y - doorLevel(y0, k)) < 13) return false;
        for (double q : PODS) {
            double S = walk(y0, q);
            if (Math.abs(angle(a, PHI0 + 2 * Math.PI * q)) < 0.24 && Math.abs(y - level(S) - 2.5) < 9) return false;
        }
        return true;
    }

    /** The signed difference of two angles, in (-pi, pi]. */
    static double angle(double a, double b) { double d = (a - b) % (2 * Math.PI); if (d > Math.PI) d -= 2 * Math.PI; if (d <= -Math.PI) d += 2 * Math.PI; return d; }

    /** Whether a crystal keeps out of the galleries' walking space and the observatory stair. */
    private static boolean clearOfGalleries(int y0, double cy, double u, double y, double v, double du, double dy, double dv, double len, double rad) {
        double n = Math.sqrt(du * du + dy * dy + dv * dv);
        du /= n; dy /= n; dv /= n;
        for (double t = 0; t <= len; t += 1.5) {
            double pu = u + du * t, pv = v + dv * t, py = y + dy * t, rr = Math.hypot(pu, pv);
            if (Math.abs(pu) < 4 + rad && pv > -17 && pv < 3 && py > y0 + TOP - 3) return false;
            double base = fracAt(Math.atan2(pv, pu));
            for (int k = 0; k < 2; k++) {
                double S = walk(y0, base + k), wall = wallAt(S, cy);
                if (rr > wall - WALK - 1.5 - rad && py > S - 3 - rad && py < S + 5 + rad) return false;
            }
        }
        return true;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int am = amethyst();
        avenue(f, s, p);
        for (double[] c : p.grove) crystal(f, s, c, am);
        geode(f, s, am);
        for (double[] c : p.shell) crystal(f, null, c, am);
        undercroft(f, s, p, am);
        chamber(f, s, p, am);
        spiral(f, s);
        pods(f, s);
        well(f, s);
        observatory(f, s);
        gate(f, s);
        isles(f, s, p, am);
        garrisons(f, p.garrisons);
    }

    /** A crystal from the plan (local coordinates). */
    private static void crystal(Draw.Frame f, Colossi.Site ground, double[] c, int am) {
        double bx = f.xd(c[0], c[2]), bz = f.zd(c[0], c[2]);
        double dx = f.xd(c[0] + c[3], c[2] + c[5]) - bx, dz = f.zd(c[0] + c[3], c[2] + c[5]) - bz;
        int col = (int) c[8];
        crystal(f.d, ground, bx, c[1], bz, dx, c[4], dz, c[6], c[7], SKIN[col], col == 3 ? am : CORE[col], (int) c[9]);
    }

    /**
     * A crystal: a hexagonal prism from (bx, by, bz) along (dx, dy, dz), its end sharpened to a point, a skin of glass over
     * a core (a big one all glass round a spine of sea lanterns, every {@code spine} blocks, so that it glows).
     */
    static void crystal(Draw d, Colossi.Site ground, double bx, double by, double bz, double dx, double dy, double dz, double len, double rad, int skin, int core, int spine) {
        double nl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= nl; dy /= nl; dz /= nl;
        double ex = bx + dx * len, ey = by + dy * len, ez = bz + dz * len, reach = rad * 1.16 + 1;
        int xa = Math.max((int) Math.floor(Math.min(bx, ex) - reach), d.x0), xb = Math.min((int) Math.ceil(Math.max(bx, ex) + reach), d.x1);
        int za = Math.max((int) Math.floor(Math.min(bz, ez) - reach), d.z0), zb = Math.min((int) Math.ceil(Math.max(bz, ez) + reach), d.z1);
        if (xa > xb || za > zb) return;
        int ya = Math.max((int) Math.floor(Math.min(by, ey) - reach), 24), yb = Math.min((int) Math.ceil(Math.max(by, ey) + reach), 122);
        // a frame across the axis
        double hx = Math.abs(dy) < 0.9 ? 0 : 1, hy = Math.abs(dy) < 0.9 ? 1 : 0;
        double e1x = hy * dz, e1y = -hx * dz, e1z = hx * dy - hy * dx;
        double e1l = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= e1l; e1y /= e1l; e1z /= e1l;
        double e2x = dy * e1z - dz * e1y, e2y = dz * e1x - dx * e1z, e2z = dx * e1y - dy * e1x;
        double tip = len - Math.min(len * 0.35, rad * 2.2);
        double fx = ex - bx, fz = ez - bz, fl2 = fx * fx + fz * fz;
        boolean glass = spine > 0;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double px = x - bx, pz = z - bz;
            double tt = fl2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (px * fx + pz * fz) / fl2));
            double ox = px - fx * tt, oz = pz - fz * tt;
            if (ox * ox + oz * oz > reach * reach) continue;
            // under the cavern floor only solid blocks (the cavern's floor fill must find the same column every chunk)
            int buried = ground == null ? Integer.MIN_VALUE : ground.floorAt(x, z);
            for (int y = ya; y <= yb; y++) {
                double py = y - by, t = px * dx + py * dy + pz * dz;
                if (t < 0 || t > len) continue;
                double qx = px - t * dx, qy = py - t * dy, qz = pz - t * dz;
                double a = qx * e1x + qy * e1y + qz * e1z, c = qx * e2x + qy * e2y + qz * e2z;
                double hex = Math.max(Math.abs(a), Math.max(Math.abs(0.5 * a + 0.866 * c), Math.abs(0.5 * a - 0.866 * c)));
                double rr = t > tip ? rad * (len - t) / (len - tip) : rad;
                if (hex > rr + 0.35) continue;
                int v;
                if (y < buried) v = core;
                else if (hex > rr - 0.75) v = skin;
                else if (glass) v = hex < 0.8 && ((int) t) % spine == 0 && t < tip ? SEA : skin;
                else v = core;
                d.set(x, y, z, v);
            }
        }
    }

    // ---- the geode ------------------------------------------------------------------------------------------------------------
    /** The shell column by column: faceted crystal plates outside, a lining of crystal inside, the undercroft's mass below. */
    private void geode(Draw.Frame f, Colossi.Site s, int am) {
        Draw d = f.d;
        int cy = s.y + GC, fl = s.y + FL, ground = s.y + 1;
        int xa = Math.max(d.x0, s.x - GR - 2), xb = Math.min(d.x1, s.x + GR + 2), za = Math.max(d.z0, s.z - GR - 2), zb = Math.min(d.z1, s.z + GR + 2);
        if (xa > xb || za > zb) return;
        double ro = GR + 0.35, ho = GH + 0.35, rb = GR + 1.45, hb = GH + 1.45, ri = IR + 0.35, hi = IH + 0.35;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double ux = x - s.x, vz = z - s.z, rho2 = ux * ux + vz * vz;
            if (rho2 > rb * rb) continue;
            double qb = rho2 / (rb * rb), qo = rho2 / (ro * ro), qi = rho2 / (ri * ri);
            int top = Math.min(122, (int) Math.floor(cy + hb * Math.sqrt(1 - qb)));
            double u = lu(f, x, z), v = lv(f, x, z);
            for (int y = 24; y <= top; y++) {
                double dy = y - cy, ei = qi + dy * dy / (hi * hi);
                if (y > fl && ei < 1) {           // the chamber: skip to its roof
                    y = (int) Math.ceil(cy + hi * Math.sqrt(1 - qi)) - 1;
                    continue;
                }
                double eo = qo + dy * dy / (ho * ho);
                boolean crack = y > cy + 3 && y < cy + 38 && v < -8 && crack(u, y, cy, s.salt);
                int val;
                if (eo <= 1) {
                    double depth = (1 - Math.sqrt(eo)) * GH;
                    if (y <= fl && ei < 1) val = core(x, y, z, s.salt);
                    else if (crack) val = depth < 1.7 ? G_MAGENTA : G_PINK;
                    else if (y >= ground && depth < 1.7) val = facetMat(facet(x, y, z, s.salt));
                    else if (y > fl && (Math.sqrt(ei) - 1) * IH < 1.7) val = lining(x, y, z, s.salt, am);
                    else val = Draw.rnd(x, y, z, s.salt + 7) < 0.35 ? C_PURPLE : T_PURPLE;
                } else {
                    if (y < ground || crack) continue;
                    long fc = facet(x, y, z, s.salt);
                    if (((int) (fc >> 1) >>> 20 & 3) != 0) continue;       // only the raised plates
                    val = facetMat(fc);
                }
                d.set(x, y, z, val);
            }
        }
    }

    /** The glowing fissure over the gate, on the north face. */
    private static boolean crack(double u, int y, int cy, int salt) {
        double t = (y - cy - 3) / 35.0;
        double w = 0.7 + 3.2 * Math.sin(Math.PI * t), mid = 4.2 * Draw.noise(y, 3, 7, salt + 5);
        return Math.abs(u - mid) < w;
    }

    private static int core(int x, int y, int z, int salt) { double q = Draw.rnd(x, y, z, salt + 3); return q < 0.7 ? T_PURPLE : q < 0.9 ? PURPUR : OBSIDIAN; }

    private static int lining(int x, int y, int z, int salt, int am) {
        double q = Draw.rnd(x, y, z, salt + 9);
        return q < 0.3 ? G_PURPLE : q < 0.55 ? am : q < 0.7 ? G_MAGENTA : q < 0.82 ? C_MAGENTA : q < 0.94 ? C_PURPLE : q < 0.98 ? SEA : GLOW;
    }

    /** The facet (a Voronoi cell of a jittered 9-block lattice) of a block: its seed's hash, and bit 0 set on a facet's edge. */
    static long facet(int x, int y, int z, int salt) {
        final int c = 10;
        int cx = Math.floorDiv(x, c), cyy = Math.floorDiv(y, c), cz = Math.floorDiv(z, c);
        double best = 1e18, second = 1e18;
        int bestHash = 0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) {
            int gx = cx + i, gy = cyy + j, gz = cz + k, h = Draw.hash(gx, gy, gz, salt + 101);
            double sx = gx * c + (h & 255) / 255.0 * c, sy = gy * c + ((h >>> 8) & 255) / 255.0 * c, sz = gz * c + ((h >>> 16) & 255) / 255.0 * c;
            double dd = (x - sx) * (x - sx) + (y - sy) * (y - sy) + (z - sz) * (z - sz);
            if (dd < best) { second = best; best = dd; bestHash = h; } else if (dd < second) second = dd;
        }
        boolean edge = Math.sqrt(second) - Math.sqrt(best) < 0.85;
        return ((long) bestHash << 1) | (edge ? 1 : 0);
    }

    private static int facetMat(long fc) {
        if ((fc & 1) != 0) return OBSIDIAN;
        int h = (int) (fc >> 1), m = (h >>> 24) & 15;
        switch (m) {
            case 0: case 1: case 2: case 3: return PURPUR;
            case 4: case 5: return C_PURPLE;
            case 6: case 7: return G_PURPLE;
            case 8: return T_PURPLE;
            case 9: return PILLAR;
            case 10: case 11: return C_MAGENTA;
            case 12: return G_MAGENTA;
            case 13: return T_MAGENTA;
            case 14: return G_PINK;
            default: return ((h >>> 28) & 1) == 0 ? G_BLUE : C_BLUE;
        }
    }

    // ---- the undercroft ---------------------------------------------------------------------------------------------------------
    /** The Vestibule of Echoes, the Rising Stair, the Scriptorium and the Hall of Facets, under the chamber's floor. */
    private void undercroft(Draw.Frame f, Colossi.Site s, Plan p, int am) {
        int y0 = s.y, yb = y0 + 2, ceil = y0 + 12, fl = y0 + FL;
        if (cols(f, -42, -50, 42, 16) == null) return;
        Draw.Mat checker = (x, y, z) -> ((x + z) & 1) == 0 ? END_BRICK : PURPUR;
        // the Vestibule of Echoes
        room(f, -10, -47, 10, -32, yb, ceil, checker, Draw.of(PURPUR), Draw.mix(C_PURPLE, SEA, 0.05, s.salt + 31));
        for (int u = -6; u <= 6; u += 12) for (int v = -43; v <= -36; v += 7) {
            f.box(u, yb, v, u, ceil - 1, v, Draw.of(PILLAR));
            f.set(u, ceil - 1, v, GLOW);
        }
        for (int i = 0; i < 6; i++) {         // crystals from the walls
            int side = (i & 1) == 0 ? -1 : 1, v = -45 + i * 2;
            crystal(f.d, null, f.xd(side * 9.5, v), yb + 5 + (i % 3), f.zd(side * 9.5, v), f.xd(-side, 0) - f.xd(0, 0), -0.3, f.zd(-side, 0) - f.zd(0, 0), 4, 1.3, i % 2 == 0 ? G_MAGENTA : G_PURPLE, C_MAGENTA, 0);
        }
        f.chest(-9, yb, -34, Draw.EAST, LOOT);
        f.chest(9, yb, -45, Draw.WEST, LOOT);
        f.wallSign(-7, yb + 2, -33, Draw.NORTH, "Rise to the", "Geode Chamber.", "She is waiting", "");
        f.wallSign(9, yb + 2, -36, Draw.WEST, "East:", "the Scriptorium", "West: the Hall", "of Facets");
        f.spawner(-8, yb, -45, "crypt_guard");
        // the Rising Stair up through the chamber's floor
        f.box(-5, yb - 1, -32, 5, fl, -19, Draw.of(PURPUR));
        f.box(-4, yb, -32, 4, fl + 4, -19, Draw.AIR);
        for (int k = 0; k <= 12; k++) {
            int v = -31 + k, y = yb + k;
            for (int u = -4; u <= 4; u++) f.set(u, y, v, f.stair(PSTAIR, 2, false));
            if (k > 0) f.box(-4, yb - 1, v, 4, y - 1, v, Draw.of(PURPUR));
            if ((k & 3) == 1) { f.set(-5, y + 2, v, SEA); f.set(5, y + 2, v, SEA); }
        }
        for (int v = -31; v <= -20; v++) { f.set(-5, fl + 1, v, (v & 3) == 0 ? PILLAR : PANE); f.set(5, fl + 1, v, (v & 3) == 0 ? PILLAR : PANE); }
        for (int u = -5; u <= 5; u++) f.set(u, fl + 1, -32, (u & 3) == 0 ? PILLAR : PANE);
        // the corridors east and west
        for (int side = -1; side <= 1; side += 2) {
            int ua = side * 10, ub = side * 26;
            f.box(Math.min(ua, ub), yb - 1, -42, Math.max(ua, ub), yb + 4, -38, Draw.of(PURPUR));
            f.box(Math.min(ua, ub) + (side > 0 ? 0 : 1), yb, -41, Math.max(ua, ub) - (side > 0 ? 1 : 0), yb + 3, -39, Draw.AIR);
            f.box(Math.min(ua, ub) + (side > 0 ? 0 : 1), yb - 1, -41, Math.max(ua, ub) - (side > 0 ? 1 : 0), yb - 1, -39, Draw.of(END_BRICK));
            f.box(side * 22, yb - 1, -42, side * 26, yb + 4, -13, Draw.of(PURPUR));
            f.box(side * 23, yb, -41, side * 25, yb + 3, -13, Draw.AIR);
            f.box(side * 23, yb - 1, -41, side * 25, yb - 1, -13, Draw.of(END_BRICK));
            for (int v = -40; v <= -16; v += 6) f.set(side * 24, yb + 4, v, SEA);
        }
        scriptorium(f, s, yb);
        facets(f, s, p, yb);
    }

    /** The Scriptorium: shelves of her prophecies, reading tables, a witch who keeps them. */
    private void scriptorium(Draw.Frame f, Colossi.Site s, int yb) {
        int top = yb + 9;
        room(f, 13, -13, 39, 11, yb, top, Draw.of(DARK_OAK), Draw.of(PURPUR), Draw.mix(C_PURPLE, PURPUR, 0.3, s.salt + 33));
        f.box(23, yb, -13, 25, yb + 3, -13, Draw.AIR);
        // shelves along the walls and in rows, an aisle down the middle
        f.walls(14, yb, -12, 38, yb + 3, 10, Draw.of(BOOKS));
        f.box(23, yb, -12, 25, yb + 3, -12, Draw.AIR);
        for (int v = -8; v <= 6; v += 5) { f.box(17, yb, v, 30, yb + 2, v, Draw.of(BOOKS)); f.box(23, yb, v, 25, yb + 2, v, Draw.AIR); }
        f.box(15, yb - 1, -11, 37, yb - 1, 9, Draw.of(DARK_OAK));
        f.box(23, yb - 1, -11, 25, yb - 1, 9, Draw.of(b(35, 10)));
        // reading tables at the far end, lamps hanging from the ceiling
        for (int v = -8; v <= 6; v += 7) { f.box(34, yb, v, 36, yb, v + 1, Draw.of(DARK_OAK)); f.box(34, yb + 1, v, 36, yb + 1, v + 1, Draw.of(CARPET)); }
        for (int u = 17; u <= 35; u += 6) for (int v = -10; v <= 8; v += 6) f.set(u, top - 1, v, ROD_DOWN);
        f.set(25, top - 1, -1, SEA);
        f.chest(37, yb, -11, Draw.WEST, LOOT);
        f.chest(15, yb, 9, Draw.EAST, LOOT);
        f.chest(37, yb, 9, Draw.WEST, RICH);
        f.spawner(20, yb, -10, "cinder_witch");
        f.wallSign(21, yb + 2, -11, Draw.SOUTH, "THE SCRIPTORIUM", "Her visions,", "set down in", "crystal ink");
        f.wallSign(37, yb + 2, -1, Draw.WEST, "Each foretold", "a death. Each", "came to pass", "");
    }

    /** The Hall of Facets: a floor of crystal tiles over a pit of embers; only the opaque ones hold. */
    private void facets(Draw.Frame f, Colossi.Site s, Plan p, int yb) {
        int top = yb + 9;
        room(f, -39, -13, -13, 11, yb, top, Draw.of(END_BRICK), Draw.of(PURPUR), Draw.mix(C_PURPLE, SEA, 0.04, s.salt + 35));
        f.box(-25, yb, -13, -23, yb + 3, -13, Draw.AIR);
        // the pit under the tiles, its embers and the ladder back up
        f.box(-39, yb - 7, -11, -13, yb - 2, 10, Draw.of(PURPUR));
        f.box(-38, yb - 5, -10, -14, yb - 2, 9, Draw.AIR);
        f.box(-38, yb - 6, -10, -14, yb - 6, 9, Draw.of(MAGMA));
        for (int y = yb - 5; y <= yb - 1; y++) f.set(-26, y, -10, b(65, f.facing(Draw.SOUTH)));
        // the tiles, in the world's grid as the path's ordeal reads them
        int[] box = wbox(s, p.rot, -38, yb - 1, -10, -14, yb - 1, 9);
        for (int x = Math.max(box[0], f.d.x0); x <= Math.min(box[3], f.d.x1); x++)
            for (int z = Math.max(box[2], f.d.z0); z <= Math.min(box[5], f.d.z1); z++) {
                boolean safe = Ordeals.safeTile(Math.floorDiv(x - box[0], 2), Math.floorDiv(z - box[2], 2), p.pathSalt);
                f.d.set(x, yb - 1, z, safe ? PURPUR : FALSE_TILE);
            }
        f.set(-26, yb - 1, -10, b(65, f.facing(Draw.SOUTH)));
        // the far landing, its alcove and reward
        f.box(-27, yb - 1, 10, -21, yb - 1, 10, Draw.of(END_BRICK));
        f.box(-27, yb, 11, -21, yb + 4, 14, Draw.AIR);
        f.box(-28, yb - 1, 11, -20, yb - 1, 15, Draw.of(END_BRICK));
        f.box(-28, yb + 5, 11, -20, yb + 5, 15, Draw.of(PURPUR));
        f.walls(-28, yb, 11, -20, yb + 4, 15, Draw.of(PURPUR));
        f.box(-27, yb, 11, -21, yb + 4, 11, Draw.AIR);
        f.chest(-24, yb, 14, Draw.NORTH, RICH);
        f.set(-24, yb + 4, 13, SEA);
        for (int u = -36; u <= -16; u += 5) f.set(u, top - 1, -1, ROD_DOWN);
        f.wallSign(-20, yb + 2, -12, Draw.SOUTH, "THE HALL OF", "FACETS", "Clear crystal", "will not hold");
    }

    /** A room: floor, walls and roof, the inside cleared (floor at yb - 1, ceiling at yt). */
    private static void room(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int yt, Draw.Mat floor, Draw.Mat wall, Draw.Mat ceil) {
        f.box(u0, yb - 1, v0, u1, yt, v1, wall);
        f.box(u0, yt, v0, u1, yt, v1, ceil);
        f.box(u0 + 1, yb, v0 + 1, u1 - 1, yt - 1, v1 - 1, Draw.AIR);
        f.box(u0 + 1, yb - 1, v0 + 1, u1 - 1, yb - 1, v1 - 1, floor);
    }

    // ---- the Geode Chamber -----------------------------------------------------------------------------------------------------
    /** The chamber's floor (rings and spokes), the Oracle's dais in its crown of crystals, the crystals of walls and roof. */
    private void chamber(Draw.Frame f, Colossi.Site s, Plan p, int am) {
        int cy = s.y + GC, fl = s.y + FL;
        double reach = rIn(fl + 1, cy) + 0.5;
        int[] c = cols(f, -reach, -reach, reach, reach);
        if (c != null) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), rr = Math.hypot(u, v);
            if (rr > reach || (Math.abs(u) < 4.6 && v > -31.6 && v < -18.4)) continue;
            double ring = rr % 7, a = (Math.atan2(v, u) / (2 * Math.PI) * 12 + 12.5) % 1;
            boolean onRing = rr > 16 && ring < 0.9, onSpoke = rr > 15 && Math.min(a, 1 - a) * 2 * Math.PI * rr / 12 < 0.6;
            int val = onRing && onSpoke ? SEA : onRing ? C_MAGENTA : onSpoke ? END_BRICK : Draw.rnd(x, 1, z, s.salt + 37) < 0.12 ? C_PURPLE : PURPUR;
            f.d.set(x, fl, z, val);
        }
        // the dais
        f.disk(0, DAIS_V, 13.5, fl + 1, Draw.of(C_PURPLE));
        f.disk(0, DAIS_V, 10.5, fl + 2, (x, y, z) -> Draw.rnd(x, y, z, s.salt + 39) < 0.3 ? am : PURPUR);
        f.disk(0, DAIS_V, 4.5, fl + 2, Draw.of(C_MAGENTA));
        f.box(-1, fl + 2, DAIS_V - 1, 1, fl + 2, DAIS_V + 1, Draw.of(GLAZED));
        f.point(0, fl + 3, DAIS_V, "lord:" + s.kind.lord);
        for (double[] k : p.clusters) crystal(f, null, k, am);
        // standing lamps round the floor
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int u = (int) Math.round(Math.cos(a) * 30), v = (int) Math.round(Math.sin(a) * 30);
            if (Math.hypot(u, v - WELL_V) < 10 || (Math.abs(u) < 7 && v < -14)) continue;
            f.set(u, fl + 1, v, PILLAR);
            f.set(u, fl + 2, v, ROD_UP);
        }
        f.wallSign(0, fl + 3, DAIS_V + 6, Draw.NORTH, "THE ORACLE'S", "DAIS", "She sees each", "step you take");
        f.set(0, fl + 2, DAIS_V + 7, C_MAGENTA);
        f.set(0, fl + 3, DAIS_V + 7, C_MAGENTA);
    }

    /** The Spiral Galleries: a walkway winding twice round the inner wall, from the chamber floor to the roof. */
    private void spiral(Draw.Frame f, Colossi.Site s) {
        int cy = s.y + GC;
        double reach = IR + 1;
        int[] c = cols(f, -reach, -reach, reach, reach);
        if (c == null) return;
        double ri = IR + 0.35, hi = IH + 0.35;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), rr = Math.hypot(u, v);
            if (rr > reach) continue;
            double phi = Math.atan2(v, u), base = fracAt(phi);
            for (int k = 0; k < 2; k++) {
                double fr = base + k, S = walk(s.y, fr), wall = wallAt(S, cy), in = wall - WALK;
                if (rr < in - 0.01) continue;
                double s2 = Math.round(S * 2) / 2.0;
                int n = (int) Math.floor(s2);
                boolean half = s2 > n;
                double edge = rIn(n - 1, cy) + 0.5;
                if (rr > edge) continue;
                f.d.set(x, n - 1, z, ((int) (fr * 2 * Math.PI * in / 3)) % 5 == 0 ? END_BRICK : PURPUR);
                if (rr > in + 2) f.d.set(x, n - 2, z, C_PURPLE);
                if (rr > in + 4) f.d.set(x, n - 3, z, C_PURPLE);
                int floorTop = n;
                if (half) { f.d.set(x, n, z, SLAB); floorTop = n + 1; }
                for (int y = floorTop; y <= n + 3; y++) if (sq(rr / ri) + sq((y - cy) / hi) < 1) f.d.set(x, y, z, 0);
                if (rr < in + 1 && S > walk(s.y, 0) + 2.5 && fr < 1.93) {      // the parapet, a lamp every few strides
                    f.d.set(x, n, z, half ? PURPUR : PURPUR);
                    if (half) f.d.set(x, n + 1, z, SLAB);
                    double arc = fr * 2 * Math.PI * in;
                    if (arc % 11 < 1) f.d.set(x, half ? n + 2 : n + 1, z, ROD_UP);
                }
            }
        }
    }

    /** The Meditation Cells: crystal bubbles on the shell, each reached from the galleries through the wall. */
    private void pods(Draw.Frame f, Colossi.Site s) {
        int cy = s.y + GC;
        for (int i = 0; i < PODS.length; i++) {
            double fr = PODS[i], S = walk(s.y, fr), phi = PHI0 + 2 * Math.PI * fr, wall = wallAt(S, cy);
            int lvl = level(S), fy = lvl - 1;
            double rc = rOut(lvl + 2.5, cy) + 2.4, cu = Math.cos(phi) * rc, cv = Math.sin(phi) * rc;
            if (cols(f, cu - 8, cv - 8, cu + 8, cv + 8) == null && cols(f, Math.cos(phi) * wall - 3, Math.sin(phi) * wall - 3, Math.cos(phi) * wall + 3, Math.sin(phi) * wall + 3) == null) continue;
            double cx = f.xd(cu, cv), cz = f.zd(cu, cv);
            int skin = i % 3 == 0 ? G_PINK : i % 3 == 1 ? G_MAGENTA : G_PURPLE;
            f.d.ellipsoid(cx, lvl + 2.5, cz, 5.6, 5.6, 5.6, 0, Draw.mix(skin, PURPUR, 0.2, s.salt + 43 + i));
            f.d.ellipsoid(cx, lvl + 2.5, cz, 4.5, 4.5, 4.5, 0, Draw.AIR);
            f.d.ellipsoid(cx, lvl + 2.5, cz, 4.6, 4.6, 4.6, 0, (x, y, z) -> y < fy ? PURPUR : -1);
            f.d.disk(cx, cz, 4.4, fy, Draw.of(END_BRICK));
            f.d.disk(cx, cz, 1.5, lvl, Draw.of(i % 2 == 0 ? CARPET : CARPET_B));
            f.d.set((int) Math.round(cx), lvl + 6, (int) Math.round(cz), SEA);
            // the way in: a round passage through the shell
            double a0 = wall - 1.2, a1 = rc - 3.5;
            f.d.line(f.xd(Math.cos(phi) * a0, Math.sin(phi) * a0), lvl + 1, f.zd(Math.cos(phi) * a0, Math.sin(phi) * a0),
                f.xd(Math.cos(phi) * a1, Math.sin(phi) * a1), lvl + 1, f.zd(Math.cos(phi) * a1, Math.sin(phi) * a1), 1.2, Draw.AIR);
            for (double t = a0; t <= a1; t += 0.5) {
                double pu = Math.cos(phi) * t, pv = Math.sin(phi) * t;
                for (int w = -1; w <= 1; w++) f.set((int) Math.round(pu - Math.sin(phi) * w), fy, (int) Math.round(pv + Math.cos(phi) * w), END_BRICK);
            }
            // its keeping: a chest at the back, now and then a spawner
            double bu = Math.cos(phi) * (rc + 3), bv = Math.sin(phi) * (rc + 3);
            f.chest((int) Math.round(bu), lvl, (int) Math.round(bv), facingTowards(-Math.cos(phi), -Math.sin(phi)), i % 3 == 1 ? RICH : LOOT);
            if (i == 4) f.spawner((int) Math.round(Math.cos(phi + 0.18) * (rc + 1.5)), lvl, (int) Math.round(Math.sin(phi + 0.18) * (rc + 1.5)), "wight");
            if (i == 0 || i == 3) {
                int su = (int) Math.round(Math.cos(phi - 0.22) * (rc + 1.5)), sv = (int) Math.round(Math.sin(phi - 0.22) * (rc + 1.5));
                f.set(su, lvl, sv, PILLAR);
                f.set(su, lvl + 1, sv, ROD_UP);
            }
        }
    }

    /** The local facing (Draw.NORTH...) that points most nearly along (du, dv), as an array for convenience. */
    private static int facingTowards(double du, double dv) {
        if (Math.abs(du) > Math.abs(dv)) return du > 0 ? Draw.EAST : Draw.WEST;
        return dv > 0 ? Draw.SOUTH : Draw.NORTH;
    }

    // ---- the Void Well and the Deep Vault -------------------------------------------------------------------------------------
    private void well(Draw.Frame f, Colossi.Site s) {
        int fl = s.y + FL, foot = s.y + FOOT;
        if (cols(f, -12, WELL_V - 12, 12, WELL_V + 22) == null) return;
        // the drum round it all, the foot, the shaft
        f.cyl(0, WELL_V, 10.5, foot - 1, fl - 1, Draw.mix(OBSIDIAN, C_PURPLE, 0.3, s.salt + 41));
        f.cyl(0, WELL_V, 8.5, foot + 1, foot + 5, Draw.AIR);
        f.disk(0, WELL_V, 8.5, foot, (x, y, z) -> ((x + z) & 1) == 0 ? OBSIDIAN : END_BRICK);
        f.ring(0, WELL_V, 7.4, 1.0, foot + 6, fl, (x, y, z) -> (y & 3) == 0 ? G_BLACK : Draw.rnd(x, y, z, s.salt + 45) < 0.08 ? SEA : OBSIDIAN);
        f.cyl(0, WELL_V, 6.4, foot + 1, fl + 3, Draw.AIR);
        // the stair: a spiral of steps down the wall, a balustrade of bars round the void
        int[] c = cols(f, -7, WELL_V - 7, 7, WELL_V + 7);
        double psi0 = -Math.PI / 2, step = 2 * Math.PI / 14;
        if (c != null) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z) - WELL_V, rr = Math.hypot(u, v);
            if (rr < 2.4 || rr > 6.6) continue;
            double psi = Math.atan2(v, u), a = (psi - psi0) % (2 * Math.PI);
            if (a < 0) a += 2 * Math.PI;
            int j = (int) Math.floor(a / step);
            double tu = Math.sin(psi), tv = -Math.cos(psi);         // the way up
            int dir = Math.abs(tu) > Math.abs(tv) ? (tu > 0 ? 0 : 1) : (tv > 0 ? 2 : 3);
            for (int m = 0; ; m++) {
                int y = fl - j - 14 * m;
                if (y < foot) break;
                if (y == foot) continue;
                f.d.set(x, y, z, rr < 3.1 ? PURPUR : f.stair(PSTAIR, dir, false));
                if (y - 1 > foot) f.d.set(x, y - 1, z, PURPUR);
                if (rr < 3.1) f.d.set(x, y + 1, z, BARS);
            }
        }
        // the mouth: a rail of pillars and bars round it, open where the stair begins
        int[] m = cols(f, -9, WELL_V - 9, 9, WELL_V + 9);
        if (m != null) for (int x = m[0]; x <= m[2]; x++) for (int z = m[1]; z <= m[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z) - WELL_V, rr = Math.hypot(u, v);
            if (rr < 6.6 || rr > 7.8) continue;
            double a = (Math.atan2(v, u) - psi0) % (2 * Math.PI);
            if (a < 0) a += 2 * Math.PI;
            if (a > 0.03 && a < 0.43) continue;
            boolean post = ((int) Math.round(a / (Math.PI / 6))) * (Math.PI / 6) - a < 0.12 && a - ((int) Math.round(a / (Math.PI / 6))) * (Math.PI / 6) < 0.12;
            f.d.set(x, fl, z, PURPUR);
            f.d.set(x, fl + 1, z, post ? PILLAR : BARS);
            if (post) f.d.set(x, fl + 2, z, ROD_UP);
        }
        f.wallSign(-3, fl + 1, WELL_V - 8, Draw.NORTH, "THE VOID WELL", "Her hoard lies", "at its foot,", "sealed by her");
        f.set(-3, fl + 1, WELL_V - 7, PURPUR);
        // the foot's keeping
        f.chest(-6, foot + 1, WELL_V - 4, Draw.EAST, RICH);
        f.spawner(6, foot + 1, WELL_V - 3, "shade");
        f.set(0, foot + 5, WELL_V, G_BLACK);
        for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2 + Math.PI / 4; f.set((int) Math.round(Math.cos(a) * 7.5), foot + 3, WELL_V + (int) Math.round(Math.sin(a) * 7.5), SEA); }
        // the Deep Vault behind its crystal door
        int vv0 = WELL_V + 10, vv1 = WELL_V + 20;
        f.box(-7, foot - 1, vv0, 7, foot + 6, vv1 + 1, Draw.of(OBSIDIAN));
        f.box(-6, foot + 1, vv0 + 1, 6, foot + 5, vv1, Draw.AIR);
        f.box(-6, foot, vv0 + 1, 6, foot, vv1, (x, y, z) -> ((x + z) & 1) == 0 ? END_BRICK : C_MAGENTA);
        f.box(-1, foot + 1, WELL_V + 8, 1, foot + 3, vv0 - 1, Draw.AIR);
        f.box(-1, foot, WELL_V + 8, 1, foot, vv0, Draw.of(OBSIDIAN));
        f.box(-1, foot + 1, vv0, 1, foot + 3, vv0, Draw.of(SEAL));
        for (int u = -4; u <= 4; u += 4) f.chest(u, foot + 1, vv1, Draw.NORTH, VAULT);
        f.chest(-6, foot + 1, vv0 + 5, Draw.EAST, RICH);
        f.chest(6, foot + 1, vv0 + 5, Draw.WEST, RICH);
        for (int u = -4; u <= 4; u += 4) f.set(u, foot + 5, vv0 + 5, SEA);
        f.wallSign(2, foot + 2, WELL_V + 8, Draw.NORTH, "THE DEEP VAULT", "Sealed until", "the Oracle", "falls");
        f.set(2, foot + 2, WELL_V + 9, OBSIDIAN);
    }

    // ---- the Prism Observatory ------------------------------------------------------------------------------------------------
    private void observatory(Draw.Frame f, Colossi.Site s) {
        int cy = s.y + GC, ob = s.y + OBS;
        if (cols(f, -15, -15, 15, 15) == null) return;
        f.cyl(0, 0, 13.6, ob - 4, ob - 1, Draw.of(PURPUR));
        f.disk(0, 0, 13.6, ob, Draw.of(PURPUR));
        // the floor's star, the wall of pillars and glass, the dome
        int[] c = cols(f, -15, -15, 15, 15);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), rr = Math.hypot(u, v);
            if (rr > 13.9) continue;
            double a = Math.atan2(v, u), star = Math.abs(Math.sin(4 * a)) * rr;
            if (rr < 12.5) f.d.set(x, ob, z, rr % 4 < 0.9 ? END_BRICK : star < 1.2 ? C_MAGENTA : PURPUR);
            if (rr > 12.6) {
                boolean post = Math.abs(Math.sin(6 * a)) < 0.2;
                for (int y = ob + 1; y <= ob + 8; y++) f.d.set(x, y, z, post ? PILLAR : y < ob + 4 ? G_PURPLE : y < ob + 7 ? G_MAGENTA : G_PINK);
            }
            // the dome
            double q = rr / 13.6;
            if (q < 1) {
                int yo = ob + 8 + (int) Math.round(9.5 * Math.sqrt(1 - q * q));
                int yi = rr < 12.4 ? ob + 8 + (int) Math.round(8.4 * Math.sqrt(1 - sq(rr / 12.4))) : ob + 8;
                boolean rib = Math.abs(Math.sin(4 * a)) < 0.12 || rr < 1.2;
                for (int y = Math.max(ob + 9, yi + 1); y <= yo; y++) f.d.set(x, y, z, rib ? PURPUR : (y > ob + 14 ? G_WHITE : G_LBLUE));
            }
        }
        // the prism, hung from the dome's crown over the stair's head
        int pc = ob + 10;
        f.d.box(f.x(0, 0), ob + 14, f.z(0, 0), f.x(0, 0), ob + 17, f.z(0, 0), ROD_DOWN);
        int[] q = cols(f, -4, -4, 4, 4);
        if (q != null) for (int x = q[0]; x <= q[2]; x++) for (int z = q[1]; z <= q[3]; z++) for (int y = pc - 4; y <= pc + 4; y++) {
            double u = lu(f, x, z), v = lv(f, x, z), m = Math.abs(u) + Math.abs(v) + Math.abs(y - pc);
            if (m > 3.6) continue;
            f.d.set(x, y, z, m < 1.2 ? SEA : (y > pc ? G_WHITE : u * v > 0 ? G_MAGENTA : G_LBLUE));
        }
        // instruments: crystals on pedestals, lamps
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int u = (int) Math.round(Math.cos(a) * 9), v = (int) Math.round(Math.sin(a) * 9);
            f.set(u, ob + 1, v, PILLAR);
            crystal(f.d, null, f.xd(u, v), ob + 2, f.zd(u, v), f.xd(Math.cos(a), Math.sin(a)) - f.xd(0, 0), 1.6, f.zd(Math.cos(a), Math.sin(a)) - f.zd(0, 0), 4, 1.0, G_LBLUE, C_BLUE, 0);
        }
        // the stair from the galleries' top, up through the roof
        int sy = s.y + TOP;
        for (int k = 0; k <= 11; k++) {
            int v = -12 + k, y = sy + k;
            f.box(-1, y + 1, v, 1, y + 4, v, Draw.AIR);
            for (int u = -1; u <= 1; u++) f.set(u, y, v, f.stair(PSTAIR, 2, false));
            f.box(-1, y - 2, v, 1, y - 1, v, Draw.of(PURPUR));
            if (y + 1 < ob) { f.set(-2, y + 1, v, k % 4 == 0 ? PILLAR : PANE); f.set(2, y + 1, v, k % 4 == 0 ? PILLAR : PANE); }
        }
        for (int v = -6; v <= -2; v++) { f.set(-2, ob + 1, v, PANE); f.set(2, ob + 1, v, PANE); }
        f.set(-1, ob + 1, -6, PANE); f.set(0, ob + 1, -6, PANE); f.set(1, ob + 1, -6, PANE);
        f.chest(-7, ob + 1, 8, Draw.NORTH, RICH);
        f.chest(7, ob + 1, -8, Draw.SOUTH, RICH);
        f.set(-7, ob + 1, 9, PURPUR);
        f.set(7, ob + 1, -9, PURPUR);
        f.wallSign(0, ob + 2, 12, Draw.NORTH, "THE PRISM", "OBSERVATORY", "Here she read", "the burning sky");
        f.set(0, ob + 2, 13, PILLAR);
    }

    // ---- the gate, the avenue -------------------------------------------------------------------------------------------------
    /** The Crystal Gate: a pointed portal through the shell into the vestibule, its frame and its flame vents. */
    private void gate(Draw.Frame f, Colossi.Site s) {
        int y = s.y + 1;
        if (cols(f, -12, -64, 12, -44) == null) return;
        for (int k = 1; k <= 13; k++) {
            int hw = k <= 8 ? 5 : 13 - k;
            if (hw < 0) break;
            f.box(-hw - 2, y + k, -58, hw + 2, y + k, -55, Draw.of(k % 4 == 0 ? END_BRICK : PILLAR));
            f.box(-hw, y + k, -60, hw, y + k, -47, Draw.AIR);
        }
        f.box(-8, y + 14, -58, 8, y + 16, -55, Draw.of(PURPUR));
        f.set(0, y + 17, -57, GLAZED);
        f.set(0, y + 18, -57, ROD_UP);
        f.box(-5, y, -60, 5, y, -47, Draw.of(END_BRICK));
        f.box(-4, y, -55, 4, y, -53, Draw.of(MAGMA));
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 7, y, -59, side * 8, y + 18, -56, Draw.of(PILLAR));
            f.set(side * 7, y + 19, -58, ROD_UP); f.set(side * 8, y + 19, -57, ROD_UP);
            f.set(side * 6, y + 4, -59, SEA);
        }
        f.wallSign(0, y + 15, -59, Draw.NORTH, "THE AMETHYST", "SANCTUM", "Enter silent:", "she foresees you");
    }

    /** The Avenue of Prisms between crystal obelisks, the forecourt with its braziers. */
    private void avenue(Draw.Frame f, Colossi.Site s, Plan p) {
        int y = s.y + 1;
        if (cols(f, -30, -142, 30, -40) == null) return;
        f.box(-5, y - 3, -138, 5, y - 1, -56, Draw.of(T_PURPLE));
        f.box(-5, y, -138, 5, y, -56, (x, yy, z) -> {
            double u = lu(f, x, z), v = lv(f, x, z);
            return Math.abs(u) > 4.5 ? END_BRICK : Math.floorMod((int) v, 6) == 0 ? C_MAGENTA : Math.abs(u) < 1 ? END_BRICK : PURPUR;
        });
        f.box(-5, y + 1, -138, 5, y + 6, -56, Draw.AIR);
        for (int v = -132; v <= -64; v += 12) for (int side = -1; side <= 1; side += 2) {
            int u = side * 10;
            f.box(u - 1, y - 2, v - 1, u + 1, y + 1, v + 1, Draw.of(PURPUR));
            f.set(u - side, y + 2, v - 1, ROD_UP); f.set(u - side, y + 2, v + 1, ROD_UP);
            double h = 9 + Draw.rnd(v, side, 0, s.salt + 51) * 7;
            crystal(f.d, s, f.xd(u, v), y + 1, f.zd(u, v), f.xd(side * 0.12, 0) - f.xd(0, 0), 1, f.zd(side * 0.12, 0) - f.zd(0, 0), h, 1.7, v % 24 == 0 ? G_MAGENTA : G_PURPLE, C_PURPLE, 3);
        }
        f.wallSign(0, y + 2, -139, Draw.NORTH, "THE AMETHYST", "SANCTUM", "The Oracle", "awaits within");
        f.box(-1, y + 1, -138, 1, y + 2, -138, Draw.of(PURPUR));
        // the forecourt
        int[] c = cols(f, -27, -83, 27, -48);
        if (c != null) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z), v = lv(f, x, z), rr = Math.hypot(u, v + 56);
            if (rr > 26 || v > -49) continue;
            f.d.set(x, y - 1, z, T_PURPLE);
            f.d.set(x, y, z, rr % 6 < 1 ? C_MAGENTA : Math.abs(u) < 5 ? END_BRICK : PURPUR);
            for (int yy = y + 1; yy <= y + 5; yy++) f.d.set(x, yy, z, 0);
        }
        int[][] braziers = {{-13, -68}, {13, -68}, {-21, -58}, {21, -58}};
        for (int[] q : braziers) {
            f.box(q[0] - 1, y + 1, q[1] - 1, q[0] + 1, y + 1, q[1] + 1, Draw.of(PURPUR));
            f.set(q[0], y + 1, q[1], RACK);
            f.set(q[0], y + 2, q[1], FIRE);
        }
        f.chest(8, y + 1, -74, Draw.WEST, LOOT);
    }

    // ---- the Floating Isles ---------------------------------------------------------------------------------------------------
    private void isles(Draw.Frame f, Colossi.Site s, Plan p, int am) {
        if (cols(f, -110, -110, 110, 110) == null) return;
        for (int i = 0; i < 8; i++) isle(f, s, p, i, am);
        for (double[] c : p.hang) crystal(f, null, c, am);
        for (int i = 0; i < 8; i++) {
            int[] a = p.isles[i], b2 = p.isles[(i + 1) % 8];
            double L = Math.hypot(b2[0] - a[0], b2[2] - a[2]), tu = (b2[0] - a[0]) / L, tv = (b2[2] - a[2]) / L;
            span(f, a[0] + tu * (a[3] - 3), a[2] + tv * (a[3] - 3), a[1] + 1, b2[0] - tu * (b2[3] - 3), b2[2] - tv * (b2[3] - 3), b2[1] + 1);
        }
        for (int k = 0; k < 3; k++) door(f, s, p, k);
        stalk(f, s, p, am);
    }

    /** An isle: a rough cone of purple stone, end stone on top, and what stands on it. */
    private void isle(Draw.Frame f, Colossi.Site s, Plan p, int i, int am) {
        int[] is = p.isles[i];
        int iu = is[0], top = is[1], iv = is[2], R = is[3];
        int[] c = cols(f, iu - R - 3, iv - R - 3, iu + R + 3, iv + R + 3);
        if (c == null) return;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z) - iu, v = lv(f, x, z) - iv, rr = Math.hypot(u, v);
            double edge = R + 1.8 * Draw.noise(x, z, 5, s.salt + 300 + i);
            if (rr > edge) continue;
            double q = rr / edge;
            int depth = (int) Math.round(2 + R * 1.25 * Math.pow(1 - q, 0.8) + Draw.noise(x, z, 4, s.salt + 310) * 2);
            for (int y = top - Math.max(1, depth); y <= top; y++) {
                int val;
                if (y == top) { double q2 = Draw.rnd(x, 0, z, s.salt + 311); val = q2 < 0.45 ? PURPUR : q2 < 0.65 ? END_BRICK : q2 < 0.82 ? am : C_PURPLE; }
                else if (y > top - 3) val = T_PURPLE;
                else { int band = Math.floorMod(y + (int) (Draw.noise(x, z, 6, s.salt + 312) * 3), 5); val = band == 0 ? T_BLACK : band < 3 ? T_PURPLE : T_MAGENTA; }
                f.d.set(x, y, z, val);
            }
        }
        // what stands on it
        if (i == 7) {                                   // the Isle of Ascent: a ring of crystals round the stair's head
            for (int k = 0; k < 5; k++) {
                double a = k * 2 * Math.PI / 5 + 0.3;
                crystal(f.d, null, f.xd(iu + Math.cos(a) * 11, iv + Math.sin(a) * 11), top, f.zd(iu + Math.cos(a) * 11, iv + Math.sin(a) * 11),
                    f.xd(Math.cos(a) * 0.3, Math.sin(a) * 0.3) - f.xd(0, 0), 1, f.zd(Math.cos(a) * 0.3, Math.sin(a) * 0.3) - f.zd(0, 0), 6 + k % 3 * 2, 1.4, k % 2 == 0 ? G_MAGENTA : G_PURPLE, C_PURPLE, 0);
            }
            f.box(iu + 10, top + 1, iv, iu + 10, top + 2, iv, Draw.of(PILLAR));
            f.wallSign(iu + 9, top + 2, iv, Draw.WEST, "ISLE OF ASCENT", "The bridges", "lead to the", "upper galleries");
            f.chest(iu - 7, top + 1, iv + 8, Draw.NORTH, LOOT);
            return;
        }
        if (i == 1 || i == 5) {                         // a prism cannon
            f.box(iu - 2, top + 1, iv - 2, iu + 2, top + 2, iv + 2, Draw.of(PURPUR));
            crystal(f.d, null, f.xd(iu, iv), top + 2, f.zd(iu, iv), 0, 1, 0, 8, 2.4, G_MAGENTA, C_MAGENTA, 0);
            f.set(iu, top + 7, iv, SEA);
            f.chest(iu + 4, top + 1, iv, Draw.WEST, LOOT);
            return;
        }
        // a domed shrine
        f.box(iu - 4, top, iv - 4, iu + 4, top, iv + 4, Draw.of(PURPUR));
        for (int a = -3; a <= 3; a += 6) for (int b2 = -3; b2 <= 3; b2 += 6) f.box(iu + a, top + 1, iv + b2, iu + a, top + 4, iv + b2, Draw.of(PILLAR));
        int[] dc = cols(f, iu - 5, iv - 5, iu + 5, iv + 5);
        if (dc != null) for (int x = dc[0]; x <= dc[2]; x++) for (int z = dc[1]; z <= dc[3]; z++) {
            double u = lu(f, x, z) - iu, v = lv(f, x, z) - iv, rr = Math.hypot(u, v);
            if (rr > 4.7) continue;
            int yo = top + 5 + (int) Math.round(4.2 * Math.sqrt(Math.max(0, 1 - sq(rr / 4.7))));
            int yi = rr < 3.6 ? top + 5 + (int) Math.round(3.2 * Math.sqrt(1 - sq(rr / 3.6))) : top + 4;
            for (int y = Math.max(top + 5, yi + 1); y <= yo; y++) f.d.set(x, y, z, (i & 1) == 0 ? G_MAGENTA : G_PURPLE);
            if (rr < 3.6 && rr > 2.6) f.d.set(x, top + 5, z, PURPUR);
        }
        f.set(iu, top + 7, iv, ROD_DOWN);
        f.set(iu, top + 8, iv, PURPUR);
        f.set(iu, top + 1, iv, CARPET);
        f.chest(iu, top + 1, iv + 2, Draw.NORTH, i == 2 || i == 4 ? RICH : LOOT);
        if (i == 3) f.spawner(iu - 2, top + 1, iv - 2, "blaze");
        if (i == 6) f.wallSign(iu, top + 2, iv - 3, Draw.NORTH, "THE FLOATING", "ISLES", "Thin crystal", "breaks. Look!");
        if (i == 6) f.set(iu, top + 2, iv - 2, PILLAR);
    }

    /** A narrow crystal bridge between two walking levels (any direction): its deck three wide, a keel, lamps. */
    private void span(Draw.Frame f, double ua, double va, double ya, double ub, double vb, double yb) {
        double L = Math.hypot(ub - ua, vb - va), tu = (ub - ua) / L, tv = (vb - va) / L;
        int[] c = cols(f, Math.min(ua, ub) - 3, Math.min(va, vb) - 3, Math.max(ua, ub) + 3, Math.max(va, vb) + 3);
        if (c == null) return;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double pu = lu(f, x, z) - ua, pv = lv(f, x, z) - va, t = pu * tu + pv * tv, w = -pu * tv + pv * tu;
            if (t < -0.5 || t > L + 0.5 || Math.abs(w) > 1.75) continue;
            double S = ya + (yb - ya) * Math.max(0, Math.min(1, t / L)), s2 = Math.round(S * 2) / 2.0;
            int n = (int) Math.floor(s2);
            boolean half = s2 > n;
            f.d.set(x, n - 1, z, Math.abs(w) < 0.75 ? G_MAGENTA : PURPUR);
            if (Math.abs(w) < 0.75) f.d.set(x, n - 2, z, C_PURPLE);
            int ft = n;
            if (half) { f.d.set(x, n, z, SLAB); ft = n + 1; }
            for (int y = ft; y <= n + 3; y++) f.d.set(x, y, z, 0);
            int seg = (int) Math.floor(t / 9);
            if (Math.abs(w) > 1.0 && t - seg * 9 < 0.8 && (w > 0) == (seg % 2 == 0) && t > 3 && t < L - 3) f.d.set(x, ft, z, ROD_UP);
        }
    }

    /** A door from the galleries' upper turn out through the shell, its terrace, and the bridge to the isle beyond. */
    private void door(Draw.Frame f, Colossi.Site s, Plan p, int k) {
        int cy = s.y + GC, lvl = doorLevel(s.y, k);
        int[] is = p.isles[k * 2];
        double wall = wallAt(walk(s.y, DOORS[k]), cy);
        int r0 = (int) Math.floor(wall) - 1, r1 = (int) Math.ceil(rOut(lvl + 2, cy)) + 1, r2 = r1 + 5, r3 = (int) Math.round(Math.hypot(is[0], is[2])) - is[3] + 3;
        // along the axis: t outward, w across
        for (int t = r0; t <= r3; t++) for (int w = -3; w <= 3; w++) {
            int u = axisU(k, t, w), v = axisV(k, t, w);
            boolean tunnel = t <= r1, terrace = t > r1 && t <= r2, bridge = t > r2;
            if (tunnel && Math.abs(w) <= 1) {
                f.set(u, lvl - 1, v, END_BRICK);
                f.box(u, lvl, v, u, lvl + 2, v, Draw.AIR);
            } else if (tunnel && Math.abs(w) == 2 && t > r0 + 1) {
                f.box(u, lvl, v, u, lvl + 2, v, Draw.of(PILLAR));
            } else if (terrace) {
                f.set(u, lvl - 1, v, Math.abs(w) == 3 || t == r2 ? (Math.abs(w) <= 1 && t == r2 ? END_BRICK : PURPUR) : END_BRICK);
                f.set(u, lvl - 2, v, C_PURPLE);
                f.box(u, lvl, v, u, lvl + 3, v, Draw.AIR);
                if (Math.abs(w) == 3) f.set(u, lvl, v, (t & 1) == 0 ? PILLAR : PANE);
            } else if (bridge && Math.abs(w) <= 1) {
                boolean trap = k != 1 && t >= r2 + 8 && t <= r2 + 12;
                f.set(u, lvl - 1, v, trap ? TRAP : w == 0 ? G_MAGENTA : PURPUR);
                if (!trap && w == 0) f.set(u, lvl - 2, v, C_PURPLE);
                f.box(u, lvl, v, u, lvl + 3, v, Draw.AIR);
                if (Math.abs(w) == 1 && (t - r2) % 8 == 4 && !trap) f.set(u, lvl, v, ROD_UP);
            }
        }
        // the falling span's cradle: five blocks under it, a ladder back up beside the bridge
        if (k != 1) {
            int t0 = r2 + 7, t1 = r2 + 13, cyl = lvl - 6;
            for (int t = t0; t <= t1; t++) for (int w = -2; w <= 2; w++) {
                int u = axisU(k, t, w), v = axisV(k, t, w);
                f.set(u, cyl, v, PURPUR);
                if (t == t0 || t == t1 || Math.abs(w) == 2) f.set(u, cyl + 1, v, SLAB);
                if ((t == t0 || t == t1) && Math.abs(w) == 2) f.box(u, cyl + 1, v, u, lvl - 2, v, Draw.of(BARS));
            }
            int lt = r2 + 10;
            f.box(axisU(k, lt, 3), cyl + 1, axisV(k, lt, 3), axisU(k, lt, 3), lvl, axisV(k, lt, 3), Draw.of(PILLAR));
            f.set(axisU(k, lt, 3), cyl, axisV(k, lt, 3), PURPUR);
            int face = k == 0 ? Draw.NORTH : Draw.SOUTH;     // the ladder faces away from its post (w = 3)
            for (int y = cyl + 1; y <= lvl; y++) f.set(axisU(k, lt, 2), y, axisV(k, lt, 2), b(65, f.facing(face)));
            f.chest(axisU(k, t0 + 1, -1), cyl + 1, axisV(k, t0 + 1, -1), k == 0 ? Draw.SOUTH : Draw.NORTH, LOOT);
        }
        if (k != 1) {
            f.box(axisU(k, r2, 2), lvl, axisV(k, r2, 2), axisU(k, r2, 2), lvl + 1, axisV(k, r2, 2), Draw.of(PILLAR));
            f.wallSign(axisU(k, r2 - 1, 2), lvl + 1, axisV(k, r2 - 1, 2), k == 0 ? Draw.WEST : Draw.EAST, "Beware the", "pale crystal", "of the bridge.", "It is thin.");
        }
    }

    /** Local u of position t outward, w across, along the door axis k (east, south, west). */
    private static int axisU(int k, int t, int w) { return k == 0 ? t : k == 2 ? -t : -w; }
    private static int axisV(int k, int t, int w) { return k == 1 ? t : k == 0 ? w : -w; }

    /** The Isle of Ascent's stalk and the stair that winds up round it from the cavern floor. */
    private void stalk(Draw.Frame f, Colossi.Site s, Plan p, int am) {
        int[] is = p.isles[7];
        double cu = is[0], cv = is[2];
        int top = is[1];
        if (cols(f, cu - 10, cv - 10, cu + 10, cv + 10) == null) return;
        crystal(f.d, s, f.xd(cu, cv), s.y - 4, f.zd(cu, cv), 0, 1, 0, top - s.y, 3.4, G_PURPLE, am, 4);
        double S0 = s.y + 2, S1 = top + 1, per = 19, turns = (S1 - S0) / per, psi0 = Math.PI / 2;
        int[] c = cols(f, cu - 9, cv - 9, cu + 9, cv + 9);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double u = lu(f, x, z) - cu, v = lv(f, x, z) - cv, rr = Math.hypot(u, v);
            if (rr < 4.4 || rr > 8.6) continue;
            double a = (Math.atan2(v, u) - psi0) % (2 * Math.PI);
            if (a < 0) a += 2 * Math.PI;
            double base = a / (2 * Math.PI);
            for (int k = 0; k <= (int) Math.ceil(turns); k++) {
                double fr = base + k;
                if (fr > turns) break;
                double S = S0 + fr * per, s2 = Math.round(S * 2) / 2.0;
                int n = (int) Math.floor(s2);
                boolean half = s2 > n;
                f.d.set(x, n - 1, z, rr > 7.6 ? PURPUR : END_BRICK);
                if (rr < 6.5 || fr < 0.3) for (int y = n - 2; y >= Math.max(s.y - 2, n - (fr < 0.3 ? 12 : 2)); y--) f.d.set(x, y, z, PURPUR);
                int ft = n;
                if (half) { f.d.set(x, n, z, SLAB); ft = n + 1; }
                for (int y = ft; y <= n + 3; y++) f.d.set(x, y, z, 0);
                if (rr > 7.8) f.d.set(x, ft, z, BARS);
            }
        }
        f.box((int) cu + 9, s.y - 2, (int) cv, (int) cu + 9, s.y + 3, (int) cv, Draw.of(PURPUR));
        f.wallSign((int) cu + 10, s.y + 3, (int) cv, Draw.EAST, "ISLE OF ASCENT", "Climb, and", "cross to the", "geode's doors");
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, yb = y0 + 2, fl = y0 + FL, foot = y0 + FOOT, cy = y0 + GC;
        // the gate's flame vents, the vestibule's falling shards
        out.add(Ordeals.flames(fr.box(-4, yb, -55, 4, yb + 2, -53), 60));
        out.add(Ordeals.rubble(fr.box(-9, yb, -46, 9, yb + 8, -33), G_MAGENTA));
        // the Hall of Facets
        out.add(Ordeals.path(fr.box(-38, yb - 1, -10, -14, yb - 1, 9), FALSE_TILE, p.pathSalt));
        // arrow slits along the galleries
        for (double q : new double[]{0.45, 0.95, 1.4, 1.85}) {
            double S = walk(y0, q), phi = PHI0 + 2 * Math.PI * q, rr = wallAt(S, cy) - 3;
            int u = (int) Math.round(Math.cos(phi) * rr), v = (int) Math.round(Math.sin(phi) * rr), n = level(S);
            out.add(Ordeals.arrows(fr.box(u - 4, n, v - 4, u + 4, n + 2, v + 4), fr.box(u - 6, n + 1, v - 6, u + 6, n + 2, v + 6)));
        }
        // incense in two of the cells
        for (int i : new int[]{1, 4}) {
            double q = PODS[i], S = walk(y0, q), phi = PHI0 + 2 * Math.PI * q, rc = rOut(level(S) + 2.5, cy) + 2.4;
            int u = (int) Math.round(Math.cos(phi) * rc), v = (int) Math.round(Math.sin(phi) * rc), n = level(S);
            out.add(Ordeals.gas(fr.box(u - 3, n, v - 3, u + 3, n + 3, v + 3)));
        }
        // the Void Well: a vent half way down its stair
        out.add(Ordeals.flames(fr.box(-6, fl - 11, WELL_V - 6, 6, fl - 9, WELL_V + 6), 70));
        // the falling spans of the east and west bridges
        for (int k = 0; k <= 2; k += 2) {
            int[] is = p.isles[k * 2];
            int lvl = doorLevel(y0, k), r1 = (int) Math.ceil(rOut(lvl + 2, cy)) + 1, r2 = r1 + 5;
            int ua = axisU(k, r2 + 8, -1), va = axisV(k, r2 + 8, -1), ub = axisU(k, r2 + 12, 1), vb = axisV(k, r2 + 12, 1);
            out.add(Ordeals.collapse(fr.box(ua, lvl - 1, va, ub, lvl - 1, vb), TRAP));
        }
        // the prism cannons
        for (int i : new int[]{1, 5}) {
            int[] is = p.isles[i];
            out.add(Ordeals.cannon(fr.box(is[0] - 32, is[1] - 16, is[2] - 32, is[0] + 32, is[1] + 12, is[2] + 32), fr.at(is[0], is[1] + 11, is[2])));
        }
        // the Deep Vault's door, which the Oracle's fall opens
        out.add(Ordeals.bossSeal(s.kind.id + "_vault", fr.box(-1, foot + 1, WELL_V + 10, 1, foot + 3, WELL_V + 10), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -------------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double f = s.f(x, z), q = Draw.rnd(x, 0, z, s.salt + 61);
        if (f > 0.88) return q < 0.5 ? b(87) : T_BLACK;
        double n = Draw.fbm(x, z, 22, s.salt + 62);
        if (n > 0.25) return q < 0.7 ? T_MAGENTA : T_PURPLE;
        return q < 0.55 ? T_PURPLE : q < 0.75 ? T_BLACK : q < 0.9 ? b(87) : OBSIDIAN;
    }

    @Override Draw.Mat under() { return Draw.mix(b(87), T_PURPLE, 0.35, 5527); }

    @Override boolean dry(Colossi.Site s, int x, int z) {
        if (s.dist(x, z) < 134) return true;
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(null, s.x, s.z, p.rot);
        return Math.abs(lu(f, x, z)) < 14 && lv(f, x, z) < 0;
    }

    @Override double lakeLevel() { return 0.42; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.0025);
        double q = Draw.rnd(x, 7, z, s.salt + 53);
        if (q < 0.004 && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {          // crystal icicles from the roof
            int len = 2 + (int) (Draw.rnd(x, 8, z, s.salt + 53) * 6);
            for (int k = 0; k < len && ceil - k > floor + 6; k++) d.set(x, ceil - k, z, k == len - 1 ? G_PINK : (k & 1) == 0 ? G_PURPLE : G_MAGENTA);
        }
        if (lake || s.dist(x, z) < 60) return;
        double r = Draw.rnd(x, 9, z, s.salt + 54);
        if (r < 0.004) d.set(x, floor + 1, z, ROD_UP);
        else if (r < 0.009) { d.set(x, floor + 1, z, G_PURPLE); if (r < 0.006) d.set(x, floor + 2, z, G_MAGENTA); }
    }


}
