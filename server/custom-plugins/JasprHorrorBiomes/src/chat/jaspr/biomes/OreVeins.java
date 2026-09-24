package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Ore in veins, the way the game itself does it.
 *
 * The generator this replaces rolled an independent probability at every block,
 * which is exactly why the world was full of lone ore blocks: independent rolls
 * cannot correlate, so two ore blocks touching was only ever a coincidence.
 * Here each vein is a swept ellipsoid along a short random axis, so the blocks
 * are placed as one object and come out of the wall as a seam.
 *
 * Veins are generated for the nine chunks around the one being built and clipped
 * to it, so a seam that straddles a border is written identically from both
 * sides. Nothing is read from the world and no neighbouring chunk is loaded.
 */
public final class OreVeins {
    private OreVeins() { }

    private static final int STONE = 1;

    /**
     * id, data, veins per chunk, blocks per vein, lowest y, highest y.
     *
     * Vanilla 1.12.2 runs one diamond vein of eight blocks per chunk, and this
     * table matched that exactly, which measured 3.92 diamond ore per chunk in
     * the live world against vanilla's ~3.7. Diamond was the only ore still on
     * vanilla numbers while iron sat at 2.6x and lapis at 8.4x, so diamond read
     * as rare next to everything around it rather than rare on its own terms.
     * Two veins puts it at roughly 2x vanilla, in line with iron.
     */
    private static final int[][] TABLE = {
        // Stone variants first: they break up the grey and make a seam read as a seam.
        {1,  1, 8,  33, 6,  74},   // granite
        {1,  3, 8,  33, 6,  74},   // diorite
        {1,  5, 8,  33, 6,  74},   // andesite
        {3,  0, 7,  33, 6,  74},   // dirt
        {13, 0, 7,  33, 6,  74},   // gravel
        {16, 0, 13, 18, 6,  78},   // coal
        {15, 0, 40, 10, 6,  64},   // iron
        {14, 0, 3,  9,  6,  32},   // gold
        {73, 0, 6,  8,  6,  20},   // redstone
        {21, 0, 7,  8,  10, 34},   // lapis
        {56, 0, 2,  8,  6,  16},   // diamond
        {129,0, 1,  3,  6,  34},   // emerald
    };

    /** Each region favours something, so where you dig starts to matter. */
    private static int bonus(int style, int id) {
        switch (style) {
            case Caves.WARRENS:    return id == 16 ? 6 : id == 15 ? 6 : 0;
            case Caves.CATHEDRAL:  return id == 21 ? 4 : id == 15 ? 4 : 0;
            case Caves.EMBERVEINS: return id == 14 ? 3 : id == 73 ? 5 : 0;
            case Caves.FROSTBORE:  return id == 56 ? 1 : id == 21 ? 5 : 0;
            case Caves.FUNGAL:     return id == 129 ? 2 : id == 16 ? 6 : 0;
            default: return 0;
        }
    }

    /**
     * Where a vein is allowed to write. Generation passes a window clipped to the
     * chunk being built; the retrofit passes the open world. A null sink consumes
     * the same randomness and writes nothing, which is what lets the retrofit
     * replay a chunk's ore stream and pick out one vein from the middle of it.
     */
    public interface Sink {
        int minX(); int maxX(); int minZ(); int maxZ();
        int idAt(int x, int y, int z);
        void set(int x, int y, int z, int id, byte data);
    }

    private static final class ChunkSink implements Sink {
        private final ChunkData d; private final int baseX, baseZ;
        ChunkSink(ChunkData d, int cx, int cz) { this.d = d; this.baseX = cx * 16; this.baseZ = cz * 16; }
        public int minX() { return baseX; }
        public int maxX() { return baseX + 15; }
        public int minZ() { return baseZ; }
        public int maxZ() { return baseZ + 15; }
        public int idAt(int x, int y, int z) { return d.getTypeId(x - baseX, y, z - baseZ); }
        public void set(int x, int y, int z, int id, byte data) { d.setBlock(x - baseX, y, z - baseZ, id, data); }
    }

