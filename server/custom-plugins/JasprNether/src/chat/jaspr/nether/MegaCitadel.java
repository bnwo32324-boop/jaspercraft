package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Frozen Citadel (Arctic Abyss): an octagonal curtain wall of ice-blue brick with eight spired towers, ringed by
 * an ichor moat and a drawbridge, around a four-storey keep crowned by a great ice spire. Inside: barracks, a rime
 * library, and on the third floor the Rime Throne between blue-fire braziers, where the Wight Lord keeps the
 * Nether's richest vault. Ice sculptures, a brute pen and coolmar nests fill the courtyard; ice spikes the cavern.
 * Wights, Coolmar Spiders, Brutes and Frost guard it; its loot is rime, frost and the top tier of Nether treasure.
 */
final class MegaCitadel extends MegaDesign {
    static final int WALL_R = 40, KEEP = 13, STOREY = 9;

    static final class Plan {
        int rot;
        final List<int[]> sculptures = new ArrayList<>();   // u, v, height
        final List<int[]> spikes = new ArrayList<>();       // u, v, height, radius
    }

    @Override Object plan(Mega.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        for (int i = 0; i < 6; i++) {
            double a = (i + r.nextDouble() * 0.6) / 6 * Math.PI * 2, d = 22 + r.nextInt(10);
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            if (v > 14 && Math.abs(u) < 7) continue;   // the gate road
            if (v < -12 && Math.abs(u) > 12) continue; // the brute pen and the coolmar nest
            p.sculptures.add(new int[]{u, v, 4 + r.nextInt(5)});
        }
        for (int i = 0; i < 16; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 58 + r.nextDouble() * 30;
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            if (v > 40 && Math.abs(u) < 8) continue;
            p.spikes.add(new int[]{u, v, 8 + r.nextInt(16), 2 + r.nextInt(3)});
        }
        return p;
    }

    private static final int ICE = b(174), ICY_BRICK = b(159, 3), WHITE = b(251, 0), LBLUE = b(251, 3), QUARTZ = b(155), QZ_PILLAR = b(155, 2);
    private static final int QZ_CHISELED = b(155, 1), LANTERN = b(169), GLASS = b(160, 3), FIRE = b(51), WATER = b(9), PRISM = b(168, 2);
    private static final int QZ_SLAB_TOP = b(44, 15), ICY_RACK = b(159, 3), SNOW = b(251, 0), FENCE = b(113), RACK = b(87);

    private static Draw.Mat stone(int salt) {
        return (x, y, z) -> {
            if (Math.floorMod(y, 6) == 0) return QUARTZ;
            double q = Draw.rnd(x, y, z, salt);
            return q < 0.38 ? ICE : q < 0.62 ? WHITE : q < 0.78 ? LBLUE : q < 0.9 ? b(168, 1) : ICY_BRICK;
        };
    }

    @Override void draw(Mega.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y, yb = y0 + 1;
        Draw.Mat st = stone(s.salt + 1);
        // bailey floor inside the wall: packed ice with quartz paths
        d.cyl(s.x, s.z, WALL_R + 2, y0 - 4, y0, Draw.of(ICY_RACK));
        d.disk(s.x, s.z, WALL_R, y0, (x, y, z) -> Draw.rnd(x, y, z, s.salt + 2) < 0.2 ? SNOW : ICE);
        f.box(-2, y0, 13, 2, y0, WALL_R + 12, Draw.of(QUARTZ));
        moat(f, s, y0);
        wall(f, s, yb, st);
        keep(f, s, yb, st);
        courtyard(f, s, p, yb);
        for (int[] sp : p.spikes) spike(f, s, sp);
    }

    /** The ichor moat with ice floes, and the drawbridge on the gate road. */
    private void moat(Draw.Frame f, Mega.Site s, int y0) {
        Draw d = f.d;
        d.tube(s.x, s.z, WALL_R + 11, WALL_R + 11, 10, y0 - 3, y0 - 3, Draw.of(PRISM));
        d.tube(s.x, s.z, WALL_R + 10, WALL_R + 10, 7.5, y0 - 2, y0, (x, y, z) -> y == y0 && Draw.rnd(x, 0, z, s.salt + 5) < 0.14 ? ICE : WATER);
        d.tube(s.x, s.z, WALL_R + 11, WALL_R + 11, 1, y0, y0 + 1, Draw.of(QUARTZ));
        f.box(-3, y0 + 1, WALL_R + 2, 3, y0 + 1, WALL_R + 12, Draw.of(QZ_SLAB_TOP));
        f.box(-3, y0 + 2, WALL_R + 2, -3, y0 + 2, WALL_R + 12, Draw.of(FENCE));
        f.box(3, y0 + 2, WALL_R + 2, 3, y0 + 2, WALL_R + 12, Draw.of(FENCE));
        f.box(-2, y0 + 2, WALL_R + 2, 2, y0 + 5, WALL_R + 12, Draw.AIR);
        for (int w = -3; w <= 3; w += 6) { f.box(w, y0 + 2, WALL_R + 12, w, y0 + 4, WALL_R + 12, Draw.of(QZ_PILLAR)); f.set(w, y0 + 5, WALL_R + 12, LANTERN); }
    }

