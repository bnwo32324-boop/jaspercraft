package chat.jaspr.lairs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A lair drawn in its own local frame: u east (0..w-1), v south (0..d-1), y up from the lair's floor level (y = 0 is the
 * layer a player stands on; negative y digs into the ground). Pure: no Bukkit, so designs can be previewed offline
 * (Preview). The Stamper turns it, levels the land under the pad and writes it into the world chunk by chunk.
 */
final class Canvas {
    static final int YMIN = -24, YMAX = 56, H = YMAX - YMIN + 1;
    /** Nothing drawn here: the land (or the pad's clearing) stays. */
    static final int UNTOUCHED = 0;

    final int w, d;
    final long seed;
    private final int[] cells;            // state + 1, or 0 for untouched
    /** Per column: -1 = the land is left as it is; else the pad: land levelled to y = 0 and cleared up to this height. */
    final int[] pad;

    // ---- what the runtime needs
    static final int CHEST = 0, SPAWNER = 1, SIGN = 2;
    static final class Tile {
        final int u, y, v, kind; final String what; final boolean vault;
        Tile(int u, int y, int v, int kind, String what, boolean vault) { this.u = u; this.y = y; this.v = v; this.kind = kind; this.what = what; this.vault = vault; }
    }
    static final class Point {
        final int u, y, v; final String pack;
        Point(int u, int y, int v, String pack) { this.u = u; this.y = y; this.v = v; this.pack = pack; }
    }
    final List<Tile> tiles = new ArrayList<>();
    final List<Point> garrisons = new ArrayList<>();
    /** Blocks that seal the vault until the boss falls (opened to air). */
    final List<int[]> seals = new ArrayList<>();
    /** The vault room (inclusive local box u0, y0, v0, u1, y1, v1): unbreakable and closed while the boss lives. */
    int[] vault;
    int[] boss;
    /** Arena radius around the boss point: a player inside wakes the boss. */
    int wake = 20;

    Canvas(int w, int d, long seed) {
        if (w < 8 || d < 8 || w > 80 || d > 80) throw new IllegalArgumentException("lair size " + w + "x" + d);
        this.w = w; this.d = d; this.seed = seed;
        cells = new int[w * d * H];
        pad = new int[w * d];
        java.util.Arrays.fill(pad, -1);
    }

    // ---------------------------------------------------------------- raw access

    boolean in(int u, int y, int v) { return u >= 0 && u < w && v >= 0 && v < d && y >= YMIN && y <= YMAX; }
    private int at(int u, int y, int v) { return ((y - YMIN) * d + v) * w + u; }

    void set(int u, int y, int v, int state) { if (in(u, y, v)) cells[at(u, y, v)] = state + 1; }
    /** Only where nothing has been drawn yet. */
    void soft(int u, int y, int v, int state) { if (in(u, y, v) && cells[at(u, y, v)] == 0) cells[at(u, y, v)] = state + 1; }
    /** The drawn state, -1 when untouched. */
    int get(int u, int y, int v) { return in(u, y, v) ? cells[at(u, y, v)] - 1 : -1; }
    boolean drawn(int u, int y, int v) { return in(u, y, v) && cells[at(u, y, v)] != 0; }
    void clear(int u, int y, int v) { if (in(u, y, v)) cells[at(u, y, v)] = 0; }

    /** Highest drawn y of any column (for the admission's height box). */
    int top() {
        for (int y = YMAX; y >= YMIN; y--) for (int v = 0; v < d; v++) for (int u = 0; u < w; u++) if (cells[at(u, y, v)] != 0) return y;
        return 0;
    }
    int bottom() {
        for (int y = YMIN; y <= YMAX; y++) for (int v = 0; v < d; v++) for (int u = 0; u < w; u++) if (cells[at(u, y, v)] != 0) return y;
        return 0;
    }

    // ---------------------------------------------------------------- stable randomness

