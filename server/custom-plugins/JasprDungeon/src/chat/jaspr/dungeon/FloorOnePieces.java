package chat.jaspr.dungeon;

/**
 * Generation 7: the signature set pieces of Floor I's eighteen new themes. Each theme has two (variant 0 and 1); a room draws
 * one in some of its 7x7 bays, in one of eight orientations (axes swapped, x flipped, z flipped), and the rest of its bays keep the
 * room's motif. Pure: a piece is a function of its bay cell alone.
 *
 * The contract is the motifs' (DungeonGenerator.decoration): a piece stays inside its bay (dx, dz in -3..3), never touches a lane,
 * the chest or a door (a bay touches none), and every block of it rests on the floor, hangs from the roof (a chain of iron bars
 * up to the layer under the roof) or is joined to a block that does. Water has a block under it and a block or water on each of
 * its four sides; a torch stands upright on a whole block; stairs rest on whole blocks and face with the piece. A piece may paint
 * the floor under itself (an accent: soot, wax, wet stone), but never a lamp of the lattice or a mark of the room's dangers (the
 * caller checks).
 *
 * Cells are written in the piece's own frame: cx, cz are the bay offsets after the orientation is undone, h is the layer above
 * the floor (0 is y 65), top the highest layer under the roof (7 in the smallest rooms).
 */
final class FloorOnePieces {
    private FloorOnePieces() {}
    private static final int BARS = 101, GLOW = 89, SEA = 169, WEB = 30, WATER = 9, TORCH = 50 | (5 << 12);
    private static int d(int id, int data) { return id | (data << 12); }

    // ---------------------------------------------------------------- orientation
    /** Canonical x of the actual offset (dx, dz) under the orientation bits (swap, flip x, flip z). */
    static int cx(int tr, int dx, int dz) { int ux = (tr & 2) != 0 ? -dx : dx, uz = (tr & 4) != 0 ? -dz : dz; return (tr & 1) != 0 ? uz : ux; }
    static int cz(int tr, int dx, int dz) { int ux = (tr & 2) != 0 ? -dx : dx, uz = (tr & 4) != 0 ? -dz : dz; return (tr & 1) != 0 ? ux : uz; }
    /** A direction of the piece's own frame (0 +x, 1 -x, 2 +z, 3 -z) as the world's direction under the orientation. */
    static int dir(int tr, int d) {
        int vx = d == 0 ? 1 : d == 1 ? -1 : 0, vz = d == 2 ? 1 : d == 3 ? -1 : 0;
        int s0 = (tr & 1) != 0 ? vz : vx, s1 = (tr & 1) != 0 ? vx : vz;
        int ax = (tr & 2) != 0 ? -s0 : s0, az = (tr & 4) != 0 ? -s1 : s1;
        return ax == 1 ? 0 : ax == -1 ? 1 : az == 1 ? 2 : 3;
    }
    /** Log axis bits for a log lying along the piece's x axis (or its z axis): 4 = the world's x, 8 = the world's z. */
    private static int axis(int tr, boolean alongX) { return ((tr & 1) != 0) == alongX ? 8 : 4; }

    // ---------------------------------------------------------------- materials (1.12 legacy ids)
    private static final int[] WOOD_STAIRS = {53, 134, 135, 136, 163, 164}, FENCE = {85, 188, 189, 190, 192, 191};
    /** A log of the planks' wood (0 oak .. 5 dark oak) with axis bits (0 upright, 4 along x, 8 along z). */
    private static int log(int w, int axis) { return w < 4 ? 17 | ((w | axis) << 12) : 162 | (((w - 4) | axis) << 12); }
    /** A stair whose high side (its back) faces a world direction (0 +x, 1 -x, 2 +z, 3 -z). */
    private static int stair(int id, int back) { return id | (back << 12); }
    private static int woodStair(int w, int back) { return stair(WOOD_STAIRS[w], back); }
    /** Carved pumpkins and lanterns: the face looks toward a world direction. Furnaces: the mouth opens toward one. */
    private static int pumpkin(int id, int face) { return id | (new int[]{3, 1, 0, 2}[face] << 12); }
    private static int furnace(int id, int face) { return id | (new int[]{5, 4, 3, 2}[face] << 12); }
    /** Persistent (non-decaying) leaves: oak, spruce, birch, jungle. */
    private static int leaf(int n) { return 18 | ((4 + Math.floorMod(n, 4)) << 12); }

