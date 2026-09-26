package chat.jaspr.nether;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads resources/blocks.tsv, the single BetterNether/NetherEx -> vanilla emulation table, and resolves
 * (mod block id, mod meta) to a combined vanilla value (id << 4 | meta). VOID means "place nothing".
 * Plain minecraft:* names resolve through the vanilla registry. An optional plugins/JasprNether/blocks.tsv
 * overrides the bundled table (for a future client content layer); nothing writes that file automatically.
 */
final class BlockMap {
    static final int VOID = -1;

    static final class Row {
        final String source, vanilla, vmeta, role, note; final int meta;
        Row(String source, int meta, String vanilla, String vmeta, String role, String note) {
            this.source = source; this.meta = meta; this.vanilla = vanilla; this.vmeta = vmeta; this.role = role; this.note = note;
        }
    }

    private static final Map<String, List<Row>> ROWS = new HashMap<>();
    private static final Map<String, Integer> VANILLA_IDS = new HashMap<>();
    static int rows;

    private BlockMap() {}

    static synchronized void load(InputStream in) throws IOException {
        ROWS.clear();
        rows = 0;
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        int n = 0;
        while ((line = r.readLine()) != null) {
            n++;
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t");
            if (p.length < 5) throw new IOException("blocks.tsv line " + n + ": expected 5+ tab-separated columns");
            int meta = p[1].equals("*") ? -1 : Integer.parseInt(p[1]);
            Row row = new Row(p[0], meta, p[2], p[3], p[4], p.length > 5 ? p[5] : "");
            if (!row.vanilla.equals("void")) vanillaId(row.vanilla); // fail fast on unknown names
            ROWS.computeIfAbsent(row.source, k -> new ArrayList<>()).add(row);
            rows++;
        }
    }

    static int vanillaId(String name) {
        Integer id = VANILLA_IDS.get(name);
        if (id != null) return id;
        net.minecraft.server.v1_12_R1.Block b = net.minecraft.server.v1_12_R1.Block.getByName(name.replace("minecraft:", ""));
        if (b == null) throw new IllegalArgumentException("unknown vanilla block " + name);
        id = net.minecraft.server.v1_12_R1.Block.getId(b);
        if (id == 0 && !name.endsWith("air")) throw new IllegalArgumentException("unknown vanilla block " + name);
        VANILLA_IDS.put(name, id);
        return id;
    }

    /** Combined vanilla value for a mod (or minecraft:) block at a mod meta; VOID for "place nothing". */
    static int resolve(String source, int meta) {
        if (source.startsWith("minecraft:")) return (vanillaId(source) << 4) | (meta & 15);
        List<Row> list = ROWS.get(source);
        if (list == null) throw new IllegalArgumentException("blocks.tsv has no row for " + source);
        Row hit = null;
        for (Row row : list) if (row.meta == meta) { hit = row; break; }
        if (hit == null) for (Row row : list) if (row.meta == -1) { hit = row; break; }
        if (hit == null) throw new IllegalArgumentException("blocks.tsv has no row for " + source + " meta " + meta);
        if (hit.vanilla.equals("void")) return VOID;
        int vm;
        if (hit.vmeta.equals("=")) vm = meta;
        else if (hit.vmeta.startsWith("+")) vm = meta + Integer.parseInt(hit.vmeta.substring(1));
        else vm = Integer.parseInt(hit.vmeta);
        return (vanillaId(hit.vanilla) << 4) | (vm & 15);
    }

    /** Default-meta resolve for world generation constants. */
    static int get(String source) { return resolve(source, 0); }

    /** Vanilla block id alone (for id-only comparisons such as thornstalk contact). */
    static int id(String source) { int v = get(source); return v < 0 ? -1 : v >> 4; }

    static List<Row> all() {
        List<Row> out = new ArrayList<>();
        for (List<Row> l : ROWS.values()) out.addAll(l);
        return out;
    }
}
