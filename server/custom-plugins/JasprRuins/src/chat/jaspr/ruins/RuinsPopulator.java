package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityLootable;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Finishes a ruins chunk once its neighbours exist, recomputing its tiles from the same plan (nothing is remembered
 * between generation and population): loot tables, lore books and relics for the chests, mobs for the spawners and the
 * Choir's chants on the carved signs. Ore veins run through the rock. Nothing grows here.
 */
final class RuinsPopulator extends BlockPopulator {
    private final RuinsGenerator gen;
    volatile long chests, spawners, signs, traps, failures;

    RuinsPopulator(RuinsGenerator gen) { this.gen = gen; }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        int cx = chunk.getX(), cz = chunk.getZ();
        try { tiles(world, chunk); } catch (RuntimeException | LinkageError e) { fail("tiles", cx, cz, e); }
        try { ores(chunk, random); } catch (RuntimeException e) { fail("ores", cx, cz, e); }
    }

    private void fail(String what, int cx, int cz, Throwable e) {
        failures++;
        gen.reportFailure("populate-" + what + " chunk=" + cx + "," + cz, e);
    }

    /** The chests, spawners and signs the plan put in this chunk, found by drawing it again without a block target. */
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
            if (t.kind == Canvas.CHEST_TILE) {
                if (b.getType() != Material.CHEST) continue;
                // Extras go in first (into the live inventory, never followed by a state update); the loot table fills the
                // remaining slots when the chest is first opened.
                if (t.extras != null) {
                    BlockState state = b.getState();
                    if (state instanceof Chest) extras(((Chest) state).getBlockInventory(), t);
                }
                TileEntity te = ((CraftWorld) world).getHandle().getTileEntity(new BlockPosition(t.x, t.y, t.z));
                if (te instanceof TileEntityLootable) {
                    ((TileEntityLootable) te).setLootTable(new MinecraftKey(t.what), Hash.of(gen.seed, t.x, t.y, t.z));
                    te.update();
                    chests++;
                }
            } else if (t.kind == Canvas.SPAWNER_TILE) {
                if (b.getType() != Material.MOB_SPAWNER) continue;
                BlockState state = b.getState();
                if (!(state instanceof CreatureSpawner)) continue;
                ((CreatureSpawner) state).setSpawnedType(EntityType.valueOf(t.what));
                state.update(true, false);
                spawners++;
            } else if (t.kind == Canvas.DISPENSER_TILE) {
                if (b.getType() != Material.DISPENSER) continue;
                BlockState state = b.getState();
                if (state instanceof org.bukkit.block.Dispenser) ((org.bukkit.block.Dispenser) state).getInventory().addItem(new ItemStack(Material.ARROW, 24));
                traps++;
            } else {
                if (b.getType() != Material.WALL_SIGN) continue;
                BlockState state = b.getState();
                if (!(state instanceof Sign)) continue;
                String[] lines = t.what.split("\n", -1);
                for (int i = 0; i < 4 && i < lines.length; i++) ((Sign) state).setLine(i, lines[i]);
                state.update(true, false);
                signs++;
            }
        }
    }

    /** "lore:<n>" puts lore book n (mod the library) in the chest; "trinket:<p>" adds a relic with probability p. */
    private void extras(Inventory inv, Canvas.Tile t) {
        Random r = new Random(Hash.of(gen.seed ^ 0x4C6F7265L, t.x, t.y, t.z));
        for (String part : t.extras.split(";")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            ItemStack item = null;
            try {
                if (kv[0].equals("lore")) item = Lore.book(Integer.parseInt(kv[1]) % (Lore.bookCount() - 1));
                else if (kv[0].equals("trinket") && r.nextDouble() < Double.parseDouble(kv[1])) item = Trinkets.random(r);
            } catch (NumberFormatException ignored) { }
            if (item == null) continue;
            int slot = r.nextInt(inv.getSize());
            for (int k = 0; k < inv.getSize() && inv.getItem(slot) != null; k++) slot = (slot + 1) % inv.getSize();
            if (inv.getItem(slot) == null) inv.setItem(slot, item);
        }
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
}