    /**
     * The block of a theme's piece at a bay cell (0 for air). theme is 36..53, variant 0 or 1, tr the orientation of the bay,
     * (dx, dz) the offset from the bay centre in the world's axes, h the layer above the floor, top the highest layer, p the palette.
     */
    static int block(int theme, int variant, int tr, int dx, int dz, int h, int top, int[] p) {
        int cx = cx(tr, dx, dz), cz = cz(tr, dx, dz), a = Math.abs(cx), b = Math.abs(cz), w = p[3] >>> 12;
        boolean one = variant == 0;
        // Each bay has one lamp of the floor's lattice in a corner: the corners stay open above it so its light still reaches the lanes.
        if (a == 3 && b == 3 && h <= 2) return 0;
        switch (theme - FloorOneExtra.BASE) {
            case 0: return bellfounder(one, tr, cx, cz, a, b, h, p, w);
            case 1: return moth(one, tr, cx, cz, a, b, h, p, w);
            case 2: return candlewright(one, cx, cz, a, b, h, top, p);
            case 3: return library(one, cx, cz, a, b, h, top, p);
            case 4: return bathhouse(one, cx, cz, a, b, h, p);
            case 5: return effigies(one, cx, cz, a, b, h, p);
            case 6: return market(one, tr, cx, cz, a, b, h, p, w);
            case 7: return orchard(one, tr, cx, cz, a, b, h, p);
            case 8: return reliquary(one, cx, cz, a, b, h, p);
            case 9: return lamplighter(one, tr, cx, cz, a, b, h, p, w);
            case 10: return kitchens(one, tr, cx, cz, a, b, h, top, p);
            case 11: return theatre(one, tr, cx, cz, a, b, h, p, w);
            case 12: return abbey(one, cx, cz, a, b, h, top, p);
            case 13: return gallery(one, tr, cx, cz, a, b, h, top, p, w);
            case 14: return gardens(one, cx, cz, a, b, h, top, p);
            case 15: return hostel(one, tr, cx, cz, a, b, h, p, w);
            case 16: return workshop(one, tr, cx, cz, a, b, h, p, w);
            case 17: return nave(one, tr, cx, cz, a, b, h, top, p, w);
            default: return 0;
        }
    }

    /** An accent for the floor of the bay (y 64) or 0. */
    static int floor(int theme, int variant, int tr, int dx, int dz, int[] p) {
        int cx = cx(tr, dx, dz), cz = cz(tr, dx, dz), a = Math.abs(cx), b = Math.abs(cz), m = Math.max(a, b);
        boolean one = variant == 0;
        switch (theme - FloorOneExtra.BASE) {
            case 0: return one && m <= 1 ? d(159, 15) : 0;                                                  // soot under the bell
            case 2: return one && b <= 1 && a <= 2 && Math.floorMod(cx * 3 + cz * 5, 4) == 0 ? p[2] : 0;      // pools of wax
            case 4: return !one && cz == -2 && a <= 2 && (a & 1) == 0 ? d(159, 9) : 0;                       // wet stone under the basins
            case 6: return one && cz >= 0 && a <= 3 && Math.floorMod(cx + cz * 3, 3) == 0 ? d(3, 1) : 0;      // trampled mud before the stall
            case 7: return m >= 3 && Math.floorMod(cx * 5 + cz * 3, 4) == 0 ? d(3, 1) : 0;                   // leaf litter turned to soil
            case 10: return !one && m == 2 ? d(159, 7) : 0;                                                  // ash around the island
            case 12: return one && cz == 0 && a <= 3 ? d(159, 9) : 0;                                        // the gutter's wet bed
            case 16: return !one && a <= 3 && b <= 2 && Math.floorMod(cx * 7 + cz * 3, 5) == 0 ? d(3, 1) : 0; // spilt earth around the grave
            default: return 0;
        }
    }

