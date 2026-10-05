package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Weeping Cistern (epoch 6): a podium of brick and marble some eighty blocks square, its plaza a field of broken
 * columns, weeping fountains and light wells, crowned by the Pavilion of Tears (a ring of marble columns under a green
 * dome, four veiled mourners weeping into an oculus). Under the plaza lies the cistern itself, flooded six deep from
 * base - 11 to base - 6: a forest of 128 columns under brick vaults, a grand stair down from the propylon, walkways and
 * bridges over the water, a dry processional ledge along the walls with burial galleries in them, two Medusa heads
 * under the columns at the back, and at its heart, under the oculus, the Gorgon's Pool (21 x 21, no columns), where the
 * Gorgon of the Cistern (an elder guardian) waits with her guardians in the dark water.
 */
final class GreatCistern extends GreatDesign {
    private static final int R = 44, FLOOR = -12, WATER_TOP = -6, WALK = -6, LEDGE = -5, PLAZA = 4, POOL = 10, OCULUS = 6;
    private static final int H2O = 9, FALLING = 8, QUARTZ_STAIRS = 156, BRICK_BLOCK = 45, TERRACOTTA = 159;
    /** The pavilion's sixteen columns. */
    private static final int[][] RING = new int[16][];
    /** Garrison points (u, y, v) and their packs. */
    private static final int[][] SPOTS = {
        {8, 5, -30}, {-26, 5, 0}, {26, 5, 0}, {0, 5, 30}, {0, 7, 10},
        {0, WALK + 1, -20}, {12, WALK + 1, 12}, {-12, WALK + 1, -12}, {22, WALK + 1, 0}, {-22, WALK + 1, 0}, {0, WALK + 1, 22},
        {34, LEDGE + 1, -20}, {-34, LEDGE + 1, 20}, {20, LEDGE + 1, 34}, {-18, WALK + 1, 22},
        {20, -9, -20}, {-20, -9, -20}, {24, -9, 18}, {-26, -9, 8}, {20, -1, 20}, {-14, -1, -26},
    };
    private static final String[] PACKS = {
        "cult_zealot+vindicator", "zombie+husk+zombie_villager", "skeleton+stray", "ghoul+creeper", "!cult_adept+witch+evoker",
        "deep_one+zombie", "deep_one+slime", "tomb_crawler+cave_spider", "ghoul+hound+silverfish", "shoggoth+slime", "mi_go+enderman",
        "skeleton+wither_skeleton", "spider+endermite", "star_spawn+illusioner", "cult_adept+witch",
        "guardian", "guardian+guardian", "guardian", "guardian+guardian", "nightgaunt", "nightgaunt",
    };

