package chat.jaspr.muse;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Muse+GLM_Maps register (pack/maps/catalog.json, built by scripts/muse-glm/build_muse_pack.py). */
public final class Catalog {
    public static final String COLLECTION = "Muse+GLM_Maps";
    public static final int EXPECTED = 139;

    public static final class Garrison { public String type, realm; public int count; }
    public static final class Signature {
        public String name, type; public double health, damage, speed; public List<String> abilities; public int count;
    }
    public static final class Hazard { public String kind, param; }
    public static final class Danger { public List<Garrison> garrison; public Signature signature; public List<Hazard> hazards; }
    public static final class Special {
        public String name, material, kind, lore; public List<List<Object>> enchants;
    }
    public static final class Boss {
        public String key, name, type, weapon; public double health, damage; public List<String> abilities;
    }
    public static final class Design {
        public String id, file, source, name, collection, habitat, theme, sha256;
        public int[] dimensions;
        public int surfaceAnchor, tier, blocks;
        public Integer waterline;
        public double weight;
        /** x, y, z, interior(1)/exterior(0) -- local design coordinates. */
        public List<int[]> spots;
        /** x, y, z, meta of the design's own chests. */
        public List<int[]> chests;
        public List<int[]> spawners;
        public int[] bossArena;
        public Danger danger;
        public Special special;
        public Boss boss;

        public int width() { return dimensions[0]; }
        public int height() { return dimensions[1]; }
        public int depth() { return dimensions[2]; }
        public int widthChunks() { return (width() + 15) >> 4; }
        public int depthChunks() { return (depth() + 15) >> 4; }
        public long area() { return (long) width() * depth(); }
    }
    private static final class Doc { int schema, count; String collection; List<Design> sites; }

    private final List<Design> designs;
    private final Map<String, Design> byId = new HashMap<>();

    public Catalog(InputStream in) throws IOException {
        Doc doc;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            doc = new Gson().fromJson(reader, Doc.class);
        }
        if (doc == null || doc.schema != 1 || !COLLECTION.equals(doc.collection) || doc.sites == null
                || doc.sites.size() != EXPECTED || doc.count != EXPECTED)
            throw new IOException("Muse+GLM_Maps catalogue identity mismatch");
        for (Design d : doc.sites) {
            if (d.id == null || !d.id.startsWith("muse:") || d.dimensions == null || d.dimensions.length != 3
                    || d.width() < 1 || d.depth() < 1 || d.height() < 1 || d.height() > 255
                    || d.width() > 256 || d.depth() > 256 || d.danger == null || d.special == null)
                throw new IOException("Invalid design entry: " + d.id);
            if (byId.put(d.id, d) != null) throw new IOException("Duplicate design: " + d.id);
        }
        designs = Collections.unmodifiableList(new ArrayList<>(doc.sites));
    }

    public List<Design> all() { return designs; }
    public Design get(String id) { return byId.get(id); }
    public int bosses() { int n = 0; for (Design d : designs) if (d.boss != null) n++; return n; }
}
