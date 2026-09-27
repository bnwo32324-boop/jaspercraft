package chat.jaspr.lostcities;

import chat.jaspr.biomes.Terrain;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprLostCities: a port of "The Lost Cities" (1.12-2.0.22) as a populator on the HorrorBiomes overworld.
 *
 * City regions (one big city each, a 64x64-chunk grid, 55% of cells) are built into new chunks only; chunks that
 * existed when the plugin first ran (the jaspr-cities-v1.boundary Anvil snapshot) and HorrorBiomes sanctuaries are
 * never written, and the border ring is blended into the surrounding terrain the way the mod flattened it.
 * Every chunk it writes carries the durable imported-chunk guard so HorrorBiomes' repair passes leave it alone.
 */
public final class LostCitiesPlugin extends JavaPlugin implements Listener {
    static final String VERSION = "1.0.0";
    static final String CHUNK_MARKER = "jaspr-imported-v1";
    private static final int GUARD_MAGIC = 0x494D5031;
    private static final int FAILURE_LIMIT = 25;
    private static final boolean FIXTURE = Boolean.getBoolean("jaspr.lostcities.fixture");
    /** Fixture control run: plan and answer CityApi as usual, but build nothing (baseline for the checks). */
    private static final boolean CONTROL = FIXTURE && Boolean.getBoolean("jaspr.lostcities.fixture.control");

    private static volatile LostCitiesPlugin instance;

    /** Worlds besides "world" that build Lost Cities (registered by other plugins through CityApi). */
    static final Map<String, Extra> EXTRA = new ConcurrentHashMap<>();

    static final class Extra {
        final String titleFormat;
        final CityApi.PrimerHook hook;
        Extra(String titleFormat, CityApi.PrimerHook hook) { this.titleFormat = titleFormat; this.hook = hook; }
    }

    private Assets assets;
    private Path protectionRoot;
    private final Map<UUID, Context> worlds = new ConcurrentHashMap<>();
    private volatile boolean stopped;
    private boolean readyLogged;
    private int failures;

    // Metrics (main thread)
    private long chunks, cityChunks, normalChunks, unchanged, skippedGuarded;
    private long computeNanos, writeNanos, maxCompute, maxWrite, maxTotal, allNanos;
    private final Map<String, Integer> features = new TreeMap<>();
    private long chests, lostCityChests, vanillaChests, emptyChests, spawners, trees, vines, valuables;
    private final Map<String, Integer> spawnerMobs = new TreeMap<>();

    private final Map<UUID, String> lastRegion = new HashMap<>();