    double unit(int u, int y, int v, int salt) {
        long x = seed ^ (u * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (v * 0x165667B19E3779F9L) ^ (salt * 0xD6E8FEB86659FD93L);
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L; x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL; x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }
    boolean chance(int u, int y, int v, double p, int salt) { return unit(u, y, v, salt) < p; }

    /** A material chosen per position from weighted options {state, weight, state, weight ...}. */
    interface Mat { int at(Canvas c, int u, int y, int v); }
    static Mat of(int state) { return (c, u, y, v) -> state; }
    static Mat mix(int... pairs) {
        int total = 0;
        for (int i = 1; i < pairs.length; i += 2) total += pairs[i];
        final int sum = total;
        return (c, u, y, v) -> {
            double r = c.unit(u, y, v, 77) * sum;
            for (int i = 0; i + 1 < pairs.length; i += 2) if ((r -= pairs[i + 1]) < 0) return pairs[i];
            return pairs[pairs.length - 2];
        };
    }

    // ---------------------------------------------------------------- shapes

    void fill(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
                for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) set(u, y, v, m.at(this, u, y, v));
    }
    void fill(int u0, int y0, int v0, int u1, int y1, int v1, int state) { fill(u0, y0, v0, u1, y1, v1, of(state)); }
    void air(int u0, int y0, int v0, int u1, int y1, int v1) { fill(u0, y0, v0, u1, y1, v1, M.AIR); }

    /** Four walls of a box (no floor or ceiling). */
    void walls(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) {
        for (int y = y0; y <= y1; y++) {
            for (int u = u0; u <= u1; u++) { set(u, y, v0, m.at(this, u, y, v0)); set(u, y, v1, m.at(this, u, y, v1)); }
            for (int v = v0; v <= v1; v++) { set(u0, y, v, m.at(this, u0, y, v)); set(u1, y, v, m.at(this, u1, y, v)); }
        }
    }
    void walls(int u0, int y0, int v0, int u1, int y1, int v1, int state) { walls(u0, y0, v0, u1, y1, v1, of(state)); }

    /** A room: walls, floor at y0, ceiling at y1, air inside. */
    void room(int u0, int y0, int v0, int u1, int y1, int v1, Mat wall, Mat floor, Mat ceiling) {
        fill(u0, y0, v0, u1, y0, v1, floor);
        if (ceiling != null) fill(u0, y1, v0, u1, y1, v1, ceiling);
        walls(u0, y0 + 1, v0, u1, y1 - (ceiling != null ? 1 : 0), v1, wall);
        air(u0 + 1, y0 + 1, v0 + 1, u1 - 1, y1 - (ceiling != null ? 1 : 0), v1 - 1);
    }

    /** Filled (or hollow, shell thickness 1) vertical cylinder of radius r around (cu, cv). */
    void cylinder(double cu, double cv, double r, int y0, int y1, Mat m, boolean hollow) {
        for (int v = (int) Math.floor(cv - r); v <= (int) Math.ceil(cv + r); v++)
            for (int u = (int) Math.floor(cu - r); u <= (int) Math.ceil(cu + r); u++) {
                double dist = Math.hypot(u - cu, v - cv);
                if (dist > r + 0.01) continue;
                if (hollow && dist < r - 1.0) continue;
                for (int y = y0; y <= y1; y++) set(u, y, v, m.at(this, u, y, v));
            }
    }
    void disc(double cu, double cv, double r, int y, Mat m) { cylinder(cu, cv, r, y, y, m, false); }
    void ring(double cu, double cv, double r, int y, Mat m) { cylinder(cu, cv, r, y, y, m, true); }

