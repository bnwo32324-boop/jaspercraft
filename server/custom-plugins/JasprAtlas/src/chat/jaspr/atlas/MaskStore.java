package chat.jaspr.atlas;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The liberation mask each chunk was generated or last healed with. Population recomputes a chunk's tiles with the mask
 * its blocks were drawn with, and healing compares a chunk's blocks with that same mask's drawing. Saved with the world.
 */
final class MaskStore {
    private final ConcurrentHashMap<Long, Byte> masks = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    static long key(int cx, int cz) { return (long) cx << 32 ^ (cz & 0xffffffffL); }

    void put(int cx, int cz, int mask) { masks.put(key(cx, cz), (byte) mask); dirty = true; }

    int get(int cx, int cz, int fallback) { Byte b = masks.get(key(cx, cz)); return b == null ? fallback : b; }

    boolean known(int cx, int cz) { return masks.containsKey(key(cx, cz)); }

    int size() { return masks.size(); }

    void load(File f) throws IOException {
        masks.clear();
        if (!f.isFile()) return;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(f)))) {
            int n = in.readInt();
            if (n < 0 || n > 50_000_000) throw new IOException("bad count " + n);
            for (int i = 0; i < n; i++) { long k = in.readLong(); masks.put(k, in.readByte()); }
        }
        dirty = false;
    }

    void save(File f) throws IOException {
        if (!dirty && f.isFile()) return;
        File tmp = new File(f.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(masks.size());
            for (java.util.Map.Entry<Long, Byte> e : masks.entrySet()) { out.writeLong(e.getKey()); out.writeByte(e.getValue()); }
        }
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        dirty = false;
    }
}
