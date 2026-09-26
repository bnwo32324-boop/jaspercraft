package chat.jaspr.lostcities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.World;

/**
 * Structures other packs had already committed to before JasprLostCities first ran: the plan receipts of
 * JasprImportedWorldgen (cells-/cells2-&lt;world uid&gt;) and JasprMuseMaps (cells-&lt;world uid&gt;). Those plans were
 * fixed without knowing about cities, and their packs build them into new chunks later, so their footprints (grown
 * by one chunk) are kept out of city land exactly like chunks that already existed: the city is built around them
 * and blends into them. The set is snapshotted once, the first time the file is missing (normally the plugin's first
 * start, together with the boundary snapshot), so the city plan never changes afterwards. Receipts written later are
 * city-aware already: those packs ask CityApi.reserved before they plan.
 */
final class Committed {
    static final String FILE = "jaspr-cities-v1.committed";
    /** Unknown importer design size: the largest footprint any pack uses. */
    private static final int UNKNOWN_SIZE = 256;

    private final Set<Long> chunks = new HashSet<>();
    final int sites;
    final boolean created;

    private Committed(List<String[]> entries, boolean created) {
        this.created = created;
        int n = 0;
        for (String[] e : entries) {
            if (e.length < 6) continue;
            int x = Integer.parseInt(e[2]), z = Integer.parseInt(e[3]);
            int w = Math.max(1, Integer.parseInt(e[4])), d = Math.max(1, Integer.parseInt(e[5]));
            for (int cx = Math.floorDiv(x, 16) - 1; cx <= Math.floorDiv(x + w - 1, 16) + 1; cx++)
                for (int cz = Math.floorDiv(z, 16) - 1; cz <= Math.floorDiv(z + d - 1, 16) + 1; cz++)
                    chunks.add(CityWorld.key(cx, cz));
            n++;
        }
        this.sites = n;
    }

    boolean contains(int cx, int cz) { return chunks.contains(CityWorld.key(cx, cz)); }

    int chunkCount() { return chunks.size(); }

    static Committed open(World world, File pluginsFolder, Logger log) throws IOException {
        File file = new File(world.getWorldFolder(), FILE);
        if (file.exists()) {
            List<String[]> entries = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                entries.add(line.trim().split(" "));
            }
            return new Committed(entries, false);
        }
        List<String[]> entries = snapshot(world, pluginsFolder, log);
        StringBuilder out = new StringBuilder("# JasprLostCities: footprints other packs committed to before the first city (pack site x z width depth)\n");
        for (String[] e : entries) out.append(String.join(" ", e)).append('\n');
        File tmp = new File(world.getWorldFolder(), FILE + ".tmp");
        Files.write(tmp.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new Committed(entries, true);
    }

    private static List<String[]> snapshot(World world, File plugins, Logger log) {
        String uid = world.getUID().toString();
        List<String[]> out = new ArrayList<>();
        File importer = new File(plugins, "JasprImportedWorldgen");
        Map<String, int[]> sizes = importerSizes(new File(importer, "import-catalog.json"), log);
        for (String dir : new String[]{"cells-" + uid, "cells2-" + uid})
            for (JsonObject r : receipts(new File(importer, dir), log)) {
                if (!r.has("site")) continue;
                String site = r.get("site").getAsString();
                int[] size = sizes.get(site);
                if (size == null) size = new int[]{UNKNOWN_SIZE, UNKNOWN_SIZE};
                out.add(new String[]{"imported", site, str(r, "x"), str(r, "z"), String.valueOf(size[0]), String.valueOf(size[1])});
            }
        for (JsonObject r : receipts(new File(new File(plugins, "JasprMuseMaps"), "cells-" + uid), log)) {
            if (!r.has("site") || !r.has("dims")) continue;
            if (r.has("abandoned") && r.get("abandoned").getAsBoolean()) continue;
            JsonArray dims = r.getAsJsonArray("dims");
            out.add(new String[]{"muse", r.get("site").getAsString(), str(r, "x"), str(r, "z"),
                String.valueOf(dims.get(0).getAsInt()), String.valueOf(dims.get(2).getAsInt())});
        }
        return out;
    }

    private static String str(JsonObject r, String k) { return String.valueOf(r.get(k).getAsInt()); }

    private static List<JsonObject> receipts(File dir, Logger log) {
        List<JsonObject> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return out;
        for (File f : files) {
            try (Reader in = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                JsonElement e = new JsonParser().parse(in);
                if (e.isJsonObject()) out.add(e.getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                log.warning("LOST_CITIES_COMMITTED_UNREADABLE dir=" + dir.getName() + " file=" + f.getName() + " reason=" + e.getClass().getSimpleName());
            }
        }
        return out;
    }

    private static Map<String, int[]> importerSizes(File catalog, Logger log) {
        Map<String, int[]> out = new HashMap<>();
        if (!catalog.isFile()) return out;
        try (Reader in = Files.newBufferedReader(catalog.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(in).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("sites")) {
                JsonObject s = e.getAsJsonObject();
                if (!s.has("id") || !s.has("dimensions")) continue;
                JsonArray d = s.getAsJsonArray("dimensions");
                out.put(s.get("id").getAsString(), new int[]{d.get(0).getAsInt(), d.get(2).getAsInt()});
            }
        } catch (IOException | RuntimeException e) {
            log.warning("LOST_CITIES_COMMITTED_CATALOG_UNREADABLE reason=" + e.getClass().getSimpleName() + " (unknown sizes use " + UNKNOWN_SIZE + ")");
        }
        return out;
    }
}
