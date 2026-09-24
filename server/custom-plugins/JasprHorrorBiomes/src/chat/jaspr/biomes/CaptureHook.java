package chat.jaspr.biomes;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

/**
 * Observation point for structure block writes (structure audit capture harness, 2026-09-22).
 *
 * Nothing in this plugin ever sets {@link #sink}. Only the out-of-tree capture harness used on
 * throwaway test servers does. Every call site reads the field once and does nothing when it is
 * null, so a production server behaves exactly as before: same blocks, same order, same random
 * draws. The harness uses the reports to tell which blocks of a generated world a structure
 * builder wrote (the "mask" of a structure dump), as opposed to terrain.
 *
 * Call sites (each reports the block id and data actually written, in world coordinates):
 *   Dungeons.set                   "populate"   every register set piece, dungeon room and
 *                                               vanilla-style spawner room (BlockPopulator)
 *   StructureArchitecture.Brush.put "catalog"   every expedition catalogue block (ChunkData)
 *   HorrorGenerator.sanctuary       "sanctuary" portal sanctuaries (ChunkData, via tap)
 *   HorrorGenerator -> BiomeDetails "detail"    biome-detail motifs, overworld (ChunkData, via tap)
 *   BiomeDetails.Native.set         "detail"    biome-detail motifs, nether/end (live chunk)
 *   LiminalGenerator                "fold"      the Fold rooms (ChunkData, via tap)
 */
public final class CaptureHook {
    private CaptureHook() { }

    /** Receives one report per block written by a structure builder. */
    public interface Sink {
        /**
         * @param world  world name, or null for a write into the ChunkData of the overworld generator
         *               (HorrorGenerator only ever generates the world named "world")
         * @param x      world block x
         * @param y      block y
         * @param z      world block z
         * @param id     1.12 numeric block id written
         * @param data   4-bit data value written
         * @param source which writer, see the class comment
         */
        void write(String world, int x, int y, int z, int id, int data, String source);
    }

    /** Null in production. Set only by the capture harness on a test server. */
    public static volatile Sink sink;

    /** Reports a write into a live chunk at chunk-local x/z. Does nothing when no sink is set. */
    static void chunk(Chunk c, int x, int y, int z, int id, int data, String source) {
        Sink s = sink;
        if (s != null) s.write(c.getWorld().getName(), c.getX() * 16 + x, y, c.getZ() * 16 + z, id, data, source);
    }

    /**
     * The ChunkData to hand to a builder that writes generation-time blocks. With no sink this is the
     * very same object, so production is unchanged. With a sink it is a forwarding view that reports
     * each block after it has been written. The view must never be returned to the server.
     */
    static ChunkGenerator.ChunkData tap(ChunkGenerator.ChunkData data, String world, int cx, int cz, String source) {
        Sink s = sink;
        return s == null ? data : new Tap(data, world, cx, cz, source, s);
    }

    private static final class Tap implements ChunkGenerator.ChunkData {
        private final ChunkGenerator.ChunkData d;
        private final String world, source;
        private final int bx, bz;
        private final Sink s;
        Tap(ChunkGenerator.ChunkData d, String world, int cx, int cz, String source, Sink s) {
            this.d = d; this.world = world; this.bx = cx * 16; this.bz = cz * 16; this.source = source; this.s = s;
        }
        private void report(int x, int y, int z) {
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= d.getMaxHeight()) return;
            s.write(world, bx + x, y, bz + z, d.getTypeId(x, y, z), d.getData(x, y, z) & 15, source);
        }
        private void report(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = Math.max(0, x0); x < Math.min(16, x1); x++)
                for (int z = Math.max(0, z0); z < Math.min(16, z1); z++)
                    for (int y = Math.max(0, y0); y < Math.min(d.getMaxHeight(), y1); y++) report(x, y, z);
        }
        @Override public int getMaxHeight() { return d.getMaxHeight(); }
        @Override public void setBlock(int x, int y, int z, Material m) { d.setBlock(x, y, z, m); report(x, y, z); }
        @Override public void setBlock(int x, int y, int z, MaterialData m) { d.setBlock(x, y, z, m); report(x, y, z); }
        @Override public void setBlock(int x, int y, int z, int id) { d.setBlock(x, y, z, id); report(x, y, z); }
        @Override public void setBlock(int x, int y, int z, int id, byte data) { d.setBlock(x, y, z, id, data); report(x, y, z); }
        @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
            d.setRegion(x0, y0, z0, x1, y1, z1, m); report(x0, y0, z0, x1, y1, z1);
        }
        @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, MaterialData m) {
            d.setRegion(x0, y0, z0, x1, y1, z1, m); report(x0, y0, z0, x1, y1, z1);
        }
        @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
            d.setRegion(x0, y0, z0, x1, y1, z1, id); report(x0, y0, z0, x1, y1, z1);
        }
        @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id, int data) {
            d.setRegion(x0, y0, z0, x1, y1, z1, id, data); report(x0, y0, z0, x1, y1, z1);
        }
        @Override public Material getType(int x, int y, int z) { return d.getType(x, y, z); }
        @Override public MaterialData getTypeAndData(int x, int y, int z) { return d.getTypeAndData(x, y, z); }
        @Override public int getTypeId(int x, int y, int z) { return d.getTypeId(x, y, z); }
        @Override public byte getData(int x, int y, int z) { return d.getData(x, y, z); }
    }
}
