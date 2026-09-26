package chat.jaspr.lostcities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityChest;
import net.minecraft.server.v1_12_R1.TileEntityLootable;
import net.minecraft.server.v1_12_R1.TileEntityMobSpawner;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

/**
 * Test fixture (only with -Djaspr.lostcities.fixture=true): generates the nearest city region and its surroundings
 * in batches, then verifies what was built -- features, loot, spawners, valuables, the reservation contract, that
 * pre-existing chunks and sanctuaries were not changed, street connectivity, how the border meets HorrorBiomes
 * terrain -- logs LOST_CITIES_FIXTURE lines and renders a map of the box into the plugin folder.
 *
 * /lostcities fixture [margin] [batch] [fromChunkX fromChunkZ]
 * /lostcities fixture verify | map
 */
final class Fixture {
    private Fixture() {}

    private static Regions.Region lastRegion;
    private static int[] lastBox;
    private static Map<Long, Long> lastBefore = new HashMap<>();

    static void start(LostCitiesPlugin plugin, LostCitiesPlugin.Context ctx, CommandSender sender, String[] args) {
        World world = ctx.world;
        if (args.length > 1 && (args[1].equalsIgnoreCase("verify") || args[1].equalsIgnoreCase("map"))) {
            if (lastRegion == null) { sender.sendMessage("No fixture region yet."); return; }
            int[] b = lastBox;
            if (args[1].equalsIgnoreCase("verify")) verify(plugin, ctx, lastRegion, b[0], b[1], b[2], b[3], lastBefore);
            else map(plugin, ctx, lastRegion, b[0], b[1], b[2], b[3]);
            return;
        }
        boolean box = args.length > 5 && args[1].equalsIgnoreCase("box");   // fixture box x0 z0 x1 z1 (chunks)
        int margin = box ? 0 : args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int batch = box ? 12 : args.length > 2 ? Integer.parseInt(args[2]) : 12;
        int fromX = box ? (Integer.parseInt(args[2]) + Integer.parseInt(args[4])) / 2 : args.length > 4 ? Integer.parseInt(args[3]) : world.getSpawnLocation().getBlockX() >> 4;
        int fromZ = box ? (Integer.parseInt(args[3]) + Integer.parseInt(args[5])) / 2 : args.length > 4 ? Integer.parseInt(args[4]) : world.getSpawnLocation().getBlockZ() >> 4;
        Regions.Region r = Regions.nearest(world.getSeed(), fromX, fromZ, 2);
        if (r == null) { sender.sendMessage("No region near " + fromX + "," + fromZ); return; }
        final int x0 = box ? Integer.parseInt(args[2]) : r.minX - margin, x1 = box ? Integer.parseInt(args[4]) : r.maxX + margin;
        final int z0 = box ? Integer.parseInt(args[3]) : r.minZ - margin, z1 = box ? Integer.parseInt(args[5]) : r.maxZ + margin;
        log(plugin, "start region=" + r.key() + " name=" + r.name + " centre=" + r.centerX + "," + r.centerZ + " radius=" + r.radius
            + " box=" + x0 + ".." + x1 + "," + z0 + ".." + z1 + " existingInBox=" + countExisting(ctx, x0, x1, z0, z1));

        // Fingerprint every pre-existing chunk in the box before anything is generated around it.
        // Only chunks that were already populated: an unpopulated edge chunk is still owed its (HorrorBiomes)
        // decoration by vanilla rules, which would change it without this plugin being involved.
        Map<Long, Long> before = new HashMap<>();
        int unpopulated = 0;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (ctx.boundary.contains(cx, cz)) {
                    Chunk c = world.getChunkAt(cx, cz);
                    if (((CraftChunk) c).getHandle().isDone()) { before.put(CityWorld.key(cx, cz), hash(c)); SNAP.put(CityWorld.key(cx, cz), blocks(c)); }
                    else unpopulated++;
                }
        log(plugin, "fingerprinted existing=" + before.size() + " unpopulatedEdgeSkipped=" + unpopulated);
        lastRegion = r;
        lastBox = new int[]{x0, x1, z0, z1};
        lastBefore = before;

        List<int[]> todo = new ArrayList<>();
        for (int cx = x0 - 1; cx <= x1 + 1; cx++) for (int cz = z0 - 1; cz <= z1 + 1; cz++) todo.add(new int[]{cx, cz});
        final int[] at = {0};
        final long began = System.nanoTime();
        final int[] task = new int[1];
        task[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int n = 0;
            while (at[0] < todo.size() && n < batch) {
                int[] c = todo.get(at[0]++);
                world.getChunkAt(c[0], c[1]);
                n++;
            }
            if (at[0] % (batch * 50) < batch) log(plugin, "progress " + at[0] + "/" + todo.size());
            if (at[0] >= todo.size()) {
                Bukkit.getScheduler().cancelTask(task[0]);
                long secs = (System.nanoTime() - began) / 1000000000L;
                log(plugin, "loaded chunks=" + todo.size() + " seconds=" + secs);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    verify(plugin, ctx, r, x0, x1, z0, z1, before);
                    map(plugin, ctx, r, x0, x1, z0, z1);
                }, 60L);
            }
        }, 1L, 1L);
        sender.sendMessage("Fixture started: " + todo.size() + " chunks around " + r.name);
    }

    private static int countExisting(LostCitiesPlugin.Context ctx, int x0, int x1, int z0, int z1) {
        int n = 0;
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) if (ctx.boundary.contains(cx, cz)) n++;
        return n;
    }

    private static void log(LostCitiesPlugin plugin, String s) { plugin.getLogger().info("LOST_CITIES_FIXTURE " + s); }

    private static final Map<Long, char[]> SNAP = new HashMap<>();

    /** The chunk's block states as primer chars, index x << 12 | z << 8 | y. */
    private static char[] blocks(Chunk chunk) {
        char[] out = new char[65536];
        ChunkSection[] sections = ((CraftChunk) chunk).getHandle().getSections();
        for (int sy = 0; sy < 16; sy++) {
            ChunkSection s = sections[sy];
            if (s == null) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                out[Driver.index(x, sy * 16 + y, z)] = B.of(s.getType(x, y, z));
        }
        return out;
    }

    private static long hash(Chunk chunk) {
        net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) chunk).getHandle();
        long h = 1125899906842597L;
        ChunkSection[] sections = nms.getSections();
        for (int sy = 0; sy < 16; sy++) {
            ChunkSection s = sections[sy];
            if (s == null) { h = 31 * h + sy; continue; }
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                h = 31 * h + B.of(s.getType(x, y, z));
        }
        return h;
    }

    static void verify(LostCitiesPlugin plugin, LostCitiesPlugin.Context ctx, Regions.Region r, int x0, int x1, int z0, int z1, Map<Long, Long> before) {
        World world = ctx.world;
        CityWorld w = ctx.w;
        int existingChanged = 0, existingGuarded = 0, existingNextToBuilt = 0;
        for (Map.Entry<Long, Long> e : before.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            if (hash(world.getChunkAt(cx, cz)) != e.getValue()) {
                char[] was = SNAP.get(e.getKey()), now = blocks(world.getChunkAt(cx, cz));
                Map<String, Integer> diff = new TreeMap<>();
                int minY = 256, maxY = -1;
                for (int i = 0; was != null && i < 65536; i++) if (was[i] != now[i]) {
                    diff.merge((was[i] >> 4) + ":" + (was[i] & 15) + ">" + (now[i] >> 4) + ":" + (now[i] & 15), 1, Integer::sum);
                    minY = Math.min(minY, i & 255); maxY = Math.max(maxY, i & 255);
                }
                if (!diff.isEmpty()) {
                    existingChanged++;
                    log(plugin, "existingChanged chunk=" + cx + "," + cz + " y=" + minY + ".." + maxY + " diff=" + diff);
                }
            }
            if (plugin.isGuarded(world, cx, cz)) existingGuarded++;
            boolean next = false;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (plugin.isGuarded(world, cx + dx, cz + dz)) next = true;
            if (next) existingNextToBuilt++;
        }
        int regionChunks = 0, regionCity = 0, regionWater = 0, regionChannel = 0, regionBlockedNear = 0;
        int built = 0, builtCity = 0, managed = 0, builtNotManaged = 0, builtNotReserved = 0, managedNotBuilt = 0, cityNotBuilt = 0;
        int reservedOutsideManaged = 0, managedButBlocked = 0, guardMarker = 0;
        Map<Integer, Integer> levels = new TreeMap<>();
        Map<String, Integer> valuables = new TreeMap<>();
        int chests = 0, chestsLootTable = 0, chestsFilled = 0, chestsEmpty = 0, spawners = 0, spawnersTyped = 0;
        Map<String, Integer> spawnerTypes = new TreeMap<>();
        Map<String, Integer> lootTables = new TreeMap<>();
        Map<String, Integer> styles = new TreeMap<>();
        int highwayChunks = 0, railChunks = 0, stations = 0, connectorChunks = 0, buildings = 0, multi = 0, streets = 0, parks = 0;
        long reservedNanos = 0;
        int reservedAsks = 0;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                boolean inRegion = r.contains(cx, cz);
                boolean isCity = w.isCityRaw(cx, cz);
                boolean isManaged = w.managed(cx, cz);
                boolean isBuilt = plugin.isGuarded(world, cx, cz);
                long t = System.nanoTime();
                boolean isReserved = CityApi.reserved(world, cx * 16 + 4, cz * 16 + 4, 8, 8);
                reservedNanos += System.nanoTime() - t;
                reservedAsks++;
                if (inRegion) {
                    regionChunks++;
                    if (isCity) {
                        regionCity++;
                        levels.merge(w.cityLevel(cx, cz), 1, Integer::sum);
                        BuildingInfo bi = w.info(cx, cz);
                        styles.merge(bi.getCityStyle().name.replace("citystyle_", ""), 1, Integer::sum);
                        if (bi.hasBuilding) { buildings++; if (bi.building2x2Section == 0) multi++; }
                        else if (bi.streetType == BuildingInfo.StreetType.PARK || bi.isElevatedParkSection()) parks++;
                        else streets++;
                    }
                    if (w.facts(cx, cz).water) regionWater++;
                    if (w.channel(cx, cz)) regionChannel++;
                    if (w.nearBlocked(cx, cz)) regionBlockedNear++;
                }
                if (isManaged) managed++;
                if (isManaged && w.blocked(cx, cz)) managedButBlocked++;
                if (isManaged && !w.regionOrRing(cx, cz)) connectorChunks++;
                if (w.highway.getXHighwayLevel(cx, cz) >= 0 || w.highway.getZHighwayLevel(cx, cz) >= 0) highwayChunks++;
                Railway.RailChunkInfo rail = w.railway.getRailChunkType(cx, cz);
                if (rail.type != RailChunkType.NONE) railChunks++;
                if (rail.type.isStation()) stations++;
                if (isBuilt) {
                    built++;
                    if (isCity) builtCity++;
                    if (!isManaged) builtNotManaged++;
                    if (!isReserved) builtNotReserved++;
                    if (world.getBlockAt(cx * 16, 0, cz * 16).hasMetadata(LostCitiesPlugin.CHUNK_MARKER)) guardMarker++;
                } else {
                    if (isManaged) managedNotBuilt++;
                    if (isCity) cityNotBuilt++;
                }
                if (!isManaged) {
                    boolean anyManaged = false;
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (w.managed(cx + dx, cz + dz)) anyManaged = true;
                    if (isReserved != (anyManaged && !w.preexisting(cx, cz))) reservedOutsideManaged++;
                }
                if (!isBuilt) continue;
                Chunk chunk = world.getChunkAt(cx, cz);
                net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) chunk).getHandle();
                ChunkSection[] sections = nms.getSections();
                for (int sy = 0; sy < 16; sy++) {
                    ChunkSection s = sections[sy];
                    if (s == null) continue;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        int id = B.of(s.getType(x, y, z)) >> 4;
                        if (B.valuable(id) || id == 137) valuables.merge(String.valueOf(id), 1, Integer::sum);
                    }
                }
                for (TileEntity te : new ArrayList<>(nms.tileEntities.values())) {
                    if (te instanceof TileEntityChest) {
                        chests++;
                        TileEntityLootable l = (TileEntityLootable) te;
                        if (l.getLootTableKey() != null) { chestsLootTable++; lootTables.merge(l.getLootTableKey().toString(), 1, Integer::sum); }
                        else {
                            boolean any = false;
                            for (int i = 0; i < ((TileEntityChest) te).getSize(); i++) if (!((TileEntityChest) te).getItem(i).isEmpty()) any = true;
                            if (any) chestsFilled++; else chestsEmpty++;
                        }
                    } else if (te instanceof TileEntityMobSpawner) {
                        spawners++;
                        net.minecraft.server.v1_12_R1.MinecraftKey k = ((TileEntityMobSpawner) te).getSpawner().getMobName();
                        String name = k == null ? "none" : k.toString();
                        spawnerTypes.merge(name, 1, Integer::sum);
                        if (k != null && !"minecraft:pig".equals(name)) spawnersTyped++;
                    }
                }
            }
        }
        log(plugin, "region chunks=" + regionChunks + " city=" + regionCity + " water=" + regionWater + " channelsKeptOpen=" + regionChannel
            + " nearBlocked=" + regionBlockedNear + " levels=" + levels + " styles=" + styles);
        log(plugin, "plan buildings=" + buildings + " multi2x2=" + multi + " streets=" + streets + " parks=" + parks);
        log(plugin, "box managed=" + managed + " built=" + built + " builtCity=" + builtCity + " connectorsOutsideRing=" + connectorChunks
            + " highwayChunks=" + highwayChunks + " railChunks=" + railChunks + " stations=" + stations);
        log(plugin, "contract builtNotManaged=" + builtNotManaged + " builtNotReserved=" + builtNotReserved + " managedNotBuilt=" + managedNotBuilt
            + " cityNotBuilt=" + cityNotBuilt + " reservedMismatch=" + reservedOutsideManaged + " managedButBlocked=" + managedButBlocked
            + " guardMarker=" + guardMarker + "/" + built + " reservedAvgUs=" + String.format(java.util.Locale.ROOT, "%.2f", reservedNanos / 1e3 / Math.max(1, reservedAsks)));
        log(plugin, "existing fingerprinted=" + before.size() + " changed=" + existingChanged + " guarded=" + existingGuarded + " nextToBuilt=" + existingNextToBuilt);
        sanctuaries(plugin, ctx, x0, x1, z0, z1);
        log(plugin, "valuables " + (valuables.isEmpty() ? "none" : valuables.toString()));
        log(plugin, "chests=" + chests + " withVanillaTable=" + chestsLootTable + " filledLostCity=" + chestsFilled + " empty=" + chestsEmpty + " tables=" + lootTables);
        log(plugin, "spawners=" + spawners + " typed=" + spawnersTyped + " types=" + spawnerTypes);
        streets(plugin, w, r);
        seams(plugin, ctx, x0, x1, z0, z1);
        log(plugin, "features " + plugin.featureCounts());
        log(plugin, "metrics " + plugin.metrics());
        log(plugin, "caches " + w.cacheStats());
        log(plugin, "done");
    }

    // ------------------------------------------------------------------ sanctuaries

    private static void sanctuaries(LostCitiesPlugin plugin, LostCitiesPlugin.Context ctx, int x0, int x1, int z0, int z1) {
        World world = ctx.world;
        Sanctuaries s = new Sanctuaries(world.getSeed(), null);
        int points = 0, possible = 0, haloManaged = 0, haloBuilt = 0, haloChunks = 0;
        StringBuilder where = new StringBuilder();
        for (int[] p : s.latticeIn(x0, z0, x1, z1)) {
            points++;
            if (p[3] == 0) continue;
            possible++;
            where.append(' ').append(new String[]{"portal", "centre", "edgeA", "edgeB"}[p[2]]).append('@').append(p[0]).append(',').append(p[1]);
            for (int dx = -Sanctuaries.HALO; dx <= Sanctuaries.HALO; dx++)
                for (int dz = -Sanctuaries.HALO; dz <= Sanctuaries.HALO; dz++) {
                    int cx = p[0] + dx, cz = p[1] + dz;
                    haloChunks++;
                    if (ctx.w.managed(cx, cz)) haloManaged++;
                    if (plugin.isGuarded(world, cx, cz)) haloBuilt++;
                }
        }
        log(plugin, "sanctuaries latticePoints=" + points + " possible=" + possible + " haloChunks=" + haloChunks + " haloManaged=" + haloManaged
            + " haloBuilt=" + haloBuilt + (where.length() > 0 ? " at" + where : ""));
    }

    // ------------------------------------------------------------------ street connectivity

    /** Street chunks of the region as a graph: same-level neighbours, the mod's stairs between levels, and its bridges. */
    private static void streets(LostCitiesPlugin plugin, CityWorld w, Regions.Region r) {
        Map<Long, Integer> id = new HashMap<>();
        List<long[]> nodes = new ArrayList<>();
        for (int cx = r.minX; cx <= r.maxX; cx++)
            for (int cz = r.minZ; cz <= r.maxZ; cz++) {
                if (!r.contains(cx, cz)) continue;
                BuildingInfo bi = w.info(cx, cz);
                if (!bi.isCity || bi.hasBuilding) continue;
                id.put(CityWorld.key(cx, cz), nodes.size());
                nodes.add(new long[]{cx, cz});
            }
        int n = nodes.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        int stairs = 0, bridges = 0, sameLevel = 0;
        for (int i = 0; i < n; i++) {
            int cx = (int) nodes.get(i)[0], cz = (int) nodes.get(i)[1];
            BuildingInfo bi = w.info(cx, cz);
            Direction st = bi.getActualStairDirection();
            if (st != null) stairs++;
            int[][] dirs = {{1, 0}, {0, 1}};
            for (int[] d : dirs) {
                Integer j = id.get(CityWorld.key(cx + d[0], cz + d[1]));
                if (j == null) continue;
                BuildingInfo o = w.info(cx + d[0], cz + d[1]);
                boolean link = o.cityLevel == bi.cityLevel;
                if (link) sameLevel++;
                if (!link && Math.abs(o.cityLevel - bi.cityLevel) == 1) {
                    BuildingInfo low = o.cityLevel < bi.cityLevel ? o : bi, high = low == o ? bi : o;
                    Direction s = low.getActualStairDirection();
                    if (s != null && s.get(low).same(high)) link = true;
                }
                if (link) union(parent, i, j);
            }
            // Bridges: a run of non-city chunks with the same bridge ending in street chunks.
            for (int k = 0; k < 2; k++) {
                int dx = k == 0 ? 1 : 0, dz = k == 0 ? 0 : 1;
                BuildingInfo next = w.info(cx + dx, cz + dz);
                if (next.isCity || (k == 0 ? next.hasXBridge() : next.hasZBridge()) == null) continue;
                int ex = cx + dx, ez = cz + dz, guard = 0;
                while (!w.info(ex, ez).isCity && guard++ < 64) { ex += dx; ez += dz; }
                Integer j = id.get(CityWorld.key(ex, ez));
                if (j != null) { union(parent, i, j); bridges++; }
            }
        }
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int i = 0; i < n; i++) sizes.merge(find(parent, i), 1, Integer::sum);
        int largest = 0;
        for (int v : sizes.values()) largest = Math.max(largest, v);
        List<Integer> top = new ArrayList<>(sizes.values());
        top.sort((a, b) -> b - a);
        log(plugin, "streets streetChunks=" + n + " components=" + sizes.size() + " largest=" + largest
            + " largestShare=" + String.format(java.util.Locale.ROOT, "%.2f", n == 0 ? 0 : largest / (double) n)
            + " top5=" + top.subList(0, Math.min(5, top.size())) + " sameLevelLinks=" + sameLevel + " stairs=" + stairs + " bridgeLinks=" + bridges);
    }

    private static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }
    private static void union(int[] p, int a, int b) { a = find(p, a); b = find(p, b); if (a != b) p[a] = b; }

    // ------------------------------------------------------------------ border blending

    /** Ground height per column: the top block that is not air, plant, leaf or log (water counts as ground). */
    private static int[] ground(Chunk chunk) {
        net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) chunk).getHandle();
        ChunkSection[] sections = nms.getSections();
        int[] h = new int[256];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int y = 255;
                for (; y > 0; y--) {
                    ChunkSection s = sections[y >> 4];
                    if (s == null) { y = (y & ~15); continue; }
                    int id = B.of(s.getType(x, y & 15, z)) >> 4;
                    switch (id) {
                        case 0: case 6: case 17: case 18: case 31: case 32: case 37: case 38: case 39: case 40: case 78: case 83:
                        case 106: case 111: case 161: case 162: case 175: case 50: case 65: case 66: case 27:
                            continue;
                        default:
                            break;
                    }
                    break;
                }
                h[z * 16 + x] = y;
            }
        return h;
    }

    private static void seams(LostCitiesPlugin plugin, LostCitiesPlugin.Context ctx, int x0, int x1, int z0, int z1) {
        World world = ctx.world;
        CityWorld w = ctx.w;
        Map<Long, int[]> heights = new HashMap<>();
        List<Integer> border = new ArrayList<>(), natural = new ArrayList<>(), old = new ArrayList<>();
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++) {
                for (int k = 0; k < 2; k++) {
                    int ox = cx + (k == 0 ? 1 : 0), oz = cz + (k == 0 ? 0 : 1);
                    if (ox > x1 || oz > z1) continue;
                    boolean a = plugin.isGuarded(world, cx, cz), b = plugin.isGuarded(world, ox, oz);
                    List<Integer> into;
                    if (a != b) {
                        int bx = a ? cx : ox, bz = a ? cz : oz;
                        if (w.info(bx, bz).isCity || w.info(bx, bz).getMaxHighwayLevel() >= 0) continue;   // quays and highway decks are meant to stand out
                        into = (ctx.boundary.contains(a ? ox : cx, a ? oz : cz)) ? old : border;
                    } else if (!a && !w.managed(cx, cz) && !w.managed(ox, oz)) {
                        into = natural;
                    } else continue;
                    int[] ha = heights.computeIfAbsent(CityWorld.key(cx, cz), q -> ground(world.getChunkAt((int) (q >> 32), (int) (long) q)));
                    int[] hb = heights.computeIfAbsent(CityWorld.key(ox, oz), q -> ground(world.getChunkAt((int) (q >> 32), (int) (long) q)));
                    for (int i = 0; i < 16; i++) {
                        int va = k == 0 ? ha[i * 16 + 15] : ha[15 * 16 + i];
                        int vb = k == 0 ? hb[i * 16] : hb[i];
                        into.add(Math.abs(va - vb));
                    }
                }
            }
        log(plugin, "seams ringToNewTerrain " + stats(border) + " | ringToPreexisting " + stats(old) + " | naturalHorrorBiomes " + stats(natural));
    }

    private static String stats(List<Integer> v) {
        if (v.isEmpty()) return "n=0";
        int[] a = new int[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        Arrays.sort(a);
        long sum = 0;
        int over3 = 0;
        for (int x : a) { sum += x; if (x > 3) over3++; }
        return "n=" + a.length + " mean=" + String.format(java.util.Locale.ROOT, "%.2f", sum / (double) a.length) + " p50=" + a[a.length / 2]
            + " p90=" + a[(int) (a.length * 0.9)] + " max=" + a[a.length - 1] + " over3=" + String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * over3 / a.length);
    }

    // ------------------------------------------------------------------ map

    static void map(LostCitiesPlugin plugin, LostCitiesPlugin.Context ctx, Regions.Region r, int x0, int x1, int z0, int z1) {
        try {
            World world = ctx.world;
            WorldServer ws = ((CraftWorld) world).getHandle();
            int W = (x1 - x0 + 1) * 16, H = (z1 - z0 + 1) * 16;
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            int[][] top = new int[W][H];
            IBlockData[][] state = new IBlockData[W][H];
            for (int cx = x0; cx <= x1; cx++)
                for (int cz = z0; cz <= z1; cz++) {
                    net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) world.getChunkAt(cx, cz)).getHandle();
                    ChunkSection[] sections = nms.getSections();
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            int y = 255;
                            IBlockData s = null;
                            for (; y > 0; y--) {
                                ChunkSection sec = sections[y >> 4];
                                if (sec == null) { y &= ~15; continue; }
                                IBlockData d = sec.getType(x, y & 15, z);
                                if (B.of(d) >> 4 != 0) { s = d; break; }
                            }
                            int px = (cx - x0) * 16 + x, pz = (cz - z0) * 16 + z;
                            top[px][pz] = y;
                            state[px][pz] = s;
                        }
                }
            for (int px = 0; px < W; px++)
                for (int pz = 0; pz < H; pz++) {
                    IBlockData s = state[px][pz];
                    int rgb = 0;
                    if (s != null) {
                        BlockPosition pos = new BlockPosition(x0 * 16 + px, top[px][pz], z0 * 16 + pz);
                        rgb = s.getBlock().c(s, (net.minecraft.server.v1_12_R1.IBlockAccess) ws, pos).ac;
                    }
                    int north = pz > 0 ? top[px][pz - 1] : top[px][pz];
                    double f = top[px][pz] > north ? 1.0 : top[px][pz] < north ? 0.72 : 0.86;
                    img.setRGB(px, pz, shade(rgb, f));
                }
            // Outline pre-existing land (cyan) and possible sanctuary halos (red).
            for (int cx = x0; cx <= x1; cx++)
                for (int cz = z0; cz <= z1; cz++) {
                    if (ctx.boundary.contains(cx, cz)) outline(img, (cx - x0) * 16, (cz - z0) * 16, 16, 16, 0x00c0c0, ctx, cx, cz, x0, z0, x1, z1);
                }
            Sanctuaries s = new Sanctuaries(world.getSeed(), null);
            for (int[] p : s.latticeIn(x0, z0, x1, z1)) {
                if (p[3] == 0) continue;
                int ax = (p[0] - Sanctuaries.HALO - x0) * 16, az = (p[1] - Sanctuaries.HALO - z0) * 16, size = (2 * Sanctuaries.HALO + 1) * 16;
                for (int i = 0; i < size; i++) {
                    set(img, ax + i, az, 0xff2020); set(img, ax + i, az + size - 1, 0xff2020);
                    set(img, ax, az + i, 0xff2020); set(img, ax + size - 1, az + i, 0xff2020);
                }
            }
            File dir = plugin.getDataFolder();
            dir.mkdirs();
            String base = "fixture-" + r.name.toLowerCase();
            ImageIO.write(img, "png", new File(dir, base + ".png"));
            // Two vertical sections through the centre.
            int rowZ = r.centerZ * 16 + 8, colX = r.centerX * 16 + 8;
            BufferedImage sx = new BufferedImage(W, 170, BufferedImage.TYPE_INT_RGB), sz = new BufferedImage(H, 170, BufferedImage.TYPE_INT_RGB);
            for (int px = 0; px < W; px++) section(sx, px, world, ws, x0 * 16 + px, rowZ);
            for (int pz = 0; pz < H; pz++) section(sz, pz, world, ws, colX, z0 * 16 + pz);
            ImageIO.write(sx, "png", new File(dir, base + "_x.png"));
            ImageIO.write(sz, "png", new File(dir, base + "_z.png"));
            log(plugin, "map " + base + ".png " + W + "x" + H + " sections z=" + rowZ + " x=" + colX);
        } catch (Throwable t) {
            log(plugin, "map failed " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void outline(BufferedImage img, int px, int pz, int w, int h, int rgb, LostCitiesPlugin.Context ctx, int cx, int cz, int x0, int z0, int x1, int z1) {
        if (!ctx.boundary.contains(cx - 1, cz)) for (int i = 0; i < h; i++) set(img, px, pz + i, rgb);
        if (!ctx.boundary.contains(cx + 1, cz)) for (int i = 0; i < h; i++) set(img, px + w - 1, pz + i, rgb);
        if (!ctx.boundary.contains(cx, cz - 1)) for (int i = 0; i < w; i++) set(img, px + i, pz, rgb);
        if (!ctx.boundary.contains(cx, cz + 1)) for (int i = 0; i < w; i++) set(img, px + i, pz + h - 1, rgb);
    }

    private static void set(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, rgb);
    }

    private static void section(BufferedImage img, int col, World world, WorldServer ws, int bx, int bz) {
        net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) world.getChunkAt(bx >> 4, bz >> 4)).getHandle();
        for (int yy = 0; yy < 170; yy++) {
            int y = 30 + yy;
            ChunkSection sec = nms.getSections()[y >> 4];
            IBlockData d = sec == null ? null : sec.getType(bx & 15, y & 15, bz & 15);
            int rgb = 0x101018;
            if (d != null && B.of(d) >> 4 != 0) rgb = d.getBlock().c(d, (net.minecraft.server.v1_12_R1.IBlockAccess) ws, new BlockPosition(bx, y, bz)).ac;
            img.setRGB(col, 169 - yy, rgb);
        }
    }

    private static int shade(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }
}
