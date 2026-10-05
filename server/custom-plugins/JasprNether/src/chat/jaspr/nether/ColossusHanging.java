package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Hanging Citadel (owner, 2026-10-04: "Add 10 new structures to the Nether ... I want them to be huge structures"): a
 * grey fortress ninety-one blocks square slung forty blocks over a lake of lava by four colossal chains of three-block
 * links that run up into the cavern's dome (and a fifth, the Crown Chain, from its keep), a black keel of stone hanging
 * beneath it almost to the lava. The ways up:
 * <ul>
 *   <li>the Cliff Gate: a tower carved into a rock promontory at the cavern's edge, its spiral stair climbing to the Chain
 *       Bridge, which sags sixty-five blocks over the lava to the Gate of Chains (gibbet cages hang along it, its dark
 *       planks are rotten, and the guns of the barbican and of the cliff tower watch it);</li>
 *   <li>the Pillar of Ascent: a stone pillar rising from an island in the lake (a causeway of flame vents leads to it),
 *       its spiral stair (arrow slits) climbing to a short bridge and the postern of the Wardens' Quarters.</li>
 * </ul>
 * Inside the curtain wall (wall walks, four corner towers on hanging bartizans, the barbican and the postern): the Court
 * of Gibbets with its gallows, the Cell Shaft (four tiers of barred cells round an atrium over a grate that looks down on
 * the lava), the Black Chapel and its belfry, the Wardens' Quarters, the Wheelhouse (the torture chamber and its pit)
 * and the keep: the Chain Hall, where the Chained Titan stands bound by four chains to its walls. His fall opens the seal
 * at the back of the hall: a stair down to the Titan's Winch (the undercroft), then a spiral down the keel through the
 * Oubliette to the Titan's Hoard at its point. Outside: the Chainworks on the shore, islets of fallen cages in the lake,
 * ghasts in the open air.
 */
