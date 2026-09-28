package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The ruin field of Drownhollow: every 24-block cell that no city, site, Lost City or the Great Door claims holds one
 * monument (giant leaning pillars, obelisks, pillar gates, cyclopean walls, stairs to nowhere, sunken plazas, arches,
 * idols of the Dreamer, cult altars, spire clusters, colonnades, fallen cyclopean blocks), and the ground between is
 * cracked paving strewn with rubble, bones and skulls, with only the odd tuft of grass. Pillars rise out of the water too.
 */
final class Field {
    private Field() {}

    /** Floor, debris and the monuments of every field cell touching the canvas' chunk. */
    static void draw(Plans plans, Canvas c) {
        int i0 = Math.floorDiv(c.x0, Plans.CELL), i1 = Math.floorDiv(c.x0 + 15, Plans.CELL);
        int j0 = Math.floorDiv(c.z0, Plans.CELL), j1 = Math.floorDiv(c.z0 + 15, Plans.CELL);
        for (int i = i0; i <= i1; i++)
            for (int j = j0; j <= j1; j++) {
                Plans.Cell cell = plans.cell(i, j);
                if (cell == null) continue;
                floor(plans, c, i, j);
                minors(plans, c, cell, i, j);
                if (cell.type != null) monument(plans, c, cell);
            }
    }

