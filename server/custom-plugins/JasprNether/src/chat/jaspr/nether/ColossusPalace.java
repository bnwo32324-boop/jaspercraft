package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Palace of the Burning Throne (owner, 2026-10-04: "Add 10 new structures to the Nether ... huge structures"): a royal
 * palace on a terraced acropolis of red nether brick, banded and capped in gold-coloured terracotta, lit by braziers,
 * fire pits and fountains of lava, in a cavern of ash and lava lakes. One axis climbs from the cavern floor to the throne:
 * <ul>
 *   <li>the Avenue of Fire and the Plaza of Ash (lava canals and fountains, obelisks, two colossal hellhounds);</li>
 *   <li>the Grand Stair to the lower terrace and the Gate of Embers, a portico of columns and braziers over a passage of
 *       arrow slits;</li>
 *   <li>the Court of Fire, colonnaded all round, its altar of fire, fire pits, lava channels and flame vents; the barracks
 *       of the Royal Guard to its west, the royal kennel of hellhounds to its east;</li>
 *   <li>the Inner Stair between lava fountains to the upper terrace and its colonnade; the Garden of Embers and the
 *       Queen's house (west); the Treasury Tower, whose floors give way under thieves, and the War Room (east); the King's
 *       apartments behind;</li>
 *   <li>the Hall of the Burning Throne: forty-five blocks high, its columns, rivers of lava, and behind the throne the
 *       seated colossus of the Burning King crowned in fire; the King fights before it, and under its feet a gilded door
 *       to the Treasury of Ash opens only when he falls. Watch towers keep the hall's roof.</li>
 * </ul>
 */
final class ColossusPalace extends ColossusDesign {
    static final int Y1 = 10, Y2 = 20;                        // the terraces' tops above the cavern floor
    static final int T1U = 100, T1V0 = -90, T1V1 = 100;       // the lower terrace
    static final int T2U = 80, T2V0 = -10, T2V1 = 92;         // the upper terrace
    static final int HU = 24, HV0 = 4, HV1 = 66, HTOP = 67;   // the throne hall, its ceiling at y0 + HTOP

    static final class Plan {
        int rot;
        final List<int[]> pillars = new ArrayList<>();       // basalt pillars on the cavern floor: u, v, height
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette -----------------------------------------------------------------------------------------------------------
    private static final int RNB = b(215), NB = b(112), GOLD = b(159, 4), ORANGE = b(159, 1), BLACK = b(159, 15), CRIMSON = b(159, 14);
    private static final int YELLOW_C = b(251, 4), RED_C = b(251, 14), ORANGE_C = b(251, 1), BLACK_C = b(251, 15), COAL = b(173);
    private static final int GLAZED_Y = b(239, 0), GLAZED_O = b(236, 0), GLAZED_R = b(249, 0);
    private static final int QUARTZ = b(155, 0), QCHISEL = b(155, 1), QPILLAR = b(155, 2);
    private static final int RACK = b(87), FIRE = b(51), GLOW = b(89), MAGMA = b(213), LAVA = b(11), OBSIDIAN = b(49);
    private static final int FENCE = b(113), BARS = b(101), SOUL = b(88), BONE = b(216, 0), WART = b(214), WART_CROP = b(115, 3);
    private static final int GLASS_O = b(95, 1), GLASS_R = b(95, 14), SLAB_TOP = b(44, 14), CARPET_R = b(171, 14), CARPET_Y = b(171, 4);
    private static final int NSTAIR = 114, QSTAIR = 156, SEAL = GLAZED_Y;

