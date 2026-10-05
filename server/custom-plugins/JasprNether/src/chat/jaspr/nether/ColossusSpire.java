package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Ashen Spire (owner, 2026-10-04: "I want them to be huge structures"): a colossal tower of ash and basalt that rises
 * from the middle of its cavern nearly to the dome. Six black blades coil about it against the grain of a walled ramp
 * that winds three times round it to the summit, where the blades' tips stand up as the Archon's crown.
 * <ul>
 *   <li>the outer works: the broken Ash Wall round the plain with its five gates, the Pyre Fields and the ash dunes,
 *       the two Ash Colossi at the great gate, and four watchtowers whose sky bridges leap to the ramp (their
 *       fire-throwers watch it);</li>
 *   <li>seven floors inside, joined by a winding stair round a column of bone and by doors from the ramp: the Hall of
 *       Ashes, the Ash Library, the Forge of Cinders, the Pyre Garden, the Bell Chamber, the Observatory and, sealed in
 *       the stair's walls just under the summit, the Reliquary (the vault, opened by the Archon's fall);</li>
 *   <li>the summit: the Archon's Crown, an open ring 29 across inside the circlet of blades, with low walls to shelter
 *       behind, where the Spire Archon hurls fire and calls down burning rain.</li>
 * </ul>
 * Traps line the ramp (flame vents, arrow slits, falling ash) and the stair; the watchtowers throw fire at the ramp.
 */
final class ColossusSpire extends ColossusDesign {
    // ---- the frame: u, v round the spire's axis (u east, v south when rot is 0), h = y - the floor (s.y) --------------
    static final int TOP = 73, LV = 11, NECK = 66;                    // the crown's floor (h), a floor every LV, the narrowest point
    static final double R0 = 24, R_NECK = 12.7, R_TOP = 14.5;         // the core's radius at the foot, the neck and the crown
    static final double RISE = (TOP - 1) / 3.0;                       // the ramp climbs to the crown in three turns
    static final int RW = 6;                                          // the ramp's width
    static final double FIN = 9, FIN_TW = -3.84 / 70;                 // the blades: how far they stand out, how they twist
    static final double ST_RI = 1.4, ST_RO = 4.5, ST_A0 = -Math.PI / 2 + 2 * Math.PI / 11;   // the inner stair (arrives north on every floor)
    static final int SAT_D = 76, WALL_R = 104;                        // the watchtowers' distance, the Ash Wall's radius
    static final double SAT_R = 7.5;
    static final String LOOT = "jaspr:colossus/spire", RICH = LOOT + "_rich", VAULT = LOOT + "_vault";
    static final String[][] NAMES = {{"THE HALL", "OF ASHES"}, {"THE ASH", "LIBRARY"}, {"THE FORGE", "OF CINDERS"}, {"THE PYRE", "GARDEN"},
        {"THE BELL", "CHAMBER"}, {"THE", "OBSERVATORY"}};
    static final String[] WATCH = {"the Ember Watch", "the Cinder Watch", "the Soot Watch", "the Ash Watch"};

    static final class Plan {
        int rot, spikeTop;
        double ramp0, rib0, wall0;
        final double[] satA = new double[4], satRise = new double[4];
        final int[] satB = new int[4], satT = new int[4], satY = new int[4];
        final List<double[]> vents = new ArrayList<>();      // flame vents on the ramp: angle, turn
        final List<int[]> pyres = new ArrayList<>();         // u, v, base y
        final List<int[]> dunes = new ArrayList<>();         // u, v, radius, height, base y
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette ------------------------------------------------------------------------------------------------------
    private static final int GREY_T = b(159, 7), LGREY_T = b(159, 8), BLACK_T = b(159, 15), WHITE_T = b(159, 0), YELLOW_T = b(159, 4), ORANGE_T = b(159, 1);
    private static final int COAL = b(173), CRACKED = b(98, 2), STONE_BRICK = b(98, 0), CHISELED = b(98, 3), BRICK = b(112), RED_BRICK = b(215);
    private static final int BONE = b(216), OBSIDIAN = b(49), MAGMA = b(213), GLOW = b(89), RACK = b(87), FIRE = b(51), SOUL = b(88), LAVA = b(11);
    private static final int FENCE = b(113), IRON = b(101), END_ROD = b(198, 1), SHELF = b(47), WART = b(115, 3), CAP = b(100, 14), WEB = b(30);
    private static final int CAULDRON = b(118), SLAB_TOP = b(44, 13), BLACK_GLASS = b(95, 15), STAIR = 114, SEAL = CHISELED;

    /** The tower's stuff: grey and black ash, cracked stone, coal-dark seams, courses of nether brick. */
    private static int ash(int x, int y, int z, int salt) {
        double q = Draw.rnd(x, y, z, salt + 3);
        if (Math.floorMod(y, 9) == 0) return q < 0.65 ? BRICK : GREY_T;
        return q < 0.3 ? LGREY_T : q < 0.5 ? CRACKED : q < 0.63 ? STONE_BRICK : q < 0.75 ? GREY_T : q < 0.84 ? WHITE_T : q < 0.93 ? BLACK_T : COAL;
    }

    private static int ribStone(int x, int y, int z, int salt) {
        double q = Draw.rnd(x, y, z, salt + 5);
        return q < 0.45 ? COAL : q < 0.8 ? BLACK_T : OBSIDIAN;
    }

    private static Draw.Mat ashMat(int salt) { return (x, y, z) -> ash(x, y, z, salt); }

    // ---- geometry -------------------------------------------------------------------------------------------------------
    /** The core's radius at height h: tapering to the neck, then flaring a little into the crown. */
    static double rc(double h) {
        if (h <= NECK) return R0 - (R0 - R_NECK) * Math.max(0, h) / NECK;
        return R_NECK + (R_TOP - R_NECK) * Math.min(1, (h - NECK) / (double) (TOP - NECK));
    }

    static double frac(double a) { double t = a % 1; return t < 0 ? t + 1 : t; }

    static double wrap(double a) { a = a % (2 * Math.PI); if (a > Math.PI) a -= 2 * Math.PI; if (a < -Math.PI) a += 2 * Math.PI; return a; }

    /** The ramp's floor height (fractional h) at angle a on turn t. */
    static double rampH(Plan p, double a, int t) { return 1 + (frac((a - p.ramp0) / (2 * Math.PI)) + t) * RISE; }

    /** The angle at which the ramp's floor reaches height h. */
    static double rampA(Plan p, double h) { return p.ramp0 + frac((h - 1) / RISE) * 2 * Math.PI; }

    /** Whether any turn of the ramp passes angle a within tol of height h. */
    static boolean rampNear(Plan p, double a, double h, double tol) {
        for (int t = 0; t < 3; t++) if (Math.abs(rampH(p, a, t) - h) < tol) return true;
        return false;
    }

    /** Whether a blade fills (rho, ang) at height h. */
    static boolean blade(Plan p, double rho, double ang, int h) {
        double out = rho - rc(Math.min(h, TOP));
        double reach = h <= NECK ? FIN : FIN * Math.max(0, (p.spikeTop - h) / (double) (p.spikeTop - NECK));
        if (out < -1 || out > reach) return false;
        if (h >= TOP && rho < R_TOP - 0.6) return false;
        double hw = (1.7 - Math.max(0, out - 1.5) * 0.12) * (h > NECK ? Math.max(0.45, (p.spikeTop - h) / (double) (p.spikeTop - NECK)) : 1);
        double kk = (ang - p.rib0 - FIN_TW * h) / (Math.PI / 3), off = Math.abs(kk - Math.rint(kk)) * (Math.PI / 3) * Math.max(rho, 1);
        return off <= hw;
    }

    static int pu(double rho, double a) { return (int) Math.round(rho * Math.cos(a)); }

    static int pv(double rho, double a) { return (int) Math.round(rho * Math.sin(a)); }

    /** The stair direction along a circle's tangent at angle a (towards increasing angle). */
    static int tangent(double a) {
        double tu = -Math.sin(a), tv = Math.cos(a);
        return Math.abs(tu) > Math.abs(tv) ? (tu > 0 ? 0 : 1) : (tv > 0 ? 2 : 3);
    }

