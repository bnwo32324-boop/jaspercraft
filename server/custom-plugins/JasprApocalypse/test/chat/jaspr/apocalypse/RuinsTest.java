package chat.jaspr.apocalypse;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Standalone main; no server, world files, JUnit, NMS initialization, or gun-module dependency.
 * Compile alongside Ruins against the target API and the lead's classes (or temporary
 * minimal ApocalypsePlugin/ApocalypseItems stubs), then run with java -ea. Tests only
 * write disposable journal files under the OS temp directory. Relic NBT integration
 * belongs to the real Paper probe; geometry checks validate designated cache flags.
 */
@SuppressWarnings("deprecation")
public final class RuinsTest {
    private static int assertions;
    private static final int[][] DIRECTIONS = { {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1} };

    public static void main(String[] arguments) throws Exception {
        siteChecks();
        distribution();
        blueprints();
        nativeRailValidation();
        journal();
        modestLoot();
        System.out.println("RuinsTest PASS: " + assertions + " assertions; 768 blueprints, six families, rotations, terrain, access, support, native rail regression, journal, loot.");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void siteChecks() {
        Terrain terrain = new Terrain();
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) != null, "flat meadow accepted");
        check(Ruins.inspect(terrain.snapshot(), 256, true, 2) != null, "solid bunker site accepted");
        terrain.change(7, 65, 7, Material.LOG);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "tree rejected");
        terrain = new Terrain(); terrain.change(7, 64, 7, Material.WATER);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "water rejected");
        terrain = new Terrain(); terrain.change(7, 65, 7, Material.CHEST);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "container rejected");
        terrain = new Terrain(); terrain.change(7, 64, 7, Material.COBBLESTONE);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "existing construction rejected");
        terrain = new Terrain(); terrain.change(7, 62, 7, Material.AIR);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "hollow foundation rejected");
        terrain = new Terrain(); terrain.change(7, 57, 7, Material.AIR);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) != null, "deep cave irrelevant to surface site");
        check(Ruins.inspect(terrain.snapshot(), 256, true, 2) == null, "bunker cave rejected");
        terrain = new Terrain(); terrain.change(7, 61, 7, Material.BEDROCK);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "protected block rejected");
        terrain = new Terrain(); terrain.biome = Biome.SWAMPLAND;
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "swamp rejected");
        terrain = new Terrain();
        for (int x = 8; x < 16; x++) Arrays.fill(terrain.heights[x], 66);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) == null, "abrupt two-block ledge rejected");
        terrain = new Terrain();
        for (int x = 0; x < 16; x++) Arrays.fill(terrain.heights[x], 64 + x / 6);
        check(Ruins.inspect(terrain.snapshot(), 256, false, 2) != null, "gentle slope accepted");
        check(Ruins.inspect(terrain.snapshot(), 256, false, 1) == null, "configured slope enforced");
    }

    private static void distribution() {
        YamlConfiguration config = new YamlConfiguration();
        Ruins.Settings settings = new Ruins.Settings(config);
        Set<Long> selected = new HashSet<Long>();
        int[] quadrants = new int[4];
        for (int x = -192; x < 192; x++) for (int z = -192; z < 192; z++) {
            boolean result = Ruins.selected(919191L, x, z, settings);
            check(result == Ruins.selected(919191L, x, z, settings), "selection deterministic");
            if (result) { selected.add(pair(x, z)); quadrants[(x < 0 ? 1 : 0) + (z < 0 ? 2 : 0)]++; }
        }
        check(selected.size() > 500 && selected.size() < 10000, "sane district occupancy " + selected.size());
        for (int quadrant : quadrants) check(quadrant > 100, "negative-coordinate district coverage");
        for (long key : selected) {
            int x = (int) (key >> 32), z = (int) key;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) check(!selected.contains(pair(x + dx, z + dz)), "spacing at region seams");
            }
        }
        config.set("ruins.edits-per-tick", 9999);
        config.set("ruins.max-pending-plans", 9999);
        config.set("ruins.district-chance", 0);
        config.set("ruins.isolated-chance", 0);
        settings = new Ruins.Settings(config);
        check(settings.editsPerTick == 150 && settings.pending == 128, "hard bounds override config");
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) check(!Ruins.selected(3L, x, z, settings), "zero density");
    }

    private static long pair(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static void blueprints() {
        int maxEdits = 0;
        for (Ruins.Family family : Ruins.Family.values()) for (int variant = 0; variant < 2; variant++) {
            for (int rotation = 0; rotation < 4; rotation++) for (int seed = 0; seed < 16; seed++) {
                Terrain terrain = new Terrain();
                // Exercise the grounded layouts on flat land and both slope directions.
                if (seed % 3 != 0) for (int x = 0; x < 16; x++) Arrays.fill(terrain.heights[x], 64 + (seed % 3 == 1 ? x : 15 - x) / 6);
                Ruins.Site site = Ruins.inspect(terrain.snapshot(), 256, family == Ruins.Family.BUNKER, 2);
                check(site != null, "test site valid");
                boolean relic = family == Ruins.Family.SHRINE || family == Ruins.Family.BUNKER;
                Ruins.Builder b = Ruins.blueprint(site, new Random(seed), family, relic, rotation, variant);
                String label = family + "/" + variant + "/" + rotation + "/" + seed;
                List<Ruins.Edit> edits = b.edits();
                maxEdits = Math.max(maxEdits, edits.size());
                check(edits.size() <= 3200, "bounded blueprint " + label);
                check(b.validCaches(), "valid cache " + label);
                int phase = -1;
                Set<Integer> positions = new HashSet<Integer>();
                Ruins.Edit cache = null;
                for (Ruins.Edit edit : edits) {
                    check(edit.x >= 2 && edit.x <= 13 && edit.z >= 2 && edit.z <= 13, "chunk border " + label);
                    check(edit.y >= site.floor - 9 && edit.y <= site.floor + 9, "vertical bounds " + label);
                    check(positions.add(Ruins.position(edit.x, edit.y, edit.z)), "no repeated writes " + label);
                    check(edit.phase() >= phase, "placement ordering " + label);
                    phase = edit.phase();
                    if (edit.cache) cache = edit;
                    if (edit.type == Material.LADDER) {
                        int dx = edit.data == 4 ? 1 : edit.data == 5 ? -1 : 0;
                        int dz = edit.data == 2 ? 1 : edit.data == 3 ? -1 : 0;
                        check(b.at(edit.x + dx, edit.y, edit.z + dz).isOccluding(), "ladder backing " + label);
                    }
                }
                check(cache != null && cache.relic == relic && edits.get(edits.size() - 1).cache, "cache policy/last edit " + label);
                anchored(b, edits, label);
                accessible(b, cache, label);
            }
        }
        System.out.println("Largest tested blueprint: " + maxEdits + " edits.");
    }

    private static void anchored(Ruins.Builder b, List<Ruins.Edit> edits, String label) {
        Set<Integer> reached = new HashSet<Integer>();
        Queue<int[]> queue = new ArrayDeque<int[]>();
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) {
            queue.add(new int[] { x, b.site.floor - 10, z });
            reached.add(Ruins.position(x, b.site.floor - 10, z));
        }
        while (!queue.isEmpty()) {
            int[] current = queue.remove();
            for (int[] d : DIRECTIONS) {
                int x = current[0] + d[0], y = current[1] + d[1], z = current[2] + d[2];
                if (x < 1 || x > 14 || z < 1 || z > 14 || y < b.site.floor - 10 || y > b.site.floor + 9) continue;
                if (b.at(x, y, z).isSolid() && reached.add(Ruins.position(x, y, z))) queue.add(new int[] { x, y, z });
            }
        }
        for (Ruins.Edit edit : edits) {
            if (edit.type.isSolid()) check(reached.contains(Ruins.position(edit.x, edit.y, edit.z)), "floating block " + label + " at " + edit.x + "," + edit.y + "," + edit.z);
        }
    }

    private static boolean passable(Material material) { return Ruins.soft(material) || material == Material.VINE || material == Material.LADDER; }
    private static boolean stand(Ruins.Builder b, int x, int y, int z) {
        if (!passable(b.at(x, y, z)) || !passable(b.at(x, y + 1, z))) return false;
        Material under = b.at(x, y - 1, z);
        return b.at(x, y, z) == Material.LADDER || (under.isSolid() && under != Material.FENCE
                && under != Material.IRON_FENCE && under != Material.COBBLE_WALL);
    }
    private static void accessible(Ruins.Builder b, Ruins.Edit cache, String label) {
        Set<Integer> reached = new HashSet<Integer>();
        Queue<int[]> queue = new ArrayDeque<int[]>();
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 14; z++) if (x == 1 || x == 14 || z == 1 || z == 14) {
            int y = b.site.heights[x][z] + 1;
            if (stand(b, x, y, z) && reached.add(Ruins.position(x, y, z))) queue.add(new int[] { x, y, z });
        }
        boolean accessible = false;
        while (!queue.isEmpty()) {
            int[] current = queue.remove();
            if (Math.abs(current[0] - cache.x) + Math.abs(current[2] - cache.z) == 1
                    && Math.abs(current[1] - cache.y) <= 1) accessible = true;
            for (int[] d : DIRECTIONS) for (int step = -1; step <= 1; step++) {
                if (d[1] != 0 && (step != 0 || b.at(current[0], current[1], current[2]) != Material.LADDER)) continue;
                int x = current[0] + d[0], y = current[1] + d[1] + step, z = current[2] + d[2];
                if (x < 1 || x > 14 || z < 1 || z > 14 || y < b.site.floor - 8 || y > b.site.floor + 8) continue;
                if (stand(b, x, y, z) && reached.add(Ruins.position(x, y, z))) queue.add(new int[] { x, y, z });
            }
        }
        check(accessible, "two-block-clear cache access " + label);
    }

    private interface IoAction { void run() throws IOException; }

    private static void nativeRailValidation() {
        // Actual Paper regression: seed 6840227782638526189, flat Y63,
        // SALVAGE chunk -160,152. Four adjoining track ends become flat
        // corners; the old exact-data validator aborted before the cache.
        Terrain terrain = new Terrain();
        for (int[] row : terrain.heights) Arrays.fill(row, 63);
        Ruins.Site site = Ruins.inspect(terrain.snapshot(), 256, false, 2);
        Random random = new Random(Ruins.chunkSeed(6840227782638526189L, -160, 152));
        Ruins.Family family = Ruins.family(random.nextInt(100));
        check(family == Ruins.Family.SALVAGE, "real failing fixture family");
        Ruins.Builder builder = Ruins.blueprint(site, random, family, false, random.nextInt(4), random.nextInt(2));
        int[][] corners = { {2,65,4,6}, {13,65,4,7}, {2,65,5,9}, {13,65,5,8} };
        int changed = 0;
        for (Ruins.Edit edit : builder.edits()) {
            if (edit.cache) continue;
            byte actual = edit.data;
            for (int[] corner : corners) if (edit.x == corner[0] && edit.y == corner[1] && edit.z == corner[2]) {
                check(edit.type == Material.RAILS && edit.data == 1, "original real-fixture straight rail");
                actual = (byte) corner[3];
                changed++;
            }
            check(Ruins.matchesPlaced(edit, edit.type, actual, true), "native rail reconnection must not suppress the cache");
            check(!Ruins.matchesPlaced(edit, Material.BEDROCK, actual, true), "replacement still invalidates build");
            if (edit.type == Material.RAILS) {
                check(!Ruins.matchesPlaced(edit, Material.AIR, edit.data, true), "missing rail still invalidates build");
                check(!Ruins.matchesPlaced(edit, Material.POWERED_RAIL, edit.data, true), "different rail type still invalidates build");
                for (byte nativeState = 0; nativeState <= 9; nativeState++) {
                    check(Ruins.matchesPlaced(edit, Material.RAILS, nativeState, true), "valid native rail state accepted");
                    check(!Ruins.matchesPlaced(edit, Material.RAILS, nativeState, false), "unsupported rail rejected");
                }
                for (byte invalid = 10; invalid <= 15; invalid++) check(!Ruins.matchesPlaced(edit, Material.RAILS, invalid, true), "invalid rail metadata rejected");
                check(!Ruins.matchesPlaced(edit, Material.RAILS, (byte) -1, true), "negative rail metadata rejected");
            } else {
                check(!Ruins.matchesPlaced(edit, edit.type, (byte) (edit.data ^ 1), true), "non-rail metadata remains exact");
            }
        }
        check(changed == 4, "all four real Paper rail corners covered");
        check(builder.validCaches(), "regression blueprint retains one usable cache");
    }

    private static void rejects(IoAction action, String message) throws IOException {
        try { action.run(); } catch (IOException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    private static void journal() throws IOException {
        Path directory = Files.createTempDirectory("jaspr-ruins-test-");
        Path path = directory.resolve("ledger.bin");
        Path damaged = directory.resolve("damaged.bin");
        try {
            UUID world = UUID.randomUUID();
            Ruins.Key key = new Ruins.Key(world, -31, 97);
            Ruins.Key otherWorld = new Ruins.Key(UUID.randomUUID(), -31, 97);
            Ruins.Ledger first = new Ruins.Ledger(path.toFile(), 2);
            first.reserve(key);
            Ruins.Ledger crashed = new Ruins.Ledger(path.toFile(), 2);
            check(crashed.contains(key) && !crashed.completed(key), "interruption retained, not falsely complete");
            rejects(() -> crashed.reserve(key), "partial build never replayed");
            crashed.complete(key);
            Ruins.Ledger completed = new Ruins.Ledger(path.toFile(), 2);
            check(completed.completed(key), "completion durable");
            rejects(() -> completed.reserve(key), "completed loot never repeated");
            completed.reserve(otherWorld);
            check(completed.contains(otherWorld), "world UUID namespaces chunk markers");
            long size = Files.size(path);
            rejects(() -> completed.reserve(new Ruins.Key(world, 1, 2)), "capacity stops rather than evicts");
            check(Files.size(path) == size, "full journal does not grow");
            byte[] bytes = Files.readAllBytes(path);
            bytes[20] ^= 1;
            Files.write(damaged, bytes);
            rejects(() -> new Ruins.Ledger(damaged.toFile(), 2), "checksum corruption stops generation");
            Files.write(path, new byte[] { 1 }, StandardOpenOption.APPEND);
            rejects(() -> new Ruins.Ledger(path.toFile(), 2), "torn tail stops generation");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(damaged);
            Files.deleteIfExists(directory);
        }
    }

    private static void modestLoot() {
        Set<Material> allowed = new HashSet<Material>(Arrays.asList(Material.BREAD, Material.COAL, Material.STRING,
                Material.STICK, Material.PAPER, Material.TORCH, Material.CARROT_ITEM));
        for (int seed = 0; seed < 1000; seed++) {
            ItemStack[] contents = Ruins.loot(new Random(seed), false, 0);
            int count = 0, stacks = 0;
            for (ItemStack item : contents) if (item != null) {
                check(allowed.contains(item.getType()), "no early gun/gear loot");
                check(item.getAmount() >= 1 && item.getAmount() <= 3, "modest stack size");
                count += item.getAmount(); stacks++;
            }
            check(stacks >= 3 && stacks <= 4 && count <= 12, "modest supply cache");
        }
    }

    private static final class Terrain {
        final int[][] heights = new int[16][16];
        final Map<Integer, Material> overrides = new HashMap<Integer, Material>();
        Biome biome = Biome.PLAINS;
        Terrain() { for (int[] row : heights) Arrays.fill(row, 64); }
        void change(int x, int y, int z, Material material) { overrides.put(Ruins.position(x, y, z), material); }
        Material at(int x, int y, int z) {
            check(x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 256, "snapshot bounds");
            Material material = overrides.get(Ruins.position(x, y, z));
            return material != null ? material : y > heights[x][z] ? Material.AIR
                    : y == heights[x][z] ? Material.GRASS : y >= heights[x][z] - 3 ? Material.DIRT : Material.STONE;
        }
        ChunkSnapshot snapshot() {
            return (ChunkSnapshot) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ChunkSnapshot.class }, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("getBlockType")) return at((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                if (name.equals("getBlockTypeId")) return at((Integer) args[0], (Integer) args[1], (Integer) args[2]).getId();
                if (name.equals("getBlockData")) return 0;
                if (name.equals("getBiome")) return biome;
                if (name.equals("getHighestBlockYAt")) {
                    int x = (Integer) args[0], z = (Integer) args[1];
                    for (int y = 255; y > 0; y--) if (at(x, y, z) != Material.AIR) return y + 1;
                    return 0;
                }
                throw new AssertionError("Unexpected snapshot method " + method);
            });
        }
    }
}
