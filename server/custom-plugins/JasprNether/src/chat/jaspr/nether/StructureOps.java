package chat.jaspr.nether;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Skull;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.EntityType;

/** Tile entities and residents of placed structures, done through Bukkit after the batched block flush. */
final class StructureOps {
    private static final BlockFace[] ROT16 = {BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST, BlockFace.NORTH,
        BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
        BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};

    private final NetherPlugin plugin;
    int chests, spawners, skulls, residents, lavaTicks;

    StructureOps(NetherPlugin plugin) { this.plugin = plugin; }

    void placeChest(World w, int x, int y, int z, int facing, String table, Random r) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setTypeIdAndData(Blocks.CHEST, (byte) facing, false);
        BlockState s = b.getState();
        if (!(s instanceof Chest)) return;
        Loot.fill(((Chest) s).getBlockInventory(), table, r); // live inventory; never update() afterwards
        chests++;
    }

    void placeSpawner(World w, int x, int y, int z, String mob) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.MOB_SPAWNER, false);
        BlockState s = b.getState();
        if (!(s instanceof CreatureSpawner)) return;
        EntityType type = mob.endsWith("ghast") ? EntityType.GHAST : EntityType.BLAZE;
        ((CreatureSpawner) s).setSpawnedType(type);
        s.update(true, false);
        spawners++;
    }

    void placeSkull(World w, int x, int y, int z, int type, int rot) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setTypeIdAndData(Blocks.SKULL, (byte) 1, false);
        BlockState s = b.getState();
        if (!(s instanceof Skull)) return;
        ((Skull) s).setSkullType(type == 0 ? SkullType.SKELETON : type == 2 ? SkullType.ZOMBIE : SkullType.SKELETON);
        ((Skull) s).setRotation(ROT16[rot & 15]);
        s.update(true, false);
        skulls++;
    }

    void spawnResident(World w, int x, int y, int z, String kind) {
        if (plugin.mobs == null || !w.isChunkLoaded(x >> 4, z >> 4)) return;
        String k = kind.replace("netherex:", "");
        if (plugin.mobs.spawn(k.equals("gold_golem") ? "gold_golem" : "pigtificate", new org.bukkit.Location(w, x + 0.5, y, z + 0.5), false) != null)
            residents++;
    }

    void tickLava(World w, int x, int y, int z) {
        try {
            ((CraftWorld) w).getHandle().a(new net.minecraft.server.v1_12_R1.BlockPosition(x, y, z), net.minecraft.server.v1_12_R1.Blocks.FLOWING_LAVA, 10);
            lavaTicks++;
        } catch (RuntimeException ignored) { }
    }
}
