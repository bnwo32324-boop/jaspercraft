package chat.jaspr.lairs;

import java.util.Random;

/** The Mutant Zombie's three lairs. */
final class ZombieLairs {
    private ZombieLairs() {}

    // ================================================================ 1. Plague Pit
    /** A terraced quarantine pit full of the dead, a fence that failed, two field-hospital tents, a crypt in the pit wall. */
    static final class PlaguePit extends Design {
        PlaguePit() {
            super("plague_pit", "The Plague Pit", "mutant_zombie", "The Pit Warden", 44, 44, 10,
                "QUARANTINE|Pit 7 - sealed|by order of the|field hospital~They kept|coming back up.|We lost count.|- Dr. Venn");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 22, cv = 22;
            c.padCircle(cu, cv, 21.5, 14);
            c.pad(0, 19, 6, 25, 10); c.pad(19, 0, 25, 6, 10);
            Canvas.Mat floor = rot(), wall = ruinStone();
            // the terraced pit: rim (depth 3), middle (6), bottom (9)
            for (int v = 0; v < c.d; v++) for (int u = 0; u < c.w; u++) {
                double dist = Math.hypot(u - cu, v - cv);
                int depth = pitDepth(dist);
                if (depth == 0) continue;
                c.air(u, -depth + 1, v, u, 0, v);
                c.set(u, -depth, v, floor.at(c, u, -depth, v));
                // terrace lips of rubble so the steps read from above
                if (dist > 8.7 && dist <= 9.5 || dist > 11.7 && dist <= 12.5 || dist > 14.7 && dist <= 15.5)
                    if (c.chance(u, 0, v, 0.5, 3)) c.set(u, -depth + 1, v, M.COBBLE_SLAB);
            }
            // a ramp down from the south rim
            stairway(c, 21, -8, 31, M.S, 9, 3, M.COBBLE_STAIRS, rubble());
            // bone piles, webs and the dead on the bottom and the terraces
            for (int i = 0; i < 14; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 2 + r.nextDouble() * 13;
                int u = (int) Math.round(cu + Math.cos(a) * rad), v = (int) Math.round(cv + Math.sin(a) * rad);
                double dist = Math.hypot(u - cu, v - cv);
                int depth = pitDepth(dist);
                if (depth == 0) continue;
                c.ellipsoid(u, -depth + 1, v, 1.6, 1.2, 1.6, bones(), false, -depth + 1);
            }
            for (int v = 6; v < 38; v++) for (int u = 6; u < 38; u++) {
                double dist = Math.hypot(u - cu, v - cv);
                int depth = pitDepth(dist);
                if (depth == 0 || c.get(u, -depth + 1, v) > 0) continue;
                double p = c.unit(u, 0, v, 11);
                if (p < 0.035) c.set(u, -depth + 1, v, M.WEB);
                else if (p < 0.06) c.set(u, -depth + 1, v, M.SKULL);
                else if (p < 0.09) { if (Canvas.plantable(32, M.id(c.get(u, -depth, v)))) c.set(u, -depth + 1, v, M.DEAD_BUSH); }
                else if (p < 0.11) c.set(u, -depth + 1, v, M.CARPET_RED);
            }
            // a spawner in the middle terrace floor
            c.spawner(30, -6, 14, "zombie");
            // the failed quarantine fence (r 21) with a gate gap at the south
            for (int deg = 0; deg < 360; deg += 2) {
                double a = Math.toRadians(deg);
                int u = (int) Math.round(cu + Math.cos(a) * 20.5), v = (int) Math.round(cv + Math.sin(a) * 20.5);
                if (v > 38 && Math.abs(u - cu) < 4) continue;
                if (u <= 6 && v >= 18 && v <= 26 || v <= 6 && u >= 18 && u <= 26) continue;
                if (c.chance(u, 1, v, 0.3, 21)) continue;
                c.set(u, 1, v, M.IRON_BARS);
                c.set(u, 2, v, M.IRON_BARS);
            }
            for (int deg = 0; deg < 360; deg += 16) {
                double a = Math.toRadians(deg);
                int u = (int) Math.round(cu + Math.cos(a) * 20.5), v = (int) Math.round(cv + Math.sin(a) * 20.5);
                if (v > 38 && Math.abs(u - cu) < 4 || u <= 6 && v >= 18 && v <= 26 || v <= 6 && u >= 18 && u <= 26) continue;
                c.set(u, 1, v, M.MOSSY_WALL); c.set(u, 2, v, M.COBBLE_WALL);
                if (c.chance(u, 3, v, 0.5, 22)) c.set(u, 3, v, M.TORCH);
            }
            c.set(22, 1, 41, M.COBBLE_WALL); c.set(22, 2, 41, M.COBBLE_WALL);
            loreSign(c, 22, 2, 42, M.S, 0);
            // two collapsed field-hospital tents on the west and north rim
            tent(c, 0, 19, true);
            tent(c, 19, 0, false);
            c.chest(5, 1, 23, M.E, "lair_zombie_tent");
            c.chest(21, 1, 5, M.S, "lair_zombie_tent");
            c.set(3, 1, 20, M.CAULDRON); c.set(23, 1, 3, M.BREWING); c.set(20, 1, 3, M.CAULDRON);
            // the crypt in the pit wall: a tunnel from the bottom east, sealed with bars, and the vault room
            c.fill(32, -9, 20, 37, -6, 24, wall);
            c.air(32, -8, 21, 37, -7, 23);
            c.room(37, -9, 18, 43, -4, 26, wall, Canvas.of(M.SB), Canvas.of(M.SB));
            c.air(37, -8, 21, 37, -7, 23);
            for (int v = 21; v <= 23; v++) for (int y = -8; y <= -7; y++) c.seal(33, y, v, M.IRON_BARS);
            c.vaultChest(42, -8, 20, M.W, "lair_zombie_vault");
            c.vaultChest(42, -8, 24, M.W, "lair_zombie_vault");
            c.set(42, -8, 22, M.SKULL); c.set(38, -5, 19, M.WEB); c.set(42, -5, 25, M.WEB);
            c.set(40, -6, 19, M.wallTorch(M.S));
            loreSign(c, 41, -7, 19, M.S, 1);
            c.vault(33, -9, 18, 43, -4, 26);
            c.boss(cu, -8, cv, 21);
            c.garrison(4, 1, 15, "zombie+zombie_villager+zombie");
            c.garrison(30, 1, 6, "zombie+husk");
            c.garrison(22, -5, 11, "husk+zombie+zombie");
            c.garrison(11, -5, 22, "zombie+zombie");
            c.garrison(22, -8, 29, "zombie_villager+husk");
            c.garrison(36, -2, 22, "zombie+zombie+husk");
        }
        static int pitDepth(double dist) { return dist <= 9.5 ? 9 : dist <= 12.5 ? 6 : dist <= 15.5 ? 3 : 0; }
        /** A field tent 7 x 7 at (u0, v0), its ridge along v (alongV) or u, half fallen in. */
        private static void tent(Canvas c, int u0, int v0, boolean alongV) {
            Canvas.Mat cloth = Canvas.mix(M.wool(M.WHITE), 5, M.wool(M.SILVER), 3, M.wool(M.BROWN), 1);
            for (int i = 0; i < 7; i++) for (int k = 0; k < 7; k++) {
                int u = u0 + i, v = v0 + k, off = alongV ? Math.abs(i - 3) : Math.abs(k - 3);
                c.set(u, 0, v, M.PODZOL);
                int y = 5 - off;
                if (!c.chance(u, y, v, 0.28, 31)) c.set(u, y, v, cloth.at(c, u, y, v));
                if (off == 3 && !c.chance(u, 1, v, 0.5, 32)) c.set(u, 1, v, cloth.at(c, u, 1, v));
            }
            int[][] posts = {{0, 0}, {6, 0}, {0, 6}, {6, 6}};
            for (int[] p : posts) post(c, u0 + p[0], v0 + p[1], 1, 2, Canvas.of(M.SPRUCE_FENCE));
            c.set(u0 + 2, 1, v0 + 2, M.carpet(M.WHITE)); c.set(u0 + 2, 1, v0 + 3, M.carpet(M.WHITE));
            c.set(u0 + 4, 1, v0 + 4, M.carpet(M.WHITE)); c.set(u0 + 4, 1, v0 + 3, M.CARPET_RED);
        }
    }