    static final class Context {
        final World world;
        final ChunkBoundary boundary;
        final CityWorld w;
        final Builder builder;
        /** Chunks known built this session (bounded; the guard file is the durable record). */
        final Lru<Long, Boolean> built = new Lru<>(131072);
        BlockPopulator populator;
        String titleFormat = "The Lost City of %s";
        Context(World world, ChunkBoundary boundary, CityWorld w, Builder builder) {
            this.world = world; this.boundary = boundary; this.w = w; this.builder = builder;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        instance = this;
        try {
            Plugin horror = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
            if (horror == null) throw new IOException("JasprHorrorBiomes unavailable");
            protectionRoot = horror.getDataFolder().toPath().resolve("imported-protection-v1");
            Files.createDirectories(protectionRoot);
            assets = Assets.load(file -> getResource("lostcities/citydata/" + file));
        } catch (Throwable e) {
            getLogger().severe("LOST_CITIES_REFUSED " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            assets = null;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        if (assets != null) {
            getLogger().info("LOST_CITIES_ASSETS parts=" + assets.parts.size() + " buildings=" + assets.buildings.size()
                + " multibuildings=" + assets.multiBuildings.size() + " palettes=" + assets.palettes.size() + " citystyles=" + assets.cityStyles.size());
            for (World world : Bukkit.getWorlds()) attach(world);   // at STARTUP none yet: "world" attaches on WorldInitEvent
        }
        Bukkit.getScheduler().runTaskTimer(this, this::titles, 100L, 100L);   // HorrorBiomes ambience phase
    }

    @Override
    public void onDisable() {
        for (Context c : worlds.values()) if (c.populator != null) c.world.getPopulators().remove(c.populator);
        getLogger().info("LOST_CITIES_METRICS " + metrics());
        worlds.clear();
        instance = null;
    }

    static CityWorld cityWorld(World world) {
        LostCitiesPlugin p = instance;
        if (p == null) return null;
        Context c = p.worlds.get(world.getUID());
        if (c == null) c = p.attach(world);
        return c == null ? null : c.w;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void worldInit(WorldInitEvent e) { attach(e.getWorld()); }

    private synchronized Context attach(World world) {
        if (assets == null) return null;
        Extra extra = EXTRA.get(world.getName());
        boolean overworld = "world".equals(world.getName());
        if ((!overworld && extra == null) || world.getEnvironment() != World.Environment.NORMAL) return null;
        Context existing = worlds.get(world.getUID());
        if (existing != null) return existing;
        if (!overworld) return attachExtra(world, extra);
        try {
            ChunkBoundary boundary = ChunkBoundary.open(world.getWorldFolder(), world.getSeed(), "jaspr-cities-v1.boundary");
            Terrain terrain = new Terrain(world.getSeed());
            Sanctuaries sanctuaries = new Sanctuaries(world.getSeed(), getLogger());
            // Plans other packs committed to before the first city are kept out of city land like existing chunks.
            Committed committed = Committed.open(world, getDataFolder().getParentFile(), getLogger());
            CityWorld w = new CityWorld(world.getSeed(), assets, terrain, (x, z) -> boundary.contains(x, z) || committed.contains(x, z), sanctuaries);
            Builder builder = new Builder(w, world, (cx, cz) -> guard(world, cx, cz));
            Context full = new Context(world, boundary, w, builder);
            full.populator = new CityPopulator(full);
            world.getPopulators().add(full.populator);
            worlds.put(world.getUID(), full);
            getLogger().info("LOST_CITIES_BOUNDARY_READY world=" + world.getName() + " existingChunks=" + boundary.count()
                + " committedSites=" + committed.sites + " committedChunks=" + committed.chunkCount() + (committed.created ? " committedSnapshot=new" : "")
                + " sanctuaries=[" + sanctuaries.describe() + "] populators=" + world.getPopulators().size());
            if (!readyLogged) {
                readyLogged = true;
                getLogger().info("LOST_CITIES_READY version=" + VERSION + " parts=" + assets.parts.size() + " buildings=" + assets.buildings.size()
                    + " regionGrid=" + Regions.GRID + " regionChance=" + Regions.CHANCE + " multibuildings=" + assets.multiBuildings.size()
                    + " worldStyle=" + Profile.WORLD_STYLE + (B.unknownBlocks > 0 ? " chiselToVanilla=" + B.unknownBlocks : "") + (FIXTURE ? " fixture=on" : "") + (CONTROL ? " control=noBuild" : ""));
            }
            return full;
        } catch (Throwable e) {
            getLogger().severe("LOST_CITIES_ATTACH_REFUSED " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            return null;
        }
    }

    /** A registered world: its own seed and terrain, no sanctuaries or committed sites, the registrant's primer hook. */
    private Context attachExtra(World world, Extra extra) {
        try {
            ChunkBoundary boundary = ChunkBoundary.open(world.getWorldFolder(), world.getSeed(), "jaspr-cities-v1.boundary");
            CityWorld w = new CityWorld(world.getSeed(), assets, new Terrain(world.getSeed()), (x, z) -> boundary.contains(x, z), null);
            Builder builder = new Builder(w, world, (cx, cz) -> guard(world, cx, cz), extra.hook);
            Context ctx = new Context(world, boundary, w, builder);
            if (extra.titleFormat != null) ctx.titleFormat = extra.titleFormat;
            ctx.populator = new CityPopulator(ctx);
            world.getPopulators().add(ctx.populator);
            worlds.put(world.getUID(), ctx);
            getLogger().info("LOST_CITIES_BOUNDARY_READY world=" + world.getName() + " existingChunks=" + boundary.count()
                + " registered=true hook=" + (extra.hook != null) + " populators=" + world.getPopulators().size());
            return ctx;
        } catch (Throwable e) {
            getLogger().severe("LOST_CITIES_ATTACH_REFUSED world=" + world.getName() + " " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            return null;
        }
    }

    // ------------------------------------------------------------------ guard (as JasprMuseMaps / JasprImportedWorldgen)

    private Path guardPath(World world, int cx, int cz) {
        return protectionRoot.resolve(world.getUID().toString()).resolve(Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32)).resolve(cx + "_" + cz + ".guard");
    }

    boolean guarded(World world, int cx, int cz) { return Files.isRegularFile(guardPath(world, cx, cz)); }

    /**
     * The HorrorBiomes imported-protection guard, written before the chunk's blocks change. Atomic (temp file + move)
     * but not fsynced: the chunk it protects lives in memory until the world saves, so an fsync here bought no
     * crash safety and cost ~6 ms per chunk.
     */
    private void guard(World world, int cx, int cz) throws IOException {
        Path path = guardPath(world, cx, cz);
        if (!Files.isRegularFile(path)) {
            Path dir = path.getParent();
            if (guardDirs.get(dir.toString()) == null) { Files.createDirectories(dir); guardDirs.put(dir.toString(), Boolean.TRUE); }
            Path tmp = dir.resolve(cx + "_" + cz + ".cities-pending");
            try {
                try (DataOutputStream data = new DataOutputStream(new FileOutputStream(tmp.toFile()))) {
                    data.writeInt(GUARD_MAGIC); data.writeInt(cx); data.writeInt(cz); data.writeLong(world.getSeed());
                }
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(tmp); }
        }
        world.getBlockAt(cx * 16, 0, cz * 16).setMetadata(CHUNK_MARKER, new FixedMetadataValue(this, true));
    }

    private final Lru<String, Boolean> guardDirs = new Lru<>(4096);

    @EventHandler(priority = EventPriority.LOW)
    public void chunkLoad(ChunkLoadEvent e) {
        Context ctx = worlds.get(e.getWorld().getUID());
        if (ctx == null || e.isNewChunk()) return;
        Chunk c = e.getChunk();
        int cx = c.getX(), cz = c.getZ();
        if (!ctx.w.managed(cx, cz)) return;
        if (guarded(ctx.world, cx, cz)) {
            c.getBlock(0, 0, 0).setMetadata(CHUNK_MARKER, new FixedMetadataValue(this, true));
            ctx.built.put(CityWorld.key(cx, cz), Boolean.TRUE);
        }
    }

    // ------------------------------------------------------------------ generation

    private final class CityPopulator extends BlockPopulator {
        private final Context ctx;
        CityPopulator(Context ctx) { this.ctx = ctx; }

        @Override
        public void populate(World world, Random ignored, Chunk chunk) {
            if (stopped || !isEnabled() || CONTROL) return;
            int cx = chunk.getX(), cz = chunk.getZ();
            try {
                if (ctx.w.blocked(cx, cz)) return;                // pre-existing land or a sanctuary: never
                if (!ctx.w.managed(cx, cz)) return;
                if (guarded(world, cx, cz)) { skippedGuarded++; return; }   // another pack already built here
                Builder.Outcome out = ctx.builder.build(chunk, k -> ctx.built.get(k) != null || guardedKey(world, k));
                record(ctx, cx, cz, out);
            } catch (Throwable t) {
                fail(cx, cz, t);
            }
        }
    }

    private boolean guardedKey(World world, long k) { return guarded(world, (int) (k >> 32), (int) k); }

    private void record(Context ctx, int cx, int cz, Builder.Outcome out) {
        chunks++;
        allNanos += out.computeNanos + out.writeNanos;
        if (!out.changed) { unchanged++; return; }
        ctx.built.put(CityWorld.key(cx, cz), Boolean.TRUE);
        if (out.city) cityChunks++; else normalChunks++;
        computeNanos += out.computeNanos;
        writeNanos += out.writeNanos;
        maxCompute = Math.max(maxCompute, out.computeNanos);
        maxWrite = Math.max(maxWrite, out.writeNanos);
        maxTotal = Math.max(maxTotal, out.computeNanos + out.writeNanos);
        for (String f : out.features) {
            String key = f.contains(" multi:") ? f.substring(f.indexOf(" multi:") + 1) : f;
            features.merge(key, 1, Integer::sum);
            if (f.contains(" multi:")) features.merge(f.substring(0, f.indexOf(" multi:")), 1, Integer::sum);
        }
        chests += out.chests; lostCityChests += out.lostCityChests; vanillaChests += out.vanillaChests; emptyChests += out.emptyChests;
        spawners += out.spawners; trees += out.trees; vines += out.vines; valuables += out.valuables;
        for (String m : out.spawnerMobs) spawnerMobs.merge(m, 1, Integer::sum);
        long ms = (out.computeNanos + out.writeNanos) / 1000000L;
        if (ms > 250) getLogger().warning("LOST_CITIES_SLOW_CHUNK chunk=" + cx + "," + cz + " ms=" + ms);
    }

    private void fail(int cx, int cz, Throwable t) {
        failures++;
        StackTraceElement at = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
        getLogger().severe("LOST_CITIES_CHUNK_FAILED chunk=" + cx + "," + cz + " " + t.getClass().getSimpleName() + ": " + safe(t.getMessage())
            + (at != null ? " at " + at.getClassName().replace("chat.jaspr.lostcities.", "") + ":" + at.getLineNumber() : ""));
        if (failures >= FAILURE_LIMIT && !stopped) {
            stopped = true;
            getLogger().severe("LOST_CITIES_GENERATION_STOPPED after " + failures + " failures; built chunks stay as they are");
            for (Context c : worlds.values()) if (c.populator != null) {
                BlockPopulator p = c.populator;
                Bukkit.getScheduler().runTask(this, () -> c.world.getPopulators().remove(p));
            }
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\r\\n\\t]", " ");
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    String metrics() {
        long built = cityChunks + normalChunks;
        return "chunks=" + chunks + " built=" + built + " city=" + cityChunks + " border=" + normalChunks + " unchanged=" + unchanged
            + " skippedGuarded=" + skippedGuarded + " failures=" + failures + " stopped=" + stopped
            + " avgMsPerManagedChunk=" + fmt(chunks == 0 ? 0 : allNanos / 1e6 / chunks)
            + " avgComputeMs=" + fmt(built == 0 ? 0 : computeNanos / 1e6 / built) + " avgWriteMs=" + fmt(built == 0 ? 0 : writeNanos / 1e6 / built)
            + " maxComputeMs=" + fmt(maxCompute / 1e6) + " maxWriteMs=" + fmt(maxWrite / 1e6) + " maxTotalMs=" + fmt(maxTotal / 1e6)
            + " chests=" + chests + " lostCityLoot=" + lostCityChests + " vanillaLoot=" + vanillaChests + " emptyChests=" + emptyChests
            + " spawners=" + spawners + " trees=" + trees + " vines=" + vines + " valuablesSanitized=" + valuables
            + " reservedCalls=" + CityApi.CALLS.get() + " reservedTrue=" + CityApi.RESERVED.get()
            + " writePhases[" + Builder.phases() + "]";
    }

    private static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }

    // ------------------------------------------------------------------ /where and titles

    /** The built city region (not the border ring) the player stands in, or null. */
    private Regions.Region builtRegion(Player p) {
        Context ctx = worlds.get(p.getWorld().getUID());
        if (ctx == null) return null;
        Location l = p.getLocation();
        int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
        Regions.Region r = Regions.regionAt(ctx.w.seed, cx, cz);
        if (r == null) return null;
        long k = CityWorld.key(cx, cz);
        if (ctx.built.get(k) == null && !guarded(ctx.world, cx, cz)) return null;
        return r;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void where(PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().trim();
        if (!c.equalsIgnoreCase("/where") && !c.equalsIgnoreCase("/survey") && !c.equalsIgnoreCase("/biome")) return;
        Player p = e.getPlayer();
        Regions.Region r = builtRegion(p);
        if (r == null) return;
        e.setCancelled(true);
        Context ctx = worlds.get(p.getWorld().getUID());
        int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
        p.sendMessage(ChatColor.GREEN + "Lost City: " + ChatColor.WHITE + r.name + ChatColor.GOLD + " (Lost Cities)");
        try {
            BuildingInfo info = ctx.w.info(cx, cz);
            String what;
            if (!info.isCity) what = "outskirts";
            else if (info.hasBuilding) what = "building " + info.buildingType.name + (info.multiBuilding != null ? " (" + info.multiBuilding.name + ")" : "")
                + ", " + info.getNumFloors() + " floors, " + info.floorsBelowGround + " cellars" + (info.ruinHeight >= 0 ? ", ruined" : "");
            else what = "street (" + info.streetType.name().toLowerCase() + ")";
            Railway.RailChunkInfo rail = info.getRailInfo();
            String extra = "";
            if (info.getMaxHighwayLevel() >= 0) extra += " | highway";
            if (rail.type != RailChunkType.NONE) extra += " | subway " + rail.type.name().toLowerCase().replace('_', ' ');
            p.sendMessage(ChatColor.GRAY + "District " + r.key() + " | " + what + " | city level " + info.cityLevel
                + " | style " + info.getCityStyle().name.replace("citystyle_", "") + extra);
        } catch (RuntimeException ex) {
            p.sendMessage(ChatColor.GRAY + "District " + r.key());
        }
    }

    /**
     * Every 100 ticks, scheduled in the same startup tick and period as HorrorBiomes' ambience task, which shows the
     * biome title when a player's HorrorBiomes profile changes. The same change is tracked here: on a step where
     * HorrorBiomes shows its title the city title waits until that one has faded (70 ticks), otherwise it shows at
     * once -- so neither title overwrites the other.
     */
    private void titles() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Context ctx = worlds.get(p.getWorld().getUID());
            boolean hbTitle = false;
            if (ctx != null) {
                Location l = p.getLocation();
                Integer index = ctx.w.terrain.sample(l.getBlockX(), l.getBlockZ()).profile.index;
                Integer old = hbIndex.put(id, index);
                hbTitle = old == null || !old.equals(index);
            } else hbIndex.remove(id);
            Regions.Region r;
            try { r = builtRegion(p); } catch (RuntimeException e) { r = null; }
            String key = r == null ? null : r.key();
            if (key == null) { lastRegion.remove(id); continue; }
            if (key.equals(lastRegion.get(id))) continue;
            lastRegion.put(id, key);
            String name = r.name;
            String format = ctx != null ? ctx.titleFormat : "The Lost City of %s";
            if (!hbTitle) { cityTitle(p, name, format); continue; }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && key.equals(lastRegion.get(id))) cityTitle(p, name, format);
            }, 72L);
        }
    }

