package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One of the colossal structures (see {@link Colossi}): its layout (planned once per site from the site's Random), its
 * cavern's floor, lakes and dressing, and the structure, drawn clip by clip. Every method must be deterministic and may
 * read the canvas only in the column it is writing.
 */
abstract class ColossusDesign {
    abstract Object plan(Colossi.Site s, Random r);

    /** Draws the part of the structure inside d's box (after the cavern). */
    abstract void draw(Colossi.Site s, Draw d);

    /** The cavern floor's top block at (x, y, z). */
    abstract int ground(Colossi.Site s, int x, int y, int z);

    /** What fills the gaps under the cavern floor. */
    abstract Draw.Mat under();

    /** Columns where the cavern floor never holds a lake (the structure's own footprint). */
    boolean dry(Colossi.Site s, int x, int z) { return false; }

    double lakeLevel() { return 0.36; }

    int lakeLiquid() { return Blocks.LAVA << 4; }

    /** Per-column cavern dressing (floor scatter, ceiling hangings); called after the column is carved. */
    void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {}

    /** The structure's traps, puzzles and seals in world coordinates (see Ordeals); made once per site. */
    java.util.List<Ordeals.Ordeal> ordeals(Colossi.Site s) { return java.util.Collections.emptyList(); }

    static int r(double v) { return (int) Math.round(v); }

    // ---- the garrisons of the colossi of 2026-10-04 -------------------------------------------------------------------------
    /**
     * Every hostile creature of the Nether (owner, 2026-10-04: "there should be all kinds of Nether mobs in them"): the
     * vanilla ones, the NetherEx ones, the GLM strongholds' {@link Fiends} and the colossi's {@link Dwellers}. Each colossus
     * of 2026-10-04 holds every one of them in its garrisons (tests/nether-colossi.test.cjs checks the drawn points).
     */
    static final String[] ROSTER = {
        // vanilla
        "blaze", "ghast", "magma_cube", "wither_skeleton", "zombie_pigman", "skeleton",
        // NetherEx
        "wight", "spinout", "salamander", "ember", "mogus", "spore", "spore_creeper", "coolmar_spider", "frost", "brute", "nethermite", "ghastling",
        // the GLM strongholds' creatures
        "infernal_knight", "ashbone_archer", "hellhound", "cinder_imp", "soul_wraith", "magma_hulk", "pyre_warden", "shade",
        "brimstone_spider", "pigman_berserker", "dread_rider", "charred_ghoul", "cinder_witch",
        // the colossi's and the Catacombs' creatures
        "mummy", "asp", "scarab", "tomb_guardian", "ember_legionnaire", "flame_adept", "royal_guard", "crypt_guard", "deep_crawler", "lost_soul"};

    /** The great fliers need open air (a hall or the cavern itself): a design places them itself, never {@link #complete}. */
    static final Set<String> OPEN_AIR = new LinkedHashSet<>(Arrays.asList("ghast", "ghastling"));

    /** A garrison point in a design's local frame (u, y, v) with its pack ("kind+kind", '!' first for an elite leader). */
    static final class Garrison {
        final int u, y, v; String pack;
        Garrison(int u, int y, int v, String pack) { this.u = u; this.y = y; this.v = v; this.pack = pack; }
    }

    static Garrison g(int u, int y, int v, String pack) { return new Garrison(u, y, v, pack); }

    /**
     * Completes a colossus's garrisons so that together they hold the whole {@link #ROSTER}: each kind still missing joins
     * the smallest pack (at most five kinds a pack, never the great fliers). Deterministic: the packs are taken in order.
     */
    static List<Garrison> complete(List<Garrison> gs) {
        Set<String> have = kinds(gs);
        List<String> missing = new ArrayList<>();
        for (String k : ROSTER) if (!have.contains(k) && !OPEN_AIR.contains(k)) missing.add(k);
        for (String k : missing) {
            Garrison best = null;
            for (Garrison x : gs) if (size(x.pack) < 5 && (best == null || size(x.pack) < size(best.pack))) best = x;
            if (best == null) break;
            best.pack = best.pack + "+" + k;
        }
        return gs;
    }

    /** The kinds named by a list of garrisons. */
    static Set<String> kinds(List<Garrison> gs) {
        Set<String> out = new LinkedHashSet<>();
        for (Garrison x : gs) for (String k : (x.pack.startsWith("!") ? x.pack.substring(1) : x.pack).split("\\+")) out.add(k);
        return out;
    }

    private static int size(String pack) { return pack.split("\\+").length; }

    /** Places every garrison point of a plan in the frame (clipped like every tile). */
    static void garrisons(Draw.Frame f, List<Garrison> gs) {
        for (Garrison x : gs) f.point(x.u, x.y, x.v, "garrison:" + x.pack);
    }
}
