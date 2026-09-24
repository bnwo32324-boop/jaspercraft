package jaspr.audit.harness;

import chat.jaspr.biomes.Dungeons;
import chat.jaspr.biomes.HorrorGenerator;
import chat.jaspr.biomes.Landmarks;
import chat.jaspr.biomes.LiminalGenerator;
import chat.jaspr.biomes.Megaliths;
import chat.jaspr.biomes.StructureCatalog;
import chat.jaspr.biomes.StructurePlanner;
import chat.jaspr.biomes.Terrain;
import chat.jaspr.biomes.WorldgenExpansion;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Chunk;

/**
 * Site discovery with the plugin's own pure placement functions (reflection for the package-private
 * ones; the harness lives in another class loader). Nothing here loads a chunk.
 *
 *   reg:k   Megaliths register: the C_CELL/C_SALT lattice, then fits / claimed / baseY exactly as
 *           Megaliths.located() tests them (located() is re-run on the result as a cross-check).
 *   dun:i   Dungeons D_* tables, both lattices (D_SALT_A, D_SALT_B != 0), through Dungeons.anchor /
 *           Dungeons.surfaceAnchor -- the functions the room builders and Dungeons.locate() use.
 *   cat:id  StructurePlanner.region / expansionRegion (admit=true: what generation builds) plus the
 *           expansion boundary, i.e. exactly WorldgenExpansion.sites().
 *   sanct:portal  HorrorGenerator.portalChunk.   fold:n  LiminalGenerator rooms (jaspr_backrooms).
 * The N nearest valid sites to (0,0) (by footprint centre) become variants a, b, ...
 */
final class Discover {
    private final long seed;
    private final Terrain t;
    private final int margin, perSite;
    private final int maxRadius;
    private final WorldgenExpansion.Boundary boundary;
    private final Logger log;
    final JsonArray notFound = new JsonArray();
    final JsonObject counts = new JsonObject();
    /** Register entries whose builder anchors differently from the register table (located()). */
    final JsonArray registerDrift = new JsonArray();
    /** k -> {kind, cell, salt, sx, sz, param, builder}: each register builder's own anchor call (from the source). */
    JsonObject builderAnchors;

    Discover(long seed, WorldgenExpansion.Boundary boundary, int margin, int perSite, int maxRadius, Logger log) {
        this.seed = seed; this.t = new Terrain(seed); this.boundary = boundary;
        this.margin = margin; this.perSite = perSite; this.maxRadius = maxRadius; this.log = log;
    }

