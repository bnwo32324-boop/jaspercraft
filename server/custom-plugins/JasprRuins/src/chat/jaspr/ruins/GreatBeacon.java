package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Beacon of R'lyeh (epoch 6; its keeper the Lamplighter). A lighthouse fortress on a rocky islet in the shallows:
 * a banded tower rising some hundred blocks over the yard, its long stair winding twice round an open well (an oil store
 * spans the well halfway up) to the lantern hall at the top, where the Lamplighter waits, with a gallery round it, the
 * green lamp above it under a dome and a spire. Round the tower: a curtain wall with a gatehouse, the keeper's house
 * (whose cellar ladder drops into a sea cave under the islet) and the oil stores; a watch battery on a bastion over the
 * sea with its powder magazine beneath; before the gate a quay, and a drowned harbour inside two breakwaters with
 * harbour lights at their heads, two sunken boats and the colonnade of an older, deeper quay. Hollows below the sea's
 * surface hold water, above it air. Every block is a function of the site (plan) and of position: canvas-blind.
 */
final class GreatBeacon extends GreatDesign {
    static final int R = 40;
    /** The tower's centre; the harbour's centre (u = 0) and half-axes; the sea cave's centre and half-axes. */
    static final int TU = 0, TV = 2, HV = -30, CU = -21, CV = 3;
    static final double HA = 32, HB = 8.5, CA = 7, CB = 6;

    static final class Plan extends Layout {
        /** ws: local y of the sea's top water block; W: the same, never below the floor; T: the yard's floor; Q: the quay's;
         *  D: the battery's deck; H: the lantern hall's floor. */
        int ws, W, T, Q, D, H;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.ws = Plans.SEA - s.base;
        p.W = Math.max(p.ws, 0);
        p.T = p.W + 8; p.Q = p.W + 2; p.D = p.T + 4; p.H = p.T + 72;
        Frame fr = new Frame(null, s.x, s.z, s.base, s.rot);
        int t = p.T, q = p.Q, h = p.H;
        p.boss = new int[] {TU, h + 1, TV + 3};
        p.garrisons.add(g(-12, q + 1, -21, "deep_one+zombie+stray"));                    // the quay
        p.garrisons.add(g(12, q + 1, -21, "deep_one+husk+creeper"));                     // the quay
        p.garrisons.add(g(-29, q + 1, -33, "skeleton+stray+hound"));                     // the west breakwater
        p.garrisons.add(g(29, q + 1, -33, "cult_zealot+skeleton+vindicator"));           // the east breakwater
        p.garrisons.add(g(0, floor(s, fr, p, 0, HV) + 2, HV, "guardian+deep_one"));        // the drowned harbour
        p.garrisons.add(g(-24, floor(s, fr, p, -24, -28) + 2, -28, "deep_one+guardian"));  // the drowned harbour
        p.garrisons.add(g(-7, t + 1, -10, "cult_zealot+cult_adept+zombie_villager"));    // the yard, inside the gate
        p.garrisons.add(g(16, t + 1, 13, "ghoul+tomb_crawler+spider"));                  // the yard, behind the stores
        p.garrisons.add(g(-18, t + 1, 1, "deep_one+witch+zombie"));                      // the keeper's house
        p.garrisons.add(g(-18, t + 6, 6, "cult_adept+evoker+illusioner"));               // the keeper's chamber
        p.garrisons.add(g(18, t + 1, 1, "ghoul+husk+silverfish+endermite"));             // the oil stores
        p.garrisons.add(g(-17, p.W + 2, 3, "deep_one+shoggoth+slime"));                  // the sea cave's ledge
        p.garrisons.add(g(0, p.D + 1, 30, "skeleton+wither_skeleton+creeper"));          // the battery
        p.garrisons.add(g(-3, t + 1, 23, "tomb_crawler+cave_spider+spider"));            // the powder magazine
        p.garrisons.add(g(TU - 3, t + 1, TV, "cult_zealot+zombie+husk"));                // the tower's foot
        p.garrisons.add(g(TU + 5, t + 10, TV + 5, "nightgaunt+mi_go"));                  // a landing of the stair
        p.garrisons.add(g(TU, t + 37, TV, "star_spawn+mi_go+enderman"));                 // the oil store in the well
        p.garrisons.add(g(TU - 4, h + 1, TV - 4, "!deep_one+star_spawn+hound"));         // the lantern hall
        p.garrisons.add(g(TU, h + 1, TV - 11, "nightgaunt"));                            // the gallery
        return p;
    }

