package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Tower of Silent Stars (epoch 6): the astronomers' observatory of Drownhollow, a slender round spire of cyclopean
 * stone 88 blocks high (a crown and a broken lens ring above that) in a round curtain wall.
 * <ul>
 * <li>The Gate of Eyes: two square towers astride the wall, a bridge over a vaulted passage with a raised portcullis, a ladder
 * in each tower to the wall-walk; four bastion drums and two flights of steps complete the wall.</li>
 * <li>The Court of Obelisks: an avenue lit by four lamps from the gate to the tower's porch, rings and the zodiac's spokes inlaid
 * in the pavement, five obelisks bound to the tower's balcony by sagging chains of iron bars, the Fallen Star (a crater with a
 * chest) and the Astronomers' Hall.</li>
 * <li>The Spire: a stepped plinth, a porch and the great door; eleven floors, each cut round the stair core into four wedge
 * chambers joined by low doors: guardroom, two floors of cages (spawners and loot), the dormitory, the library and the
 * scriptorium, the instrument rooms (armillary spheres), the star-chart room, the antechamber with its altar, the lens hall.
 * The core is a 5x5 well round a pillar, its pinwheel stair eight steps a turn; the shaft steps in every three floors, where
 * balconies open from the floors; the Plough is picked out in pale light on its front.</li>
 * <li>The Observatory Crown: the shaft flares out on corbels to a star-map floor behind parapets and pinnacles; the stair comes
 * up through a railed hole; two pylons hold a broken lens ring over the Star Priest's point.</li>
 * <li>The Undercroft: the round hall under the tower with its mosaic of the heavens, and four corridors to the orrery, the
 * archive, the dormitory and the cistern.</li>
 * </ul>
 * The structure is drawn in the quarter turn that puts its gate on the easiest ground (see {@link #pickRot}); the garrisons
 * are handed to the runtime in the site's own frame. Everything is a pure function of the site: no canvas reads.
 */
final class GreatStarTower extends GreatDesign {
    private static final int R = 36;
    private static final int MAS = -1, ELD = -2, GLY = -3, RUB = -4, PAV = -5;
    /** The undercroft's floor, the crown's floor (local y); the stair's pitch (one turn per floor). */
    private static final int UND = -8, TOP = 88, PITCH = 8;
    private static final double TAU = Math.PI * 2;
    /** The tower's squared outer and inner radius at each height 0 .. TOP-1. */
    private static final double[] O2 = new double[TOP], I2 = new double[TOP];
    static {
        for (int y = 0; y < TOP; y++) { double o = outerR(y), i = innerR(y); O2[y] = o * o; I2[y] = i * i; }
    }

    private static int b(int id, int meta) { return id << 4 | meta; }

    /** The shaft's outer radius at height y: stepping in a block every three floors, then flaring out on corbels under the crown. */
    private static double outerR(int y) {
        if (y < 80) return 12.5 - y / 24;
        double t = (y - 79) / 9.0;
        return 9.5 + 5.0 * Math.pow(t, 1.6);
    }

    private static double innerR(int y) { return y < 80 ? outerR(y) - 2.2 : 7.3; }

    private static final class L extends Layout {
        final List<int[]> lens = new ArrayList<>();        // {u, y, v, kind}: 0 ring, 1 rim bars, 2 glass
        final List<int[]> chains = new ArrayList<>();      // {u, y, v}
        final List<int[]> stars = new ArrayList<>();       // {u, v, kind}: 0 line, 1 bright star
        final int[][] obelisks = new int[5][];             // {u, v, height}
        int rot;                                           // the quarter turn the structure is drawn in (the gate faces the easiest ground)
        int gateD;                                         // how far the land outside the gate lies above (+) or below (-) the court, at most 2
    }

    /**
     * Which side the gate faces: of the four, the one whose land just outside the footprint lies nearest the court's level
     * (and dry), so the avenue never ends against a hillside or a drop. The structure is drawn in that turn; the garrisons
     * are handed to the runtime in the site's own frame.
     */
    private static int pickRot(Plans.GreatSite s) {
        int[] dx = {0, 1, 0, -1}, dz = {-1, 0, 1, 0};
        int best = s.rot & 3;
        double bestCost = 1e9;
        for (int d = 0; d < 4; d++) {
            double sum = 0;
            for (int k = -1; k <= 1; k++) sum += s.surface(s.x + dx[d] * 38 - dz[d] * 6 * k, s.z + dz[d] * 38 + dx[d] * 6 * k);
            double h = sum / 3, cost = Math.abs(h - s.base) + (h <= Plans.SEA ? 30 : 0) - (d == (s.rot & 3) ? 0.5 : 0);
            if (cost < bestCost) { bestCost = cost; best = d; }
        }
        return best;
    }

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        // the obelisks round the courtyard and their chains up to the tower's waist
        for (int k = 0; k < 5; k++) {
            double th = Math.toRadians(-54 + 72 * k);
            int ou = (int) Math.round(22 * Math.cos(th)), ov = (int) Math.round(22 * Math.sin(th)), h = 24 + r.nextInt(6);
            l.obelisks[k] = new int[] {ou, ov, h};
            double ax = ou, ay = h - 2, az = ov, bx = 12.8 * Math.cos(th), by = 50 + r.nextInt(3), bz = 12.8 * Math.sin(th);
            double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)), sag = 3 + r.nextDouble() * 2;
            int n = (int) Math.ceil(len * 2.5);
            int[] last = null;
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                int[] q = {(int) Math.round(ax + (bx - ax) * t), (int) Math.round(ay + (by - ay) * t - sag * 4 * t * (1 - t)), (int) Math.round(az + (bz - az) * t)};
                if (last != null && last[0] == q[0] && last[1] == q[1] && last[2] == q[2]) continue;
                l.chains.add(q);
                last = q;
            }
        }
        // the broken lens ring over the crown, leaning toward the back, a quarter of it fallen, most of its glass gone
        double cy = TOP + 9, rad = 8.0, al = Math.toRadians(25);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < 360; i++) {
            double t = Math.toRadians(i);
            if (i >= 224 && i <= 316) continue;
            for (double rr = rad - 0.6; rr <= rad + 0.45; rr += 0.5) addLens(l, seen, rr * Math.cos(t), cy + rr * Math.sin(t) * Math.cos(al), rr * Math.sin(t) * Math.sin(al), 0);
            addLens(l, seen, (rad - 1.3) * Math.cos(t), cy + (rad - 1.3) * Math.sin(t) * Math.cos(al), (rad - 1.3) * Math.sin(t) * Math.sin(al), 1);
        }
        for (double x = -7; x <= 7; x += 0.5)
            for (double z = -7; z <= 7; z += 0.5) {
                double rr = Math.sqrt(x * x + z * z), t = Math.toDegrees(Math.atan2(z, x));
                if (rr > 6.4 || rr < 4.2 || t > -136 && t < -44 || Hash.unit(Hash.of(s.hash, (long) (x * 2), (long) (z * 2))) < 0.5) continue;
                addLens(l, seen, x, cy + z * Math.cos(al), z * Math.sin(al), 2);
            }
        // the star map's constellations: bright stars joined by lines of pale stone
        for (int c = 0; c < 7; c++) {
            double th = r.nextDouble() * TAU, rr = 3 + r.nextDouble() * 8;
            double u = rr * Math.cos(th), v = rr * Math.sin(th);
            int stars = 3 + r.nextInt(3);
            for (int k = 0; k < stars; k++) {
                double nu = u + r.nextDouble() * 6 - 3, nv = v + r.nextDouble() * 6 - 3;
                if (nu * nu + nv * nv > 144) { nu *= 0.8; nv *= 0.8; }
                int steps = (int) Math.ceil(Math.max(Math.abs(nu - u), Math.abs(nv - v)) * 2);
                for (int i = 1; i < steps; i++) l.stars.add(new int[] {(int) Math.round(u + (nu - u) * i / steps), (int) Math.round(v + (nv - v) * i / steps), 0});
                l.stars.add(new int[] {(int) Math.round(u), (int) Math.round(v), 1});
                u = nu; v = nv;
            }
            l.stars.add(new int[] {(int) Math.round(u), (int) Math.round(v), 1});
        }

        l.rot = pickRot(s);
        int er = (l.rot - s.rot) & 3;
        Frame fr = new Frame(null, s.x, s.z, s.base, l.rot);
        l.gateD = Math.max(-2, Math.min(2, s.surface(fr.wx(0, -37), fr.wz(0, -37)) - s.base));
        l.boss = new int[] {0, TOP + 1, 0};
        List<Garrison> g = new ArrayList<>();
        g.add(g(0, 1, -28, "zombie+husk"));                      // the avenue inside the gate
        g.add(g(-20, 1, -9, "skeleton+stray"));                  // the court, west
        g.add(g(20, 1, -4, "spider+cave_spider"));               // the court, east
        g.add(g(0, 1, 27, "cult_zealot+cult_adept"));            // behind the tower, by the back obelisk
        g.add(g(-14, 1, 17, "illusioner+witch"));                // the astronomers' hall
        g.add(g(0, 1, -7, "ghoul+zombie_villager"));             // the tower's ground floor
        g.add(g(5, 9, 1, "tomb_crawler+silverfish"));            // the floors, one pack to each
        g.add(g(-5, 17, -1, "mi_go+endermite"));
        g.add(g(1, 25, 5, "deep_one+hound"));
        g.add(g(-1, 33, -5, "enderman+vindicator"));
        g.add(g(5, 41, -1, "shoggoth+slime"));
        g.add(g(-5, 49, 1, "star_spawn+wither_skeleton"));
        g.add(g(4, 57, 2, "!cult_adept+evoker"));
        g.add(g(-1, 65, -5, "nightgaunt+creeper"));
        g.add(g(9, TOP + 1, 7, "nightgaunt+star_spawn"));                   // the crown
        g.add(g(6, UND + 1, 0, "creeper+cave_spider"));          // the undercroft: the round hall
        g.add(g(0, UND + 1, 15, "ghoul+tomb_crawler"));          // the cistern
        g.add(g(-27, UND + 1, 0, "star_spawn+enderman"));        // the orrery
        g.add(g(20, UND + 1, 2, "wither_skeleton+skeleton"));    // the archive
        g.add(g(0, UND + 1, -20, "zombie+husk"));                // the dormitory
        for (Garrison x : g) {
            int u = x.u, v = x.v;
            for (int i = 0; i < er; i++) { int t = u; u = -v; v = t; }
            l.garrisons.add(g(u, x.y, v, x.pack));
        }
        return l;
    }

    private static void addLens(L l, java.util.Set<Long> seen, double x, double y, double z, int kind) {
        int u = (int) Math.round(x), yy = (int) Math.round(y), v = (int) Math.round(z);
        long key = ((long) (u + 512) << 40) | ((long) (yy + 512) << 20) | (v + 512);
        if (!seen.add(key)) return;
        l.lens.add(new int[] {u, yy, v, kind});
    }

    // ------------------------------------------------------------------------------------------------ the drawing tools

    /** The chunk's share of the footprint in local coordinates, and drawing tools clipped to it. */
    private static final class P {
        final Plans.GreatSite s; final Frame f; final int u0, u1, v0, v1;
        private final int[] ground = new int[256];
        private final boolean[] known = new boolean[256];

        P(Plans.GreatSite s, Canvas c, int rot) {
            this.s = s;
            f = new Frame(c, s.x, s.z, s.base, rot);
            int xa = c.x0 - s.x, xb = xa + 15, za = c.z0 - s.z, zb = za + 15;
            switch (rot & 3) {
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
                default: f.set(a, y, b, m >> 4, m & 15);
            }
        }

        void box(int a0, int y0, int b0, int a1, int y1, int b1, int m) {
            int aa = Math.max(Math.min(a0, a1), u0), ab = Math.min(Math.max(a0, a1), u1), ba = Math.max(Math.min(b0, b1), v0), bb = Math.min(Math.max(b0, b1), v1);
            for (int a = aa; a <= ab; a++) for (int b = ba; b <= bb; b++) for (int y = y0; y <= y1; y++) put(a, y, b, m);
        }

        void set(int a, int y, int b, int id, int meta) { if (in(a, b)) f.set(a, y, b, id, meta); }
        void set(int a, int y, int b, int id) { if (in(a, b)) f.set(a, y, b, id, 0); }

        /** A floor that hangs over a room: worn brick and andesite, nothing that falls (the court's paving has gravel in it). */
        void deck(int a, int y, int b) {
            double r = f.roll(a, y, b, 603);
            if (r < 0.28) f.set(a, y, b, BRICK, 2);
            else if (r < 0.52) f.set(a, y, b, BRICK, 1);
            else if (r < 0.66) f.set(a, y, b, STONE, 5);
            else if (r < 0.78) f.set(a, y, b, PRISMARINE, 2);
            else if (r < 0.88) f.set(a, y, b, COBBLE, 0);
            else f.set(a, y, b, BRICK, 0);
        }

        /** A carved band: chiseled stone and obsidian, no lamps. */
        void band(int a, int y, int b) {
            if (f.roll(a, y, b, 601) < 0.55) f.set(a, y, b, BRICK, 3); else f.set(a, y, b, OBSIDIAN);
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override void draw(Plans.GreatSite s, Layout layout, Canvas c) {
        L l = (L) layout;
        P p = new P(s, c, l.rot);
        if (!p.hit(-R, -R, R, R)) return;
        land(p);
        wall(p);
        gate(p, l);
        for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) turret(p, 22 * i, 22 * j);
        wallStairs(p);
        court(p);
        chains(p, l);
        crater(p);
        for (int[] o : l.obelisks) obelisk(p, o[0], o[1], o[2]);
        hall(p);
        undercroft(p);
        tower(p);
        balconies(p);
        plinth(p);
        porch(p);
        constellation(p);
        storeys(p);
        crown(p, l);
        furnish(p, s);
    }

    /** Levels the court: rubble up out of hollows, the land (and the field's debris) cut away, the wall's apron included. */
    private static void land(P p) {
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                double r2 = a * a + b * b;
                if (r2 > 35.6 * 35.6 && !(Math.abs(a) <= 10 && b <= -30)) continue;      // beyond the wall the land stays as it is
                int t = p.ground(a, b);
                for (int y = t + 1; y < 0; y++) p.f.rubble(a, y, b);
                p.f.rubble(a, -1, b);                                                       // plugs any pocket the field left under the paving
                p.f.clear(a, b, 1, Math.max(t, 0) + 3);
                if (underCode(a, b) >= 2) p.deck(a, 0, b); else p.f.paving(a, 0, b);       // over the undercroft's rooms nothing may fall
            }
    }

    private static final double WALL_IN = 31.4, WALL_MID = 33.5, WALL_OUT = 35.0;

    /** The curtain wall: a round wall with a wall-walk on its inner half (floor y 9) and a parapet and merlons on its outer half. */
    private static void wall(P p) {
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                double r2 = a * a + b * b;
                if (r2 < WALL_IN * WALL_IN || r2 >= WALL_OUT * WALL_OUT) continue;
                boolean outer = r2 >= WALL_MID * WALL_MID;
                int top = outer ? 11 : 9;
                for (int y = 1; y <= top; y++) {
                    if (y >= 10 && !p.f.keep(a, y, b, 0.9)) continue;                      // the parapet has lost a block here and there
                    if (y == 9 && !outer) p.deck(a, y, b);
                    else if (outer && (y == 5 || y == 10 && ((a + b) & 3) == 0)) p.band(a, y, b);
                    else p.f.eldritch(a, y, b);
                }
                if (outer && ((a + b) & 1) == 0 && p.f.keep(a, 12, b, 0.8)) p.f.masonry(a, 12, b);
            }
    }

    /**
     * The Gate of Eyes: two square towers on the front of the wall, a bridge between them over a vaulted passage (a raised
     * portcullis hanging in its arch), eyes of obsidian and pale light over the arch. The towers have a ladder to the
     * wall-walk and doors onto it; their tops are open battlements.
     */
    private static void gate(P p, L l) {
        if (!p.hit(-10, -37, 10, -29)) return;
        p.box(-9, 1, -36, 9, 9, -30, ELD);
        for (int a = Math.max(-9, p.u0); a <= Math.min(9, p.u1); a++) { if (p.in(a, -36)) p.band(a, 5, -36); if (p.in(a, -36) && Math.abs(a) > 2) p.band(a, 9, -36); }
        for (int s = -1; s <= 1; s += 2) {
            p.box(s * 4, 1, -35, s * 8, 8, -31, AIR);                      // the ground room
            p.set(s * 4, 9, -34, AIR);                                      // the ladder's hole in the deck
            for (int y = 1; y <= 9; y++) p.set(s * 4, y, -34, LADDER, p.f.facing(s, 0));
            p.box(s * 3, 10, -36, s * 9, 14, -30, AIR);
            for (int a = 3; a <= 9; a++)
                for (int b = -36; b <= -30; b++) {
                    if (a != 3 && a != 9 && b != -36 && b != -30) continue;
                    int u = s * a;
                    if (!p.in(u, b)) continue;
                    for (int y = 10; y <= 13; y++) p.f.eldritch(u, y, b);
                    if (((a + b) & 1) == 0) p.f.masonry(u, 14, b);
                }
            p.box(s * 3, 10, -33, s * 3, 12, -33, AIR);                    // onto the bridge
            p.box(s * 9, 10, -31, s * 9, 12, -31, AIR);                    // onto the wall-walk
            p.box(s * 6, 1, -30, s * 6, 3, -30, AIR);                      // the door from the court
            p.box(s * 6, 5, -36, s * 6, 6, -36, AIR);                      // arrow slits
            p.box(s * 9, 5, -34, s * 9, 6, -34, AIR);
            p.set(s * 8, 1, -31, TORCH, 5);
        }
        // the passage and its arch; the bridge over it
        for (int a = -2; a <= 2; a++) {
            int top = 6 - Math.abs(a);
            p.box(a, 1, -36, a, top, -30, AIR);
            p.box(a, 10, -36, a, 14, -30, AIR);
            p.set(a, top, -34, IRON_BARS);
            p.set(a, top - 1, -34, IRON_BARS);
            if (p.in(a, -36)) { p.f.eldritch(a, 10, -36); p.f.eldritch(a, 11, -36); if ((a & 1) == 0) p.f.masonry(a, 12, -36); }
            p.set(a, 10, -30, WALL, 1);
        }
        // the passage floor meets the land outside: a short flight of steps up (land below) or down (land above) toward the court
        int d = l.gateD;
        for (int a = -2; a <= 2; a++)
            for (int b = -36; b <= -34; b++) {
                int fl = d > 0 ? Math.max(0, d - (b + 36)) : Math.min(0, d + (b + 36));
                if (fl == 0) continue;
                if (fl > 0) {
                    for (int y = 1; y < fl; y++) p.f.masonry(a, y, b);
                    p.set(a, fl, b, BRICK_STAIRS, p.f.stairs(0, -1, false));
                } else {
                    for (int y = fl + 1; y <= 0; y++) p.set(a, y, b, AIR);
                    p.set(a, fl, b, BRICK_STAIRS, p.f.stairs(0, 1, false));
                }
            }
        // the eyes over the arch: obsidian sockets, pale irises
        for (int s = -1; s <= 1; s += 2) {
            p.set(s * 2, 8, -36, SEA_LANTERN);
            p.set(s * 2, 7, -36, OBSIDIAN);
            p.set(s * 2, 9, -36, OBSIDIAN);
            p.set(s * 1, 8, -36, OBSIDIAN);
            p.set(s * 3, 8, -36, OBSIDIAN);
        }
        p.set(0, 8, -36, BRICK, 3);
        p.set(0, 9, -36, OBSIDIAN);
    }

    /** A bastion drum on the wall: solid to the wall-walk, a battlement above with the walk passing through it, a pale lamp in its floor. */
    private static void turret(P p, int cu, int cv) {
        if (!p.hit(cu - 4, cv - 4, cu + 4, cv + 4)) return;
        for (int a = Math.max(cu - 4, p.u0); a <= Math.min(cu + 4, p.u1); a++)
            for (int b = Math.max(cv - 4, p.v0); b <= Math.min(cv + 4, p.v1); b++) {
                double d2 = (a - cu) * (a - cu) + (b - cv) * (b - cv);
                if (d2 > 3.6 * 3.6) continue;
                boolean shell = d2 > 2.3 * 2.3;
                for (int y = 1; y <= 8; y++) p.f.eldritch(a, y, b);
                if (shell) p.f.eldritch(a, 9, b); else p.f.paving(a, 9, b);
                for (int y = 10; y <= 14; y++) { if (shell) p.f.eldritch(a, y, b); else p.f.set(a, y, b, AIR); }
                p.f.set(a, 15, b, AIR);
                if (shell && ((a + b) & 1) == 0) p.f.masonry(a, 15, b);
                double r2 = a * a + b * b;
                if (r2 >= WALL_IN * WALL_IN && r2 < WALL_MID * WALL_MID) for (int y = 10; y <= 12; y++) p.f.set(a, y, b, AIR);
                if (d2 < 1.0) p.f.set(a, 9, b, SEA_LANTERN);
            }
    }

    /** Two flights up the wall's inner face, east and west, from the court to the wall-walk. */
    private static void wallStairs(P p) {
        for (int s = -1; s <= 1; s += 2) {
            if (!p.hit(s > 0 ? 22 : -32, -3, s > 0 ? 32 : -22, 3)) continue;
            for (int k = 23; k <= 31; k++) {
                int u = s * k, y = k - 22;
                for (int v = -2; v <= 2; v++) {
                    if (!p.in(u, v)) continue;
                    for (int yy = 1; yy < y; yy++) p.f.masonry(u, yy, v);
                    if (Math.abs(v) == 2) { p.f.masonry(u, y, v); p.f.set(u, y + 1, v, WALL, 1); }
                    else p.f.set(u, y, v, BRICK_STAIRS, p.f.stairs(s, 0, false));
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the court

    /** The court's pavement: the avenue from the gate to the tower's door, inlaid rings and the twelve spokes of the zodiac. */
    private static void court(P p) {
        int a0 = Math.max(-R, p.u0), a1 = Math.min(R, p.u1), b0 = Math.max(-R, p.v0), b1 = Math.min(R, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                if (Math.abs(a) <= 2 && b <= -12 && b >= -36) {
                    if (Math.abs(a) == 2) p.f.set(a, 0, b, PRISMARINE, 2);
                    else if (a == 0 && (b & 1) == 0) p.f.set(a, 0, b, BRICK, 3);
                    else p.f.set(a, 0, b, BRICK, p.f.roll(a, 0, b, 602) < 0.55 ? 0 : 2);
                    continue;
                }
                double r = Math.sqrt(a * a + b * b);
                if (r >= WALL_IN || r < 12.6) continue;
                if (r >= 16.5 && r < 17.5 || r >= 27.5 && r < 28.5) p.f.set(a, 0, b, PRISMARINE, 2);
                else if (r >= 13.2 && r < 14.2) p.f.set(a, 0, b, PRISMARINE, 1);
                else if (r >= 17.5 && r < 27.5) {
                    double th = Math.toDegrees(Math.atan2(b, a)), d = Math.abs(((th % 30) + 30) % 30);
                    d = Math.min(d, 30 - d);
                    if (d * Math.PI / 180 * r < 0.62) p.f.set(a, 0, b, OBSIDIAN, 0);
                }
            }
        // four lamp posts along the avenue
        for (int s = -1; s <= 1; s += 2)
            for (int v = -26; v <= -18; v += 8) {
                if (!p.in(3 * s, v)) continue;
                p.f.set(3 * s, 1, v, BRICK, 3);
                p.f.eldritch(3 * s, 2, v);
                p.f.eldritch(3 * s, 3, v);
                p.f.set(3 * s, 4, v, SEA_LANTERN);
                p.f.set(3 * s, 5, v, OBSIDIAN);
            }
    }

    /** The Fallen Star: a crater in the court's front-left, a lump of obsidian and magma at its heart, glassed ground, a chest. */
    private static void crater(P p) {
        int cu = -25, cv = -15;
        if (!p.hit(cu - 6, cv - 6, cu + 6, cv + 6)) return;
        for (int a = Math.max(cu - 6, p.u0); a <= Math.min(cu + 6, p.u1); a++)
            for (int b = Math.max(cv - 6, p.v0); b <= Math.min(cv + 6, p.v1); b++) {
                double d = Math.sqrt((a - cu) * (a - cu) + (b - cv) * (b - cv));
                if (d > 5.4) continue;
                if (d > 4.4) { if (p.f.roll(a, 1, b, 670) < 0.5) p.f.rubble(a, 1, b); continue; }
                int depth = (int) (2.6 * (1 - d * d / 19.4) + 0.5);
                for (int y = 1 - depth; y <= 0; y++) p.f.set(a, y, b, AIR);
                double q = p.f.roll(a, -depth, b, 671);
                if (q < 0.45) p.f.set(a, -depth, b, OBSIDIAN); else if (q < 0.7) p.f.set(a, -depth, b, CLAY, 1); else p.f.set(a, -depth, b, CLAY, 15);
                if (d < 1.2) { p.f.set(a, 1 - depth, b, OBSIDIAN); if (d < 0.5) { p.f.set(a, 2 - depth, b, OBSIDIAN); p.f.set(a, 3 - depth, b, MAGMA); } }
            }
        p.f.chest(cu + 3, 1 - (int) (2.6 * (1 - 9 / 19.4) + 0.5), cv, -1, 0, "minecraft:chests/nether_bridge", "lore:" + Hash.range(Hash.mix(p.s.hash ^ 51), 0, 99) + ";trinket:0.3");
        p.f.masonry(cu, 1, cv + 6);
        p.f.masonry(cu, 2, cv + 6);
        p.f.sign(cu, 1, cv + 5, 0, -1, "THE STAR FELL" + "\n" + "SILENT. IT IS" + "\n" + "STILL WARM.");
    }

    /** The chains: sagging lines of iron bars from the obelisks' tops to the railing of the balcony at the tower's waist. */
    private static void chains(P p, L l) {
        for (int[] c : l.chains) if (p.in(c[0], c[2])) p.f.set(c[0], c[1], c[2], IRON_BARS, 0);
    }

    /** An obelisk on a stepped plinth: a 3x3 shaft with glyph bands narrowing to a spike, a pale star under its tip. */
    private static void obelisk(P p, int cu, int cv, int h) {
        if (!p.hit(cu - 3, cv - 3, cu + 3, cv + 3)) return;
        p.box(cu - 2, 1, cv - 2, cu + 2, 1, cv + 2, b(BRICK, 3));
        p.box(cu - 1, 2, cv - 1, cu + 1, 2, cv + 1, b(DOUBLE_SLAB, 5));
        for (int y = 3; y <= h - 9; y++)
            for (int a = cu - 1; a <= cu + 1; a++)
                for (int bb = cv - 1; bb <= cv + 1; bb++) { if (!p.in(a, bb)) continue; if (y % 5 == 0) p.band(a, y, bb); else p.f.eldritch(a, y, bb); }
        for (int y = h - 8; y <= h - 6; y++) {
            p.put(cu, y, cv, ELD);
            p.put(cu + 1, y, cv, ELD); p.put(cu - 1, y, cv, ELD); p.put(cu, y, cv + 1, ELD); p.put(cu, y, cv - 1, ELD);
        }
        for (int y = h - 5; y <= h - 3; y++) p.set(cu, y, cv, PRISMARINE, 2);
        p.set(cu, h - 2, cv, SEA_LANTERN);
        p.set(cu, h - 1, cv, OBSIDIAN);
        p.set(cu, h, cv, OBSIDIAN);
    }

    // ------------------------------------------------------------------------------------------------ the tower

    /** The spiral stair: its 24 cells in the 5x5 well, as stages 0..11 (landing, step, step; four runs round the pillar). */
    private static final int[][] STAGE = new int[5][5];
    private static final int[] TREAD_J = {0, 1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 8};
    private static final int[] RUN_DU = {0, -1, 0, 1}, RUN_DV = {1, 0, -1, 0};
    private static final double[] B2 = new double[TOP];
    static {
        for (int[] row : STAGE) java.util.Arrays.fill(row, -1);
        for (int i = 0; i < 3; i++)
            for (int w = 0; w < 2; w++) {
                STAGE[1 + w + 2][-2 + i + 2] = i;
                STAGE[2 - i + 2][1 + w + 2] = 3 + i;
                STAGE[-2 + w + 2][2 - i + 2] = 6 + i;
                STAGE[-2 + i + 2][-2 + w + 2] = 9 + i;
            }
        for (int y = 0; y < TOP; y++) { double o = Math.sqrt(O2[y]) - 1.5; B2[y] = o * o; }
    }

    private static void tower(P p) {
        if (!p.hit(-15, -15, 15, 15)) return;
        int a0 = Math.max(-15, p.u0), a1 = Math.min(15, p.u1), b0 = Math.max(-15, p.v0), b1 = Math.min(15, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int r2 = a * a + b * b;
                if (r2 > 210) continue;
                int m = Math.max(Math.abs(a), Math.abs(b));
                boolean core = m <= 3;
                for (int y = core ? UND : 0; y < TOP; y++) {
                    if (y >= 0 && r2 > O2[y]) continue;
                    int j = Math.floorMod(y, PITCH);
                    if (y >= 0 && r2 > I2[y]) shellCell(p, a, b, y, j, r2);
                    else if (core) coreCell(p, a, b, y, j, m);
                    else if (j == 0) p.deck(a, y, b);
                    else if (m >= 4 && Math.abs(a) == Math.abs(b) && !(m == 5 && j <= 3)) { if (m == 5 && j == 4) p.band(a, y, b); else p.f.masonry(a, y, b); }
                }
            }
    }

    /** A cell of the shaft's wall: the great door, the window slits on the four axes, a carved band at every floor. */
    private static void shellCell(P p, int a, int b, int y, int j, int r2) {
        if (b < 0 && y >= 1 && y <= 5 && Math.abs(a) <= 2) {
            int w = Math.abs(a);
            if (!(w <= 1 && (y <= 4 || w == 0))) p.f.set(a, y, b, BRICK, 3);
            return;
        }
        if (a == 0 || b == 0) {
            int w = b < 0 ? FRONT : b > 0 ? BACK : a > 0 ? RIGHT : LEFT;
            boolean door = (BAL[y / PITCH] >> w & 1) != 0;
            if (door ? j >= 1 && j <= 4 : j >= 3 && j <= 5) return;
        }
        if (r2 > B2[y] && j != 0 && y > 1 && outermost(a, b, y) && !p.f.keep(a, y, b, 0.985)) return;      // weathering: the odd outer block is gone
        if (r2 > B2[y] && (j == 0 || a == 0 || b == 0 || Math.abs(a) == Math.abs(b))) { p.band(a, y, b); return; }
        p.f.eldritch(a, y, b);
    }

    /** Whether a shaft cell lies on the outside (the cell beyond it, along its stronger axis, is outside the shaft). */
    private static boolean outermost(int a, int b, int y) {
        int na = a + (Math.abs(a) >= Math.abs(b) ? Integer.signum(a) : 0), nb = b + (Math.abs(a) < Math.abs(b) ? Integer.signum(b) : 0);
        return na * na + nb * nb > O2[y];
    }

    /** A cell of the core: its wall (a low door at every floor on the front), the pillar, the stair. */
    private static void coreCell(P p, int a, int b, int y, int j, int m) {
        int k = Math.floorDiv(y, PITCH);
        if (y == UND) { p.f.paving(a, y, b); return; }
        if (m == 3) {
            if (b == -3 && (a == 1 || a == 2) && j >= 1 && j <= 3) return;
            else if (j == 0) p.band(a, y, b);
            else p.f.masonry(a, y, b);
            return;
        }
        if (m == 0) {
            if (j == 4 && (k & 1) == 0) p.f.set(a, y, b, SEA_LANTERN);
            else if (j == 0) p.band(a, y, b);
            else p.f.eldritch(a, y, b);
            return;
        }
        int st = STAGE[a + 2][b + 2];
        int d = Math.floorMod(y - TREAD_J[st], PITCH);
        boolean step = st % 3 != 0;
        if (d == 0) {
            if (step) p.f.set(a, y, b, BRICK_STAIRS, p.f.stairs(RUN_DU[st / 3], RUN_DV[st / 3], false));
            else p.f.masonry(a, y, b);
        } else if (d == PITCH - 1) p.f.masonry(a, y, b);
        else if (y == 0) p.f.set(a, y, b, AIR);                     // the court's paving over the well
    }

    // ------------------------------------------------------------------------------------------------ the crown

    /**
     * The observatory crown: the star-map floor (black stone, constellations in pale stone and bright stars), the parapet and
     * its pinnacles, the pylons that hold the broken lens ring, the ring itself, the last flight of the stair.
     */
    private static boolean hole(int a, int b) { return a >= -2 && a <= -1 && b >= -2 && b <= 1; }

    private static void crown(P p, L l) {
        if (!p.hit(-15, -15, 15, 15) && !p.hit(-12, -12, 12, 12)) return;
        int a0 = Math.max(-15, p.u0), a1 = Math.min(15, p.u1), b0 = Math.max(-15, p.v0), b1 = Math.min(15, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                double r = Math.sqrt(a * a + b * b);
                if (r > 14.5) continue;
                double q = p.f.roll(a, TOP, b, 610);
                if (hole(a, b)) continue;
                if (r >= 11.5 && r < 12.3) p.f.set(a, TOP, b, BRICK, 3);
                else if (q < 0.5) p.f.set(a, TOP, b, CLAY, 15);
                else if (q < 0.82) p.f.set(a, TOP, b, OBSIDIAN);
                else p.f.set(a, TOP, b, CLAY, 7);
                if (r > 13.4) {
                    p.f.eldritch(a, TOP + 1, b);
                    p.f.eldritch(a, TOP + 2, b);
                    if (((a + b) & 1) == 0) p.f.masonry(a, TOP + 3, b);
                }
            }
        for (int i = 0; i < l.stars.size(); i++) {
            int[] st = l.stars.get(i);
            if (!p.in(st[0], st[1]) || hole(st[0], st[1])) continue;
            if (st[2] == 0) p.f.set(st[0], TOP, st[1], BRICK, 3);
            else p.f.set(st[0], TOP, st[1], (st[0] * 7 + st[1] * 13) % 5 == 0 ? SEA_LANTERN : PRISMARINE, 0);
        }
        p.set(0, TOP, 0, SEA_LANTERN);
        // the last flight comes out of the well at the front of the floor: a hole over its last three runs, railed round
        int[][] rail = {{-3, 1}, {-3, 0}, {-3, -1}, {-3, -2}, {-2, 2}, {-1, 2}, {0, 1}, {-2, -3}, {-1, -3}};
        for (int[] h : rail) p.set(h[0], TOP + 1, h[1], IRON_BARS);
        p.set(0, TOP, -2, BRICK_STAIRS, p.f.stairs(1, 0, false));
        p.set(0, TOP, -1, BRICK_STAIRS, p.f.stairs(1, 0, false));
        // pieces of the fallen quarter of the ring lie on the floor in front
        int[][] frag = {{4, -6, 0}, {5, -6, 1}, {4, -7, 1}, {-6, -8, 0}, {-7, -8, 1}, {-6, -9, 2}, {7, -4, 1}, {6, -9, 2}, {8, -8, 0}};
        for (int[] fr : frag) {
            if (!p.in(fr[0], fr[1])) continue;
            if (fr[2] == 0) p.f.set(fr[0], TOP + 1, fr[1], PRISMARINE, 2);
            else if (fr[2] == 1) p.f.set(fr[0], TOP + 1, fr[1], OBSIDIAN, 0);
            else p.f.set(fr[0], TOP + 1, fr[1], 95, 3);
        }
        // eight pinnacles on the parapet
        for (int k = 0; k < 8; k++) {
            double th = Math.toRadians(22.5 + 45 * k);
            int pu = (int) Math.round(13.9 * Math.cos(th)), pv = (int) Math.round(13.9 * Math.sin(th));
            if (!p.in(pu, pv)) continue;
            for (int y = TOP + 1; y <= TOP + 6; y++) p.f.eldritch(pu, y, pv);
            p.f.set(pu, TOP + 7, pv, (k & 1) == 0 ? SEA_LANTERN : OBSIDIAN);
            p.f.set(pu, TOP + 8, pv, OBSIDIAN);
        }
        // the pylons and their arms to the ring
        for (int s = -1; s <= 1; s += 2) {
            p.box(s * 12, TOP + 1, -1, s * 10, TOP + 2, 1, ELD);
            for (int y = TOP + 3; y <= TOP + 9; y++) p.put(s * 11, y, 0, y % 4 == 0 ? GLY : ELD);
            p.set(s * 11, TOP + 10, 0, OBSIDIAN);
            p.set(s * 10, TOP + 9, 0, PRISMARINE, 2);
            p.set(s * 9, TOP + 9, 0, PRISMARINE, 2);
        }
        for (int[] c : l.lens) {
            if (!p.in(c[0], c[2])) continue;
            double q = p.f.roll(c[0], c[1], c[2], 611);
            if (c[3] == 0) {
                if (q < 0.05) p.f.set(c[0], c[1], c[2], SEA_LANTERN, 0);
                else if (q < 0.6) p.f.set(c[0], c[1], c[2], OBSIDIAN, 0);
                else p.f.set(c[0], c[1], c[2], PRISMARINE, 2);
            } else if (c[3] == 1) p.f.set(c[0], c[1], c[2], IRON_BARS, 0);
            else p.f.set(c[0], c[1], c[2], 95, q < 0.7 ? 3 : 9);
        }
    }

    // ------------------------------------------------------------------------------------------------ the shaft's dress

    /** Balconies on the three ledges where the shaft steps in (floors 3, 6, 9): a slab ring two wide behind a railing of bars and posts. */
    private static void balconies(P p) {
        if (!p.hit(-15, -15, 15, 15)) return;
        int a0 = Math.max(-15, p.u0), a1 = Math.min(15, p.u1), b0 = Math.max(-15, p.v0), b1 = Math.min(15, p.v1);
        for (int k = 3; k <= 9; k += 3) {
            int y = PITCH * k;
            double rb = Math.sqrt(O2[y]) + 2.8;
            for (int a = a0; a <= a1; a++)
                for (int b = b0; b <= b1; b++) {
                    int r2 = a * a + b * b;
                    if (r2 <= O2[y] || r2 > rb * rb) continue;
                    p.f.eldritch(a, y, b);
                    if (r2 > (rb - 1.0) * (rb - 1.0)) {
                        if (((a * 5 + b * 3) & 3) == 0) { p.f.set(a, y + 1, b, WALL, 1); p.f.set(a, y + 2, b, WALL, 1); }
                        else p.f.set(a, y + 1, b, IRON_BARS, 0);
                    }
                }
        }
    }

    /** The stepped plinth round the shaft's foot (stairs rising to the wall, left off in front of the door). */
    private static void plinth(P p) {
        if (!p.hit(-15, -15, 15, 15)) return;
        int a0 = Math.max(-15, p.u0), a1 = Math.min(15, p.u1), b0 = Math.max(-15, p.v0), b1 = Math.min(15, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int r2 = a * a + b * b;
                if (r2 <= O2[0] || r2 > 13.6 * 13.6 || b < 0 && Math.abs(a) <= 3) continue;
                int m = Math.abs(a) > Math.abs(b) ? p.f.stairs(a > 0 ? -1 : 1, 0, false) : p.f.stairs(0, b > 0 ? -1 : 1, false);
                p.f.set(a, 1, b, BRICK_STAIRS, m);
            }
    }

    /** A porch before the great door: four chiseled pillars and a roof of slabs over the end of the avenue. */
    private static void porch(P p) {
        if (!p.hit(-4, -16, 4, -12)) return;
        for (int s = -1; s <= 1; s += 2)
            for (int v = -15; v <= -13; v += 2) {
                p.set(3 * s, 1, v, BRICK, 3);
                for (int y = 2; y <= 6; y++) p.f.eldritch(3 * s, y, v);
                p.set(3 * s, 7, v, BRICK, 3);
            }
        for (int a = Math.max(-3, p.u0); a <= Math.min(3, p.u1); a++)
            for (int b = Math.max(-15, p.v0); b <= Math.min(-13, p.v1); b++) {
                p.f.set(a, 7, b, DOUBLE_SLAB, 5);
                if (b == -15) p.band(a, 8, b);
            }
    }

    /** The Plough on the front of the shaft: seven stars of pale light joined by lines of quartz. */
    private static final int[][] DIPPER = {{-8, 61}, {-5, 58}, {-2, 57}, {1, 54}, {1, 49}, {6, 50}, {6, 55}};
    private static final int[][] DIPPER_LINES = {{0, 1}, {1, 2}, {2, 3}, {3, 6}, {3, 4}, {4, 5}, {5, 6}};

    private static void constellation(P p) {
        if (!p.hit(-12, -13, 12, -3)) return;
        for (int[] ln : DIPPER_LINES) {
            int[] s = DIPPER[ln[0]], e = DIPPER[ln[1]];
            int n = Math.max(Math.abs(e[0] - s[0]), Math.abs(e[1] - s[1]));
            for (int i = 1; i < n; i++) face(p, (int) Math.round(s[0] + (double) (e[0] - s[0]) * i / n), (int) Math.round(s[1] + (double) (e[1] - s[1]) * i / n), QUARTZ);
        }
        for (int[] st : DIPPER) face(p, st[0], st[1], SEA_LANTERN);
    }

    private static void face(P p, int u, int y, int id) {
        int j = Math.floorMod(y, PITCH);
        if (u == 0 && j >= 3 && j <= 5) return;
        double o2 = O2[y] - u * u;
        if (o2 < 0) return;
        int v = -(int) Math.floor(Math.sqrt(o2));
        if (p.in(u, v)) p.f.set(u, y, v, id, 0);
    }

    // ------------------------------------------------------------------------------------------------ the floors

    /** The four wedge chambers of a floor lie on the axes: front (-v), right (+u), back (+v), left (-u). */
    private static final int[] AU = {0, 1, 0, -1}, AV = {-1, 0, 1, 0};
    private static final int GUARD = 0, CELLS = 1, DORM = 2, LIB = 3, SCRIPT = 4, INSTR = 5, CHART = 6, ALTAR = 7, LENS = 8;
    private static final int[] THEME = {GUARD, CELLS, DORM, LIB, SCRIPT, INSTR, CHART, CELLS, INSTR, ALTAR, LENS};
    private static final int FRONT = 0, RIGHT = 1, BACK = 2, LEFT = 3;
    /** The floors that open onto a balcony, as a mask of the axes that have a door (the others keep their window slits). */
    private static final int[] BAL = new int[11];
    static {
        BAL[3] = 1 << RIGHT | 1 << LEFT | 1 << BACK;
        BAL[6] = 1 << RIGHT | 1 << LEFT | 1 << BACK;
        BAL[9] = 1 << RIGHT | 1 << LEFT;
    }

    /** The wedge's own coordinates: t along its axis from the centre, s across it. */
    private static int ax(int w, int t, int s) { return AU[w] * t - AV[w] * s; }
    private static int bx(int w, int t, int s) { return AV[w] * t + AU[w] * s; }
    /** The farthest axis cell of a floor that is still room (the shell begins after it). */
    private static int tOut(int k) { return (int) Math.floor(Math.sqrt(I2[PITCH * k + 1] - 1)); }

    private static void storeys(P p) {
        if (!p.hit(-12, -12, 12, 12)) return;
        for (int k = 0; k <= 10; k++) storey(p, k);
    }

    /** What fills the rim of a floor's wedge chambers, by theme, and its special pieces. */
    private static void storey(P p, int k) {
        int y0 = PITCH * k, f = y0 + 1, theme = THEME[k];
        double lane = Math.sqrt(I2[f]) - 1.6;
        int a0 = Math.max(-11, p.u0), a1 = Math.min(11, p.u1), b0 = Math.max(-11, p.v0), b1 = Math.min(11, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int da = Math.abs(a), db = Math.abs(b), r2 = a * a + b * b, m = Math.max(da, db);
                if (m < 4 || da == db || r2 > I2[f]) continue;
                boolean rim = r2 > lane * lane, near = Math.abs(da - db) < 2, side = Math.min(da, db) > 1;
                double q = p.f.roll(a, f, b, 620 + k);
                if (rim && !near && p.f.roll(a, f, b, 640) < 0.03) p.f.set(a, f, b, TORCH, 5);
                if (rim && theme != LENS && p.f.roll(a, y0 + 7, b, 641) < 0.2) p.f.set(a, y0 + 7, b, WEB, 0);
                switch (theme) {
                    case GUARD:
                        if (rim && !near && side && q < 0.14) p.f.set(a, f, b, CAULDRON, 0);
                        break;
                    case CELLS:
                        if (rim && !near && q < 0.10) p.f.set(a, f, b, WEB, 0);
                        else if (rim && !near && q > 0.93) p.f.set(a, f, b, q > 0.97 ? SKULL : BONE, q > 0.97 ? 1 : 0);
                        break;
                    case DORM:
                        if (rim && !near && side && ((a * 3 + b * 5) & 3) == 0) p.f.set(a, f, b, 35, q < 0.5 ? 14 : 0);
                        break;
                    case LIB:
                        if (rim && !near && side && q < 0.88) for (int y = f; y < f + (q < 0.45 ? 4 : 3); y++) p.f.set(a, y, b, BOOKSHELF, 0);
                        break;
                    case SCRIPT:
                        if (rim && !near && side && q < 0.7) for (int y = f; y < f + 3; y++) p.f.set(a, y, b, BOOKSHELF, 0);
                        else if (!rim && !near && r2 > (lane - 1.5) * (lane - 1.5) && q > 0.9) { p.f.set(a, f, b, FENCE, 0); p.f.set(a, f + 1, b, SLAB, 0); }
                        break;
                    case CHART:
                        if (!near && q < 0.16) p.f.set(a, y0, b, q < 0.06 ? PRISMARINE : QUARTZ, 0);
                        else if (rim && !near && side && q > 0.93) { p.f.set(a, f, b, FENCE, 0); p.f.set(a, f + 1, b, SLAB, 0); }
                        break;
                    case ALTAR:
                        if (rim && !near && side && q > 0.9) p.f.set(a, f, b, q > 0.96 ? SKULL : OBSIDIAN, q > 0.96 ? 1 : 0);
                        break;
                    case LENS:
                        if (rim && !near && side && q < 0.2) { p.f.set(a, f, b, WALL, 0); p.f.set(a, f + 1, b, IRON_BARS); }
                        break;
                    default:
                }
            }
        specials(p, k);
    }

    /** A cage of iron bars on a wedge's axis: closed round a spawner, or open round a chest. */
    private static void cage(P p, int k, int w, String mob, String table, String extras) {
        int y0 = PITCH * k, f = y0 + 1, to = tOut(k);
        for (int s = -2; s <= 2; s++)
            for (int t = to - 3; t <= to; t++) {
                boolean face = t == to - 3 && Math.abs(s) <= 1, wall = Math.abs(s) == 2 && t >= to - 3;
                if (!face && !wall) continue;
                if (face && mob == null && s == 0) continue;
                for (int y = f; y <= f + 2; y++) p.set(ax(w, t, s), y, bx(w, t, s), IRON_BARS);
            }
        if (mob != null) {
            for (int t = to + 1; t * t <= O2[f]; t++) for (int y = y0 + 3; y <= y0 + 5; y++) p.set(ax(w, t, 0), y, bx(w, t, 0), IRON_BARS);
            p.f.spawner(ax(w, to - 1, 0), f, bx(w, to - 1, 0), mob);
            p.set(ax(w, to - 2, 1), f, bx(w, to - 2, 1), BONE, 0);
            p.set(ax(w, to, -1), f, bx(w, to, -1), SKULL, 1);
        } else if (table != null) {
            p.f.chest(ax(w, to, 0), f, bx(w, to, 0), -AU[w], -AV[w], table, extras);
            p.set(ax(w, to - 1, 1), f, bx(w, to - 1, 1), BONE, 0);
        }
    }

    private static void wallChest(P p, int k, int w, int s, String table, String extras) {
        int t = tOut(k);
        p.f.chest(ax(w, t, s), PITCH * k + 1, bx(w, t, s), -AU[w], -AV[w], table, extras);
    }

    private static void wallSign(P p, int k, int w, int s, String text) { wallSign(p, k, w, 0, s, text); }

    private static void wallSign(P p, int k, int w, int dt, int s, String text) {
        int t = tOut(k) + dt;
        p.f.sign(ax(w, t, s), PITCH * k + 2, bx(w, t, s), -AU[w], -AV[w], text);
    }

    /** An armillary sphere: three rings of iron bars round a pale star on a stone stem. */
    private static void armillary(P p, int cu, int y0, int cv) {
        int cy = y0 + 4;
        for (int i = 0; i < 16; i++) {
            double th = i * Math.PI / 8;
            int dx = (int) Math.round(2.2 * Math.cos(th)), dz = (int) Math.round(2.2 * Math.sin(th));
            ringBar(p, cu + dx, cy, cv + dz);
            ringBar(p, cu + dx, cy + dz, cv);
            ringBar(p, cu, cy + dx, cv + dz);
        }
        for (int y = y0 + 1; y < cy; y++) ringBar(p, cu, y, cv, WALL);
        ringBar(p, cu, cy, cv, SEA_LANTERN);
    }

    private static void ringBar(P p, int a, int y, int b) { ringBar(p, a, y, b, IRON_BARS); }

    private static void ringBar(P p, int a, int y, int b, int id) {
        if (Math.max(Math.abs(a), Math.abs(b)) < 4) return;
        p.set(a, y, b, id, 0);
    }

    private static void specials(P p, int k) {
        long h = p.s.hash;
        int y0 = PITCH * k, f = y0 + 1, to = tOut(k);
        switch (k) {
            case 0:
                wallChest(p, k, RIGHT, 0, Sites.DUNGEON, null);
                wallChest(p, k, LEFT, 0, Sites.SMITH, null);
                wallSign(p, k, RIGHT, 1, "THE ASTRONOMERS\nCOUNTED STARS.\nTHEN THE STARS\nCOUNTED BACK");
                wallSign(p, k, BACK, 1, "THE GUARDS WERE\nTHE FIRST TO\nLOOK UP");
                for (int s = -1; s <= 1; s += 2) { p.set(ax(BACK, to, 2 * s), f, bx(BACK, to, 2 * s), OBSIDIAN); p.set(ax(BACK, to, 2 * s), f + 1, bx(BACK, to, 2 * s), SKULL, 1); }
                break;
            case 1:
                cage(p, k, RIGHT, "SKELETON", null, null);
                cage(p, k, LEFT, "ZOMBIE", null, null);
                cage(p, k, FRONT, null, Sites.CORRIDOR, null);
                cage(p, k, BACK, null, Sites.SMITH, "trinket:0.2");
                wallSign(p, k, RIGHT, -1, 3, "THEY SAW TOO\nMUCH. WE KEPT\nTHEM HERE FOR\nTHEIR OWN GOOD");
                break;
            case 2:
                wallChest(p, k, BACK, 0, Sites.SMITH, "trinket:0.15");
                wallSign(p, k, FRONT, 1, "THE WATCHERS\nSLEPT IN SHIFTS\nSO THE SKY WAS\nNEVER ALONE");
                break;
            case 3:
                wallChest(p, k, FRONT, 0, Sites.LIBRARY, "lore:" + Hash.range(h, 0, 99));
                wallSign(p, k, RIGHT, 2, "DO NOT READ\nTHE LAST PAGE.\nIT IS READING\nYOU BACK");
                break;
            case 4:
                wallChest(p, k, RIGHT, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h), 0, 99) + ";trinket:0.2");
                break;
            case 5:
                armillary(p, ax(RIGHT, to - 3, 0), y0, bx(RIGHT, to - 3, 0));
                armillary(p, ax(LEFT, to - 3, 0), y0, bx(LEFT, to - 3, 0));
                wallChest(p, k, BACK, 0, Sites.DESERT, "trinket:0.25");
                wallSign(p, k, FRONT, 1, "THE LENS SEES\nWHAT IS NOT\nTHERE YET");
                break;
            case 6:
                wallChest(p, k, FRONT, 0, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h ^ 5), 0, 99));
                wallSign(p, k, LEFT, 1, "EVERY STAR IS\nON THE FLOOR\nTHE FLOOR IS\nWRONG");
                break;
            case 7:
                cage(p, k, FRONT, "CAVE_SPIDER", null, null);
                cage(p, k, BACK, "SPIDER", null, null);
                cage(p, k, RIGHT, null, "minecraft:chests/stronghold_crossing", "trinket:0.3");
                cage(p, k, LEFT, null, "minecraft:chests/nether_bridge", null);
                break;
            case 8:
                armillary(p, ax(LEFT, to - 3, 0), y0, bx(LEFT, to - 3, 0));
                armillary(p, ax(BACK, to - 3, 0), y0, bx(BACK, to - 3, 0));
                wallChest(p, k, RIGHT, 0, "minecraft:chests/igloo_chest", null);
                break;
            case 9:
                for (int s = -2; s <= 2; s++) p.set(ax(BACK, to, s), f, bx(BACK, to, s), OBSIDIAN);
                p.set(ax(BACK, to, 0), f + 1, bx(BACK, to, 0), SKULL, 1);
                p.set(ax(BACK, to, -2), f + 1, bx(BACK, to, -2), MAGMA);
                p.set(ax(BACK, to, 2), f + 1, bx(BACK, to, 2), MAGMA);
                wallChest(p, k, FRONT, 0, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(Hash.mix(h ^ 9), 0, 99) + ";trinket:0.5");
                wallSign(p, k, LEFT, 1, "THE PRIEST WENT\nUP TO LISTEN\nTHE STARS\nANSWERED");
                break;
            case 10:
                wallChest(p, k, LEFT, 0, "minecraft:chests/woodland_mansion", "trinket:0.4");
                wallSign(p, k, FRONT, 1, "THE LAST FLIGHT\nTHE STARS ARE\nWATCHING");
                break;
            default:
        }
    }

    // ------------------------------------------------------------------------------------------------ the undercroft

    /** Under the court: 0 nothing, 1 wall mass, 2 room air, 3 low corridor air, 4 pool, 5 island or bridge. */
    private static int underCode(int a, int b) {
        double r2 = a * a + b * b;
        int code = 0;
        if (r2 <= 12.5 * 12.5) code = 1;
        if (r2 <= 8.6 * 8.6) return 2;
        if (Math.abs(b) <= 1 && (a >= -14 && a <= -9 || a >= 9 && a <= 14) || Math.abs(a) <= 1 && (b >= -14 && b <= -9 || b >= 9 && b <= 14)) return 3;
        if (Math.abs(b) <= 2 && (a >= -15 && a <= -8 || a >= 8 && a <= 15) || Math.abs(a) <= 2 && (b >= -15 && b <= -8 || b >= 8 && b <= 15)) code = 1;
        double d2 = (a + 21) * (a + 21) + b * b;
        if (d2 <= 49) return 2;
        if (d2 <= 68.9) code = 1;
        if (a >= 14 && a <= 27 && Math.abs(b) <= 5) return 2;
        if (a >= 13 && a <= 28 && Math.abs(b) <= 6) code = 1;
        if (Math.abs(a) <= 6 && b >= -26 && b <= -14) return 2;
        if (Math.abs(a) <= 7 && b >= -27 && b <= -13) code = 1;
        if (Math.abs(a) <= 7 && b >= 14 && b <= 27) {
            if (Math.abs(a) <= 4 && b >= 17 && b <= 24) return Math.abs(a) <= 1 && b >= 19 && b <= 21 || a == 0 && b <= 18 ? 5 : 4;
            return 2;
        }
        if (Math.abs(a) <= 8 && b >= 13 && b <= 28) code = 1;
        return code;
    }

    /**
     * The astronomers' undercroft: the round hall at the stair's foot with a mosaic of the heavens, four corridors to the
     * orrery (west), the archive (east), the dormitory (front) and the cistern (back), each lined with masonry.
     */
    private static void undercroft(P p) {
        if (!p.hit(-29, -28, 29, 29)) return;
        int a0 = Math.max(-29, p.u0), a1 = Math.min(29, p.u1), b0 = Math.max(-28, p.v0), b1 = Math.min(29, p.v1);
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                int c = underCode(a, b);
                if (c == 0) continue;
                double r = Math.sqrt(a * a + b * b);
                for (int y = UND; y <= -1; y++) {
                    boolean air = c == 1 ? false : c == 3 ? y >= UND + 1 && y <= UND + 4 : y >= UND + 1;
                    if (air) p.f.set(a, y, b, AIR);
                    else if (y == UND && c == 4) p.f.set(a, y, b, Canvas.WATER, 0);
                    else if (y == UND && c != 1) {
                        if (c == 2 && r <= 8.6 && r >= 5.4 && r < 6.2 || c == 2 && r <= 8.6 && r >= 7.6) p.f.set(a, y, b, PRISMARINE, 2);
                        else if (c == 2 && r < 8.6 && p.f.roll(a, y, b, 650) < 0.05) p.f.set(a, y, b, QUARTZ, 0);
                        else p.f.paving(a, y, b);
                    } else p.f.masonry(a, y, b);
                }
                if (c == 4) {
                    p.f.set(a, UND - 3, b, BRICK, 0);
                    p.f.set(a, UND - 2, b, Canvas.WATER, 0);
                    p.f.set(a, UND - 1, b, Canvas.WATER, 0);
                } else if (Math.abs(a) <= 8 && b >= 13 && b <= 28) for (int y = UND - 3; y < UND; y++) p.f.masonry(a, y, b);
            }
        undercroftFurnish(p);
    }

    private static void ring(P p, int cu, int cv, double r, int y, int id) {
        int n = (int) (r * 8);
        for (int i = 0; i < n; i++) {
            double th = i * TAU / n;
            p.set(cu + (int) Math.round(r * Math.cos(th)), y, cv + (int) Math.round(r * Math.sin(th)), id, 0);
        }
    }

    private static void undercroftFurnish(P p) {
        long h = p.s.hash;
        int f = UND + 1;
        // the hall: eight ribs, two torches, a dart trap at the front corridor's mouth, a sign
        for (int k = 0; k < 8; k++) {
            double th = Math.toRadians(22.5 + 45 * k);
            int pu = (int) Math.round(6.9 * Math.cos(th)), pv = (int) Math.round(6.9 * Math.sin(th));
            if (!p.in(pu, pv)) continue;
            p.f.set(pu, f, pv, BRICK, 3);
            for (int y = f + 1; y <= -2; y++) p.f.eldritch(pu, y, pv);
            p.f.set(pu, -1, pv, BRICK, 3);
        }
        p.set(2, f, -8, TORCH, 5);
        p.set(-2, f, -8, TORCH, 5);
        p.f.dispenser(0, UND, -7, 0, 0);
        p.set(0, f, -7, PLATE);
        p.f.sign(-8, f + 1, -2, 1, 0, "BELOW THE STARS\nTHE DEEP KEEPS\nITS OWN MAP");
        // the orrery: a pale sun on an obsidian pedestal, three rings of iron bars on stone posts, a planet to each
        int cu = -21;
        p.box(cu - 1, f, -1, cu + 1, f, 1, b(OBSIDIAN, 0));
        p.set(cu, f + 1, 0, SEA_LANTERN);
        double[] rr = {2.5, 4.0, 5.3};
        for (int i = 0; i < 3; i++) {
            int y = f + 2 + i;
            ring(p, cu, 0, rr[i], y, IRON_BARS);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int pa = cu + (int) Math.round(d[0] * rr[i]), pb = (int) Math.round(d[1] * rr[i]);
                for (int yy = f + 1; yy < y; yy++) p.set(pa, yy, pb, WALL, 0);
            }
            double th = Math.toRadians(40 + 130 * i);
            p.set(cu + (int) Math.round(rr[i] * Math.cos(th)), y + 1, (int) Math.round(rr[i] * Math.sin(th)), i == 1 ? OBSIDIAN : PRISMARINE, i == 2 ? 2 : 0);
        }
        p.f.chest(-27, f, -3, 1, 0, "minecraft:chests/nether_bridge", "trinket:0.3");
        p.f.sign(-12, f + 1, 1, 0, -1, "THE ORRERY\nTURNS STILL.\nTHE SKY DOES\nNOT");
        // the archive: shelves along the long walls and down the middle, a nest of silverfish at the far end
        for (int a = 16; a <= 26; a++) {
            for (int y = f; y <= f + 3; y++) {
                p.set(a, y, 5, BOOKSHELF, 0);
                p.set(a, y, -5, BOOKSHELF, 0);
                if (a >= 17 && a <= 24 && a != 20) p.set(a, y, 0, BOOKSHELF, 0);
            }
        }
        p.f.spawner(27, f, 0, "SILVERFISH");
        p.f.chest(26, f, 3, -1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 21), 0, 99));
        p.f.chest(26, f, -3, -1, 0, Sites.DUNGEON, "trinket:0.2");
        p.f.sign(14, f + 1, 3, 1, 0, "THE ARCHIVE OF\nWHAT WAS SEEN\nBURN NOTHING");
        // the dormitory: eight cots, a spawner at the back wall
        for (int s = -1; s <= 1; s += 2)
            for (int v = -16; v >= -25; v -= 3) {
                p.set(5 * s, f, v, 35, 14);
                p.set(5 * s, f, v - 1, 35, 0);
            }
        p.f.spawner(0, f, -26, "ZOMBIE");
        p.f.chest(5, f, -14, -1, 0, Sites.SMITH);
        p.f.sign(3, f + 1, -14, 0, -1, "THE WATCHERS\nSLEPT BY DAY.\nBY NIGHT THEY\nWATCHED");
        // the cistern: a chest on the island, a spawner in the corner
        p.f.chest(0, f, 20, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(Hash.mix(h ^ 22), 0, 99) + ";trinket:0.4");
        p.f.spawner(6, f, 26, "CAVE_SPIDER");
        p.f.sign(7, f + 1, 16, -1, 0, "THE STARS DRINK\nHERE WHEN THE\nSKY IS EMPTY");
    }

    // ------------------------------------------------------------------------------------------------ the astronomers' hall

    /** A long hall of stone against the court's back wall: a pitched roof of steps with a slot for the great telescope. */
    private static void hall(P p) {
        if (!p.hit(-22, 13, -8, 24)) return;
        for (int a = Math.max(-21, p.u0); a <= Math.min(-9, p.u1); a++)
            for (int b = Math.max(14, p.v0); b <= Math.min(23, p.v1); b++) {
                boolean wall = a == -21 || a == -9 || b == 14 || b == 23;
                if (wall) for (int y = 1; y <= 6; y++) { if (y == 4 && ((a + b) & 1) == 0 && p.f.roll(a, y, b, 660) < 0.6) p.band(a, y, b); else p.f.eldritch(a, y, b); }
                if (!wall && p.f.roll(a, 0, b, 661) < 0.06) p.f.set(a, 0, b, PRISMARINE, 0); else if (!wall) p.f.paving(a, 0, b);
                int ring = Math.min(Math.min(a + 21, -9 - a), Math.min(b - 14, 23 - b));
                for (int y = 7; y <= 6 + 1 + ring && y <= 11; y++) {
                    boolean slot = a >= -15 && a <= -14 && b >= 18 && b <= 19;
                    if (slot) continue;
                    else if (y == 6 + 1 + ring || y == 11) p.f.masonry(a, y, b);
                    else p.f.eldritch(a, y, b);
                }
            }
        p.box(-15, 1, 14, -14, 3, 14, AIR);
        for (int a = -19; a <= -11; a += 4) { p.set(a, 4, 14, AIR); p.set(a, 5, 14, AIR); p.set(a, 4, 23, AIR); p.set(a, 5, 23, AIR); }
        for (int a = -20; a <= -10; a++) for (int y = 1; y <= 3; y++) { if (a >= -16 && a <= -13) continue; p.set(a, y, 22, BOOKSHELF, 0); }
        for (int a = -18; a <= -12; a++) { if ((a & 1) == 0) { p.set(a, 1, 20, FENCE); p.set(a, 2, 20, SLAB, 0); } else p.set(a, 2, 20, SLAB, 0); }
        p.set(-20, 1, 15, TORCH, 5);
        p.set(-10, 1, 15, TORCH, 5);
        p.f.chest(-20, 1, 18, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(p.s.hash ^ 31), 0, 99));
        p.f.chest(-10, 1, 18, -1, 0, Sites.DESERT, "trinket:0.2");
        p.f.sign(-17, 3, 15, 0, 1, "THE ASTRONOMERS\nSAT HERE AND\nWROTE DOWN\nWHAT THEY SAW");
    }

    /** The pieces that belong to no floor: the gate's signs and chests, the vestibule's trap, the crown's treasure and sign. */
    private static void furnish(P p, Plans.GreatSite s) {
        long h = s.hash;
        p.f.sign(-4, 3, -12, 0, -1, "THE TOWER OF\nSILENT STARS");
        p.f.sign(4, 3, -12, 0, -1, "NO ONE HEARD\nTHE LAST WORD");
        p.f.sign(2, 3, -32, -1, 0, "THE TOWER OF\nSILENT STARS\nTURN BACK");
        p.f.sign(-2, 3, -32, 1, 0, "THE STARS DO\nNOT SPEAK HERE\nLISTEN");
        p.f.chest(8, 1, -33, -1, 0, Sites.SMITH);
        p.f.chest(-8, 1, -33, 1, 0, Sites.DUNGEON);
        p.f.dispenser(0, 0, -8, 0, 0);
        p.set(0, 1, -8, PLATE);
        p.f.chest(-5, TOP + 1, 9, 0, -1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(Hash.mix(h ^ 41), 0, 99) + ";trinket:0.6");
        p.f.chest(5, TOP + 1, 9, 0, -1, "minecraft:chests/woodland_mansion", "trinket:0.4");
        p.f.sign(0, TOP + 1, -13, 0, 1, "THE STARS ARE\nWATCHING. THEY\nHAVE NEVER\nBLINKED");
    }
}
