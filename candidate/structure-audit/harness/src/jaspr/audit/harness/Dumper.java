package jaspr.audit.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Reads the loaded world over a site's capture box and writes the JSD1 natural dump (with mask) and
 * the structure-only "iso" dump. Format: see CONVENTIONS.md (little-endian; states uint16
 * (id<<4)|data at index (y*SZ+z)*SX+x; then the uint8 mask 0 terrain / 1 structure solid / 2 carved).
 */
final class Dumper {
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Result numbers for the results file. */
    final JsonObject stats = new JsonObject();

    private final World world;
    private final Recorder rec;
    private final JsonObject site;
    private final Map<Long, ChunkSnapshot> snaps = new HashMap<>();
    private final Map<Long, Chunk> chunks = new HashMap<>();

    Dumper(World world, Recorder rec, JsonObject site) { this.world = world; this.rec = rec; this.site = site; }

    private ChunkSnapshot snap(int cx, int cz) {
        long k = Recorder.key(cx, cz);
        ChunkSnapshot s = snaps.get(k);
        if (s == null) {
            Chunk c = world.getChunkAt(cx, cz);
            chunks.put(k, c);
            s = c.getChunkSnapshot(false, false, false);
            snaps.put(k, s);
        }
        return s;
    }

    private static int[] ints(JsonElement e) {
        JsonArray a = e.getAsJsonArray(); int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
        return r;
    }

    /**
     * @return {natural file, iso file or null}
     */
    File[] dump(File outDir, String set, String seed, JsonObject capture) throws IOException {
        String id = site.get("id").getAsString(), variant = site.get("variant").getAsString();
        int[] bb = ints(site.get("bbox"));
        int x0 = bb[0], z0 = bb[2], x1 = bb[3], z1 = bb[5];
        int SX = x1 - x0, SZ = z1 - z0;
        String category = site.has("category") ? site.get("category").getAsString() : "";
        int[] res = site.has("reserved") ? ints(site.get("reserved")) : null;
        String fixed = site.has("builder") ? site.get("builder").getAsString() : null;

        // ---- which builder is this structure? fixed for dungeon rooms/catalogue/sanctuary/fold; for the
        //      register it is whichever builder wrote most blocks inside the predicted footprint.
        int[] sb = ints(site.get("structureBox"));
        Map<String, Integer> inFoot = new TreeMap<>(), inBox = new TreeMap<>();
        for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) {
            Recorder.Rec r = rec.rec(world.getName(), x >> 4, z >> 4);
            if (r == null) continue;
            boolean foot = x >= sb[0] && x < sb[3] && z >= sb[2] && z < sb[5];
            int base = ((z & 15) << 4) | (x & 15);
            for (int y = 0; y < 256; y++) {
                int i = (y << 8) | base;
                if (r.state[i] == 0) continue;
                String w = rec.name(r.who[i]);
                inBox.merge(w, 1, Integer::sum);
                if (foot) inFoot.merge(w, 1, Integer::sum);
            }
        }
        String target = fixed;
        if (target == null) {
            int best = 0;
            for (Map.Entry<String, Integer> e : inFoot.entrySet()) {
                String w = e.getKey();
                if (w.startsWith("Dungeons.") || !w.contains(".")) continue;   // rooms, catalogue, sanctuary, fold
                if (e.getValue() > best) { best = e.getValue(); target = w; }
            }
        }
        stats.addProperty("builder", target == null ? "" : target);
        stats.add("builderWritesInBox", GSON.toJsonTree(inBox));

