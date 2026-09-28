package chat.jaspr.atlas;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

/**
 * A chunk drawn in memory (block ids, data and biomes), for comparing what the generator makes under two liberation
 * masks: healing turns every block that is still exactly as first generated into its liberated form.
 */
final class Drawing implements ChunkData, BiomeGrid {
    final short[] ids = new short[16 * 256 * 16];
    final byte[] data = new byte[16 * 256 * 16];
    final Biome[] biomes = new Biome[256];

    static int index(int x, int y, int z) { return (y << 8) | (z << 4) | x; }

    int id(int x, int y, int z) { return ids[index(x, y, z)]; }
    int meta(int x, int y, int z) { return data[index(x, y, z)]; }

    @Override public int getMaxHeight() { return 256; }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlock(int x, int y, int z, Material m) { setBlock(x, y, z, m.getId(), (byte) 0); }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlock(int x, int y, int z, MaterialData m) { setBlock(x, y, z, m.getItemTypeId(), m.getData()); }

    @Override
    @SuppressWarnings("deprecation")
    public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, Material m) { setRegion(x0, y0, z0, x1, y1, z1, m.getId(), 0); }

    @Override
    @SuppressWarnings("deprecation")
    public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, MaterialData m) { setRegion(x0, y0, z0, x1, y1, z1, m.getItemTypeId(), m.getData()); }

    @Override
    @SuppressWarnings("deprecation")
    public Material getType(int x, int y, int z) { return Material.getMaterial(getTypeId(x, y, z)); }

    @Override
    @SuppressWarnings("deprecation")
    public MaterialData getTypeAndData(int x, int y, int z) { return new MaterialData(getTypeId(x, y, z), getData(x, y, z)); }

    @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id) { setRegion(x0, y0, z0, x1, y1, z1, id, 0); }

    @Override
    public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id, int meta) {
        for (int x = Math.max(0, x0); x < Math.min(16, x1); x++)
            for (int y = Math.max(0, y0); y < Math.min(256, y1); y++)
                for (int z = Math.max(0, z0); z < Math.min(16, z1); z++) setBlock(x, y, z, id, (byte) meta);
    }

    @Override public void setBlock(int x, int y, int z, int id) { setBlock(x, y, z, id, (byte) 0); }

    @Override
    public void setBlock(int x, int y, int z, int id, byte meta) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return;
        int i = index(x, y, z);
        ids[i] = (short) id;
        data[i] = meta;
    }

    @Override public int getTypeId(int x, int y, int z) { return x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255 ? 0 : ids[index(x, y, z)]; }
    @Override public byte getData(int x, int y, int z) { return x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255 ? 0 : data[index(x, y, z)]; }

    @Override public Biome getBiome(int x, int z) { return biomes[(z << 4) | x]; }
    @Override public void setBiome(int x, int z, Biome b) { biomes[(z << 4) | x] = b; }
}
