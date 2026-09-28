package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The shared building kit: boxes, walls, floors, gable roofs, quartz columns, doors, windows, furniture, lamps, trees and
 * crops, all in a structure's local {@link Frame}. Everything here is deterministic (decisions use the frame's rolls).
 */
final class Build {
    private Build() {}

    // ------------------------------------------------------------------ volumes

    /** Walls round a rectangle (local, inclusive) from y0 to y1; {@code wall} is 0 marble, 1 ashlar, 2 cinder, 3 plank. */
    static void walls(Frame f, int x0, int z0, int x1, int z1, int y0, int y1, int wall) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                for (int y = y0; y <= y1; y++) block(f, x, y, z, wall);
            }
    }

    static void block(Frame f, int x, int y, int z, int wall) {
        switch (wall) {
            case 0: f.marble(x, y, z); break;
            case 1: f.ashlar(x, y, z); break;
            case 2: f.cinder(x, y, z); break;
            case 3: f.set(x, y, z, PLANKS, 2); break;
            case 4: f.set(x, y, z, CONCRETE, WHITE); break;
            default: f.set(x, y, z, STONE, 5);
        }
    }

    /** A floor of one block over a rectangle. */
    static void floor(Frame f, int x0, int z0, int x1, int z1, int y, int id, int meta) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) f.set(x, y, z, id, meta);
    }

    /** A paved floor in the Asterian manner. */
    static void pavedFloor(Frame f, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) f.pave(x, y, z);
    }

    /** A mosaic: concentric glazed rings in white, light blue and cyan. */
    static void mosaic(Frame f, int x0, int z0, int x1, int z1, int y) {
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int[] ids = {GLAZED_WHITE, GLAZED_LIGHT_BLUE, GLAZED_CYAN, GLAZED_WHITE, GLAZED_BLUE};
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int ring = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                f.set(x, y, z, ids[ring % ids.length], Math.floorMod(x + z, 4));
            }
    }

    /** Clears the inside of a room above its floor. */
    static void hollow(Frame f, int x0, int z0, int x1, int z1, int y0, int y1) { f.air(x0, y0, z0, x1, y1, z1); }

    /** A simple room: floor, walls, and air inside (no roof). */
    static void room(Frame f, int x0, int z0, int x1, int z1, int floorY, int height, int wall) {
        floor(f, x0, z0, x1, z1, floorY, DOUBLE_SLAB, 8);
        walls(f, x0, z0, x1, z1, floorY + 1, floorY + height, wall);
        hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, floorY + 1, floorY + height);
    }

    // ------------------------------------------------------------------ roofs

    /**
     * A gable roof over a rectangle, ridge along local z (alongZ) or x. {@code tile} 0 terracotta (stairs of brick), 1 marble
     * (quartz stairs, for temples), 2 dark (nether brick stairs, for the Dominion). The gable ends are closed.
     */
    static void gable(Frame f, int x0, int z0, int x1, int z1, int y, boolean alongZ, int tile) {
        int span = alongZ ? x1 - x0 : z1 - z0;
        int layers = span / 2 + 1;
        int stairId = tile == 1 ? QUARTZ_STAIRS : tile == 2 ? NETHER_STAIRS : BRICK_STAIRS;
        for (int k = 0; k < layers; k++) {
            int yy = y + k;
            if (alongZ) {
                int a = x0 + k, b = x1 - k;
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    if (a < b) {
                        f.set(a, yy, z, stairId, f.stairs(1, 0, false));
                        f.set(b, yy, z, stairId, f.stairs(-1, 0, false));
                        boolean end = z == z0 - 1 || z == z1 + 1;
                        for (int x = a + 1; x < b; x++) if (end || z == z0 || z == z1) gableFill(f, x, yy, z, tile);
                    } else if (a == b) f.set(a, yy, z, tile == 1 ? SLAB : tile == 2 ? SLAB : SLAB, tile == 1 ? 7 : tile == 2 ? 6 : 4);
                }
            } else {
                int a = z0 + k, b = z1 - k;
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    if (a < b) {
                        f.set(x, yy, a, stairId, f.stairs(0, 1, false));
                        f.set(x, yy, b, stairId, f.stairs(0, -1, false));
                        boolean end = x == x0 - 1 || x == x1 + 1;
                        for (int z = a + 1; z < b; z++) if (end || x == x0 || x == x1) gableFill(f, x, yy, z, tile);
                    } else if (a == b) f.set(x, yy, a, SLAB, tile == 1 ? 7 : tile == 2 ? 6 : 4);
                }
            }
        }
    }

    private static void gableFill(Frame f, int x, int y, int z, int tile) {
        if (tile == 1) f.marble(x, y, z);
        else if (tile == 2) f.cinder(x, y, z);
        else f.set(x, y, z, CONCRETE, WHITE);
    }

    /** A flat roof with a low parapet. */
    static void flatRoof(Frame f, int x0, int z0, int x1, int z1, int y, int wall) {
        floor(f, x0, z0, x1, z1, y, DOUBLE_SLAB, 8);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (x == x0 || x == x1 || z == z0 || z == z1) f.set(x, y + 1, z, SLAB, wall == 2 ? 6 : 7);
    }

    // ------------------------------------------------------------------ columns and ornament

    /** An Asterian column: a moulded base, a quartz pillar shaft and a chiseled capital. */
    static void column(Frame f, int x, int z, int y0, int y1) {
        f.set(x, y0, z, QUARTZ, 1);
        for (int y = y0 + 1; y < y1; y++) f.set(x, y, z, QUARTZ, 2);
        f.set(x, y1, z, QUARTZ, 1);
    }

    /** A Dominion post: black stone with a spiked iron crown. */
    static void spikePost(Frame f, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) f.cinder(x, y, z);
        f.set(x, y1 + 1, z, NETHER_FENCE);
        f.set(x, y1 + 2, z, BARS);
    }

    /** A lumen lamp: a quartz post with a sea-lantern head and an end-rod finial. */
    static void lamp(Frame f, int x, int y, int z) {
        f.set(x, y, z, QUARTZ, 2);
        f.set(x, y + 1, z, QUARTZ, 2);
        f.set(x, y + 2, z, SEA_LANTERN);
        f.set(x, y + 3, z, END_ROD, 1);
    }

    /** A hanging lumen lamp in a ceiling. */
    static void ceilingLamp(Frame f, int x, int y, int z) { f.set(x, y, z, SEA_LANTERN); }

    /** A brazier (Dominion light): a fence, netherrack and fire. */
    static void brazier(Frame f, int x, int y, int z) {
        f.set(x, y, z, NETHER_FENCE);
        f.set(x, y + 1, z, NETHERRACK);
        f.set(x, y + 2, z, FIRE);
    }

    /** A statue on a plinth: a figure of stacked blocks (feet, robe, torso, head) in marble. */
    static void statue(Frame f, int x, int y, int z, int dx, int dz, int height) {
        f.set(x, y, z, QUARTZ, 1);
        for (int k = 1; k <= height; k++) f.set(x, y + k, z, k == height ? QUARTZ : CONCRETE, k == height ? 1 : WHITE);
        f.set(x + dz, y + height - 1, z + dx, QUARTZ_STAIRS, f.stairs(-dz, -dx, true));
        f.set(x - dz, y + height - 1, z - dx, QUARTZ_STAIRS, f.stairs(dz, dx, true));
    }

    // ------------------------------------------------------------------ doors, windows, furniture

    /** A wooden door facing local (dx, dz) (the side you open it from). */
    static void door(Frame f, int x, int y, int z, int dx, int dz, int doorId) {
        int rx = f.wx(dx, dz) - f.ox, rz = f.wz(dx, dz) - f.oz;
        int facing = rx > 0 ? 0 : rz > 0 ? 1 : rx < 0 ? 2 : 3;
        f.set(x, y, z, doorId, facing);
        f.set(x, y + 1, z, doorId, 8);
    }

    static void window(Frame f, int x, int y, int z, int glassColor) {
        f.set(x, y, z, STAINED_PANE, glassColor);
        f.set(x, y + 1, z, STAINED_PANE, glassColor);
    }

    /** A table: a fence post topped with a pressure plate (or a slab for a desk). */
    static void table(Frame f, int x, int y, int z, boolean desk) {
        f.set(x, y, z, FENCE);
        f.set(x, y + 1, z, desk ? WOOD_SLAB : WOOD_PLATE, desk ? 2 : 0);
    }

    /** A seat: a stair facing local (dx, dz) (you sit looking that way). */
    static void seat(Frame f, int x, int y, int z, int dx, int dz, int stairId) { f.set(x, y, z, stairId, f.stairs(-dx, -dz, false)); }

    /** A bookcase wall segment from y0 to y1. */
    static void shelves(Frame f, int x, int z, int y0, int y1) { for (int y = y0; y <= y1; y++) f.set(x, y, z, BOOKSHELF); }

    // ------------------------------------------------------------------ trees and crops (on the natural ground)

    /** The ground level at a local column, or -1 outside the chunk. */
    static int top(Frame f, int x, int z) { return f.ground(x, z); }

    /** An olive: a short gnarled trunk under a broad grey-green crown. */
    static void olive(Frame f, int x, int z) {
        int g = top(f, x, z);
        if (g < 0) return;
        int t = 2 + (int) (f.roll(x, 0, z, 61) * 2);
        for (int y = 1; y <= t; y++) setW(f, x, g + y, z, LOG, 0);
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++)
                for (int y = t; y <= t + 1; y++) {
                    if (Math.abs(a) + Math.abs(b) > 3 || (y == t + 1 && Math.abs(a) + Math.abs(b) > 2)) continue;
                    if (f.roll(x + a, y, z + b, 62) < 0.85) leafW(f, x + a, g + y + 1, z + b, LEAVES, 4);
                }
    }

    /** A cypress: a tall dark spire. */
    static void cypress(Frame f, int x, int z) {
        int g = top(f, x, z);
        if (g < 0) return;
        int t = 7 + (int) (f.roll(x, 0, z, 63) * 4);
        for (int y = 1; y <= t; y++) setW(f, x, g + y, z, LOG, 1);
        for (int y = 2; y <= t + 1; y++) {
            leafW(f, x, g + y + 1, z, LEAVES, 5);
            if (y < t - 1) for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) leafW(f, x + d[0], g + y, z + d[1], LEAVES, 5);
        }
    }

    /** A fruit tree: an oak with a round crown. */
    static void fruitTree(Frame f, int x, int z) {
        int g = top(f, x, z);
        if (g < 0) return;
        int t = 3 + (int) (f.roll(x, 0, z, 64) * 2);
        for (int y = 1; y <= t; y++) setW(f, x, g + y, z, LOG, 0);
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++)
                for (int y = t - 1; y <= t + 2; y++) {
                    int r = Math.abs(a) + Math.abs(b) + Math.abs(y - t);
                    if (r > 3 || (a == 0 && b == 0 && y <= t)) continue;
                    leafW(f, x + a, g + y, z + b, LEAVES, 4);
                }
    }

    /** A dead tree: a blackened trunk and a few bare limbs. */
    static void deadTree(Frame f, int x, int z, boolean charred) {
        int g = top(f, x, z);
        if (g < 0) return;
        int t = 3 + (int) (f.roll(x, 0, z, 65) * 4);
        int log = charred ? LOG2 : LOG, meta = charred ? 1 : 0;
        for (int y = 1; y <= t; y++) setW(f, x, g + y, z, log, meta);
        if (f.roll(x, 1, z, 66) < 0.7) setW(f, x + 1, g + t - 1, z, log, meta | 4);
        if (f.roll(x, 2, z, 66) < 0.6) setW(f, x, g + t - 2, z - 1, log, meta | 8);
    }

    /** A petrified tree: trunk and limbs of stone, a canopy of cobble walls and andesite. */
    static void stoneTree(Frame f, int x, int z, int size) {
        int g = top(f, x, z);
        if (g < 0) return;
        int t = 5 + size + (int) (f.roll(x, 0, z, 67) * 4);
        for (int y = 1; y <= t; y++) setW(f, x, g + y, z, STONE, y < 3 ? 0 : 5);
        int r = 2 + size;
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++)
                for (int y = t - 1; y <= t + 2; y++) {
                    double d = Math.sqrt(a * a + b * b + (y - t) * (y - t) * 2);
                    if (d > r || f.roll(x + a, y, z + b, 68) > 0.6) continue;
                    double k = f.roll(x + a, y, z + b, 69);
                    leafW(f, x + a, g + y, z + b, k < 0.45 ? WALL : k < 0.75 ? STONE : COBBLE, k < 0.45 ? 0 : k < 0.75 ? 5 : 0);
                }
        if (size > 0) { setW(f, x + 1, g + t - 2, z, STONE, 5); setW(f, x - 1, g + t - 3, z, STONE, 5); }
    }

    private static void setW(Frame f, int x, int y, int z, int id, int meta) { f.c.set(f.wx(x, z), y, f.wz(x, z), id, meta); }

    /** Leaves (or canopy blocks) only into air. */
    private static void leafW(Frame f, int x, int y, int z, int id, int meta) {
        int wx = f.wx(x, z), wz = f.wz(x, z);
        int cur = f.c.get(wx, y, wz);
        if (cur == AIR || cur == TALLGRASS || cur == RED_FLOWER || cur == YELLOW_FLOWER || cur == DEADBUSH) f.c.set(wx, y, wz, id, meta);
    }

    /** A field of a crop on the natural ground: farmland rows with irrigation every fourth row. */
    static void field(Frame f, int x0, int z0, int x1, int z1, int crop, boolean alongZ) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int g = top(f, x, z);
                if (g < 0) continue;
                int row = alongZ ? x : z;
                if (Math.floorMod(row, 5) == 2) { setW(f, x, g, z, WATER, 0); setW(f, x, g + 1, z, AIR, 0); continue; }
                setW(f, x, g, z, FARMLAND, 7);
                int age = (int) (f.roll(x, 0, z, 70) * 3) + 5;
                if (crop == BEETROOT) age = Math.min(age, 3);
                setW(f, x, g + 1, z, crop, crop == BEETROOT ? Math.min(3, age) : Math.min(7, age));
                setW(f, x, g + 2, z, AIR, 0);
            }
    }

    /** Low hedges or fences round a rectangle on the ground. */
    static void fence(Frame f, int x0, int z0, int x1, int z1, int id, int meta, int gateX, int gateZ) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                int g = top(f, x, z);
                if (g < 0) continue;
                if (x == gateX && z == gateZ) { setW(f, x, g + 1, z, AIR, 0); continue; }
                setW(f, x, g + 1, z, id, meta);
            }
    }

    /** Clears plants above the natural ground over a rectangle (so a floor can be laid). */
    static void clearGround(Frame f, int x0, int z0, int x1, int z1, int height) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int g = top(f, x, z);
                if (g < 0) continue;
                for (int y = 1; y <= height; y++) setW(f, x, g + y, z, AIR, 0);
            }
    }

    /** Sets a block on the natural ground (dy above it). */
    static void onGround(Frame f, int x, int z, int dy, int id, int meta) {
        int g = top(f, x, z);
        if (g >= 0) setW(f, x, g + dy, z, id, meta);
    }

    /** A flower bed: mixed blossoms on grass. */
    static void flowers(Frame f, int x0, int z0, int x1, int z1, double density) {
        int[] reds = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int g = top(f, x, z);
                if (g < 0 || f.roll(x, 0, z, 71) > density) continue;
                setW(f, x, g, z, GRASS, 0);
                double k = f.roll(x, 1, z, 72);
                if (k < 0.15) setW(f, x, g + 1, z, YELLOW_FLOWER, 0);
                else if (k < 0.85) setW(f, x, g + 1, z, RED_FLOWER, reds[(int) (k * 97) % reds.length]);
                else { setW(f, x, g + 1, z, DOUBLE_PLANT, k < 0.93 ? 1 : 5); setW(f, x, g + 2, z, DOUBLE_PLANT, 8); }
            }
    }
}
