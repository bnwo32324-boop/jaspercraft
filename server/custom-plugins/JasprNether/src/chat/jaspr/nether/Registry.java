package chat.jaspr.nether;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Append-only record of what generation placed (structures with their boxes, urns, blue-fire spots) and the per-cell
 * city decisions, so /where, the urn summon, village upkeep and blue-fire contact work after restarts. Bounded: the
 * file is rewritten compactly when it grows past the cap.
 */
final class Registry {
    static final class Entry {
        final String type, name; final int x1, y1, z1, x2, y2, z2;
        Entry(String type, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.type = type; this.name = name; this.x1 = x1; this.y1 = y1; this.z1 = z1; this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }
        boolean contains(int x, int y, int z) { return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2; }
        int cx() { return (x1 + x2) >> 1; }
        int cz() { return (z1 + z2) >> 1; }
        String line() { return type + " " + name + " " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2; }
    }

    private static final int CAP = 60000;
    private final File file;
    private final Logger log;
    private final Map<Long, List<Entry>> byRegion = new HashMap<>();
    private final Map<Long, Boolean> cities = new HashMap<>();
    private final Map<Long, Boolean> megas = new HashMap<>();
    private final Map<String, Boolean> glms = new HashMap<>();
    private final Map<Long, Boolean> colossi = new HashMap<>();
    private int count;
    private BufferedWriter out;
    int writeFailures;

    Registry(File folder, Logger log) {
        this.log = log;
        folder.mkdirs();
        file = new File(folder, "placed.txt");
        load();
    }