    // ================================================================ 2. Rotting Abattoir
    /** A slaughterhouse with its roof fallen in, hooks still hanging, pens outside and a grain silo used as a strongroom. */
    static final class Abattoir extends Design {
        Abattoir() {
            super("rotting_abattoir", "The Rotting Abattoir", "mutant_zombie", "The Butcher of the Fold", 48, 38, 9,
                "FOLD ABATTOIR|est. before the|Turning|No admittance~The meat would|not stay still.|Lock the silo.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(1, 1, 46, 36, 22);
            Canvas.Mat base = Canvas.mix(M.COBBLE, 5, M.MOSSY_COBBLE, 3, M.ANDESITE, 1);
            Canvas.Mat boards = Canvas.mix(M.DARK_OAK, 6, M.SPRUCE, 3, M.AIR, 1);
            int u0 = 6, u1 = 37, v0 = 8, v1 = 27;
            // floor and walls
            c.fill(u0, 0, v0, u1, 0, v1, Canvas.mix(M.COBBLE, 4, M.COARSE, 3, M.GRAVEL, 1, M.MOSSY_COBBLE, 2));
            c.walls(u0, 1, v0, u1, 2, v1, base);
            c.walls(u0, 3, v0, u1, 8, v1, boards);
            for (int u = u0; u <= u1; u += 4) { post(c, u, v0, 1, 9, Canvas.of(M.LOG_DARK)); post(c, u, v1, 1, 9, Canvas.of(M.LOG_DARK)); }
            for (int v = v0; v <= v1; v += 4) { post(c, u0, v, 1, 9, Canvas.of(M.LOG_DARK)); post(c, u1, v, 1, 9, Canvas.of(M.LOG_DARK)); }
            post(c, u1, v1, 1, 9, Canvas.of(M.LOG_DARK));
            c.air(u0 + 1, 1, v0 + 1, u1 - 1, 14, v1 - 1);
            // big barn doors west and east, a cart door south
            c.air(u0, 1, 15, u0, 5, 20); c.air(u1, 1, 15, u1, 5, 20); c.air(16, 1, v1, 19, 4, v1);
            for (int v = 15; v <= 20; v += 5) { c.set(u0 - 1, 1, v, M.gate(186, M.W, true)); c.set(u1 + 1, 1, v, M.gate(186, M.E, true)); }
            // the gambrel roof, ridge along u, with a great hole where it fell
            double mid = (v0 + v1) / 2.0;
            for (int v = v0 - 1; v <= v1 + 1; v++) {
                double off = Math.abs(v - mid);
                int y = (int) Math.round(off < 4 ? 15 - off * 0.5 : 13 - (off - 4) * 0.62);
                y = Math.max(9, y);
                for (int u = u0 - 1; u <= u1 + 1; u++) {
                    boolean hole = u >= 15 && u <= 27 && c.chance(u, y, v, 0.72, 41);
                    if (hole || c.chance(u, y, v, 0.06, 42)) continue;
                    c.set(u, y, v, v < mid ? M.stairs(M.DARK_OAK_STAIRS, M.S, false) : M.stairs(M.DARK_OAK_STAIRS, M.N, false));
                    c.set(u, y - 1, v, M.DARK_OAK);
                }
            }
            // gable ends
            for (int v = v0; v <= v1; v++) {
                double off = Math.abs(v - mid);
                int top = (int) Math.round(off < 4 ? 14 - off * 0.5 : 12 - (off - 4) * 0.62);
                for (int y = 9; y <= top; y++) { if (!c.chance(u0, y, v, 0.2, 43)) c.set(u0, y, v, M.DARK_OAK); if (!c.chance(u1, y, v, 0.2, 44)) c.set(u1, y, v, M.DARK_OAK); }
            }
            // fallen roof on the floor
            for (int i = 0; i < 40; i++) {
                int u = 15 + r.nextInt(13), v = v0 + 2 + r.nextInt(v1 - v0 - 3);
                c.soft(u, 1, v, r.nextInt(3) == 0 ? M.LOG_DARK_X : r.nextInt(2) == 0 ? M.DARK_OAK_SLAB : M.DARK_OAK);
            }
            // beams and hooks: rows of carcasses on chains
            for (int u = 9; u <= 34; u += 6) {
                for (int v = v0 + 1; v <= v1 - 1; v++) c.set(u, 9, v, M.LOG_DARK_Z);
                for (int v = v0 + 3; v <= v1 - 3; v += 3) {
                    if (u >= 15 && u <= 27 && c.chance(u, 0, v, 0.5, 45)) continue;
                    c.set(u, 8, v, M.IRON_BARS); c.set(u, 7, v, M.IRON_BARS);
                    int meat = c.chance(u, 6, v, 0.5, 46) ? M.RED_MUSH_BLOCK : M.NETHER_WART_BLOCK;
                    c.set(u, 6, v, meat); c.set(u, 5, v, c.chance(u, 5, v, 0.5, 47) ? meat : M.BROWN_MUSH_BLOCK);
                    if (c.chance(u, 1, v, 0.4, 48)) c.set(u, 1, v, M.CARPET_RED);
                }
            }
            // troughs, the butcher's blocks, hay
            for (int u = 10; u <= 32; u += 4) c.set(u, 1, v0 + 1, M.CAULDRON);
            c.set(30, 1, 22, M.CRAFTING); c.set(31, 1, 22, M.ANVIL); c.set(30, 1, 23, M.CARPET_RED);
            for (int i = 0; i < 10; i++) { int u = u0 + 2 + r.nextInt(6), v = v0 + 2 + r.nextInt(5); c.soft(u, 1, v, M.HAY); if (r.nextBoolean()) c.soft(u, 2, v, M.HAY); }
            // the cold store in the south-west corner
            c.walls(u0, 1, 21, 13, 7, v1, Canvas.of(M.SB));
            c.fill(u0 + 1, 7, 22, 12, 7, v1 - 1, M.SB);
            c.air(u0 + 1, 1, 22, 12, 6, v1 - 1);
            c.air(13, 1, 23, 13, 3, 24);
            c.set(13, 1, 23, M.IRON_TRAPDOOR);
            c.chest(7, 1, 26, M.E, "lair_zombie_cache");
            c.chest(7, 1, 22, M.E, "lair_zombie_cache");
            c.spawner(10, 0, 24, "zombie");
            c.fill(11, 1, 26, 12, 1, 26, M.PACKED_ICE); c.set(12, 2, 26, M.PACKED_ICE);
            c.set(12, 3, 22, M.wallTorch(M.S));
            // pens south of the hall
            for (int u = 8; u <= 30; u++) { c.set(u, 1, 34, M.DARK_OAK_FENCE); }
            for (int v = 29; v <= 34; v++) { c.set(8, 1, v, M.DARK_OAK_FENCE); c.set(30, 1, v, M.DARK_OAK_FENCE); c.set(19, 1, v, M.DARK_OAK_FENCE); }
            c.set(13, 1, 34, M.gate(186, M.S, true)); c.set(25, 1, 34, M.gate(186, M.S, false));
            c.fill(9, 0, 29, 29, 0, 33, Canvas.mix(M.COARSE, 4, M.PODZOL, 3, M.SOUL_SAND, 1, M.GRASS_PATH, 1));
            c.scatter(9, 29, 29, 33, 1, 0.08, Canvas.mix(M.BONE, 3, M.SKULL, 1, M.HAY, 2), 49);
            // the silo strongroom (vault), door sealed with bars toward the yard
            int sx = 42, sv = 12;
            c.cylinder(sx, sv, 4.5, 0, 0, Canvas.of(M.SB), false);
            c.cylinder(sx, sv, 4.5, 1, 17, Canvas.mix(M.SB, 5, M.CRACKED_SB, 2, M.MOSSY_SB, 2, M.BRICKS, 1), true);
            c.ellipsoid(sx, 17, sv, 4.5, 4, 4.5, Canvas.mix(M.SB, 3, M.CRACKED_SB, 2, M.BRICK_SLAB, 1), true, 18);
            c.cylinder(sx, sv, 3.4, 1, 20, Canvas.of(M.AIR), false);
            c.air(sx - 4, 1, sv - 1, sx - 4, 3, sv + 1);
            c.air(sx - 5, 1, sv - 1, sx - 5, 3, sv + 1);
            for (int v = sv - 1; v <= sv + 1; v++) for (int y = 1; y <= 3; y++) c.seal(sx - 4, y, v, M.IRON_BARS);
            for (int y = 1; y <= 4; y++) c.set(sx + 3, y, sv, M.HAY);
            c.vaultChest(sx + 2, 1, sv - 2, M.W, "lair_zombie_vault");
            c.vaultChest(sx + 2, 1, sv + 2, M.W, "lair_zombie_vault");
            c.set(sx, 1, sv + 3, M.wallTorch(M.N));
            loreSign(c, sx - 2, 2, sv - 3, M.S, 1);
            c.vault(sx - 5, 0, sv - 5, sx + 5, 22, sv + 5);
            loreSign(c, u0 - 1, 2, 14, M.W, 0);
            c.air(21, 1, 16, 23, 3, 18);
            c.boss(22, 1, 17, 20);
            c.garrison(12, 1, 12, "zombie+zombie+zombie_villager");
            c.garrison(31, 1, 25, "husk+zombie");
            c.garrison(24, 1, 11, "zombie+zombie");
            c.garrison(14, 1, 31, "zombie+zombie_villager");
            c.garrison(25, 1, 31, "zombie+husk");
            c.garrison(37, 1, 4, "zombie+zombie");
        }
    }