final class ColossusHanging extends ColossusDesign {
    // ---- measures (local frame: u east and v south before the plan's turn; the bridge comes from -v) ---------------------
    static final int H = 45, KEEP = 20, BASE = 60, DECK = 72, WALK = 90, TIP = 38, LAVA = 31, LAKE = 108;
    static final int CLIFF = 128, CT_V = -132, CT_R = 10, GATE_V = -122, BAR_V = -57, DECK_CLIFF = 69, CT_TOP = 80;
    static final int PU = 80, PV = 22, PR = 9, P_TOP = 86;      // the Pillar of Ascent
    static final int TC = 42, TR = 8;                           // the corner towers
    static final int CW_U = -100, CW_V = -96;                    // the Chainworks
    static final String T = "jaspr:colossus/hanging", RICH = T + "_rich", VAULT = T + "_vault";
    private static final double SQ2 = Math.sqrt(2);
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    static final class Plan {
        int rot, ctFloor, cwFloor;
        double sag;                                             // the bridge cables' sag
        final double[][] anchors = new double[4][];             // the great chains' ends in the dome (u, y, v)
        final List<int[]> islets = new ArrayList<>();           // u, v, radius
        final List<int[]> rotten = new ArrayList<>();           // the bridge's rotten stretches: v0, v1
        final List<int[]> hangs = new ArrayList<>();            // cages under the citadel: u, v, floor y, big
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    // ---- palette --------------------------------------------------------------------------------------------------------
    private static final int SB = b(98, 0), SB_CRACK = b(98, 2), SB_MOSS = b(98, 1), SB_CHISEL = b(98, 3), AND = b(1, 5), AND_P = b(1, 6);
    private static final int NB = b(112), RNB = b(215), NFENCE = b(113), BLACK_T = b(159, 15), GRAY_T = b(159, 7), OBSIDIAN = b(49), COAL = b(173);
    private static final int MAGMA = b(213), GLOW = b(89), BARS = b(101), RACK = b(87), FIRE = b(51), LAVA_B = b(11), BONE = b(216, 0), WEB = b(30);
    private static final int IRON = b(251, 8), IRON_D = b(251, 7), RUST = b(159, 1), SOUL = b(88), GRAVEL = b(13), BROWN_T = b(159, 12);
    private static final int SLAB_NB = b(44, 6), SPRUCE_SLAB_TOP = b(126, 9);
    private static final int SPRUCE = b(5, 1), DOAK = b(5, 5), DOAK_FENCE = b(191), DOAK_LOG = b(162, 1);
    private static final int RED_GLASS = b(95, 14), BLACK_GLASS = b(95, 15), CAULDRON = b(118), WOOL_RED = b(35, 14), WOOL_WHITE = b(35, 0);
    private static final int CARPET_RED = b(171, 14), YELLOW_T = b(159, 4), YELLOW_C = b(251, 4), END_ROD = b(198, 1), LADDER = 65;
    private static final int SBSTAIR = 109, NBSTAIR = 114, SPRUCESTAIR = 134, DOAKSTAIR = 164;

    private static Draw.Mat stone(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.16 ? SB_CRACK : q < 0.24 ? AND : q < 0.27 ? SB_MOSS : SB; };
    }
    /** The keel's and the bartizans' black stone, glowing here and there. */
    private static Draw.Mat dark(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.05 ? MAGMA : q < 0.2 ? COAL : q < 0.32 ? OBSIDIAN : BLACK_T; };
    }
    private static Draw.Mat iron(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.1 ? RUST : q < 0.45 ? IRON_D : IRON; };
    }
    private static Draw.Mat rock(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.03 ? MAGMA : q < 0.22 ? BLACK_T : q < 0.3 ? BROWN_T : RACK; };
    }
    private static Draw.Mat paving(int salt) {
        return (x, y, z) -> { double q = Draw.rnd(x, y, z, salt); return q < 0.2 ? AND : q < 0.32 ? SB_CRACK : AND_P; };
    }

    // ---- plan -----------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        p.ctFloor = s.floorAt(wx(s, p.rot, 0, GATE_V - 1), wz(s, p.rot, 0, GATE_V - 1));
        p.cwFloor = s.floorAt(wx(s, p.rot, CW_U, CW_V), wz(s, p.rot, CW_U, CW_V));
        // the four great chains run from the corner towers up and out to the dome, a hundred blocks out on the diagonals
        for (int i = 0; i < 4; i++) {
            int su = (i & 1) == 0 ? -1 : 1, sv = (i & 2) == 0 ? -1 : 1;
            double ra = 98 + r.nextDouble() * 8, u = su * ra / SQ2, v = sv * ra / SQ2;
            int iu = (int) Math.round(u), iv = (int) Math.round(v);
            int c = s.ceilAt(wx(s, p.rot, iu, iv), wz(s, p.rot, iu, iv));
            p.anchors[i] = new double[]{u, Math.min(116, c + 2), v};
        }
        // islets of fallen cages in the lake (not under the bridge, the pillar or its causeway)
        for (int t = 0; t < 60 && p.islets.size() < 6; t++) {
            double a = r.nextDouble() * 2 * Math.PI, rad = 64 + r.nextDouble() * 34;
            int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad), rr = 4 + r.nextInt(4);
            if (Math.abs(u) < 18 && v < 0) continue;
            if (Math.hypot(u - PU, v - PV) < 32 || (u > PU - 6 && Math.abs(v - PV) < 12)) continue;
            boolean clash = false;
            for (int[] q : p.islets) if (Math.hypot(q[0] - u, q[1] - v) < 22) clash = true;
            if (!clash) p.islets.add(new int[]{u, v, rr});
        }
        // the bridge's rotten stretches (between its cross-beams)
        for (int i = 0; i < 3; i++) { int v0 = -111 + 16 * i + 4 * r.nextInt(2); p.rotten.add(new int[]{v0, v0 + 2}); }
        // the cables' sag: their lowest point some five blocks over the deck
        double lo = 0, hi = 60;
        for (int it = 0; it < 32; it++) {
            double sag = (lo + hi) / 2, min = 1e9;
            for (int v = GATE_V; v <= BAR_V; v++) min = Math.min(min, cableYd(sag, v) - deckY(v));
            if (min > 4.5) lo = sag; else hi = sag;
        }
        p.sag = lo;
        // gibbets under the citadel's rim
        for (int i = 0; i < 12; i++) {
            double a = (i + 0.2 + r.nextDouble() * 0.6) * Math.PI / 6, rad = 36 + r.nextDouble() * 5;
            int u = (int) Math.round(Math.cos(a) * rad), v = (int) Math.round(Math.sin(a) * rad);
            p.hangs.add(new int[]{u, v, 40 + r.nextInt(12), r.nextInt(3) == 0 ? 1 : 0});
        }
        // the garrisons: every hall, tower, gate and outer work; the great fliers in the open air
        List<Garrison> gs = new ArrayList<>();
        gs.add(g(7, p.ctFloor + 1, GATE_V + 4, "ashbone_archer+skeleton"));           // the foot of the Cliff Gate
        gs.add(g(0, 78, CT_V - 16, "!wither_skeleton+skeleton"));                       // the Bridgewardens' Hall
        gs.add(g(0, deckY(-90) + 1, -90, "spinout+wight"));                             // mid-bridge
        gs.add(g(0, DECK + 1, -40, "!infernal_knight+wither_skeleton"));                // inside the Gate of Chains
        gs.add(g(14, DECK + 1, -30, "zombie_pigman+pigman_berserker"));                 // the Court of Gibbets
        gs.add(g(-38, 67, -12, "wight+lost_soul"));                                     // the Cell Shaft's lowest tier
        gs.add(g(-31, 79, 14, "crypt_guard+shade"));                                    // the Cell Shaft's third tier
        gs.add(g(34, DECK + 1, -14, "cinder_witch+soul_wraith"));                       // the Black Chapel
        gs.add(g(31, DECK + 1, 22, "!dread_rider+royal_guard"));                        // the Wardens' guard hall
        gs.add(g(33, 79, 16, "ember_legionnaire+flame_adept"));                         // the Wardens' dormitory
        gs.add(g(-6, DECK + 1, 28, "charred_ghoul+mummy"));                             // the Wheelhouse
        gs.add(g(4, 65, 34, "deep_crawler+nethermite"));                                // the Wheelhouse's pit
        gs.add(g(-10, 65, -2, "tomb_guardian+crypt_guard"));                            // the Titan's Winch (behind the seal)
        gs.add(g(6, 53, 6, "lost_soul+shade"));                                         // the Oubliette (behind the seal)
        gs.add(g(12, WALK + 1, 12, "ashbone_archer+blaze"));                            // the keep's roof
        gs.add(g(20, WALK + 1, -44, "skeleton+wither_skeleton"));                       // the north wall walk
        gs.add(g(-TC, DECK + 1, TC, "magma_cube+salamander"));                          // the south-west tower
        gs.add(g(PU + 11, LAVA + 2, PV, "magma_hulk+salamander"));                      // the Pillar's island
        gs.add(g(PU - 3, P_TOP + 1, PV + 4, "blaze+ember"));                            // the Pillar's crown
        if (!p.islets.isEmpty()) { int[] q = p.islets.get(0); gs.add(g(q[0], LAVA + 3, q[1], "brimstone_spider+coolmar_spider")); }
        gs.add(g(CW_U + 6, p.cwFloor + 1, CW_V + 2, "brute+hellhound"));                // the Chainworks
        complete(gs);
        gs.add(g(26, 54, -90, "ghast+ghastling"));                                      // under the bridge
        gs.add(g(-84, 70, 22, "ghast+ghastling"));                                      // west of the citadel
        gs.add(g(86, 72, -30, "ghastling+ghastling"));                                  // east, between the chains
        gs.add(g(-30, 46, 76, "ghast+ghastling"));                                      // south, over the lava
        gs.add(g(64, 48, 74, "ghastling+ghast"));                                       // south-east, over the lava
        p.garrisons = gs;
        return p;
    }

    static int wx(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.x - v; case 2: return s.x - u; case 3: return s.x + v; default: return s.x + u; } }
    static int wz(Colossi.Site s, int rot, int u, int v) { switch (rot & 3) { case 1: return s.z + u; case 2: return s.z - v; case 3: return s.z - u; default: return s.z + v; } }

    /** The bridge deck's height at v: sagging from the cliff gate (69) to the barbican (72). */
    static int deckY(int v) {
        double t = Math.max(0, Math.min(1, (v - (GATE_V + 1)) / (double) (BAR_V - (GATE_V + 1))));
        return (int) Math.round(DECK_CLIFF + (DECK - DECK_CLIFF) * t - 16 * t * (1 - t));
    }

    static double cableYd(double sag, double v) {
        double t = Math.max(0, Math.min(1, (v - GATE_V) / (double) (BAR_V + 1 - GATE_V)));
        return CT_TOP - 1 + (88 - (CT_TOP - 1)) * t - 4 * sag * t * (1 - t);
    }

    static double lakeEdge(Colossi.Site s, int x, int z) { return LAKE + Draw.fbm(x, z, 40, s.salt + 101) * 9; }

    static double keelW(int y) { return 2 + (y - TIP) * 28.0 / (BASE - 1 - TIP); }
    static double keelOct(int u, int v) { return Math.max(Math.max(Math.abs(u), Math.abs(v)), (Math.abs(u) + Math.abs(v)) / 1.35); }
    /** The keel's underside at octagonal radius o: stepped in tiers of three. */
    static int keelBottom(double o) {
        if (o <= 2) return TIP;
        int y = TIP + (int) Math.ceil((o - 2) * (BASE - 1 - TIP) / 28.0);
        return TIP + 3 * (int) Math.ceil((y - TIP) / 3.0);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        lake(f, s, p);
        cliff(f, s, p);
        chainworks(f, s, p);
        bridge(f, s, p);
        body(f, s);
        towers(f, s);
        barbican(f, s);
        keep(f, s);
        keel(f, s);
        cells(f, s);
        chapel(f, s);
        quarters(f, s);
        wheelhouse(f, s);
        postern(f, s);
        court(f, s);
        pillar(f, s);
        chains(f, s, p);
        gibbets(f, s, p);
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

    /** The lava lake under the citadel: two deep, a step below the cavern floor; its islets, the pillar's island, the causeway. */
    private void lake(Draw.Frame f, Colossi.Site s, Plan p) {
        int R = LAKE + 14;
        Draw.Mat bank = rock(s.salt + 5);
        cols(f, -R, -R, R, R, (x, z, u, v) -> {
            double dd = Math.sqrt((double) u * u + (double) v * v), edge = lakeEdge(s, x, z);
            if (dd > edge + 3) return;
            int fl = s.floorAt(x, z);
            if (dd > edge) { for (int y = fl + 1; y <= LAVA; y++) f.d.set(x, y, z, bank.at(x, y, z)); return; }
            int top = isle(s, p, x, z, u, v);
            for (int y = LAVA + 1; y <= fl + 4; y++) f.d.set(x, y, z, 0);
            if (top > 0) {
                for (int y = LAVA - 2; y < top; y++) f.d.set(x, y, z, bank.at(x, y, z));
                f.d.set(x, top, z, Draw.rnd(x, 7, z, s.salt + 6) < 0.2 ? MAGMA : ground(s, x, top, z));
            } else {
                f.d.set(x, LAVA, z, LAVA_B);
                f.d.set(x, LAVA - 1, z, LAVA_B);
            }
        });
        // fallen cages on the islets
        for (int[] q : p.islets) {
            if (!touches(f, q[0] - 6, q[1] - 6, q[0] + 6, q[1] + 6)) continue;
            int u = q[0] - 1, v = q[1] - 1, y = LAVA + 2;
            f.box(u - 1, y, v - 1, u + 1, y + 2, v + 1, Draw.of(BARS));
            f.box(u, y, v, u, y + 1, v, Draw.AIR);
            f.box(u - 1, y + 3, v - 1, u + 1, y + 3, v, Draw.of(SLAB_NB));
            f.set(u + 1, y + 3, v + 1, NFENCE);
            f.skull(u, y, v, 0, (q[0] * 3 + q[1]) & 15);
            f.box(q[0] + 2, y, q[1] + 1, q[0] + 3, y, q[1] + 1, Draw.of(BONE));
            f.set(q[0] + 2, y + 1, q[1] + 1, BONE);
        }
        if (!p.islets.isEmpty()) { int[] q = p.islets.get(0); f.chest(q[0] + 2, LAVA + 3, q[1] - 2, Draw.SOUTH, T); }
    }

    /** The rock's top in the lake (the pillar's island, the causeway, the islets), or -1 for lava. */
    private static int isle(Colossi.Site s, Plan p, int x, int z, int u, int v) {
        double n = Draw.noise(x, z, 5, s.salt + 8) * 1.6;
        if (Math.hypot(u - PU, v - PV) < 14 + n) return LAVA + 1;
        if (u > PU && Math.abs(v - PV) <= 3) return LAVA + 1;
        if (!p.islets.isEmpty()) {          // the first islet's causeway, straight out to the shore
            int[] q = p.islets.get(0);
            double len = Math.hypot(q[0], q[1]), ax = q[0] / len, az = q[1] / len, along = u * ax + v * az;
            if (along > len && Math.abs(-u * az + v * ax) <= 1.2) return LAVA + 1;
        }
        for (int[] q : p.islets) {
            double di = Math.hypot(u - q[0], v - q[1]);
            if (di < q[2] + n) return di < q[2] - 2 ? LAVA + 2 : LAVA + 1;
        }
        return -1;
    }

    /** The promontory at the cavern's edge on the bridge's line: rock from the floor to the dome, its face at CLIFF. */
    private void cliff(Draw.Frame f, Colossi.Site s, Plan p) {
        if (touches(f, -104, -186, 104, -CLIFF + 6)) {
            Draw.Mat rk = rock(s.salt + 7);
            cols(f, -104, -186, 104, -CLIFF + 6, (x, z, u, v) -> {
                double dd = Math.sqrt((double) u * u + (double) v * v);
                if (dd > 183) return;
                double a = Math.atan2(u, -v), face = CLIFF + a * a * 220 + Draw.fbm(x, z, 12, s.salt + 111) * 4;
                if (dd < face) return;
                // solid from the floor to the dome; round the gate tower and the hall behind it, on up past them (sealing what lies above)
                double hu = u / 20.0, hv = (v - CT_V + 10) / 22.0;
                int hump = (int) Math.round(96 - 14 * (hu * hu + hv * hv));
                int fl = s.floorAt(x, z), top = Math.min(122, Math.max(s.ceilAt(x, z) + 3, hump));
                for (int y = Math.max(24, fl - 1); y <= top; y++) f.d.set(x, y, z, rk.at(x, y, z));
            });
        }
        cliffTower(f, s, p);
    }

    /** The Cliff Gate: a round tower half out of the cliff, its spiral from the foot to the bridge gate and the battlements, the Bridgewardens' Hall behind. */
    private void cliffTower(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -CT_R - 12, CT_V - 26, CT_R + 12, GATE_V + 6)) return;
        Draw.Mat wall = stone(s.salt + 13), dk = dark(s.salt + 14);
        int fl = p.ctFloor, top = CT_TOP;
        f.cyl(0, CT_V, CT_R, fl - 4, top, wall);
        f.ring(0, CT_V, CT_R, 1, fl - 4, fl + 2, dk);
        f.ring(0, CT_V, CT_R, 1, DECK_CLIFF - 1, DECK_CLIFF - 1, Draw.of(BLACK_T));
        f.cyl(0, CT_V, CT_R - 3, fl + 1, top - 1, Draw.AIR);
        f.disk(0, CT_V, CT_R - 3, fl, Draw.of(AND_P));
        spiral(f, 0, CT_V, 1.4, CT_R - 3.0, fl + 1, top, 16, 1, SBSTAIR, Draw.of(SB_CHISEL));
        f.cyl(0, CT_V, CT_R - 1, top + 1, top + 4, Draw.AIR);
        spiralMouth(f, 0, CT_V, 1.4, CT_R - 3.0, top, 16, 1, Draw.of(AND_P));
        f.ring(0, CT_V, CT_R - 1, 2, top, top, Draw.of(AND_P));
        f.ring(0, CT_V, CT_R, 1, top + 1, top + 1, wall);
        f.ring(0, CT_V, CT_R, 1, top + 2, top + 2, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
        // slits up the stair
        for (int y = fl + 8; y < top - 4; y += 9) for (int k = 0; k < 3; k++) {
            double a = Math.PI * (0.25 + k * 0.25);
            int su = (int) Math.round(Math.cos(a) * (CT_R - 1)), sv = (int) Math.round(Math.sin(a) * (CT_R - 1));
            f.box(su, y, CT_V + sv, su, y + 2, CT_V + sv, Draw.of(BARS));
        }
        // the Cliff Gate at the foot and the bridge gate above it, both towards the citadel; braziers at the foot
        f.box(-2, fl + 1, CT_V + 8, 2, fl + 4, CT_V + CT_R, Draw.AIR);
        f.box(-2, fl, CT_V + 8, 2, fl, CT_V + CT_R + 2, Draw.of(AND_P));
        f.box(-3, fl + 5, CT_V + CT_R, 3, fl + 5, CT_V + CT_R, Draw.of(SB_CHISEL));
        f.box(-2, DECK_CLIFF + 1, CT_V + 8, 2, DECK_CLIFF + 5, CT_V + CT_R, Draw.AIR);
        f.box(-2, DECK_CLIFF, CT_V + 8, 2, DECK_CLIFF, CT_V + CT_R, Draw.of(AND_P));
        f.box(-3, DECK_CLIFF + 6, CT_V + CT_R, 3, DECK_CLIFF + 6, CT_V + CT_R, Draw.of(SB_CHISEL));
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 5, fl + 1, GATE_V + 3, side * 5, fl + 2, GATE_V + 3, Draw.of(NB));
            f.set(side * 5, fl + 3, GATE_V + 3, RACK);
            f.set(side * 5, fl + 4, GATE_V + 3, FIRE);
        }
        f.wallSign(0, fl + 7, CT_V + CT_R + 1, Draw.SOUTH, "THE HANGING", "CITADEL", "Climb, then", "cross the chain");
        f.wallSign(2, DECK_CLIFF + 3, CT_V + 9, Draw.WEST, "Walk the iron", "beams: the", "black planks", "are rotten");
        // the Bridgewardens' Hall, cut into the cliff behind the tower (its door off the stair at 77)
        int hy = 77, h0 = CT_V - 21, h1 = CT_V - 11;
        room(f, -9, h0, 9, h1, hy + 1, hy + 7, Draw.of(AND_P), wall, Draw.mix(SB, GLOW, 0.06, s.salt + 15));
        f.box(-1, hy + 1, h1, 1, hy + 4, CT_V - 7, Draw.AIR);
        f.box(-1, hy, h1, 1, hy, CT_V - 8, Draw.of(AND_P));
        f.box(-4, hy + 1, h0 + 4, 4, hy + 1, h0 + 6, Draw.of(SPRUCE_SLAB_TOP));
        for (int u = -4; u <= 4; u += 2) { f.set(u, hy + 1, h0 + 3, f.stair(SPRUCESTAIR, 3, false)); f.set(u, hy + 1, h0 + 7, f.stair(SPRUCESTAIR, 2, false)); }
        for (int v = h0 + 2; v <= h1 - 2; v += 3) { f.set(-8, hy + 4, v, GLOW); f.set(8, hy + 4, v, GLOW); }
        f.box(-8, hy + 1, h1 - 1, -6, hy + 1, h1 - 1, Draw.of(NFENCE));
        f.chest(-8, hy + 1, h0 + 1, Draw.EAST, T);
        f.chest(8, hy + 1, h0 + 1, Draw.WEST, T);
        f.spawner(6, hy + 1, h1 - 2, "ashbone_archer");
        f.wallSign(-3, hy + 3, h0 + 1, Draw.SOUTH, "BRIDGEWARDENS", "Four chains", "hold up the", "citadel. Pray.");
    }

    /** The Chainworks on the shore: a forge yard, a giant link half made, a crane with a cage, a shed. */
    private void chainworks(Draw.Frame f, Colossi.Site s, Plan p) {
        int cu = CW_U, cv = CW_V, y = p.cwFloor;
        if (!touches(f, cu - 18, cv - 16, cu + 18, cv + 16)) return;
        Draw.Mat rk = rock(s.salt + 21);
        cols(f, cu - 16, cv - 13, cu + 16, cv + 13, (x, z, u, v) -> {
            int fl = s.floorAt(x, z);
            for (int yy = Math.min(fl, y) - 1; yy < y; yy++) f.d.set(x, yy, z, rk.at(x, yy, z));
            for (int yy = y + 1; yy <= Math.max(fl, y) + 3; yy++) f.d.set(x, yy, z, 0);
            f.d.set(x, y, z, Draw.rnd(x, 1, z, s.salt + 22) < 0.3 ? GRAVEL : Draw.rnd(x, 2, z, s.salt + 22) < 0.3 ? BLACK_T : AND);
        });
        // the giant link, lying in the yard half sunk
        link(f, cu - 2, y + 0.5, cv, new double[]{1, 0, 0}, new double[]{0, 0, 1}, 3.5, 3.5, 1.5, 121, iron(s.salt + 23));
        // the forge: a hearth of magma under a hood, the quench trench of lava
        f.box(cu + 8, y + 1, cv - 10, cu + 14, y + 1, cv - 6, Draw.of(NB));
        f.box(cu + 9, y + 1, cv - 9, cu + 13, y + 1, cv - 7, Draw.of(MAGMA));
        f.box(cu + 8, y + 5, cv - 10, cu + 14, y + 5, cv - 6, Draw.of(NB));
        for (int[] q : new int[][]{{8, -10}, {14, -10}, {8, -6}, {14, -6}}) f.box(cu + q[0], y + 2, cv + q[1], cu + q[0], y + 4, cv + q[1], Draw.of(NFENCE));
        f.box(cu + 10, y + 6, cv - 9, cu + 12, y + 9, cv - 7, Draw.of(NB));
        f.box(cu + 11, y + 6, cv - 8, cu + 11, y + 9, cv - 8, Draw.AIR);
        f.box(cu + 5, y - 2, cv + 5, cu + 14, y, cv + 8, Draw.of(NB));
        f.box(cu + 6, y - 1, cv + 6, cu + 13, y, cv + 7, Draw.of(LAVA_B));
        f.set(cu + 7, y + 1, cv - 8, CAULDRON);
        f.set(cu + 15, y + 1, cv - 8, OBSIDIAN);
        // the crane: a mast and a jib, a chain and a cage over the yard
        f.box(cu - 12, y + 1, cv - 9, cu - 12, y + 16, cv - 9, Draw.of(DOAK_FENCE));
        f.box(cu - 13, y + 1, cv - 10, cu - 11, y + 1, cv - 8, Draw.of(NB));
        f.box(cu - 12, y + 17, cv - 9, cu - 2, y + 17, cv - 9, Draw.of(DOAK));
        for (int k = 1; k < 6; k++) f.set(cu - 12 + k, y + 17 - k, cv - 9, f.stair(DOAKSTAIR, 1, true));
        cage(f, s, cu - 3, cv - 9, y + 5, y + 16, false);
        // the shed: nether brick under a slab roof, open to the yard
        int su = cu - 14, sv = cv + 3;
        f.box(su, y + 1, sv, su + 8, y + 5, sv + 8, Draw.of(NB));
        f.box(su + 1, y + 1, sv + 1, su + 7, y + 4, sv + 7, Draw.AIR);
        f.box(su + 8, y + 1, sv + 2, su + 8, y + 3, sv + 6, Draw.AIR);
        f.box(su - 1, y + 6, sv - 1, su + 9, y + 6, sv + 9, Draw.of(SLAB_NB));
        f.set(su + 4, y + 4, sv + 4, GLOW);
        f.chest(su + 1, y + 1, sv + 1, Draw.EAST, T);
        f.box(su + 1, y + 1, sv + 7, su + 3, y + 1, sv + 7, Draw.of(IRON_D));
        f.wallSign(su + 9, y + 4, sv + 1, Draw.EAST, "THE", "CHAINWORKS", "Every link was", "forged here");
    }

    /** The Chain Bridge: a deck of beams and planks sagging between the cliff gate and the barbican, its cables, hangers and gibbets. */
    private void bridge(Draw.Frame f, Colossi.Site s, Plan p) {
        if (!touches(f, -12, GATE_V - 2, 12, BAR_V + 2)) return;
        for (int v = GATE_V + 1; v <= BAR_V; v++) {
            if (!touches(f, -12, v, 12, v)) continue;
            int y = deckY(v), yp = deckY(v - 1), yn = v == BAR_V ? DECK : deckY(v + 1);
            int dir = yp < y ? 2 : yn < y ? 3 : -1;
            boolean rot = false;
            for (int[] q : p.rotten) if (v >= q[0] && v <= q[1]) rot = true;
            for (int u = -2; u <= 2; u++) {
                boolean beam = Math.abs(u) == 2;
                int blk = dir >= 0 ? f.stair(beam ? NBSTAIR : SPRUCESTAIR, dir, false) : beam ? NB : rot ? DOAK : SPRUCE;
                f.set(u, y, v, blk);
                f.box(u, y + 1, v, u, y + 5, v, Draw.AIR);
            }
            f.set(-3, y, v, NB); f.set(3, y, v, NB);
            f.set(-3, y + 1, v, NFENCE); f.set(3, y + 1, v, NFENCE);
            f.set(-2, y - 1, v, NB); f.set(2, y - 1, v, NB);
            if (Math.floorMod(v, 4) == 0) {
                f.box(-4, y - 1, v, 4, y - 1, v, Draw.of(NB));
                int yc = (int) Math.round(cableYd(p.sag, v));
                f.box(-4, y, v, -4, yc - 1, v, Draw.of(BARS));
                f.box(4, y, v, 4, yc - 1, v, Draw.of(BARS));
                if (Math.floorMod(v, 8) == 0) { f.set(-3, y + 2, v, NFENCE); f.set(3, y + 2, v, NFENCE); f.set(-3, y + 3, v, GLOW); f.set(3, y + 3, v, GLOW); }
            }
        }
        Draw.Mat ir = iron(s.salt + 17);
        for (int side = -1; side <= 1; side += 2) cable(f, p, side * 4, ir);
        for (int k = 0; k < 7; k++) {
            int v = GATE_V + 7 + k * 9;
            v -= Math.floorMod(v, 4);
            int side = (k & 1) == 0 ? -1 : 1, y = deckY(v);
            f.box(side * 5, y - 1, v, side * 7, y - 1, v, Draw.of(NFENCE));
            cage(f, s, side * 7, v, y - 9 - (k % 3) * 2, y - 2, k == 3);
        }
    }

    /** A cable of small links along the sagging curve at u. */
    private static void cable(Draw.Frame f, Plan p, int u, Draw.Mat m) {
        double pitch = 4.2, pv = GATE_V, py = cableYd(p.sag, GATE_V);
        int i = 0;
        for (double v = GATE_V; v <= BAR_V + 1; v += 0.25) {
            double y = cableYd(p.sag, v), dl = Math.hypot(v - pv, y - py);
            if (dl < pitch) continue;
            double[] ax = {0, (y - py) / dl, (v - pv) / dl};
            double[] w = (i & 1) == 0 ? new double[]{1, 0, 0} : cross(ax, new double[]{1, 0, 0});
            link(f, u, (y + py) / 2, (v + pv) / 2, ax, w, 1.2, 1.4, 0.5, 121, m);
            pv = v; py = y; i++;
        }
    }

    /** The base slab, the court's paving, the curtain wall (walks, slits, buttresses), the underside and the keel's shell. */
    private void body(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -H - 4, -H - 4, H + 4, H + 4)) return;
        Draw.Mat wall = Draw.bands(stone(s.salt + 1), Draw.of(BLACK_T), DECK + 1, 16), dk = dark(s.salt + 2);
        f.box(-H, BASE, -H, H, DECK - 1, H, stone(s.salt + 3));
        f.box(-H, BASE, -H, H, BASE, H, dk);
        f.box(-H, DECK, -H, H, DECK, H, paving(s.salt + 4));
        f.box(-H, DECK + 1, -H, H, WALK, H, wall);
        f.box(-H + 3, DECK + 1, -H + 3, H - 3, WALK + 2, H - 3, Draw.AIR);
        f.walls(-H, WALK + 1, -H, H, WALK + 1, H, wall);
        f.walls(-H, WALK + 2, -H, H, WALK + 2, H, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
        f.walls(-H, BASE - 1, -H, H, BASE - 1, H, Draw.of(BLACK_T));
        for (int t = -35; t <= 35; t += 7) {
            if (Math.abs(t) > 10) f.box(t, 79, -H, t, 84, -H + 2, Draw.of(BARS));
            f.box(t, 79, H - 2, t, 84, H, Draw.of(BARS));
            f.box(-H, 79, t, -H + 2, 84, t, Draw.of(BARS));
            if (t < 13 || t > 31) f.box(H - 2, 79, t, H, 84, t, Draw.of(BARS));
        }
        for (int t : new int[]{-30, -15, 0, 15, 30}) {
            if (Math.abs(t) > 12) buttress(f, wall, t, 0, -1);
            buttress(f, wall, t, 0, 1);
            buttress(f, wall, t, -1, 0);
            if (t < 12 || t > 32) buttress(f, wall, t, 1, 0);
        }
        // the underside: corbel spikes under the rim, stalactites of black stone under the slab
        cols(f, -H, -H, H, H, (x, z, u, v) -> {
            int o = Math.max(Math.abs(u), Math.abs(v));
            double q = Draw.rnd(x, 3, z, s.salt + 19);
            int len = 0;
            if (o == H && Math.floorMod(u + v, 5) == 0) len = 2 + (int) (q * 4);
            else if (q < 0.03 && keelOct(u, v) > 31) len = 1 + (int) (Draw.rnd(x, 4, z, s.salt + 19) * 7);
            for (int k = 1; k <= len; k++) f.d.set(x, BASE - k, z, k == len ? BLACK_T : dk.at(x, BASE - k, z));
        });
        // the keel: an octagonal point of black stone stepping down in tiers of three, banded and ribbed with iron
        Draw.Mat ir = iron(s.salt + 18);
        cols(f, -31, -31, 31, 31, (x, z, u, v) -> {
            double o = keelOct(u, v);
            if (o > keelW(BASE - 1)) return;
            int yb = keelBottom(o);
            boolean rib = u == 0 || v == 0 || Math.abs(u) == Math.abs(v);
            for (int y = yb; y <= BASE - 1; y++) f.d.set(x, y, z, y == yb ? (((y - TIP) / 3 & 1) == 0 ? IRON : IRON_D) : rib ? ir.at(x, y, z) : dk.at(x, y, z));
        });
    }

    /** A buttress against the curtain's outer face at t along the side (du, dv), stepped at its top. */
    private void buttress(Draw.Frame f, Draw.Mat wall, int t, int du, int dv) {
        if (du != 0) {
            int u0 = du * (H + 1), u1 = du * (H + 2);
            f.box(u0, BASE - 2, t - 1, u1, WALK - 6, t + 1, wall);
            f.box(u1, WALK - 5, t - 1, u1, WALK - 5, t + 1, Draw.of(f.stair(SBSTAIR, du > 0 ? 1 : 0, false)));
            f.box(u0, WALK - 5, t - 1, u0, WALK - 5, t + 1, wall);
        } else {
            int v0 = dv * (H + 1), v1 = dv * (H + 2);
            f.box(t - 1, BASE - 2, v0, t + 1, WALK - 6, v1, wall);
            f.box(t - 1, WALK - 5, v1, t + 1, WALK - 5, v1, Draw.of(f.stair(SBSTAIR, dv > 0 ? 3 : 2, false)));
            f.box(t - 1, WALK - 5, v0, t + 1, WALK - 5, v0, wall);
        }
    }

    /** The four corner towers on their hanging bartizans: a hall at the court, a room at the walks, a conical roof; the chains' eyes. */
    private void towers(Draw.Frame f, Colossi.Site s) {
        Draw.Mat wall = stone(s.salt + 31), dk = dark(s.salt + 32);
        for (int i = 0; i < 4; i++) {
            int su = (i & 1) == 0 ? -1 : 1, sv = (i & 2) == 0 ? -1 : 1, cu = su * TC, cv = sv * TC;
            if (!touches(f, cu - TR - 6, cv - TR - 6, cu + TR + 6, cv + TR + 6)) continue;
            f.d.cone(f.xd(cu, cv), f.zd(cu, cv), 1, TR, 46, BASE - 1, dk);
            f.cyl(cu, cv, TR, BASE, 99, wall);
            f.ring(cu, cv, TR, 1, 88, 88, Draw.of(BLACK_T));
            f.cyl(cu, cv, TR - 2, DECK + 1, 98, Draw.AIR);
            f.disk(cu, cv, TR - 2, DECK, Draw.of(AND_P));
            f.disk(cu, cv, TR - 2, WALK, Draw.of(SB));
            int apex = Math.min(110, s.ceilAt(f.x(cu, cv), f.z(cu, cv)) - 1);
            f.d.cone(f.xd(cu, cv), f.zd(cu, cv), TR + 1.5, 0.4, 100, apex, Draw.of(NB));
            f.disk(cu, cv, TR - 2, 99, Draw.of(SB));
            // the ladder from the hall to the upper room, against the outer wall
            int lu = cu + su * 6;
            for (int y = DECK + 1; y <= 98; y++) f.set(lu, y, cv, b(LADDER, f.facing(su > 0 ? Draw.WEST : Draw.EAST)));
            // doors: to the court, and from the upper room to both wall walks
            f.box(cu - su * 8, DECK + 1, cv - sv * 2, cu - su * 5, DECK + 4, cv, Draw.AIR);
            f.box(cu - su * 8, WALK + 1, cv + sv, cu - su * 5, WALK + 3, cv + sv * 2, Draw.AIR);
            f.box(cu + su, WALK + 1, cv - sv * 8, cu + su * 2, WALK + 3, cv - sv * 5, Draw.AIR);
            // slits
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + Math.PI / 8;
                int wu = cu + (int) Math.round(Math.cos(a) * (TR - 1)), wv = cv + (int) Math.round(Math.sin(a) * (TR - 1));
                f.box(wu, 80, wv, wu, 83, wv, Draw.of(BARS));
                f.box(wu, 93, wv, wu, 95, wv, Draw.of(BARS));
            }
            f.set(cu, 98, cv, GLOW);
            f.set(cu - su * 5, 86, cv, GLOW);
            f.chest(cu, WALK + 1, cv + sv * 6, sv > 0 ? Draw.NORTH : Draw.SOUTH, i == 3 ? RICH : T);
            // the eye where the great chain is shackled to the tower
            ell(f, su * 48.5, 68, sv * 48.5, 2.6, 2.6, 2.6, 0, iron(s.salt + 33));
            f.box(cu + su * 4, 63, cv + sv * 4, cu + su * 6, 71, cv + sv * 6, Draw.of(IRON_D));
        }
    }

    /** The Gate of Chains: the barbican where the bridge arrives, two towers, the passage with its flame vents and portcullis. */
    private void barbican(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -16, BAR_V - 4, 16, -38)) return;
        Draw.Mat wall = stone(s.salt + 41), dk = dark(s.salt + 42);
        f.box(-7, BASE, BAR_V + 1, 7, WALK, -H, wall);
        f.d.cone(f.xd(0, -51), f.zd(0, -51), 1, 6, 50, BASE - 1, dk);
        for (int side = -1; side <= 1; side += 2) {
            int tu = side * 8, tv = -51;
            f.d.cone(f.xd(tu, tv), f.zd(tu, tv), 1, 5, 48, BASE - 1, dk);
            f.cyl(tu, tv, 5, BASE, 96, wall);
            f.cyl(tu, tv, 3, DECK + 1, 95, Draw.AIR);
            f.disk(tu, tv, 3, DECK, Draw.of(AND_P));
            f.disk(tu, tv, 3, 96, Draw.of(SB));
            f.disk(tu, tv, 3, 80, Draw.of(SB));
            f.ring(tu, tv, 5, 1, 97, 97, wall);
            f.ring(tu, tv, 5, 1, 98, 98, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
            for (int y = DECK + 1; y <= 96; y++) f.set(tu + side * 3, y, tv, b(LADDER, f.facing(side > 0 ? Draw.WEST : Draw.EAST)));
            f.box(side * 4, DECK + 1, tv - 1, side * 5, DECK + 3, tv + 1, Draw.AIR);
            f.set(tu, 94, tv, GLOW);
            f.set(tu, 98, tv - 4, COAL);                                // the gun's barrel
        }
        // the passage, the portcullis, the vents, the murder holes and the room over it
        f.box(-3, DECK + 1, BAR_V + 1, 3, DECK + 7, -H + 2, Draw.AIR);
        f.box(-3, DECK, BAR_V + 1, 3, DECK, -H + 2, Draw.of(AND_P));
        f.box(-3, DECK, -53, 3, DECK, -50, Draw.of(MAGMA));
        f.box(-3, DECK + 5, BAR_V + 1, 3, DECK + 7, BAR_V + 1, Draw.of(BARS));
        f.box(-4, DECK + 8, BAR_V + 1, 4, DECK + 9, BAR_V + 1, Draw.of(SB_CHISEL));
        room(f, -4, -55, 4, -46, 81, 88, Draw.of(SB), wall, Draw.of(SB));
        for (int side = -1; side <= 1; side += 2) f.box(side * 4, 81, -52, side * 5, 83, -50, Draw.AIR);   // its doors from the towers
        for (int v : new int[]{-52, -48}) f.box(-2, 80, v, 2, 80, v, Draw.of(BARS));
        for (int u = -3; u <= 3; u++) f.set(u, 84, -50, b(162, 1 | (f.rot % 2 == 0 ? 4 : 8)));
        f.box(0, 81, -54, 0, 83, -54, Draw.of(BARS));
        f.set(0, 87, -50, GLOW);
        f.chest(3, 81, -47, Draw.WEST, T);
        f.wallSign(0, DECK + 10, BAR_V, Draw.NORTH, "GATE OF CHAINS", "The Titan", "sleeps within,", "in his chains");
    }

    /** The postern: a tower on the east wall, its passage into the Wardens' Quarters and the bridge to the Pillar. */
    private void postern(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, 40, 12, PU - PR + 2, 32)) return;
        Draw.Mat wall = stone(s.salt + 51);
        f.d.cone(f.xd(49, 22), f.zd(49, 22), 1, 5, 48, BASE - 1, dark(s.salt + 52));
        f.box(45, BASE, 16, 53, 94, 28, wall);
        for (int k = 0; k < 6; k++) f.box(44 + k, 95 + k, 15 + k, 54 - k, 95 + k, 29 - k, Draw.of(NB));
        f.box(42, DECK + 1, 20, 53, DECK + 5, 24, Draw.AIR);
        f.box(43, DECK, 20, 53, DECK, 24, Draw.of(AND_P));
        f.box(53, DECK + 6, 19, 53, DECK + 6, 25, Draw.of(SB_CHISEL));
        for (int v = 18; v <= 26; v += 4) f.box(53, 82, v, 53, 85, v, Draw.of(BARS));
        f.set(49, DECK + 5, 22, GLOW);
        // the bridge to the Pillar, chained to the postern
        for (int u = 54; u <= PU - PR; u++) {
            f.box(u, DECK, 21, u, DECK, 23, Draw.of(u % 4 == 0 ? NB : SPRUCE));
            f.box(u, DECK - 1, 21, u, DECK - 1, 23, Draw.of(NB));
            f.set(u, DECK + 1, 20, NFENCE); f.set(u, DECK + 1, 24, NFENCE);
            f.box(u, DECK + 1, 21, u, DECK + 4, 23, Draw.AIR);
        }
        for (int side = -1; side <= 1; side += 2)
            f.line(53, 90, PV + side * 2, 63, DECK + 2, PV + side * 2, 0.45, (x, y, z) -> ((x + y + z) & 1) == 0 ? BARS : IRON_D);
        f.wallSign(54, DECK + 6, 26, Draw.EAST, "THE POSTERN", "of the", "Wardens", "");
    }

    /** The keep: the Chain Hall (the Titan's arena), the seal, the annex stair, the Titan's Winch below, the roof, the Crown. */
    private void keep(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -26, -26, 26, 26)) return;
        Draw.Mat wall = Draw.bands(stone(s.salt + 61), Draw.of(BLACK_T), DECK + 2, 6);
        f.box(-KEEP, BASE, -KEEP, KEEP, WALK, KEEP, wall);
        // the Chain Hall: a ring of obsidian round the Titan's place, iron grid beyond
        f.box(-16, DECK + 1, -16, 16, 86, 12, Draw.AIR);
        f.box(-16, DECK, -16, 16, DECK, 12, (x, y, z) -> {
            int u = lu(f, x, z), v = lv(f, x, z);
            double r = Math.hypot(u, v + 2);
            if (r >= 6.5 && r <= 8.2) return Draw.rnd(x, y, z, s.salt + 63) < 0.15 ? MAGMA : OBSIDIAN;
            if (r < 6.5) return BLACK_T;
            return (Math.floorMod(u, 4) == 0 || Math.floorMod(v, 4) == 0) ? GRAY_T : AND_P;
        });
        f.box(-16, 87, -16, 16, 87, 12, Draw.mix(SB, GLOW, 0.03, s.salt + 62));
        for (int v = -12; v <= 8; v += 10) f.box(-16, 86, v, 16, 86, v, Draw.of(SB_CHISEL));
        for (int side = -1; side <= 1; side += 2) {
            for (int v : new int[]{-10, -2, 6}) {
                f.box(side * 15, DECK + 1, v, side * 16, 86, v + 1, Draw.of(AND_P));
                f.set(side * 14, 82, v, GLOW);
            }
            for (int v : new int[]{-14, -6, 2, 10}) { f.set(side * 16, DECK + 1, v, NB); f.set(side * 16, DECK + 2, v, RACK); f.set(side * 16, DECK + 3, v, FIRE); }
            for (int v : new int[]{-12, -4, 4}) f.box(side * 17, 78, v, side * 20, 84, v, Draw.of(BARS));
        }
        // the door from the court, the rose window over it
        f.box(-3, DECK + 1, -KEEP, 3, DECK + 8, -17, Draw.AIR);
        f.box(-4, DECK + 9, -KEEP, 4, DECK + 9, -KEEP, Draw.of(SB_CHISEL));
        for (int u = -3; u <= 3; u++) for (int y = 81; y <= 86; y++) {
            double r = Math.hypot(u, y - 83.5);
            if (r <= 2.7) { f.set(u, y, -KEEP, r > 1.6 ? RED_GLASS : BLACK_GLASS); f.box(u, y, -KEEP + 1, u, y, -17, Draw.AIR); }
        }
        // the four chains that bind the Titan: from the hall's corners to shackles round his place
        Draw.Mat ch = (x, y, z) -> ((x + y + z) & 1) == 0 ? BARS : IRON_D;
        int[][] corners = {{-15, -15}, {15, -15}, {-15, 11}, {15, 11}}, shackles = {{-4, -6}, {4, -6}, {-4, 2}, {4, 2}};
        for (int k = 0; k < 4; k++) {
            int cu = corners[k][0], cv = corners[k][1];
            f.box(cu - (cu < 0 ? 1 : 0), 82, cv - (cv < 0 ? 1 : 0), cu + (cu > 0 ? 1 : 0), 86, cv + (cv > 0 ? 1 : 0), Draw.of(IRON));
            f.line(cu, 84, cv, shackles[k][0], DECK + 3, shackles[k][1], 0.45, ch);
            f.box(shackles[k][0], DECK + 1, shackles[k][1], shackles[k][0], DECK + 2, shackles[k][1], Draw.of(OBSIDIAN));
        }
        for (int v : new int[]{-11, 7}) { f.box(0, 84, v, 0, 86, v, Draw.of(NFENCE)); f.box(-1, 83, v - 1, 1, 83, v + 1, Draw.of(GLOW)); f.box(0, 82, v, 0, 82, v, Draw.of(GLOW)); }
        f.point(0, DECK + 1, -2, "lord:" + s.kind.lord);
        f.wallSign(-6, DECK + 4, -16, Draw.SOUTH, "THE CHAIN HALL", "Four chains", "bind the Titan", "to these walls");
        // the seal at the back of the hall, and the annex with the stair down
        f.box(-1, DECK + 1, 13, 1, DECK + 4, 13, Draw.of(OBSIDIAN));
        f.box(-2, DECK + 5, 13, 2, DECK + 5, 13, Draw.of(SB_CHISEL));
        f.wallSign(0, DECK + 6, 12, Draw.NORTH, "THE KEEL", "Sealed until", "the Titan", "falls");
        f.box(-5, DECK + 1, 14, 5, DECK + 6, 18, Draw.AIR);
        f.set(-5, DECK + 4, 16, GLOW); f.set(5, DECK + 4, 16, GLOW);
        // the Titan's Winch, the undercroft under the hall: two great drums and their gears
        f.box(-16, 65, -16, 16, 70, 16, Draw.AIR);
        f.box(-16, 64, -16, 16, 64, 16, paving(s.salt + 64));
        f.box(-16, 71, -16, 16, 71, 16, Draw.mix(SB, GLOW, 0.04, s.salt + 65));
        int log = b(162, 1 | (f.rot % 2 == 0 ? 4 : 8));
        Draw.Mat ir = iron(s.salt + 66);
        for (int dv : new int[]{-9, 8}) {
            for (int v = dv - 3; v <= dv + 3; v++) for (int y = 64; y <= 71; y++) {
                double q = Math.hypot(v - dv, y - 67.5);
                if (q <= 2.6) f.box(-12, y, v, 12, y, v, Draw.of(log));
                if (q > 1.9 && q <= 2.9) for (int u = -9; u <= 9; u += 6) f.set(u, y, v, ir);
                if (q <= 3.6) { f.set(-13, y, v, SPRUCE); f.set(13, y, v, SPRUCE); }
            }
            for (int u = -10; u <= 10; u += 5) f.box(u, 70, dv - 2, u, 70, dv - 2, Draw.of(BARS));
        }
        ringStair(f, 0, 16, 65, DECK, 64, SBSTAIR, Draw.of(SB_CHISEL));
        f.chest(-15, 65, 14, Draw.EAST, RICH);
        f.wallSign(16, 67, -2, Draw.WEST, "THE TITAN'S", "WINCH", "His hoard lies", "below, in the keel");
        // the keep's roof and its stair from the court, the bartizans at its corners
        f.walls(-KEEP, WALK + 1, -KEEP, KEEP, WALK + 1, KEEP, wall);
        f.walls(-KEEP, WALK + 2, -KEEP, KEEP, WALK + 2, KEEP, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
        for (int k = 0; k <= 17; k++) {
            int v = 18 - k, y = DECK + 1 + k;
            f.box(-23, DECK + 1, v, -21, y - 1, v, wall);
            f.box(-23, y, v, -21, y, v, Draw.of(f.stair(SBSTAIR, 3, false)));
            f.box(-23, y + 1, v, -21, y + 4, v, Draw.AIR);
        }
        f.box(-23, DECK + 1, -2, -21, WALK, 0, wall);
        f.box(-23, WALK + 1, -2, -20, WALK + 4, 0, Draw.AIR);
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
            f.cyl(cu * 19, cv * 19, 2.6, 84, 96, wall);
            f.d.cone(f.xd(cu * 19, cv * 19), f.zd(cu * 19, cv * 19), 3.4, 0.3, 97, 102, Draw.of(NB));
            f.cyl(cu * 19, cv * 19, 1.3, WALK + 1, 95, Draw.AIR);
            f.box(cu * 17, WALK + 1, cv * 17, cu * 18, WALK + 2, cv * 18, Draw.AIR);
        }
        // the Crown: the chain-master's loft on the keep, the fifth chain's foot on its roof
        f.box(-8, WALK + 1, -8, 8, 104, 8, wall);
        f.box(-7, WALK + 1, -7, 7, 97, 7, Draw.AIR);
        f.box(-7, 98, -7, 7, 98, 7, Draw.of(SB));
        f.box(-7, 99, -7, 7, 103, 7, Draw.AIR);
        f.box(-1, WALK + 1, -8, 1, WALK + 3, -8, Draw.AIR);
        for (int y = WALK + 1; y <= 104; y++) f.set(7, y, 0, b(LADDER, f.facing(Draw.WEST)));
        f.walls(-8, 105, -8, 8, 105, 8, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
        for (int t = -4; t <= 4; t += 4) {
            f.box(t, 93, -8, t, 95, -8, Draw.of(BARS)); f.box(t, 93, 8, t, 95, 8, Draw.of(BARS));
            f.box(-8, 100, t, -8, 102, t, Draw.of(BARS)); f.box(8, 100, t + (t == 0 ? 2 : 0), 8, 102, t + (t == 0 ? 2 : 0), Draw.of(BARS));
        }
        f.set(0, 97, 0, GLOW); f.set(0, 103, 0, GLOW);
        f.chest(-7, WALK + 1, 6, Draw.EAST, RICH);
        f.wallSign(-1, WALK + 3, -7, Draw.SOUTH, "THE CROWN", "The fifth", "chain holds", "the keep");
    }

    /** The keel's inside: the spiral from the undercroft down through the Oubliette to the Titan's Hoard at the point. */
    private void keel(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -24, -24, 24, 24)) return;
        f.cyl(0, 0, 9, 53, 58, Draw.AIR);
        f.disk(0, 0, 9, 52, Draw.mix(BLACK_T, SOUL, 0.3, s.salt + 72));
        f.cyl(0, 0, 9, 46, 51, Draw.AIR);
        f.disk(0, 0, 9, 45, (x, y, z) -> ((x + z) & 1) == 0 ? OBSIDIAN : YELLOW_T);
        // the Oubliette: bones, skulls, webs, a cage from the ceiling
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6 + 0.2;
            int u = (int) Math.round(Math.cos(a) * 8), v = (int) Math.round(Math.sin(a) * 8);
            if (k % 3 == 0) f.box(u, 53, v, u, 54, v, Draw.of(BONE));
            else if (k % 3 == 1) f.set(u, 58, v, WEB);
            else f.set(u, 53, v, MAGMA);
        }
        f.skull(7, 53, -3, 0, 4);
        f.skull(-6, 53, 5, 0, 11);
        f.box(-6, 55, -6, -6, 58, -6, Draw.of(BARS));
        f.chest(9, 53, 0, Draw.WEST, T);
        f.spawner(-7, 53, -3, "lost_soul");
        f.wallSign(0, 55, 9, Draw.NORTH, "THE OUBLIETTE", "Forgotten by", "all but the", "Titan");
        // the Hoard: chests round the wall, obsidian columns, heaps of gilt
        for (int[] q : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            f.box(q[0], 46, q[1], q[0], 51, q[1], Draw.of(OBSIDIAN));
            f.set(q[0], 51, q[1], GLOW);
        }
        for (int[] q : new int[][]{{7, 4}, {-7, 4}, {4, -7}, {-4, -7}, {7, -3}, {-3, 7}}) f.set(q[0], 46, q[1], (q[0] + q[1] & 1) == 0 ? YELLOW_C : YELLOW_T);
        f.chest(9, 46, -2, Draw.WEST, VAULT);
        f.chest(9, 46, 2, Draw.WEST, VAULT);
        f.chest(-9, 46, -2, Draw.EAST, VAULT);
        f.chest(-9, 46, 2, Draw.EAST, VAULT);
        f.chest(0, 46, 9, Draw.NORTH, RICH);
        f.chest(0, 46, -9, Draw.SOUTH, RICH);
        f.wallSign(-9, 48, 0, Draw.EAST, "THE TITAN'S", "HOARD", "Taken from all", "who hung here");
        // the spiral from the undercroft's floor down through both
        spiral(f, 0, 0, 1.2, 4.4, 46, 64, 12, 4, SBSTAIR, Draw.of(OBSIDIAN));
        spiralMouth(f, 0, 0, 1.2, 4.4, 64, 12, 4, paving(s.salt + 64));
    }

    /** The Cell Shaft: four tiers of barred cells round a narrow atrium, a ladder, a grate over the drop to the lava. */
    private void cells(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -44, -33, -25, 33)) return;
        Draw.Mat wall = stone(s.salt + 81);
        f.box(-43, 66, -32, -26, 89, 32, wall);
        f.box(-42, 67, -31, -27, 88, 31, Draw.AIR);
        f.box(-42, 66, -31, -27, 66, 31, Draw.of(AND_P));
        f.box(-36, 89, -28, -33, 89, 28, Draw.of(BARS));
        for (int t = -28; t <= 28; t += 7) f.box(-43, 79, t, -43, 84, t, Draw.of(BARS));
        f.box(-35, BASE, -8, -34, 65, 8, Draw.AIR);
        f.box(-35, 66, -8, -34, 66, 8, Draw.of(BARS));
        for (int L = 0; L < 4; L++) {
            int fy = 66 + 6 * L;
            if (L > 0) {
                f.box(-42, fy, -31, -37, fy, 31, Draw.of(SB));
                f.box(-32, fy, -31, -27, fy, 31, Draw.of(SB));
                f.box(-36, fy, -30, -33, fy, -29, Draw.of(SB));
                f.box(-36, fy, -2, -33, fy, 0, Draw.of(SB));
                f.box(-36, fy, 27, -33, fy, 28, Draw.of(SB));
                f.box(-36, fy + 1, -2, -36, fy + 1, 0, Draw.of(NFENCE));
            }
            for (int side = 0; side < 2; side++) {
                int ua = side == 0 ? -42 : -29, ub = side == 0 ? -40 : -27, bars = side == 0 ? -39 : -30;
                int mu = side == 0 ? -41 : -28, back = side == 0 ? -42 : -27;
                f.box(ua, fy + 1, 29, ub, fy + 5, 31, wall);
                f.box(bars, fy + 1, 29, bars, fy + 5, 31, wall);
                for (int k = 0; k < 15; k++) {
                    int c0 = -30 + 4 * k;
                    f.box(ua, fy + 1, c0 - 1, ub, fy + 5, c0 - 1, wall);
                    f.box(bars, fy + 1, c0 - 1, bars, fy + 5, c0 - 1, wall);
                    if ((k & 1) == 0) f.set(bars, fy + 4, c0 - 1, GLOW);
                    if (side == 1 && L == 1 && k == 7) continue;     // the corridor from the court
                    f.box(bars, fy + 1, c0, bars, fy + 5, c0 + 2, Draw.of(BARS));
                    double q = Draw.rnd(k, L, side, s.salt + 82), q2 = Draw.rnd(k, L, side, s.salt + 83);
                    if (q < 0.35) f.box(bars, fy + 1, c0 + 1, bars, fy + 2, c0 + 1, Draw.AIR);
                    int face = side == 0 ? Draw.EAST : Draw.WEST;
                    if ((L == 0 && side == 0 && k == 3) || (L == 2 && side == 1 && k == 10) || (L == 0 && side == 1 && k == 5)) f.chest(back, fy + 1, c0, face, T);
                    else if (L == 3 && side == 0 && k == 12) f.chest(back, fy + 1, c0, face, RICH + "!trap");
                    else if (L == 2 && side == 0 && k == 6) f.spawner(mu, fy + 1, c0 + 1, "wight");
                    else if (q2 < 0.4) f.skull(mu, fy + 1, c0 + 1, 0, (int) (Draw.rnd(k, L, side, s.salt + 84) * 16));
                    else if (q2 < 0.6) { f.set(back, fy + 1, c0 + 2, BONE); f.set(mu, fy + 5, c0, WEB); }
                    else if (q2 < 0.7) f.set(back, fy + 1, c0 + 2, CAULDRON);
                }
            }
        }
        // the ladder at the atrium's north end, the corridor and door from the court, chandeliers down the atrium
        for (int y = 67; y <= 88; y++) { f.set(-35, y, -31, b(LADDER, f.facing(Draw.SOUTH))); f.set(-34, y, -31, b(LADDER, f.facing(Draw.SOUTH))); }
        f.box(-30, DECK + 1, -2, -26, DECK + 3, 0, Draw.AIR);
        for (int v : new int[]{-16, 16}) { f.box(-35, 82, v, -35, 88, v, Draw.of(NFENCE)); f.box(-35, 81, v, -34, 81, v, Draw.of(GLOW)); }
        f.wallSign(-25, DECK + 4, -3, Draw.EAST, "THE CELL SHAFT", "Four tiers", "of the damned", "");
    }

    /** The Black Chapel: a nave of nether brick under a steep open roof, pews, the altar, a rose window, the vestry and the belfry. */
    private void chapel(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, 22, -36, 46, -4)) return;
        Draw.Mat wall = Draw.mix(NB, RNB, 0.15, s.salt + 91);
        int u0 = 25, u1 = 43, v0 = -33, v1 = -6;
        f.box(u0, DECK, v0, u1, 86, v1, wall);
        f.box(u0 + 1, DECK + 1, v0 + 1, u1 - 1, 86, v1 - 1, Draw.AIR);
        f.box(u0 + 1, DECK, v0 + 1, u1 - 1, DECK, v1 - 1, (x, y, z) -> ((x + z) & 1) == 0 ? BLACK_T : GRAY_T);
        gableV(f, u0, u1, v0, v1, 87, NBSTAIR, NB, true, wall);
        // the rose window in the north gable, lancets on the west wall
        for (int u = 30; u <= 38; u++) for (int y = 86; y <= 95; y++) {
            double r = Math.hypot(u - 34, y - 90.5);
            if (r <= 3.6) f.set(u, y, v0, r > 2.6 ? RED_GLASS : r < 0.8 ? GLOW : (u == 34 || y == 90) ? BARS : BLACK_GLASS);
        }
        for (int v : new int[]{-28, -16, -11}) { f.box(u0, 76, v, u0, 82, v, Draw.of(RED_GLASS)); f.set(u0, 83, v, BLACK_GLASS); }
        // the altar on its dais, pews, the aisle, chandeliers
        f.box(29, DECK + 1, -32, 36, DECK + 1, -29, Draw.of(BLACK_T));
        f.box(29, DECK + 1, -28, 36, DECK + 1, -28, Draw.of(f.stair(NBSTAIR, 3, false)));
        f.box(31, DECK + 2, -31, 37, DECK + 2, -31, Draw.of(OBSIDIAN));
        f.set(31, DECK + 3, -31, END_ROD); f.set(35, DECK + 3, -31, END_ROD);
        f.skull(33, DECK + 3, -31, 0, 0);
        f.chest(33, DECK + 2, -32, Draw.SOUTH, RICH);
        for (int v = -26; v <= -10; v += 2) {
            f.box(27, DECK + 1, v, 31, DECK + 1, v, Draw.of(f.stair(NBSTAIR, 2, false)));
            f.box(36, DECK + 1, v, 41, DECK + 1, v, Draw.of(f.stair(NBSTAIR, 2, false)));
        }
        f.box(32, DECK + 1, -27, 35, DECK + 1, -8, Draw.of(CARPET_RED));
        for (int v : new int[]{-24, -14}) { f.box(34, 88, v, 34, 93, v, Draw.of(NFENCE)); f.box(33, 87, v - 1, 35, 87, v + 1, Draw.of(BARS)); f.set(34, 87, v, GLOW); }
        // the vestry
        f.box(37, DECK + 1, -28, 42, 78, -28, wall);
        f.box(37, DECK + 1, -32, 37, 78, -28, wall);
        f.box(37, 79, -32, 42, 79, -28, wall);
        f.box(37, DECK + 1, -30, 37, DECK + 3, -30, Draw.AIR);
        f.set(40, 78, -30, GLOW);
        f.chest(41, DECK + 1, -32, Draw.SOUTH, T);
        f.spawner(40, DECK + 1, -29, "cinder_witch");
        // the belfry
        f.box(25, DECK, -12, 29, 100, -6, wall);
        f.box(26, DECK + 1, -11, 28, 99, -7, Draw.AIR);
        f.box(29, DECK + 1, -10, 29, DECK + 3, -8, Draw.AIR);
        for (int y = DECK + 1; y <= 98; y++) f.set(26, y, -9, b(LADDER, f.facing(Draw.EAST)));
        f.box(27, 94, -12, 27, 97, -6, Draw.AIR);
        f.box(25, 94, -9, 29, 97, -9, Draw.AIR);
        f.set(27, 99, -9, NFENCE); f.box(27, 98, -9, 27, 98, -9, Draw.of(YELLOW_T));
        for (int k = 0; k < 4; k++) f.box(24 + k, 101 + k, -13 + k, 30 - k, 101 + k, -5 - k, Draw.of(NB));
        f.set(27, 105, -9, END_ROD);
        f.box(u0, DECK + 1, -22, u0, DECK + 5, -20, Draw.AIR);
        f.wallSign(u0 - 1, DECK + 4, -23, Draw.WEST, "THE BLACK", "CHAPEL", "Pray to the", "chains");
    }

    /** The Wardens' Quarters: the guard hall on the postern's line, the mess, the armoury, the dormitory and the warden's office. */
    private void quarters(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, 22, 2, 46, 36)) return;
        Draw.Mat wall = stone(s.salt + 101);
        int u0 = 25, u1 = 43, v0 = 4, v1 = 33;
        f.box(u0, DECK, v0, u1, 84, v1, wall);
        f.box(u0 + 1, DECK, v0 + 1, u1 - 1, DECK, v1 - 1, Draw.of(SPRUCE));
        f.box(u0 + 1, DECK + 1, v0 + 1, u1 - 1, 77, v1 - 1, Draw.AIR);
        f.box(u0 + 1, 78, v0 + 1, u1 - 1, 78, v1 - 1, Draw.of(SPRUCE));
        f.box(u0 + 1, 79, v0 + 1, u1 - 1, 83, v1 - 1, Draw.AIR);
        gableV(f, u0, u1, v0, v1, 85, NBSTAIR, NB, false, wall);
        for (int v = v0 + 3; v <= v1 - 3; v += 4) {
            if (v >= 18 && v <= 26) continue;
            f.box(u0, 75, v, u0, 76, v, Draw.of(BARS));
            f.box(u0, 81, v, u0, 82, v, Draw.of(BARS));
        }
        f.box(u0, DECK + 1, 20, u0, DECK + 4, 24, Draw.AIR);
        f.box(u1, DECK + 1, 20, u1, DECK + 4, 24, Draw.AIR);
        // the mess: a long table and its benches, a hearth of magma
        f.box(30, DECK + 1, 10, 38, DECK + 1, 12, Draw.of(SPRUCE_SLAB_TOP));
        f.box(30, DECK + 1, 9, 38, DECK + 1, 9, Draw.of(f.stair(SPRUCESTAIR, 3, false)));
        f.box(30, DECK + 1, 13, 38, DECK + 1, 13, Draw.of(f.stair(SPRUCESTAIR, 2, false)));
        f.box(41, DECK + 1, 6, 42, DECK + 5, 12, Draw.of(NB));
        f.box(41, DECK + 1, 8, 41, DECK + 2, 10, Draw.of(MAGMA));
        f.set(41, DECK + 3, 9, CAULDRON);
        f.chest(26, DECK + 1, 5, Draw.EAST, T);
        // the armoury
        f.box(27, DECK + 1, 32, 37, DECK + 2, 32, Draw.of(NFENCE));
        f.box(27, DECK + 3, 32, 37, DECK + 3, 32, Draw.of(BARS));
        f.chest(26, DECK + 1, 31, Draw.EAST, T);
        f.chest(42, DECK + 1, 31, Draw.WEST, T);
        f.spawner(34, DECK + 1, 29, "infernal_knight");
        // the stair to the dormitory
        for (int k = 0; k <= 5; k++) {
            int v = 13 - k, y = DECK + 1 + k;
            if (k > 0) f.box(39, DECK + 1, v, 40, y - 1, v, Draw.of(SPRUCE));
            f.box(39, y, v, 40, y, v, Draw.of(f.stair(SPRUCESTAIR, 3, false)));
        }
        f.box(39, 78, 9, 40, 78, 13, Draw.AIR);
        // the dormitory: bunks along both walls
        for (int v = 6; v <= 30; v += 3) {
            if (v >= 25) continue;
            f.set(26, 79, v, WOOL_WHITE); f.set(27, 79, v, WOOL_RED);
            if (v > 14) { f.set(42, 79, v, WOOL_WHITE); f.set(41, 79, v, WOOL_RED); }
        }
        // the warden's office
        f.box(35, 79, 25, 42, 83, 25, wall);
        f.box(35, 79, 25, 35, 83, 32, wall);
        f.box(35, 79, 28, 35, 80, 28, Draw.AIR);
        f.box(39, 79, 29, 40, 79, 30, Draw.of(SPRUCE_SLAB_TOP));
        f.chest(42, 79, 32, Draw.WEST, RICH);
        f.wallSign(34, 81, 28, Draw.WEST, "WARDEN'S", "OFFICE", "", "");
        for (int v : new int[]{9, 17, 27}) { f.set(33, 78, v, GLOW); f.set(30, 84, v, GLOW); }
        f.wallSign(u0 - 1, DECK + 4, 19, Draw.WEST, "WARDENS'", "QUARTERS", "The postern", "lies east");
    }

    /** The Wheelhouse: the torture chamber, its wheel, maiden, racks and hooks, the pit below with its cage. */
    private void wheelhouse(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -18, 23, 18, 46)) return;
        Draw.Mat wall = stone(s.salt + 111);
        int u0 = -15, u1 = 15, v0 = 26, v1 = 43;
        f.box(u0, DECK, v0, u1, 83, v1, wall);
        f.box(u0 + 1, DECK + 1, v0 + 1, u1 - 1, 82, v1 - 1, Draw.AIR);
        gableU(f, u0, u1, v0, v1, 84, NBSTAIR, NB, wall);
        for (int t = -14; t <= 14; t += 7) f.box(t, 79, v1, t, 82, v1, Draw.of(BARS));
        f.box(-2, DECK + 1, v0, 2, DECK + 4, v0, Draw.AIR);
        // the pit, sunk into the slab, its railing and ladder
        f.box(-9, 63, 29, 9, DECK, 40, wall);
        f.box(-8, 65, 30, 8, DECK, 39, Draw.AIR);
        f.box(-8, 64, 30, 8, 64, 39, Draw.mix(BLACK_T, SOUL, 0.4, s.salt + 112));
        f.walls(-9, DECK + 1, 29, 9, DECK + 1, 40, Draw.of(NFENCE));
        f.set(0, DECK + 1, 40, 0);
        for (int y = 65; y <= DECK; y++) f.set(0, y, 39, b(LADDER, f.facing(Draw.NORTH)));
        for (int[] q : new int[][]{{-7, 31}, {7, 31}, {-7, 38}, {3, 31}}) { f.set(q[0], 65, q[1], BONE); f.set(q[0], 66, q[1], NFENCE); }
        f.skull(-7, 67, 31, 0, 6);
        f.skull(7, 67, 31, 0, 10);
        f.set(-5, 71, 33, WEB); f.set(5, 71, 36, WEB);
        f.chest(-8, 65, 38, Draw.EAST, RICH);
        f.spawner(6, 65, 37, "charred_ghoul");
        // the beam over the pit and the cage it lowers
        int beam = b(162, 1 | (f.rot % 2 == 0 ? 4 : 8));
        f.box(-14, 80, 34, 14, 80, 34, Draw.of(beam));
        cage(f, s, -4, 34, 67, 79, false);
        // the wheel, the maiden, the racks, the hooks
        for (int u = -14; u <= -4; u++) for (int y = 73; y <= 83; y++) {
            double r = Math.hypot(u + 9, y - 78);
            if (Math.abs(r - 4) <= 0.6) f.set(u, y, 41, SPRUCE);
            else if (r < 3.5 && (u == -9 || y == 78)) f.set(u, y, 41, DOAK_FENCE);
        }
        f.set(-9, 78, 41, DOAK_LOG);
        f.skull(-9, 78, 40, 0, 0);
        f.box(9, DECK + 1, 39, 11, DECK + 3, 41, Draw.of(BARS));
        f.box(10, DECK + 1, 40, 10, DECK + 2, 40, Draw.AIR);
        f.box(10, DECK + 1, 39, 10, DECK + 2, 39, Draw.AIR);
        f.skull(10, DECK + 1, 40, 0, 0);
        f.box(9, DECK, 36, 11, DECK, 38, Draw.of(MAGMA));
        f.box(-2, DECK + 1, 41, 4, DECK + 1, 41, Draw.of(SPRUCE_SLAB_TOP));
        for (int u = -2; u <= 4; u += 3) f.set(u, DECK + 2, 41, NFENCE);
        for (int u : new int[]{-12, 12}) { f.box(u, 79, 30, u, 82, 30, Draw.of(NFENCE)); f.set(u, 78, 30, BARS); }
        f.set(-13, DECK + 1, 28, CAULDRON); f.set(13, DECK + 1, 28, CAULDRON);
        f.set(-12, 82, 37, GLOW); f.set(12, 82, 37, GLOW); f.set(0, 82, 28, GLOW);
        f.chest(-14, DECK + 1, 33, Draw.EAST, T);
        f.wallSign(3, DECK + 4, v0 - 1, Draw.NORTH, "THE", "WHEELHOUSE", "", "");
    }

    /** The Court of Gibbets: a gallows with three cages, braziers, lamp posts. */
    private void court(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, -26, -43, 26, 43)) return;
        int beam = b(162, 1 | (f.rot % 2 == 0 ? 4 : 8));
        f.box(-9, DECK + 1, -34, 9, DECK + 1, -30, Draw.of(DOAK));
        for (int u : new int[]{-8, 8}) f.box(u, DECK + 2, -32, u, DECK + 9, -32, Draw.of(DOAK_LOG));
        f.box(-8, DECK + 10, -32, 8, DECK + 10, -32, Draw.of(beam));
        for (int u : new int[]{-4, 0, 4}) cage(f, s, u, -32, DECK + 3, DECK + 9, false);
        f.box(-1, DECK + 1, -35, 1, DECK + 1, -35, Draw.of(f.stair(SPRUCESTAIR, 2, false)));
        for (int[] q : new int[][]{{-6, -22}, {6, -22}, {-20, -40}, {20, -40}, {-18, 38}, {18, 38}}) brazier(f, q[0], q[1], DECK + 1);
        for (int[] q : new int[][]{{-24, 24}, {-24, -24}, {23, 0}, {0, 24}, {-12, 24}, {12, 24}}) {
            f.box(q[0], DECK + 1, q[1], q[0], DECK + 3, q[1], Draw.of(NFENCE));
            f.set(q[0], DECK + 4, q[1], GLOW);
        }
    }

    /** The Pillar of Ascent: a pillar of banded stone from the lake to the bridge's height, its spiral, slits and crown. */
    private void pillar(Draw.Frame f, Colossi.Site s) {
        if (!touches(f, PU - 16, PV - 16, PU + 26, PV + 16)) return;
        Draw.Mat wall = Draw.bands(stone(s.salt + 121), Draw.of(BLACK_T), LAVA, 7);
        int base = LAVA + 1;
        f.cyl(PU, PV, PR + 2, LAVA - 2, base + 1, dark(s.salt + 122));
        f.cyl(PU, PV, PR, base, P_TOP, wall);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4, cu = PU + Math.cos(a) * (PR + 0.2), cv = PV + Math.sin(a) * (PR + 0.2);
            f.line(cu, base, cv, cu, P_TOP - 3, cv, 1.0, dark(s.salt + 123));
        }
        f.cyl(PU, PV, PR - 2.6, base + 1, P_TOP - 1, Draw.AIR);
        spiral(f, PU, PV, 1.4, PR - 2.6, base + 1, P_TOP, 16, 0, SBSTAIR, Draw.of(SB_CHISEL));
        f.cyl(PU, PV, PR - 1, P_TOP + 1, P_TOP + 4, Draw.AIR);
        spiralMouth(f, PU, PV, 1.4, PR - 2.6, P_TOP, 16, 0, Draw.of(AND_P));
        f.ring(PU, PV, PR - 1, 2, P_TOP, P_TOP, Draw.of(AND_P));
        f.ring(PU, PV, PR, 1, P_TOP + 1, P_TOP + 1, wall);
        f.ring(PU, PV, PR, 1, P_TOP + 2, P_TOP + 2, (x, y, z) -> ((x + z) & 1) == 0 ? SB : 0);
        // doors: at the foot (towards the causeway) and at the postern's bridge
        f.box(PU + 7, base + 1, PV - 1, PU + PR, base + 3, PV + 1, Draw.AIR);
        f.box(PU - PR, DECK + 1, PV - 1, PU - 7, DECK + 4, PV + 1, Draw.AIR);
        f.box(PU - PR, DECK, PV - 1, PU - 7, DECK, PV + 1, Draw.of(AND_P));
        // slits round the stair
        for (int y = base + 6; y < P_TOP - 4; y += 8) for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + y * 0.3;
            int su = (int) Math.round(Math.cos(a) * (PR - 1)), sv = (int) Math.round(Math.sin(a) * (PR - 1));
            f.box(PU + su, y, PV + sv, PU + su, y + 2, PV + sv, Draw.of(BARS));
        }
        // the crown: a brazier on the core, a chest by the parapet
        f.set(PU, P_TOP + 1, PV, RACK); f.set(PU, P_TOP + 2, PV, FIRE);
        f.chest(PU + PR - 2, P_TOP + 1, PV, Draw.WEST, T);
        // the causeway's flame vents
        f.box(PU + 17, base, PV - 1, PU + 22, base, PV + 1, Draw.of(MAGMA));
        f.wallSign(PU + PR + 1, base + 4, PV, Draw.EAST, "THE PILLAR OF", "ASCENT", "Mind the slits", "on the stair");
    }

    /** The four great chains from the corner towers into the dome, their anchors, and the Crown Chain. */
    private void chains(Draw.Frame f, Colossi.Site s, Plan p) {
        Draw.Mat i1 = iron(s.salt + 131), i2 = iron(s.salt + 132), dk = dark(s.salt + 133);
        for (int i = 0; i < 4; i++) {
            int su = (i & 1) == 0 ? -1 : 1, sv = (i & 2) == 0 ? -1 : 1;
            double[] a = {su * 48.5, 68, sv * 48.5}, e = p.anchors[i];
            chain(f, a, e, 3.5, 3.5, 1.5, 121, i1, i2);
            int ceil = (int) e[1] - 2;
            double ry = Math.min(5, 122 - (ceil + 2));
            ell(f, e[0], ceil + 2, e[2], 7, ry, 7, 0, dk);
            f.ring(e[0], e[2], 5.5, 1.6, ceil - 3, ceil - 2, i2);
        }
        chain(f, new double[]{0, 104, 0}, new double[]{0, 126, 0}, 2.0, 3.0, 1.4, 121, i1, i2);
        f.ring(0, 0, 3.4, 1.2, 105, 105, i2);
        ell(f, 0, 120, 0, 7, 2, 7, 0, dk);
    }

    /** Cages under the citadel's rim, on the keel's arms, and the Last Gibbet under the keel's point. */
    private void gibbets(Draw.Frame f, Colossi.Site s, Plan p) {
        for (int[] h : p.hangs) cage(f, s, h[0], h[1], h[2], BASE - 1, h[3] == 1);
        for (int k = 0; k < 4; k++) {
            int du = k == 0 ? 1 : k == 1 ? -1 : 0, dv = k == 2 ? 1 : k == 3 ? -1 : 0;
            for (int r = 17; r <= 24; r++) f.set(du * r, 52, dv * r, NFENCE);
            cage(f, s, du * 24, dv * 24, 42 + k % 2 * 2, 51, false);
        }
        cage(f, s, 0, 0, LAVA + 1, TIP - 1, false);
    }

    // ---- shared shapes -----------------------------------------------------------------------------------------------
    /** A gibbet: a cage of iron bars (one block inside, or three with big) with its floor at y, hung by a chain from (u, top, v). */
    private static void cage(Draw.Frame f, Colossi.Site s, int u, int v, int y, int top, boolean big) {
        int r = big ? 2 : 1, h = big ? 4 : 3;
        if (!touches(f, u - r - 1, v - r - 1, u + r + 1, v + r + 1)) return;
        f.box(u - r, y, v - r, u + r, y, v + r, Draw.of(NB));
        f.box(u - r, y + 1, v - r, u + r, y + h, v + r, Draw.of(BARS));
        f.box(u - r + 1, y + 1, v - r + 1, u + r - 1, y + h, v + r - 1, Draw.AIR);
        f.box(u - r, y + h + 1, v - r, u + r, y + h + 1, v + r, Draw.of(SLAB_NB));
        for (int yy = y + h + 2; yy <= top; yy++) f.set(u, yy, v, (yy & 1) == 0 ? BARS : NFENCE);
        double q = Draw.rnd(u, y, v, s.salt + 141);
        if (q < 0.55) f.skull(u, y + 1, v, 0, (int) (Draw.rnd(u, y, v, s.salt + 142) * 16));
        else if (q < 0.8) f.set(u, y + 1, v, BONE);
    }

    private static void brazier(Draw.Frame f, int u, int v, int y) { f.set(u, y, v, NB); f.set(u, y + 1, v, RACK); f.set(u, y + 2, v, FIRE); }

    /** A gabled roof whose ridge runs along v over u0..u1 from y; hollow leaves the space under it open. */
    private static void gableV(Draw.Frame f, int u0, int u1, int v0, int v1, int y, int stair, int under, boolean hollow, Draw.Mat gable) {
        for (int k = 0; ; k++) {
            int ua = u0 + k, ub = u1 - k, yy = y + k;
            if (ua > ub) break;
            f.box(ua, yy, v0, ub, yy, v0, gable); f.box(ua, yy, v1, ub, yy, v1, gable);
            if (ub - ua <= 1) { f.box(ua, yy, v0 - 1, ub, yy, v1 + 1, Draw.of(under)); f.box(ua, yy + 1, v0 - 1, ub, yy + 1, v1 + 1, Draw.of(SLAB_NB)); break; }
            f.box(ua, yy, v0 - 1, ua, yy, v1 + 1, Draw.of(f.stair(stair, 0, false)));
            f.box(ub, yy, v0 - 1, ub, yy, v1 + 1, Draw.of(f.stair(stair, 1, false)));
            f.box(ua + 1, yy, v0, ua + 1, yy, v1, Draw.of(under));
            f.box(ub - 1, yy, v0, ub - 1, yy, v1, Draw.of(under));
            if (!hollow && ua + 2 <= ub - 2) f.box(ua + 2, yy, v0, ub - 2, yy, v1, Draw.of(under));
        }
    }

    /** A gabled roof whose ridge runs along u over v0..v1 from y (solid beneath). */
    private static void gableU(Draw.Frame f, int u0, int u1, int v0, int v1, int y, int stair, int under, Draw.Mat gable) {
        for (int k = 0; ; k++) {
            int va = v0 + k, vb = v1 - k, yy = y + k;
            if (va > vb) break;
            f.box(u0, yy, va, u0, yy, vb, gable); f.box(u1, yy, va, u1, yy, vb, gable);
            if (vb - va <= 1) { f.box(u0 - 1, yy, va, u1 + 1, yy, vb, Draw.of(under)); f.box(u0 - 1, yy + 1, va, u1 + 1, yy + 1, vb, Draw.of(SLAB_NB)); break; }
            f.box(u0 - 1, yy, va, u1 + 1, yy, va, Draw.of(f.stair(stair, 2, false)));
            f.box(u0 - 1, yy, vb, u1 + 1, yy, vb, Draw.of(f.stair(stair, 3, false)));
            if (va + 1 <= vb - 1) f.box(u0, yy, va + 1, u1, yy, vb - 1, Draw.of(under));
        }
    }

    /** One link of a chain: a ring of rod radius rt round a stadium (straight half a, radius rr) centred at (cu, cy, cv),
     *  its long axis ax and in-plane width axis wv (unit vectors in u, y, v order); nothing above yMax. */
    private static void link(Draw.Frame f, double cu, double cy, double cv, double[] ax, double[] wv, double a, double rr, double rt, int yMax, Draw.Mat m) {
        double ext = a + rr + rt + 0.6;
        int u0 = (int) Math.floor(cu - ext), u1 = (int) Math.ceil(cu + ext), v0 = (int) Math.floor(cv - ext), v1 = (int) Math.ceil(cv + ext);
        if (!touches(f, u0, v0, u1, v1)) return;
        double[] nv = cross(ax, wv);
        int ya = Math.max(1, (int) Math.floor(cy - ext)), yb = Math.min(yMax, (int) Math.ceil(cy + ext));
        double lim = (rt + 0.3) * (rt + 0.3);
        cols(f, u0, v0, u1, v1, (x, z, u, v) -> {
            double pu = u - cu, pv = v - cv;
            for (int y = ya; y <= yb; y++) {
                double py = y - cy;
                double n = pu * nv[0] + py * nv[1] + pv * nv[2];
                if (n * n > lim) continue;
                double sa = Math.abs(pu * ax[0] + py * ax[1] + pv * ax[2]), w = pu * wv[0] + py * wv[1] + pv * wv[2], dc;
                if (sa <= a) dc = Math.abs(Math.abs(w) - rr);
                else { double ds = sa - a; dc = Math.abs(Math.sqrt(ds * ds + w * w) - rr); }
                if (dc * dc + n * n <= lim) f.d.set(x, y, z, m.at(x, y, z));
            }
        });
    }

    /** A chain of interlocking links from a to b (local u, y, v), alternate links turned a quarter about the axis. */
    private static void chain(Draw.Frame f, double[] a, double[] b, double la, double rr, double rt, int yMax, Draw.Mat m1, Draw.Mat m2) {
        double du = b[0] - a[0], dy = b[1] - a[1], dv = b[2] - a[2], len = Math.sqrt(du * du + dy * dy + dv * dv);
        double[] ax = {du / len, dy / len, dv / len};
        double[] w1 = Math.abs(ax[1]) < 0.9 ? norm(new double[]{-ax[2], 0, ax[0]}) : new double[]{1, 0, 0};
        double[] w2 = cross(ax, w1);
        double pitch = 2 * (la + rr - rt) - 0.3;
        int n = (int) Math.ceil(len / pitch);
        for (int i = 0; i < n; i++) {
            double t = pitch * (i + 0.5);
            link(f, a[0] + ax[0] * t, a[1] + ax[1] * t, a[2] + ax[2] * t, ax, (i & 1) == 0 ? w1 : w2, la, rr, rt, yMax, (i & 1) == 0 ? m1 : m2);
        }
    }

    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    private static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

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

    /** A room: floor, walls and roof materials, the inside cleared (floor at yb - 1, ceiling at yt). */
    private static void room(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int yt, Draw.Mat floor, Draw.Mat wall, Draw.Mat ceil) {
        f.box(u0, yb - 1, v0, u1, yt, v1, wall);
        f.box(u0, yt, v0, u1, yt, v1, ceil);
        f.box(u0 + 1, yb, v0 + 1, u1 - 1, yt - 1, v1 - 1, Draw.AIR);
        f.box(u0 + 1, yb - 1, v0 + 1, u1 - 1, yb - 1, v1 - 1, floor);
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
        // the Titan's seal: the door at the back of the Chain Hall, down to the keel and his hoard
        out.add(Ordeals.bossSeal("hanging_keel", fr.box(-1, DECK + 1, 13, 1, DECK + 4, 13), OBSIDIAN, s.kind.lord));
        // the bridge: rotten planks, the barbican's guns and the cliff tower's
        for (int[] q : p.rotten) {
            int ya = Math.min(deckY(q[0]), deckY(q[1])), yb = Math.max(deckY(q[0]), deckY(q[1]));
            out.add(Ordeals.collapse(fr.box(-1, ya, q[0], 1, yb, q[1]), DOAK));
        }
        for (int side = -1; side <= 1; side += 2) out.add(Ordeals.cannon(fr.box(-9, 56, -104, 9, 86, BAR_V), fr.at(side * 8, 99, -55)));
        out.add(Ordeals.cannon(fr.box(-9, 56, GATE_V + 1, 9, 86, -86), fr.at(0, CT_TOP + 2, GATE_V - 2)));
        // flame vents: the Gate of Chains, the maiden in the Wheelhouse, the causeway to the Pillar
        out.add(Ordeals.flames(fr.box(-3, DECK + 1, -53, 3, DECK + 3, -50), 70));
        out.add(Ordeals.flames(fr.box(9, DECK + 1, 36, 11, DECK + 3, 38), 90));
        out.add(Ordeals.flames(fr.box(PU + 17, LAVA + 2, PV - 1, PU + 22, LAVA + 4, PV + 1), 80));
        // the Pillar's arrow slits
        int[] lowStair = fr.box(PU - 6, 40, PV - 6, PU + 6, 50, PV + 6), highStair = fr.box(PU - 6, 58, PV - 6, PU + 6, 68, PV + 6);
        out.add(Ordeals.arrows(lowStair, lowStair));
        out.add(Ordeals.arrows(highStair, highStair));
        // the keel: rubble in the shaft, gas in the Oubliette; gas in the Wheelhouse's pit
        out.add(Ordeals.rubble(fr.box(-4, 59, -4, 4, 63, 4), GRAVEL));
        out.add(Ordeals.gas(fr.box(-8, 53, -8, 8, 55, 8)));
        out.add(Ordeals.gas(fr.box(-8, 65, 30, 8, 67, 39)));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -----------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double q = Draw.rnd(x, 0, z, s.salt + 61);
        if (q < 0.3 && s.dist(x, z) < lakeEdge(s, x, z) + 7) return MAGMA;
        return q < 0.22 ? SOUL : q < 0.4 ? GRAVEL : q < 0.55 ? BLACK_T : RACK;
    }

    @Override Draw.Mat under() { return rock(9157); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < LAKE + 26; }

    @Override double lakeLevel() { return 0.42; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        // glowstone and hanging chains under the dome (never reading the world)
        double q = Draw.rnd(x, 0, z, s.salt + 151);
        if (q < 0.004) { int len = 1 + (int) (Draw.rnd(x, 1, z, s.salt + 151) * 3); for (int k = 0; k < len; k++) d.set(x, ceil - k, z, GLOW); }
        else if (q < 0.0063 && ceil - floor > 24) {
            int len = 3 + (int) (Draw.rnd(x, 2, z, s.salt + 152) * 12);
            for (int k = 0; k < len; k++) d.set(x, ceil - k, z, (k & 1) == 0 ? BARS : NFENCE);
        }
        if (lake || s.dist(x, z) < lakeEdge(s, x, z) + 5) return;
        int cx = wx(s, ((Plan) s.plan).rot, CW_U, CW_V), cz = wz(s, ((Plan) s.plan).rot, CW_U, CW_V);
        boolean nearWood = Math.abs(x - cx) < 26 && Math.abs(z - cz) < 26;
        double w = Draw.rnd(x, 9, z, s.salt + 153);
        if (w < 0.006) { d.set(x, floor + 1, z, BONE); if (w < 0.003) d.set(x, floor + 2, z, BONE); }
        else if (w < 0.01 && !nearWood) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
        else if (w < 0.014) { int len = 1 + (int) (Draw.rnd(x, 10, z, s.salt + 154) * 4); for (int k = 1; k <= len; k++) d.set(x, floor + k, z, BLACK_T); }
    }
}
