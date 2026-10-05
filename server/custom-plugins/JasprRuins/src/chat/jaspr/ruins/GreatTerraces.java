package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Terraces of the Drowned Queen (epoch 6): a palace stepped up the land's rise on six terraces, 35 blocks from the
 * outer garden to the Queen's palace, its dome and towers rising 40 more. Her tears run down the middle of it all: a
 * channel across every terrace, a sheet of water falling through a slot in every terrace wall into a basin at its foot,
 * and the great pool of the forecourt, under which they gather in a flooded cistern.
 * <ul>
 * <li>Terrace 0, the Garden of Stone: the Gate of Pearls, two parterres of stone hedges with pavilions, statues and
 * urns, the avenue of the Queen's guards, and the Queen's Mirror (the great pool with her effigy);</li>
 * <li>1, the Colonnade of Tides; 2, the Weeping Pools and their fountains; 3, the Court of the Suitors (rows of
 * kneeling statues and two pavilions of waiting); 4, the Petrified Garden (trees of coral stone);</li>
 * <li>5, the Court of Tears (the weeping statue whose tears feed the falls, the lamps of the court) and the palace: the
 * throne hall under the dome (where the Queen wakes), the Queen's Bath and the Library of Tides, the Treasury of the
 * Drowned and her Chamber, four towers;</li>
 * <li>ten grottoes under the terraces behind arches in their walls, and the Cistern of Tears under the forecourt (down a
 * stair in the western grotto of the first wall).</li>
 * </ul>
 * Water stands only in contained basins, channels and slots, so a block update lets it fall into the basin below and no
 * farther. The whole design is drawn column by column clipped to the chunk, in a frame turned so the terraces climb.
 */
final class GreatTerraces extends GreatDesign {
    static final int R = 50;
    /** Terrace k: its front edge (local v), its floor (y above base) and its half-width; each runs back to the rear wall. */
    static final int[] VF = {-50, -33, -21, -9, 3, 15}, FL = {0, 7, 14, 21, 28, 35}, HW = {50, 46, 42, 38, 34, 30};
    /** The grottoes' centres (|u|) under terrace k, opening in its front wall. */
    static final int[] GC = {0, 22, 20, 18, 16, 14};
    /** Where each wall's inner flights of stairs begin (|u|): the first wall's stand clear of the great pool. */
    static final int[] IN = {0, 11, 5, 5, 5, 5};
    private static final String TREASURE = "minecraft:chests/end_city_treasure", MANSION = "minecraft:chests/woodland_mansion",
        CROSSING = "minecraft:chests/stronghold_crossing", MINESHAFT = "minecraft:chests/abandoned_mineshaft", IGLOO = "minecraft:chests/igloo_chest";
    private static final int QUARTZ_STAIRS = 156, CARPET = 171, WOOL = 35;

    static final class Plan extends Layout {
        /** The frame's turn (the terraces climb toward the land's high side) and the land's level outside the gate. */
        int rot, gateY;
        long h;
        /** The coral trees of the Petrified Garden: u, v, trunk height, crown radius. */
        final int[][] trees = new int[8][];
        /** Which grottoes (2 * (k - 1) + (side > 0 ? 1 : 0)) keep a cage. */
        final boolean[] cage = new boolean[10];
    }

