package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * Epoch 5 ruins out in Drownhollow's shallows (owner 2026-10-04: "make new ones"): each stands on the sea floor 3 to 24
 * blocks down and rises out of the water. The Sea Temple (a flooded hall with a dry gallery above the waterline), the
 * wreck of a Choir barge, a drowned lighthouse with its keeper's chest, and a shrine of the tide on its islet.
 * Hollows below the sea's surface hold water, above it air, so nothing is an air bubble under the sea.
 */
final class Shallows {
    private Shallows() {}

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int ws = Math.max(1, Plans.SEA - s.base);   // local y of the sea's top water block
        switch (s.kind) {
            case SUNKEN_TEMPLE: seaTemple(f, s.hash, ws); break;
            case WRECK: wreck(f, s.hash, ws); break;
            case LIGHTHOUSE: lighthouse(f, s.hash, ws); break;
            case TIDE_SHRINE: tideShrine(f, s.hash, ws); break;
            default: break;
        }
    }

    /** Water up to the sea's surface, air above it. */
    private static void hollow(Frame f, int lx, int y, int lz) { f.set(lx, y, lz, f.base + y <= Plans.SEA ? WATER : AIR); }

    private static void hollow(Frame f, int lx, int lz, int y0, int y1) { for (int y = y0; y <= y1; y++) hollow(f, lx, y, lz); }

    /** A prismarine temple on the sea floor: a flooded hall of pillars and an altar, a dry gallery above the waterline. */
    static void seaTemple(Frame f, long h, int ws) {
        if (!f.touches(-13, -13, 13, 13)) return;
        int gallery = ws + 1, top = ws + 7;
        for (int a = -12; a <= 12; a++)
            for (int b = -12; b <= 12; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                f.set(a, 0, b, PRISMARINE, Math.max(Math.abs(a), Math.abs(b)) % 3 == 0 ? 2 : 1);
                hollow(f, a, b, 1, top + 3);
                boolean wall = Math.abs(a) == 8 && Math.abs(b) <= 10 || Math.abs(b) == 10 && Math.abs(a) <= 8;
                if (wall) {
                    for (int y = 1; y <= top; y++) {
                        boolean door = b == -10 && Math.abs(a) <= 1 && (y <= 4 || y > gallery && y <= gallery + 3);
                        if (door) continue;
                        if (y > top - 2 && !f.keep(a, y, b, 0.7)) continue;
                        boolean corner = Math.abs(a) == 8 && Math.abs(b) == 10;
                        f.set(a, y, b, corner && y % 6 == 3 ? SEA_LANTERN : PRISMARINE, y % 4 == 0 ? 2 : 1);
                    }
                } else if (Math.abs(a) < 8 && Math.abs(b) < 10) {
                    if (f.keep(a, top, b, 0.65)) f.set(a, top, b, PRISMARINE, 2);                    // the fallen roof
                    boolean well = Math.abs(a) <= 1 && Math.abs(b) <= 1, ladder = a == -6 && b == 9;
                    if (!well && !ladder) f.set(a, gallery, b, PRISMARINE, 2);
                }
            }
        // The flooded hall: four pillars and an altar with its offering chest.
        for (int sa = -1; sa <= 1; sa += 2)
            for (int sb = -1; sb <= 1; sb += 2) for (int y = 1; y < gallery; y++) f.set(4 * sa, y, 5 * sb, y % 5 == 2 ? SEA_LANTERN : PRISMARINE, 2);
        f.set(0, 1, 7, OBSIDIAN);
        f.chest(0, 2, 7, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.15");
        f.spawner(0, 1, -5, "ZOMBIE");
        for (int y = 1; y <= gallery; y++) f.set(-6, y, 9, LADDER, f.facing(0, -1));
        // The dry gallery: the keeper's hoard above the water.
        f.chest(6, gallery + 1, 8, -1, 0, Sites.DESERT, "lore:" + Hash.range(Hash.mix(h), 0, 99) + ";trinket:0.25");
        f.set(-6, gallery + 1, -8, SEA_LANTERN);
        f.sign(2, gallery + 2, -9, 0, 1, "THE SEA\nKEEPS WHAT\nIT TAKES");
    }

    /** Half-width of the barge's hull at local z: full amidships, narrowing to the bow and stern. */
    private static int beam(int lz) { int d = Math.abs(lz); return d <= 6 ? 3 : d <= 8 ? 2 : d <= 10 ? 1 : -1; }

    /** The wreck of a Choir barge on the sea floor: a broken hull, a rotted deck, a cabin at the stern and a snapped mast. */
    static void wreck(Frame f, long h, int ws) {
        if (!f.touches(-12, -12, 12, 12)) return;
        for (int a = -4; a <= 4; a++)
            for (int lz = -11; lz <= 11; lz++) {
                if (!f.inside(a, lz)) continue;
                int w = beam(lz);
                if (w < 0 || Math.abs(a) > w) continue;
                f.footing(a, lz, -1);
                f.set(a, 0, lz, PLANKS, 5);                                                         // the keel and the bottom
                hollow(f, a, lz, 1, 12);
                boolean side = Math.abs(a) == w || Math.abs(lz) == 10;
                boolean breach = a == w && lz >= -2 && lz <= 1 && w == 3;
                for (int y = 1; y <= 5; y++) {
                    if (!side || breach && y <= 3) continue;
                    if (y >= 4 && !f.keep(a, y, lz, 0.85)) continue;
                    f.set(a, y, lz, PLANKS, (y & 1) == 0 ? 5 : 1);
                }
                if (y5rail(a, w, lz) && f.keep(a, 6, lz, 0.6)) f.set(a, 6, lz, FENCE);
                if (!side && !(a == 0 && (lz == 0 || lz == 1)) && f.keep(a, 4, lz, 0.72)) f.set(a, 4, lz, PLANKS, 1);   // the deck, a hatch amidships
            }
        // The stern cabin.
        for (int a = -2; a <= 2; a++)
            for (int lz = 5; lz <= 8; lz++) {
                boolean wall = Math.abs(a) == 2 || lz == 5 || lz == 8;
                for (int y = 5; y <= 7; y++) {
                    if (wall && !(lz == 5 && a == 0 && y <= 6)) f.set(a, y, lz, PLANKS, 5);
                    else if (!wall) hollow(f, a, y, lz);
                }
                if (f.keep(a, 8, lz, 0.8)) f.set(a, 8, lz, 126, 1);                                  // a spruce slab roof
            }
        f.set(0, 4, 7, PLANKS, 1);
        f.chest(0, 5, 7, 0, -1, Sites.SMITH, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.12");
        // The snapped mast and its yard.
        int mast = 5 + Hash.range(Hash.mix(h ^ 170), 0, 7);
        for (int y = 4; y <= 4 + mast; y++) f.set(0, y, -3, FENCE);
        if (mast >= 8) for (int a = -3; a <= 3; a++) if (a != 0 && f.keep(a, 2 + mast, -3, 0.7)) f.set(a, 2 + mast, -3, FENCE);
        // The hold's cargo.
        f.chest(2, 1, -4, -1, 0, Sites.JUNGLE);
        f.chest(-2, 1, 3, 1, 0, Sites.DUNGEON, "trinket:0.1");
        // Planks the sea scattered round it.
        for (int a = -11; a <= 11; a++)
            for (int lz = -11; lz <= 11; lz++) {
                if (beam(lz) >= 0 && Math.abs(a) <= beam(lz) + 1) continue;
                if (f.roll(a, 0, lz, 171) < 0.05) f.onGround(a, lz, 1, PLANKS, 5);
            }
    }

    private static boolean y5rail(int a, int w, int lz) { return Math.abs(a) == w && Math.abs(lz) < 10 && (lz < 5 || lz > 8); }

    /** A drowned lighthouse on a mound of rock: a door just above the water, floors on a ladder, a lantern room on top. */
    static void lighthouse(Frame f, long h, int ws) {
        if (!f.touches(-7, -7, 7, 7)) return;
        int top = ws + 16 + Hash.range(h, 0, 4);
        for (int a = -6; a <= 6; a++)
            for (int b = -6; b <= 6; b++) {
                if (!f.inside(a, b)) continue;
                double d = Math.sqrt(a * a + b * b);
                if (d > 6.4) continue;
                f.footing(a, b, -1);
                boolean tower = Math.max(Math.abs(a), Math.abs(b)) <= 3 && Math.abs(a) + Math.abs(b) <= 5;
                int mound = tower ? ws + 1 : ws - 2 + (int) Math.round((6.4 - d) * 0.6) + (f.roll(a, 0, b, 172) < 0.3 ? 1 : 0);
                for (int y = 0; y <= mound; y++) f.rubble(a, y, b);
                hollow(f, a, b, mound + 1, top + 7);
                if (!tower) continue;
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == 3 || Math.abs(a) + Math.abs(b) == 5;
                for (int y = ws + 2; y <= top; y++) {
                    if (wall) {
                        if (b == -3 && a == 0 && y <= ws + 3) continue;                                // the door
                        if (y % 6 == 0 && Math.abs(a) + Math.abs(b) == 3) continue;                     // windows
                        f.set(a, y, b, y % 5 == 0 ? BRICK : MOSSY, y % 5 == 0 ? 3 : 0);
                    } else if ((y - ws) % 6 == 1 && y < top && !(a == 0 && b == 2) && f.keep(a, y, b, 0.8)) f.set(a, y, b, PLANKS, 1);
                }
                if (!wall && !(a == 0 && b == 2)) f.masonry(a, top, b);
            }
        for (int y = ws + 2; y < top; y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        f.set(0, top, 2, AIR);
        f.set(0, top, 2, LADDER, f.facing(0, -1));
        // The lantern room: four posts, barred windows, a sea-lantern heart, a slab cap.
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                boolean edge = Math.max(Math.abs(a), Math.abs(b)) == 2;
                for (int y = top + 1; y <= top + 4; y++) {
                    boolean post = Math.abs(a) == 2 && Math.abs(b) == 2;
                    if (edge) f.set(a, y, b, post ? PRISMARINE : IRON_BARS, post ? 2 : 0);
                }
                if (f.keep(a, top + 5, b, 0.8)) f.set(a, top + 5, b, SLAB, 5);
            }
        f.set(0, top + 1, 2, AIR);                                                              // the ladder's way in
        f.set(0, top + 2, 2, AIR);
        f.set(0, top + 1, 0, BRICK, 3);
        f.set(0, top + 2, 0, SEA_LANTERN);
        f.set(0, top + 3, 0, SEA_LANTERN);
        f.chest(-1, top + 1, -1, 1, 0, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.1");
        f.sign(1, ws + 3, -4, 0, -1, "THE LAMP\nWAS LIT FOR\nTHOSE WHO\nCAME BACK");
    }

    /** A ring of pillars in the water round an islet with an idol, an offering chest, and a drowned pit before it. */
    static void tideShrine(Frame f, long h, int ws) {
        if (!f.touches(-10, -10, 10, 10)) return;
        for (int a = -9; a <= 9; a++)
            for (int b = -9; b <= 9; b++) {
                if (!f.inside(a, b)) continue;
                f.footing(a, b, -1);
                if (Math.max(Math.abs(a), Math.abs(b)) <= 2) { for (int y = 0; y <= ws + 1; y++) f.eldritch(a, y, b); hollow(f, a, b, ws + 2, ws + 9); }
                else { if (f.roll(a, 0, b, 173) < 0.6) f.set(a, 0, b, PRISMARINE, 1); hollow(f, a, b, 1, ws + 9); }
            }
        for (int k = 0; k < 8; k++) {
            double t = Math.PI / 4 * k;
            int a = (int) Math.round(7 * Math.cos(t)), b = (int) Math.round(7 * Math.sin(t));
            boolean broken = Hash.unit(h, k, 0, 174) < 0.3;
            int height = broken ? Math.max(2, ws - 1 - Hash.range(Hash.of(h, k, 175), 0, 3)) : ws + 4 + Hash.range(Hash.of(h, k, 176), 0, 4);
            for (int y = 0; y <= height; y++) { if (y % 6 == 5) f.glyph(a, y, b); else f.eldritch(a, y, b); }
        }
        // The idol of the tide and its offerings.
        for (int y = ws + 2; y <= ws + 4; y++) f.set(0, y, 0, PRISMARINE, 2);
        f.set(0, ws + 5, 0, BRICK, 3);
        f.set(0, ws + 5, -1, SEA_LANTERN);
        f.sign(0, ws + 3, -1, 0, -1, Lore.chant(h));
        f.chest(1, ws + 2, 1, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99));
        // The drowned pit before the islet: a chest of older offerings, and what guards it.
        for (int a = -1; a <= 1; a++)
            for (int b = 4; b <= 6; b++) {
                for (int y = -4; y <= 0; y++) f.set(a, y, b, WATER);
                f.eldritch(a, -5, b);
            }
        f.chest(0, -4, 6, 0, -1, Sites.DESERT, "trinket:0.2");
        f.spawner(0, -4, 4, "ZOMBIE");
    }
}
