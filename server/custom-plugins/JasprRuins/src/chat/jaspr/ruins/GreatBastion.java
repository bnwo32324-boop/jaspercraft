package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Y'ha-nthlei, Bastion of the Deep (epoch 6): the sea fortress of the Deep Ones, standing on the sea floor (or a drowned
 * shore) with its sea-gate turned to the open water. A curving curtain of prismarine and dark prismarine rises from the
 * floor like a reef, ribbed with coral, crowned by seven round towers with horns of coral; inside, the flooded harbour
 * and its piers, the breeding pool where the guardians are spawned (a pit down into the sea floor full of glowing eggs),
 * the barracks with their brine troughs and armoury, the stepped temple of Dagon (its sanctum, its summit shrine) with
 * the fish-god's idol standing in the water before it, the coral garden, and at the back the war-hall: a dry ribbed hall
 * on its platform above the sea, under a hull-shaped roof and a spiralling coral spire, where the Warlord waits; under
 * its floor, dry behind the platform walls, the cells of the drowned.
 * <p>
 * Every level hangs from the sea's surface (local {@code W}, the top water block), so the bastion works on a shore and
 * thirty blocks down alike: the courtyard is dug at least three blocks under the surface and refilled with water, the
 * piers lie one above it, the platforms two to four. Whatever is hollowed below the surface is water, except the
 * sanctum and the cells, which are enclosed in solid platforms.
 */
final class GreatBastion extends GreatDesign {
    static final int R = 52;
    private static final String TREASURE = "minecraft:chests/end_city_treasure", CROSSING = "minecraft:chests/stronghold_crossing",
        MINESHAFT = "minecraft:chests/abandoned_mineshaft", IGLOO = "minecraft:chests/igloo_chest", NETHER = "minecraft:chests/nether_bridge";
    /** The ring's towers (degrees from local +u toward +v; the sea-gate is at -90): the two gate towers first. */
    static final int[] TOWER_DEG = {-103, -77, -150, -30, 150, 30, 90};

    static final class Plan extends Layout {
        /** The frame's turn (the sea-gate faces the deepest water) and the local y of the sea's top water block. */
        int rot, W;
        double p1, p2;
        long h;
        /** Tower centres: u, v, top (local y). */
        final int[][] towers = new int[TOWER_DEG.length][];
    }

    // ------------------------------------------------------------------------------------------------------ the plan
    @Override Layout plan(Plans.GreatSite s, Random r) {
        Plan p = new Plan();
        p.h = s.hash;
        p.rot = seaward(s);
        p.W = Plans.SEA - s.base;
        p.p1 = r.nextDouble() * 2 * Math.PI;
        p.p2 = r.nextDouble() * 2 * Math.PI;
        int W = p.W;
        for (int i = 0; i < TOWER_DEG.length; i++) {
            double th = Math.toRadians(TOWER_DEG[i]), rr = wallR(p, th) - 2;
            p.towers[i] = new int[] {(int) Math.round(rr * Math.cos(th)), (int) Math.round(rr * Math.sin(th)), W + (i < 2 ? 28 : 24)};
        }
        int t = (p.rot - s.rot) & 3;
        p.boss = turn(t, 0, W + 5, 25);
        // the harbour, the gate and the breeding pool
        add(p, t, 0, W - 1, -40, "guardian");
        add(p, t, -10, W - 1, -30, "guardian");
        add(p, t, 16, -6, -22, "guardian+guardian");
        add(p, t, 19, -8, -25, "guardian");
        add(p, t, -7, W + 2, -40, "deep_one+zombie");
        add(p, t, 7, W + 2, -40, "deep_one+husk");
        add(p, t, 0, W + 2, -12, "!deep_one+zombie_villager");
        add(p, t, -12, W + 2, -20, "deep_one+slime");
        add(p, t, 14, W + 2, -12, "shoggoth+slime");
        // the temple of Dagon
        add(p, t, -25, W + 3, -8, "cult_zealot+vindicator");
        add(p, t, -25, W + 3, 8, "mi_go+enderman");
        add(p, t, -24, W + 3, -2, "witch+illusioner");
        add(p, t, -25, W + 12, 2, "!cult_adept+evoker");
        // the barracks, the war-hall and the cells
        add(p, t, 24, W + 3, -4, "deep_one+zombie");
        add(p, t, 24, W + 3, 14, "skeleton+stray");
        add(p, t, -6, W + 5, 24, "!star_spawn+wither_skeleton");
        add(p, t, 6, W + 5, 24, "deep_one+hound");
        add(p, t, 0, W - 2, 27, "ghoul+tomb_crawler");
        add(p, t, 8, W - 2, 27, "silverfish+endermite");
        add(p, t, -8, W - 2, 27, "cave_spider+spider");
        // the wall walk and a tower top
        for (int k = 0; k < 2; k++) {
            double th = Math.toRadians(k == 0 ? -50 : 120), rr = wallR(p, th) - 1.5;
            add(p, t, (int) Math.round(rr * Math.cos(th)), W + 11, (int) Math.round(rr * Math.sin(th)), k == 0 ? "skeleton+stray" : "creeper+spider");
        }
        add(p, t, p.towers[6][0] + 1, p.towers[6][2] + 1, p.towers[6][1], "nightgaunt");
        return p;
    }

