package chat.jaspr.atlas;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Atlas: the land, then everything built on it, in a fixed order (cells, sites, roads, the Lampwall and the Teeth, the
 * places). Every chunk is a pure function of the seed and the realm's liberation mask, so land that is liberated later
 * can be regenerated in its healed form and compared block by block with what was first generated.
 */
public final class AtlasGenerator extends ChunkGenerator {
    final long seed;
    final Plans plans;
    private final AtlasPopulator populator;
    private final BiConsumer<String, Throwable> failure;
    /** The liberation mask new chunks are generated with (the realm's state when they are first made). */
    private final IntSupplier maskSource;
    volatile long chunks, failures;
    /** The liberation mask each chunk was generated (or last healed) with, so population and healing match it. */
    final MaskStore masks = new MaskStore();

    AtlasGenerator(long seed, IntSupplier maskSource, BiConsumer<String, Throwable> failure) {
        this.seed = seed;
        this.plans = new Plans(seed);
        this.maskSource = maskSource;
        this.failure = failure;
        this.populator = new AtlasPopulator(this);
    }

    AtlasPopulator populator() { return populator; }

    int currentMask() { return maskSource == null ? 0 : maskSource.getAsInt(); }

    void reportFailure(String where, Throwable e) {
        failures++;
        if (failure != null) failure.accept(where, e);
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);
        int mask = currentMask();
        masks.put(cx, cz, mask);
        fill(data, biomes, cx, cz, mask);
        return data;
    }

    int[][] heights(int cx, int cz) {
        int[][] h = new int[16][16];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) h[x][z] = plans.surface(cx * 16 + x, cz * 16 + z);
        return h;
    }

    /** Terrain, then everything built on it. Separate from Bukkit's ChunkData factory so tests and healing can run it. */
    void fill(ChunkData d, BiomeGrid biomes, int cx, int cz, int mask) { fill(d, biomes, cx, cz, mask, null); chunks++; }

    /** The whole chunk drawn into {@code d}; the tiles it places are added to {@code tiles} when given. */
    void fill(ChunkData d, BiomeGrid biomes, int cx, int cz, int mask, List<Canvas.Tile> tiles) {
        int[][] heights = heights(cx, cz);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) column(d, biomes, x, z, cx * 16 + x, cz * 16 + z, heights[x][z], mask);
        try {
            stamp(new Canvas(seed, cx, cz, mask, d, heights, tiles));
        } catch (RuntimeException e) {
            reportFailure("chunk=" + cx + "," + cz, e);
        }
    }

    /** Everything built on the land, in order; later layers win. */
    void stamp(Canvas c) {
        Cells.draw(plans, c);
        Sites.draw(plans, c);
        Roads.draw(plans, c);
        Line.draw(plans, c);
        Teeth.draw(plans, c);
        Places.draw(plans, c);
        Infill.draw(plans, c);
    }

    // ------------------------------------------------------------------ biomes (chosen for how the browser client styles them)

    // The client restyles every vanilla biome slot for the horror overworld (see scripts/biome-client-patch.cjs): these
    // keep their vanilla grass logic with the styled climate. Extreme Hills is lush (0.6 / 0.7): the Concord and healed
    // land. Plains, Birch Forest, Savanna Plateau, Stone Beach and Jungle Edge are dry (1.1 / 0.05, no rain): scorched
    // olive grass where any survives. Roofed Forest M darkens that further: the petrified Weald.
    static final Biome LUSH = Biome.EXTREME_HILLS, WOUND = Biome.BIRCH_FOREST, ASHEN = Biome.PLAINS, STONEWOOD = Biome.MUTATED_ROOFED_FOREST,
        FORGE = Biome.SAVANNA_ROCK, FALLEN = Biome.STONE_BEACH, PLATEAU = Biome.JUNGLE_EDGE;

    static Biome biome(Realm.Zone zone, int mask) {
        if (zone.province != null && (mask & zone.province.bit) != 0) return LUSH;
        switch (zone) {
            case CONCORD: case LINE: case RIM: return LUSH;
            case WOUND: return WOUND;
            case WEALD: return STONEWOOD;
            case FORGES: return FORGE;
            case FALLEN: return FALLEN;
            case PLATEAU: return PLATEAU;
            default: return ASHEN;
        }
    }

    // ------------------------------------------------------------------ the land

    @SuppressWarnings("deprecation")
    private void column(ChunkData d, BiomeGrid biomes, int x, int z, int wx, int wz, int h, int mask) {
        Realm.Zone zone = Realm.zone(wx, wz);
        biomes.setBiome(x, z, biome(zone, mask));
        d.setBlock(x, 0, z, Material.BEDROCK);
        for (int y = 1; y <= 3; y++) if (Hash.unit(seed ^ 0xBEDL, wx, y, wz) < 0.6 - y * 0.18) d.setBlock(x, y, z, Material.BEDROCK);
        if (h > 4) d.setRegion(x, 1, z, x + 1, h - 3, z + 1, Material.STONE);
        for (int y = 1; y <= 3; y++) if (Hash.unit(seed ^ 0xBEDL, wx, y, wz) < 0.6 - y * 0.18) d.setBlock(x, y, z, Material.BEDROCK);
        boolean healed = zone.province != null && (mask & zone.province.bit) != 0;
        double n = Hash.unit(seed ^ 0x5EAL, wx, h, wz), m = Hash.unit(seed ^ 0x5EBL, wx, h + 1, wz);
        switch (zone) {
            case CONCORD: case LINE: case RIM: lush(d, x, z, h, n, m, 1.0); return;
            case WOUND: wound(d, x, z, wx, wz, h, n, m); return;
            default:
                if (healed) { healing(d, x, z, h, n, m, zone); return; }
                dominion(d, x, z, wx, wz, h, n, m, zone);
        }
    }

    /** Grass over dirt, with meadow flowers and tall grass (the cells and roads draw over it). */
    @SuppressWarnings("deprecation")
    private static void lush(ChunkData d, int x, int z, int h, double n, double m, double cover) {
        d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
        d.setBlock(x, h, z, Canvas.GRASS, (byte) 0);
        if (h >= 254) return;
        if (m < 0.20 * cover) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 1);
        else if (m < 0.215 * cover) d.setBlock(x, h + 1, z, Canvas.RED_FLOWER, (byte) (n < 0.3 ? 3 : n < 0.55 ? 8 : n < 0.7 ? 0 : n < 0.85 ? 6 : 1));
        else if (m < 0.225 * cover) d.setBlock(x, h + 1, z, Canvas.YELLOW_FLOWER, (byte) 0);
    }

    /** The Wound: grass giving way to scorched earth, gravel and ash toward the Dominion. */
    @SuppressWarnings("deprecation")
    private void wound(ChunkData d, int x, int z, int wx, int wz, int h, double n, double m) {
        double depth = Realm.woundDepth(wx, wz) + (Hash.noise(seed ^ 0x40DL, wx, wz, 11) - 0.5) * 0.35;
        d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
        if (depth < 0.2 || n < 0.35 - depth * 0.5) {
            d.setBlock(x, h, z, Canvas.GRASS, (byte) 0);
            if (h < 254 && m < 0.10) d.setBlock(x, h + 1, z, Canvas.DEADBUSH, (byte) 0);
            else if (h < 254 && m < 0.16) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 1);
            return;
        }
        if (n < 0.55) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
        else if (n < 0.70) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
        else if (n < 0.80) d.setBlock(x, h, z, Canvas.DIRT, (byte) 2);
        else if (n < 0.92) d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.GRAY);
        else d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.BLACK);
        if (h >= 254) return;
        if (m < 0.06) d.setBlock(x, h + 1, z, Canvas.DEADBUSH, (byte) 0);
        else if (depth > 0.55 && m < 0.16) d.setBlock(x, h + 1, z, Canvas.CARPET, (byte) (m < 0.12 ? Canvas.GRAY : Canvas.BLACK));
    }

    /** Occupied Dominion ground: ash and slag, cracked with magma on the Plateau and at the Forges. */
    @SuppressWarnings("deprecation")
    private void dominion(ChunkData d, int x, int z, int wx, int wz, int h, double n, double m, Realm.Zone zone) {
        d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.STONE);
        switch (zone) {
            case WEALD:
                if (n < 0.30) d.setBlock(x, h, z, Canvas.STONE, (byte) 5);
                else if (n < 0.50) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
                else if (n < 0.66) d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.GRAY);
                else if (n < 0.78) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
                else if (n < 0.90) d.setBlock(x, h, z, Canvas.MOSSY, (byte) 0);
                else d.setBlock(x, h, z, Canvas.STONE, (byte) 0);
                if (h < 254 && m < 0.05) d.setBlock(x, h + 1, z, Canvas.DEADBUSH, (byte) 0);
                else if (h < 254 && m < 0.10) d.setBlock(x, h + 1, z, Canvas.CARPET, (byte) Canvas.GRAY);
                return;
            case FORGES: {
                double crack = Hash.noise(seed ^ 0xF0F0L, wx, wz, 9);
                if (crack > 0.47 && crack < 0.515) { d.setBlock(x, h, z, Canvas.MAGMA, (byte) 0); return; }
                if (n < 0.35) d.setBlock(x, h, z, Canvas.CLAY, (byte) Canvas.BLACK);
                else if (n < 0.55) d.setBlock(x, h, z, Canvas.CONCRETE, (byte) Canvas.BLACK);
                else if (n < 0.70) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
                else if (n < 0.82) d.setBlock(x, h, z, Canvas.SOUL_SAND, (byte) 0);
                else if (n < 0.92) d.setBlock(x, h, z, Canvas.COBBLE, (byte) 0);
                else d.setBlock(x, h, z, Canvas.NETHERRACK, (byte) 0);
                if (h < 254 && m < 0.08) d.setBlock(x, h + 1, z, Canvas.CARPET, (byte) Canvas.BLACK);
                return;
            }
            case PLATEAU: {
                double crack = Hash.noise(seed ^ 0xF1F1L, wx, wz, 13);
                if (crack > 0.48 && crack < 0.52) { d.setBlock(x, h, z, Canvas.MAGMA, (byte) 0); return; }
                if (n < 0.45) d.setBlock(x, h, z, Canvas.CLAY, (byte) Canvas.BLACK);
                else if (n < 0.70) d.setBlock(x, h, z, Canvas.CONCRETE, (byte) Canvas.BLACK);
                else if (n < 0.85) d.setBlock(x, h, z, Canvas.OBSIDIAN, (byte) 0);
                else d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.BLACK);
                if (h < 254 && m < 0.06) d.setBlock(x, h + 1, z, Canvas.CARPET, (byte) Canvas.BLACK);
                return;
            }
            default:
                if (n < 0.30) d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.GRAY);
                else if (n < 0.42) d.setBlock(x, h, z, Canvas.POWDER, (byte) Canvas.BLACK);
                else if (n < 0.58) d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
                else if (n < 0.72) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
                else if (n < 0.82) d.setBlock(x, h, z, Canvas.SOUL_SAND, (byte) 0);
                else if (n < 0.94) d.setBlock(x, h, z, Canvas.CLAY, (byte) Canvas.GRAY);
                else d.setBlock(x, h, z, Canvas.STONE, (byte) 5);
                if (h >= 254) return;
                if (m < 0.10) d.setBlock(x, h + 1, z, Canvas.CARPET, (byte) (m < 0.06 ? Canvas.GRAY : Canvas.BLACK));
                else if (m < 0.13) d.setBlock(x, h + 1, z, Canvas.DEADBUSH, (byte) 0);
        }
    }

    /** Liberated Dominion ground: soil and young grass returning through the last of the ash. */
    @SuppressWarnings("deprecation")
    private static void healing(ChunkData d, int x, int z, int h, double n, double m, Realm.Zone zone) {
        d.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
        if (zone == Realm.Zone.PLATEAU && n < 0.35) { d.setBlock(x, h, z, Canvas.STONE, (byte) 5); return; }
        if (n < 0.70) d.setBlock(x, h, z, Canvas.GRASS, (byte) 0);
        else if (n < 0.86) d.setBlock(x, h, z, Canvas.DIRT, (byte) 1);
        else d.setBlock(x, h, z, Canvas.GRAVEL, (byte) 0);
        if (h >= 254 || n >= 0.70) return;
        if (m < 0.14) d.setBlock(x, h + 1, z, Canvas.TALLGRASS, (byte) 1);
        else if (m < 0.155) d.setBlock(x, h + 1, z, Canvas.RED_FLOWER, (byte) (m < 0.147 ? 3 : 8));
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) { return Collections.singletonList(populator); }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        Realm.Place g = Realm.Place.GATE_OF_STRANGERS;
        return new Location(world, g.x + 0.5, Terrain.base(g) + 1, g.z + 0.5);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) { return true; }
}
