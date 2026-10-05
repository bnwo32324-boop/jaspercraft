package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Vault of the Elder Sign (epoch 6): a monument of the Elder Things in the shape of their five-pointed star. Each
 * arm is a tall hall (the Sign-Bearers, the Scriptorium, the Armoury of the Wardens, the Hall of Offerings, the
 * Reliquary) ending in its treasure; the heart is a pillared hall round an open oculus; the roof is a plaza carved with
 * the Elder Sign in obsidian, with an obelisk on every point and the Flame of the Sign, a spire some sixty blocks high,
 * over the heart. A precinct wall rings it; in the notches between the arms lie the Forecourt and its gatehouse, the
 * Web Garden, the Pit of Bones, the Garden of the Petrified and the Well of the Sign. Under it all (down to base - 12)
 * the vault: five crypt halls under the arms (the Ossuary, the Crypt of the Star-Heads, the Cells of the Wardens, the
 * Drowned Crypt, the Reliquaries), each with its apse, an ambulatory joining them, five reliquary chapels, and under
 * the heart the star-shaped lair of the Elder Thing. A newel stair in every arm runs from the vault to the roof.
 */
final class GreatElderVault extends GreatDesign {
    private static final int R = 44, FLOOR = -12, NONE = -99;
    /** The star's tips and inner corners, the heart's apothem, the precinct wall. */
    private static final double RO = 40, RI = 17, PENT = RI * Math.cos(Math.PI / 5), WALL_IN = 42, WALL_OUT = 43.6;
    /** Arm axes (toward tip k) and notch axes (between tips k and k + 1), turned from +v toward +u. */
    private static final double[] SU = new double[5], SV = new double[5], NU = new double[5], NV = new double[5];
    private static final Star STAR = new Star(RO, RI, 0), LAIR = new Star(19, 10, 36), SIGN = new Star(4.6, 1.9, 0);
    private static final int LAIR_R = 1, HALL = 10, CHAPEL = 20, RING = 30;
    /** Notch themes by notch index: 2 is the front. */
    private static final int WEBS = 0, PIT = 1, FORECOURT = 2, GARDEN = 3, WELL = 4;
    private static final int[][] PILLARS = new int[5][], OBELISKS = new int[5][], STAIRS = new int[5][];
    private static final int[] PIT_C, WELL_C;
    /** Garrison points: u, y, v; and their packs. */
    private static final int[][] SPOTS;
    private static final String[] PACKS;

