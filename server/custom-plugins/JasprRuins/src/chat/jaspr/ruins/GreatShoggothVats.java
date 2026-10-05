package chat.jaspr.ruins;

import java.util.Arrays;
import java.util.Random;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Shoggoth Vats (epoch 6): the elder ones' breeding laboratory, where the shoggoths were grown and from which they
 * rose against their makers. A walled yard (gatehouse, corner towers) round an octagonal vat hall under a broken glass
 * dome: four huge vats of ichor (one cracked, spilling into the middle) under a ring of catwalks and hanging cages, and
 * in the middle the Pit, an arena sunk ten blocks into the rock where the Elder Shoggoth lurks, with holding cells, a
 * treasure alcove and a drain. West, the specimen galleries (two floors of jars); east, the elder ones' pentagonal
 * operating theatre, their cold room and their quarters; behind, the sluice house over the drainage channel and the sump
 * beneath it, the rendering chimney (the landmark, sixty blocks of brick) and the shoggoth pens; before the gate, a
 * fallen vat and the offal heap.
 */
final class GreatShoggothVats extends GreatDesign {
    // drawing styles (negative "ids" the painter resolves per block)
    private static final int MAS = -1, ELD = -2, RUB = -3, GLY = -4, PAV = -5, STAIN = -6, LAB = -7, PITF = -8, VGLASS = -9, BRICKY = -10;
    private static final int H2O = 9, STAIRS = 109, NETHER = 112, ICE = 174, TINT = 95, WART = 214, QUARTZ_B = 155, SPONGE_B = 19, SOUL = 88;
    private static final int W = 44;                                                  // the yard wall's outer face
    private static final int[][] VATS = {{-13, -13}, {13, -13}, {13, 13}, {-13, 13}};
    private static final int CX = -34, CZ = 34, CH = 58;                             // the rendering chimney and its grate
    private static final double PCX = 34.5, PCZ = -13.5;                             // the operating theatre's centre

    static final class L extends Layout {
        long h;
        int broken;
        final int[] ichor = new int[4];
        final boolean[] thing = new boolean[4];
    }

    private static final int[][] POINTS = {
        {0, 8, -41}, {-24, 1, -38}, {14, 1, -36}, {-9, 1, -19}, {10, 1, 19}, {-19, 1, 3}, {19, 1, -3}, {0, 19, 19},
        {-6, -9, 4}, {11, -9, 11}, {0, -9, 24}, {2, -9, 34}, {-34, 1, -11}, {-34, 1, 10}, {-38, 8, -5}, {34, 1, -7},
        {33, 1, 13}, {33, 8, 6}, {10, 1, 34}, {CX + 1, 1, CZ - 2}, {28, 1, 32}, {CX, CH + 1, CZ + 1}, {-23, 1, 33}, {-12, 1, 31}};
    private static final String[] PACKS = {
        "cult_zealot+vindicator", "ghoul+zombie", "deep_one+husk", "shoggoth+slime", "!ghoul+husk+zombie_villager", "mi_go+enderman",
        "cult_adept+evoker", "nightgaunt+creeper", "tomb_crawler+cave_spider", "zombie+skeleton", "deep_one+zombie", "tomb_crawler+silverfish",
        "cult_adept+witch", "zombie_villager+silverfish", "illusioner+evoker", "mi_go+endermite", "stray+skeleton", "star_spawn+witch",
        "hound+spider", "wither_skeleton+skeleton", "!shoggoth+slime+slime", "nightgaunt", "ghoul+cult_zealot", "hound+creeper"};

    @Override Layout plan(Plans.GreatSite s, Random r) {
        L l = new L();
        l.h = s.hash;
        l.broken = r.nextInt(4);
        for (int i = 0; i < 4; i++) { l.ichor[i] = 9 + r.nextInt(6); l.thing[i] = r.nextInt(4) > 0; }
        l.ichor[l.broken] = 4;
        l.thing[l.broken] = false;
        l.boss = new int[] {0, -9, 0};
        for (int i = 0; i < POINTS.length; i++) l.garrisons.add(g(POINTS[i][0], POINTS[i][1], POINTS[i][2], PACKS[i]));
        return l;
    }

    @Override void draw(Plans.GreatSite s, Layout plan, Canvas c) {
        L l = (L) plan;
        P p = new P(s, c);
        if (!p.hit(-46, -46, 46, 46)) return;
        ground(p);
        outerWall(p);
        gatehouse(p);
        tower(p, -40, -40);
        tower(p, 40, -40);
        tower(p, 40, 40);
        hall(p);
        pit(p, l);
        vats(p, l);
        catwalks(p);
        westWing(p);
        eastWing(p);
        sump(p);
        sluice(p);
        chimney(p);
        pens(p);
        yard(p);
        roofs(p);
        tiles(p, l);
    }

    // ------------------------------------------------------------------------------------------------ the ground and the walls

    /** The walled plinth: rubble footings down to low land, the hill cut back where the land stands higher. */
    private static void ground(P p) {
        for (int u = p.ua(-W); u <= p.ub(W); u++)
            for (int v = p.va(-W); v <= p.vb(W); v++) {
                int g = p.ground(u, v);
                for (int y = Math.max(-12, g - 1); y < 0; y++) p.set(u, y, v, RUB, 0);
                p.set(u, 0, v, PAV, 0);
                if (g >= 0) p.clear(u, v, 1, g + 1);
            }
    }

    private static void outerWall(P p) {
        for (int u = p.ua(-W); u <= p.ub(W); u++)
            for (int v = p.va(-W); v <= p.vb(W); v++) {
                int m = Math.max(Math.abs(u), Math.abs(v));
                if (m < W - 1 || v < -38 && Math.abs(u) <= 9) continue;
                for (int y = 1; y <= 9; y++) p.set(u, y, v, y == 5 ? GLY : y <= 2 ? MAS : ELD, 0);
                if (m == W && ((u + v) & 1) == 0) p.set(u, 10, v, ELD, 0);
            }
    }

    /** The gatehouse: a vaulted passage with dart plates, two guard rooms, the gate hall above with its murder holes. */
    private static void gatehouse(P p) {
        if (!p.hit(-9, -45, 9, -39)) return;
        for (int u = p.ua(-9); u <= p.ub(9); u++)
            for (int v = p.va(-45); v <= p.vb(-39); v++) {
                int a = Math.abs(u);
                boolean wallU = a == 9, wallV = v == -45 || v == -39;
                for (int y = 1; y <= 13; y++) {
                    boolean shell = wallU || wallV || y == 7 || y == 13 || a == 3 && y <= 6;
                    if (a <= 2 && y <= 6) shell = y == 6 && a == 2;
                    p.set(u, y, v, shell ? (y == 7 || y == 13 ? GLY : y <= 2 ? MAS : ELD) : AIR, 0);
                }
                if ((wallU || wallV) && ((u + v) & 1) == 0) p.set(u, 14, v, ELD, 0);
            }
        p.box(-6, -39, -5, -39, 1, 3, AIR, 0);                                       // the guard rooms open on the yard
        p.box(5, -39, 6, -39, 1, 3, AIR, 0);
        for (int y = 1; y <= 7; y++) p.set(-8, y, -42, LADDER, p.f.facing(1, 0));    // up to the gate hall
        p.box(-1, -43, 1, -43, 7, 7, IRON_BARS, 0);                                  // murder holes
        p.box(-2, -44, 2, -44, 4, 5, IRON_BARS, 0);                                  // the raised portcullis
        for (int u = -6; u <= 6; u += 12) { p.box(u, -45, u, -45, 3, 4, IRON_BARS, 0); p.box(u, -45, u, -45, 10, 11, IRON_BARS, 0); }
        p.set(-3, 5, -45, SEA_LANTERN, 0);
        p.set(3, 5, -45, SEA_LANTERN, 0);
    }

