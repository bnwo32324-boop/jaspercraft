package chat.jaspr.muse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Block data of the 139 designs, read from the asset-only JasprMuseMapsPack jar (maps/<file>). Each design is one
 * SE45 file ("SE45", x, y, z, count, then count x (u32 index, u16 id, u8 meta), index = (y*Z + z)*X + x). Decoded
 * designs are cut into per-chunk tiles in the populator's (y<<8 | z<<4 | x) order and kept in a small LRU.
 */
public final class Pack {
    /** One chunk's worth of a design: sorted local indexes (y<<8|z<<4|x, y relative to the design origin). */
    public static final class Tile {
        public final int[] index; public final short[] id; public final byte[] meta;
        Tile(int n) { index = new int[n]; id = new short[n]; meta = new byte[n]; }
        public int size() { return index.length; }
    }
    public static final class Blocks {
        public final int sx, sy, sz, tilesX, tilesZ;
        final Tile[] tiles;
        /** Plan-view occupancy in 4x4 cells: lowest / highest local y with a record (Short.MAX when empty). */
        final short[] lo, hi;
        final int cellsX, cellsZ;
        Blocks(int sx, int sy, int sz) {
            this.sx = sx; this.sy = sy; this.sz = sz; tilesX = (sx + 15) >> 4; tilesZ = (sz + 15) >> 4;
            tiles = new Tile[tilesX * tilesZ];
            cellsX = (sx + 3) / 4; cellsZ = (sz + 3) / 4;
            lo = new short[cellsX * cellsZ]; hi = new short[cellsX * cellsZ];
            Arrays.fill(lo, Short.MAX_VALUE); Arrays.fill(hi, Short.MIN_VALUE);
        }
        public Tile tile(int tx, int tz) {
            if (tx < 0 || tz < 0 || tx >= tilesX || tz >= tilesZ) return null;
            return tiles[tx * tilesZ + tz];
        }
        /** Whether a record placed with origin (x, y, z) can fall in world box [bx0,bx1) x [by0,by1] x [bz0,bz1). */
        public boolean meets(int x, int y, int z, int bx0, int bz0, int bx1, int bz1, int by0, int by1) {
            int i0 = Math.max(0, Math.floorDiv(bx0 - x, 4)), i1 = Math.min(cellsX - 1, Math.floorDiv(bx1 - 1 - x, 4));
            int j0 = Math.max(0, Math.floorDiv(bz0 - z, 4)), j1 = Math.min(cellsZ - 1, Math.floorDiv(bz1 - 1 - z, 4));
            for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
                int c = i * cellsZ + j;
                if (lo[c] > hi[c]) continue;
                if (y + hi[c] >= by0 && y + lo[c] <= by1) return true;
            }
            return false;
        }
        /** Local block id at design coordinates, or -1 when the design leaves that cell to the world. */
        public int idAt(int x, int y, int z) {
            Tile t = tile(x >> 4, z >> 4);
            if (t == null || y < 0 || y >= sy) return -1;
            int k = Arrays.binarySearch(t.index, (y << 8) | ((z & 15) << 4) | (x & 15));
            return k < 0 ? -1 : t.id[k];
        }
    }

    private final JavaPlugin source;
    private final Catalog catalog;
    private final Map<String, Blocks> cache = new LinkedHashMap<String, Blocks>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Blocks> e) { return size() > 10; }
    };

    public Pack(JavaPlugin source, Catalog catalog) { this.source = source; this.catalog = catalog; }

    public synchronized Blocks blocks(Catalog.Design d) throws IOException {
        Blocks b = cache.get(d.id);
        if (b != null) return b;
        b = decode(d, read(d));
        cache.put(d.id, b);
        return b;
    }

    byte[] read(Catalog.Design d) throws IOException {
        InputStream in = source.getResource("maps/" + d.file);
        if (in == null) throw new IOException("Missing pack file " + d.file);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        try (InputStream raw = in) {
            byte[] buf = new byte[1 << 15]; int n;
            while ((n = raw.read(buf)) > 0) out.write(buf, 0, n);
        }
        byte[] bytes = out.toByteArray();
        if (!hex(sha(bytes)).equals(d.sha256)) throw new IOException("Pack drift: " + d.file);
        return bytes;
    }

    /** Verify every design's hash once (startup, off the main thread). */
    public void verifyAll() throws IOException { for (Catalog.Design d : catalog.all()) read(d); }

    private static Blocks decode(Catalog.Design d, byte[] packed) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(packed), 1 << 16))) {
            if (in.readInt() != 0x53453435) throw new IOException("SE45 magic");
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt(), count = in.readInt();
            if (sx != d.width() || sy != d.height() || sz != d.depth()) throw new IOException("SE45 dimensions: " + d.id);
            int[] idx = new int[count]; short[] ids = new short[count]; byte[] metas = new byte[count];
            int previous = -1; long volume = (long) sx * sy * sz;
            for (int i = 0; i < count; i++) {
                int index = in.readInt(), id = in.readUnsignedShort(), meta = in.readUnsignedByte();
                if (index <= previous || index >= volume || id > 255 || meta > 15 || valuable(id))
                    throw new IOException("Unsafe SE45 record in " + d.id);
                idx[i] = index; ids[i] = (short) id; metas[i] = (byte) meta; previous = index;
            }
            if (in.read() != -1) throw new IOException("Trailing SE45 data: " + d.id);
            Blocks b = new Blocks(sx, sy, sz);
            int[] counts = new int[b.tiles.length];
            for (int i = 0; i < count; i++) {
                int x = idx[i] % sx, rest = idx[i] / sx, z = rest % sz;
                counts[(x >> 4) * b.tilesZ + (z >> 4)]++;
            }
            for (int t = 0; t < counts.length; t++) if (counts[t] > 0) b.tiles[t] = new Tile(counts[t]);
            int[] fill = new int[b.tiles.length];
            // Design order is y, z, x ascending; within a tile that is exactly ascending (y<<8|z<<4|x).
            for (int i = 0; i < count; i++) {
                int x = idx[i] % sx, rest = idx[i] / sx, z = rest % sz, y = rest / sz;
                int t = (x >> 4) * b.tilesZ + (z >> 4), k = fill[t]++;
                Tile tile = b.tiles[t];
                tile.index[k] = (y << 8) | ((z & 15) << 4) | (x & 15); tile.id[k] = ids[i]; tile.meta[k] = metas[i];
                int c = (x / 4) * b.cellsZ + z / 4;
                if (y < b.lo[c]) b.lo[c] = (short) y;
                if (y > b.hi[c]) b.hi[c] = (short) y;
            }
            return b;
        }
    }

    static boolean valuable(int id) { return id == 41 || id == 42 || id == 57 || id == 133 || id == 138; }

    static byte[] sha(byte[] b) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(b); } catch (Exception e) { throw new IOException(e); }
    }
    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte v : b) s.append(Character.forDigit((v >> 4) & 15, 16)).append(Character.forDigit(v & 15, 16));
        return s.toString();
    }
}
