package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Epoch 5 lesser ruins (owner 2026-10-04: "more dense with dungeons and structures ... 2x it"): small structures and
 * dungeons in the ruin field, one to a 24-block cell like the monuments: a hut's cellar, a barred tomb, a well down to a
 * drowned room, a fallen hut, a spider den and an offering stone. Every one holds a chest or a spawner (most both), so
 * between the great ruins there is always somewhere to go in. Each stays within nine blocks of its cell's centre.
 */
final class Lesser {
    private Lesser() {}

    /** A sealed room of the Choir's stone with air inside. */
    private static void room(Frame f, int a0, int b0, int a1, int b1, int y0, int y1) {
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++)
                for (int y = y0; y <= y1; y++) {
                    if (a == a0 || a == a1 || b == b0 || b == b1 || y == y0 || y == y1) f.masonry(a, y, b); else f.set(a, y, b, AIR);
                }
    }

    /** Floor and low broken walls of a 7x7 hut. */
    private static void hutFloor(Frame f, double sturdy) {
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                f.footing(a, b, -1);
                f.masonry(a, 0, b);
                f.clear(a, b, 1, 6);
                if (Math.max(Math.abs(a), Math.abs(b)) == 3 && !(b == -3 && a == 0)) f.wall(a, b, 1, 3, sturdy);
            }
    }

    /** A hut's floor over a ladder into a cellar: a chest, sometimes a spawner, webs in the corners. */
    static void cellar(Frame f, long h) {
        hutFloor(f, 0.6);
        room(f, -3, -3, 3, 3, -8, -3);
        for (int y = -3; y <= 0; y++) f.set(0, y, 2, AIR);
        for (int y = -7; y <= 0; y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        for (int sa = -1; sa <= 1; sa += 2) for (int sb = -1; sb <= 1; sb += 2) if (f.roll(2 * sa, -4, 2 * sb, 180) < 0.6) f.set(2 * sa, -4, 2 * sb, WEB);
        f.chest(-2, -7, -2, 1, 0, Sites.DUNGEON, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.06");
        f.set(-2, -7, 1, CAULDRON, 0);
        if (Hash.unit(h ^ 181) < 0.55) f.spawner(2, -7, -2, Hash.unit(h ^ 182) < 0.5 ? "ZOMBIE" : "SKELETON");
    }

    /** A gabled tomb with a barred door, a coffin and a chest hidden under it; sometimes a sleeper that wakes. */
    static void tomb(Frame f, long h) {
        for (int a = -3; a <= 3; a++)
            for (int b = -4; b <= 4; b++) {
                f.footing(a, b, -1);
                f.masonry(a, 0, b);
                f.clear(a, b, 1, 7);
                if (Math.abs(a) == 3 || Math.abs(b) == 4) {
                    for (int y = 1; y <= 4; y++) { if (b == -4 && a == 0 && y <= 2) continue; if (y == 4) f.glyph(a, y, b); else f.eldritch(a, y, b); }
                }
                if (a == 0) { f.eldritch(a, 5, b); if (f.keep(a, 6, b, 0.85)) f.set(a, 6, b, SLAB, 5); }
                else if (f.keep(a, 5, b, 0.9)) f.set(a, 5, b, SLAB, 5);
            }
        for (int y = 1; y <= 2; y++) if (f.keep(0, y, -4, 0.55)) f.set(0, y, -4, IRON_BARS);
        f.set(0, 1, 1, DOUBLE_SLAB, 5); f.set(0, 1, 2, DOUBLE_SLAB, 5);
        f.set(0, 2, 1, SLAB, 5); f.set(0, 2, 2, SLAB, 5);
        f.set(-2, 1, 3, SKULL, 1); f.set(2, 1, 3, SKULL, 1);
        if (Hash.unit(h ^ 183) < 0.75) f.chest(0, 0, 2, 0, -1, Sites.DUNGEON, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.08");
        if (Hash.unit(h ^ 184) < 0.4) f.spawner(-2, 1, -2, "SKELETON");
    }

    /** A stone well whose ladder drops to a half-drowned room: a chest on a plinth, sometimes a Deep One's cage. */
    static void well(Frame f, long h) {
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, 5);
            }
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) f.masonry(a, 1, b);
        for (int side = -1; side <= 1; side += 2) { f.set(side, 2, 0, WALL, 1); f.set(side, 3, 0, WALL, 1); }
        for (int a = -1; a <= 1; a++) f.set(a, 4, 0, SLAB, 5);
        room(f, -3, -3, 3, 3, -14, -8);
        for (int y = -8; y <= 1; y++) f.set(0, y, 0, AIR);
        for (int y = -13; y <= 1; y++) { f.masonry(0, y, 1); f.set(0, y, 0, LADDER, f.facing(0, -1)); }
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) if (!(a == 0 && b == 1)) for (int y = -13; y <= -12; y++) f.set(a, y, b, WATER);
        f.set(0, -13, 0, WATER);
        f.set(2, -13, 2, OBSIDIAN);
        f.set(2, -12, 2, OBSIDIAN);
        f.chest(2, -11, 2, -1, 0, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.08");
        if (Hash.unit(h ^ 185) < 0.45) f.spawner(-2, -13, -2, "ZOMBIE");
    }

    /** A fallen hut: crumbling walls, a cold hearth, a table, a chest under the rubble of its roof. */
    static void hut(Frame f, long h) {
        for (int a = -3; a <= 3; a++)
            for (int b = -4; b <= 4; b++) {
                f.footing(a, b, -1);
                f.set(a, 0, b, PLANKS, f.keep(a, 0, b, 0.75) ? 1 : 5);
                f.clear(a, b, 1, 6);
                if (Math.abs(a) == 3 || Math.abs(b) == 4) {
                    boolean door = b == -4 && a == 0, window = Math.abs(a) == 3 && b == 0;
                    for (int y = 1; y <= 4; y++) {
                        if (door && y <= 2 || window && y == 2) continue;
                        if (y >= 3 && !f.keep(a, y, b, 0.75)) continue;
                        f.masonry(a, y, b);
                    }
                } else if (f.keep(a, 5, b, 0.3)) f.set(a, 5, b, 126, 1);
                if (f.roll(a, 1, b, 186) < 0.06) f.rubble(a, 1, b);
            }
        for (int y = 1; y <= 4; y++) f.set(0, y, 3, y == 1 ? MAGMA : COBBLE);
        f.set(-2, 1, -1, FENCE); f.set(-2, 2, -1, PLATE, 0);
        if (Hash.unit(h ^ 187) < 0.85) f.chest(2, 1, 3, -1, 0, Sites.SMITH, "lore:" + Hash.range(h, 0, 99));
        if (Hash.unit(h ^ 188) < 0.2) f.spawner(-2, 1, 2, "ZOMBIE");
    }

    /** A web-choked mound over a pit of cave spiders; a chest among the bones. */
    static void spiderDen(Frame f, long h) {
        for (int a = -5; a <= 5; a++)
            for (int b = -5; b <= 5; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 5.4) continue;
                int up = d < 2.5 ? 2 : d < 4 ? 1 : 0;
                f.footing(a, b, -1);
                f.rubble(a, 0, b);
                for (int y = 1; y <= up; y++) f.rubble(a, y, b);
                f.clear(a, b, up + 1, up + 4);
                if (f.roll(a, up + 1, b, 189) < 0.25) f.set(a, up + 1, b, WEB);
            }
        room(f, -3, -3, 3, 3, -5, -1);
        for (int y = -1; y <= 3; y++) f.set(2, y, 0, AIR);
        for (int y = -4; y <= 3; y++) { f.set(3, y, 0, COBBLE); f.set(2, y, 0, LADDER, f.facing(-1, 0)); }
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
            double v = f.roll(a, -4, b, 190);
            if (a == 2 && b == 0) continue;
            if (v < 0.3) f.set(a, -3, b, WEB); else if (v < 0.42) f.set(a, -4, b, BONE, 0);
        }
        f.spawner(0, -4, 0, "CAVE_SPIDER");
        if (Hash.unit(h ^ 191) < 0.7) f.chest(-2, -4, -2, 1, 0, Sites.DUNGEON, "trinket:0.08");
    }

    /** A raised dais with an altar, skulls at its corners, a carved chant, and an offering buried under it. */
    static void offeringStone(Frame f, long h) {
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                f.footing(a, b, -1);
                int m = Math.max(Math.abs(a), Math.abs(b));
                f.masonry(a, 0, b);
                if (m <= 2) f.eldritch(a, 1, b);
                f.clear(a, b, m <= 2 ? 2 : 1, 6);
            }
        f.set(0, 2, 1, BRICK, 3);
        f.set(0, 3, 1, SLAB, 5);
        for (int sa = -1; sa <= 1; sa += 2) for (int sb = -1; sb <= 1; sb += 2) f.set(2 * sa, 2, 2 * sb, SKULL, 1);
        f.sign(0, 2, 0, 0, -1, Lore.chant(h));
        if (Hash.unit(h ^ 192) < 0.8) f.chest(0, -1, 1, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.1");
    }
}