    private static void cityTitle(Player p, String name, String format) {
        p.sendTitle(ChatColor.GRAY + String.format(format, name), ChatColor.DARK_GRAY + "Lost Cities", 10, 60, 20);
    }

    private final Map<UUID, Integer> hbIndex = new HashMap<>();

    @EventHandler
    public void quit(PlayerQuitEvent e) { lastRegion.remove(e.getPlayer().getUniqueId()); hbIndex.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "Operators only."); return true; }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        World world = sender instanceof Player ? ((Player) sender).getWorld() : Bukkit.getWorld("world");
        Context ctx = world == null ? null : worlds.get(world.getUID());
        switch (sub) {
            case "status":
                sender.sendMessage("LOST_CITIES_STATUS " + metrics());
                if (ctx != null) sender.sendMessage("LOST_CITIES_CACHES " + ctx.w.cacheStats());
                return true;
            case "features":
                sender.sendMessage("LOST_CITIES_FEATURES " + features + " mobs=" + spawnerMobs);
                getLogger().info("LOST_CITIES_FEATURES " + features + " mobs=" + spawnerMobs);
                return true;
            case "density": {
                if (world == null) return true;
                int cells = args.length > 1 ? Integer.parseInt(args[1]) : 20;
                density(sender, world.getSeed(), cells);
                return true;
            }
            case "nearest": {
                if (world == null) return true;
                Location l = sender instanceof Player ? ((Player) sender).getLocation() : world.getSpawnLocation();
                Regions.Region r = Regions.nearest(world.getSeed(), l.getBlockX() >> 4, l.getBlockZ() >> 4, 2);
                sender.sendMessage(r == null ? "No city region nearby." : "The Lost City of " + r.name + " centre chunk " + r.centerX + "," + r.centerZ
                    + " (block " + (r.centerX * 16 + 8) + "," + (r.centerZ * 16 + 8) + ") radius " + r.radius + " chunks");
                return true;
            }
            case "fixture":
                if (!FIXTURE) { sender.sendMessage("Fixture commands need -Djaspr.lostcities.fixture=true."); return true; }
                if (ctx == null) { sender.sendMessage("World not attached."); return true; }
                Fixture.start(this, ctx, sender, args);
                return true;
            default:
                sender.sendMessage("/lostcities [status|features|density [cells]|nearest|fixture ...]");
                return true;
        }
    }

    private void density(CommandSender sender, long seed, int cells) {
        int regions = 0;
        long regionChunks = 0;
        for (int gx = -cells / 2; gx < cells / 2; gx++)
            for (int gz = -cells / 2; gz < cells / 2; gz++) {
                Regions.Region r = Regions.cell(seed, gx, gz);
                if (r == null) continue;
                regions++;
                for (int cx = r.minX; cx <= r.maxX; cx++) for (int cz = r.minZ; cz <= r.maxZ; cz++) if (r.contains(cx, cz)) regionChunks++;
            }
        double areaBlocks = (double) cells * cells * Regions.GRID * Regions.GRID * 256;
        double per1000 = regions / (areaBlocks / 1e6);
        String msg = "LOST_CITIES_DENSITY cells=" + cells * cells + " cities=" + regions + " citiesPer1000x1000=" + fmt(per1000)
            + " avgCityChunks=" + (regions == 0 ? 0 : regionChunks / regions) + " cityAreaFraction=" + fmt(regionChunks * 256.0 / areaBlocks);
        sender.sendMessage(msg);
        getLogger().info(msg);
    }

    // ------------------------------------------------------------------ fixture accessors

    Map<String, Integer> featureCounts() { return features; }
    Map<String, Integer> mobCounts() { return spawnerMobs; }
    boolean isGuarded(World w, int cx, int cz) { return guarded(w, cx, cz); }
    Assets assets() { return assets; }
}
