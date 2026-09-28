package chat.jaspr.nether;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Offline check of the Nether mega structures and wonders (no server): builds each of the five on synthetic Nether
 * terrain chunk by chunk, exactly as population clips them, and checks that
 * <ul>
 *   <li>drawing is deterministic and clip-independent (chunk by chunk == all at once);</li>
 *   <li>no forbidden or valuable block is ever written;</li>
 *   <li>each structure has its chests, vault, garrisons and (where it should) its urn, statue or spawners;</li>
 *   <li>a chunk costs little to draw;</li>
 * </ul>
 * and renders isometric and cut-away PNGs (args: outDir blocks.tsv [kinds...]). Prints MEGA_OK when all pass.
 */
public final class MegaPreview {
    static final int SX = 240, SY = 128, SZ = 240;
    // never written by generation: valuables, unsafe or technical blocks (the owner's "no valuable blocks" rule)
    static final int[] FORBIDDEN = {41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 120, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154, 27, 28, 147, 148, 71, 167};

    static final class Volume implements Canvas {
        final int ox, oz;
        final char[] data = new char[SX * SY * SZ];
        final BitSet written = new BitSet(SX * SY * SZ);
        Volume(int ox, int oz) { this.ox = ox; this.oz = oz; }
        int idx(int x, int y, int z) { return ((x - ox) * SZ + (z - oz)) * SY + y; }
        boolean inside(int x, int y, int z) { return x >= ox && x < ox + SX && z >= oz && z < oz + SZ && y >= 0 && y < SY; }
        @Override public int get(int x, int y, int z) { return inside(x, y, z) ? data[idx(x, y, z)] : (Blocks.BEDROCK << 4); }
        @Override public void set(int x, int y, int z, int v) {
            if (!inside(x, y, z)) return;
            int i = idx(x, y, z);
            data[i] = (char) v;
            written.set(i);
        }
    }

