package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.PlayerChunkMap;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

/**
 * The 2x2-chunk work area of one population call (vanilla convention: decoration centred on [x*16+8, x*16+24), spilling
 * at most into the +x/+z neighbours, which Bukkit guarantees are loaded). Reads come from ChunkSnapshots, writes are
 * buffered and flushed straight into the NMS chunk sections, then block light is re-flooded once for the whole area and
 * any chunk a player already watches gets its changed blocks flagged for resend. Tile entities (chests, spawners, skulls)
 * are placed afterwards through Bukkit by the caller.
 */
final class Area {
    static final int H = 128;                 // the Nether's generated height
    static final int SIZE = 32 * 32 * H;
    static final int BEDROCK = 7 << 4;
    private static final IBlockData[] STATES = new IBlockData[4096 * 16];
    static final byte[] OPACITY = new byte[4096];
    static final byte[] EMIT = new byte[4096];
    private static volatile boolean tables;

    final World world;
    final int cx, cz, ox, oz;
    private final Chunk[] chunks = new Chunk[4];
    private final char[] data = new char[SIZE];
    private final BitSet dirty = new BitSet(SIZE);
    private boolean lightChanged;
    int writes, clipped;

    static void initTables() {
        if (tables) return;
        for (int id = 0; id < 256; id++) {
            net.minecraft.server.v1_12_R1.Block b = net.minecraft.server.v1_12_R1.Block.getById(id);
            if (b == null) continue;
            IBlockData s = b.getBlockData();
            try { OPACITY[id] = (byte) Math.min(15, s.c()); EMIT[id] = (byte) Math.min(15, s.d()); } catch (Throwable ignored) { OPACITY[id] = 15; }
        }
        tables = true;
    }

    static IBlockData state(int combined) {
        IBlockData s = STATES[combined];
        if (s == null) {
            net.minecraft.server.v1_12_R1.Block b = net.minecraft.server.v1_12_R1.Block.getById(combined >> 4);
            s = b.fromLegacyData(combined & 15);
            STATES[combined] = s;
        }
        return s;
    }

    Area(World world, int cx, int cz) {
        initTables();
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.ox = cx << 4;
        this.oz = cz << 4;
        for (int i = 0; i < 4; i++) {
            int chunkX = cx + (i & 1), chunkZ = cz + (i >> 1);
            if (!world.isChunkLoaded(chunkX, chunkZ)) { fillBedrock(i); continue; }
            Chunk c = world.getChunkAt(chunkX, chunkZ);
            chunks[i] = c;
            ChunkSnapshot s = c.getChunkSnapshot(false, false, false);
            int bx = (i & 1) << 4, bz = (i >> 1) << 4;
            for (int y = 0; y < H; y++) {
                if (s.isSectionEmpty(y >> 4)) { y |= 15; continue; }
                for (int z = 0; z < 16; z++) {
                    int row = (y << 10) | ((bz + z) << 5) | bx;
                    for (int xx = 0; xx < 16; xx++) data[row + xx] = (char) ((s.getBlockTypeId(xx, y, z) << 4) | s.getBlockData(xx, y, z));
                }
            }
        }
    }

    private void fillBedrock(int i) {
        int bx = (i & 1) << 4, bz = (i >> 1) << 4;
        for (int y = 0; y < H; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) data[(y << 10) | ((bz + z) << 5) | (bx + x)] = BEDROCK;
    }

    boolean inside(int x, int y, int z) {
        int lx = x - ox, lz = z - oz;
        return lx >= 0 && lx < 32 && lz >= 0 && lz < 32 && y >= 0 && y < H;
    }

    /** Combined (id << 4 | meta); outside the area reads as bedrock so nothing is placed against unknown terrain. */
    int get(int x, int y, int z) {
        int lx = x - ox, lz = z - oz;
        if (lx < 0 || lx >= 32 || lz < 0 || lz >= 32 || y < 0 || y >= H) return BEDROCK;
        return data[(y << 10) | (lz << 5) | lx];
    }
    int id(int x, int y, int z) { return get(x, y, z) >> 4; }
    boolean air(int x, int y, int z) { return get(x, y, z) == 0; }