    /** The harbour basin's floor (local y) at a column: the sea floor, dredged to nine below the water. */
    static int floor(Plans.GreatSite s, Frame fr, Plan p, int u, int v) {
        int t = s.surface(fr.wx(u, v), fr.wz(u, v)) - s.base;
        return Math.max(-12, Math.min(t, p.W - 9));
    }

    /** Stair height (0..36 within a turn) of a band cell of the tower (tower-local, max(|du|, |dv|) in 5..6): corners are landings. */
    static int stepH(int du, int dv) {
        boolean e = du >= 5, n = dv >= 5, w = du <= -5, s = dv <= -5;
        if (e && s) return 0;
        if (e && n) return 9;
        if (w && n) return 18;
        if (w && s) return 27;
        if (e) return dv + 5;
        if (n) return 9 + (5 - du);
        if (w) return 18 + (5 - dv);
        return 27 + (du + 5);
    }

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) { new D(s, (Plan) layout, c).draw(); }

    private static final class D {
        final Plans.GreatSite s;
        final Plan p;
        final Frame f;
        final int u0, u1, v0, v1, cu0, cv0, ws, W, T, Q, DK, H;
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
            ws = p.ws; W = p.W; T = p.T; Q = p.Q; DK = p.D; H = p.H;
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
        /** Water up to the sea's surface, air above it. */
        void hollow(int u, int y, int v) { set(u, y, v, s.base + y <= Plans.SEA ? Canvas.WATER : AIR); }
        void hollow(int u, int v, int y0, int y1) { for (int y = y0; y <= y1; y++) hollow(u, y, v); }
        void air(int u, int v, int y0, int y1) { for (int y = y0; y <= y1; y++) set(u, y, v, AIR); }

        void fill(int a0, int y0, int b0, int a1, int y1, int b1, int id, int m) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, m);
        }

        int stair(int du, int dv) { return f.stairs(du, dv, false); }

        // ---------------------------------------------------------------- materials

        void rock(int u, int y, int v) {
            double q = f.roll(u, y, v, 71);
            if (y <= W + 1 && q < 0.2) { set(u, y, v, PRISMARINE, q < 0.1 ? 0 : 1); return; }
            if (q < 0.40) set(u, y, v, STONE, 0); else if (q < 0.62) set(u, y, v, STONE, 5); else if (q < 0.76) set(u, y, v, COBBLE);
            else if (q < 0.90) set(u, y, v, MOSSY); else set(u, y, v, STONE, 6);
        }

        /** The tower's daymark bands: the Choir's dark stone, and bands of pale bone and diorite. */
        void band(int u, int y, int v) {
            double q = f.roll(u, y, v, 72);
            if (((y - T) / 8 & 1) == 0) { if (q < 0.5) set(u, y, v, PRISMARINE, 2); else f.eldritch(u, y, v); return; }
            if (q < 0.62) set(u, y, v, STONE, 4); else if (q < 0.9) set(u, y, v, BONE, 0); else set(u, y, v, STONE, 3);
        }

        // ---------------------------------------------------------------- the whole

        void draw() {
            if (u0 > u1 || v0 > v1) return;
            base();
            if (hit(-26, -18, 26, 22)) yard();
            if (hit(CU - 12, CV - 8, CU + 9, CV + 8)) cave();
            if (hit(-23, -5, -13, 11)) house();
            if (hit(13, -5, 23, 11)) stores();
            if (hit(-14, 12, 14, 37)) battery();
            if (hit(-34, -40, 34, -18)) harbour();
            if (hit(TU - 13, TV - 13, TU + 13, TV + 13)) tower();
        }

        double yardE(int u, int v) { double a = u / 25.0, b = (v - 3) / 18.5; return Math.sqrt(a * a + b * b); }
        double harbourE(int u, int v) { double a = u / HA, b = (v - HV) / HB; return Math.sqrt(a * a + b * b); }
        boolean bastion(int u, int v) { return Math.abs(u) <= 13 && v >= 17 && v <= 23 || v > 23 && Math.hypot(u, v - 23) <= 13; }
        boolean quay(int u, int v) { return Math.abs(u) <= 22 && v >= -24 && v <= -18; }
        boolean arm(int u, int v) { double e = harbourE(u, v); return e >= 0.92 && e <= 1.06 && v < -21 && !(Math.abs(u) <= 5 && v < HV); }
        boolean basin(int u, int v) { return harbourE(u, v) < 0.92 && v < -24; }

        /** The rock of the islet, the quay, the breakwaters, the bastion and the dredged basin, column by column. */
        void base() {
            for (int u = u0; u <= u1; u++)
                for (int v = v0; v <= v1; v++) {
                    int t = g(u, v);
                    double ey = yardE(u, v);
                    int top = Integer.MIN_VALUE;
                    int kind = 0;
                    if (ey <= 1.0) { top = T; kind = 1; }
                    else {
                        double en = ey + (Hash.noise(s.hash, f.wx(u, v), f.wz(u, v), 5) - 0.5) * 0.12;
                        if (en <= 1.2) { top = T - (int) Math.round((T - W + 2) * (en - 1.0) / 0.2) + (f.roll(u, 0, v, 73) < 0.3 ? 1 : 0); kind = 2; }
                    }
                    if (quay(u, v) && Q > top) { top = Q; kind = 3; }
                    if (arm(u, v) && Q > top) { top = Q; kind = 4; }
                    if (bastion(u, v)) { top = DK; kind = 5; }
                    if (kind == 0) {
                        if (!basin(u, v)) continue;
                        int fl = Math.max(-12, Math.min(t, W - 9));
                        if (t > fl) { hollow(u, v, fl + 1, Math.max(t + 1, ws)); }
                        double q = f.roll(u, fl, v, 74);
                        set(u, fl, v, q < 0.4 ? GRAVEL : q < 0.6 ? 82 : q < 0.8 ? PRISMARINE : STONE, q >= 0.6 && q < 0.8 ? 2 : q >= 0.8 ? 5 : 0);
                        continue;
                    }
                    int from = Math.max(-12, Math.min(t, top) - 1);
                    for (int y = from; y < top; y++) { if (kind <= 2) rock(u, y, v); else f.eldritch(u, y, v); }
                    if (kind == 1) f.paving(u, top, v);
                    else if (kind == 2) rock(u, top, v);
                    else if (kind == 5) { if (((u + v) & 1) == 0) set(u, top, v, PRISMARINE, 1); else f.paving(u, top, v); }
                    else f.paving(u, top, v);
                    hollow(u, v, top + 1, Math.max(t + 1, ws));
                    if (kind == 1) air(u, v, top + 1, top + 3);
                    // the breakwaters' seaward parapet and the quay's bollards
                    if (kind == 4 && harbourE(u, v) > 1.0) set(u, Q + 1, v, WALL, 0);
                    if (kind == 3 && v == -24 && Math.floorMod(u, 6) == 0) { set(u, Q + 1, v, WALL, 0); set(u, Q + 2, v, WALL, 0); }
                }
        }

        // ---------------------------------------------------------------- the yard, its curtain wall and gatehouse

        void yard() {
            for (int u = Math.max(-26, u0); u <= Math.min(26, u1); u++)
                for (int v = Math.max(-18, v0); v <= Math.min(22, v1); v++) {
                    double e = yardE(u, v);
                    if (e > 1.0 || e <= 0.88 || bastion(u, v)) continue;
                    boolean gate = Math.abs(u) <= 1 && v < 0;
                    boolean outer = e > 0.94;
                    for (int y = T + 1; y <= T + 4; y++) { if (gate && y <= T + 3) continue; f.eldritch(u, y, v); }
                    if (outer) { f.eldritch(u, T + 5, v); if (((u + v) & 1) == 0) set(u, T + 6, v, PRISMARINE, 2); }
                    else f.paving(u, T + 4, v);
                }
            // the gatehouse: two square towers, an arch, a portcullis half raised
            for (int sd = -1; sd <= 1; sd += 2)
                for (int u = 3; u <= 6; u++)
                    for (int v = -18; v <= -13; v++) {
                        int uu = sd * u;
                        if (!in(uu, v)) continue;
                        int foot = yardE(uu, v) <= 1.0 ? T : Q;
                        for (int y = foot + 1; y <= T + 9; y++) { if (y == T + 6) f.glyph(uu, y, v); else f.eldritch(uu, y, v); }
                        if (((u + v) & 1) == 0) set(uu, T + 10, v, PRISMARINE, 2);
                    }
            for (int u = -2; u <= 2; u++) for (int v = -18; v <= -13; v++) if (Math.abs(u) == 2) { for (int y = Q + 1; y <= T + 4; y++) if (in(u, v)) f.eldritch(u, y, v); }
            for (int u = -1; u <= 1; u++) for (int v = -16; v <= -13; v++) { set(u, T + 4, v, BRICK, 3); set(u, T + 5, v, BRICK, 0); }
            for (int u = -1; u <= 1; u++) set(u, T + 3, -15, IRON_BARS);
            // the stair from the quay up to the gate
            for (int k = 1; k <= 6; k++)
                for (int u = -1; u <= 1; u++) {
                    int v = -20 + k, y = Q + k;
                    if (!in(u, v)) continue;
                    for (int yy = Q - 2; yy < y; yy++) f.eldritch(u, yy, v);
                    set(u, y, v, BRICK_STAIRS, stair(0, 1));
                    air(u, v, y + 1, Math.min(T + 3, y + 4));
                }
            for (int u = -1; u <= 1; u++) air(u, -13, T + 1, T + 3);
            set(-3, T + 8, -18, SEA_LANTERN); set(3, T + 8, -18, SEA_LANTERN);
            f.sign(-4, Q + 3, -19, 0, -1, "THE BEACON\nOF R'LYEH\nITS LAMP\nNEVER SLEEPS");
            // stairs up to the rampart walk, either side of the gate
            for (int sd = -1; sd <= 1; sd += 2)
                for (int k = 1; k <= 4; k++) {
                    int u = sd * (7 + k), v = -11;
                    if (!in(u, v)) continue;
                    for (int y = T + 1; y < T + k; y++) f.eldritch(u, y, v);
                    set(u, T + k, v, BRICK_STAIRS, stair(sd, 0));
                    air(u, v, T + k + 1, T + k + 3);
                }
        }

        // ---------------------------------------------------------------- the tower

        void tower() {
            for (int u = Math.max(TU - 13, u0); u <= Math.min(TU + 13, u1); u++)
                for (int v = Math.max(TV - 13, v0); v <= Math.min(TV + 13, v1); v++) {
                    int du = u - TU, dv = v - TV, m = Math.max(Math.abs(du), Math.abs(dv));
                    double d = Math.hypot(du, dv);
                    if (d > 12.6 && m > 6) continue;
                    if (m <= 6) inside(u, v, du, dv, m);
                    else wall(u, v, du, dv, d);
                    top(u, v, du, dv, d);
                }
            // the tower's door and its sign; the furniture of the stair, the oil store and the hall
            if (hit(TU - 2, TV - 13, TU + 2, TV - 6)) {
                set(TU, T + 6, TV - 12, SEA_LANTERN);
                f.sign(TU + 2, T + 3, TV - 13, 0, -1, "A HUNDRED\nSTEPS UP\nTHE KEEPER\nWAITS");
            }
            furnishHall();
        }

        /** The wall at a column outside the shaft: banded, tapering from 12.5 to 9.5, with its door, slits and the hall's doorways. */
        void wall(int u, int v, int du, int dv, double d) {
            boolean axisU = du == 0, axisV = dv == 0;
            for (int y = T + 1; y <= H + 8; y++) {
                double rout = y <= H ? 12.5 - 3.0 * (y - T) / 72.0 : 9.5;
                if (d > rout) continue;
                if (dv <= -7 && Math.abs(du) <= 1 && y <= T + 4) { if (y == T + 4 && Math.abs(du) == 1) band(u, y, v); else set(u, y, v, AIR); continue; }
                if (y > H && y <= H + 4 && (Math.abs(du) <= 1 || Math.abs(dv) <= 1)) { set(u, y, v, AIR); continue; }
                if (axisU || axisV) {
                    int h = axisV ? (du > 0 ? 5 : 23) : (dv > 0 ? 14 : 32);
                    int k = Math.floorMod(y - T - h - 2, 36);
                    if (k <= 1 && y < H - 2) { set(u, y, v, AIR); continue; }
                }
                band(u, y, v);
            }
            // the gallery round the hall, on corbels
            if (d > 9.5 && d <= 12.6) {
                set(u, H, v, PRISMARINE, 2);
                if (d > 11.6) set(u, H + 1, v, WALL, 0);
                if (((du + dv) & 1) == 0 && d <= 11.6) set(u, H - 1, v, BRICK_STAIRS, f.stairs(Integer.signum(du) * (Math.abs(du) >= Math.abs(dv) ? 1 : 0), Integer.signum(dv) * (Math.abs(dv) > Math.abs(du) ? 1 : 0), true));
            }
        }

        /** The shaft: the ground floor, the stair round the open well with its rail, the oil store, the lantern hall. */
        void inside(int u, int v, int du, int dv, int m) {
            set(u, T, v, m <= 1 ? SEA_LANTERN : PRISMARINE, m <= 1 ? 0 : (du + dv & 1) == 0 ? 1 : 2);
            air(u, v, T + 1, H - 1);
            if (m >= 5) {
                int h = stepH(du, dv);
                boolean corner = Math.abs(du) >= 5 && Math.abs(dv) >= 5;
                int fu = 0, fv = 0;
                if (du >= 5 && !corner) fv = 1; else if (dv >= 5 && !corner) fu = -1; else if (du <= -5 && !corner) fv = -1; else fu = 1;
                for (int rev = 0; rev <= 1; rev++) {
                    int y = T + h + 36 * rev;
                    if (y <= T || y >= H) continue;
                    if (corner) { if (Math.abs(du) == 6 && Math.abs(dv) == 6) set(u, y, v, SEA_LANTERN); else f.masonry(u, y, v); }
                    else set(u, y, v, BRICK_STAIRS, stair(fu, fv));
                }
            } else {
                if (m == 4) {
                    int bu = Math.abs(du) == 4 ? du + Integer.signum(du) : du, bv = Math.abs(dv) == 4 ? dv + Integer.signum(dv) : dv;
                    int h = stepH(bu, bv);
                    for (int rev = 0; rev <= 1; rev++) {
                        int y = T + h + 36 * rev + 1;
                        if (y <= T + 1 || y == T + 36 || y == T + 37 || y >= H) continue;
                        set(u, y, v, IRON_BARS);
                    }
                }
                // the oil store spanning the well halfway up
                set(u, T + 36, v, PLANKS, 5);
                if (m == 4 && !(du >= 3 && dv <= -3)) set(u, T + 37, v, IRON_BARS);
            }
            // the lantern hall
            set(u, H, v, m <= 1 ? OBSIDIAN : PRISMARINE, m <= 1 ? 0 : 1);
            air(u, v, H + 1, H + 7);
            if (!(du == 6 && dv == 3)) f.eldritch(u, H + 8, v);
        }

        /** Above the hall: the lantern room, its lamp, the dome and the spire. */
        void top(int u, int v, int du, int dv, double d) {
            if (d > 9.6) return;
            int L = H + 8;
            if (d <= 8.5) for (int y = L + 1; y <= L + 7; y++) {
                if (d > 7.5) {
                    double a = Math.atan2(dv, du) / (Math.PI / 4);
                    boolean post = Math.abs(a - Math.rint(a)) < 0.12;
                    if (post) set(u, y, v, PRISMARINE, 2); else set(u, y, v, 95, 13);
                } else if (d <= 2.5 && y >= L + 2 && y <= L + 6) set(u, y, v, SEA_LANTERN);
                else if (d <= 3.5 && d > 2.5) set(u, y, v, 95, 13);
                else if (d <= 2.5) set(u, y, v, PRISMARINE, 2);
                else set(u, y, v, AIR);
            }
            for (int y = L + 8; y <= L + 15; y++) {
                double k = (y - (L + 8)) / 7.0, r = 9.2 * Math.sqrt(Math.max(0, 1 - k * k));
                if (d > r || r >= 5 && d <= r - 1.4 && y > L + 8) continue;
                double a = Math.atan2(dv, du) / (Math.PI / 4);
                if (Math.abs(a - Math.rint(a)) < 0.1) set(u, y, v, PRISMARINE, 1); else set(u, y, v, PRISMARINE, 2);
            }
            if (du == 0 && dv == 0) {
                for (int y = L + 16; y <= L + 19; y++) set(u, y, v, WALL, 0);
                for (int y = L + 20; y <= L + 22; y++) set(u, y, v, IRON_BARS);
                set(u, L + 23, v, SEA_LANTERN);
            }
            if (Math.abs(du) + Math.abs(dv) == 1) set(u, L + 23, v, 95, 13);
        }

        void furnishHall() {
            fill(TU - 1, H + 1, TV - 1, TU + 1, H + 1, TV + 1, PRISMARINE, 2);
            set(TU, H + 2, TV, SEA_LANTERN);
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) set(TU + a, H + 2, TV + b, IRON_BARS);
            for (int y = H + 1; y <= H + 8; y++) f.set(TU + 6, y, TV + 3, LADDER, f.facing(-1, 0));
            f.chest(TU - 5, H + 1, TV + 5, 1, 0, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(s.hash, 0, 99) + ";trinket:0.6");
            f.chest(TU + 5, H + 1, TV + 5, -1, 0, Sites.DESERT, "trinket:0.2");
            f.sign(TU - 6, H + 3, TV - 4, 1, 0, "THE LAMP\nBURNS GREEN\nFOR THOSE\nBELOW");
            // the stair's furniture: a chest on the first landing, the oil store's vats and its spawner
            f.chest(TU + 6, T + 10, TV + 6, -1, 0, Sites.DUNGEON);
            set(TU + 2, T + 37, TV + 2, CAULDRON); set(TU + 2, T + 37, TV + 3, CAULDRON); set(TU - 2, T + 37, TV + 3, CAULDRON);
            f.chest(TU - 3, T + 37, TV + 3, 1, 0, Sites.CORRIDOR);
            f.spawner(TU - 3, T + 37, TV - 2, "SKELETON");
        }


        // ---------------------------------------------------------------- the keeper's house and the stores

        void house() {
            for (int u = Math.max(-22, u0); u <= Math.min(-14, u1); u++)
                for (int v = Math.max(-4, v0); v <= Math.min(10, v1); v++) {
                    boolean wall = u == -22 || u == -14 || v == -4 || v == 10;
                    int ridge = 14 - Math.abs(u + 18);
                    if (wall) {
                        boolean door = u == -14 && (v == 2 || v == 3);
                        for (int y = T + 1; y <= T + 9; y++) {
                            if (door && y <= T + 3) { set(u, y, v, AIR); continue; }
                            boolean window = (y == T + 3 || y == T + 7) && (v == 0 || v == 6) && (u == -22 || u == -14);
                            if (window) set(u, y, v, 102); else f.masonry(u, y, v);
                        }
                        if (v == -4 || v == 10) for (int y = T + 10; y <= T + ridge; y++) f.masonry(u, y, v);
                    } else {
                        air(u, v, T + 1, T + 4);
                        if (!(v <= -2 && u >= -18)) set(u, T + 5, v, PLANKS, 5); else set(u, T + 5, v, AIR);
                        air(u, v, T + 6, T + 9);
                    }
                    if (f.keep(u, T + 10, v, 0.85)) set(u, T + ridge, v, u == -18 ? 168 : BRICK_STAIRS, u == -18 ? 2 : stair(u < -18 ? 1 : -1, 0));
                }
            if (!hit(-22, -4, -14, 10)) return;
            // the stair to the chamber along the south wall
            for (int k = 1; k <= 4; k++) for (int v = -3; v <= -2; v++) { set(-14 - k, T + k, v, BRICK_STAIRS, stair(-1, 0)); for (int y = T + 1; y < T + k; y++) f.masonry(-14 - k, y, v); }
            // the hearth and its chimney, a table, the cellar ladder down to the sea cave
            set(-21, T + 1, 3, MAGMA); set(-21, T + 1, 2, COBBLE); set(-21, T + 1, 4, COBBLE);
            for (int y = T + 2; y <= T + 16; y++) set(-22, y, 3, BRICK, y > T + 13 ? 0 : 2);
            set(-19, T + 1, 5, FENCE); set(-19, T + 2, 5, 126, 5);
            f.chest(-21, T + 1, 8, 1, 0, Sites.SMITH);
            f.chest(-21, T + 6, 0, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 3), 0, 99));
            for (int v = 4; v <= 8; v++) set(-21, T + 6, v, BOOKSHELF);
            set(-15, T + 6, 9, TORCH, 5);
            f.sign(-13, T + 3, 1, 1, 0, "KEEPER HOLT\nSAW SHAPES\nIN THE BEAM\nAND STAYED");
        }

        void stores() {
            for (int u = Math.max(14, u0); u <= Math.min(22, u1); u++)
                for (int v = Math.max(-4, v0); v <= Math.min(10, v1); v++) {
                    boolean wall = u == 14 || u == 22 || v == -4 || v == 10 || v == 3 && u > 17;
                    boolean door = u == 14 && (v == 2 || v == 3) || v == 3 && u == 19;
                    if (wall) {
                        for (int y = T + 1; y <= T + 6; y++) { if (door && y <= T + 3) set(u, y, v, AIR); else f.masonry(u, y, v); }
                        if (((u + v) & 1) == 0 && (u == 14 || u == 22 || v == -4 || v == 10)) f.masonry(u, T + 7, v);
                    } else air(u, v, T + 1, T + 6);
                    if (!wall && f.keep(u, T + 7, v, 0.8)) set(u, T + 7, v, SLAB, 5);
                }
            if (!hit(14, -4, 22, 10)) return;
            for (int v = -3; v <= 1; v++) set(21, T + 1, v, CAULDRON);
            set(16, T + 1, 9, PLANKS, 1); set(17, T + 1, 9, PLANKS, 1); set(16, T + 2, 9, PLANKS, 1); set(15, T + 1, 9, PLANKS, 1);
            f.chest(21, T + 1, 9, -1, 0, "minecraft:chests/abandoned_mineshaft");
            f.chest(15, T + 1, -3, 1, 0, Sites.CORRIDOR);
            f.spawner(20, T + 1, 7, "SILVERFISH");
        }

        // ---------------------------------------------------------------- the sea cave under the islet

        void cave() {
            for (int u = Math.max(CU - 12, u0); u <= Math.min(CU + 9, u1); u++)
                for (int v = Math.max(CV - 8, v0); v <= Math.min(CV + 8, v1); v++) {
                    double a = (u - CU) / CA, b = (v - CV) / CB, e = Math.sqrt(a * a + b * b);
                    boolean mouth = u < CU && v >= CV - 1 && v <= CV + 1 && u >= -30;
                    if (e > 1.0 && !mouth) {
                        if (e <= 1.3) for (int y = W - 4; y <= W + 7; y++) rock(u, y, v);
                        continue;
                    }
                    int roof = mouth && e > 1.0 ? W + 3 : W + 3 + (int) Math.round(3 * Math.sqrt(Math.max(0, 1 - e * e)));
                    boolean ledge = u >= CU + 3 && !mouth;
                    if (ledge) {
                        rock(u, W, v); set(u, W + 1, v, f.roll(u, W + 1, v, 75) < 0.5 ? PRISMARINE : STONE, f.roll(u, W + 1, v, 75) < 0.5 ? 1 : 5);
                        air(u, v, W + 2, roof - 1);
                    } else {
                        set(u, W - 3, v, f.roll(u, W - 3, v, 76) < 0.5 ? GRAVEL : PRISMARINE, 0);
                        hollow(u, v, W - 2, roof - 1);
                    }
                    rock(u, roof, v);
                }
            if (!hit(CU - 3, CV - 3, CU + 6, CV + 6)) return;
            for (int y = W + 2; y <= T; y++) f.set(-17, y, 7, LADDER, f.facing(0, -1));
            f.chest(-16, W + 2, 1, -1, 0, Sites.JUNGLE, "trinket:0.25");
            f.chest(-18, W + 2, 6, 0, -1, "minecraft:chests/stronghold_crossing");
            f.spawner(CU - 3, W - 2, CV, "ZOMBIE");
            set(-15, W + 2, 4, TORCH, 5);
        }

        // ---------------------------------------------------------------- the watch battery and its magazine

        void battery() {
            for (int u = Math.max(-13, u0); u <= Math.min(13, u1); u++)
                for (int v = Math.max(17, v0); v <= Math.min(36, v1); v++) {
                    if (!bastion(u, v)) continue;
                    double d = v > 23 ? Math.hypot(u, v - 23) : Math.abs(u);
                    boolean rim = d > 11.8 || v > 23 && d > 11.8;
                    boolean mag = Math.abs(u) <= 6 && v >= 19 && v <= 27;
                    if (mag) {
                        boolean mw = Math.abs(u) == 6 || v == 19 || v == 27;
                        for (int y = T + 1; y <= T + 3; y++) { if (mw) f.eldritch(u, y, v); else set(u, y, v, AIR); }
                        if (!mw) f.paving(u, T, v);
                    }
                    if (rim) { f.eldritch(u, DK + 1, v); if (((u + v) & 1) == 0) f.eldritch(u, DK + 2, v); }
                    else {
                        air(u, v, DK + 1, DK + 3);
                        if (v == 17 && !(Math.abs(u) >= 8 && Math.abs(u) <= 10)) set(u, DK + 1, v, WALL, 0);
                    }
                }
            if (!hit(-13, 15, 13, 37)) return;
            // the magazine's door from the yard, its sign, chest and spawner
            for (int v = 17; v <= 19; v++) for (int y = T + 1; y <= T + 2; y++) set(0, y, v, AIR);
            set(0, T + 3, 17, BRICK, 3);
            f.sign(1, T + 2, 16, 0, -1, "FIRE ON ANY\nTHAT RISE\nFROM THE SEA");
            f.chest(5, T + 1, 26, -1, 0, Sites.DESERT, "trinket:0.2");
            f.spawner(-5, T + 1, 26, "CAVE_SPIDER");
            // stairs from the yard up to the deck
            for (int sd = -1; sd <= 1; sd += 2)
                for (int k = 1; k <= 4; k++)
                    for (int u = 8; u <= 10; u++) {
                        int uu = sd * u, v = 12 + k;
                        for (int y = T + 1; y < T + k; y++) f.eldritch(uu, y, v);
                        set(uu, T + k, v, BRICK_STAIRS, stair(0, 1));
                        air(uu, v, T + k + 1, T + k + 3);
                    }
            // the guns: dart traps in the rim, each fired from a plate set on it
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(30 + 40 * k);
                int u = (int) Math.round(12.4 * Math.cos(a)), v = 23 + (int) Math.round(12.4 * Math.sin(a));
                int fu = Math.abs(Math.cos(a)) > 0.7 ? (int) Math.signum(Math.cos(a)) : 0, fv = fu == 0 ? 1 : 0;
                f.dispenser(u, DK, v, fu, fv);
                set(u, DK + 1, v, PLATE);
                set(u, DK + 2, v, AIR);
            }
            set(-8, DK + 1, 31, SEA_LANTERN);
        }

        // ---------------------------------------------------------------- the drowned harbour

        void harbour() {
            wreck(-16, -30, 1);
            wreck(15, -28, -1);
            for (int sd = -1; sd <= 1; sd += 2) {
                for (int k = 0; k < 2; k++) {
                    int cu = sd * (12 + 10 * k), cv = -34;
                    if (!hit(cu - 1, cv - 1, cu + 1, cv + 1)) continue;
                    int fl = Math.max(-12, Math.min(g0(cu, cv), W - 9));
                    for (int y = fl + 1; y <= ws - 2; y++) { if (y % 4 == 0) f.glyph(cu, y, cv); else f.eldritch(cu, y, cv); }
                }
                if (hit(sd * 22 - 1, -35, sd * 12 + 1, -33)) for (int u = Math.min(sd * 12, sd * 22); u <= Math.max(sd * 12, sd * 22); u++) if (f.keep(u, ws - 1, -34, 0.6)) f.eldritch(u, ws - 1, -34);
                // the harbour light on the breakwater's head
                int lu = sd * 7, lv = -38;
                if (!hit(lu - 1, lv - 1, lu + 1, lv + 1)) continue;
                for (int u = lu - 1; u <= lu + 1; u++)
                    for (int v = lv - 1; v <= lv + 1; v++) {
                        if (!in(u, v)) continue;
                        int fl = Math.min(g(u, v), W - 9);
                        for (int y = Math.max(-12, fl); y <= Q + 9; y++) { if (y == Q + 5) f.glyph(u, y, v); else f.eldritch(u, y, v); }
                        set(u, Q + 10, v, u == lu && v == lv ? SEA_LANTERN : 95, u == lu && v == lv ? 0 : 13);
                        set(u, Q + 11, v, SLAB, 5);
                    }
            }
        }

        int g0(int u, int v) { return s.surface(f.wx(u, v), f.wz(u, v)) - s.base; }

        /** A sunken boat on the harbour floor: a hull of rotten planks along u, its hold flooded, a chest in it, a snapped mast. */
        void wreck(int cu, int cv, int dir) {
            if (!hit(cu - 6, cv - 3, cu + 6, cv + 3)) return;
            int fl = Math.max(-12, Math.min(g0(cu, cv), W - 9));
            for (int u = Math.max(cu - 6, u0); u <= Math.min(cu + 6, u1); u++)
                for (int v = Math.max(cv - 3, v0); v <= Math.min(cv + 3, v1); v++) {
                    int l = Math.abs(u - cu), w = l <= 3 ? 2 : l <= 5 ? 1 : 0, b = Math.abs(v - cv);
                    if (b > w) continue;
                    boolean side = b == w || l == 6;
                    set(u, fl + 1, v, PLANKS, 5);
                    for (int y = fl + 2; y <= fl + 4; y++) {
                        if (!side || u == cu + 2 * dir && y <= fl + 3) { hollow(u, y, v); continue; }
                        if (y == fl + 4 && !f.keep(u, y, v, 0.7)) { hollow(u, y, v); continue; }
                        set(u, y, v, PLANKS, (y & 1) == 0 ? 5 : 1);
                    }
                    if (!side && f.keep(u, fl + 4, v, 0.5)) set(u, fl + 4, v, PLANKS, 1);
                }
            for (int y = fl + 2; y <= fl + 7 && y <= ws; y++) set(cu - 2 * dir, y, cv, FENCE);
            f.chest(cu + 3 * dir, fl + 2, cv, -dir, 0, dir > 0 ? Sites.DUNGEON : Sites.JUNGLE, dir > 0 ? "trinket:0.15" : null);
        }
    }
}
