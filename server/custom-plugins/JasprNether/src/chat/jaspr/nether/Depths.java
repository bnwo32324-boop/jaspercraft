package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static chat.jaspr.nether.Draw.b;

/**
 * The Endless Catacombs (owner, 2026-10-01: "an ancient, sprawling dungeon that goes on for miles"): one labyrinth under
 * the Nether's floor (y 9 to 22, below the lava sea and every other structure), 3,072 blocks square (nearly two miles a
 * side), on a twelve-block grid of corridors, rooms and halls.
 * <ul>
 *   <li>Straight highways every 192 blocks cross it; the two through the Heart run from its edges through the four
 *       Wardens' vaults, 480 blocks out, to the Heart's four gates.</li>
 *   <li>Rooms: crypts, store rooms, prisons, libraries, shrines, gas-filled tombs, flame-vented galleries, lava-channel
 *       halls, rooms whose floor only partly bears weight, lever puzzles; and pillared halls.</li>
 *   <li>Traps in the corridors: arrows, falling floors over pits of magma, rubble, flames, gas.</li>
 *   <li>Stairwells climb to the surface every 156 blocks or so, under ruined gatehouses.</li>
 *   <li>Deeper in (towards the Heart) the halls turn from old stone to ember brick to black stone, the loot grows richer
 *       and the dead grow stronger.</li>
 *   <li>The four Wardens (the Gaoler, the Bone Harrower, the Weeping Shade, the Rot Mother) each keep a key; the four
 *       keys open the Heart's gates, where the Hollow King waits on his throne; his treasury opens when he falls.</li>
 * </ul>
 * Everything is a pure function of the seed and the Heart's place, chunk-local, never drawn into land that existed
 * before it (see {@link History}) or under a structure that reaches down into its depth.
 */
final class Depths {
    static final int P = 12, HALF = 1536, HEART = 5, WARD = 40, WARD_R = 2;
    static final int FLOOR = 9, TOP = 22, BOTTOM = 5, CORR = 13, ROOM_TOP = 15, HALL_TOP = 18;
    static final String[] WARDENS = {"gaoler", "bone_harrower", "weeping_shade", "rot_mother"};          // north, east, south, west
    static final String[] KEYS = {"warden_key_gaol", "warden_key_ossuary", "warden_key_gallery", "warden_key_pits"};
    static final String[] VAULTS = {"The Gaol", "The Bone Harrow", "The Weeping Gallery", "The Rot Pits"};

    enum Kind { OUT, NODE, ROOM, HALL, STAIR, TRAP, PUZZLE, COLLAPSED, HEART, WARDEN }
    enum Room { CRYPT, STORE, PRISON, LIBRARY, SHRINE, GAS, FLAMES, LAVA, PATH }

    /** What the Catacombs give way to: structures reaching down into their depth, and (for a stairwell) any overhead. */
    interface Claims {
        boolean deep(int x0, int z0, int x1, int z1);
        default boolean above(int x0, int z0, int x1, int z1) { return deep(x0, z0, x1, z1); }
    }

    static final class Cell {
        final int i, j; Kind kind; Room room; int trap; int ai, aj; int warden = -1; double depth;
        boolean e, s;            // this cell's own links (east, south); west and north are the neighbours'
        List<Ordeals.Ordeal> ordeals;
        Cell(int i, int j) { this.i = i; this.j = j; }
    }

