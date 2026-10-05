package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Temple of the Black Goat (epoch 6; its keeper the Dark Young). A megalithic temple of the Goat with a Thousand Young
 * on a levelled platform 101 blocks across: a processional way of horned pillars through a trilithon gate; an outer ring
 * of leaning standing stones and an inner ring of trilithons round a ring of teeth and the blood-red altar plaza; sunk in
 * the plaza's heart the altar court where the Dark Young wakes; the goat-horned idol on its stepped dais, the cave temple
 * hollowed out beneath it with a back stair to the surface; the sunken amphitheatre of worship; the Pits of the Thousand
 * Young over their brood warren; the passage barrow of the priests; dolmens; a grove of petrified Dark Young; and the
 * goatherd-priests' lodge. Every block is a function of the site (plan) and of position (hashes): canvas-blind.
 */
final class GreatBlackGoat extends GreatDesign {
    static final int R = 50;
    /** Floor blocks (local y) of the sunken altar court and of the caverns beneath. */
    static final int COURT = -7, CAVE = -11;
    /** The amphitheatre's centre and the brood chamber's centre. */
    static final int AU = -35, AV = 35, BU = 37, BV = 37;
    /** The Goat Stone (a colossal leaning menhir) and the well of the young. */
    static final int GU = -42, GV = 0, WU = 42, WV = 3;
    static final int[][] PITS = {{30, 30, 3}, {24, 44, 3}, {44, 24, 3}, {44, 44, 3}};
    static final int[][] TREES = {{30, -27, 12}, {40, -31, 10}, {37, -17, 11}, {23, -34, 9}, {46, -22, 13}, {47, -11, 9}};
    static final int[][] DOLMENS = {{-28, 6}, {28, -4}, {-26, -15}, {26, 14}};

    /** A megalith seen from above: centre, radial unit vector, half-width along the ring, half-depth across it. */
    static final class Stone {
        final double cu, cv, cs, sn, ht, hr;
        /** kind: 0 standing stone, 1 fallen stone, 2 trilithon, 3 trilithon with its lintel fallen, 4 trilithon with a broken upright. */
        final int h, lean, kind;
        Stone(double a, double rad, double ht, double hr, int h, int lean, int kind) {
            cs = Math.cos(a); sn = Math.sin(a); cu = cs * rad; cv = sn * rad;
            this.ht = ht; this.hr = hr; this.h = h; this.lean = lean; this.kind = kind;
        }
    }

    static final class Plan extends Layout {
        final List<Stone> stones = new ArrayList<>();
        /** The land's height just before the processional way (local y, at most 12 up or down): its entrance stair. */
        int front;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.boss = new int[] {0, COURT + 1, -3};
        Frame fr = new Frame(null, s.x, s.z, s.base, s.rot);
        int sum = 0;
        for (int u = -3; u <= 3; u++) sum += s.surface(fr.wx(u, -52), fr.wz(u, -52)) - s.base;
        p.front = Math.max(-12, Math.min(12, Math.round(sum / 7f)));
        p.garrisons.add(g(0, 1, -44, "cult_zealot+cult_adept+vindicator"));          // the processional way, by the gate
        p.garrisons.add(g(0, 1, -24, "ghoul+zombie+husk"));                           // the way's end, before the plaza
        p.garrisons.add(g(-28, 1, -2, "hound+skeleton+stray"));                       // between the rings, west
        p.garrisons.add(g(29, 1, 6, "nightgaunt+enderman+creeper"));                  // between the rings, east
        p.garrisons.add(g(-14, 1, -9, "cult_zealot+witch+evoker"));                   // the altar plaza
        p.garrisons.add(g(14, 1, -9, "ghoul+tomb_crawler+spider"));                   // the altar plaza
        p.garrisons.add(g(-6, COURT + 1, 3, "!cult_adept+witch+illusioner"));         // the sunken court, by its keeper
        p.garrisons.add(g(6, CAVE + 1, 26, "ghoul+star_spawn+wither_skeleton+silverfish")); // the cave temple
        p.garrisons.add(g(AU, -5, AV - 3, "cult_zealot+cult_adept+zombie_villager"));  // the amphitheatre's stage
        p.garrisons.add(g(-28, 1, 28, "mi_go+endermite+creeper"));                    // the amphitheatre's upper walk
        p.garrisons.add(g(35, 1, 26, "tomb_crawler+cave_spider+spider"));             // the rim of the pits
        p.garrisons.add(g(BU, CAVE + 1, BV - 3, "!shoggoth+slime+cave_spider"));      // the brood chamber
        p.garrisons.add(g(-38, 1, -40, "skeleton+wither_skeleton+zombie"));           // the barrow's passage
        p.garrisons.add(g(32, 1, -21, "hound+ghoul+spider"));                         // the grove of the Dark Young
        p.garrisons.add(g(30, 1, -42, "cult_zealot+vindicator+evoker"));              // the lodge
        p.garrisons.add(g(6, 1, 44, "deep_one+husk+stray"));                          // behind the idol, by the back stair
        p.garrisons.add(g(-8, 10, 22, "star_spawn+mi_go+nightgaunt"));                // the idol's dais
        p.garrisons.add(g(-36, 1, -4, "ghoul+skeleton+cult_zealot"));                 // the Goat Stone
        p.garrisons.add(g(39, 1, -1, "deep_one+zombie+tomb_crawler"));                // the well of the young
        // the outer ring of leaning standing stones (open before the processional way and behind the idol)
        for (int k = 0; k < 30; k++) {
            double a = 2 * Math.PI * (k + 0.5) / 30 + (r.nextDouble() - 0.5) * 0.05;
            double rad = 34 + (r.nextDouble() - 0.5) * 1.2, ht = 1.8 + r.nextDouble() * 1.0, hr = 1.0 + r.nextDouble() * 0.5;
            int h = 10 + r.nextInt(8), lean = r.nextInt(h >= 14 ? 7 : 5) - (h >= 14 ? 3 : 2), kind = r.nextDouble() < 0.18 ? 1 : 0;
            if (Math.abs(diff(a, -Math.PI / 2)) < 0.4 || Math.abs(diff(a, Math.PI / 2)) < 0.45) continue;
            Stone st = new Stone(a, rad, ht, hr, h, lean, kind);
            if (clash(p, st.cu, st.cv, 4.5)) continue;
            p.stones.add(st);
        }
        // the inner ring of trilithons (every thirty degrees, but for the idol's side and the way)
        for (int k = 0; k < 12; k++) {
            double q = r.nextDouble();
            if (k == 2 || k == 3 || k == 4 || k == 9) continue;
            p.stones.add(new Stone(2 * Math.PI * k / 12, 22, 3.2, 1.05, 14, 0, q < 0.6 ? 2 : q < 0.82 ? 3 : 4));
        }
        return p;
    }

    private static double diff(double a, double b) {
        double d = (a - b) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private static boolean clash(Plan p, double u, double v, double near) {
        for (Garrison x : p.garrisons) if (Math.hypot(x.u - u, x.v - v) < near) return true;
        for (int[] d : DOLMENS) if (Math.hypot(d[0] - u, d[1] - v) < 5.5) return true;
        return Math.hypot(u - GU, v - GV) < 10 || Math.hypot(u - WU, v - WV) < 7;
    }

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) { new D(s, (Plan) layout, c).draw(); }

    /** One chunk's drawing: the chunk's columns in the local frame, the land's height there, and the parts that touch it. */
    private static final class D {
        final Plans.GreatSite s;
        final Plan p;
        final Frame f;
        final int u0, u1, v0, v1, cu0, cv0;
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
        }

