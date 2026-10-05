package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Orrery of Aeons (epoch 6): a great domed hall some forty-five blocks high, its drum ringed with tall blue windows,
 * its copper-green dome ribbed in quartz and painted inside as the night sky, open at the crown. Inside turns the
 * colossal orrery: a sun of glowstone on its axle, stone and quartz orbits (two of them tilted, an armillary), seven
 * worlds on their arms (Yuggoth the black among them) set as the stars stood when the site was planned. Galleries ring
 * the dome at three levels, reached by four stair turrets; the Observation Bridge spans it in front of the sun, where the
 * Keeper of Aeons wakes. Round it: the Hall of Hours and its portico (the Great Clock, a pendulum), the Clockwork Halls
 * (gears on every wall), the Astronomers' Wing (library, chart room, a great telescope through the roof), the Gnomon
 * tower behind, two armillaries in the forecourt, and under the dome the Engine Room, whose great wheels drive the axle.
 */
final class GreatOrrery extends GreatDesign {
    private static final int R = 42, FLOOR = -12, CV = 2;
    private static final double DRUM_IN = 25.5, DRUM_OUT = 27.5;
    private static final int SPRING = 20, G1 = 12, G2 = 20, G3 = 28, BRIDGE = 12, SUN_Y = 22, ENGINE = 18;
    private static final int H2O_GLASS = 95, CONCRETE = 251, TERRACOTTA = 159;
    /** The stair turrets on the diagonals and the ring cell that faces the dome from each. */
    private static final int[][] TURRETS = {{21, 23}, {21, -19}, {-21, -19}, {-21, 23}};
    private static final int[][] RING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] SPOTS = {
        {0, 1, -41}, {5, 1, -31}, {10, 1, 12}, {-12, 1, 14}, {0, G1 + 1, CV + 23}, {-23, G2 + 1, CV}, {21, G3 + 1, CV}, {0, 36, CV},
        {-36, 1, -20}, {-36, 1, 16}, {35, 1, -18}, {35, 1, 2}, {38, 1, 24}, {2, 58, 36}, {4, -11, -9}, {-7, -11, 14},
        {-30, 1, -36}, {30, 1, -36}, {16, 1, -14},
    };
    private static final String[] PACKS = {
        "cult_zealot+vindicator", "ghoul+zombie+husk", "star_spawn+mi_go", "enderman+endermite", "skeleton+stray", "cult_adept+evoker",
        "witch+cult_zealot", "nightgaunt", "vindicator+zombie_villager", "silverfish+cave_spider", "cult_adept+illusioner", "mi_go+witch",
        "skeleton+creeper", "nightgaunt", "shoggoth+slime", "tomb_crawler+spider", "hound+ghoul", "hound+wither_skeleton", "deep_one+zombie",
    };

    /** The site's sky: where each world stands on its orbit, and the hands of the Great Clock. */
    static final class Sky extends Layout {
        final double[] at = new double[7];
        double hour, minute;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Sky l = new Sky();
        for (int i = 0; i < 7; i++) l.at[i] = r.nextDouble() * 2 * Math.PI;
        l.hour = r.nextDouble() * 2 * Math.PI;
        l.minute = r.nextDouble() * 2 * Math.PI;
        l.boss = new int[] {0, BRIDGE + 1, -11};
        for (int i = 0; i < SPOTS.length; i++) l.garrisons.add(g(SPOTS[i][0], SPOTS[i][1], SPOTS[i][2], PACKS[i]));
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int[] b = bounds(f, c, R);
        if (b == null) return;
        Sky sky = (Sky) plan;
        for (int u = b[0]; u <= b[1]; u++)
            for (int v = b[2]; v <= b[3]; v++) column(s, f, u, v);
        for (int k = 0; k < 4; k++) turret(f, b, k);
        gnomon(f, b);
        orrery(f, b, sky);
        engine(f, b);
        clock(f, b, sky);
        gears(f, b);
        astronomers(f, b);
        for (int side = -1; side <= 1; side += 2) armillary(f, b, 25 * side, 4, -34, 4.5);
        tiles(f, b, s.hash);
    }

    // ------------------------------------------------------------------ columns

    private static double outerDome(double dc) { return dc >= DRUM_OUT ? -1 : SPRING + 23 * Math.sqrt(1 - dc * dc / (DRUM_OUT * DRUM_OUT)); }

    private static double innerDome(double dc) { return dc >= DRUM_IN ? SPRING : SPRING + 21 * Math.sqrt(1 - dc * dc / (DRUM_IN * DRUM_IN)); }

    private static void column(Plans.GreatSite s, Frame f, int u, int v) {
        int au = Math.abs(u), av = Math.abs(v);
        int nat = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
        for (int y = Math.max(FLOOR, nat + 1); y < 0; y++) f.rubble(u, y, v);
        f.clear(u, v, 1, Math.max(3, nat + 3));
        double dc = Math.sqrt(u * u + (v - CV) * (v - CV));
        // the forecourt and the ground round the buildings
        if (Math.max(au, av) >= 41) f.set(u, 0, v, STONE, 6);
        else if (f.roll(u, 0, v, 501) < 0.75) f.set(u, 0, v, BRICK, f.roll(u, 0, v, 502) < 0.3 ? 2 : 0);
        else f.paving(u, 0, v);
        if (dc <= ENGINE + 2) undercroft(f, u, v, dc);
        if (dc <= DRUM_OUT) dome(f, u, v, au, dc);
        if (au <= 10 && v >= -39 && v <= -24 && dc > DRUM_OUT) hall(f, u, v, au);
        if (au >= 29 && au <= 41 && v >= -28 && v <= 30) wing(f, u, v, au, u < 0);
        if (au <= 2 && v >= 28 && v <= 33) passage(f, u, v);
        if (au >= 26 && au <= 30 && Math.abs(v - CV) <= 2) { f.set(u, 0, v, STONE, 6); f.clear(u, v, 1, 5); for (int y = 6; y <= 7; y++) f.masonry(u, y, v); }
    }

    /** The drum with its windows, the dome painted inside with the night sky, the floor's zodiac, the galleries, the bridge. */
    private static void dome(Frame f, int u, int v, int au, double dc) {
        double ang = Math.atan2(u, v - CV), deg = (Math.toDegrees(ang) + 360) % 360;
        if (dc > DRUM_IN) {
            boolean pilaster = Math.abs(((deg + 11.25) % 22.5) - 11.25) < 2.2;
            for (int y = 1; y <= SPRING; y++) {
                boolean window = !pilaster && y >= 4 && y <= 10;
                if (pilaster && dc > DRUM_IN + 1) f.set(u, y, v, QUARTZ, y == SPRING ? 1 : 2);
                else if (window) f.set(u, y, v, H2O_GLASS, 11);
                else if (y == 1 || y == SPRING || y == 11) f.set(u, y, v, BRICK, 3);
                else f.masonry(u, y, v);
            }
        } else {
            // the floor: an axle well, rings, a zodiac of twelve houses
            int fl;
            int fm = 0;
            if (dc < 6) { fl = STONE; fm = 6; }
            else if (dc < 7) fl = QUARTZ;
            else if (dc < 14) { fl = BRICK; fm = 0; }
            else if (dc < 15) fl = QUARTZ;
            else if (dc < 21) { int house = (int) (deg / 30); fl = (house & 1) == 0 ? PRISMARINE : TERRACOTTA; fm = (house & 1) == 0 ? 2 : 15; if (Math.abs(deg % 30 - 15) < 1.5 && Math.abs(dc - 18) < 1) { fl = SEA_LANTERN; fm = 0; } }
            else if (dc < 22) fl = OBSIDIAN;
            else { fl = BRICK; fm = 3; }
            if (stairwell(au, v)) fl = AIR;
            f.set(u, 0, v, fl, fm);
            if ((au == 11 || au == 15) && v >= -2 && v <= 10 || v == 10 && au >= 11 && au <= 15) f.set(u, 1, v, WALL, 0);
            // galleries round the drum, the third under the dome
            for (int g : new int[] {G1, G2}) if (dc >= 22) { f.set(u, g, v, BRICK, 0); if (dc < 23) f.set(u, g + 1, v, WALL, 0); }
            double in3 = DRUM_IN * Math.sqrt(1 - Math.pow((G3 - SPRING) / 21.0, 2));
            if (dc >= 20 && dc <= in3 + 0.5) { f.set(u, G3, v, BRICK, 0); if (dc < 21) f.set(u, G3 + 1, v, WALL, 0); }
            // the Observation Bridge across the dome, in front of the sun
            if (Math.abs(v + 11) <= 2 && dc < 22.5) {
                f.set(u, BRIDGE, v, Math.abs(v + 11) == 0 ? QUARTZ : BRICK, Math.abs(v + 11) == 0 ? 0 : 3);
                if (Math.abs(v + 11) == 2) f.set(u, BRIDGE + 1, v, WALL, 0);
            }
        }
        // the dome: copper-green outside with quartz ribs, the night sky inside, an eye at the crown
        if (dc > 3) {
            double ho = outerDome(dc), hi = innerDome(dc);
            boolean rib = Math.abs(((deg + 11.25) % 22.5) - 11.25) < 1.6;
            for (int y = Math.max(SPRING + 1, (int) Math.floor(hi) + 1); y <= ho; y++) {
                boolean inner = y <= hi + 1.2 && dc < DRUM_IN;
                if (inner) f.set(u, y, v, f.roll(u, y, v, 503) < 0.012 ? SEA_LANTERN : TERRACOTTA, f.roll(u, y, v, 503) < 0.012 ? 0 : 11);
                else if (rib) f.set(u, y, v, QUARTZ, 0);
                else f.set(u, y, v, PRISMARINE, f.roll(u, y, v, 504) < 0.2 ? 1 : 0);
            }
            if (dc <= 4.2) f.set(u, (int) ho, v, QUARTZ, 1);
        }
        // the gates through the drum: from the Hall of Hours, the wings and the Gnomon passage
        if (dc > DRUM_IN - 0.5) {
            if (au <= 3 && v < 0) f.clear(u, v, 1, au <= 2 ? 5 : 4);
            if (Math.abs(v - CV) <= 2 && au >= 20) f.clear(u, v, 1, 5);
            if (au <= 2 && v > 0) f.clear(u, v, 1, 4);
        }
    }

    /** The Engine Room under the dome: its walls, floor and the two stairs down from the dome floor. */
    private static void undercroft(Frame f, int u, int v, double dc) {
        for (int y = FLOOR; y <= -1; y++) f.masonry(u, y, v);
        if (dc > ENGINE) return;
        f.set(u, FLOOR, v, (u + v & 1) == 0 ? STONE : BRICK, (u + v & 1) == 0 ? 6 : 0);
        f.clear(u, v, FLOOR + 1, -3);
        if (f.roll(u, 1, v, 505) < 0.015) f.set(u, FLOOR, v, MAGMA);
        int au = Math.abs(u);
        if (au >= 12 && au <= 14 && v >= -3 && v <= 9) {
            int i = v + 3, y = -i;
            for (int k = FLOOR + 1; k < y; k++) f.masonry(u, k, v);
            f.set(u, y, v, BRICK_STAIRS, f.stairs(0, -1, false));
            f.clear(u, v, y + 1, 0);
        }
    }

    private static boolean stairwell(int au, int v) { return au >= 12 && au <= 14 && v >= -2 && v <= 9; }

    /** The Hall of Hours and its portico. */
    private static void hall(Frame f, int u, int v, int au) {
        if (v <= -37) {                                  // the portico: steps, columns, a pediment
            f.set(u, 0, v, QUARTZ, 0);
            if (v == -38 && (au + 1) % 3 == 0) { for (int y = 1; y <= 13; y++) f.set(u, y, v, QUARTZ, y == 13 ? 1 : 2); }
            if (v >= -38) { for (int y = 14; y <= 15 + (10 - au) / 2; y++) if (y == 14) f.set(u, y, v, BRICK, 3); else f.masonry(u, y, v); }
            return;
        }
        boolean wall = au >= 9 || v == -36;
        f.set(u, 0, v, (u + v & 1) == 0 ? QUARTZ : STONE, (u + v & 1) == 0 ? 0 : 6);
        for (int y = 1; y <= 16; y++) {
            if (wall) {
                if (v == -36 && au <= 2 && y <= 6) { f.set(u, y, v, AIR); continue; }
                if (au >= 9 && y >= 4 && y <= 8 && Math.floorMod(v, 4) == 1) { f.set(u, y, v, BARS); continue; }
                if (y == 16 || y == 9) f.glyph(u, y, v); else f.masonry(u, y, v);
            } else if (y == 16) f.set(u, y, v, BRICK, 0);
        }
        if (!wall) for (int y = 17; y <= 17 + (9 - au) / 3; y++) f.set(u, y, v, PRISMARINE, 2);
        // the pendulum of the Hall of Hours
        if (u == 0 && v == -31) { for (int y = 6; y <= 15; y++) f.set(u, y, v, FENCE); f.set(u, 5, v, QUARTZ, 1); f.set(u, 4, v, OBSIDIAN); }
    }

    /** The two wings: the Clockwork Halls (left) and the Astronomers' Wing (right). */
    private static void wing(Frame f, int u, int v, int au, boolean left) {
        boolean wall = au <= 30 || au >= 40 || v <= -27 || v >= 29;
        int top = 13;
        f.set(u, 0, v, left ? ((u + v & 1) == 0 ? STONE : BRICK) : ((u + v & 1) == 0 ? BRICK : STONE), left ? ((u + v & 1) == 0 ? 6 : 0) : ((u + v & 1) == 0 ? 0 : 6));
        for (int y = 1; y <= top; y++) {
            if (wall) {
                if (au <= 30 && Math.abs(v - CV) <= 2 && y <= 5) { f.set(u, y, v, AIR); continue; }
                if (au >= 40 && y >= 4 && y <= 7 && Math.floorMod(v, 6) == 3) { f.set(u, y, v, BARS); continue; }
                if (y == top || y == 7) f.glyph(u, y, v); else f.masonry(u, y, v);
            } else if (y == top) f.set(u, y, v, PRISMARINE, 2);
        }
        if (!left && !wall) {                            // the Astronomers' rooms: partitions with doors
            if (v == -7 || v == 11) for (int y = 1; y <= top - 1; y++) if (!(Math.abs(u - 35) <= 1 && y <= 3)) f.masonry(u, y, v);
        }
    }

    private static void passage(Frame f, int u, int v) {
        f.set(u, 0, v, STONE, 6);
        for (int y = 1; y <= 6; y++) if (Math.abs(u) == 2 || y == 6) f.masonry(u, y, v); else f.set(u, y, v, AIR);
    }

    // ------------------------------------------------------------------ features

    /** A stair turret on a diagonal: a newel stair from the ground to the third gallery, doors onto every gallery. */
    private static void turret(Frame f, int[] b, int k) {
        int cu = TURRETS[k][0], cv = TURRETS[k][1];
        if (!hit(b, cu - 8, cv - 8, cu + 8, cv + 8)) return;
        int du = -Integer.signum(cu), dv = -Integer.signum(cv - CV), face = 0;
        for (int i = 0; i < 8; i++) if (RING[i][0] == du && RING[i][1] == dv) face = i;
        int phase = Math.floorMod(G1 - face, 8);
        for (int a = -2; a <= 2; a++)
            for (int c = -2; c <= 2; c++) {
                int u = cu + a, v = cv + c, m = Math.max(Math.abs(a), Math.abs(c));
                f.set(u, 0, v, BRICK, 0);
                for (int y = 1; y <= 34; y++) {
                    if (m == 2) { if (y % 8 == 0) f.glyph(u, y, v); else f.masonry(u, y, v); }
                    else if (m == 0 && y <= G3 + 1) f.set(u, y, v, BRICK, Math.floorMod(y, 6) == 0 ? 3 : 0);
                    else f.set(u, y, v, AIR);
                }
                f.set(u, 35, v, PRISMARINE, 2);
                if (m <= 1) f.set(u, 36, v, PRISMARINE, 2);
                if (m == 2 && (a + c & 1) == 0) f.set(u, 35, v, BRICK, 3);
            }
        f.set(cu, 37, cv, SEA_LANTERN);
        for (int h = 1; h <= G3; h++) {
            int cell = Math.floorMod(h - phase, 8);
            int[] p = RING[cell], n = RING[(cell + 1) % 8];
            f.set(cu + p[0], h, cv + p[1], BRICK_STAIRS, f.stairs(n[0] - p[0], n[1] - p[1], false));
        }
        // doorways toward the dome at the ground and at every gallery, and out to the court at the ground
        for (int g : new int[] {0, G1, G2, G3})
            for (int t = 2; t <= 4; t++) {
                f.clear(cu + du * t, cv + dv * t, g + 1, g + 3);
                f.clear(cu + du * t - du, cv + dv * t, g + 1, g + 3);
            }
        f.clear(cu - du * 2, cv - dv * 2, 1, 3);
        f.clear(cu - du * 2, cv, 1, 3);
    }

    /** The Gnomon: a slender tower behind the dome, a newel stair inside, a platform and a spike of obsidian on top. */
    private static void gnomon(Frame f, int[] b) {
        if (!hit(b, -4, 32, 4, 40)) return;
        for (int a = -3; a <= 3; a++)
            for (int c = 33; c <= 39; c++) {
                int m = Math.max(Math.abs(a), Math.abs(c - 36));
                f.set(a, 0, c, BRICK, 0);
                for (int y = 1; y <= 56; y++) {
                    if (m == 3) { if (c == 33 && Math.abs(a) <= 1 && y <= 3) { f.set(a, y, c, AIR); continue; } if (y % 7 == 0) f.glyph(a, y, c); else if (Math.abs(a) == 3 && Math.abs(c - 36) == 3) f.set(a, y, c, QUARTZ, 2); else f.masonry(a, y, c); }
                    else if (m == 0) f.set(a, y, c, BRICK, 0);
                    else f.set(a, y, c, AIR);
                }
                f.set(a, 57, c, m <= 1 ? AIR : BRICK, m <= 1 ? 0 : 3);
                if (m == 3 && (a + c & 1) == 0) f.set(a, 58, c, BRICK, 3);
            }
        for (int h = 1; h <= 56; h++) {
            int[] p = RING[Math.floorMod(h, 8)], n = RING[Math.floorMod(h + 1, 8)];
            f.set(p[0], h, 36 + p[1], BRICK_STAIRS, f.stairs(n[0] - p[0], n[1] - p[1], false));
        }
        for (int y = 57; y <= 66; y++) f.set(0, y, 36, y >= 65 ? SEA_LANTERN : OBSIDIAN);
    }

    /** The orrery: the axle and the sun, the orbits, the armillary rings and the seven worlds on their arms. */
    private static void orrery(Frame f, int[] b, Sky sky) {
        if (!hit(b, -24, CV - 24, 24, CV + 24)) return;
        for (int y = FLOOR + 1; y <= SUN_Y - 4; y++)
            for (int a = -1; a <= 1; a++)
                for (int c = -1; c <= 1; c++) f.set(a, y, CV + c, a == 0 && c == 0 ? OBSIDIAN : QUARTZ, a == 0 && c == 0 ? 0 : 2);
        for (int a = -2; a <= 2; a++) for (int c = -2; c <= 2; c++) if (a * a + c * c <= 5) { f.set(a, 6, CV + c, BRICK, 3); f.set(a, 1, CV + c, BRICK, 3); }
        sphere(f, b, 0, SUN_Y, CV, 4.6, GLOWSTONE, 0, SEA_LANTERN, 0.15);
        // orbits: (radius, height, block) - the inner of quartz, the outer of stone
        double[][] orbits = {{8, SUN_Y, 0}, {11.5, 18, 1}, {15, 26, 0}, {18.5, 15, 1}, {17, 33, 0}};
        for (double[] o : orbits) ring(f, b, o[0], o[1], 0, 0, o[2] == 0 ? QUARTZ : BRICK, o[2] == 0 ? 0 : 3);
        ring(f, b, 13, SUN_Y, 0.55, 0.0, OBSIDIAN, 0);
        ring(f, b, 13, SUN_Y, 0.55, Math.PI / 2, OBSIDIAN, 0);
        // the worlds: orbit, size, body, meta (Mercury, Venus, Earth, Mars, Jupiter, Saturn, Yuggoth)
        double[][] worlds = {{8, 1.2, CONCRETE, 8}, {11.5, 1.6, CONCRETE, 4}, {15, 2.1, CONCRETE, 11}, {11.5, 1.6, TERRACOTTA, 14},
            {18.5, 3.1, TERRACOTTA, 1}, {17, 2.6, TERRACOTTA, 4}, {17, 2.1, OBSIDIAN, 0}};
        int[] heights = {SUN_Y, 18, 26, 18, 15, 33, 33};
        for (int i = 0; i < 7; i++) {
            double a = sky.at[i] + (i == 3 ? Math.PI : 0), rr = worlds[i][0];
            int pu = (int) Math.round(rr * Math.sin(a)), pv = CV + (int) Math.round(rr * Math.cos(a)), py = heights[i];
            if (i == 6) { pu = (int) Math.round(rr * Math.sin(sky.at[5] + Math.PI)); pv = CV + (int) Math.round(rr * Math.cos(sky.at[5] + Math.PI)); }
            int rad = (int) Math.ceil(worlds[i][1]);
            if (Math.abs(pv + 11) <= rad + 3 && py - rad <= BRIDGE + 3) py = BRIDGE + 4 + rad;
            arm(f, b, py, pu, pv);
            int id = (int) worlds[i][2], meta = (int) worlds[i][3];
            if (i == 4) sphereBands(f, b, pu, py, pv, worlds[i][1]);
            else sphere(f, b, pu, py, pv, worlds[i][1], id, meta, i == 2 ? TERRACOTTA : i == 6 ? SEA_LANTERN : id, i == 2 ? 0.3 : i == 6 ? 0.08 : 0);
            if (i == 2) { int mu = pu + (int) Math.round(3.5 * Math.sin(a + 1)), mv = pv + (int) Math.round(3.5 * Math.cos(a + 1)); f.set(mu, py + 1, mv, CONCRETE, 0); }
            if (i == 5) ring(f, b, 4.5, py, 0, 0, QUARTZ, 0, pu, pv);
        }
    }

    /** An arm from the axle out to a world at height y. */
    private static void arm(Frame f, int[] b, int y, int pu, int pv) {
        double len = Math.sqrt(pu * pu + (pv - CV) * (pv - CV));
        int n = (int) Math.ceil(len * 1.5);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            int u = (int) Math.round(pu * t), v = CV + (int) Math.round((pv - CV) * t);
            if (Math.sqrt(u * u + (v - CV) * (v - CV)) < 4.8 && Math.abs(y - SUN_Y) < 5) continue;
            f.set(u, y, v, BRICK, 0);
        }
        // a strut up from the sun when the arm runs above it
        if (y > SUN_Y + 4) for (int yy = SUN_Y + 4; yy <= y; yy++) f.set(0, yy, CV, OBSIDIAN);
    }

    /** A horizontal ring (tilt 0) or a ring tilted by {@code tilt} radians about the axis at angle {@code turn}, round the sun. */
    private static void ring(Frame f, int[] b, double rad, double cy, double tilt, double turn, int id, int meta) { ring(f, b, rad, cy, tilt, turn, id, meta, 0, CV); }

    private static void ring(Frame f, int[] b, double rad, double cy, double tilt, double turn, int id, int meta, int cu, int cv) {
        if (!hit(b, cu - (int) rad - 1, cv - (int) rad - 1, cu + (int) rad + 1, cv + (int) rad + 1)) return;
        int n = (int) Math.ceil(2 * Math.PI * rad * 2);
        double ct = Math.cos(tilt), st = Math.sin(tilt), cr = Math.cos(turn), sr = Math.sin(turn);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n, x = rad * Math.cos(a), z = rad * Math.sin(a);
            double y = z * st;
            z = z * ct;
            double u = x * cr - z * sr, v = x * sr + z * cr;
            int iu = cu + (int) Math.round(u), iv = cv + (int) Math.round(v), iy = (int) Math.round(cy + y);
            if (Math.abs(iv + 11) <= 2 && (iy == BRIDGE + 1 || iy == BRIDGE + 2)) continue;     // the bridge stays walkable
            f.set(iu, iy, iv, id, meta);
        }
    }

    private static void sphere(Frame f, int[] b, int cu, int cy, int cv, double rad, int id, int meta, int spot, double spots) {
        int r = (int) Math.ceil(rad);
        if (!hit(b, cu - r, cv - r, cu + r, cv + r)) return;
        for (int a = -r; a <= r; a++)
            for (int y = -r; y <= r; y++)
                for (int c = -r; c <= r; c++) {
                    if (a * a + y * y + c * c > rad * rad) continue;
                    boolean sp = spots > 0 && f.roll(cu + a, cy + y, cv + c, 506) < spots;
                    f.set(cu + a, cy + y, cv + c, sp ? spot : id, sp ? (spot == TERRACOTTA ? 13 : 0) : meta);
                }
    }

    /** Jupiter: bands of orange, white and brown terracotta, one red eye. */
    private static void sphereBands(Frame f, int[] b, int cu, int cy, int cv, double rad) {
        int r = (int) Math.ceil(rad);
        if (!hit(b, cu - r, cv - r, cu + r, cv + r)) return;
        int[] bands = {1, 0, 12, 1, 0, 1, 12};
        for (int a = -r; a <= r; a++)
            for (int y = -r; y <= r; y++)
                for (int c = -r; c <= r; c++) {
                    if (a * a + y * y + c * c > rad * rad) continue;
                    f.set(cu + a, cy + y, cv + c, TERRACOTTA, bands[Math.floorMod(y + 3, bands.length)]);
                }
        f.set(cu + r, cy - 1, cv, TERRACOTTA, 14);
    }

    /** The Engine Room's great wheels and axles. */
    private static void engine(Frame f, int[] b) {
        if (!hit(b, -ENGINE, CV - ENGINE, ENGINE, CV + ENGINE)) return;
        wheel(f, b, 0, -7, CV, 8.5, 18);
        wheel(f, b, 0, -6, CV + 13, 4.5, 10);
        wheel(f, b, 0, -6, CV - 13, 4.5, 10);
        for (int[] ax : new int[][] {{0, CV + 13}, {0, CV - 13}}) for (int y = FLOOR + 1; y <= -3; y++) f.set(ax[0], y, ax[1], y == FLOOR + 1 ? BRICK : QUARTZ, y == FLOOR + 1 ? 3 : 2);
    }

    /** A horizontal toothed wheel: rim, spokes and teeth. */
    private static void wheel(Frame f, int[] b, int cu, int y, int cv, double rad, int teeth) {
        int r = (int) Math.ceil(rad + 1.5);
        if (!hit(b, cu - r, cv - r, cu + r, cv + r)) return;
        for (int a = -r; a <= r; a++)
            for (int c = -r; c <= r; c++) {
                double d = Math.sqrt(a * a + c * c), ang = Math.atan2(a, c);
                boolean rim = d >= rad - 1.2 && d <= rad;
                boolean tooth = d > rad && d <= rad + 1.3 && Math.floorMod((int) Math.floor((ang / (2 * Math.PI) + 1) * teeth * 2), 2) == 0;
                boolean spoke = d < rad && (Math.abs(a) <= 0 || Math.abs(c) <= 0 || Math.abs(Math.abs(a) - Math.abs(c)) == 0 && rad > 6);
                boolean hub = d <= 1.6;
                if (rim || tooth) f.set(cu + a, y, cv + c, tooth ? SLAB : STONE, tooth ? 5 : 6);
                else if (hub) f.set(cu + a, y, cv + c, BRICK, 3);
                else if (spoke) f.set(cu + a, y, cv + c, STONE, 0);
            }
    }

    /** The Great Clock over the gate in the Hall of Hours: a face of quartz, hour stones of obsidian, two hands. */
    private static void clock(Frame f, int[] b, Sky sky) {
        if (!hit(b, -9, -27, 9, -26)) return;
        int cy = 11, v = -27;
        for (int a = -5; a <= 5; a++)
            for (int y = cy - 5; y <= cy + 5; y++) {
                double d = Math.sqrt(a * a + (y - cy) * (y - cy));
                if (d > 4.9) continue;
                double hr = Math.atan2(a, y - cy) / (Math.PI / 6);
                if (d > 4) f.set(a, y, v, Math.abs(hr - Math.round(hr)) < 0.25 ? OBSIDIAN : QUARTZ, 0);
                else f.set(a, y, v, QUARTZ, 1);
            }
        for (int t = 1; t <= 4; t++) {
            f.set((int) Math.round(t * Math.sin(sky.minute)), cy + (int) Math.round(t * Math.cos(sky.minute)), v, OBSIDIAN);
            if (t <= 2) f.set((int) Math.round(t * Math.sin(sky.hour)), cy + (int) Math.round(t * Math.cos(sky.hour)), v, CLAY, 15);
        }
        f.set(0, cy, v, SEA_LANTERN);
    }

    /** The Clockwork Halls: wheels on both long walls, meshing, of andesite, stone and slabs. */
    private static void gears(Frame f, int[] b) {
        if (!hit(b, -41, -28, -29, 30)) return;
        int[][] g = {{-22, 6, 5}, {-13, 4, 3}, {-5, 6, 5}, {5, 5, 4}, {13, 4, 3}, {21, 6, 5}};
        for (int[] x : g) { vgear(f, b, -39, x[1], x[0], x[2]); vgear(f, b, -32, x[1] + 1, x[0] + 4, Math.max(3, x[2] - 1)); }
    }

    /** A vertical gear on the wall plane u = wu, its centre at (y, v). */
    private static void vgear(Frame f, int[] b, int wu, int cy, int cv, int rad) {
        if (!hit(b, wu, cv - rad - 2, wu, cv + rad + 2)) return;
        for (int c = -rad - 1; c <= rad + 1; c++)
            for (int y = -rad - 1; y <= rad + 1; y++) {
                int yy = cy + y;
                if (yy < 1 || yy > 12) continue;
                double d = Math.sqrt(c * c + y * y), ang = Math.atan2(c, y);
                boolean rim = d >= rad - 1 && d <= rad;
                boolean tooth = d > rad && d <= rad + 1.2 && Math.floorMod((int) Math.floor((ang / (2 * Math.PI) + 1) * rad * 4), 2) == 0;
                boolean spoke = d < rad - 1 && (c == 0 || y == 0);
                if (rim) f.set(wu, yy, cv + c, STONE, 5);
                else if (tooth) f.set(wu, yy, cv + c, BRICK, 3);
                else if (d <= 1) f.set(wu, yy, cv + c, OBSIDIAN);
                else if (spoke) f.set(wu, yy, cv + c, STONE, 6);
            }
    }

    /** The Astronomers' Wing: the library, the chart room's floor of stars, the telescope through the roof. */
    private static void astronomers(Frame f, int[] b) {
        if (!hit(b, 29, -28, 41, 30)) return;
        for (int v = -25; v <= -9; v++) {
            if (Math.floorMod(v, 4) == 0) continue;
            for (int y = 1; y <= 5; y++) { f.set(39, y, v, BOOKSHELF); if (v % 3 != 0) f.set(32, y, v, BOOKSHELF); }
        }
        for (int u = 33; u <= 38; u++)
            for (int v = -5; v <= 9; v++) {
                double r = f.roll(u, 0, v, 507);
                f.set(u, 0, v, r < 0.08 ? SEA_LANTERN : r < 0.2 ? QUARTZ : TERRACOTTA, r < 0.08 ? 0 : r < 0.2 ? 0 : 11);
            }
        for (int u = 34; u <= 36; u++) { f.set(u, 1, 6, FENCE); f.set(u, 2, 6, SLAB, 5); }
        // the telescope: a tube of iron and glass rising through the roof toward the zenith
        for (int t = 0; t <= 16; t++) {
            int tu = 35 - t / 3, tv = 20 - t / 2, ty = 2 + t;
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) if (a != 0 || c != 0) f.set(tu + a, ty, tv + c, t % 4 == 0 ? BRICK : BARS, t % 4 == 0 ? 3 : 0);
            f.set(tu, ty, tv, t == 16 ? H2O_GLASS : AIR, t == 16 ? 3 : 0);
        }
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) f.set(35 + a, 1, 20 + c, BRICK, 3);
    }

    /** A little armillary sphere on a pedestal in the forecourt. */
    private static void armillary(Frame f, int[] b, int cu, int y0, int cv, double rad) {
        if (!hit(b, cu - 6, cv - 6, cu + 6, cv + 6)) return;
        for (int y = 1; y <= y0; y++) f.set(cu, y, cv, y == y0 ? BRICK : QUARTZ, y == y0 ? 3 : 2);
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) f.set(cu + a, 1, cv + c, BRICK, 3);
        ring(f, b, rad, y0 + rad + 1, 0, 0, QUARTZ, 0, cu, cv);
        ring(f, b, rad, y0 + rad + 1, Math.PI / 2, 0, BRICK, 3, cu, cv);
        ring(f, b, rad, y0 + rad + 1, Math.PI / 2, Math.PI / 2, BRICK, 3, cu, cv);
        f.set(cu, y0 + (int) rad + 1, cv, SEA_LANTERN);
    }

    private static void tiles(Frame f, int[] b, long h) {
        int lore = Hash.range(h, 0, 99);
        // the Keeper's hoard at the ends of the bridge
        f.chest(-20, BRIDGE + 1, -11, 1, 0, "minecraft:chests/end_city_treasure", "lore:" + lore + ";trinket:0.6");
        f.chest(20, BRIDGE + 1, -11, -1, 0, "minecraft:chests/woodland_mansion", "trinket:0.4");
        // the galleries
        f.chest(0, G2 + 1, CV + 24, 0, -1, Sites.LIBRARY, "lore:" + (lore + 3));
        f.chest(-22, G3 + 1, CV + 3, 1, 0, Sites.DESERT, null);
        f.chest(24, G1 + 1, CV + 2, -1, 0, Sites.JUNGLE, null);
        // the halls
        f.chest(-7, 1, -34, 1, 0, Sites.CORRIDOR, null);
        f.chest(-38, 1, -26, 1, 0, Sites.SMITH, null);
        f.chest(-38, 1, 28, 1, 0, "minecraft:chests/abandoned_mineshaft", null);
        f.chest(38, 1, -26, -1, 0, Sites.LIBRARY, "lore:" + (lore + 17));
        f.chest(38, 1, 8, -1, 0, "minecraft:chests/stronghold_crossing", "trinket:0.25");
        f.chest(33, 1, 27, 1, 0, "minecraft:chests/igloo_chest", null);
        f.chest(-1, 58, 34, 0, 1, Sites.DUNGEON, null);
        f.chest(0, FLOOR + 1, CV + 16, 0, -1, "minecraft:chests/nether_bridge", null);
        f.chest(0, FLOOR + 1, CV - 16, 0, 1, Sites.DUNGEON, "lore:" + (lore + 29));
        f.spawner(-35, 1, 0, "ZOMBIE");
        f.spawner(36, 1, 14, "SKELETON");
        f.spawner(10, FLOOR + 1, CV, "SILVERFISH");
        f.sign(4, 3, -40, 0, -1, "THE ORRERY\nOF AEONS");
        f.sign(-4, 3, -40, 0, -1, "THE STARS\nARE RIGHT\nTONIGHT");
        for (int side = -1; side <= 1; side += 2) for (int y = 1; y <= 3; y++) f.set(4 * side, y, -39, y == 3 ? BRICK : QUARTZ, y == 3 ? 3 : 2);
        f.set(0, BRIDGE + 2, -13, WALL, 0);
        f.sign(0, BRIDGE + 2, -14, 0, -1, "TIME RUNS\nBACKWARDS\nHERE");
        f.sign(-34, 3, -27, 0, 1, "THE CLOCKWORK\nHALLS");
        f.sign(34, 3, -27, 0, 1, "THE\nASTRONOMERS");
        f.dispenser(0, 0, -30, 0, 0);
        f.set(0, 1, -30, PLATE, 0);
        f.dispenser(-35, 0, 10, 0, 0);
        f.set(-35, 1, 10, PLATE, 0);
    }

    // ------------------------------------------------------------------ geometry

    private static int[] bounds(Frame f, Canvas c, int r) {
        int ax = c.x0 - f.ox, az = c.z0 - f.oz, bx = ax + 15, bz = az + 15, u0, u1, v0, v1;
        switch (f.rot) {
            case 1: u0 = az; u1 = bz; v0 = -bx; v1 = -ax; break;
            case 2: u0 = -bx; u1 = -ax; v0 = -bz; v1 = -az; break;
            case 3: u0 = -bz; u1 = -az; v0 = ax; v1 = bx; break;
            default: u0 = ax; u1 = bx; v0 = az; v1 = bz; break;
        }
        u0 = Math.max(u0, -r); u1 = Math.min(u1, r); v0 = Math.max(v0, -r); v1 = Math.min(v1, r);
        return u0 > u1 || v0 > v1 ? null : new int[] {u0, u1, v0, v1};
    }

    private static boolean hit(int[] b, int u0, int v0, int u1, int v1) { return u1 >= b[0] && u0 <= b[1] && v1 >= b[2] && v0 <= b[3]; }
}
