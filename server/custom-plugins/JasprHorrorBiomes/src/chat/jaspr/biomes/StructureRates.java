package chat.jaspr.biomes;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;

/**
 * The 1.5x structure rate (3.25.0, owner request). Everything here is ADDITIVE: every site the 3.24 rules
 * place is still placed identically -- same anchor, same seed, same blocks -- and recognised exactly as
 * before. The extra sites come from
 *   - the catalogue: RELATIVE_STRUCTURE_DENSITY 0.20 -> 0.46 on the same fixed per-cell random. A cell below
 *     0.20 is "tier 0" and keeps the old rules verbatim; 0.20..0.46 is "tier 1" and yields to everything older
 *     (only ~38% of tier-1 cells can be built, hence 0.46 rather than 0.30 for 1.5x);
 *   - the set-piece register: a secondary lattice per structure with its own salt (Megaliths.C_CELL2, sized per
 *     structure so it adds half the primary count after its yields) that yields to every built primary, to
 *     higher-ranked secondaries, to older catalogue sites, older rooms and sanctuaries (Megaliths.secondaryFree);
 *   - the dungeon rooms: a third lattice for the eight doubled rooms, a second for the six single ones
 *     (Dungeons.D_CELL_C / D_SALT_C), sized the same way, yielding to everything older;
 *   - the vanilla-style rooms: 39 attempts per chunk instead of 26 (the first 26 draw exactly as before);
 *   - portal sanctuaries: at a seeded share of the square centres between the old ones (candidate()), yielding
 *     to older sites.
 * All sizes were measured with the rates probe (SA/work/r-rates: 96,000 x 96,000 blocks, 3.24.1 vs 3.25.0).
 * No secondary cell shares a factor above 2 with any room lattice: lattices whose spacings share a large
 * factor sit at one fixed offset from each other everywhere, and one of them then blocks the other.
 *
 * New sites also stay out of every chunk that existed when 3.25.0 first started (an immutable boundary, as
 * the expansion boundary does for the catalogue), so nothing is half built against old ground and the
 * dungeon retrofit never lays one into it. Precedence among new sites keeps the old order: set pieces, then
 * sanctuaries, then catalogue, then lattice rooms, then vanilla rooms; no test depends on a later one.
 */
public final class StructureRates {
    private StructureRates() {}

    /*
     * 3.28.0 (owner request, the second 1.5x: "all structures universally should spawn 1.5x whatever their
     * current spawn rate is"). A third layer, built exactly like the 3.25.0 one and on top of it: TIER 2.
     *   - set pieces: a tertiary lattice per register entry (Megaliths.C_CELL3), yielding to every primary and
     *     secondary site, every tier-0/1 catalogue site, every lattice A/B/C room and every sanctuary;
     *   - lattice rooms: a fourth lattice, D, for all fourteen rooms (Dungeons.D_CELL_D);
     *   - vanilla-style rooms: attempts 39 -> 59 (the first 39 draw exactly as before);
     *   - catalogue: RELATIVE_STRUCTURE_DENSITY 0.46 -> DENSITY_V2, the cells from 0.46 up being tier 2;
     *   - portal sanctuaries: the edge midpoints between the old ones (sanctuary2()).
     * Every tier-2 site stays out of every chunk that existed when 3.28.0 first started (the v2 boundary, with a
     * ring of one chunk), so the whole world generated so far is untouched and every site already built is
     * placed and recognised exactly as before: no older admission rule ever asks about a tier-2 site.
     * Precedence among tier 2: sanctuaries, set pieces, catalogue, lattice rooms, vanilla rooms.
     */
    public static final String BOUNDARY_FILE_V2 = "jaspr-rates-v2.boundary";
    private static final int BOUNDARY_MAGIC_V2 = 0x4a525232;
    private static final long TERTIARY_SALT = 0x5448495244L;          // "THIRD"
    static long tertiarySalt(long salt) { return salt * 37L + TERTIARY_SALT; }
    /** A tertiary lattice for a builder beyond the register table (none today): the secondary one, ~0.8 wider. */
    static int tertiaryCell(int cell) { return (int) Math.round(cell * 1.25); }
    private static final Map<Long, WorldgenExpansion.Boundary> BOUNDARIES_V2 = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> FAILED_V2 = new ConcurrentHashMap<>();