    /** The chest/sign facing that looks from (u, v) towards the axis. */
    static int inward(double u, double v) {
        return Math.abs(u) >= Math.abs(v) ? (u > 0 ? Draw.WEST : Draw.EAST) : (v > 0 ? Draw.NORTH : Draw.SOUTH);
    }

    // ---- plan -----------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        p.ramp0 = r.nextDouble() * 2 * Math.PI;
        p.rib0 = r.nextDouble() * 2 * Math.PI;
        p.wall0 = r.nextDouble() * 500;
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        // the blades' tips keep under the dome
        int low = 122;
        for (int k = 0; k < 24; k++) for (double d = 13; d <= 25; d += 4) {
            double a = k * Math.PI / 12;
            low = Math.min(low, s.ceilAt(fr.x(pu(d, a), pv(d, a)), fr.z(pu(d, a), pv(d, a))));
        }
        p.spikeTop = Math.max(TOP + 4, Math.min(TOP + 12, low - s.y - 3));
        // the watchtowers stand on the diagonals of the ramp's start; their bridges meet its second turn
        for (int i = 0; i < 4; i++) {
            double a = p.ramp0 + Math.PI / 4 + i * Math.PI / 2;
            p.satA[i] = a;
            p.satB[i] = (int) Math.floor(rampH(p, a, 1));
            int lowT = 122;
            for (int k = 0; k < 8; k++) {
                double ka = k * Math.PI / 4;
                int u = pu(SAT_D, a) + pu(9, ka), v = pv(SAT_D, a) + pv(9, ka);
                lowT = Math.min(lowT, s.ceilAt(fr.x(u, v), fr.z(u, v)));
            }
            p.satT[i] = Math.min(p.satB[i] + 9, lowT - s.y - 11);
            int k = (int) Math.max(1, Math.round((p.satB[i] - 1) / 7.5 - 0.4443));
            p.satRise[i] = (p.satB[i] - 1) / (k + 0.4443);
            p.satY[i] = s.floorAt(fr.x(pu(SAT_D + 12, a), pv(SAT_D + 12, a)), fr.z(pu(SAT_D + 12, a), pv(SAT_D + 12, a)));
        }
        // flame vents on the ramp's three turns
        p.vents.add(new double[]{p.ramp0 + 2.3, 0});
        p.vents.add(new double[]{p.ramp0 + 4.4, 1});
        p.vents.add(new double[]{p.ramp0 + 1.6, 2});
        // the Pyre Fields and the ash dunes, clear of the towers, the bridges and the gates' roads
        for (int t = 0; t < 300 && p.pyres.size() < 10; t++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 46 + r.nextDouble() * 48;
            if (!clear(p, a, d, 9)) continue;
            int u = pu(d, a), v = pv(d, a);
            p.pyres.add(new int[]{u, v, s.floorAt(fr.x(u, v), fr.z(u, v))});
        }
        for (int t = 0; t < 300 && p.dunes.size() < 16; t++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 44 + r.nextDouble() * 54;
            int rad = 4 + r.nextInt(6);
            if (!clear(p, a, d, rad + 3)) continue;
            int u = pu(d, a), v = pv(d, a);
            p.dunes.add(new int[]{u, v, rad, 1 + r.nextInt(3), s.floorAt(fr.x(u, v), fr.z(u, v))});
        }
        p.garrisons = complete(garrisonsOf(p, s, fr));
        return p;
    }

    /** Whether a spot on the plain (angle, distance) keeps clear of the watchtowers, the bridges, the roads and the pyres. */
    private static boolean clear(Plan p, double a, double d, double room) {
        for (int i = 0; i < 4; i++) if (Math.abs(wrap(a - p.satA[i])) * d < room + 4) return false;
        if (Math.abs(wrap(a - p.ramp0 - Math.PI)) * d < room + 14) return false;       // the great gate's road and its colossi
        int u = pu(d, a), v = pv(d, a);
        for (int[] q : p.pyres) if (Math.hypot(q[0] - u, q[1] - v) < room + 6) return false;
        for (int[] q : p.dunes) if (Math.hypot(q[0] - u, q[1] - v) < room + q[2]) return false;
        return true;
    }

    /** A garrison standing on the cavern floor at (u, v). */
    private static Garrison ground(Colossi.Site s, Ordeals.Frame fr, int u, int v, String pack) { return g(u, s.floorAt(fr.x(u, v), fr.z(u, v)) + 1, v, pack); }

    private static List<Garrison> garrisonsOf(Plan p, Colossi.Site s, Ordeals.Frame fr) {
        int y0 = s.y;
        double ga = p.ramp0 + Math.PI;
        List<Garrison> gs = new ArrayList<>();
        gs.add(ground(s, fr, pu(46, ga), pv(46, ga), "!infernal_knight+wither_skeleton"));           // before the great gate
        gs.add(g(pu(10, ga + 0.6), y0 + 1, pv(10, ga + 0.6), "ember_legionnaire+flame_adept"));      // the Hall of Ashes
        gs.add(g(pu(16.5, ga + Math.PI), y0 + 1, pv(16.5, ga + Math.PI), "charred_ghoul+zombie_pigman"));
        String[] rooms = {"cinder_witch+skeleton+wight", "magma_hulk+blaze+salamander", "ember+magma_cube+spore", "soul_wraith+lost_soul+shade",
            "pyre_warden+royal_guard"};
        for (int k = 1; k <= 5; k++) { double a = p.ramp0 + k * 1.3; gs.add(g(pu(6.6, a), y0 + k * LV + 1, pv(6.6, a), rooms[k - 1])); }
        String[] ramp = {"hellhound+hellhound+dread_rider", "ashbone_archer+pigman_berserker", "tomb_guardian+crypt_guard"};
        for (int t = 0; t < 3; t++) {                                                                  // on the ramp
            double a = p.ramp0 + 3.4 + t * 0.7, h = Math.floor(rampH(p, a, t));
            gs.add(g(pu(rc(h) + 3, a), y0 + (int) h + 1, pv(rc(h) + 3, a), ramp[t]));
        }
        String[] base = {"brute+nethermite", "spinout+coolmar_spider", "mummy+asp+scarab", "deep_crawler+brimstone_spider"};
        String[] tops = {"cinder_imp+ashbone_archer", "infernal_knight+pigman_berserker", "dread_rider+wither_skeleton", "flame_adept+cinder_witch"};
        for (int i = 0; i < 4; i++) {                                                                  // the watchtowers, at the foot and on top
            double a = p.satA[i];
            gs.add(g(pu(SAT_D + 12, a), p.satY[i] + 1, pv(SAT_D + 12, a), base[i]));
            int cu = pu(SAT_D, a), cv = pv(SAT_D, a);
            gs.add(g(cu + pu(5.5, a + Math.PI / 2), y0 + p.satT[i] + 1, cv + pv(5.5, a + Math.PI / 2), tops[i]));
        }
        gs.add(ground(s, fr, pu(WALL_R - 8, ga), pv(WALL_R - 8, ga), "cinder_imp+mogus"));            // inside the Ash Wall's gates
        gs.add(ground(s, fr, pu(WALL_R - 8, p.satA[2]), pv(WALL_R - 8, p.satA[2]), "frost+spore_creeper"));
        // the great fliers wheel round the spire's flanks in the open air
        String[] fliers = {"ghast+ghastling", "ghastling+ghastling", "ghast", "ghastling+ghast"};
        int[] fh = {50, 56, 40, 46};
        for (int j = 0; j < 4; j++) { double a = p.ramp0 + j * Math.PI / 2; gs.add(g(pu(40, a), y0 + fh[j], pv(40, a), fliers[j])); }
        return gs;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        plain(f, s, p);
        tower(f, s, p);
        rooms(f, s);
        stairs(f, s);
        doors(f, s, p);
        hall(f, s, p);
        library(f, s, p);
        forge(f, s, p);
        garden(f, s, p);
        bells(f, s, p);
        observatory(f, s, p);
        reliquary(f, s);
        crown(f, s, p);
        for (int i = 0; i < 4; i++) watchtower(f, s, p, i);
        garrisons(f, p.garrisons);
    }

