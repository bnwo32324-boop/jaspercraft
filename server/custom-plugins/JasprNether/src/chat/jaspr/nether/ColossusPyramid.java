package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * The Great Pyramid (owner, 2026-10-01: "One should be a giant pyramid"): a smooth-cased sandstone pyramid 177 blocks
 * square and 84 high, its arrises traced in glowstone and its pyramidion glowing, in a cavern of soul-sand dunes. An
 * avenue of sphinxes leads to the Great Sphinx, where the Sphinx Sentinel keeps the way, and on to the grand stair up the
 * north face. Inside, on five levels:
 * <ul>
 *   <li>the Hall of the Ka behind the north portal, with the Queen's Chamber to the west (Vizier Hekkat) and the Hall of
 *       Stars to the east (four levers, set in the order the labyrinth's shrines tell, open a niche of the Hapy jar);</li>
 *   <li>the Labyrinth on the ground floor, its corridors trapped (arrows, falling floors over sand pits, rubble, flame and
 *       poison), its four shrines and burial rooms, reached from the hall and through false doors in the east and west
 *       faces, and under its pillared heart the Scarab Pit (the Scarab Matriarch);</li>
 *   <li>the Grand Gallery climbing to the King's Chamber, sealed until the four canopic jars are shown (the Sphinx,
 *       the Vizier, the Matriarch and the Hall of Stars give them), where Akhemet the Sunless Pharaoh rises;</li>
 *   <li>the treasury behind him (opened by his fall), the relieving chambers above and the Sun Chamber under the
 *       apex.</li>
 * </ul>
 * Three queens' pyramids, corner obelisks and a mortuary court stand around it.
 */
final class ColossusPyramid extends ColossusDesign {
    static final int H = 88, HEIGHT = 84;                 // half base and height
    static final int MAZE = 72, PITCH = 4;                 // the labyrinth's half size and cell pitch
    static final int MN = MAZE * 2 / PITCH;                // cells per side (36)
    static final int L2 = 18, KING = 40;                   // floor heights above the base: the Ka hall level, the King's Chamber
    // the plan along v (south): the Ka hall, the wing corridors and rooms, the gallery's foot and top, the antechamber,
    // the King's Chamber and the treasury
    static final int KA_V0 = -59, KA_V1 = -45, SIDE_V = -47, WING_V = -40, GAL_V0 = -40, GAL_V1 = GAL_V0 + (KING - L2);
    static final int ANTE_V1 = GAL_V1 + 6, KING_V0 = ANTE_V1 + 1, KING_V1 = KING_V0 + 22, TREAS_V1 = KING_V1 + 8;
    static final String[] JARS = {"canopic_jar_imsety", "canopic_jar_hapy", "canopic_jar_duamutef", "canopic_jar_qebehsenuef"};
    static final String[] SIGNS = {"the Man", "the Baboon", "the Jackal", "the Falcon"};

    static final class Plan {
        int rot;
        boolean[][] east = new boolean[MN][MN], south = new boolean[MN][MN];   // open passages of the labyrinth
        int[] order = new int[4];                                              // lever order (indices into SIGNS)
        final List<int[]> rooms = new ArrayList<>();                           // burial rooms: cell u, cell v (3x3 cells)
        final List<int[]> traps = new ArrayList<>();                           // cell u, cell v, kind (0 arrows 1 collapse 2 rubble 3 flame 4 gas)
        List<Ordeals.Ordeal> ordeals;                                          // in world coordinates, made once
    }

    // ---- palette --------------------------------------------------------------------------------------------------------
    private static final int SAND = b(24, 0), CHISELED = b(24, 1), SMOOTH = b(24, 2), RED = b(179, 0), RED_SMOOTH = b(179, 2);
    private static final int GLOW = b(89), SOUL = b(88), BRICK = b(112), MAGMA = b(213), QUARTZ = b(155, 0), QPILLAR = b(155, 2);
    private static final int GRANITE = b(1, 1), POLISHED = b(1, 2), BONE = b(216, 0), YELLOW = b(251, 4), ORANGE_T = b(159, 1);
    private static final int BLACK_T = b(159, 15), LAPIS_LOOK = b(251, 11), RACK = b(87), FIRE = b(51), WEB = b(30), SANDY = b(12, 0);
    private static final int SLAB = b(44, 1), SLAB_TOP = b(44, 9), CARPET = b(171, 14), IRON_BARS = b(101), TORCH = b(50, 5), CAULDRON = b(118);

    private static Draw.Mat body(int salt) {
        return (x, y, z) -> {
            double q = Draw.rnd(x, y, z, salt);
            return q < 0.07 ? RED : q < 0.1 ? b(24, 2) : SAND;
        };
    }

