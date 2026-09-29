package chat.jaspr.nether;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * The owner's GLM Nether structures in natural generation (owner, 2026-09-29: "Implement everything from GLM Nether
 * structures into the Nether's natural structure generation ... make these structures dangerous and filled with loot").
 * Three grids share the Nether with the mega structures and the Nether Cities (which keep their places):
 * <ul>
 *   <li>lords (640-block cells, one in every cell): the ten Nether Lords' strongholds, assigned so that neighbouring
 *       cells never repeat a lord;</li>
 *   <li>great builds (448-block cells, 65 %): castles, cathedrals, labyrinths, palaces;</li>
 *   <li>common builds (176-block cells, 50 %): keeps, towers, farms, crypts, hubs.</li>
 * </ul>
 * A grid only takes spots clear of mega sites, city reaches and the grids above it, and prefers the builds whose theme
 * suits the NetherEx region there. Every build hollows its own cavern (converted per column by tools/convert_glm.py:
 * the ceiling it needs, a lava seal around it, a margin where lava lakes may lie) and takes the region's materials for
 * its ground, trees and water. It is drawn chunk by chunk through {@link Draw} and is deterministic from the seed.
 */
final class GlmSites {
    enum Tier {
        LORD(640, 1.0, 0x4C4F5244L), GREAT(448, 0.65, 0x47524541L), COMMON(176, 0.5, 0x434F4D4DL);
        final int cell; final double chance; final long salt;
        Tier(int cell, double chance, long salt) { this.cell = cell; this.chance = chance; this.salt = salt; }
    }

    /** One line of resources/glm/builds.tsv. */
    static final class Entry {
        final String key, title, theme, lord; final Tier tier; final Biomes.Nex affinity;
        final int sx, sy, sz, ground, margin, maxCeil, cavW, cavD;
        Entry(String[] p) {
            key = p[0]; title = p[1]; tier = Tier.valueOf(p[2].toUpperCase(java.util.Locale.ROOT)); theme = p[3];
            affinity = p[4].equals("-") ? null : Biomes.Nex.valueOf(p[4]); lord = p[5].equals("-") ? null : p[5];
            sx = Integer.parseInt(p[6]); sy = Integer.parseInt(p[7]); sz = Integer.parseInt(p[8]); ground = Integer.parseInt(p[9]);
            margin = Integer.parseInt(p[10]); maxCeil = Integer.parseInt(p[11]); cavW = Integer.parseInt(p[12]); cavD = Integer.parseInt(p[13]);
        }
    }

    /** A planned build. Its box is the rotated cavern map; layer 0 of the build lies at {@link #base()}. */
    static final class Site {
        final Entry e; final Tier tier; final int cellX, cellZ, rot, floor, minX, minZ, maxX, maxZ, x, z, salt; final Biomes.Nex region;
        Site(Entry e, Tier tier, int cellX, int cellZ, int rot, int minX, int minZ, int floor, Biomes.Nex region, long seed) {
            this.e = e; this.tier = tier; this.cellX = cellX; this.cellZ = cellZ; this.rot = rot; this.minX = minX; this.minZ = minZ;
            this.maxX = minX + ((rot & 1) == 0 ? e.cavW : e.cavD) - 1; this.maxZ = minZ + ((rot & 1) == 0 ? e.cavD : e.cavW) - 1;
            this.floor = floor; this.region = region; this.x = (minX + maxX) / 2; this.z = (minZ + maxZ) / 2;
            this.salt = (int) (seed ^ (seed >>> 32));
        }
        int base() { return floor - e.ground; }
        /** The registry box: the cavern from under the build to its ceiling. */
        int[] box() {
            int y1 = Math.max(1, Math.min(base(), floor) - 3), y2 = Math.min(126, Math.max(base() + e.sy, floor + e.maxCeil) + 3);
            return new int[]{minX, y1, minZ, maxX, y2, maxZ};
        }
        String id() { return tier.name().charAt(0) + ":" + cellX + ":" + cellZ; }
        double dist(double px, double pz) { return Math.hypot(px - x, pz - z); }
    }

