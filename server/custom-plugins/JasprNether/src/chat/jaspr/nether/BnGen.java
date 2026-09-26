package chat.jaspr.nether;

import java.util.Random;
import net.minecraft.server.v1_12_R1.NoiseGeneratorOctaves;

/**
 * BetterNether 0.1.8.6 generation (BNWorldGenerator.smoothChunk + generate) restricted to NetherEx Hell regions:
 * the second smoothing pass, the 1/16-per-chunk random structures, per-biome surfaces and floor/wall/ceiling flora,
 * and cincinnasite ore. Mod blocks are written as their vanilla stand-ins (see Blocks).
 */
final class BnGen {
    private static final String[] LAND = {null, "bn_altar_01", "bn_altar_02", "bn_altar_03", "bn_altar_04", "bn_altar_05", "bn_altar_06",
        "bn_portal_01", "bn_portal_02", "bn_garden_01", "bn_garden_02", "bn_pillar_01", "bn_respawn_point_01", "bn_respawn_point_02"};
    private static final int[] LAND_OFFSET = {0, -1, -4, -3, -3, -2, -2, -4, -3, -3, -2, -1, -3, -2};
    private static final String[] BONES = {"bn_bone_01", "bn_bone_02", "bn_bone_03"};

    private final Gen g;
    private final NoiseGeneratorOctaves scatter = new NoiseGeneratorOctaves(new Random(1337), 3);
    private final double[] noiseBuf = new double[1];
    private final BnNoise.Worley2D magmaOffset = new BnNoise.Worley2D(0L);

    BnGen(Gen g) { this.g = g; }

    private double featureNoise(int x, int z) {
        return scatter.a(noiseBuf, x, z, 1, 1, 0.1, 0.1, 1.0)[0];
    }

    private static boolean nr(int id) { return id == Blocks.NETHERRACK || id == Blocks.NETHER_MYCELIUM >> 4 || id == Blocks.NETHERRACK_MOSS >> 4; }
    private static boolean nrs(int id) { return nr(id) || id == Blocks.SOUL_SAND; }

