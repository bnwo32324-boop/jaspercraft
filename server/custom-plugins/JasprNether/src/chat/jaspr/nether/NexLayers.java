package chat.jaspr.nether;

/**
 * NetherEx's biome layer chain (BiomeProviderNetherEx), ported to plain Java so no NMS IntCache is shared:
 * weighted biome pick (seed 200) -> zoom x2 (1000) -> [sub-biome layer: no-op, NetherEx 2.2.5 defines no sub-biomes]
 * -> zoom x3 (1000) -> smooth (1000) -> voronoi (10). One pick cell covers 128 x 128 blocks before the fuzzy zooms.
 * Values are NexBiome ordinals.
 */
final class NexLayers {
    private static final long A = 6364136223846793005L, B = 1442695040888963407L;

    abstract static class Layer {
        long baseSeed, worldSeed, chunkSeed;
        Layer parent;
        Layer(long seed) {
            baseSeed = seed;
            for (int i = 0; i < 3; i++) { baseSeed *= baseSeed * A + B; baseSeed += seed; }
        }
        void initWorldGenSeed(long seed) {
            worldSeed = seed;
            if (parent != null) parent.initWorldGenSeed(seed);
            for (int i = 0; i < 3; i++) { worldSeed *= worldSeed * A + B; worldSeed += baseSeed; }
        }
        final void initChunkSeed(long x, long z) {
            chunkSeed = worldSeed;
            chunkSeed *= chunkSeed * A + B; chunkSeed += x;
            chunkSeed *= chunkSeed * A + B; chunkSeed += z;
            chunkSeed *= chunkSeed * A + B; chunkSeed += x;
            chunkSeed *= chunkSeed * A + B; chunkSeed += z;
        }
        final int nextInt(int n) {
            int i = (int) ((chunkSeed >> 24) % n);
            if (i < 0) i += n;
            chunkSeed *= chunkSeed * A + B;
            chunkSeed += worldSeed;
            return i;
        }
        final int pick2(int a, int b) { return nextInt(2) == 0 ? a : b; }
        final int pick4(int a, int b, int c, int d) {
            int i = nextInt(4);
            return i == 0 ? a : i == 1 ? b : i == 2 ? c : d;
        }
        final int modeOrRandom(int a, int b, int c, int d) {
            if (b == c && c == d) return b;
            if (a == b && a == c) return a;
            if (a == b && a == d) return a;
            if (a == c && a == d) return a;
            if (a == b && c != d) return a;
            if (a == c && b != d) return a;
            if (a == d && b != c) return a;
            if (b == c && a != d) return b;
            if (b == d && a != c) return b;
            if (c == d && a != b) return c;
            return pick4(a, b, c, d);
        }
        abstract int[] get(int x, int z, int w, int h);
    }

