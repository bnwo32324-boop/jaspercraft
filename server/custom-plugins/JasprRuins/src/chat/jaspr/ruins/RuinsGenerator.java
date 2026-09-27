package chat.jaspr.ruins;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/**
 * The Ancient Ruins: HorrorBiomes' terrain heights (so the Lost Cities fit the land exactly as they do in the
 * overworld) dressed as an overgrown, mossy world of forests, jungle edges and swamps, with the original old cities
 * levelled into it and wilderness ruins stamped on top. The Lost Cities plugin builds its own cities afterwards through
 * its populator, restyled by {@link Weathering}.
 */
public final class RuinsGenerator extends ChunkGenerator {
    static final int SEA = Plans.SEA;

    final long seed;
    final Plans plans;
    final Plans.Reserved reserved;
    private final RuinsPopulator populator;
    private final BiConsumer<String, Throwable> failure;
    volatile long chunks, failures;

    RuinsGenerator(long seed, Plans.Reserved reserved, BiConsumer<String, Throwable> failure) {
        this.seed = seed;
        this.reserved = reserved;
        this.plans = new Plans(seed, reserved);
        this.failure = failure;
        this.populator = new RuinsPopulator(this);
    }

    RuinsPopulator populator() { return populator; }

    void reportFailure(String where, Throwable e) {
        failures++;
        if (failure != null) failure.accept(where, e);
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);
        fill(data, biomes, cx, cz);
        return data;
    }

    int[][] heights(int cx, int cz) {
        int[][] h = new int[16][16];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) h[x][z] = plans.surface(cx * 16 + x, cz * 16 + z);
        return h;
    }

    /** Terrain, then the ruins touching this chunk. Separate from Bukkit's ChunkData factory so tests can run it. */
    void fill(ChunkData d, BiomeGrid biomes, int cx, int cz) {
        int[][] heights = heights(cx, cz);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) column(d, biomes, x, z, cx * 16 + x, cz * 16 + z, heights[x][z]);
        try {
            stamp(new Canvas(seed, cx, cz, d, heights, null));
        } catch (RuntimeException e) {
            reportFailure("chunk=" + cx + "," + cz, e);
        }
        chunks++;
    }

    /** Draws every original ruin touching the canvas' chunk (a chunk lies in exactly one city cell and one site cell). */
    void stamp(Canvas c) {
        Plans.City city = plans.city(Math.floorDiv(c.x0, Plans.CITY_GRID), Math.floorDiv(c.z0, Plans.CITY_GRID));
        if (city != null) OldCity.draw(city, c);
        Plans.Site site = plans.site(Math.floorDiv(c.x0, Plans.SITE_GRID), Math.floorDiv(c.z0, Plans.SITE_GRID));
        if (site != null) Sites.draw(site, c);
    }

    // Biome slots the browser client styles green and snow-free (JasprBiomeStyles restyles every slot for the horror
    // overworld: FOREST, PLAINS and JUNGLE_EDGE come out dry and olive there, SWAMPLAND snowy). These keep temperature
    // 0.6 / rainfall 0.7 with rain, so grass and leaves stay lush; ROOFED_FOREST and the swamp keep their own tints.
    static final Biome MARSH = Biome.MUTATED_SWAMPLAND, WOODS = Biome.FOREST_HILLS, JUNGLE = Biome.JUNGLE, MEADOW = Biome.MUTATED_BIRCH_FOREST,
        DARKWOOD = Biome.ROOFED_FOREST, HIGHLANDS = Biome.EXTREME_HILLS_WITH_TREES, SEA_BIOME = Biome.OCEAN;

    Biome biome(int wx, int wz, int h) {
        if (h <= SEA - 6) return SEA_BIOME;
        if (h <= SEA) return MARSH;
        if (h >= 108) return HIGHLANDS;
        double n = Hash.noise(seed ^ 0xB10L, wx, wz, 160);
        if (n < 0.38) return DARKWOOD;
        if (n < 0.50) return WOODS;
        if (n < 0.62) return JUNGLE;
        if (n < 0.72) return MEADOW;
        return MARSH;
    }

    @SuppressWarnings("deprecation")
    private void column(ChunkData d, BiomeGrid biomes, int x, int z, int wx, int wz, int h) {
        Biome biome = biome(wx, wz, h);
        biomes.setBiome(x, z, biome);
        if (h - 3 > 1) d.setRegion(x, 1, z, x + 1, h - 3, z + 1, Material.STONE);
        d.setBlock(x, 0, z, Material.BEDROCK);
        for (int y = 1; y <= 3; y++) if (Hash.unit(seed ^ 0xBEDL, wx, y, wz) < 0.6 - y * 0.18) d.setBlock(x, y, z, Material.BEDROCK);
        if (h <= SEA) {
            double n = Hash.unit(seed ^ 0x5EAL, wx, h, wz);
            d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
            d.setBlock(x, h, z, n < 0.4 ? Material.SAND : n < 0.7 ? Material.GRAVEL : n < 0.85 ? Material.CLAY : Material.DIRT);
            d.setRegion(x, h + 1, z, x + 1, SEA + 1, z + 1, Material.STATIONARY_WATER);
            return;
        }
        boolean grass;
        if (h >= 108) {
            d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.STONE);
            double n = Hash.noise(seed ^ 0x40CL, wx, wz, 6);
            grass = n >= 0.6;
            if (n < 0.45) d.setBlock(x, h, z, Canvas.STONE, (byte) (Hash.unit(seed, wx, h, wz) < 0.3 ? 5 : 0));
            else if (!grass) d.setBlock(x, h, z, Canvas.MOSSY, (byte) 0);
            else d.setBlock(x, h, z, Canvas.GRASS, (byte) 0);
        } else {
            d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
            double v = Hash.unit(seed ^ 0x70BL, wx, 0, wz);
            grass = false;
            if (biome == DARKWOOD && v < 0.09) d.setBlock(x, h, z, Canvas.DIRT, (byte) 2);
            else if (v < 0.03) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
            else if (v < 0.045) d.setBlock(x, h, z, Canvas.MOSSY, (byte) 0);
            else { d.setBlock(x, h, z, Canvas.GRASS, (byte) 0); grass = true; }
        }
        if (!grass || h > 250) return;
        double boulder = Hash.noise(seed ^ 0xB01DL, wx, wz, 4.0);
        if (boulder > 0.9) {
            d.setBlock(x, h + 1, z, Canvas.MOSSY, (byte) 0);
            if (boulder > 0.94) d.setBlock(x, h + 2, z, Canvas.MOSSY, (byte) 0);
            return;
        }
        double p = Hash.unit(seed ^ 0x9A55L, wx, h, wz);
        boolean lush = biome == JUNGLE || biome == DARKWOOD;
        if (p < 0.16) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 1);
        else if (p < (lush ? 0.30 : 0.22)) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 2);
        else if (p < (lush ? 0.31 : 0.23)) { d.setBlock(x, h + 1, z, Canvas.DOUBLE_PLANT, (byte) 2); d.setBlock(x, h + 2, z, Canvas.DOUBLE_PLANT, (byte) 10); }
        else if (p < (lush ? 0.318 : 0.245)) {
            double f = Hash.unit(seed ^ 0xF10L, wx, h, wz);
            if (biome == MARSH) d.setBlock(x, h + 1, z, 38, (byte) 1);
            else if (f < 0.4) d.setBlock(x, h + 1, z, 37, (byte) 0);
            else d.setBlock(x, h + 1, z, 38, (byte) (f < 0.7 ? 0 : 8));
        } else if (biome == DARKWOOD && p < 0.33) {
            d.setBlock(x, h + 1, z, 39, (byte) 0);
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) { return Collections.singletonList(populator); }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 0.5, plans.surface(0, 0) + 1, 0.5); }

    @Override
    public boolean canSpawn(World world, int x, int z) { return plans.surface(x, z) > SEA; }
}
