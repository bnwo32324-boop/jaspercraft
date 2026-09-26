package chat.jaspr.muse;

import chat.jaspr.biomes.ChunkLight;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.TileEntity;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.EntityType;

/**
 * Writes one chunk tile of a planned Muse+GLM_Maps site: fits the ground (flattens natural terrain inside the
 * footprint, fills foundations under the walls), batches the design's blocks straight into the chunk sections,
 * relights once, then furnishes it -- loot in the design's own chests, the site's vault chest, live spawners.
 * Used both by the new-chunk populator and by the retrofit job for chunks that already existed.
 */
final class Stamper {
    private Stamper() {}

    /** Natural terrain the ground fit may clear or fill around; never anything a player or plugin builds with. */
    private static final boolean[] NATURAL = new boolean[256];
    static {
        for (int id : new int[]{1, 2, 3, 12, 13, 14, 15, 16, 17, 18, 21, 24, 31, 32, 37, 38, 39, 40, 56, 73, 74, 78, 79, 80, 81,
                82, 83, 86, 99, 100, 106, 110, 111, 129, 159, 161, 162, 172, 174, 175, 51})
            NATURAL[id] = true;
    }
    private static boolean natural(int id) { return id >= 0 && id < 256 && NATURAL[id]; }
    private static boolean fillable(int id) {
        return id == 0 || id == 8 || id == 9 || id == 31 || id == 32 || id == 37 || id == 38 || id == 39 || id == 40
            || id == 78 || id == 106 || id == 175 || id == 18 || id == 161 || id == 83 || id == 111;
    }

    static final class Result { int changed, chests, spawners, vault; }

    static Result stamp(MusePlugin plugin, Chunk chunk, Planner.Plan plan, Pack.Blocks blocks, Ledger.Record record) {
        Catalog.Design d = plan.design;
        int tx = chunk.getX() - Math.floorDiv(plan.x, 16), tz = chunk.getZ() - Math.floorDiv(plan.z, 16);
        Pack.Tile tile = blocks.tile(tx, tz);
        Result result = new Result();
        fitGround(chunk, plan, blocks, tx, tz);
        if (tile != null) result.changed = write(chunk, plan.y, tile);
        furnish(plugin, chunk, plan, tx, tz, result, record);
        return result;
    }

