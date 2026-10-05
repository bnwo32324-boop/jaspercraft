package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Dreadnought (epoch 6): a warship of the Choir, a hundred blocks of stone and iron, wrecked on the shallows and broken
 * in two. Its stern half lies listing on a reef of its own rubble (the sea floor raised under it where the water runs
 * deep, so that its gun deck stands dry, clearly above the sea), its bow half swung aside onto a higher heap, listing harder
 * and nosing down. Through the hull: the holds (flooded on the low side) with their cargo and bulkheads, the gun deck above
 * the waterline with its cannons at the ports and darts under its planks, the main deck open to the night; the forecastle
 * and its deep-one figurehead and bowsprit; the stern castle with the officers' cabins, the quarterdeck, and on it the
 * captain's hall where the Drowned Admiral keeps his table, dry above the sea, under the poop deck and its stern lantern.
 * Three masts of stone banded in iron lean with the list, rigged in chains, their stone sails torn; the mainmast snapped and
 * fallen over the side and down to the sea floor (the way aboard), the foremast's yard fallen across the break (the way to
 * the bow). Cargo, cannons, plates and boats lie scattered on the sea floor.
 * <p>
 * Everything that is stood on, opened or hung on is fixed in {@code plan()} from the hull's own cell model, never from what
 * happens to be drawn: the boss, every garrison, chest and spawner has a floor under it and free cells over it (kept open
 * against every later part, see {@code L.free}); ladders and signs are set against solid plating; the forecastle holds a
 * walker's height even over the half step of a listing deck.
 */
final class GreatDreadnought extends GreatDesign {
    private static final int RUB = -3, HULL = -21, DECK = -22, BULK = -23, BALLAST = -24, SAIL = -25, MAST = -26;
    private static final int H2O = 9, PANE = 102, WSLAB = 126, BSTAIRS = 109, DARK_OAK = 5;
    private static final int FORE = 0, MID = 1, CASTLE = 2;
    // what a cell of the hull holds
    private static final int NONE = 0, SOLID = 1, FLOOR = 2, OPEN = 3, WALLC = 4, GLAZE = 5, BARREL = 6, CANNON = 7, BALLASTC = 8, RAIL = 9;

    /** The waterline's height on the keel: the hold floods up to it, so the gun deck (floor at 7) stands 3 or more blocks above the sea. */
    private static final int WL = 3;
    /** The bow half rides this much higher on its own heap of the reef than the stern half, so that even where it lists and noses under its gun deck stays dry. */
    private static final int BOW = 3;

    static final class L extends Layout {
        int ws, k, low;
        double rollS, rollB, sp, cp;
        double slope;          // how steeply the fallen mast runs down over the side
        int mastEnd;           // how far out (in blocks, from the mainmast's foot) it runs
        final List<int[]> debris = new ArrayList<>();      // {kind, u, v, a, b}
        final List<int[]> ladders = new ArrayList<>();     // {u, y, v, meta}: each rung, on the hull's own plating
        final List<int[]> tops = new ArrayList<>();        // {u, y, v}: the cell over each ladder's top rung, kept open
        final List<Object[]> signs = new ArrayList<>();    // {u, y, v, dx, dz, text}: each on the face of a solid block
        final List<Object[]> chests = new ArrayList<>();   // {u, y, v, dx, dz, table, extras}: each on a deck with air over it
        final List<Object[]> cages = new ArrayList<>();    // {u, y, v, entity}
        final List<int[]> darts = new ArrayList<>();       // {u, y, v}: the plate's cell, over a dart trap set in the gun deck's floor
        /** Cells that stay open (the standing room of the boss and of every dry garrison, the air over a chest), and the deck cells under them that stay whole. */
        final Set<Long> free = new HashSet<>(), whole = new HashSet<>();
    }

    static long key(int u, int y, int v) { return (long) (u + 512) << 40 | (long) (y + 512) << 20 | (long) (v + 512); }

    // ------------------------------------------------------------------------------------------------ the ship's frame

    /** Half the beam at a station (S along the ship, bow at -50, transom at 52). */
    static double beam(double s) {
        if (s < -50 || s > 52.5) return 0;
        if (s < -24) { double q = (s + 24) / 26.0; return 14 * Math.sqrt(Math.max(0, 1 - q * q)); }
        if (s > 30) return 14 - (s - 30) * 0.22;
        return 14;
    }

    /** Where a column lies in the wreck: {S, t, offset (the list and the bow's sinking), distance from the break}, or null. */
    static double[] locate(L l, double u, double v) {
        if (v >= 2) return new double[] {v, u, u * l.rollS, v - 2};
        double du = u, dv = v + 4, len = du * l.sp - dv * l.cp;
        if (len < 2) return null;
        double t = du * l.cp + dv * l.sp;
        return new double[] {-4 - len, t, t * l.rollB - len * 3.5 / 46 + BOW, len - 2};
    }

    /** The local column of a point given in the ship's frame. */
    static int[] column(L l, double s, double t) {
        if (s >= 2) return new int[] {(int) Math.round(t), (int) Math.round(s)};
        double len = -4 - s;
        return new int[] {(int) Math.round(len * l.sp + t * l.cp), (int) Math.round(-4 - len * l.cp + t * l.sp)};
    }

    /** The y of height h above the keel at a local column (its own list and sinking), or Integer.MIN_VALUE off the wreck. */
    static int yAt(L l, int u, int v, int h, boolean onDeck) {
        double[] q = locate(l, u, v);
        if (q == null) return Integer.MIN_VALUE;
        int o2 = (int) Math.round(q[2] * 2), oy = Math.floorDiv(o2, 2);
        return l.k + oy + h + (onDeck && (o2 & 1) != 0 ? 1 : 0);
    }

    /** By the rounded station, so that a bulkhead (a whole row of cells) never falls half in one zone and half in the next in the bow's turned frame. */
    static int zone(double s) { long si = Math.round(s); return si <= -36 ? FORE : si >= 35 ? CASTLE : MID; }