    // ---- plan ---------------------------------------------------------------------------------------------------------
    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        // the labyrinth: a randomised depth-first maze over the cells, then a tenth of the walls knocked through for loops
        boolean[][] seen = new boolean[MN][MN];
        int[] stack = new int[MN * MN];
        int sp = 0;
        stack[sp++] = (MN / 2) * MN + MN / 2;
        seen[MN / 2][MN / 2] = true;
        while (sp > 0) {
            int c = stack[sp - 1], cu = c / MN, cv = c % MN;
            int[] dirs = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t; }
            boolean moved = false;
            for (int dIdx : dirs) {
                int nu = cu + (dIdx == 0 ? 1 : dIdx == 1 ? -1 : 0), nv = cv + (dIdx == 2 ? 1 : dIdx == 3 ? -1 : 0);
                if (nu < 0 || nv < 0 || nu >= MN || nv >= MN || seen[nu][nv]) continue;
                if (dIdx == 0) p.east[cu][cv] = true; else if (dIdx == 1) p.east[nu][nv] = true;
                else if (dIdx == 2) p.south[cu][cv] = true; else p.south[nu][nv] = true;
                seen[nu][nv] = true;
                stack[sp++] = nu * MN + nv;
                moved = true;
                break;
            }
            if (!moved) sp--;
        }
        for (int u = 0; u < MN; u++) for (int v = 0; v < MN; v++) {
            if (u + 1 < MN && r.nextInt(10) == 0) p.east[u][v] = true;
            if (v + 1 < MN && r.nextInt(10) == 0) p.south[u][v] = true;
        }
        // lever order
        int[] o = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = o[i]; o[i] = o[j]; o[j] = t; }
        p.order = o;
        // burial rooms (3x3 cells) away from the heart and the corridors to it; traps on corridor cells
        for (int t = 0; t < 40 && p.rooms.size() < 14; t++) {
            int cu = 1 + r.nextInt(MN - 4), cv = 1 + r.nextInt(MN - 4);
            if (Math.abs(cu + 1 - MN / 2) < 7 && Math.abs(cv + 1 - MN / 2) < 7) continue;     // the heart
            if (Math.abs(cv + 1 - MN / 2) <= 2 || Math.abs(cu + 1 - MN / 2) <= 2) continue;    // the four axes stay corridors
            boolean clash = false;
            for (int[] q : p.rooms) if (Math.abs(q[0] - cu) < 5 && Math.abs(q[1] - cv) < 5) clash = true;
            if (!clash) p.rooms.add(new int[]{cu, cv});
        }
        for (int t = 0; t < 400 && p.traps.size() < 34; t++) {
            int cu = r.nextInt(MN), cv = r.nextInt(MN);
            if (Math.abs(cu - MN / 2) < 6 && Math.abs(cv - MN / 2) < 6) continue;
            if (inRoom(p, cu, cv)) continue;
            boolean clash = false;
            for (int[] q : p.traps) if (Math.abs(q[0] - cu) < 3 && Math.abs(q[1] - cv) < 3) clash = true;
            if (!clash) p.traps.add(new int[]{cu, cv, r.nextInt(5)});
        }
        return p;
    }

    private static boolean inRoom(Plan p, int cu, int cv) {
        for (int[] q : p.rooms) if (cu >= q[0] && cu <= q[0] + 2 && cv >= q[1] && cv <= q[1] + 2) return true;
        return false;
    }

    /** Half width of the pyramid at height h above its base. */
    static int half(int h) { return (int) Math.floor(H * (1 - h / (double) HEIGHT)); }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y;
        Draw.Mat body = body(s.salt);
        // a foundation under the whole base, then the courses (casing on the faces, bands, glowing arrises)
        f.box(-H - 3, y0 - 6, -H - 3, H + 3, y0, H + 3, Draw.of(BRICK));
        f.box(-H - 3, y0, -H - 3, H + 3, y0, H + 3, Draw.of(SMOOTH));
        for (int h = 1; h <= HEIGHT; h++) {
            int w = half(h), y = y0 + h;
            if (w < 0) break;
            boolean band = h % 9 == 0, cap = h > HEIGHT - 8;
            if (cap) { f.box(-w, y, -w, w, y, w, Draw.of(h == HEIGHT ? GLOW : YELLOW)); continue; }
            f.box(-w, y, -w, w, y, w, body);
            f.walls(-w, y, -w, w, y, w, Draw.of(band ? CHISELED : SMOOTH));
            if (h < 3) f.walls(-w, y, -w, w, y, w, Draw.of(BLACK_T));
            if ((h & 3) == 0) for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) f.set(cu * w, y, cv * w, GLOW);
        }
        f.set(0, y0 + HEIGHT + 1, 0, GLOW);
        labyrinth(f, s, p, y0);
        pit(f, s, y0);
        portal(f, s, y0, body);
        hall(f, s, y0);
        queen(f, s, y0);
        stars(f, s, p, y0);
        gallery(f, s, y0);
        king(f, s, y0);
        grounds(f, s, p, y0, body);
    }

    /** The north portal: a pylon gatehouse set into the face, a grand stair up the face, the threshold's flame vents. */
    private void portal(Draw.Frame f, Colossi.Site s, int y0, Draw.Mat body) {
        int yb = y0 + L2, face = -(half(L2));          // v of the face at the portal's floor
        int pv0 = face - 8, pv1 = face + 4;
        // the stair: nine wide, climbing from the cavern floor to the porch's door
        for (int k = 0; k < L2; k++) {
            int v = pv0 - L2 + k, y = y0 + 1 + k;
            f.box(-5, y0, v, 5, y - 1, v, body);
            for (int u = -4; u <= 4; u++) f.set(u, y, v, f.stair(128, 2, false));
            f.box(-4, y + 1, v, 4, y + 5, v, Draw.AIR);
            f.set(-5, y, v, SMOOTH); f.set(5, y, v, SMOOTH);
            if ((k & 3) == 0) { f.set(-5, y + 1, v, RACK); f.set(-5, y + 2, v, FIRE); f.set(5, y + 1, v, RACK); f.set(5, y + 2, v, FIRE); }
        }
        // the porch: a pylon of two towers half sunk into the face, the doorway between them
        for (int side = -1; side <= 1; side += 2) {
            f.box(side * 6, yb - 1, pv0, side * 13, yb + 16, pv1, Draw.of(SMOOTH));
            f.box(side * 7, yb + 17, pv0 + 1, side * 12, yb + 17, pv1 - 1, Draw.of(CHISELED));
            f.box(side * 9, yb + 18, pv0 + 3, side * 10, yb + 21, pv0 + 4, Draw.of(QPILLAR));
            f.set(side * 9, yb + 22, pv0 + 3, GLOW);
        }
        f.box(-6, yb - 1, pv0, 6, yb + 9, pv1, Draw.of(SMOOTH));
        f.box(-6, yb + 7, pv0, 6, yb + 9, pv0, Draw.of(CHISELED));
        f.box(-3, yb, pv0, 3, yb + 5, KA_V0, Draw.AIR);         // the doorway and the passage to the hall
        f.box(-3, yb - 1, pv0, 3, yb - 1, KA_V0, Draw.of(SMOOTH));
        // the Eye above the door
        f.box(-2, yb + 7, pv0 - 1, 2, yb + 8, pv0 - 1, Draw.of(BLACK_T));
        f.set(0, yb + 7, pv0 - 1, GLOW); f.set(0, yb + 8, pv0 - 1, GLOW);
        f.wallSign(0, yb + 3, pv0 - 1, Draw.NORTH, "THE GREAT", "PYRAMID", "Four jars open", "the King's way");
        // the flame vents of the threshold
        for (int u = -3; u <= 3; u++) f.set(u, yb - 1, pv0 + 5, MAGMA);
        f.point(0, yb, pv0 + 2, "garrison:tomb_guardian+mummy+mummy");
    }

    /** The Hall of the Ka: a pillared hub behind the portal, with the way down to the labyrinth. */
    private void hall(Draw.Frame f, Colossi.Site s, int y0) {
        int yb = y0 + L2, v0 = KA_V0, v1 = KA_V1;
        room(f, -11, v0, 11, v1, yb, yb + 10, Draw.of(POLISHED), Draw.of(SMOOTH), Draw.mix(CHISELED, GLOW, 0.1, s.salt + 3));
        for (int u = -7; u <= 7; u += 14) for (int v = v0 + 3; v <= v1 - 3; v += 4) { f.box(u, yb, v, u, yb + 9, v, Draw.of(QPILLAR)); f.set(u, yb + 10, v, CHISELED); }
        f.point(0, yb, v0 + 7, "garrison:mummy+mummy+asp");
        f.chest(-10, yb, v1 - 1, Draw.EAST, "jaspr:colossus/pyramid");
        f.chest(10, yb, v1 - 1, Draw.WEST, "jaspr:colossus/pyramid");
        // the stair down to the labyrinth: from the hall's floor, north to south, down to the ground floor
        int top = yb, bottom = y0 + 2;
        for (int k = 0; k < top - bottom + 1; k++) {
            int v = v0 + 2 + k, y = top - 1 - k;
            if (y < bottom - 1) break;
            f.box(-1, y + 1, v, 1, y + 6, v, Draw.AIR);
            for (int u = -1; u <= 1; u++) f.set(u, y, v, f.stair(128, 3, false));
            f.set(-2, y + 3, v, (k % 5) == 0 ? GLOW : SMOOTH); f.set(2, y + 3, v, (k % 5) == 0 ? GLOW : SMOOTH);
        }
        f.box(-1, yb, v0 + 2, 1, yb, v0 + 3, Draw.AIR);
        // the passage south to the gallery's foot, and the signs for the three ways
        f.box(-3, yb, v1, 3, yb + 4, GAL_V0, Draw.AIR);
        f.box(-3, yb - 1, v1, 3, yb - 1, GAL_V0, Draw.of(SMOOTH));
        f.wallSign(-10, yb + 2, SIDE_V - 3, Draw.EAST, "West:", "the Queen's", "Chamber", "");
        f.wallSign(10, yb + 2, SIDE_V - 3, Draw.WEST, "East:", "the Hall of", "Stars", "");
        f.wallSign(-4, yb + 2, v1 - 1, Draw.NORTH, "South:", "the Grand", "Gallery", "(four jars)");
    }

    /** The Queen's Chamber (west): Vizier Hekkat's hall under a gabled roof, a corridor from the hall. */
    private void queen(Draw.Frame f, Colossi.Site s, int y0) {
        int yb = y0 + L2, hv = WING_V;
        f.box(-30, yb, SIDE_V - 1, -11, yb + 4, SIDE_V + 1, Draw.AIR);
        f.box(-30, yb - 1, SIDE_V - 1, -11, yb - 1, SIDE_V + 1, Draw.of(SMOOTH));
        int u0 = -46, u1 = -30, v0 = hv - 10, v1 = hv + 10;
        room(f, u0, v0, u1, v1, yb, yb + 9, Draw.of(GRANITE), Draw.of(SMOOTH), Draw.of(SMOOTH));
        for (int k = 0; k < 5; k++) f.box(u0 + 1 + k, yb + 9 + k, v0 + 1, u1 - 1 - k, yb + 9 + k, v1 - 1, Draw.AIR);   // the gable
        for (int v = v0 + 2; v <= v1 - 2; v += 4) { f.set(u0 + 1, yb + 3, v, GLOW); f.set(u1 - 1, yb + 3, v, GLOW); }
        f.box(u0 + 6, yb, hv - 2, u0 + 9, yb, hv + 2, Draw.of(QUARTZ));           // the Vizier's dais
        f.point(u0 + 8, yb + 1, hv, "lord:vizier_hekkat");
        f.chest(u0 + 1, yb, v0 + 1, Draw.SOUTH, "jaspr:colossus/pyramid_rich");
        f.chest(u0 + 1, yb, v1 - 1, Draw.NORTH, "jaspr:colossus/pyramid");
        f.spawner(u1 - 2, yb, v0 + 2, "mummy");
        f.wallSign(u1 - 1, yb + 2, hv - 3, Draw.WEST, "The Vizier", "keeps the jar", "of Imsety", "");
    }

    /** The Hall of Stars (east): four lever pillars under a starry ceiling; the right order opens the Hapy niche. */
    private void stars(Draw.Frame f, Colossi.Site s, Plan p, int y0) {
        int yb = y0 + L2, hv = WING_V;
        f.box(11, yb, SIDE_V - 1, 30, yb + 4, SIDE_V + 1, Draw.AIR);
        f.box(11, yb - 1, SIDE_V - 1, 30, yb - 1, SIDE_V + 1, Draw.of(SMOOTH));
        int u0 = 30, u1 = 50, v0 = hv - 11, v1 = hv + 11;
        room(f, u0, v0, u1, v1, yb, yb + 12, Draw.of(BLACK_T), Draw.of(SMOOTH), (x, y, z) -> Draw.rnd(x, 5, z, s.salt + 77) < 0.08 ? GLOW : LAPIS_LOOK);
        int cu = (u0 + u1) / 2;
        int[][] posts = {{cu - 5, hv - 5}, {cu + 5, hv - 5}, {cu + 5, hv + 5}, {cu - 5, hv + 5}};
        for (int i = 0; i < 4; i++) {
            int[] q = posts[i];
            f.box(q[0], yb, q[1], q[0], yb + 1, q[1], Draw.of(QPILLAR));
            f.set(q[0], yb + 2, q[1], b(69, 5));                                     // a lever on its pillar
            int face = i == 0 || i == 3 ? Draw.WEST : Draw.EAST;
            f.wallSign(q[0] + (face == Draw.WEST ? -1 : 1), yb + 1, q[1], face, "", SIGNS[i], "", "");
        }
        // the riddle on the far wall; the order itself is carved in the labyrinth's four shrines
        f.wallSign(u1 - 1, yb + 3, hv, Draw.WEST, "Wake the stars", "in the order", "the shrines", "remember");
        f.wallSign(u1 - 1, yb + 2, hv, Draw.WEST, "Wrong, and", "the sky", "falls", "");
        // the niche of Hapy: sealed by quartz until the stars are woken
        f.box(cu - 1, yb, v1, cu + 1, yb + 3, v1 + 3, Draw.AIR);
        f.box(cu - 1, yb - 1, v1, cu + 1, yb - 1, v1 + 3, Draw.of(QUARTZ));
        f.box(cu - 1, yb, v1, cu + 1, yb + 2, v1, Draw.of(CHISELED));                // the seal
        f.set(cu, yb, v1 + 2, b(118));                                               // the jar's place
        f.chest(cu + 1, yb, v1 + 2, Draw.WEST, "jaspr:colossus/pyramid_rich");
    }

    /** The labyrinth: corridors on a four-block grid under the hall level, its rooms, shrines, traps and false doors. */
    private void labyrinth(Draw.Frame f, Colossi.Site s, Plan p, int y0) {
        int ya = y0 + 1, yt = y0 + 7;             // floor block, ceiling block
        Draw.Mat wall = body(s.salt + 11), floor = Draw.mix(SMOOTH, SANDY, 0.0, s.salt), ceil = Draw.mix(SAND, GLOW, 0.035, s.salt + 13);
        // clipped to the draw box: only cells overlapping it
        for (int cu = 0; cu < MN; cu++) for (int cv = 0; cv < MN; cv++) {
            int u = -MAZE + cu * PITCH, v = -MAZE + cv * PITCH;
            if (!touches(f, u - 1, v - 1, u + PITCH, v + PITCH)) continue;
            f.box(u, ya, v, u + PITCH - 1, yt, v + PITCH - 1, wall);
            f.box(u, yt, v, u + PITCH - 1, yt, v + PITCH - 1, ceil);
            f.box(u + 1, ya + 1, v + 1, u + PITCH - 1, yt - 1, v + PITCH - 1, Draw.AIR);    // the cell's corridor (3x3)
            f.box(u + 1, ya, v + 1, u + PITCH - 1, ya, v + PITCH - 1, floor);
            if (cu > 0 && p.east[cu - 1][cv]) f.box(u, ya + 1, v + 1, u, yt - 1, v + PITCH - 1, Draw.AIR);
            if (cv > 0 && p.south[cu][cv - 1]) f.box(u + 1, ya + 1, v, u + PITCH - 1, yt - 1, v, Draw.AIR);
            if ((cu * 7 + cv * 3) % 23 == 0) f.set(u + 2, yt, v + 2, GLOW);
        }
        // the outer wall of the labyrinth, and the four axes opened to the heart
        f.walls(-MAZE - 1, ya, -MAZE - 1, MAZE, yt, MAZE, Draw.of(SMOOTH));
        // the heart: the Hall of Pillars, with the shaft down to the Scarab Pit
        room(f, -16, -16, 16, 16, ya + 1, ya + 13, Draw.of(SMOOTH), Draw.of(CHISELED), Draw.mix(CHISELED, GLOW, 0.06, s.salt + 17));
        for (int u = -12; u <= 12; u += 6) for (int v = -12; v <= 12; v += 6) if (Math.abs(u) + Math.abs(v) > 6) f.box(u, ya + 1, v, u, ya + 12, v, Draw.of(QPILLAR));
        f.box(-1, ya + 1, -18, 1, ya + 4, -16, Draw.AIR); f.box(-1, ya + 1, 16, 1, ya + 4, 18, Draw.AIR);
        f.box(-18, ya + 1, -1, -16, ya + 4, 1, Draw.AIR); f.box(16, ya + 1, -1, 18, ya + 4, 1, Draw.AIR);
        // the shaft: a spiral down to the pit's floor, around a bone column
        int pitFloor = y0 - 8;
        f.box(-2, pitFloor + 1, -2, 2, ya + 1, 2, Draw.AIR);
        f.box(0, pitFloor + 1, 0, 0, ya + 1, 0, Draw.of(BONE));
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = ya; y > pitFloor; y--) {
            int[] q = ring[Math.floorMod(ya - y, 8)];
            f.set(q[0], y, q[1], SMOOTH);
        }
        f.chest(-15, ya + 1, -15, Draw.SOUTH, "jaspr:colossus/pyramid_rich");
        f.chest(15, ya + 1, 15, Draw.NORTH, "jaspr:colossus/pyramid_rich");
        f.point(-8, ya + 1, 8, "garrison:tomb_guardian+tomb_guardian+mummy");
        // the four shrines in the wings (each tells one star's place in the order) and the false doors
        int[][] shrines = {{0, -MAZE + 6}, {MAZE - 6, 0}, {0, MAZE - 6}, {-MAZE + 6, 0}};
        for (int i = 0; i < 4; i++) shrine(f, s, p, shrines[i][0], shrines[i][1], ya, i);
        for (int side = -1; side <= 1; side += 2) {
            int faceU = side * half(4);
            f.box(Math.min(side * MAZE, faceU), ya + 1, -1, Math.max(side * MAZE, faceU), ya + 4, 1, Draw.AIR);
            f.box(Math.min(side * MAZE, faceU), ya, -1, Math.max(side * MAZE, faceU), ya, 1, Draw.of(SMOOTH));
            f.box(faceU, ya + 5, -2, faceU, ya + 6, 2, Draw.of(CHISELED));
            f.point(side * (MAZE + 4), ya + 1, 0, "garrison:mummy+asp");
        }
        // burial rooms
        int n = 0;
        for (int[] q : p.rooms) burial(f, s, -MAZE + q[0] * PITCH, -MAZE + q[1] * PITCH, ya, yt, n++);
        // the traps' marks: pits under falling floors, slits, vents (the ordeals themselves are in ordeals())
        for (int[] t : p.traps) {
            int u = -MAZE + t[0] * PITCH, v = -MAZE + t[1] * PITCH;
            switch (t[2]) {
                case 1: f.box(u + 1, ya - 4, v + 1, u + PITCH - 1, ya - 1, v + PITCH - 1, Draw.AIR); f.box(u + 1, ya - 5, v + 1, u + PITCH - 1, ya - 5, v + PITCH - 1, Draw.of(MAGMA));
                    f.box(u + 1, ya, v + 1, u + PITCH - 1, ya, v + PITCH - 1, Draw.of(SLAB_TOP)); break;
                case 3: f.set(u + 2, ya, v + 2, MAGMA); break;
                case 4: f.set(u + 1, ya + 1, v + 1, WEB); f.set(u + 3, ya + 1, v + 3, WEB); break;
                default:
            }
        }
    }

    private static boolean touches(Draw.Frame f, int u0, int v0, int u1, int v1) {
        int xa = f.x(u0, v0), za = f.z(u0, v0), xb = f.x(u1, v1), zb = f.z(u1, v1);
        return f.d.touches(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    /** A wing shrine: an altar, two braziers and the carving that gives one star's place in the order. */
    private void shrine(Draw.Frame f, Colossi.Site s, Plan p, int cu, int cv, int ya, int i) {
        room(f, cu - 4, cv - 4, cu + 4, cv + 4, ya + 1, ya + 6, Draw.of(POLISHED), Draw.of(SMOOTH), Draw.of(CHISELED));
        for (int k = -1; k <= 1; k += 2) { f.box(cu + k * 4, ya + 1, cv - 1, cu + k * 4, ya + 3, cv + 1, Draw.AIR); f.box(cu - 1, ya + 1, cv + k * 4, cu + 1, ya + 3, cv + k * 4, Draw.AIR); }
        f.box(cu - 1, ya + 1, cv - 1, cu + 1, ya + 1, cv + 1, Draw.of(QUARTZ));
        f.set(cu - 2, ya + 1, cv - 2, RACK); f.set(cu - 2, ya + 2, cv - 2, FIRE);
        f.set(cu + 2, ya + 1, cv + 2, RACK); f.set(cu + 2, ya + 2, cv + 2, FIRE);
        int place = 0;
        for (int k = 0; k < 4; k++) if (p.order[k] == i) place = k;
        String[] nth = {"first", "second", "third", "last"};
        f.wallSign(cu, ya + 3, cv - 3, Draw.SOUTH, "Of the stars,", SIGNS[i], "wakes " + nth[place], "");
        f.chest(cu + 3, ya + 1, cv + 3, Draw.NORTH, "jaspr:colossus/pyramid");
    }

    /** A burial room of 3x3 cells: a sarcophagus, canopic niches, a chest (some trapped), a mummy that wakes. */
    private void burial(Draw.Frame f, Colossi.Site s, int u, int v, int ya, int yt, int idx) {
        int u1 = u + 3 * PITCH - 1, v1 = v + 3 * PITCH - 1;
        room(f, u, v, u1, v1, ya + 1, yt, Draw.of(SMOOTH), body(s.salt + 19), Draw.of(CHISELED));
        for (int k = 1; k <= 2; k++) { f.box(u + k * PITCH, ya + 1, v + 1, u + k * PITCH, ya + 1, v1 - 1, Draw.of(0)); }
        int cu = (u + u1) / 2, cv = (v + v1) / 2;
        f.box(cu - 1, ya + 1, cv - 2, cu + 1, ya + 1, cv + 2, Draw.of(QUARTZ));
        f.box(cu - 1, ya + 2, cv - 2, cu + 1, ya + 2, cv + 2, Draw.of(b(44, 15)));
        f.skull(cu, ya + 3, cv - 2, 0, 0);
        f.set(u + 1, ya + 1, v + 1, CAULDRON); f.set(u1 - 1, ya + 1, v + 1, CAULDRON);
        f.set(u + 2, yt - 1, v + 2, WEB); f.set(u1 - 2, yt - 1, v1 - 2, WEB);
        f.chest(cu + 2, ya + 1, cv, Draw.WEST, (idx % 3) == 0 ? "jaspr:colossus/pyramid_rich" : "jaspr:colossus/pyramid");
        if ((idx & 1) == 0) f.spawner(cu - 3, ya + 1, cv + 3, idx % 4 == 0 ? "asp" : "mummy");
        f.point(cu, ya + 1, cv + 3, "garrison:mummy+mummy");
    }

    /** The Scarab Pit, under the labyrinth's heart: a rough-hewn chamber, a trench of embers, the Matriarch's brood. */
    private void pit(Draw.Frame f, Colossi.Site s, int y0) {
        int fl = y0 - 8, top = y0 - 1;
        f.box(-20, fl - 1, -20, 20, top + 1, 20, Draw.of(BRICK));
        f.box(-18, fl + 1, -18, 18, top - 1, 18, (x, y, z) -> 0);
        f.box(-18, fl, -18, 18, fl, 18, Draw.mix(SOUL, SAND, 0.4, s.salt + 23));
        for (int u = -18; u <= 18; u++) for (int v = -18; v <= 18; v++) {
            double q = Draw.rnd(u, 0, v, s.salt + 29);
            if (Math.max(Math.abs(u), Math.abs(v)) > 15 && q < 0.35) f.box(u, fl + 1, v, u, fl + 1 + (int) (q * 10), v, Draw.of(SAND));
            if (Math.abs(Math.abs(u) - 9) <= 0 && Math.abs(v) < 12) f.set(u, fl, v, MAGMA);
        }
        for (int[] c : new int[][]{{-12, -12}, {12, -12}, {12, 12}, {-12, 12}}) { f.box(c[0], fl + 1, c[1], c[0], top - 1, c[1], Draw.of(BONE)); f.set(c[0], top - 1, c[1], GLOW); }
        f.point(0, fl + 1, 8, "lord:scarab_matriarch");
        f.chest(-17, fl + 1, 17, Draw.NORTH, "jaspr:colossus/pyramid_rich");
        f.spawner(14, fl + 1, -14, "scarab");
        f.spawner(-14, fl + 1, -14, "scarab");
    }

    /** The Grand Gallery: a broad stair climbing south to the King's antechamber under a corbelled vault. */
    private void gallery(Draw.Frame f, Colossi.Site s, int y0) {
        int vStart = GAL_V0, yStart = y0 + L2;
        int rise = KING - L2;
        for (int k = 0; k <= rise; k++) {
            int v = vStart + k, y = yStart + k;
            f.box(-4, y - 1, v, 4, y - 1, v, Draw.of(SMOOTH));
            for (int u = -3; u <= 3; u++) f.set(u, y, v, f.stair(128, 2, false));
            // the vault: corbelled walls stepping in every two blocks
            for (int lvl = 0; lvl < 8; lvl++) {
                int w = 3 - (lvl >= 4 ? (lvl - 3) / 2 : 0);
                f.box(-w, y + 1 + lvl, v, w, y + 1 + lvl, v, Draw.AIR);
                f.set(-w - 1, y + 1 + lvl, v, (lvl == 3 && (k % 4) == 0) ? GLOW : SMOOTH);
                f.set(w + 1, y + 1 + lvl, v, (lvl == 3 && (k % 4) == 2) ? GLOW : SMOOTH);
            }
            f.box(-2, y + 9, v, 2, y + 9, v, Draw.of(CHISELED));
            if (k % 3 == 0 && k > 1 && k < rise - 1) { f.set(-4, y + 1, v, BLACK_T); f.set(4, y + 1, v, BLACK_T); }   // the arrow slits
        }
        f.box(-3, yStart, vStart - 1, 3, yStart + 4, vStart, Draw.AIR);
        f.point(0, yStart + 6, vStart + 8, "garrison:tomb_guardian+mummy");
    }

    /** The antechamber with the Canopic Seal, the King's Chamber, the treasury, the relieving chambers, the Sun Chamber. */
    private void king(Draw.Frame f, Colossi.Site s, int y0) {
        int yb = y0 + KING, gv = GAL_V1;                              // the gallery's top
        // antechamber with the four niches
        room(f, -6, gv, 6, gv + 6, yb, yb + 7, Draw.of(GRANITE), Draw.of(POLISHED), Draw.of(CHISELED));
        f.box(-3, yb, gv, 3, yb + 4, gv, Draw.AIR);
        for (int i = 0; i < 4; i++) {
            int u = -5 + i * 3 + (i > 1 ? 1 : 0);
            f.set(u, yb + 1, gv + 1, b(118));
            f.wallSign(u, yb + 2, gv + 1, Draw.SOUTH, "", "Jar of", JARS[i].substring(12, 13).toUpperCase() + JARS[i].substring(13), "");
        }
        f.box(-2, yb, gv + 6, 2, yb + 4, gv + 6, Draw.of(GRANITE));      // the Canopic Seal
        f.box(-1, yb + 1, gv + 6, 1, yb + 3, gv + 6, Draw.of(b(1, 6)));
        f.wallSign(0, yb + 5, gv + 5, Draw.NORTH, "Show the four", "jars and the", "seal will part", "");
        // the King's Chamber
        int c0 = KING_V0, c1 = KING_V1;
        room(f, -12, c0, 12, c1, yb, yb + 12, Draw.of(GRANITE), Draw.of(POLISHED), Draw.of(GRANITE));
        f.box(-3, yb + 1, c0, 3, yb + 4, c0, Draw.AIR);
        f.box(-11, yb, c0 + 1, 11, yb, c1 - 1, (x, y, z) -> ((x + z) & 1) == 0 ? POLISHED : GRANITE);
        f.box(-2, yb + 1, c0 + 1, 2, yb + 1, c1 - 1, Draw.of(CARPET));
        for (int u = -9; u <= 9; u += 18) for (int v = c0 + 3; v <= c1 - 3; v += 5) { f.box(u, yb + 1, v, u, yb + 11, v, Draw.of(POLISHED)); f.set(u, yb + 6, v, GLOW); }
        int sv = (c0 + c1) / 2 + 3;
        f.box(-3, yb + 1, sv - 2, 3, yb + 1, sv + 2, Draw.of(QUARTZ));                // the sarcophagus
        f.box(-2, yb + 2, sv - 1, 2, yb + 2, sv + 1, Draw.of(YELLOW));
        f.box(-1, yb + 3, sv, 1, yb + 3, sv, Draw.of(b(44, 15)));
        f.point(0, yb + 1, sv - 5, "lord:sunless_pharaoh");
        f.point(-6, yb + 1, c0 + 4, "garrison:tomb_guardian+tomb_guardian");
        // the treasury behind the King (sharing his south wall): sealed until he falls
        int t0 = c1, t1 = TREAS_V1;
        room(f, -7, t0, 7, t1, yb, yb + 7, Draw.of(YELLOW), Draw.of(POLISHED), Draw.of(CHISELED));
        f.box(-1, yb + 1, c1, 1, yb + 3, c1, Draw.of(b(1, 6)));                      // its seal
        for (int u = -5; u <= 5; u += 5) f.chest(u, yb + 1, t1 - 1, Draw.NORTH, "jaspr:colossus/pyramid_vault");
        f.chest(-6, yb + 1, t0 + 1, Draw.EAST, "jaspr:colossus/pyramid_rich");
        f.chest(6, yb + 1, t0 + 1, Draw.WEST, "jaspr:colossus/pyramid_rich");
        // the relieving chambers stacked above, reached by a ladder on the chamber's east wall, and the Sun Chamber
        int rv = (c0 + c1) / 2;
        for (int k = 0; k < 3; k++) {
            int ry = yb + 13 + k * 5;                       // floor block at ry - 1 = the room below's ceiling
            room(f, -12, rv - 5, 12, rv + 5, ry, ry + 4, Draw.of(GRANITE), Draw.of(GRANITE), Draw.of(GRANITE));
            f.chest(-10 + k * 9, ry, rv + 4, Draw.NORTH, k == 2 ? "jaspr:colossus/pyramid_rich" : "jaspr:colossus/pyramid");
            f.set(-11, ry + 2, rv - 4, GLOW);
        }
        int top3 = yb + 13 + 2 * 5 + 4;                      // the third chamber's ceiling
        for (int y = yb; y < top3; y++) f.set(11, y, rv - 4, b(65, f.facing(Draw.WEST)));   // a ladder against the east wall (u 12)
        int sunY = top3 + 4;                                  // the Sun Chamber's floor block lies at sunY - 1
        for (int y = yb + 13 + 2 * 5; y < sunY; y++) f.set(0, y, rv + 4, b(65, f.facing(Draw.NORTH)));   // against the wall at rv + 5
        room(f, -4, rv - 3, 4, rv + 5, sunY, sunY + 5, Draw.of(YELLOW), Draw.of(SMOOTH), Draw.of(YELLOW));
        f.set(0, sunY - 1, rv + 4, b(65, f.facing(Draw.NORTH)));
        f.chest(0, sunY, rv - 2, Draw.SOUTH, "jaspr:colossus/pyramid_sun");
        f.set(-3, sunY + 3, rv + 1, GLOW); f.set(3, sunY + 3, rv + 1, GLOW);
    }

    /** The grounds: the avenue of sphinxes to the north stair, the Great Sphinx, obelisks, the queens' pyramids, a court. */
    private void grounds(Draw.Frame f, Colossi.Site s, Plan p, int y0, Draw.Mat body) {
        // the avenue
        int v0 = -H - 3, v1 = -s.kind.radius + 18;
        for (int v = v1; v <= v0; v++) {
            f.box(-5, y0 - 2, v, 5, y0, v, Draw.of(SMOOTH));
            f.box(-5, y0 + 1, v, 5, y0 + 6, v, Draw.AIR);
            f.set(-5, y0, v, CHISELED); f.set(5, y0, v, CHISELED);
        }
        for (int v = v1 + 4; v <= v0 - 6; v += 12) for (int side = -1; side <= 1; side += 2) smallSphinx(f, side * 9, v, y0, side);
        // the Great Sphinx beside the avenue, facing it, and its plaza (the Sentinel's arena)
        int gu = -44, gv = -H - 40;
        greatSphinx(f, s, gu, gv, y0);
        f.box(gu + 14, y0, gv - 12, -6, y0, gv + 12, Draw.of(SMOOTH));
        f.box(gu + 14, y0 + 1, gv - 12, -6, y0 + 8, gv + 12, Draw.AIR);
        f.point(gu + 22, y0 + 1, gv, "lord:sphinx_sentinel");
        f.wallSign(gu + 13, y0 + 3, gv, Draw.EAST, "The Sphinx", "keeps the jar", "of Duamutef", "");
        // corner obelisks
        for (int cu = -1; cu <= 1; cu += 2) for (int cv = -1; cv <= 1; cv += 2) {
            int u = cu * (H + 14), v = cv * (H + 14);
            f.box(u - 2, y0 - 3, v - 2, u + 2, y0 + 1, v + 2, Draw.of(SMOOTH));
            f.box(u - 1, y0 + 2, v - 1, u + 1, y0 + 30, v + 1, Draw.of(RED_SMOOTH));
            f.box(u, y0 + 31, v, u, y0 + 34, v, Draw.of(YELLOW));
            f.set(u, y0 + 35, v, GLOW);
        }
        // three queens' pyramids on the east side, each with a tomb
        for (int i = 0; i < 3; i++) queenPyramid(f, s, H + 34, -50 + i * 50, y0, i);
        // the mortuary court against the west face
        int mu = -H - 26;
        f.box(mu - 10, y0, -14, mu + 10, y0, 14, Draw.of(POLISHED));
        for (int u = mu - 8; u <= mu + 8; u += 4) for (int v = -12; v <= 12; v += 24) { f.box(u, y0 + 1, v, u, y0 + 9, v, Draw.of(QPILLAR)); f.set(u, y0 + 10, v, CHISELED); }
        f.chest(mu, y0 + 1, 0, Draw.EAST, "jaspr:colossus/pyramid");
        f.point(mu + 4, y0 + 1, 4, "garrison:mummy+mummy+tomb_guardian");
        f.point(20, y0 + 1, H + 20, "garrison:mummy+asp");
        f.point(-30, y0 + 1, H + 30, "garrison:scarab+scarab+mummy");
    }

    private void smallSphinx(Draw.Frame f, int u, int v, int y0, int side) {
        f.box(u - 1, y0 + 1, v - 3, u + 1, y0 + 1, v + 3, Draw.of(SMOOTH));
        f.box(u - 1, y0 + 2, v - 2, u + 1, y0 + 2, v + 3, Draw.of(SAND));
        f.box(u - 1, y0 + 2, v - 3, u + 1, y0 + 4, v - 3, Draw.of(SAND));
        f.set(u, y0 + 5, v - 3, CHISELED);
        f.set(u - side * 2, y0 + 1, v, RACK); f.set(u - side * 2, y0 + 2, v, FIRE);
    }

    /** The Great Sphinx: a lion body forty long, its head twenty high, facing east towards the avenue. */
    private void greatSphinx(Draw.Frame f, Colossi.Site s, int u, int v, int y0) {
        Draw.Mat stone = body(s.salt + 41);
        f.box(u - 20, y0 + 1, v - 7, u + 4, y0 + 8, v + 7, stone);                    // body
        f.box(u + 4, y0 + 1, v - 7, u + 14, y0 + 3, v - 3, stone);                    // paws
        f.box(u + 4, y0 + 1, v + 3, u + 14, y0 + 3, v + 7, stone);
        f.box(u - 2, y0 + 9, v - 5, u + 6, y0 + 20, v + 5, stone);                   // head and nemes
        f.box(u - 4, y0 + 9, v - 7, u + 2, y0 + 16, v - 6, Draw.of(RED_SMOOTH));
        f.box(u - 4, y0 + 9, v + 6, u + 2, y0 + 16, v + 7, Draw.of(RED_SMOOTH));
        f.box(u + 7, y0 + 12, v - 2, u + 7, y0 + 13, v - 2, Draw.of(GLOW));          // eyes
        f.box(u + 7, y0 + 12, v + 2, u + 7, y0 + 13, v + 2, Draw.of(GLOW));
        f.box(u - 24, y0 + 1, v - 1, u - 20, y0 + 3, v + 1, stone);                  // tail
        // a tomb in its chest, behind a door between the paws
        f.box(u - 8, y0 + 1, v - 3, u + 4, y0 + 5, v + 3, Draw.AIR);
        f.box(u + 4, y0 + 1, v - 1, u + 6, y0 + 3, v + 1, Draw.AIR);
        f.chest(u - 7, y0 + 1, v, Draw.EAST, "jaspr:colossus/pyramid_rich");
        f.set(u - 7, y0 + 4, v, GLOW);
    }

    private void queenPyramid(Draw.Frame f, Colossi.Site s, int u, int v, int y0, int i) {
        int h = 16;
        for (int k = 0; k <= h; k++) {
            int w = 17 - k;
            f.box(u - w, y0 + k, v - w, u + w, y0 + k, v + w, Draw.of(k == h ? YELLOW : k % 4 == 3 ? CHISELED : SMOOTH));
        }
        f.box(u - 4, y0 + 1, v - 4, u + 4, y0 + 5, v + 4, Draw.AIR);
        f.box(u - 17, y0 + 1, v - 1, u - 4, y0 + 3, v + 1, Draw.AIR);
        f.chest(u + 3, y0 + 1, v, Draw.WEST, i == 1 ? "jaspr:colossus/pyramid_rich" : "jaspr:colossus/pyramid");
        f.set(u, y0 + 5, v, GLOW);
        f.spawner(u - 2, y0 + 1, v + 3, "mummy");
        f.point(u - 10, y0 + 1, v, "garrison:mummy+asp");
    }

    /** A room: floor, walls and roof materials, the inside cleared (floor at yb - 1, ceiling at yt). */
    private static void room(Draw.Frame f, int u0, int v0, int u1, int v1, int yb, int yt, Draw.Mat floor, Draw.Mat wall, Draw.Mat ceil) {
        f.box(u0, yb - 1, v0, u1, yt, v1, wall);
        f.box(u0, yb - 1, v0, u1, yb - 1, v1, floor);
        f.box(u0, yt, v0, u1, yt, v1, ceil);
        f.box(u0 + 1, yb, v0 + 1, u1 - 1, yt - 1, v1 - 1, Draw.AIR);
        f.box(u0 + 1, yb - 1, v0 + 1, u1 - 1, yb - 1, v1 - 1, floor);
    }

    // ---- the ordeals: traps, the levers, the seals (in world coordinates; see Ordeals) -------------------------------------
    List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        int y0 = s.y, ya = y0 + 1;
        // the labyrinth's traps
        for (int[] t : p.traps) {
            int u = -MAZE + t[0] * PITCH, v = -MAZE + t[1] * PITCH;
            int[] box = fr.box(u + 1, ya + 1, v + 1, u + PITCH - 1, ya + 3, v + PITCH - 1);
            switch (t[2]) {
                case 0: out.add(Ordeals.arrows(box, fr.box(u, ya + 2, v, u + PITCH, ya + 2, v + PITCH))); break;
                case 1: out.add(Ordeals.collapse(fr.box(u + 1, ya, v + 1, u + PITCH - 1, ya, v + PITCH - 1), SLAB_TOP)); break;
                case 2: out.add(Ordeals.rubble(box, SANDY)); break;
                case 3: out.add(Ordeals.flames(box, 80)); break;
                default: out.add(Ordeals.gas(box));
            }
        }
        // the threshold's flames, the gallery's arrows
        int pv0 = -half(L2) - 8, yb = y0 + L2;
        out.add(Ordeals.flames(fr.box(-3, yb, pv0 + 4, 3, yb + 2, pv0 + 6), 60));
        int vStart = GAL_V0;
        out.add(Ordeals.arrows(fr.box(-3, yb + 2, vStart + 3, 3, yb + 8, vStart + 12), fr.box(-4, yb + 3, vStart + 2, 4, yb + 13, vStart + 13)));
        // the Hall of Stars: four levers, in the plan's order, open the Hapy niche (and give the jar)
        int hv = WING_V, cu = 40;
        int[][] posts = {{cu - 5, hv - 5}, {cu + 5, hv - 5}, {cu + 5, hv + 5}, {cu - 5, hv + 5}};
        int[][] levers = new int[4][];
        for (int i = 0; i < 4; i++) levers[i] = fr.at(posts[i][0], yb + 2, posts[i][1]);
        int[] niche = fr.box(cu - 1, yb, hv + 11, cu + 1, yb + 2, hv + 11);
        out.add(Ordeals.levers("pyramid_stars", fr.box(30, yb, hv - 11, 50, yb + 11, hv + 11), levers, p.order, niche, CHISELED, "canopic_jar_hapy",
            fr.box(31, yb, hv - 10, 49, yb + 10, hv + 10)));
        // the Canopic Seal (four jars) and the treasury's seal (the Pharaoh's fall)
        int kb = y0 + KING;
        out.add(Ordeals.keySeal("pyramid_canopic", fr.box(-1, kb + 1, ANTE_V1, 1, kb + 3, ANTE_V1), b(1, 6), JARS));
        out.add(Ordeals.bossSeal("pyramid_treasury", fr.box(-1, kb + 1, KING_V1, 1, kb + 3, KING_V1), b(1, 6), "sunless_pharaoh"));
        p.ordeals = out;
        return out;
    }

    // ---- the cavern -----------------------------------------------------------------------------------------------------
    @Override int ground(Colossi.Site s, int x, int y, int z) {
        double f = s.f(x, z);
        if (f > 0.86) return b(159, 12);
        return Draw.rnd(x, 0, z, s.salt + 61) < 0.3 ? SOUL : b(12, 0);
    }

    @Override Draw.Mat under() { return Draw.mix(b(24, 0), SOUL, 0.25, 4473); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < 140; }

    @Override double lakeLevel() { return 0.4; }

    @Override void dress(Colossi.Site s, Draw d, int x, int z, int floor, int ceil, double f, boolean lake) {
        MegaDesign.glowHang(d, x, z, ceil, s.salt + 51, 0.005);
        if (lake || s.dist(x, z) < 132) return;
        double q = Draw.rnd(x, 9, z, s.salt + 52);
        if (q < 0.008) { d.set(x, floor + 1, z, BONE); if (q < 0.004) d.set(x, floor + 2, z, BONE); }
        else if (q < 0.0095) { d.set(x, floor, z, RACK); d.set(x, floor + 1, z, FIRE); }
    }
}