    // ---------------------------------------------------------------- 36 Bellfounder's Crypt
    /** A: a timber frame with a cast bronze bell hanging from its beam. B: a rack of three bells, one great and two small. */
    private static int bellfounder(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        int bronze = p[2];
        if (one) {
            if (a == 3 && cz == 0 && h <= 4) return log(w, 0);
            if (cz == 0 && a <= 3 && h == 5) return log(w, axis(tr, true));
            if (cx == 0 && cz == 0) { if (h == 4 || h == 1) return BARS; if (h == 2 || h == 3) return bronze; }
            if (a <= 1 && b <= 1 && !(a == 0 && b == 0)) { if (h == 1) return bronze; if (h == 2 && a + b == 1) return bronze; }
            return 0;
        }
        if (a == 3 && cz == 0 && h <= 4) return p[0];
        if (cz == 0 && a <= 3 && h == 5) return log(w, axis(tr, true));
        if (cz == 0 && a == 2) { if (h == 4 || h == 2) return BARS; if (h == 3) return bronze; }
        if (cz == 0 && a == 0) { if (h == 4 || h == 1) return BARS; if (h == 2 || h == 3) return bronze; }
        return 0;
    }

    // ---------------------------------------------------------------- 37 Moth Sanctum
    /** A: a moth hung in a frame, its wings of wool. B: four cocoon pillars joined by a ring of silk. */
    private static int moth(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        if (one) {
            if (a == 3 && cz == 0 && h <= 4) return log(w, 0);
            if (cz == 0 && a <= 3 && h == 5) return log(w, axis(tr, true));
            if (cz == 0) {
                if (a == 0 && h >= 1 && h <= 4) return d(35, 12);
                if (a == 1) { if (h == 2 || h == 4) return d(35, 8); if (h == 1 || h == 3) return d(35, 0); }
                if (a == 2) { if (h == 2 || h == 4) return d(35, 8); if (h == 3) return d(35, 15); }
            }
            return 0;
        }
        if (a == 2 && b == 2) { if (h == 0) return p[2]; if (h >= 1 && h <= 3) return d(35, h == 2 ? 8 : 0); if (h == 4) return WEB; }
        if (h == 4 && (a == 2 && b < 2 || b == 2 && a < 2)) return WEB;
        return 0;
    }

    // ---------------------------------------------------------------- 38 Candlewright Hall
    /** A: two stepped votive stands of timber, a candle on every second step. B: a wooden candle wheel hung from the roof by a chain. */
    private static int candlewright(boolean one, int cx, int cz, int a, int b, int h, int top, int[] p) {
        if (one) {
            if (a <= 2 && b == 3 && h <= 2) return p[3];
            if (a <= 2 && b == 2 && h <= 1) return p[3];
            if (a <= 2 && b == 3 && h == 3 && (a & 1) == 0) return TORCH;
            if (a <= 2 && b == 2 && h == 2 && (a & 1) == 1) return TORCH;
            return 0;
        }
        int m = Math.max(a, b);
        if (h == 4 && (m == 2 || m <= 2 && (cx == 0 || cz == 0))) return p[3];
        if (h == 5 && m == 2 && (a == 0 || b == 0 || a == b)) return TORCH;
        if (cx == 0 && cz == 0 && h >= 5 && h <= top) return BARS;
        return 0;
    }

