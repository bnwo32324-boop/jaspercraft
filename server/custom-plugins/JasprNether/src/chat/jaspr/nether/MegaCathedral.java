package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Spore Cathedral (Fungi Forest): a colossal elder mushroom. A spiral stair climbs its hollow, glass-slotted stem
 * to the nave inside the spotted cap, where pews face a spore altar; gills fan out beneath the cap. On the crown is
 * the Weeping Balcony with an Urn of Sorrow among weeping ghast statues: pour a Potion of Sorrow in it to summon the
 * Ghast Queen, whose open sky is the cavern dome. Chapels shelter under smaller caps around the foot, and the
 * Reliquary lies in the crypt below. Spore Creepers and Mogus keep it; its loot is spores, elder mushrooms, ghast
 * meat for the Sorrow brew, and in the Reliquary a Potion of Sorrow.
 */
final class MegaCathedral extends MegaDesign {
    static final int STEM = 8, CAP_R = 34, CAP_H = 16, RISE = 56;   // stem radius, cap radii, stem height

    static final class Plan {
        int rot;
        final List<int[]> chapels = new ArrayList<>();   // u, v, stem height, cap radius
        final List<int[]> shrooms = new ArrayList<>();   // u, v, height, red?
    }

    @Override Object plan(Mega.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        for (int i = 0; i < 4; i++) {
            double a = (i + 0.5) / 4 * Math.PI * 2 + (r.nextDouble() - 0.5) * 0.4, d = 50 + r.nextInt(8);
            p.chapels.add(new int[]{(int) (Math.cos(a) * d), (int) (Math.sin(a) * d), 11 + r.nextInt(5), 8 + r.nextInt(2)});
        }
        for (int i = 0; i < 14; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 40 + r.nextDouble() * 42;
            int u = (int) (Math.cos(a) * d), v = (int) (Math.sin(a) * d);
            boolean clash = false;
            for (int[] c : p.chapels) if (Math.hypot(c[0] - u, c[1] - v) < c[3] + 6) clash = true;
            if (!clash) p.shrooms.add(new int[]{u, v, 5 + r.nextInt(9), r.nextInt(2)});
        }
        return p;
    }

    private static final int STEM_B = b(99, 10), STEM_ALL = b(99, 15), PORES = b(99, 0), RED_CAP = b(100, 14), BROWN_CAP = b(99, 14), SPOT = b(100, 15);
    private static final int BRICK = b(201), BRICK_PILLAR = b(202), GLASS_M = b(95, 2), GLASS_P = b(95, 10), GLOW = b(89), MYCEL = b(110);
    private static final int RACK_LIVELY = b(159, 2), FENCE = b(189), QUARTZ = b(155), WHITE = b(159, 0), BLACK = b(159, 15), TEAR = b(95, 3);
    private static final int CAULDRON = b(118), SHROOM_R = b(40), SHROOM_B = b(39);

    @Override void draw(Mega.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y, yb = y0 + 1, capY = y0 + RISE;
        for (int[] sh : p.shrooms) shroom(f, s, sh);
        for (int i = 0; i < p.chapels.size(); i++) chapel(f, s, p.chapels.get(i), i);
        crypt(f, s, y0);
        stem(f, s, yb, capY);
        cap(f, s, capY);
        nave(f, s, capY);
        balcony(f, s, capY);
    }