    void set(int x, int y, int z, int id, int meta) {
        int lx = x - ox, lz = z - oz;
        if (lx < 0 || lx >= 32 || lz < 0 || lz >= 32 || y < 1 || y >= H) { clipped++; return; }
        int i = (y << 10) | (lz << 5) | lx;
        char v = (char) ((id << 4) | (meta & 15));
        char old = data[i];
        if (old == v) return;
        if (!lightChanged && (EMIT[old >> 4] != EMIT[id] || OPACITY[old >> 4] != OPACITY[id])) lightChanged = true;
        data[i] = v;
        dirty.set(i);
        writes++;
    }
    void set(int x, int y, int z, int combined) { if (combined >= 0) set(x, y, z, combined >> 4, combined & 15); }
    void setIfAir(int x, int y, int z, int id, int meta) { if (air(x, y, z)) set(x, y, z, id, meta); }
    void setIfAir(int x, int y, int z, int combined) { if (combined >= 0 && air(x, y, z)) set(x, y, z, combined >> 4, combined & 15); }

    /** Solid, full terrain-like block (what the mods call "full block" when they test a surface). */
    boolean solid(int x, int y, int z) { return Blocks.isFullSolid(id(x, y, z)); }

    /** Writes the buffered blocks into the chunk sections, relights the area and flags watched chunks for resend. */
    void flush() {
        if (writes == 0) return;
        WorldServer ws = ((CraftWorld) world).getHandle();
        PlayerChunkMap map = ws.getPlayerChunkMap();
        for (int ci = 0; ci < 4; ci++) {
            Chunk c = chunks[ci];
            if (c == null) continue;
            net.minecraft.server.v1_12_R1.Chunk nc = ((CraftChunk) c).getHandle();
            ChunkSection[] sections = nc.getSections();
            int bx = (ci & 1) << 4, bz = (ci >> 1) << 4;
            boolean watched = map.isChunkInUse(c.getX(), c.getZ());
            boolean changed = false;
            for (int y = 0; y < H; y++) {
                for (int z = 0; z < 16; z++) {
                    int row = (y << 10) | ((bz + z) << 5) | bx;
                    int next = dirty.nextSetBit(row);
                    if (next < 0 || next >= row + 16) continue;
                    for (int x = 0; x < 16; x++) {
                        int i = row + x;
                        if (!dirty.get(i)) continue;
                        int v = data[i];
                        ChunkSection sec = sections[y >> 4];
                        if (sec == null) {
                            if (v == 0) continue;
                            sec = new ChunkSection((y >> 4) << 4, false);
                            sections[y >> 4] = sec;
                        }
                        sec.setType(x, y & 15, z, state(v));
                        changed = true;
                        if (!nc.tileEntities.isEmpty()) {
                            BlockPosition p = new BlockPosition((c.getX() << 4) + x, y, (c.getZ() << 4) + z);
                            TileEntity te = nc.tileEntities.get(p);
                            if (te != null && !Blocks.isTileEntity(v >> 4)) { nc.tileEntities.remove(p); te.z(); }
                        }
                        if (watched) map.flagDirty(new BlockPosition((c.getX() << 4) + x, y, (c.getZ() << 4) + z));
                    }
                }
            }
            if (changed) nc.markDirty();
        }
        if (lightChanged) relight(ws);
    }

