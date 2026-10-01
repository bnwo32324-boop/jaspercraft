package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Caldera Citadel (owner, 2026-10-01: "a giant fire fortress inspired by Avatar: The Last Airbender"): a volcano
 * that rises from the cavern floor nearly to the Nether's roof, a royal city built in its crater. Red walls under dark,
 * gold-trimmed pagoda roofs, braziers and red lanterns everywhere.
 * <ul>
 *   <li>The Great Gate at the foot, between two colossal statues of the Sovereign; the Royal Road spirals up the outer
 *       slope past guard towers (their fire cannons watch it) to the North Rim Gate; or the Lava Gate leads straight
 *       in, to the magma chamber.</li>
 *   <li>Inside the cone the magma chamber: a lava lake, and on its island the Boiling Keep, the prison, whose Warden
 *       keeps the Warden's Seal. A stair climbs from the keep's roof through the Dragon Bone Catacombs to the palace.</li>
 *   <li>The crater city: the Sun Temple (light its four braziers in the order the garden shrines tell to open the
 *       sanctum of the High Fire Sage, who keeps the Sun Seal), the duelling ring where the Blazing Admiral answers
 *       every challenge (the Admiral's Seal), the royal gardens and the nobles' houses.</li>
 *   <li>The palace on its podium: the Throne Gate opens to the three seals; in the throne hall Sovereign Vahrun waits
 *       before a wall of fire; the royal treasury behind it opens when he falls.</li>
 * </ul>
 */
final class ColossusCitadel extends ColossusDesign {
    static final int RB = 150, RR = 74, RIM = 66, CITY = 46;      // base radius, rim radius, rim and city heights above the base
    static final int CHAMBER = 54, KEEP = 10;                      // magma chamber and keep radii
    static final String[] SEALS = {"sun_seal", "admiral_seal", "warden_seal"};
    static final String[] SEASONS = {"the Dawn", "the Noon", "the Dusk", "the Comet"};

    static final class Plan {
        int rot;
        int[] order = new int[4];
        double roadPhase;
        final List<int[]> houses = new ArrayList<>();    // u, v, facing
        List<Ordeals.Ordeal> ordeals;
    }

    private static final int BLACK = b(159, 15), GREY = b(159, 7), MAGMA = b(213), RACK = b(87), FIRE = b(51), GLOW = b(89);
    private static final int BRICK = b(112), RED_BRICK = b(215), FENCE = b(113), RED = b(251, 14), GOLD = b(251, 4), GOLD_T = b(239, 0);
    private static final int ROOF = 114, ROOF_TRIM = 180, OBSIDIAN = b(49), RED_GLASS = b(95, 14), QUARTZ = b(155, 0), QPILLAR = b(155, 2);
    private static final int CARPET = b(171, 14), LAVA = b(11), IRON = b(101), BONE = b(216, 0), WART = b(214), RED_SAND = b(179, 2);
    private static final int SLAB_BRICK = b(44, 6), SLAB_BRICK_TOP = b(44, 14), ORANGE_T = b(159, 1);

    private static Draw.Mat rock(int salt) {
        return (x, y, z) -> {
            double q = Draw.rnd(x, y, z, salt);
            return q < 0.08 ? MAGMA : q < 0.3 ? GREY : BLACK;
        };
    }

    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        int[] o = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = o[i]; o[i] = o[j]; o[j] = t; }
        p.order = o;
        p.roadPhase = r.nextDouble() * 0.3;
        int[] angles = {30, 62, 118, 150, 240, 300};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i] + (r.nextDouble() - 0.5) * 5);
            int rad = 61 + r.nextInt(3);
            p.houses.add(new int[]{(int) Math.round(Math.cos(a) * rad), (int) Math.round(Math.sin(a) * rad), i});
        }
        return p;
    }

    /** The cone's top at radius rr (outside the crater): the slope from the base to the rim, with lava-rock ridges. */
    static int slope(Colossi.Site s, int x, int z, double rr) {
        double h = RIM * (RB - rr) / (double) (RB - RR);
        h += Draw.noise(x, z, 9, s.salt + 71) * 2.2 + Draw.fbm(x, z, 26, s.salt + 73) * 3;
        return s.y + (int) Math.round(Math.max(0, Math.min(RIM, h)));
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        volcano(f, s, p);
        chamber(f, s);
        gate(f, s);
        road(f, s, p);
        rim(f, s);
        city(f, s, p);
        palace(f, s);
        temple(f, s, p);
        arena(f, s);
        gardens(f, s, p);
        for (int[] h : p.houses) house(f, s, h[0], h[1], h[2]);
    }

    /** The cone, column by column: rock to the slope, the crater floor, the rim crest. */
    private void volcano(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw.Mat rock = rock(s.salt + 3);
        int y0 = s.y, city = y0 + CITY;
        for (int u = -RB - 2; u <= RB + 2; u++) for (int v = -RB - 2; v <= RB + 2; v++) {
            int x = f.x(u, v), z = f.z(u, v);
            if (!f.d.in(x, z)) continue;
            double rr = Math.sqrt(u * (double) u + v * (double) v);
            if (rr > RB + 2) continue;
            int top;
            if (rr >= RR) top = slope(s, x, z, rr);
            else if (rr >= RR - 4) top = y0 + RIM;
            else top = city - 1;
            for (int y = y0 + 1; y <= top; y++) f.d.set(x, y, z, rock.at(x, y, z));
            if (rr < RR - 4) f.d.set(x, city - 1, z, ((u + v) & 1) == 0 ? RED_BRICK : BRICK);    // the city's paving
            else if (rr < RB && Draw.rnd(x, 1, z, s.salt + 5) < 0.012) { f.d.set(x, top, z, RACK); f.d.set(x, top + 1, z, FIRE); }
        }
    }

    /** The magma chamber inside the cone, its lava lake, the Boiling Keep on its island, the bridge from the Lava Gate. */
    private void chamber(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, lo = y0 + 2, hi = y0 + CITY - 7;
        for (int u = -CHAMBER - 6; u <= CHAMBER + 6; u++) for (int v = -CHAMBER - 6; v <= CHAMBER + 6; v++) {
            int x = f.x(u, v), z = f.z(u, v);
            if (!f.d.in(x, z)) continue;
            double rr = Math.sqrt(u * (double) u + v * (double) v), edge = CHAMBER + Draw.noise(x, z, 11, s.salt + 31) * 4;
            if (rr > edge) continue;
            double q = rr / edge;
            int roof = hi - (int) Math.round(q * q * 12);
            for (int y = lo; y <= roof; y++) f.d.set(x, y, z, y <= y0 + 4 ? LAVA : 0);
            f.d.set(x, lo - 1, z, OBSIDIAN);
            if (Draw.rnd(x, 2, z, s.salt + 33) < 0.01 && roof - 1 > y0 + 6) f.d.set(x, roof, z, GLOW);
        }
        // the island and the keep: a rock island, a round keep of six floors, iron-barred windows, a stair up the middle
        f.cyl(0, 0, KEEP + 3, lo, y0 + 7, rock(s.salt + 35));   // the island
        int kb = y0 + 8;
        for (int fl = 0; fl < 6; fl++) {
            int y = kb + fl * 5;
            f.ring(0, 0, KEEP, 2, y, y + 4, Draw.of(BRICK));
            f.disk(0, 0, KEEP - 1, y - 1, Draw.of(fl == 0 ? RED_BRICK : SLAB_BRICK_TOP));
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + fl * 0.4;
                int wu = (int) Math.round(Math.cos(a) * KEEP), wv = (int) Math.round(Math.sin(a) * KEEP);
                f.set(wu, y + 2, wv, IRON);
            }
            // prison cells: iron bars partitions around the stair
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                int cu = (int) Math.round(Math.cos(a) * 6), cv = (int) Math.round(Math.sin(a) * 6);
                f.box(cu - 1, y, cv - 1, cu + 1, y + 3, cv + 1, Draw.of(IRON));
                f.box(cu, y, cv, cu, y + 2, cv, Draw.AIR);
                if (fl % 2 == 1) f.skull(cu, y, cv, 0, k * 4);
            }
        }
        int ktop = kb + 30;
        f.disk(0, 0, KEEP + 1, ktop, Draw.of(RED_BRICK));
        f.ring(0, 0, KEEP + 1, 1, ktop + 1, ktop + 1, Draw.of(FENCE));
        // the stair up through the keep: a square spiral around a pillar
        f.box(0, kb, 0, 0, ktop + 6, 0, Draw.of(RED_BRICK));
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = kb; y <= ktop + 6; y++) {
            f.box(-2, y, -2, 2, y, 2, Draw.AIR);
            f.set(0, y, 0, RED_BRICK);
        }
        for (int y = kb; y <= ktop + 6; y++) { int[] q = ring[Math.floorMod(y - kb, 8)]; f.set(q[0], y, q[1], BRICK); }
        f.point(4, kb + 10, 0, "lord:boiling_warden");
        f.wallSign(-KEEP + 1, kb + 1, 0, Draw.EAST, "THE BOILING", "KEEP", "The Warden", "keeps a seal");
        f.chest(-5, kb + 15, 3, Draw.EAST, "jaspr:colossus/citadel_rich");
        f.chest(5, kb + 25, -3, Draw.WEST, "jaspr:colossus/citadel");
        f.point(-4, kb, -4, "garrison:ember_legionnaire+ember_legionnaire");
        f.point(4, kb + 20, 4, "garrison:royal_guard+flame_adept");
        f.spawner(-6, kb + 5, -6, "ember_legionnaire");
        // the shaft from the keep's roof up through the cone (its last stretch, into the catacombs, comes with the palace)
        f.box(-2, ktop + 1, -2, 2, ktop + 1, 2, Draw.AIR);
        shaft(f, ktop + 1, s.y + CITY - 2, kb);
        // the bridge: from the Lava Gate tunnel (north) across the lake to the keep
        int by = y0 + 12;
        for (int v = -CHAMBER - 2; v <= -KEEP; v++) {
            f.box(-2, by, v, 2, by, v, Draw.of(BRICK));
            f.set(-2, by + 1, v, FENCE); f.set(2, by + 1, v, FENCE);
            f.box(-1, by + 1, v, 1, by + 4, v, Draw.AIR);
            if (Math.floorMod(v, 8) == 0) { f.box(-2, by + 2, v, -2, s.y + CITY - 10, v, Draw.of(FENCE)); f.box(2, by + 2, v, 2, s.y + CITY - 10, v, Draw.of(FENCE)); f.set(0, by + 3, v, RED_GLASS); }
        }
        f.box(-1, by + 1, -KEEP, 1, by + 4, -KEEP + 2, Draw.AIR);
        f.box(-1, by, -KEEP + 1, 1, by, -KEEP + 3, Draw.of(RED_BRICK));
    }

    /** A stairwell: a 5x5 shaft with a spiral of steps around a brick pillar, from ya up to yb. */
    private void shaft(Draw.Frame f, int ya, int yb, int phase) {
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        f.box(-3, ya, -3, 3, yb, 3, Draw.of(RED_BRICK));
        f.box(-2, ya, -2, 2, yb, 2, Draw.AIR);
        f.box(0, ya, 0, 0, yb, 0, Draw.of(BRICK));
        for (int y = ya; y <= yb; y++) { int[] q = ring[Math.floorMod(y - phase, 8)]; f.set(q[0], y, q[1], BRICK); }
    }

    /** The Dragon Bone Catacombs inside the podium: a vault, a dragon's skeleton, the shaft's top, the way to the west wing. */
    private void catacombs(Draw.Frame f, Colossi.Site s) {
        int cy = s.y + CITY, kb = s.y + 8;
        f.box(-14, cy, -6, 14, cy + 5, 6, Draw.of(BRICK));
        f.box(-13, cy + 1, -5, 13, cy + 4, 5, Draw.AIR);
        f.box(-13, cy, -5, 13, cy, 5, Draw.of(RED_BRICK));
        f.box(-12, cy + 4, -4, 12, cy + 4, 4, Draw.AIR);
        for (int u = -12; u <= 9; u++) {
            int h = cy + 2 + (int) Math.round(Math.sin((u + 12) / 21.0 * Math.PI) * 1.4);
            f.set(u, h, 0, BONE);
            if (Math.floorMod(u, 3) == 0) { f.set(u, h - 1, -1, BONE); f.set(u, h - 1, 1, BONE); f.set(u, h - 1, -2, BONE); f.set(u, h - 1, 2, BONE); }
        }
        f.box(9, cy + 1, -2, 12, cy + 3, 2, Draw.of(BONE));
        f.box(10, cy + 2, -1, 11, cy + 2, 1, Draw.AIR);
        f.set(12, cy + 3, -1, GLOW); f.set(12, cy + 3, 1, GLOW);
        f.chest(-12, cy + 1, 4, Draw.SOUTH, "jaspr:colossus/citadel_rich");
        f.point(0, cy + 1, 3, "garrison:royal_guard+royal_guard");
        // the shaft's top: from the paving up into the vault, off the dragon's spine
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        f.box(-2, cy - 1, -2, 2, cy, 2, Draw.AIR);
        f.box(0, cy - 1, 0, 0, cy, 0, Draw.of(BRICK));
        for (int y = cy - 1; y <= cy; y++) { int[] q = ring[Math.floorMod(y - kb, 8)]; f.set(q[0], y, q[1], BRICK); }
        // to the west wing: a ladder up through the podium
        f.box(-15, cy + 1, -1, -14, cy + 4, 1, Draw.AIR);
        for (int y = cy + 1; y <= cy + 6; y++) f.set(-15, y, 0, b(65, f.facing(Draw.EAST)));
        f.box(-15, cy + 5, 0, -15, cy + 6, 0, Draw.AIR);
        for (int y = cy + 1; y <= cy + 6; y++) f.set(-15, y, 0, b(65, f.facing(Draw.EAST)));
        f.set(-16, cy + 5, 0, BRICK); f.set(-16, cy + 6, 0, BRICK);
    }

    /** The Great Gate between the Sovereign's statues, the Lava Gate tunnel behind it. */
    private void gate(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, gv = -RB - 4;
        // the gatehouse: a pagoda gate on a wall across the slope's foot
        f.box(-26, y0, gv - 6, 26, y0 + 18, gv + 6, Draw.of(RED_BRICK));
        f.box(-26, y0 + 18, gv - 6, 26, y0 + 18, gv + 6, Draw.of(BRICK));
        roof(f, -28, gv - 8, 28, gv + 8, y0 + 19, 5);
        f.box(-5, y0 + 1, gv - 6, 5, y0 + 12, gv + 6, Draw.AIR);
        f.box(-5, y0, gv - 6, 5, y0, gv + 6, Draw.of(BRICK));
        for (int u = -4; u <= 4; u++) f.set(u, y0 + 12, gv - 6, IRON);
        f.wallSign(0, y0 + 14, gv - 7, Draw.NORTH, "THE CALDERA", "CITADEL", "Three seals", "open the Throne");
        for (int side = -1; side <= 1; side += 2) {
            statue(f, s, side * 38, gv - 4);
            f.box(side * 7, y0 + 1, gv - 6, side * 7, y0 + 12, gv - 6, Draw.of(GOLD));
            f.set(side * 8, y0 + 4, gv - 7, RACK); f.set(side * 8, y0 + 5, gv - 7, FIRE);
        }
        f.point(0, y0 + 1, gv, "garrison:ember_legionnaire+ember_legionnaire+flame_adept");
        // the Lava Gate: a stair up inside the gatehouse, then the tunnel into the cone at the bridge's height
        int by = y0 + 12;
        for (int k = 0; k < 11; k++) { int v = gv + 7 + k, y = y0 + 1 + k; f.box(-2, y, v, 2, y + 4, v, Draw.AIR); for (int u = -2; u <= 2; u++) f.set(u, y, v, f.stair(ROOF, 2, false)); }
        f.box(-2, by + 1, gv + 18, 2, by + 5, -CHAMBER + 6, Draw.AIR);
        f.box(-2, by, gv + 18, 2, by, -CHAMBER + 6, Draw.of(BRICK));
        for (int v = gv + 20; v <= -CHAMBER - 4; v += 6) { f.set(-3, by + 3, v, GLOW); f.set(3, by + 3, v, GLOW); }
        f.box(-3, by + 6, gv + 18, 3, by + 6, -CHAMBER - 2, Draw.of(BRICK));
        f.point(0, by + 1, -CHAMBER - 12, "garrison:hellhound+hellhound+ember_legionnaire");
    }

    /** A colossal statue of the Sovereign: robes, a crown of flame, a brazier held before him. */
    private void statue(Draw.Frame f, Colossi.Site s, int u, int v) {
        int y0 = s.y;
        f.box(u - 6, y0, v - 6, u + 6, y0 + 3, v + 6, Draw.of(BLACK));
        f.box(u - 4, y0 + 4, v - 3, u + 4, y0 + 14, v + 3, Draw.of(RED));          // robes
        f.box(u - 3, y0 + 15, v - 2, u + 3, y0 + 19, v + 2, Draw.of(RED));
        f.box(u - 6, y0 + 15, v - 2, u - 4, y0 + 18, v + 1, Draw.of(RED));          // arms
        f.box(u + 4, y0 + 15, v - 2, u + 6, y0 + 18, v + 1, Draw.of(RED));
        f.box(u - 2, y0 + 20, v - 2, u + 2, y0 + 23, v + 2, Draw.of(BLACK));         // head
        f.box(u - 1, y0 + 24, v - 1, u + 1, y0 + 24, v + 1, Draw.of(GOLD));         // crown
        f.set(u, y0 + 25, v, RACK); f.set(u, y0 + 26, v, FIRE);
        f.box(u - 2, y0 + 15, v - 5, u + 2, y0 + 15, v - 3, Draw.of(GOLD_T));      // the brazier
        f.box(u - 1, y0 + 16, v - 4, u + 1, y0 + 16, v - 4, Draw.of(RACK));
        f.box(u - 1, y0 + 17, v - 4, u + 1, y0 + 17, v - 4, Draw.of(FIRE));
        f.set(u - 1, y0 + 21, v - 3, GLOW); f.set(u + 1, y0 + 21, v - 3, GLOW);
    }

    /** The Royal Road: a terrace spiralling up the outer slope from the Great Gate to the North Rim Gate. */
    private void road(Draw.Frame f, Colossi.Site s, Plan p) {
        int y0 = s.y;
        double turns = 1.25, r0 = RB - 6, r1 = RR + 3;
        double a0 = -Math.PI / 2;                  // starts at the north (the gate) and arrives at the north again
        for (int u = -RB; u <= RB; u++) for (int v = -RB; v <= RB; v++) {
            int x = f.x(u, v), z = f.z(u, v);
            if (!f.d.in(x, z)) continue;
            double rr = Math.sqrt(u * (double) u + v * (double) v);
            if (rr < r1 - 4 || rr > r0 + 4) continue;
            double a = Math.atan2(v, u);
            for (int k = 0; k <= 2; k++) {
                double t = (a - a0 + 2 * Math.PI * k) / (2 * Math.PI * turns);
                if (t < 0 || t > 1) continue;
                double rk = r0 - (r0 - r1) * t;
                if (Math.abs(rr - rk) > 3.5) continue;
                int ry = y0 + 1 + (int) Math.round((RIM - 1) * t);
                boolean edge = Math.abs(rr - rk) > 2.6;
                for (int y = ry - 8; y < ry; y++) if (f.d.air(x, y, z) || f.d.id(x, y, z) == Blocks.LAVA) f.d.set(x, y, z, b(112));
                f.d.set(x, ry - 1, z, edge ? RED_BRICK : ((x + z) & 1) == 0 ? b(1, 6) : b(1, 5));
                for (int y = ry; y <= ry + 5; y++) f.d.set(x, y, z, 0);
                if (edge && rr > rk) { f.d.set(x, ry, z, FENCE); if (Draw.rnd(x, 0, z, s.salt + 81) < 0.05) { f.d.set(x, ry + 1, z, FENCE); f.d.set(x, ry + 2, z, RED_GLASS); } }
            }
        }
        // guard towers along the road (their cannons watch it)
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0, a = a0 + 2 * Math.PI * turns * t, rk = r0 - (r0 - r1) * t + 7;
            int tu = (int) Math.round(Math.cos(a) * rk), tv = (int) Math.round(Math.sin(a) * rk);
            int ty = y0 + 1 + (int) Math.round((RIM - 1) * t);
            tower(f, tu, tv, ty - 1, 7);
            f.point(tu, ty + 8, tv, "garrison:ember_legionnaire+flame_adept");
        }
    }

    private void tower(Draw.Frame f, int u, int v, int yb, int h) {
        f.box(u - 2, yb - 6, v - 2, u + 2, yb + h, v + 2, Draw.of(RED_BRICK));
        f.box(u - 1, yb + 1, v - 1, u + 1, yb + h - 1, v + 1, Draw.AIR);
        f.box(u - 3, yb + h, v - 3, u + 3, yb + h, v + 3, Draw.of(BRICK));
        roof(f, u - 3, v - 3, u + 3, v + 3, yb + h + 1, 2);
        f.set(u, yb + h - 1, v, GLOW);
    }

    /** A pagoda roof over a box: courses of dark stairs stepping in, upturned gold-trimmed eaves, a ridge. */
    private void roof(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int courses) {
        int a = Math.min(u0, u1), bU = Math.max(u0, u1), c = Math.min(v0, v1), dV = Math.max(v0, v1);
        for (int k = 0; k < courses; k++) {
            int ua = a + k, ub = bU - k, va = c + k, vb = dV - k, y = yb + k;
            if (ua > ub || va > vb) break;
            for (int u = ua; u <= ub; u++) { f.set(u, y, va, f.stair(ROOF, 2, false)); f.set(u, y, vb, f.stair(ROOF, 3, false)); }
            for (int v = va; v <= vb; v++) { f.set(ua, y, v, f.stair(ROOF, 0, false)); f.set(ub, y, v, f.stair(ROOF, 1, false)); }
            if (k == 0) for (int[] q : new int[][]{{ua, va}, {ub, va}, {ua, vb}, {ub, vb}}) { f.set(q[0], y, q[1], GOLD_T); f.set(q[0], y + 1, q[1], f.stair(ROOF_TRIM, 2, true)); }
            f.box(ua + 1, y, va + 1, ub - 1, y, vb - 1, Draw.of(BRICK));
        }
        // the top: a gilded cap on a small roof; on a broad one dark tiles edged in gold
        int k = courses;
        int ua = a + k, ub = bU - k, va = c + k, vb = dV - k;
        if (ua > ub || va > vb) return;
        if (ub - ua <= 2 && vb - va <= 2) { f.box(ua, yb + k, va, ub, yb + k, vb, Draw.of(GOLD_T)); return; }
        f.box(ua, yb + k, va, ub, yb + k, vb, Draw.of(GOLD_T));
        f.box(ua + 1, yb + k, va + 1, ub - 1, yb + k, vb - 1, Draw.of(BRICK));
    }

    /** The rim: a city wall along the crest, watch posts with fire cannons, the North Rim Gate and its stair down. */
    private void rim(Draw.Frame f, Colossi.Site s) {
        int y0 = s.y, top = y0 + RIM, city = y0 + CITY;
        for (int u = -RR - 1; u <= RR + 1; u++) for (int v = -RR - 1; v <= RR + 1; v++) {
            double rr = Math.sqrt(u * (double) u + v * (double) v);
            if (rr < RR - 1 || rr > RR + 0.5) continue;
            f.set(u, top + 1, v, RED_BRICK);
            if (Math.floorMod(Math.round(Math.atan2(v, u) * RR), 3) == 0) f.set(u, top + 2, v, RED_BRICK);
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int tu = (int) Math.round(Math.cos(a) * (RR - 2)), tv = (int) Math.round(Math.sin(a) * (RR - 2));
            tower(f, tu, tv, top, 6);
        }
        // the North Rim Gate: a pagoda gatehouse over the road's arrival, and the stair down into the crater
        int gv = -RR + 2;
        f.box(-7, top - 2, gv - 6, 7, top + 8, gv + 4, Draw.of(RED_BRICK));
        f.box(-3, top + 1, gv - 6, 3, top + 6, gv + 4, Draw.AIR);
        f.box(-3, top, gv - 6, 3, top, gv + 4, Draw.of(BRICK));
        roof(f, -9, gv - 8, 9, gv + 6, top + 9, 4);
        f.wallSign(0, top + 3, gv - 7, Draw.NORTH, "North Rim Gate", "", "Bow before", "the Throne");
        for (int k = 0; k < RIM - CITY; k++) {
            int v = gv + 5 + k, y = top - k;
            f.box(-3, y - 1, v, 3, y - 1, v, Draw.of(BRICK));
            for (int u = -3; u <= 3; u++) f.set(u, y - 1, v, f.stair(ROOF, 3, false));
            f.box(-3, y, v, 3, y + 5, v, Draw.AIR);
            f.set(-4, y, v, RED_BRICK); f.set(4, y, v, RED_BRICK);
        }
        f.point(0, top + 1, gv, "garrison:royal_guard+ember_legionnaire");
    }

    /** The crater floor: lantern posts and the four garden shrines (each tells one brazier's place in the rite). */
    private void city(Draw.Frame f, Colossi.Site s, Plan p) {
        int fl = s.y + CITY;
        for (int u = -RR + 6; u <= RR - 6; u += 12) for (int v = -RR + 6; v <= RR - 6; v += 12) {
            if (u * u + v * v > (RR - 8) * (RR - 8) || (Math.abs(u) < 34 && v > -46 && v < 50)) continue;
            f.box(u, fl, v, u, fl + 2, v, Draw.of(FENCE));
            f.set(u, fl + 3, v, RED_GLASS);
            f.set(u, fl + 4, v, GLOW);
        }
        int[][] shrines = {{-52, -40}, {52, -40}, {52, 40}, {-52, 40}};
        String[] nth = {"first", "second", "third", "last"};
        for (int i = 0; i < 4; i++) {
            int u = shrines[i][0], v = shrines[i][1];
            f.box(u - 2, fl, v - 2, u + 2, fl, v + 2, Draw.of(QUARTZ));
            f.box(u - 1, fl + 1, v - 1, u + 1, fl + 1, v + 1, Draw.of(GOLD_T));
            f.set(u, fl + 2, v, RACK); f.set(u, fl + 3, v, FIRE);
            int place = 0;
            for (int k = 0; k < 4; k++) if (p.order[k] == i) place = k;
            f.wallSign(u, fl + 1, v - 2, Draw.NORTH, "The flame of", SEASONS[i], "is lit " + nth[place], "");
            f.wallSign(u, fl + 1, v + 2, Draw.SOUTH, "The flame of", SEASONS[i], "is lit " + nth[place], "");
        }
        f.point(-30, fl, -55, "garrison:ember_legionnaire+ember_legionnaire");
        f.point(30, fl, 55, "garrison:royal_guard+flame_adept");
        f.point(55, fl, 0, "garrison:flame_adept+hellhound");
    }

    /** The palace on its podium: the grand stair, the Throne Gate, the throne hall before the wall of fire, the wings. */
    private void palace(Draw.Frame f, Colossi.Site s) {
        int fl = s.y + CITY, pb = fl + 6;
        // the podium
        f.box(-32, fl, -42, 32, pb - 1, 46, Draw.of(RED_BRICK));
        f.walls(-32, fl, -42, 32, pb - 1, 46, Draw.of(BLACK));
        for (int k = 0; k < 6; k++) { int v = -48 + k, y = fl + k; f.box(-8, fl, v, 8, y, v, Draw.of(RED_BRICK)); for (int u = -8; u <= 8; u++) f.set(u, y, v, f.stair(ROOF, 2, false)); }
        f.box(-32, pb - 1, -42, 32, pb - 1, 46, (x, y, z) -> ((x + z) & 1) == 0 ? RED_BRICK : BRICK);
        catacombs(f, s);
        // the throne hall
        int h0 = -26, h1 = 32, top = pb + 14;
        f.box(-11, pb, h0, 11, top, h1, Draw.of(RED));
        f.box(-10, pb, h0 + 1, 10, top - 1, h1 - 1, Draw.AIR);
        f.box(-11, top, h0, 11, top, h1, Draw.of(BRICK));
        roof(f, -14, h0 - 3, 14, h1 + 3, top + 1, 6);
        roof(f, -9, h0 + 4, 9, h1 - 4, top + 8, 4);
        f.box(-1, pb, h0 + 2, 1, pb, h1 - 9, Draw.of(CARPET));
        for (int v = h0 + 4; v <= h1 - 10; v += 6) for (int u = -7; u <= 7; u += 14) {
            f.box(u, pb, v, u, top - 2, v, Draw.of(RED_BRICK));
            f.set(u, top - 1, v, GOLD);
            f.set(u + (u < 0 ? 1 : -1), pb + 5, v, RACK); f.set(u + (u < 0 ? 1 : -1), pb + 6, v, FIRE);
        }
        // the dais, the throne and the wall of fire behind it
        for (int k = 0; k < 4; k++) f.box(-8 + k, pb + k, h1 - 9 + k, 8 - k, pb + k, h1 - 2, Draw.of(k == 3 ? GOLD_T : RED_BRICK));
        f.box(-1, pb + 4, h1 - 5, 1, pb + 4, h1 - 5, Draw.of(GOLD));
        f.set(0, pb + 4, h1 - 4, f.stair(ROOF_TRIM, 2, false));
        f.box(-1, pb + 5, h1 - 3, 1, pb + 7, h1 - 3, Draw.of(GOLD));
        for (int u = -9; u <= 9; u++) for (int y = pb; y <= top - 2; y++) f.set(u, y, h1 - 1, ((y - pb) & 1) == 0 ? RACK : FIRE);
        f.point(0, pb + 4, h1 - 7, "lord:ember_sovereign");
        f.point(-6, pb, h0 + 10, "garrison:royal_guard+royal_guard");
        f.point(6, pb, h0 + 20, "garrison:royal_guard+flame_adept");
        // the Throne Gate (three seals) and its doorway
        f.box(-3, pb, h0, 3, pb + 6, h0, Draw.of(RED_BRICK));
        f.box(-2, pb, h0, 2, pb + 4, h0, Draw.of(BLACK));
        f.box(-3, pb + 7, h0 - 1, 3, pb + 8, h0 - 1, Draw.of(GOLD));
        f.wallSign(0, pb + 6, h0 - 1, Draw.NORTH, "THE THRONE", "Show the Sun,", "the Admiral's", "and the Warden's");
        f.wallSign(-4, pb + 2, h0 - 1, Draw.NORTH, "", "seals, and", "the gate opens", "");
        // the royal treasury behind the wall of fire (opens when the Sovereign falls)
        f.box(-8, pb, h1, 8, pb + 7, h1 + 9, Draw.of(RED_BRICK));
        f.box(-7, pb, h1 + 1, 7, pb + 6, h1 + 8, Draw.AIR);
        f.box(-7, pb - 1, h1 + 1, 7, pb - 1, h1 + 8, Draw.of(GOLD_T));
        for (int u = -5; u <= 5; u += 5) f.chest(u, pb, h1 + 7, Draw.NORTH, "jaspr:colossus/citadel_vault");
        f.box(-8, pb, h1 + 4, -8, pb + 3, h1 + 4, Draw.of(BLACK));                       // its seal, reached round the hall
        // the wings: royal quarters (west) and the war room (east)
        for (int side = -1; side <= 1; side += 2) {
            int ua = side * 14, ub = side * 30;
            f.box(Math.min(ua, ub), pb, -20, Math.max(ua, ub), pb + 8, 20, Draw.of(RED));
            f.box(Math.min(ua, ub) + 1, pb, -19, Math.max(ua, ub) - 1, pb + 7, 19, Draw.AIR);
            roof(f, Math.min(ua, ub) - 2, -22, Math.max(ua, ub) + 2, 22, pb + 9, 4);
            f.box(side * 11, pb, -2, side * 14, pb + 4, 2, Draw.AIR);                    // from the hall
            f.chest(side * 22, pb, -18, side < 0 ? Draw.SOUTH : Draw.SOUTH, side < 0 ? "jaspr:colossus/citadel_rich" : "jaspr:colossus/citadel_war");
            f.chest(side * 28, pb, 18, Draw.NORTH, "jaspr:colossus/citadel");
            f.point(side * 22, pb, 0, "garrison:royal_guard+flame_adept");
            f.set(side * 22, pb + 6, 10, GLOW); f.set(side * 22, pb + 6, -10, GLOW);
        }
        // the war room table (east) and the way down from the west wing to the catacombs
        f.box(18, pb, -6, 26, pb, 6, Draw.of(b(5, 5)));
        f.box(19, pb + 1, -5, 25, pb + 1, 5, Draw.of(b(171, 14)));
    }

    /** The Sun Temple: a four-tiered pagoda; four braziers around the sun at its foot open the stair to the Sage's sanctum. */
    private void temple(Draw.Frame f, Colossi.Site s, Plan p) {
        int fl = s.y + CITY, tu = 48, tv = -8;
        int[] half = {9, 7, 5, 4}, tall = {7, 4, 4, 3};
        int y = fl;
        for (int t = 0; t < half.length; t++) {
            int w = half[t], h = tall[t];
            f.box(tu - w, y, tv - w, tu + w, y + h, tv + w, Draw.of(RED));
            f.box(tu - w + 1, y, tv - w + 1, tu + w - 1, y + h - 1, tv + w - 1, Draw.AIR);
            f.box(tu - w, y - 1, tv - w, tu + w, y - 1, tv + w, Draw.of(BRICK));
            roof(f, tu - w - 2, tv - w - 2, tu + w + 2, tv + w + 2, y + h, 2);
            y += h + 2;
        }
        f.box(tu - 1, y, tv - 1, tu + 1, y + 2, tv + 1, Draw.of(GOLD));
        f.set(tu, y + 3, tv, GLOW);
        // the rite hall at its foot: the sun mosaic and four braziers
        f.box(tu - 3, fl - 1, tv - 3, tu + 3, fl - 1, tv + 3, Draw.of(GOLD_T));
        f.set(tu, fl - 1, tv, GLOW);
        int[][] braziers = {{tu - 5, tv - 5}, {tu + 5, tv - 5}, {tu + 5, tv + 5}, {tu - 5, tv + 5}};
        for (int i = 0; i < 4; i++) {
            int[] q = braziers[i];
            f.set(q[0], fl, q[1], GOLD_T);
            f.set(q[0], fl + 1, q[1], RACK);
            f.wallSign(q[0] + (i == 0 || i == 3 ? 1 : -1), fl, q[1], i == 0 || i == 3 ? Draw.EAST : Draw.WEST, "", SEASONS[i], "", "");
        }
        f.box(tu - 9, fl, tv - 1, tu - 9, fl + 3, tv + 1, Draw.AIR);       // the door, facing the city's centre
        f.wallSign(tu - 10, fl + 3, tv + 2, Draw.WEST, "THE SUN TEMPLE", "Light the four", "as the garden", "shrines tell");
        // the sealed stair along the south wall, up to the second tier (the sanctum)
        f.box(tu - 7, fl, tv + 6, tu + 7, fl + 6, tv + 6, Draw.of(RED_BRICK));
        f.box(tu - 7, fl, tv + 6, tu - 7, fl + 2, tv + 6, Draw.of(GOLD));      // the seal
        int top = fl + tall[0] + 1;                                           // the sanctum's floor block
        for (int k = 0; k <= top - fl; k++) {
            int u = tu - 6 + k, yy = fl + k;
            f.set(u, yy, tv + 7, f.stair(ROOF, 0, false));
            f.box(u, yy + 1, tv + 7, u, yy + 3, tv + 7, Draw.AIR);
        }
        f.point(tu, top + 1, tv, "lord:high_fire_sage");
        f.chest(tu - 5, top + 1, tv + 5, Draw.NORTH, "jaspr:colossus/citadel_rich");
        f.spawner(tu + 5, top + 1, tv - 5, "flame_adept");
    }

    /** The duelling ring: a sunken ring of fire-blackened stone, braziers on posts, tiers for the court, the Admiral's pavilion. */
    private void arena(Draw.Frame f, Colossi.Site s) {
        int fl = s.y + CITY, au = -48, av = -8, r = 13;
        f.cyl(au, av, r + 6, fl, fl + 3, Draw.AIR);
        for (int k = 0; k < 3; k++) f.ring(au, av, r + 5 - k, 1, fl - 3 + k, fl - 3 + k, Draw.of(k == 2 ? RED_BRICK : BRICK));
        f.disk(au, av, r + 2, fl - 3, Draw.of(BLACK));
        f.cyl(au, av, r + 2, fl - 2, fl + 3, Draw.AIR);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int u = au + (int) Math.round(Math.cos(a) * (r + 1)), v = av + (int) Math.round(Math.sin(a) * (r + 1));
            f.box(u, fl - 2, v, u, fl + 1, v, Draw.of(FENCE));
            f.set(u, fl + 2, v, RACK); f.set(u, fl + 3, v, FIRE);
        }
        f.point(au, fl - 2, av, "lord:blazing_admiral");
        f.wallSign(au + r + 5, fl, av, Draw.EAST, "THE RING", "The Admiral", "answers every", "challenge");
        int pv = av - 30;
        f.box(au - 6, fl, pv - 5, au + 6, fl + 6, pv + 5, Draw.of(RED));
        f.box(au - 5, fl, pv - 4, au + 5, fl + 5, pv + 4, Draw.AIR);
        roof(f, au - 8, pv - 7, au + 8, pv + 7, fl + 7, 3);
        f.box(au - 1, fl, pv + 5, au + 1, fl + 3, pv + 5, Draw.AIR);
        f.chest(au, fl, pv - 3, Draw.SOUTH, "jaspr:colossus/citadel_war");
        f.point(au + 3, fl, pv, "garrison:ember_legionnaire+ember_legionnaire");
    }

    /** The royal gardens behind the palace: ember trees, a magma pond with an obsidian rim, a pavilion. */
    private void gardens(Draw.Frame f, Colossi.Site s, Plan p) {
        int fl = s.y + CITY;
        f.disk(0, 58, 7, fl - 1, Draw.of(OBSIDIAN));
        f.disk(0, 58, 5, fl - 1, Draw.of(MAGMA));
        for (int i = 0; i < 6; i++) {
            int u = -24 + i * 10, v = 52 + (i % 2) * 8;
            if (Math.abs(u) < 8) continue;
            f.box(u, fl, v, u, fl + 4, v, Draw.of(BRICK));
            f.box(u - 2, fl + 5, v - 2, u + 2, fl + 6, v + 2, Draw.of(WART));
            f.box(u - 1, fl + 7, v - 1, u + 1, fl + 7, v + 1, Draw.of(WART));
        }
        f.box(18, fl, 56, 26, fl + 4, 64, Draw.AIR);
        for (int[] q : new int[][]{{18, 56}, {26, 56}, {18, 64}, {26, 64}}) f.box(q[0], fl, q[1], q[0], fl + 3, q[1], Draw.of(RED_BRICK));
        roof(f, 16, 54, 28, 66, fl + 4, 3);
        f.chest(22, fl, 60, Draw.NORTH, "jaspr:colossus/citadel");
    }

    /** A noble's house: a small pagoda on a plinth, a chest (now and then trapped), a guard. */
    private void house(Draw.Frame f, Colossi.Site s, int u, int v, int i) {
        int fl = s.y + CITY;
        f.box(u - 5, fl - 1, v - 5, u + 5, fl - 1, v + 5, Draw.of(BRICK));
        f.box(u - 4, fl, v - 4, u + 4, fl + 4, v + 4, Draw.of(RED));
        f.box(u - 3, fl, v - 3, u + 3, fl + 3, v + 3, Draw.AIR);
        roof(f, u - 6, v - 6, u + 6, v + 6, fl + 5, 3);
        int du = u > 0 ? -4 : 4;
        f.box(u + du, fl, v - 1, u + du, fl + 2, v + 1, Draw.AIR);
        f.chest(u - (u > 0 ? -2 : 2), fl, v + 2, Draw.NORTH, i % 3 == 0 ? "jaspr:colossus/citadel_rich!trap" : "jaspr:colossus/citadel");
        f.set(u, fl + 4, v, GLOW);
        f.point(u, fl, v - 2, i % 2 == 0 ? "garrison:ember_legionnaire+flame_adept" : "garrison:royal_guard");
    }

    // ---- the ordeals ------------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, fl = y0 + CITY, pb = fl + 6, top = y0 + RIM;
        // cannons: the road's guard towers and the rim's watch posts
        double turns = 1.25, r0 = RB - 6, r1 = RR + 3, a0 = -Math.PI / 2;
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0, a = a0 + 2 * Math.PI * turns * t, rk = r0 - (r0 - r1) * t + 7;
            int tu = (int) Math.round(Math.cos(a) * rk), tv = (int) Math.round(Math.sin(a) * rk), ty = y0 + 1 + (int) Math.round((RIM - 1) * t);
            out.add(Ordeals.cannon(fr.box(tu - 30, ty - 12, tv - 30, tu + 30, ty + 10, tv + 30), fr.at(tu, ty + 8, tv)));
        }
        for (int i = 0; i < 8; i += 2) {
            double a = i * Math.PI / 4;
            int tu = (int) Math.round(Math.cos(a) * (RR - 2)), tv = (int) Math.round(Math.sin(a) * (RR - 2));
            out.add(Ordeals.cannon(fr.box(tu - 28, top - 26, tv - 28, tu + 28, top + 8, tv + 28), fr.at(tu, top + 7, tv)));
        }
        // flame vents: the Throne Gate's approach, the bridge to the keep, the ring's edge
        out.add(Ordeals.flames(fr.box(-3, pb, -36, 3, pb + 2, -30), 70));
        out.add(Ordeals.flames(fr.box(-1, y0 + 13, -40, 1, y0 + 15, -34), 90));
        out.add(Ordeals.flames(fr.box(-1, y0 + 13, -26, 1, y0 + 15, -20), 90));
        out.add(Ordeals.gas(fr.box(-8, y0 + 8, -8, 8, y0 + 12, 8)));
        // the rite: four braziers in the shrines' order open the sanctum stair
        int tu = 48, tv = -8;
        int[][] br = {{tu - 5, tv - 5}, {tu + 5, tv - 5}, {tu + 5, tv + 5}, {tu - 5, tv + 5}};
        int[][] parts = new int[4][];
        for (int i = 0; i < 4; i++) parts[i] = fr.at(br[i][0], fl + 1, br[i][1]);
        out.add(Ordeals.braziers("citadel_rite", fr.box(tu - 8, fl, tv - 8, tu + 8, fl + 8, tv + 8), parts, p.order, fr.box(tu - 7, fl, tv + 6, tu - 7, fl + 2, tv + 6), GOLD, null,
            fr.box(tu - 8, fl, tv - 8, tu + 8, fl + 6, tv + 8)));
        // the Throne Gate (three seals) and the treasury (the Sovereign's fall)
        out.add(Ordeals.keySeal("citadel_throne", fr.box(-2, pb, -26, 2, pb + 4, -26), BLACK, SEALS));
        out.add(Ordeals.bossSeal("citadel_treasury", fr.box(-8, pb, 32 + 4, -8, pb + 3, 32 + 4), BLACK, "ember_sovereign"));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -------------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61);
        return q < 0.15 ? MAGMA : q < 0.5 ? GREY : BLACK;
    }

    @Override Draw.Mat under() { return rock(5521); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < RB + 6; }

    @Override double lakeLevel() { return 0.3; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.004);
        if (lake || s.dist(x, z) < RB + 8) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.01) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
        else if (q < 0.016) d.set(x, floor + 1, z, MAGMA);
    }
}
