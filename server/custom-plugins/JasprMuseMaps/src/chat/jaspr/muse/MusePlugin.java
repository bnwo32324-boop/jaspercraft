package chat.jaspr.muse;

import chat.jaspr.biomes.StructurePlanner;
import chat.jaspr.biomes.Terrain;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.RegionFileCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * JasprMuseMaps: the owner's Muse+GLM_Maps collection (139 schematics) as a separate, identifiable structure pack.
 *
 *  - New chunks: its own 32-chunk grid (Planner), admitted clear of every HorrorBiomes structure (ClaimGuard, the
 *    expedition catalogue), every JasprImportedWorldgen site (ImporterPlans) and the spawn box.
 *  - Chunks that already existed: a background retrofit builds sites into land nobody has spent time in yet
 *    (chunk inhabited time under the configured limit, no player nearby), and never where anyone has been.
 *  - /where names these sites "Muse+GLM_Maps".
 *  - Each site has Overworld, Nether and End dangers, its own custom mob and two hazards (Encounter), its own loot
 *    and a unique special item in its vault (Loot); ten carry a boss (Bosses) guarding the vault.
 *  - Essences (permanent upgrades) and the sixteen Muse+GLM_Maps weapons' perks (Weapons).
 * Chunks it builds are guarded for HorrorBiomes' repair passes exactly as imported chunks are.
 */
public final class MusePlugin extends JavaPlugin implements Listener {
    static final String CHUNK_MARKER = "jaspr-imported-v1";
    static final int SPAWN_CORE = 256;
    private static final int GUARD_MAGIC = 0x494D5031;

    private Catalog catalog;
    private Pack pack;
    private Planner planner;
    private ClaimGuard claims;
    private final ImporterPlans importer = new ImporterPlans();
    private Path protectionRoot;
    private TempBlocks temp;
    private Abilities abilities;
    private Hazards hazards;
    private Bosses bosses;
    private Essences essences;
    private Weapons weapons;
    private long ticks;
    private int tileFailures, stampedTiles, retrofitTiles;
    private long inhabitedLimit;
    private boolean retrofitEnabled, encountersEnabled;
    private Field inhabitedField;

    private final class Context {
        final World world; final ChunkBoundary boundary; final Ledger ledger; final Terrain terrain; Planner.Ground ground;
        final ArrayDeque<long[]> undecided = new ArrayDeque<>();
        final ArrayDeque<Ledger.Record> retrofit = new ArrayDeque<>();
        final Set<String> queued = new HashSet<>();
        Context(World w, ChunkBoundary b, Ledger l, Terrain t) { world = w; boundary = b; ledger = l; terrain = t; }
    }
    private final Map<UUID, Context> worlds = new HashMap<>();
    private final Map<UUID, BlockPopulator> populators = new HashMap<>();

    /** Live encounter state per placed site. */
    private static final class Site {
        final List<UUID> mobs = new ArrayList<>();
        boolean spawned; long armedAt, lastSeen; long[] hazardNext = new long[2];
        final Set<UUID> announced = new HashSet<>();
    }
    private final Map<String, Site> sites = new HashMap<>();
    private final Map<UUID, Long> nextCast = new HashMap<>();
    private final Random random = new Random();

