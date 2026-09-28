package chat.jaspr.nether;

import java.util.Random;

/**
 * One of the five mega structures: its layout (planned once per site from the site's Random), its cavern's floor,
 * lakes and dressing, and the structure itself, drawn clip by clip. Every method must be deterministic and may read
 * the canvas only in the column it is writing.
 */
abstract class MegaDesign {
    /** Layout chosen once per site (the same object every chunk sees). */
    abstract Object plan(Mega.Site s, Random r);

    /** Draws the part of the structure inside d's box (after the cavern). */
    abstract void draw(Mega.Site s, Draw d);

    /** The cavern floor's top block at (x, y, z). */
    abstract int ground(Mega.Site s, int x, int y, int z);

    /** What fills the gaps under the cavern floor. */
    abstract Draw.Mat under();

    /** Columns where the cavern floor never holds a lake (the structure's own footprint). */
    boolean dry(Mega.Site s, int x, int z) { return false; }

    /** Lake noise threshold: higher means fewer lakes. */
    double lakeLevel() { return 0.34; }

    int lakeLiquid() { return Blocks.LAVA << 4; }

    /** Per-column cavern dressing (floor scatter, ceiling hangings); called after the column is carved. */
    void dress(Mega.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {}

    // ---- shared helpers ------------------------------------------------------------------------------------------
    static int r(double v) { return (int) Math.round(v); }

    /** Hangs a glowstone cluster from the ceiling at (x, ceil, z) when the column's hash picks it. */
    static void glowHang(Draw d, int x, int z, int ceil, int salt, double share) {
        if (Draw.rnd(x, 0, z, salt) >= share || !Blocks.isFullSolid(d.id(x, ceil + 1, z))) return;
        int len = 1 + (int) (Draw.rnd(x, 1, z, salt) * 3);
        for (int k = 0; k < len; k++) d.set(x, ceil - k, z, Blocks.GLOWSTONE << 4);
    }

    /** A stalactite of v hanging from the ceiling (when solid) and a matching stalagmite, each 1-5 long. */
    static void spikes(Draw d, int x, int z, int floor, int ceil, int v, int salt, double share) {
        double p = Draw.rnd(x, 2, z, salt);
        if (p < share && Blocks.isFullSolid(d.id(x, ceil + 1, z))) {
            int len = 1 + (int) (Draw.rnd(x, 3, z, salt) * 5);
            for (int k = 0; k < len && ceil - k > floor + 3; k++) d.set(x, ceil - k, z, v);
        } else if (p < share * 2) {
            int len = 1 + (int) (Draw.rnd(x, 4, z, salt) * 4);
            for (int k = 1; k <= len && floor + k < ceil - 3; k++) d.set(x, floor + k, z, v);
        }
    }
}