    /** A round corner tower: a door on the yard, a ladder to its crenellated top. */
    private static void tower(P p, int tx, int tz) {
        if (!p.hit(tx - 4, tz - 4, tx + 4, tz + 4)) return;
        int iu = -Integer.signum(tx);
        for (int u = p.ua(tx - 4); u <= p.ub(tx + 4); u++)
            for (int v = p.va(tz - 4); v <= p.vb(tz + 4); v++) {
                int du = u - tx, dv = v - tz, r2 = du * du + dv * dv;
                if (r2 > 17) continue;
                boolean wall = r2 > 9;
                for (int y = 1; y <= 14; y++) p.set(u, y, v, wall ? (y % 7 == 0 ? GLY : ELD) : y == 14 ? PRISMARINE : AIR, wall ? 0 : 2);
                if (wall && ((du + dv) & 1) == 0) p.set(u, 15, v, ELD, 0);
            }
        p.clear(tx + 4 * iu, tz, 1, 3);
        for (int y = 1; y <= 14; y++) p.set(tx - 3 * iu, y, tz, LADDER, p.f.facing(iu, 0));
    }

    // ------------------------------------------------------------------------------------------------ the vat hall

    private static double oct(int u, int v) { int a = Math.abs(u), b = Math.abs(v); return Math.max(Math.max(a, b), (a + b) * 0.70710678); }

    /** The dome's outer surface over a point at octagonal distance d from the centre. */
    private static double domeTop(double d) { double t = d / 25.5; return t >= 1 ? 21 : 21 + 24 * Math.sqrt(1 - t * t); }

    /** The eight ribs of the dome, along the octagon's corners. */
    private static boolean rib(int u, int v) { int a = Math.abs(u), b = Math.abs(v); return Math.abs(b - 0.41421 * a) < 0.75 || Math.abs(a - 0.41421 * b) < 0.75; }

    /** The octagonal drum (two thick, twenty high), its doors and windows, the buttresses, and the ribbed glass dome. */
    private static void hall(P p) {
        if (!p.hit(-29, -29, 29, 29)) return;
        for (int u = p.ua(-29); u <= p.ub(29); u++)
            for (int v = p.va(-29); v <= p.vb(29); v++) {
                double d = oct(u, v);
                if (d >= 28.5) continue;
                int a = Math.abs(u), b = Math.abs(v);
                if (d < 23.5) {
                    if (d >= 12.5) p.set(u, 0, v, LAB, 0);
                    p.clear(u, v, 1, 20);
                } else if (d < 25.5) {
                    boolean door = a <= 2 || b <= 2;
                    for (int y = 1; y <= 20; y++) {
                        if (door && (y <= 5 || y == 6 && (a <= 1 || b <= 1))) { p.set(u, y, v, AIR, 0); continue; }
                        if (y >= 11 && y <= 16 && (a <= 2 || b <= 2 || Math.abs(a - b) <= 2)) { p.set(u, y, v, d < 24.5 ? AIR : IRON_BARS, 0); continue; }
                        p.set(u, y, v, y <= 2 ? MAS : y == 8 || y == 20 ? GLY : STAIN, 0);
                    }
                } else if (b <= 3 && a <= 26) {
                    // the short passages to the wings
                    for (int y = 1; y <= 7; y++) p.set(u, y, v, b == 3 || y == 7 ? ELD : AIR, 0);
                } else if (b > a && rib(u, v)) {
                    int top = 22 - (int) ((d - 25.5) * 4);
                    for (int y = 1; y <= top; y++) p.set(u, y, v, y == top ? GLY : ELD, 0);
                }
                if (d >= 25.5 || d < 3) continue;
                int top = (int) Math.floor(domeTop(d)), bot = d + 1.5 >= 25.5 ? 21 : (int) Math.floor(domeTop(d + 1.5));
                boolean r = rib(u, v);
                for (int y = bot; y <= top; y++) {
                    if (d < 4.3) p.set(u, y, v, OBSIDIAN, 0);
                    else if (r) p.set(u, y, v, y == 22 && d > 23 ? SEA_LANTERN : y % 4 == 0 ? OBSIDIAN : PRISMARINE, 2);
                    else if (y >= 31) { if (p.f.keep(u, y, v, 0.8)) p.set(u, y, v, VGLASS, 0); else p.set(u, y, v, AIR, 0); }
                    else p.set(u, y, v, ELD, 0);
                }
            }
    }

    // ------------------------------------------------------------------------------------------------ the Pit

