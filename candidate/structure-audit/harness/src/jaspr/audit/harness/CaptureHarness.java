package jaspr.audit.harness;

import chat.jaspr.biomes.CaptureHook;
import chat.jaspr.biomes.StructureCatalog;
import chat.jaspr.biomes.WorldgenExpansion;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Structure-audit capture harness (test servers only; never on the live server).
 *
 * Job file: plugins/JasprCaptureHarness/job.json, written by SA/tools/capture.py.
 *   mode "discover": compute sites with the plugin's pure placement functions -> sitesOut (sites.json)
 *   mode "capture":  for each site: load its box + a one-chunk ring (so 1.12 populates every chunk of
 *                    the box), let the plugin's post-load passes run, dump JSD1 natural + iso, unload
 *                    everything WITHOUT saving; one results line per site; then stop the server.
 * Chunks are never saved (ChunkUnloadEvent.setSaveChunk(false), autosave off), so each capture is a
 * first generation, like a player walking into unexplored ground on the live server.
 */
public final class CaptureHarness extends JavaPlugin implements Listener {
    static final String VERSION = "1.0";
    private JsonObject job;
    private String mode = "none";
    private Recorder recorder;
    private final Set<String> populated = new HashSet<>();
    private long newChunks, loadEvents, populateEvents, unloadEvents, savesPrevented;
    private long peakHeap;
    private Writer results;
    private long startNanos;
    private int tick;
    private boolean finished;
    private String shimStatus = "off";

    // capture state
    private List<JsonObject> sites = new ArrayList<>();
    private int index = -1;
    private String phase = "wait";
    private World world;
    private List<int[]> loadList;
    private int loadPos, settleTicks, waitTicks;
    private long tSite, tLoad0, tLoad, tSettle, tDump, siteNew0;
    private long totalLoadNanos, totalNewChunks, totalSites, okSites;

    @Override public void onLoad() {
        try {
            File f = new File(getDataFolder(), "job.json");
            if (!f.isFile()) { getLogger().warning("CAPTURE_NO_JOB " + f); return; }
            job = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            mode = job.get("mode").getAsString();
            shimStatus = ApocalypseShim.install(getDataFolder().getAbsoluteFile().getParentFile().getParentFile(),
                    str("shim", "real"), getLogger());
            getLogger().info("CAPTURE_JOB mode=" + mode + " shim=" + shimStatus);
        } catch (Exception e) {
            getLogger().severe("CAPTURE_JOB_UNREADABLE " + e);
            job = null;
        }
    }

    private String str(String k, String def) { return job != null && job.has(k) && !job.get(k).isJsonNull() ? job.get(k).getAsString() : def; }
    private int num(String k, int def) { return job != null && job.has(k) ? job.get(k).getAsInt() : def; }

