package chat.jaspr.nether;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Offline check of the colossal structures and the Endless Catacombs (no server; run by tests/nether-colossi.test.cjs):
 * <ul>
 *   <li>the Great Pyramid and the Caldera Citadel, each drawn chunk by chunk on synthetic Nether terrain exactly as
 *       population clips it: deterministic (== all at once), no forbidden block, its bosses' arenas, chests, vault,
 *       garrisons, spawners, clues and ordeals, and cheap;</li>
 *   <li>the Catacombs: two windows (the Heart, a Warden's vault) drawn the same way and checked likewise; and the whole
 *       labyrinth's map, every open column walked from the Heart: the four vaults' doors reached, nearly all of it
 *       connected, lava never left touching the open space;</li>
 * </ul>
 * and renders PNGs (args: outDir|- blocks.tsv). Prints COLOSSI_OK when all pass.
 */
public final class ColossusPreview {
    static final int SY = 128;
    static final int[] FORBIDDEN = MegaPreview.FORBIDDEN;

    static final class Volume implements Canvas {
        final int ox, oz, sx, sz;
        final char[] data;
        final BitSet written;
        Volume(int ox, int oz, int sx, int sz) { this.ox = ox; this.oz = oz; this.sx = sx; this.sz = sz; data = new char[sx * SY * sz]; written = new BitSet(sx * SY * sz); }
        int idx(int x, int y, int z) { return ((x - ox) * sz + (z - oz)) * SY + y; }
        boolean inside(int x, int y, int z) { return x >= ox && x < ox + sx && z >= oz && z < oz + sz && y >= 0 && y < SY; }
        @Override public int get(int x, int y, int z) { return inside(x, y, z) ? data[idx(x, y, z)] : (Blocks.BEDROCK << 4); }
        @Override public void set(int x, int y, int z, int v) { if (!inside(x, y, z)) return; int i = idx(x, y, z); data[i] = (char) v; written.set(i); }
    }

    /** Synthetic Nether: netherrack with noise caverns, a lava sea below y 32, bedrock floor and roof. */
    static void terrain(Volume v, int salt) {
        for (int x = v.ox; x < v.ox + v.sx; x++) for (int z = v.oz; z < v.oz + v.sz; z++) {
            double h1 = Draw.fbm(x, z, 60, salt) * 18, h2 = Draw.fbm(x, z, 45, salt + 40) * 14;
            int caveLo = 34 + (int) h1, caveHi = 70 + (int) (h2 + Draw.noise(x, z, 20, salt + 3) * 10);
            int lowLo = 12 + (int) (Draw.noise(x, z, 30, salt + 50) * 4), lowHi = lowLo + (int) (Draw.noise(x, z, 25, salt + 60) * 8);
            for (int y = 0; y < SY; y++) {
                int b;
                if (y == 0 || y >= 127 || (y < 5 && Draw.rnd(x, y, z, salt) < 1 - y / 5.0) || (y > 122 && Draw.rnd(x, y, z, salt) < (y - 122) / 5.0)) b = Blocks.BEDROCK << 4;
                else if (y > caveLo && y < caveHi) b = y <= 31 ? Blocks.LAVA << 4 : 0;
                else if (y > lowLo && y < lowHi) b = Blocks.LAVA << 4;          // deep lava pockets (where the Catacombs run)
                else if (y <= 31 && y > 22 && Draw.noise(x, z, 24, salt + 9) > 0.1) b = Blocks.LAVA << 4;
                else b = Blocks.NETHERRACK << 4;
                v.data[v.idx(x, y, z)] = (char) b;
            }
        }
    }

    interface Painter { void draw(Draw d); }

    static final class Run {
        final Volume a; final Template.Placed tiles = new Template.Placed();
        boolean same; long worst, total; int chunks, maxWrites;
        Map<Integer, Integer> forbidden = new TreeMap<>();
        Run(Volume a) { this.a = a; }
    }

    /** Draws a painter chunk by chunk (as population does) and all at once, and compares the two. */
    static Run run(int ox, int oz, int sx, int sz, Painter p) {
        Volume a = new Volume(ox, oz, sx, sz), b = new Volume(ox, oz, sx, sz);
        terrain(a, 5); terrain(b, 5);
        Run r = new Run(a);
        for (int chx = Math.floorDiv(a.ox - 8, 16); chx * 16 + 8 < a.ox + sx; chx++)
            for (int chz = Math.floorDiv(a.oz - 8, 16); chz * 16 + 8 < a.oz + sz; chz++) {
                int cx = chx * 16, cz = chz * 16, bx = cx + 8, bz = cz + 8;
                long t0 = System.nanoTime();
                Template.Placed here = new Template.Placed();
                // tiles only inside the window, as the whole-window draw places them
                Draw d = new Draw(a, cx, cz, cx + 31, cz + 31, Math.max(bx, a.ox), Math.max(bz, a.oz), Math.min(bx + 15, a.ox + sx - 1), Math.min(bz + 15, a.oz + sz - 1), here);
                p.draw(d);
                long dt = System.nanoTime() - t0;
                place(a, here);
                merge(r.tiles, here);
                if (d.writes > 0) { r.chunks++; r.total += dt; r.worst = Math.max(r.worst, dt); r.maxWrites = Math.max(r.maxWrites, d.writes); }
            }
        Template.Placed t2 = new Template.Placed();
        p.draw(new Draw(b, b.ox, b.oz, b.ox + sx - 1, b.oz + sz - 1, t2));
        place(b, t2);
        r.same = java.util.Arrays.equals(a.data, b.data) && r.tiles.chests.size() == t2.chests.size() && r.tiles.points.size() == t2.points.size()
            && r.tiles.spawners.size() == t2.spawners.size() && r.tiles.signs.size() == t2.signs.size();
        if (!r.same) {          // where the two differ, for whoever has to find out why
            int shown = 0;
            for (int i = 0; i < a.data.length && shown < 12; i++) if (a.data[i] != b.data[i]) {
                int y = i % SY, z = (i / SY) % sz + oz, x = i / SY / sz + ox;
                System.out.println("  differs at " + x + "," + y + "," + z + " chunked=" + (a.data[i] >> 4) + ":" + (a.data[i] & 15) + " whole=" + (b.data[i] >> 4) + ":" + (b.data[i] & 15));
                shown++;
            }
            System.out.println("  tiles chunked/whole: chests " + r.tiles.chests.size() + "/" + t2.chests.size() + " points " + r.tiles.points.size() + "/" + t2.points.size()
                + " spawners " + r.tiles.spawners.size() + "/" + t2.spawners.size() + " signs " + r.tiles.signs.size() + "/" + t2.signs.size());
        }
        for (int i = a.written.nextSetBit(0); i >= 0; i = a.written.nextSetBit(i + 1)) {
            int id = a.data[i] >> 4;
            for (int f : FORBIDDEN) if (id == f) r.forbidden.merge(id, 1, Integer::sum);
        }
        return r;
    }

    static void place(Volume v, Template.Placed t) {
        for (int[] c : t.chests) v.set(c[0], c[1], c[2], (Blocks.CHEST << 4) | c[3]);
        for (int[] c : t.spawners) v.set(c[0], c[1], c[2], Blocks.SPAWNER << 4);
        for (int[] c : t.skulls) v.set(c[0], c[1], c[2], Blocks.SKULL << 4 | 1);
        for (int[] c : t.signs) v.set(c[0], c[1], c[2], c[3]);
    }

    static void merge(Template.Placed into, Template.Placed from) {
        MegaPreview.merge(into, from);
        into.signs.addAll(from.signs); into.signLines.addAll(from.signLines);
    }

    static Map<String, Integer> census(List<String> items, boolean prefix) {
        Map<String, Integer> m = new TreeMap<>();
        for (String s : items) m.merge(prefix && s.startsWith("garrison:") ? "garrison" : s.replace("!trap", ""), 1, Integer::sum);
        return m;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        boolean pictures = !out.getName().equals("-");
        if (pictures) out.mkdirs();
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        try (FileInputStream in = new FileInputStream(args[1])) { BlockMap.load(in); }
        Blocks.load();
        boolean ok = true;
        // "-only=<kind id>[,<kind id>...]" checks just those colossi (no Catacombs): for whoever is drawing one up
        String only = System.getProperty("only", "");
        for (Colossi.Kind k : Colossi.Kind.values()) if (only.isEmpty() || java.util.Arrays.asList(only.split(",")).contains(k.id)) ok &= colossus(k, out, pictures);
        if (only.isEmpty()) ok &= depths(out, pictures);
        System.out.println(ok ? "COLOSSI_OK" : "COLOSSI_FAILED");
        if (!ok) System.exit(1);
    }

    // ---- the colossi ------------------------------------------------------------------------------------------------------
    static boolean colossus(Colossi.Kind kind, File out, boolean pictures) throws Exception {
        long seed = 424242L + kind.ordinal() * 7919L;
        int cx = 2000 + kind.ordinal() * 5000 + 7, cz = -3000 + kind.ordinal() * 1300 + 5;
        Colossi.Site s = Colossi.prepared(new Colossi.Site(kind, 0, 0, cx, cz, seed));
        int half = Colossi.REACH + 2;
        Run r = run(cx - half, cz - half, 2 * half + 1, 2 * half + 1, d -> Colossi.draw(s, d));
        Map<String, Integer> tables = census(r.tiles.chestTables, false), points = census(r.tiles.pointKinds, true), spawners = census(r.tiles.spawnerMobs, false);
        boolean vault = false;
        for (String t : tables.keySet()) if (t.endsWith("_vault")) vault = true;
        List<Ordeals.Ordeal> ordeals = Colossi.design(kind).ordeals(s);
        Map<String, Integer> kinds = new TreeMap<>();
        for (Ordeals.Ordeal o : ordeals) kinds.merge(o.type.name().toLowerCase(), 1, Integer::sum);
        boolean titan = kind.titan();
        String[] lords = kind == Colossi.Kind.PYRAMID ? new String[]{"sphinx_sentinel", "vizier_hekkat", "scarab_matriarch", "sunless_pharaoh"}
            : kind == Colossi.Kind.CITADEL ? new String[]{"high_fire_sage", "blazing_admiral", "boiling_warden", "ember_sovereign"} : new String[]{kind.lord};
        boolean arenas = true;
        for (String l : lords) if (points.getOrDefault("lord:" + l, 0) != 1) arenas = false;
        double avg = r.total / 1e6 / Math.max(1, r.chunks), max = r.worst / 1e6;
        boolean seals = titan ? kinds.getOrDefault("bossseal", 0) >= 1 : kinds.getOrDefault("keyseal", 0) == 1 && kinds.getOrDefault("bossseal", 0) == 1;
        boolean puzzles = titan || (kind == Colossi.Kind.PYRAMID ? kinds.getOrDefault("levers", 0) == 1 : kinds.getOrDefault("braziers", 0) == 1);
        // the colossi of 2026-10-04: every hostile creature of the Nether in the drawn garrisons, and a vault behind the Lord's seal
        java.util.Set<String> held = new java.util.TreeSet<>();
        for (String pk : r.tiles.pointKinds) if (pk.startsWith("garrison:")) for (String k : pk.substring(9).replace("!", "").split("[+]")) held.add(k);
        java.util.Set<String> lacking = new java.util.TreeSet<>(java.util.Arrays.asList(ColossusDesign.ROSTER));
        lacking.removeAll(held);
        java.util.Set<String> strangers = new java.util.TreeSet<>(held);
        strangers.removeAll(java.util.Arrays.asList(ColossusDesign.ROSTER));
        strangers.removeAll(Mobs.KINDS.keySet());
        int chestCount = r.tiles.chests.size();
        boolean roster = !titan || lacking.isEmpty();
        boolean ok = r.same && r.forbidden.isEmpty() && vault && arenas && points.getOrDefault("garrison", 0) >= (titan ? 14 : 10) && spawners.size() >= 1
            && r.tiles.signs.size() >= (titan ? 4 : 8) && seals && puzzles && ordeals.size() >= (titan ? 1 : 6) && avg < 30 && max < 160
            && roster && strangers.isEmpty() && (!titan || chestCount >= 16);
        // every ordeal's box lies inside the site's reach
        for (Ordeals.Ordeal o : ordeals) if (o.x1 < s.minX || o.x2 > s.maxX || o.z1 < s.minZ || o.z2 > s.maxZ) ok = false;
        System.out.println("colossus " + kind.id + " site=" + s.x + "," + s.y + "," + s.z + " chunks=" + r.chunks + " avgMs=" + String.format("%.2f", avg) + " maxMs=" + String.format("%.2f", max)
            + " maxWrites=" + r.maxWrites + " deterministic=" + r.same + " forbidden=" + r.forbidden + " chests=" + tables + " points=" + points + " spawners=" + spawners
            + " signs=" + r.tiles.signs.size() + " skulls=" + r.tiles.skulls.size() + " ordeals=" + kinds
            + (titan ? " roster=" + (lacking.isEmpty() ? "complete" : "lacking" + lacking) + " strangers=" + strangers : "") + (ok ? " PASS" : " FAIL"));
        if (pictures) {
            int view = Colossi.REACH;
            iso(r.a, s.x, s.z, view, new File(out, kind.id + "-iso.png"), false, false, 2);
            iso(r.a, s.x, s.z, view, new File(out, kind.id + "-iso-back.png"), true, false, 2);
            iso(r.a, s.x, s.z, view, new File(out, kind.id + "-cut.png"), false, true, 2);
            iso(r.a, s.x, s.z, 70, new File(out, kind.id + "-zoom-cut.png"), false, true, 5);
            top(r.a, s.x, s.z, view, new File(out, kind.id + "-top.png"), 121);
        }
        return ok;
    }

    // ---- the Catacombs ----------------------------------------------------------------------------------------------------
    static boolean depths(File out, boolean pictures) throws Exception {
        long seed = 777001L;
        Depths dp = new Depths(seed, 4000, -2500, History.none(), null);
        boolean ok = true;
        // windows: the Heart, the north Warden's vault, a stretch of maze with a stairwell
        int[][] windows = {{dp.hx, dp.hz, 80}, {dp.hx, dp.hz - Depths.WARD * Depths.P, 44}, {dp.hx + 27 * Depths.P + 3, dp.hz + 25 * Depths.P + 3, 90}};
        String[] names = {"heart", "vault", "maze"};
        for (int w = 0; w < windows.length; w++) {
            int[] c = windows[w];
            Run r = run(c[0] - c[2], c[1] - c[2], 2 * c[2] + 1, 2 * c[2] + 1, dp::draw);
            Map<String, Integer> tables = census(r.tiles.chestTables, false), points = census(r.tiles.pointKinds, true), spawners = census(r.tiles.spawnerMobs, false);
            int lavaTouch = lavaTouching(r.a, dp);
            double avg = r.total / 1e6 / Math.max(1, r.chunks), max = r.worst / 1e6;
            boolean wok = r.same && r.forbidden.isEmpty() && lavaTouch == 0 && avg < 12 && max < 60 && tables.size() > 0;
            if (w == 0) wok &= points.getOrDefault("lord:hollow_king", 0) == 1 && tables.getOrDefault("jaspr:depths/heart", 0) >= 3;
            if (w == 1) wok &= points.getOrDefault("lord:gaoler", 0) == 1 && tables.getOrDefault("jaspr:depths/warden", 0) == 1;
            System.out.println("depths " + names[w] + " at=" + c[0] + "," + c[1] + " chunks=" + r.chunks + " avgMs=" + String.format("%.2f", avg) + " maxMs=" + String.format("%.2f", max)
                + " deterministic=" + r.same + " forbidden=" + r.forbidden + " lavaTouching=" + lavaTouch + " chests=" + tables + " points=" + points + " spawners=" + spawners
                + " signs=" + r.tiles.signs.size() + (wok ? " PASS" : " FAIL"));
            ok &= wok;
            if (pictures) {
                iso(r.a, c[0], c[1], c[2], new File(out, "catacombs-" + names[w] + "-iso.png"), false, true, w == 0 ? 3 : 4, 24);
                top(r.a, c[0], c[1], c[2], new File(out, "catacombs-" + names[w] + "-top.png"), 22);
            }
        }
        // the whole labyrinth: open columns, walked from the Heart's centre
        int half = Depths.HALF, n = 2 * half + 1, ox = dp.hx - half, oz = dp.hz - half;
        BitSet open = new BitSet(n * n), seen = new BitSet(n * n);
        long t0 = System.nanoTime();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) if (dp.top(ox + x, oz + z) >= 0) open.set(x * n + z);
        long mapMs = (System.nanoTime() - t0) / 1000000;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int start = (dp.hx - ox) * n + (dp.hz - oz);
        seen.set(start); q.add(start);
        while (!q.isEmpty()) {
            int k = q.poll(), x = k / n, z = k % n;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= n || nz >= n) continue;
                int nk = nx * n + nz;
                if (open.get(nk) && !seen.get(nk)) { seen.set(nk); q.add(nk); }
            }
        }
        int total = open.cardinality(), reached = seen.cardinality();
        boolean doors = true;
        int[][] cc = {{0, -Depths.WARD}, {Depths.WARD, 0}, {0, Depths.WARD}, {-Depths.WARD, 0}};
        for (int[] c : cc) {
            int vx = dp.hx + c[0] * Depths.P - ox, vz = dp.hz + c[1] * Depths.P - oz;
            if (!seen.get(vx * n + vz)) doors = false;
        }
        Map<String, Integer> kinds = dp.census(Depths.HALF / Depths.P);
        double share = reached / (double) Math.max(1, total);
        boolean mok = doors && share > 0.9 && total > 1_500_000 && kinds.getOrDefault("stair", 0) >= 300 && kinds.getOrDefault("puzzle", 0) >= 100
            && kinds.getOrDefault("trap", 0) >= 3000 && kinds.getOrDefault("hall", 0) >= 200;
        System.out.println("depths map size=" + n + "x" + n + " open=" + total + " reached=" + String.format("%.1f", 100 * share) + "% vaults=" + doors + " mapMs=" + mapMs
            + " cells=" + kinds + (mok ? " PASS" : " FAIL"));
        ok &= mok;
        if (pictures) {
            int S = 2;
            BufferedImage img = new BufferedImage(n / S, n / S, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < n / S; x++) for (int z = 0; z < n / S; z++) {
                int k = (x * S) * n + z * S;
                int c = open.get(k) ? (seen.get(k) ? 0xC8B88A : 0xC04040) : 0x201014;
                img.setRGB(x, z, c);
            }
            javax.imageio.ImageIO.write(img, "png", new File(out, "catacombs-map.png"));
        }
        return ok;
    }

    /** Lava left next to an open column of the Catacombs (it would pour in): 0 when sealed. */
    static int lavaTouching(Volume v, Depths dp) {
        int n = 0;
        for (int x = v.ox + 1; x < v.ox + v.sx - 1; x++) for (int z = v.oz + 1; z < v.oz + v.sz - 1; z++) {
            int t = dp.top(x, z);
            if (t < 0) continue;
            for (int y = Depths.FLOOR + 1; y <= Math.min(t, Depths.TOP); y++) {
                if (v.get(x, y, z) >> 4 != 0) continue;
                int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
                for (int[] o : nb) {
                    int id = v.get(x + o[0], y + o[1], z + o[2]) >> 4;
                    if ((id == Blocks.LAVA || id == Blocks.LAVA_FLOW) && !(dp.top(x + o[0], z + o[2]) >= 0 && y + o[1] <= Depths.FLOOR)) n++;
                }
            }
        }
        return n;
    }

    // ---- pictures -----------------------------------------------------------------------------------------------------------
    static void iso(Volume v, int cx, int cz, int half, File f, boolean back, boolean cut, int S) throws Exception { iso(v, cx, cz, half, f, back, cut, S, SY); }

    static void iso(Volume v, int cx, int cz, int half, File f, boolean back, boolean cut, int S, int yMax) throws Exception {
        int NX = 2 * half, NZ = 2 * half, bx = cx - half, bz = cz - half;
        int w = (NX + NZ) * S + 8, h = (NX + NZ) * S / 2 + SY * S + 8;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x201014));
        g.fillRect(0, 0, w, h);
        for (int sum = 0; sum < NX + NZ - 1; sum++) {
            for (int i = Math.max(0, sum - NZ + 1); i <= Math.min(NX - 1, sum); i++) {
                int j = sum - i;
                int x = back ? bx + NX - 1 - i : bx + i, z = back ? bz + NZ - 1 - j : bz + j;
                int dx = back ? -1 : 1;
                for (int y = 0; y < Math.min(SY, yMax + 1); y++) {
                    if (!shown(v, x, y, z, cut, cx, yMax)) continue;
                    boolean top = !shown(v, x, y + 1, z, cut, cx, yMax), east = !shown(v, x + dx, y, z, cut, cx, yMax), south = !shown(v, x, y, z + dx, cut, cx, yMax);
                    if (!top && !east && !south) continue;
                    int c = MegaPreview.color(v.data[v.idx(x, y, z)]);
                    int px = (i - j) * S + NZ * S + 4, py = (i + j) * S / 2 - y * S + SY * S + 4;
                    if (top) { g.setColor(new java.awt.Color(MegaPreview.shade(c, 1.0))); g.fillPolygon(new int[]{px, px + S, px, px - S}, new int[]{py - S, py - S / 2, py, py - S / 2}, 4); }
                    if (south) { g.setColor(new java.awt.Color(MegaPreview.shade(c, 0.62))); g.fillPolygon(new int[]{px - S, px, px, px - S}, new int[]{py - S / 2, py, py + S, py + S / 2}, 4); }
                    if (east) { g.setColor(new java.awt.Color(MegaPreview.shade(c, 0.8))); g.fillPolygon(new int[]{px, px + S, px + S, px}, new int[]{py, py - S / 2, py + S / 2, py + S}, 4); }
                }
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    static boolean shown(Volume v, int x, int y, int z, boolean cut, int cx, int yMax) {
        if (!v.inside(x, y, z) || y > yMax) return false;
        int i = v.idx(x, y, z);
        if (!v.written.get(i) || v.data[i] == 0) return false;
        return !(cut && x > cx);
    }

    /** Top-down: the highest written block at or below yTop, shaded by height. */
    static void top(Volume v, int cx, int cz, int half, File f, int yTop) throws Exception {
        int S = 2, n = 2 * half;
        BufferedImage img = new BufferedImage(n * S, n * S, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int x = cx - half + i, z = cz - half + j, c = 0x201014;
            for (int y = yTop; y > 0; y--) {
                if (!v.inside(x, y, z)) continue;
                int k = v.idx(x, y, z);
                if (!v.written.get(k) || v.data[k] == 0) continue;
                c = MegaPreview.shade(MegaPreview.color(v.data[k]), 0.5 + 0.5 * Math.min(1, y / (double) Math.max(1, yTop)));
                break;
            }
            for (int a = 0; a < S; a++) for (int b2 = 0; b2 < S; b2++) img.setRGB(i * S + a, j * S + b2, c);
        }
        javax.imageio.ImageIO.write(img, "png", f);
    }
}
