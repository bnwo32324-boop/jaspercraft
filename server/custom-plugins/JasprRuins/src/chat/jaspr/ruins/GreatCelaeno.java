package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Library of Celaeno (epoch 6; its keeper the Librarian). A domed great library on a terrace: a cloistered reading
 * court with a reflecting pool before a columned portico and the entrance hall; the rotunda reading hall, thirty blocks
 * of bookshelves to a dome with a lantern, three balconies round it reached by curving stairs and ladders, a walk at
 * the foot of the dome, a seven-rayed star of the Pleiades on its floor and the Book on its pedestal where the Librarian
 * waits; the scriptorium wing (desks and candles) and the map room wing (a floor map of Drownhollow, a globe), each with
 * stacks above; the archive wing with its forbidden stack behind iron bars, and beneath it the half-flooded lower
 * archive, whose stacks stand out of black water; a ruined star tower; a bindery. Every block is a function of the site
 * (plan) and of position: canvas-blind.
 */
final class GreatCelaeno extends GreatDesign {
    static final int R = 42;
    /** The rotunda's centre (u = 0, v = RV), its inner and outer radii, the balconies' floors and the drum's top. */
    static final int RV = 4, B1 = 7, B2 = 14, B3 = 21, DOME = 30, LOW = -10;
    static final double RIN = 13, ROUT = 15.5;