    @Override public void onEnable() {
        startNanos = System.nanoTime();
        if (job == null) return;
        Bukkit.getPluginManager().registerEvents(this, this);
        try {
            results = new OutputStreamWriter(new FileOutputStream(str("resultsFile", new File(getDataFolder(), "results.jsonl").getPath()), true), StandardCharsets.UTF_8);
        } catch (IOException e) { getLogger().severe("CAPTURE_RESULTS_UNWRITABLE " + e); }
        if (mode.equals("capture")) {
            recorder = new Recorder();
            for (JsonElement e : job.getAsJsonArray("sites")) {
                JsonObject s = e.getAsJsonObject();
                sites.add(s);
                JsonObject c = s.getAsJsonObject("chunks");
                recorder.target(s.has("world") ? s.get("world").getAsString() : "world",
                        c.get("x0").getAsInt() - 1, c.get("z0").getAsInt() - 1, c.get("x1").getAsInt() + 1, c.get("z1").getAsInt() + 1);
            }
            CaptureHook.sink = recorder;
            getLogger().info("CAPTURE_READY sites=" + sites.size() + " targetChunks=" + recorder.targetChunks());
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override public void onDisable() {
        CaptureHook.sink = null;
        ApocalypseShim.remove();
        try { if (results != null) results.close(); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ events
    private static String ck(Chunk c) { return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void load(ChunkLoadEvent e) { loadEvents++; if (e.isNewChunk()) newChunks++; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void populate(ChunkPopulateEvent e) { populateEvents++; populated.add(ck(e.getChunk())); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void unloadNoSave(ChunkUnloadEvent e) { if (e.isSaveChunk()) savesPrevented++; e.setSaveChunk(false); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloaded(ChunkUnloadEvent e) {
        unloadEvents++;
        populated.remove(ck(e.getChunk()));
        if (recorder != null) recorder.drop(e.getWorld().getName(), e.getChunk().getX(), e.getChunk().getZ());
    }

    // ------------------------------------------------------------------ main loop
    private void tick() {
        tick++;
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (used > peakHeap) peakHeap = used;
        if (finished) return;
        try {
            if (mode.equals("discover")) { if (tick == num("startDelayTicks", 40)) startDiscover(); return; }
            if (mode.equals("capture")) { captureTick(); return; }
            finish("unknown mode " + mode);
        } catch (Throwable t) {
            getLogger().severe("CAPTURE_FATAL " + t);
            t.printStackTrace();
            finish("fatal: " + t);
        }
    }

    private void startDiscover() {
        World w = Bukkit.getWorld("world");
        if (w == null) { finish("no overworld"); return; }
        final WorldgenExpansion.Boundary boundary = WorldgenExpansion.initialize(w);
        final long seed = w.getSeed();
        final JsonObject d = job.has("discover") ? job.getAsJsonObject("discover") : new JsonObject();
        final int margin = d.has("margin") ? d.get("margin").getAsInt() : 8;
        final int perSite = d.has("perSite") ? d.get("perSite").getAsInt() : 2;
        final int radius = d.has("maxRadius") ? d.get("maxRadius").getAsInt() : 100000;
        final String out = str("sitesOut", null);
        getLogger().info("CAPTURE_DISCOVER start seed=" + seed + " protectedChunks=" + boundary.protectedChunks());
        Thread th = new Thread(() -> {
            long t0 = System.nanoTime();
            JsonObject root = new JsonObject();
            String err = null;
            try {
                Discover disc = new Discover(seed, boundary, margin, perSite, radius, getLogger());
                if (d.has("builderAnchors")) disc.builderAnchors = d.getAsJsonObject("builderAnchors");
                List<JsonObject> recs = new ArrayList<>();
                long a = System.nanoTime(); disc.register(recs);
                long b = System.nanoTime(); disc.dungeons(recs);
                long c = System.nanoTime(); disc.catalogue(recs);
                long e = System.nanoTime(); disc.sanctuaries(recs); disc.fold(recs);
                long f = System.nanoTime();
                root.addProperty("format", "sites-v1");
                root.addProperty("generated", Instant.now().toString());
                root.addProperty("generatedBy", "SA/harness CaptureHarness " + VERSION + " discover");
                root.addProperty("seed", Long.toString(seed));
                root.addProperty("plugin", pluginVersion());
                if (job.has("jar")) root.add("jar", job.get("jar"));
                root.addProperty("margin", margin);
                root.addProperty("perSite", perSite);
                root.addProperty("maxRadius", radius);
                root.addProperty("expansionBoundaryProtectedChunks", boundary.protectedChunks());
                root.addProperty("designs", StructureCatalog.ALL.size());
                root.addProperty("disabledStructures", disabledCount());
                root.addProperty("bboxConvention", "bbox and structureBox are [x0,y0,z0,x1,y1,z1] world coords, half-open (x1,y1,z1 exclusive); "
                        + "bbox = structureBox + margin horizontally, vertical range covers the surface above buried sites; "
                        + "chunks = inclusive chunk rectangle of bbox, loadCount includes the one-chunk ring loaded so every bbox chunk populates");
                disc.counts.addProperty("total", recs.size());
                root.add("counts", disc.counts);
                JsonObject timing = new JsonObject();
                timing.addProperty("registerMs", (b - a) / 1000000); timing.addProperty("dungeonsMs", (c - b) / 1000000);
                timing.addProperty("catalogueMs", (e - c) / 1000000); timing.addProperty("otherMs", (f - e) / 1000000);
                root.add("timing", timing);
                root.add("notFound", disc.notFound);
                root.addProperty("builderAnchorsChecked", disc.builderAnchors == null ? 0 : disc.builderAnchors.size());
                root.add("registerDrift", disc.registerDrift);
                JsonArray arr = new JsonArray();
                for (JsonObject r : recs) arr.add(r);
                root.add("sites", arr);
                File o = new File(out), tmp = new File(out + ".part");
                try (Writer wr = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, wr);
                }
                Files.move(tmp.toPath(), o.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("CAPTURE_DISCOVER done sites=" + recs.size() + " notFound=" + disc.notFound.size()
                        + " ms=" + (System.nanoTime() - t0) / 1000000 + " out=" + out);
            } catch (Throwable t) {
                err = t.toString();
                getLogger().severe("CAPTURE_DISCOVER_FAILED " + t);
                t.printStackTrace();
            }
            final String error = err;
            Bukkit.getScheduler().runTask(this, () -> {
                JsonObject line = new JsonObject();
                line.addProperty("type", "discover");
                line.addProperty("ok", error == null);
                if (error != null) line.addProperty("error", error);
                if (root.has("counts")) line.add("counts", root.get("counts"));
                line.addProperty("ms", (System.nanoTime() - t0) / 1000000);
                result(line);
                finish(error == null ? null : error);
            });
        }, "capture-discover");
        th.setDaemon(true);
        th.start();
    }

    private String pluginVersion() {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
        return p == null ? "?" : p.getDescription().getVersion();
    }

    private int disabledCount() {
        try {
            java.lang.reflect.Method m = Class.forName("chat.jaspr.biomes.DisabledStructures").getDeclaredMethod("count");
            m.setAccessible(true);
            return (Integer) m.invoke(null);
        } catch (Throwable t) { return -1; }
    }

    // ------------------------------------------------------------------ capture
    private void captureTick() {
        if (phase.equals("wait")) {
            if (tick < num("startDelayTicks", 60)) return;
            JsonObject line = new JsonObject();
            line.addProperty("type", "start");
            line.addProperty("plugin", pluginVersion());
            line.addProperty("shim", shimStatus);
            line.addProperty("disabledStructures", disabledCount());
            line.addProperty("startupNewChunks", newChunks);
            line.addProperty("startupRecordedWrites", recorder.recordedWrites);
            line.addProperty("loadedChunksAtStart", loadedChunks());
            line.addProperty("ms", (System.nanoTime() - startNanos) / 1000000);
            result(line);
            // A clean slate: nothing that happened at startup stays loaded.
            for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) w.unloadChunk(c.getX(), c.getZ(), false, false);
            next();
            return;
        }
        if (index >= sites.size()) return;
        JsonObject s = sites.get(index);
        String label = s.get("id").getAsString() + "@" + s.get("variant").getAsString();
        try {
            switch (phase) {
                case "load": {
                    long budget = num("loadBudgetMs", 200) * 1000000L, t0 = System.nanoTime();
                    while (loadPos < loadList.size() && System.nanoTime() - t0 < budget) {
                        int[] c = loadList.get(loadPos++);
                        world.getChunkAt(c[0], c[1]);
                    }
                    totalLoadNanos += System.nanoTime() - t0;
                    if (loadPos < loadList.size()) return;
                    tLoad = System.nanoTime();
                    phase = "settle"; settleTicks = 0;
                    return;
                }
                case "settle": {
                    settleTicks++;
                    int queued = waterQueue();
                    if (settleTicks < num("settleMinTicks", 5)) return;
                    if (queued > 0 && settleTicks < num("settleMaxTicks", 1200)) return;
                    tSettle = System.nanoTime();
                    JsonObject line = base(s);
                    JsonArray unpop = new JsonArray();
                    JsonObject ch = s.getAsJsonObject("chunks");
                    for (int x = ch.get("x0").getAsInt(); x <= ch.get("x1").getAsInt(); x++)
                        for (int z = ch.get("z0").getAsInt(); z <= ch.get("z1").getAsInt(); z++)
                            if (!populated.contains(world.getName() + ":" + x + ":" + z)) unpop.add(x + "," + z);
                    line.add("unpopulated", unpop);
                    line.addProperty("waterRepairQueueLeft", queued);
                    line.addProperty("settleTicks", settleTicks);
                    File outDir = new File(str("outDir", "dumps"));
                    outDir.mkdirs();
                    JsonObject capture = new JsonObject();
                    capture.addProperty("harness", VERSION);
                    capture.addProperty("plugin", pluginVersion());
                    if (job.has("jar")) capture.add("jar", job.get("jar"));
                    capture.addProperty("shim", shimStatus);
                    capture.addProperty("time", Instant.now().toString());
                    capture.addProperty("run", str("run", ""));
                    capture.addProperty("chunksGenerated", newChunks - siteNew0);
                    capture.addProperty("unpopulatedChunks", unpop.size());
                    capture.addProperty("waterRepairQueueLeft", queued);
                    Dumper dumper = new Dumper(world, recorder, s);
                    File[] files = dumper.dump(outDir, str("set", "unnamed"), Long.toString(world.getSeed()), capture);
                    tDump = System.nanoTime();
                    line.add("dump", dumper.stats);
                    JsonArray fl = new JsonArray();
                    for (File f : files) if (f != null) fl.add(f.getName());
                    line.add("files", fl);
                    line.addProperty("ok", unpop.size() == 0 && dumper.stats.get("maskSolid").getAsInt() > 0);
                    if (unpop.size() > 0) line.addProperty("error", "unpopulated chunks in the capture box");
                    else if (dumper.stats.get("maskSolid").getAsInt() == 0) line.addProperty("error", "empty mask: nothing built here");
                    finishSite(line);
                    return;
                }
                default:
                    return;
            }
        } catch (Throwable t) {
            getLogger().severe("CAPTURE_SITE_FAILED " + label + " " + t);
            t.printStackTrace();
            JsonObject line = base(s);
            line.addProperty("ok", false);
            line.addProperty("error", t.toString());
            finishSite(line);
        }
    }

    private JsonObject base(JsonObject s) {
        JsonObject line = new JsonObject();
        line.addProperty("type", "site");
        line.addProperty("id", s.get("id").getAsString());
        line.addProperty("variant", s.get("variant").getAsString());
        line.addProperty("name", s.get("name").getAsString());
        return line;
    }

    private void finishSite(JsonObject line) {
        long now = System.nanoTime();
        line.addProperty("chunksLoaded", loadList == null ? 0 : loadList.size());
        line.addProperty("chunksGenerated", newChunks - siteNew0);
        JsonObject t = new JsonObject();
        t.addProperty("loadMs", tLoad > 0 ? (tLoad - tLoad0) / 1000000 : -1);
        t.addProperty("settleMs", tSettle > 0 && tLoad > 0 ? (tSettle - tLoad) / 1000000 : -1);
        t.addProperty("dumpMs", tDump > 0 && tSettle > 0 ? (tDump - tSettle) / 1000000 : -1);
        long u0 = System.nanoTime();
        int unloaded = unloadAll();
        t.addProperty("unloadMs", (System.nanoTime() - u0) / 1000000);
        t.addProperty("totalMs", (System.nanoTime() - tSite) / 1000000);
        line.add("timing", t);
        line.addProperty("unloaded", unloaded);
        line.addProperty("stillLoaded", loadedChunks());
        line.addProperty("recordsLeft", recorder.liveRecords());
        line.addProperty("heapMB", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20);
        totalNewChunks += newChunks - siteNew0;
        totalSites++;
        if (line.has("ok") && line.get("ok").getAsBoolean()) okSites++;
        result(line);
        getLogger().info("CAPTURE_SITE " + line.get("id").getAsString() + "@" + line.get("variant").getAsString()
                + " ok=" + line.get("ok") + " " + t + (line.has("error") ? " error=" + line.get("error").getAsString() : ""));
        next();
    }

    private int unloadAll() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) if (w.unloadChunk(c.getX(), c.getZ(), false, false)) n++;
        return n;
    }

    private int loadedChunks() { int n = 0; for (World w : Bukkit.getWorlds()) n += w.getLoadedChunks().length; return n; }

    private void next() {
        index++;
        tLoad = tSettle = tDump = 0;
        loadList = null;
        if (index >= sites.size()) { finish(null); return; }
        JsonObject s = sites.get(index);
        String wn = s.has("world") ? s.get("world").getAsString() : "world";
        world = Bukkit.getWorld(wn);
        tSite = tLoad0 = System.nanoTime();
        siteNew0 = newChunks;
        if (world == null) {
            JsonObject line = base(s);
            line.addProperty("ok", false);
            line.addProperty("error", "world not loaded: " + wn);
            finishSite(line);
            return;
        }
        JsonObject c = s.getAsJsonObject("chunks");
        loadList = new ArrayList<>();
        for (int z = c.get("z0").getAsInt() - 1; z <= c.get("z1").getAsInt() + 1; z++)
            for (int x = c.get("x0").getAsInt() - 1; x <= c.get("x1").getAsInt() + 1; x++) loadList.add(new int[]{x, z});
        loadPos = 0;
        phase = "load";
    }

    /** WaterRepair's pending seam/repair queue (a live post-load pass that may prune blocks). */
    private int waterQueue() {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
            Object water = Discover.get(p, "water");
            if (water == null) return 0;
            return ((Collection<?>) Discover.get(water, "queue")).size();
        } catch (Throwable t) { return 0; }
    }

    private void result(JsonObject line) {
        if (results == null) return;
        try { results.write(Dumper.GSON.toJson(line)); results.write('\n'); results.flush(); }
        catch (IOException e) { getLogger().severe("CAPTURE_RESULTS_WRITE " + e); }
    }

    private void finish(String error) {
        if (finished) return;
        finished = true;
        JsonObject line = new JsonObject();
        line.addProperty("type", "end");
        line.addProperty("ok", error == null);
        if (error != null) line.addProperty("error", error);
        line.addProperty("mode", mode);
        line.addProperty("sites", totalSites);
        line.addProperty("okSites", okSites);
        line.addProperty("chunksGenerated", totalNewChunks);
        line.addProperty("loadSeconds", totalLoadNanos / 1e9);
        line.addProperty("chunksPerSecondLoadPhase", totalLoadNanos > 0 ? Math.round(totalNewChunks / (totalLoadNanos / 1e9) * 10) / 10.0 : 0);
        line.addProperty("newChunksTotal", newChunks);
        line.addProperty("populateEvents", populateEvents);
        line.addProperty("unloadEvents", unloadEvents);
        line.addProperty("savesPrevented", savesPrevented);
        line.addProperty("regionChunksOnDisk", regionChunksOnDisk());
        if (recorder != null) {
            line.addProperty("hookWritesTotal", recorder.totalWrites);
            line.addProperty("hookWritesRecorded", recorder.recordedWrites);
            line.addProperty("stackWalks", recorder.walks);
            line.addProperty("stackWalkMs", recorder.walkNanos / 1000000);
        }
        line.addProperty("peakHeapMB", peakHeap >> 20);
        long pools = 0;
        for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans())
            if (b.getType() == MemoryType.HEAP && b.getPeakUsage() != null) pools += b.getPeakUsage().getUsed();
        line.addProperty("peakHeapPoolsMB", pools >> 20);
        line.addProperty("uptimeSeconds", (System.nanoTime() - startNanos) / 1e9);
        result(line);
        getLogger().info("CAPTURE_END " + Dumper.GSON.toJson(line));
        CaptureHook.sink = null;
        ApocalypseShim.remove();
        if (job == null || !job.has("shutdown") || job.get("shutdown").getAsBoolean()) {
            unloadAll();
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5L);
        }
    }

    /** Chunks present in the region files (must stay 0: the harness never saves). */
    private int regionChunksOnDisk() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            File[] files = new File(w.getWorldFolder(), "region").listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.getName().endsWith(".mca")) continue;
                try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                    if (in.length() < 4096) continue;
                    for (int i = 0; i < 1024; i++) if (in.readInt() != 0) n++;
                } catch (IOException ignored) { }
            }
        }
        return n;
    }
}