    /** Synthetic Nether: netherrack with noise caverns, a lava sea below y 32, bedrock floor and roof. */
    static void terrain(Volume v, int salt) {
        for (int x = v.ox; x < v.ox + SX; x++) for (int z = v.oz; z < v.oz + SZ; z++) {
            double h1 = Draw.fbm(x, z, 60, salt) * 18, h2 = Draw.fbm(x, z, 45, salt + 40) * 14;
            int caveLo = 34 + (int) h1, caveHi = 70 + (int) (h2 + Draw.noise(x, z, 20, salt + 3) * 10);
            int cave2Lo = 84 + (int) (Draw.noise(x, z, 30, salt + 50) * 6), cave2Hi = cave2Lo + 6 + (int) (Draw.noise(x, z, 25, salt + 60) * 8);
            for (int y = 0; y < SY; y++) {
                int b;
                if (y == 0 || y >= 127 || (y < 5 && Draw.rnd(x, y, z, salt) < 1 - y / 5.0) || (y > 122 && Draw.rnd(x, y, z, salt) < (y - 122) / 5.0)) b = Blocks.BEDROCK << 4;
                else if ((y > caveLo && y < caveHi) || (y > cave2Lo && y < cave2Hi)) b = y <= 31 ? Blocks.LAVA << 4 : 0;
                else if (y <= 31 && y > 22 && Draw.noise(x, z, 24, salt + 9) > 0.1) b = Blocks.LAVA << 4;
                else b = Blocks.NETHERRACK << 4;
                v.data[v.idx(x, y, z)] = (char) b;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        if (!out.getName().equals("-")) out.mkdirs();
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        try (FileInputStream in = new FileInputStream(args[1])) { BlockMap.load(in); }
        Blocks.load();
        List<Mega.Kind> kinds = new ArrayList<>();
        for (int i = 2; i < args.length; i++) kinds.add(Mega.Kind.valueOf(args[i].toUpperCase()));
        if (kinds.isEmpty()) for (Mega.Kind k : Mega.Kind.values()) kinds.add(k);
        boolean ok = true;
        for (Mega.Kind k : kinds) ok &= preview(k, out);
        System.out.println(ok ? "MEGA_OK" : "MEGA_FAILED");
        if (!ok) System.exit(1);
    }

    static boolean preview(Mega.Kind kind, File out) throws Exception {
        long seed = 90210L + kind.ordinal() * 7919L;
        int cx = 1000 + kind.ordinal() * 3000 + 7, cz = -2000 + kind.ordinal() * 1100 + 3;
        Mega.Site s = Mega.prepared(new Mega.Site(kind, 0, 0, cx, cz, seed));
        Volume a = new Volume(cx - SX / 2, cz - SZ / 2), b = new Volume(cx - SX / 2, cz - SZ / 2);
        terrain(a, 5);
        terrain(b, 5);
        // chunk by chunk, as population draws it: blocks over the 2x2-chunk area, tiles in the chunk's box, and the
        // tiles placed (as their blocks) right after, so later redraws of the strip must leave them standing
        Template.Placed tiles = new Template.Placed();
        long worst = 0, total = 0; int chunks = 0, maxWrites = 0;
        for (int chx = Math.floorDiv(a.ox - 8, 16); chx * 16 + 8 < a.ox + SX; chx++)
            for (int chz = Math.floorDiv(a.oz - 8, 16); chz * 16 + 8 < a.oz + SZ; chz++) {
                int ox = chx * 16, oz = chz * 16, bx = ox + 8, bz = oz + 8;
                long t0 = System.nanoTime();
                Template.Placed here = new Template.Placed();
                Draw d = new Draw(a, ox, oz, ox + 31, oz + 31, bx, bz, bx + 15, bz + 15, here);
                Mega.draw(s, d);
                long dt = System.nanoTime() - t0;
                place(a, here);
                merge(tiles, here);
                if (d.writes > 0) { chunks++; total += dt; worst = Math.max(worst, dt); maxWrites = Math.max(maxWrites, d.writes); }
            }
        // all at once
        Template.Placed tiles2 = new Template.Placed();
        Mega.draw(s, new Draw(b, b.ox, b.oz, b.ox + SX - 1, b.oz + SZ - 1, tiles2));
        place(b, tiles2);
        boolean same = java.util.Arrays.equals(a.data, b.data) && tiles.chests.size() == tiles2.chests.size()
            && tiles.points.size() == tiles2.points.size() && tiles.spawners.size() == tiles2.spawners.size();
        Map<Integer, Integer> forbidden = new TreeMap<>();
        Map<String, Integer> census = new TreeMap<>();
        for (int i = a.written.nextSetBit(0); i >= 0; i = a.written.nextSetBit(i + 1)) {
            int id = a.data[i] >> 4;
            for (int f : FORBIDDEN) if (id == f) forbidden.merge(id, 1, Integer::sum);
        }
        Map<String, Integer> tables = new TreeMap<>(), points = new TreeMap<>(), spawners = new TreeMap<>();
        for (String t : tiles.chestTables) tables.merge(t, 1, Integer::sum);
        for (String p : tiles.pointKinds) points.merge(p.startsWith("garrison:") ? "garrison" : p, 1, Integer::sum);
        for (String m : tiles.spawnerMobs) spawners.merge(m, 1, Integer::sum);
        census.put("residents", tiles.entities.size());
        census.put("skulls", tiles.skulls.size());
        boolean vault = false;
        for (String t : tables.keySet()) if (t.endsWith("_vault")) vault = true;
        boolean ok = same && forbidden.isEmpty() && tables.size() > 0 && vault && points.getOrDefault("garrison", 0) >= (kind == Mega.Kind.BAZAAR ? 1 : 3)
            && worst / 1e6 < 60 && total / 1e6 / Math.max(1, chunks) < 12;
        switch (kind) {
            case BAZAAR: ok &= points.getOrDefault("statue", 0) >= 1 && tiles.entities.size() >= 8; break;
            case CATHEDRAL: ok &= points.getOrDefault("urn", 0) == 1; break;
            case FORGE: ok &= spawners.getOrDefault("blaze", 0) >= 2; break;
            default:
        }
        System.out.println("mega " + kind.id + " site=" + s.x + "," + s.y + "," + s.z + " chunks=" + chunks + " avgMs=" + String.format("%.2f", total / 1e6 / Math.max(1, chunks))
            + " maxMs=" + String.format("%.2f", worst / 1e6) + " maxWrites=" + maxWrites + " deterministic=" + same + " forbidden=" + forbidden
            + " chests=" + tables + " points=" + points + " spawners=" + spawners + " " + census + (ok ? " PASS" : " FAIL"));
        if (out.getName().equals("-")) return ok;   // the test run skips the pictures
        render(a, s, new File(out, kind.id + "-iso.png"), false, false, 3, SX / 2);
        render(a, s, new File(out, kind.id + "-iso-back.png"), true, false, 3, SX / 2);
        render(a, s, new File(out, kind.id + "-cut.png"), false, true, 3, SX / 2);
        render(a, s, new File(out, kind.id + "-zoom.png"), false, false, 6, 60);
        render(a, s, new File(out, kind.id + "-zoom-back.png"), true, false, 6, 60);
        render(a, s, new File(out, kind.id + "-zoom-cut.png"), false, true, 6, 60);
        top(a, s, new File(out, kind.id + "-top.png"));
        return ok;
    }

    /** What population's tile work leaves in the world: a chest, spawner or skull block at each recorded spot. */
    static void place(Volume v, Template.Placed t) {
        for (int[] c : t.chests) v.set(c[0], c[1], c[2], (Blocks.CHEST << 4) | c[3]);
        for (int[] c : t.spawners) v.set(c[0], c[1], c[2], Blocks.SPAWNER << 4);
        for (int[] c : t.skulls) v.set(c[0], c[1], c[2], Blocks.SKULL << 4 | 1);
    }

    static void merge(Template.Placed into, Template.Placed from) {
        into.chests.addAll(from.chests); into.chestTables.addAll(from.chestTables);
        into.spawners.addAll(from.spawners); into.spawnerMobs.addAll(from.spawnerMobs);
        into.skulls.addAll(from.skulls); into.entities.addAll(from.entities); into.entityKinds.addAll(from.entityKinds);
        into.points.addAll(from.points); into.pointKinds.addAll(from.pointKinds);
    }

    // ---- rendering ---------------------------------------------------------------------------------------------------
    static final int[] DYE = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447, 0x8E8E86, 0x158991, 0x792AAC, 0x35399D,
        0x724728, 0x546D1B, 0xA12722, 0x141519};
    static final int[] CLAY = {0xD1B1A1, 0xA15325, 0x95576C, 0x706C8A, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23, 0x876A61, 0x565B5B, 0x764656, 0x4A3B5B,
        0x4D3323, 0x4B522A, 0x8E3C2E, 0x251610};

    static int color(int v) {
        int id = v >> 4, m = v & 15;
        switch (id) {
            case 1: return 0x7D7D7D; case 4: return 0x7A7A7A; case 7: return 0x333333; case 8: case 9: return 0x3F76E4;
            case 10: case 11: return 0xE3781E; case 12: return 0xDBD3A0; case 13: return 0x857F7E; case 17: case 162: return 0x6B5433;
            case 18: case 161: return 0x3E7A2A; case 20: case 102: return 0xC0E0F0; case 24: return m == 2 ? 0xE0D6A8 : m == 1 ? 0xD8CB94 : 0xDBD3A0;
            case 30: return 0xDDDDDD; case 35: return DYE[m]; case 39: return 0x9A7456; case 40: return 0xC43A3A;
            case 43: case 44: return m % 8 == 6 ? 0x2C1519 : m % 8 == 7 ? 0xECE6DF : m % 8 == 1 ? 0xDBD3A0 : 0x9C9C9C;
            case 45: return 0x965A4B; case 48: return 0x5A6C4A; case 49: return 0x14121E; case 50: return 0xFFD35A; case 51: return 0xFF9A1E;
            case 52: return 0x243646; case 53: case 5: return 0xA2834F; case 54: return 0x9E6E2E; case 58: return 0x7B5A33; case 61: return 0x6E6E6E;
            case 65: return 0x8C6A39; case 67: return 0x7A7A7A; case 79: return 0x91B7FD; case 80: return 0xF0FBFB; case 85: return 0x9A7A4B;
            case 87: return 0x6F3634; case 88: return 0x51402F; case 89: return 0xF9D49C; case 95: case 160: return DYE[m];
            case 98: return 0x7A7A7A; case 99: return m == 15 ? 0xCFC9BE : m == 0 ? 0xC9A77F : 0x8D6A4F; case 100: return m == 15 ? 0xCFC9BE : m == 0 ? 0xC9A77F : 0xB32D2B;
            case 101: return 0x5E5E5E; case 107: case 183: case 184: case 185: case 186: case 187: return 0x6E5838; case 109: return 0x7A7A7A;
            case 110: return 0x6F6369; case 112: case 113: case 114: return 0x2C1519; case 115: return 0x8A1818; case 118: return 0x3A3A3A;
            case 121: return 0xDDDFA5; case 126: return 0xA2834F; case 128: return 0xDBD3A0; case 139: return 0x7A7A7A; case 144: return 0xCCCCCC;
            case 153: return 0x7D4E4A; case 155: case 156: return 0xECE6DF; case 159: return CLAY[m]; case 168: return m == 0 ? 0x63A597 : 0x5CA28E;
            case 169: return 0xAECBC0; case 171: return DYE[m]; case 172: return 0x985E43; case 173: return 0x111111; case 174: return 0x8DB4FA;
            case 179: case 180: case 181: case 182: return m == 2 ? 0xB9612A : 0xA8551F; case 188: return 0x5A4228; case 189: return 0xC8B77A;
            case 190: return 0x9A6E4B; case 191: return 0x3D2813; case 192: return 0xA85A32; case 198: return 0xF0F0F0; case 201: case 202: case 203: case 205: return 0xA97EA9;
            case 206: return 0xE2E7AB; case 213: return 0x8E3F1F; case 214: return 0x730303; case 215: return 0x450709; case 216: return 0xE1DDC9;
            case 251: return DYE[m]; case 252: return DYE[m];
            default:
                if (id >= 235 && id <= 250) return DYE[id - 235];
                return 0xFF00FF;
        }
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static boolean shown(Volume v, int x, int y, int z, boolean cut, int cx) {
        if (!v.inside(x, y, z)) return false;
        int i = v.idx(x, y, z);
        if (!v.written.get(i) || v.data[i] == 0) return false;
        return !(cut && x > cx);
    }

    /** Isometric view from the south-east (or the north-west with back): only what the site wrote is drawn. */
    static void render(Volume v, Mega.Site s, File f, boolean back, boolean cut, int S, int half) throws Exception {
        int NX = 2 * half, NZ = 2 * half, bx = s.x - half, bz = s.z - half;
        int w = (NX + NZ) * S + 8, h = (NX + NZ) * S / 2 + SY * S + 8;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x201014));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int sum = 0; sum < NX + NZ - 1; sum++) {
            for (int i = Math.max(0, sum - NZ + 1); i <= Math.min(NX - 1, sum); i++) {
                int j = sum - i;
                int x = back ? bx + NX - 1 - i : bx + i, z = back ? bz + NZ - 1 - j : bz + j;
                int dx = back ? -1 : 1;
                for (int y = 0; y < SY; y++) {
                    if (!shown(v, x, y, z, cut, s.x)) continue;
                    boolean top = !shown(v, x, y + 1, z, cut, s.x), east = !shown(v, x + dx, y, z, cut, s.x), south = !shown(v, x, y, z + dx, cut, s.x);
                    if (!top && !east && !south) continue;
                    int c = color(v.data[v.idx(x, y, z)]);
                    int px = (i - j) * S + NZ * S + 4, py = (i + j) * S / 2 - y * S + SY * S + 4;
                    if (top) { g.setColor(new Color(shade(c, 1.0))); g.fillPolygon(new int[]{px, px + S, px, px - S}, new int[]{py - S, py - S / 2, py, py - S / 2}, 4); }
                    if (south) { g.setColor(new Color(shade(c, 0.62))); g.fillPolygon(new int[]{px - S, px, px, px - S}, new int[]{py - S / 2, py, py + S, py + S / 2}, 4); }
                    if (east) { g.setColor(new Color(shade(c, 0.8))); g.fillPolygon(new int[]{px, px + S, px + S, px}, new int[]{py, py - S / 2, py + S / 2, py + S}, 4); }
                }
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    /** Top-down map of the highest block the site wrote below its dome, shaded by height. */
    static void top(Volume v, Mega.Site s, File f) throws Exception {
        int S = 3;
        BufferedImage img = new BufferedImage(SX * S, SZ * S, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < SX; i++) for (int j = 0; j < SZ; j++) {
            int x = v.ox + i, z = v.oz + j, c = 0x201014;
            for (int y = 121; y > 0; y--) {
                int k = v.idx(x, y, z);
                if (!v.written.get(k) || v.data[k] == 0) continue;
                c = shade(color(v.data[k]), 0.55 + 0.45 * (y - 30) / 80.0);
                break;
            }
            for (int a = 0; a < S; a++) for (int b2 = 0; b2 < S; b2++) img.setRGB(i * S + a, j * S + b2, c);
        }
        javax.imageio.ImageIO.write(img, "png", f);
    }
}
