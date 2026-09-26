package chat.jaspr.lostcities;

import chat.jaspr.biomes.Catalog;
import chat.jaspr.biomes.Terrain;
import java.util.Random;

/**
 * The mod ran the biome's genTerrainBlocks over every chunk after building it, which is what turns exposed base
 * stone (sidewalks, rubble, the tops of fills) into the biome's top and filler blocks. This is vanilla's
 * Biome.generateBiomeTerrain with the HorrorBiomes profile's own surface/under blocks as top and filler, at the
 * HorrorBiomes sea level. Bedrock is left as generated.
 */
final class Surface {
    private Surface() {}

    static final int SEA = 63;

    /** Apply to the whole primer (city chunks). */
    static void apply(CityWorld w, int chunkX, int chunkZ, char[] data, double[] depth, Random rand) {
        apply(w, chunkX, chunkZ, data, depth, rand, null, null);
    }

    /**
     * With original/out: only accept changes within the band just below the highest changed block of each column
     * (normal chunks, whose untouched terrain keeps its own surface and caves).
     */
    static void apply(CityWorld w, int chunkX, int chunkZ, char[] data, double[] depth, Random rand, char[] original, int[] changedTop) {
        Terrain t = w.terrain;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int lo = 0, hi = 255;
                if (changedTop != null) {
                    int top = changedTop[z * 16 + x];
                    if (top < 0) continue;
                    lo = Math.max(1, top - 8);
                    hi = Math.min(255, top + 1);
                }
                Catalog.Profile p = t.sample(chunkX * 16 + x, chunkZ * 16 + z).profile;
                char topBlock = B.c(p.surface, p.surfaceData);
                char filler = B.c(p.under, p.underData);
                column(data, x, z, topBlock, filler, depth[x + z * 16], rand, lo, hi);
            }
        }
    }

    private static void column(char[] data, int x, int z, char topBlock, char fillerBlock, double noise, Random rand, int lo, int hi) {
        char top = topBlock, filler = fillerBlock;
        int j = -1;
        int k = (int) (noise / 3.0D + 3.0D + rand.nextDouble() * 0.25D);
        for (int y = 255; y >= 1; --y) {
            int idx = Driver.index(x, y, z);
            char b = data[idx];
            boolean inBand = y >= lo && y <= hi;
            if (B.isAir(b)) {
                j = -1;
            } else if ((b >> 4) == 1) {   // Blocks.STONE (any variant)
                if (j == -1) {
                    if (k <= 0) {
                        top = B.AIR;
                        filler = B.STONE;
                    } else if (y >= SEA - 4 && y <= SEA + 1) {
                        top = topBlock;
                        filler = fillerBlock;
                    }
                    if (y < SEA && B.isAir(top)) top = B.WATER;
                    j = k;
                    if (y >= SEA - 1) {
                        if (inBand) data[idx] = top;
                    } else if (y < SEA - 7 - k) {
                        top = B.AIR;
                        filler = B.STONE;
                        if (inBand) data[idx] = B.GRAVEL;
                    } else {
                        if (inBand) data[idx] = filler;
                    }
                } else if (j > 0) {
                    --j;
                    if (inBand) data[idx] = filler;
                    if (j == 0 && (filler >> 4) == 12 && k > 1) {
                        j = rand.nextInt(4) + Math.max(0, y - 63);
                        filler = (filler & 15) == 1 ? B.c(179, 0) : B.c(24, 0);   // red sand -> red sandstone
                    }
                }
            }
        }
    }
}
