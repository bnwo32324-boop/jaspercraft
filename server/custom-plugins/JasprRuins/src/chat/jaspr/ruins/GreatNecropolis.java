package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Necropolis of the Ghoul-Kings (epoch 6): a walled city of the dead. A gatehouse with a raised portcullis opens on
 * the Avenue of Kings (obelisks and broken statues); four quarters of mausolea of many sizes (some with stairs down to
 * burial vaults), grave plots and obelisks; the Garden of Graves and its chapel, the Charnel Pit, the Ossuary Tower of
 * bone and the domed Mausoleum of the First King; and in the middle the great stepped tomb, whose grand stair runs down
 * to the Ghoul-King's throne hall ten blocks under the city. The ghoul warrens, low tunnels under the avenues, join the
 * vaults, the landmarks and the throne hall.
 */
final class GreatNecropolis extends GreatDesign {
    private static final int R = 52;
    /** Materials: id << 4 | meta, or one of the Choir's mixed stones (negative). */
    private static final int MAS = -1, ELD = -2, GLY = -3, RUB = -4, PAV = -5, WARREN = -6;
    private static final int SMALL = 0, MEDIUM = 1, GRAVES = 2, OBELISK = 3, STATUE = 4;
    private static final int[] DU = {0, 1, 0, -1}, DV = {-1, 0, 1, 0};
    /** The warrens' floor, the throne hall's floor (local y). */
    private static final int WF = -6, HF = -10;

    private static int b(int id, int meta) { return id << 4 | meta; }

    /** The layout: the lots of the four quarters and the straight runs of the ghoul warrens. */
    private static final class L extends Layout {
        final List<int[]> lots = new ArrayList<>();      // {kind, cu, cv, face, vault, seed}
        final List<int[]> tunnels = new ArrayList<>();   // {a0, b0, a1, b1}
        final List<int[]> vaults = new ArrayList<>();    // {cu, cv, half}: rooms at the warrens' level
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        int[] cs = {10, 20, 30, 40};
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2)
                for (int ci : cs)
                    for (int cj : cs) {
                        if (ci <= 20 && cj <= 20 || ci >= 30 && cj >= 30) continue;   // the great tomb; the quarter's landmark
                        int cu = su * ci, cv = sv * cj;
                        int face = ci < cj ? (su < 0 ? 1 : 3) : (sv < 0 ? 2 : 0);
                        boolean vault = ci == 20 && cj == 30 || sv < 0 && ci == 30 && cj == 20;
                        int roll = r.nextInt(100), kind = vault ? MEDIUM : roll < 30 ? SMALL : roll < 58 ? MEDIUM : roll < 78 ? GRAVES : roll < 89 ? OBELISK : STATUE;
                        l.lots.add(new int[] {kind, cu, cv, face, vault ? 1 : 0, r.nextInt(1 << 20)});
                        if (vault) { l.vaults.add(new int[] {cu, cv, 4}); warren(l, cu, cv); }
                    }
        // the landmarks' cellars join the warrens too
        warren(l, 35, -35);
        warren(l, -35, 35);
        warren(l, 35, 35);
        warren(l, -35, -35);
        l.vaults.add(new int[] {35, 35, 5});
        l.vaults.add(new int[] {-35, -35, 4});
        l.vaults.add(new int[] {-35, 35, 4});
        // the ring under the ring road
        l.tunnels.add(new int[] {-21, -21, 21, -21});
        l.tunnels.add(new int[] {-21, 21, 21, 21});
        l.tunnels.add(new int[] {-21, -21, -21, 21});
        l.tunnels.add(new int[] {21, -21, 21, 21});

