package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Soul Pyramid (Ruthless Sands): a nine-tier soul-sandstone ziggurat half-buried in soul-sand dunes. Grand stairs
 * climb two faces to an obsidian obelisk; portals flanked by Soul Wardens open the other two onto a gallery, a ring
 * corridor with burial chambers, and the Hall of Kings where the Soul King lies. A ladder shaft drops from the obelisk
 * into the hall. Wither skeletons and Spinouts keep it; its loot is wither bones and temple treasure.
 */
final class MegaPyramid extends MegaDesign {
    static final int B = 38, TIERS = 9;

    static final class Plan {
        int rot;
        final List<int[]> fossils = new ArrayList<>();     // u, v, length, angle(0..3)
        final boolean[] roomChest = new boolean[8];
    }

    @Override Object plan(Mega.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(2);
        for (int i = 0; i < 4; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 58 + r.nextDouble() * 22;
            p.fossils.add(new int[]{(int) (Math.cos(a) * dist), (int) (Math.sin(a) * dist), 12 + r.nextInt(8), r.nextInt(4)});
        }
        for (int i = 0; i < 8; i++) p.roomChest[i] = r.nextInt(4) != 0;
        return p;
    }

    private static final int SANDSTONE = b(179, 0), CHISELED = b(179, 1), SMOOTH = b(179, 2), GLOOMY = b(159, 12), BASALT = b(159, 15);
    private static final int SOUL = b(88), OBSIDIAN = b(49), GLOW = b(89), QUARTZ_PILLAR = b(155, 2), BONE_Y = b(216, 0), BONE_X = b(216, 4), BONE_Z = b(216, 8);
    private static final int RACK = b(87), FIRE = b(51), FENCE = b(113), THORN_MAT = b(191), SLAB_TOP = b(182, 8);

    private static Draw.Mat body(int salt) {
        return (x, y, z) -> {
            double q = Draw.rnd(x, y, z, salt);
            return q < 0.09 ? GLOOMY : q < 0.115 ? SOUL : SANDSTONE;
        };
    }

