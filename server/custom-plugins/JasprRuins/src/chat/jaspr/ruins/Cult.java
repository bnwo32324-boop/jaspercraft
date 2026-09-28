package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The Choir's great works: the five Warden arenas (Sanctum of the Drowned Star, Circle of the Watchers, Pit of Offerings,
 * Spawning Pool, Chapel of the Faceless) and the citadel of the Great Door, whose obsidian leaves the plugin opens when
 * three different Seals are set into it and behind which the Dreamer's Herald waits in a round hall open to the sky.
 */
final class Cult {
    private Cult() {}

    // ------------------------------------------------------------------ Warden arenas

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        switch (s.kind) {
            case SANCTUM: sanctum(f, s.hash); break;
            case MONOLITHS: monoliths(f, s.hash); break;
            case PIT: pit(f, s.hash); break;
            case POOL: pool(f, s.hash); break;
            case CHAPEL: chapel(f, s.hash); break;
            default: break;
        }
    }

    /** Where a Warden arena's boss appears, in world coordinates {x, y, z}. */
    static int[] spawnPoint(Plans.Site s) {
        int lx = 0, y = 1, lz = 0;
        switch (s.kind) {
            case MONOLITHS: y = -2; break;
            case PIT: y = -14; break;
            case POOL: y = 1; break;
            case CHAPEL: lz = 3; break;
            default: break;
        }
        Frame f = new Frame(null, s.x, s.z, s.base, s.rot);
        return new int[] {f.wx(lx, lz), s.base + y, f.wz(lx, lz)};
    }

    private static final String WARDENS = "lore:" + Lore.WARDENS_BOOK + ";trinket:0.25";

    /** A stepped temple of green-black stone; a nave of glyph pillars leading to an idol of the Dreamer. */
    static void sanctum(Frame f, long h) {
        for (int a = -16; a <= 16; a++)
            for (int b = -16; b <= 16; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b)), top = m <= 13 ? 0 : 13 - m;
                f.footing(a, b, top - 1);
                if (m <= 13) f.set(a, 0, b, a == 0 || b == 0 || Math.abs(a) == Math.abs(b) ? OBSIDIAN : PRISMARINE, a == 0 || b == 0 || Math.abs(a) == Math.abs(b) ? 0 : 1);
                else f.eldritch(a, top, b);
                f.clear(a, b, top + 1, 18);
            }
        for (int k = 1; k <= 3; k++) for (int a = -2; a <= 2; a++) f.set(a, 1 - k, -13 - k, BRICK_STAIRS, f.stairs(0, 1, false));
        for (int a = -8; a <= 8; a++)
            for (int b = -12; b <= 12; b++) {
                boolean wall = Math.abs(a) == 8 || Math.abs(b) == 12;
                if (!wall) continue;
                for (int y = 1; y <= 12; y++) {
                    if (b == -12 && Math.abs(a) <= 2 && y <= 6) continue;                       // the doorway
                    if (Math.abs(a) == 8 && y >= 4 && y <= 6 && Math.floorMod(b + 8, 6) == 0) continue;   // windows
                    if (y == 6 || y == 12) f.glyph(a, y, b); else f.eldritch(a, y, b);
                }
                // The upper wall leans in, as no wall should.
                int ia = Math.abs(a) == 8 ? a - Integer.signum(a) : a, ib = Math.abs(b) == 12 ? b - Integer.signum(b) : b;
                for (int y = 13; y <= 15; y++) if (f.keep(ia, y, ib, 0.8)) f.eldritch(ia, y, ib);
            }
        for (int b = -10; b <= 10; b += 4)
            for (int a = -7; a <= 7; a++) if (f.keep(a, 13, b, 0.6)) f.eldritch(a, 13, b);
        for (int side = -1; side <= 1; side += 2)
            for (int b = -8; b <= 4; b += 4) {
                for (int y = 1; y <= 12; y++) if (y == 6) f.glyph(4 * side, y, b); else f.eldritch(4 * side, y, b);
                f.sign(4 * side - side, 3, b, -side, 0, Lore.chant(h ^ b ^ side));
            }
        for (int a = -6; a <= 6; a++)
            for (int b = 7; b <= 11; b++) { f.eldritch(a, 1, b); if (b >= 9) f.eldritch(a, 2, b); }
        for (int a = -1; a <= 1; a++) f.set(a, 1, 5, OBSIDIAN);
        f.set(0, 2, 5, SKULL, 1);
        Field.idol(new Frame(f.c, f.wx(0, 9), f.wz(0, 9), f.base + 2, f.rot), h, 1);
        f.chest(4, 3, 10, -1, 0, Sites.DESERT, WARDENS);
        f.chest(-4, 3, 10, 1, 0, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.2");
    }

    /** Eight giant watchers leaning in over a sunken star, where the Pillar Warden stands. */
    static void monoliths(Frame f, long h) {
        for (int a = -20; a <= 20; a++)
            for (int b = -20; b <= 20; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 20.5) continue;
                if (d <= 11) {
                    f.footing(a, b, -4);
                    boolean star = a == 0 || b == 0 || Math.abs(a) == Math.abs(b);
                    f.set(a, -3, b, star ? OBSIDIAN : PRISMARINE, star ? 0 : 1);
                    f.clear(a, b, -2, 40);
                } else if (d <= 13.5) {
                    int top = -3 + (int) (d - 11) + 1;
                    f.footing(a, b, top - 1);
                    f.eldritch(a, top, b);
                    f.clear(a, b, top + 1, 40);
                } else {
                    f.footing(a, b, -1);
                    f.paving(a, 0, b);
                    f.clear(a, b, 1, 40);
                }
            }
        for (int k = 0; k < 8; k++) {
            double t = k * Math.PI / 4 + 0.2;
            int px = (int) Math.round(Math.cos(t) * 16), pz = (int) Math.round(Math.sin(t) * 16);
            int height = 34 + Hash.range(Hash.of(h, k, 50), 0, 20);
            boolean broken = Hash.unit(h, k, 1, 51) < 0.25;
            for (int y = 0; y <= height; y++) {
                int off = y / 11, ox = px - Integer.signum(px) * Math.min(Math.abs(px), off), oz = pz - Integer.signum(pz) * Math.min(Math.abs(pz), off);
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++) {
                        if (broken && y > height - (int) (f.roll(a, 0, b, 52) * 8) - 6) continue;
                        if (y == 0) f.footing(px + a, pz + b, -1);
                        if (y % 9 == 4) f.glyph(ox + a, y, oz + b); else f.eldritch(ox + a, y, oz + b);
                    }
            }
            f.sign(px - Integer.signum(px) * 2, 2, pz, -Integer.signum(px) == 0 ? 1 : -Integer.signum(px), 0, Lore.chant(h ^ k));
        }
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) f.set(a, -2, b, SKULL, 1);
        f.chest(0, -2, 5, 0, -1, Sites.DESERT, WARDENS);
    }

    /** A round pit with a spiral ramp down to a floor of bones; cages and skull-spikes about the rim. */
    static void pit(Frame f, long h) {
        for (int a = -14; a <= 14; a++)
            for (int b = -14; b <= 14; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 14.4) continue;
                if (d <= 6.5) {
                    for (int y = -17; y <= -16; y++) f.eldritch(a, y, b);
                    double r = f.roll(a, -15, b, 53);
                    if (r < 0.45) f.set(a, -15, b, BONE, 0); else if (r < 0.8) f.set(a, -15, b, GRAVEL); else f.eldritch(a, -15, b);
                    f.clear(a, b, -14, 14);
                    if (r > 0.96) f.set(a, -14, b, SKULL, 1);
                } else if (d <= 8.5) {
                    double t = (Math.atan2(b, a) + 2 * Math.PI) % (2 * Math.PI);
                    int y = -(int) Math.round(t / (2 * Math.PI) * 14);
                    for (int yy = -17; yy <= y; yy++) f.eldritch(a, yy, b);
                    f.clear(a, b, y + 1, 14);
                } else if (d <= 10.5) {
                    for (int yy = -17; yy <= 0; yy++) f.eldritch(a, yy, b);
                    f.clear(a, b, 1, 14);
                } else {
                    f.footing(a, b, -1);
                    f.paving(a, 0, b);
                    f.clear(a, b, 1, 14);
                }
            }
        for (int k = 0; k < 12; k++) {
            double t = k * Math.PI / 6;
            int a = (int) Math.round(Math.cos(t) * 11.8), b = (int) Math.round(Math.sin(t) * 11.8);
            if (k % 3 == 0) {
                for (int da = 0; da <= 1; da++) for (int db = 0; db <= 1; db++) for (int y = 1; y <= 3; y++) f.set(a + da, y, b + db, IRON_BARS);
                f.set(a, 4, b, SLAB, 5); f.set(a + 1, 4, b, SLAB, 5); f.set(a, 4, b + 1, SLAB, 5); f.set(a + 1, 4, b + 1, SLAB, 5);
            } else {
                f.set(a, 1, b, FENCE); f.set(a, 2, b, FENCE); f.set(a, 3, b, SKULL, 1);
            }
        }
        f.sign(0, 1, -13, 0, -1, Lore.chant(h));
        f.chest(4, -14, 0, -1, 0, Sites.DUNGEON, WARDENS);
        f.spawner(-4, -16, 0, "CAVE_SPIDER");
    }

    /** A rimmed pool of dark water, clotted with slime, ringed with broken pillars. */
    static void pool(Frame f, long h) {
        for (int a = -14; a <= 14; a++)
            for (int b = -14; b <= 14; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 14.4) continue;
                if (d <= 10.5) {
                    f.footing(a, b, -8);
                    f.eldritch(a, -7, b);
                    f.set(a, -6, b, PRISMARINE, 2);
                    for (int y = -5; y <= -1; y++) f.set(a, y, b, WATER);
                    f.clear(a, b, 0, 14);
                } else if (d <= 12.5) {
                    f.footing(a, b, -8);
                    for (int y = -7; y <= 1; y++) f.eldritch(a, y, b);
                    f.clear(a, b, 2, 14);
                } else {
                    f.footing(a, b, -1);
                    f.paving(a, 0, b);
                    f.clear(a, b, 1, 14);
                }
            }
        for (int k = 0; k < 5; k++) {
            double t = Hash.unit(h, k, 0, 54) * 2 * Math.PI, r = 3 + Hash.unit(h, k, 1, 55) * 5;
            int ca = (int) Math.round(Math.cos(t) * r), cb = (int) Math.round(Math.sin(t) * r), rise = Hash.range(Hash.of(h, k, 56), 0, 3);
            for (int da = -1; da <= 1; da++)
                for (int db = -1; db <= 1; db++)
                    for (int y = -6; y <= -2 + rise - Math.abs(da) - Math.abs(db); y++) f.set(ca + da, y, cb + db, SLIME);
        }
        for (int k = 0; k < 6; k++) {
            double t = k * Math.PI / 3 + 0.4;
            int a = (int) Math.round(Math.cos(t) * 11.5), b = (int) Math.round(Math.sin(t) * 11.5);
            int height = 6 + Hash.range(Hash.of(h, k, 57), 0, 8);
            for (int y = 2; y <= height; y++) if (y % 5 == 0) f.glyph(a, y, b); else f.eldritch(a, y, b);
        }
        f.sign(0, 2, -13, 0, -1, Lore.chant(h ^ 7));
        f.chest(0, 2, -12, 0, 1, Sites.DUNGEON, WARDENS);
    }

    /** A tall black chapel of blank faces: pews before an altar where the congregation gave up its features. */
    static void chapel(Frame f, long h) {
        for (int a = -6; a <= 6; a++)
            for (int b = -13; b <= 13; b++) {
                boolean nave = Math.abs(a) <= 4 && Math.abs(b) <= 11;
                f.footing(a, b, -1);
                if (nave) f.set(a, 0, b, (a + b & 1) == 0 ? CLAY : STONE, (a + b & 1) == 0 ? 15 : 6);
                else f.paving(a, 0, b);
                f.clear(a, b, 1, 22);
            }
        for (int a = -4; a <= 4; a++)
            for (int b = -11; b <= 11; b++) {
                if (Math.abs(a) != 4 && Math.abs(b) != 11) continue;
                for (int y = 1; y <= 16; y++) {
                    if (b == -11 && Math.abs(a) <= 1 && y <= 4) continue;
                    if (Math.abs(a) == 4 && y >= 5 && y <= 8 && Math.floorMod(b, 4) == 0) continue;
                    double r = f.roll(a, y, b, 58);
                    if (r < 0.6) f.set(a, y, b, CLAY, 15); else if (r < 0.8) f.set(a, y, b, OBSIDIAN); else f.set(a, y, b, CLAY, 7);
                }
            }
        for (int a = -4; a <= 4; a++)
            for (int b = -11; b <= 11; b++) {
                if (f.keep(a, 17, b, 0.75)) f.set(a, 17, b, PRISMARINE, 2);
                if (Math.abs(a) <= 2 && f.keep(a, 18, b, 0.7)) f.set(a, 18, b, PRISMARINE, 2);
                if (a == 0 && f.keep(a, 19, b, 0.7)) f.set(a, 19, b, OBSIDIAN);
            }
        for (int b = -8; b <= 2; b += 2)
            for (int a = -3; a <= 3; a++) if (a != 0) f.set(a, 1, b, BRICK_STAIRS, f.stairs(0, -1, false));
        for (int a = -2; a <= 2; a++) f.set(a, 1, 8, OBSIDIAN);
        f.set(0, 2, 8, SKULL, 1);
        for (int a = -2; a <= 2; a += 2) for (int y = 4; y <= 5; y++) { f.set(a, y, 10, BONE, 0); f.set(a, y + 3, 10, BONE, 0); }
        f.sign(3, 3, -10, 0, 1, Lore.chant(h ^ 9));
        f.sign(-3, 3, -10, 0, 1, "THEY TOOK\nOUR FACES\nFOR THE\nDREAMER");
        f.chest(3, 1, 10, -1, 0, Sites.DUNGEON, WARDENS);
    }

    // ------------------------------------------------------------------ the Great Door

    /** Door leaves (obsidian): x in [-5, 5], y in [1, 18] above the base, z in [0, 1] from the Door's centre. */
    static boolean doorBlock(Plans.Door d, int x, int y, int z) {
        int lx = x - d.x, ly = y - d.base, lz = z - d.z;
        return Math.abs(lx) <= 5 && ly >= 1 && ly <= 18 && (lz == 0 || lz == 1);
    }

    static int[] heraldSpawn(Plans.Door d) { return new int[] {d.x, d.base + 1, d.z + 27}; }

    /** The citadel: a stepped platform, the overhanging Door wall with its great eye, and the round hall of the Herald. */
    static void door(Plans.Door d, Canvas c) {
        if (!c.touches(d.x - 46, d.z - 46, d.x + 46, d.z + 50)) return;
        Frame f = new Frame(c, d.x, d.z, d.base, 0);
        for (int a = -44; a <= 44; a++)
            for (int b = -44; b <= 48; b++) {
                if (!f.inside(a, b)) continue;
                int edge = Math.min(44 - Math.abs(a), Math.min(b + 44, 48 - b)), top = edge <= 4 ? edge - 5 : 0;
                f.footing(a, b, top - 1);
                if (top < 0) f.eldritch(a, top, b);
                else {
                    boolean star = a == 0 || Math.abs(a) == Math.abs(b + 10);
                    f.set(a, 0, b, star ? OBSIDIAN : PRISMARINE, star ? 0 : (f.roll(a, 0, b, 60) < 0.5 ? 1 : 2));
                }
                f.clear(a, b, top + 1, 72);
            }
        // The arena: a round hall open to the sky, ringed by a wall and eight pillars, with the Dreamer's throne.
        for (int a = -22; a <= 22; a++)
            for (int b = 5; b <= 49; b++) {
                if (!f.inside(a, b)) continue;
                double dist = Math.sqrt(a * a + (b - 27) * (b - 27));
                if (dist <= 18.5) {
                    boolean star = a == 0 || b == 27 || Math.abs(a) == Math.abs(b - 27);
                    f.set(a, 0, b, star ? OBSIDIAN : PRISMARINE, star ? 0 : 1);
                } else if (dist <= 21) {
                    for (int y = 1; y <= 36; y++) {
                        if (Math.abs(a) <= 5 && b < 12 && y <= 18) continue;   // the passage from the Door
                        if (y % 8 == 0) f.glyph(a, y, b); else f.eldritch(a, y, b);
                    }
                }
            }
        for (int k = 0; k < 8; k++) {
            double t = k * Math.PI / 4 + Math.PI / 8;
            int pa = (int) Math.round(Math.cos(t) * 15), pb = 27 + (int) Math.round(Math.sin(t) * 15);
            int height = k % 3 == 1 ? 18 : 30;
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int y = 1; y <= height; y++)
                if (y % 7 == 0) f.glyph(pa + a, y, pb + b); else f.eldritch(pa + a, y, pb + b);
        }
        throne(f, 0, 41);
        // The Door wall: vertical around the Door, overhanging the plaza above it.
        for (int a = -24; a <= 24; a++)
            for (int y = 1; y <= 56; y++) {
                int shift = Math.max(0, (y - 24) / 6);
                for (int b = -2 - shift; b <= 3 - shift; b++) {
                    if (!f.inside(a, b)) continue;
                    boolean recess = Math.abs(a) <= 7 && y <= 22 && b <= -1, leaf = Math.abs(a) <= 5 && y <= 18 && (b == 0 || b == 1),
                        passage = Math.abs(a) <= 5 && y <= 18 && b >= 2;
                    if (recess || passage) { f.set(a, y, b, AIR); continue; }
                    if (leaf) { f.set(a, y, b, OBSIDIAN); continue; }
                    boolean frame = b <= 0 && (Math.abs(a) == 8 && y <= 23 || y == 23 && Math.abs(a) <= 8);
                    if (frame || y % 8 == 0) f.glyph(a, y, b); else f.eldritch(a, y, b);
                }
            }
        for (int a = -5; a <= 5; a++)
            for (int b = 2; b <= 11; b++) if (f.inside(a, b)) f.clear(a, b, 1, 18);
        // The eye above the Door, and the five seal-marks under it.
        for (int a = -7; a <= 7; a++)
            for (int y = 27; y <= 35; y++) {
                double e = (a * a) / 49.0 + ((y - 31) * (y - 31)) / 16.0;
                int front = -2 - Math.max(0, (y - 24) / 6) - 1;
                if (e <= 1.0 && e > 0.55) f.set(a, y, front, SEA_LANTERN);
                else if (e <= 0.2) f.set(a, y, front, OBSIDIAN);
                else if (e <= 0.55) f.set(a, y, front, PRISMARINE, 0);
            }
        for (int a = -4; a <= 4; a += 2) f.set(a, 20, -3, BRICK, 3);
        f.sign(-10, 2, -3, 0, -1, "THREE SEALS\nOPEN THE\nGREAT DOOR");
        f.sign(10, 2, -3, 0, -1, Lore.chant(0x5eal ^ 3));
        f.chest(-12, 1, -4, 0, -1, Sites.DESERT, "lore:" + Lore.HERALD_BOOK + ";trinket:0.3");
        f.chest(12, 1, -4, 0, -1, Sites.JUNGLE, "lore:4;trinket:0.3");
    }

    /** The Dreamer's throne: a colossal seated idol at the back of the hall. */
    private static void throne(Frame f, int ox, int oz) {
        for (int a = -4; a <= 4; a++)
            for (int b = -4; b <= 4; b++) for (int y = 1; y <= 4; y++) f.set(ox + a, y, oz + b, PRISMARINE, 2);
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                for (int y = 5; y <= 14; y++) f.set(ox + a, y, oz + b, PRISMARINE, 2);
                for (int y = 15; y <= 19; y++) f.set(ox + a, y, oz + b, PRISMARINE, 0);
            }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 12; y <= 24; y++) for (int b = 1; b <= 3; b++) if (y - 12 <= b * 4) f.set(ox + side * (3 + (y - 12) / 4), y, oz + b, OBSIDIAN);
            f.set(ox + side, 18, oz - 3, SEA_LANTERN);
            for (int y = 7; y <= 9; y++) f.set(ox + side * 3, y, oz - 2, OBSIDIAN);
        }
        for (int a = -2; a <= 2; a++) {
            int len = 4 + (int) (f.roll(ox + a, 16, oz - 3, 61) * 4);
            for (int k = 0; k < len; k++) f.set(ox + a, 16 - k, oz - 3, WALL, 1);
        }
    }
}
