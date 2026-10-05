package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Angles of Tindalos (epoch 6): a fortress made only of sharp angles, where the Hounds come through. An eight-pointed
 * star fort (a square core, four kite bastions with acute salients, four ravelins) behind battered walls faced like broken
 * mirrors, black and white. Through the front ravelin's point runs the zig-zag gallery to the Alpha's arena, a stepped
 * hexagram sunk in the middle of the fort under leaning obsidian fangs; diagonal walls divide four triangular courts:
 * the Court of Mirrors (shards of black glass), the Kennels (and the sunken pack pit in the left ravelin), the Maze of
 * Acute Corners (a sheared labyrinth filling the right court and ravelin), and the Court of Hours with its dial. Behind
 * rises the Needle, a triangular spire eighty blocks high over the hall of the hound-priests and their crypt; four prism
 * towers stand in the bastions, joined to it at the height of their lookouts by knife-edge bridges. Outside, shards.
 */
final class GreatTindalos extends GreatDesign {
    private static final int MAS = -1, ELD = -2, RUB = -3, GLY = -4, FACET = -6, CHECK = -7;
    private static final int STAIRS = 109, CONCRETE = 251, TINT = 95, QUARTZ_B = 155;
    private static final double HEX = 17;                                            // the arena hexagram's circumradius
    private static final int MI0 = 4, MI1 = 11, MJ0 = -4, MJ1 = 19;                  // the maze's cell grid
    private static final int NX = 0, NZ = 33, NR = 11, NTOP = 52, NTIP = 86;         // the Needle
    private static final int TR = 8, TTOP = 28, TTIP = 47;                            // the bastion towers
    private static final int[][] GALLERY = {{0, -46}, {0, -39}, {-2, -36}, {3, -33}, {-5, -29}, {6, -24}, {0, -17}};

    // ------------------------------------------------------------------------------------------------ the star fort's plan

