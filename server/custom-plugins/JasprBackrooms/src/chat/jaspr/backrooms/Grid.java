package chat.jaspr.backrooms;

/**
 * A corridor maze on a square grid of junctions (the Maintenance Tunnels and the Electrical Corridors). Junction (i, j)
 * sits at (i * p + p / 2, j * p + p / 2); the corridor between two neighbouring junctions is open at random, and the
 * spine's corridors are always open: along the spine's row at each grid column, then up or down at the next column to
 * the next row. Near both ends of the level the spine keeps to the middle row and an antechamber around z = 0 is open,
 * so the maze always joins the entry vestibule and the arena's door.
 */
final class Grid {
    final Level lv;
    final long seed, salt;
    final int p, half;
    final double openH, openV;

    Grid(Level lv, long seed, long salt, int p, int half, double openH, double openV) {
        this.lv = lv; this.seed = seed; this.salt = salt; this.p = p; this.half = half; this.openH = openH; this.openV = openV;
    }

    int centre(int i) { return i * p + p / 2; }

    boolean inGrid(int i, int j) {
        int x = centre(i), z = centre(j);
        return x >= lv.entryEnd() + 3 && x < lv.arenaStart() - 3 && z > lv.zMin() + 6 && z < lv.zMax() - 6;
    }

    /** Distance from a column to the nearer end of the level's main space. */
    private int fromEnds(int x) { return Math.min(x - lv.entryEnd(), lv.arenaStart() - x); }

    /** The row the spine follows at grid column i: the middle row (0) near both ends. */
    int row(int i) {
        int x = centre(i);
        if (fromEnds(x) < 28) return 0;
        return Math.floorDiv((int) Math.round(lv.spineZ(seed, x)), p);
    }

    boolean edgeH(int i, int j) {
        if (!inGrid(i, j) || !inGrid(i + 1, j)) return false;
        return row(i) == j || Hash.unit(seed, salt, i, j) < openH;
    }

    boolean edgeV(int i, int j) {
        if (!inGrid(i, j) || !inGrid(i, j + 1)) return false;
        int a = row(i - 1), b = row(i);
        if (j >= Math.min(a, b) && j < Math.max(a, b)) return true;
        return Hash.unit(seed, salt + 1, i, j) < openV;
    }

    boolean junction(int i, int j) { return edgeH(i - 1, j) || edgeH(i, j) || edgeV(i, j - 1) || edgeV(i, j); }

    int links(int i, int j) { return (edgeH(i - 1, j) ? 1 : 0) + (edgeH(i, j) ? 1 : 0) + (edgeV(i, j - 1) ? 1 : 0) + (edgeV(i, j) ? 1 : 0); }

    /** The open antechamber near each end of the level (joins the vestibule to the middle row). */
    boolean antechamber(int x, int z) { return fromEnds(x) < 18 && Math.abs(z) <= p / 2 + half; }

    /** Whether (x, z) lies in an open corridor or junction (rooms are the level's own business). */
    boolean open(int x, int z) {
        if (antechamber(x, z)) return true;
        int m = p / 2, mx = Math.floorMod(x, p), mz = Math.floorMod(z, p), i = Math.floorDiv(x, p), j = Math.floorDiv(z, p);
        boolean bandH = Math.abs(mz - m) <= half, bandV = Math.abs(mx - m) <= half;
        if (bandH && bandV) return junction(i, j);
        if (bandH) return edgeH(mx < m ? i - 1 : i, j);
        if (bandV) return edgeV(i, mz < m ? j - 1 : j);
        return false;
    }
}