    // ------------------------------------------------------------------ reflection helpers
    static Object field(Class<?> c, String name) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    static Method method(Class<?> c, String name, Class<?>... types) throws ReflectiveOperationException {
        Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m;
    }
    static Object get(Object o, String name) throws ReflectiveOperationException {
        Class<?> c = o.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static final String[] VARIANTS = {"a", "b", "c", "d", "e", "f"};

    private static final class Cand {
        final double dist; final JsonObject rec;
        Cand(double d, JsonObject r) { dist = d; rec = r; }
    }

    private int[] groundRange(int x0, int z0, int x1, int z1) {
        int lo = 999, hi = -999;
        for (int x = x0; x < x1; x += 4) for (int z = z0; z < z1; z += 4) {
            int h = t.sample(x, z).y; lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        int h = t.sample(x1 - 1, z1 - 1).y; lo = Math.min(lo, h); hi = Math.max(hi, h);
        return new int[]{lo, hi};
    }

    private JsonObject record(String id, String name, String category, String world, int x, int z, int floorY,
                              int sizeX, int sizeZ, int height, String mode, int[] sbox, int vy0, int vy1,
                              String builder, int[] reserved, JsonObject meta) {
        JsonObject r = new JsonObject();
        r.addProperty("id", id);
        r.addProperty("name", name);
        r.addProperty("category", category);
        r.addProperty("world", world);
        JsonObject site = new JsonObject();
        site.addProperty("x", x); site.addProperty("z", z); site.addProperty("floorY", floorY);
        site.addProperty("sizeX", sizeX); site.addProperty("sizeZ", sizeZ); site.addProperty("height", height);
        site.addProperty("mode", mode);
        r.add("site", site);
        r.add("structureBox", arr(sbox));
        int[] bbox = {sbox[0] - margin, Math.max(0, vy0), sbox[2] - margin, sbox[3] + margin, Math.min(256, vy1), sbox[5] + margin};
        r.add("bbox", arr(bbox));
        JsonObject ch = new JsonObject();
        int cx0 = Math.floorDiv(bbox[0], 16), cz0 = Math.floorDiv(bbox[2], 16);
        int cx1 = Math.floorDiv(bbox[3] - 1, 16), cz1 = Math.floorDiv(bbox[5] - 1, 16);
        ch.addProperty("x0", cx0); ch.addProperty("z0", cz0); ch.addProperty("x1", cx1); ch.addProperty("z1", cz1);
        ch.addProperty("count", (cx1 - cx0 + 1) * (cz1 - cz0 + 1));
        ch.addProperty("loadCount", (cx1 - cx0 + 3) * (cz1 - cz0 + 3));
        r.add("chunks", ch);
        if (builder != null) r.addProperty("builder", builder);
        if (reserved != null) r.add("reserved", arr(reserved));
        double cxw = (sbox[0] + sbox[3]) / 2.0, czw = (sbox[2] + sbox[5]) / 2.0;
        r.addProperty("distance", Math.round(Math.hypot(cxw, czw)));
        r.add("meta", meta == null ? new JsonObject() : meta);
        return r;
    }

    static JsonArray arr(int[] a) { JsonArray j = new JsonArray(); for (int v : a) j.add(v); return j; }

    private void take(List<JsonObject> out, List<Cand> found, String id) {
        found.sort(Comparator.comparingDouble(c -> c.dist));
        for (int i = 0; i < Math.min(perSite, found.size()); i++) {
            JsonObject r = found.get(i).rec;
            r.addProperty("variant", VARIANTS[i]);
            out.add(r);
        }
        if (found.size() < perSite) {
            JsonObject nf = new JsonObject();
            nf.addProperty("id", id); nf.addProperty("found", found.size());
            nf.addProperty("reason", "fewer than " + perSite + " valid sites within " + maxRadius + " blocks of (0,0)");
            notFound.add(nf);
        }
    }

    // ------------------------------------------------------------------ register (reg:k)
    void register(List<JsonObject> out) throws ReflectiveOperationException {
        Class<?> M = Megaliths.class;
        int[] cellA = (int[]) field(M, "C_CELL"), sxA = (int[]) field(M, "C_SX"), szA = (int[]) field(M, "C_SZ"),
                hA = (int[]) field(M, "C_HEIGHT"), modeA = (int[]) field(M, "C_MODE"), fitA = (int[]) field(M, "C_FIT");
        long[] saltA = (long[]) field(M, "C_SALT");
        String[] nameA = (String[]) field(M, "C_NAME"), kindA = (String[]) field(M, "C_KIND"),
                groupA = (String[]) field(M, "C_GROUP"), atmoA = (String[]) field(M, "C_ATMO");
        Method fits = method(M, "fits", Terrain.class, int.class, int.class, int.class);
        Method claimed = method(M, "claimed", Terrain.class, int.class, int.class, int.class, int.class, int.class);
        Method baseY = method(M, "baseY", Terrain.class, int.class, int.class, int.class);
        Method located = method(M, "located", Terrain.class, int.class, int.class, int.class);
        int n = 0;
        for (int k = 0; k < cellA.length; k++) {
            int cell = cellA[k], sx = sxA[k], sz = szA[k];
            long salt = saltA[k];
            int ax = (int) Math.floorMod(Terrain.mix(seed + salt) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(seed + salt + 17L) >>> 3, (long) cell);
            List<Cand> found = new ArrayList<>();
            int tested = 0;
            for (int r = 0; ; r++) {
                double ringMin = Math.max(0, r - 1) * (double) cell * 16 - 64;
                if (found.size() >= perSite) {
                    found.sort(Comparator.comparingDouble(c -> c.dist));
                    if (found.get(perSite - 1).dist <= ringMin) break;
                }
                if (ringMin > maxRadius) break;
                for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    int acx = ax + i * cell, acz = az + j * cell;
                    int x = acx * 16 + 1, z = acz * 16 + 1;
                    tested++;
                    if (!(Boolean) fits.invoke(null, t, k, x, z)) continue;
                    if ((Boolean) claimed.invoke(null, t, x, z, sx, sz, k)) continue;
                    int base = (Integer) baseY.invoke(null, t, k, acx, acz);
                    if (base < 0) continue;
                    int[] g = groundRange(x, z, x + sx, z + sz);
                    int[] sbox = {x, base - 10, z, x + sx, base + hA[k] + 7, z + sz};
                    int vy0 = base - 16, vy1 = Math.max(base + hA[k] + 8, g[1] + 16);
                    JsonObject meta = new JsonObject();
                    meta.addProperty("k", k); meta.addProperty("group", groupA[k]);
                    meta.addProperty("cell", cell); meta.addProperty("acx", acx); meta.addProperty("acz", acz);
                    meta.addProperty("modeCode", modeA[k]); meta.addProperty("fit", fitA[k]);
                    if (!atmoA[k].isEmpty()) meta.addProperty("weather", atmoA[k]);
                    meta.addProperty("groundMin", g[0]); meta.addProperty("groundMax", g[1]);
                    int loc = (Integer) located.invoke(null, t, x + sx / 2, base + 1, z + sz / 2);
                    meta.addProperty("locatedAtCentre", loc);
                    JsonObject rec = record("reg:" + k, nameA[k], "reg", "world", x, z, base, sx, sz, hA[k], kindA[k],
                            sbox, vy0, vy1, null, null, meta);
                    found.add(new Cand(Math.hypot(x + sx / 2.0, z + sz / 2.0), rec));
                }
            }
            List<Cand> chosen = found;
            if (builderAnchors != null && builderAnchors.has(Integer.toString(k))) {
                JsonObject ba = builderAnchors.getAsJsonObject(Integer.toString(k));
                List<Cand> built = builderSites(k, ba, nameA[k], kindA[k], groupA[k], hA[k], located);
                String a = positions(found), b = positions(built);
                if (a.equals(b)) {
                    for (Cand c : found) c.rec.getAsJsonObject("meta").addProperty("builderCheck", "agrees: " + ba.get("builder").getAsString());
                } else {
                    JsonObject dr = new JsonObject();
                    dr.addProperty("id", "reg:" + k);
                    dr.addProperty("name", nameA[k]);
                    dr.addProperty("builder", ba.get("builder").getAsString());
                    dr.addProperty("builderAnchor", ba.get("kind").getAsString() + " cell=" + ba.get("cell") + " salt=" + ba.get("salt")
                            + " size=" + ba.get("sx") + "x" + ba.get("sz") + " param=" + ba.get("param"));
                    dr.addProperty("registerTable", "cell=" + cell + " salt=" + salt + " size=" + sx + "x" + sz + " fit=" + fitA[k] + " mode=" + modeA[k]);
                    dr.addProperty("locatedSites", a);
                    dr.addProperty("builderSites", b);
                    dr.addProperty("effect", "located() (used by /where, loot restock, discovery, containment) names sites that are never "
                            + "built and does not recognise the built ones; the capture uses the builder's own sites");
                    registerDrift.add(dr);
                    JsonArray pred = new JsonArray();
                    for (int i = 0; i < Math.min(perSite, found.size()); i++) {
                        JsonObject si = found.get(i).rec.getAsJsonObject("site");
                        pred.add(Discover.arr(new int[]{si.get("x").getAsInt(), si.get("z").getAsInt(), si.get("floorY").getAsInt()}));
                    }
                    for (Cand c : built) {
                        c.rec.getAsJsonObject("meta").add("locatedPrediction", pred);
                        c.rec.getAsJsonObject("meta").addProperty("registerDrift", dr.get("builderAnchor").getAsString()
                                + " vs register " + dr.get("registerTable").getAsString());
                    }
                    chosen = built;
                }
            }
            take(out, chosen, "reg:" + k);
            n += Math.min(perSite, chosen.size());
            log.info("CAPTURE_DISCOVER reg:" + k + " " + nameA[k] + " found=" + chosen.size() + " tested=" + tested
                    + (chosen == found ? "" : " REGISTER_DRIFT"));
        }
        counts.addProperty("reg", n);
    }

    private String positions(List<Cand> l) {
        l.sort(Comparator.comparingDouble(c -> c.dist));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(perSite, l.size()); i++) {
            JsonObject si = l.get(i).rec.getAsJsonObject("site");
            b.append(si.get("x")).append(',').append(si.get("z")).append(',').append(si.get("floorY"))
             .append(' ').append(si.get("sizeX")).append('x').append(si.get("sizeZ")).append(';');
        }
        return b.toString();
    }