    static {
        for (int k = 0; k < 16; k++) {
            double a = Math.toRadians(22.5 * k + 11.25);
            RING[k] = new int[] {(int) Math.round(13 * Math.sin(a)), (int) Math.round(13 * Math.cos(a))};
        }
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Layout l = new Layout();
        l.boss = new int[] {0, FLOOR + 1, 0};
        for (int i = 0; i < SPOTS.length; i++) {
            String pack = PACKS[i];
            if (i == 1 + r.nextInt(3) && !pack.startsWith("!")) pack = "!" + pack;
            l.garrisons.add(g(SPOTS[i][0], SPOTS[i][1], SPOTS[i][2], pack));
        }
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int[] b = bounds(f, c, R);
        if (b == null) return;
        for (int u = b[0]; u <= b[1]; u++)
            for (int v = b[2]; v <= b[3]; v++) column(s, f, u, v);
        medusa(f, b, -21, -8, 27, new int[] {0, 0, -1}, new int[] {0, -1, 0});      // upside down
        medusa(f, b, -15, -8, 27, new int[] {0, 0, -1}, new int[] {1, 0, 0});       // on her side
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) {
                mourner(f, b, 6 * su, 7, 6 * sv, -su, -sv, 1, 4 * su, 4 * sv, WATER_TOP + 1);
                mourner(f, b, 24 * su, PLAZA, 24 * sv, -su, -sv, 2, 21 * su, 22 * sv, PLAZA + 1);
            }
        propylon(f, b);
        tiles(f, b, s.hash);
    }

    // ------------------------------------------------------------------ columns

    private static void column(Plans.GreatSite s, Frame f, int u, int v) {
        int au = Math.abs(u), av = Math.abs(v), m = Math.max(au, av);
        int nat = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
        for (int y = Math.max(FLOOR, nat + 1); y < 0; y++) f.rubble(u, y, v);
        f.clear(u, v, 1, Math.max(3, nat + 3));
        if (m >= 43) { f.paving(u, 0, v); return; }
        if (m >= 40) {                                   // the podium's steps
            int top = PLAZA - (m - 39);
            for (int y = 0; y < top; y++) f.masonry(u, y, v);
            int[] d = au >= av ? new int[] {-Integer.signum(u), 0} : new int[] {0, -Integer.signum(v)};
            f.set(u, top, v, BRICK_STAIRS, f.stairs(d[0], d[1], false));
            return;
        }
        double r = Math.sqrt(u * u + v * v);
        cistern(f, u, v, au, av, m, r);
        plaza(f, u, v, au, av, m, r);
        if (r <= 16.5) pavilion(f, u, v, r);
    }

    /** Distance (0..3) from a column line of the forest (lines at 3 mod 6). */
    private static int line(int x) { int a = Math.floorMod(x - 3, 6); return Math.min(a, 6 - a); }

    /** The top of the air under the vaults: groined bays on the column grid, a high dome over the pool. */
    private static int vault(int m, int u, int v) {
        if (m <= POOL + 1) return 2;
        int mn = Math.min(line(u), line(v));
        return mn == 0 ? 0 : mn == 1 ? 1 : 2;
    }

    private static boolean columnAt(int u, int v, int m) {
        if (line(u) != 0 || line(v) != 0 || m <= POOL + 1 || m > 35) return false;
        return !(Math.abs(u) <= 3 && v >= -37 && v <= -26);                  // the grand stair
    }

    private static boolean walkway(int u, int v, int au, int av, int m) {
        if (m >= 11 && m <= 13) return true;                                 // round the pool
        if (au <= 2 && v >= -27 && v <= -14) return true;                    // the processional way from the stair
        if (av <= 1 && au >= 14 && au <= 31) return true;                    // across, to the side ledges
        if (au <= 1 && v >= 14 && v <= 31) return true;                      // to the back ledge
        return v >= 22 && v <= 23 && u >= -26 && u <= -2;                    // the Medusa walk
    }

    private static void cistern(Frame f, int u, int v, int au, int av, int m, double r) {
        boolean stair = au <= 2 && v >= -36 && v <= -27;
        if (m >= 36) {                                   // the walls, with burial galleries above the ledge
            for (int y = FLOOR; y <= 3; y++) if (f.roll(u, y, v, 401) < 0.75) f.set(u, y, v, BRICK_BLOCK); else f.masonry(u, y, v);
            if (m <= 37 && niche(u, v, au, av) >= 0) { f.clear(u, v, -4, -1); f.set(u, LEDGE, v, STONE, 6); }
            if (stair) step(f, u, v);
            return;
        }
        f.set(u, FLOOR, v, f.roll(u, 0, v, 402) < 0.6 ? STONE : BRICK, f.roll(u, 0, v, 402) < 0.6 ? 6 : 2);
        int top = vault(m, u, v);
        boolean ledge = m >= 32;
        if (ledge) { for (int y = FLOOR + 1; y < LEDGE; y++) f.masonry(u, y, v); f.set(u, LEDGE, v, STONE, 6); }
        else for (int y = FLOOR + 1; y <= WATER_TOP; y++) f.set(u, y, v, H2O);
        f.clear(u, v, ledge ? LEDGE + 1 : WATER_TOP + 1, top);
        for (int y = top + 1; y <= 3; y++) f.set(u, y, v, BRICK_BLOCK);
        if (stair) { step(f, u, v); return; }
        if (columnAt(u, v, m)) {
            boolean weeping = (u + 3) % 18 == 0 && (v + 3) % 12 == 0;
            for (int y = FLOOR + 1; y <= 0; y++) {
                if (y == FLOOR + 1 || y == 0) f.set(u, y, v, weeping ? PRISMARINE : BRICK, weeping ? 1 : 3);
                else if (weeping) { if (Math.floorMod(y, 3) == 0) f.glyph(u, y, v); else f.set(u, y, v, PRISMARINE, 2); }
                else f.set(u, y, v, QUARTZ, 2);
            }
            return;
        }
        if (!ledge && walkway(u, v, au, av, m)) {
            f.set(u, WALK, v, (u + v & 1) == 0 ? STONE : BRICK, (u + v & 1) == 0 ? 6 : 0);
            if (au == 2 && v >= -26 && v <= -15 && Math.floorMod(v, 6) == 0) f.set(u, WALK + 1, v, TORCH, 5);
        }
        if (m <= POOL) {                                 // the Gorgon's Pool: lights and embers on its floor
            if (au == 8 && av == 8) f.set(u, FLOOR, v, SEA_LANTERN);
            else if (f.roll(u, 1, v, 403) < 0.02) f.set(u, FLOOR, v, MAGMA);
            if (r <= OCULUS) f.clear(u, v, top + 1, 3);
        } else if (!ledge && f.roll(u, 2, v, 404) < 0.012) f.rubble(u, FLOOR + 1, v);   // fallen drums
        // light wells over the walkways; moss hanging from the vaults elsewhere
        if (lightWell(u, v)) f.clear(u, v, top + 1, 3);
        else if (!ledge && r > OCULUS && f.roll(u, 3, v, 405) < 0.03) f.set(u, top, v, VINE, 0);
    }

    /** One step of the grand stair (5 wide) from the plaza down to the walkways: step i at v = -37 + i, ten steps. */
    private static void step(Frame f, int u, int v) {
        int y = PLAZA - (v + 37);
        for (int k = FLOOR + 1; k < y; k++) f.masonry(u, k, v);
        f.set(u, y, v, BRICK_STAIRS, f.stairs(0, -1, false));
        f.clear(u, v, y + 1, PLAZA);
    }

    private static boolean lightWell(int u, int v) {
        int[][] wells = {{0, -21}, {0, 21}, {21, 0}, {-21, 0}};
        for (int[] w : wells) if (Math.abs(u - w[0]) <= 1 && Math.abs(v - w[1]) <= 1) return true;
        return false;
    }

    /** The gallery a wall column opens into (0..19), or -1: five bays a side, none behind the stair. */
    private static int niche(int u, int v, int au, int av) {
        int side, t;
        if (au >= 36 && av <= 33) { side = u > 0 ? 0 : 1; t = v; }
        else if (av >= 36 && au <= 33) { side = v > 0 ? 2 : 3; t = u; }
        else return -1;
        for (int i = 0; i < 5; i++) {
            int c = -24 + 12 * i;
            if (Math.abs(t - c) <= 1) return side == 3 && i == 2 ? -1 : side * 5 + i;
        }
        return -1;
    }

    private static void plaza(Frame f, int u, int v, int au, int av, int m, double r) {
        boolean stairwell = au <= 2 && v >= -36 && v <= -27;
        if (stairwell) return;
        if (r <= OCULUS) { f.clear(u, v, 3, PLAZA); return; }
        if (lightWell(u, v)) { f.set(u, PLAZA, v, IRON_BARS); return; }
        boolean grid = line(u) == 0 || line(v) == 0;
        if (m == 39) f.set(u, PLAZA, v, BRICK, 3);
        else if (grid) f.set(u, PLAZA, v, BRICK, f.roll(u, PLAZA, v, 410) < 0.25 ? 2 : 0);
        else if (f.roll(u, PLAZA, v, 411) < 0.8) f.set(u, PLAZA, v, STONE, 6);
        else f.paving(u, PLAZA, v);
        // the stairwell's balustrade
        if (au == 3 && v >= -35 && v <= -26 || au <= 3 && v == -26) f.set(u, PLAZA + 1, v, WALL, 0);
        // the fountains at the corners
        double dc = Math.sqrt((au - 24) * (au - 24) + (av - 24) * (av - 24));
        if (dc <= 4.2) { f.set(u, PLAZA, v, H2O); f.set(u, PLAZA - 1, v, PRISMARINE, 1); }
        else if (dc <= 5.4) { f.set(u, PLAZA, v, PRISMARINE, 1); f.set(u, PLAZA + 1, v, BRICK, 3); }
        // broken columns of the forest rise through the plaza
        if (line(u) == 0 && line(v) == 0 && r > 19 && dc > 6 && m <= 37 && !(au <= 4 && v <= -24)) {
            double q = f.roll(u, 9, v, 412);
            if (q < 0.35) {
                int h = 1 + (int) (f.roll(u, 10, v, 413) * 7);
                for (int y = PLAZA + 1; y <= PLAZA + h; y++) f.set(u, y, v, QUARTZ, 2);
                if (q < 0.12) f.set(u, PLAZA + h + 1, v, QUARTZ, 1);
            }
        }
    }

    /** The Pavilion of Tears: a stepped marble floor, sixteen columns, an entablature and a green dome with a lantern. */
    private static void pavilion(Frame f, int u, int v, double r) {
        if (r > OCULUS) {
            f.set(u, PLAZA + 1, v, QUARTZ, 0);
            if (r <= 15.5) f.set(u, PLAZA + 2, v, r >= 9.5 && r < 10.3 ? PRISMARINE : QUARTZ, r >= 9.5 && r < 10.3 ? 2 : 0);
            if (r <= OCULUS + 1) f.set(u, PLAZA + 3, v, WALL, 0);
        }
        for (int[] p : RING)
            if (p[0] == u && p[1] == v) {
                f.set(u, 7, v, BRICK, 3);
                for (int y = 8; y <= 15; y++) f.set(u, y, v, QUARTZ, 2);
                f.set(u, 16, v, QUARTZ, 1);
            }
        if (r >= 11.5 && r <= 14.6) { f.set(u, 17, v, BRICK, 0); f.glyph(u, 18, v); }
        if (r <= 14.6) {
            int ho = 19 + (int) Math.round(16 * Math.sqrt(Math.max(0, 1 - r * r / (14.6 * 14.6))));
            int hi = r < 13 ? 19 + (int) Math.round(14.2 * Math.sqrt(1 - r * r / 169.0)) : 18;
            double ang = Math.toDegrees(Math.atan2(u, v)) + 360;
            boolean rib = Math.abs(((ang - 11.25) % 22.5 + 22.5) % 22.5 - 11.25) > 9.5 && r > 3;
            for (int y = Math.max(19, hi + 1); y <= ho; y++) {
                if (r <= 2.2) break;                     // the dome's eye under the lantern
                f.set(u, y, v, PRISMARINE, rib ? 2 : y == 27 ? 1 : 0);
            }
            if (r <= 2.9) {
                boolean post = Math.abs(u) == 2 && Math.abs(v) == 2;
                if (post) for (int y = 33; y <= 38; y++) f.set(u, y, v, PRISMARINE, 2);
                if (r <= 2.9 && !post && (Math.abs(u) == 2 || Math.abs(v) == 2)) f.set(u, 33, v, PRISMARINE, 2);
                f.set(u, 39, v, PRISMARINE, 2);
                if (u == 0 && v == 0) { f.set(u, 37, v, SEA_LANTERN); f.set(u, 38, v, PRISMARINE, 2); for (int y = 40; y <= 43; y++) f.set(u, y, v, y == 43 ? OBSIDIAN : WALL, 1); }
            }
        }
    }

    // ------------------------------------------------------------------ sculptures

    /** A Medusa head of terracotta under a column: forward f, up up (unit vectors in u, y, v), snakes for hair. */
    private static void medusa(Frame fr, int[] b, int cu, int cy, int cv, int[] fw, int[] up) {
        if (!hit(b, cu - 4, cv - 4, cu + 4, cv + 4)) return;
        int[] rt = {fw[1] * up[2] - fw[2] * up[1], fw[2] * up[0] - fw[0] * up[2], fw[0] * up[1] - fw[1] * up[0]};
        for (int du = -4; du <= 4; du++)
            for (int dy = -4; dy <= 4; dy++)
                for (int dv = -4; dv <= 4; dv++) {
                    double x = du * rt[0] + dy * rt[1] + dv * rt[2], y = du * up[0] + dy * up[1] + dv * up[2], z = du * fw[0] + dy * fw[1] + dv * fw[2];
                    double e = x * x / 6.8 + y * y / 10.2 + z * z / 6.8;
                    int u = cu + du, yy = cy + dy, v = cv + dv;
                    if (e > 1) {
                        // the snakes: stray locks of green clay round the back and crown
                        if (e < 1.5 && (z < 0.5 || y > 1.5) && fr.roll(u, yy, v, 420) < 0.3) fr.set(u, yy, v, TERRACOTTA, 13);
                        continue;
                    }
                    int id = TERRACOTTA, meta = 0;
                    if (z < 0.6 || y > 2.2) meta = fr.roll(u, yy, v, 421) < 0.6 ? 13 : 5;          // hair
                    else if (z > 1.2) {
                        if (Math.abs(Math.abs(x) - 1) < 0.5 && Math.abs(y - 0.7) < 0.5) { id = OBSIDIAN; meta = 0; }   // eyes
                        else if (Math.abs(x) < 0.5 && Math.abs(y + 1.6) < 0.5) meta = 15;                 // mouth
                    }
                    fr.set(u, yy, v, id, meta);
                }
    }

    /**
     * A veiled mourner of marble on a plinth, facing (fu, fv), her hands to her face; her tears run from (tu, tv) down to
     * {@code tearTo}. Scale 1 stands nine blocks high, scale 2 sixteen.
     */
    private static void mourner(Frame f, int[] b, int cu, int y0, int cv, int fu, int fv, int sc, int tu, int tv, int tearTo) {
        int reach = 2 * sc + 2;
        if (!hit(b, Math.min(cu, tu) - reach, Math.min(cv, tv) - reach, Math.max(cu, tu) + reach, Math.max(cv, tv) + reach)) return;
        double fl = Math.sqrt(fu * fu + fv * fv), nu = fu / fl, nv = fv / fl;
        int plinth = sc, body = 5 * sc + 1, head = y0 + plinth + body;
        for (int du = -reach; du <= reach; du++)
            for (int dv = -reach; dv <= reach; dv++) {
                int u = cu + du, v = cv + dv;
                double dd = Math.sqrt(du * du + dv * dv), front = du * nu + dv * nv;
                if (Math.max(Math.abs(du), Math.abs(dv)) <= sc) for (int y = y0; y < y0 + plinth; y++) f.set(u, y, v, BRICK, y == y0 + plinth - 1 ? 3 : 0);
                for (int y = y0 + plinth; y < head; y++) {
                    double t = (y - y0 - plinth) / (double) body, prof = sc * (1.35 - 0.55 * t) + 0.2;
                    // she bows: her upper body leans forward
                    double lean = t > 0.6 ? (t - 0.6) * 1.5 * sc : 0, fx = du - nu * lean, fz = dv - nv * lean;
                    if (Math.sqrt(fx * fx + fz * fz) > prof) continue;
                    boolean veil = t > 0.55 && fx * nu + fz * nv < -0.2 * sc;
                    f.set(u, y, v, veil ? CLAY : QUARTZ, veil ? 15 : Math.floorMod(du + dv, 2) == 0 ? 2 : 0);
                }
                double hx = du - nu * sc * 0.9, hz = dv - nv * sc * 0.9;
                for (int y = head; y < head + sc + 1; y++) {
                    if (Math.sqrt(hx * hx + hz * hz) > sc * 0.85 + 0.15) continue;
                    f.set(u, y, v, hx * nu + hz * nv < 0 ? CLAY : QUARTZ, hx * nu + hz * nv < 0 ? 15 : 0);
                }
                if (front > sc * 0.9 + 0.3 && front < sc * 0.9 + 1.4 && Math.abs(du * nv - dv * nu) < 0.9) f.set(u, head, v, QUARTZ, 1);   // the hands
            }
        f.set(cu, head + sc + 1, cv, CLAY, 15);
        f.set(tu, head, tv, H2O);
        for (int y = head - 1; y >= tearTo; y--) f.set(tu, y, tv, FALLING, 8);
    }

    /** The propylon over the grand stair: two pylons and a lintel carved with the cistern's name. */
    private static void propylon(Frame f, int[] b) {
        if (!hit(b, -6, -39, 6, -35)) return;
        for (int u = -6; u <= 6; u++)
            for (int v = -39; v <= -36; v++) {
                int au = Math.abs(u);
                if (au >= 3) for (int y = PLAZA + 1; y <= 16; y++) { if (y == 10 || y == 16) f.glyph(u, y, v); else if (f.roll(u, y, v, 430) < 0.7) f.set(u, y, v, BRICK_BLOCK); else f.masonry(u, y, v); }
                else for (int y = 13; y <= 16; y++) if (y == 14) f.glyph(u, y, v); else f.masonry(u, y, v);
            }
        for (int u = -6; u <= 6; u++) if ((u & 1) == 0) f.set(u, 17, -39, BRICK, 3);
        f.set(-3, 12, -40, SEA_LANTERN);
        f.set(3, 12, -40, SEA_LANTERN);
    }

    // ------------------------------------------------------------------ tiles

    private static void tiles(Frame f, int[] b, long h) {
        int lore = Hash.range(h, 0, 99);
        // the Gorgon's hoard on the pool's walk
        f.chest(-12, WALK + 1, 0, 1, 0, "minecraft:chests/end_city_treasure", "lore:" + lore + ";trinket:0.6");
        f.chest(12, WALK + 1, 0, -1, 0, "minecraft:chests/woodland_mansion", "trinket:0.4");
        f.glyph(3, WALK + 1, -13);
        f.glyph(3, WALK + 2, -13);
        f.sign(3, WALK + 2, -14, 0, -1, "DO NOT MEET\nHER EYES");
        // the galleries
        String[] loot = {Sites.DUNGEON, "minecraft:chests/abandoned_mineshaft", Sites.CORRIDOR, Sites.DESERT, Sites.JUNGLE, "minecraft:chests/igloo_chest",
            Sites.LIBRARY, "minecraft:chests/stronghold_crossing"};
        int chests = 0;
        for (int side = 0; side < 4; side++)
            for (int i = 0; i < 5; i++) {
                if (side == 3 && i == 2) continue;
                int t = -24 + 12 * i, idx = side * 5 + i;
                int[] in = side == 0 ? new int[] {37, t} : side == 1 ? new int[] {-37, t} : side == 2 ? new int[] {t, 37} : new int[] {t, -37};
                int[] out = {-Integer.signum(in[0]) * (Math.abs(in[0]) == 37 ? 1 : 0), -Integer.signum(in[1]) * (Math.abs(in[1]) == 37 ? 1 : 0)};
                int kind = Math.floorMod(idx * 7 + 3, 5);
                if (kind <= 1 && chests < 8) { f.chest(in[0], LEDGE + 1, in[1], out[0], out[1], loot[chests], chests == 2 ? "lore:" + (lore + 11) : chests == 5 ? "trinket:0.25" : null); chests++; }
                else if (kind == 2) { f.set(in[0], LEDGE + 1, in[1], BRICK, 3); f.set(in[0], LEDGE + 2, in[1], SLAB, 5); f.set(in[0] + Math.abs(out[1]), LEDGE + 1, in[1] + Math.abs(out[0]), SKULL, 1); }
                else if (kind == 3) f.spawner(in[0], LEDGE + 1, in[1], idx % 2 == 0 ? "SKELETON" : "ZOMBIE");
                else { f.set(in[0], LEDGE + 1, in[1], BONE, 0); f.set(in[0], LEDGE + 2, in[1], SKULL, 1); }
            }
        // the pavilion, the fountains, the Medusa walk, the propylon
        f.chest(0, 7, 12, 0, -1, Sites.LIBRARY, "lore:" + (lore + 37));
        f.chest(0, 7, -12, 0, 1, Sites.JUNGLE, null);
        f.chest(-24, PLAZA + 1, 30, 0, -1, Sites.DESERT, null);
        f.chest(24, PLAZA + 1, -30, 0, 1, "minecraft:chests/nether_bridge", null);
        f.chest(-25, WALK + 1, 23, 1, 0, Sites.DUNGEON, "trinket:0.2");
        f.chest(-7, PLAZA + 1, -37, 0, -1, Sites.SMITH, null);
        f.spawner(0, 7, -10, "WITCH");
        f.sign(4, 8, -40, 0, -1, "THE WEEPING\nCISTERN");
        f.sign(-4, 8, -40, 0, -1, "SHE WEEPS FOR\nWHAT SHE HAS\nSEEN");
        f.glyph(-26, WALK + 1, 22);
        f.glyph(-26, WALK + 2, 22);
        f.sign(-25, WALK + 2, 22, 1, 0, "THE GORGON'S\nSISTERS\nLOOKED UPON\nTHE WATER");
        f.sign(8, 7, 6, 1, 0, "HER TEARS\nFILL THE\nDARK");
        // the dart traps on the walkways
        f.dispenser(0, WALK, -24, 0, 0);
        f.set(0, WALK + 1, -24, PLATE, 0);
        f.dispenser(26, WALK, 0, 0, 0);
        f.set(26, WALK + 1, 0, PLATE, 0);
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
