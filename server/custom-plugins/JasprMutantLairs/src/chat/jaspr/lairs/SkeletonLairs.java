package chat.jaspr.lairs;

import java.util.Random;

/** The Mutant Skeleton's three lairs. */
final class SkeletonLairs {
    private SkeletonLairs() {}

    /** The stair-block direction (N/E/S/W) of travel at angle a (radians) going round anticlockwise in u-v. */
    static int tangent(double a) {
        double du = -Math.sin(a), dv = Math.cos(a);
        if (Math.abs(du) > Math.abs(dv)) return du > 0 ? M.E : M.W;
        return dv > 0 ? M.S : M.N;
    }

    // ================================================================ 4. Ossuary Spire
    /** A tower of bone and stone in a ring of rib spikes, a stair winding up to a sealed reliquary on its crown. */
    static final class OssuarySpire extends Design {
        OssuarySpire() {
            super("ossuary_spire", "The Ossuary Spire", "mutant_skeleton", "The Bone Regent", 44, 44, 10,
                "Every bone that|fell in the war|was brought|here, stacked.~The Regent|counts each one|The reliquary|opens to none.");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 22, cv = 22;
            c.padCircle(cu, cv, 21.5, 42);
            Canvas.Mat yard = Canvas.mix(M.GRAVEL, 4, M.ANDESITE, 2, M.COARSE, 3, M.BONE, 1, M.POL_ANDESITE, 1);
            Canvas.Mat towerMat = Canvas.mix(M.BONE, 5, M.SB, 3, M.CRACKED_SB, 2, M.ANDESITE, 1);
            c.disc(cu, cv, 20.5, 0, yard);
            // the outer ring wall with rib spikes leaning in, a gap at the south
            for (int deg = 0; deg < 360; deg += 2) {
                double a = Math.toRadians(deg);
                int u = (int) Math.round(cu + Math.cos(a) * 20), v = (int) Math.round(cv + Math.sin(a) * 20);
                if (v > cv + 15 && Math.abs(u - cu) <= 2) continue;
                c.fill(u, 1, v, u, 3, v, Canvas.mix(M.BONE, 4, M.SB, 1, M.MOSSY_SB, 1));
                if (c.chance(u, 3, v, 0.18, 61)) c.set(u, 3, v, M.AIR);
            }
            for (int deg = 7; deg < 360; deg += 15) {
                double a = Math.toRadians(deg);
                double su = cu + Math.cos(a) * 20, sv = cv + Math.sin(a) * 20;
                if (sv > cv + 15 && Math.abs(su - cu) <= 4) continue;
                double tu = cu + Math.cos(a) * 16.5, tv = cv + Math.sin(a) * 16.5;
                c.beam(su, 3, sv, tu, 10 + r.nextInt(3), tv, 0.5, Canvas.of(M.BONE));
            }
            // skull posts and bone heaps in the yard
            for (int deg = 15; deg < 360; deg += 30) {
                double a = Math.toRadians(deg);
                int u = (int) Math.round(cu + Math.cos(a) * 13.5), v = (int) Math.round(cv + Math.sin(a) * 13.5);
                c.set(u, 1, v, M.COBBLE_WALL); c.set(u, 2, v, M.COBBLE_WALL); c.set(u, 3, v, M.SKULL);
            }
            for (int i = 0; i < 9; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 8.5 + r.nextDouble() * 9;
                c.ellipsoid(cu + Math.cos(a) * rad, 1, cv + Math.sin(a) * rad, 1.5, 1.4, 1.5, bones(), false, 1);
            }
            c.scatter(2, 2, 41, 41, 1, 0.025, Canvas.mix(M.SKULL, 2, M.WEB, 1, M.DEAD_BUSH, 1), 62);
            // target dummies the skeletons practised on
            for (int k = 0; k < 4; k++) {
                int u = k < 2 ? 9 : 35, v = k % 2 == 0 ? 12 : 32;
                c.set(u, 1, v, M.FENCE); c.set(u, 2, v, M.HAY); c.set(u, 3, v, M.jack(u < cu ? M.E : M.W));
            }
            // the tower: shell r 6, chiselled bands, windows, a door at the south
            c.cylinder(cu, cv, 6, 1, 30, towerMat, true);
            c.disc(cu, cv, 5, 0, Canvas.of(M.SB));
            for (int y = 6; y <= 30; y += 6) c.ring(cu, cv, 6, y, Canvas.of(M.CHISELED_SB));
            for (int y = 7; y <= 25; y += 6) {
                c.set(cu + 6, y, cv, M.IRON_BARS); c.set(cu + 6, y + 1, cv, M.IRON_BARS); c.set(cu - 6, y, cv, M.IRON_BARS); c.set(cu - 6, y + 1, cv, M.IRON_BARS);
                c.set(cu, y, cv + 6, M.IRON_BARS); c.set(cu, y + 1, cv + 6, M.IRON_BARS); c.set(cu, y, cv - 6, M.IRON_BARS); c.set(cu, y + 1, cv - 6, M.IRON_BARS);
            }
            c.air(cu - 1, 1, cv + 5, cu + 1, 3, cv + 6);
            c.set(cu - 2, 3, cv + 6, M.wallTorch(M.S)); c.set(cu + 2, 3, cv + 6, M.wallTorch(M.S));
            // the winding stair round a bone core: eight steps a turn
            post(c, cu, cv, 1, 30, Canvas.of(M.BONE));
            int[][] top = new int[6][];
            int ti = 0;
            for (int i = 0; i < 30; i++) {
                double a = i * Math.PI / 4;
                int dir = tangent(a);
                for (double rad : new double[]{1.25, 2.3}) {
                    int u = cu + (int) Math.round(Math.cos(a) * rad), v = cv + (int) Math.round(Math.sin(a) * rad);
                    c.set(u, 1 + i, v, M.stairs(M.SB_STAIRS, dir, false));
                    if (i >= 27 && ti < top.length) top[ti++] = new int[]{u, v};
                }
                if (i % 8 == 4) {
                    // a torch on the inside of the wall, pointing in
                    int tu = cu + (int) Math.round(Math.cos(a) * 4.6), tv = cv + (int) Math.round(Math.sin(a) * 4.6);
                    double du = -Math.cos(a), dv = -Math.sin(a);
                    int td = Math.abs(du) > Math.abs(dv) ? (du > 0 ? M.E : M.W) : (dv > 0 ? M.S : M.N);
                    c.set(tu, 3 + i, tv, M.wallTorch(td));
                    c.set(tu - (td == M.E ? 1 : td == M.W ? -1 : 0), 3 + i, tv - (td == M.S ? 1 : td == M.N ? -1 : 0), M.SB);
                }
            }
            c.spawner(cu - 3, 0, cv, "skeleton");
            c.chest(cu + 4, 1, cv, M.W, "lair_skeleton_cache");
            // the crown: a platform overhanging the tower, bone spikes round it
            c.disc(cu, cv, 8, 31, Canvas.mix(M.SB, 3, M.BONE, 2, M.CRACKED_SB, 1));
            for (int deg = 0; deg < 360; deg += 20) {
                double a = Math.toRadians(deg);
                int u = (int) Math.round(cu + Math.cos(a) * 8), v = (int) Math.round(cv + Math.sin(a) * 8);
                c.fill(u, 32, v, u, 32 + (deg % 40 == 0 ? 3 : 1), v, M.BONE);
            }
            // the reliquary (vault): sealed where the stair comes up through the crown
            c.cylinder(cu, cv, 4, 32, 37, Canvas.mix(M.BONE, 3, M.CHISELED_SB, 1), true);
            for (int deg = 45; deg < 360; deg += 90) c.set(cu + (int) Math.round(Math.cos(Math.toRadians(deg)) * 4), 34, cv + (int) Math.round(Math.sin(Math.toRadians(deg)) * 4), M.IRON_BARS);
            c.disc(cu, cv, 4.5, 38, Canvas.of(M.BONE));
            c.set(cu, 39, cv, M.BONE); c.set(cu, 40, cv, M.SKULL);
            c.cylinder(cu, cv, 3, 32, 37, Canvas.of(M.AIR), false);
            c.set(cu, 32, cv, M.BONE);
            for (int[] p : top) if (p != null) c.seal(p[0], 31, p[1], M.IRON_BARS);
            c.vaultChest(cu + 2, 32, cv - 1, M.W, "lair_skeleton_vault");
            c.vaultChest(cu + 2, 32, cv + 1, M.W, "lair_skeleton_vault");
            c.set(cu, 36, cv + 3, M.wallTorch(M.N));
            loreSign(c, cu, 33, cv + 3, M.N, 1);
            c.vault(cu - 8, 31, cv - 8, cu + 8, 41, cv + 8);
            loreSign(c, cu - 2, 2, cv + 6, M.S, 0);
            c.air(cu - 1, 1, cv + 13, cu + 1, 3, cv + 15);
            c.boss(cu, 1, cv + 14, 22);
            c.garrison(cu, 1, cv + 17, "skeleton+skeleton+stray");
            c.garrison(cu - 12, 1, cv, "stray+skeleton");
            c.garrison(cu + 12, 1, cv, "skeleton+skeleton");
            c.garrison(cu, 1, cv - 12, "skeleton+stray");
            c.garrison(cu + 3, 1, cv - 3, "skeleton");
            c.garrison(cu - 9, 1, cv - 9, "stray+stray");
        }
    }