        l.boss = new int[] {0, HF + 1, 8};
        List<Garrison> g = l.garrisons;
        g.add(g(0, 1, -38, "ghoul+zombie+husk"));                    // inside the gate
        g.add(g(-8, 11, -46, "skeleton+stray"));                      // the gatehouse's murder room
        g.add(g(0, 1, -29, "!ghoul+zombie_villager+zombie"));          // the Avenue of Kings
        g.add(g(-15, 1, -35, "tomb_crawler+cave_spider+spider"));      // the Garden of Graves
        g.add(g(35, -8, -35, "ghoul+tomb_crawler+silverfish"));        // the charnel pit's floor
        g.add(g(24, 1, -24, "witch+cult_zealot"));                     // by the pit's rim
        g.add(g(-28, 1, 35, "skeleton+wither_skeleton"));              // the ossuary yard
        g.add(g(-35, 37, 35, "nightgaunt+stray"));                     // the ossuary tower's crown
        g.add(g(35, 1, 25, "vindicator+evoker+cult_adept"));           // before the First King's mausoleum
        g.add(g(-20, 1, -10, "creeper+hound"));                        // the ring road, west
        g.add(g(20, 1, 10, "enderman+mi_go"));                         // the ring road, east
        g.add(g(0, 21, -5, "star_spawn+illusioner"));                  // the summit shrine's terrace
        g.add(g(-10, 5, -15, "shoggoth+slime"));                       // the tomb's first terrace
        g.add(g(0, WF + 1, 21, "ghoul+silverfish+endermite"));         // the warrens behind the tomb
        g.add(g(-21, WF + 1, -10, "tomb_crawler+cave_spider"));        // the warrens, west
        g.add(g(0, HF + 1, -4, "!ghoul+husk"));                         // the foot of the grand stair
        g.add(g(-9, HF + 1, 12, "wither_skeleton+skeleton"));           // the kings' niches
        g.add(g(2, 1, 45, "cult_zealot+vindicator"));                    // the back tower
        g.add(g(0, 1, 30, "deep_one+zombie+witch"));                     // the back avenue
        return l;
    }

    /** A warren from a cellar at (cu, cv) to the ring: along v to the ring's band, then along u. */
    private static void warren(L l, int cu, int cv) {
        int cvr = Math.max(-21, Math.min(21, cv)), cur = Math.max(-21, Math.min(21, cu));
        if (cvr != cv) l.tunnels.add(new int[] {cu, cv, cu, cvr});
        if (cur != cu) l.tunnels.add(new int[] {cu, cvr, cur, cvr});
    }

    // ------------------------------------------------------------------------------------------------ the drawing tools

    /** The chunk's share of the footprint in local coordinates, and drawing tools clipped to it. */
    private static final class P {
        final Plans.GreatSite s; final Frame f; final int u0, u1, v0, v1;
        private final int[] ground = new int[256];
        private final boolean[] known = new boolean[256];

        P(Plans.GreatSite s, Canvas c) {
            this.s = s;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int xa = c.x0 - s.x, xb = xa + 15, za = c.z0 - s.z, zb = za + 15;
            switch (s.rot & 3) {
                case 1: u0 = za; u1 = zb; v0 = -xb; v1 = -xa; break;
                case 2: u0 = -xb; u1 = -xa; v0 = -zb; v1 = -za; break;
                case 3: u0 = -zb; u1 = -za; v0 = xa; v1 = xb; break;
                default: u0 = xa; u1 = xb; v0 = za; v1 = zb;
            }
        }

        /** The land's height (local y) at a column of this chunk. */
        int ground(int a, int b) {
            int i = (a - u0) << 4 | (b - v0);
            if (!known[i]) { known[i] = true; ground[i] = s.surface(f.wx(a, b), f.wz(a, b)) - s.base; }
            return ground[i];
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int a, int b) { return a >= u0 && a <= u1 && b >= v0 && b <= v1; }

        void put(int a, int y, int b, int m) {
            switch (m) {
                case MAS: f.masonry(a, y, b); return;
                case ELD: f.eldritch(a, y, b); return;
                case GLY: f.glyph(a, y, b); return;
                case RUB: f.rubble(a, y, b); return;
                case PAV: f.paving(a, y, b); return;
                case WARREN: {
                    double q = f.roll(a, y, b, 301);
                    if (q < 0.45) f.rubble(a, y, b); else if (q < 0.65) f.set(a, y, b, DIRT, 1); else if (q < 0.8) f.set(a, y, b, STONE, 5);
                    else if (q < 0.92) f.set(a, y, b, BONE, 0); else f.set(a, y, b, SOUL_SAND);
                    return;
                }
                default: f.set(a, y, b, m >> 4, m & 15);
            }
        }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int aa = Math.max(Math.min(a0, a1), u0), ab = Math.min(Math.max(a0, a1), u1), ba = Math.max(Math.min(b0, b1), v0), bb = Math.min(Math.max(b0, b1), v1);
            for (int a = aa; a <= ab; a++) for (int b = ba; b <= bb; b++) for (int y = y0; y <= y1; y++) put(a, y, b, m);
        }

        /** The four sides of a box from y0 to y1. */
        void walls(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            box(a0, y0, b0, a1, y1, b0, m); box(a0, y0, b1, a1, y1, b1, m);
            box(a0, y0, b0, a0, y1, b1, m); box(a1, y0, b0, a1, y1, b1, m);
        }

        void set(int a, int y, int b, int id, int meta) { if (in(a, b)) f.set(a, y, b, id, meta); }
        void set(int a, int y, int b, int id) { if (in(a, b)) f.set(a, y, b, id, 0); }
    }

    private static final int SOUL_SAND = 88;

    /** A lot's own frame: x to its right, z into it from its door (face is the side the door looks toward). */
    private static int lu(int cu, int face, int x, int z) { return cu + x * -DV[face] + z * -DU[face]; }
    private static int lv(int cv, int face, int x, int z) { return cv + x * DU[face] + z * -DV[face]; }

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) {
        L l = (L) layout;
        P p = new P(s, c);
        if (!p.hit(-R, -R, R, R)) return;
        land(p);
        under(p, l);
        cityWall(p);
        gatehouse(p);
        avenues(p);
        tomb(p);
        for (int[] lot : l.lots) lot(p, lot);
        charnelPit(p);
        ossuary(p);
        firstKing(p);
        garden(p);
        throneHall(p);
        tiles(p, s, l);
    }

    // ------------------------------------------------------------------------------------------------ ground and walls

    /** Levels the footprint: footings down to the land, the land (and the field's debris) cut away, a retaining face at the edge. */
    private static void land(P p) {
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int t = p.ground(a, b), m = Math.max(Math.abs(a), Math.abs(b));
                boolean edge = m >= R - 1;
                for (int y = t + 1; y < 0; y++) { if (edge) p.f.masonry(a, y, b); else p.f.rubble(a, y, b); }
                p.f.clear(a, b, 1, Math.max(t, 0) + 3);
                if (edge && t > 1) for (int y = 1; y < t; y++) p.f.masonry(a, y, b);
                if (m >= 48) { double q = p.f.roll(a, 0, b, 302); if (q < 0.5) p.f.rubble(a, 0, b); else if (q < 0.75) p.f.set(a, 0, b, DIRT, 1); else p.f.paving(a, 0, b); }
                else p.f.paving(a, 0, b);
            }
    }

    /** The city wall (the ring 45..47 out), its corner and side towers. */
    private static void cityWall(P p) {
        int a0 = Math.max(-47, p.u0), a1 = Math.min(47, p.u1), b0 = Math.max(-47, p.v0), b1 = Math.min(47, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b));
                if (m < 45 || Math.abs(a) <= 12 && b <= -40) continue;   // the gatehouse
                for (int y = 1; y <= 10; y++) p.put(a, y, b, y == 6 ? GLY : ELD);
                if (m == 47) { p.f.masonry(a, 11, b); if (((a + b) & 1) == 0) p.f.masonry(a, 12, b); }
                else if (m == 45 && p.f.roll(a, 11, b, 303) < 0.04) p.f.set(a, 11, b, SKULL, 1);
            }
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) tower(p, 46 * su, 46 * sv, 5, 18, -su, -sv);
        tower(p, -46, 0, 4, 15, 1, 0);
        tower(p, 46, 0, 4, 15, -1, 0);
        tower(p, 0, 46, 4, 15, 0, -1);
    }

    /** A wall tower: two rooms on a ladder, doors to the city (toward du, dv) and onto the wall walk, a crenellated roof. */
    private static void tower(P p, int cu, int cv, int h, int height, int du, int dv) {
        if (!p.hit(cu - h, cv - h, cu + h, cv + h)) return;
        for (int a = cu - h; a <= cu + h; a++)
            for (int b = cv - h; b <= cv + h; b++) {
                if (!p.in(a, b)) continue;
                boolean wall = Math.abs(a - cu) == h || Math.abs(b - cv) == h;
                for (int y = 1; y <= height; y++) {
                    if (wall) p.put(a, y, b, y % 6 == 0 ? GLY : ELD);
                    else if (y == 10 || y == height - 1) p.f.set(a, y, b, DOUBLE_SLAB, 5);
                    else p.f.set(a, y, b, AIR);
                }
                if (wall && ((a + b) & 1) == 0) p.f.masonry(a, height + 1, b);
            }
        // a door toward the city, door-ways onto the wall walk on the two sides the wall meets
        int da = cu + du * h, db = cv + dv * h;
        if (du != 0 && dv != 0) { da = cu + du * h; db = cv + dv * 2; }
        p.box(da, 1, db, da, 3, db, AIR);
        if (du != 0 && dv != 0) {
            p.box(cu + du * h, 11, cv, cu + du * h, 12, cv, AIR);
            p.box(cu, 11, cv + dv * h, cu, 12, cv + dv * h, AIR);
        } else if (du != 0) {
            p.box(cu, 11, cv - h, cu, 12, cv - h, AIR);
            p.box(cu, 11, cv + h, cu, 12, cv + h, AIR);
        } else {
            p.box(cu - h, 11, cv, cu - h, 12, cv, AIR);
            p.box(cu + h, 11, cv, cu + h, 12, cv, AIR);
        }
        // the ladder on the far wall, through both floors
        boolean corner = du != 0 && dv != 0;
        int la = cu - du * (h - 1), lb = corner ? cv : cv - dv * (h - 1), fb = corner ? 0 : dv;
        for (int y = 1; y <= height; y++) p.set(la, y, lb, LADDER, p.f.facing(du, fb));
        p.set(cu, height - 2, cv, GLOWSTONE);
    }

    /**
     * The gatehouse: two towers flanking an arched passage with a raised portcullis and murder holes, a murder room over it.
     */
    private static void gatehouse(P p) {
        if (!p.hit(-12, -51, 12, -41)) return;
        for (int s = -1; s <= 1; s += 2)
            for (int a = 4; a <= 12; a++)
                for (int b = -51; b <= -41; b++) {
                    int u = a * s;
                    if (!p.in(u, b)) continue;
                    boolean wall = a == 4 || a == 12 || b == -51 || b == -41;
                    for (int y = 1; y <= 22; y++) {
                        if (wall) p.put(u, y, b, y % 7 == 0 ? GLY : ELD);
                        else if (y == 10 || y == 17) p.f.set(u, y, b, DOUBLE_SLAB, 5);
                        else p.f.set(u, y, b, AIR);
                    }
                    if (wall && ((a + b) & 1) == 0) p.f.masonry(u, 23, b);
                    if (wall && b == -51 && (a == 6 || a == 10)) for (int y = 13; y <= 15; y++) p.f.set(u, y, b, AIR);   // arrow slits
                }
        // the passage and its arch, the murder room above
        for (int a = -3; a <= 3; a++)
            for (int b = -51; b <= -41; b++) {
                if (!p.in(a, b)) continue;
                int arch = Math.abs(a) == 3 ? 5 : Math.abs(a) == 2 ? 6 : 7;
                for (int y = 1; y <= 17; y++) {
                    if (y <= arch) p.f.set(a, y, b, AIR);
                    else if (y >= 11 && y <= 15 && b >= -49 && b <= -43) p.f.set(a, y, b, AIR);
                    else if (y == 10 && b >= -49 && b <= -43) p.f.set(a, y, b, DOUBLE_SLAB, 5);
                    else p.put(a, y, b, y == arch + 1 && b == -51 ? GLY : ELD);
                }
                if (((a + b) & 1) == 0) p.f.masonry(a, 18, b);
            }
        for (int b = -48; b <= -44; b += 4) p.box(0, 8, b, 0, 10, b, AIR);                  // murder holes
        for (int a = -3; a <= 3; a++) for (int y = 5; y <= 9; y++) if (y > (Math.abs(a) == 3 ? 5 : Math.abs(a) == 2 ? 6 : 7) - 2) p.set(a, y, -49, IRON_BARS);
        for (int s = -1; s <= 1; s += 2) {
            p.box(4 * s, 1, -46, 4 * s, 3, -46, AIR);                                          // guard rooms off the passage
            p.box(4 * s, 11, -46, 4 * s, 13, -46, AIR);                                        // the murder room's doors
            for (int y = 1; y <= 21; y++) p.set(11 * s, y, -46, LADDER, p.f.facing(-s, 0));
            p.box(12 * s, 11, -44, 12 * s, 12, -44, AIR);                                      // onto the wall walk
            p.set(4 * s, 6, -51, SEA_LANTERN);
        }
    }

    /** The paved ways: the Avenue of Kings, the ring road round the tomb, the side and back avenues. */
    private static void avenues(P p) {
        int a0 = Math.max(-44, p.u0), a1 = Math.min(44, p.u1), b0 = Math.max(-44, p.v0), b1 = Math.min(44, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b)), w;
                if (m >= 18 && m <= 22) w = m == 18 || m == 22 ? 4 : 2;
                else if (m > 22 && (Math.abs(a) <= 4 || Math.abs(b) <= 4)) w = Math.min(Math.abs(a), Math.abs(b));
                else continue;
                if (w == 4) p.f.set(a, 0, b, PRISMARINE, 2);
                else if (w <= 1 && m > 22) p.f.set(a, 0, b, p.f.roll(a, 0, b, 304) < 0.7 ? BRICK : STONE, p.f.roll(a, 0, b, 304) < 0.7 ? 0 : 6);
                else p.f.set(a, 0, b, BRICK, p.f.roll(a, 0, b, 305) < 0.5 ? 2 : 0);
            }
        // the Avenue of Kings: obelisks and broken statues of the old kings
        for (int s = -1; s <= 1; s += 2) {
            obelisk(p, 9 * s, -38, 14 + (s > 0 ? 2 : 0));
            obelisk(p, 9 * s, -26, 16 - (s > 0 ? 2 : 0));
            statue(p, 9 * s, -32, s > 0 ? 3 : 1, s > 0 ? 1 : 2, 0x51 + s);
            statue(p, 12 * s, -21, 0, 0, 0x53 + s);
        }
        obelisk(p, -9, 30, 13);
        obelisk(p, 9, 30, 15);
    }

    /** An obelisk on a stepped plinth: a 3x3 shaft tapering to a single block and an obsidian tip, glyph bands, a lamp. */
    private static void obelisk(P p, int cu, int cv, int height) {
        if (!p.hit(cu - 2, cv - 2, cu + 2, cv + 2)) return;
        p.box(cu - 2, 1, cv - 2, cu + 2, 1, cv + 2, b(BRICK, 3));
        p.box(cu - 1, 2, cv - 1, cu + 1, 2, cv + 1, b(DOUBLE_SLAB, 5));
        int shaft = height - 4;
        for (int y = 3; y <= shaft; y++) p.box(cu - 1, y, cv - 1, cu + 1, y, cv + 1, y % 5 == 0 ? GLY : ELD);
        for (int y = shaft + 1; y < height; y++) p.set(cu, y, cv, y == height - 2 ? SEA_LANTERN : PRISMARINE, 2);
        p.set(cu, height, cv, OBSIDIAN);
    }

    /**
     * A statue of a Ghoul-King on a plinth, facing direction {@code face}: legs, a robed body, arms, a crowned head. Broken
     * ones ({@code broken} 1: no head, its head fallen beside; 2: one arm gone too) still stand.
     */
    private static void statue(P p, int cu, int cv, int face, int broken, int salt) {
        if (!p.hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
        p.box(cu - 2, 1, cv - 2, cu + 2, 2, cv + 2, b(BRICK, 3));
        int ru = -DV[face], rv = DU[face], fu = DU[face], fv = DV[face];
        int stone = b(STONE, 6);
        for (int k = -1; k <= 1; k += 2) for (int y = 3; y <= 5; y++) p.set(cu + ru * k, y, cv + rv * k, STONE, 6);   // legs
        for (int y = 6; y <= 10; y++) {
            int w = y <= 7 ? 1 : 1;
            for (int k = -w; k <= w; k++) { p.put(cu + ru * k, y, cv + rv * k, stone); p.put(cu + ru * k - fu, y, cv + rv * k - fv, stone); }
        }
        for (int k = -1; k <= 1; k += 2) {
            if (broken == 2 && k > 0) { p.set(cu + ru * 3 + fu * 2, 1, cv + rv * 3 + fv * 2, STONE, 6); p.set(cu + ru * 3 + fu * 3, 1, cv + rv * 3 + fv * 3, STONE, 6); continue; }
            for (int y = 7; y <= 10; y++) p.set(cu + ru * 2 * k, y, cv + rv * 2 * k, STONE, 6);   // arms
            p.set(cu + ru * 2 * k + fu, 7, cv + rv * 2 * k + fv, STONE, 6);                      // clawed hands reaching out
        }
        if (broken == 0) {
            p.set(cu, 11, cv, STONE, 6); p.set(cu - fu, 11, cv - fv, STONE, 6);
            p.set(cu, 12, cv, STONE, 6); p.set(cu - fu, 12, cv - fv, STONE, 6);
            p.set(cu + fu, 12, cv + fv, SKULL, faceMeta(face));
            for (int k = -1; k <= 1; k += 2) p.set(cu + ru * k, 13, cv + rv * k, IRON_BARS);       // the crown
            p.set(cu - fu, 13, cv - fv, IRON_BARS);
        } else {
            int hu = cu + ru * 3 - fu * 2, hv = cv + rv * 3 - fv * 2;                                // the fallen head
            p.set(hu, 1, hv, STONE, 6); p.set(hu + ru, 1, hv + rv, STONE, 6); p.set(hu, 2, hv, STONE, 6);
            p.set(cu, 11, cv, BRICK, 2);
        }
    }

    /** Skull meta on a wall... skulls here stand on the floor (meta 1). */
    private static int faceMeta(int face) { return 1; }

    // ------------------------------------------------------------------------------------------------ the great tomb

    /** The stepped tomb's top (local y) over a column, 0 off it: five steps four high, from half-width 17 to 5. */
    private static int tombTop(int a, int b) {
        int m = Math.max(Math.abs(a), Math.abs(b));
        return m > 17 ? 0 : 4 * (Math.min(4, (17 - m) / 3) + 1);
    }

    /** The great stepped tomb: its terraces, the portal of the grand stair, the stairs up its flanks to the summit shrine. */
    private static void tomb(P p) {
        if (!p.hit(-25, -21, 25, 17)) return;
        int a0 = Math.max(-17, p.u0), a1 = Math.min(17, p.u1), b0 = Math.max(-17, p.v0), b1 = Math.min(17, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b)), top = tombTop(a, b);
                for (int y = 1; y <= top; y++) {
                    int half = 17 - 3 * ((y - 1) / 4);
                    if (y == top) { if (m == half && ((a + b) & 3) == 0) p.f.set(a, y, b, BRICK, 3); else p.f.paving(a, y, b); }
                    else if (m == half) p.put(a, y, b, y % 4 == 2 ? GLY : ELD);
                    else p.f.masonry(a, y, b);
                }
            }
        // the portal: two pylons and an obsidian lintel over the grand stair's mouth
        for (int s = -1; s <= 1; s += 2) {
            p.box(3 * s, 1, -18, 4 * s, 10, -17, ELD);
            p.set(3 * s, 4, -18, SEA_LANTERN);
            p.box(3 * s, 11, -18, 4 * s, 11, -17, b(BRICK, 3));
            p.set(3 * s, 12, -18, SKULL, 1);
        }
        p.box(-2, 6, -18, 2, 8, -17, b(OBSIDIAN, 0));
        p.box(-2, 9, -18, 2, 9, -17, GLY);
        p.box(-2, 1, -18, 2, 5, -15, AIR);
        // obelisks on the first terrace's corners
        for (int s = -1; s <= 1; s += 2)
            for (int q = -1; q <= 1; q += 2) {
                for (int y = 5; y <= 13; y++) p.put(16 * s, y, 16 * q, y % 4 == 0 ? GLY : ELD);
                p.set(16 * s, 14, 16 * q, OBSIDIAN);
            }
        // the stairs up the two flanks, from the side avenues over the ring road (on arches) to the summit
        for (int s = -1; s <= 1; s += 2)
            for (int k = 5; k <= 24; k++) {
                int u = k * s, y = 25 - k;
                for (int v = -2; v <= 2; v++) {
                    if (!p.in(u, v)) continue;
                    for (int yy = Math.max(tombTop(u, v) + 1, 1); yy < y; yy++) {
                        if (k >= 18 && k <= 20 && yy <= 3) p.f.set(u, yy, v, AIR);
                        else p.f.masonry(u, yy, v);
                    }
                    p.f.set(u, y, v, BRICK_STAIRS, p.f.stairs(-s, 0, false));
                }
                if (k >= 18 && k <= 24) { p.set(u, y + 1, -3, WALL, 1); p.set(u, y + 1, 3, WALL, 1); int w0 = Math.max(k <= 20 ? 4 : 1, y - 3); p.box(u, w0, -3, u, y, -3, MAS); p.box(u, w0, 3, u, y, 3, MAS); }
            }
        // the summit shrine
        if (p.hit(-3, -3, 3, 3)) {
            for (int a = -3; a <= 3; a++)
                for (int b = -3; b <= 3; b++) {
                    if (!p.in(a, b)) continue;
                    boolean wall = Math.abs(a) == 3 || Math.abs(b) == 3, corner = Math.abs(a) == 3 && Math.abs(b) == 3;
                    for (int y = 21; y <= 28; y++) {
                        if (corner) p.f.set(a, y, b, BRICK, 3);
                        else if (!wall || b == -3 && Math.abs(a) <= 1 && y <= 23 || b == 0 && y >= 23 && y <= 24) p.f.set(a, y, b, AIR);
                        else p.put(a, y, b, y == 26 ? GLY : ELD);
                    }
                    if (corner) for (int y = 29; y <= 31; y++) p.f.set(a, y, b, y == 31 ? OBSIDIAN : BRICK, y == 31 ? 0 : 3);
                }
            p.box(-3, 29, -3, 3, 29, 3, b(DOUBLE_SLAB, 5));
            p.box(-2, 30, -2, 2, 30, 2, ELD);
            p.box(-1, 31, -1, 1, 31, 1, GLY);
            p.set(0, 32, 0, OBSIDIAN);
            p.set(0, 33, 0, SKULL, 1);
            p.set(-2, 21, 2, OBSIDIAN);
            p.set(-2, 22, 2, SKULL, 1);
            p.set(0, 28, 0, GLOWSTONE);
        }
    }

    // ------------------------------------------------------------------------------------------------ under the city

    /** The underground: every wall and floor of the warrens, the vaults and the throne hall first, then all their air. */
    private static void under(P p, L l) {
        for (int pass = 0; pass < 2; pass++) {
            for (int[] t : l.tunnels) tunnel(p, t, pass);
            for (int[] v : l.vaults) {
                int h = v[2];
                if (!p.hit(v[0] - h - 1, v[1] - h - 1, v[0] + h + 1, v[1] + h + 1)) continue;
                if (pass == 0) p.box(v[0] - h - 1, WF, v[1] - h - 1, v[0] + h + 1, -1, v[1] + h + 1, MAS);
                else {
                    p.box(v[0] - h, WF + 1, v[1] - h, v[0] + h, -2, v[1] + h, AIR);
                    for (int s = -1; s <= 1; s += 2) for (int q = -1; q <= 1; q += 2) {
                        p.box(v[0] + s * h, WF + 1, v[1] + q * (h - 1), v[0] + s * h, WF + 1, v[1] + q * (h - 1), b(DOUBLE_SLAB, 5));
                        p.set(v[0] + s * h, WF + 2, v[1] + q * (h - 1), SKULL, 1);
                    }
                    p.set(v[0], -1, v[1], GLOWSTONE);
                }
            }
            if (!p.hit(-20, -10, 20, 19)) continue;
            if (pass == 0) p.box(-15, HF, -9, 15, -1, 19, ELD);
            else {
                p.box(-12, HF + 1, -7, 12, -2, 17, AIR);
                p.box(-4, -1, -7, 4, -1, 17, AIR);
                for (int s = -1; s <= 1; s += 2) {
                    for (int v : new int[] {-5, 5, 9, 13}) p.box(13 * s, HF + 1, v - 1, 14 * s, HF + 3, v + 1, AIR);
                    p.box(13 * s, HF + 1, 0, 16 * s, -3, 2, AIR);                         // the side passages up to the warrens
                    p.box(17 * s, WF + 1, 0, 19 * s, -3, 2, AIR);
                }
            }
        }
    }

    /** A straight run of the ghoul warrens: rough walls of rubble, dirt and bone; low, crooked, three wide. */
    private static void tunnel(P p, int[] t, int pass) {
        int a0 = Math.min(t[0], t[2]), a1 = Math.max(t[0], t[2]), b0 = Math.min(t[1], t[3]), b1 = Math.max(t[1], t[3]);
        if (!p.hit(a0 - 2, b0 - 2, a1 + 2, b1 + 2)) return;
        if (pass == 0) { p.box(a0 - 2, WF, b0 - 2, a1 + 2, WF + 4, b1 + 2, WARREN); return; }
        boolean alongU = b0 == b1;
        for (int a = Math.max(a0 - 2, p.u0); a <= Math.min(a1 + 2, p.u1); a++)
            for (int b = Math.max(b0 - 2, p.v0); b <= Math.min(b1 + 2, p.v1); b++) {
                int w = alongU ? Math.abs(b - b0) : Math.abs(a - a0);
                int k = alongU ? a : b;
                boolean beyond = alongU ? a < a0 || a > a1 : b < b0 || b > b1;
                if (beyond && w > 1) continue;
                double q = Hash.unit(Hash.of(0x5A11L, k * 3L + (alongU ? 1 : 2), w));
                if (w <= 1) {
                    p.f.set(a, WF + 1, b, AIR); p.f.set(a, WF + 2, b, AIR);
                    if (p.f.roll(a, WF + 3, b, 306) > 0.12 || w == 0 && beyond) p.f.set(a, WF + 3, b, AIR);
                    double d = p.f.roll(a, WF + 1, b, 307);
                    if (w == 1 && d < 0.04) p.f.set(a, WF + 1, b, BONE, 0);
                    else if (w == 1 && d < 0.06) p.f.set(a, WF + 1, b, SKULL, 1);
                    else if (w == 1 && d < 0.08) p.f.set(a, WF + 2, b, WEB);
                } else if (q < 0.3 || p.f.roll(a, WF, b, 308) < 0.15) { p.f.set(a, WF + 1, b, AIR); p.f.set(a, WF + 2, b, AIR); }
            }
    }

    /** The throne hall: the grand stair down to it, the obsidian way, pillars, the kings' niches, the dais and the throne. */
    private static void throneHall(P p) {
        if (!p.hit(-20, -18, 20, 19)) return;
        for (int v = -16; v <= -8; v++) {
            int y = -17 - v;
            p.box(-2, y + 1, v, 2, y + 4, v, AIR);
            p.box(-2, y, v, 2, y, v, b(BRICK_STAIRS, p.f.stairs(0, -1, false)));
            p.box(-2, y - 1, v, 2, y - 1, v, MAS);
            for (int s = -1; s <= 1; s += 2) {
                p.box(3 * s, y, v, 3 * s, y + 4, v, (v & 1) == 0 ? GLY : ELD);
                if (v % 4 == 0) p.set(3 * s, y + 3, v, SEA_LANTERN);
            }
        }
        int a0 = Math.max(-12, p.u0), a1 = Math.min(12, p.u1), b0 = Math.max(-7, p.v0), b1 = Math.min(17, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int w = Math.abs(a);
                if (w <= 1 && b <= 12) p.f.set(a, HF, b, OBSIDIAN);
                else if (w == 4 && b <= 12) p.f.set(a, HF, b, PRISMARINE, 2);
                else p.f.paving(a, HF, b);
            }
        for (int s = -1; s <= 1; s += 2)
            for (int v = -3; v <= 12; v += 5) {
                p.set(6 * s, HF + 1, v, BRICK, 3);
                for (int y = HF + 2; y <= -3; y++) p.put(6 * s, y, v, y == -6 ? GLY : MAS);
                p.set(6 * s, -2, v, BRICK, 3);
            }
        for (int v = -3; v <= 13; v += 8) p.set(0, 0, v, SEA_LANTERN);
        // the kings' niches: a sarcophagus and its skull in each
        for (int s = -1; s <= 1; s += 2)
            for (int v : new int[] {-5, 5, 9, 13}) {
                p.box(14 * s, HF + 1, v - 1, 14 * s, HF + 1, v + 1, b(DOUBLE_SLAB, 5));
                p.set(14 * s, HF + 2, v, SKULL, 1);
            }
        // the side stairs up to the warrens
        for (int s = -1; s <= 1; s += 2)
            for (int k = 0; k < 4; k++) {
                int u = (14 + k) * s, y = HF + 1 + k;
                p.box(u, y, 0, u, y, 2, b(BRICK_STAIRS, p.f.stairs(s, 0, false)));
                p.box(u, HF, 0, u, y - 1, 2, MAS);
            }
        // the dais and the throne
        p.box(-5, HF + 1, 13, 5, HF + 1, 16, MAS);
        p.box(-5, HF + 1, 13, 5, HF + 1, 13, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
        p.box(-3, HF + 2, 14, 3, HF + 2, 16, b(PRISMARINE, 2));
        p.box(-3, HF + 2, 14, 3, HF + 2, 14, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
        p.set(0, HF + 3, 16, BRICK_STAIRS, p.f.stairs(0, 1, false));
        p.set(-1, HF + 3, 16, BRICK_STAIRS, p.f.stairs(-1, 0, false));
        p.set(1, HF + 3, 16, BRICK_STAIRS, p.f.stairs(1, 0, false));
        p.box(-1, HF + 3, 17, 1, -4, 17, b(OBSIDIAN, 0));
        p.set(0, -3, 17, OBSIDIAN);
        p.set(-1, -3, 17, SKULL, 1);
        p.set(1, -3, 17, SKULL, 1);
        for (int s = -1; s <= 1; s += 2) { p.set(3 * s, HF + 2, 16, MAGMA); p.set(7 * s, HF, 15, MAGMA); }
        // the kings' way: a ladder from the hall to the summit shrine
        for (int y = HF + 1; y <= 20; y++) { p.set(0, y, 2, BRICK, y == HF + 1 ? 3 : 0); p.set(0, y, 1, LADDER, p.f.facing(0, -1)); }
    }

    // ------------------------------------------------------------------------------------------------ the quarters

    private static void lbox(P p, int cu, int cv, int face, int x0, int y0, int z0, int x1, int y1, int z1, int m) {
        p.box(lu(cu, face, x0, z0), y0, lv(cv, face, x0, z0), lu(cu, face, x1, z1), y1, lv(cv, face, x1, z1), m);
    }

    private static void lset(P p, int cu, int cv, int face, int x, int y, int z, int id, int meta) { p.set(lu(cu, face, x, z), y, lv(cv, face, x, z), id, meta); }

    private static void lot(P p, int[] lot) {
        int kind = lot[0], cu = lot[1], cv = lot[2], face = lot[3], h = lot[5];
        if (!p.hit(cu - 6, cv - 6, cu + 6, cv + 6)) return;
        switch (kind) {
            case SMALL: smallTomb(p, cu, cv, face, h); break;
            case MEDIUM: mausoleum(p, cu, cv, face, lot[4] == 1, h); break;
            case GRAVES: graves(p, cu, cv, face, h); break;
            case OBELISK:
                obelisk(p, cu, cv, 11 + h % 6);
                for (int s = -1; s <= 1; s += 2) for (int q = -1; q <= 1; q += 2) p.set(cu + 4 * s, 1, cv + 4 * q, WALL, 1);
                break;
            default:
                statue(p, cu, cv, face, h % 3, 0);
                for (int s = -1; s <= 1; s += 2) { lbox(p, cu, cv, face, 4 * s, 1, 1, 4 * s, 1, 3, b(DOUBLE_SLAB, 5)); lset(p, cu, cv, face, 4 * s, 2, 2, SKULL, 1); }
        }
    }

    /** A small tomb: a plinth, four walls, a low door, a sarcophagus inside, and one of three roofs. */
    private static void smallTomb(P p, int cu, int cv, int face, int h) {
        int wall = (h & 4) != 0 ? ELD : MAS;
        lbox(p, cu, cv, face, -3, 1, -3, 3, 1, 3, b(SLAB, 5));
        lbox(p, cu, cv, face, -2, 1, -2, 2, 1, 2, b(DOUBLE_SLAB, 5));
        lbox(p, cu, cv, face, -2, 2, -2, 2, 5, 2, wall);
        lbox(p, cu, cv, face, -1, 2, -1, 1, 5, 1, AIR);
        lbox(p, cu, cv, face, 0, 2, -2, 0, 3, -2, AIR);
        for (int s = -1; s <= 1; s += 2) for (int q = -1; q <= 1; q += 2) lbox(p, cu, cv, face, 2 * s, 2, 2 * q, 2 * s, 5, 2 * q, b(BRICK, 3));
        switch (h % 3) {
            case 0:
                lbox(p, cu, cv, face, -2, 6, -2, 2, 6, 2, MAS);
                lbox(p, cu, cv, face, -1, 7, -1, 1, 7, 1, GLY);
                lset(p, cu, cv, face, 0, 8, 0, OBSIDIAN, 0);
                break;
            case 1:
                lbox(p, cu, cv, face, -2, 6, -2, 2, 6, 2, b(SLAB, 5));
                lset(p, cu, cv, face, 0, 7, 0, CAULDRON, 0);
                break;
            default:
                lbox(p, cu, cv, face, -2, 6, -2, 2, 6, 2, b(DOUBLE_SLAB, 5));
                lbox(p, cu, cv, face, -1, 7, -1, 1, 7, 1, b(SLAB, 5));
                lset(p, cu, cv, face, 0, 8, 0, SKULL, 1);
        }
        lbox(p, cu, cv, face, -1, 2, 1, 1, 2, 1, b(DOUBLE_SLAB, 5));
        lset(p, cu, cv, face, 0, 3, 1, SKULL, 1);
    }

    /** A mausoleum with a portico of two columns and a stepped roof; vault mausolea have a stair down to their burial vault. */
    private static void mausoleum(P p, int cu, int cv, int face, boolean vault, int h) {
        int wall = (h & 8) != 0 ? ELD : MAS;
        lbox(p, cu, cv, face, -3, 1, -5, 3, 1, 3, b(DOUBLE_SLAB, 5));
        lbox(p, cu, cv, face, -2, 1, -6, 2, 1, -6, b(BRICK_STAIRS, p.f.stairs(-DU[face], -DV[face], false)));
        lbox(p, cu, cv, face, -3, 2, -3, 3, 6, 3, wall);
        lbox(p, cu, cv, face, -2, 2, -2, 2, 6, 2, AIR);
        lbox(p, cu, cv, face, -1, 2, -3, 1, 4, -3, AIR);
        lbox(p, cu, cv, face, -3, 4, 0, -3, 4, 0, GLY);
        lbox(p, cu, cv, face, 3, 4, 0, 3, 4, 0, GLY);
        for (int s = -1; s <= 1; s += 2) {
            for (int q = -1; q <= 1; q += 2) lbox(p, cu, cv, face, 3 * s, 2, 3 * q, 3 * s, 7, 3 * q, b(BRICK, 3));
            lset(p, cu, cv, face, 2 * s, 2, -5, BRICK, 3);
            lbox(p, cu, cv, face, 2 * s, 3, -5, 2 * s, 5, -5, MAS);
            lset(p, cu, cv, face, 2 * s, 6, -5, BRICK, 3);
        }
        lbox(p, cu, cv, face, -3, 7, -3, 3, 7, 3, b(DOUBLE_SLAB, 5));
        lbox(p, cu, cv, face, -3, 7, -5, 3, 7, -4, b(SLAB, 5));
        if ((h & 16) != 0) {
            lbox(p, cu, cv, face, -2, 8, -2, 2, 8, 2, wall);
            lbox(p, cu, cv, face, -1, 9, -1, 1, 9, 1, GLY);
            lset(p, cu, cv, face, 0, 10, 0, OBSIDIAN, 0);
        } else {
            for (int k = 0; k <= 2; k++) lbox(p, cu, cv, face, -3 + k, 8 + k, -3, 3 - k, 8 + k, 3, k == 2 ? GLY : wall);
        }
        if (vault) {
            for (int k = 0; k <= 5; k++) {
                int z = -2 + k, y = -k;
                lbox(p, cu, cv, face, -1, y + 1, z, 1, 1, z, AIR);
                lbox(p, cu, cv, face, -1, y, z, 1, y, z, b(BRICK_STAIRS, p.f.stairs(DU[face], DV[face], false)));
                lbox(p, cu, cv, face, -1, y - 1, z, 1, y - 1, z, MAS);
            }
        } else {
            lbox(p, cu, cv, face, -1, 2, 1, 1, 2, 1, b(DOUBLE_SLAB, 5));
            lset(p, cu, cv, face, 0, 3, 1, SKULL, 1);
            if (h % 3 == 0) p.f.chest(lu(cu, face, 2, 2), 2, lv(cv, face, 2, 2), DU[face], DV[face], Sites.DUNGEON);
        }
    }

    /** A plot of graves: mounds, headstones of five kinds, and opened graves with their bones. */
    private static void graves(P p, int cu, int cv, int face, int h) {
        for (int gx = -3; gx <= 3; gx += 3)
            for (int gz = -3; gz <= 3; gz += 3) {
                int kind = (int) ((Hash.of(h, gx, gz) >>> 3) % 7);
                if (kind == 6) continue;
                if (kind == 5) {   // opened
                    lbox(p, cu, cv, face, gx, -1, gz - 1, gx, 0, gz, AIR);
                    lset(p, cu, cv, face, gx, -2, gz, BONE, 0);
                    lset(p, cu, cv, face, gx, -1, gz - 1, SKULL, 1);
                    lset(p, cu, cv, face, gx, 1, gz + 1, DIRT, 1);
                    continue;
                }
                lbox(p, cu, cv, face, gx, 0, gz - 1, gx, 0, gz, b(DIRT, 1));
                switch (kind) {
                    case 0: lset(p, cu, cv, face, gx, 1, gz + 1, WALL, 1); break;
                    case 1: lset(p, cu, cv, face, gx, 1, gz + 1, BRICK, 3); break;
                    case 2: lset(p, cu, cv, face, gx, 1, gz + 1, WALL, 0); lset(p, cu, cv, face, gx, 2, gz + 1, WALL, 0); break;
                    case 3: lset(p, cu, cv, face, gx, 1, gz + 1, STONE, 6); lset(p, cu, cv, face, gx, 2, gz + 1, SKULL, 1); break;
                    default: lset(p, cu, cv, face, gx, 1, gz + 1, SLAB, 5);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the landmarks

    /** The Charnel Pit: a round pit of bones and soul sand ten deep, a ledge round it where a warren comes in, gibbets. */
    private static void charnelPit(P p) {
        int cu = 35, cv = -35;
        if (!p.hit(cu - 12, cv - 11, cu + 11, cv + 11)) return;
        for (int a = Math.max(cu - 11, p.u0); a <= Math.min(cu + 11, p.u1); a++)
            for (int b = Math.max(cv - 11, p.v0); b <= Math.min(cv + 11, p.v1); b++) {
                double d = Math.sqrt((a - cu) * (a - cu) + (b - cv) * (b - cv));
                if (d > 10.6) continue;
                if (d <= 8.5) {
                    for (int y = -8; y <= 0; y++) p.f.set(a, y, b, AIR);
                    double q = p.f.roll(a, -9, b, 310);
                    p.f.set(a, -9, b, q < 0.4 ? BONE : q < 0.75 ? SOUL_SAND : q < 0.8 ? MAGMA : GRAVEL, 0);
                    if (d >= 2.5 && p.f.roll(a, -8, b, 311) < 0.22) p.f.set(a, -8, b, p.f.roll(a, -7, b, 312) < 0.3 ? SKULL : BONE, p.f.roll(a, -7, b, 312) < 0.3 ? 1 : 0);
                    if (d >= 7) p.f.set(a, WF, b, BRICK, 2);
                } else if (d <= 9.6) {
                    for (int y = -9; y <= 0; y++) if (!(y >= WF + 1 && y <= WF + 3 && Math.abs(a - cu) <= 1 && b > cv)) p.put(a, y, b, WARREN);
                } else if (!(Math.abs(b - cv) <= 1 && a > cu)) {
                    p.f.set(a, 1, b, WALL, 1);
                    if (((a * 7 + b * 3) & 7) == 0) { p.f.set(a, 1, b, FENCE); p.f.set(a, 2, b, FENCE); p.f.set(a, 3, b, SKULL, 1); }
                }
            }
        for (int y = -8; y <= 0; y++) p.set(cu + 8, y, cv, LADDER, p.f.facing(-1, 0));
        for (int s = -1; s <= 1; s += 2) {          // two gibbets on the rim
            int gu = cu - 10, gv = cv + 4 * s;
            for (int y = 1; y <= 5; y++) p.set(gu, y, gv, FENCE);
            p.set(gu + 1, 5, gv, FENCE);
            p.set(gu + 1, 4, gv, IRON_BARS);
            p.set(gu + 1, 3, gv, SKULL, 1);
        }
        p.box(cu - 11, 1, cv, cu - 11, 2, cv, MAS);
        p.f.sign(cu - 12, 2, cv, -1, 0, "THE PIT IS\nNEVER FULL\nTHE KINGS\nARE HUNGRY");
        p.f.spawner(cu - 3, -8, cv + 2, "SKELETON");
        p.box(cu + 1, -8, cv - 4, cu + 3, -8, cv - 4, b(BONE, 0));
        p.f.chest(cu + 2, -8, cv - 3, 0, 1, Sites.DUNGEON, "trinket:0.2");
    }

    /** The Ossuary Tower: a round tower of bone, four floors on a ladder from its cellar, a crown of bone spikes. */
    private static void ossuary(P p) {
        int cu = -35, cv = 35, top = 36;
        if (!p.hit(cu - 10, cv - 10, cu + 10, cv + 10)) return;
        for (int a = Math.max(cu - 10, p.u0); a <= Math.min(cu + 10, p.u1); a++)
            for (int b = Math.max(cv - 10, p.v0); b <= Math.min(cv + 10, p.v1); b++) {
                int da = a - cu, db = b - cv;
                double d = Math.sqrt(da * da + db * db);
                if (d > 6.5) {
                    if (d > 9.6) continue;
                    if (d > 8.6) { if (!(Math.abs(db) <= 1 && da > 0)) p.f.set(a, 1, b, IRON_BARS); continue; }
                    double q = p.f.roll(a, 1, b, 313);
                    if (Math.abs(db) <= 1 && da > 0) continue;
                    if (q < 0.3) { p.f.set(a, 1, b, BONE, 0); if (q < 0.12) p.f.set(a, 2, b, q < 0.05 ? SKULL : BONE, q < 0.05 ? 1 : 0); }
                    continue;
                }
                boolean wall = d > 4.5;
                for (int y = 1; y <= top; y++) {
                    if (wall) {
                        boolean window = y % 9 >= 4 && y % 9 <= 5 && (da == 0 || db == 0) && y > 3;
                        if (window) p.f.set(a, y, b, AIR);
                        else if (y % 9 == 0) p.f.set(a, y, b, BRICK, 3);
                        else p.f.set(a, y, b, BONE, 0);
                    } else if (y % 9 == 0) p.f.set(a, y, b, y == top ? 216 : DOUBLE_SLAB, y == top ? 0 : 5);
                    else p.f.set(a, y, b, AIR);
                }
                if (wall && d > 5.5) {
                    p.f.set(a, top + 1, b, BONE, 0);
                    if (((da + db) & 1) == 0) p.f.set(a, top + 2, b, SKULL, 1);
                }
            }
        p.box(cu + 5, 1, cv, cu + 6, 3, cv, AIR);
        for (int y = WF + 1; y <= top; y++) p.set(cu - 4, y, cv, LADDER, p.f.facing(1, 0));
        for (int k = 0; k < 8; k++) {
            int su = (int) Math.round(Math.cos(k * Math.PI / 4) * 6), sv = (int) Math.round(Math.sin(k * Math.PI / 4) * 6), len = 3 + (k * 5 + 3) % 4;
            for (int y = top + 1; y <= top + len; y++) p.set(cu + su, y, cv + sv, BONE, 0);
            p.set(cu + su, top + len + 1, cv + sv, SKULL, 1);
        }
        p.set(cu, top - 1, cv, GLOWSTONE);
        p.f.spawner(cu + 2, 10, cv, "SKELETON");
        p.f.chest(cu + 2, 28, cv + 2, -1, 0, Sites.LIBRARY, "lore:" + ((cu * 31 + cv) & 63));
        p.f.sign(cu + 7, 5, cv, 1, 0, "BONE OF\nTHE CITY\nCLIMB AND\nBE COUNTED");
    }

    /** The Mausoleum of the First King: a stepped base, a peristyle, a dome; the kings' biers inside, a stair to his vault. */
    private static void firstKing(P p) {
        int cu = 35, cv = 35;
        if (!p.hit(cu - 8, cv - 9, cu + 8, cv + 8)) return;
        p.box(cu - 8, 1, cv - 8, cu + 8, 1, cv + 8, b(DOUBLE_SLAB, 5));
        p.box(cu - 7, 2, cv - 7, cu + 7, 2, cv + 7, MAS);
        p.box(cu - 2, 1, cv - 9, cu + 2, 1, cv - 9, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
        p.box(cu - 2, 2, cv - 8, cu + 2, 2, cv - 8, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
        for (int a = Math.max(cu - 7, p.u0); a <= Math.min(cu + 7, p.u1); a++)
            for (int b = Math.max(cv - 7, p.v0); b <= Math.min(cv + 7, p.v1); b++) {
                int m = Math.max(Math.abs(a - cu), Math.abs(b - cv));
                if (m == 7 && ((a - cu + b - cv) & 1) == 0 && !(b == cv - 7 && Math.abs(a - cu) <= 2)) {
                    p.f.set(a, 3, b, BRICK, 3);
                    for (int y = 4; y <= 11; y++) p.f.masonry(a, y, b);
                    p.f.set(a, 12, b, BRICK, 3);
                } else if (m == 5) for (int y = 3; y <= 12; y++) p.put(a, y, b, y == 8 ? GLY : ELD);
                else if (m < 5) for (int y = 3; y <= 12; y++) p.f.set(a, y, b, AIR);
                p.f.set(a, 13, b, DOUBLE_SLAB, 5);
                double r = Math.sqrt((a - cu) * (a - cu) + (b - cv) * (b - cv));
                if (r <= 6.4) {
                    int dome = (int) Math.round(Math.sqrt(Math.max(0, 41 - r * r)));
                    for (int y = 14; y <= 13 + dome; y++) {
                        double rr = Math.sqrt(r * r + (y - 13) * (y - 13));
                        if (rr > 5.2) p.put(a, y, b, (y & 3) == 0 ? GLY : ELD);
                        else p.f.set(a, y, b, AIR);
                    }
                    if (r < 5) p.f.set(a, 13, b, AIR);
                }
            }
        p.set(cu, 20, cv, OBSIDIAN);
        p.set(cu, 21, cv, SKULL, 1);
        p.set(cu, 18, cv, GLOWSTONE);
        p.box(cu - 1, 3, cv - 5, cu + 1, 6, cv - 5, AIR);
        for (int s = -1; s <= 1; s += 2)
            for (int q = -1; q <= 1; q += 2) {
                p.box(cu + 3 * s, 3, cv + 3 * q, cu + 3 * s, 3, cv + 3 * q + 1, b(DOUBLE_SLAB, 5));
                p.set(cu + 3 * s, 4, cv + 3 * q, SKULL, 1);
            }
        for (int k = 0; k <= 6; k++) {
            int v = cv - 1 + k, y = 1 - k;
            p.box(cu - 1, y + 1, v, cu + 1, 2, v, AIR);
            p.box(cu - 1, y, v, cu + 1, y, v, b(BRICK_STAIRS, p.f.stairs(0, -1, false)));
            p.box(cu - 1, y - 1, v, cu + 1, y - 1, v, MAS);
        }
        p.f.sign(cu + 2, 5, cv - 6, 0, -1, "THE FIRST\nKING SLEEPS\nHUNGRY");
        p.f.chest(cu - 4, 3, cv + 4, 1, 0, Sites.JUNGLE, "trinket:0.25");
        p.f.chest(cu + 4, WF + 1, cv - 4, -1, 0, "minecraft:chests/stronghold_crossing", "lore:" + ((cu + cv) & 63) + ";trinket:0.3");
        p.f.spawner(cu - 4, WF + 1, cv - 4, "ZOMBIE");
        p.set(cu, 2, cv - 5, PLATE);
        p.f.dispenser(cu, 1, cv - 5, 0, 0);
    }

    /** The Garden of Graves: a fenced cemetery round a chapel whose crypt joins the warrens. */
    private static void garden(P p) {
        int cu = -35, cv = -35;
        if (!p.hit(cu - 9, cv - 9, cu + 9, cv + 9)) return;
        for (int a = Math.max(cu - 9, p.u0); a <= Math.min(cu + 9, p.u1); a++)
            for (int b = Math.max(cv - 9, p.v0); b <= Math.min(cv + 9, p.v1); b++) {
                int da = a - cu, db = b - cv, m = Math.max(Math.abs(da), Math.abs(db));
                if (m == 9) {
                    if (Math.abs(da) <= 1 && db == 9 || Math.abs(db) <= 1 && da == 9) continue;
                    p.f.set(a, 1, b, WALL, 1);
                    if (p.f.roll(a, 2, b, 314) < 0.7) p.f.set(a, 2, b, IRON_BARS);
                } else if (da == 0 || db == 0) p.f.set(a, 0, b, p.f.roll(a, 0, b, 315) < 0.5 ? GRAVEL : COBBLE);
            }
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                if (i == 0 || j == 0) continue;
                int gu = cu + 3 * i + (i > 0 ? 1 : -1), gv = cv + 4 * j - (j > 0 ? 1 : 0);
                if (Math.abs(gu - cu) <= 4 && Math.abs(gv - cv) <= 5) continue;
                if (!p.hit(gu, gv - 1, gu, gv + 1)) continue;
                long g = Hash.of(0x6A7EL, gu, gv);
                p.box(gu, 0, gv - 1, gu, 0, gv, b(DIRT, 1));
                int k = (int) ((g >>> 5) % 5);
                if (k == 0) p.set(gu, 1, gv + 1, WALL, 1);
                else if (k == 1) { p.set(gu, 1, gv + 1, WALL, 0); p.set(gu, 2, gv + 1, WALL, 0); }
                else if (k == 2) p.set(gu, 1, gv + 1, BRICK, 3);
                else if (k == 3) { p.set(gu, 1, gv + 1, STONE, 6); p.set(gu, 2, gv + 1, SKULL, 1); }
                else p.set(gu, 1, gv + 1, SLAB, 5);
            }
        // the chapel: door toward the city (+v), an altar at the back, a stair down to the crypt
        p.box(cu - 3, 1, cv - 4, cu + 3, 6, cv + 4, MAS);
        p.box(cu - 2, 1, cv - 3, cu + 2, 6, cv + 3, AIR);
        p.box(cu - 1, 1, cv + 4, cu + 1, 3, cv + 4, AIR);
        for (int k = 0; k <= 3; k++) p.box(cu - 3 + k, 7 + k, cv - 4, cu + 3 - k, 7 + k, cv + 4, k == 3 ? GLY : ELD);
        for (int s = -1; s <= 1; s += 2) p.box(cu + 3 * s, 3, cv - 1, cu + 3 * s, 4, cv + 1, b(IRON_BARS, 0));
        p.set(cu, 1, cv - 3, OBSIDIAN);
        p.set(cu, 2, cv - 3, SKULL, 1);
        p.set(cu, 6, cv, GLOWSTONE);
        for (int k = 0; k <= 4; k++) {
            int v = cv + 2 - k, y = -1 - k;
            p.box(cu - 1, y + 1, v, cu + 1, 0, v, AIR);
            p.box(cu - 1, y, v, cu + 1, y, v, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
            p.box(cu - 1, y - 1, v, cu + 1, y - 1, v, MAS);
        }
        p.f.chest(cu + 3, WF + 1, cv - 3, -1, 0, Sites.CORRIDOR, "lore:" + ((cu * 7 + cv * 3) & 63));
    }

    // ------------------------------------------------------------------------------------------------ tiles

    private static void tiles(P p, Plans.GreatSite s, L l) {
        Frame f = p.f;
        long h = s.hash;
        f.sign(0, 9, -52, 0, -1, "THE NECROPOLIS\nOF THE\nGHOUL-KINGS");
        f.sign(3, 3, -19, 0, -1, "HERE THE KINGS\nFEAST FOREVER");
        f.sign(-3, 3, -19, 0, -1, "DESCEND AND\nBE EATEN");
        f.sign(4, -6, -7, 0, 1, "BOW TO THE\nGHOUL-KING\nOR FEED\nHIS COURT");
        f.chest(4, HF + 2, 15, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        f.chest(-4, HF + 2, 15, 0, -1, "minecraft:chests/woodland_mansion", "trinket:0.4");
        f.chest(-13, HF + 1, 8, 1, 0, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h), 0, 99));
        f.spawner(10, HF + 1, 15, "HUSK");
        f.spawner(-10, HF + 1, 15, "HUSK");
        f.set(0, HF + 1, -6, PLATE, 0);
        f.dispenser(0, HF, -6, 0, 0);
        f.chest(2, 21, 2, 0, -1, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99) + ";trinket:0.2");
        f.chest(2, 11, -48, 0, 1, Sites.SMITH);
        f.chest(48, 11, 48, -1, 0, "minecraft:chests/abandoned_mineshaft");
        f.chest(-48, 11, 48, 1, 0, "minecraft:chests/stronghold_corridor");
        f.chest(48, 1, -48, -1, 0, "minecraft:chests/igloo_chest");
        String[] tables = {Sites.DUNGEON, Sites.CORRIDOR, Sites.JUNGLE, "minecraft:chests/nether_bridge", Sites.DUNGEON, Sites.DESERT};
        int k = 0;
        for (int[] lot : l.lots) {
            if (lot[4] != 1) continue;
            int cu = lot[1], cv = lot[2], face = lot[3];
            f.chest(lu(cu, face, 3, 3), WF + 1, lv(cv, face, 3, 3), DU[face], DV[face], tables[k % tables.length], k % 2 == 0 ? "lore:" + ((cu * 13 + cv) & 63) : "trinket:0.15");
            k++;
        }
    }
}
