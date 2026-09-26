package chat.jaspr.muse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Freezes each 32-chunk cell's decision (site or none) before any of its chunks is stamped, and keeps the placed
 * site's state: which tiles are built, whether the retrofit into pre-existing chunks finished, the boss, the vault.
 * One small JSON receipt per decided cell under plugins/JasprMuseMaps/cells-<worldUID>/ (atomic writes).
 */
public final class Ledger {
    public static final class Record {
        int version = 1;
        long seed;
        int cx, cz;
        public String site;
        public int x, y, z;
        int[] dims;
        /** Tiles already written ("tx,tz"). */
        public List<String> stamped = new ArrayList<>();
        /** The footprint touched chunks that existed before JasprMuseMaps (built by the retrofit job). */
        public boolean retrofit;
        public boolean retrofitDone;
        /** Retrofit gave up (a player arrived first); the new-ground tiles still build. */
        public boolean abandoned;
        public boolean bossDefeated;
        public long bossDefeatedAt;
        public boolean vaultOpened;
        transient Planner.Plan plan;
    }

    private final Path root;
    private final Catalog catalog;
    private final Map<Long, Record> cache = new HashMap<>();
    private final Gson gson = new GsonBuilder().create();

    public Ledger(Path root, Catalog catalog) throws IOException {
        this.root = root; this.catalog = catalog; Files.createDirectories(root);
    }

    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    /** The frozen record of this cell (plan may be null: the cell holds no site). */
    public synchronized Record get(long seed, int cx, int cz, Planner planner, Planner.Ground ground) throws IOException {
        long key = key(cx, cz);
        Record r = cache.get(key);
        if (r != null) return r;
        Path path = root.resolve(cx + "_" + cz + ".json");
        if (Files.exists(path)) {
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { r = gson.fromJson(in, Record.class); }
            if (r == null || r.version != 1 || r.seed != seed || r.cx != cx || r.cz != cz)
                throw new IOException("Muse cell receipt identity mismatch: " + path);
            if (r.site != null) {
                Catalog.Design d = catalog.get(r.site);
                if (d == null || r.dims == null || r.dims[0] != d.width() || r.dims[1] != d.height() || r.dims[2] != d.depth())
                    throw new IOException("Muse cell receipt names a different design: " + path);
                if (r.x % 16 != 0 || r.z % 16 != 0 || r.y < 1 || r.y + d.height() > 256
                        || Math.floorDiv(r.x, 16 * Planner.CELL_CHUNKS) != cx || Math.floorDiv(r.z, 16 * Planner.CELL_CHUNKS) != cz)
                    throw new IOException("Invalid Muse cell receipt coordinates: " + path);
                r.plan = new Planner.Plan(d, cx, cz, r.x, r.y, r.z);
            }
        } else {
            Planner.Plan plan = planner.choose(seed, cx, cz, ground);
            r = new Record();
            r.seed = seed; r.cx = cx; r.cz = cz;
            if (plan != null) {
                r.site = plan.design.id; r.x = plan.x; r.y = plan.y; r.z = plan.z;
                r.dims = plan.design.dimensions.clone();
                r.plan = plan;
            }
            write(path, r);
        }
        cache.put(key, r);
        return r;
    }

    /** Cached record without deciding (null when the cell has not been decided in this run or on disk). */
    public synchronized Record peek(long seed, int cx, int cz) {
        Record r = cache.get(key(cx, cz));
        if (r != null) return r;
        Path path = root.resolve(cx + "_" + cz + ".json");
        if (!Files.exists(path)) return null;
        try { return get(seed, cx, cz, null, null); } catch (IOException e) { return null; }
    }

    public synchronized void save(Record r) throws IOException { write(root.resolve(r.cx + "_" + r.cz + ".json"), r); }

    /** Test-server fixture only: overwrite a cell's decision with a given placement. */
    synchronized Record force(long seed, int cx, int cz, Catalog.Design d, int x, int y, int z) throws IOException {
        Record r = new Record();
        r.seed = seed; r.cx = cx; r.cz = cz; r.site = d.id; r.x = x; r.y = y; r.z = z; r.dims = d.dimensions.clone();
        r.plan = new Planner.Plan(d, cx, cz, x, y, z);
        write(root.resolve(cx + "_" + cz + ".json"), r);
        cache.put(key(cx, cz), r);
        return r;
    }

    public synchronized List<Record> placed() {
        List<Record> out = new ArrayList<>();
        for (Record r : cache.values()) if (r.plan != null) out.add(r);
        return out;
    }

    /** Load every receipt on disk into the cache (startup; a few hundred small files at most). */
    public synchronized int loadAll(long seed) throws IOException {
        int n = 0;
        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                String[] parts = name.substring(0, name.length() - 5).split("_");
                if (parts.length != 2) continue;
                get(seed, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), null, null);
                n++;
            }
        }
        return n;
    }

    private void write(Path path, Record r) throws IOException {
        byte[] bytes = (gson.toJson(r) + "\n").getBytes(StandardCharsets.UTF_8);
        Path temp = Files.createTempFile(root, "cell-", ".pending");
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) { out.write(bytes); out.flush(); out.getFD().sync(); }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
