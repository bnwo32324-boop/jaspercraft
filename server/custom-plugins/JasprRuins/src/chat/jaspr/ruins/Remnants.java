package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Epoch 5 ruins of the Choir's daily life (owner 2026-10-04: "more dense with dungeons and structures. make new ones"):
 * a belfry whose black bell still hangs in its broken crown, a cloister round a drowned well, and a scriptorium where
 * the Choir wrote down what the deep told it.
 */
final class Remnants {
    private Remnants() {}

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        switch (s.kind) {
            case BELFRY: belfry(f, s.hash); break;
            case CLOISTER: cloister(f, s.hash); break;
            case SCRIPTORIUM: scriptorium(f, s.hash); break;
            default: break;
        }
    }

    /** A square bell tower: three floors on a ladder, an open belfry with a bell of black stone, rubble at its foot. */
    static void belfry(Frame f, long h) {
        if (!f.touches(-8, -8, 8, 8)) return;
        int top = 20 + Hash.range(h, 0, 5), loft = top - 6;
        for (int a = -7; a <= 7; a++)
            for (int b = -7; b <= 7; b++) {
                if (!f.inside(a, b)) continue;
                int m = Math.max(Math.abs(a), Math.abs(b));
                if (m > 3) { if (f.roll(a, 0, b, 140) < 0.22) f.rubbleOnGround(a, b, 1); continue; }
                f.footing(a, b, -1);
                f.masonry(a, 0, b);
                f.clear(a, b, 1, top + 3);
                if (m == 3) {
                    for (int y = 1; y <= top; y++) {
                        boolean door = b == -3 && a == 0 && y <= 3;
                        boolean slit = Math.abs(a) + Math.abs(b) == 3 && y % 5 == 3;   // mid-wall arrow slits
                        boolean arch = Math.abs(a == 0 || Math.abs(a) == 3 && Math.abs(b) < 3 ? b : a) <= 1 && y >= loft + 2 && y <= top - 2;
                        if (door || slit || arch) continue;
                        if (y > top - 2 && !f.keep(a, y, b, 0.7)) continue;            // the crown has crumbled
                        if (y == loft || y == top) f.glyph(a, y, b); else f.masonry(a, y, b);
                    }
                } else {
                    // Floors at 6 and 12 (rotten, some boards gone) and the solid loft under the bell.
                    for (int y = 6; y < loft; y += 6) if (!(a == 0 && b == 2) && f.keep(a, y, b, 0.8)) f.set(a, y, b, PLANKS, 1);
                    if (!(a == 0 && b == 2)) f.masonry(a, loft, b);
                    if (f.keep(a, top + 1, b, 0.55)) f.set(a, top + 1, b, SLAB, 5);
                }
            }
        for (int y = 1; y <= loft; y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        // The bell: a fence from the roof beam, a black clay bell, an iron clapper.
        f.set(0, top, 0, BRICK, 3);
        f.set(0, top - 1, 0, FENCE);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) f.set(a, top - 3, b, CLAY, 15);
        f.set(0, top - 2, 0, CLAY, 15);
        f.set(0, top - 4, 0, IRON_BARS);
        f.chest(-2, loft + 1, -2, 1, 0, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.12");
        f.chest(2, 1, 2, -1, 0, Sites.DUNGEON);
        f.sign(1, 2, -4, 0, -1, "IT RANG ONCE\nFOR EVERY\nSOUL THE DEEP\nTOOK");
    }

    /** A walled cloister: an arcaded walk round a garth with a drowned well, monks' cells along the back. */
    static void cloister(Frame f, long h) {
        if (!f.touches(-15, -15, 15, 15)) return;
        for (int a = -14; a <= 14; a++)
            for (int b = -14; b <= 14; b++) {
                if (!f.inside(a, b)) continue;
                int m = Math.max(Math.abs(a), Math.abs(b));
                f.footing(a, b, -1);
                if (m <= 9 && (Math.abs(a) == 0 || Math.abs(b) == 0)) f.set(a, 0, b, GRAVEL); else f.paving(a, 0, b);
                f.clear(a, b, 1, 10);
                if (m == 14) {
                    boolean gate = b == -14 && Math.abs(a) <= 1;
                    f.wall(a, b, 1, gate ? 0 : 7, 0.9);
                    if (gate && f.keep(a, 5, b, 0.8)) f.masonry(a, 5, b);
                } else if (m >= 11) {
                    if (f.keep(a, 6, b, 0.75)) f.set(a, 6, b, SLAB, 5);                     // the walk's roof
                } else if (m == 10) {
                    boolean post = Math.floorMod(a + b, 3) == 0;
                    if (post) f.pillar(a, b, 1, 5, f.roll(a, 1, b, 141) < 0.25);
                    else if (f.keep(a, 5, b, 0.7)) f.masonry(a, 5, b);                       // arches between the posts
                }
            }
        // The well in the garth: a masonry ring over a shaft of black water.
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) { for (int y = -5; y <= 0; y++) f.set(a, y, b, WATER); f.masonry(a, -6, b); continue; }
                for (int y = -6; y <= 0; y++) f.masonry(a, y, b);
                if (f.keep(a, 1, b, 0.85)) f.masonry(a, 1, b);
            }
        f.set(-1, 2, -1, WALL, 1); f.set(1, 2, -1, WALL, 1);
        // Monks' cells along the back walk, behind partitions.
        int cellWith = Hash.range(Hash.mix(h ^ 142), 0, 5), lore = Hash.range(Hash.mix(h ^ 143), 0, 5);
        for (int k = 0; k <= 6; k++) {
            int a = -12 + 4 * k;
            for (int b = 11; b <= 13; b++) f.wall(a, b, 1, 5, 0.95);
            if (k == 6) break;
            int ca = a + 2;
            f.set(ca - 1, 1, 13, SLAB, 5);                                                // a stone cot
            if (k == cellWith) f.chest(ca + 1, 1, 13, 0, -1, Sites.DUNGEON, "trinket:0.08");
            if (k == lore) f.chest(ca, 1, 13, 0, -1, Sites.LIBRARY, "lore:" + Hash.range(h, 0, 99));
        }
        // The prior's lectern at the head of the garth.
        f.set(0, 1, 8, BRICK, 3);
        f.set(0, 2, 8, SLAB, 5);
        f.sign(0, 1, 7, 0, -1, "SILENCE.\nTHE DEEP HEARS\nEVERY WORD\nSPOKEN HERE");
    }

    /** A two-storey library: shelves along the walls, desks, a gallery reached by a ladder, a chest of lore up top. */
    static void scriptorium(Frame f, long h) {
        if (!f.touches(-11, -11, 11, 11)) return;
        for (int a = -8; a <= 8; a++)
            for (int b = -10; b <= 10; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, 14);
                boolean wall = Math.abs(a) == 8 || Math.abs(b) == 10;
                if (wall) {
                    for (int y = 1; y <= 12; y++) {
                        if (b == -10 && Math.abs(a) <= 1 && y <= 4) continue;                       // the doorway
                        if (Math.abs(a) == 8 && Math.floorMod(b, 5) == 0 && y >= 3 && y <= 4) continue; // windows
                        if (y > 9 && !f.keep(a, y, b, 1.25 - y * 0.08)) continue;
                        if (y == 6) f.glyph(a, y, b); else f.masonry(a, y, b);
                    }
                    continue;
                }
                // The gallery floor round the sides (rotting), shelves under and on it.
                if (Math.abs(a) >= 5 && f.keep(a, 6, b, 0.8)) f.set(a, 6, b, PLANKS, 1);
                if (Math.abs(a) == 7 && Math.abs(b) <= 8) {
                    for (int y = 1; y <= 4; y++) if (f.keep(a, y, b, 0.85)) f.set(a, y, b, BOOKSHELF);
                    for (int y = 7; y <= 8; y++) if (f.keep(a, y, b, 0.7)) f.set(a, y, b, BOOKSHELF);
                }
                // Desks down the middle: slabs on stone, some fallen.
                if (Math.abs(a) == 2 && Math.floorMod(b, 4) == 1 && Math.abs(b) <= 7) {
                    if (f.roll(a, 1, b, 144) < 0.75) { f.set(a, 1, b, DOUBLE_SLAB, 5); f.set(a, 2, b, SLAB, 5); }
                    else f.rubble(a, 1, b);
                }
                if (f.roll(a, 1, b, 145) < 0.03) f.set(a, 1, b, WEB);
            }
        for (int y = 1; y <= 6; y++) f.set(-6, y, 9, LADDER, f.facing(0, -1));
        f.clear(-6, 9, 7, 8);
        f.chest(6, 7, 9, 0, -1, Sites.LIBRARY, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.15");
        f.chest(0, 1, 9, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h), 0, 99));
        f.sign(2, 3, -11, 0, -1, "WE WROTE DOWN\nWHAT THE DEEP\nSAID. FORGIVE\nUS");
    }
}
