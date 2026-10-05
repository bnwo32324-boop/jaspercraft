package chat.jaspr.ruins;

import chat.jaspr.lostcities.CityApi;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
 * Offline check of the Ancient Ruins generator (no server): runs the real generator on in-memory chunks, checks it
 * is deterministic and that the populator's recomputed chests/spawners match the generated blocks, exercises the
 * weathering hook and portal-frame detection, times generation, and renders top-down previews of an old city and one
 * site of every kind into the directory given as the first argument.
 */
public final class RuinsPreview {
    static final long SEED = 0x1234ABCDL ^ RuinsPlugin.SALT;

    /** A 16x256x16 chunk in memory, behind Bukkit's ChunkData interface. */
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
                    case "getTypeAndData": return new MaterialData(id((Integer) a[0], (Integer) a[1], (Integer) a[2]), (byte) data((Integer) a[0], (Integer) a[1], (Integer) a[2]));
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

    static final Map<Long, Chunk> cache = new HashMap<>();
    static final int[] SIGNS = {0}, TRAPS = {0};
    static long genNanos, genChunks;

    static Chunk chunk(RuinsGenerator gen, int cx, int cz) {
        long k = (long) cx << 32 ^ (cz & 0xffffffffL);
        Chunk c = cache.get(k);
        if (c != null) return c;
        c = new Chunk();
        long t = System.nanoTime();
        gen.fill(c.proxy(), biomes(), cx, cz);
        genNanos += System.nanoTime() - t;
        genChunks++;
        cache.put(k, c);
        return c;
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "ruins-preview");
        out.mkdirs();
        // Offline the Lost Cities plan is its pure region geometry (plus the one-chunk ring).
        Plans.Reserved reserved = (x, z, w, d) -> {
            for (int cx = Math.floorDiv(x, 16) - 1; cx <= Math.floorDiv(x + w - 1, 16) + 1; cx++)
                for (int cz = Math.floorDiv(z, 16) - 1; cz <= Math.floorDiv(z + d - 1, 16) + 1; cz++)
                    if (CityApi.cityRegion(SEED, cx, cz)) return true;
            return false;
        };
        int[] failures = {0};
        RuinsGenerator gen = new RuinsGenerator(SEED, reserved, (w, e) -> { failures[0]++; e.printStackTrace(); });