    /** GenLayerNetherBiome: every cell is an independent weighted pick (the island parent is never read). */
    static final class Pick extends Layer {
        private final int[] weights;
        private final int total;
        Pick(long seed, int[] weights) {
            super(seed);
            this.weights = weights;
            int t = 0;
            for (int w : weights) t += w;
            total = t;
        }
        @Override int[] get(int ax, int az, int w, int h) {
            int[] out = new int[w * h];
            for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
                initChunkSeed(x + ax, z + az);
                int r = nextInt(total), id = 0;
                for (int i = 0; i < weights.length; i++) { r -= weights[i]; if (r < 0) { id = i; break; } }
                out[x + z * w] = id;
            }
            return out;
        }
    }

    static final class Zoom extends Layer {
        Zoom(long seed, Layer parent) { super(seed); this.parent = parent; }
        @Override int[] get(int areaX, int areaZ, int w, int h) {
            int px = areaX >> 1, pz = areaZ >> 1, pw = (w >> 1) + 2, ph = (h >> 1) + 2;
            int[] in = parent.get(px, pz, pw, ph);
            int zw = (pw - 1) << 1, zh = (ph - 1) << 1;
            int[] tmp = new int[zw * zh];
            for (int k1 = 0; k1 < ph - 1; k1++) {
                int l1 = (k1 << 1) * zw;
                int i2 = 0;
                int j2 = in[i2 + k1 * pw];
                int k2 = in[i2 + (k1 + 1) * pw];
                for (; i2 < pw - 1; i2++) {
                    initChunkSeed((long) (i2 + px) << 1, (long) (k1 + pz) << 1);
                    int l2 = in[i2 + 1 + k1 * pw];
                    int i3 = in[i2 + 1 + (k1 + 1) * pw];
                    tmp[l1] = j2;
                    tmp[l1++ + zw] = pick2(j2, k2);
                    tmp[l1] = pick2(j2, l2);
                    tmp[l1++ + zw] = modeOrRandom(j2, l2, k2, i3);
                    j2 = l2;
                    k2 = i3;
                }
            }
            int[] out = new int[w * h];
            for (int z = 0; z < h; z++) System.arraycopy(tmp, (z + (areaZ & 1)) * zw + (areaX & 1), out, z * w, w);
            return out;
        }
        static Layer magnify(long seed, Layer layer, int times) {
            for (int i = 0; i < times; i++) layer = new Zoom(seed + i, layer);
            return layer;
        }
    }

    static final class Smooth extends Layer {
        Smooth(long seed, Layer parent) { super(seed); this.parent = parent; }
        @Override int[] get(int areaX, int areaZ, int w, int h) {
            int pw = w + 2;
            int[] in = parent.get(areaX - 1, areaZ - 1, pw, h + 2);
            int[] out = new int[w * h];
            for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
                int west = in[x + (z + 1) * pw], east = in[x + 2 + (z + 1) * pw];
                int north = in[x + 1 + z * pw], south = in[x + 1 + (z + 2) * pw];
                int c = in[x + 1 + (z + 1) * pw];
                if (west == east && north == south) {
                    initChunkSeed(x + areaX, z + areaZ);
                    c = nextInt(2) == 0 ? west : north;
                } else {
                    if (west == east) c = west;
                    if (north == south) c = north;
                }
                out[x + z * w] = c;
            }
            return out;
        }
    }

    static final class Voronoi extends Layer {
        Voronoi(long seed, Layer parent) { super(seed); this.parent = parent; }
        @Override int[] get(int areaX, int areaZ, int w, int h) {
            areaX -= 2; areaZ -= 2;
            int px = areaX >> 2, pz = areaZ >> 2, pw = (w >> 2) + 2, ph = (h >> 2) + 2;
            int[] in = parent.get(px, pz, pw, ph);
            int zw = (pw - 1) << 2, zh = (ph - 1) << 2;
            int[] tmp = new int[zw * zh];
            for (int k1 = 0; k1 < ph - 1; k1++) {
                int l1 = 0;
                int i2 = in[l1 + k1 * pw];
                int j2 = in[l1 + (k1 + 1) * pw];
                for (; l1 < pw - 1; l1++) {
                    initChunkSeed((long) (l1 + px) << 2, (long) (k1 + pz) << 2);
                    double d1 = (nextInt(1024) / 1024.0 - 0.5) * 3.6;
                    double d2 = (nextInt(1024) / 1024.0 - 0.5) * 3.6;
                    initChunkSeed((long) (l1 + px + 1) << 2, (long) (k1 + pz) << 2);
                    double d3 = (nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                    double d4 = (nextInt(1024) / 1024.0 - 0.5) * 3.6;
                    initChunkSeed((long) (l1 + px) << 2, (long) (k1 + pz + 1) << 2);
                    double d5 = (nextInt(1024) / 1024.0 - 0.5) * 3.6;
                    double d6 = (nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                    initChunkSeed((long) (l1 + px + 1) << 2, (long) (k1 + pz + 1) << 2);
                    double d7 = (nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                    double d8 = (nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                    int k2 = in[l1 + 1 + k1 * pw];
                    int l2 = in[l1 + 1 + (k1 + 1) * pw];
                    for (int i3 = 0; i3 < 4; i3++) {
                        int j3 = ((k1 << 2) + i3) * zw + (l1 << 2);
                        for (int k3 = 0; k3 < 4; k3++) {
                            double a = (i3 - d2) * (i3 - d2) + (k3 - d1) * (k3 - d1);
                            double b = (i3 - d4) * (i3 - d4) + (k3 - d3) * (k3 - d3);
                            double c = (i3 - d6) * (i3 - d6) + (k3 - d5) * (k3 - d5);
                            double d = (i3 - d8) * (i3 - d8) + (k3 - d7) * (k3 - d7);
                            if (a < b && a < c && a < d) tmp[j3++] = i2;
                            else if (b < a && b < c && b < d) tmp[j3++] = k2;
                            else if (c < a && c < b && c < d) tmp[j3++] = j2;
                            else tmp[j3++] = l2;
                        }
                    }
                    i2 = k2;
                    j2 = l2;
                }
            }
            int[] out = new int[w * h];
            for (int z = 0; z < h; z++) System.arraycopy(tmp, (z + (areaZ & 3)) * zw + (areaX & 3), out, z * w, w);
            return out;
        }
    }

    /** Builds the chain for a world seed; the returned layer yields block-resolution NexBiome ordinals. */
    static Layer build(long worldSeed, int[] weights) {
        Layer biome = new Pick(200L, weights);
        biome = Zoom.magnify(1000L, biome, 2);
        biome = Zoom.magnify(1000L, biome, 3);
        biome = new Smooth(1000L, biome);
        Layer voronoi = new Voronoi(10L, biome);
        voronoi.initWorldGenSeed(worldSeed);
        return voronoi;
    }
}
