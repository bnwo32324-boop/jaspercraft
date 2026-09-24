/*
 * JasprImportedWorldgen 1.3.0 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152 decompilation of
 * the 1.2.0 CellLedger, cleaned up, with one change: the grid size and the planner call follow the lattice through
 * CellPlanner.cellChunks()/choose(..., lattice) instead of the hard-coded "lattice == 0 ? 48 : 41", so lattice 2
 * (grid 3, folder cells3-<uid>) works. Lattices 0 and 1 read and write receipts exactly as before.
 * Apply the same change to the PC source. Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;

public final class CellLedger {
    private final Path root;
    private final String catalogHash;
    private final Map<String, SiteSpec> sites = new HashMap<String, SiteSpec>();
    private final Map<Long, CellPlanner.Plan> cache = new HashMap<Long, CellPlanner.Plan>();
    private final Gson gson = new Gson();
    private final int lattice;

    public CellLedger(Path root, String catalogHash, SiteCatalog catalog) throws IOException {
        this(root, catalogHash, catalog, 0);
    }

    public CellLedger(Path root, String catalogHash, SiteCatalog catalog, int lattice) throws IOException {
        this.root = root;
        this.catalogHash = catalogHash;
        this.lattice = lattice;
        Files.createDirectories(root, new FileAttribute[0]);
        for (SiteSpec site : catalog.glm()) {
            this.sites.put(site.id, site);
        }
        for (SiteSpec site : catalog.codex()) {
            this.sites.put(site.id, site);
        }
    }

    private static long key(int cx, int cz) {
        return (long)cx << 32 ^ (long)cz & 0xFFFFFFFFL;
    }

    public CellPlanner.Plan get(long seed, int cx, int cz, CellPlanner planner, CellPlanner.Ground ground) throws IOException {
        CellPlanner.Plan plan;
        long key = CellLedger.key(cx, cz);
        if (this.cache.containsKey(key)) {
            return this.cache.get(key);
        }
        Path path = this.root.resolve(cx + "_" + cz + ".json");
        if (Files.exists(path, new LinkOption[0])) {
            Entry entry;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
                entry = this.gson.fromJson(reader, Entry.class);
            }
            if (entry == null || entry.version != 1 || entry.seed != seed || entry.cx != cx || entry.cz != cz || !this.catalogHash.equals(entry.catalog)) {
                throw new IOException("Cell receipt identity mismatch: " + path);
            }
            SiteSpec site = entry.site == null ? null : this.sites.get(entry.site);
            if (entry.site != null && site == null) {
                throw new IOException("Unknown site in cell receipt: " + path);
            }
            plan = site == null ? null : new CellPlanner.Plan(site, cx, cz, entry.x, entry.y, entry.z, this.lattice);
            int cell = CellPlanner.cellChunks(this.lattice);
            if (plan != null && (entry.x % 16 != 0 || entry.z % 16 != 0 || entry.y < 1 || entry.y + site.dimensions[1] > 256 || Math.floorDiv(entry.x, 16 * cell) != cx || Math.floorDiv(entry.z, 16 * cell) != cz)) {
                throw new IOException("Invalid cell receipt coordinates: " + path);
            }
        } else {
            plan = planner.choose(seed, cx, cz, ground, this.lattice);
            Entry entry = new Entry();
            entry.version = 1;
            entry.seed = seed;
            entry.cx = cx;
            entry.cz = cz;
            entry.catalog = this.catalogHash;
            if (plan != null) {
                entry.site = plan.site.id;
                entry.x = plan.x;
                entry.y = plan.y;
                entry.z = plan.z;
            }
            byte[] bytes = (this.gson.toJson(entry) + "\n").getBytes(StandardCharsets.UTF_8);
            Path pending = Files.createTempFile(this.root, "cell-", ".pending", new FileAttribute[0]);
            try {
                try (FileOutputStream out = new FileOutputStream(pending.toFile());){
                    out.write(bytes);
                    out.flush();
                    out.getFD().sync();
                }
                Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(pending);
            }
        }
        this.cache.put(key, plan);
        return plan;
    }

    private static final class Entry {
        int version;
        int cx;
        int cz;
        int x;
        int y;
        int z;
        long seed;
        String catalog;
        String site;

        private Entry() {
        }
    }
}
