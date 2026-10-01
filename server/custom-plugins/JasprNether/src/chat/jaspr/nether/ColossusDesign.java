package chat.jaspr.nether;

import java.util.Random;

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
}