        // 1. An old city and one site of every kind near the origin.
        Plans.City city = null;
        for (int r = 0; r <= 8 && city == null; r++)
            for (int i = -r; i <= r && city == null; i++)
                for (int j = -r; j <= r && city == null; j++) if (Math.max(Math.abs(i), Math.abs(j)) == r) city = gen.plans.city(i, j);
        check(city != null, "an old city within 8 cells");
        Map<Plans.Kind, Plans.Site> sites = new HashMap<>();
        for (int r = 0; r <= 40 && sites.size() < Plans.Kind.values().length; r++)
            for (int i = -r; i <= r; i++)
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    Plans.Site s = gen.plans.site(i, j);
                    if (s != null) sites.putIfAbsent(s.kind, s);
                }
        check(sites.size() == Plans.Kind.values().length, "every kind of site appears: " + sites.keySet());
        // Epoch 5 (owner 2026-10-04: "more dense with dungeons and structures. make new ones. 2x it"): everything in a
        // 2048 x 2048 block square. Epoch 4 measured, with exactly these definitions, 137.3 ruins per km2 (70.6 of them
        // dungeons: the five Warden arenas, Ziggurat, Crypt, Bastion, Labyrinth, Ossuary Temple, Temple of the Deep) and
        // 1712.8 catacomb rooms; no lesser ruins. The great ruins can only pack about 1.5x as close on this land; the lesser
        // ruins of the field make up the rest.
        int R = 1024, ruins = 0, dungeonRuins = 0, wetRuins = 0, lesser = 0, lesserDungeons = 0, rooms1 = 0, rooms2 = 0, shafts = 0;
        java.util.List<Plans.Site> square = new java.util.ArrayList<>();
        for (int i = Math.floorDiv(-R, Plans.SITE_GRID); i < Math.floorDiv(R, Plans.SITE_GRID); i++)
            for (int j = Math.floorDiv(-R, Plans.SITE_GRID); j < Math.floorDiv(R, Plans.SITE_GRID); j++) {
                Plans.Site s = gen.plans.site(i, j);
                if (s == null || s.x < -R || s.x >= R || s.z < -R || s.z >= R) continue;
                square.add(s);
                ruins++;
                if (s.kind.dungeon) dungeonRuins++;
                if (s.kind.wet) wetRuins++;
            }
        java.util.Set<Plans.Filler> lesserKinds = java.util.EnumSet.noneOf(Plans.Filler.class);
        for (int i = Math.floorDiv(-R, Plans.CELL); i < Math.floorDiv(R, Plans.CELL); i++)
            for (int j = Math.floorDiv(-R, Plans.CELL); j < Math.floorDiv(R, Plans.CELL); j++) {
                Plans.Cell cell = gen.plans.cell(i, j);
                if (cell == null || cell.type == null || !cell.type.lesser()) continue;
                lesser++;
                lesserKinds.add(cell.type);
                if (cell.type != Plans.Filler.HUT && cell.type != Plans.Filler.OFFERING_STONE) lesserDungeons++;
            }
        for (int i = -R / 16; i < R / 16; i++)
            for (int j = -R / 16; j < R / 16; j++) {
                if (Catacombs.node(gen.plans, i, j) >= Catacombs.CRYPT) rooms1++;
                if (Catacombs.deepNode(gen.plans, i, j) >= Catacombs.CRYPT) rooms2++;
                if (Catacombs.descent(gen.plans, i, j)) shafts++;
            }
        // Epoch 6: the great structures (each counted once, though each is a whole complex) and the land they take.
        int greats = 0;
        for (int i = Math.floorDiv(-R, Plans.GREAT_GRID); i < Math.floorDiv(R, Plans.GREAT_GRID); i++)
            for (int j = Math.floorDiv(-R, Plans.GREAT_GRID); j < Math.floorDiv(R, Plans.GREAT_GRID); j++) {
                Plans.GreatSite g = gen.plans.great(i, j);
                if (g != null && g.x >= -R && g.x < R && g.z >= -R && g.z < R) greats++;
            }
        double km2 = 4 * R * (double) R / 1e6;
        int clashes = 0;
        for (int a = 0; a < square.size(); a++) for (int b = a + 1; b < square.size(); b++) if (Plans.clash(square.get(a), square.get(b))) clashes++;
        System.out.println(String.format(java.util.Locale.ROOT, "SITE_DENSITY grid=%d ruins=%.1f dungeonRuins=%.1f wetRuins=%.1f lesser=%.1f lesserDungeons=%.1f greats=%.1f"
            + " structures=%.1f (x%.2f) dungeons=%.1f (x%.2f) catacombRooms=%.1f+%.1f (x%.2f) shafts=%.1f clashes=%d", Plans.SITE_GRID, ruins / km2,
            dungeonRuins / km2, wetRuins / km2, lesser / km2, lesserDungeons / km2, greats / km2, (ruins + lesser + greats) / km2, (ruins + lesser + greats) / km2 / 137.3,
            (dungeonRuins + lesserDungeons + greats) / km2, (dungeonRuins + lesserDungeons + greats) / km2 / 70.6, rooms1 / km2, rooms2 / km2, (rooms1 + rooms2) / km2 / 1712.8, shafts / km2, clashes));
        check(clashes == 0, "no two ruins overlap: " + clashes);
        check(greats / km2 > 8, "the great structures (epoch 6): " + greats / km2 + " per km2");
        check((ruins + greats) / km2 > 1.2 * 137.3, "the ruins and great structures together closer than epoch 4's ruins: " + (ruins + greats) / km2);
        check((ruins + lesser + greats) / km2 > 2.0 * 137.3, "ruins, lesser ruins and great structures twice epoch 4's ruins: " + (ruins + lesser + greats) / km2);
        check((dungeonRuins + lesserDungeons + greats) / km2 > 2.0 * 70.6, "dungeons twice epoch 4's: " + (dungeonRuins + lesserDungeons + greats) / km2);
        check(rooms1 + rooms2 > 2.0 * 1712.8 * km2, "catacomb rooms twice epoch 4's: " + (rooms1 + rooms2) / km2);
        check(wetRuins > 10 && shafts > 100, "drowned ruins and shafts to the deep catacombs: " + wetRuins + " " + shafts);
        check(lesserKinds.size() == 6, "every lesser ruin appears: " + lesserKinds);

        // 2. Render and verify the city.
        int reach = city.half + 24;
        render(gen, city.x - reach, city.z - reach, 2 * reach, 2 * reach, new File(out, "city-" + city.name + ".png"));
        int chests = 0, spawners = 0;
        for (int cx = Math.floorDiv(city.x - reach, 16); cx <= Math.floorDiv(city.x + reach, 16); cx++)
            for (int cz = Math.floorDiv(city.z - reach, 16); cz <= Math.floorDiv(city.z + reach, 16); cz++) {
                int[] n = verifyTiles(gen, cx, cz);
                chests += n[0];
                spawners += n[1];
            }
        check(chests > 3, "the city has chests: " + chests);

        // 3. Every site kind: a preview tile each, tiles verified.
        int tile = 64, cols = 5;
        BufferedImage sheet = new BufferedImage(cols * tile * 2, (Plans.Kind.values().length + cols - 1) / cols * tile * 2, BufferedImage.TYPE_INT_RGB);
        int n = 0;
        for (Plans.Kind k : Plans.Kind.values()) {
            Plans.Site s = sites.get(k);
            BufferedImage img = image(gen, s.x - tile / 2, s.z - tile / 2, tile, tile);
            for (int x = 0; x < tile * 2; x++)
                for (int z = 0; z < tile * 2; z++) sheet.setRGB((n % cols) * tile * 2 + x, (n / cols) * tile * 2 + z, img.getRGB(x / 2, z / 2));
            for (int cx = Math.floorDiv(s.x - k.radius, 16); cx <= Math.floorDiv(s.x + k.radius, 16); cx++)
                for (int cz = Math.floorDiv(s.z - k.radius, 16); cz <= Math.floorDiv(s.z + k.radius, 16); cz++) {
                    int[] t = verifyTiles(gen, cx, cz);
                    chests += t[0];
                    spawners += t[1];
                }
            System.out.println("site " + k + " at " + s.x + "," + s.z + " base=" + s.base + " rot=" + s.rot + " name=" + s.name);
            n++;
        }
        ImageIO.write(sheet, "png", new File(out, "sites.png"));

        // 3b. The Great Door citadel.
        Plans.Door door = gen.plans.door();
        render(gen, door.x - 56, door.z - 56, 112, 112, new File(out, "door.png"));
        for (int cx = Math.floorDiv(door.x - 48, 16); cx <= Math.floorDiv(door.x + 48, 16); cx++)
            for (int cz = Math.floorDiv(door.z - 48, 16); cz <= Math.floorDiv(door.z + 52, 16); cz++) { int[] t = verifyTiles(gen, cx, cz); chests += t[0]; }
        Chunk doorChunk = chunk(gen, Math.floorDiv(door.x, 16), Math.floorDiv(door.z, 16));
        check(doorChunk.id(Math.floorMod(door.x, 16), door.base + 5, Math.floorMod(door.z, 16)) == 49, "the Door's obsidian leaves");
        check(Cult.doorBlock(door, door.x, door.base + 5, door.z) && !Cult.doorBlock(door, door.x, door.base + 25, door.z), "door leaf test");
        System.out.println("door at " + door.x + "," + door.z + " base=" + door.base);

        // 3c. The ruin field: no trees or flowers, a little grass, and nearly every column built on.
        int span = 384, ox = -192, oz = -192, columns = 0, built = 0, grassTops = 0, forbidden = 0, monuments = 0, cellsSeen = 0;
        java.util.Set<Integer> banned = new java.util.HashSet<>(Arrays.asList(17, 18, 161, 162, 6, 37, 38, 175, 111, 39, 40, 83, 81, 99, 100));
        java.util.Set<Integer> made = new java.util.HashSet<>(Arrays.asList(98, 168, 49, 43, 44, 109, 139, 159, 216, 144, 169, 101, 85, 54, 52, 68, 118, 165, 30, 67));
        for (int x = ox; x < ox + span; x++)
            for (int z = oz; z < oz + span; z++) {
                Chunk c = chunk(gen, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16), h = gen.plans.surface(x, z), y = 255;
                while (y > 0 && c.id(lx, y, lz) == 0) y--;
                for (int yy = 1; yy <= y; yy++) if (banned.contains(c.id(lx, yy, lz))) forbidden++;
                int top = c.id(lx, y, lz);
                columns++;
                if (top == 2 || top == 31 && c.id(lx, y - 1, lz) == 2) grassTops++;
                if (h <= Plans.SEA || y > h || made.contains(top)) built++;
            }
        for (int i = Math.floorDiv(ox, Plans.CELL); i < Math.floorDiv(ox + span, Plans.CELL); i++)
            for (int j = Math.floorDiv(oz, Plans.CELL); j < Math.floorDiv(oz + span, Plans.CELL); j++) {
                Plans.Cell cell = gen.plans.cell(i, j);
                if (cell == null) continue;
                cellsSeen++;
                if (cell.type != null) monuments++;
            }
        render(gen, -96, -96, 192, 192, new File(out, "field.png"));
        double coverage = built / (double) columns, grass = grassTops / (double) columns;
        System.out.println(String.format("field coverage=%.3f grass=%.3f forbidden=%d monumentCells=%d/%d", coverage, grass, forbidden, monuments, cellsSeen));
        check(forbidden == 0, "no trees, leaves, flowers or mushrooms anywhere: " + forbidden);
        check(grass < 0.06, "only a little grass: " + grass);
        check(coverage > 0.6, "the land is built over: " + coverage);

        // 3d. The catacombs: vaulted rooms hollowed out under the ruins at the district depth, reached by gates.
        int rooms = 0, hollow = 0, gates = 0;
        for (int i = -12; i <= 12; i++)
            for (int j = -12; j <= 12; j++) {
                int type = Catacombs.node(gen.plans, i, j);
                if (type == Catacombs.NONE) continue;
                rooms++;
                int x = i * 16 + 8, z = j * 16 + 8, depth = gen.plans.depth(x, z);
                Chunk c = chunk(gen, i, j);
                if (c.id(8, depth + 3, 8) == 0 || c.id(8, depth + 3, 8) == 8 || c.id(8, depth + 3, 8) == 30) hollow++;
                verifyTiles(gen, i, j);
            }
        for (int i = Math.floorDiv(-192, Plans.CELL); i < Math.floorDiv(192, Plans.CELL); i++)
            for (int j = Math.floorDiv(-192, Plans.CELL); j < Math.floorDiv(192, Plans.CELL); j++) {
                Plans.Cell cell = gen.plans.cell(i, j);
                if (cell != null && cell.type == Plans.Filler.CATACOMB_GATE) gates++;
            }
        // The deep catacombs (epoch 5): hollow rooms 14 blocks under the first level, and ladders down to them.
        int deepRooms = 0, deepHollow = 0, ladders = 0, descents = 0;
        for (int i = -12; i <= 12; i++)
            for (int j = -12; j <= 12; j++) {
                int x = i * 16 + 8, z = j * 16 + 8, deep = Catacombs.deepDepth(gen.plans, x, z);
                if (deep < 0 || Catacombs.deepNode(gen.plans, i, j) == Catacombs.NONE) continue;
                deepRooms++;
                Chunk c = chunk(gen, i, j);
                int id = c.id(8, deep + 3, 8);
                if (id == 0 || id == 8 || id == 9 || id == 30) deepHollow++;
                if (Catacombs.descent(gen.plans, i, j)) {
                    descents++;
                    int depth = gen.plans.depth(x, z);
                    if (c.id(12, depth, 12) == 65 && c.id(12, deep + 1, 12) == 65 && c.id(12, depth + 1, 12) == 0) ladders++;
                }
            }
        System.out.println("catacombs rooms=" + rooms + " hollow=" + hollow + " gates=" + gates + " traps=" + TRAPS[0]
            + " deepRooms=" + deepRooms + " deepHollow=" + deepHollow + " descents=" + descents + " ladders=" + ladders);
        check(rooms > 200 && hollow > rooms * 0.9, "catacomb rooms are hollow underground: " + hollow + "/" + rooms);
        check(deepRooms > 200 && deepHollow > deepRooms * 0.9, "deep catacomb rooms are hollow: " + deepHollow + "/" + deepRooms);
        check(descents > 20 && ladders == descents, "every shaft runs from the upper room's floor to the deep room: " + ladders + "/" + descents);
        check(gates > 3, "catacomb gates in the field: " + gates);
        check(TRAPS[0] > 0, "dart traps with plates: " + TRAPS[0]);

        // 4. Determinism: a fresh generator draws the same chunk.
        RuinsGenerator again = new RuinsGenerator(SEED, reserved, null);
        Chunk a = chunk(gen, Math.floorDiv(city.x, 16), Math.floorDiv(city.z, 16)), b = new Chunk();
        again.fill(b.proxy(), biomes(), Math.floorDiv(city.x, 16), Math.floorDiv(city.z, 16));
        check(Arrays.equals(a.blocks, b.blocks), "generation is deterministic");

        // 5. Weathering on a synthetic Lost Cities chunk.
        char[] primer = new char[65536];
        int ground = 70;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y <= ground; y++) primer[x << 12 | z << 8 | y] = (char) (1 << 4);
                primer[x << 12 | z << 8 | ground] = (char) (43 << 4);                          // street
                if (x == 10) { primer[x << 12 | z << 8 | ground] = (char) (2 << 4); primer[x << 12 | z << 8 | ground + 1] = (char) (38 << 4); }   // a park bed
                if (x == 12) for (int y = ground + 1; y <= ground + 4; y++) primer[x << 12 | z << 8 | y] = (char) (18 << 4);                    // a hedge
                if (x == 4) for (int y = ground + 1; y < ground + 60; y++) primer[x << 12 | z << 8 | y] = (char) ((y % 4 == 0 ? 20 : 4) << 4);   // a tall wall with glass
            }
        new Weathering(SEED).apply(0, 0, primer, true, ground);
        int tall = 0, glass = 0, mossy = 0;
        for (int z = 0; z < 16; z++)
            for (int y = ground + 1; y < 256; y++) {
                int id = primer[4 << 12 | z << 8 | y] >> 4;
                if (y > ground + 55 && id != 0 && id != 106 && id != 18) tall++;
                if (id == 20) glass++;
                if (id == 48) mossy++;
            }
        check(glass < 30, "most glass shattered: " + glass);
        int plants = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = ground; y < 256; y++) { int id = primer[x << 12 | z << 8 | y] >> 4; if (id == 18 || id == 38) plants++; }
        check(plants == 0, "weathering leaves no leaves or flowers in the Lost Cities: " + plants);
        check(mossy > 100, "cobblestone turned mossy: " + mossy);
        long t0 = System.nanoTime();
        for (int k = 0; k < 20; k++) new Weathering(SEED).apply(k, 3, primer.clone(), true, ground);
        long weatherMs = (System.nanoTime() - t0) / 20_000_000L;

        // 6. Portal frames: a 4x5 mossy frame is found from inside, broken or wrong frames are not.
        Map<Long, Integer> world = new HashMap<>();
        Portals.Blocks blocks = (x, y, z) -> world.getOrDefault(Portals.key(x, y, z), 0);
        for (int x = 0; x <= 3; x++) { world.put(Portals.key(x, 64, 0), 48); world.put(Portals.key(x, 68, 0), 48); }
        for (int y = 65; y <= 67; y++) { world.put(Portals.key(0, y, 0), 48); world.put(Portals.key(3, y, 0), 48); }
        Portals.Portal p = Portals.detect(blocks, "world", 1, 66, 0, true);
        check(p != null && p.w == 2 && p.h == 3 && p.x == 1 && p.y == 65, "4x5 mossy frame detected");
        check(Portals.detect(blocks, "world", 1, 66, 0, false) == null, "not along the other axis");
        world.put(Portals.key(0, 64, 0), 0);
        world.put(Portals.key(3, 68, 0), 0);
        check(Portals.detect(blocks, "world", 2, 65, 0, true) != null, "corners are optional");
        world.put(Portals.key(0, 66, 0), 49);
        check(Portals.detect(blocks, "world", 1, 66, 0, true) == null, "an obsidian block breaks a mossy frame");
        check(Portals.Portal.decode(p.encode()).encode().equals(p.encode()), "portal registry round trip");
        check(Portals.Portal.decode("world;z;-41;44;-22;2;3").link == null, "an unlinked gate from an older registry");
        Portals.Portal linked = Portals.Portal.decode("world;z;-41;44;-22;2;3;jaspr_ruins;-41;73;-21");
        check("jaspr_ruins;-41;73;-21".equals(linked.link) && linked.encode().equals("world;z;-41;44;-22;2;3;jaspr_ruins;-41;73;-21"), "linked gate round trip");

        double ms = genNanos / 1e6 / Math.max(1, genChunks);
        System.out.println("city " + city.name + " at " + city.x + "," + city.z + " half=" + city.half + " ground=" + city.ground);
        System.out.println(String.format("chunks=%d avgMs=%.2f chests=%d spawners=%d signs=%d weatherMs=%d failures=%d", genChunks, ms, chests, spawners, SIGNS[0], weatherMs, failures[0]));
        check(SIGNS[0] > 5, "carved chants: " + SIGNS[0]);
        check(Lore.bookCount() >= 10 && Lore.CHANTS.length >= 8, "the lore library");
        check(failures[0] == 0, "no generation failures");
        check(ms < 40, "chunks generate quickly: " + ms + " ms");
        // The danger ramp (owner, 2026-09-29): safe at the gates, danger growing outward, the most of it inside structures.
        check(Danger.ramp(0) == 0 && Danger.ramp(Danger.SAFE) == 0 && Danger.ramp(Danger.FULL) == 1 && Danger.ramp(Double.MAX_VALUE) == 1, "the ramp's ends");
        check(Math.abs(Danger.ramp((Danger.SAFE + Danger.FULL) / 2.0) - 0.5) < 1e-9, "the ramp is linear");
        check(Danger.wanderers(Danger.ramp(140)) == 0 && Danger.wanderers(Danger.ramp(200)) == 1 && Danger.wanderers(Danger.ramp(400)) == 2,
            "no wanderers near the gates, one farther out, two far out");
        check(Danger.crowdCap(0) == 1 && Danger.crowdCap(Danger.ramp(150)) == 2 && Danger.crowdCap(1) == 6, "structure crowds: 1 by the gates, 6 far out");
        check(Danger.cageCap(0) == 2 && Danger.cageCap(1) == 5, "cage crowds: 2 by the gates, 5 far out");
        check(Math.abs(Danger.strength(0) - 0.25) < 1e-9 && Danger.strength(1) == 1.0, "structures at a quarter by the gates, full far out");
        check(Danger.WILD_DREAD < 55 && Danger.dreadCap(null, 1) == Danger.WILD_DREAD, "the Dread only whispers on open ground (nausea starts at 55)");
        check(Danger.dreadCap(Danger.COVERED, 0) == 100, "under a roof or underground the Dread is full, even near a gate");
        check(Danger.dreadCap("city", 0) == Danger.WILD_DREAD && Danger.dreadCap("site", 1) == 100 && Danger.dreadCap("city", 0.5) < 90,
            "in a ruin's open air the Dread grows with the ramp");
        check(RuinsPlugin.MONSTER_CAP <= 20 && RuinsPlugin.SPAWN_TICKS >= 20, "fewer spawns: cap " + RuinsPlugin.MONSTER_CAP);
        System.out.println(String.format(java.util.Locale.ROOT, "danger ramp 48=%.2f 148=%.2f 248=%.2f 348=%.2f 448=%.2f", Danger.ramp(48), Danger.ramp(148),
            Danger.ramp(248), Danger.ramp(348), Danger.ramp(448)));
        System.out.println("RUINS_OK");
    }

    /** Chests/spawners recomputed by the populator must be exactly the ones in the generated chunk. */
    static int[] verifyTiles(RuinsGenerator gen, int cx, int cz) {
        Chunk c = chunk(gen, cx, cz);
        List<Canvas.Tile> tiles = RuinsPopulator.tilesOf(gen, cx, cz);
        Set<Long> planned = new HashSet<>();
        int chests = 0, spawners = 0;
        for (Canvas.Tile t : tiles) {
            int id = c.id(t.x & 15, t.y, t.z & 15);
            if (t.kind == Canvas.CHEST_TILE) { check(id == 54, "chest block at " + t.x + "," + t.y + "," + t.z + " is " + id); chests++; }
            else if (t.kind == Canvas.SPAWNER_TILE) { check(id == 52, "spawner block at " + t.x + "," + t.y + "," + t.z + " is " + id); spawners++; }
            else if (t.kind == Canvas.DISPENSER_TILE) { check(id == 23, "dispenser block at " + t.x + "," + t.y + "," + t.z + " is " + id); check(c.id(t.x & 15, t.y + 1, t.z & 15) == 70, "a plate over the dart trap"); TRAPS[0]++; }
            else { check(id == 68, "sign block at " + t.x + "," + t.y + "," + t.z + " is " + id); check(t.what.split("\n").length <= 4, "four sign lines"); SIGNS[0]++; }
            planned.add(Portals.key(t.x & 15, t.y, t.z & 15));
        }
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 256; y++) {
                    int id = c.id(x, y, z);
                    if ((id == 54 || id == 52 || id == 23) && !planned.contains(Portals.key(x, y, z))) throw new AssertionError("unplanned tile " + id + " at chunk " + cx + "," + cz + " " + x + "," + y + "," + z);
                }
        return new int[] {chests, spawners};
    }

    static void render(RuinsGenerator gen, int x0, int z0, int w, int h, File file) throws Exception {
        BufferedImage img = image(gen, x0, z0, w, h);
        BufferedImage big = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w * 2; x++) for (int z = 0; z < h * 2; z++) big.setRGB(x, z, img.getRGB(x / 2, z / 2));
        ImageIO.write(big, "png", file);
    }

    static BufferedImage image(RuinsGenerator gen, int x0, int z0, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[][] top = new int[w][h];
        for (int x = 0; x < w; x++)
            for (int z = 0; z < h; z++) {
                int wx = x0 + x, wz = z0 + z;
                Chunk c = chunk(gen, Math.floorDiv(wx, 16), Math.floorDiv(wz, 16));
                int lx = Math.floorMod(wx, 16), lz = Math.floorMod(wz, 16), y = 255;
                while (y > 0 && (c.id(lx, y, lz) == 0 || small(c.id(lx, y, lz)))) y--;
                top[x][z] = y;
                int id = c.id(lx, y, lz), data = c.data(lx, y, lz), rgb = color(id, data);
                if (id == 9 || id == 8) { int d = 0; while (d < 12 && (c.id(lx, y - d - 1, lz) == 9)) d++; rgb = mix(rgb, 0x10205a, d / 14.0); }
                img.setRGB(x, z, rgb);
            }
        for (int x = 1; x < w; x++)
            for (int z = 1; z < h; z++) {
                int dy = top[x][z] - top[x - 1][z - 1];
                double f = dy > 0 ? 1.18 : dy < 0 ? 0.8 : 1.0;
                int rgb = img.getRGB(x, z);
                img.setRGB(x, z, scale(rgb, f * (0.75 + Math.max(0, Math.min(1, (top[x][z] - 50) / 90.0)) * 0.4)));
            }
        return img;
    }

    static boolean small(int id) { return id == 31 || id == 37 || id == 38 || id == 175 || id == 39 || id == 106 || id == 30 || id == 111; }

    static int color(int id, int data) {
        switch (id) {
            case 2: return 0x5f9f35; case 3: return data == 2 ? 0x5a3d22 : 0x866043; case 1: return data == 5 ? 0x8a8a8c : 0x7d7d7d;
            case 4: return 0x6e6e6e; case 48: return 0x5f7a5a; case 98: return data == 1 ? 0x6f8a68 : data == 2 ? 0x646464 : data == 3 ? 0xa0a0a0 : 0x8c8c8c;
            case 109: return 0x949494; case 44: case 43: return 0xa8a8a8; case 139: return 0x6f8a68;
            case 9: case 8: return 0x3050d0; case 12: return 0xdbd3a0; case 13: return 0x857f7c; case 82: return 0x9fa4b1;
            case 18: return 0x2f6b1f; case 17: return 0x6b5230; case 5: return 0xa0824e; case 54: return 0xff9a00; case 52: return 0x200020;
            case 168: return data == 2 ? 0x24463a : data == 1 ? 0x4f8f7e : 0x5f9c90; case 144: return 0xd8d8d8; case 216: return 0xe0dcc8;
            case 169: return 0xcfe8e0; case 68: return 0x8a6a3a; case 85: return 0x8a6a3a; case 165: return 0x7cc56a; case 49: return 0x160c24; case 30: return 0xe8e8e8;
            case 7: return 0x333333; case 101: return 0x505050; case 65: return 0xa0824e; case 118: return 0x303030; case 159: return 0x252525;
            default: return 0xff00ff;
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static int scale(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return r << 16 | g << 8 | b;
    }
}