    /** The octagonal curtain wall with a wall walk, eight spired towers and the gatehouse. */
    private void wall(Draw.Frame f, Mega.Site s, int yb, Draw.Mat st) {
        Draw d = f.d;
        int top = yb + 13;
        double[][] vtx = new double[8][];
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) / 8 * Math.PI * 2;
            vtx[i] = new double[]{Math.cos(a) * WALL_R, Math.sin(a) * WALL_R};
        }
        for (int i = 0; i < 8; i++) {
            double[] a = vtx[i], c = vtx[(i + 1) % 8];
            for (int y = yb; y <= top; y++) f.line(a[0], y, a[1], c[0], y, c[1], 1.4, st);
            f.line(a[0], top + 1, a[1], c[0], top + 1, c[1], 1.4, (x, y, z) -> ((x + z) & 1) == 0 ? QUARTZ : -1);
        }
        for (int i = 0; i < 8; i++) {
            int x = f.x((int) Math.round(vtx[i][0]), (int) Math.round(vtx[i][1])), z = f.z((int) Math.round(vtx[i][0]), (int) Math.round(vtx[i][1]));
            tower(d, x, z, yb, 4, top + 12, st, i);
        }
        // gatehouse over the gate road
        for (int w = -1; w <= 1; w += 2) {
            f.box(w * 4, yb, WALL_R - 4, w * 8, top + 4, WALL_R + 2, st);
            f.box(w * 5, top + 5, WALL_R - 3, w * 7, top + 5, WALL_R + 1, (x, y, z) -> ((x + z) & 1) == 0 ? QUARTZ : -1);
            f.set(w * 6, top + 5, WALL_R - 1, LANTERN);
        }
        f.point(0, top + 1, WALL_R - 1, "garrison:frost");
        f.box(-3, yb, WALL_R - 4, 3, top, WALL_R + 2, st);
        f.box(-2, yb, WALL_R - 5, 2, yb + 6, WALL_R + 3, Draw.AIR);
        f.box(-1, yb + 7, WALL_R - 5, 1, yb + 7, WALL_R + 3, Draw.AIR);
        f.box(-3, yb + 8, WALL_R + 2, 3, yb + 8, WALL_R + 2, Draw.of(QZ_CHISELED));
        f.point(0, yb, WALL_R - 8, "garrison:wight+wight");
    }

    /** A round tower with an ice cone roof and a lantern tip. */
    static void tower(Draw d, int x, int z, int yb, double r, int top, Draw.Mat st, int idx) {
        d.cyl(x, z, r, yb, top, st);
        d.cyl(x, z, r - 1.5, yb, top - 1, Draw.AIR);
        d.disk(x, z, r - 1.5, yb + 12, Draw.of(ICE));
        d.cone(x, z, r + 1.2, 0.2, top + 1, top + 12, Draw.of(ICE));
        d.set(x, top + 13, z, LANTERN);
        d.ring(x, z, r + 0.2, 0.9, top - 3, top - 2, (xx, y, zz) -> y == top - 2 && ((xx + zz) & 1) == 0 ? GLASS : -1);
        d.set(x, top, z, LANTERN);
    }

    /** The keep: four storeys of barracks, library, the Rime Throne and a roof terrace under the great spire. */
    private void keep(Draw.Frame f, Mega.Site s, int yb, Draw.Mat st) {
        Draw d = f.d;
        int roof = yb + STOREY * 4;
        f.box(-KEEP, yb, -KEEP, KEEP, roof, KEEP, st);
        for (int k = 0; k < 4; k++) {
            int fy = yb + STOREY * k;
            f.box(-KEEP + 2, fy + (k == 0 ? 0 : 1), -KEEP + 2, KEEP - 2, fy + STOREY - 1, KEEP - 2, Draw.AIR);
            f.box(-KEEP + 2, fy, -KEEP + 2, KEEP - 2, fy, KEEP - 2, (x, y, z) -> ((x + z) & 1) == 0 ? QUARTZ : ICE);
            // windows between the corner pillars
            for (int side = 0; side < 4; side++) {
                Draw.Frame g = new Draw.Frame(d, s.x, s.z, f.rot + side);
                for (int w = -9; w <= 9; w += 6) g.box(KEEP, fy + 3, w - 1, KEEP - 1, fy + 6, w + 1, Draw.of(GLASS));
            }
            for (int[] c : new int[][]{{-KEEP + 3, -KEEP + 3}, {KEEP - 3, -KEEP + 3}, {-KEEP + 3, KEEP - 3}, {KEEP - 3, KEEP - 3}})
                f.set(c[0], fy + STOREY - 1, c[1], LANTERN);
        }
        for (int[] c : new int[][]{{-KEEP, -KEEP}, {KEEP, -KEEP}, {-KEEP, KEEP}, {KEEP, KEEP}}) f.box(c[0], yb, c[1], c[0], roof, c[1], Draw.of(QZ_PILLAR));
        // the door on the gate side
        f.box(-2, yb, KEEP - 1, 2, yb + 5, KEEP, Draw.AIR);
        f.box(-3, yb + 6, KEEP, 3, yb + 6, KEEP, Draw.of(QZ_CHISELED));
        // a stair up the west side through every floor
        for (int k = 0; k < 4; k++) {
            int fy = yb + STOREY * k;
            for (int j = 0; j < STOREY; j++) {
                int v = -KEEP + 3 + j, y = fy + j;
                f.box(-KEEP + 2, y, v, -KEEP + 3, y, v, f.stair(156, 2, false));
                f.box(-KEEP + 2, fy + 1, v, -KEEP + 3, y - 1, v, Draw.of(QUARTZ));
                f.box(-KEEP + 2, y + 1, v, -KEEP + 3, y + 3, v, Draw.AIR);
            }
        }
        // storey 1: barracks
        int f1 = yb;
        for (int u = -8; u <= 8; u += 4) { f.box(u, f1 + 1, 4, u + 1, f1 + 1, 8, Draw.of(QZ_SLAB_TOP)); }
        f.chest(9, f1 + 1, -9, Draw.WEST, "jaspr:mega/citadel");
        f.chest(-8, f1 + 1, 9, Draw.EAST, "jaspr:mega/citadel");
        for (int u = 2; u <= 8; u += 2) f.box(u, f1 + 1, -10, u, f1 + 2, -10, Draw.of(FENCE));
        f.point(0, f1 + 1, 0, "garrison:wight+coolmar_spider");
        // storey 2: the rime library
        int f2 = yb + STOREY;
        for (int v = -8; v <= 8; v += 4) f.box(2, f2 + 1, v, 9, f2 + 4, v, (x, y, z) -> Draw.rnd(x, y, z, s.salt + 31) < 0.2 ? LANTERN : b(47));
        f.chest(-4, f2 + 1, 9, Draw.NORTH, "jaspr:mega/citadel");
        f.chest(-4, f2 + 1, -9, Draw.SOUTH, "jaspr:mega/citadel");
        f.point(-3, f2 + 1, 0, "garrison:wight+wight");
        // storey 3: the Rime Throne between blue fires, the vault behind it
        int f3 = yb + STOREY * 2;
        f.box(-4, f3 + 1, -10, 4, f3 + 1, -6, Draw.of(QUARTZ));
        f.box(-3, f3 + 2, -10, 3, f3 + 2, -8, Draw.of(ICE));
        f.set(0, f3 + 3, -8, f.stair(156, 3, false));
        f.box(-1, f3 + 3, -10, 1, f3 + 6, -10, Draw.of(ICE));
        f.set(0, f3 + 7, -10, LANTERN);
        f.box(-2, f3 + 3, -9, -2, f3 + 4, -9, Draw.of(ICE));
        f.box(2, f3 + 3, -9, 2, f3 + 4, -9, Draw.of(ICE));
        for (int u = -6; u <= 6; u += 12) for (int v = -6; v <= 4; v += 5) {
            f.set(u, f3 + 1, v, QZ_CHISELED); f.set(u, f3 + 2, v, RACK); f.set(u, f3 + 3, v, FIRE);
            f.point(u, f3 + 3, v, "bluefire");
        }
        f.box(-3, f3 + 1, -KEEP + 2, 3, f3 + 1, -KEEP + 2, Draw.of(QZ_CHISELED));
        f.chest(-3, f3 + 2, -KEEP + 2, Draw.SOUTH, "jaspr:mega/citadel_vault");
        f.chest(3, f3 + 2, -KEEP + 2, Draw.SOUTH, "jaspr:mega/citadel");
        f.point(0, f3 + 1, -2, "garrison:!wight+wight+wight");
        // storey 4 and the roof terrace
        int f4 = yb + STOREY * 3;
        f.chest(8, f4 + 1, 8, Draw.WEST, "jaspr:mega/citadel");
        f.box(-KEEP, roof + 1, -KEEP, KEEP, roof + 1, KEEP, (x, y, z) -> (Math.abs(x - s.x) == KEEP || Math.abs(z - s.z) == KEEP) && ((x + z) & 1) == 0 ? QUARTZ : -1);
        f.box(-KEEP + 2, roof, -KEEP + 2, -KEEP + 3, roof, -KEEP + 3 + STOREY, Draw.AIR);
        f.point(6, roof + 1, 6, "garrison:frost+frost");
        // corner spires and the great spire
        for (int[] c : new int[][]{{-KEEP + 2, -KEEP + 2}, {KEEP - 2, -KEEP + 2}, {-KEEP + 2, KEEP - 2}, {KEEP - 2, KEEP - 2}}) {
            int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
            d.cyl(x, z, 3, roof + 1, roof + 10, st);
            d.cone(x, z, 4, 0.2, roof + 11, roof + 20, Draw.of(ICE));
            d.set(x, roof + 21, z, LANTERN);
        }
        d.cone(s.x, s.z, 8, 0.4, roof + 1, Math.min(118, roof + 34), (x, y, z) -> Math.floorMod(y, 7) == 0 ? QUARTZ : Math.floorMod(y, 7) == 3 ? LANTERN : ICE);
    }

    /** Ice sculptures, a brute pen and coolmar nests in the bailey. */
    private void courtyard(Draw.Frame f, Mega.Site s, Plan p, int yb) {
        Draw d = f.d;
        for (int[] sc : p.sculptures) {
            int x = f.x(sc[0], sc[1]), z = f.z(sc[0], sc[1]);
            d.box(x - 1, yb, z - 1, x + 1, yb, z + 1, Draw.of(QZ_CHISELED));
            d.cone(x, z, 1.4, 0.3, yb + 1, yb + sc[2], Draw.of(ICE));
            d.set(x, yb + sc[2] + 1, z, LANTERN);
        }
        // the brute pen (north-east) and a coolmar nest (north-west)
        f.walls(14, yb, -25, 23, yb + 2, -15, Draw.of(FENCE));
        f.box(18, yb, -15, 19, yb + 2, -15, Draw.AIR);
        f.point(18, yb, -20, "garrison:brute");
        f.box(-24, yb - 1, -24, -15, yb - 1, -15, Draw.of(SNOW));
        f.point(-19, yb, -19, "garrison:coolmar_spider+coolmar_spider");
        f.chest(-23, yb, -23, Draw.SOUTH, "jaspr:mega/citadel");
        // lamp posts along the gate road
        for (int v = 16; v <= WALL_R - 6; v += 6) for (int w = -3; w <= 3; w += 6) {
            f.box(w, yb, v, w, yb + 3, v, Draw.of(QZ_PILLAR));
            f.set(w, yb + 4, v, LANTERN);
        }
    }

    /** An ice spike rising from the cavern floor. */
    private void spike(Draw.Frame f, Mega.Site s, int[] sp) {
        int x = f.x(sp[0], sp[1]), z = f.z(sp[0], sp[1]);
        int g = s.floorAt(x, z);
        int top = Math.min(g + sp[2], s.ceilAt(x, z) - 2);
        if (top <= g + 2) return;
        f.d.cone(x, z, sp[3], 0.2, g - 1, top, Draw.of(ICE));
    }

    @Override int ground(Mega.Site s, int x, int y, int z) {
        return Draw.noise(x, z, 9, s.salt + 81) > 0.5 ? SNOW : Blocks.FROSTBURN_ICE;
    }

    @Override Draw.Mat under() { return Draw.of(ICY_RACK); }

    @Override boolean dry(Mega.Site s, int x, int z) { return s.dist(x, z) < 58; }

    @Override double lakeLevel() { return 0.3; }

    @Override int lakeLiquid() { return WATER; }

    @Override void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        spikes(d, x, z, floor, ceil, ICE, s.salt + 61, 0.012);
        if (Draw.rnd(x, 11, z, s.salt + 62) < 0.004) d.set(x, ceil - 1, z, LANTERN);
        if (lake) {
            if (Draw.rnd(x, 12, z, s.salt + 63) < 0.12) d.set(x, floor, z, ICE);
            return;
        }
        if (s.dist(x, z) > 60 && Draw.rnd(x, 13, z, s.salt + 64) < 0.002 && d.id(x, floor, z) == (Blocks.FROSTBURN_ICE >> 4)) d.set(x, floor + 1, z, FIRE);
    }
}
