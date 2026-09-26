package chat.jaspr.nether;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.World;

/**
 * One population pass for a Nether chunk: NetherEx terrain swaps, BetterNether cities / smoothing / structures /
 * flora / ores inside Hell regions, NetherEx biome traits and structures, then a single batched flush and the tile
 * work (loot, spawners, skulls, village residents). Deterministic from the world seed and chunk position.
 */
final class Gen {
    static final String[] TEMPLATE_NAMES = {
        "bn_altar_01", "bn_altar_02", "bn_altar_03", "bn_altar_04", "bn_altar_05", "bn_altar_06", "bn_bone_01", "bn_bone_02",
        "bn_bone_03", "bn_garden_01", "bn_garden_02", "bn_pillar_01", "bn_portal_01", "bn_portal_02", "bn_respawn_point_01",
        "bn_respawn_point_02", "bn_room_01", "bn_city_building_01", "bn_city_building_02", "bn_city_building_03",
        "bn_city_building_04", "bn_city_building_05", "bn_city_building_06", "bn_city_building_07", "bn_city_building_08",
        "bn_city_building_09", "bn_city_building_10", "bn_city_center_01", "bn_city_center_02", "bn_city_enchanter_01",
        "bn_city_hall", "bn_city_library_01", "bn_city_tower_01", "bn_city_tower_02", "bn_city_road_end_01",
        "bn_city_road_end_02", "nex_ghast_queen_shrine", "nex_tiny_pigtificate_village", "nex_soul_sandstone_arch_01",
        "nex_spoul_shroom_01", "nex_spoul_shroom_02", "nex_spoul_shroom_03", "nex_spoul_shroom_04", "nex_spoul_shroom_05",
        "nex_spoul_shroom_06", "nex_spoul_shroom_07", "nex_spoul_shroom_08", "nex_spoul_shroom_09", "nex_spoul_shroom_10",
        "nex_spoul_shroom_11", "nex_spoul_shroom_12"
    };

