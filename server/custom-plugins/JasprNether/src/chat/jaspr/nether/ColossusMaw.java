package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Maw of the Abyss (owner, 2026-10-04: "I want them to be huge structures"): a colossal demon head, some 115 blocks
 * across and 54 high, carved into a monolith of black stone and nether brick in the middle of its cavern. Two great
 * horns rise from its brow near to the dome, its slit eyes burn, and its open jaws are the gate.
 * <ul>
 *   <li>the outer works: the Avenue of Spikes and its gate, the Forecourt of obelisks and impaling spikes before the
 *       face (the eyes and the obelisks spit fire at whoever crosses it), the lava moat crossed by the lolling tongue,
 *       the bone fields with the ribcages of fallen giants and four shrines of offering;</li>
 *   <li>the Maw behind the fangs, and the Gullet: a throat hall climbing under ribs of bone (flame vents, falling floors
 *       over fire and lava, arrow slits), with the Torture Gallery, the Bone Larder, the Ember Shrine and the Kennels
 *       off it;</li>
 *   <li>the Abyssal Pit: a domed chamber 52 across, ribbed with bone, around a chasm of lava, where the Abyssal
 *       Gatekeeper waits before the Gate of the vault (it opens when he falls);</li>
 *   <li>the Spine Stair, climbing through the skull to the Eye Hall (the slit pupils look out over the forecourt) and
 *       the Brow Gallery, from which a stair winds up inside each horn to its lookout.</li>
 * </ul>
 */
final class ColossusMaw extends ColossusDesign {
    // ---- the frame: u across the face, v from the face backwards (the face looks to -v), h = y - the floor (s.y) --------
    static final int HW = 62, HT = 58;                          // the head's half width and height
    static final int FP = -14, VC = 38, VMIN = -34, VMAX = 116;  // the face's plane, the skull's centre, the head's extent (v)
    static final int THROAT = 6, GUL1 = 36;                     // the Gullet runs from the throat to the Pit's door
    static final int PIT_V = 62, PIT_R = 26, PIT_H = 8;         // the Abyssal Pit: centre (v), radius, floor block (h)
    static final int GATE_V = PIT_V + PIT_R, VAULT_V0 = GATE_V + 3, VAULT_V1 = VAULT_V0 + 11;
    static final int EYE_U = 21, EYE_FL = 28, BROW_FL = 41, TUN0 = 27, TUN1 = 56;   // the eyes, the halls' floors, the horn stairs (|u|)
    static final double SPINE_U = -22, SPINE_V = 5, SPINE_RISE = 7.31, SPINE_A0 = 0.35;
    static final byte ROCK = 0, SKIN = 1, EYE = 2, SOCKET = 3, BROW = 4, LIP = 5;
    static final String LOOT = "jaspr:colossus/maw", RICH = LOOT + "_rich", VAULT = LOOT + "_vault";

    static final class Plan {
        int rot, hornTip;
        double[][] horn;                                    // the right horn's centreline (a cubic: u, h, v); the left one mirrors it
        int[] tunV, tunY;                                   // the stair inside each horn: its v and floor (h) per |u| - TUN0
        short[][] vf, vb;                                   // the head's loft: its front and back (v) per (u + HW, h)
        byte[][] tag;                                       // what its face is made of there (rock, skin, eye)
        int[] minF;                                         // the head's foremost v per u + HW
        int[] ridge;                                        // the skull's top (h) along u = 0, per v - VMIN
        final List<int[]> talus = new ArrayList<>();        // fallen rocks round the monolith's foot: u, v, radius, height
        final List<int[]> spikes = new ArrayList<>();       // the forecourt's spikes: u, v, height
        final List<int[]> piles = new ArrayList<>();        // bone piles: u, v, radius, height, base y
        final List<int[]> cages = new ArrayList<>();        // the ribcages of fallen giants: u, v, length, axis, base y
        final List<int[]> shrines = new ArrayList<>();      // the shrines of offering: u, v, base y
        int[] avenueY;                                      // the avenue's road height (y) per v from -101 outwards
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette ------------------------------------------------------------------------------------------------------
    private static final int BRICK = b(112), RED_BRICK = b(215), OBSIDIAN = b(49), BLACK_T = b(159, 15), GREY_T = b(159, 7), RED_T = b(159, 14);
    private static final int MAGMA = b(213), BONE = b(216), WART = b(214), RACK = b(87), SOUL = b(88), GLOW = b(89), FIRE = b(51), LAVA = b(11);
    private static final int FENCE = b(113), IRON = b(101), COAL = b(173), SLAB_TOP = b(44, 14), RED_C = b(251, 14), YELLOW_T = b(159, 4);
    private static final int WEB = b(30), CAULDRON = b(118), STAIR = 114, BROWN_T = b(159, 12);

    private static Draw.Mat flesh(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.55 ? WART : q < 0.9 ? RED_BRICK : RED_T; };
    }