    /** Opens (first 3.28.0 start: snapshots) the v2 boundary. Called on WorldInitEvent, before any chunk. */
    public static synchronized int initializeV2(World world) {
        long seed = world.getSeed();
        WorldgenExpansion.Boundary known = BOUNDARIES_V2.get(seed);
        if (known != null) return known.protectedChunks();
        try {
            WorldgenExpansion.Boundary b = WorldgenExpansion.open(world.getWorldFolder(), seed, BOUNDARY_FILE_V2, BOUNDARY_MAGIC_V2);
            BOUNDARIES_V2.put(seed, b);
            FAILED_V2.remove(seed);
            return b.protectedChunks();
        } catch (IOException | RuntimeException e) {
            FAILED_V2.put(seed, Boolean.TRUE);                          // fail closed: no tier-2 site anywhere
            return -1;
        }
    }

    public static boolean failedV2(long seed) { return FAILED_V2.containsKey(seed); }

    /** True when no chunk of this box, nor the ring round it, existed before 3.28.0 (and the v1 test passes). */
    static boolean permits2(long seed, int x, int z, int sizeX, int sizeZ) {
        if (FAILED_V2.containsKey(seed) || !permits(seed, x, z, sizeX, sizeZ)) return false;
        WorldgenExpansion.Boundary b = BOUNDARIES_V2.get(seed);
        if (b == null || b.protectedChunks() == 0) return true;
        for (int cx = Math.floorDiv(x - 16, 16); cx <= Math.floorDiv(x + sizeX + 15, 16); cx++)
            for (int cz = Math.floorDiv(z - 16, 16); cz <= Math.floorDiv(z + sizeZ + 15, 16); cz++)
                if (b.contains(cx, cz)) return false;
        return true;
    }

    /** True when this chunk did not exist before 3.28.0. */
    static boolean fresh2(long seed, int cx, int cz) {
        if (FAILED_V2.containsKey(seed) || !fresh(seed, cx, cz)) return false;
        WorldgenExpansion.Boundary b = BOUNDARIES_V2.get(seed);
        return b == null || !b.contains(cx, cz);
    }

    /** What RELATIVE_STRUCTURE_DENSITY was before 3.25.0: a catalogue cell below it is tier 0 (old rules). */
    public static final double PREVIOUS_DENSITY = 0.20;
    public static final String BOUNDARY_FILE = "jaspr-rates-v1.boundary";
    private static final int BOUNDARY_MAGIC = 0x4a525231;
    /** Secondary set-piece lattices are sqrt 2 wider (half the sites) with a salt of their own. */
    static final double SECONDARY_SPACING = Math.sqrt(2.0);
    private static final long SECONDARY_SALT = 0x5345434F4E44L;       // "SECOND"

    static int secondaryCell(int cell) { return (int) Math.round(cell * SECONDARY_SPACING); }
    static long secondarySalt(long salt) { return salt * 31L + SECONDARY_SALT; }

    // == the upgrade boundary =======================================================================
    /** Seed -> boundary of the overworld generated by HorrorGenerator. A deny-all entry if it failed to open. */
    private static final Map<Long, WorldgenExpansion.Boundary> BOUNDARIES = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> FAILED = new ConcurrentHashMap<>();

    /** Opens (first start: snapshots) the boundary for the overworld. Called on WorldInitEvent, before any chunk. */
    public static synchronized int initialize(World world) {
        long seed = world.getSeed();
        WorldgenExpansion.Boundary known = BOUNDARIES.get(seed);
        if (known != null) return known.protectedChunks();
        try {
            WorldgenExpansion.Boundary b = WorldgenExpansion.open(world.getWorldFolder(), seed, BOUNDARY_FILE, BOUNDARY_MAGIC);
            BOUNDARIES.put(seed, b);
            FAILED.remove(seed);
            return b.protectedChunks();
        } catch (IOException | RuntimeException e) {
            // Fail closed: without the boundary no new site may be placed (old ones are unaffected).
            FAILED.put(seed, Boolean.TRUE);
            return -1;
        }
    }