    // ================================================================ 5. Colossus Ribcage
    /** The fossil of something enormous lying on its back in an abandoned dig; its skull is the vault. */
    static final class Ribcage extends Design {
        Ribcage() {
            super("colossus_ribcage", "The Colossus Ribcage", "mutant_skeleton", "The Ribbound Colossus", 60, 32, 9,
                "EXCAVATION 14|Do not wake|the specimen|- Dig office~We found the|skull hollow,|full. Sealed it|again. Wise.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(0, 0, 59, 31, 22);
            Canvas.Mat pitFloor = Canvas.mix(M.COARSE, 4, M.GRAVEL, 3, M.SAND, 1, M.DIRT, 2);
            // the dig: an outer step 2 deep and an inner floor 4 deep
            for (int v = 3; v <= 28; v++) for (int u = 3; u <= 58; u++) {
                boolean inner = u >= 5 && u <= 56 && v >= 5 && v <= 26;
                int depth = inner ? 4 : 2;
                c.air(u, -depth + 1, v, u, 0, v);
                c.set(u, -depth, v, pitFloor.at(c, u, -depth, v));
            }
            // the spine along the floor, vertebrae knobs
            for (int u = 2; u <= 41; u++) {
                c.fill(u, -3, 15, u, -2, 16, M.BONE_X);
                if (u % 3 == 0) { c.set(u, -1, 15, M.BONE); c.set(u, -1, 16, M.BONE); }
            }
            // the ribs: each a closed ellipse round the spine, some broken
            for (int u = 11; u <= 39; u += 4) {
                double cy = 3.5, rvv = 11, ry = 7.2;
                boolean broken = r.nextInt(4) == 0;
                double prevV = 15.5, prevY = -3.5;
                for (int deg = -90; deg <= 270; deg += 10) {
                    double a = Math.toRadians(deg), v = 15.5 + Math.cos(a) * rvv, y = cy + Math.sin(a) * ry;
                    boolean skip = broken && deg > 40 && deg < 140;
                    if (deg > -90 && !skip) c.beam(u, prevY, prevV, u, y, v, 0.55, Canvas.of(M.BONE));
                    prevV = v; prevY = y;
                }
            }
            // the breastbone along the top, cracked
            for (int u = 11; u <= 39; u++) if (!c.chance(u, 10, 15, 0.25, 71)) { c.set(u, 10, 15, M.BONE_X); c.set(u, 10, 16, M.BONE_X); }
            // pelvis and tail at the west end
            c.ellipsoid(6, -1, 11, 3, 3.5, 2.5, Canvas.of(M.BONE), true, -3);
            c.ellipsoid(6, -1, 20, 3, 3.5, 2.5, Canvas.of(M.BONE), true, -3);
            // the skull at the east end, face to the sky: eye sockets on top, teeth at the east
            int su = 50;
            c.ellipsoid(su, 2, 15.5, 7, 6.5, 8, Canvas.mix(M.BONE, 6, M.BONE_X, 1), true, -4);
            c.ellipsoid(su, 2, 15.5, 6, 5.5, 7, Canvas.of(M.AIR), false, -3);
            c.fill(su - 5, -4, 10, su + 5, -4, 21, M.BONE);
            c.disc(su, 15.5, 5.5, -3, Canvas.mix(M.BONE, 3, M.SOUL_SAND, 1));
            c.air(su - 2, 6, 11, su - 1, 8, 12); c.air(su - 2, 6, 19, su - 1, 8, 20);
            c.air(su + 2, 6, 15, su + 3, 8, 16);
            for (int v = 11; v <= 20; v++) if (v % 2 == 0) { c.set(57, -1, v, M.BONE); c.set(57, 0, v, M.COBBLE_WALL); }
            c.fill(su, -4, 9, su + 8, -3, 22, Canvas.mix(M.BONE, 3, M.BONE_X, 1));
            // the neck opening from the ribcage, sealed with bars
            c.air(su - 8, -3, 14, su - 6, -1, 17);
            for (int v = 14; v <= 17; v++) for (int y = -3; y <= -1; y++) c.seal(su - 7, y, v, M.IRON_BARS);
            c.vaultChest(su + 2, -2, 12, M.S, "lair_skeleton_vault");
            c.vaultChest(su + 2, -2, 19, M.N, "lair_skeleton_vault");
            c.set(su + 3, -2, 15, M.SKULL);
            c.set(su, -2, 15, M.TORCH);
            loreSign(c, su - 2, -2, 12, M.S, 1);
            c.vault(su - 8, -4, 7, su + 8, 9, 24);
            // the dig camp on the south rim: tents, crates, scaffolds, a track
            for (int u = 4; u <= 56; u++) if ((u < 7 || u > 11) && (u < 29 || u > 33) && (u < 51 || u > 55) && !c.chance(u, 1, 0, 0.15, 72)) c.set(u, 1, 0, M.RAIL_EW);
            for (int k = 0; k < 3; k++) {
                int u0 = 10 + k * 14;
                for (int u = u0; u <= u0 + 4; u++) { c.set(u, 1, 31, M.wool(M.BROWN)); c.set(u, 2, 31, M.wool(M.BROWN)); c.set(u, 3, 30, M.wool(M.BROWN)); c.set(u, 2, 29, M.wool(M.BROWN)); }
                c.set(u0 + 4, 1, 29, M.SPRUCE_FENCE); c.set(u0 + 4, 2, 29, M.TORCH);
            }
            c.chest(12, 1, 30, M.N, "lair_skeleton_dig");
            c.chest(40, 1, 30, M.N, "lair_skeleton_dig");
            c.set(26, 1, 30, M.CRAFTING); c.set(27, 1, 30, M.LOG_SPRUCE); c.set(28, 1, 30, M.CAULDRON);
            // scaffold towers and ladders down into the dig
            for (int u : new int[]{8, 30, 52}) {
                post(c, u, 2, 1, 5, Canvas.of(M.SPRUCE_FENCE)); post(c, u + 2, 2, 1, 5, Canvas.of(M.SPRUCE_FENCE));
                c.fill(u, 6, 1, u + 2, 6, 2, M.SPRUCE_SLAB);
                c.set(u + 1, 0, 3, M.SPRUCE); c.set(u + 1, -1, 3, M.SPRUCE);
                ladder(c, u + 1, 4, -1, 0, M.S);
                c.set(u + 1, -2, 4, M.SPRUCE); c.set(u + 1, -3, 4, M.SPRUCE);
                ladder(c, u + 1, 5, -3, -2, M.S);
            }
            c.scatter(5, 5, 44, 26, -3, 0.03, Canvas.mix(M.SKULL, 1, M.WEB, 1, M.BONE, 2, M.COBBLE, 1), 73);
            c.spawner(8, -4, 24, "skeleton");
            c.set(4, 1, 1, M.COBBLE_WALL); c.set(4, 2, 1, M.COBBLE_WALL);
            loreSign(c, 4, 2, 0, M.N, 0);
            c.air(26, -3, 10, 26, -1, 10);
            c.boss(26, -3, 10, 20);
            c.garrison(16, -3, 9, "skeleton+skeleton");
            c.garrison(30, -3, 22, "stray+skeleton");
            c.garrison(36, -3, 8, "skeleton+stray+skeleton");
            c.garrison(22, 1, 30, "skeleton+skeleton");
            c.garrison(45, -1, 4, "stray+skeleton");
            c.garrison(10, -3, 18, "skeleton");
        }
    }

