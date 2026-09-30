package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/**
 * The Backrooms: seven closed zones along x (one per {@link Level}), nothing between them. Every column is drawn by
 * {@link Rooms} in its level's {@link Style}; the populator then fills the chunk's chests from the level's loot and
 * writes its signs (it replays the same drawing without writing blocks, which only records the tiles).
 */
public final class BackroomsGenerator extends ChunkGenerator {
    final long seed;
    private final BiConsumer<String, Throwable> failure;
    private final Filler filler = new Filler();
    volatile long chunks, failures, chests, signs;

    /** How chests get their contents (the plugin's loot tables; a test can plug in its own). */
    interface Loot { void fill(org.bukkit.inventory.Inventory inv, int level, double danger, String table, Random random); }
    private volatile Loot loot;

    BackroomsGenerator(long seed, BiConsumer<String, Throwable> failure) { this.seed = seed; this.failure = failure; }

    void loot(Loot loot) { this.loot = loot; }

    void reportFailure(String where, Throwable e) {
        failures++;
        if (failure != null) failure.accept(where, e);
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);
        try {
            fill(data, biomes, cx, cz, null);
        } catch (RuntimeException e) {
            reportFailure("chunk=" + cx + "," + cz, e);
        }
        chunks++;
        return data;
    }

    /** Draws a chunk (data may be null to only collect its tiles). Separate from Bukkit's factory so tests can run it. */
    void fill(ChunkData data, BiomeGrid biomes, int cx, int cz, List<Canvas.Tile> tiles) {
        Canvas c = new Canvas(seed, cx, cz, data, tiles);
        int x0 = cx << 4, z0 = cz << 4;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                if (biomes != null) biomes.setBiome(dx, dz, Biome.FOREST_HILLS);
                Level lv = Level.at(x, z);
                if (lv == null) continue;
                Rooms.column(c, lv, Styles.of(lv), x, z);
            }
    }

    /** Whether any level touches the chunk (empty chunks between zones need no populating). */
    static boolean touches(int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        return Level.at(x0, z0) != null || Level.at(x0 + 15, z0 + 15) != null || Level.at(x0, z0 + 15) != null || Level.at(x0 + 15, z0) != null;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) { return Collections.singletonList(filler); }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        double[] t = Rooms.threshold();
        return new Location(world, t[0], t[1], t[2], (float) t[3], 0);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) { return true; }

    /** Fills the chunk's chests and writes its signs. */
    private final class Filler extends BlockPopulator {
        @Override
        public void populate(World world, Random random, Chunk chunk) {
            if (!touches(chunk.getX(), chunk.getZ())) return;
            List<Canvas.Tile> tiles = new ArrayList<>();
            try {
                fill(null, null, chunk.getX(), chunk.getZ(), tiles);
            } catch (RuntimeException e) {
                reportFailure("tiles=" + chunk.getX() + "," + chunk.getZ(), e);
                return;
            }
            for (Canvas.Tile t : tiles) {
                try {
                    Block b = world.getBlockAt(t.x, t.y, t.z);
                    BlockState state = b.getState();
                    if (t.kind == Canvas.CHEST_TILE && b.getType() == Material.CHEST && state instanceof Chest) {
                        Loot l = loot;
                        // The live inventory: never update() the state afterwards (it would overwrite the contents).
                        if (l != null) l.fill(((Chest) state).getBlockInventory(), t.level, t.danger, t.text,
                            new Random(Hash.of(seed, 901, t.x, t.y, t.z)));
                        chests++;
                    } else if (t.kind == Canvas.SIGN_TILE && state instanceof Sign) {
                        Sign s = (Sign) state;
                        String[] lines = t.text.split("\n", -1);
                        for (int i = 0; i < 4; i++) s.setLine(i, i < lines.length ? lines[i] : "");
                        s.update(false, false);
                        signs++;
                    }
                } catch (RuntimeException e) {
                    reportFailure("tile=" + t.x + "," + t.y + "," + t.z, e);
                }
            }
        }
    }
}