        int lu(int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
        int lv(int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }

        /** The land's height (local y) of a column of this chunk. */
        int g(int u, int v) {
            int i = (u - cu0) * 16 + (v - cv0);
            if (u - cu0 < 0 || u - cu0 > 15 || v - cv0 < 0 || v - cv0 > 15) return s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
            if (gnd[i] == Integer.MIN_VALUE) gnd[i] = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
            return gnd[i];
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }

        void set(int u, int y, int v, int id, int m) { if (Math.abs(u) <= R && Math.abs(v) <= R && y >= -12) f.set(u, y, v, id, m); }
        void set(int u, int y, int v, int id) { set(u, y, v, id, 0); }
        void air(int u, int v, int y0, int y1) { for (int y = y0; y <= y1; y++) set(u, y, v, AIR, 0); }

        void fill(int a0, int y0, int b0, int a1, int y1, int b1, int id, int m) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, m);
        }

        int stair(int du, int dv) { return f.stairs(du, dv, false); }

        // ---------------------------------------------------------------- materials

        /** The standing stones' pale weathered rock, mossed toward the foot. */
        void sarsen(int u, int y, int v) {
            double q = f.roll(u, y, v, 41);
            if (y <= 2 && q < 0.35) { set(u, y, v, MOSSY); return; }
            if (q < 0.34) set(u, y, v, STONE, 0); else if (q < 0.52) set(u, y, v, STONE, 6); else if (q < 0.66) set(u, y, v, STONE, 3);
            else if (q < 0.80) set(u, y, v, STONE, 5); else if (q < 0.90) set(u, y, v, BRICK, 2); else if (q < 0.96) set(u, y, v, MOSSY); else set(u, y, v, COBBLE);
        }

        /** Blood-stained paving: the plaza and the court. */
        void bloody(int u, int y, int v) {
            double q = f.roll(u, y, v, 33);
            if (q < 0.22) set(u, y, v, CLAY, 14); else if (q < 0.32) set(u, y, v, 215); else if (q < 0.36) set(u, y, v, 214); else dark(u, y, v);
        }

        /** The idol's black hide, flecked with red mouths. */
        void hide(int u, int y, int v) {
            double q = f.roll(u, y, v, 42);
            if (q < 0.32) set(u, y, v, CLAY, 15); else if (q < 0.56) set(u, y, v, 173); else if (q < 0.76) set(u, y, v, OBSIDIAN);
            else if (q < 0.90) set(u, y, v, 251, 15); else if (q < 0.97) set(u, y, v, 112); else set(u, y, v, 215);
        }

        void rock(int u, int y, int v) {
            double q = f.roll(u, y, v, 43);
            if (q < 0.45) set(u, y, v, STONE, 0); else if (q < 0.68) set(u, y, v, STONE, 5); else if (q < 0.84) set(u, y, v, COBBLE); else set(u, y, v, MOSSY);
        }

        void earth(int u, int y, int v) {
            double q = f.roll(u, y, v, 31);
            if (q < 0.30) set(u, y, v, DIRT, 1); else if (q < 0.47) set(u, y, v, GRAVEL); else if (q < 0.61) set(u, y, v, DIRT, 2);
            else if (q < 0.73) set(u, y, v, STONE, 5); else if (q < 0.83) set(u, y, v, COBBLE); else if (q < 0.90) set(u, y, v, MOSSY);
            else if (q < 0.95) set(u, y, v, 88); else set(u, y, v, GRASS);
        }

        /** Dark paving of the plaza. */
        void dark(int u, int y, int v) {
            double q = f.roll(u, y, v, 32);
            if (q < 0.28) set(u, y, v, STONE, 6); else if (q < 0.48) set(u, y, v, BRICK, 0); else if (q < 0.66) set(u, y, v, BRICK, 2);
            else if (q < 0.84) set(u, y, v, CLAY, 15); else set(u, y, v, STONE, 5);
        }

        // ---------------------------------------------------------------- the whole

        void draw() {
            if (u0 > u1 || v0 > v1) return;
            ground();
            if (hit(-12, -50, 12, -17)) way();
            for (Stone st : p.stones) stone(st);
            teeth();
            if (hit(-12, -18, 12, 12)) court();
            if (hit(-16, 10, 16, 50)) underIdol();
            if (hit(-14, 12, 14, 39)) dais();
            if (hit(-20, 12, 20, 40)) idol();
            if (hit(AU - 13, AV - 13, AU + 13, AV + 13)) amphitheatre();
            if (hit(19, 0, 50, 50)) warren();
            if (hit(GU - 9, GV - 9, GU + 9, GV + 9)) goatStone();
            if (hit(WU - 5, WV - 5, WU + 5, WV + 5)) well();
            if (hit(-46, -48, -30, -20)) barrow();
            for (int[] t : TREES) darkYoung(t[0], t[1], t[2]);
            if (hit(26, -48, 46, -38)) lodge();
            for (int[] d : DOLMENS) dolmen(d[0], d[1]);
        }

        /** The platform: cut down to the floor where the land is higher, built up where it is lower, a revetment at its edge. */
        void ground() {
            for (int u = u0; u <= u1; u++)
                for (int v = v0; v <= v1; v++) {
                    int t = g(u, v);
                    boolean edge = Math.abs(u) == R || Math.abs(v) == R;
                    if (t < 0) { for (int y = t + 1; y < 0; y++) if (edge) f.masonry(u, y, v); else f.rubble(u, y, v); }
                    else if (t > 0) { f.clear(u, v, 1, t + 1); if (edge) for (int y = 1; y <= t; y++) f.masonry(u, y, v); }
                    else f.clear(u, v, 1, 1);
                    double rad = Math.hypot(u, v);
                    if (Math.abs(u) <= 4 && v <= -17) f.paving(u, 0, v);
                    else if (rad <= 18.5) plazaFloor(u, v, rad);
                    else earth(u, 0, v);
                    if (edge && t <= 0 && !(v == -R && Math.abs(u) <= 4)) f.masonry(u, 1, v);
                }
        }

        void plazaFloor(int u, int v, double rad) {
            if (rad > 17.5) { if (((u + v) & 1) == 0) set(u, 0, v, OBSIDIAN); else set(u, 0, v, BRICK, 3); return; }
            if (rad > 11.6 && rad < 12.6) { set(u, 0, v, CLAY, 15); return; }
            double sector = Math.atan2(v, u) / (Math.PI / 4);
            double off = Math.abs(sector - Math.rint(sector)) * (Math.PI / 4) * rad;
            if (rad >= 12.6 && off < 1.1) {
                boolean lamp = rad > 16.4 && off < 0.5 && Math.abs(Math.rint(sector)) % 2 == 1;
                set(u, 0, v, lamp ? 95 : off < 0.6 ? CLAY : 215, 14);
                if (lamp) set(u, -1, v, GLOWSTONE);
                return;
            }
            if (rad > 14.6 && rad < 15.4) { set(u, 0, v, 215); return; }
            bloody(u, 0, v);
        }

        // ---------------------------------------------------------------- the processional way and its gate

        void way() {
            for (int u = Math.max(-4, u0); u <= Math.min(4, u1); u++)
                for (int v = Math.max(-50, v0); v <= Math.min(-17, v1); v++) {
                    if (Math.abs(u) == 4) set(u, 0, v, BRICK, 3);
                    else if (u == 0 && Math.floorMod(v, 3) == 0) set(u, 0, v, PRISMARINE, 2);
                }
            // the entrance stair, where the land before the way lies higher or lower than the platform
            int n = Math.abs(p.front);
            for (int v = Math.max(-50, v0); v <= Math.min(-51 + n, v1); v++)
                for (int u = Math.max(-5, u0); u <= Math.min(5, u1); u++) {
                    int e = p.front < 0 ? Math.min(0, p.front + (v + 50)) : Math.max(0, p.front - (v + 50));
                    if (e == 0) continue;
                    if (Math.abs(u) == 5) { for (int y = Math.min(e, 0) - 1; y <= Math.max(e, 0) + 1; y++) f.masonry(u, y, v); continue; }
                    for (int y = Math.min(e, 0) - 2; y < e; y++) f.masonry(u, y, v);
                    set(u, e, v, BRICK_STAIRS, stair(0, p.front < 0 ? 1 : -1));
                    air(u, v, e + 1, Math.max(e, 0) + 4);
                }
            for (int v = -45; v <= -27; v += 6) for (int sd = -1; sd <= 1; sd += 2) hornedPillar(7 * sd, v, sd, v % 12 == -9);
            // the trilithon gate
            if (!hit(-9, -50, 9, -46)) return;
            for (int sd = -1; sd <= 1; sd += 2)
                for (int u = 6; u <= 8; u++)
                    for (int v = -49; v <= -47; v++) for (int y = 1; y <= 13; y++) { if (y == 7) f.glyph(sd * u, y, v); else f.eldritch(sd * u, y, v); }
            for (int u = -9; u <= 9; u++) for (int v = -49; v <= -47; v++) for (int y = 14; y <= 15; y++) f.eldritch(u, y, v);
            set(-3, 13, -48, GLOWSTONE);
            set(3, 13, -48, GLOWSTONE);
            for (int u = -2; u <= 2; u++) set(u, 16, -48, BONE, 0);
            f.sign(-7, 3, -50, 0, -1, "THE TEMPLE OF\nTHE BLACK GOAT\nIA! SHUB-\nNIGGURATH!");
            f.sign(7, 3, -50, 0, -1, "THE GOAT OF\nTHE WOODS\nWITH A\nTHOUSAND YOUNG");
        }

        /** A pillar of the processional way with a goat's horns sweeping out of its capital (and, on some, a brazier). */
        void hornedPillar(int cu, int cv, int sd, boolean fire) {
            if (!hit(cu - 5, cv - 2, cu + 5, cv + 2)) return;
            for (int u = cu - 2; u <= cu + 2; u++) for (int v = cv - 2; v <= cv + 2; v++) f.rubble(u, 1, v);
            for (int u = cu - 1; u <= cu + 1; u++)
                for (int v = cv - 1; v <= cv + 1; v++) {
                    for (int y = 2; y <= 9; y++) { if (y == 5) f.glyph(u, y, v); else f.eldritch(u, y, v); }
                    set(u, 10, v, BRICK, 3);
                }
            int[][] horn = {{2, 10}, {2, 11}, {3, 11}, {3, 12}, {4, 13}, {4, 14}, {4, 15}, {3, 16}, {2, 16}};
            for (int side = -1; side <= 1; side += 2) for (int[] q : horn) set(cu + side * q[0], q[1], cv, BONE, 0);
            set(cu, 11, cv, fire ? MAGMA : BRICK, fire ? 0 : 3);
        }

        // ---------------------------------------------------------------- the rings

        void stone(Stone st) {
            int ext = (int) Math.ceil(Math.max(st.ht, st.hr) + Math.abs(st.lean) + (st.kind == 1 ? st.h + 2 : st.kind >= 3 ? 6 : 2));
            int a0 = (int) Math.floor(st.cu) - ext, a1 = (int) Math.ceil(st.cu) + ext, b0 = (int) Math.floor(st.cv) - ext, b1 = (int) Math.ceil(st.cv) + ext;
            if (!hit(a0, b0, a1, b1)) return;
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++) {
                    double du = u - st.cu, dv = v - st.cv;
                    double rr = du * st.cs + dv * st.sn, tt = -du * st.sn + dv * st.cs;
                    if (st.kind == 0) {
                        if (Math.abs(tt) > st.ht) continue;
                        for (int y = 1; y <= st.h; y++) {
                            double shift = st.lean * (y - 1) / (double) Math.max(1, st.h - 1);
                            if (Math.abs(rr - shift) > st.hr || y == st.h && Math.abs(tt) > st.ht - 0.7) continue;
                            if (y == st.h / 2 + 1 && f.roll(u, y, v, 44) < 0.35) f.glyph(u, y, v); else sarsen(u, y, v);
                        }
                    } else if (st.kind == 1) {
                        if (Math.abs(tt) > st.ht) continue;
                        if (Math.abs(rr) <= st.hr) for (int y = 1; y <= 2; y++) sarsen(u, y, v);
                        else if (rr > st.hr + 0.5 && rr < st.hr + 0.5 + st.h * 0.8) {
                            sarsen(u, 1, v);
                            if (Math.abs(tt) < st.ht - 0.6) sarsen(u, 2, v);
                        }
                    } else trilithon(u, v, rr, tt, st.kind);
                }
        }

        void trilithon(int u, int v, double rr, double tt, int kind) {
            boolean upright = Math.abs(rr) <= 1.05 && Math.abs(tt) >= 0.9 && Math.abs(tt) <= 3.1;
            if (upright) {
                int top = kind == 4 && tt > 0 ? 6 + (int) (f.roll(u, 0, v, 45) * 3) : 14;
                for (int y = 1; y <= top; y++) { if (y == 6 || y == 11) f.glyph(u, y, v); else f.eldritch(u, y, v); }
            }
            if (kind == 2 && Math.abs(rr) <= 1.05 && Math.abs(tt) <= 3.8) for (int y = 15; y <= 16; y++) { if (y == 16 && Math.abs(tt) > 3.2) continue; f.eldritch(u, y, v); }
            if (kind == 3 && rr >= 2.2 && rr <= 4.2 && Math.abs(tt) <= 3.6) { f.eldritch(u, 1, v); if (rr <= 3.4) f.eldritch(u, 2, v); }
            if (kind == 4 && rr <= -2.2 && rr >= -4.2 && Math.abs(tt) <= 3.6) { f.eldritch(u, 1, v); if (rr >= -3.4) f.eldritch(u, 2, v); }
        }

        /** The ring of teeth round the plaza: short fanged stones. */
        void teeth() {
            if (!hit(-21, -21, 21, 21)) return;
            for (int k = 0; k < 40; k++) {
                double a = 2 * Math.PI * k / 40;
                if (Math.abs(diff(a, -Math.PI / 2)) < 0.25 || Math.abs(diff(a, Math.PI / 2)) < 0.72) continue;
                int u = (int) Math.round(Math.cos(a) * 19.6), v = (int) Math.round(Math.sin(a) * 19.6);
                if (u < u0 || u > u1 || v < v0 || v > v1) continue;
                int h = 2 + (int) (f.roll(u, 0, v, 46) * 3);
                for (int y = 1; y < h; y++) set(u, y, v, WALL, 1);
                set(u, h, v, BONE, 0);
            }
        }

        // ---------------------------------------------------------------- the sunken altar court

        void court() {
            for (int u = Math.max(-11, u0); u <= Math.min(11, u1); u++)
                for (int v = Math.max(-11, v0); v <= Math.min(11, v1); v++) {
                    int m = Math.max(Math.abs(u), Math.abs(v));
                    if (m <= 9) {
                        if (m <= 1) set(u, COURT, v, OBSIDIAN);
                        else if (m % 3 == 0) set(u, COURT, v, 215);
                        else if (v == 5 && Math.abs(u) >= 3) set(u, COURT, v, CLAY, 14);
                        else bloody(u, COURT, v);
                        air(u, v, COURT + 1, 1);
                    } else {
                        boolean door = v >= 10 && Math.abs(u) <= 1;
                        for (int y = COURT; y <= 0; y++) {
                            if (door && y >= COURT + 1 && y <= COURT + 3) { set(u, y, v, AIR); continue; }
                            if (y == -3 && m == 10) f.glyph(u, y, v); else f.eldritch(u, y, v);
                        }
                        if (m == 10) set(u, 1, v, WALL, 0);
                    }
                }
            // the corner pillars with their pale lamps
            for (int su = -1; su <= 1; su += 2)
                for (int sv = -1; sv <= 1; sv += 2) {
                    for (int y = COURT + 1; y <= -2; y++) set(9 * su, y, 9 * sv, BRICK, 3);
                    set(9 * su, -1, 9 * sv, SEA_LANTERN);
                }
            // the altar, its blood and the cages of the offered
            fill(-2, COURT + 1, 4, 2, COURT + 1, 6, OBSIDIAN, 0);
            fill(-2, COURT + 2, 4, 2, COURT + 2, 6, OBSIDIAN, 0);
            fill(-1, COURT + 2, 5, 1, COURT + 2, 5, CLAY, 14);
            for (int su = -1; su <= 1; su += 2) {
                int cu = 6 * su, cv = -6;
                for (int du = -1; du <= 1; du++)
                    for (int dv = -1; dv <= 1; dv++) {
                        set(cu + du, COURT + 4, cv + dv, SLAB, 5);
                        for (int y = COURT + 1; y <= COURT + 3; y++) set(cu + du, y, cv + dv, du == 0 && dv == 0 ? AIR : IRON_BARS);
                    }
                set(cu, COURT + 1, cv, BONE, 0);
            }
            // the stair down from the plaza, through the court's front wall
            for (int k = 1; k <= 6; k++)
                for (int u = -3; u <= 3; u++) {
                    int v = -10 - k, y = COURT + k;
                    if (v < v0 || v > v1 || u < u0 || u > u1) continue;
                    if (Math.abs(u) == 3) { for (int yy = y; yy <= 0; yy++) f.masonry(u, yy, v); set(u, 1, v, WALL, 0); continue; }
                    f.masonry(u, y - 1, v);
                    set(u, y, v, BRICK_STAIRS, stair(0, -1));
                    air(u, v, y + 1, 1);
                }
            for (int u = -2; u <= 2; u++) { dark(u, COURT, -10); air(u, -10, COURT + 1, 1); }
            f.chest(0, COURT + 1, 8, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(s.hash, 0, 99) + ";trinket:0.6");
            f.chest(8, COURT + 1, 8, -1, 0, Sites.DUNGEON);
            f.chest(-8, COURT + 1, 8, 1, 0, Sites.JUNGLE, "trinket:0.2");
            f.sign(0, COURT + 1, 3, 0, -1, "THE GOAT WITH\nA THOUSAND\nYOUNG HUNGERS\nFEED HER");
        }

        // ---------------------------------------------------------------- beneath the idol: tunnel, cave temple, back stair

        void underIdol() {
            // the cavern's rock shell
            for (int u = Math.max(-16, u0); u <= Math.min(16, u1); u++)
                for (int v = Math.max(16, v0); v <= Math.min(42, v1); v++) {
                    double du = u / 14.0, dv = (v - 29) / 11.0, d2 = du * du + dv * dv;
                    if (d2 > 1.0 && d2 <= 1.32) for (int y = CAVE - 1; y <= -2; y++) rock(u, y, v);
                }
            // the tunnel from the court's back door
            for (int v = 12; v <= 20; v++)
                for (int u = -2; u <= 2; u++) {
                    if (v < v0 || v > v1 || u < u0 || u > u1) continue;
                    int floor = v <= 13 ? COURT : v >= 17 ? CAVE : COURT - (v - 13);
                    if (Math.abs(u) == 2) { for (int y = CAVE - 1; y <= -3; y++) f.masonry(u, y, v); continue; }
                    for (int y = CAVE - 1; y < floor; y++) rock(u, y, v);
                    if (v >= 14 && v <= 16) set(u, floor, v, BRICK_STAIRS, stair(0, -1)); else f.paving(u, floor, v);
                    air(u, v, floor + 1, -4);
                    f.masonry(u, -3, v);
                }
            // the cavern
            for (int u = Math.max(-14, u0); u <= Math.min(14, u1); u++)
                for (int v = Math.max(18, v0); v <= Math.min(40, v1); v++) {
                    double du = u / 14.0, dv = (v - 29) / 11.0, d2 = du * du + dv * dv;
                    if (d2 > 1.0) continue;
                    int roof = CAVE + 3 + (int) Math.round(6 * Math.sqrt(1 - d2)) + (f.roll(u, 0, v, 47) < 0.3 ? -1 : 0);
                    if (Math.abs(u) <= 1 && v <= 21) roof = Math.max(roof, -3);
                    roof = Math.min(roof, -2);
                    double q = f.roll(u, CAVE, v, 48);
                    if (q < 0.10) set(u, CAVE, v, CLAY, 14); else if (q < 0.16) set(u, CAVE, v, BONE, 0); else if (q < 0.30) set(u, CAVE, v, GRAVEL); else rock(u, CAVE, v);
                    air(u, v, CAVE + 1, roof - 1);
                    rock(u, roof, v);
                    double st = f.roll(u, 1, v, 49);
                    if (d2 < 0.8 && st < 0.06) { int len = 1 + (int) (f.roll(u, 2, v, 49) * 3); for (int k = 1; k <= len && roof - k > CAVE + 3; k++) set(u, roof - k, v, WALL, k == len ? 0 : 1); }
                    else if (d2 > 0.42 && d2 < 0.9 && st > 0.95) { set(u, CAVE + 1, v, STONE, 0); if (st > 0.975) set(u, CAVE + 2, v, WALL, 0); }
                }
            // the fire pit and the shrine at the cavern's back
            if (hit(-6, 26, 6, 40)) {
                set(0, CAVE, 29, MAGMA);
                for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) if (du != 0 || dv != 0) set(du, CAVE + 1, 29 + dv, SLAB, 3);
                fill(-4, CAVE + 1, 35, 4, CAVE + 1, 38, BRICK, 0);
                fill(-1, CAVE + 2, 37, 1, CAVE + 5, 38, OBSIDIAN, 0);
                set(-2, CAVE + 6, 37, BONE, 0); set(-2, CAVE + 7, 37, BONE, 0); set(2, CAVE + 6, 37, BONE, 0); set(2, CAVE + 7, 37, BONE, 0);
                set(0, CAVE + 5, 37, CLAY, 14);
                set(-4, CAVE + 2, 35, TORCH, 5);
                set(4, CAVE + 2, 35, TORCH, 5);
                f.chest(2, CAVE + 2, 36, 0, -1, Sites.DESERT, "lore:" + Hash.range(Hash.mix(s.hash ^ 5), 0, 99) + ";trinket:0.3");
                f.sign(0, CAVE + 3, 36, 0, -1, "SHE WAS HERE\nBEFORE THE\nWOODS. SHE\nWILL BE AFTER");
            }
            f.chest(-12, CAVE + 1, 28, 1, 0, "minecraft:chests/woodland_mansion");
            f.spawner(9, CAVE + 1, 33, "WITCH");
            f.set(0, COURT, 12, AIR);
            f.dispenser(0, COURT, 12, 0, 0);
            set(0, COURT + 1, 12, PLATE);
            // the back stair: up from the cavern's far end to the surface behind the idol
            for (int k = 1; k <= 10; k++) {
                int v = 38 + k, y = CAVE + k;
                if (v < v0 || v > v1) continue;
                boolean open = k >= 7;
                for (int u = Math.max(-2, u0); u <= Math.min(2, u1); u++) {
                    if (Math.abs(u) == 2) { for (int yy = y - 1; yy <= (open ? 0 : y + 4); yy++) f.masonry(u, yy, v); if (open) set(u, 1, v, WALL, 0); continue; }
                    for (int yy = CAVE; yy < y; yy++) rock(u, yy, v);
                    set(u, y, v, BRICK_STAIRS, stair(0, 1));
                    air(u, v, y + 1, open ? 1 : y + 3);
                    double du = u / 14.0, dv = (v - 29) / 11.0;
                    if (!open && du * du + dv * dv > 1.0) f.masonry(u, y + 4, v);
                }
            }
            for (int u = -1; u <= 1; u++) set(u, 1, 44, WALL, 0);
            // its porch: two uprights and a capstone
            if (hit(-4, 44, 4, 49)) {
                for (int su = -1; su <= 1; su += 2) fill(3 * su - (su > 0 ? 0 : 1), 1, 47, 3 * su + (su > 0 ? 1 : 0), 4, 48, STONE, 5);
                for (int u = -4; u <= 4; u++) for (int v = 46; v <= 49; v++) if (f.keep(u, 5, v, 0.85)) sarsen(u, 5, v);
            }
        }

        // ---------------------------------------------------------------- the dais and the idol

        /** The idol's stepped dais: three tiers of three blocks, a stair up its front, a niche in its back. */
        void dais() {
            for (int u = Math.max(-14, u0); u <= Math.min(14, u1); u++)
                for (int v = Math.max(13, v0); v <= Math.min(39, v1); v++) {
                    int au = Math.abs(u);
                    int tier = au <= 10 && v >= 21 && v <= 35 ? 3 : au <= 12 && v >= 18 && v <= 37 ? 2 : v >= 15 ? 1 : 0;
                    if (au <= 3 && v <= 21) { int y = v - 12; for (int yy = 1; yy < y; yy++) f.eldritch(u, yy, v); set(u, y, v, BRICK_STAIRS, stair(0, 1)); continue; }
                    int top = tier * 3;
                    for (int y = 1; y <= top; y++) {
                        boolean face = y == top - 1 && (au == 14 || au == 12 || au == 10 || v == 15 || v == 18 || v == 21 || v == 35 || v == 37 || v == 39);
                        if (y == top) { if (((u + v) & 1) == 0) set(u, y, v, STONE, 6); else set(u, y, v, CLAY, 15); }
                        else if (face) f.glyph(u, y, v); else f.eldritch(u, y, v);
                    }
                }
            // the hidden niche at the dais' back
            set(0, 2, 39, AIR);
            f.chest(0, 1, 39, 0, 1, Sites.DESERT, "trinket:0.25");
        }

        /** The Black Goat: hooves, shaggy legs, a vast black body, a goat's head with lamp-red eyes, ram's horns, tentacles. */
        void idol() {
            for (int sd = -1; sd <= 1; sd += 2) {
                fill(sd < 0 ? -8 : 2, 10, 24, sd < 0 ? -2 : 8, 11, 31, OBSIDIAN, 0);
                for (int u = 3; u <= 7; u++) for (int v = 25; v <= 30; v++) for (int y = 12; y <= 22; y++) { int uu = sd * u; if (uu >= u0 && uu <= u1 && v >= v0 && v <= v1) hide(uu, y, v); }
            }
            ellipsoid(0, 29, 28, 10, 8.5, 6.5);
            ellipsoid(0, 35, 25, 5.5, 4.5, 5);
            ellipsoid(0, 39.5, 23.5, 4.4, 5.2, 4.4);
            // the snout, the maw and the beard
            for (int v = 14; v <= 20; v++) {
                int w = v >= 16 ? 2 : 1, top = v >= 18 ? 39 : v >= 16 ? 38 : v == 15 ? 37 : 36;
                for (int u = -w; u <= w; u++) for (int y = 35; y <= top; y++) { if (u >= u0 && u <= u1 && v >= v0 && v <= v1) hide(u, y, v); }
            }
            for (int v = 14; v <= 17; v++) set(0, 35, v, 215);
            set(-1, 35, 16, 215); set(1, 35, 16, 215);
            for (int y = 31; y <= 34; y++) set(0, y, 16, WALL, 0);
            for (int y = 32; y <= 34; y++) { set(-1, y, 17, 113); set(1, y, 17, 113); }
            // the eyes: lamps behind red glass; the ears; the horns
            for (int sd = -1; sd <= 1; sd += 2) {
                set(2 * sd, 40, 19, 95, 14);
                set(2 * sd, 40, 20, SEA_LANTERN);
                set(5 * sd, 41, 24, CLAY, 15); set(6 * sd, 41, 24, CLAY, 15); set(6 * sd, 40, 25, CLAY, 15);
                horn(sd);
            }
            // six tentacles slumped over the dais
            for (int sd = -1; sd <= 1; sd += 2) {
                tentacle(7 * sd, 24, 23, 14 * sd, 19, 18, 13 * sd, 4, 16);
                tentacle(9 * sd, 23, 28, 16 * sd, 18, 29, 14 * sd, 4, 30);
                tentacle(7 * sd, 22, 33, 13 * sd, 18, 38, 11 * sd, 7, 37);
            }
        }

        void ellipsoid(double cu, double cy, double cv, double ru, double ry, double rv) {
            int a0 = (int) Math.floor(cu - ru), a1 = (int) Math.ceil(cu + ru), b0 = (int) Math.floor(cv - rv), b1 = (int) Math.ceil(cv + rv);
            if (!hit(a0, b0, a1, b1)) return;
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
                        double x = (u - cu) / ru, yy = (y - cy) / ry, z = (v - cv) / rv;
                        if (x * x + yy * yy + z * z <= 1.0) hide(u, y, v);
                    }
        }

        /** A ball of blocks: bone for the horns, hide for the tentacles. */
        void blob(double cu, double cy, double cv, double rad, boolean bone) {
            int r0 = (int) Math.ceil(rad);
            int a0 = (int) Math.floor(cu) - r0, a1 = (int) Math.ceil(cu) + r0, b0 = (int) Math.floor(cv) - r0, b1 = (int) Math.ceil(cv) + r0;
            if (!hit(a0, b0, a1, b1)) return;
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = (int) Math.floor(cy - rad); y <= (int) Math.ceil(cy + rad); y++) {
                        double d = (u - cu) * (u - cu) + (y - cy) * (y - cy) + (v - cv) * (v - cv);
                        if (d > rad * rad + 0.3) continue;
                        if (bone) set(u, y, v, BONE, 0); else hide(u, y, v);
                    }
        }

        /** A ram's horn: out of the skull, up, out, down and round, thinning to a point. */
        void horn(int sd) {
            for (int i = 0; i <= 110; i++) {
                double t = i / 110.0, phi = Math.toRadians(153 - 320 * t), rho = 6.7 * (1 - 0.5 * t);
                blob(sd * (10 + rho * Math.cos(phi)), 42 + rho * Math.sin(phi), 25 + 4 * t, 2.0 - 1.35 * t, true);
            }
        }

        void tentacle(double au, double ay, double av, double bu, double by, double bv, double cu, double cy, double cv) {
            for (int i = 0; i <= 36; i++) {
                double t = i / 36.0, a = (1 - t) * (1 - t), b = 2 * t * (1 - t), q = t * t;
                blob(a * au + b * bu + q * cu, a * ay + b * by + q * cy, a * av + b * bv + q * cv, 1.3 - 0.7 * t, false);
            }
        }

        // ---------------------------------------------------------------- the amphitheatre of worship

        void amphitheatre() {
            for (int u = Math.max(AU - 13, u0); u <= Math.min(AU + 13, u1); u++)
                for (int v = Math.max(AV - 13, v0); v <= Math.min(AV + 13, v1); v++) {
                    int du = u - AU, dv = v - AV;
                    double d = Math.hypot(du, dv);
                    if (d > 12.7) continue;
                    double ang = Math.atan2(dv, du);
                    boolean aisle = Math.abs(diff(ang, Math.PI / 4)) < 0.14 || Math.abs(diff(ang, 3 * Math.PI / 4)) < 0.14
                        || Math.abs(diff(ang, -Math.PI / 4)) < 0.14 || Math.abs(diff(ang, -3 * Math.PI / 4)) < 0.14;
                    if (d <= 4.5) {
                        if (d <= 1.5) set(u, -6, v, OBSIDIAN); else if (d > 3.5) set(u, -6, v, BRICK, 3); else if (((u + v) & 1) == 0) set(u, -6, v, CLAY, 14); else set(u, -6, v, CLAY, 15);
                        air(u, v, -5, 1);
                    } else if (d <= 9.5) {
                        int y = -5 + (int) (d - 4.5);
                        f.masonry(u, y - 1, v);
                        int sx = Math.abs(du) >= Math.abs(dv) ? Integer.signum(du) : 0, sz = sx == 0 ? Integer.signum(dv) : 0;
                        if (!aisle && f.roll(u, y, v, 50) < 0.07) f.masonry(u, y, v);
                        else set(u, y, v, aisle ? 114 : BRICK_STAIRS, stair(sx, sz));
                        air(u, v, y + 1, 1);
                    } else if (d <= 11.5) {
                        f.paving(u, 0, v);
                        air(u, v, 1, 1);
                    } else if (!aisle) {
                        f.eldritch(u, 1, v);
                        if (f.roll(u, 1, v, 51) < 0.12) { for (int y = 2; y <= 5; y++) sarsen(u, y, v); } else if (f.keep(u, 2, v, 0.7)) f.eldritch(u, 2, v);
                    }
                }
            if (!hit(AU - 2, AV - 4, AU + 2, AV + 4)) return;
            fill(AU - 1, -5, AV - 1, AU + 1, -5, AV + 1, OBSIDIAN, 0);
            set(AU, -5, AV, MAGMA);
            f.sign(AU, -5, AV - 2, 0, -1, "SING TO HER\nAND SHE\nWILL ANSWER");
            f.chest(AU, -5, AV + 3, 0, -1, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 9), 0, 99));
        }

        // ---------------------------------------------------------------- the Pits of the Thousand Young and their warren

        void warren() {
            tunnel(WU, WV, 44, 24);
            for (int[] q : PITS) tunnel(q[0], q[1], BU, BV);
            brood();
            for (int[] q : PITS) pit(q[0], q[1], q[2]);
            if (hit(32, 24, 34, 26)) {
                fill(33, 1, 25, 33, 3, 25, STONE, 5);
                set(33, 4, 25, BONE, 0);
                f.sign(33, 2, 24, 0, -1, "HERE SLEEP\nTHE THOUSAND\nYOUNG\nDO NOT WAKE");
            }
        }

        /** A burrow of the young at the warren's depth, from (a, b) to (c, d). */
        void tunnel(int a, int b, int c, int d0) {
            int a0 = Math.min(a, c) - 2, a1 = Math.max(a, c) + 2, b0 = Math.min(b, d0) - 2, b1 = Math.max(b, d0) + 2;
            if (!hit(a0, b0, a1, b1)) return;
            double lx = c - a, lz = d0 - b, len2 = lx * lx + lz * lz;
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++) {
                    double t = Math.max(0, Math.min(1, ((u - a) * lx + (v - b) * lz) / len2));
                    double d = Math.hypot(u - a - t * lx, v - b - t * lz);
                    if (d > 2.4) continue;
                    if (d > 1.3) { for (int y = CAVE; y <= -6; y++) rock(u, y, v); continue; }
                    if (f.roll(u, CAVE, v, 52) < 0.3) set(u, CAVE, v, BONE, 0); else set(u, CAVE, v, GRAVEL);
                    air(u, v, CAVE + 1, CAVE + 3);
                    rock(u, CAVE + 4, v);
                    if (f.roll(u, CAVE + 3, v, 53) < 0.12) set(u, CAVE + 3, v, WEB);
                }
        }

        void brood() {
            if (!hit(BU - 7, BV - 7, BU + 7, BV + 7)) return;
            for (int u = Math.max(BU - 7, u0); u <= Math.min(BU + 7, u1); u++)
                for (int v = Math.max(BV - 7, v0); v <= Math.min(BV + 7, v1); v++) {
                    double d = Math.hypot(u - BU, v - BV);
                    if (d > 6.6) continue;
                    if (d > 5.2) { for (int y = CAVE - 1; y <= -4; y++) rock(u, y, v); continue; }
                    int roof = CAVE + 2 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - (d / 5.6) * (d / 5.6))));
                    set(u, CAVE, v, f.roll(u, CAVE, v, 54) < 0.4 ? BONE : 88, 0);
                    air(u, v, CAVE + 1, roof - 1);
                    rock(u, roof, v);
                    boolean nearPoint = Math.hypot(u - BU, v - (BV - 3)) < 1.6;
                    double q = f.roll(u, CAVE + 1, v, 55);
                    if (!nearPoint && d > 3.4 && q < 0.22) { set(u, CAVE + 1, v, SLIME); if (q < 0.07) set(u, CAVE + 2, v, SLIME); }
                    else if (!nearPoint && d > 2.2 && q > 0.9) set(u, CAVE + 1, v, BONE, 0);
                    if (f.roll(u, roof - 1, v, 56) < 0.18 && roof - 1 > CAVE + 2) set(u, roof - 1, v, WEB);
                }
            f.spawner(BU, CAVE + 1, BV, "CAVE_SPIDER");
            f.chest(BU + 3, CAVE + 1, BV + 2, -1, 0, Sites.DUNGEON, "trinket:0.3");
        }

        void pit(int cu, int cv, int r) {
            if (!hit(cu - r - 2, cv - r - 2, cu + r + 2, cv + r + 2)) return;
            for (int u = Math.max(cu - r - 2, u0); u <= Math.min(cu + r + 2, u1); u++)
                for (int v = Math.max(cv - r - 2, v0); v <= Math.min(cv + r + 2, v1); v++) {
                    double d = Math.hypot(u - cu, v - cv);
                    boolean ladder = u == cu && v == cv - r, gap = u == cu && v == cv - r - 1;
                    if (d <= r + 0.4) {
                        double q = f.roll(u, CAVE, v, 57);
                        set(u, CAVE, v, q < 0.4 ? BONE : q < 0.7 ? 88 : GRAVEL, 0);
                        air(u, v, CAVE + 1, 1);
                        if (!ladder && d > r - 1.3) for (int y = CAVE + 1; y <= -3; y++) if (f.roll(u, y, v, 58) < 0.1) set(u, y, v, WEB);
                        if (!ladder && d < 1.2 && f.roll(u, CAVE + 1, v, 59) < 0.5) set(u, CAVE + 1, v, WEB);
                    } else if (d <= r + 1.6) {
                        for (int y = CAVE; y <= 0; y++) { double q = f.roll(u, y, v, 60); if (q < 0.15) set(u, y, v, BONE, 0); else if (q < 0.55) set(u, y, v, MOSSY); else set(u, y, v, COBBLE); }
                        if (!gap) set(u, 1, v, f.roll(u, 1, v, 61) < 0.5 ? BONE : WALL, 0);
                    }
                }
            for (int y = CAVE + 1; y <= 0; y++) f.set(cu, y, cv - r, LADDER, f.facing(0, 1));
            if (cu == 44 && cv == 44) {
                f.spawner(cu + 1, CAVE + 1, cv + 1, "SPIDER");
                f.chest(cu - 1, CAVE + 1, cv + 2, 0, -1, "minecraft:chests/abandoned_mineshaft");
            }
        }

        // ---------------------------------------------------------------- the passage barrow of the priests

        boolean barrowRoom(int u, int v) {
            if (u >= -39 && u <= -37 && v >= -42 && v <= -23) return true;
            if ((u >= -44 && u <= -41 || u >= -35 && u <= -32) && (v >= -31 && v <= -29 || v >= -38 && v <= -36)) return true;
            return u >= -42 && u <= -34 && v >= -46 && v <= -43;
        }

        boolean barrowDoor(int u, int v) { return (u == -40 || u == -36) && (v == -30 || v == -37); }

        void barrow() {
            for (int u = Math.max(-46, u0); u <= Math.min(-30, u1); u++)
                for (int v = Math.max(-48, v0); v <= Math.min(-20, v1); v++) {
                    double du = (u + 38) / 7.5, dv = (v + 35) / 12.5, d2 = du * du + dv * dv;
                    int h = d2 <= 1 ? (int) Math.round(7 * Math.sqrt(1 - d2)) : 0;
                    boolean room = barrowRoom(u, v), door = barrowDoor(u, v), wall = false;
                    if (!room) for (int a = -1; a <= 1 && !wall; a++) for (int b = -1; b <= 1; b++) if (barrowRoom(u + a, v + b)) { wall = true; break; }
                    if (room || door) {
                        f.paving(u, 0, v);
                        air(u, v, 1, 3);
                        sarsen(u, 4, v);
                        for (int y = 5; y <= h; y++) mound(u, y, v, h);
                    } else if (wall) {
                        for (int y = 1; y <= 4; y++) sarsen(u, y, v);
                        for (int y = 5; y <= h; y++) mound(u, y, v, h);
                    } else for (int y = 1; y <= h; y++) mound(u, y, v, h);
                }
            // the facade of upright stones and the portal
            int[][] fac = {{-45, 6}, {-43, 7}, {-34, 7}, {-32, 6}};
            for (int[] q : fac) fill(q[0], 1, -22, q[0] + 1, q[1], -21, STONE, 5);
            for (int u = -40; u <= -36; u++) for (int y = 4; y <= 5; y++) sarsen(u, y, -23);
            for (int y = 1; y <= 5; y++) { sarsen(-40, y, -22); sarsen(-36, y, -22); }
            air(-39, -22, 1, 3); air(-38, -22, 1, 3); air(-37, -22, 1, 3);
            for (int u = -39; u <= -37; u++) { f.paving(u, 0, -22); sarsen(u, 4, -22); }
            f.sign(-42, 3, -20, 0, 1, "THE PRIESTS\nWHO FED HER\nLIE HERE\nUNFED");
            f.set(-38, 0, -34, AIR);
            f.dispenser(-38, 0, -34, 0, 0);
            set(-38, 1, -34, PLATE);
            f.chest(-44, 1, -30, 1, 0, Sites.DUNGEON);
            f.chest(-32, 1, -37, -1, 0, "minecraft:chests/stronghold_crossing");
            f.chest(-41, 1, -46, 0, 1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(s.hash ^ 7), 0, 99) + ";trinket:0.2");
            f.chest(-35, 1, -46, 0, 1, Sites.CORRIDOR);
            f.spawner(-38, 1, -46, "SKELETON");
        }

        void mound(int u, int y, int v, int h) {
            if (y == h) { double q = f.roll(u, y, v, 62); set(u, y, v, q < 0.4 ? GRASS : DIRT, q < 0.4 ? 0 : q < 0.75 ? 2 : 1); }
            else if (f.roll(u, y, v, 63) < 0.12) set(u, y, v, MOSSY); else set(u, y, v, DIRT, 0);
        }

        // ---------------------------------------------------------------- the grove of the Dark Young

        void darkYoung(int cu, int cv, int h) {
            if (!hit(cu - 5, cv - 5, cu + 5, cv + 5)) return;
            long hh = Hash.of(s.hash, cu, cv);
            double a0 = Hash.unit(hh) * 2 * Math.PI;
            for (int k = 0; k < 3; k++) {
                double a = a0 + k * 2 * Math.PI / 3;
                for (int i = 0; i <= 8; i++) {
                    double t = i / 8.0;
                    int u = (int) Math.round(cu + Math.cos(a) * 3.2 * t), v = (int) Math.round(cv + Math.sin(a) * 3.2 * t), y = (int) Math.round(4 - 3 * t);
                    if (i == 8) set(u, 1, v, OBSIDIAN); else { hide(u, y, v); hide(u, y - 1, v); }
                }
            }
            for (int y = 1; y <= h; y++) {
                double rad = y < h - 2 ? 1.3 : 0.8;
                for (int u = cu - 2; u <= cu + 2; u++) for (int v = cv - 2; v <= cv + 2; v++) if (Math.hypot(u - cu, v - cv) <= rad) hide(u, y, v);
            }
            set(cu + 1, h / 2, cv, MAGMA);
            set(cu - 1, h / 2 + 2, cv, MAGMA);
            for (int k = 0; k < 5; k++) {
                double b = a0 + 0.35 + k * 2 * Math.PI / 5;
                int lu = cu, lv = cv, ly = h;
                for (int i = 1; i <= 10; i++) {
                    double t = i / 10.0;
                    lu = (int) Math.round(cu + Math.cos(b) * 4.2 * t); lv = (int) Math.round(cv + Math.sin(b) * 4.2 * t);
                    ly = (int) Math.round(h - 1 + 4 * Math.sin(Math.PI * t * 0.75) - 4 * t * t);
                    if (t < 0.5) hide(lu, ly, lv); else set(lu, ly, lv, WALL, 0);
                }
                int len = 1 + (int) (Hash.unit(Hash.of(hh, k, 1)) * 3);
                for (int i = 1; i <= len; i++) set(lu, ly - i, lv, 113);
            }
        }

        // ---------------------------------------------------------------- the goatherd-priests' lodge

        void lodge() {
            for (int u = Math.max(27, u0); u <= Math.min(45, u1); u++)
                for (int v = Math.max(-47, v0); v <= Math.min(-39, v1); v++) {
                    boolean outer = u == 27 || u == 45 || v == -47 || v == -39;
                    boolean part = u == 37 || u > 37 && v == -43;
                    boolean door = v == -39 && (u == 35 || u == 36) || u == 37 && (v == -45 || v == -41);
                    if (outer || part) {
                        f.rubble(u, 0, v);
                        int top = outer ? 5 : 4;
                        for (int y = 1; y <= top; y++) {
                            if (door && y <= (u == 37 ? 2 : 3)) { set(u, y, v, AIR); continue; }
                            if (y == top && !f.keep(u, y, v, 0.65)) break;
                            f.masonry(u, y, v);
                        }
                        if (outer && (u == 27 || u == 45)) for (int y = 6; y <= 10 - Math.abs(v + 43); y++) if (f.keep(u, y, v, 0.8)) f.masonry(u, y, v);
                    } else {
                        if (f.roll(u, 0, v, 64) < 0.75) set(u, 0, v, PLANKS, 5); else set(u, 0, v, GRAVEL);
                        air(u, v, 1, 9);
                    }
                    // the fallen roof
                    if (u > 27 && u < 45) {
                        int y = 6 + 4 - Math.abs(v + 43);
                        if (f.keep(u, y, v, 0.55)) set(u, y, v, v == -43 ? PLANKS : 164, v == -43 ? 5 : stair(0, v < -43 ? 1 : -1));
                    }
                }
            if (!hit(28, -47, 44, -40)) return;
            set(31, 1, -46, MAGMA);
            set(30, 1, -46, WALL, 0); set(32, 1, -46, WALL, 0); set(31, 1, -45, WALL, 0);
            for (int y = 2; y <= 8; y++) f.masonry(31, y, -46);
            set(39, 1, -46, 170); set(40, 1, -46, 170); set(39, 1, -40, 170); set(40, 1, -40, 170);
            set(33, 1, -40, CAULDRON); set(28, 1, -41, CAULDRON);
            f.chest(28, 1, -46, 1, 0, Sites.SMITH);
            f.chest(44, 1, -46, -1, 0, "minecraft:chests/igloo_chest");
            f.chest(44, 1, -40, -1, 0, "minecraft:chests/woodland_mansion", "trinket:0.15");
        }

        // ---------------------------------------------------------------- the Goat Stone and the well of the young

        /** A black menhir twenty-six blocks tall, leaning out over the platform's edge, horned at its head; fangs round its foot. */
        void goatStone() {
            for (int y = 1; y <= 26; y++) {
                int shift = (int) Math.round(6.0 * (y - 1) / 25.0);
                for (int du = -2; du <= 1; du++)
                    for (int dv = -2; dv <= 2; dv++) {
                        if (y >= 24 && (Math.abs(dv) == 2 || y == 26 && du == -2)) continue;
                        int u = GU + du - shift, v = GV + dv;
                        if (u < u0 || u > u1 || v < v0 || v > v1) continue;
                        if (y % 6 == 3 && (du == 1 || Math.abs(dv) == 2)) f.glyph(u, y, v); else hide(u, y, v);
                    }
            }
            int[][] horn = {{2, 25}, {3, 25}, {3, 26}, {4, 27}, {4, 28}, {5, 29}, {5, 30}, {4, 31}, {3, 31}};
            for (int sd = -1; sd <= 1; sd += 2) for (int[] q : horn) { set(GU - 6, q[1], GV + sd * q[0], BONE, 0); set(GU - 5, q[1], GV + sd * q[0], BONE, 0); }
            for (int k = 0; k < 6; k++) {
                int u = GU + (int) Math.round(6 * Math.cos(k * Math.PI / 3)), v = GV + (int) Math.round(6 * Math.sin(k * Math.PI / 3));
                set(u, 1, v, WALL, 1); set(u, 2, v, WALL, 1); set(u, 3, v, BONE, 0);
            }
            fill(GU + 3, 1, GV - 1, GU + 4, 1, GV + 1, OBSIDIAN, 0);
            set(GU + 3, 1, GV, CLAY, 14);
            f.sign(GU + 2, 2, GV, 1, 0, "THE GOAT STONE\nLEANS TOWARD\nHER COMING");
            f.chest(GU + 4, 2, GV, 1, 0, Sites.DUNGEON, "lore:" + Hash.range(Hash.mix(s.hash ^ 11), 0, 99));
        }

        /** A dry well, its windlass still standing; a ladder down to a burrow that runs to the pits. */
        void well() {
            for (int u = Math.max(WU - 4, u0); u <= Math.min(WU + 4, u1); u++)
                for (int v = Math.max(WV - 4, v0); v <= Math.min(WV + 4, v1); v++) {
                    double d = Math.hypot(u - WU, v - WV);
                    if (d <= 2.3) {
                        set(u, CAVE, v, f.roll(u, CAVE, v, 65) < 0.4 ? BONE : GRAVEL, 0);
                        air(u, v, CAVE + 1, 1);
                    } else if (d <= 3.5) {
                        double lx = 44 - WU, lz = 24 - WV, t = Math.max(0, Math.min(1, ((u - WU) * lx + (v - WV) * lz) / (lx * lx + lz * lz)));
                        boolean burrow = Math.hypot(u - WU - t * lx, v - WV - t * lz) <= 1.3;
                        for (int y = CAVE; y <= 0; y++) { if (burrow && y > CAVE && y <= CAVE + 3) set(u, y, v, AIR); else f.masonry(u, y, v); }
                        if (!(u == WU && v == WV - 3)) set(u, 1, v, BRICK, 0);
                    }
                }
            for (int y = CAVE + 1; y <= 0; y++) f.set(WU, y, WV - 2, LADDER, f.facing(0, 1));
            for (int sd = -1; sd <= 1; sd += 2) for (int y = 2; y <= 3; y++) set(WU + 3 * sd, y, WV, FENCE);
            for (int u = WU - 3; u <= WU + 3; u++) set(u, 4, WV, FENCE);
            set(WU, 3, WV, FENCE);
            f.chest(WU - 1, CAVE + 1, WV - 1, 1, 0, "minecraft:chests/abandoned_mineshaft", "trinket:0.15");
        }

        // ---------------------------------------------------------------- dolmens

        void dolmen(int cu, int cv) {
            if (!hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
            int fu = 0, fv = 0;
            if (Math.abs(cu) >= Math.abs(cv)) fu = -Integer.signum(cu); else fv = -Integer.signum(cv);
            for (int sd = -3; sd <= 3; sd++)
                for (int w = -2; w <= 2; w++) {
                    int u = cu + sd * -fv + w * fu, v = cv + sd * fu + w * fv;
                    boolean side = Math.abs(sd) == 2 && w >= -2 && w <= 1, back = w == -2 && Math.abs(sd) <= 2;
                    if (side || back) for (int y = 1; y <= 3; y++) sarsen(u, y, v);
                    if (Math.abs(sd) <= 3 && w >= -2 && w <= 2 && !(Math.abs(sd) == 3 && w == 2)) {
                        if (f.keep(u, 4, v, 0.92)) set(u, 4, v, STONE, 5);
                    }
                    if (Math.abs(sd) <= 1 && w >= -1) air(u, v, 1, 3);
                }
            f.chest(cu, 1, cv, fu, fv, (cu + cv & 1) == 0 ? Sites.CORRIDOR : Sites.DUNGEON);
        }
    }
}