    static final class Plan extends Layout {
        /** The land's height before the reading court's gate (at most 12 up or down): the entrance stair. */
        int front;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        Frame fr = new Frame(null, s.x, s.z, s.base, s.rot);
        int sum = 0;
        for (int u = -3; u <= 3; u++) sum += s.surface(fr.wx(u, -44), fr.wz(u, -44)) - s.base;
        p.front = Math.max(-12, Math.min(12, Math.round(sum / 7f)));
        p.boss = new int[] {0, 1, RV - 4};
        p.garrisons.add(g(-10, 1, -38, "cult_zealot+cult_adept+vindicator"));          // the reading court
        p.garrisons.add(g(9, 1, -28, "skeleton+stray+zombie"));                       // the portico
        p.garrisons.add(g(0, 1, -18, "ghoul+husk+zombie_villager"));                  // the entrance hall
        p.garrisons.add(g(-7, 1, RV + 5, "!cult_adept+illusioner+evoker"));           // the rotunda, by the Librarian
        p.garrisons.add(g(-11, B1 + 1, RV, "nightgaunt+mi_go"));                      // the first balcony
        p.garrisons.add(g(11, B2 + 1, RV - 1, "star_spawn+enderman"));                // the second balcony
        p.garrisons.add(g(0, B3 + 1, RV + 11, "nightgaunt+witch"));                   // the third balcony
        p.garrisons.add(g(0, DOME + 1, RV - 12, "nightgaunt"));                       // the walk under the dome
        p.garrisons.add(g(-30, 1, 2, "cult_zealot+witch+zombie"));                    // the scriptorium
        p.garrisons.add(g(-30, B1 + 1, 3, "silverfish+endermite+spider"));            // the west stacks
        p.garrisons.add(g(24, 1, 3, "mi_go+hound+creeper"));                          // the map room
        p.garrisons.add(g(30, B1 + 1, 3, "skeleton+wither_skeleton+cave_spider"));    // the east stacks
        p.garrisons.add(g(0, 1, 27, "tomb_crawler+ghoul+spider"));                    // the archive
        p.garrisons.add(g(-6, 1, 38, "!star_spawn+illusioner+silverfish"));           // the forbidden stack
        p.garrisons.add(g(-12, LOW + 6, 31, "deep_one+shoggoth+slime"));              // the lower archive's gallery
        p.garrisons.add(g(0, B1 + 1, 30, "hound+stray+creeper"));                     // the north stacks
        p.garrisons.add(g(30, 1, 33, "tomb_crawler+husk+cave_spider"));               // the bindery
        return p;
    }

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) { new D(s, (Plan) layout, c).draw(); }

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

        void fill(int a0, int y0, int b0, int a1, int y1, int b1, int id, int m) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, m);
        }

        int stair(int du, int dv) { return f.stairs(du, dv, false); }

        // ---------------------------------------------------------------- materials

        /** The library's pale ashlar, weathered: polished diorite, stone brick, smooth stone, cracked and mossy here and there. */
        void ashlar(int u, int y, int v) {
            double q = f.roll(u, y, v, 91);
            if (q < 0.34) set(u, y, v, STONE, 4); else if (q < 0.56) set(u, y, v, BRICK, 0); else if (q < 0.70) set(u, y, v, DOUBLE_SLAB, 8);
            else if (q < 0.80) set(u, y, v, STONE, 3); else if (q < 0.91) set(u, y, v, BRICK, 2); else set(u, y, v, BRICK, 1);
        }

        /** The domes' and roofs' sea-green stone. */
        void verdigris(int u, int y, int v) {
            double q = f.roll(u, y, v, 92);
            if (q < 0.55) set(u, y, v, PRISMARINE, 2); else if (q < 0.85) set(u, y, v, PRISMARINE, 1); else set(u, y, v, PRISMARINE, 0);
        }

        void floorStone(int u, int y, int v) {
            double q = f.roll(u, y, v, 93);
            if (((u + v) & 1) == 0) set(u, y, v, q < 0.85 ? STONE : BRICK, q < 0.85 ? 6 : 2); else set(u, y, v, q < 0.8 ? BRICK : STONE, q < 0.8 ? 0 : 4);
        }

        void column(int u, int v, int y0, int y1) {
            set(u, y0, v, QUARTZ, 1);
            for (int y = y0 + 1; y < y1; y++) set(u, y, v, f.keep(u, y, v, 0.93) ? QUARTZ : BRICK, f.keep(u, y, v, 0.93) ? 2 : 2);
            set(u, y1, v, QUARTZ, 1);
        }

        // ---------------------------------------------------------------- the whole

        void draw() {
            if (u0 > u1 || v0 > v1) return;
            ground();
            if (hit(-42, -42, 42, -26)) court();
            if (hit(-13, -32, 13, -11)) entrance();
            if (hit(-42, -11, -15, 17)) wing(-1);
            if (hit(15, -11, 42, 17)) wing(1);
            if (hit(-15, 19, 15, 42)) north();
            if (hit(-17, RV - 17, 17, RV + 17)) rotunda();
            if (hit(-37, 25, -25, 37)) observatory();
            if (hit(23, 23, 39, 37)) bindery();
        }

        /** The terrace: cut down where the land is higher, built up where it is lower, a revetment round its edge, the entrance stair. */
        void ground() {
            for (int u = u0; u <= u1; u++)
                for (int v = v0; v <= v1; v++) {
                    int t = g(u, v);
                    boolean edge = Math.abs(u) == R || Math.abs(v) == R;
                    if (t < 0) { for (int y = Math.max(t + 1, -12); y < 0; y++) if (edge) f.masonry(u, y, v); else f.rubble(u, y, v); }
                    else if (t > 0) { f.clear(u, v, 1, t + 1); if (edge) for (int y = 1; y <= t; y++) f.masonry(u, y, v); }
                    else f.clear(u, v, 1, 1);
                    floorStone(u, 0, v);
                    if (edge && t <= 0 && !(v == -R && Math.abs(u) <= 3)) f.masonry(u, 1, v);
                }
            int n = Math.abs(p.front);
            for (int v = Math.max(-42, v0); v <= Math.min(-43 + n, v1); v++)
                for (int u = Math.max(-4, u0); u <= Math.min(4, u1); u++) {
                    int e = p.front < 0 ? Math.min(0, p.front + (v + 42)) : Math.max(0, p.front - (v + 42));
                    if (e == 0) continue;
                    if (Math.abs(u) == 4) { for (int y = Math.min(e, 0) - 1; y <= Math.max(e, 0) + 1; y++) f.masonry(u, y, v); continue; }
                    for (int y = Math.min(e, 0) - 2; y < e; y++) f.masonry(u, y, v);
                    set(u, e, v, BRICK_STAIRS, stair(0, p.front < 0 ? 1 : -1));
                    air(u, v, e + 1, Math.max(e, 0) + 4);
                }
        }

        // ---------------------------------------------------------------- the reading court

        void court() {
            for (int u = Math.max(-41, u0); u <= Math.min(41, u1); u++)
                for (int v = Math.max(-41, v0); v <= Math.min(-27, v1); v++) {
                    int au = Math.abs(u);
                    boolean walk = v <= -39 || au >= 38 || v >= -29 && au >= 13;
                    boolean colonnade = v == -38 && au <= 37 || au == 37 && v >= -38 && v <= -30 || v == -30 && au >= 13 && au <= 37;
                    if (walk) {
                        if (((u + v) & 1) == 0) set(u, 0, v, QUARTZ, 0); else floorStone(u, 0, v);
                        set(u, 5, v, SLAB, (u + v & 3) == 0 ? 5 : 0);
                        if (au == 41 || v == -41 || v == -27 && au >= 13) for (int y = 1; y <= 4; y++) { if (v == -41 && au <= 3 && y <= 3) continue; ashlar(u, y, v); }
                    } else if (colonnade) {
                        if (au % 4 == 1 && v != -30 || v == -30 && au % 4 == 1 || au == 37 && Math.floorMod(v, 4) == 2) column(u, v, 1, 4);
                        else set(u, 0, v, BRICK, 3);
                        set(u, 5, v, DOUBLE_SLAB, 8);
                    } else {
                        // the garden and its reflecting pool
                        double q = f.roll(u, 0, v, 94);
                        if (au <= 16 && v >= -36 && v <= -34) { set(u, 0, v, Canvas.WATER); set(u, -1, v, PRISMARINE, 1); }
                        else if (au <= 17 && v >= -37 && v <= -33) set(u, 0, v, BRICK, 3);
                        else if (q < 0.3) set(u, 0, v, GRASS); else if (q < 0.45) set(u, 0, v, DIRT, 1);
                    }
                }
            if (!hit(-30, -38, 30, -31)) return;
            // stone benches facing the pool, lecterns, the fountain's statue of Celaeno
            for (int k = -3; k <= 3; k++) {
                if (k == 0) continue;
                int u = k * 7;
                set(u, 1, -32, BRICK_STAIRS, stair(0, 1)); set(u + 1, 1, -32, BRICK_STAIRS, stair(0, 1));
                set(u, 1, -38 + 1, FENCE); set(u, 2, -37, 126, 1);
            }
            fill(-1, 0, -36, 1, 0, -34, BRICK, 3);
            for (int y = 1; y <= 5; y++) set(0, y, -35, y == 5 ? QUARTZ : QUARTZ, y == 5 ? 1 : 2);
            set(0, 6, -35, SEA_LANTERN);
            f.chest(-36, 1, -33, 1, 0, Sites.LIBRARY);
        }

        // ---------------------------------------------------------------- the portico and the entrance hall

        void entrance() {
            for (int u = Math.max(-13, u0); u <= Math.min(13, u1); u++)
                for (int v = Math.max(-31, v0); v <= Math.min(-11, v1); v++) {
                    int au = Math.abs(u);
                    if (v <= -26) {
                        // the portico: six columns, an entablature, a pediment
                        if (au > 12) continue;
                        set(u, 0, v, (u + v & 1) == 0 ? QUARTZ : STONE, (u + v & 1) == 0 ? 0 : 4);
                        if (v == -30 && au % 4 == 3) column(u, v, 1, 12);
                        else air(u, v, 1, 12);
                        for (int y = 13; y <= 14; y++) { if (y == 13 && (v == -31 || au == 12)) set(u, y, v, QUARTZ, 1); else ashlar(u, y, v); }
                        int peak = 15 + (12 - au) / 2;
                        for (int y = 15; y <= peak; y++) { if (v == -31 || v == -26) ashlar(u, y, v); }
                        if (peak >= 15) { if (v > -31 && v < -26) verdigris(u, peak, v); }
                        continue;
                    }
                    // the entrance hall
                    if (au > 8) continue;
                    boolean wall = au == 8 || v == -25;
                    boolean door = v == -25 && au <= 2;
                    if (wall) {
                        for (int y = 1; y <= 13; y++) {
                            if (door && y <= 7) { set(u, y, v, AIR); continue; }
                            boolean window = au == 8 && (v == -22 || v == -15) && y >= 7 && y <= 10;
                            if (window) set(u, y, v, 102); else if (y == 13 || y == 1) set(u, y, v, BRICK, 3); else ashlar(u, y, v);
                        }
                    } else {
                        air(u, v, 1, 12);
                        if (au == 7 && v != -12) for (int y = 1; y <= 4; y++) set(u, y, v, BOOKSHELF);
                        if (au == 4 && (v == -22 || v == -18 || v == -14)) column(u, v, 1, 12);
                        set(u, 0, v, au <= 1 ? 159 : BRICK, au <= 1 ? 10 : (v & 1) == 0 ? 0 : 3);
                    }
                    int top = 13 + (8 - au) / 2;
                    for (int y = 13; y <= top; y++) { if (y == top) verdigris(u, y, v); else if (!wall && y == 13) ashlar(u, y, v); else if (wall) ashlar(u, y, v); }
                    if (!wall) set(u, 13, v, BRICK, 0);
                }
            if (!hit(-8, -26, 8, -12)) return;
            set(0, 12, -18, GLOWSTONE);
            f.sign(-4, 3, -26, 0, -1, "THE LIBRARY\nOF CELAENO\nREAD, AND\nBE READ");
            f.sign(4, 3, -24, 0, 1, "ALL THAT WAS\nWRITTEN IS\nKEPT HERE");
        }

        // ---------------------------------------------------------------- the rotunda

        /** A doorway through the rotunda's drum (and its shelves) at a column and height. */
        boolean doorway(int u, int dv, int y) {
            boolean ground = y >= 1 && y <= 6 && (Math.abs(u) <= 2 || Math.abs(dv) <= 2);
            boolean upper = y > B1 && y <= B1 + 3 && (Math.abs(dv) <= 1 && u != 0 || Math.abs(u) <= 1 && dv > 0);
            return ground || upper;
        }

        /** The curving stairs' height (half-blocks, 0..14) at an angle, or -1 off the stair. */
        static int rampA(double deg) { return deg >= 195 && deg <= 255 ? (int) Math.round(14 * (deg - 195) / 60) : -1; }
        static int rampB(double deg) { return deg >= 15 && deg <= 75 ? (int) Math.round(14 * (deg - 15) / 60) : -1; }

        void rotunda() {
            for (int u = Math.max(-16, u0); u <= Math.min(16, u1); u++)
                for (int v = Math.max(RV - 16, v0); v <= Math.min(RV + 16, v1); v++) {
                    int dv = v - RV;
                    double d = Math.hypot(u, dv);
                    if (d > ROUT + 0.3) continue;
                    double deg = Math.toDegrees(Math.atan2(dv, u));
                    if (deg < 0) deg += 360;
                    if (d > RIN) drum(u, v, dv, d, deg); else hall(u, v, dv, d, deg);
                    dome(u, v, dv, d, deg);
                }
            if (!hit(-12, RV - 12, 12, RV + 12)) return;
            // the pedestal and the Book; the chandelier
            fill(-1, 1, RV - 1, 1, 1, RV + 1, QUARTZ, 1);
            f.chest(0, 2, RV, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(s.hash, 0, 99) + ";trinket:0.6");
            f.sign(0, 1, RV - 2, 0, -1, "SOME BOOKS\nREAD YOU");
            for (int y = 25; y <= DOME + 15; y++) set(0, y, RV, FENCE);
            for (int k = 0; k < 4; k++) { int a = k == 0 ? 2 : k == 1 ? -2 : 0, b = k == 2 ? 2 : k == 3 ? -2 : 0; set(a, 24, RV + b, GLOWSTONE); set(a / 2, 25, RV + b / 2, FENCE); }
            // ladders: second balcony to third, third to the walk under the dome
            for (int y = B2 + 1; y <= B3; y++) f.set(-8, y, RV + 8, LADDER, f.facing(1, 0));
            for (int y = B3 + 1; y <= DOME; y++) f.set(8, y, RV - 8, LADDER, f.facing(-1, 0));
            // the balconies' books and chests
            f.chest(-9, B1 + 1, RV - 6, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 21), 0, 99));
            f.chest(6, B2 + 1, RV + 9, 0, -1, Sites.LIBRARY);
            f.chest(-6, B3 + 1, RV - 9, 0, 1, Sites.CORRIDOR, "trinket:0.2");
            f.chest(10, 1, RV + 6, -1, 0, Sites.LIBRARY);
        }

        void drum(int u, int v, int dv, double d, double deg) {
            for (int y = 1; y <= DOME; y++) {
                if (doorway(u, dv, y)) { set(u, y, v, AIR); continue; }
                boolean window = y >= 24 && y <= 28 && d <= RIN + 1.2 && Math.abs(Math.abs(u) - Math.abs(dv)) <= 1 && Math.abs(u) > 3;
                boolean outerWindow = y >= 24 && y <= 28 && Math.abs(Math.abs(u) - Math.abs(dv)) <= 1 && Math.abs(u) > 3;
                if (outerWindow) { set(u, y, v, window ? AIR : 102); continue; }
                if (y == 1 || y == DOME || y == B1 || y == B2 || y == B3) set(u, y, v, BRICK, 3);
                else if (d > ROUT - 0.6 && (Math.round(deg) % 30 == 0 || Math.round(deg) % 30 == 1)) set(u, y, v, QUARTZ, 2);
                else ashlar(u, y, v);
            }
            if (d > RIN + 0.4) set(u, 0, v, BRICK, 3);
        }

        void hall(int u, int v, int dv, double d, double deg) {
            // the floor: the seven-rayed star of the Pleiades, its stars lit, rings of purple and black
            double ray = deg / (360.0 / 7);
            double off = Math.abs(ray - Math.rint(ray)) * (2 * Math.PI / 7) * d;
            if (d > 12) set(u, 0, v, PRISMARINE, 2);
            else if (d > 8.5 && d <= 9.5) set(u, 0, v, CLAY, 10);
            else if (d > 4.5 && d <= 5.3) set(u, 0, v, QUARTZ, 0);
            else if (off < 0.75 && d <= 11.6 && d > 1.6) set(u, 0, v, d > 10.6 ? SEA_LANTERN : CLAY, d > 10.6 ? 0 : 9);
            else if (d <= 1.6) set(u, 0, v, QUARTZ, 1);
            else set(u, 0, v, ((int) Math.floor(d) & 1) == 0 ? STONE : CLAY, ((int) Math.floor(d) & 1) == 0 ? 6 : 15);
            air(u, v, 1, DOME - 1);
            int ra = rampA(deg), rb = rampB(deg);
            boolean ring = d > 10 && d <= 12;
            // the shelves round the walls, broken only by the doorways and the balconies
            if (d > 12) {
                for (int y = 1; y < DOME; y++) {
                    if (doorway(u, dv, y)) continue;
                    if (y == B1 || y == B2 || y == B3) set(u, y, v, PLANKS, 5); else set(u, y, v, BOOKSHELF);
                }
            }
            // the balconies and their rails
            for (int b = 1; b <= 3; b++) {
                int L = b * 7;
                if (d <= 9 || d > 13) continue;
                boolean overRamp = ring && (b == 1 && ra >= 9 && ra <= 13 || b == 2 && rb >= 9 && rb <= 13);
                if (!overRamp && !(b == 3 && u == -8 && dv == 8)) set(u, L, v, PLANKS, 5);
                boolean gap = b == 1 && ra >= 0 && d > 9 && d <= 10 && ra >= 12 || b == 2 && rb >= 12 && d <= 10;
                if (d <= 10 && !gap) set(u, L + 1, v, 191);
            }
            // the walk under the dome
            if (d > 11 && d <= 13) { if (!(u == 8 && dv == -8)) set(u, DOME, v, PLANKS, 5); if (d <= 12) set(u, DOME + 1, v, 191); }
            // the curving stairs of half-steps, with shelves beneath them
            if (ring && ra >= 0) ramp(u, v, 0, ra, true);
            if (ring && rb >= 0) ramp(u, v, B1, rb, false);
            // reading desks between the rays
            double mid = Math.abs(ray - Math.floor(ray) - 0.5) * (2 * Math.PI / 7) * d;
            if (d > 6 && d <= 7.4 && mid < 1.0) { set(u, 1, v, FENCE); set(u, 2, v, 126, 5); }
        }

        void ramp(int u, int v, int L, int hh, boolean shelves) {
            int y = L + hh / 2;
            for (int yy = L + 1; yy < y; yy++) set(u, yy, v, BOOKSHELF);
            if (y > L) set(u, y, v, PLANKS, 5);
            if ((hh & 1) == 1) set(u, y + 1, v, 126, 5);
        }

        void dome(int u, int v, int dv, double d, double deg) {
            for (int y = DOME + 1; y <= DOME + 16; y++) {
                double k = (y - DOME) / 16.0, r = ROUT * Math.sqrt(Math.max(0, 1 - k * k));
                if (d > r) continue;
                boolean shell = d > r - 1.6 || r < 3.2;
                boolean lantern = d <= 3.2 && y >= DOME + 13;
                if (lantern) continue;
                if (!shell) { if (!(u == 0 && dv == 0) || y < 25) set(u, y, v, AIR); continue; }
                double rib = deg / 45.0;
                if (Math.abs(rib - Math.rint(rib)) * Math.PI / 4 * d < 0.7) set(u, y, v, QUARTZ, 0);
                else if ((y - DOME) % 5 == 0) set(u, y, v, PRISMARINE, 1);
                else verdigris(u, y, v);
            }
            // the lantern on the dome, its windows, its cap and the Eye
            int L0 = DOME + 13;
            if (d <= 3.2) {
                for (int y = L0; y <= L0 + 5; y++) {
                    boolean wall = d > 2.2, window = wall && (u == 0 || dv == 0) && y >= L0 + 2 && y <= L0 + 4;
                    if (wall) { if (window) set(u, y, v, IRON_BARS); else ashlar(u, y, v); } else set(u, y, v, y == L0 ? QUARTZ : AIR, y == L0 ? 1 : 0);
                }
                if (d <= 3.2) verdigris(u, L0 + 6, v);
                if (d <= 2.2) verdigris(u, L0 + 7, v);
                if (u == 0 && dv == 0) { for (int y = L0 + 8; y <= L0 + 11; y++) set(u, y, v, WALL, 0); set(u, L0 + 12, v, SEA_LANTERN); set(u, L0 + 1, v, FENCE); }
            }
        }

        // ---------------------------------------------------------------- the wings: scriptorium (west) and map room (east)

        void wing(int sd) {
            for (int a = 16; a <= 42; a++) {
                int u = sd * a;
                if (u < u0 || u > u1) continue;
                for (int v = Math.max(-10, v0); v <= Math.min(16, v1); v++) {
                    boolean wall = a == 16 || a == 42 || v == -10 || v == 16;
                    boolean rot = a == 16 && Math.abs(v - RV) <= 2;
                    if (wall) {
                        for (int y = 1; y <= 14; y++) {
                            boolean door = rot && (y <= 6 || y > B1 && y <= B1 + 3 && Math.abs(v - RV) <= 1) || v == -10 && a == 29 && y <= 3;
                            boolean window = (v == -10 || v == 16) && a % 4 == 0 && a > 16 && a < 42 && (y >= 3 && y <= 5 || y >= 10 && y <= 12);
                            if (door) set(u, y, v, AIR); else if (window) set(u, y, v, 102); else if (y == 7 || y == 14) set(u, y, v, BRICK, 3); else ashlar(u, y, v);
                        }
                    } else {
                        air(u, v, 1, 13);
                        boolean stairwell = a >= 39 && a <= 40 && v >= -9 && v <= -3;
                        if (!stairwell) set(u, B1, v, PLANKS, 5);
                        set(u, 14, v, BRICK, 0);
                        // the stacks above: rows of shelves, a central aisle
                        if (a % 4 == 2 && a >= 18 && a <= 38 && !(v >= 2 && v <= 4) && v >= -8 && v <= 14) for (int y = B1 + 1; y <= B1 + 3; y++) set(u, y, v, BOOKSHELF);
                    }
                    int roof = 15 + (13 - Math.abs(v - 3)) / 2;
                    for (int y = 15; y <= roof; y++) { if (y == roof) { if (v == 3) verdigris(u, y, v); else set(u, y, v, BRICK_STAIRS, stair(0, v < 3 ? 1 : -1)); } else if (wall && (a == 16 || a == 42)) ashlar(u, y, v); }
                }
            }
            // the stair inside the far end, up to the stacks
            for (int k = 1; k <= 7; k++) for (int a = 39; a <= 40; a++) { int u = sd * a, v = -10 + k; if (!in(u, v)) continue; for (int y = 1; y < k; y++) ashlar(u, y, v); set(u, k, v, BRICK_STAIRS, stair(0, 1)); }
            if (sd < 0) scriptorium(); else mapRoom();
        }

        void scriptorium() {
            if (!hit(-40, -9, -17, 15)) return;
            for (int u = -38; u <= -20; u += 4)
                for (int v = -7; v <= 13; v += 5) {
                    if (v == 3) continue;
                    set(u, 1, v, FENCE); set(u, 2, v, 126, 1); set(u + 1, 1, v, FENCE); set(u + 1, 2, v, 126, 1);
                    set(u, 1, v - 1, 53, stair(0, -1));
                    if ((u + v & 3) == 0) { set(u + 2, 1, v, FENCE); set(u + 2, 2, v, TORCH, 5); }
                }
            for (int v = -8; v <= 14; v++) for (int y = 1; y <= 4; y++) if (v < 2 || v > 4) set(-41, y, v, BOOKSHELF);
            f.chest(-17, 1, -8, -1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 23), 0, 99));
            f.chest(-33, B1 + 1, 15, 0, -1, Sites.CORRIDOR);
            f.spawner(-24, 1, 14, "WITCH");
            f.sign(-17, 3, 2, -1, 0, "THE SCRIBES\nCOPIED WHAT\nTHE STARS\nDICTATED");
        }

        void mapRoom() {
            if (!hit(17, -9, 41, 15)) return;
            // the floor map of Drownhollow: sea, land, hills, the cities, the Door
            for (int u = Math.max(21, u0); u <= Math.min(36, u1); u++)
                for (int v = Math.max(-6, v0); v <= Math.min(12, v1); v++) {
                    boolean rim = u == 21 || u == 36 || v == -6 || v == 12;
                    if (rim) { set(u, 0, v, BRICK, 3); set(u, 1, v, 191); continue; }
                    double n = Hash.noise(s.hash, u, v, 5.5);
                    int col = n < 0.42 ? 11 : n < 0.47 ? 3 : n < 0.68 ? 13 : 12;
                    if (Hash.unit(s.hash, u, v, 31) < 0.04 && n >= 0.47) col = 14;
                    set(u, 0, v, CLAY, col);
                }
            if (in(30, 4)) set(30, 0, 4, OBSIDIAN);
            for (int u = 21; u <= 36; u += 5) set(u, 1, 3, AIR);
            // the globe on its stand
            set(39, 1, 3, QUARTZ, 1);
            for (int u = 37; u <= 41; u++)
                for (int v = 1; v <= 5; v++)
                    for (int y = 2; y <= 6; y++) {
                        double dd = Math.sqrt((u - 39) * (u - 39) + (v - 3) * (v - 3) + (y - 4) * (y - 4));
                        if (dd > 2.4) continue;
                        set(u, y, v, CLAY, Hash.noise(s.hash ^ 7, u * 3 + y, v * 3 + y, 3) < 0.5 ? 11 : 13);
                    }
            f.chest(17, 1, -8, 1, 0, Sites.CORRIDOR);
            f.chest(40, 1, 14, 0, -1, "minecraft:chests/stronghold_crossing");
            f.chest(33, B1 + 1, 15, 0, -1, Sites.LIBRARY, "trinket:0.15");
            f.sign(17, 3, 9, 1, 0, "THE MAP IS\nOLDER THAN\nTHE LAND");
        }

        // ---------------------------------------------------------------- the archive, the forbidden stack, the lower archive

        void north() {
            for (int u = Math.max(-14, u0); u <= Math.min(14, u1); u++)
                for (int v = Math.max(19, v0); v <= Math.min(42, v1); v++) {
                    int au = Math.abs(u);
                    boolean wall = au == 14 || v == 20 || v == 42;
                    if (v == 19) { if (au <= 2) { f.paving(u, 0, v); air(u, v, 1, 6); } continue; }
                    // the lower archive below
                    lower(u, v, au, wall);
                    if (wall) {
                        for (int y = 1; y <= 14; y++) {
                            boolean door = v == 20 && (au <= 2 && y <= 6 || au <= 1 && y > B1 && y <= B1 + 3);
                            boolean window = au == 14 && v % 5 == 0 && (y >= 3 && y <= 5 || y >= 10 && y <= 12);
                            if (door) set(u, y, v, AIR); else if (window) set(u, y, v, 102); else if (y == 7 || y == 14) set(u, y, v, BRICK, 3); else ashlar(u, y, v);
                        }
                    } else {
                        air(u, v, 1, 13);
                        boolean down = u >= -13 && u <= -12 && v >= 22 && v <= 25;
                        if (!down) set(u, 0, v, PLANKS, 5);
                        if (!(u >= -13 && u <= -12 && v >= 21 && v <= 26 && false)) set(u, B1, v, PLANKS, 5);
                        set(u, 14, v, BRICK, 0);
                        // the archive's stacks, the bars of the forbidden stack, the stacks above
                        if (au % 3 == 0 && au >= 3 && au <= 12 && v >= 22 && v <= 31) for (int y = 1; y <= 3; y++) set(u, y, v, BOOKSHELF);
                        if (v == 33) for (int y = 1; y <= 6; y++) if (!(u == 1 && y <= 2)) set(u, y, v, IRON_BARS);
                        if (v >= 34 && (au == 13 || v == 41)) for (int y = 1; y <= 6; y++) set(u, y, v, BOOKSHELF);
                        if (v >= 34 && f.roll(u, 5, v, 95) < 0.12) set(u, 5, v, WEB);
                        if (au % 4 == 2 && v >= 22 && v <= 40 && !(v >= 29 && v <= 31)) for (int y = B1 + 1; y <= B1 + 3; y++) set(u, y, v, BOOKSHELF);
                    }
                    int roof = 15 + (14 - au) / 2;
                    for (int y = 15; y <= roof; y++) { if (y == roof) { if (u == 0) verdigris(u, y, v); else set(u, y, v, BRICK_STAIRS, stair(u < 0 ? 1 : -1, 0)); } else if (v == 20 || v == 42) ashlar(u, y, v); }
                }
            if (!hit(-14, 20, 14, 42)) return;
            // the stair down to the lower archive's gallery, railed at the top
            for (int k = 1; k <= 4; k++) for (int u = -13; u <= -12; u++) { int v = 21 + k; set(u, -k, v, BRICK_STAIRS, stair(0, -1)); air(u, v, -k + 1, 0); }
            for (int v = 22; v <= 25; v++) set(-11, 1, v, 191);
            // the forbidden stack's lectern, its books, its keeper
            set(0, 1, 38, QUARTZ, 1);
            f.chest(0, 2, 38, 0, -1, Sites.DESERT, "lore:" + Hash.range(Hash.mix(s.hash ^ 29), 0, 99) + ";trinket:0.4");
            f.chest(12, 1, 40, -1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 31), 0, 99));
            f.spawner(8, 1, 39, "SILVERFISH");
            f.sign(1, 3, 32, 0, -1, "DO NOT READ\nTHE BOOKS\nTHAT READ\nYOU");
            f.chest(4, 1, 21, 0, 1, Sites.LIBRARY);
            f.chest(9, B1 + 1, 41, 0, -1, Sites.CORRIDOR);
            f.sign(-11, 2, 21, 0, 1, "THE LOWER\nARCHIVE. THE\nWATER READS\nTOO");
        }

        /** The half-flooded lower archive: a gallery round the walls above black water, stacks standing out of it. */
        void lower(int u, int v, int au, boolean wall) {
            if (v < 20) return;
            if (wall) { for (int y = LOW - 1; y < 0; y++) f.masonry(u, y, v); return; }
            boolean gallery = au >= 12 || v <= 22 || v >= 40;
            set(u, LOW - 1, v, BRICK, 0);
            if (gallery) {
                for (int y = LOW; y <= LOW + 4; y++) f.masonry(u, y, v);
                set(u, LOW + 5, v, PLANKS, 5);
                air(u, v, LOW + 6, -1);
                return;
            }
            set(u, LOW, v, f.roll(u, LOW, v, 96) < 0.06 ? SEA_LANTERN : GRAVEL);
            boolean stack = (au == 3 || au == 7) && v >= 25 && v <= 37;
            for (int y = LOW + 1; y <= -1; y++) {
                if (stack && y <= LOW + 6) set(u, y, v, BOOKSHELF);
                else if (y <= LOW + 4) set(u, y, v, Canvas.WATER);
                else set(u, y, v, AIR);
            }
            set(u, 0, v, PLANKS, 5);
            if (u == 0 && v == 30) { f.spawner(u, LOW + 1, v, "ZOMBIE"); }
            if (u == 10 && v == 23) f.chest(u, LOW + 6, v, -1, 0, Sites.DUNGEON);
        }

        // ---------------------------------------------------------------- the star tower and the bindery

        void observatory() {
            int cu = -31, cv = 31;
            for (int u = Math.max(cu - 5, u0); u <= Math.min(cu + 5, u1); u++)
                for (int v = Math.max(cv - 5, v0); v <= Math.min(cv + 5, v1); v++) {
                    double d = Math.hypot(u - cu, v - cv);
                    if (d > 5.2) continue;
                    if (d > 3.8) {
                        for (int y = 1; y <= 24; y++) {
                            boolean door = v < cv && Math.abs(u - cu) <= 1 && y <= 3;
                            boolean window = (y == 10 || y == 22) && (u == cu || v == cv);
                            if (door) set(u, y, v, AIR); else if (window) set(u, y, v, 102); else if (y % 8 == 0) set(u, y, v, BRICK, 3); else ashlar(u, y, v);
                        }
                    } else {
                        air(u, v, 1, 24);
                        if (!(u == cu && v == cv + 3)) set(u, 20, v, PLANKS, 5);
                    }
                    // the broken dome: only its western half still stands
                    for (int y = 25; y <= 29; y++) {
                        double r = 5.2 * Math.sqrt(Math.max(0, 1 - (y - 24) * (y - 24) / 25.0));
                        if (d <= r && d > r - 1.3 && u <= cu) verdigris(u, y, v);
                    }
                }
            if (!hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
            for (int y = 1; y <= 20; y++) f.set(cu, y, cv + 3, LADDER, f.facing(0, -1));
            // the telescope, pointed at the Pleiades
            for (int k = 0; k <= 5; k++) set(cu + 1 + k / 2, 21 + k, cv - 1 - k / 2, k == 5 ? 95 : IRON_BARS, k == 5 ? 3 : 0);
            set(cu + 1, 21, cv - 1, FENCE);
            f.chest(cu - 2, 21, cv, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(s.hash ^ 37), 0, 99));
        }

        void bindery() {
            for (int u = Math.max(24, u0); u <= Math.min(38, u1); u++)
                for (int v = Math.max(24, v0); v <= Math.min(36, v1); v++) {
                    boolean wall = u == 24 || u == 38 || v == 24 || v == 36;
                    if (!wall) { if (f.keep(u, 6, v, 0.4)) set(u, 6, v, SLAB, 0); continue; }
                    boolean door = u == 24 && (v == 30 || v == 31);
                    for (int y = 1; y <= 5; y++) {
                        if (door && y <= 3) { set(u, y, v, AIR); continue; }
                        if (y >= 4 && !f.keep(u, y, v, 0.75)) break;
                        ashlar(u, y, v);
                    }
                }
            if (!hit(25, 25, 37, 35)) return;
            for (int u = 27; u <= 35; u += 4) { set(u, 1, 26, CAULDRON); set(u, 1, 34, 35, 0); set(u, 2, 34, 35, 0); }
            set(36, 1, 30, PLANKS, 1); set(36, 2, 30, PLANKS, 1); set(36, 1, 31, PLANKS, 1);
            f.chest(37, 1, 26, -1, 0, Sites.SMITH);
            f.spawner(34, 1, 30, "SPIDER");
        }
    }
}
