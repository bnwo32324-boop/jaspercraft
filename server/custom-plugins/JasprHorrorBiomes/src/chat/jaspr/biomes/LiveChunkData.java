package chat.jaspr.biomes;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

/**
 * ChunkData over a live chunk (3.29.0, Tier2): the catalogue's brush draws a tier-2 site into its chunks as they
 * populate (and the retrofit into chunks that already exist), rather than into the generator's ChunkData, because a
 * tier-2 site is only drawn once decided, and deciding can mean asking the other packs. Every write goes through
 * Dungeons.set (no physics; the single-site guard of a retrofit pass applies). Chunk-local coordinates, as ChunkData.
 */
@SuppressWarnings("deprecation")
final class LiveChunkData implements ChunkGenerator.ChunkData {
    private final Chunk c;

    LiveChunkData(Chunk c) { this.c = c; }

    @Override public int getMaxHeight() { return 256; }

    @Override public void setBlock(int x, int y, int z, int id, byte data) {
        if (x < 0 || x > 15 || z < 0 || z > 15) return;
        Dungeons.set(c, x, y, z, id, data & 15);
    }
    @Override public void setBlock(int x, int y, int z, int id) { setBlock(x, y, z, id, (byte) 0); }
    @Override public void setBlock(int x, int y, int z, Material m) { setBlock(x, y, z, m.getId(), (byte) 0); }
    @Override public void setBlock(int x, int y, int z, MaterialData m) { setBlock(x, y, z, m.getItemTypeId(), m.getData()); }

    @Override public int getTypeId(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return 0;
        return c.getBlock(x, y, z).getTypeId();
    }
    @Override public byte getData(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return 0;
        return c.getBlock(x, y, z).getData();
    }
    @Override public Material getType(int x, int y, int z) { return Material.getMaterial(getTypeId(x, y, z)); }
    @Override public MaterialData getTypeAndData(int x, int y, int z) { return new MaterialData(getTypeId(x, y, z), getData(x, y, z)); }

    @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id, int data) {
        for (int x = Math.max(0, x0); x < Math.min(16, x1); x++)
            for (int y = Math.max(0, y0); y < Math.min(256, y1); y++)
                for (int z = Math.max(0, z0); z < Math.min(16, z1); z++) setBlock(x, y, z, id, (byte) data);
    }
    @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id) { setRegion(x0, y0, z0, x1, y1, z1, id, 0); }
    @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, Material m) { setRegion(x0, y0, z0, x1, y1, z1, m.getId(), 0); }
    @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, MaterialData m) { setRegion(x0, y0, z0, x1, y1, z1, m.getItemTypeId(), m.getData()); }
}
