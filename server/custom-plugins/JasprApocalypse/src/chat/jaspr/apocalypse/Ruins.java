package chat.jaspr.apocalypse;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.CRC32;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * New-terrain-only ruins for Bukkit/Paper 1.12.2; all Bukkit access is on the
 * server thread. The populator only offers its own chunk, and the tick worker
 * waits two ticks for vanilla/other population to finish. There is deliberately
 * no ChunkLoad handler, chunk loading, world saving, regeneration, or retrofit.
 *
 * Defaults (all under ruins.): enabled=true, edits-per-tick=100 (1..150),
 * max-pending-plans=32 (1..128), max-plan-edits=3200 (512..4096),
 * max-ledger-entries=32768 (1..262144), max-slope=2 (0..2),
 * region-chunks=24 (16..128), district-radius-chunks=7,
 * spacing-chunks=2 (2..8), district-chance=0.60, district-density=0.80,
 * isolated-chance=0.008, relic-cache-chance=0.25, scrap-chance=0.20.
 * Region radius is clamped to 2..(region-chunks/2-2). Changes take effect on
 * start(); start/stop and diagnostic reads belong on the server thread.
 *
 * ruins-ledger-v1.bin lives in the plugin data directory, NOT the world.
 * A forced reservation record precedes ALL edits; a separate completion record
 * follows all edits and loot. An interrupted reservation is never replayed.
 * This deliberately prefers a missing/partial ruin over duplicated loot, even
 * if the server loses unsaved chunks. The bounded ledger never evicts markers;
 * capacity/corruption/I/O errors disable new builds, with a warning. Do not
 * delete this journal while reusing the associated worlds. No world save is
 * forced, and incomplete structures are neither resumed nor rolled back.
 */
@SuppressWarnings("deprecation")
public final class Ruins implements Listener {
    private final ApocalypsePlugin plugin;
    private final LinkedHashMap<Key, Candidate> waiting = new LinkedHashMap<Key, Candidate>();
    private final LinkedHashMap<Key, Boolean> recent = new LinkedHashMap<Key, Boolean>();
    private final Set<World> attached = new HashSet<World>();
    private final BlockPopulator populator = new BlockPopulator() {
        @Override public void populate(World world, Random ignored, Chunk source) {
            // Do not touch Bukkit from a nonstandard asynchronous generator.
            if (Bukkit.isPrimaryThread()) offer(source);
        }
    };
    private Settings settings;
    private Ledger ledger;
    private ThreadPoolExecutor io;
    private BukkitTask task;
    private Work active;
    private boolean running;
    private boolean stopped;
    private boolean failed;
    private long tick;

    public Ruins(ApocalypsePlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        this.plugin = plugin;
    }