    public static void stamp(ChunkData d, Terrain t, Caves caves, int cx, int cz) {
        Sink sink = new ChunkSink(d, cx, cz);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                replay(t, caves, cx + ox, cz + oz, sink, -1, -1);
            }
        }
    }

    /**
     * Replays one source chunk's whole ore stream in order.
     *
     * Every vein draws from one shared Random, so a vein can only be reproduced by
     * running everything before it. Passing an id and index writes just that one
     * vein and dry-runs the rest, which is exactly what retrofitting a single added
     * vein into terrain that already exists requires. Passing -1/-1 writes them all.
     */
    public static void replay(Terrain t, Caves caves, int scx, int scz, Sink sink, int onlyId, int onlyIndex) {
        int style = caves.region(scx * 16 + 8, scz * 16 + 8);
        Random r = new Random(Terrain.mix(t.seed + scx * 341873128712L + scz * 132897987541L + 0x5EEDL));
        for (int[] row : TABLE) {
            int count = row[2] + bonus(style, row[0]);
            for (int n = 0; n < count; n++) {
                int wx = scx * 16 + r.nextInt(16);
                int wz = scz * 16 + r.nextInt(16);
                int y = row[4] + r.nextInt(Math.max(1, row[5] - row[4]));
                boolean write = onlyId < 0 || (row[0] == onlyId && n == onlyIndex);
                vein(write ? sink : null, r, row[0], (byte) row[1], row[3], wx, y, wz);
            }
        }
    }

    /** How many veins of an ore a chunk gets, including its cave region's bonus. */
    public static int veinCount(Caves caves, int scx, int scz, int id, int baseCount) {
        return baseCount + bonus(caves.region(scx * 16 + 8, scz * 16 + 8), id);
    }

    /**
     * One swept ellipsoid. A null sink draws the same numbers and writes nothing:
     * the block loops read no randomness, so skipping them cannot shift the stream.
     */
    private static void vein(Sink out, Random r, int id, byte data,
                             int size, int wx, int wy, int wz) {
        float angle = r.nextFloat() * (float) Math.PI;
        double reach = size / 8.0;
        double x1 = wx + Math.sin(angle) * reach, x2 = wx - Math.sin(angle) * reach;
        double z1 = wz + Math.cos(angle) * reach, z2 = wz - Math.cos(angle) * reach;
        double y1 = wy + r.nextInt(3) - 2, y2 = wy + r.nextInt(3) - 2;
        for (int i = 0; i < size; i++) {
            double t = i / (double) size;
            double centreX = x1 + (x2 - x1) * t, centreY = y1 + (y2 - y1) * t, centreZ = z1 + (z2 - z1) * t;
            double spread = r.nextDouble() * size / 16.0;
            if (out == null) continue;
            double radius = (Math.sin(Math.PI * t) + 1.0) * spread + 1.0;
            double half = radius / 2.0;
            int lowX = (int) Math.floor(centreX - half), highX = (int) Math.floor(centreX + half);
            int lowY = (int) Math.floor(centreY - half), highY = (int) Math.floor(centreY + half);
            int lowZ = (int) Math.floor(centreZ - half), highZ = (int) Math.floor(centreZ + half);
            if (highX < out.minX() || lowX > out.maxX() || highZ < out.minZ() || lowZ > out.maxZ()) continue;
            for (int x = Math.max(lowX, out.minX()); x <= Math.min(highX, out.maxX()); x++) {
                double dx = (x + 0.5 - centreX) / half;
                if (dx * dx >= 1.0) continue;
                for (int y = Math.max(lowY, 2); y <= Math.min(highY, 250); y++) {
                    double dy = (y + 0.5 - centreY) / half;
                    if (dx * dx + dy * dy >= 1.0) continue;
                    for (int z = Math.max(lowZ, out.minZ()); z <= Math.min(highZ, out.maxZ()); z++) {
                        double dz = (z + 0.5 - centreZ) / half;
                        if (dx * dx + dy * dy + dz * dz >= 1.0) continue;
                        // Only ever replace plain stone: this is what keeps veins out of
                        // the air of a cave, out of the soil layer and out of bedrock.
                        if (out.idAt(x, y, z) != STONE) continue;
                        out.set(x, y, z, id, data);
                    }
                }
            }
        }
    }
}