    /** The fort's convex parts: each edge {nx, nz, c} with n the outward normal, inside where n.p <= c for every edge. */
    private static final double[][][] FORT;
    static {
        List<double[][]> polys = new ArrayList<>();
        polys.add(edges(new double[][] {{-32, -32}, {32, -32}, {32, 32}, {-32, 32}}));
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) polys.add(edges(new double[][] {{44 * su, 44 * sv}, {42 * su, 24 * sv}, {24 * su, 24 * sv}, {24 * su, 42 * sv}}));
        polys.add(edges(new double[][] {{0, -45}, {13, -26}, {-13, -26}}));
        polys.add(edges(new double[][] {{0, 45}, {13, 26}, {-13, 26}}));
        polys.add(edges(new double[][] {{-45, 0}, {-26, 13}, {-26, -13}}));
        polys.add(edges(new double[][] {{45, 0}, {26, 13}, {26, -13}}));
        FORT = polys.toArray(new double[0][][]);
    }

    private static double[][] edges(double[][] p) {
        double cx = 0, cz = 0;
        for (double[] q : p) { cx += q[0] / p.length; cz += q[1] / p.length; }
        double[][] e = new double[p.length][];
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            double nx = b[1] - a[1], nz = a[0] - b[0], len = Math.sqrt(nx * nx + nz * nz);
            nx /= len; nz /= len;
            double c = nx * a[0] + nz * a[1];
            if (nx * cx + nz * cz - c > 0) { nx = -nx; nz = -nz; c = -c; }
            e[i] = new double[] {nx, nz, c};
        }
        return e;
    }

    /** How far inside the fort a column lies (negative outside): the walls are where 0 <= d < 2, the glacis down to -3. */
    static double fortD(double u, double v) {
        double best = -1e9;
        for (double[][] poly : FORT) {
            double m = -1e9;
            for (double[] e : poly) m = Math.max(m, e[0] * u + e[1] * v - e[2]);
            best = Math.max(best, -m);
        }
        return best;
    }

    /** The largest of a point's projections on an equilateral triangle's three edge normals (vertex toward angle t). */
    private static double tri(double x, double z, double ct, double st) {
        double a = -(x * ct + z * st);
        double b = -(x * (-0.5 * ct - 0.8660254 * st) + z * (-0.5 * st + 0.8660254 * ct));
        double c = -(x * (-0.5 * ct + 0.8660254 * st) + z * (-0.5 * st - 0.8660254 * ct));
        return Math.max(a, Math.max(b, c));
    }

    /** The arena: how far inside the hexagram a column lies (its steps down to the floor). */
    private static double hexE(double u, double v) { return HEX / 2 - Math.min(tri(u, v, 0, -1), tri(u, v, 0, 1)); }

    /** The arena's floor level at a column (0 at the rim, -4 in the middle), or 1 outside it. */
    private static int arenaLevel(int u, int v) {
        double e = hexE(u, v);
        return e < 0 ? 1 : -(int) Math.min(4, Math.floor(e));
    }

    /** Distance from a column to the zig-zag gallery's centre line. */
    private static double galleryDist(double u, double v) {
        double best = 1e9;
        for (int i = 0; i + 1 < GALLERY.length; i++) {
            double ax = GALLERY[i][0], az = GALLERY[i][1], bx = GALLERY[i + 1][0], bz = GALLERY[i + 1][1];
            double dx = bx - ax, dz = bz - az, t = Math.max(0, Math.min(1, ((u - ax) * dx + (v - az) * dz) / (dx * dx + dz * dz)));
            double ex = ax + t * dx - u, ez = az + t * dz - v;
            best = Math.min(best, ex * ex + ez * ez);
        }
        return Math.sqrt(best);
    }

    private static boolean mazeArea(int u, int v) { return u >= 17 && Math.abs(v) <= Math.min(20, u - 3) && fortD(u, v) >= 2.5; }

    // ------------------------------------------------------------------------------------------------ the layout

    static final class L extends Layout {
        long h;
        final boolean[][] valid = new boolean[MI1 - MI0 + 2][MJ1 - MJ0 + 2];
        final boolean[][] openE = new boolean[MI1 - MI0 + 2][MJ1 - MJ0 + 2], openN = new boolean[MI1 - MI0 + 2][MJ1 - MJ0 + 2];
        int entI, entJ, heartI, heartJ;
        final List<int[]> shards = new ArrayList<>();      // {u0, v0, u1, v1, height, kind} inside the Court of Mirrors
        final List<int[]> blades = new ArrayList<>();      // {u0, v0, u1, v1, height, kind} out in the shard fields

        boolean valid(int i, int j) { return i >= MI0 && i <= MI1 && j >= MJ0 && j <= MJ1 && valid[i - MI0][j - MJ0]; }
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        l.h = s.hash;
        // the maze: every cell wholly inside the right court and ravelin, carved by a depth-first search from the entrance
        for (int i = MI0; i <= MI1; i++)
            for (int j = MJ0; j <= MJ1; j++) {
                boolean ok = true;
                for (int a = 1; a <= 3 && ok; a++) for (int b = 1; b <= 3 && ok; b++) { int u = 4 * i + a; ok = mazeArea(u, 4 * j + b - u); }
                l.valid[i - MI0][j - MJ0] = ok;
            }
        int best = Integer.MAX_VALUE;
        for (int i = MI0; i <= MI1 && best == Integer.MAX_VALUE; i++)
            for (int j = MJ0; j <= MJ1; j++) {
                if (!l.valid(i, j)) continue;
                int vc = Math.abs(4 * j + 2 - (4 * i + 2));
                if (vc < best) { best = vc; l.entI = i; l.entJ = j; }
            }
        int ni = MI1 - MI0 + 2, nj = MJ1 - MJ0 + 2;
        int[][] depth = new int[ni][nj];
        for (int[] row : depth) Arrays.fill(row, -1);
        int[] stack = new int[ni * nj * 2];
        int sp = 0;
        stack[sp++] = l.entI;
        stack[sp++] = l.entJ;
        depth[l.entI - MI0][l.entJ - MJ0] = 0;
        int far = -1;
        while (sp > 0) {
            int ci = stack[sp - 2], cj = stack[sp - 1];
            int[] dirs = {0, 1, 2, 3};
            for (int k = 3; k > 0; k--) { int q = r.nextInt(k + 1), t = dirs[k]; dirs[k] = dirs[q]; dirs[q] = t; }
            boolean moved = false;
            for (int d : dirs) {
                int ti = ci + (d == 0 ? 1 : d == 1 ? -1 : 0), tj = cj + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (!l.valid(ti, tj) || depth[ti - MI0][tj - MJ0] >= 0) continue;
                if (d == 0) l.openE[ci - MI0][cj - MJ0] = true;
                else if (d == 1) l.openE[ti - MI0][tj - MJ0] = true;
                else if (d == 2) l.openN[ci - MI0][cj - MJ0] = true;
                else l.openN[ti - MI0][tj - MJ0] = true;
                depth[ti - MI0][tj - MJ0] = depth[ci - MI0][cj - MJ0] + 1;
                if (depth[ti - MI0][tj - MJ0] > far) { far = depth[ti - MI0][tj - MJ0]; l.heartI = ti; l.heartJ = tj; }
                stack[sp++] = ti;
                stack[sp++] = tj;
                moved = true;
                break;
            }
            if (!moved) sp -= 2;
        }
        // three of its cells hold packs: the heart, and two along the way
        List<int[]> mid = new ArrayList<>();
        for (int i = MI0; i <= MI1; i++) for (int j = MJ0; j <= MJ1; j++) if (l.valid(i, j) && depth[i - MI0][j - MJ0] == far / 3 || l.valid(i, j) && depth[i - MI0][j - MJ0] == 2 * far / 3) mid.add(new int[] {i, j});
        // the mirror shards and the blades outside
        int[][] keep = {{-14, -24}, {14, -26}, {0, -42}, {3, -33}, {-25, -30}, {25, -30}};
        for (int n = 0, tries = 0; n < 11 && tries < 200; tries++) {
            int u = -27 + r.nextInt(55), v = -31 + r.nextInt(14);
            if (v > -Math.abs(u) - 2 || fortD(u, v) < 4 || galleryDist(u, v) < 5 || arenaLevel(u, v) <= 0) continue;
            boolean near = false;
            for (int[] k : keep) if (Math.abs(u - k[0]) <= 4 && Math.abs(v - k[1]) <= 4) near = true;
            if (near) continue;
            double a = r.nextDouble() * Math.PI;
            int len = 2 + r.nextInt(4);
            int u1 = u + (int) Math.round(Math.cos(a) * len), v1 = v + (int) Math.round(Math.sin(a) * len);
            if (fortD(u1, v1) < 4 || galleryDist(u1, v1) < 4) continue;
            l.shards.add(new int[] {u, v, u1, v1, 4 + r.nextInt(6), r.nextInt(4)});
            n++;
        }
        for (int n = 0, tries = 0; n < 22 && tries < 300; tries++) {
            int u = -44 + r.nextInt(89), v = -44 + r.nextInt(89);
            if (fortD(u, v) > -4.5) continue;
            double a = r.nextDouble() * Math.PI;
            int len = 2 + r.nextInt(5);
            int u1 = u + (int) Math.round(Math.cos(a) * len), v1 = v + (int) Math.round(Math.sin(a) * len);
            if (Math.abs(u1) > 44 || Math.abs(v1) > 44 || fortD(u1, v1) > -4.5) continue;
            l.blades.add(new int[] {u, v, u1, v1, 5 + r.nextInt(9), r.nextInt(4)});
            n++;
        }
        l.boss = new int[] {0, -3, 0};
        int[][] pts = {
            {0, 1, -42}, {3, 1, -33}, {-14, 1, -24}, {14, 1, -26}, {6, -3, 2}, {-20, 1, 6}, {-29, 1, 18}, {-36, -2, 0},
            {-8, 1, 22}, {3, 1, 35}, {-3, 13, 35}, {-2, 37, 34}, {-3, -9, 33}, {1, -9, 37}, {-27, 1, -30}, {27, 1, 30},
            {-34, TTOP - 3, -34}, {34, TTOP - 3, 34}, {25, 1, -30}, {-24, 1, 29}};
        String[] packs = {
            "cult_zealot+vindicator", "hound+spider", "hound+cave_spider", "ghoul+zombie", "!hound+skeleton", "tomb_crawler+husk",
            "hound+zombie_villager", "!ghoul+husk+hound", "deep_one+stray", "mi_go+enderman", "cult_adept+witch", "cult_zealot+evoker",
            "shoggoth+slime", "tomb_crawler+skeleton", "deep_one+zombie", "hound+creeper", "nightgaunt+creeper", "nightgaunt", "mi_go+endermite", "ghoul+spider"};
        for (int k = 0; k < pts.length; k++) l.garrisons.add(g(pts[k][0], pts[k][1], pts[k][2], packs[k]));
        String[] mazePacks = {"illusioner+evoker", "endermite+silverfish", "star_spawn+wither_skeleton"};
        int[][] cells = {mid.isEmpty() ? new int[] {l.entI, l.entJ} : mid.get(0), mid.size() < 2 ? new int[] {l.heartI, l.heartJ} : mid.get(mid.size() - 1), {l.heartI, l.heartJ}};
        for (int k = 0; k < 3; k++) {
            int u = 4 * cells[k][0] + 1, v = 4 * cells[k][1] + 3 - u;
            l.garrisons.add(g(u, 1, v, mazePacks[k]));
        }
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        L l = (L) plan;
        P p = new P(s, c);
        if (!p.hit(-46, -46, 46, 46)) return;
        fort(p);
        diagonals(p);
        arena(p);
        gallery(p);
        shards(p, l.shards, false);
        shards(p, l.blades, true);
        kennels(p);
        maze(p, l);
        dial(p);
        bridges(p);
        for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2) tower(p, 34 * su, 34 * sv, Math.atan2(sv, su));
        needle(p);
        tiles(p, l);
    }

    // ------------------------------------------------------------------------------------------------ walls and ground

    /** The fort's floor (black and white triangles), the battered mirror-faced walls and their glacis, the gate. */
    private static void fort(P p) {
        for (int u = p.ua(-45); u <= p.ub(45); u++)
            for (int v = p.va(-45); v <= p.vb(45); v++) {
                double d = fortD(u, v);
                if (d < -3) continue;
                int g = p.ground(u, v);
                if (d < 0) {
                    int hb = (int) Math.round(12 + d * 4);
                    for (int y = Math.max(-12, Math.min(g, 0) - 1); y <= hb; y++) p.set(u, y, v, y <= 0 ? RUB : FACET, 0);
                    continue;
                }
                for (int y = Math.max(-12, g - 1); y < 0; y++) p.set(u, y, v, RUB, 0);
                if (d < 2) {
                    p.set(u, 0, v, MAS, 0);
                    for (int y = 1; y <= 12; y++) p.set(u, y, v, y == 6 ? GLY : FACET, 0);
                    if (d < 0.9 && ((u + v) & 1) == 0) p.set(u, 13, v, OBSIDIAN, 0);
                } else {
                    p.set(u, 0, v, CHECK, 0);
                    if (g >= 0) p.clear(u, v, 1, g + 1);
                }
            }
    }

    /** The diagonal walls between the four courts, leaning in to a ridge, each with a door. */
    private static void diagonals(P p) {
        if (!p.hit(-25, -25, 25, 25)) return;
        for (int u = p.ua(-25); u <= p.ub(25); u++)
            for (int v = p.va(-25); v <= p.vb(25); v++) {
                int a = Math.abs(u), b = Math.abs(v), off = Math.abs(a - b);
                if (off > 1 || a < 13 || a > 24 || b < 13) continue;
                boolean door = a >= 17 && a <= 19;
                int top = off == 0 ? 10 : 9;
                for (int y = 1; y <= top; y++) {
                    if (door && y <= 4) { p.set(u, y, v, AIR, 0); continue; }
                    p.set(u, y, v, y == top ? OBSIDIAN : FACET, 0);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the arena

    /** The Alpha's arena: a stepped hexagram sunk four blocks, six obsidian fangs leaning over it from its inner corners. */
    private static void arena(P p) {
        if (!p.hit(-18, -18, 18, 18)) return;
        for (int u = p.ua(-18); u <= p.ub(18); u++)
            for (int v = p.va(-18); v <= p.vb(18); v++) {
                int lv = arenaLevel(u, v);
                if (lv > 0) continue;
                p.set(u, lv - 1, v, RUB, 0);
                p.set(u, lv, v, lv == -4 ? (Math.abs(hexE(u, v) - 6.5) < 0.5 ? OBSIDIAN : CHECK) : FACET, 0);
                p.clear(u, v, lv + 1, 0);
            }
        // the fangs: six obsidian claws rising from the inner corners and leaning in over the floor, an eye in each
        for (int k = 0; k < 6; k++) {
            double ct = Math.cos(k * Math.PI / 3), st = Math.sin(k * Math.PI / 3);
            for (int y = 1; y <= 14; y++) {
                double rr = 11.5 - (y - 1) * 0.32;
                int u = (int) Math.round(ct * rr), v = (int) Math.round(st * rr);
                if (p.in(u, v)) p.set(u, y, v, y == 5 ? SEA_LANTERN : OBSIDIAN, 0);
                if (y < 7) {
                    int u2 = (int) Math.round(ct * (rr + 1)), v2 = (int) Math.round(st * (rr + 1));
                    if (p.in(u2, v2)) p.set(u2, y, v2, CONCRETE, 15);
                }
            }
        }
        // the altar at the back point: a raised obsidian dais under a quartz screen
        p.box(-2, 10, 2, 12, -4, -3, OBSIDIAN, 0);
        p.box(-2, 10, 2, 12, -2, 0, AIR, 0);
        p.box(-2, 13, 2, 13, -3, -1, QUARTZ_B, 0);
    }

    // ------------------------------------------------------------------------------------------------ the Court of Mirrors

    /** The zig-zag gallery from the gate through the front ravelin to the arena: an A-framed corridor of mirror stone. */
    private static void gallery(P p) {
        if (!p.hit(-9, -46, 9, -16)) return;
        for (int u = p.ua(-9); u <= p.ub(9); u++)
            for (int v = p.va(-46); v <= p.vb(-16); v++) {
                double d = galleryDist(u, v);
                if (d > 2.7) continue;
                if (arenaLevel(u, v) <= 0) continue;
                int g = p.ground(u, v);
                for (int y = Math.max(-12, g - 1); y < 0; y++) p.set(u, y, v, RUB, 0);
                p.set(u, 0, v, d <= 1.6 ? CHECK : MAS, 0);
                if (g > 8) p.clear(u, v, 9, g + 1);
                for (int y = 1; y <= 8; y++) {
                    double w = y <= 4 ? 1.6 : y == 5 ? 1.05 : y == 6 ? 0.5 : -1;
                    p.set(u, y, v, d <= w ? AIR : y == 8 ? (d <= 1.2 ? OBSIDIAN : AIR) : FACET, 0);
                }
            }
        // side doors into the court at the bends, two lamps
        p.box(-8, -29, -7, -29, 1, 3, AIR, 0);
        p.box(8, -24, 9, -24, 1, 3, AIR, 0);
        p.set(0, 7, -40, GLOWSTONE, 0);
        p.set(0, 7, -25, GLOWSTONE, 0);
    }

    /** Mirror shards: tall thin blades of black glass, obsidian or quartz, their tops cut at a slant. */
    private static void shards(P p, List<int[]> list, boolean outside) {
        for (int[] b : list) {
            int a0 = Math.min(b[0], b[2]), a1 = Math.max(b[0], b[2]), c0 = Math.min(b[1], b[3]), c1 = Math.max(b[1], b[3]);
            if (!p.hit(a0, c0, a1, c1)) continue;
            double dx = b[2] - b[0], dz = b[3] - b[1], len2 = Math.max(1, dx * dx + dz * dz);
            for (int u = p.ua(a0); u <= p.ub(a1); u++)
                for (int v = p.va(c0); v <= p.vb(c1); v++) {
                    double t = Math.max(0, Math.min(1, ((u - b[0]) * dx + (v - b[1]) * dz) / len2));
                    double ex = b[0] + t * dx - u, ez = b[1] + t * dz - v;
                    if (ex * ex + ez * ez > 0.3) continue;
                    int y0 = outside ? Math.max(-11, Math.min(p.ground(u, v), 30)) : 0;
                    int top = y0 + 1 + (int) Math.round(b[4] * (1 - t * 0.8));
                    for (int y = y0 + 1; y <= top; y++) {
                        int kind = b[5];
                        int id = kind == 0 ? TINT : kind == 1 ? OBSIDIAN : kind == 2 ? QUARTZ_B : CONCRETE, meta = kind == 0 ? 15 : kind == 3 ? 15 : 0;
                        if (outside && y == y0 + 1) { id = OBSIDIAN; meta = 0; }
                        p.set(u, y, v, id, meta);
                    }
                    if (outside) p.set(u, y0, v, RUB, 0);
                }
        }
    }

    // ------------------------------------------------------------------------------------------------ the Kennels

    /** The kennels along the left curtain under lean-to roofs, the feeding yard, and the sunken pack pit in the ravelin. */
    private static void kennels(P p) {
        if (!p.hit(-46, -26, -12, 26)) return;
        for (int sv = -1; sv <= 1; sv += 2)
            for (int u = p.ua(-30); u <= p.ub(-27); u++)
                for (int k = 12; k <= 24; k++) {
                    int v = k * sv;
                    if (!p.in(u, v)) continue;
                    boolean part = k % 4 == 0;
                    if (part) { for (int y = 1; y <= 5; y++) p.set(u, y, v, FACET, 0); continue; }
                    if (u == -27) { for (int y = 1; y <= 3; y++) p.set(u, y, v, k % 4 == 2 && y <= 2 ? AIR : IRON_BARS, 0); }
                    else { p.set(u, 0, v, k % 2 == 0 ? BONE : RUB, 0); p.clear(u, v, 1, 3); }
                    int y = u == -27 ? 4 : u >= -29 ? 5 : 6;
                    p.set(u, y, v, u == -30 ? FACET : STAIRS, u == -30 ? 0 : p.f.stairs(-1, 0, false));
                }
        // the feeding yard: troughs, chained posts, bone heaps
        int[][] posts = {{-17, -3}, {-22, -12}, {-22, 12}, {-15, 9}};
        for (int[] q : posts) { p.box(q[0], q[1], q[0], q[1], 1, 2, FENCE, 0); p.set(q[0], 3, q[1], SKULL, 1); }
        for (int v = -6; v <= 6; v += 12) { p.set(-24, 1, v, CAULDRON, 1); p.set(-24, 1, v + 1, CAULDRON, 0); }
        p.box(-19, -8, -18, -7, 1, 1, BONE, 0);
        p.set(-18, 2, -8, BONE, 0);
        // the pack pit: the left ravelin sunk three blocks, ringed by steps, bones standing up like teeth
        for (int u = p.ua(-45); u <= p.ub(-28); u++)
            for (int v = p.va(-14); v <= p.vb(14); v++) {
                double d = fortD(u, v);
                if (d < 2 || u > -28 || Math.abs(v) > 11) continue;
                int depth = u <= -33 ? 3 : u == -32 ? 3 : u == -31 ? 2 : u == -30 ? 1 : 0;
                if (d < 2.5) depth = Math.min(depth, 0);
                if (depth == 0) continue;
                p.set(u, -depth, v, depth == 3 && p.f.roll(u, 0, v, 61) < 0.3 ? BONE : RUB, 0);
                p.clear(u, v, -depth + 1, 0);
                if (depth == 3 && u <= -36 && p.f.roll(u, 1, v, 62) < 0.12 && Math.abs(v) > 1) for (int y = -2; y <= -1 + (int) (p.f.roll(u, 2, v, 63) * 3); y++) p.set(u, y, v, BONE, 0);
            }
    }

    // ------------------------------------------------------------------------------------------------ the Maze of Acute Corners

    /** The maze: walls along the grid's columns and along its diagonals, seven high, capped in obsidian. */
    private static void maze(P p, L l) {
        if (!p.hit(16, -25, 45, 25)) return;
        for (int u = p.ua(16); u <= p.ub(45); u++)
            for (int v = p.va(-25); v <= p.vb(25); v++) {
                int i = Math.floorDiv(u, 4), a = Math.floorMod(u, 4), w = u + v, j = Math.floorDiv(w, 4), b = Math.floorMod(w, 4);
                boolean adj, open;
                if (a != 0 && b != 0) { adj = l.valid(i, j); open = adj; }
                else if (a == 0 && b != 0) {
                    adj = l.valid(i - 1, j) || l.valid(i, j);
                    open = l.valid(i - 1, j) && l.valid(i, j) && l.openE[i - 1 - MI0][j - MJ0] || i == l.entI && j == l.entJ;
                } else if (b == 0 && a != 0) {
                    adj = l.valid(i, j - 1) || l.valid(i, j);
                    open = l.valid(i, j - 1) && l.valid(i, j) && l.openN[i - MI0][j - 1 - MJ0];
                } else { adj = l.valid(i - 1, j - 1) || l.valid(i, j - 1) || l.valid(i - 1, j) || l.valid(i, j); open = false; }
                if (!adj && !mazeArea(u, v)) continue;
                if (open) { p.clear(u, v, 1, 7); continue; }
                for (int y = 1; y <= 7; y++) p.set(u, y, v, y == 7 ? OBSIDIAN : FACET, 0);
            }
        p.set(4 * l.entI, 7, 4 * l.entJ - 4 * l.entI, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------ the Court of Hours

    /** The dial of angles before the Needle: twelve obsidian hours round a leaning blade of a gnomon. */
    private static void dial(P p) {
        if (!p.hit(-6, 16, 6, 28)) return;
        for (int k = 0; k < 12; k++) {
            double t = k * Math.PI / 6;
            int u = (int) Math.round(Math.cos(t) * 4.6), v = 22 + (int) Math.round(Math.sin(t) * 4.6);
            p.set(u, 0, v, OBSIDIAN, 0);
            if (k % 3 == 0) p.set(u, 1, v, CONCRETE, 15);
        }
        for (int y = 1; y <= 11; y++) {
            int v = 22 + (y - 1) / 2;
            p.set(0, y, v, y == 11 ? SEA_LANTERN : y % 2 == 0 ? QUARTZ_B : OBSIDIAN, 0);
            if (y <= 6) p.set(0, y, v + 1, OBSIDIAN, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ towers, Needle, bridges

    /** The knife-edge bridges: one block of quartz wide, joining the lookouts of the four towers and the Needle. */
    private static void bridges(P p) {
        for (int s = -34; s <= 34; s += 68) {
            if (p.hit(-34, s, 34, s)) for (int u = p.ua(-34); u <= p.ub(34); u++) p.set(u, TTOP - 4, s, SLAB, 15);
            if (p.hit(s, -34, s, 34)) for (int v = p.va(-34); v <= p.vb(34); v++) p.set(s, TTOP - 4, v, SLAB, 15);
        }
    }

    /**
     * A prism tower in a bastion: triangular, its point toward the salient, three floors round a central pillar and its
     * ladder, a lookout with doors for the bridges, then a solid spire to a needle point.
     */
    private static void tower(P p, int cx, int cz, double t) {
        if (!p.hit(cx - TR, cz - TR, cx + TR, cz + TR)) return;
        int look = TTOP - 4;
        double inr = TR / 2.0, ct = Math.cos(t), st = Math.sin(t);
        for (int u = p.ua(cx - TR); u <= p.ub(cx + TR); u++)
            for (int v = p.va(cz - TR); v <= p.vb(cz + TR); v++) {
                double x = u - cx, z = v - cz, m = tri(x, z, ct, st);
                if (m > inr) continue;
                boolean shell = m > inr - 1.3;
                double toward = -(x * ct + z * st), perp = Math.abs(-x * st + z * ct);
                boolean door = shell && toward > 0 && perp <= 1.0;
                boolean onBridge = u == cx && (v - cz) * cz < 0 || v == cz && (u - cx) * cx < 0;
                int spire = (int) Math.floor(TTOP + (TTIP - TTOP) * (1 - m / inr));
                for (int y = 1; y <= spire; y++) {
                    if (y > TTOP) { p.set(u, y, v, y == spire ? OBSIDIAN : FACET, 0); continue; }
                    if (shell) {
                        if (door && y <= 3 || onBridge && (y == look + 1 || y == look + 2)) { p.set(u, y, v, AIR, 0); continue; }
                        if (y == look + 1 && (int) Math.abs(x + z) % 3 == 0) { p.set(u, y, v, IRON_BARS, 0); continue; }
                        p.set(u, y, v, y % 8 == 0 ? GLY : FACET, 0);
                    } else if (y == 8 || y == 16 || y == look || y == TTOP) p.set(u, y, v, y == TTOP ? FACET : QUARTZ_B, 0);
                    else p.set(u, y, v, AIR, 0);
                }
            }
        for (int y = 1; y < look; y++) p.set(cx, y, cz, OBSIDIAN, 0);
        int lu = cx + (cx > 0 ? -1 : 1);
        for (int y = 1; y <= look; y++) p.set(lu, y, cz, LADDER, p.f.facing(cx > 0 ? -1 : 1, 0));
        p.set(cx, TTOP - 1, cz, SEA_LANTERN, 0);
    }

    /** The Needle: a triangular spire over the hall of the hound-priests, four floors up to its lookout, the crypt below. */
    private static void needle(P p) {
        if (!p.hit(NX - NR, NZ - NR, NX + NR, NZ + NR)) return;
        double inr = NR / 2.0;
        for (int u = p.ua(NX - NR); u <= p.ub(NX + NR); u++)
            for (int v = p.va(NZ - NR); v <= p.vb(NZ + NR); v++) {
                double x = u - NX, z = v - NZ, m = tri(x, z, 0, 1);
                if (m > inr) continue;
                boolean shell = m > inr - 1.3;
                int spire = (int) Math.floor(NTOP + (NTIP - NTOP) * (1 - m / inr));
                boolean onBridge = v == 34;
                // the crypt beneath
                if (m <= inr - 0.8) {
                    p.set(u, -11, v, RUB, 0);
                    boolean cw = m > inr - 2.2;
                    for (int y = -10; y <= -1; y++) p.set(u, y, v, cw || y == -10 || y >= -4 ? (y == -10 ? CHECK : FACET) : AIR, 0);
                }
                p.set(u, 0, v, CHECK, 0);
                for (int y = 1; y <= spire; y++) {
                    if (y > NTOP) { p.set(u, y, v, y == spire ? OBSIDIAN : FACET, 0); continue; }
                    if (shell) {
                        boolean door = v == 28 && Math.abs(u) <= 1 && y <= 4;
                        boolean bridgeDoor = onBridge && (y == 25 || y == 26);
                        boolean window = (y == 49 || y == 50 || y == 9 || y == 33) && Math.abs(u) % 4 == 1;
                        if (door || bridgeDoor) { p.set(u, y, v, AIR, 0); continue; }
                        if (window) { p.set(u, y, v, IRON_BARS, 0); continue; }
                        p.set(u, y, v, y % 12 == 0 ? GLY : FACET, 0);
                    } else if (y % 12 == 0) p.set(u, y, v, y == NTOP ? FACET : QUARTZ_B, 0);
                    else p.set(u, y, v, AIR, 0);
                }
            }
        // the central pillar and its ladder up through the floors
        for (int y = 1; y < 48; y++) p.set(NX, y, NZ, OBSIDIAN, 0);
        for (int y = 1; y <= 48; y++) p.set(NX + 1, y, NZ, LADDER, p.f.facing(1, 0));
        p.set(NX, NTOP, NZ, SEA_LANTERN, 0);
        p.set(NX, 11, NZ - 2, GLOWSTONE, 0);
        // the stair down to the crypt
        for (int k = 0; k <= 10; k++) {
            int u = -6 + k, y = -k;
            for (int v = 30; v <= 31; v++) {
                for (int yy = -10; yy < y; yy++) p.set(u, yy, v, FACET, 0);
                p.set(u, y, v, STAIRS, p.f.stairs(-1, 0, false));
                p.clear(u, v, y + 1, y + 3);
            }
        }
        p.box(-5, 29, -2, 29, 1, 1, IRON_BARS, 0);
        p.box(-5, 32, -2, 32, 1, 1, IRON_BARS, 0);
        // three tombs and a lamp in the crypt
        for (int k = -1; k <= 1; k++) { p.box(k * 2, 35, k * 2, 36, -9, -9, QUARTZ_B, 0); p.box(k * 2, 35, k * 2, 36, -8, -8, SLAB, 7); }
        p.set(NX, -4, NZ + 1, SEA_LANTERN, 0);
    }

    // ------------------------------------------------------------------------------------------------ chests, cages, carvings, darts

    private static void tiles(P p, L l) {
        Frame f = p.f;
        long h = l.h;
        // the Alpha's hoard on the dais at the arena's back point
        f.chest(0, -2, 12, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.7");
        f.chest(-2, -2, 12, 0, -1, Sites.DESERT, "trinket:0.35");
        f.chest(2, -2, 12, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 1), 0, 99));
        f.sign(0, -1, 12, 0, -1, "IT COMES\nTHROUGH THE\nANGLES");
        // the gate and the gallery
        f.sign(-1, 3, -43, 1, 0, "THE ANGLES\nOF TINDALOS\nDO NOT COUNT\nTHEM");
        f.chest(1, 1, -45, -1, 0, Sites.CORRIDOR);
        int[][] traps = {{-1, 0, -37}, {-1, 0, -31}, {0, 0, -26}, {3, 0, -20}};
        for (int[] t : traps) { f.dispenser(t[0], t[1], t[2], 0, 0); f.set(t[0], t[1] + 1, t[2], PLATE); }
        // the kennels and the pack pit
        f.chest(-30, 1, -21, 1, 0, Sites.DUNGEON);
        f.spawner(-29, 1, 21, "SPIDER");
        f.sign(-26, 3, 12, 1, 0, "FED ON\nTIME ITSELF\nTHEY ARE\nNEVER FULL");
        f.chest(-34, -2, -3, 1, 0, Sites.DUNGEON, "trinket:0.2");
        f.spawner(-37, -2, 1, "ZOMBIE");
        // the maze: its heart and a dead end
        int hu = 4 * l.heartI + 2, hv = 4 * l.heartJ + 2 - hu;
        f.chest(hu, 1, hv, -1, 0, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 2), 0, 99) + ";trinket:0.4");
        f.spawner(hu + 1, 1, hv - 1, "SKELETON");
        f.chest(4 * l.entI + 3, 1, 4 * l.entJ + 1 - (4 * l.entI + 3), -1, 0, "minecraft:chests/stronghold_crossing");
        f.sign(4 * l.entI - 1, 3, 4 * l.entJ - 4 * l.entI, -1, 0, "COUNT THE\nCORNERS.\nTHEY ARE\nCOUNTING YOU");
        // the dial, the Needle's hall, floors and lookout, the crypt
        f.chest(2, 1, 19, 0, -1, "minecraft:chests/stronghold_library", "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99));
        f.chest(-2, 1, 36, 1, 0, Sites.LIBRARY);
        f.sign(3, 3, 29, 0, 1, "THE HOUND\nPRIESTS KEPT\nTHE HOURS\nHERE");
        f.chest(3, 13, 31, 0, 1, Sites.SMITH);
        f.chest(-3, 37, 32, 0, 1, "minecraft:chests/woodland_mansion");
        f.chest(-2, 49, 33, 1, 0, "minecraft:chests/end_city_treasure", "trinket:0.3");
        f.chest(-2, -9, 34, 0, 1, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h ^ 4), 0, 99));
        f.chest(2, -9, 33, 0, 1, "minecraft:chests/abandoned_mineshaft");
        f.spawner(0, -9, 38, "HUSK");
        f.sign(0, -7, 39, 0, -1, "TIME BENDS\nHERE. DO\nNOT STAY\nTOO LONG");
        // the bastion towers' lookouts
        int look = TTOP - 4;
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) {
                int cx = 34 * su, cz = 34 * sv;
                f.chest(cx - su, look + 1, cz - sv, -su, 0, su * sv > 0 ? Sites.DUNGEON : "minecraft:chests/igloo_chest");
            }
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
                case ELD: f.eldritch(u, y, v); break;
                case RUB: f.rubble(u, y, v); break;
                case GLY: f.glyph(u, y, v); break;
                case FACET: {
                    // broken-mirror facets: space cut by three families of slanted planes, each cell its own stone
                    int i = Math.floorDiv(u * 7 + y * 4, 23), j = Math.floorDiv(v * 7 - y * 4, 23), k = Math.floorDiv(u * 5 - v * 5 + y * 3, 29);
                    r = f.roll(i, k, j, 64);
                    if (r < 0.27) f.set(u, y, v, OBSIDIAN);
                    else if (r < 0.45) f.set(u, y, v, CONCRETE, 15);
                    else if (r < 0.60) f.set(u, y, v, QUARTZ_B);
                    else if (r < 0.72) f.set(u, y, v, CONCRETE, 0);
                    else if (r < 0.80) f.set(u, y, v, TINT, 15);
                    else if (r < 0.84) f.set(u, y, v, TINT, 0);
                    else if (r < 0.93) f.set(u, y, v, STONE, 6);
                    else f.set(u, y, v, CLAY, 15);
                    break;
                }
                case CHECK: {
                    int a = Math.floorMod(u, 6), b = Math.floorMod(v, 6), cell = Math.floorDiv(u, 6) + Math.floorDiv(v, 6) + (a + b >= 6 ? 1 : 0);
                    r = f.roll(u, y, v, 65);
                    if (r < 0.08) f.set(u, y, v, STONE, 6);
                    else if (r < 0.11) f.set(u, y, v, BRICK, 2);
                    else f.set(u, y, v, CONCRETE, (cell & 1) == 0 ? 15 : 0);
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