    /** Bucketed block-light flood over the 32x32x128 area, seeded by emitters and the light already outside it. */
    private void relight(WorldServer ws) {
        byte[] light = new byte[SIZE];
        IntList[] buckets = new IntList[16];
        for (int i = 1; i < 16; i++) buckets[i] = new IntList(1024);
        for (int i = 0; i < SIZE; i++) {
            int e = EMIT[data[i] >> 4];
            if (e > 0) { light[i] = (byte) e; buckets[e].add(i); }
        }
        // Light arriving from the ring of chunks around the area (the eight neighbouring chunk columns, looked up once).
        ring = new net.minecraft.server.v1_12_R1.Chunk[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            if ((i == 1 || i == 2) && (j == 1 || j == 2)) continue;
            ring[i][j] = ws.getChunkProviderServer().getChunkIfLoaded(cx - 1 + i, cz - 1 + j);
        }
        for (int y = 0; y < H; y++) {
            for (int k = 0; k < 32; k++) {
                seed(ws, light, buckets, ox - 1, y, oz + k, 0, y, k);
                seed(ws, light, buckets, ox + 32, y, oz + k, 31, y, k);
                seed(ws, light, buckets, ox + k, y, oz - 1, k, y, 0);
                seed(ws, light, buckets, ox + k, y, oz + 32, k, y, 31);
            }
        }
        for (int level = 15; level > 1; level--) {
            IntList q = buckets[level];
            for (int n = 0; n < q.size; n++) {
                int i = q.a[n];
                if (light[i] != level) continue;
                int x = i & 31, z = (i >> 5) & 31, y = i >> 10;
                if (x > 0) spread(light, buckets, i - 1, level);
                if (x < 31) spread(light, buckets, i + 1, level);
                if (z > 0) spread(light, buckets, i - 32, level);
                if (z < 31) spread(light, buckets, i + 32, level);
                if (y > 0) spread(light, buckets, i - 1024, level);
                if (y < H - 1) spread(light, buckets, i + 1024, level);
            }
        }
        for (int ci = 0; ci < 4; ci++) {
            Chunk c = chunks[ci];
            if (c == null) continue;
            ChunkSection[] sections = ((CraftChunk) c).getHandle().getSections();
            int bx = (ci & 1) << 4, bz = (ci >> 1) << 4;
            for (int y = 0; y < H; y++) {
                ChunkSection sec = sections[y >> 4];
                if (sec == null) { y |= 15; continue; }
                for (int z = 0; z < 16; z++) {
                    int row = (y << 10) | ((bz + z) << 5) | bx;
                    for (int x = 0; x < 16; x++) sec.b(x, y & 15, z, light[row + x]);
                }
            }
        }
    }

    private net.minecraft.server.v1_12_R1.Chunk[][] ring;

    private void seed(WorldServer ws, byte[] light, IntList[] buckets, int wx, int y, int wz, int lx, int ly, int lz) {
        int ri = (wx >> 4) - cx + 1, rj = (wz >> 4) - cz + 1;
        if (ri < 0 || ri > 3 || rj < 0 || rj > 3) return;
        net.minecraft.server.v1_12_R1.Chunk nc = ring[ri][rj];
        if (nc == null) return;
        ChunkSection sec = nc.getSections()[y >> 4];
        if (sec == null) return;
        int outside = sec.c(wx & 15, y & 15, wz & 15);
        if (outside <= 1) return;
        int i = (ly << 10) | (lz << 5) | lx;
        int v = outside - Math.max(1, OPACITY[data[i] >> 4]);
        if (v > light[i]) { light[i] = (byte) v; if (v > 1) buckets[v].add(i); }
    }

    private void spread(byte[] light, IntList[] buckets, int i, int level) {
        int v = level - Math.max(1, OPACITY[data[i] >> 4]);
        if (v > light[i]) { light[i] = (byte) v; if (v > 1) buckets[v].add(i); }
    }

    static final class IntList {
        int[] a; int size;
        IntList(int cap) { a = new int[cap]; }
        void add(int v) { if (size == a.length) a = java.util.Arrays.copyOf(a, size * 2); a[size++] = v; }
    }

    /** Positions of blocks written this call with the given id (for post-processing such as lava ticks). */
    List<int[]> written(int id) {
        List<int[]> out = new ArrayList<>();
        for (int i = dirty.nextSetBit(0); i >= 0; i = dirty.nextSetBit(i + 1)) {
            if ((data[i] >> 4) == id) out.add(new int[]{ox + (i & 31), i >> 10, oz + ((i >> 5) & 31)});
        }
        return out;
    }
}
