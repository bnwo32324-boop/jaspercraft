package chat.jaspr.ruins;

import chat.jaspr.lostcities.CityApi;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/**
 * Offline check of Drownhollow's great structures (epoch 6; no server; run by tests/ruins.test.cjs and by whoever draws
 * one up): for every kind (or those named in -Donly=KIND,KIND), the nearest planned site to the origin is generated
 * chunk by chunk with the real generator, and checked: the populator recomputes exactly its chests, spawners and signs;
 * the boss's hall is open where it wakes; every garrison point stands free and together they hold every kind of monster;
 * no tree, plant or precious block; a fresh generator draws the same chunks; chunks stay quick. Renders a top view and
 * two isometric views of each into the directory given as the first argument ("-" for none). Prints GREATS_OK.
 */
public final class GreatPreview {
    static final Set<Integer> BANNED = new HashSet<>(Arrays.asList(17, 18, 161, 162, 6, 37, 38, 175, 111, 39, 40, 83, 81, 99, 100,
        41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 120, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154, 27, 28, 147, 148, 71, 167));

    public static void main(String[] args) throws Exception {
        String outArg = args.length > 0 ? args[0] : "-";
        boolean pictures = !outArg.equals("-");
        File out = new File(outArg);
        if (pictures) out.mkdirs();
        long seed = RuinsPreview.SEED;
        Plans.Reserved reserved = (x, z, w, d) -> {
            for (int cx = Math.floorDiv(x, 16) - 1; cx <= Math.floorDiv(x + w - 1, 16) + 1; cx++)
                for (int cz = Math.floorDiv(z, 16) - 1; cz <= Math.floorDiv(z + d - 1, 16) + 1; cz++)
                    if (CityApi.cityRegion(seed, cx, cz)) return true;
            return false;
        };
        int[] failures = {0};
        RuinsGenerator gen = new RuinsGenerator(seed, reserved, (w, e) -> { failures[0]++; e.printStackTrace(); });
        String only = System.getProperty("only", "");
        Set<String> wanted = new TreeSet<>();
        if (!only.isEmpty()) wanted.addAll(Arrays.asList(only.toUpperCase(java.util.Locale.ROOT).split(",")));

        // the nearest planned site of each kind, ring by ring around the origin
        Map<Plans.Great, Plans.GreatSite> sites = new TreeMap<>();
        int planned = 0, cells = 0;
        for (int r = 0; r <= 24; r++)
            for (int i = -r; i <= r; i++)
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    Plans.GreatSite g = gen.plans.great(i, j);
                    if (r <= 8) { cells++; if (g != null) planned++; }
                    if (g != null) sites.putIfAbsent(g.kind, g);
                }
        System.out.println("great plan cells=" + cells + " planned=" + planned + String.format(java.util.Locale.ROOT, " share=%.2f", planned / (double) cells)
            + " kindsFound=" + sites.size() + "/" + Plans.Great.values().length);
        boolean ok = true;
        for (Plans.Great k : Plans.Great.values()) {
            if (!wanted.isEmpty() && !wanted.contains(k.name())) continue;
            Plans.GreatSite s = sites.get(k);
            if (s == null) { System.out.println("great " + k + " NOT_PLANNED FAIL"); ok = false; continue; }
            ok &= check(gen, s, out, pictures);
        }
        ok &= failures[0] == 0;
        System.out.println(ok ? "GREATS_OK" : "GREATS_FAILED failures=" + failures[0]);
        if (!ok) System.exit(1);
    }

    static boolean check(RuinsGenerator gen, Plans.GreatSite s, File out, boolean pictures) throws Exception {
        int r = s.kind.radius;
        int cx0 = Math.floorDiv(s.x - r - 2, 16), cx1 = Math.floorDiv(s.x + r + 2, 16), cz0 = Math.floorDiv(s.z - r - 2, 16), cz1 = Math.floorDiv(s.z + r + 2, 16);
        long worst = 0, total = 0;
        int chunks = 0;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                long before = RuinsPreview.genNanos;
                RuinsPreview.chunk(gen, cx, cz);
                long dt = RuinsPreview.genNanos - before;
                total += dt;
                worst = Math.max(worst, dt);
                chunks++;
            }
        // tiles: the populator's recomputation matches the blocks; count this structure's own
        int chests = 0, spawners = 0, signs = 0, traps = 0;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                RuinsPreview.verifyTiles(gen, cx, cz);
                for (Canvas.Tile t : RuinsPopulator.tilesOf(gen, cx, cz)) {
                    if (!s.covers(t.x, t.z, 1) || t.y < s.base - 14) continue;      // not the catacombs far below
                    if (t.kind == Canvas.CHEST_TILE) chests++;
                    else if (t.kind == Canvas.SPAWNER_TILE) spawners++;
                    else if (t.kind == Canvas.DISPENSER_TILE) traps++;
                    else signs++;
                }
            }
        // the boss's hall
        int[] b = Greats.boss(s);
        boolean water = s.kind == Plans.Great.CISTERN;
        int at = id(gen, b[0], b[1], b[2]), above = id(gen, b[0], b[1] + 1, b[2]), below = id(gen, b[0], b[1] - 1, b[2]);
        boolean bossOk = s.covers(b[0], b[2], 0) && (water ? (at == 8 || at == 9 || at == 0) : at == 0 && above == 0) && solid(below);
        // the garrisons: free ground at every point, every kind of monster among them
        List<Greats.Point> gs = Greats.garrisons(s);
        Set<String> held = new TreeSet<>();
        int bad = 0;
        StringBuilder badAt = new StringBuilder();
        for (Greats.Point g : gs) {
            boolean wet = false, flier = true;
            for (String k : (g.pack.startsWith("!") ? g.pack.substring(1) : g.pack).split("[+]")) {
                held.add(k);
                if (GreatDesign.WATER.contains(k)) wet = true;
                if (!k.equals("nightgaunt")) flier = false;
            }
            int a = id(gen, g.x, g.y, g.z), a1 = id(gen, g.x, g.y + 1, g.z), d = id(gen, g.x, g.y - 1, g.z);
            boolean free = wet ? (a == 8 || a == 9) : a == 0 && a1 == 0 && (flier || solid(d));
            if (!free || !s.covers(g.x, g.z, 0)) { bad++; if (badAt.length() < 200) badAt.append(' ').append(g.x).append(',').append(g.y).append(',').append(g.z).append('=').append(a).append('/').append(a1).append('/').append(d); }
        }
        Set<String> lacking = new TreeSet<>(Arrays.asList(GreatDesign.ROSTER));
        lacking.removeAll(held);
        Set<String> strangers = new TreeSet<>(held);
        strangers.removeAll(Arrays.asList(GreatDesign.ROSTER));
        strangers.removeAll(GreatDesign.WATER);
        // no tree, plant or precious block in the footprint; how high it rises
        int forbidden = 0, top = 0;
        Map<Integer, Integer> forbiddenIds = new TreeMap<>();
        for (int x = s.x - r - 1; x <= s.x + r + 1; x++)
            for (int z = s.z - r - 1; z <= s.z + r + 1; z++) {
                RuinsPreview.Chunk c = RuinsPreview.chunk(gen, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
                for (int y = 1; y < 256; y++) {
                    int id = c.id(lx, y, lz);
                    if (id == 0) continue;
                    if (BANNED.contains(id)) { forbidden++; forbiddenIds.merge(id, 1, Integer::sum); }
                    if (id != 8 && id != 9) top = Math.max(top, y);
                }
            }
        // determinism: a fresh generator draws the same chunks (the boss's, and two corners)
        RuinsGenerator again = new RuinsGenerator(gen.seed, gen.reserved, null);
        boolean same = true;
        int[][] probe = {{Math.floorDiv(b[0], 16), Math.floorDiv(b[2], 16)}, {cx0 + 1, cz0 + 1}, {cx1 - 1, cz1 - 1}};
        for (int[] p : probe) {
            RuinsPreview.Chunk fresh = new RuinsPreview.Chunk();
            again.fill(fresh.proxy(), RuinsPreview.biomes(), p[0], p[1]);
            same &= Arrays.equals(fresh.blocks, RuinsPreview.chunk(gen, p[0], p[1]).blocks);
        }
        double avg = total / 1e6 / Math.max(1, chunks), max = worst / 1e6;
        boolean ok = bossOk && bad == 0 && lacking.isEmpty() && strangers.isEmpty() && forbidden == 0 && same && chests >= 12 && spawners >= 2 && signs >= 3
            && gs.size() >= 14 && avg < 30 && max < 160;
        System.out.println("great " + s.kind + " at=" + s.x + "," + s.z + " base=" + s.base + " rot=" + s.rot + " sea=" + s.sea() + " top=" + top + " chunks=" + chunks
            + String.format(java.util.Locale.ROOT, " avgMs=%.2f maxMs=%.2f", avg, max) + " chests=" + chests + " spawners=" + spawners + " signs=" + signs + " traps=" + traps
            + " garrisons=" + gs.size() + " roster=" + (lacking.isEmpty() ? "complete" : "lacking" + lacking) + " strangers=" + strangers + " badPoints=" + bad + badAt
            + " boss=" + (bossOk ? "ok" : "BAD(" + b[0] + "," + b[1] + "," + b[2] + " " + at + "/" + above + "/" + below + ")") + " forbidden=" + forbiddenIds
            + " deterministic=" + same + (ok ? " PASS" : " FAIL"));
        if (pictures) {
            String n = s.kind.name().toLowerCase(java.util.Locale.ROOT);
            RuinsPreview.render(gen, s.x - r - 8, s.z - r - 8, 2 * r + 17, 2 * r + 17, new File(out, n + "-top.png"));
            iso(gen, s, false, false, new File(out, n + "-iso.png"));
            iso(gen, s, true, false, new File(out, n + "-iso-back.png"));
            iso(gen, s, false, true, new File(out, n + "-cut.png"));
        }
        return ok;
    }

    static int id(RuinsGenerator gen, int x, int y, int z) { return RuinsPreview.chunk(gen, Math.floorDiv(x, 16), Math.floorDiv(z, 16)).id(Math.floorMod(x, 16), y, Math.floorMod(z, 16)); }

    static boolean solid(int id) {
        return id != 0 && id != 8 && id != 9 && id != 10 && id != 11 && id != 30 && id != 31 && id != 50 && id != 65 && id != 68 && id != 106 && id != 70 && id != 78;
    }

    /** Isometric view of the footprint, the land cut away below base - 14; {@code cut} slices it through the centre. */
    static void iso(RuinsGenerator gen, Plans.GreatSite s, boolean back, boolean cut, File f) throws Exception {
        int half = s.kind.radius + 4, S = 3, n = 2 * half;
        int yMin = Math.max(1, s.base - 14), yMax = 200, sy = yMax - yMin;
        int w = (n + n) * S + 8, h = n * S + sy * S + 8;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x12161a));
        g.fillRect(0, 0, w, h);
        int bx = s.x - half, bz = s.z - half;
        for (int sum = 0; sum < n + n - 1; sum++)
            for (int i = Math.max(0, sum - n + 1); i <= Math.min(n - 1, sum); i++) {
                int j = sum - i;
                int x = back ? bx + n - 1 - i : bx + i, z = back ? bz + n - 1 - j : bz + j, dx = back ? -1 : 1;
                for (int y = yMin; y < yMax; y++) {
                    if (!shown(gen, s, x, y, z, cut, yMin)) continue;
                    boolean topF = !shown(gen, s, x, y + 1, z, cut, yMin), east = !shown(gen, s, x + dx, y, z, cut, yMin), south = !shown(gen, s, x, y, z + dx, cut, yMin);
                    if (!topF && !east && !south) continue;
                    RuinsPreview.Chunk c = RuinsPreview.chunk(gen, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                    int col = colour(c.id(Math.floorMod(x, 16), y, Math.floorMod(z, 16)), c.data(Math.floorMod(x, 16), y, Math.floorMod(z, 16)));
                    int px = (i - j) * S + n * S + 4, py = (i + j) * S / 2 - (y - yMin) * S + sy * S + 4;
                    if (topF) { g.setColor(new java.awt.Color(RuinsPreview.scale(col, 1.0))); g.fillPolygon(new int[] {px, px + S, px, px - S}, new int[] {py - S, py - S / 2, py, py - S / 2}, 4); }
                    if (south) { g.setColor(new java.awt.Color(RuinsPreview.scale(col, 0.62))); g.fillPolygon(new int[] {px - S, px, px, px - S}, new int[] {py - S / 2, py, py + S, py + S / 2}, 4); }
                    if (east) { g.setColor(new java.awt.Color(RuinsPreview.scale(col, 0.8))); g.fillPolygon(new int[] {px, px + S, px + S, px}, new int[] {py, py - S / 2, py + S / 2, py + S}, 4); }
                }
            }
        g.dispose();
        ImageIO.write(img, "png", f);
    }

    static boolean shown(RuinsGenerator gen, Plans.GreatSite s, int x, int y, int z, boolean cut, int yMin) {
        int half = s.kind.radius + 4;
        if (y < yMin || Math.abs(x - s.x) >= half || Math.abs(z - s.z) >= half) return false;
        if (cut && x > s.x) return false;
        int id = id(gen, x, y, z);
        return id != 0 && id != 8 && id != 9;
    }

    static int colour(int id, int data) {
        switch (id) {
            case 1: return data == 5 || data == 6 ? 0x8a8a8c : data == 1 || data == 2 ? 0x9a6a5a : 0x7d7d7d;
            case 2: return 0x5f9f35; case 3: return 0x866043; case 4: return 0x6e6e6e; case 5: return 0xa0824e; case 7: return 0x333333;
            case 12: return 0xdbd3a0; case 13: return 0x857f7c; case 20: return 0xc8e0e8; case 24: return 0xd8cc94; case 30: return 0xe8e8e8;
            case 35: return 0xb0b0b0; case 43: case 44: return 0xa8a8a8; case 45: return 0x9a4a3a; case 47: return 0x8a6a3a; case 48: return 0x5f7a5a;
            case 49: return 0x1a1028; case 50: return 0xffd060; case 52: return 0x200020; case 54: return 0xff9a00; case 65: return 0xa0824e;
            case 67: return 0x7a7a7a; case 68: return 0x8a6a3a; case 70: return 0x8c8c8c; case 79: case 174: return 0xa8c8f0; case 80: return 0xf0f8ff;
            case 82: return 0x9fa4b1; case 85: return 0x8a6a3a; case 87: return 0x6a2a2a; case 88: return 0x4a3a2a; case 89: return 0xf8d878;
            case 95: case 160: return 0x7088a0; case 98: return data == 1 ? 0x6f8a68 : data == 2 ? 0x646464 : data == 3 ? 0xa0a0a0 : 0x8c8c8c;
            case 101: return 0x505050; case 106: return 0x3a6a2a; case 109: case 108: return 0x949494; case 112: case 114: case 113: return 0x3a1a1e;
            case 118: return 0x303030; case 121: case 206: return 0xe0e0a8; case 139: return 0x6f8a68; case 144: return 0xd8d8d8; case 155: case 156: return 0xece6dc;
            case 159: return clay(data); case 165: return 0x7cc56a; case 168: return data == 2 ? 0x24463a : data == 1 ? 0x4f8f7e : 0x5f9c90; case 169: return 0xcfe8e0;
            case 172: return 0x96593e; case 179: case 180: return 0xb05a2a; case 201: case 202: case 203: return 0xa878a8; case 213: return 0xb04010;
            case 214: return 0x7a1010; case 215: return 0x501414; case 216: return 0xe0dcc8; case 251: return clay(data); case 9: case 8: return 0x3050d0;
            case 10: case 11: return 0xe06010; case 23: return 0x606060; case 63: return 0x8a6a3a; case 96: return 0x8a6a3a;
            default: return 0xff00ff;
        }
    }

    static int clay(int d) {
        int[] c = {0xd0b0a0, 0xa05020, 0x904060, 0x707890, 0xb08020, 0x607030, 0xa04a4a, 0x3a2a24, 0x806a60, 0x565a5a, 0x76465a, 0x4a3a5a, 0x4a3020, 0x4a5228, 0x8a3a2a, 0x24180f};
        return c[d & 15];
    }
}
