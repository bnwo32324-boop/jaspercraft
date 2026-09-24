/*
 * JasprImportedWorldgen 1.3.0 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152 decompilation of
 * the 1.2.0 ImportedWorldgenPlugin, cleaned up, with one change: grid 3 (CellPlanner lattice 2). attach() opens
 * a third immutable boundary, jaspr-imported-v3.boundary (snapshotted at the first 1.3.0 start, before any chunk
 * generates; fail closed like v1/v2), and a third ledger, cells3-<uid>; grid 3 is admitted only where v1, v2 and v3
 * all permit, yields to every grid-1 and grid-2 plan (Admission.secondary, chained) and to every HorrorBiomes site
 * of every tier (ClaimGuard). The populator and /where ask grid 3 only after grids 1 and 2 (as grid 2 after grid 1),
 * so every chunk and position that a grid-1/2 plan covers answers exactly as before. Log lines gain grid-3 fields.
 * Apply the same change to the PC source. Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import chat.jaspr.biomes.StructurePlanner;
import chat.jaspr.biomes.Terrain;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ImportedWorldgenPlugin
extends JavaPlugin
implements Listener {
    public static final String IMPORTED_CHUNK_MARKER = "jaspr-imported-v1";
    private static final String CATALOG_SHA = "ca608b20e30ee80358256b64b32c23850cc7d2bf88e07c252dee02933c900d38";
    private static final String PIECES_SHA = "fb1347b588055f58d898d4df88a0fb2ac5d41ef2191225838bada6b0de79011d";
    /** 1.3.0: chunks that existed when 1.3.0 first started; grid 3 never builds in them (nor in the ring round them). */
    public static final String BOUNDARY_V3 = "jaspr-imported-v3.boundary";
    private SiteCatalog catalog;
    private TileIndex tiles;
    private CellPlanner planner;
    private ClaimGuard claims;
    private Occupancy.Cache occupancy;
    private final Map<UUID, Context> worlds = new HashMap<UUID, Context>();
    private final Map<UUID, ImportsPopulator> populators = new HashMap<UUID, ImportsPopulator>();
    private Path protectionRoot;

    public void onEnable() {
        this.saveDefaultConfig();
        if (!this.getConfig().getBoolean("enabled", false)) {
            this.getLogger().warning("Imported generation disabled by config; no chunks changed.");
            return;
        }
        try {
            Path folder = this.getDataFolder().toPath();
            Path catalogPath = folder.resolve("import-catalog.json");
            Path manifestPath = folder.resolve("piece-manifest.json");
            if (!ImportedWorldgenPlugin.hash(catalogPath).equals(CATALOG_SHA) || !ImportedWorldgenPlugin.hash(manifestPath).equals(PIECES_SHA)) {
                throw new IOException("Catalog/manifest release identity mismatch");
            }
            this.catalog = new SiteCatalog(catalogPath);
            this.tiles = new TileIndex(manifestPath, folder.resolve("pieces"), this.catalog);
            this.planner = new CellPlanner(this.catalog);
            this.claims = new ClaimGuard();
            this.occupancy = new Occupancy.Cache(this.tiles);
            TileIndex tileIndex = this.tiles;
            SiteCatalog siteCatalog = this.catalog;
            Thread thread = new Thread(() -> {
                try {
                    for (SiteSpec site : siteCatalog.glm()) {
                        GearLoot.chests(site, tileIndex);
                    }
                    for (SiteSpec site : siteCatalog.codex()) {
                        GearLoot.chests(site, tileIndex);
                    }
                }
                catch (Exception | LinkageError e) {
                    this.getLogger().warning("IMPORTED_CHEST_INDEX_FAILED " + e.getClass().getSimpleName());
                }
            }, "JasprImported-chest-index");
            thread.setDaemon(true);
            thread.setPriority(1);
            thread.start();
            Plugin horror = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
            if (horror == null || !horror.isEnabled()) {
                throw new IOException("Horror dependency unavailable");
            }
            this.protectionRoot = horror.getDataFolder().toPath().resolve("imported-protection-v1");
            Files.createDirectories(this.protectionRoot, new FileAttribute[0]);
            Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
            for (World world : Bukkit.getWorlds()) {
                this.attach(world);
            }
            int big = 0;
            for (SiteSpec site : this.catalog.glm()) {
                if (!Occupancy.big(site)) continue;
                ++big;
            }
            for (SiteSpec site : this.catalog.codex()) {
                if (!Occupancy.big(site)) continue;
                ++big;
            }
            this.getLogger().info("IMPORTED_ASSETS_READY version=" + this.getDescription().getVersion() + " collections=GLM_freebuff:45,ChatGPT_Codex_Structures:98 tiles=4409 envelopeDesigns=" + big + " envelopeArea>=" + 12000L + " gearLoot=reflective grids=" + 48 + "+" + 41 + "+" + CellPlanner.CELL3_CHUNKS);
        }
        catch (Exception e) {
            this.getLogger().severe("IMPORTED_GENERATION_REFUSED " + e);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    public void onDisable() {
        if (this.catalog != null) {
            this.getLogger().info("IMPORTED_GEAR_METRICS " + GearLoot.metrics());
        }
        Plugin horror = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
        for (Map.Entry<UUID, ImportsPopulator> entry : this.populators.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) continue;
            ImportsPopulator populator = entry.getValue();
            if (horror != null && horror.isEnabled()) {
                try {
                    Bukkit.getScheduler().runTask(horror, () -> world.getPopulators().remove((Object)populator));
                }
                catch (RuntimeException e) {
                    world.getPopulators().remove((Object)populator);
                }
                continue;
            }
            world.getPopulators().remove((Object)populator);
        }
        this.populators.clear();
        this.worlds.clear();
    }

    private static String hash(Path path) throws IOException {
        return TileIndex.hex(TileIndex.sha(Files.readAllBytes(path)));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void init(WorldInitEvent event) {
        this.attach(event.getWorld());
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void markImportedChunk(ChunkLoadEvent event) {
        if (!this.isEnabled() || !this.worlds.containsKey(event.getWorld().getUID())) {
            return;
        }
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        try {
            if (this.isProtectedChunk(event.getWorld(), cx, cz)) {
                event.getChunk().getBlock(0, 0, 0).setMetadata(IMPORTED_CHUNK_MARKER, (MetadataValue)new FixedMetadataValue((Plugin)this, (Object)true));
            }
        }
        catch (Exception e) {
            this.getLogger().severe("IMPORTED_MARK_FAILED chunk=" + cx + "," + cz + " " + e);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private Path protectionPath(World world, int cx, int cz) {
        if (this.protectionRoot == null) {
            throw new IllegalStateException("Protection store not ready");
        }
        return this.protectionRoot.resolve(world.getUID().toString()).resolve(Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32)).resolve(cx + "_" + cz + ".guard");
    }

    private boolean isProtectedChunk(World world, int cx, int cz) {
        return Files.isRegularFile(this.protectionPath(world, cx, cz), new LinkOption[0]);
    }

    private void protectChunk(World world, int cx, int cz) throws IOException {
        Path path = this.protectionPath(world, cx, cz);
        if (Files.isRegularFile(path, new LinkOption[0])) {
            return;
        }
        Files.createDirectories(path.getParent(), new FileAttribute[0]);
        Path pending = Files.createTempFile(path.getParent(), "imported-", ".pending", new FileAttribute[0]);
        try {
            try (FileOutputStream out = new FileOutputStream(pending.toFile());){
                DataOutputStream data = new DataOutputStream(out);
                data.writeInt(1229803569);
                data.writeInt(cx);
                data.writeInt(cz);
                data.writeLong(world.getSeed());
                data.flush();
                out.getFD().sync();
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(pending);
        }
    }

    private void attach(World world) {
        if (this.planner == null || !world.getName().equals("world") || world.getEnvironment() != World.Environment.NORMAL || this.worlds.containsKey(world.getUID())) {
            return;
        }
        try {
            ChunkBoundary boundary = ChunkBoundary.open(world.getWorldFolder(), world.getSeed());
            Terrain terrain = new Terrain(world.getSeed());
            CellLedger ledger = new CellLedger(this.getDataFolder().toPath().resolve("cells-" + world.getUID()), CATALOG_SHA, this.catalog);
            CellPlanner.Ground ground = Admission.ground(world.getSeed(), terrain, boundary::permits, this.claims);
            ChunkBoundary boundary2 = ChunkBoundary.open(world.getWorldFolder(), world.getSeed(), "jaspr-imported-v2.boundary");
            CellPlanner.Ground ground2 = Admission.ground(world.getSeed(), terrain, (x, z, sizeX, sizeZ) -> boundary.permits(x, z, sizeX, sizeZ) && boundary2.permits(x, z, sizeX, sizeZ), this.claims, this.occupancy);
            Context context = new Context(boundary, ledger, terrain, ground);
            context.ledger2 = new CellLedger(this.getDataFolder().toPath().resolve("cells2-" + world.getUID()), CATALOG_SHA, this.catalog, 1);
            context.ground2 = Admission.secondary(ground2, (i, j) -> ledger.get(world.getSeed(), i, j, this.planner, ground));
            // 1.3.0: grid 3, on ground new in 1.3.0 only, yielding to grid-1 and grid-2 plans (and ClaimGuard: HB tier 2).
            ChunkBoundary boundary3 = ChunkBoundary.open(world.getWorldFolder(), world.getSeed(), BOUNDARY_V3);
            CellPlanner.Ground ground3 = Admission.ground(world.getSeed(), terrain, (x, z, sizeX, sizeZ) -> boundary.permits(x, z, sizeX, sizeZ) && boundary2.permits(x, z, sizeX, sizeZ) && boundary3.permits(x, z, sizeX, sizeZ), this.claims, this.occupancy);
            CellLedger ledger2 = context.ledger2;
            CellPlanner.Ground secondary = context.ground2;
            context.ledger3 = new CellLedger(this.getDataFolder().toPath().resolve("cells3-" + world.getUID()), CATALOG_SHA, this.catalog, 2);
            context.ground3 = Admission.secondary(Admission.secondary(ground3, (i, j) -> ledger2.get(world.getSeed(), i, j, this.planner, secondary), 41), (i, j) -> ledger.get(world.getSeed(), i, j, this.planner, ground), 48);
            ImportsPopulator populator = new ImportsPopulator(context);
            world.getPopulators().add(populator);
            this.populators.put(world.getUID(), populator);
            this.worlds.put(world.getUID(), context);
            this.getLogger().info("IMPORTED_BOUNDARY_READY world=" + world.getName() + " protectedChunks=" + boundary.count() + " protectedChunksV2=" + boundary2.count() + " populators=" + world.getPopulators().size() + " grids=" + 48 + "+" + 41 + "+" + CellPlanner.CELL3_CHUNKS + " protectedChunksV3=" + boundary3.count());
        }
        catch (Exception e) {
            this.getLogger().severe("IMPORTED_ATTACH_REFUSED " + e);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private static boolean overlap(int x, int z, int sizeX, int sizeZ, StructurePlanner.Site site) {
        return (long)x < (long)site.x + (long)site.width + 16L && (long)x + (long)sizeX + 16L > (long)site.x && (long)z < (long)site.z + (long)site.depth + 16L && (long)z + (long)sizeZ + 16L > (long)site.z;
    }

    static boolean expeditionEnvelope(long seed, int x, int y, int z, int sizeX, int sizeZ, Occupancy occupancy) {
        StructurePlanner.Site site;
        int j;
        int i;
        int gap = 16;
        for (i = Math.floorDiv(x - 512, 384); i <= Math.floorDiv(x + sizeX + 512, 384); ++i) {
            for (j = Math.floorDiv(z - 512, 384); j <= Math.floorDiv(z + sizeZ + 512, 384); ++j) {
                site = StructurePlanner.expansionRegion(seed, i, j);
                if (site == null || !ImportedWorldgenPlugin.overlap(x, z, sizeX, sizeZ, site) || !occupancy.meets(x, y, z, site.x - gap, site.z - gap, site.x + site.width + gap, site.z + site.depth + gap, 0, 255)) continue;
                return true;
            }
        }
        for (i = Math.floorDiv(x - 512, 1024); i <= Math.floorDiv(x + sizeX + 512, 1024); ++i) {
            for (j = Math.floorDiv(z - 512, 1024); j <= Math.floorDiv(z + sizeZ + 512, 1024); ++j) {
                site = StructurePlanner.region(seed, i, j);
                if (site == null || !ImportedWorldgenPlugin.overlap(x, z, sizeX, sizeZ, site) || !occupancy.meets(x, y, z, site.x - gap, site.z - gap, site.x + site.width + gap, site.z + site.depth + gap, 0, 255)) continue;
                return true;
            }
        }
        return false;
    }

    static boolean conflictsWithExpeditions(long seed, int x, int z, int sizeX, int sizeZ) {
        int j;
        int i;
        int i0 = Math.floorDiv(x - 512, 384);
        int i1 = Math.floorDiv(x + sizeX + 512, 384);
        int j0 = Math.floorDiv(z - 512, 384);
        int j1 = Math.floorDiv(z + sizeZ + 512, 384);
        for (i = i0; i <= i1; ++i) {
            for (j = j0; j <= j1; ++j) {
                StructurePlanner.Site site = StructurePlanner.expansionRegion(seed, i, j);
                if (site == null || !ImportedWorldgenPlugin.overlap(x, z, sizeX, sizeZ, site)) continue;
                return true;
            }
        }
        i = Math.floorDiv(x - 512, 1024);
        j = Math.floorDiv(x + sizeX + 512, 1024);
        int k0 = Math.floorDiv(z - 512, 1024);
        int k1 = Math.floorDiv(z + sizeZ + 512, 1024);
        for (int a = i; a <= j; ++a) {
            for (int b = k0; b <= k1; ++b) {
                StructurePlanner.Site site = StructurePlanner.region(seed, a, b);
                if (site == null || !ImportedWorldgenPlugin.overlap(x, z, sizeX, sizeZ, site)) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean inside(CellPlanner.Plan plan, int x, int y, int z) {
        return plan != null && x >= plan.x && x < plan.x + plan.site.dimensions[0] && z >= plan.z && z < plan.z + plan.site.dimensions[2] && y >= plan.y && y < plan.y + plan.site.dimensions[1];
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void where(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (!(message.equalsIgnoreCase("/where") || message.equalsIgnoreCase("/survey") || message.equalsIgnoreCase("/biome"))) {
            return;
        }
        Player player = event.getPlayer();
        Context context = this.worlds.get(player.getWorld().getUID());
        if (context == null) {
            return;
        }
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        try {
            CellPlanner.Plan plan = context.ledger.get(player.getWorld().getSeed(), Math.floorDiv(x, 768), Math.floorDiv(z, 768), this.planner, context.ground);
            if (!ImportedWorldgenPlugin.inside(plan, x, y, z)) {
                plan = context.ledger2.get(player.getWorld().getSeed(), Math.floorDiv(x, 656), Math.floorDiv(z, 656), this.planner, context.ground2);
            }
            if (!ImportedWorldgenPlugin.inside(plan, x, y, z)) {
                int cell = 16 * CellPlanner.CELL3_CHUNKS;
                plan = context.ledger3.get(player.getWorld().getSeed(), Math.floorDiv(x, cell), Math.floorDiv(z, cell), this.planner, context.ground3);
            }
            if (!ImportedWorldgenPlugin.inside(plan, x, y, z)) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(ChatColor.GREEN + "Structure " + ChatColor.WHITE + plan.site.name + ChatColor.GOLD + " (" + plan.site.collection + ")");
            player.sendMessage(ChatColor.GRAY + "ID " + plan.site.id + " | Tier " + plan.site.tier + " | " + plan.site.habitat + " | origin " + plan.x + "," + plan.y + "," + plan.z + " | size " + plan.site.dimensions[0] + "x" + plan.site.dimensions[1] + "x" + plan.site.dimensions[2]);
        }
        catch (Exception e) {
            this.getLogger().warning("IMPORTED_WHERE_FAILED " + e);
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Boolean.getBoolean("jaspr.imported.fixture")) {
            sender.sendMessage("Fixture probe unavailable.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("palette")) {
            try {
                HashSet<Integer> states = new HashSet<Integer>();
                for (List<SiteSpec> list : Arrays.asList(this.catalog.glm(), this.catalog.codex())) {
                    for (SiteSpec site : list) {
                        for (int tx = 0; tx < site.widthChunks(); ++tx) {
                            for (int tz = 0; tz < site.depthChunks(); ++tz) {
                                TileIndex.Tile tile = this.tiles.tile(site, tx, tz);
                                if (tile == null) continue;
                                TileIndex.Data data = this.tiles.decode(tile, site.dimensions[1]);
                                for (int r = 0; r < data.size(); ++r) {
                                    states.add(data.id[r] << 4 | data.meta[r]);
                                }
                            }
                        }
                    }
                }
                int canonicalized = 0;
                Iterator<Integer> iterator = states.iterator();
                while (iterator.hasNext()) {
                    int state = iterator.next();
                    int id = state >>> 4;
                    int meta = state & 0xF;
                    net.minecraft.server.v1_12_R1.Block block = net.minecraft.server.v1_12_R1.Block.getById(id);
                    if (block == null) {
                        throw new IOException("Native block missing " + id);
                    }
                    int canonical = block.toLegacyData(block.fromLegacyData(meta));
                    if (canonical == meta || ++canonicalized > 12) continue;
                    sender.sendMessage("PALETTE_CANONICALIZED " + id + ":" + meta + " -> " + canonical);
                }
                sender.sendMessage("PALETTE_PASS states=" + states.size() + " canonicalized=" + canonicalized);
            }
            catch (Exception e) {
                sender.sendMessage("PALETTE_FAIL " + e);
                this.getLogger().severe("PALETTE_FAIL " + e);
            }
            return true;
        }
        World world = Bukkit.getWorld("world");
        Context context = world == null ? null : this.worlds.get(world.getUID());
        if (context == null) {
            sender.sendMessage("FIXTURE_FAIL no attached world");
            return true;
        }
        try {
            boolean whole = args.length == 3 && args[0].equalsIgnoreCase("whole");
            boolean cellMode = args.length == 3 && (args[0].equalsIgnoreCase("cell") || whole);
            String filter = args.length == 0 || cellMode ? "any" : args[0].toLowerCase(Locale.ROOT);
            int x0 = cellMode ? Integer.parseInt(args[1]) : 2;
            int x1 = cellMode ? x0 + 1 : 20;
            int z0 = cellMode ? Integer.parseInt(args[2]) : 2;
            int z1 = cellMode ? z0 + 1 : 20;
            for (int i = x0; i < x1; ++i) {
                for (int j = z0; j < z1; ++j) {
                    CellPlanner.Plan plan = this.planner.choose(world.getSeed(), i, j, context.ground);
                    if (plan == null || filter.equals("glm") && !plan.site.collection.equals("GLM_freebuff") || filter.equals("codex") && !plan.site.codex() || filter.equals("water") && !plan.site.habitat.matches("ocean_surface|afloat|half_submerged|submerged|shore") || filter.contains(":") && !filter.equalsIgnoreCase(plan.site.id)) continue;
                    if (whole) {
                        int bx = plan.x / 16;
                        int bz = plan.z / 16;
                        for (int a = bx - 1; a <= bx + plan.site.widthChunks(); ++a) {
                            for (int b = bz - 1; b <= bz + plan.site.depthChunks(); ++b) {
                                world.getChunkAt(a, b);
                            }
                        }
                    }
                    int tilesChecked = 0;
                    long recordsChecked = 0L;
                    long driftTotal = 0L;
                    long loadTotal = 0L;
                    int maxRecords = 0;
                    if (cellMode) {
                        for (int tx = 0; tx < plan.site.widthChunks(); ++tx) {
                            for (int tz = 0; tz < plan.site.depthChunks(); ++tz) {
                                TileIndex.Tile tile = this.tiles.tile(plan.site, tx, tz);
                                if (tile == null) continue;
                                maxRecords = Math.max(maxRecords, tile.records);
                            }
                        }
                    }
                    for (int tx = 0; tx < plan.site.widthChunks(); ++tx) {
                        for (int tz = 0; tz < plan.site.depthChunks(); ++tz) {
                            TileIndex.Tile tile = this.tiles.tile(plan.site, tx, tz);
                            if (tile == null || !cellMode && tile.records < 1000 || cellMode && !whole && tile.records != maxRecords) continue;
                            CellPlanner.Plan frozen = context.ledger.get(world.getSeed(), i, j, this.planner, context.ground);
                            if (frozen == null || !frozen.site.id.equals(plan.site.id) || frozen.x != plan.x || frozen.z != plan.z) {
                                throw new IOException("Frozen cell plan changed");
                            }
                            int chunkX = plan.x / 16 + tx;
                            int chunkZ = plan.z / 16 + tz;
                            long started = System.nanoTime();
                            for (int a = chunkX - 1; a <= chunkX + 1; ++a) {
                                for (int b = chunkZ - 1; b <= chunkZ + 1; ++b) {
                                    world.getChunkAt(a, b);
                                }
                            }
                            long loadMs = (System.nanoTime() - started) / 1000000L;
                            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                            if (!chunk.getBlock(0, 0, 0).hasMetadata(IMPORTED_CHUNK_MARKER)) {
                                throw new IOException("Imported cleanup guard missing for " + plan.site.id + " at " + chunkX + "," + chunkZ);
                            }
                            TileIndex.Data data = this.tiles.decode(tile, plan.site.dimensions[1]);
                            int mismatches = 0;
                            int drift = 0;
                            int water = 0;
                            int torches = 0;
                            int trapdoors = 0;
                            int other = 0;
                            StringBuilder detail = new StringBuilder();
                            for (int r = 0; r < data.size(); ++r) {
                                int index = data.index[r];
                                int lx = index & 0xF;
                                int lz = index >>> 4 & 0xF;
                                int wy = plan.y + (index >>> 8);
                                Block block = chunk.getBlock(lx, wy, lz);
                                int type = block.getTypeId();
                                int meta = block.getData() & 0xF;
                                if (type == data.id[r] && meta == data.meta[r]) continue;
                                boolean flowed = !(data.id[r] != 8 && data.id[r] != 9 || type != 0 && type != 8 && type != 9);
                                boolean torch = data.id[r] == 50 && type == 0;
                                boolean trapdoor = data.id[r] == 96 && type == 96;
                                boolean plant = data.id[r] == 175 && data.meta[r] == 8 && type == 175 && meta == 10 || data.id[r] == 218 && data.meta[r] == 10 && type == 218 && meta == 2;
                                boolean lamp = data.id[r] == 124 && type == 123;
                                boolean chest = data.id[r] == 54 && type == 54;
                                boolean ladder = data.id[r] == 65 && type == 0;
                                boolean snow = data.id[r] == 80 && type == 0;
                                boolean leaves = (data.id[r] == 18 || data.id[r] == 161) && type == data.id[r];
                                boolean grown = data.id[r] == 0 && (type == 18 || type == 161);
                                boolean fallen = data.id[r] == 0 && (type == 12 || type == 13);
                                if (flowed || torch || trapdoor || plant || lamp || chest || ladder || snow || leaves || grown || fallen) {
                                    ++drift;
                                    if (flowed) {
                                        ++water;
                                    }
                                    if (torch) {
                                        ++torches;
                                    }
                                    if (trapdoor) {
                                        ++trapdoors;
                                    }
                                    if (!plant && !lamp && !chest && !ladder && !snow && !leaves && !grown && !fallen) continue;
                                    ++other;
                                    continue;
                                }
                                if (++mismatches > 32) continue;
                                detail.append(" [").append(r).append(" @").append(lx).append(',').append(wy).append(',').append(lz).append(" expected ").append(data.id[r]).append(':').append(data.meta[r]).append(" got ").append(type).append(':').append(meta).append(']');
                            }
                            if (mismatches > 0) {
                                throw new IOException("Tile mismatches site=" + plan.site.id + " count=" + mismatches + detail);
                            }
                            if (drift > Math.max(32, data.size() / 100)) {
                                throw new IOException("Excessive natural-physics drift site=" + plan.site.id + " drift=" + drift + " water=" + water + " torches=" + torches + " trapdoors=" + trapdoors + " other=" + other + " of " + data.size());
                            }
                            int tileEntities = 0;
                            int lights = 0;
                            net.minecraft.server.v1_12_R1.Chunk handle = ((CraftChunk)chunk).getHandle();
                            for (int r = 0; r < data.size(); ++r) {
                                int index = data.index[r];
                                int lx = index & 0xF;
                                int lz = index >>> 4 & 0xF;
                                int wy = plan.y + (index >>> 8);
                                net.minecraft.server.v1_12_R1.Block nativeBlock = net.minecraft.server.v1_12_R1.Block.getById(data.id[r]);
                                Block block = chunk.getBlock(lx, wy, lz);
                                if (block.getTypeId() != data.id[r]) continue;
                                if (nativeBlock.isTileEntity()) {
                                    ++tileEntities;
                                    if (handle.getTileEntityImmediately(new BlockPosition(chunkX * 16 + lx, wy, chunkZ * 16 + lz)) == null) {
                                        throw new IOException("Missing tile entity in " + plan.site.id + " at " + lx + "," + wy + "," + lz);
                                    }
                                }
                                if (nativeBlock.fromLegacyData(data.meta[r]).d() <= 0) continue;
                                ++lights;
                                if (block.getLightFromBlocks() != 0) continue;
                                throw new IOException("Unlit source in " + plan.site.id + " at " + lx + "," + wy + "," + lz);
                            }
                            if ("deep_ocean_basin".equals(plan.site.terrainAdaptation)) {
                                boolean found = false;
                                for (int lx = 0; lx < 16 && !found; ++lx) {
                                    for (int lz = 0; lz < 16 && !found; ++lz) {
                                        int key = (62 - plan.y) * 256 + lz * 16 + lx;
                                        if (Arrays.binarySearch(data.index, key) >= 0 || context.terrain.sample(chunkX * 16 + lx, chunkZ * 16 + lz).y >= 62) continue;
                                        if (chunk.getBlock(lx, 62, lz).getTypeId() != 9) {
                                            throw new IOException("Basin water absent at " + lx + "," + lz);
                                        }
                                        found = true;
                                    }
                                }
                            }
                            ++tilesChecked;
                            recordsChecked += (long)data.size();
                            driftTotal += (long)drift;
                            loadTotal += loadMs;
                            if (whole) continue;
                            sender.sendMessage("FIXTURE_PASS site=" + plan.site.id + " tile=" + tx + "," + tz + " records=" + data.size() + " chunk=" + chunkX + "," + chunkZ + " boundary=" + context.boundary.count() + " loadMs=" + loadMs + " physicsDrift=" + drift + " water=" + water + " torches=" + torches + " trapdoors=" + trapdoors + " other=" + other + " tileEntities=" + tileEntities + " lightSources=" + lights);
                            return true;
                        }
                    }
                    if (!whole) continue;
                    sender.sendMessage("FIXTURE_WHOLE_PASS site=" + plan.site.id + " tiles=" + tilesChecked + " records=" + recordsChecked + " physicsDrift=" + driftTotal + " loadMs=" + loadTotal);
                    return true;
                }
            }
            sender.sendMessage("FIXTURE_FAIL no admitted tile in scan");
        }
        catch (Exception e) {
            sender.sendMessage("FIXTURE_FAIL " + e);
            this.getLogger().severe("FIXTURE_FAIL " + e);
        }
        return true;
    }

    private final class ImportsPopulator
    extends BlockPopulator {
        private final Context context;

        ImportsPopulator(Context context) {
            this.context = context;
        }

        public void populate(World world, Random random, Chunk chunk) {
            if (!ImportedWorldgenPlugin.this.isEnabled()) {
                return;
            }
            int cx = chunk.getX();
            int cz = chunk.getZ();
            int cellX = Math.floorDiv(cx, 48);
            int cellZ = Math.floorDiv(cz, 48);
            try {
                CellPlanner.Plan plan = this.context.ledger.get(world.getSeed(), cellX, cellZ, ImportedWorldgenPlugin.this.planner, this.context.ground);
                if (!(plan != null && plan.intersects(cx, cz) || (plan = this.context.ledger2.get(world.getSeed(), Math.floorDiv(cx, 41), Math.floorDiv(cz, 41), ImportedWorldgenPlugin.this.planner, this.context.ground2)) != null && plan.intersects(cx, cz) || (plan = this.context.ledger3.get(world.getSeed(), Math.floorDiv(cx, CellPlanner.CELL3_CHUNKS), Math.floorDiv(cz, CellPlanner.CELL3_CHUNKS), ImportedWorldgenPlugin.this.planner, this.context.ground3)) != null && plan.intersects(cx, cz))) {
                    return;
                }
                int tx = cx - Math.floorDiv(plan.x, 16);
                int tz = cz - Math.floorDiv(plan.z, 16);
                TileIndex.Tile tile = ImportedWorldgenPlugin.this.tiles.tile(plan.site, tx, tz);
                TileIndex.Data data = tile == null ? null : ImportedWorldgenPlugin.this.tiles.decode(tile, plan.site.dimensions[1]);
                boolean basin = "deep_ocean_basin".equals(plan.site.terrainAdaptation);
                if (data == null && !basin) {
                    return;
                }
                ImportedWorldgenPlugin.this.protectChunk(world, cx, cz);
                chunk.getBlock(0, 0, 0).setMetadata(ImportedWorldgenPlugin.IMPORTED_CHUNK_MARKER, (MetadataValue)new FixedMetadataValue((Plugin)ImportedWorldgenPlugin.this, (Object)true));
                if (basin) {
                    this.fillBasin(chunk, tx, tz, plan.site);
                }
                if (data != null) {
                    long started = System.nanoTime();
                    if (Boolean.parseBoolean(System.getProperty("jaspr.imported.fastblocks", "true"))) {
                        NativeStamp.stamp(chunk, plan.y, data);
                    } else {
                        this.stamp(chunk, plan.y, data);
                    }
                    long ms = (System.nanoTime() - started) / 1000000L;
                    if (ms > 200L) {
                        ImportedWorldgenPlugin.this.getLogger().warning("IMPORTED_SLOW_TILE site=" + plan.site.id + " tile=" + tx + "," + tz + " records=" + data.size() + " ms=" + ms);
                    }
                    GearLoot.fill(ImportedWorldgenPlugin.this.getLogger(), chunk, plan.y, data, plan.site, ImportedWorldgenPlugin.this.tiles);
                }
            }
            catch (Exception e) {
                ImportedWorldgenPlugin.this.getLogger().severe("IMPORTED_TILE_FAILED chunk=" + cx + "," + cz + " " + e);
                Bukkit.getPluginManager().disablePlugin((Plugin)ImportedWorldgenPlugin.this);
            }
        }

        private void fillBasin(Chunk chunk, int tx, int tz, SiteSpec site) {
            int sizeX = site.dimensions[0];
            int sizeZ = site.dimensions[2];
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    if (tx * 16 + i >= sizeX || tz * 16 + j >= sizeZ) continue;
                    for (int y = 1; y <= 63; ++y) {
                        chunk.getBlock(i, y, j).setTypeIdAndData(9, (byte)0, false);
                    }
                }
            }
        }

        private void stamp(Chunk chunk, int baseY, TileIndex.Data data) {
            for (int i = 0; i < data.size(); ++i) {
                int index = data.index[i];
                int lx = index & 0xF;
                int lz = index >>> 4 & 0xF;
                int wy = baseY + (index >>> 8);
                Block block = chunk.getBlock(lx, wy, lz);
                block.setTypeIdAndData(data.id[i], (byte)data.meta[i], false);
            }
        }
    }

    private static final class Context {
        final ChunkBoundary boundary;
        final CellLedger ledger;
        CellLedger ledger2;
        CellPlanner.Ground ground2;
        CellLedger ledger3;
        CellPlanner.Ground ground3;
        final Terrain terrain;
        final CellPlanner.Ground ground;

        Context(ChunkBoundary boundary, CellLedger ledger, Terrain terrain, CellPlanner.Ground ground) {
            this.boundary = boundary;
            this.ledger = ledger;
            this.terrain = terrain;
            this.ground = ground;
        }
    }
}
