package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static chat.jaspr.biomes.Megaliths.Site;
import static chat.jaspr.biomes.Megaliths.graded;

/**
 * Landmarks: the big set pieces, as opposed to the rooms in Dungeons.
 *
 * These are large enough to see from a distance and are meant to be the reason somebody
 * walks in a direction. Each one is its own architecture rather than a reskin of a box --
 * a house up on legs, a foundry built round its chimney, a tower of glass, an apartment
 * that exists twice. Each also carries a loot table nothing else uses and its own idea of
 * what is dangerous about it, because a landmark that pays out the same as a hole in the
 * ground is not a landmark.
 *
 * Every one of them is an idiom rather than a copy: a ramshackle walking house, a brass
 * workshop, a neon megablock, a sealed flat, a colonial manor, a precinct, a wrecked
 * street, a hill village, a fallen tower, a shopfront row, a flooded platform, and a room
 * where the stairs disagree about which way is down.
 */
public final class Landmarks {
    private Landmarks() { }

    /* Lattice spacing, in chunks. Landmarks are deliberately much rarer than the rooms:
     * finding one should be an event, and two in sight of each other would spoil both. */
    private static final int HOUSE_CELL = 74, FOUNDRY_CELL = 70, ARCOLOGY_CELL = 78;
    private static final int ROOM_CELL = 62, MANOR_CELL = 80, PRECINCT_CELL = 76;
    private static final int STREET_CELL = 68, VILLAGE_CELL = 72, TOWER_CELL = 66;
    private static final int BLOCKS_CELL = 64, METRO_CELL = 58, STAIR_CELL = 84;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        house(w, c, t);
        foundry(w, c, t);
        arcology(w, c, t);
        theRoom(w, c, t);
        manor(w, c, t);
        precinct(w, c, t);
        street(w, c, t);
        village(w, c, t);
        tower(w, c, t);
        blocks(w, c, t);
        metro(w, c, t, caves);
        stair(w, c, t);
    }


    // == 1. The Walking House ====================================================
    // A house that was never one house: someone bolted rooms together and put the result
    // up on legs. Mismatched timber and plate, a stack that still smokes, a round window
    // like an eye at the front, and a hearth inside that has not gone out.

    private static final int[][] HOUSE_LOOT = {
        {61, 0, 10, 1, 1}, {322, 0,  9, 1, 3}, {263, 0, 14, 4, 12}, {331, 0, 12, 4, 16},
        {388, 0,  8, 2, 6}, {348, 0, 10, 3, 9}, {369, 0,  7, 1, 3},  {377, 0,  6, 1, 4},
        {340, 0, 11, 1, 3}, {325, 0, 9, 1, 4}, {343, 0, 6, 2, 6},  {345, 0,  5, 1, 1},
    };

    private static void house(World w, Chunk c, Terrain t) {
        Site s = site(t, c, HOUSE_CELL, 0x484F574C00L, 17, 15, 7, Megaliths.RANK_HOUSE);
        if (s == null) return;
        Random r = new Random(s.seed);
        int floor = s.y + 7;                                  // the body rides this high
        for (int dx = 0; dx < 17; dx++) for (int dz = 0; dz < 15; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            // Four legs, splayed, each a plated column on a wide iron foot.
            // The plate is smooth stone double slab (43:8), not iron block: no valuables in builds.
            boolean leg = (dx == 3 || dx == 13) && (dz == 3 || dz == 11);
            if (leg) {
                footing(c, s, wx, wz, 43, 8);
                // The back-left leg is solid plate all the way up: the hatch ladder hangs on it.
                boolean hatchLeg = dx == 3 && dz == 11;
                for (int y = s.y; y < floor; y++) {
                    boolean plate = y % 3 == 0 || hatchLeg;
                    put(c, wx, y, wz, plate ? 43 : 101, plate ? 8 : 0);
                }
                for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++)
                    put(c, wx + ax, s.y, wz + az, 43, 8);
                continue;
            }
            boolean body = dx >= 2 && dx <= 14 && dz >= 2 && dz <= 12;
            // Nothing grows up into the underside or against the walls (dead trees and the uphill
            // bank were left standing inside the footprint -- structure audit 2026-09-23).
            clearAbove(c, wx, wz, s.y + 1, body ? floor - 2 : floor + 14);
            if (!body) continue;
            // Underside: plate, sagging. The hatch is now beside the back-left leg (see below);
            // the old hole here was capped by the floor and led nowhere.
            int under = dx == 8 && dz == 7 ? 43 : r.nextInt(6) == 0 ? 101 : 43;   // same draws as before
            put(c, wx, floor - 1, wz, under, under == 43 ? 8 : 0);
        }
        int[] palette = {5, 17, 4, 45, 5, 43, 5, 112};        // nothing matches anything (43:8 plate, was iron)
        for (int dx = 2; dx <= 14; dx++) for (int dz = 2; dz <= 12; dz++) for (int dy = 0; dy < 12; dy++) {
            int wx = s.x + dx, wy = floor + dy, wz = s.z + dz;
            boolean outer = dx == 2 || dx == 14 || dz == 2 || dz == 12;
            // The upper storey is set back, so the silhouette steps rather than being a box.
            boolean upper = dy >= 6;
            if (upper && (dx < 4 || dx > 12 || dz < 4 || dz > 10)) { put(c, wx, wy, wz, 0, 0); continue; }
            boolean upperOuter = dx == 4 || dx == 12 || dz == 4 || dz == 10;
            boolean shell = upper ? upperOuter : outer;
            if (dy == 0) { put(c, wx, wy, wz, 5, r.nextInt(4)); continue; }
            if (dy == 5 && !upper) {
                // Rot only where the ledge is open to the weather: the inner ring of the terrace,
                // never the attic floor or the wall top (same draws as before).
                boolean gap = r.nextInt(5) == 0 && !outer && (dx < 4 || dx > 12 || dz < 4 || dz > 10);
                put(c, wx, wy, wz, gap ? 0 : 5, gap ? 0 : 1);
                continue;
            }
            if (dy == 11) { put(c, wx, wy, wz, 53, 0); continue; }
            if (shell) {
                // The eye: a round window over the door (it used to take the door's upper block).
                boolean eye = !upper && dz == 2 && dy >= 3 && dy <= 4 && dx >= 7 && dx <= 9
                        && !(dy == 3 && dx != 8);
                boolean door = !upper && dz == 2 && dx == 8 && dy <= 2;
                boolean pane = upper && dy == 8 && (dx % 3 == 1 || dz % 3 == 1);
                if (eye || pane) { put(c, wx, wy, wz, 102, 0); continue; }
                if (door) { put(c, wx, wy, wz, 193, dy == 1 ? 3 : 8); continue; }   // spruce door, 2 high
                int skin = palette[(dx + dz * 3 + dy) % palette.length];
                put(c, wx, wy, wz, skin, skin == 43 ? 8 : 0);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            // The hearth, still burning, and the stack that carries it up through the roof.
            if (dx == 12 && dz == 10) {
                if (dy <= 1) { put(c, wx, wy, wz, 87, 0); continue; }
                if (dy == 2) { put(c, wx, wy, wz, 51, 0); continue; }
                put(c, wx, wy, wz, 45, 0);
                continue;
            }
            if (dy == 1 && dx == 5 && dz == 10) { put(c, wx, wy, wz, 61, 2); continue; }
            if (dy == 1 && dx == 6 && dz == 4) { put(c, wx, wy, wz, 47, 0); continue; }
            if (dy == 1 && dx == 10 && dz == 4) graded(c, s, wx, wy, wz, r, HOUSE_LOOT, false);
            // The attic chest and spawner stand on the attic floor (dy 5), not a block above it.
            else if (dy == 6 && dx == 6 && dz == 8) graded(c, s, wx, wy, wz, r, HOUSE_LOOT, true);
            else if (dy == 6 && dx == 10 && dz == 6) mob(c, s, wx, wy, wz, r, EntityType.BLAZE);
            else if (dy == 1 && dx == 4 && dz == 6) mob(c, s, wx, wy, wz, r, EntityType.WITCH);
        }
        houseFitOut(c, s, floor);
    }

    /**
     * What makes the Walking House a house (structure audit 2026-09-23): a way up from the
     * ground, a porch at the door, a stair to the attic, a hearth that cannot burn the place
     * down with a stack that reaches the sky, and rooms -- kitchen, parlour, a witch's corner
     * and an attic bedroom -- bolted together out of whatever was to hand.
     */
    private static void houseFitOut(Chunk c, Site s, int floor) {
        int x0 = s.x, z0 = s.z;
        // The hatch: a ladder up the east face of the back-left leg, through plate and floor,
        // coming up beside the furnace.
        for (int y = s.y + 1; y <= floor; y++) put(c, x0 + 4, y, z0 + 11, 65, 5);
        // The porch the door opens onto: a spruce deck on stair brackets, fenced on three sides.
        for (int dx = 6; dx <= 10; dx++) for (int dz = 0; dz <= 1; dz++) {
            put(c, x0 + dx, floor, z0 + dz, 5, 1);
            boolean rail = dz == 0 || dx == 6 || dx == 10;
            put(c, x0 + dx, floor + 1, z0 + dz, rail ? 85 : 0, 0);
            if (dx % 2 == 0 && dz == 1) put(c, x0 + dx, floor - 1, z0 + dz, 53, 6);   // upside-down bracket off the plate
        }
        // The attic stair: five oak steps up along dx 5 on a plank stringer, through a well in the ceiling.
        for (int n = 0; n <= 4; n++) {
            for (int y = floor + 1; y < floor + 1 + n; y++) put(c, x0 + 5, y, z0 + 4 + n, 5, 1);
            put(c, x0 + 5, floor + 1 + n, z0 + 4 + n, 53, 2);
        }
        for (int dz = 5; dz <= 7; dz++) {
            put(c, x0 + 5, floor + 5, z0 + dz, 0, 0);           // the stairwell
            put(c, x0 + 6, floor + 6, z0 + dz, 85, 0);          // and its rail in the attic
        }
        // Hearth: a brick firebox on a brick hearthstone under a brick hood, so the fire that has
        // not gone out has nothing to catch; the stack carries on up the attic corner and out.
        for (int dx = 11; dx <= 13; dx++) for (int dz = 9; dz <= 11; dz++) {
            put(c, x0 + dx, floor, z0 + dz, 45, 0);
            put(c, x0 + dx, floor + 5, z0 + dz, 45, 0);
            boolean box = dz == 11 || (dz == 10 && dx != 12);
            if (box) for (int y = 1; y <= 4; y++) put(c, x0 + dx, floor + y, z0 + dz, 45, 0);
        }
        for (int dz = 9; dz <= 11; dz++) for (int y = 1; y <= 4; y++) put(c, x0 + 14, floor + y, z0 + dz, 45, 0);
        for (int y = 5; y <= 12; y++) put(c, x0 + 12, floor + y, z0 + 10, 45, 0);
        put(c, x0 + 12, floor + 13, z0 + 10, 87, 0);
        put(c, x0 + 12, floor + 14, z0 + 10, 51, 0);            // the stack that still smokes
        for (int y = 6; y <= 10; y++) { put(c, x0 + 11, floor + y, z0 + 10, 45, 0); put(c, x0 + 12, floor + y, z0 + 9, 45, 0); }
        // Rooms bolted together: a spruce wall and a brick wall make a parlour round the hearth.
        for (int y = 1; y <= 4; y++) {
            for (int dz = 7; dz <= 11; dz++) put(c, x0 + 9, floor + y, z0 + dz, dz == 9 && y <= 2 ? 0 : 5, dz == 9 && y <= 2 ? 0 : 1);
            for (int dx = 10; dx <= 13; dx++) put(c, x0 + dx, floor + y, z0 + 7, dx == 10 && y <= 2 ? 0 : 45, 0);
        }
        int f = floor + 1;
        put(c, x0 + 12, f, z0 + 8, 108, 3); put(c, x0 + 13, f, z0 + 8, 108, 3);    // brick settle facing the fire
        put(c, x0 + 10, f, z0 + 10, 85, 0); put(c, x0 + 10, f + 1, z0 + 10, 72, 0); // table
        put(c, x0 + 10, f, z0 + 11, 53, 2);                                        // chair
        // Kitchen round the hatch and the furnace.
        put(c, x0 + 3, f, z0 + 9, 58, 0); put(c, x0 + 3, f, z0 + 11, 118, 3);
        put(c, x0 + 6, f, z0 + 11, 126, 9); put(c, x0 + 7, f, z0 + 11, 126, 9);
        // The witch's corner behind the stair.
        put(c, x0 + 3, f, z0 + 5, 118, 0); put(c, x0 + 3, f, z0 + 7, 117, 0);
        put(c, x0 + 3, floor + 4, z0 + 3, 30, 0); put(c, x0 + 4, floor + 4, z0 + 8, 30, 0);
        // The attic is a bedroom: a bed, a rug, a shelf, a lamp on the chimney breast.
        int a = floor + 6;
        put(c, x0 + 8, a, z0 + 5, 26, 3); put(c, x0 + 9, a, z0 + 5, 26, 11);
        for (int dx = 8; dx <= 9; dx++) for (int dz = 7; dz <= 8; dz++) put(c, x0 + dx, a, z0 + dz, 171, 12);
        put(c, x0 + 11, a, z0 + 5, 47, 0); put(c, x0 + 11, a + 1, z0 + 5, 47, 0);
        put(c, x0 + 11, a + 1, z0 + 9, 50, 4);
        put(c, x0 + 11, floor + 10, z0 + 6, 30, 0); put(c, x0 + 7, floor + 10, z0 + 9, 30, 0);
    }

    // == 2. The Brass Foundry ====================================================
    // Built around its chimney and never finished. Brick gone black at the flue, iron
    // plate riveted over the gaps, pipework on the outside where it was easier to reach,
    // and a boiler pit somebody should have drained before they left.

    private static final int[][] FOUNDRY_LOOT = {
        {158, 0, 16, 4, 14}, {151, 0, 10, 2, 6}, {331, 0, 14, 6, 20}, {263, 0, 13, 6, 18},
        {154, 0,  7, 1, 2},  { 33, 0,  7, 1, 3}, {328, 0,  6, 1, 2},  { 66, 0,  9, 6, 20},
        {406, 0,  9, 3, 9},  {145, 0,  4, 1, 1}, {152, 0,  5, 1, 2},  {218, 0, 5, 1, 3},
    };

    private static void foundry(World w, Chunk c, Terrain t) {
        Site s = site(t, c, FOUNDRY_CELL, 0x42524153L, 19, 15, 6, Megaliths.RANK_FOUNDRY);
        if (s == null) {
            // The cut round the walls reaches into neighbouring chunks (see apron()).
            Site n = siteNear(t, c, FOUNDRY_CELL, 0x42524153L, 19, 15, 6, Megaliths.RANK_FOUNDRY, 3);
            if (n != null) apron(c, n, 45, 0, 2, 0);
            return;
        }
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 19; dx++) for (int dz = 0; dz < 15; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            footing(c, s, wx, wz, 45, 0);
            put(c, wx, s.y - 1, wz, dx >= 12 && dz >= 9 ? 112 : 45, 0);   // soot by the furnace end
        }
        for (int dx = 0; dx < 19; dx++) for (int dz = 0; dz < 15; dz++) for (int dy = 0; dy < 14; dy++) {
            int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
            boolean wall = dx == 0 || dx == 18 || dz == 0 || dz == 14;
            boolean chimney = dx >= 14 && dx <= 16 && dz >= 10 && dz <= 12;
            if (chimney) {
                boolean core = dx == 15 && dz == 11;
                if (dy >= 14) continue;
                put(c, wx, wy, wz, core ? (dy < 2 ? 87 : 0) : (r.nextInt(9) == 0 ? 112 : 45), 0);
                if (core && dy == 2) put(c, wx, wy, wz, 51, 0);
                continue;
            }
            if (dy >= 9) { put(c, wx, wy, wz, 0, 0); continue; }
            // Plate is smooth stone double slab (43:8) rather than iron block (valuables policy).
            if (dy == 8) { put(c, wx, wy, wz, wall ? 43 : r.nextInt(7) == 0 ? 0 : 101, wall ? 8 : 0); continue; }
            if (wall) {
                // Windows every fourth block along each wall. The old test also used the wall's own
                // constant coordinate, which banded the whole east and south walls with bars; those
                // band cells are brick again (no draw, so the sequence after is unchanged).
                boolean band = dy >= 4 && dy <= 6 && (dx % 4 == 2 || dz % 4 == 2);
                boolean window = band && ((dx == 0 || dx == 18) ? dz % 4 == 2 : dx % 4 == 2);
                boolean gate = dz == 0 && dx >= 8 && dx <= 10 && dy <= 3;
                if (gate) { put(c, wx, wy, wz, 0, 0); continue; }
                if (window) { put(c, wx, wy, wz, 101, 0); continue; }
                if (band) { put(c, wx, wy, wz, 45, 0); continue; }
                // Plate riveted over brick, and a run of pipe along the outside.
                boolean pipe = dy == 3 && (dx == 0 || dx == 18);
                int plate = pipe ? 43 : r.nextInt(5) == 0 ? 43 : 45;             // same draws as before
                put(c, wx, wy, wz, plate, plate == 43 ? 8 : 0);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            // The boiler pit: two courses down, still hot. It used to be a flush magma inlay with
            // loose flowing lava in the walking floor; now the middle is sunk into a nether-brick
            // lined pit over lava and magma, and the magma apron round it carries an iron rail.
            boolean pit = dx >= 3 && dx <= 7 && dz >= 9 && dz <= 12;
            if (pit && dy == 0) {
                boolean hot = r.nextInt(4) == 0;                           // same draw as before
                boolean inner = dx >= 4 && dx <= 6 && dz >= 10 && dz <= 11;
                if (inner) {
                    put(c, wx, s.y - 1, wz, 0, 0);
                    put(c, wx, s.y - 2, wz, hot || dx == 5 ? 11 : 213, 0);
                    put(c, wx, s.y - 3, wz, 112, 0);
                } else {
                    put(c, wx, s.y - 1, wz, 213, 0);
                    put(c, wx, s.y - 2, wz, 112, 0);
                    put(c, wx, wy, wz, 101, 0);
                }
                continue;
            }
            // The great cog, stood on edge against the long wall: a brass ring on plate spokes,
            // with red teeth at the rim (it read as a striped ring round a loose dot).
            int cogx = dx - 9, cogy = dy - 4;
            double cog = Math.sqrt(cogx * cogx + cogy * cogy);
            if (dz == 13 && cog > 2.6 && cog < 3.7) { put(c, wx, wy, wz, 159, 4); continue; }
            if (dz == 13 && cog < 2.7 && (cogx == 0 || cogy == 0)) { put(c, wx, wy, wz, 43, 8); continue; }
            if (dz == 13 && ((Math.abs(cogx) == 4 && cogy == 0) || (cogx == 0 && cogy == -4)
                    || (Math.abs(cogx) == 3 && Math.abs(cogy) == 3))) { put(c, wx, wy, wz, 251, 14); continue; }
            // Quench tubs; the two at the casting bed hold water.
            if (dy == 0 && (dx == 2 || dx == 16) && dz % 5 == 2) { put(c, wx, wy, wz, 118, dx == 2 && dz < 10 ? 3 : 0); continue; }
            // The anvil stands on the floor and the torches hang on the north wall (the anvil was set a
            // block up and fell; the torches stood on air and popped off -- structure audit 2026-09-22).
            // The one over the gate has no wall to hang on and is left out.
            if (dy == 0 && dx == 12 && dz == 3) { put(c, wx, wy, wz, 145, 0); continue; }
            if (dy == 1 && dz == 1 && dx % 6 == 3 && dx != 9) { put(c, wx, wy, wz, 76, 3); continue; }
            if (dy == 0 && dx == 15 && dz == 4) graded(c, s, wx, wy, wz, r, FOUNDRY_LOOT, false);
            else if (dy == 0 && dx == 4 && dz == 3) graded(c, s, wx, wy, wz, r, FOUNDRY_LOOT, true);
            else if (dy == 0 && dx == 9 && dz == 7) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
            else if (dy == 0 && dx == 6 && dz == 6) mob(c, s, wx, wy, wz, r, EntityType.SPIDER);
        }
        // A working floor rather than an empty hall (structure audit 2026-09-23).
        int x0 = s.x, z0 = s.z, y0 = s.y;
        // The chimney's firebox shows through a grate, and the furnace bank stands against it.
        put(c, x0 + 14, y0 + 1, z0 + 11, 101, 0); put(c, x0 + 14, y0 + 2, z0 + 11, 101, 0);
        for (int dz = 10; dz <= 12; dz += 2) {
            put(c, x0 + 13, y0, z0 + dz, 61, 4);                 // facing the hall (a lit one goes out with no fuel)
            put(c, x0 + 13, y0 + 1, z0 + dz, 112, 0);
            put(c, x0 + 13, y0 + 2, z0 + dz, 154, 0);            // charging chute
        }
        // Casting bed: a line of moulds between the two water tubs on the west wall.
        for (int dz = 3; dz <= 6; dz++) put(c, x0 + 2, y0, z0 + dz, 44, 0);
        // Pattern bench by the door, next to the anvil.
        put(c, x0 + 12, y0, z0 + 1, 58, 0);
        // Coal and moulding sand heaped in the north-east corner.
        put(c, x0 + 17, y0, z0 + 1, 252, 15); put(c, x0 + 17, y0 + 1, z0 + 1, 252, 15);
        put(c, x0 + 17, y0, z0 + 2, 252, 15); put(c, x0 + 17, y0, z0 + 3, 12, 0); put(c, x0 + 16, y0, z0 + 1, 12, 0);
        // The hoist chain over the pit, hung from a grating bar that is always there.
        for (int y = 5; y <= 8; y++) put(c, x0 + 5, y0 + y, z0 + 10, 101, 0);
        // Where it was set into a slope the hill banked up against the walls over the windows:
        // a yard is cut round it at floor level, held back by a brick retaining wall.
        apron(c, s, 45, 0, 2, 0);
    }

    // == 3. Neon Arcology ========================================================
    // One slab, twenty-six storeys, built when somebody still had money. Dark panel and
    // glass, lit strips up every column, and enough of the upper floors open to the air
    // that getting to the top is the dangerous part.

    private static final int[][] ARCOLOGY_LOOT = {
        {331, 0, 15, 8, 24}, {264, 0,  8, 1, 4}, {368, 0,  9, 2, 5}, {348, 0, 12, 4, 14},
        {198, 0, 13, 4, 12}, {266, 0,  9, 2, 7}, {410, 0, 7, 1, 1}, {384, 0,  8, 3, 8},
        {152, 0,  6, 1, 3},  {169, 0,  6, 1, 4}, {347, 0,  5, 1, 1}, {388, 0,  7, 2, 6},
    };

    private static void arcology(World w, Chunk c, Terrain t) {
        Site s = site(t, c, ARCOLOGY_CELL, 0x4E454F4EL, 15, 15, 8, Megaliths.RANK_ARCOLOGY);
        if (s == null) return;
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 15; dz++) footing(c, s, s.x + dx, s.z + dz, 251, 15);
        int storeys = 9;
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 15; dz++) for (int dy = 0; dy < storeys * 3 + 4; dy++) {
            int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
            boolean wall = dx == 0 || dx == 14 || dz == 0 || dz == 14;
            boolean corner = (dx == 0 || dx == 14) && (dz == 0 || dz == 14);
            boolean deck = dy % 3 == 0;
            int storey = dy / 3;
            // The top three storeys are stripped back to the frame.
            boolean stripped = storey >= storeys - 3 && !corner && !deck;
            // The stair core (structure audit 2026-09-23): one continuous ladder at (5,7) from the
            // lobby to a roof hatch, hung on a black core wall at x 4, through every slab including
            // the stripped storeys. It used to be six 2-block stubs capped by the next slab.
            boolean core = dx == 4 && dz >= 6 && dz <= 8 && dy >= 1 && dy <= storeys * 3;
            boolean ladder = dx == 5 && dz == 7 && dy >= 1 && dy <= storeys * 3;
            if (dy >= storeys * 3) {
                // Roof: plant, rails and a mast.
                if (ladder) { put(c, wx, wy, wz, 65, 5); continue; }
                if (core) { put(c, wx, wy, wz, 251, 15); continue; }
                if (dy == storeys * 3 && !wall) { put(c, wx, wy, wz, 251, 7); continue; }
                // A black kerb at the roof edge and the rail stood on it (the bars used to sit flush in the slab).
                if (dy == storeys * 3 && wall) { put(c, wx, wy, wz, 251, 15); continue; }
                if (dy == storeys * 3 + 1 && wall) { put(c, wx, wy, wz, 101, 0); continue; }
                // The mast light sits on a nether brick post (a torch cannot stand on iron bars, and it popped off).
                if (dx == 7 && dz == 7) {
                    int top = storeys * 3 + 3;
                    put(c, wx, wy, wz, dy == top ? 76 : dy == top - 1 ? 113 : 101, dy == top ? 5 : 0);
                    continue;
                }
                put(c, wx, wy, wz, 0, 0);
                continue;
            }
            if (corner) { put(c, wx, wy, wz, dy % 3 == 1 ? 169 : 251, dy % 3 == 1 ? 0 : 15); continue; }
            if (ladder) { put(c, wx, wy, wz, 65, 5); continue; }
            if (core) { put(c, wx, wy, wz, 251, 15); continue; }
            if (deck) {
                // Floor plate, with a light well straight down the middle of the building; the well's
                // foot in the lobby is a lit floor rather than a hole. Lit lintels over the two doors.
                boolean well = dx >= 6 && dx <= 8 && dz >= 6 && dz <= 8;
                boolean lintel = dy == 3 && ((dz == 14 && dx >= 5 && dx <= 7) || (dx == 0 && dz >= 5 && dz <= 7));
                if (well) { put(c, wx, wy, wz, dy == 0 ? 169 : 0, 0); continue; }
                put(c, wx, wy, wz, lintel ? 169 : 251, lintel ? 0 : 7);
                continue;
            }
            if (wall) {
                // The lobby is entered through the curtain wall on the south and west faces (it was sealed).
                boolean door = storey == 0 && ((dz == 14 && dx >= 5 && dx <= 7) || (dx == 0 && dz >= 5 && dz <= 7));
                if (door) { put(c, wx, wy, wz, 0, 0); continue; }
                if (stripped) { put(c, wx, wy, wz, r.nextInt(3) == 0 ? 101 : 0, 0); continue; }
                // Curtain wall, lit from behind in two colours that alternate by storey. The mullion
                // rhythm runs along each face; it used to test the face's own coordinate, which left the
                // north and west faces solid black.
                boolean mullion = (dx == 0 || dx == 14) ? dz % 4 == 0 : dx % 4 == 0;
                if (mullion) { put(c, wx, wy, wz, 251, 15); continue; }
                put(c, wx, wy, wz, 95, storey % 2 == 0 ? 9 : 2);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            if (stripped) continue;
            // A rail round the light well on the enclosed storeys, open on the ladder side.
            boolean rail = dy % 3 == 1 && ((dx >= 6 && dx <= 9 && (dz == 5 || dz == 9)) || (dx == 9 && dz >= 6 && dz <= 8));
            if (rail) { put(c, wx, wy, wz, 101, 0); continue; }
            if (dy % 3 == 1 && (dx == 2 || dx == 12) && dz % 6 == 1) { put(c, wx, wy, wz, 169, 0); continue; }
            if (dy % 3 == 1 && storey % 3 == 1 && dx == 11 && dz == 3)
                graded(c, s, wx, wy, wz, r, ARCOLOGY_LOOT, storey >= 5);
            else if (dy % 3 == 1 && storey % 2 == 0 && dx == 3 && dz == 11)
                mob(c, s, wx, wy, wz, r, storey >= 6 ? EntityType.SKELETON : EntityType.ZOMBIE);
            else {
                int fit = arcologyFitting(storey, dx, dy % 3, dz);
                if (fit >= 0) put(c, wx, wy, wz, fit >> 4, fit & 15);
            }
        }
        // Roof plant: two air-handling boxes and a pair of tanks, and a hood over the ladder hatch.
        int roof = s.y + storeys * 3 + 1;
        for (int[] box : new int[][]{{10, 10}, {2, 10}}) for (int ax = 0; ax <= 1; ax++) for (int az = 0; az <= 1; az++) {
            put(c, s.x + box[0] + ax, roof, s.z + box[1] + az, 251, 15);
            put(c, s.x + box[0] + ax, roof + 1, s.z + box[1] + az, 151, 0);
        }
        put(c, s.x + 11, roof, s.z + 2, 118, 3); put(c, s.x + 12, roof, s.z + 2, 118, 3);
        for (int y = 0; y <= 1; y++) {
            put(c, s.x + 4, roof + y, s.z + 7, 251, 7);
            put(c, s.x + 5, roof + y, s.z + 6, 251, 7);
            put(c, s.x + 5, roof + y, s.z + 8, 251, 7);
        }
        for (int az = 6; az <= 8; az++) for (int ax = 4; ax <= 5; ax++) put(c, s.x + ax, roof + 2, s.z + az, 251, 7);
        // A black step outside the west door where the ground falls away, and steps where it banks up.
        for (int dz = 5; dz <= 7; dz++) {
            steps(c, s.x, s.z + dz, -1, 0, s.y + 1, 3, 114, 1);
            steps(c, s.x + dz, s.z + 14, 0, 1, s.y + 1, 3, 114, 2);
        }
    }

    /**
     * What each enclosed storey of the arcology was for (structure audit 2026-09-23): a lobby
     * with a security desk, two floors of capsule housing, a noodle-market floor and two office /
     * server floors, all in the tower's own black concrete, neon glass, bars and lanterns.
     * Returns (id << 4 | data) for the cell at feet level k = 1 or head level k = 2, or -1.
     */
    private static int arcologyFitting(int storey, int dx, int k, int dz) {
        switch (storey) {
            case 0:
                if (dz == 11 && dx >= 9 && dx <= 11) return k == 1 ? 251 << 4 | 15 : dx == 10 ? 160 << 4 | 9 : -1;
                if (dz == 12 && dx == 10 && k == 1) return 113 << 4;
                if (k == 1 && (dx == 1 || dx == 13) && dz == 13) return 140 << 4;
                return -1;
            case 1: case 2: {
                boolean pod = dx >= 4 && dx <= 10;
                if (pod && (dz == 1 || dz == 2 || dz == 12 || dz == 13)) {
                    if (dx % 2 == 1) return 251 << 4 | 7;                            // pod partitions
                    if (k != 1) return -1;
                    if (dz == 1) return 26 << 4 | 10;  if (dz == 2) return 26 << 4 | 2;   // beds, head to the wall
                    if (dz == 13) return 26 << 4 | 8;  return 26 << 4;
                }
                if (pod && dx % 2 == 0 && k == 1 && (dz == 3 || dz == 11) && !(dx == 10 && dz == 3))
                    return 171 << 4 | (storey == 1 ? 2 : 9);
                return -1;
            }
            case 3:
                if (k != 1) return -1;
                if ((dz == 3 || dz == 11) && dx >= 4 && dx <= 9 && !(dz == 11 && dx == 4)) return 251 << 4 | 15;   // stall counters
                if ((dz == 2 || dz == 12) && (dx == 5 || dx == 8)) return 118 << 4 | 3;                      // broth vats
                if ((dz == 4 || dz == 10) && (dx == 6 || dx == 9)) return 113 << 4;                           // stools
                return -1;
            case 4: case 5:
                if (dz == 2 && dx >= 4 && dx <= 10 && dx % 2 == 0) return k == 1 ? 251 << 4 | 15 : 151 << 4;  // desks and terminals
                if (dz == 3 && dx >= 4 && dx <= 8 && dx % 2 == 0 && k == 1) return 113 << 4;                 // stools
                if (dz == 13 && dx >= 4 && dx <= 10 && dx % 2 == 0) return dx == 8 && k == 2 ? 169 << 4 : 251 << 4 | 15;   // racks
                if (dz == 12 && dx >= 4 && dx <= 10 && dx % 2 == 0) return 101 << 4;                        // rack grilles
                return -1;
            default:
                return -1;
        }
    }

    // == 4. The Room That Repeats ================================================
    // A one-bed flat, chained shut from the inside. Directly beneath it, at the depth of a
    // cellar nobody dug, the same flat again: same walls, same window, same furniture in
    // the same places, and every surface of it gone wrong.

    private static final int[][] ROOM_LOOT = {
        {339, 0, 16, 3, 9},  {389, 0, 12, 1, 3}, {367, 0, 15, 3, 9}, {370, 0,  7, 1, 2},
        {368, 0,  8, 1, 3},  {345, 0,  7, 1, 1}, {347, 0,  7, 1, 1}, {358, 0,  6, 1, 1},
        {373, 0,  8, 1, 2},  {330, 0, 6, 1, 1}, {96, 0, 10, 2, 6}, {352, 0, 10, 2, 7},
    };

    private static void theRoom(World w, Chunk c, Terrain t) {
        Site s = site(t, c, ROOM_CELL, 0x524F4F4DL, 13, 11, 5, Megaliths.RANK_ROOM);
        if (s == null) return;
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 11; dz++) footing(c, s, s.x + dx, s.z + dz, 98, 0);
        // Twice: once at the surface as it should be, once far below as it should not.
        for (int copy = 0; copy < 2; copy++) {
            boolean wrong = copy == 1;
            int base = wrong ? s.y - 24 : s.y;
            if (base < 6) continue;
            Random rr = new Random(s.seed + copy * 7717L);
            for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 11; dz++) for (int dy = -1; dy < 7; dy++) {
                int wx = s.x + dx, wy = base + dy, wz = s.z + dz;
                boolean wall = dx == 0 || dx == 12 || dz == 0 || dz == 10;
                boolean bath = dx >= 9 && dz >= 7;                    // the small room off the back
                // The hole in the bathroom floor, and the ladder that runs from the copy below up
                // through it (structure audit 2026-09-23: the hole was never cut, so the copies never met).
                boolean shaft = dx == 11 && dz == 9;
                if (shaft && (dy == -1 ? !wrong : dy == 6 && wrong)) { put(c, wx, wy, wz, 65, 2); continue; }
                if (dy == -1 && !wrong && dx == 10 && dz == 9) { put(c, wx, wy, wz, 98, 2); continue; }   // the broken edge
                // The copy below has the same floor, stained here and there with soul sand.
                if (dy == -1) { put(c, wx, wy, wz, wrong ? (roll(s, dx, copy, dz, 7) == 0 ? 88 : 159) : 5, wrong ? (roll(s, dx, copy, dz, 7) == 0 ? 0 : 15) : 1); continue; }
                if (dy == 6) { put(c, wx, wy, wz, wrong ? 98 : 159, wrong ? 2 : 0); continue; }
                if (wall) {
                    boolean window = dz == 0 && dy >= 2 && dy <= 4 && dx >= 3 && dx <= 5;
                    boolean door = dz == 10 && dx == 6 && dy <= 2;
                    if (window) { put(c, wx, wy, wz, wrong ? 101 : 102, 0); continue; }
                    // Chains across the door, from the inside, in both copies.
                    if (door) { put(c, wx, wy, wz, dy == 1 ? 101 : 0, 0); continue; }
                    put(c, wx, wy, wz, 159, wrong ? (rr.nextInt(6) == 0 ? 14 : 12) : (rr.nextInt(7) == 0 ? 8 : 0));
                    continue;
                }
                // The bathroom is closed off from the flat, with its doorway at x 11.
                if (bath && dx == 9 && dy <= 5 && dy >= 0) { put(c, wx, wy, wz, 159, wrong ? 12 : 0); continue; }
                if (dx == 10 && dz == 6 && dy >= 0 && dy <= 5) { put(c, wx, wy, wz, 159, wrong ? 12 : 0); continue; }
                put(c, wx, wy, wz, 0, 0);
                // The hole in the bathroom floor is how the two copies meet: the ladder hangs on the
                // back wall (data 2; it used to face open floor and hold on to nothing).
                if (shaft) {
                    if (wrong) put(c, wx, wy, wz, 65, 2);
                    continue;
                }
                // Webs in the copy below, strung from the walls (loose in mid-air they read as debris).
                if (wrong && rr.nextInt(13) == 0 && dy <= 2 && (dx == 1 || dx == 11 || dz == 1 || dz == 9)) { put(c, wx, wy, wz, 30, 0); continue; }
                // The same furniture in the same places in both copies: a bed (it was two stone
                // slabs), a kitchenette, a sofa facing a television, a rug and a bath.
                if (dy == 0 && dx == 3 && dz == 3) { put(c, wx, wy, wz, 26, 3); continue; }
                if (dy == 0 && dx == 4 && dz == 3) { put(c, wx, wy, wz, 26, 11); continue; }
                if (dy == 0 && dx == 7 && dz == 6) { put(c, wx, wy, wz, 58, 0); continue; }
                if (dy == 0 && dx == 2 && dz == 8) { put(c, wx, wy, wz, 47, 0); continue; }
                if (dy == 0 && dx == 1 && dz == 4) { put(c, wx, wy, wz, 126, 9); continue; }
                if (dy == 0 && dx == 1 && dz == 5) { put(c, wx, wy, wz, 61, 5); continue; }
                if (dy == 0 && dx == 1 && dz == 6) { put(c, wx, wy, wz, 118, wrong ? 0 : 3); continue; }
                if (dy == 0 && dx == 5 && (dz == 7 || dz == 8)) { put(c, wx, wy, wz, 134, 1); continue; }
                if (dy == 0 && dx == 8 && dz == 8) { put(c, wx, wy, wz, 126, 1); continue; }
                if (dy == 1 && dx == 8 && dz == 8) { put(c, wx, wy, wz, 159, 15); continue; }
                if (dy == 0 && (dx == 6 || dx == 7) && (dz == 7 || dz == 8)) { put(c, wx, wy, wz, 171, wrong ? 14 : 12); continue; }
                if (dy == 0 && dx == 10 && dz == 8) { put(c, wx, wy, wz, 118, wrong ? 0 : 3); continue; }
                // The lamp is on the wall behind the bed (standing on air a block up, it popped off).
                if (dy == 1 && dx == 6 && dz == 1) { put(c, wx, wy, wz, wrong ? 76 : 50, 3); continue; }
                // A second lamp by the sofa and one in the bathroom (the flat was half in the dark);
                // they are hung once the walls behind them stand, below.
                if (dy == 1 && ((dx == 3 && dz == 9) || (dx == 11 && dz == 7))) continue;
                if (dy == 0 && dx == 8 && dz == 2) graded(c, s, wx, wy, wz, r, ROOM_LOOT, wrong);
                else if (wrong && dy == 0 && dx == 3 && dz == 6) mob(c, s, wx, wy, wz, rr, EntityType.ZOMBIE);
                else if (wrong && dy == 0 && dx == 10 && dz == 4) mob(c, s, wx, wy, wz, rr, EntityType.CAVE_SPIDER);
            }
        }
        for (int copy = 0; copy < 2; copy++) {
            int base = copy == 1 ? s.y - 24 : s.y;
            if (base < 6) continue;
            put(c, s.x + 3, base + 1, s.z + 9, copy == 1 ? 76 : 50, 4);
            put(c, s.x + 11, base + 1, s.z + 7, copy == 1 ? 76 : 50, 2);
        }
        // (structure audit 2026-09-23) The shaft between the copies: the ladder carries on through
        // the rock from the lower ceiling to the upper floor, in a stone-brick lining.
        int lower = s.y - 24;
        if (lower >= 6) {
            for (int y = lower + 7; y <= s.y - 2; y++) {
                put(c, s.x + 11, y, s.z + 9, 65, 2);
                put(c, s.x + 10, y, s.z + 9, 98, roll(s, 10, y, 9, 4) == 0 ? 2 : 0);
                put(c, s.x + 12, y, s.z + 9, 98, roll(s, 12, y, 9, 4) == 0 ? 2 : 0);
                put(c, s.x + 11, y, s.z + 8, 98, roll(s, 11, y, 8, 4) == 0 ? 2 : 0);
                put(c, s.x + 11, y, s.z + 10, 98, 0);
            }
        }
        // The way in, as in the flat it copies: a hole broken through the bathroom wall, rough
        // masonry showing round it. The copy below has the same hole, into rock.
        for (int copy = 0; copy < 2; copy++) {
            int base = copy == 1 ? lower : s.y;
            if (base < 6) continue;
            int edge = copy == 1 ? 159 : 98, edgeData = copy == 1 ? 12 : 2;
            put(c, s.x + 12, base, s.z + 8, 0, 0); put(c, s.x + 12, base + 1, s.z + 8, 0, 0);
            put(c, s.x + 12, base + 2, s.z + 8, edge, edgeData);
            put(c, s.x + 12, base, s.z + 7, edge, edgeData); put(c, s.x + 12, base + 1, s.z + 9, edge, edgeData);
        }
        // Outside the hole: a stone-brick threshold, and steps cut up the bank where the flat sits
        // in a hillside (it was buried to the eaves on the uphill sides).
        for (int dx = 13; dx <= 14; dx++) {
            footing(c, s, s.x + dx, s.z + 8, 98, 0);
            clearAbove(c, s.x + dx, s.z + 8, s.y, s.y + 3);
            steps(c, s.x + dx, s.z + 8, 0, -1, s.y, 8, 109, 3);
        }
        // And a flight down the bank to the chained front door, which the hill had buried.
        for (int dx = 5; dx <= 7; dx++) steps(c, s.x + dx, s.z + 10, 0, 1, s.y, 6, 109, 2);
    }


    // == 5. Spencer Manor ========================================================
    // A country house with money behind it: black and white stone in the entrance hall,
    // a staircase that splits and returns, dark panelling, and a great deal of the ground
    // floor given over to rooms nobody needed.

    private static final int[][] MANOR_LOOT = {
        {388, 0, 13, 3, 9},  {266, 0, 14, 3, 10}, {373, 0, 10, 1, 3}, {282, 0, 9, 1, 1},
        {264, 0,  7, 1, 4},  {322, 0,  8, 1, 2},  {321, 0,  7, 1, 2}, {358, 0,  6, 1, 1},
        {340, 0, 10, 2, 5},  {384, 0,  8, 2, 7},  {421, 0, 11, 3, 9}, {35,  14, 8, 3, 9},
    };

    private static void manor(World w, Chunk c, Terrain t) {
        Site s = site(t, c, MANOR_CELL, 0x4D414E4F52L, 21, 17, 6, Megaliths.RANK_MANOR);
        if (s == null) {
            Site n = siteNear(t, c, MANOR_CELL, 0x4D414E4F52L, 21, 17, 6, Megaliths.RANK_MANOR, 4);
            if (n != null) manorGrounds(c, n);
            return;
        }
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 21; dx++) for (int dz = 0; dz < 17; dz++) footing(c, s, s.x + dx, s.z + dz, 98, 0);
        for (int dx = 0; dx < 21; dx++) for (int dz = 0; dz < 17; dz++) for (int dy = 0; dy < 13; dy++) {
            int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
            boolean wall = dx == 0 || dx == 20 || dz == 0 || dz == 16;
            boolean hall = dx >= 6 && dx <= 14 && dz >= 2 && dz <= 12;
            if (dy == 0) {
                // Chequered stone through the hall, boards everywhere else.
                if (hall) { put(c, wx, wy, wz, 159, ((dx + dz) & 1) == 0 ? 15 : 0); continue; }
                put(c, wx, wy, wz, 5, 5);
                continue;
            }
            if (dy == 12) { put(c, wx, wy, wz, wall ? 45 : 53, 0); continue; }
            if (dy == 6 && !hall) { put(c, wx, wy, wz, 5, 5); continue; }     // first floor, not over the hall
            if (wall) {
                // Windows on both storeys (the first floor had none).
                boolean window = (dy >= 2 && dy <= 4 || dy >= 8 && dy <= 10) && (dx % 5 == 2 || dz % 5 == 2);
                // One panelled front door between panelled posts (it was a bare 3x4 hole).
                boolean door = dz == 16 && dx == 10 && dy >= 1 && dy <= 2;
                if (door) { put(c, wx, wy, wz, 197, dy == 1 ? 3 : 8); continue; }
                if (window) { put(c, wx, wy, wz, 102, 0); continue; }
                // Panelled below the rail, rendered above it.
                put(c, wx, wy, wz, dy <= 3 ? 5 : 159, dy <= 3 ? 5 : 0);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            if (hall) {
                // The stair: three wide up the middle on a panelled mass, six steps so the top one
                // meets the gallery flush (it was a one-wide run of floating steps, a step short).
                if (dx >= 9 && dx <= 11 && dz >= 3 && dz <= 8) {
                    if (dy == dz - 2) { put(c, wx, wy, wz, 53, 2); continue; }
                    if (dy >= 1 && dy < dz - 2) { put(c, wx, wy, wz, 5, 5); continue; }
                }
                // The first floor over the back of the hall is a gallery; the double-height part in
                // front of it is railed along every edge, at rail height rather than in the floor.
                if (dy == 6 && (dz >= 9 || dz == 2 || (dz == 8 && (dx <= 8 || dx >= 12)))) { put(c, wx, wy, wz, 5, 5); continue; }
                if (dy == 7 && (dz == 2 || (dz == 8 && (dx <= 8 || dx >= 12)))) { put(c, wx, wy, wz, 85, 0); continue; }
                // The chandelier hangs from the roof on a chain, low enough to light the floor.
                if (dx == 10 && dz == 7 && dy >= 8 && dy <= 11) { put(c, wx, wy, wz, dy == 9 ? 89 : 85, 0); continue; }
                if (dy == 1 && dz == 11 && (dx == 8 || dx == 12)) { put(c, wx, wy, wz, 171, 14); continue; }
                continue;
            }
            // Panelled partitions make the wings into rooms rather than one open floor: the hall
            // walls, and (structure audit 2026-09-23) cross walls at dz 4 and 10, all with doors.
            boolean partition = (dx == 5 || dx == 15) && dz != 8;
            if (partition && dy <= 5) {
                boolean door = dz == 5 && dy >= 1 && dy <= 2;
                put(c, wx, wy, wz, door ? 197 : 5, door ? (dy == 1 ? 0 : 8) : 5);
                continue;
            }
            boolean cross = (dz == 4 || dz == 10) && (dx <= 4 || dx >= 16) && dy <= 5;
            if (cross) {
                boolean door = (dx == 3 || dx == 17) && dy >= 1 && dy <= 2;
                put(c, wx, wy, wz, door ? 197 : 5, door ? (dy == 1 ? 1 : 8) : 5);
                continue;
            }
            if (dy == 1 && (dx == 2 || dx == 18) && dz % 4 == 1) { put(c, wx, wy, wz, 47, 0); continue; }
            // Wall torches face into the room from the side walls (placed standing on air, they popped off
            // -- structure audit 2026-09-22). The east ones go up after the sweep, once their wall stands.
            if (dy == 3 && (dx == 1 || dx == 19) && dz % 6 == 3) { if (dx == 1) put(c, wx, wy, wz, 50, 1); continue; }
            if (dy == 1 && dx == 2 && dz == 14) graded(c, s, wx, wy, wz, r, MANOR_LOOT, false);
            else if (dy == 7 && dx == 18 && dz == 3) graded(c, s, wx, wy, wz, r, MANOR_LOOT, true);
            else if (dy == 1 && dx == 17 && dz == 13) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
            else if (dy == 7 && dx == 3 && dz == 5) mob(c, s, wx, wy, wz, r, EntityType.SPIDER);
        }
        for (int dz = 3; dz < 17; dz += 6) put(c, s.x + 19, s.y + 3, s.z + dz, 50, 2);
        manorFitOut(c, s);
        manorGrounds(c, s);
    }

    /**
     * The manor's grounds (structure audit 2026-09-23): trees had grown up against every wall and
     * over the windows, and one stood two blocks in front of the door. They are cleared for two
     * blocks round the house and in a forecourt before the door, which gets a stone-brick path
     * and a step up to the threshold where the floor stands above the ground.
     */
    private static void manorGrounds(Chunk c, Site s) {
        clearGrowth(c, s, 2, s.y + 20);
        for (int dx = 8; dx <= 12; dx++) for (int dz = 17; dz <= 20; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            clearGrowth(c, wx, wz, s.y - 1, s.y + 20);
            if (dx < 9 || dx > 11) continue;
            int g = ground(c, wx, wz, s.y + 3, s.y - 4);
            if (g == NONE || g < s.y - 3) continue;
            if (dz == 17 && g == s.y - 1) { put(c, wx, s.y, wz, 109, 3); continue; }   // the step up
            if (g <= s.y) put(c, wx, g, wz, 98, roll(s, dx, 0, dz, 5) == 0 ? 2 : 0);   // the path
        }
    }

    /** Clears trees and undergrowth (not ground) from every column within `ring` blocks outside a footprint. */
    private static void clearGrowth(Chunk c, Site s, int ring, int yTop) {
        for (int dx = -ring; dx < s.sizeX + ring; dx++) for (int dz = -ring; dz < s.sizeZ + ring; dz++) {
            if (dx >= 0 && dx < s.sizeX && dz >= 0 && dz < s.sizeZ) continue;
            clearGrowth(c, s.x + dx, s.z + dz, s.y - 1, yTop);
        }
    }

    /** Removes logs, leaves, vines and tall plants from one column between y0 and y1. */
    private static void clearGrowth(Chunk c, int wx, int wz, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            Block b = blockAt(c, wx, y, wz);
            if (b == null) return;
            int id = b.getTypeId();
            if (id == 17 || id == 18 || id == 161 || id == 162 || id == 106 || id == 31 || id == 32 || id == 175)
                put(c, wx, y, wz, 0, 0);
        }
    }

    /**
     * A cut round a landmark that was set into a slope (structure audit 2026-09-23): wherever the
     * ground within two blocks of its walls stands above its floor, that ground is dug out to floor
     * level and surfaced, trees on it included, and the third ring out is faced as a retaining wall
     * up to the ground behind. Level or lower ground is left alone. Runs for the neighbouring
     * chunks too (through siteNear), so the cut is whole on every side.
     */
    private static void apron(Chunk c, Site s, int wallId, int wallData, int floorId, int floorData) {
        for (int dx = -3; dx < s.sizeX + 3; dx++) for (int dz = -3; dz < s.sizeZ + 3; dz++) {
            int out = Math.max(Math.max(-dx, dx - (s.sizeX - 1)), Math.max(-dz, dz - (s.sizeZ - 1)));
            if (out <= 0) continue;
            int wx = s.x + dx, wz = s.z + dz;
            int g = ground(c, wx, wz, s.y + 16, s.y - 2);
            if (g == NONE || g < s.y) continue;
            if (out == 3) {
                for (int y = s.y; y <= g; y++) put(c, wx, y, wz, wallId, wallData);
                continue;
            }
            clearAbove(c, wx, wz, s.y, s.y + 24);
            put(c, wx, s.y - 1, wz, floorId, floorData);
            // Where the bank was an overhang, pack under the new floor down to the ground.
            for (int y = s.y - 2; y >= s.y - 10 && !solidAt(c, wx, y, wz); y--) put(c, wx, y, wz, wallId, wallData);
        }
    }

    /**
     * Spencer Manor's rooms (structure audit 2026-09-23). Ground floor wings: library, dining
     * room and store on the west; study, drawing room and the shut room with the zombies on the
     * east. First floor: two bedrooms each side, the study round the upper chest, a gallery over
     * the back of the hall, windows and wall lights. The spider's bedroom is left unlit.
     */
    private static void manorFitOut(Chunk c, Site s) {
        int x0 = s.x, y1 = s.y + 1, y7 = s.y + 7, z0 = s.z;
        // Hall: lights on the partition faces. The east partition is the first column of the next
        // chunk, so a torch hung on it from this side can be placed before it exists and drop:
        // those two are lamps set into the panelling instead.
        for (int dz = 3; dz <= 11; dz += 8) { put(c, x0 + 6, s.y + 3, z0 + dz, 50, 1); put(c, x0 + 15, s.y + 3, z0 + dz, 89, 0); }
        // West ground floor: library (shelved wall, armchair, rug), dining room, store.
        for (int dz = 1; dz <= 2; dz++) for (int y = 0; y <= 1; y++) put(c, x0 + 1, y1 + y, z0 + dz, 47, 0);
        put(c, x0 + 3, y1, z0 + 2, 53, 0); put(c, x0 + 2, y1, z0 + 3, 171, 14); put(c, x0 + 3, y1, z0 + 3, 171, 14);
        put(c, x0 + 3, y1, z0 + 7, 85, 0); put(c, x0 + 3, y1 + 1, z0 + 7, 72, 0);
        put(c, x0 + 3, y1, z0 + 6, 53, 3); put(c, x0 + 3, y1, z0 + 8, 53, 2);
        put(c, x0 + 1, y1, z0 + 15, 17, 1); put(c, x0 + 1, y1 + 1, z0 + 15, 17, 1); put(c, x0 + 1, y1, z0 + 12, 17, 1);
        // East ground floor: study (desk and chair), drawing room (table and two chairs), shut room.
        put(c, x0 + 18, y1, z0 + 2, 126, 13); put(c, x0 + 18, y1, z0 + 3, 53, 2);
        put(c, x0 + 17, y1, z0 + 7, 85, 0); put(c, x0 + 17, y1 + 1, z0 + 7, 72, 0);
        put(c, x0 + 17, y1, z0 + 6, 53, 3); put(c, x0 + 17, y1, z0 + 8, 53, 2);
        put(c, x0 + 16, y1, z0 + 7, 171, 14); put(c, x0 + 18, y1, z0 + 7, 171, 14);
        put(c, x0 + 19, s.y + 4, z0 + 15, 30, 0); put(c, x0 + 16, s.y + 5, z0 + 11, 30, 0);
        // First floor: panelled walls between the wings and the hall, and across each wing.
        for (int y = 0; y <= 4; y++) {
            for (int dz = 1; dz <= 15; dz++) for (int dx = 5; dx <= 15; dx += 10) {
                boolean door = (dz == 1 || dz == 11) && y <= 1;
                put(c, x0 + dx, y7 + y, z0 + dz, door ? 197 : 5, door ? (y == 0 ? 0 : 8) : 5);
            }
            // Cross walls between the front and back rooms of each wing, each with a door (without
            // one the whole north half of the floor could not be reached from the gallery).
            for (int dx = 1; dx <= 19; dx++) if (dx <= 4 || dx >= 16) {
                boolean door = (dx == 3 || dx == 17) && y <= 1;
                put(c, x0 + dx, y7 + y, z0 + 8, door ? 197 : 5, door ? (y == 0 ? 1 : 8) : 5);
            }
        }
        // North-west bedroom (the spider's; nobody sleeps here now).
        put(c, x0 + 2, y7, z0 + 2, 26, 1); put(c, x0 + 1, y7, z0 + 2, 26, 9);
        put(c, x0 + 1, y7, z0 + 1, 47, 0); put(c, x0 + 1, y7 + 1, z0 + 1, 47, 0);
        put(c, x0 + 2, y7, z0 + 4, 171, 14); put(c, x0 + 1, y7, z0 + 4, 171, 14);
        put(c, x0 + 4, y7 + 4, z0 + 7, 30, 0); put(c, x0 + 1, y7 + 4, z0 + 6, 30, 0);
        // South-west bedroom.
        put(c, x0 + 2, y7, z0 + 14, 26, 1); put(c, x0 + 1, y7, z0 + 14, 26, 9);
        put(c, x0 + 1, y7, z0 + 15, 47, 0); put(c, x0 + 1, y7 + 1, z0 + 15, 47, 0);
        put(c, x0 + 2, y7, z0 + 12, 171, 14); put(c, x0 + 3, y7, z0 + 12, 171, 14);
        put(c, x0 + 1, y7 + 2, z0 + 11, 50, 1);
        // North-east study round the chest: shelves, a desk and chair.
        for (int dz = 5; dz <= 6; dz++) for (int y = 0; y <= 1; y++) put(c, x0 + 19, y7 + y, z0 + dz, 47, 0);
        put(c, x0 + 17, y7, z0 + 5, 126, 13); put(c, x0 + 17, y7, z0 + 6, 53, 2);
        put(c, x0 + 19, y7 + 2, z0 + 4, 50, 2);
        // South-east drawing room.
        put(c, x0 + 18, y7, z0 + 12, 85, 0); put(c, x0 + 18, y7 + 1, z0 + 12, 72, 0);
        put(c, x0 + 17, y7, z0 + 12, 53, 1); put(c, x0 + 19, y7, z0 + 12, 53, 0);
        for (int dx = 17; dx <= 19; dx++) put(c, x0 + dx, y7, z0 + 14, 171, 14);
        put(c, x0 + 19, y7 + 2, z0 + 11, 50, 2);
        // Gallery and north corridor: a runner and lights.
        for (int dx = 7; dx <= 13; dx++) put(c, x0 + dx, y7, z0 + 13, 171, 14);
        put(c, x0 + 6, y7 + 2, z0 + 15, 50, 4); put(c, x0 + 14, y7 + 2, z0 + 15, 50, 4);
        put(c, x0 + 10, y7 + 2, z0 + 1, 50, 3);
    }

    // == 6. The Precinct =========================================================
    // A civic building with columns across the front, pressed into use as something else:
    // desks pushed against the windows, a holding wing at the back, and a hall that still
    // has the fountain in it.

    private static final int[][] PRECINCT_LOOT = {
        {416, 0, 15, 5, 14}, {262, 0, 14, 12, 40}, {442, 0,  8, 1, 1}, {267, 0,  9, 1, 1},
        {306, 0,  7, 1, 1},  {307, 0,  7, 1, 1},   {308, 0,  7, 1, 1}, {309, 0,  7, 1, 1},
        {289, 0, 11, 4, 12}, {339, 0, 10, 3, 9},   {320, 0,  9, 2, 6}, {334, 0, 8, 2, 6},
    };

    private static void precinct(World w, Chunk c, Terrain t) {
        Site s = site(t, c, PRECINCT_CELL, 0x50524543L, 21, 17, 6, Megaliths.RANK_PRECINCT);
        if (s == null) return;
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 21; dx++) for (int dz = 0; dz < 17; dz++) footing(c, s, s.x + dx, s.z + dz, 98, 0);
        for (int dx = 0; dx < 21; dx++) for (int dz = 0; dz < 17; dz++) for (int dy = 0; dy < 14; dy++) {
            int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
            boolean portico = dz <= 2;                              // the columned front
            boolean wall = dx == 0 || dx == 20 || dz == 3 || dz == 16;
            boolean cells = dz >= 12 && dx >= 2 && dx <= 18;
            if (dy == 0) { put(c, wx, wy, wz, portico ? 155 : 98, portico ? 0 : 0); continue; }
            if (portico) {
                boolean column = dz == 1 && dx % 4 == 2 && dx >= 2 && dx <= 18;
                if (column && dy <= 8) { put(c, wx, wy, wz, 155, dy == 8 ? 1 : 2); continue; }
                if (dy == 9 && dz <= 2) { put(c, wx, wy, wz, 155, 1); continue; }   // entablature
                // A low stepped pediment over the middle bays (it was one flat row of slabs).
                int half = Math.abs(dx - 10);
                if (dz >= 1 && dy >= 10 && dy <= 12) {
                    int reach = dy == 10 ? 6 : dy == 11 ? 3 : 1;
                    if (half <= reach) { put(c, wx, wy, wz, 155, 1); continue; }
                    if (half == reach + 1) { put(c, wx, wy, wz, 156, dx < 10 ? 0 : 1); continue; }
                }
                if (dy == 10 && dz == 1) { put(c, wx, wy, wz, 44, 7); continue; }   // cornice
                put(c, wx, wy, wz, 0, 0);
                continue;
            }
            if (dy >= 11) { put(c, wx, wy, wz, dy == 11 ? 98 : 0, 0); continue; }
            if (wall) {
                boolean window = dy >= 3 && dy <= 5 && dx % 4 == 2 && dz == 3;
                // Boarded windows down both sides too, over the desks, and barred windows high in
                // the back of the holding wing (the side and back walls were blank). Same draws.
                boolean side = dy >= 3 && dy <= 5 && (dx == 0 || dx == 20) && (dz == 5 || dz == 9);
                boolean barred = dy >= 3 && dy <= 4 && dz == 16 && dx % 3 == 0 && dx >= 3 && dx <= 18;
                boolean door = dz == 3 && dx >= 9 && dx <= 11 && dy <= 3;
                if (door) { put(c, wx, wy, wz, 0, 0); continue; }
                // Windows boarded from the inside by whoever was last holding it.
                if (window || side) { put(c, wx, wy, wz, r.nextInt(3) == 0 ? 102 : 5, 0); continue; }
                int fleck = r.nextInt(8);
                put(c, wx, wy, wz, barred ? 101 : fleck == 0 ? 155 : 98, 0);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            // The fountain, dry on one side. Its centre is a block further in (the rim used to lie
            // across the doorway); the pillar stands on the basin floor, and a slab kerb along the
            // middle holds the water on its own half.
            int fx = dx - 10, fz = dz - 8;
            double f = Math.sqrt(fx * fx + fz * fz);
            if (dy >= 1 && dy <= 3 && f < 0.7) { put(c, wx, wy, wz, 155, 1); continue; }
            if (dy == 1 && f < 3.2) {
                if (f < 2.3 && fz == 0) { put(c, wx, wy, wz, 44, 7); continue; }
                put(c, wx, wy, wz, f < 2.3 ? (fz > 0 ? 9 : 0) : 155, 0);
                continue;
            }
            if (cells) {
                // The west strip joins the first cell (it was a dead-end alley behind the bars).
                boolean bars = (dx % 3 == 2 && !(dx == 2 && dz > 12)) || dz == 12;
                if (bars && dy <= 4) { put(c, wx, wy, wz, dz == 12 && dx % 3 == 0 ? 0 : 101, 0); continue; }
                if (dy == 1 && dx % 3 == 1 && dz == 14) { put(c, wx, wy, wz, 44, 0); continue; }
                if (dy == 1 && dx % 3 == 0 && dz == 15) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
                continue;
            }
            // Wall torches face into the room from the side walls (placed standing on air, they popped off
            // -- structure audit 2026-09-22). The east ones go up after the sweep, once their wall stands.
            if (dy == 4 && (dx == 1 || dx == 19) && dz % 5 == 2) { if (dx == 1) put(c, wx, wy, wz, 50, 1); continue; }
            if (dy == 1 && dx == 4 && dz == 5) graded(c, s, wx, wy, wz, r, PRECINCT_LOOT, true);
            else if (dy == 1 && dx == 16 && dz == 9) graded(c, s, wx, wy, wz, r, PRECINCT_LOOT, false);
            else if (dy == 1 && dx == 7 && dz == 10) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
        }
        for (int dz = 7; dz <= 12; dz += 5) put(c, s.x + 19, s.y + 4, s.z + dz, 50, 2);
        precinctFitOut(c, s);
    }

    /**
     * The precinct as a police hall (structure audit 2026-09-23): desks under the side windows
     * (they were loose floor slabs), a reception counter inside the door, records along the front,
     * an armoury cage round the weapon chest, more light, the end cell closed, and steps up to
     * the portico wherever the ground has banked above it.
     */
    private static void precinctFitOut(Chunk c, Site s) {
        int x0 = s.x, y1 = s.y + 1, z0 = s.z;
        // Desks: a plank cabinet against the wall, a smooth-stone top, a chair.
        put(c, x0 + 1, y1, z0 + 9, 5, 0); put(c, x0 + 2, y1, z0 + 9, 44, 8); put(c, x0 + 3, y1, z0 + 9, 53, 0);
        put(c, x0 + 19, y1, z0 + 9, 5, 0); put(c, x0 + 18, y1, z0 + 9, 44, 8); put(c, x0 + 17, y1, z0 + 9, 53, 1);
        // Reception: an L of stone brick under quartz slabs in the north-east corner, a stool behind it.
        for (int[] p : new int[][]{{14, 4}, {14, 5}, {14, 6}, {15, 6}, {16, 6}, {17, 6}}) {
            put(c, x0 + p[0], y1, z0 + p[1], 98, 0); put(c, x0 + p[0], y1 + 1, z0 + p[1], 44, 15);
        }
        put(c, x0 + 16, y1, z0 + 4, 53, 3);
        // Records: shelving along the front wall either side of the door.
        for (int dx : new int[]{6, 7, 13}) for (int y = 0; y <= 1; y++) put(c, x0 + dx, y1 + y, z0 + 4, 47, 0);
        // Armoury: a barred cage round the weapon chest in the north-west corner, gate gap at dz 5.
        for (int y = 0; y <= 2; y++) {
            for (int dx = 1; dx <= 5; dx++) put(c, x0 + dx, y1 + y, z0 + 7, 101, 0);
            put(c, x0 + 5, y1 + y, z0 + 4, 101, 0); put(c, x0 + 5, y1 + y, z0 + 6, 101, 0);
        }
        put(c, x0 + 1, y1, z0 + 4, 145, 1);                                            // armourer's anvil
        // Front-wall lights and a light over the cell block.
        put(c, x0 + 4, s.y + 4, z0 + 4, 50, 3); put(c, x0 + 16, s.y + 4, z0 + 4, 50, 3);
        put(c, x0 + 6, s.y + 6, z0 + 15, 50, 4); put(c, x0 + 14, s.y + 6, z0 + 15, 50, 4);
        // The east cell gets its front and its bench; the widened west cell its front.
        for (int y = 0; y <= 3; y++) { put(c, x0 + 19, y1 + y, z0 + 12, 101, 0); put(c, x0 + 1, y1 + y, z0 + 12, 101, 0); }
        put(c, x0 + 19, y1, z0 + 14, 44, 0);
        // Steps up to the portico where sand or earth has banked above its floor.
        for (int dx = 0; dx <= 20; dx++) steps(c, x0 + dx, z0, 0, -1, y1, 2, 156, 3);
        for (int dz = 0; dz <= 2; dz++) { steps(c, x0, z0 + dz, -1, 0, y1, 2, 156, 1); steps(c, x0 + 20, z0 + dz, 1, 0, y1, 2, 156, 0); }
    }

    // == 7. Raccoon Street =======================================================
    // One block of a road that stopped being a road. Cars pushed across it and burned
    // where they stood, shopfronts open to the weather, a service bus dragged over as a
    // barricade, and a manhole somebody left open getting out.

    private static final int[][] STREET_LOOT = {
        {297, 0, 14, 3, 8},  {320, 0, 11, 2, 6}, {289, 0, 13, 4, 14}, {319, 0, 12, 3, 10},
        {339, 0, 12, 4, 12}, {262, 0, 11, 8, 26}, {373, 0,  8, 1, 2}, {350, 0, 11, 4, 14},
        {354, 0,  6, 1, 1},  {345, 0,  6, 1, 1},  {322, 0,  6, 1, 1}, {301, 0, 9, 4, 12},
    };

    private static void street(World w, Chunk c, Terrain t) {
        Site s = site(t, c, STREET_CELL, 0x53545254L, 25, 13, 5, Megaliths.RANK_STREET);
        if (s == null) {
            // The back alley and the road-end cuts reach into neighbouring chunks (see apron()).
            Site n = siteNear(t, c, STREET_CELL, 0x53545254L, 25, 13, 5, Megaliths.RANK_STREET, 3);
            if (n != null) apron(c, n, 98, 0, 13, 0);
            return;
        }
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 25; dx++) for (int dz = 0; dz < 13; dz++) footing(c, s, s.x + dx, s.z + dz, 1, 0);
        for (int dx = 0; dx < 25; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = -1; dy < 11; dy++) {
            int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
            boolean road = dz >= 4 && dz <= 8;
            boolean kerb = dz == 3 || dz == 9;
            boolean shops = dz <= 2 || dz >= 10;
            if (dy == -1) {
                put(c, wx, wy, wz, road ? 251 : kerb ? 155 : 98, road ? 15 : 0);
                continue;
            }
            if (shops) {
                // Two rows of units, mostly gutted, walls broken down to head height.
                boolean divider = dx % 6 == 0;
                boolean front = dz == 2 || dz == 10;
                int top = 7 - (dx / 7);
                if (dy > top) { put(c, wx, wy, wz, 0, 0); continue; }
                if (dy == top) { put(c, wx, wy, wz, r.nextInt(4) == 0 ? 0 : 45, 0); continue; }
                if (divider || (front && dx % 6 != 3)) {
                    boolean glass = front && dy >= 2 && dy <= 3;
                    put(c, wx, wy, wz, glass ? (r.nextInt(3) == 0 ? 0 : 102) : 45, 0);
                    continue;
                }
                put(c, wx, wy, wz, 0, 0);
                if (dy == 0 && dx % 6 == 4 && dz == 0) graded(c, s, wx, wy, wz, r, STREET_LOOT, false);
                else if (dy == 0 && dx % 6 == 1 && dz == 12) graded(c, s, wx, wy, wz, r, STREET_LOOT, true);
                // The spawner that sat right behind the west unit's door stands a block aside.
                else if (dy == 0 && dz == 11 && (dx % 7 == 3 && dx % 6 != 3 || dx == 2)) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            if (kerb && dy == 0 && dx % 8 == 2) { put(c, wx, wy, wz, 139, 0); continue; }
            if (kerb && dy >= 1 && dy <= 3 && dx % 8 == 2) { put(c, wx, wy, wz, 101, 0); continue; }
            if (kerb && dy == 4 && dx % 8 == 2) { put(c, wx, wy, wz, 89, 0); continue; }      // street light
            // Two cars burned out across the carriageway, and the bus jammed over the far end.
            boolean car = (dx >= 4 && dx <= 7 && dz >= 5 && dz <= 6) || (dx >= 13 && dx <= 16 && dz >= 6 && dz <= 7);
            if (car && dy == 0) { put(c, wx, wy, wz, 251, 15); continue; }                  // black concrete, was coal block
            if (car && dy == 1 && dx % 4 != 0) {
                // A fire burns on netherrack set into the wreck; on the concrete body it went out.
                boolean burning = r.nextInt(3) == 0;
                if (burning) put(c, wx, wy - 1, wz, 87, 0);
                put(c, wx, wy, wz, burning ? 51 : 101, 0);
                continue;
            }
            // The service bus, turned across the road from kerb to kerb (it was a solid 5x5 cube):
            // a white body with a band of windows, black wheels, a hollow aisle and one door.
            // It lies across the carriageway only: over the kerbs it blocked both end shops' doors.
            boolean bus = dx >= 20 && dx <= 22 && dz >= 4 && dz <= 8;
            if (bus && dy <= 2) {
                boolean side = dx != 21, end = dz == 4 || dz == 8;
                if (dy == 2) { put(c, wx, wy, wz, 251, 0); continue; }
                if (!side && !end) { put(c, wx, wy, wz, 0, 0); continue; }            // the aisle
                if (dx == 20 && dz == 6) { put(c, wx, wy, wz, 0, 0); continue; }      // the door
                if (dy == 0) { boolean wheel = side && (dz == 5 || dz == 7); put(c, wx, wy, wz, 251, wheel ? 15 : 0); continue; }
                put(c, wx, wy, wz, roll(s, dx, dy, dz, 4) == 0 ? 0 : 102, 0);         // a few panes gone
                continue;
            }
            // The manhole, and the ladder somebody went down.
            if (dx == 10 && dz == 6) {
                if (dy == -1) { put(c, wx, wy, wz, 0, 0); continue; }
                if (dy < -1) continue;
            }
            if (dy == 0 && dx == 10 && dz == 6) { put(c, wx, wy, wz, 0, 0); continue; }
        }
        // The manhole goes somewhere now (structure audit 2026-09-23): the ladder runs down to a
        // stub of brick sewer with a water channel between two walkways, grated at both ends and
        // lit from behind one grate. The chest stands on the walkway (it was sealed into the rock
        // at the bottom of a dead-end hole, with its lid blocked); its tier is unchanged.
        int fl = s.y - 11;                                      // sewer floor
        for (int dx = 4; dx <= 16; dx++) for (int dz = 4; dz <= 8; dz++) for (int y = fl - 1; y <= s.y - 7; y++) {
            boolean shellCell = dx == 4 || dx == 16 || dz == 4 || dz == 8 || y == fl - 1 || y == s.y - 7;
            boolean grate = (dx == 5 || dx == 15) && dz >= 5 && dz <= 7 && y > fl && y < s.y - 7;
            if (dx == 4 || dx == 16) { if (dz >= 5 && dz <= 7 && y > fl && y < s.y - 7) { put(c, s.x + dx, y, s.z + dz, 98, 0); continue; } }
            if (grate) { put(c, s.x + dx, y, s.z + dz, 101, 0); continue; }
            if (shellCell) { int k = roll(s, dx, y, dz, 6); put(c, s.x + dx, y, s.z + dz, 98, k == 0 ? 1 : k == 1 ? 2 : 0); continue; }
            if (y == fl) { put(c, s.x + dx, y, s.z + dz, dz == 6 ? 9 : 98, 0); continue; }
            put(c, s.x + dx, y, s.z + dz, 0, 0);
        }
        put(c, s.x + 16, fl + 2, s.z + 6, 89, 0);                  // light behind the east grate
        for (int y = fl + 1; y <= s.y - 1; y++) {
            put(c, s.x + 10, y, s.z + 5, 65, 3);
            if (y >= s.y - 7) put(c, s.x + 10, y, s.z + 6, 0, 0);
        }
        graded(c, s, s.x + 7, fl + 1, s.z + 5, r, STREET_LOOT, true);
        streetFitOut(c, s);
    }

    /**
     * Raccoon Street's shop rows (structure audit 2026-09-23): back walls, so the units are
     * rooms rather than stage flats; a trade for each gutted unit; a police barricade at both
     * road ends; and no loose course bricks left hanging over the shops.
     */
    private static void streetFitOut(Chunk c, Site s) {
        // The block was cut into rising ground: a gravel back alley behind both rows (their back
        // doors opened into the bank) and at the road ends, held by stone-brick retaining walls.
        apron(c, s, 98, 0, 13, 0);
        for (int dx = 0; dx < 25; dx++) {
            int top = 7 - (dx / 7);
            for (int zb : new int[]{-1, 13}) {
                int wx = s.x + dx, wz = s.z + zb;
                footing(c, s, wx, wz, 98, 0);
                for (int dy = 0; dy <= top; dy++) {
                    boolean door = dx % 6 == 3 && dy <= 1;
                    boolean window = (dx % 6 == 1 || dx % 6 == 5) && (dy == 2 || dy == 3);
                    int id = dy == top ? (roll(s, dx, dy, zb, 4) == 0 ? 0 : 45) : door ? 0 : window ? (roll(s, dx, dy, zb, 3) == 0 ? 0 : 102) : 45;
                    put(c, wx, s.y + dy, wz, id, 0);
                }
            }
        }
        // A trade in each unit: gun shop, pharmacy, diner, news-stand, round both rows.
        for (int u = 0; u < 4; u++) for (int row = 0; row < 2; row++) {
            int x0 = s.x + 6 * u + 1, y = s.y;
            int zi = s.z + (row == 0 ? 1 : 11), zb = s.z + (row == 0 ? 0 : 12);   // front row, back row
            int trade = (u + row * 2) % 4;
            int[][] fit;
            switch (trade) {
                case 0: fit = new int[][]{{1, 0, 44, 15}, {3, 0, 44, 15}, {0, 1, 101, 0}, {4, 1, 101, 0}, {1, 1, 23, 3}}; break;
                case 1: fit = new int[][]{{1, 0, 251, 0}, {3, 0, 251, 0}, {0, 1, 117, 0}, {4, 1, 118, 0}}; break;
                case 2: fit = new int[][]{{1, 0, 109, 2}, {4, 0, 109, 2}, {1, 1, 44, 13}, {3, 1, 44, 13}, {4, 1, 118, 0}}; break;
                default: fit = new int[][]{{0, 1, 47, 0}, {4, 1, 47, 0}, {1, 1, 134, 4}, {3, 0, 126, 1}}; break;
            }
            for (int[] f : fit) {
                int wx = x0 + f[0], wz = f[1] == 0 ? zi : zb;
                int dx = wx - s.x, dz = wz - s.z;
                boolean loot = (dz == 0 && dx % 6 == 4) || (dz == 12 && dx % 6 == 1)
                        || (dz == 11 && (dx % 7 == 3 && dx % 6 != 3 || dx == 2));
                if (loot || dx % 6 == 3) continue;                   // chests, spawners, the door column
                put(c, wx, y, wz, f[2], f[3]);
            }
            put(c, x0 + (row == 0 ? 4 : 0), y + 2, zb, 30, 0);       // a web in the back corner
        }
        // Police barricades across the carriageway at both ends; the kerbs stay open to walk.
        for (int dx : new int[]{0, 24}) for (int dz = 4; dz <= 8; dz++) {
            put(c, s.x + dx, s.y, s.z + dz, 139, 0);
            if (dz >= 5 && dz <= 7) put(c, s.x + dx, s.y + 1, s.z + dz, 101, 0);
        }
        // Course bricks the random breaks left standing alone over the shops come down (twice,
        // so a pair left holding only each other goes too).
        for (int pass = 0; pass < 2; pass++) for (int dx = 0; dx < 25; dx++) {
            int y = s.y + 7 - (dx / 7);
            for (int dz : new int[]{-1, 0, 1, 2, 10, 11, 12, 13}) {
                Block b = blockAt(c, s.x + dx, y, s.z + dz);
                if (b == null || b.getTypeId() != 45) continue;
                if (!solidAt(c, s.x + dx + 1, y, s.z + dz) && !solidAt(c, s.x + dx - 1, y, s.z + dz)
                        && !solidAt(c, s.x + dx, y, s.z + dz + 1) && !solidAt(c, s.x + dx, y, s.z + dz - 1)
                        && !solidAt(c, s.x + dx, y - 1, s.z + dz)) put(c, s.x + dx, y, s.z + dz, 0, 0);
            }
        }
    }

    // == 8. The Village ==========================================================
    // A hill hamlet that kept to itself. Rough plaster and timber, thatch going black,
    // a fire in the middle of the green that has been fed recently, a well, and a church
    // at the top end with the door standing open.

    private static final int[][] VILLAGE_LOOT = {
        {296, 0, 15, 6, 18}, {344, 0, 11, 3, 8},  {320, 0, 12, 2, 6}, {266, 0, 10, 2, 7},
        {388, 0,  8, 1, 4},  {260, 0, 12, 3, 9},  {391, 0, 10, 3, 9}, {392, 0,  9, 3, 9},
        {338, 0,  8, 2, 6},  {287, 0, 10, 3, 10}, {265, 0, 10, 2, 7}, {373, 0,  7, 1, 2},
    };

    private static void village(World w, Chunk c, Terrain t) {
        Site s = site(t, c, VILLAGE_CELL, 0x56494C4CL, 23, 19, 8, Megaliths.RANK_VILLAGE);
        if (s == null) return;
        Random r = new Random(s.seed);
        // Four cottages round a green, plus the church along the top edge.
        int[][] huts = {{1, 1, 7, 6}, {15, 2, 7, 6}, {2, 12, 6, 6}, {13, 11, 8, 7}};
        for (int dx = 0; dx < 23; dx++) for (int dz = 0; dz < 19; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            footing(c, s, wx, wz, 3, 0);
            put(c, wx, s.y - 1, wz, r.nextInt(7) == 0 ? 13 : 2, 0);
            // The green is a level clearing: the hillside above the site floor no longer runs on
            // into the footprint, burying doorways, the fire and the chest (structure audit 2026-09-23).
            clearAbove(c, wx, wz, s.y, s.hi + 12);
        }
        villageEdge(c, s);
        for (int h = 0; h < huts.length; h++) {
            int hx = huts[h][0], hz = huts[h][1], hw = huts[h][2], hd = huts[h][3];
            // Every cottage opens onto the green: the top row's doors are on their south walls.
            boolean top = hz < 9;
            for (int dx = 0; dx < hw; dx++) for (int dz = 0; dz < hd; dz++) for (int dy = 0; dy < 8; dy++) {
                int wx = s.x + hx + dx, wy = s.y + dy, wz = s.z + hz + dz;
                int v = top ? hd - 1 - dz : dz;                  // distance from the door wall
                boolean wall = dx == 0 || dx == hw - 1 || dz == 0 || dz == hd - 1;
                if (dy >= 5) {
                    // Thatch, pitched, blackened at the ridge (it was all new straw).
                    int inset = dy - 5;
                    boolean roof = dx >= inset && dx <= hw - 1 - inset && dz >= inset && dz <= hd - 1 - inset;
                    boolean ridge = roof && (dy == 7 || (dy == 6 && roll(s, hx + dx, dy, hz + dz, 4) == 0));
                    put(c, wx, wy, wz, roof ? (ridge ? 159 : 170) : 0, ridge ? (roll(s, hx + dx, dy + 9, hz + dz, 3) == 0 ? 12 : 15) : 0);
                    continue;
                }
                if (wall) {
                    boolean door = v == 0 && dx == hw / 2 && dy <= 2;
                    boolean window = dy == 3 && (dx == 1 || dx == hw - 2) && (dz == 0 || dz == hd - 1);
                    if (door) { put(c, wx, wy, wz, 0, 0); continue; }
                    if (window) { put(c, wx, wy, wz, 101, 0); continue; }
                    // Timber frame with plaster between; the corner of the cottage by the fire is
                    // stone, so the fire on the green has no timber within its reach.
                    boolean beam = dx == 0 || dx == hw - 1 || dy == 0 || dy == 4;
                    if (h == 3 && dx == 0 && dz == 0) { put(c, wx, wy, wz, 4, 0); continue; }
                    put(c, wx, wy, wz, beam ? 17 : 159, beam ? 0 : 0);
                    continue;
                }
                put(c, wx, wy, wz, 0, 0);
                // The hearth corner beside the door (the furnace faces the room now), the chest and
                // the spawner at the back.
                if (dy == 0 && dx == 1 && v == 1) { put(c, wx, wy, wz, 61, 5); continue; }
                if (dy == 0 && dx == hw - 2 && v == hd - 2)
                    graded(c, s, wx, wy, wz, r, VILLAGE_LOOT, r.nextInt(3) == 0);
                else if (dy == 0 && dx == 1 && v == hd - 2) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
                else {
                    int fit = cottageFitting(hw, hd, dx, v, dy);
                    if (fit >= 0) put(c, wx, wy, wz, fit >> 4, fit & 15);
                }
            }
            // A light outside each door.
            int lx = s.x + hx + hw / 2 + 1, lz = s.z + hz + (top ? hd : -1);
            put(c, lx, s.y + 2, lz, 50, top ? 3 : 4);
        }
        // The green: a fire that is still going, and a well beside it.
        for (int dx = 9; dx <= 13; dx++) for (int dz = 7; dz <= 11; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            double d = Math.sqrt((dx - 11) * (dx - 11) + (dz - 9) * (dz - 9));
            if (d < 1.4) { put(c, wx, s.y - 1, wz, 87, 0); put(c, wx, s.y, wz, 51, 0); }
            else if (d < 2.6) put(c, wx, s.y, wz, 139, 0);
        }
        // The well stands on the green beside the fire (it was built inside the south-west cottage,
        // over its chest): a stone-lined shaft of water under a roofed frame.
        for (int dx = 14; dx <= 16; dx++) for (int dz = 8; dz <= 10; dz++) {
            boolean rim = dx != 15 || dz != 9;
            int wx = s.x + dx, wz = s.z + dz;
            if (rim) {
                for (int y = s.y - 4; y <= s.y; y++) put(c, wx, y, wz, roll(s, dx, y - s.y, dz, 3) == 0 ? 48 : 4, 0);
            } else {
                put(c, wx, s.y - 4, wz, 4, 0);
                for (int y = s.y - 3; y <= s.y - 1; y++) put(c, wx, y, wz, 9, 0);
                put(c, wx, s.y, wz, 0, 0);
            }
            if (rim && dx != 15 && dz != 9) for (int dy = 1; dy <= 3; dy++) put(c, wx, s.y + dy, wz, 85, 0);
            put(c, wx, s.y + 4, wz, 126, 0);
        }
        // Pitchforks left stood in the ground where the crowd broke up (same draws; they now stand
        // on the open green west of the fire, never in a wall, the chapel or a doorway).
        for (int n = 0; n < 5; n++) {
            int px = 8 + r.nextInt(8) - 7, pz = 3 + r.nextInt(4) + 4;
            if (px == 4 && pz == 7) continue;
            put(c, s.x + px, s.y, s.z + pz, 85, 0);
            put(c, s.x + px, s.y + 1, s.z + pz, 101, 0);
        }
        // The church at the top end, door open onto the green; the crowd's spawner is inside it now
        // (it stood bare on the grass by the fire, where it could never spawn anything).
        villageChapel(c, s);
        mob(c, s, s.x + 13, s.y + 1, s.z + 1, r, EntityType.ZOMBIE);
        graded(c, s, s.x + 12, s.y, s.z + 12, r, VILLAGE_LOOT, true);
        put(c, s.x + 12, s.y, s.z + 13, 170, 0);                  // a bale beside the supply crate
    }

    /**
     * Cottage furniture by (dx, distance from the door wall v): a straw bed, a table and stool, a
     * cauldron and crafting table by the hearth, a log store. Returns (id << 4 | data) or -1.
     */
    private static int cottageFitting(int hw, int hd, int dx, int v, int dy) {
        if (dy != 0 && !(dy == 1 && dx == hw - 2 && v == 2)) return -1;
        if (dy == 1) return 72 << 4;                                         // plate on the table
        if (dx == 1 && v == 2 && hd - 2 > 2) return 118 << 4;                // cauldron by the hearth
        if (dx == 1 && v == 3 && hd - 2 > 3) return 58 << 4;                 // crafting table
        if (v == hd - 2 && dx == hw - 3) return 170 << 4;                    // straw bed
        if (v == hd - 2 && dx == hw - 4 && dx > 1) return 170 << 4;
        if (dx == hw - 2 && v == 2) return 85 << 4;                          // table
        if (dx == hw - 3 && v == 2 && dx != hw / 2) return 53 << 4 | 0;      // stool
        if (dx == hw - 2 && v == 1 && dx != hw / 2) return 17 << 4;          // log store
        return -1;
    }

    /**
     * The cut edge of the clearing: where the hill outside stands above the green, its face is
     * dressed as a dry-stone retaining wall instead of raw earth.
     */
    private static void villageEdge(Chunk c, Site s) {
        for (int dx = -1; dx <= 23; dx++) for (int dz = -1; dz <= 19; dz++) {
            boolean ring = dx == -1 || dx == 23 || dz == -1 || dz == 19;
            if (!ring) continue;
            int g = ground(c, s.x + dx, s.z + dz, s.hi + 12, s.y - 3);
            if (g == NONE || g < s.y) continue;
            for (int y = s.y; y <= g; y++) put(c, s.x + dx, y, s.z + dz, roll(s, dx, y - s.y, dz, 3) == 0 ? 48 : 4, 0);
        }
    }

    /** The village chapel: stone plinth, log frame, plaster, a steep thatch with a charred ridge, a bell. */
    private static void villageChapel(Chunk c, Site s) {
        int x0 = s.x, y0 = s.y, z0 = s.z;
        for (int dx = 8; dx <= 14; dx++) for (int dz = 0; dz <= 5; dz++) for (int dy = 0; dy <= 10; dy++) {
            int wx = x0 + dx, wy = y0 + dy, wz = z0 + dz;
            boolean wall = dx == 8 || dx == 14 || dz == 0 || dz == 5;
            if (dy >= 7) {
                int inset = dy - 7;                                   // gable roof along the nave
                boolean roof = dx >= 8 + inset && dx <= 14 - inset;
                boolean ridge = roof && (dy == 10 || roll(s, dx, dy, dz, 4) == 0);
                put(c, wx, wy, wz, roof ? (ridge ? 159 : 170) : 0, roof && ridge ? 15 : 0);
                continue;
            }
            if (dy == 0) { put(c, wx, wy, wz, wall ? 4 : 5, 0); continue; }
            if (wall) {
                boolean door = dz == 5 && dx == 11 && dy <= 2;
                if (door) { put(c, wx, wy, wz, 64, dy == 1 ? 7 : 8); continue; }   // standing open
                boolean window = (dx == 8 || dx == 14) && (dz == 2 || dz == 3) && (dy == 2 || dy == 3);
                if (window) { put(c, wx, wy, wz, 101, 0); continue; }
                boolean beam = (dx == 8 || dx == 14) && (dz == 0 || dz == 5) || dy == 6;
                put(c, wx, wy, wz, beam ? 17 : 159, 0);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
            if (dy != 1) continue;
            if (dz == 1 && dx >= 10 && dx <= 12) { put(c, wx, wy, wz, 4, 0); continue; }         // altar
            if ((dz == 3 || dz == 4) && dx != 11) put(c, wx, wy, wz, 53, 2);                      // pews
        }
        put(c, x0 + 10, y0 + 2, z0 + 1, 76, 5); put(c, x0 + 12, y0 + 2, z0 + 1, 76, 5);            // red candles
        // The bell-cote on the south gable.
        for (int y = 10; y <= 12; y++) { put(c, x0 + 10, y0 + y, z0 + 5, 85, 0); put(c, x0 + 12, y0 + y, z0 + 5, 85, 0); }
        for (int dx = 10; dx <= 12; dx++) put(c, x0 + dx, y0 + 13, z0 + 5, 126, 0);
        put(c, x0 + 11, y0 + 12, z0 + 5, 25, 0);
    }


    // == 9. Lost Tower ===========================================================
    // A commercial tower with the top sheared off at an angle. Slab floors still stacked,
    // the curtain wall gone except where it jammed, reinforcement standing out of the
    // broken edges, and a skirt of its own rubble round the foot.

    private static final int[][] TOWER_LOOT = {
        {160, 0, 14, 4, 12}, {339, 0, 14, 5, 16}, {340, 0, 10, 2, 5}, {331, 0, 12, 5, 16},
        { 20, 0,  9, 4, 12}, {263, 0, 11, 4, 12}, {123, 0, 8, 1, 5}, {102, 0, 6, 1, 1},
        {154, 0,  7, 1, 2},  {347, 0,  6, 1, 1},  {358, 0,  6, 1, 1}, {264, 0,  5, 1, 2},
    };

    private static void tower(World w, Chunk c, Terrain t) {
        Site s = site(t, c, TOWER_CELL, 0x544F5752L, 15, 15, 8, Megaliths.RANK_TOWER);
        if (s == null) {
            // The rubble skirt spills into the neighbouring chunks, which the footprint does not
            // reach; they find the same site (same lattice, same tests) and lay only the skirt.
            Site n = siteNear(t, c, TOWER_CELL, 0x544F5752L, 15, 15, 8, Megaliths.RANK_TOWER, 3);
            if (n != null) towerSkirt(c, n);
            return;
        }
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 15; dz++) footing(c, s, s.x + dx, s.z + dz, 251, 7);
        int floors = 11;
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 15; dz++) {
            boolean holeBelow = false, barBelow = false;          // the broken storey's slab and rebar, per column
            for (int dy = 0; dy < floors * 4; dy++) {
                int wx = s.x + dx, wy = s.y + dy, wz = s.z + dz;
                int floor = dy / 4;
                // The shear: a diagonal plane takes the building away towards one corner.
                int cut = 6 + (14 - dx + 14 - dz) / 2;
                if (floor > cut) { put(c, wx, wy, wz, 0, 0); continue; }
                boolean edge = floor == cut;                       // the broken storey
                boolean wall = dx == 0 || dx == 14 || dz == 0 || dz == 14;
                boolean core = dx >= 6 && dx <= 8 && dz >= 6 && dz <= 8;
                // (structure audit 2026-09-23) The lift core keeps one concrete column at (7,7) with
                // a ladder on its south face at (7,8), unbroken from the lobby to the top floor, and
                // every floor keeps the landing in front of it. The ladder used to hang in the
                // middle of the hole, backed by nothing and cut at every slab.
                boolean column = dx == 7 && dz == 7 && dy <= floors * 4 - 3;
                boolean ladder = dx == 7 && dz == 8 && dy >= 1 && dy <= (floors - 1) * 4;
                if (column) { put(c, wx, wy, wz, 251, 7); continue; }
                if (ladder) { put(c, wx, wy, wz, 65, 3); continue; }
                if (dy % 4 == 0) {
                    // Slab floor, holed where it has failed, and always holed at the core.
                    boolean hole = core || (edge && r.nextInt(3) == 0) || r.nextInt(22) == 0;   // same draws
                    // ... but never under the ladder foot, the landing, the lift rail, a chest,
                    // a spawner or a piece of furniture.
                    if (hole && !core && !edge && towerKeep(s, floor, dx, dz)) hole = false;
                    if (dx == 7 && dz == 8) hole = false;          // the lobby floor under the ladder
                    holeBelow = hole;
                    barBelow = false;
                    put(c, wx, wy, wz, hole ? 0 : 251, hole ? 0 : 7);
                    continue;
                }
                if (edge) {
                    // Reinforcement left standing out of the break -- only on slab that survived
                    // or on the rebar below it (single bars used to hang over holes).
                    boolean bar = wall && r.nextInt(3) == 0;        // same draws as before
                    bar = bar && !holeBelow && (dy % 4 == 1 || barBelow);
                    barBelow = bar;
                    put(c, wx, wy, wz, bar ? 101 : 0, 0);
                    continue;
                }
                if (wall) {
                    // Mullions every third block along each face. The old test also used the face's
                    // own coordinate, which made the north and west faces solid concrete; the glass
                    // they get now is rolled per cell rather than drawn, so the sequence is kept.
                    boolean corner = (dx == 0 || dx == 14) && (dz == 0 || dz == 14);
                    boolean mullion = corner || ((dx == 0 || dx == 14) ? dz % 3 == 0 : dx % 3 == 0);
                    boolean oldMullion = dx % 3 == 0 || dz % 3 == 0;
                    // A lobby entrance on the south face, and one on the north (it had none).
                    boolean door = floor == 0 && dy % 4 <= 2 && (dz == 14 || dz == 0) && (dx == 7 || dx == 8);
                    if (mullion) { put(c, wx, wy, wz, 251, 7); continue; }
                    if (oldMullion) { put(c, wx, wy, wz, door || roll(s, dx, dy, dz, 6) == 0 ? 0 : 20, 0); continue; }
                    // Most of the glass is gone; what is left is at the sheltered end.
                    int pane = r.nextInt(6) < (floor < 4 ? 1 : 3) ? 0 : 20;
                    put(c, wx, wy, wz, door ? 0 : pane, 0);
                    continue;
                }
                put(c, wx, wy, wz, 0, 0);
                // A rail round the open lift shaft on every floor, open at the landing.
                if (dy % 4 == 1 && towerRail(dx, dz)) { put(c, wx, wy, wz, 101, 0); continue; }
                if (dy % 4 == 1 && floor % 2 == 1 && dx == 3 && dz == 11)
                    graded(c, s, wx, wy, wz, r, TOWER_LOOT, floor >= 6);
                else if (dy % 4 == 1 && floor % 3 == 2 && dx == 11 && dz == 3)
                    mob(c, s, wx, wy, wz, r, floor >= 7 ? EntityType.SKELETON : EntityType.ZOMBIE);
                else {
                    int fit = towerFitting(s, floor, dx, dy % 4, dz);
                    if (fit >= 0) put(c, wx, wy, wz, fit >> 4, fit & 15);
                }
            }
        }
        // Slab left as an island at the break, touching nothing, comes down.
        for (int dx = 0; dx < 15; dx++) for (int dz = 0; dz < 15; dz++) {
            int cut = 6 + (14 - dx + 14 - dz) / 2;
            if (cut >= floors) continue;
            int wx = s.x + dx, wy = s.y + cut * 4, wz = s.z + dz;
            Block b = blockAt(c, wx, wy, wz);
            if (b == null || b.getTypeId() != 251) continue;
            if (!solidAt(c, wx + 1, wy, wz) && !solidAt(c, wx - 1, wy, wz) && !solidAt(c, wx, wy, wz + 1)
                    && !solidAt(c, wx, wy, wz - 1) && !solidAt(c, wx, wy - 1, wz) && !solidAt(c, wx, wy + 1, wz))
                put(c, wx, wy, wz, 0, 0);
        }
        // Steps from the north door where the ground banks up against it.
        for (int dx = 7; dx <= 8; dx++) steps(c, s.x + dx, s.z, 0, -1, s.y + 1, 3, 109, 3);
        towerSkirt(c, s);
    }

    /** The rail round the lift shaft: every cell bordering the open core except the landing at (7,9). */
    private static boolean towerRail(int dx, int dz) {
        if ((dx == 5 || dx == 9) && dz >= 6 && dz <= 8) return true;
        if (dz == 5 && dx >= 6 && dx <= 8) return true;
        return dz == 9 && (dx == 6 || dx == 8);
    }

    /** Slab cells that must not be holed at random: landing, rail, chest, spawner and furniture. */
    private static boolean towerKeep(Site s, int floor, int dx, int dz) {
        if (dx == 7 && dz == 9) return true;
        if (towerRail(dx, dz)) return true;
        if ((dx == 3 && dz == 11) || (dx == 11 && dz == 3)) return true;
        for (int k = 1; k <= 2; k++) if (towerFitting(s, floor, dx, k, dz) >= 0) return true;
        return false;
    }

    /**
     * The tower as a wrecked office block (structure audit 2026-09-23): a lobby counter, desks and
     * chairs, filing, a glass-walled office round each chest, and cobwebs and rubble that get
     * heavier towards the side that sheared off. Returns (id << 4 | data) for feet level k = 1,
     * head level k = 2 or k = 3, or -1.
     */
    private static int towerFitting(Site s, int floor, int dx, int k, int dz) {
        if (floor == 0) {
            if (k == 1 && dz == 11 && dx >= 9 && dx <= 11) return 251 << 4 | 8;       // reception counter
            if (k == 2 && dz == 11 && dx == 10) return 44 << 4;
            if (k == 1 && dz == 1 && (dx == 1 || dx == 13)) return 140 << 4;          // planters
            return -1;
        }
        // Desks with chairs in the north-west.
        if (k == 1 && dz == 2 && (dx == 2 || dx == 4)) return 251 << 4 | 8;
        if (k == 1 && dz == 3 && (dx == 2 || dx == 4)) return 109 << 4 | 2;
        // Filing along the east wall.
        if (k <= 2 && dx == 13 && (dz == 9 || dz == 10)) return 47 << 4;
        if (floor % 2 == 1) {
            // The glass-walled office round the chest: partitions along z 9 and x 5, door gap at (2,9).
            if (k <= 2 && ((dz == 9 && dx >= 1 && dx <= 4 && dx != 2) || (dx == 5 && dz >= 10 && dz <= 13 && dz != 12))) return 102 << 4;
            if (k == 1 && dx == 1 && dz == 12) return 251 << 4 | 8;
            if (k == 1 && dx == 2 && dz == 12) return 109 << 4 | 1;
        } else if (k == 1 && dx >= 2 && dx <= 4 && dz >= 11 && dz <= 12) {
            return 171 << 4 | 8;                                                      // worn carpet tiles
        }
        // Spawner floors: webs round the lift lobby.
        if (floor % 3 == 2 && k == 3 && ((dx == 10 && dz == 2) || (dx == 12 && dz == 4) || (dx == 5 && dz == 10))) return 30 << 4;
        // Cubicle stubs in the south-east, then debris there, heavier the nearer the break.
        if (k == 1 && dx == 10 && (dz == 10 || dz == 11)) return 102 << 4;
        if (s != null && k == 1 && dx >= 11 && dz >= 10 && dx <= 13 && dz <= 13) {
            int roll = roll(s, dx, floor, dz, 10);
            if (roll < floor / 3) return roll % 2 == 0 ? 4 << 4 : 13 << 4;
        }
        if (s != null && k == 3 && (dx == 1 || dx == 13) && (dz == 1 || dz == 13) && roll(s, dx, floor + 40, dz, 3) == 0) return 30 << 4;
        return -1;
    }

    /** The skirt of rubble round the tower's foot, heaviest on the south-east where it fell. */
    private static void towerSkirt(Chunk c, Site s) {
        for (int dx = -3; dx < 18; dx++) for (int dz = -3; dz < 18; dz++) {
            int out = Math.max(0, Math.max(-dx, Math.max(dx - 14, Math.max(-dz, dz - 14))));
            if (out <= 0 || out > 3) continue;
            boolean fell = dx >= 10 || dz >= 10;
            if (roll(s, dx, 0, dz, fell ? out + 1 : out + 3) != 0) continue;
            int g = ground(c, s.x + dx, s.z + dz, s.hi + 6, s.lo - 8);
            if (g == NONE || g < s.lo - 7) continue;
            boolean cobble = roll(s, dx, 1, dz, 3) == 0;
            put(c, s.x + dx, g + 1, s.z + dz, cobble ? 4 : 251, cobble ? 0 : 7);
            if (fell && out == 1 && roll(s, dx, 2, dz, 3) == 0) put(c, s.x + dx, g + 2, s.z + dz, 101, 0);   // a bent bar
        }
    }

    // == 10. Lost Block ==========================================================
    // Three buildings sharing party walls, put up at different times and different
    // heights, with the street frontage on one side and a yard behind. Floors intact
    // enough to walk, which is what makes it worth going in.

    private static final int[][] BLOCKS_LOOT = {
        {297, 0, 13, 2, 7},  {139, 0, 13, 3, 10}, {263, 0, 12, 4, 14}, {339, 0, 11, 3, 10},
        {331, 0, 10, 4, 14}, {320, 0,  9, 2, 5},  {35, 7, 9, 3, 9},  {324, 0, 8, 1, 5},
        {373, 0,  7, 1, 2},  {384, 0,  7, 2, 6},  {345, 0,  5, 1, 1},  {108, 0, 5, 1, 1},
    };

    private static void blocks(World w, Chunk c, Terrain t) {
        Site s = site(t, c, BLOCKS_CELL, 0x424C4B53L, 25, 15, 7, Megaliths.RANK_BLOCKS);
        if (s == null) return;
        Random r = new Random(s.seed);
        for (int dx = 0; dx < 25; dx++) for (int dz = 0; dz < 15; dz++) {
            int wx = s.x + dx, wz = s.z + dz;
            footing(c, s, wx, wz, 251, 7);
            put(c, wx, s.y - 1, wz, dz <= 2 ? 251 : 98, dz <= 2 ? 15 : 0);   // road along the front
            // The street is a street again: the bank and the trees that stood on the road and against
            // the shopfronts are cleared, and a pavement runs in front of the shops (structure audit
            // 2026-09-23; the road was buried under up to three blocks of ground).
            if (dz <= 2) {
                clearAbove(c, wx, wz, s.y, s.hi + 12);
                if (dz == 2) put(c, wx, s.y, wz, 98, 0);
            }
        }
        // Where the ground outside the road stands higher, its face is a stone-brick retaining wall.
        for (int dx = -1; dx < 25; dx++) for (int dz = -1; dz <= 2; dz++) {
            if (dx >= 0 && dz >= 0) continue;
            int g = ground(c, s.x + dx, s.z + dz, s.hi + 12, s.y - 2);
            if (g == NONE || g < s.y) continue;
            for (int y = s.y; y <= g; y++) put(c, s.x + dx, y, s.z + dz, 98, 0);
        }
        int[][] units = {{0, 3, 8, 5}, {8, 3, 9, 8}, {17, 3, 8, 12}};        // x, z, width, storeys
        for (int u = 0; u < units.length; u++) {
            int ux = units[u][0], uz = units[u][1], uw = units[u][2], storeys = units[u][3];
            int ud = 15 - uz;
            int skin = u == 1 ? 45 : u == 2 ? 251 : 98, skinData = u == 2 ? 8 : 0;
            for (int dx = 0; dx < uw; dx++) for (int dz = 0; dz < ud; dz++) for (int dy = 0; dy < storeys * 4; dy++) {
                int wx = s.x + ux + dx, wy = s.y + dy, wz = s.z + uz + dz;
                boolean party = dx == 0 || dx == uw - 1;
                boolean face = dz == 0 || dz == ud - 1;
                boolean wall = party || face;
                int floor = dy / 4;
                // (structure audit 2026-09-23) The ladder in the back corner runs unbroken from the
                // ground floor to the new roof: the slab used to be laid across it at every storey.
                boolean ladder = dx == uw - 2 && dz == ud - 2 && dy > 0;
                if (dy % 4 == 0) {
                    boolean hole = r.nextInt(18) == 0;                           // same draws as before
                    if (ladder) { put(c, wx, wy, wz, 65, 2); continue; }
                    if (hole && blocksKeep(u, uw, ud, floor, dx, dz)) hole = false;   // not under what stands on it
                    boolean lamp = u == 2 && floor > 0 && floor % 2 == 1 && dx == 3 && dz == 7;   // office ceiling light
                    put(c, wx, wy, wz, hole ? 0 : lamp ? 89 : 251, hole || lamp ? 0 : 7);
                    continue;
                }
                if (dy == storeys * 4 - 1) { put(c, wx, wy, wz, wall ? 45 : ladder ? 65 : 0, ladder ? 2 : 0); continue; }
                if (wall) {
                    boolean shopfront = dz == 0 && floor == 0 && dx > 0 && dx < uw - 1;
                    boolean window = floor > 0 && dz == 0 && dy % 4 == 2 && dx % 2 == 1;
                    if (shopfront) {
                        int pane = dy % 4 == 3 ? 45 : (r.nextInt(4) == 0 ? 0 : 102);
                        // Each shop gets a door in the middle of its front (there was none).
                        if (dx == uw / 2 && dy % 4 != 3) { put(c, wx, wy, wz, 197, dy == 1 ? 5 : 8); continue; }
                        put(c, wx, wy, wz, pane, 0);
                        continue;
                    }
                    if (window) { put(c, wx, wy, wz, r.nextInt(5) == 0 ? 0 : 102, 0); continue; }
                    // Windows at the back, and in outer side walls where no neighbour stands against
                    // them (all were blank); rolled, so the draws above are unchanged.
                    boolean back = dz == ud - 1 && dy % 4 == 2 && dx % 2 == 1 && dx != uw - 2 && !party;
                    boolean exposed = party && ((u == 0 && dx == 0) || (u == 2 && dx == uw - 1)
                            || (dx == 0 && u == 1 && dy >= 20) || (dx == 0 && u == 2 && dy >= 32));
                    boolean side = exposed && dy % 4 == 2 && dz % 2 == 1 && dz > 0 && dz < ud - 1;
                    if (back || side) { put(c, wx, wy, wz, roll(s, ux + dx, dy, uz + dz, 5) == 0 ? 0 : 102, 0); continue; }
                    put(c, wx, wy, wz, skin, skinData);
                    continue;
                }
                put(c, wx, wy, wz, 0, 0);
                if (ladder) { put(c, wx, wy, wz, 65, 2); continue; }
                if (dy % 4 == 1 && floor % 2 == 0 && dx == 2 && dz == 3)
                    graded(c, s, wx, wy, wz, r, BLOCKS_LOOT, floor >= 4);
                else if (dy % 4 == 1 && floor % 2 == 1 && dx == uw - 3 && dz == 5)
                    mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
                else {
                    int fit = blocksFitting(u, uw, ud, floor, dx, dy % 4, dz);
                    if (fit >= 0 && fit >> 4 != 50) put(c, wx, wy, wz, fit >> 4, fit & 15);
                }
            }
            // The flats' lamps go up once the back wall they hang on stands (hung first, they dropped).
            for (int f = 1; f < storeys; f++) {
                int fit = blocksFitting(u, uw, ud, f, 2, 2, 10);
                if (fit >> 4 == 50) put(c, s.x + ux + 2, s.y + f * 4 + 2, s.z + uz + 10, 50, fit & 15);
            }
            // A roof, reachable up the ladder, with a brick parapet (the top storeys were open to the sky).
            int ry = s.y + storeys * 4;
            for (int dx = 0; dx < uw; dx++) for (int dz = 0; dz < ud; dz++) {
                int wx = s.x + ux + dx, wz = s.z + uz + dz;
                boolean edge = dx == 0 || dx == uw - 1 || dz == 0 || dz == ud - 1;
                boolean ladder = dx == uw - 2 && dz == ud - 2;
                put(c, wx, ry, wz, ladder ? 65 : 251, ladder ? 2 : 7);
                if (edge) put(c, wx, ry + 1, wz, 45, 0);
            }
            if (u == 2) { put(c, s.x + ux + 3, ry + 1, s.z + uz + 4, 139, 0); put(c, s.x + ux + 3, ry + 2, s.z + uz + 4, 139, 0); }
        }
        // Street furniture along the frontage.
        for (int dx = 2; dx < 25; dx += 7) {
            put(c, s.x + dx, s.y, s.z + 1, 139, 0);
            for (int dy = 1; dy <= 3; dy++) put(c, s.x + dx, s.y + dy, s.z + 1, 101, 0);
            put(c, s.x + dx, s.y + 4, s.z + 1, 89, 0);
        }
    }

    /** Slab cells in the Lost Blocks that must not be holed: under the chest, spawner, ladder landing or furniture. */
    private static boolean blocksKeep(int u, int uw, int ud, int floor, int dx, int dz) {
        if (floor % 2 == 0 && dx == 2 && dz == 3) return true;
        if (floor % 2 == 1 && dx == uw - 3 && dz == 5) return true;
        if ((dx == uw - 3 && dz == ud - 2) || (dx == uw - 2 && dz == ud - 3)) return true;
        if (dx == uw - 2 && dz == ud - 1) return true;                       // the wall the ladder hangs on
        if (u < 2 && floor > 0 && dx == uw - 4 && dz >= 5 && dz <= 7) return true;   // through the partition doorway
        for (int k = 1; k <= 2; k++) if (blocksFitting(u, uw, ud, floor, dx, k, dz) >= 0) return true;
        return false;
    }

    /**
     * What each storey of the Lost Blocks is (structure audit 2026-09-23): a shop on every ground
     * floor (counter, shelving), flats above the stone-brick and brick buildings (a partition with
     * a doorway, a table and chairs, a stove and sink, a bed, a lamp), offices in the concrete one
     * (desks, chairs, filing, glass screens), and webs for age. Returns (id << 4 | data) or -1.
     */
    private static int blocksFitting(int u, int uw, int ud, int floor, int dx, int k, int dz) {
        // A full-height partition across each flat, doorway at uw-4, in the other building's stone.
        if (u < 2 && floor > 0 && dz == 6 && dx != uw - 4) return (u == 0 ? 45 : 98) << 4;
        if (k == 3) return (dx == 1 && dz == 1 && floor % 2 == 1) ? 30 << 4 : -1;
        if (floor == 0) {
            if (k == 1 && dz == 6 && dx >= 1 && dx <= uw - 5) return 126 << 4 | 13;      // counter
            if (dx == 1 && (dz == 8 || dz == 9)) return 47 << 4;                            // shelving
            if (k == 1 && dx == uw - 2 && dz == 1) return 85 << 4;                          // display stand
            return -1;
        }
        if (u < 2) {
            if (k == 1 && dx == 4 && dz == 3) return 85 << 4;                               // table
            if (k == 2 && dx == 4 && dz == 3) return 72 << 4;
            if (k == 1 && dx == 4 && (dz == 2 || dz == 4)) return 53 << 4 | (dz == 2 ? 3 : 2);
            if (k == 1 && dx == 1 && dz == 4) return 118 << 4;                              // sink
            if (k == 1 && dx == 1 && dz == 5) return 61 << 4 | 5;                           // stove
            if (k == 1 && dz == 9 && dx == 2) return 26 << 4 | 1;                           // bed, head to the west
            if (k == 1 && dz == 9 && dx == 1) return 26 << 4 | 9;
            if (k == 2 && dx == 2 && dz == 10 && floor % 2 == 0) return 50 << 4 | 4;        // lamp on the back wall
            if (k == 1 && dz == 8 && (dx == 1 || dx == 2)) return 171 << 4 | 12;
            return -1;
        }
        if (k == 1 && (dz == 3 || dz == 7) && (dx == 4 || dx == 5)) return 126 << 4 | 13; // desks
        if (k == 1 && (dz == 4 || dz == 8) && dx == 4) return 109 << 4 | 2;               // chairs
        if (dx == 1 && dz >= 6 && dz <= 9) return 47 << 4;                                 // filing
        if (dz == 5 && dx >= 3 && dx <= 4) return 102 << 4;                                // glass screen
        if (k == 1 && dz >= 2 && dz <= 4 && dx >= 2 && dx <= 3 && !(dx == 2 && dz == 3)) return 171 << 4 | 7;
        return -1;
    }

    // == 11. Lost Metro ==========================================================
    // A platform under the street, found because the ceiling let go and left a shaft of
    // daylight in the middle of it. Tiled to head height, flooded at one end, and a train
    // still sitting in the tunnel with its doors open.

    private static final int[][] METRO_LOOT = {
        { 66, 0, 14, 10, 32}, {328, 0,  9, 1, 3}, {157, 0, 13, 4, 12}, {331, 0, 12, 5, 16},
        {263, 0, 12, 5, 16},  {339, 0, 10, 3, 9}, {152, 0,  7, 1, 3},  {27,  0,  8, 4, 12},
        {297, 0,  9, 2, 6},   {408, 0, 7, 1, 4}, {356, 0,  7, 2, 6},  {28, 0, 5, 1, 1},
    };

    private static void metro(World w, Chunk c, Terrain t, Caves caves) {
        Site s = site(t, c, METRO_CELL, 0x4D455452L, 23, 13, 9, Megaliths.RANK_METRO);
        if (s == null) return;
        Random r = new Random(s.seed);
        int base = s.y - 16;
        if (base < 8) return;
        for (int dx = 0; dx < 23; dx++) for (int dz = 0; dz < 13; dz++) for (int dy = 0; dy < 8; dy++) {
            int wx = s.x + dx, wy = base + dy, wz = s.z + dz;
            boolean shell = dx == 0 || dx == 22 || dz == 0 || dz == 12 || dy == 0;
            boolean roof = dy == 7;
            // The hole: a ragged shaft from the platform up to daylight.
            double hole = Math.sqrt((dx - 11) * (dx - 11) + (dz - 6) * (dz - 6));
            if (roof) {
                if (hole < 2.6) { put(c, wx, wy, wz, 0, 0); continue; }
                int crack = r.nextInt(5) == 0 ? 2 : 0;                         // same draw as before
                // The lamps are set into the ceiling (they hung in mid-air a block below it), and
                // two more light the train.
                boolean lamp = (dz % 4 == 2 && dx % 5 == 2 && dz <= 7) || ((dx == 7 || dx == 17) && dz == 10);
                put(c, wx, wy, wz, lamp ? 169 : 98, lamp ? 0 : crack);
                continue;
            }
            // The tunnel mouths at both ends have caved in: rubble under a stone lintel, where there
            // was a flat wall across the track.
            if ((dx == 0 || dx == 22) && dz >= 9 && dz <= 11 && dy >= 1 && dy <= 5) {
                int k = roll(s, dx, dy, dz, 3);
                put(c, wx, wy, wz, dy == 5 ? 98 : k == 0 ? 4 : k == 1 ? 13 : 98, dy == 5 ? 0 : k == 2 ? 2 : 0);
                continue;
            }
            if (shell) { put(c, wx, wy, wz, dy == 0 ? 98 : 159, dy == 0 ? 0 : 8); continue; }
            put(c, wx, wy, wz, 0, 0);
            // Track bed down one side, platform up a step on the other.
            boolean track = dz >= 9;
            boolean train = dx >= 3 && dx <= 15;
            if (track) {
                // Two rails with ballast between (three touching rows kinked into curves); under the
                // car the middle row is the carriage floor.
                if (dy == 1) {
                    if (dz == 10) { put(c, wx, wy, wz, train && dx >= 4 && dx <= 14 ? 44 : 13, 0); continue; }
                    put(c, wx, wy, wz, dx % 9 == 4 ? 13 : 66, 0);
                    continue;
                }
                // The train, stopped with its doors open: a hollow carriage now, not a solid block.
                if (train && dy >= 2 && dy <= 4) {
                    boolean side = dz == 9 || dz == 11;
                    boolean door = dz == 9 && dx % 6 == 1 && dy <= 3;           // doors open onto the platform
                    boolean aisle = dz == 10 && dx >= 4 && dx <= 14 && dy <= 3;
                    if (door) { put(c, wx, wy, wz, 0, 0); continue; }
                    if (aisle) {
                        boolean seat = dy == 2 && (dx == 4 || dx == 14);
                        put(c, wx, wy, wz, seat ? 156 : 0, seat ? (dx == 4 ? 1 : 0) : 0);
                        continue;
                    }
                    // Carriage skin: smooth stone double slab 43:8 (was iron block), a band of windows,
                    // and a cab window at each end.
                    boolean glass = dy == 3 && (side || dx == 3 || dx == 15);
                    put(c, wx, wy, wz, glass ? 20 : 43, glass ? 0 : 8);
                    continue;
                }
                continue;
            }
            // The flooded low end: the water lies in the deck, held by the platform edge and the
            // dry deck beyond (it was a sheet on top of the deck, waiting for an update to run).
            if (dy == 1 && dz >= 1 && dz <= 7) { put(c, wx, wy, wz, dz == 7 ? 155 : dx <= 5 && dz <= 6 ? 9 : 98, 0); continue; }
            if (dy == 2 && dx == 18 && dz == 3) graded(c, s, wx, wy, wz, r, METRO_LOOT, true);
            else if (dy == 2 && dx == 8 && dz == 5) graded(c, s, wx, wy, wz, r, METRO_LOOT, false);
            else if (dy == 2 && dx == 14 && dz == 4) mob(c, s, wx, wy, wz, r, EntityType.CAVE_SPIDER);
            // The zombie spawner sits on the gutter floor (it hovered a block above it).
            else if (dy == 1 && dx == 4 && dz == 8) mob(c, s, wx, wy, wz, r, EntityType.ZOMBIE);
        }
        metroFitOut(c, s, base);
    }

    /**
     * The Lost Metro's way in and its station fittings (structure audit 2026-09-23). The ladder
     * used to start above the roof, a six-block drop over the platform with no way back, and the
     * shaft was only cleared to the site floor, so on higher ground it ended under rock. Now a
     * stone-brick column stands under the collapse, the ladder runs on it from the platform to the
     * real surface, the fallen ceiling lies heaped on the platform, and the mouth has a broken rim.
     */
    private static void metroFitOut(Chunk c, Site s, int base) {
        int x0 = s.x, z0 = s.z, p = base + 2;                   // p: platform feet level
        int top = s.y;
        boolean here = false;
        for (int dx = 9; dx <= 13; dx++) for (int dz = 4; dz <= 8; dz++) {
            int g = ground(c, x0 + dx, z0 + dz, s.hi + 12, base + 8);
            if (g == NONE) continue;
            here = true;
            top = Math.max(top, g);
        }
        if (here) {
            for (int y = base + 8; y <= top + 8; y++) for (int dx = 10; dx <= 12; dx++) for (int dz = 5; dz <= 7; dz++)
                put(c, x0 + dx, y, z0 + dz, 0, 0);
            // Trees over the mouth come down too, so the top of the ladder can be stepped off.
            for (int dx = 9; dx <= 13; dx++) for (int dz = 4; dz <= 8; dz++) {
                int g = ground(c, x0 + dx, z0 + dz, top + 8, base + 8);
                if (g != NONE) clearAbove(c, x0 + dx, z0 + dz, g + 1, top + 8);
            }
            for (int y = p; y <= top; y++) {
                put(c, x0 + 10, y, z0 + 4, 98, roll(s, 10, y - base, 4, 4) == 0 ? 2 : 0);
                put(c, x0 + 10, y, z0 + 5, 65, 3);
            }
            // A broken rim round the mouth, at ground level.
            for (int dx = 9; dx <= 13; dx++) for (int dz = 4; dz <= 8; dz++) {
                boolean ring = dx == 9 || dx == 13 || dz == 4 || dz == 8;
                if (!ring || (dx == 10 && dz == 4)) continue;
                int g = ground(c, x0 + dx, z0 + dz, top + 2, base + 8);
                if (g == NONE || g < base + 8) continue;
                put(c, x0 + dx, g, z0 + dz, roll(s, dx, 1, dz, 2) == 0 ? 98 : 4, roll(s, dx, 1, dz, 2) == 0 ? 2 : 0);
            }
        }
        // The fallen ceiling, heaped on the far side of the ladder.
        for (int dx = 11; dx <= 13; dx++) for (int dz = 4; dz <= 8; dz++) {
            double h = Math.sqrt((dx - 11) * (dx - 11) + (dz - 6) * (dz - 6));
            if (h >= 2.6 || (dx == 11 && dz <= 6)) continue;
            int y = dz == 8 ? base + 1 : p;                       // the gutter is a step lower
            int k = roll(s, dx, 0, dz, 4);
            if (k == 3) continue;
            put(c, x0 + dx, y, z0 + dz, k == 0 ? 13 : k == 1 ? 4 : 98, k == 2 ? 2 : 0);
            if (k == 1 && dz < 8) put(c, x0 + dx, y + 1, z0 + dz, 98, 2);
        }
        // Benches facing the track against the back wall, with slab ends.
        for (int bx : new int[]{7, 15}) {
            put(c, x0 + bx - 1, p, z0 + 1, 44, 7);
            put(c, x0 + bx, p, z0 + 1, 156, 3); put(c, x0 + bx + 1, p, z0 + 1, 156, 3);
            put(c, x0 + bx + 2, p, z0 + 1, 44, 7);
        }
        // A line of columns, the one in the flooded end standing in the water, and a bin.
        for (int y = base + 1; y <= base + 6; y++) put(c, x0 + 5, y, z0 + 4, 155, 2);
        for (int y = p; y <= base + 6; y++) put(c, x0 + 16, y, z0 + 4, 155, 2);
        put(c, x0 + 12, p, z0 + 1, 118, 0);
        // The way out at the east end, choked: two steps up into a plug of rubble.
        put(c, x0 + 19, p, z0 + 2, 109, 0);
        put(c, x0 + 20, p, z0 + 2, 4, 0); put(c, x0 + 20, p + 1, z0 + 2, 109, 0);
        for (int dz = 1; dz <= 3; dz++) for (int y = p; y <= base + 6; y++) {
            int k = roll(s, 21, y - base, dz, 3);
            put(c, x0 + 21, y, z0 + dz, k == 0 ? 13 : k == 1 ? 4 : 98, k == 2 ? 2 : 0);
        }
    }

    // == 12. The Impossible Stair ================================================
    // A single chamber, cut into the rock, in which the stairs do not agree. Flights leave
    // every wall and arrive at the ceiling; doorways open onto drops; the same landing is
    // the top of one stair and the underside of another. Nothing here is load-bearing in
    // any sense a builder would recognise.

    private static final int[][] STAIR_LOOT = {
        {368, 0, 12, 2, 6},  {345, 0, 10, 1, 2}, {347, 0, 10, 1, 2}, {388, 0, 12, 3, 9},
        {381, 0,  8, 1, 3},  {109, 0, 9, 1, 1}, {322, 0,  8, 1, 2}, {264, 0,  7, 1, 3},
        {163, 0, 9, 3, 9},  {358, 0,  7, 1, 1}, {134, 0, 9, 2, 7}, {175, 0,  6, 1, 2},
    };

    private static void stair(World w, Chunk c, Terrain t) {
        Site s = site(t, c, STAIR_CELL, 0x53544149L, 21, 21, 10, Megaliths.RANK_STAIR);
        if (s == null) return;
        Random r = new Random(s.seed);
        int base = s.y - 22;
        if (base < 6) return;
        int H = 19;
        for (int dx = 0; dx < 21; dx++) for (int dz = 0; dz < 21; dz++) for (int dy = 0; dy < H; dy++) {
            int wx = s.x + dx, wy = base + dy, wz = s.z + dz;
            boolean shell = dx == 0 || dx == 20 || dz == 0 || dz == 20 || dy == 0 || dy == H - 1;
            if (shell) {
                // Doorways in the walls at heights that cannot all be floors -- two high now, so
                // they read as doors rather than holes (the upper block still draws, as before).
                boolean place = (dx == 0 || dx == 20) ? (dz % 7 == 3) : (dx % 7 == 3);
                boolean doorway = dy % 6 == 2 && place && dy != 0 && dy != H - 1;
                boolean ceilingDoor = dy == H - 1 && ((dx == 5 && dz == 15) || (dx == 15 && dz == 5));
                if (doorway || ceilingDoor) { put(c, wx, wy, wz, 0, 0); continue; }
                int brick = r.nextInt(7) == 0 ? 1 : r.nextInt(9) == 0 ? 2 : 0;
                boolean head = dy % 6 == 3 && place && dy != H - 1;
                put(c, wx, wy, wz, head ? 0 : 98, head ? 0 : brick);
                continue;
            }
            put(c, wx, wy, wz, 0, 0);
        }
        // (structure audit 2026-09-23) Each doorway is a blind door: stone brick close behind it,
        // so none opens onto raw rock or a cave, and the low and middle ones hold a lamp.
        for (int dy = 2; dy < H - 1; dy += 6) for (int p = 3; p < 20; p += 7) {
            int[][] doors = {{0, p, -1, 0, 1}, {20, p, 1, 0, 2}, {p, 0, 0, -1, 3}, {p, 20, 0, 1, 4}};
            for (int[] d : doors) {
                for (int y = 0; y <= 1; y++) put(c, s.x + d[0] + d[2], base + dy + y, s.z + d[1] + d[3], 98, y == 1 ? 2 : 0);
                if (dy <= 8) put(c, s.x + d[0], base + dy, s.z + d[1], 50, d[4]);
            }
        }
        // Six flights, each obeying its own idea of down.
        flight(c, s, base, 2, 2, 1, 1, 0, 16, 109, 0);       // rising north-east
        flight(c, s, base, 18, 2, -1, 1, 4, 14, 109, 1);     // rising north-west, from halfway up
        flight(c, s, base, 2, 18, 1, -1, 8, 12, 109, 2);     // rising south-east, higher still
        flight(c, s, base, 18, 18, -1, -1, 12, 8, 109, 3);   // and one that arrives at the ceiling
        flight(c, s, base, 10, 1, 0, 1, 14, 12, 109, 2);     // straight up the middle of a wall
        flight(c, s, base, 1, 10, 1, 0, 6, 12, 109, 0);      // and across it
        // Landings that belong to two stairs at once. Each lamp stands in a corner of its landing
        // (on the centre, where it was, the chest or spawner put there afterwards replaced it).
        for (int[] pad : STAIR_PADS) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                put(c, s.x + pad[0] + dx, base + pad[2], s.z + pad[1] + dz, 98, 0);
            }
            put(c, s.x + pad[3], base + pad[2] + 1, s.z + pad[4], 50, 5);
        }
        stairRoute(c, s, base);
        graded(c, s, s.x + 10, base + 9, s.z + 10, r, STAIR_LOOT, true);
        graded(c, s, s.x + 6, base + 7, s.z + 6, r, STAIR_LOOT, false);
        graded(c, s, s.x + 14, base + 5, s.z + 14, r, STAIR_LOOT, false);
        mob(c, s, s.x + 14, base + 11, s.z + 6, r, EntityType.SKELETON);
        mob(c, s, s.x + 6, base + 13, s.z + 14, r, EntityType.SPIDER);
        // The enderman spawner hung in mid-air above the middle landing, held only by the steps of
        // two flights; it stands on the chamber floor under that landing now, with room for them.
        mob(c, s, s.x + 10, base + 1, s.z + 10, r, EntityType.ENDERMAN);
        // The way in: one of the two doors in the ceiling is a shaft to daylight, and a ladder on
        // a stone-brick pillar comes down from it to the north-east landing.
        for (int y = base + 11; y <= base + 17; y++) {
            put(c, s.x + 15, y, s.z + 4, 98, roll(s, 15, y - base, 4, 4) == 0 ? 2 : 0);
            put(c, s.x + 15, y, s.z + 5, 65, 3);
        }
        shaft(c, t, s.x + 15, s.z + 5, base + 18, 98, 0);
    }

    /**
     * A ladder shaft from a buried ceiling up to daylight, lined in the chamber's own stone. The
     * top rung is level with the ground round the mouth and the lining ends flush there, so the
     * climber steps straight out onto the ground (a lip standing a block proud of the surface,
     * with the ladder a block short of it, could not be climbed over). The ladder hangs on the
     * lining's north side. Anything growing over the mouth is cleared.
     */
    private static void shaft(Chunk c, Terrain t, int wx, int wz, int fromY, int wallId, int wallData) {
        int top = t.sample(wx, wz).y;
        if (top < fromY) return;
        for (int y = fromY; y <= top; y++) {
            put(c, wx, y, wz, 65, 3);
            put(c, wx, y, wz - 1, wallId, wallData);
            put(c, wx, y, wz + 1, wallId, wallData);
            put(c, wx - 1, y, wz, wallId, wallData);
            put(c, wx + 1, y, wz, wallId, wallData);
        }
        clearAbove(c, wx, wz, top + 1, top + 8);
    }

    /** The landings: centre x, centre z, height, and the corner their lamp stands in. */
    private static final int[][] STAIR_PADS = {
        {6, 6, 6, 4, 4}, {14, 6, 10, 16, 8}, {6, 14, 12, 4, 16}, {14, 14, 4, 16, 16}, {10, 10, 8, 8, 12},
    };

    /**
     * One true way round the Impossible Stair (structure audit 2026-09-23). The six flights stay
     * as they are, and none of them agrees with any other; this is the plain masonry stair that
     * somebody built afterwards so the landings could be reached and left: from the floor up to
     * the low landing, on up to the middle one, one step up to the landing under the way in, a
     * step down to the north-west landing, and a walk out onto the flight across the west wall,
     * which is the only one that arrives anywhere -- at the high south-west landing.
     */
    private static void stairRoute(Chunk c, Site s, int base) {
        int x0 = s.x, z0 = s.z;
        // Floor to the low (south-east) landing: three steps up to its north edge on a solid base.
        for (int i = 0; i < 3; i++) for (int dx = 13; dx <= 14; dx++) {
            for (int y = 1; y <= i; y++) put(c, x0 + dx, base + y, z0 + 9 + i, 98, 0);
            put(c, x0 + dx, base + 1 + i, z0 + 9 + i, 109, 2);
        }
        // Low landing to the middle one: three steps west off its west edge, on a solid base.
        for (int i = 0; i < 3; i++) for (int dz = 13; dz <= 14; dz++) {
            for (int y = 1; y <= 4 + i; y++) put(c, x0 + 11 - i, base + y, z0 + dz, 98, roll(s, 11 - i, y, dz, 6) == 0 ? 2 : 0);
            put(c, x0 + 11 - i, base + 5 + i, z0 + dz, 109, 1);
        }
        // Middle landing to the north-east landing, where the ladder comes down: one step.
        put(c, x0 + 12, base + 9, z0 + 9, 109, 3);
        // North-west landing up to the middle one, and a walk from there to the west-wall flight.
        put(c, x0 + 7, base + 7, z0 + 8, 109, 2);
        for (int dx = 4; dx <= 7; dx++) put(c, x0 + dx, base + 8, z0 + 9, 98, 0);
    }

    /** One flight of steps, walked out from a corner in whatever direction it believes in. */
    private static void flight(Chunk c, Site s, int base, int x, int z, int sx, int sz,
                               int startY, int steps, int id, int data) {
        for (int n = 0; n < steps; n++) {
            int wx = s.x + x + sx * n, wz = s.z + z + sz * n, wy = base + startY + n;
            if (wy < base + 1 || wy > base + 17) continue;
            // A step with a landing a block or two over it is a stub nobody can stand on: where a
            // landing cuts a flight, the flight stops at its underside (structure audit 2026-09-23).
            if (!underPad(x + sx * n, z + sz * n, startY + n)) put(c, wx, wy, wz, id, data);
            // A step is no use without something beside it to stand on.
            int nx = x + sx * n + (sz != 0 ? 1 : 0), nz = z + sz * n + (sx != 0 ? 1 : 0);
            if (!underPad(nx, nz, startY + n)) put(c, s.x + nx, wy, s.z + nz, id, data);
        }
    }

    /** True when a landing lies one or two blocks above this chamber cell. */
    private static boolean underPad(int dx, int dz, int dy) {
        for (int[] pad : STAIR_PADS) {
            int gap = pad[2] - dy;
            if ((gap == 1 || gap == 2) && Math.abs(dx - pad[0]) <= 2 && Math.abs(dz - pad[1]) <= 2) return true;
        }
        return false;
    }

    // -- placement ---------------------------------------------------------------

    /**
     * Where a landmark stands.
     *
     * The rooms in Dungeons demand ground that is already flat, which is fine at eleven
     * blocks across and hopeless at thirty: almost nothing in this terrain is level over
     * that distance, so the same test would simply never fire. This one accepts a good deal
     * of slope and reports the range back, and every builder below is expected to pack its
     * own foundation down to the low corner rather than float or bury itself.
     */
    static Site site(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int maxSlope, int rank) {
        int cx = c.getX(), cz = c.getZ();
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                int acx = cx + ox, acz = cz + oz;
                if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) continue;
                if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) continue;
                int x = acx * 16 + 1, z = acz * 16 + 1;
                if (x + sizeX <= cx * 16 || x > cx * 16 + 15) continue;
                if (z + sizeZ <= cz * 16 || z > cz * 16 + 15) continue;
                int lo = 999, hi = -999;
                for (int sx = 0; sx < sizeX; sx += 4) {
                    for (int sz = 0; sz < sizeZ; sz += 4) {
                        int h = t.sample(x + sx, z + sz).y;
                        if (h < lo) lo = h;
                        if (h > hi) hi = h;
                    }
                }
                if (hi - lo > maxSlope) continue;
                if (lo < 64 || hi > 136) continue;      // out of the sea, off the peaks
                // Something rarer may already have this ground; see Megaliths.claimed.
                if (Megaliths.claimed(t, x, z, sizeX, sizeZ, rank)) continue;
                long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
                return new Site(x, lo + 1, z, lo, hi, seed, sizeX, sizeZ);
            }
        }
        return secondary(t, cx, cz, cell, salt, sizeX, sizeZ, maxSlope, rank, 0);
    }

    /**
     * site() / siteNear() on the secondary lattice (3.25.0, StructureRates): asked only when no primary site
     * reaches this chunk; the same slope and height tests, and Megaliths.secondaryFree in place of claimed().
     */
    private static Site secondary(Terrain t, int cx, int cz, int cell, long salt, int sizeX, int sizeZ, int maxSlope,
                                  int rank, int margin) {
        int cell2 = Megaliths.cell2(rank, cell);
        long salt2 = Megaliths.salt2(rank, salt);
        int span = ((Math.max(sizeX, sizeZ) + margin) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                int acx = cx + ox, acz = cz + oz;
                if (Math.floorMod(acx, cell2) != Math.floorMod(Terrain.mix(t.seed + salt2) >>> 3, cell2)) continue;
                if (Math.floorMod(acz, cell2) != Math.floorMod(Terrain.mix(t.seed + salt2 + 17L) >>> 3, cell2)) continue;
                int x = acx * 16 + 1, z = acz * 16 + 1;
                if (x + sizeX + margin <= cx * 16 || x - margin > cx * 16 + 15) continue;
                if (z + sizeZ + margin <= cz * 16 || z - margin > cz * 16 + 15) continue;
                int lo = 999, hi = -999;
                for (int sx = 0; sx < sizeX; sx += 4) {
                    for (int sz = 0; sz < sizeZ; sz += 4) {
                        int h = t.sample(x + sx, z + sz).y;
                        if (h < lo) lo = h;
                        if (h > hi) hi = h;
                    }
                }
                if (hi - lo > maxSlope) continue;
                if (lo < 64 || hi > 136) continue;
                if (!Megaliths.secondaryFree(t, x, z, sizeX, sizeZ, rank)) continue;
                long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt2);
                return new Site(x, lo + 1, z, lo, hi, seed, sizeX, sizeZ);
            }
        }
        return null;
    }

    /** Local chunk column for a world column, or -1 when it is not in this chunk. */
    private static int lx(Chunk c, int wx) { int v = wx - c.getX() * 16; return v < 0 || v > 15 ? -1 : v; }
    private static int lz(Chunk c, int wz) { int v = wz - c.getZ() * 16; return v < 0 || v > 15 ? -1 : v; }

    private static void put(Chunk c, int wx, int wy, int wz, int id, int data) {
        int x = lx(c, wx), z = lz(c, wz);
        if (x < 0 || z < 0 || wy < 1 || wy > 250) return;
        Dungeons.set(c, x, wy, z, id, data);
    }

    private static Block blockAt(Chunk c, int wx, int wy, int wz) {
        int x = lx(c, wx), z = lz(c, wz);
        if (x < 0 || z < 0 || wy < 1 || wy > 250) return null;
        return c.getBlock(x, wy, z);
    }

    /**
     * Packs a solid column from the site floor down, so nothing is left hanging over a dip.
     * It fills down to the ground and stops there (structure audit 2026-09-23): driven a fixed
     * six blocks below the low corner whatever was there, it hung its plug into caves under
     * the site. The floor cell itself is always laid. (Merge fix-up: it also gave up six under
     * the low corner when no ground had been met, leaving a stub hanging over a cave mouth or a
     * cliff; it now carries on down to the ground when that is within Megaliths.DROP more blocks,
     * and over a deeper cave leaves its last course as the cave's roof, as Megaliths.footing does.)
     */
    private static void footing(Chunk c, Site s, int wx, int wz, int id, int data) {
        if (blockAt(c, wx, 64, wz) == null) return;
        int y = s.y - 1;
        for (; y >= s.lo - 6; y--) {
            if (y < s.y - 1 && solidAt(c, wx, y, wz)) return;
            put(c, wx, y, wz, id, data);
        }
        int foot = Megaliths.reach(c, wx, y, wz);
        for (; y > foot; y--) put(c, wx, y, wz, id, data);
    }

    // -- repair helpers (structure audit 2026-09-23) -------------------------------
    // Local to this file: they read and write only the chunk being populated, draw nothing
    // from a builder's Random (so every draw sequence above is unchanged), and never take part
    // in deciding where a landmark stands.

    /** What ground() returns for a column with nothing solid in range, or outside this chunk. */
    private static final int NONE = Integer.MIN_VALUE;

    /** A fixed per-cell roll in [0, n), for detail that must not shift a builder's draws. */
    private static int roll(Site s, int a, int b, int c, int n) {
        long h = Terrain.mix(s.seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L);
        return (int) Math.floorMod(h, (long) n);
    }

    /** Not ground: air, liquids, plants, leaves and trunks, snow cover, webs, torches, fire. */
    private static boolean soft(int id) {
        switch (id) {
            case 0: case 6: case 8: case 9: case 10: case 11: case 17: case 18: case 30: case 31:
            case 32: case 37: case 38: case 39: case 40: case 50: case 51: case 59: case 78: case 81:
            case 83: case 106: case 111: case 161: case 162: case 175:
                return true;
            default:
                return false;
        }
    }

    /** A solid block at a world cell. Cells outside this chunk count as solid (unknown: keep). */
    private static boolean solidAt(Chunk c, int wx, int wy, int wz) {
        Block b = blockAt(c, wx, wy, wz);
        return b == null || !soft(b.getTypeId());
    }

    /** The highest solid block of a column between yTop and yBottom (trees do not count), or NONE. */
    private static int ground(Chunk c, int wx, int wz, int yTop, int yBottom) {
        for (int y = yTop; y >= yBottom; y--) {
            Block b = blockAt(c, wx, y, wz);
            if (b == null) return NONE;
            if (!soft(b.getTypeId())) return y;
        }
        return NONE;
    }

    /** Clears a column to air from y0 to y1 inclusive: the bank, trees and snow inside a footprint. */
    private static void clearAbove(Chunk c, int wx, int wz, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            Block b = blockAt(c, wx, y, wz);
            if (b != null && b.getTypeId() != 0) put(c, wx, y, wz, 0, 0);
        }
    }

    /**
     * Steps out of a doorway. (wx, wz) is the threshold, whose feet level is fy; the steps walk
     * out along (sx, sz), at most n of them. Where the ground outside stands above the floor they
     * are cut up into the bank, with the bank cleared over them; where it falls away two or more
     * they go down to it on a packed base. On level ground nothing is placed. The stair data is
     * the one that rises away from the door.
     */
    private static void steps(Chunk c, int wx, int wz, int sx, int sz, int fy, int n, int id, int data) {
        int baseId = id == 109 ? 98 : id == 114 ? 112 : id == 156 ? 155 : id == 108 ? 45 : id == 67 ? 4 : 5;
        int baseData = id == 134 ? 1 : 0;
        int first = ground(c, wx + sx, wz + sz, fy + n + 4, fy - n - 6);
        if (first == NONE) return;
        if (first >= fy) {
            for (int i = 1; i <= n; i++) {
                int x = wx + sx * i, z = wz + sz * i, y = fy + i - 1;
                int g = ground(c, x, z, fy + n + 6, fy - 2);
                if (g == NONE || g < y || !solidAt(c, x, y - 1, z)) return;
                put(c, x, y, z, id, data);
                for (int a = y + 1; a <= Math.max(g, y + 2); a++) put(c, x, a, z, 0, 0);
            }
        } else if (first <= fy - 3) {
            for (int i = 1; i <= n; i++) {
                int x = wx + sx * i, z = wz + sz * i, y = fy - 1 - i;
                int g = ground(c, x, z, fy + 2, fy - n - 8);
                if (g == NONE || g >= y) return;
                for (int a = g + 1; a < y; a++) put(c, x, a, z, baseId, baseData);
                put(c, x, y, z, id, data ^ 1);                       // rising back towards the door
            }
        }
    }

    /**
     * The same site() answer for a chunk that lies within `margin` blocks of a landmark's footprint
     * without overlapping it -- for outside dressing (a rubble skirt, steps) that spills over the
     * chunk edge. It repeats site()'s lattice, slope, height and claim tests exactly; site() itself
     * is untouched, so where landmarks stand and how they are recognised does not change.
     */
    static Site siteNear(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int maxSlope, int rank, int margin) {
        int cx = c.getX(), cz = c.getZ();
        int span = ((Math.max(sizeX, sizeZ) + margin) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                int acx = cx + ox, acz = cz + oz;
                if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) continue;
                if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) continue;
                int x = acx * 16 + 1, z = acz * 16 + 1;
                if (x + sizeX + margin <= cx * 16 || x - margin > cx * 16 + 15) continue;
                if (z + sizeZ + margin <= cz * 16 || z - margin > cz * 16 + 15) continue;
                int lo = 999, hi = -999;
                for (int sx = 0; sx < sizeX; sx += 4) {
                    for (int sz = 0; sz < sizeZ; sz += 4) {
                        int h = t.sample(x + sx, z + sz).y;
                        if (h < lo) lo = h;
                        if (h > hi) hi = h;
                    }
                }
                if (hi - lo > maxSlope) continue;
                if (lo < 64 || hi > 136) continue;
                if (Megaliths.claimed(t, x, z, sizeX, sizeZ, rank)) continue;
                long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
                return new Site(x, lo + 1, z, lo, hi, seed, sizeX, sizeZ);
            }
        }
        return secondary(t, cx, cz, cell, salt, sizeX, sizeZ, maxSlope, rank, margin);
    }

    // -- danger and reward -------------------------------------------------------

    private static void mob(Chunk c, Site s, int wx, int wy, int wz, Random r, EntityType type) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return;
        Dungeons.set(c, lx(c, wx), wy, lz(c, wz), 52, 0);
        try {
            org.bukkit.block.CreatureSpawner cs = (org.bukkit.block.CreatureSpawner) b.getState();
            cs.setSpawnedType(type);
            cs.update(true, false);
        } catch (RuntimeException ignored) { }
    }

}
