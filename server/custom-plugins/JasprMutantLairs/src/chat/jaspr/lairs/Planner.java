package chat.jaspr.lairs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where lairs may go: one per 32-chunk (512-block) cell, pure in the world seed. Each 4 x 3 block of cells holds all twelve
 * designs once, in an order of its own, so the same lair never stands in a neighbouring cell and every 2048 x 1536 blocks
 * has every lair. A cell offers its design sixteen candidate spots (and a quarter turn each); the first one the
 * Director admits on the real land is built.
 */
final class Planner {
    static final int CELL_CHUNKS = 32, CELL = CELL_CHUNKS * 16, MARGIN = 24, CANDIDATES = 16;
    private static final long SALT = 0x4D5554414E544CL; // "MUTANTL"

    static final class Candidate {
        final int index, x, z, rot, w, d;
        Candidate(int index, int x, int z, int rot, int w, int d) { this.index = index; this.x = x; this.z = z; this.rot = rot; this.w = w; this.d = d; }
        int cx() { return x + w / 2; }
        int cz() { return z + d / 2; }
    }

    static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    static String key(int i, int j) { return i + "_" + j; }

    /** The design of cell (i, j). */
    static Design design(long seed, int i, int j) {
        int bi = Math.floorDiv(i, 4), bj = Math.floorDiv(j, 3);
        int slot = Math.floorMod(i, 4) + 4 * Math.floorMod(j, 3);
        int n = Designs.ALL.size();
        int[] order = new int[n];
        for (int k = 0; k < n; k++) order[k] = k;
        Random r = new Random(mix(seed ^ SALT ^ (bi * 0x9E3779B97F4A7C15L) ^ (bj * 0xC2B2AE3D27D4EB4FL)));
        for (int k = n - 1; k > 0; k--) { int x = r.nextInt(k + 1), t = order[k]; order[k] = order[x]; order[x] = t; }
        return Designs.ALL.get(order[slot % n]);
    }

    /** The cell's candidate spots for its design, in the order they are tried. */
    static List<Candidate> candidates(long seed, int i, int j, Design d) {
        Random r = new Random(mix(seed ^ SALT ^ 0x43414E44L ^ (i * 341873128712L) ^ (j * 132897987541L)));
        List<Candidate> out = new ArrayList<>();
        for (int k = 0; k < CANDIDATES; k++) {
            int rot = r.nextInt(4);
            int w = (rot & 1) == 0 ? d.w : d.d, dd = (rot & 1) == 0 ? d.d : d.w;
            int x = i * CELL + MARGIN + r.nextInt(CELL - 2 * MARGIN - w);
            int z = j * CELL + MARGIN + r.nextInt(CELL - 2 * MARGIN - dd);
            out.add(new Candidate(k, x, z, rot, w, dd));
        }
        return out;
    }

    /** A per-site seed for the drawing (decay, scatter): the same site always looks the same. */
    static long siteSeed(long seed, int i, int j) { return mix(seed ^ SALT ^ 0x53495445L ^ (i * 0x632BE59BD9B4E019L) ^ (j * 0x8CB92BA72F3D8DD7L)); }
}
