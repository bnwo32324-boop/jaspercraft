package chat.jaspr.nether;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Which Nether chunks existed before this JasprNether first ran with the colossal structures and the Endless Catacombs
 * (2026-10-01). Taken once, at the first attach of this version, from the region files' location tables (plus any chunk
 * already loaded), and kept in the world's data folder, so it moves away with the old Nether when the Nether is ever
 * regenerated. Land in this set was populated without the new structures: they are never drawn into it.
 */
final class History {
    private final Map<Long, long[]> regions = new HashMap<>();   // region key -> 1024 bits, chunk (x & 31) + (z & 31) * 32
    final boolean fresh;
    final int chunks;

    private History(boolean fresh) { this.fresh = fresh; this.chunks = 0; }
    private History(Map<Long, long[]> regions, int chunks) { this.fresh = false; this.regions.putAll(regions); this.chunks = chunks; }

    /** A history with no old land at all (a new Nether, and the offline previews). */
    static History none() { return new History(true); }

    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    /** Whether chunk (cx, cz) existed before the upgrade. */
    boolean old(int cx, int cz) {
        long[] bits = regions.get(key(cx >> 5, cz >> 5));
        if (bits == null) return false;
        int i = (cx & 31) + ((cz & 31) << 5);
        return (bits[i >> 6] >>> (i & 63) & 1L) != 0;
    }

    /** Whether any chunk overlapping the block box existed before the upgrade. */
    boolean anyOld(int x0, int z0, int x1, int z1) {
        if (regions.isEmpty()) return false;
        int cx0 = Math.min(x0, x1) >> 4, cx1 = Math.max(x0, x1) >> 4, cz0 = Math.min(z0, z1) >> 4, cz1 = Math.max(z0, z1) >> 4;
        for (int rx = cx0 >> 5; rx <= cx1 >> 5; rx++) for (int rz = cz0 >> 5; rz <= cz1 >> 5; rz++) {
            long[] bits = regions.get(key(rx, rz));
            if (bits == null) continue;
            int ax = Math.max(cx0, rx << 5), bx = Math.min(cx1, (rx << 5) + 31), az = Math.max(cz0, rz << 5), bz = Math.min(cz1, (rz << 5) + 31);
            for (int cx = ax; cx <= bx; cx++) for (int cz = az; cz <= bz; cz++) {
                int i = (cx & 31) + ((cz & 31) << 5);
                if ((bits[i >> 6] >>> (i & 63) & 1L) != 0) return true;
            }
        }
        return false;
    }

    private void mark(int cx, int cz) {
        long[] bits = regions.computeIfAbsent(key(cx >> 5, cz >> 5), k -> new long[16]);
        int i = (cx & 31) + ((cz & 31) << 5);
        bits[i >> 6] |= 1L << (i & 63);
    }

    /**
     * The history of world w: read from {@code folder/colossi-history.txt} when it exists, otherwise taken now (the
     * region files' tables and the loaded chunks) and written there. A Nether without region files is fresh.
     */
    static History of(World w, File folder, Logger log) {
        File file = new File(folder, "colossi-history.txt");
        try {
            if (file.exists()) {
                History h = read(file);
                log.info("NETHER_COLOSSI_HISTORY source=file oldChunks=" + h.chunks + " fresh=" + h.fresh);
                return h;
            }
            File regionDir = new File(new File(w.getWorldFolder(), "DIM-1"), "region");
            String[] files = regionDir.isDirectory() ? regionDir.list((d, n) -> n.endsWith(".mca")) : null;
            History h = new History(new HashMap<>(), 0);
            int count = 0;
            if (files != null) for (String n : files) {
                String[] p = n.split("\\.");
                if (p.length != 4) continue;
                int rx, rz;
                try { rx = Integer.parseInt(p[1]); rz = Integer.parseInt(p[2]); } catch (NumberFormatException e) { continue; }
                byte[] table = new byte[4096];
                try (RandomAccessFile f = new RandomAccessFile(new File(regionDir, n), "r")) {
                    if (f.length() < 4096) continue;
                    f.readFully(table);
                }
                for (int i = 0; i < 1024; i++) {
                    int off = ((table[i * 4] & 255) << 16) | ((table[i * 4 + 1] & 255) << 8) | (table[i * 4 + 2] & 255);
                    if (off == 0 && table[i * 4 + 3] == 0) continue;
                    h.mark((rx << 5) + (i & 31), (rz << 5) + (i >> 5));
                    count++;
                }
            }
            for (Chunk c : w.getLoadedChunks()) if (!h.old(c.getX(), c.getZ())) { h.mark(c.getX(), c.getZ()); count++; }
            History made = count == 0 ? new History(true) : new History(h.regions, count);
            write(file, made);
            log.info("NETHER_COLOSSI_HISTORY source=scan regionFiles=" + (files == null ? 0 : files.length) + " oldChunks=" + count + " fresh=" + made.fresh);
            return made;
        } catch (IOException | RuntimeException e) {
            // Without a trustworthy history the new structures stay out of everything that exists: treat all of the
            // Nether within a generous square of the origin as old (players rarely go further before the next start).
            log.warning("NETHER_COLOSSI_HISTORY_FAILED reason=" + e.getClass().getSimpleName() + " -- the new structures keep 2048 blocks from the origin");
            History h = new History(new HashMap<>(), 0);
            for (int cx = -128; cx < 128; cx++) for (int cz = -128; cz < 128; cz++) h.mark(cx, cz);
            return new History(h.regions, 256 * 256);
        }
    }

    private static History read(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Map<Long, long[]> regions = new HashMap<>();
        int count = 0;
        boolean fresh = false;
        for (String line : lines) {
            if (line.startsWith("fresh")) fresh = true;
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("fresh")) continue;
            String[] p = line.split(" ");
            if (p.length != 3 || p[2].length() != 256) throw new IOException("bad history line");
            long[] bits = new long[16];
            for (int k = 0; k < 16; k++) bits[k] = Long.parseUnsignedLong(p[2].substring(k * 16, k * 16 + 16), 16);
            for (long b : bits) count += Long.bitCount(b);
            regions.put(key(Integer.parseInt(p[0]), Integer.parseInt(p[1])), bits);
        }
        return fresh && regions.isEmpty() ? new History(true) : new History(regions, count);
    }

    private static void write(File file, History h) throws IOException {
        file.getParentFile().mkdirs();
        List<String> out = new ArrayList<>();
        out.add("# Nether chunks that existed before the colossal structures and the Endless Catacombs (never drawn into)");
        if (h.fresh) out.add("fresh Nether at " + java.time.Instant.now());
        for (Map.Entry<Long, long[]> e : h.regions.entrySet()) {
            StringBuilder b = new StringBuilder();
            b.append((int) (e.getKey() >> 32)).append(' ').append((int) (long) e.getKey()).append(' ');
            for (long v : e.getValue()) { String s = Long.toHexString(v); for (int i = s.length(); i < 16; i++) b.append('0'); b.append(s); }
            out.add(b.toString());
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(tmp.toPath(), out, StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