    // ---------------------------------------------------------------- 39 Chained Library
    /** A: two bookcases chained to the roof, a reading candle between the chains. B: a chained lectern, its tome on a plinth. */
    private static int library(boolean one, int cx, int cz, int a, int b, int h, int top, int[] p) {
        if (one) {
            if (a == 2 && b <= 2 && h <= 2) return 47;
            if (a == 2 && b == 1 && h >= 3 && h <= top) return BARS;
            if (a == 2 && cz == 0 && h == 3) return TORCH;
            return 0;
        }
        int m = Math.max(a, b);
        if (m <= 1 && h == 0) return p[0];
        if (h == 1 && a + b <= 1) return 47;
        if (cx == 0 && cz == 0 && h == 2) return d(44, 5);
        if (a == 2 && b == 2 && h <= top) return BARS;
        return 0;
    }

    // ---------------------------------------------------------------- 40 Penitent Bathhouse
    /** A: a tiled bath of still water with a slab step to wade in by. B: three washing basins in a stall, a lantern over each. */
    private static int bathhouse(boolean one, int cx, int cz, int a, int b, int h, int[] p) {
        int m = Math.max(a, b);
        if (one) {
            if (m == 2 && h == 0) return cz == 2 && cx == 0 ? d(44, 7) : p[2];
            if (m <= 1 && h == 0) return WATER;
            if (a == 2 && b == 2 && h == 1) return TORCH;
            return 0;
        }
        if (cz == -3 && a <= 2 && h <= 2) return p[2];
        if (cz == -2 && (a == 2 || a == 0)) { if (h == 0) return d(118, 3); if (h == 2) return SEA; }
        if (cz == -2 && a == 1 && h <= 1) return d(160, 3);
        return 0;
    }

    // ---------------------------------------------------------------- 41 Hall of Effigies
    /** A: a weeping effigy in robes, hands to its face, on a stepped plinth. B: two kneeling effigies facing a candle. */
    private static int effigies(boolean one, int cx, int cz, int a, int b, int h, int[] p) {
        if (one) {
            int m = Math.max(a, b);
            if (m <= 2 && h == 0) return p[0];
            if (m <= 1 && h == 1) return p[2];
            if (m <= 1 && h == 2) return 216;
            if (cx == 0 && cz == 0 && (h == 3 || h == 4)) return 216;
            if (cz == 0 && a == 1 && (h == 4 || h == 5)) return 216;
            if (cx == 0 && cz == 0 && h == 5) return d(155, 1);
            return 0;
        }
        if (b == 2 && a <= 1 && h == 0) return p[0];
        if (b == 2 && cx == 0) { if (h == 1 || h == 2) return 216; if (h == 3) return d(155, 1); }
        if (b == 2 && a == 1 && h == 1) return d(44, 7);
        if (cx == 0 && cz == 0) { if (h == 0) return p[0]; if (h == 1) return TORCH; }
        return 0;
    }

    // ---------------------------------------------------------------- 42 Grave Market
    /** A: a stall under a striped awning, bones and gourds on its counter. B: a rope of hanging wares, lantern gourds and bones. */
    private static int market(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        if (one) {
            if (a == 3 && b == 2 && h <= 3) return log(w, 0);
            if (h == 4 && a <= 3 && b <= 2) return ((cx + 3) & 1) == 0 ? d(35, 14) : d(35, 0);
            if (h == 3 && b == 2 && a <= 2) return ((cx + 3) & 1) == 0 ? d(35, 0) : d(35, 14);
            if (cz == 1 && a <= 2 && h == 0) return p[3];
            if (cz == 1 && a <= 2 && h == 1) return a == 2 ? 216 : cx == -1 ? pumpkin(86, dir(tr, 2)) : cx == 0 ? d(118, 0) : d(103, 0);
            if (cz == -2 && a <= 2 && h <= 1) return 47;
            return 0;
        }
        if (a == 3 && cz == 0 && h <= 4) return log(w, 0);
        if (cz == 0 && a <= 2 && h == 4) return BARS;
        if (cz == 0 && a <= 2 && h == 3) return (a & 1) == 0 ? pumpkin(91, dir(tr, 2)) : 216;
        return 0;
    }