    interface Keepout { boolean blocked(int x0, int z0, int x1, int z1); }

    final long seed;
    private final Mega.Regions regions;
    private final Keepout lordKeepout, outer;
    private final Function<String, InputStream> resources;
    final List<Entry> lords = new ArrayList<>(), greats = new ArrayList<>(), commons = new ArrayList<>();
    final Map<String, Entry> byKey = new LinkedHashMap<>();
    private static final Site NONE = null;
    private final Map<Long, Object>[] cache;
    private final LinkedHashMap<String, GlmBuild> builds = new LinkedHashMap<>(16, 0.75f, true);
    private long cachedBytes;
    static final long CACHE_BYTES = 48L << 20;
    int loads, loadFailures, drawn;
    long loadNanos;

    @SuppressWarnings("unchecked")
    /**
     * @param lordKeepout what the Lords' strongholds and the great builds avoid (the Nether Cities: the mega structures
     *                    avoid them)
     * @param outer what the common builds avoid besides the Lords and the great builds (the cities and the mega structures)
     */
    GlmSites(long seed, Mega.Regions regions, Keepout lordKeepout, Keepout outer, Function<String, InputStream> resources) throws IOException {
        this.seed = seed; this.regions = regions; this.lordKeepout = lordKeepout; this.outer = outer; this.resources = resources;
        cache = new Map[Tier.values().length];
        for (int i = 0; i < cache.length; i++) cache[i] = new LinkedHashMap<Long, Object>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Object> e) { return size() > 1024; }
        };
        try (InputStream in = resources.apply("glm/builds.tsv")) {
            if (in == null) throw new IOException("missing glm/builds.tsv");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                Entry e = new Entry(line.split("\t"));
                byKey.put(e.key, e);
                (e.tier == Tier.LORD ? lords : e.tier == Tier.GREAT ? greats : commons).add(e);
            }
        }
        // lords in a fixed order so that the lord of a cell never depends on the file's order
        lords.sort((a, b) -> Integer.compare(LORD_ORDER.indexOf(a.lord), LORD_ORDER.indexOf(b.lord)));
    }

    static final List<String> LORD_ORDER = java.util.Arrays.asList("deathwing", "ignareth", "pit_lord", "ashen_wither", "cursed_king",
        "dread_sorcerer", "voidborn", "bone_colossus", "crimson_tyrant", "blood_count");

    Entry entry(String key) { return byKey.get(key); }
    Entry lordEntry(String lord) { for (Entry e : lords) if (lord.equals(e.lord)) return e; return null; }
    int size() { return byKey.size(); }

    // ---- planning --------------------------------------------------------------------------------------------------
    synchronized Site site(Tier t, int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        Map<Long, Object> c = cache[t.ordinal()];
        Object o = c.get(key);
        if (o == null) {
            Site s = plan(t, cx, cz);
            c.put(key, s == null ? Boolean.FALSE : s);
            return s;
        }
        return o instanceof Site ? (Site) o : null;
    }

    private Site plan(Tier t, int cx, int cz) {
        Random r = new Random(seed ^ (cx * 0x5DEECE66DL + cz * 0x2545F4914F6CDD1DL) ^ t.salt);
        if (r.nextDouble() >= t.chance) return null;
        List<Entry> list = t == Tier.LORD ? lords : t == Tier.GREAT ? greats : commons;
        if (list.isEmpty()) return null;
        int n = list.size();
        // neighbouring cells differ: the lord grid steps 1 east and 3 south, the others 7 and 3
        int pick = t == Tier.LORD ? Math.floorMod(cx + 3 * cz + (int) Math.floorMod(seed, 10L), n)
            : Math.floorMod(cx * 7 + cz * 3 + (int) (seed & 0xffff), n);
        for (int tries = 0; tries < 24; tries++) {
            int px = cx * t.cell + 8 + r.nextInt(t.cell - 16), pz = cz * t.cell + 8 + r.nextInt(t.cell - 16);
            Biomes.Nex region = regions.at(px, pz);
            Entry e = list.get(pick);
            if (t != Tier.LORD && e.affinity != null && e.affinity != region) {
                e = null;
                for (int k = 1; k < n && e == null; k++) {
                    Entry c = list.get((pick + k) % n);
                    if (c.affinity == null || c.affinity == region) e = c;
                }
                if (e == null) continue;
            }
            int rot = r.nextInt(4);
            int w = (rot & 1) == 0 ? e.cavW : e.cavD, d = (rot & 1) == 0 ? e.cavD : e.cavW;
            int x0 = cx * t.cell + 8, z0 = cz * t.cell + 8, x1 = (cx + 1) * t.cell - 9 - w, z1 = (cz + 1) * t.cell - 9 - d;
            if (x1 < x0 || z1 < z0) continue;
            int minX = Math.max(x0, Math.min(x1, px - w / 2)), minZ = Math.max(z0, Math.min(z1, pz - d / 2));
            if (blocked(t, minX - 8, minZ - 8, minX + w + 7, minZ + d + 7)) continue;
            long siteSeed = r.nextLong();
            int floor = floor(e, r);
            return new Site(e, t, cx, cz, rot, minX, minZ, floor, regions.at(minX + w / 2, minZ + d / 2), siteSeed);
        }
        return null;
    }

    /** The floor heights a build allows: {lowest, highest}; the highest may be below the lowest for the tallest builds. */
    static int[] floorRange(Entry e) {
        int lo = Math.max(5, 5 + e.ground), hi = 121 - e.maxCeil;
        if (e.sy >= 118) lo = 4 + Math.max(0, e.ground);
        return new int[]{lo, hi};
    }

    /** Floor height: above the lava sea where the build allows it, low enough for its cavern to fit under the roof. */
    static int floor(Entry e, Random r) {
        int[] f = floorRange(e);
        int pref = 30 + r.nextInt(26);
        return f[1] < f[0] ? f[0] : Math.max(f[0], Math.min(f[1], pref));
    }

    /** Planning order: cities, the Lords' strongholds, the great builds, the mega structures, the common builds. */
    private boolean blocked(Tier t, int x0, int z0, int x1, int z1) {
        if (t == Tier.LORD) return lordKeepout != null && lordKeepout.blocked(x0, z0, x1, z1);
        if (t == Tier.GREAT) return (lordKeepout != null && lordKeepout.blocked(x0, z0, x1, z1)) || !touching(Tier.LORD, x0, z0, x1, z1).isEmpty();
        if (outer != null && outer.blocked(x0, z0, x1, z1)) return true;
        return !touching(Tier.LORD, x0, z0, x1, z1).isEmpty() || !touching(Tier.GREAT, x0, z0, x1, z1).isEmpty();
    }

    /** Planned sites of one tier whose box overlaps the block box (a site never leaves its cell). */
    List<Site> touching(Tier t, int x0, int z0, int x1, int z1) {
        List<Site> out = new ArrayList<>(1);
        for (int cx = Math.floorDiv(x0, t.cell); cx <= Math.floorDiv(x1, t.cell); cx++)
            for (int cz = Math.floorDiv(z0, t.cell); cz <= Math.floorDiv(z1, t.cell); cz++) {
                Site s = site(t, cx, cz);
                if (s != null && s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) out.add(s);
            }
        return out;
    }

    List<Site> touching(int x0, int z0, int x1, int z1) {
        List<Site> out = new ArrayList<>(1);
        for (Tier t : Tier.values()) out.addAll(touching(t, x0, z0, x1, z1));
        return out;
    }

    /** The planned site whose box contains the column, or null. */
    Site at(int x, int z) {
        for (Tier t : Tier.values()) {
            Site s = site(t, Math.floorDiv(x, t.cell), Math.floorDiv(z, t.cell));
            if (s != null && x >= s.minX && x <= s.maxX && z >= s.minZ && z <= s.maxZ) return s;
        }
        return null;
    }

    /** Nearest planned lord stronghold within {@code cells} lord cells whose lord is not in {@code skip}. */
    Site nearestLord(int x, int z, Collection<String> skip, int cells, java.util.function.Predicate<Site> built) {
        int ccx = Math.floorDiv(x, Tier.LORD.cell), ccz = Math.floorDiv(z, Tier.LORD.cell);
        Site best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
            Site s = site(Tier.LORD, ccx + dx, ccz + dz);
            if (s == null || s.e.lord == null || (skip != null && skip.contains(s.e.lord)) || (built != null && !built.test(s))) continue;
            double d = s.dist(x, z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    // ---- the builds -------------------------------------------------------------------------------------------------
    synchronized GlmBuild build(String key) {
        GlmBuild b = builds.get(key);
        if (b != null) return b;
        long t = System.nanoTime();
        try (InputStream in = resources.apply("glm/" + key + ".glb")) {
            if (in == null) throw new IOException("missing glm/" + key + ".glb");
            b = GlmBuild.read(key, in);
        } catch (IOException | RuntimeException e) {
            loadFailures++;
            return null;
        } finally {
            loadNanos += System.nanoTime() - t;
        }
        loads++;
        builds.put(key, b);
        cachedBytes += b.bytes;
        Iterator<Map.Entry<String, GlmBuild>> it = builds.entrySet().iterator();
        while (cachedBytes > CACHE_BYTES && builds.size() > 1 && it.hasNext()) {
            Map.Entry<String, GlmBuild> old = it.next();
            if (old.getKey().equals(key)) continue;
            cachedBytes -= old.getValue().bytes;
            it.remove();
        }
        return b;
    }

    synchronized int cached() { return builds.size(); }
    synchronized long cachedBytes() { return cachedBytes; }

    // ---- region materials ------------------------------------------------------------------------------------------
    /** What a build's natural ground, trees and water become in a NetherEx region. */
    static final class Mats {
        final Biomes.Nex n; final int surface, soil, rock, sand, canopy, trunk, liquid, path; final boolean axis;
        Mats(Biomes.Nex n, int surface, int soil, int rock, int sand, int canopy, int trunk, boolean axis, int liquid, int path) {
            this.n = n; this.surface = surface; this.soil = soil; this.rock = rock; this.sand = sand; this.canopy = canopy;
            this.trunk = trunk; this.axis = axis; this.liquid = liquid; this.path = path;
        }
    }

    private static final Map<Biomes.Nex, Mats> MATS = new HashMap<>();
    static Mats mats(Biomes.Nex n) {
        Mats m = MATS.get(n);
        if (m != null) return m;
        int lava = Blocks.LAVA << 4, rack = Blocks.NETHERRACK << 4, soul = Blocks.SOUL_SAND << 4, bone = Blocks.BONE_BLOCK << 4;
        switch (n) {
            case RUTHLESS_SANDS: m = new Mats(n, soul, Blocks.GLOOMY_NETHERRACK, Blocks.GLOOMY_NETHERRACK, soul, 0, bone, true, lava, soul); break;
            case TORRID_WASTELAND: m = new Mats(n, Blocks.FIERY_NETHERRACK, Blocks.FIERY_NETHERRACK, Blocks.FIERY_NETHERRACK, Blocks.BASALT,
                Blocks.MAGMA << 4, Blocks.BASALT, false, lava, Blocks.BASALT); break;
            case FUNGI_FOREST: m = new Mats(n, Blocks.HYPHAE, Blocks.LIVELY_NETHERRACK, Blocks.LIVELY_NETHERRACK, soul,
                (Blocks.HUGE_RED << 4) | 14, Blocks.ELDER_STEM, false, lava, Blocks.HYPHAE); break;
            case ARCTIC_ABYSS: m = new Mats(n, Blocks.FROSTBURN_ICE, Blocks.ICY_NETHERRACK, Blocks.ICY_NETHERRACK, Blocks.ICY_NETHERRACK,
                174 << 4, bone, true, Blocks.WATER << 4, Blocks.FROSTBURN_ICE); break;
            default: m = new Mats(n, rack, rack, rack, soul, Blocks.NETHER_WART_BLOCK << 4, Blocks.STALAGNATE_MIDDLE, false, lava, soul);
        }
        MATS.put(n, m);
        return m;
    }

    /** The block a palette entry places at (x, y, z); -1 keeps the world block. */
    static int cell(int role, int v, Mats m, int x, int y, int z, int salt) {
        switch (role) {
            case GlmBuild.LIT: return v;
            case GlmBuild.AIR: case GlmBuild.TILE: return 0;
            case GlmBuild.VOID: return -1;
            case GlmBuild.SURFACE: return m.surface;
            case GlmBuild.SOIL: return m.soil;
            case GlmBuild.ROCK: return m.rock;
            case GlmBuild.SAND: return m.sand;
            case GlmBuild.CANOPY: return m.canopy;
            case GlmBuild.TRUNK: return m.axis ? m.trunk | (v & 12) : m.trunk;
            case GlmBuild.PATH: return m.path;
            case GlmBuild.LIQUID: return (m.liquid & ~15) | (v & 15);
            case GlmBuild.PLANT:
                if (m.n != Biomes.Nex.FUNGI_FOREST || Draw.rnd(x, y, z, salt + 17) > 0.55) return 0;
                return (Draw.rnd(x, y, z, salt + 18) < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM) << 4;
            case GlmBuild.SNOW:
                if (m.n == Biomes.Nex.ARCTIC_ABYSS) return v;
                return (v >> 4) == 80 ? Blocks.BONE_BLOCK << 4 : 0;
            default: return 0;
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------
    /** Draws the part of a site inside d's box: sealing ring, cavern, build, then its tiles and markers (tile box only). */
    void draw(Site s, Draw d) {
        if (!d.touches(s.minX, s.minZ, s.maxX, s.maxZ)) return;
        GlmBuild b = build(s.e.key);
        if (b == null) return;
        Mats m = mats(s.region);
        int rot = s.rot, M = b.margin, base = s.base(), salt = s.salt;
        int xa = Math.max(d.x0, s.minX), xb = Math.min(d.x1, s.maxX), za = Math.max(d.z0, s.minZ), zb = Math.min(d.z1, s.maxZ);
        char[] val = b.values[rot];
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            int X = b.cavX(x - s.minX, z - s.minZ, rot), Z = b.cavZ(x - s.minX, z - s.minZ, rot);
            if (X < 0 || Z < 0 || X >= b.cw || Z >= b.cd) continue;
            int ci = Z * b.cw + X;
            int k = b.kind[ci] & 255, h = b.ceil[ci] & 255, lo = b.low[ci] & 255;
            int bx = X - M, bz = Z - M;
            boolean inB = bx >= 0 && bx < b.sx && bz >= 0 && bz < b.sz;
            if (k == 0 && !inB) continue;
            boolean cave = k == 1 || k == 3;
            int ceilY = Math.min(121, s.floor + h);
            // seal the lava in the rock around and under the cavern, so nothing pours in
            int sealLo = Math.max(5, Math.min(s.floor - 6, lo < 255 ? base + lo - 3 : s.floor - 6));
            int sealHi = Math.min(122, ceilY + (cave ? 4 : 2));
            for (int y = sealLo; y <= sealHi; y++) {
                int id = d.id(x, y, z);
                if (id == Blocks.LAVA || id == Blocks.LAVA_FLOW) d.set(x, y, z, m.rock);
            }
            if (k == 2) continue;
            boolean lake = k == 3 && Draw.fbm(x, z, 23, salt + 5) > 0.36;
            int yTop = Math.min(126, Math.max(cave ? ceilY + 3 : 0, inB ? base + b.sy - 1 : 0));
            int yBot = Math.max(1, Math.min(cave ? s.floor - 9 : 127, lo < 255 ? base + lo - 2 : 127));
            if (inB) yBot = Math.max(1, Math.min(yBot, base));
            for (int y = yBot; y <= yTop; y++) {
                int by = y - base;
                if (inB && by >= 0 && by < b.sy) {
                    int p = b.pal(bx, by, bz);
                    int v = cell(b.roles[p], val[p], m, x, y, z, salt);
                    if (v >= 0) {
                        if (v != 0 || d.id(x, y, z) != Blocks.BEDROCK) d.set(x, y, z, v);
                        continue;
                    }
                }
                // the build keeps the world here: the cavern around it, the rock under and around its buried part
                if (!cave) {
                    if (y < s.floor && lo < 255 && y >= base + lo - 2 && !Blocks.isFullSolid(d.id(x, y, z))) d.set(x, y, z, m.rock);
                    continue;
                }
                if (y > s.floor && y <= ceilY) { if (d.id(x, y, z) != Blocks.BEDROCK) d.set(x, y, z, 0); }
                else if (y == s.floor) d.set(x, y, z, lake ? m.liquid : m.surface);
                else if (y > ceilY) { if (y <= 122 && !Blocks.isFullSolid(d.id(x, y, z))) d.set(x, y, z, m.rock); }
                else if (lake && y == s.floor - 1) d.set(x, y, z, m.liquid);
                else if (!Blocks.isFullSolid(d.id(x, y, z))) d.set(x, y, z, m.rock);
            }
            if (cave && !inB && h > 6) MegaDesign.glowHang(d, x, z, ceilY, salt + 11, 0.012);
        }
        drawn++;
        tiles(s, b, d);
    }

    private void tiles(Site s, GlmBuild b, Draw d) {
        int rot = s.rot, M = b.margin, base = s.base();
        for (GlmBuild.Tile t : b.tiles) {
            int X = t.x + M, Z = t.z + M;
            int wx = s.minX + b.rotU(X, Z, rot), wz = s.minZ + b.rotV(X, Z, rot), wy = base + t.y;
            if (!d.inTiles(wx, wz) || wy < 1 || wy > 126) continue;
            String table = t.type == GlmBuild.T_CHEST && !t.table.isEmpty() ? "glm:" + t.table + ":" + s.e.theme : "";
            d.out.late.add(new Pending(wx, wy, wz, b.values[rot][t.pal], t, table, rot));
        }
        int spawners = 0, garrisons = 0;
        for (GlmBuild.Marker mk : b.markers) {
            int X = mk.x + M, Z = mk.z + M;
            int wx = s.minX + b.rotU(X, Z, rot), wz = s.minZ + b.rotV(X, Z, rot), wy = base + mk.y;
            if (!d.inTiles(wx, wz) || wy < 1 || wy > 126) continue;
            int slot = parse(mk.arg);
            switch (mk.type) {
                case "spawner": d.spawner(wx, wy, wz, spawnerKind(s, slot)); spawners++; break;
                case "garrison": d.point(wx, wy, wz, "garrison:" + pack(s, slot)); garrisons++; break;
                case "arena": d.point(wx, wy, wz, "lord:" + mk.arg); break;
                default:
            }
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    /** Where a Lord's stronghold raises its Lord (world x, y, z), from the build itself; null for other builds. */
    int[] arena(Site s) {
        if (s.e.lord == null) return null;
        GlmBuild b = build(s.e.key);
        if (b == null) return null;
        for (GlmBuild.Marker mk : b.markers) if (mk.type.equals("arena")) {
            int X = mk.x + b.margin, Z = mk.z + b.margin;
            return new int[]{s.minX + b.rotU(X, Z, s.rot), s.base() + mk.y, s.minZ + b.rotV(X, Z, s.rot)};
        }
        return null;
    }

    /** A tile block of a build, placed through Bukkit after the flush (so it gets its tile entity). */
    static final class Pending {
        final int x, y, z, block, rot; final GlmBuild.Tile t; final String table;
        Pending(int x, int y, int z, int block, GlmBuild.Tile t, String table, int rot) {
            this.x = x; this.y = y; this.z = z; this.block = block; this.t = t; this.table = table; this.rot = rot;
        }
    }

    // ---- who guards a build ---------------------------------------------------------------------------------------------
    static final Map<String, String[]> ROSTER = new HashMap<>();
    static final Map<Biomes.Nex, String[]> NATIVE = new HashMap<>();
    static {
        ROSTER.put("bastion", new String[]{"pigman_berserker", "cinder_imp", "hellhound", "magma_cube"});
        ROSTER.put("fortress", new String[]{"infernal_knight", "ashbone_archer", "wither_skeleton", "blaze"});
        ROSTER.put("castle", new String[]{"infernal_knight", "ashbone_archer", "hellhound", "dread_rider"});
        ROSTER.put("crypt", new String[]{"charred_ghoul", "soul_wraith", "ashbone_archer", "wither_skeleton"});
        ROSTER.put("arcane", new String[]{"pyre_warden", "cinder_witch", "shade", "soul_wraith"});
        ROSTER.put("volcanic", new String[]{"magma_hulk", "cinder_imp", "blaze", "magma_cube"});
        ROSTER.put("void", new String[]{"shade", "soul_wraith", "cinder_witch", "brimstone_spider"});
        ROSTER.put("temple", new String[]{"cinder_witch", "charred_ghoul", "pyre_warden", "brimstone_spider"});
        ROSTER.put("dungeon", new String[]{"brimstone_spider", "charred_ghoul", "cinder_imp", "infernal_knight"});
        ROSTER.put("hub", new String[]{"hellhound", "pyre_warden", "cinder_imp", "pigman_berserker"});
        ROSTER.put("farm", new String[]{"magma_cube", "cinder_imp", "brimstone_spider", "hellhound"});
        ROSTER.put("vault", new String[]{"pigman_berserker", "infernal_knight", "brimstone_spider", "cinder_imp"});
        ROSTER.put("cathedral", new String[]{"soul_wraith", "cinder_witch", "infernal_knight", "ashbone_archer"});
        ROSTER.put("ruin", new String[]{"hellhound", "cinder_imp", "charred_ghoul", "brimstone_spider"});
        NATIVE.put(Biomes.Nex.HELL, new String[]{"zombie_pigman", "magma_cube"});
        NATIVE.put(Biomes.Nex.RUTHLESS_SANDS, new String[]{"spinout", "wither_skeleton"});
        NATIVE.put(Biomes.Nex.TORRID_WASTELAND, new String[]{"salamander", "ember"});
        NATIVE.put(Biomes.Nex.FUNGI_FOREST, new String[]{"spore_creeper", "mogus"});
        NATIVE.put(Biomes.Nex.ARCTIC_ABYSS, new String[]{"wight", "coolmar_spider"});
    }

    static String[] roster(String theme) { String[] r = ROSTER.get(theme); return r == null ? ROSTER.get("castle") : r; }

    /** The kind a build's spawner number {@code slot} makes: mostly the theme's creatures, some of the region's own. */
    static String spawnerKind(Site s, int slot) {
        String[] r = roster(s.e.theme);
        double h = Draw.rnd(slot, 7, s.cellX * 31 + s.cellZ, s.salt);
        if (h < 0.2) {
            String[] nat = NATIVE.get(s.region);
            String k = nat[slot % nat.length];
            if (!k.equals("mogus") && !k.equals("spore_creeper")) return k;
        }
        return r[slot % r.length];
    }

    /** The pack of garrison number {@code slot}: two or three of the theme's creatures, sometimes a native, an elite lead. */
    static String pack(Site s, int slot) {
        String[] r = roster(s.e.theme);
        StringBuilder b = new StringBuilder();
        double h = Draw.rnd(slot, 9, s.cellX * 17 + s.cellZ, s.salt);
        double eliteShare = s.tier == Tier.COMMON ? 0.25 : 0.5;
        if (h < eliteShare) b.append('!');
        b.append(r[slot % r.length]).append('+').append(r[(slot + 1) % r.length]);
        if (Draw.rnd(slot, 11, s.cellX, s.salt) < 0.45) b.append('+').append(NATIVE.get(s.region)[slot % 2]);
        else if (s.tier != Tier.COMMON) b.append('+').append(r[(slot + 2) % r.length]);
        return b.toString();
    }

    static List<String> allKinds() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String[] r : ROSTER.values()) Collections.addAll(out, r);
        for (String[] r : NATIVE.values()) Collections.addAll(out, r);
        return new ArrayList<>(out);
    }
}