    void populate(Area a, Random r, Gen.Post post) {
        int bx = a.ox + 8, bz = a.oz + 8;
        boolean[] hell = new boolean[256];
        boolean any = false;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            hell[x * 16 + z] = g.biomes.nex(bx + x, bz + z) == Biomes.Nex.HELL;
            any |= hell[x * 16 + z];
        }
        structures(a, r, post, hell);
        if (!any) return;
        smooth(a, hell);
        Biomes.Bn[] memo = new Biomes.Bn[8 * 64 * 8];
        for (int x = 0; x < 16; x++) {
            int wx = bx + x;
            for (int z = 0; z < 16; z++) {
                if (!hell[x * 16 + z]) continue;
                int wz = bz + z;
                for (int y = 5; y < 126; y++) {
                    int id = a.id(wx, y, wz);
                    if (Blocks.isFullSolid(id)) {
                        Biomes.Bn b = local(memo, bx, bz, x, y, z, r);
                        if (a.air(wx, y + 1, wz)) {
                            surface(a, r, b, wx, y, wz);
                            floor(a, r, b, wx, y, wz, post);
                        } else if (a.air(wx, y - 1, wz)) {
                            ceiling(a, r, b, wx, y, wz);
                        } else if (((x + y + z) & 1) == 0) {
                            int ox = 0, oz = 0;
                            if (a.air(wx, y, wz - 1)) oz = -1;
                            else if (a.air(wx, y, wz + 1)) oz = 1;
                            else if (a.air(wx + 1, y, wz)) ox = 1;
                            else if (a.air(wx - 1, y, wz)) ox = -1;
                            if ((ox != 0 || oz != 0) && a.air(wx + ox, y + 1, wz + oz) && a.air(wx + ox, y - 1, wz + oz)) {
                                wall(a, r, b, wx, y, wz, wx + ox, y, wz + oz);
                                if (y < 37 && id == Blocks.NETHER_BRICK && r.nextInt(512) == 0) wartCap(a, r, wx, y, wz);
                            }
                        }
                    }
                    if (r.nextInt(1024) == 0) cincinnasite(a, r, wx, y, wz);
                }
            }
        }
    }

    private Biomes.Bn local(Biomes.Bn[] memo, int bx, int bz, int x, int y, int z, Random r) {
        int lx = Math.min(7, (x + r.nextInt(2)) >> 1), ly = Math.min(63, (y + r.nextInt(2)) >> 1), lz = Math.min(7, (z + r.nextInt(2)) >> 1);
        int i = (lx * 64 + ly) * 8 + lz;
        Biomes.Bn b = memo[i];
        if (b == null) { b = g.biomes.bn(bx + (lx << 1), ly << 1, bz + (lz << 1)); memo[i] = b; }
        return b;
    }

    // ---- smoothChunk (second pass) ------------------------------------------------------------------------------
    private void smooth(Area a, boolean[] hell) {
        int bx = a.ox + 8, bz = a.oz + 8;
        java.util.List<int[]> clear = new java.util.ArrayList<>();
        for (int y = 32; y < 110; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (!hell[x * 16 + z]) continue;
            int wx = bx + x, wz = bz + z;
            int id = a.id(wx, y, wz);
            if (!(nrs(id) || id == Blocks.GRAVEL)) continue;
            if (a.air(wx, y, wz - 1) && a.air(wx, y, wz + 1) || a.air(wx + 1, y, wz) && a.air(wx - 1, y, wz)
                || a.air(wx, y + 1, wz) && a.air(wx, y - 1, wz)
                || a.air(wx + 1, y - 1, wz - 1) && a.air(wx - 1, y + 1, wz + 1)
                || a.air(wx + 1, y - 1, wz + 1) && a.air(wx - 1, y + 1, wz - 1)
                || a.air(wx - 1, y - 1, wz - 1) && a.air(wx + 1, y + 1, wz + 1)
                || a.air(wx - 1, y - 1, wz + 1) && a.air(wx + 1, y + 1, wz - 1))
                clear.add(new int[]{wx, y, wz});
        }
        for (int[] p : clear) a.set(p[0], p[1], p[2], 0, 0);
    }

    // ---- random structures (1/16 per chunk, jar default) -----------------------------------------------------------
    private void structures(Area a, Random r, Gen.Post post, boolean[] hell) {
        if (r.nextFloat() >= 0.0625f) return;
        int x = a.ox + 8 + r.nextInt(8), y = 32 + r.nextInt(88), z = a.oz + 8 + r.nextInt(8);
        while (!a.air(x, y, z) && y > 32) y--;
        int gy = -1;
        for (int j = y; j > 31; j--) {
            int id = a.id(x, j, z);
            if (id != 0 && (nrs(id) || id == Blocks.LAVA || id == Blocks.LAVA_FLOW || Blocks.rack(a.get(x, j, z)))) { gy = j; break; }
        }
        if (gy < 0) return;
        boolean open = true;
        for (int k = 1; k < 8; k++) if (!a.air(x, gy + k, z)) { open = false; break; }
        int rot = r.nextInt(4);
        if (open) {
            if (!hell[(x - a.ox - 8) * 16 + (z - a.oz - 8)]) return;
            int pick = r.nextInt(LAND.length);
            if (pick == 0) { altar(a, x, gy + 1, z); return; }
            Template t = g.t(LAND[pick]);
            int py = gy + 1 + LAND_OFFSET[pick];
            int ox = x - (t.width(rot) >> 1), oz = z - (t.depth(rot) >> 1);
            Template.Placed p = new Template.Placed();
            t.place(a, ox, py, oz, rot, p);
            post.add(p, LAND[pick]);
            String name = LAND[pick].substring(3);
            g.registry.add("bn", name, ox, py, oz, ox + t.width(rot) - 1, py + t.sy - 1, oz + t.depth(rot) - 1);
            g.count("bn_" + name.replaceAll("_0\\d$", ""));
        } else {
            Template t = g.t("bn_room_01");
            int py = gy - 5;
            int ox = x - (t.width(rot) >> 1), oz = z - (t.depth(rot) >> 1);
            Template.Placed p = new Template.Placed();
            t.place(a, ox, py, oz, rot, p);
            post.add(p, "room");
            g.registry.add("bn", "cave_room", ox, py, oz, ox + t.width(rot) - 1, py + t.sy - 1, oz + t.depth(rot) - 1);
            g.count("bn_cave_room");
        }
    }

    /** StructureAltar: cincinnasite pillar with a lit fire bowl and four corner walls. */
    private void altar(Area a, int x, int y, int z) {
        int below = a.id(x, y - 1, z);
        if (!nrs(below) || !a.air(x, y, z) || !a.air(x, y + 1, z)) return;
        a.set(x, y, z, Blocks.CINCINNASITE_PILLAR);
        a.set(x, y + 1, z, Blocks.CINCINNASITE_BOWL);
        int[][] d = {{1, -1}, {-1, -1}, {1, 1}, {-1, 1}};
        for (int[] o : d) {
            a.set(x + o[0], y, z + o[1], Blocks.CINCINNASITE_WALL);
            if (!Blocks.isFullSolid(a.id(x + o[0], y - 1, z + o[1]))) a.set(x + o[0], y - 1, z + o[1], Blocks.NETHERRACK, 0);
        }
        g.registry.add("bn", "altar", x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
        g.count("bn_altar");
    }

    // ---- surfaces ----------------------------------------------------------------------------------------------
    private void surface(Area a, Random r, Biomes.Bn b, int x, int y, int z) {
        int id = a.id(x, y, z);
        switch (b) {
            case GRAVEL_DESERT: {
                int n = 1 + r.nextInt(3);
                for (int i = 1; i < n; i++) {
                    if (a.id(x, y - i, z) != Blocks.NETHERRACK) continue;
                    if (!a.air(x, y - i - 1, z)) a.set(x, y - i, z, Blocks.GRAVEL, 0);
                }
                if (id == Blocks.NETHERRACK) a.set(x, y, z, Blocks.GRAVEL, 0);
                break;
            }
            case NETHER_JUNGLE: case GRASSLANDS: case POOR_GRASSLANDS: case BONE_REEF:
                if (id == Blocks.NETHERRACK) {
                    int k = r.nextInt(3);
                    if (k == 0) a.set(x, y, z, Blocks.SOUL_SAND, 0);
                    else if (k == 1) a.set(x, y, z, Blocks.NETHERRACK_MOSS);
                }
                break;
            case WART_FOREST: case WART_FOREST_EDGE:
                if (id == Blocks.NETHERRACK) {
                    a.set(x, y, z, r.nextInt(3) < 2 ? Blocks.SOUL_SAND << 4 : Blocks.NETHERRACK_MOSS);
                    subSoul(a, r, x, y, z);
                }
                break;
            case MUSHROOM_FOREST: case MUSHROOM_FOREST_EDGE:
                if (id == Blocks.NETHERRACK) {
                    if (r.nextInt(b == Biomes.Bn.MUSHROOM_FOREST ? 10 : 5) == 0) {
                        a.set(x, y, z, Blocks.SOUL_SAND, 0);
                        a.set(x, y + 1, z, Blocks.NETHER_WART, r.nextInt(4));
                    } else a.set(x, y, z, Blocks.NETHER_MYCELIUM);
                    subSoul(a, r, x, y, z);
                }
                break;
            default:
        }
    }

    private void subSoul(Area a, Random r, int x, int y, int z) {
        int n = 1 + r.nextInt(3);
        for (int i = 1; i < n; i++) if (r.nextInt(3) == 0 && a.id(x, y - i, z) == Blocks.NETHERRACK) a.set(x, y - i, z, Blocks.SOUL_SAND, 0);
    }

    // ---- floor objects -------------------------------------------------------------------------------------------
    private void floor(Area a, Random r, Biomes.Bn b, int x, int y, int z, Gen.Post post) {
        int ground = a.id(x, y, z);
        switch (b) {
            case GRAVEL_DESERT:
                if (ground == Blocks.GRAVEL && (r.nextInt(16) == 0 || featureNoise(x, z) > 0.3)) {
                    if (r.nextInt(8) == 0) {
                        int h = 1 + r.nextInt(3);
                        for (int i = 1; i < h; i++) if (!a.air(x, y + i, z)) { h = i; break; }
                        for (int i = 1; i <= h; i++) a.set(x, y + i, z, Blocks.NETHER_CACTUS);
                        g.count("bn_nether_cactus");
                    } else if (r.nextInt(7) == 0) {
                        if (r.nextBoolean()) a.setIfAir(x, y + 1, z, Blocks.BARREL_CACTUS);
                        else a.setIfAir(x, y + 1, z, Blocks.AGAVE);
                    }
                }
                break;
            case NETHER_JUNGLE:
                if (nrs(ground)) {
                    boolean reeds = r.nextInt(4) == 0 && reeds(a, r, x, y, z);
                    if (!reeds) {
                        if (r.nextInt(8) == 0) stalagnate(a, x, y, z);
                        else if (y < 37 && y > 23 && r.nextInt(32) == 0) magmaFlower(a, x, y, z);
                        else if (r.nextInt(16) == 0) scatter(a, r, x, y, z, 18, Blocks.EGG_PLANT, false, "bn_egg_plant");
                        else if (r.nextInt(3) != 0) grass(a, r, x, y, z);
                    }
                }
                break;
            case WART_FOREST: case WART_FOREST_EDGE:
                if (ground == Blocks.SOUL_SAND) {
                    if (r.nextInt(b == Biomes.Bn.WART_FOREST ? 15 : 35) == 0) wartTree(a, r, x, y, z);
                    else if (r.nextInt(3) == 0 && a.air(x, y + 1, z)) a.set(x, y + 1, z, Blocks.NETHER_WART, r.nextInt(4));
                    else if (b == Biomes.Bn.WART_FOREST_EDGE && r.nextInt(3) == 0) a.setIfAir(x, y + 1, z, Blocks.BLACK_BUSH);
                }
                break;
            case GRASSLANDS: case POOR_GRASSLANDS:
                if (nrs(ground)) {
                    boolean reeds = r.nextInt(4) == 0 && reeds(a, r, x, y, z);
                    if (reeds || (b == Biomes.Bn.POOR_GRASSLANDS && r.nextFloat() >= 0.4f)) break;
                    if (ground == Blocks.SOUL_SAND && r.nextInt(2) == 0) a.setIfAir(x, y + 1, z, Blocks.NETHER_WART, r.nextInt(4));
                    else if (y < 37 && y > 23 && r.nextInt(32) == 0) magmaFlower(a, x, y, z);
                    else if (r.nextInt(16) == 0) smoker(a, r, x, y, z);
                    else if (r.nextInt(16) == 0) scatter(a, r, x, y, z, 10, Blocks.INK_BUSH, false, "bn_ink_bush");
                    else if (r.nextInt(16) == 0) scatter(a, r, x, y, z, 18, Blocks.EGG_PLANT, false, "bn_egg_plant");
                    else if (r.nextInt(16) == 0) scatter(a, r, x, y, z, 8, Blocks.BLACK_APPLE, false, "bn_black_apple");
                    else if (r.nextInt(6) == 0 && featureNoise(x, z) > 0.3) a.setIfAir(x, y + 1, z, Blocks.BLACK_BUSH);
                    else if (r.nextInt(5) == 0) { /* wart seed: no safe vanilla stand-in */ }
                    else if (r.nextInt(4) != 0) grass(a, r, x, y, z);
                }
                break;
            case BONE_REEF:
                if (nrs(ground)) {
                    if (r.nextInt(20) == 0) bone(a, r, x, y - r.nextInt(4), z, post);
                    else if (r.nextInt(4) != 0 && nr(ground)) grass(a, r, x, y, z);
                } else if (ground == Blocks.BONE_BLOCK && r.nextBoolean()) a.setIfAir(x, y + 1, z, Blocks.BONE_MUSHROOM);
                break;
            case MUSHROOM_FOREST: case MUSHROOM_FOREST_EDGE: {
                boolean edge = b == Biomes.Bn.MUSHROOM_FOREST_EDGE;
                if (a.get(x, y, z) == Blocks.NETHER_MYCELIUM) {
                    if (r.nextInt(edge ? 27 : 7) == 0) medRed(a, r, x, y, z);
                    else if (r.nextInt(edge ? 26 : 6) == 0) medBrown(a, r, x, y, z);
                    else if (r.nextInt(edge ? 40 : 20) == 0) scatter(a, r, x, y, z, 10, Blocks.ORANGE_MUSHROOM, true, "bn_orange_mushroom");
                    else if (r.nextInt(edge ? 32 : 12) == 0) mold(a, r, x, y, z, Blocks.RED_MOLD, "bn_red_mold");
                    else if (r.nextInt(edge ? 30 : 10) == 0) mold(a, r, x, y, z, Blocks.GRAY_MOLD, "bn_gray_mold");
                    else if (r.nextInt(edge ? 8 : 3) == 0) a.setIfAir(x, y + 1, z, r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM, 0);
                } else if (edge && nr(ground) && r.nextBoolean()) grass(a, r, x, y, z);
                break;
            }
            default:
        }
    }

    private void wall(Area a, Random r, Biomes.Bn b, int ox, int oy, int oz, int x, int y, int z) {
        if ((b == Biomes.Bn.NETHER_JUNGLE || b == Biomes.Bn.MUSHROOM_FOREST || b == Biomes.Bn.MUSHROOM_FOREST_EDGE)
            && r.nextInt(4) == 0 && nr(a.id(ox, oy, oz))) lucis(a, r, x, y, z);
    }

    private void ceiling(Area a, Random r, Biomes.Bn b, int x, int y, int z) {
        if (b == Biomes.Bn.NETHER_JUNGLE && r.nextInt(8) == 0 && r.nextDouble() * 4 + 0.5 < featureNoise(x, z)) eye(a, r, x, y - 1, z);
    }

    // ---- flora ---------------------------------------------------------------------------------------------------
    /** Nether grass: its stand-in (blocks.tsv) must stay on soul sand, so it is placed there only, one in four. */
    private void grass(Area a, Random r, int x, int y, int z) {
        if (a.id(x, y, z) == Blocks.SOUL_SAND && r.nextInt(4) == 0) a.setIfAir(x, y + 1, z, Blocks.NETHER_GRASS);
    }

    private boolean reeds(Area a, Random r, int x, int y, int z) {
        if (a.id(x + 1, y, z) != Blocks.LAVA && a.id(x - 1, y, z) != Blocks.LAVA && a.id(x, y, z + 1) != Blocks.LAVA && a.id(x, y, z - 1) != Blocks.LAVA)
            return false;
        int h = 1 + r.nextInt(4);
        for (int i = 1; i < h; i++) { if (!a.air(x, y + i, z)) break; a.set(x, y + i, z, Blocks.NETHER_REED); }
        g.count("bn_reeds");
        return true;
    }

    private void stalagnate(Area a, int x, int y, int z) {
        int top = -1;
        for (int j = y + 1; j < 126; j++) if (!a.air(x, j, z)) { top = j; break; }
        if (top < 0) return;
        int dist = top - y;
        if (dist >= 25 || dist <= 2 || !(nr(a.id(x, top, z)) || Blocks.rack(a.get(x, top, z)))) return;
        a.set(x, y + 1, z, Blocks.STALAGNATE_BOTTOM);
        for (int j = 2; j < dist - 1; j++) a.set(x, y + j, z, Blocks.STALAGNATE_MIDDLE);
        a.set(x, top - 1, z, Blocks.STALAGNATE_TOP);
        g.count("bn_stalagnate");
    }

    private void magmaFlower(Area a, int x, int y, int z) {
        if (!nrs(a.id(x, y, z)) && !Blocks.rack(a.get(x, y, z))) return;
        for (int yy = -3; yy < 4; yy++) for (int xx = -3; xx < 4; xx++) {
            int oz = xx + (int) (magmaOffset.value(xx, yy) * 3);
            for (int zz = -3; zz < 4; zz++) {
                int ox = yy + (int) (magmaOffset.value(yy, zz) * 3), oy = zz + (int) (magmaOffset.value(xx, zz) * 3);
                if (ox * ox + oy * oy + oz * oz > 9) continue;
                int px = x + ox, py = y + oy, pz = z + oz;
                int id = a.id(px, py, pz);
                if (Blocks.isFullSolid(id) && id != Blocks.BEDROCK && id != Blocks.NETHER_BRICK) a.set(px, py, pz, Blocks.MAGMA, 0);
                else if (id == 0 && a.id(px, py - 1, pz) == Blocks.MAGMA) a.set(px, py, pz, Blocks.MAGMA_FLOWER);
            }
        }
        g.count("bn_magma_flower");
    }

    /** The egg plant / ink bush / black apple / orange mushroom scatter (gaussian around the origin, onto terrain). */
    private void scatter(Area a, Random r, int x, int y, int z, int tries, int block, boolean mycelium, String what) {
        if (!nrs(a.id(x, y, z))) return;
        int placed = 0;
        for (int i = 0; i < tries; i++) {
            int px = x + (int) (r.nextGaussian() * 2), pz = z + (int) (r.nextGaussian() * 2), py = y + r.nextInt(6);
            for (int j = 0; j < 6; j++) {
                int ny = py - j;
                if (ny <= 31) break;
                int under = a.id(px, ny - 1, pz);
                boolean soil = mycelium ? a.get(px, ny - 1, pz) == Blocks.NETHER_MYCELIUM : nrs(under);
                if (soil) {
                    if (a.air(px, ny, pz)) { a.set(px, ny, pz, block); placed++; }
                    break;
                }
            }
            if (block == Blocks.BLACK_APPLE && placed > 0) break;
        }
        if (placed > 0) g.count(what);
    }

    private void smoker(Area a, Random r, int x, int y, int z) {
        for (int i = 0; i < 8; i++) {
            int px = x + (int) (r.nextGaussian() * 2), pz = z + (int) (r.nextGaussian() * 2), py = y + r.nextInt(6);
            for (int j = 0; j < 6; j++) {
                int ny = py - j;
                if (ny <= 31) return;
                int under = a.id(px, ny - 1, pz);
                if (nrs(under) || Blocks.rack(a.get(px, ny - 1, pz))) {
                    int h = 1 + r.nextInt(5);
                    for (int k = 0; k < h && a.air(px, ny + k, pz); k++) a.set(px, ny + k, pz, Blocks.SMOKER);
                    g.count("bn_smoker");
                    return;
                }
            }
        }
    }

    /** Red / gray mold patches on nether mycelium. */
    private void mold(Area a, Random r, int x, int y, int z, int block, String what) {
        int placed = 0;
        for (int i = 0; i < 32; i++) {
            int px = x + (int) (r.nextGaussian() * 2), pz = z + (int) (r.nextGaussian() * 2), py = y + r.nextInt(6);
            for (int j = 0; j < 6; j++) {
                int ny = py - j;
                if (ny <= 31) break;
                if (a.get(px, ny - 1, pz) == Blocks.NETHER_MYCELIUM && a.air(px, ny, pz)) { a.set(px, ny, pz, block); placed++; break; }
            }
        }
        if (placed > 0) g.count(what);
    }

    private int[] myceliumSpot(Area a, Random r, int x, int y, int z) {
        for (int i = 0; i < 10; i++) {
            int px = x + (int) (r.nextGaussian() * 2), pz = z + (int) (r.nextGaussian() * 2), py = y + r.nextInt(6);
            for (int j = 0; j < 6; j++) {
                int ny = py - j;
                if (ny <= 31) break;
                if (a.get(px, ny - 1, pz) == Blocks.NETHER_MYCELIUM && a.air(px, ny, pz)) return new int[]{px, ny, pz};
            }
        }
        return null;
    }

    /** Medium red mushroom: a 3-4 tall stalk with a red cap. */
    private void medRed(Area a, Random r, int x, int y, int z) {
        int[] p = myceliumSpot(a, r, x, y, z);
        if (p == null) return;
        int size = 2 + r.nextInt(3);
        for (int k = 1; k <= size; k++) if (!a.air(p[0], p[1] - 1 + k, p[2])) { size = k - 1; break; }
        if (size <= 2) return;
        for (int k = 1; k < size; k++) a.set(p[0], p[1] - 1 + k, p[2], Blocks.RED_LARGE_STALK);
        a.set(p[0], p[1] - 1 + size, p[2], Blocks.RED_LARGE_CAP);
        g.count("bn_red_large_mushroom");
    }

    /** Medium brown mushroom: stalk with a 3x3 flat cap. */
    private void medBrown(Area a, Random r, int x, int y, int z) {
        int[] p = myceliumSpot(a, r, x, y, z);
        if (p == null) return;
        int size = 2 + r.nextInt(3);
        for (int k = 1; k <= size; k++) if (!a.air(p[0], p[1] + k, p[2])) { size = k - 1; break; }
        if (size <= 2) return;
        for (int dx = -1; dx < 2; dx++) for (int dz = -1; dz < 2; dz++) if (!a.air(p[0] + dx, p[1] + size, p[2] + dz)) return;
        for (int k = 0; k < size; k++) a.set(p[0], p[1] + k, p[2], Blocks.BROWN_LARGE_ID, 10);
        int ty = p[1] + size;
        int[][] cap = {{-1, -1, 1}, {0, -1, 2}, {1, -1, 3}, {-1, 0, 4}, {0, 0, 5}, {1, 0, 6}, {-1, 1, 7}, {0, 1, 8}, {1, 1, 9}};
        for (int[] c : cap) a.set(p[0] + c[0], ty, p[2] + c[1], Blocks.BROWN_LARGE_ID, c[2]);
        g.count("bn_brown_large_mushroom");
    }

    /** Lucis: glowing wall shelf (3x3 or 2x2), glowstone heart with brown-cap rim. */
    private void lucis(Area a, Random r, int x, int y, int z) {
        if (r.nextInt(3) == 0) {
            if (a.air(x, y, z)) a.set(x, y, z, Blocks.LUCIS_CENTER);
            for (int dx = -1; dx < 2; dx++) for (int dz = -1; dz < 2; dz++)
                if ((dx != 0 || dz != 0) && a.air(x + dx, y, z + dz)) a.set(x + dx, y, z + dz, Blocks.LUCIS_RIM);
        } else {
            int sx = x, sz = z;
            if (a.solid(x, y, z - 1)) sz = z - 1; else if (a.solid(x + 1, y, z)) sx = x + 1;
            boolean first = true;
            int[][] d = {{0, 0}, {-1, 0}, {0, 1}, {-1, 1}};
            for (int[] o : d) {
                int px = sx + o[0], pz = sz + o[1];
                if (!a.air(px, y, pz)) continue;
                a.set(px, y, pz, first ? Blocks.LUCIS_CENTER : Blocks.LUCIS_RIM);
                first = false;
            }
        }
        g.count("bn_lucis");
    }

    /** Eye vine hanging from the ceiling with an eyeball at its end (eye seed growth rule). */
    private void eye(Area a, Random r, int x, int y, int z) {
        if (!a.air(x, y, z)) return;
        int h = 5 + r.nextInt(19);
        for (int i = 1; i < h; i++) {
            if (!a.air(x, y - i, z)) {
                int h2 = i;
                if (h2 < 5) return;
                h = (h2 >> 2) + r.nextInt(Math.max(1, h2 >> 2));
                h = Math.max(5, h) - 1;
                break;
            }
        }
        if (y - h <= 32) return;
        for (int i = 0; i < h; i++) a.set(x, y - i, z, Blocks.EYE_VINE);
        a.set(x, y - h, z, r.nextBoolean() ? Blocks.EYEBALL : Blocks.EYEBALL_SMALL);
        g.count("bn_eye_vine");
    }

    /** Wart tree (jar): brick-mottled wart trunk with a crown missing its diagonals. */
    private void wartTree(Area a, Random r, int x, int y, int z) {
        int height = 5 + r.nextInt(5);
        if (!(a.air(x - 4, y + 5, z) && a.air(x + 4, y + 5, z) && a.air(x, y + 5, z - 4) && a.air(x, y + 5, z + 4) && a.air(x, y + height, z))) return;
        a.set(x, y, z, Blocks.NETHER_WART_BLOCK, 0);
        int h2 = height >>> 1, h3 = height >>> 2, width = (height >>> 2) + 1, off = width >>> 1;
        for (int xx = 0; xx < width; xx++) for (int zz = 0; zz < width; zz++) {
            int px = x + xx - off, pz = z + zz - off;
            int rh = r.nextInt(Math.max(1, h2)), rh2 = r.nextInt(Math.max(1, h3));
            for (int yy = 0; yy < height; yy++) {
                int py = y + yy + 1;
                if (!Blocks.replaceable(a.id(px, py, pz))) continue;
                if (yy < rh2 && r.nextBoolean()) a.set(px, py, pz, Blocks.NETHER_BRICK, 0);
                else if (yy < rh && r.nextBoolean()) a.set(px, py, pz, Blocks.RED_NETHER_BRICK, 0);
                else a.set(px, py, pz, Blocks.NETHER_WART_BLOCK, 0);
            }
        }
        int hw = width + 2, off2 = off + 1, cy = y + height - width;
        for (int xx = 0; xx < hw; xx++) for (int zz = 0; zz < hw; zz++) {
            if (xx == zz || xx == hw - zz - 1) continue;
            for (int yy = 0; yy < width; yy++) {
                int px = x + xx - off2, py = cy + yy, pz = z + zz - off2;
                if (Blocks.replaceable(a.id(px, py, pz))) a.set(px, py, pz, Blocks.NETHER_WART_BLOCK, 0);
            }
        }
        g.count("bn_wart_tree");
    }

    /** Wart cap on low fortress walls: red-mushroom flesh wrapped in nether wart. */
    private void wartCap(Area a, Random r, int x, int y, int z) {
        int radius = 3 + r.nextInt(3), r2 = radius * radius;
        java.util.List<int[]> inside = new java.util.ArrayList<>();
        for (int yy = 0; yy <= radius >> 1; yy++) for (int xx = -radius; xx <= radius; xx++) for (int zz = -radius; zz <= radius; zz++) {
            if (xx * xx + yy * yy * 6 + zz * zz > r2) continue;
            int v = a.get(x + xx, y + yy, z + zz);
            if (v == 0 || v == (Blocks.NETHER_WART_BLOCK << 4)) inside.add(new int[]{x + xx, y + yy, z + zz});
        }
        for (int[] p : inside) {
            a.set(p[0], p[1], p[2], Blocks.HUGE_RED, 0);
            if (a.air(p[0], p[1] + 1, p[2])) a.set(p[0], p[1] + 1, p[2], Blocks.NETHER_WART_BLOCK, 0);
            if (a.air(p[0], p[1], p[2] - 1)) a.set(p[0], p[1], p[2] - 1, Blocks.NETHER_WART_BLOCK, 0);
            if (a.air(p[0], p[1], p[2] + 1)) a.set(p[0], p[1], p[2] + 1, Blocks.NETHER_WART_BLOCK, 0);
            if (a.air(p[0] + 1, p[1], p[2])) a.set(p[0] + 1, p[1], p[2], Blocks.NETHER_WART_BLOCK, 0);
            if (a.air(p[0] - 1, p[1], p[2])) a.set(p[0] - 1, p[1], p[2], Blocks.NETHER_WART_BLOCK, 0);
        }
        if (!inside.isEmpty()) g.count("bn_wart_cap");
    }

    private void bone(Area a, Random r, int x, int y, int z, Gen.Post post) {
        Template t = g.t(BONES[r.nextInt(BONES.length)]);
        int rot = r.nextInt(4);
        int ox = x - (t.width(rot) >> 1), oz = z - (t.depth(rot) >> 1);
        t.place(a, ox, y, oz, rot, null);
        g.count("bn_bone");
    }

    private void cincinnasite(Area a, Random r, int x, int y, int z) {
        int n = 6 + r.nextInt(11), placed = 0;
        for (int i = 0; i < n; i++) {
            int px = x + r.nextInt(3), py = y + r.nextInt(3), pz = z + r.nextInt(3);
            if (a.get(px, py, pz) == (Blocks.NETHERRACK << 4)) { a.set(px, py, pz, Blocks.CINCINNASITE_ORE); placed++; }
        }
        if (placed > 0) g.count("cincinnasite_cluster");
    }
}
