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
 * Drownhollow (the ruins dimension): HorrorBiomes' terrain heights (so the Lost Cities fit the land exactly as they do in the
 * overworld) dressed as an overgrown, mossy world of forests, jungle edges and swamps, with the original old cities
 * levelled into it and wilderness ruins stamped on top. The Lost Cities plugin builds its own cities afterwards through
 * its populator, restyled by {@link Weathering}.
 */
public final class RuinsGenerator extends ChunkGenerator {
    static final int SEA = Plans.SEA;
    /** How far past its own cell a site may draw: radius 25 plus its 1-block frame, against the 23-block margin (3), with room to spare. */
    static final int SITE_REACH = 8;

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

    /**
     * Draws every original ruin touching the canvas' chunk. A chunk lies in exactly one city cell; the 56-block site grid
     * is not a multiple of 16, so a chunk may touch two site cells each way, and a site may reach a few blocks past its
     * own cell (radius 25 against a 23-block margin): every site cell within reach is drawn (Sites.draw clips to the chunk).
     */
    void stamp(Canvas c) {
        Plans.Door door = plans.door();
        if (door.near(c.x0 + 8, c.z0 + 8, 16)) Cult.door(door, c);
        Catacombs.draw(plans, c);
        Field.draw(plans, c);
        Plans.City city = plans.city(Math.floorDiv(c.x0, Plans.CITY_GRID), Math.floorDiv(c.z0, Plans.CITY_GRID));
        if (city != null) OldCity.draw(city, c);
        for (int i = Math.floorDiv(c.x0 - SITE_REACH, Plans.SITE_GRID); i <= Math.floorDiv(c.x0 + 15 + SITE_REACH, Plans.SITE_GRID); i++)
            for (int j = Math.floorDiv(c.z0 - SITE_REACH, Plans.SITE_GRID); j <= Math.floorDiv(c.z0 + 15 + SITE_REACH, Plans.SITE_GRID); j++) {
                Plans.Site site = plans.site(i, j);
                if (site != null) Sites.draw(site, c);
            }
    }

    // Biome slots the browser client styles green and snow-free (JasprBiomeStyles restyles every slot for the horror
    // overworld: FOREST, PLAINS and JUNGLE_EDGE come out dry and olive there, SWAMPLAND snowy). These keep temperature
    // 0.6 / rainfall 0.7 with rain, so grass and leaves stay lush; ROOFED_FOREST and the swamp keep their own tints.
    static final Biome MARSH = Biome.MUTATED_SWAMPLAND, WOODS = Biome.FOREST_HILLS, JUNGLE = Biome.JUNGLE, MEADOW = Biome.MUTATED_BIRCH_FOREST,
        DARKWOOD = Biome.ROOFED_FOREST, HIGHLANDS = Biome.EXTREME_HILLS_WITH_TREES, SEA_BIOME = Biome.DEEP_OCEAN;

    /** One sombre biome over the land and a green-watered one over the drowned parts (see the client-style note above). */
    Biome biome(int wx, int wz, int h) { return h <= SEA - 4 ? SEA_BIOME : DARKWOOD; }

    /**
     * Bare, drowned ground: stone under a crust of gravel, andesite, cobble and cracked rock, with only the odd tuft of
     * grass (about 3% of columns). The field paves most of it; the sea floor is gravel, clay and fallen prismarine.
     */
    @SuppressWarnings("deprecation")
    private void column(ChunkData d, BiomeGrid biomes, int x, int z, int wx, int wz, int h) {
        biomes.setBiome(x, z, biome(wx, wz, h));
        if (h > 2) d.setRegion(x, 1, z, x + 1, h, z + 1, Material.STONE);
        d.setBlock(x, 0, z, Material.BEDROCK);
        for (int y = 1; y <= 3; y++) if (Hash.unit(seed ^ 0xBEDL, wx, y, wz) < 0.6 - y * 0.18) d.setBlock(x, y, z, Material.BEDROCK);
        double n = Hash.unit(seed ^ 0x5EAL, wx, h, wz);
        if (h <= SEA) {
            if (n < 0.45) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
            else if (n < 0.7) d.setBlock(x, h, z, 82, (byte) 0);
            else if (n < 0.85) d.setBlock(x, h, z, Canvas.PRISMARINE, (byte) 2);
            else d.setBlock(x, h, z, Canvas.STONE, (byte) 5);
            d.setRegion(x, h + 1, z, x + 1, SEA + 1, z + 1, Material.STATIONARY_WATER);
            return;
        }
        if (n < 0.03) {
            d.setBlock(x, h - 1, z, Canvas.DIRT, (byte) 0);
            d.setBlock(x, h, z, Canvas.GRASS, (byte) 0);
            if (Hash.unit(seed ^ 0x9A55L, wx, h, wz) < 0.3 && h < 254) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 1);
        } else if (n < 0.33) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
        else if (n < 0.55) d.setBlock(x, h, z, Canvas.STONE, (byte) 5);
        else if (n < 0.70) d.setBlock(x, h, z, Canvas.COBBLE, (byte) 0);
        else if (n < 0.80) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
        else if (n < 0.86) d.setBlock(x, h, z, Canvas.MOSSY, (byte) 0);
        // else: bare stone
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) { return Collections.singletonList(populator); }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 0.5, plans.surface(0, 0) + 1, 0.5); }

    @Override
    public boolean canSpawn(World world, int x, int z) { return plans.surface(x, z) > SEA; }
}