    /** Freeze only the pre-density placement history once. Never regenerate or edit any chunks. */
    synchronized Registry legacySnapshot() throws IOException {
        flush();
        File folder = new File(file.getParentFile(), "legacy-layout-v1");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("cannot create legacy layout snapshot");
        File snapshot = new File(folder, "placed.txt");
        if (!snapshot.exists()) {
            java.nio.file.Path temp = new File(folder, "placed.tmp").toPath();
            if (file.exists()) java.nio.file.Files.copy(file.toPath(), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            else java.nio.file.Files.write(temp, new byte[0]);
            try { java.nio.file.Files.move(temp, snapshot.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { java.nio.file.Files.move(temp, snapshot.toPath()); }
            log.info("NETHER_GLM_LEGACY_FROZEN");
        }
        return new Registry(folder, log);
    }

    synchronized Map<Long, Boolean> megaCells() { return new HashMap<>(megas); }

    boolean structureReach(int x0, int z0, int x1, int z1) {
        for (Entry e : near((x0 + x1) / 2, (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 + 2, null))
            if (!point(e.type) && e.x2 >= x0 && e.x1 <= x1 && e.z2 >= z0 && e.z1 <= z1) return true;
        return false;
    }

    private static long key(int x, int z) { return ((long) (x >> 7) << 32) ^ ((z >> 7) & 0xffffffffL); }

    private void load() {
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(" ");
                try {
                    if (p[0].equals("citycell") && p.length == 4) {
                        cities.put(((long) Integer.parseInt(p[1]) << 32) ^ (Integer.parseInt(p[2]) & 0xffffffffL), p[3].equals("1"));
                    } else if (p[0].equals("megacell") && p.length == 4) {
                        megas.put(((long) Integer.parseInt(p[1]) << 32) ^ (Integer.parseInt(p[2]) & 0xffffffffL), p[3].equals("1"));
                    } else if (p[0].equals("colossuscell") && p.length == 4) {
                        colossi.put(((long) Integer.parseInt(p[1]) << 32) ^ (Integer.parseInt(p[2]) & 0xffffffffL), p[3].equals("1"));
                    } else if (p[0].equals("glmcell") && p.length == 5) {
                        glms.put(p[1] + " " + p[2] + " " + p[3], p[4].equals("1"));
                    } else if (p.length == 8) {
                        index(new Entry(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                            Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7])));
                    }
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException e) {
            log.warning("NETHER_REGISTRY_READ_FAILED reason=" + e.getClass().getSimpleName());
        }
    }

    private void index(Entry e) {
        for (int x = e.x1 >> 7; x <= e.x2 >> 7; x++) for (int z = e.z1 >> 7; z <= e.z2 >> 7; z++)
            byRegion.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> new ArrayList<>()).add(e);
        count++;
    }

    synchronized void add(String type, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        Entry e = new Entry(type, name, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        index(e);
        write(e.line());
        if (count > CAP) compact();
    }

    synchronized Boolean cityDecision(int cellX, int cellZ) { return cities.get(((long) cellX << 32) ^ (cellZ & 0xffffffffL)); }

    synchronized void setCityDecision(int cellX, int cellZ, boolean active) {
        cities.put(((long) cellX << 32) ^ (cellZ & 0xffffffffL), active);
        write("citycell " + cellX + " " + cellZ + (active ? " 1" : " 0"));
    }

    /** Whether a mega-structure cell's site is built (decided once, by the first chunk that reaches it). */
    synchronized Boolean megaDecision(int cellX, int cellZ) { return megas.get(((long) cellX << 32) ^ (cellZ & 0xffffffffL)); }

    synchronized void setMegaDecision(int cellX, int cellZ, boolean built) {
        megas.put(((long) cellX << 32) ^ (cellZ & 0xffffffffL), built);
        write("megacell " + cellX + " " + cellZ + (built ? " 1" : " 0"));
    }

    /** Whether a colossal structure's cell is built (decided once; see Gen.colossusBuilt). */
    synchronized Boolean colossusDecision(int cellX, int cellZ) { return colossi.get(((long) cellX << 32) ^ (cellZ & 0xffffffffL)); }

    synchronized void setColossusDecision(int cellX, int cellZ, boolean built) {
        colossi.put(((long) cellX << 32) ^ (cellZ & 0xffffffffL), built);
        write("colossuscell " + cellX + " " + cellZ + (built ? " 1" : " 0"));
    }

    /** Whether a GLM build's cell (tier L/G/C) is built (decided once, by the first chunk that reaches it). */
    synchronized Boolean glmDecision(char tier, int cellX, int cellZ) { return glms.get(tier + " " + cellX + " " + cellZ); }

    synchronized void setGlmDecision(char tier, int cellX, int cellZ, boolean built) {
        glms.put(tier + " " + cellX + " " + cellZ, built);
        write("glmcell " + tier + " " + cellX + " " + cellZ + (built ? " 1" : " 0"));
    }

    private void write(String line) {
        try {
            if (out == null) out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
            out.write(line);
            out.newLine();
        } catch (IOException e) {
            if (writeFailures++ < 3) log.warning("NETHER_REGISTRY_WRITE_FAILED reason=" + e.getClass().getSimpleName());
        }
    }

    synchronized void flush() {
        try { if (out != null) out.flush(); } catch (IOException ignored) { }
    }

    synchronized void close() {
        try { if (out != null) out.close(); } catch (IOException ignored) { }
        out = null;
    }

    /** Drops the oldest bone/blue-fire style point entries to keep the file bounded. */
    private void compact() {
        List<Entry> keep = new ArrayList<>();
        java.util.Set<Entry> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (List<Entry> l : byRegion.values()) for (Entry e : l) if (seen.add(e) && !e.type.equals("bluefire")) keep.add(e);
        byRegion.clear();
        count = 0;
        close();
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            for (Map.Entry<Long, Boolean> c : cities.entrySet())
                w.write("citycell " + (int) (c.getKey() >> 32) + " " + (int) (long) c.getKey() + (c.getValue() ? " 1" : " 0") + "\n");
            for (Map.Entry<Long, Boolean> c : megas.entrySet())
                w.write("megacell " + (int) (c.getKey() >> 32) + " " + (int) (long) c.getKey() + (c.getValue() ? " 1" : " 0") + "\n");
            for (Map.Entry<String, Boolean> c : glms.entrySet()) w.write("glmcell " + c.getKey() + (c.getValue() ? " 1" : " 0") + "\n");
            for (Map.Entry<Long, Boolean> c : colossi.entrySet())
                w.write("colossuscell " + (int) (c.getKey() >> 32) + " " + (int) (long) c.getKey() + (c.getValue() ? " 1" : " 0") + "\n");
            for (Entry e : keep) { w.write(e.line()); w.newLine(); index(e); }
        } catch (IOException e) {
            log.warning("NETHER_REGISTRY_COMPACT_FAILED reason=" + e.getClass().getSimpleName());
        }
        log.info("NETHER_REGISTRY_COMPACTED entries=" + count);
    }

    synchronized Entry at(int x, int y, int z, String type) {
        List<Entry> l = byRegion.get(key(x, z));
        if (l == null) return null;
        Entry best = null;
        for (Entry e : l) if ((type == null || e.type.equals(type)) && e.contains(x, y, z)) {
            if (best == null || volume(e) < volume(best)) best = e;
        }
        return best;
    }

    /** Point records (urns, blue fire, statues, garrisons) are not structures. */
    static boolean point(String type) {
        return type.equals("urn") || type.equals("bluefire") || type.equals("statue") || type.equals("garrison") || type.equals("font")
            || type.equals("glmspawner") || type.equals("lord") || type.equals("sprung");
    }

    /** The smallest structure box (not a point record) containing the position, for /where. */
    synchronized Entry structureAt(int x, int y, int z) {
        List<Entry> l = byRegion.get(key(x, z));
        if (l == null) return null;
        Entry best = null;
        for (Entry e : l) if (!point(e.type) && e.contains(x, y, z) && (best == null || volume(e) < volume(best))) best = e;
        return best;
    }

    private static long volume(Entry e) { return (long) (e.x2 - e.x1 + 1) * (e.y2 - e.y1 + 1) * (e.z2 - e.z1 + 1); }

    synchronized List<Entry> near(int x, int z, int radius, String type) {
        List<Entry> out = new ArrayList<>();
        java.util.Set<Entry> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int rx = (x - radius) >> 7; rx <= (x + radius) >> 7; rx++) for (int rz = (z - radius) >> 7; rz <= (z + radius) >> 7; rz++) {
            List<Entry> l = byRegion.get(((long) rx << 32) ^ (rz & 0xffffffffL));
            if (l == null) continue;
            for (Entry e : l) if ((type == null || e.type.equals(type)) && seen.add(e)
                && e.x2 >= x - radius && e.x1 <= x + radius && e.z2 >= z - radius && e.z1 <= z + radius) out.add(e);
        }
        return out;
    }

    synchronized int size() { return count; }
}