    private static Draw.Mat dark(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.5 ? BRICK : q < 0.85 ? BLACK_T : OBSIDIAN; };
    }

    /** The monolith's stone: black strata banded with nether brick and grey ash, flecked with obsidian. */
    private static int stone(int x, int y, int z, int shift, int salt) {
        double q = Draw.rnd(x, y, z, salt + 13);
        if (q < 0.03) return OBSIDIAN;
        int band = Math.floorMod(y + shift, 11);
        if (band <= 1) return q < 0.75 ? BRICK : RED_BRICK;
        if (band == 5 || band == 6) return q < 0.65 ? GREY_T : BLACK_T;
        if (band == 8) return q < 0.5 ? BROWN_T : BLACK_T;
        return q < 0.12 ? BRICK : q < 0.17 ? COAL : BLACK_T;
    }

    private static int skin(int x, int y, int z, int salt) {
        double crack = Math.abs(Draw.noise(x * 0.7 + z * 0.7, y * 1.6, 7, salt + 19));
        if (crack < 0.03) return MAGMA;
        double q = Draw.rnd(x, y, z, salt + 17);
        return q < 0.24 ? BRICK : q < 0.27 ? BLACK_T : RED_BRICK;
    }

    // ---- the head's shape ----------------------------------------------------------------------------------------------
    /** Half the head's width at height h (seen from the front). */
    static double width(double h) {
        if (h <= 6) return 50 + h;
        if (h <= 34) return 56;
        double t = (h - 34) / 20.0;
        return t >= 1 ? 0 : 56 * Math.cbrt(1 - t * t * t);
    }

    /** Half the skull's depth (v) at height h. */
    static double depth(double h) {
        if (h <= 28) return 70;
        double t = (h - 28) / 27.0;
        return t >= 1 ? 0 : 70 * Math.sqrt(1 - t * t);
    }

    /** The brow's lower edge: meeting low over the nose, rising to the temples (an angry frown). */
    static double browH(double au) { return Math.max(32, 31.5 + 0.5 * (au - 13)); }

    static double eyeC(double au) { return 32 + 0.3 * (au - EYE_U); }

    /** Whether (au, h) lies in the lips' dark rim round the mouth. */
    static boolean lip(double au, int h) {
        if (h < 1) return false;
        double e = (h - MOUTH_C) / (h < MOUTH_C ? 16.0 : MOUTH_B + 2.2);
        return Math.pow(au / (MOUTH_A + 1.5), 4) + e * e <= 1;
    }

    /** War paint of fire: tears from the eyes' inner corners, three claw marks down each cheek, a line up the brow. */
    static boolean paint(double au, int h) {
        if (h >= 13 && h <= 28 && Math.abs(au - (12.5 + (28 - h) * 0.12)) < 0.8) return true;
        for (int k = 0; k < 3; k++) if (h >= 12 && h <= 26 && Math.abs(au - (27 + 2.8 * k + (26 - h) * 0.55)) < 0.65) return true;
        return au < 0.6 && h >= 38 && h <= 47;
    }

    /**
     * The head as a loft along v: for each (u, h) the solid runs from vf to vb. Its cross-sections are squarish
     * (cliff-like) and stepped in ledges; the face is carved in relief on its front.
     */
    private static void loft(Plan p, Colossi.Site s) {
        int n = 2 * HW + 1;
        p.vf = new short[n][HT + 1]; p.vb = new short[n][HT + 1]; p.tag = new byte[n][HT + 1]; p.minF = new int[n];
        java.util.Arrays.fill(p.minF, Integer.MAX_VALUE);
        for (int u = -HW; u <= HW; u++) for (int h = 0; h <= HT; h++) {
            int iu = u + HW;
            double au = Math.abs(u), ledge = Math.floorMod(h, 7) == 3 ? 1.3 : 0;
            double w = width(h) + Draw.noise(h * 0.8, u < 0 ? -500 : 500, 7, s.salt + 3) * 2.5 - ledge;
            p.vf[iu][h] = 1; p.vb[iu][h] = 0;
            if (w <= 0 || au > w) continue;
            double s3 = Math.cbrt(1 - Math.pow(au / w, 3)), dp = depth(h) - ledge;
            double ellF = VC - dp * s3, ellB = VC + dp * s3;
            byte tag = ROCK;
            double rel = 0;
            if (h <= 20) rel += 6 * Math.max(0, 1 - Math.pow(au / 36, 4)) * Math.min(1, (21 - h) / 4.0);   // the muzzle
            if (h <= 4) rel += 3 * Math.max(0, 1 - (au / 30) * (au / 30));                                  // the jaw
            if (h >= 17 && h <= 39) {                                                                        // the snout and the nose's bridge
                double nw = h <= 31 ? 5 + (31 - h) * 0.4 : 4, np = h <= 31 ? 2.5 + (31 - h) * 0.5 : 2.2;
                if (au < nw) rel += np * Math.sqrt(1 - (au / nw) * (au / nw));
            }
            double bh = browH(au), browE = 0;
            if (au <= 41) {                                                                                  // the brow: an angry ridge
                double e = 1 - Math.pow((h - (bh + 2.5)) / 3.2, 2);
                if (e > 0) { browE = e * (au < 35 ? 1 : (41 - au) / 6.0) * (au < 5 ? 0.55 + au * 0.09 : 1); rel += 5.5 * browE; }
            }
            double ce = 1 - Math.pow((au - 38) / 11, 2) - Math.pow((h - 25) / 6.0, 2);                         // the cheekbones
            if (ce > 0) rel += 4.5 * ce;
            rel += Draw.noise(u, h, 5, s.salt + 7) * 1.2;
            double ex = (au - EYE_U) / 10.0, ey = (h - eyeC(au)) / 4.2, ee = ex * ex + ey * ey;              // the eyes, in dark sockets
            if (ee <= 1 && h <= bh - 1) { rel = -2; tag = EYE; }
            else if (ee <= 1.6 && h <= bh + 0.5) { rel = Math.min(rel, -1); tag = SOCKET; }
            double face = FP - rel;
            if (tag == ROCK && face >= ellF - 0.5 && au <= 46 && h <= 47) tag = browE > 0.3 ? BROW : lip(au, h) ? LIP : SKIN;
            p.vf[iu][h] = (short) Math.round(Math.max(ellF, face));
            p.vb[iu][h] = (short) Math.round(ellB + Draw.noise(u * 0.6, h * 0.6, 9, s.salt + 9) * 3.5);
            p.tag[iu][h] = tag;
            p.minF[iu] = Math.min(p.minF[iu], p.vf[iu][h]);
        }
    }

    // ---- plan -----------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        loft(p, s);
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        // the horns: from the brow's corners out and up, curling at the tips; lowered where the dome comes down
        p.horn = new double[][]{{28, 44, 4}, {48, 58, 4}, {72, 60, 0}, {78, 76, -8}};
        for (int pass = 0; pass < 4; pass++) {
            double excess = 0;
            for (int i = 8; i <= 48; i++) {
                double t = i / 48.0;
                double[] c = cub(p.horn, t);
                for (int side = -1; side <= 1; side += 2) {
                    int u = (int) Math.round(side * c[0]), v = (int) Math.round(c[2]);
                    excess = Math.max(excess, s.y + c[1] + hornR(t) + 3 - s.ceilAt(fr.x(u, v), fr.z(u, v)));
                }
            }
            if (excess <= 0) break;
            for (int k = 1; k < 4; k++) p.horn[k][1] -= (excess + 0.5) * k / 3.0;
        }
        p.hornTip = (int) Math.round(p.horn[3][1]);
        int tn = TUN1 - TUN0 + 1, prev = BROW_FL - 1;
        p.tunV = new int[tn]; p.tunY = new int[tn];
        for (int k = 0; k < tn; k++) {
            int u = TUN0 + k;
            double bt = 0, bd = 1e9;
            for (int i = 0; i <= 1000; i++) { double t = i / 1000.0, du = Math.abs(cub(p.horn, t)[0] - u); if (du < bd) { bd = du; bt = t; } }
            double[] c = cub(p.horn, bt);
            int fy = Math.max(prev, Math.min(prev + 1, (int) Math.round(c[1]) - 2));
            p.tunY[k] = fy; p.tunV[k] = (int) Math.round(c[2]); prev = fy;
        }
        // the skull's ridge along u = 0
        p.ridge = new int[VMAX - VMIN + 1];
        for (int v = VMIN; v <= VMAX; v++) {
            int top = -1;
            for (int h = 0; h <= HT; h++) if (p.vf[HW][h] <= v && v <= p.vb[HW][h]) top = h;
            p.ridge[v - VMIN] = top;
        }
        // fallen rocks round the monolith's foot (not before the face)
        for (int k = 0; k < 26; k++) {
            double a = Math.PI * (-0.12 + 1.24 * k / 25.0) + (r.nextDouble() - 0.5) * 0.08;
            int u = (int) Math.round(Math.cos(a) * (60 + r.nextInt(8))), v = VC + (int) Math.round(Math.sin(a) * (72 + r.nextInt(8)));
            p.talus.add(new int[]{u, v, 4 + r.nextInt(5), 3 + r.nextInt(7)});
        }
        // the forecourt's spikes
        for (int v = -94; v <= -50; v += 11) for (int side = -1; side <= 1; side += 2) {
            p.spikes.add(new int[]{side * 15, v, 7 + r.nextInt(6)});
            p.spikes.add(new int[]{side * 28, v + 5, 9 + r.nextInt(7)});
        }
        // the avenue's road follows the cavern floor out to the wall
        p.avenueY = new int[80];
        for (int k = 0; k < 80; k++) { int v = -101 - k; p.avenueY[k] = s.floorAt(fr.x(0, v), fr.z(0, v)); }
        // the shrines of offering, then the bone fields around them
        double[] sa = {-2.3, -0.85, 0.55, 2.55};
        for (double a0 : sa) {
            double a = a0 + (r.nextDouble() - 0.5) * 0.25, d = 100 + r.nextInt(12);
            int u = (int) Math.round(Math.cos(a) * d), v = (int) Math.round(Math.sin(a) * d) + 10;
            p.shrines.add(new int[]{u, v, s.floorAt(fr.x(u, v), fr.z(u, v))});
        }
        for (int t = 0; t < 400 && p.cages.size() < 7; t++) {
            double a = r.nextDouble() * Math.PI * 2, d = 78 + r.nextDouble() * 62;
            int u = (int) Math.round(Math.cos(a) * d), v = (int) Math.round(Math.sin(a) * d) + 10, len = 16 + r.nextInt(12), axis = r.nextInt(2);
            int u1 = axis == 0 ? u + len : u, v1 = axis == 0 ? v : v + len;
            if (reserved(p, u - 8, v - 8, u1 + 8, v1 + 8) || Math.hypot(u1, v1 - 10) > 146) continue;
            p.cages.add(new int[]{u, v, len, axis, s.floorAt(fr.x(u, v), fr.z(u, v))});
        }
        for (int t = 0; t < 600 && p.piles.size() < 40; t++) {
            double a = r.nextDouble() * Math.PI * 2, d = 70 + r.nextDouble() * 80;
            int u = (int) Math.round(Math.cos(a) * d), v = (int) Math.round(Math.sin(a) * d) + 10, rad = 2 + r.nextInt(4);
            if (reserved(p, u - rad - 2, v - rad - 2, u + rad + 2, v + rad + 2)) continue;
            p.piles.add(new int[]{u, v, rad, 1 + r.nextInt(3), s.floorAt(fr.x(u, v), fr.z(u, v))});
        }
        p.garrisons = complete(garrisonsOf(p, s));
        return p;
    }

    /** Whether a box (local) meets the head, the forecourt and its ways, a shrine or a ribcage already planned. */
    private static boolean reserved(Plan p, int u0, int v0, int u1, int v1) {
        if (u1 >= -74 && u0 <= 74 && v1 >= -36 && v0 <= 124) return true;              // the head and its talus
        if (u1 >= -58 && u0 <= 58 && v1 >= -106 && v0 <= -20) return true;            // the forecourt and the moat
        if (u1 >= -12 && u0 <= 12 && v0 <= -100) return true;                           // the avenue
        for (int[] q : p.shrines) if (u1 >= q[0] - 13 && u0 <= q[0] + 13 && v1 >= q[1] - 13 && v0 <= q[1] + 13) return true;
        for (int[] c : p.cages) {
            int cu1 = c[3] == 0 ? c[0] + c[2] : c[0], cv1 = c[3] == 0 ? c[1] : c[1] + c[2];
            if (u1 >= c[0] - 8 && u0 <= cu1 + 8 && v1 >= c[1] - 8 && v0 <= cv1 + 8) return true;
        }
        return false;
    }

    private static List<Garrison> garrisonsOf(Plan p, Colossi.Site s) {
        int y0 = s.y;
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(-20, y0 + 1, -62, "!infernal_knight+wither_skeleton"));                 // the forecourt
        gs.add(g(21, y0 + 1, -84, "pigman_berserker+zombie_pigman+cinder_imp"));
        gs.add(g(0, p.avenueY[30] + 1, -131, "hellhound+hellhound+dread_rider"));        // the avenue
        String[] shrinePacks = {"cinder_witch+flame_adept", "ember_legionnaire+salamander", "charred_ghoul+ashbone_archer+skeleton", "wight+spinout"};
        for (int i = 0; i < 4; i++) { int[] q = p.shrines.get(i); gs.add(g(q[0] + 3, q[2] + 1, q[1] + 3, shrinePacks[i])); }
        gs.add(g(-14, y0 + 1, -6, "!magma_hulk+magma_cube+ember"));                      // the Maw
        gs.add(g(0, y0 + gfl(14) + 1, 14, "brute+nethermite"));                          // the Gullet
        gs.add(g(0, y0 + gfl(26) + 1, 26, "crypt_guard+tomb_guardian"));
        gs.add(g(-22, y0 + 3, 18, "mummy+lost_soul+soul_wraith"));                       // the Torture Gallery
        gs.add(g(22, y0 + 3, 16, "deep_crawler+brimstone_spider+spore"));                // the Bone Larder
        gs.add(g(-22, y0 + 6, 30, "pyre_warden+blaze+frost"));                           // the Ember Shrine
        gs.add(g(22, y0 + 6, 28, "hellhound+mogus+spore_creeper"));                      // the Kennels
        gs.add(g(8, y0 + EYE_FL + 1, -5, "shade+royal_guard"));                          // the Eye Hall
        gs.add(g(8, y0 + BROW_FL + 1, 6, "asp+scarab+coolmar_spider"));                  // the Brow Gallery
        // the great fliers, in the open air: before the face, around the horns, behind the head
        gs.add(g(-6, y0 + 12, -72, "ghast+ghastling"));
        gs.add(g(6, y0 + 12, -88, "ghastling+ghastling"));
        int look = y0 + p.tunY[p.tunY.length - 1] + 3;
        gs.add(g(-62, look, -18, "ghastling+ghast"));
        gs.add(g(62, look, -18, "ghastling+ghastling"));
        gs.add(g(0, y0 + 12, 134, "ghast"));
        return gs;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        grounds(f, s, p);
        talus(f, s, p);
        head(f, s, p);
        horns(f, s, p);
        mouth(f, s, p);
        gullet(f, s);
        rooms(f, s);
        pit(f, s);
        vault(f, s);
        eyeHall(f, s);
        browGallery(f, s);
        spine(f, s);
        hornTunnels(f, s, p);
        spines(f, s, p);
        outer(f, s, p);
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

    /** A room: floor block at yb - 1, ceiling at yt, the inside cleared. */
    private static void room(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int yt, Draw.Mat floor, Draw.Mat wall, Draw.Mat ceil) {
        if (!touches(f, u0, v0, u1, v1)) return;
        f.box(u0, yb - 1, v0, u1, yt, v1, wall);
        f.box(u0, yt, v0, u1, yt, v1, ceil);
        f.box(u0 + 1, yb, v0 + 1, u1 - 1, yt - 1, v1 - 1, Draw.AIR);
        f.box(u0 + 1, yb - 1, v0 + 1, u1 - 1, yb - 1, v1 - 1, floor);
    }

    /** A fire on netherrack. */
    private static void brazier(Draw.Frame f, int u, int y, int v) { f.set(u, y, v, RACK); f.set(u, y + 1, v, FIRE); }

    private static void ell(Draw.Frame f, double u, double y, double v, double ru, double ry, double rv, Draw.Mat m) {
        boolean odd = (f.rot & 1) == 1;
        f.d.ellipsoid(f.xd(u, v), y, f.zd(u, v), odd ? rv : ru, ry, odd ? ru : rv, 0, m);
    }

    /** The head, column by column: stone in strata, the face's skin, the burning eyes. */
    private void head(Draw.Frame f, Colossi.Site s, Plan p) {
        int[] c = clip(f);
        int ua = Math.max(c[0], -HW), ub = Math.min(c[2], HW), va = Math.max(c[1], VMIN), vb = Math.min(c[3], VMAX);
        int y0 = s.y, salt = s.salt;
        for (int u = ua; u <= ub; u++) {
            int iu = u + HW;
            double au = Math.abs(u);
            for (int v = Math.max(va, p.minF[iu]); v <= vb; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                int shift = (int) Math.round(Draw.noise(x, z, 23, salt + 1) * 3);
                for (int h = -3; h <= HT; h++) {
                    int k = h < 0 ? 0 : h;
                    int a = p.vf[iu][k], e = p.vb[iu][k];
                    if (v < a || v > e) continue;
                    int y = y0 + h, dF = v - a, blk;
                    byte t = p.tag[iu][k];
                    if (t == EYE && dF <= 1) blk = eye(u, h);
                    else if (t == SOCKET && dF <= 1) blk = Draw.rnd(x, y, z, salt + 21) < 0.5 ? OBSIDIAN : BLACK_T;
                    else if (t == BROW && dF <= 2) { double q = Draw.rnd(x, y, z, salt + 23); blk = q < 0.12 ? RED_BRICK : q < 0.55 ? OBSIDIAN : BRICK; }
                    else if (t == LIP && dF <= 1) blk = Draw.rnd(x, y, z, salt + 25) < 0.6 ? BLACK_T : BRICK;
                    else if (t == SKIN && dF <= 2) blk = dF == 0 && paint(au, h) ? MAGMA : skin(x, y, z, salt);
                    else blk = stone(x, y, z, shift, salt);
                    f.d.set(x, y, z, blk);
                }
            }
        }
    }

    private static int eye(int u, int h) {
        double au = Math.abs(u), ex = (au - EYE_U) / 10.0, ey = (h - eyeC(au)) / 4.2, ee = ex * ex + ey * ey;
        if (Math.abs(au - EYE_U) < 0.5 && ey * ey < 0.85) return IRON;         // the slit pupil (a window from the Eye Hall)
        return ee < 0.6 ? GLOW : MAGMA;
    }

    /** Fallen rocks heaped round the monolith's foot. */
    private void talus(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw.Mat rock = (x, y, z) -> stone(x, y, z, 0, s.salt);
        for (int[] q : p.talus) {
            if (!touches(f, q[0] - q[2], q[1] - q[2], q[0] + q[2], q[1] + q[2])) continue;
            ell(f, q[0], s.y + q[3] * 0.3, q[1], q[2], q[3], q[2] * 0.8, rock);
        }
    }

    // ---- the horns ------------------------------------------------------------------------------------------------------
    private static double[] cub(double[][] q, double t) {
        double a = (1 - t) * (1 - t) * (1 - t), b = 3 * (1 - t) * (1 - t) * t, c = 3 * (1 - t) * t * t, d = t * t * t;
        return new double[]{a * q[0][0] + b * q[1][0] + c * q[2][0] + d * q[3][0], a * q[0][1] + b * q[1][1] + c * q[2][1] + d * q[3][1],
            a * q[0][2] + b * q[1][2] + c * q[2][2] + d * q[3][2]};
    }

    static double hornR(double t) { return t < 0.7 ? 6.6 - 3.6 * t : 4.08 * (1 - (t - 0.7) / 0.3) + 0.6 * ((t - 0.7) / 0.3); }

    /** A horn's stuff along its length: black at the root through ash to bone at the tip, ringed with ridges. */
    private static Draw.Mat hornMat(double t, boolean ridge, int salt) {
        return (x, y, z) -> {
            double q = Draw.rnd(x, y, z, salt), tt = t + (q - 0.5) * 0.12;
            if (tt < 0.3) return ridge ? OBSIDIAN : q < 0.5 ? BLACK_T : COAL;
            if (tt < 0.45) return ridge ? BLACK_T : BROWN_T;
            if (tt < 0.6) return ridge ? BROWN_T : b(159, 8);
            if (tt < 0.72) return ridge ? b(159, 8) : b(159, 0);
            return ridge && tt < 0.9 ? b(159, 0) : BONE;
        };
    }

    /** Two great horns from the brow's corners, sweeping out and up across the cavern and curling at the tips. */
    private void horns(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, n = 40;
        for (int side = -1; side <= 1; side += 2) {
            if (!touches(f, side * 20, -18, side * 88, 12)) continue;
            for (int i = 0; i < n; i++) {
                double t0 = i / (double) n, t1 = (i + 1) / (double) n, tm = (t0 + t1) / 2;
                double[] a = cub(p.horn, t0), bb = cub(p.horn, t1);
                f.line(side * a[0], y0 + a[1], a[2], side * bb[0], y0 + bb[1], bb[2], hornR(tm), hornMat(tm, i % 3 == 0, s.salt + 31));
            }
        }
    }

    // ---- the jaws ---------------------------------------------------------------------------------------------------------
    static final double MOUTH_C = 9.5, MOUTH_B = 8.5, MOUTH_A = 27;

    /** The mouth: the opening in the muzzle narrowing into the throat, its floor of flesh, the fangs, the tongue, the nostrils. */
    private void mouth(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        Draw.Mat flesh = flesh(s.salt + 41);
        int[] c = clip(f);
        int ua = Math.max(c[0], -29), ub = Math.min(c[2], 29), va = Math.max(c[1], -28), vb = Math.min(c[3], THROAT);
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) {
            double t = Math.max(0, Math.min(1, (v + 22) / 28.0)), A = MOUTH_A * (1 - 0.66 * t), B = MOUTH_B * (1 - 0.18 * t);
            double xx = Math.pow(Math.abs(u) / A, 4);
            if (xx > 1) continue;
            int top = 0;
            for (int h = 1; h <= 20; h++) { double dy = h - MOUTH_C, e = dy < 0 ? dy / 14.0 : dy / B; if (xx + e * e <= 1) top = h; }
            if (top < 1) continue;
            int x = f.x(u, v), z = f.z(u, v);
            for (int h = 1; h <= top; h++) f.d.set(x, y0 + h, z, 0);
            if (v >= p.vf[u + HW][0]) {
                f.d.set(x, y0, z, flesh.at(x, y0, z));
                f.d.set(x, y0 + top + 1, z, (Math.floorMod(v, 5) == 0 && v > -18) ? BONE : flesh.at(x, y0 + top + 1, z));   // the palate's ridges
            }
        }
        if (touches(f, -29, -28, 29, THROAT)) {
            // the lower lip and the fangs
            double A = MOUTH_A * (1 - 0.66 * 0.07), B = MOUTH_B * (1 - 0.18 * 0.07);
            for (int side = -1; side <= 1; side += 2) {
                f.box(side * 6, y0 + 1, -24, side * 23, y0 + 2, -21, flesh);
                for (int[] t : UPPER) {
                    int edge = (int) Math.floor(MOUTH_C + B * Math.sqrt(Math.max(0, 1 - Math.pow(t[0] / A, 4))));
                    fang(f, side * t[0], y0 + edge + 1, -1, t[1], t[2], -21);
                }
                for (int[] t : LOWER) fang(f, side * t[0], y0 + 3, 1, t[1], t[2], -23);
            }
        }
        tongue(f, s);
        // the nostrils: deep, with fires smouldering inside
        for (int side = -1; side <= 1; side += 2) {
            if (!touches(f, side * 2, -32, side * 8, -14)) continue;
            for (int v = -32; v <= -15; v++) for (int u = 3; u <= 7; u++) for (int h = 20; h <= 23; h++) {
                double e = Math.pow((u - 5) / 2.4, 2) + Math.pow((h - 21.5) / 1.9, 2);
                if (e <= 1) f.set(side * u, y0 + h, v, 0);
            }
            f.set(side * 5, y0 + 19, -15, RACK); f.set(side * 5, y0 + 20, -15, FIRE);
            f.set(side * 4, y0 + 19, -16, RACK); f.set(side * 4, y0 + 20, -16, FIRE);
        }
    }

    private static final int[][] UPPER = {{3, 5, 2}, {7, 5, 2}, {11, 6, 2}, {16, 11, 3}, {20, 5, 2}, {23, 4, 2}};   // |u|, length, root
    private static final int[][] LOWER = {{9, 4, 2}, {13, 5, 2}, {18, 9, 3}, {22, 4, 2}};

    /** A fang of bone rooted at (u, y), growing up (dir 1) or down (-1) for len blocks, tapering from its root. */
    private static void fang(Draw.Frame f, int u, int y, int dir, int len, int root, int v0) {
        int out = u > 0 ? 1 : -1;
        for (int k = 0; k < len; k++) {
            double t = k / (double) len;
            int yy = y + dir * k, deep = t < 0.7 ? 1 : 0, lo = u, hi = u;
            if (root >= 3 && t < 0.45) { lo = u - 1; hi = u + 1; }
            else if (root == 2 && t < 0.5) { if (out > 0) hi = u + 1; else lo = u - 1; }
            f.box(lo, yy, v0, hi, yy, v0 + deep, Draw.of(BONE));
        }
    }

    /** The tongue: from the throat over the lower lip, across the moat as a bridge, forked on the forecourt. */
    private void tongue(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (!touches(f, -6, -50, 6, THROAT)) return;
        for (int v = -50; v <= THROAT; v++) {
            boolean tip = v < -41;
            for (int u = -6; u <= 6; u++) {
                int au = Math.abs(u);
                if (tip) {                                                       // the forked tip lies on the paving
                    double c = 1.5 + (-41 - v) * 0.35, hw = 1.6 - (-41 - v) * 0.17;
                    if (hw <= 0 || Math.abs(au - c) > hw) continue;
                    f.set(u, y0, v, WART);
                    continue;
                }
                if (au > 5) continue;
                int top = au == 5 ? 0 : 1;
                Draw.Mat m = au == 0 ? Draw.of(RED_BRICK) : Draw.of(WART);
                f.box(u, y0 - (v < -26 && v > -40 ? 0 : 1), v, u, y0 + top, v, m);
                if (au < 5) for (int h = top + 1; h <= top + 3; h++) if (v < -24) f.set(u, y0 + h, v, 0);
            }
            if (v == -41) for (int u = -4; u <= 4; u++) f.set(u, y0 + 1, v, f.stair(STAIR, 2, false));   // the step up from the forecourt
        }
        f.set(0, y0 + 1, -33, MAGMA);                                          // a vent on the bridge
    }

    // ---- the Gullet and its chambers ---------------------------------------------------------------------------------------
    /** The Gullet's floor (h of the floor block) at v: it climbs in steps from the throat to the Pit. */
    static int gfl(int v) {
        return v <= 11 ? 0 : v <= 12 ? 1 : v <= 22 ? 2 : v <= 23 ? 3 : v <= 24 ? 4 : v <= 31 ? 5 : v <= 32 ? 6 : v <= 33 ? 7 : 8;
    }

    private void gullet(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (!touches(f, -9, THROAT + 1, 9, GUL1 + 1)) return;
        Draw.Mat flesh = flesh(s.salt + 43), floor = Draw.mix(RED_BRICK, BRICK, 0.35, s.salt + 44);
        for (int v = THROAT + 1; v <= GUL1 + 1; v++) {
            int fl = gfl(v), yb = y0 + fl;
            f.box(-8, yb - 2, v, 8, yb + 14, v, flesh);
            for (int h = 1; h <= 12; h++) { int w = h <= 8 ? 6 : 6 - (h - 8); f.box(-w, yb + h, v, w, yb + h, v, Draw.AIR); }
            f.box(-6, yb, v, 6, yb, v, floor);
            f.box(-1, yb, v, 1, yb, v, Draw.of(BRICK));
            if (gfl(v - 1) < fl) for (int u = -6; u <= 6; u++) f.set(u, yb, v, f.stair(STAIR, 2, false));
            if (Math.floorMod(v, 4) == 0) {                                       // a rib of bone
                for (int h = 1; h <= 8; h++) { f.set(-6, yb + h, v, BONE); f.set(6, yb + h, v, BONE); }
                for (int h = 9; h <= 12; h++) { int w = 6 - (h - 8); f.set(-w, yb + h, v, BONE); f.set(w, yb + h, v, BONE); }
                f.box(-2, yb + 13, v, 2, yb + 13, v, Draw.of(Math.floorMod(v, 8) == 0 ? GLOW : BONE));
            }
            if (Math.floorMod(v, 8) == 2) for (int side = -1; side <= 1; side += 2) {   // fire niches
                f.set(side * 7, yb + 2, v, 0); f.set(side * 7, yb + 3, v, 0); brazier(f, side * 7, yb + 1, v);
            }
        }
        // the traps' marks: flame vents (magma), falling floors over pits, arrow slits
        for (int v = 8; v <= 10; v++) for (int u = -4; u <= 4; u += 2) f.set(u, y0 + gfl(v), v, MAGMA);
        for (int v = 34; v <= 35; v++) for (int u = -4; u <= 4; u += 2) f.set(u, y0 + gfl(v), v, MAGMA);
        pitTrap(f, s, 18, 21, true);
        pitTrap(f, s, 28, 30, false);
        for (int v = 21; v <= 33; v += 3) for (int side = -1; side <= 1; side += 2) f.set(side * 7, y0 + gfl(v) + 2, v, BLACK_T);
        f.wallSign(-6, y0 + 3, 7, Draw.EAST, "THE GULLET", "The Pit lies", "beyond. Mind", "the floor.");
        f.wallSign(-6, y0 + gfl(33) + 3, 33, Draw.EAST, "THE ABYSSAL", "PIT", "Its Gatekeeper", "never sleeps");
    }

    /** A falling floor of slabs over a pit (lava or embers below, a ladder out). */
    private void pitTrap(Draw.Frame f, Colossi.Site s, int v0, int v1, boolean lava) {
        int y0 = s.y, fl = y0 + gfl(v0);
        f.box(-5, fl - (lava ? 6 : 5), v0 - 1, 5, fl - 1, v1 + 1, dark(s.salt + 47));
        f.box(-4, fl - 4, v0, 4, fl - 1, v1, Draw.AIR);
        f.box(-4, fl - 5, v0, 4, fl - 5, v1, Draw.of(lava ? LAVA : MAGMA));
        f.box(-4, fl, v0, 4, fl, v1, Draw.of(SLAB_TOP));
        for (int y = fl - 4; y <= fl - 1; y++) f.set(0, y, v1, b(65, f.facing(Draw.NORTH)));
    }

    /** The chambers off the Gullet: the Torture Gallery and the Bone Larder, the Ember Shrine and the Kennels. */
    private void rooms(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        Draw.Mat wall = dark(s.salt + 51), floor = Draw.mix(BRICK, BLACK_T, 0.4, s.salt + 52);
        // the Torture Gallery (west, front)
        if (touches(f, -37, 11, -6, 25)) {
            int yb = y0 + 3;
            room(f, -37, 12, -9, 25, yb, yb + 9, floor, wall, wall);
            f.box(-10, yb, 14, -6, yb + 3, 16, Draw.AIR);
            f.box(-10, yb - 1, 14, -7, yb - 1, 16, Draw.of(BRICK));
            for (int k = 0; k < 4; k++) {                                     // cages along the north wall
                int u = -33 + k * 6;
                f.box(u - 1, yb, 13, u + 1, yb + 3, 15, Draw.of(IRON));
                f.box(u, yb, 14, u, yb + 2, 14, Draw.AIR);
                f.skull(u, yb, 14, k % 2 == 0 ? 0 : 2, 8);
            }
            f.box(-26, yb, 20, -18, yb, 22, Draw.of(BRICK));                  // the rack
            f.box(-26, yb + 1, 20, -18, yb + 1, 22, Draw.of(SLAB_TOP));
            f.set(-26, yb + 2, 20, FENCE); f.set(-18, yb + 2, 22, FENCE);
            for (int u = -34; u <= -12; u += 4) { f.set(u, yb + 8, 18, FENCE); f.set(u, yb + 7, 18, FENCE); f.set(u, yb + 6, 18, IRON); }   // chains
            f.box(-35, yb, 23, -33, yb, 24, Draw.of(RED_C));
            f.set(-12, yb, 23, RED_C); f.set(-14, yb, 17, RED_C);
            f.set(-30, yb - 1, 18, MAGMA); f.set(-14, yb - 1, 21, MAGMA);
            f.set(-22, yb + 8, 18, GLOW);
            f.chest(-35, yb, 18, Draw.EAST, LOOT);
            f.chest(-11, yb, 24, Draw.NORTH, RICH);
            f.spawner(-30, yb, 22, "charred_ghoul");
            f.wallSign(-6, yb + 2, 17, Draw.EAST, "", "The Torture", "Gallery", "");
            f.set(-31, yb, 18, CAULDRON); f.set(-29, yb + 4, 24, WEB); f.set(-35, yb + 7, 13, WEB);
        }
        // the Bone Larder (east, front)
        if (touches(f, 6, 11, 37, 25)) {
            int yb = y0 + 3;
            room(f, 9, 12, 37, 25, yb, yb + 9, floor, wall, wall);
            f.box(6, yb, 14, 10, yb + 3, 16, Draw.AIR);
            f.box(7, yb - 1, 14, 10, yb - 1, 16, Draw.of(BRICK));
            for (int k = 0; k < 5; k++) {                                     // bone heaps and hooks
                int u = 13 + k * 5;
                f.box(u - 1, yb, 13, u + 1, yb + 1 + (k % 2), 14, Draw.of(BONE));
                f.set(u, yb + 8, 21, FENCE); f.set(u, yb + 7, 21, FENCE); f.set(u, yb + 6, 21, WART); f.set(u, yb + 5, 21, WART);
            }
            for (int u = 13; u <= 33; u += 5) f.skull(u, yb + 2 + Math.floorMod(u, 2), 13, 0, 0);
            f.box(20, yb, 18, 26, yb, 19, Draw.of(RED_BRICK));               // the butcher's block
            f.box(20, yb + 1, 18, 26, yb + 1, 19, Draw.of(SLAB_TOP));
            f.set(30, yb, 23, CAULDRON); f.set(32, yb, 23, CAULDRON);
            f.set(22, yb + 8, 18, GLOW);
            f.set(14, yb - 1, 22, MAGMA); f.set(30, yb - 1, 16, MAGMA);
            f.chest(36, yb, 18, Draw.WEST, LOOT);
            f.chest(12, yb, 24, Draw.NORTH, LOOT);
            f.spawner(31, yb, 20, "brimstone_spider");
            f.wallSign(6, yb + 2, 17, Draw.WEST, "", "The Bone", "Larder", "");
        }
        // the Ember Shrine (west, back)
        if (touches(f, -37, 25, -6, 36)) {
            int yb = y0 + 6;
            room(f, -37, 26, -9, 35, yb, yb + 10, Draw.mix(BRICK, MAGMA, 0.15, s.salt + 54), wall, wall);
            f.box(-10, yb, 25, -6, yb + 3, 27, Draw.AIR);
            f.box(-10, yb - 1, 25, -7, yb - 1, 27, Draw.of(BRICK));
            f.box(-33, yb, 28, -31, yb + 1, 33, Draw.of(OBSIDIAN));          // the altar and the idol behind it
            f.box(-33, yb + 2, 29, -33, yb + 2, 32, Draw.of(MAGMA));
            brazier(f, -32, yb + 1, 30);
            f.box(-36, yb, 28, -35, yb + 7, 33, Draw.of(BLACK_T));
            f.set(-35, yb + 6, 29, GLOW); f.set(-35, yb + 6, 32, GLOW);
            f.box(-36, yb + 8, 27, -35, yb + 9, 34, Draw.of(OBSIDIAN));
            for (int u = -28; u <= -14; u += 7) { brazier(f, u, yb, 27); brazier(f, u, yb, 34); }
            f.chest(-31, yb, 27, Draw.SOUTH, RICH);
            f.chest(-31, yb, 34, Draw.NORTH, LOOT);
            f.spawner(-20, yb, 31, "pyre_warden");
            f.wallSign(-6, yb + 2, 28, Draw.EAST, "", "The Ember", "Shrine", "");
        }
        // the Kennels (east, back)
        if (touches(f, 6, 25, 37, 36)) {
            int yb = y0 + 6;
            room(f, 9, 26, 37, 35, yb, yb + 8, Draw.mix(SOUL, BRICK, 0.4, s.salt + 55), wall, wall);
            f.box(6, yb, 25, 10, yb + 3, 27, Draw.AIR);
            f.box(7, yb - 1, 25, 10, yb - 1, 27, Draw.of(BRICK));
            for (int k = 0; k < 4; k++) {                                     // cells barred with iron
                int u = 14 + k * 6;
                f.box(u - 2, yb, 31, u + 2, yb + 3, 31, Draw.of(IRON));
                f.box(u - 3, yb, 32, u - 3, yb + 3, 34, Draw.of(BRICK));
                f.set(u, yb, 33, BONE);
            }
            f.set(36, yb, 28, BONE); f.set(35, yb, 27, BONE);
            f.set(23, yb + 7, 29, GLOW);
            f.chest(36, yb, 29, Draw.WEST, LOOT);
            f.spawner(30, yb, 28, "hellhound");
            f.wallSign(6, yb + 2, 28, Draw.WEST, "", "The Kennels", "", "");
        }
    }

    // ---- the Abyssal Pit --------------------------------------------------------------------------------------------------
    private static boolean rib(double ang, double r) {
        double k = ang / (Math.PI / 6), off = Math.abs(k - Math.rint(k)) * (Math.PI / 6) * Math.max(r, 1);
        return off < 0.75;
    }

    private void pit(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + PIT_H;
        if (!touches(f, -PIT_R - 2, PIT_V - PIT_R - 2, PIT_R + 2, PIT_V + PIT_R + 2)) return;
        int[] c = clip(f);
        int ua = Math.max(c[0], -PIT_R - 1), ub = Math.min(c[2], PIT_R + 1), va = Math.max(c[1], PIT_V - PIT_R - 1), vb = Math.min(c[3], PIT_V + PIT_R + 1);
        Draw.Mat lining = Draw.mix(Draw.mix(OBSIDIAN, MAGMA, 0.3, s.salt + 61), Draw.of(BLACK_T), 0.3, s.salt + 62);
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) {
            double r = Math.hypot(u, v - PIT_V);
            if (r > PIT_R + 1.2) continue;
            int x = f.x(u, v), z = f.z(u, v);
            double ang = Math.atan2(v - PIT_V, u);
            boolean rb = rib(ang, r);
            int ceil = fl + 17 + (int) Math.round(11 * Math.sqrt(Math.max(0, 1 - (r / PIT_R) * (r / PIT_R))));
            if (r > PIT_R) {                                               // the wall: ribs of bone rise from the floor
                if (rb) for (int y = fl + 1; y <= ceil; y++) f.d.set(x, y, z, BONE);
                continue;
            }
            for (int y = fl + 1; y <= ceil; y++) f.d.set(x, y, z, 0);
            double q = Draw.rnd(x, 7, z, s.salt + 63);
            f.d.set(x, ceil + 1, z, rb || r < 2.2 ? BONE : q < 0.025 ? GLOW : q < 0.5 ? BRICK : BLACK_T);
            if (r <= 7) {                                                  // the chasm: lava far below
                for (int y = y0 - 4; y <= fl; y++) f.d.set(x, y, z, 0);
                f.d.set(x, y0 - 5, z, LAVA); f.d.set(x, y0 - 6, z, LAVA); f.d.set(x, y0 - 7, z, OBSIDIAN);
            } else if (r <= 8.4) {                                         // its lining, the kerb and the railing
                for (int y = y0 - 7; y <= fl + 1; y++) f.d.set(x, y, z, lining.at(x, y, z));
                f.d.set(x, fl + 1, z, OBSIDIAN);
                f.d.set(x, fl + 2, z, FENCE);
            } else {
                boolean spoke = rib(ang + Math.PI / 12, r);
                double crack = Math.abs(Draw.noise(x, z, 6, s.salt + 64));
                f.d.set(x, fl, z, spoke ? RED_BRICK : crack < 0.05 ? MAGMA : r < 10 ? OBSIDIAN : Draw.rnd(x, 0, z, s.salt + 65) < 0.5 ? BLACK_T : BRICK);
            }
        }
        // braziers in niches between the ribs, chains and cages hanging from the dome
        for (int k = 0; k < 12; k++) {
            double a = (k + 0.5) * Math.PI / 6;
            int u = (int) Math.round(Math.cos(a) * (PIT_R + 1)), v = PIT_V + (int) Math.round(Math.sin(a) * (PIT_R + 1));
            if (Math.abs(u) <= 4 && (v < PIT_V - 20 || v > PIT_V + 20)) continue;     // the door and the Gate
            f.box(u, fl + 1, v, u, fl + 3, v, Draw.AIR);
            brazier(f, u, fl, v);
            int cu = (int) Math.round(Math.cos(a) * 17), cv = PIT_V + (int) Math.round(Math.sin(a) * 17);
            int top = fl + 17 + (int) Math.round(11 * Math.sqrt(1 - (17.0 / PIT_R) * (17.0 / PIT_R)));
            for (int y = fl + 24; y <= top; y++) f.set(cu, y, cv, FENCE);
            if (k % 3 == 1) { f.box(cu - 1, fl + 21, cv - 1, cu + 1, fl + 23, cv + 1, Draw.of(IRON)); f.set(cu, fl + 22, cv, 0); f.skull(cu, fl + 21, cv, 0, k); }
        }
        // the door from the Gullet under an arch of bone
        f.box(-6, fl + 1, PIT_V - PIT_R - 1, 6, fl + 12, PIT_V - PIT_R + 1, Draw.AIR);
        for (int h = 1; h <= 13; h++) { int w = h <= 9 ? 7 : 7 - (h - 9); f.set(-w, fl + h, PIT_V - PIT_R, BONE); f.set(w, fl + h, PIT_V - PIT_R, BONE); }
        f.box(-3, fl + 13, PIT_V - PIT_R, 3, fl + 13, PIT_V - PIT_R, Draw.of(BONE));
        f.point(0, fl + 1, PIT_V + 15, "lord:abyssal_gatekeeper");
        f.chest(-PIT_R + 4, fl + 1, PIT_V - 9, Draw.EAST, RICH);
        f.chest(PIT_R - 4, fl + 1, PIT_V - 9, Draw.WEST, RICH);
    }

    /** The Gate of the vault in the Pit's far wall, sealed until the Gatekeeper falls; the hoard behind it. */
    private void vault(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + PIT_H;
        if (!touches(f, -12, GATE_V - 3, 12, VAULT_V1 + 1)) return;
        f.box(-1, fl + 1, GATE_V - 2, 1, fl + 4, GATE_V + 1, Draw.AIR);       // the alcove
        f.box(-1, fl, GATE_V - 2, 1, fl, GATE_V + 1, Draw.of(OBSIDIAN));
        f.box(-1, fl + 1, GATE_V + 2, 1, fl + 4, GATE_V + 2, Draw.of(OBSIDIAN)); // the seal
        for (int side = -1; side <= 1; side += 2) {                              // its frame: pillars, a lintel, a skull of bone
            f.box(side * 2, fl + 1, GATE_V - 2, side * 3, fl + 7, GATE_V - 1, Draw.of(OBSIDIAN));
            brazier(f, side * 5, fl, GATE_V - 2);
        }
        f.box(-3, fl + 6, GATE_V - 2, 3, fl + 7, GATE_V - 1, Draw.of(OBSIDIAN));
        f.box(-3, fl + 8, GATE_V - 1, 3, fl + 12, GATE_V - 1, Draw.of(BONE));
        f.set(-1, fl + 10, GATE_V - 2, 0); f.set(1, fl + 10, GATE_V - 2, 0);
        f.set(-1, fl + 10, GATE_V - 1, GLOW); f.set(1, fl + 10, GATE_V - 1, GLOW);
        f.box(-2, fl + 8, GATE_V - 2, 2, fl + 8, GATE_V - 2, Draw.of(BONE));
        f.wallSign(-2, fl + 3, GATE_V - 2, Draw.NORTH, "Sealed until", "the Gatekeeper", "falls", "");
        // the hoard
        int yb = fl + 1;
        room(f, -9, VAULT_V0, 9, VAULT_V1, yb, yb + 9, Draw.mix(YELLOW_T, BRICK, 0.4, s.salt + 71), Draw.of(OBSIDIAN), Draw.of(BLACK_T));
        f.box(-1, yb, VAULT_V0, 1, yb + 3, VAULT_V0, Draw.AIR);
        for (int u = -5; u <= 5; u += 5) f.chest(u, yb, VAULT_V1 - 1, Draw.NORTH, VAULT);
        f.chest(-8, yb, VAULT_V0 + 3, Draw.EAST, RICH);
        f.chest(8, yb, VAULT_V0 + 3, Draw.WEST, RICH);
        f.box(-1, yb, VAULT_V0 + 5, 1, yb, VAULT_V0 + 6, Draw.of(OBSIDIAN));
        f.set(0, yb + 1, VAULT_V0 + 5, MAGMA); f.set(0, yb + 1, VAULT_V0 + 6, MAGMA);
        for (int u = -7; u <= 7; u += 14) { f.box(u, yb, VAULT_V1 - 3, u, yb + 1, VAULT_V1 - 2, Draw.of(YELLOW_T)); f.skull(u, yb + 2, VAULT_V1 - 3, 0, 8); }
        f.set(-4, yb + 8, VAULT_V0 + 5, GLOW); f.set(4, yb + 8, VAULT_V0 + 5, GLOW);
    }

    // ---- inside the skull: the Spine Stair, the Eye Hall, the Brow Gallery, the horns' stairs ------------------------------
    /** A winding stair of stairs-blocks around (cu, cv) between radii ri and ro, from yA, rising `rise` a turn up to yB. */
    private static void helix(Draw.Frame f, double cu, double cv, double ri, double ro, int yA, int yB, double rise, double a0, Draw.Mat under) {
        int u0 = (int) Math.floor(cu - ro), u1 = (int) Math.ceil(cu + ro), v0 = (int) Math.floor(cv - ro), v1 = (int) Math.ceil(cv + ro);
        if (!touches(f, u0, v0, u1, v1)) return;
        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
            double du = u - cu, dv = v - cv, r = Math.sqrt(du * du + dv * dv);
            if (r < ri || r > ro) continue;
            double ang = Math.atan2(dv, du), a = ((ang - a0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            double tu = -Math.sin(ang), tv = Math.cos(ang);
            int dir = Math.abs(tu) > Math.abs(tv) ? (tu > 0 ? 0 : 1) : (tv > 0 ? 2 : 3);
            for (double y = yA + a / (2 * Math.PI) * rise; y <= yB + 0.001; y += rise) {
                int yi = (int) Math.floor(y);
                f.set(u, yi, v, f.stair(STAIR, dir, false));
                if (yi - 1 >= yA - 1) f.set(u, yi - 1, v, under);
            }
        }
    }

    /** The angle at which a helix stands at height y (its step block). */
    private static double helixAngle(int yA, int y, double rise, double a0) { return a0 + ((y - yA) / rise) % 1 * 2 * Math.PI; }

    private void spine(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        if (!touches(f, -30, -4, -12, 14)) return;
        double cu = SPINE_U, cv = SPINE_V;
        int top = y0 + BROW_FL;
        f.cyl(cu, cv, 5.6, y0, top - 1, dark(s.salt + 81));
        f.cyl(cu, cv, 4.5, y0 + 1, top, Draw.AIR);
        f.disk(cu, cv, 4.5, y0, Draw.of(BRICK));
        f.cyl(cu, cv, 0.9, y0 + 1, top + 6, Draw.of(BONE));
        helix(f, cu, cv, 1.3, 4.5, y0 + 1, top, SPINE_RISE, SPINE_A0, Draw.of(BRICK));
        for (int y = y0 + 6; y < top; y += 7) { f.set((int) cu, y, (int) cv - 5, GLOW); f.set((int) cu, y + 3, (int) cv + 5, GLOW); }
        // from the mouth (east, on the floor)
        f.box(-17, y0 + 1, 4, -10, y0 + 3, 6, Draw.AIR);
        f.box(-17, y0, 4, -10, y0, 6, Draw.of(BRICK));
        f.wallSign(-12, y0 + 3, 3, Draw.SOUTH, "The Spine Stair", "up to the Eyes", "and the Horns", "");
        // to the Eye Hall (north, at its floor)
        f.box(-23, y0 + EYE_FL + 1, -1, -21, y0 + EYE_FL + 3, 0, Draw.AIR);
        f.box(-23, y0 + EYE_FL, -1, -21, y0 + EYE_FL, 0, Draw.of(BRICK));
        // the railing round its head in the Brow Gallery, open where the stair arrives
        double arrive = helixAngle(y0 + 1, top, SPINE_RISE, SPINE_A0);
        for (int k = 0; k < 40; k++) {
            double a = k * Math.PI / 20, off = Math.abs(((a - arrive) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI);
            if (off < 0.9) continue;
            f.set((int) Math.round(cu + Math.cos(a) * 5), top + 1, (int) Math.round(cv + Math.sin(a) * 5), FENCE);
        }
    }

    private void eyeHall(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, yb = y0 + EYE_FL;
        if (!touches(f, -32, -10, 32, 0)) return;
        f.box(-30, yb + 1, -10, 30, yb + 9, -1, Draw.AIR);
        f.box(-30, yb, -10, 30, yb, -1, Draw.mix(BRICK, BLACK_T, 0.4, s.salt + 83));
        f.box(-3, yb + 1, -5, 3, yb + 1, -3, Draw.of(OBSIDIAN));            // the Watcher's altar
        f.box(-1, yb + 2, -4, 1, yb + 2, -4, Draw.of(MAGMA));
        brazier(f, 0, yb + 2, -4);
        for (int u = -10; u <= 10; u += 20) { f.box(u, yb + 1, -10, u, yb + 8, -10, Draw.of(BONE)); f.set(u, yb + 8, -9, GLOW); }
        f.chest(-29, yb + 1, -2, Draw.EAST, RICH);
        f.chest(29, yb + 1, -2, Draw.WEST, LOOT);
        f.wallSign(0, yb + 4, -1, Draw.NORTH, "THE EYE HALL", "Its eyes watch", "the forecourt,", "and burn it");
    }

    private void browGallery(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, yb = y0 + BROW_FL;
        if (!touches(f, -28, -1, 28, 13)) return;
        f.box(-26, yb + 1, 0, 26, yb + 6, 12, Draw.AIR);
        f.box(-26, yb, 0, 26, yb, 12, Draw.mix(RED_BRICK, BRICK, 0.4, s.salt + 85));
        for (int u = -18; u <= 18; u += 9) for (int v = 2; v <= 10; v += 8) { f.box(u, yb + 1, v, u, yb + 5, v, Draw.of(OBSIDIAN)); f.set(u, yb + 6, v, GLOW); }
        for (int side = -1; side <= 1; side += 2) brazier(f, side * 23, yb, 10);
        f.chest(4, yb + 1, 11, Draw.NORTH, RICH);
        f.wallSign(2, yb + 3, 12, Draw.NORTH, "THE BROW", "A stair climbs", "in each horn", "to its lookout");
    }

    /** Inside each horn a stair climbs from the Brow Gallery out along the horn to a lookout on its front. */
    private void hornTunnels(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, n = p.tunY.length;
        for (int side = -1; side <= 1; side += 2) {
            if (!touches(f, side * TUN0, -24, side * (TUN1 + 8), 8)) continue;
            for (int k = 0; k < n; k++) {
                int u = side * (TUN0 + k), vc = p.tunV[k], fy = y0 + p.tunY[k];
                f.box(u, fy + 1, vc - 1, u, fy + 3, vc + 1, Draw.AIR);
                boolean step = k > 0 && p.tunY[k] > p.tunY[k - 1];
                for (int dv = -1; dv <= 1; dv++) f.set(u, fy, vc + dv, step ? f.stair(STAIR, side > 0 ? 0 : 1, false) : BRICK);
                if (k % 6 == 3) f.set(u, fy + 4, vc, GLOW);
            }
            // the lookout: a balcony on the horn's front, high over the face and the forecourt
            int ue = side * TUN1, vc = p.tunV[n - 1], fy = y0 + p.tunY[n - 1];
            int ua = side * (TUN1 - 6), ub = side * (TUN1 + 6), v0 = vc - 13, v1 = vc - 5;
            f.box(ue - 1, fy + 1, vc - 5, ue + 1, fy + 3, vc - 1, Draw.AIR);
            f.box(ue - 1, fy, vc - 5, ue + 1, fy, vc - 1, Draw.of(BRICK));
            f.box(ua, fy, v0, ub, fy, v1, Draw.mix(BRICK, RED_BRICK, 0.3, s.salt + 87));
            f.box(ua, fy + 1, v0, ub, fy + 4, v1, Draw.AIR);
            for (int u = Math.min(ua, ub); u <= Math.max(ua, ub); u++) f.set(u, fy + 1, v0, FENCE);
            for (int v = v0; v <= v1; v++) { f.set(ua, fy + 1, v, FENCE); f.set(ub, fy + 1, v, FENCE); }
            for (int du : new int[]{-5, 0, 5}) f.line(ue + side * du, fy - 1, v0 + 1, ue + side * du, fy - 7, vc - 3, 0.6, Draw.of(BRICK));   // struts
            brazier(f, ue - side * 4, fy, v0 + 1);
            brazier(f, ue + side * 4, fy, v0 + 1);
            f.chest(ue + side * 4, fy + 1, v1, Draw.NORTH, RICH);
            f.wallSign(ue - side * 3, fy + 2, v1, Draw.NORTH, "THE LOOKOUT", "of the Horn of", side > 0 ? "Wrath" : "Ruin", "");
        }
    }

    /** Spikes of obsidian down the ridge of the skull. */
    private void spines(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -8, 30, 8, VMAX)) return;
        for (int v = 40; v <= 104; v += 8) {
            int top = p.ridge[v - VMIN];
            if (top < 6) continue;
            int hgt = 11 - Math.abs(v - 66) / 7;
            f.d.cone(f.xd(0, v), f.zd(0, v), 2.8, 0.3, s.y + top - 2, s.y + top + hgt, Draw.mix(OBSIDIAN, BLACK_T, 0.3, s.salt + 89));
        }
    }

    // ---- the cavern floor's works ----------------------------------------------------------------------------------------------
    /** The forecourt's paving, the ledge under the chin and the moat (drawn before the head). */
    private void grounds(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        int[] c = clip(f);
        int ua = Math.max(c[0], -58), ub = Math.min(c[2], 58), va = Math.max(c[1], -104), vb = Math.min(c[3], -20);
        Draw.Mat under = dark(s.salt + 91);
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) {
            int au = Math.abs(u);
            boolean moat = v >= -38 && v <= -28 && (au <= 48 || Math.hypot(au - 48, v + 33) <= 5.5);
            boolean court = v <= -39 && au <= 46 && v >= -104;
            boolean ledge = v >= -27 && au <= 52;
            int x = f.x(u, v), z = f.z(u, v);
            for (int y = y0 - 4; y < y0; y++) f.d.set(x, y, z, under.at(x, y, z));
            for (int y = y0 + 1; y <= y0 + 4; y++) f.d.set(x, y, z, 0);
            if (moat) {
                f.d.set(x, y0 - 4, z, BRICK);
                f.d.set(x, y0 - 3, z, LAVA); f.d.set(x, y0 - 2, z, LAVA); f.d.set(x, y0 - 1, z, LAVA);
                f.d.set(x, y0, z, 0);
            } else if (court || ledge) f.d.set(x, y0, z, paving(u, v, x, z, s.salt));
            else f.d.set(x, y0, z, ground(s, x, y0, z));                       // the margin, levelled round the works
        }
        // the railing along the moat's outer edge, open for the tongue
        if (touches(f, -53, -40, 53, -38)) for (int u = -50; u <= 50; u++) if (Math.abs(u) > 6) f.set(u, y0 + 1, -39, FENCE);
    }

    private static int paving(int u, int v, int x, int z, int salt) {
        int au = Math.abs(u);
        if (au <= 5) return au == 5 ? BRICK : (au == 0 && Math.floorMod(v, 4) == 0) ? MAGMA : RED_BRICK;
        if (Math.abs(Draw.noise(x, z, 9, salt + 93)) < 0.04) return MAGMA;
        return ((Math.floorDiv(u, 3) + Math.floorDiv(v, 3)) & 1) == 0 ? BLACK_T : BRICK;
    }

    /** The outer works: spikes and obelisks on the forecourt, the avenue and its gate, the bone fields, the shrines. */
    private void outer(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        Draw.Mat spike = Draw.mix(OBSIDIAN, BLACK_T, 0.35, s.salt + 95);
        for (int[] q : p.spikes) {
            if (!touches(f, q[0] - 3, q[1] - 2, q[0] + 3, q[1] + 2)) continue;
            f.d.cone(f.xd(q[0], q[1]), f.zd(q[0], q[1]), 1.7, 0.2, y0 + 1, y0 + q[2], spike);
            f.box(q[0] - 1, y0 + 1, q[1] - 1, q[0] + 1, y0 + 1, q[1] + 1, Draw.of(BRICK));
            f.skull(q[0], y0 + q[2] + 1, q[1], Math.floorMod(q[1], 3) == 0 ? 2 : 0, 8);
            f.set(q[0] + (q[0] > 0 ? -2 : 2), y0 + 1, q[1], RACK); f.set(q[0] + (q[0] > 0 ? -2 : 2), y0 + 2, q[1], FIRE);
        }
        for (int cu = -1; cu <= 1; cu += 2) for (int v : new int[]{-46, -96}) obelisk(f, s, cu * 39, v);
        // the outer gate and the Avenue of Spikes out to the cavern's wall
        if (touches(f, -12, -106, 12, -98)) {
            for (int side = -1; side <= 1; side += 2) {
                f.box(side * 6, y0 + 1, -103, side * 9, y0 + 18, -100, Draw.of(BRICK));
                f.box(side * 7, y0 + 19, -102, side * 8, y0 + 22, -101, Draw.of(OBSIDIAN));
                f.set(side * 7, y0 + 23, -102, RACK); f.set(side * 7, y0 + 24, -102, FIRE);
            }
            f.box(-9, y0 + 14, -103, 9, y0 + 17, -100, Draw.of(BRICK));
            f.box(-5, y0 + 13, -102, 5, y0 + 13, -101, Draw.of(BONE));
            for (int u = -4; u <= 4; u += 2) f.set(u, y0 + 12, -102, BONE);
            f.wallSign(-7, y0 + 3, -104, Draw.NORTH, "THE MAW OF", "THE ABYSS", "Enter, and be", "swallowed");
        }
        for (int k = 0; k < p.avenueY.length; k++) {
            int v = -101 - k, yr = p.avenueY[k];
            if (!touches(f, -6, v, 6, v)) continue;
            int x0 = f.x(0, v), z0 = f.z(0, v);
            if (s.f(x0, z0) > 0.9) continue;
            for (int u = -3; u <= 3; u++) {
                f.box(u, yr - 3, v, u, yr - 1, v, Draw.of(BRICK));
                f.set(u, yr, v, Math.abs(u) == 3 ? BRICK : u == 0 && Math.floorMod(v, 4) == 0 ? MAGMA : RED_BRICK);
                f.box(u, yr + 1, v, u, yr + 3, v, Draw.AIR);
            }
            if (Math.floorMod(v, 6) == 0) for (int side = -1; side <= 1; side += 2) {     // impaling stakes, fires between
                f.box(side * 5, yr - 2, v, side * 5, yr, v, Draw.of(BRICK));
                if (Math.floorMod(v, 12) == 0) {
                    f.box(side * 5, yr + 1, v, side * 5, yr + 4, v, Draw.of(FENCE));
                    f.skull(side * 5, yr + 5, v, Math.floorMod(v, 24) == 0 ? 2 : 0, side > 0 ? 4 : 12);
                } else brazier(f, side * 5, yr + 1, v);
            }
        }
        // the bone fields
        Draw.Mat bones = (x, y, z) -> { double q = Draw.rnd(x, y, z, s.salt + 97); return q < 0.55 ? BONE : q < 0.8 ? SOUL : BROWN_T; };
        for (int[] q : p.piles) {
            if (!touches(f, q[0] - q[2], q[1] - q[2], q[0] + q[2], q[1] + q[2])) continue;
            ell(f, q[0], q[4], q[1], q[2], q[3] + 0.5, q[2] * 0.8, bones);
            for (int k = 0; k < q[2]; k++) {                                       // bones sticking out of the heap
                int du = (int) Math.round(Math.cos(k * 2.4) * q[2] * 0.5), dv = (int) Math.round(Math.sin(k * 2.4) * q[2] * 0.4);
                f.box(q[0] + du, q[4] + 1, q[1] + dv, q[0] + du, q[4] + q[3] + 1 + (k % 2), q[1] + dv, Draw.of(BONE));
            }
            if (q[2] >= 3) f.skull(q[0], q[4] + q[3] + 1, q[1], Math.floorMod(q[0], 2) == 0 ? 0 : 2, Math.floorMod(q[1], 16));
        }
        for (int[] c : p.cages) ribcage(f, c);
        for (int i = 0; i < p.shrines.size(); i++) shrine(f, s, p.shrines.get(i), i);
    }

    private void obelisk(Draw.Frame f, Colossi.Site s, int u, int v) {
        if (!touches(f, u - 4, v - 4, u + 4, v + 4)) return;
        int y0 = s.y;
        f.box(u - 3, y0 + 1, v - 3, u + 3, y0 + 2, v + 3, Draw.of(BRICK));
        f.box(u - 2, y0 + 3, v - 2, u + 2, y0 + 24, v + 2, Draw.bands(Draw.of(RED_BRICK), Draw.of(BRICK), y0 + 3, 5));
        f.box(u - 1, y0 + 25, v - 1, u + 1, y0 + 28, v + 1, Draw.of(OBSIDIAN));
        f.set(u, y0 + 29, v, GLOW);
        for (int y = y0 + 9; y <= y0 + 21; y += 6) { f.set(u, y, v - 2, MAGMA); f.set(u, y, v + 2, MAGMA); f.set(u - 2, y, v, MAGMA); f.set(u + 2, y, v, MAGMA); }
    }

    private void ribcage(Draw.Frame f, int[] c) {
        int u = c[0], v = c[1], len = c[2], axis = c[3], yb = c[4];
        int u1 = axis == 0 ? u + len : u, v1 = axis == 0 ? v : v + len;
        if (!touches(f, Math.min(u, u1) - 7, Math.min(v, v1) - 7, Math.max(u, u1) + 7, Math.max(v, v1) + 7)) return;
        Draw.Mat bone = Draw.of(BONE);
        f.line(u, yb + 1, v, u1, yb + 2, v1, 1.0, bone);                          // the spine
        for (int k = 3; k < len - 2; k += 3) {                                     // the ribs
            int pu = axis == 0 ? u + k : u, pv = axis == 0 ? v : v + k;
            double ou = axis == 0 ? 0 : 1, ov = axis == 0 ? 1 : 0;
            int ht = 6 + (int) Math.round(3 * Math.sin(Math.PI * k / len));
            for (int side = -1; side <= 1; side += 2) {
                f.line(pu, yb + 2, pv, pu + ou * side * 4, yb + ht, pv + ov * side * 4, 0.5, bone);
                f.line(pu + ou * side * 4, yb + ht, pv + ov * side * 4, pu + ou * side * 6, yb + 1, pv + ov * side * 6, 0.5, bone);
            }
        }
        f.box(u1 - 2, yb + 1, v1 - 2, u1 + 2, yb + 4, v1 + 2, bone);              // the skull
        if (axis == 0) { f.set(u1 + 2, yb + 3, v1 - 1, 0); f.set(u1 + 2, yb + 3, v1 + 1, 0); }
        else { f.set(u1 - 1, yb + 3, v1 + 2, 0); f.set(u1 + 1, yb + 3, v1 + 2, 0); }
    }

    /** A shrine of offering: a dais under a stepped roof on four pillars, an altar of fire, the offerings. */
    private void shrine(Draw.Frame f, Colossi.Site s, int[] q, int i) {
        int u = q[0], v = q[1], yb = q[2];
        if (!touches(f, u - 9, v - 9, u + 9, v + 9)) return;
        f.box(u - 8, yb - 3, v - 8, u + 8, yb, v + 8, Draw.of(BRICK));
        f.box(u - 7, yb, v - 7, u + 7, yb, v + 7, Draw.mix(RED_BRICK, BRICK, 0.3, s.salt + 99));
        f.box(u - 8, yb + 1, v - 8, u + 8, yb + 12, v + 8, Draw.AIR);
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
            f.box(u + cu * 6, yb + 1, v + cv * 6, u + cu * 6, yb + 8, v + cv * 6, Draw.of(RED_BRICK));
            f.set(u + cu * 6, yb + 6, v + cv * 5, MAGMA);
            f.skull(u + cu * 7, yb + 1, v + cv * 7, (cu + cv) == 0 ? 2 : 0, (cu > 0 ? 4 : 12));
        }
        f.box(u - 7, yb + 9, v - 7, u + 7, yb + 9, v + 7, Draw.of(BRICK));
        f.box(u - 5, yb + 10, v - 5, u + 5, yb + 10, v + 5, Draw.of(BLACK_T));
        f.box(u - 3, yb + 11, v - 3, u + 3, yb + 11, v + 3, Draw.of(BRICK));
        f.box(u - 1, yb + 12, v - 1, u + 1, yb + 13, v + 1, Draw.of(OBSIDIAN));
        f.set(u, yb + 14, v, RACK); f.set(u, yb + 15, v, FIRE);
        f.box(u - 1, yb + 1, v - 1, u + 1, yb + 1, v + 1, Draw.of(OBSIDIAN));     // the altar
        brazier(f, u, yb + 1, v);
        f.skull(u - 1, yb + 2, v - 1, 0, 2); f.skull(u + 1, yb + 2, v + 1, 2, 10);
        int dv = v < 10 ? 1 : -1;
        f.chest(u - 3, yb + 1, v - dv * 4, dv > 0 ? Draw.SOUTH : Draw.NORTH, i == 1 ? RICH : LOOT);
        String[] names = {"of Hunger", "of Ashes", "of the Fang", "of Embers"};
        f.wallSign(u, yb + 1, v - dv * 2, dv > 0 ? Draw.NORTH : Draw.SOUTH, "The Shrine", names[i], "Feed the Maw", "");
        f.set(u, yb + 1, v - dv, OBSIDIAN);
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y;
        // the eyes and the inner obelisks spit fire over the forecourt
        for (int side = -1; side <= 1; side += 2) {
            out.add(Ordeals.cannon(fr.box(-44, y0 - 2, -62, 44, y0 + 16, -20), fr.at(side * EYE_U, y0 + 32, -14)));
            out.add(Ordeals.cannon(fr.box(side * 4, y0 - 2, -80, side * 46, y0 + 14, -36), fr.at(side * 39, y0 + 31, -46)));
        }
        // the tongue's vent, the Gullet's vents, falling floors and arrow slits
        out.add(Ordeals.flames(fr.box(-4, y0 + 2, -35, 4, y0 + 4, -31), 70));
        out.add(Ordeals.flames(fr.box(-6, y0 + 1, 8, 6, y0 + 3, 10), 60));
        out.add(Ordeals.flames(fr.box(-6, y0 + 9, 34, 6, y0 + 11, 35), 80));
        out.add(Ordeals.collapse(fr.box(-4, y0 + gfl(18), 18, 4, y0 + gfl(18), 21), SLAB_TOP));
        out.add(Ordeals.collapse(fr.box(-4, y0 + gfl(28), 28, 4, y0 + gfl(28), 30), SLAB_TOP));
        out.add(Ordeals.arrows(fr.box(-6, y0 + 3, 21, 6, y0 + 8, 33), fr.box(-7, y0 + 4, 19, 7, y0 + 7, 35)));
        // the Torture Gallery's ceiling, the Larder's rot
        out.add(Ordeals.rubble(fr.box(-34, y0 + 3, 16, -12, y0 + 7, 22), GREY_T));
        out.add(Ordeals.gas(fr.box(10, y0 + 3, 14, 34, y0 + 5, 23)));
        // the vault: its Gate opens when the Gatekeeper falls
        out.add(Ordeals.bossSeal("maw_vault", fr.box(-1, y0 + PIT_H + 1, GATE_V + 2, 1, y0 + PIT_H + 4, GATE_V + 2), OBSIDIAN, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern ---------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61), d = s.dist(x, z);
        if (d < 150 && Math.abs(Draw.noise(x, z, 14, s.salt + 62)) < 0.025) return MAGMA;
        if (q < 0.008) return BONE;
        double ash = Draw.noise(x, z, 30, s.salt + 63);
        return ash > 0.35 ? (q < 0.5 ? GREY_T : BLACK_T) : q < 0.3 ? SOUL : q < 0.38 ? BLACK_T : RACK;
    }

    @Override Draw.Mat under() { return Draw.mix(RACK, BLACK_T, 0.3, 7713); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < 150; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.004);
        MegaDesign.spikes(d, x, z, floor, ceil, b(49), s.salt + 53, 0.0015);
        if (lake) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.0015) { d.set(x, floor + 1, z, BONE); d.set(x, floor + 2, z, BONE); }
        else if (q < 0.003) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
    }
}