    // ------------------------------------------------------------------------------------------------------ the plan
    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.h = s.hash;
        p.rot = uphill(s);
        Frame probe = new Frame(null, s.x, s.z, s.base, p.rot);
        p.gateY = Math.max(-9, Math.min(6, s.surface(probe.wx(0, -52), probe.wz(0, -52)) - s.base));
        int[][] spots = {{12, 6, 5, 2}, {16, 11, 6, 3}, {27, 5, 4, 2}, {31, 10, 6, 3}};
        for (int i = 0; i < 8; i++) {
            int[] sp = spots[i / 2];
            p.trees[i] = new int[] {(i % 2 == 0 ? -1 : 1) * sp[0], sp[1], sp[2] + r.nextInt(2), sp[3]};
        }
        for (int i = 0; i < 10; i++) p.cage[i] = r.nextInt(10) < 3;
        p.cage[3] = true;                                  // the east grotto of the second wall always keeps one
        int t = (p.rot - s.rot) & 3;
        p.boss = turn(t, 0, 36, 40);
        // the Garden of Stone
        add(p, t, 9, 1, -47, "deep_one+zombie");
        add(p, t, -19, 1, -42, "creeper+skeleton");
        add(p, t, 19, 1, -42, "ghoul+husk");
        add(p, t, -48, 1, 8, "spider+cave_spider");
        add(p, t, 48, 1, 8, "hound+zombie_villager");
        // the Colonnade of Tides, the Weeping Pools, the Court of the Suitors, the Petrified Garden
        add(p, t, -25, 8, -30, "!cult_zealot+vindicator");
        add(p, t, 25, 8, -30, "cult_adept+evoker");
        add(p, t, -18, 15, -12, "deep_one+witch");
        add(p, t, 18, 15, -12, "slime+shoggoth");
        add(p, t, -14, 22, -4, "zombie_villager+zombie");
        add(p, t, 30, 22, 0, "illusioner+cult_adept");
        add(p, t, -20, 29, 5, "enderman+mi_go");
        add(p, t, 20, 29, 5, "spider+tomb_crawler");
        // the Court of Tears and the palace
        add(p, t, -12, 36, 23, "!deep_one+star_spawn");
        add(p, t, 12, 36, 23, "witch+stray");
        add(p, t, 0, 36, 32, "wither_skeleton+skeleton");
        add(p, t, 18, 36, 46, "skeleton+silverfish");
        add(p, t, -18, 44, 46, "cult_adept+illusioner");
        add(p, t, 18, 44, 33, "witch+endermite");
        add(p, t, -18, 51, 38, "nightgaunt+stray");
        // the grottoes and the cistern
        add(p, t, 22, 1, -32, "spider+creeper");
        add(p, t, 20, 8, -20, "tomb_crawler+cave_spider");
        add(p, t, -18, 15, -8, "silverfish+endermite");
        add(p, t, 16, 22, 4, "hound+ghoul");
        add(p, t, -14, 29, 16, "deep_one+slime");
        add(p, t, 10, -9, -43, "ghoul+tomb_crawler");
        add(p, t, -10, -9, -43, "nightgaunt+deep_one");
        return p;
    }

    /** The frame whose back (local +v) faces the land's high side; the site's own turn where the land is flat. */
    static int uphill(Plans.GreatSite s) {
        int[] sum = new int[4];   // 0: +z is the back, 1: -x, 2: -z, 3: +x (local +v under each turn)
        for (int d = 18; d <= 46; d += 14)
            for (int w = -24; w <= 24; w += 24) {
                sum[0] += s.surface(s.x + w, s.z + d);
                sum[1] += s.surface(s.x - d, s.z + w);
                sum[2] += s.surface(s.x + w, s.z - d);
                sum[3] += s.surface(s.x + d, s.z + w);
            }
        int best = s.rot & 3;
        for (int k = 0; k < 4; k++) if (sum[k] > sum[best] + 18) best = k;
        return best;
    }

    private static void add(Plan p, int t, int u, int y, int v, String pack) {
        int[] w = turn(t, u, y, v);
        p.garrisons.add(g(w[0], w[1], w[2], pack));
    }

    /** A point of this design's frame in the site's frame (the design's frame is the site's turned t steps further). */
    private static int[] turn(int t, int u, int y, int v) {
        switch (t & 3) {
            case 1: return new int[] {-v, y, u};
            case 2: return new int[] {-u, y, -v};
            case 3: return new int[] {v, y, -u};
            default: return new int[] {u, y, v};
        }
    }

    // ------------------------------------------------------------------------------------------------------ drawing
    @Override void draw(Plans.GreatSite s, Layout l, Canvas c) {
        Plan p = (Plan) l;
        Pen q = new Pen(s, c, p.rot, R);
        if (q.empty()) return;
        terrain(q);
        gate(q, p);
        court(q);
        for (int k = 1; k <= 5; k++) { cascade(q, k); flights(q, k); }
        mirror(q);
        garden(q);
        colonnade(q);
        weepingPools(q);
        suitors(q);
        for (int[] tr : p.trees) tree(q, tr[0], tr[1], FL[4], tr[2], tr[3]);
        for (int k = 0; k <= 4; k++)                     // urns along the side walks of the terraces
            for (int v = -6; v <= 42; v += 24)
                if (v >= VF[k + 1] + 2) for (int side = -1; side <= 1; side += 2) urn(q, side * (HW[k] - 1), FL[k] + 1, v);
        palace(q);
        for (int k = 1; k <= 5; k++) { grotto(q, p, k, -1); grotto(q, p, k, 1); }
        cistern(q);
        tiles(q, p);
    }



    /** The terrace a column belongs to (-1 outside the footprint). */
    private static int level(int u, int v) {
        if (u < -R || u > R || v < -R || v > R) return -1;
        int k = 0, a = Math.abs(u);
        for (int i = 1; i < 6; i++) if (v >= VF[i] && a <= HW[i]) k = i;
        return k;
    }

    /** Every column: levelled to its terrace, faced where it stands above a lower one, the land cut back above it. */
    private static void terrain(Pen q) {
        Frame f = q.f;
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                int k = level(u, v), H = FL[k], G = q.land(u, v);
                int ln = level(u, v - 1), ls = level(u, v + 1), lw = level(u - 1, v), le = level(u + 1, v);
                int low = Math.min(Math.min(ln, ls), Math.min(lw, le));
                boolean edge = low < k, rim = low < 0, front = ln < k, across = front || ls < k;
                if (edge) {
                    int from = rim ? G + 1 : FL[low] + 1;              // the face shows above the lower terrace's floor
                    if (G + 1 < from) q.col(u, v, G + 1, from - 1, STONE << 4);
                    for (int y = from; y < H; y++) face(f, u, y, v, across);
                    f.set(u, H, v, BRICK, 0);
                } else {
                    if (G < H) q.col(u, v, G + 1, H - 1, STONE << 4);
                    floor(f, u, H, v, k);
                }
                if (G + 2 > H) {                       // the land (and the field's litter on it) cut back above the floor
                    if (rim && G > H) { for (int y = H + 1; y <= G; y++) f.masonry(u, y, v); f.clear(u, v, G + 1, G + 2); }
                    else f.clear(u, v, H + 1, G + 2);
                }
                if (edge && k > 0 && !(rim && G > H) && !gap(u, k, front)) parapet(f, u, H + 1, v, front);
            }
    }

    private static void face(Frame f, int u, int y, int v, boolean across) {
        int along = across ? u : v;
        if (Math.floorMod(along, 8) == 0) f.set(u, y, v, QUARTZ, 2);          // a pale pilaster
        else if (Math.floorMod(y, 7) == 6) f.set(u, y, v, PRISMARINE, 2);       // a frieze at every terrace's level
        else f.masonry(u, y, v);
    }

    private static void floor(Frame f, int u, int y, int v, int k) {
        if (k == 0) { f.paving(u, y, v); return; }
        if (f.roll(u, y, v, 41) < 0.08) { f.paving(u, y, v); return; }       // the sea-worn patches
        if (Math.abs(u) <= 3) { f.set(u, y, v, k == 5 ? QUARTZ : BRICK, 0); return; }   // the processional way
        if (Math.floorMod(u, 6) == 0 || Math.floorMod(v, 6) == 0) f.set(u, y, v, STONE, 6);
        else f.set(u, y, v, PRISMARINE, ((u ^ v) & 1) == 0 ? 1 : 2);
    }

    /** Where a terrace's front parapet opens: the falls and the flights of stairs. */
    private static boolean gap(int u, int k, boolean front) {
        if (!front) return false;
        int a = Math.abs(u);
        return a <= 1 || a >= IN[k] && a <= IN[k] + 2 || a >= HW[k] - 9 && a <= HW[k] - 7;
    }

    private static void parapet(Frame f, int u, int y, int v, boolean front) {
        if (Math.floorMod(front ? u : v, 4) == 0) f.set(u, y, v, BRICK, 3);
        else f.set(u, y, v, WALL, 1);
    }

    // --------------------------------------------------------------------------------------- the gate and garden wall
    private static void gate(Pen q, Plan p) {
        Frame f = q.f;
        // the garden wall along the footprint's edge round the ground terrace
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                if (Math.max(Math.abs(u), Math.abs(v)) != R || level(u, v) != 0) continue;
                if (v == -R && Math.abs(u) <= 7) continue;
                int top = Math.max(5, q.land(u, v) + 1);
                for (int y = 1; y <= top; y++) if (y == 3) f.set(u, y, v, PRISMARINE, 2); else f.masonry(u, y, v);
                if (((u + v) & 1) == 0) f.masonry(u, top + 1, v);
            }
        if (!q.hit(-7, -50, 7, -44)) return;
        // the Gate of Pearls: two towers and an arched passage, stepped to the land outside
        int gy = p.gateY, arch = Math.max(6, gy + 4);
        for (int side = -1; side <= 1; side += 2)
            for (int a = 3; a <= 7; a++)
                for (int v = -49; v <= -45; v++) {
                    int u = side * a;
                    if (!q.in(u, v)) continue;
                    int from = Math.min(1, q.land(u, v) + 1);
                    boolean shell = a == 3 || a == 7 || v == -49 || v == -45;
                    for (int y = from; y <= 11; y++) if (y == 6 || y == 11) f.set(u, y, v, PRISMARINE, 2); else f.masonry(u, y, v);
                    if (shell && ((a + v) & 1) == 0) f.masonry(u, 12, v);
                }
        for (int side = -1; side <= 1; side += 2) q.set(side * 5, 12, -47, SEA_LANTERN, 0);
        for (int u = Math.max(q.u0, -2); u <= Math.min(q.u1, 2); u++)
            for (int v = Math.max(q.v0, -50); v <= Math.min(q.v1, -45); v++) {
                int j = v + 50, y;
                if (gy < 0 && j < -gy) { y = gy + 1 + j; f.set(u, y, v, BRICK_STAIRS, f.stairs(0, 1, false)); }
                else if (gy > 0 && j < gy) { y = gy - j; f.set(u, y, v, BRICK_STAIRS, f.stairs(0, -1, false)); for (int k = 1; k < y; k++) f.masonry(u, k, v); }
                else { y = 0; f.set(u, 0, v, PRISMARINE, 1); }
                f.clear(u, v, y + 1, arch - 1);
                if (v >= -49) { f.set(u, arch, v, PRISMARINE, 2); f.masonry(u, arch + 1, v); if (((u + v) & 1) == 0) f.masonry(u, arch + 2, v); }
            }
    }

    // ---------------------------------------------------------------------------------------------- the falling tears
    private static void cascade(Pen q, int k) {
        int lo = FL[k - 1], hi = FL[k], vf = VF[k];
        if (q.hit(-4, vf - 4, 4, vf)) {
            q.box(-4, lo - 2, vf - 3, 4, lo, vf, b(PRISMARINE, 2));
            q.box(-3, lo - 1, vf - 3, 3, lo, vf, b(Canvas.WATER, 0));
            for (int v = vf - 3; v <= vf - 1; v++) { q.set(-4, lo + 1, v, SLAB, 7); q.set(4, lo + 1, v, SLAB, 7); }
            if (k > 1) for (int u = -4; u <= 4; u++) if (Math.abs(u) > 1) q.set(u, lo + 1, vf - 4, SLAB, 7);
        }
        if (q.hit(-1, vf, 1, vf)) q.box(-1, lo + 1, vf, 1, hi, vf, b(Canvas.WATER, 0));
        int back = k == 5 ? 16 : VF[k + 1] - 4;
        if (q.hit(-2, vf + 1, 2, back)) {
            q.box(-2, hi - 1, vf + 1, 2, hi - 1, back, b(PRISMARINE, 1));
            q.box(-1, hi, vf + 1, 1, hi, back, b(Canvas.WATER, 0));
            for (int v = vf + 1; v <= back; v++) { q.set(-2, hi + 1, v, SLAB, 7); q.set(2, hi + 1, v, SLAB, 7); }
        }
    }

    /** The flights of stairs up a terrace wall: a pair beside the falls and a pair near its ends, railed outside. */
    private static void flights(Pen q, int k) {
        Frame f = q.f;
        int lo = FL[k - 1], vf = VF[k], stair = k == 5 ? QUARTZ_STAIRS : BRICK_STAIRS;
        int[][] spans = {{IN[k], IN[k] + 2, IN[k] == 5 ? 8 : IN[k] - 1, IN[k] + 3}, {HW[k] - 9, HW[k] - 7, HW[k] - 10, HW[k] - 6}};
        for (int side = -1; side <= 1; side += 2)
            for (int[] sp : spans) {
                if (!q.hit(Math.min(side * sp[2], side * sp[3]), vf - 7, Math.max(side * sp[2], side * sp[3]), vf - 1)
                    && !q.hit(Math.min(side * sp[0], side * sp[1]), vf - 7, Math.max(side * sp[0], side * sp[1]), vf - 1)) continue;
                for (int d = 1; d <= 7; d++) {
                    int v = vf - d, y = lo + 8 - d;
                    for (int m = sp[0]; m <= sp[1]; m++) {
                        int u = side * m;
                        if (!q.in(u, v)) continue;
                        q.col(u, v, lo + 1, y - 1, MAS);
                        f.set(u, y, v, stair, f.stairs(0, 1, false));
                    }
                    for (int rail = sp[2]; rail <= sp[3]; rail += Math.max(1, sp[3] - sp[2])) {
                        int u = side * rail;
                        if (!q.in(u, v)) continue;
                        q.col(u, v, lo + 1, y, MAS);
                        f.set(u, y + 1, v, WALL, 1);
                    }
                }
            }
        if ((k & 1) == 1) for (int side = -1; side <= 1; side += 2) q.set(side * (IN[k] + 3), FL[k] + 2, vf, SEA_LANTERN, 0);
    }

    // ------------------------------------------------------------------------------------------ terrace 0: the garden
    /** The Queen's Mirror: the great pool of the forecourt with her effigy; its bottom is the cistern's vault. */
    private static void mirror(Pen q) {
        if (!q.hit(-11, -46, 11, -35)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -11); u <= Math.min(q.u1, 11); u++)
            for (int v = Math.max(q.v0, -46); v <= Math.min(q.v1, -35); v++) {
                double e = u * u / 72.25 + (v + 40) * (v + 40) / 20.25;
                if (e <= 1) { f.set(u, -2, v, PRISMARINE, 2); f.set(u, -1, v, Canvas.WATER); f.set(u, 0, v, Canvas.WATER); }
                else if (e <= 1.25 && !(Math.abs(u) <= 4 && v >= -36)) { f.set(u, 0, v, QUARTZ, 0); f.set(u, 1, v, SLAB, 7); }
            }
        q.box(-1, -2, -41, 1, 1, -39, b(QUARTZ, 0));
        grandStatue(q, 0, 2, -40, true);
    }

    /** Two parterres of stone hedges, beds of stone, statues, urns and a pavilion each; the avenue of the guards. */
    private static void garden(Pen q) {
        Frame f = q.f;
        for (int side = -1; side <= 1; side += 2) {
            int cu = 25 * side, cv = -42;
            if (q.hit(cu - 10, cv - 5, cu + 10, cv + 5)) {
                for (int u = Math.max(q.u0, cu - 10); u <= Math.min(q.u1, cu + 10); u++)
                    for (int v = Math.max(q.v0, cv - 5); v <= Math.min(q.v1, cv + 5); v++) {
                        int du = Math.abs(u - cu), dv = Math.abs(v - cv);
                        boolean path = du <= 1 || dv <= 1;
                        if (path) { f.set(u, 0, v, GRAVEL); continue; }
                        boolean hedge = du == 10 || dv == 5 || du == 2 || du == 9 || dv == 2 || dv == 4;
                        f.set(u, 0, v, STONE, ((du + dv) % 3 == 0) ? 6 : 5);
                        if (hedge && !(du == 10 && dv == 5)) f.set(u, 1, v, WALL, 1);
                    }
                pavilion(q, cu, 0, cv, false);
                for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
                    figure(q, cu + 6 * a, 1, cv + 3 * b, a > 0 ? 0 : 1, -a, 0);
                    urn(q, cu + 10 * a, 1, cv + 5 * b);
                }
            }
            for (int v = -47; v <= -43; v += 4) if (q.hit(side * 11, v, side * 11, v)) figure(q, side * 11, 1, v, 1, -side, 0);
        }
    }

    // ----------------------------------------------------------------------------------------- terraces 1 to 4
    /** The Colonnade of Tides: a ruined portico along the first terrace, and the tide-callers behind it. */
    private static void colonnade(Pen q) {
        Frame f = q.f;
        if (q.hit(-44, -31, 44, -28)) {
            int[] cols = {15, 19, 23, 27, 31, 39, 43};
            for (int side = -1; side <= 1; side += 2)
                for (int c : cols)
                    for (int v = -31; v <= -28; v += 3) {
                        int u = side * c;
                        if (!q.in(u, v)) continue;
                        for (int y = 8; y <= 11; y++) f.set(u, y, v, QUARTZ, 2);
                        f.set(u, 12, v, BRICK, 3);
                    }
            for (int u = Math.max(q.u0, -44); u <= Math.min(q.u1, 44); u++) {
                if (Math.abs(u) < 14) continue;
                for (int v = Math.max(q.v0, -31); v <= Math.min(q.v1, -28); v++) {
                    boolean beam = v == -31 || v == -28;
                    if (!beam && !f.keep(u, 13, v, 0.8)) continue;
                    f.set(u, 13, v, PRISMARINE, beam ? 2 : 1);
                }
            }
        }
        for (int side = -1; side <= 1; side += 2)
            for (int a = 16; a <= 26; a += 10) if (q.hit(side * a, -25, side * a, -25)) figure(q, side * a, 8, -25, 0, 0, -1);
    }

    /** The Weeping Pools: two long pools with fountains, and the weeping maidens at their rims. */
    private static void weepingPools(Pen q) {
        Frame f = q.f;
        for (int side = -1; side <= 1; side += 2) {
            int a0 = side < 0 ? -27 : 9, a1 = side < 0 ? -9 : 27;
            if (!q.hit(a0, -20, a1, -13)) continue;
            for (int u = Math.max(q.u0, a0); u <= Math.min(q.u1, a1); u++)
                for (int v = Math.max(q.v0, -20); v <= Math.min(q.v1, -13); v++) {
                    int au = Math.abs(u);
                    boolean rim = au == 9 || au == 27 || v == -20 || v == -13;
                    if (rim) { f.set(u, 14, v, QUARTZ, 0); f.set(u, 15, v, SLAB, 7); }
                    else { f.set(u, 13, v, PRISMARINE, 2); f.set(u, 14, v, Canvas.WATER); }
                }
            int fu = side * 18;
            if (q.in(fu, -16)) { for (int y = 14; y <= 16; y++) f.set(fu, y, -16, PRISMARINE, 1); f.set(fu, 17, -16, BRICK, 3); f.set(fu, 18, -16, Canvas.WATER); }
            for (int a = 13; a <= 23; a += 10) if (q.hit(side * a, -20, side * a, -20)) figure(q, side * a, 15, -20, 0, 0, 1);
        }
    }

    /** The Court of the Suitors: two rows of kneeling statues facing the palace, and the pavilions of waiting. */
    private static void suitors(Pen q) {
        Frame f = q.f;
        for (int side = -1; side <= 1; side += 2) {
            if (q.hit(Math.min(side * 10, side * 22), -6, Math.max(side * 10, side * 22), -2))
                for (int a = 10; a <= 22; a += 3)
                    for (int v = -6; v <= -2; v += 4) {
                        int u = side * a;
                        if (!q.in(u, v)) continue;
                        f.set(u, 22, v, BRICK_STAIRS, f.stairs(0, -1, false));
                        f.set(u, 23, v, STONE, 5);
                        f.set(u, 24, v, WALL, 0);
                    }
            pavilion(q, side * 33, 21, -5, true);
        }
    }

    /** A tree of coral stone in the Petrified Garden: a trunk of walls and a ragged crown of prismarine. */
    private static void tree(Pen q, int u, int v, int y, int h, int r) {
        if (!q.hit(u - r, v - r, u + r, v + r)) return;
        Frame f = q.f;
        if (q.in(u, v)) {
            f.set(u, y + 1, v, STONE, 6);
            for (int i = 2; i <= h; i++) f.set(u, y + i, v, WALL, 1);
        }
        int cy = y + h + 1;
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                if (!q.in(u + a, v + b)) continue;
                for (int dy = -r + 1; dy <= r - 1; dy++) {
                    double e = (a * a + b * b) / (r * r + 0.5) + dy * dy / ((r - 0.5) * (r - 0.5));
                    if (e > 1 || !f.keep(u + a, cy + dy, v + b, 0.82)) continue;
                    double c = f.roll(u + a, cy + dy, v + b, 61);
                    if (c < 0.42) f.set(u + a, cy + dy, v + b, PRISMARINE, 0);
                    else if (c < 0.66) f.set(u + a, cy + dy, v + b, PRISMARINE, 1);
                    else if (c < 0.88) f.set(u + a, cy + dy, v + b, PRISMARINE, 2);
                    else if (c < 0.98) f.set(u + a, cy + dy, v + b, MOSSY);
                    else f.set(u + a, cy + dy, v + b, SEA_LANTERN);
                }
            }
    }

    // ---------------------------------------------------------------------------------------- terrace 5: the summit
    /** The Court of Tears: the weeping statue in the source basin, the lamps of the court, its guards and the portico. */
    private static void court(Pen q) {
        Frame f = q.f;
        if (q.hit(-5, 16, 5, 22)) {
            q.box(-5, 33, 16, 5, 35, 22, b(PRISMARINE, 2));
            q.box(-4, 34, 17, 4, 35, 21, b(Canvas.WATER, 0));
            for (int u = -5; u <= 5; u++)
                for (int v = 16; v <= 22; v++)
                    if ((Math.abs(u) == 5 || v == 16 || v == 22) && !(Math.abs(u) <= 1 && v == 16)) q.set(u, 36, v, SLAB, 7);
            q.box(-1, 33, 18, 1, 37, 20, b(QUARTZ, 0));
            grandStatue(q, 0, 38, 19, true);
            q.set(0, 42, 18, Canvas.WATER, 0);                                   // her tears
        }
        for (int side = -1; side <= 1; side += 2) {
            int cu = side * 22, cv = 19;
            if (q.hit(cu - 1, cv - 1, cu + 1, cv + 1)) {
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++) {
                        boolean corner = a != 0 && b != 0;
                        for (int y = 36; y <= 50; y++) {
                            if (y <= 38 || y == 50) f.set(cu + a, y, cv + b, BRICK, corner ? 3 : 0);
                            else f.set(cu + a, y, cv + b, PRISMARINE, corner ? 1 : 2);
                        }
                        f.set(cu + a, 51, cv + b, corner ? AIR : QUARTZ, 0);
                        if (corner) f.set(cu + a, 51, cv + b, WALL, 1);
                    }
                q.set(cu, 51, cv, SEA_LANTERN, 0);
                q.set(cu, 52, cv, QUARTZ, 0);
                for (int y = 53; y <= 55; y++) q.set(cu, y, cv, QUARTZ, 2);
            }
            for (int v = 18; v <= 24; v += 6) if (q.hit(side * 9, v, side * 9, v)) figure(q, side * 9, 36, v, 0, 0, -1);
        }
        // the portico before the palace
        if (q.hit(-8, 24, 8, 26)) {
            q.box(-8, 35, 24, 8, 35, 26, b(QUARTZ, 0));
            for (int u : new int[] {-7, -3, 3, 7})
                if (q.in(u, 25)) { for (int y = 36; y <= 45; y++) f.set(u, y, 25, QUARTZ, 2); f.set(u, 46, 25, BRICK, 3); }
            q.box(-8, 47, 24, 8, 47, 26, b(PRISMARINE, 1));
            q.box(-8, 48, 24, 8, 48, 26, b(PRISMARINE, 2));
            for (int i = 0; i < 4; i++) {
                int w = 7 - 2 * i;
                for (int u = -w; u <= w; u++)
                    for (int v = 24; v <= 26; v++) {
                        if (!q.in(u, v)) continue;
                        boolean edge = Math.abs(u) >= w - 1;
                        if (edge && v == 24) f.set(u, 49 + i, v, QUARTZ_STAIRS, f.stairs(u < 0 ? 1 : -1, 0, false));
                        else f.set(u, 49 + i, v, edge ? PRISMARINE : QUARTZ, edge ? 2 : 0);
                    }
            }
        }
    }

    private static void palaceStone(Frame f, int u, int y, int v) {
        if (y == 36 || y == 43 || y == 50) { f.set(u, y, v, PRISMARINE, 2); return; }
        double r = f.roll(u, y, v, 43);
        if (r < 0.60) f.set(u, y, v, PRISMARINE, 1);
        else if (r < 0.72) f.set(u, y, v, PRISMARINE, 0);
        else if (r < 0.85) f.set(u, y, v, BRICK, 2);
        else if (r < 0.94) f.set(u, y, v, BRICK, 1);
        else f.set(u, y, v, BRICK, 0);
    }

    /** The Queen's palace: outer walls, the throne hall under its drum and dome, two wings of two floors, four towers. */
    private static void palace(Pen q) {
        if (!q.hit(-29, 26, 29, 50)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -26); u <= Math.min(q.u1, 26); u++)
            for (int v = Math.max(q.v0, 27); v <= Math.min(q.v1, 50); v++) {
                int au = Math.abs(u);
                boolean outer = au == 26 || v == 27 || v == 50, inner = au == 10;
                if (outer || inner) {
                    int along = v == 27 || v == 50 ? u : v;
                    for (int y = 36; y <= 50; y++) {
                        if (outer && Math.floorMod(along, 6) == 0 && y < 50 && y != 43) f.set(u, y, v, QUARTZ, 2);
                        else palaceStone(f, u, y, v);
                    }
                    if (outer && ((u + v) & 1) == 0) palaceStone(f, u, 51, v);
                    // windows: lancets of bars between the pilasters
                    if (outer && Math.floorMod(along, 6) == 3) {
                        boolean side = au == 26;
                        boolean ok = side ? v >= 33 && v <= 41 : au >= 6 && au <= 16;
                        if (ok) for (int y = 38; y <= 47; y++) if (y <= 40 || y >= 45) f.set(u, y, v, BARS, 0);
                    }
                    continue;
                }
                if (au > 10) {
                    if (!(au <= 12 && v >= 34 && v <= 40)) f.set(u, 43, v, PRISMARINE, 1);
                    f.set(u, 50, v, PRISMARINE, 2);
                } else {
                    if (u * u + (v - 39) * (v - 39) > 90) f.set(u, 50, v, PRISMARINE, 2);
                    // the hall's floor: a carpet of wool down the aisle, dark stone either side
                    if (au <= 2 && v <= 43) f.set(u, 35, v, WOOL, au == 2 ? 9 : 11);
                    else if (au > 2) f.set(u, 35, v, ((u + v) & 1) == 0 ? STONE : PRISMARINE, ((u + v) & 1) == 0 ? 6 : 2);
                }
            }
        // doors: the portal, the side doors from the court, the hall's doorways into the wings
        q.box(-2, 36, 27, 2, 41, 27, b(AIR, 0));
        for (int side = -1; side <= 1; side += 2) {
            q.box(side * 3, 36, 27, side * 3, 42, 27, b(QUARTZ, 1));
            q.box(side * 17, 36, 27, side * 18, 38, 27, b(AIR, 0));
            q.box(side * 17, 39, 27, side * 18, 39, 27, b(BRICK, 3));
            q.box(side * 10, 36, 29, side * 10, 39, 31, b(AIR, 0));
            q.box(side * 10, 36, 44, side * 10, 39, 46, b(AIR, 0));
        }
        q.box(-2, 42, 27, 2, 42, 27, b(QUARTZ, 0));
        q.set(0, 43, 27, SEA_LANTERN, 0);
        hall(q);
        wing(q, -1);
        wing(q, 1);
        dome(q);
        for (int side = -1; side <= 1; side += 2) { tower(q, side * 25, 29, side); tower(q, side * 25, 47, side); }
    }

    /** The throne hall: pillars of pale stone between her pools, the dais and the throne at the back. */
    private static void hall(Pen q) {
        Frame f = q.f;
        if (!q.hit(-9, 28, 9, 49)) return;
        for (int side = -1; side <= 1; side += 2) {
            q.box(side * 6, 34, 33, side * 8, 34, 42, b(PRISMARINE, 2));
            q.box(side * 6, 35, 33, side * 8, 35, 42, b(Canvas.WATER, 0));
            for (int v = 30; v <= 45; v += 3) {
                int u = side * 5;
                if (!q.in(u, v)) continue;
                for (int y = 36; y <= 48; y++) f.set(u, y, v, (y == 44 && (v == 36 || v == 42)) ? SEA_LANTERN : QUARTZ, (y == 44 && (v == 36 || v == 42)) ? 0 : 2);
                f.set(u, 49, v, BRICK, 3);
            }
        }
        // the dais
        for (int u = Math.max(q.u0, -6); u <= Math.min(q.u1, 6); u++)
            for (int v = Math.max(q.v0, 44); v <= Math.min(q.v1, 49); v++) {
                if (Math.abs(u) == 5 && (v == 45)) continue;
                if (v == 44) f.set(u, 36, v, QUARTZ_STAIRS, f.stairs(0, 1, false));
                else f.set(u, 36, v, Math.abs(u) == 6 || v == 49 ? QUARTZ : PRISMARINE, Math.abs(u) == 6 || v == 49 ? 0 : 2);
            }
        // the throne
        q.set(0, 37, 48, QUARTZ_STAIRS, f.stairs(0, 1, false));
        q.set(-1, 37, 48, SLAB, 7);
        q.set(1, 37, 48, SLAB, 7);
        for (int y = 37; y <= 41; y++) q.set(0, y, 49, PRISMARINE, 2);
        for (int y = 37; y <= 39; y++) { q.set(-1, y, 49, PRISMARINE, 1); q.set(1, y, 49, PRISMARINE, 1); }
        q.set(0, 42, 49, SEA_LANTERN, 0);
        q.set(-1, 40, 49, QUARTZ_STAIRS, f.stairs(1, 0, true));
        q.set(1, 40, 49, QUARTZ_STAIRS, f.stairs(-1, 0, true));
    }

    /** A wing: the Queen's Bath under the Library of Tides (west), the Treasury under her Chamber (east). */
    private static void wing(Pen q, int side) {
        Frame f = q.f;
        if (!q.hit(Math.min(side * 11, side * 25), 28, Math.max(side * 11, side * 25), 49)) return;
        // the stair to the upper floor along the hall wall
        for (int j = 0; j <= 7; j++)
            for (int m = 11; m <= 12; m++) {
                int u = side * m, v = 34 + j;
                if (!q.in(u, v)) continue;
                for (int y = 36; y < 36 + j; y++) f.set(u, y, v, BRICK, 0);
                f.set(u, 36 + j, v, BRICK_STAIRS, f.stairs(0, 1, false));
            }
        if (side < 0) {
            // the Queen's Bath
            q.box(-23, 33, 33, -13, 35, 45, b(PRISMARINE, 1));
            q.box(-22, 34, 34, -14, 35, 44, b(Canvas.WATER, 0));
            for (int u = -23; u <= -13; u++)
                for (int v = 33; v <= 45; v++) {
                    boolean ring = u == -23 || u == -13 || v == 33 || v == 45;
                    if (!ring) continue;
                    boolean post = (u == -23 || u == -13) && (v == 33 || v == 45);
                    if (post) for (int y = 36; y <= 42; y++) q.set(u, y, v, QUARTZ, 2);
                    else q.set(u, 36, v, SLAB, 7);
                }
            // the Library of Tides
            for (int v = 34; v <= 42; v++) for (int y = 44; y <= 46; y++) q.set(-25, y, v, BOOKSHELF, 0);
            for (int v = 36; v <= 40; v += 4)
                for (int u = -22; u <= -16; u++) { q.set(u, 44, v, BOOKSHELF, 0); q.set(u, 45, v, BOOKSHELF, 0); }
            for (int u = -20; u <= -18; u++) q.set(u, 44, 44, SLAB, 13);
        } else {
            // the Treasury of the Drowned: urns of drowned gold along the walls
            for (int v = 33; v <= 43; v += 5) urn(q, 21, 36, v);
            // the Queen's Chamber: her bier and her mirror
            for (int u = 17; u <= 19; u++)
                for (int v = 43; v <= 46; v++) { q.set(u, 44, v, QUARTZ, 0); q.set(u, 45, v, CARPET, 11); }
            for (int v = 42; v <= 47; v++) for (int y = 44; y <= 47; y++) q.set(11, y, v, GLASS, 0);
        }
    }

    /** The drum and dome over the throne hall, an oculus of glowstone and a lantern spire. */
    private static void dome(Pen q) {
        Frame f = q.f;
        if (!q.hit(-11, 28, 11, 50)) return;
        for (int u = Math.max(q.u0, -11); u <= Math.min(q.u1, 11); u++)
            for (int v = Math.max(q.v0, 28); v <= Math.min(q.v1, 50); v++) {
                int dv = v - 39, d2 = u * u + dv * dv;
                if (d2 > 90 && d2 <= 110) for (int y = 50; y <= 53; y++) {
                    boolean window = y >= 51 && y <= 52 && (u == 0 || dv == 0);
                    f.set(u, y, v, window ? AIR : PRISMARINE, window ? 0 : (y == 53 ? 2 : 1));
                }
                for (int y = 54; y <= 64; y++) {
                    int dy = y - 53, dd = d2 + dy * dy;
                    if (dd <= 90 || dd > 110) continue;
                    boolean rib = u == 0 || dv == 0 || Math.abs(u) == Math.abs(dv);
                    f.set(u, y, v, PRISMARINE, rib ? 1 : 2);
                }
                if (d2 <= 2) f.set(u, 63, v, GLOWSTONE);
            }
        for (int y = 64; y <= 66; y++) q.set(0, y, 39, QUARTZ, 2);
        q.set(0, 67, 39, SEA_LANTERN, 0);
        q.set(0, 68, 39, QUARTZ, 1);
    }

    /** A round corner tower: a ladder to the lookout under a cone roof, doors to the wing's floors and the roof. */
    private static void tower(Pen q, int cu, int cv, int side) {
        Frame f = q.f;
        if (!q.hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
        for (int a = -4; a <= 4; a++)
            for (int b = -4; b <= 4; b++) {
                int u = cu + a, v = cv + b, d = a * a + b * b;
                if (!q.in(u, v)) continue;
                if (d <= 6) {
                    f.clear(u, v, 36, 59);
                    f.set(u, 60, v, PRISMARINE, 1);
                    f.clear(u, v, 61, 64);
                } else if (d <= 12) {
                    for (int y = 36; y <= 64; y++) {
                        boolean window = y >= 61 && y <= 63 && (a == 0 || b == 0);
                        if (window) f.set(u, y, v, AIR);
                        else if (y == 43 || y == 50 || y == 60) f.set(u, y, v, PRISMARINE, 2);
                        else palaceStone(f, u, y, v);
                    }
                }
                for (int i = 0; i <= 7; i++) {
                    double rr = 4.4 - i * 0.6;
                    if (d <= rr * rr) f.set(u, 65 + i, v, PRISMARINE, i == 0 ? 1 : 2);
                }
            }
        q.set(cu, 73, cv, QUARTZ, 2);
        q.set(cu, 74, cv, QUARTZ, 2);
        q.set(cu, 61, cv, SEA_LANTERN, 0);
        // the ladder on the outer side, and the doors toward the wing
        int lu = cu + 2 * side;
        for (int y = 36; y <= 60; y++) q.set(lu, y, cv, LADDER, f.facing(-side, 0));
        for (int y : new int[] {36, 37, 44, 45, 51, 52}) q.set(cu - 3 * side, y, cv, AIR, 0);
    }

    // ------------------------------------------------------------------------------------------ the grottoes
    private static void grotto(Pen q, Plan p, int k, int side) {
        int gc = side * GC[k], lo = FL[k - 1], vf = VF[k];
        if (!q.hit(gc - 5, vf, gc + 5, vf + 8)) return;
        Frame f = q.f;
        boolean descent = k == 1 && side < 0;
        for (int u = Math.max(q.u0, gc - 5); u <= Math.min(q.u1, gc + 5); u++)
            for (int v = Math.max(q.v0, vf); v <= Math.min(q.v1, vf + 8); v++) {
                int du = Math.abs(u - gc);
                if (v == vf) {                                              // the arch in the terrace wall
                    if (du <= 1) { f.clear(u, v, lo + 1, lo + 3); f.set(u, lo + 4, v, QUARTZ, 1); }
                    else if (du == 2) for (int y = lo + 1; y <= lo + 4; y++) f.set(u, y, v, QUARTZ, 2);
                    continue;
                }
                boolean wall = du == 5 || v == vf + 8;
                f.set(u, lo, v, PRISMARINE, f.roll(u, lo, v, 51) < 0.7 ? 1 : 2);
                f.set(u, lo + 6, v, PRISMARINE, 2);
                for (int y = lo + 1; y <= lo + 5; y++) {
                    if (wall) { if (y == lo + 3) f.set(u, y, v, PRISMARINE, 2); else f.masonry(u, y, v); }
                    else f.set(u, y, v, AIR);
                }
                if (!wall && f.roll(u, lo + 5, v, 52) < 0.10) f.set(u, lo + 5, v, WALL, 1);         // stone drips
                if (!wall && du == 4 && v >= vf + 3 && f.roll(u, lo + 1, v, 53) < 0.3) f.set(u, lo + 1, v, WEB);
            }
        if (!descent) {
            q.box(gc - 1, lo - 1, vf + 3, gc + 1, lo - 1, vf + 5, b(PRISMARINE, 2));
            q.box(gc - 1, lo, vf + 3, gc + 1, lo, vf + 5, b(Canvas.WATER, 0));
            figure(q, gc, lo + 1, vf + 7, 2, 0, -1);
        }
        for (int a = -3; a <= 3; a += 6)
            for (int v = vf + 2; v <= vf + 6; v += 4) {
                if (!q.in(gc + a, v)) continue;
                for (int y = lo + 1; y <= lo + 4; y++) f.set(gc + a, y, v, PRISMARINE, 1);
                f.set(gc + a, lo + 5, v, BRICK, 3);
            }
        q.set(gc, lo + 6, vf + 4, SEA_LANTERN, 0);
        if (descent) {
            // the stair down to the cistern: its well in the grotto floor, railed
            for (int i = 1; i <= 10; i++) {
                int v = -26 - i, y = -i;
                for (int u = -24; u <= -20; u++) {
                    if (!q.in(u, v)) continue;
                    boolean wall = u == -24 || u == -20;
                    int top = v >= -32 ? 0 : y + 4;
                    if (wall) {
                        for (int yy = y - 1; yy <= top; yy++) f.masonry(u, yy, v);
                        if (v >= -32) f.set(u, 1, v, WALL, 1);
                        else f.masonry(u, y + 5, v);
                    } else {
                        f.masonry(u, y - 1, v);
                        f.set(u, y, v, BRICK_STAIRS, f.stairs(0, 1, false));
                        f.clear(u, v, y + 1, top);
                        if (v < -32) f.masonry(u, y + 5, v);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ the cistern
    private static boolean walk(int u, int v) {
        return v >= -44 && v <= -42 || Math.abs(u) <= 1 || u >= -24 && u <= -20 && v >= -41
            || Math.abs(u) >= 11 && Math.abs(u) <= 13 && v <= -45 || Math.abs(u) <= 3 && v <= -46;
    }

    private static boolean pillar(int u, int v) {
        int au = Math.abs(u);
        return (au == 7 || au == 8 || au == 18 || au == 19) && (v == -47 || v == -46 || v == -40 || v == -39);
    }

    /** The Cistern of Tears under the forecourt: a flooded hall of pillars, causeways, islands and the altar of tears. */
    private static void cistern(Pen q) {
        Frame f = q.f;
        if (!q.hit(-25, -49, 25, -37)) return;
        for (int u = Math.max(q.u0, -25); u <= Math.min(q.u1, 25); u++)
            for (int v = Math.max(q.v0, -49); v <= Math.min(q.v1, -37); v++) {
                boolean wall = Math.abs(u) == 25 || v == -49 || v == -37;
                f.masonry(u, -12, v);
                f.set(u, -2, v, BRICK, 0);
                if (wall) {
                    boolean door = v == -37 && u >= -23 && u <= -21;
                    for (int y = -11; y <= -3; y++) {
                        if (door && y >= -9 && y <= -6) f.set(u, y, v, AIR);
                        else if (door && y == -10) f.set(u, y, v, BRICK, 0);
                        else if (y == -7) f.set(u, y, v, PRISMARINE, 2);
                        else f.masonry(u, y, v);
                    }
                    continue;
                }
                if (pillar(u, v)) {
                    for (int y = -11; y <= -3; y++) f.set(u, y, v, (y == -6 && Math.abs(u) == 7 && v == -40) ? SEA_LANTERN : PRISMARINE, y == -3 ? 2 : 1);
                    continue;
                }
                if (walk(u, v)) { f.masonry(u, -11, v); f.set(u, -10, v, BRICK, f.roll(u, -10, v, 54) < 0.3 ? 2 : 0); }
                else { f.set(u, -11, v, Canvas.WATER); f.set(u, -10, v, Canvas.WATER); }
                f.clear(u, v, -9, -4);
                boolean beam = v == -47 || v == -46 || v == -40 || v == -39 || Math.abs(u) == 7 || Math.abs(u) == 8 || Math.abs(u) == 18 || Math.abs(u) == 19;
                if (beam) f.masonry(u, -3, v); else f.set(u, -3, v, AIR);
            }
        // the drowned court: kneeling statues in the water, and the altar of tears
        for (int a = -4; a <= 4; a += 8)
            for (int v = -48; v <= -39; v += 9) {
                if (!q.in(a, v)) continue;
                f.set(a, -11, v, BRICK_STAIRS, f.stairs(0, -1, false));
                f.set(a, -10, v, STONE, 5);
                f.set(a, -9, v, WALL, 1);
            }
        q.set(0, -9, -47, BRICK, 3);
        q.set(0, -8, -47, CAULDRON, 3);
    }

    // ------------------------------------------------------------------------------------------ small works
    /** A grand robed statue facing -v: a hem three wide, the body, the shoulders, the head and a crown of light. */
    private static void grandStatue(Pen q, int cu, int y0, int cv, boolean crown) {
        if (!q.hit(cu - 1, cv - 1, cu + 1, cv + 1)) return;
        Frame f = q.f;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) { q.set(cu + a, y0, cv + b, QUARTZ, 0); q.set(cu + a, y0 + 1, cv + b, QUARTZ, 0); }
        q.set(cu, y0 + 2, cv, QUARTZ, 0);
        q.set(cu - 1, y0 + 2, cv, QUARTZ, 0);
        q.set(cu + 1, y0 + 2, cv, QUARTZ, 0);
        q.set(cu, y0 + 2, cv - 1, PRISMARINE, 1);
        q.set(cu, y0 + 2, cv + 1, QUARTZ, 0);
        q.set(cu, y0 + 3, cv, QUARTZ, 2);
        q.set(cu - 1, y0 + 3, cv, QUARTZ_STAIRS, f.stairs(1, 0, true));
        q.set(cu + 1, y0 + 3, cv, QUARTZ_STAIRS, f.stairs(-1, 0, true));
        q.set(cu, y0 + 4, cv, QUARTZ, 1);
        if (crown) q.set(cu, y0 + 5, cv, SEA_LANTERN, 0);
    }

    /**
     * A figure on a plinth at (u, y, v) facing (fu, fv): the plinth, a robe two high, its shoulders, a head. Material 0
     * marble, 1 stone, 2 sea-stone.
     */
    private static void figure(Pen q, int u, int y, int v, int mat, int fu, int fv) {
        if (!q.hit(u - 1, v - 1, u + 1, v + 1)) return;
        Frame f = q.f;
        int id = mat == 0 ? QUARTZ : mat == 1 ? STONE : PRISMARINE, meta = mat == 0 ? 0 : mat == 1 ? 6 : 1;
        q.set(u, y, v, BRICK, 3);
        q.set(u, y + 1, v, id, meta);
        q.set(u, y + 2, v, id, meta);
        int su = fv != 0 ? 1 : 0, sv = fu != 0 ? 1 : 0;                       // the shoulders lie across the facing
        int st = mat == 0 ? QUARTZ_STAIRS : BRICK_STAIRS;
        q.set(u - su, y + 2, v - sv, st, f.stairs(su, sv, true));
        q.set(u + su, y + 2, v + sv, st, f.stairs(-su, -sv, true));
        if (mat == 0) q.set(u, y + 3, v, QUARTZ, 1);
        else q.set(u, y + 3, v, WALL, mat == 1 ? 0 : 1);
    }

    /** An urn: a cauldron of still water on a carved pedestal. */
    private static void urn(Pen q, int u, int y, int v) {
        if (!q.in(u, v)) return;
        q.set(u, y, v, BRICK, 3);
        q.set(u, y + 1, v, CAULDRON, 3);
    }

    /** A pavilion five wide: corner columns, a roof of dark stone, and (on the upper terraces) a little dome. */
    private static void pavilion(Pen q, int cu, int y, int cv, boolean dome) {
        if (!q.hit(cu - 2, cv - 2, cu + 2, cv + 2)) return;
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                int u = cu + a, v = cv + b;
                if (!q.in(u, v)) continue;
                q.set(u, y, v, QUARTZ, 0);
                if (Math.abs(a) == 2 && Math.abs(b) == 2) for (int k = 1; k <= 4; k++) q.set(u, y + k, v, QUARTZ, 2);
                else for (int k = 1; k <= 4; k++) q.set(u, y + k, v, AIR, 0);
                q.set(u, y + 5, v, PRISMARINE, 2);
                if (Math.abs(a) <= 1 && Math.abs(b) <= 1) q.set(u, y + 6, v, PRISMARINE, dome ? 1 : 2);
                if (dome && a == 0 && b == 0) q.set(u, y + 7, v, PRISMARINE, 1);
            }
    }

    // ------------------------------------------------------------------------------------------ chests, cages, signs
    private static void tiles(Pen q, Plan p) {
        Frame f = q.f;
        long h = p.h;
        // the throne and the palace
        f.chest(3, 37, 48, 0, -1, TREASURE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        f.chest(-3, 37, 48, 0, -1, CROSSING, "trinket:0.25");
        f.chest(25, 36, 35, -1, 0, Sites.DESERT, "trinket:0.3");
        f.chest(25, 36, 41, -1, 0, Sites.JUNGLE, null);
        f.chest(-24, 44, 38, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 7), 0, 99));
        f.chest(24, 44, 38, -1, 0, MANSION, "trinket:0.2");
        f.spawner(18, 36, 40, "SKELETON");
        f.set(0, 36, 30, PLATE, 0);
        f.dispenser(0, 35, 30, 0, 0);
        f.set(11, 36, 30, PLATE, 0);
        f.dispenser(11, 35, 30, 0, 0);
        f.sign(-4, 38, 26, 0, -1, "KNEEL\nAND DROWN");
        f.sign(4, 38, 26, 0, -1, "HER TEARS\nRUN DOWN\nEVERY STAIR");
        // the grottoes
        String[] tables = {Sites.DUNGEON, MINESHAFT, Sites.JUNGLE, Sites.CORRIDOR, IGLOO, Sites.DESERT, Sites.DUNGEON, Sites.SMITH, CROSSING, Sites.JUNGLE};
        String[] cages = {"SPIDER", "ZOMBIE", "CAVE_SPIDER", "WITCH"};
        for (int k = 1; k <= 5; k++)
            for (int side = -1; side <= 1; side += 2) {
                int i = 2 * (k - 1) + (side > 0 ? 1 : 0), gc = side * GC[k], lo = FL[k - 1], vf = VF[k];
                boolean descent = k == 1 && side < 0;
                f.chest(descent ? gc + 3 : gc + 2, lo + 1, vf + 7, 0, -1, tables[i], i % 3 == 0 ? "trinket:0.15" : null);
                if (p.cage[i] && !descent) f.spawner(gc - 2, lo + 1, vf + 7, cages[i % cages.length]);
            }
        f.sign(GC[2] - 4, 10, VF[2] + 7, 0, -1, "SHE WEPT\nUNTIL THE SEA\nCAME TO HER");
        // the cistern
        f.chest(12, -9, -47, 0, 1, Sites.DUNGEON, null);
        f.chest(-12, -9, -47, 0, 1, MINESHAFT, "lore:" + Hash.range(Hash.mix(h ^ 11), 0, 99));
        f.spawner(2, -9, -47, "WITCH");
        f.sign(0, -7, -48, 0, 1, "ALL HER TEARS\nCOME TO REST\nIN THE DEEP");
        // the garden, the gate and the suitors
        f.chest(-25, 1, -42, 0, -1, Sites.DUNGEON, null);
        f.chest(25, 1, -42, 0, -1, IGLOO, "trinket:0.1");
        f.chest(33, 22, -5, 0, -1, Sites.DESERT, null);
        urn(q, -33, 22, -5);
        f.sign(-5, 3, -50, 0, -1, "THE TERRACES\nOF THE\nDROWNED QUEEN");
        f.sign(5, 3, -50, 0, -1, "SHE WEPT\nAND THE SEA\nCAME AT\nHER CALL");
        f.sign(12, 23, 2, 0, -1, "HER SUITORS\nKNEEL STILL\nAND WAIT");
    }

    // ------------------------------------------------------------------------------------------ the pen
    private static final int MAS = -1;

    private static int b(int id, int meta) { return id << 4 | meta; }

    /** Draws clipped to the canvas' chunk and the footprint, in the design's turned frame; knows the land's height. */
    private static final class Pen {
        final Frame f;
        final Plans.GreatSite s;
        final int u0, v0, u1, v1;
        private final int x0, z0;
        private final int[] land = new int[256];

        Pen(Plans.GreatSite s, Canvas c, int rot, int r) {
            this.s = s;
            f = new Frame(c, s.x, s.z, s.base, rot);
            x0 = c.x0;
            z0 = c.z0;
            int[] a = local(rot, c.x0 - s.x, c.z0 - s.z), b = local(rot, c.x0 + 15 - s.x, c.z0 + 15 - s.z);
            u0 = Math.max(-r, Math.min(a[0], b[0]));
            u1 = Math.min(r, Math.max(a[0], b[0]));
            v0 = Math.max(-r, Math.min(a[1], b[1]));
            v1 = Math.min(r, Math.max(a[1], b[1]));
            Arrays.fill(land, Integer.MIN_VALUE);
        }

        static int[] local(int rot, int dx, int dz) {
            switch (rot & 3) {
                case 1: return new int[] {dz, -dx};
                case 2: return new int[] {-dx, -dz};
                case 3: return new int[] {-dz, dx};
                default: return new int[] {dx, dz};
            }
        }

        boolean empty() { return u0 > u1 || v0 > v1; }

        boolean in(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }

        /** The land's height at a column, in local y (from the plan's terrain, never the canvas). */
        int land(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v);
            if (wx < x0 || wx > x0 + 15 || wz < z0 || wz > z0 + 15) return s.surface(wx, wz) - s.base;
            int i = (wx - x0) << 4 | (wz - z0);
            if (land[i] == Integer.MIN_VALUE) land[i] = s.surface(wx, wz) - s.base;
            return land[i];
        }

        void set(int u, int y, int v, int id, int meta) { if (in(u, v)) f.set(u, y, v, id, meta); }

        void put(int u, int y, int v, int m) {
            if (m >= 0) f.set(u, y, v, m >> 4, m & 15);
            else f.masonry(u, y, v);
        }

        void col(int u, int v, int y0, int y1, int m) { for (int y = y0; y <= y1; y++) put(u, y, v, m); }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int A0 = Math.max(Math.min(a0, a1), u0), A1 = Math.min(Math.max(a0, a1), u1);
            int B0 = Math.max(Math.min(b0, b1), v0), B1 = Math.min(Math.max(b0, b1), v1);
            for (int a = A0; a <= A1; a++) for (int b = B0; b <= B1; b++) for (int y = y0; y <= y1; y++) put(a, y, b, m);
        }
    }
}