    /** What a cell of the hull holds: the plating, its decks and bulkheads, ports, windows, rooms. */
    static int cell(double s, double t, int h, double b, int hb) {
        double at = Math.abs(t);
        int z = zone(s);
        int si = (int) Math.round(s);
        boolean shell = at > b - 1 || s >= 51.5 || s <= -48.5;
        int top = z == FORE ? 18 : z == CASTLE ? (s >= 37.5 ? 25 : 19) : 14;
        if (h < hb || h > 25) return NONE;
        if (shell) {
            if (h > top) return NONE;
            if (z == MID && (h == 8 || h == 9) && si >= -32 && si <= 32 && Math.floorMod(si, 4) == 0 && at > b - 1 && Math.abs(si - 12) > 1 && Math.abs(si + 28) > 1)
                return h == 8 ? BARREL : OPEN;
            if (z == CASTLE && s >= 38.5 && s < 51.5 && (h == 20 || h == 21) && Math.floorMod(si, 3) == 0) return GLAZE;
            if (s >= 51.5 && h >= 19 && h <= 22 && at <= b - 3) return GLAZE;
            if (z == MID && h == top) return RAIL;
            return SOLID;
        }
        if (h <= 1) return BALLASTC;
        if (h == 2) return FLOOR;
        if (h <= 6) return (si == 20 || si == -20) && at > 1.2 ? WALLC : OPEN;
        if (h == 7) return FLOOR;       // (the hatches are cut by their own ladders, one cell each, see holds())
        if (h <= 11) {
            if ((si == 20 || si == -20) && (at > 1.2 || h == 11)) return WALLC;
            if (h == 8 && z == MID && Math.floorMod(si, 4) == 0 && si >= -32 && si <= 32 && at > b - 3 && Math.abs(si - 12) > 1 && Math.abs(si + 28) > 1) return CANNON;
            return OPEN;
        }
        if (h == 12) return FLOOR;
        if (z == FORE) {
            // three cells under the forecastle deck, so that even over a listing deck's half step there is a walker's height
            if (h <= 15) return si == -36 && at > 1.2 ? WALLC : OPEN;
            if (h == 16) return FLOOR;
            return h <= 18 ? OPEN : NONE;
        }
        if (z == CASTLE) {
            if (h <= 16) {
                if (si == 35 && !(at <= 1.2 && h <= 15)) return WALLC;
                if (at > 2.5 && at < 3.5 && si >= 36 && !((si == 38 || si == 46) && h <= 15)) return WALLC;
                if (si == 42 && at >= 3.5) return WALLC;
                return OPEN;
            }
            if (h == 17) return FLOOR;
            if (s < 37.5) return h <= 20 ? OPEN : NONE;
            if (h <= 23) {
                if (si == 38) return at <= 1.2 && h <= 20 ? OPEN : h >= 21 && at >= 3 && at <= 5 ? GLAZE : WALLC;
                return OPEN;
            }
            if (h == 24) return FLOOR;
            return OPEN;
        }
        return h <= 15 ? OPEN : NONE;
    }

    // ------------------------------------------------------------------------------------------------ the layout

    /** What the hull model holds at height h above the keel of the column (u, v), before the decay of the break. */
    private static int cellAt(L l, int u, int v, int h) {
        double[] q = locate(l, u, v);
        if (q == null) return NONE;
        double s = q[0], t = q[1], b = beam(s), at = Math.abs(t);
        if (b <= 0.3 || at > b + 0.5) return NONE;
        int hb = (int) Math.round(4 * (at / b) * (at / b));
        return cell(s, t, h, b, hb);
    }

    private static boolean air(int kind) { return kind == OPEN || kind == NONE; }

    /** Whether the hull's own plating or a bulkhead (or, with {@code deck}, a deck's edge, which the plan then keeps whole) fills the cell (u, y, v) above the water: what a ladder or a sign may hang on. */
    private static boolean solidAt(L l, int u, int y, int v, boolean deck) {
        double[] q = locate(l, u, v);
        if (q == null || q[3] < 4 || y < l.ws) return false;
        int k = cellAt(l, u, v, y - l.k - Math.floorDiv((int) Math.round(q[2] * 2), 2));
        return k == SOLID || k == WALLC || deck && k == FLOOR;
    }