    // ------------------------------------------------------------------ lifecycle

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) { getLogger().warning("Muse+GLM_Maps generation disabled by config."); }
        inhabitedLimit = Math.max(0, getConfig().getLong("retrofit-max-inhabited-ticks", 1200));
        retrofitEnabled = getConfig().getBoolean("retrofit", true);
        encountersEnabled = getConfig().getBoolean("encounters", true);
        temp = new TempBlocks();
        abilities = new Abilities(this);
        hazards = new Hazards(this, abilities);
        bosses = new Bosses(this, abilities);
        essences = new Essences(this);
        weapons = new Weapons(this);
        try {
            Plugin assets = Bukkit.getPluginManager().getPlugin("JasprMuseMapsPack");
            if (!(assets instanceof JavaPlugin) || !assets.isEnabled()) throw new IOException("JasprMuseMapsPack (the map data) is not installed");
            try (InputStream in = ((JavaPlugin) assets).getResource("maps/catalog.json")) {
                if (in == null) throw new IOException("Pack catalogue missing");
                catalog = new Catalog(in);
            }
            pack = new Pack((JavaPlugin) assets, catalog);
            planner = new Planner(catalog);
            Plugin horror = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
            if (horror == null || !horror.isEnabled()) throw new IOException("JasprHorrorBiomes unavailable");
            claims = new ClaimGuard();
            protectionRoot = horror.getDataFolder().toPath().resolve("imported-protection-v1");
            Files.createDirectories(protectionRoot);
            inhabitedField = net.minecraft.server.v1_12_R1.Chunk.class.getDeclaredField("w");
            inhabitedField.setAccessible(true);
            if (inhabitedField.getType() != long.class) throw new IOException("Chunk inhabited-time field changed");
        } catch (Exception e) {
            getLogger().severe("MUSE_MAPS_REFUSED " + e);
            catalog = null;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(bosses, this);
        Bukkit.getPluginManager().registerEvents(essences, this);
        Bukkit.getPluginManager().registerEvents(weapons, this);
        if (catalog != null && getConfig().getBoolean("enabled", true)) for (World w : Bukkit.getWorlds()) attach(w);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 10L);
        essences.applyAll();
        if (catalog != null) {
            final Pack check = pack;
            Thread t = new Thread(() -> {
                try { check.verifyAll(); getLogger().info("MUSE_PACK_VERIFIED designs=" + Catalog.EXPECTED); }
                catch (Exception e) { getLogger().severe("MUSE_PACK_DRIFT " + e.getMessage()); }
            }, "JasprMuseMaps-verify");
            t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); t.start();
            getLogger().info("MUSE_MAPS_READY version=" + getDescription().getVersion() + " collection=" + Catalog.COLLECTION
                + " designs=" + catalog.all().size() + " bosses=" + catalog.bosses() + " grid=" + Planner.CELL_CHUNKS
                + " retrofit=" + retrofitEnabled + " inhabitedLimit=" + inhabitedLimit + " encounters=" + encountersEnabled
                + " weapons=" + (Weapons.BOSS_WEAPON.size() + Weapons.CRAFTABLE.size()) + " essences=" + Essences.Kind.values().length);
        }
    }

    @Override public void onDisable() {
        bosses.shutdown();
        temp.restoreAll();
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (Mobs.ours(e) && !(e instanceof Player)) e.remove();
        for (Map.Entry<UUID, BlockPopulator> e : populators.entrySet()) {
            World w = Bukkit.getWorld(e.getKey());
            if (w != null) w.getPopulators().remove(e.getValue());
        }
        populators.clear(); worlds.clear(); sites.clear();
        getLogger().info("MUSE_MAPS_METRICS tiles=" + stampedTiles + " retrofitTiles=" + retrofitTiles + " failures=" + tileFailures);
    }

    long now() { return ticks; }
    TempBlocks temp() { return temp; }
    void later(int t, Runnable r) { if (isEnabled()) Bukkit.getScheduler().runTaskLater(this, r, Math.max(1, t)); }
    Ledger ledger(World w) { Context c = worlds.get(w.getUID()); if (c == null) throw new IllegalStateException("world not attached"); return c.ledger; }

    static boolean vulnerable(Player p) {
        return p.isOnline() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void init(WorldInitEvent e) { if (catalog != null && getConfig().getBoolean("enabled", true)) attach(e.getWorld()); }

    private void attach(World world) {
        if (!world.getName().equals("world") || world.getEnvironment() != World.Environment.NORMAL || worlds.containsKey(world.getUID())) return;
        try {
            ChunkBoundary boundary = ChunkBoundary.open(world.getWorldFolder(), world.getSeed(), "jaspr-muse-v1.boundary");
            Ledger ledger = new Ledger(getDataFolder().toPath().resolve("cells-" + world.getUID()), catalog);
            Context ctx = new Context(world, boundary, ledger, new Terrain(world.getSeed()));
            ctx.ground = new Planner.Ground() {
                @Override public int surface(int x, int z) { return ctx.terrain.sample(x, z).y; }
                @Override public boolean permits(int x, int y, int z, Catalog.Design d) { return admit(ctx, x, y, z, d); }
            };
            int loaded = ledger.loadAll(world.getSeed());
            Set<Long> cells = new LinkedHashSet<>();
            for (long[] c : boundary.chunks())
                cells.add(Ledger.key(Math.floorDiv((int) c[0], Planner.CELL_CHUNKS), Math.floorDiv((int) c[1], Planner.CELL_CHUNKS)));
            for (long k : cells) ctx.undecided.add(new long[]{(int) (k >> 32), (int) k});
            for (Ledger.Record r : ledger.placed()) if (r.retrofit && !r.retrofitDone && !r.abandoned) queueRetrofit(ctx, r);
            BlockPopulator populator = new MusePopulator(ctx);
            world.getPopulators().add(populator);
            populators.put(world.getUID(), populator);
            worlds.put(world.getUID(), ctx);
            getLogger().info("MUSE_BOUNDARY_READY world=" + world.getName() + " importer=" + importerState(world) + " existingChunks=" + boundary.count()
                + " existingCells=" + cells.size() + " receipts=" + loaded + " populators=" + world.getPopulators().size());
        } catch (Exception e) {
            getLogger().severe("MUSE_ATTACH_REFUSED " + e);
        }
    }

    // ------------------------------------------------------------------ admission

    private boolean admit(Context ctx, int x, int y, int z, Catalog.Design d) {
        int w = d.width(), dp = d.depth();
        // Spawn core: nothing within 256 blocks of spawn. (Farther out, the inhabited-time rule below keeps every
        // place players actually use untouched; the other packs' 600-block box would leave no explored land at all.)
        if (x < SPAWN_CORE && x + w > -SPAWN_CORE && z < SPAWN_CORE && z + dp > -SPAWN_CORE) return false;
        long seed = ctx.world.getSeed();
        try {
            if (Planner.big(d)) {
                Pack.Blocks occ = pack.blocks(d);
                if (expeditionEnvelope(seed, x, y, z, w, dp, occ)) return false;
                if (claims.envelopeConflicts(ctx.terrain, x, y, z, d, occ)) return false;
            } else {
                if (conflictsWithExpeditions(seed, x, z, w, dp)) return false;
                if (claims.conflicts(ctx.terrain, x, y, z, d)) return false;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        int c0 = Math.floorDiv(x, 16) - 1, c1 = Math.floorDiv(x + w - 1, 16) + 1;
        int d0 = Math.floorDiv(z, 16) - 1, d1 = Math.floorDiv(z + dp - 1, 16) + 1;
        if (importer.conflicts(ctx.world, c0, d0, c1, d1)) return false;
        // Chunks that already existed: only land nobody has spent time in, and never another pack's guarded chunk.
        for (int cx = c0; cx <= c1; cx++) for (int cz = d0; cz <= d1; cz++) {
            if (!ctx.boundary.contains(cx, cz)) continue;
            if (!retrofitEnabled) return false;
            if (guarded(ctx.world, cx, cz)) return false;
            if (inhabited(ctx.world, cx, cz) > inhabitedLimit) return false;
        }
        return true;
    }

    private static boolean overlap(int x, int z, int sx, int sz, StructurePlanner.Site other) {
        return x < (long) other.x + other.width + 16 && x + (long) sx + 16 > other.x
            && z < (long) other.z + other.depth + 16 && z + (long) sz + 16 > other.z;
    }
    /** The HorrorBiomes expedition catalogue's planned sites (as JasprImportedWorldgen checks them). */
    static boolean conflictsWithExpeditions(long seed, int x, int z, int sx, int sz) {
        for (int a = Math.floorDiv(x - 512, StructurePlanner.EXPANSION_REGION); a <= Math.floorDiv(x + sx + 512, StructurePlanner.EXPANSION_REGION); a++)
            for (int b = Math.floorDiv(z - 512, StructurePlanner.EXPANSION_REGION); b <= Math.floorDiv(z + sz + 512, StructurePlanner.EXPANSION_REGION); b++) {
                StructurePlanner.Site s = StructurePlanner.expansionRegion(seed, a, b);
                if (s != null && overlap(x, z, sx, sz, s)) return true;
            }
        for (int a = Math.floorDiv(x - 512, StructurePlanner.REGION); a <= Math.floorDiv(x + sx + 512, StructurePlanner.REGION); a++)
            for (int b = Math.floorDiv(z - 512, StructurePlanner.REGION); b <= Math.floorDiv(z + sz + 512, StructurePlanner.REGION); b++) {
                StructurePlanner.Site s = StructurePlanner.region(seed, a, b);
                if (s != null && overlap(x, z, sx, sz, s)) return true;
            }
        return false;
    }
    static boolean expeditionEnvelope(long seed, int x, int y, int z, int sx, int sz, Pack.Blocks occ) {
        int m = 16;
        for (int a = Math.floorDiv(x - 512, StructurePlanner.EXPANSION_REGION); a <= Math.floorDiv(x + sx + 512, StructurePlanner.EXPANSION_REGION); a++)
            for (int b = Math.floorDiv(z - 512, StructurePlanner.EXPANSION_REGION); b <= Math.floorDiv(z + sz + 512, StructurePlanner.EXPANSION_REGION); b++) {
                StructurePlanner.Site s = StructurePlanner.expansionRegion(seed, a, b);
                if (s != null && overlap(x, z, sx, sz, s) && occ.meets(x, y, z, s.x - m, s.z - m, s.x + s.width + m, s.z + s.depth + m, 0, 255)) return true;
            }
        for (int a = Math.floorDiv(x - 512, StructurePlanner.REGION); a <= Math.floorDiv(x + sx + 512, StructurePlanner.REGION); a++)
            for (int b = Math.floorDiv(z - 512, StructurePlanner.REGION); b <= Math.floorDiv(z + sz + 512, StructurePlanner.REGION); b++) {
                StructurePlanner.Site s = StructurePlanner.region(seed, a, b);
                if (s != null && overlap(x, z, sx, sz, s) && occ.meets(x, y, z, s.x - m, s.z - m, s.x + s.width + m, s.z + s.depth + m, 0, 255)) return true;
            }
        return false;
    }

    /** Ticks players have spent near this chunk (0 when it does not exist). Main thread. */
    long inhabited(World w, int cx, int cz) {
        try {
            if (w.isChunkLoaded(cx, cz)) return inhabitedField.getLong(((CraftChunk) w.getChunkAt(cx, cz)).getHandle());
            NBTTagCompound root = RegionFileCache.d(w.getWorldFolder(), cx, cz);
            if (root == null || !root.hasKeyOfType("Level", 10)) return 0;
            return root.getCompound("Level").getLong("InhabitedTime");
        } catch (Exception e) {
            return Long.MAX_VALUE;   // unreadable: treat as lived-in (never retrofit)
        }
    }

    private Path guardPath(World world, int cx, int cz) {
        return protectionRoot.resolve(world.getUID().toString()).resolve(Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32)).resolve(cx + "_" + cz + ".guard");
    }
    private boolean guarded(World world, int cx, int cz) { return Files.isRegularFile(guardPath(world, cx, cz)); }

    /** Same durable guard as JasprImportedWorldgen, so HorrorBiomes' water/floater repair leaves the chunk alone. */
    private void guard(World world, int cx, int cz) throws IOException {
        Path path = guardPath(world, cx, cz);
        if (Files.isRegularFile(path)) return;
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "muse-", ".pending");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
                DataOutputStream data = new DataOutputStream(out);
                data.writeInt(GUARD_MAGIC); data.writeInt(cx); data.writeInt(cz); data.writeLong(world.getSeed());
                data.flush(); out.getFD().sync();
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    // ------------------------------------------------------------------ building

    private final class MusePopulator extends BlockPopulator {
        private final Context ctx;
        MusePopulator(Context ctx) { this.ctx = ctx; }
        @Override public void populate(World world, Random ignored, Chunk chunk) {
            if (!isEnabled() || catalog == null) return;
            int cx = chunk.getX(), cz = chunk.getZ();
            Ledger.Record r;
            try { r = ctx.ledger.get(world.getSeed(), Math.floorDiv(cx, Planner.CELL_CHUNKS), Math.floorDiv(cz, Planner.CELL_CHUNKS), planner, ctx.ground); }
            catch (IOException e) { fail("ledger", cx, cz, e); return; }
            if (r.plan == null || !r.plan.intersects(cx, cz) || r.abandoned) return;
            if (!r.retrofit && touchesExisting(ctx, r.plan)) { r.retrofit = true; queueRetrofit(ctx, r); }
            build(ctx, r, chunk, false);
        }
    }

    private boolean touchesExisting(Context ctx, Planner.Plan p) {
        for (int cx = Math.floorDiv(p.x, 16); cx <= Math.floorDiv(p.x + p.design.width() - 1, 16); cx++)
            for (int cz = Math.floorDiv(p.z, 16); cz <= Math.floorDiv(p.z + p.design.depth() - 1, 16); cz++)
                if (ctx.boundary.contains(cx, cz)) return true;
        return false;
    }

    private boolean build(Context ctx, Ledger.Record r, Chunk chunk, boolean retrofit) {
        int cx = chunk.getX(), cz = chunk.getZ();
        String tile = (cx - Math.floorDiv(r.plan.x, 16)) + "," + (cz - Math.floorDiv(r.plan.z, 16));
        if (r.stamped.contains(tile)) return true;
        try {
            guard(ctx.world, cx, cz);
            chunk.getBlock(0, 0, 0).setMetadata(CHUNK_MARKER, new FixedMetadataValue(this, true));
            long began = System.nanoTime();
            Stamper.Result res = Stamper.stamp(this, chunk, r.plan, pack.blocks(r.plan.design), r);
            r.stamped.add(tile);
            ctx.ledger.save(r);
            long ms = (System.nanoTime() - began) / 1000000;
            if (ms > 250) getLogger().warning("MUSE_SLOW_TILE site=" + r.site + " tile=" + tile + " ms=" + ms);
            if (retrofit) retrofitTiles++; else stampedTiles++;
            if (res.chests + res.vault + res.spawners > 0 || retrofit)
                getLogger().info("MUSE_TILE site=" + r.site + " tile=" + tile + " blocks=" + res.changed + " chests=" + res.chests
                    + " vault=" + res.vault + " spawners=" + res.spawners + (retrofit ? " retrofit=1" : ""));
            return true;
        } catch (Exception e) {
            fail(r.site, cx, cz, e);
            return false;
        }
    }

    private void fail(String what, int cx, int cz, Exception e) {
        tileFailures++;
        getLogger().severe("MUSE_TILE_FAILED what=" + what + " chunk=" + cx + "," + cz + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
        if (tileFailures >= 25) {
            getLogger().severe("MUSE_GENERATION_STOPPED after " + tileFailures + " failures; encounters stay on");
            for (Map.Entry<UUID, BlockPopulator> en : populators.entrySet()) {
                World w = Bukkit.getWorld(en.getKey());
                if (w != null) later(1, () -> w.getPopulators().remove(en.getValue()));
            }
        }
    }

    private void queueRetrofit(Context ctx, Ledger.Record r) {
        if (!retrofitEnabled || r.plan == null) return;
        if (ctx.queued.add(r.plan.key())) ctx.retrofit.add(r);
    }

    /** One bounded step of background work: decide an existing-land cell, or build one retrofit tile. */
    private void retrofitStep(Context ctx) throws IOException {
        if (!ctx.retrofit.isEmpty()) {
            Ledger.Record r = ctx.retrofit.peek();
            Planner.Plan p = r.plan;
            if (r.abandoned || r.retrofitDone) { ctx.retrofit.poll(); return; }
            // Never while anyone is near: the site appears in land no one is looking at.
            for (Player pl : ctx.world.getPlayers())
                if (pl.getLocation().getX() > p.x - 112 && pl.getLocation().getX() < p.x + p.design.width() + 112
                        && pl.getLocation().getZ() > p.z - 112 && pl.getLocation().getZ() < p.z + p.design.depth() + 112) {
                    ctx.retrofit.add(ctx.retrofit.poll());   // try the next site; this one later
                    return;
                }
            List<int[]> todo = new ArrayList<>();
            for (int cx = Math.floorDiv(p.x, 16); cx <= Math.floorDiv(p.x + p.design.width() - 1, 16); cx++)
                for (int cz = Math.floorDiv(p.z, 16); cz <= Math.floorDiv(p.z + p.design.depth() - 1, 16); cz++) {
                    if (!ctx.boundary.contains(cx, cz)) continue;
                    String tile = (cx - Math.floorDiv(p.x, 16)) + "," + (cz - Math.floorDiv(p.z, 16));
                    if (!r.stamped.contains(tile)) todo.add(new int[]{cx, cz});
                }
            if (todo.isEmpty()) {
                r.retrofitDone = true; ctx.ledger.save(r); ctx.retrofit.poll();
                getLogger().info("MUSE_RETROFIT_DONE site=" + r.site + " at " + p.x + "," + p.y + "," + p.z);
                return;
            }
            boolean first = todo.size() == countExisting(ctx, p);
            if (first) for (int[] c : todo)                // all-or-nothing: re-check every chunk before the first block
                if (inhabited(ctx.world, c[0], c[1]) > inhabitedLimit || guarded(ctx.world, c[0], c[1])) {
                    r.abandoned = true; ctx.ledger.save(r); ctx.retrofit.poll();
                    getLogger().info("MUSE_RETROFIT_ABANDONED site=" + r.site + " reason=inhabited");
                    return;
                }
            int[] c = todo.get(0);
            boolean wasLoaded = ctx.world.isChunkLoaded(c[0], c[1]);
            Chunk chunk = ctx.world.getChunkAt(c[0], c[1]);
            if (build(ctx, r, chunk, true)) ctx.world.refreshChunk(c[0], c[1]);
            else { r.abandoned = true; ctx.ledger.save(r); ctx.retrofit.poll(); }
            if (!wasLoaded) ctx.world.unloadChunkRequest(c[0], c[1]);
            return;
        }
        if (!ctx.undecided.isEmpty()) {
            long[] cell = ctx.undecided.poll();
            Ledger.Record r = ctx.ledger.get(ctx.world.getSeed(), (int) cell[0], (int) cell[1], planner, ctx.ground);
            if (r.plan != null && !r.abandoned && !r.retrofitDone && touchesExisting(ctx, r.plan)) {
                if (!r.retrofit) { r.retrofit = true; ctx.ledger.save(r); }
                queueRetrofit(ctx, r);
            }
        }
    }

    private int countExisting(Context ctx, Planner.Plan p) {
        int n = 0;
        for (int cx = Math.floorDiv(p.x, 16); cx <= Math.floorDiv(p.x + p.design.width() - 1, 16); cx++)
            for (int cz = Math.floorDiv(p.z, 16); cz <= Math.floorDiv(p.z + p.design.depth() - 1, 16); cz++)
                if (ctx.boundary.contains(cx, cz)) n++;
        return n;
    }

    // ------------------------------------------------------------------ sites and encounters

    /** The built site containing this location (margin in blocks), or null. */
    Ledger.Record siteAt(Location at) { return siteAt(at, 0); }
    Ledger.Record siteAt(Location at, int margin) {
        Context ctx = worlds.get(at.getWorld().getUID());
        if (ctx == null) return null;
        int cellX = Math.floorDiv(at.getBlockX(), Planner.CELL_CHUNKS * 16), cellZ = Math.floorDiv(at.getBlockZ(), Planner.CELL_CHUNKS * 16);
        Ledger.Record r = ctx.ledger.peek(ctx.world.getSeed(), cellX, cellZ);
        if (r == null || r.plan == null || r.stamped.isEmpty()) return null;
        return r.plan.contains(at.getBlockX(), at.getBlockY(), at.getBlockZ(), margin) ? r : null;
    }

    int minions(String siteKey) { Site s = sites.get(siteKey); return s == null ? 0 : s.mobs.size(); }
    void adopt(String siteKey, LivingEntity mob) { sites.computeIfAbsent(siteKey, k -> new Site()).mobs.add(mob.getUniqueId()); }

    private void tick() {
        ticks += 10;
        temp.tick(ticks);
        if (catalog == null) return;
        bosses.tick();
        for (Context ctx : worlds.values()) {
            long budget = System.nanoTime() + 4_000_000L;             // at most ~4 ms of background work per 10 ticks
            try { do { retrofitStep(ctx); } while (System.nanoTime() < budget && (!ctx.undecided.isEmpty()) && ctx.retrofit.isEmpty()); }
            catch (Exception e) { getLogger().warning("MUSE_RETROFIT_STEP_FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
        }
        if (ticks % 20 == 0 && encountersEnabled) encounters();
    }

    private void encounters() {
        long now = System.currentTimeMillis();
        Map<String, List<Player>> inside = new HashMap<>();
        Map<String, Ledger.Record> records = new HashMap<>();
        for (Context ctx : worlds.values()) for (Player p : ctx.world.getPlayers()) {
            if (!vulnerable(p)) continue;
            Ledger.Record r = siteAt(p.getLocation(), 3);
            if (r == null) continue;
            inside.computeIfAbsent(r.plan.key(), k -> new ArrayList<>()).add(p);
            records.put(r.plan.key(), r);
        }
        for (Map.Entry<String, List<Player>> e : inside.entrySet()) {
            String key = e.getKey();
            Ledger.Record r = records.get(key);
            Site s = sites.computeIfAbsent(key, k -> new Site());
            s.lastSeen = now;
            for (Player p : e.getValue()) {
                if (s.announced.add(p.getUniqueId())) announce(p, r);
                bosses.offer(r, p);
            }
            Player first = e.getValue().get(0);
            if (!s.spawned && now >= s.armedAt && first.getWorld().getDifficulty() != org.bukkit.Difficulty.PEACEFUL) spawnGarrison(r, s, first);
            List<Catalog.Hazard> hz = r.plan.design.danger.hazards;
            for (int i = 0; i < hz.size() && i < 2; i++) {
                if (now < s.hazardNext[i]) continue;
                Player target = e.getValue().get(random.nextInt(e.getValue().size()));
                try { hazards.fire(hz.get(i), target, r.plan, key); }
                catch (RuntimeException ex) { getLogger().warning("MUSE_HAZARD_FAILED kind=" + hz.get(i).kind + " " + ex.getClass().getSimpleName()); }
                s.hazardNext[i] = now + 7000 + random.nextInt(8000) - Math.min(3000, r.plan.design.tier * 500L);
            }
        }
        // Retire garrisons nobody is fighting; re-arm cleared sites after a while.
        for (Iterator<Map.Entry<String, Site>> it = sites.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Site> en = it.next();
            Site s = en.getValue();
            s.mobs.removeIf(id -> { Entity m = Bukkit.getEntity(id); return m == null || m.isDead() || !m.isValid(); });
            if (s.spawned && s.mobs.isEmpty()) { s.spawned = false; s.armedAt = now + getConfig().getLong("rearm-minutes", 20) * 60000L; }
            if (now - s.lastSeen > 90000) {
                for (UUID id : s.mobs) { Entity m = Bukkit.getEntity(id); if (m != null) m.remove(); }
                s.mobs.clear();
                if (s.spawned) { s.spawned = false; s.armedAt = now; }
                s.announced.clear();
                if (!bosses.active(en.getKey()) && s.armedAt <= now) it.remove();
            }
        }
        castSignatures();
    }

    private void announce(Player p, Ledger.Record r) {
        Catalog.Design d = r.plan.design;
        p.sendTitle(ChatColor.GOLD + d.name, ChatColor.LIGHT_PURPLE + Catalog.COLLECTION, 10, 50, 15);
        StringBuilder hz = new StringBuilder();
        for (Catalog.Hazard h : d.danger.hazards) hz.append(hz.length() == 0 ? "" : ", ").append(Hazards.label(h));
        p.sendMessage(ChatColor.DARK_RED + "☠ " + ChatColor.GRAY + "Dangers: " + ChatColor.LIGHT_PURPLE + d.danger.signature.name
            + ChatColor.GRAY + " and its garrison. Hazards: " + hz + (d.boss != null && !r.bossDefeated ? ChatColor.DARK_RED + ". Boss: " + d.boss.name : ""));
    }

    private void spawnGarrison(Ledger.Record r, Site s, Player target) {
        Planner.Plan p = r.plan;
        Catalog.Design d = p.design;
        World w = target.getWorld();
        List<Location> spots = new ArrayList<>();
        for (int[] sp : d.spots) {
            Location at = new Location(w, p.x + sp[0] + 0.5, p.y + sp[1], p.z + sp[2] + 0.5);
            double dist = at.distance(target.getLocation());
            if (dist < 6 || dist > 48) continue;
            Location f = Abilities.floor(at);
            if (f != null) spots.add(f.add(0.5, 0, 0.5));
        }
        if (spots.isEmpty()) for (int i = 0; i < 6; i++) { Location f = abilities.beside(target, 8 + random.nextInt(6)); if (f != null) spots.add(f); }
        if (spots.isEmpty()) return;
        java.util.Collections.shuffle(spots, random);
        String key = p.key();
        int k = 0, cap = 24;
        for (Catalog.Garrison g : d.danger.garrison) {
            for (int i = 0; i < g.count && s.mobs.size() < cap; i++) {
                Location at = spots.get(k++ % spots.size());
                String type = g.type;
                if (type.equals("GUARDIAN") && !nearWater(at)) type = "STRAY";
                if (type.equals("GHAST") && w.getHighestBlockYAt(at) > at.getBlockY() + 2) type = "BLAZE";
                LivingEntity m = Mobs.garrison(w, at, type, d.tier, key, target, random);
                if (m != null) s.mobs.add(m.getUniqueId());
            }
        }
        Catalog.Signature sig = d.danger.signature;
        for (int i = 0; i < sig.count; i++) {
            Location at = spots.get(k++ % spots.size());
            if (sig.type.equals("GUARDIAN") && !nearWater(at)) continue;
            LivingEntity m = Mobs.signature(w, at, sig, key, target, random, d.tier);
            if (m != null) s.mobs.add(m.getUniqueId());
        }
        s.spawned = true;
        getLogger().info("MUSE_ENCOUNTER site=" + key + " design=" + d.id + " mobs=" + s.mobs.size());
    }

    private static boolean nearWater(Location at) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 1; dy++)
            if (at.getBlock().getRelative(dx, dy, dz).isLiquid()) return true;
        return false;
    }

    private void castSignatures() {
        long now = System.currentTimeMillis();
        // Copies: a cast can summon (adopt) into these very lists.
        for (Site site : new ArrayList<>(sites.values())) {
            for (UUID id : new ArrayList<>(site.mobs)) {
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof LivingEntity) || !e.getScoreboardTags().contains(Mobs.SIGNATURE_TAG) || e.isDead()) continue;
                Long next = nextCast.get(id);
                if (next != null && now < next) continue;
                LivingEntity mob = (LivingEntity) e;
                Player target = null; double best = 20 * 20;
                for (Player p : abilities.near(mob.getLocation(), 20)) { double d = p.getLocation().distanceSquared(mob.getLocation()); if (d < best) { best = d; target = p; } }
                if (target == null) continue;
                Ledger.Record r = siteAt(mob.getLocation(), 16);
                int tier = r == null ? 3 : r.plan.design.tier;
                List<String> abil = r == null ? null : r.plan.design.danger.signature.abilities;
                if (abil == null) continue;
                String first = abil.get(random.nextInt(abil.size()));
                String other = abil.get(0).equals(first) && abil.size() > 1 ? abil.get(1) : abil.get(0);
                boolean cast = active(first) && abilities.cast(first, mob, target, tier);
                if (!cast && active(other)) cast = abilities.cast(other, mob, target, tier);
                nextCast.put(id, now + (cast ? 5000 + random.nextInt(4000) : 1500));
            }
        }
        if (nextCast.size() > 2000) nextCast.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }

    /** Passive abilities are handled by events, not cast. */
    private static boolean active(String a) {
        return !(a.equals("WITHER_TOUCH") || a.equals("THORNS") || a.equals("FROSTBOUND") || a.equals("VOLATILE") || a.equals("SPLIT"));
    }

    private List<String> signatureAbilities(Entity e) {
        if (!e.getScoreboardTags().contains(Mobs.SIGNATURE_TAG)) return null;
        Ledger.Record r = siteAt(e.getLocation(), 24);
        return r == null ? null : r.plan.design.danger.signature.abilities;
    }

    // ------------------------------------------------------------------ combat and safety events

    private static Entity source(Entity damager) {
        if (damager instanceof Projectile) {
            ProjectileSource s = ((Projectile) damager).getShooter();
            if (s instanceof Entity) return (Entity) s;
        }
        return damager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Entity attacker = source(e.getDamager()), victim = e.getEntity();
        if (Mobs.ours(attacker) && Mobs.ours(victim) && !(victim instanceof Player)) { e.setCancelled(true); return; }
        if (victim instanceof Player && Mobs.ours(attacker)) {
            List<String> a = signatureAbilities(attacker);
            if (a != null && a.contains("WITHER_TOUCH")) ((Player) victim).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
        }
        if (attacker instanceof Player && Mobs.ours(victim)) {
            List<String> a = signatureAbilities(victim);
            if (a == null) return;
            Player p = (Player) attacker;
            if (a.contains("THORNS") && e.getDamager() == p) later(1, () -> { if (p.isOnline() && !p.isDead()) p.damage(Math.min(6, e.getFinalDamage() * 0.25)); });
            if (a.contains("FROSTBOUND")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && Mobs.ours(e.getEntity()) && Mobs.ours(e.getTarget()) && !(e.getTarget() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler
    public void died(EntityDeathEvent e) {
        LivingEntity m = e.getEntity();
        if (!Mobs.ours(m) || m instanceof Player || bosses.isBoss(m)) return;
        List<String> a = signatureAbilities(m);
        if (a == null) return;
        Ledger.Record r = siteAt(m.getLocation(), 24);
        int tier = r == null ? 3 : r.plan.design.tier;
        e.setDroppedExp(30 + tier * 10);
        if (random.nextDouble() < 0.35) e.getDrops().add(Loot.smeBook(random, tier, false));
        if (random.nextDouble() < 0.04 + tier * 0.01) e.getDrops().add(Essences.item(Essences.random(random), 1));
        Location at = m.getLocation();
        if (a.contains("VOLATILE")) later(1, () -> at.getWorld().createExplosion(at.getX(), at.getY(), at.getZ(), 2.5f, false, false));
        if (a.contains("SPLIT") && r != null) {
            String type = m.getType() == EntityType.SLIME ? "SLIME" : m.getType() == EntityType.MAGMA_CUBE ? "MAGMA_CUBE" : "SILVERFISH";
            for (int i = 0; i < 2; i++) {
                LivingEntity child = Mobs.garrison(at.getWorld(), at, type, 1, r.plan.key(), m.getKiller(), random);
                if (child != null) adopt(r.plan.key(), child);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent e) {
        Entity src = e.getEntity();
        if (src == null) return;
        if (Mobs.ours(src) || src.hasMetadata(Abilities.PROJECTILE_META) || Mobs.ours(source(src))) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prime(ExplosionPrimeEvent e) {
        Entity src = e.getEntity();
        if (Mobs.ours(src) || src.hasMetadata(Abilities.PROJECTILE_META) || Mobs.ours(source(src))) e.setFire(false);
    }

    @EventHandler
    public void projectile(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!(e.getHitEntity() instanceof Player)) return;
        Player p = (Player) e.getHitEntity();
        if (pr.hasMetadata("jaspr_muse_venom")) {
            int tier = pr.getMetadata("jaspr_muse_venom").get(0).asInt();
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, tier >= 4 ? 1 : 0));
            p.damage(2);
        }
        if (pr.hasMetadata("jaspr_muse_frost")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
    }

    /** Mobs from an earlier run (or a chunk that unloaded mid-fight) are retired; their site re-arms. */
    @EventHandler
    public void chunkLoad(ChunkLoadEvent e) {
        for (Entity en : e.getChunk().getEntities()) if (Mobs.ours(en) && !(en instanceof Player) && !tracked(en)) en.remove();
    }
    @EventHandler
    public void chunkUnload(ChunkUnloadEvent e) {
        for (Entity en : e.getChunk().getEntities()) if (Mobs.ours(en) && !(en instanceof Player) && !bosses.isBoss(en)) en.remove();
    }
    private boolean tracked(Entity e) {
        String key = Mobs.siteOf(e);
        Site s = key == null ? null : sites.get(key);
        return (s != null && s.mobs.contains(e.getUniqueId())) || bosses.isBoss(e);
    }

    // ------------------------------------------------------------------ /where and commands

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void where(PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().trim();
        if (!c.equalsIgnoreCase("/where") && !c.equalsIgnoreCase("/survey") && !c.equalsIgnoreCase("/biome")) return;
        Ledger.Record r = siteAt(e.getPlayer().getLocation());
        if (r == null) return;
        e.setCancelled(true);
        Catalog.Design d = r.plan.design;
        Player p = e.getPlayer();
        p.sendMessage(ChatColor.GREEN + "Structure " + ChatColor.WHITE + d.name + ChatColor.GOLD + " (" + Catalog.COLLECTION + ")");
        p.sendMessage(ChatColor.GRAY + "ID " + d.id + " | Tier " + d.tier + " | " + d.theme + " | " + d.habitat + " | origin "
            + r.plan.x + "," + r.plan.y + "," + r.plan.z + " | size " + d.width() + "x" + d.height() + "x" + d.depth()
            + (r.retrofit ? " | built into existing land" : ""));
        StringBuilder g = new StringBuilder();
        for (Catalog.Garrison x : d.danger.garrison) g.append(g.length() == 0 ? "" : ", ").append(x.count).append("x ").append(pretty(x.type)).append(" (").append(x.realm).append(')');
        StringBuilder hz = new StringBuilder();
        for (Catalog.Hazard h : d.danger.hazards) hz.append(hz.length() == 0 ? "" : ", ").append(Hazards.label(h));
        p.sendMessage(ChatColor.DARK_RED + "Dangers: " + ChatColor.LIGHT_PURPLE + d.danger.signature.name + ChatColor.GRAY + " (" + pretty(d.danger.signature.type)
            + ": " + pretty(d.danger.signature.abilities.get(0)) + ", " + pretty(d.danger.signature.abilities.get(1)) + "); " + g + "; hazards: " + hz);
        p.sendMessage(ChatColor.AQUA + "Special loot: " + ChatColor.LIGHT_PURPLE + d.special.name + ChatColor.GRAY + " in the vault"
            + (d.boss != null ? ChatColor.DARK_RED + " | Boss: " + d.boss.name + (r.bossDefeated ? ChatColor.GREEN + " (defeated)" : "") : ""));
    }

    private String importerState(World w) {
        try { return importer.context(w) == null ? "absent" : importer.healthy() ? "linked" : "unreadable"; }
        catch (ReflectiveOperationException e) { return "unreadable"; }
    }

    static String pretty(String s) { String t = s.toLowerCase().replace('_', ' '); return Character.toUpperCase(t.charAt(0)) + t.substring(1); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("essences")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "Essences: " + essences.summary(((Player) sender).getUniqueId()));
            return true;
        }
        if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "Operators only."); return true; }
        if (catalog == null) { sender.sendMessage(ChatColor.RED + "Muse+GLM_Maps is not running (see the server log)."); return true; }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        if (sub.equals("status")) {
            for (Context ctx : worlds.values()) {
                List<Ledger.Record> placed = ctx.ledger.placed();
                int built = 0, retro = 0, bossesLeft = 0;
                for (Ledger.Record r : placed) { if (!r.stamped.isEmpty()) built++; if (r.retrofitDone) retro++; if (r.plan.design.boss != null && !r.bossDefeated) bossesLeft++; }
                sender.sendMessage(ChatColor.GOLD + "Muse+GLM_Maps " + ChatColor.GRAY + "world=" + ctx.world.getName() + " sitesPlanned=" + placed.size()
                    + " built=" + built + " retrofitted=" + retro + " retrofitQueue=" + ctx.retrofit.size() + " undecidedCells=" + ctx.undecided.size()
                    + " bossesWaiting=" + bossesLeft + " activeEncounters=" + sites.size() + " activeBosses=" + bosses.active()
                    + " importer=" + importerState(ctx.world));
            }
            return true;
        }
        if (sub.equals("nearest") && sender instanceof Player) {
            Player p = (Player) sender;
            Context ctx = worlds.get(p.getWorld().getUID());
            if (ctx == null) return true;
            Ledger.Record best = null; double bd = Double.MAX_VALUE;
            for (Ledger.Record r : ctx.ledger.placed()) {
                if (r.stamped.isEmpty()) continue;
                double d = Math.pow(r.plan.x - p.getLocation().getX(), 2) + Math.pow(r.plan.z - p.getLocation().getZ(), 2);
                if (d < bd) { bd = d; best = r; }
            }
            sender.sendMessage(best == null ? ChatColor.GRAY + "No built Muse+GLM_Maps site known yet."
                : ChatColor.GOLD + best.plan.design.name + ChatColor.GRAY + " at " + best.plan.x + "," + best.plan.y + "," + best.plan.z
                    + " (" + (int) Math.sqrt(bd) + " blocks)");
            return true;
        }
        if (sub.equals("essence") && args.length >= 2 && sender instanceof Player) {
            try { ((Player) sender).getInventory().addItem(Essences.item(Essences.Kind.valueOf(args[1].toUpperCase()), 1)); }
            catch (IllegalArgumentException ex) { sender.sendMessage("Kinds: vitality, might, celerity, bulwark, resolve, fortune"); }
            return true;
        }
        if (Boolean.getBoolean("jaspr.muse.fixture") && fixture(sender, sub, args)) return true;
        sender.sendMessage(ChatColor.GRAY + "/musemaps status | nearest | essence <kind>");
        return true;
    }

    /** The real admission rule minus the existing-chunk and importer checks (a pure grid census). */
    private Planner.Ground censusGround(Context ctx, World world) {
        return new Planner.Ground() {
            @Override public int surface(int x, int z) { return ctx.terrain.sample(x, z).y; }
            @Override public boolean permits(int x, int y, int z, Catalog.Design d) {
                int w = d.width(), dp = d.depth();
                if (x < SPAWN_CORE && x + w > -SPAWN_CORE && z < SPAWN_CORE && z + dp > -SPAWN_CORE) return false;
                try {
                    if (Planner.big(d)) { Pack.Blocks occ = pack.blocks(d); return !expeditionEnvelope(world.getSeed(), x, y, z, w, dp, occ) && !claims.envelopeConflicts(ctx.terrain, x, y, z, d, occ); }
                    return !conflictsWithExpeditions(world.getSeed(), x, z, w, dp) && !claims.conflicts(ctx.terrain, x, y, z, d);
                } catch (Exception e) { return false; }
            }
        };
    }

    /** Test-server-only tools (-Djaspr.muse.fixture=true): census of the grid, forced placement of one design. */
    private boolean fixture(CommandSender sender, String sub, String[] args) {
        World world = Bukkit.getWorld("world");
        Context ctx = world == null ? null : worlds.get(world.getUID());
        if (ctx == null) return false;
        if (sub.equals("census")) {
            int n = args.length > 1 ? Integer.parseInt(args[1]) : 12, plans = 0, cells = 0, big = 0;
            Map<String, Integer> byDesign = new HashMap<>();
            Map<String, Integer> byHabitat = new HashMap<>();
            Planner.Ground free = censusGround(ctx, world);
            long began = System.currentTimeMillis();
            for (int a = -n; a < n; a++) for (int b = -n; b < n; b++) {
                cells++;
                Planner.Plan p = planner.choose(world.getSeed(), a, b, free);
                if (p == null) continue;
                plans++;
                if (Planner.big(p.design)) big++;
                byDesign.merge(p.design.id, 1, Integer::sum);
                byHabitat.merge(p.design.habitat, 1, Integer::sum);
            }
            double area = cells * Math.pow(Planner.CELL_CHUNKS * 16 / 1000.0, 2);
            String line = "MUSE_CENSUS cells=" + cells + " plans=" + plans + " big=" + big + " designsSeen=" + byDesign.size()
                + " per1000x1000=" + String.format("%.2f", plans / area) + " spacing=1/" + (int) Math.sqrt(1e6 * area / Math.max(1, plans)) + "blocks"
                + " habitats=" + byHabitat + " ms=" + (System.currentTimeMillis() - began);
            getLogger().info(line); sender.sendMessage(line);
            return true;
        }
        if (sub.equals("calibrate")) {
            int m = args.length > 1 ? Integer.parseInt(args[1]) : 40;
            Planner.Ground free = censusGround(ctx, world);
            StringBuilder json = new StringBuilder("{");
            Random pick = new Random(12345);
            long began = System.currentTimeMillis();
            for (Catalog.Design d : catalog.all()) {
                int ok = 0;
                for (int i = 0; i < m; i++) {
                    int a = pick.nextInt(80) - 40, b = pick.nextInt(80) - 40;
                    if (Math.abs(a) < 3 && Math.abs(b) < 3) { a += 6; }
                    if (planner.tryDesign(world.getSeed(), a, b, d, free) != null) ok++;
                }
                json.append(json.length() > 1 ? "," : "").append('"').append(d.id).append("\":").append(ok / (double) m);
            }
            json.append('}');
            try { Files.write(getDataFolder().toPath().resolve("calibration.json"), json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            catch (IOException e) { sender.sendMessage("FAIL " + e); }
            String line = "MUSE_CALIBRATED samples=" + m + " ms=" + (System.currentTimeMillis() - began);
            getLogger().info(line); sender.sendMessage(line);
            return true;
        }
        if (sub.equals("why") && args.length > 2) {
            Catalog.Design d = catalog.get(args[1].startsWith("muse:") ? args[1] : "muse:" + args[1]);
            int m = Integer.parseInt(args[2]), terrain = 0, spawn = 0, exped = 0, claim = 0, ok = 0;
            Random pick = new Random(99);
            for (int i = 0; i < m; i++) {
                int x = (pick.nextInt(2400) - 1200) * 16, z = (pick.nextInt(2400) - 1200) * 16;
                int y = Planner.originY(d, x, z, ctx.ground);
                if (y < 1 || y + d.height() > 256) { terrain++; continue; }
                if (x < SPAWN_CORE && x + d.width() > -SPAWN_CORE && z < SPAWN_CORE && z + d.depth() > -SPAWN_CORE) { spawn++; continue; }
                try {
                    Pack.Blocks occ = pack.blocks(d);
                    if (Planner.big(d) ? expeditionEnvelope(world.getSeed(), x, y, z, d.width(), d.depth(), occ) : conflictsWithExpeditions(world.getSeed(), x, z, d.width(), d.depth())) { exped++; continue; }
                    if (Planner.big(d) ? claims.envelopeConflicts(ctx.terrain, x, y, z, d, occ) : claims.conflicts(ctx.terrain, x, y, z, d)) { claim++; continue; }
                } catch (Exception e) { claim++; continue; }
                ok++;
            }
            String line = "MUSE_WHY design=" + d.id + " samples=" + m + " terrain=" + terrain + " spawn=" + spawn + " expeditions=" + exped + " claims=" + claim + " ok=" + ok;
            getLogger().info(line); sender.sendMessage(line);
            return true;
        }
        if (sub.equals("locate") && sender instanceof Player) {
            Player p = (Player) sender;
            int cx0 = Math.floorDiv(p.getLocation().getBlockX(), Planner.CELL_CHUNKS * 16), cz0 = Math.floorDiv(p.getLocation().getBlockZ(), Planner.CELL_CHUNKS * 16);
            try {
                for (int ring = 0; ring <= 6; ring++) for (int a = -ring; a <= ring; a++) for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    Ledger.Record r = ctx.ledger.get(world.getSeed(), cx0 + a, cz0 + b, planner, ctx.ground);
                    if (r.plan == null || !r.stamped.isEmpty()) continue;
                    String line = "MUSE_FIXTURE_LOCATED design=" + r.site + " at " + r.plan.x + "," + r.plan.y + "," + r.plan.z
                        + " centre=" + (r.plan.x + r.plan.design.width() / 2) + "," + (r.plan.z + r.plan.design.depth() / 2);
                    getLogger().info(line); sender.sendMessage(line);
                    return true;
                }
            } catch (IOException e) { sender.sendMessage("FAIL " + e); return true; }
            sender.sendMessage("MUSE_FIXTURE_LOCATED none");
            return true;
        }
        if (sub.equals("place") && args.length > 1 && sender instanceof Player) {
            Player p = (Player) sender;
            Catalog.Design d = args[1].equals("random") ? catalog.all().get(random.nextInt(catalog.all().size())) : catalog.get(args[1].startsWith("muse:") ? args[1] : "muse:" + args[1]);
            if (d == null) { sender.sendMessage("Unknown design"); return true; }
            int cellX = Math.floorDiv(p.getLocation().getBlockX(), Planner.CELL_CHUNKS * 16), cellZ = Math.floorDiv(p.getLocation().getBlockZ(), Planner.CELL_CHUNKS * 16);
            int x = Math.floorDiv(p.getLocation().getBlockX(), 16) * 16, z = Math.floorDiv(p.getLocation().getBlockZ(), 16) * 16;
            x = Math.min(x, (cellX * Planner.CELL_CHUNKS + Planner.CELL_CHUNKS - d.widthChunks()) * 16);
            z = Math.min(z, (cellZ * Planner.CELL_CHUNKS + Planner.CELL_CHUNKS - d.depthChunks()) * 16);
            int y = Planner.originY(d, x, z, ctx.ground);
            if (y < 1 || y + d.height() > 256) y = Math.max(1, Math.min(256 - d.height(), p.getLocation().getBlockY() - d.surfaceAnchor));
            try {
                Ledger.Record r = ctx.ledger.force(world.getSeed(), cellX, cellZ, d, x, y, z);
                for (int cx = x >> 4; cx <= (x + d.width() - 1) >> 4; cx++) for (int cz = z >> 4; cz <= (z + d.depth() - 1) >> 4; cz++)
                    build(ctx, r, world.getChunkAt(cx, cz), true);
                for (int cx = x >> 4; cx <= (x + d.width() - 1) >> 4; cx++) for (int cz = z >> 4; cz <= (z + d.depth() - 1) >> 4; cz++) world.refreshChunk(cx, cz);
                String line = "MUSE_FIXTURE_PLACED design=" + d.id + " at " + x + "," + y + "," + z + " tiles=" + r.stamped.size();
                getLogger().info(line); sender.sendMessage(line);
            } catch (IOException e) { sender.sendMessage("FAIL " + e); }
            return true;
        }
        return false;
    }

    Catalog catalog() { return catalog; }
}
