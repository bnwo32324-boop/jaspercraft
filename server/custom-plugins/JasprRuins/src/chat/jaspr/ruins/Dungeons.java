package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Greater ruins of the Choir: two dungeons above ground (the Bastion of the Choir, a walled keep full of guards; the
 * Labyrinth of Angles, a true maze with a treasure heart), two temples (the Ossuary Temple of bone; the half-flooded
 * Temple of the Deep) and two monuments (the Star-Watcher's Spire; the Great Idol of Ythaqqua).
 */
final class Dungeons {
    private Dungeons() {}

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        switch (s.kind) {
            case FORTRESS: fortress(f, s.hash); break;
            case LABYRINTH: labyrinth(f, s.hash); break;
            case OSSUARY: ossuary(f, s.hash); break;
            case DEEP_TEMPLE: deepTemple(f, s.hash); break;
            case OBSERVATORY: observatory(f, s.hash); break;
            case GREAT_IDOL: greatIdol(f, s.hash); break;
            default: break;
        }
    }

    private static void ground(Frame f, int r, int clearTo) {
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.paving(a, 0, b);
                f.clear(a, b, 1, clearTo);
            }
    }

    /** A walled bastion: corner towers, a gate, prison cells, and a two-floor keep of spawners and treasure. */
    static void fortress(Frame f, long h) {
        ground(f, 21, 24);
        for (int a = -20; a <= 20; a++)
            for (int b = -20; b <= 20; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b));
                if (m < 19 || !f.inside(a, b)) continue;
                boolean gate = b == -20 || b == -19 ? Math.abs(a) <= 2 : false;
                for (int y = 1; y <= 10; y++) {
                    if (gate && y <= 5) continue;
                    if (y == 10 && (a + b & 1) == 0) continue;   // crenels
                    if (y == 6) f.glyph(a, y, b); else f.eldritch(a, y, b);
                }
            }
        for (int sa = -1; sa <= 1; sa += 2)
            for (int sb = -1; sb <= 1; sb += 2) tower(f, 17 * sa, 17 * sb, 3, 16);
        // The keep.
        for (int a = -6; a <= 6; a++)
            for (int b = -6; b <= 6; b++) {
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == 6;
                for (int y = 1; y <= 14; y++) {
                    if (wall) { if (!(b == -6 && Math.abs(a) <= 1 && y <= 3) && !(y >= 9 && y <= 10 && (a + b) % 4 == 0)) { if (y == 7 || y == 14) f.glyph(a, y, b); else f.eldritch(a, y, b); } }
                    else if (y == 7) f.set(a, y, b, PRISMARINE, 2);
                    else f.set(a, y, b, AIR);
                }
            }
        f.set(-4, 7, 4, AIR); f.set(-4, 7, 3, AIR);
        for (int y = 1; y <= 7; y++) f.set(-4, y, 5, LADDER, f.facing(0, -1));
        f.spawner(3, 1, 3, "ZOMBIE");
        f.spawner(-3, 1, 3, "SKELETON");
        f.spawner(0, 8, 0, "ZOMBIE");
        f.set(0, 1, -5, PLATE, 0);
        f.dispenser(0, 0, -5, 0, 0);
        f.set(3, 8, 4, OBSIDIAN);
        f.chest(3, 9, 4, -1, 0, Sites.DESERT, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.35");
        f.chest(-3, 8, -4, 1, 0, Sites.SMITH, "trinket:0.15");
        // Prison cells along the east wall of the yard.
        for (int b = -14; b <= 14; b += 4) {
            for (int y = 1; y <= 3; y++) { f.set(15, y, b - 1, IRON_BARS); f.set(15, y, b, IRON_BARS); f.set(15, y, b + 1, IRON_BARS); }
            f.set(17, 1, b, SKULL, 1);
            f.set(17, 1, b + 1, BONE, 0);
        }
        f.sign(0, 3, -21, 0, -1, "THE BASTION\nOF THE CHOIR\nNONE WHO\nENTER LEAVE");
    }

    private static void tower(Frame f, int cx, int cz, int r, int height) {
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == r;
                f.footing(cx + a, cz + b, -1);
                for (int y = 1; y <= height; y++) {
                    if (wall) { if (y == height && (a + b & 1) == 0) continue; if (y % 5 == 0) f.glyph(cx + a, y, cz + b); else f.eldritch(cx + a, y, cz + b); }
                    else f.set(cx + a, y, cz + b, AIR);
                }
            }
    }

    /**
     * The Labyrinth of Angles: a 9x9-cell maze (4-block cells, walls 7 high, capped so it cannot be walked along the
     * top) carved by a seeded depth-first search; a treasure heart, guarded dead ends, a way in on the south side.
     */
    static void labyrinth(Frame f, long h) {
        int n = 9, cell = 4, half = n * cell / 2;
        boolean[][] eastOpen = new boolean[n][n], southOpen = new boolean[n][n], seen = new boolean[n][n];
        int[] stack = new int[n * n];
        int sp = 0;
        java.util.Random r = new java.util.Random(h);
        stack[sp++] = 0;
        seen[0][0] = true;
        while (sp > 0) {
            int cur = stack[sp - 1], x = cur % n, z = cur / n;
            int[] dirs = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t; }
            boolean moved = false;
            for (int d : dirs) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= n || nz >= n || seen[nx][nz]) continue;
                if (d == 0) eastOpen[x][z] = true; else if (d == 1) eastOpen[nx][z] = true; else if (d == 2) southOpen[x][z] = true; else southOpen[x][nz] = true;
                seen[nx][nz] = true;
                stack[sp++] = nx + nz * n;
                moved = true;
                break;
            }
            if (!moved) sp--;
        }
        ground(f, half + 1, 10);
        for (int a = -half; a <= half; a++)
            for (int b = -half; b <= half; b++) {
                if (!f.inside(a, b)) continue;
                int ga = a + half, gb = b + half, x = ga / cell, z = gb / cell, ia = ga % cell, ib = gb % cell;
                boolean wall;
                if (ga == n * cell || gb == n * cell) wall = true;
                else if (ia == 0 && ib == 0) wall = true;
                else if (ia == 0) wall = x == 0 || !eastOpen[x - 1][z];
                else if (ib == 0) wall = z == 0 || !southOpen[x][z - 1];
                else wall = false;
                if (b == -half && Math.abs(a - (-half + cell * 4 + 2)) <= 1) wall = false;    // the way in
                // The heart: the middle 3x3 cells joined into one hall (the maze already leads into it somewhere).
                boolean inHeart = x >= 3 && x <= 5 && z >= 3 && z <= 5 && ga < n * cell && gb < n * cell;
                if (inHeart && (ia == 0 && x > 3 || ib == 0 && z > 3)) wall = false;
                if (wall) for (int y = 1; y <= 7; y++) if (y == 7) f.set(a, y, b, OBSIDIAN); else f.eldritch(a, y, b);
                else if (f.roll(a, 0, b, 120) < 0.04) f.set(a, 1, b, WEB);
            }
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                int exits = (eastOpen[x][z] ? 1 : 0) + (x > 0 && eastOpen[x - 1][z] ? 1 : 0) + (southOpen[x][z] ? 1 : 0) + (z > 0 && southOpen[x][z - 1] ? 1 : 0);
                int ca = -half + x * cell + 2, cb = -half + z * cell + 2;
                boolean heart = x >= 3 && x <= 5 && z >= 3 && z <= 5;
                if (exits == 1 && !heart && Hash.unit(h, x, z, 121) < 0.35) f.spawner(ca, 0, cb, "ZOMBIE");
            }
        f.set(0, 1, 0, OBSIDIAN);
        f.chest(0, 2, 0, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.5");
        f.spawner(0, 0, 3, "SKELETON");
        f.sign(-half + cell * 4 + 2, 3, -half - 1, 0, -1, "THE LABYRINTH\nOF ANGLES\nCOUNT THEM\nIF YOU CAN");
    }

    /** The Ossuary Temple: walls and pillars of bone, a frieze of skulls, an altar and its keeper. */
    static void ossuary(Frame f, long h) {
        ground(f, 13, 16);
        for (int a = -7; a <= 7; a++)
            for (int b = -11; b <= 11; b++) {
                boolean wall = Math.abs(a) == 7 || Math.abs(b) == 11;
                f.set(a, 0, b, BONE, 0);
                if (!wall) { if (Math.abs(a) == 4 && b % 3 == 0) for (int y = 1; y <= 8; y++) f.set(a, y, b, BONE, 0); continue; }
                for (int y = 1; y <= 9; y++) {
                    if (b == -11 && Math.abs(a) <= 1 && y <= 4) continue;
                    if (y == 7 && (a + b & 1) == 0) f.set(a, y, b, SKULL, 1); else if (y == 9) f.eldritch(a, y, b); else f.set(a, y, b, BONE, 0);
                }
            }
        for (int a = -6; a <= 6; a++) for (int b = -10; b <= 10; b++) if (f.keep(a, 10, b, 0.5)) f.set(a, 10, b, PRISMARINE, 2);
        for (int a = -2; a <= 2; a++) f.set(a, 1, 8, OBSIDIAN);
        f.set(0, 2, 8, SKULL, 1);
        f.chest(0, 1, 10, 0, -1, Sites.DUNGEON, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.3");
        f.spawner(0, 0, 0, "SKELETON");
        f.sign(0, 3, -12, 0, -1, "BONE OF\nOUR BONE\nWE BUILT\nHIS HOUSE");
    }

    /** The Temple of the Deep: a prismarine temple standing in its own drowned court, flooded to the knees within. */
    static void deepTemple(Frame f, long h) {
        for (int a = -15; a <= 15; a++)
            for (int b = -15; b <= 15; b++) {
                if (!f.inside(a, b)) continue;
                int m = Math.max(Math.abs(a), Math.abs(b));
                f.footing(a, b, -3);
                f.set(a, -2, b, PRISMARINE, 2);
                if (m >= 14) { for (int y = -1; y <= 1; y++) f.eldritch(a, y, b); f.clear(a, b, 2, 20); }
                else { f.set(a, -1, b, WATER); f.set(a, 0, b, WATER); f.clear(a, b, 1, 20); }
            }
        for (int a = -8; a <= 8; a++)
            for (int b = -8; b <= 8; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b));
                f.set(a, 0, b, m == 8 ? PRISMARINE : WATER, 1);
                if (m == 8) for (int y = 1; y <= 10; y++) { if (b == -8 && Math.abs(a) <= 1 && y <= 4) { f.set(a, y, b, AIR); continue; } if (y % 4 == 0) f.set(a, y, b, SEA_LANTERN); else f.set(a, y, b, PRISMARINE, y > 7 ? 2 : 1); }
                else for (int y = 1; y <= 10; y++) f.set(a, y, b, AIR);
                if (m <= 7 && f.keep(a, 11, b, 0.8)) f.set(a, 11, b, PRISMARINE, 2);
                if (m <= 4 && f.keep(a, 12, b, 0.8)) f.set(a, 12, b, PRISMARINE, 2);
            }
        for (int a = -2; a <= 2; a++) for (int b = 3; b <= 6; b++) { f.set(a, 1, b, PRISMARINE, 2); f.set(a, 2, b, PRISMARINE, 2); }
        f.chest(0, 3, 5, 0, -1, Sites.JUNGLE, "lore:5;trinket:0.3");
        f.spawner(3, -1, -3, "ZOMBIE");
        f.spawner(-3, -1, -3, "ZOMBIE");
        f.sign(0, 5, -9, 0, -1, "THE DEEP\nREMEMBERS\nTHE CHOIR\nSANG HERE");
    }

    /** The Star-Watcher's Spire: a banded tower crowned with an armillary ring, where the Choir counted the stars. */
    static void observatory(Frame f, long h) {
        ground(f, 11, 50);
        int height = 30 + Hash.range(h, 0, 8);
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == 3;
                for (int y = 1; y <= height; y++) {
                    if (wall) { if (b == -3 && a == 0 && y <= 3) continue; if (y % 6 == 0) f.glyph(a, y, b); else f.eldritch(a, y, b); }
                    else if (y % 8 == 0 || y == height) f.set(a, y, b, PRISMARINE, 2);
                }
            }
        for (int y = 1; y <= height; y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        for (int y = 8; y <= height; y += 8) f.set(0, y, 2, LADDER, f.facing(0, -1));
        for (int k = 0; k < 40; k++) {
            double t = 2 * Math.PI * k / 40;
            int a = (int) Math.round(Math.cos(t) * 7), y = height + 5 + (int) Math.round(Math.sin(t) * 7);
            f.set(a, y, 0, OBSIDIAN);
            int b = (int) Math.round(Math.cos(t) * 7);
            f.set(0, height + 5 + (int) Math.round(Math.sin(t) * 7), b, OBSIDIAN);
        }
        for (int y = height + 1; y <= height + 11; y++) f.set(0, y, 0, y == height + 5 ? SEA_LANTERN : WALL, 0);
        f.chest(1, height + 1, -1, 0, -1, Sites.DUNGEON, "lore:3;trinket:0.25");
        f.sign(1, 2, -4, 0, -1, "THE STARS\nARE ALMOST\nRIGHT");
    }

    /** The Great Idol of Ythaqqua: the Dreamer three times the size of the wayside idols, on a stepped dais. */
    static void greatIdol(Frame f, long h) {
        ground(f, 11, 40);
        for (int a = -9; a <= 9; a++)
            for (int b = -9; b <= 9; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b));
                for (int y = 1; y <= Math.max(0, 10 - m); y++) if (y <= 3) f.eldritch(a, y, b);
            }
        int base = 4;
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                for (int y = base; y <= base + 11; y++) f.set(a, y, b, PRISMARINE, 2);
                for (int y = base + 12; y <= base + 19; y++) if (Math.max(Math.abs(a), Math.abs(b)) <= 3) f.set(a, y, b, PRISMARINE, 0);
            }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = base + 10; y <= base + 24; y++) for (int b = 2; b <= 5; b++) if (y - base - 10 <= b * 3) f.set(side * (4 + (y - base - 10) / 3), y, b, OBSIDIAN);
            for (int y = base + 3; y <= base + 6; y++) for (int b = -4; b <= -3; b++) f.set(side * 4, y, b, OBSIDIAN);
            f.set(side * 2, base + 17, -4, SEA_LANTERN);
            f.set(side * 2, base + 16, -4, SEA_LANTERN);
        }
        for (int a = -3; a <= 3; a++) {
            int len = 5 + (int) (f.roll(a, 0, -4, 122) * 6);
            for (int k = 0; k < len; k++) f.set(a, base + 15 - k, -4, WALL, 1);
        }
        for (int k = 0; k < 10; k++) {
            double t = 2 * Math.PI * k / 10;
            int a = (int) Math.round(Math.cos(t) * 10), b = (int) Math.round(Math.sin(t) * 10);
            f.set(a, 1, b, OBSIDIAN); f.set(a, 2, b, SKULL, 1);
        }
        f.chest(0, 4, -5, 0, -1, Sites.DESERT, "lore:1;trinket:0.3");
        f.sign(0, 2, -10, 0, -1, Lore.chant(h));
    }
}