    /** The ring's radius at an angle: a reef's wobble, smoothed flat round the sea-gate. */
    static double wallR(Plan p, double th) {
        double d = Math.abs(Math.IEEEremainder(th + Math.PI / 2, 2 * Math.PI));
        double k = Math.max(0, Math.min(1, (d - 0.3) / 0.4));
        return 47 + k * (1.8 * Math.sin(3 * th + p.p1) + 0.8 * Math.sin(5 * th + p.p2));
    }

    /** The frame whose front (local -v, the sea-gate) faces the deepest water; the site's own turn where it is even. */
    static int seaward(Plans.GreatSite s) {
        int[] sum = new int[4];   // the floor under local -v for each turn: 0 -z, 1 +x, 2 +z, 3 -x
        for (int d = 30; d <= 50; d += 10)
            for (int w = -20; w <= 20; w += 20) {
                sum[0] += s.surface(s.x + w, s.z - d);
                sum[1] += s.surface(s.x + d, s.z + w);
                sum[2] += s.surface(s.x + w, s.z + d);
                sum[3] += s.surface(s.x - d, s.z + w);
            }
        int best = s.rot & 3;
        for (int k = 0; k < 4; k++) if (sum[k] < sum[best] - 9) best = k;
        return best;
    }

    private static void add(Plan p, int t, int u, int y, int v, String pack) {
        int[] w = turn(t, u, y, v);
        p.garrisons.add(g(w[0], w[1], w[2], pack));
    }

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
        int W = p.W;
        ring(q, p);
        // the islands: the quays, the idol's pedestal, the temple's base, the barracks' and the war-hall's platforms
        island(q, p, -10, -43, -4, -37, W + 1);
        island(q, p, 4, -43, 10, -37, W + 1);
        island(q, p, -26, -24, -18, -16, W + 2);
        island(q, p, -34, -9, -16, 9, W + 2);
        island(q, p, 17, -8, 31, 18, W + 2);
        island(q, p, -15, 10, 15, 36, W + 4);
        // the piers
        pier(q, p, -10, -36, 10, -36);
        pier(q, p, -1, -35, 1, 6);
        pier(q, p, -17, -21, -2, -19);
        pier(q, p, 2, -23, 6, -21);
        pier(q, p, -12, -1, 16, 1);
        breedingPool(q, p);
        temple(q, p);
        idol(q, p, -22, W + 2, -20);
        effigy(q, -6, W + 2, -38);
        effigy(q, 6, W + 2, -38);
        barracks(q, p);
        warHall(q, p);
        spire(q, p);
        cells(q, p);
        for (int[] tw : p.towers) tower(q, p, tw);
        tiles(q, p);
    }



    // ------------------------------------------------------------------------------------------------------ materials
    /** The Deep Ones' masonry: dark prismarine and prismarine brick, worn rough, with old stone in the cracks. */
    private static void deep(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 81);
        if (r < 0.46) f.set(u, y, v, PRISMARINE, 2);
        else if (r < 0.76) f.set(u, y, v, PRISMARINE, 1);
        else if (r < 0.88) f.set(u, y, v, PRISMARINE, 0);
        else if (r < 0.95) f.set(u, y, v, BRICK, 1);
        else f.set(u, y, v, BRICK, 2);
    }

    /** Living coral stone: rough prismarine mottled dark. */
    private static void coral(Frame f, int u, int y, int v) {
        double r = f.roll(u, y, v, 82);
        f.set(u, y, v, PRISMARINE, r < 0.6 ? 0 : r < 0.85 ? 2 : 1);
    }

    /** Water at or under the sea's surface, air above it. */
    private static void hollow(Frame f, int W, int u, int y, int v) { f.set(u, y, v, y <= W ? Canvas.WATER : AIR); }

    // ------------------------------------------------------------------------------------------------------ the ring
    /** Every column inside the ring: the courtyard dug and flooded, the curtain wall with its ribs and the sea-gate. */
    private static void ring(Pen q, Plan p) {
        Frame f = q.f;
        int W = p.W;
        for (int u = q.u0; u <= q.u1; u++)
            for (int v = q.v0; v <= q.v1; v++) {
                double r = Math.sqrt(u * u + v * v), th = Math.atan2(v, u), rw = wallR(p, th);
                if (r > rw + 1.5) continue;
                int G = q.land(u, v);
                if (r <= rw - 2.5) { court(q, p, u, v, G, r, rw); continue; }
                boolean gateZone = v < 0 && Math.abs(u) <= 7;
                if (r > rw + 0.5) {                                   // a coral rib down the outer face
                    if (!gateZone && Math.abs(Math.IEEEremainder(Math.toDegrees(th), 12)) < 1.7)
                        for (int y = Math.min(G, W - 3); y <= W + 8; y++) coral(f, u, y, v);
                    continue;
                }
                int foot = Math.min(G, W - 3), arc = (int) Math.floor(th * rw);
                boolean parapet = r > rw - 0.5, inner = r <= rw - 1.5;
                boolean gate = v < 0 && Math.abs(u) <= 5, jamb = v < 0 && Math.abs(u) == 6;
                int arch = W + 5 - u * u / 7;
                for (int y = foot; y <= W + 10; y++) {
                    if (gate && y > foot && y <= arch) { hollow(f, W, u, y, v); continue; }
                    if (gate && y == foot) { f.set(u, y, v, PRISMARINE, 1); continue; }
                    if (gate && y == arch + 1) { f.set(u, y, v, (u == 0 && parapet) ? SEA_LANTERN : PRISMARINE, 1); continue; }
                    if (jamb && y <= W + 7) { f.set(u, y, v, PRISMARINE, 2); continue; }
                    if (inner && y == W - 1 && Math.floorMod(arc, 24) == 0) { f.set(u, y, v, SEA_LANTERN); continue; }
                    if (y == W + 1 || y == W + 10) f.set(u, y, v, PRISMARINE, y == W + 1 ? 2 : 1);
                    else deep(f, u, y, v);
                }
                if (parapet && Math.floorMod(arc, 2) == 0) deep(f, u, W + 11, v);
                else f.set(u, W + 11, v, AIR);
                if (G > W + 10) f.clear(u, v, W + 12, G + 2);
            }
    }

    /** A courtyard column: paved at least three under the surface, flooded to it, open above; coral in the garden. */
    private static void court(Pen q, Plan p, int u, int v, int G, double r, double rw) {
        Frame f = q.f;
        int W = p.W, cf = Math.min(G, W - 3);
        double c = f.roll(u, cf, v, 83);
        if (c < 0.14) f.set(u, cf, v, GRAVEL);
        else if (c < 0.22) f.set(u, cf, v, 82, 0);                          // clay
        else f.set(u, cf, v, PRISMARINE, Math.floorMod(u + 2 * v, 7) == 0 ? 2 : 1);
        if (G <= W) for (int y = cf + 1; y <= G; y++) f.set(u, y, v, Canvas.WATER);
        else { for (int y = cf + 1; y <= W; y++) f.set(u, y, v, Canvas.WATER); f.clear(u, v, W + 1, G + 2); }
        if (r < rw - 5 && f.roll(u, 0, v, 84) < (u < -14 && v > 12 ? 0.09 : 0.025)) {   // reefs, thickest in the coral garden
            int hgt = 2 + (int) (f.roll(u, 1, v, 85) * Math.max(1, Math.min(10, W - cf - 3)));
            for (int y = cf + 1; y <= cf + hgt && y <= W - 2; y++) coral(f, u, y, v);
            if (cf + hgt <= W - 3 && f.roll(u, 2, v, 86) < 0.2) f.set(u, cf + hgt + 1, v, SEA_LANTERN);
        }
    }

    /** A solid island from the courtyard floor to {@code top}, faced with deep masonry; the land cut back above it. */
    private static void island(Pen q, Plan p, int a0, int b0, int a1, int b1, int top) {
        if (!q.hit(a0, b0, a1, b1)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, a0); u <= Math.min(q.u1, a1); u++)
            for (int v = Math.max(q.v0, b0); v <= Math.min(q.v1, b1); v++) {
                int G = q.land(u, v), cf = Math.min(G, p.W - 3);
                boolean edge = u == a0 || u == a1 || v == b0 || v == b1;
                for (int y = cf + 1; y <= top; y++) {
                    if (y == top) f.set(u, y, v, PRISMARINE, edge ? 2 : 1);
                    else if (edge) deep(f, u, y, v);
                    else f.set(u, y, v, PRISMARINE, 2);
                }
                if (G > top) f.clear(u, v, top + 1, G + 2);
            }
    }

    /** A pier one above the surface, its deck edged dark, on posts every five blocks. */
    private static void pier(Pen q, Plan p, int a0, int b0, int a1, int b1) {
        if (!q.hit(a0, b0, a1, b1)) return;
        Frame f = q.f;
        int W = p.W;
        boolean along = a1 - a0 >= b1 - b0;
        for (int u = Math.max(q.u0, a0); u <= Math.min(q.u1, a1); u++)
            for (int v = Math.max(q.v0, b0); v <= Math.min(q.v1, b1); v++) {
                boolean edge = along ? v == b0 || v == b1 : u == a0 || u == a1;
                f.set(u, W + 1, v, PRISMARINE, edge ? 2 : 1);
                if (edge && Math.floorMod(along ? u : v, 5) == 0) {
                    int cf = Math.min(q.land(u, v), W - 3);
                    for (int y = cf + 1; y <= W; y++) f.set(u, y, v, PRISMARINE, 2);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the breeding pool
    /** The breeding pool: a pit down into the sea floor full of glowing eggs, a ring of piers and spires of coral round it. */
    private static void breedingPool(Pen q, Plan p) {
        int W = p.W, cu = 16, cv = -22;
        if (!q.hit(cu - 11, cv - 11, cu + 11, cv + 11)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, cu - 11); u <= Math.min(q.u1, cu + 11); u++)
            for (int v = Math.max(q.v0, cv - 11); v <= Math.min(q.v1, cv + 11); v++) {
                int du = u - cu, dv = v - cv, d2 = du * du + dv * dv;
                int cf = Math.min(q.land(u, v), W - 3);
                if (d2 <= 56) {                                        // the pit (r 7.5)
                    f.set(u, -10, v, PRISMARINE, 2);
                    for (int y = -9; y <= cf; y++) f.set(u, y, v, Canvas.WATER);
                } else if (d2 <= 72) {                                 // its lining (r 8.5)
                    for (int y = -10; y < cf; y++) f.set(u, y, v, PRISMARINE, 2);
                    f.set(u, cf, v, PRISMARINE, 1);
                } else if (d2 >= 81 && d2 <= 110) {                    // the ring of piers (r 9 to 10.5)
                    f.set(u, W + 1, v, PRISMARINE, d2 >= 100 ? 2 : 1);
                }
            }
        // the eggs: lanterns under shells of rough prismarine, and the hoard in the middle
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            int eu = cu + (int) Math.round(4 * Math.cos(a)), ev = cv + (int) Math.round(4 * Math.sin(a));
            if (!q.in(eu, ev)) continue;
            f.set(eu, -9, ev, SEA_LANTERN);
            f.set(eu, -8, ev, PRISMARINE, 0);
        }
        // the spires round the ring, two of them lit, and the posts under the ring
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6;
            int su = cu + (int) Math.round(9.6 * Math.cos(a)), sv = cv + (int) Math.round(9.6 * Math.sin(a));
            if (!q.in(su, sv)) continue;
            int cf = Math.min(q.land(su, sv), W - 3);
            for (int y = cf + 1; y <= W + 7; y++) {
                if (y == W + 5 && (k == 1 || k == 4)) f.set(su, y, sv, SEA_LANTERN);
                else coral(f, su, y, sv);
            }
            f.set(su, W + 8, sv, WALL, 1);
        }
    }

    // ------------------------------------------------------------------------------------------------ the temple of Dagon
    /** The stepped temple of Dagon: three tiers on its base, a stair up the east face, the sanctum within, the summit shrine. */
    private static void temple(Pen q, Plan p) {
        int W = p.W;
        if (!q.hit(-34, -9, -12, 9)) return;
        Frame f = q.f;
        int[][] tiers = {{7, W + 3, W + 5}, {5, W + 6, W + 8}, {3, W + 9, W + 11}};
        for (int[] t : tiers)
            for (int u = Math.max(q.u0, -25 - t[0]); u <= Math.min(q.u1, -25 + t[0]); u++)
                for (int v = Math.max(q.v0, -t[0]); v <= Math.min(q.v1, t[0]); v++)
                    for (int y = t[1]; y <= t[2]; y++) {
                        boolean band = y == t[2];
                        if (band) f.set(u, y, v, PRISMARINE, 1);
                        else deep(f, u, y, v);
                    }
        // the stair up the east face
        for (int j = 0; j <= 9; j++) {
            int u = -13 - j, y = W + 2 + j;
            for (int v = -1; v <= 1; v++) {
                if (!q.in(u, v)) continue;
                int cf = Math.min(q.land(u, v), W - 3);
                for (int yy = cf + 1; yy < y; yy++) f.set(u, yy, v, PRISMARINE, 2);
                f.set(u, y, v, BRICK_STAIRS, f.stairs(-1, 0, false));
                f.clear(u, v, y + 1, y + 3);
            }
            for (int v = -2; v <= 2; v += 4) {                       // its balustrades
                if (!q.in(u, v) || j == 0) continue;
                int cf = Math.min(q.land(u, v), W - 3);
                for (int yy = cf + 1; yy <= y; yy++) f.set(u, yy, v, PRISMARINE, 2);
                f.set(u, y + 1, v, WALL, 1);
            }
        }
        // the sanctum in the heart of the temple, its door from the north terrace
        q.box(-28, W + 3, -4, -22, W + 7, 4, b(AIR, 0));
        q.box(-28, W + 2, -4, -22, W + 2, 4, b(PRISMARINE, 1));
        for (int u = -28; u <= -22; u++) for (int v = -4; v <= 4; v++) if (((u + v) & 1) == 0) q.set(u, W + 2, v, PRISMARINE, 2);
        q.box(-25, W + 3, -7, -25, W + 4, -5, b(AIR, 0));
        q.set(-28, W + 3, 0, PRISMARINE, 2);
        q.set(-28, W + 4, 0, SEA_LANTERN, 0);
        q.set(-28, W + 3, -1, BRICK_STAIRS, f.stairs(-1, 0, false));
        q.set(-28, W + 3, 1, BRICK_STAIRS, f.stairs(-1, 0, false));
        for (int v = -4; v <= 4; v += 8) for (int y = W + 3; y <= W + 7; y++) q.set(-22, y, v, PRISMARINE, 1);
        // the summit shrine
        for (int a = -2; a <= 2; a += 4) for (int b = -2; b <= 2; b += 4) for (int y = W + 12; y <= W + 15; y++) q.set(-25 + a, y, b, PRISMARINE, 1);
        q.box(-28, W + 16, -3, -22, W + 16, 3, b(PRISMARINE, 2));
        q.box(-27, W + 17, -2, -23, W + 17, 2, b(PRISMARINE, 1));
        q.box(-26, W + 18, -1, -24, W + 18, 1, b(PRISMARINE, 2));
        q.set(-25, W + 19, 0, SEA_LANTERN, 0);
        q.set(-25, W + 12, 0, BRICK, 3);
        q.set(-25, W + 13, 0, CAULDRON, 3);
    }

    /**
     * The idol of Dagon on its pedestal, facing the sea-gate: a fish-headed giant twenty blocks tall, his belly scaled,
     * a trident in his right hand, lantern eyes bulging from the sides of his head and a fin of spines down his back.
     */
    private static void idol(Pen q, Plan p, int cu, int y0, int cv) {
        if (!q.hit(cu - 4, cv - 4, cu + 5, cv + 2)) return;
        Frame f = q.f;
        int y = y0 + 1;
        for (int a = -2; a <= 2; a++) {                                // two legs, two wide each, and webbed feet
            if (a == 0) continue;
            for (int b = 0; b <= 1; b++) for (int k = 0; k <= 5; k++) q.set(cu + a, y + k, cv + b, PRISMARINE, 2);
            q.set(cu + a, y, cv - 1, BRICK_STAIRS, f.stairs(0, 1, false));
        }
        for (int a = -2; a <= 2; a++)                                   // the torso, its belly scaled
            for (int b = -1; b <= 1; b++)
                for (int k = 6; k <= 12; k++) q.set(cu + a, y + k, cv + b, PRISMARINE, k == 6 ? 2 : b == -1 && k <= 9 ? 0 : 1);
        for (int a = -3; a <= 3; a += 6) {                              // the arms hanging from the shoulders
            for (int k = 7; k <= 12; k++) q.set(cu + a, y + k, cv, PRISMARINE, 2);
            q.set(cu + a, y + 6, cv, PRISMARINE, 1);
        }
        for (int k = 1; k <= 16; k++) q.set(cu + 4, y + k, cv - 1, WALL, 1);   // the trident
        q.set(cu + 3, y + 7, cv - 1, PRISMARINE, 2);
        for (int a = 3; a <= 5; a++) q.set(cu + a, y + 17, cv - 1, PRISMARINE, 2);
        q.set(cu + 3, y + 18, cv - 1, WALL, 1);
        q.set(cu + 5, y + 18, cv - 1, WALL, 1);
        q.set(cu + 4, y + 18, cv - 1, WALL, 1);
        q.set(cu + 4, y + 19, cv - 1, WALL, 1);
        for (int a = -2; a <= 2; a++)                                   // the fish head
            for (int b = -2; b <= 1; b++)
                for (int k = 13; k <= 17; k++) q.set(cu + a, y + k, cv + b, PRISMARINE, k == 17 || (Math.abs(a) == 2 && b == 1) ? 2 : 0);
        for (int a = -1; a <= 1; a++) { q.set(cu + a, y + 13, cv - 3, PRISMARINE, 2); q.set(cu + a, y + 14, cv - 3, PRISMARINE, 0); }
        q.set(cu, y + 13, cv - 4, PRISMARINE, 2);                       // the gaping lip
        q.set(cu - 3, y + 16, cv - 1, SEA_LANTERN, 0);                  // the eyes, bulging to the sides
        q.set(cu + 3, y + 16, cv - 1, SEA_LANTERN, 0);
        for (int b = -2; b <= 1; b++) q.set(cu, y + 18, cv + b, BARS, 0);   // the fin of spines over the head and down the back
        for (int b = -1; b <= 0; b++) q.set(cu, y + 19, cv + b, BARS, 0);
        for (int k = 7; k <= 16; k++) q.set(cu, y + k, cv + 2, BARS, 0);
    }

    /** A guardian's effigy on a quay: a block of rough stone with one lantern eye and spines all round. */
    private static void effigy(Pen q, int cu, int y, int cv) {
        if (!q.hit(cu - 2, cv - 2, cu + 2, cv + 2)) return;
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int k = 0; k <= 2; k++) q.set(cu + a, y + k, cv + b, PRISMARINE, k == 1 ? 0 : 2);
        q.set(cu, y + 1, cv + 1, SEA_LANTERN, 0);
        q.set(cu - 2, y + 1, cv, WALL, 1);
        q.set(cu + 2, y + 1, cv, WALL, 1);
        q.set(cu, y + 1, cv - 2, WALL, 1);
        q.set(cu, y + 3, cv, WALL, 1);
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) q.set(cu + a, y + 3, cv + b, WALL, 1);
    }

    // ------------------------------------------------------------------------------------------------ the barracks
    private static void barracks(Pen q, Plan p) {
        int W = p.W, F = W + 2;
        if (!q.hit(17, -8, 31, 18)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, 18); u <= Math.min(q.u1, 30); u++)
            for (int v = Math.max(q.v0, -7); v <= Math.min(q.v1, 17); v++) {
                boolean wall = u == 18 || u == 30 || v == -7 || v == 17 || v == 11 && u > 18 && u < 30;
                if (wall) {
                    for (int y = F + 1; y <= F + 7; y++) {
                        boolean door = u == 18 && Math.abs(v) <= 1 && y <= F + 3 || v == 11 && u == 24 && y <= F + 2;
                        boolean slit = (u == 18 || u == 30) && (v == -4 || v == 5 || v == 14) && y >= F + 3 && y <= F + 5;
                        if (door) f.set(u, y, v, AIR);
                        else if (slit) f.set(u, y, v, BARS);
                        else if (y == F + 1 || y == F + 7) f.set(u, y, v, PRISMARINE, 2);
                        else deep(f, u, y, v);
                    }
                    continue;
                }
                f.set(u, F, v, PRISMARINE, 1);
                f.clear(u, v, F + 1, F + 7);
                boolean trough = (u <= 20 || u >= 28) && v >= -6 && v <= 9 && Math.floorMod(v + 6, 4) != 3;
                if (trough) f.set(u, F, v, Canvas.WATER);
            }
        // a flat roof behind a crenellated parapet, horns of coral at its corners
        for (int u = Math.max(q.u0, 18); u <= Math.min(q.u1, 30); u++)
            for (int v = Math.max(q.v0, -7); v <= Math.min(q.v1, 17); v++) {
                f.set(u, F + 8, v, PRISMARINE, 2);
                boolean rim = u == 18 || u == 30 || v == -7 || v == 17;
                if (rim && ((u + v) & 1) == 0) f.set(u, F + 9, v, PRISMARINE, 1);
            }
        for (int u = 18; u <= 30; u += 12) for (int v = -7; v <= 17; v += 24) for (int y = F + 9; y <= F + 11; y++) q.set(u, y, v, WALL, 1);
        q.set(24, F + 8, 2, SEA_LANTERN, 0);
        // the armoury's racks
        for (int v = 12; v <= 16; v += 2) { q.set(19, F + 1, v, WALL, 0); q.set(29, F + 1, v, WALL, 0); q.set(19, F + 2, v, FENCE, 0); q.set(29, F + 2, v, FENCE, 0); }
    }

    // ------------------------------------------------------------------------------------------------ the war-hall
    private static void warHall(Pen q, Plan p) {
        int W = p.W, F = W + 4;
        if (!q.hit(-15, 6, 15, 36)) return;
        Frame f = q.f;
        // the front stair from the causeway up to the platform
        for (int j = 0; j < 3; j++)
            for (int u = -3; u <= 3; u++) {
                int v = 7 + j;
                if (!q.in(u, v)) continue;
                int cf = Math.min(q.land(u, v), W - 3);
                for (int y = cf + 1; y < W + 2 + j; y++) f.set(u, y, v, PRISMARINE, 2);
                f.set(u, W + 2 + j, v, BRICK_STAIRS, f.stairs(0, 1, false));
            }
        for (int u = Math.max(q.u0, -14); u <= Math.min(q.u1, 14); u++)
            for (int v = Math.max(q.v0, 12); v <= Math.min(q.v1, 34); v++) {
                int au = Math.abs(u);
                if (au == 14) {                                         // the ribs of the hull
                    if (Math.floorMod(v - 14, 4) == 0) for (int y = F + 1; y <= F + 12; y++) coral(f, u, y, v);
                    continue;
                }
                boolean wall = au == 13 || v == 12 || v == 34;
                if (wall) {
                    for (int y = F + 1; y <= F + 14; y++) {
                        boolean door = v == 12 && au <= 2 && y <= F + (au == 2 ? 4 : 5);
                        boolean slit = au == 13 && Math.floorMod(v - 16, 4) == 0 && y >= F + 6 && y <= F + 9;
                        if (door || slit) f.set(u, y, v, AIR);
                        else if (y == F + 1 || y == F + 14) f.set(u, y, v, PRISMARINE, 2);
                        else deep(f, u, y, v);
                    }
                    continue;
                }
                f.set(u, F, v, PRISMARINE, au <= 2 ? 2 : 1);
                f.clear(u, v, F + 1, F + 14);
            }
        // the roof, a hull turned over
        for (int i = 0; i <= 6; i++)
            for (int u = Math.max(q.u0, -14 + 2 * i); u <= Math.min(q.u1, 14 - 2 * i); u++)
                for (int v = Math.max(q.v0, 12); v <= Math.min(q.v1, 34); v++) {
                    boolean eave = Math.abs(u) == 14 - 2 * i;
                    f.set(u, F + 15 + i, v, PRISMARINE, eave || i == 6 ? 1 : 2);
                }
        // the pillars, two lanterns, the war table, the dais and the throne
        for (int a = -7; a <= 7; a += 14)
            for (int v = 16; v <= 28; v += 4) {
                if (!q.in(a, v)) continue;
                for (int y = F + 1; y <= F + 13; y++) f.set(a, y, v, (y == F + 9 && v == 20) ? SEA_LANTERN : PRISMARINE, (y == F + 9 && v == 20) ? 0 : 1);
                f.set(a, F + 14, v, PRISMARINE, 2);
            }
        q.box(-7, F + 14, 13, -7, F + 14, 33, b(PRISMARINE, 2));
        q.box(7, F + 14, 13, 7, F + 14, 33, b(PRISMARINE, 2));
        q.box(-1, F + 1, 17, 1, F + 1, 21, b(SLAB, 13));
        q.box(-5, F + 1, 30, 5, F + 1, 33, b(PRISMARINE, 2));
        for (int u = -5; u <= 5; u++) q.set(u, F + 1, 29, BRICK_STAIRS, f.stairs(0, 1, false));
        q.set(0, F + 2, 32, BRICK_STAIRS, f.stairs(0, 1, false));
        for (int y = F + 2; y <= F + 6; y++) q.set(0, y, 33, PRISMARINE, 2);
        q.set(-1, F + 2, 32, SLAB, 5);
        q.set(1, F + 2, 32, SLAB, 5);
        for (int y = F + 2; y <= F + 4; y++) { q.set(-1, y, 33, PRISMARINE, 1); q.set(1, y, 33, PRISMARINE, 1); }
        q.set(0, F + 7, 33, SEA_LANTERN, 0);
        for (int a = -1; a <= 1; a += 2) for (int y = F + 5; y <= F + 8; y++) q.set(a * 2, y, 33, WALL, 1);
        for (int v = 15; v <= 31; v += 8) { q.set(-12, F + 1, v, BONE, 0); q.set(12, F + 1, v, BONE, 0); }
        // the well of the stair down to the cells, railed
        for (int j = 0; j <= 6; j++)
            for (int u = -12; u <= -11; u++) {
                int v = 14 + j;
                if (!q.in(u, v)) continue;
                f.clear(u, v, W + 4 - j, F);
                f.set(u, W + 3 - j, v, BRICK_STAIRS, f.stairs(0, -1, false));
            }
        for (int v = 14; v <= 20; v++) q.set(-10, F + 1, v, WALL, 1);
        q.set(-12, F + 1, 21, WALL, 1);
        q.set(-11, F + 1, 21, WALL, 1);
    }

    /** The coral spire over the war-hall: tapering, ringed by a spiral fin, lit by three bands of eyes and a crown. */
    private static void spire(Pen q, Plan p) {
        int W = p.W, y0 = W + 19, y1 = W + 50;
        if (!q.hit(-6, 17, 6, 29)) return;
        Frame f = q.f;
        for (int y = y0; y <= y1; y++) {
            double rr = 3.6 - (y - y0) * 0.1;
            int ri = (int) Math.ceil(rr) + 1;
            for (int a = -ri; a <= ri; a++)
                for (int b = -ri; b <= ri; b++) {
                    if (!q.in(a, 23 + b)) continue;
                    double d = Math.sqrt(a * a + b * b);
                    if (d <= rr) {
                        boolean eye = (y == y0 + 9 || y == y0 + 18) && (a == 0 || b == 0) && d > rr - 1;
                        if (eye) f.set(a, y, 23 + b, SEA_LANTERN);
                        else if (Math.floorMod(y - y0, 6) == 5) f.set(a, y, 23 + b, PRISMARINE, 1);
                        else f.set(a, y, 23 + b, PRISMARINE, 2);
                    }
                }
            double phi = (y - y0) * 0.45;
            for (int k = 0; k < 2; k++) {
                int fa = (int) Math.round(Math.cos(phi + k * Math.PI) * (rr + 1)), fb = (int) Math.round(Math.sin(phi + k * Math.PI) * (rr + 1));
                if (q.in(fa, 23 + fb)) coral(f, fa, y, 23 + fb);
            }
        }
        q.set(0, y1 + 1, 23, SEA_LANTERN, 0);
        for (int a = -1; a <= 1; a += 2) { q.set(a, y1 + 1, 23, WALL, 1); q.set(0, y1 + 1, 23 + a, WALL, 1); }
        q.set(0, y1 + 2, 23, WALL, 1);
    }

    /** The cells of the drowned, dry under the war-hall: a corridor between two rows of barred cells. */
    private static void cells(Pen q, Plan p) {
        int W = p.W, fl = W - 3;
        if (!q.hit(-13, 20, 13, 34)) return;
        Frame f = q.f;
        for (int u = Math.max(q.u0, -12); u <= Math.min(q.u1, 12); u++)
            for (int v = Math.max(q.v0, 21); v <= Math.min(q.v1, 33); v++) {
                f.set(u, fl, v, PRISMARINE, 2);
                f.set(u, W + 3, v, PRISMARINE, 2);
                boolean corridor = v >= 26 && v <= 28 || u <= -11;
                boolean cellWall = !corridor && (u == -10 || u == -5 || u == 0 || u == 5 || u == 10);
                boolean front = !corridor && (v == 25 || v == 29);
                int gap = u < -5 ? -8 : u < 0 ? -3 : u < 5 ? 2 : u < 10 ? 7 : 11;
                for (int y = fl + 1; y <= W + 2; y++) {
                    if (cellWall) deep(f, u, y, v);
                    else if (front && u != gap) f.set(u, y, v, u == -10 || u == -5 || u == 0 || u == 5 || u == 10 ? PRISMARINE : BARS, u == -10 || u == -5 || u == 0 || u == 5 || u == 10 ? 2 : 0);
                    else f.set(u, y, v, AIR);
                }
                if (!corridor && !front && !cellWall) {
                    double c = f.roll(u, fl + 1, v, 87);
                    if (c < 0.06) f.set(u, fl + 1, v, BONE);
                    else if (c < 0.12) f.set(u, W + 2, v, WEB);
                }
            }
        q.set(0, W + 3, 27, SEA_LANTERN, 0);
    }

    // ------------------------------------------------------------------------------------------------ the towers
    /** A round tower on the ring: a ladder from the pier level to its top, doors onto the wall walk, horns of coral. */
    private static void tower(Pen q, Plan p, int[] tw) {
        int W = p.W, cu = tw[0], cv = tw[1], top = tw[2];
        if (!q.hit(cu - 5, cv - 5, cu + 5, cv + 5)) return;
        Frame f = q.f;
        double th = Math.atan2(cv, cu), tu = -Math.sin(th), tv = Math.cos(th), iu = -Math.cos(th), iv = -Math.sin(th);
        boolean gateTower = top == W + 28;
        for (int a = -5; a <= 5; a++)
            for (int b = -5; b <= 5; b++) {
                int u = cu + a, v = cv + b, d2 = a * a + b * b;
                if (d2 > 20 || !q.in(u, v)) continue;
                int foot = Math.min(q.land(u, v), W - 3);
                if (d2 <= 6) {
                    for (int y = foot; y <= W + 1; y++) f.set(u, y, v, PRISMARINE, 2);
                    for (int y = W + 2; y < top; y++) f.set(u, y, v, y == W + 10 ? PRISMARINE : AIR, y == W + 10 ? 1 : 0);
                    f.set(u, top, v, PRISMARINE, 1);
                } else {
                    for (int y = foot; y <= top; y++) {
                        if (y == W + 1 || y == top) f.set(u, y, v, PRISMARINE, 2);
                        else deep(f, u, y, v);
                    }
                    if (((a + b) & 1) == 0) deep(f, u, top + 1, v);
                }
            }
        // horns of coral on the battlement
        for (int a = -1; a <= 1; a += 2)
            for (int b = -1; b <= 1; b += 2) for (int y = top + 1; y <= top + 3 + ((a + b) == 0 ? 1 : 0); y++) q.set(cu + 3 * a, y, cv + 3 * b, WALL, 1);
        q.set(cu, top + 1, cv, SEA_LANTERN, 0);
        // the ladder on the side away from the sea, through the floors
        int lu = cu + (int) Math.round(2 * iu), lv = cv + (int) Math.round(2 * iv);
        int wu = cu + (int) Math.round(3 * iu), wv = cv + (int) Math.round(3 * iv);
        int fu = Integer.signum(lu - wu), fv = Integer.signum(lv - wv);
        if (fu != 0 && fv != 0) { if (Math.abs(iu) > Math.abs(iv)) fv = 0; else fu = 0; }
        for (int y = W + 2; y <= top; y++) q.set(lu, y, lv, LADDER, f.facing(fu, fv));
        q.set(cu, top, cv, PRISMARINE, 1);
        // doors onto the wall walk on both sides, and the gate towers' doors onto the quays
        for (int k = -1; k <= 1; k += 2)
            for (int dd = 3; dd <= 4; dd++) {
                int du = cu + (int) Math.round(k * dd * tu), dv = cv + (int) Math.round(k * dd * tv);
                q.set(du, W + 11, dv, AIR, 0);
                q.set(du, W + 12, dv, AIR, 0);
            }
        if (gateTower)
            for (int dd = 3; dd <= 4; dd++) {
                int du = cu + (int) Math.round(dd * iu), dv = cv + (int) Math.round(dd * iv);
                q.set(du, W + 2, dv, AIR, 0);
                q.set(du, W + 3, dv, AIR, 0);
            }
    }

    // ------------------------------------------------------------------------------------------------ chests, cages, signs
    private static void tiles(Pen q, Plan p) {
        Frame f = q.f;
        int W = p.W;
        long h = p.h;
        // the war-hall: the Warlord's hoard beside his throne, and his trophies
        f.chest(3, W + 6, 32, 0, -1, TREASURE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        f.chest(-3, W + 6, 32, 0, -1, CROSSING, "trinket:0.3");
        f.chest(-12, W + 5, 27, 1, 0, Sites.DESERT, null);
        f.chest(12, W + 5, 27, -1, 0, NETHER, null);
        f.set(0, W + 5, 14, PLATE, 0);
        f.dispenser(0, W + 4, 14, 0, 0);
        f.sign(3, W + 7, 13, 0, 1, "THE DEEP ONES\nMARCH AT\nHIS WORD");
        // the cells
        f.chest(3, W - 2, 21, 0, 1, Sites.DUNGEON, null);
        f.chest(8, W - 2, 33, 0, -1, MINESHAFT, "lore:" + Hash.range(Hash.mix(h ^ 5), 0, 99));
        f.spawner(-3, W - 2, 32, "CAVE_SPIDER");
        f.sign(12, W, 27, -1, 0, "NONE HERE\nWILL SEE\nTHE AIR AGAIN");
        // the barracks and the armoury
        f.chest(21, W + 3, -6, 0, 1, Sites.CORRIDOR, null);
        f.chest(29, W + 3, 15, -1, 0, Sites.SMITH, "trinket:0.15");
        f.chest(19, W + 3, 15, 1, 0, Sites.SMITH, null);
        f.spawner(24, W + 3, 4, "ZOMBIE");
        // the temple, the idol and the breeding pool
        f.chest(-27, W + 3, -3, 1, 0, Sites.JUNGLE, "trinket:0.2");
        f.chest(-27, W + 3, 3, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 9), 0, 99));
        f.spawner(-23, W + 3, 3, "WITCH");
        f.set(-25, W + 3, -4, PLATE, 0);
        f.dispenser(-25, W + 2, -4, 0, 0);
        f.sign(-28, W + 6, 0, 1, 0, "DAGON\nFATHER OF\nTHE DEEP ONES");
        f.chest(-24, W + 3, -22, 0, -1, Sites.DESERT, "trinket:0.1");
        f.chest(16, -9, -22, 0, -1, Sites.DUNGEON, "trinket:0.15");
        f.sign(7, W + 4, -17, -1, 0, "HERE THE\nDEEP ONES\nARE SPAWNED");
        // the gate towers
        int[] g0 = p.towers[0];
        f.sign(g0[0], W + 4, g0[1] + 5, 0, 1, "Y'HA-NTHLEI\nBASTION OF\nTHE DEEP");
        f.chest(g0[0], W + 2, g0[1], 0, 1, IGLOO, null);
        f.chest(p.towers[1][0], W + 2, p.towers[1][1], 0, 1, MINESHAFT, null);
    }

    // ------------------------------------------------------------------------------------------------------ the pen
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

        int land(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v);
            if (wx < x0 || wx > x0 + 15 || wz < z0 || wz > z0 + 15) return s.surface(wx, wz) - s.base;
            int i = (wx - x0) << 4 | (wz - z0);
            if (land[i] == Integer.MIN_VALUE) land[i] = s.surface(wx, wz) - s.base;
            return land[i];
        }

        void set(int u, int y, int v, int id, int meta) { if (in(u, v)) f.set(u, y, v, id, meta); }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int A0 = Math.max(Math.min(a0, a1), u0), A1 = Math.min(Math.max(a0, a1), u1);
            int B0 = Math.max(Math.min(b0, b1), v0), B1 = Math.min(Math.max(b0, b1), v1);
            for (int a = A0; a <= A1; a++) for (int b = B0; b <= B1; b++) for (int y = y0; y <= y1; y++) f.set(a, y, b, m >> 4, m & 15);
        }
    }
}