    // ---------------------------------------------------------------- 43 Weeping Orchard
    /** A: a dead tree in a stone planter, a few last leaves drooping from its twigs. B: a scarecrow among dead bushes in a bed. */
    private static int orchard(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p) {
        int m = Math.max(a, b);
        if (one) {
            if (m == 2 && h == 0) return d(98, 1);
            if (m <= 1 && h == 0) return d(3, 1);
            if (cx == 0 && cz == 0 && h >= 1 && h <= 5) return log(5, 0);
            int run = cz == 0 ? axis(tr, true) : axis(tr, false);
            if ((h == 4 || h == 5) && a + b == 1) return log(5, run);
            if (h == 5 && (a == 2 && b == 0 || a == 0 && b == 2)) return log(5, run);
            if (h == 4 && (a == 2 && b == 0 || a == 0 && b == 2)) return leaf(cx + cz);
            return 0;
        }
        if (a <= 2 && b <= 1 && h == 0) return a == 2 || b == 1 ? d(98, 1) : d(3, 1);
        if (b == 0 && a == 1 && h == 1) return d(32, 0);
        if (cx == 0 && cz == 0) {
            if (h == 1 || h == 3) return 85;
            if (h == 2) return d(170, 0);
            if (h == 4) return pumpkin(86, dir(tr, 2));
        }
        if (a == 1 && cz == 0 && h == 3) return 85;
        return 0;
    }

    // ---------------------------------------------------------------- 44 Rusted Reliquary
    /** A: two barred cages, a relic lit by a candle in each. B: a rusted portcullis hung in a stone gate. */
    private static int reliquary(boolean one, int cx, int cz, int a, int b, int h, int[] p) {
        if (one) {
            if (a >= 1 && b <= 1) {
                if (h == 0 || h == 4) return 4;
                if (h >= 1 && h <= 3) return a == 2 && cz == 0 ? (h == 1 ? p[2] : h == 2 ? TORCH : 0) : BARS;
            }
            return 0;
        }
        if (a == 3 && b <= 1 && h <= 4) return 4;
        if (a <= 3 && b <= 1 && h == 4) return 4;
        if (a <= 2 && cz == 0 && (h == 2 || h == 3)) return BARS;
        if (a == 3 && cz == 0 && h == 5) return TORCH;
        return 0;
    }

    // ---------------------------------------------------------------- 45 Lamplighter's Rest
    /** A: two lamp posts and a bench. B: a tall lantern post ringed by four benches. */
    private static int lamplighter(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        if (one) {
            if (a == 2 && cz == -2) { if (h <= 4) return d(139, 0); if (h == 5) return GLOW; if (h == 6) return d(44, 3); }
            if (cz == 2 && a <= 1 && h == 0) return woodStair(w, dir(tr, 2));
            if (a == 2 && cz == 2 && h == 0) return d(118, 0);
            return 0;
        }
        int m = Math.max(a, b);
        if (m <= 1 && h == 0) return p[0];
        if (cx == 0 && cz == 0) { if (h >= 1 && h <= 5) return d(139, 0); if (h == 6) return GLOW; if (h == 7) return d(44, 3); }
        if (h == 6 && a + b == 1) return d(95, 4);
        if (h == 0 && (a == 2 && b == 0 || a == 0 && b == 2)) return woodStair(w, dir(tr, a == 2 ? (cx > 0 ? 0 : 1) : (cz > 0 ? 2 : 3)));
        return 0;
    }

    // ---------------------------------------------------------------- 46 Ashen Kitchens
    /** A: a great hearth with three lit ovens under a brick chimney, a worktable before it. B: an oven island with a flue to the roof. */
    private static int kitchens(boolean one, int tr, int cx, int cz, int a, int b, int h, int top, int[] p) {
        if (one) {
            if (cz == -3 && a <= 2 && h <= 3) return 45;
            if (cz == -3 && a <= 1 && (h == 4 || h == 5)) return 45;
            if (cz == -3 && a == 0 && h >= 6 && h <= top) return 45;
            if (cz == -2 && (a == 0 || a == 2)) { if (h == 0) return furnace(62, dir(tr, 2)); if (h == 1 || h == 2) return 45; }
            if (cz == 1 && h == 0) { if (a == 1) return 58; if (a == 0) return d(118, 0); }
            return 0;
        }
        int m = Math.max(a, b);
        if (m <= 1 && h == 0) return a == b ? 45 : furnace(62, dir(tr, a == 1 ? (cx > 0 ? 0 : 1) : (cz > 0 ? 2 : 3)));
        if (a == 1 && b == 1 && h == 1) return d(118, 0);
        if (cx == 0 && cz == 0 && h >= 1 && h <= top) return BARS;
        return 0;
    }

