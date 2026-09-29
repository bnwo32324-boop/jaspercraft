package chat.jaspr.nether;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * One of the owner's GLM Nether structures, converted by tools/convert_glm.py into resources/glm/KEY.glb (gzip,
 * big-endian). The volume holds palette indices in (y * sz + z) * sx + x order; each palette entry has a role (a literal
 * block, air, "keep the world", a region placeholder such as the ground or a tree, or a tile block placed after the
 * flush) and its value for each of the four quarter turns. The cavern map (the build's box plus a margin on every side)
 * holds per column its kind (0 none, 1 cavern, 2 sealing ring, 3 cavern where a lake may lie), the ceiling height above
 * the floor, and the lowest layer of the build there (255 none), below which nothing needs encasing.
 */
final class GlmBuild {
    static final int LIT = 0, AIR = 1, VOID = 2, SURFACE = 3, SOIL = 4, ROCK = 5, SAND = 6, CANOPY = 7, TRUNK = 8, PLANT = 9,
        LIQUID = 10, SNOW = 11, PATH = 12, TILE = 13;
    static final int T_CHEST = 1, T_SIGN = 2, T_BANNER = 3, T_SKULL = 4, T_POT = 5;

    static final class Tile {
        final int type, x, y, z, pal;
        boolean trapped; String table = ""; String[] lines; int base, skull, rot; int[] patternColours; String[] patterns; String item;
        Tile(int type, int x, int y, int z, int pal) { this.type = type; this.x = x; this.y = y; this.z = z; this.pal = pal; }
    }

    static final class Marker {
        final int x, y, z; final String type, arg;
        Marker(int x, int y, int z, String type, String arg) { this.x = x; this.y = y; this.z = z; this.type = type; this.arg = arg; }
    }

    final String key;
    final int sx, sy, sz, ground, margin, cw, cd;
    final byte[] roles;
    final char[][] values;          // [rot][palette entry]
    private final byte[] vol8;
    private final char[] vol16;
    final byte[] kind, ceil, low;
    final List<Tile> tiles = new ArrayList<>();
    final List<Marker> markers = new ArrayList<>();
    final long bytes;

    private GlmBuild(String key, int sx, int sy, int sz, int ground, int margin, byte[] roles, char[][] values, byte[] vol8, char[] vol16,
                     byte[] kind, byte[] ceil, byte[] low) {
        this.key = key; this.sx = sx; this.sy = sy; this.sz = sz; this.ground = ground; this.margin = margin;
        this.roles = roles; this.values = values; this.vol8 = vol8; this.vol16 = vol16; this.kind = kind; this.ceil = ceil; this.low = low;
        this.cw = sx + 2 * margin; this.cd = sz + 2 * margin;
        this.bytes = (vol8 != null ? vol8.length : vol16.length * 2L) + kind.length * 3L + roles.length * 9L;
    }

    static GlmBuild read(String key, InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'J' || magic[1] != 'G' || magic[2] != 'L' || magic[3] != 'B') throw new IOException("not a GLM build: " + key);
        int version = in.readUnsignedByte();
        if (version != 1) throw new IOException("GLM build " + key + " has version " + version);
        int sx = in.readUnsignedShort(), sy = in.readUnsignedShort(), sz = in.readUnsignedShort();
        int ground = in.readShort(), margin = in.readUnsignedByte();
        int n = in.readUnsignedShort();
        byte[] roles = new byte[n];
        char[][] values = new char[4][n];
        for (int i = 0; i < n; i++) {
            roles[i] = (byte) in.readUnsignedByte();
            for (int r = 0; r < 4; r++) values[r][i] = (char) in.readUnsignedShort();
        }
        boolean wide = in.readUnsignedByte() == 1;
        int cells = sx * sy * sz;
        byte[] v8 = null; char[] v16 = null;
        if (wide) { v16 = new char[cells]; for (int i = 0; i < cells; i++) v16[i] = in.readChar(); }
        else { v8 = new byte[cells]; in.readFully(v8); }
        int cols = (sx + 2 * margin) * (sz + 2 * margin);
        byte[] kind = new byte[cols], ceil = new byte[cols], low = new byte[cols];
        in.readFully(kind); in.readFully(ceil); in.readFully(low);
        GlmBuild b = new GlmBuild(key, sx, sy, sz, ground, margin, roles, values, v8, v16, kind, ceil, low);
        int tiles = in.readUnsignedShort();
        for (int i = 0; i < tiles; i++) {
            int type = in.readUnsignedByte();
            Tile t = new Tile(type, in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort());
            switch (type) {
                case T_CHEST: t.trapped = in.readUnsignedByte() == 1; t.table = in.readUTF(); break;
                case T_SIGN: t.lines = new String[]{in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF()}; break;
                case T_BANNER: {
                    t.base = in.readUnsignedByte();
                    int k = in.readUnsignedByte();
                    t.patternColours = new int[k]; t.patterns = new String[k];
                    for (int j = 0; j < k; j++) { t.patternColours[j] = in.readUnsignedByte(); t.patterns[j] = in.readUTF(); }
                    break;
                }
                case T_SKULL: t.skull = in.readUnsignedByte(); t.rot = in.readUnsignedByte(); break;
                case T_POT: t.item = in.readUTF(); break;
                default: throw new IOException("GLM build " + key + ": tile type " + type);
            }
            b.tiles.add(t);
        }
        int markers = in.readUnsignedShort();
        for (int i = 0; i < markers; i++) {
            int x = in.readShort(), y = in.readShort(), z = in.readShort();   // may lie just outside the box (cavern floor)
            b.markers.add(new Marker(x, y, z, in.readUTF(), in.readUTF()));
        }
        return b;
    }

    /** Palette entry of build cell (x, y, z) (unrotated build coordinates). */
    int pal(int x, int y, int z) {
        int i = (y * sz + z) * sx + x;
        return vol8 != null ? vol8[i] & 255 : vol16[i];
    }

    /** Rotated cavern-map extents (the site's box). */
    int cavWidth(int rot) { return (rot & 1) == 0 ? cw : cd; }
    int cavDepth(int rot) { return (rot & 1) == 0 ? cd : cw; }

    /** Cavern-map cell at rotated local (u, v): the inverse of the clockwise turns Template uses. */
    int cavX(int u, int v, int rot) {
        switch (rot & 3) { case 1: return v; case 2: return cw - 1 - u; case 3: return cw - 1 - v; default: return u; }
    }
    int cavZ(int u, int v, int rot) {
        switch (rot & 3) { case 1: return cd - 1 - u; case 2: return cd - 1 - v; case 3: return u; default: return v; }
    }
    /** Rotated local (u, v) of cavern-map cell (X, Z). */
    int rotU(int x, int z, int rot) {
        switch (rot & 3) { case 1: return cd - 1 - z; case 2: return cw - 1 - x; case 3: return z; default: return x; }
    }
    int rotV(int x, int z, int rot) {
        switch (rot & 3) { case 1: return x; case 2: return cd - 1 - z; case 3: return cw - 1 - x; default: return z; }
    }
}
