package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

/**
 * Finishes an Atlas chunk once its neighbours exist, recomputing its tiles from the same plan (nothing is remembered
 * between generation and population): fills chests, carves signs, sets spawners, colours beds and banners, plants pots,
 * dresses statues, and hands every person and marker to the runtime to spawn or register. Ore veins run under the land.
 */
final class AtlasPopulator extends BlockPopulator {
    /** The runtime side of population (spawning people, registering markers, filling chests); absent in offline tests. */
    interface Sink { void tile(World world, Chunk chunk, Canvas.Tile tile, Random random); }

    private final AtlasGenerator gen;
    volatile Sink sink;
    volatile long populated, tiles, failures;

    AtlasPopulator(AtlasGenerator gen) { this.gen = gen; }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        int cx = chunk.getX(), cz = chunk.getZ();
        populated++;
        try {
            Sink s = sink;
            for (Canvas.Tile t : tilesOf(gen, cx, cz, gen.masks.get(cx, cz, gen.currentMask()))) {
                tiles++;
                if (s != null) {
                    try { s.tile(world, chunk, t, random); }
                    catch (RuntimeException | LinkageError e) { failures++; gen.reportFailure("tile " + t.what + " at " + t.x + "," + t.y + "," + t.z, e); }
                }
            }
        } catch (RuntimeException e) { failures++; gen.reportFailure("populate chunk=" + cx + "," + cz, e); }
        try { ores(chunk, random); } catch (RuntimeException e) { failures++; gen.reportFailure("ores chunk=" + cx + "," + cz, e); }
    }

    /** The tiles the plan put in this chunk, found by drawing it again without a block target. */
    static List<Canvas.Tile> tilesOf(AtlasGenerator gen, int cx, int cz, int mask) {
        int[][] heights = gen.heights(cx, cz);
        List<Canvas.Tile> tiles = new ArrayList<>();
        gen.stamp(new Canvas(gen.seed, cx, cz, mask, null, heights, tiles));
        return tiles;
    }

    // id, veins per chunk, min y, max y, blocks per vein
    private static final int[][] ORES = {{16, 18, 5, 110, 12}, {15, 18, 5, 60, 8}, {14, 2, 5, 32, 8}, {73, 7, 5, 16, 7}, {56, 1, 5, 16, 6}, {21, 1, 10, 30, 6}};

    @SuppressWarnings("deprecation")
    private void ores(Chunk chunk, Random random) {
        for (int[] ore : ORES)
            for (int v = 0; v < ore[1]; v++) {
                int x = 1 + random.nextInt(14), z = 1 + random.nextInt(14), y = ore[2] + random.nextInt(ore[3] - ore[2]);
                for (int k = 0; k < ore[4]; k++) {
                    org.bukkit.block.Block b = chunk.getBlock(x, y, z);
                    if (b.getTypeId() == 1 && b.getData() == 0) b.setTypeIdAndData(ore[0], (byte) 0, false);
                    x = Math.max(0, Math.min(15, x + random.nextInt(3) - 1));
                    y = Math.max(1, Math.min(250, y + random.nextInt(3) - 1));
                    z = Math.max(0, Math.min(15, z + random.nextInt(3) - 1));
                }
            }
    }
}