    /**
     * The Pit: an octagon 25 across sunk ten blocks, its walls streaked with ichor; stairs cut into the side walls as open
     * galleries, four barred holding cells in the corners, the drain behind and the treasure alcove before.
     */
    private static void pit(P p, L l) {
        if (!p.hit(-15, -18, 15, 31)) return;
        for (int u = p.ua(-15); u <= p.ub(15); u++)
            for (int v = p.va(-15); v <= p.vb(15); v++) {
                double d = oct(u, v);
                if (d >= 14.5) continue;
                if (d < 12.5) {
                    p.set(u, -12, v, RUB, 0);
                    p.set(u, -11, v, RUB, 0);
                    double ring = Math.abs(d - 7);
                    p.set(u, -10, v, ring < 0.5 ? GLY : ring < 1.2 && rib(u, v) ? OBSIDIAN : PITF, 0);
                    p.clear(u, v, -9, 0);
                } else {
                    for (int y = -11; y <= -1; y++) p.set(u, y, v, y == -5 && d < 13.5 ? GLY : STAIN, 0);
                    boolean stairTop = Math.abs(u) >= 13 && v >= -6 && v <= -4;
                    if (d < 13.5 && !stairTop) p.set(u, 1, v, IRON_BARS, 0);
                }
            }
        // the gallery stairs in the side walls, open to the pit
        for (int sx = -1; sx <= 1; sx += 2)
            for (int k = 0; k <= 9; k++) {
                int v = -6 + k, y = -1 - k;
                for (int w = 13; w <= 14; w++) {
                    int u = sx * w;
                    if (!p.in(u, v)) continue;
                    for (int yy = -10; yy < y; yy++) p.set(u, yy, v, MAS, 0);
                    p.set(u, y, v, STAIRS, p.f.stairs(0, -1, false));
                    p.clear(u, v, y + 1, Math.min(0, y + 3));
                    if (y + 3 >= 0) p.clear(u, v, 0, y + 3);
                }
            }
        p.set(-12, -6, 1, SEA_LANTERN, 0);
        p.set(12, -6, -1, SEA_LANTERN, 0);
        for (int k = 0; k < 4; k++) p.set(k < 2 ? (k == 0 ? -4 : 4) : 0, -10, k < 2 ? 0 : (k == 2 ? -4 : 4), MAGMA, 0);
        // the holding cells in the four corners
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) {
                if (!p.hit(Math.min(8 * su, 13 * su), Math.min(8 * sv, 13 * sv), Math.max(8 * su, 13 * su), Math.max(8 * sv, 13 * sv))) continue;
                for (int a = 9; a <= 12; a++)
                    for (int b = 9; b <= 12; b++) {
                        int u = a * su, v = b * sv;
                        p.set(u, -10, v, PITF, 0);
                        double d = oct(a, b);
                        boolean front = d < 13.5 && !(a == 9 && b == 9);
                        for (int y = -9; y <= -7; y++) p.set(u, y, v, front ? IRON_BARS : AIR, 0);
                        p.set(u, -6, v, STAIN, 0);
                        if (!front && (a + b) % 5 == 0) p.set(u, -9, v, BONE, 0);
                    }
                for (int a = 9; a <= 13; a++) { p.box(13 * su, a * sv, 13 * su, a * sv, -10, -6, STAIN, 0); p.box(a * su, 13 * sv, a * su, 13 * sv, -10, -6, STAIN, 0); }
                p.set(12 * su, -9, 10 * sv, SKULL, 1);
            }
        // the treasure alcove under the front wall
        if (p.hit(-4, -18, 4, -12)) {
            p.box(-4, -18, 4, -13, -10, -5, STAIN, 0);
            p.box(-3, -17, 3, -13, -9, -6, AIR, 0);
            p.box(-3, -17, 3, -13, -10, -10, PRISMARINE, 2);
            p.box(-1, -17, 1, -17, -10, -10, OBSIDIAN, 0);
            p.set(-3, -7, -18, SEA_LANTERN, 0);
            p.set(3, -7, -18, SEA_LANTERN, 0);
            p.box(-3, -17, -3, -17, -9, -6, GLY, 0);
            p.box(3, -17, 3, -17, -9, -6, GLY, 0);
        }
        // the drain: a tunnel too narrow for the Elder Shoggoth, back to the sump
        if (p.hit(-2, 12, 2, 30)) {
            p.box(-2, 13, 2, 30, -10, -6, STAIN, 0);
            p.box(-1, 12, 1, 30, -9, -7, AIR, 0);
            p.box(-1, 12, 1, 30, -10, -10, PITF, 0);
            for (int v = 16; v <= 28; v += 6) p.set(0, -6, v, SEA_LANTERN, 0);
        }
        // the cracked vat's ichor runs down the pit wall and pools below
        int[] bv = VATS[l.broken];
        int du = -Integer.signum(bv[0]), dv = -Integer.signum(bv[1]);
        for (int k = 0; k <= 4; k++)
            for (int j = -1; j <= 1; j++) {
                int u = du * (9 - k) + j, v = dv * (9 + k) - j * 0;
                if (!p.in(u, v)) continue;
                if (oct(u, v) >= 12.5 && oct(u, v) < 14.5) for (int y = -9; y <= 0; y++) if (p.f.keep(u, y, v, 0.55)) p.set(u, y, v, SLIME, 0);
            }
        for (int u = -6; u <= 6; u++)
            for (int v = -6; v <= 6; v++) {
                int x = du * 8 + u, z = dv * 8 + v;
                if (!p.in(x, z) || u * u + v * v > 20 || oct(x, z) >= 12.5) continue;
                if (p.f.keep(x, -9, z, 0.7)) p.set(x, -10, z, SLIME, 0);
            }
    }

    // ------------------------------------------------------------------------------------------------ the vats

    /** Four huge glass vats of ichor on dark plinths, banded in prismarine, with something curled in most of them. */
    private static void vats(P p, L l) {
        for (int i = 0; i < 4; i++) {
            int cu = VATS[i][0], cv = VATS[i][1];
            boolean broken = i == l.broken;
            double nu = -Math.signum(cu) * 0.7071, nv = -Math.signum(cv) * 0.7071;
            if (p.hit(cu - 6, cv - 6, cu + 6, cv + 6))
                for (int u = p.ua(cu - 6); u <= p.ub(cu + 6); u++)
                    for (int v = p.va(cv - 6); v <= p.vb(cv + 6); v++) {
                        int du = u - cu, dv = v - cv, r2 = du * du + dv * dv;
                        if (r2 > 30) continue;
                        p.set(u, 1, v, PRISMARINE, 2);
                        if (r2 > 24) continue;
                        p.set(u, 2, v, r2 > 13 ? QUARTZ_B : STONE, r2 > 13 ? 0 : 6);
                        boolean facing = du * nu + dv * nv > 1.5;
                        if (r2 > 13) {
                            int crack = broken && facing ? 8 + (int) (p.f.roll(u, 0, v, 43) * 4) : 0;
                            for (int y = 3; y <= 16; y++) {
                                if (y <= crack) { p.set(u, y, v, AIR, 0); continue; }
                                boolean band = y == 7 || y == 12;
                                p.set(u, y, v, band ? PRISMARINE : VGLASS, band ? 2 : 0);
                            }
                            p.set(u, 17, v, PRISMARINE, 2);
                        } else {
                            for (int y = 3; y <= 16; y++) p.set(u, y, v, y <= l.ichor[i] ? SLIME : AIR, 0);
                            if (l.thing[i] && r2 <= 2) {
                                for (int y = 5; y <= 9; y++) if (r2 == 0 || y >= 6 && y <= 8 && p.f.keep(u, y, v, 0.7)) p.set(u, y, v, WART, 0);
                                if (r2 == 1 && p.f.roll(u, 8, v, 44) < 0.5) p.set(u, 8, v, MAGMA, 0);
                            }
                        }
                    }
            if (!broken) continue;
            // the spill: a fan of ichor across the floor toward the pit
            int fu = (int) Math.round(cu + nu * 8), fv = (int) Math.round(cv + nv * 8);
            if (!p.hit(fu - 7, fv - 7, fu + 7, fv + 7)) continue;
            for (int u = p.ua(fu - 7); u <= p.ub(fu + 7); u++)
                for (int v = p.va(fv - 7); v <= p.vb(fv + 7); v++) {
                    double du = u - cu, dv = v - cv, along = du * nu + dv * nv, across = Math.abs(du * nv - dv * nu);
                    if (along < 4.6 || along > 12 || across > 1.5 + (along - 4.6) * 0.5 || oct(u, v) < 13.5) continue;
                    if (p.f.keep(u, 1, v, 0.8)) p.set(u, 1, v, SLIME, 0);
                    if (along < 7 && p.f.keep(u, 2, v, 0.45)) p.set(u, 2, v, SLIME, 0);
                }
        }
    }

    // ------------------------------------------------------------------------------------------------ the catwalks

    private static boolean walk(int u, int v) {
        double d = oct(u, v);
        int a = Math.abs(u), b = Math.abs(v);
        if (d >= 16.5 && d < 21.5) return true;
        if (d < 23.5 && (a <= 5 && b >= 21 || b <= 5 && a >= 21)) return true;
        return a <= 1 && b < 17;
    }

    private static boolean ladderAt(int u, int v) { return u == -4 && v == -23 || u == 4 && v == 23 || u == -23 && v == 4 || u == 23 && v == -4; }

    /** A ring of catwalks over the vats (gratings over their mouths), landings at the walls, a bridge over the Pit, cages. */
    private static void catwalks(P p) {
        if (!p.hit(-24, -24, 24, 24)) return;
        for (int u = p.ua(-23); u <= p.ub(23); u++)
            for (int v = p.va(-23); v <= p.vb(23); v++) {
                if (!walk(u, v) || ladderAt(u, v)) continue;
                boolean grate = false;
                for (int[] t : VATS) if ((u - t[0]) * (u - t[0]) + (v - t[1]) * (v - t[1]) <= 12) grate = true;
                p.set(u, 18, v, grate ? IRON_BARS : SLAB, grate ? 0 : 13);
                boolean edge = !walk(u + 1, v) || !walk(u - 1, v) || !walk(u, v + 1) || !walk(u, v - 1);
                if (edge) p.set(u, 19, v, IRON_BARS, 0);
            }
        int[][] ladders = {{-4, -23, 0, 1}, {4, 23, 0, -1}, {-23, 4, 1, 0}, {23, -4, -1, 0}};
        for (int[] t : ladders) for (int y = 1; y <= 18; y++) p.set(t[0], y, t[1], LADDER, p.f.facing(t[2], t[3]));
        // chains from the dome to the ring's outer rail
        int[][] hang = {{21, 9}, {9, 21}, {-9, 21}, {-21, 9}, {-21, -9}, {-9, -21}, {9, -21}, {21, -9}};
        for (int[] t : hang) {
            if (!p.in(t[0], t[1])) continue;
            int top = (int) Math.floor(domeTop(oct(t[0], t[1]) + 1.5));
            for (int y = 20; y < top; y++) p.set(t[0], y, t[1], IRON_BARS, 0);
        }
        // cages under the bridge and over the vats
        cage(p, 0, 11, -8, 17);
        cage(p, 0, 11, 8, 17);
        for (int[] t : VATS) cage(p, t[0], 24, t[1], (int) Math.floor(domeTop(oct(t[0], t[1]) + 1.5)) - 1);
    }

    /** A hanging cage of bars (3 x 3, four high) with its prisoner's bones, on a chain up to {@code chainTop}. */
    private static void cage(P p, int cu, int y0, int cv, int chainTop) {
        if (!p.hit(cu - 1, cv - 1, cu + 1, cv + 1)) return;
        for (int u = cu - 1; u <= cu + 1; u++)
            for (int v = cv - 1; v <= cv + 1; v++) {
                boolean mid = u == cu && v == cv;
                p.set(u, y0, v, SLAB, 5);
                for (int y = y0 + 1; y <= y0 + 3; y++) p.set(u, y, v, mid ? AIR : IRON_BARS, 0);
                p.set(u, y0 + 4, v, mid ? IRON_BARS : SLAB, mid ? 0 : 5);
            }
        p.set(cu, y0 + 1, cv, BONE, 0);
        p.set(cu, y0 + 2, cv, SKULL, 1);
        for (int y = y0 + 5; y <= chainTop; y++) p.set(cu, y, cv, IRON_BARS, 0);
    }

    // ------------------------------------------------------------------------------------------------ the specimen galleries

    private static final int[][] SPECIMENS = {{SLIME, 0}, {SPONGE_B, 1}, {WART, 0}, {BONE, 0}, {SOUL, 0}, {CLAY, 6}, {MAGMA, 0}, {CLAY, 13}, {SKULL, 1}, {CLAY, 2}};

    /** A great specimen jar: three by three of green glass, two things inside, a slab lid. */
    private static void jar(P p, int cu, int y0, int cv) {
        if (!p.hit(cu - 1, cv - 1, cu + 1, cv + 1)) return;
        int k = (int) (p.f.roll(cu, y0, cv, 45) * SPECIMENS.length), q = (k + 3 + (int) (p.f.roll(cu, y0, cv, 46) * 4)) % SPECIMENS.length;
        int[] a = SPECIMENS[k], b = SPECIMENS[q];
        if (a[0] == SKULL) a = SPECIMENS[0];
        for (int u = cu - 1; u <= cu + 1; u++)
            for (int v = cv - 1; v <= cv + 1; v++) {
                boolean mid = u == cu && v == cv;
                p.set(u, y0 + 1, v, mid ? a[0] : TINT, mid ? a[1] : 5);
                p.set(u, y0 + 2, v, mid ? b[0] : TINT, mid ? b[1] : 5);
                p.set(u, y0 + 3, v, mid ? GLASS : TINT, mid ? 0 : 13);
                p.set(u, y0 + 4, v, SLAB, 5);
            }
    }

    /** West: two floors of specimen rooms off a long corridor, the receiving hall, and the archive upstairs. */
    private static void westWing(P p) {
        if (!p.hit(-42, -24, -26, 24)) return;
        for (int u = p.ua(-42); u <= p.ub(-27); u++)
            for (int v = p.va(-24); v <= p.vb(24); v++) {
                boolean ext = u == -42 || u == -27 || Math.abs(v) == 24;
                if (ext) {
                    for (int y = 1; y <= 14; y++) p.set(u, y, v, y == 7 || y == 14 ? GLY : y <= 2 ? MAS : ELD, 0);
                    if (((u + v) & 1) == 0) p.set(u, 15, v, ELD, 0);
                    continue;
                }
                p.set(u, 0, v, LAB, 0);
                boolean inner = u == -37 || u == -32, cross = (u < -37 || u > -32) && (v == -15 || v == -6 || v == 6 || v == 15);
                for (int y = 1; y <= 6; y++) p.set(u, y, v, inner || cross ? MAS : AIR, 0);
                p.set(u, 7, v, BRICK, 0);
                p.clear(u, v, 8, 13);
                p.set(u, 14, v, PRISMARINE, 2);
            }
        for (int v0 : new int[] {-23, -14, 7, 16}) {
            p.box(-37, v0 + 3, -37, v0 + 4, 1, 3, AIR, 0);
            p.box(-32, v0 + 3, -32, v0 + 4, 1, 3, AIR, 0);
            jar(p, -40, 0, v0 + 1);
            if (v0 != -14) jar(p, -40, 0, v0 + 6);
            jar(p, -29, 0, v0 + 1);
            jar(p, -29, 0, v0 + 6);
        }
        p.box(-37, -1, -37, 0, 1, 3, AIR, 0);
        jar(p, -40, 0, -4);
        jar(p, -40, 0, 3);
        p.box(-32, -2, -32, 2, 1, 4, AIR, 0);                                         // the receiving hall
        p.box(-27, -2, -27, 2, 1, 4, AIR, 0);
        // the spawner's cage in the second room
        for (int u = -41; u <= -39; u++) for (int v = -9; v <= -7; v++) if (u != -40 || v != -8) p.box(u, v, u, v, 1, 2, IRON_BARS, 0);
        p.box(-41, -9, -39, -7, 3, 3, SLAB, 5);
        // the stair to the archive, its well railed
        for (int k = 0; k <= 6; k++) {
            int v = 16 + k, y = 1 + k;
            for (int u = -35; u <= -34; u++) {
                for (int yy = 1; yy < y; yy++) p.set(u, yy, v, MAS, 0);
                p.set(u, y, v, STAIRS, p.f.stairs(0, 1, false));
                p.clear(u, v, y + 1, y + 3);
            }
        }
        p.box(-36, 19, -36, 21, 8, 8, FENCE, 0);
        p.box(-33, 19, -33, 21, 8, 8, FENCE, 0);
        p.box(-35, 18, -34, 18, 8, 8, FENCE, 0);
        // the archive: shelves along the walls, a row of great jars
        for (int u = -41; u <= -28; u += 13)
            for (int v = -23; v <= 23; v++) if (Math.floorMod(v, 5) != 0 && p.in(u, v)) p.box(u, v, u, v, 8, 10, BOOKSHELF, 0);
        for (int cv : new int[] {-19, -10, -1, 8}) jar(p, -35, 7, cv);
        for (int v : new int[] {-20, -11, 0, 11, 20}) { p.box(-42, v, -42, v, 3, 4, IRON_BARS, 0); p.box(-42, v, -42, v, 10, 11, IRON_BARS, 0); }
        for (int v : new int[] {-20, -11, 11, 20}) p.box(-27, v, -27, v, 10, 11, IRON_BARS, 0);
        for (int v : new int[] {-19, -1, 13}) p.set(-35, 7, v, GLOWSTONE, 0);
        p.set(-35, 14, -14, GLOWSTONE, 0);
        p.set(-35, 14, 4, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------ the theatre wing

    private static double pent(double x, double z) {
        double m = -1e9;
        for (int k = 0; k < 5; k++) { double t = Math.toRadians(90 + 72 * k); m = Math.max(m, x * Math.cos(t) + z * Math.sin(t)); }
        return m;
    }

    /** East: the pentagonal operating theatre of the elder ones, the hallway, the cold room, and their quarters above. */
    private static void eastWing(P p) {
        if (!p.hit(26, -24, 42, 24)) return;
        for (int u = p.ua(27); u <= p.ub(42); u++)
            for (int v = p.va(-24); v <= p.vb(24); v++) {
                boolean ext = u == 27 || u == 42 || Math.abs(v) == 24;
                if (ext) {
                    for (int y = 1; y <= 14; y++) p.set(u, y, v, y == 7 && v >= -3 || y == 14 ? GLY : y <= 2 ? MAS : ELD, 0);
                    if (((u + v) & 1) == 0) p.set(u, 15, v, ELD, 0);
                    continue;
                }
                p.set(u, 14, v, PRISMARINE, 2);
                if (v <= -4) {
                    double x = u - PCX, z = v - PCZ, a = pent(x, z);
                    int lv = a <= 2.5 ? 0 : a <= 3.7 ? 1 : a <= 4.9 ? 2 : a <= 6.1 ? 3 : 4;
                    if ((u == 34 || u == 35) && v > -12) lv = 0;                       // the aisle from the door
                    p.set(u, 0, v, lv == 0 && a <= 2.5 ? PRISMARINE : LAB, 2);
                    for (int y = 1; y <= lv; y++) {
                        if (y == lv && lv < 4) p.set(u, y, v, STAIRS, Math.abs(x) > Math.abs(z) ? p.f.stairs(x > 0 ? 1 : -1, 0, false) : p.f.stairs(0, z > 0 ? 1 : -1, false));
                        else p.set(u, y, v, MAS, 0);
                    }
                    p.clear(u, v, lv + 1, 13);
                } else if (v == -3) {
                    for (int y = 1; y <= 13; y++) p.set(u, y, v, y == 7 ? GLY : MAS, 0);
                } else {
                    boolean cold = v >= 4, lining = cold && (u == 28 || u == 41 || v == 23 || v == 4);
                    p.set(u, 0, v, cold ? ICE : LAB, 0);
                    for (int y = 1; y <= 6; y++) p.set(u, y, v, v == 3 ? MAS : lining ? ICE : AIR, 0);
                    p.set(u, 7, v, Math.abs(pent(u - PCX, v - 12) - 3.5) < 0.55 ? CLAY : BRICK, Math.abs(pent(u - PCX, v - 12) - 3.5) < 0.55 ? 15 : 0);
                    p.clear(u, v, 8, 13);
                }
            }
        p.box(27, -2, 27, 2, 1, 4, AIR, 0);                                            // from the passage
        p.box(34, -3, 35, -3, 1, 4, AIR, 0);                                           // into the theatre
        p.box(31, 3, 32, 4, 1, 3, AIR, 0);                                             // into the cold room
        // the operating table, its subject, the lamp, five pillars at the pentagon's corners
        if (p.hit(28, -23, 41, -4)) {
            p.box(34, -15, 35, -13, 1, 1, QUARTZ_B, 2);
            p.set(34, 2, -15, SKULL, 1);
            p.box(34, -14, 35, -14, 2, 2, WART, 0);
            p.set(35, 2, -13, BONE, 0);
            p.set(33, 1, -14, IRON_BARS, 0);
            p.set(36, 1, -14, IRON_BARS, 0);
            p.set(34, 10, -14, SEA_LANTERN, 0);
            p.box(34, -14, 34, -14, 11, 13, IRON_BARS, 0);
            for (int k = 0; k < 5; k++) {
                double t = Math.toRadians(126 + 72 * k);
                int pu = (int) Math.round(PCX + 6.5 * Math.cos(t)), pv = (int) Math.round(PCZ + 6.5 * Math.sin(t));
                for (int y = 1; y <= 13; y++) p.set(pu, y, pv, y == 13 || y == 1 ? GLY : MAS, 0);
            }
            for (int v : new int[] {-18, -10}) p.box(42, v, 42, v, 9, 11, IRON_BARS, 0);
            p.set(41, 9, -21, GLOWSTONE, 0);
            p.set(28, 9, -21, GLOWSTONE, 0);
        }
        // the cold room's hooks and their meat
        for (int hu = 31; hu <= 38; hu += 7)
            for (int hv = 8; hv <= 20; hv += 4) {
                if (!p.in(hu, hv)) continue;
                p.box(hu, hv, hu, hv, 4, 6, IRON_BARS, 0);
                double r = p.f.roll(hu, 3, hv, 47);
                p.set(hu, 3, hv, r < 0.4 ? BONE : r < 0.7 ? WART : SLIME, 0);
                if (r > 0.5) p.set(hu, 2, hv, r > 0.8 ? WART : BONE, 0);
            }
        p.set(35, 6, 12, SEA_LANTERN, 0);
        // the stair up from the hallway to the quarters
        for (int k = 0; k <= 6; k++) {
            int u = 35 + k, y = 1 + k;
            for (int v = 1; v <= 2; v++) {
                for (int yy = 1; yy < y; yy++) p.set(u, yy, v, MAS, 0);
                p.set(u, y, v, STAIRS, p.f.stairs(1, 0, false));
                p.clear(u, v, y + 1, y + 3);
            }
        }
        p.box(38, 0, 40, 0, 8, 8, FENCE, 0);
        p.box(38, 3, 40, 3, 8, 8, FENCE, 0);
        p.box(37, 1, 37, 2, 8, 8, FENCE, 0);
        p.set(30, 6, 0, GLOWSTONE, 0);
        for (int v : new int[] {-1, 8, 16}) p.box(42, v, 42, v, 10, 11, IRON_BARS, 0);
        p.set(41, 13, 12, GLOWSTONE, 0);
    }

    // ------------------------------------------------------------------------------------------------ the sluice and the sump

    /** The sump under the sluice house: the drain's end, a black pool, a stair up through the floor. */
    private static void sump(P p) {
        if (!p.hit(-8, 30, 8, 41)) return;
        for (int u = p.ua(-8); u <= p.ub(8); u++)
            for (int v = p.va(30); v <= p.vb(41); v++) {
                boolean wall = Math.abs(u) == 8 || v == 30 || v == 41;
                p.set(u, -11, v, RUB, 0);
                for (int y = -10; y <= -6; y++) p.set(u, y, v, wall || y == -6 ? STAIN : y == -10 ? PITF : AIR, 0);
            }
        p.box(-1, 30, 1, 30, -9, -7, AIR, 0);
        p.box(-6, 33, -3, 38, -11, -11, MAS, 0);
        p.box(-6, 33, -3, 38, -10, -10, H2O, 0);
        for (int k = 0; k <= 9; k++) {
            int v = 31 + k, y = -9 + k;
            for (int u = 5; u <= 6; u++) {
                for (int yy = -10; yy < y; yy++) p.set(u, yy, v, MAS, 0);
                p.set(u, y, v, STAIRS, p.f.stairs(0, 1, false));
                p.clear(u, v, y + 1, y + 3);
            }
            if (y + 3 > -6) { p.box(4, v, 4, v, -6, Math.min(0, y + 3), MAS, 0); p.box(7, v, 7, v, -6, Math.min(0, y + 3), MAS, 0); }
        }
        p.set(0, -6, 35, SEA_LANTERN, 0);
    }

    /** The sluice house over the drainage channel: two barred gates, bridges, the great ichor pipe from the hall. */
    private static void sluice(P p) {
        if (!p.hit(-17, 22, 17, 44)) return;
        for (int u = p.ua(-17); u <= p.ub(17); u++)
            for (int v = p.va(28); v <= p.vb(42); v++) {
                boolean ext = Math.abs(u) == 17 || v == 28 || v == 42;
                if (ext) {
                    for (int y = 1; y <= 10; y++) p.set(u, y, v, y == 10 || y == 5 ? GLY : y <= 2 ? MAS : ELD, 0);
                    if (((u + v) & 1) == 0) p.set(u, 11, v, ELD, 0);
                    continue;
                }
                p.set(u, 0, v, LAB, 0);
                p.clear(u, v, 1, 9);
                p.set(u, 10, v, PRISMARINE, 2);
            }
        // the channel
        for (int u = p.ua(-3); u <= p.ub(3); u++)
            for (int v = p.va(29); v <= p.vb(44); v++) {
                boolean side = Math.abs(u) == 3 || v == 44;
                if (side) { for (int y = -6; y <= (v >= 42 ? -1 : 0); y++) p.set(u, y, v, MAS, 0); continue; }
                p.set(u, -6, v, MAS, 0);
                for (int y = -5; y <= -2; y++) p.set(u, y, v, H2O, 0);
                p.set(u, -1, v, v >= 42 ? MAS : AIR, 0);
                if (v < 42) p.set(u, 0, v, AIR, 0);
                if (v == 42) for (int y = -5; y <= -2; y++) p.set(u, y, v, IRON_BARS, 0);
            }
        for (int bvv = 32; bvv <= 37; bvv += 5) {
            p.box(-2, bvv - 1, 2, bvv + 1, 0, 0, MAS, 0);
            p.box(-2, bvv - 1, 2, bvv - 1, 1, 1, FENCE, 0);
            p.box(-2, bvv + 1, 2, bvv + 1, 1, 1, FENCE, 0);
        }
        for (int gv = 30; gv <= 40; gv += 10) {
            p.box(-2, gv, 2, gv, -5, -1, IRON_BARS, 0);
            p.box(-3, gv, -3, gv, 1, 6, MAS, 0);
            p.box(3, gv, 3, gv, 1, 6, MAS, 0);
            p.box(-3, gv, 3, gv, 5, 6, ELD, 0);
            p.box(-1, gv, 1, gv, 7, 7, FENCE, 0);
        }
        // the great pipe of ichor from the vat hall
        for (int v = p.va(23); v <= p.vb(38); v++)
            for (int u = p.ua(-1); u <= p.ub(1); u++)
                for (int y = 7; y <= 9; y++) {
                    boolean ring = Math.abs(u) == 1 || y != 8, band = v % 4 == 0;
                    if (v == 38 && y == 7) { p.set(u, y, v, AIR, 0); continue; }
                    p.set(u, y, v, ring ? (band ? PRISMARINE : VGLASS) : SLIME, ring && band ? 2 : 0);
                }
        for (int y = -2; y <= 6; y++) if (p.in(0, 38) && p.f.keep(0, y, 38, 0.5)) p.set(0, y, 38, SLIME, 0);
        p.box(4, 28, 6, 28, 1, 4, AIR, 0);                                             // the doors
        p.box(-17, 33, -17, 35, 1, 3, AIR, 0);
        for (int v = 31; v <= 39; v += 4) p.set(-16, 1, v, CAULDRON, 3);
        p.set(-17, 6, 34, SEA_LANTERN, 0);
        p.set(17, 6, 34, SEA_LANTERN, 0);
        // the well of the sump stair
        p.box(4, 36, 4, 39, 1, 1, FENCE, 0);
        p.box(7, 36, 7, 39, 1, 1, FENCE, 0);
        p.box(5, 36, 6, 36, 1, 1, FENCE, 0);
    }

    // ------------------------------------------------------------------------------------------------ the chimney and the pens

    /** The rendering chimney: a tapering brick stack sixty blocks high over its furnace room, a ladder up to the grate. */
    private static void chimney(P p) {
        if (!p.hit(CX - 8, CZ - 8, CX + 8, CZ + 8)) return;
        for (int u = p.ua(CX - 8); u <= p.ub(CX + 8); u++)
            for (int v = p.va(CZ - 8); v <= p.vb(CZ + 8); v++) {
                int du = u - CX, dv = v - CZ;
                double r = Math.sqrt(du * du + dv * dv);
                if (r > 7.6) continue;
                boolean door = du >= 4 && Math.abs(dv) <= 1;
                if (r > 6.5) { if (!door) { p.set(u, 1, v, MAS, 0); p.set(u, 2, v, SLAB, 5); } continue; }
                double in0 = 6.5 - 1.6;
                if (r < in0) p.set(u, 0, v, NETHER, 0);
                for (int y = 1; y <= CH + 3; y++) {
                    double rad = 6.5 - y * 0.05, in = rad - 1.6, out = y >= CH - 2 ? rad + 1 : rad;
                    if (r < in) {
                        if (y == CH) p.set(u, y, v, du == -1 && dv == 0 ? AIR : IRON_BARS, 0);
                        else p.set(u, y, v, AIR, 0);
                    } else if (r < out) {
                        if (door && y <= 3) { p.set(u, y, v, AIR, 0); continue; }
                        if (y > CH && (r < out - 1.2 || y == CH + 3 && ((du + dv) & 1) == 1)) { p.set(u, y, v, AIR, 0); continue; }
                        if ((y == 5 || y == 6) && (du == 0 || dv == 0)) { p.set(u, y, v, IRON_BARS, 0); continue; }
                        p.set(u, y, v, y >= CH - 2 ? OBSIDIAN : y % 12 == 0 ? PRISMARINE : y <= 8 ? NETHER : BRICKY, y % 12 == 0 ? 2 : 0);
                    }
                }
            }
        for (int y = 1; y < CH; y++) p.set(CX - 2, y, CZ, BRICKY, 0);
        for (int y = 1; y <= CH; y++) p.set(CX - 1, y, CZ, LADDER, p.f.facing(1, 0));
        p.set(CX + 1, 0, CZ + 2, MAGMA, 0);
        p.set(CX + 2, 0, CZ - 2, MAGMA, 0);
        p.set(CX - 3, 0, CZ + 1, MAGMA, 0);
        p.set(CX - 3, 1, CZ + 2, CAULDRON, 0);
        p.set(CX - 3, 1, CZ - 2, CAULDRON, 0);
        p.box(CX + 2, CZ + 3, CX + 3, CZ + 3, 1, 1, CLAY, 15);
        p.set(CX, CH, CZ, GLOWSTONE, 0);
    }

    /** Three barred pens where the young shoggoths were kept, ichor and bones on their floors. */
    private static void pens(P p) {
        for (int k = 0; k < 3; k++) {
            int a0 = 19 + 7 * k, a1 = a0 + 5;
            if (!p.hit(a0, 28, a1, 35)) continue;
            for (int u = p.ua(a0); u <= p.ub(a1); u++)
                for (int v = p.va(28); v <= p.vb(35); v++) {
                    boolean eu = u == a0 || u == a1, ev = v == 28 || v == 35;
                    if (eu && ev) { for (int y = 1; y <= 6; y++) p.set(u, y, v, y == 6 ? GLY : MAS, 0); continue; }
                    if (eu || ev) { for (int y = 1; y <= 5; y++) p.set(u, y, v, IRON_BARS, 0); p.set(u, 6, v, ELD, 0); continue; }
                    p.set(u, 0, v, PITF, 0);
                    if (Math.abs(u - 28) <= 1 && Math.abs(v - 32) <= 1) continue;
                    double r = p.f.roll(u, 1, v, 48);
                    if (r < 0.10) p.set(u, 1, v, SLIME, 0);
                    else if (r < 0.14) p.set(u, 1, v, BONE, 0);
                }
            p.box(a0 + 2, 28, a0 + 3, 28, 1, 2, AIR, 0);
            p.set(a0 + 1, 1, 34, CAULDRON, 2);
        }
    }

    // ------------------------------------------------------------------------------------------------ the yard

    /** The fallen vat (its ichor run out across the yard), the offal heap, the lamps along the way in. */
    private static void yard(P p) {
        if (p.hit(-35, -37, -15, -27))
            for (int u = p.ua(-34); u <= p.ub(-16); u++)
                for (int v = p.va(-36); v <= p.vb(-28); v++)
                    for (int y = 1; y <= 8; y++) {
                        double dy = y - 4.5, dz = v + 32, r2 = dy * dy + dz * dz;
                        if (r2 > 13) continue;
                        if (u == -34) { p.set(u, y, v, MAS, 0); continue; }
                        boolean band = u == -28 || u == -22;
                        if (r2 > 7.3) {
                            if (band) p.set(u, y, v, PRISMARINE, 2);
                            else if (p.f.keep(u, y, v, u > -20 ? 0.35 : 0.7)) p.set(u, y, v, VGLASS, 0);
                        } else if (dy < -1.4) p.set(u, y, v, SLIME, 0);
                    }
        if (p.hit(-16, -38, -4, -26))
            for (int u = p.ua(-15); u <= p.ub(-5); u++)
                for (int v = p.va(-37); v <= p.vb(-27); v++) {
                    double w = 1.5 + (u + 15) / 3.0;
                    if (Math.abs(v + 32) <= w && p.f.keep(u, 1, v, 0.75 - (u + 15) * 0.05)) p.set(u, 1, v, SLIME, 0);
                }
        if (p.hit(17, -40, 31, -26))
            for (int u = p.ua(18); u <= p.ub(30); u++)
                for (int v = p.va(-39); v <= p.vb(-27); v++) {
                    double r = Math.sqrt((u - 24) * (u - 24) + (v + 33) * (v + 33));
                    if (r > 6) continue;
                    int h = (int) ((6 - r) * 0.8) + (p.f.roll(u, 0, v, 49) < 0.3 ? 1 : 0);
                    for (int y = 1; y <= h; y++) {
                        double q = p.f.roll(u, y, v, 50);
                        p.set(u, y, v, q < 0.45 ? RUB : q < 0.65 ? BONE : q < 0.82 ? SLIME : CLAY, q >= 0.82 ? 13 : 0);
                    }
                    if (h > 0 && p.f.roll(u, h, v, 51) < 0.12) p.set(u, h + 1, v, SKULL, 1);
                }
        for (int u = -3; u <= 3; u += 6) { p.box(u, -33, u, -33, 1, 2, WALL, 0); p.set(u, 3, -33, SEA_LANTERN, 0); }
        // litter across the front yard: ichor, bones and glass shards, never on the way in or by the guards' posts
        for (int u = p.ua(-38); u <= p.ub(38); u++)
            for (int v = p.va(-38); v <= p.vb(-27); v++) {
                if (Math.abs(u) <= 4 || Math.abs(u + 24) <= 2 && Math.abs(v + 38) <= 2 || Math.abs(u - 14) <= 2 && Math.abs(v + 36) <= 2) continue;
                if (u < -15 && u > -36 && v > -37 || u > 17 && u < 31 && v > -40 && v < -26) continue;
                double r = p.f.roll(u, 1, v, 52);
                if (r < 0.025) p.set(u, 1, v, SLIME, 0);
                else if (r < 0.04) p.set(u, 1, v, BONE, 0);
                else if (r < 0.05) p.set(u, 1, v, TINT, 5);
            }
    }

    // ------------------------------------------------------------------------------------------------ the roofs

    /**
     * The roofs: a glass skylight and brick vent stacks over the galleries, a stepped pentagonal lantern over the theatre,
     * a glazed monitor over the sluice, and short glass pipes of ichor from the hall's drum into both wings.
     */
    private static void roofs(P p) {
        if (p.hit(-41, -23, -28, 23)) {
            for (int v = p.va(-21); v <= p.vb(21); v++) if (Math.floorMod(v, 6) != 0) p.box(-35, v, -34, v, 14, 14, TINT, 13);
            for (int sv = -16; sv <= 16; sv += 16) { p.box(-40, sv, -39, sv + 1, 15, 18, BRICKY, 0); p.box(-40, sv, -39, sv + 1, 19, 19, IRON_BARS, 0); }
        }
        if (p.hit(28, -23, 41, -4))
            for (int u = p.ua(28); u <= p.ub(41); u++)
                for (int v = p.va(-23); v <= p.vb(-4); v++) {
                    double a = pent(u - PCX, v - PCZ);
                    if (a > 5) continue;
                    int top = 14 + (int) Math.ceil(5 - a);
                    for (int y = 15; y <= top; y++) p.set(u, y, v, y == top && a > 1.2 ? STAIRS : PRISMARINE, y == top && a > 1.2 ? stairsOut(p, u - PCX, v - PCZ) : 2);
                    if (a <= 1.2) { p.set(u, top + 1, v, SEA_LANTERN, 0); p.set(u, top + 2, v, IRON_BARS, 0); }
                }
        for (int sv = 30; sv <= 38; sv += 4) { p.box(-15, sv, -14, sv, 11, 14, BRICKY, 0); p.set(-15, 15, sv, IRON_BARS, 0); }
        if (p.hit(-5, 28, 5, 42))
            for (int u = p.ua(-5); u <= p.ub(5); u++)
                for (int v = p.va(29); v <= p.vb(41); v++) {
                    if (Math.abs(u) == 5) p.box(u, v, u, v, 11, 12, v % 3 == 0 ? ELD : TINT, v % 3 == 0 ? 0 : 13);
                    p.set(u, 13, v, PRISMARINE, 2);
                }
        for (int side = -1; side <= 1; side += 2) {
            if (!p.hit(Math.min(side * 22, side * 28), -1, Math.max(side * 22, side * 28), 1)) continue;
            for (int a = 22; a <= 28; a++)
                for (int v = -1; v <= 1; v++)
                    for (int y = 10; y <= 12; y++) {
                        boolean ring = Math.abs(v) == 1 || y != 11, band = a % 3 == 0;
                        p.set(side * a, y, v, ring ? (band ? PRISMARINE : VGLASS) : SLIME, ring && band ? 2 : 0);
                    }
        }
    }

    private static int stairsOut(P p, double x, double z) { return Math.abs(x) > Math.abs(z) ? p.f.stairs(x > 0 ? -1 : 1, 0, false) : p.f.stairs(0, z > 0 ? -1 : 1, false); }

    // ------------------------------------------------------------------------------------------------ chests, cages, carvings, darts

    private static void tiles(P p, L l) {
        Frame f = p.f;
        long h = l.h;
        // the Pit's hoard, by the Elder Shoggoth
        f.set(0, -10, -17, OBSIDIAN);
        f.chest(0, -9, -17, 0, 1, "minecraft:chests/end_city_treasure", "lore:" + Hash.range(h, 0, 99) + ";trinket:0.7");
        f.chest(-2, -9, -17, 0, 1, Sites.DESERT, "trinket:0.35");
        f.chest(2, -9, -17, 0, 1, Sites.DUNGEON, "lore:" + Hash.range(Hash.mix(h ^ 1), 0, 99));
        f.sign(0, -7, -17, 0, 1, "IT REMEMBERS\nITS MAKERS.\nIT ATE\nTHEM.");
        f.chest(12, -9, 12, -1, 0, Sites.DUNGEON);
        f.spawner(-11, -9, -11, "SKELETON");
        // the specimen galleries and the archive
        f.chest(-38, 1, -23, 0, 1, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 2), 0, 99));
        f.chest(-38, 1, 7, 0, 1, Sites.JUNGLE);
        f.chest(-31, 1, -14, 0, 1, "minecraft:chests/stronghold_crossing");
        f.chest(-31, 1, 16, 0, 1, Sites.DUNGEON, "trinket:0.2");
        f.spawner(-40, 1, -8, "SPIDER");
        f.chest(-40, 8, -22, 1, 0, Sites.LIBRARY, "lore:" + Hash.range(Hash.mix(h ^ 3), 0, 99));
        f.chest(-29, 8, 22, -1, 0, Sites.LIBRARY);
        // the theatre, the cold room, the quarters
        f.chest(28, 5, -23, 1, 0, Sites.DESERT, "trinket:0.25");
        f.sign(34, 7, -23, 0, 1, "THE ELDER\nONES MADE\nTHEM HERE\nTO SERVE");
        f.chest(40, 1, 22, -1, 0, "minecraft:chests/igloo_chest");
        f.spawner(35, 1, 18, "STRAY");
        f.sign(33, 3, 5, 0, 1, "KEEP THEM\nCOLD. KEEP\nTHEM\nASLEEP.");
        f.chest(41, 8, 22, -1, 0, "minecraft:chests/end_city_treasure", "trinket:0.3");
        f.chest(28, 8, 22, 1, 0, "minecraft:chests/stronghold_crossing", "lore:" + Hash.range(Hash.mix(h ^ 4), 0, 99));
        f.sign(34, 10, 23, 0, -1, "THEY HAD\nFIVE SIDES\nAND NO\nMERCY");
        // the sluice, the sump, the chimney, the pens, the gate
        f.chest(-16, 1, 41, 1, 0, Sites.CORRIDOR);
        f.sign(3, 3, 29, 0, -1, "THE ICHOR\nRUNS BACK\nTO THE SEA");
        f.chest(-7, -9, 31, 1, 0, "minecraft:chests/abandoned_mineshaft");
        f.spawner(-7, -9, 40, "ZOMBIE");
        f.chest(CX + 1, CH + 1, CZ, -1, 0, "minecraft:chests/nether_bridge", "trinket:0.2");
        f.chest(23, 1, 34, -1, 0, Sites.DUNGEON);
        f.spawner(36, 1, 33, "CAVE_SPIDER");
        f.chest(-8, 8, -44, 0, 1, Sites.SMITH);
        f.sign(0, 7, -46, 0, -1, "THE SHOGGOTH\nVATS\nTEKELI-LI\nTEKELI-LI");
        f.sign(3, 4, -26, 0, -1, "THE VATS\nARE NOT\nEMPTY");
        // darts under plates: the gate passage, the corridors, the drain
        int[][] traps = {{-1, 0, -42}, {1, 0, -40}, {-34, 0, -3}, {31, 0, 0}, {0, -10, 20}, {34, 0, -9}};
        for (int[] t : traps) { f.dispenser(t[0], t[1], t[2], 0, 0); f.set(t[0], t[1] + 1, t[2], PLATE); }
    }

    // ------------------------------------------------------------------------------------------------ the painter

    /** A chunk-clipped painter in the structure's local frame (u across, v front to back, y above the base). */
    private static final class P {
        final Plans.GreatSite s;
        final Canvas c;
        final Frame f;
        final int u0, u1, v0, v1;
        private final int[] gnd = new int[256];

        P(Plans.GreatSite s, Canvas c) {
            this.s = s;
            this.c = c;
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

        /** The land's height above the base at a column of this chunk (from the site's plan, never from the canvas). */
        int ground(int u, int v) {
            int wx = f.wx(u, v), wz = f.wz(u, v), i = (wx - c.x0) << 4 | (wz - c.z0);
            if (wx < c.x0 || wx > c.x0 + 15 || wz < c.z0 || wz > c.z0 + 15) return s.surface(wx, wz) - s.base;
            int g = gnd[i];
            if (g == Integer.MIN_VALUE) gnd[i] = g = s.surface(wx, wz) - s.base;
            return g;
        }

        void set(int u, int y, int v, int id, int meta) {
            if (id >= 0) { f.set(u, y, v, id, meta); return; }
            double r;
            switch (id) {
                case MAS: f.masonry(u, y, v); break;
                case ELD: f.eldritch(u, y, v); break;
                case RUB: f.rubble(u, y, v); break;
                case GLY: f.glyph(u, y, v); break;
                case PAV: f.paving(u, y, v); break;
                case STAIN:
                    r = f.roll(u, y, v, 31);
                    if (r < 0.05) f.set(u, y, v, SLIME);
                    else if (r < 0.12) f.set(u, y, v, CLAY, 13);
                    else if (r < 0.16) f.set(u, y, v, MOSSY);
                    else f.masonry(u, y, v);
                    break;
                case LAB:
                    r = f.roll(u, y, v, 32);
                    if (r < 0.02) f.set(u, y, v, SLIME);
                    else if (Math.floorMod(u, 6) == 0 || Math.floorMod(v, 6) == 0) f.set(u, y, v, PRISMARINE, 2);
                    else f.set(u, y, v, STONE, r < 0.35 ? 5 : 6);
                    break;
                case PITF:
                    r = f.roll(u, y, v, 33);
                    if (r < 0.10) f.set(u, y, v, SLIME);
                    else if (r < 0.22) f.set(u, y, v, CLAY, 13);
                    else if (r < 0.50) f.set(u, y, v, MOSSY);
                    else if (r < 0.75) f.set(u, y, v, COBBLE);
                    else f.set(u, y, v, BRICK, 2);
                    break;
                case VGLASS:
                    r = f.roll(u, y, v, 34);
                    if (r < 0.45) f.set(u, y, v, TINT, 5);
                    else if (r < 0.75) f.set(u, y, v, TINT, 13);
                    else f.set(u, y, v, GLASS);
                    break;
                case BRICKY:
                    r = f.roll(u, y, v, 35);
                    if (r < 0.84) f.set(u, y, v, 45);
                    else if (r < 0.95) f.set(u, y, v, BRICK, 2);
                    else f.set(u, y, v, NETHER);
                    break;
                default: break;
            }
        }

        void box(int a0, int b0, int a1, int b1, int y0, int y1, int id, int meta) {
            for (int u = Math.max(a0, u0); u <= Math.min(a1, u1); u++)
                for (int v = Math.max(b0, v0); v <= Math.min(b1, v1); v++)
                    for (int y = y0; y <= y1; y++) set(u, y, v, id, meta);
        }

        void clear(int u, int v, int y0, int y1) { if (in(u, v)) for (int y = y0; y <= y1; y++) f.set(u, y, v, AIR, 0); }
    }
}