    /** The stem: a hollow banded column with flaring roots, glass slots, two doors and a spiral stair inside. */
    private void stem(Draw.Frame f, Mega.Site s, int yb, int capY) {
        Draw d = f.d;
        Draw.Mat wall = (x, y, z) -> {
            if (Math.floorMod(y - yb, 9) == 0) return BRICK;
            double a = Math.atan2(z - s.z, x - s.x) / (Math.PI * 2) * 12;
            boolean slot = Math.abs(a - Math.round(a)) < 0.06 && Math.floorMod(y - yb, 9) >= 3 && Math.floorMod(y - yb, 9) <= 6;
            return slot ? (Math.floorMod(y, 2) == 0 ? GLASS_M : GLASS_P) : STEM_B;
        };
        d.tube(s.x, s.z, STEM, STEM, 2.2, yb, capY, wall);
        d.cyl(s.x, s.z, STEM - 2.2, yb, capY + 12, Draw.AIR);
        d.disk(s.x, s.z, STEM, yb - 1, Draw.of(BRICK));
        for (int i = 0; i < 7; i++) {
            double a = (i + 0.3) / 7 * Math.PI * 2;
            double ca = Math.cos(a), sa = Math.sin(a);
            d.line(s.x + ca * (STEM - 1), yb + 12, s.z + sa * (STEM - 1), s.x + ca * (STEM + 9), yb - 1, s.z + sa * (STEM + 9), 1.6, Draw.of(STEM_ALL));
        }
        // doors on the u axis
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * (STEM - 3), yb, -2, side * (STEM + 3), yb + 5, 2, Draw.AIR);
            f.box(side * (STEM - 3), yb + 6, -1, side * (STEM + 3), yb + 6, 1, Draw.AIR);
            f.box(side * (STEM + 1), yb + 7, -2, side * (STEM + 1), yb + 7, 2, Draw.of(BRICK));
        }
        // the central column and the spiral stair round it, up into the cap and on to the crown
        int top = capY + CAP_H - 3;
        d.cyl(s.x, s.z, 1.2, yb, top - 1, (x, y, z) -> Math.floorMod(y - yb, 7) == 6 ? GLOW : BRICK_PILLAR);
        spiral(d, s, yb, top, 1.6, 5.4, 22);
    }

    /** A spiral stair between radii r0 and r1 around the site's centre, rising {@code pitch} blocks per turn. */
    static void spiral(Draw d, Mega.Site s, int yb, int top, double r0, double r1, int pitch) {
        int xa = Math.max(d.x0, (int) Math.floor(s.x - r1)), xb = Math.min(d.x1, (int) Math.ceil(s.x + r1));
        int za = Math.max(d.z0, (int) Math.floor(s.z - r1)), zb = Math.min(d.z1, (int) Math.ceil(s.z + r1));
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double dx = x - s.x, dz = z - s.z, r = Math.sqrt(dx * dx + dz * dz);
            if (r < r0 || r > r1) continue;
            double a = Math.atan2(dz, dx);
            double turn = (a < 0 ? a + Math.PI * 2 : a) / (Math.PI * 2);
            // the stair climbs anticlockwise (towards increasing angle): its step faces along the tangent
            double tx = -Math.sin(a), tz = Math.cos(a);
            int dir = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? 0 : 1) : (tz > 0 ? 2 : 3);
            for (int k = 0; ; k++) {
                int y = yb + (int) Math.floor((turn + k) * pitch);
                if (y > top) break;
                d.set(x, y, z, Draw.stair(203, dir, false));
                if (y - 1 >= yb) d.set(x, y - 1, z, BRICK);
            }
        }
    }

    /** The cap: a spotted red dome over gills, its rim curling down. */
    private void cap(Draw.Frame f, Mega.Site s, int capY) {
        Draw d = f.d;
        // a third of the pale spots glow, so the cap shows in the dark from across the cavern
        Draw.Mat skin = (x, y, z) -> Draw.noise(x * 1.7 + y, z * 1.7 - y, 6, s.salt + 41) > 0.52
            ? (Draw.rnd(x, y, z, s.salt + 42) < 0.3 ? GLOW : SPOT) : RED_CAP;
        d.ellipsoid(s.x, capY, s.z, CAP_R, CAP_H, CAP_R, 2.2, (x, y, z) -> y < capY ? -1 : skin.at(x, y, z));
        d.tube(s.x, s.z, CAP_R, CAP_R - 1, 2, capY - 4, capY - 1, skin);
        // gills: radial pore fins under the cap
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2, ca = Math.cos(a), sa = Math.sin(a);
            d.line(s.x + ca * (STEM + 1), capY - 1, s.z + sa * (STEM + 1), s.x + ca * (CAP_R - 3), capY - 3, s.z + sa * (CAP_R - 3), 0.5, Draw.of(PORES));
            // glowing spore sacs hang under every third gill
            if (i % 3 == 0) for (int k = 0; k < 2; k++) d.set((int) Math.round(s.x + ca * (CAP_R - 6)), capY - 4 - k, (int) Math.round(s.z + sa * (CAP_R - 6)), GLOW);
        }
        d.disk(s.x, s.z, CAP_R - 2, capY, Draw.of(PORES));
    }

    /** The nave inside the cap: a brick floor, stem pillars, pews facing the spore altar, glowing spores above. */
    private void nave(Draw.Frame f, Mega.Site s, int capY) {
        Draw d = f.d;
        int fy = capY + 1;
        d.tube(s.x, s.z, CAP_R - 3, CAP_R - 3, CAP_R - 3 - (STEM - 2.2), fy - 1, fy - 1, (x, y, z) -> ((x + z) & 1) == 0 ? BRICK : RACK_LIVELY);
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) / 8 * Math.PI * 2;
            int px = s.x + (int) Math.round(Math.cos(a) * 19), pz = s.z + (int) Math.round(Math.sin(a) * 19);
            d.cyl(px, pz, 1.2, fy, capY + 13, Draw.of(STEM_ALL));
            d.set(px, fy + 5, pz, GLOW);
        }
        // hanging glow-spores from the dome
        for (int i = 0; i < 24; i++) {
            double a = i * 2.39996, r = 8 + (i * 37 % 19);
            int hx = s.x + (int) Math.round(Math.cos(a) * r), hz = s.z + (int) Math.round(Math.sin(a) * r);
            double q = r / CAP_R;
            int roof = capY + (int) Math.floor(CAP_H * Math.sqrt(Math.max(0, 1 - q * q))) - 3;
            if (roof > fy + 4) { d.set(hx, roof, hz, FENCE); d.set(hx, roof - 1, hz, GLOW); }
        }
        // pews facing north towards the altar
        for (int v = -2; v <= 12; v += 3) for (int u = -14; u <= 14; u++) {
            if (Math.abs(u) < 3 || Math.hypot(u, v) > CAP_R - 6 || Math.hypot(u, v) < STEM + 1) continue;
            f.set(u, fy, v, f.stair(203, 2, false));
        }
        // the spore altar at the north
        f.box(-4, fy, -22, 4, fy, -18, Draw.of(BRICK));
        f.box(-2, fy + 1, -21, 2, fy + 1, -20, Draw.of(BRICK_PILLAR));
        f.box(-3, fy + 1, -19, 3, fy + 1, -19, Draw.of(MYCEL));
        for (int u = -3; u <= 3; u += 2) f.set(u, fy + 2, -19, (u & 2) == 0 ? SHROOM_R : SHROOM_B);
        f.chest(0, fy + 2, -21, Draw.SOUTH, "jaspr:mega/cathedral");
        f.box(-1, fy + 1, -23, 1, fy + 6, -23, Draw.of(GLASS_M));
        f.set(0, fy + 7, -23, GLOW);
        // spore gardens in the side aisles
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 17, fy - 1, -6, side * 23, fy - 1, 6, Draw.of(MYCEL));
            for (int v = -5; v <= 5; v += 2) f.set(side * 20, fy, v, (v & 2) == 0 ? SHROOM_R : SHROOM_B);
            f.chest(side * 23, fy, 0, side > 0 ? Draw.WEST : Draw.EAST, "jaspr:mega/cathedral");
        }
        f.point(0, fy, 14, "garrison:spore_creeper+spore");
        f.point(-12, fy, -10, "garrison:spore_creeper+spore_creeper");
    }

    /** The Weeping Balcony on the crown: the Urn of Sorrow among four weeping ghasts. */
    private void balcony(Draw.Frame f, Mega.Site s, int capY) {
        Draw d = f.d;
        int py = capY + CAP_H - 2;
        d.disk(s.x, s.z, 10, py, Draw.of(BRICK));
        d.cyl(s.x, s.z, 10, py + 1, py + 12, Draw.AIR);
        d.ring(s.x, s.z, 10.4, 1, py + 1, py + 1, Draw.of(FENCE));
        d.ring(s.x, s.z, 10.6, 0.8, py, py, Draw.of(BRICK_PILLAR));
        // the stair's mouth: the floor opens over its last turn
        spiralMouth(d, s, capY + 1 - RISE, py - 1, 1.6, 5.4, 22, py);
        // the urn on a pedestal
        f.box(-1, py + 1, -1, 1, py + 1, 1, Draw.of(QUARTZ));
        f.set(0, py + 2, 0, CAULDRON);
        f.point(0, py + 2, 0, "urn");
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) ghast(f, cu * 6, py + 1, cv * 6);
        f.point(-5, py + 1, 5, "garrison:ghastling");
    }

    /** A weeping ghast statue facing the urn: a white cube, black eyes, glass tears running down. */
    private void ghast(Draw.Frame f, int u, int y, int v) {
        f.box(u - 1, y, v - 1, u + 1, y + 2, v + 1, Draw.of(WHITE));
        f.set(u, y + 3, v, WHITE);
        int du = u > 0 ? -1 : 1, dv = v > 0 ? -1 : 1;
        // the two faces towards the urn each get a pair of eyes with tears below
        for (int e = -1; e <= 1; e += 2) {
            f.set(u + du, y + 2, v + e, BLACK); f.set(u + du, y + 1, v + e, TEAR);
            f.set(u + e, y + 2, v + dv, BLACK); f.set(u + e, y + 1, v + dv, TEAR);
        }
    }

    /** Opens the platform at {@code py} over the spiral's last steps, so the stair comes out on top. */
    static void spiralMouth(Draw d, Mega.Site s, int yb, int top, double r0, double r1, int pitch, int py) {
        int xa = Math.max(d.x0, (int) Math.floor(s.x - r1)), xb = Math.min(d.x1, (int) Math.ceil(s.x + r1));
        int za = Math.max(d.z0, (int) Math.floor(s.z - r1)), zb = Math.min(d.z1, (int) Math.ceil(s.z + r1));
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double dx = x - s.x, dz = z - s.z, r = Math.sqrt(dx * dx + dz * dz);
            if (r < r0 || r > r1) continue;
            double a = Math.atan2(dz, dx), turn = (a < 0 ? a + Math.PI * 2 : a) / (Math.PI * 2);
            for (int k = 0; ; k++) {
                int y = yb + (int) Math.floor((turn + k) * pitch);
                if (y > top) break;
                if (y >= top - 3) { d.set(x, py, z, 0); d.set(x, py + 1, z, 0); }
            }
        }
    }

    /** A chapel under a smaller brown cap: a stem, a pillared room with an altar chest. */
    private void chapel(Draw.Frame f, Mega.Site s, int[] c, int idx) {
        Draw d = f.d;
        int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
        int g = s.floorAt(x, z), top = g + c[2], rc = c[3];
        d.disk(x, z, rc - 1, g, Draw.of(BRICK));
        d.cyl(x, z, 1.6, g + 1, top, Draw.of(STEM_ALL));
        d.ellipsoid(x, top, z, rc, 3, rc, 1.2, (xx, y, zz) -> y < top ? -1 : BROWN_CAP);
        d.disk(x, z, rc - 1, top, Draw.of(PORES));
        d.tube(x, z, rc, rc, 1, top - 2, top - 1, Draw.of(BROWN_CAP));
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            int px = x + (int) Math.round(Math.cos(a) * (rc - 2)), pz = z + (int) Math.round(Math.sin(a) * (rc - 2));
            d.box(px, g + 1, pz, px, top - 1, pz, Draw.of(BRICK_PILLAR));
        }
        d.set(x + 2, g + 1, z, BRICK);
        d.chest(x + 2, g + 2, z, Draw.WEST, "jaspr:mega/cathedral");
        d.set(x, top - 1, z + 2, GLOW);
        d.set(x - 2, g + 1, z - 2, SHROOM_R);
        if ((idx & 1) == 0) d.point(x - 3, g + 1, z + 3, "garrison:mogus+mogus");
        for (int xx = x - rc; xx <= x + rc; xx++) for (int zz = z - rc; zz <= z + rc; zz++)
            if (Math.hypot(xx - x, zz - z) < rc - 0.5) d.foundation(xx, g - 1, zz, 6, Draw.of(RACK_LIVELY));
    }

    /** The Reliquary: a round crypt under the stem, reached by a ladder beside the central column. */
    private void crypt(Draw.Frame f, Mega.Site s, int y0) {
        Draw d = f.d;
        int fl = y0 - 9;
        d.cyl(s.x, s.z, 11, fl - 1, y0, Draw.of(BRICK));
        d.cyl(s.x, s.z, 10, fl, y0 - 2, Draw.AIR);
        d.disk(s.x, s.z, 10, fl - 1, (x, y, z) -> ((x + z) & 1) == 0 ? BRICK : RACK_LIVELY);
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            int px = s.x + (int) Math.round(Math.cos(a) * 7), pz = s.z + (int) Math.round(Math.sin(a) * 7);
            d.box(px, fl, pz, px, y0 - 2, pz, Draw.of(STEM_ALL));
            d.set(px, y0 - 3, pz, GLOW);
        }
        // the way down: a stair from the cavern floor south of the stem, through an arch
        for (int k = 0; k <= 9; k++) {
            int v = 22 - k, y = y0 - k;
            f.box(-2, y - 1, v, 2, y + 4, v, Draw.of(BRICK));
            f.box(-1, y, v, 1, y, v, f.stair(203, 2, false));
            f.box(-1, y + 1, v, 1, y + 3, v, Draw.AIR);
        }
        f.box(-2, fl - 1, 10, 2, fl + 4, 13, Draw.of(BRICK));
        f.box(-1, fl, 9, 1, fl + 3, 13, Draw.AIR);
        f.box(-2, y0 + 1, 22, 2, y0 + 5, 24, Draw.of(BRICK));
        f.box(-1, y0 + 1, 22, 1, y0 + 3, 24, Draw.AIR);
        f.set(0, y0 + 5, 23, GLOW);
        // relics
        f.box(-9, fl, -2, -8, fl, 2, Draw.of(BRICK_PILLAR));
        f.chest(-7, fl, 0, Draw.EAST, "jaspr:mega/cathedral_vault");
        f.chest(0, fl, 8, Draw.NORTH, "jaspr:mega/cathedral");
        f.box(-2, fl, -9, 2, fl, -8, Draw.of(MYCEL));
        f.set(0, fl + 1, -8, SHROOM_R);
        f.point(0, fl, 3, "garrison:!spore_creeper+spore");
    }

    /** A free-standing elder mushroom (red bulb or brown flat cap) in the fields round the cathedral. */
    private void shroom(Draw.Frame f, Mega.Site s, int[] sh) {
        Draw d = f.d;
        int x = f.x(sh[0], sh[1]), z = f.z(sh[0], sh[1]);
        int g = s.floorAt(x, z), top = g + sh[2];
        d.box(x, g + 1, z, x, top, z, Draw.of(STEM_B));
        int r = 2 + sh[2] / 5;
        if (sh[3] == 1) d.ellipsoid(x, top, z, r, r * 0.8, r, 1, (xx, y, zz) -> y < top - 1 ? -1 : RED_CAP);
        else { d.disk(x, z, r + 0.6, top + 1, Draw.of(BROWN_CAP)); d.set(x, top + 1, z, BROWN_CAP); }
    }

    @Override int ground(Mega.Site s, int x, int y, int z) {
        return s.f(x, z) > 0.88 ? RACK_LIVELY : MYCEL;
    }

    @Override Draw.Mat under() { return Draw.of(RACK_LIVELY); }

    @Override boolean dry(Mega.Site s, int x, int z) { return s.dist(x, z) < 46; }

    @Override double lakeLevel() { return 0.5; }

    @Override void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        glowHang(d, x, z, ceil, s.salt + 51, 0.005);
        // enoki strands hanging from the roof
        if (Draw.rnd(x, 7, z, s.salt + 53) < 0.012 && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {
            int len = 2 + (int) (Draw.rnd(x, 8, z, s.salt) * 7);
            for (int k = 0; k < len && ceil - k > floor + 4; k++) d.set(x, ceil - k, z, k == len - 1 ? Blocks.ENOKI_CAP : Blocks.ENOKI_STEM);
        }
        if (lake || s.dist(x, z) < 20) return;
        double q = Draw.rnd(x, 9, z, s.salt + 54);
        if (q < 0.03) d.set(x, floor + 1, z, q < 0.015 ? SHROOM_R : SHROOM_B);
    }
}