    /** The local (u, v) box over the draw's clip: u0, v0, u1, v1. */
    private static int[] clip(Draw.Frame f) {
        Draw d = f.d;
        int[][] corners = {{d.x0, d.z0}, {d.x1, d.z0}, {d.x0, d.z1}, {d.x1, d.z1}};
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v1 = Integer.MIN_VALUE;
        for (int[] c : corners) {
            int u, v, x = c[0], z = c[1];
            switch (f.rot) {
                case 1: u = z - f.oz; v = f.ox - x; break;
                case 2: u = f.ox - x; v = f.oz - z; break;
                case 3: u = f.oz - z; v = x - f.ox; break;
                default: u = x - f.ox; v = z - f.oz;
            }
            u0 = Math.min(u0, u); u1 = Math.max(u1, u); v0 = Math.min(v0, v); v1 = Math.max(v1, v);
        }
        return new int[]{u0, v0, u1, v1};
    }

    private static boolean touches(Draw.Frame f, int u0, int v0, int u1, int v1) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        return f.d.touches(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    private static void brazier(Draw.Frame f, int u, int y, int v) { f.set(u, y, v, RACK); f.set(u, y + 1, v, FIRE); }

    /** A radial box out from (cu, cv) along angle a: columns between radii r0 and r1, within hw of the line, from ya to yb. */
    private static void radialAt(Draw.Frame f, double cu, double cv, double a, double r0, double r1, double hw, int ya, int yb, Draw.Mat m) {
        double ca = Math.cos(a), sa = Math.sin(a);
        int u0 = (int) Math.floor(cu + Math.min(r0 * ca, r1 * ca) - hw - 1), u1 = (int) Math.ceil(cu + Math.max(r0 * ca, r1 * ca) + hw + 1);
        int v0 = (int) Math.floor(cv + Math.min(r0 * sa, r1 * sa) - hw - 1), v1 = (int) Math.ceil(cv + Math.max(r0 * sa, r1 * sa) + hw + 1);
        if (!touches(f, u0, v0, u1, v1)) return;
        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
            double du = u - cu, dv = v - cv, along = du * ca + dv * sa, perp = -du * sa + dv * ca;
            if (along < r0 || along > r1 || Math.abs(perp) > hw) continue;
            f.box(u, ya, v, u, yb, v, m);
        }
    }

    private static void radial(Draw.Frame f, double a, double r0, double r1, double hw, int ya, int yb, Draw.Mat m) { radialAt(f, 0, 0, a, r0, r1, hw, ya, yb, m); }

    /** A wall sign at (u, y, v) facing the given angle (rounded to a side), its stone a pillar from {@code base} up to it. */
    private static void plate(Draw.Frame f, int u, int y, int v, double a, int base, String... lines) {
        double cu = Math.cos(a), cv = Math.sin(a);
        int fc, bu = 0, bv = 0;
        if (Math.abs(cu) >= Math.abs(cv)) { fc = cu > 0 ? Draw.EAST : Draw.WEST; bu = cu > 0 ? -1 : 1; }
        else { fc = cv > 0 ? Draw.SOUTH : Draw.NORTH; bv = cv > 0 ? -1 : 1; }
        f.box(u + bu, Math.min(base, y), v + bv, u + bu, y + 1, v + bv, Draw.of(CHISELED));
        f.wallSign(u, y, v, fc, lines);
    }

    /** A winding stair of stairs-blocks around (cu, cv) between radii ri and ro, from yA, rising `rise` a turn up to yB. */
    private static void helix(Draw.Frame f, double cu, double cv, double ri, double ro, int yA, int yB, double rise, double a0, Draw.Mat under) {
        int u0 = (int) Math.floor(cu - ro), u1 = (int) Math.ceil(cu + ro), v0 = (int) Math.floor(cv - ro), v1 = (int) Math.ceil(cv + ro);
        if (!touches(f, u0, v0, u1, v1)) return;
        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
            double du = u - cu, dv = v - cv, r = Math.sqrt(du * du + dv * dv);
            if (r < ri || r > ro) continue;
            double ang = Math.atan2(dv, du), a = ((ang - a0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            int dir = tangent(ang);
            for (double y = yA + a / (2 * Math.PI) * rise; y <= yB + 0.001; y += rise) {
                int yi = (int) Math.floor(y);
                f.set(u, yi, v, f.stair(STAIR, dir, false));
                if (yi - 1 >= yA - 1) f.set(u, yi - 1, v, under);
            }
        }
    }

    /** Fences round a stair's well (radius ro) at y, open over an arc round angle `gap`. */
    private static void railing(Draw.Frame f, double cu, double cv, double ro, int y, double gap) {
        for (int k = 0; k < 48; k++) {
            double a = k * Math.PI / 24;
            if (Math.abs(wrap(a - gap)) < 0.8) continue;
            f.set((int) Math.round(cu + Math.cos(a) * (ro + 0.9)), y, (int) Math.round(cv + Math.sin(a) * (ro + 0.9)), FENCE);
        }
    }

    // ---- the spire ------------------------------------------------------------------------------------------------------
    /** The core, the coiling blades and the ramp, column by column. */
    private void tower(Draw.Frame f, Colossi.Site s, Plan p) {
        int[] c = clip(f);
        int lim = (int) Math.ceil(R0 + FIN + 1);
        int ua = Math.max(c[0], -lim), ub = Math.min(c[2], lim), va = Math.max(c[1], -lim), vb = Math.min(c[3], lim);
        if (ua > ub || va > vb) return;
        int y0 = s.y, salt = s.salt;
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) {
            double rho = Math.sqrt((double) u * u + (double) v * v);
            if (rho > R0 + FIN + 0.5) continue;
            int x = f.x(u, v), z = f.z(u, v);
            double ang = Math.atan2(v, u);
            if (rho <= R0) for (int h = -3; h <= TOP; h++) {
                double rr = rc(h);
                if (rho > rr) continue;
                boolean band = h > 0 && h % LV == 0 && rho > rr - 1.5;
                f.d.set(x, y0 + h, z, band ? (Draw.rnd(x, h, z, salt + 7) < 0.55 ? MAGMA : BRICK) : ash(x, y0 + h, z, salt));
            }
            // the blades coil up the tower against the ramp's turn and stand up round the crown
            for (int h = 0; h <= p.spikeTop; h++) {
                if (!blade(p, rho, ang, h)) continue;
                double out = rho - rc(Math.min(h, TOP));
                f.d.set(x, y0 + h, z, Math.floorMod(h, 6) == 0 && out > 1 && h < TOP ? BONE : ribStone(x, y0 + h, z, salt));
            }
            // the ramp: three turns, walled, from the foot to the crown
            for (int t = 0; t < 3; t++) {
                double hr = rampH(p, ang, t);
                if (hr > TOP + 0.01) continue;
                int fh = (int) Math.floor(hr);
                double rr = rc(fh);
                if (rho < rr - 0.5 || rho > rr + RW) continue;
                boolean edge = rho > rr + RW - 1;
                boolean step = !edge && (int) Math.floor(hr - RISE / (2 * Math.PI * Math.max(rho, 2))) < fh;
                boolean vent = false;
                for (double[] q : p.vents) if ((int) q[1] == t && Math.abs(wrap(ang - q[0])) * rho < 2.2 && !edge) vent = true;
                f.d.set(x, y0 + fh - 1, z, BLACK_T);
                f.d.set(x, y0 + fh, z, step ? f.stair(STAIR, tangent(ang), false) : vent ? MAGMA : Math.abs(rho - rr - RW / 2.0) < 0.6 ? RED_BRICK : BRICK);
                if (rho >= rr) for (int h = fh + 1; h <= fh + 4; h++) f.d.set(x, y0 + h, z, 0);
                if (edge) {
                    f.d.set(x, y0 + fh + 1, z, CRACKED);
                    int arc = (int) Math.floor((ang + Math.PI) * rho);
                    if (Math.floorMod(arc, 14) == 0) { f.d.set(x, y0 + fh + 2, z, RACK); f.d.set(x, y0 + fh + 3, z, FIRE); }
                    else if (Math.floorMod((int) Math.floor(ang * rho / 2), 2) == 0) f.d.set(x, y0 + fh + 2, z, BONE);
                    if (Math.floorMod((int) Math.floor(ang * rho), 9) == 0) { f.d.set(x, y0 + fh - 2, z, BLACK_T); f.d.set(x, y0 + fh - 3, z, BLACK_T); }
                }
            }
        }
    }