    @Override void draw(Mega.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y, yb = y0 + 1;
        Draw.Mat body = body(s.salt);
        // foundation under the whole base, so dunes and lava never undercut it
        f.box(-B - 2, y0 - 6, -B - 2, B + 2, y0, B + 2, Draw.of(GLOOMY));
        // the tiers: soul sandstone, a basalt course at each foot and a chiseled course at each lip, smooth corners
        for (int k = 0; k < TIERS; k++) {
            int h = B - 4 * k, ya = yb + 4 * k, yt = ya + 3;
            f.box(-h, ya, -h, h, yt, h, body);
            f.walls(-h, ya, -h, h, ya, h, Draw.of(BASALT));
            f.walls(-h, yt, -h, h, yt, h, Draw.of(CHISELED));
            for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
                f.box(cu * h, ya, cv * h, cu * h, yt, cv * h, SMOOTH);
                f.set(cu * h, yt, cv * h, GLOW);   // lamps on every corner trace the pyramid in the dark
            }
        }
        int top = yb + 4 * TIERS - 1;      // surface of the top tier
        stairs(f, yb, top, body);
        portals(f, s, yb, body);
        interior(f, s, yb, top);
        summit(f, yb, top);
        grounds(f, s, p, yb);
    }

    /** Grand stairs up the two v faces, between smooth balustrades. */
    private void stairs(Draw.Frame f, int yb, int top, Draw.Mat body) {
        for (int side = -1; side <= 1; side += 2) {
            int dir = side > 0 ? 3 : 2;   // ascending towards the centre
            for (int pp = 7; pp <= B + 3; pp++) {
                int h = yb + 3 + (B - pp);
                if (h > top) continue;
                int v = side * pp;
                f.box(-3, yb, v, 3, h - 1, v, body);
                for (int w = -3; w <= 3; w++) f.set(w, h, v, f.stair(180, dir, false));
                f.box(-4, yb, v, -4, h + 1, v, SMOOTH);
                f.box(4, yb, v, 4, h + 1, v, SMOOTH);
                if ((pp & 3) == 0) { f.set(-4, h + 2, v, GLOW); f.set(4, h + 2, v, GLOW); }
            }
        }
    }

    /** Portals on the u faces: a pylon gatehouse, an obsidian-framed opening and two Soul Wardens. */
    private void portals(Draw.Frame f, Mega.Site s, int yb, Draw.Mat body) {
        for (int side = -1; side <= 1; side += 2) {
            int face = side * B;
            // pylon: stepped block standing proud of the face
            for (int k = 0; k < 4; k++) f.box(face - side * 2, yb + k * 3, -7 + k, face + side * (5 - k), yb + k * 3 + 2, 7 - k, body);
            f.box(face + side * 5, yb, -7, face + side * 5, yb + 2, 7, Draw.of(BASALT));
            // opening
            f.box(face - side * 3, yb, -2, face + side * 6, yb + 6, 2, Draw.AIR);
            f.box(face + side * 5, yb, -3, face + side * 5, yb + 7, -3, OBSIDIAN);
            f.box(face + side * 5, yb, 3, face + side * 5, yb + 7, 3, OBSIDIAN);
            f.box(face + side * 5, yb + 7, -3, face + side * 5, yb + 7, 3, OBSIDIAN);
            f.box(face + side * 5, yb + 8, -1, face + side * 5, yb + 8, 1, CHISELED);
            // causeway out across the dunes
            f.box(face + side * 6, yb - 1, -2, face + side * 36, yb - 1, 2, Draw.of(SMOOTH));
            f.box(face + side * 6, yb, -2, face + side * 36, yb + 2, 2, Draw.AIR);
            for (int t = 6; t <= 36; t++) for (int w = -2; w <= 2; w++) f.d.foundation(f.x(face + side * t, w), yb - 2, f.z(face + side * t, w), 7, Draw.of(GLOOMY));
            for (int t = 10; t <= 30; t += 10) {
                int u = face + side * t;
                warden(f, u, yb, -5, side);
                warden(f, u, yb, 5, side);
            }
            f.point(face + side * 9, yb, 0, "garrison:wither_skeleton+spinout");
        }
    }

    /** A Soul Warden: a smooth plinth, a quartz body, bone shoulders and a skull. */
    private void warden(Draw.Frame f, int u, int yb, int v, int side) {
        f.box(u - 1, yb - 1, v - 1, u + 1, yb, v + 1, SMOOTH);
        f.box(u, yb + 1, v, u, yb + 2, v, QUARTZ_PILLAR);
        f.set(u, yb + 3, v, BONE_Y);
        f.set(u, yb + 3, v - 1, BONE_Z);
        f.set(u, yb + 3, v + 1, BONE_Z);
        f.skull(u, yb + 4, v, 0, side > 0 ? 12 : 4);
    }

    private void interior(Draw.Frame f, Mega.Site s, int yb, int top) {
        Draw.Mat floor = Draw.of(SMOOTH), ceil = Draw.mix(CHISELED, GLOW, 0.12, s.salt + 31);
        // gallery from each portal to the hall
        for (int side = -1; side <= 1; side += 2) {
            int a = side * 12, bnd = side * (B + 2);
            f.box(Math.min(a, bnd), yb, -2, Math.max(a, bnd), yb + 5, 2, Draw.AIR);
            f.box(Math.min(a, bnd), yb - 1, -2, Math.max(a, bnd), yb - 1, 2, floor);
            f.box(Math.min(a, bnd), yb + 6, -2, Math.max(a, bnd), yb + 6, 2, ceil);
            for (int t = 16; t <= 34; t += 6) {
                int u = side * t;
                f.set(u, yb + 1, -3, 0); f.set(u, yb + 2, -3, 0); f.set(u, yb + 1, 3, 0); f.set(u, yb + 2, 3, 0);
                f.set(u, yb, -3, BONE_Y); f.set(u, yb, 3, BONE_Y);
                f.skull(u, yb + 1, -3, 0, 0);
                f.skull(u, yb + 1, 3, 0, 8);
            }
        }
        // ring corridor
        f.box(-29, yb, -29, 29, yb + 4, 29, Draw.AIR);
        f.box(-26, yb, -26, 26, yb + 4, 26, Draw.of(SANDSTONE));
        f.box(-29, yb - 1, -29, 29, yb - 1, 29, floor);
        f.walls(-29, yb + 5, -29, 29, yb + 5, 29, ceil);
        f.walls(-28, yb + 5, -28, 28, yb + 5, 28, ceil);
        f.walls(-27, yb + 5, -27, 27, yb + 5, 27, ceil);
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) f.point(cu * 28, yb, cv * 28, "garrison:wither_skeleton+wither_skeleton");
        // gallery passes through the inner block to the hall
        for (int side = -1; side <= 1; side += 2) f.box(side * 26, yb, -2, side * 12, yb + 5, 2, Draw.AIR);
        // burial chambers: two per side, outside the ring
        int room = 0;
        for (int sideIdx = 0; sideIdx < 4; sideIdx++) {
            for (int w = -14; w <= 14; w += 28) {
                int[] c = sideIdx == 0 ? new int[]{31, w, 1, 0} : sideIdx == 1 ? new int[]{-31, w, -1, 0} : sideIdx == 2 ? new int[]{w, 31, 0, 1} : new int[]{w, -31, 0, -1};
                burial(f, s, c[0], c[1], c[2], c[3], yb, room++);
            }
        }
        // the Hall of Kings
        f.box(-12, yb, -12, 12, yb + 13, 12, Draw.AIR);
        f.box(-12, yb - 1, -12, 12, yb - 1, 12, (x, y, z) -> ((x + z) & 1) == 0 ? SMOOTH : CHISELED);
        f.box(-12, yb + 14, -12, 12, yb + 14, 12, ceil);
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, 9}}) {
            f.box(c[0], yb, c[1], c[0], yb + 12, c[1], Draw.of(BONE_Y));
            f.box(c[0] - 1, yb + 13, c[1] - 1, c[0] + 1, yb + 13, c[1] + 1, CHISELED);
        }
        // the dais and the Soul King's sarcophagus
        f.box(-4, yb, -4, 4, yb, 4, SMOOTH);
        f.box(-3, yb + 1, -3, 3, yb + 1, 3, SMOOTH);
        f.box(-1, yb + 2, -2, 1, yb + 2, 2, Draw.of(QUARTZ_PILLAR));
        f.box(0, yb + 3, -2, 0, yb + 3, 2, Draw.of(SLAB_TOP));
        f.chest(0, yb + 2, 3, Draw.SOUTH, "jaspr:mega/pyramid_vault");
        f.skull(0, yb + 3, -2, 0, 0);
        for (int cu = -3; cu <= 3; cu += 6) for (int cv = -3; cv <= 3; cv += 6) { f.set(cu, yb + 2, cv, RACK); f.set(cu, yb + 3, cv, FIRE); }
        f.chest(-11, yb, 10, Draw.EAST, "jaspr:mega/pyramid");
        f.chest(11, yb, -10, Draw.WEST, "jaspr:mega/pyramid");
        f.point(0, yb + 2, 6, "garrison:!spinout+wither_skeleton+wither_skeleton");
        f.point(-7, yb, -7, "garrison:wither_skeleton+spinout");
        // the shaft from the summit: a column with a ladder from the hall floor to the top platform
        f.box(-1, yb + 14, -5, 1, top, -3, Draw.AIR);
        f.box(0, yb, -6, 0, yb + 13, -6, Draw.of(BONE_Y));
        for (int y = yb; y <= top; y++) f.set(0, y, -5, b(65, f.facing(Draw.SOUTH)));
    }

    private void burial(Draw.Frame f, Mega.Site s, int cu, int cv, int du, int dv, int yb, int idx) {
        // room from 31 to 35 outwards from the ring, 7 wide
        int u0 = du != 0 ? cu : cu - 3, u1 = du != 0 ? cu + du * 3 : cu + 3, v0 = dv != 0 ? cv : cv - 3, v1 = dv != 0 ? cv + dv * 3 : cv + 3;
        f.box(Math.min(u0, u1), yb, Math.min(v0, v1), Math.max(u0, u1), yb + 3, Math.max(v0, v1), Draw.AIR);
        f.box(Math.min(u0, u1), yb - 1, Math.min(v0, v1), Math.max(u0, u1), yb - 1, Math.max(v0, v1), Draw.of(GLOOMY));
        // doorway from the ring corridor
        f.box(cu - du - (dv != 0 ? 1 : 0), yb, cv - dv - (du != 0 ? 1 : 0), cu - du + (dv != 0 ? 1 : 0), yb + 2, cv - dv + (du != 0 ? 1 : 0), Draw.AIR);
        // sarcophagus against the far wall, the chest at its foot
        int fu = cu + du * 3, fv = cv + dv * 3;
        int facing = du > 0 ? Draw.WEST : du < 0 ? Draw.EAST : dv > 0 ? Draw.NORTH : Draw.SOUTH;
        if (du != 0) f.box(fu, yb, fv - 1, fu + du, yb, fv + 1, Draw.of(QUARTZ_PILLAR));
        else f.box(fu - 1, yb, fv, fu + 1, yb, fv + dv, Draw.of(QUARTZ_PILLAR));
        Plan p = (Plan) s.plan;
        if (p.roomChest[idx]) f.chest(cu + du, yb, cv + dv, facing, "jaspr:mega/pyramid");
        f.set(fu, yb + 1, fv, GLOW);
        f.skull(fu + (dv != 0 ? 1 : 0), yb + 1, fv + (du != 0 ? 1 : 0), 0, idx * 4);
        // bones and thorns along the walls
        f.set(cu + du * 2 + (dv != 0 ? -3 : 0), yb, cv + dv * 2 + (du != 0 ? -3 : 0), BONE_Y);
        if ((idx & 1) == 0) {
            f.set(cu + du * 2 + (dv != 0 ? 3 : 0), yb, cv + dv * 2 + (du != 0 ? 3 : 0), THORN_MAT);
            f.point(cu + du * 2, yb, cv + dv * 2, "garrison:wither_skeleton+spinout");
        }
    }

    /** The summit: a colonnade around an obsidian obelisk, soul fires at the corners, the shaft's mouth. */
    private void summit(Draw.Frame f, int yb, int top) {
        int y = top + 1;
        f.walls(-5, y + 6, -5, 5, y + 6, 5, Draw.of(CHISELED));
        for (int cu = -5; cu <= 5; cu += 10) for (int cv = -5; cv <= 5; cv += 10) {
            f.box(cu, y, cv, cu + (cu < 0 ? 1 : -1), y + 5, cv + (cv < 0 ? 1 : -1), Draw.of(SMOOTH));
            f.set(cu, y + 6, cv, RACK);
            f.set(cu, y + 7, cv, FIRE);
        }
        f.box(-1, y, -1, 1, y + 3, 1, OBSIDIAN);
        f.box(0, y + 4, 0, 0, y + 17, 0, OBSIDIAN);
        f.set(0, y + 18, 0, GLOW);
        f.chest(2, y, 2, Draw.SOUTH, "jaspr:mega/pyramid");
        f.point(3, y, -2, "garrison:spinout+spinout");
    }

    /** Around the pyramid: corner obelisks and half-buried ghast fossils. */
    private void grounds(Draw.Frame f, Mega.Site s, Plan p, int yb) {
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
            int u = cu * 58, v = cv * 58;
            int g = s.floorAt(f.x(u, v), f.z(u, v));
            f.box(u - 2, g - 3, v - 2, u + 2, g + 1, v + 2, Draw.of(SMOOTH));
            f.box(u - 1, g + 2, v - 1, u + 1, g + 6, v + 1, OBSIDIAN);
            f.box(u, g + 7, v, u, g + 19, v, OBSIDIAN);
            f.set(u, g + 20, v, GLOW);
            f.walls(u - 2, g + 2, v - 2, u + 2, g + 2, v + 2, Draw.of(CHISELED));
        }
        for (int[] fo : p.fossils) fossil(f, s, fo[0], fo[1], fo[2], fo[3]);
    }

    /** A ghast skeleton: a spine of bone with rib arches, sunk in the dunes. */
    private void fossil(Draw.Frame f, Mega.Site s, int u, int v, int len, int ang) {
        int du = ang == 0 ? 1 : ang == 1 ? -1 : 0, dv = ang == 2 ? 1 : ang == 3 ? -1 : 0;
        int g = s.floorAt(f.x(u, v), f.z(u, v));
        for (int t = 0; t < len; t++) {
            int pu = u + du * t, pv = v + dv * t;
            f.set(pu, g, pv, du != 0 ? BONE_X : BONE_Z);
            if (t % 3 == 1 && t < len - 2) {
                int half = 4 + (t < len / 2 ? t / 3 : (len - t) / 3);
                for (int k = -half; k <= half; k++) {
                    double q = Math.abs(k) / (double) half;
                    int hy = g + (int) Math.round(Math.sqrt(Math.max(0, 1 - q * q)) * half * 0.8);
                    f.set(pu + dv * k, hy, pv + du * k, BONE_Y);
                }
            }
        }
        // the skull: a hollow block of bone at the head
        int hu = u - du * 2, hv = v - dv * 2;
        f.box(hu - 2, g, hv - 2, hu + 2, g + 3, hv + 2, Draw.of(BONE_Y));
        f.box(hu - 1, g + 1, hv - 1, hu + 1, g + 2, hv + 1, Draw.AIR);
        f.set(hu - du * 2 + dv, g + 2, hv - dv * 2 + du, 0);
        f.set(hu - du * 2 - dv, g + 2, hv - dv * 2 - du, 0);
    }

    @Override int ground(Mega.Site s, int x, int y, int z) {
        return s.f(x, z) > 0.86 ? GLOOMY : SOUL;
    }

    @Override Draw.Mat under() { return Draw.mix(GLOOMY, SOUL, 0.3, 4471); }

    @Override boolean dry(Mega.Site s, int x, int z) { return s.dist(x, z) < 50; }

    @Override double lakeLevel() { return 0.42; }

    @Override void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        glowHang(d, x, z, ceil, s.salt + 51, 0.006);
        if (lake || s.dist(x, z) < 46) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.012) {
            int h = 1 + (int) (Draw.rnd(x, 10, z, s.salt) * 3);
            for (int k = 1; k <= h; k++) d.set(x, floor + k, z, THORN_MAT);
        } else if (q < 0.016) {
            d.set(x, floor + 1, z, BONE_Y);
            if (q < 0.014) d.set(x, floor + 2, z, BONE_Y);
        } else if (q < 0.0172) {
            d.set(x, floor, z, RACK);
            d.set(x, floor + 1, z, FIRE);
        }
    }
}
