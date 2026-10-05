package chat.jaspr.ruins;

import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Drowned Cathedral (epoch 6): a gothic cathedral half sunk in the shallows. A causeway runs out to its west front
 * (two towers, one of them broken, and a rose window over the portal); the nave is flooded to the sea's level between
 * the dry walkways of its aisles, with a triforium gallery above the arcades, a clerestory and flying buttresses outside;
 * the crossing carries a lantern tower and a spire high over the sea, and its stairs climb out of the water to the dry
 * choir and the high altar, where the Drowned Bishop waits. Under the choir lies the flooded crypt of the bishops; beside
 * the nave the cloister round its drowned garth and the sacristy; across a bridge the bell tower; round it all a drowned
 * graveyard. Heights are planned in world y: the sea's top water block is y 62.
 */
final class GreatCathedral extends GreatDesign {
    private static final int R = 48, SEA = Plans.SEA;
    /** World y: the flooded nave's floor, the dry walkways' floor, the choir's floor. */
    private static final int NF = 57, DRY = 63, CH = 64;
    private static final int MAS = -1, ELD = -2, GLY = -3, RUB = -4, PAV = -5, FLUID = -7, ROOF = -8;

    private static int b(int id, int meta) { return id << 4 | meta; }

    private static final class L extends Layout {
        int broken;     // the side (sign of u) of the broken west tower
        int cf;         // the crypt's floor (world y)
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        l.broken = r.nextBoolean() ? 1 : -1;
        l.cf = Math.max(48, s.base - 11);
        int k = l.broken;
        l.boss = new int[] {0, CH + 1 - s.base, 27};
        l.garrisons.add(g(0, 64 - s.base, -44, "deep_one+zombie"));                 // the causeway
        l.garrisons.add(g(-3, 64 - s.base, -33, "cult_zealot+cult_adept"));         // the narthex landing
        l.garrisons.add(g(0, 60 - s.base, -18, "guardian"));                        // the flooded nave
        l.garrisons.add(g(0, 60 - s.base, 0, "guardian+guardian"));
        l.garrisons.add(g(-10, 64 - s.base, -15, "zombie_villager+husk"));          // the north aisle
        l.garrisons.add(g(10, 64 - s.base, -5, "skeleton+stray"));                  // the south aisle
        l.garrisons.add(g(-10, 76 - s.base, -9, "spider+silverfish"));              // the triforium
        l.garrisons.add(g(-20, 64 - s.base, 13, "hound+creeper"));                  // the north transept
        l.garrisons.add(g(20, 64 - s.base, 13, "mi_go+enderman"));                  // the south transept
        l.garrisons.add(g(0, l.cf + 2 - s.base, 28, "guardian"));                   // the crypt
        l.garrisons.add(g(-10, 64 - s.base, 30, "vindicator+evoker"));              // the choir's aisle
        l.garrisons.add(g(10, 64 - s.base, 33, "tomb_crawler+cave_spider"));
        l.garrisons.add(g(-12 * k, 64 - s.base, -33, "witch+illusioner"));          // the whole tower's foot
        l.garrisons.add(g(12 * k, 90 - s.base, -33, "star_spawn+wither_skeleton")); // the broken tower's top
        l.garrisons.add(g(-34, 100 - s.base, -19, "nightgaunt"));                   // the belfry
        l.garrisons.add(g(23, 64 - s.base, -28, "shoggoth+slime"));                 // the cloister walk
        l.garrisons.add(g(28, 60 - s.base, -24, "guardian+guardian"));              // the drowned garth
        l.garrisons.add(g(41, 64 - s.base, -37, "ghoul+endermite"));                // the sacristy
        return l;
    }

    // ------------------------------------------------------------------------------------------------ the drawing tools

    /** The chunk's share of the footprint in local coordinates, and drawing tools clipped to it (heights in world y). */
    private static final class P {
        final Plans.GreatSite s; final Frame f; final int u0, u1, v0, v1, base;
        private final int[] ground = new int[256];
        private final boolean[] known = new boolean[256];

        P(Plans.GreatSite s, Canvas c) {
            this.s = s;
            base = s.base;
            f = new Frame(c, s.x, s.z, s.base, s.rot);
            int xa = c.x0 - s.x, xb = xa + 15, za = c.z0 - s.z, zb = za + 15;
            switch (s.rot & 3) {
                case 1: u0 = za; u1 = zb; v0 = -xb; v1 = -xa; break;
                case 2: u0 = -xb; u1 = -xa; v0 = -zb; v1 = -za; break;
                case 3: u0 = -zb; u1 = -za; v0 = xa; v1 = xb; break;
                default: u0 = xa; u1 = xb; v0 = za; v1 = zb;
            }
        }

        /** The land's height (world y) at a column of this chunk. */
        int ground(int a, int b) {
            int i = (a - u0) << 4 | (b - v0);
            if (!known[i]) { known[i] = true; ground[i] = s.surface(f.wx(a, b), f.wz(a, b)); }
            return ground[i];
        }

        boolean hit(int a0, int b0, int a1, int b1) { return a1 >= u0 && a0 <= u1 && b1 >= v0 && b0 <= v1; }
        boolean in(int a, int b) { return a >= u0 && a <= u1 && b >= v0 && b <= v1; }

        /** One block at world height wy. */
        void w(int a, int wy, int b, int m) {
            int y = wy - base;
            switch (m) {
                case MAS: f.masonry(a, y, b); return;
                case ELD: f.eldritch(a, y, b); return;
                case GLY: f.glyph(a, y, b); return;
                case RUB: f.rubble(a, y, b); return;
                case PAV: f.paving(a, y, b); return;
                case FLUID: f.set(a, y, b, wy <= SEA ? Canvas.WATER : AIR); return;
                case ROOF: { double q = f.roll(a, y, b, 401); f.set(a, y, b, q < 0.55 ? PRISMARINE : q < 0.8 ? BRICK : CLAY, q < 0.55 ? 2 : q < 0.8 ? 1 : 9); return; }
                default: f.set(a, y, b, m >> 4, m & 15);
            }
        }