        // ---- vertical range: the predicted range, widened to the mask and to the highest block of the box
        int maskLo = 256, maskHi = -1, top = -1;
        for (int cx = x0 >> 4; cx <= (x1 - 1) >> 4; cx++) for (int cz = z0 >> 4; cz <= (z1 - 1) >> 4; cz++) {
            ChunkSnapshot s = snap(cx, cz);
            Recorder.Rec r = rec.rec(world.getName(), cx, cz);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
                for (int y = 255; y > top; y--) if (s.getBlockTypeId(lx, y, lz) != 0) { top = y; break; }
                if (r == null || target == null) continue;
                for (int y = 0; y < 256; y++) {
                    int i = (y << 8) | (lz << 4) | lx;
                    if (r.state[i] != 0 && owns(target, rec.name(r.who[i]), res, x, z)) { maskLo = Math.min(maskLo, y); maskHi = Math.max(maskHi, y); }
                }
            }
        }
        int y0 = bb[1], y1 = bb[4];
        if (maskHi >= 0) { y0 = Math.min(y0, maskLo - 4); y1 = Math.max(maskHi + 5, top + 2); }
        else y1 = Math.max(y1, top + 2);
        y0 = Math.max(0, y0); y1 = Math.min(256, y1);
        int SY = y1 - y0;

        // ---- states + mask
        long n = (long) SX * SY * SZ;
        if (n > 150_000_000L) throw new IOException("capture box too large: " + SX + "x" + SY + "x" + SZ);
        char[] states = new char[(int) n];
        byte[] mask = new byte[(int) n];
        int solid = 0, carved = 0, idMismatch = 0, dataMismatch = 0, foreign = 0, foreignInFoot = 0;
        Map<String, Integer> foreignInFootBy = new TreeMap<>();
        int mx0 = Integer.MAX_VALUE, my0 = Integer.MAX_VALUE, mz0 = Integer.MAX_VALUE, mx1 = -1, my1 = -1, mz1 = -1;
        Map<String, Integer> foreignBy = new TreeMap<>();
        JsonArray mismatchSamples = new JsonArray();
        for (int cx = x0 >> 4; cx <= (x1 - 1) >> 4; cx++) for (int cz = z0 >> 4; cz <= (z1 - 1) >> 4; cz++) {
            ChunkSnapshot s = snap(cx, cz);
            Recorder.Rec r = rec.rec(world.getName(), cx, cz);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
                int ax = x - x0, az = z - z0;
                for (int y = y0; y < y1; y++) {
                    int bid = s.getBlockTypeId(lx, y, lz), data = s.getBlockData(lx, y, lz);
                    int at = ((y - y0) * SZ + az) * SX + ax;
                    states[at] = (char) (((bid & 0xFFF) << 4) | (data & 15));
                    if (r == null) continue;
                    int i = (y << 8) | (lz << 4) | lx;
                    int w = r.state[i];
                    if (w == 0) continue;
                    String who = rec.name(r.who[i]);
                    if (target == null || !owns(target, who, res, x, z)) {
                        foreign++;
                        foreignBy.merge(who, 1, Integer::sum);
                        if (x >= sb[0] && x < sb[3] && z >= sb[2] && z < sb[5]) { foreignInFoot++; foreignInFootBy.merge(who, 1, Integer::sum); }
                        continue;
                    }
                    int wid = (w - 1) >> 4, wdata = (w - 1) & 15;
                    mask[at] = (byte) (wid == 0 ? 2 : 1);
                    if (wid == 0) carved++; else solid++;
                    if (wid != bid || wdata != data) {
                        if (wid != bid) idMismatch++; else dataMismatch++;
                        if (mismatchSamples.size() < 64) {
                            JsonObject m = new JsonObject();
                            m.add("at", Discover.arr(new int[]{x, y, z}));
                            m.addProperty("written", wid + ":" + wdata);
                            m.addProperty("now", bid + ":" + data);
                            mismatchSamples.add(m);
                        }
                    }
                    if (wid != 0) {
                        mx0 = Math.min(mx0, ax); my0 = Math.min(my0, y - y0); mz0 = Math.min(mz0, az);
                        mx1 = Math.max(mx1, ax); my1 = Math.max(my1, y - y0); mz1 = Math.max(mz1, az);
                    }
                }
            }
        }

        // ---- tile entities in the box
        JsonArray tiles = new JsonArray();
        Map<String, Integer> tileCounts = new TreeMap<>();
        int chestItems = 0, emptyChests = 0;
        Set<Long> seenChunks = new HashSet<>(chunks.keySet());
        for (Long k : seenChunks) {
            Chunk c = chunks.get(k);
            for (BlockState st : c.getTileEntities()) {
                int x = st.getX(), y = st.getY(), z = st.getZ();
                if (x < x0 || x >= x1 || z < z0 || z >= z1 || y < y0 || y >= y1) continue;
                JsonObject tile = new JsonObject();
                tile.addProperty("x", x - x0); tile.addProperty("y", y - y0); tile.addProperty("z", z - z0);
                String kind;
                if (st instanceof Chest) {
                    kind = "chest";
                    if (st.getType() == org.bukkit.Material.TRAPPED_CHEST) tile.addProperty("type", "TRAPPED_CHEST");
                    int count = items(tile, ((Chest) st).getBlockInventory().getContents());
                    chestItems += count; if (count == 0) emptyChests++;
                } else if (st instanceof CreatureSpawner) {
                    kind = "spawner";
                    tile.addProperty("mob", String.valueOf(((CreatureSpawner) st).getSpawnedType()));
                } else if (st instanceof Sign) {
                    kind = "sign";
                    JsonArray lines = new JsonArray();
                    for (String l : ((Sign) st).getLines()) lines.add(l);
                    tile.add("lines", lines);
                } else if (st instanceof InventoryHolder) {
                    kind = "container";
                    tile.addProperty("type", st.getType().name());
                    items(tile, ((InventoryHolder) st).getInventory().getContents());
                } else {
                    kind = "tile";
                    tile.addProperty("type", st.getType().name());
                }
                tile.addProperty("kind", kind);
                int at = ((y - y0) * SZ + (z - z0)) * SX + (x - x0);
                tile.addProperty("masked", mask[at] == 1);
                tiles.add(tile);
                tileCounts.merge(kind, 1, Integer::sum);
            }
        }

        // ---- chest / spawner / sign BLOCKS that have no tile entity yet. Blocks written into ChunkData
        //      (catalogue chests) only get one when something first touches them (a player opening the chest,
        //      where StructureLoot fills it). Listed with "te": false so auditors see every container.
        Set<Long> withTe = new HashSet<>();
        for (JsonElement e : tiles) {
            JsonObject t = e.getAsJsonObject();
            withTe.add(((long) t.get("y").getAsInt() << 40) | ((long) t.get("z").getAsInt() << 20) | t.get("x").getAsInt());
        }
        int noTe = 0;
        for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) for (int x = 0; x < SX; x++) {
            int at = (y * SZ + z) * SX + x, bid = states[at] >> 4;
            String kind = bid == 54 || bid == 146 ? "chest" : bid == 52 ? "spawner" : bid == 63 || bid == 68 ? "sign" : null;
            if (kind == null || withTe.contains(((long) y << 40) | ((long) z << 20) | x)) continue;
            JsonObject tile = new JsonObject();
            tile.addProperty("x", x); tile.addProperty("y", y); tile.addProperty("z", z);
            tile.addProperty("kind", kind);
            tile.addProperty("te", false);
            if (kind.equals("chest")) tile.add("items", new JsonArray());
            if (bid == 146) tile.addProperty("type", "TRAPPED_CHEST");
            tile.addProperty("masked", mask[at] == 1);
            tiles.add(tile);
            tileCounts.merge(kind + "(no te)", 1, Integer::sum);
            noTe++;
        }

        // ---- header + natural file
        JsonObject h = new JsonObject();
        h.addProperty("format", "JSD1");
        h.addProperty("id", id); h.addProperty("variant", variant); h.addProperty("set", set);
        h.addProperty("name", site.get("name").getAsString());
        h.addProperty("context", "natural");
        h.add("origin", Discover.arr(new int[]{x0, y0, z0}));
        h.add("size", Discover.arr(new int[]{SX, SY, SZ}));
        h.add("site", site.get("site"));
        h.add("tiles", tiles);
        h.addProperty("hasMask", true);
        h.addProperty("seed", seed);
        StringBuilder notes = new StringBuilder();
        if (target == null) notes.append("EMPTY MASK: no structure writes attributable to this site in the box. ");
        if (idMismatch > 0) notes.append(idMismatch).append(" masked cells changed after the builder wrote them. ");
        h.addProperty("notes", notes.toString().trim());
        h.addProperty("category", category);
        h.addProperty("world", world.getName());
        h.addProperty("builder", target == null ? "" : target);
        h.add("structureBox", site.get("structureBox"));
        h.add("bbox", site.get("bbox"));
        if (res != null) h.add("reserved", site.get("reserved"));
        JsonObject mc = new JsonObject();
        mc.addProperty("solid", solid); mc.addProperty("air", carved);
        h.add("maskCounts", mc);
        if (mx1 >= 0) h.add("maskBox", Discover.arr(new int[]{x0 + mx0, y0 + my0, z0 + mz0, x0 + mx1 + 1, y0 + my1 + 1, z0 + mz1 + 1}));
        JsonObject mm = new JsonObject();
        mm.addProperty("id", idMismatch); mm.addProperty("dataOnly", dataMismatch);
        h.add("hookMismatch", mm);
        if (mismatchSamples.size() > 0) h.add("hookMismatchSamples", mismatchSamples);
        h.addProperty("foreignWrites", foreign);
        h.add("foreignWritesBy", GSON.toJsonTree(foreignBy));
        h.addProperty("foreignWritesInFootprint", foreignInFoot);
        h.add("foreignWritesInFootprintBy", GSON.toJsonTree(foreignInFootBy));
        h.add("builderWritesInBox", GSON.toJsonTree(inBox));
        h.add("capture", capture);
        if (site.has("meta")) h.add("meta", site.get("meta"));
        if (site.has("distance")) h.add("distance", site.get("distance"));

        String stem = id.replace(":", "__") + "__" + variant;
        File nat = new File(outDir, stem + ".jsd");
        write(nat, h, states, mask);

        stats.addProperty("size", SX + "x" + SY + "x" + SZ);
        stats.addProperty("maskSolid", solid); stats.addProperty("maskAir", carved);
        stats.addProperty("hookMismatchId", idMismatch); stats.addProperty("hookMismatchData", dataMismatch);
        stats.addProperty("foreignWrites", foreign);
        stats.addProperty("foreignWritesInFootprint", foreignInFoot);
        if (foreignInFoot > 0) stats.add("foreignWritesInFootprintBy", GSON.toJsonTree(foreignInFootBy));
        stats.add("tiles", GSON.toJsonTree(tileCounts));
        stats.addProperty("chestItemStacks", chestItems); stats.addProperty("emptyChests", emptyChests);
        if (mx1 >= 0) stats.add("maskBox", h.get("maskBox"));

        // ---- iso: mask==1 only, cropped to the tight box of mask==1
        File iso = null;
        if (mx1 >= 0) {
            int ix = mx1 - mx0 + 1, iy = my1 - my0 + 1, iz = mz1 - mz0 + 1;
            char[] is = new char[ix * iy * iz];
            for (int y = 0; y < iy; y++) for (int z = 0; z < iz; z++) for (int x = 0; x < ix; x++) {
                int from = ((y + my0) * SZ + (z + mz0)) * SX + (x + mx0);
                if (mask[from] == 1) is[(y * iz + z) * ix + x] = states[from];
            }
            JsonObject ih = copy(h);
            ih.addProperty("variant", variant + "iso");
            ih.addProperty("context", "iso");
            ih.add("origin", Discover.arr(new int[]{x0 + mx0, y0 + my0, z0 + mz0}));
            ih.add("size", Discover.arr(new int[]{ix, iy, iz}));
            JsonArray it = new JsonArray();
            for (JsonElement e : tiles) {
                JsonObject tile = e.getAsJsonObject();
                if (!tile.get("masked").getAsBoolean()) continue;
                JsonObject c = copy(tile);
                c.addProperty("x", tile.get("x").getAsInt() - mx0);
                c.addProperty("y", tile.get("y").getAsInt() - my0);
                c.addProperty("z", tile.get("z").getAsInt() - mz0);
                it.add(c);
            }
            ih.add("tiles", it);
            ih.addProperty("hasMask", false);
            ih.addProperty("notes", ("structure-only (mask==1) crop of " + nat.getName() + ". " + h.get("notes").getAsString()).trim());
            iso = new File(outDir, stem + "iso.jsd");
            write(iso, ih, is, null);
            stats.addProperty("isoSize", ix + "x" + iy + "x" + iz);
        }
        return new File[]{nat, iso};
    }

    static JsonObject copy(JsonObject o) { return GSON.fromJson(GSON.toJson(o), JsonObject.class); }

    private static boolean owns(String target, String who, int[] res, int x, int z) {
        if (!target.equals(who)) return false;
        return res == null || (x >= res[0] && x < res[2] && z >= res[1] && z < res[3]);
    }

    private static int items(JsonObject tile, ItemStack[] contents) {
        JsonArray items = new JsonArray(), names = new JsonArray();
        boolean named = false;
        for (ItemStack s : contents) {
            if (s == null || s.getTypeId() == 0) continue;
            JsonArray it = new JsonArray();
            it.add(s.getTypeId()); it.add(s.getDurability() & 0xFFFF); it.add(s.getAmount());
            items.add(it);
            String name = null;
            if (s.hasItemMeta()) { ItemMeta m = s.getItemMeta(); if (m.hasDisplayName()) name = m.getDisplayName(); }
            if (name != null) { named = true; names.add(name); } else names.add((String) null);
        }
        tile.add("items", items);
        if (named) tile.add("itemNames", names);
        return items.size();
    }

    static void write(File f, JsonObject header, char[] states, byte[] mask) throws IOException {
        File tmp = new File(f.getPath() + ".part");
        byte[] hb = GSON.toJson(header).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1 << 20)) {
            out.write(new byte[]{'J', 'S', 'D', '1'});
            int L = hb.length;
            out.write(new byte[]{(byte) L, (byte) (L >> 8), (byte) (L >> 16), (byte) (L >> 24)});
            out.write(hb);
            byte[] buf = new byte[1 << 16];
            int p = 0;
            for (char c : states) {
                buf[p++] = (byte) c; buf[p++] = (byte) (c >> 8);
                if (p == buf.length) { out.write(buf); p = 0; }
            }
            out.write(buf, 0, p);
            if (mask != null) out.write(mask);
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
