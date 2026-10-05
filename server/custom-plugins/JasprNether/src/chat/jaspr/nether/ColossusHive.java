package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Sporefather's Hive (owner, 2026-10-04: "Add 10 new structures to the Nether ... I want them to be huge
 * structures"): a forest-city of colossal fungi inside a palisade of mushroom stalks. Twelve giant mushrooms, thirty to
 * seventy blocks tall (red domes with white spots, flat brown umbrellas, crimson wart-blobs hung with vines, tiered elder
 * mushrooms), each with a hollow stem and a spiral stair inside, a gill gallery under its cap and a platform on top; rope
 * bridges of stalk run between the caps and to the crown of the spore chimney. At the centre stands the Hive Dome, a
 * puffball sixty blocks across:
 * <ul>
 *   <li>its ground floor: four gates, the pillared atrium round the spore font, and four bays: the Spore Vats (their
 *       fumes poison), the Brood Chamber, the Nursery and the Mycelium Garden;</li>
 *   <li>the floor above: the Hall of Gills round the Throne of the Sporefather, where he waits; his fall opens the seal
 *       behind the throne, to the Spore Heart (the vault);</li>
 *   <li>under the top: the Bellows, and the spore chimney rising through the dome to its crown under the cavern's
 *       roof.</li>
 * </ul>
 * Outside: the Spore Gate and the road to the dome, the Spore Fields of puffballs, the Mushroom Farm and the Rotting
 * Larder; ghasts drift between the caps. Rotten patches on three caps give way into the galleries below.
 */
final class ColossusHive extends ColossusDesign {
    // ---- measures (local frame: u east and v south before the plan's turn; the Spore Gate is at -v) -----------------------
    static final int DR = 30, DRY = 32, DCY = 42, RI = 27, RIY = 29;   // the Hive Dome: an ellipsoid sat on the floor (top at 74)
    static final int L0 = 33, L1 = 47, L2 = 60;                     // its floors (the floor blocks)
    static final int WALL_R = 138;                                  // the Fungal Wall
    static final int RED = 0, BROWN = 1, WART = 2, ELDER = 3;
    static final int FARM_U = -74, FARM_V = 88, LARDER_U = -38, LARDER_V = -105, CACHE_U = 112, CACHE_V = -14;
    static final String T = "jaspr:colossus/hive", RICH = T + "_rich", VAULT = T + "_vault";
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    /** A giant mushroom: centre, kind, stem and cap radii, the cap's top at the axis, the stem's floor, the spiral's phase. */
    static final class Shroom {
        final int u, v, kind, stem, cap, top, base, phase;
        Shroom(int u, int v, int kind, int stem, int cap, int top, int base, int phase) {
            this.u = u; this.v = v; this.kind = kind; this.stem = stem; this.cap = cap; this.top = top; this.base = base; this.phase = phase;
        }
        /** The gill gallery's floor, under the (lower) cap. */
        int gallery() { return kind == ELDER ? top - 17 : kind == WART ? top - 11 : top - 9; }
        int upper() { return (int) Math.round(cap * 0.55); }
        /** How far from the axis a bridge lands on the cap's top. */
        double reach() { return Math.max(stem + 0.6, kind == BROWN ? cap - 3 : kind == ELDER ? upper() * 0.42 : kind == WART ? cap * 0.3 : cap * 0.42); }
        /** The top surface's height at r from the axis (the elder's crown for the elder). */
        int surface(double r) { return kind == ELDER ? capTop(RED, upper(), top, r) : capTop(kind, cap, top, r); }
    }

    static final class Plan {
        int rot, chTop, farmY, larderY, gateY, cacheY;
        final List<Shroom> shrooms = new ArrayList<>();
        final List<int[]> bridges = new ArrayList<>();          // shroom a, shroom b (-1: the chimney's crown)
        final List<int[]> puffs = new ArrayList<>();            // u, v, radius
        final List<int[]> patches = new ArrayList<>();          // shroom, centre u, centre v: rotten patches on cap tops
        final List<int[]> fillers = new ArrayList<>();          // u, v, stem radius, cap radius, height, kind: the lesser fungi
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette (the mod stand-ins are read from Blocks at draw time) -------------------------------------------------
    private static final int STEM = b(99, 10), STEM_ALL = b(99, 15), PORES = b(99, 0), BROWN_CAP = b(99, 14), RED_CAP = b(100, 14), RED_PORES = b(100, 0);
    private static final int MYCEL = b(110), WART_B = b(214), SOUL = b(88), GLOW = b(89), BONE = b(216, 0), WEB = b(30), CAULDRON = b(118);
    private static final int LIME_GLASS = b(95, 5), GREEN_GLASS = b(95, 13), GREEN_T = b(159, 13), BROWN_T = b(159, 12), RED_T = b(159, 14), PODZOL = b(3, 2);
    private static final int WOOL_RED = b(35, 14), WOOL_BROWN = b(35, 12), WART_CROP = b(115, 3), SPRUCE_FENCE = b(188), SPRUCE = b(5, 1), JACK = b(91, 2);
    private static final int BIRCH_SLAB_TOP = b(126, 10), SLAB_RNB = b(44, 6), CHISEL_Q = b(155, 1);
    private static final int BIRCHSTAIR = 135, LADDER = 65;

