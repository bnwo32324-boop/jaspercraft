package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Cinder Forge (Torrid Wasteland): a basalt foundry hall under a gabled brick roof, three chimneys rising into
 * the cavern roof, a great obsidian crucible pouring lava into casting channels, blaze furnaces in the back wall, a
 * lava aqueduct striding in on arches from the cavern wall, storerooms, a slag pit of magma cubes and the master
 * smith's strongroom above the gate. Salamanders and Embers work it; its loot is hides, blaze stock, magma and
 * cincinnasite.
 */
final class MegaForge extends MegaDesign {
    static final int HU = 30, HV = 16, WALL = 22;   // hall half-length, half-width and wall height
    static final int AQ = 9;                          // the aqueduct runs beside the gate, at this v

    static final class Plan {
        int rot;
        final List<int[]> heaps = new ArrayList<>();    // u, v, r
        final List<int[]> falls = new ArrayList<>();    // u, v
    }

    @Override Object plan(Mega.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 50 + r.nextDouble() * 30;
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            if (u > 30 && Math.abs(v - AQ) < 9) continue;   // the aqueduct's line
            p.heaps.add(new int[]{u, v, 3 + r.nextInt(4)});
        }
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 45 + r.nextDouble() * 35;
            p.falls.add(new int[]{(int) (Math.cos(a) * d), (int) (Math.sin(a) * d)});
        }
        return p;
    }

    private static final int BASALT = b(159, 15), FIERY = b(179, 2), FIERY_RAW = b(159, 1), NB = b(112), RNB = b(215), NB_FENCE = b(113);
    private static final int OBSIDIAN = b(49), LAVA = b(11), LAVA_FALL = b(11, 8), MAGMA = b(213), GLOW = b(89), RACK = b(87), FIRE = b(51);
    private static final int GLASS = b(95, 1), FURNACE = 61, TABLE = b(58), NB_SLAB = b(44, 6), NB_SLAB_TOP = b(44, 14);

    @Override void draw(Mega.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y, yb = y0 + 1;
        Draw.Mat basalt = Draw.mix(BASALT, b(159, 7), 0.08, s.salt + 5);
        // footing
        f.box(-HU - 3, y0 - 5, -HV - 14, HU + 3, y0, HV + 14, Draw.of(NB));
        hall(f, s, yb, basalt);
        chimneys(f, s, yb);
        crucible(f, s, yb);
        annexes(f, s, yb, basalt);
        aqueduct(f, s, y0);
        slagPit(f, s, y0);
        for (int[] h : p.heaps) heap(f, s, h);
        for (int[] fl : p.falls) lavafall(f, s, fl);
    }

    private void hall(Draw.Frame f, Mega.Site s, int yb, Draw.Mat basalt) {
        int top = yb + WALL;
        f.box(-HU, yb, -HV, HU, top, HV, basalt);
        f.box(-HU + 2, yb, -HV + 2, HU - 2, top, HV - 2, Draw.AIR);
        f.walls(-HU, yb, -HV, HU, yb + 1, HV, Draw.of(NB));
        f.walls(-HU - 1, top, -HV - 1, HU + 1, top, HV + 1, Draw.of(FIERY));
        // pilasters on the long walls, tall windows between them
        for (int u = -HU; u <= HU; u += 6) for (int side = -1; side <= 1; side += 2) {
            f.box(u, yb, side * (HV + 1), u + 1, top - 1, side * (HV + 1), Draw.of(FIERY));
            f.set(u, yb + 11, side * (HV + 1), GLOW);
            f.set(u + 1, top - 1, side * (HV + 1), GLOW);
            if (u + 3 < HU && Math.abs(u + 3) > 3) f.box(u + 3, yb + 6, side * HV, u + 4, yb + 16, side * (HV - 1), Draw.of(GLASS));
        }
        // gabled roof: brick stairs climbing to a glowing ridge
        for (int k = 0; k <= HV + 1; k++) {
            int y = top + 1 + k / 2;
            for (int side = -1; side <= 1; side += 2) {
                int v = side * (HV + 1 - k);
                boolean half = (k & 1) == 1;
                f.box(-HU - 1, y, v, HU + 1, y, v, half ? f.stair(114, side > 0 ? 3 : 2, false) : NB);
                if (half) f.box(-HU - 1, y - 1, v, HU + 1, y - 1, v, Draw.of(NB));
            }
        }
        int ridge = top + 1 + (HV + 1) / 2;
        f.box(-HU - 1, ridge, 0, HU + 1, ridge, 0, (x, y, z) -> Math.floorMod(x + z, 7) == 0 ? GLOW : NB);
        // gable walls
        for (int side = -1; side <= 1; side += 2) for (int k = 0; k <= HV; k++) {
            int y = top + 1 + k / 2;
            f.box(side * HU, top + 1, -(HV - k), side * HU, y, HV - k, basalt);
        }
        // the great gate at the east end, a side door on the south
        f.box(HU - 2, yb, -3, HU + 1, yb + 9, 3, Draw.AIR);
        f.box(HU - 2, yb + 10, -2, HU + 1, yb + 10, 2, Draw.AIR);
        f.box(HU + 1, yb + 11, -4, HU + 1, yb + 11, 4, Draw.of(FIERY));
        f.box(HU + 1, yb, -4, HU + 1, yb + 10, -4, Draw.of(FIERY));
        f.box(HU + 1, yb, 4, HU + 1, yb + 10, 4, Draw.of(FIERY));
        f.box(-4, yb, HV - 2, -2, yb + 3, HV + 1, Draw.AIR);
        // floor: brick with a lava casting channel along the hall's axis
        f.box(-HU + 2, yb - 1, -HV + 2, HU - 2, yb - 1, HV - 2, (x, y, z) -> Draw.rnd(x, y, z, s.salt + 3) < 0.1 ? MAGMA : NB);
        f.box(-HU + 4, yb - 1, 0, HU - 6, yb - 1, 0, Draw.of(LAVA));
        f.box(-HU + 4, yb - 2, 0, HU - 6, yb - 2, 0, Draw.of(OBSIDIAN));
        for (int u = -24; u <= 20; u += 11) {
            f.box(u - 1, yb - 1, 2, u + 1, yb - 1, 4, Draw.of(LAVA));
            f.box(u - 1, yb - 1, -4, u + 1, yb - 1, -2, Draw.of(LAVA));
            f.box(u - 1, yb - 2, -4, u + 1, yb - 2, 4, Draw.of(OBSIDIAN));
        }
        // workbenches along the south wall: furnaces, tables, chests
        for (int u = -HU + 4; u <= HU - 8; u += 5) {
            f.set(u, yb, HV - 3, b(FURNACE, f.facing(Draw.NORTH)));
            f.set(u + 1, yb, HV - 3, b(FURNACE, f.facing(Draw.NORTH)));
            f.set(u + 2, yb, HV - 3, TABLE);
            f.set(u, yb + 1, HV - 3, b(FURNACE, f.facing(Draw.NORTH)));
        }
        f.chest(-HU + 3, yb, HV - 3, Draw.NORTH, "jaspr:mega/forge");
        f.chest(HU - 4, yb, -HV + 3, Draw.SOUTH, "jaspr:mega/forge");
        // catwalks along the long walls, a bridge over the crucible, stairs up in two corners
        int cy = yb + 12;
        for (int side = -1; side <= 1; side += 2) {
            f.box(-HU + 2, cy, side * (HV - 3), HU - 2, cy, side * (HV - 2), Draw.of(NB_SLAB_TOP));
            f.box(-HU + 2, cy + 1, side * (HV - 4), HU - 2, cy + 1, side * (HV - 4), Draw.of(NB_FENCE));
        }
        f.box(-1, cy + 4, -HV + 2, 1, cy + 4, HV - 2, Draw.of(NB_SLAB_TOP));
        f.box(-2, cy + 5, -HV + 2, -2, cy + 5, HV - 2, Draw.of(NB_FENCE));
        f.box(2, cy + 5, -HV + 2, 2, cy + 5, HV - 2, Draw.of(NB_FENCE));
        for (int side = -1; side <= 1; side += 2) for (int k = 0; k < 4; k++) {
            int v = side * (HV - 4 - k), y = cy + 1 + k;
            f.box(-1, y, v, 1, y, v, f.stair(114, side > 0 ? 3 : 2, false));
            f.box(-1, y + 1, v, 1, Math.max(y + 2, cy + 4), v, Draw.AIR);
        }
        for (int k = 0; k <= 12; k++) {
            int u = -HU + 3 + k, y = yb + k;
            f.box(u, y, -HV + 2, u, y, -HV + 3, f.stair(114, 0, false));
            f.box(u, yb, -HV + 2, u, y - 1, -HV + 3, Draw.of(NB));
            f.box(u, y + 1, -HV + 2, u, y + 3, -HV + 3, Draw.AIR);
        }
        // blaze furnaces in the north wall, the heart furnace between them
        for (int u = -20; u <= 20; u += 20) {
            f.box(u - 3, yb, -HV - 4, u + 3, yb + 6, -HV + 1, Draw.of(RNB));
            f.box(u - 2, yb, -HV - 3, u + 2, yb + 5, -HV + 1, Draw.AIR);
            f.box(u - 2, yb - 1, -HV - 3, u + 2, yb - 1, -HV + 1, Draw.of(RACK));
            if (u == 0) {
                f.box(-2, yb - 1, -HV - 3, 2, yb - 1, -HV - 2, Draw.of(LAVA));
                f.box(-2, yb + 5, -HV - 3, 2, yb + 5, -HV + 1, Draw.of(MAGMA));
                f.point(0, yb, -HV + 3, "garrison:salamander+ember");
            } else {
                f.spawner(u, yb + 1, -HV - 3, "blaze");
                f.set(u - 2, yb, -HV - 3, FIRE); f.set(u + 2, yb, -HV - 3, FIRE);
                f.box(u - 2, yb, -HV + 1, u + 2, yb + 3, -HV + 1, Draw.of(NB_FENCE));
                f.set(u, yb, -HV + 1, 0); f.set(u, yb + 1, -HV + 1, 0);
            }
        }
        f.point(-14, yb, 6, "garrison:ember+magma_cube");
        f.point(14, yb, -6, "garrison:ember+magma_cube");
        f.point(0, cy + 1, HV - 3, "garrison:ember+ember");
        // the master smith's strongroom above the gate, off the catwalks
        f.box(HU - 8, cy, -12, HU - 2, cy + 6, 12, Draw.of(FIERY));
        f.box(HU - 7, cy + 1, -11, HU - 3, cy + 5, 11, Draw.AIR);
        f.box(HU - 5, cy + 1, -12, HU - 5, cy + 2, -12, Draw.AIR);
        f.box(HU - 5, cy + 1, 12, HU - 5, cy + 2, 12, Draw.AIR);
        f.box(HU - 8, cy + 3, -4, HU - 8, cy + 4, 4, Draw.of(b(101)));
        f.set(HU - 3, cy + 5, 0, GLOW);
        f.set(HU - 3, cy + 5, -8, GLOW);
        f.set(HU - 3, cy + 5, 8, GLOW);
        f.chest(HU - 3, cy + 1, 0, Draw.WEST, "jaspr:mega/forge_vault");
        f.chest(HU - 3, cy + 1, 4, Draw.WEST, "jaspr:mega/forge");
        f.point(HU - 5, cy + 1, -2, "garrison:!salamander+ember");
    }

    /** Three chimneys behind the hall, banded brick up into the cavern roof, each with a stoking mouth. */
    private void chimneys(Draw.Frame f, Mega.Site s, int yb) {
        Draw d = f.d;
        for (int u = -20; u <= 20; u += 20) {
            int v = -HV - 10;
            int x = f.x(u, v), z = f.z(u, v);
            int top = Math.min(118, s.ceilAt(x, z) + 3);
            d.tube(x, z, 5, 5, 2, yb, top, Draw.bands(Draw.of(RNB), Draw.of(BASALT), yb, 6));
            d.tube(x, z, 3.2, 3.2, 0, yb, top, Draw.AIR);
            Draw.Mat collar = (xx, y, zz) -> ((xx + zz) & 1) == 0 && y % 2 == 0 ? GLOW : FIERY;
            d.ring(x, z, 6.2, 1.2, yb + 30, yb + 31, collar);
            d.ring(x, z, 6.2, 1.2, yb + 58, yb + 59, collar);
            d.disk(x, z, 3.2, yb - 1, Draw.of(RACK));
            d.disk(x, z, 2.2, yb, Draw.of(FIRE));
            // flue to the furnace in the hall's back wall
            f.box(u - 1, yb, v + 4, u + 1, yb + 3, -HV - 4, Draw.AIR);
        }
    }

    /** The great crucible on four legs, brimming with lava, pouring into the casting channel. */
    private void crucible(Draw.Frame f, Mega.Site s, int yb) {
        Draw d = f.d;
        int cy = yb + 10;
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) f.box(cu * 5, yb, cv * 5, cu * 5 + cu, cy, cv * 5 + cv, Draw.of(OBSIDIAN));
        d.ellipsoid(s.x, cy + 4, s.z, 7, 6, 7, 1.5, (x, y, z) -> y > cy + 4 ? -1 : ((y - cy) % 3 == 0 ? NB : OBSIDIAN));
        d.ellipsoid(s.x, cy + 4, s.z, 5.6, 4.8, 5.6, 0, (x, y, z) -> y > cy + 3 ? -1 : LAVA);
        d.ring(s.x, s.z, 7.2, 1.2, cy + 4, cy + 4, Draw.of(NB));
        // the pour: a sheet of lava from the lip to the channel
        int pu = 7;
        f.box(pu, cy + 4, 0, pu, cy + 4, 0, NB_SLAB);
        f.box(pu, yb, 0, pu, cy + 3, 0, Draw.of(LAVA_FALL));
        f.set(pu, yb - 1, 0, LAVA);
    }

    /** Storerooms against the south wall: the ingot store and the tannery. */
    private void annexes(Draw.Frame f, Mega.Site s, int yb, Draw.Mat basalt) {
        for (int side = -1; side <= 1; side += 2) {
            int u0 = side < 0 ? -26 : 8, u1 = side < 0 ? -8 : 26;
            f.box(u0, yb, HV + 1, u1, yb + 7, HV + 11, basalt);
            f.box(u0 + 1, yb, HV + 2, u1 - 1, yb + 6, HV + 10, Draw.AIR);
            f.box(u0 - 1, yb + 8, HV, u1 + 1, yb + 8, HV + 12, Draw.of(NB_SLAB));
            f.box(u0, yb - 1, HV + 2, u1, yb - 1, HV + 10, Draw.of(NB));
            int mid = (u0 + u1) / 2;
            f.box(mid - 1, yb, HV + 11, mid + 1, yb + 3, HV + 11, Draw.AIR);
            f.box(mid - 1, yb, HV + 1, mid + 1, yb + 3, HV + 1, Draw.AIR);
            f.box(mid - 1, yb, HV, mid + 1, yb + 3, HV, Draw.AIR);
            f.set(mid, yb + 6, HV + 6, GLOW);
            f.chest(u0 + 1, yb, HV + 10, Draw.NORTH, "jaspr:mega/forge");
            f.chest(u1 - 1, yb, HV + 10, Draw.NORTH, "jaspr:mega/forge");
            for (int u = u0 + 3; u <= u1 - 3; u += 3) { f.set(u, yb, HV + 10, side < 0 ? BASALT : b(35, 1)); f.set(u, yb + 1, HV + 10, side < 0 ? NB_SLAB : b(35, 12)); }
            f.point(mid, yb, HV + 5, "garrison:salamander+ember");
        }
    }

    /** A lava aqueduct striding in from the cavern wall on arches and spilling into a quench pool at the gate. */
    private void aqueduct(Draw.Frame f, Mega.Site s, int y0) {
        Draw d = f.d;
        int deck = y0 + 26, v = AQ;
        // it runs from the quench pool out to where the cavern wall swallows it
        int end = HU + 12;
        while (end < Mega.REACH - 4) {
            int x = f.x(end, v), z = f.z(end, v);
            if (s.f(x, z) > 0.97 || s.ceilAt(x, z) < deck + 3) break;
            end++;
        }
        end += 4;
        f.box(HU + 12, deck - 1, v - 2, end, deck - 1, v + 2, Draw.of(NB));
        f.box(HU + 12, deck, v - 2, end, deck, v + 2, Draw.of(NB));
        f.box(HU + 12, deck, v - 1, end, deck, v + 1, Draw.of(LAVA));
        f.box(HU + 12, deck + 1, v - 2, end, deck + 1, v - 2, Draw.of(FIERY));
        f.box(HU + 12, deck + 1, v + 2, end, deck + 1, v + 2, Draw.of(FIERY));
        for (int u = HU + 12; u <= end - 4; u += 12) {
            int x = f.x(u, v), z = f.z(u, v);
            int g = s.floorAt(x, z);
            f.box(u, g - 2, v - 2, u + 1, deck - 2, v + 2, Draw.of(BASALT));
            if (u + 12 <= end - 4) for (int w = -2; w <= 2; w++) d.arch(f.xd(u + 1, v + w), f.zd(u + 1, v + w), f.xd(u + 12, v + w), f.zd(u + 12, v + w), deck - 8, 6, 0.5, 1.5, Draw.of(RNB));
        }
        // the spill: a falling sheet into a quench pool beside the gate
        f.box(HU + 11, deck, v - 1, HU + 11, deck, v + 1, Draw.of(FIERY));
        f.box(HU + 12, y0 + 1, v - 1, HU + 12, deck - 2, v + 1, Draw.of(LAVA_FALL));
        f.box(HU + 9, y0 - 2, v - 4, HU + 17, y0 - 2, v + 4, Draw.of(OBSIDIAN));
        f.box(HU + 9, y0 - 1, v - 4, HU + 17, y0, v + 4, Draw.of(NB));
        f.box(HU + 10, y0 - 1, v - 3, HU + 16, y0, v + 3, Draw.of(LAVA));
        f.walls(HU + 9, y0 + 1, v - 4, HU + 17, y0 + 1, v + 4, Draw.of(NB_FENCE));
        f.box(HU + 2, y0, -3, HU + 8, y0, 3, Draw.of(NB));
        f.box(HU + 2, y0 + 1, -3, HU + 8, y0 + 5, 3, Draw.AIR);
        f.point(HU + 20, y0 + 1, 8, "garrison:salamander+salamander");
    }

    /** The slag pit west of the hall: a magma-crusted hollow with a magma cube cage. */
    private void slagPit(Draw.Frame f, Mega.Site s, int y0) {
        Draw d = f.d;
        int u = -HU - 16, v = 0;
        int x = f.x(u, v), z = f.z(u, v);
        d.ellipsoid(x, y0, z, 8, 5, 8, 0, (xx, y, zz) -> y > y0 ? -1 : 0);
        d.ellipsoid(x, y0, z, 8, 5, 8, 1.5, (xx, y, zz) -> y > y0 ? -1 : Draw.rnd(xx, y, zz, s.salt + 9) < 0.35 ? MAGMA : BASALT);
        d.disk(x, z, 3, y0 - 4, Draw.of(LAVA));
        d.ring(x, z, 9, 1, y0 + 1, y0 + 1, Draw.of(NB_FENCE));
        d.spawner(x, y0 - 3, z + 4, "magma_cube");
        f.point(u, y0 - 3, v - 3, "garrison:magma_cube+magma_cube");
    }

    /** A slag heap: a mound of magma, basalt and rack. */
    private void heap(Draw.Frame f, Mega.Site s, int[] h) {
        int x = f.x(h[0], h[1]), z = f.z(h[0], h[1]);
        int g = s.floorAt(x, z);
        f.d.ellipsoid(x, g, z, h[2], h[2] * 0.7, h[2], 0, (xx, y, zz) -> {
            double q = Draw.rnd(xx, y, zz, s.salt + 17);
            return q < 0.3 ? MAGMA : q < 0.65 ? BASALT : FIERY_RAW;
        });
    }

    /** A lava fall from the cavern roof into a pool on the floor. */
    private void lavafall(Draw.Frame f, Mega.Site s, int[] fl) {
        int x = f.x(fl[0], fl[1]), z = f.z(fl[0], fl[1]);
        if (!f.d.in(x, z)) return;
        int g = s.floorAt(x, z), c = s.ceilAt(x, z);
        if (c - g < 8 || !Blocks.isFullSolid(f.d.id(x, c + 1, z))) return;
        f.d.set(x, c, z, LAVA);
        for (int y = g + 1; y < c; y++) f.d.set(x, y, z, LAVA_FALL);
        f.d.set(x, g, z, LAVA);
        f.d.set(x, g - 1, z, OBSIDIAN);
    }

    @Override int ground(Mega.Site s, int x, int y, int z) {
        double n = Draw.fbm(x, z, 16, s.salt + 71), m = Draw.noise(x, z, 7, s.salt + 72);
        if (n > 0.3) return MAGMA;
        if (m > 0.55) return BASALT;
        return FIERY_RAW;
    }

    @Override Draw.Mat under() { return Draw.mix(FIERY_RAW, BASALT, 0.2, 5519); }

    @Override boolean dry(Mega.Site s, int x, int z) { return s.dist(x, z) < 44; }

    @Override double lakeLevel() { return 0.3; }

    @Override void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        spikes(d, x, z, floor, ceil, BASALT, s.salt + 61, 0.01);
        if (lake || s.dist(x, z) < 42) return;
        double q = Draw.rnd(x, 5, z, s.salt + 62);
        if (q < 0.006) {
            // a basalt column
            int h = 3 + (int) (Draw.rnd(x, 6, z, s.salt) * 8);
            for (int k = 1; k <= h && floor + k < ceil - 2; k++) d.set(x, floor + k, z, BASALT);
        } else if (q < 0.012) {
            d.set(x, floor, z, RACK);
            d.set(x, floor + 1, z, FIRE);
        }
    }
}
