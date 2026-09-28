package chat.jaspr.lostcities;

import chat.jaspr.biomes.ChunkLight;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityLootable;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

/**
 * Turns one chunk into its Lost City form: reads the chunk into a primer, runs the generator, applies the biome
 * surface, writes the difference straight into the chunk sections, places tile-entity blocks through Bukkit,
 * relights once, then finishes the mod's populate stage (spawners, loot, saplings to trees, vines).
 */
final class Builder {
    interface Guard { void guard(int cx, int cz) throws Exception; }

    static final class Outcome {
        boolean city, changed;
        int blocks, chests, lostCityChests, vanillaChests, emptyChests, spawners, trees, vines, valuables;
        long computeNanos, writeNanos;
        List<String> features;
        final List<String> spawnerMobs = new ArrayList<>();
    }

    /** Cumulative write-phase nanos (main thread): guard, sections, tile blocks, relight, spawners+loot, trees, vines. */
    static final long[] PHASES = new long[7];
    static final String[] PHASE_NAMES = {"guard", "sections", "tiles", "light", "loot", "trees", "vines"};

    static String phases() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < PHASES.length; i++) b.append(i == 0 ? "" : " ").append(PHASE_NAMES[i]).append('=').append(PHASES[i] / 1000000L).append("ms");
        return b.toString();
    }

    private final CityWorld w;
    private final CityGenerator gen;
    private final World world;
    private final Guard guard;
    private final CityApi.PrimerHook hook;   // registered worlds only (JasprRuins' weathering); null in the overworld
    private final char[] original = new char[65536];
    private double[] depth = new double[256];

    Builder(CityWorld w, World world, Guard guard) { this(w, world, guard, null); }

    Builder(CityWorld w, World world, Guard guard, CityApi.PrimerHook hook) {
        this.w = w;
        this.gen = new CityGenerator(w);
        this.world = world;
        this.guard = guard;
        this.hook = hook;
    }

    Outcome build(Chunk chunk, java.util.function.LongPredicate built) throws Exception {
        long t0 = System.nanoTime();
        int cx = chunk.getX(), cz = chunk.getZ();
        net.minecraft.server.v1_12_R1.Chunk nms = ((CraftChunk) chunk).getHandle();
        read(nms, original);
        BuildingInfo info = w.info(cx, cz);
        char[] primer = info.isCity ? new char[65536] : original.clone();
        CityGenerator.Result res = gen.generate(cx, cz, primer);

        Random surfaceRand = new Random(w.seed ^ (cx * 341873128712L + cz * 132897987541L) ^ 0x5355524641L);
        depth = w.surfaceNoise.a(depth, cx * 16, cz * 16, 16, 16, 0.0625D, 0.0625D, 1.0D);
        if (info.isCity) {
            Surface.apply(w, cx, cz, primer, depth, surfaceRand);
            restoreTerrain(primer, info.getCityGroundLevel());
        } else {
            int[] top = changedTops(primer);
            Surface.apply(w, cx, cz, primer, depth, surfaceRand, original, top);
        }
        int valuables = sanitize(primer);
        if (hook != null) {
            hook.apply(cx, cz, primer, info.isCity, info.isCity ? info.getCityGroundLevel() : -1);
            valuables += sanitize(primer);
        }

        Outcome out = new Outcome();
        out.city = info.isCity;
        out.features = res.features;
        out.valuables = valuables;
        int diff = 0;
        for (int i = 0; i < 65536; i++) if (primer[i] != original[i]) diff++;
        long t1 = System.nanoTime();
        out.computeNanos = t1 - t0;
        if (diff == 0) { out.writeNanos = 0; return out; }

        // Guard before any block is written (HorrorBiomes' water/floater repair then leaves the chunk alone).
        guard.guard(cx, cz);
        PHASES[0] += System.nanoTime() - t1;
        out.changed = true;
        out.blocks = write(chunk, nms, primer);
        finish(chunk, info, primer, res, out, built);
        out.writeNanos = System.nanoTime() - t1;
        return out;
    }

    // ------------------------------------------------------------------ primer I/O

    private static void read(net.minecraft.server.v1_12_R1.Chunk nms, char[] data) {
        java.util.Arrays.fill(data, B.AIR);
        ChunkSection[] sections = nms.getSections();
        for (int sy = 0; sy < 16; sy++) {
            ChunkSection s = sections[sy];
            if (s == null) continue;
            int base = sy << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        IBlockData st = s.getType(x, y, z);
                        data[Driver.index(x, base + y, z)] = B.of(st);
                    }
        }
    }

    /** Highest y changed per column, or -1. */
    private int[] changedTops(char[] primer) {
        int[] top = new int[256];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int t = -1;
                int base = Driver.index(x, 0, z);
                for (int y = 255; y >= 0; y--) if (primer[base + y] != original[base + y]) { t = y; break; }
                top[z * 16 + x] = t;
            }
        return top;
    }

    /**
     * The city's solid base is plain stone (the mod generated it into an empty chunk and let the biome decorator add
     * ores afterwards). Below the street level the HorrorBiomes rock that was there -- ores, granite, diorite,
     * andesite, bedrock -- is kept wherever the city only put base stone.
     */
    private void restoreTerrain(char[] primer, int cityGround) {
        int limit = Math.max(1, cityGround - 8);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int base = Driver.index(x, 0, z);
                for (int y = 0; y < limit; y++) {
                    int i = base + y;
                    if (primer[i] != B.STONE && !(y == 0 && primer[i] == B.BEDROCK)) continue;
                    char o = original[i];
                    int id = o >> 4;
                    if (id == 1 || id == 7 || id == 14 || id == 15 || id == 16 || id == 21 || id == 56 || id == 73 || id == 74 || id == 129)
                        primer[i] = o;
                }
            }
    }

    /** Last line of the owner's rules: no valuable blocks placed, no command blocks placed. */
    private int sanitize(char[] primer) {
        int found = 0;
        for (int i = 0; i < 65536; i++) {
            char c = primer[i];
            if (c == original[i]) continue;
            int id = c >> 4;
            if (id == 137) { primer[i] = B.AIR; found++; }
            else if (B.valuable(id)) { primer[i] = B.sanitize(c); found++; }
        }
        return found;
    }

    private int write(Chunk chunk, net.minecraft.server.v1_12_R1.Chunk nms, char[] primer) {
        // Stale tile entities where the block changes.
        if (!nms.tileEntities.isEmpty()) {
            for (BlockPosition pos : new ArrayList<>(nms.tileEntities.keySet())) {
                if ((pos.getX() >> 4) != chunk.getX() || (pos.getZ() >> 4) != chunk.getZ()) continue;
                int y = pos.getY();
                if (y < 0 || y > 255) continue;
                int i = Driver.index(pos.getX() & 15, y, pos.getZ() & 15);
                if (primer[i] == original[i]) continue;
                TileEntity stale = nms.tileEntities.remove(pos);
                if (stale != null) stale.z();
            }
        }
        long p0 = System.nanoTime();
        ChunkSection[] sections = nms.getSections();
        boolean skylight = nms.world.worldProvider.m();
        int changed = 0;
        List<Integer> tiles = new ArrayList<>();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 256; y++) {
                    int i = Driver.index(x, y, z);
                    char c = primer[i];
                    if (c == original[i]) continue;
                    if (B.tile(c)) { tiles.add(i); continue; }
                    int sy = y >> 4;
                    ChunkSection section = sections[sy];
                    if (section == null) {
                        if (c == B.AIR) continue;
                        section = new ChunkSection(sy << 4, skylight);
                        sections[sy] = section;
                    }
                    section.setType(x, y & 15, z, B.state(c));
                    changed++;
                }
        long p1 = System.nanoTime();
        PHASES[1] += p1 - p0;
        for (int i : tiles) {
            char c = primer[i];
            int x = (i >> 12) & 15, z = (i >> 8) & 15, y = i & 0xff;
            chunk.getBlock(x, y, z).setTypeIdAndData(c >> 4, (byte) (c & 15), false);
            changed++;
        }
        long p2 = System.nanoTime();
        PHASES[2] += p2 - p1;
        nms.initLighting();
        ChunkLight.initialize(chunk);
        nms.markDirty();
        PHASES[3] += System.nanoTime() - p2;
        return changed;
    }

    // ------------------------------------------------------------------ populate stage

    private void finish(Chunk chunk, BuildingInfo info, char[] primer, CityGenerator.Result res, Outcome out, java.util.function.LongPredicate built) {
        long q0 = System.nanoTime();
        int cx = chunk.getX(), cz = chunk.getZ();
        Random random = new Random(w.seed ^ Regions.mix(cx * 31L + cz * 17L + 0x4C4F4F54L));
        String biome = w.biomes(cx, cz)[0];

        for (CityGenerator.Todo t : res.spawners) {
            if ((primer[Driver.index(t.x, t.y, t.z)] >> 4) != 52) continue;   // destroyed by an explosion
            Block b = chunk.getBlock(t.x, t.y, t.z);
            BlockState state = b.getState();
            if (!(state instanceof CreatureSpawner)) continue;
            Assets.Condition cnd = w.assets.conditions.get(t.condition);
            if (cnd == null) continue;
            ConditionContext ctx = context(info, t, biome);
            String mob = cnd.getRandomValue(random, ctx);
            EntityType type = entity(mob);
            if (type == null) continue;
            ((CreatureSpawner) state).setSpawnedType(type);
            state.update(true, false);
            out.spawners++;
            out.spawnerMobs.add(type.name());
        }

        for (CityGenerator.Todo t : res.loot) {
            if ((primer[Driver.index(t.x, t.y, t.z)] >> 4) != 54) continue;
            out.chests++;
            if (!Profile.GENERATE_LOOT) continue;
            if (random.nextFloat() < Profile.CHEST_WITHOUT_LOOT_CHANCE) { out.emptyChests++; continue; }
            Assets.Condition cnd = w.assets.conditions.get(t.condition);
            if (cnd == null) continue;
            String table = cnd.getRandomValue(random, context(info, t, biome));
            if (table == null) continue;
            Block b = chunk.getBlock(t.x, t.y, t.z);
            if (Loot.isOwnTable(table)) {
                BlockState state = b.getState();
                if (state instanceof Chest) {
                    Loot.fill(((Chest) state).getBlockInventory(), table, random);   // live inventory; never update()
                    out.lostCityChests++;
                }
            } else {
                TileEntity te = ((CraftWorld) world).getHandle().getTileEntity(new BlockPosition(b.getX(), b.getY(), b.getZ()));
                if (te instanceof TileEntityLootable) {
                    ((TileEntityLootable) te).setLootTable(new MinecraftKey(table), random.nextLong());
                    te.update();
                    out.vanillaChests++;
                }
            }
        }

        long q1 = System.nanoTime();
        // Saplings grow into trees where the whole neighbourhood is loaded and ours to write (as the mod's populate).
        if (!res.saplings.isEmpty() && neighbourhoodWritable(cx, cz)) {
            for (CityGenerator.Todo t : res.saplings) {
                Block b = chunk.getBlock(t.x, t.y, t.z);
                if (b.getType() != Material.SAPLING) continue;
                int meta = b.getData() & 7;
                TreeType type;
                switch (meta) {
                    case 1: type = TreeType.REDWOOD; break;
                    case 2: type = TreeType.BIRCH; break;
                    case 3: type = TreeType.SMALL_JUNGLE; break;
                    case 4: type = TreeType.ACACIA; break;
                    case 5: type = TreeType.DARK_OAK; break;
                    default: type = random.nextInt(10) == 0 ? TreeType.BIG_TREE : TreeType.TREE; break;
                }
                byte data = b.getData();
                b.setType(Material.AIR, false);
                if (world.generateTree(new Location(world, b.getX(), b.getY(), b.getZ()), type)) out.trees++;
                else b.setTypeIdAndData(6, data, false);
            }
        }

        long q2 = System.nanoTime();
        out.vines = hook != null && !hook.vines() ? 0 : vines(cx, cz, built);
        long q3 = System.nanoTime();
        PHASES[4] += q1 - q0;
        PHASES[5] += q2 - q1;
        PHASES[6] += q3 - q2;

        if (info.isCity) {
            for (Entity e : chunk.getEntities()) {
                if (e instanceof Player) continue;
                if (e instanceof Hanging) { e.remove(); continue; }
                Block at = e.getLocation().getBlock();
                if (at.getType().isOccluding() || at.getRelative(0, 1, 0).getType().isOccluding()) e.remove();
            }
        }
    }

    private boolean neighbourhoodWritable(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) return false;
                if (!w.managed(cx + dx, cz + dz)) return false;
            }
        return true;
    }

    private static ConditionContext context(BuildingInfo info, CityGenerator.Todo t, String biome) {
        int level = (t.y - Profile.GROUNDLEVEL) / 6;
        int floor = (t.y - info.getCityGroundLevel()) / 6;
        return new ConditionContext(level, floor, info.floorsBelowGround, info.getNumFloors(), t.part, t.building,
            info.chunkX, info.chunkZ, biome, false);
    }

    /** EntityId.fixTagCompound for the mod's old mob names. */
    static EntityType entity(String mob) {
        if (mob == null) return null;
        String m = mob.toLowerCase();
        if (m.startsWith("minecraft:")) m = m.substring(10);
        switch (m) {
            case "zombie": return EntityType.ZOMBIE;
            case "skeleton": return EntityType.SKELETON;
            case "spider": return EntityType.SPIDER;
            case "blaze": return EntityType.BLAZE;
            case "witch": return EntityType.WITCH;
            case "cavespider": case "cave_spider": return EntityType.CAVE_SPIDER;
            default:
                @SuppressWarnings("deprecation") EntityType t = EntityType.fromName(m);
                return t;
        }
    }

    // ------------------------------------------------------------------ vines (the mod's generateVines)

    /**
     * Vines on building walls facing a lower neighbour. The mod did this in populate with the east/south neighbour
     * loaded; here each pair of adjacent chunks is done once, when the second of the two has been built.
     */
    private int vines(int cx, int cz, java.util.function.LongPredicate built) {
        int n = 0;
        int[][] others = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int[] o : others) {
            int ox = cx + o[0], oz = cz + o[1];
            if (!world.isChunkLoaded(ox, oz) || !built.test(CityWorld.key(ox, oz))) continue;
            int ax = Math.min(cx, ox), az = Math.min(cz, oz);   // the "info" chunk of the pair
            boolean xPair = o[0] != 0;
            n += vinePair(ax, az, xPair);
        }
        return n;
    }

    private int vinePair(int ax, int az, boolean xPair) {
        BuildingInfo info = w.info(ax, az);
        BuildingInfo adjacent = xPair ? info.getXmax() : info.getZmax();
        Random random = new Random(w.seed ^ Regions.mix(ax * 7919L + az * 104729L + (xPair ? 1 : 2)));
        int cx = ax * 16, cz = az * 16;
        int n = 0;
        if (info.hasBuilding) {
            int bottom = Math.max(adjacent.getCityGroundLevel() + 3, adjacent.hasBuilding ? adjacent.getMaxHeight() : (adjacent.getCityGroundLevel() + 3));
            for (int a = 0; a < 15; a++)
                for (int y = bottom; y < info.getMaxHeight(); y++)
                    if (random.nextFloat() < Profile.VINE_CHANCE) {
                        n += xPair ? strip(random, bottom, 2, cx + 16, y, cz + a, cx + 15)
                                   : strip(random, bottom, 4, cx + a, y, cz + 16, cz + 15);
                    }
        }
        if (adjacent.hasBuilding) {
            int bottom = Math.max(info.getCityGroundLevel() + 3, info.hasBuilding ? info.getMaxHeight() : (info.getCityGroundLevel() + 3));
            for (int a = 0; a < 15; a++)
                for (int y = bottom; y < adjacent.getMaxHeight(); y++)
                    if (random.nextFloat() < Profile.VINE_CHANCE) {
                        n += xPair ? strip(random, bottom, 8, cx + 15, y, cz + a, cx + 16)
                                   : strip(random, bottom, 1, cx + a, y, cz + 15, cz + 16);
                    }
        }
        return n;
    }

    /** createVineStrip; meta is the vine's side flag (south 1, west 2, north 4, east 8). holder is x or z. */
    private int strip(Random random, int bottom, int meta, int x, int y, int z, int holder) {
        Block pos = world.getBlockAt(x, y, z);
        Block hold = (meta == 2 || meta == 8) ? world.getBlockAt(holder, y, z) : world.getBlockAt(x, y, holder);
        if (hold.getType() == Material.AIR) return 0;
        if (pos.getType() != Material.AIR) return 0;
        if (!w.managed(x >> 4, z >> 4)) return 0;
        pos.setTypeIdAndData(106, (byte) meta, false);
        int n = 1;
        int yy = y - 1;
        while (yy >= bottom && random.nextFloat() < .8f) {
            Block b = world.getBlockAt(x, yy, z);
            if (b.getType() != Material.AIR) return n;
            b.setTypeIdAndData(106, (byte) meta, false);
            n++;
            yy--;
        }
        return n;
    }
}