    private static void fitGround(Chunk chunk, Planner.Plan plan, Pack.Blocks blocks, int tx, int tz) {
        Catalog.Design d = plan.design;
        String h = d.habitat;
        boolean land = h.equals("land") || h.equals("tall");
        boolean shore = h.equals("shore");
        if (!land && !shore) return;
        int ground = land ? plan.y + d.surfaceAnchor : 63;
        int top = Math.min(255, plan.y + d.height() - 1);
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int dx = tx * 16 + lx, dz = tz * 16 + lz;
            if (dx < 0 || dz < 0 || dx >= d.width() || dz >= d.depth()) continue;
            // Clear natural terrain standing inside the structure's volume above its ground plane (only up to the
            // column's current top, so a tall design does not scan two hundred empty blocks per column).
            int highest = Math.min(top, chunk.getWorld().getHighestBlockYAt(chunk.getX() * 16 + lx, chunk.getZ() * 16 + lz) + 1);
            for (int y = ground; y <= highest; y++) {
                Block b = chunk.getBlock(lx, y, lz);
                int id = b.getTypeId();
                if (id != 0 && natural(id)) b.setTypeIdAndData(0, (byte) 0, false);
            }
            if (!land) continue;
            // Foundations: where the design's lowest block sits near its ground plane, fill the air/water under it.
            int lowest = -1;
            for (int y = 0; y <= d.surfaceAnchor + 1 && y < d.height(); y++) if (blocks.idAt(dx, y, dz) > 0) { lowest = y; break; }
            if (lowest < 0) continue;
            int wy = plan.y + lowest - 1;
            for (int depth = 0; depth < 24 && wy > 1; depth++, wy--) {
                Block b = chunk.getBlock(lx, wy, lz);
                if (!fillable(b.getTypeId())) break;
                b.setTypeIdAndData(depth < 3 ? 3 : 1, (byte) 0, false);
            }
        }
    }

    /** Direct section writes (as JasprImportedWorldgen's NativeStamp), tile entities through Bukkit, one relight. */
    private static int write(Chunk chunk, int originY, Pack.Tile data) {
        net.minecraft.server.v1_12_R1.Chunk nativeChunk = ((CraftChunk) chunk).getHandle();
        if (!nativeChunk.tileEntities.isEmpty()) {
            for (BlockPosition pos : new ArrayList<>(nativeChunk.tileEntities.keySet())) {
                if ((pos.getX() >> 4) != chunk.getX() || (pos.getZ() >> 4) != chunk.getZ()) continue;
                int localY = pos.getY() - originY;
                if (localY < 0 || localY >= 256) continue;
                int index = (localY << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
                int record = Arrays.binarySearch(data.index, index);
                if (record < 0 || net.minecraft.server.v1_12_R1.Block.getById(data.id[record]).isTileEntity()) continue;
                TileEntity stale = nativeChunk.tileEntities.remove(pos);
                if (stale != null) stale.z();
            }
        }
        ChunkSection[] sections = nativeChunk.getSections();
        boolean skylight = nativeChunk.world.worldProvider.m();
        int[] special = new int[data.size()];
        int specialCount = 0, changed = 0;
        for (int i = 0; i < data.size(); i++) {
            int index = data.index[i], x = index & 15, z = (index >>> 4) & 15, y = originY + (index >>> 8);
            if (y < 0 || y > 255) continue;
            net.minecraft.server.v1_12_R1.Block nativeBlock = net.minecraft.server.v1_12_R1.Block.getById(data.id[i]);
            if (nativeBlock.isTileEntity()) { special[specialCount++] = i; continue; }
            IBlockData state = nativeBlock.fromLegacyData(data.meta[i]);
            int sy = y >>> 4;
            ChunkSection section = sections[sy];
            if (section == null) {
                if (data.id[i] == 0) continue;
                section = new ChunkSection(sy << 4, skylight, (IBlockData[]) null);
                sections[sy] = section;
            }
            if (section.getType(x, y & 15, z) == state) continue;
            section.setType(x, y & 15, z, state);
            changed++;
        }
        for (int j = 0; j < specialCount; j++) {
            int i = special[j], index = data.index[i], x = index & 15, z = (index >>> 4) & 15, y = originY + (index >>> 8);
            chunk.getBlock(x, y, z).setTypeIdAndData(data.id[i], data.meta[i], false);
            changed++;
        }
        if (changed > 0) {
            nativeChunk.initLighting();
            ChunkLight.initialize(chunk);
            nativeChunk.markDirty();
        }
        return changed;
    }

    /** Loot, the vault chest and live spawners for whatever of the site lies in this chunk. */
    private static void furnish(MusePlugin plugin, Chunk chunk, Planner.Plan plan, int tx, int tz, Result result, Ledger.Record record) {
        Catalog.Design d = plan.design;
        long seed = chunk.getWorld().getSeed() ^ Planner.mix(plan.x * 31L + plan.z * 17L + d.id.hashCode());
        int n = 0;
        if (d.chests != null) for (int[] c : d.chests) {
            n++;
            if ((c[0] >> 4) != tx || (c[2] >> 4) != tz) continue;
            Block b = chunk.getBlock(c[0] & 15, plan.y + c[1], c[2] & 15);
            if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) continue;
            BlockState state = b.getState();
            if (!(state instanceof Chest)) continue;
            Random r = new Random(seed ^ (n * 0x9E3779B97F4A7C15L));
            // A design walled with dozens of chests does not become a treasury: a few are stocked, the rest stay bare.
            int stocked = Math.min(d.chests.size(), 2 + d.tier);
            if (r.nextInt(d.chests.size()) >= stocked) continue;
            Loot.fill(plugin, ((Chest) state).getBlockInventory(), d, r, false);   // live inventory; never update()
            result.chests++;
        }
        int[] vault = vaultSpot(d);
        if (vault != null && (vault[0] >> 4) == tx && (vault[2] >> 4) == tz) {
            Block b = chunk.getBlock(vault[0] & 15, plan.y + vault[1], vault[2] & 15);
            Block below = b.getRelative(0, -1, 0);
            if ((b.getType() == Material.AIR || b.getType() == Material.CARPET) && below.getType().isSolid()) {
                b.setTypeIdAndData(54, (byte) 2, false);
                BlockState state = b.getState();
                if (state instanceof Chest) {
                    Loot.fill(plugin, ((Chest) state).getBlockInventory(), d, new Random(seed ^ 0x5641554C54L), true);
                    result.vault = 1;
                }
            }
        }
        if (d.spawners != null) for (int[] s : d.spawners) {
            if ((s[0] >> 4) != tx || (s[2] >> 4) != tz) continue;
            Block b = chunk.getBlock(s[0] & 15, plan.y + s[1], s[2] & 15);
            b.setType(Material.MOB_SPAWNER, false);
            BlockState state = b.getState();
            if (state instanceof CreatureSpawner) {
                EntityType type = Mobs.spawnerType(d);
                ((CreatureSpawner) state).setSpawnedType(type);
                state.update(true, false);
                result.spawners++;
            }
        }
    }

    /** The site's vault chest: its first interior spot, or the boss arena for boss sites (the reward sits there). */
    static int[] vaultSpot(Catalog.Design d) {
        if (d.spots == null || d.spots.isEmpty()) return null;
        if (d.boss != null && d.bossArena != null) {
            int[] best = null; long bd = Long.MAX_VALUE;
            for (int[] s : d.spots) {
                long dx = s[0] - d.bossArena[0], dy = s[1] - d.bossArena[1], dz = s[2] - d.bossArena[2];
                long dist = dx * dx + dy * dy * 4 + dz * dz;
                if (dist > 9 && dist < bd) { bd = dist; best = s; }
            }
            if (best != null) return best;
        }
        for (int[] s : d.spots) if (s.length > 3 && s[3] == 1) return s;
        return d.spots.get(0);
    }
}