    // ---------------------------------------------------------------- 47 Mute Theatre
    /** A: a stage under a velvet proscenium, curtains drawn, a black backdrop, steps up from the floor. B: rows of audience benches by a lantern. */
    private static int theatre(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        if (one) {
            if (cz <= -1 && h == 0 && !(a == 3 && cz == -3)) return p[3];
            if (a == 3 && cz == -1 && h >= 1 && h <= 5) return log(w, 0);
            if (cz == -1 && a <= 3 && h == 5) return log(w, axis(tr, true));
            if (a == 2 && cz == -1 && h >= 1 && h <= 4) return d(35, 14);
            if (a <= 1 && cz == -1 && h == 4) return d(35, 14);
            if (cz == -3 && a <= 2 && h >= 1 && h <= 4) return d(35, 15);
            if (cz == 0 && a <= 1 && h == 0) return woodStair(w, dir(tr, 3));
            return 0;
        }
        if ((cz == 0 || cz == 2) && a <= 3 && h == 0) return woodStair(w, dir(tr, 2));
        if (a == 3 && cz == -2) { if (h == 0) return p[0]; if (h == 1) return TORCH; }
        return 0;
    }

    // ---------------------------------------------------------------- 48 Gutter Abbey
    /** A: a gutter of still water with a downspout of mossy wall from the roof. B: three gargoyle spouts over three troughs. */
    private static int abbey(boolean one, int cx, int cz, int a, int b, int h, int top, int[] p) {
        if (one) {
            if (cz == 0 && a <= 2 && h == 0) return WATER;
            if (b == 1 && a <= 2 && h == 0) return d(98, 1);
            if (a == 3 && cz == 0 && h == 0) return BARS;
            if (cx == -3 && cz == 0 && h >= 1 && h <= top) return d(139, 1);
            return 0;
        }
        boolean trough = cx == -2 || cx == 0 || cx == 2;
        if (cz == -3 && a <= 2 && h <= 3) return h == 3 && trough ? d(98, 3) : p[0];
        if (cz == -2 && trough) { if (h == 0) return WATER; if (h == 3) return BARS; }
        if (cz == -2 && !trough && h == 0) return d(98, 1);
        if (cz == -1 && trough && h == 0) return d(98, 1);
        return 0;
    }

    // ---------------------------------------------------------------- 49 Thurible Gallery
    /** A: three censers swung on chains from the roof, smoke caught beneath each. B: a gantry carrying one great censer. */
    private static int gallery(boolean one, int tr, int cx, int cz, int a, int b, int h, int top, int[] p, int w) {
        if (one) {
            if ((a == 2 || a == 0) && cz == 0) { if (h >= 5 && h <= top) return BARS; if (h == 4) return d(118, 0); if (h == 3) return WEB; }
            return 0;
        }
        if (a == 3 && cz == 0 && h <= 5) return p[2];
        if (a <= 3 && cz == 0 && h == 6) return log(w, axis(tr, true));
        if (a == 3 && cz == 0 && h == 7) return TORCH;
        if (cx == 0 && cz == 0) { if (h == 4 || h == 5) return BARS; if (h == 3) return d(118, 0); if (h == 2) return WEB; }
        return 0;
    }