    static {
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(72 * k), n = Math.toRadians(72 * k + 36);
            SU[k] = Math.sin(a); SV[k] = Math.cos(a); NU[k] = Math.sin(n); NV[k] = Math.cos(n);
        }
        for (int k = 0; k < 5; k++) { PILLARS[k] = arm(k, 10, 0); OBELISKS[k] = arm(k, 35, 0); STAIRS[k] = arm(k, 21, 0); }
        PIT_C = notch(PIT, 30, 0);
        WELL_C = notch(WELL, 30, 0);
        Object[][] g = {
            {0, 1, -38, "cult_zealot+vindicator"}, {-11, 1, -34, "zombie+husk+zombie_villager"},
            {notch(WEBS, 33, 0), 1, "spider+cave_spider+tomb_crawler"}, {notch(PIT, 30, 0), -4, "skeleton+stray"},
            {notch(GARDEN, 31, 0), 1, "star_spawn+mi_go+enderman"}, {notch(WELL, 38, 0), 1, "deep_one+zombie"},
            {arm(0, 28, 0), 1, "mi_go+endermite"}, {arm(1, 28, 0), 1, "cult_adept+evoker+illusioner"},
            {arm(2, 28, 0), 1, "vindicator+wither_skeleton"}, {arm(3, 28, 0), 1, "witch+cult_zealot"},
            {arm(4, 28, 0), 1, "ghoul+zombie_villager"}, {0, 1, 7, "!star_spawn+cult_adept"},
            {arm(0, 26, 0), 15, "nightgaunt"}, {arm(3, 26, 2), 15, "nightgaunt+creeper"},
            {arm(0, 15, 0), -11, "skeleton+ghoul"}, {arm(1, 28, 0), -11, "tomb_crawler+silverfish"},
            {arm(2, 28, 0), -11, "wither_skeleton+stray"}, {arm(3, 28, 3), -11, "deep_one+slime"},
            {arm(4, 28, 0), -11, "ghoul+husk"}, {notch(WEBS, 26, 0), -11, "hound+cave_spider"},
            {notch(FORECOURT, 32, 0), -11, "shoggoth+slime"}, {notch(GARDEN, 26, 0), -11, "!ghoul+zombie"},
        };
        SPOTS = new int[g.length][];
        PACKS = new String[g.length];
        for (int i = 0; i < g.length; i++) {
            if (g[i][0] instanceof int[]) { int[] p = (int[]) g[i][0]; SPOTS[i] = new int[] {p[0], (Integer) g[i][1], p[1]}; PACKS[i] = (String) g[i][2]; }
            else { SPOTS[i] = new int[] {(Integer) g[i][0], (Integer) g[i][1], (Integer) g[i][2]}; PACKS[i] = (String) g[i][3]; }
        }
    }

    /** A point along arm k's axis ({@code rr} out, {@code ll} to the side). */
    private static int[] arm(int k, double rr, double ll) {
        return new int[] {(int) Math.round(rr * SU[k] + ll * SV[k]), (int) Math.round(rr * SV[k] - ll * SU[k])};
    }

    /** A point along notch k's axis. */
    private static int[] notch(int k, double a, double l) {
        return new int[] {(int) Math.round(a * NU[k] + l * NV[k]), (int) Math.round(a * NV[k] - l * NU[k])};
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        Layout l = new Layout();
        l.boss = new int[] {0, FLOOR + 1, 5};
        int elder = r.nextInt(5);
        for (int i = 0; i < SPOTS.length; i++) {
            String pack = PACKS[i];
            if (i == 2 + elder && !pack.startsWith("!")) pack = "!" + pack;     // one courtyard pack follows an elder
            l.garrisons.add(g(SPOTS[i][0], SPOTS[i][1], SPOTS[i][2], pack));
        }
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int[] b = bounds(f, c, R);
        if (b == null) return;
        double[] along = new double[1];
        for (int u = b[0]; u <= b[1]; u++)
            for (int v = b[2]; v <= b[3]; v++) column(s, f, u, v, along);
        for (int k = 0; k < 5; k++) spiral(f, b, STAIRS[k][0], STAIRS[k][1], FLOOR + 1, 14, 0, 14);
        statues(f, b);
        lair(f, b);
        vaultFeatures(f, b);
        tiles(f, b, s.hash);
    }

    // ------------------------------------------------------------------ columns

    private static void column(Plans.GreatSite s, Frame f, int u, int v, double[] along) {
        double d = Math.sqrt(u * u + v * v);
        if (d >= WALL_OUT) return;                       // beyond the precinct the land lies as it is
        int nat = s.surface(f.wx(u, v), f.wz(u, v)) - s.base;
        for (int y = Math.max(FLOOR, nat + 1); y < 0; y++) f.rubble(u, y, v);
        f.clear(u, v, 1, Math.max(3, nat + 3));
        if (d >= WALL_IN) { precinct(f, u, v, nat); return; }
        f.set(u, 0, v, d >= 40.5 ? STONE : BRICK, d >= 40.5 ? 6 : f.roll(u, 0, v, 301) < 0.5 ? 0 : 2);
        if (d < 40.5 && f.roll(u, 0, v, 302) < 0.3) f.paving(u, 0, v);
        vaultColumn(f, u, v, d);
        double e = STAR.dist(u, v, along);
        if (e > 0) court(f, u, v, d, e);
        else starColumn(f, u, v, d, -e, along[0]);
        int k = nearestNotch(u, v);
        double na = u * NU[k] + v * NV[k], nl = Math.abs(u * NV[k] - v * NU[k]);
        if (k != FORECOURT && na >= 12.5 && na <= 20 && nl <= 1.5) f.clear(u, v, 1, 4);     // the side doors at the inner corners
        gateColumn(f, u, v, e);
    }

    /** The precinct wall, retaining the land where it stands higher; a gate on every notch's axis. */
    private static void precinct(Frame f, int u, int v, int nat) {
        int k = nearestNotch(u, v);
        double nl = Math.abs(u * NV[k] - v * NU[k]);
        boolean gate = nl <= 2.5;
        int top = gate ? 0 : Math.max(nl <= 3.7 ? 6 : 3, nat + 1);
        for (int y = Math.max(FLOOR, Math.min(nat + 1, 0)); y <= top; y++) {
            if (gate) { if (y == 0) f.set(u, 0, v, STONE, 6); else f.rubble(u, y, v); }
            else if (y == top) f.glyph(u, y, v);
            else f.masonry(u, y, v);
        }
    }

    // ------------------------------------------------------------------ the vault

    /** Which part of the vault a column lies in (its walls too, grown by {@code g}), or 0. */
    private static int region(double u, double v, double d, double g) {
        if (d <= 11 + g) return LAIR_R;
        if (d <= 21 + g && LAIR.dist(u, v, null) <= g) return LAIR_R;
        for (int k = 0; k < 5; k++) {
            double rr = u * SU[k] + v * SV[k];
            if (rr < 5) continue;
            double ll = Math.abs(u * SV[k] - v * SU[k]), ar = rr - 33;
            if (rr >= 11 - g && rr <= 33 && ll <= 4.5 + g || ar * ar + ll * ll <= (6 + g) * (6 + g)) return HALL + k;
        }
        for (int k = 0; k < 5; k++) {
            double na = u * NU[k] + v * NV[k], nl = Math.abs(u * NV[k] - v * NU[k]);
            if (na >= 28 - g && na <= 36 + g && nl <= 3.5 + g) return CHAPEL + k;
        }
        return d >= 24 - g && d <= 28 + g ? RING : 0;
    }

    /** The top of the vault's air in a column (the halls vaulted, the lair domed), or NONE. */
    private static int airTop(double u, double v, double d) {
        int top = NONE;
        if (d <= 11 || d <= 21 && LAIR.dist(u, v, null) <= 0) top = Math.max(-8, -2 - (int) (d * d / 60));
        for (int k = 0; k < 5; k++) {
            double rr = u * SU[k] + v * SV[k];
            if (rr < 5) continue;
            double ll = Math.abs(u * SV[k] - v * SU[k]), ar = rr - 33, a2 = ar * ar + ll * ll;
            if (rr >= 11 && rr <= 33 && ll <= 4.5) top = Math.max(top, ll <= 2.5 ? -5 : ll <= 3.5 ? -6 : -7);
            if (a2 <= 36) top = Math.max(top, -4 - (int) (a2 / 12));
            double na = u * NU[k] + v * NV[k], nl = Math.abs(u * NV[k] - v * NU[k]);
            if (na >= 28 && na <= 36 && nl <= 3.5) top = Math.max(top, -7);
        }
        if (d >= 24 && d <= 28) top = Math.max(top, -7);
        return top;
    }

    private static void vaultColumn(Frame f, int u, int v, double d) {
        int shell = region(u, v, d, 2);
        if (shell == 0) return;
        boolean ossuary = shell == HALL;
        for (int y = FLOOR; y <= -1; y++) {
            if (ossuary && y > FLOOR && y < -4) f.set(u, y, v, BONE, 0);
            else f.eldritch(u, y, v);
        }
        int top = airTop(u, v, d);
        int here = region(u, v, d, 0);
        if (top == NONE) {
            // reliquary niches and prison cells cut into the walls of halls 4 and 2, skull niches in the ossuary
            for (int k : new int[] {0, 2, 4}) {
                double rr = u * SU[k] + v * SV[k], ll = Math.abs(u * SV[k] - v * SU[k]);
                if (rr < 13 || rr > 31 || ll <= 4.5 || Math.abs(rr - 21) < 3) continue;
                int band = Math.floorMod((int) Math.floor(rr), 6);
                if (k == 4 && ll <= 6.5 && band >= 1 && band <= 3) {
                    if (ll <= 5.5) for (int y = FLOOR + 1; y <= FLOOR + 3; y++) f.set(u, y, v, IRON_BARS);
                    else { f.clear(u, v, FLOOR + 1, FLOOR + 3); f.set(u, FLOOR + 1, v, f.roll(u, 0, v, 303) < 0.5 ? SKULL : GLOWSTONE, 1); }
                } else if (k == 2 && ll <= 6.5 && band <= 3) {
                    if (ll <= 5.5 && band != 1) for (int y = FLOOR + 1; y <= FLOOR + 4; y++) f.set(u, y, v, IRON_BARS);
                    else f.clear(u, v, FLOOR + 1, FLOOR + 4);
                    if (ll > 5.5 && f.roll(u, 0, v, 304) < 0.3) f.set(u, FLOOR + 1, v, BONE, 0);
                } else if (k == 0 && ll <= 5.5 && Math.floorMod((int) Math.floor(rr), 3) == 0) {
                    f.set(u, -9, v, SKULL, 1);
                    f.set(u, -7, v, SKULL, 1);
                }
            }
            return;
        }
        // the floor
        if (here == LAIR_R) f.set(u, FLOOR, v, f.roll(u, 0, v, 305) < 0.5 ? OBSIDIAN : CLAY, 15);
        else if (here == HALL) f.set(u, FLOOR, v, f.roll(u, 0, v, 305) < 0.6 ? BONE : GRAVEL, 0);
        else f.set(u, FLOOR, v, (u + v & 1) == 0 ? STONE : BRICK, (u + v & 1) == 0 ? 6 : 0);
        f.clear(u, v, FLOOR + 1, top);
        boolean keep = reserved(u, v);
        double w = f.roll(u, 1, v, 306);
        if (here == LAIR_R) {
            if (d <= 2.5) { f.clear(u, v, -1, 0); for (int y = FLOOR + 1; y <= FLOOR + 3; y++) f.set(u, y, v, WEB); return; }
            if (d > 6 && w < 0.22) f.set(u, top, v, WEB);
            if (d > 8 && w > 0.9) f.set(u, top - 1, v, WEB);
            if (!keep && d > 4) {
                double q = f.roll(u, 2, v, 307);
                if (q < 0.05) f.set(u, FLOOR + 1, v, BONE, 0);
                else if (q < 0.08) f.set(u, FLOOR + 1, v, SKULL, 1);
                else if (q < 0.10) f.set(u, FLOOR + 1, v, WEB);
                else if (q > 0.985) f.set(u, FLOOR, v, MAGMA);
            }
            return;
        }
        if (w < 0.04) f.set(u, top, v, WEB);
        if (here == HALL + 1) {                           // the Crypt of the Star-Heads: rows of tombs
            double rr = u * SU[1] + v * SV[1], ll = Math.abs(u * SV[1] - v * SU[1]);
            int band = Math.floorMod((int) Math.floor(rr), 6);
            if (rr >= 13 && rr <= 31 && ll >= 1.2 && ll <= 2.4 && band >= 1 && band <= 3 && Math.abs(rr - 21) > 2.6) {
                f.set(u, FLOOR + 1, v, BRICK, 3);
                f.set(u, FLOOR + 2, v, SLAB, 5);
            }
        } else if (here == HALL + 3) {                    // the Drowned Crypt: a channel down the middle
            double rr = u * SU[3] + v * SV[3], ll = Math.abs(u * SV[3] - v * SU[3]);
            boolean chan = ll <= 0.7 && (rr >= 13 && rr <= 17.5 || rr >= 24.5 && rr <= 31);
            boolean curb = ll <= 1.9 && (rr >= 12 && rr <= 18.5 || rr >= 23.5 && rr <= 32);
            if (chan) f.set(u, FLOOR + 1, v, Canvas.WATER);
            else if (curb) f.set(u, FLOOR + 1, v, SLAB, 5);
        } else if (here == HALL && !keep && f.roll(u, 3, v, 308) < 0.06) {
            f.set(u, FLOOR + 1, v, BONE, 0);
        }
    }

    // ------------------------------------------------------------------ the courts between the arms

    private static void court(Frame f, int u, int v, double d, double e) {
        int k = nearestNotch(u, v);
        double na = u * NU[k] + v * NV[k], nl = u * NV[k] - v * NU[k];
        double w = f.roll(u, 1, v, 310);
        boolean keep = reserved(u, v);
        switch (k) {
            case FORECOURT: {
                int au = Math.abs(u);
                if (au <= 3 && v <= -25) f.set(u, 0, v, au == 3 ? OBSIDIAN : STONE, au == 3 ? 0 : 6);
                if ((au == 6 || au == 7) && (v == -31 || v == -30 || v == -37 || v == -36)) {
                    for (int y = 1; y <= 7; y++) if (y == 4) f.glyph(u, y, v); else f.eldritch(u, y, v);
                    f.set(u, 8, v, au == 6 && (v == -30 || v == -36) ? MAGMA : BRICK, 3);
                } else if (!keep && au > 3 && e > 2 && w < 0.025) f.rubble(u, 1, v);
                break;
            }
            case PIT: {
                double dp = Math.sqrt((u - PIT_C[0]) * (u - PIT_C[0]) + (v - PIT_C[1]) * (v - PIT_C[1]));
                if (dp < 7) {
                    int fy = Math.max(-5, (int) Math.floor(dp) - 7);
                    f.set(u, fy, v, dp < 2 ? BONE : BRICK, dp < 2 ? 0 : f.roll(u, fy, v, 311) < 0.5 ? 2 : 1);
                    f.clear(u, v, fy + 1, 0);
                    if (fy == -5 && !keep && w < 0.12) f.set(u, -4, v, w < 0.04 ? SKULL : BONE, w < 0.04 ? 1 : 0);
                } else if (dp < 8.2) {
                    if (w < 0.45) { f.set(u, 1, v, BONE, 0); if (w < 0.12) f.set(u, 2, v, SKULL, 1); }
                }
                break;
            }
            case WELL: {
                double dw = Math.sqrt((u - WELL_C[0]) * (u - WELL_C[0]) + (v - WELL_C[1]) * (v - WELL_C[1]));
                if (dw < 5) { f.set(u, -4, v, PRISMARINE, 1); for (int y = -3; y <= 0; y++) f.set(u, y, v, Canvas.WATER); }
                else if (dw < 6.6) { for (int y = -4; y <= 0; y++) f.eldritch(u, y, v); f.glyph(u, 1, v); }
                break;
            }
            case WEBS: {
                if (na < 21 || e < 1.5 || keep) break;
                if (w < 0.10) { f.set(u, 1, v, WEB); if (w < 0.03) f.set(u, 2, v, WEB); }
                else if (w < 0.13) f.set(u, 1, v, BONE, 0);
                else if (w < 0.14) f.set(u, 1, v, SKULL, 1);
                break;
            }
            default: {
                if (!keep && e > 1.5 && w < 0.02) { f.rubble(u, 1, v); if (w < 0.006) f.masonry(u, 2, v); }
                break;
            }
        }
    }

    // ------------------------------------------------------------------ the star

    private static void starColumn(Frame f, int u, int v, double d, double in, double t) {
        int k = 0;
        double rr = -1e9;
        for (int i = 0; i < 5; i++) { double x = u * SU[i] + v * SV[i]; if (x > rr) { rr = x; k = i; } }
        double ll = Math.abs(u * SV[k] - v * SU[k]);
        boolean heart = rr <= PENT;
        // the floor: the five ways run in obsidian from the oculus to the points
        if (ll < 0.55 && d > 3.6) f.set(u, 0, v, OBSIDIAN);
        else if (heart && d >= 5 && d < 5.8) f.set(u, 0, v, PRISMARINE, 2);
        else f.set(u, 0, v, BRICK, f.roll(u, 0, v, 320) < 0.7 ? 0 : 2);
        if (in <= 2) {
            boolean slit = t > 2 && Math.floorMod((int) Math.floor(t), 8) == 4;
            for (int y = 1; y <= 13; y++) {
                if (slit && y >= 4 && y <= 6) { f.set(u, y, v, AIR); continue; }
                if (y == 1) f.masonry(u, y, v);
                else if (y == 7 || y == 13) f.glyph(u, y, v);
                else f.eldritch(u, y, v);
            }
        } else {
            if (Math.abs(rr - PENT) <= 0.7) for (int y = 10; y <= 13; y++) { if (y == 10) f.glyph(u, y, v); else f.eldritch(u, y, v); }
            if (heart) {
                for (int[] p : PILLARS)
                    if (Math.abs(u - p[0]) <= 1 && Math.abs(v - p[1]) <= 1)
                        for (int y = 1; y <= 13; y++) { if (y == 1 || y == 7 || y == 13) f.glyph(u, y, v); else f.eldritch(u, y, v); }
                if (d <= 2.5) f.clear(u, v, -1, 0);
                else if (d <= 3.6) { f.set(u, 0, v, OBSIDIAN); f.set(u, 1, v, OBSIDIAN); }
            } else if (in <= 3.2) lining(f, u, v, k, rr, t);
            if (!heart && Math.abs(ll) < 0.6 && Math.abs(rr - 28) < 0.6) f.set(u, 13, v, SEA_LANTERN);
        }
        // the roof plaza, carved with the Elder Sign
        if (in <= 2) f.eldritch(u, 14, v);
        else if (Math.abs(rr - PENT) < 0.6 || in >= 2.6 && in <= 3.5 || d >= 6.8 && d < 7.6) f.set(u, 14, v, OBSIDIAN);
        else if (!heart && ll < 0.55) f.set(u, 14, v, CLAY, 15);
        else if (d >= 9 && d < 9.7) f.glyph(u, 14, v);
        else if (f.roll(u, 14, v, 321) < 0.7) f.set(u, 14, v, PRISMARINE, 2);
        else f.eldritch(u, 14, v);
        if (in <= 1.2 && Math.floorMod((int) Math.floor(t / 2), 2) == 0) f.eldritch(u, 15, v);
        // the Flame of the Sign over the heart
        if (d <= 5.6) {
            int top = Math.min(58, 15 + (int) ((5.6 - d) / 0.1));
            for (int y = 15; y <= top; y++) if (Math.floorMod(y, 7) == 1) f.glyph(u, y, v); else f.eldritch(u, y, v);
            if (d <= 0.8) { f.set(u, 59, v, MAGMA); f.set(u, 60, v, MAGMA); f.set(u, 61, v, GLOWSTONE); }
        }
        double er = 5.6 - (36 - 15) * 0.1;
        if (d > er - 0.5 && d <= er + 3) {
            f.set(u, 36, v, OBSIDIAN);
            if (d > er + 2) for (int i = 0; i < 5; i++) if (Math.abs(u * NV[i] - v * NU[i]) < 0.6 && u * NU[i] + v * NV[i] > 0) f.set(u, 36, v, SEA_LANTERN);
        }
        // an obelisk on every point
        for (int[] o : OBELISKS)
            if (Math.abs(u - o[0]) <= 1 && Math.abs(v - o[1]) <= 1) {
                for (int y = 15; y <= 25; y++) if (y % 5 == 0) f.glyph(u, y, v); else f.eldritch(u, y, v);
                if (u == o[0] && v == o[1]) { for (int y = 26; y <= 29; y++) f.set(u, y, v, OBSIDIAN); f.set(u, 30, v, MAGMA); }
            }
    }

    /** The arms' inner faces: shelves, racks, ledges, barred niches. */
    private static void lining(Frame f, int u, int v, int k, double rr, double t) {
        if (rr < PENT + 1.5 || Math.abs(rr - 21) < 3) return;
        int band = Math.floorMod((int) Math.floor(t), 5);
        switch (k) {
            case 1:                                        // the Scriptorium
                if (band != 0) for (int y = 1; y <= 4; y++) f.set(u, y, v, BOOKSHELF);
                f.set(u, 5, v, SLAB, 5);
                break;
            case 2:                                        // the Armoury of the Wardens
                if (band == 1 || band == 3) { f.set(u, 1, v, FENCE); f.set(u, 2, v, FENCE); f.set(u, 3, v, SLAB, 5); }
                else if (band == 2) f.set(u, 1, v, CAULDRON);
                break;
            case 3:                                        // the Hall of Offerings
                f.masonry(u, 1, v);
                f.set(u, 2, v, SLAB, 5);
                if (f.roll(u, 3, v, 322) < 0.35) f.set(u, 3, v, SKULL, 1);
                break;
            case 4:                                        // the Reliquary
                if (band != 0) for (int y = 1; y <= 3; y++) f.set(u, y, v, IRON_BARS);
                break;
            default:
                break;
        }
    }

    /** The gatehouse in the front notch, its passage into the heart, the Elder Sign over the gate. */
    private static void gateColumn(Frame f, int u, int v, double e) {
        int au = Math.abs(u);
        if (au > 6 || v < -26 || v > -12) return;
        boolean passage = au <= 2;
        if (passage) {
            f.set(u, 0, v, au == 2 ? OBSIDIAN : STONE, au == 2 ? 0 : 6);
            f.clear(u, v, 1, au == 2 ? 6 : 7);
            if (v <= -15 && e > -2.4)
                for (int y = au == 2 ? 7 : 8; y <= 18; y++) {
                    if (v == -26 && SIGN.dist(u, y - 12.5, null) < 0) f.set(u, y, v, OBSIDIAN);
                    else if (y == 9 || y == 18) f.glyph(u, y, v);
                    else f.eldritch(u, y, v);
                }
            if (v == -26 && u == 0) f.set(u, 12, v, MAGMA);
            return;
        }
        if (v > -15 || e < -2.4) return;
        for (int y = 1; y <= 18; y++) {
            if (v == -26 && SIGN.dist(u, y - 12.5, null) < 0) f.set(u, y, v, OBSIDIAN);
            else if (y == 9 || y == 18) f.glyph(u, y, v);
            else f.eldritch(u, y, v);
        }
        if ((au == 6 || v == -26) && (u + v & 1) == 0) f.eldritch(u, 19, v);
    }

    // ------------------------------------------------------------------ features

    /** A newel stair round (cu, cv) from step y0 to step y1, eight steps a turn, railed where it meets floors fy0 and fy1. */
    private static void spiral(Frame f, int[] b, int cu, int cv, int y0, int y1, int fy0, int fy1) {
        if (!hit(b, cu - 2, cv - 2, cu + 2, cv + 2)) return;
        int[][] ring = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int[] c : ring) f.clear(cu + c[0], cv + c[1], y0, y1 + 3);
        for (int y = y0 - 1; y <= y1 + 1; y++) if (y == y1 + 1) f.set(cu, y, cv, SEA_LANTERN); else f.set(cu, y, cv, BRICK, Math.floorMod(y, 6) == 0 ? 3 : 0);
        for (int i = 0; y0 + i <= y1; i++) {
            int[] c = ring[i % 8], n = ring[(i + 1) % 8];
            f.set(cu + c[0], y0 + i, cv + c[1], BRICK_STAIRS, f.stairs(n[0] - c[0], n[1] - c[1], false));
        }
        for (int fy : new int[] {fy0, fy1}) {
            int[] a = ring[Math.floorMod(fy - y0, 8)], z = ring[Math.floorMod(fy - y0 + 1, 8)], p = ring[Math.floorMod(fy - y0 - 1, 8)];
            for (int du = -2; du <= 2; du++)
                for (int dv = -2; dv <= 2; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != 2) continue;
                    if (near(du, dv, a) || near(du, dv, z) || near(du, dv, p)) continue;
                    f.set(cu + du, fy + 1, cv + dv, WALL, 0);
                }
        }
    }

    private static boolean near(int du, int dv, int[] c) { return Math.abs(du - c[0]) <= 1 && Math.abs(dv - c[1]) <= 1; }

    private static void statues(Frame f, int[] b) {
        // the Garden of the Petrified: three great Elder Things
        int[] a = notch(GARDEN, 25, 0), p = notch(GARDEN, 35, 10), q = notch(GARDEN, 35, -10);
        statue(f, b, a[0], 1, a[1], 2, 72 * GARDEN + 36 + 180);
        statue(f, b, p[0], 1, p[1], 2, 72 * GARDEN + 20 + 180);
        statue(f, b, q[0], 1, q[1], 2, 72 * GARDEN + 52 + 180);
        // the Well of the Sign: one stands in the water
        statue(f, b, WELL_C[0], -3, WELL_C[1], 1, 72 * WELL + 36 + 180);
        // the Hall of the Sign-Bearers: two pairs along the walls
        for (int side = -1; side <= 1; side += 2)
            {
                int[] s = arm(0, 16.5, side * 4.5);
                statue(f, b, s[0], 1, s[1], 1, side * 90);
            }
    }

    /** A petrified Elder Thing: a ridged barrel body on a plinth, five fins, a starfish head with one pale eye. */
    private static void statue(Frame f, int[] b, int cu, int y0, int cv, int sc, int turn) {
        int reach = 3 * sc + 2;
        if (!hit(b, cu - reach, cv - reach, cu + reach, cv + reach)) return;
        int body = y0 + sc + 1, h = 5 * sc + 1, neck = body + h, head = neck + sc;
        for (int du = -reach; du <= reach; du++)
            for (int dv = -reach; dv <= reach; dv++) {
                int u = cu + du, v = cv + dv;
                double dd = Math.sqrt(du * du + dv * dv);
                if (Math.max(Math.abs(du), Math.abs(dv)) <= sc) for (int y = y0; y < body; y++) if (y == body - 1) f.set(u, y, v, BRICK, 3); else f.masonry(u, y, v);
                for (int y = body; y < neck; y++) {
                    double tt = (y - body + 0.5) / h, prof = sc * (0.6 + 0.95 * Math.sin(Math.PI * tt));
                    if (dd > prof) continue;
                    boolean ridge = dd > prof - 1 && Math.floorMod((int) Math.floor((Math.atan2(du, dv) / (2 * Math.PI) + 1) * 10 + 0.5), 2) == 0;
                    f.set(u, y, v, PRISMARINE, ridge ? 1 : 2);
                }
                if (dd <= sc - 0.5) for (int y = neck; y < head; y++) f.set(u, y, v, PRISMARINE, 2);
            }
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(turn + 72 * i), su = Math.sin(a), sv = Math.cos(a);
            for (int t = 1; t <= 3 * sc; t++) {
                int fy = body + 3 * sc - t / 2;
                f.set(cu + (int) Math.round(su * (sc + t * 0.8)), fy, cv + (int) Math.round(sv * (sc + t * 0.8)), OBSIDIAN);
            }
            for (int t = 0; t <= 2 * sc; t++) {
                int hu = cu + (int) Math.round(su * t), hv = cv + (int) Math.round(sv * t);
                if (t == 2 * sc && i == 0) f.set(hu, head, hv, SEA_LANTERN);
                else if (t == 2 * sc) f.set(hu, head, hv, CLAY, 15);
                else f.set(hu, head, hv, PRISMARINE, 1);
            }
        }
        f.set(cu, head + 1, cv, PRISMARINE, 2);
    }

    /** The Elder Thing's lair: egg sacs in two lobes, its hoard in two more. */
    private static void lair(Frame f, int[] b) {
        if (!hit(b, -20, -20, 20, 20)) return;
        for (int k : new int[] {1, 3}) {
            int[] e = notch(k, 15, 0);
            for (int du = 0; du <= 1; du++) for (int dv = 0; dv <= 1; dv++) for (int y = FLOOR + 1; y <= FLOOR + 2; y++) f.set(e[0] + du, y, e[1] + dv, 35, 0);
            f.set(e[0], FLOOR + 3, e[1], WEB);
        }
    }

    /** The crypt halls' pillars, the apses' altars, the chapels' relic shelves. */
    private static void vaultFeatures(Frame f, int[] b) {
        for (int k = 0; k < 5; k++) {
            for (double rr : new double[] {17, 29}) { int[] p = arm(k, rr, 0); f.set(p[0], -4, p[1], GLOWSTONE); }
            for (double rr : new double[] {14, 17, 25, 29})
                for (int side = -1; side <= 1; side += 2) {
                    int[] p = arm(k, rr, side * 3);
                    if (!in(b, p[0], p[1])) continue;
                    for (int y = FLOOR + 1; y <= -6; y++) {
                        if (k == 0) f.set(p[0], y, p[1], BONE, 0);
                        else if (y == FLOOR + 1 || y == -6) f.set(p[0], y, p[1], BRICK, 3);
                        else f.eldritch(p[0], y, p[1]);
                    }
                }
            int[] c = notch(k, 35.5, 0);
            if (hit(b, c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3))
                for (int l = -2; l <= 2; l++) {
                    int[] p = notch(k, 35.5, l);
                    f.set(p[0], FLOOR + 1, p[1], l == 0 ? OBSIDIAN : BRICK, 3);
                    if (l != 0) f.set(p[0], FLOOR + 2, p[1], Math.abs(l) == 2 ? SKULL : SLAB, Math.abs(l) == 2 ? 1 : 5);
                }
        }
        // the bone pit's shaft down into the Garden chapel... and the pit's ladder down into chapel 1 below it
        int[] sh = notch(PIT, 31, 0);
        if (in(b, sh[0], sh[1]) || in(b, sh[0], sh[1] + 1)) {
            for (int y = FLOOR + 1; y <= -5; y++) { f.set(sh[0], y, sh[1], LADDER, f.facing(0, -1)); f.masonry(sh[0], y, sh[1] + 1); }
            f.set(sh[0], -4, sh[1], AIR);
        }
    }

    private static void tiles(Frame f, int[] b, long h) {
        int lore = Hash.range(h, 0, 99);
        // the hoard of the Elder Thing
        int[] h0 = notch(0, 14, 0), h4 = notch(4, 14, 0), sp = notch(2, 15, 0);
        chestToward(f, h0, FLOOR + 1, "minecraft:chests/end_city_treasure", "lore:" + lore + ";trinket:0.6");
        chestToward(f, h4, FLOOR + 1, "minecraft:chests/woodland_mansion", "trinket:0.4");
        f.spawner(sp[0], FLOOR + 1, sp[1], "CAVE_SPIDER");
        // each arm's treasure at its point
        String[] armLoot = {Sites.JUNGLE, Sites.LIBRARY, Sites.SMITH, Sites.DESERT, "minecraft:chests/stronghold_crossing"};
        String[] names = {"THE HALL OF\nSIGN-BEARERS", "THE\nSCRIPTORIUM", "THE ARMOURY\nOF WARDENS", "THE HALL OF\nOFFERINGS", "THE\nRELIQUARY"};
        for (int k = 0; k < 5; k++) {
            chestToward(f, arm(k, 31.5, 0), 1, armLoot[k], k == 1 ? "lore:" + (lore + 7) : k == 4 ? "trinket:0.25" : null);
            int[] st = arm(k, 25, 0), dir = card(-SU[k], -SV[k]);
            f.glyph(st[0], 1, st[1]);
            f.set(st[0], 2, st[1], BRICK, 3);
            f.sign(st[0] + dir[0], 2, st[1] + dir[1], dir[0], dir[1], names[k]);
        }
        // the apses and two chapels below
        String[] apse = {Sites.DUNGEON, "minecraft:chests/abandoned_mineshaft", Sites.CORRIDOR, "minecraft:chests/igloo_chest", "minecraft:chests/nether_bridge"};
        for (int k = 0; k < 5; k++) chestToward(f, arm(k, 36.5, 0), FLOOR + 1, apse[k], k == 0 ? "lore:" + (lore + 23) : null);
        int[] ch1 = notch(PIT, 34, 0), ch3 = notch(GARDEN, 34, 0);
        chestToward(f, ch1, FLOOR + 1, Sites.DUNGEON, "trinket:0.2");
        chestToward(f, ch3, FLOOR + 1, Sites.CORRIDOR, null);
        int[] os = arm(0, 33, 0);
        f.spawner(os[0], FLOOR + 1, os[1], "SKELETON");
        // the courts
        int[] wg = notch(WEBS, 36, -3), wc = notch(WEBS, 29, 4), pc = notch(PIT, 31, 2), wl = notch(WELL, 23, 0), gc = notch(GARDEN, 20, 0);
        f.spawner(wg[0], 1, wg[1], "SPIDER");
        chestToward(f, wc, 1, Sites.JUNGLE, null);
        chestToward(f, pc, -4, Sites.DUNGEON, null);
        chestToward(f, wl, 1, Sites.DESERT, null);
        chestToward(f, gc, 1, "minecraft:chests/stronghold_crossing", "lore:" + (lore + 41));
        // the gate's carvings and its dart trap
        f.sign(4, 3, -27, 0, -1, "THE VAULT OF\nTHE ELDER SIGN");
        f.sign(-4, 3, -27, 0, -1, "BEFORE THE\nSTARS IT WAS\nAND IT WAITS\nBELOW");
        f.dispenser(0, 0, -21, 0, 0);
        f.set(0, 1, -21, PLATE, 0);
        int[] t1 = notch(PIT, 26, 0), t3 = notch(WELL, 26, 0);
        f.dispenser(t1[0], FLOOR, t1[1], 0, 0);
        f.set(t1[0], FLOOR + 1, t1[1], PLATE, 0);
        f.dispenser(t3[0], FLOOR, t3[1], 0, 0);
        f.set(t3[0], FLOOR + 1, t3[1], PLATE, 0);
        int[] ls = arm(0, 12.5, 2);
        f.glyph(ls[0], FLOOR + 1, ls[1]);
        f.glyph(ls[0], FLOOR + 2, ls[1]);
        int[] ld = card(-SU[0], -SV[0]);
        f.sign(ls[0] + ld[0], FLOOR + 2, ls[1] + ld[1], ld[0], ld[1], "IT WAS HERE\nBEFORE THE\nSTARS");
    }

    /** A chest at p (y) facing the centre. */
    private static void chestToward(Frame f, int[] p, int y, String table, String extras) {
        int[] d = card(-p[0], -p[1]);
        f.chest(p[0], y, p[1], d[0], d[1], table, extras);
    }

    // ------------------------------------------------------------------ geometry

    /** The garrison points and the boss's, kept free of litter. */
    private static boolean reserved(int u, int v) {
        if (Math.abs(u) <= 1 && Math.abs(v - 5) <= 1) return true;
        for (int[] s : SPOTS) if (Math.abs(u - s[0]) <= 1 && Math.abs(v - s[2]) <= 1) return true;
        return false;
    }

    private static int nearestNotch(double u, double v) {
        int k = 0;
        double best = -1e9;
        for (int i = 0; i < 5; i++) { double x = u * NU[i] + v * NV[i]; if (x > best) { best = x; k = i; } }
        return k;
    }

    /** The cardinal direction nearest (x, z). */
    private static int[] card(double x, double z) {
        return Math.abs(x) >= Math.abs(z) ? new int[] {x >= 0 ? 1 : -1, 0} : new int[] {0, z >= 0 ? 1 : -1};
    }

    /** The chunk's columns in the local frame, clipped to the footprint: {u0, u1, v0, v1}, or null. */
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

    private static boolean in(int[] b, int u, int v) { return u >= b[0] && u <= b[1] && v >= b[2] && v <= b[3]; }

    /** A five-pointed star (tips at {@code ro}, inner corners at {@code ri}), its first tip turned {@code turn} degrees from +v toward +u. */
    private static final class Star {
        final double[] x = new double[10], z = new double[10];

        Star(double ro, double ri, double turn) {
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(turn + 36 * i), r = (i & 1) == 0 ? ro : ri;
                x[i] = r * Math.sin(a);
                z[i] = r * Math.cos(a);
            }
        }

        /** Signed distance to the outline (negative inside); {@code along} receives the position along the nearest edge. */
        double dist(double u, double v, double[] along) {
            double best = Double.MAX_VALUE;
            boolean in = false;
            for (int i = 0, j = 9; i < 10; j = i++) {
                if ((z[i] > v) != (z[j] > v) && u < (x[j] - x[i]) * (v - z[i]) / (z[j] - z[i]) + x[i]) in = !in;
                double ex = x[i] - x[j], ez = z[i] - z[j], len2 = ex * ex + ez * ez;
                double t = Math.max(0, Math.min(1, ((u - x[j]) * ex + (v - z[j]) * ez) / len2));
                double dx = u - x[j] - t * ex, dz = v - z[j] - t * ez, dd = dx * dx + dz * dz;
                if (dd < best) { best = dd; if (along != null) along[0] = t * Math.sqrt(len2); }
            }
            double d = Math.sqrt(best);
            return in ? -d : d;
        }
    }

}
