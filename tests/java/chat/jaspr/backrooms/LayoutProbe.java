package chat.jaspr.backrooms;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

/**
 * Generates every level of the Backrooms with the real generator into memory (no server) and checks it the way a player
 * would: a 3D walk (steps up one block, drops, swims) from each level's arrival point must reach its arena and its exit,
 * and in level 1 the Threshold's gate and all six level doors. Also counts chests and water that could flow, and works
 * out the block light the way the game spreads it (sources, minus one a block, water dimming more): the Threshold and
 * every landing must be bright, level 1 bright near its start and darker deeper in, the Poolrooms bright. With a
 * directory argument it writes a top-down picture of each level (walls dark, floors by material, water blue, the part
 * a player can reach tinted, chests gold). Prints BACKROOMS_LAYOUT_OK when every check passes.
 * Run: tests/backrooms.test.cjs (or java ... LayoutProbe <seed> [pngDir]).
 */
public final class LayoutProbe {
    static final int Y0 = 26, Y1 = 66, H = Y1 - Y0;

    /** Chunk data backed by arrays, only for the band of heights the checks need. */
    static final class Data implements ChunkData {
        final short[] ids = new short[16 * 16 * 256];
        final byte[] metas = new byte[16 * 16 * 256];
        static int i(int x, int y, int z) { return (y * 16 + z) * 16 + x; }
        @Override public int getMaxHeight() { return 256; }
        @Override public void setBlock(int x, int y, int z, Material m) { setBlock(x, y, z, m.getId(), (byte) 0); }
        @Override public void setBlock(int x, int y, int z, MaterialData m) { setBlock(x, y, z, m.getItemTypeId(), m.getData()); }
        @Override public void setRegion(int a, int b, int c, int d, int e, int f, Material m) { setRegion(a, b, c, d, e, f, m.getId(), 0); }
        @Override public void setRegion(int a, int b, int c, int d, int e, int f, MaterialData m) { setRegion(a, b, c, d, e, f, m.getItemTypeId(), m.getData()); }
        @Override public Material getType(int x, int y, int z) { return Material.getMaterial(getTypeId(x, y, z)); }
        @Override public MaterialData getTypeAndData(int x, int y, int z) { return new MaterialData(getTypeId(x, y, z), getData(x, y, z)); }
        @Override public void setRegion(int a, int b, int c, int d, int e, int f, int id) { setRegion(a, b, c, d, e, f, id, 0); }
        @Override public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, int id, int data) {
            for (int y = Math.max(0, y0); y < Math.min(256, y1); y++) for (int z = Math.max(0, z0); z < Math.min(16, z1); z++) for (int x = Math.max(0, x0); x < Math.min(16, x1); x++) setBlock(x, y, z, id, (byte) data);
        }
        @Override public void setBlock(int x, int y, int z, int id) { setBlock(x, y, z, id, (byte) 0); }
        @Override public void setBlock(int x, int y, int z, int id, byte data) {
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return;
            ids[i(x, y, z)] = (short) id; metas[i(x, y, z)] = data;
        }
        @Override public int getTypeId(int x, int y, int z) { return ids[i(x, y, z)]; }
        @Override public byte getData(int x, int y, int z) { return metas[i(x, y, z)]; }
    }

    // ---- one level in memory --------------------------------------------------------------------------------------------
    final Level lv;
    final int x0, z0, w, d;
    final short[] vox;
    final List<int[]> chests = new ArrayList<>();
    int leaks;

    LayoutProbe(BackroomsGenerator gen, Level lv) {
        this.lv = lv;
        x0 = lv.x0(); z0 = lv.zMin(); w = lv.length; d = lv.width;
        vox = new short[w * d * H];
        for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + w - 1, 16); cx++)
            for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + d - 1, 16); cz++) {
                Data data = new Data();
                List<Canvas.Tile> tiles = new ArrayList<>();
                gen.fill(data, null, cx, cz, tiles);
                for (Canvas.Tile t : tiles) if (t.kind == Canvas.CHEST_TILE && lv.contains(t.x, t.z)) chests.add(new int[] {t.x, t.y, t.z});
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                    int x = cx * 16 + dx, z = cz * 16 + dz;
                    if (x < x0 || x >= x0 + w || z < z0 || z >= z0 + d) continue;
                    for (int y = Y0; y < Y1; y++) vox[idx(x, y, z)] = data.ids[Data.i(dx, y, dz)];
                }
            }
    }

    int idx(int x, int y, int z) { return ((x - x0) * d + (z - z0)) * H + (y - Y0); }
    boolean in(int x, int y, int z) { return x >= x0 && x < x0 + w && z >= z0 && z < z0 + d && y >= Y0 && y < Y1; }
    int id(int x, int y, int z) { return in(x, y, z) ? vox[idx(x, y, z)] : Canvas.STONE; }

    static boolean thin(int id) {
        return id == 0 || id == 8 || id == 9 || id == Canvas.CARPET || id == Canvas.WALL_SIGN || id == Canvas.PORTAL || id == Canvas.TORCH
            || id == Canvas.STONE_PLATE || id == 72 || id == Canvas.REDSTONE_TORCH || id == 31;
    }
    static boolean water(int id) { return id == 8 || id == 9; }
    static boolean lava(int id) { return id == 10 || id == 11; }
    boolean pass(int x, int y, int z) { int id = id(x, y, z); return thin(id); }
    /** A player can be at (x, y, z): room for body and head, and ground under the feet (or water to swim in). */
    boolean stand(int x, int y, int z) {
        if (!pass(x, y, z) || !pass(x, y + 1, z)) return false;
        int below = id(x, y - 1, z), here = id(x, y, z);
        return !thin(below) && !lava(below) || water(here) || water(below);
    }

    /** Breadth-first walk from a start; returns the visited set (index by idx). */
    boolean[] walk(int sx, int sy, int sz) {
        boolean[] seen = new boolean[vox.length];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (!stand(sx, sy, sz)) throw new IllegalStateException(lv + ": start " + sx + "," + sy + "," + sz + " is not standable (" + id(sx, sy, sz) + "/" + id(sx, sy - 1, sz) + ")");
        seen[idx(sx, sy, sz)] = true;
        q.add(new int[] {sx, sy, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], z = p[2];
            boolean swim = water(id(x, y, z)) || water(id(x, y + 1, z));
            if (swim) {
                for (int dy = -1; dy <= 1; dy += 2) if (stand(x, y + dy, z) || water(id(x, y + dy, z)) && pass(x, y + dy + 1, z)) visit(seen, q, x, y + dy, z);
            }
            for (int[] dd : dirs) {
                int nx = x + dd[0], nz = z + dd[1];
                if (stand(nx, y, nz)) visit(seen, q, nx, y, nz);
                else if (stand(nx, y + 1, nz) && pass(x, y + 2, z)) visit(seen, q, nx, y + 1, nz);
                else if (pass(nx, y, nz) && pass(nx, y + 1, nz)) {   // walk off an edge and fall
                    for (int fy = y - 1; fy >= Y0 + 1; fy--) { if (stand(nx, fy, nz)) { visit(seen, q, nx, fy, nz); break; } if (!pass(nx, fy, nz)) break; }
                }
            }
        }
        return seen;
    }

    private void visit(boolean[] seen, ArrayDeque<int[]> q, int x, int y, int z) {
        if (!in(x, y, z)) return;
        int i = idx(x, y, z);
        if (seen[i]) return;
        seen[i] = true;
        q.add(new int[] {x, y, z});
    }

    boolean reached(boolean[] seen, int x, int z, int yLo, int yHi) {
        for (int y = yLo; y <= yHi; y++) if (in(x, y, z) && seen[idx(x, y, z)]) return true;
        return false;
    }

    /** Water that could flow: a water block with air (or a thin non-water block) beside or below it. */
    int countLeaks() {
        int n = 0;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) for (int y = Y0 + 1; y < Y1 - 1; y++) {
            if (!water(id(x, y, z))) continue;
            for (int[] k : dirs) {
                int nid = id(x + k[0], y + k[1], z + k[2]);
                if (nid == 0 || thin(nid) && !water(nid) && nid != Canvas.WALL_SIGN) { n++; break; }
            }
        }
        return n;
    }

    // ---- block light ----------------------------------------------------------------------------------------------------
    static int emits(int id) {
        switch (id) {
            case Canvas.GLOWSTONE: case Canvas.SEA_LANTERN: case 10: case 11: case 91: case 124: case 51: case 138: return 15;
            case 198: case 50: return 14;
            case 62: return 13;
            case Canvas.PORTAL: return 11;
            case 74: return 9;
            case 76: case 130: return 7;
            case 213: return 3;
            default: return 0;
        }
    }

    /** How much light a block takes away on the way through (as the game has it): see-through blocks 1, water 3, solid ones all. */
    static int opacity(int id) {
        if (thin(id) && !water(id)) return 1;
        switch (id) {
            case 8: case 9: case 79: return 3;
            case 18: case 161: return 1;
            case 20: case 95: case 102: case 160: case 101: case 85: case 113: case 188: case 189: case 190: case 191: case 192: case 107:
            case 54: case 146: case 30: case 96: case 167: case 65: case 198: case 154: case 118: case 145: case 117: case 50: case 76: case 51:
                return 1;
            default: return 16;
        }
    }

    byte[] light;

    /** Block light everywhere in the level (sources spread in steps from 15 down; a block keeps its brightest). */
    void light() {
        light = new byte[vox.length];
        int[][] buckets = new int[16][];
        int[] sizes = new int[16];
        for (int v = 0; v < 16; v++) buckets[v] = new int[1024];
        for (int i = 0; i < vox.length; i++) {
            int e = emits(vox[i]);
            if (e > 0) { light[i] = (byte) e; buckets[e] = push(buckets[e], sizes[e]++, i); }
        }
        int dy = 1, dz = H, dx = d * H;
        for (int v = 15; v >= 2; v--) {
            for (int k = 0; k < sizes[v]; k++) {
                int i = buckets[v][k];
                if (light[i] != v) continue;
                int y = i % H, z = (i / H) % d, x = i / (H * d);
                for (int n = 0; n < 6; n++) {
                    int j;
                    if (n == 0) { if (y + 1 >= H) continue; j = i + dy; }
                    else if (n == 1) { if (y == 0) continue; j = i - dy; }
                    else if (n == 2) { if (z + 1 >= d) continue; j = i + dz; }
                    else if (n == 3) { if (z == 0) continue; j = i - dz; }
                    else if (n == 4) { if (x + 1 >= w) continue; j = i + dx; }
                    else { if (x == 0) continue; j = i - dx; }
                    int nv = v - opacity(vox[j]);
                    if (nv > light[j]) { light[j] = (byte) nv; buckets[nv] = push(buckets[nv], sizes[nv]++, j); }
                }
            }
            buckets[v] = null;
        }
    }

    private static int[] push(int[] a, int n, int i) {
        if (n == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
        a[n] = i;
        return a;
    }

    /** Mean light where a player can stand (at the feet, or the head when wading) in x in [xa, xb), z in [za, zb]. */
    double meanLight(int xa, int xb, int za, int zb) {
        long sum = 0, n = 0;
        for (int x = Math.max(xa, x0); x < Math.min(xb, x0 + w); x++) for (int z = Math.max(za, z0); z <= Math.min(zb, z0 + d - 1); z++)
            for (int y = Level.WALK - 8; y <= Level.WALK + 2; y++) if (stand(x, y, z)) { sum += Math.max(light[idx(x, y, z)], in(x, y + 1, z) ? light[idx(x, y + 1, z)] : 0); n++; break; }
        return n == 0 ? 0 : sum / (double) n;
    }

    static String f1(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }

    // ---- the picture ----------------------------------------------------------------------------------------------------
    void png(File out, boolean[] seen) throws Exception {
        BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            int rgb = 0x101010;
            boolean reach = false;
            for (int y = Level.WALK + 2; y >= Y0 + 1; y--) {
                if (stand(x, y, z)) {
                    int below = id(x, y - 1, z), here = id(x, y, z);
                    rgb = water(here) ? 0x3a78c8 : colour(here == Canvas.CARPET ? here : below);
                    reach = seen != null && seen[idx(x, y, z)];
                    break;
                }
            }
            if (reach) rgb = blend(rgb, 0x40ff40, 0.18);
            img.setRGB(x - x0, z - z0, rgb);
        }
        for (int[] c : chests) if (lv.contains(c[0], c[2])) img.setRGB(c[0] - x0, c[2] - z0, 0xffd000);
        ImageIO.write(img, "png", out);
    }

    static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static int colour(int id) {
        switch (id) {
            case Canvas.CARPET: return 0xd8c060;
            case Canvas.SANDSTONE: return 0xd8cc8c;
            case Canvas.CONCRETE: return 0xb8b8b8;
            case Canvas.STONE_BRICK: return 0x8a8a8a;
            case Canvas.WOOL: return 0x9a9a9a;
            case Canvas.QUARTZ: return 0xf0ece4;
            case Canvas.MAGMA: return 0xc04010;
            case Canvas.REDSTONE_ORE: return 0xb02020;
            case Canvas.GRASS: return 0x5a9a3a;
            case Canvas.DOUBLE_SLAB: return 0xa8a8a8;
            default: return 0x777777;
        }
    }

    // ---- the checks -----------------------------------------------------------------------------------------------------
    String check() {
        double[] a = Rooms.arrival(lv);
        int sx = (int) Math.floor(a[0]), sz = (int) Math.floor(a[2]);
        boolean[] seen = walk(sx, Level.WALK, sz);
        List<String> bad = new ArrayList<>();
        int ax = lv.arenaStart() + 4;
        if (!reached(seen, ax, 0, Level.WALK - 6, Level.WALK + 2)) bad.add("arena");
        int ex = lv.xEnd() - Level.WALL - 2;
        if (!reached(seen, ex, 0, Level.WALK, Level.WALK)) bad.add("exit");
        int[] b = Styles.of(lv).bossSpot(lv);
        if (!stand(b[0], b[1], b[2])) bad.add("bossSpot:" + id(b[0], b[1], b[2]) + "/" + id(b[0], b[1] - 1, b[2]));
        if (lv.number == 1) {
            int[] g = Rooms.gate();
            for (int k = 0; k < 2; k++) if (id(g[0], g[1], g[2] + k) != Canvas.PORTAL) bad.add("gate");
            for (int n = 2; n <= 7; n++) { int[] al = Rooms.doorAlcove(n); if (!reached(seen, al[0], al[1], Level.WALK, Level.WALK)) bad.add("door" + n); }
        } else if (!reached(seen, lv.x0() + 2, 0, Level.WALK, Level.WALK) && !reached(seen, lv.x0() + 3, -1, Level.WALK, Level.WALK)) bad.add("return");
        leaks = countLeaks();
        if (leaks > 0) bad.add("waterLeaks=" + leaks);
        // Light: the arrival room (the Threshold or a landing) is a safe room and bright; so is level 1's start (darker
        // deeper in, as its lights fail) and the Poolrooms.
        light();
        double arrival = lv.number == 1 ? meanLight(lv.x0() + 5, lv.x0() + 45, -Rooms.HUB_HALF + 1, Rooms.HUB_HALF - 1)
            : meanLight(lv.x0() + 5, lv.x0() + 27, -Rooms.LANDING_HALF + 1, Rooms.LANDING_HALF - 1);
        double start = meanLight(lv.entryEnd(), lv.entryEnd() + 64, z0, z0 + d), deep = meanLight(lv.arenaStart() - 64, lv.arenaStart(), z0, z0 + d);
        double body = meanLight(lv.entryEnd(), lv.arenaStart(), z0, z0 + d), arena = meanLight(lv.arenaStart(), lv.arenaStart() + lv.arenaLength, z0, z0 + d);
        lightReport = " light arrival=" + f1(arrival) + " start=" + f1(start) + " deep=" + f1(deep) + " body=" + f1(body) + " arena=" + f1(arena);
        if (arrival < 11) bad.add("darkArrival=" + f1(arrival));
        if (lv == Level.YELLOW && (start < 10.5 || deep > start - 1.5)) bad.add("yellowLight=" + f1(start) + "/" + f1(deep));
        if (lv == Level.POOLS && body < 9) bad.add("darkPools=" + f1(body));
        // Every level can be seen near its start and in its boss fight (not the Dark Room, which is meant to be dark,
        // nor the city, lit by the sky).
        if (lv != Level.CITY && start < 6) bad.add("darkStart=" + f1(start));
        if (lv != Level.CITY && lv != Level.YELLOW && arena < 5) bad.add("darkArena=" + f1(arena));
        int open = 0, reach = 0;
        for (int x = lv.entryEnd(); x < lv.arenaStart(); x++) for (int z = z0; z < z0 + d; z++) {
            boolean o = false, r = false;
            for (int y = Level.WALK - 8; y <= Level.WALK + 2; y++) if (stand(x, y, z)) { o = true; if (seen[idx(x, y, z)]) r = true; }
            if (o) open++;
            if (r) reach++;
        }
        lastSeen = seen;
        return lv.name() + " chests=" + chests.size() + " open=" + open + " reachable=" + (open == 0 ? 0 : Math.round(reach * 1000.0 / open) / 10.0) + "%"
            + lightReport + (bad.isEmpty() ? " ok" : " FAILED " + bad);
    }
    boolean[] lastSeen;
    String lightReport = "";

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20260930L;
        File dir = args.length > 1 ? new File(args[1]) : null;
        if (dir != null) dir.mkdirs();
        BackroomsGenerator gen = new BackroomsGenerator(seed, (w, e) -> { throw new RuntimeException(w, e); });
        boolean ok = true;
        for (Level lv : Level.ALL) {
            if (args.length > 2 && !args[2].contains(lv.name())) continue;
            long t0 = System.nanoTime();
            LayoutProbe p = new LayoutProbe(gen, lv);
            String r = p.check();
            System.out.println(r + " ms=" + (System.nanoTime() - t0) / 1_000_000);
            if (r.contains("FAILED")) ok = false;
            if (dir != null) p.png(new File(dir, "level" + lv.number + "-" + lv.name().toLowerCase() + ".png"), p.lastSeen);
        }
        if (ok) System.out.println("BACKROOMS_LAYOUT_OK");
        else System.exit(1);
    }
}
