package chat.jaspr.nether;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The harmonised Nether biome map. NetherEx supplies the large 2D regions (its weighted layer chain, ~128-block cells);
 * inside NetherEx "Hell" regions BetterNether's own 3D Worley noise (100 blocks horizontal, 32 vertical) chooses one of
 * its biomes per position, so BetterNether biomes stack vertically exactly as in the mod. The client biome id stays
 * vanilla HELL everywhere; these are server-side identities only.
 */
final class Biomes {
    enum Nex {
        HELL("Hell", 10), RUTHLESS_SANDS("Ruthless Sands", 8), TORRID_WASTELAND("Torrid Wasteland", 6),
        FUNGI_FOREST("Fungi Forest", 4), ARCTIC_ABYSS("Arctic Abyss", 2);
        final String display; final int weight;
        Nex(String display, int weight) { this.display = display; this.weight = weight; }
    }

    enum Bn {
        EMPTY("Empty Nether"), GRAVEL_DESERT("Gravel Desert"), NETHER_JUNGLE("Nether Jungle"), WART_FOREST("Wart Forest"),
        GRASSLANDS("Nether Grasslands"), MUSHROOM_FOREST("Nether Mushroom Forest"),
        MUSHROOM_FOREST_EDGE("Nether Mushroom Forest Edge"), WART_FOREST_EDGE("Nether Wart Forest Edge"),
        BONE_REEF("Bone Reef"), POOR_GRASSLANDS("Poor Nether Grasslands");
        final String display;
        Bn(String display) { this.display = display; }
    }

    /** BetterNether 0.1.8.6 main list (edges and sub-biomes are derived, as in BiomeRegister). */
    private static final Bn[] MAIN = {Bn.EMPTY, Bn.GRAVEL_DESERT, Bn.NETHER_JUNGLE, Bn.WART_FOREST, Bn.GRASSLANDS, Bn.MUSHROOM_FOREST};
    static final int BIOME_COUNT = Nex.values().length + Bn.values().length - 1; // "Empty Nether" is plain Hell

    private final NexLayers.Layer layer;
    private final BnNoise.DistortedId3D noise3d, subNoise;
    private final BnNoise.Dither dither;
    private final double sizeXZ = 1.0 / 100.0, sizeY = 1.0 / 32.0, subSize = 3.0 / 100.0;
    private final Map<Long, byte[]> cache = new LinkedHashMap<Long, byte[]>(4096, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, byte[]> e) { return size() > 8192; }
    };

    Biomes(long seed) {
        int[] weights = new int[Nex.values().length];
        for (Nex n : Nex.values()) weights[n.ordinal()] = n.weight;
        layer = NexLayers.build(seed, weights);
        noise3d = new BnNoise.DistortedId3D(seed, MAIN.length);
        subNoise = new BnNoise.DistortedId3D(~seed, 256);
        dither = new BnNoise.Dither(seed);
    }

    /** NetherEx region of a column (cached per chunk). */
    synchronized Nex nex(int x, int z) {
        long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        byte[] b = cache.get(key);
        if (b == null) {
            int[] v = layer.get((x >> 4) << 4, (z >> 4) << 4, 16, 16);
            b = new byte[256];
            for (int i = 0; i < 256; i++) b[i] = (byte) v[i];
            cache.put(key, b);
        }
        return Nex.values()[b[((z & 15) << 4) | (x & 15)]];
    }

    // Direct-mapped caches of the 3D noise at 4-block resolution (the dither already jitters by up to 3 blocks).
    private static final int CACHE_BITS = 18, CACHE_MASK = (1 << CACHE_BITS) - 1;
    private final long[] rawKey = new long[1 << CACHE_BITS], subKey = new long[1 << CACHE_BITS];
    private final byte[] rawVal = new byte[1 << CACHE_BITS], subVal = new byte[1 << CACHE_BITS];
    long cacheHits, cacheMisses;

    private static long qkey(int x, int y, int z) {
        return (((long) (x >> 2) & 0x3FFFFFL) << 41) | (((long) (z >> 2) & 0x3FFFFFL) << 19) | (((long) (y >> 2) & 0x7FFFL) << 1) | 1L;
    }
    private static int slot(long k) { long h = k * 0x9E3779B97F4A7C15L; return (int) (h >>> (64 - CACHE_BITS)) & CACHE_MASK; }

    private Bn raw(int x, int y, int z) {
        long k = qkey(x, y, z);
        int s = slot(k);
        if (rawKey[s] == k) { cacheHits++; return MAIN[rawVal[s]]; }
        cacheMisses++;
        int cx = (x & ~3) + 2, cy = (y & ~3) + 2, cz = (z & ~3) + 2;
        double px = dither.x(cx, cy, cz) * sizeXZ, py = dither.y(cx, cy, cz) * sizeY, pz = dither.z(cx, cy, cz) * sizeXZ;
        int id = noise3d.id(px, py, pz);
        rawKey[s] = k; rawVal[s] = (byte) id;
        return MAIN[id];
    }

    private int sub(int x, int y, int z) {
        long k = qkey(x, y, z);
        int s = slot(k);
        if (subKey[s] == k) return subVal[s] & 255;
        int cx = (x & ~3) + 2, cy = (y & ~3) + 2, cz = (z & ~3) + 2;
        double px = dither.x(cx, cy, cz) * subSize, py = dither.y(cx, cy, cz) * subSize, pz = dither.z(cx, cy, cz) * subSize;
        int id = subNoise.id(px, py, pz) % 16;
        subKey[s] = k; subVal[s] = (byte) id;
        return id;
    }

    /** BetterNether biome at a position (only meaningful inside a Hell region); edges and sub-biomes as in the jar. */
    Bn bn(int x, int y, int z) {
        Bn b = raw(x, y, z);
        int edge = b == Bn.MUSHROOM_FOREST ? 10 : b == Bn.WART_FOREST ? 9 : 0;
        if (edge > 0) {
            if (raw(x + edge, y, z) != b || raw(x - edge, y, z) != b || raw(x, y + edge, z) != b
                || raw(x, y - edge, z) != b || raw(x, y, z + edge) != b || raw(x, y, z - edge) != b)
                return b == Bn.MUSHROOM_FOREST ? Bn.MUSHROOM_FOREST_EDGE : Bn.WART_FOREST_EDGE;
            return b;
        }
        if (b == Bn.GRASSLANDS) {
            int id = sub(x, y, z);   // two sub-biomes -> sl = 2 << 3 = 16
            if (id == 0) return Bn.BONE_REEF;
            if (id == 1) return Bn.POOR_GRASSLANDS;
        }
        return b;
    }

    /** Human-readable name and origin for /where and /jnether where. */
    String describe(int x, int y, int z) {
        Nex n = nex(x, z);
        if (n != Nex.HELL) return n.display + " (NetherEx)";
        Bn b = bn(x, y, z);
        if (b == Bn.EMPTY) return "Hell (NetherEx)";
        return b.display + " (BetterNether) in a Hell region (NetherEx)";
    }
}
