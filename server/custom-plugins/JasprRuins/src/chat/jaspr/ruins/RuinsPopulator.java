package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityLootable;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;

/**
 * Finishes a ruins chunk once its neighbours exist: loot tables for the ruins' chests and mobs for their spawners
 * (recomputed from the same plan, so nothing has to be remembered between generation and population), trees at the
 * vanilla +8 offset away from every ruin and the Lost Cities' land, ore veins and lily pads.
 */
final class RuinsPopulator extends BlockPopulator {
    private final RuinsGenerator gen;
    volatile long chests, spawners, trees, failures;

    RuinsPopulator(RuinsGenerator gen) { this.gen = gen; }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        int cx = chunk.getX(), cz = chunk.getZ();
        try { tiles(world, chunk); } catch (RuntimeException | LinkageError e) { fail("tiles", cx, cz, e); }
        try { trees(world, random, cx, cz); } catch (RuntimeException e) { fail("trees", cx, cz, e); }
        try { ores(chunk, random); lilies(world, chunk, random); } catch (RuntimeException e) { fail("details", cx, cz, e); }
    }

    private void fail(String what, int cx, int cz, Throwable e) {
        failures++;
        gen.reportFailure("populate-" + what + " chunk=" + cx + "," + cz, e);
    }

    /** The chests and spawners the plan put in this chunk, found by drawing it again without a block target. */
    static List<Canvas.Tile> tilesOf(RuinsGenerator gen, int cx, int cz) {
        int[][] none = new int[16][16];
        for (int[] row : none) java.util.Arrays.fill(row, -1);
        List<Canvas.Tile> tiles = new ArrayList<>();
        gen.stamp(new Canvas(gen.seed, cx, cz, null, none, tiles));
        return tiles;
    }

    @SuppressWarnings("deprecation")
    private void tiles(World world, Chunk chunk) {
        for (Canvas.Tile t : tilesOf(gen, chunk.getX(), chunk.getZ())) {
            Block b = chunk.getBlock(t.x & 15, t.y, t.z & 15);
            if (t.chest) {
                if (b.getType() != Material.CHEST) continue;
                TileEntity te = ((CraftWorld) world).getHandle().getTileEntity(new BlockPosition(t.x, t.y, t.z));
                if (te instanceof TileEntityLootable) {
                    ((TileEntityLootable) te).setLootTable(new MinecraftKey(t.what), Hash.of(gen.seed, t.x, t.y, t.z));
                    te.update();
                    chests++;
                }
            } else {
                if (b.getType() != Material.MOB_SPAWNER) continue;
                BlockState state = b.getState();
                if (!(state instanceof CreatureSpawner)) continue;
                ((CreatureSpawner) state).setSpawnedType(EntityType.valueOf(t.what));
                state.update(true, false);
                spawners++;
            }
        }
    }

    private void trees(World world, Random random, int cx, int cz) {
        int x0 = cx * 16 + 8, z0 = cz * 16 + 8;
        Biome biome = world.getBiome(x0 + 8, z0 + 8);
        int count;
        if (biome == RuinsGenerator.WOODS) count = 6;
        else if (biome == RuinsGenerator.DARKWOOD) count = 9;
        else if (biome == RuinsGenerator.JUNGLE) count = 5;
        else if (biome == RuinsGenerator.MARSH || biome == RuinsGenerator.HIGHLANDS) count = 2;
        else if (biome == RuinsGenerator.MEADOW) count = random.nextInt(3) == 0 ? 1 : 0;
        else count = 0;
        for (int i = 0; i < count; i++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            if (!gen.plans.open(x, z, 4) || gen.reserved.test(x - 4, z - 4, 9, 9)) continue;
            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            if (ground != Material.GRASS && ground != Material.DIRT) continue;
            if (world.generateTree(new Location(world, x, y, z), pick(biome, random))) trees++;
        }
    }

    private static TreeType pick(Biome biome, Random random) {
        int r = random.nextInt(100);
        if (biome == RuinsGenerator.DARKWOOD)
            return r < 55 ? TreeType.DARK_OAK : r < 80 ? TreeType.TREE : r < 88 ? TreeType.BROWN_MUSHROOM : r < 95 ? TreeType.RED_MUSHROOM : TreeType.BIG_TREE;
        if (biome == RuinsGenerator.JUNGLE) return r < 45 ? TreeType.SMALL_JUNGLE : r < 80 ? TreeType.JUNGLE_BUSH : TreeType.COCOA_TREE;
        if (biome == RuinsGenerator.MARSH) return TreeType.SWAMP;
        if (biome == RuinsGenerator.HIGHLANDS) return r < 60 ? TreeType.REDWOOD : TreeType.TREE;
        if (biome == RuinsGenerator.MEADOW) return r < 70 ? TreeType.TREE : TreeType.BIG_TREE;
        return r < 65 ? TreeType.TREE : r < 90 ? TreeType.BIRCH : TreeType.BIG_TREE;
    }

    // id, veins per chunk, min y, max y, blocks per vein (vanilla-like amounts)
    private static final int[][] ORES = {{16, 20, 5, 128, 12}, {15, 20, 5, 64, 8}, {14, 2, 5, 32, 8}, {73, 8, 5, 16, 7}, {56, 1, 5, 16, 7}, {21, 1, 10, 30, 6}};

    @SuppressWarnings("deprecation")
    private void ores(Chunk chunk, Random random) {
        for (int[] ore : ORES)
            for (int v = 0; v < ore[1]; v++) {
                int x = 1 + random.nextInt(14), z = 1 + random.nextInt(14), y = ore[2] + random.nextInt(ore[3] - ore[2]);
                for (int k = 0; k < ore[4]; k++) {
                    Block b = chunk.getBlock(x, y, z);
                    if (b.getTypeId() == 1 && b.getData() == 0) b.setTypeIdAndData(ore[0], (byte) 0, false);
                    x = Math.max(0, Math.min(15, x + random.nextInt(3) - 1));
                    y = Math.max(1, Math.min(250, y + random.nextInt(3) - 1));
                    z = Math.max(0, Math.min(15, z + random.nextInt(3) - 1));
                }
            }
    }

    @SuppressWarnings("deprecation")
    private void lilies(World world, Chunk chunk, Random random) {
        for (int i = 0; i < 4; i++) {
            int x = random.nextInt(16), z = random.nextInt(16);
            int wx = chunk.getX() * 16 + x, wz = chunk.getZ() * 16 + z;
            if (world.getBiome(wx, wz) != RuinsGenerator.MARSH) continue;
            int y = world.getHighestBlockYAt(wx, wz);
            if (y > 64 || chunk.getBlock(x, y - 1, z).getType() != Material.STATIONARY_WATER) continue;
            chunk.getBlock(x, y, z).setTypeIdAndData(111, (byte) 0, false);
        }
    }
}
