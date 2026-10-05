package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Leviathan's Bones (owner, 2026-10-04: "Add 10 new structures to the Nether ... I want them to be huge
 * structures"): the skeleton of a titanic serpent-dragon, three hundred blocks from snout to tail, lying in a sweeping S
 * across a cavern of soul sand and bone dust. From the head:
 * <ul>
 *   <li>the Skull, forty long and twenty-six high under two great horns: the Jaw-Hall between its teeth (the broken chin
 *       is its door, the tongue a floor that gives way), the Gullet climbing to the Marrow Shrine in the braincase (its
 *       eyes burn), the crack in the crown, and behind the shrine's bone wall the Marrow-Vault, sealed until the Wyrm
 *       falls;</li>
 *   <li>the Spine Road: the neural spines' tops and the scavengers' bridges between them, a walk from the crown over the
 *       neck, the hump and the tail into the Coil;</li>
 *   <li>the Rib-Vault: ribs arching forty-five blocks over a nave eighty wide, Marrowtown (the scavengers' shanty town)
 *       in its aisles behind palisades, and the Heart Court, where the fossil Heart lies and the Marrow Wyrm circles in
 *       the open air under the spine;</li>
 *   <li>the forelimbs' claws gripping the floor, the hips and hind legs, the coiled tail and the Nest inside the Coil;</li>
 *   <li>outer works: the Ossuary (its charnel house and bone pits), the scavengers' camp and six bone obelisks.</li>
 * </ul>
 */
final class ColossusLeviathan extends ColossusDesign {
    // the centreline, in blocks of arc length from the snout's tip
    static final int SKULL = 42, NECK = 107, HEART = NECK + 45, CHEST1 = NECK + 91, TAIL1 = CHEST1 + 70, END = TAIL1 + 68;
    static final int PLATEAU0 = HEART - 28, PLATEAU1 = HEART + 28, HIPS = CHEST1 + 12;
    static final int RIB0 = NECK + 5, RIB1 = CHEST1 + 6;   // the thoracic vertebrae carry the great ribs
    static final int NAVE = 1;                          // the nave's paving above s.y
    static final int WYRM = 28;                         // the Marrow Wyrm's point above s.y (open air from +23 to +36)
    static final int OSS_U = 42, OSS_V = -86, CAMP_U = -78, CAMP_V = 92;
    static final String LOOT = "jaspr:colossus/leviathan";

    // ---- palette --------------------------------------------------------------------------------------------------------
    private static final int BONE_Y = b(216, 0), BONE_X = b(216, 4), BONE_Z = b(216, 8);
    private static final int QUARTZ = b(155, 0), CHISELED = b(155, 1), QP_Y = b(155, 2), QP_Z = b(155, 3), QP_X = b(155, 4);
    private static final int WHITE_T = b(159, 0), LGRAY_T = b(159, 8), BROWN_T = b(159, 12), RED_T = b(159, 14), BLACK_T = b(159, 15);
    private static final int WHITE_C = b(251, 0), END_STONE = b(121), SOUL = b(88), RACK = b(87), FIRE = b(51), GLOW = b(89), MAGMA = b(213);
    private static final int BRICK = b(112), RED_NB = b(215), FENCE_NB = b(113), WART = b(214), GRAVEL = b(13);
    private static final int QSLAB = b(44, 7), QSLAB_TOP = b(44, 15), NBSLAB = b(44, 6), NBSLAB_TOP = b(44, 14);
    private static final int PLANK = b(5, 5), PLANK_S = b(5, 1), DSLAB = b(126, 5), DSLAB_TOP = b(126, 13), FENCE = b(191), SFENCE = b(188);
    private static final int WOOL_BROWN = b(35, 12), WOOL_RED = b(35, 14), WOOL_BLACK = b(35, 15), WOOL_WHITE = b(35, 0);
    private static final int CARPET_RED = b(171, 14), CARPET_BROWN = b(171, 12), CARPET_BLACK = b(171, 15);
    private static final int IRON = b(101), WEB = b(30), CAULDRON = b(118), JACK = b(91), END_ROD = b(198, 1), BOOKS = b(47), CRAFT = b(58);
    private static final int OBSIDIAN = b(49), COAL = b(173), HAY = b(170);
    private static final int SEAL = b(215);             // the Marrow-Vault's seal: red nether brick in the bone wall
    private static final int PIT_COVER = b(155, 1);      // the ossuary's pit covers (chiseled quartz slabs of bone)
    private static final Draw.Mat[] BONES = {bone(0, 7101), bone(4, 7102), bone(8, 7103)};

    private static Draw.Mat bone(int meta, int salt) {
        final int bb = b(216, meta), qp = meta == 0 ? QP_Y : meta == 4 ? QP_X : QP_Z;
        return (x, y, z) -> {
            double q = Draw.rnd(x, y, z, salt);
            return q < 0.04 ? WHITE_T : q < 0.07 ? qp : q < 0.085 ? END_STONE : bb;
        };
    }

    // ---- the plan -------------------------------------------------------------------------------------------------------
    /** One bone: a capsule in local coordinates (absolute y), its block's world axis and its world bounding box. */
    static final class Bone {
        final double u0, y0, v0, u1, y1, v1, r; final int axis, x0, z0, x1, z1;
        Bone(Colossi.Site s, int rot, double u0, double y0, double v0, double u1, double y1, double v1, double r) {
            this.u0 = u0; this.y0 = y0; this.v0 = v0; this.u1 = u1; this.y1 = y1; this.v1 = v1; this.r = r;
            axis = axis(rot, u1 - u0, y1 - y0, v1 - v0);
            double xa = wx(s.x, rot, u0, v0), za = wz(s.z, rot, u0, v0), xb = wx(s.x, rot, u1, v1), zb = wz(s.z, rot, u1, v1);
            x0 = (int) Math.floor(Math.min(xa, xb) - r - 1); x1 = (int) Math.ceil(Math.max(xa, xb) + r + 1);
            z0 = (int) Math.floor(Math.min(za, zb) - r - 1); z1 = (int) Math.ceil(Math.max(za, zb) + r + 1);
        }
    }

    /** A shack of Marrowtown: its box (local), its height, style, roof, door wall and contents. */
    static final class Shack {
        int u0, v0, u1, v1, h, style, roof, door, side; boolean chest, rich, trap, books;
    }

    static final class Plan {
        int rot, us, vs;                                    // the frame; the skull's axis (u) and its snout's tip (v)
        double[] cu, cv, hd;                                // the centreline per block of arc length: u, v, heading
        boolean[] cap;                                      // arc-length blocks over a vertebra (the Spine Road's bone caps)
        double nestU, nestV;                                // the middle of the Coil
        final List<Integer> verts = new ArrayList<>();
        final List<Bone> bones = new ArrayList<>(), skullBones = new ArrayList<>();
        final List<double[]> feet = new ArrayList<>();      // the great ribs' feet: u, side, lateral, broken (1/0)
        final List<Shack> shacks = new ArrayList<>();
        final List<int[]> obelisks = new ArrayList<>();     // u, v, height
        final List<int[]> traps = new ArrayList<>();        // kind, box (local u0 y0 v0 u1 y1 v1), emitter box or gun, block
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** The centrum's height above the floor along the body: the neck rising to the hump over the heart, the tail sinking. */
    static double hc(double t) {
        if (t <= 46) return 12;
        if (t <= PLATEAU0) return 12 + 31 * smooth((t - 46) / (PLATEAU0 - 46.0));
        if (t <= PLATEAU1) return 43;
        if (t <= PLATEAU1 + 37) return 43 - 19 * smooth((t - PLATEAU1) / 37.0);
        if (t <= PLATEAU1 + 90) return 24 - 17 * smooth((t - PLATEAU1 - 37) / 53.0);
        return 7 - 3.5 * smooth((t - PLATEAU1 - 90) / (double) (END - PLATEAU1 - 90));
    }
    /** Half the great ribs' span at the floor. */
    static double span(double t) { return hc(t) * (t < PLATEAU1 + 8 ? 1.0 : 1.0 - (t - PLATEAU1 - 8) * 0.012); }
    static double rc(double t) { return 1.3 + 3.9 * Math.pow(hc(t) / 43, 0.6); }
    static double ridge(double t) { return hc(t) + rc(t) + 2 + 6 * hc(t) / 43; }
    /** The Spine Road's surface (the top of its blocks) above s.y: over the crown, down to the neck, then on the ridge. */
    static double walkH(double t) {
        if (t < 44) return 27.0;
        double rr = ridge(t) - 0.5;
        if (t < 58) return 27.0 + (rr - 27.0) * smooth((t - 44) / 14.0);
        return rr;
    }

    private static double wx(int ox, int rot, double u, double v) { switch (rot & 3) { case 1: return ox - v; case 2: return ox - u; case 3: return ox + v; default: return ox + u; } }
    private static double wz(int oz, int rot, double u, double v) { switch (rot & 3) { case 1: return oz + u; case 2: return oz - v; case 3: return oz - u; default: return oz + v; } }

    /** The bone block's axis (0 Y, 1 X, 2 Z) for a local direction, in world axes. */
    static int axis(int rot, double du, double dy, double dv) {
        double au = Math.abs(du), av = Math.abs(dv), ay = Math.abs(dy);
        if (ay >= au && ay >= av) return 0;
        boolean alongU = au >= av;
        return ((rot & 1) == 0) == alongU ? 1 : 2;
    }

    private static double[] at(Plan p, double t) {
        t = Math.max(0, Math.min(END, t));
        int i = Math.min((int) Math.floor(t), END - 1);
        double q = t - i;
        return new double[]{p.cu[i] + (p.cu[i + 1] - p.cu[i]) * q, p.cv[i] + (p.cv[i + 1] - p.cv[i]) * q, p.hd[i] + (p.hd[i + 1] - p.hd[i]) * q};
    }

    private static void bone(Plan p, Colossi.Site s, double u0, double y0, double v0, double u1, double y1, double v1, double r) {
        p.bones.add(new Bone(s, p.rot, u0, y0, v0, u1, y1, v1, r));
    }
    private static void skullBone(Plan p, Colossi.Site s, double u0, double y0, double v0, double u1, double y1, double v1, double r) {
        p.skullBones.add(new Bone(s, p.rot, u0, y0, v0, u1, y1, v1, r));
    }

    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        int y0 = s.y;
        // the centreline: the skull straight, the neck bending a quarter turn, the chest straight over the heart, the tail
        // sweeping the other way and winding into a coil
        double tailTurn = Math.toRadians(96 + r.nextInt(12)), r0 = 16 + r.nextDouble() * 2, r1 = 6.5 + r.nextDouble();
        int n = END + 1;
        p.cu = new double[n]; p.cv = new double[n]; p.hd = new double[n];
        double x = 0, z = 0, h = Math.PI / 2;
        for (int t = 0; t < n; t++) {
            p.cu[t] = x; p.cv[t] = z; p.hd[t] = h;
            double k = t < SKULL ? 0 : t < NECK ? -(Math.PI / 2) / (NECK - SKULL) : t < CHEST1 ? 0 : t < TAIL1 ? tailTurn / (TAIL1 - CHEST1)
                : 1.0 / (r0 + (r1 - r0) * (t - TAIL1) / (double) (END - TAIL1));
            double hm = h + k * 0.5;
            x += Math.cos(hm); z += Math.sin(hm); h += k;
        }
        double ou = p.cu[HEART], ov = p.cv[HEART];
        for (int t = 0; t < n; t++) { p.cu[t] -= ou; p.cv[t] -= ov; }
        p.us = (int) Math.round(p.cu[0]); p.vs = (int) Math.round(p.cv[0]);
        // the vertebrae, spaced by their size
        for (double t = 45; t <= END - 2; ) {
            int ti = (int) Math.round(t);
            p.verts.add(ti);
            t += Math.max(4, Math.round(2 * rc(t) + 1.6));
        }
        p.cap = new boolean[n];
        for (int ti : p.verts) for (int t = Math.max(0, ti - 2); t <= Math.min(END, ti + 2); t++) p.cap[t] = true;
        // the coil's middle
        double su = 0, sv = 0; int cnt = 0;
        for (int t = TAIL1 + 20; t <= END; t++) { su += p.cu[t]; sv += p.cv[t]; cnt++; }
        p.nestU = su / cnt; p.nestV = sv / cnt;
        vertebrae(p, s, y0);
        ribs(p, s, r, y0);
        limbs(p, s, y0);
        skullBones(p, s, y0);
        town(p, r);
        double[] angles = {0, -45, -100, 55, 105, 150};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i] + (r.nextDouble() - 0.5) * 6), rad = 118 + r.nextInt(6);
            p.obelisks.add(new int[]{(int) Math.round(Math.cos(a) * rad), (int) Math.round(Math.sin(a) * rad), 30 + r.nextInt(12)});
        }
        traps(p, s);
        p.garrisons = complete(garrisons(p, s));
        return p;
    }

    /** The vertebrae: centrum, neural spine (under the Spine Road), transverse processes; spikes along the tail. */
    private void vertebrae(Plan p, Colossi.Site s, int y0) {
        for (int ti : p.verts) {
            double[] c = at(p, ti);
            double tu = Math.cos(c[2]), tv = Math.sin(c[2]), nu = -tv, nv = tu;
            double hcv = hc(ti), rcv = rc(ti), half = rcv * 0.95;
            double[] a0 = at(p, ti - half), a1 = at(p, ti + half);
            bone(p, s, a0[0], y0 + hc(ti - half), a0[1], a1[0], y0 + hc(ti + half), a1[1], rcv);
            bone(p, s, c[0], y0 + hcv + rcv * 0.5, c[1], c[0] + tu * 0.6, y0 + walkH(ti) - 1, c[1] + tv * 0.6, Math.max(1.0, rcv * 0.33));
            double reach = rcv + 2.5 + rcv * 0.5;
            for (int sg = -1; sg <= 1; sg += 2)
                bone(p, s, c[0], y0 + hcv, c[1], c[0] + nu * sg * reach - tu, y0 + hcv - 1.5, c[1] + nv * sg * reach - tv, Math.max(0.8, rcv * 0.24));
            if (ti > CHEST1 + 14 && ti < END - 6) {    // the tail's spikes flank the road
                double w = walkH(ti);
                for (int sg = -1; sg <= 1; sg += 2)
                    bone(p, s, c[0] + nu * sg * 2.4, y0 + w, c[1] + nv * sg * 2.4, c[0] + nu * sg * 3.6 + tu * 2.5, y0 + w + 3.5 + rcv * 0.6, c[1] + nv * sg * 3.6 + tv * 2.5, 0.7);
            }
            if (ti >= 48 && ti < RIB0) {               // the neck's little ribs
                for (int sg = -1; sg <= 1; sg += 2) {
                    double au = c[0] + nu * sg * rcv * 0.8, av = c[1] + nv * sg * rcv * 0.8;
                    double bu = c[0] + nu * sg * (rcv + 3) + tu * 2.5, bv = c[1] + nv * sg * (rcv + 3) + tv * 2.5;
                    double cu2 = c[0] + nu * sg * (rcv + 3.6) + tu * 5, cv2 = c[1] + nv * sg * (rcv + 3.6) + tv * 5;
                    bone(p, s, au, y0 + hcv, av, bu, y0 + hcv - 3.5, bv, 1.0);
                    bone(p, s, bu, y0 + hcv - 3.5, bv, cu2, y0 + hcv - 7.5, cv2, 0.8);
                }
            }
        }
        // the tail's tip: a spade of three bone blades
        double[] c = at(p, END);
        double tu = Math.cos(c[2]), tv = Math.sin(c[2]), nu = -tv, nv = tu;
        for (int k = -1; k <= 1; k++)
            bone(p, s, c[0], y0 + 3, c[1], c[0] + tu * 6 + nu * k * 3.5, y0 + 2.5 + (k == 0 ? 2 : 0), c[1] + tv * 6 + nv * k * 3.5, 1.0);
    }

    /** The great ribs: barrel-vaulted over the nave (a superellipse), sweeping back as they fall; a few broken. */
    private void ribs(Plan p, Colossi.Site s, Random r, int y0) {
        for (int ti : p.verts) {
            if (ti < RIB0 || ti > RIB1) continue;
            double[] c = at(p, ti);
            double tu = Math.cos(c[2]), tv = Math.sin(c[2]), nu = -tv, nv = tu;
            double hcv = hc(ti), rcv = rc(ti);
            double w = span(ti), sweep = 3 + hcv * 0.08;
            for (int sg = -1; sg <= 1; sg += 2) {
                int brk = r.nextInt(100) < 16 ? 7 + r.nextInt(4) : 99;
                double pu = 0, pv = 0, py = 0, fu = 0, fv = 0;
                int segs = 12;
                for (int i = 0; i <= segs; i++) {
                    double phi = (Math.PI / 2) * i / segs;
                    double lat = rcv * 0.7 + (w - rcv * 0.7) * Math.pow(Math.sin(phi), 2.0 / 3);
                    double hh = i == segs ? -2 : hcv * Math.pow(Math.cos(phi), 2.0 / 3);
                    double back = sweep * (1 - Math.max(0, hh) / hcv);
                    double qu = c[0] + tu * back + nu * sg * lat, qv = c[1] + tv * back + nv * sg * lat, qy = y0 + hh;
                    if (i > 0 && i <= brk) bone(p, s, pu, py, pv, qu, qy, qv, 1.5 + 0.9 * Math.max(0, hh) / hcv);
                    pu = qu; pv = qv; py = qy;
                    if (i == segs) { fu = qu; fv = qv; }
                }
                if (brk < 99)        // the broken rib's lower half lies in the dust beside its place
                    bone(p, s, fu - tu * 2, y0 + 1.2, fv - tv * 2, fu + nu * sg * 9 + tu * 5, y0 + 0.8, fv + nv * sg * 9 + tv * 5, 1.4);
                p.feet.add(new double[]{fu, sg, w, brk < 99 ? 1 : 0});
            }
        }
    }

    /** The forelimbs (scapula, humerus, forearm, four clawed digits gripping the floor), the hips and the hind legs. */
    private void limbs(Plan p, Colossi.Site s, int y0) {
        double w = span(HEART - 30);
        for (int sg = -1; sg <= 1; sg += 2) {
            // the shoulder blade lies on the ribs' outside
            bone(p, s, -30, y0 + 20, sg * (w + 3), -24, y0 + w - 3, sg * 31, 2.2);
            bone(p, s, -36, y0 + 22, sg * (w + 3), -30, y0 + w - 5, sg * 30, 2.0);
            bone(p, s, -31, y0 + 19, sg * (w + 4), -31, y0 + 19, sg * (w + 4), 4.0);
            bone(p, s, -31, y0 + 19, sg * (w + 4), -37, y0 + 9, sg * (w + 14), 3.2);
            bone(p, s, -37, y0 + 9, sg * (w + 14), -37, y0 + 9, sg * (w + 14), 3.6);
            bone(p, s, -36, y0 + 9, sg * (w + 13), -43, y0 + 3, sg * (w + 10), 2.1);
            bone(p, s, -39, y0 + 9, sg * (w + 15), -45, y0 + 3, sg * (w + 12), 1.9);
            bone(p, s, -44, y0 + 3, sg * (w + 11), -44, y0 + 3, sg * (w + 11), 2.6);
            for (int dg = 0; dg < 4; dg++) {
                double a = Math.toRadians(5 + dg * 25), du = -Math.cos(a), dl = Math.sin(a);
                double ku = -44, kl = w + 11;
                double k1u = ku + du * 6, k1l = kl + dl * 6, k2u = k1u + du * 5, k2l = k1l + dl * 5, k3u = k2u + du * 5, k3l = k2l + dl * 5;
                bone(p, s, ku, y0 + 3, sg * kl, k1u, y0 + 4.5, sg * k1l, 1.6);
                bone(p, s, k1u, y0 + 4.5, sg * k1l, k2u, y0 + 3.5, sg * k2l, 1.4);
                bone(p, s, k2u, y0 + 3.5, sg * k2l, k3u, y0 - 1.5, sg * k3l, 1.1);
            }
        }
        // the hips and hind legs
        int th = HIPS;
        double[] c = at(p, th);
        double tu = Math.cos(c[2]), tv = Math.sin(c[2]), nu = -tv, nv = tu, hcv = hc(th);
        for (int sg = -1; sg <= 1; sg += 2) {
            double hipU = c[0] + nu * sg * 13, hipV = c[1] + nv * sg * 13;
            bone(p, s, c[0] - tu * 4 + nu * sg * 2, y0 + hcv - 1, c[1] - tv * 4 + nv * sg * 2, hipU - tu, y0 + 12, hipV - tv, 2.0);
            bone(p, s, c[0] + tu * 4 + nu * sg * 2, y0 + hcv - 2, c[1] + tv * 4 + nv * sg * 2, hipU + tu, y0 + 12, hipV + tv, 1.8);
            bone(p, s, hipU, y0 + 12, hipV, hipU, y0 + 12, hipV, 3.0);
            double kneeU = c[0] + nu * sg * 22 - tu * 8, kneeV = c[1] + nv * sg * 22 - tv * 8;
            bone(p, s, hipU, y0 + 12, hipV, kneeU, y0 + 7, kneeV, 2.6);
            bone(p, s, kneeU, y0 + 7, kneeV, kneeU, y0 + 7, kneeV, 2.8);
            double ankU = c[0] + nu * sg * 21 + tu * 2, ankV = c[1] + nv * sg * 21 + tv * 2;
            bone(p, s, kneeU, y0 + 7, kneeV, ankU, y0 + 2.5, ankV, 1.9);
            for (int dg = 0; dg < 3; dg++) {
                double a = Math.toRadians(-20 + dg * 25);
                double du = -tu * Math.cos(a) + nu * sg * Math.sin(a), dv = -tv * Math.cos(a) + nv * sg * Math.sin(a);
                bone(p, s, ankU, y0 + 2.5, ankV, ankU + du * 6, y0 + 3, ankV + dv * 6, 1.3);
                bone(p, s, ankU + du * 6, y0 + 3, ankV + dv * 6, ankU + du * 10, y0 - 1.5, ankV + dv * 10, 1.0);
            }
        }
    }

    /** The skull's outer bones: the horns sweeping back over the neck, the frill, the brows, the cheekbones. */
    private void skullBones(Plan p, Colossi.Site s, int y0) {
        int us = p.us, vs = p.vs;
        double[][] horn = {{39, 7, 24, 2.7}, {47, 10.5, 30, 2.4}, {57, 14.5, 34, 2.0}, {67, 19, 35, 1.6}, {75, 22.5, 32, 1.2}, {80, 24.5, 28, 0.9}};
        for (int sg = -1; sg <= 1; sg += 2)
            for (int i = 0; i + 1 < horn.length; i++)
                skullBone(p, s, us + sg * horn[i][1], y0 + horn[i][2], vs + horn[i][0], us + sg * horn[i + 1][1], y0 + horn[i + 1][2], vs + horn[i + 1][0], horn[i][3]);
        for (int k = -2; k <= 2; k++) {                                                                   // the frill
            if (k == 0) continue;
            skullBone(p, s, us + k * 3.4, y0 + 23 - Math.abs(k), vs + 42, us + k * 6, y0 + 29 - Math.abs(k) * 2, vs + 50, 1.0);
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            skullBone(p, s, us + sg * 8.5, y0 + 24, vs + 19, us + sg * 12.5, y0 + 26, vs + 31, 1.8);             // the brow ridge
            skullBone(p, s, us + sg * 12, y0 + 13, vs + 23, us + sg * 14.5, y0 + 6, vs + 40, 1.8);               // the cheekbone
            skullBone(p, s, us + sg * 14.5, y0 + 5, vs + 40, us + sg * 14.5, y0 + 5, vs + 40, 3.0);              // the jaw's hinge
            skullBone(p, s, us + sg * ramus(29), y0 + 6, vs + 29, us + sg * (ramus(32) + 0.5), y0 + 10, vs + 32, 1.4);   // coronoid
        }
    }

    /** Marrowtown's shacks in two rows on each side of the nave, the street between them; the lookouts keep a gap. */
    private void town(Plan p, Random r) {
        for (int side = -1; side <= 1; side += 2) for (int row = 0; row < 2; row++) {
            int lat0 = row == 0 ? 21 : 31, lat1 = row == 0 ? 27 : 38;
            int u = -44 + r.nextInt(3);
            while (u < 46) {
                int w = 5 + r.nextInt(4), u1 = u + w - 1;
                if (u1 > 48) break;
                int look = side < 0 ? -14 : 16;
                boolean clash = (row == 1 && u1 >= look - 4 && u <= look + 4) || (u <= 3 && u1 >= -3);
                double wt = Math.min(span(HEART + u), span(HEART + u1));
                int top = Math.min(row == 0 ? lat1 - r.nextInt(2) : lat1, (int) Math.floor(wt) - 4);
                int bot = row == 0 ? lat0 : lat0 + r.nextInt(2);
                if (!clash && top - bot >= 4) {
                    Shack k = new Shack();
                    k.u0 = u; k.u1 = u1; k.side = side;
                    k.v0 = side > 0 ? bot : -top; k.v1 = side > 0 ? top : -bot;
                    k.h = 5 + r.nextInt(3) + (r.nextInt(4) == 0 ? 4 : 0);
                    k.style = r.nextInt(3); k.roof = r.nextInt(3);
                    boolean streetHigh = row == 0;           // the street lies outward of the inner row, inward of the outer
                    k.door = (side > 0) == streetHigh ? k.v1 : k.v0;
                    int q = r.nextInt(10);
                    k.chest = q < 4; k.rich = q == 0; k.trap = q == 1 || q == 2; k.books = r.nextInt(5) == 0;
                    p.shacks.add(k);
                }
                u = u1 + 2 + r.nextInt(3);
            }
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        groundworks(f, s, p);
        for (Bone b : p.bones) {
            if (!d.touches(b.x0, b.z0, b.x1, b.z1)) continue;
            f.line(b.u0, b.y0, b.v0, b.u1, b.y1, b.v1, b.r, BONES[b.axis]);
        }
        skull(f, s, p);
        for (Bone b : p.skullBones) {
            if (!d.touches(b.x0, b.z0, b.x1, b.z1)) continue;
            f.line(b.u0, b.y0, b.v0, b.u1, b.y1, b.v1, b.r, BONES[b.axis]);
        }
        spineRoad(f, s, p);
        heart(f, s, p);
        palisade(f, s, p);
        for (Shack k : p.shacks) shack(f, s, k);
        market(f, s);
        lamps(f, s);
        rookery(f, s);
        lookouts(f, s, p);
        hoist(f, s, p);
        nest(f, s, p);
        ossuary(f, s, p);
        camp(f, s, p);
        for (int i = 0; i < p.obelisks.size(); i++) obelisk(f, s, p.obelisks.get(i), i);
        trapMarks(f, s, p);
        f.point(0, s.y + WYRM, 0, "lord:" + s.kind.lord);
        garrisons(f, p.garrisons);
    }

    private static boolean touches(Draw.Frame f, int u0, int v0, int u1, int v1) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        return f.d.touches(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    interface Col { void at(int u, int v, int x, int z); }

    /** Calls c for every local column of the rectangle inside the draw box. */
    static void cols(Draw.Frame f, int u0, int v0, int u1, int v1, Col c) {
        Draw d = f.d;
        int[][] cs = {{d.x0, d.z0}, {d.x1, d.z0}, {d.x0, d.z1}, {d.x1, d.z1}};
        int ua = Integer.MAX_VALUE, ub = Integer.MIN_VALUE, va = Integer.MAX_VALUE, vb = Integer.MIN_VALUE;
        for (int[] q : cs) {
            int lu = lu(f, q[0], q[1]), lv = lv(f, q[0], q[1]);
            ua = Math.min(ua, lu); ub = Math.max(ub, lu); va = Math.min(va, lv); vb = Math.max(vb, lv);
        }
        ua = Math.max(ua, Math.min(u0, u1)); ub = Math.min(ub, Math.max(u0, u1));
        va = Math.max(va, Math.min(v0, v1)); vb = Math.min(vb, Math.max(v0, v1));
        for (int u = ua; u <= ub; u++) for (int v = va; v <= vb; v++) c.at(u, v, f.x(u, v), f.z(u, v));
    }
    static int lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    static int lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }

    /** An axis-aligned ellipsoid given in local radii. */
    static void ell(Draw.Frame f, double u, double y, double v, double ru, double ry, double rv, double shell, Draw.Mat m) {
        boolean swap = (f.rot & 1) == 1;
        f.d.ellipsoid(f.xd(u, v), y, f.zd(u, v), swap ? rv : ru, ry, swap ? ru : rv, shell, m);
    }

    /** A domed chamber: an ellipsoid's upper part carved over a flat floor block at yFloor, inside the given columns. */
    static void chamber(Draw.Frame f, double cu, double cy, double cv, double ru, double ry, double rv, int yFloor, Draw.Mat floor, int u0, int v0, int u1, int v1) {
        cols(f, Math.max(u0, (int) Math.floor(cu - ru)), Math.max(v0, (int) Math.floor(cv - rv)), Math.min(u1, (int) Math.ceil(cu + ru)), Math.min(v1, (int) Math.ceil(cv + rv)),
            (u, v, x, z) -> {
                double du = (u - cu) / ru, dv = (v - cv) / rv, hh = 1 - du * du - dv * dv;
                if (hh <= 0) return;
                int top = (int) Math.floor(cy + ry * Math.sqrt(hh));
                if (top < yFloor + 2) return;
                if (floor != null) f.d.set(x, yFloor, z, floor.at(x, yFloor, z));
                for (int y = yFloor + 1; y <= top; y++) f.d.set(x, y, z, 0);
            });
    }

    // ---- the ground: the nave's paving, the skull's apron, the yards, the trails --------------------------------------------
    private void groundworks(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y, ny = y0 + NAVE;
        Draw.Mat fill = Draw.mix(SOUL, RACK, 0.3, s.salt + 11);
        // the nave: soul sand and bone dust, nether-brick streets, the Heart Court's rings and spokes
        if (touches(f, -48, -46, 52, 46))
            cols(f, -48, -46, 52, 46, (u, v, x, z) -> {
                for (int y = y0 - 3; y < ny; y++) f.d.set(x, y, z, fill.at(x, y, z));
                int av = Math.abs(v), top;
                double q = Draw.rnd(x, 7, z, s.salt + 13);
                if (Math.abs(u) <= 25 && av <= 21) {
                    double rr = Math.sqrt(u * (double) u + 1.3 * v * (double) v);
                    int ring = (int) rr;
                    double ang = Math.atan2(v * 1.3, u), spoke = Math.abs(Math.sin(ang * 4));
                    top = ring == 14 || ring == 21 || ring == 22 ? BONE_Y : (rr > 14 && rr < 21 && spoke < 0.12) ? RED_NB : q < 0.08 ? MAGMA : SOUL;
                } else if (av >= 28 && av <= 30 && u > -46 && u < 50) top = q < 0.15 ? RED_NB : BRICK;
                else if (Math.abs(u) <= 2 || (u < -26 && av <= 1)) top = BRICK;
                else top = q < 0.1 ? WHITE_T : q < 0.2 ? GRAVEL : q < 0.26 ? BONE_Y : SOUL;
                f.d.set(x, ny, z, top);
                for (int y = ny + 1; y <= ny + 6; y++) f.d.set(x, y, z, 0);
            });
        // the skull's apron
        int us = p.us, vs = p.vs;
        if (touches(f, us - 18, vs - 14, us + 18, vs + 46))
            cols(f, us - 18, vs - 14, us + 18, vs + 46, (u, v, x, z) -> {
                for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, fill.at(x, y, z));
                double q = Draw.rnd(x, 7, z, s.salt + 17);
                f.d.set(x, y0, z, q < 0.15 ? WHITE_T : q < 0.25 ? GRAVEL : SOUL);
                for (int y = y0 + 1; y <= y0 + 4; y++) f.d.set(x, y, z, 0);
            });
        // the ossuary's yard and the camp's ground
        if (touches(f, OSS_U - 24, OSS_V - 19, OSS_U + 24, OSS_V + 19))
            cols(f, OSS_U - 24, OSS_V - 19, OSS_U + 24, OSS_V + 19, (u, v, x, z) -> {
                for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, fill.at(x, y, z));
                double q = Draw.rnd(x, 7, z, s.salt + 19);
                f.d.set(x, y0, z, q < 0.3 ? GRAVEL : q < 0.5 ? WHITE_T : q < 0.6 ? BONE_Y : SOUL);
                for (int y = y0 + 1; y <= y0 + 5; y++) f.d.set(x, y, z, 0);
            });
        if (touches(f, CAMP_U - 22, CAMP_V - 22, CAMP_U + 22, CAMP_V + 22))
            cols(f, CAMP_U - 22, CAMP_V - 22, CAMP_U + 22, CAMP_V + 22, (u, v, x, z) -> {
                double du = u - CAMP_U, dv = v - CAMP_V;
                if (du * du + dv * dv > 21.5 * 21.5) return;
                for (int y = y0 - 3; y < y0; y++) f.d.set(x, y, z, fill.at(x, y, z));
                double q = Draw.rnd(x, 7, z, s.salt + 23);
                f.d.set(x, y0, z, q < 0.2 ? GRAVEL : q < 0.3 ? BROWN_T : SOUL);
                for (int y = y0 + 1; y <= y0 + 5; y++) f.d.set(x, y, z, 0);
            });
        // trails of trodden bone dust between them
        int[][] trails = {{CAMP_U, CAMP_V - 21, -54, 34}, {-54, 34, -49, 12}, {OSS_U, OSS_V + 18, 30, -47}, {us + 12, vs - 9, -52, -42},
            {-52, -42, -49, -14}, {53, -4, 58, -15}, {58, -15, 46, -68}};
        for (int[] tr : trails) trail(f, s, tr[0], tr[1], tr[2], tr[3]);
    }

    private void trail(Draw.Frame f, Colossi.Site s, int u0, int v0, int u1, int v1) {
        if (!touches(f, Math.min(u0, u1) - 2, Math.min(v0, v1) - 2, Math.max(u0, u1) + 2, Math.max(v0, v1) + 2)) return;
        double du = u1 - u0, dv = v1 - v0, len2 = du * du + dv * dv;
        cols(f, Math.min(u0, u1) - 2, Math.min(v0, v1) - 2, Math.max(u0, u1) + 2, Math.max(v0, v1) + 2, (u, v, x, z) -> {
            double t = Math.max(0, Math.min(1, ((u - u0) * du + (v - v0) * dv) / len2));
            double qu = u0 + du * t - u, qv = v0 + dv * t - v;
            if (qu * qu + qv * qv > 2.3 * 2.3) return;
            if (Math.abs(u) <= 48 && Math.abs(v) <= 46) return;       // the nave is paved already
            int y = s.floorAt(x, z);
            double q = Draw.rnd(x, 3, z, s.salt + 29);
            f.d.set(x, y, z, q < 0.45 ? GRAVEL : q < 0.75 ? LGRAY_T : WHITE_T);
        });
    }

    // ---- the skull ------------------------------------------------------------------------------------------------------
    /** Half the snout's width at a (blocks from the snout's tip). */
    static double snoutHalf(double a) {
        double hw = 4.5 + 0.28 * a;
        if (a < 3) hw *= Math.sqrt(Math.max(0, 1 - Math.pow((3 - a) / 3.6, 2)));
        return hw;
    }
    /** The snout's top above s.y at a. */
    static double snoutTop(double a) { return 17.5 + 0.22 * a; }
    /** The middle of the lower jaw's ramus (|b|) at a, from the broken chin to the hinge. */
    static double ramus(double a) { return 3.5 + 11 * (a - 2) / 38.0; }

    /**
     * The skull (in its own frame: a from the snout's tip back along the local v axis, b across it): the upper jaw over
     * the open mouth (the Jaw-Hall), its nasal passage under the nostrils, the antorbital windows, the burning eyes, the
     * brow, the cranium with the Marrow Shrine and the Marrow-Vault, the temporal windows, the lower jaw and the teeth.
     */
    private void skull(Draw.Frame f, Colossi.Site s, Plan p) {
        int us = p.us, vs = p.vs, y0 = s.y;
        if (!touches(f, us - 30, vs - 12, us + 30, vs + 84)) return;
        Draw.Mat sk = BONES[axis(p.rot, 0, 0, 1)];
        // the mass: the upper jaw over the mouth, the brow and cheeks, the skull's base, the cranium, the condyle
        cols(f, us - 16, vs - 1, us + 16, vs + 46, (u, v, x, z) -> {
            int b = u - us, a = v - vs, ab = Math.abs(b);
            if (a >= 0 && a <= 28) {
                double hw = snoutHalf(a), top = snoutTop(a);
                if (ab <= hw) for (int yy = 13; yy <= (int) top; yy++) if (!rounded(ab, yy, hw, top, 3)) f.d.set(x, y0 + yy, z, sk.at(x, y0 + yy, z));
            }
            if (a >= 21 && a <= 34 && ab <= 13.5) for (int yy = 9; yy <= 25; yy++) if (!rounded(ab, yy, 13.5, 25, 4.5)) f.d.set(x, y0 + yy, z, sk.at(x, y0 + yy, z));
            if (a >= 24 && a <= 44 && ab <= 11) for (int yy = 0; yy <= (a >= 30 ? 10 : 8); yy++) f.d.set(x, y0 + yy, z, sk.at(x, y0 + yy, z));
        });
        ell(f, us, y0 + 17, vs + 35, 12.5, 10.5, 10, 0, sk);
        f.line(us, y0 + 12, vs + 42, us, y0 + 12, vs + 46, 3, sk);
        // the mouth: the Jaw-Hall under the snout (under the brow between the rami), the throat behind it
        cols(f, us - 16, vs, us + 16, vs + 32, (u, v, x, z) -> {
            int b = u - us, a = v - vs, ab = Math.abs(b);
            if (a >= 1 && a <= 20 && ab <= snoutHalf(a) + 1) for (int yy = 1; yy <= 12; yy++) f.d.set(x, y0 + yy, z, 0);
            else if (a > 20 && a <= 26 && ab <= ramus(a)) for (int yy = 1; yy <= 12; yy++) f.d.set(x, y0 + yy, z, 0);
            else if (a > 26 && a <= 31 && ab <= 5) for (int yy = 1; yy <= 6; yy++) f.d.set(x, y0 + yy, z, 0);
            if (a <= 31 && ab <= 14) f.d.set(x, y0, z, Draw.rnd(x, 1, z, s.salt + 31) < 0.3 ? BONE_Y : SOUL);
        });
        // the nasal passage inside the snout, the nostrils over its end, the antorbital windows into it
        cols(f, us - 14, vs, us + 14, vs + 26, (u, v, x, z) -> {
            int b = u - us, a = v - vs, ab = Math.abs(b);
            double hw = snoutHalf(a), top = snoutTop(a);
            if (a >= 4 && ab <= hw - 2.5) for (int yy = 15; yy <= (int) top - 2; yy++) f.d.set(x, y0 + yy, z, 0);
            if (a >= 1 && a <= 4 && ab >= 1 && ab <= 3) for (int yy = 15; yy <= 21; yy++) f.d.set(x, y0 + yy, z, 0);
            if (ab >= hw - 3 && ab <= hw + 1) for (int yy = 14; yy <= 19; yy++) {
                double da = (a - 16) / 4.5, dy = (yy - 16.5) / 2.3;
                if (da * da + dy * dy <= 1) f.d.set(x, y0 + yy, z, 0);
            }
        });
        // the Marrow Shrine in the cranium; the Marrow-Vault behind its bone wall, sealed until the Wyrm falls
        chamber(f, us, y0 + 14, vs + 31, 10, 9.5, 7, y0 + 8, Draw.of(BONE_Y), us - 12, vs + 23, us + 12, vs + 34);
        f.box(us - 4, y0 + 9, vs + 36, us + 4, y0 + 14, vs + 40, Draw.AIR);
        f.box(us - 4, y0 + 8, vs + 36, us + 4, y0 + 8, vs + 40, Draw.of(CHISELED));
        f.box(us - 1, y0 + 9, vs + 35, us + 1, y0 + 11, vs + 35, SEAL);
        f.box(us - 2, y0 + 12, vs + 35, us + 2, y0 + 12, vs + 35, Draw.of(CHISELED));
        // the eyes: sockets through the brow into the shrine, fire deep in them; the temporal windows behind them
        for (int sg = -1; sg <= 1; sg += 2) {
            for (int bb = 8; bb <= 15; bb++) for (int a = 21; a <= 30; a++) for (int yy = 14; yy <= 24; yy++) {
                double da = a - 25.5, dy = yy - 19;
                if (da * da + dy * dy <= 4.5 * 4.5) f.set(us + sg * bb, y0 + yy, vs + a, 0);
            }
            f.set(us + sg * 10, y0 + 15, vs + 25, RACK); f.set(us + sg * 10, y0 + 16, vs + 25, FIRE);
            f.set(us + sg * 11, y0 + 15, vs + 26, MAGMA);
            for (int bb = 10; bb <= 16; bb++) for (int a = 31; a <= 39; a++) for (int yy = 9; yy <= 16; yy++) {
                double da = (a - 35) / 3.8, dy = (yy - 12.5) / 2.6;
                if (da * da + dy * dy <= 1) f.set(us + sg * bb, y0 + yy, vs + a, 0);
            }
            for (int yy = 15; yy <= 18; yy++) f.set(us + sg * 2, y0 + yy, vs + 1, b(65, f.facing(Draw.SOUTH)));   // out by a nostril
            f.set(us + sg * 2, y0 + 14, vs + 4, MAGMA);
        }
        // the gullet: a stair from the Jaw-Hall up through the shrine's floor
        for (int k = 0; k <= 7; k++) {
            int a = 18 + k, yy = 1 + k;
            for (int b = -1; b <= 1; b++) f.set(us + b, y0 + yy, vs + a, f.stair(156, 2, false));
            f.box(us - 1, y0 + yy + 1, vs + a, us + 1, y0 + yy + 4, vs + a, Draw.AIR);
            f.box(us - 1, y0, vs + a, us + 1, y0 + yy - 1, vs + a, sk);
        }
        // a ladder down from the nasal passage into the shrine; the crack in the crown and its ladder
        f.box(us - 3, y0 + 9, vs + 26, us - 3, y0 + 15, vs + 26, Draw.of(QP_Y));
        for (int y = y0 + 9; y <= y0 + 15; y++) f.set(us - 3, y, vs + 25, b(65, f.facing(Draw.NORTH)));
        f.box(us + 3, y0 + 9, vs + 32, us + 3, y0 + 26, vs + 32, Draw.of(QP_Y));
        f.box(us + 3, y0 + 23, vs + 31, us + 4, y0 + 28, vs + 31, Draw.AIR);
        for (int y = y0 + 9; y <= y0 + 26; y++) f.set(us + 3, y, vs + 31, b(65, f.facing(Draw.NORTH)));
        // the lower jaw: two rami (the chin broken between them is the hall's door), the teeth, the great fangs
        cols(f, us - 16, vs, us + 16, vs + 41, (u, v, x, z) -> {
            int b = u - us, a = v - vs;
            if (a < 1 || a > 40 || Math.abs(Math.abs(b) - ramus(a)) > 1.6) return;
            int h = a < 10 ? 5 : 6;
            for (int yy = 0; yy <= h; yy++) f.d.set(x, y0 + yy, z, sk.at(x, y0 + yy, z));
        });
        for (int sg = -1; sg <= 1; sg += 2) {
            for (int a = 5; a <= 21; a += 4) {
                int b = (int) Math.round(ramus(a)) * sg, tall = 9 + (a % 8 == 1 ? 1 : 0);
                f.box(us + b, y0 + 7, vs + a, us + b, y0 + tall - 1, vs + a, Draw.of(BONE_Y));
                f.set(us + b, y0 + tall, vs + a, WHITE_C);
            }
            for (int a = 6; a <= 18; a += 4) {
                int b = (int) Math.round(snoutHalf(a) - 1) * sg;
                f.box(us + b, y0 + 9, vs + a, us + b, y0 + 12, vs + a, Draw.of(BONE_Y));
                f.set(us + b, y0 + 8, vs + a, WHITE_C);
            }
            f.box(us + sg * 3, y0 + 7, vs + 3, us + sg * 4, y0 + 12, vs + 4, Draw.of(BONE_Y));
            f.box(us + sg * 3, y0 + 4, vs + 3, us + sg * 3, y0 + 6, vs + 3, Draw.of(BONE_Y));
            f.set(us + sg * 3, y0 + 3, vs + 3, WHITE_C);
        }
        // the tongue: a floor of bone slabs over a pit of embers (it gives way), a ladder out
        f.box(us - 3, y0 - 6, vs + 10, us + 3, y0 - 1, vs + 16, Draw.of(BRICK));
        f.box(us - 2, y0 - 5, vs + 11, us + 2, y0 - 1, vs + 15, Draw.AIR);
        f.box(us - 2, y0 - 6, vs + 11, us + 2, y0 - 6, vs + 15, Draw.mix(SOUL, MAGMA, 0.4, s.salt + 33));
        f.box(us - 2, y0, vs + 11, us + 2, y0, vs + 15, Draw.of(QSLAB_TOP));
        for (int y = y0 - 5; y <= y0 - 1; y++) f.set(us - 2, y, vs + 13, b(65, f.facing(Draw.EAST)));
        // the nose stair: the scavengers' flight up the skull's side onto the snout; steps from the snout to the brow
        for (int k = 0; k <= 18; k++) {
            int a = -8 + k, yy = 1 + k;
            if (k % 4 == 1) { f.box(us + 9, y0, vs + a, us + 9, y0 + yy - 1, vs + a, Draw.of(BONE_Y)); f.box(us + 11, y0, vs + a, us + 11, y0 + yy - 1, vs + a, Draw.of(BONE_Y)); }
            for (int b = 9; b <= 11; b++) f.set(us + b, y0 + yy, vs + a, f.stair(114, 2, false));
            f.box(us + 9, y0 + yy + 1, vs + a, us + 11, y0 + yy + 3, vs + a, Draw.AIR);
            f.set(us + 12, y0 + yy, vs + a, Draw.rnd(a, 5, 0, s.salt) < 0.5 ? BONE_Y : BRICK);
            f.set(us + 12, y0 + yy + 1, vs + a, FENCE_NB);
            if (k % 6 == 3) f.set(us + 12, y0 + yy + 2, vs + a, END_ROD);
        }
        f.box(us + 6, y0 + 19, vs + 11, us + 11, y0 + 19, vs + 12, Draw.of(BRICK));
        f.box(us + 11, y0, vs + 12, us + 11, y0 + 18, vs + 12, Draw.of(BONE_Y));
        f.box(us + 6, y0 + 20, vs + 11, us + 11, y0 + 22, vs + 12, Draw.AIR);
        for (int k = 0; k < 4; k++) {
            int a = 18 + k, yy = 22 + k;
            f.box(us - 1, y0 + 20, vs + a, us + 1, y0 + yy - 1, vs + a, sk);
            for (int b = -1; b <= 1; b++) f.set(us + b, y0 + yy, vs + a, f.stair(156, 2, false));
        }
        // light: the hall, the nasal passage, the shrine, the vault
        for (int a = 6; a <= 18; a += 6) f.set(us, y0 + 12, vs + a, GLOW);
        for (int sg = -1; sg <= 1; sg += 2) { f.set(us + sg * 5, y0 + 1, vs + 19, RACK); f.set(us + sg * 5, y0 + 2, vs + 19, FIRE); }
        f.set(us, y0 + 18, vs + 14, GLOW);
        f.set(us - 5, y0 + 23, vs + 30, GLOW); f.set(us + 5, y0 + 23, vs + 30, GLOW); f.set(us, y0 + 24, vs + 31, GLOW); f.set(us, y0 + 15, vs + 38, GLOW);
        // the shrine: a bone altar and its fire, the skulls of the scavengers' dead
        f.box(us - 2, y0 + 9, vs + 29, us + 2, y0 + 9, vs + 31, Draw.of(QUARTZ));
        f.set(us, y0 + 10, vs + 30, RACK); f.set(us, y0 + 11, vs + 30, FIRE);
        f.skull(us - 2, y0 + 10, vs + 29, 0, 8); f.skull(us + 2, y0 + 10, vs + 29, 0, 8);
        f.set(us - 7, y0 + 9, vs + 29, COAL); f.set(us + 7, y0 + 9, vs + 29, COAL);
        // the vault's hoard; the shrine's, the throat's, the hall's and the passage's chests
        for (int b = -3; b <= 3; b += 3) f.chest(us + b, y0 + 9, vs + 40, Draw.NORTH, LOOT + "_vault");
        f.chest(us - 4, y0 + 9, vs + 38, Draw.EAST, LOOT + "_vault");
        f.chest(us + 4, y0 + 9, vs + 38, Draw.WEST, LOOT + "_rich");
        f.chest(us - 9, y0 + 9, vs + 31, Draw.EAST, LOOT + "_rich");
        f.chest(us - 3, y0 + 1, vs + 31, Draw.NORTH, LOOT + "_rich");
        f.chest(us + 7, y0 + 1, vs + 21, Draw.WEST, LOOT);
        f.chest(us, y0 + 15, vs + 4, Draw.SOUTH, LOOT);
        f.spawner(us + 3, y0 + 1, vs + 28, "brimstone_spider");
        f.wallSign(us + 3, y0 + 3, vs, Draw.NORTH, "THE LEVIATHAN'S", "BONES", "Enter by the", "broken chin");
        f.wallSign(us, y0 + 13, vs + 34, Draw.NORTH, "The Marrow-", "Vault opens", "when the Wyrm", "falls");
        f.wallSign(us - 2, y0 + 2, vs + 21, Draw.WEST, "The gullet", "climbs to the", "Marrow Shrine", "");
    }

    private static boolean rounded(int ab, int yy, double hw, double top, double rr) {
        double ex = ab - (hw - rr), ey = yy - (top - rr);
        return ex > 0 && ey > 0 && ex * ex + ey * ey > rr * rr;
    }

    // ---- the Spine Road -------------------------------------------------------------------------------------------------
    /** The road along the neural spines' tops: bone caps over the vertebrae, plank bridges railed between them, bone lamps. */
    private void spineRoad(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        Draw d = f.d;
        Draw.Mat cap = BONES[0];
        for (int pass = 0; pass < 2; pass++)
            for (int k = 2 * 34; k <= 2 * END; k++) {
                double t = k * 0.5;
                double[] c = at(p, t);
                double cx = f.xd(c[0], c[1]), cz = f.zd(c[0], c[1]);
                if (cx < d.x0 - 4 || cx > d.x1 + 4 || cz < d.z0 - 4 || cz > d.z1 + 4) continue;
                double h = walkH(t);
                int base = y0 + (int) Math.floor(h);
                boolean half = h - Math.floor(h) >= 0.5, onCap = p.cap[(int) Math.round(t)] || t < 44;
                double nu = -Math.sin(c[2]), nv = Math.cos(c[2]);
                if (pass == 0) {
                    for (int l = -2; l <= 2; l++) {
                        int u = (int) Math.round(c[0] + nu * l * 0.6), v = (int) Math.round(c[1] + nv * l * 0.6);
                        f.set(u, base, v, onCap ? cap.at(f.x(u, v), base, f.z(u, v)) : PLANK);
                        f.set(u, base + 1, v, half ? (onCap ? QSLAB : DSLAB) : 0);
                        f.set(u, base + 2, v, 0);
                    }
                } else if (!onCap && t < TAIL1 && t > 44) {
                    for (int sg = -1; sg <= 1; sg += 2) {
                        int u = (int) Math.round(c[0] + nu * sg * 2.3), v = (int) Math.round(c[1] + nv * sg * 2.3);
                        f.set(u, base + 1, v, FENCE_NB);
                        if (half) f.set(u, base + 2, v, FENCE_NB);
                    }
                } else if (onCap && k % 2 == 0 && p.verts.contains((int) t) && ((int) t) % 3 == 0) {
                    int u = (int) Math.round(c[0] + nu * 1.2), v = (int) Math.round(c[1] + nv * 1.2);
                    f.set(u, base + (half ? 2 : 1), v, END_ROD);
                }
            }
    }

    // ---- the Heart ------------------------------------------------------------------------------------------------------
    /** The fossil Heart on the court: ventricles and atrium of flesh turned to stone, arteries arching to the floor, the chamber within. */
    private void heart(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -28, -26, 28, 26)) return;
        int y0 = s.y + NAVE;
        Draw.Mat flesh = (x, y, z) -> {
            double q = Draw.rnd(x, y, z, s.salt + 301), n = Draw.noise(x * 0.8 + y * 0.6, z * 0.8 - y * 0.4, 5, s.salt + 303);
            return n > 0.62 ? MAGMA : q < 0.4 ? WART : q < 0.65 ? RED_T : RED_NB;
        };
        // veins over the court, then the heart
        double[][] veins = {{8, 2, 22, 10}, {-6, 8, -18, 20}, {-9, -4, -24, -10}, {5, -9, 16, -20}, {11, -2, 24, -4}};
        for (double[] vn : veins) f.line(vn[0], y0 + 1, vn[1], vn[2], y0 + 0.5, vn[3], 1.2, flesh);
        ell(f, 0, y0 + 9, 1, 12, 8.5, 10, 0, flesh);
        ell(f, -7, y0 + 13, -7, 7, 6, 6, 0, flesh);
        ell(f, 6, y0 + 7, -6, 6, 6, 5, 0, flesh);
        // the aorta's arch and the pulmonary trunk (all below the Wyrm's open air)
        double[][] aorta = {{2, 15, 2}, {6, 19, 4}, {12, 19, 6}, {17, 13, 8}, {20, 4, 9}, {21, -1, 9}};
        for (int i = 0; i + 1 < aorta.length; i++) f.line(aorta[i][0], y0 + aorta[i][1], aorta[i][2], aorta[i + 1][0], y0 + aorta[i + 1][1], aorta[i + 1][2], 2.3, flesh);
        double[][] trunk = {{-3, 14, 4}, {-8, 18, 7}, {-14, 16, 9}, {-18, 8, 10}, {-19, -1, 10}};
        for (int i = 0; i + 1 < trunk.length; i++) f.line(trunk[i][0], y0 + trunk[i][1], trunk[i][2], trunk[i + 1][0], y0 + trunk[i + 1][1], trunk[i + 1][2], 1.9, flesh);
        // the vena cava lying west, the passage through it into the chamber
        f.line(-22, y0 + 2.5, 0, -6, y0 + 5, 0, 3.0, flesh);
        chamber(f, 0, y0 + 9, 1, 9.5, 7, 7.5, y0 + 3, Draw.mix(RED_NB, MAGMA, 0.12, s.salt + 307), -12, -10, 12, 12);
        for (int u = -23; u <= -8; u++) {
            int fy = y0 + Math.max(0, Math.min(3, (u + 14) / 2));
            f.box(u, fy + 1, -1, u, fy + 3, 1, Draw.AIR);
            f.box(u, fy, -1, u, fy, 1, Draw.of(RED_NB));
        }
        // the heart-shrine: a dais of magma and wart, the flames' vents, its keeper's things
        f.box(-1, y0 + 4, 4, 1, y0 + 4, 6, Draw.of(WART));
        f.set(0, y0 + 5, 5, MAGMA);
        f.set(-5, y0 + 3, -2, MAGMA); f.set(5, y0 + 3, 2, MAGMA); f.set(3, y0 + 3, -3, MAGMA);
        f.set(0, y0 + 16, 1, GLOW); f.set(-6, y0 + 13, 4, GLOW); f.set(5, y0 + 13, -3, GLOW);
        f.chest(-4, y0 + 4, 7, Draw.NORTH, LOOT + "_rich");
        f.chest(9, y0 + 4, 1, Draw.WEST, LOOT);
        f.spawner(4, y0 + 4, -3, "magma_cube");
        f.wallSign(0, y0 + 6, 8, Draw.NORTH, "Here beat the", "heart of the", "Leviathan", "");
        // the court's braziers and the Wyrm's warning
        int[][] braz = {{-22, -17}, {22, -17}, {22, 17}, {-22, 17}};
        for (int[] q : braz) {
            f.box(q[0], y0 + 1, q[1], q[0], y0 + 3, q[1], Draw.of(BONE_Y));
            f.set(q[0], y0 + 4, q[1], RACK); f.set(q[0], y0 + 5, q[1], FIRE);
            f.skull(q[0] + 1, y0 + 1, q[1], 0, 4);
        }
        f.wallSign(-23, y0 + 2, -17, Draw.WEST, "THE HEART COURT", "The Marrow Wyrm", "nests in the", "ribs above");
    }

    // ---- Marrowtown ------------------------------------------------------------------------------------------------------
    /** The palisades between the ribs' feet, a gate in every other bay. */
    private void palisade(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -56, -52, 56, 52)) return;
        int y = s.y + NAVE;
        for (int sg = -1; sg <= 1; sg += 2) {
            List<double[]> fs = new ArrayList<>();
            for (double[] q : p.feet) if (q[1] == sg && q[0] <= CHEST1 - HEART) fs.add(q);
            for (int i = 0; i + 1 < fs.size(); i++) {
                double[] a = fs.get(i), bq = fs.get(i + 1);
                int ua = (int) Math.round(a[0]) + 2, ub = (int) Math.round(bq[0]) - 2;
                int gate = i % 2 == 0 ? (ua + ub) / 2 : Integer.MIN_VALUE;
                for (int u = ua; u <= ub; u++) {
                    double t = (u - a[0]) / Math.max(1, bq[0] - a[0]);
                    int lat = (int) Math.round(a[2] + (bq[2] - a[2]) * t) - 1, v = sg * lat;
                    if (Math.abs(u - gate) <= 2) {
                        if (Math.abs(u - gate) == 2) f.box(u, y + 1, v, u, y + 6, v, Draw.of(BONE_Y));
                        else f.set(u, y + 6, v, BONE_Y);
                        continue;
                    }
                    f.box(u, y + 1, v, u, y + 3, v, Draw.mix(BRICK, BONE_Y, 0.35, s.salt + 41));
                    f.set(u, y + 4, v, Math.floorMod(u, 3) == 0 ? BONE_Y : FENCE);
                    if (Math.floorMod(u, 6) == 0) { f.set(u, y + 5, v, FENCE); f.skull(u, y + 6, v, 0, sg > 0 ? 0 : 8); }
                }
            }
        }
    }

    private void shack(Draw.Frame f, Colossi.Site s, Shack k) {
        if (!touches(f, k.u0 - 2, k.v0 - 2, k.u1 + 2, k.v1 + 2)) return;
        int y = s.y + NAVE, top = y + k.h;
        Draw.Mat wall = k.style == 0 ? Draw.mix(BONE_Y, WHITE_T, 0.15, s.salt + 51) : k.style == 1 ? Draw.mix(BRICK, RED_NB, 0.25, s.salt + 52)
            : Draw.mix(PLANK, PLANK_S, 0.2, s.salt + 53);
        Draw.Mat post = Draw.of(k.style == 2 ? BONE_Y : k.style == 0 ? BRICK : BONE_Y);
        f.box(k.u0, y, k.v0, k.u1, top, k.v1, wall);
        f.box(k.u0 + 1, y + 1, k.v0 + 1, k.u1 - 1, top - 1, k.v1 - 1, Draw.AIR);
        f.box(k.u0 + 1, y, k.v0 + 1, k.u1 - 1, y, k.v1 - 1, Draw.of(k.style == 1 ? RED_NB : PLANK));
        for (int[] c : new int[][]{{k.u0, k.v0}, {k.u1, k.v0}, {k.u0, k.v1}, {k.u1, k.v1}}) f.box(c[0], y, c[1], c[0], top, c[1], post);
        int mu = (k.u0 + k.u1) / 2, mv = (k.v0 + k.v1) / 2;
        boolean two = k.h >= 9;
        if (two) {           // an upper floor, a ladder up the west wall
            f.box(k.u0 + 1, y + 5, k.v0 + 1, k.u1 - 1, y + 5, k.v1 - 1, Draw.of(PLANK));
            f.set(k.u0 + 1, y + 5, mv, 0);
            for (int yy = y + 1; yy <= y + 5; yy++) f.set(k.u0 + 1, yy, mv, b(65, f.facing(Draw.EAST)));
            f.set(mu, y + 7, k.door, IRON);
        }
        // the door and the windows
        boolean wide = k.u1 - k.u0 >= 6;
        f.box(mu, y + 1, k.door, mu + (wide ? 1 : 0), y + 3, k.door, Draw.AIR);
        int back = k.door == k.v0 ? k.v1 : k.v0;
        f.set(k.u0, y + 3, mv, FENCE); f.set(k.u1, y + 3, mv, FENCE);
        f.set(mu, y + 3, back, IRON);
        // the roof
        if (k.roof == 0) {
            f.box(k.u0, top, k.v0, k.u1, top, k.v1, Draw.of(PLANK));
            f.walls(k.u0, top + 1, k.v0, k.u1, top + 1, k.v1, Draw.of(FENCE));
        } else if (k.roof == 1) {
            f.box(k.u0 - 1, top + 1, k.v0 - 1, k.u1 + 1, top + 1, k.v1 + 1, Draw.of(NBSLAB));
        } else {
            int wool = k.style == 1 ? WOOL_RED : k.style == 0 ? WOOL_BROWN : WOOL_BLACK;
            for (int q = 0; ; q++) {
                int va = k.v0 - 1 + q, vb = k.v1 + 1 - q;
                if (va > vb) break;
                f.box(k.u0 - 1, top + 1 + q, va, k.u1 + 1, top + 1 + q, vb, Draw.of(wool));
            }
        }
        f.set(mu, top - 1, mv, GLOW);
        // what the scavengers keep
        int inner = back == k.v0 ? k.v0 + 1 : k.v1 - 1, face = back == k.v0 ? Draw.SOUTH : Draw.NORTH;
        f.set(k.u1 - 1, y + 1, inner, Draw.rnd(k.u0, 1, k.v0, 7) < 0.5 ? CAULDRON : CRAFT);
        if (k.books) f.box(k.u0 + 1, y + 1, inner, k.u0 + 1, y + 2, inner, Draw.of(BOOKS));
        else f.set(k.u0 + 1, y + 1, inner, BONE_Y);
        f.set(k.u1 - 1, top - 1, inner, WEB);
        if (k.chest) f.chest(mu, y + 1, inner, face, LOOT + (k.rich ? "_rich" : "") + (k.trap ? "!trap" : ""));
    }

    /** The market under the shoulders: stalls of fence posts and awnings around the road to the court. */
    private void market(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -46, -20, -28, 20)) return;
        int y = s.y + NAVE;
        int[][] stalls = {{-42, -14}, {-35, -14}, {-42, 9}, {-35, 9}, {-42, -6}, {-42, 3}};
        int[] awn = {CARPET_RED, CARPET_BROWN, CARPET_BLACK, CARPET_RED, CARPET_BROWN, CARPET_RED};
        for (int i = 0; i < stalls.length; i++) {
            int u = stalls[i][0], v = stalls[i][1];
            for (int[] c : new int[][]{{u, v}, {u + 4, v}, {u, v + 4}, {u + 4, v + 4}}) f.box(c[0], y + 1, c[1], c[0], y + 3, c[1], Draw.of(FENCE));
            f.box(u, y + 4, v, u + 4, y + 4, v + 4, Draw.of(i % 2 == 0 ? WOOL_BROWN : WOOL_RED));
            f.box(u, y + 5, v, u + 4, y + 5, v + 4, Draw.of(awn[i]));
            f.box(u + 1, y + 1, v + 1, u + 3, y + 1, v + 1, Draw.of(DSLAB_TOP));
            f.set(u + 2, y + 2, v + 1, i % 3 == 0 ? BONE_Y : i % 3 == 1 ? HAY : CAULDRON);
            f.set(u + 2, y + 3, v + 2, JACK);
        }
        f.chest(-40, y + 1, -12, Draw.SOUTH, LOOT);
        f.wallSign(-42, y + 2, -1, Draw.WEST, "MARROWTOWN", "Bones bought", "and sold. Mind", "the Wyrm");
        f.box(-41, y + 1, -1, -41, y + 3, -1, Draw.of(BONE_Y));
    }

    /** The rookery behind the heart: the marrow pots over their embers, drying racks hung with hides, bone heaps. */
    private void rookery(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, 26, -20, 48, 20)) return;
        int y = s.y + NAVE;
        f.box(35, y, -1, 37, y, 1, Draw.of(MAGMA));
        f.set(36, y + 1, 0, FIRE);
        for (int[] q : new int[][]{{34, -2}, {38, -2}, {34, 2}, {38, 2}}) { f.set(q[0], y + 1, q[1], BONE_Y); f.set(q[0], y + 2, q[1], CAULDRON); }
        for (int[] q : new int[][]{{34, 0}, {38, 0}}) f.box(q[0], y + 1, q[1], q[0], y + 5, q[1], Draw.of(FENCE));
        f.box(34, y + 6, 0, 38, y + 6, 0, Draw.of(FENCE));
        f.box(36, y + 3, 0, 36, y + 5, 0, Draw.of(IRON));
        int[] hides = {WOOL_BROWN, WOOL_RED, WOOL_BLACK, WOOL_BROWN};
        int[] rows = {-15, -8, 8, 15};
        for (int i = 0; i < rows.length; i++) {
            int v = rows[i];
            f.box(29, y + 1, v, 29, y + 5, v, Draw.of(FENCE)); f.box(43, y + 1, v, 43, y + 5, v, Draw.of(FENCE));
            f.box(30, y + 5, v, 42, y + 5, v, Draw.of(FENCE));
            for (int u = 31; u <= 41; u += 3) f.box(u, y + 3, v, u + 1, y + 4, v, Draw.of(hides[(i + u) % 4]));
        }
        ell(f, 31, y + 0.5, -4, 2.5, 2, 2.5, 0, BONES[1]);
        ell(f, 42, y + 0.5, 5, 2, 1.6, 2, 0, BONES[2]);
        f.chest(39, y + 1, 1, Draw.WEST, LOOT);
        f.set(46, y + 1, -19, JACK); f.set(46, y + 1, 19, JACK);
    }

    /** Lamp posts along Marrowtown's two streets. */
    private void lamps(Draw.Frame f, Colossi.Site s) {
        int y = s.y + NAVE;
        for (int u = -46; u <= 46; u += 12) for (int sg = -1; sg <= 1; sg += 2) {
            if (Math.abs(u) < 4) continue;
            f.box(u, y + 1, sg * 29, u, y + 3, sg * 29, Draw.of(FENCE));
            f.set(u, y + 4, sg * 29, JACK);
        }
    }

    /** Lookout nests under the ribs: scaffolds of dark oak with a platform, a ladder up a plank column. */
    private void lookouts(Draw.Frame f, Colossi.Site s, Plan p) {
        int y = s.y + NAVE;
        int[][] at = {{-14, -1}, {16, 1}};
        for (int[] q : at) {
            int u = q[0], sg = q[1];
            if (!touches(f, u - 4, sg > 0 ? 31 : -40, u + 4, sg > 0 ? 40 : -31)) continue;
            int v0 = sg * 33, v1 = sg * 38, top = y + 16;
            for (int[] c : new int[][]{{u - 2, v0}, {u + 2, v0}, {u - 2, v1}, {u + 2, v1}}) f.box(c[0], y + 1, c[1], c[0], top - 1, c[1], Draw.of(FENCE));
            f.box(u - 2, top, Math.min(v0, v1), u + 2, top, Math.max(v0, v1), Draw.of(PLANK));
            f.walls(u - 2, top + 1, Math.min(v0, v1), u + 2, top + 1, Math.max(v0, v1), Draw.of(FENCE));
            f.box(u, y + 1, v0, u, top, v0, Draw.of(PLANK));
            int lv = v0 - sg;
            f.set(u, top + 1, v0, 0);
            for (int yy = y + 1; yy <= top + 1; yy++) f.set(u, yy, lv, b(65, f.facing(sg > 0 ? Draw.NORTH : Draw.SOUTH)));
            f.set(u + 2, top + 2, v1, JACK);
            f.chest(u - 1, top + 1, v1 - sg, sg > 0 ? Draw.NORTH : Draw.SOUTH, LOOT);
        }
    }

    /** The Hoist: the scavengers' tower up beside the hips to the Spine Road. */
    private void hoist(Draw.Frame f, Colossi.Site s, Plan p) {
        int th = CHEST1 + 9;
        double[] c = at(p, th);
        double nu = -Math.sin(c[2]), nv = Math.cos(c[2]);
        int u = (int) Math.round(c[0] - nu * 13), v = (int) Math.round(c[1] - nv * 13);
        if (!touches(f, u - 14, v - 14, u + 14, v + 14)) return;
        int y0 = s.y, top = y0 + (int) Math.floor(walkH(th));
        f.box(u - 2, y0 - 2, v - 2, u + 2, y0, v + 2, Draw.of(BRICK));
        for (int[] q : new int[][]{{u - 2, v - 2}, {u + 2, v - 2}, {u - 2, v + 2}, {u + 2, v + 2}}) f.box(q[0], y0 + 1, q[1], q[0], top, q[1], Draw.of(FENCE));
        for (int yy = y0 + 8; yy < top; yy += 8) f.box(u - 2, yy, v - 2, u + 2, yy, v + 2, Draw.of(DSLAB_TOP));
        f.box(u - 2, top, v - 2, u + 2, top, v + 2, Draw.of(PLANK));
        f.box(u, y0 + 1, v, u, top, v, Draw.of(PLANK));
        for (int yy = y0 + 1; yy <= top; yy++) f.set(u + 1, yy, v, b(65, f.facing(Draw.EAST)));
        // the bridge to the road
        double ru = c[0], rv = c[1];
        for (int k = 0; k <= 26; k++) {
            double q = k / 26.0;
            int bu = (int) Math.round(u + (ru - u) * q), bv = (int) Math.round(v + (rv - v) * q);
            f.box(bu, top, bv, bu, top, bv, Draw.of(PLANK));
            f.box(bu, top + 1, bv, bu, top + 2, bv, Draw.AIR);
        }
        f.set(u - 2, top + 1, v - 2, JACK);
        f.wallSign(u, y0 + 2, v - 3 < v ? v - 1 : v + 1, Draw.NORTH, "THE HOIST", "Up to the", "Spine Road", "");
    }

    /** The Nest inside the Coil: soul sand, webs and bones, a ladder down the tail's tip, a spider's hoard. */
    private void nest(Draw.Frame f, Colossi.Site s, Plan p) {
        int nu = (int) Math.round(p.nestU), nv = (int) Math.round(p.nestV), y0 = s.y;
        if (!touches(f, nu - 12, nv - 12, nu + 12, nv + 12)) return;
        cols(f, nu - 5, nv - 5, nu + 5, nv + 5, (u, v, x, z) -> {
            double du = u - p.nestU, dv = v - p.nestV;
            if (du * du + dv * dv > 25) return;
            f.d.set(x, y0, z, Draw.rnd(x, 0, z, s.salt + 61) < 0.25 ? BONE_Y : SOUL);
            if (Draw.rnd(x, 1, z, s.salt + 62) < 0.18) f.d.set(x, y0 + 1, z, WEB);
        });
        double[] c = at(p, END - 3);
        int lu = (int) Math.round(c[0]), lv = (int) Math.round(c[1]);
        int du = Integer.signum((int) Math.round(p.nestU - c[0])), dv = Integer.signum((int) Math.round(p.nestV - c[1]));
        if (Math.abs(p.nestU - c[0]) >= Math.abs(p.nestV - c[1])) dv = 0; else du = 0;
        if (du == 0 && dv == 0) du = 1;
        int face = du > 0 ? Draw.EAST : du < 0 ? Draw.WEST : dv > 0 ? Draw.SOUTH : Draw.NORTH;
        int top = y0 + (int) Math.floor(walkH(END - 3));
        f.box(lu, y0, lv, lu, top, lv, Draw.of(BONE_Y));
        for (int y = y0 + 1; y <= top + 1; y++) f.set(lu + du, y, lv + dv, b(65, f.facing(face)));
        f.chest(nu + 2, y0 + 1, nv, Draw.WEST, LOOT + "_rich");
        f.spawner(nu - 2, y0 + 1, nv + 1, "brimstone_spider");
        f.set(nu, y0, nv - 2, MAGMA);
    }

    // ---- the outer works -----------------------------------------------------------------------------------------------
    /** The Ossuary: a yard walled in bone, the charnel house with its skull niches, bone piles and the pits. */
    private void ossuary(Draw.Frame f, Colossi.Site s, Plan p) {
        int ou = OSS_U, ov = OSS_V, y = s.y;
        if (!touches(f, ou - 26, ov - 20, ou + 26, ov + 20)) return;
        Draw.Mat wallM = Draw.mix(BONE_Y, WHITE_T, 0.2, s.salt + 71);
        f.walls(ou - 22, y + 1, ov - 17, ou + 22, y + 3, ov + 17, wallM);
        for (int u = ou - 22; u <= ou + 22; u += 4) for (int v : new int[]{ov - 17, ov + 17}) { f.box(u, y + 1, v, u, y + 4, v, Draw.of(QP_Y)); f.skull(u, y + 5, v, 0, v > ov ? 0 : 8); }
        for (int v = ov - 17; v <= ov + 17; v += 4) for (int u : new int[]{ou - 22, ou + 22}) { f.box(u, y + 1, v, u, y + 4, v, Draw.of(QP_Y)); f.skull(u, y + 5, v, 0, u > ou ? 12 : 4); }
        // the gate toward the skeleton: two posts, a lintel, a skull crown
        f.box(ou - 2, y + 1, ov + 17, ou + 2, y + 4, ov + 17, Draw.AIR);
        f.box(ou - 3, y + 1, ov + 17, ou - 3, y + 7, ov + 17, Draw.of(BONE_Y));
        f.box(ou + 3, y + 1, ov + 17, ou + 3, y + 7, ov + 17, Draw.of(BONE_Y));
        f.box(ou - 3, y + 8, ov + 17, ou + 3, y + 8, ov + 17, BONES[axis(p.rot, 1, 0, 0)]);
        f.box(ou - 1, y + 9, ov + 17, ou + 1, y + 10, ov + 17, Draw.of(BONE_Y));
        f.set(ou, y + 9, ov + 18, BLACK_T);
        f.wallSign(ou - 3, y + 3, ov + 18, Draw.SOUTH, "THE OSSUARY", "The bones of", "those who came", "before you");
        // the charnel house
        int h0 = ou - 18, h1 = ou - 4, w0 = ov - 14, w1 = ov - 4;
        f.box(h0, y, w0, h1, y + 8, w1, Draw.mix(BONE_Y, QUARTZ, 0.1, s.salt + 73));
        f.box(h0 + 1, y + 1, w0 + 1, h1 - 1, y + 7, w1 - 1, Draw.AIR);
        f.box(h0 + 1, y, w0 + 1, h1 - 1, y, w1 - 1, Draw.of(BLACK_T));
        for (int[] q : new int[][]{{h0, w0}, {h1, w0}, {h0, w1}, {h1, w1}}) f.box(q[0], y, q[1], q[0], y + 9, q[1], Draw.of(QP_Y));
        for (int k = 0; k < 5; k++) f.box(h0 + k, y + 9 + k, w0 + k, h1 - k, y + 9 + k, w1 - k, Draw.of(k == 4 ? BONE_Y : BRICK));
        for (int u = h0 + 2; u <= h1 - 2; u += 2) for (int v : new int[]{w0, w1}) for (int yy = y + 2; yy <= y + 5; yy += 3) {
            f.set(u, yy, v, 0);
            f.skull(u, yy, v, 0, v == w0 ? 0 : 8);
        }
        f.box(h1, y + 1, (w0 + w1) / 2 - 1, h1, y + 3, (w0 + w1) / 2 + 1, Draw.AIR);
        f.set((h0 + h1) / 2, y + 7, (w0 + w1) / 2, GLOW);
        f.set(h0 + 2, y + 6, w0 + 2, WEB); f.set(h1 - 2, y + 6, w1 - 2, WEB);
        f.chest(h0 + 1, y + 1, (w0 + w1) / 2, Draw.EAST, LOOT + "_rich");
        f.chest(h0 + 3, y + 1, w0 + 1, Draw.SOUTH, LOOT);
        f.spawner(h0 + 5, y + 1, w1 - 2, "wither_skeleton");
        // the bone piles
        int[][] piles = {{ou + 4, ov - 12, 4}, {ou + 15, ov - 11, 3}, {ou - 12, ov + 9, 5}, {ou + 16, ov + 12, 3}, {ou - 2, ov + 4, 3}};
        for (int[] q : piles) {
            ell(f, q[0], y + 0.5, q[1], q[2], q[2] * 0.7, q[2], 0, (x, yy, z) -> {
                double r = Draw.rnd(x, yy, z, s.salt + 77);
                return r < 0.33 ? BONE_Y : r < 0.66 ? BONE_X : r < 0.9 ? BONE_Z : WHITE_T;
            });
            f.skull(q[0], y + (int) Math.ceil(q[2] * 0.7) + 1, q[1], 0, q[0] & 15);
        }
        // the pits under their bone covers (they give way), a ladder out of each
        for (int[] q : new int[][]{{ou + 7, ov - 1}, {ou + 14, ov + 3}}) {
            f.box(q[0] - 3, y - 5, q[1] - 3, q[0] + 3, y - 1, q[1] + 3, Draw.of(BRICK));
            f.box(q[0] - 2, y - 4, q[1] - 2, q[0] + 2, y - 1, q[1] + 2, Draw.AIR);
            f.box(q[0] - 2, y - 5, q[1] - 2, q[0] + 2, y - 5, q[1] + 2, Draw.mix(SOUL, MAGMA, 0.3, s.salt + 79));
            f.box(q[0] - 2, y, q[1] - 2, q[0] + 2, y, q[1] + 2, Draw.of(PIT_COVER));
            for (int yy = y - 4; yy <= y - 1; yy++) f.set(q[0] - 2, yy, q[1], b(65, f.facing(Draw.EAST)));
        }
        // the sorting racks
        for (int v = ov - 6; v <= ov + 10; v += 4) {
            f.box(ou - 2, y + 1, v, ou + 2, y + 1, v, Draw.of(FENCE));
            f.box(ou - 2, y + 2, v, ou + 2, y + 2, v, BONES[axis(p.rot, 1, 0, 0)]);
        }
        f.set(ou + 20, y + 1, ov - 15, RACK); f.set(ou + 20, y + 2, ov - 15, FIRE);
        f.set(ou - 20, y + 1, ov + 15, RACK); f.set(ou - 20, y + 2, ov + 15, FIRE);
        f.chest(ou + 21, y + 1, ov, Draw.WEST, LOOT);
    }

    /** The scavengers' camp: a palisade ring, hide tents, the rendering vats, a lookout tower. */
    private void camp(Draw.Frame f, Colossi.Site s, Plan p) {
        int cu = CAMP_U, cv = CAMP_V, y = s.y;
        if (!touches(f, cu - 22, cv - 22, cu + 22, cv + 22)) return;
        cols(f, cu - 20, cv - 20, cu + 20, cv + 20, (u, v, x, z) -> {
            double du = u - cu, dv = v - cv, rr = Math.sqrt(du * du + dv * dv);
            if (rr < 18.5 || rr > 19.5) return;
            if (dv < -15 && Math.abs(du) <= 3) return;                // the gate (toward the nave)
            boolean stake = Math.floorMod(u * 7 + v * 3, 6) == 0;
            f.d.set(x, y + 1, z, FENCE); f.d.set(x, y + 2, z, FENCE);
            if (stake) { f.d.set(x, y + 3, z, BONE_Y); f.d.set(x, y + 4, z, BONE_Y); f.d.set(x, y + 1, z, BONE_Y); f.d.set(x, y + 2, z, BONE_Y); }
        });
        for (int sg = -1; sg <= 1; sg += 2) { f.box(cu + sg * 4, y + 1, cv - 19, cu + sg * 4, y + 6, cv - 19, Draw.of(BONE_Y)); f.set(cu + sg * 4, y + 7, cv - 19, JACK); }
        f.wallSign(cu - 4, y + 3, cv - 20, Draw.NORTH, "SCAVENGERS'", "CAMP", "Marrow, horn", "and teeth");
        // tents
        int[][] tents = {{cu - 11, cv - 4}, {cu - 8, cv + 8}, {cu + 3, cv + 11}, {cu + 11, cv - 6}, {cu - 2, cv - 12}};
        int[] wools = {WOOL_BROWN, WOOL_RED, WOOL_BLACK, WOOL_BROWN, WOOL_WHITE};
        for (int i = 0; i < tents.length; i++) {
            int u = tents[i][0], v = tents[i][1];
            for (int q = 0; q <= 3; q++) f.box(u - 3 + q, y + 1 + q, v - 2, u + 3 - q, y + 1 + q, v + 2, Draw.of(wools[i]));
            f.box(u - 2, y + 1, v - 2, u + 2, y + 2, v + 2, Draw.AIR);
            f.box(u - 1, y + 3, v - 2, u + 1, y + 3, v + 2, Draw.AIR);
            f.box(u - 2, y + 1, v - 1, u + 2, y + 1, v + 1, Draw.of(CARPET_BROWN));
            if (i == 1) f.chest(u, y + 1, v + 1, Draw.NORTH, LOOT);
            if (i == 3) f.chest(u, y + 1, v + 1, Draw.NORTH, LOOT + "!trap");
        }
        // the fire pit and the rendering vats
        ell(f, cu, y + 0.5, cv, 2.5, 0.6, 2.5, 0, Draw.of(BONE_Y));
        f.set(cu, y + 1, cv, RACK); f.set(cu, y + 2, cv, FIRE);
        for (int k = 0; k < 3; k++) { f.set(cu + 6, y, cv - 2 + k * 2, MAGMA); f.set(cu + 6, y + 1, cv - 2 + k * 2, CAULDRON); }
        // the lookout
        int lu = cu + 12, lv = cv + 9, top = y + 12;
        for (int[] q : new int[][]{{lu - 2, lv - 2}, {lu + 2, lv - 2}, {lu - 2, lv + 2}, {lu + 2, lv + 2}}) f.box(q[0], y + 1, q[1], q[0], top - 1, q[1], Draw.of(FENCE));
        f.box(lu - 2, top, lv - 2, lu + 2, top, lv + 2, Draw.of(PLANK));
        f.walls(lu - 2, top + 1, lv - 2, lu + 2, top + 1, lv + 2, Draw.of(FENCE));
        f.box(lu, y + 1, lv, lu, top, lv, Draw.of(PLANK));
        f.set(lu - 1, top, lv, 0);
        for (int yy = y + 1; yy <= top + 1; yy++) f.set(lu - 1, yy, lv, b(65, f.facing(Draw.WEST)));
        f.set(lu + 2, top + 2, lv + 2, JACK); f.set(lu - 2, top + 2, lv - 2, JACK);
        // bone racks
        for (int k = 0; k < 3; k++) {
            int u = cu - 14 + k * 3, v = cv + 12;
            f.box(u, y + 1, v, u, y + 2, v, Draw.of(FENCE));
            f.set(u, y + 3, v, BONE_Y);
        }
    }

    /** A bone obelisk: a plinth, a column of stacked vertebrae, a burning crown under four horns. */
    private void obelisk(Draw.Frame f, Colossi.Site s, int[] o, int i) {
        int u = o[0], v = o[1], h = o[2], y = s.y;
        if (!touches(f, u - 8, v - 8, u + 8, v + 8)) return;
        f.box(u - 4, y - 3, v - 4, u + 4, y, v + 4, Draw.of(BRICK));
        f.box(u - 3, y + 1, v - 3, u + 3, y + 2, v + 3, Draw.mix(RED_NB, BRICK, 0.3, s.salt + 91));
        int yy = y + 3, k = 0;
        while (yy + 4 <= y + h) {
            double rr = 2.6 - k * 0.08;
            f.cyl(u, v, rr, yy, yy + 2, BONES[0]);
            f.box(u - 1, yy + 3, v - 1, u + 1, yy + 3, v + 1, Draw.of(QP_Y));
            if (k % 2 == 1) f.set(u, yy + 1, v + (int) Math.ceil(rr), Draw.rnd(u, yy, v, 3) < 0.5 ? MAGMA : BONE_Y);
            yy += 4; k++;
        }
        f.box(u - 2, yy, v - 2, u + 2, yy + 1, v + 2, BONES[0]);
        f.box(u - 1, yy + 2, v - 1, u + 1, yy + 2, v + 1, Draw.of(RACK));
        f.box(u - 1, yy + 3, v - 1, u + 1, yy + 3, v + 1, Draw.of(FIRE));
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            f.line(u + c[0], yy + 1, v + c[1], u + c[0] * 2.2, yy + 5, v + c[1] * 2.2, 0.6, BONES[0]);
        }
        int dir = Math.abs(u) > Math.abs(v) ? (u > 0 ? Draw.WEST : Draw.EAST) : (v > 0 ? Draw.NORTH : Draw.SOUTH);
        int su = dir == Draw.WEST ? u - 4 : dir == Draw.EAST ? u + 4 : u, sv = dir == Draw.NORTH ? v - 4 : dir == Draw.SOUTH ? v + 4 : v;
        f.box(su, y + 1, sv, su, y + 2, sv, Draw.of(BRICK));
        int pu = dir == Draw.WEST ? su - 1 : dir == Draw.EAST ? su + 1 : su, pv = dir == Draw.NORTH ? sv - 1 : dir == Draw.SOUTH ? sv + 1 : sv;
        if (i == 0) f.wallSign(pu, y + 2, pv, dir, "Six obelisks", "mark the bones", "of the deep", "beast");
        if (i == 2 || i == 4) f.chest(pu, y + 1, pv, dir, LOOT);
        f.skull(u + 3, y + 3, v + 3, 0, 2); f.skull(u - 3, y + 3, v - 3, 0, 10);
    }

    /** What the traps show: the magma vents of the flames. */
    private void trapMarks(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int[] t : p.traps) if (t[0] == 3) { int mu = (t[1] + t[4]) / 2, mv = (t[3] + t[6]) / 2; f.set(mu, t[2] - 1, mv, MAGMA); }
    }

    // ---- the garrisons and the traps -----------------------------------------------------------------------------------
    private List<Garrison> garrisons(Plan p, Colossi.Site s) {
        int y0 = s.y, ny = y0 + NAVE + 1, us = p.us, vs = p.vs;
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(us, y0 + 1, vs + 8, "!wither_skeleton+skeleton"));
        gs.add(g(us + 1, y0 + 9, vs + 28, "!crypt_guard+wight+lost_soul"));
        gs.add(g(us, y0 + 15, vs + 12, "shade+soul_wraith"));
        double[] n1 = at(p, NECK - 23);
        gs.add(g((int) Math.round(n1[0]), y0 + (int) Math.floor(walkH(NECK - 23)) + 1, (int) Math.round(n1[1]), "ashbone_archer+skeleton"));
        gs.add(g(-48, y0 + 1, -54, "brute+nethermite"));
        gs.add(g(-48, y0 + 1, 54, "magma_hulk+charred_ghoul"));
        gs.add(g(-30, ny, -29, "pigman_berserker+zombie_pigman"));
        gs.add(g(34, ny, 29, "cinder_witch+cinder_imp"));
        gs.add(g(-38, ny, 0, "dread_rider+hellhound"));
        gs.add(g(2, y0 + NAVE + 4, 4, "!pyre_warden+flame_adept+ember"));
        gs.add(g(-16, ny, 15, "infernal_knight+ember_legionnaire"));
        gs.add(g(32, ny, 4, "ember+salamander"));
        double[] h1 = at(p, HEART + 12);
        gs.add(g((int) Math.round(h1[0]), y0 + (int) Math.floor(walkH(HEART + 12)) + 1, (int) Math.round(h1[1]), "!royal_guard+ashbone_archer"));
        double[] t1 = at(p, CHEST1 + 40);
        gs.add(g((int) Math.round(t1[0]), y0 + (int) Math.floor(walkH(CHEST1 + 40)) + 1, (int) Math.round(t1[1]), "salamander+spinout"));
        gs.add(g((int) Math.round(p.nestU), y0 + 1, (int) Math.round(p.nestV), "deep_crawler+brimstone_spider+coolmar_spider"));
        gs.add(g(OSS_U + 8, y0 + 1, OSS_V + 10, "mummy+tomb_guardian+scarab"));
        gs.add(g(OSS_U - 11, y0 + 1, OSS_V - 9, "asp+spore+spore_creeper"));
        int[] ob = p.obelisks.get(1);
        gs.add(g(ob[0] + (ob[0] > 0 ? -6 : 6), y0 + 1, ob[1], "blaze+magma_cube"));
        gs.add(g(CAMP_U + 2, y0 + 1, CAMP_V + 4, "zombie_pigman+pigman_berserker+mogus"));
        double[] hp = at(p, HIPS);
        gs.add(g((int) Math.round(hp[0] + Math.sin(hp[2]) * 18), y0 + 1, (int) Math.round(hp[1] - Math.cos(hp[2]) * 18), "frost+wight"));
        gs.add(g(-14, y0 + NAVE + 17, -35, "ashbone_archer+skeleton"));
        // the great fliers in the open air: beside the hump, over the ossuary and the coil, among the ribs
        gs.add(g(0, y0 + 40, 76, "ghast+ghastling"));
        gs.add(g(20, y0 + 38, -74, "ghast+ghastling"));
        gs.add(g(-32, y0 + 22, 30, "ghastling+ghastling"));
        gs.add(g(36, y0 + 22, -30, "ghastling+ghast"));
        gs.add(g(104, y0 + 34, -12, "ghast+blaze"));
        gs.add(g(-112, y0 + 40, -22, "ghastling+ghast"));
        return gs;
    }

    /** The traps: kind 0 arrows, 1 collapse, 2 rubble, 3 flames, 4 gas, 5 cannon; a local box, an emitter (or gun) and a block. */
    private void traps(Plan p, Colossi.Site s) {
        int y0 = s.y, ny = y0 + NAVE, us = p.us, vs = p.vs;
        List<int[]> t = p.traps;
        // the skull: the tongue gives way, the palate sheds bone, the gullet's walls shoot
        t.add(new int[]{1, us - 2, y0, vs + 11, us + 2, y0, vs + 15, 0, 0, 0, 0, 0, 0, QSLAB_TOP});
        t.add(new int[]{2, us - 4, y0 + 1, vs + 5, us + 4, y0 + 6, vs + 10, 0, 0, 0, 0, 0, 0, BONE_Y});
        t.add(new int[]{0, us - 1, y0 + 2, vs + 18, us + 1, y0 + 9, vs + 25, us - 2, y0 + 3, vs + 18, us + 2, y0 + 9, vs + 25, 0});
        t.add(new int[]{2, us - 6, y0 + 9, vs + 26, us + 6, y0 + 14, vs + 33, 0, 0, 0, 0, 0, 0, BONE_Y});
        t.add(new int[]{2, us - 3, y0 + 15, vs + 8, us + 3, y0 + 17, vs + 16, 0, 0, 0, 0, 0, 0, BONE_Y});
        // the streets: arrows from the ribs, and a shack alley's rubble
        for (int sg = -1; sg <= 1; sg += 2) {
            int[] us2 = sg < 0 ? new int[]{-20, 22} : new int[]{-8, 30};
            for (int u : us2) t.add(new int[]{0, u - 4, ny + 1, sg > 0 ? 28 : -30, u + 4, ny + 3, sg > 0 ? 30 : -28, u - 5, ny + 2, sg > 0 ? 27 : -31, u + 5, ny + 3, sg > 0 ? 31 : -27, 0});
        }
        t.add(new int[]{2, 8, ny + 1, 31, 14, ny + 4, 37, 0, 0, 0, 0, 0, 0, BONE_Y});
        // the heart: flames from its vents, the reek of its chamber
        t.add(new int[]{3, -6, ny + 4, -3, -4, ny + 6, -1, 0, 0, 0, 0, 0, 0, 0});
        t.add(new int[]{3, 4, ny + 4, 1, 6, ny + 6, 3, 0, 0, 0, 0, 0, 0, 0});
        t.add(new int[]{4, -8, ny + 4, -5, -2, ny + 7, 0, 0, 0, 0, 0, 0, 0, 0});
        t.add(new int[]{3, -16, ny + 1, -1, -14, ny + 3, 1, 0, 0, 0, 0, 0, 0, 0});
        // the ossuary: the pits, the charnel house's miasma
        t.add(new int[]{1, OSS_U + 5, y0, OSS_V - 3, OSS_U + 9, y0, OSS_V + 1, 0, 0, 0, 0, 0, 0, PIT_COVER});
        t.add(new int[]{1, OSS_U + 12, y0, OSS_V + 1, OSS_U + 16, y0, OSS_V + 5, 0, 0, 0, 0, 0, 0, PIT_COVER});
        t.add(new int[]{4, OSS_U - 17, y0 + 1, OSS_V - 13, OSS_U - 5, y0 + 4, OSS_V - 5, 0, 0, 0, 0, 0, 0, 0});
        // the neck's road: arrows; the coil's nest: gas
        double[] n1 = at(p, NECK - 11);
        int nu = (int) Math.round(n1[0]), nv = (int) Math.round(n1[1]), ry = y0 + (int) Math.floor(walkH(NECK - 11)) + 1;
        t.add(new int[]{0, nu - 3, ry, nv - 3, nu + 3, ry + 2, nv + 3, nu - 4, ry, nv - 4, nu + 4, ry + 2, nv + 4, 0});
        int cu = (int) Math.round(p.nestU), cv = (int) Math.round(p.nestV);
        t.add(new int[]{4, cu - 3, y0 + 1, cv - 3, cu + 3, y0 + 3, cv + 3, 0, 0, 0, 0, 0, 0, 0});
        // the camp's vats
        t.add(new int[]{3, CAMP_U + 5, y0 + 1, CAMP_V - 3, CAMP_U + 7, y0 + 3, CAMP_V + 3, 0, 0, 0, 0, 0, 0, 0});
    }

    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        for (int[] t : p.traps) {
            int[] box = fr.box(t[1], t[2], t[3], t[4], t[5], t[6]);
            switch (t[0]) {
                case 0: out.add(Ordeals.arrows(box, fr.box(t[7], t[8], t[9], t[10], t[11], t[12]))); break;
                case 1: out.add(Ordeals.collapse(box, t[13])); break;
                case 2: out.add(Ordeals.rubble(box, t[13])); break;
                case 3: out.add(Ordeals.flames(box, 70)); break;
                case 4: out.add(Ordeals.gas(box)); break;
                default:
            }
        }
        // two obelisks' crowns spit fire at whoever comes near
        for (int i : new int[]{0, 3}) {
            int[] o = p.obelisks.get(i);
            int top = s.y + 3 + 4 * ((o[2] - 7) / 4 + 1) + 4;
            out.add(Ordeals.cannon(fr.box(o[0] - 28, s.y - 4, o[1] - 28, o[0] + 28, s.y + 30, o[1] + 28), fr.at(o[0], top, o[1])));
        }
        out.add(Ordeals.bossSeal(s.kind.id + "_vault", fr.box(p.us - 1, s.y + 9, p.vs + 34, p.us + 1, s.y + 11, p.vs + 34), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern: soul sand and drifts of bone dust, bones in the ground ----------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double f = s.f(x, z), q = Draw.rnd(x, 0, z, s.salt + 61);
        if (f > 0.86) return q < 0.35 ? SOUL : RACK;
        double n = Draw.noise(x, z, 11, s.salt + 63);
        if (n > 0.72) return q < 0.7 ? BONE_Y : WHITE_T;                  // fields of bone in the dust
        if (n > 0.62) return q < 0.5 ? WHITE_T : q < 0.75 ? LGRAY_T : SOUL;
        return q < 0.07 ? GRAVEL : q < 0.12 ? BROWN_T : SOUL;
    }

    @Override Draw.Mat under() { return Draw.mix(SOUL, RACK, 0.45, 7781); }

    @Override double lakeLevel() { return 0.38; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.004);
        if (lake) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.0016) { int n = 1 + (int) (Draw.rnd(x, 10, z, s.salt + 53) * 3); for (int k = 1; k <= n; k++) d.set(x, floor + k, z, BONE_Y); }
        else if (q < 0.0022) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
    }
}