    /** Ellipsoid (or its shell) centred at (cu, cy, cv) with radii ru, ry, rv; only y >= yCut when given. */
    void ellipsoid(double cu, double cy, double cv, double ru, double ry, double rv, Mat m, boolean hollow, int yCut) {
        for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
            if (y < yCut) continue;
            for (int v = (int) Math.floor(cv - rv); v <= (int) Math.ceil(cv + rv); v++)
                for (int u = (int) Math.floor(cu - ru); u <= (int) Math.ceil(cu + ru); u++) {
                    double k = sq((u - cu) / ru) + sq((y - cy) / ry) + sq((v - cv) / rv);
                    if (k > 1.0) continue;
                    if (hollow) {
                        double inner = sq((u - cu) / Math.max(0.5, ru - 1)) + sq((y - cy) / Math.max(0.5, ry - 1)) + sq((v - cv) / Math.max(0.5, rv - 1));
                        if (inner < 1.0) continue;
                    }
                    set(u, y, v, m.at(this, u, y, v));
                }
        }
    }
    static double sq(double x) { return x * x; }

    /** A one-block line between two points (inclusive). */
    void line(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) {
        int n = Math.max(Math.abs(u1 - u0), Math.max(Math.abs(y1 - y0), Math.abs(v1 - v0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            int u = (int) Math.round(u0 + (u1 - u0) * t), y = (int) Math.round(y0 + (y1 - y0) * t), v = (int) Math.round(v0 + (v1 - v0) * t);
            set(u, y, v, m.at(this, u, y, v));
        }
    }
    /** A thick line (a ball of radius r swept along it). */
    void beam(double u0, double y0, double v0, double u1, double y1, double v1, double r, Mat m) {
        double len = Math.sqrt(sq(u1 - u0) + sq(y1 - y0) + sq(v1 - v0));
        int n = Math.max(1, (int) Math.ceil(len * 2));
        int ir = (int) Math.ceil(r);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, cu = u0 + (u1 - u0) * t, cy = y0 + (y1 - y0) * t, cv = v0 + (v1 - v0) * t;
            for (int y = (int) Math.floor(cy) - ir; y <= (int) Math.ceil(cy) + ir; y++)
                for (int v = (int) Math.floor(cv) - ir; v <= (int) Math.ceil(cv) + ir; v++)
                    for (int u = (int) Math.floor(cu) - ir; u <= (int) Math.ceil(cu) + ir; u++)
                        if (sq(u - cu) + sq(y - cy) + sq(v - cv) <= r * r + 0.01) set(u, y, v, m.at(this, u, y, v));
        }
    }

    /** Removes drawn blocks (not air) with the given chance per block: ruin. */
    void decay(int u0, int y0, int v0, int u1, int y1, int v1, double p, int salt) {
        for (int y = y0; y <= y1; y++) for (int v = v0; v <= v1; v++) for (int u = u0; u <= u1; u++) {
            int s = get(u, y, v);
            if (s > 0 && M.id(s) != 54 && M.id(s) != 52 && chance(u, y, v, p, salt)) set(u, y, v, M.AIR);
        }
    }

    /** Scatters a state on top of solid drawn floor (at y where y-1 is solid and y is air or untouched). */
    void scatter(int u0, int v0, int u1, int v1, int y, double p, Mat m, int salt) {
        for (int v = v0; v <= v1; v++) for (int u = u0; u <= u1; u++) {
            int below = get(u, y - 1, v), here = get(u, y, v);
            if (below <= 0 || M.id(below) == 9 || (here > 0)) continue;
            if (!chance(u, y, v, p, salt)) continue;
            int s = m.at(this, u, y, v);
            if (plantable(M.id(s), M.id(below))) set(u, y, v, s);
        }
    }
    static boolean plant(int id) { return id == 6 || id == 31 || id == 32 || id == 37 || id == 38 || id == 39 || id == 40 || id == 175; }
    /** Whether a plant can stand on that block (other things stand anywhere). */
    static boolean plantable(int plant, int ground) {
        switch (plant) {
            case 31: case 37: case 38: case 6: return ground == 2 || ground == 3;
            case 32: return ground == 2 || ground == 3 || ground == 12 || ground == 159 || ground == 172;
            case 39: case 40: return ground == 2 || ground == 3 || ground == 110;
            case 86: case 103: return ground != 0;
            default: return true;
        }
    }

    // ---------------------------------------------------------------- the pad

    void pad(int u0, int v0, int u1, int v1, int clear) {
        for (int v = Math.max(0, v0); v <= Math.min(d - 1, v1); v++)
            for (int u = Math.max(0, u0); u <= Math.min(w - 1, u1); u++) pad[v * w + u] = Math.max(pad[v * w + u], clear);
    }
    void padCircle(double cu, double cv, double r, int clear) {
        for (int v = 0; v < d; v++) for (int u = 0; u < w; u++)
            if (Math.hypot(u - cu, v - cv) <= r) pad[v * w + u] = Math.max(pad[v * w + u], clear);
    }
    int pad(int u, int v) { return u < 0 || v < 0 || u >= w || v >= d ? -1 : pad[v * w + u]; }

    // ---------------------------------------------------------------- tiles and points

    void chest(int u, int y, int v, int dir, String loot) { set(u, y, v, M.chest(dir)); tiles.add(new Tile(u, y, v, CHEST, loot, false)); }
    void vaultChest(int u, int y, int v, int dir, String loot) { set(u, y, v, M.chest(dir)); tiles.add(new Tile(u, y, v, CHEST, loot, true)); }
    void spawner(int u, int y, int v, String entity) { set(u, y, v, M.SPAWNER); tiles.add(new Tile(u, y, v, SPAWNER, entity, false)); }
    /** A wall sign facing dir (attached to the block behind it); lines separated by '|'. */
    void sign(int u, int y, int v, int dir, String text) { set(u, y, v, M.wallSign(dir)); tiles.add(new Tile(u, y, v, SIGN, text, false)); }
    void seal(int u, int y, int v, int state) { set(u, y, v, state); seals.add(new int[]{u, y, v}); }
    void garrison(int u, int y, int v, String pack) { garrisons.add(new Point(u, y, v, pack)); }
    void boss(int u, int y, int v, int wakeRadius) { boss = new int[]{u, y, v}; wake = wakeRadius; }
    void vault(int u0, int y0, int v0, int u1, int y1, int v1) { vault = new int[]{Math.min(u0, u1), Math.min(y0, y1), Math.min(v0, v1), Math.max(u0, u1), Math.max(y0, y1), Math.max(v0, v1)}; }

    /** Local (u, v) after r clockwise quarter turns, in a footprint that becomes d x w for odd r. */
    static int ru(int u, int v, int w, int d, int r) {
        switch (r & 3) { case 1: return d - 1 - v; case 2: return w - 1 - u; case 3: return v; default: return u; }
    }
    static int rv(int u, int v, int w, int d, int r) {
        switch (r & 3) { case 1: return u; case 2: return d - 1 - v; case 3: return w - 1 - u; default: return v; }
    }
    int width(int r) { return (r & 1) == 0 ? w : d; }
    int depth(int r) { return (r & 1) == 0 ? d : w; }

    /** Sanity of a finished drawing (Preview and the tests call it): a boss point, a vault, its chests. */
    List<String> problems() {
        List<String> out = new ArrayList<>();
        if (boss == null) out.add("no boss point");
        if (vault == null) out.add("no vault");
        int vaultChests = 0, chests = 0;
        for (Tile t : tiles) if (t.kind == CHEST) { chests++; if (t.vault) vaultChests++; }
        if (vaultChests == 0) out.add("no vault chest");
        if (chests < 2) out.add("fewer than two chests");
        if (garrisons.isEmpty()) out.add("no garrison");
        if (seals.isEmpty()) out.add("no seal");
        if (boss != null && !in(boss[0], boss[1], boss[2])) out.add("boss point outside");
        if (boss != null && (get(boss[0], boss[1], boss[2]) > 0 || get(boss[0], boss[1] + 1, boss[2]) > 0)) out.add("boss point not open");
        if (boss != null && get(boss[0], boss[1] - 1, boss[2]) <= 0 && pad(boss[0], boss[2]) < 0) out.add("boss point has no floor");
        for (Tile t : tiles) if (t.kind == SIGN) {
            String[] lines = t.what.split("\\|");
            if (lines.length > 4) out.add("sign with " + lines.length + " lines");
            for (String l : lines) if (l.length() > 15) out.add("sign line too long: " + l);
        }
        return out;
    }

    Random random() { return new Random(seed); }
}