        void w(int a, int wy, int b, int id, int meta) { if (in(a, b)) f.set(a, wy - base, b, id, meta); }

        /** A box in world heights, clipped to the chunk. */
        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int aa = Math.max(Math.min(a0, a1), u0), ab = Math.min(Math.max(a0, a1), u1), ba = Math.max(Math.min(b0, b1), v0), bb = Math.min(Math.max(b0, b1), v1);
            for (int a = aa; a <= ab; a++) for (int b = ba; b <= bb; b++) for (int y = y0; y <= y1; y++) w(a, y, b, m);
        }

        /** Footing from the land up to world y (rubble under masonry), nothing where the land is higher. */
        void foot(int a, int b, int top) {
            int t = ground(a, b);
            for (int y = Math.max(t + 1, base - 12); y <= top; y++) w(a, y, b, top - y < 3 ? MAS : RUB);
        }

        void tile(int kind, int a, int wy, int b, int dx, int dz, String what, String extras) {
            int y = wy - base;
            if (kind == 0) f.chest(a, y, b, dx, dz, what, extras);
            else if (kind == 1) f.spawner(a, y, b, what);
            else f.sign(a, y, b, dx, dz, what);
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) {
        L l = (L) layout;
        P p = new P(s, c);
        if (!p.hit(-R, -R, R, R)) return;
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int t = p.ground(a, b);
                shore(p, a, b, t);
                body(p, l, a, b, t);
            }
        westFront(p, l);
        crossingTower(p);
        buttresses(p);
        bellTower(p);
        cloister(p);
        causeway(p);
        graveyard(p);
        choir(p, l);
        crypt(p, l);
        tiles(p, s, l);
    }

    /**
     * Land above the sea inside the footprint is cut down to a terrace at y 63 (the field's debris with it), a retaining
     * wall where it rises at the edge; the sea floor is left as it lies.
     */
    private static void shore(P p, int a, int b, int t) {
        if (t < DRY) return;
        boolean edge = Math.max(Math.abs(a), Math.abs(b)) >= R - 1;
        for (int y = DRY + 1; y <= t + 3; y++) p.w(a, y, b, edge && y <= t ? MAS : AIR);
        p.w(a, DRY, b, edge ? MAS : PAV);
    }

    private static int vaultY(int d) { return 95 - d * d / 7; }
    private static int roofY(int d) { return 101 - d; }

    /** Stained glass by bay: the drowned colours (blue, cyan, purple, green), some panes long gone. */
    private static void glass(P p, int a, int wy, int b, int bay) {
        if (p.f.roll(a, wy - p.base, b, 402) < 0.18) { p.w(a, wy, b, b(AIR, 0)); return; }
        int[] cols = {11, 9, 10, 13, 3};
        p.w(a, wy, b, b(95, cols[Math.floorMod(bay, cols.length)]));
    }

    /** The church's own columns: the nave and the choir with their aisles, the crossing, the transepts, the apse. */
    private static void body(P p, L l, int a, int b, int t) {
        int au = Math.abs(a), sa = a < 0 ? -1 : 1;
        if (b < -31 || b > 46 || au > 28) return;
        // the crossing piers carry the lantern tower
        if (au >= 6 && au <= 8 && (b >= 6 && b <= 8 || b >= 18 && b <= 20)) {
            p.foot(a, b, DRY - 1);
            for (int y = DRY; y <= 111; y++) p.w(a, y, b, y % 8 == 0 ? GLY : ELD);
            return;
        }
        if (b <= 6 || b >= 20 && b <= 37) {
            boolean choir = b >= 20;
            if (au <= 6) { if (choir) choirFloor(p, l, a, b, t); else naveFloor(p, a, b, t); vaultAndRoof(p, a, b, au, choir || b > -31); return; }
            if (b < -27) return;                                   // the west towers stand here
            int k = choir ? Math.floorMod(b - 20, 5) : Math.floorMod(b + 27, 5), bay = choir ? (b - 20) / 5 + 9 : (b + 27) / 5;
            if (au == 7) arcade(p, a, b, k, bay, choir, sa);
            else if (au <= 12) aisle(p, a, b, k, choir && b == 37 || false);
            else if (au == 13) outerWall(p, a, b, k, bay, sa);
            if (choir && b == 37 && au >= 8 && au <= 13) for (int y = DRY + 1; y <= 81; y++) p.w(a, y, b, y == 74 ? GLY : MAS);
            return;
        }
        if (b >= 7 && b <= 19) { crossing(p, a, b, au, t); return; }
        apse(p, a, b, au, t);
    }

    private static void naveFloor(P p, int a, int b, int t) {
        p.foot(a, b, NF - 1);
        p.w(a, NF, b, Math.abs(a) <= 1 ? b(PRISMARINE, 2) : (a + b & 1) == 0 ? b(BRICK, 0) : PAV);
        for (int y = NF + 1; y <= DRY; y++) p.w(a, y, b, FLUID);
        if (b <= -28 && Math.abs(a) <= 1) {                     // steps down from the narthex into the water
            int y = SEA + (b + 31) * -1;
            p.w(a, y, b, BRICK_STAIRS, p.f.stairs(0, -1, false));
            for (int yy = NF + 1; yy < y; yy++) p.w(a, yy, b, MAS);
        }
    }

    private static void choirFloor(P p, L l, int a, int b, int t) {
        boolean crypt = b >= 21 && b <= 36;
        if (crypt) {
            p.foot(a, b, l.cf - 1);
            p.w(a, l.cf, b, PAV);
            for (int y = l.cf + 1; y <= DRY; y++) p.w(a, y, b, FLUID);
        } else p.foot(a, b, CH - 1);
        p.w(a, CH, b, (a + b & 1) == 0 ? b(PRISMARINE, 2) : b(BRICK, 0));
        for (int y = CH + 1; y <= CH + 2; y++) if (t >= y - 3) p.w(a, y, b, b(AIR, 0));
    }

    /** The high vault (two blocks of ribbed masonry) and the steep roof over the nave and the choir. */
    private static void vaultAndRoof(P p, int a, int b, int au, boolean vault) {
        int vy = vaultY(au), ry = roofY(au);
        if (vault) {
            p.w(a, vy, b, au == 0 || Math.floorMod(b + 27, 5) == 0 ? b(BRICK, 3) : MAS);
            p.w(a, vy + 1, b, MAS);
        }
        p.w(a, ry, b, ROOF);
        p.w(a, ry - 1, b, ROOF);
        if (au == 0 && Math.floorMod(b, 4) == 0) p.w(a, ry + 1, b, b(WALL, 1));
    }

    /** The arcade between the nave and an aisle: piers and pointed arches, the triforium's openings, the clerestory. */
    private static void arcade(P p, int a, int b, int k, int bay, boolean choir, int sa) {
        p.foot(a, b, DRY - 1);
        p.w(a, DRY, b, choir ? MAS : b(BRICK, 3));
        int arch = k == 1 || k == 4 ? 70 : 72;
        for (int y = DRY + 1; y <= 75; y++) {
            if (k == 0) p.w(a, y, b, y == 70 ? GLY : y == DRY + 1 || y == 75 ? b(BRICK, 3) : MAS);
            else if (y > arch) p.w(a, y, b, MAS);
            else if (choir && y == DRY + 1) p.w(a, y, b, BRICK_STAIRS, p.f.stairs(-sa, 0, false));
        }
        for (int y = 76; y <= 79; y++) if (k == 0 || k == 1 || k == 4 || y == 79) p.w(a, y, b, MAS);
        p.w(a, 80, b, GLY);
        for (int y = 81; y <= 93; y++) {
            if ((k == 2 || k == 3) && y >= 82 && y <= 90) glass(p, a, y, b, bay);
            else p.w(a, y, b, MAS);
        }
    }

    /** An aisle: the dry walkway on its foundation, the aisle vault, the triforium gallery above, the lean-to roof. */
    private static void aisle(P p, int a, int b, int k, boolean end) {
        int au = Math.abs(a);
        p.foot(a, b, DRY - 1);
        p.w(a, DRY, b, k == 0 ? b(BRICK, 3) : PAV);
        if (k == 0) for (int y = 72; y <= 73; y++) p.w(a, y, b, MAS);
        p.w(a, 74, b, MAS);
        p.w(a, 75, b, k == 0 ? b(BRICK, 3) : b(DOUBLE_SLAB, 5));
        p.w(a, 80, b, MAS);
        int lean = 81 + (13 - au) / 2;
        for (int y = 81; y <= lean; y++) p.w(a, y, b, y == lean ? ROOF : MAS);
    }

    /** An aisle's outer wall: tall lancets of stained glass in every bay, doors to the bell tower and the cloister. */
    private static void outerWall(P p, int a, int b, int k, int bay, int sa) {
        p.foot(a, b, DRY);
        boolean door = sa < 0 ? b >= -20 && b <= -18 : b >= -15 && b <= -13;
        for (int y = DRY + 1; y <= 81; y++) {
            if (door && y <= DRY + 3) { p.w(a, y, b, b(AIR, 0)); continue; }
            if ((k == 2 || k == 3) && y >= 66 && y <= 77) glass(p, a, y, b, bay + 2);
            else p.w(a, y, b, y == 74 ? GLY : y % 7 == 0 ? ELD : MAS);
        }
        if (((b + k) & 1) == 0) p.w(a, 82, b, MAS);
    }

    /** The crossing (flooded, with the stairs up to the choir) and the dry transepts with their end walls and rose. */
    private static void crossing(P p, int a, int b, int au, int t) {
        int db = Math.abs(b - 13);
        if (au <= (b <= 8 || b >= 18 ? 5 : 6)) {
            if (b >= 18) { p.foot(a, b, CH - 1); p.w(a, CH, b, b(PRISMARINE, 2)); return; }
            p.foot(a, b, NF - 1);
            boolean shaft = au <= 1 && b >= 9 && b <= 11;
            p.w(a, NF, b, shaft ? FLUID : PAV);
            for (int y = NF + 1; y <= DRY; y++) p.w(a, y, b, FLUID);
            if (b >= 12) {
                int y = 46 + b;
                for (int yy = NF + 1; yy < y; yy++) p.w(a, yy, b, MAS);
                p.w(a, y, b, BRICK_STAIRS, p.f.stairs(0, 1, false));
            }
            return;
        }
        if (au <= 8) {                                              // the great arches into the transepts
            p.foot(a, b, DRY - 1);
            p.w(a, DRY, b, au == 7 ? b(BRICK, 3) : PAV);
            int apex = 88 - db * db / 2;
            for (int y = apex + 1; y <= 93; y++) p.w(a, y, b, y == 93 ? GLY : MAS);
            return;
        }
        boolean side = b == 7 || b == 19;
        if (au == 28) {                                             // the transept's end wall, its door and its rose
            p.foot(a, b, DRY);
            int top = roofY(Math.min(7, db));
            for (int y = DRY + 1; y <= top; y++) {
                double dr = Math.sqrt(db * db + (y - 82) * (y - 82));
                if (db <= 1 && y <= DRY + 4) p.w(a, y, b, b(AIR, 0));
                else if (dr <= 4.4) p.w(a, y, b, dr < 1.5 ? b(95, 4) : (db == 0 || y == 82) ? MAS : b(95, dr < 3 ? 14 : 11));
                else p.w(a, y, b, y % 9 == 0 ? GLY : MAS);
            }
            return;
        }
        if (side && au >= 13) {
            p.foot(a, b, DRY);
            boolean win = au == 16 || au == 17 || au == 22 || au == 23;
            for (int y = DRY + 1; y <= 93; y++) {
                if (win && y >= 68 && y <= 86) glass(p, a, y, b, au);
                else p.w(a, y, b, y == 80 ? GLY : MAS);
            }
            return;
        }
        p.foot(a, b, DRY - 1);
        p.w(a, DRY, b, au % 5 == 0 && !side ? b(BRICK, 3) : PAV);
        if (side) { for (int y = 74; y <= 93; y++) p.w(a, y, b, y == 80 ? GLY : MAS); return; }
        int vy = vaultY(Math.min(6, db));
        p.w(a, vy, b, au % 5 == 0 ? b(BRICK, 3) : MAS);
        p.w(a, vy + 1, b, MAS);
        p.w(a, roofY(db), b, ROOF);
        p.w(a, roofY(db) - 1, b, ROOF);
    }

    /** The apse: a half-round of tall lancets behind the high altar under a conical roof. */
    private static void apse(P p, int a, int b, int au, int t) {
        double r = Math.sqrt(a * a + (b - 37) * (b - 37));
        if (r >= 8.5) return;
        if (r < 6.5) {
            p.foot(a, b, CH - 1);
            p.w(a, CH, b, b(PRISMARINE, 2));
            int d = (int) Math.round(r);
            p.w(a, vaultY(Math.min(6, d)), b, MAS);
            p.w(a, roofY(d), b, ROOF);
            p.w(a, roofY(d) - 1, b, ROOF);
            return;
        }
        p.foot(a, b, CH);
        double ang = Math.toDegrees(Math.atan2(a, b - 37));
        boolean win = false;
        for (int w = -72; w <= 72; w += 36) if (Math.abs(ang - w) < 7) win = true;
        for (int y = CH + 1; y <= 93; y++) {
            if (win && y >= 68 && y <= 88) glass(p, a, y, b, (int) ang);
            else p.w(a, y, b, y == 80 ? GLY : MAS);
        }
        p.w(a, roofY((int) Math.round(r)), b, ROOF);
    }

    // ------------------------------------------------------------------------------------------------ the west front

    /** The facade with its portal and rose window, the narthex landing behind it, and the two west towers. */
    private static void westFront(P p, L l) {
        if (!p.hit(-18, -39, 18, -28)) return;
        for (int a = Math.max(-17, p.u0); a <= Math.min(17, p.u1); a++)
            for (int b = Math.max(-38, p.v0); b <= Math.min(-28, p.v1); b++) {
                int au = Math.abs(a);
                if (au <= 6) {
                    if (b <= -36) facade(p, a, b, au);
                    else if (b <= -32) {
                        p.foot(a, b, DRY - 1);
                        p.w(a, DRY, b, au <= 1 ? b(PRISMARINE, 2) : PAV);
                        if (b == -32 && au >= 2) p.w(a, DRY + 1, b, b(WALL, 1));
                        vaultAndRoof(p, a, b, au, true);
                    }
                    continue;
                }
                tower(p, l, a, b, au, a < 0 ? -1 : 1);
            }
    }

    private static void facade(P p, int a, int b, int au) {
        p.foot(a, b, DRY);
        int top = 104 - au;
        int portal = au <= 1 ? 75 : au == 2 ? 74 : au == 3 ? 72 : 0;
        for (int y = DRY + 1; y <= top; y++) {
            double dr = Math.sqrt(au * au + (y - 84) * (y - 84));
            if (y <= portal) { p.w(a, y, b, b(AIR, 0)); continue; }
            if (dr <= 6.2) {
                if (b != -37) { p.w(a, y, b, dr > 5.6 ? b(BRICK, 3) : b(AIR, 0)); continue; }
                double ang = Math.toDegrees(Math.atan2(a, y - 84));
                boolean spoke = dr > 2.2 && Math.abs(((ang % 30) + 30) % 30 - 15) > 11.5;
                if (dr > 5.6 || dr > 1.6 && dr < 2.3 || dr > 4.4 && dr < 5.0 || spoke) p.w(a, y, b, b(BRICK, 3));
                else if (p.f.roll(a, y - p.base, b, 403) < 0.12) p.w(a, y, b, b(AIR, 0));
                else p.w(a, y, b, b(95, dr < 1.6 ? 4 : dr < 4.4 ? ((int) Math.floor((ang + 360) / 30) & 1) == 0 ? 14 : 11 : 10));
                continue;
            }
            if (au == 4 && y <= 76) p.w(a, y, b, b(BRICK, 3));
            else p.w(a, y, b, y % 6 == 0 ? GLY : y > 96 ? MAS : ELD);
        }
        if (b == -38 && au <= 5 && y0(au)) p.w(a, top + 1, b, b(WALL, 1));
        if (au == 5 && b == -38) p.w(a, 68, b, SEA_LANTERN, 0);
    }

    private static boolean y0(int au) { return (au & 1) == 0; }

    /** A west tower: two-thick walls, four floors on a ladder; the whole one wears a spire, the broken one ends in a jagged crown. */
    private static void tower(P p, L l, int a, int b, int au, int sa) {
        boolean broken = sa == l.broken;
        boolean wall = au <= 8 || au >= 16 || b <= -37 || b >= -29;
        int top = broken ? 89 + (int) (Hash.unit(Hash.of(0x7A3E5L, a, b)) * 9) : 112;
        p.foot(a, b, DRY - 1);
        if (!broken) {          // the spire: a hollow pyramid of dark stone over the tower, an obsidian tip
            int du = au - 12, dv = b + 33;
            for (int h = 0; h <= 15; h++) if (Math.max(Math.abs(du), Math.abs(dv)) == 5 - h / 3) p.w(a, 113 + h, b, ROOF);
            if (du == 0 && dv == 0) { p.w(a, 129, b, OBSIDIAN, 0); p.w(a, 130, b, b(WALL, 0)); p.w(a, 131, b, b(WALL, 0)); }
        }
        if (!wall) {
            p.w(a, DRY, b, PAV);
            for (int fl : new int[] {76, 89, 102}) {
                if (fl > top || broken && fl == 89 && p.f.roll(a, fl - p.base, b, 404) < 0.3) continue;
                p.w(a, fl, b, b(DOUBLE_SLAB, 5));
            }
            if (broken && p.f.roll(a, 90 - p.base, b, 405) < 0.12 && !(au == 12 && b == -33)) p.w(a, 90, b, RUB);
            if (au == 15 && b == -33) for (int y = DRY + 1; y < (broken ? 89 : 103); y++) p.w(a, y, b, LADDER, p.f.facing(-sa, 0));
            if (!broken && au == 12 && b == -33) p.w(a, 101, b, GLOWSTONE, 0);
            return;
        }
        boolean corner = (au == 7 || au == 17) && (b == -38 || b == -28);
        for (int y = DRY; y <= top; y++) {
            if (broken && y > 92 && !p.f.keep(a, y - p.base, b, 1.0 - (y - 92) * 0.09)) break;
            boolean door = (au <= 8 && b >= -34 && b <= -33 || b >= -29 && au >= 9 && au <= 11) && (y >= DRY + 1 && y <= DRY + 3)
                || b >= -29 && au >= 9 && au <= 11 && y >= 76 && y <= 78;
            boolean slit = (au >= 16 && (b == -34 || b == -33) || b <= -37 && (au == 12 || au == 13))
                && (y >= 68 && y <= 73 || y >= 81 && y <= 86 || !broken && y >= 94 && y <= 100);
            if (door || slit) p.w(a, y, b, b(AIR, 0));
            else p.w(a, y, b, corner ? b(BRICK, 3) : y % 10 == 0 ? GLY : ELD);
        }
        if (broken) return;
        if ((a + b & 1) == 0 && (au == 7 || au == 17 || b == -38 || b == -28)) p.w(a, 113, b, MAS);
        if (corner) { for (int y = 113; y <= 117; y++) p.w(a, y, b, y == 117 ? b(WALL, 0) : b(BRICK, 3)); }
    }

    // ------------------------------------------------------------------------------------------------ the crossing tower

    /** The lantern tower over the crossing, its louvred lancets and corner pinnacles, the spire, the hanging lamp. */
    private static void crossingTower(P p) {
        if (!p.hit(-8, 6, 8, 20)) return;
        for (int a = Math.max(-8, p.u0); a <= Math.min(8, p.u1); a++)
            for (int b = Math.max(6, p.v0); b <= Math.min(20, p.v1); b++) {
                int au = Math.abs(a), db = Math.abs(b - 13);
                boolean ring = au == 8 || db == 7;
                if (ring) {
                    boolean lancet = au == 8 && db <= 1 || db == 7 && au <= 1;
                    for (int y = db == 7 ? 90 : 94; y <= 111; y++) {
                        if (lancet && y >= 98 && y <= 106) p.w(a, y, b, y == 98 ? b(SLAB, 5) : b(IRON_BARS, 0));
                        else p.w(a, y, b, y % 6 == 0 ? GLY : ELD);
                    }
                    if ((a + b & 1) == 0) p.w(a, 112, b, MAS);
                    if (au == 8 && db == 7) for (int y = 112; y <= 119; y++) p.w(a, y, b, y == 119 ? b(WALL, 0) : y == 118 ? OBSIDIAN : b(BRICK, 3));
                } else {
                    p.w(a, 110, b, au == 0 || db == 0 ? b(BRICK, 3) : MAS);
                    p.w(a, 111, b, MAS);
                }
                // the spire
                for (int h = 0; h <= 22; h++) {
                    double rho = 7.2 * (1 - h / 23.0);
                    double d = Math.max(Math.max(au, db), (au + db) / 1.41);
                    if (d <= rho && d > rho - 1.3) p.w(a, 112 + h, b, ROOF);
                }
            }
        if (p.in(0, 13)) {
            for (int y = 135; y <= 138; y++) p.w(0, y, 13, y == 135 ? b(OBSIDIAN, 0) : b(WALL, 0));
            for (int y = 99; y <= 109; y++) p.w(0, y, 13, IRON_BARS, 0);
            p.w(0, 98, 13, SEA_LANTERN, 0);
        }
        if (p.in(-1, 13)) p.w(-1, 137, 13, IRON_BARS, 0);
        if (p.in(1, 13)) p.w(1, 137, 13, IRON_BARS, 0);
    }

    // ------------------------------------------------------------------------------------------------ the buttresses

    /** Flying buttresses: a pier with a pinnacle outside every arcade pier, an arch leaping over the aisle to the clerestory. */
    private static void buttresses(P p) {
        int[] lines = {-22, -17, -12, -7, -2, 3, 25, 30, 35};
        for (int pv : lines)
            for (int s = -1; s <= 1; s += 2) {
                if (!p.hit(Math.min(8 * s, 19 * s), pv, Math.max(8 * s, 19 * s), pv + 1)) continue;
                for (int b = pv; b <= pv + 1; b++) {
                    for (int au = 18; au <= 19; au++) {
                        int a = au * s;
                        if (!p.in(a, b)) continue;
                        p.foot(a, b, DRY - 1);
                        int top = au == 19 ? 80 : 86;
                        for (int y = DRY; y <= top; y++) p.w(a, y, b, y == 75 ? GLY : ELD);
                        if (au == 18) for (int y = 87; y <= 90; y++) p.w(a, y, b, y == 90 ? b(WALL, 0) : b(BRICK, 3));
                        else p.w(a, 81, b, b(SLAB, 5));
                    }
                    for (int au = 8; au <= 17; au++) {
                        int a = au * s;
                        if (!p.in(a, b)) continue;
                        int y = 86 + (17 - au) * 2 / 3;
                        p.w(a, y, b, MAS);
                        p.w(a, y - 1, b, au == 13 ? b(BRICK, 3) : MAS);
                        if (au >= 14) p.w(a, y - 2, b, MAS);
                    }
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the bell tower

    /** The bell tower across its bridge from the north aisle: floors on a ladder, an open belfry with two bells, a cap. */
    private static void bellTower(P p) {
        int cu = -34, cv = -19;
        if (!p.hit(-38, -23, -14, -15)) return;
        for (int a = Math.max(-29, p.u0); a <= Math.min(-14, p.u1); a++)
            for (int b = Math.max(-21, p.v0); b <= Math.min(-17, p.v1); b++) {
                if (a == -27 || a == -22) p.foot(a, b, 61);
                p.w(a, 62, b, MAS);
                p.w(a, 63, b, Math.abs(b + 19) == 2 ? b(BRICK, 3) : PAV);
                if (Math.abs(b + 19) == 2 && p.f.keep(a, 64 - p.base, b, 0.8)) p.w(a, 64, b, b(WALL, 1));
            }
        for (int a = Math.max(cu - 4, p.u0); a <= Math.min(cu + 4, p.u1); a++)
            for (int b = Math.max(cv - 4, p.v0); b <= Math.min(cv + 4, p.v1); b++) {
                int da = a - cu, db = b - cv, m = Math.max(Math.abs(da), Math.abs(db));
                boolean corner = Math.abs(da) == 4 && Math.abs(db) == 4;
                p.foot(a, b, DRY - 1);
                if (m == 4) {
                    int along = Math.abs(da) == 4 ? db : da;
                    for (int y = DRY; y <= 107; y++) {
                        boolean belfry = y >= 100 && y <= (Math.abs(along) == 2 ? 104 : 105) && !corner && Math.abs(along) >= 1 && Math.abs(along) <= 2;
                        boolean door = da == 4 && Math.abs(db) <= 1 && y >= DRY + 1 && y <= DRY + 3;
                        boolean slit = !corner && along == 0 && (y == 70 || y == 71 || y == 82 || y == 83 || y == 94 || y == 95);
                        if (belfry || door || slit) p.w(a, y, b, b(AIR, 0));
                        else p.w(a, y, b, corner ? b(BRICK, 3) : y % 12 == 3 ? GLY : ELD);
                    }
                } else {
                    p.w(a, DRY, b, PAV);
                    for (int fl = 75; fl <= 99; fl += 12) p.w(a, fl, b, b(DOUBLE_SLAB, 5));
                    p.w(a, 107, b, MAS);
                }
                for (int h = 0; h <= 8; h++) if (m == 4 - h / 2) p.w(a, 108 + h, b, ROOF);
            }
        if (p.in(cu - 3, cv)) for (int y = DRY + 1; y <= 99; y++) p.w(cu - 3, y, cv, LADDER, p.f.facing(1, 0));
        if (p.in(cu, cv)) {
            p.w(cu, 104, cv, CAULDRON, 0); p.w(cu, 105, cv, FENCE, 0); p.w(cu, 106, cv, FENCE, 0);
            p.w(cu, 117, cv, OBSIDIAN, 0); p.w(cu, 118, cv, b(WALL, 0));
        }
        if (p.in(cu + 2, cv - 2)) { p.w(cu + 2, 105, cv - 2, CAULDRON, 0); p.w(cu + 2, 106, cv - 2, FENCE, 0); }
    }

    // ------------------------------------------------------------------------------------------------ the cloister

    /** The cloister: arcaded walks round a drowned garth with its saint, a covered passage from the aisle, the sacristy. */
    private static void cloister(P p) {
        if (!p.hit(14, -40, 45, -5)) return;
        for (int a = Math.max(14, p.u0); a <= Math.min(20, p.u1); a++)
            for (int b = Math.max(-16, p.v0); b <= Math.min(-12, p.v1); b++) {
                if (a == 17) p.foot(a, b, 61);
                p.w(a, 62, b, MAS);
                p.w(a, 63, b, PAV);
                if (b == -16 || b == -12) for (int y = 64; y <= 67; y++) p.w(a, y, b, MAS);
                p.w(a, 68, b, b(SLAB, 5));
            }
        for (int a = Math.max(21, p.u0); a <= Math.min(45, p.u1); a++)
            for (int b = Math.max(-31, p.v0); b <= Math.min(-5, p.v1); b++) {
                int e = Math.min(Math.min(a - 21, 45 - a), Math.min(b + 31, -5 - b));
                if (e == 0) {
                    p.foot(a, b, DRY);
                    int along = a == 21 || a == 45 ? b : a;
                    for (int y = DRY + 1; y <= 72; y++) {
                        boolean door = y <= DRY + 3 && (a == 21 && b >= -15 && b <= -13 || b == -31 && a >= 40 && a <= 42);
                        if (door) p.w(a, y, b, b(AIR, 0));
                        else if (Math.floorMod(along, 4) == 0 && y >= 67 && y <= 69) p.w(a, y, b, b(IRON_BARS, 0));
                        else p.w(a, y, b, y == 72 ? b(BRICK, 3) : MAS);
                    }
                    if (((a + b) & 1) == 0) p.w(a, 73, b, MAS);
                } else if (e <= 4) {
                    p.foot(a, b, DRY - 1);
                    p.w(a, DRY, b, (a + b & 1) == 0 ? b(BRICK, 0) : PAV);
                    p.w(a, e <= 2 ? 72 : 71, b, ROOF);
                } else if (e == 5) {
                    p.foot(a, b, DRY);
                    int along = a == 26 || a == 40 ? b : a;
                    boolean column = Math.floorMod(along, 3) == 0;
                    for (int y = DRY + 1; y <= 70; y++) {
                        if (column) p.w(a, y, b, y == DRY + 1 || y == 68 ? b(BRICK, 3) : MAS);
                        else if (y >= 68) p.w(a, y, b, y == 70 ? b(SLAB, 5) : MAS);
                    }
                } else {
                    p.foot(a, b, NF - 1);
                    p.w(a, NF, b, PAV);
                    for (int y = NF + 1; y <= DRY; y++) p.w(a, y, b, FLUID);
                    double r = Math.sqrt((a - 33) * (a - 33) + (b + 18) * (b + 18));
                    if (r <= 2.5) for (int y = NF + 1; y <= DRY; y++) p.w(a, y, b, r > 1.5 ? MAS : b(STONE, 5));
                }
            }
        if (p.in(33, -18)) {                                          // the drowned saint on the well-head
            for (int y = 64; y <= 69; y++) p.w(33, y, -18, STONE, 5);
            p.w(33, 70, -18, STONE, 6);
            p.w(33, 71, -18, SKULL, 1);
        }
        if (p.in(32, -18)) p.w(32, 68, -18, STONE, 5);
        if (p.in(34, -18)) p.w(34, 68, -18, STONE, 5);
        // the sacristy
        for (int a = Math.max(37, p.u0); a <= Math.min(45, p.u1); a++)
            for (int b = Math.max(-40, p.v0); b <= Math.min(-32, p.v1); b++) {
                int e = Math.min(Math.min(a - 37, 45 - a), Math.min(b + 40, -32 - b)), m = Math.max(Math.abs(a - 41), Math.abs(b + 36));
                if (e == 0) {
                    p.foot(a, b, DRY);
                    for (int y = DRY + 1; y <= 72; y++) {
                        if (y <= DRY + 3 && b == -32 && a >= 40 && a <= 42) p.w(a, y, b, b(AIR, 0));
                        else if (y >= 67 && y <= 69 && (a == 41 || b == -36) && b != -32) p.w(a, y, b, b(IRON_BARS, 0));
                        else p.w(a, y, b, y == 72 ? GLY : MAS);
                    }
                } else {
                    p.foot(a, b, DRY - 1);
                    p.w(a, DRY, b, (a + b & 1) == 0 ? b(PRISMARINE, 2) : b(BRICK, 0));
                    p.w(a, 72, b, MAS);
                }
                for (int h = 0; h <= 4; h++) if (m == 4 - h) p.w(a, 73 + h, b, ROOF);
            }
        if (p.in(41, -36)) p.w(41, 71, -36, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------ the causeway

    /** The causeway out to the portal: a paved deck on piers over the water, broken saints on its parapets, ladders down. */
    private static void causeway(P p) {
        if (!p.hit(-4, -48, 4, -39)) return;
        for (int a = Math.max(-4, p.u0); a <= Math.min(4, p.u1); a++)
            for (int b = Math.max(-48, p.v0); b <= Math.min(-39, p.v1); b++) {
                int au = Math.abs(a);
                if (au == 4) {
                    if (b == -46) { int t = p.ground(a, b); for (int y = Math.max(t + 1, 52); y <= 63; y++) p.w(a, y, b, LADDER, p.f.facing(a < 0 ? -1 : 1, 0)); }
                    continue;
                }
                if (b == -47 || b == -46 || b == -43 || b == -42 || b == -39) p.foot(a, b, 61);
                else p.w(a, 61, b, MAS);
                p.w(a, 62, b, MAS);
                p.w(a, 63, b, au == 3 ? b(PRISMARINE, 2) : au == 0 ? b(BRICK, 3) : PAV);
                if (au == 3 && b != -44 && p.f.keep(a, 64 - p.base, b, 0.8)) p.w(a, 64, b, b(WALL, 1));
                if (au == 3 && b == -44) {
                    p.w(a, 64, b, BRICK, 3);
                    for (int y = 65; y <= 67; y++) p.w(a, y, b, STONE, 5);
                    p.w(a, 66, b + 1, STONE, 5);
                    p.w(a, 66, b - 1, STONE, 5);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the graveyard

    /** Whether a column belongs to the church or its buildings (the graveyard keeps off them). */
    private static boolean church(int u, int v) {
        int au = Math.abs(u);
        if (au <= 6 && v <= -38) return true;
        if (au <= 21 && v >= -41 && v <= 39) return true;
        if (au <= 30 && v >= 5 && v <= 21) return true;
        if (u * u + (v - 37) * (v - 37) <= 110) return true;
        if (u >= 12 && u <= 47 && v >= -42 && v <= -3) return true;
        return u >= -40 && u <= -12 && v >= -25 && v <= -13;
    }

    /** The drowned graveyard: headstones, crosses, sarcophagi, broken obelisks and railed plots on the sea floor round it all. */
    private static void graveyard(P p) {
        for (int gu = -46; gu <= 46; gu += 4)
            for (int gv = -46; gv <= 46; gv += 4) {
                if (!p.hit(gu - 1, gv - 1, gu + 1, gv + 1) || church(gu, gv)) continue;
                long h = Hash.of(0x6EA7L ^ p.s.hash, gu, gv);
                int kind = (int) ((h >>> 7) % 9);
                for (int a = gu - 1; a <= gu + 1; a++)
                    for (int b = gv - 1; b <= gv + 1; b++) {
                        if (!p.in(a, b)) continue;
                        int t = p.ground(a, b), y0 = (t >= DRY ? DRY : t) + 1;
                        int da = a - gu, db = b - gv;
                        switch (kind) {
                            case 0: if (da == 0 && db == 0) p.w(a, y0, b, b(WALL, 1)); break;
                            case 1:
                                if (da == 0 && db == 0) for (int y = y0; y <= y0 + 2; y++) p.w(a, y, b, b(WALL, 0));
                                if (Math.abs(da) == 1 && db == 0) p.w(a, y0 + 1, b, b(WALL, 0));
                                break;
                            case 2:
                                if (da == 0 && db >= 0) p.w(a, y0, b, b(DOUBLE_SLAB, 5));
                                if (da == 0 && db == 0) p.w(a, y0 + 1, b, SKULL, 1);
                                break;
                            case 3: if (da == 0 && db == 0) for (int y = y0; y <= y0 + 1 + (int) (h & 3); y++) p.w(a, y, b, ELD); break;
                            case 4: if (da == 0 && db == 0) { p.w(a, y0, b, b(BRICK, 3)); if ((h & 16) != 0) p.w(a, y0 + 1, b, SEA_LANTERN, 0); } break;
                            case 5:
                                if (da == 0 && db == 0) p.w(a, y0, b, b(STONE, 6));
                                else if (p.f.keep(a, y0 - p.base, b, 0.7)) p.w(a, y0, b, b(IRON_BARS, 0));
                                break;
                            case 6: if (da == 0 && db == 0) { p.w(a, y0, b, b(SLAB, 5)); p.w(a, y0, b + 1, b(WALL, 1)); } break;
                            default: break;
                        }
                    }
            }
    }

    // ------------------------------------------------------------------------------------------------ the choir and the crypt

    /** The choir: dark oak stalls, the altar on its dais, the bishop's throne, two lamps in the vault. */
    private static void choir(P p, L l) {
        if (!p.hit(-7, 18, 7, 37)) return;
        for (int s = -1; s <= 1; s += 2)
            for (int b = 22; b <= 31; b++) {
                p.w(5 * s, CH + 1, b, 164, p.f.stairs(s, 0, false));
                for (int y = CH + 1; y <= CH + 3; y++) p.w(6 * s, y, b, PLANKS, 5);
                p.w(5 * s, CH + 4, b, 126, 5);
                p.w(6 * s, CH + 4, b, 126, 5);
            }
        p.box(-3, CH + 1, 32, 3, CH + 1, 35, b(BRICK, 3));
        p.box(-3, CH + 1, 32, 3, CH + 1, 32, b(BRICK_STAIRS, p.f.stairs(0, 1, false)));
        p.box(-1, CH + 2, 34, 1, CH + 2, 34, b(QUARTZ, 1));
        p.w(-1, CH + 3, 34, TORCH, 5);
        p.w(1, CH + 3, 34, TORCH, 5);
        p.w(0, CH + 1, 36, 164, p.f.stairs(0, 1, false));
        p.box(0, CH + 1, 37, 0, CH + 5, 37, b(OBSIDIAN, 0));
        p.w(-1, CH + 4, 37, SKULL, 1);
        p.w(1, CH + 4, 37, SKULL, 1);
        p.w(0, 95, 24, SEA_LANTERN, 0);
        p.w(0, 95, 31, SEA_LANTERN, 0);
        p.w(0, 95, -10, SEA_LANTERN, 0);
        p.w(0, 95, -22, SEA_LANTERN, 0);
    }

    /** The bishops' crypt under the choir, flooded to the sea's level: pillars, tombs; a shaft down from the crossing. */
    private static void crypt(P p, L l) {
        if (!p.hit(-7, 8, 7, 37)) return;
        int cf = l.cf;
        p.box(-1, cf + 1, 9, 1, NF, 11, FLUID);
        p.box(-1, cf + 1, 9, 1, cf + 3, 21, FLUID);
        for (int s = -1; s <= 1; s += 2) {
            for (int b = 24; b <= 32; b += 4) { p.w(3 * s, cf + 1, b, BRICK, 3); p.box(3 * s, cf + 2, b, 3 * s, DRY, b, MAS); }
            for (int b = 23; b <= 31; b += 4) { p.box(5 * s, cf + 1, b, 5 * s, cf + 1, b + 1, b(DOUBLE_SLAB, 5)); p.w(5 * s, cf + 2, b, SKULL, 1); }
        }
        p.w(0, CH, 28, SEA_LANTERN, 0);
    }

    // ------------------------------------------------------------------------------------------------ tiles

    private static void tiles(P p, Plans.GreatSite s, L l) {
        long h = s.hash;
        int k = l.broken, cf = l.cf;
        p.tile(2, 0, 77, -39, 0, -1, "THE DROWNED\nCATHEDRAL\nTHE BELLS\nSTILL TOLL", null);
        p.tile(2, 0, CH + 3, 36, 0, -1, "THE BISHOP\nSTILL SAYS\nMASS FOR\nTHE DROWNED", null);
        p.tile(2, 0, cf + 3, 36, 0, -1, "HERE LIE THE\nBISHOPS OF\nTHE DEEP", null);
        p.tile(2, -29, 66, -22, 1, 0, "THE BELLS\nTOLL UNDER\nTHE WATER", null);
        p.tile(2, 44, 66, -36, -1, 0, "WASH THY\nHANDS IN\nTHE SEA", null);
        p.tile(0, 4, CH + 1, 34, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.6");
        p.tile(0, -4, CH + 1, 34, 0, -1, "minecraft:chests/woodland_mansion", "trinket:0.4");
        p.tile(0, 5, cf + 1, 35, -1, 0, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h), 0, 99));
        p.tile(0, -5, cf + 1, 35, 1, 0, Sites.DUNGEON, "trinket:0.2");
        p.tile(0, 2, cf + 1, 22, 0, 1, "minecraft:chests/stronghold_crossing", null);
        p.tile(1, -2, cf + 1, 22, 0, 0, "ZOMBIE", null);
        p.tile(0, -32, 100, -21, 0, 1, Sites.CORRIDOR, null);
        p.tile(0, -12 * k, 103, -35, 0, 1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 5), 0, 99));
        p.tile(0, 12 * k, 90, -31, 0, -1, Sites.SMITH, "trinket:0.15");
        p.tile(0, 11, 76, -20, -1, 0, "minecraft:chests/abandoned_mineshaft", null);
        p.tile(0, -26, 64, 9, 1, 0, Sites.DUNGEON, null);
        p.tile(0, 26, 64, 17, -1, 0, "minecraft:chests/igloo_chest", null);
        p.tile(0, 41, 64, -39, 0, 1, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 9), 0, 99));
        p.tile(1, 38, 64, -39, 0, 0, "WITCH", null);
        p.tile(0, -5, 64, -35, 0, 1, Sites.DUNGEON, null);
        p.tile(0, 2, 64, -47, 0, 1, "minecraft:chests/nether_bridge", null);
        p.tile(1, -11, 64, -24, 0, 0, "SKELETON", null);
        if (p.in(0, 19)) { p.w(0, CH + 1, 19, PLATE, 0); p.f.dispenser(0, CH - p.base, 19, 0, 0); }
    }
}