    // ================================================================ 3. Gravewarden Mausoleum
    /** A walled cemetery, a pillared mausoleum and below it a crypt hall where the warden waits. */
    static final class Mausoleum extends Design {
        Mausoleum() {
            super("gravewarden_mausoleum", "The Gravewarden Mausoleum", "mutant_zombie", "The Gravewarden", 48, 48, 9,
                "Here lie the|faithful of the|old parish|R.I.P.~The Gravewarden|keeps the dead.|Never open|the ossuary.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(0, 0, 47, 47, 18);
            Canvas.Mat sb = ruinStone();
            // cemetery ground and its wall of bars on a low stone base, a gate in the south
            c.fill(1, 0, 1, 46, 0, 46, Canvas.mix(M.GRASS, 6, M.PODZOL, 3, M.COARSE, 2, M.GRASS_PATH, 1));
            for (int i = 1; i <= 46; i++) {
                int[][] edge = {{i, 1}, {i, 46}, {1, i}, {46, i}};
                for (int[] p : edge) {
                    if (p[1] == 46 && p[0] >= 21 && p[0] <= 26) continue;
                    if (c.chance(p[0], 1, p[1], 0.12, 51)) continue;
                    boolean pillar = (p[0] % 5 == 1) && (p[1] % 5 == 1);
                    c.set(p[0], 1, p[1], pillar ? M.CHISELED_SB : M.MOSSY_WALL);
                    c.set(p[0], 2, p[1], pillar ? M.SB : M.IRON_BARS);
                    if (pillar) c.set(p[0], 3, p[1], M.SB_SLAB);
                }
            }
            post(c, 20, 46, 1, 5, sb); post(c, 27, 46, 1, 5, sb);
            archU(c, 20, 27, 4, 46, sb);
            c.fill(21, 0, 44, 26, 0, 47, M.GRASS_PATH);
            c.fill(22, 0, 36, 25, 0, 43, M.GRASS_PATH);
            // the graves
            for (int v = 6; v <= 40; v += 4) for (int u = 4; u <= 43; u += 3) {
                if (u >= 13 && u <= 34 && v >= 8 && v <= 37) continue;
                if (c.chance(u, 0, v, 0.15, 52)) continue;
                grave(c, u, v, M.N, r);
            }
            // dead trees
            for (int i = 0; i < 3; i++) {
                int u = i == 0 ? 6 : i == 1 ? 41 : 40, v = i == 0 ? 42 : i == 1 ? 5 : 42;
                post(c, u, v, 1, 5, Canvas.of(M.LOG_DARK));
                c.set(u + 1, 4, v, M.LOG_DARK_X); c.set(u + 2, 5, v, M.LOG_DARK_X); c.set(u, 5, v - 1, M.LOG_DARK_Z); c.set(u, 6, v - 2, M.LOG_DARK_Z);
            }
            // the mausoleum
            int u0 = 15, u1 = 32, v0 = 10, v1 = 31;
            c.fill(u0 - 1, 0, v0 - 1, u1 + 1, 0, v1 + 4, Canvas.mix(M.POL_ANDESITE, 3, M.SB, 2, M.CRACKED_SB, 1));
            c.walls(u0, 1, v0, u1, 9, v1, sb);
            c.air(u0 + 1, 1, v0 + 1, u1 - 1, 9, v1 - 1);
            for (int u = u0; u <= u1; u += 3) { post(c, u, v0, 1, 9, Canvas.of(M.CHISELED_SB)); }
            // stepped roof
            for (int k = 0; k < 5; k++) c.fill(u0 - 1 + k, 10 + k, v0 - 1 + k, u1 + 1 - k, 10 + k, v1 + 1 - k, k == 4 ? Canvas.of(M.SB) : Canvas.mix(M.SB, 4, M.MOSSY_SB, 2, M.CRACKED_SB, 2));
            c.air(u0 + 4, 10, v0 + 4, u1 - 4, 13, v1 - 4);
            c.decay(u0 - 1, 10, v0 - 1, u1 + 1, 14, v1 + 1, 0.06, 53);
            // portico: columns, a pediment, steps
            for (int u = u0 + 1; u <= u1 - 1; u += 3) { post(c, u, v1 + 3, 1, 7, Canvas.of(M.axis(M.QUARTZ, 0))); c.set(u, 8, v1 + 3, M.CHISELED_SB); }
            c.fill(u0, 9, v1 + 1, u1, 9, v1 + 3, M.SB);
            for (int k = 0; k < 4; k++) c.fill(u0 + 1 + k * 2, 10 + k, v1 + 3, u1 - 1 - k * 2, 10 + k, v1 + 3, M.SB);
            for (int k = 0; k < 3; k++) c.fill(u0 + 2, 0 + 0, v1 + 4 + k, u1 - 2, 0, v1 + 4 + k, M.SB);
            c.air(22, 1, v1, 25, 5, v1);
            c.set(21, 4, v1 + 1, M.wallTorch(M.S)); c.set(26, 4, v1 + 1, M.wallTorch(M.S));
            loreSign(c, 22, 3, v1 + 4, M.S, 0);
            // inside: sarcophagi and candles, the stair down
            for (int v = v0 + 3; v <= v1 - 4; v += 5) {
                c.fill(u0 + 2, 1, v, u0 + 3, 1, v + 1, M.POL_ANDESITE); c.fill(u0 + 2, 2, v, u0 + 3, 2, v + 1, M.SB_SLAB);
                c.fill(u1 - 3, 1, v, u1 - 2, 1, v + 1, M.POL_ANDESITE); c.fill(u1 - 3, 2, v, u1 - 2, 2, v + 1, M.SB_SLAB);
                c.set(u0 + 1, 3, v, M.wallTorch(M.E)); c.set(u1 - 1, 3, v, M.wallTorch(M.W));
            }
            c.chest(u0 + 1, 1, v0 + 1, M.S, "lair_zombie_cache");
            // the crypt hall below the cemetery (y -10 .. -1), its stair from the mausoleum
            int a0 = 10, a1 = 37, b0 = 6, b1 = 35;
            c.room(a0, -10, b0, a1, -1, b1, sb, Canvas.mix(M.SB, 3, M.CRACKED_SB, 2, M.SOUL_SAND, 1, M.COARSE, 1), Canvas.of(M.SB));
            for (int u = a0 + 5; u < a1; u += 6) for (int v = b0 + 5; v < b1; v += 6) post(c, u, v, -9, -2, Canvas.of(M.CHISELED_SB));
            // burial niches in the long walls
            for (int v = b0 + 2; v <= b1 - 2; v += 3) {
                for (int u : new int[]{a0, a1}) { c.air(u, -8, v, u, -8, v + 1); if (c.chance(u, -8, v, 0.5, 54)) c.set(u, -8, v, M.SKULL); }
                c.set(a0 + 1, -6, v, M.wallTorch(M.E)); c.set(a1 - 1, -6, v + 1, M.wallTorch(M.W));
            }
            for (int i = 0; i < 26; i++) { int u = a0 + 1 + r.nextInt(a1 - a0 - 1), v = b0 + 1 + r.nextInt(b1 - b0 - 1); c.soft(u, -9, v, r.nextInt(3) == 0 ? M.WEB : r.nextInt(2) == 0 ? M.BONE : M.SKULL); }
            stairway(c, 22, -9, 19, M.S, 10, 4, M.SB_STAIRS, Canvas.of(M.SB));
            c.spawner(13, -10, 32, "zombie");
            // the ossuary (vault) behind the crypt's north wall, sealed with bars
            c.room(18, -10, 1, 29, -4, b0, sb, Canvas.of(M.SB), Canvas.of(M.SB));
            c.air(19, -9, 2, 28, -5, b0 - 1);
            for (int u = 23; u <= 24; u++) for (int y = -9; y <= -8; y++) c.seal(u, y, b0, M.IRON_BARS);
            c.vaultChest(19, -9, 3, M.E, "lair_zombie_vault");
            c.vaultChest(28, -9, 3, M.W, "lair_zombie_vault");
            c.fill(21, -9, 2, 26, -9, 2, Canvas.mix(M.BONE, 2, M.SKULL, 1));
            c.set(23, -6, 2, M.wallTorch(M.S));
            loreSign(c, 24, -7, 2, M.S, 1);
            c.vault(18, -10, 1, 29, -4, b0);
            c.boss(23, -9, 27, 18);
            c.garrison(8, 1, 12, "zombie+zombie");
            c.garrison(40, 1, 30, "zombie_villager+zombie");
            c.garrison(14, -9, 12, "husk+zombie");
            c.garrison(33, -9, 30, "zombie+zombie_villager+zombie");
            c.garrison(18, 1, 22, "zombie");
            c.garrison(30, -9, 10, "zombie+husk");
        }
    }
}