    static Chunk fakeChunk(int cx, int cz) {
        return (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "getX": return cx;
                case "getZ": return cz;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                case "toString": return "fakeChunk(" + cx + "," + cz + ")";
                default: throw new UnsupportedOperationException("fake chunk: " + m.getName());
            }
        });
    }

    /** The N nearest sites where the builder itself anchors (its own ground/deep/sunken/site call). */
    private List<Cand> builderSites(int k, JsonObject ba, String name, String kind, String group, int height, Method located)
            throws ReflectiveOperationException {
        String fn = ba.get("kind").getAsString();
        int cell = ba.get("cell").getAsInt(), sx = ba.get("sx").getAsInt(), sz = ba.get("sz").getAsInt(), param = ba.get("param").getAsInt();
        long salt = ba.get("salt").getAsLong();
        Class<?> owner = fn.equals("site") ? Landmarks.class : Megaliths.class;
        Method anchor = method(owner, fn, Terrain.class, Chunk.class, int.class, long.class, int.class, int.class, int.class, int.class);
        int ax = (int) Math.floorMod(Terrain.mix(seed + salt) >>> 3, (long) cell);
        int az = (int) Math.floorMod(Terrain.mix(seed + salt + 17L) >>> 3, (long) cell);
        List<Cand> found = new ArrayList<>();
        for (int r = 0; ; r++) {
            double ringMin = Math.max(0, r - 1) * (double) cell * 16 - 64;
            if (found.size() >= perSite) {
                found.sort(Comparator.comparingDouble(c -> c.dist));
                if (found.get(perSite - 1).dist <= ringMin) break;
            }
            if (ringMin > maxRadius) break;
            for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) {
                if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                int acx = ax + i * cell, acz = az + j * cell;
                Object site = anchor.invoke(null, t, fakeChunk(acx, acz), cell, salt, sx, sz, param, k);
                if (site == null) continue;
                int x = (Integer) get(site, "x"), y = (Integer) get(site, "y"), z = (Integer) get(site, "z");
                if (x != acx * 16 + 1 || z != acz * 16 + 1) continue;
                int[] g = groundRange(x, z, x + sx, z + sz);
                int[] sbox = {x, y - 10, z, x + sx, y + height + 7, z + sz};
                JsonObject meta = new JsonObject();
                meta.addProperty("k", k);
                meta.addProperty("group", group);
                meta.addProperty("cell", cell);
                meta.addProperty("acx", acx);
                meta.addProperty("acz", acz);
                meta.addProperty("anchoredBy", ba.get("builder").getAsString() + " (" + fn + ")");
                meta.addProperty("groundMin", g[0]);
                meta.addProperty("groundMax", g[1]);
                meta.addProperty("locatedAtCentre", (Integer) located.invoke(null, t, x + sx / 2, y + 1, z + sz / 2));
                JsonObject rec = record("reg:" + k, name, "reg", "world", x, z, y, sx, sz, height, kind, sbox,
                        y - 16, Math.max(y + height + 8, g[1] + 16), null, null, meta);
                found.add(new Cand(Math.hypot(x + sx / 2.0, z + sz / 2.0), rec));
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ dungeon rooms (dun:i)
    void dungeons(List<JsonObject> out) throws ReflectiveOperationException {
        Class<?> D = Dungeons.class;
        String[] name = (String[]) field(D, "D_NAME"), meth = (String[]) field(D, "D_METHOD");
        int[] cellA = (int[]) field(D, "D_CELL"), sxA = (int[]) field(D, "D_SX"), szA = (int[]) field(D, "D_SZ"),
                syA = (int[]) field(D, "D_SY");
        long[] saltA = (long[]) field(D, "D_SALT_A"), saltB = (long[]) field(D, "D_SALT_B");
        boolean[] surf = (boolean[]) field(D, "D_SURFACE");
        Method anchor = method(D, "anchor", Terrain.class, int.class, int.class, int.class, long.class, int.class, int.class);
        Method surface = method(D, "surfaceAnchor", Terrain.class, int.class, int.class, int.class, long.class, int.class, int.class);
        int n = 0;
        for (int i = 0; i < name.length; i++) {
            List<Cand> found = new ArrayList<>();
            int cell = cellA[i], sx = sxA[i], sz = szA[i];
            for (int lat = 0; lat < 2; lat++) {
                long salt = lat == 0 ? saltA[i] : saltB[i];
                if (salt == 0L) continue;
                int mx = (int) Math.floorMod(Terrain.mix(seed + salt) >>> 3, (long) cell);
                int mz = (int) Math.floorMod(Terrain.mix(seed + salt + 17L) >>> 3, (long) cell);
                List<Cand> mine = new ArrayList<>();
                for (int r = 0; ; r++) {
                    double ringMin = Math.max(0, r - 1) * (double) cell * 16 - 32;
                    if (mine.size() >= perSite) {
                        mine.sort(Comparator.comparingDouble(c -> c.dist));
                        if (mine.get(perSite - 1).dist <= ringMin) break;
                    }
                    if (ringMin > maxRadius) break;
                    for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != r) continue;
                        int acx = mx + a * cell, acz = mz + b * cell;
                        Object an = (surf[i] ? surface : anchor).invoke(null, t, acx, acz, cell, salt, sx, sz);
                        if (an == null) continue;
                        int x = (Integer) get(an, "x"), y = (Integer) get(an, "y"), z = (Integer) get(an, "z");
                        if (x != acx * 16 + 2 || z != acz * 16 + 2) continue;   // a different lattice cell answered
                        int[] g = groundRange(x, z, x + sx, z + sz);
                        int[] sbox = {x, y - 2, z, x + sx, y + syA[i] + 3, z + sz};
                        int vy0 = y - 10, vy1 = Math.max(y + syA[i] + 8, g[1] + 12);
                        JsonObject meta = new JsonObject();
                        meta.addProperty("method", meth[i]); meta.addProperty("lattice", lat == 0 ? "A" : "B");
                        meta.addProperty("cell", cell); meta.addProperty("acx", acx); meta.addProperty("acz", acz);
                        meta.addProperty("anchorY", y); meta.addProperty("groundMin", g[0]); meta.addProperty("groundMax", g[1]);
                        String locate = Dungeons.locate(t, x + sx / 2, y + 1, z + sz / 2);
                        meta.addProperty("locateAtCentre", locate == null ? "" : locate.split("\u0000")[0]);
                        JsonObject rec = record("dun:" + i, name[i], "dun", "world", x, z, y, sx, sz, syA[i],
                                surf[i] ? "surface" : "buried", sbox, vy0, vy1, "Dungeons." + meth[i], null, meta);
                        mine.add(new Cand(Math.hypot(x + sx / 2.0, z + sz / 2.0), rec));
                    }
                }
                found.addAll(mine);
            }
            take(out, found, "dun:" + i);
            n += Math.min(perSite, found.size());
        }
        counts.addProperty("dun", n);
        JsonObject nf = new JsonObject();
        nf.addProperty("id", "dun:14");
        nf.addProperty("found", 0);
        nf.addProperty("reason", "vanilla-style spawner room (Dungeons.plain, 26 attempts per chunk): placement tests the "
                + "chunk's actual blocks, so it is not a pure function of seed + position and cannot be discovered here");
        notFound.add(nf);
    }

    // ------------------------------------------------------------------ catalogue (cat:id)
    void catalogue(List<JsonObject> out) throws ReflectiveOperationException {
        List<StructureCatalog.Design> designs = StructureCatalog.ALL;
        Map<String, List<Cand>> byDesign = new HashMap<>();
        for (StructureCatalog.Design d : designs) byDesign.put(d.id, new ArrayList<>());
        Method floors = method(StructureCatalog.class, "floors", char.class);
        // regions of both grids, nearest first (a site's footprint centre is its anchor)
        List<long[]> regions = new ArrayList<>();   // {grid(0 legacy,1 expansion), rx, rz, centreDist}
        int lr = maxRadius / StructurePlanner.REGION + 2, er = maxRadius / StructurePlanner.EXPANSION_REGION + 2;
        for (int rx = -lr; rx <= lr; rx++) for (int rz = -lr; rz <= lr; rz++) {
            double d = Math.hypot(rx * 1024.0 + 512, rz * 1024.0 + 512);
            if (d - 800 <= maxRadius) regions.add(new long[]{0, rx, rz, (long) d});
        }
        for (int rx = -er; rx <= er; rx++) for (int rz = -er; rz <= er; rz++) {
            double d = Math.hypot(rx * 384.0 + 192, rz * 384.0 + 192);
            if (d - 300 <= maxRadius) regions.add(new long[]{1, rx, rz, (long) d});
        }
        regions.sort(Comparator.comparingLong(a -> a[3]));
        int[] perBiome = new int[62];
        int processed = 0, sites = 0, legacySites = 0, rejectedByBoundary = 0;
        double reached = 0;
        for (long[] reg : regions) {
            // any region from here on is at least this far out (legacy anchors sit up to ~181 blocks off-centre)
            double lower = reg[3] - 750;
            boolean done = (processed & 255) == 0;
            if (done) for (List<Cand> l : byDesign.values()) {
                if (l.size() < perSite) { done = false; break; }
                l.sort(Comparator.comparingDouble(c -> c.dist));
                if (l.get(perSite - 1).dist > lower) { done = false; break; }
            }
            if (done) break;
            processed++;
            reached = reg[3];
            StructurePlanner.Site s = reg[0] == 0 ? StructurePlanner.region(seed, (int) reg[1], (int) reg[2])
                    : StructurePlanner.expansionRegion(seed, (int) reg[1], (int) reg[2]);
            if (s == null) continue;
            if (!boundary.permits(s)) { rejectedByBoundary++; continue; }
            sites++;
            if (reg[0] == 0) legacySites++;
            perBiome[t.sample(s.anchorX, s.anchorZ).profile.index]++;
            List<Cand> l = byDesign.get(s.design.id);
            double dist = Math.hypot(s.x + s.width / 2.0, s.z + s.depth / 2.0);
            if (l.size() >= perSite) {
                l.sort(Comparator.comparingDouble(c -> c.dist));
                if (l.get(perSite - 1).dist <= dist) continue;
            }
            // room floors, from the planner's own plan
            @SuppressWarnings("unchecked") List<Object> rooms = (List<Object>) get(s, "rooms");
            int minFloor = 999, maxTop = -999;
            for (Object room : rooms) {
                int f = (Integer) get(room, "floor");
                char type = (Character) get(room, "type");
                int storeys = (Integer) floors.invoke(null, type);
                minFloor = Math.min(minFloor, f);
                maxTop = Math.max(maxTop, f + storeys * 6 + 8);
            }
            int[] g = groundRange(s.x, s.z, s.x + s.width, s.z + s.depth);
            int y0 = Math.min(minFloor, g[0]) - 6, y1 = Math.max(maxTop, g[1] + 2);
            int[] sbox = {s.x, y0, s.z, s.x + s.width, y1, s.z + s.depth};
            JsonObject meta = new JsonObject();
            meta.addProperty("key", s.key);
            meta.addProperty("grid", reg[0] == 0 ? "legacy-1024" : "expansion-384");
            meta.addProperty("rx", reg[1]); meta.addProperty("rz", reg[2]);
            meta.addProperty("anchorX", s.anchorX); meta.addProperty("anchorZ", s.anchorZ);
            meta.addProperty("family", s.design.family); meta.addProperty("tier", s.design.tier);
            meta.addProperty("exclusive", s.design.exclusive);
            meta.addProperty("columns", s.design.columns); meta.addProperty("rows", s.design.rows);
            meta.addProperty("rooms", rooms.size());
            meta.addProperty("entranceY", s.y); meta.addProperty("minFloor", minFloor); meta.addProperty("maxTop", maxTop);
            meta.addProperty("groundMin", g[0]); meta.addProperty("groundMax", g[1]);
            meta.addProperty("markers", s.markers().size());
            meta.addProperty("anchorBiome", t.sample(s.anchorX, s.anchorZ).profile.name);
            JsonObject rec = record("cat:" + s.design.id, s.design.name, "cat", "world", s.x, s.z, s.y,
                    s.width, s.depth, maxTop - minFloor, s.design.mode, sbox, y0 - 4, y1 + 12, "catalog",
                    new int[]{s.x, s.z, s.x + s.width, s.z + s.depth}, meta);
            l.add(new Cand(dist, rec));
        }
        int n = 0;
        for (StructureCatalog.Design d : designs) {
            List<Cand> l = byDesign.get(d.id);
            int before = notFound.size();
            take(out, l, "cat:" + d.id);
            n += Math.min(perSite, l.size());
            if (notFound.size() > before) {
                JsonObject nf = notFound.get(notFound.size() - 1).getAsJsonObject();
                int idx = designs.indexOf(d);
                nf.addProperty("grid", idx < StructureCatalog.LEGACY_COUNT ? "legacy-1024 only (one of the frozen first 104)"
                        : "expansion-384 only");
                nf.addProperty("mode", d.mode); nf.addProperty("tier", d.tier); nf.addProperty("exclusive", d.exclusive);
                JsonArray bi = new JsonArray();
                int inBiomes = 0;
                for (int b : d.biomes) { bi.add(b); inBiomes += perBiome[b]; }
                nf.add("biomes", bi);
                nf.addProperty("admittedSitesAnchoredInItsBiomes", inBiomes);
                String why;
                if (inBiomes == 0) why = "biome-restricted: no admitted catalogue site within the radius is anchored in its biome(s)";
                else if (idx >= StructureCatalog.LEGACY_COUNT && !d.mode.equals("surface"))
                    why = "non-surface expansion design: only the 1-in-4 mixed expansion cells can hold it, weighted by tier "
                            + d.tier + (d.exclusive ? ", exclusive pool (60% of mixed cells)" : ", shared pool");
                else if (idx >= StructureCatalog.LEGACY_COUNT && d.tier < StructurePlanner.SURFACE_LANDMARK_TIER)
                    why = "surface design below tier " + StructurePlanner.SURFACE_LANDMARK_TIER
                            + ": not eligible for the 3-in-4 surface-landmark cells, only for mixed cells";
                else if (d.mode.equals("underwater")) why = "underwater: needs >= 90% of room samples below y62 in an accepted biome";
                else why = "rare by selection weight/biome frequency within the radius";
                nf.addProperty("reason", why);
            }
        }
        counts.addProperty("cat", n);
        counts.addProperty("catRegionsProcessed", processed);
        counts.addProperty("catSitesSeen", sites);
        counts.addProperty("catLegacySitesSeen", legacySites);
        counts.addProperty("catRejectedByBoundary", rejectedByBoundary);
        counts.addProperty("catSearchReached", Math.round(reached));
    }

    // ------------------------------------------------------------------ portal sanctuaries
    void sanctuaries(List<JsonObject> out) {
        List<Cand> found = new ArrayList<>();
        for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) {
            int cx = 8 + 256 * i, cz = 256 * j;
            if (!HorrorGenerator.portalChunk(cx, cz)) continue;
            int bx = cx * 16, bz = cz * 16;
            int[] g = groundRange(bx + 3, bz + 3, bx + 13, bz + 13);
            int base = Math.max(65, t.sample(bx + 8, bz + 8).y);
            int[] sbox = {bx + 3, Math.min(g[0], base) - 3, bz + 3, bx + 13, base + 7, bz + 13};
            JsonObject meta = new JsonObject();
            meta.addProperty("chunkX", cx); meta.addProperty("chunkZ", cz);
            meta.addProperty("baseEstimate", base);
            meta.addProperty("note", "floorY is estimated from the terrain function; the builder uses the carved height map");
            JsonObject rec = record("sanct:portal", "Portal Sanctuary", "sanct", "world", bx + 3, bz + 3, base, 10, 10, 7,
                    "surface", sbox, sbox[1] - 8, Math.max(base + 8, g[1] + 16), "sanctuary", new int[]{bx, bz, bx + 16, bz + 16}, meta);
            found.add(new Cand(Math.hypot(bx + 8, bz + 8), rec));
        }
        take(out, found, "sanct:portal");
        counts.addProperty("sanct", Math.min(perSite, found.size()));
    }

    // ------------------------------------------------------------------ the Fold (jaspr_backrooms)
    void fold(List<JsonObject> out) {
        for (int room = 0; room < LiminalGenerator.ROOM_COUNT; room++) {
            int cx = room % LiminalGenerator.SIDE, cz = room / LiminalGenerator.SIDE;
            int base = LiminalGenerator.floor(room), roof = base + 5 + room % 4;
            int bx = cx * 16, bz = cz * 16;
            int[] sbox = {bx + 1, base - 14, bz + 1, bx + 15, roof + 1, bz + 15};
            JsonObject meta = new JsonObject();
            meta.addProperty("room", room); meta.addProperty("roomName", LiminalGenerator.roomName(room));
            meta.addProperty("note", "seed-independent; neighbouring rooms fall inside the 8-block margin");
            JsonObject rec = record("fold:" + room, LiminalGenerator.roomName(room), "fold", "jaspr_backrooms", bx + 1, bz + 1,
                    base, 14, 14, roof - base, "buried", sbox, base - 16, roof + 4, "fold", new int[]{bx, bz, bx + 16, bz + 16}, meta);
            rec.addProperty("variant", "a");
            out.add(rec);
        }
        counts.addProperty("fold", LiminalGenerator.ROOM_COUNT);
    }
}
