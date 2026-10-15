package chat.jaspr.lairs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.ITileEntity;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.RegionFileCache;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/**
 * Finds and builds the lairs. A lair is only ever built into land that already exists and that nobody has touched: every
 * chunk under it (and its four-block skirt) generated and populated, no chunk lived in (inhabited time at most the
 * limit), none guarded by another pack's build, no plan of JasprMuseMaps or JasprImportedWorldgen reaching it, no Lost
 * City reserved there, no player within the margin, and - read block by block - nothing made, placed or crafted in it
 * (M.built), no container, spawner or sign, no armour stand, frame, cart or villager. Then it is drawn into those chunks
 * at once (a few chunks per step), the chunks relit and guarded (HorrorBiomes' repair and retrofit passes leave guarded
 * chunks alone). Work is spread: one candidate check or two chunks per step, a step every five ticks.
 */
final class Director {
    static final int SKIRT = 4;
    private static final int GUARD_MAGIC = 0x494D5031;
    private static final long RETRY_MS = 120_000L, CACHE_MS = 300_000L;

    private final LairsPlugin plugin;
    final World world;
    final long seed;
    final Ledger ledger;
    private final File guardRoot, dataDir;
    private Field inhabitedField;
    final int spawnExclusion, playerMargin;
    final long inhabitedLimit;

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Map<String, Long> nextCheck = new HashMap<>();
    private final Map<String, Integer> cursor = new HashMap<>();
    private final Map<String, String> waitedWhy = new HashMap<>();
    private final Map<Long, long[]> chunkFacts = new HashMap<>();   // chunk -> {checkedAt, populated 0/1, inhabited}
    /** Saved-chunk reads in this step (each decompresses a chunk from disk): at most READS a step. */
    private int reads;
    private static final int READS = 6;
    private Scan scan;
    private Build build;
    private final ArrayDeque<Build> resumes = new ArrayDeque<>();
    private int steps;
    // metrics
    int candidatesChecked, scansStarted, scansRefused, lairsBuilt, chunksWritten, failures;
    final Map<String, Integer> refusalCounts = new HashMap<>();

    Director(LairsPlugin plugin, World world, Ledger ledger, File guardRoot) {
        this.plugin = plugin; this.world = world; this.seed = world.getSeed(); this.ledger = ledger; this.guardRoot = guardRoot;
        this.dataDir = plugin.getDataFolder();
        this.spawnExclusion = plugin.getConfig().getInt("spawn-exclusion", 400);
        this.playerMargin = plugin.getConfig().getInt("player-margin", 160);
        this.inhabitedLimit = plugin.getConfig().getLong("max-inhabited-ticks", 6000);
        try {
            inhabitedField = net.minecraft.server.v1_12_R1.Chunk.class.getDeclaredField("w");
            inhabitedField.setAccessible(true);
            if (inhabitedField.getType() != long.class) inhabitedField = null;
        } catch (ReflectiveOperationException e) { inhabitedField = null; }
        if (inhabitedField == null) plugin.getLogger().severe("LAIRS_INHABITED_UNREADABLE -- old land is treated as lived in (no lairs there)");
        for (Ledger.Lair l : ledger.all()) if (!l.built()) resumeLater(l);
    }

    // ---------------------------------------------------------------- the loop (every 5 ticks)

    void step() {
        steps++;
        try {
            if (build != null) { buildStep(); return; }
            if (!resumes.isEmpty()) { Build b = resumes.peek(); if (!playerNear(b.lair.x, b.lair.z, b.lair.x + b.lair.w, b.lair.z + b.lair.d, playerMargin)) { build = resumes.poll(); } return; }
            if (scan != null) { scanStep(); return; }
            if (steps % 20 == 1) refill();
            nextCandidate();
        } catch (RuntimeException e) {
            failures++;
            plugin.getLogger().warning("LAIRS_STEP_FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (build != null && failures > 20) { plugin.getLogger().severe("LAIRS_BUILD_ABANDONED key=" + build.lair.key); build = null; }
            scan = null;
        }
    }

    private void refill() {
        long now = System.currentTimeMillis();
        List<long[]> found = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            Location at = p.getLocation();
            int pi = Math.floorDiv(at.getBlockX(), Planner.CELL), pj = Math.floorDiv(at.getBlockZ(), Planner.CELL);
            for (int di = -2; di <= 2; di++) for (int dj = -2; dj <= 2; dj++) {
                int i = pi + di, j = pj + dj;
                String key = Planner.key(i, j);
                if (ledger.decided(key) || queued.contains(key) || nextCheck.getOrDefault(key, 0L) > now) continue;
                double cx = (i + 0.5) * Planner.CELL, cz = (j + 0.5) * Planner.CELL;
                found.add(new long[]{(long) Math.hypot(cx - at.getX(), cz - at.getZ()), i, j});
            }
        }
        found.sort((a, b) -> Long.compare(a[0], b[0]));
        for (long[] f : found) { String key = Planner.key((int) f[1], (int) f[2]); if (queued.add(key)) queue.add(key); }
    }