    /** True when the rates boundary failed to open for this seed (new sites are then all refused). */
    public static boolean failed(long seed) { return FAILED.containsKey(seed); }

    /** True when no chunk of this box, nor the ring of chunks round it, existed before 3.25.0. */
    static boolean permits(long seed, int x, int z, int sizeX, int sizeZ) {
        if (FAILED.containsKey(seed)) return false;
        WorldgenExpansion.Boundary b = BOUNDARIES.get(seed);
        if (b == null || b.protectedChunks() == 0) return true;
        for (int cx = Math.floorDiv(x - 16, 16); cx <= Math.floorDiv(x + sizeX + 15, 16); cx++)
            for (int cz = Math.floorDiv(z - 16, 16); cz <= Math.floorDiv(z + sizeZ + 15, 16); cz++)
                if (b.contains(cx, cz)) return false;
        return true;
    }

    /** True when this chunk did not exist before 3.25.0 (the retrofit may lay only old work into old chunks). */
    static boolean fresh(long seed, int cx, int cz) {
        if (FAILED.containsKey(seed)) return false;
        WorldgenExpansion.Boundary b = BOUNDARIES.get(seed);
        return b == null || !b.contains(cx, cz);
    }

    // == portal sanctuaries ============================================================================
    /*
     * The original sanctuaries stand at chunk (8, 0) modulo 256 (HorrorGenerator.portalChunk, unchanged: the
     * importer and the catalogue's tier-0 reserve still ask exactly that). The new ones stand at the centres
     * of the squares between them -- as far from the old ones as the lattice allows -- on a seeded SELECT share
     * of those centres, and only where nothing older stands in the chunk itself (a sanctuary writes only its own
     * chunk). SELECT is set so that half as many again are built (rates probe, SA/work/r-rates).
     */
    static final int CENTRE_X = 136, CENTRE_Z = 128, SANCTUARY_HALO = 2;
    static final double SELECT = 0.56;

    /** A chunk the new sanctuary lattice could use (before admission). */
    static boolean candidate(long seed, int cx, int cz) {
        if (Math.floorMod(cx - CENTRE_X, 256) != 0 || Math.floorMod(cz - CENTRE_Z, 256) != 0) return false;
        long i = Math.floorDiv(cx - CENTRE_X, 256), j = Math.floorDiv(cz - CENTRE_Z, 256);
        return (Terrain.mix(seed + 0x53414E4354L + i * 341873128712L + j * 132897987541L) >>> 11) * 0x1.0p-53 < SELECT;
    }

    private static final Map<Long, Boolean> SANCTUARY_CACHE = new ConcurrentHashMap<>();

    /** True when a new (3.25.0) portal sanctuary stands in this chunk. */
    public static boolean sanctuary(Terrain t, int cx, int cz) {
        if (!candidate(t.seed, cx, cz)) return false;
        boolean ready = BOUNDARIES.containsKey(t.seed) || FAILED.containsKey(t.seed);
        long key = Terrain.mix(t.seed + cx * 341873128712L + cz * 132897987541L);
        Boolean known = ready ? SANCTUARY_CACHE.get(key) : null;
        if (known != null) return known;
        int x = cx * 16, z = cz * 16, size = 16;                      // its own chunk (each test adds a 2-block cordon)
        boolean ok = permits(t.seed, x, z, size, size)
            && !Megaliths.occupied(t, x, z, size, size, 255)            // a built (primary) set piece, any height
            && !StructurePlanner.catalogueTier0(t, x, z, size, size)   // an older catalogue site
            && !Dungeons.oldRoomNear(t, x, z, size, size);             // an older lattice room
        if (ready) { if (SANCTUARY_CACHE.size() > 4096) SANCTUARY_CACHE.clear(); SANCTUARY_CACHE.put(key, ok); }
        return ok;
    }

