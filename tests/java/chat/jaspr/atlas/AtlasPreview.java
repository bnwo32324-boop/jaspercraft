package chat.jaspr.atlas;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

/**
 * Offline check of the Atlas generator (no server): runs it on in-memory chunks; checks determinism, that the populator's
 * recomputed tiles match the generated blocks, liberation (occupied vs healed drawings), that places draw, and speed;
 * and renders a zone map and top-down views of the key places into the directory given as the first argument.
 */
public final class AtlasPreview {
    static final long SEED = 0x41544C4153L;   // "ATLAS"

    static final class Chunk {
        final char[] blocks = new char[16 * 256 * 16];
        static int i(int x, int y, int z) { return (x * 16 + z) * 256 + y; }
        int id(int x, int y, int z) { return y < 0 || y > 255 ? 0 : blocks[i(x, y, z)] >> 4; }
        int data(int x, int y, int z) { return blocks[i(x, y, z)] & 15; }
        void set(int x, int y, int z, int id, int data) { if (y >= 0 && y < 256 && x >= 0 && x < 16 && z >= 0 && z < 16) blocks[i(x, y, z)] = (char) (id << 4 | data & 15); }

        @SuppressWarnings("deprecation")
        ChunkData proxy() {
            return (ChunkData) Proxy.newProxyInstance(ChunkData.class.getClassLoader(), new Class<?>[] {ChunkData.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "getMaxHeight": return 256;
                    case "setBlock": {
                        int x = (Integer) a[0], y = (Integer) a[1], z = (Integer) a[2];
                        if (a[3] instanceof Material) set(x, y, z, ((Material) a[3]).getId(), 0);
                        else if (a[3] instanceof MaterialData) set(x, y, z, ((MaterialData) a[3]).getItemTypeId(), ((MaterialData) a[3]).getData());
                        else set(x, y, z, (Integer) a[3], a.length > 4 ? ((Number) a[4]).intValue() : 0);
                        return null;
                    }
                    case "setRegion": {
                        int id, data = 0;
                        if (a[6] instanceof Material) id = ((Material) a[6]).getId();
                        else if (a[6] instanceof MaterialData) { id = ((MaterialData) a[6]).getItemTypeId(); data = ((MaterialData) a[6]).getData(); }
                        else { id = (Integer) a[6]; if (a.length > 7) data = ((Number) a[7]).intValue(); }
                        for (int x = Math.max(0, (Integer) a[0]); x < Math.min(16, (Integer) a[3]); x++)
                            for (int y = Math.max(0, (Integer) a[1]); y < Math.min(256, (Integer) a[4]); y++)
                                for (int z = Math.max(0, (Integer) a[2]); z < Math.min(16, (Integer) a[5]); z++) set(x, y, z, id, data);
                        return null;
                    }
                    case "getTypeId": return id((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                    case "getData": return (byte) data((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                    case "getType": return Material.getMaterial(id((Integer) a[0], (Integer) a[1], (Integer) a[2]));
                    case "hashCode": return System.identityHashCode(p);
                    case "equals": return p == a[0];
                    case "toString": return "Chunk";
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
        }
    }

    static BiomeGrid biomes() {
        Biome[] b = new Biome[256];
        return (BiomeGrid) Proxy.newProxyInstance(BiomeGrid.class.getClassLoader(), new Class<?>[] {BiomeGrid.class}, (p, m, a) -> {
            if (m.getName().equals("setBiome")) { b[(Integer) a[0] * 16 + (Integer) a[1]] = (Biome) a[2]; return null; }
            if (m.getName().equals("getBiome")) return b[(Integer) a[0] * 16 + (Integer) a[1]];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            return null;
        });
    }

    static int mask = 0;
    static final Map<Long, Chunk> cache = new LinkedHashMap<Long, Chunk>(4096, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Chunk> e) { return size() > 1400; }
    };
    static long genNanos, genChunks;

    static Chunk chunk(AtlasGenerator gen, int cx, int cz) {
        long k = (long) cx << 32 ^ (cz & 0xffffffffL) ^ ((long) mask << 58);
        Chunk c = cache.get(k);
        if (c != null) return c;
        c = new Chunk();
        long t = System.nanoTime();
        gen.fill(c.proxy(), biomes(), cx, cz, mask);
        genNanos += System.nanoTime() - t;
        genChunks++;
        cache.put(k, c);
        return c;
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * Empty space in a square: the share of columns with nothing built within three blocks (a 7x7 window of bare ground:
     * grass, flowers, dead bushes or ash, with nothing up to four blocks above).
     */
    static double bareness(AtlasGenerator gen, int x0, int z0, int size, int mask) {
        boolean[][] bare = new boolean[size][size];
        for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + size - 1, 16); cx++)
            for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + size - 1, 16); cz++) {
                Drawing d = new Drawing();
                gen.fill(d, d, cx, cz, mask, null);
                int[][] h = gen.heights(cx, cz);
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        int ax = cx * 16 + x - x0, az = cz * 16 + z - z0;
                        if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                        boolean open = true;
                        for (int y = h[x][z] + 1; y <= h[x][z] + 4 && open; y++) {
                            int id = d.id(x, y, z);
                            open = id == 0 || id == Canvas.TALLGRASS || id == Canvas.RED_FLOWER || id == Canvas.YELLOW_FLOWER || id == Canvas.DEADBUSH
                                || id == Canvas.CARPET || id == Canvas.DOUBLE_PLANT;
                        }
                        int top = d.id(x, h[x][z], z);
                        boolean ground = top == Canvas.GRASS || top == Canvas.DIRT || top == Canvas.GRAVEL || top == Canvas.POWDER || top == Canvas.SOUL_SAND
                            || top == Canvas.CLAY || top == Canvas.STONE || top == Canvas.CONCRETE || top == Canvas.COBBLE || top == Canvas.NETHERRACK
                            || top == Canvas.OBSIDIAN || top == Canvas.MOSSY || top == Canvas.MAGMA;
                        bare[ax][az] = open && ground;
                    }
            }
        int empty = 0, all = 0;
        for (int x = 3; x < size - 3; x++)
            for (int z = 3; z < size - 3; z++) {
                all++;
                boolean far = true;
                for (int a = -3; a <= 3 && far; a++) for (int b = -3; b <= 3 && far; b++) far = bare[x + a][z + b];
                if (far) empty++;
            }
        return empty / (double) all;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "atlas-preview");
        out.mkdirs();
        int[] failures = {0};
        AtlasGenerator gen = new AtlasGenerator(SEED, () -> mask, (w, e) -> { failures[0]++; if (failures[0] < 5) e.printStackTrace(); });