    // ================================================================ 6. Bone Barbican
    /** A ruined archers' fort: curtain walls with a walk, four round towers, a gatehouse, a keep with a sealed armoury. */
    static final class Barbican extends Design {
        Barbican() {
            super("bone_barbican", "The Bone Barbican", "mutant_skeleton", "The Marrow Marshal", 46, 46, 10,
                "THE BARBICAN|Archers of the|Marrow Watch~The Marshal's|bow was never|unstrung. Mind|the keep stair.");
        }
        @Override void draw(Canvas c, Random r) {
            c.pad(0, 0, 45, 45, 24);
            Canvas.Mat stone = Canvas.mix(M.SB, 5, M.MOSSY_SB, 2, M.CRACKED_SB, 2, M.COBBLE, 1, M.MOSSY_COBBLE, 1);
            c.fill(4, 0, 4, 41, 0, 41, Canvas.mix(M.GRASS, 4, M.COARSE, 3, M.GRAVEL, 2, M.GRASS_PATH, 1));
            // curtain walls, two thick, a walk on top and crenels
            c.walls(3, 1, 3, 42, 7, 42, stone);
            c.walls(4, 1, 4, 41, 6, 41, stone);
            c.walls(4, 7, 4, 41, 7, 41, Canvas.mix(M.SB, 4, M.SB_SLAB_TOP, 1));
            crenels(c, 3, 3, 42, 42, 8, stone);
            // arrow slits
            for (int k = 8; k <= 38; k += 5) { c.set(k, 4, 3, M.IRON_BARS); c.set(k, 4, 4, M.AIR); c.set(3, 4, k, M.IRON_BARS); c.set(4, 4, k, M.AIR); c.set(42, 4, k, M.IRON_BARS); c.set(41, 4, k, M.AIR); }
            // four round towers
            int[][] towers = {{5, 5}, {40, 5}, {5, 40}, {40, 40}};
            for (int[] t : towers) {
                c.cylinder(t[0], t[1], 4.5, 1, 12, stone, true);
                c.cylinder(t[0], t[1], 3.5, 1, 12, Canvas.of(M.AIR), false);
                c.disc(t[0], t[1], 3.5, 6, Canvas.of(M.SPRUCE));
                c.disc(t[0], t[1], 4.5, 12, Canvas.of(M.SB));
                for (int deg = 0; deg < 360; deg += 30) c.set(t[0] + (int) Math.round(Math.cos(Math.toRadians(deg)) * 4.5), 13, t[1] + (int) Math.round(Math.sin(Math.toRadians(deg)) * 4.5), M.SB);
                int du = t[0] < 22 ? 1 : -1, dv = t[1] < 22 ? 1 : -1;
                c.air(t[0] + du * 3, 1, t[1] + dv * 3, t[0] + du * 3, 2, t[1] + dv * 3);
                c.air(t[0] + du * 4, 8, t[1] - dv * 2, t[0] + du * 4, 9, t[1] - dv * 1);
                ladder(c, t[0] - du * 3, t[1], 1, 11, du > 0 ? M.E : M.W);
                c.air(t[0] - du * 3, 6, t[1], t[0] - du * 3, 6, t[1]);
                c.air(t[0] - du * 3, 12, t[1], t[0] - du * 3, 12, t[1]);
                c.set(t[0] - du * 3, 6, t[1], M.ladder(du > 0 ? M.E : M.W));
                c.set(t[0] - du * 3, 12, t[1], M.ladder(du > 0 ? M.E : M.W));
                c.set(t[0], 1, t[1], M.TORCH);
            }
            // the gatehouse at the south
            c.fill(16, 1, 38, 19, 11, 44, stone); c.fill(26, 1, 38, 29, 11, 44, stone);
            crenels(c, 16, 38, 19, 44, 12, stone); crenels(c, 26, 38, 29, 44, 12, stone);
            c.air(20, 1, 40, 25, 5, 44);
            for (int u = 20; u <= 25; u++) if (!c.chance(u, 5, 42, 0.3, 81)) { c.set(u, 6, 42, M.SB); c.set(u, 5, 42, M.IRON_BARS); }
            c.fill(20, 0, 40, 25, 0, 45, M.GRASS_PATH);
            c.set(19, 3, 45, M.wallTorch(M.S)); c.set(26, 3, 45, M.wallTorch(M.S));
            loreSign(c, 18, 2, 45, M.S, 0);
            // a stair up to the west walk
            stairway(c, 6, 1, 30, M.N, 7, 2, M.SB_STAIRS, stone);
            c.set(5, 7, 24, M.SB); c.set(5, 7, 23, M.SB);
            // the keep against the north wall
            int k0 = 14, k1 = 31;
            c.room(k0, 0, 5, k1, 15, 15, stone, Canvas.of(M.SB), Canvas.of(M.SB));
            c.fill(k0 + 1, 6, 6, k1 - 1, 6, 14, M.SPRUCE);
            crenels(c, k0, 5, k1, 15, 16, stone);
            c.air(21, 1, 15, 24, 3, 15);
            c.set(20, 3, 16, M.wallTorch(M.S)); c.set(25, 3, 16, M.wallTorch(M.S));
            // armoury below: racks, anvil, chests
            c.chest(k0 + 1, 1, 8, M.E, "lair_skeleton_armory");
            c.chest(k1 - 1, 1, 8, M.W, "lair_skeleton_armory");
            c.set(k0 + 1, 1, 12, M.ANVIL); c.set(k1 - 1, 1, 12, M.CAULDRON); c.set(k0 + 1, 1, 10, M.CRAFTING);
            for (int u = k0 + 3; u <= k1 - 3; u += 2) { c.set(u, 1, 6, M.FENCE); c.set(u, 2, 6, M.FENCE); }
            c.set(22, 4, 6, M.wallTorch(M.S)); c.set(16, 4, 14, M.wallTorch(M.N));
            // the ladder up to the sealed upper hall (vault)
            ladder(c, k1 - 1, 14, 1, 5, M.N);
            c.seal(k1 - 1, 6, 14, M.IRON_BARS);
            for (int u = k0 + 2; u <= k1 - 2; u += 4) { c.set(u, 10, 5, M.IRON_BARS); c.set(u, 11, 5, M.IRON_BARS); c.set(u, 10, 15, M.IRON_BARS); c.set(u, 11, 15, M.IRON_BARS); }
            c.vaultChest(18, 7, 7, M.S, "lair_skeleton_vault");
            c.vaultChest(25, 7, 7, M.S, "lair_skeleton_vault");
            c.set(22, 7, 7, M.SKULL); c.set(21, 7, 7, M.BONE); c.set(23, 7, 7, M.BONE);
            c.set(22, 10, 14, M.wallTorch(M.N));
            loreSign(c, 20, 8, 14, M.N, 1);
            c.vault(k0, 6, 5, k1, 16, 15);
            // the archery range along the east and the yard's clutter
            for (int v = 14; v <= 34; v += 5) { c.set(37, 1, v, M.FENCE); c.set(37, 2, v, M.HAY); c.set(37, 3, v, M.HAY); }
            for (int v = 14; v <= 34; v++) c.set(28, 0, v, M.SMOOTH_STONE);
            c.cylinder(12, 32, 1.6, 1, 1, Canvas.of(M.MOSSY_COBBLE), true); c.set(12, 0, 32, M.WATER); c.set(12, 1, 32, M.AIR);
            c.scatter(5, 17, 40, 37, 1, 0.025, Canvas.mix(M.HAY, 2, M.SKULL, 1, M.BONE, 1, M.COBBLE_SLAB, 1), 82);
            // two breaches, their rubble outside
            c.decay(41, 2, 18, 42, 8, 25, 0.6, 83);
            c.decay(3, 3, 10, 4, 8, 14, 0.55, 84);
            for (int i = 0; i < 18; i++) c.soft(43 + r.nextInt(3), 1, 17 + r.nextInt(10), rubble().at(c, 43, 1, 17 + i));
            c.spawner(40, 0, 40, "skeleton");
            c.air(22, 1, 27, 22, 3, 27);
            c.boss(22, 1, 27, 20);
            c.garrison(10, 8, 3, "skeleton+skeleton");
            c.garrison(3, 8, 20, "skeleton+stray");
            c.garrison(42, 8, 30, "skeleton+skeleton");
            c.garrison(12, 1, 21, "stray+skeleton");
            c.garrison(33, 1, 33, "skeleton+skeleton+stray");
            c.garrison(40, 7, 5, "skeleton");
        }
    }
}