    private void nextCandidate() {
        String key = queue.peek();
        if (key == null) return;
        if (ledger.decided(key)) { queue.poll(); queued.remove(key); return; }
        String[] f = key.split("_");
        int i = Integer.parseInt(f[0]), j = Integer.parseInt(f[1]);
        Design d = Planner.design(seed, i, j);
        List<Planner.Candidate> cands = Planner.candidates(seed, i, j, d);
        Map<Integer, String> refusals = ledger.refusals(key);
        int idx = cursor.getOrDefault(key, 0);
        while (idx < cands.size() && refusals.containsKey(idx)) idx++;
        if (idx >= cands.size()) {
            queue.poll(); queued.remove(key); cursor.remove(key);
            String waited = waitedWhy.remove(key);
            if (refusals.size() >= cands.size()) {
                ledger.markEmpty(key);
                plugin.getLogger().info("LAIR_CELL_EMPTY cell=" + key + " design=" + d.id + " refusals=" + summary(refusals));
            } else {
                nextCheck.put(key, System.currentTimeMillis() + RETRY_MS);
                if (waited != null) plugin.getLogger().fine("LAIR_CELL_WAITING cell=" + key + " design=" + d.id + " why=" + waited);
            }
            return;
        }
        Planner.Candidate c = cands.get(idx);
        reads = 0;
        String why = quick(c);
        if ("budget".equals(why)) { cursor.put(key, idx); return; }   // saved chunk reads spread over steps: the same candidate next step
        cursor.put(key, idx + 1);
        candidatesChecked++;
        if (why == null) { startScan(key, i, j, d, c); return; }
        if (why.startsWith("wait:")) { waitedWhy.put(key, why.substring(5)); count(why); return; }
        ledger.refuse(key, idx, why);
        count(why);
    }

    private void count(String why) {
        String k = why.contains(":") && !why.startsWith("wait:") ? why.substring(0, why.indexOf(':')) : why;
        refusalCounts.merge(k, 1, Integer::sum);
    }
    private static String summary(Map<Integer, String> m) {
        Map<String, Integer> n = new HashMap<>();
        for (String s : m.values()) n.merge(s.contains(":") ? s.substring(0, s.indexOf(':')) : s, 1, Integer::sum);
        return n.toString().replace(" ", "");
    }

    // ---------------------------------------------------------------- 1. the quick checks (no chunk loads)

    /** null = go on to the scan; "wait:why" = try again later; else the reason it is refused for good. */
    String quick(Planner.Candidate c) {
        int x0 = c.x - SKIRT, z0 = c.z - SKIRT, x1 = c.x + c.w + SKIRT - 1, z1 = c.z + c.d + SKIRT - 1;
        Location spawn = world.getSpawnLocation();
        if (Math.hypot(c.cx() - spawn.getX(), c.cz() - spawn.getZ()) < spawnExclusion) return "spawn";
        if (cityReserved(x0, z0, x1 - x0 + 1, z1 - z0 + 1)) return "city";
        int c0 = x0 >> 4, c1 = x1 >> 4, d0 = z0 >> 4, d1 = z1 >> 4;
        for (int cx = c0; cx <= c1; cx++) for (int cz = d0; cz <= d1; cz++) if (guarded(cx, cz)) return "guarded";
        for (int cx = c0; cx <= c1; cx++) for (int cz = d0; cz <= d1; cz++) if (!world.isChunkGenerated(cx, cz)) return "wait:ungenerated";
        if (playerNear(x0, z0, x1, z1, playerMargin)) return "wait:player";
        for (int cx = c0; cx <= c1; cx++) for (int cz = d0; cz <= d1; cz++) {
            long[] facts = facts(cx, cz);
            if (facts == null) return "budget";
            if (facts[1] == 0) return "wait:unpopulated";
            if (facts[2] > inhabitedLimit) return "inhabited";
        }
        String pack = PackPlans.conflict(world, c0 - 1, d0 - 1, c1 + 1, d1 + 1);
        if (pack != null) return pack.endsWith("unreadable") ? "wait:" + pack : "pack:" + pack;
        return null;
    }

    boolean cityReserved(int x, int z, int w, int d) {
        try { return chat.jaspr.biomes.Cities.reserved(seed, x, z, w, d); }
        catch (LinkageError e) { return false; }
    }