        // 1. The zone map (1 px = 4 blocks) and the road network.
        zoneMap(gen, new File(out, "zones.png"));

        // 2. Every place draws; render each.
        for (Realm.Place p : Realm.Place.values()) {
            int r = p == Realm.Place.ANTHRAKION ? 90 : p.radius + 12;
            render(gen, p.x - r, p.z - r, 2 * r, 2 * r, new File(out, "place-" + p.name().toLowerCase() + ".png"));
            int tiles = 0, npcs = 0;
            for (int cx = Math.floorDiv(p.x - p.radius, 16); cx <= Math.floorDiv(p.x + p.radius, 16); cx++)
                for (int cz = Math.floorDiv(p.z - p.radius, 16); cz <= Math.floorDiv(p.z + p.radius, 16); cz++) {
                    int[] t = verifyTiles(gen, cx, cz);
                    tiles += t[0];
                    npcs += t[1];
                }
            System.out.println("place " + p + " tiles=" + tiles + " people=" + npcs);
            check(npcs > 0, p + " has people");
        }

        // 3. Countryside samples: a stretch of each zone.
        int[][] samples = {{-300, -150}, {-150, 150}, {-40, 200}, {150, -250}, {540, -150}, {700, 150}, {760, 60}, {600, 60}};
        String[] names = {"concord", "concord-frontier", "wound", "marches", "weald", "forges", "fallen", "plateau"};
        for (int k = 0; k < samples.length; k++) render(gen, samples[k][0] - 96, samples[k][1] - 96, 192, 192, new File(out, "sample-" + names[k] + ".png"));