    /** True when this chunk is a sanctuary chunk or lies in the two-chunk halo of one (old or new). */
    static boolean nearSanctuary(Terrain t, int x, int z, int sizeX, int sizeZ) {
        int c0 = Math.floorDiv(x - 2, 16) - SANCTUARY_HALO, c1 = Math.floorDiv(x + sizeX + 1, 16) + SANCTUARY_HALO;
        int d0 = Math.floorDiv(z - 2, 16) - SANCTUARY_HALO, d1 = Math.floorDiv(z + sizeZ + 1, 16) + SANCTUARY_HALO;
        for (int cx = c0 + Math.floorMod(8 - c0, 256); cx <= c1; cx += 256)
            for (int cz = d0 + Math.floorMod(-d0, 256); cz <= d1; cz += 256)
                if (HorrorGenerator.portalChunk(cx, cz)) return true;
        for (int cx = c0 + Math.floorMod(CENTRE_X - c0, 256); cx <= c1; cx += 256)
            for (int cz = d0 + Math.floorMod(CENTRE_Z - d0, 256); cz <= d1; cz += 256)
                if (sanctuary(t, cx, cz)) return true;
        return false;
    }

    /** BiomeDetails keeps its motifs out of a new sanctuary's halo, as it does for the old ones. */
    static boolean newSanctuaryHalo(Terrain t, int cx, int cz) {
        for (int ax = cx - SANCTUARY_HALO; ax <= cx + SANCTUARY_HALO; ax++)
            for (int az = cz - SANCTUARY_HALO; az <= cz + SANCTUARY_HALO; az++)
                if (sanctuary(t, ax, az)) return true;
        return false;
    }

    /** The nearest standing sanctuary (old or new) to a block position, as {blockX, blockZ} of its centre. */
    public static int[] nearestSanctuary(Terrain t, int x, int z) {
        int bestX = (int) Math.round((x - 136) / 4096.0) * 4096 + 136, bestZ = (int) Math.round((z - 8) / 4096.0) * 4096 + 8;
        long best = sq(bestX - x, bestZ - z);
        int i0 = Math.floorDiv(Math.floorDiv(x, 16) - CENTRE_X, 256), j0 = Math.floorDiv(Math.floorDiv(z, 16) - CENTRE_Z, 256);
        for (int i = i0 - 1; i <= i0 + 2; i++) for (int j = j0 - 1; j <= j0 + 2; j++) {
            int cx = CENTRE_X + 256 * i, cz = CENTRE_Z + 256 * j;
            int bx = cx * 16 + 8, bz = cz * 16 + 8;
            long d = sq(bx - x, bz - z);
            if (d < best && sanctuary(t, cx, cz)) { best = d; bestX = bx; bestZ = bz; }
        }
        for (int[] e : new int[][]{{EDGE_A_X, EDGE_A_Z}, {EDGE_B_X, EDGE_B_Z}}) {         // 3.28.0 tier 2
            int a0 = Math.floorDiv(Math.floorDiv(x, 16) - e[0], 256), b0 = Math.floorDiv(Math.floorDiv(z, 16) - e[1], 256);
            for (int i = a0 - 1; i <= a0 + 2; i++) for (int j = b0 - 1; j <= b0 + 2; j++) {
                int cx = e[0] + 256 * i, cz = e[1] + 256 * j;
                int bx = cx * 16 + 8, bz = cz * 16 + 8;
                long d = sq(bx - x, bz - z);
                if (d < best && sanctuary2(t, cx, cz)) { best = d; bestX = bx; bestZ = bz; }
            }
        }
        return new int[]{bestX, bestZ};
    }

    private static long sq(long a, long b) { return a * a + b * b; }

    // == tier-2 portal sanctuaries (3.28.0) ================================================================
    /*
     * On the edge midpoints between the old sanctuaries -- chunk (136, 0) and (8, 128) modulo 256, as far from
     * both older families as the lattice allows -- on a seeded SELECT_2 share, admitted where nothing older
     * stands in the chunk (with its cordon) and only in chunks that did not exist before 3.28.0.
     */
    static final int EDGE_A_X = 136, EDGE_A_Z = 0, EDGE_B_X = 8, EDGE_B_Z = 128;
    static final double SELECT_2 = 0.46;