    // ---- helpers (local frame) ---------------------------------------------------------------------------------------------
    static int lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    static int lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }
    static int wx(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.x - v; case 2: return s.x - u; case 3: return s.x + v; default: return s.x + u; } }
    static int wz(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.z + u; case 2: return s.z - v; case 3: return s.z - u; default: return s.z + v; } }
    static int ri(double v) { return (int) Math.round(v); }

    static boolean touches(Draw.Frame f, double u0, double v0, double u1, double v1) {
        double xa = f.xd(u0, v0), za = f.zd(u0, v0), xb = f.xd(u1, v1), zb = f.zd(u1, v1);
        return f.d.touches((int) Math.floor(Math.min(xa, xb)), (int) Math.floor(Math.min(za, zb)), (int) Math.ceil(Math.max(xa, xb)), (int) Math.ceil(Math.max(za, zb)));
    }

    interface Col { void at(int x, int z, int u, int v); }

    static void columns(Draw.Frame f, double ua, double va, double ub, double vb, Col c) {
        Draw d = f.d;
        double x0 = f.xd(ua, va), x1 = f.xd(ub, vb), z0 = f.zd(ua, va), z1 = f.zd(ub, vb);
        int xa = Math.max(d.x0, (int) Math.floor(Math.min(x0, x1))), xb = Math.min(d.x1, (int) Math.ceil(Math.max(x0, x1)));
        int za = Math.max(d.z0, (int) Math.floor(Math.min(z0, z1))), zb = Math.min(d.z1, (int) Math.ceil(Math.max(z0, z1)));
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) c.at(x, z, lu(f, x, z), lv(f, x, z));
    }

    /** A spiral stair round a newel in a 3x3 well (walls round it) from ya to yb, open at the top. */
    static void spiral(Draw.Frame f, int u, int v, int ya, int yb, Draw.Mat wall) {
        f.box(u - 2, ya, v - 2, u + 2, yb, v + 2, wall);
        f.box(u - 1, ya, v - 1, u + 1, yb, v + 1, Draw.AIR);
        f.box(u, ya, v, u, yb - 1, v, Draw.of(NB));
        int[][] ring = RING;
        for (int y = ya; y <= yb; y++) { int[] q = ring[Math.floorMod(y, 8)]; f.set(u + q[0], y, v + q[1], RNB); }
    }

    static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    /** A door out of a spiral's well at level y (the floor the step there stands on): beside that step's cell. */
    static void spiralDoor(Draw.Frame f, int u, int v, int y, int len) {
        int[] q = RING[Math.floorMod(y, 8)];
        int du = q[0] != 0 ? q[0] : 0, dv = q[0] != 0 ? 0 : q[1];
        for (int k = 2; k <= 1 + len; k++) f.box(u + q[0] + du * (k - 1), y + 1, v + q[1] + dv * (k - 1), u + q[0] + du * (k - 1), y + 3, v + q[1] + dv * (k - 1), Draw.AIR);
    }

    /** A brazier: a gold bowl on a post, burning (w = half the bowl's width). */
    static void brazier(Draw.Frame f, int u, int v, int yb, int post, int w) {
        if (post > 0) f.box(u, yb, v, u, yb + post - 1, v, Draw.of(RNB));
        int y = yb + post;
        f.box(u - w, y, v - w, u + w, y, v + w, Draw.of(GOLD));
        if (w > 0) f.box(u - w + 1, y, v - w + 1, u + w - 1, y, v + w - 1, Draw.of(RACK)); else f.set(u, y, v, RACK);
        if (w > 0) f.box(u - w + 1, y + 1, v - w + 1, u + w - 1, y + 1, v + w - 1, Draw.of(FIRE)); else f.set(u, y + 1, v, FIRE);
        if (w > 0) for (int a = -1; a <= 1; a += 2) for (int c = -1; c <= 1; c += 2) f.set(u + a * w, y + 1, v + c * w, GOLD);
    }

    /** A lava fountain: a column of lava in a sheath of orange glass rising from a basin. */
    static void fountain(Draw.Frame f, int u, int v, int yb, int h) {
        f.box(u - 2, yb, v - 2, u + 2, yb, v + 2, Draw.of(GOLD));
        f.box(u - 1, yb, v - 1, u + 1, yb, v + 1, Draw.of(LAVA));
        f.box(u - 2, yb - 1, v - 2, u + 2, yb - 1, v + 2, Draw.of(NB));
        f.box(u - 1, yb + 1, v - 1, u + 1, yb + h, v + 1, Draw.of(GLASS_O));
        f.box(u, yb + 1, v, u, yb + h, v, Draw.of(LAVA));
        f.set(u, yb + h + 1, v, GOLD);
        f.set(u, yb + h + 2, v, RACK);
        f.set(u, yb + h + 3, v, FIRE);
    }

    /** A hipped roof over a box: courses of nether-brick stairs stepping in, gold eaves, then a flat top with a gold rim. */
    static void roof(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int courses) {
        int k = 0;
        for (; k < courses; k++) {
            int ua = u0 + k, ub = u1 - k, va = v0 + k, vb = v1 - k, y = yb + k;
            if (ub - ua < 2 || vb - va < 2) break;
            f.box(ua + 1, y, va + 1, ub - 1, y, vb - 1, Draw.of(NB));
            for (int u = ua; u <= ub; u++) { f.set(u, y, va, f.stair(NSTAIR, 2, false)); f.set(u, y, vb, f.stair(NSTAIR, 3, false)); }
            for (int v = va + 1; v < vb; v++) { f.set(ua, y, v, f.stair(NSTAIR, 0, false)); f.set(ub, y, v, f.stair(NSTAIR, 1, false)); }
            if (k == 0) for (int[] q : new int[][]{{ua, va}, {ub, va}, {ua, vb}, {ub, vb}}) f.set(q[0], y, q[1], GOLD);
        }
        int ua = u0 + k, ub = u1 - k, va = v0 + k, vb = v1 - k, y = yb + k;
        if (ub < ua || vb < va) return;
        f.box(ua, y, va, ub, y, vb, Draw.of(GOLD));
        if (ub - ua >= 2 && vb - va >= 2) f.box(ua + 1, y, va + 1, ub - 1, y, vb - 1, Draw.of(NB));
    }

    /** A column: base of quartz, shaft of red brick banded in gold, a gold capital with a glowing heart. */
    static void column(Draw.Frame f, int u, int v, int ya, int yb, int w) {
        f.box(u - w, ya, v - w, u + w, ya, v + w, Draw.of(QUARTZ));
        f.box(u - w, ya + 1, v - w, u + w, yb - 1, v + w, (x, y, z) -> Math.floorMod(y - ya, 8) == 0 ? GOLD : RNB);
        f.box(u - w - (w > 0 ? 1 : 0), yb, v - w - (w > 0 ? 1 : 0), u + w + (w > 0 ? 1 : 0), yb, v + w + (w > 0 ? 1 : 0), Draw.of(GOLD));
    }

    // ---- plan ----------------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        // pillars of basalt on the ash plain round the acropolis
        for (int t = 0; t < 300 && p.pillars.size() < 26; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rr = 120 + r.nextDouble() * 45;
            int u = ri(Math.cos(a) * rr), v = ri(Math.sin(a) * rr), h = 6 + r.nextInt(16);
            if (Math.abs(u) <= T1U + 6 && v >= T1V0 - 8 && v <= T1V1 + 6) continue;
            if (Math.abs(u) < 48 && v < 0) continue;                          // the plaza and the avenue
            if (s.f(wx(s, p.rot, u, v), wz(s, p.rot, u, v)) > 0.86) continue;
            boolean clash = false;
            for (int[] q : p.pillars) if (Math.abs(q[0] - u) + Math.abs(q[1] - v) < 12) clash = true;
            if (!clash) p.pillars.add(new int[]{u, v, h});
        }
        int y0 = s.y, t1 = y0 + Y1, t2 = y0 + Y2;
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(0, y0 + 1, -128, "!ember_legionnaire+flame_adept"));             // the Plaza of Ash
        gs.add(g(-30, y0 + 1, -126, "hellhound+hellhound+magma_cube"));
        gs.add(g(30, y0 + 1, -126, "salamander+ember"));
        gs.add(g(0, t1 + 1, -86, "royal_guard+ember_legionnaire"));             // the portico
        gs.add(g(-11, t1 + 1, -76, "!royal_guard+flame_adept"));                // the Gate of Embers (its guard rooms)
        gs.add(g(11, t1 + 1, -76, "ember_legionnaire+pyre_warden"));
        gs.add(g(0, t1 + 1, -58, "!flame_adept+blaze"));                        // the Court of Fire
        gs.add(g(-24, t1 + 1, -44, "salamander+magma_cube+cinder_imp"));
        gs.add(g(24, t1 + 1, -44, "blaze+ember+cinder_imp"));
        gs.add(g(-66, t1 + 1, -62, "!royal_guard+royal_guard"));                // the barracks
        gs.add(g(-80, t1 + 1, -36, "ember_legionnaire+pigman_berserker"));
        gs.add(g(-60, t1 + 1, -60, "infernal_knight+dread_rider"));
        gs.add(g(72, t1 + 1, -70, "!hellhound+hellhound"));                     // the kennel
        gs.add(g(80, t1 + 1, -36, "hellhound+dread_rider"));
        gs.add(g(0, t2 + 1, -4, "!pyre_warden+royal_guard"));                   // the Court of the Sun
        gs.add(g(-46, t2 + 1, 14, "cinder_witch+charred_ghoul"));                // the Garden of Embers
        gs.add(g(-46, t2 + 1, 56, "salamander+magma_cube"));
        gs.add(g(-70, t2 + 1, 30, "!pigman_berserker+zombie_pigman"));          // the Queen's house
        gs.add(g(52, t2 + 1, 10, "!pyre_warden+flame_adept"));                   // the Treasury Tower, floor by floor
        gs.add(g(52, t2 + 17, 10, "magma_hulk+cinder_imp"));
        gs.add(g(52, t2 + 33, 10, "royal_guard+dread_rider"));
        gs.add(g(39, t2 + 1, 40, "!infernal_knight+royal_guard"));             // the War Room
        gs.add(g(0, t2 + 1, 80, "dread_rider+wither_skeleton"));                // the King's apartments
        gs.add(g(-10, t2 + 1, 14, "!royal_guard+royal_guard+flame_adept"));     // the King's guard in the hall
        gs.add(g(10, t2 + 1, 14, "pyre_warden+royal_guard"));
        gs.add(g(18, y0 + HTOP + 2, 35, "ember_legionnaire+blaze"));            // the hall's roof
        complete(gs);
        // the great fliers in the open air over the courts and the plaza
        gs.add(g(0, t1 + 34, -44, "ghast+ghastling"));
        gs.add(g(-46, t2 + 30, 40, "ghastling+ghastling+ghast"));
        gs.add(g(0, y0 + 30, -126, "ghast+ghastling"));
        p.garrisons = gs;
        return p;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        terraces(f, s);
        approach(f, s);
        gate(f, s);
        court(f, s);
        barracks(f, s);
        kennel(f, s);
        innerStair(f, s);
        throneHall(f, s);
        statue(f, s);
        treasury(f, s);
        warRoom(f, s);
        garden(f, s);
        apartments(f, s);
        towers(f, s);
        ashPlain(f, s, p);
        garrisons(f, p.garrisons);
    }

    /** The two terraces of the acropolis, column by column: battered walls banded in gold, buttresses, parapets, paving. */
    private void terraces(Draw.Frame f, Colossi.Site s) {
        final int y0 = s.y;
        if (!touches(f, -T1U - 3, T1V0 - 3, T1U + 3, T1V1 + 3)) return;
        final Draw d = f.d;
        columns(f, -T1U - 3, T1V0 - 3, T1U + 3, T1V1 + 3, (x, z, u, v) -> {
            int au = Math.abs(u);
            int e1u = T1U - au, e1v = Math.min(v - T1V0, T1V1 - v), d1 = Math.min(e1u, e1v);
            int e2u = T2U - au, e2v = Math.min(v - T2V0, T2V1 - v), d2 = Math.min(e2u, e2v);
            if (d1 < -2) return;
            if (d1 < 0) {
                int along = e1u <= e1v ? v : u;
                boolean stair = v < T1V0 && au <= 22;
                boolean butt = !stair && Math.floorMod(along + 1, 12) <= 2;
                int h = butt ? y0 + Y1 - 1 : d1 == -1 ? y0 + 2 : y0 + 1;
                if (stair) return;
                for (int y = y0 - 3; y <= h; y++) d.set(x, y, z, y <= y0 + 1 ? NB : Math.floorMod(y - y0, 5) == 0 ? GOLD : RNB);
                if (butt && d1 == -1 && Math.floorMod(along, 24) == 0) { d.set(x, h + 1, z, RACK); d.set(x, h + 2, z, FIRE); }
                return;
            }
            boolean upper = d2 >= 0;
            int top = y0 + (upper ? Y2 : Y1), edge = upper ? d2 : d1, base = upper ? y0 + Y1 : y0;
            for (int y = y0 - 3; y < top; y++) {
                int bl;
                if (edge <= 1 && y > base) bl = Math.floorMod(y - y0, 5) == 0 ? GOLD : y == top - 1 ? ORANGE : RNB;
                else bl = y <= y0 + 1 ? NB : RNB;
                d.set(x, y, z, bl);
            }
            d.set(x, top, z, edge == 0 ? QUARTZ : upper ? (Math.floorMod(u, 8) == 0 || Math.floorMod(v, 8) == 0 ? GOLD : RNB)
                : (Math.floorMod(u + v, 2) == 0 ? NB : RNB));
            if (edge == 0) {
                int along = (upper ? e2u <= e2v : e1u <= e1v) ? v : u;
                boolean open = upper ? (v < T2V0 + 1 && au <= 12) : (v < T1V0 + 1 && au <= 17);
                if (!open) {
                    d.set(x, top + 1, z, RNB);
                    if (Math.floorMod(along, 4) == 0) { d.set(x, top + 2, z, GOLD); if (Math.floorMod(along, 16) == 0) { d.set(x, top + 3, z, RACK); d.set(x, top + 4, z, FIRE); } }
                }
            }
            for (int y = top + 1 + (edge == 0 ? 4 : 0); y <= top + 4; y++) d.set(x, y, z, 0);
            if (!upper && d2 >= -2 && d2 < 0) {   // the upper terrace's skirt and buttresses on the lower one
                int along2 = e2u <= e2v ? v : u;
                boolean stair2 = v < T2V0 && au <= 14;
                if (!stair2) {
                    boolean butt = Math.floorMod(along2 + 1, 12) <= 2;
                    int h = butt ? y0 + Y2 - 1 : d2 == -1 ? top + 2 : top + 1;
                    for (int y = top; y <= h; y++) d.set(x, y, z, Math.floorMod(y - y0, 5) == 0 ? GOLD : RNB);
                    if (butt && d2 == -1 && Math.floorMod(along2, 24) == 0) { d.set(x, h + 1, z, RACK); d.set(x, h + 2, z, FIRE); }
                }
            }
        });
    }

    // ---- the Avenue of Fire, the Plaza of Ash, the Grand Stair ----------------------------------------------------------------
    private void approach(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        // the avenue from the cavern's edge, braziers and obelisks along it
        if (touches(f, -10, -184, 10, -150)) for (int v = -184; v <= -151; v++) {
            int x = f.x(0, v), z = f.z(0, v);
            if (s.f(x, z) > 0.98) continue;
            int top = Math.max(y0, s.floorAt(x, z));
            f.box(-5, top - 4, v, 5, top - 1, v, Draw.of(NB));
            for (int u = -5; u <= 5; u++) f.set(u, top, v, Math.abs(u) == 5 ? GOLD : Math.floorMod(v, 4) == 0 ? RNB : NB);
            f.box(-4, top + 1, v, 4, top + 5, v, Draw.AIR);
            if (Math.floorMod(v, 10) == 0) for (int side = -1; side <= 1; side += 2) brazier(f, side * 7, v, top + 1, 3, 0);
        }
        // the plaza
        if (touches(f, -46, -152, 46, -100)) {
            f.box(-44, y0 - 2, -150, 44, y0 - 1, -105, Draw.of(NB));
            f.box(-44, y0, -150, 44, y0, -105, (x, y, z) -> {
                int u = lu(f, x, z), v = lv(f, x, z);
                if (Math.abs(u) <= 4) return Math.floorMod(v, 3) == 0 ? GOLD : RNB;
                return Math.floorMod(u, 6) == 0 || Math.floorMod(v, 6) == 0 ? ORANGE : (Math.floorMod(u + v, 2) == 0 ? NB : RNB);
            });
            f.box(-44, y0 + 1, -150, 44, y0 + 6, -105, Draw.AIR);
            // the lava canals with their fountains
            for (int side = -1; side <= 1; side += 2) {
                f.box(side * 12, y0, -146, side * 22, y0, -110, Draw.of(GOLD));
                f.box(side * 13, y0, -145, side * 21, y0, -111, Draw.of(LAVA));
                f.box(side * 12, y0 + 1, -146, side * 22, y0 + 1, -110, Draw.of(RNB));
                f.box(side * 13, y0 + 1, -145, side * 21, y0 + 1, -111, Draw.AIR);
                fountain(f, side * 17, -138, y0, 7);
                fountain(f, side * 17, -118, y0, 7);
                // obelisks at the plaza's corners
                for (int v : new int[]{-147, -128}) {
                    int u = side * 38;
                    f.box(u - 2, y0 + 1, v - 2, u + 2, y0 + 2, v + 2, Draw.of(BLACK));
                    f.box(u - 1, y0 + 3, v - 1, u + 1, y0 + 20, v + 1, (x, y, z) -> Math.floorMod(y - y0, 6) == 0 ? GOLD : CRIMSON);
                    f.set(u, y0 + 21, v, GOLD);
                    f.set(u, y0 + 22, v, RACK);
                    f.set(u, y0 + 23, v, FIRE);
                }
            }
            for (int v = -146; v <= -110; v += 9) for (int side = -1; side <= 1; side += 2) brazier(f, side * 7, v, y0 + 1, 2, 0);
        }
        // two colossal hellhounds lying before the stair, watching the plaza
        for (int side = -1; side <= 1; side += 2) hound(f, s, side * 33, -112);
        // the Grand Stair: two flights and a landing, split by a gilded spine, braziers on its newels
        if (touches(f, -24, -106, 24, -89)) {
            for (int k = 0; k < 10; k++) {
                int v = k < 5 ? -105 + k : -100 + k, y = y0 + 1 + k;
                f.box(-17, y0, v, 17, y - 1, v, Draw.of(RNB));
                for (int u = -16; u <= 16; u++) f.set(u, y, v, f.stair(NSTAIR, 2, false));
                f.box(-16, y + 1, v, 16, y + 6, v, Draw.AIR);
                f.box(-17, y0, v, -17, y + 1, v, Draw.of(GOLD));
                f.box(17, y0, v, 17, y + 1, v, Draw.of(GOLD));
                f.box(0, y0, v, 0, y + 1, v, Draw.of(GOLD));
            }
            f.box(-17, y0, -100, 17, y0 + 5, -96, Draw.of(RNB));
            f.box(-16, y0 + 5, -100, 16, y0 + 5, -96, (x, y, z) -> Math.floorMod(lu(f, x, z), 4) == 0 ? GOLD : RNB);
            f.box(-16, y0 + 6, -100, 16, y0 + 10, -96, Draw.AIR);
            f.box(-17, y0 + 6, -100, -17, y0 + 6, -96, Draw.of(GOLD));
            f.box(17, y0 + 6, -100, 17, y0 + 6, -96, Draw.of(GOLD));
            f.box(0, y0 + 6, -100, 0, y0 + 6, -96, Draw.of(GOLD));
            for (int u : new int[]{-17, 0, 17}) { brazier(f, u, -105, y0 + 2, 1, 0); brazier(f, u, -98, y0 + 7, 1, 0); }
            f.box(-17, y0 + 1, -91, 17, y0 + Y1, -91, Draw.of(RNB));
            for (int u = -16; u <= 16; u++) f.set(u, y0 + Y1, -91, f.stair(NSTAIR, 2, false));
            f.box(-16, y0 + Y1 + 1, -91, 16, y0 + Y1 + 6, -90, Draw.AIR);
        }
    }

    /** A colossal hellhound of black stone lying with its head raised, eyes of fire. */
    private void hound(Draw.Frame f, Colossi.Site s, int u, int v) {
        int y0 = s.y;
        if (!touches(f, u - 8, v - 9, u + 8, v + 17)) return;
        f.box(u - 7, y0, v - 6, u + 7, y0 + 1, v + 11, Draw.of(GOLD));                    // the plinth
        f.box(u - 4, y0 + 2, v - 1, u + 4, y0 + 7, v + 10, Draw.of(BLACK));               // the body
        f.box(u - 3, y0 + 8, v, u + 3, y0 + 8, v + 9, Draw.of(BLACK));
        for (int side = -1; side <= 1; side += 2) f.box(u + side * 3, y0 + 2, v - 5, u + side * 2, y0 + 3, v, Draw.of(BLACK));   // forepaws
        f.box(u - 3, y0 + 7, v - 4, u + 3, y0 + 13, v + 1, Draw.of(BLACK));               // the head, raised
        f.box(u - 2, y0 + 7, v - 7, u + 2, y0 + 10, v - 5, Draw.of(BLACK));               // the muzzle
        f.box(u - 2, y0 + 7, v - 7, u + 2, y0 + 7, v - 5, Draw.of(QUARTZ));               // fangs
        f.box(u - 1, y0 + 8, v - 7, u + 1, y0 + 8, v - 7, Draw.of(MAGMA));               // a glowing maw
        f.set(u - 2, y0 + 11, v - 4, RACK); f.set(u - 2, y0 + 12, v - 4, FIRE);           // eyes of fire
        f.set(u + 2, y0 + 11, v - 4, RACK); f.set(u + 2, y0 + 12, v - 4, FIRE);
        f.box(u - 3, y0 + 14, v - 2, u - 2, y0 + 15, v - 1, Draw.of(BLACK));             // ears
        f.box(u + 2, y0 + 14, v - 2, u + 3, y0 + 15, v - 1, Draw.of(BLACK));
        f.box(u - 4, y0 + 8, v + 1, u + 4, y0 + 8, v + 2, Draw.of(GOLD));                // a gold collar
        f.line(u, y0 + 4, v + 11, u + 3, y0 + 2, v + 15, 0.8, Draw.of(BLACK));          // the tail
    }

    // ---- the Gate of Embers (the propylaea) --------------------------------------------------------------------------------------
    private void gate(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, t = y0 + Y1;
        if (!touches(f, -26, -92, 26, -68)) return;
        f.box(-20, t + 1, -82, 20, t + 14, -70, (x, y, z) -> Math.floorMod(y - y0, 5) == 0 ? GOLD : RNB);
        f.box(-21, t + 15, -89, 21, t + 15, -69, Draw.of(GOLD));
        f.box(-20, t + 15, -88, 20, t + 15, -70, Draw.of(RNB));
        for (int u = -21; u <= 21; u++) for (int v : new int[]{-89, -69}) if (Math.floorMod(u, 3) != 1) f.set(u, t + 16, v, RNB);
        for (int v = -89; v <= -69; v++) for (int u : new int[]{-21, 21}) if (Math.floorMod(v, 3) != 1) f.set(u, t + 16, v, RNB);
        // the portico: four columns under the roof's front
        for (int u : new int[]{-16, -7, 7, 16}) column(f, u, -87, t + 1, t + 14, 1);
        f.box(-20, t, -89, 20, t, -83, Draw.of(QUARTZ));
        // the passage with its arrow slits, a stepped arch over it
        f.box(-3, t + 1, -89, 3, t + 9, -69, Draw.AIR);
        f.box(-2, t + 10, -89, 2, t + 10, -69, Draw.AIR);
        f.box(-1, t + 11, -89, 1, t + 11, -69, Draw.AIR);
        f.box(-3, t, -89, 3, t, -69, (x, y, z) -> Math.floorMod(lv(f, x, z), 2) == 0 ? GOLD : RNB);
        for (int v = -80; v <= -72; v += 2) { f.set(-4, t + 2, v, BLACK); f.set(4, t + 2, v, BLACK); }
        f.set(0, t + 12, -76, GLOW);
        f.wallSign(0, t + 12, -83, Draw.NORTH, "THE PALACE OF", "THE BURNING", "THRONE", "Kneel or burn");
        // its guard rooms, a ladder to the roof
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 5, t + 1, -80, side * 19, t + 7, -72, Draw.AIR);
            f.box(side * 4, t + 1, -77, side * 4, t + 3, -75, Draw.AIR);
            f.set(side * 12, t + 8, -76, GLOW);
            f.chest(side * 19, t + 1, -79, side < 0 ? Draw.EAST : Draw.WEST, side < 0 ? "jaspr:colossus/palace" : "jaspr:colossus/palace_rich");
            f.box(side * 6, t + 1, -72, side * 9, t + 1, -72, Draw.of(SLAB_TOP));
        }
        for (int y = t + 1; y <= t + 15; y++) f.set(-18, y, -72, b(65, f.facing(Draw.NORTH)));
        // flame braziers flanking the portico, and two small towers on the roof
        for (int side = -1; side <= 1; side += 2) {
            brazier(f, side * 24, -88, t + 1, 7, 1);
            f.box(side * 18 - 2, t + 16, -88, side * 18 + 2, t + 22, -86, Draw.of(RNB));
            f.box(side * 18 - 2, t + 23, -88, side * 18 + 2, t + 23, -86, Draw.of(GOLD));
            brazier(f, side * 18, -87, t + 24, 0, 0);
        }
    }

    // ---- the Court of Fire --------------------------------------------------------------------------------------------------------
    private void court(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, t = y0 + Y1;
        if (!touches(f, -49, -71, 49, -17)) return;
        // the enclosure and the colonnade (a roofed walk all round)
        f.walls(-46, t + 1, -70, 46, t + 10, -18, (x, y, z) -> y == t + 10 ? GOLD : RNB);
        f.box(-45, t + 9, -69, 45, t + 9, -19, Draw.of(RNB));
        f.box(-38, t + 9, -62, 38, t + 9, -26, Draw.AIR);
        f.box(-45, t + 1, -69, 45, t + 8, -19, Draw.AIR);
        f.box(-45, t, -69, 45, t, -19, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            if (Math.abs(u) > 39 || v < -63 || v > -25) return Math.floorMod(u + v, 2) == 0 ? NB : RNB;
            if (Math.abs(u) <= 3) return Math.floorMod(v, 2) == 0 ? GOLD : ORANGE;
            return Math.floorMod(u, 7) == 0 || Math.floorMod(v, 7) == 0 ? GOLD : RNB;
        });
        for (int u = -39; u <= 39; u += 6) for (int v : new int[]{-63, -25}) if (Math.abs(u) > 4) column(f, u, v, t + 1, t + 8, 0);
        for (int v = -57; v <= -31; v += 6) for (int u : new int[]{-39, 39}) column(f, u, v, t + 1, t + 8, 0);
        f.box(-3, t + 1, -70, 3, t + 9, -70, Draw.AIR);                 // from the gate
        f.box(-12, t + 1, -18, 12, t + 9, -18, Draw.AIR);               // to the inner stair
        f.box(-48, t + 1, -55, -46, t + 4, -53, Draw.AIR);              // the west door, to the barracks' alley
        f.box(46, t + 1, -55, 48, t + 4, -53, Draw.AIR);                // the east door, to the kennel's alley
        // the altar of fire
        int[] half = {6, 5, 4, 3};
        for (int k = 0; k < 4; k++) f.box(-half[k], t + 1 + k, -44 - half[k], half[k], t + 1 + k, -44 + half[k], Draw.of(k == 3 ? RACK : k == 2 ? GOLD : k == 1 ? NB : RNB));
        f.box(-3, t + 5, -47, 3, t + 5, -41, Draw.of(FIRE));
        for (int a = -1; a <= 1; a += 2) for (int c = -1; c <= 1; c += 2) { f.set(a * 6, t + 2, -44 + c * 6, GOLD); f.set(a * 6, t + 3, -44 + c * 6, RACK); f.set(a * 6, t + 4, -44 + c * 6, FIRE); }
        // fire pits, lava channels with fountains, braziers
        for (int a = -1; a <= 1; a += 2) for (int v : new int[]{-56, -32}) {
            int u = a * 22;
            f.box(u - 2, t + 1, v - 2, u + 2, t + 1, v + 2, Draw.of(GOLD));
            f.box(u - 1, t, v - 1, u + 1, t, v + 1, Draw.of(RACK));
            f.box(u - 1, t + 1, v - 1, u + 1, t + 1, v + 1, Draw.of(FIRE));
        }
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 31, t - 1, -60, side * 30, t - 1, -28, Draw.of(NB));
            f.box(side * 31, t, -60, side * 30, t, -28, Draw.of(LAVA));
            fountain(f, side * 31, -52, t, 5);
            fountain(f, side * 31, -36, t, 5);
        }
        for (int v = -66; v <= -22; v += 11) for (int side = -1; side <= 1; side += 2) brazier(f, side * 9, v, t + 1, 3, 0);
        // the flame vents: magma in the floor of the processional way
        for (int v = -67; v <= -58; v++) for (int u = -3; u <= 3; u += 2) f.set(u, t, v, MAGMA);
        for (int v = -30; v <= -21; v++) for (int u = -3; u <= 3; u += 2) f.set(u, t, v, MAGMA);
        for (int a = -1; a <= 1; a += 2) for (int v = -47; v <= -41; v += 2) for (int u = a * 22 - 3; u <= a * 22 + 3; u += 2) f.set(u, t, v, MAGMA);
        f.chest(-45, t + 1, -69, Draw.SOUTH, "jaspr:colossus/palace");
        f.chest(45, t + 1, -19, Draw.NORTH, "jaspr:colossus/palace_rich");
        f.spawner(-36, t + 1, -66, "flame_adept");
        f.wallSign(6, t + 4, -69, Draw.SOUTH, "THE COURT OF", "FIRE", "The vents breathe", "on the King's way");
    }

    // ---- the barracks of the Royal Guard (west) and the royal kennel (east) on the lower terrace --------------------------------------
    private void barracks(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, t = y0 + Y1;
        if (!touches(f, -96, -88, -48, -18)) return;
        f.box(-90, t + 1, -86, -50, t + 12, -52, (x, y, z) -> y == t + 12 ? GOLD : Math.floorMod(y - y0, 5) == 0 ? ORANGE : RNB);
        f.box(-89, t + 1, -85, -51, t + 11, -53, Draw.AIR);
        f.box(-89, t, -85, -51, t, -53, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? NB : RNB);
        roof(f, -91, -87, -49, -51, t + 13, 7);
        brazier(f, -70, -69, t + 20, 1, 1);
        f.box(-50, t + 1, -55, -50, t + 4, -53, Draw.AIR);                   // its door to the alley
        f.box(-74, t + 1, -52, -70, t + 5, -52, Draw.AIR);                   // and to the yard
        for (int u = -88; u <= -54; u += 4) for (int v : new int[]{-84, -54}) {
            f.set(u, t + 1, v, SLAB_TOP); f.set(u + 1, t + 1, v, SLAB_TOP);
            f.set(u, t + 2, v, CARPET_R); f.set(u + 1, t + 2, v, CARPET_R);
        }
        f.box(-84, t + 1, -71, -58, t + 1, -67, Draw.of(FENCE));
        f.box(-84, t + 2, -71, -58, t + 2, -67, Draw.of(SLAB_TOP));
        for (int u = -86; u <= -54; u += 8) f.set(u, t + 8, -69, GLOW);
        for (int v = -84; v <= -54; v += 3) { f.set(-89, t + 2, v, BARS); f.set(-89, t + 3, v, BARS); }
        f.chest(-89, t + 1, -70, Draw.EAST, "jaspr:colossus/palace");
        f.chest(-60, t + 1, -85, Draw.SOUTH, "jaspr:colossus/palace");
        f.chest(-84, t + 1, -53, Draw.NORTH, "jaspr:colossus/palace_rich");
        f.spawner(-72, t + 1, -60, "royal_guard");
        f.wallSign(-51, t + 3, -57, Draw.WEST, "THE BARRACKS", "of the Royal", "Guard", "");
        // the training yard: low walls, dummies with skull heads
        f.walls(-90, t + 1, -48, -50, t + 3, -20, Draw.of(RNB));
        f.box(-90, t + 3, -48, -50, t + 3, -48, Draw.of(GOLD));
        f.box(-74, t + 1, -48, -70, t + 3, -48, Draw.AIR);
        f.box(-50, t + 1, -36, -50, t + 3, -32, Draw.AIR);
        for (int u = -84; u <= -56; u += 7) for (int v = -42; v <= -26; v += 8) {
            f.box(u, t + 1, v, u, t + 2, v, Draw.of(FENCE));
            f.set(u - 1, t + 2, v, FENCE); f.set(u + 1, t + 2, v, FENCE);
            f.skull(u, t + 3, v, 0, 8);
        }
    }

    private void kennel(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, t = y0 + Y1;
        if (!touches(f, 48, -88, 96, -18)) return;
        f.box(50, t + 1, -86, 90, t + 12, -52, (x, y, z) -> y == t + 12 ? GOLD : Math.floorMod(y - y0, 5) == 0 ? ORANGE : RNB);
        f.box(51, t + 1, -85, 89, t + 11, -53, Draw.AIR);
        f.box(51, t, -85, 89, t, -53, (x, y, z) -> Math.floorMod(x * 3 + z, 5) == 0 ? BONE : SOUL);
        roof(f, 49, -87, 91, -51, t + 13, 7);
        brazier(f, 70, -69, t + 20, 1, 1);
        f.box(50, t + 1, -55, 50, t + 4, -53, Draw.AIR);
        f.box(70, t + 1, -52, 74, t + 5, -52, Draw.AIR);
        // the pens: cages of iron bars along both long walls
        for (int u = 52; u <= 82; u += 6) for (int side = 0; side <= 1; side++) {
            int va = side == 0 ? -85 : -59, vb = side == 0 ? -79 : -53;
            f.box(u, t + 1, va, u + 5, t + 4, vb, Draw.of(BARS));
            f.box(u + 1, t + 1, va + (side == 0 ? 0 : 1), u + 4, t + 4, vb - (side == 0 ? 1 : 0), Draw.AIR);
            f.box(u + 2, t + 1, side == 0 ? vb : va, u + 3, t + 3, side == 0 ? vb : va, Draw.AIR);
            f.set(u + 1, t + 1, side == 0 ? va + 1 : vb - 1, BONE);
            f.set(u + 4, t + 1, side == 0 ? va + 1 : vb - 1, BONE);
        }
        for (int u = 54; u <= 92; u += 8) f.set(u, t + 11, -69, GLOW);
        f.box(82, t, -72, 86, t, -66, Draw.of(MAGMA));
        f.chest(89, t + 1, -70, Draw.WEST, "jaspr:colossus/palace");
        f.chest(60, t + 1, -60, Draw.NORTH, "jaspr:colossus/palace");
        f.spawner(76, t + 1, -69, "hellhound");
        f.wallSign(51, t + 3, -57, Draw.EAST, "THE ROYAL", "KENNEL", "His hounds are", "never fed");
        // the hounds' yard: bone heaps, ash, a pit of embers
        f.walls(50, t + 1, -48, 90, t + 3, -20, Draw.of(RNB));
        f.box(50, t + 3, -48, 90, t + 3, -48, Draw.of(GOLD));
        f.box(70, t + 1, -48, 74, t + 3, -48, Draw.AIR);
        f.box(50, t + 1, -36, 50, t + 3, -32, Draw.AIR);
        for (int i = 0; i < 6; i++) {
            int u = 56 + i * 6, v = -42 + (i % 3) * 8;
            f.set(u, t + 1, v, BONE); f.set(u + 1, t + 1, v, BONE); f.set(u, t + 2, v, BONE);
        }
        f.box(70, t, -36, 76, t, -30, Draw.of(SOUL));
        f.box(72, t, -34, 74, t, -32, Draw.of(RACK));
        f.box(72, t + 1, -34, 74, t + 1, -32, Draw.of(FIRE));
    }

    // ---- the Inner Stair to the upper terrace, between fountains of lava; the colonnade along its front -------------------------
    private void innerStair(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, t1 = y0 + Y1, t2 = y0 + Y2;
        if (!touches(f, -T2U, -19, T2U, -6)) return;
        for (int k = 0; k < Y2 - Y1; k++) {
            int v = -17 + k, y = t1 + 1 + k;
            f.box(-13, t1 + 1, v, 13, y - 1, v, Draw.of(RNB));
            for (int u = -12; u <= 12; u++) f.set(u, y, v, f.stair(NSTAIR, 2, false));
            f.box(-12, y + 1, v, 12, y + 6, v, Draw.AIR);
            f.box(-13, t1 + 1, v, -13, y + 1, v, Draw.of(GOLD));
            f.box(13, t1 + 1, v, 13, y + 1, v, Draw.of(GOLD));
        }
        for (int side = -1; side <= 1; side += 2) fountain(f, side * 16, -14, t1 + 1, Y2 - Y1 + 6);
        // the colonnade along the upper terrace's front
        for (int u = -76; u <= 76; u += 4) {
            if (Math.abs(u) <= 14 || (u >= -58 && u <= -50) || (u >= 42 && u <= 50)) continue;
            f.box(u, t2 + 1, -9, u, t2 + 9, -9, Draw.of(RNB));
            f.set(u, t2 + 1, -9, QUARTZ);
            f.set(u, t2 + 9, -9, GOLD);
        }
        f.box(-77, t2 + 10, -10, 77, t2 + 10, -8, (x, y, z) -> lv(f, x, z) == -9 ? RNB : GOLD);
        f.box(-14, t2 + 10, -10, 14, t2 + 10, -8, Draw.AIR);
        for (int u = -76; u <= 76; u += 8) if (Math.abs(u) > 14) { f.set(u, t2 + 11, -9, RACK); f.set(u, t2 + 12, -9, FIRE); }
    }

    // ---- the Hall of the Burning Throne -------------------------------------------------------------------------------------------
    private void throneHall(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + Y2, top = y0 + HTOP;
        if (!touches(f, -37, -9, 37, HV1 + 9)) return;
        // the forecourt: the Court of the Sun, its mosaic and four great braziers
        f.box(-30, fl, -8, 30, fl, 3, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            double r = Math.sqrt(u * u + (v + 2) * (v + 2) * 1.0);
            return r < 2 ? GLAZED_Y : r < 5 ? (Math.floorMod(u + v, 2) == 0 ? GOLD : ORANGE) : Math.abs(u) <= 3 ? GOLD : RNB;
        });
        for (int u : new int[]{-34, -16, 16, 34}) brazier(f, u, -3, fl + 1, 4, 1);
        // the shell: walls banded in gold, pilasters, tall windows of fire-coloured glass
        f.box(-HU, fl, HV0, HU, top, HV1, (x, y, z) -> Math.floorMod(y - y0, 6) == 0 ? GOLD : RNB);
        f.box(-HU + 2, fl + 1, HV0 + 2, HU - 2, top - 1, HV1 - 2, Draw.AIR);
        for (int v = HV0 + 4; v <= HV1 - 4; v += 8) for (int side = -1; side <= 1; side += 2) {
            f.box(side * (HU + 1), fl + 1, v - 1, side * (HU + 1), top - 2, v + 1, Draw.of(NB));
            f.set(side * (HU + 1), top - 1, v, GOLD);
            if (v + 4 < HV1 - 2) f.box(side * HU, fl + 8, v + 3, side * (HU - 1), fl + 34, v + 5, (x, y, z) -> Math.floorMod(y, 7) == 0 ? GOLD : GLASS_O);
        }
        // the floor: red and black, a gold way to the throne; rivers of lava along the walls
        f.box(-HU + 2, fl, HV0 + 2, HU - 2, fl, HV1 - 2, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            if (Math.abs(u) <= 2 && v < 24) return Math.abs(u) == 2 ? GOLD : CRIMSON;
            return Math.floorMod(u + v, 2) == 0 ? NB : RNB;
        });
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 19, fl - 1, HV0 + 4, side * 20, fl - 1, HV1 - 4, Draw.of(NB));
            f.box(side * 19, fl, HV0 + 4, side * 20, fl, HV1 - 4, Draw.of(LAVA));
            f.box(side * 18, fl + 1, HV0 + 4, side * 18, fl + 1, HV1 - 4, Draw.of(GOLD));
            f.box(side * 21, fl + 1, HV0 + 4, side * 21, fl + 1, HV1 - 4, Draw.of(GOLD));
        }
        // the colossal columns (cover from his lightning), and two by the colossus
        for (int v : new int[]{12, 20, 28}) for (int side = -1; side <= 1; side += 2) {
            column(f, side * 13, v, fl + 1, top - 1, 1);
            f.box(side * 13 - 1, fl + 14, v - 1, side * 13 + 1, fl + 14, v + 1, Draw.of(GLOW));
            f.box(side * 13 - 1, fl + 30, v - 1, side * 13 + 1, fl + 30, v + 1, Draw.of(GLOW));
        }
        for (int v : new int[]{40, 52}) for (int side = -1; side <= 1; side += 2) column(f, side * 17, v, fl + 1, top - 1, 1);
        // the ceiling: gold coffers, and two chandeliers of glowstone
        f.box(-HU + 2, top, HV0 + 2, HU - 2, top, HV1 - 2, (x, y, z) -> Math.floorMod(lu(f, x, z), 6) == 0 || Math.floorMod(lv(f, x, z), 6) == 0 ? GOLD : NB);
        for (int v : new int[]{14, 26}) {
            f.box(0, top - 9, v, 0, top - 1, v, Draw.of(BARS));
            f.box(-1, top - 11, v - 1, 1, top - 10, v + 1, Draw.of(GLOW));
        }
        // the portal: a gilded, stepped arch twenty-eight high, through which the colossus watches the courts
        f.box(-8, fl + 1, HV0, 8, fl + 28, HV0 + 1, Draw.AIR);
        for (int k = 1; k <= 7; k++) f.box(-8 + k, fl + 28 + k, HV0, 8 - k, fl + 28 + k, HV0 + 1, Draw.AIR);
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 9, fl + 1, HV0 - 1, side * 10, fl + 30, HV0 - 1, Draw.of(GOLD));
            f.box(side * 9, fl + 1, HV0 - 1, side * 10, fl + 1, HV0 - 1, Draw.of(QUARTZ));
            f.wallSign(side * 12, fl + 3, HV0 - 1, Draw.NORTH, side < 0 ? "THE BURNING" : "Lightning finds", side < 0 ? "THRONE" : "all who stand", side < 0 ? "Bow before the" : "in his sight.", side < 0 ? "King of Ash" : "Keep to pillars");
        }
        for (int k = 0; k <= 8; k++) f.set(-8 + k, fl + 29 + k, HV0 - 1, GOLD);
        for (int k = -4; k <= 4; k++) f.box(-(4 - Math.abs(k)), fl + 42 + k, HV0 - 1, 4 - Math.abs(k), fl + 42 + k, HV0 - 1, Draw.of(Math.abs(k) <= 1 ? GLAZED_O : GLAZED_Y));
        f.set(0, fl + 42, HV0 - 2, GLOW);
        for (int k = 0; k <= 8; k++) f.set(8 - k, fl + 29 + k, HV0 - 1, GOLD);
        // the King's own throne on its dais before the colossus's feet
        int sb = fl + 1;
        f.box(-6, sb, 24, 6, sb, 33, Draw.of(RNB));
        f.box(-5, sb + 1, 26, 5, sb + 1, 33, Draw.of(GOLD));
        f.box(-4, sb + 2, 28, 4, sb + 2, 33, Draw.of(BLACK));
        f.set(0, sb + 3, 32, f.stair(NSTAIR, 2, false));
        f.box(-1, sb + 3, 33, 1, sb + 7, 33, Draw.of(RNB));
        f.box(-1, sb + 8, 33, 1, sb + 8, 33, Draw.of(GOLD));
        f.set(0, sb + 9, 33, GOLD);
        f.set(-1, sb + 3, 32, GOLD); f.set(1, sb + 3, 32, GOLD);
        for (int side = -1; side <= 1; side += 2) { f.set(side * 5, sb + 2, 28, RACK); f.set(side * 5, sb + 3, 28, FIRE); }
        f.point(0, sb + 3, 29, "lord:" + s.kind.lord);
        // the roof: a terrace with a parapet; four watch towers at the hall's corners (the north-east one holds a stair)
        f.box(-HU, top + 1, HV0, HU, top + 1, HV1, Draw.of(RNB));
        for (int u = -HU; u <= HU; u++) for (int v : new int[]{HV0, HV1}) if (Math.floorMod(u, 3) != 1) f.set(u, top + 2, v, GOLD);
        for (int v = HV0; v <= HV1; v++) for (int u : new int[]{-HU, HU}) if (Math.floorMod(v, 3) != 1) f.set(u, top + 2, v, GOLD);
        f.box(-HU + 1, top + 2, HV0 + 1, HU - 1, top + 6, HV1 - 1, Draw.AIR);
        int lt = Math.min(top + 9, s.ceilAt(f.x(0, 35), f.z(0, 35)) - 4);
        f.box(-12, top + 2, 14, 12, lt, 56, (x, y, z) -> Math.floorMod(y - y0, 4) == 0 ? GOLD : RNB);
        for (int v = 17; v <= 53; v += 4) for (int side = -1; side <= 1; side += 2) f.box(side * 12, top + 4, v, side * 12, lt - 2, v + 1, Draw.of(GLASS_O));
        roof(f, -13, 13, 13, 57, lt + 1, 4);
        brazier(f, 0, 35, lt + 5, 2, 2);
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = 0; cv <= 1; cv++) {
            int u = cu * HU, v = cv == 0 ? HV0 : HV1;
            int wxi = f.x(u, v), wzi = f.z(u, v), cap = Math.min(top + 14, s.ceilAt(wxi, wzi) - 4);
            f.cyl(u, v, 3.8, fl + 1, cap, (x, y, z) -> Math.floorMod(y - y0, 6) == 0 ? GOLD : RNB);
            f.ring(u, v, 4.5, 1.0, cap + 1, cap + 1, Draw.of(GOLD));
            f.disk(u, v, 3.8, cap, Draw.of(RNB));
            brazier(f, u, v, cap + 1, 0, 1);
        }
        // a ladder in the north-east corner up through the ceiling to the roof
        for (int y = fl + 1; y <= top + 1; y++) f.set(HU - 8, y, HV0 + 2, b(65, f.facing(Draw.SOUTH)));
        f.wallSign(HU - 10, fl + 3, HV0 + 2, Draw.SOUTH, "", "To the roof", "and its towers", "");
    }

    // ---- the colossus of the Burning King: seated on his throne behind the dais, sword planted, crowned in fire ---------------------
    private void statue(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, sb = y0 + Y2 + 1, fb = sb + 2;
        if (!touches(f, -16, 36, 16, 65)) return;
        f.box(-15, sb, 38, 15, sb + 1, 63, Draw.of(BLACK));                              // the plinth
        f.box(-15, sb + 1, 38, 15, sb + 1, 38, Draw.of(GOLD));
        // the colossal throne: seat, back with a burning crest, armrests
        f.box(-13, fb, 46, 13, fb + 7, 63, (x, y, z) -> y == fb + 7 ? GOLD : OBSIDIAN);
        f.box(-13, fb + 8, 59, 13, fb + 34, 63, (x, y, z) -> {
            int u = lu(f, x, z);
            return Math.abs(u) == 13 || Math.floorMod(y - fb, 9) == 0 ? GOLD : NB;
        });
        f.box(-4, fb + 20, 58, 4, fb + 32, 58, (x, y, z) -> Math.floorMod(lu(f, x, z) + y, 3) == 0 ? GLAZED_O : GLAZED_Y);
        f.box(-12, fb + 35, 61, 12, fb + 35, 61, Draw.of(RACK));
        f.box(-12, fb + 36, 61, 12, fb + 36, 61, Draw.of(FIRE));
        for (int side = -1; side <= 1; side += 2) f.box(side * 10, fb + 8, 46, side * 13, fb + 12, 58, (x, y, z) -> y == fb + 12 ? GOLD : NB);
        // feet, shins, thighs and lap; the robe falling between the knees
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 3, fb, 39, side * 8, fb + 2, 42, Draw.of(BLACK_C));
            f.box(side * 3, fb, 39, side * 8, fb, 39, Draw.of(GOLD));
            f.box(side * 3, fb + 3, 42, side * 7, fb + 7, 45, Draw.of(BLACK_C));
            f.box(side * 2, fb + 8, 41, side * 7, fb + 11, 55, Draw.of(RED_C));
            f.box(side * 3, fb + 9, 41, side * 7, fb + 10, 41, Draw.of(GOLD));
        }
        f.box(-2, fb, 39, 2, fb, 42, Draw.of(BLACK));
        f.box(-1, fb + 8, 45, 1, fb + 11, 55, Draw.of(RED_C));
        f.box(-2, fb, 43, 2, fb + 11, 44, Draw.of(RED_C));
        f.box(-2, fb, 43, 2, fb, 44, Draw.of(GOLD));
        // the body: robed, a breastplate, a gold belt
        f.box(-8, fb + 12, 48, 8, fb + 24, 58, Draw.of(RED_C));
        f.box(-8, fb + 12, 48, 8, fb + 12, 58, Draw.of(GOLD));
        f.box(-6, fb + 14, 47, 6, fb + 23, 47, Draw.of(BLACK_C));
        f.box(-1, fb + 16, 46, 1, fb + 21, 46, Draw.of(GLAZED_O));
        // shoulders with spikes, arms on the armrests, the left hand resting, the right gripping the sword
        f.box(-12, fb + 24, 48, 12, fb + 26, 57, Draw.of(BLACK_C));
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 11, fb + 27, 51, side * 11, fb + 29, 53, Draw.of(BLACK_C));
            f.box(side * 9, fb + 27, 49, side * 9, fb + 28, 49, Draw.of(GOLD));
            f.box(side * 10, fb + 15, 50, side * 12, fb + 23, 55, Draw.of(RED_C));
            f.box(side * 10, fb + 13, 45, side * 12, fb + 14, 52, Draw.of(BLACK_C));
        }
        f.box(-12, fb + 13, 43, -10, fb + 15, 45, Draw.of(BLACK_C));
        f.box(10, fb + 14, 41, 12, fb + 17, 44, Draw.of(BLACK_C));
        f.box(11, fb, 42, 11, fb + 12, 43, Draw.of(QUARTZ));                              // the blade
        f.box(8, fb + 13, 42, 14, fb + 13, 43, Draw.of(GOLD));                             // its guard
        f.box(11, fb + 18, 42, 11, fb + 18, 43, Draw.of(YELLOW_C));                        // its pommel
        // the head: a crowned skull with eyes of fire
        f.box(-2, fb + 26, 51, 2, fb + 27, 55, Draw.of(BLACK_C));
        f.box(-4, fb + 28, 49, 4, fb + 35, 57, Draw.of(BLACK_C));
        f.box(-3, fb + 29, 49, 3, fb + 29, 49, Draw.of(BLACK_C));
        for (int u = -3; u <= 3; u++) f.set(u, fb + 30, 49, (u & 1) == 0 ? QUARTZ : BLACK_C);
        f.set(0, fb + 31, 49, 0);
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 2, fb + 32, 49, side * 3, fb + 32, 49, Draw.of(RACK));
            f.box(side * 2, fb + 33, 49, side * 3, fb + 33, 49, Draw.of(FIRE));
        }
        f.box(-5, fb + 36, 48, 5, fb + 37, 58, Draw.of(YELLOW_C));
        f.box(-4, fb + 36, 49, 4, fb + 37, 57, Draw.of(BLACK_C));
        int[][] spikes = {{-4, 48}, {0, 48}, {4, 48}, {-5, 53}, {5, 53}, {-4, 58}, {4, 58}};
        for (int[] q : spikes) {
            f.box(q[0], fb + 38, q[1], q[0], fb + 39, q[1], Draw.of(YELLOW_C));
            f.set(q[0], fb + 40, q[1], RACK);
            f.set(q[0], fb + 41, q[1], FIRE);
        }
        // the Treasury of Ash, then the Gilded Door under his feet, sealed until he falls, and the stair down to the Treasury of Ash
        int vf = y0 + Y1;
        f.box(-11, vf, 47, 11, y0 + Y2 - 1, 64, Draw.of(NB));
        f.box(-10, vf + 1, 48, 10, y0 + Y2 - 2, 63, Draw.AIR);
        f.box(-10, vf, 48, 10, vf, 63, (x, y, z) -> Math.floorMod(lu(f, x, z) + lv(f, x, z), 2) == 0 ? GOLD : RNB);
        for (int u = -8; u <= 8; u += 4) f.set(u, y0 + Y2 - 1, 56, GLOW);
        for (int u = -7; u <= 7; u += 14) for (int v = 53; v <= 59; v += 6) { f.set(u, vf + 1, v, YELLOW_C); f.set(u + 1, vf + 1, v, GOLD); f.set(u, vf + 2, v, GOLD); }
        for (int u : new int[]{-8, -3, 3, 8}) f.chest(u, vf + 1, 63, Draw.NORTH, "jaspr:colossus/palace_vault");
        f.chest(-10, vf + 1, 58, Draw.EAST, "jaspr:colossus/palace_rich");
        f.wallSign(10, vf + 3, 50, Draw.WEST, "THE TREASURY", "OF ASH", "", "");
        for (int k = 0; k <= 9; k++) {
            int v = 42 + k, y = sb - 1 - k;
            f.box(-1, y + 1, v, 1, Math.min(y + 3, sb + 1), v, Draw.AIR);
            for (int u = -1; u <= 1; u++) f.set(u, y, v, f.stair(NSTAIR, 3, false));
        }
        f.box(-1, sb, 39, 1, sb + 1, 41, Draw.AIR);
        f.box(-1, sb, 38, 1, sb + 1, 38, Draw.of(SEAL));
        f.wallSign(-3, sb + 1, 37, Draw.NORTH, "His treasury", "lies beneath", "his feet. It", "opens to none");
        f.wallSign(3, sb + 1, 37, Draw.NORTH, "while he", "still burns", "", "");
    }

    // ---- the Treasury Tower (east), whose floors give way under thieves --------------------------------------------------------------
    static final int TU = 46, TV = 4, TH = 10, FLOORS = 6, STOREY = 8;

    private void treasury(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + Y2, roof = fl + FLOORS * STOREY;
        if (!touches(f, TU - TH - 3, TV - TH - 3, TU + TH + 3, TV + TH + 3)) return;
        f.box(TU - TH, fl, TV - TH, TU + TH, roof, TV + TH, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            boolean corner = Math.abs(u - TU) >= TH - 1 && Math.abs(v - TV) >= TH - 1;
            return corner ? GOLD : Math.floorMod(y - fl, STOREY) == 0 ? ORANGE : RNB;
        });
        for (int k = 0; k < FLOORS; k++) {
            int yb = fl + k * STOREY;
            f.box(TU - TH + 1, yb + 1, TV - TH + 1, TU + TH - 1, yb + STOREY - 1, TV + TH - 1, Draw.AIR);
            f.box(TU - TH + 1, yb, TV - TH + 1, TU + TH - 1, yb, TV + TH - 1, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? GOLD : RNB);
            for (int q = 0; q < 4; q++) {
                int wu = q == 0 ? TU - TH : q == 1 ? TU + TH : TU, wv = q == 2 ? TV - TH : q == 3 ? TV + TH : TV;
                if (k == 0 && q == 2) continue;
                f.box(wu - (q >= 2 ? 1 : 0), yb + 3, wv - (q < 2 ? 1 : 0), wu + (q >= 2 ? 1 : 0), yb + 5, wv + (q < 2 ? 1 : 0), Draw.of(GLASS_O));
            }
            f.set(TU - 6, yb + STOREY - 1, TV - 6, GLOW); f.set(TU + 6, yb + STOREY - 1, TV + 6, GLOW);
            if (k > 0) {
                f.chest(TU - TH + 1, yb + 1, TV + 5, Draw.EAST, k >= 4 ? "jaspr:colossus/palace_rich" : "jaspr:colossus/palace");
                f.set(TU + TH - 2, yb + 1, TV - TH + 2, GOLD); f.set(TU + TH - 3, yb + 1, TV - TH + 2, YELLOW_C); f.set(TU + TH - 2, yb + 2, TV - TH + 2, GOLD);
            }
            // the floors that give way: orange tiles over the room below, its floor of magma beneath them
            if (k >= 2 && k <= 4) {
                int[] q = trap(k);
                f.box(q[0] - 1, yb, q[1] - 1, q[0] + 1, yb, q[1] + 1, Draw.of(ORANGE_C));
                f.box(q[0] - 1, yb - STOREY, q[1] - 1, q[0] + 1, yb - STOREY, q[1] + 1, Draw.of(MAGMA));
            }
        }
        f.chest(TU + TH - 1, fl + FLOORS * STOREY - STOREY + 1, TV - 3, Draw.WEST, "jaspr:colossus/palace_rich");
        // the stair: a spiral in its middle, a door at every floor
        spiral(f, TU, TV, fl + 1, roof, Draw.of(RNB));
        for (int k = 0; k < FLOORS; k++) spiralDoor(f, TU, TV, fl + k * STOREY, 1);
        // the door from the terrace, the sign
        f.box(TU - 1, fl + 1, TV - TH, TU + 1, fl + 4, TV - TH, Draw.AIR);
        f.box(TU - 2, fl + 5, TV - TH - 1, TU + 2, fl + 5, TV - TH - 1, Draw.of(GOLD));
        f.wallSign(TU + 3, fl + 3, TV - TH - 1, Draw.NORTH, "THE TREASURY", "TOWER", "Its floors give", "way to thieves");
        f.spawner(TU + 6, fl + 1, TV + 6, "pyre_warden");
        // battlements, corner turrets, a stepped roof of gold with a beacon of fire
        for (int u = TU - TH; u <= TU + TH; u++) for (int v : new int[]{TV - TH, TV + TH}) if (Math.floorMod(u, 2) == 0) f.set(u, roof + 1, v, GOLD);
        for (int v = TV - TH; v <= TV + TH; v++) for (int u : new int[]{TU - TH, TU + TH}) if (Math.floorMod(v, 2) == 0) f.set(u, roof + 1, v, GOLD);
        int cap = Math.min(roof + 9, s.ceilAt(f.x(TU, TV), f.z(TU, TV)) - 3);
        for (int k = 0; roof + 1 + k <= cap - 2 && 6 - k >= 1; k++) f.box(TU - 6 + k, roof + 1 + k, TV - 6 + k, TU + 6 - k, roof + 1 + k, TV + 6 - k, Draw.of(k % 2 == 0 ? GOLD : YELLOW_C));
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
            int u = TU + cu * TH, v = TV + cv * TH;
            f.cyl(u, v, 2, roof - 6, Math.min(roof + 5, cap), Draw.of(RNB));
            brazier(f, u, v, Math.min(roof + 6, cap + 1), 0, 0);
        }
        f.box(TU - 1, roof + 1, TV - 1, TU + 1, roof + 3, TV + 1, Draw.AIR);
    }

    static int[] trap(int k) { return new int[]{TU + (k % 2 == 0 ? -5 : 5), TV + (k == 3 ? -5 : 5)}; }

    // ---- the War Room (east) -----------------------------------------------------------------------------------------------------------
    private void warRoom(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + Y2;
        if (!touches(f, 31, 25, 79, 67)) return;
        f.box(32, fl, 26, 78, fl + 14, 66, (x, y, z) -> y == fl + 14 ? GOLD : Math.floorMod(y - y0, 6) == 0 ? ORANGE : RNB);
        f.box(33, fl + 1, 27, 77, fl + 13, 65, Draw.AIR);
        f.box(33, fl, 27, 77, fl, 65, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? NB : RNB);
        roof(f, 31, 25, 79, 67, fl + 15, 8);
        brazier(f, 55, 46, fl + 23, 1, 1);
        f.box(32, fl + 1, 44, 32, fl + 5, 48, Draw.AIR);
        f.box(52, fl + 1, 26, 56, fl + 5, 26, Draw.AIR);
        // the map of the Nether: a table of coloured clay, its armies marked in fire and bone
        f.box(44, fl + 1, 36, 66, fl + 1, 56, Draw.of(NB));
        f.box(45, fl + 2, 37, 65, fl + 2, 55, (x, y, z) -> {
            double q = Draw.fbm(x, z, 5, 7181);
            return q > 0.35 ? ORANGE_C : q > 0.05 ? CRIMSON : q > -0.25 ? b(159, 12) : BLACK;
        });
        f.set(50, fl + 3, 42, RACK); f.set(50, fl + 4, 42, FIRE);
        f.set(60, fl + 3, 50, RACK); f.set(60, fl + 4, 50, FIRE);
        f.set(56, fl + 3, 40, BONE); f.set(48, fl + 3, 52, BONE); f.set(62, fl + 3, 39, GOLD);
        for (int u = 45; u <= 65; u += 4) { f.set(u, fl + 1, 35, f.stair(NSTAIR, 3, false)); f.set(u, fl + 1, 57, f.stair(NSTAIR, 2, false)); }
        for (int v = 30; v <= 62; v += 4) { f.set(77, fl + 2, v, BARS); f.set(77, fl + 3, v, BARS); f.set(77, fl + 4, v, b(198, 1)); }
        for (int u = 40; u <= 72; u += 8) f.set(u, fl + 13, 46, GLOW);
        f.chest(77, fl + 1, 44, Draw.WEST, "jaspr:colossus/palace_rich");
        f.chest(34, fl + 1, 64, Draw.EAST, "jaspr:colossus/palace");
        f.wallSign(33, fl + 4, 50, Draw.EAST, "THE WAR ROOM", "Every war of", "the Nether was", "lost here first");
    }

    // ---- the Garden of Embers and the Queen's house (west) -----------------------------------------------------------------------------
    private void garden(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + Y2;
        if (!touches(f, -79, -10, -29, 89)) return;
        f.walls(-78, fl + 1, -6, -33, fl + 7, 88, (x, y, z) -> y == fl + 7 ? GOLD : RNB);
        f.box(-77, fl + 1, -5, -34, fl + 6, 87, Draw.AIR);
        f.box(-57, fl + 1, -6, -51, fl + 6, -6, Draw.AIR);                       // the gate from the Court of the Sun
        f.box(-33, fl + 1, 30, -33, fl + 5, 34, Draw.AIR);                       // the gate by the hall
        brazier(f, -59, -8, fl + 1, 3, 0); brazier(f, -49, -8, fl + 1, 3, 0);
        // paths of gold, soil of ash and wart, ember trees
        f.box(-60, fl, -5, -35, fl, 87, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            if (Math.abs(u + 54) <= 1 || Math.abs(v - 32) <= 1 || Math.abs(u + 46) <= 1) return GOLD;
            return Draw.rnd(x, 0, z, 3171) < 0.2 ? RACK : SOUL;
        });
        for (int u = -59; u <= -36; u += 2) for (int v = -4; v <= 86; v += 2) {
            if (Math.abs(u + 54) <= 2 || Math.abs(v - 32) <= 2 || Math.abs(u + 46) <= 2) continue;
            if (Draw.rnd(u, 5, v, 3172) < 0.35) f.set(u, fl + 1, v, WART_CROP);
        }
        int[][] trees = {{-39, 4}, {-39, 20}, {-58, 12}, {-58, 56}, {-39, 50}, {-39, 76}, {-58, 80}, {-50, 66}};
        for (int[] q : trees) {
            f.box(q[0], fl + 1, q[1], q[0], fl + 6, q[1], Draw.of(NB));
            f.box(q[0] - 2, fl + 7, q[1] - 2, q[0] + 2, fl + 8, q[1] + 2, Draw.of(WART));
            f.box(q[0] - 1, fl + 9, q[1] - 1, q[0] + 1, fl + 9, q[1] + 1, Draw.of(WART));
            f.set(q[0] + 2, fl + 6, q[1], GLOW); f.set(q[0] - 2, fl + 6, q[1] + 1, GLOW);
        }
        // the magma pond with its fountain, and rills of lava north and south
        f.disk(-46, 40, 6.5, fl, Draw.of(OBSIDIAN));
        f.disk(-46, 40, 5.3, fl, Draw.of(MAGMA));
        f.disk(-46, 40, 6.5, fl + 1, Draw.of(GOLD));
        f.disk(-46, 40, 5.3, fl + 1, Draw.AIR);
        fountain(f, -46, 40, fl, 6);
        for (int side = -1; side <= 1; side += 2) {
            int va = side < 0 ? 0 : 47, vb = side < 0 ? 33 : 84;
            f.box(-47, fl - 1, va, -47, fl - 1, vb, Draw.of(NB));
            f.box(-47, fl, va, -47, fl, vb, Draw.of(LAVA));
        }
        f.box(-48, fl, 30, -45, fl, 34, Draw.of(GOLD));
        f.spawner(-40, fl + 1, 44, "magma_cube");
        // the pavilion at the garden's end
        f.box(-52, fl, 72, -40, fl, 84, Draw.of(QUARTZ));
        for (int[] q : new int[][]{{-52, 72}, {-40, 72}, {-52, 84}, {-40, 84}}) column(f, q[0], q[1], fl + 1, fl + 7, 0);
        for (int k = 0; k < 4; k++) f.box(-53 + k, fl + 8 + k, 71 + k, -39 - k, fl + 8 + k, 85 - k, Draw.of(k % 2 == 0 ? GOLD : RNB));
        f.set(-46, fl + 12, 78, GOLD); f.set(-46, fl + 13, 78, RACK); f.set(-46, fl + 14, 78, FIRE);
        f.chest(-46, fl + 1, 82, Draw.NORTH, "jaspr:colossus/palace_rich");
        // the Queen's house along the west wall: a colonnaded front, two floors of rooms
        f.box(-77, fl, -4, -62, fl + 15, 86, (x, y, z) -> y == fl + 15 ? GOLD : Math.floorMod(y - y0, 5) == 0 ? ORANGE : RNB);
        for (int r = 0; r < 4; r++) {
            int va = r == 0 ? -3 : 22 * r - 2, vb = r == 3 ? 85 : 22 * r + 18, vc = (va + vb) / 2;
            f.box(-76, fl + 1, va, -63, fl + 7, vb, Draw.AIR);
            f.box(-76, fl + 9, va, -63, fl + 14, vb, Draw.AIR);
            f.box(-76, fl, va, -63, fl, vb, Draw.of(CRIMSON));
            f.box(-62, fl + 1, vc - 1, -62, fl + 4, vc + 1, Draw.AIR);
            f.box(-75, fl + 1, vc - 2, -72, fl + 1, vc + 2, Draw.of(QUARTZ));
            f.box(-75, fl + 2, vc - 2, -72, fl + 2, vc + 2, Draw.of(CARPET_R));
            f.set(-69, fl + 8, vc, GLOW); f.set(-69, fl + 15, vc, GLOW);
            if (r % 2 == 0) f.chest(-76, fl + 1, vb, Draw.EAST, r == 0 ? "jaspr:colossus/palace" : "jaspr:colossus/palace_rich");
            for (int y = fl + 1; y <= fl + 8; y++) f.set(-63, y, va + 1, b(65, f.facing(Draw.WEST)));
        }
        for (int v = -2; v <= 84; v += 4) column(f, -59, v, fl + 1, fl + 14, 0);
        f.box(-61, fl + 15, -4, -59, fl + 15, 86, Draw.of(GOLD));
        roof(f, -78, -5, -61, 87, fl + 16, 5);
        f.wallSign(-61, fl + 6, 30, Draw.EAST, "THE QUEEN'S", "HOUSE", "She went into", "the fire first");
        f.wallSign(-60, fl + 3, -5, Draw.SOUTH, "THE GARDEN OF", "EMBERS", "", "");
    }

    // ---- the King's apartments (behind the hall) -----------------------------------------------------------------------------------------
    private void apartments(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, fl = y0 + Y2;
        if (!touches(f, -27, 69, 27, 91)) return;
        f.box(-26, fl, 70, 26, fl + 12, 90, (x, y, z) -> y == fl + 12 ? GOLD : Math.floorMod(y - y0, 6) == 0 ? ORANGE : RNB);
        roof(f, -27, 69, 27, 91, fl + 13, 6);
        f.box(-25, fl + 1, 71, 25, fl + 11, 89, Draw.AIR);
        f.box(-25, fl, 71, 25, fl, 89, (x, y, z) -> Math.floorMod(x + z, 2) == 0 ? CRIMSON : RNB);
        f.box(-26, fl + 1, 78, -26, fl + 4, 82, Draw.AIR);
        f.box(26, fl + 1, 78, 26, fl + 4, 82, Draw.AIR);
        f.box(-4, fl + 1, 71, 4, fl + 11, 71, Draw.of(RNB));
        f.box(-4, fl + 1, 71, 4, fl + 11, 89, Draw.of(RNB));
        f.box(-3, fl + 1, 72, 3, fl + 10, 88, Draw.AIR);
        f.box(-4, fl + 1, 79, -4, fl + 4, 81, Draw.AIR);
        f.box(4, fl + 1, 79, 4, fl + 4, 81, Draw.AIR);
        // the King's bed of state (crimson on quartz), his wardrobe of black
        f.box(-20, fl + 1, 82, -12, fl + 1, 88, Draw.of(QUARTZ));
        f.box(-20, fl + 2, 82, -12, fl + 2, 88, Draw.of(CARPET_R));
        f.box(-20, fl + 2, 88, -12, fl + 5, 88, Draw.of(GOLD));
        f.box(12, fl + 1, 88, 22, fl + 5, 88, Draw.of(BLACK));
        f.set(-16, fl + 11, 80, GLOW); f.set(16, fl + 11, 80, GLOW); f.set(0, fl + 10, 80, GLOW);
        f.chest(-25, fl + 1, 72, Draw.EAST, "jaspr:colossus/palace_rich");
        f.chest(25, fl + 1, 72, Draw.WEST, "jaspr:colossus/palace");
        f.wallSign(-5, fl + 6, 80, Draw.WEST, "The King's", "chambers. He", "has not slept", "in an age");
    }

    // ---- watch towers on the terraces' corners (the lower terrace's front ones carry fire cannons) -----------------------------------------
    private void towers(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y;
        int[][] at = {{-T1U + 3, T1V0 + 3, Y1, 26}, {T1U - 3, T1V0 + 3, Y1, 26}, {-T2U + 3, T2V0 + 3, Y2, 24}, {T2U - 3, T2V0 + 3, Y2, 24},
            {-T2U + 3, T2V1 - 3, Y2, 22}, {T2U - 3, T2V1 - 3, Y2, 22}, {-T1U + 3, T1V1 - 3, Y1, 22}, {T1U - 3, T1V1 - 3, Y1, 22}};
        for (int[] q : at) {
            if (!touches(f, q[0] - 6, q[1] - 6, q[0] + 6, q[1] + 6)) continue;
            int yb = y0 + q[2], cap = Math.min(yb + q[3], s.ceilAt(f.x(q[0], q[1]), f.z(q[0], q[1])) - 3);
            f.box(q[0] - 3, yb, q[1] - 3, q[0] + 3, cap, q[1] + 3, (x, y, z) -> Math.floorMod(y - y0, 5) == 0 ? GOLD : RNB);
            f.box(q[0] - 4, cap, q[1] - 4, q[0] + 4, cap, q[1] + 4, Draw.of(GOLD));
            for (int a = -4; a <= 4; a += 2) for (int c = -4; c <= 4; c += 8) { f.set(q[0] + a, cap + 1, q[1] + c, RNB); f.set(q[0] + c, cap + 1, q[1] + a, RNB); }
            brazier(f, q[0], q[1], cap + 1, 0, 1);
            for (int y = yb + 4; y < cap - 2; y += 6) for (int k = -1; k <= 1; k += 2) { f.set(q[0] + k * 3, y, q[1], GLASS_R); f.set(q[0], y, q[1] + k * 3, GLASS_R); }
        }
    }

    // ---- the ash plain: basalt pillars ----------------------------------------------------------------------------------------------------
    private void ashPlain(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int[] q : p.pillars) {
            if (!touches(f, q[0] - 4, q[1] - 4, q[0] + 4, q[1] + 4)) continue;
            int wxi = f.x(q[0], q[1]), wzi = f.z(q[0], q[1]);
            int yb = s.floorAt(wxi, wzi), top = Math.min(yb + q[2], s.ceilAt(wxi, wzi) - 4);
            if (top - yb < 4) continue;
            f.cyl(q[0], q[1], 1.6, yb - 2, top, (x, y, z) -> Math.floorMod(y, 7) == 0 ? ORANGE : BLACK);
            f.cyl(q[0] + 2, q[1] + 1, 1.0, yb - 2, yb + q[2] / 2, Draw.of(BLACK));
            f.set(q[0], top + 1, q[1], RACK);
            f.set(q[0], top + 2, q[1], FIRE);
        }
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, t1 = y0 + Y1, t2 = y0 + Y2;
        // arrows in the Gate of Embers' passage
        out.add(Ordeals.arrows(fr.box(-3, t1 + 1, -82, 3, t1 + 3, -70), fr.box(-4, t1 + 2, -82, 4, t1 + 2, -70)));
        // flame vents on the King's way through the Court of Fire, round its fire pits, at the head of the Inner Stair
        out.add(Ordeals.flames(fr.box(-3, t1 + 1, -67, 3, t1 + 2, -58), 70));
        out.add(Ordeals.flames(fr.box(-3, t1 + 1, -30, 3, t1 + 2, -21), 90));
        for (int a = -1; a <= 1; a += 2) out.add(Ordeals.flames(fr.box(a * 22 - 3, t1 + 1, -47, a * 22 + 3, t1 + 2, -41), 110));
        out.add(Ordeals.flames(fr.box(-12, t2 + 1, -8, 12, t2 + 2, -6), 120));
        // fire cannons on the lower terrace's front towers watch the plaza and the stair
        for (int side = -1; side <= 1; side += 2) {
            int u = side * (T1U - 3), v = T1V0 + 3;
            int cap = Math.min(t1 + 26, s.ceilAt(fr.x(u, v), fr.z(u, v)) - 3);
            out.add(Ordeals.cannon(fr.box(side * 4, y0 - 2, -150, side * 70, t1 + 12, -84), fr.at(u, cap + 3, v)));
        }
        // the Treasury Tower's floors that give way
        for (int k = 2; k <= 4; k++) {
            int[] q = trap(k);
            int yb = t2 + k * STOREY;
            out.add(Ordeals.collapse(fr.box(q[0] - 1, yb, q[1] - 1, q[0] + 1, yb, q[1] + 1), ORANGE_C));
        }
        out.add(Ordeals.rubble(fr.box(TU - 8, t2 + 41, TV - 8, TU + 8, t2 + 45, TV + 8), b(13)));
        // the Treasury of Ash, under the colossus's feet
        int sb = t2 + 1;
        out.add(Ordeals.bossSeal("palace_treasury", fr.box(-1, sb, 38, 1, sb + 1, 38), SEAL, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern: a plain of ash, basalt and embers, lava lakes at its rim ------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double n = Draw.fbm(x, z, 22, s.salt + 71), q = Draw.rnd(x, 0, z, s.salt + 61);
        if (q < 0.05) return MAGMA;
        if (n > 0.3) return BLACK;
        if (n < -0.32) return ORANGE;
        return q < 0.4 ? RACK : b(159, 12);
    }

    @Override Draw.Mat under() {
        return (x, y, z) -> {
            int band = Math.floorMod(y + (int) Math.floor(Draw.noise(x, z, 21, 6151) * 3), 8);
            return band < 4 ? RACK : band < 6 ? BLACK : band < 7 ? b(159, 12) : MAGMA;
        };
    }

    @Override boolean dry(Colossi.Site s, int x, int z) {
        Plan p = (Plan) s.plan;
        if (p == null) return false;
        int u, v;
        switch (p.rot) { case 1: u = z - s.z; v = s.x - x; break; case 2: u = s.x - x; v = s.z - z; break; case 3: u = s.z - z; v = x - s.x; break; default: u = x - s.x; v = z - s.z; }
        return Math.abs(u) < 12 && v < 0;
    }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.0015);
        if (lake) return;
        double q = Draw.rnd(x, 7, z, s.salt + 52);
        if (q < 0.01 && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {
            int len = 1 + (int) (Draw.rnd(x, 8, z, s.salt + 53) * 6);
            for (int k = 0; k < len && ceil - k > floor + 6; k++) d.set(x, ceil - k, z, k == len - 1 ? MAGMA : BLACK);
        }
        if (s.dist(x, z) < 138) return;
        double w = Draw.rnd(x, 9, z, s.salt + 54);
        if (w < 0.008) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
        else if (w < 0.012) d.set(x, floor + 1, z, BLACK);
    }
}
