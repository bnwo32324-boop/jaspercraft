package chat.jaspr.biomes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Tier 2 (3.29.0; owner 2026-10-04: "Make structures in the overworld 2x common ... Don't regenerate the overworld, but
 * do retrofit structures that would normally spawn if it was regenerated").
 *
 * The tier-2 PLAN -- Megaliths' tertiary lattices, Dungeons' lattice D, the catalogue's tier-2 grids and the extra
 * vanilla-room attempts (StructureRates) -- is a pure function of the seed, exactly as a regenerated world would have it:
 * every tier-2 site yields to every older one, and nothing older ever asks about tier 2, so nothing standing moves.
 * What is new is WHEN a planned site is built. Each is decided once, and the decision is a receipt in
 * plugins/JasprHorrorBiomes/tier2-world-uid.receipts ("B key" built, "R key why" refused):
 *  - a site wholly on ground generated since 3.29.0 (outside the v2 boundary) is decided by the first populate that
 *    reaches it and built chunk by chunk as its chunks populate, like any other site;
 *  - a site touching ground that already existed is decided by Tier2Retrofit, and built only where nobody has spent
 *    time (the inhabited time of every existing chunk under it is at most 1200 ticks), no chunk is guarded by an
 *    imported or Muse build, and no player is within 112 blocks; it is then built into every populated chunk at once,
 *    and any of its chunks that populate later build their share themselves.
 * Either way a site yields to every JasprImportedWorldgen and JasprMuseMaps plan that could reach it: those cells are
 * decided (frozen) first, so nothing of theirs can be planned over it later (PackPlans). Recognition -- loot, /where,
 * encounters, the Fold door, relighting -- names a tier-2 site only once it is built (built()).
 *
 * Builders ask allow() at the point where the plan has admitted a tier-2 site reaching their chunk. The retrofit runs
 * the same builders in PROBE mode (they offer their tier-2 sites instead of building) and ONLY mode (only the decided
 * site is built: every older finder is skipped and every block write outside its box is dropped, Dungeons.set()).
 */
public final class Tier2 {
    private Tier2() {}

    enum Mode { INLINE, PROBE, ONLY }

    /** A tier-2 site a builder offered in PROBE mode: its key and the block box (inclusive) it may write in. */
    static final class Offer {
        final String key;
        final int x0, z0, x1, z1;
        Offer(String key, int x0, int z0, int x1, int z1) { this.key = key; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; }
    }

    private static final class Scope {
        Mode mode = Mode.INLINE;
        String target;
        int x0, z0, x1, z1;
        List<Offer> offers;
    }

    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<Scope>() {
        @Override protected Scope initialValue() { return new Scope(); }
    };

    private static final class Ledger {
        final World world;
        final File file;
        final Map<String, Boolean> receipts = new ConcurrentHashMap<>();
        Ledger(World world, File file) { this.world = world; this.file = file; }
    }

    private static final Map<Long, Ledger> LEDGERS = new ConcurrentHashMap<>();
    /** Offline probes and tests only: every planned tier-2 site of this seed counts as built. */
    private static final Set<Long> ASSUME_BUILT = ConcurrentHashMap.newKeySet();
    static volatile Logger log;
    static volatile long builtCount, refusedCount, pendingCount;

    /** Opens (creates) this world's receipts. Called once the world is initialised, before its first populate. */
    public static synchronized int open(World world, File dataFolder) throws IOException {
        Ledger known = LEDGERS.get(world.getSeed());
        if (known != null) return known.receipts.size();
        File file = new File(dataFolder, "tier2-" + world.getUID() + ".receipts");
        Ledger l = new Ledger(world, file);
        if (file.isFile()) {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.length() < 3 || line.charAt(1) != ' ') continue;
                int end = line.indexOf(' ', 2);
                String key = end < 0 ? line.substring(2) : line.substring(2, end);
                if (line.charAt(0) == 'B') l.receipts.put(key, Boolean.TRUE);
                else if (line.charAt(0) == 'R') l.receipts.put(key, Boolean.FALSE);
            }
        } else {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(file.toPath(), new byte[0]);
        }
        LEDGERS.put(world.getSeed(), l);
        return l.receipts.size();
    }

    public static void close() { LEDGERS.clear(); }

    /** Offline probes and tests: treat every planned tier-2 site of this seed as built. */
    public static void assumeBuilt(long seed) { ASSUME_BUILT.add(seed); }

    static boolean active(long seed) { return LEDGERS.containsKey(seed) || ASSUME_BUILT.contains(seed); }

    /** Recognition: true once this tier-2 site is built (decided "B"). */
    public static boolean built(long seed, String key) {
        if (ASSUME_BUILT.contains(seed)) return true;
        Ledger l = LEDGERS.get(seed);
        if (l == null) return false;
        return Boolean.TRUE.equals(l.receipts.get(key));
    }

    /** The receipt for this key: TRUE built, FALSE refused, null undecided. */
    static Boolean receipt(long seed, String key) {
        Ledger l = LEDGERS.get(seed);
        return l == null ? null : l.receipts.get(key);
    }

    static World world(long seed) { Ledger l = LEDGERS.get(seed); return l == null ? null : l.world; }

    /** Records a decision once: appended and flushed to disk before anything is built on it. */
    static synchronized boolean record(long seed, String key, boolean built, String why) {
        Ledger l = LEDGERS.get(seed);
        if (l == null) return false;
        Boolean prev = l.receipts.get(key);
        if (prev != null) return prev;
        String line = (built ? "B " : "R ") + key + (why == null ? "" : " " + why) + "\n";
        try (FileOutputStream out = new FileOutputStream(l.file, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            if (log != null) log.warning("TIER2_RECEIPT_FAILED key=" + key + " " + e.getClass().getSimpleName());
            return false;                                  // not on disk: not built (asked again next time)
        }
        l.receipts.put(key, built);
        if (built) builtCount++; else refusedCount++;
        return built;
    }

    // ---------------------------------------------------------------- the builders' questions

    /** True outside the normal populate: in a probe or a single-site retrofit pass, nothing older may be built. */
    static boolean restricted() { return SCOPE.get().mode != Mode.INLINE; }

    /**
     * A builder's plan admitted tier-2 site key, which may write in the block box (inclusive), reaching its chunk.
     * INLINE (populate): decided now when the box is all new ground (else the retrofit decides it); true once built.
     * PROBE: offered to the retrofit, never built. ONLY: true for the one site being retrofitted.
     */
    static boolean allow(long seed, String key, int x0, int z0, int x1, int z1) {
        Scope s = SCOPE.get();
        if (s.mode == Mode.PROBE) { s.offers.add(new Offer(key, x0, z0, x1, z1)); return false; }
        if (s.mode == Mode.ONLY) return key.equals(s.target);
        if (ASSUME_BUILT.contains(seed)) return true;
        Ledger l = LEDGERS.get(seed);
        if (l == null) return false;
        Boolean known = l.receipts.get(key);
        if (known != null) return known;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                if (StructureRates.old2(seed, cx, cz)) { pendingCount++; return false; }   // the retrofit decides it
        String why = PackPlans.conflict(l.world, (x0 >> 4) - 1, (z0 >> 4) - 1, (x1 >> 4) + 1, (z1 >> 4) + 1);
        return record(seed, key, why == null, why == null ? "new" : why);
    }

    /**
     * Whether this chunk takes the extra vanilla-room attempts as it populates. A chunk populates once: one generated
     * since 3.29.0 does; one that existed then but is only populating now does too, and is marked so the retrofit leaves
     * it alone. Chunks populated before 3.29.0 get theirs from the retrofit.
     */
    static boolean extras(long seed, int cx, int cz) {
        if (SCOPE.get().mode != Mode.INLINE) return false;
        if (ASSUME_BUILT.contains(seed)) return true;
        Ledger l = LEDGERS.get(seed);
        if (l == null || StructureRates.failedV2(seed)) return false;
        if (StructureRates.old2(seed, cx, cz)) {
            String key = vanillaKey(cx, cz);
            Boolean known = l.receipts.get(key);
            if (known != null) return false;               // already done (or refused) by the retrofit
            record(seed, key, true, "populate");
        }
        return true;
    }

    /** ONLY mode: a builder's block write at this world column stays inside the decided site's box; PROBE writes nothing. */
    static boolean writable(Chunk c, int lx, int lz) {
        Scope s = SCOPE.get();
        if (s.mode == Mode.INLINE) return true;
        if (s.mode == Mode.PROBE) return false;
        int wx = c.getX() * 16 + lx, wz = c.getZ() * 16 + lz;
        return wx >= s.x0 && wx <= s.x1 && wz >= s.z0 && wz <= s.z1;
    }

    // ---------------------------------------------------------------- keys

    static String setPieceKey(long salt3, int acx, int acz) { return "t:" + Long.toHexString(salt3) + ":" + acx + ":" + acz; }
    static String roomKey(int i, int acx, int acz) { return "d:" + i + ":" + acx + ":" + acz; }
    static String vanillaKey(int cx, int cz) { return "v:" + cx + ":" + cz; }

    // ---------------------------------------------------------------- the retrofit's passes

    /** Runs the populate builders for this chunk in PROBE mode and returns the tier-2 sites they would build. */
    static List<Offer> probe(Runnable builders) {
        Scope s = SCOPE.get();
        Mode was = s.mode;
        List<Offer> prev = s.offers;
        s.mode = Mode.PROBE;
        s.offers = new ArrayList<>();
        try { builders.run(); return s.offers; }
        finally { s.mode = was; s.offers = prev; }
    }

    /** Runs the populate builders for one chunk with only the decided site key allowed to build, inside its box. */
    static void only(String key, int x0, int z0, int x1, int z1, Runnable builders) {
        Scope s = SCOPE.get();
        Mode was = s.mode;
        String prevTarget = s.target;
        int a = s.x0, b = s.z0, c = s.x1, d = s.z1;
        s.mode = Mode.ONLY; s.target = key; s.x0 = x0; s.z0 = z0; s.x1 = x1; s.z1 = z1;
        try { builders.run(); }
        finally { s.mode = was; s.target = prevTarget; s.x0 = a; s.z0 = b; s.x1 = c; s.z1 = d; }
    }

    static String describe() {
        int receipts = 0;
        for (Ledger l : LEDGERS.values()) receipts += l.receipts.size();
        return "receipts=" + receipts + " builtThisRun=" + builtCount + " refusedThisRun=" + refusedCount + " pendingAsks=" + pendingCount;
    }
}