    static boolean candidate2(long seed, int cx, int cz) {
        boolean a = Math.floorMod(cx - EDGE_A_X, 256) == 0 && Math.floorMod(cz - EDGE_A_Z, 256) == 0;
        boolean b = Math.floorMod(cx - EDGE_B_X, 256) == 0 && Math.floorMod(cz - EDGE_B_Z, 256) == 0;
        if (!a && !b) return false;
        long i = Math.floorDiv(cx, 256), j = Math.floorDiv(cz, 256);
        return (Terrain.mix(seed + 0x53414E4332L + (a ? 0 : 97L) + i * 341873128712L + j * 132897987541L) >>> 11) * 0x1.0p-53 < SELECT_2;
    }

    private static final Map<Long, Boolean> SANCTUARY2_CACHE = new ConcurrentHashMap<>();

    /** True when a tier-2 (3.28.0) portal sanctuary stands in this chunk. */
    public static boolean sanctuary2(Terrain t, int cx, int cz) {
        if (!candidate2(t.seed, cx, cz)) return false;
        boolean ready = BOUNDARIES_V2.containsKey(t.seed) || FAILED_V2.containsKey(t.seed);
        long key = Terrain.mix(t.seed + cx * 341873128712L + cz * 132897987541L + 2L);
        Boolean known = ready ? SANCTUARY2_CACHE.get(key) : null;
        if (known != null) return known;
        int x = cx * 16, z = cz * 16, size = 16;
        boolean ok = permits2(t.seed, x, z, size, size)
            && !Megaliths.occupiedAll(t, x, z, size, size, 255)         // a primary or secondary set piece
            && !StructurePlanner.catalogueTier01(t, x, z, size, size)  // a tier-0/1 catalogue site
            && !Dungeons.roomNearABC(t, x, z, size, size);             // a lattice A/B/C room
        if (ready) { if (SANCTUARY2_CACHE.size() > 4096) SANCTUARY2_CACHE.clear(); SANCTUARY2_CACHE.put(key, ok); }
        return ok;
    }

    /** nearSanctuary() counting the tier-2 sanctuaries too: what every tier-2 site asks. */
    static boolean nearSanctuaryAll(Terrain t, int x, int z, int sizeX, int sizeZ) {
        if (nearSanctuary(t, x, z, sizeX, sizeZ)) return true;
        int c0 = Math.floorDiv(x - 2, 16) - SANCTUARY_HALO, c1 = Math.floorDiv(x + sizeX + 1, 16) + SANCTUARY_HALO;
        int d0 = Math.floorDiv(z - 2, 16) - SANCTUARY_HALO, d1 = Math.floorDiv(z + sizeZ + 1, 16) + SANCTUARY_HALO;
        for (int[] e : new int[][]{{EDGE_A_X, EDGE_A_Z}, {EDGE_B_X, EDGE_B_Z}})
            for (int cx = c0 + Math.floorMod(e[0] - c0, 256); cx <= c1; cx += 256)
                for (int cz = d0 + Math.floorMod(e[1] - d0, 256); cz <= d1; cz += 256)
                    if (sanctuary2(t, cx, cz)) return true;
        return false;
    }

    /** BiomeDetails keeps its motifs out of a tier-2 sanctuary's halo too. */
    static boolean sanctuary2Halo(Terrain t, int cx, int cz) {
        for (int ax = cx - SANCTUARY_HALO; ax <= cx + SANCTUARY_HALO; ax++)
            for (int az = cz - SANCTUARY_HALO; az <= cz + SANCTUARY_HALO; az++)
                if (sanctuary2(t, ax, az)) return true;
        return false;
    }

    /** For the server log (RATES_BOUNDARY_READY). */
    static File boundaryFile(World w) { return new File(w.getWorldFolder(), BOUNDARY_FILE); }
}
