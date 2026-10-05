package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Viaduct of the Drowned Kings (epoch 6; its keeper the Toll Keeper). A three-tier arcaded viaduct a hundred and ten
 * blocks long across the whole footprint: great arches on piers that stand in the land on their footings, the Kings'
 * Road on the first tier (an arcade through the second tier's piers), a walk on the second, an aqueduct channel on the
 * third that still runs with water in places; statues of the drowned kings on corbels on the piers, prison cells inside
 * them, a span fallen in the east. At mid-span the toll fortress: its toll hall (where the Toll Keeper waits) straddles
 * the road between portcullised gates, an undercroft of cells below and the kings' crypt under that, quarters, treasury
 * and guardroom above, corner towers and a toll yard before it. Stair towers at both ends climb to every tier. A canal
 * of drowned kings' heads passes under the western arches; at the west end the ruined gatehouse village, at the east a
 * fallen colossus and the toll-takers' barracks. Every block is a function of the site (plan) and of position.
 */
final class GreatViaduct extends GreatDesign {
    static final int R = 56;
    /** Village houses (centre u, v) east of the village road, and the chapel. */
    static final int[][] HOUSES = {{-41, -22}, {-41, -37}, {-41, 22}, {-41, 38}};
    static final int CHU = -50, CHV = 30;

    static final class Plan extends Layout {
        /** The road (tier 1), the walk (tier 2) and the channel floor (tier 3). */
        int K1, K2, K3;
        /** The stair towers' floors (west, east), the fortress' ground floor and crypt floor, the canal's water and bed. */
        int Fw, Fe, GF, CF, cw, cb;
        /** Levelled floors: each house, the chapel, the barracks, the colossus' ground, the gate road. */
        int[] house = new int[HOUSES.length];
        int chapel, barracks, colossus, colossusPoint, gate;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        Frame fr = new Frame(null, s.x, s.z, s.base, s.rot);
        int tmax = -99, tmin = 99;
        for (int u = -54; u <= 54; u += 2)
            for (int v = -8; v <= 8; v += 8) { int t = surf(s, fr, u, v); tmax = Math.max(tmax, t); tmin = Math.min(tmin, t); }
        int tw = surf(s, fr, -50, -7), te = surf(s, fr, 50, -7);
        p.K1 = Math.min(44, Math.max(Math.max(18, tmax + 8), Math.max(tw, te) + 5));
        p.K2 = p.K1 + 10; p.K3 = p.K2 + 10;
        p.Fw = p.K1 - 5 - 10 * Math.floorDiv(p.K1 - 5 - tw, 10);
        p.Fe = p.K1 - 5 - 10 * Math.floorDiv(p.K1 - 5 - te, 10);
        p.GF = Math.max(p.K1 - 20, Math.min(p.K1 - 6, surf(s, fr, 0, -22)));
        p.CF = p.GF - Math.min(10, p.GF + 11);
        int[] cs = new int[28];
        for (int k = 0; k < cs.length; k++) cs[k] = surf(s, fr, -28, -54 + 4 * k);
        Arrays.sort(cs);
        p.cw = Math.max(-7, Math.min(p.K1 - 16, cs[cs.length / 2] - 2));
        p.cb = p.cw - 3;
        for (int k = 0; k < HOUSES.length; k++) p.house[k] = surf(s, fr, HOUSES[k][0], HOUSES[k][1]);
        p.chapel = surf(s, fr, CHU, CHV);
        p.barracks = surf(s, fr, 45, -29);
        p.colossus = surf(s, fr, 43, 27);
        p.gate = surf(s, fr, -50, -44);
        int K1 = p.K1, K2 = p.K2, K3 = p.K3;
        p.boss = new int[] {0, K1 + 1, -4};
        p.garrisons.add(g(-53, p.Fw + 1, 0, "skeleton+stray+zombie"));                   // the west stair tower
        p.garrisons.add(g(53, p.Fe + 1, 0, "husk+zombie_villager+creeper"));             // the east stair tower
        p.garrisons.add(g(-28, K1 + 1, 0, "cult_zealot+vindicator+hound"));              // the Kings' Road, west
        p.garrisons.add(g(16, K1 + 1, 0, "cult_zealot+evoker+ghoul"));                   // the Kings' Road, east
        p.garrisons.add(g(40, K1 + 1, 0, "nightgaunt+mi_go+enderman"));                  // the road beyond the fallen span
        p.garrisons.add(g(-40, K2 + 1, 4, "skeleton+wither_skeleton+stray"));            // the walk on the second tier
        p.garrisons.add(g(40, K3 + 1, 0, "nightgaunt"));                                 // the dry channel
        p.garrisons.add(g(-6, K1 + 1, -5, "!cult_adept+vindicator+illusioner"));         // the toll hall
        p.garrisons.add(g(0, p.GF + 1, 6, "ghoul+tomb_crawler+silverfish"));             // the undercroft
        p.garrisons.add(g(0, p.CF + 1, 6, "tomb_crawler+wither_skeleton+endermite"));    // the kings' crypt
        p.garrisons.add(g(-6, K2 + 1, -8, "cult_adept+witch+evoker"));                   // the Toll Keeper's quarters
        p.garrisons.add(g(6, K2 + 1, -8, "shoggoth+slime+creeper"));                     // the treasury
        p.garrisons.add(g(-6, K2 + 7, 8, "mi_go+star_spawn+spider"));                    // the fortress roof
        int pf = surf(s, fr, -35, 0);
        if (pf + 7 < K1 - 8) p.garrisons.add(g(-35, pf + 1, 1, "cave_spider+spider+tomb_crawler")); // the cells in a pier
        else p.garrisons.add(g(-10, p.GF + 1, -20, "cave_spider+spider+tomb_crawler"));              // the toll yard
        p.colossusPoint = surf(s, fr, 43, 10);
        p.garrisons.add(g(HOUSES[0][0], p.house[0] + 1, HOUSES[0][1], "deep_one+zombie+zombie_villager")); // the village
        p.garrisons.add(g(-50, p.gate + 1, -44, "hound+husk+deep_one"));                // the village gate
        p.garrisons.add(g(43, p.colossusPoint + 1, 10, "star_spawn+ghoul+enderman"));        // by the fallen colossus
        p.garrisons.add(g(45, p.barracks + 1, -29, "vindicator+zombie+skeleton"));      // the barracks
        p.garrisons.add(g(-6, p.GF + 1, -24, "deep_one+shoggoth+witch"));               // the toll yard
        p.garrisons.add(g(CHU, p.chapel + 1, CHV - 3, "cult_zealot+cult_adept+ghoul"));  // the chapel of the kings
        return p;
    }

    static int surf(Plans.GreatSite s, Frame fr, int u, int v) { return s.surface(fr.wx(u, v), fr.wz(u, v)) - s.base; }

    /** Whether a u (by its distance from the middle) is a pier of the first two tiers, and the span's centre otherwise. */
    static boolean pier(int au) { return au >= 21 && au <= 24 || au >= 33 && au <= 36; }
    static double spanCentre(int au) { return au <= 20 ? 16.5 : au <= 32 ? 28.5 : 40.5; }
    /** The fallen span. */
    static boolean broken(int u) { return u >= 25 && u <= 32; }

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) { new D(s, (Plan) layout, c).draw(); }

    private static final class D {
        final Plans.GreatSite s;
        final Plan p;
        final Frame f;
        final int u0, u1, v0, v1, cu0, cv0, K1, K2, K3;
        final int[] gnd = new int[256];

        D(Plans.GreatSite s, Plan p, Canvas c) {
            this.s = s; this.p = p;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int a0 = Integer.MAX_VALUE, a1 = Integer.MIN_VALUE, b0 = Integer.MAX_VALUE, b1 = Integer.MIN_VALUE;
            for (int k = 0; k < 4; k++) {
                int x = c.x0 + ((k & 1) == 0 ? 0 : 15), z = c.z0 + ((k & 2) == 0 ? 0 : 15);
                int lu = lu(x, z), lv = lv(x, z);
                a0 = Math.min(a0, lu); a1 = Math.max(a1, lu); b0 = Math.min(b0, lv); b1 = Math.max(b1, lv);
            }
            cu0 = a0; cv0 = b0;
            u0 = Math.max(a0, -R); u1 = Math.min(a1, R); v0 = Math.max(b0, -R); v1 = Math.min(b1, R);
            Arrays.fill(gnd, Integer.MIN_VALUE);
            K1 = p.K1; K2 = p.K2; K3 = p.K3;
        }

        int lu(int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
        int lv(int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }

        int g(int u, int v) {
            int i = (u - cu0) * 16 + (v - cv0);
            if (u - cu0 < 0 || u - cu0 > 15 || v - cv0 < 0 || v - cv0 > 15) return s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
            if (gnd[i] == Integer.MIN_VALUE) gnd[i] = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
            return gnd[i];
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }

        void set(int u, int y, int v, int id, int m) { if (Math.abs(u) <= R && Math.abs(v) <= R && y >= -12) f.set(u, y, v, id, m); }
        void set(int u, int y, int v, int id) { set(u, y, v, id, 0); }
        void air(int u, int v, int y0, int y1) { for (int y = y0; y <= y1; y++) set(u, y, v, AIR); }
        void mas(int u, int v, int y0, int y1) { for (int y = Math.max(-12, y0); y <= y1; y++) if (Math.abs(u) <= R && Math.abs(v) <= R) f.masonry(u, y, v); }

        void fill(int a0, int y0, int b0, int a1, int y1, int b1, int id, int m) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, m);
        }

        int stair(int du, int dv) { return f.stairs(du, dv, false); }

        /** Weathered stone of the kings, green with the sea's patina. */
        void patina(int u, int y, int v) {
            double q = f.roll(u, y, v, 81);
            if (q < 0.30) set(u, y, v, PRISMARINE, 0); else if (q < 0.55) set(u, y, v, BRICK, 1); else if (q < 0.75) set(u, y, v, STONE, 5);
            else if (q < 0.88) set(u, y, v, MOSSY); else set(u, y, v, STONE, 6);
        }

        /** A floor levelled at y over the box: footing below where the land is lower, air above where it is higher. */
        void pad(int a0, int b0, int a1, int b1, int y, boolean pave) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++) {
                    int t = g(u, v);
                    for (int yy = Math.min(t, y) - 1; yy < y; yy++) f.rubble(u, yy, v);
                    if (pave) f.paving(u, y, v); else f.rubble(u, y, v);
                    air(u, v, y + 1, Math.max(t + 1, y + 4));
                }
        }

        // ---------------------------------------------------------------- the whole

        void draw() {
            if (u0 > u1 || v0 > v1) return;
            if (hit(-32, -56, -25, 56)) canal();
            if (hit(-45, -11, 45, 11)) { viaduct(); for (int c : new int[] {-36, -24, 21, 33}) pierCell(c); }
            if (hit(-14, -18, 14, 18)) fortress();
            if (hit(-12, -33, 12, -17)) tollYard();
            if (hit(-56, -56, -36, 48)) village();
            for (int sd = -1; sd <= 1; sd += 2) if (hit(sd * 50 - 6, -18, sd * 50 + 6, 6)) stairTower(sd * 50, sd > 0 ? p.Fe : p.Fw);
            if (hit(36, 12, 52, 44)) colossus();
            if (hit(37, -37, 53, -21)) barracks();
        }

        // ---------------------------------------------------------------- the viaduct's three tiers

        void viaduct() {
            int S1 = K1 - 8, S2 = K1 + 4, S3 = K2 + 5;
            for (int u = Math.max(-44, u0); u <= Math.min(44, u1); u++) {
                int au = Math.abs(u);
                if (au <= 12) continue;
                boolean pr = pier(au), brk = broken(u);
                double x = pr ? 0 : au - spanCentre(au);
                int top1 = pr ? Integer.MIN_VALUE : S1 + (int) Math.floor(Math.sqrt(16.5 - x * x));
                int top2 = pr ? Integer.MIN_VALUE : S2 + (int) Math.floor(Math.sqrt(16.5 - x * x));
                int m6 = Math.floorMod(u, 6);
                double x3 = m6 - 3.5;
                int top3 = m6 <= 1 ? Integer.MIN_VALUE : S3 + (int) Math.floor(Math.sqrt(4.5 - x3 * x3));
                // the fallen span: ragged stubs at its edges, nothing between
                int keep = Integer.MAX_VALUE;
                if (brk) {
                    if (u == 25 || u == 32) keep = S1 + 2 + (int) (Hash.unit(s.hash, u, 7, 0) * (K2 - S1));
                    else keep = Integer.MIN_VALUE;
                }
                for (int v = Math.max(-11, v0); v <= Math.min(11, v1); v++) {
                    int av = Math.abs(v), t = g(u, v);
                    if (u >= -32 && u <= -25) t = Math.max(t, p.cw + 1);
                    if (av > 8) { if (pr && av <= 10) statue(u, v); continue; }
                    // tier one: a pier, or a great arch over the land
                    if (pr) mas(u, v, t - 2, Math.min(K1 - 1, keep));
                    else {
                        if (t < top1) air(u, v, t + 1, Math.min(top1, keep));
                        mas(u, v, Math.max(t + 1, top1 + 1), Math.min(K1 - 1, keep));
                    }
                    if (keep < K1) { if (brk) rubble(u, v, t); continue; }
                    if (av == 8) { f.eldritch(u, K1, v); f.glyph(u, K1 + 1, v); continue; }
                    f.paving(u, K1, v);
                    if (av >= 6) { air(u, v, K1 + 1, K1 + 4); continue; }
                    // tier two: the arcade over the road, its piers pierced by the gallery
                    for (int y = K1 + 1; y < K2; y++) {
                        if (y > keep) break;
                        boolean open = pr ? av <= 1 && y <= K1 + 3 : y <= top2;
                        if (open) set(u, y, v, AIR); else if (y == K1 + 3 && pr && av == 2) f.glyph(u, y, v); else f.masonry(u, y, v);
                    }
                    if (pr && av == 0 && (au == 22 || au == 34)) set(u, K1 + 3, v, SEA_LANTERN);
                    if (keep < K2) continue;
                    if (av == 5) { f.eldritch(u, K2, v); set(u, K2 + 1, v, WALL, 1); continue; }
                    f.paving(u, K2, v);
                    if (av >= 3) { air(u, v, K2 + 1, K2 + 4); continue; }
                    // tier three: the small arcade and the channel on top
                    for (int y = K2 + 1; y < K3; y++) { if (y > keep) break; if (y <= top3) set(u, y, v, AIR); else f.masonry(u, y, v); }
                    if (keep < K3) continue;
                    f.eldritch(u, K3, v);
                    boolean wet = u >= -43 && u <= -16 || u >= 16 && u <= 24;
                    if (av == 2) { f.masonry(u, K3 + 1, v); if (f.keep(u, K3 + 2, v, 0.8)) f.masonry(u, K3 + 2, v); }
                    else if (wet && u != -43 && u != -16 && u != 16 && u != 24) { set(u, K3 + 1, v, Canvas.WATER); air(u, v, K3 + 2, K3 + 3); }
                    else if (wet) { f.masonry(u, K3 + 1, v); air(u, v, K3 + 2, K3 + 3); }
                    else air(u, v, K3 + 1, K3 + 3);
                }
            }
        }

        /** Prison cells hollowed in a pier, a door from under the arch, barred cells at either end. */
        void pierCell(int c) {
            if (!hit(c - 1, -6, c + 4, 6)) return;
            int pf = g(c + 1, 0);
            if (pf + 7 >= K1 - 8) return;
            int side = c > 0 ? c : c + 3;                                      // the side toward the fortress
            for (int u = c + 1; u <= c + 2; u++)
                for (int v = -5; v <= 5; v++) {
                    if (!in(u, v)) continue;
                    f.paving(u, pf, v);
                    air(u, v, pf + 1, pf + 5);
                    if (Math.abs(v) == 3) for (int y = pf + 1; y <= pf + 3; y++) if (!(u == c + 1 && y <= pf + 2)) set(u, y, v, IRON_BARS);
                }
            for (int v = -1; v <= 1; v++) { if (in(side, v)) air(side, v, pf + 1, pf + 3); int out = c > 0 ? c - 1 : c + 4; if (in(out, v)) air(out, v, g(out, v) + 1, pf + 3); }
            if (in(c + 1, 0) || in(c + 2, 0)) set(c + 2, pf + 5, 0, SEA_LANTERN);
            if (c == -36) { f.chest(c + 2, pf + 1, 5, 0, -1, Sites.CORRIDOR); f.spawner(c + 1, pf + 1, -5, "SPIDER"); }
            else if (c == 33) f.chest(c + 1, pf + 1, 5, 0, -1, "minecraft:chests/abandoned_mineshaft", "trinket:0.1");
            else if (c == 21) f.chest(c + 2, pf + 1, -5, 0, 1, Sites.DUNGEON);
            if (c == -24 || c == 21) set(c + 1, pf + 1, 4, BONE, 0);
        }

        /** The fallen span's rubble heaped on the land below it. */
        void rubble(int u, int v, int t) {
            int h = (int) (f.roll(u, 0, v, 82) * 3 + (8 - Math.abs(v)) / 3.0);
            for (int y = t + 1; y <= t + h; y++) f.rubble(u, y, v);
        }

        /** A drowned king on a corbel against a pier's face: robe, folded arms on a sword, a bearded head and a crown. */
        void statue(int u, int v) {
            int sd = Integer.signum(v), av = Math.abs(v), c = u > 0 ? (u <= 24 ? 21 : 33) : (u >= -24 ? -24 : -36);
            int du = u - c;                                // 0..3 across the pier
            int t = g(u, v), base = Math.max(t + 1, K1 - 15);
            if (base > t + 1) for (int y = base - 2; y < base; y++) if (av == 9) set(u, y, v, BRICK_STAIRS, f.stairs(0, -sd, true));
            set(u, base, v, BRICK, 3);
            boolean mid = du == 1 || du == 2, front = av == 10;
            for (int y = base + 1; y <= base + 12; y++) {
                int k = y - base;
                if (k <= 5) { if (!front || mid || k <= 2) patina(u, y, v); }                  // the robe, widening to the hem
                else if (k <= 8) { if (mid && !front) patina(u, y, v); else if (!mid && k <= 7 && !front) patina(u, y, v); else if (front && mid && k == 7) patina(u, y, v); }
                else if (k <= 10) { if (mid && !front) patina(u, y, v); else if (mid && k == 9) set(u, y, v, WALL, 1); }   // the head, its beard
                else if (mid && !front) set(u, y, v, CLAY, 4);                                  // the crown
            }
            if (front && du == 1) for (int y = base + 3; y <= base + 6; y++) set(u, y, v, IRON_BARS);
            if (front && du == 1) set(u, base + 7, v, BRICK, 3);
        }

        // ---------------------------------------------------------------- the canal of the drowned kings

        void canal() {
            int cw = p.cw, cb = p.cb;
            for (int u = Math.max(-32, u0); u <= Math.min(-25, u1); u++)
                for (int v = Math.max(-56, v0); v <= Math.min(56, v1); v++) {
                    int t = g(u, v);
                    boolean wall = u == -32 || u == -25 || Math.abs(v) == 56;
                    if (wall) {
                        for (int y = Math.min(t, cb) - 1; y <= cw + 1; y++) f.masonry(u, y, v);
                        if (t > cw + 1) air(u, v, cw + 2, t + 1);
                        continue;
                    }
                    for (int y = Math.min(t, cb) - 1; y <= cb; y++) f.rubble(u, y, v);
                    set(u, cb, v, f.roll(u, cb, v, 83) < 0.5 ? GRAVEL : PRISMARINE, 0);
                    for (int y = cb + 1; y <= cw; y++) set(u, y, v, Canvas.WATER);
                    air(u, v, cw + 1, Math.max(t + 1, cw + 3));
                }
            // the heads of the drowned kings in the water, crowned, their eyes still lit
            for (int k = -2; k <= 2; k++) {
                if (k == 0) continue;
                int hv = k * 22, hu = -29;
                if (!hit(hu - 2, hv - 2, hu + 2, hv + 2)) continue;
                for (int u = hu - 1; u <= hu + 2; u++)
                    for (int v = hv - 1; v <= hv + 2; v++)
                        for (int y = cb + 1; y <= cw + 2; y++) {
                            boolean crown = y == cw + 2;
                            if (crown) { if ((u == hu - 1 || u == hu + 2 || v == hv - 1 || v == hv + 2) && ((u + v) & 1) == 0) set(u, y, v, CLAY, 4); else if (crown) set(u, y, v, AIR); }
                            else patina(u, y, v);
                        }
                set(hu - 1, cw - 1, hv, SEA_LANTERN);
                set(hu - 1, cw - 1, hv + 1, SEA_LANTERN);
            }
            // a footbridge of slabs
            for (int u = -33; u <= -24; u++) for (int v = -41; v <= -39; v++) if (in(u, v)) set(u, cw + 2, v, SLAB, 5);
        }

        // ---------------------------------------------------------------- the stair towers

        /** A tower of switchback flights (five steps a flight, landings every five) from its floor to above the channel. */
        void stairTower(int c, int F) {
            int sg = c < 0 ? 1 : -1;                                    // toward the viaduct
            int top = K3 + 5;
            for (int u = Math.max(c - 5, u0); u <= Math.min(c + 5, u1); u++)
                for (int v = Math.max(-5, v0); v <= Math.min(5, v1); v++) {
                    int a = (u - c) * sg, t = g(u, v);
                    boolean wall = Math.abs(a) == 5 || Math.abs(v) == 5;
                    if (wall) {
                        for (int y = t - 2; y <= top; y++) {
                            boolean door = false;
                            if (a == 5 && Math.abs(v) <= 1) for (int lv = F + 5; lv <= K3; lv += 10) if (y > lv && y <= lv + 3 && (lv == K1 || lv == K2 || lv == K3)) door = true;
                            if (v == -5 && Math.abs(a) <= 1 && y > F && y <= F + 3) door = true;
                            if (door) set(u, y, v, AIR);
                            else if ((y - F) % 10 == 5 && Math.abs(a) == 5 && Math.abs(v) == 5) f.glyph(u, y, v);
                            else f.eldritch(u, y, v);
                        }
                        if (((u + v) & 1) == 0) f.eldritch(u, top + 1, v);
                        continue;
                    }
                    for (int y = Math.min(t, F) - 1; y < F; y++) f.rubble(u, y, v);
                    f.paving(u, F, v);
                    air(u, v, F + 1, top);
                    boolean core = Math.abs(a) <= 2 && Math.abs(v) <= 1;
                    if (core) { for (int y = F + 1; y <= top - 1; y++) { if ((y - F) % 10 == 7 && a == 0) set(u, y, v, SEA_LANTERN); else f.masonry(u, y, v); } continue; }
                    for (int base = F; base < top; base += 10) {
                        if (v >= 2 && Math.abs(a) <= 2) { int y = base + a + 3; if (y < top) set(u, y, v, BRICK_STAIRS, stair(sg, 0)); }
                        else if (v <= -2 && Math.abs(a) <= 2) { int y = base + 8 - a; if (y < top) set(u, y, v, BRICK_STAIRS, stair(-sg, 0)); else if (y == top) f.masonry(u, y, v); }
                        else if (a >= 3) { int y = base + 5; if (y < top) f.masonry(u, y, v); }
                        else if (a <= -3 && base + 10 <= top) { f.masonry(u, base + 10, v); }
                    }
                    // the roof, open over the last flight
                    if (!(v <= -2 && a >= -1 && a <= 2)) f.paving(u, top, v);
                }
            // the steps down from the ground door to the land, and the tower's landmarks
            int td = g(c, -7);
            for (int k = 1; k <= F - td && k <= 12; k++)
                for (int a = -1; a <= 1; a++) {
                    int u = c + a * sg, v = -5 - k;
                    if (!in(u, v)) continue;
                    mas(u, v, F - k - 2, F - k);
                    set(u, F - k + 1, v, BRICK_STAIRS, stair(0, 1));
                    air(u, v, F - k + 2, F - k + 4);
                }
            for (int a = -1; a <= 1; a++) { int u = c + a * sg; if (in(u, -6)) { if (F - td <= 0) { f.paving(u, F, -6); air(u, -6, F + 1, F + 3); } } }
            if (hit(c - 2, -7, c + 2, -5)) {
                f.sign(c + 2 * sg, F + 2, -6, 0, -1, c < 0 ? "THE WEST STAIR\nTO THE ROAD\nOF THE KINGS" : "THE EAST STAIR\nTHE ROAD IS\nBROKEN. CLIMB");
                f.chest(c - 4 * sg, F + 1, 4, sg, 0, c < 0 ? Sites.SMITH : Sites.DUNGEON);
            }
        }

        // ---------------------------------------------------------------- the toll fortress

        void fortress() {
            int GF = p.GF, CF = p.CF, n = K1 - GF, m = GF - CF, roof = K2 + 6, ch = Math.min(5, m - 2);
            for (int u = Math.max(-14, u0); u <= Math.min(14, u1); u++)
                for (int v = Math.max(-18, v0); v <= Math.min(18, v1); v++) {
                    int au = Math.abs(u), av = Math.abs(v), t = g(u, v);
                    boolean tower = au >= 11 && au <= 14 && av >= 15 && av <= 18;
                    boolean outer = au >= 11 && au <= 12 && av <= 16 || av >= 15 && av <= 16 && au <= 12;
                    if (tower) {
                        boolean tw = au == 14 || av == 18;
                        for (int y = Math.min(t, GF) - 2; y <= K2 + 16; y++) { if (y % 9 == 0) f.glyph(u, y, v); else f.eldritch(u, y, v); }
                        if (tw && ((u + v) & 1) == 0) f.eldritch(u, K2 + 17, v);
                        if (au == 14 && av == 18) set(u, K2 + 17, v, SEA_LANTERN);
                        continue;
                    }
                    if (au > 12 || av > 16) continue;
                    if (outer) { wallColumn(u, v, au, av, t, roof); continue; }
                    interior(u, v, au, av, t, n, m, ch, roof);
                }
            furnish(GF, CF, n, m, ch, roof);
            keep(roof);
        }

        void wallColumn(int u, int v, int au, int av, int t, int roof) {
            int GF = p.GF;
            for (int y = Math.min(t, GF) - 2; y <= roof; y++) {
                boolean gate = au >= 11 && Math.abs(v) <= 2 && y > K1 && y <= K1 + 5;
                boolean door = av >= 15 && v < 0 && Math.abs(u) <= 1 && y > GF && y <= GF + 3;
                boolean upper = au >= 11 && (Math.abs(v) == 3 || Math.abs(v) == 4) && y > K2 && y <= K2 + 3;
                boolean slit = (y == K1 + 6 || y == K2 + 3) && (au <= 12 && av >= 15 && u % 4 == 0 || av <= 12 && au >= 11 && v % 4 == 2);
                if (gate || door || upper) { set(u, y, v, gate && y == K1 + 5 ? IRON_BARS : AIR); continue; }
                if (slit) { set(u, y, v, IRON_BARS); continue; }
                if (y == K1 || y == K2) f.glyph(u, y, v); else f.eldritch(u, y, v);
            }
            boolean outerRing = au == 12 || av == 16;
            if (outerRing && ((u + v) & 1) == 0) f.eldritch(u, roof + 1, v);
            else if (!outerRing) f.paving(u, roof, v);
        }

        void interior(int u, int v, int au, int av, int t, int n, int m, int ch, int roof) {
            int GF = p.GF, CF = p.CF;
            // below the ground floor: footing, and the crypt cut out of it
            for (int y = Math.min(t, CF) - 1; y < GF; y++) f.rubble(u, y, v);
            boolean crypt = au <= 10 && v >= 0 && v <= 13;
            if (crypt) {
                boolean cw = au == 10 || v == 0 || v == 13;
                for (int y = CF; y <= CF + ch + 1; y++) { if (y == CF || y == CF + ch + 1 || cw) f.eldritch(u, y, v); else set(u, y, v, AIR); }
            }
            // the crypt stair from the undercroft
            if (u >= -10 && u <= -8 && v >= 1 - m && v <= -1) {
                int k = v + m, y = GF - k;
                for (int yy = y + 1; yy <= GF; yy++) set(u, yy, v, AIR);
                set(u, y, v, BRICK_STAIRS, stair(0, -1));
            }
            if (u == -7 && v >= 1 - m && v <= -1) { for (int y = GF - (v + m); y <= GF; y++) f.masonry(u, y, v); }
            if (u >= -10 && u <= -8 && v == 0) { for (int y = CF + 1; y <= CF + 3; y++) set(u, y, v, AIR); }
            // the undercroft
            f.paving(u, GF, v);
            boolean stairUp = av >= 12 && v < 0 && u >= -10 && u <= -11 + n;
            boolean stairDown = u >= -10 && u <= -8 && v >= 1 - m && v <= -1;
            if (stairDown) set(u, GF, v, AIR);
            air(u, v, GF + 1, K1 - 1);
            if (v >= 10 && v <= 14) {
                boolean cellWall = Math.floorMod(u + 10, 5) == 4 || au == 10;
                if (v == 10 && !cellWall) { for (int y = GF + 1; y <= GF + 3; y++) if (Math.floorMod(u + 12, 5) != 0) set(u, y, v, IRON_BARS); }
                else if (cellWall) for (int y = GF + 1; y <= GF + 4; y++) f.masonry(u, y, v);
            }
            if (au == 5 && (v == -6 || v == 0 || v == 6)) for (int y = GF + 1; y < K1; y++) { if (y == GF + 4) f.glyph(u, y, v); else f.masonry(u, y, v); }
            if (u == -7 && v >= 1 - m && v <= -1) set(u, GF + 1, v, WALL, 0);
            if (stairUp) { int k = u + 11, y = GF + k; for (int yy = GF + 1; yy < y; yy++) f.masonry(u, yy, v); set(u, y, v, BRICK_STAIRS, stair(1, 0)); air(u, v, y + 1, K1 + 2); }
            // the toll hall floor (open over the stair up), the hall and its wings
            if (!stairUp) f.paving(u, K1, v);
            boolean hall = av <= 8, wing = av >= 10, partition = av == 9;
            if (partition) { for (int y = K1 + 1; y < K2; y++) { boolean door = Math.abs(u - (v < 0 ? 4 : 0)) <= 1 && y <= K1 + 3; if (door) set(u, y, v, AIR); else f.masonry(u, y, v); } }
            else air(u, v, K1 + 1, K2 - 1);
            if (hall && (au == 4 || au == 8) && av == 5) for (int y = K1 + 1; y < K2; y++) { if (y == K1 + 7) f.glyph(u, y, v); else f.masonry(u, y, v); }
            // the stair from the north wing up to the upper floor
            boolean stair2 = v >= 12 && v <= 14 && u >= -8 && u <= 1;
            if (stair2) { int k = u + 9, y = K1 + k; for (int yy = K1 + 1; yy < y; yy++) f.masonry(u, yy, v); set(u, y, v, BRICK_STAIRS, stair(1, 0)); air(u, v, y + 1, y + 3); }
            if (!stair2) f.paving(u, K2, v); else if (u == 1) set(u, K2, v, BRICK_STAIRS, stair(1, 0));
            // the upper floor: quarters (west), treasury (east), guardroom (north)
            air(u, v, K2 + 1, roof - 1);
            if (v == -1 || u == 0 && v < -1) for (int y = K2 + 1; y < roof; y++) { boolean door = (u == -5 || u == 5) && v == -1 && y <= K2 + 3; if (!door) f.masonry(u, y, v); }
            boolean stair3 = v >= 5 && v <= 7 && u >= 3 && u <= 8;
            if (stair3) { int y = K2 + (u - 2); for (int yy = K2 + 1; yy < y; yy++) f.masonry(u, yy, v); set(u, y, v, BRICK_STAIRS, stair(1, 0)); air(u, v, y + 1, roof); }
            if (!stair3) f.paving(u, roof, v);
        }

        /** The keep on the fortress roof: a tower of the Toll Keeper's watch, a ladder to its lookout. */
        void keep(int roof) {
            int top = roof + 16;
            for (int u = Math.max(-4, u0); u <= Math.min(4, u1); u++)
                for (int v = Math.max(-4, v0); v <= Math.min(4, v1); v++) {
                    boolean wall = Math.abs(u) == 4 || Math.abs(v) == 4;
                    if (wall) {
                        for (int y = roof + 1; y <= top; y++) {
                            boolean door = u == 4 && v == 0 && y <= roof + 3, window = (y - roof) % 5 == 3 && (u == 0 || v == 0) && !(u == 4 && v == 0);
                            if (door) set(u, y, v, AIR); else if (window) set(u, y, v, IRON_BARS); else if (y == roof + 8) f.glyph(u, y, v); else f.eldritch(u, y, v);
                        }
                        if (((u + v) & 1) == 0) f.eldritch(u, top + 1, v);
                    } else {
                        air(u, v, roof + 1, top - 1);
                        if (!(u == -3 && v == 0)) f.paving(u, top, v);
                    }
                }
            for (int y = roof + 1; y <= top; y++) f.set(-3, y, 0, LADDER, f.facing(1, 0));
            set(0, top - 1, 0, GLOWSTONE);
            set(3, roof + 1, -3, CAULDRON);
        }

        void furnish(int GF, int CF, int n, int m, int ch, int roof) {
            // the hall: the toll barrier across the road, the desk, the scales, the lamps, the gates' dart traps
            for (int v = -2; v <= 2; v++) if (v != 0) { set(0, K1 + 1, v, IRON_BARS); set(0, K1 + 2, v, IRON_BARS); }
            for (int u = -3; u <= 3; u++) { set(u, K1 + 1, 5, DOUBLE_SLAB, 5); set(u, K1 + 2, 5, SLAB, 5); }
            set(-2, K1 + 3, 5, FENCE); set(2, K1 + 3, 5, FENCE);
            for (int sd = -1; sd <= 1; sd += 2) {
                set(6 * sd, K2 - 1, 0, GLOWSTONE);
                set(11 * sd, K1, 0, AIR);
                f.dispenser(11 * sd, K1, 0, 0, 0);
                set(11 * sd, K1 + 1, 0, PLATE);
            }
            f.chest(0, K1 + 1, 7, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(s.hash, 0, 99) + ";trinket:0.6");
            f.chest(-3, K1 + 1, 7, 0, -1, Sites.DESERT, "trinket:0.2");
            f.sign(0, K1 + 1, 4, 0, -1, "NONE CROSS\nWITHOUT\nPAYING");
            f.chest(9, K1 + 1, -10, -1, 0, Sites.CORRIDOR);
            // the undercroft's cells, the crypt and its kings, the upper rooms
            f.chest(8, GF + 1, 13, 0, -1, Sites.DUNGEON);
            f.spawner(-8, GF + 1, 13, "ZOMBIE");
            for (int k = 0; k < 4; k++) {
                int u = -7 + 5 * k - (k >= 2 ? 1 : 0);
                if (u >= -8 && u <= 8) { set(u, CF + 1, 11, DOUBLE_SLAB, 5); set(u, CF + 2, 11, SLAB, 5); }
            }
            for (int u = -8; u <= 8; u += 4) { set(u, CF + 1, 3, DOUBLE_SLAB, 5); set(u, CF + 2, 3, CLAY, 4); }
            f.chest(6, CF + 1, 12, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(s.hash ^ 13), 0, 99) + ";trinket:0.3");
            f.chest(-6, CF + 1, 12, 0, -1, "minecraft:chests/stronghold_crossing");
            f.spawner(0, CF + 1, 11, "SKELETON");
            set(0, CF + ch, 7, SEA_LANTERN);
            f.sign(-9, CF + 2, 1, 1, 0, "HERE THE\nKINGS SLEEP\nUNDER THE\nWATER");
            f.chest(-9, K2 + 1, -13, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 17), 0, 99));
            for (int v = -12; v <= -9; v++) set(-10, K2 + 1, v, BOOKSHELF);
            f.chest(9, K2 + 1, -13, -1, 0, Sites.DESERT, "trinket:0.35");
            f.chest(9, K2 + 1, -11, -1, 0, Sites.SMITH);
            set(-2, K2 + 5, 6, GLOWSTONE);
        }

        // ---------------------------------------------------------------- the toll yard before the fortress

        void tollYard() {
            int y = p.GF;
            pad(-12, -32, 12, -17, y, true);
            for (int u = Math.max(-12, u0); u <= Math.min(12, u1); u++)
                for (int v = Math.max(-32, v0); v <= Math.min(-17, v1); v++) {
                    boolean edge = Math.abs(u) == 12 || v == -32;
                    if (edge && !(v == -32 && Math.abs(u) <= 2)) { f.masonry(u, y + 1, v); if (f.keep(u, y + 2, v, 0.7)) f.masonry(u, y + 2, v); }
                    if ((u == -4 || u == 4) && v >= -28 && v <= -20 && v != -24) set(u, y + 1, v, FENCE);
                }
            // the gallows of those who would not pay
            if (hit(6, -30, 10, -26)) {
                for (int yy = y + 1; yy <= y + 5; yy++) { set(7, yy, -28, FENCE); set(10, yy, -28, FENCE); }
                for (int u = 7; u <= 10; u++) set(u, y + 6, -28, PLANKS, 5);
                set(8, y + 5, -28, FENCE); set(9, y + 5, -28, FENCE); set(9, y + 4, -28, FENCE);
            }
        }

        // ---------------------------------------------------------------- the gatehouse village

        void village() {
            // the road from the gate to the west stair tower
            for (int u = Math.max(-52, u0); u <= Math.min(-48, u1); u++)
                for (int v = Math.max(-56, v0); v <= Math.min(-12, v1); v++) {
                    int t = g(u, v);
                    f.paving(u, t, v);
                    air(u, v, t + 1, t + 3);
                }
            // the Kings' Gate: two towers and an arch over the road
            if (hit(-56, -53, -44, -47)) {
                int t = p.gate;
                for (int sd = 0; sd <= 1; sd++)
                    for (int u = sd == 0 ? -56 : -46; u <= (sd == 0 ? -54 : -44); u++)
                        for (int v = -52; v <= -48; v++) { if (!in(u, v)) continue; mas(u, v, g(u, v) - 2, t + 12); if (((u + v) & 1) == 0) f.masonry(u, t + 13, v); }
                for (int u = -53; u <= -47; u++) for (int v = -51; v <= -49; v++) for (int y = t + 7; y <= t + 9; y++) { if (y == t + 7 && Math.abs(u + 50) <= 1) continue; f.masonry(u, y, v); }
                f.sign(-50, t + 6, -52, 0, -1, "THE VIADUCT\nOF THE\nDROWNED KINGS");
                set(-55, t + 10, -47, SEA_LANTERN);
            }
            for (int k = 0; k < HOUSES.length; k++) house(HOUSES[k][0], HOUSES[k][1], p.house[k], k);
            chapel();
        }

        /** A ruined house of the toll-takers' village: crumbling walls, a door to the road, a fallen roof. */
        void house(int cu, int cv, int y, int k) {
            if (!hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
            pad(cu - 4, cv - 4, cu + 4, cv + 4, y, false);
            for (int u = Math.max(cu - 3, u0); u <= Math.min(cu + 3, u1); u++)
                for (int v = Math.max(cv - 3, v0); v <= Math.min(cv + 3, v1); v++) {
                    boolean wall = Math.abs(u - cu) == 3 || Math.abs(v - cv) == 3;
                    if (!wall) { if (f.roll(u, y, v, 84) < 0.7) set(u, y, v, PLANKS, 1); continue; }
                    boolean door = u == cu - 3 && Math.abs(v - cv) <= 0;
                    for (int yy = y + 1; yy <= y + 5; yy++) {
                        if (door && yy <= y + 2) { set(u, yy, v, AIR); continue; }
                        if (yy > y + 2 && !f.keep(u, yy, v, 1.1 - (yy - y) * 0.15)) break;
                        f.masonry(u, yy, v);
                    }
                }
            for (int u = cu - 2; u <= cu + 2; u++) for (int v = cv - 2; v <= cv + 2; v++) if (f.keep(u, y + 5, v, 0.35)) set(u, y + 5, v, 126, 5);
            String[] loot = {"minecraft:chests/igloo_chest", "minecraft:chests/woodland_mansion", Sites.SMITH, Sites.CORRIDOR};
            if (k != 1) f.chest(cu + 2, y + 1, cv + 2, -1, 0, loot[k]);
            else f.spawner(cu + 2, y + 1, cv + 2, "SPIDER");
            set(cu + 2, y + 1, cv - 2, CAULDRON);
        }

        /** The chapel of the drowned kings: a nave, an altar under a crowned niche, and the kings' last words. */
        void chapel() {
            int cu = CHU, cv = CHV, y = p.chapel;
            if (!hit(cu - 6, cv - 8, cu + 6, cv + 8)) return;
            pad(cu - 5, cv - 7, cu + 5, cv + 7, y, true);
            for (int u = Math.max(cu - 4, u0); u <= Math.min(cu + 4, u1); u++)
                for (int v = Math.max(cv - 6, v0); v <= Math.min(cv + 6, v1); v++) {
                    boolean wall = Math.abs(u - cu) == 4 || Math.abs(v - cv) == 6;
                    if (!wall) continue;
                    boolean door = v == cv - 6 && Math.abs(u - cu) <= 1;
                    for (int yy = y + 1; yy <= y + 8; yy++) {
                        if (door && yy <= y + 3) { set(u, yy, v, AIR); continue; }
                        if (yy > y + 4 && !f.keep(u, yy, v, 1.5 - (yy - y) * 0.15)) break;
                        if (yy == y + 4 && Math.abs(u - cu) == 4 && (v - cv) % 3 == 0) set(u, yy, v, 102); else f.masonry(u, yy, v);
                    }
                }
            fill(cu - 1, y + 1, cv + 4, cu + 1, y + 1, cv + 4, BRICK, 3);
            set(cu, y + 2, cv + 5, CLAY, 4);
            set(cu, y + 3, cv + 5, SEA_LANTERN);
            for (int v = cv - 3; v <= cv + 2; v += 2) { set(cu - 2, y + 1, v, BRICK_STAIRS, stair(0, -1)); set(cu + 2, y + 1, v, BRICK_STAIRS, stair(0, -1)); }
            f.sign(cu, y + 1, cv + 3, 0, -1, "WE PAID THE\nSEA ITS TOLL\nAND IT TOOK\nOUR KINGDOM");
            f.chest(cu + 3, y + 1, cv + 5, -1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 19), 0, 99));
        }

        // ---------------------------------------------------------------- the east end: a fallen colossus, the barracks

        /** A king forty blocks tall once stood here: now he lies on his back in three pieces, crowned still. */
        void colossus() {
            int y = p.colossus;
            for (int u = Math.max(37, u0); u <= Math.min(51, u1); u++)
                for (int v = Math.max(13, v0); v <= Math.min(43, v1); v++) {
                    int du = u - 44, t = g(u, v);
                    int h = 0;
                    if (v >= 14 && v <= 21) h = Math.abs(du) <= 1 || Math.abs(du) >= 3 && Math.abs(du) <= 4 ? 3 : 0;   // the legs
                    else if (v >= 24 && v <= 33) h = Math.abs(du) <= 5 ? 4 - (Math.abs(du) == 5 ? 1 : 0) : Math.abs(du) <= 7 && v >= 26 && v <= 31 ? 2 : 0; // the trunk, the arms
                    else if (v >= 36 && v <= 41) h = Math.abs(du) <= 3 ? 5 - (Math.abs(du) == 3 ? 1 : 0) : 0;            // the head
                    if (h == 0) continue;
                    int b = Math.max(t, y);
                    for (int yy = Math.min(t, y); yy <= b; yy++) f.rubble(u, yy, v);
                    for (int yy = b + 1; yy <= b + h; yy++) patina(u, yy, v);
                    if (v >= 36 && v <= 41 && Math.abs(du) <= 3 && (v == 41 || v == 36) && ((u + v) & 1) == 0) set(u, b + h + 1, v, CLAY, 4);
                }
            if (hit(42, 34, 46, 42)) {
                int hb = Math.max(g(43, 38), y), cb = g(44, 35);
                set(43, hb + 5, 38, SEA_LANTERN); set(45, hb + 5, 38, SEA_LANTERN);
                f.chest(44, cb + 1, 35, 0, -1, Sites.JUNGLE, "trinket:0.25");
                air(44, 35, cb + 2, cb + 3);
            }
            if (in(43, 10)) air(43, 10, p.colossusPoint + 1, p.colossusPoint + 2);
        }

        void barracks() {
            int y = p.barracks;
            pad(37, -37, 53, -21, y, true);
            for (int u = Math.max(38, u0); u <= Math.min(52, u1); u++)
                for (int v = Math.max(-36, v0); v <= Math.min(-22, v1); v++) {
                    boolean wall = u == 38 || u == 52 || v == -36 || v == -22 || u == 45 && v < -29 || v == -29 && u > 45;
                    boolean door = v == -22 && (u == 41 || u == 49) || u == 45 && v == -32 || v == -29 && u == 49;
                    if (!wall) continue;
                    for (int yy = y + 1; yy <= y + 5; yy++) {
                        if (door && yy <= y + 2) { set(u, yy, v, AIR); continue; }
                        if (yy > y + 2 && !f.keep(u, yy, v, 1.2 - (yy - y) * 0.16)) break;
                        f.masonry(u, yy, v);
                    }
                }
            if (!hit(38, -36, 52, -22)) return;
            for (int v = -35; v <= -31; v += 2) set(39, y + 1, v, 170);
            f.chest(51, y + 1, -35, -1, 0, Sites.SMITH);
            f.chest(51, y + 1, -23, -1, 0, Sites.DUNGEON, "trinket:0.15");
            f.sign(45, y + 2, -21, 0, 1, "TOLL TAKERS\nSLEPT HERE\nNONE WOKE");
        }
    }
}