    public void start() {
        requireMainThread();
        if (running || failed) return;
        // A new instance after stop avoids overlapping journal writers on reload.
        if (stopped) throw new IllegalStateException("Create a new Ruins instance after stop()");
        settings = new Settings(plugin.getConfig());
        if (!settings.enabled) return;
        try {
            ledger = new Ledger(new File(plugin.getDataFolder(), "ruins-ledger-v1.bin"), settings.ledgerLimit);
        } catch (IOException ex) {
            fail("Cannot read the ruins journal; generation is disabled", ex);
            return;
        }
        io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2), runnable -> {
                    Thread thread = new Thread(runnable, "JasprApocalypse-ruins-journal");
                    thread.setDaemon(true);
                    return thread;
                });
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) attach(world);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try { step(); }
            catch (RuntimeException ex) { fail("Ruins worker failed; generation is disabled", ex); }
        }, 1L, 1L);
    }

    public void stop() {
        requireMainThread();
        stopped = true;
        running = false;
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        for (World world : attached) world.getPopulators().remove(populator);
        attached.clear();
        waiting.clear();
        recent.clear();
        active = null;
        // Finish at most one already-submitted durable record; never edit blocks.
        if (io != null) io.shutdown();
    }

    public boolean isBusy() { return pendingPlans() != 0; }
    public int pendingPlans() { return waiting.size() + (active == null ? 0 : 1); }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Ruins lifecycle requires the server thread");
    }

    private boolean eligible(World world) {
        return world.getEnvironment() == World.Environment.NORMAL && plugin.enabledWorld(world);
    }

    private void attach(World world) {
        if (running && !failed && eligible(world) && attached.add(world)) world.getPopulators().add(populator);
    }

    @EventHandler public void onWorldInit(WorldInitEvent event) { attach(event.getWorld()); }
    @EventHandler public void onWorldLoad(WorldLoadEvent event) { attach(event.getWorld()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPopulate(ChunkPopulateEvent event) {
        if (Bukkit.isPrimaryThread()) offer(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) { abandon(Key.of(event.getChunk())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        if (attached.remove(world)) world.getPopulators().remove(populator);
        Iterator<Key> iterator = waiting.keySet().iterator();
        while (iterator.hasNext()) if (iterator.next().world.equals(world.getUID())) iterator.remove();
        if (active != null && active.candidate.key.world.equals(world.getUID())) active.aborted = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { abandon(blockKey(event.getBlockPlaced())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { abandon(blockKey(event.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) abandon(blockKey(block));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) abandon(blockKey(block));
    }

    private static Key blockKey(Block block) {
        return new Key(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private void abandon(Key key) {
        if (waiting.remove(key) != null) remember(key);
        if (active != null && active.candidate.key.equals(key)) active.aborted = true;
    }

    private void remember(Key key) {
        recent.put(key, Boolean.TRUE);
        if (recent.size() > settings.pending * 4) recent.remove(recent.keySet().iterator().next());
    }

    private void offer(Chunk chunk) {
        if (!running || failed || !eligible(chunk.getWorld())) return;
        Key key = Key.of(chunk);
        if (recent.containsKey(key) || waiting.containsKey(key) || ledger.contains(key)
                || (active != null && active.candidate.key.equals(key))) return;
        if (!selected(chunk.getWorld().getSeed(), key.x, key.z, settings)) return;
        remember(key);
        if (pendingPlans() >= settings.pending) return; // Backpressure: no retroactive retry.
        waiting.put(key, new Candidate(key, chunk, tick + 2));
    }

    private void step() {
        tick++;
        if (!running || failed) return;
        if (active == null) {
            if (waiting.isEmpty()) return;
            Candidate candidate = waiting.values().iterator().next();
            if (candidate.readyAt > tick) return;
            waiting.remove(candidate.key);
            if (!safe(candidate)) return;
            long seed = chunkSeed(candidate.chunk.getWorld().getSeed(), candidate.key.x, candidate.key.z);
            Random random = new Random(seed);
            Family family = family(random.nextInt(100));
            Site site = inspect(candidate.chunk.getChunkSnapshot(true, true, false),
                    candidate.chunk.getWorld().getMaxHeight(), family == Family.BUNKER, settings.slope);
            if (site == null || candidate.chunk.getTileEntities().length != 0) return;
            boolean relic = (family == Family.BUNKER || family == Family.SHRINE)
                    && random.nextDouble() < settings.relicChance;
            Builder builder = blueprint(site, random, family, relic, random.nextInt(4), random.nextInt(2));
            List<Edit> edits = builder.edits();
            if (edits.isEmpty() || edits.size() > settings.maxEdits || !builder.validCaches()) return;
            Work work = new Work(candidate, edits, seed);
            active = work;
            // The only background work is bounded journal I/O; no Bukkit objects are accessed there.
            work.record = io.submit(() -> { ledger.reserve(candidate.key); return null; });
            return;
        }
        Work work = active;
        if (work.record != null) {
            if (!work.record.isDone()) return;
            try { work.record.get(); }
            catch (Exception ex) { fail("Cannot persist ruins journal; generation is disabled", ex); return; }
            work.record = null;
            if (work.finishing || work.aborted) { active = null; return; }
        }
        if (work.aborted || !safe(work.candidate)) { active = null; return; }
        int budget = settings.editsPerTick;
        // Reads are bounded as well. A final validation pass must precede loot.
        while (budget > 0 && work.cursor < work.edits.size()) {
            Edit edit = work.edits.get(work.cursor);
            if (edit.cache && !work.verified) {
                if (work.verifyCursor == 0) restoreRails(work);
                int end = Math.min(work.cursor, work.verifyCursor + 150);
                for (; work.verifyCursor < end; work.verifyCursor++) {
                    Edit previous = work.edits.get(work.verifyCursor);
                    Block block = work.candidate.chunk.getBlock(previous.x, previous.y, previous.z);
                    boolean grounded = previous.type != Material.RAILS || work.candidate.chunk
                            .getBlock(previous.x, previous.y - 1, previous.z).getType().isSolid();
                    if (!matchesPlaced(previous, block.getType(), block.getData(), grounded)) {
                        active = null;
                        return;
                    }
                }
                if (work.verifyCursor < work.cursor) return;
                work.verified = true;
            }
            Block block = work.candidate.chunk.getBlock(edit.x, edit.y, edit.z);
            if (!work.cachePlaced && (block.getTypeId() != edit.before || block.getData() != edit.beforeData)) {
                active = null; // Someone/another plugin changed the site: never overwrite it.
                return;
            }
            if (edit.cache) {
                // Placement and tile-state update each consume one block edit. With a
                // budget of one, the empty chest remains empty until the next tick.
                if (!work.cachePlaced) {
                    if (!block.setTypeIdAndData(Material.CHEST.getId(), edit.data, false)) { active = null; return; }
                    work.cachePlaced = true;
                    budget--;
                    continue;
                }
                if (!finishCache(work, edit, block)) { active = null; return; }
                work.cachePlaced = false;
                budget--;
            } else {
                if (!block.setTypeIdAndData(edit.type.getId(), edit.data, false)) {
                    active = null;
                    return;
                }
                budget--;
            }
            work.cursor++;
        }
        if (work.cursor == work.edits.size()) {
            work.finishing = true;
            work.record = io.submit(() -> { ledger.complete(work.candidate.key); return null; });
        }
    }

    /**
     * Audit: placing a rail re-shapes the adjoining rails (even without physics), so the salvage
     * siding's parallel lines end in hairpin corners. Once every rail is down (rails are phase 2,
     * the cache phase 3), write each rail's planned shape back as a data-only change, which does
     * not re-run the placement shaping. Bounded by the plan's rail count; same block type only.
     */
    private static void restoreRails(Work work) {
        for (Edit rail : work.edits) {
            if (rail.type != Material.RAILS) continue;
            Block block = work.candidate.chunk.getBlock(rail.x, rail.y, rail.z);
            if (block.getType() == Material.RAILS && block.getData() != rail.data) block.setData(rail.data, false);
        }
    }

    static boolean matchesPlaced(Edit edit, Material type, byte data, boolean grounded) {
        if (type != edit.type) return false;
        // BlockMinecartTrack reconnects adjoining rails during placement, even
        // with applyPhysics=false. Parallel salvage tracks acquire native flat
        // corners (6..9); that is not interference with the completed structure.
        // Accept only valid native shapes of the SAME ordinary rail material,
        // with solid support still present. Other metadata and original-terrain
        // checks remain exact; missing/replaced/unsupported rails still abort.
        if (type == Material.RAILS) return grounded && data >= 0 && data <= 9;
        return data == edit.data;
    }

    private boolean finishCache(Work work, Edit edit, Block block) {
        if (block.getType() != Material.CHEST || block.getData() != edit.data) return false;
        Chest chest = (Chest) block.getState();
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        ItemStack[] contents = loot(new Random(work.seed ^ 0x74656c696373L), edit.relic, settings.scrapChance);
        chest.setCustomName(edit.relic ? "Sealed Relic Cache" : "Weathered Supplies");
        // Fill the snapshot, not the live inventory: CraftChest.update() applies
        // that snapshot and would overwrite a separately filled live inventory.
        Inventory inventory = chest.getSnapshotInventory();
        inventory.setContents(contents);
        return chest.update(false, false);
    }

    private boolean safe(Candidate candidate) {
        // Never call getChunkAt/getRelative: even inspection is confined to the offered chunk.
        if (!candidate.chunk.isLoaded() || !eligible(candidate.chunk.getWorld())) return false;
        for (Player player : candidate.chunk.getWorld().getPlayers()) {
            Location location = player.getLocation();
            long x = (long) candidate.key.x * 16, z = (long) candidate.key.z * 16;
            if (location.getX() >= x - 8 && location.getX() < x + 24
                    && location.getZ() >= z - 8 && location.getZ() < z + 24) return false;
        }
        return true;
    }

    private void fail(String message, Exception ex) {
        if (!failed) plugin.getLogger().log(Level.WARNING, message, ex);
        failed = true;
        waiting.clear();
        active = null;
    }

    static ItemStack[] loot(Random random, boolean relic, double scrapChance) {
        ItemStack[] contents = new ItemStack[27];
        List<ItemStack> items = new ArrayList<ItemStack>();
        items.add(new ItemStack(Material.BREAD, 1 + random.nextInt(3)));
        Material[] supplies = { Material.COAL, Material.STRING, Material.STICK,
                Material.PAPER, Material.TORCH, Material.CARROT_ITEM };
        int supplyStacks = 2 + random.nextInt(2);
        for (int i = 0; i < supplyStacks; i++) {
            items.add(new ItemStack(supplies[random.nextInt(supplies.length)], 1 + random.nextInt(3)));
        }
        if (random.nextDouble() < scrapChance) items.add(ApocalypseItems.scrap(1));
        if (relic) items.add(ApocalypseItems.relic(1)); // Exactly one guaranteed marked relic.
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < contents.length; i++) slots.add(i);
        Collections.shuffle(slots, random);
        for (int i = 0; i < items.size(); i++) contents[slots.get(i)] = items.get(i);
        return contents; // Intentionally no guns, ammunition, or iron/diamond gear.
    }

    static final class Settings {
        final boolean enabled;
        final int editsPerTick, pending, maxEdits, ledgerLimit, slope, region, radius, spacing;
        final double districtChance, density, isolatedChance, relicChance, scrapChance;
        Settings(ConfigurationSection config) {
            enabled = config.getBoolean("ruins.enabled", true);
            editsPerTick = clamp(config.getInt("ruins.edits-per-tick", 100), 1, 150);
            pending = clamp(config.getInt("ruins.max-pending-plans", 32), 1, 128);
            maxEdits = clamp(config.getInt("ruins.max-plan-edits", 3200), 512, 4096);
            ledgerLimit = clamp(config.getInt("ruins.max-ledger-entries", 32768), 1, 262144);
            slope = clamp(config.getInt("ruins.max-slope", 2), 0, 2);
            region = clamp(config.getInt("ruins.region-chunks", 24), 16, 128);
            radius = clamp(config.getInt("ruins.district-radius-chunks", 7), 2, region / 2 - 2);
            spacing = clamp(config.getInt("ruins.spacing-chunks", 2), 2, 8);
            districtChance = chance(config, "district-chance", 0.60);
            density = chance(config, "district-density", 0.80);
            isolatedChance = chance(config, "isolated-chance", 0.008);
            relicChance = chance(config, "relic-cache-chance", 0.25);
            scrapChance = chance(config, "scrap-chance", 0.20);
        }
        private static double chance(ConfigurationSection config, String key, double fallback) {
            double value = config.getDouble("ruins." + key, fallback);
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : Math.max(0, Math.min(1, value));
        }
    }

    private static int clamp(int n, int low, int high) { return Math.max(low, Math.min(high, n)); }
    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    static long chunkSeed(long seed, int x, int z) {
        return mix(seed ^ ((long) x * 0x632be59bd9b4e019L) ^ ((long) z * 0x9e3779b97f4a7c15L));
    }
    private static double unit(long seed) { return (mix(seed) >>> 11) * 0x1.0p-53; }

    static boolean selected(long seed, int x, int z, Settings settings) {
        // A seed-shifted lattice guarantees spacing even across region boundaries.
        int phaseX = (int) Math.floorMod(mix(seed), (long) settings.spacing);
        int phaseZ = (int) Math.floorMod(mix(seed ^ 0x53495445L), (long) settings.spacing);
        if (Math.floorMod(x, settings.spacing) != phaseX || Math.floorMod(z, settings.spacing) != phaseZ) return false;
        int rx = Math.floorDiv(x, settings.region), rz = Math.floorDiv(z, settings.region);
        double probability = settings.isolatedChance;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long regionSeed = chunkSeed(seed ^ 0x6469737472696374L, rx + dx, rz + dz);
            if (unit(regionSeed) >= settings.districtChance) continue;
            double cx = ((long) rx + dx) * settings.region + settings.region * (0.35 + unit(regionSeed + 1) * 0.3);
            double cz = ((long) rz + dz) * settings.region + settings.region * (0.35 + unit(regionSeed + 2) * 0.3);
            double distance = Math.hypot(x - cx, z - cz);
            if (distance < settings.radius) {
                probability = Math.max(probability, settings.density * (1.0 - 0.70 * distance / settings.radius));
            }
        }
        return unit(chunkSeed(seed, x, z) ^ 0x7275696eL) < probability;
    }

    enum Family { TOWN, MOTEL, WATCHPOST, SHRINE, BUNKER, SALVAGE }
    static Family family(int roll) {
        return roll < 32 ? Family.TOWN : roll < 49 ? Family.MOTEL : roll < 65 ? Family.WATCHPOST
                : roll < 79 ? Family.SHRINE : roll < 87 ? Family.BUNKER : Family.SALVAGE;
    }

    static final class Site {
        final ChunkSnapshot snapshot;
        final int floor;
        final int[][] heights;
        Site(ChunkSnapshot snapshot, int floor, int[][] heights) {
            this.snapshot = snapshot;
            this.floor = floor;
            this.heights = heights;
        }
    }

    static Site inspect(ChunkSnapshot snapshot, int worldHeight, boolean bunker, int slope) {
        int height = Math.min(worldHeight, 256);
        int[][] heights = new int[16][16];
        int low = height, high = 0;
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) {
            String biome = snapshot.getBiome(x, z).name();
            if (biome.contains("OCEAN") || biome.contains("RIVER") || biome.contains("BEACH")
                    || biome.contains("SWAMP") || biome.equals("VOID")) return null;
            int y = Math.min(height - 1, snapshot.getHighestBlockYAt(x, z));
            while (y > 10 && soft(snapshot.getBlockType(x, y, z))) y--;
            if (y < 12 || y > height - 12 || !natural(snapshot.getBlockType(x, y, z))) return null;
            // Also examine space ABOVE the height map, including a tree canopy/overhang.
            for (int above = y + 1; above <= Math.min(height - 1, y + 10); above++) {
                if (!soft(snapshot.getBlockType(x, above, z))) return null;
            }
            heights[x][z] = y;
            low = Math.min(low, y);
            high = Math.max(high, y);
            if (high - low > slope) return null;
            if ((x > 1 && Math.abs(y - heights[x - 1][z]) > 1)
                    || (z > 1 && Math.abs(y - heights[x][z - 1]) > 1)) return null;
        }
        int floor = low + 1;
        // A broad 14x14 guard ring rejects caves, fluids, ores with special tiles,
        // vanilla structures, bedrock and player materials. No tree is cut down.
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) {
            for (int y = floor - (bunker ? 10 : 4); y <= heights[x][z]; y++) {
                if (!natural(snapshot.getBlockType(x, y, z))) return null;
            }
        }
        return new Site(snapshot, floor, heights);
    }

    static boolean soft(Material material) {
        return material == Material.AIR || material == Material.LONG_GRASS || material == Material.DEAD_BUSH
                || material == Material.YELLOW_FLOWER || material == Material.RED_ROSE
                || material == Material.DOUBLE_PLANT || material == Material.SNOW;
    }
    static boolean natural(Material material) {
        switch (material) {
            case STONE: case GRASS: case DIRT: case SAND: case SANDSTONE: case RED_SANDSTONE:
            case GRAVEL: case CLAY: case HARD_CLAY: case STAINED_CLAY: case SNOW_BLOCK:
            case COAL_ORE: case IRON_ORE: case GOLD_ORE: case REDSTONE_ORE: case GLOWING_REDSTONE_ORE:
            case LAPIS_ORE: case DIAMOND_ORE: case EMERALD_ORE: return true;
            default: return false;
        }
    }

    static Builder blueprint(Site site, Random random, Family family, boolean relic, int rotation, int variant) {
        Builder b = new Builder(site, random, rotation);
        switch (family) {
            case TOWN: town(b, variant); break;
            case MOTEL: motel(b, variant); break;
            case WATCHPOST: watchpost(b, variant); break;
            case SHRINE: shrine(b, variant, relic); break;
            case BUNKER: bunker(b, variant, relic); break;
            case SALVAGE: if (variant == 0) salvage(b); else waterworks(b); break;
            default: throw new AssertionError(family);
        }
        b.overgrow();
        return b;
    }

    private static void town(Builder b, int variant) {
        b.lot(3, 4, 11, 12, Material.COBBLESTONE, 0);
        b.lot(3, 2, 11, 3, Material.GRAVEL, 0);
        Material wall = variant == 0 ? Material.BRICK : Material.STAINED_CLAY;
        int color = variant == 0 ? 0 : 8;
        b.shell(3, 4, 11, 12, 4, wall, color);
        b.doorway(6, variant == 0 ? 7 : 9, 4, 3, Material.LOG, 0);
        b.clear(3, 2, 6, 3, 2, 7);
        b.clear(11, 2, 6, 11, 2, 7);
        for (int x : new int[] { 3, 11 }) {
            b.column(x, 5, 1, 3, Material.LOG, 0);
            b.column(x, 8, 1, 3, Material.LOG, 0);
            for (int z = 5; z <= 8; z++) b.put(x, 3, z, Material.LOG, 8);
        }
        // Roof fragments share anchored posts/beams; damage never leaves a floating tile.
        for (int z = 10; z <= 12; z++) {
            b.column(3, z, 1, 4, Material.LOG, 0);
            b.column(11, z, 1, 4, Material.LOG, 0);
            for (int x = 3; x <= 11; x++) b.put(x, 4, z, Material.WOOD_STEP, 0);
        }
        if (variant == 0) {
            // Audit: the back wall stands full height under the gable, so the gable rises out of the
            // wall (the roof strip butts against it) and doubles as the fireplace chimney stack.
            for (int x = 5; x <= 9; x++) { b.column(x, 12, 1, 4, wall, color); b.put(x, 5, 12, wall, color); }
            for (int x = 6; x <= 8; x++) b.put(x, 6, 12, wall, color);
            for (int z = 8; z <= 10; z++) b.put(8, 1, z, Material.WOOD, 1);
            // Audit: a stripped two-block mattress by the west wall (the cache is its bedside trunk)
            // and a wash-up cauldron at the end of the plank counter.
            b.put(4, 1, 10, Material.WOOL, 12);
            b.put(4, 1, 11, Material.WOOL, 12);
            b.put(9, 1, 11, Material.CAULDRON, 0);
        } else {
            b.column(5, 3, 1, 2, Material.FENCE, 0);
            b.column(10, 3, 1, 2, Material.FENCE, 0);
            for (int x = 5; x <= 10; x++) b.put(x, 3, 3, Material.WOOD_STEP, 1);
            for (int x = 4; x <= 6; x++) b.put(x, 1, 8, Material.WOOD, 1);
            b.put(4, 2, 8, Material.WOOD_STEP, 1);
            for (int x = 6; x <= 8; x++) b.column(x, 12, 1, 3, wall, color); // backing for the hearth
        }
        // Audit: a fireplace breast against the back wall (brick, or cobblestone in the clay house) with
        // a cold hearth slab, and fallen roof boards in the roofless front of the house.
        Material hearth = variant == 0 ? Material.BRICK : Material.COBBLESTONE;
        b.column(6, 11, 1, 3, hearth, 0);
        b.column(8, 11, 1, 3, hearth, 0);
        b.column(7, 11, 2, 3, hearth, 0);
        b.put(7, 1, 11, Material.STEP, 3);
        b.put(6, 1, 7, Material.WOOD_STEP, 0);
        b.put(9, 1, 6, Material.WOOD_STEP, 0);
        b.rubble(4, 5); b.rubble(10, 9);
        b.cache(5, 1, 10, false);
    }

    private static void motel(Builder b, int variant) {
        b.lot(2, 5, 13, 12, Material.SMOOTH_BRICK, 2);
        b.lot(2, 2, 13, 4, Material.GRAVEL, 0);
        int color = variant == 0 ? 1 : 9;
        b.shell(2, 7, 13, 12, 3, Material.STAINED_CLAY, color);
        for (int x : new int[] { 2, 6, 10, 13 }) {
            for (int z = 7; z <= 12; z++) b.column(x, z, 1, 3, Material.STAINED_CLAY, color);
        }
        for (int door : new int[] { 3, 7, 11 }) {
            b.doorway(door, door + 1, 7, 3, Material.STAINED_CLAY, color);
            b.put(door, 1, 10, Material.WOOL, variant == 0 ? 7 : 14);
            b.put(door, 1, 11, Material.WOOL, variant == 0 ? 7 : 14);
            b.put(door + 1, 0, 3, Material.SMOOTH_BRICK, 0);
        }
        for (int x = 2; x <= 13; x++) {
            b.column(x, 12, 1, 3, Material.STAINED_CLAY, color);
            for (int z = 10; z <= 12; z++) b.put(x, 4, z, Material.STEP, 0);
        }
        b.column(12, 3, 1, 4, Material.LOG, 1);
        for (int x = 10; x <= 12; x++) {
            b.put(x, 4, 3, Material.STAINED_CLAY, color);
            b.put(x, 5, 3, Material.STAINED_CLAY, 4);
        }
        // Audit: bedside and wash fittings in each room, a worn carpet, and the collapsed front roof
        // edge: one slab per room is missing (never over a partition) and lies on the floor below.
        for (int door : new int[] { 3, 7, 11 }) {
            if (door != 11) {
                b.put(door + 1, 1, 11, Material.WOOD, 1);
                b.put(door + 2, 1, 11, Material.CAULDRON, 0);
            }
            b.put(door + 1, 1, 9, Material.CARPET, 12);
            b.put(door + 1, 4, 10, Material.AIR, 0);
            b.put(door + 1, 1, 8, Material.STEP, 0);
        }
        b.rubble(5, 6);
        b.cache(12, 1, 11, false); // Audit: at the bed head in room 3, no longer half across its doorway
    }

    private static void watchpost(Builder b, int variant) {
        b.lot(5, 5, 11, 11, Material.COBBLESTONE, 0);
        b.lot(5, 2, 9, 4, Material.GRAVEL, 0);
        Material post = variant == 0 ? Material.LOG : Material.SMOOTH_BRICK;
        for (int x : new int[] { 6, 10 }) for (int z : new int[] { 6, 10 }) b.column(x, z, 1, 6, post, 0);
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) {
            // Audit: the four corner posts stay continuous; deck and rail frame into them.
            boolean postCell = (x == 6 || x == 10) && (z == 6 || z == 10);
            if (!postCell && (x != 7 || z != 7)) b.put(x, 4, z, Material.WOOD, 1);
            if (!postCell && (x == 6 || x == 10 || z == 6 || z == 10)) b.put(x, 5, z, Material.FENCE, 0);
        }
        b.column(6, 7, 1, 5, post, 0);
        for (int y = 1; y <= 5; y++) b.put(7, y, 7, Material.LADDER, 5);
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 10; z++) b.put(x, 7, z, Material.WOOD_STEP, 1);
        // Audit: the east half of the roof has fallen in: its ends survive on the east posts, and two
        // of its slabs lie on the deck and on the ground below.
        b.put(10, 7, 6, Material.WOOD_STEP, 1);
        b.put(9, 7, 6, Material.WOOD_STEP, 1);
        b.put(10, 7, 10, Material.WOOD_STEP, 1);
        b.put(7, 5, 9, Material.WOOD_STEP, 1);
        b.put(9, 1, 8, Material.WOOD_STEP, 1);
        for (int x : new int[] { 3, 4, 5, 9, 10, 11 }) {
            b.ground(x, 3, Material.MOSSY_COBBLESTONE, 0);
            b.put(x, 1, 3, Material.COBBLE_WALL, variant);
        }
        b.cache(9, 5, 9, false);
    }

    private static void shrine(Builder b, int variant, boolean relic) {
        if (variant == 0) {
            b.lot(4, 4, 11, 12, Material.SMOOTH_BRICK, 1);
            b.lot(6, 2, 9, 3, Material.GRAVEL, 0);
            b.shell(4, 4, 11, 12, 4, Material.SMOOTH_BRICK, 2);
            b.doorway(7, 8, 4, 4, Material.SMOOTH_BRICK, 1);
            // Audit: two-seat pews facing the altar (backs to the door), the aisle x 7..8 kept clear.
            for (int z : new int[] { 6, 8 }) for (int x : new int[] { 5, 6, 9, 10 }) {
                b.put(x, 1, z, Material.WOOD_STAIRS, 3);
            }
            for (int x = 4; x <= 11; x++) {
                b.column(x, 12, 1, 4, Material.SMOOTH_BRICK, 1);
                if (x >= 6 && x <= 9) b.put(x, 5, 12, Material.SMOOTH_BRICK, 2);
                if (x == 7 || x == 8) b.put(x, 6, 12, Material.SMOOTH_BRICK, 2);
            }
            b.put(7, 3, 12, Material.STAINED_GLASS, 10);
            b.put(8, 3, 12, Material.STAINED_GLASS, 10);
            for (int z : new int[] { 4, 6 }) b.column(4, z, 1, 6, Material.SMOOTH_BRICK, 1);
            for (int z = 4; z <= 6; z++) b.put(4, 7, z, Material.STEP, 5);
            b.put(7, 1, 11, Material.OBSIDIAN, 0);
            b.put(8, 1, 11, Material.NETHER_BRICK, 0);
            // Audit: altar candles, and the fallen roof in the nave (off the aisle, clear of the cache lid).
            b.put(7, 2, 11, Material.TORCH, 5);
            b.put(8, 2, 11, Material.TORCH, 5);
            b.put(6, 1, 9, Material.STEP, 5);
            b.put(9, 1, 7, Material.SMOOTH_BRICK, 2);
            b.put(9, 1, 5, Material.LOG, 4);
            b.put(10, 1, 5, Material.LOG, 4);
            b.rubble(5, 10);
            b.cache(9, 1, 10, relic);
        } else {
            for (int x = 2; x <= 12; x++) for (int z = 3; z <= 13; z++) {
                int distance = (x - 7) * (x - 7) + (z - 8) * (z - 8);
                if (distance <= 26) b.ground(x, z, Material.SMOOTH_BRICK, distance > 17 ? 1 : 2);
            }
            b.lot(6, 2, 8, 4, Material.GRAVEL, 0);
            int[][] pillars = { {4,5}, {10,5}, {11,8}, {9,11}, {6,12}, {3,9} };
            for (int[] point : pillars) {
                int top = 2 + b.random.nextInt(4);
                b.column(point[0], point[1], 1, top, Material.SMOOTH_BRICK, 1);
                b.put(point[0], top + 1, point[1], Material.STEP, 5);
            }
            b.column(7, 10, 1, 3, Material.OBSIDIAN, 0);
            b.put(7, 0, 9, Material.NETHER_WART_BLOCK, 0);
            b.put(6, 0, 10, Material.NETHER_BRICK, 0);
            b.put(8, 0, 10, Material.NETHER_BRICK, 0);
            b.rubble(4, 9);
            b.cache(7, 1, 8, relic);
        }
    }

    private static void bunker(Builder b, int variant, boolean relic) {
        b.lot(5, 2, 8, 4, Material.SMOOTH_BRICK, 2);
        // Fully enclosed dry room, four blocks of headroom and two blocks of cover.
        for (int x = 3; x <= 12; x++) for (int z = 6; z <= 12; z++) {
            b.put(x, -8, z, Material.COBBLESTONE, 0);
            b.put(x, -7, z, Material.SMOOTH_BRICK, 2);
            for (int y = -6; y <= -3; y++) {
                b.put(x, y, z, x == 3 || x == 12 || z == 6 || z == 12 ? Material.SMOOTH_BRICK : Material.AIR,
                        x == 3 || x == 12 || z == 6 || z == 12 ? variant : 0);
            }
            b.put(x, -2, z, Material.SMOOTH_BRICK, 2);
        }
        // Two-wide stairs run north-to-south; the shell is breached only here.
        for (int z = 2; z <= 9; z++) {
            int step = 2 - z;
            for (int x = 6; x <= 7; x++) {
                for (int y = -8; y < step; y++) b.put(x, y, z, Material.SMOOTH_BRICK, 2);
                b.put(x, step, z, Material.SMOOTH_STAIRS, 3);
                b.clear(x, step + 1, z, x, step + 3, z);
            }
            if (z <= 7) for (int x : new int[] { 5, 8 }) {
                b.column(x, z, step - 1, step + 3, Material.SMOOTH_BRICK, 2);
            }
        }
        for (int x : new int[] { 5, 8 }) for (int z = 2; z <= 4; z++) b.column(x, z, 1, 3, Material.SMOOTH_BRICK, 2);
        for (int x = 5; x <= 8; x++) for (int z = 2; z <= 4; z++) b.put(x, 4, z, Material.STEP, 5);
        b.column(5, 9, -6, -4, Material.IRON_FENCE, 0);
        b.column(8, 9, -6, -4, Material.IRON_FENCE, 0);
        for (int z = 8; z <= 10; z++) b.put(4, -6, z, Material.STEP, 5);
        b.put(10, -6, 8, Material.WOOL, variant == 0 ? 13 : 7);
        b.put(10, -6, 9, Material.WOOL, variant == 0 ? 13 : 7);
        b.put(10, -3, 12, Material.GLOWSTONE, 0);
        // Audit: two flush ceiling lamps light the whole room; a stove under the vent with its flue run
        // up through the ceiling; a map table, a water cauldron and a lidded supply crate.
        b.put(5, -2, 9, Material.GLOWSTONE, 0);
        b.put(9, -2, 8, Material.GLOWSTONE, 0);
        b.put(11, -6, 11, Material.FURNACE, 2);
        b.column(11, 11, -5, -3, Material.COBBLE_WALL, 1);
        b.put(11, -2, 11, Material.MOSSY_COBBLESTONE, 0);
        b.put(4, -6, 11, Material.WORKBENCH, 0);
        b.put(11, -6, 7, Material.CAULDRON, 3);
        b.put(4, -6, 7, Material.WOOD, 0);
        b.put(4, -5, 7, Material.TRAP_DOOR, 0);
        b.column(11, 11, -1, 1, Material.MOSSY_COBBLESTONE, 0);
        b.put(11, 2, 11, Material.STEP, 3);
        b.cache(10, -6, 11, relic);
    }

    private static void salvage(Builder b) {
        b.lot(2, 2, 13, 13, Material.GRAVEL, 0);
        for (int z = 2; z <= 13; z++) {
            if (z % 3 == 0) for (int x = 3; x <= 6; x++) b.put(x, 0, z, Material.LOG, 5);
            b.put(4, 1, z, Material.RAILS, 0);
            b.put(5, 1, z, Material.RAILS, 0);
        }
        for (int x : new int[] { 3, 6 }) for (int z : new int[] { 5, 10 }) b.put(x, 1, z, Material.STAINED_CLAY, 15);
        for (int x = 3; x <= 6; x++) for (int z = 4; z <= 11; z++) {
            b.put(x, 2, z, Material.WOOD, 1);
            if (x == 3 || x == 6 || z == 4 || z == 11) {
                b.put(x, 3, z, Material.STAINED_CLAY, 14);
                if (b.random.nextBoolean() || z >= 9) b.put(x, 4, z, Material.STAINED_CLAY, 14);
            }
            if (z >= 9) b.put(x, 5, z, Material.WOOD_STEP, 1);
        }
        b.clear(6, 3, 7, 6, 4, 8);
        b.put(7, 1, 7, Material.COBBLESTONE_STAIRS, 1);
        b.put(7, 1, 8, Material.COBBLESTONE_STAIRS, 1);
        for (int x = 9; x <= 12; x++) for (int z = 8; z <= 11; z++) {
            b.put(x, 1, z, Material.WOOD, 1);
            if (z == 11 || x == 12) b.put(x, 2, z, Material.STAINED_CLAY, 1);
        }
        // Audit: the yard office reads as collapsed (spruce corner post, clerk's table, fallen boards),
        // fallen roof boards in the boxcar, and scrap heaps from the car's own materials on the yard.
        b.put(12, 2, 11, Material.LOG, 1);
        b.put(12, 3, 11, Material.LOG, 1);
        b.put(10, 2, 9, Material.WORKBENCH, 0);
        b.put(11, 2, 9, Material.WOOD_STEP, 1);
        b.put(9, 2, 10, Material.WOOD_STEP, 1);
        b.put(4, 3, 5, Material.WOOD_STEP, 1);
        b.put(5, 3, 6, Material.WOOD_STEP, 1);
        b.put(4, 3, 7, Material.WOOD, 1);
        b.put(11, 1, 3, Material.MOSSY_COBBLESTONE, 0);
        b.put(12, 1, 3, Material.STAINED_CLAY, 14);
        b.put(11, 1, 4, Material.STAINED_CLAY, 15);
        b.put(11, 2, 3, Material.IRON_FENCE, 0);
        b.put(12, 1, 4, Material.CAULDRON, 0);
        b.put(8, 1, 13, Material.WOOD, 1);
        b.put(9, 1, 13, Material.STAINED_CLAY, 14);
        b.put(8, 2, 13, Material.WOOD_STEP, 1);
        b.put(9, 1, 12, Material.STAINED_CLAY, 15);
        b.rubble(10, 5); b.rubble(11, 6);
        b.cache(4, 3, 10, false);
    }

    private static void waterworks(Builder b) {
        for (int x = 3; x <= 11; x++) for (int z = 4; z <= 12; z++) {
            int distance = (x - 7) * (x - 7) + (z - 8) * (z - 8);
            if (distance > 20) continue;
            b.ground(x, z, Material.MOSSY_COBBLESTONE, 0);
            b.put(x, -3, z, Material.COBBLESTONE, 0);
            b.put(x, -2, z, Material.SMOOTH_BRICK, 2);
            for (int y = -1; y <= 2; y++) b.put(x, y, z,
                    distance >= 12 ? Material.MOSSY_COBBLESTONE : Material.AIR, 0);
        }
        b.lot(6, 2, 8, 3, Material.GRAVEL, 0);
        for (int z = 4; z <= 6; z++) for (int x = 6; x <= 7; x++) {
            b.put(x, 4 - z, z, Material.SMOOTH_STAIRS, 3);
            b.clear(x, 5 - z, z, x, 3, z);
        }
        b.lot(10, 2, 13, 6, Material.SMOOTH_BRICK, 2);
        b.shell(10, 2, 13, 6, 3, Material.BRICK, 0);
        b.doorway(11, 12, 2, 3, Material.BRICK, 0);
        for (int x = 10; x <= 13; x++) {
            b.column(x, 6, 1, 3, Material.BRICK, 0);
            b.put(x, 4, 6, Material.STEP, 0);
        }
        b.ground(12, 7, Material.COBBLESTONE, 0);
        b.ground(12, 8, Material.COBBLESTONE, 0);
        b.put(12, 1, 7, Material.IRON_FENCE, 0);
        b.put(12, 1, 8, Material.IRON_FENCE, 0);
        // Audit: the cistern holds a foot of water (the steps run down into it) and the pump hut has its
        // basin, with the grille carried through the south wall into the hut.
        for (int x = 3; x <= 11; x++) for (int z = 4; z <= 12; z++) {
            int distance = (x - 7) * (x - 7) + (z - 8) * (z - 8);
            if (distance < 12 && !((x == 6 || x == 7) && z == 5)) b.put(x, -1, z, Material.STATIONARY_WATER, 0);
        }
        b.put(11, 1, 5, Material.CAULDRON, 3);
        b.put(12, 1, 6, Material.IRON_FENCE, 0);
        b.cache(12, 1, 4, false);
    }

    static final class Builder {
        final Site site;
        final Random random;
        final int rotation;
        final Map<Integer, Edit> blocks = new LinkedHashMap<Integer, Edit>();
        Builder(Site site, Random random, int rotation) { this.site = site; this.random = random; this.rotation = rotation; }

        void put(int x, int y, int z, Material type, int data) {
            if (x < 2 || x > 13 || z < 2 || z > 13 || y < -9 || y > 9
                    || site.floor + y < 1 || site.floor + y >= 256) throw new IllegalArgumentException("Ruins footprint exceeded");
            int rx = x, rz = z;
            for (int i = 0; i < rotation; i++) { int oldX = rx; rx = 15 - rz; rz = oldX; }
            int absoluteY = site.floor + y;
            int key = position(rx, absoluteY, rz);
            blocks.put(key, new Edit(rx, absoluteY, rz, type, rotateData(type, data, rotation),
                    site.snapshot.getBlockTypeId(rx, absoluteY, rz), (byte) site.snapshot.getBlockData(rx, absoluteY, rz)));
        }

        void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = y1; y <= y2; y++) put(x, y, z, Material.AIR, 0);
        }
        void column(int x, int z, int bottom, int top, Material material, int data) {
            for (int y = bottom; y <= top; y++) put(x, y, z, material, data);
        }
        void ground(int x, int z, Material material, int data) {
            // Two courses below grade and at most one block of visible foundation.
            for (int y = -3; y < 0; y++) put(x, y, z, random.nextInt(4) == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE, 0);
            put(x, 0, z, material, data);
            clear(x, 1, z, x, 9, z);
        }
        void lot(int x1, int z1, int x2, int z2, Material material, int data) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) ground(x, z, material, data);
        }
        void shell(int x1, int z1, int x2, int z2, int height, Material material, int data) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                int top = height - random.nextInt(3);
                column(x, z, 1, Math.max(1, top), material, data);
            }
        }
        void doorway(int left, int right, int z, int height, Material material, int data) {
            clear(left, 1, z, right, height - 1, z);
            column(left - 1, z, 1, height, material, data);
            column(right + 1, z, 1, height, material, data);
            for (int x = left; x <= right; x++) put(x, height, z, material, data);
        }
        void rubble(int x, int z) {
            put(x, 1, z, Material.STEP, 3);
            if (random.nextBoolean()) put(x, 1, z, Material.COBBLESTONE, 0);
        }
        void cache(int x, int y, int z, boolean relic) {
            put(x, y, z, Material.CHEST, 2);
            Edit edit = lastAt(x, y, z);
            edit.cache = true;
            edit.relic = relic;
            clear(x, y + 1, z, x, y + 1, z);
        }
        private Edit lastAt(int x, int y, int z) {
            for (int i = 0; i < rotation; i++) { int oldX = x; x = 15 - z; z = oldX; }
            return blocks.get(position(x, site.floor + y, z));
        }
        Material at(int x, int y, int z) {
            Edit edit = blocks.get(position(x, y, z));
            return edit == null ? site.snapshot.getBlockType(x, y, z) : edit.type;
        }
        boolean validCaches() {
            int count = 0;
            for (Edit edit : blocks.values()) if (edit.cache) {
                count++;
                if (at(edit.x, edit.y + 1, edit.z) != Material.AIR || !at(edit.x, edit.y - 1, edit.z).isSolid()) return false;
                for (int[] d : DIRECTIONS) if (at(edit.x + d[0], edit.y, edit.z + d[1]) == Material.CHEST) return false;
            }
            return count == 1;
        }
        void overgrow() {
            // A vine is only placed against an actual solid wall, inside the guard ring.
            List<Edit> walls = new ArrayList<Edit>(blocks.values());
            Set<Integer> lids = new HashSet<Integer>();
            for (Edit edit : walls) if (edit.cache) lids.add(position(edit.x, edit.y + 1, edit.z));
            for (Edit wall : walls) {
                if (wall.y <= site.floor || wall.y > site.floor + 5 || !wall.type.isOccluding() || wall.cache
                        || random.nextInt(12) != 0) continue;
                for (int i = 0; i < DIRECTIONS.length; i++) {
                    int x = wall.x + DIRECTIONS[i][0], z = wall.z + DIRECTIONS[i][1];
                    if (x < 2 || x > 13 || z < 2 || z > 13 || at(x, wall.y, z) != Material.AIR
                            || lids.contains(position(x, wall.y, z))) continue;
                    // Data describes the supporting face: east, west, south, north.
                    int[] vineData = { 2, 8, 4, 1 };
                    int key = position(x, wall.y, z);
                    blocks.put(key, new Edit(x, wall.y, z, Material.VINE, (byte) vineData[i],
                            site.snapshot.getBlockTypeId(x, wall.y, z), (byte) site.snapshot.getBlockData(x, wall.y, z)));
                    break;
                }
            }
        }
        List<Edit> edits() {
            List<Edit> edits = new ArrayList<Edit>();
            for (Edit edit : blocks.values()) {
                if (edit.type.getId() != edit.before || edit.data != edit.beforeData) edits.add(edit);
            }
            Collections.sort(edits, Comparator.comparingInt((Edit e) -> e.phase())
                    .thenComparingInt(e -> e.type == Material.AIR ? -e.y : e.y)
                    .thenComparingInt(e -> position(e.x, e.y, e.z)));
            return edits;
        }
    }

    private static final int[][] DIRECTIONS = { {1,0}, {-1,0}, {0,1}, {0,-1} };
    static int position(int x, int y, int z) { return (y << 8) | (z << 4) | x; }
    static byte rotateData(Material type, int data, int rotation) {
        if (type == Material.LADDER || type == Material.CHEST || type == Material.FURNACE) {
            for (int i = 0; i < rotation; i++) data = data == 2 ? 5 : data == 5 ? 3 : data == 3 ? 4 : 2;
        } else if (type.name().endsWith("_STAIRS")) {
            int flags = data & ~3;
            data &= 3;
            for (int i = 0; i < rotation; i++) data = data == 0 ? 2 : data == 2 ? 1 : data == 1 ? 3 : 0;
            data |= flags;
        } else if (type == Material.RAILS && (rotation & 1) != 0) data ^= 1;
        else if ((type == Material.LOG || type == Material.LOG_2) && (rotation & 1) != 0) {
            if ((data & 12) == 4) data = (data & 3) | 8;
            else if ((data & 12) == 8) data = (data & 3) | 4;
        }
        return (byte) data;
    }

    static final class Edit {
        final int x, y, z, before;
        final Material type;
        final byte data, beforeData;
        boolean cache, relic;
        Edit(int x, int y, int z, Material type, byte data, int before, byte beforeData) {
            this.x = x; this.y = y; this.z = z; this.type = type; this.data = data;
            this.before = before; this.beforeData = beforeData;
        }
        int phase() { return cache ? 3 : type == Material.AIR ? 0 : type == Material.VINE || type == Material.LADDER || type == Material.RAILS ? 2 : 1; }
    }
    static final class Key {
        final UUID world;
        final int x, z;
        Key(UUID world, int x, int z) { this.world = world; this.x = x; this.z = z; }
        static Key of(Chunk chunk) { return new Key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return world.equals(key.world) && x == key.x && z == key.z;
        }
        @Override public int hashCode() { return 31 * (31 * world.hashCode() + x) + z; }
    }
    private static final class Candidate {
        final Key key;
        final Chunk chunk;
        final long readyAt;
        Candidate(Key key, Chunk chunk, long readyAt) { this.key = key; this.chunk = chunk; this.readyAt = readyAt; }
    }
    private static final class Work {
        final Candidate candidate;
        final List<Edit> edits;
        final long seed;
        int cursor, verifyCursor;
        boolean aborted, verified, finishing, cachePlaced;
        Future<?> record;
        Work(Candidate candidate, List<Edit> edits, long seed) { this.candidate = candidate; this.edits = edits; this.seed = seed; }
    }

    /** Fixed 32-byte checksummed records, maximum two records per chunk. */
    static final class Ledger {
        private static final long HEADER = 0x4a41535255494e31L; // JASRUIN1
        private final File file;
        private final int limit;
        private final Map<Key, Integer> states = new ConcurrentHashMap<Key, Integer>();
        Ledger(File file, int limit) throws IOException {
            this.file = file;
            this.limit = limit;
            if (!file.exists()) return;
            if (file.length() < 8 || file.length() > 8L + 64L * limit || (file.length() - 8) % 32 != 0) {
                throw new IOException("Invalid or oversized ruins journal: " + file);
            }
            try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
                if (input.readLong() != HEADER) throw new IOException("Unknown ruins journal version");
                long count = (file.length() - 8) / 32;
                for (long i = 0; i < count; i++) {
                    byte[] record = new byte[32];
                    input.readFully(record);
                    ByteBuffer bytes = ByteBuffer.wrap(record);
                    Key key = new Key(new UUID(bytes.getLong(), bytes.getLong()), bytes.getInt(), bytes.getInt());
                    int state = bytes.getInt();
                    CRC32 crc = new CRC32();
                    crc.update(record, 0, 28);
                    if (bytes.getInt() != (int) crc.getValue()) throw new IOException("Damaged ruins journal record");
                    Integer previous = states.get(key);
                    if (state == 1 && previous == null) states.put(key, 1);
                    else if (state == 2 && Integer.valueOf(1).equals(previous)) states.put(key, 2);
                    else throw new IOException("Invalid ruins journal transition");
                    if (states.size() > limit) throw new IOException("Ruins journal capacity exceeded");
                }
            }
        }
        boolean contains(Key key) { return states.containsKey(key); }
        boolean completed(Key key) { return Integer.valueOf(2).equals(states.get(key)); }
        synchronized void reserve(Key key) throws IOException {
            if (states.containsKey(key)) throw new IOException("Ruins chunk already reserved");
            if (states.size() >= limit) throw new IOException("Ruins journal is full; raise ruins.max-ledger-entries (never delete the journal)");
            append(key, 1);
            states.put(key, 1);
        }
        synchronized void complete(Key key) throws IOException {
            if (!Integer.valueOf(1).equals(states.get(key))) throw new IOException("Ruins chunk has no reservation");
            append(key, 2);
            states.put(key, 2);
        }
        private void append(Key key, int state) throws IOException {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create ruins data directory");
            boolean header = !file.exists();
            ByteBuffer record = ByteBuffer.allocate(32);
            record.putLong(key.world.getMostSignificantBits()).putLong(key.world.getLeastSignificantBits());
            record.putInt(key.x).putInt(key.z).putInt(state);
            CRC32 crc = new CRC32();
            crc.update(record.array(), 0, 28);
            record.putInt((int) crc.getValue());
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                if (header) output.write(ByteBuffer.allocate(8).putLong(HEADER).array());
                output.write(record.array());
                output.getChannel().force(true);
            }
        }
    }
}