    /** The seven floors, hollowed out of the core (each a little narrower than the one below). */
    private void rooms(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -22, -22, 22, 22)) return;
        int y0 = s.y;
        double cx = f.xd(0, 0), cz = f.zd(0, 0);
        for (int k = 0; k <= 6; k++) {
            int fl = k * LV, top = k == 6 ? TOP - 1 : fl + LV - 1;
            f.d.tube(cx, cz, rc(fl + 1) - 3.4, rc(top) - 3.4, 0, y0 + fl + 1, y0 + top, Draw.AIR);
        }
    }

    /** The winding stair round its column of bone, the railings, the Reliquary's walled well and its seal. */
    private void stairs(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -7, -7, 7, 7)) return;
        int y0 = s.y;
        f.cyl(0, 0, ST_RO, y0 + 1, y0 + TOP, Draw.AIR);
        f.ring(0, 0, ST_RO + 1, 1, y0 + 6 * LV + 1, y0 + TOP - 1, Draw.of(STONE_BRICK));
        f.cyl(0, 0, 0.9, y0 + 1, y0 + TOP, Draw.of(BONE));
        f.set(0, y0 + TOP, 0, GLOW);
        helix(f, 0, 0, ST_RI, ST_RO, y0 + 1, y0 + TOP, LV, ST_A0, Draw.of(BLACK_T));
        for (int k = 1; k <= 5; k++) railing(f, 0, 0, ST_RO, y0 + k * LV + 1, -Math.PI / 2);
        railing(f, 0, 0, ST_RO, y0 + TOP + 1, ST_A0 + frac((TOP - 1) / (double) LV) * 2 * Math.PI);
        f.box(-1, y0 + 6 * LV + 1, -5, 1, y0 + 6 * LV + 3, -5, Draw.of(SEAL));
        for (int y = y0 + 6; y < y0 + TOP - 6; y += LV) f.set(0, y, 0, GLOW);
    }

    /** The great gate of the Hall of Ashes, the doors from the ramp into the floors, the slits in the walls. */
    private void doors(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        double ga = p.ramp0 + Math.PI;
        radial(f, ga, rc(0) - 4.5, rc(0) + FIN + 1.5, 2.6, y0 + 1, y0 + 7, Draw.AIR);
        radial(f, ga, rc(0) - 4.5, rc(0) + FIN + 1.5, 2.6, y0, y0, Draw.of(CHISELED));
        radial(f, ga, rc(0) + 0.6, rc(0) + 1.5, 3.6, y0 + 8, y0 + 9, Draw.of(BONE));
        double ca = Math.cos(ga), sa = Math.sin(ga), al = rc(0) - 1.5;
        int su = (int) Math.round(al * ca - 2.0 * sa), sv = (int) Math.round(al * sa + 2.0 * ca);
        if (touches(f, su - 2, sv - 2, su + 2, sv + 2)) plate(f, su, y0 + 3, sv, ga - Math.PI / 2, y0 + 3, "THE ASHEN", "SPIRE", "Climb, and", "burn");
        for (int k = 1; k <= 5; k++) {
            double a = rampA(p, k * LV) + 0.04;
            int fl = y0 + k * LV;
            radial(f, a, rc(k * LV) - 4.5, rc(k * LV) + 1.0, 1.6, fl + 1, fl + 4, Draw.AIR);
        }
        // slits in every floor's wall but the Reliquary's, never onto the ramp
        for (int k = 0; k <= 5; k++) {
            int fl = y0 + k * LV;
            for (int j = 0; j < 12; j++) {
                double a = p.rib0 + j * Math.PI / 6 + k * 0.26;
                if (rampNear(p, a, k * LV + 5, 5)) continue;
                radial(f, a, rc(k * LV + 5) - 3.6, rc(k * LV + 5) + 0.6, 0.5, fl + 4, fl + 6, Draw.AIR);
            }
        }
        // the Ash Colossi: two hooded figures with braziers flanking the great gate's road
        for (int side = -1; side <= 1; side += 2) colossus(f, s, ga + side * 0.3, 40);
    }

    private void colossus(Draw.Frame f, Colossi.Site s, double a, double d) {
        int u = pu(d, a), v = pv(d, a), y0 = s.y;
        if (!touches(f, u - 6, v - 6, u + 6, v + 6)) return;
        Draw.Mat robe = ashMat(s.salt + 11);
        f.box(u - 3, y0 - 2, v - 3, u + 3, y0 + 2, v + 3, Draw.of(CHISELED));
        f.cyl(u, v, 2.4, y0 + 3, y0 + 11, robe);                                        // robes
        f.cyl(u, v, 1.6, y0 + 12, y0 + 14, robe);                                       // shoulders and chest
        f.cyl(u, v, 1.6, y0 + 15, y0 + 18, Draw.of(COAL));                              // the hood
        f.cyl(u, v, 0.9, y0 + 15, y0 + 16, Draw.of(BLACK_T));
        int fu = u - pu(1.6, a), fv = v - pv(1.6, a);                                    // a face of embers under the hood
        f.set(fu, y0 + 16, fv, MAGMA);
        int bu = u - pu(3.2, a), bv = v - pv(3.2, a);                                    // the brazier held over the road
        f.box(bu - 1, y0 + 12, bv - 1, bu + 1, y0 + 12, bv + 1, Draw.of(BRICK));
        f.set(bu, y0 + 13, bv, RACK); f.set(bu, y0 + 14, bv, FIRE);
    }

    // ---- the floors -------------------------------------------------------------------------------------------------------
    /** Whether a spot on floor k (angle, radius) is free of the stair's landing, the ramp's door and the great gate. */
    private static boolean free(Plan p, int k, double a, double rho) {
        if (rho < 7) return false;
        if (Math.abs(wrap(a + Math.PI / 2)) * rho < 4.5 && rho < 10) return false;
        double ri = rc(k * LV + 1) - 3;
        if (k >= 1 && Math.abs(wrap(a - rampA(p, k * LV))) * rho < 4.5 && rho > ri - 6) return false;
        if (k == 0 && Math.abs(wrap(a - p.ramp0 - Math.PI)) * rho < 5.5 && rho > ri - 8) return false;
        return true;
    }

    /** A chest against floor k's wall at angle a (moved along the wall until it is clear). */
    private static void wallChest(Draw.Frame f, Plan p, int k, double a, int y, String table) {
        double ri = rc(k * LV + 1) - 3.8;
        for (int t = 0; t < 16 && !free(p, k, a, ri); t++) a += 0.12;
        int u = pu(ri, a), v = pv(ri, a);
        f.chest(u, y, v, inward(u, v), table);
    }

    /** A spawner on floor k near (angle, radius), moved round until clear. */
    private static void spawnerAt(Draw.Frame f, Plan p, int k, double a, double rho, int y, String mob) {
        for (int t = 0; t < 16 && !free(p, k, a, rho); t++) a += 0.15;
        f.spawner(pu(rho, a), y, pv(rho, a), mob);
    }

    private void roomSign(Draw.Frame f, Plan p, int k, int yb) {
        double da = rampA(p, k * LV);
        double rho = rc(k * LV + 1) - 5.5;
        plate(f, pu(rho, da + 3.0 / rho), yb + 2, pv(rho, da + 3.0 / rho), da + Math.PI, yb, NAMES[k][0], NAMES[k][1], "", "");
    }

    private void hall(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -22, -22, 22, 22)) return;
        int y0 = s.y, yb = y0 + 1;
        for (int j = 0; j < 12; j++) {                                                 // the pillars under the Library
            double a = p.ramp0 + (j + 0.5) * Math.PI / 6;
            if (!free(p, 0, a, 14)) continue;
            int u = pu(14, a), v = pv(14, a);
            f.box(u - 1, yb, v - 1, u + 1, yb + 9, v + 1, Draw.of(CRACKED));
            f.box(u - 1, yb + 8, v - 1, u + 1, yb + 8, v + 1, Draw.of(BONE));
            f.set(u, yb + 4, v, MAGMA);
        }
        for (int j = 0; j < 12; j++) {                                                 // braziers at the wall, heaps of ash, urns
            double a = p.ramp0 + j * Math.PI / 6;
            if (free(p, 0, a, 19.5)) brazier(f, pu(19.5, a), yb, pv(19.5, a));
            double ha = a + 0.26;
            if (j % 2 == 0 && free(p, 0, ha, 17)) f.d.ellipsoid(f.xd(pu(17, ha), pv(17, ha)), yb, f.zd(pu(17, ha), pv(17, ha)), 2.2, 1.6, 2.2, 0, Draw.mix(LGREY_T, WHITE_T, 0.3, s.salt + 13));
            if (j % 3 == 1 && free(p, 0, a, 9.5)) f.set(pu(9.5, a), yb, pv(9.5, a), CAULDRON);
        }
        for (int j = 0; j < 8; j++) { double a = j * Math.PI / 4 + 0.2; f.set(pu(11, a), y0 + LV, pv(11, a), GLOW); }
        wallChest(f, p, 0, p.ramp0 + 0.9, yb, LOOT);
        wallChest(f, p, 0, p.ramp0 + 2.4, yb, RICH);
        plate(f, pu(8, -Math.PI / 2 + 0.95), yb + 2, pv(8, -Math.PI / 2 + 0.95), Math.PI / 2 + 0.95, yb, "THE STAIR", "climbs to the", "Crown of the", "Archon");
    }

    private void library(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, k = 1, yb = y0 + k * LV + 1;
        if (!touches(f, -21, -21, 21, 21)) return;
        Draw.Mat books = (x, y, z) -> { double q = Draw.rnd(x, y, z, s.salt + 17); return q < 0.68 ? SHELF : q < 0.84 ? COAL : BLACK_T; };
        for (int j = 0; j < 15; j++) {                                                 // shelves in radial rows, half burned
            double a = p.ramp0 + j * 2 * Math.PI / 15;
            for (double rho = 9; rho <= 16.5; rho += 0.5) {
                if (!free(p, k, a, rho)) continue;
                int u = pu(rho, a), v = pv(rho, a);
                f.box(u, yb, v, u, yb + 4, v, books);
            }
            double ma = a + Math.PI / 15;
            if (free(p, k, ma, 14)) { int u = pu(14, ma), v = pv(14, ma); f.set(u, yb, v, SLAB_TOP); f.set(u, yb + 7, v, WEB); }
        }
        for (int j = 0; j < 6; j++) { double a = j * Math.PI / 3 + 0.4; f.set(pu(12, a), y0 + 2 * LV, pv(12, a), GLOW); }
        wallChest(f, p, k, p.ramp0 + 0.2, yb, LOOT);
        wallChest(f, p, k, p.ramp0 + 3.3, yb, RICH);
        spawnerAt(f, p, k, p.ramp0 + 5.0 + Math.PI / 15, 15.5, yb, "cinder_witch");
        roomSign(f, p, k, yb);
    }

    private void forge(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, k = 2, yb = y0 + k * LV + 1;
        if (!touches(f, -19, -19, 19, 19)) return;
        double door = rampA(p, k * LV);
        // a trough of lava round the floor, crossed in five places
        for (int u = -11; u <= 11; u++) for (int v = -11; v <= 11; v++) {
            double rho = Math.hypot(u, v), a = Math.atan2(v, u);
            if (rho < 7.5 || rho > 10.5) continue;
            boolean cross = Math.abs(wrap(a + Math.PI / 2)) * rho < 2.2 || Math.abs(wrap(a - door)) * rho < 2.2 || Math.abs(wrap(a - p.ramp0 - 0.6)) * rho < 2.2
                || Math.abs(wrap(a - p.ramp0 - 2.6)) * rho < 2.2 || Math.abs(wrap(a - p.ramp0 - 4.4)) * rho < 2.2;
            f.set(u, yb, v, !cross && rho >= 8.5 && rho <= 9.5 ? LAVA : BRICK);
        }
        for (int j = 0; j < 4; j++) {                                                  // the hearths, their hoods and anvils
            double a = p.ramp0 + 1.6 + j * Math.PI / 2;
            for (int t = 0; t < 6 && !free(p, k, a, 13.5); t++) a += 0.25;
            int u = pu(13.5, a), v = pv(13.5, a);
            f.box(u - 2, yb, v - 2, u + 2, yb + 1, v + 2, Draw.of(BRICK));
            f.box(u - 1, yb + 2, v - 1, u + 1, yb + 2, v + 1, Draw.of(MAGMA));
            f.set(u, yb + 2, v, LAVA);
            f.box(u - 1, yb + 2, v - 2, u + 1, yb + 2, v - 2, Draw.of(BRICK)); f.box(u - 1, yb + 2, v + 2, u + 1, yb + 2, v + 2, Draw.of(BRICK));
            f.box(u - 2, yb + 2, v - 1, u - 2, yb + 2, v + 1, Draw.of(BRICK)); f.box(u + 2, yb + 2, v - 1, u + 2, yb + 2, v + 1, Draw.of(BRICK));
            for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) f.box(u + cu * 2, yb + 3, v + cv * 2, u + cu * 2, yb + 8, v + cv * 2, Draw.of(FENCE));
            f.box(u - 2, yb + 8, v - 2, u + 2, yb + 8, v + 2, Draw.of(BRICK));
            int au = pu(13.5, a + 3.6 / 13.5), av = pv(13.5, a + 3.6 / 13.5);
            if (free(p, k, a + 3.6 / 13.5, 13.5)) { f.set(au, yb, av, f.stair(STAIR, 0, false)); f.set(au, yb + 1, av, IRON); }
        }
        for (int j = 0; j < 10; j++) {                                                 // chains from the vault
            double a = p.ramp0 + j * Math.PI / 5 + 0.3;
            if (!free(p, k, a, 11.5)) continue;
            int u = pu(11.5, a), v = pv(11.5, a);
            f.box(u, yb + 5, v, u, yb + 9, v, Draw.of(FENCE));
        }
        for (int j = 0; j < 6; j++) { double a = j * Math.PI / 3 + 0.9; f.set(pu(11, a), y0 + 3 * LV, pv(11, a), GLOW); }
        wallChest(f, p, k, p.ramp0 + 0.9, yb, LOOT);
        wallChest(f, p, k, p.ramp0 + 3.9, yb, RICH);
        spawnerAt(f, p, k, p.ramp0 + 2.4, 12, yb, "blaze");
        roomSign(f, p, k, yb);
    }

    private void garden(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, k = 3, yb = y0 + k * LV + 1;
        if (!touches(f, -17, -17, 17, 17)) return;
        for (int u = -15; u <= 15; u++) for (int v = -15; v <= 15; v++) {             // beds of wart and of fire, paths of magma
            double rho = Math.hypot(u, v), a = Math.atan2(v, u);
            if (rho < 7.5 || rho > 14 || !free(p, k, a, rho)) continue;
            double fa = frac((a - p.ramp0) / (2 * Math.PI)) * 10;
            if (Math.abs(rho - 10.5) < 0.6 || frac(fa) < 0.12) { f.set(u, yb - 1, v, MAGMA); continue; }
            if (((int) fa) % 2 == 0) { f.set(u, yb - 1, v, SOUL); if (Draw.rnd(u, 3, v, s.salt + 19) < 0.7) f.set(u, yb, v, WART); }
            else if (Draw.rnd(u, 4, v, s.salt + 21) < 0.25) { f.set(u, yb - 1, v, RACK); f.set(u, yb, v, FIRE); }
            else f.set(u, yb - 1, v, RACK);
        }
        for (int j = 0; j < 4; j++) {                                                  // ember trees: trunks of bone, crowns of fire-caps
            double a = p.ramp0 + 0.8 + j * Math.PI / 2;
            for (int t = 0; t < 6 && !free(p, k, a, 11); t++) a += 0.3;
            int u = pu(11, a), v = pv(11, a);
            f.box(u, yb, v, u, yb + 5, v, Draw.of(BONE));
            f.d.ellipsoid(f.xd(u, v), yb + 6.5, f.zd(u, v), 2.6, 1.6, 2.6, 0, Draw.mix(CAP, GLOW, 0.12, s.salt + 23));
            f.box(u, yb + 4, v - 1, u, yb + 4, v + 1, Draw.of(FENCE));
        }
        wallChest(f, p, k, p.ramp0 + 1.9, yb, LOOT);
        wallChest(f, p, k, p.ramp0 + 4.9, yb, RICH);
        spawnerAt(f, p, k, p.ramp0 + 3.4, 13, yb, "salamander");
        for (int j = 0; j < 6; j++) { double a = j * Math.PI / 3; f.set(pu(9, a), y0 + 4 * LV, pv(9, a), GLOW); }
        roomSign(f, p, k, yb);
    }

    private void bells(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, k = 4, yb = y0 + k * LV + 1, top = y0 + 5 * LV - 1;
        if (!touches(f, -15, -15, 15, 15)) return;
        for (int j = 0; j < 3; j++) {                                                  // three great bells
            double a = Math.PI / 2 + 0.5 + j * 2 * Math.PI / 3;
            for (int t = 0; t < 8 && !free(p, k, a, 9); t++) a += 0.2;
            int u = pu(9, a), v = pv(9, a);
            f.box(u, top - 1, v, u, top, v, Draw.of(FENCE));
            double[] rr = {1.0, 1.5, 1.8, 2.1, 2.6};
            for (int i = 0; i < rr.length; i++) f.disk(u, v, rr[i], top - 2 - i, Draw.of(i == rr.length - 1 ? ORANGE_T : YELLOW_T));
            f.ring(u, v, 2.6, 1, top - 6, top - 6, Draw.of(ORANGE_T));
            f.set(u, top - 6, v, FENCE); f.set(u, top - 7, v, FENCE);
        }
        for (int j = 0; j < 8; j++) {                                                  // the belfry's arches over the cavern
            double a = p.rib0 + j * Math.PI / 4 + 0.39;
            if (rampNear(p, a, k * LV + 5, 7.5)) continue;
            radial(f, a, rc(k * LV + 5) - 3.8, rc(k * LV + 5) + 0.8, 1.5, yb + 1, yb + 6, Draw.AIR);
        }
        wallChest(f, p, k, p.ramp0 + 0.5, yb, LOOT);
        wallChest(f, p, k, p.ramp0 + 3.5, yb, RICH);
        spawnerAt(f, p, k, p.ramp0 + 2.0, 11.5, yb, "soul_wraith");
        for (int j = 0; j < 4; j++) { double a = j * Math.PI / 2 + 0.8; f.set(pu(8, a), y0 + 5 * LV, pv(8, a), GLOW); }
        roomSign(f, p, k, yb);
    }

    private void observatory(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, k = 5, yb = y0 + k * LV + 1;
        if (!touches(f, -13, -13, 13, 13)) return;
        for (int u = -11; u <= 11; u++) for (int v = -11; v <= 11; v++) {              // the vault of stars
            double rho = Math.hypot(u, v);
            if (rho < 6.5 || rho > 11) continue;
            if (Draw.rnd(u, 5, v, s.salt + 25) < 0.07) f.set(u, y0 + 6 * LV, v, GLOW);
        }
        // the orrery round the stair: rings of iron, its worlds of magma, glowstone and obsidian
        for (int j = 0; j < 36; j++) {
            double a = j * Math.PI / 18;
            f.set(pu(6.8, a), yb + 6, pv(6.8, a), IRON);
            f.set(pu(8.6, a + 0.05), yb + 8, pv(8.6, a + 0.05), IRON);
        }
        f.set(pu(6.8, p.ramp0), yb + 5, pv(6.8, p.ramp0), MAGMA);
        f.set(pu(8.6, p.ramp0 + 2), yb + 7, pv(8.6, p.ramp0 + 2), GLOW);
        f.set(pu(8.6, p.ramp0 + 4), yb + 7, pv(8.6, p.ramp0 + 4), OBSIDIAN);
        // the great glass: a black tube aimed through a window at the dome
        double ta = p.ramp0 + 1.0;
        for (int t = 0; t < 24 && (!free(p, k, ta, 9) || rampNear(p, ta, k * LV + 6, 6)); t++) ta += 0.25;
        f.line(pu(7.5, ta), yb + 1, pv(7.5, ta), pu(10.5, ta), yb + 4, pv(10.5, ta), 0.8, Draw.of(BLACK_T));
        f.set(pu(11, ta), yb + 4, pv(11, ta), BLACK_GLASS);
        radial(f, ta, rc(k * LV + 6) - 3.6, rc(k * LV + 6) + 0.8, 1.0, yb + 3, yb + 5, Draw.AIR);
        f.set(pu(7.5, ta), yb, pv(7.5, ta), CHISELED);
        wallChest(f, p, k, p.ramp0 + 2.9, yb, RICH);
        wallChest(f, p, k, p.ramp0 + 4.6, yb, LOOT);
        spawnerAt(f, p, k, p.ramp0 + 5.6, 10, yb, "pyre_warden");
        roomSign(f, p, k, yb);
    }

    /** The Reliquary: the vault walled round the stair just under the crown, its door sealed until the Archon falls. */
    private void reliquary(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, yb = y0 + 6 * LV + 1;
        if (!touches(f, -12, -12, 12, 12)) return;
        f.chest(0, yb, 8, Draw.NORTH, VAULT);
        f.chest(8, yb, 0, Draw.WEST, VAULT);
        f.chest(-8, yb, 0, Draw.EAST, VAULT);
        f.chest(6, yb, 6, Draw.NORTH, RICH);
        f.chest(-6, yb, 6, Draw.NORTH, RICH);
        for (int[] q : new int[][]{{6, -6}, {-6, -6}}) {                                // relic cases
            f.box(q[0] - 1, yb, q[1] - 1, q[0] + 1, yb + 2, q[1] + 1, Draw.of(IRON));
            f.set(q[0], yb, q[1], CHISELED);
            f.skull(q[0], yb + 1, q[1], 0, 0);
        }
        for (int[] q : new int[][]{{0, -8}, {7, 3}, {-7, 3}}) f.set(q[0], y0 + TOP - 1, q[1], GLOW);
        f.wallSign(0, yb + 3, -4, Draw.SOUTH, "THE RELIQUARY", "Sealed until", "the Archon", "falls");
    }

    /** The Archon's Crown: the open summit inside the circlet of blades, its sigil, low walls and braziers. */
    private void crown(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, fl = y0 + TOP;
        if (!touches(f, -26, -26, 26, 26)) return;
        double edge = R_TOP + 0.3;
        for (int u = -15; u <= 15; u++) for (int v = -15; v <= 15; v++) {              // the floor: the Archon's sigil
            double rho = Math.hypot(u, v), a = Math.atan2(v, u);
            if (rho > edge || rho < ST_RO + 0.5) continue;
            double kk = (a - p.ramp0) / (Math.PI / 3), off = Math.abs(kk - Math.rint(kk)) * (Math.PI / 3) * rho;
            f.set(u, fl, v, Math.abs(rho - 10) < 0.55 ? MAGMA : off < 0.6 && rho > 6 ? RED_BRICK : ((u + v) & 1) == 0 ? OBSIDIAN : BLACK_T);
            if (rho > edge - 0.9 && Math.abs(wrap(a - p.ramp0 + 0.12)) > 0.32 && !blade(p, rho, a, TOP + 1)) {   // a kerb round the rim, open where the ramp arrives
                f.set(u, fl + 1, v, CRACKED);
                f.set(u, fl + 2, v, FENCE);
            }
        }
        for (int j = 0; j < 4; j++) {                                                  // low walls to hide behind
            double a0 = p.ramp0 + Math.PI / 4 + j * Math.PI / 2;
            for (double a = a0 - 0.34; a <= a0 + 0.34; a += 0.05) for (double rho = 8; rho <= 8.9; rho += 0.45) {
                int u = pu(rho, a), v = pv(rho, a);
                f.set(u, fl + 1, v, CRACKED);
                f.set(u, fl + 2, v, IRON);
            }
        }
        for (int j = 0; j < 4; j++) { double a = p.ramp0 + Math.PI / 2 + j * Math.PI / 2; brazier(f, pu(11.5, a), fl, pv(11.5, a)); }
        // a light on the tip of every blade
        for (int i = 0; i < 6; i++) {
            double a = p.rib0 + i * Math.PI / 3 + FIN_TW * (p.spikeTop - 1);
            int u = pu(R_TOP + 0.4, a), v = pv(R_TOP + 0.4, a);
            int y = -1;
            for (int h = p.spikeTop; h >= TOP && y < 0; h--) if (blade(p, Math.hypot(u, v), Math.atan2(v, u), h)) y = h;
            if (y > 0) { f.set(u, y0 + y, v, RACK); f.set(u, y0 + y + 1, v, FIRE); }
        }
        f.point(pu(7, p.ramp0 + Math.PI), fl + 1, pv(7, p.ramp0 + Math.PI), "lord:" + s.kind.lord);
    }

    // ---- the watchtowers and their sky bridges -----------------------------------------------------------------------------
    private void watchtower(Draw.Frame f, Colossi.Site s, Plan p, int i) {
        int y0 = s.y;
        double a = p.satA[i];
        double cu = SAT_D * Math.cos(a), cv = SAT_D * Math.sin(a);
        int iu = (int) Math.round(cu), iv = (int) Math.round(cv), top = y0 + p.satT[i], hb = y0 + p.satB[i];
        Draw.Mat stone = ashMat(s.salt + 31);
        if (touches(f, iu - 13, iv - 13, iu + 13, iv + 13)) {
            f.cyl(cu, cv, SAT_R, y0 - 4, top, stone);
            for (int y = y0 + 6; y < top; y += 8) f.ring(cu, cv, SAT_R + 0.6, 1, y, y, Draw.of(BRICK));
            f.cyl(cu, cv, 4.5, y0 + 1, top, Draw.AIR);
            f.disk(cu, cv, 4.5, y0, Draw.of(CRACKED));
            f.cyl(cu, cv, 0.9, y0 + 1, top, Draw.of(BRICK));
            helix(f, cu, cv, ST_RI, 4.5, y0 + 1, top, p.satRise[i], a + 0.35, Draw.of(BLACK_T));
            // the door at the foot (outwards) and the door to the bridge (towards the spire)
            radialAt(f, cu, cv, a, 3.5, SAT_R + 1.5, 1.5, y0 + 1, y0 + 4, Draw.AIR);
            radialAt(f, cu, cv, a, 3.5, SAT_R + 1.5, 1.5, y0, y0, Draw.of(CRACKED));
            radialAt(f, cu, cv, a + Math.PI, 3.5, SAT_R + 0.6, 1.5, hb + 1, hb + 3, Draw.AIR);
            // the roof: battlements, horns of stone at the corners, a pyre; the stair comes up through it
            f.ring(cu, cv, SAT_R, 1, top + 1, top + 1, Draw.of(CRACKED));
            for (int k = 0; k < 16; k += 2) {
                double ka = k * Math.PI / 8;
                f.set((int) Math.round(cu + Math.cos(ka) * (SAT_R - 0.3)), top + 2, (int) Math.round(cv + Math.sin(ka) * (SAT_R - 0.3)), BONE);
            }
            for (int k = 0; k < 4; k++) {
                double ka = a + Math.PI / 4 + k * Math.PI / 2, hu = cu + Math.cos(ka) * (SAT_R + 0.5), hv = cv + Math.sin(ka) * (SAT_R + 0.5);
                f.d.cone(f.xd(hu, hv), f.zd(hu, hv), 1.3, 0.2, top - 2, top + 6, Draw.mix(BLACK_T, BONE, 0.2, s.salt + 33));
            }
            railing(f, cu, cv, 4.5, top + 1, a + 0.35 + frac((p.satT[i] - 1) / p.satRise[i]) * 2 * Math.PI);
            brazier(f, (int) Math.round(cu + Math.cos(a - Math.PI / 2) * 6), top, (int) Math.round(cv + Math.sin(a - Math.PI / 2) * 6));
            int ku = (int) Math.round(cu + Math.cos(a + Math.PI * 0.75) * 5.6), kv = (int) Math.round(cv + Math.sin(a + Math.PI * 0.75) * 5.6);
            f.chest(ku, top + 1, kv, inward(ku - cu, kv - cv), RICH);
            for (int y = y0 + 10; y < top - 2; y += 9) for (int k = 0; k < 3; k++)                 // arrow slits
                radialAt(f, cu, cv, a + 1.2 + k * 1.9, 3.6, SAT_R + 0.6, 0.5, y, y + 2, Draw.AIR);
            int su = (int) Math.round(cu + Math.cos(a) * (SAT_R + 1.5) + Math.cos(a + Math.PI / 2) * 2.5);
            int sv = (int) Math.round(cv + Math.sin(a) * (SAT_R + 1.5) + Math.sin(a + Math.PI / 2) * 2.5);
            plate(f, su, y0 + 3, sv, a, y0 + 1, "WATCHTOWER", WATCH[i].substring(4), "Its bridge leads", "to the ramp");
        }
        // the sky bridge: a deck on a deep arch, from the tower to the ramp's outer wall
        double rin = rc(p.satB[i]) + RW - 1.2, rout = SAT_D - SAT_R + 0.5;
        double ca = Math.cos(a), sa = Math.sin(a);
        int u0 = (int) Math.floor(Math.min(rin * ca, rout * ca) - 4), u1 = (int) Math.ceil(Math.max(rin * ca, rout * ca) + 4);
        int v0 = (int) Math.floor(Math.min(rin * sa, rout * sa) - 4), v1 = (int) Math.ceil(Math.max(rin * sa, rout * sa) + 4);
        if (touches(f, u0, v0, u1, v1)) {
            for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                double along = u * ca + v * sa, perp = Math.abs(-u * sa + v * ca);
                if (along < rin || along > rout || perp > 2.6) continue;
                double t = 2 * (along - rin) / (rout - rin) - 1;
                int under = hb - 1 - (int) Math.round(9 * t * t);
                f.box(u, under, v, u, hb - 1, v, perp > 1.8 ? Draw.of(BLACK_T) : stone);
                f.set(u, hb, v, perp > 1.8 ? CRACKED : perp < 0.6 ? RED_BRICK : BRICK);
                f.box(u, hb + 1, v, u, hb + 4, v, Draw.AIR);
                if (perp > 1.8) {
                    f.set(u, hb + 1, v, CRACKED);
                    if (Math.floorMod((int) Math.round(along), 4) == 0) { f.set(u, hb + 2, v, CRACKED); f.set(u, hb + 3, v, BONE); }
                }
            }
        }
    }

    // ---- the plain: the Ash Wall, its gates and roads, the Pyre Fields, the dunes ----------------------------------------------
    private void plain(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        int[] c = clip(f);
        int lim = WALL_R + 8;
        int ua = Math.max(c[0], -lim), ub = Math.min(c[2], lim), va = Math.max(c[1], -lim), vb = Math.min(c[3], lim);
        double[] gates = {p.satA[0], p.satA[1], p.satA[2], p.satA[3], p.ramp0 + Math.PI};
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) {
            double rho = Math.sqrt((double) u * u + (double) v * v);
            if (rho < 30 || rho > lim) continue;
            double a = Math.atan2(v, u);
            int x = f.x(u, v), z = f.z(u, v);
            // the roads from the gates
            boolean road = false;
            for (int g = 0; g < 5; g++) {
                double inner = g < 4 ? SAT_D + SAT_R : R0 + FIN + 1;
                if (rho >= inner - 1 && rho <= WALL_R + 3 && Math.abs(wrap(a - gates[g])) * rho <= 2.6) road = true;
            }
            if (road) {
                int fy = s.floorAt(x, z);
                f.d.set(x, fy, z, Draw.rnd(x, 1, z, s.salt + 35) < 0.7 ? CRACKED : STONE_BRICK);
                f.d.set(x, fy + 1, z, 0); f.d.set(x, fy + 2, z, 0);
            }
            if (rho < WALL_R - 1.6 || rho > WALL_R + 1.6) continue;
            // the Ash Wall: broken, crenellated, with five gates
            double gd = 99;
            for (double ga : gates) gd = Math.min(gd, Math.abs(wrap(a - ga)) * rho);
            if (gd < 4.5) continue;
            int fy = s.floorAt(x, z);
            double n = Draw.noise(a * 40, p.wall0, 7, s.salt + 37), n2 = Draw.noise(a * 90, p.wall0 + 50, 3, s.salt + 39);
            int hgt = gd < 9 ? 15 : n < -0.32 ? (int) Math.round(1 + (n2 + 1) * 1.5) : 7 + (int) Math.round(n * 4 + n2 * 1.5);
            for (int y = fy - 2; y <= fy + hgt; y++) f.d.set(x, y, z, ash(x, y, z, s.salt + 41));
            if (hgt > 5 && Math.floorMod((int) Math.floor(a * rho / 2), 2) == 0 && rho > WALL_R + 0.5) f.d.set(x, fy + hgt + 1, z, CRACKED);
            if (gd < 9 && hgt == 15) f.d.set(x, fy + 16, z, Math.floorMod((int) Math.floor(a * rho), 2) == 0 ? BONE : CRACKED);
        }
        // the gates' lintels and braziers
        for (double ga : gates) {
            int gu = pu(WALL_R, ga), gv = pv(WALL_R, ga);
            if (!touches(f, gu - 8, gv - 8, gu + 8, gv + 8)) continue;
            int fy = s.floorAt(f.x(gu, gv), f.z(gu, gv));
            radial(f, ga, WALL_R - 1.6, WALL_R + 1.6, 4.6, fy + 9, fy + 11, Draw.of(BRICK));
            radial(f, ga, WALL_R - 1.6, WALL_R + 1.6, 3.6, fy + 8, fy + 8, Draw.of(BONE));
            for (int side = -1; side <= 1; side += 2) {
                int bu = pu(WALL_R + 3, ga + side * 6.0 / WALL_R), bv = pv(WALL_R + 3, ga + side * 6.0 / WALL_R);
                brazier(f, bu, s.floorAt(f.x(bu, bv), f.z(bu, bv)) + 1, bv);
            }
        }
        // the Pyre Fields
        for (int[] q : p.pyres) {
            if (!touches(f, q[0] - 4, q[1] - 4, q[0] + 4, q[1] + 4)) continue;
            int yb = q[2];
            f.box(q[0] - 3, yb - 1, q[1] - 3, q[0] + 3, yb, q[1] + 3, Draw.of(CRACKED));
            f.box(q[0] - 3, yb + 1, q[1] - 3, q[0] + 3, yb + 4, q[1] + 3, Draw.AIR);
            f.box(q[0] - 1, yb + 1, q[1] - 1, q[0] + 1, yb + 2, q[1] + 1, Draw.of(RACK));
            f.box(q[0] - 1, yb + 3, q[1] - 1, q[0] + 1, yb + 3, q[1] + 1, Draw.of(FIRE));
            f.box(q[0] - 2, yb + 1, q[1] - 2, q[0] + 2, yb + 1, q[1] - 2, Draw.of(BONE));
            f.box(q[0] - 2, yb + 1, q[1] + 2, q[0] + 2, yb + 1, q[1] + 2, Draw.of(BONE));
            f.skull(q[0] + 2, yb + 1, q[1], 0, Math.floorMod(q[0], 16));
            f.skull(q[0] - 2, yb + 1, q[1], 2, Math.floorMod(q[1], 16));
        }
        Draw.Mat dune = Draw.mix(LGREY_T, WHITE_T, 0.25, s.salt + 43);
        for (int[] q : p.dunes) {
            if (!touches(f, q[0] - q[2], q[1] - q[2], q[0] + q[2], q[1] + q[2])) continue;
            f.d.ellipsoid(f.xd(q[0], q[1]), q[4], f.zd(q[0], q[1]), q[2], q[3] + 0.5, q[2], 0, dune);
        }
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y;
        // the ramp's flame vents
        for (double[] q : p.vents) {
            int h = (int) Math.floor(rampH(p, q[0], (int) q[1]));
            double rho = rc(h) + RW / 2.0;
            int u = pu(rho, q[0]), v = pv(rho, q[0]);
            out.add(Ordeals.flames(fr.box(u - 3, y0 + h + 1, v - 3, u + 3, y0 + h + 3, v + 3), 70));
        }
        // arrow slits along the ramp where it runs north-south (east side) and east-west (south side)
        for (int t = 1; t <= 2; t++) {
            double a = t == 1 ? 0 : Math.PI / 2;
            int h = (int) Math.floor(rampH(p, a, t));
            int r0 = (int) Math.floor(rc(h));
            if (t == 1) out.add(Ordeals.arrows(fr.box(r0, y0 + h + 1, -6, r0 + RW - 1, y0 + h + 3, 6), fr.box(r0 - 1, y0 + h + 1, -8, r0 + RW, y0 + h + 3, 8)));
            else out.add(Ordeals.arrows(fr.box(-6, y0 + h + 1, r0, 6, y0 + h + 3, r0 + RW - 1), fr.box(-8, y0 + h + 1, r0 - 1, 8, y0 + h + 3, r0 + RW)));
        }
        // falling ash on the last turn
        double ra = p.ramp0 + 3.0;
        int rh = (int) Math.floor(rampH(p, ra, 2));
        int ru = pu(rc(rh) + 3, ra), rv = pv(rc(rh) + 3, ra);
        out.add(Ordeals.rubble(fr.box(ru - 3, y0 + rh + 1, rv - 3, ru + 3, y0 + rh + 4, rv + 3), LGREY_T));
        // the watchtowers' fire-throwers watch the ramp
        for (int i = 0; i < 4; i++) {
            double a = p.satA[i];
            int cu = pu(SAT_D, a), cv = pv(SAT_D, a), mu = pu(30, a), mv = pv(30, a);
            out.add(Ordeals.cannon(fr.box(mu - 22, y0 + p.satB[i] - 24, mv - 22, mu + 22, y0 + p.satB[i] + 20, mv + 22), fr.at(cu, y0 + p.satT[i] + 4, cv)));
        }
        // inside: the Library's choking ash, the Forge's vents, the stair's hidden archers
        int lb = y0 + LV + 1;
        double ga = p.ramp0 + 3.3;
        out.add(Ordeals.gas(fr.box(pu(13, ga) - 6, lb, pv(13, ga) - 6, pu(13, ga) + 6, lb + 2, pv(13, ga) + 6)));
        out.add(Ordeals.flames(fr.box(-11, y0 + 2 * LV + 1, -11, 11, y0 + 2 * LV + 3, 11), 90));
        out.add(Ordeals.arrows(fr.box(-5, y0 + 3 * LV + 1, -5, 5, y0 + 4 * LV, 5), fr.box(-6, y0 + 3 * LV + 1, -7, 6, y0 + 4 * LV, 7)));
        // the Reliquary opens when the Archon falls
        out.add(Ordeals.bossSeal("spire_vault", fr.box(-1, y0 + 6 * LV + 1, -5, 1, y0 + 6 * LV + 3, -5), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern ---------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61), n = Draw.noise(x, z, 26, s.salt + 63);
        if (Math.abs(Draw.noise(x, z, 17, s.salt + 62)) < 0.02) return MAGMA;
        if (n > 0.2) return q < 0.55 ? LGREY_T : q < 0.75 ? WHITE_T : GREY_T;
        return q < 0.4 ? GREY_T : q < 0.6 ? SOUL : q < 0.8 ? BLACK_T : COAL;
    }

    @Override Draw.Mat under() { return Draw.mix(Draw.of(GREY_T), Draw.of(BLACK_T), 0.4, 9127); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < WALL_R + 8; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.004);
        MegaDesign.spikes(d, x, z, floor, ceil, BLACK_T, s.salt + 53, 0.002);
        if (lake) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.004) d.set(x, floor + 1, z, LGREY_T);
        else if (q < 0.0055) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
        else if (q < 0.0062) { d.set(x, floor + 1, z, BONE); d.set(x, floor + 2, z, BONE); }
    }
}