        // 4. Density: in a 384-block square of each side, nearly every cell holds something (not bare ground).
        density(gen, -500, -300, "concord");
        density(gen, 120, -300, "marches");
        // Empty space: ground with nothing built within three blocks, in a 192-block square of each countryside (it was
        // 25-52% before the infill layer; now a few percent), and how many people, foes and chests that adds per chunk.
        int[][] bareAt = {{-400, -150}, {-150, 150}, {-40, 200}, {150, -250}, {540, -150}, {700, 150}, {760, 60}};
        String[] bareNames = {"concord", "concord-frontier", "wound", "marches", "weald", "forges", "fallen"};
        for (int k = 0; k < bareAt.length; k++) {
            int people = 0, hostile = 0, chests = 0, chunksHere = 0;
            for (int cx = Math.floorDiv(bareAt[k][0], 16); cx <= Math.floorDiv(bareAt[k][0] + 191, 16); cx++)
                for (int cz = Math.floorDiv(bareAt[k][1], 16); cz <= Math.floorDiv(bareAt[k][1] + 191, 16); cz++) {
                    chunksHere++;
                    for (Canvas.Tile t : AtlasPopulator.tilesOf(gen, cx, cz, mask)) {
                        if (t.kind == Canvas.NPC_TILE) { people++; if (t.what.startsWith("dominion:")) hostile++; }
                        if (t.kind == Canvas.CHEST_TILE) chests++;
                    }
                }
            System.out.println(String.format("entities %s per chunk: people %.2f hostile %.2f chests %.2f", bareNames[k], people / (double) chunksHere, hostile / (double) chunksHere, chests / (double) chunksHere));
            double bare = bareness(gen, bareAt[k][0], bareAt[k][1], 192, mask);
            System.out.println(String.format("empty %s %.1f%%", bareNames[k], bare * 100));
            check(bare < 0.2, bareNames[k] + " is densely filled (empty " + Math.round(bare * 100) + "%)");
        }

        // 5. Determinism and liberation: the same chunk twice; the Marches liberated differ only by healing.
        AtlasGenerator again = new AtlasGenerator(SEED, () -> mask, null);
        Chunk a = chunk(gen, 10, -20), b = new Chunk();
        again.fill(b.proxy(), biomes(), 10, -20, mask);
        check(java.util.Arrays.equals(a.blocks, b.blocks), "generation is deterministic");
        mask = Realm.Province.MARCHES.bit;
        cache.clear();
        render(gen, 160, -160, 192, 192, new File(out, "marches-liberated.png"));
        render(gen, Realm.Place.PYLON.x - 50, -50, 100, 100, new File(out, "pylon-liberated.png"));
        mask = Realm.ALL_LIBERATED;
        cache.clear();
        render(gen, Realm.TX - 90, Realm.TZ - 90, 180, 180, new File(out, "anthrakion-won.png"));
        mask = 0;
        cache.clear();

        // 6. The story's fixed spots: every key figure, boss, mechanism, berth, ward, heliodrome, captive and talker is drawn.
        java.util.Map<String, int[]> spots = new java.util.TreeMap<>();
        for (Realm.Place p : Realm.Place.values()) {
            int r = p == Realm.Place.ANTHRAKION ? Realm.WARD_R + 10 : p.radius + 16;
            for (int cx = Math.floorDiv(p.x - r, 16); cx <= Math.floorDiv(p.x + r, 16); cx++)
                for (int cz = Math.floorDiv(p.z - r, 16); cz <= Math.floorDiv(p.z + r, 16); cz++)
                    for (int m : new int[] {0, Realm.ALL_LIBERATED})
                        for (Canvas.Tile t : AtlasPopulator.tilesOf(gen, cx, cz, m))
                            if (t.kind == Canvas.NPC_TILE && t.what.matches("(key|boss|mech|heliodrome|berth|ward|captive|choir|talker):.*|monument")) spots.putIfAbsent(t.what, new int[] {t.x, t.y, t.z});
        }
        int keys = 0, bosses = 0, mechs = 0, berths = 0, wards = 0, helios = 0, captives = 0, choir = 0, talkers = 0;
        for (String k : spots.keySet()) {
            if (k.startsWith("key:")) keys++; else if (k.startsWith("boss:")) bosses++; else if (k.startsWith("mech:")) mechs++; else if (k.startsWith("berth:")) berths++;
            else if (k.startsWith("ward:")) wards++; else if (k.startsWith("heliodrome:")) helios++; else if (k.startsWith("captive:")) captives++; else if (k.startsWith("choir:")) choir++;
            else if (k.startsWith("talker:")) talkers++;
        }
        System.out.println("spots keys=" + keys + " bosses=" + bosses + " mechanisms=" + mechs + " berths=" + berths + " wards=" + wards + " heliodromes=" + helios
            + " captives=" + captives + " choir=" + choir + " talkers=" + talkers + " monument=" + spots.containsKey("monument"));
        for (String f : Lore.FIGURES.keySet()) if (!spots.containsKey("key:" + f)) System.out.println("MISSING key:" + f);
        for (String c : Lore.CAPTIVES.keySet()) if (!spots.containsKey("captive:" + c)) System.out.println("MISSING captive:" + c);
        check(keys == Lore.FIGURES.size(), "every key figure has a post: " + keys);
        for (Bosses.Boss bb : Bosses.Boss.values()) check(spots.containsKey("boss:" + bb.id), "boss " + bb.id + " has a place");
        for (String m : new String[] {"font:0", "font:1", "font:2", "governor:0", "governor:1", "governor:2", "edict:0", "edict:1", "edict:2"}) check(spots.containsKey("mech:" + m), "mechanism " + m);
        for (String c : Lore.CAPTIVES.keySet()) check(spots.containsKey("captive:" + c), "captive " + c + " is held somewhere");
        check(berths >= Lore.CAPTIVES.size(), "a berth for every captive: " + berths);
        check(wards == 4 && choir == 6 && talkers == 2 && spots.containsKey("monument"), "wards, choir, talkers, monument");
        check(spots.containsKey("heliodrome:pylon") && helios >= 10, "heliodromes incl. the freed Pylon's: " + helios);
        int[] heart = spots.get("boss:pyrarch");
        check(heart[1] == Mechanisms.heartRootY(), "the Pyrarch's throne is at the Heart's root: " + heart[1] + " vs " + Mechanisms.heartRootY());