    /** {checkedAt, populated, inhabited} of a chunk, from the loaded chunk or its saved NBT (cached five minutes). */
    private long[] facts(int cx, int cz) {
        long k = ((long) cx << 32) ^ (cz & 0xffffffffL), now = System.currentTimeMillis();
        long[] f = chunkFacts.get(k);
        if (f != null && now - f[0] < CACHE_MS) return f;
        if (!world.isChunkLoaded(cx, cz) && ++reads > READS) return null;
        long populated = 0, inhabited = Long.MAX_VALUE;
        try {
            if (world.isChunkLoaded(cx, cz)) {
                net.minecraft.server.v1_12_R1.Chunk n = ((CraftChunk) world.getChunkAt(cx, cz)).getHandle();
                populated = n.isDone() ? 1 : 0;
                inhabited = inhabitedField == null ? Long.MAX_VALUE : inhabitedField.getLong(n);
            } else {
                NBTTagCompound root = RegionFileCache.d(world.getWorldFolder(), cx, cz);
                if (root != null && root.hasKeyOfType("Level", 10)) {
                    NBTTagCompound level = root.getCompound("Level");
                    populated = level.getBoolean("TerrainPopulated") ? 1 : 0;
                    inhabited = inhabitedField == null ? Long.MAX_VALUE : level.getLong("InhabitedTime");
                }
            }
        } catch (Exception e) {
            populated = 0; inhabited = Long.MAX_VALUE;
        }
        if (chunkFacts.size() > 20000) chunkFacts.clear();
        f = new long[]{now, populated, inhabited};
        chunkFacts.put(k, f);
        return f;
    }