    /** Cracked paving over the cell's columns in this chunk, with rubble, bones, skulls and stumps; rare grass. */
    private static void floor(Plans plans, Canvas c, int i, int j) {
        int x0 = Math.max(c.x0, i * Plans.CELL), x1 = Math.min(c.x0 + 15, i * Plans.CELL + Plans.CELL - 1);
        int z0 = Math.max(c.z0, j * Plans.CELL), z1 = Math.min(c.z0 + 15, j * Plans.CELL + Plans.CELL - 1);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int g = c.ground(x, z);
                if (g <= Plans.SEA) continue;
                Plans.City city = plans.cityNear(x, z);
                if (city != null && city.outside(x, z) <= 0) continue;   // inside the walls the city paves itself
                double r = c.roll(x, g, z, 40);
                if (r < 0.86) c.paving(x, g, z);
                double d = c.roll(x, g + 1, z, 41);
                if (d < 0.035) c.rubble(x, g + 1, z);
                else if (d < 0.045) c.set(x, g + 1, z, BONE, 0);
                else if (d < 0.051) c.set(x, g + 1, z, SKULL, 1);
                else if (d < 0.060) { c.masonry(x, g + 1, z); if (c.roll(x, g + 2, z, 42) < 0.6) c.masonry(x, g + 2, z); }
                else if (d < 0.066) c.set(x, g + 1, z, WEB);
            }
    }

    /**
     * Two or three lesser remnants in the corners of every cell (also beside cities and sites, where there is room):
     * stumps, small obelisks, sarcophagi, statues, rubble heaps, skull posts and arch fragments, so no stretch of the city
     * lies bare.
     */
    private static void minors(Plans plans, Canvas c, Plans.Cell cell, int i, int j) {
        int cx = i * Plans.CELL + Plans.CELL / 2, cz = j * Plans.CELL + Plans.CELL / 2;
        if (!c.touches(cx - 13, cz - 13, cx + 13, cz + 13)) return;
        int[][] corners = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
        int skip = Hash.range(Hash.mix(cell.hash ^ 90), 0, 4);   // 4 = keep all four
        for (int k = 0; k < 4; k++) {
            if (k == skip) continue;
            long h = Hash.of(cell.hash, k, 91);
            int px = cx + corners[k][0] + Hash.range(h, -2, 2), pz = cz + corners[k][1] + Hash.range(Hash.mix(h), -2, 2);
            if (!c.touches(px - 3, pz - 3, px + 3, pz + 3)) continue;
            Plans.City city = plans.cityNear(px, pz);
            if (city != null && city.outside(px, pz) <= 3) continue;
            if (plans.siteNear(px, pz, 4) || plans.door().near(px, pz, 4)) continue;
            int g = plans.surface(px, pz);
            if (g <= Plans.SEA) continue;
            Frame f = new Frame(c, px, pz, g, Hash.range(Hash.mix(h ^ 1), 0, 3));
            switch (Hash.range(Hash.mix(h ^ 2), 0, 6)) {
                case 0: for (int y = 1; y <= 3 + Hash.range(h, 0, 4); y++) f.eldritch(0, y, 0); break;
                case 1: {
                    int height = 6 + Hash.range(h, 0, 5);
                    for (int y = 1; y <= height; y++) if (y % 4 == 0) f.glyph(0, y, 0); else f.eldritch(0, y, 0);
                    f.set(0, height + 1, 0, OBSIDIAN);
                    break;
                }
                case 2:
                    f.set(0, 1, 0, DOUBLE_SLAB, 5); f.set(0, 1, 1, DOUBLE_SLAB, 5);
                    f.set(0, 2, 0, SLAB, 5); f.set(0, 2, 1, SLAB, 5);
                    f.set(0, 1, -1, SKULL, 1);
                    break;
                case 3:
                    f.set(0, 1, 0, BRICK, 3); f.set(0, 2, 0, STONE, 5); f.set(0, 3, 0, STONE, 5);
                    if (Hash.unit(h ^ 3) < 0.6) f.set(0, 4, 0, PRISMARINE, 0);
                    f.set(-1, 3, 0, WALL, 0); f.set(1, 3, 0, WALL, 0);
                    break;
                case 4:
                    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { f.onGround(a, b, 1, MOSSY, 0); if (a == 0 && b == 0) f.onGround(a, b, 2, COBBLE, 0); }
                    break;
                case 5: f.set(0, 1, 0, FENCE); f.set(0, 2, 0, FENCE); f.set(0, 3, 0, SKULL, 1); break;
                default:
                    for (int y = 1; y <= 5; y++) { f.eldritch(-2, y, 0); f.eldritch(2, y, 0); }
                    for (int a = -2; a <= 2; a++) if (Hash.unit(h, a, 0, 92) < 0.7) f.eldritch(a, 6, 0);
                    break;
            }
        }
    }

    static void monument(Plans plans, Canvas c, Plans.Cell cell) {
        int reach = Plans.CELL / 2 + 3;
        if (!c.touches(cell.x - reach, cell.z - reach, cell.x + reach, cell.z + reach)) return;
        Frame f = new Frame(c, cell.x, cell.z, cell.base, cell.rot);
        long h = cell.hash;
        switch (cell.type) {
            case GIANT_PILLAR: giantPillar(f, h, cell.sea); break;
            case OBELISK: obelisk(f, h, cell.sea); break;
            case PILLAR_GATE: pillarGate(f, h, cell.sea); break;
            case CYCLOPEAN_WALL: cyclopeanWall(f, h); break;
            case STAIR_TO_NOWHERE: stairToNowhere(f, h); break;
            case SUNKEN_PLAZA: sunkenPlaza(f, h); break;
            case ARCHWAY: archway(f, h); break;
            case IDOL: idol(f, h, 1); break;
            case CULT_ALTAR: cultAltar(f, h); break;
            case SPIRE_CLUSTER: spires(f, h, cell.sea); break;
            case COLONNADE_ROW: colonnade(f, h); break;
            case CYCLOPEAN_BLOCKS: blocks(f, h); break;
            case SHRINE_TEMPLE: shrineTemple(f, h); break;
            case CATACOMB_GATE: Catacombs.gate(plans, f, h); break;
            case WATCHER_STATUE: watcher(f, h); break;
            case OBELISK_GROVE: grove(f, h); break;
            case GIBBETS: gibbets(f, h); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ monuments

    /** A giant pillar, 3-7 wide and up to 70+ tall, often leaning a way no pillar can stand; banded with glyphs. */
    static void giantPillar(Frame f, long h, int sea) {
        int w = Hash.unit(h ^ 5) < 0.12 ? 7 : Hash.unit(h ^ 6) < 0.5 ? 5 : 3, r = w / 2;
        int height = 26 + Hash.range(Hash.mix(h ^ 7), 0, 46) + sea;
        boolean lean = Hash.unit(h ^ 8) < 0.45, broken = Hash.unit(h ^ 9) < 0.45;
        int every = 8 + Hash.range(Hash.mix(h ^ 10), 0, 6);
        for (int a = -r - 1; a <= r + 1; a++)
            for (int b = -r - 1; b <= r + 1; b++) {
                f.footing(a, b, -1);
                f.eldritch(a, 0, b);
                if (Math.max(Math.abs(a), Math.abs(b)) <= r) f.eldritch(a, 1, b);
            }
        for (int y = 2; y <= height; y++) {
            int off = lean ? Math.min(6, (y - 2) / every) : 0;
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++) {
                    if (w >= 5 && Math.abs(a) == r && Math.abs(b) == r) continue;
                    int top = broken ? height - (int) (f.roll(a, 0, b, 11) * 7) : height;
                    if (y > top) continue;
                    if (y % 9 == 0) f.glyph(a, y, b + off); else f.eldritch(a, y, b + off);
                }
        }
        int offTop = lean ? Math.min(6, (height - 2) / every) : 0;
        if (!broken) {
            for (int a = -r - 1; a <= r + 1; a++)
                for (int b = -r - 1; b <= r + 1; b++) f.eldritch(a, height + 1, b + offTop);
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++) f.glyph(a, height + 2, b + offTop);
            f.set(0, height + 3, offTop, OBSIDIAN);
            f.set(0, height + 4, offTop, OBSIDIAN);
        }
        if (sea == 0 && Hash.unit(h ^ 11) < 0.25)   // a fallen drum lying beside the plinth
            for (int d = r + 3; d <= r + 9; d++)
                for (int t = -1; t <= 1; t++) {
                    f.onGround(d, t, 1, PRISMARINE, 2);
                    if (t == 0) f.onGround(d, t, 2, PRISMARINE, 1);
                }
        if (sea == 0 && Hash.unit(h ^ 12) < 0.3) f.sign(0, 3, -r - 1, 0, -1, Lore.chant(h));
    }

    /** A tapering obelisk with glyph bands and an obsidian pyramidion. */
    static void obelisk(Frame f, long h, int sea) {
        int height = 16 + Hash.range(Hash.mix(h ^ 13), 0, 18) + sea;
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) { f.footing(a, b, -1); f.eldritch(a, 0, b); }
        for (int y = 1; y <= height; y++) {
            int r = y > height * 0.72 ? 0 : 1;
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++) {
                    if (y % 5 == 0 && (Math.abs(a) == r || Math.abs(b) == r)) f.glyph(a, y, b); else f.eldritch(a, y, b);
                }
        }
        f.set(0, height + 1, 0, OBSIDIAN);
        f.set(0, height + 2, 0, OBSIDIAN);
        if (sea == 0) f.sign(0, 2, -2, 0, -1, Lore.chant(h ^ 1));
    }

    /** Two giant pillars and the lintel across them (fallen in a third of them). */
    static void pillarGate(Frame f, long h, int sea) {
        int height = 18 + Hash.range(Hash.mix(h ^ 14), 0, 10) + sea;
        boolean fallen = Hash.unit(h ^ 15) < 0.3;
        for (int side = -1; side <= 1; side += 2)
            for (int a = 4; a <= 6; a++)
                for (int b = -1; b <= 1; b++) {
                    int lx = side * a;
                    f.footing(lx, b, -1);
                    for (int y = 0; y <= height; y++) if (y % 7 == 3) f.glyph(lx, y, b); else f.eldritch(lx, y, b);
                }
        if (!fallen) {
            for (int lx = -7; lx <= 7; lx++)
                for (int b = -1; b <= 1; b++)
                    for (int y = height + 1; y <= height + 3; y++) if (y == height + 2 && Math.abs(lx) <= 1 && b == -1) f.glyph(lx, y, b); else f.eldritch(lx, y, b);
        } else if (sea == 0) {
            for (int lx = -7; lx <= 7; lx++)
                for (int b = 3; b <= 5; b++) { f.onGround(lx, b, 1, PRISMARINE, 2); if (b == 4) f.onGround(lx, b, 2, PRISMARINE, 1); }
        }
    }

    /** A wall of huge 3x3 blocks, uneven along the top, pierced by a doorway. */
    static void cyclopeanWall(Frame f, long h) {
        int height = 8 + Hash.range(Hash.mix(h ^ 16), 0, 7);
        boolean door = Hash.unit(h ^ 17) < 0.5;
        for (int lx = -10; lx <= 10; lx++)
            for (int b = -1; b <= 1; b++) {
                f.footing(lx, b, -1);
                int top = height - (Math.abs(lx) > 6 ? (Math.abs(lx) - 6) * 2 : 0) - (int) (f.roll(Math.floorDiv(lx, 3), 0, 0, 18) * 3);
                for (int y = 0; y <= top; y++) {
                    if (door && Math.abs(lx) <= 1 && y >= 1 && y <= 5) { f.set(lx, y, b, AIR); continue; }
                    int block = Math.floorMod(Math.floorDiv(lx + 30, 3) + Math.floorDiv(y, 3), 3);
                    if (block == 0) f.set(lx, y, b, PRISMARINE, 2);
                    else if (block == 1) f.set(lx, y, b, BRICK, f.roll(lx, y, b, 19) < 0.5 ? 2 : 0);
                    else f.set(lx, y, b, STONE, 6);
                }
            }
        if (door) for (int lx = -2; lx <= 2; lx++) for (int b = -1; b <= 1; b++) f.glyph(lx, 6, b);
        f.sign(3, 3, -2, 0, -1, Lore.chant(h ^ 2));
    }

    /** A broad stair that climbs to nothing and stops. */
    static void stairToNowhere(Frame f, long h) {
        int steps = 12 + Hash.range(Hash.mix(h ^ 20), 0, 6);
        for (int k = 0; k < steps; k++) {
            int lz = -10 + k, y = k + 1;
            for (int lx = -3; lx <= 3; lx++) {
                f.footing(lx, lz, -1);
                for (int yy = 0; yy < y; yy++) f.eldritch(lx, yy, lz);
                if (Math.abs(lx) == 3) { f.eldritch(lx, y, lz); if (k % 4 == 0) f.glyph(lx, y + 1, lz); }
                else f.set(lx, y, lz, BRICK_STAIRS, f.stairs(0, 1, false));
                f.clear(lx, lz, y + 1 + (Math.abs(lx) == 3 ? 1 + (k % 4 == 0 ? 1 : 0) : 0), y + 4);
            }
        }
        int top = steps;
        f.set(0, top + 1, -10 + steps - 1, STONE, 0);
        f.set(0, top + 2, -10 + steps - 1, SKULL, 1);
    }

    /** A square plaza sunk into the ground, terraced down to a star floor and an obsidian altar. */
    static void sunkenPlaza(Frame f, long h) {
        for (int a = -8; a <= 8; a++)
            for (int b = -8; b <= 8; b++) {
                int m = Math.max(Math.abs(a), Math.abs(b)), top = m >= 6 ? -1 - (8 - m) : -4;
                for (int y = -6; y <= top; y++) f.eldritch(a, y, b);
                if (m < 6) {
                    boolean star = a == 0 || b == 0 || Math.abs(a) == Math.abs(b);
                    f.set(a, -4, b, star ? OBSIDIAN : PRISMARINE, star ? 0 : 1);
                }
                f.clear(a, b, top + 1, 10);
            }
        for (int a = -1; a <= 1; a++) f.set(a, -3, 0, OBSIDIAN);
        f.set(0, -2, 0, SKULL, 1);
        for (int sa = -1; sa <= 1; sa += 2)
            for (int sb = -1; sb <= 1; sb += 2) for (int y = -3; y <= -1; y++) f.eldritch(4 * sa, y, 4 * sb);
        f.sign(0, -3, -1, 0, -1, Lore.chant(h ^ 3));
        if (Hash.unit(h ^ 21) < 0.3) f.chest(0, -3, 1, 0, 1, Sites.DUNGEON, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.12");
    }

    /** A great round arch on two legs; a third of them broken. */
    static void archway(Frame f, long h) {
        boolean broken = Hash.unit(h ^ 22) < 0.35;
        int gapFrom = Hash.range(Hash.mix(h ^ 23), -3, 1);
        for (int lx = -7; lx <= 7; lx++)
            for (int b = -1; b <= 1; b++) {
                if (Math.abs(lx) >= 6) { f.footing(lx, b, -1); for (int y = 0; y <= 6; y++) f.eldritch(lx, y, b); }
                if (broken && lx >= gapFrom && lx <= gapFrom + 3) continue;
                int top = 6 + (int) Math.round(Math.sqrt(Math.max(0, 49 - lx * lx)));
                for (int y = Math.max(0, top - 1); y <= top; y++) if (lx == 0 && y == top) f.glyph(lx, y, b); else f.eldritch(lx, y, b);
            }
    }

    /** An idol of the Dreamer: a squatting, winged thing with a head of tentacles, on a plinth heaped with offerings. */
    static void idol(Frame f, long h, int scale) {
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                f.footing(a, b, -1);
                for (int y = 0; y <= 2; y++) if (y < 2 || Math.max(Math.abs(a), Math.abs(b)) <= 2) f.set(a, y, b, PRISMARINE, 2);
            }
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                for (int y = 3; y <= 6; y++) f.set(a, y, b, PRISMARINE, 2);
                for (int y = 7; y <= 9; y++) f.set(a, y, b, PRISMARINE, 0);
            }
        for (int side = -1; side <= 1; side += 2) {
            f.set(2 * side, 4, -1, OBSIDIAN); f.set(2 * side, 3, -1, OBSIDIAN);
            for (int y = 7; y <= 10; y++) f.set(2 * side, y, 1, OBSIDIAN);
            for (int y = 9; y <= 11; y++) f.set(3 * side, y, 1, PRISMARINE, 2);
            f.set(side, 9, -2, SEA_LANTERN);
        }
        for (int a = -1; a <= 1; a++) {
            int len = 2 + (int) (f.roll(a, 8, -2, 24) * 3);
            for (int k = 0; k < len; k++) f.set(a, 8 - k, -2, WALL, 1);
        }
        for (int a = -3; a <= 3; a += 2) {
            f.set(a, 3, -3, BONE, 0);
            if (f.roll(a, 3, -4, 25) < 0.6) f.onGround(a, -5, 1, SKULL, 1);
        }
        f.sign(0, 1, -4, 0, -1, Lore.chant(h ^ 4));
        if (Hash.unit(h ^ 26) < 0.35) f.chest(0, 3, 2, 0, 1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.15");
    }

    /** A ring of skull-topped posts around an obsidian altar; some still guarded. */
    static void cultAltar(Frame f, long h) {
        for (int a = -6; a <= 6; a++)
            for (int b = -6; b <= 6; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 6.4) continue;
                f.footing(a, b, -1);
                f.set(a, 0, b, d < 2.5 ? OBSIDIAN : PRISMARINE, d < 2.5 ? 0 : 2);
                f.clear(a, b, 1, 6);
            }
        for (int k = 0; k < 8; k++) {
            double t = k * Math.PI / 4;
            int a = (int) Math.round(Math.cos(t) * 5), b = (int) Math.round(Math.sin(t) * 5);
            int height = 2 + (int) (f.roll(a, 1, b, 27) * 2);
            for (int y = 1; y <= height; y++) f.eldritch(a, y, b);
            f.set(a, height + 1, b, SKULL, 1);
        }
        for (int a = -1; a <= 1; a++) f.set(a, 1, 0, DOUBLE_SLAB, 5);
        f.set(0, 2, 0, SKULL, 1);
        f.sign(0, 1, -1, 0, -1, Lore.chant(h ^ 5));
        if (Hash.unit(h ^ 28) < 0.4) f.chest(0, 1, 1, 0, 1, Sites.DUNGEON, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.12");
        if (Hash.unit(h ^ 29) < 0.3) f.spawner(0, -1, 0, "ZOMBIE");
    }

    /** Thin spires leaning in toward each other, each ending in an obsidian point. */
    static void spires(Frame f, long h, int sea) {
        int n = 3 + Hash.range(h, 0, 2);
        for (int k = 0; k < n; k++) {
            double t = 2 * Math.PI * k / n + Hash.unit(h, k, 0, 30) * 0.6;
            int px = (int) Math.round(Math.cos(t) * 6), pz = (int) Math.round(Math.sin(t) * 6);
            int height = 14 + Hash.range(Hash.of(h, k, 31), 0, 24) + sea, w = height > 30 ? 1 : 0;
            for (int a = -w; a <= w; a++) for (int b = -w; b <= w; b++) f.footing(px + a, pz + b, -1);
            for (int y = 0; y <= height; y++) {
                int off = y / 7, ox = px - Integer.signum(px) * Math.min(Math.abs(px), off), oz = pz - Integer.signum(pz) * Math.min(Math.abs(pz), off);
                for (int a = -w; a <= w; a++) for (int b = -w; b <= w; b++) if (y % 8 == 4) f.glyph(ox + a, y, oz + b); else f.eldritch(ox + a, y, oz + b);
                if (y == height) f.set(ox, y + 1, oz, OBSIDIAN);
            }
        }
    }

    /** Two rows of columns along a paved way; lintels where both columns stand. */
    static void colonnade(Frame f, long h) {
        for (int lx = -11; lx <= 11; lx++)
            for (int b = -2; b <= 2; b++) { f.footing(lx, b, -1); f.paving(lx, 0, b); f.clear(lx, b, 1, 13); }
        for (int k = 0; k < 6; k++) {
            int lx = -10 + 4 * k;
            boolean both = true;
            for (int side = -1; side <= 1; side += 2) {
                boolean broken = f.roll(lx, 0, side * 3, 32) < 0.4;
                both &= !broken;
                f.footing(lx, side * 3, -1);
                f.pillar(lx, side * 3, 1, 9, broken);
            }
            if (both && Hash.unit(h, k, 0, 33) < 0.6) for (int b = -3; b <= 3; b++) f.eldritch(lx, 11, b);
        }
    }

    /** A small temple: a portico of columns before a walled cella with an altar, sometimes an offering. */
    static void shrineTemple(Frame f, long h) {
        for (int a = -5; a <= 5; a++)
            for (int b = -8; b <= 8; b++) { f.footing(a, b, -1); f.eldritch(a, 0, b); f.clear(a, b, 1, 10); }
        for (int a = -4; a <= 4; a++)
            for (int b = -2; b <= 7; b++) {
                boolean wall = Math.abs(a) == 4 || b == -2 || b == 7;
                if (!wall) continue;
                for (int y = 1; y <= 7; y++) {
                    if (b == -2 && Math.abs(a) <= 1 && y <= 3) continue;
                    if (y == 7) f.glyph(a, y, b); else f.eldritch(a, y, b);
                }
            }
        for (int a = -4; a <= 4; a += 2) { f.pillar(a, -6, 1, 6, f.roll(a, 0, -6, 130) < 0.3); f.eldritch(a, 8, -6); }
        for (int a = -4; a <= 4; a++) for (int b = -6; b <= -3; b++) if (f.keep(a, 8, b, 0.6)) f.set(a, 8, b, SLAB, 5);
        for (int a = -1; a <= 1; a++) f.set(a, 1, 5, OBSIDIAN);
        f.set(0, 2, 5, SKULL, 1);
        f.sign(2, 2, -3, 0, -1, Lore.chant(h ^ 11));
        if (Hash.unit(h ^ 131) < 0.4) f.chest(0, 1, 6, 0, -1, Sites.JUNGLE, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.15");
        if (Hash.unit(h ^ 132) < 0.35) f.spawner(0, 0, 2, "ZOMBIE");
    }

    /** A faceless watcher: a robed colossus with its arms raised to the sky, its face a blank slab. */
    static void watcher(Frame f, long h) {
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) { f.footing(a, b, -1); f.eldritch(a, 0, b); f.eldritch(a, 1, b); }
        int top = 12 + Hash.range(h, 0, 4);
        for (int y = 2; y <= top; y++) {
            int r = y < top - 4 ? 1 : 0;
            for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) f.set(a, y, b, PRISMARINE, y % 5 == 0 ? 0 : 2);
        }
        for (int y = top + 1; y <= top + 3; y++) for (int a = -1; a <= 1; a++) f.set(a, y, 0, STONE, 6);
        for (int side = -1; side <= 1; side += 2)
            for (int k = 0; k < 6; k++) f.set(side * (2 + k / 2), top - 3 + k, 0, PRISMARINE, 2);
        f.sign(0, 1, -3, 0, -1, "IT HAS NO\nFACE\nIT WATCHES\nANYWAY");
    }

    /** A grove of small obelisks around a skull on an obsidian stone. */
    static void grove(Frame f, long h) {
        int n = 5 + Hash.range(h, 0, 2);
        for (int k = 0; k < n; k++) {
            int a = Hash.range(Hash.of(h, k, 133), -9, 9), b = Hash.range(Hash.of(h, k, 134), -9, 9), height = 5 + Hash.range(Hash.of(h, k, 135), 0, 7);
            f.footing(a, b, -1);
            for (int y = 0; y <= height; y++) if (y % 4 == 2) f.glyph(a, y, b); else f.eldritch(a, y, b);
            f.set(a, height + 1, b, OBSIDIAN);
        }
        f.footing(0, 0, -1);
        f.set(0, 0, 0, OBSIDIAN);
        f.set(0, 1, 0, SKULL, 1);
    }

    /** A row of gibbets: iron cages hung from posts, bones and skulls inside. */
    static void gibbets(Frame f, long h) {
        for (int k = -2; k <= 2; k++) {
            int a = k * 4;
            f.footing(a, 0, -1);
            for (int y = 0; y <= 7; y++) f.eldritch(a, y, 0);
            f.eldritch(a, 7, 1); f.eldritch(a, 7, 2);
            for (int y = 3; y <= 5; y++) { f.set(a - 1, y, 2, IRON_BARS); f.set(a + 1, y, 2, IRON_BARS); f.set(a, y, 3, IRON_BARS); f.set(a, y, 1, IRON_BARS); }
            f.set(a, 6, 2, IRON_BARS);
            f.set(a, 3, 2, BONE, 0);
            f.set(a, 4, 2, SKULL, 1);
        }
    }

    /** Huge fallen blocks, half sunk, one still bearing a watching eye. */
    static void blocks(Frame f, long h) {
        int n = 4 + Hash.range(h, 0, 2);
        for (int k = 0; k < n; k++) {
            int s = 3 + (Hash.unit(h, k, 0, 34) < 0.4 ? 1 : 0);
            int px = Hash.range(Hash.of(h, k, 35), -8, 8 - s), pz = Hash.range(Hash.of(h, k, 36), -8, 8 - s);
            int sink = Hash.range(Hash.of(h, k, 37), 0, 1), shift = Hash.unit(h, k, 1, 38) < 0.5 ? 1 : 0;
            for (int a = 0; a < s; a++)
                for (int b = 0; b < s; b++) {
                    f.footing(px + a, pz + b, -1);
                    for (int y = -sink; y < s - sink; y++) {
                        int sa = y >= (s - sink) / 2 ? shift : 0;
                        f.set(px + a + sa, y, pz + b, k % 2 == 0 ? PRISMARINE : STONE, k % 2 == 0 ? 2 : 6);
                    }
                }
            if (k == 0) f.set(px + 1, s - sink - 2, pz, SEA_LANTERN);   // an eye on the block's front face
        }
    }
}