        // 7. Every written book fits its pages (14 rows of 19 characters), and so does every conversation's book.
        int pages = 0;
        java.util.List<String[]> texts = new java.util.ArrayList<>();
        for (LoreBooks.Book bk : LoreBooks.BOOKS.values()) texts.add(bk.pages);
        texts.add(Lore.OATH); texts.add(Lore.HYMN); texts.add(Lore.COUNTERPOINT); texts.add(Lore.CHARTER); texts.add(Lore.WELCOME);
        for (Lore.Captive c : Lore.CAPTIVES.values()) texts.add(c.testimony);
        for (String[] t : texts)
            for (String page : Items.fit(t)) {
                pages++;
                check(Talk.rows(java.util.Arrays.asList(page.split("\n", -1))) <= Talk.ROWS, "page fits: " + page.substring(0, Math.min(40, page.length())));
            }
        System.out.println("books pages=" + pages + " texts=" + texts.size());

        double ms = genNanos / 1e6 / Math.max(1, genChunks);
        System.out.println(String.format("chunks=%d avgMs=%.2f failures=%d", genChunks, ms, failures[0]));
        check(failures[0] == 0, "no generation failures");
        check(ms < 60, "chunks generate quickly: " + ms + " ms");
        System.out.println("ATLAS_OK");
    }