    boolean playerNear(int x0, int z0, int x1, int z1, int margin) {
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR && !p.isOp()) continue;
            Location l = p.getLocation();
            if (l.getX() > x0 - margin && l.getX() < x1 + margin && l.getZ() > z0 - margin && l.getZ() < z1 + margin) return true;
        }
        return false;
    }

    private File guardFile(int cx, int cz) {
        return new File(new File(guardRoot, Math.floorDiv(cx, 32) + "_" + Math.floorDiv(cz, 32)), cx + "_" + cz + ".guard");
    }
    boolean guarded(int cx, int cz) { return guardFile(cx, cz).isFile(); }
    private void guard(int cx, int cz) throws IOException {
        File f = guardFile(cx, cz);
        if (f.isFile()) return;
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(dir, "lairs-" + cx + "_" + cz + ".pending");
        try (FileOutputStream raw = new FileOutputStream(tmp); DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(GUARD_MAGIC); out.writeInt(cx); out.writeInt(cz); out.writeLong(seed);
            out.flush(); raw.getFD().sync();
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------------------------------------------------------------- 2. the scan (two chunks a step)

    private final class Scan {
        final String key; final int i, j; final Design d; final Planner.Candidate c; final Canvas canvas;
        final int rx0, rz0, rw, rd;
        final List<int[]> chunks = new ArrayList<>();
        int next;
        final Map<Long, ChunkSnapshot> snaps = new HashMap<>();
        final List<int[]> tileEntities = new ArrayList<>();
        String structure;
        Scan(String key, int i, int j, Design d, Planner.Candidate c) {
            this.key = key; this.i = i; this.j = j; this.d = d; this.c = c;
            this.canvas = d.render(Planner.siteSeed(seed, i, j));
            rx0 = c.x - SKIRT; rz0 = c.z - SKIRT; rw = c.w + 2 * SKIRT; rd = c.d + 2 * SKIRT;
            for (int cx = rx0 >> 4; cx <= (rx0 + rw - 1) >> 4; cx++) for (int cz = rz0 >> 4; cz <= (rz0 + rd - 1) >> 4; cz++) chunks.add(new int[]{cx, cz});
        }
        boolean inRegion(int x, int z) { return x >= rx0 && x < rx0 + rw && z >= rz0 && z < rz0 + rd; }
    }

    private void startScan(String key, int i, int j, Design d, Planner.Candidate c) {
        scan = new Scan(key, i, j, d, c);
        scansStarted++;
    }

    private void scanStep() {
        Scan s = scan;
        if (playerNear(s.rx0, s.rz0, s.rx0 + s.rw, s.rz0 + s.rd, playerMargin)) { waitedWhy.put(s.key, "player"); scan = null; return; }
        for (int k = 0; k < 2 && s.next < s.chunks.size(); k++) {
            int[] ch = s.chunks.get(s.next++);
            boolean was = world.isChunkLoaded(ch[0], ch[1]);
            Chunk chunk = world.getChunkAt(ch[0], ch[1]);
            try {
                s.snaps.put(((long) ch[0] << 32) ^ (ch[1] & 0xffffffffL), chunk.getChunkSnapshot(false, false, false));
                for (BlockState t : chunk.getTileEntities())
                    if (s.inRegion(t.getX(), t.getZ())) s.tileEntities.add(new int[]{t.getX(), t.getY(), t.getZ()});
                for (Entity e : chunk.getEntities()) {
                    Location l = e.getLocation();
                    if (!s.inRegion(l.getBlockX(), l.getBlockZ())) continue;
                    if (e instanceof Item || e instanceof ExperienceOrb || e instanceof Player) continue;
                    if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e instanceof Villager || e instanceof IronGolem) { s.structure = "entity:" + e.getType().name().toLowerCase(); break; }
                }
            } finally {
                if (!was) world.unloadChunkRequest(ch[0], ch[1]);
            }
        }
        if (s.next < s.chunks.size()) return;
        scan = null;
        String why = analyse(s);
        if (why != null) {
            scansRefused++;
            if (why.startsWith("wait:")) { waitedWhy.put(s.key, why.substring(5)); count(why); return; }
            ledger.refuse(s.key, s.c.index, why);
            count(why);
            return;
        }
    }

    /** Reads the land, admits or refuses the candidate; on admission records the lair and starts the build. */
    private String analyse(Scan s) {
        if (s.structure != null) return s.structure;
        int n = s.rw * s.rd;
        int[] ground = new int[n], top = new int[n];
        boolean[] wet = new boolean[n];
        for (int rz = 0; rz < s.rd; rz++) for (int rx = 0; rx < s.rw; rx++) {
            int x = s.rx0 + rx, z = s.rz0 + rz, i = rz * s.rw + rx;
            ChunkSnapshot snap = s.snaps.get(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL));
            if (snap == null) return "wait:snapshot";
            int lx = x & 15, lz = z & 15, g = 0, t = M.GRASS;
            for (int y = 250; y >= 1; y--) {
                int id = snap.getBlockTypeId(lx, y, lz);
                if (id == 0 || vegetation(id)) continue;
                if (id == 8 || id == 9 || id == 10 || id == 11) { wet[i] = true; g = y; t = M.s(id, 0); break; }
                g = y; t = M.s(id, snap.getBlockData(lx, y, lz)); break;
            }
            ground[i] = g; top[i] = t;
        }
        // the pad: how flat and dry the land is under it
        List<Integer> pads = new ArrayList<>();
        int wetPad = 0;
        for (int rz = 0; rz < s.rd; rz++) for (int rx = 0; rx < s.rw; rx++) {
            int fx = rx - SKIRT, fz = rz - SKIRT;
            if (fx < 0 || fz < 0 || fx >= s.c.w || fz >= s.c.d) continue;
            int[] uv = local(fx, fz, s.d, s.c.rot);
            if (s.canvas.pad(uv[0], uv[1]) < 0) continue;
            int i = rz * s.rw + rx;
            pads.add(ground[i]);
            if (wet[i]) wetPad++;
        }
        if (pads.isEmpty()) return "nopad";
        if (wetPad > pads.size() * 0.04) return "water";
        int[] sorted = pads.stream().mapToInt(Integer::intValue).sorted().toArray();
        int p05 = sorted[(int) (sorted.length * 0.05)], p50 = sorted[sorted.length / 2], p95 = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))];
        if (p95 - p05 > s.d.flat) return "steep";
        if (sorted[sorted.length - 1] - sorted[0] > s.d.flat + 7) return "steep";
        int base = p50;
        if (base < 56 || base > 160 || base + s.canvas.top() > 250 || base + Canvas.YMIN < 5) return "height";
        // nothing made by anyone anywhere from the lair's lowest dig to the sky
        int from = Math.max(1, Math.min(base + s.canvas.bottom(), sorted[0]) - 3);
        for (int[] t : s.tileEntities) if (t[1] >= from) return "structure:tile";
        for (int rz = 0; rz < s.rd; rz++) for (int rx = 0; rx < s.rw; rx++) {
            int x = s.rx0 + rx, z = s.rz0 + rz;
            ChunkSnapshot snap = s.snaps.get(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL));
            for (int y = from; y <= 255; y++) {
                int id = snap.getBlockTypeId(x & 15, y, z & 15);
                if (id != 0 && M.built(id)) return "structure:" + id;
            }
        }
        // admitted
        Ledger.Lair l = record(s, base);
        try {
            writeTerrain(l.key, base, s.rx0, s.rz0, s.rw, s.rd, ground, top);
            for (int[] ch : s.chunks) guard(ch[0], ch[1]);
            ledger.save();
        } catch (IOException e) {
            failures++;
            plugin.getLogger().severe("LAIR_RECORD_FAILED cell=" + s.key + " " + e.getMessage());
            return "wait:io";
        }
        plugin.getLogger().info("LAIR_DECIDED cell=" + s.key + " design=" + s.d.id + " at=" + s.c.x + "," + base + "," + s.c.z + " rot=" + s.c.rot
            + " candidate=" + s.c.index + " spread=" + (p95 - p05) + " chunks=" + s.chunks.size());
        queue.remove(s.key); queued.remove(s.key); cursor.remove(s.key);
        build = new Build(l, s.canvas, base, s.rx0, s.rz0, s.rw, s.rd, ground, top, s.chunks);
        return null;
    }

    static boolean vegetation(int id) {
        switch (id) {
            case 17: case 162: case 18: case 161: case 31: case 32: case 37: case 38: case 39: case 40: case 175: case 106: case 83:
            case 81: case 111: case 78: case 86: case 103: case 99: case 100: case 127: case 6: case 104: case 105:
                return true;
            default: return false;
        }
    }

    /** The design frame (u, v) of a footprint offset (fx, fz) in the turned footprint. */
    static int[] local(int fx, int fz, Design d, int rot) {
        switch (rot & 3) {
            case 1: return new int[]{fz, d.d - 1 - fx};
            case 2: return new int[]{d.w - 1 - fx, d.d - 1 - fz};
            case 3: return new int[]{d.w - 1 - fz, fx};
            default: return new int[]{fx, fz};
        }
    }

    private Ledger.Lair record(Scan s, int base) {
        Ledger.Lair l = new Ledger.Lair(s.key, s.i, s.j, s.d, s.c.x, base, s.c.z, s.c.rot);
        l.state = "building";
        Canvas c = s.canvas;
        l.boss = new int[]{l.wx(c.boss[0], c.boss[2]), base + c.boss[1], l.wz(c.boss[0], c.boss[2])};
        l.wake = c.wake;
        int ax = l.wx(c.vault[0], c.vault[2]), az = l.wz(c.vault[0], c.vault[2]), bx = l.wx(c.vault[3], c.vault[5]), bz = l.wz(c.vault[3], c.vault[5]);
        l.vault = new int[]{Math.min(ax, bx), base + c.vault[1], Math.min(az, bz), Math.max(ax, bx), base + c.vault[4], Math.max(az, bz)};
        for (int[] p : c.seals) l.seals.add(new int[]{l.wx(p[0], p[2]), base + p[1], l.wz(p[0], p[2])});
        for (Canvas.Tile t : c.tiles) if (t.kind == Canvas.CHEST && t.vault) l.vaultChests.add(new int[]{l.wx(t.u, t.v), base + t.y, l.wz(t.u, t.v)});
        for (Canvas.Point g : c.garrisons) l.garrisons.add(new Ledger.Garrison(l.wx(g.u, g.v), base + g.y, l.wz(g.u, g.v), g.pack));
        ledger.put(l);
        return l;
    }

    // ---------------------------------------------------------------- 3. building (three chunks a step)

    final class Build {
        final Ledger.Lair lair; final Canvas canvas; final int base, rx0, rz0, rw, rd;
        final int[] ground, top, skirt;
        final ArrayDeque<int[]> todo = new ArrayDeque<>();
        final long began = System.nanoTime();
        int written;
        Build(Ledger.Lair lair, Canvas canvas, int base, int rx0, int rz0, int rw, int rd, int[] ground, int[] top, List<int[]> chunks) {
            this.lair = lair; this.canvas = canvas; this.base = base; this.rx0 = rx0; this.rz0 = rz0; this.rw = rw; this.rd = rd;
            this.ground = ground; this.top = top;
            this.skirt = skirtDistances(this);
            for (int[] ch : chunks) if (!lair.chunksDone.contains(ch[0] + "," + ch[1])) todo.add(ch);
        }
    }

    /** Distance (in blocks, 1..SKIRT) of each region column outside the pad to the nearest pad column; 0 = pad or too far. */
    private static int[] skirtDistances(Build b) {
        int n = b.rw * b.rd;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int rz = 0; rz < b.rd; rz++) for (int rx = 0; rx < b.rw; rx++) {
            int fx = rx - SKIRT, fz = rz - SKIRT;
            if (fx < 0 || fz < 0 || fx >= b.lair.w || fz >= b.lair.d) continue;
            int[] uv = local(fx, fz, b.lair.design, b.lair.rot);
            if (b.canvas.pad(uv[0], uv[1]) >= 0) { dist[rz * b.rw + rx] = 0; q.add(rz * b.rw + rx); }
        }
        while (!q.isEmpty()) {
            int i = q.poll(), rx = i % b.rw, rz = i / b.rw, dd = dist[i];
            if (dd >= SKIRT) continue;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : nb) {
                int x = rx + o[0], z = rz + o[1];
                if (x < 0 || z < 0 || x >= b.rw || z >= b.rd) continue;
                int k = z * b.rw + x;
                if (dist[k] > dd + 1) { dist[k] = dd + 1; q.add(k); }
            }
        }
        for (int i = 0; i < n; i++) if (dist[i] == Integer.MAX_VALUE || dist[i] > SKIRT) dist[i] = -1;
        return dist;
    }

    private void buildStep() {
        Build b = build;
        for (int k = 0; k < 2 && !b.todo.isEmpty(); k++) {
            int[] ch = b.todo.poll();
            long t0 = System.nanoTime();
            boolean was = world.isChunkLoaded(ch[0], ch[1]);
            Chunk chunk = world.getChunkAt(ch[0], ch[1]);
            try {
                writeChunk(b, chunk);
                ChunkLight.initialize(chunk);
                world.refreshChunk(ch[0], ch[1]);
                fillTiles(b, chunk);
                chunksWritten++;
                b.lair.chunksDone.add(ch[0] + "," + ch[1]);
                ledger.touch();
            } catch (RuntimeException e) {
                failures++;
                plugin.getLogger().warning("LAIR_CHUNK_FAILED key=" + b.lair.key + " chunk=" + ch[0] + "," + ch[1] + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (!was) world.unloadChunkRequest(ch[0], ch[1]);
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (ms > 60) plugin.getLogger().info("LAIR_SLOW_CHUNK key=" + b.lair.key + " ms=" + ms);
        }
        if (!b.todo.isEmpty()) return;
        build = null;
        b.lair.state = "built";
        b.lair.chunksDone.clear();
        lairsBuilt++;
        try { ledger.save(); } catch (IOException e) { plugin.getLogger().severe("LAIR_LEDGER_SAVE_FAILED " + e.getMessage()); }
        new File(dataDir, "building-" + b.lair.key + ".dat").delete();
        plugin.getLogger().info("LAIR_BUILT key=" + b.lair.key + " design=" + b.lair.design.id + " at=" + b.lair.x + "," + b.lair.y + "," + b.lair.z
            + " blocks=" + b.written + " ms=" + (System.nanoTime() - b.began) / 1_000_000L + " boss=" + Ledger.str(b.lair.boss)
            + " vaultChest=" + (b.lair.vaultChests.isEmpty() ? "-" : Ledger.str(b.lair.vaultChests.get(0))) + " vault=" + Ledger.str(b.lair.vault));
        plugin.bosses().index(b.lair);
    }

    /**
     * Writes straight into a chunk's sections, as world generation does: no block placement logic (a torch written before
     * the wall it hangs on, a rail beside another, sand over a gap are all left exactly as drawn), tile entities made for
     * the blocks that have them. The chunk's height map and light are rebuilt afterwards (initLighting, ChunkLight).
     */
    static final class Writer {
        final net.minecraft.server.v1_12_R1.Chunk n;
        final net.minecraft.server.v1_12_R1.World w;
        final ChunkSection[] sections;
        int written;
        Writer(Chunk c) { n = ((CraftChunk) c).getHandle(); w = n.world; sections = n.getSections(); }
        int type(int x, int y, int z) {
            if (y < 0 || y > 255) return 0;
            ChunkSection s = sections[y >> 4];
            return s == null ? 0 : net.minecraft.server.v1_12_R1.Block.getId(s.getType(x & 15, y & 15, z & 15).getBlock());
        }
        void set(int x, int y, int z, int state) {
            if (y < 1 || y > 254) return;
            IBlockData data = net.minecraft.server.v1_12_R1.Block.getByCombinedId(M.id(state) | (M.data(state) << 12));
            ChunkSection s = sections[y >> 4];
            if (s == null) {
                if (M.id(state) == 0) return;
                s = new ChunkSection(y >> 4 << 4, true);
                sections[y >> 4] = s;
            }
            IBlockData old = s.getType(x & 15, y & 15, z & 15);
            if (old == data) return;
            BlockPosition pos = new BlockPosition(x, y, z);
            if (old.getBlock() instanceof ITileEntity) n.removeTileEntity(pos);
            s.setType(x & 15, y & 15, z & 15, data);
            written++;
            net.minecraft.server.v1_12_R1.Block block = data.getBlock();
            if (block instanceof ITileEntity) {
                TileEntity te = ((ITileEntity) block).a(w, block.toLegacyData(data));
                if (te != null) w.setTileEntity(pos, te);
            }
        }
        void finish() { n.initLighting(); n.markDirty(); }
    }

    private void writeChunk(Build b, Chunk chunk) {
        Writer out = new Writer(chunk);
        int cx0 = chunk.getX() << 4, cz0 = chunk.getZ() << 4;
        Ledger.Lair l = b.lair;
        for (int z = Math.max(cz0, b.rz0); z <= Math.min(cz0 + 15, b.rz0 + b.rd - 1); z++)
            for (int x = Math.max(cx0, b.rx0); x <= Math.min(cx0 + 15, b.rx0 + b.rw - 1); x++) {
                int ri = (z - b.rz0) * b.rw + (x - b.rx0), g = b.ground[ri];
                int fx = x - l.x, fz = z - l.z;
                boolean foot = fx >= 0 && fz >= 0 && fx < l.w && fz < l.d;
                int[] uv = foot ? local(fx, fz, l.design, l.rot) : null;
                int pad = foot ? b.canvas.pad(uv[0], uv[1]) : -1;
                int topState = M.topsoil(M.id(b.top[ri])) ? b.top[ri] : M.id(b.top[ri]) == 8 || M.id(b.top[ri]) == 9 ? M.DIRT : M.GRASS;
                if (pad >= 0) fit(out, x, z, g, b.base, topState, pad);
                else if (b.skirt[ri] > 0 && g > 0) {
                    int t = (int) Math.round(b.base + (g - b.base) * (b.skirt[ri] / (SKIRT + 1.0)));
                    if (t != g) fit(out, x, z, g, t, topState, 2);
                }
                if (!foot) continue;
                for (int y = Canvas.YMIN; y <= Canvas.YMAX; y++) {
                    int s = b.canvas.get(uv[0], y, uv[1]);
                    if (s >= 0) out.set(x, b.base + y, z, M.rotate(s, l.rot));
                }
            }
        out.finish();
        b.written += out.written;
    }

    /** Levels a column to top height t: fill or cut, the top layer of the land's own soil, then clear above it. */
    private static void fit(Writer out, int x, int z, int g, int t, int topState, int clear) {
        if (g > 0 && g < t) for (int y = g + 1; y < t; y++) out.set(x, y, z, t - y <= 3 ? M.DIRT : M.STONE);
        if (g > t) for (int y = t + 1; y <= g; y++) out.set(x, y, z, M.AIR);
        out.set(x, t, z, topState);
        for (int y = t + 1; y <= Math.min(254, t + clear); y++) if (out.type(x, y, z) != 0) out.set(x, y, z, M.AIR);
    }

    private void fillTiles(Build b, Chunk chunk) {
        Ledger.Lair l = b.lair;
        Random r = new Random(Planner.siteSeed(seed, l.ci, l.cj) ^ 0x4C4F4F54L);
        for (Canvas.Tile t : b.canvas.tiles) {
            int x = l.wx(t.u, t.v), z = l.wz(t.u, t.v), y = b.base + t.y;
            if ((x >> 4) != chunk.getX() || (z >> 4) != chunk.getZ()) continue;
            Block block = world.getBlockAt(x, y, z);
            try {
                BlockState st = block.getState();
                if (t.kind == Canvas.CHEST && st instanceof Chest) {
                    Loot.fill(((Chest) st).getBlockInventory(), t.what, l.design, r);
                } else if (t.kind == Canvas.SPAWNER && st instanceof CreatureSpawner) {
                    CreatureSpawner sp = (CreatureSpawner) st;
                    EntityType type = EntityType.fromName(t.what);
                    if (type != null) { sp.setSpawnedType(type); sp.update(true, false); }
                } else if (t.kind == Canvas.SIGN && st instanceof Sign) {
                    Sign sign = (Sign) st;
                    String[] lines = t.what.split("\\|");
                    for (int i = 0; i < 4; i++) sign.setLine(i, i < lines.length ? lines[i] : "");
                    sign.update(true, false);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("LAIR_TILE_FAILED key=" + l.key + " kind=" + t.kind + " " + e.getClass().getSimpleName());
            }
        }
    }

    // ---------------------------------------------------------------- resume after a restart mid-build

    private void writeTerrain(String key, int base, int rx0, int rz0, int rw, int rd, int[] ground, int[] top) throws IOException {
        File f = new File(dataDir, "building-" + key + ".dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeInt(0x4C414952); out.writeInt(base); out.writeInt(rx0); out.writeInt(rz0); out.writeInt(rw); out.writeInt(rd);
            for (int g : ground) out.writeInt(g);
            for (int t : top) out.writeInt(t);
        }
    }

    private void resumeLater(Ledger.Lair l) {
        File f = new File(dataDir, "building-" + l.key + ".dat");
        Canvas canvas = l.design.render(Planner.siteSeed(seed, l.ci, l.cj));
        int rx0 = l.x - SKIRT, rz0 = l.z - SKIRT, rw = l.w + 2 * SKIRT, rd = l.d + 2 * SKIRT;
        int[] ground = new int[rw * rd], top = new int[rw * rd];
        int base = l.y;
        boolean terrain = false;
        if (f.isFile()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                if (in.readInt() == 0x4C414952 && in.readInt() == base && in.readInt() == rx0 && in.readInt() == rz0 && in.readInt() == rw && in.readInt() == rd) {
                    for (int i = 0; i < ground.length; i++) ground[i] = in.readInt();
                    for (int i = 0; i < top.length; i++) top[i] = in.readInt();
                    terrain = true;
                }
            } catch (IOException e) { terrain = false; }
        }
        if (!terrain) { Arrays.fill(ground, base); Arrays.fill(top, M.GRASS); }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = rx0 >> 4; cx <= (rx0 + rw - 1) >> 4; cx++) for (int cz = rz0 >> 4; cz <= (rz0 + rd - 1) >> 4; cz++) chunks.add(new int[]{cx, cz});
        resumes.add(new Build(l, canvas, base, rx0, rz0, rw, rd, ground, top, chunks));
        plugin.getLogger().info("LAIR_RESUME key=" + l.key + " terrain=" + terrain + " chunksLeft=" + resumes.peekLast().todo.size());
    }

    // ---------------------------------------------------------------- operators: build one here (testing)

    /** Builds a design at a spot now, without the land checks (an operator's /lairs build); still refuses unloaded land. */
    String forceBuild(Design d, int x, int z, int rot, int base) {
        if (build != null || scan != null) return "busy";
        int i = Math.floorDiv(x, Planner.CELL), j = Math.floorDiv(z, Planner.CELL);
        String key = Planner.key(i, j);
        if (ledger.get(key) != null) return "cell " + key + " already has a lair";
        Planner.Candidate c = new Planner.Candidate(-1, x, z, rot, (rot & 1) == 0 ? d.w : d.d, (rot & 1) == 0 ? d.d : d.w);
        Scan s = new Scan(key, i, j, d, c);
        int n = s.rw * s.rd;
        int[] ground = new int[n], top = new int[n];
        for (int[] ch : s.chunks) if (!world.isChunkLoaded(ch[0], ch[1])) world.loadChunk(ch[0], ch[1], true);
        for (int rz = 0; rz < s.rd; rz++) for (int rx = 0; rx < s.rw; rx++) {
            int wx = s.rx0 + rx, wz = s.rz0 + rz;
            Block hb = world.getHighestBlockAt(wx, wz);
            int y = hb.getY() - 1;
            while (y > 1 && (world.getBlockAt(wx, y, wz).getTypeId() == 0 || vegetation(world.getBlockAt(wx, y, wz).getTypeId()))) y--;
            ground[rz * s.rw + rx] = y;
            Block g = world.getBlockAt(wx, y, wz);
            top[rz * s.rw + rx] = M.s(g.getTypeId(), g.getData());
        }
        if (base == Integer.MIN_VALUE) { int[] sorted = ground.clone(); Arrays.sort(sorted); base = sorted[sorted.length / 2]; }
        Ledger.Lair l = record(s, base);
        try {
            writeTerrain(l.key, base, s.rx0, s.rz0, s.rw, s.rd, ground, top);
            for (int[] ch : s.chunks) guard(ch[0], ch[1]);
            ledger.save();
        } catch (IOException e) { return "io: " + e.getMessage(); }
        plugin.getLogger().info("LAIR_DECIDED cell=" + key + " design=" + d.id + " at=" + x + "," + base + "," + z + " rot=" + rot + " forced=1");
        build = new Build(l, s.canvas, base, s.rx0, s.rz0, s.rw, s.rd, ground, top, s.chunks);
        return null;
    }

    /** Compares a built lair with its drawing, block by block (an operator's /lairs check; the tests run it). */
    String check(Ledger.Lair l) {
        Canvas c = l.design.render(Planner.siteSeed(seed, l.ci, l.cj));
        int drawn = 0, bad = 0, chests = 0, filled = 0, spawners = 0, typed = 0, signs = 0, written = 0, sealsShut = 0;
        String first = "";
        Set<String> seals = new HashSet<>();
        for (int[] p : c.seals) seals.add(p[0] + "," + p[1] + "," + p[2]);
        for (int y = Canvas.YMIN; y <= Canvas.YMAX; y++) for (int v = 0; v < c.d; v++) for (int u = 0; u < c.w; u++) {
            int s = c.get(u, y, v);
            if (s < 0) continue;
            int wy = l.y + y;
            if (wy < 1 || wy > 254) continue;
            drawn++;
            Block b = world.getBlockAt(l.wx(u, v), wy, l.wz(u, v));
            int want = M.rotate(s, l.rot);
            if (seals.contains(u + "," + y + "," + v)) { if (b.getTypeId() != 0) sealsShut++; if (l.vaultOpen) want = M.AIR; }
            // soil lives: grass, dirt, podzol and mycelium turn into one another under cover or in the sun
            if (soil(b.getTypeId()) && soil(M.id(want))) continue;
            // plants are trampled, picked and shaken off by the mutants' shock waves
            if (b.getTypeId() == 0 && Canvas.plant(M.id(want))) continue;
            if (b.getTypeId() != M.id(want) || (M.id(want) != 0 && b.getData() != M.data(want))) {
                bad++;
                if (bad <= 6) first += (bad == 1 ? " diff=" : ";") + b.getX() + "," + b.getY() + "," + b.getZ() + ":" + b.getTypeId() + "/" + b.getData() + "!=" + M.id(want) + "/" + M.data(want);
            }
        }
        for (Canvas.Tile t : c.tiles) {
            BlockState st = world.getBlockAt(l.wx(t.u, t.v), l.y + t.y, l.wz(t.u, t.v)).getState();
            if (t.kind == Canvas.CHEST) { chests++; if (st instanceof Chest && !java.util.Arrays.stream(((Chest) st).getBlockInventory().getContents()).allMatch(i -> i == null)) filled++; }
            if (t.kind == Canvas.SPAWNER) { spawners++; if (st instanceof CreatureSpawner && ((CreatureSpawner) st).getSpawnedType() == EntityType.fromName(t.what)) typed++; }
            if (t.kind == Canvas.SIGN) { signs++; if (st instanceof Sign && !((Sign) st).getLine(0).isEmpty()) written++; }
        }
        return "LAIR_CHECK key=" + l.key + " design=" + l.design.id + " drawn=" + drawn + " mismatches=" + bad + " chests=" + filled + "/" + chests
            + " spawners=" + typed + "/" + spawners + " signs=" + written + "/" + signs + " sealsShut=" + sealsShut + "/" + c.seals.size() + " vaultOpen=" + l.vaultOpen + first;
    }

    private static boolean soil(int id) { return id == 2 || id == 3 || id == 110 || id == 60 || id == 208; }

    String status() {
        return "queue=" + queue.size() + " scanning=" + (scan == null ? "-" : scan.key + "#" + scan.c.index) + " building=" + (build == null ? "-" : build.lair.key + "(" + build.todo.size() + " left)")
            + " checked=" + candidatesChecked + " scans=" + scansStarted + " scanRefused=" + scansRefused + " built=" + lairsBuilt + " chunks=" + chunksWritten
            + " failures=" + failures + " reasons=" + refusalCounts.toString().replace(" ", "");
    }
}
