package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Epoch 5 dungeons (owner 2026-10-04: "more dense with dungeons ... make new ones"): the Undercroft of the Choir (a
 * pillared crypt hall under a fallen chapel), the Oubliette of the Choir (a prison tower over a pit with a drowned cell
 * at its foot), the Hall of the Drowned Kings (a buried gallery of statues, four side chambers and a throne room), and
 * the Necropolis (a street of barred tombs, some of them still occupied).
 */
final class Undercrofts {
    private Undercrofts() {}

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        switch (s.kind) {
            case UNDERCROFT: undercroft(f, s.hash); break;
            case OUBLIETTE: oubliette(f, s.hash); break;
            case KINGS_HALL: kingsHall(f, s.hash); break;
            case NECROPOLIS: necropolis(f, s.hash); break;
            default: break;
        }
    }

    /** A sealed room (shell of the Choir's stone) with air inside: floor at y0, ceiling at y1. */
    private static void vault(Frame f, int a0, int b0, int a1, int b1, int y0, int y1) {
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                if (!f.inside(a, b)) continue;
                boolean side = a == a0 || a == a1 || b == b0 || b == b1;
                for (int y = y0; y <= y1; y++) {
                    if (side || y == y0 || y == y1) f.eldritch(a, y, b); else f.set(a, y, b, AIR);
                }
            }
    }

    /**
     * A straight stair down along +z from the surface at (0, z0) to the floor at local y = -depth (stair blocks on solid
     * ground, three blocks of headroom, masonry cheeks), ending in a doorway cut through the wall at z0 + depth.
     */
    private static void stairDown(Frame f, int z0, int depth) {
        for (int k = 1; k <= depth; k++) {
            int lz = z0 + k - 1, y = -k;
            for (int a = -2; a <= 2; a++) {
                if (Math.abs(a) == 2) { for (int yy = y - 1; yy <= Math.min(0, y + 4); yy++) f.masonry(a, yy, lz); continue; }
                f.masonry(a, y - 1, lz);
                f.set(a, y, lz, BRICK_STAIRS, f.stairs(0, -1, false));
                for (int yy = y + 1; yy <= Math.min(2, y + 4); yy++) f.set(a, yy, lz, AIR);
            }
        }
    }

    /** The fallen chapel's low walls, broken pillars and paving: the surface of a dungeon. */
    private static void chapelRuin(Frame f, int ha, int b0, int b1, double sturdy) {
        for (int a = -ha; a <= ha; a++)
            for (int b = b0; b <= b1; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, 12);
                boolean wall = Math.abs(a) == ha || b == b0 || b == b1;
                if (wall && !(b == b0 && Math.abs(a) <= 1)) f.wall(a, b, 1, 6, sturdy);
                else if (!wall && Math.abs(a) == ha - 3 && Math.floorMod(b, 4) == 0 && Math.abs(a) > 2) f.pillar(a, b, 1, 6, f.roll(a, 1, b, 150) < 0.6);
            }
    }

    /** The Undercroft: a fallen chapel over a stair into a pillared crypt hall with niches, two spawners and a hoard. */
    static void undercroft(Frame f, long h) {
        if (!f.touches(-13, -13, 13, 13)) return;
        chapelRuin(f, 9, -12, 12, 0.6);
        vault(f, -9, -2, 9, 12, -12, -5);
        // The stair from just inside the chapel door down through the hall's front wall to its floor.
        stairDown(f, -11, 11);
        // Two rows of pillars; niches with sarcophagi down both sides.
        for (int b = 2; b <= 10; b += 4)
            for (int side = -1; side <= 1; side += 2) {
                for (int y = -11; y <= -6; y++) { if (y == -8) f.glyph(4 * side, y, b); else f.masonry(4 * side, y, b); }
                f.set(7 * side, -11, b, DOUBLE_SLAB, 5);
                f.set(7 * side, -10, b, SLAB, 5);
                if (f.roll(7 * side, -9, b, 151) < 0.3) f.set(7 * side, -9, b, SKULL, 1);
            }
        for (int a = -8; a <= 8; a += 16) for (int b = -1; b <= 11; b += 12) f.set(a, -6, b, WEB);
        f.spawner(-6, -11, 4, "ZOMBIE");
        f.spawner(6, -11, 8, "SKELETON");
        // The hoard at the far end, behind a dart trap.
        f.set(0, -11, 11, OBSIDIAN);
        f.chest(0, -10, 11, 0, -1, Sites.DESERT, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.3");
        f.set(0, -11, 9, PLATE, 0);
        f.dispenser(0, -12, 9, 0, 0);
        f.sign(0, -9, 11, 0, -1, Lore.chant(h));
        f.masonry(2, 1, -12);
        f.sign(2, 1, -13, 0, -1, "BELOW THE\nCHAPEL THEY\nSTILL SING");
    }

    /** The Oubliette: a squat prison tower, barred cells round a pit, and at the pit's foot a drowned cell and its keeper. */
    static void oubliette(Frame f, long h) {
        if (!f.touches(-12, -12, 12, 12)) return;
        for (int a = -8; a <= 8; a++)
            for (int b = -8; b <= 8; b++) {
                if (!f.inside(a, b)) continue;
                int m = Math.max(Math.abs(a), Math.abs(b)), d = Math.abs(a) + Math.abs(b);
                if (m > 7 || d > 11) { if (m <= 8 && f.roll(a, 0, b, 152) < 0.25) f.rubbleOnGround(a, b, 1); continue; }
                boolean wall = m == 7 || d == 11;
                f.footing(a, b, -1);
                f.masonry(a, 0, b);
                f.clear(a, b, 1, 13);
                if (wall) {
                    for (int y = 1; y <= 10; y++) {
                        if (b == -7 && Math.abs(a) <= 1 && y <= 3) continue;
                        if (y == 10 && (a + b & 1) == 0) continue;                                   // crenels
                        if (y == 5) f.glyph(a, y, b); else f.eldritch(a, y, b);
                    }
                } else if (m <= 6 && m >= 4) {
                    // Cells round the walls: bars toward the pit, skulls and bones inside.
                    if (m == 4 && !(Math.abs(a) <= 1 && b < 0)) for (int y = 1; y <= 3; y++) f.set(a, y, b, IRON_BARS);
                    else if (m >= 5 && f.roll(a, 1, b, 153) < 0.08) f.set(a, 1, b, f.roll(a, 2, b, 154) < 0.5 ? BONE : SKULL, f.roll(a, 2, b, 154) < 0.5 ? 0 : 1);
                    if (m == 4 && Math.floorMod(a - b, 3) == 0 && b >= 0) for (int y = 1; y <= 3; y++) f.set(a, y, b, AIR);   // doors
                }
                if (m <= 6 && f.keep(a, 7, b, 0.6)) f.set(a, 7, b, SLAB, 5);                           // a sagging upper floor
            }
        // The pit: a 3x3 shaft from the yard to the drowned cell, a fence round its lip, a ladder down one side.
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                for (int y = -16; y <= 0; y++) f.set(a, y, b, AIR);
                if ((a != 0 || b != 0) && !(a == 0 && b == 1)) f.set(a, 1, b, f.keep(a, 1, b, 0.6) ? FENCE : AIR);
            }
        f.set(0, 1, 1, AIR);                                                                    // the way out at the ladder's head
        for (int y = -15; y <= 0; y++) f.set(0, y, 1, LADDER, f.facing(0, -1));
        for (int y = -16; y <= 0; y++) f.masonry(0, y, 2);
        vault(f, -4, -4, 4, 4, -17, -11);
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                f.set(a, -16, b, WATER);
                if (f.roll(a, -15, b, 155) < 0.12) f.set(a, -15, b, BONE, 0);
            }
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) f.set(a, -11, b, AIR);
        f.set(3, -16, 3, OBSIDIAN);
        f.chest(3, -15, 3, -1, 0, Sites.DESERT, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.3");
        f.spawner(-3, -15, -3, "ZOMBIE");
        f.spawner(5, 1, 3, "SKELETON");
        f.sign(2, 2, -8, 0, -1, "THE FORGOTTEN\nARE KEPT\nBELOW");
    }

    /** The Hall of the Drowned Kings: a buried gallery of statues, four side chambers and a throne room with the hoard. */
    static void kingsHall(Frame f, long h) {
        if (!f.touches(-18, -18, 18, 18)) return;
        // The portico on the surface: paving, four pillars and a lintel over the stair.
        for (int a = -6; a <= 6; a++)
            for (int b = -18; b <= -6; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, 10);
            }
        for (int side = -1; side <= 1; side += 2)
            for (int b = -17; b <= -13; b += 4) f.pillar(4 * side, b, 1, 6, f.roll(side, 1, b, 160) < 0.3);
        for (int a = -4; a <= 4; a++) for (int b = -17; b <= -13; b += 4) if (f.keep(a, 7, b, 0.7)) f.masonry(a, 7, b);
        stairDown(f, -16, 9);
        // The gallery of kings.
        vault(f, -4, -8, 4, 10, -10, -4);
        for (int a = -1; a <= 1; a++) for (int y = -9; y <= -7; y++) f.set(a, y, -8, AIR);
        for (int b = -6; b <= 8; b += 2)
            for (int side = -1; side <= 1; side += 2) {
                if (b == -2 || b == 6) continue;                                                   // the side doorways
                for (int y = -9; y <= -7; y++) f.set(3 * side, y, b, STONE, y == -7 ? 6 : 0);       // a king: body, crowned head
                f.set(3 * side, -6, b, BRICK, 3);
            }
        for (int b = -7; b <= 9; b++) if (f.roll(0, -9, b, 161) < 0.06) f.set(0, -9, b, BONE, 0);
        // Four side chambers off the gallery: crypt, treasury, library, bone pit.
        int[][] rooms = {{-1, -2}, {1, -2}, {-1, 6}, {1, 6}};
        for (int r = 0; r < 4; r++) {
            int side = rooms[r][0], cb = rooms[r][1], ca = 9 * side;
            vault(f, ca - 4, cb - 4, ca + 4, cb + 4, -10, -4);
            for (int y = -9; y <= -7; y++) { f.set(4 * side, y, cb, AIR); f.set(5 * side, y, cb, AIR); }
            switch (r) {
                case 0:
                    for (int b = cb - 2; b <= cb + 2; b += 2) { f.set(ca - side, -9, b, DOUBLE_SLAB, 5); f.set(ca - side, -8, b, SLAB, 5); }
                    f.spawner(ca + 2 * side, -9, cb, "SKELETON");
                    break;
                case 1:
                    f.set(ca + 2 * side, -9, cb, OBSIDIAN);
                    f.chest(ca + 2 * side, -8, cb, -side, 0, Sites.DUNGEON, "trinket:0.2");
                    f.set(ca, -9, cb, PLATE, 0);
                    f.dispenser(ca, -10, cb, 0, 0);
                    break;
                case 2:
                    for (int b = cb - 3; b <= cb + 3; b++) for (int y = -9; y <= -6; y++) if (f.keep(ca + 3 * side, y, b, 0.8)) f.set(ca + 3 * side, y, b, BOOKSHELF);
                    f.chest(ca + 2 * side, -9, cb + 3, 0, -1, Sites.LIBRARY, "lore:" + Hash.range(h, 0, 99));
                    break;
                default:
                    for (int a = ca - 3; a <= ca + 3; a++) for (int b = cb - 3; b <= cb + 3; b++) {
                        double v = f.roll(a, -9, b, 162);
                        if (v < 0.3) f.set(a, -9, b, BONE, 0); else if (v < 0.4) f.set(a, -9, b, WEB);
                    }
                    f.spawner(ca, -9, cb, "CAVE_SPIDER");
                    break;
            }
        }
        // The throne room behind the gallery.
        vault(f, -8, 10, 8, 18, -10, -3);
        for (int a = -1; a <= 1; a++) for (int y = -9; y <= -7; y++) f.set(a, y, 10, AIR);
        for (int a = -2; a <= 2; a++) for (int b = 14; b <= 16; b++) f.set(a, -9, b, PRISMARINE, 2);
        for (int a = -1; a <= 1; a++) f.set(a, -9, 13, BRICK_STAIRS, f.stairs(0, 1, false));
        f.set(0, -8, 15, BRICK_STAIRS, f.stairs(0, -1, false));
        f.set(-1, -8, 15, BRICK, 3); f.set(1, -8, 15, BRICK, 3);
        f.set(0, -8, 16, BRICK, 3); f.set(0, -7, 16, BRICK, 3); f.set(0, -6, 16, SKULL, 1);
        for (int side = -1; side <= 1; side += 2) { f.set(6 * side, -6, 12, SEA_LANTERN); f.set(6 * side, -6, 16, SEA_LANTERN); }
        f.chest(0, -9, 17, 0, -1, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h), 0, 99) + ";trinket:0.5");
        f.spawner(-5, -9, 14, "ZOMBIE");
        f.spawner(5, -9, 14, "ZOMBIE");
        for (int y = 1; y <= 3; y++) f.masonry(-3, y, -17);                                   // a stele beside the stair
        f.sign(-3, 2, -18, 0, -1, "HERE LIE THE\nKINGS WHO\nKNELT TO\nTHE DEEP");
    }

    /** The Necropolis: a walled street of barred tombs; some hold a hoard, some are not empty. */
    static void necropolis(Frame f, long h) {
        if (!f.touches(-15, -15, 15, 15)) return;
        for (int a = -2; a <= 2; a++)
            for (int b = -15; b <= 14; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, 9);
            }
        // The gate: two posts and a lintel, bars half rusted away.
        for (int side = -1; side <= 1; side += 2) { f.footing(3 * side, -15, 0); f.pillar(3 * side, -15, 1, 5, false); }
        for (int a = -3; a <= 3; a++) f.masonry(a, 6, -15);
        for (int a = -2; a <= 2; a++) for (int y = 3; y <= 5; y++) if (f.keep(a, y, -15, 0.55)) f.set(a, y, -15, IRON_BARS);
        int n = 0;
        for (int side = -1; side <= 1; side += 2)
            for (int b0 = -12; b0 <= 9; b0 += 7, n++) tomb(f, h, n, side, b0);
        // Grave posts between the tombs.
        for (int a = -14; a <= 14; a++)
            for (int b = -14; b <= 14; b++) {
                if (Math.abs(a) <= 3 || Math.abs(a) >= 4 && Math.abs(a) <= 9 && Math.floorMod(b + 12, 7) <= 4) continue;
                if (f.roll(a, 0, b, 163) < 0.05) f.onGround(a, b, 1, WALL, 1);
            }
        f.sign(0, 6, -16, 0, -1, "LET THEM\nSLEEP");
    }

    /** One tomb on side (-1 west, 1 east) of the street, from b0 to b0 + 4: walls, a gabled roof, a barred door, a coffin. */
    private static void tomb(Frame f, long h, int n, int side, int b0) {
        for (int k = 4; k <= 9; k++)
            for (int b = b0; b <= b0 + 4; b++) {
                int a = k * side;
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.masonry(a, 0, b);
                f.clear(a, b, 1, 8);
                boolean wall = k == 4 || k == 9 || b == b0 || b == b0 + 4;
                for (int y = 1; y <= 4; y++) if (wall) f.masonry(a, y, b);
                if (b == b0 + 2) { f.masonry(a, 5, b); if (f.keep(a, 6, b, 0.8)) f.set(a, 6, b, SLAB, 5); }   // the ridge
                else if (f.keep(a, 5, b, 0.9)) f.set(a, 5, b, SLAB, 5);
            }
        int door = 4 * side, mid = b0 + 2;
        for (int y = 1; y <= 2; y++) f.set(door, y, mid, Hash.unit(h, n, y, 164) < 0.6 ? IRON_BARS : AIR);
        f.set(8 * side, 1, mid, DOUBLE_SLAB, 5);
        f.set(7 * side, 1, mid, DOUBLE_SLAB, 5);
        f.set(8 * side, 2, mid, SLAB, 5);
        f.set(7 * side, 2, mid, SLAB, 5);
        double r = Hash.unit(h, n, 0, 165);
        if (r < 0.45) f.chest(6 * side, 1, b0 + 1, -side, 0, Sites.DUNGEON, r < 0.12 ? "lore:" + Hash.range(Hash.mix(h ^ n), 0, 99) + ";trinket:0.15" : null);
        if (Hash.unit(h, n, 1, 166) < 0.35) f.spawner(6 * side, 1, b0 + 3, "SKELETON");
        else if (f.roll(6 * side, 1, b0 + 3, 167) < 0.5) f.set(6 * side, 1, b0 + 3, SKULL, 1);
    }
}