    /** Tiles recomputed by the populator must be exactly those in the generated chunk. Returns {tiles, people}. */
    static int[] verifyTiles(AtlasGenerator gen, int cx, int cz) {
        Chunk c = chunk(gen, cx, cz);
        List<Canvas.Tile> tiles = AtlasPopulator.tilesOf(gen, cx, cz, mask);
        Set<Integer> planned = new HashSet<>();
        int people = 0;
        for (Canvas.Tile t : tiles) {
            int id = c.id(t.x & 15, t.y, t.z & 15);
            switch (t.kind) {
                case Canvas.CHEST_TILE: check(id == 54, "chest at " + t.x + "," + t.y + "," + t.z + " is " + id); break;
                case Canvas.SIGN_TILE: check(id == 68, "sign at " + t.x + "," + t.y + "," + t.z + " is " + id + " (" + t.what.replace('\n', '/') + ")"); check(t.what.split("\n").length <= 4, "four lines: " + t.what.replace('\n', '/')); break;
                case Canvas.STANDING_SIGN_TILE: check(id == 63, "post at " + t.x + "," + t.y + "," + t.z + " is " + id); break;
                case Canvas.SPAWNER_TILE: check(id == 52, "spawner at " + t.x + "," + t.y + "," + t.z + " is " + id); break;
                case Canvas.BED_TILE: check(id == 26, "bed at " + t.x + "," + t.y + "," + t.z + " is " + id); break;
                case Canvas.POT_TILE: check(id == 140, "pot at " + t.x + "," + t.y + "," + t.z + " is " + id); break;
                case Canvas.NPC_TILE: people++; break;
                default: break;
            }
            for (String line : t.kind == Canvas.SIGN_TILE || t.kind == Canvas.STANDING_SIGN_TILE ? t.what.split("\n") : new String[0])
                check(line.length() <= 15, "sign line too long: '" + line + "'");
            planned.add((t.x & 15) << 12 | (t.z & 15) << 8 | t.y);
        }
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 256; y++) {
                    int id = c.id(x, y, z);
                    if ((id == 54 || id == 52) && !planned.contains(x << 12 | z << 8 | y)) throw new AssertionError("unplanned tile " + id + " at chunk " + cx + "," + cz + " " + x + "," + y + "," + z);
                }
        return new int[] {tiles.size(), people};
    }

    static void density(AtlasGenerator gen, int x0, int z0, String name) {
        int cells = 0, filled = 0, span = 384;
        for (int i = Math.floorDiv(x0, Plans.CELL); i < Math.floorDiv(x0 + span, Plans.CELL); i++)
            for (int j = Math.floorDiv(z0, Plans.CELL); j < Math.floorDiv(z0 + span, Plans.CELL); j++) {
                cells++;
                int x = i * Plans.CELL + 12, z = j * Plans.CELL + 12;
                Plans.Cell c = gen.plans.cell(i, j);
                if (c == null || c.kind != null) filled++;   // null: claimed by a place, site, wall or road
            }
        System.out.println("density " + name + " cells=" + cells + " filled=" + filled);
        check(filled == cells, name + ": every cell holds something");
    }

    static void zoneMap(AtlasGenerator gen, File file) throws Exception {
        int s = 4, n = 2048 / s;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < n; px++)
            for (int pz = 0; pz < n; pz++) {
                int x = -1024 + px * s, z = -1024 + pz * s;
                Realm.Zone zone = Realm.zone(x, z);
                int rgb;
                switch (zone) {
                    case CONCORD: rgb = 0x6fae4a; break;
                    case LINE: rgb = 0xffffff; break;
                    case WOUND: rgb = 0x8a7a5a; break;
                    case MARCHES: rgb = 0x5a5a5a; break;
                    case GATE_ROAD: rgb = 0x4a4a4a; break;
                    case TEETH: rgb = 0x111111; break;
                    case WEALD: rgb = 0x6a7070; break;
                    case FORGES: rgb = 0x7a3a2a; break;
                    case FALLEN: rgb = 0x8a8278; break;
                    case PLATEAU: rgb = 0x2a1010; break;
                    default: rgb = 0x000000;
                }
                if (gen.plans.roadDistance(x, z) < 1) rgb = 0xe8d8a0;
                Realm.Place p = Realm.placeAt(x, z);
                if (p != null) rgb = zone.concord ? 0xf0f0ff : 0xc02020;
                Plans.Site site = gen.plans.siteAt(x, z, 0);
                if (site != null && p == null) rgb = site.kind.concord ? 0xb8e0a0 : 0xa05050;
                img.setRGB(px, pz, rgb);
            }
        ImageIO.write(img, "png", file);
    }

    static void render(AtlasGenerator gen, int x0, int z0, int w, int h, File file) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[][] top = new int[w][h];
        for (int x = 0; x < w; x++)
            for (int z = 0; z < h; z++) {
                int wx = x0 + x, wz = z0 + z;
                Chunk c = chunk(gen, Math.floorDiv(wx, 16), Math.floorDiv(wz, 16));
                int lx = Math.floorMod(wx, 16), lz = Math.floorMod(wz, 16), y = 255;
                while (y > 0 && (c.id(lx, y, lz) == 0 || small(c.id(lx, y, lz)))) y--;
                top[x][z] = y;
                img.setRGB(x, z, color(c.id(lx, y, lz), c.data(lx, y, lz)));
            }
        for (int x = 1; x < w; x++)
            for (int z = 1; z < h; z++) {
                int d = top[x][z] - top[x - 1][z - 1];
                double f = d > 0 ? 1.18 : d < 0 ? 0.8 : 1.0;
                img.setRGB(x, z, scale(img.getRGB(x, z), f * (0.7 + Math.max(0, Math.min(1, (top[x][z] - 50) / 120.0)) * 0.5)));
            }
        BufferedImage big = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w * 2; x++) for (int z = 0; z < h * 2; z++) big.setRGB(x, z, img.getRGB(x / 2, z / 2));
        ImageIO.write(big, "png", file);
    }

    static boolean small(int id) { return id == 31 || id == 37 || id == 38 || id == 175 || id == 32 || id == 111 || id == 171 || id == 50 || id == 51; }

    static int color(int id, int d) {
        switch (id) {
            case 1: return d == 5 || d == 6 ? 0x8a8a8c : d == 4 ? 0xd8d8d8 : 0x7d7d7d;
            case 2: return 0x6fae4a; case 3: return d == 1 ? 0x7a5a3a : 0x866043; case 4: return 0x6e6e6e; case 5: return 0xc8b078;
            case 8: case 9: return 0x3a6ad0; case 10: case 11: return 0xff6a10; case 12: return 0xdbd3a0; case 13: return 0x857f7c;
            case 17: case 162: return 0x6b5230; case 18: case 161: return 0x3a7a2a; case 20: case 95: case 102: case 160: return 0xa0d8f0;
            case 26: return 0xa03030; case 35: return wool(d); case 43: case 44: return d == 6 ? 0x3a1a20 : d == 7 ? 0xf0ece4 : 0xb0b0b0;
            case 45: case 108: return 0x9a4a3a; case 47: return 0x8a6a3a; case 48: return 0x5f7a5a; case 49: return 0x160c24;
            case 52: return 0x200020; case 54: return 0xff9a00; case 59: case 141: case 142: case 207: return 0x9ab040; case 60: return 0x6a4a2a;
            case 63: case 68: return 0x8a6a3a; case 64: return 0x8a6a3a; case 65: return 0xa0824e; case 66: return 0x908070;
            case 85: case 188: case 191: return 0x8a6a3a; case 86: return 0xe08a20; case 87: return 0x7a2a2a; case 88: return 0x4a3a2a; case 89: return 0xf8e090;
            case 90: return 0x7030d0; case 98: return 0x8c8c8c; case 101: return 0x505050; case 103: return 0x7ab030; case 109: return 0x949494;
            case 112: case 114: return 0x2c1418; case 113: return 0x2c1418; case 115: return 0x8a2020; case 116: return 0xc02020; case 117: case 118: return 0x303030;
            case 128: return 0xdbd3a0; case 134: case 135: case 53: case 164: return 0xa0824e; case 139: return 0x6e6e6e; case 140: return 0x8a4a3a;
            case 144: return 0xd8d8d8; case 145: return 0x404040; case 155: return 0xf2eee6; case 156: return 0xf2eee6; case 159: return clay(d);
            case 164 + 1000: return 0; case 167: return 0x909090; case 168: return d == 2 ? 0x24463a : 0x4f8f7e; case 169: return 0xcfe8e0;
            case 170: return 0xc0a020; case 171: return wool(d); case 172: return 0x9a5a40; case 175: return 0x4a8a3a; case 176: case 177: return 0x202020;
            case 198: return 0xf0f0f0; case 208: return 0xa08a50; case 213: return 0xc04a10; case 214: return 0x801010; case 215: return 0x501010;
            case 216: return 0xe0dcc8; case 235: return 0xf0f0f0; case 238: return 0x60b0e0; case 242: return 0x505050; case 244: return 0x3a8a9a; case 246: return 0x2a3aa0;
            case 250: return 0x101010; case 251: return concrete(d); case 252: return concrete(d) + 0x0a0a0a; case 7: return 0x333333; case 81: return 0x2a7a2a;
            case 82: return 0x9fa4b1; case 83: return 0x8ab060; case 21: case 16: case 15: case 14: case 56: case 73: return 0x7d7d7d;
            case 23: return 0x606060; case 25: return 0x6a4a2a; case 58: return 0x8a6a3a; case 61: case 62: return 0x606060;
            default: return 0xff00ff;
        }
    }

    static int wool(int d) { int[] c = {0xe8e8e8, 0xe07a20, 0xb040b0, 0x6a90d0, 0xe0c020, 0x60b020, 0xe07aa0, 0x404040, 0x9a9a9a, 0x2a8a9a, 0x7a30b0, 0x2a3aa0, 0x6a4a2a, 0x4a6a2a, 0xa02a2a, 0x151515}; return c[d & 15]; }
    static int clay(int d) { int[] c = {0xd0b0a0, 0xa05a2a, 0x9a5a6a, 0x707090, 0xb0802a, 0x6a7a3a, 0xa04a4a, 0x3a2a2a, 0x8a6a60, 0x5a5a5a, 0x7a4a5a, 0x4a3a5a, 0x4a3020, 0x4a5028, 0x8a3a2a, 0x252020}; return c[d & 15]; }
    static int concrete(int d) { int[] c = {0xcfd5d6, 0xe06100, 0xa9309f, 0x2489c7, 0xf1af15, 0x5ea918, 0xd6658f, 0x373a3e, 0x7d7d73, 0x157788, 0x64209c, 0x2d2f8f, 0x603c20, 0x495b24, 0x8e2121, 0x080a0f}; return c[d & 15]; }

    static int scale(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return r << 16 | g << 8 | b;
    }
}