    /**
     * A standing place on the deck whose floor cell lies at height {@code deck}, as near as may be to the point (S, t) of the
     * ship's frame: the column's floor must be there, the two cells above it free (above the half step a listing deck carries),
     * and the break's ragged edge left alone. Returns {u, y, v} or null.
     */
    private static int[] stand(L l, double s0, double t0, int deck) {
        int[] c0 = column(l, s0, t0);
        for (int d = 0; d <= 3; d++)
            for (int du = -d; du <= d; du++)
                for (int dv = -d; dv <= d; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != d) continue;
                    int u = c0[0] + du, v = c0[1] + dv;
                    double[] q = locate(l, u, v);
                    if (q == null || q[3] < 4) continue;
                    int h1 = deck + ((Math.round(q[2] * 2) & 1) != 0 ? 2 : 1);
                    if (cellAt(l, u, v, deck) != FLOOR || !air(cellAt(l, u, v, h1)) || !air(cellAt(l, u, v, h1 + 1))) continue;
                    return new int[] {u, yAt(l, u, v, deck, true) + 1, v};
                }
        return null;
    }

    /** Keeps n cells from (u, y, v) upward open, and the deck under them whole: nothing drawn later may stand on a boss or a garrison. */
    private static void reserve(L l, int u, int y, int v, int n) {
        for (int i = 0; i < n; i++) l.free.add(key(u, y + i, v));
        l.whole.add(key(u, y - 1, v));
        l.whole.add(key(u, y - 2, v));
    }

    /**
     * A free cell right over the floor of {@code deck}, with two cells of air above it, as near as may be to the point (S, t) of
     * the ship's frame (a chest or a spawner stands there, on the floor itself and never on a listing deck's half step); the cell
     * and the one over it are kept open for it. Returns {u, y, v} or null (nothing is placed in the gap where the hull breaks).
     */
    private static int[] spotOn(L l, double s0, double t0, int deck) {
        int[] c0 = column(l, s0, t0);
        for (int d = 0; d <= 2; d++)
            for (int du = -d; du <= d; du++)
                for (int dv = -d; dv <= d; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != d) continue;
                    int u = c0[0] + du, v = c0[1] + dv;
                    double[] q = locate(l, u, v);
                    if (q == null || q[3] < 4) continue;
                    if (cellAt(l, u, v, deck) != FLOOR || !air(cellAt(l, u, v, deck + 1)) || !air(cellAt(l, u, v, deck + 2))) continue;
                    int y = yAt(l, u, v, deck, false) + 1;
                    if (l.free.contains(key(u, y, v)) || l.free.contains(key(u, y + 1, v))) continue;
                    reserve(l, u, y, v, 2);
                    return new int[] {u, y, v};
                }
        return null;
    }

    private static void chestAt(L l, double s, double t, int deck, int dx, int dz, String table, String extras) {
        int[] c = spotOn(l, s, t, deck);
        if (c != null) l.chests.add(new Object[] {c[0], c[1], c[2], dx, dz, table, extras});
    }

    private static void cageAt(L l, double s, double t, int deck, String what) {
        int[] c = spotOn(l, s, t, deck);
        if (c != null) l.cages.add(new Object[] {c[0], c[1], c[2], what});
    }

    /** A ladder up the cells h0..h1 at about (S, t) facing local (dx, dz), moved to where the hull's plating stands whole behind every rung. */
    private static void ladderUp(L l, Frame f, double s0, double t0, int h0, int h1, int dx, int dz) {
        int[] c0 = column(l, s0, t0);
        for (int d = 0; d <= 2; d++)
            for (int du = -d; du <= d; du++)
                for (int dv = -d; dv <= d; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != d) continue;
                    int u = c0[0] + du, v = c0[1] + dv;
                    boolean ok = true;
                    for (int h = h0; h <= h1 && ok; h++) {
                        int kd = cellAt(l, u, v, h);
                        ok = (air(kd) || h == h1 && kd == FLOOR) && solidAt(l, u - dx, yAt(l, u, v, h, false), v - dz, true);
                    }
                    if (!ok) continue;
                    for (int h = h0; h <= h1; h++) {
                        int y = yAt(l, u, v, h, false);
                        l.ladders.add(new int[] {u, y, v, f.facing(dx, dz)});
                        l.whole.add(key(u - dx, y, v - dz));   // a deck plank behind a rung never rots away
                    }
                    l.tops.add(new int[] {u, yAt(l, u, v, h1 + 1, false), v});
                    return;
                }
    }

    /** A wall sign facing local (dx, dz) at about (S, t) and height h, moved to where a solid block of the hull stands behind it. */
    private static void signAt(L l, Frame f, double s0, double t0, int h, int dx, int dz, String text) {
        int[] c0 = column(l, s0, t0);
        for (int d = 0; d <= 2; d++)
            for (int du = -d; du <= d; du++)
                for (int dv = -d; dv <= d; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != d) continue;
                    int u = c0[0] + du, v = c0[1] + dv, y = yAt(l, u, v, h, false);
                    if (!air(cellAt(l, u, v, h)) || !solidAt(l, u - dx, y, v - dz, false)) continue;
                    l.signs.add(new Object[] {u, y, v, dx, dz, text});
                    return;
                }
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        l.ws = Plans.SEA - s.base;
        l.k = Math.max(0, l.ws - WL);
        l.low = r.nextBoolean() ? -1 : 1;
        l.rollS = -l.low / 7.0;
        l.rollB = -l.low / 5.0;
        double psi = Math.toRadians(6 + r.nextInt(5)) * (r.nextBoolean() ? 1 : -1);
        l.sp = Math.sin(psi);
        l.cp = Math.cos(psi);
        // the wreckage on the sea floor round about: crates, cannons, plates, boats, an anchor, bones
        for (int n = 0, tries = 0; n < 40 && tries < 600; tries++) {
            int u = -52 + r.nextInt(105), v = -54 + r.nextInt(109);
            double[] q = locate(l, u, v);
            boolean onHull = q != null && Math.abs(q[1]) <= beam(q[0]) + 2.5;
            if (onHull || Math.abs(u) > 52 || Math.abs(v) > 54) continue;
            int kind = n < 6 ? 0 : n < 10 ? 1 : n < 12 ? 2 : n < 14 ? 3 : n < 16 ? 4 : 5;
            if ((kind == 2 || kind == 0) && (Math.abs(u) > 48 || Math.abs(v) > 50)) continue;
            l.debris.add(new int[] {kind, u, v, r.nextInt(4), r.nextInt(100)});
            n++;
        }
        // the Admiral: in his hall on the quarterdeck's floor, a few paces before his table, clear of the railing, the lamp and the ladder
        int[] bs = stand(l, 46, 0, 17);
        l.boss = bs != null ? bs : new int[] {0, l.k + 18, 46};
        reserve(l, l.boss[0], l.boss[1], l.boss[2], 3);
        // the garrisons, in the ship's frame: {S, t, the deck's floor height}; each stands where its deck really has a floor and two free cells
        double[][] at = {
            {20, 4, 12}, {28, -5, 12}, {36, 3, 17}, {41, -4, 17}, {37, 0, 12}, {39, 6, 12}, {10, 2, 7}, {29, -2, 7},
            {-21, 2, 7}, {-25, 3, 12}, {-40, 0, 12}, {-42, 2, 16}, {46, 0, 24}, {15, -8 * l.low, 12}, {-12, -3, 12}, {6, 0, 12}, {24, -3, 12}};
        String[] packs = {
            "deep_one+zombie", "skeleton+stray", "cult_zealot+vindicator", "illusioner+evoker", "cult_adept+witch", "ghoul+husk",
            "skeleton+creeper", "wither_skeleton+zombie_villager", "hound+spider", "ghoul+zombie", "cave_spider+silverfish", "nightgaunt+creeper",
            "nightgaunt", "shoggoth+slime", "tomb_crawler+endermite", "star_spawn+mi_go", "mi_go+enderman"};
        for (int i = 0; i < at.length; i++) {
            int[] p = stand(l, at[i][0], at[i][1], (int) at[i][2]);
            if (p == null) {
                int[] c = column(l, at[i][0], at[i][1]);
                p = new int[] {c[0], yAt(l, c[0], c[1], (int) at[i][2], true) + 1, c[1]};
            }
            l.garrisons.add(g(p[0], p[1], p[2], packs[i]));
            reserve(l, p[0], p[1], p[2], 2);
        }
        // the drowned in the flooded holds, on the low side where the water stands deepest (two cells of it over the hold floor)
        double[][] wet = {{10, 7 * l.low}, {-24, 6 * l.low}, {32, 8 * l.low}};
        for (double[] w : wet) {
            int[] c = column(l, w[0], w[1]);
            if (cellAt(l, c[0], c[1], 3) != OPEN || cellAt(l, c[0], c[1], 4) != OPEN) continue;
            int y = yAt(l, c[0], c[1], 3, false);
            if (s.base + y + 1 <= Plans.SEA) l.garrisons.add(g(c[0], y, c[1], "guardian"));
        }
        // the fallen mast's gangway: it runs down over the side until it meets the sea floor or the shore (never more than 54 out, at no steeper than a block a block)
        Frame f = new Frame(null, s.x, s.z, s.base, s.rot);
        l.slope = Math.min(1.0, Math.max(0.75, (l.k + 12) / 38.0));
        l.mastEnd = 54;
        for (int k = 15; k <= 54; k++) {
            int ground = Integer.MIN_VALUE;
            for (int a = 0; a <= 1; a++) {
                int[] c = column(l, 14 + a, l.low * k);
                ground = Math.max(ground, s.surface(f.wx(c[0], c[1]), f.wz(c[0], c[1])) - s.base);
            }
            if (ground + 1 >= l.k + (int) Math.round(13 - (k - 13) * l.slope)) { l.mastEnd = k; break; }
        }
        // the ladders to the poop deck (from the hall) and to the forecastle deck (from the main deck), and the carved signs, each against the hull's own plating
        ladderUp(l, f, 46, 9, 18, 24, -1, 0);
        ladderUp(l, f, -35, -4, 13, 16, 0, 1);
        signAt(l, f, 37, 0, 22, 0, -1, "ALL HANDS\nTO THE DEEP");
        signAt(l, f, 34, 4, 15, 0, -1, "THE ADMIRAL\nRECEIVES\nNO ONE");
        signAt(l, f, -35, 3, 14, 0, 1, "THE CHOIR'S\nDREADNOUGHT\nSHE SAILED\nINTO THE DARK");
        signAt(l, f, 8, -13 * l.low, 13, l.low, 0, "SHE BROKE\nON THE REEF\nAND THE\nDEEP CAME IN");
        // the admiral's hoard below the stern windows, the helmsman's chest on the poop deck
        long h = s.hash;
        chestAt(l, 50, 0, 17, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.7");
        chestAt(l, 50, -3, 17, 0, -1, Sites.DESERT, "trinket:0.35");
        chestAt(l, 50, 3, 17, 0, -1, "minecraft:chests/stronghold_library", "lore:" + Hash.range(Hash.mix(h ^ 1), 0, 99));
        chestAt(l, 49, -6, 24, 0, -1, Sites.DUNGEON, null);
        // the cabins, the forecastle, the gun deck's powder room, the holds
        chestAt(l, 37, -7, 12, 1, 0, Sites.SMITH, null);
        chestAt(l, 47, 7, 12, -1, 0, "minecraft:chests/igloo_chest", "lore:" + Hash.range(Hash.mix(h ^ 2), 0, 99));
        chestAt(l, -43, -3, 12, 1, 0, Sites.CORRIDOR, null);
        chestAt(l, 22, -8, 7, 1, 0, "minecraft:chests/nether_bridge", "trinket:0.2");
        chestAt(l, 8, 5, 2, 0, 1, Sites.JUNGLE, null);
        chestAt(l, -16, -4, 2, 0, 1, "minecraft:chests/abandoned_mineshaft", null);
        chestAt(l, 33, 2, 2, 0, -1, Sites.DUNGEON, "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99));
        // the spawners: the hold (on its dry, high side), the gun deck, the forecastle
        cageAt(l, 27, -6 * l.low, 2, "ZOMBIE");
        cageAt(l, 16, 9, 7, "SKELETON");
        cageAt(l, -44, 2, 12, "SPIDER");
        // the darts under the gun deck's planks: a trap in the floor, its plate on top, only where the deck is dry
        double[][] darts = {{-26, 3}, {-10, -3}, {8, 3}, {26, -3}};
        for (double[] d : darts) {
            int[] c = spotOn(l, d[0], d[1], 7);
            if (c != null && s.base + c[1] > Plans.SEA) l.darts.add(c);
        }
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        L l = (L) plan;
        P p = new P(s, c, l);
        if (!p.hit(-56, -56, 56, 56)) return;
        hull(p, l);
        castle(p, l);
        holds(p, l);
        masts(p, l);
        fallen(p, l);
        bridge(p, l);
        figurehead(p, l);
        wreckage(p, l);
        tiles(p, l);
    }

    // ------------------------------------------------------------------------------------------------ the hull

    /** Every column of the wreck: the reef under it, its plating, its decks and rooms, flooded below the sea. */
    private static void hull(P p, L l) {
        if (!p.hit(-26, -56, 26, 54)) return;
        for (int u = p.ua(-26); u <= p.ub(26); u++)
            for (int v = p.va(-56); v <= p.vb(54); v++) {
                double[] q = locate(l, u, v);
                if (q == null) continue;
                double s = q[0], t = q[1], b = beam(s), at = Math.abs(t);
                if (b <= 0.3 || at > b + 0.5) continue;
                int o2 = (int) Math.round(q[2] * 2), oy = Math.floorDiv(o2, 2), y0 = l.k + oy;
                boolean half = (o2 & 1) != 0;
                int hb = (int) Math.round(4 * (at / b) * (at / b));
                double edge = q[3];
                int g = p.ground(u, v);
                for (int y = Math.max(-12, g + 1); y < y0 + hb; y++) p.set(u, y, v, RUB, 0);
                int prev = NONE;
                for (int h = hb; h <= 26; h++) {
                    int k = cell(s, t, h, b, hb), y = y0 + h;
                    if (k == NONE) { if (y <= g + 1 && y > y0 + hb) p.hollow(u, y, v); prev = k; continue; }
                    if (edge < 4 && !p.f.keep(u, y, v, edge / 4)) { prev = NONE; continue; }
                    switch (k) {
                        case SOLID: if (y < l.ws - 1 && !p.f.keep(u, y, v, 0.93)) p.hollow(u, y, v); else p.set(u, y, v, HULL, h); break;
                        case RAIL: p.set(u, y, v, IRON_BARS, 0); break;
                        case FLOOR: p.set(u, y, v, DECK, 0); break;
                        case BALLASTC: p.set(u, y, v, BALLAST, 0); break;
                        case WALLC: p.set(u, y, v, BULK, 0); break;
                        case GLAZE: p.set(u, y, v, PANE, 0); break;
                        case BARREL: p.set(u, y, v, WALL, 0); break;
                        case CANNON: p.set(u, y, v, CLAY, 15); break;
                        default:
                            if (prev == FLOOR && half) p.set(u, y, v, WSLAB, DARK_OAK);
                            else p.hollow(u, y, v);
                            break;
                    }
                    prev = k;
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ inside the hull

    /** A block at a point of the ship's frame (h above the keel), on that column's own list. */
    private static void put(P p, L l, double s, double t, int h, int id, int meta) {
        int[] c = column(l, s, t);
        if (!p.in(c[0], c[1])) return;
        int y = yAt(l, c[0], c[1], h, false);
        if (y != Integer.MIN_VALUE) p.set(c[0], y, c[1], id, meta);
    }

    /** The stern castle: its stair up from the cabins into the captain's hall, the hall's table, lamp and ladder. */
    private static void castle(P p, L l) {
        if (!p.hit(-12, 33, 12, 53)) return;
        // the stair: five steps up the starboard side of the cabin corridor, its well railed in the hall above
        for (int k = 0; k <= 4; k++)
            for (int t = 1; t <= 2; t++) {
                int[] c = column(l, 41 + k, t);
                if (!p.in(c[0], c[1])) continue;
                int y = yAt(l, c[0], c[1], 13 + k, false);
                for (int yy = yAt(l, c[0], c[1], 13, false); yy < y; yy++) p.set(c[0], yy, c[1], BULK, 0);
                p.set(c[0], y, c[1], BSTAIRS, p.f.stairs(0, 1, false));
                // headroom above the step; where it opens through the hall's floor, the half step of a listing deck goes too
                int top = k == 0 ? y + 3 : Math.max(y + 3, yAt(l, c[0], c[1], 18, false));
                for (int yy = y + 1; yy <= top; yy++) p.set(c[0], yy, c[1], AIR, 0);
            }
        for (int sv = 41; sv <= 44; sv++) put(p, l, sv, 0, 18, FENCE, 0);
        for (int t = 1; t <= 2; t++) put(p, l, 40, t, 18, FENCE, 0);
        // the admiral's table across the stern, his lamp, the ladder to the poop deck
        for (int t = -3; t <= 3; t++) { put(p, l, 48, t, 18, FENCE, 0); put(p, l, 48, t, 19, WSLAB, 8 + DARK_OAK); }
        for (int t = -3; t <= 3; t += 2) put(p, l, 49, t, 18, 164, p.f.stairs(0, 1, false));
        put(p, l, 44, 0, 22, SEA_LANTERN, 0);
        put(p, l, 44, 0, 23, IRON_BARS, 0);
        // the stern lantern over the transom, a glow in the cabin corridor
        put(p, l, 52, 0, 26, SEA_LANTERN, 0);
        put(p, l, 52, 0, 25, IRON_BARS, 0);
        put(p, l, 39, 0, 16, GLOWSTONE, 0);
        // bunks in the cabins
        for (int sv = 37; sv <= 49; sv += 6) for (int side = -1; side <= 1; side += 2) { put(p, l, sv, side * 6, 13, FENCE, 0); put(p, l, sv, side * 6, 14, WSLAB, DARK_OAK + 8); }
    }

    /** The holds and the gun deck: hatches and their ladders, cargo, the powder room's barrels, lamps. */
    private static void holds(P p, L l) {
        if (!p.hit(-26, -56, 26, 54)) return;
        double[][] hatches = {{24.5, 0}, {-13.5, 0}};
        for (double[] hz : hatches) {
            // the ladder climbs from the hold to the main deck through both decks (its own cell is the hatch, with floor on every side
            // to step off onto), backed by a post of planking one cell toward the stern, as tall as the ladder itself (the same y for
            // both, whatever the list does to the two columns)
            int[] c = column(l, hz[0] + 0.5, hz[1]);
            int yLo = yAt(l, c[0], c[1], 3, false), yHi = yAt(l, c[0], c[1], 12, false);
            if (p.in(c[0], c[1] + 1)) for (int y = yLo; y <= yHi; y++) p.set(c[0], y, c[1] + 1, BULK, 0);
            if (p.in(c[0], c[1])) {
                for (int y = yLo; y <= yHi; y++) p.set(c[0], y, c[1], LADDER, p.f.facing(0, -1));
                p.set(c[0], yHi + 1, c[1], AIR, 0);      // nothing, not even a deck's half step, caps the shaft
            }
        }
        // the ladders the plan set against the plating: up from the hall to the poop deck, up the forecastle's bulkhead to its deck
        for (int[] rung : l.ladders) if (p.in(rung[0], rung[2])) p.set(rung[0], rung[1], rung[2], LADDER, rung[3]);
        for (int[] top : l.tops) if (p.in(top[0], top[2])) p.set(top[0], top[1], top[2], AIR, 0);
        // cargo: crates in the holds
        double[][] crates = {{6, -6}, {14, 6}, {30, -6}, {-10, 5}, {-30, -5}, {44, -5}};
        for (double[] cr : crates)
            for (int a = 0; a <= 1; a++)
                for (int b = 0; b <= 1; b++)
                    for (int h = 3; h <= 4; h++) put(p, l, cr[0] + a, cr[1] + b, h, PLANKS, 1);
        // the powder room behind the bulkhead: barrels of slab and plank
        for (int sv = 21; sv <= 23; sv++) for (int t = -6; t <= -4; t += 2) { put(p, l, sv, t, 8, PLANKS, 1); put(p, l, sv, t, 9, WSLAB, 1); }
        // the lamps hang under the main deck on the hatch posts, and under the forecastle deck beside its door
        put(p, l, -12, 0, 11, SEA_LANTERN, 0);
        put(p, l, 26, 0, 11, SEA_LANTERN, 0);
        put(p, l, -40, 4, 15, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------ masts, sails and rigging

    /** A block at a point of the ship's frame (h above the keel), where the leaning mast stands. */
    private static void mastBlock(P p, L l, double s, double t, double h, int id, int meta) {
        int[] c = column(l, s, t);
        if (!p.in(c[0], c[1])) return;
        double[] q = locate(l, c[0], c[1]);
        if (q == null) return;
        p.set(c[0], l.k + (int) Math.round(q[2] + h), c[1], id, meta);
    }

    /** The three masts: stone banded in iron, leaning with the list; yards, torn stone sails, chain shrouds. */
    private static void masts(P p, L l) {
        if (!p.hit(-30, -56, 30, 54)) return;
        double[][] masts = {{-28, 42, 33, 24}, {12, 30, 0, 0}, {30, 38, 29, 0}};   // station, top, upper yard, lower yard
        for (double[] m : masts) {
            double s0 = m[0], roll = s0 >= 2 ? l.rollS : l.rollB;
            int top = (int) m[1];
            for (int h = 3; h <= top; h++) {
                double tm = -(h - 12) * roll;
                for (int a = 0; a <= 1; a++)
                    for (int b = 0; b <= 1; b++) mastBlock(p, l, s0 + a, tm + b, h, h % 6 == 0 ? CLAY : MAST, h % 6 == 0 ? 15 : 0);
            }
            if (m[0] == 12) { mastBlock(p, l, s0, -(top - 12) * roll, top + 1, SLAB, 5); continue; }
            for (int y = 2; y <= 3; y++) {
                int hy = (int) m[y];
                if (hy == 0) continue;
                int span = y == 2 ? 7 : 9;
                double tm = -(hy - 12) * roll;
                for (int t = -span; t <= span; t++) mastBlock(p, l, s0 + 0.5, tm + t, hy + t * roll * 0 , WALL, 0);
                // the sail hanging under it, bellied, torn below
                for (int t = -span + 1; t <= span - 1; t++)
                    for (int d = 1; d <= 9; d++) {
                        double hh = hy - d;
                        int[] c = column(l, s0 - 1 + (Math.abs(t) < span - 2 && d > 2 && d < 8 ? -1 : 0), tm + t);
                        if (!p.in(c[0], c[1])) continue;
                        if (d >= 7 && p.f.roll(c[0], (int) hh, c[1], 81) < 0.35 + 0.15 * (d - 7)) continue;
                        if (!p.f.keep(c[0], (int) hh, c[1], 0.82)) continue;
                        mastBlock(p, l, s0 - 1 + (Math.abs(t) < span - 2 && d > 2 && d < 8 ? -1 : 0), tm + t, hh, SAIL, 0);
                    }
            }
            // the chain shrouds from the masthead down to the bulwarks on either side
            double tTop = -(top - 4 - 12) * roll;
            for (int side = -1; side <= 1; side += 2)
                for (int fa = -1; fa <= 1; fa += 2) {
                    double s1 = s0 + fa * 6, t1 = side * (beam(s1) - 1), h1 = 14, h0 = top - 4;
                    int steps = (int) Math.ceil(Math.max(Math.abs(t1 - tTop), Math.abs(h1 - h0)) * 1.2);
                    for (int k = 0; k <= steps; k++) {
                        double f = k / (double) steps;
                        mastBlock(p, l, s0 + (s1 - s0) * f, tTop + (t1 - tTop) * f, h0 + (h1 - h0) * f, IRON_BARS, 0);
                    }
                }
        }
        // the crow's nest on the foremast: a ring of dark oak planking round the mast (which runs on through it), fenced
        double tn = -(35 - 12) * l.rollB;
        for (int a = -1; a <= 2; a++) for (int b = -1; b <= 2; b++) {
            if (a >= 0 && a <= 1 && b >= 0 && b <= 1) continue;
            mastBlock(p, l, -28 + a, tn + b, 35, PLANKS, DARK_OAK);
            mastBlock(p, l, -28 + a, tn + b, 36, FENCE, 0);
        }
    }

    /** The y of the fallen mast's block at step k (column a of its two): on the deck, through the hull's side, or down over the sea floor. */
    private static int mastY(P p, L l, int k, int a) {
        double hh = k <= 13 ? 13 : 13 - (k - 13) * l.slope;
        int[] c = column(l, 14 + a, l.low * k);
        double[] q = locate(l, c[0], c[1]);
        return q != null && Math.abs(q[1]) <= beam(q[0]) ? l.k + (int) Math.round(q[2] + hh) : Math.max(p.ground(c[0], c[1]) + 1, l.k + (int) Math.round(hh));
    }

    /** The mainmast's upper half, snapped at thirty and fallen across the deck and over the side, down to the sea floor or the shore: the way aboard. */
    private static void fallen(P p, L l) {
        if (!p.hit(-56, 5, 56, 20)) return;
        int side = l.low;
        for (int k = 0; k <= l.mastEnd; k++)
            for (int a = 0; a <= 1; a++) {
                int[] c = column(l, 14 + a, side * k);
                if (!p.in(c[0], c[1])) continue;
                double[] q = locate(l, c[0], c[1]);
                int y = mastY(p, l, k, a);
                p.set(c[0], y, c[1], MAST, 0);
                // where it crosses the bulwark the rail gives way, so that one can walk it
                if (q != null && Math.abs(q[1]) <= beam(q[0]) && k >= 12) { p.set(c[0], y + 1, c[1], AIR, 0); p.set(c[0], y + 2, c[1], AIR, 0); }
            }
        // a yard lying across it near its end, on either side of the walkway
        int yk = Math.min(26, Math.max(16, l.mastEnd - 2)), yy = mastY(p, l, yk, 0);
        for (int s = 8; s <= 20; s++) {
            if (s == 14 || s == 15) continue;
            int[] c = column(l, s, side * yk);
            if (p.in(c[0], c[1])) p.set(c[0], yy, c[1], WALL, 0);
        }
        // its torn sail draped over the bulwark
        for (int s = 10; s <= 18; s++)
            for (int d = 0; d <= 4; d++) {
                double t = side * (14 + d * 0.4);
                int[] c = column(l, s, t);
                if (!p.in(c[0], c[1]) || !p.f.keep(c[0], d, c[1], 0.75)) continue;
                double[] q = locate(l, c[0], c[1]);
                if (q == null) continue;
                p.set(c[0], l.k + (int) Math.round(q[2] + 13 - d * 1.5), c[1], SAIL, 0);
            }
    }

    /** The foremast's snapped yard, fallen across the break: a bridge, two blocks wide and flush with both main decks, from the stern half to the bow half. */
    private static void bridge(P p, L l) {
        if (!p.hit(-14, -22, 14, 12)) return;
        double tb = -3 * l.low;                    // on the high side of the list, clear of the hatch and the fallen mainmast
        int[] a = column(l, 8, tb), b = column(l, -14, tb);
        int ya = yAt(l, a[0], a[1], 12, false), yb = yAt(l, b[0], b[1], 12, false);
        if (ya == Integer.MIN_VALUE || yb == Integer.MIN_VALUE) return;
        for (int k = 0; k <= 22; k++) {
            int[] c = column(l, 8 - k, tb);
            int y = (int) Math.round(ya + (yb - ya) * k / 22.0);
            for (int w = 0; w <= 1; w++) if (p.in(c[0] + w, c[1])) p.set(c[0] + w, y, c[1], MAST, 0);
        }
    }

    /** The figurehead under the bowsprit: a deep one with lantern eyes and iron tentacles, leaning out over the sea. */
    private static void figurehead(P p, L l) {
        if (!p.hit(-26, -56, 26, -40)) return;
        for (int k = 0; k <= 8; k++) {
            double s = -48.5 - k * 0.55, h = 6 + k;
            mastBlock(p, l, s, 0, h, PRISMARINE, 2);
            if (k >= 2 && k <= 6) { mastBlock(p, l, s, -1, h, PRISMARINE, 2); mastBlock(p, l, s, 1, h, PRISMARINE, 2); }
        }
        double s = -53.5;
        mastBlock(p, l, s, 0, 15, PRISMARINE, 1);
        mastBlock(p, l, s - 0.6, -1, 15, SEA_LANTERN, 0);
        mastBlock(p, l, s - 0.6, 1, 15, SEA_LANTERN, 0);
        mastBlock(p, l, s, 0, 16, PRISMARINE, 1);
        for (int k = 1; k <= 4; k++) { mastBlock(p, l, s - 0.5, -1, 15 - k, IRON_BARS, 0); mastBlock(p, l, s - 0.5, 1, 15 - k, IRON_BARS, 0); }
        // the bowsprit
        for (int k = 0; k <= 7; k++) mastBlock(p, l, -47 - k, 0, 16 + k * 0.5, WALL, 0);
        // the anchor chain from the hawse hole down to the anchor on the sea floor
        int[] hawse = column(l, -46, 4);
        int[] anchor = {hawse[0] + 4, hawse[1] - 3};
        if (p.hit(Math.min(hawse[0], anchor[0]) - 2, Math.min(hawse[1], anchor[1]) - 2, Math.max(hawse[0], anchor[0]) + 2, Math.max(hawse[1], anchor[1]) + 2)) {
            int yTop = yAt(l, hawse[0], hawse[1], 10, false), yBot = p.ground(anchor[0], anchor[1]) + 1;
            int steps = Math.max(1, yTop - yBot);
            for (int k = 0; k <= steps; k++) {
                int u = hawse[0] + (anchor[0] - hawse[0]) * k / steps, v = hawse[1] + (anchor[1] - hawse[1]) * k / steps;
                p.set(u, yTop - k, v, IRON_BARS, 0);
            }
            for (int d = -2; d <= 2; d++) p.set(anchor[0] + d, yBot, anchor[1], OBSIDIAN, 0);
            p.set(anchor[0] - 2, yBot + 1, anchor[1], OBSIDIAN, 0);
            p.set(anchor[0] + 2, yBot + 1, anchor[1], OBSIDIAN, 0);
            for (int y = yBot + 1; y <= yBot + 4; y++) p.set(anchor[0], y, anchor[1], OBSIDIAN, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the wreckage

    /** On the sea floor: crates, cannons, chests' boats, plates of the hull, an anchor, the bones of the crew. */
    private static void wreckage(P p, L l) {
        for (int[] d : l.debris) {
            int u = d[1], v = d[2];
            if (!p.hit(u - 4, v - 4, u + 4, v + 4)) continue;
            int g = p.ground(u, v) + 1;
            switch (d[0]) {
                case 0:
                    for (int a = 0; a <= 1; a++) for (int b = 0; b <= 1; b++) for (int y = 0; y <= d[3] % 2; y++) p.set(u + a, g + y, v + b, PLANKS, 1);
                    break;
                case 1: {
                    int dx = d[3] % 2 == 0 ? 1 : 0, dz = 1 - dx;
                    p.set(u, g, v, CLAY, 15);
                    p.set(u + dx, g, v + dz, CLAY, 15);
                    p.set(u + 2 * dx, g, v + 2 * dz, WALL, 0);
                    break;
                }
                case 2: {
                    int dx = d[3] % 2 == 0 ? 1 : 0, dz = 1 - dx;
                    for (int k = -3; k <= 3; k++)
                        for (int w = -1; w <= 1; w++) {
                            int bu = u + k * dx + w * dz, bv = v + k * dz + w * dx;
                            boolean side = Math.abs(w) == 1 || Math.abs(k) == 3;
                            int gy = p.ground(bu, bv) + 1;
                            p.set(bu, gy, bv, side ? PLANKS : WSLAB, side ? 5 : 5);
                            if (side && Math.abs(k) < 3 && p.f.keep(bu, gy + 1, bv, 0.6)) p.set(bu, gy + 1, bv, PLANKS, 5);
                        }
                    break;
                }
                case 3:
                    p.set(u, g, v, BONE, 0);
                    p.set(u + 1, g, v, SKULL, 1);
                    break;
                case 4:
                    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (p.f.keep(u + a, g, v + b, 0.7)) p.set(u + a, g + (a == 0 && b == 0 ? 1 : 0), v + b, HULL, d[4]);
                    break;
                default:
                    p.set(u, g, v, HULL, d[4]);
                    if (d[4] % 3 == 0) p.set(u, g + 1, v, HULL, d[4] + 1);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ chests, spawners, carvings, darts

    private static void tiles(P p, L l) {
        // the chests, the spawners and the carved signs, each in the spot the plan found for it
        for (Object[] c : l.chests)
            if (p.in((Integer) c[0], (Integer) c[2])) p.f.chest((Integer) c[0], (Integer) c[1], (Integer) c[2], (Integer) c[3], (Integer) c[4], (String) c[5], (String) c[6]);
        for (Object[] c : l.cages)
            if (p.in((Integer) c[0], (Integer) c[2])) p.f.spawner((Integer) c[0], (Integer) c[1], (Integer) c[2], (String) c[3]);
        for (Object[] c : l.signs)
            if (p.in((Integer) c[0], (Integer) c[2])) p.f.sign((Integer) c[0], (Integer) c[1], (Integer) c[2], (Integer) c[3], (Integer) c[4], (String) c[5]);
        // the darts under the gun deck's planks: a trap in the floor, its plate on top
        for (int[] d : l.darts)
            if (p.in(d[0], d[2])) {
                p.f.dispenser(d[0], d[1] - 1, d[2], 0, 0);
                p.f.set(d[0], d[1], d[2], PLATE);
            }
        // chests among the wreckage on the sea floor
        int n = 0;
        for (int[] d : l.debris) {
            if (d[0] != 0 || n >= 4) continue;
            n++;
            if (p.in(d[1], d[2] - 1)) p.f.chest(d[1], p.ground(d[1], d[2] - 1) + 1, d[2] - 1, 0, -1, n % 2 == 0 ? Sites.JUNGLE : "minecraft:chests/abandoned_mineshaft", n == 1 ? "trinket:0.25" : null);
        }
    }

    // ------------------------------------------------------------------------------------------------ the painter

    /** A chunk-clipped painter in the structure's local frame (u across, v front to back, y above the base). */
    private static final class P {
        final Plans.GreatSite s;
        final Canvas c;
        final L l;
        final Frame f;
        final int u0, u1, v0, v1;
        private final int[] gnd = new int[256];

        P(Plans.GreatSite s, Canvas c, L l) {
            this.s = s;
            this.c = c;
            this.l = l;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int ax = c.x0 - s.x, az = c.z0 - s.z, bx = ax + 15, bz = az + 15;
            switch (s.rot & 3) {
                case 1: u0 = az; u1 = bz; v0 = -bx; v1 = -ax; break;
                case 2: u0 = -bx; u1 = -ax; v0 = -bz; v1 = -az; break;
                case 3: u0 = -bz; u1 = -az; v0 = ax; v1 = bx; break;
                default: u0 = ax; u1 = bx; v0 = az; v1 = bz; break;
            }
            Arrays.fill(gnd, Integer.MIN_VALUE);
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }
        int ua(int a) { return Math.max(a, u0); }
        int ub(int b) { return Math.min(b, u1); }
        int va(int a) { return Math.max(a, v0); }
        int vb(int b) { return Math.min(b, v1); }

        /** The land's height above the base at a column (from the site's plan, never from the canvas). */
        int ground(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v), i = (wx - c.x0) << 4 | (wz - c.z0);
            if (wx < c.x0 || wx > c.x0 + 15 || wz < c.z0 || wz > c.z0 + 15) return s.surface(wx, wz) - s.base;
            int g = gnd[i];
            if (g == Integer.MIN_VALUE) gnd[i] = g = s.surface(wx, wz) - s.base;
            return g;
        }

        /** Water below the sea's surface, air above it. */
        void hollow(int u, int y, int v) { f.set(u, y, v, s.base + y <= Plans.SEA ? H2O : AIR, 0); }

        void set(int u, int y, int v, int id, int meta) {
            if (y < -12) return;
            if (id != AIR && l.free.contains(key(u, y, v))) { hollow(u, y, v); return; }   // a boss's, a garrison's or a chest's room stays open
            if (id >= 0) { f.set(u, y, v, id, meta); return; }
            double r;
            switch (id) {
                case RUB: f.rubble(u, y, v); break;
                case HULL:
                    // the plating: dark prismarine and stone brick, plates of black iron, two dark wales
                    r = f.roll(u, y, v, 91);
                    if (meta == 6 || meta == 12) f.set(u, y, v, r < 0.6 ? CLAY : OBSIDIAN, 15);
                    else if (r < 0.38) f.set(u, y, v, PRISMARINE, 2);
                    else if (r < 0.55) f.set(u, y, v, BRICK, 0);
                    else if (r < 0.66) f.set(u, y, v, BRICK, 2);
                    else if (r < 0.76) f.set(u, y, v, STONE, 6);
                    else if (r < 0.88) f.set(u, y, v, CLAY, 15);
                    else if (r < 0.95) f.set(u, y, v, COBBLE);
                    else f.set(u, y, v, MOSSY);
                    break;
                case DECK:
                    r = f.roll(u, y, v, 92);
                    if (r < 0.08 && !l.whole.contains(key(u, y, v))) hollow(u, y, v);   // a rotted plank, but never under a boss, a garrison or a chest
                    else if (r < 0.70) f.set(u, y, v, PLANKS, DARK_OAK);
                    else if (r < 0.88) f.set(u, y, v, PLANKS, 1);
                    else f.set(u, y, v, BRICK, 2);
                    break;
                case BULK:
                    r = f.roll(u, y, v, 93);
                    f.set(u, y, v, r < 0.6 ? PLANKS : BRICK, r < 0.6 ? DARK_OAK : 0);
                    break;
                case BALLAST:
                    r = f.roll(u, y, v, 94);
                    f.set(u, y, v, r < 0.5 ? COBBLE : r < 0.8 ? GRAVEL : MOSSY);
                    break;
                case MAST:
                    r = f.roll(u, y, v, 95);
                    f.set(u, y, v, r < 0.5 ? STONE : BRICK, r < 0.5 ? 6 : 0);
                    break;
                case SAIL:
                    r = f.roll(u, y, v, 96);
                    f.set(u, y, v, CLAY, r < 0.55 ? 0 : r < 0.85 ? 8 : 12);
                    break;
                default: break;
            }
        }

        void box(int a0, int b0, int a1, int b1, int y0, int y1, int id, int meta) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, meta);
        }
    }
}
