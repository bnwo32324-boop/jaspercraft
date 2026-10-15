package chat.jaspr.lairs;

import java.util.Random;

/** The Mutant Creeper's three lairs. */
final class CreeperLairs {
    private CreeperLairs() {}

    // ================================================================ 7. Blast Crater
    /** A scorched crater with a creeper nest in its heart, wreckage on the rim and a half-buried bunker. */
    static final class BlastCrater extends Design {
        BlastCrater() {
            super("blast_crater", "The Blast Crater", "mutant_creeper", "The Crater Mother", 46, 46, 10,
                "SITE ZERO|Do not approach|the nest~It did not die|in the blast.|It laid eggs in|the crater.");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 23, cv = 23;
            c.padCircle(cu, cv, 22, 14);
            Canvas.Mat scorch = Canvas.mix(M.concrete(M.BLACK), 3, M.powder(M.GRAY), 2, M.GRAVEL, 3, M.COARSE, 3, M.COBBLE, 1, M.MAGMA, 0);
            Canvas.Mat rim = Canvas.mix(M.GRAVEL, 3, M.COARSE, 3, M.COBBLE, 2, M.DIRT, 2, M.powder(M.GRAY), 1);
            for (int v = 0; v < c.d; v++) for (int u = 0; u < c.w; u++) {
                double dist = Math.hypot(u - cu, v - cv);
                if (dist <= 18) {
                    int depth = (int) Math.round(8 * (1 - Canvas.sq(dist / 18.0)));
                    if (depth <= 0) continue;
                    c.air(u, -depth + 1, v, u, 0, v);
                    c.set(u, -depth, v, scorch.at(c, u, -depth, v));
                } else if (dist <= 21) {
                    int h = dist < 19.5 ? 2 : 1;
                    if (c.chance(u, 0, v, 0.15, 91)) h--;
                    for (int y = 1; y <= h; y++) c.set(u, y, v, rim.at(c, u, y, v));
                }
            }
            // thrown stone and twisted iron round the rim
            for (int i = 0; i < 30; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 15 + r.nextDouble() * 6;
                int u = (int) Math.round(cu + Math.cos(a) * rad), v = (int) Math.round(cv + Math.sin(a) * rad);
                double dist = Math.hypot(u - cu, v - cv);
                int y = dist <= 18 ? -(int) Math.round(8 * (1 - Canvas.sq(dist / 18.0))) + 1 : dist <= 19.5 ? 3 : 2;
                if (i % 3 == 0) c.fill(u, y, v, u, y + 1 + r.nextInt(2), v, M.IRON_BARS);
                else c.soft(u, y, v, i % 3 == 1 ? M.LOG_DARK_X : M.COBBLE);
            }
            // charred trees beyond the rim
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(30 + k * 90);
                int u = (int) Math.round(cu + Math.cos(a) * 21), v = (int) Math.round(cv + Math.sin(a) * 21);
                post(c, u, v, 1, 4 + r.nextInt(3), Canvas.of(M.BARK_DARK));
            }
            // the nest: moss, leaves and eggs in the bottom
            c.ellipsoid(cu, -8, cv, 4.5, 2.5, 4.5, Canvas.mix(M.MOSSY_COBBLE, 4, M.LEAVES_JUNGLE, 3, M.COARSE, 2), false, -8);
            c.ellipsoid(cu, -7, cv, 3.2, 1.6, 3.2, Canvas.of(M.AIR), false, -7);
            for (int i = 0; i < 7; i++) {
                double a = i * Math.PI * 2 / 7;
                int u = (int) Math.round(cu + Math.cos(a) * 2), v = (int) Math.round(cv + Math.sin(a) * 2);
                c.set(u, -7, v, i % 2 == 0 ? M.concrete(M.LIME) : M.powder(M.GREEN));
                if (i % 3 == 0) c.set(u, -6, v, M.concrete(M.LIME));
            }
            c.scatter(5, 5, 40, 40, -2, 0.02, Canvas.mix(M.FERN, 2, M.DEAD_BUSH, 2, M.VINE_N, 0), 92);
            // the spawner sits in the nest wall
            c.spawner(cu + 4, -8, cv, "creeper");
            // a wrecked supply cart on the west rim
            c.fill(1, 1, 20, 4, 1, 25, M.DARK_OAK_SLAB);
            c.set(1, 2, 20, M.DARK_OAK_FENCE); c.set(4, 2, 25, M.DARK_OAK_FENCE);
            c.chest(2, 2, 22, M.E, "lair_creeper_cache");
            c.set(3, 2, 23, M.HAY);
            // the bunker on the north-east rim (vault), its door facing the crater sealed with bars
            int b0u = 32, b1u = 42, b0v = 3, b1v = 13;
            c.fill(b0u - 1, -6, b0v - 1, b1u + 1, -1, b1v + 1, rubble());
            c.room(b0u, -1, b0v, b1u, 5, b1v, Canvas.mix(M.concrete(M.GRAY), 4, M.concrete(M.SILVER), 2, M.CRACKED_SB, 2, M.COBBLE, 1), Canvas.of(M.concrete(M.GRAY)), Canvas.mix(M.concrete(M.GRAY), 3, M.COBBLE, 1));
            c.decay(b0u, 4, b0v, b1u, 5, b1v, 0.12, 93);
            c.air(b0u, 0, b1v - 3, b0u, 2, b1v - 1);
            for (int v = b1v - 3; v <= b1v - 1; v++) for (int y = 0; y <= 2; y++) c.seal(b0u, y, v, M.IRON_BARS);
            c.vaultChest(b1u - 1, 0, b0v + 2, M.W, "lair_creeper_vault");
            c.vaultChest(b1u - 1, 0, b0v + 5, M.W, "lair_creeper_vault");
            c.set(b1u - 1, 0, b0v + 8, M.CAULDRON); c.set(b0u + 2, 0, b0v + 1, M.FURNACE);
            c.set(b0u + 4, 2, b0v + 1, M.wallTorch(M.S));
            loreSign(c, b0u + 1, 1, b0v + 1, M.S, 1);
            c.vault(b0u - 1, -6, b0v - 1, b1u + 1, 6, b1v + 1);
            c.set(cu, 1, 44, M.COBBLE_WALL); c.set(cu, 2, 44, M.COBBLE_WALL);
            loreSign(c, cu, 2, 45, M.S, 0);
            c.boss(cu - 7, stand(c, cu - 7, cv + 3), cv + 3, 22);
            c.garrison(cu + 9, stand(c, cu + 9, cv), cv, "creeper+creeper");
            c.garrison(cu - 9, stand(c, cu - 9, cv - 5), cv - 5, "creeper+creeper+creeper");
            c.garrison(cu, stand(c, cu, cv + 10), cv + 10, "creeper+spider");
            c.garrison(cu - 18, stand(c, cu - 18, cv + 8), cv + 8, "creeper+creeper");
            c.garrison(cu + 6, stand(c, cu + 6, cv + 19), cv + 19, "creeper+spider");
            c.garrison(cu - 14, stand(c, cu - 14, cv - 12), cv - 12, "creeper");
        }
        /** The first open height above the drawn ground of a column (y 1 where nothing is drawn). */
        static int stand(Canvas c, int u, int v) {
            for (int y = Canvas.YMIN + 1; y <= 20; y++) if (c.get(u, y - 1, v) > 0 && M.id(c.get(u, y - 1, v)) != 0 && c.get(u, y, v) <= 0 && c.get(u, y + 1, v) <= 0) return y;
            return 1;
        }
    }

    // ================================================================ 8. Overgrown Greenhouse
    /** A glass conservatory gone wild: leaves, vines, a dry fountain, and a potting shed bolted shut. */
    static final class Greenhouse extends Design {
        Greenhouse() {
            super("overgrown_greenhouse", "The Overgrown Greenhouse", "mutant_creeper", "The Verdant Detonator", 48, 32, 9,
                "BOTANICAL HOUSE|Specimen wing|- staff only -~It grew. It|hissed. It grew|again. The shed|is locked.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(1, 1, 46, 30, 22);
            int u0 = 5, u1 = 38, v0 = 6, v1 = 25;
            Canvas.Mat glassWall = Canvas.mix(M.pane(M.GREEN), 4, M.pane(M.LIME), 1, M.PANE, 3, M.AIR, 1);
            Canvas.Mat glassRoof = Canvas.mix(M.glass(M.GREEN), 4, M.GLASS, 3, M.glass(M.LIME), 1, M.AIR, 2);
            Canvas.Mat soil = Canvas.mix(M.PODZOL, 4, M.GRASS, 4, M.COARSE, 2, M.MYCELIUM, 0);
            c.fill(u0, 0, v0, u1, 0, v1, soil);
            c.walls(u0, 1, v0, u1, 1, v1, Canvas.mix(M.SB, 4, M.MOSSY_SB, 3));
            c.walls(u0, 2, v0, u1, 8, v1, glassWall);
            for (int u = u0; u <= u1; u += 3) { post(c, u, v0, 2, 8, Canvas.of(M.SPRUCE_FENCE)); post(c, u, v1, 2, 8, Canvas.of(M.SPRUCE_FENCE)); }
            for (int v = v0; v <= v1; v += 3) { post(c, u0, v, 2, 8, Canvas.of(M.SPRUCE_FENCE)); post(c, u1, v, 2, 8, Canvas.of(M.SPRUCE_FENCE)); }
            c.air(u0 + 1, 1, v0 + 1, u1 - 1, 20, v1 - 1);
            // the barrel-vault roof with spruce ribs
            double mid = (v0 + v1) / 2.0, rad = (v1 - v0) / 2.0;
            for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                double dy = Math.sqrt(Math.max(0, rad * rad - Canvas.sq(v - mid)));
                int y = 8 + (int) Math.round(dy * 0.85);
                boolean rib = (u - u0) % 3 == 0;
                c.set(u, y, v, rib ? M.SPRUCE : glassRoof.at(c, u, y, v));
            }
            for (int u = u0; u <= u1; u += 33) for (int v = v0; v <= v1; v++) {
                double dy = Math.sqrt(Math.max(0, rad * rad - Canvas.sq(v - mid)));
                int top = 8 + (int) Math.round(dy * 0.85);
                for (int y = 9; y < top; y++) c.set(u, y, v, glassWall.at(c, u, y, v));
            }
            // doors at both ends of the nave
            c.air(u0, 1, 14, u0, 4, 17);
            // the overgrowth: leaf masses, vines, ferns, flowers, pumpkins and melons
            for (int i = 0; i < 16; i++) {
                double pu = u0 + 3 + r.nextDouble() * (u1 - u0 - 6), pv = v0 + 2 + r.nextDouble() * (v1 - v0 - 4);
                if (Math.abs(pu - 21) < 5 && Math.abs(pv - mid) < 5) continue;
                c.ellipsoid(pu, 2 + r.nextDouble() * 4, pv, 1.5 + r.nextDouble() * 2, 1.2 + r.nextDouble() * 1.5, 1.5 + r.nextDouble() * 2,
                    Canvas.mix(M.LEAVES_OAK, 4, M.LEAVES_JUNGLE, 3, M.LEAVES_DARK, 1), false, 1);
            }
            for (int v = v0 + 1; v <= v1 - 1; v += 2) for (int u = u0 + 1; u <= u1 - 1; u += 2)
                if ((v == v0 + 1 || v == v1 - 1) && c.chance(u, 0, v, 0.45, 101)) for (int y = 2; y <= 7; y++) if (c.get(u, y, v) <= 0 && c.chance(u, y, v, 0.7, 102)) c.set(u, y, v, v == v0 + 1 ? M.VINE_N : M.s(106, 1));
            c.scatter(u0 + 1, v0 + 1, u1 - 1, v1 - 1, 1, 0.3, Canvas.mix(M.FERN, 5, M.TALL_GRASS, 4, M.RED_FLOWER, 1, M.BLUE_ORCHID, 1, M.ALLIUM, 1, M.PUMPKIN, 1, M.MELON, 1, M.MUSHROOM_RED, 1), 103);
            // a huge red mushroom at the west end
            post(c, 9, 10, 1, 7, Canvas.of(M.MUSH_STEM));
            c.ellipsoid(9, 8, 10, 3, 1.2, 3, Canvas.of(M.RED_MUSH_BLOCK), false, 8);
            // planter beds along the walls
            for (int u = u0 + 2; u <= u1 - 2; u += 6) {
                c.fill(u, 1, v0 + 1, u + 3, 1, v0 + 2, M.SPRUCE_SLAB); c.fill(u + 1, 1, v0 + 1, u + 2, 1, v0 + 1, M.PODZOL);
                c.fill(u, 1, v1 - 2, u + 3, 1, v1 - 1, M.SPRUCE_SLAB); c.fill(u + 1, 1, v1 - 1, u + 2, 1, v1 - 1, M.PODZOL);
            }
            // the dry fountain in the middle, the arena round it
            int fu = 21, fv = 15;
            c.disc(fu + 0.5, fv + 0.5, 4, 0, Canvas.of(M.SB));
            c.ring(fu + 0.5, fv + 0.5, 4, 1, Canvas.mix(M.SB_SLAB, 3, M.MOSSY_SB, 1));
            c.disc(fu + 0.5, fv + 0.5, 3, 1, Canvas.of(M.AIR));
            post(c, fu, fv, 1, 3, Canvas.of(M.CHISELED_SB)); c.set(fu, 4, fv, M.FLOWER_POT);
            c.air(fu - 7, 1, fv - 5, fu - 5, 4, fv + 5);
            c.chest(u0 + 1, 1, v1 - 3, M.E, "lair_creeper_cache");
            c.spawner(30, 0, 9, "creeper");
            // the potting shed (vault) at the east end, its door to the nave sealed with bars
            int s0 = 39, s1 = 46, t0 = 10, t1 = 21;
            c.room(s0, 0, t0, s1, 6, t1, Canvas.mix(M.SPRUCE, 4, M.DARK_OAK, 2, M.LOG_SPRUCE, 1), Canvas.of(M.SPRUCE), Canvas.mix(M.SPRUCE_SLAB, 3, M.DARK_OAK_SLAB, 1));
            c.air(u1, 1, 15, s0, 2, 16);
            for (int v = 15; v <= 16; v++) for (int y = 1; y <= 2; y++) c.seal(s0, y, v, M.IRON_BARS);
            c.vaultChest(s1 - 1, 1, t0 + 2, M.W, "lair_creeper_vault");
            c.vaultChest(s1 - 1, 1, t1 - 2, M.W, "lair_creeper_vault");
            c.fill(s0 + 2, 1, t1 - 1, s0 + 4, 1, t1 - 1, M.FLOWER_POT); c.set(s0 + 1, 1, t0 + 1, M.CAULDRON); c.set(s0 + 2, 1, t0 + 1, M.BREWING);
            c.set(s1 - 1, 3, 15, M.wallTorch(M.W));
            loreSign(c, s1 - 1, 2, 17, M.W, 1);
            c.vault(s0, 0, t0, s1, 7, t1);
            c.set(3, 1, 13, M.COBBLE_WALL); c.set(3, 2, 13, M.COBBLE_WALL);
            loreSign(c, 2, 2, 13, M.W, 0);
            c.boss(fu - 6, 1, fv, 20);
            c.garrison(10, 1, 20, "creeper+creeper");
            c.garrison(31, 1, 20, "creeper+witch");
            c.garrison(27, 1, 10, "creeper+creeper+spider");
            c.garrison(14, 1, 9, "creeper");
            c.garrison(2, 1, 6, "creeper+spider");
            c.garrison(44, 1, 26, "creeper+creeper");
        }
    }

    // ================================================================ 9. Powder Mill
    /** An abandoned gunpowder works: mill house and water wheel, chimney, storehouse, rails, and a sealed magazine. */
    static final class PowderMill extends Design {
        PowderMill() {
            super("powder_mill", "The Powder Mill", "mutant_creeper", "The Old Fuse", 52, 42, 9,
                "BLACK POWDER|WORKS No. 3|No naked flames~The fuse burned|for nine years.|Keep the|magazine shut.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(0, 0, 51, 41, 30);
            Canvas.Mat brick = Canvas.mix(M.BRICKS, 6, M.CRACKED_SB, 1, M.COBBLE, 1);
            Canvas.Mat yard = Canvas.mix(M.GRAVEL, 4, M.COARSE, 3, M.GRASS_PATH, 2, M.powder(M.GRAY), 1, M.concrete(M.BLACK), 0);
            c.fill(0, 0, 0, 51, 0, 41, yard);
            // the mill house with a gable roof
            int m0 = 6, m1 = 19, n0 = 4, n1 = 17;
            c.room(m0, 0, n0, m1, 9, n1, brick, Canvas.of(M.SPRUCE), null);
            for (int k = 0; k <= 7; k++) {
                for (int u = m0 - 1; u <= m1 + 1; u++) {
                    if (c.chance(u, 9 + k, n0 + k, 0.08, 111)) continue;
                    c.set(u, 9 + k, n0 - 1 + k, M.stairs(M.SPRUCE_STAIRS, M.S, false));
                    c.set(u, 9 + k, n1 + 1 - k, M.stairs(M.SPRUCE_STAIRS, M.N, false));
                }
                for (int v = n0 + k; v <= n1 - k; v++) { c.set(m0, 9 + k, v, M.BRICKS); c.set(m1, 9 + k, v, M.BRICKS); }
            }
            c.air(12, 1, n1, 14, 4, n1);
            for (int v = n0 + 3; v <= n1 - 3; v += 4) { c.set(m0, 4, v, M.PANE); c.set(m0, 5, v, M.PANE); c.set(m1, 4, v, M.PANE); c.set(m1, 5, v, M.PANE); }
            // grinding stones and hoppers inside
            c.cylinder(12.5, 10.5, 2, 1, 1, Canvas.of(M.SMOOTH_STONE), false);
            c.set(12, 2, 10, M.HOPPER); c.set(13, 2, 11, M.HOPPER);
            c.fill(m0 + 1, 1, n0 + 1, m0 + 2, 2, n0 + 3, Canvas.mix(M.COBBLE_SLAB, 1, M.GRAVEL, 1));
            c.chest(m1 - 1, 1, n0 + 1, M.W, "lair_creeper_cache");
            // the water wheel on the mill's west wall, in a dry stone race
            c.fill(1, -2, 2, 4, -1, 19, M.SB);
            c.air(2, -1, 2, 3, 0, 19);
            for (int deg = 0; deg < 360; deg += 8) {
                double a = Math.toRadians(deg);
                int v = (int) Math.round(10.5 + Math.cos(a) * 6), y = (int) Math.round(5 + Math.sin(a) * 6);
                c.set(3, y, v, M.SPRUCE); c.set(2, y, v, M.SPRUCE);
            }
            for (int deg = 0; deg < 180; deg += 45) {
                double a = Math.toRadians(deg);
                c.line(3, (int) Math.round(5 + Math.sin(a) * 5), (int) Math.round(10.5 + Math.cos(a) * 5), 3, (int) Math.round(5 - Math.sin(a) * 5), (int) Math.round(10.5 - Math.cos(a) * 5), Canvas.of(M.SPRUCE_FENCE));
            }
            c.set(4, 5, 10, M.LOG_SPRUCE_X); c.set(5, 5, 10, M.LOG_SPRUCE_X);
            // the chimney
            c.cylinder(23, 8, 1.8, 0, 27, Canvas.mix(M.BRICKS, 5, M.CRACKED_SB, 1), true);
            c.set(23, 28, 8, M.AIR);
            for (int y = 26; y <= 27; y++) c.ring(23, 8, 2.3, y, Canvas.of(M.BRICKS));
            // the storehouse with a fallen roof
            int s0 = 30, s1 = 47, t0 = 3, t1 = 13;
            c.room(s0, 0, t0, s1, 7, t1, Canvas.mix(M.COBBLE, 3, M.SPRUCE, 3, M.MOSSY_COBBLE, 1), Canvas.of(M.SPRUCE), Canvas.mix(M.SPRUCE_SLAB, 4, M.AIR, 2));
            c.air(37, 1, t1, 40, 4, t1);
            for (int i = 0; i < 18; i++) c.soft(s0 + 1 + r.nextInt(s1 - s0 - 1), 1, t0 + 1 + r.nextInt(t1 - t0 - 1), r.nextInt(3) == 0 ? M.LOG_SPRUCE_X : M.SPRUCE_SLAB);
            for (int u = s0 + 2; u <= s1 - 2; u += 3) { c.set(u, 1, t0 + 1, M.LOG_SPRUCE); c.set(u, 2, t0 + 1, M.SPRUCE_SLAB); }
            c.chest(s1 - 1, 1, t1 - 1, M.W, "lair_creeper_cache");
            c.spawner(s0 + 3, 0, t1 - 2, "creeper");
            // rails through the yard, a cart turned over, heaps of sand and gravel
            for (int u = 4; u <= 48; u++) if (!c.chance(u, 1, 21, 0.1, 112)) { c.set(u, 1, 21, M.RAIL_EW); c.set(u, 0, 21, M.GRAVEL); }
            for (int v = 14; v <= 38; v++) if (!c.chance(26, 1, v, 0.1, 113)) { c.set(26, 1, v, M.RAIL_NS); c.set(26, 0, v, M.GRAVEL); }
            c.set(26, 1, 21, M.RAIL_NS); c.set(26, 0, 21, M.GRAVEL);
            c.ellipsoid(12, 1, 30, 4, 2.5, 3.5, Canvas.mix(M.SAND, 2, M.GRAVEL, 2), false, 1);
            c.ellipsoid(18, 1, 36, 3, 2, 3, Canvas.of(M.GRAVEL), false, 1);
            for (int i = 0; i < 8; i++) { int u = 6 + r.nextInt(18), v = 24 + r.nextInt(14); c.soft(u, 1, v, M.LOG_OAK); c.soft(u, 2, v, M.OAK_SLAB); }
            // the magazine (vault): a stone vault under an earth mound, door to the yard sealed
            int g0 = 34, g1 = 45, h0 = 26, h1 = 37;
            c.ellipsoid((g0 + g1) / 2.0, 2, (h0 + h1) / 2.0, 8, 6, 7.5, Canvas.mix(M.GRASS, 3, M.DIRT, 2, M.COARSE, 1), false, 1);
            c.room(g0, 0, h0, g1, 5, h1, Canvas.mix(M.SB, 4, M.MOSSY_SB, 2, M.CRACKED_SB, 1), Canvas.of(M.SB), Canvas.of(M.SB));
            c.air(g0 - 6, 1, 30, g0, 3, 33);
            c.fill(g0 - 3, 1, 29, g0 - 1, 4, 29, M.SB); c.fill(g0 - 3, 1, 34, g0 - 1, 4, 34, M.SB); c.fill(g0 - 3, 4, 30, g0 - 1, 4, 33, M.SB);
            for (int v = 30; v <= 33; v++) for (int y = 1; y <= 3; y++) c.seal(g0, y, v, M.IRON_BARS);
            c.vaultChest(g1 - 1, 1, h0 + 2, M.W, "lair_creeper_vault");
            c.vaultChest(g1 - 1, 1, h1 - 2, M.W, "lair_creeper_vault");
            for (int v = h0 + 1; v <= h1 - 1; v += 2) { c.set(g0 + 3, 1, v, M.LOG_OAK); c.set(g0 + 3, 2, v, M.OAK_SLAB); }
            c.set(g1 - 1, 3, 31, M.wallTorch(M.W));
            loreSign(c, g1 - 1, 2, 33, M.W, 1);
            c.vault(g0 - 3, 0, h0, g1, 9, h1);
            c.set(25, 1, 40, M.COBBLE_WALL); c.set(25, 2, 40, M.COBBLE_WALL);
            loreSign(c, 25, 2, 41, M.S, 0);
            c.air(23, 1, 24, 25, 3, 26);
            c.boss(24, 1, 25, 20);
            c.garrison(14, 1, 24, "creeper+creeper");
            c.garrison(36, 1, 18, "creeper+creeper+creeper");
            c.garrison(40, 1, 8, "creeper+spider");
            c.garrison(10, 1, 10, "creeper");
            c.garrison(20, 1, 34, "creeper+creeper");
            c.garrison(29, 1, 38, "creeper+spider");
        }
    }
}