    final NetherPlugin plugin;
    final World world;
    final long seed;
    final Biomes biomes;
    final Registry registry;
    final Map<String, Template> templates = new HashMap<>();
    final BnGen bn;
    final Cities cities;
    final Map<String, Integer> placed = new java.util.TreeMap<>();
    private final Map<Long, Boolean> swapped = new LinkedHashMap<Long, Boolean>(4096, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> e) { return size() > 8192; }
    };

    Gen(NetherPlugin plugin, World world, Registry registry) throws java.io.IOException {
        this.plugin = plugin;
        this.world = world;
        this.seed = world.getSeed();
        this.registry = registry;
        this.biomes = new Biomes(seed);
        for (String n : TEMPLATE_NAMES) {
            try (InputStream in = plugin.getResource("structures/" + n + ".jnt")) {
                if (in == null) throw new java.io.IOException("missing template " + n);
                templates.put(n, Template.read(n, in));
            }
        }
        this.bn = new BnGen(this);
        this.cities = new Cities(this);
    }

    Template t(String name) { return templates.get(name); }

    void count(String what) { placed.merge(what, 1, Integer::sum); }

    Random chunkRandom(int cx, int cz, long salt) {
        Random r = new Random(seed ^ salt);
        long a = r.nextLong() | 1L, b = r.nextLong() | 1L;
        r.setSeed((cx * a + cz * b) ^ seed ^ salt);
        return r;
    }

    // ------------------------------------------------------------------------------------------------------------
    /** Populates chunk (cx, cz). Returns the number of blocks written. */
    /** Accumulated nanoseconds per phase: read, terrain, city, betternether, netherex, flush+light, tiles. */
    final long[] phase = new long[7];
    static final String[] PHASES = {"read", "terrain", "city", "bn", "nex", "flush", "tiles"};

    int populate(int cx, int cz) {
        long t = System.nanoTime(), t2;
        Area a = new Area(world, cx, cz);
        t2 = System.nanoTime(); phase[0] += t2 - t; t = t2;
        for (int i = 0; i < 4; i++) swapTerrain(a, cx + (i & 1), cz + (i >> 1));
        t2 = System.nanoTime(); phase[1] += t2 - t; t = t2;
        Post post = new Post();
        Random rand = chunkRandom(cx, cz, 0x6A6E6574L);
        cities.populate(a, post);
        t2 = System.nanoTime(); phase[2] += t2 - t; t = t2;
        bn.populate(a, rand, post);
        t2 = System.nanoTime(); phase[3] += t2 - t; t = t2;
        Biomes.Nex centre = biomes.nex(a.ox + 16, a.oz + 16);
        nexTraits(a, chunkRandom(cx, cz, 0x4E4558L), centre, post);
        t2 = System.nanoTime(); phase[4] += t2 - t; t = t2;
        a.flush();
        t2 = System.nanoTime(); phase[5] += t2 - t; t = t2;
        post.apply(this, a);
        phase[6] += System.nanoTime() - t;
        return a.writes;
    }

    String phases(long chunks) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < phase.length; i++) b.append(i == 0 ? "" : " ").append(PHASES[i]).append('=').append(NetherPlugin.fmt(chunks == 0 ? 0 : phase[i] / 1e6 / chunks));
        return b.toString();
    }

    // ---- NetherEx terrain (BiomeDataNetherEx.generateTerrain) ---------------------------------------------------
    private void swapTerrain(Area a, int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        if (swapped.containsKey(key)) return;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        swapped.put(key, Boolean.TRUE);
        int bx = chunkX << 4, bz = chunkZ << 4;
        for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) {
            Biomes.Nex n = biomes.nex(x, z);
            if (n == Biomes.Nex.HELL) continue;
            int surface, sub, liquid;
            switch (n) {
                case RUTHLESS_SANDS: surface = Blocks.SOUL_SAND << 4; sub = Blocks.GLOOMY_NETHERRACK; liquid = Blocks.LAVA << 4; break;
                case TORRID_WASTELAND: surface = sub = Blocks.FIERY_NETHERRACK; liquid = Blocks.LAVA << 4; break;
                case FUNGI_FOREST: surface = Blocks.HYPHAE; sub = Blocks.LIVELY_NETHERRACK; liquid = Blocks.LAVA << 4; break;
                default: surface = Blocks.FROSTBURN_ICE; sub = Blocks.ICY_NETHERRACK; liquid = Blocks.MAGMA << 4; break;
            }
            boolean open = false;
            for (int y = Area.H - 1; y >= 0; y--) {
                int v = a.get(x, y, z);
                if (v == 0) open = true;
                else if (v == (Blocks.NETHERRACK << 4)) { a.set(x, y, z, open ? (y < 31 ? liquid : surface) : sub); open = false; }
                else if (v == (Blocks.LAVA << 4)) { if (liquid != v) a.set(x, y, z, liquid); open = true; }
            }
        }
    }

    static int variant(Biomes.Nex n) {
        switch (n) {
            case RUTHLESS_SANDS: return Blocks.GLOOMY_NETHERRACK;
            case TORRID_WASTELAND: return Blocks.FIERY_NETHERRACK;
            case FUNGI_FOREST: return Blocks.LIVELY_NETHERRACK;
            case ARCTIC_ABYSS: return Blocks.ICY_NETHERRACK;
            default: return Blocks.NETHERRACK << 4;
        }
    }

    // ---- NetherEx biome traits ---------------------------------------------------------------------------------
    private void nexTraits(Area a, Random r, Biomes.Nex b, Post post) {
        int v = variant(b);
        switch (b) {
            case RUTHLESS_SANDS:
                fluids(a, r, 8, 4, 124, v, true);
                clusters(a, r, r.nextInt(10) + 1, 4, 124, v);
                clusters(a, r, 10, 1, 128, v);
                fluids(a, r, 16, 10, 118, v, true);
                for (int i = 0; i < 16; i++) thornstalk(a, r, a.ox + 8 + r.nextInt(16), 32 + r.nextInt(76), a.oz + 8 + r.nextInt(16));
                ores(a, r, 16, 10, 108, Blocks.NEX_QUARTZ_ORE, v, 14);
                if (r.nextInt(100) < 3) extra(a, r, "nex_soul_sandstone_arch_01", Blocks.SOUL_SAND, post, "arch");
                break;
            case TORRID_WASTELAND:
                for (int i = 0; i < 8; i++) pool(a, r, 10, 108, Blocks.LAVA << 4, v);
                fluids(a, r, 16, 4, 124, v, false);
                fire(a, r, r.nextInt(20) + 1, 4, 124, v, true);
                clusters(a, r, r.nextInt(10) + 1, 4, 124, v);
                clusters(a, r, 10, 1, 128, v);
                fluids(a, r, 32, 10, 118, v, true);
                ores(a, r, 16, 10, 108, Blocks.NEX_QUARTZ_ORE, v, 14);
                ores(a, r, 16, 10, 108, Blocks.BASALT, v, 24);
                ores(a, r, 8, 10, 108, Blocks.MAGMA << 4, v, 32);
                break;
            case FUNGI_FOREST:
                clusters(a, r, r.nextInt(10) + 1, 4, 124, v);
                clusters(a, r, 10, 1, 128, v);
                for (int i = 0; i < 256; i++) bigMushroom(a, r, a.ox + 8 + r.nextInt(16), 32 + r.nextInt(76), a.oz + 8 + r.nextInt(16), true);
                for (int i = 0; i < 256; i++) bigMushroom(a, r, a.ox + 8 + r.nextInt(16), 32 + r.nextInt(76), a.oz + 8 + r.nextInt(16), false);
                for (int i = 0; i < 32; i++) enoki(a, r, a.ox + 8 + r.nextInt(16), 48 + r.nextInt(70), a.oz + 8 + r.nextInt(16));
                ores(a, r, 16, 10, 108, Blocks.NEX_QUARTZ_ORE, v, 14);
                if (r.nextDouble() < 0.0125) shrine(a, r, post);
                if (r.nextInt(100) < 15) extra(a, r, "nex_spoul_shroom_" + String.format("%02d", 1 + r.nextInt(12)), Blocks.HYPHAE >> 4, post, null);
                break;
            case ARCTIC_ABYSS:
                for (int i = 0; i < 2; i++) if (r.nextDouble() < 0.125) pool(a, r, 36, 108, Blocks.ICHOR, Blocks.FROSTBURN_ICE);
                blueFire(a, r, r.nextInt(5) + 1, post);
                clusters(a, r, r.nextInt(10) + 1, 4, 124, v);
                clusters(a, r, 10, 1, 128, v);
                ores(a, r, 16, 10, 108, Blocks.NEX_QUARTZ_ORE, v, 14);
                ores(a, r, 16, 10, 108, Blocks.RIME_ORE, v, 7);
                break;
            default: // Hell: vanilla features already exist; NetherEx adds amethyst ore and Pigtificate villages.
                ores(a, r, 8, 10, 108, Blocks.AMETHYST_ORE, Blocks.NETHERRACK << 4, 7);
                if (r.nextDouble() < 0.25) village(a, r, post);
        }
    }

    /** WorldGenMinable with a target block. */
    void ores(Area a, Random r, int attempts, int minY, int maxY, int ore, int target, int size) {
        for (int n = 0; n < attempts; n++) {
            int px = a.ox + r.nextInt(16), py = minY + r.nextInt(Math.max(1, maxY - minY)), pz = a.oz + r.nextInt(16);
            float f = r.nextFloat() * (float) Math.PI;
            double d0 = px + 8 + Math.sin(f) * size / 8.0, d1 = px + 8 - Math.sin(f) * size / 8.0;
            double d2 = pz + 8 + Math.cos(f) * size / 8.0, d3 = pz + 8 - Math.cos(f) * size / 8.0;
            double d4 = py + r.nextInt(3) - 2, d5 = py + r.nextInt(3) - 2;
            for (int i = 0; i < size; i++) {
                float f1 = (float) i / size;
                double d6 = d0 + (d1 - d0) * f1, d7 = d4 + (d5 - d4) * f1, d8 = d2 + (d3 - d2) * f1;
                double d9 = r.nextDouble() * size / 16.0;
                double d10 = (Math.sin(Math.PI * f1) + 1.0) * d9 + 1.0;
                int x0 = (int) Math.floor(d6 - d10 / 2), y0 = (int) Math.floor(d7 - d10 / 2), z0 = (int) Math.floor(d8 - d10 / 2);
                int x1 = (int) Math.floor(d6 + d10 / 2), y1 = (int) Math.floor(d7 + d10 / 2), z1 = (int) Math.floor(d8 + d10 / 2);
                for (int x = x0; x <= x1; x++) {
                    double dx = (x + 0.5 - d6) / (d10 / 2);
                    if (dx * dx >= 1) continue;
                    for (int y = y0; y <= y1; y++) {
                        double dy = (y + 0.5 - d7) / (d10 / 2);
                        if (dx * dx + dy * dy >= 1) continue;
                        for (int z = z0; z <= z1; z++) {
                            double dz = (z + 0.5 - d8) / (d10 / 2);
                            if (dx * dx + dy * dy + dz * dz < 1 && a.get(x, y, z) == target) a.set(x, y, z, ore);
                        }
                    }
                }
            }
        }
    }

    /** WorldGenGlowStone1 hanging from the given ceiling block. */
    void clusters(Area a, Random r, int attempts, int minY, int maxY, int ceiling) {
        for (int n = 0; n < attempts; n++) {
            int x = a.ox + 8 + r.nextInt(16), y = minY + r.nextInt(Math.max(1, maxY - minY)), z = a.oz + 8 + r.nextInt(16);
            if (!a.air(x, y, z) || a.get(x, y + 1, z) != ceiling) continue;
            a.set(x, y, z, Blocks.GLOWSTONE, 0);
            for (int i = 0; i < 1500; i++) {
                int bx = x + r.nextInt(8) - r.nextInt(8), by = y - r.nextInt(12), bz = z + r.nextInt(8) - r.nextInt(8);
                if (!a.air(bx, by, bz)) continue;
                int c = 0;
                if (a.id(bx + 1, by, bz) == Blocks.GLOWSTONE) c++;
                if (a.id(bx - 1, by, bz) == Blocks.GLOWSTONE) c++;
                if (a.id(bx, by + 1, bz) == Blocks.GLOWSTONE) c++;
                if (a.id(bx, by - 1, bz) == Blocks.GLOWSTONE) c++;
                if (a.id(bx, by, bz + 1) == Blocks.GLOWSTONE) c++;
                if (a.id(bx, by, bz - 1) == Blocks.GLOWSTONE) c++;
                if (c == 1) a.set(bx, by, bz, Blocks.GLOWSTONE, 0);
            }
            count("glowstone_cluster");
        }
    }

    /** WorldGenHellLava against a target block; exposed=false keeps springs sealed inside rock. */
    void fluids(Area a, Random r, int attempts, int minY, int maxY, int target, boolean exposed) {
        for (int n = 0; n < attempts; n++) {
            int x = a.ox + 8 + r.nextInt(16), y = minY + r.nextInt(Math.max(1, maxY - minY)), z = a.oz + 8 + r.nextInt(16);
            if (a.get(x, y + 1, z) != target) continue;
            int here = a.get(x, y, z);
            if (here != 0 && here != target) continue;
            int rock = 0, air = 0;
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
            for (int[] o : d) {
                int v = a.get(x + o[0], y + o[1], z + o[2]);
                if (v == target) rock++; else if (v == 0) air++;
            }
            if ((exposed && rock == 4 && air == 1) || rock == 5) {
                a.set(x, y, z, Blocks.LAVA_FLOW, 0);
                count("lava_spring");
            }
        }
    }

    /** WorldGenFire on the target ground; in the Torrid Wasteland the fire keeps a netherrack hearth so it burns forever. */
    void fire(Area a, Random r, int attempts, int minY, int maxY, int ground, boolean hearth) {
        for (int n = 0; n < attempts; n++) {
            int x = a.ox + 8 + r.nextInt(16), y = minY + r.nextInt(Math.max(1, maxY - minY)), z = a.oz + 8 + r.nextInt(16);
            for (int i = 0; i < 64; i++) {
                int bx = x + r.nextInt(8) - r.nextInt(8), by = y + r.nextInt(4) - r.nextInt(4), bz = z + r.nextInt(8) - r.nextInt(8);
                if (a.air(bx, by, bz) && a.get(bx, by - 1, bz) == ground) {
                    if (hearth) a.set(bx, by - 1, bz, Blocks.NETHERRACK, 0);
                    a.set(bx, by, bz, Blocks.FIRE, 0);
                }
            }
        }
    }

    /** NetherEx blue fire scatter on frostburn ice; the spots are registered so contact applies Fire Burning. */
    private void blueFire(Area a, Random r, int attempts, Post post) {
        for (int n = 0; n < attempts; n++) {
            int x = a.ox + 8 + r.nextInt(16), y = 4 + r.nextInt(120), z = a.oz + 8 + r.nextInt(16);
            for (int i = 0; i < 64; i++) {
                int bx = x + r.nextInt(8) - r.nextInt(8), by = y + r.nextInt(4) - r.nextInt(4), bz = z + r.nextInt(8) - r.nextInt(8);
                if (a.air(bx, by, bz) && a.get(bx, by - 1, bz) == Blocks.FROSTBURN_ICE) {
                    a.set(bx, by, bz, Blocks.BLUE_FIRE);
                    count("blue_fire");
                }
            }
        }
    }

    /** WorldGenLakes-style pool: liquid lower half, air upper half, walls/floor of the surround block. */
    void pool(Area a, Random r, int minY, int maxY, int liquid, int surround) {
        int x = a.ox + 8 + r.nextInt(16) - 8, y = minY + r.nextInt(Math.max(1, maxY - minY)), z = a.oz + 8 + r.nextInt(16) - 8;
        while (y > 5 && a.air(x + 8, y, z + 8)) y--;
        if (y <= minY - 4) return;
        y -= 4;
        boolean[] cell = new boolean[2048];
        int blobs = r.nextInt(4) + 4;
        for (int j = 0; j < blobs; j++) {
            double d0 = r.nextDouble() * 6 + 3, d1 = r.nextDouble() * 4 + 2, d2 = r.nextDouble() * 6 + 3;
            double d3 = r.nextDouble() * (16 - d0 - 2) + 1 + d0 / 2, d4 = r.nextDouble() * (8 - d1 - 4) + 2 + d1 / 2, d5 = r.nextDouble() * (16 - d2 - 2) + 1 + d2 / 2;
            for (int l = 1; l < 15; l++) for (int i1 = 1; i1 < 15; i1++) for (int j1 = 1; j1 < 7; j1++) {
                double d6 = (l - d3) / (d0 / 2), d7 = (j1 - d4) / (d1 / 2), d8 = (i1 - d5) / (d2 / 2);
                if (d6 * d6 + d7 * d7 + d8 * d8 < 1) cell[(l * 16 + i1) * 8 + j1] = true;
            }
        }
        for (int l = 0; l < 16; l++) for (int i1 = 0; i1 < 16; i1++) for (int j1 = 0; j1 < 8; j1++) {
            boolean edge = !cell[(l * 16 + i1) * 8 + j1] && (l < 15 && cell[((l + 1) * 16 + i1) * 8 + j1] || l > 0 && cell[((l - 1) * 16 + i1) * 8 + j1]
                || i1 < 15 && cell[(l * 16 + i1 + 1) * 8 + j1] || i1 > 0 && cell[(l * 16 + (i1 - 1)) * 8 + j1]
                || j1 < 7 && cell[(l * 16 + i1) * 8 + j1 + 1] || j1 > 0 && cell[(l * 16 + i1) * 8 + (j1 - 1)]);
            if (!edge) continue;
            int id = a.id(x + l, y + j1, z + i1);
            boolean liquidHere = id == 8 || id == 9 || id == 10 || id == 11;
            if (j1 >= 4 && liquidHere) return;
            if (j1 < 4 && !Blocks.isFullSolid(id) && a.get(x + l, y + j1, z + i1) != liquid) return;
            if (id == Blocks.BEDROCK && a.inside(x + l, y + j1, z + i1) == false) return;
        }
        for (int l = 0; l < 16; l++) for (int i1 = 0; i1 < 16; i1++) for (int j1 = 0; j1 < 8; j1++) {
            if (cell[(l * 16 + i1) * 8 + j1]) a.set(x + l, y + j1, z + i1, j1 >= 4 ? 0 : liquid);
        }
        for (int l = 0; l < 16; l++) for (int i1 = 0; i1 < 16; i1++) for (int j1 = 0; j1 < 8; j1++) {
            if (cell[(l * 16 + i1) * 8 + j1]) continue;
            boolean edge = (l < 15 && cell[((l + 1) * 16 + i1) * 8 + j1] || l > 0 && cell[((l - 1) * 16 + i1) * 8 + j1]
                || i1 < 15 && cell[(l * 16 + i1 + 1) * 8 + j1] || i1 > 0 && cell[(l * 16 + (i1 - 1)) * 8 + j1]
                || j1 > 0 && cell[(l * 16 + i1) * 8 + (j1 - 1)]);
            if (edge && (j1 < 4 || r.nextInt(2) != 0) && Blocks.isFullSolid(a.id(x + l, y + j1, z + i1)))
                a.set(x + l, y + j1, z + i1, surround);
        }
        count(liquid == Blocks.ICHOR ? "ichor_pool" : "lava_pool");
    }

    /** NetherEx thornstalk trait: 64 tries onto soul sand, heights 1-3. */
    private void thornstalk(Area a, Random r, int x, int y, int z) {
        for (int i = 0; i < 64; i++) {
            int bx = x + r.nextInt(8) - r.nextInt(8), by = y + r.nextInt(4) - r.nextInt(4), bz = z + r.nextInt(8) - r.nextInt(8);
            if (!a.air(bx, by, bz) || a.id(bx, by - 1, bz) != Blocks.SOUL_SAND) continue;
            int h = r.nextInt(3) + 1;
            for (int k = 0; k < h && a.air(bx, by + k, bz); k++) a.set(bx, by + k, bz, Blocks.THORNSTALK);
            count("thornstalk");
        }
    }

    /** Vanilla WorldGenBigMushroom (brown = flat, red = bulb) on hyphae: NetherEx elder mushrooms. */
    boolean bigMushroom(Area a, Random r, int x, int y, int z, boolean brown) {
        if (!a.air(x, y, z) || a.get(x, y - 1, z) != Blocks.HYPHAE) return false;
        int block = brown ? Blocks.ELDER_CAP_BROWN_ID : Blocks.ELDER_CAP_RED_ID;
        int h = r.nextInt(3) + 4;
        if (r.nextInt(12) == 0) h *= 2;
        if (y + h + 1 >= Area.H - 1) return false;
        for (int j = y; j <= y + 1 + h; j++) {
            int k = j <= y + 3 ? 0 : 3;
            for (int l = x - k; l <= x + k; l++) for (int m = z - k; m <= z + k; m++) {
                int id = a.id(l, j, m);
                if (id != 0 && id != Blocks.LEAVES && id != Blocks.LEAVES2) return false;
            }
        }
        int top = y + h, k2 = brown ? top : top - 3;
        for (int l2 = k2; l2 <= top; l2++) {
            int j3 = brown ? 3 : (l2 < top ? 2 : 1);
            int x0 = x - j3, x1 = x + j3, z0 = z - j3, z1 = z + j3;
            for (int l1 = x0; l1 <= x1; l1++) for (int i2 = z0; i2 <= z1; i2++) {
                int t = 5;
                if (l1 == x0) t--; else if (l1 == x1) t++;
                if (i2 == z0) t -= 3; else if (i2 == z1) t += 3;
                if (brown || l2 < top) {
                    if ((l1 == x0 || l1 == x1) && (i2 == z0 || i2 == z1)) continue;
                    if (l1 == x - (j3 - 1) && i2 == z0) t = 1;
                    if (l1 == x0 && i2 == z - (j3 - 1)) t = 1;
                    if (l1 == x + (j3 - 1) && i2 == z0) t = 3;
                    if (l1 == x1 && i2 == z - (j3 - 1)) t = 3;
                    if (l1 == x - (j3 - 1) && i2 == z1) t = 7;
                    if (l1 == x0 && i2 == z + (j3 - 1)) t = 7;
                    if (l1 == x + (j3 - 1) && i2 == z1) t = 9;
                    if (l1 == x1 && i2 == z + (j3 - 1)) t = 9;
                }
                if (t == 5 && l2 < top) t = 0;
                if (t != 0 || y >= top - 1) {
                    if (!Blocks.isFullSolid(a.id(l1, l2, i2))) a.set(l1, l2, i2, block, t);
                }
            }
        }
        for (int i = 0; i < h; i++) if (!Blocks.isFullSolid(a.id(x, y + i, z))) a.set(x, y + i, z, Blocks.ELDER_STEM);
        count(brown ? "elder_mushroom_brown" : "elder_mushroom_red");
        return true;
    }

    /** NetherEx enoki: an upside-down chorus of stems hanging from lively netherrack, ending in caps. */
    private void enoki(Area a, Random r, int x, int y, int z) {
        int top = y;
        while (top < 120 && a.air(x, top + 1, z)) top++;
        if (a.get(x, top + 1, z) != Blocks.LIVELY_NETHERRACK) return;
        if (!a.air(x, top, z) || !a.air(x, top - 1, z) || r.nextInt(8) != 7) return;
        a.set(x, top, z, Blocks.ENOKI_STEM);
        enokiGrow(a, r, x, top, z, x, z, 0);
        count("enoki");
    }

    private void enokiGrow(Area a, Random r, int x, int y, int z, int rootX, int rootZ, int depth) {
        int h = r.nextInt(4) + 1;
        if (depth == 0) h++;
        for (int j = 0; j < h; j++) {
            int by = y - j - 1;
            if (!emptyAround(a, x, by, z, -1)) return;
            a.set(x, by, z, Blocks.ENOKI_STEM);
        }
        boolean grew = false;
        int end = y - h;
        if (depth < 4) {
            int chances = r.nextInt(4) + (depth == 0 ? 1 : 0);
            for (int i = 0; i < chances; i++) {
                int f = r.nextInt(4);
                int bx = x + (f == 0 ? 1 : f == 1 ? -1 : 0), bz = z + (f == 2 ? 1 : f == 3 ? -1 : 0);
                if (Math.abs(bx - rootX) < 8 && Math.abs(bz - rootZ) < 8 && a.air(bx, end, bz) && a.air(bx, end + 1, bz)
                    && emptyAround(a, bx, end, bz, f ^ 1)) {
                    grew = true;
                    a.set(bx, end, bz, Blocks.ENOKI_STEM);
                    enokiGrow(a, r, bx, end, bz, rootX, rootZ, depth + 1);
                }
            }
        }
        if (!grew) a.set(x, end, z, Blocks.ENOKI_CAP);
    }

    private static boolean emptyAround(Area a, int x, int y, int z, int except) {
        if (except != 0 && !a.air(x + 1, y, z)) return false;
        if (except != 1 && !a.air(x - 1, y, z)) return false;
        if (except != 2 && !a.air(x, y, z + 1)) return false;
        if (except != 3 && !a.air(x, y, z - 1)) return false;
        return true;
    }

    // ---- NetherEx structures -------------------------------------------------------------------------------------
    /** Ghast Queen shrine: floating (type AIR, clearance 1.0) in the Fungi Forest. */
    private void shrine(Area a, Random r, Post post) {
        Template t = t("nex_ghast_queen_shrine");
        int rot = r.nextInt(4);
        int cx = a.ox + 16 + r.nextInt(4) - 2, cz = a.oz + 16 + r.nextInt(4) - 2;
        int ox = cx - (t.width(rot) >> 1), oz = cz - (t.depth(rot) >> 1);
        for (int tries = 0; tries < 8; tries++) {
            int oy = 32 + r.nextInt(118 - 32 - t.sy);
            if (t.airFraction(a, ox, oy, oz, rot, 1) < 1.0) continue;
            Template.Placed p = new Template.Placed();
            t.place(a, ox, oy, oz, rot, p);
            post.add(p, "shrine");
            registry.add("shrine", "ghast_queen_shrine", ox, oy, oz, ox + t.width(rot) - 1, oy + t.sy - 1, oz + t.depth(rot) - 1);
            count("ghast_queen_shrine");
            return;
        }
    }

    /** Tiny Pigtificate village on the ground of a Hell region (NetherEx p = 0.25, with an 8-chunk spacing added). */
    private void village(Area a, Random r, Post post) {
        int cx = a.ox + 16, cz = a.oz + 16;
        if (!registry.near(cx, cz, 128, "village").isEmpty()) return;
        Template t = t("nex_tiny_pigtificate_village");
        int rot = r.nextInt(4);
        int w = t.width(rot), d = t.depth(rot);
        int ox = cx - (w >> 1), oz = cz - (d >> 1);
        for (int y = 108; y > 32; y--) {
            if (!(a.air(cx, y, cz) && a.air(cx, y + 1, cz) && Blocks.isFullSolid(a.id(cx, y - 1, cz)))) continue;
            int ground = y - 1;
            int ok = 0, samples = 0;
            for (int sx = 0; sx < w; sx += 5) for (int sz = 0; sz < d; sz += 5) {
                samples++;
                int gx = ox + sx, gz = oz + sz, gy = ground + 3;
                while (gy > ground - 4 && !Blocks.isFullSolid(a.id(gx, gy, gz))) gy--;
                if (gy > ground - 4 && gy <= ground + 2 && a.air(gx, ground + 3, gz)) ok++;
            }
            if (ok * 10 < samples * 7) return;
            Template.Placed p = new Template.Placed();
            t.place(a, ox, ground, oz, rot, p);
            // fill foundations under the village's floor so it never floats
            for (int x = ox; x < ox + w; x++) for (int z = oz; z < oz + d; z++) {
                if (!Blocks.isFullSolid(a.id(x, ground, z))) continue;
                for (int yy = ground - 1; yy > ground - 8 && !Blocks.isFullSolid(a.id(x, yy, z)); yy--) a.set(x, yy, z, Blocks.NETHERRACK, 0);
            }
            post.add(p, "village");
            post.golems.add(new int[]{cx, ground + 1, cz});
            registry.add("village", "pigtificate_village", ox, ground, oz, ox + w - 1, ground + t.sy - 1, oz + d - 1);
            count("pigtificate_village");
            return;
        }
    }

    /** Unused-in-2.2.5 NetherEx templates (spoul shrooms, soul sandstone arch) as flavour on their biome's ground. */
    private void extra(Area a, Random r, String name, int groundId, Post post, String registryType) {
        Template t = t(name);
        int rot = r.nextInt(4);
        int cx = a.ox + 12 + r.nextInt(8), cz = a.oz + 12 + r.nextInt(8);
        for (int y = 110; y > 32; y--) {
            if (a.air(cx, y, cz) && a.id(cx, y - 1, cz) == groundId) {
                int ox = cx - (t.width(rot) >> 1), oz = cz - (t.depth(rot) >> 1);
                if (t.airFraction(a, ox, y, oz, rot, 2) < 0.85) return;
                t.place(a, ox, y, oz, rot, null);
                if (registryType != null)
                    registry.add(registryType, name.replace("nex_", ""), ox, y, oz, ox + t.width(rot) - 1, y + t.sy - 1, oz + t.depth(rot) - 1);
                count(name.startsWith("nex_spoul") ? "spoul_shroom" : "soul_sandstone_arch");
                return;
            }
        }
    }

    // ---- post-flush tile work ------------------------------------------------------------------------------------
    static final class Post {
        final List<Template.Placed> placed = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();
        final List<int[]> golems = new ArrayList<>();
        final List<int[]> ores = new ArrayList<>();
        void add(Template.Placed p, String kind) { placed.add(p); kinds.add(kind); }

        void apply(Gen g, Area a) {
            for (int i = 0; i < placed.size(); i++) {
                Template.Placed p = placed.get(i);
                String kind = kinds.get(i);
                for (int c = 0; c < p.chests.size(); c++) {
                    int[] v = p.chests.get(c);
                    g.plugin.structures.placeChest(g.world, v[0], v[1], v[2], v[3], p.chestTables.get(c), g.chunkRandom(v[0], v[2], v[1] * 31L + c));
                }
                for (int c = 0; c < p.spawners.size(); c++) {
                    int[] v = p.spawners.get(c);
                    g.plugin.structures.placeSpawner(g.world, v[0], v[1], v[2], p.spawnerMobs.get(c));
                }
                for (int[] v : p.skulls) g.plugin.structures.placeSkull(g.world, v[0], v[1], v[2], v[3], v[4]);
                for (int c = 0; c < p.entities.size(); c++) {
                    int[] v = p.entities.get(c);
                    g.plugin.structures.spawnResident(g.world, v[0], v[1], v[2], p.entityKinds.get(c));
                }
                for (int c = 0; c < p.points.size(); c++) {
                    int[] v = p.points.get(c);
                    String k = p.pointKinds.get(c);
                    if (k.equals("urn")) g.registry.add("urn", kind, v[0], v[1], v[2], v[0], v[1], v[2]);
                    else if (k.equals("bluefire")) g.registry.add("bluefire", kind, v[0], v[1], v[2], v[0], v[1], v[2]);
                    else if (k.equals("statue")) g.registry.add("statue", kind, v[0], v[1], v[2], v[0], v[1], v[2]);
                }
            }
            for (int[] v : golems) g.plugin.structures.spawnResident(g.world, v[0], v[1], v[2], "netherex:gold_golem");
            for (int[] v : a.written(Blocks.LAVA_FLOW)) g.plugin.structures.tickLava(g.world, v[0], v[1], v[2]);
        }
    }
}
