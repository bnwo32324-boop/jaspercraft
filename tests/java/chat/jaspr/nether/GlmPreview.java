package chat.jaspr.nether;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Offline check of the owner's GLM Nether structures (no server).
 * <ul>
 *   <li>planning over a 12,000-block square: the three grids never overlap each other or a mega structure's reach,
 *       neighbouring lord cells never share a Lord, every build and every Lord appears, every floor fits the Nether;</li>
 *   <li>drawing, for every build (or the named ones): chunk by chunk on synthetic Nether terrain, exactly as population
 *       clips it, must equal drawing it at once; no forbidden or valuable block is ever written; every build is stocked
 *       (loot chests, spawners, garrisons, and its Lord's arena for the ten strongholds) and cheap to draw.</li>
 * </ul>
 * Args: outDir|- resourcesDir [keys...] (outDir gets isometric PNGs). Prints GLM_OK when all pass.
 */
public final class GlmPreview {
    static final int[] FORBIDDEN = MegaPreview.FORBIDDEN;

    static final class Vol implements Canvas {
        final int ox, oz, sx, sz, sy = 128;
        final char[] data; final BitSet written;
        Vol(int ox, int oz, int sx, int sz) {
            this.ox = ox; this.oz = oz; this.sx = sx; this.sz = sz;
            data = new char[sx * sy * sz]; written = new BitSet(data.length);
        }
        boolean inside(int x, int y, int z) { return x >= ox && x < ox + sx && z >= oz && z < oz + sz && y >= 0 && y < sy; }
        int idx(int x, int y, int z) { return ((x - ox) * sz + (z - oz)) * sy + y; }
        @Override public int get(int x, int y, int z) { return inside(x, y, z) ? data[idx(x, y, z)] : (Blocks.BEDROCK << 4); }
        @Override public void set(int x, int y, int z, int v) {
            if (!inside(x, y, z)) return;
            int i = idx(x, y, z);
            data[i] = (char) v;
            written.set(i);
        }
    }

    static void terrain(Vol v, int salt) {
        for (int x = v.ox; x < v.ox + v.sx; x++) for (int z = v.oz; z < v.oz + v.sz; z++) {
            double h1 = Draw.fbm(x, z, 60, salt) * 18, h2 = Draw.fbm(x, z, 45, salt + 40) * 14;
            int caveLo = 34 + (int) h1, caveHi = 70 + (int) (h2 + Draw.noise(x, z, 20, salt + 3) * 10);
            int cave2Lo = 84 + (int) (Draw.noise(x, z, 30, salt + 50) * 6), cave2Hi = cave2Lo + 6 + (int) (Draw.noise(x, z, 25, salt + 60) * 8);
            for (int y = 0; y < v.sy; y++) {
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
        boolean pictures = !out.getName().equals("-");
        if (pictures) out.mkdirs();
        final File res = new File(args[1]);
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        try (FileInputStream in = new FileInputStream(new File(res, "blocks.tsv"))) { BlockMap.load(in); }
        Blocks.load();
        java.util.function.Function<String, InputStream> resources = n -> {
            try { return new FileInputStream(new File(res, n)); } catch (IOException e) { return null; }
        };
        boolean ok = plan(resources);
        GlmSites g = new GlmSites(4242L, (x, z) -> Biomes.Nex.HELL, null, null, resources);
        List<String> keys = new ArrayList<>();
        for (int i = 2; i < args.length; i++) keys.add(args[i]);
        if (keys.isEmpty()) keys.addAll(g.byKey.keySet());
        Biomes.Nex[] regions = Biomes.Nex.values();
        int n = 0;
        for (String k : keys) {
            GlmSites.Entry e = g.entry(k);
            if (e == null) { System.out.println("glm " + k + " unknown FAIL"); ok = false; continue; }
            ok &= draw(g, e, regions[n++ % regions.length], out, pictures);
        }
        System.out.println(ok ? "GLM_OK" : "GLM_FAILED");
        if (!ok) System.exit(1);
    }

    // ---- planning ----------------------------------------------------------------------------------------------------
    static boolean plan(java.util.function.Function<String, InputStream> resources) throws IOException {
        long seed = 918273645L;
        Biomes biomes = new Biomes(seed);
        Mega[] holder = new Mega[1];
        GlmSites g = new GlmSites(seed, biomes::nex, null, (x0, z0, x1, z1) -> !holder[0].touching(x0, z0, x1, z1).isEmpty(), resources);
        Mega mega = new Mega(seed, biomes::nex, (x0, z0, x1, z1) -> !g.touching(GlmSites.Tier.LORD, x0, z0, x1, z1).isEmpty()
            || !g.touching(GlmSites.Tier.GREAT, x0, z0, x1, z1).isEmpty());
        holder[0] = mega;
        int R = 6000;
        List<GlmSites.Site> all = new ArrayList<>();
        Map<String, Integer> tiers = new TreeMap<>(), builds = new TreeMap<>(), lords = new TreeMap<>();
        int badFloor = 0, lordRepeat = 0;
        for (GlmSites.Tier t : GlmSites.Tier.values()) {
            int c0 = Math.floorDiv(-R, t.cell), c1 = Math.floorDiv(R, t.cell);
            for (int cx = c0; cx <= c1; cx++) for (int cz = c0; cz <= c1; cz++) {
                GlmSites.Site s = g.site(t, cx, cz);
                if (s == null) continue;
                all.add(s);
                tiers.merge(t.name(), 1, Integer::sum);
                builds.merge(s.e.key, 1, Integer::sum);
                if (s.e.lord != null) lords.merge(s.e.lord, 1, Integer::sum);
                int[] f = GlmSites.floorRange(s.e);
                if (s.floor < 4 || s.base() < 4 || (s.floor + s.e.maxCeil > 121 && s.floor > f[0]) || s.floor < f[0]) badFloor++;
                if (t == GlmSites.Tier.LORD) {
                    GlmSites.Site east = g.site(t, cx + 1, cz), south = g.site(t, cx, cz + 1);
                    if (east != null && east.e.lord.equals(s.e.lord)) lordRepeat++;
                    if (south != null && south.e.lord.equals(s.e.lord)) lordRepeat++;
                }
            }
        }
        int overlaps = 0, megaHits = 0;
        // sites sorted by x for a sweep
        all.sort((a, b) -> Integer.compare(a.minX, b.minX));
        for (int i = 0; i < all.size(); i++) {
            GlmSites.Site a = all.get(i);
            for (int j = i + 1; j < all.size() && all.get(j).minX <= a.maxX; j++) {
                GlmSites.Site b = all.get(j);
                if (b.maxZ >= a.minZ && b.minZ <= a.maxZ) overlaps++;
            }
            if (!mega.touching(a.minX, a.minZ, a.maxX, a.maxZ).isEmpty()) megaHits++;
        }
        int missing = 0, disabledPlaced = 0;
        StringBuilder miss = new StringBuilder();
        for (String k : g.byKey.keySet()) {
            if (g.generates(k) && !builds.containsKey(k)) { missing++; miss.append(' ').append(k); }
            if (!g.generates(k) && builds.containsKey(k)) disabledPlaced++;
        }
        int megas = 0;
        for (int cx = Math.floorDiv(-R, Mega.CELL); cx <= Math.floorDiv(R, Mega.CELL); cx++)
            for (int cz = Math.floorDiv(-R, Mega.CELL); cz <= Math.floorDiv(R, Mega.CELL); cz++) if (mega.site(cx, cz) != null) megas++;
        boolean ok = overlaps == 0 && megaHits == 0 && lordRepeat == 0 && badFloor == 0 && missing == 0 && disabledPlaced == 0 && lords.size() == Lords.DEFS.size();
        System.out.println("glm plan sites=" + all.size() + " tiers=" + tiers + " lords=" + lords + " distinctBuilds=" + builds.size() + "/" + g.activeSize()
            + " catalog=" + g.size() + " disabledPlaced=" + disabledPlaced
            + " missing=" + missing + miss + " megas=" + megas + " overlaps=" + overlaps + " megaHits=" + megaHits + " lordRepeat=" + lordRepeat + " badFloor=" + badFloor + (ok ? " PASS" : " FAIL"));
        return ok;
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------
    static boolean draw(GlmSites g, GlmSites.Entry e, Biomes.Nex region, File out, boolean pictures) {
        long seed = 77L + e.key.hashCode();
        Random r = new Random(seed);
        int rot = Math.floorMod(e.key.hashCode(), 4);
        int w = (rot & 1) == 0 ? e.cavW : e.cavD, d = (rot & 1) == 0 ? e.cavD : e.cavW;
        int minX = 1000 + (e.key.hashCode() & 511), minZ = -3000 + ((e.key.hashCode() >> 9) & 511);
        GlmSites.Site s = new GlmSites.Site(e, GlmSites.Tier.valueOf(e.tier.name()), 0, 0, rot, minX, minZ, GlmSites.floor(e, r), region, seed);
        int pad = 24;
        Vol a = new Vol(minX - pad, minZ - pad, w + 2 * pad, d + 2 * pad), b = new Vol(minX - pad, minZ - pad, w + 2 * pad, d + 2 * pad);
        terrain(a, 5);
        terrain(b, 5);
        Template.Placed tiles = new Template.Placed();
        long worst = 0, total = 0; int chunks = 0;
        g.build(e.key);     // loading the build file is not drawing: time the chunks only
        for (int chx = Math.floorDiv(a.ox - 8, 16); chx * 16 + 8 < a.ox + a.sx; chx++)
            for (int chz = Math.floorDiv(a.oz - 8, 16); chz * 16 + 8 < a.oz + a.sz; chz++) {
                int ox = chx * 16, oz = chz * 16, bx = ox + 8, bz = oz + 8;
                Template.Placed here = new Template.Placed();
                Draw dr = new Draw(a, ox, oz, ox + 31, oz + 31, bx, bz, bx + 15, bz + 15, here);
                long t0 = System.nanoTime();
                g.draw(s, dr);
                long dt = System.nanoTime() - t0;
                place(a, here);
                merge(tiles, here);
                if (dr.writes > 0) { chunks++; total += dt; worst = Math.max(worst, dt); }
            }
        Template.Placed tiles2 = new Template.Placed();
        g.draw(s, new Draw(b, b.ox, b.oz, b.ox + b.sx - 1, b.oz + b.sz - 1, tiles2));
        place(b, tiles2);
        boolean same = java.util.Arrays.equals(a.data, b.data) && tiles.late.size() == tiles2.late.size() && tiles.points.size() == tiles2.points.size()
            && tiles.spawners.size() == tiles2.spawners.size();
        Map<Integer, Integer> forbidden = new TreeMap<>();
        for (int i = a.written.nextSetBit(0); i >= 0; i = a.written.nextSetBit(i + 1)) {
            int id = a.data[i] >> 4;
            for (int f : FORBIDDEN) if (id == f) forbidden.merge(id, 1, Integer::sum);
        }
        for (GlmSites.Pending p : tiles.late) for (int f : FORBIDDEN) if ((p.block >> 4) == f) forbidden.merge(f, 1, Integer::sum);
        Map<String, Integer> chests = new TreeMap<>(), points = new TreeMap<>(), spawners = new TreeMap<>(), kinds = new TreeMap<>();
        for (GlmSites.Pending p : tiles.late) {
            kinds.merge(p.t.type == GlmBuild.T_CHEST ? "chest" : p.t.type == GlmBuild.T_SIGN ? "sign" : p.t.type == GlmBuild.T_BANNER ? "banner"
                : p.t.type == GlmBuild.T_SKULL ? "skull" : "pot", 1, Integer::sum);
            if (p.t.type == GlmBuild.T_CHEST && !p.table.isEmpty()) chests.merge(p.table.split(":")[1], 1, Integer::sum);
        }
        for (String p : tiles.pointKinds) points.merge(p.startsWith("garrison:") ? "garrison" : p, 1, Integer::sum);
        for (String m : tiles.spawnerMobs) spawners.merge(m, 1, Integer::sum);
        int loot = 0; for (int c : chests.values()) loot += c;
        int unknownKinds = 0;
        for (String m : tiles.spawnerMobs) if (!Mobs.KINDS.containsKey(m) && !m.equals("wither_skeleton") && !m.equals("magma_cube") && !m.equals("blaze") && !m.equals("zombie_pigman")) unknownKinds++;
        for (String p : tiles.pointKinds) if (p.startsWith("garrison:")) for (String k : p.substring(9).replace("!", "").split("\\+"))
            if (!Mobs.KINDS.containsKey(k) && !k.equals("wither_skeleton") && !k.equals("magma_cube") && !k.equals("blaze") && !k.equals("zombie_pigman")) unknownKinds++;
        boolean lordOk = e.lord == null || points.getOrDefault("lord:" + e.lord, 0) == 1;
        boolean ok = same && forbidden.isEmpty() && loot >= 3 && spawners.values().stream().mapToInt(Integer::intValue).sum() >= 1
            && points.getOrDefault("garrison", 0) >= 1 && lordOk && unknownKinds == 0 && worst / 1e6 < 250 && total / 1e6 / Math.max(1, chunks) < 30;
        System.out.println("glm " + e.key + " " + e.tier + " " + e.theme + " region=" + region + " rot=" + rot + " floor=" + s.floor + " chunks=" + chunks
            + " avgMs=" + String.format("%.2f", total / 1e6 / Math.max(1, chunks)) + " maxMs=" + String.format("%.2f", worst / 1e6) + " deterministic=" + same
            + " forbidden=" + forbidden + " loot=" + chests + " tiles=" + kinds + " spawners=" + spawners.values().stream().mapToInt(Integer::intValue).sum()
            + " points=" + points + " unknownKinds=" + unknownKinds + (ok ? " PASS" : " FAIL"));
        if (pictures) render(a, s, new File(out, e.key + ".png"));
        return ok;
    }

    static void place(Vol v, Template.Placed t) {
        for (GlmSites.Pending p : t.late) v.set(p.x, p.y, p.z, p.block);
        for (int[] c : t.spawners) v.set(c[0], c[1], c[2], Blocks.SPAWNER << 4);
    }

    static void merge(Template.Placed into, Template.Placed from) {
        into.late.addAll(from.late);
        into.spawners.addAll(from.spawners); into.spawnerMobs.addAll(from.spawnerMobs);
        into.points.addAll(from.points); into.pointKinds.addAll(from.pointKinds);
    }

    /** Isometric picture of what the site wrote (its cavern shell, ground and build). */
    static void render(Vol v, GlmSites.Site s, File file) {
        int S = Math.max(1, Math.min(4, 1400 / (v.sx + v.sz)));
        int W = (v.sx + v.sz) * S + 4, H = (v.sx + v.sz) * S / 2 + v.sy * S + 4;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < W * H; i++) img.setRGB(i % W, i / W, 0x180E10);
        int rock = GlmSites.mats(s.region).rock;
        for (int sum = 0; sum < v.sx + v.sz - 1; sum++)
            for (int x = Math.max(0, sum - v.sz + 1); x <= Math.min(v.sx - 1, sum); x++) {
                int z = sum - x;
                for (int y = 0; y < v.sy; y++) {
                    int i = ((x) * v.sz + z) * v.sy + y;
                    if (!v.written.get(i) || v.data[i] == 0) continue;
                    // the cavern's shell (region rock above the floor) is left out so the build shows
                    if (y > s.floor && v.data[i] == rock) continue;
                    int c = MegaPreview.color(v.data[i]);
                    int px = (x - z) * S + v.sz * S + 2, py = (x + z) * S / 2 - y * S + v.sy * S + 2;
                    for (int dx = -S; dx < S; dx++) for (int dy = -S; dy < S; dy++) {
                        int X = px + dx, Y = py + dy;
                        if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
                        img.setRGB(X, Y, dy < 0 ? c : MegaPreview.shade(c, dx < 0 ? 0.62 : 0.8));
                    }
                }
            }
        try { javax.imageio.ImageIO.write(img, "png", file); } catch (IOException ignored) { }
    }
}