    // ---------------------------------------------------------------- 50 Hanging Gardens of Mercy
    /** A: a planter of earth and leaves hung from the roof by four chains. B: a terraced mound of earth and leaves in mossy stone. */
    private static int gardens(boolean one, int cx, int cz, int a, int b, int h, int top, int[] p) {
        int m = Math.max(a, b);
        if (one) {
            if (m <= 2 && h == 3) return d(3, 1);
            if (m <= 2 && h == 4) return leaf(cx * 2 + cz);
            if (a == 2 && b == 2 && h >= 5 && h <= top) return BARS;
            if (m <= 1 && h == 2 && Math.floorMod(cx * 3 + cz * 5, 4) == 0) return leaf(cx + cz);
            return 0;
        }
        if (h == 0) { if (m == 2) return d(98, 1); if (m <= 1) return d(3, 1); }
        if (h == 1) { if (m == 1) return leaf(cx + cz * 2); if (m == 0) return d(3, 1); if (a == 2 && b == 2) return TORCH; }
        if ((h == 2 || h == 3) && m == 0) return leaf(h);
        return 0;
    }

    // ---------------------------------------------------------------- 51 Pilgrim's Hostel
    /** A: two bunks of timber, wool mattresses on both levels. B: a long table with a bench each side, bowls and candles. */
    private static int hostel(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        if (one) {
            boolean bunk = b == 2 || b == 3;
            if (a == 2 && bunk && h <= 4) return log(w, 0);
            if (a <= 1 && bunk) {
                if (h == 0 || h == 3) return p[3];
                if (h == 1) return d(35, cz < 0 ? 14 : 11);
                if (h == 4) return d(35, cz < 0 ? 0 : 9);
            }
            return 0;
        }
        if (cz == 0 && a <= 3) { if (h == 0) return p[3]; if (h == 1) { if (a == 2) return TORCH; if (a == 1) return d(118, 0); } }
        if (b == 1 && a <= 3 && h == 0) return woodStair(w, dir(tr, cz > 0 ? 2 : 3));
        return 0;
    }

    // ---------------------------------------------------------------- 52 Sexton's Workshop
    /** A: a bench of crafting tables under a tool rack, a coffin half-lidded on trestles. B: an open grave's mound, a cross and a spade. */
    private static int workshop(boolean one, int tr, int cx, int cz, int a, int b, int h, int[] p, int w) {
        int fence = FENCE[w];
        if (one) {
            if (cz == -3 && a <= 2 && h <= 3) return p[3];
            if (cz == -2 && a <= 1 && h == 0) return 58;
            if (cz == -2 && a == 2 && h == 2) return fence;
            if (cz == 1 && a == 2 && h == 0) return fence;
            if (cz == 1 && a <= 2 && h == 1) return p[3];
            if (cz == 1 && cx <= 0 && a <= 2 && h == 2) return d(126, w);
            return 0;
        }
        if (a <= 2 && b <= 1) { if (h == 0) return d(3, 1); if (h == 1 && a <= 1 && cz == 0) return d(3, 1); }
        if (cx == 0 && cz == 2 && h <= 2) return fence;
        if (a == 1 && cz == 2 && h == 2) return fence;
        if (cx == 2 && cz == -2 && h <= 2) return fence;
        return 0;
    }

    // ---------------------------------------------------------------- 53 The Unlit Nave
    /** A: two long pews and one candle on a plinth, the only light in the bay. B: facing choir stalls with armrests, webs hanging from the roof. */
    private static int nave(boolean one, int tr, int cx, int cz, int a, int b, int h, int top, int[] p, int w) {
        if (one) {
            if (b == 2 && a <= 3 && h == 0) return woodStair(w, dir(tr, 2));
            if (cx == 0 && cz == 0) { if (h == 0) return p[0]; if (h == 1) return TORCH; }
            return 0;
        }
        if (b == 2 && a <= 3 && h == 0) return woodStair(w, dir(tr, cz < 0 ? 3 : 2));
        if (b == 2 && (a & 1) == 1 && h == 1) return FENCE[w];
        if (a == 1 && b == 1 && h >= top - 1 && h <= top) return WEB;
        return 0;
    }
}
