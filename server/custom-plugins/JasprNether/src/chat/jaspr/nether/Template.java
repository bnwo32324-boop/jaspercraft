package chat.jaspr.nether;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A converted structure template (BetterNether / NetherEx .nbt -> vanilla id:meta, see the resources/structures/*.jnt
 * files and the converter mapping table). Rotation r is a number of clockwise quarter turns; rotated coordinates are
 * shifted back to positive, as vanilla Template placement with the mods' centring does.
 */
final class Template {
    static final int NORMAL = 0, CHEST = 1, SPAWNER = 2, SKULL = 3;

    static final class Marker {
        final int x, y, z; final String type, arg;
        Marker(int x, int y, int z, String type, String arg) { this.x = x; this.y = y; this.z = z; this.type = type; this.arg = arg; }
    }

    final String name;
    final int sx, sy, sz;
    final int[] palette;      // combined vanilla id << 4 | meta, resolved from the mod ids through blocks.tsv; VOID = keep
    final byte[] kind;
    final byte[] data;        // (y * sz + z) * sx + x ; 255 = leave the world block
    final List<Marker> markers = new ArrayList<>();
    final int solidCount;

    private Template(String name, int sx, int sy, int sz, int[] palette, byte[] kind, byte[] data) {
        this.name = name; this.sx = sx; this.sy = sy; this.sz = sz; this.palette = palette; this.kind = kind; this.data = data;
        int n = 0;
        for (byte b : data) if ((b & 255) != 255 && palette[b & 255] > 0) n++;
        solidCount = n;
    }

    static Template read(String name, InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        int sx = 0, sy = 0, sz = 0;
        int[] pal = null; byte[] kinds = null; byte[] data = null;
        List<Marker> markers = new ArrayList<>();
        String line;
        int palRead = 0;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) continue;
            String[] p = line.split(" ");
            switch (p[0]) {
                case "size": sx = Integer.parseInt(p[1]); sy = Integer.parseInt(p[2]); sz = Integer.parseInt(p[3]); break;
                case "palette": { int n = Integer.parseInt(p[1]); pal = new int[n]; kinds = new byte[n]; palRead = 0; break; }
                case "data": data = Base64.getDecoder().decode(p[1]); break;
                case "marker": markers.add(new Marker(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], p.length > 5 ? p[5] : "-")); break;
                case "name": break;
                default:
                    if (pal != null && palRead < pal.length && p.length == 3) {
                        pal[palRead] = BlockMap.resolve(p[0], Integer.parseInt(p[1]));   // mod block -> blocks.tsv
                        kinds[palRead] = Byte.parseByte(p[2]);
                        palRead++;
                    }
            }
        }
        if (pal == null || data == null || data.length != sx * sy * sz) throw new IOException("bad template " + name);
        Template t = new Template(name, sx, sy, sz, pal, kinds, data);
        t.markers.addAll(markers);
        return t;
    }

    int width(int rot) { return (rot & 1) == 0 ? sx : sz; }
    int depth(int rot) { return (rot & 1) == 0 ? sz : sx; }

    /** Rotated local x/z of template cell (x, z). */
    int rx(int x, int z, int rot) {
        switch (rot & 3) { case 1: return sz - 1 - z; case 2: return sx - 1 - x; case 3: return z; default: return x; }
    }
    int rz(int x, int z, int rot) {
        switch (rot & 3) { case 1: return x; case 2: return sz - 1 - z; case 3: return sx - 1 - x; default: return z; }
    }

    private static final int[] STAIR_CW = {2, 3, 1, 0};   // east->south, west->north, south->west, north->east
    private static final int[] FACE_CW = {0, 1, 5, 4, 2, 3}; // 2 north->east(5), 3 south->west(4), 4 west->north(2), 5 east->south(3)
    private static final int[] SHROOM_CW = {0, 3, 6, 9, 2, 5, 8, 1, 4, 7, 10, 11, 12, 13, 14, 15};
    private static final boolean[] STAIRS = new boolean[256];
    static {
        for (int id : new int[]{53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180, 203}) STAIRS[id] = true;
    }

    /** Rotates one combined id/meta clockwise by a quarter turn. */
    static int rotateOnce(int combined) {
        int id = combined >> 4, m = combined & 15;
        if (id < 256 && STAIRS[id]) return (id << 4) | (m & 4) | STAIR_CW[m & 3];
        switch (id) {
            case 64: case 71: case 193: case 194: case 195: case 196: case 197:
                return m >= 8 ? combined : (id << 4) | (m & 4) | ((m + 1) & 3);
            case 107: case 183: case 184: case 185: case 186: case 187:
                return (id << 4) | (m & 4) | ((m + 1) & 3);
            case 54: case 61: case 62: case 65: case 146: case 198:
                return m >= 2 && m <= 5 ? (id << 4) | FACE_CW[m] : combined;
            case 17: case 162: case 216: case 170: case 202: {
                int axis = m & 12;
                if (axis == 4) axis = 8; else if (axis == 8) axis = 4;
                return (id << 4) | (m & 3) | axis;
            }
            case 155: return m == 3 ? (id << 4) | 4 : m == 4 ? (id << 4) | 3 : combined;
            case 99: case 100: return (id << 4) | SHROOM_CW[m];
            default: return combined;
        }
    }

    static int rotate(int combined, int rot) {
        for (int i = 0; i < (rot & 3); i++) combined = rotateOnce(combined);
        return combined;
    }

    /** Result of placing a template: tile work to do after the area flush. */
    static final class Placed {
        final List<int[]> chests = new ArrayList<>();      // x y z facing
        final List<String> chestTables = new ArrayList<>();
        final List<int[]> spawners = new ArrayList<>();
        final List<String> spawnerMobs = new ArrayList<>();
        final List<int[]> skulls = new ArrayList<>();       // x y z type rot
        final List<int[]> entities = new ArrayList<>();
        final List<String> entityKinds = new ArrayList<>();
        final List<int[]> points = new ArrayList<>();       // urn, blue fire, ends
        final List<String> pointKinds = new ArrayList<>();
        final List<GlmSites.Pending> late = new ArrayList<>(); // GLM tile blocks (chests, signs, banners, skulls, pots)
    }

    /**
     * Writes the template with its minimum corner at (ox, oy, oz). Air cells carve (vanilla Template semantics);
     * void cells keep the world block. Tile entities are recorded in {@code out} and left as air for now.
     */
    void place(Area a, int ox, int oy, int oz, int rot, Placed out) {
        Map<Integer, Integer> rotated = new HashMap<>();
        for (int y = 0; y < sy; y++) {
            int wy = oy + y;
            if (wy < 1 || wy >= Area.H) continue;
            for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
                int p = data[(y * sz + z) * sx + x] & 255;
                if (p == 255 || palette[p] == BlockMap.VOID) continue;
                int wx = ox + rx(x, z, rot), wz = oz + rz(x, z, rot);
                if (!a.inside(wx, wy, wz)) continue;
                int k = kind[p];
                if (k == CHEST || k == SPAWNER || k == SKULL) { a.set(wx, wy, wz, 0, 0); continue; }
                Integer c = rotated.get(p);
                if (c == null) { c = rotate(palette[p], rot); rotated.put(p, c); }
                a.set(wx, wy, wz, c);
            }
        }
        if (out == null) return;
        for (Marker m : markers) {
            int wx = ox + rx(m.x, m.z, rot), wy = oy + m.y, wz = oz + rz(m.x, m.z, rot);
            if (!a.inside(wx, wy, wz)) continue;
            switch (m.type) {
                case "chest": {
                    int facing = 2;
                    int p = data[(m.y * sz + m.z) * sx + m.x] & 255;
                    if (p != 255 && (palette[p] >> 4) == Blocks.CHEST) facing = rotate(palette[p], rot) & 15;
                    out.chests.add(new int[]{wx, wy, wz, facing}); out.chestTables.add(m.arg); break;
                }
                case "spawner": out.spawners.add(new int[]{wx, wy, wz}); out.spawnerMobs.add(m.arg); break;
                case "skull": {
                    String[] tr = m.arg.split(":");
                    int type = Integer.parseInt(tr[0]), r = (Integer.parseInt(tr[1]) + rot * 4) & 15;
                    if (type == 1) type = 0; // owner rule: no wither skulls handed out by generation
                    out.skulls.add(new int[]{wx, wy, wz, type, r}); break;
                }
                case "entity": out.entities.add(new int[]{wx, wy, wz}); out.entityKinds.add(m.arg); break;
                default: out.points.add(new int[]{wx, wy, wz}); out.pointKinds.add(m.type);
            }
        }
    }

    /** Fraction of the rotated footprint volume at (ox, oy, oz) that is currently air. */
    double airFraction(Area a, int ox, int oy, int oz, int rot, int step) {
        int total = 0, air = 0;
        for (int y = 0; y < sy; y += step) for (int z = 0; z < sz; z += step) for (int x = 0; x < sx; x += step) {
            int wx = ox + rx(x, z, rot), wz = oz + rz(x, z, rot);
            total++;
            if (a.air(wx, oy + y, wz)) air++;
        }
        return total == 0 ? 0 : (double) air / total;
    }
}
