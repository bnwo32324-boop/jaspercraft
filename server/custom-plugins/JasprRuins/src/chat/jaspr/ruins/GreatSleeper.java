package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Fallen Sleeper (epoch 6): a colossal statue of a tentacled god toppled from its plinth, lying on its side some
 * ninety blocks long. Its octopoid head looks toward the front with two stacked dead eyes, its tentacled beard spills
 * over the ground, and a processional way climbs to its open mouth (the Maw); one arm reaches out to a clawed hand, one
 * wing stands raised and torn against the sky, the other lies broken on the ground behind it. Inside: the Maw, the Throat
 * down into the dark, the Dreaming Gallery in its skull, the Heart Chamber (a round flat hall 33 across and 18 high where
 * the Sleeper's Avatar walks), the Spine Gallery over it, the Belly Vault, and a tunnel under the arm out to the palm.
 * Outside: the worship stair cut into its chest up to the shrine on its shoulder, cult shrines, offering pits, and the
 * broken plinth it fell from. The statue is a union of ellipsoids and tapered capsules evaluated column by column.
 */
final class GreatSleeper extends GreatDesign {
    private static final int R = 50;
    private static final int MAS = -1, ELD = -2, GLY = -3, RUB = -4, PAV = -5, SKIN = -6, CLAW = -7, FLESH = -8;
    /** The statue's local y range. */
    private static final int Y0 = -12, Y1 = 64, YN = Y1 - Y0 + 1;
    /** The Heart Chamber's floor (local y) and radius. */
    private static final int HF = -4, HR = 16;

    private static int b(int id, int meta) { return id << 4 | meta; }

    /** A primitive of the statue (or of its hollows): an ellipsoid, a tapered capsule, a vertical cylinder or a box. */
    private static final class Prim {
        final int kind, mat;
        final double ax, ay, az, bx, by, bz, ra, rb;
        final int u0, u1, v0, v1;

        Prim(int kind, int mat, double ax, double ay, double az, double bx, double by, double bz, double ra, double rb) {
            this.kind = kind; this.mat = mat; this.ax = ax; this.ay = ay; this.az = az; this.bx = bx; this.by = by; this.bz = bz; this.ra = ra; this.rb = rb;
            double lo0, hi0, lo1, hi1;
            switch (kind) {
                case 0: lo0 = ax - bx; hi0 = ax + bx; lo1 = az - bz; hi1 = az + bz; break;
                case 1: { double r = Math.max(ra, rb); lo0 = Math.min(ax, bx) - r; hi0 = Math.max(ax, bx) + r; lo1 = Math.min(az, bz) - r; hi1 = Math.max(az, bz) + r; break; }
                case 2: lo0 = ax - ra; hi0 = ax + ra; lo1 = az - ra; hi1 = az + ra; break;
                default: lo0 = ax; hi0 = bx; lo1 = az; hi1 = bz;
            }
            u0 = (int) Math.floor(lo0) - 2; u1 = (int) Math.ceil(hi0) + 2; v0 = (int) Math.floor(lo1) - 2; v1 = (int) Math.ceil(hi1) + 2;
        }

        /** The y interval (local) this covers on column (u, v), grown by g (negative shrinks); false when it misses. */
        boolean span(int u, int v, double g, double[] out) {
            switch (kind) {
                case 0: {
                    double rx = bx + g, ry = by + g, rz = bz + g;
                    if (rx <= 0.2 || ry <= 0.2 || rz <= 0.2) return false;
                    double du = (u - ax) / rx, dv = (v - az) / rz, q = 1 - du * du - dv * dv;
                    if (q <= 0) return false;
                    double h = ry * Math.sqrt(q);
                    out[0] = ay - h; out[1] = ay + h;
                    return true;
                }
                case 1: {
                    double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    int n = Math.max(2, (int) Math.ceil(len / 0.45));
                    double lo = 1e9, hi = -1e9;
                    for (int k = 0; k <= n; k++) {
                        double t = k / (double) n, r = ra + (rb - ra) * t + g;
                        if (r <= 0.2) continue;
                        double px = ax + dx * t - u, pz = az + dz * t - v, d2 = px * px + pz * pz;
                        if (d2 >= r * r) continue;
                        double h = Math.sqrt(r * r - d2), py = ay + dy * t;
                        if (py - h < lo) lo = py - h;
                        if (py + h > hi) hi = py + h;
                    }
                    if (lo > hi) return false;
                    out[0] = lo; out[1] = hi;
                    return true;
                }
                case 2: {
                    double r = ra + g, du = u - ax, dv = v - az;
                    if (du * du + dv * dv > r * r) return false;
                    out[0] = ay - g; out[1] = by + g;
                    return true;
                }
                default:
                    if (u < ax - g || u > bx + g || v < az - g || v > bz + g) return false;
                    out[0] = ay - g; out[1] = by + g;
                    return true;
            }
        }
    }

    private static Prim ell(double u, double y, double v, double ru, double ry, double rv) { return new Prim(0, 0, u, y, v, ru, ry, rv, 0, 0); }
    private static Prim cap(double au, double ay, double av, double bu, double by, double bv, double ra, double rb, int mat) { return new Prim(1, mat, au, ay, av, bu, by, bv, ra, rb); }
    private static Prim cyl(double u, double v, double r, int y0, int y1) { return new Prim(2, 0, u, y0, v, 0, y1, 0, r, r); }
    private static Prim box(int u0, int y0, int v0, int u1, int y1, int v1) { return new Prim(3, 0, u0, y0, v0, u1, y1, v1, 0, 0); }

    /** The layout: the statue's solids and hollows, the wing membranes (sampled points), the fallen blocks. */
    private static final class L extends Layout {
        final List<Prim> solid = new ArrayList<>(), hollow = new ArrayList<>();
        final List<int[]> membrane = new ArrayList<>();   // {u, y, v, keep}
        final List<int[]> fallen = new ArrayList<>();     // {u, v, size}
        int sbu0 = 99, sbu1 = -99, sbv0 = 99, sbv1 = -99;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        List<Prim> S = l.solid, H = l.hollow;
        // the head: a great octopoid skull, its crown bulging back, a brow over the eyes
        S.add(ell(-33, 11, -1, 12, 12, 12));
        S.add(ell(-40, 14, 1, 9, 10, 10));
        S.add(cap(-43, 1, -6, -43, 21, -5, 2.2, 2.2, 0));
        // the neck and shoulder, the torso, the hips
        S.add(ell(-18, 12, 1, 9, 17, 14));
        S.add(ell(0, 8, 3, 24, 26, 20));
        S.add(ell(24, 7, 5, 11, 18, 15));
        // the legs, drawn up, and the clawed feet against the plinth
        S.add(cap(26, 14, 2, 37, 12, -8, 7, 5.5, 0));
        S.add(cap(26, 4, 4, 37, 4, -4, 6, 5, 0));
        S.add(cap(37, 12, -8, 44, 11, 4, 5.5, 4, 0));
        S.add(cap(37, 4, -4, 45, 4, 6, 5, 3.5, 0));
        S.add(ell(46, 11, 7, 4, 3, 5));
        S.add(ell(47, 4, 9, 3.5, 3, 5));
        for (int k = -1; k <= 1; k++) {
            S.add(cap(47, 11 + k * 2, 10, 49, 10 + k * 2, 14, 1.2, 0.4, 1));
            S.add(cap(48, 4 + k * 2, 12, 49, 3 + k * 2, 16, 1.2, 0.4, 1));
        }
        // the outstretched arm and its clawed hand; the other arm broken off at the elbow
        S.add(cap(-15, 5, -10, -10, 4, -28, 5.5, 4.6, 0));
        S.add(cap(-10, 4, -28, -2, 3, -40, 4.6, 3.6, 0));
        S.add(ell(0, 2, -43, 5.5, 2.6, 4.2));
        double[][] fingers = {{-3, -45, -8, -49}, {-1, -46, -2, -50}, {2, -46, 4, -50}, {4, -44, 9, -47}, {4, -41, 9, -37}};
        for (double[] fg : fingers) {
            S.add(cap(fg[0], 2.2, fg[1], fg[2], 1.4, fg[3], 1.9, 1.2, 0));
            double tu = fg[2] + (fg[2] - fg[0]) * 0.45, tv = fg[3] + (fg[3] - fg[1]) * 0.45;
            if (tv < -50) { tu = fg[2] + (fg[2] - fg[0]) * 0.2; tv = -50; }
            S.add(cap(fg[2], 1.4, fg[3], tu, 0.8, tv, 1.1, 0.3, 1));
        }
        S.add(cap(-16, 28, -2, -5, 33, -6, 4.8, 4.2, 0));
        // the tentacled beard: eight from round the mouth, drooping to the ground and curling there
        for (int i = 0; i < 8; i++) {
            double ru = i % 2 == 0 ? -28 : -26, ry = 3 + 2 * i;
            double q = 1 - Math.pow((ru + 33) / 12, 2) - Math.pow((ry - 11) / 12, 2);
            double rv = q > 0 ? -1 - 12 * Math.sqrt(q) + 1.2 : -10;
            double p1u = ru + 3 + r.nextDouble() * 2, p1y = Math.max(2.2, ry * 0.55), p1v = rv - 4 - r.nextDouble() * 2;
            double p2u = -22 + 3.4 * i + r.nextDouble() * 3 - 1.5, p2v = -20 - r.nextDouble() * 6;
            double p3u = p2u + r.nextDouble() * 8 - 3, p3v = p2v - 3 - r.nextDouble() * 6;
            S.add(cap(ru, ry, rv, p1u, p1y, p1v, 2.6, 2.0, 0));
            S.add(cap(p1u, p1y, p1v, p2u, 1.4, p2v, 2.0, 1.3, 0));
            S.add(cap(p2u, 1.4, p2v, p3u, 1.0, p3v, 1.3, 0.5, 0));
        }
        // the raised wing's ribs (the fourth snapped), the fallen wing's ribs on the ground behind
        double[] root = {-10, 26, 14};
        double[][] tips = {{-30, 50, 26}, {-16, 58, 32}, {2, 54, 34}, {14, 42, 30}};
        for (int i = 0; i < tips.length; i++) {
            double[] t = tips[i];
            if (i == 3) { S.add(cap(root[0], root[1], root[2], (root[0] + t[0]) / 2, (root[1] + t[1]) / 2, (root[2] + t[2]) / 2, 1.9, 1.4, 1)); continue; }
            S.add(cap(root[0], root[1], root[2], t[0], t[1], t[2], 1.9, 0.8, 1));
        }
        membrane(l, root, tips[0], tips[1], 0.9, 1);
        membrane(l, root, tips[1], tips[2], 0.7, 2);
        membrane(l, root, tips[2], new double[] {(root[0] + tips[3][0]) / 2, (root[1] + tips[3][1]) / 2, (root[2] + tips[3][2]) / 2}, 0.55, 3);
        S.add(cap(8, 1.2, 40, 20, 1.0, 44, 1.4, 0.8, 1));                       // the snapped rib, fallen
        // a row of spines down its back, from the nape to the hips
        for (int k = 0; k < 7; k++) {
            double u = -20 + 6.5 * k, y = 9 + (k % 2) * 3;
            double q = k < 3 ? 1 - Math.pow((u + 18) / 9, 2) - Math.pow((y - 12) / 17, 2) : 1 - Math.pow(u / 24, 2) - Math.pow((y - 8) / 26, 2);
            if (q <= 0) continue;
            double v = (k < 3 ? 1 + 14 * Math.sqrt(q) : 3 + 20 * Math.sqrt(q)) - 1.5;
            double len = 6 + r.nextInt(4);
            S.add(cap(u, y, v, u + 1.5, y + 2 + r.nextInt(3), v + len, 2.1, 0.4, 1));
        }
        double[] low = {-8, 6, 20};
        double[][] lowTips = {{-26, 1, 42}, {-10, 1, 47}, {8, 1, 45}, {22, 1, 36}};
        for (double[] t : lowTips) S.add(cap(low[0], low[1], low[2], t[0], t[1], t[2], 1.7, 0.8, 1));
        for (int i = 0; i + 1 < lowTips.length; i++) membrane(l, low, lowTips[i], lowTips[i + 1], 0.6, 10 + i);
        for (Prim p : S) { l.sbu0 = Math.min(l.sbu0, p.u0); l.sbu1 = Math.max(l.sbu1, p.u1); l.sbv0 = Math.min(l.sbv0, p.v0); l.sbv1 = Math.max(l.sbv1, p.v1); }

        // the hollows: the Maw, the Throat (with its own stairs), the Dreaming Gallery, the Heart Chamber and its dome,
        // the passage to the Belly Vault, the Spine Gallery, the tunnel under the arm
        H.add(box(-35, 7, -17, -27, 12, -5));
        H.add(box(-30, 7, -5, -26, 11, -1));
        for (int u = -25; u <= -17; u++) { int y = -u - 20; H.add(box(u, y + 1, -5, u, y + 5, -1)); }
        H.add(box(-16, HF + 1, -5, -10, 1, -1));
        H.add(box(-40, 16, -5, -30, 20, 4));
        H.add(cyl(0, 2, HR, HF + 1, 14));
        H.add(ell(0, 14, 2, 15, 8, 15));
        H.add(box(14, HF + 1, 0, 19, 1, 4));
        H.add(box(18, HF + 1, -5, 30, 6, 9));
        H.add(box(-12, 25, -1, 12, 29, 7));
        H.add(cap(-8, -2, -13, -12, -2, -26, 1.8, 1.8, 0));
        H.add(cap(-12, -2, -26, -4, -2, -38, 1.8, 1.8, 0));
        H.add(cap(-4, -2, -38, -1, -2, -42, 1.8, 1.8, 0));
        // fallen blocks of the plinth and the wing
        for (int k = 0; k < 7; k++) l.fallen.add(new int[] {22 + r.nextInt(24), 30 + r.nextInt(16), 2 + r.nextInt(2)});

        l.boss = new int[] {0, HF + 1, 2};
        List<Garrison> g = l.garrisons;
        g.add(g(-31, 1, -40, "cult_zealot+cult_adept"));            // the processional way
        g.add(g(-31, 7, -9, "ghoul+tomb_crawler"));                 // the Maw
        g.add(g(-14, HF + 1, -3, "deep_one+zombie"));                // the Throat's foot
        g.add(g(-36, 16, 0, "mi_go+enderman"));                      // the Dreaming Gallery
        g.add(g(8, 25, 3, "skeleton+stray"));                         // the Spine Gallery
        g.add(g(-2, 33, 2, "nightgaunt+cult_adept"));                 // the shoulder shrine
        g.add(g(26, HF + 1, 2, "slime+shoggoth"));                    // the Belly Vault
        g.add(g(-11, -3, -26, "cave_spider+spider"));                  // the tunnel under the arm
        g.add(g(-44, 2, -42, "witch+illusioner"));                     // the cult shrines
        g.add(g(44, 2, -42, "vindicator+evoker"));
        g.add(g(-44, 2, 40, "hound+creeper"));
        g.add(g(22, -5, -38, "silverfish+endermite"));                 // an offering pit
        g.add(g(42, 10, 24, "star_spawn+wither_skeleton"));            // the plinth's broken top
        g.add(g(-6, 1, 34, "nightgaunt"));                             // among the fallen wing
        g.add(g(-10, HF + 1, 8, "!star_spawn+husk"));                   // the Heart Chamber
        g.add(g(20, 1, -24, "zombie_villager+husk"));                   // before the worship stair
        return l;
    }

    /** Samples a triangle of wing membrane into points, torn where the hash says (keep is the share that survives). */
    private static void membrane(L l, double[] a, double[] b, double[] c, double keep, int salt) {
        double e = Math.max(dist(a, b), Math.max(dist(b, c), dist(a, c)));
        int n = (int) Math.ceil(e * 1.7);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i <= n; i++)
            for (int j = 0; i + j <= n; j++) {
                double s = i / (double) n, t = j / (double) n;
                int u = (int) Math.round(a[0] + (b[0] - a[0]) * s + (c[0] - a[0]) * t);
                int y = (int) Math.round(a[1] + (b[1] - a[1]) * s + (c[1] - a[1]) * t);
                int v = (int) Math.round(a[2] + (b[2] - a[2]) * s + (c[2] - a[2]) * t);
                if (Math.abs(u) > R || Math.abs(v) > R || y < 1) continue;
                long k = ((long) u & 0xffff) << 32 | ((long) y & 0xffff) << 16 | ((long) v & 0xffff);
                if (!seen.add(k)) continue;
                double torn = Hash.noise(salt * 7919L, u * 1.0 + y * 0.5, v * 1.0 - y * 0.5, 4.0);
                if (torn > keep + 0.1) continue;
                l.membrane.add(new int[] {u, y, v});
            }
    }

    private static double dist(double[] a, double[] b) { double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2]; return Math.sqrt(x * x + y * y + z * z); }

    // ------------------------------------------------------------------------------------------------ the drawing tools

    /** The chunk's share of the footprint in local coordinates, and drawing tools clipped to it. */
    private static final class P {
        final Plans.GreatSite s; final Frame f; final int u0, u1, v0, v1;
        private final int[] ground = new int[256];
        private final boolean[] known = new boolean[256];

        P(Plans.GreatSite s, Canvas c) {
            this.s = s;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int xa = c.x0 - s.x, xb = xa + 15, za = c.z0 - s.z, zb = za + 15;
            switch (s.rot & 3) {
                case 1: u0 = za; u1 = zb; v0 = -xb; v1 = -xa; break;
                case 2: u0 = -xb; u1 = -xa; v0 = -zb; v1 = -za; break;
                case 3: u0 = -zb; u1 = -za; v0 = xa; v1 = xb; break;
                default: u0 = xa; u1 = xb; v0 = za; v1 = zb;
            }
        }

        int ground(int a, int b) {
            int i = (a - u0) << 4 | (b - v0);
            if (!known[i]) { known[i] = true; ground[i] = s.surface(f.wx(a, b), f.wz(a, b)) - s.base; }
            return ground[i];
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int a, int b) { return a >= u0 && a <= u1 && b >= v0 && b <= v1; }

        void put(int a, int y, int b, int m) {
            switch (m) {
                case MAS: f.masonry(a, y, b); return;
                case ELD: case SKIN: f.eldritch(a, y, b); return;
                case GLY: f.glyph(a, y, b); return;
                case RUB: f.rubble(a, y, b); return;
                case PAV: f.paving(a, y, b); return;
                case CLAW: { double q = f.roll(a, y, b, 501); f.set(a, y, b, q < 0.7 ? OBSIDIAN : CLAY, q < 0.7 ? 0 : 15); return; }
                case FLESH: {
                    double q = f.roll(a, y, b, 502);
                    if (q < 0.38) f.set(a, y, b, 214); else if (q < 0.66) f.set(a, y, b, 215); else if (q < 0.84) f.set(a, y, b, 112);
                    else if (q < 0.975) f.set(a, y, b, OBSIDIAN); else f.set(a, y, b, MAGMA);
                    return;
                }
                default: f.set(a, y, b, m >> 4, m & 15);
            }
        }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int aa = Math.max(Math.min(a0, a1), u0), ab = Math.min(Math.max(a0, a1), u1), ba = Math.max(Math.min(b0, b1), v0), bb = Math.min(Math.max(b0, b1), v1);
            for (int a = aa; a <= ab; a++) for (int b = ba; b <= bb; b++) for (int y = y0; y <= y1; y++) put(a, y, b, m);
        }

        void set(int a, int y, int b, int id, int meta) { if (in(a, b)) f.set(a, y, b, id, meta); }
        void set(int a, int y, int b, int id) { if (in(a, b)) f.set(a, y, b, id, 0); }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) {
        L l = (L) layout;
        P p = new P(s, c);
        if (!p.hit(-R, -R, R, R)) return;
        land(p);
        statue(p, l);
        for (int[] m : l.membrane) {
            if (!p.in(m[0], m[2])) continue;
            int q = (m[0] * 3 + m[1] * 5 + m[2] * 7) & 7;
            p.f.set(m[0], m[1], m[2], q < 2 ? CLAY : PRISMARINE, q < 2 ? 15 : 2);
        }
        heart(p);
        maw(p);
        face(p);
        innerStairs(p);
        outside(p, l);
        tiles(p, s);
    }

    /** Levels the footprint: footings down to the land, the land (and the field's debris) cut away, a retaining face at the edge. */
    private static void land(P p) {
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int t = p.ground(a, b);
                boolean edge = Math.max(Math.abs(a), Math.abs(b)) >= R - 1;
                for (int y = t + 1; y < 0; y++) { if (edge) p.f.masonry(a, y, b); else p.f.rubble(a, y, b); }
                p.f.clear(a, b, 1, Math.max(t, 0) + 3);
                if (edge && t > 1) for (int y = 1; y < t; y++) p.f.masonry(a, y, b);
                double q = p.f.roll(a, 0, b, 503);
                if (q < 0.55) p.f.paving(a, 0, b); else if (q < 0.8) p.f.rubble(a, 0, b); else if (q < 0.92) p.f.set(a, 0, b, DIRT, 1); else p.f.set(a, 0, b, GRAVEL);
            }
    }

    /**
     * The statue, column by column: inside any solid it is the god's skin (the Choir's dark stone; claws and wing bones of
     * obsidian), within reach of a hollow its red-black flesh, deeper plain stone; the hollows are carved out of it all.
     */
    private static void statue(P p, L l) {
        List<Prim> fs = new ArrayList<>(), fh = new ArrayList<>();
        for (Prim q : l.solid) if (p.hit(q.u0, q.v0, q.u1, q.v1)) fs.add(q);
        for (Prim q : l.hollow) if (p.hit(q.u0, q.v0, q.u1, q.v1)) fh.add(q);
        if (fs.isEmpty() && fh.isEmpty()) return;
        boolean[] solid = new boolean[YN], core = new boolean[YN], hol = new boolean[YN], near = new boolean[YN];
        int[] mat = new int[YN];
        double[] iv = new double[2];
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int lo = YN, hi = -1;
                for (Prim q : fs) {
                    if (a < q.u0 || a > q.u1 || b < q.v0 || b > q.v1 || !q.span(a, b, 0, iv)) continue;
                    int i0 = Math.max(0, (int) Math.ceil(iv[0]) - Y0), i1 = Math.min(YN - 1, (int) Math.floor(iv[1]) - Y0);
                    for (int i = i0; i <= i1; i++) { if (!solid[i]) { solid[i] = true; mat[i] = q.mat; } else if (q.mat > mat[i]) mat[i] = q.mat; }
                    lo = Math.min(lo, i0); hi = Math.max(hi, i1);
                    if (q.span(a, b, -1.6, iv)) {
                        int j0 = Math.max(0, (int) Math.ceil(iv[0]) - Y0), j1 = Math.min(YN - 1, (int) Math.floor(iv[1]) - Y0);
                        for (int i = j0; i <= j1; i++) core[i] = true;
                    }
                }
                for (Prim q : fh) {
                    if (a < q.u0 || a > q.u1 || b < q.v0 || b > q.v1) continue;
                    if (q.span(a, b, 0, iv)) {
                        int i0 = Math.max(0, (int) Math.ceil(iv[0] - 1e-9) - Y0), i1 = Math.min(YN - 1, (int) Math.floor(iv[1] + 1e-9) - Y0);
                        for (int i = i0; i <= i1; i++) hol[i] = true;
                        lo = Math.min(lo, i0); hi = Math.max(hi, i1);
                    }
                    if (q.span(a, b, 1.6, iv)) {
                        int i0 = Math.max(0, (int) Math.ceil(iv[0]) - Y0), i1 = Math.min(YN - 1, (int) Math.floor(iv[1]) - Y0);
                        for (int i = i0; i <= i1; i++) near[i] = true;
                    }
                }
                for (int i = Math.max(0, lo); i <= hi; i++) {
                    int y = Y0 + i;
                    if (hol[i]) p.f.set(a, y, b, AIR);
                    else if (solid[i]) {
                        if (!core[i]) p.put(a, y, b, mat[i] == 1 ? CLAW : SKIN);
                        else if (near[i]) p.put(a, y, b, FLESH);
                        else p.f.set(a, y, b, STONE, (i & 7) == 0 ? 5 : 0);
                    }
                }
                java.util.Arrays.fill(solid, false); java.util.Arrays.fill(core, false); java.util.Arrays.fill(hol, false); java.util.Arrays.fill(near, false);
            }
    }

    // ------------------------------------------------------------------------------------------------ inside

    /** The Heart Chamber's floor: a round mosaic of rings and spokes round an obsidian heart, a magma core. */
    private static void heart(P p) {
        if (!p.hit(-HR - 1, 2 - HR - 1, HR + 1, 2 + HR + 1)) return;
        for (int a = Math.max(-HR - 1, p.u0); a <= Math.min(HR + 1, p.u1); a++)
            for (int b = Math.max(2 - HR - 1, p.v0); b <= Math.min(2 + HR + 1, p.v1); b++) {
                double r = Math.sqrt(a * a + (b - 2) * (b - 2));
                if (r > HR + 0.5) continue;
                double ang = Math.toDegrees(Math.atan2(a, b - 2));
                boolean spoke = Math.abs(((ang % 45) + 45) % 45 - 22.5) > 20.5 && r > 3;
                int id, meta = 0;
                if (r < 1) id = MAGMA;
                else if (r < 3) id = OBSIDIAN;
                else if (r > 6.6 && r < 7.6 || r > 11.6 && r < 12.6) id = 215;
                else if (spoke) id = 112;
                else if (r > HR - 0.6) id = 214;
                else { id = (((a + b) & 1) == 0) ? PRISMARINE : CLAY; meta = id == PRISMARINE ? 2 : 15; }
                p.f.set(a, HF, b, id, meta);
                p.f.set(a, HF - 1, b, STONE, 0);
            }
        // the altar of the heart at the chamber's back
        p.box(-1, HF + 1, 16, 1, HF + 1, 16, b(OBSIDIAN, 0));
        p.set(0, HF + 2, 16, SKULL, 1);
    }

    /** The Maw: its floor and the ground under its lip, rows of bone teeth top and bottom. */
    private static void maw(P p) {
        if (!p.hit(-36, -18, -26, -4)) return;
        p.box(-35, 6, -17, -27, 6, -5, PAV);
        for (int b = -17; b <= -13; b++) p.box(-35, 1, b, -27, 5, b, MAS);
        for (int a = -35; a <= -27; a++) {
            if ((a & 1) == 0) { p.set(a, 12, -14, BONE, 0); if ((a & 3) == 0) p.set(a, 11, -14, BONE, 0); }
            else if ((a & 3) == 1) p.set(a, 7, -14, BONE, 0);
        }
        p.set(-31, 13, -9, GLOWSTONE);
    }

    /** The two dead eyes, stacked on the face of a god lying on its side: obsidian with slit pupils of pale light. */
    private static void face(P p) {
        if (!p.hit(-43, -14, -35, -4)) return;
        for (int yc = 6; yc <= 16; yc += 10)
            for (int a = -42; a <= -36; a++)
                for (int y = yc - 3; y <= yc + 3; y++) {
                    double e = Math.pow((a + 39) / 2.7, 2) + Math.pow((y - yc) / 3.3, 2);
                    if (e > 1) continue;
                    double qh = 1 - Math.pow((a + 33) / 12.0, 2) - Math.pow((y - 11) / 12.0, 2), qc = 1 - Math.pow((a + 40) / 9.0, 2) - Math.pow((y - 14) / 10.0, 2);
                    double vf = 99;
                    if (qh > 0) vf = Math.min(vf, -1 - 12 * Math.sqrt(qh));
                    if (qc > 0) vf = Math.min(vf, 1 - 10 * Math.sqrt(qc));
                    if (vf > 50) continue;
                    int v = (int) Math.ceil(vf);
                    boolean pupil = a == -39 && Math.abs(y - yc) <= 1;
                    p.set(a, y, v, pupil ? SEA_LANTERN : OBSIDIAN, 0);
                    p.set(a, y, v + 1, OBSIDIAN, 0);
                    if (e > 0.6 && p.in(a, v - 1) && !pupil) p.set(a, y, v - 1, CLAY, 15);
                }
    }

    /** The Throat's stair down, the ladder from the Maw to the Dreaming Gallery, the way out through the palm, the oculi. */
    private static void innerStairs(P p) {
        if (p.hit(-26, -6, -16, 0))
            for (int u = -25; u <= -17; u++) {
                int y = -u - 20;
                p.box(u, y, -5, u, y, -1, b(BRICK_STAIRS, p.f.stairs(-1, 0, false)));
                p.box(u, y - 2, -5, u, y - 1, -1, MAS);
            }
        for (int y = 7; y <= 15; y++) p.set(-33, y, -5, LADDER, p.f.facing(0, -1));
        if (p.hit(-3, -50, 1, -42)) {
            for (int k = 0; k < 4; k++) {
                int v = -43 - k, y = HF + 1 + k;
                p.box(-2, y + 1, v, 0, y + 3, v, b(AIR, 0));
                p.box(-2, y, v, 0, y, v, b(BRICK_STAIRS, p.f.stairs(0, -1, false)));
            }
            p.box(-2, 1, -50, 0, 3, -47, b(AIR, 0));
            p.box(-2, 0, -50, 0, 0, -47, PAV);
        }
        for (int a = -6; a <= 6; a += 6) { p.set(a, 23, 2, AIR); p.set(a, 24, 2, IRON_BARS); }
        for (int y = 25; y <= 32; y++) p.set(-2, y, 7, LADDER, p.f.facing(0, -1));
        p.set(0, 30, 3, GLOWSTONE);
        p.set(-36, 21, 0, GLOWSTONE);
    }

    // ------------------------------------------------------------------------------------------------ outside

    private static final int[] DU = {0, 1, 0, -1}, DV = {-1, 0, 1, 0};

    private static void outside(P p, L l) {
        processional(p);
        flankStair(p);
        cultShrine(p, -44, -42, 1);
        cultShrine(p, 44, -42, 3);
        cultShrine(p, -44, 40, 1);
        pit(p, 22, -38);
        pit(p, 28, 40);
        plinth(p);
        for (int[] fb : l.fallen) {
            if (!p.hit(fb[0], fb[1], fb[0] + fb[2], fb[1] + fb[2])) continue;
            p.box(fb[0], 1, fb[1], fb[0] + fb[2] - 1, fb[2], fb[1] + fb[2] - 1, ELD);
            p.set(fb[0] + fb[2], 1, fb[1], MOSSY);
        }
    }

    /** The processional way to the Maw: braziers on chiseled posts, then a broad stair up between the tentacles. */
    private static void processional(P p) {
        if (!p.hit(-38, -48, -24, -17)) return;
        for (int a = Math.max(-35, p.u0); a <= Math.min(-27, p.u1); a++)
            for (int b = Math.max(-48, p.v0); b <= Math.min(-24, p.v1); b++) {
                int w = Math.abs(a + 31);
                p.f.set(a, 0, b, w == 4 ? PRISMARINE : BRICK, w == 4 ? 2 : w == 0 && Math.floorMod(b, 3) == 0 ? 3 : 0);
                p.f.clear(a, b, 1, 5);
            }
        for (int v = -44; v <= -28; v += 8)
            for (int s = -1; s <= 1; s += 2) {
                int a = -31 + 6 * s;
                p.box(a, 1, v, a, 2, v, b(BRICK, 3));
                p.set(a, 3, v, MAGMA);
            }
        for (int v = -23; v <= -18; v++) {
            int y = v + 24;
            p.box(-34, 1, v, -28, y - 1, v, MAS);
            p.box(-34, y, v, -28, y, v, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
            p.box(-34, y + 1, v, -28, y + 5, v, b(AIR, 0));
            p.box(-35, 1, v, -35, y + 1, v, MAS);
            p.box(-27, 1, v, -27, y + 1, v, MAS);
        }
        p.box(-36, 1, -47, -36, 2, -47, MAS);
    }

    /** The worship stair: a ramp out of the ground, then a trench cut up the god's chest to the shrine on its shoulder. */
    private static void flankStair(P p) {
        if (!p.hit(3, -33, 11, 9)) return;
        for (int v = -32; v <= -1; v++) {
            int y = v + 33;
            boolean out = v <= -16;
            for (int a = 3; a <= 9; a++) {
                if (!p.in(a, v)) continue;
                for (int yy = out ? 1 : y - 2; yy < y; yy++) p.f.masonry(a, yy, v);
                if (a == 3 || a == 9) { if (out) p.f.set(a, y, v, BRICK, 2); if (out) p.f.set(a, y + 1, v, WALL, 1); continue; }
                p.f.set(a, y, v, BRICK_STAIRS, p.f.stairs(0, 1, false));
                for (int yy = y + 1; yy <= y + 4; yy++) p.f.set(a, yy, v, AIR);
            }
        }
        // the shrine on the shoulder: a paved platform, four posts, an altar
        for (int a = Math.max(-4, p.u0); a <= Math.min(10, p.u1); a++)
            for (int b = Math.max(0, p.v0); b <= Math.min(8, p.v1); b++) {
                if (a == -2 && b == 7) continue;
                p.f.masonry(a, 31, b);
                p.f.paving(a, 32, b);
                p.f.clear(a, b, 33, 37);
            }
        for (int a = -4; a <= 10; a += 14)
            for (int b = 0; b <= 8; b += 8) { p.box(a, 33, b, a, 35, b, ELD); p.set(a, 36, b, OBSIDIAN); }
        p.set(3, 33, 6, OBSIDIAN);
        p.set(3, 34, 6, SKULL, 1);
    }

    /** A cult shrine: a plinth, four columns, low walls on three sides, a broken roof, an altar with its skull. */
    private static void cultShrine(P p, int cu, int cv, int face) {
        if (!p.hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
        int fu = DU[face], fv = DV[face];
        p.box(cu - 4, 1, cv - 4, cu + 4, 1, cv + 4, b(DOUBLE_SLAB, 5));
        for (int a = cu - 4; a <= cu + 4; a++)
            for (int b = cv - 4; b <= cv + 4; b++) {
                if (!p.in(a, b)) continue;
                int da = a - cu, db = b - cv;
                boolean col = Math.abs(da) == 3 && Math.abs(db) == 3;
                boolean edge = Math.max(Math.abs(da), Math.abs(db)) == 3;
                boolean open = fu != 0 ? da == 3 * fu : db == 3 * fv;
                if (col) { p.f.set(a, 2, b, BRICK, 3); for (int y = 3; y <= 6; y++) p.f.eldritch(a, y, b); p.f.set(a, 7, b, BRICK, 3); }
                else if (edge && !open) { p.f.masonry(a, 2, b); p.f.masonry(a, 3, b); }
                if (Math.max(Math.abs(da), Math.abs(db)) <= 4 && p.f.keep(a, 8, b, 0.75)) p.f.set(a, 8, b, SLAB, 5);
                if (Math.max(Math.abs(da), Math.abs(db)) <= 1) p.f.eldritch(a, 9, b);
            }
        p.set(cu, 10, cv, OBSIDIAN);
        p.set(cu - 2 * fu, 2, cv - 2 * fv, OBSIDIAN);
        p.set(cu - 2 * fu, 3, cv - 2 * fv, SKULL, 1);
    }

    /** An offering pit: six deep, lined, floored with bones and soul sand, a ladder down, a rim of skull posts. */
    private static void pit(P p, int pu, int pv) {
        if (!p.hit(pu - 6, pv - 6, pu + 6, pv + 6)) return;
        for (int a = Math.max(pu - 6, p.u0); a <= Math.min(pu + 6, p.u1); a++)
            for (int b = Math.max(pv - 6, p.v0); b <= Math.min(pv + 6, p.v1); b++) {
                double d = Math.sqrt((a - pu) * (a - pu) + (b - pv) * (b - pv));
                if (d <= 4.4) {
                    for (int y = -5; y <= 0; y++) p.f.set(a, y, b, AIR);
                    double q = p.f.roll(a, -6, b, 504);
                    p.f.set(a, -6, b, q < 0.45 ? BONE : q < 0.8 ? 88 : GRAVEL, 0);
                    if (d > 1.5 && p.f.roll(a, -5, b, 505) < 0.2) p.f.set(a, -5, b, SKULL, 1);
                } else if (d <= 5.4) {
                    for (int y = -6; y <= 0; y++) p.f.masonry(a, y, b);
                    if (((a + b) & 3) == 0) { p.f.set(a, 1, b, FENCE); p.f.set(a, 2, b, SKULL, 1); }
                    else if (!(b == pv && a > pu)) p.f.set(a, 1, b, WALL, 1);
                }
            }
        for (int y = -5; y <= 0; y++) p.set(pu + 4, y, pv, LADDER, p.f.facing(-1, 0));
    }

    /** The plinth the Sleeper fell from: three broken steps, its corner collapsed, the god's ankles still standing on it. */
    private static void plinth(P p) {
        if (!p.hit(27, 13, 50, 35)) return;
        for (int a = Math.max(36, p.u0); a <= Math.min(50, p.u1); a++)
            for (int b = Math.max(14, p.v0); b <= Math.min(34, p.v1); b++) {
                int e = Math.min(Math.min(a - 36, 50 - a), Math.min(b - 14, 34 - b));
                int top = e >= 4 ? 9 : e >= 2 ? 6 : 3;
                double broken = (a - 36) + (b - 14) - 6 - Hash.unit(Hash.of(0x51EEL, a, b)) * 5;
                if (broken < 0) top = Math.max(0, Math.min(top, (int) (top + broken)));
                for (int y = 1; y <= top; y++) p.put(a, y, b, y == 3 || y == 6 ? GLY : y == top ? MAS : ELD);
                if (broken < 0 && broken > -4 && p.f.roll(a, top + 1, b, 506) < 0.4) p.f.rubble(a, top + 1, b);
            }
        for (int k = 0; k <= 8; k++) p.box(33 + k, 1 + k, 25, 33 + k, 1 + k, 27, b(BRICK_STAIRS, p.f.stairs(1, 0, false)));
        for (int k = 0; k <= 7; k++) p.box(33 + k, 1, 25, 33 + k, k, 27, MAS);
        int[][] ankles = {{42, 22, 4}, {44, 27, 3}};
        for (int[] an : ankles) p.box(an[0] - 1, 10, an[1] - 1, an[0] + 1, 9 + an[2], an[1] + 1, ELD);
    }

    // ------------------------------------------------------------------------------------------------ tiles

    private static void tiles(P p, Plans.GreatSite s) {
        Frame f = p.f;
        long h = s.hash;
        f.sign(-36, 2, -48, 0, -1, "THE SLEEPER\nFELL FROM\nTHE STARS");
        f.sign(-36, 3, -20, -1, 0, "ENTER HIS\nMOUTH AND\nBE DREAMT");
        f.sign(0, HF + 1, 15, 0, -1, "HIS HEART\nSTILL BEATS\nDO NOT\nWAKE IT");
        f.sign(43, 3, 13, 0, -1, "HERE HE\nSTOOD UNTIL\nTHE STARS\nFELL");
        f.chest(2, HF + 1, 16, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        f.chest(-2, HF + 1, 16, 0, -1, "minecraft:chests/woodland_mansion", "trinket:0.4");
        f.chest(-39, 16, 3, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h), 0, 99));
        f.spawner(-38, 16, -3, "SKELETON");
        f.chest(10, 25, 6, 0, -1, Sites.CORRIDOR);
        f.chest(29, HF + 1, 8, -1, 0, Sites.DESERT, "trinket:0.25");
        f.spawner(29, HF + 1, -4, "ZOMBIE");
        f.chest(-34, 7, -7, 1, 0, Sites.DUNGEON);
        f.chest(5, 33, 6, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 7), 0, 99));
        int[][] shrines = {{-44, -42, 1}, {44, -42, 3}, {-44, 40, 1}};
        String[] tables = {Sites.DUNGEON, Sites.SMITH, "minecraft:chests/igloo_chest"};
        for (int k = 0; k < 3; k++) {
            int cu = shrines[k][0], cv = shrines[k][1], face = shrines[k][2], fu = DU[face], fv = DV[face];
            int ru = -fv, rv = fu;
            f.chest(cu - 2 * fu + ru, 2, cv - 2 * fv + rv, fu, fv, tables[k]);
            f.sign(cu - 2 * fu - ru, 3, cv - 2 * fv - rv, fu, fv, Lore.chant(Hash.mix(h ^ k)));
        }
        f.chest(20, -5, -37, 1, 0, Sites.DESERT, "trinket:0.15");
        f.spawner(23, -5, -40, "SPIDER");
        f.chest(26, -5, 41, 1, 0, "minecraft:chests/nether_bridge");
        f.chest(45, 10, 20, -1, 0, "minecraft:chests/stronghold_crossing", "lore:" + Hash.range(Hash.mix(h ^ 11), 0, 99));
        f.chest(-3, 1, -49, 1, 0, "minecraft:chests/abandoned_mineshaft");
    }
}