    final long seed;
    final int hi, hj, hx, hz;    // the Heart's cell and its centre block
    final int salt;
    private final History history;
    private final Claims claims;
    private final Map<Long, Cell> cache = new LinkedHashMap<Long, Cell>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Cell> e) { return size() > 8192; }
    };
    private final Map<Long, Boolean> sectors = new LinkedHashMap<Long, Boolean>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> e) { return size() > 8192; }
    };
    private final Map<Long, Boolean> claimed = new LinkedHashMap<Long, Boolean>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> e) { return size() > 8192; }
    };
    int drawnColumns, shafts, capped;

    Depths(long seed, int heartX, int heartZ, History history, Claims claims) {
        this.seed = seed;
        this.hi = Math.floorDiv(heartX, P); this.hj = Math.floorDiv(heartZ, P);
        this.hx = hi * P + 6; this.hz = hj * P + 6;
        this.salt = (int) (seed ^ (seed >>> 32)) ^ 0x44455054;
        this.history = history == null ? History.none() : history;
        this.claims = claims;
    }

    /**
     * Where the Heart may lie: the first of 96 places 1,300-3,300 blocks out whose Heart and vaults are new land and
     * clear of the keepout (a city, or a built structure reaching down into the Catacombs' depth).
     */
    static int[] chooseHeart(long seed, History h, Mega.Keepout cities) {
        java.util.Random r = new java.util.Random(seed ^ 0x4845415254L);
        for (int ring = 0; ring < 6; ring++) {
            double base = r.nextDouble() * Math.PI * 2;
            for (int k = 0; k < 16; k++) {
                double a = base + k * Math.PI / 8, dist = 1300 + ring * 360 + r.nextInt(200);
                int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
                if (h != null && h.anyOld(x - 80, z - 80, x + 80, z + 80)) continue;
                boolean vaultsNew = true;
                for (int[] o : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                    int vx = x + o[0] * WARD * P, vz = z + o[1] * WARD * P;
                    if (h != null && h.anyOld(vx - 36, vz - 36, vx + 36, vz + 36)) vaultsNew = false;
                    if (cities != null && cities.blocked(vx - 36, vz - 36, vx + 36, vz + 36)) vaultsNew = false;
                }
                if (!vaultsNew) continue;
                if (cities != null && cities.blocked(x - 70, z - 70, x + 70, z + 70)) continue;
                return new int[]{x, z};
            }
        }
        return new int[]{3200, 3200};
    }

    boolean inRegion(int x, int z) { return Math.abs(x - hx) <= HALF && Math.abs(z - hz) <= HALF; }

    /** Whether a block box overlaps the Heart (and its gates) or one of the four Wardens' vaults. */
    boolean heartOrVault(int x0, int z0, int x1, int z1) {
        if (x1 >= hx - 70 && x0 <= hx + 70 && z1 >= hz - 70 && z0 <= hz + 70) return true;
        for (int[] o : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            int vx = hx + o[0] * WARD * P, vz = hz + o[1] * WARD * P;
            if (x1 >= vx - 36 && x0 <= vx + 36 && z1 >= vz - 36 && z0 <= vz + 36) return true;
        }
        return false;
    }

    /** A 64-block sector is drawn only if none of its chunks is old land. */
    private boolean sectorOk(int x, int z) {
        long k = ((long) (x >> 6) << 32) ^ ((z >> 6) & 0xffffffffL);
        Boolean v = sectors.get(k);
        if (v == null) {
            int sx = (x >> 6) << 6, sz = (z >> 6) << 6;
            v = !history.anyOld(sx, sz, sx + 63, sz + 63);
            sectors.put(k, v);
        }
        return v;
    }

    /** A cell under a structure that reaches down into the Catacombs' depth is left solid. */
    private boolean cellClaimed(int i, int j) {
        if (claims == null) return false;
        long k = ((long) i << 32) ^ (j & 0xffffffffL);
        Boolean v = claimed.get(k);
        if (v == null) {
            v = claims.deep(i * P - 1, j * P - 1, i * P + P, j * P + P);
            claimed.put(k, v);
        }
        return v;
    }

    private int h(int i, int j, int what) { return Draw.hash(i, what, j, salt); }
    private double u(int i, int j, int what) { return (h(i, j, what) >>> 8) / (double) (1 << 24); }

    boolean highwayRow(int j) { return Math.floorMod(j - hj, 16) == 0; }      // runs along x
    boolean highwayCol(int i) { return Math.floorMod(i - hi, 16) == 0; }      // runs along z

    /** Which warden vault (0 north, 1 east, 2 south, 3 west) holds the cell, or -1. */
    int wardenOf(int i, int j) {
        int[][] c = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}};
        for (int k = 0; k < 4; k++) if (Math.abs(i - c[k][0]) <= WARD_R && Math.abs(j - c[k][1]) <= WARD_R) return k;
        return -1;
    }

    synchronized Cell cell(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        Cell c = cache.get(key);
        if (c != null) return c;
        c = new Cell(i, j);
        int x = i * P + 6, z = j * P + 6;
        c.depth = Math.max(Math.abs(x - hx), Math.abs(z - hz)) / (double) HALF;
        if (!inRegion(i * P, j * P) || !inRegion(i * P + P - 1, j * P + P - 1)) c.kind = Kind.OUT;
        else if (Math.abs(i - hi) <= HEART && Math.abs(j - hj) <= HEART) c.kind = Kind.HEART;
        else if ((c.warden = wardenOf(i, j)) >= 0) c.kind = Kind.WARDEN;
        else {
            int[] a = hallAnchor(i, j);
            if (a != null) { c.kind = Kind.HALL; c.ai = a[0]; c.aj = a[1]; }
            else if (!highwayRow(j) && !highwayCol(i) && u(i, j, 1) < 0.02) c.kind = Kind.COLLAPSED;
            else if (Math.floorMod(i - hi, 13) == 6 && Math.floorMod(j - hj, 13) == 6) c.kind = Kind.STAIR;
            else {
                double r = u(i, j, 2);
                if (r < 0.2) {
                    c.kind = Kind.ROOM;
                    double q = u(i, j, 3), deep = 1 - c.depth;
                    c.room = q < 0.24 + 0.16 * deep ? Room.CRYPT : q < 0.40 ? Room.STORE : q < 0.50 ? Room.PRISON : q < 0.60 ? Room.LIBRARY
                        : q < 0.68 ? Room.SHRINE : q < 0.76 ? Room.GAS : q < 0.84 ? Room.FLAMES : q < 0.92 ? Room.LAVA : Room.PATH;
                } else if (r < 0.28 && !highwayRow(j) && !highwayCol(i)) { c.kind = Kind.TRAP; c.trap = (int) (u(i, j, 4) * 5); }
                else if (r < 0.286) c.kind = Kind.PUZZLE;
                else c.kind = Kind.NODE;
            }
        }
        // own links: east and south, always open along the highways, never into a collapsed cell, into the Heart and the
        // vaults only on their axes
        c.e = link(c, i + 1, j, true);
        c.s = link(c, i, j + 1, false);
        cache.put(key, c);
        return c;
    }

    private int[] hallAnchor(int i, int j) {
        for (int di = 0; di <= 1; di++) for (int dj = 0; dj <= 1; dj++) {
            int ai = i - di, aj = j - dj;
            if (Math.floorMod(ai - hi, 2) != 0 || Math.floorMod(aj - hj, 2) != 0) continue;
            if (u(ai, aj, 5) >= 0.045) continue;
            boolean ok = true;
            for (int a = 0; a <= 1 && ok; a++) for (int bb = 0; bb <= 1 && ok; bb++) {
                int ci = ai + a, cj = aj + bb;
                if (Math.abs(ci - hi) <= HEART + 1 && Math.abs(cj - hj) <= HEART + 1) ok = false;
                if (wardenOf(ci, cj) >= 0 || wardenOf(ci - 1, cj) >= 0 || wardenOf(ci + 1, cj) >= 0 || wardenOf(ci, cj - 1) >= 0 || wardenOf(ci, cj + 1) >= 0) ok = false;
                if (!inRegion(ci * P, cj * P) || !inRegion(ci * P + P - 1, cj * P + P - 1)) ok = false;
            }
            if (ok) return new int[]{ai, aj};
        }
        return null;
    }

    private Kind rawKind(int i, int j) {
        if (!inRegion(i * P, j * P) || !inRegion(i * P + P - 1, j * P + P - 1)) return Kind.OUT;
        if (Math.abs(i - hi) <= HEART && Math.abs(j - hj) <= HEART) return Kind.HEART;
        if (wardenOf(i, j) >= 0) return Kind.WARDEN;
        if (hallAnchor(i, j) != null) return Kind.HALL;
        if (!highwayRow(j) && !highwayCol(i) && u(i, j, 1) < 0.02) return Kind.COLLAPSED;
        return Kind.NODE;
    }

    private boolean link(Cell c, int ni, int nj, boolean east) {
        Kind a = c.kind, bk = rawKind(ni, nj);
        if (a == Kind.OUT || bk == Kind.OUT || a == Kind.COLLAPSED || bk == Kind.COLLAPSED) return false;
        boolean aSpecial = a == Kind.HEART || a == Kind.WARDEN, bSpecial = bk == Kind.HEART || bk == Kind.WARDEN;
        if (aSpecial || bSpecial) {
            if (aSpecial && bSpecial) return false;        // inside a special region: its own drawing decides
            // only the middle cell of each side of the Heart or a vault takes a link
            int si = aSpecial ? c.i : ni, sj = aSpecial ? c.j : nj;
            int ci, cj;
            Kind sk = aSpecial ? a : bk;
            if (sk == Kind.HEART) { ci = hi; cj = hj; }
            else { int w = wardenOf(si, sj); int[][] cc = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}}; ci = cc[w][0]; cj = cc[w][1]; }
            return east ? sj == cj : si == ci;
        }
        if (a == Kind.HALL && bk == Kind.HALL) {
            int[] p = hallAnchor(c.i, c.j), q = hallAnchor(ni, nj);
            if (p != null && q != null && p[0] == q[0] && p[1] == q[1]) return true;
        }
        if (east ? highwayRow(c.j) : highwayCol(c.i)) return true;
        return u(c.i, c.j, east ? 6 : 7) < 0.58;
    }

    private boolean west(Cell c) { return cell(c.i - 1, c.j).e; }
    private boolean north(Cell c) { return cell(c.i, c.j - 1).s; }

    // ---- the shape: the top of the open space in a column (or -1) -------------------------------------------------------
    int top(int x, int z) {
        if (!inRegion(x, z) || !sectorOk(x, z)) return -1;
        int i = Math.floorDiv(x, P), j = Math.floorDiv(z, P), lx = x - i * P, lz = z - j * P;
        Cell c = cell(i, j);
        if (c.kind != Kind.HEART && c.kind != Kind.WARDEN && cellClaimed(i, j)) return -1;
        switch (c.kind) {
            case OUT: case COLLAPSED: return -1;
            case HEART: return heartTop(x - hx, z - hz);
            case WARDEN: return wardenTop(c.warden, x, z);
            case HALL: {
                int px = x - c.ai * P, pz = z - c.aj * P;
                if (px >= 1 && px <= 2 * P - 2 && pz >= 1 && pz <= 2 * P - 2) return HALL_TOP;
                return arms(c, lx, lz, true);
            }
            case ROOM: case STAIR: case PUZZLE:
                if (lx >= 1 && lx <= 10 && lz >= 1 && lz <= 10) return c.kind == Kind.STAIR && lx >= 4 && lx <= 8 && lz >= 4 && lz <= 8 ? TOP : ROOM_TOP;
                return arms(c, lx, lz, true);
            default:
                if (lx >= 5 && lx <= 7 && lz >= 5 && lz <= 7) return CORR;
                return arms(c, lx, lz, false);
        }
    }

    /** The corridor arms of a cell: from its middle (or its room's wall when walled) out to each open link. */
    private int arms(Cell c, int lx, int lz, boolean walled) {
        boolean midZ = lz >= 5 && lz <= 7, midX = lx >= 5 && lx <= 7;
        if (midZ && lx >= (walled ? 11 : 8) && c.e) return CORR;
        if (midZ && lx <= (walled ? 0 : 4) && west(c)) return CORR;
        if (midX && lz >= (walled ? 11 : 8) && c.s) return CORR;
        if (midX && lz <= (walled ? 0 : 4) && north(c)) return CORR;
        return -1;
    }

    /** The Heart: the gates, the processional ring, the kings' tombs, the inner gates (sealed) and the throne hall. */
    static int heartTop(int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az);
        if (m > 66) return -1;
        if ((ax <= 1 && az >= 56) || (az <= 1 && ax >= 56)) return CORR;                // the outer gates
        if (m > 62) return -1;
        if (m >= 56) return ROOM_TOP;                                                     // the processional ring
        if (m >= 46 && Math.min(ax, az) % 14 >= 3 && Math.min(ax, az) % 14 <= 10 && Math.min(ax, az) < 44) return ROOM_TOP;   // the kings' tombs
        if (m >= 41 && m <= 55 && (ax <= 1 || az <= 1)) return CORR;                       // the inner gates
        if (m <= 40) return 20;                                                            // the throne hall
        return -1;
    }

    /** A Warden's vault: a hall 53 blocks square with a door on each side. */
    int wardenTop(int w, int x, int z) {
        int[][] cc = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}};
        int dx = x - (cc[w][0] * P + 6), dz = z - (cc[w][1] * P + 6);
        int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az);
        if (m > 30) return -1;
        if ((ax <= 1 && az > 26) || (az <= 1 && ax > 26)) return CORR;
        if (m <= 26) return HALL_TOP + 1;
        return -1;
    }

    // ---- materials by depth -------------------------------------------------------------------------------------------
    /** 0 the Old Halls (stone), 1 the Ember Halls (nether brick), 2 the Black Halls (deepest). */
    int zone(int x, int z) {
        double d = Math.max(Math.abs(x - hx), Math.abs(z - hz)) / (double) HALF;
        return d > 0.6 ? 0 : d > 0.25 ? 1 : 2;
    }

    int wall(int x, int y, int z) {
        double q = Draw.rnd(x, y, z, salt + 11);
        switch (zone(x, z)) {
            case 0: return q < 0.18 ? b(98, 2) : q < 0.3 ? b(98, 1) : b(98, 0);
            case 1: return q < 0.2 ? b(215) : q < 0.24 ? b(112) : b(112);
            default: return q < 0.12 ? b(216, 0) : q < 0.3 ? b(159, 7) : b(159, 15);
        }
    }

    int floor(int x, int z) {
        switch (zone(x, z)) {
            case 0: return Draw.rnd(x, 9, z, salt + 13) < 0.2 ? b(4) : b(1, 6);
            case 1: return ((x + z) & 1) == 0 ? b(112) : b(215);
            default: return ((x + z) & 3) == 0 ? b(155, 0) : b(159, 15);
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------
    /** Draws the Catacombs inside d's box: the shell and the open space column by column, then the cells' furnishings. */
    void draw(Draw d) {
        if (Math.max(d.x0, hx - HALF) > Math.min(d.x1, hx + HALF) || Math.max(d.z0, hz - HALF) > Math.min(d.z1, hz + HALF)) return;
        int xa = Math.max(d.x0, hx - HALF), xb = Math.min(d.x1, hx + HALF), za = Math.max(d.z0, hz - HALF), zb = Math.min(d.z1, hz + HALF);
        int w = xb - xa + 3, len = zb - za + 3;
        int[] tops = new int[w * len];
        for (int x = xa - 1; x <= xb + 1; x++) for (int z = za - 1; z <= zb + 1; z++) tops[(x - xa + 1) * len + (z - za + 1)] = top(x, z);
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            int t = tops[(x - xa + 1) * len + (z - za + 1)];
            if (t >= 0) { open(d, x, z, t); continue; }
            int around = -1;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) around = Math.max(around, tops[(x - xa + 1 + dx) * len + (z - za + 1 + dz)]);
            if (around >= 0 && sectorOk(x, z)) shell(d, x, z, around);
        }
        // furnishings of every cell reaching the box (a hall, the Heart and a vault whole, once, by their main cell)
        java.util.Set<Long> done = new java.util.HashSet<>();
        int[][] cc = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}};
        for (int i = Math.floorDiv(xa, P); i <= Math.floorDiv(xb, P); i++) for (int j = Math.floorDiv(za, P); j <= Math.floorDiv(zb, P); j++) {
            Cell c = cell(i, j);
            if (c.kind == Kind.OUT || c.kind == Kind.COLLAPSED) continue;
            if (c.kind == Kind.HALL) c = cell(c.ai, c.aj);
            else if (c.kind == Kind.HEART) c = cell(hi, hj);
            else if (c.kind == Kind.WARDEN) c = cell(cc[c.warden][0], cc[c.warden][1]);
            if (!done.add(((long) c.i << 32) ^ (c.j & 0xffffffffL))) continue;
            if (!sectorOk(c.i * P + 6, c.j * P + 6)) continue;
            if (c.kind != Kind.HEART && c.kind != Kind.WARDEN && cellClaimed(c.i, c.j)) continue;
            furnish(d, c);
        }
    }

    private void open(Draw d, int x, int z, int t) {
        if (!d.in(x, z)) return;
        for (int y = BOTTOM; y < FLOOR; y++) if (!Blocks.isFullSolid(d.id(x, y, z))) d.set(x, y, z, b(87));
        d.set(x, FLOOR, z, floor(x, z));
        for (int y = FLOOR + 1; y <= t; y++) d.set(x, y, z, 0);
        if (t < TOP) {
            d.set(x, t + 1, z, wall(x, t + 1, z));
            if (t + 2 <= TOP && !Blocks.isFullSolid(d.id(x, t + 2, z))) d.set(x, t + 2, z, b(87));
            if (Draw.rnd(x, 7, z, salt + 17) < (t >= HALL_TOP ? 0.012 : 0.006)) d.set(x, t + 1, z, b(89));
        }
        drawnColumns++;
    }

    private void shell(Draw d, int x, int z, int around) {
        if (!d.in(x, z)) return;
        for (int y = BOTTOM; y < FLOOR; y++) if (!Blocks.isFullSolid(d.id(x, y, z))) d.set(x, y, z, b(87));
        for (int y = FLOOR; y <= Math.min(TOP, around + 1); y++) d.set(x, y, z, wall(x, y, z));
        if (around + 2 <= TOP && !Blocks.isFullSolid(d.id(x, around + 2, z))) d.set(x, around + 2, z, b(87));
    }

    private void furnish(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR + 1;
        switch (c.kind) {
            case NODE:
                if (u(c.i, c.j, 20) < 0.05) d.skull(x0 + 5, f, z0 + 5, 0, h(c.i, c.j, 21) & 15);
                if (u(c.i, c.j, 22) < 0.04) { d.set(x0 + 7, f, z0 + 7, b(30)); }
                if (u(c.i, c.j, 23) < 0.012) d.chest(x0 + 7, f, z0 + 5, Draw.WEST, table(c, false));
                if ((highwayRow(c.j) || highwayCol(c.i)) && Math.floorMod(c.i + c.j, 2) == 0) d.set(x0 + 6, CORR + 1, z0 + 6, b(89));
                if (u(c.i, c.j, 24) < 0.03 + 0.05 * (1 - c.depth)) d.point(x0 + 6, f, z0 + 6, garrison(c));
                break;
            case TRAP: trapMarks(d, c); break;
            case ROOM: room(d, c); break;
            case STAIR: stair(d, c); break;
            case PUZZLE: puzzle(d, c); break;
            case HALL: if (c.ai == c.i && c.aj == c.j) hall(d, c); break;
            case HEART: if (c.i == hi && c.j == hj) heart(d); break;
            case WARDEN: {
                int[][] cc = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}};
                if (c.i == cc[c.warden][0] && c.j == cc[c.warden][1]) vault(d, c.warden);
                break;
            }
            default:
        }
    }

    String table(Cell c, boolean rich) {
        double deep = 1 - c.depth;
        if (rich) return deep > 0.75 ? "jaspr:depths/deep" : "jaspr:depths/rich";
        return deep > 0.75 ? "jaspr:depths/rich" : deep > 0.4 ? "jaspr:depths/common" : "jaspr:depths/common";
    }

    // Owner 2026-10-05 ("I keep seeing the same structures over and over"): each hall's pack from several, by depth.
    private static final String[][] PACKS = {
        // the outer halls
        {"garrison:crypt_guard+deep_crawler", "garrison:crypt_guard+mummy", "garrison:wight+crypt_guard", "garrison:deep_crawler+brimstone_spider",
            "garrison:ashbone_archer+crypt_guard", "garrison:charred_ghoul+deep_crawler", "garrison:skeleton+crypt_guard+asp"},
        // the middle depths
        {"garrison:crypt_guard+charred_ghoul", "garrison:tomb_guardian+mummy", "garrison:wither_skeleton+crypt_guard", "garrison:lost_soul+charred_ghoul",
            "garrison:dread_rider+crypt_guard", "garrison:scarab+mummy+crypt_guard"},
        // the deep
        {"garrison:crypt_guard+deep_crawler+lost_soul", "garrison:soul_wraith+crypt_guard+lost_soul", "garrison:tomb_guardian+lost_soul+deep_crawler",
            "garrison:infernal_knight+crypt_guard+lost_soul", "garrison:royal_guard+crypt_guard+soul_wraith"}};

    String garrison(Cell c) {
        double deep = 1 - c.depth;
        String[] p = PACKS[deep > 0.75 ? 2 : deep > 0.4 ? 1 : 0];
        return p[Math.min(p.length - 1, (int) (u(c.i, c.j, 41) * p.length))];
    }

    private String spawnerMob(Cell c) {
        double deep = 1 - c.depth, q = u(c.i, c.j, 30);
        if (deep > 0.75) return q < 0.3 ? "lost_soul" : q < 0.55 ? "crypt_guard" : q < 0.8 ? "soul_wraith" : "tomb_guardian";
        if (deep > 0.4) return q < 0.3 ? "crypt_guard" : q < 0.55 ? "charred_ghoul" : q < 0.8 ? "deep_crawler" : "mummy";
        return q < 0.35 ? "crypt_guard" : q < 0.6 ? "deep_crawler" : q < 0.8 ? "wight" : "ashbone_archer";
    }

    private void trapMarks(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR;
        switch (c.trap) {
            case 0:   // arrow slits along the arms
                for (int k = 0; k <= 11; k += 3) { d.set(x0 + k, f + 2, z0 + 4, b(159, 15)); d.set(x0 + k, f + 2, z0 + 8, b(159, 15)); }
                break;
            case 1:   // a pit of magma under a floor of cracked brick, a ladder out
                for (int x = x0 + 5; x <= x0 + 7; x++) for (int z = z0 + 5; z <= z0 + 7; z++) {
                    d.set(x, f, z, b(98, 2));
                    for (int y = BOTTOM + 1; y < f; y++) d.set(x, y, z, 0);
                    d.set(x, BOTTOM, z, b(213));
                }
                for (int y = BOTTOM + 1; y < f; y++) d.set(x0 + 6, y, z0 + 5, b(65, Draw.SOUTH));
                d.set(x0 + 6, BOTTOM + 1, z0 + 4, b(98, 0));
                break;
            case 3: d.set(x0 + 6, f, z0 + 6, b(213)); d.set(x0 + 5, f, z0 + 7, b(213)); break;
            case 4: d.set(x0 + 5, f + 1, z0 + 5, b(30)); d.set(x0 + 7, f + 3, z0 + 7, b(30)); break;
            default: d.set(x0 + 6, CORR, z0 + 6, b(13)); break;
        }
    }

    private void room(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR + 1, deepRich = (1 - c.depth) > 0.6 ? 1 : 0;
        switch (c.room) {
            case CRYPT:
                for (int k = 0; k < 2; k++) {
                    int sx = x0 + 3 + k * 5;
                    d.box(sx, f, z0 + 3, sx + 1, f, z0 + 7, Draw.of(b(155, 0)));
                    d.box(sx, f + 1, z0 + 3, sx + 1, f + 1, z0 + 7, Draw.of(b(44, 7)));
                    d.skull(sx, f + 2, z0 + 3, 0, 0);
                }
                d.set(x0 + 1, f + 4, z0 + 1, b(30)); d.set(x0 + 10, f + 4, z0 + 10, b(30));
                d.spawner(x0 + 5, f, z0 + 9, spawnerMob(c));
                d.chest(x0 + 9, f, z0 + 2, Draw.WEST, table(c, deepRich > 0) + (u(c.i, c.j, 31) < 0.3 ? "!trap" : ""));
                break;
            case STORE:
                for (int k = 0; k < 6; k++) d.set(x0 + 1 + k, f, z0 + 10, u(c.i, c.j, 40 + k) < 0.5 ? b(17, 0) : b(5, 1));
                d.chest(x0 + 2, f, z0 + 2, Draw.SOUTH, table(c, false));
                if (u(c.i, c.j, 32) < 0.5) d.chest(x0 + 9, f, z0 + 2, Draw.SOUTH, table(c, false));
                break;
            case PRISON:
                for (int k = 0; k < 3; k++) {
                    int cx = x0 + 2 + k * 3;
                    d.box(cx - 1, f, z0 + 1, cx + 1, f + 3, z0 + 3, Draw.of(b(101)));
                    d.box(cx, f, z0 + 1, cx, f + 2, z0 + 2, Draw.AIR);
                    d.skull(cx, f, z0 + 1, 0, 8);
                }
                d.point(x0 + 6, f, z0 + 7, "garrison:crypt_guard+crypt_guard");
                d.chest(x0 + 10, f, z0 + 10, Draw.NORTH, table(c, false));
                break;
            case LIBRARY:
                for (int k = 1; k <= 10; k++) { d.box(x0 + k, f, z0 + 1, x0 + k, f + 3, z0 + 1, Draw.of(b(47))); d.box(x0 + k, f, z0 + 10, x0 + k, f + 3, z0 + 10, Draw.of(b(47))); }
                d.box(x0 + 1, f, z0 + 1, x0 + 1, f + 3, z0 + 10, Draw.of(b(47)));
                d.box(x0 + 5, f, z0 + 1, x0 + 7, f + 3, z0 + 1, Draw.AIR);
                d.chest(x0 + 6, f, z0 + 6, Draw.NORTH, "jaspr:depths/library");
                d.set(x0 + 6, ROOM_TOP, z0 + 6, b(89));
                break;
            case SHRINE:
                d.box(x0 + 4, f, z0 + 4, x0 + 7, f, z0 + 7, Draw.of(b(155, 0)));
                d.set(x0 + 5, f + 1, z0 + 5, b(87)); d.set(x0 + 5, f + 2, z0 + 5, b(51));
                d.set(x0 + 6, f + 1, z0 + 6, b(87)); d.set(x0 + 6, f + 2, z0 + 6, b(51));
                d.sign(x0 + 6, f + 1, z0 + 4, b(68, Draw.NORTH), "Here the old", "kings of Hell", "were laid down.", "Go no deeper.");
                d.chest(x0 + 9, f, z0 + 9, Draw.NORTH, table(c, true));
                break;
            case GAS:
                for (int k = 0; k < 4; k++) d.set(x0 + 2 + k * 2, f, z0 + 2 + (k & 1) * 6, b(165));
                d.chest(x0 + 6, f, z0 + 6, Draw.NORTH, table(c, true));
                d.sign(x0 + 6, f + 1, z0 + 1, b(68, Draw.SOUTH), "", "Hold your", "breath", "");
                break;
            case FLAMES:
                for (int x = x0 + 2; x <= x0 + 9; x += 3) for (int z = z0 + 2; z <= z0 + 9; z += 3) d.set(x, FLOOR, z, b(213));
                d.chest(x0 + 10, f, z0 + 10, Draw.NORTH, table(c, true));
                break;
            case LAVA:
                for (int x = x0 + 1; x <= x0 + 10; x++) for (int z = z0 + 5; z <= z0 + 6; z++) { d.set(x, FLOOR - 1, z, b(11)); d.set(x, FLOOR, z, b(11)); d.set(x, FLOOR - 2, z, b(87)); }
                for (int x = x0 + 5; x <= x0 + 7; x++) for (int z = z0 + 5; z <= z0 + 6; z++) d.set(x, FLOOR + 1, z, b(44, 6));
                d.chest(x0 + 2, f, z0 + 9, Draw.EAST, table(c, true));
                d.point(x0 + 9, f, z0 + 2, garrison(c));
                break;
            case PATH:
                for (int tx = 0; tx < 5; tx++) for (int tz = 0; tz < 5; tz++) {
                    boolean safe = tz == 0 || Ordeals.safeTile(tx, tz - 1, salt + c.i * 31 + c.j * 17);
                    for (int ex = 0; ex < 2; ex++) for (int ez = 0; ez < 2; ez++) {
                        int x = x0 + 1 + tx * 2 + ex, z = z0 + 1 + tz * 2 + ez;
                        d.set(x, FLOOR, z, safe ? b(1, 6) : b(98, 2));
                        d.set(x, FLOOR - 1, z, safe ? b(87) : b(213));
                    }
                }
                d.chest(x0 + 6, f, z0 + 10, Draw.NORTH, table(c, true));
                d.sign(x0 + 6, f + 1, z0 + 1, b(68, Draw.SOUTH), "Tread only on", "the smooth", "stones", "");
                break;
            default:
        }
    }

    private void puzzle(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR + 1;
        int[] order = puzzleOrder(c);
        for (int k = 0; k < 3; k++) d.set(x0 + 1, f + 1, z0 + 2 + k, b(69, 1));          // levers on the west wall, facing east
        String[] names = {"north", "middle", "south"};
        d.sign(x0 + 1, f + 2, z0 + 3, b(68, Draw.EAST), "The dead wake:", names[order[0]] + ", then", names[order[1]] + ", then", names[order[2]] + ".");
        // the alcove in the north-east corner, walled, its door sealed, its chest
        d.box(x0 + 8, f, z0 + 1, x0 + 8, ROOM_TOP, z0 + 3, Draw.of(b(98, 0)));
        d.box(x0 + 8, f, z0 + 4, x0 + 10, ROOM_TOP, z0 + 4, Draw.of(b(98, 0)));
        d.box(x0 + 8, f, z0 + 2, x0 + 8, f + 1, z0 + 2, Draw.of(b(98, 3)));            // the seal
        d.chest(x0 + 10, f, z0 + 1, Draw.SOUTH, table(c, true));
        d.set(x0 + 9, ROOM_TOP, z0 + 2, b(89));
    }

    int[] puzzleOrder(Cell c) {
        int[][] perms = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        return perms[Math.floorMod(h(c.i, c.j, 50), 6)];
    }

    /**
     * A stairwell. Its well up to the rock's roof (y 22) is drawn alike by every chunk that reaches it; above, it climbs
     * to the surface (drawn once, by the chunk whose box holds its middle, which reads where the surface is), or is
     * capped where there is none or a structure stands overhead.
     */
    private void stair(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, cx = x0 + 6, cz = z0 + 6;
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = FLOOR + 1; y <= TOP; y++) {
            if (y > ROOM_TOP) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) d.set(cx + dx, y, cz + dz, b(98, 0));
            d.set(cx, y, cz, b(98, 3));
            int[] q = ring[Math.floorMod(y, 8)];
            d.set(cx + q[0], y, cz + q[1], b(98, 0));
        }
        if (!d.inTiles(cx, cz)) return;
        // the sign on the pillar, on the side in the same population box as the middle (boxes start at x = 16k + 8);
        // y 10 is clear of the steps on both sides
        boolean east = Math.floorMod(cx - 8, 16) != 15;
        int sx = east ? cx + 1 : cx - 1;
        int surface = -1;
        for (int y = TOP + 8; y < 112; y++) {
            int at = d.id(cx, y, cz), below = d.id(cx, y - 1, cz), above = d.id(cx, y + 1, cz);
            if (at == 0 && above == 0 && Blocks.isFullSolid(below) && y >= 33) { surface = y; break; }
        }
        if (surface < 0 || (claims != null && claims.above(cx - 6, cz - 6, cx + 6, cz + 6))) {
            d.box(cx - 2, TOP + 1, cz - 2, cx + 2, TOP + 1, cz + 2, Draw.of(b(98, 0)));
            d.sign(sx, FLOOR + 1, cz, b(68, east ? Draw.EAST : Draw.WEST), "The way up", "has fallen in.", "", "");
            capped++;
            return;
        }
        d.sign(sx, FLOOR + 1, cz, b(68, east ? Draw.EAST : Draw.WEST), "Up to the", "surface", "", "");
        for (int y = TOP + 1; y < surface; y++) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                d.set(cx + dx, y, cz + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 3 ? b(98, 0) : 0);
            d.set(cx, y, cz, b(98, 3));
            int[] q = ring[Math.floorMod(y, 8)];
            d.set(cx + q[0], y, cz + q[1], b(98, 0));
        }
        // the gatehouse: four pillars, a lintel, a sign
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) d.box(cx + p[0], surface, cz + p[1], cx + p[0], surface + 4, cz + p[1], Draw.of(b(98, 0)));
        d.box(cx - 3, surface + 5, cz - 3, cx + 3, surface + 5, cz + 3, Draw.of(b(98, 2)));
        d.box(cx - 2, surface + 5, cz - 2, cx + 2, surface + 5, cz + 2, Draw.AIR);
        boolean north = Math.floorMod(cz - 8, 16) >= 3;        // the side in this chunk's box
        d.sign(cx, surface, north ? cz - 3 : cz + 3, b(63, north ? 8 : 0), "THE ENDLESS", "CATACOMBS", "the deep", "remembers");
        d.set(cx - 3, surface + 6, cz - 3, b(87)); d.set(cx - 3, surface + 7, cz - 3, b(51));
        shafts++;
    }

    private void hall(Draw d, Cell c) {
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR + 1;
        for (int px = 4; px <= 19; px += 5) for (int pz = 4; pz <= 19; pz += 5) d.box(x0 + px, f, z0 + pz, x0 + px, HALL_TOP, z0 + pz, Draw.of(wall(x0 + px, 12, z0 + pz)));
        int q = Math.floorMod(h(c.i, c.j, 60), 3);
        if (q == 0) { d.box(x0 + 10, f, z0 + 10, x0 + 13, f, z0 + 13, Draw.of(b(49))); d.box(x0 + 11, f, z0 + 11, x0 + 12, f, z0 + 12, Draw.of(b(11))); }
        else if (q == 1) { d.box(x0 + 11, f, z0 + 11, x0 + 12, f + 6, z0 + 12, Draw.of(b(216, 0))); d.skull(x0 + 11, f + 7, z0 + 11, 0, 0); }
        else { d.box(x0 + 9, f, z0 + 9, x0 + 14, f, z0 + 14, Draw.of(b(155, 0))); d.set(x0 + 11, f + 1, z0 + 11, b(87)); d.set(x0 + 11, f + 2, z0 + 11, b(51)); }
        d.point(x0 + 8, f, z0 + 15, garrison(c));
        d.chest(x0 + 2, f, z0 + 2, Draw.SOUTH, table(c, true));
        d.chest(x0 + 21, f, z0 + 21, Draw.NORTH, table(c, false));
        d.spawner(x0 + 21, f, z0 + 2, spawnerMob(c));
        for (int k = 0; k < 4; k++) d.set(x0 + 6 + k * 4, HALL_TOP + 1, z0 + 11, b(89));
    }

    /** The Heart: the Hollow King's hall, the tombs, the four gates (four keys), the throne, the sealed treasury. */
    private void heart(Draw d) {
        int f = FLOOR + 1;
        for (int px = -36; px <= 36; px += 9) for (int pz = -36; pz <= 36; pz += 9) {
            if (px == 0 || pz == 0) continue;
            d.box(hx + px, f, hz + pz, hx + px + 1, 20, hz + pz + 1, Draw.of(b(216, 0)));
            d.set(hx + px, 20, hz + pz, b(89));
        }
        // the throne at the south end, on a dais, the King's place before it
        for (int k = 0; k < 3; k++) d.box(hx - 8 + k, f + k, hz + 26 + k, hx + 8 - k, f + k, hz + 36, Draw.of(k == 2 ? b(49) : b(159, 15)));
        d.box(hx - 1, f + 3, hz + 34, hx + 1, f + 6, hz + 34, Draw.of(b(216, 0)));
        d.set(hx, f + 3, hz + 33, b(156, 2));
        d.skull(hx, f + 7, hz + 34, 0, 0);
        d.point(hx, f, hz + 22, "lord:hollow_king");
        d.point(hx - 12, f, hz, "garrison:crypt_guard+crypt_guard+lost_soul");
        d.point(hx + 12, f, hz, "garrison:crypt_guard+deep_crawler+lost_soul");
        // the treasury behind the throne, sealed until the King falls
        d.box(hx - 6, f - 1, hz + 37, hx + 6, f + 6, hz + 40, Draw.of(b(49)));
        d.box(hx - 5, f, hz + 38, hx + 5, f + 5, hz + 39, Draw.AIR);
        d.box(hx - 9, f, hz + 37, hx - 7, f + 3, hz + 39, Draw.AIR);
        d.box(hx - 6, f, hz + 38, hx - 6, f + 3, hz + 39, Draw.of(b(98, 3)));       // its seal
        for (int k = -4; k <= 4; k += 4) d.chest(hx + k, f, hz + 39, Draw.NORTH, "jaspr:depths/heart");
        // the inner gates: sealed until all four Wardens' keys are shown
        int[][] gates = {{0, -42}, {42, 0}, {0, 42}, {-42, 0}};
        for (int[] g : gates) {
            boolean ns = g[0] == 0;
            if (ns) d.box(hx - 1, f, hz + g[1], hx + 1, f + 3, hz + g[1], Draw.of(b(98, 3)));
            else d.box(hx + g[0], f, hz - 1, hx + g[0], f + 3, hz + 1, Draw.of(b(98, 3)));
        }
        for (int[] g : new int[][]{{0, -60}, {60, 0}, {0, 60}, {-60, 0}}) {
            int sx = hx + g[0], sz = hz + g[1];
            d.sign(sx + (g[0] == 0 ? 2 : 0), f + 2, sz + (g[0] == 0 ? 0 : 2), b(63, 0), "THE HEART", "Four keys", "open the way", "to the King");
        }
        // the kings' tombs between the ring and the hall
        for (int side = 0; side < 4; side++) for (int k = -1; k <= 1; k++) {
            int along = k * 14 + 6, out = 49;
            int tx = side == 0 ? hx + along : side == 1 ? hx + out : side == 2 ? hx - along : hx - out;
            int tz = side == 0 ? hz - out : side == 1 ? hz + along : side == 2 ? hz + out : hz - along;
            d.chest(tx, f, tz, Draw.NORTH, "jaspr:depths/deep");
            d.skull(tx + 1, f, tz, 0, side * 4);
        }
    }

    /** A Warden's vault: its arena, its theme, its point and its hoard. */
    private void vault(Draw d, int w) {
        int[][] cc = {{hi, hj - WARD}, {hi + WARD, hj}, {hi, hj + WARD}, {hi - WARD, hj}};
        int cx = cc[w][0] * P + 6, cz = cc[w][1] * P + 6, f = FLOOR + 1;
        switch (w) {
            case 0:   // the Gaol: cells round the walls, a pit in the middle
                for (int k = -24; k <= 24; k += 4) for (int side = -1; side <= 1; side += 2) {
                    if (Math.abs(k) <= 2) continue;
                    d.box(cx + k - 1, f, cz + side * 24 - 1, cx + k + 1, f + 4, cz + side * 24 + 1, Draw.of(b(101)));
                    d.box(cx + k, f, cz + side * 24, cx + k, f + 3, cz + side * 24, Draw.AIR);
                }
                d.box(cx - 6, FLOOR - 1, cz - 6, cx + 6, FLOOR - 1, cz + 6, Draw.of(b(88)));
                d.box(cx - 5, FLOOR, cz - 5, cx + 5, FLOOR, cz + 5, Draw.AIR);
                break;
            case 1:   // the Bone Harrow: pillars of bone, skulls on every one
                for (int px = -20; px <= 20; px += 8) for (int pz = -20; pz <= 20; pz += 8) {
                    if (Math.abs(px) < 6 && Math.abs(pz) < 6) continue;
                    d.box(cx + px, f, cz + pz, cx + px, HALL_TOP, cz + pz, Draw.of(b(216, 0)));
                    d.skull(cx + px, HALL_TOP + 1 - 1, cz + pz, 0, (px + pz) & 15);
                }
                break;
            case 2:   // the Weeping Gallery: soul sand pools and gravestones
                for (int px = -20; px <= 20; px += 5) for (int pz = -20; pz <= 20; pz += 5) {
                    if (Math.abs(px) < 6 && Math.abs(pz) < 6) continue;
                    if (((px + pz) & 7) == 0) d.box(cx + px, FLOOR, cz + pz, cx + px + 1, FLOOR, cz + pz + 1, Draw.of(b(88)));
                    else d.box(cx + px, f, cz + pz, cx + px, f + 1, cz + pz, Draw.of(b(98, 0)));
                }
                break;
            default:  // the Rot Pits: sunken pits webbed over
                for (int px = -18; px <= 18; px += 9) for (int pz = -18; pz <= 18; pz += 9) {
                    if (Math.abs(px) < 6 && Math.abs(pz) < 6) continue;
                    d.box(cx + px - 1, FLOOR - 1, cz + pz - 1, cx + px + 1, FLOOR, cz + pz + 1, Draw.of(b(88)));
                    d.set(cx + px, f + 2, cz + pz, b(30));
                }
        }
        for (int k = 0; k < 4; k++) d.set(cx - 18 + k * 12, HALL_TOP + 2, cz, b(89));
        d.point(cx, f, cz, "lord:" + WARDENS[w]);
        d.chest(cx + 22, f, cz + 22, Draw.NORTH, "jaspr:depths/warden");
        d.chest(cx - 22, f, cz - 22, Draw.SOUTH, "jaspr:depths/rich");
        d.point(cx + 14, f, cz - 14, "garrison:crypt_guard+crypt_guard+deep_crawler");
        d.sign(cx + 2, f, cz - 26, b(63, 8), VAULTS[w].toUpperCase().length() > 15 ? VAULTS[w].substring(4) : VAULTS[w], "Its Warden", "keeps a key", "of the Heart");
    }

    // ---- the ordeals around a place ---------------------------------------------------------------------------------------
    List<Ordeals.Ordeal> ordealsNear(int x, int z, int r) {
        if (!inRegion(x, z)) return Collections.emptyList();
        List<Ordeals.Ordeal> out = new ArrayList<>();
        for (int i = Math.floorDiv(x - r, P); i <= Math.floorDiv(x + r, P); i++) for (int j = Math.floorDiv(z - r, P); j <= Math.floorDiv(z + r, P); j++) {
            Cell c = cell(i, j);
            if (c.kind == Kind.OUT || c.kind == Kind.COLLAPSED || c.kind == Kind.NODE || c.kind == Kind.STAIR || c.kind == Kind.WARDEN) continue;
            if (!sectorOk(i * P + 6, j * P + 6)) continue;
            if (c.kind != Kind.HEART && cellClaimed(i, j)) continue;
            for (Ordeals.Ordeal o : ordeals(c)) if (o.near(x, z, r)) out.add(o);
        }
        return out;
    }

    private List<Ordeals.Ordeal> ordeals(Cell c) {
        if (c.ordeals != null) return c.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        int x0 = c.i * P, z0 = c.j * P, f = FLOOR + 1;
        switch (c.kind) {
            case TRAP:
                switch (c.trap) {
                    case 0: out.add(Ordeals.arrows(new int[]{x0, f, z0 + 5, x0 + 11, f + 2, z0 + 7}, new int[]{x0, f + 1, z0 + 4, x0 + 11, f + 2, z0 + 8})); break;
                    case 1: out.add(Ordeals.collapse(new int[]{x0 + 5, FLOOR, z0 + 5, x0 + 7, FLOOR, z0 + 7}, b(98, 2))); break;
                    case 2: out.add(Ordeals.rubble(new int[]{x0 + 5, f, z0 + 5, x0 + 7, f + 2, z0 + 7}, b(13))); break;
                    case 3: out.add(Ordeals.flames(new int[]{x0 + 4, f, z0 + 4, x0 + 8, f + 2, z0 + 8}, 70)); break;
                    default: out.add(Ordeals.gas(new int[]{x0 + 4, f, z0 + 4, x0 + 8, f + 3, z0 + 8}));
                }
                break;
            case ROOM:
                if (c.room == Room.GAS) out.add(Ordeals.gas(new int[]{x0 + 1, f, z0 + 1, x0 + 10, f + 4, z0 + 10}));
                else if (c.room == Room.FLAMES) out.add(Ordeals.flames(new int[]{x0 + 1, f, z0 + 1, x0 + 10, f + 2, z0 + 10}, 60));
                else if (c.room == Room.PATH) out.add(Ordeals.path(new int[]{x0 + 1, FLOOR, z0 + 3, x0 + 10, FLOOR, z0 + 10}, b(98, 2), salt + c.i * 31 + c.j * 17));
                else if (c.room == Room.CRYPT && u(c.i, c.j, 33) < 0.4) out.add(Ordeals.arrows(new int[]{x0 + 2, f, z0 + 2, x0 + 9, f + 2, z0 + 9}, new int[]{x0 + 1, f + 1, z0 + 1, x0 + 10, f + 2, z0 + 10}));
                break;
            case PUZZLE: {
                int[][] levers = {{x0 + 1, f + 1, z0 + 2}, {x0 + 1, f + 1, z0 + 3}, {x0 + 1, f + 1, z0 + 4}};
                out.add(Ordeals.levers("depths_levers", new int[]{x0 + 1, f, z0 + 1, x0 + 10, f + 4, z0 + 10}, levers, puzzleOrder(c),
                    new int[]{x0 + 8, f, z0 + 2, x0 + 8, f + 1, z0 + 2}, b(98, 3), "hollow_reliquary", new int[]{x0 + 1, f, z0 + 1, x0 + 10, f + 3, z0 + 10}));
                break;
            }
            case HEART:
                if (c.i == hi && c.j == hj) {
                    int[][] gates = {{0, -42}, {42, 0}, {0, 42}, {-42, 0}};
                    for (int[] g : gates) {
                        int[] box = g[0] == 0 ? new int[]{hx - 1, f, hz + g[1], hx + 1, f + 3, hz + g[1]} : new int[]{hx + g[0], f, hz - 1, hx + g[0], f + 3, hz + 1};
                        out.add(Ordeals.keySeal("depths_heart", box, b(98, 3), KEYS));
                    }
                    out.add(Ordeals.bossSeal("depths_treasury", new int[]{hx - 6, f, hz + 38, hx - 6, f + 3, hz + 39}, b(98, 3), "hollow_king"));
                    out.add(Ordeals.flames(new int[]{hx - 3, f, hz - 56, hx + 3, f + 2, hz - 50}, 80));
                    out.add(Ordeals.flames(new int[]{hx - 3, f, hz + 50, hx + 3, f + 2, hz + 56}, 80));
                }
                break;
            default:
        }
        c.ordeals = out;
        return out;
    }

    /** The cell kinds and room kinds in a square around the Heart (for the self-test and the offline check). */
    Map<String, Integer> census(int radiusCells) {
        Map<String, Integer> m = new java.util.TreeMap<>();
        for (int i = hi - radiusCells; i <= hi + radiusCells; i++) for (int j = hj - radiusCells; j <= hj + radiusCells; j++) {
            Cell c = cell(i, j);
            m.merge(c.kind == Kind.ROOM ? "room_" + c.room.name().toLowerCase(java.util.Locale.ROOT) : c.kind.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }
        return m;
    }
}