    private static Draw.Mat skin(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.1 ? BROWN_CAP : q < 0.14 ? WART_B : STEM_ALL; };
    }
    private static Draw.Mat flesh(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.3 ? RED_CAP : q < 0.36 ? GLOW : WART_B; };
    }

    // ---- plan -----------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        p.chTop = Math.min(104, s.ceilAt(s.x, s.z) - 9);
        p.farmY = floorAt(s, p.rot, FARM_U + 15, FARM_V - 6);
        p.larderY = floorAt(s, p.rot, LARDER_U, LARDER_V + 10);
        p.gateY = floorAt(s, p.rot, 0, -WALL_R);
        p.cacheY = floorAt(s, p.rot, CACHE_U - 7, CACHE_V);
        // the giant mushrooms: four great ones round the dome, five in the middle ring, three by the wall
        int[] kinds = {RED, BROWN, WART, ELDER};
        for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = kinds[i]; kinds[i] = kinds[j]; kinds[j] = t; }
        for (int i = 0; i < 4; i++) add(p, s, r, 45 + 90 * i + (r.nextDouble() - 0.5) * 14, 57 + r.nextInt(5), kinds[i], 8, 21 + r.nextInt(4), 66 + r.nextInt(9));
        for (int i = 0; i < 5; i++) add(p, s, r, 18 + 72 * i + (r.nextDouble() - 0.5) * 10, 93 + r.nextInt(6), r.nextInt(4), 7, 15 + r.nextInt(4), 50 + r.nextInt(13));
        int[] outer = {50, 190, 320};
        for (int i = 0; i < 3; i++) add(p, s, r, outer[i] + (r.nextDouble() - 0.5) * 12, 119 + r.nextInt(5), r.nextInt(3), 5, 11 + r.nextInt(3), 32 + r.nextInt(11));
        // bridges between neighbouring caps (never steeper than a stair, never through a third mushroom), and to the chimney
        int n = p.shrooms.size();
        int[] count = new int[n];
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            Shroom a = p.shrooms.get(i), c = p.shrooms.get(j);
            double d = Math.hypot(a.u - c.u, a.v - c.v), gap = d - a.cap - c.cap, run = d - a.reach() - c.reach();
            if (gap < 3 || gap > 38 || count[i] >= 4 || count[j] >= 4) continue;
            if (Math.abs(a.top - c.top) > 0.7 * run) continue;
            if (blocked(p, i, j, a.u, a.v, c.u, c.v)) continue;
            p.bridges.add(new int[]{i, j});
            count[i]++; count[j]++;
        }
        for (int i = 0; i < 4; i++) {
            Shroom a = p.shrooms.get(i);
            double run = Math.hypot(a.u, a.v) - a.reach() - 7;
            if (Math.abs(a.top - p.chTop) <= 0.7 * run) p.bridges.add(new int[]{i, -1});
        }
        // rotten patches on three of the great caps, over their galleries
        for (int i = 0; i < 4 && p.patches.size() < 3; i++) {
            Shroom m = p.shrooms.get(i);
            if (m.kind == WART) continue;
            double a = r.nextDouble() * 2 * Math.PI;
            p.patches.add(new int[]{i, m.u + (int) Math.round(Math.cos(a) * (m.stem + 3)), m.v + (int) Math.round(Math.sin(a) * (m.stem + 3))});
        }
        // puffballs: the Spore Fields east of the dome, and a few scattered between the stems
        for (int t = 0; t < 200 && p.puffs.size() < 30; t++) {
            boolean field = p.puffs.size() < 20;
            double a = field ? (r.nextDouble() - 0.5) * 0.75 : r.nextDouble() * 2 * Math.PI, rad = field ? 100 + r.nextDouble() * 32 : 40 + r.nextDouble() * 90;
            int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad), pr = 2 + r.nextInt(field ? 3 : 2);
            if (clear(p, u, v, pr + 3)) p.puffs.add(new int[]{u, v, pr});
        }
        // the forest between the giants: lesser fungi without a way up, thirteen to thirty blocks tall
        for (int t = 0; t < 400 && p.fillers.size() < 26; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rad = 38 + r.nextDouble() * 92;
            int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad), cr = 4 + r.nextInt(6);
            if (!clear(p, u, v, cr + 2)) continue;
            boolean ok = true;
            for (int[] q : p.fillers) if (Math.hypot(u - q[0], v - q[1]) < cr + q[3] + 3) ok = false;
            for (Shroom m : p.shrooms) if (m.top - m.base < 50 && Math.hypot(u - m.u, v - m.v) < m.cap + cr + 1) ok = false;
            if (ok) p.fillers.add(new int[]{u, v, 1 + r.nextInt(3), cr, 13 + r.nextInt(18), r.nextInt(3)});
        }
        // the garrisons: the gate, the fields, the farm and the larder, every hall of the dome, the caps and galleries
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(0, p.gateY + 1, -WALL_R + 9, "!pigman_berserker+zombie_pigman"));       // inside the Spore Gate
        gs.add(g(118, floorAt(s, p.rot, 118, 8) + 1, 8, "spore+spore_creeper"));            // the Spore Fields
        gs.add(g(FARM_U + 4, p.farmY + 1, FARM_V - 6, "mogus+zombie_pigman"));              // the Mushroom Farm
        gs.add(g(LARDER_U + 2, p.larderY + 1, LARDER_V + 1, "charred_ghoul+mummy"));        // the Rotting Larder
        gs.add(g(0, L0 + 1, -20, "!infernal_knight+brute"));                                // the dome's north gate
        gs.add(g(0, L0 + 1, 5, "wight+lost_soul"));                                         // the atrium
        gs.add(g(15, L0 + 1, -6, "spore+salamander"));                                      // the Spore Vats
        gs.add(g(-14, L0 + 1, -7, "spore_creeper+coolmar_spider"));                         // the Brood Chamber
        gs.add(g(14, L0 + 1, 10, "mogus+ember"));                                           // the Nursery
        gs.add(g(-13, L0 + 1, 13, "brimstone_spider+deep_crawler"));                        // the Mycelium Garden
        gs.add(g(-21, L1 + 1, 6, "royal_guard+crypt_guard"));                               // the Hall of Gills
        gs.add(g(7, L2 + 1, -9, "blaze+magma_cube"));                                       // the Bellows
        gs.add(g(5, p.chTop + 1, -6, "blaze+ember"));                                       // the chimney's crown
        String[] tops = {"ashbone_archer+skeleton", "wither_skeleton+shade", "spinout+salamander", "cinder_witch+soul_wraith"};
        for (int i = 0; i < 4; i++) { int[] q = topSpot(p, i); gs.add(g(q[0], q[1], q[2], tops[i])); }
        gs.add(g(p.shrooms.get(0).u - p.shrooms.get(0).stem - 2, p.shrooms.get(0).gallery() + 1, p.shrooms.get(0).v, "coolmar_spider+nethermite"));
        gs.add(g(p.shrooms.get(2).u - p.shrooms.get(2).stem - 2, p.shrooms.get(2).gallery() + 1, p.shrooms.get(2).v, "deep_crawler+brimstone_spider"));
        for (int i : new int[]{5, 7}) { int[] q = topSpot(p, i); gs.add(g(q[0], q[1], q[2], i == 5 ? "pyre_warden+cinder_imp" : "tomb_guardian+mummy")); }
        gs.add(g(WALL_R - 8, floorAt(s, p.rot, WALL_R - 8, 0) + 1, 0, "hellhound+brute"));  // the east gate
        complete(gs);
        gs.add(g(0, 80, -42, "ghast+ghastling"));                                           // between the great caps
        gs.add(g(42, 80, 0, "ghast+ghastling"));
        gs.add(g(-42, 80, 0, "ghastling+ghastling"));
        gs.add(g(0, 80, 42, "ghast+ghastling"));
        gs.add(g(0, 74, -100, "ghastling+ghast"));                                          // over the road
        p.garrisons = gs;
        return p;
    }

    private static void add(Plan p, Colossi.Site s, Random r, double deg, int rad, int kind, int stem, int cap, int height) {
        double a = Math.toRadians(deg);
        int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad);
        int base = Integer.MIN_VALUE;
        for (int[] q : new int[][]{{0, 0}, {stem, 0}, {-stem, 0}, {0, stem}, {0, -stem}}) base = Math.max(base, floorAt(s, p.rot, u + q[0], v + q[1]));
        int lim = ceilAt(s, p.rot, u, v) - 7;
        int drop = kind == BROWN ? 3 : kind == ELDER ? 13 : (int) (cap * 0.45);
        for (int[] q : new int[][]{{cap, 0}, {-cap, 0}, {0, cap}, {0, -cap}}) lim = Math.min(lim, ceilAt(s, p.rot, u + q[0], v + q[1]) - 4 + drop);
        p.shrooms.add(new Shroom(u, v, kind, stem, cap, Math.min(base + height, lim), base, r.nextInt(16)));
    }

    /** A spot on shroom i's top (feet height) clear of its bridges' decks. */
    static int[] topSpot(Plan p, int i) {
        Shroom m = p.shrooms.get(i);
        double r = m.stem + 1.5;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            double pu = m.u + Math.cos(a) * r, pv = m.v + Math.sin(a) * r;
            boolean free = true;
            for (int[] br : p.bridges) {
                if (br[0] != i && br[1] != i) continue;
                int o = br[0] == i ? br[1] : br[0];
                double bu = o < 0 ? 0 : p.shrooms.get(o).u, bv = o < 0 ? 0 : p.shrooms.get(o).v;
                double dx = bu - m.u, dz = bv - m.v, len = Math.hypot(dx, dz);
                double along = ((pu - m.u) * dx + (pv - m.v) * dz) / len, across = Math.abs((-(pu - m.u) * dz + (pv - m.v) * dx) / len);
                if (along > 0 && across < 4) free = false;
            }
            if (free) return new int[]{(int) Math.round(pu), m.surface(r) + 1, (int) Math.round(pv)};
        }
        return new int[]{m.u + m.stem + 1, m.top + 1, m.v};
    }

    /** Whether the bridge from shroom i to shroom j passes near a third mushroom. */
    private static boolean blocked(Plan p, int i, int j, int au, int av, int bu, int bv) {
        double dx = bu - au, dz = bv - av, len2 = dx * dx + dz * dz;
        for (int k = 0; k < p.shrooms.size(); k++) {
            if (k == i || k == j) continue;
            Shroom m = p.shrooms.get(k);
            double t = Math.max(0, Math.min(1, ((m.u - au) * dx + (m.v - av) * dz) / len2));
            if (Math.hypot(au + dx * t - m.u, av + dz * t - m.v) < m.cap + 3) return true;
        }
        return Math.abs((bu * av - bv * au) / Math.sqrt(len2)) < DR + 4 && ((-au) * dx + (-av) * dz) > 0 && ((-au) * dx + (-av) * dz) < len2;
    }

    /** Whether (u, v) keeps r clear of the stems, the dome, the road and the outer works. */
    private static boolean clear(Plan p, int u, int v, int r) {
        if (Math.hypot(u, v) < DR + r + 4 || Math.hypot(u, v) > WALL_R - r - 4) return false;
        if (Math.abs(u) < r + 5 && v < 0) return false;
        for (Shroom m : p.shrooms) if (Math.hypot(u - m.u, v - m.v) < m.stem + r + 5) return false;
        for (int[] q : new int[][]{{FARM_U, FARM_V, 17}, {LARDER_U, LARDER_V, 11}, {CACHE_U, CACHE_V, 6}}) if (Math.hypot(u - q[0], v - q[1]) < q[2] + r) return false;
        for (int[] q : p.puffs) if (Math.hypot(u - q[0], v - q[1]) < q[2] + r + 2) return false;
        return true;
    }

    static int wx(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.x - v; case 2: return s.x - u; case 3: return s.x + v; default: return s.x + u; } }
    static int wz(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.z + u; case 2: return s.z - v; case 3: return s.z - u; default: return s.z + v; } }
    static int floorAt(Colossi.Site s, int rot, int u, int v) { return s.floorAt(wx(s, rot, u, v), wz(s, rot, u, v)); }
    static int ceilAt(Colossi.Site s, int rot, int u, int v) { return s.ceilAt(wx(s, rot, u, v), wz(s, rot, u, v)); }

    // ---- cap shapes ------------------------------------------------------------------------------------------------------
    /** The cap's top at r from the axis: a dome flat on top (red, the elder's crown), a flat disk with a curled rim (brown), a blob (wart). */
    static int capTop(int kind, int R, int top, double r) {
        switch (kind) {
            case BROWN: return r <= R - 2.5 ? top : top - (int) Math.ceil(r - (R - 2.5));
            case WART: return top - (int) Math.round((r / R) * (r / R) * 0.45 * R);
            default: { double fl = 0.42 * R, q = Math.max(0, (r - fl) / (R - fl)); return top - (int) Math.round(q * q * 0.45 * R); }
        }
    }
    static int capBottom(int kind, int R, int top, double r) {
        int t = capTop(kind, R, top, r);
        switch (kind) {
            case BROWN: return t - 1;
            case WART: return t - 3;
            default: return t - 2 - (r > 0.85 * R ? (int) Math.round((r - 0.85 * R) * 1.2) : 0);
        }
    }

    /** The huge-mushroom meta for a cap block at world offset (dx, dz) from its axis: cap on top and on its outward sides. */
    static int capMeta(double dx, double dz, double r0) {
        if (Math.hypot(dx, dz) < r0) return 5;
        double t = Math.max(Math.abs(dx), Math.abs(dz)) * 0.42;
        boolean e = dx > t, w = dx < -t, so = dz > t, n = dz < -t;
        if (n && w) return 1;
        if (n && e) return 3;
        if (so && w) return 7;
        if (so && e) return 9;
        return n ? 2 : so ? 8 : w ? 4 : e ? 6 : 5;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        road(f, s);
        wall(f, s, p);
        farm(f, s, p);
        larder(f, s, p);
        puffballs(f, s, p);
        for (int[] q : p.fillers) {
            int fl = floorAt(s, p.rot, q[0], q[1]);
            solidShroom(f, s, q[0], q[1], fl, q[2], q[3], fl + q[4], q[5]);
        }
        dome(f, s);
        floor0(f, s);
        upper(f, s);
        bellows(f, s, p);
        for (Shroom m : p.shrooms) shroom(f, s, m);
        for (int[] q : p.patches) patch(f, p, q);
        for (int[] q : p.bridges) bridge(f, p, q);
        for (int i = 0; i < 4; i++) capLamps(f, p, i);
        chests(f, s, p);
        garrisons(f, p.garrisons);
    }



    // ---- TEMPORARY walk check (debug only; removed before hand-off) ---------------------------------------------------
    static final class Walk {
        final Colossi.Site s; final Draw d; final int x0, z0, nx = 369, nz = 369, y0 = 24, ny = 99;
        final java.util.BitSet seen = new java.util.BitSet(369 * 369 * 99);
        final List<int[]> open = new ArrayList<>();
        Walk(Colossi.Site s, Draw d) { this.s = s; this.d = d; x0 = s.x - 184; z0 = s.z - 184; }
        boolean inOpen(int x, int y, int z) { for (int[] b : open) if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true; return false; }
        boolean pass(int x, int y, int z) {
            if (inOpen(x, y, z)) return true;
            int id = d.id(x, y, z);
            return id == 0 || id == 65 || id == 171 || id == 39 || id == 40 || id == 68 || id == 63 || id == 30 || id == 51 || id == 115 || id == 50 || id == 198 || id == 78 || id == 31 || id == 144;
        }
        boolean ladder(int x, int y, int z) { return d.id(x, y, z) == 65; }
        boolean support(int x, int y, int z) {
            if (inOpen(x, y, z)) return false;
            int id = d.id(x, y, z);
            if (id == 8 || id == 9 || id == 10 || id == 11) return false;
            if (Blocks.isFullSolid(id)) return true;
            switch (id) { case 53: case 67: case 108: case 109: case 114: case 128: case 134: case 135: case 136: case 156: case 163: case 164: case 180: case 44: case 126: case 118: case 20: case 95: case 89: case 91: case 86: case 169: case 198: return true; default: return false; }
        }
        boolean stand(int x, int y, int z) { return y > y0 && y < y0 + ny - 2 && pass(x, y, z) && pass(x, y + 1, z) && (support(x, y - 1, z) || ladder(x, y, z) || ladder(x, y + 1, z)); }
        int idx(int x, int y, int z) { return ((x - x0) * nz + (z - z0)) * ny + (y - y0); }
        boolean inside(int x, int y, int z) { return x >= x0 && x < x0 + nx && z >= z0 && z < z0 + nz && y >= y0 && y < y0 + ny; }
        int[] q = new int[3 * 4_000_000]; int qh, qt;
        void push(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            int i = idx(x, y, z);
            if (seen.get(i)) return;
            seen.set(i);
            if (qt + 3 > q.length) return;
            q[qt++] = x; q[qt++] = y; q[qt++] = z;
        }
        void run(int sx, int sy, int sz) {
            push(sx, sy, sz);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (qh < qt) {
                int x = q[qh++], y = q[qh++], z = q[qh++];
                for (int[] dv : dirs) {
                    int ax = x + dv[0], az = z + dv[1];
                    if (stand(ax, y, az)) { push(ax, y, az); continue; }
                    if (stand(ax, y + 1, az) && pass(x, y + 2, z)) { push(ax, y + 1, az); continue; }
                    if (pass(ax, y, az) && pass(ax, y + 1, az)) for (int k = 1; k <= 5; k++) {
                        if (stand(ax, y - k, az)) { push(ax, y - k, az); break; }
                        if (!pass(ax, y - k, az)) break;
                    }
                }
                if ((ladder(x, y, z) || ladder(x, y + 1, z)) && pass(x, y + 2, z)) push(x, y + 1, z);
                if (ladder(x, y - 1, z) || (ladder(x, y, z) && pass(x, y - 1, z))) push(x, y - 1, z);
            }
        }
        boolean reached(int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++)
                if (inside(x + dx, y + dy, z + dz) && seen.get(idx(x + dx, y + dy, z + dz))) return true;
            return false;
        }
        int startY(int x, int z) { for (int y = 60; y > y0 + 1; y--) if (stand(x, y, z)) return y; return -1; }
    }

    /** The road from the Spore Gate to the dome's north gate, with lucis lamps. */
    private void road(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -4, -WALL_R - 2, 4, -DR + 2)) return;
        cols(f, -3, -WALL_R + 2, 3, -DR + 1, (x, z, u, v) -> {
            int fl = s.floorAt(x, z);
            if (Math.abs(u) <= 2) {
                f.d.set(x, fl, z, Math.abs(u) == 2 ? MYCEL : Draw.rnd(x, 1, z, s.salt + 3) < 0.25 ? BROWN_T : RED_T);
                f.d.set(x, fl + 1, z, 0); f.d.set(x, fl + 2, z, 0);
            }
        });
        // lamps along both sides (each block clipped on its own: a lamp's rim may fall in the next chunk)
        for (int v = -WALL_R + 12; v <= -DR - 2; v += 12) for (int side = -1; side <= 1; side += 2) {
            int lx = f.x(side * 3, v), lz = f.z(side * 3, v);
            lamp(f.d, lx, s.floorAt(lx, lz) + 1, lz, 3);
        }
    }

    /** A lucis lamp: a stalk of enoki with a glowing heart ringed by its rim. */
    private static void lamp(Draw d, int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) d.set(x, y + k, z, Blocks.ENOKI_STEM);
        d.set(x, y + h, z, Blocks.LUCIS_CENTER);
        d.set(x + 1, y + h, z, Blocks.LUCIS_RIM); d.set(x - 1, y + h, z, Blocks.LUCIS_RIM);
        d.set(x, y + h, z + 1, Blocks.LUCIS_RIM); d.set(x, y + h, z - 1, Blocks.LUCIS_RIM);
        d.set(x, y + h + 1, z, Blocks.ENOKI_CAP);
    }

    /** The Fungal Wall: a palisade of mushroom stalks round the city, capped, with the Spore Gate and three lesser gates. */
    private void wall(Draw.Frame f, Colossi.Site s, Plan p) {
        int R = WALL_R + 5;
        cols(f, -R, -R, R, R, (x, z, u, v) -> {
            double rr = Math.sqrt((double) u * u + (double) v * v);
            if (rr < WALL_R - 6 || rr > WALL_R + 5) return;
            double rw = WALL_R + Draw.noise(x, z, 30, s.salt + 31) * 3, dw = Math.abs(rr - rw);
            if (dw > 2.6) return;
            double ang = Math.atan2(v, u);
            for (int k = 0; k < 4; k++) if (Math.abs(Math.IEEEremainder(ang - k * Math.PI / 2, 2 * Math.PI)) * rr < (k == 3 ? 8 : 5)) return;
            int fl = s.floorAt(x, z);
            double along = ang * rw, post = Math.abs(Math.IEEEremainder(along, 9));
            int h = fl + 16 + (int) Math.round(Draw.noise(x, z, 7, s.salt + 32) * 3) + (post < 1.3 && dw < 1.4 ? 6 : 0);
            boolean red = Math.floorMod((int) Math.floor(along / 9), 2) == 0;
            if (dw > 1.7) {
                for (int y = fl - 1; y <= fl + 1; y++) f.d.set(x, y, z, y == fl + 1 ? MYCEL : STEM_ALL);
                f.d.set(x, h - 1, z, red ? RED_CAP : BROWN_CAP);
                f.d.set(x, h, z, red ? RED_CAP : BROWN_CAP);
                return;
            }
            for (int y = fl - 1; y < h; y++) f.d.set(x, y, z, STEM);
            f.d.set(x, h, z, red ? RED_CAP : BROWN_CAP);
            if (post < 1.3 && dw < 1.4) f.d.set(x, h + 1, z, red ? b(100, 15) : BROWN_CAP);
            if (Draw.rnd(x, 2, z, s.salt + 33) < 0.03 && rr < rw) f.d.set(x, fl + 6, z, Blocks.LUCIS_CENTER);
        });
        // the Spore Gate: two gate mushrooms and an arch of stalk between them; lesser gates east, south and west
        int gy = p.gateY;
        if (touches(f, -24, -WALL_R - 14, 24, -WALL_R + 14)) {
            for (int side = -1; side <= 1; side += 2) solidShroom(f, s, side * 10, -WALL_R, gy, 3, 8, gy + 26, RED);
            f.d.arch(f.xd(-10, -WALL_R), f.zd(-10, -WALL_R), f.xd(10, -WALL_R), f.zd(10, -WALL_R), gy + 15, 5, 1.6, 2, Draw.of(STEM_ALL));
            for (int side = -1; side <= 1; side += 2) { lamp(f.d, f.x(side * 6, -WALL_R - 3), gy + 1, f.z(side * 6, -WALL_R - 3), 4); }
            f.wallSign(-10, gy + 6, -WALL_R - 4, Draw.NORTH, "THE", "SPOREFATHER'S", "HIVE", "Breathe softly");
            f.wallSign(10, gy + 6, -WALL_R - 4, Draw.NORTH, "The dome lies", "south. Its", "father waits", "above the font");
        }
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI / 2;
            for (int side = -1; side <= 1; side += 2) {
                double ga = a + side * 6.0 / WALL_R;
                int u = (int) Math.round(Math.cos(ga) * WALL_R), v = (int) Math.round(Math.sin(ga) * WALL_R);
                if (touches(f, u - 8, v - 8, u + 8, v + 8)) solidShroom(f, s, u, v, floorAt(s, p.rot, u, v), 2, 5, floorAt(s, p.rot, u, v) + 18, k == 1 ? BROWN : RED);
            }
        }
    }

    /** A mushroom without a way up: a gate post or a field fungus. */
    private void solidShroom(Draw.Frame f, Colossi.Site s, int cu, int cv, int fl, int S, int R, int top, int kind) {
        if (!touches(f, cu - R - 2, cv - R - 2, cu + R + 2, cv + R + 2)) return;
        f.cyl(cu, cv, S + 1.2, fl - 2, fl + 1, Draw.of(STEM_ALL));
        f.cyl(cu, cv, S, fl - 2, top - 1, Draw.of(STEM));
        cap(f, s, cu, cv, kind, R, top, kind == RED ? 100 : 99, s.salt + cu * 7 + cv);
    }

    /** One cap: blocks of id, each with the meta its outward faces want, from its underside to its top round (cu, cv). */
    private static void cap(Draw.Frame f, Colossi.Site s, int cu, int cv, int kind, int R, int top, int id, int salt) {
        if (!touches(f, cu - R - 1, cv - R - 1, cu + R + 1, cv + R + 1)) return;
        double wcx = f.xd(cu, cv), wcz = f.zd(cu, cv), r0 = 0.3 * R;
        int vine = Blocks.EYE_VINE, glow = Blocks.LUCIS_CENTER;
        cols(f, cu - R - 1, cv - R - 1, cu + R + 1, cv + R + 1, (x, z, u, v) -> {
            double rr = Math.hypot(u - cu, v - cv);
            if (rr > R + 0.4) return;
            int yt = capTop(kind, R, top, rr), yb = capBottom(kind, R, top, rr);
            for (int y = yb; y <= yt; y++) {
                int blk;
                if (kind == WART) blk = y == yb && Draw.rnd(x, y, z, salt) < 0.05 ? glow : WART_B;
                else {
                    boolean face = y == yt || y == yb || rr > R - 1.5;
                    blk = b(id, face ? capMeta(x - wcx, z - wcz, r0) : 0);
                    if (kind == RED && y == yt && Draw.rnd(x, 5, z, salt) < 0.09) blk = b(id, 15);
                }
                f.d.set(x, y, z, blk);
            }
            if (kind == WART && rr > 3 && Draw.rnd(x, 6, z, salt) < 0.08) {
                int len = 2 + (int) (Draw.rnd(x, 7, z, salt) * 6);
                for (int k = 1; k <= len; k++) f.d.set(x, yb - k, z, vine);
            }
        });
    }

    /** A giant mushroom: flaring roots, a hollow stem with its spiral stair, lucis shelves, the cap(s), the gill gallery, the doors. */
    private void shroom(Draw.Frame f, Colossi.Site s, Shroom m) {
        int S = m.stem, R = m.cap;
        if (!touches(f, m.u - R - 3, m.v - R - 3, m.u + R + 3, m.v + R + 3)) return;
        boolean elder = m.kind == ELDER;
        int stemBlk = elder ? Blocks.ELDER_STEM : m.kind == WART ? Blocks.RED_LARGE_STALK : STEM;
        cols(f, m.u - S - 3, m.v - S - 3, m.u + S + 3, m.v + S + 3, (x, z, u, v) -> {
            double rr = Math.hypot(u - m.u, v - m.v);
            if (rr > S + 2.6) return;
            int fl = s.floorAt(x, z);
            if (rr > S + 0.35) {
                int h = (int) Math.round((S + 2.6 - rr) * 1.6);
                for (int y = fl - 1; y <= fl + h; y++) f.d.set(x, y, z, y == fl + h ? MYCEL : STEM_ALL);
                return;
            }
            for (int y = Math.min(fl, m.base) - 2; y <= m.top - 1; y++) f.d.set(x, y, z, y <= m.base ? STEM_ALL : stemBlk);
        });
        // the cap: a brown flat cap under a red crown for the elder
        int capKind = elder ? BROWN : m.kind, capTop = elder ? m.top - 10 : m.top;
        int capId = m.kind == RED ? 100 : m.kind == BROWN ? Blocks.BROWN_LARGE_ID : elder ? Blocks.ELDER_CAP_BROWN_ID : 214;
        cap(f, s, m.u, m.v, capKind, R, capTop, capId, s.salt + m.u * 31 + m.v);
        if (elder) cap(f, s, m.u, m.v, RED, m.upper(), m.top, Blocks.ELDER_CAP_RED_ID, s.salt + m.u * 37 + m.v);
        // lucis shelves up the stem
        for (int k = 0; k < 3; k++) {
            double a = m.phase + k * 2.1;
            int y = m.base + 8 + k * Math.max(4, (m.gallery() - m.base - 10) / 3);
            shelf(f, m.u + Math.cos(a) * (S + 1), m.v + Math.sin(a) * (S + 1), y, m.u, m.v, S);
        }
        // the gill gallery: a ring floor under the cap, its railing, gills hanging between
        int gy = m.gallery();
        double rg = 0.7 * R;
        int finBlk = m.kind == WART ? WART_B : capKind == RED ? RED_PORES : PORES;
        cols(f, m.u - R, m.v - R, m.u + R, m.v + R, (x, z, u, v) -> {
            double du = u - m.u, dv = v - m.v, rr = Math.hypot(du, dv);
            if (rr <= S + 0.35 || rr > R - 0.5) return;
            if (rr <= rg) {
                f.d.set(x, gy, z, STEM_ALL);
                for (int y = gy + 1; y <= gy + 3; y++) f.d.set(x, y, z, 0);
                if (rr > rg - 0.9) f.d.set(x, gy + 1, z, Blocks.ENOKI_STEM);
            }
            if (rr > S + 1.2 && Math.abs(Math.IEEEremainder(Math.atan2(dv, du), Math.PI / 8)) * rr < 0.6) {
                int under = capBottom(capKind, R, capTop, rr) - 1;
                for (int y = gy + 4; y <= under; y++) f.d.set(x, y, z, finBlk);
            }
        });
        for (int k = 0; k < 2; k++) {
            double a = m.phase * 0.7 + k * Math.PI;
            shelf(f, m.u + Math.cos(a) * (S + 1), m.v + Math.sin(a) * (S + 1), gy + 3, m.u, m.v, S);
        }
        // the spiral stair inside the stem, out at the top through the cap
        spiral(f, m.u, m.v, 1.4, S - 1.6, m.base + 1, m.top, 16, m.phase, BIRCHSTAIR, Draw.of(STEM_ALL));
        double wcx = f.xd(m.u, m.v), wcz = f.zd(m.u, m.v);
        int topId = elder ? Blocks.ELDER_CAP_RED_ID : capId, topKind = elder ? RED : m.kind, topR = elder ? m.upper() : R;
        spiralMouth(f, m.u, m.v, 1.4, S - 1.6, m.top, 16, m.phase,
            topKind == WART ? Draw.of(WART_B) : (x, y, z) -> b(topId, capMeta(x - wcx, z - wcz, 0.3 * topR)));
        // doors: at the foot (towards the dome), into the gallery, onto the elder's lower cap
        door(f, m.u, m.v, S, Math.atan2(-m.v, -m.u), m.base + 1, S + 3);
        porch(f, s, m.u, m.v, Math.atan2(-m.v, -m.u), S + 0.5, m.base);
        door(f, m.u, m.v, S, stepAngle(gy, m.phase), gy + 1, S + 1);
        if (elder) door(f, m.u, m.v, S, stepAngle(m.top - 10, m.phase), m.top - 9, S + 1);
    }

    /**
     * A porch from a doorway's sill (floor block at y, at distance from (cu, cv) along angle a) down to the ground: three
     * wide, a stair a block, stalk beneath; where the ground is higher it only clears the way.
     */
    private static void porch(Draw.Frame f, Colossi.Site s, int cu, int cv, double a, double from, int y) {
        double ca = Math.cos(a), sa = Math.sin(a), to = from + 6;
        int u0 = (int) Math.floor(cu + Math.min(ca * from, ca * to) - 2), u1 = (int) Math.ceil(cu + Math.max(ca * from, ca * to) + 2);
        int v0 = (int) Math.floor(cv + Math.min(sa * from, sa * to) - 2), v1 = (int) Math.ceil(cv + Math.max(sa * from, sa * to) + 2);
        if (!touches(f, u0, v0, u1, v1)) return;
        boolean alongU = Math.abs(ca) >= Math.abs(sa);
        int inward = alongU ? (ca > 0 ? 1 : 0) : (sa > 0 ? 3 : 2);
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double du = u - cu, dv = v - cv, along = du * ca + dv * sa, across = Math.abs(-du * sa + dv * ca);
            if (along < from || along > to || across > 1.6) return;
            int fl = s.floorAt(x, z), ty = y - (int) Math.floor(along - from);
            if (fl >= ty) { for (int yy = fl + 1; yy <= fl + 3; yy++) f.d.set(x, yy, z, 0); return; }
            for (int yy = fl + 1; yy < ty; yy++) f.d.set(x, yy, z, STEM_ALL);
            f.d.set(x, ty, z, along - from < 1 ? STEM_ALL : f.stair(BIRCHSTAIR, inward, false));
            for (int yy = ty + 1; yy <= ty + 3; yy++) f.d.set(x, yy, z, 0);
        });
    }

    private static double stepAngle(int y, int phase) { return (Math.floorMod(y - phase, 16) + 0.5) * 2 * Math.PI / 16; }

    /** A doorway three high through the stem wall at angle a from the axis, out to r = reach. */
    private static void door(Draw.Frame f, int cu, int cv, int S, double a, int y0, double reach) {
        double ca = Math.cos(a), sa = Math.sin(a);
        int u0 = (int) Math.floor(cu + Math.min(0, ca * reach) - 2), u1 = (int) Math.ceil(cu + Math.max(0, ca * reach) + 2);
        int v0 = (int) Math.floor(cv + Math.min(0, sa * reach) - 2), v1 = (int) Math.ceil(cv + Math.max(0, sa * reach) + 2);
        if (!touches(f, u0, v0, u1, v1)) return;
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double du = u - cu, dv = v - cv, along = du * ca + dv * sa, across = Math.abs(-du * sa + dv * ca);
            if (along < S - 1.25 || along > reach || across > 1.3) return;
            for (int y = y0; y < y0 + 3; y++) f.d.set(x, y, z, 0);
        });
    }

    /** A lucis shelf: a half-disk of rim round a glowing heart, out of a stem's side. */
    private static void shelf(Draw.Frame f, double cu, double cv, int y, int su, int sv, int S) {
        int u0 = (int) Math.floor(cu - 3), u1 = (int) Math.ceil(cu + 3), v0 = (int) Math.floor(cv - 3), v1 = (int) Math.ceil(cv + 3);
        if (!touches(f, u0, v0, u1, v1)) return;
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double dc = Math.hypot(u - cu, v - cv);
            if (dc > 2.3 || Math.hypot(u - su, v - sv) <= S + 0.35) return;
            f.d.set(x, y, z, dc < 0.8 ? Blocks.LUCIS_CENTER : Blocks.LUCIS_RIM);
        });
    }

    /** Lucis lamps round a great cap's top, wherever no bridge lands. */
    private static void capLamps(Draw.Frame f, Plan p, int i) {
        Shroom m = p.shrooms.get(i);
        double r = m.kind == ELDER ? m.upper() * 0.42 + 2.5 : m.kind == BROWN ? m.cap - 4 : m.stem + 3.5;
        if (!touches(f, (int) (m.u - r - 2), (int) (m.v - r - 2), (int) (m.u + r + 2), (int) (m.v + r + 2))) return;
        int placed = 0;
        for (int k = 0; k < 8 && placed < 3; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            double pu = m.u + Math.cos(a) * r, pv = m.v + Math.sin(a) * r;
            boolean free = true;
            for (int[] br : p.bridges) {
                if (br[0] != i && br[1] != i) continue;
                int o = br[0] == i ? br[1] : br[0];
                double bu = o < 0 ? 0 : p.shrooms.get(o).u, bv = o < 0 ? 0 : p.shrooms.get(o).v, dx = bu - m.u, dz = bv - m.v, len = Math.hypot(dx, dz);
                if (((pu - m.u) * dx + (pv - m.v) * dz) > 0 && Math.abs((-(pu - m.u) * dz + (pv - m.v) * dx) / len) < 4.5) free = false;
            }
            for (int[] q : p.patches) if (q[0] == i && Math.hypot(pu - q[1], pv - q[2]) < 4) free = false;
            if (!free) continue;
            int lu = (int) Math.round(pu), lv = (int) Math.round(pv);
            lamp(f.d, f.x(lu, lv), m.surface(Math.hypot(lu - m.u, lv - m.v)) + 1, f.z(lu, lv), 2);
            placed++;
        }
    }

    /** A rotten patch on a cap's top, its whole depth of another mushroom block (the collapse in ordeals()). */
    private void patch(Draw.Frame f, Plan p, int[] q) {
        Shroom m = p.shrooms.get(q[0]);
        if (!touches(f, q[1] - 2, q[2] - 2, q[1] + 2, q[2] + 2)) return;
        int kind = m.kind == ELDER ? BROWN : m.kind, top = m.kind == ELDER ? m.top - 10 : m.top;
        for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) {
            double rr = Math.hypot(q[1] + du - m.u, q[2] + dv - m.v);
            f.box(q[1] + du, capBottom(kind, m.cap, top, rr), q[2] + dv, q[1] + du, capTop(kind, m.cap, top, rr), q[2] + dv, Draw.of(patchBlock(m)));
        }
    }

    static int patchBlock(Shroom m) { return m.kind == RED ? BROWN_CAP : RED_CAP; }

    /** A rope bridge of stalk between two caps (or to the chimney's crown): three wide, sagging a little, railed with enoki. */
    private void bridge(Draw.Frame f, Plan p, int[] q) {
        Shroom a = p.shrooms.get(q[0]);
        double bu, bv, br; int bt;
        if (q[1] < 0) { bu = 0; bv = 0; br = 7; bt = p.chTop; }
        else { Shroom c = p.shrooms.get(q[1]); bu = c.u; bv = c.v; br = c.reach(); bt = c.top; }
        double dx = bu - a.u, dz = bv - a.v, dd = Math.hypot(dx, dz), ux = dx / dd, uz = dz / dd;
        double su = a.u + ux * a.reach(), sv = a.v + uz * a.reach(), len = dd - a.reach() - br;
        int at = a.top;
        double sag = Math.min(3, len / 14);
        int u0 = (int) Math.floor(Math.min(su, su + ux * len) - 3), u1 = (int) Math.ceil(Math.max(su, su + ux * len) + 3);
        int v0 = (int) Math.floor(Math.min(sv, sv + uz * len) - 3), v1 = (int) Math.ceil(Math.max(sv, sv + uz * len) + 3);
        if (!touches(f, u0, v0, u1, v1)) return;
        boolean alongU = Math.abs(ux) >= Math.abs(uz);
        int fwd = alongU ? (ux > 0 ? 0 : 1) : (uz > 0 ? 2 : 3);
        double dt = (alongU ? Math.abs(ux) : Math.abs(uz)) / len;
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double pu = u - su, pv = v - sv, t = (pu * ux + pv * uz) / len, side = Math.abs(-pu * uz + pv * ux);
            if (t < -0.03 || t > 1.03 || side > 2.45) return;
            int h = deckH(at, bt, sag, t), hp = deckH(at, bt, sag, t - dt), hn = deckH(at, bt, sag, t + dt);
            if (side <= 1.5) {
                int blk = hp < h ? f.stair(BIRCHSTAIR, fwd, false) : hn < h ? f.stair(BIRCHSTAIR, fwd ^ 1, false) : STEM_ALL;
                f.d.set(x, h, z, blk);
                for (int y = h + 1; y <= h + 3; y++) f.d.set(x, y, z, 0);
                if (side <= 0.6) f.d.set(x, h - 1, z, STEM);
            } else {
                f.d.set(x, h, z, STEM_ALL);
                f.d.set(x, h + 1, z, Blocks.ENOKI_STEM);
                if (Math.abs(Math.IEEEremainder(t * len, 8)) < 0.5) { f.d.set(x, h + 2, z, Blocks.ENOKI_STEM); f.d.set(x, h + 3, z, Blocks.LUCIS_CENTER); }
            }
        });
    }

    private static int deckH(int at, int bt, double sag, double t) {
        t = Math.max(0, Math.min(1, t));
        return (int) Math.round(at + (bt - at) * t - sag * 4 * t * (1 - t));
    }

    /** Puffballs: pale spheres sat on the floor, the Spore Fields thick with them; one hollow, hiding a cache. */
    private void puffballs(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw.Mat sk = skin(s.salt + 51);
        for (int[] q : p.puffs) {
            if (!touches(f, q[0] - q[2] - 1, q[1] - q[2] - 1, q[0] + q[2] + 1, q[1] + q[2] + 1)) continue;
            int fl = floorAt(s, p.rot, q[0], q[1]);
            ell(f, q[0], fl + q[2] * 0.6, q[1], q[2], q[2] * 0.85, q[2], 0, sk);
        }
        if (touches(f, CACHE_U - 6, CACHE_V - 6, CACHE_U + 6, CACHE_V + 6)) {
            int fl = p.cacheY;
            ell(f, CACHE_U, fl + 3, CACHE_V, 5, 4.5, 5, 0, sk);
            ell(f, CACHE_U, fl + 3, CACHE_V, 3.6, 3.2, 3.6, 0, Draw.AIR);
            f.box(CACHE_U - 5, fl, CACHE_V - 5, CACHE_U + 5, fl, CACHE_V + 5, Draw.of(MYCEL));
            f.box(CACHE_U - 6, fl + 1, CACHE_V - 1, CACHE_U - 3, fl + 2, CACHE_V + 1, Draw.AIR);
            f.set(CACHE_U, fl + 6, CACHE_V, GLOW);
        }
    }

    /** The Hive Dome's shell: a puffball sixty across, pale and warted outside, pored within; its four gates. */
    private void dome(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -DR - 3, -DR - 3, DR + 3, DR + 3)) return;
        Draw.Mat sk = skin(s.salt + 41);
        cols(f, -DR - 1, -DR - 1, DR + 1, DR + 1, (x, z, u, v) -> {
            double rr = Math.hypot(u, v), R1 = DR + 0.4;
            if (rr > R1) return;
            int fl = s.floorAt(x, z);
            double h = DRY * Math.sqrt(Math.max(0, 1 - (rr / R1) * (rr / R1)));
            int yTop = DCY + (int) Math.round(h), yBot = Math.max(Math.min(fl, L0) - 1, DCY - (int) Math.round(h));
            boolean inner = rr < RI + 0.4;
            int inTop = inner ? DCY + (int) Math.round(RIY * Math.sqrt(Math.max(0, 1 - (rr / (RI + 0.4)) * (rr / (RI + 0.4))))) : -1;
            for (int y = yBot; y <= yTop; y++) {
                int blk;
                if (inner && y > L0 && y <= inTop) blk = 0;
                else if (inner && y == L0) blk = MYCEL;
                else if (y >= yTop - 1 || rr > DR - 1.2) blk = sk.at(x, y, z);
                else blk = PORES;
                f.d.set(x, y, z, blk);
            }
            if (Draw.rnd(x, 1, z, s.salt + 44) < 0.035) { f.d.set(x, yTop + 1, z, WART_B); if (Draw.rnd(x, 2, z, s.salt + 44) < 0.4) f.d.set(x, yTop + 2, z, WART_B); }
        });
        // spore vents: little chimneys of stalk on the dome's shoulders, glowing within
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6;
            double vu = Math.cos(a) * 19, vv = Math.sin(a) * 19;
            int vy = DCY + (int) Math.round(DRY * Math.sqrt(1 - (19 / (DR + 0.4)) * (19 / (DR + 0.4))));
            f.cyl(vu, vv, 1.6, vy - 1, vy + 3, Draw.of(STEM));
            f.cyl(vu, vv, 0.6, vy, vy + 3, Draw.AIR);
            f.set((int) Math.round(vu), vy - 1, (int) Math.round(vv), GLOW);
            f.ring(vu, vv, 2.2, 1, vy + 4, vy + 4, Draw.of(BROWN_CAP));
        }
        // the floors: the upper floor over the ground floor, the Bellows' floor over the throne
        f.disk(0, 0, 27.2, L1 - 1, Draw.of(PORES));
        f.disk(0, 0, 27.2, L1, (x, y, z) -> Draw.rnd(x, y, z, s.salt + 71) < 0.15 ? PODZOL : MYCEL);
        f.disk(0, 0, 22.2, L2 - 1, Draw.mix(PORES, GLOW, 0.05, s.salt + 72));
        f.disk(0, 0, 22.2, L2, Draw.of(MYCEL));
        // the four gates: tunnels through the shell, hooded, lit, a porch down to the ground
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] q : dirs) {
            int au = q[0], av = q[1];
            porch(f, s, 0, 0, Math.atan2(av, au), 31.5, L0);
            f.box(au == 0 ? -2 : au * 22, L0 + 1, av == 0 ? -2 : av * 22, au == 0 ? 2 : au * 31, L0 + 6, av == 0 ? 2 : av * 31, Draw.AIR);
            f.box(au == 0 ? -2 : au * 22, L0, av == 0 ? -2 : av * 22, au == 0 ? 2 : au * 31, L0, av == 0 ? 2 : av * 31, Draw.of(MYCEL));
            f.box(au == 0 ? -4 : au * 28, L0 + 7, av == 0 ? -4 : av * 28, au == 0 ? 4 : au * 31, L0 + 8, av == 0 ? 4 : av * 31, Draw.of(BROWN_CAP));
            for (int side = -1; side <= 1; side += 2) {
                int lu = au == 0 ? side * 4 : au * 31, lv = av == 0 ? side * 4 : av * 31;
                int lx = f.x(lu, lv), lz = f.z(lu, lv);
                lamp(f.d, lx, s.floorAt(lx, lz) + 1, lz, 4);
            }
        }
        f.wallSign(-2, L0 + 4, -29, Draw.EAST, "THE HIVE DOME", "The Sporefather", "keeps his throne", "above the font");
    }

    /** The ground floor: the atrium and its font, the corridors from the gates, the four bays, the stairs up. */
    private void floor0(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -28, -28, 28, 28)) return;
        Draw.Mat wall = Draw.mix(STEM, PORES, 0.25, s.salt + 61);
        // the corridor walls from the gates to the atrium, doors into the bays
        for (int sg = -1; sg <= 1; sg += 2) {
            f.box(sg * 3, L0 + 1, -24, sg * 3, L1 - 2, -12, wall);
            f.box(sg * 3, L0 + 1, 12, sg * 3, L1 - 2, 24, wall);
            f.box(-24, L0 + 1, sg * 3, -12, L1 - 2, sg * 3, wall);
            f.box(12, L0 + 1, sg * 3, 24, L1 - 2, sg * 3, wall);
            f.box(sg * 3, L0 + 1, -18, sg * 3, L0 + 4, -16, Draw.AIR);
            f.box(sg * 3, L0 + 1, 16, sg * 3, L0 + 4, 18, Draw.AIR);
            f.box(-18, L0 + 1, sg * 3, -16, L0 + 4, sg * 3, Draw.AIR);
            f.box(16, L0 + 1, sg * 3, 18, L0 + 4, sg * 3, Draw.AIR);
        }
        // the atrium: eight stalk pillars round the spore font, a floor of rings
        f.box(-11, L0, -11, 11, L0, 11, (x, y, z) -> {
            double r = Math.hypot(lu(f, x, z), lv(f, x, z));
            return r > 11.4 ? MYCEL : ((int) r & 3) == 0 ? RED_CAP : ((int) r & 3) == 2 ? BROWN_CAP : MYCEL;
        });
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            double pu = Math.cos(a) * 8, pv = Math.sin(a) * 8;
            f.cyl(pu, pv, 1.3, L0 + 1, L1 - 1, Draw.of(STEM));
            shelf(f, Math.cos(a) * 9.5, Math.sin(a) * 9.5, L0 + 6, (int) Math.round(pu), (int) Math.round(pv), 1);
        }
        f.ring(0, 0, 3.4, 1, L0 + 1, L0 + 1, Draw.of(STEM_ALL));
        f.disk(0, 0, 2.4, L0, Draw.of(GLOW));
        f.disk(0, 0, 2.4, L0 + 1, Draw.of(LIME_GLASS));
        // the Spore Vats (north-east): three basins of green soup
        for (int[] q : new int[][]{{12, -12}, {20, -8}, {8, -20}}) {
            f.ring(q[0], q[1], 3, 1, L0 + 1, L0 + 2, Draw.of(STEM_ALL));
            f.disk(q[0], q[1], 2, L0, Draw.of(GREEN_T));
            f.disk(q[0], q[1], 2, L0 + 1, Draw.of(GREEN_GLASS));
            f.disk(q[0], q[1], 2, L0 + 2, Draw.of(LIME_GLASS));
            f.set(q[0], L0 + 3, q[1], Blocks.ENOKI_STEM);
            f.set(q[0], L1 - 2, q[1], GLOW);
        }
        f.spawner(15, L0 + 1, -16, "spore");
        f.wallSign(4, L0 + 3, -14, Draw.EAST, "THE SPORE VATS", "Breathe not", "the green", "");
        // the Brood Chamber (north-west): egg sacs on the floor and hung from the ceiling
        for (int[] q : new int[][]{{-10, -9}, {-21, -6}, {-8, -21}, {-14, -21}}) ell(f, q[0], L0 + 2.5, q[1], 2.4, 2.4, 2.4, 0, flesh(s.salt + 62));
        for (int[] q : new int[][]{{-15, -9}, {-10, -15}}) { ell(f, q[0], L1 - 4, q[1], 1.8, 2.2, 1.8, 0, flesh(s.salt + 63)); f.box(q[0], L1 - 2, q[1], q[0], L1 - 2, q[1], Draw.of(STEM)); }
        f.set(-6, L0 + 1, -6, WEB); f.set(-20, L0 + 4, -11, WEB); f.set(-12, L0 + 6, -19, WEB);
        f.spawner(-17, L0 + 1, -10, "spore_creeper");
        // the Nursery (south-east): beds of mycelium and young mushrooms, cribs of stalk
        for (int bv = 7; bv <= 21; bv += 4) for (int bu = 6; bu <= 22; bu++) {
            if (Math.hypot(bu, bv) > 24) continue;
            f.set(bu, L0 + 1, bv, Blocks.NETHER_MYCELIUM);
            f.set(bu, L0 + 2, bv, ((bu + bv) & 1) == 0 ? Blocks.ORANGE_MUSHROOM : Blocks.BONE_MUSHROOM);
        }
        for (int[] q : new int[][]{{8, 9}, {16, 17}}) { f.box(q[0] - 1, L0 + 1, q[1] - 1, q[0] + 1, L0 + 2, q[1] + 1, Draw.of(STEM_ALL)); f.box(q[0], L0 + 2, q[1], q[0], L0 + 2, q[1], Draw.AIR); f.box(q[0] - 1, L0 + 4, q[1] - 1, q[0] + 1, L0 + 4, q[1] + 1, Draw.of(RED_CAP)); }
        f.spawner(20, L0 + 1, 13, "mogus");
        // the Mycelium Garden (south-west): enoki thickets, molds, lucis on the walls
        f.box(-23, L0 + 1, 5, -5, L0 + 1, 23, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            if (Math.hypot(u, v) > 23.5 || (u + v & 3) != 0) return 0;
            return Draw.rnd(x, y, z, s.salt + 64) < 0.5 ? Blocks.RED_MOLD : Blocks.GRAY_MOLD;
        });
        for (int[] q : new int[][]{{-8, 8}, {-14, 9}, {-9, 15}, {-19, 14}, {-15, 19}, {-21, 8}}) {
            int h = 4 + Math.floorMod(q[0] * 3 + q[1], 5);
            for (int k = 1; k <= h; k++) f.set(q[0], L0 + k, q[1], Blocks.ENOKI_STEM);
            f.set(q[0], L0 + h + 1, q[1], Blocks.ENOKI_CAP);
            f.set(q[0] + 1, L0 + h - 1, q[1], Blocks.ENOKI_STEM); f.set(q[0] + 1, L0 + h, q[1], Blocks.ENOKI_CAP);
        }
        for (int[] q : new int[][]{{-6, 22}, {-22, 6}, {-17, 16}}) shelf(f, q[0], q[1], L0 + 5, 0, 0, 0);
        // the stairs up to the Hall of Gills, in the north bays
        ringStair(f, 18, -14, L0 + 1, L1, L0 - 7, BIRCHSTAIR, Draw.of(STEM_ALL));
        ringStair(f, -18, -14, L0 + 1, L1, L0 - 3, BIRCHSTAIR, Draw.of(STEM_ALL));
        // rubble of spore pods in the north and south gate tunnels
        f.box(-2, L0 + 6, -29, 2, L0 + 6, -23, Draw.of(BROWN_CAP));
        f.box(-2, L0 + 6, 23, 2, L0 + 6, 29, Draw.of(BROWN_CAP));
    }

    /** The upper floor: the Hall of Gills round the Throne of the Sporefather, the throne, the seal and the Spore Heart. */
    private void upper(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -28, -28, 28, 28)) return;
        // the throne room's wall and door; its floor of rings
        f.ring(0, 0, 16.5, 1.6, L1 + 1, L2 - 2, Draw.mix(STEM, WART_B, 0.3, s.salt + 73));
        f.disk(0, 0, 15, L1, (x, y, z) -> {
            double r = Math.hypot(lu(f, x, z), lv(f, x, z));
            return r < 4 ? MYCEL : ((int) r % 3) == 0 ? RED_CAP : ((int) r % 3) == 1 ? BROWN_CAP : MYCEL;
        });
        f.box(-2, L1 + 1, -17, 2, L1 + 5, -14, Draw.AIR);
        f.box(-3, L1 + 6, -17, 3, L1 + 6, -16, Draw.of(RED_CAP));
        f.wallSign(-3, L1 + 4, -17, Draw.NORTH, "THE THRONE OF", "THE", "SPOREFATHER", "Kneel, or rot");
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            shelf(f, Math.cos(a) * 14, Math.sin(a) * 14, L1 + 6, 0, 0, 13);
            shelf(f, Math.cos(a) * 18, Math.sin(a) * 18, L1 + 6, 0, 0, 16);
        }
        // the throne: a dais of stalk, a seat of red flesh, a canopy cap on the back post
        f.box(-4, L1 + 1, 6, 4, L1 + 1, 10, Draw.of(STEM_ALL));
        f.box(-4, L1 + 1, 5, 4, L1 + 1, 5, Draw.of(f.stair(BIRCHSTAIR, 2, false)));
        f.box(-1, L1 + 2, 9, 1, L1 + 2, 9, Draw.of(RED_CAP));
        f.box(-1, L1 + 3, 10, 1, L1 + 5, 10, Draw.of(RED_CAP));
        f.box(0, L1 + 2, 10, 0, L1 + 8, 10, Draw.of(STEM));
        cap(f, s, 0, 8, RED, 5, L1 + 10, 100, s.salt + 74);
        f.set(-2, L1 + 2, 9, Blocks.LUCIS_CENTER); f.set(2, L1 + 2, 9, Blocks.LUCIS_CENTER);
        f.point(0, L1 + 1, 0, "lord:" + s.kind.lord);
        // the seal behind the throne, and the Spore Heart beyond it
        f.box(-7, L1, 16, 7, L1 + 8, 25, Draw.of(WART_B));
        f.box(-6, L1 + 1, 17, 6, L1 + 7, 24, Draw.AIR);
        f.box(-6, L1, 17, 6, L1, 24, Draw.mix(MYCEL, GLOW, 0.06, s.salt + 75));
        f.box(-1, L1 + 1, 16, 1, L1 + 4, 16, Draw.of(RED_CAP));
        f.box(-2, L1 + 5, 15, 2, L1 + 5, 15, Draw.of(STEM_ALL));
        f.wallSign(0, L1 + 6, 15, Draw.NORTH, "THE SPORE HEART", "opens when its", "father falls", "");
        ell(f, 0, L1 + 4, 21, 2.5, 3, 2.5, 0, flesh(s.salt + 76));
        f.set(0, L1 + 7, 21, Blocks.EYE_VINE);
        f.wallSign(-6, L1 + 3, 21, Draw.EAST, "THE SPORE", "HEART", "It still beats", "");
        // the Hall of Gills: gills hung from its ceiling round the throne room, egg sacs, the stair to the Bellows
        cols(f, -27, -27, 27, 27, (x, z, u, v) -> {
            double r = Math.hypot(u, v);
            if (r < 17.5 || r > 25.5) return;
            if (Math.abs(Math.IEEEremainder(Math.atan2(v, u), Math.PI / 12)) * r < 0.55) for (int y = L2 - 4; y <= L2 - 2; y++) f.d.set(x, y, z, PORES);
        });
        for (int[] q : new int[][]{{-22, 10}, {22, 10}}) ell(f, q[0], L1 + 2.5, q[1], 2.2, 2.4, 2.2, 0, flesh(s.salt + 77));
        ringStair(f, 12, -14, L1 + 1, L2, L1 - 3, BIRCHSTAIR, Draw.of(STEM_ALL));
    }

    /** The Bellows under the dome's top: two great lungs, the spore chimney rising through the dome to its crown. */
    private void bellows(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -22, -22, 22, 22)) return;
        int ct = p.chTop;
        for (int side = -1; side <= 1; side += 2) ell(f, side * 11, L2 + 4, 3, 4, 3, 5, 0, flesh(s.salt + 81 + side));
        // the chimney: a tube of stalk banded with wart, a ladder up its inside, out onto the crown
        f.cyl(0, 0, 4.5, L2 + 1, ct + 4, Draw.bands(Draw.of(STEM), Draw.of(WART_B), L2, 7));
        f.cyl(0, 0, 2.5, L2 + 1, ct + 4, Draw.AIR);
        f.box(-1, L2 + 1, -4, 1, L2 + 3, -3, Draw.AIR);
        for (int y = L2 + 1; y <= ct + 1; y++) f.set(0, y, 2, b(LADDER, f.facing(Draw.NORTH)));
        for (int k = 0; k < 3; k++) {
            double a = k * 2.2;
            shelf(f, Math.cos(a) * 5.5, Math.sin(a) * 5.5, 78 + k * 7, 0, 0, 4);
        }
        // the crown: a platform round the vent, its railing, the vent's flared lip
        f.disk(0, 0, 8.5, ct, Draw.of(STEM_ALL));
        f.set(0, ct, 2, b(LADDER, f.facing(Draw.NORTH)));
        f.ring(0, 0, 8.5, 0.9, ct + 1, ct + 1, Draw.of(Blocks.ENOKI_STEM));
        f.ring(0, 0, 4.5, 2, ct + 1, ct + 4, Draw.of(WART_B));
        f.box(-1, ct + 1, -4, 1, ct + 2, -3, Draw.AIR);
        f.ring(0, 0, 6.5, 2.5, ct + 5, ct + 5, Draw.of(BROWN_CAP));
        f.set(0, ct + 4, -2, GLOW); f.set(0, ct + 4, 2, GLOW);
        f.wallSign(-1, ct + 3, -5, Draw.NORTH, "THE SPORE", "CHIMNEY", "The Hive's", "breath");
    }

    /** The Mushroom Farm: a fenced plot of wart and mushroom beds, a farmhouse under a cap. */
    private void farm(Draw.Frame f, Colossi.Site s, Plan p) {
        int cu = FARM_U, cv = FARM_V, y = p.farmY;
        if (!touches(f, cu - 16, cv - 12, cu + 16, cv + 12)) return;
        cols(f, cu - 14, cv - 10, cu + 14, cv + 10, (x, z, u, v) -> {
            int fl = s.floorAt(x, z);
            for (int yy = Math.min(fl, y) - 1; yy < y; yy++) f.d.set(x, yy, z, STEM_ALL);
            for (int yy = y + 1; yy <= Math.max(fl, y) + 3; yy++) f.d.set(x, yy, z, 0);
            boolean edge = Math.abs(u - cu) == 14 || Math.abs(v - cv) == 10;
            int row = Math.floorMod(v - cv, 4);
            if (edge) { f.d.set(x, y, z, PODZOL); f.d.set(x, y + 1, z, SPRUCE_FENCE); }
            else if (u - cu < 3 && row == 1) { f.d.set(x, y, z, SOUL); f.d.set(x, y + 1, z, WART_CROP); }
            else if (u - cu < 3 && row == 3) { f.d.set(x, y, z, Blocks.NETHER_MYCELIUM); f.d.set(x, y + 1, z, ((u + v) & 1) == 0 ? Blocks.ORANGE_MUSHROOM : Blocks.BONE_MUSHROOM); }
            else f.d.set(x, y, z, PODZOL);
        });
        f.box(cu + 14, y + 1, cv - 7, cu + 14, y + 1, cv - 5, Draw.AIR);
        // the farmhouse: walls of stalk, a red cap for a roof, a door towards the dome
        int hu = cu + 8, hv = cv;
        f.box(hu - 4, y + 1, hv - 4, hu + 4, y + 6, hv + 4, Draw.of(STEM_ALL));
        f.box(hu - 3, y + 1, hv - 3, hu + 3, y + 6, hv + 3, Draw.AIR);
        f.box(hu - 4, y + 1, hv - 1, hu - 4, y + 3, hv + 1, Draw.AIR);
        f.box(hu + 4, y + 2, hv, hu + 4, y + 3, hv, Draw.of(b(101)));
        cap(f, s, hu, hv, RED, 7, y + 9, 100, s.salt + 91);
        f.set(hu, y + 4, hv, GLOW);
        f.set(hu + 3, y + 1, hv - 3, CAULDRON);
        f.chest(hu + 3, y + 1, hv + 3, Draw.WEST, T);
        // a scarecrow of fence and pumpkin over the beds
        f.box(cu - 4, y + 1, cv - 2, cu - 4, y + 3, cv - 2, Draw.of(SPRUCE_FENCE));
        f.set(cu - 4, y + 4, cv - 2, JACK);
        f.set(cu - 5, y + 3, cv - 2, SPRUCE_FENCE); f.set(cu - 3, y + 3, cv - 2, SPRUCE_FENCE);
        f.wallSign(hu - 5, y + 4, hv - 2, Draw.WEST, "THE MUSHROOM", "FARM", "Wart and cap", "feed the Hive");
    }

    /** The Rotting Larder: a half-sunk cap of a hut, carcasses on hooks, bones, the stench. */
    private void larder(Draw.Frame f, Colossi.Site s, Plan p) {
        int cu = LARDER_U, cv = LARDER_V, y = p.larderY;
        if (!touches(f, cu - 11, cv - 11, cu + 11, cv + 11)) return;
        cols(f, cu - 9, cv - 9, cu + 9, cv + 9, (x, z, u, v) -> {
            if (Math.hypot(u - cu, v - cv) > 9.4) return;
            int fl = s.floorAt(x, z);
            for (int yy = Math.min(fl, y) - 1; yy < y; yy++) f.d.set(x, yy, z, STEM_ALL);
            for (int yy = y + 1; yy <= Math.max(fl, y) + 2; yy++) f.d.set(x, yy, z, 0);
            f.d.set(x, y, z, Draw.rnd(x, 3, z, s.salt + 92) < 0.4 ? SOUL : PODZOL);
        });
        // a half-sunk cap for a roof: a dome over the floor, its inside hollow down to the floor (nothing hollow below)
        double wcx = f.xd(cu, cv), wcz = f.zd(cu, cv);
        cols(f, cu - 9, cv - 9, cu + 9, cv + 9, (x, z, u, v) -> {
            double rr = Math.hypot(u - cu, v - cv);
            if (rr > 8.4) return;
            int out = y + (int) Math.round(7 * Math.sqrt(Math.max(0, 1 - (rr / 8.4) * (rr / 8.4))));
            int in = rr < 6.6 ? y + (int) Math.round(5.8 * Math.sqrt(Math.max(0, 1 - (rr / 6.6) * (rr / 6.6)))) : y;
            for (int yy = y + 1; yy <= out; yy++) f.d.set(x, yy, z, yy <= in ? 0 : b(99, capMeta(x - wcx, z - wcz, 2.4)));
            f.d.set(x, y, z, rr < 6.6 ? (Draw.rnd(x, 4, z, s.salt + 93) < 0.15 ? BONE : SOUL) : STEM_ALL);
        });
        f.box(cu - 1, y + 1, cv + 6, cu + 1, y + 3, cv + 9, Draw.AIR);
        for (int[] q : new int[][]{{-3, -2}, {2, -3}, {-2, 3}, {3, 2}, {0, -4}}) {
            f.box(cu + q[0], y + 4, cv + q[1], cu + q[0], y + 5, cv + q[1], Draw.of(Blocks.EYE_VINE));
            f.set(cu + q[0], y + 3, cv + q[1], ((q[0] + q[1]) & 1) == 0 ? WOOL_RED : WOOL_BROWN);
        }
        f.box(cu - 5, y + 1, cv - 1, cu - 5, y + 2, cv + 1, Draw.of(BONE));
        f.set(cu + 4, y + 1, cv - 3, CAULDRON);
        f.set(cu - 3, y + 4, cv + 3, WEB); f.set(cu + 3, y + 4, cv - 1, WEB);
        f.set(cu, y + 5, cv, Blocks.LUCIS_CENTER);
        f.chest(cu, y + 1, cv - 6, Draw.SOUTH, RICH + "!trap");
        f.spawner(cu + 4, y + 1, cv + 3, "charred_ghoul");
        f.wallSign(cu + 2, y + 3, cv + 9, Draw.SOUTH, "THE ROTTING", "LARDER", "Mind the", "stench");
    }

    /** The chests of the dome, the galleries, the gate and the cache. */
    private void chests(Draw.Frame f, Colossi.Site s, Plan p) {
        f.chest(16, L0 + 1, -4, Draw.NORTH, T);                  // the Spore Vats
        f.chest(-16, L0 + 1, -4, Draw.NORTH, T);                 // the Brood Chamber
        f.chest(16, L0 + 1, 4, Draw.SOUTH, T);                   // the Nursery
        f.chest(-16, L0 + 1, 4, Draw.SOUTH, T);                  // the Mycelium Garden
        f.chest(0, L1 + 1, -26, Draw.SOUTH, T);                  // the Hall of Gills
        f.chest(0, L2 + 1, -21, Draw.SOUTH, RICH);               // the Bellows
        f.chest(0, p.chTop + 1, 5, Draw.SOUTH, RICH);            // the chimney's crown
        for (int[] q : new int[][]{{-6, 19}, {-6, 23}}) f.chest(q[0], L1 + 1, q[1], Draw.EAST, VAULT);
        for (int[] q : new int[][]{{6, 19}, {6, 23}}) f.chest(q[0], L1 + 1, q[1], Draw.WEST, VAULT);
        f.chest(0, L1 + 1, 24, Draw.NORTH, RICH);
        for (int i = 0; i < p.shrooms.size(); i += 2) {          // a chest in every other gallery, against the stem
            Shroom m = p.shrooms.get(i);
            double a = stepAngle(m.gallery(), m.phase) + Math.PI;
            int cu = m.u + (int) Math.round(Math.cos(a) * (m.stem + 1)), cv = m.v + (int) Math.round(Math.sin(a) * (m.stem + 1));
            double ca = Math.cos(a), sa = Math.sin(a);
            int face = Math.abs(ca) >= Math.abs(sa) ? (ca > 0 ? Draw.EAST : Draw.WEST) : (sa > 0 ? Draw.SOUTH : Draw.NORTH);
            f.chest(cu, m.gallery() + 1, cv, face, i < 4 ? RICH : T);
        }
        f.chest(-12, p.gateY + 1, -WALL_R + 3, Draw.EAST, T);    // by the Spore Gate's west stalk
        f.chest(CACHE_U + 2, p.cacheY + 1, CACHE_V, Draw.WEST, T);  // the hollow puffball
        f.wallSign(CACHE_U - 6, p.cacheY + 3, CACHE_V, Draw.WEST, "SPORE FIELDS", "One of these", "is hollow", "");
    }

    // ---- shared shapes ----------------------------------------------------------------------------------------------
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    private static int sector(double du, double dv, int n) {
        double a = Math.atan2(dv, du);
        if (a < 0) a += 2 * Math.PI;
        return Math.min(n - 1, (int) (a / (2 * Math.PI) * n));
    }

    /**
     * A round spiral stair about (cu, cv): stairs in the band rIn..rOut climbing from +u towards +v, n to a turn, one
     * step at every height from ya to yb (the step of a sector lies where (y - phase - sector) mod n == 0), the band clear
     * elsewhere, the core solid.
     */
    private static void spiral(Draw.Frame f, double cu, double cv, double rIn, double rOut, int ya, int yb, int n, int phase, int stairId, Draw.Mat core) {
        int u0 = (int) Math.floor(cu - rOut - 1), u1 = (int) Math.ceil(cu + rOut + 1), v0 = (int) Math.floor(cv - rOut - 1), v1 = (int) Math.ceil(cv + rOut + 1);
        if (!touches(f, u0, v0, u1, v1)) return;
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double du = u - cu, dv = v - cv, dd = Math.sqrt(du * du + dv * dv);
            if (dd > rOut + 0.35) return;
            if (dd <= rIn + 0.35) { for (int y = ya; y <= yb; y++) f.d.set(x, y, z, core.at(x, y, z)); return; }
            int k = sector(du, dv, n);
            int dir = Math.abs(dv) >= Math.abs(du) ? (dv < 0 ? 0 : 1) : (du > 0 ? 2 : 3);
            int st = f.stair(stairId, dir, false);
            for (int y = ya; y <= yb; y++) f.d.set(x, y, z, Math.floorMod(y - phase - k, n) == 0 ? st : 0);
        });
    }

    /** The floor at y round a spiral's band: solid but for the sectors whose steps climb the last three blocks to it. */
    private static void spiralMouth(Draw.Frame f, double cu, double cv, double rIn, double rOut, int y, int n, int phase, Draw.Mat m) {
        int u0 = (int) Math.floor(cu - rOut - 1), u1 = (int) Math.ceil(cu + rOut + 1), v0 = (int) Math.floor(cv - rOut - 1), v1 = (int) Math.ceil(cv + rOut + 1);
        if (!touches(f, u0, v0, u1, v1)) return;
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double du = u - cu, dv = v - cv, dd = Math.sqrt(du * du + dv * dv);
            if (dd > rOut + 0.35 || dd <= rIn + 0.35) return;
            int below = Math.floorMod(y - phase - sector(du, dv, n), n);
            if (below == 0) return;
            f.d.set(x, y, z, below <= 3 ? 0 : m.at(x, y, z));
        });
    }

    /** A square stairwell's steps round the column at (cu, cv): one stair a height from ya to yb, climbing round the ring. */
    private static void ringStair(Draw.Frame f, int cu, int cv, int ya, int yb, int phase, int stairId, Draw.Mat column) {
        for (int y = ya; y <= yb; y++) {
            f.box(cu - 1, y, cv - 1, cu + 1, y, cv + 1, Draw.AIR);
            int i = Math.floorMod(y - phase, 8);
            int[] q = RING[i], nx = RING[(i + 1) % 8];
            int du = nx[0] - q[0], dv = nx[1] - q[1];
            int dir = du > 0 ? 0 : du < 0 ? 1 : dv > 0 ? 2 : 3;
            f.set(cu + q[0], y, cv + q[1], f.stair(stairId, dir, false));
            f.set(cu, y, cv, column);
        }
    }

    private static void ell(Draw.Frame f, double u, double y, double v, double ru, double ry, double rv, double shell, Draw.Mat m) {
        boolean odd = (f.rot & 1) == 1;
        f.d.ellipsoid(f.xd(u, v), y, f.zd(u, v), odd ? rv : ru, ry, odd ? ru : rv, shell, m);
    }

    // ---- clipping helpers ---------------------------------------------------------------------------------------------
    private interface Col { void at(int x, int z, int u, int v); }

    /** Every column of the clip box inside the local rectangle (u0..u1, v0..v1), with its local position. */
    private static void cols(Draw.Frame f, int u0, int v0, int u1, int v1, Col c) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        int x0 = Math.max(Math.min(xa, xb), f.d.x0), x1 = Math.min(Math.max(xa, xb), f.d.x1);
        int z0 = Math.max(Math.min(za, zb), f.d.z0), z1 = Math.min(Math.max(za, zb), f.d.z1);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) c.at(x, z, lu(f, x, z), lv(f, x, z));
    }

    private static int lu(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return z - f.oz; case 2: return f.ox - x; case 3: return f.oz - z; default: return x - f.ox; } }
    private static int lv(Draw.Frame f, int x, int z) { switch (f.rot) { case 1: return f.ox - x; case 2: return f.oz - z; case 3: return x - f.ox; default: return z - f.oz; } }

    private static boolean touches(Draw.Frame f, int u0, int v0, int u1, int v1) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        return f.d.touches(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    // ---- the ordeals ----------------------------------------------------------------------------------------------------
    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        // the Sporefather's seal: the fleshy door behind his throne, to the Spore Heart
        out.add(Ordeals.bossSeal("hive_heart", fr.box(-1, L1 + 1, 16, 1, L1 + 4, 16), RED_CAP, s.kind.lord));
        // the vats' fumes, the larder's stench
        for (int[] q : new int[][]{{12, -12}, {20, -8}, {8, -20}}) out.add(Ordeals.gas(fr.box(q[0] - 3, L0 + 1, q[1] - 3, q[0] + 3, L0 + 3, q[1] + 3)));
        out.add(Ordeals.gas(fr.box(LARDER_U - 6, p.larderY + 1, LARDER_V - 6, LARDER_U + 6, p.larderY + 3, LARDER_V + 6)));
        // rotten patches on the caps
        for (int[] q : p.patches) {
            Shroom m = p.shrooms.get(q[0]);
            int kind = m.kind == ELDER ? BROWN : m.kind, top = m.kind == ELDER ? m.top - 10 : m.top, ya = 999, yb = -999;
            for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) {
                double rr = Math.hypot(q[1] + du - m.u, q[2] + dv - m.v);
                ya = Math.min(ya, capBottom(kind, m.cap, top, rr)); yb = Math.max(yb, capTop(kind, m.cap, top, rr));
            }
            out.add(Ordeals.collapse(fr.box(q[1] - 1, ya, q[2] - 1, q[1] + 1, yb, q[2] + 1), patchBlock(m)));
        }
        // spore pods falling in the north and south gate tunnels
        out.add(Ordeals.rubble(fr.box(-2, L0 + 1, -29, 2, L0 + 5, -23), BROWN_CAP));
        out.add(Ordeals.rubble(fr.box(-2, L0 + 1, 23, 2, L0 + 5, 29), BROWN_CAP));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -----------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double n = Draw.noise(x, z, 14, s.salt + 61), q = Draw.rnd(x, 0, z, s.salt + 62);
        if (n > 0.45) return q < 0.5 ? SOUL : Blocks.LIVELY_NETHERRACK;
        if (n < -0.55) return Blocks.LIVELY_NETHERRACK;
        return q < 0.08 ? PODZOL : Blocks.NETHER_MYCELIUM;
    }

    @Override Draw.Mat under() { return (x, y, z) -> Draw.rnd(x, y, z, 7717) < 0.5 ? Blocks.LIVELY_NETHERRACK : b(87); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < WALL_R + 8; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        // under the dome: eye vines and lucis clusters (never reading the world)
        double q = Draw.rnd(x, 0, z, s.salt + 151);
        if (q < 0.004) { d.set(x, ceil, z, Blocks.LUCIS_CENTER); d.set(x, ceil - 1, z, Blocks.LUCIS_RIM); }
        else if (q < 0.0085 && ceil - floor > 28) { int len = 2 + (int) (Draw.rnd(x, 1, z, s.salt + 152) * 9); for (int k = 0; k < len; k++) d.set(x, ceil - k, z, Blocks.EYE_VINE); }
        if (lake) return;
        // on the floor: molds, young mushrooms on the mycelium, enoki sprigs, thicker in the Spore Fields
        Plan p = (Plan) s.plan;
        int u = lu(p, s, x, z), v = lv(p, s, x, z);
        boolean field = u > 96 && Math.abs(v) < u * 0.42;
        double w = Draw.rnd(x, 9, z, s.salt + 153), k = field ? 2.5 : 1;
        boolean mycel = ground(s, x, floor, z) == Blocks.NETHER_MYCELIUM;
        if (w < 0.03 * k) d.set(x, floor + 1, z, Draw.rnd(x, 10, z, s.salt + 154) < 0.5 ? Blocks.RED_MOLD : Blocks.GRAY_MOLD);
        else if (w < 0.045 * k && mycel) d.set(x, floor + 1, z, Draw.rnd(x, 11, z, s.salt + 155) < 0.5 ? Blocks.ORANGE_MUSHROOM : Blocks.BONE_MUSHROOM);
        else if (w < 0.05 * k) { int h = 1 + (int) (Draw.rnd(x, 12, z, s.salt + 156) * 3); for (int j = 1; j <= h; j++) d.set(x, floor + j, z, Blocks.ENOKI_STEM); d.set(x, floor + h + 1, z, Blocks.ENOKI_CAP); }
    }

    private static int lu(Plan p, Colossi.Site s, int x, int z) { switch (p.rot) { case 1: return z - s.z; case 2: return s.x - x; case 3: return s.z - z; default: return x - s.x; } }
    private static int lv(Plan p, Colossi.Site s, int x, int z) { switch (p.rot) { case 1: return s.x - x; case 2: return s.z - z; case 3: return x - s.x; default: return z - s.z; } }
}
