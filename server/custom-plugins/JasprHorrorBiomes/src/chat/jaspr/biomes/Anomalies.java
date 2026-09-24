package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import static chat.jaspr.biomes.Megaliths.Site;
import static chat.jaspr.biomes.Megaliths.deep;
import static chat.jaspr.biomes.Megaliths.fill;
import static chat.jaspr.biomes.Megaliths.footing;
import static chat.jaspr.biomes.Megaliths.ground;
import static chat.jaspr.biomes.Megaliths.graded;
import static chat.jaspr.biomes.Megaliths.mob;
import static chat.jaspr.biomes.Megaliths.put;
import static chat.jaspr.biomes.Megaliths.riser;
import static chat.jaspr.biomes.Megaliths.shell;

/**
 * Anomalies: the sites that are filed rather than built.
 *
 * A containment wing with six occupied cells, a shop with no way out of it, a stairwell
 * that goes further than the rock it is cut into, a corroded place that is not really a
 * place, a cabin with a lift under the cellar, and a laboratory complex under a mountain
 * that sealed itself with everybody still inside.
 *
 * They share Megaliths' placement and block helpers and nothing else: every one of them
 * has its own loot table and its own way of going wrong.
 */
public final class Anomalies {
    private Anomalies() { }

    private static final int SITE19_CELL = 118, STORE_CELL = 108, STAIRWELL_CELL = 71;
    private static final int POCKET_CELL = 96, LODGE_CELL = 83, HIVE_CELL = 102;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        site19(c, t);
        store3008(c, t);
        stairwell(c, t);
        pocket(c, t);
        lodge(c, t);
        hive(c, t);
    }

    /**
     * graded(), with each box rolling from its own stream (structure audit 2026-09-23). Every chunk
     * starts the builder's Random over from the site seed and graded() only draws for the boxes in
     * that chunk, so the first box in one chunk rolled exactly what the first box in the next chunk
     * rolled. Same tables, same tiers; the stream is now keyed to where the box stands.
     */
    private static void box(Chunk c, Site s, int wx, int wy, int wz, int[][] pool, boolean gun) {
        long key = s.seed ^ (wx * 341873128712L) ^ (wz * 132897987541L) ^ ((long) wy << 40);
        graded(c, s, wx, wy, wz, new Random(key), pool, gun);
    }

    // == 12. Site-19 =============================================================
    // A containment wing with the power still on. One hall, observation glass down both
    // sides, and six cells with six different reasons for the glass being that thick.
    // The staff went out through the north stair and did not come back for the keys.

    private static final int[][] SITE19_LOOT = {
        {397, 0, 7, 1, 1}, {340, 0, 13, 3,  9}, {339, 0, 14, 6, 18}, {386, 0,  9, 1, 2},
        {130, 0,  4, 1,  1}, {368, 0,  8, 1,  3}, {381, 0,  6, 1,  2}, {116, 0,  3, 1, 1},
        {384, 0,  9, 2,  8}, { 89, 0, 10, 2,  6}, {264, 0,  8, 1,  3}, {306, 0,  7, 1, 1},
    };

    /** One cell off the hall: a glass front, a light, and whatever it holds. */
    private static void cell(Chunk c, int bx, int by, int bz, int wall, int data, boolean north) {
        shell(c, bx, by, bz, bx + 10, by + 7, bz + 10, wall, data);
        fill(c, bx + 1, by, bz + 1, bx + 9, by + 6, bz + 9, 0, 0);
        fill(c, bx + 1, by, bz + 1, bx + 9, by, bz + 9, 251, 8);
        int face = north ? bz + 10 : bz;
        fill(c, bx + 2, by + 1, face, bx + 8, by + 5, face, 95, 0);
        fill(c, bx + 4, by + 1, face, bx + 6, by + 3, face, 0, 0);
        // The way in is a hatch, not a hole (structure audit 2026-09-23): a bar across the top of the
        // opening and the release button on the hall side of the frame.
        fill(c, bx + 4, by + 3, face, bx + 6, by + 3, face, 101, 0);
        put(c, bx + 9, by + 2, north ? face + 1 : face - 1, 77, north ? 3 : 4);
        put(c, bx + 5, by + 6, bz + 5, 89, 0);
    }

    private static void site19(Chunk c, Terrain t) {
        Site s = deep(t, c, SITE19_CELL, 0x53313900L, 53, 45, 20, Megaliths.RANK_SITE19);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 52, z1 = z0 + 44, y1 = s.y + 19;
        fill(c, x0, y0, z0, x1, y1, z1, 251, 8);                     // poured solid, then cut
        // The hall: nine wide, four high, running the length of the wing.
        fill(c, x0 + 1, y0 + 1, z0 + 18, x1 - 1, y0 + 6, z0 + 26, 0, 0);
        fill(c, x0 + 1, y0 + 1, z0 + 18, x1 - 1, y0 + 1, z0 + 26, 251, 0);
        fill(c, x0 + 1, y0 + 1, z0 + 22, x1 - 1, y0 + 1, z0 + 22, 251, 4);
        for (int dx = 4; dx <= 48; dx += 6) {
            put(c, x0 + dx, y0 + 6, z0 + 20, 89, 0);
            put(c, x0 + dx, y0 + 6, z0 + 24, 89, 0);
            // Pilasters: smooth stone double slab 43:8 (was iron block).
            fill(c, x0 + dx, y0 + 2, z0 + 18, x0 + dx, y0 + 5, z0 + 18, 43, 8);
            fill(c, x0 + dx, y0 + 2, z0 + 26, x0 + dx, y0 + 5, z0 + 26, 43, 8);
        }
        // Six cells, three a side, all of them still sealed.
        int[][] cells = {{x0 + 2, z0 + 7}, {x0 + 21, z0 + 7}, {x0 + 40, z0 + 7},
                         {x0 + 2, z0 + 27}, {x0 + 21, z0 + 27}, {x0 + 40, z0 + 27}};
        // 1. The one that only moves when the glass is not being watched.
        cell(c, cells[0][0], y0 + 1, cells[0][1], 251, 8, true);
        fill(c, cells[0][0] + 7, y0 + 1, cells[0][1] + 2, cells[0][0] + 8, y0 + 3, cells[0][1] + 3, 155, 0);
        put(c, cells[0][0] + 7, y0 + 4, cells[0][1] + 2, 155, 1);
        for (int i = 0; i < 9; i++)
            put(c, cells[0][0] + 1 + r.nextInt(8), y0 + 1, cells[0][1] + 1 + r.nextInt(8), 159, 14);
        mob(c, cells[0][0] + 5, y0 + 1, cells[0][1] + 5, EntityType.ZOMBIE);
        // 2. The one you must not look at.
        cell(c, cells[1][0], y0 + 1, cells[1][1], 251, 8, true);
        // It sits on a bench standing on the floor (the slab used to be sunk into the floor course).
        put(c, cells[1][0] + 5, y0 + 2, cells[1][1] + 5, 44, 15);
        put(c, cells[1][0] + 5, y0 + 3, cells[1][1] + 5, 35, 0);
        for (int dz = 2; dz <= 8; dz++) put(c, cells[1][0] + 1, y0 + 2, cells[1][1] + dz, 159, 14);
        mob(c, cells[1][0] + 2, y0 + 1, cells[1][1] + 2, EntityType.HUSK);
        mob(c, cells[1][0] + 8, y0 + 1, cells[1][1] + 8, EntityType.HUSK);
        // 3. The acid tank, which is the third one they built.
        cell(c, cells[2][0], y0 + 1, cells[2][1], 49, 0, true);
        // A tank, not an open pit (structure audit 2026-09-23): glass sides round the lava as well as
        // the lid over it, still lava inside, and the walkway ring outside the glass.
        fill(c, cells[2][0] + 2, y0 + 2, cells[2][1] + 2, cells[2][0] + 8, y0 + 3, cells[2][1] + 8, 20, 0);
        fill(c, cells[2][0] + 3, y0 + 1, cells[2][1] + 3, cells[2][0] + 7, y0 + 3, cells[2][1] + 7, 11, 0);
        fill(c, cells[2][0] + 2, y0 + 4, cells[2][1] + 2, cells[2][0] + 8, y0 + 4, cells[2][1] + 8, 20, 0);
        mob(c, cells[2][0] + 5, y0 + 5, cells[2][1] + 5, EntityType.MAGMA_CUBE);
        mob(c, cells[2][0] + 2, y0 + 5, cells[2][1] + 8, EntityType.BLAZE);
        // 4. The surgery, and the doctor's own instruments.
        cell(c, cells[3][0], y0 + 1, cells[3][1], 251, 8, false);
        // Tables, sheet and instruments stand on the floor (they used to be set into the floor course).
        for (int i = 0; i < 3; i++) {
            fill(c, cells[3][0] + 2 + i * 3, y0 + 2, cells[3][1] + 3, cells[3][0] + 3 + i * 3, y0 + 2, cells[3][1] + 6, 155, 0);
            put(c, cells[3][0] + 2 + i * 3, y0 + 3, cells[3][1] + 3, 171, 14);
        }
        put(c, cells[3][0] + 8, y0 + 2, cells[3][1] + 8, 117, 0);
        put(c, cells[3][0] + 8, y0 + 2, cells[3][1] + 7, 118, 0);
        mob(c, cells[3][0] + 5, y0 + 1, cells[3][1] + 8, EntityType.ZOMBIE_VILLAGER);
        // 5. The clockworks: two chambers, brass, and a lever with four settings.
        // Brass is yellow terracotta 159:4 (was gold block); the pistons never needed it.
        cell(c, cells[4][0], y0 + 1, cells[4][1], 159, 4, false);
        fill(c, cells[4][0] + 5, y0 + 1, cells[4][1] + 1, cells[4][0] + 5, y0 + 6, cells[4][1] + 9, 159, 4);
        fill(c, cells[4][0] + 5, y0 + 1, cells[4][1] + 5, cells[4][0] + 5, y0 + 2, cells[4][1] + 5, 0, 0);
        for (int dz = 2; dz <= 8; dz += 2) {
            put(c, cells[4][0] + 4, y0 + 3, cells[4][1] + dz, 33, 1);
            put(c, cells[4][0] + 6, y0 + 3, cells[4][1] + dz, 29, 3);
        }
        put(c, cells[4][0] + 1, y0 + 3, cells[4][1] + 2, 69, 1);    // on the brass wall (it hung on air)
        box(c, s, cells[4][0] + 8, y0 + 2, cells[4][1] + 8, SITE19_LOOT, true);   // on the floor, not in it
        // 6. The friendly one. Nothing in here has ever hurt anybody.
        cell(c, cells[5][0], y0 + 1, cells[5][1], 95, 1, false);
        fill(c, cells[5][0] + 3, y0 + 1, cells[5][1] + 3, cells[5][0] + 7, y0 + 2, cells[5][1] + 7, 165, 0);
        put(c, cells[5][0] + 5, y0 + 3, cells[5][1] + 5, 89, 0);
        mob(c, cells[5][0] + 5, y0 + 1, cells[5][1] + 1, EntityType.SLIME);
        box(c, s, cells[5][0] + 1, y0 + 2, cells[5][1] + 1, SITE19_LOOT, false);
        // Control room at the west end, armoury at the east, stair out of the north wall.
        fill(c, x0 + 1, y0 + 8, z0 + 18, x0 + 14, y0 + 13, z0 + 26, 0, 0);
        fill(c, x0 + 1, y0 + 8, z0 + 18, x0 + 14, y0 + 8, z0 + 26, 251, 7);
        // The west wall is a bank of dead monitors (black glass; clear glass there looked onto concrete).
        fill(c, x0 + 1, y0 + 9, z0 + 19, x0 + 1, y0 + 12, z0 + 25, 95, 15);
        for (int dz = 19; dz <= 25; dz += 2) {
            put(c, x0 + 3, y0 + 9, z0 + dz, 251, 15);
            put(c, x0 + 3, y0 + 10, z0 + dz, 95, 5);
            put(c, x0 + 4, y0 + 9, z0 + dz, 156, 0);                  // a chair at each console
        }
        put(c, x0 + 8, y0 + 13, z0 + 22, 89, 0);
        put(c, x0 + 4, y0 + 13, z0 + 22, 89, 0);                      // and a light over them
        // An observation strip: glass through the floor and the hall ceiling, looking down the hall.
        fill(c, x0 + 7, y0 + 7, z0 + 20, x0 + 9, y0 + 8, z0 + 24, 95, 0);
        fill(c, x0 + 13, y0 + 2, z0 + 22, x0 + 14, y0 + 8, z0 + 22, 0, 0);
        fill(c, x0 + 13, y0 + 2, z0 + 22, x0 + 13, y0 + 8, z0 + 22, 65, 4);
        // The ladder hangs on a service post (it used to stand free in the hall, and the slot beside
        // it was a hole in the control-room floor).
        fill(c, x0 + 14, y0 + 2, z0 + 22, x0 + 14, y0 + 8, z0 + 22, 251, 7);
        box(c, s, x0 + 6, y0 + 9, z0 + 20, SITE19_LOOT, false);
        box(c, s, x0 + 10, y0 + 9, z0 + 24, SITE19_LOOT, true);
        fill(c, x1 - 12, y0 + 8, z0 + 18, x1 - 1, y0 + 12, z0 + 26, 0, 0);
        fill(c, x1 - 12, y0 + 8, z0 + 18, x1 - 1, y0 + 8, z0 + 26, 251, 7);
        for (int dz = 19; dz <= 25; dz += 2) put(c, x1 - 2, y0 + 9, z0 + dz, 43, 8);   // racks: 43:8, was iron
        put(c, x1 - 6, y0 + 12, z0 + 22, 89, 0);
        // Armoury fittings (structure audit 2026-09-23; it was four grey blocks and a chest): locker
        // doors on the east wall between the racks, an issue counter across the room with its end
        // open, ammunition drawers in the side walls and a second light at the ladder end.
        for (int dz = 20; dz <= 24; dz += 2) fill(c, x1 - 1, y0 + 9, z0 + dz, x1 - 1, y0 + 10, z0 + dz, 167, 6);
        fill(c, x1 - 7, y0 + 9, z0 + 18, x1 - 7, y0 + 9, z0 + 24, 251, 7);
        fill(c, x1 - 7, y0 + 10, z0 + 18, x1 - 7, y0 + 10, z0 + 24, 44, 0);
        for (int dx = 3; dx <= 5; dx += 2) {
            put(c, x1 - dx, y0 + 10, z0 + 17, 23, 3);
            put(c, x1 - dx, y0 + 10, z0 + 27, 23, 2);
        }
        put(c, x1 - 10, y0 + 12, z0 + 22, 89, 0);
        fill(c, x1 - 13, y0 + 2, z0 + 22, x1 - 12, y0 + 8, z0 + 22, 0, 0);
        fill(c, x1 - 12, y0 + 2, z0 + 22, x1 - 12, y0 + 8, z0 + 22, 65, 5);
        fill(c, x1 - 13, y0 + 2, z0 + 22, x1 - 13, y0 + 8, z0 + 22, 251, 7);   // its service post
        box(c, s, x1 - 4, y0 + 9, z0 + 20, SITE19_LOOT, true);
        mob(c, x1 - 8, y0 + 9, z0 + 24, EntityType.ZOMBIE);
        mob(c, x0 + 26, y0 + 1, z0 + 22, EntityType.ZOMBIE);
        mob(c, x0 + 34, y0 + 1, z0 + 20, EntityType.SKELETON);
        // The way out: the shaft ladder comes down a service post to the hall floor (structure audit
        // 2026-09-23: it used to stop at the hall ceiling, five blocks over the floor, one way only).
        fill(c, x0 + 26, y0 + 7, z0 + 22, x0 + 27, y1 - 1, z0 + 23, 0, 0);
        fill(c, x0 + 26, y0 + 2, z0 + 21, x0 + 26, y0 + 6, z0 + 21, 251, 7);
        fill(c, x0 + 26, y0 + 2, z0 + 22, x0 + 26, y1 - 1, z0 + 22, 65, 3);   // same face as the riser's rungs
        riser(c, t, x0 + 26, z0 + 22, y1, 251, 8);
    }

    // == 13. The Store ===========================================================
    // A retail floor the size of a village, lit evenly from a ceiling that has no lights
    // in it, laid out as a one-way route through showrooms nobody will ever buy. The
    // doors at the front open onto more store. The staff are still on shift.

    private static final int[][] STORE_LOOT = {
        {355, 0, 14, 1,  3}, {  5, 0, 15, 12, 32}, { 53, 0, 12, 6, 18}, { 47, 0, 11, 2, 8},
        { 58, 0,  9, 1,  2}, {171, 11, 13, 6, 18}, {35, 11, 13, 6, 18}, {390, 0, 10, 2, 8},
        {323, 0,  9, 2,  6}, {280, 0, 12,  8, 24}, {297, 0, 10, 2,  6}, {338, 0,  8, 2, 8},
    };

    private static void store3008(Chunk c, Terrain t) {
        Site s = deep(t, c, STORE_CELL, 0x33303038L, 45, 45, 14, Megaliths.RANK_STORE);
        if (s == null) return;
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 44, z1 = z0 + 44, y1 = s.y + 13;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);
        shell(c, x0, y0, z0, x1, y1, z1, 251, 0);
        fill(c, x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, 5, 2);        // pale board floor
        // The ceiling that is the light, hung two blocks over the shelving (structure audit 2026-09-23).
        // It used to be the roof, eleven blocks up behind a course of slabs, and the sales floor stood
        // at light 3 or 4; the void over the new ceiling is poured in the shell's white concrete.
        fill(c, x0 + 1, y0 + 8, z0 + 1, x1 - 1, y1 - 1, z1 - 1, 251, 0);
        fill(c, x0 + 1, y0 + 7, z0 + 1, x1 - 1, y0 + 7, z1 - 1, 89, 0);
        // Where the staff stand, the ceiling is dead: a dark panel over each showroom and one over
        // each staff post out on the floor. Under the unbroken ceiling the floor stood at light 9
        // and none of the seven spawners could put anyone on shift.
        for (int i = 0; i < 4; i++) {
            int bx = x0 + 5 + (i & 1) * 22, bz = z0 + 5 + (i >> 1) * 22;
            fill(c, bx + 1, y0 + 7, bz + 1, bx + 10, y0 + 7, bz + 10, 251, 0);
        }
        fill(c, x0 + 19, y0 + 7, z0 + 9, x0 + 25, y0 + 7, z0 + 15, 251, 0);
        fill(c, x0 + 31, y0 + 7, z0 + 19, x0 + 37, y0 + 7, z0 + 25, 251, 0);
        // The boards are laid on concrete (they were the only thing between the sales floor and the
        // caves under it, and read from below as a plank ceiling).
        fill(c, x0, y0 - 1, z0, x1, y0 - 1, z1, 251, 0);
        // Aisles of shelving, always the same, always one row off from where you left it.
        for (int dz = 4; dz <= 38; dz += 5) {
            for (int dx = 3; dx <= 41; dx++) {
                if (dx % 13 == 0) continue;                            // the cross aisles
                // The first row leaves a bay open in front of each showroom's doorway (it stood
                // against both fronts and sealed one of them).
                if (dz == 4 && ((dx >= 7 && dx <= 14) || (dx >= 29 && dx <= 36))) continue;
                fill(c, x0 + dx, y0 + 1, z0 + dz, x0 + dx, y0 + 5, z0 + dz, 5, 2);
                if ((dx & 1) == 0) {
                    put(c, x0 + dx, y0 + 2, z0 + dz, 35, (dx + dz) & 15);
                    put(c, x0 + dx, y0 + 4, z0 + dz, 171, (dx * 3 + dz) & 15);
                }
            }
        }
        // Showrooms: four little flats with everything in them and no walls facing you.
        int[][] rooms = {{x0 + 5, z0 + 5}, {x0 + 27, z0 + 5}, {x0 + 5, z0 + 27}, {x0 + 27, z0 + 27}};
        for (int i = 0; i < 4; i++) {
            int bx = rooms[i][0], bz = rooms[i][1];
            fill(c, bx, y0 + 1, bz, bx + 11, y0 + 6, bz + 11, 0, 0);
            shell(c, bx, y0 + 1, bz, bx + 11, y0 + 4, bz + 11, 5, i & 3);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 10, y0 + 4, bz + 10, 0, 0);
            fill(c, bx + 3, y0 + 1, bz, bx + 8, y0 + 3, bz, 0, 0);
            // The rug lies on the floor; it had replaced it (a carpet over whatever was underneath).
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 10, y0 + 1, bz + 10, 171, (i * 4) & 15);
            // The bed's head lies beyond its foot (the halves used to stand side by side).
            put(c, bx + 2, y0 + 1, bz + 2, 26, 0); put(c, bx + 2, y0 + 1, bz + 3, 26, 8);
            put(c, bx + 9, y0 + 1, bz + 9, 47, 0);  put(c, bx + 9, y0 + 2, bz + 9, 47, 0);
            put(c, bx + 5, y0 + 1, bz + 7, 58, 0);
            put(c, bx + 6, y0 + 1, bz + 7, 53, 1);
            put(c, bx + 2, y0 + 1, bz + 9, 140, 0);
            // A standard lamp on the floor (the lamp used to be a glowstone block hung in mid-air). It
            // is on display, not switched on: the showroom is left in the dim under its dead ceiling
            // panel, which is where the staff wait (a lit lamp kept the spawner from ever waking).
            fill(c, bx + 9, y0 + 1, bz + 5, bx + 9, y0 + 2, bz + 5, 85, 0);
            put(c, bx + 9, y0 + 3, bz + 5, 123, 0);
            // Each flat is one room set (structure audit 2026-09-23: they were a bed, a desk and a
            // shelf apiece): a bedroom, a living room, a kitchen and a study.
            if (i == 0) {
                fill(c, bx + 1, y0 + 1, bz + 5, bx + 1, y0 + 3, bz + 6, 5, 1);          // wardrobe
                fill(c, bx + 2, y0 + 1, bz + 5, bx + 2, y0 + 2, bz + 6, 96, 7);         // its doors, open
                put(c, bx + 1, y0 + 1, bz + 3, 85, 0); put(c, bx + 1, y0 + 2, bz + 3, 72, 0);   // bedside table
            } else if (i == 1) {
                fill(c, bx + 4, y0 + 1, bz + 10, bx + 6, y0 + 1, bz + 10, 53, 2);       // sofa, back to the wall
                fill(c, bx + 10, y0 + 1, bz + 9, bx + 10, y0 + 2, bz + 9, 47, 0);       // wall unit
            } else if (i == 2) {
                put(c, bx + 10, y0 + 1, bz + 6, 155, 0);                                // worktop,
                put(c, bx + 10, y0 + 1, bz + 7, 118, 0);                                // sink
                put(c, bx + 10, y0 + 1, bz + 8, 61, 4);                                 // and oven
            } else {
                fill(c, bx + 10, y0 + 1, bz + 9, bx + 10, y0 + 2, bz + 9, 47, 0);       // twin shelves
                put(c, bx + 1, y0 + 1, bz + 6, 84, 0);                                  // record player
            }
            box(c, s, bx + 8, y0 + 1, bz + 2, STORE_LOOT, i == 3);
            mob(c, bx + 5, y0 + 1, bz + 5, i < 2 ? EntityType.ZOMBIE_VILLAGER : EntityType.ZOMBIE);
        }
        // The cafe, which is the only part of it anybody remembers fondly.
        fill(c, x0 + 17, y0 + 1, z0 + 17, x0 + 27, y0 + 6, z0 + 27, 0, 0);
        fill(c, x0 + 17, y0, z0 + 17, x0 + 27, y0, z0 + 27, 155, 0);
        for (int dx = 19; dx <= 25; dx += 3) for (int dz = 19; dz <= 25; dz += 3) {
            put(c, x0 + dx, y0 + 1, z0 + dz, 85, 0);
            put(c, x0 + dx, y0 + 2, z0 + dz, 44, 7);
            put(c, x0 + dx - 1, y0 + 1, z0 + dz, 53, 1);
        }
        put(c, x0 + 22, y0 + 1, z0 + 18, 61, 2);
        box(c, s, x0 + 18, y0 + 1, z0 + 26, STORE_LOOT, false);
        fill(c, x0 + 27, y0 + 1, z0 + 27, x0 + 27, y0 + 4, z0 + 27, 5, 3);   // showroom 4's corner, which the cafe cut
        // The way in, and the several ways that only look like ways out. The front doorway opens onto
        // more store: it is filled by a shelving unit (structure audit 2026-09-23; in a buried box the
        // bare doorway gave onto rock or a drop into a cave).
        fill(c, x0 + 21, y0 + 1, z0, x0 + 23, y0 + 4, z0, 5, 2);
        for (int dx = 21; dx <= 23; dx++) {
            put(c, x0 + dx, y0 + 2, z0, 35, (dx * 5) & 15);
            put(c, x0 + dx, y0 + 4, z0, 35, (dx * 7 + 3) & 15);
        }
        for (int i = 0; i < 3; i++) {
            int dx = 8 + i * 14;
            fill(c, x0 + dx, y0 + 1, z1, x0 + dx + 2, y0 + 4, z1, 251, 8);
            put(c, x0 + dx + 1, y0 + 2, z1 - 1, 69, 4);
        }
        mob(c, x0 + 22, y0 + 1, z0 + 12, EntityType.ZOMBIE_VILLAGER);
        mob(c, x0 + 12, y0 + 1, z0 + 34, EntityType.ZOMBIE_VILLAGER);
        mob(c, x0 + 34, y0 + 1, z0 + 22, EntityType.ZOMBIE);
        box(c, s, x0 + 3, y0 + 1, z0 + 21, STORE_LOOT, true);
        box(c, s, x1 - 3, y0 + 1, z0 + 23, STORE_LOOT, false);
        // The way down: a service ladder on the front wall beside the doorway, from the sales floor up
        // through the ceiling into the shaft (structure audit 2026-09-23: the shaft used to end in the
        // ceiling, eleven blocks over the floor).
        fill(c, x0 + 19, y0 + 1, z0 + 1, x0 + 19, y1 - 1, z0 + 1, 65, 3);
        riser(c, t, x0 + 19, z0 + 1, y1, 251, 0);
    }

    // == 14. The Stairwell =======================================================
    // Concrete stairs going down in a square, landing after landing, further than the
    // rock they are cut into has any business going. The lights fail as you descend and
    // the marks on the walls stop being writing. There is a face at the bottom of it.

    private static final int[][] STAIRWELL_LOOT = {
        { 50, 0, 15, 8, 24}, {345, 0,  8, 1,  1}, {347, 0,  8, 1, 1}, {367, 0, 13, 4, 12},
        {352, 0, 12, 4, 12}, {263, 0, 13, 6, 18}, {393, 0, 7, 2, 6}, {374, 0, 4, 1, 1},
        {368, 0,  7, 1,  2}, {264, 0,  6, 1,  2}, {340, 0, 10, 2, 6}, {  4, 0, 12, 8, 24},
    };

    private static void stairwell(Chunk c, Terrain t) {
        Site s = deep(t, c, STAIRWELL_CELL, 0x30383700L, 15, 15, 56, Megaliths.RANK_STAIRWELL);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 14, z1 = z0 + 14, y1 = s.y + 55;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);
        shell(c, x0, y0, z0, x1, y1, z1, 251, 8);
        fill(c, x0 + 6, y0, z0 + 6, x0 + 8, y1, z0 + 8, 251, 8);      // the well down the middle
        fill(c, x0 + 1, y0, z0 + 1, x0 + 13, y0, z0 + 13, 251, 15);   // the black floor at the bottom
        // A square spiral round the well: a landing at each corner, three treads down each side,
        // four blocks of drop a side, and the lights giving out as it goes.
        //
        // Structure audit 2026-09-23: the treads used to hang a block clear of the well, and every
        // tread cleared its headroom through the tread before it, so what stood was a dozen loose
        // landings and a trail of chips over a fifty-block drop, the chests on landings 4 and 9 cut
        // away and the lights hanging in the air. Now nothing is cut after anything is laid: each
        // tread runs from the face of the well out to a kerb, and the drop is the open ring between
        // the kerb and the walls. The lights, dead fittings and marks are set into the concrete.
        int cw = x0 + 7, dw = z0 + 7, top = y1 - 3;
        int[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};        // NW, NE, SE, SW, the way it turns
        int[][] run = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};             // the side leading on from each
        // The top landing runs to the walls under the way in (the shaft comes down its north wall).
        fill(c, x0 + 1, top, z0 + 1, x0 + 5, top, z0 + 5, 251, 7);
        fill(c, x0 + 1, top + 1, z0 + 5, x0 + 5, top + 1, z0 + 5, 251, 8);
        fill(c, x0 + 5, top + 1, z0 + 1, x0 + 5, top + 1, z0 + 2, 251, 8);
        fill(c, x0 + 3, top + 1, z0 + 1, x0 + 3, y1 - 1, z0 + 1, 65, 3);
        for (int landings = 1; landings <= 13; landings++) {
            int k = (landings - 1) & 3, y = top - 4 * (landings - 1);
            int ox = corner[k][0], oz = corner[k][1];
            int px = cw + 4 * ox, pz = dw + 4 * oz;                  // the landing's outer corner
            fill(c, px, y, pz, px - 2 * ox, y, pz - 2 * oz, 251, 7);
            if (landings > 1) {                                       // kerb on its two open sides
                fill(c, px, y + 1, pz, px - 2 * ox, y + 1, pz, 251, 8);
                fill(c, px, y + 1, pz, px, y + 1, pz - 2 * oz, 251, 8);
            }
            for (int i = 1; i <= 3 && y - i > y0; i++) {             // the flight on to the next corner
                int a = i - 2;
                if (run[k][0] != 0) {
                    int tx = cw + run[k][0] * a;
                    fill(c, tx, y - i, pz, tx, y - i, pz - 2 * oz, 251, 7);
                    put(c, tx, y - i + 1, pz, 251, 8);
                } else {
                    int tz = dw + run[k][1] * a;
                    fill(c, px, y - i, tz, px - 2 * ox, y - i, tz, 251, 7);
                    put(c, px, y - i + 1, tz, 251, 8);
                }
            }
            // A light set in the well's corner beside every third landing; below the ninth only the
            // fittings are left.
            if (landings % 3 == 1) put(c, cw + ox, y + 2, dw + oz, landings < 10 ? 89 : 123, 0);
            // Marks on the wall across the drop: a line of it at first, then a scrawl, then nothing.
            if (landings <= 5) fill(c, cw + 7 * ox, y + 2, pz, cw + 7 * ox, y + 2, pz - 2 * oz, 251, 15);
            else if (landings <= 9) {
                put(c, cw + 7 * ox, y + 2, pz, 251, 15);
                put(c, cw + 7 * ox, y + 3, pz - oz, 251, 15);
            }
            if (landings == 4 || landings == 9)
                box(c, s, px - ox, y + 1, pz, STAIRWELL_LOOT, landings == 9);
            if (landings >= 3)
                mob(c, px, y + 1, pz, landings < 6 ? EntityType.ZOMBIE
                        : (landings < 10 ? EntityType.CAVE_SPIDER : EntityType.HUSK));
            // Webs hang under the landings, thicker the further down.
            for (int i = 0; i < landings / 2; i++)
                put(c, px - ox * r.nextInt(3), y - 1, pz - oz * r.nextInt(3), 30, 0);
        }
        // The bottom, and what is standing in it.
        fill(c, x0 + 5, y0 + 1, z0 + 12, x0 + 9, y0 + 4, z0 + 12, 251, 15);
        put(c, x0 + 6, y0 + 3, z0 + 12, 251, 14);
        put(c, x0 + 8, y0 + 3, z0 + 12, 251, 14);
        fill(c, x0 + 6, y0 + 2, z0 + 12, x0 + 8, y0 + 2, z0 + 12, 49, 0);
        mob(c, x0 + 7, y0 + 1, z0 + 10, EntityType.HUSK);
        mob(c, x0 + 3, y0 + 1, z0 + 3, EntityType.CAVE_SPIDER);
        box(c, s, x0 + 11, y0 + 1, z0 + 3, STAIRWELL_LOOT, true);
        box(c, s, x0 + 3, y0 + 1, z0 + 11, STAIRWELL_LOOT, false);
        riser(c, t, x0 + 3, z0 + 1, y1, 251, 8);                      // down onto the top landing
    }

    // == 15. The Corroded Place ==================================================
    // Not a room. A set of rooms the same size laid out wrong, rusted through, with
    // pools that are not water and a chair at the middle of it that somebody made for
    // themselves. The floors do not agree about which way is down.

    private static final int[][] POCKET_LOOT = {
        {336, 0, 14, 6, 18}, {337, 0, 13, 6, 18}, {351, 3, 12, 6, 18}, {318, 0, 12, 6, 18},
        {367, 0, 14, 6, 18}, {377, 0,  9, 2,  6}, {388, 0,  7, 1,  3}, {266, 0,  9, 2,  6},
        {372, 0,  8, 2,  6}, {375, 0, 10, 3,  9}, {384, 0,  6, 1,  3}, {376, 0, 4, 1, 1},
    };

    private static void pocket(Chunk c, Terrain t) {
        Site s = deep(t, c, POCKET_CELL, 0x31303600L, 37, 37, 14, Megaliths.RANK_POCKET);
        if (s == null) return;
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 36, z1 = z0 + 36, y1 = s.y + 13;
        fill(c, x0, y0, z0, x1, y1, z1, 159, 12);                    // rusted through
        // A six by six grid of identical rooms, and the doors between them are not.
        for (int gx = 0; gx < 6; gx++) for (int gz = 0; gz < 6; gz++) {
            int bx = x0 + 1 + gx * 6, bz = z0 + 1 + gz * 6, lift = ((gx * 7 + gz * 5) % 3) - 1;
            int by = y0 + 1 + lift;
            fill(c, bx, by, bz, bx + 4, by + 5, bz + 4, 0, 0);
            fill(c, bx, by - 1, bz, bx + 4, by - 1, bz + 4, ((gx + gz) & 1) == 0 ? 172 : 159, ((gx + gz) & 1) == 0 ? 0 : 8);
            // Structure audit 2026-09-23: the corner room under the shaft gets a door east (it had
            // none), and a door into a room two blocks higher is cut tall with a step in it (it was a
            // slot nobody could climb, which cut off three rooms and trapped anyone in six more).
            // The other odd doors stay odd.
            if ((((gx * 3 + gz) & 3) != 0 || (gx == 0 && gz == 0)) && gx < 5) {
                boolean up = lift(gx + 1, gz) - lift == 2;
                fill(c, bx + 5, by, bz + 1, bx + 5, by + (up ? 4 : 2), bz + 3, 0, 0);
                if (up) put(c, bx + 5, by, bz + 2, 159, 12);
            }
            if (((gx + gz * 3) & 3) != 0 && gz < 5) {
                boolean up = lift(gx, gz + 1) - lift == 2;
                fill(c, bx + 1, by, bz + 5, bx + 3, by + (up ? 4 : 2), bz + 5, 0, 0);
                if (up) put(c, bx + 2, by, bz + 5, 159, 12);
            }
            if (((gx * 5 + gz * 3) & 7) == 0) {                      // a pool of the black stuff,
                fill(c, bx + 1, by - 2, bz + 1, bx + 3, by - 2, bz + 3, 251, 15);   // sunk into the floor
                fill(c, bx + 1, by - 1, bz + 1, bx + 3, by - 1, bz + 3, 9, 0);      // (it stood on it)
            }
            // The lit rooms are the barred ones (the three tests pick the same rooms), so the light
            // hangs beside the bars and the spawner stands beside their bone foot instead of on the
            // same cell, where the bars and the spawner used to overwrite both.
            if (((gx * 11 + gz * 7) & 7) == 3) put(c, bx + 1, by + 5, bz + 3, 89, 0);
            if (((gx * 13 + gz * 9) & 7) == 5) {
                fill(c, bx + 2, by, bz + 2, bx + 2, by + 5, bz + 2, 101, 0);
                put(c, bx + 2, by, bz + 2, 216, 0);
            }
            // The same corrosion in the same corner of every room: a rust streak down the east wall,
            // the black stain trailing from it to the east door, a drain and a vent (the streak and
            // the vent only in the inner walls; the outer ones stand against rock).
            if (gx < 5) {
                put(c, bx + 5, by + 5, bz + 4, 159, 1);
                put(c, bx + 5, by + 4, bz + 4, 159, 1);
                put(c, bx + 5, by + 3, bz + 4, 159, 14);
            }
            put(c, bx + 4, by, bz + 4, 171, 15);
            put(c, bx + 4, by, bz + 3, 171, 15);
            put(c, bx + 3, by, bz + 4, 167, 0);
            if (gz < 5) put(c, bx + 4, by + 3, bz + 5, 101, 0);
            if (gx == 1 && gz == 4) box(c, s, bx + 1, by, bz + 1, POCKET_LOOT, false);
            if (gx == 4 && gz == 1) box(c, s, bx + 3, by, bz + 3, POCKET_LOOT, true);
            if (((gx * 17 + gz * 13) & 7) == 1)
                mob(c, bx + 3, by, bz + 1, ((gx + gz) & 1) == 0 ? EntityType.CAVE_SPIDER : EntityType.SILVERFISH);
        }
        // The middle: two rooms knocked together, and the chair.
        int mx = x0 + 18, mz = z0 + 18;
        fill(c, mx - 5, y0 + 1, mz - 5, mx + 5, y0 + 7, mz + 5, 0, 0);
        fill(c, mx - 5, y0, mz - 5, mx + 5, y0, mz + 5, 172, 0);
        fill(c, mx - 2, y0, mz - 2, mx + 2, y0, mz + 2, 251, 15);
        fill(c, mx - 1, y0 + 1, mz + 1, mx + 1, y0 + 3, mz + 1, 159, 12);
        put(c, mx, y0 + 1, mz, 53, 2);                                // back to its backrest (it faced it)
        put(c, mx - 2, y0 + 1, mz, 216, 4);                           // and what is left by it
        put(c, mx + 2, y0 + 1, mz - 1, 216, 0);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 4), pz = mz + (int) Math.round(Math.sin(ang) * 4);
            fill(c, px, y0 + 1, pz, px, y0 + 7, pz, 101, 0);
        }
        put(c, mx, y0 + 7, mz, 89, 0);
        mob(c, mx - 3, y0 + 1, mz - 3, EntityType.WITCH);
        mob(c, mx + 3, y0 + 1, mz + 3, EntityType.CAVE_SPIDER);
        box(c, s, mx + 4, y0 + 1, mz - 4, POCKET_LOOT, true);
        // The way in (structure audit 2026-09-23): the shaft used to stop on the roof over seven
        // blocks of solid rust. It now comes through the ceiling of the corner room and down its
        // north wall onto the dry rim of its pool.
        fill(c, x0 + 3, y0, z0 + 1, x0 + 3, y0 + 5, z0 + 1, 65, 3);
        riser(c, t, x0 + 3, z0 + 1, y0 + 6, 159, 12);
    }

    /** How far a Corroded Place grid room's floor is lifted or dropped: -1, 0 or 1. */
    private static int lift(int gx, int gz) { return ((gx * 7 + gz * 5) % 3) - 1; }

    /**
     * Faces the bank a level cut leaves round the Lodge: the natural ground standing in this column
     * above the pad, up to the top of the bank, is laid as cobblestone. It stops at the first thing
     * that is not ground (air, a plant, a trunk, a stone cactus), and a column that is not in this
     * chunk is left to the chunk that owns it.
     */
    @SuppressWarnings("deprecation")
    private static void retain(Chunk c, int wx, int y0, int wz) {
        for (int y = y0 + 1; y <= y0 + 12; y++) {
            org.bukkit.block.Block b = Megaliths.blockAt(c, wx, y, wz);
            if (b == null) return;
            int id = b.getTypeId();
            if (!((id == 1 && b.getData() == 0) || id == 2 || id == 3 || id == 12 || id == 13 || id == 24
                    || id == 80 || id == 82 || id == 87 || id == 88 || id == 110 || id == 159 || id == 172
                    || id == 179)) return;
            put(c, wx, y, wz, 4, 0);
        }
    }

    // == 16. The Lodge ===========================================================
    // A holiday cabin with a cellar full of other people's belongings, and under the
    // cellar a lift shaft, and off the lift shaft a ring of glass cells with a different
    // thing in each one. Somebody upstairs chose which cell opened. There is a control
    // room with the board still lit and the coffee cups still out.

    private static final int[][] LODGE_LOOT = {
        {340, 0, 12, 2,  6}, {386, 0,  9, 1, 2}, { 50, 0, 13, 6, 18}, {352, 0, 12, 4, 12},
        {367, 0, 13, 4, 12}, {322, 0,  6, 1, 1}, {261, 0,  8, 1,  1}, {262, 0, 13, 8, 24},
        {395, 0,  7, 1,  1}, {288, 0, 10, 3, 9}, {353, 0, 10, 4, 12}, {420, 0, 5, 1, 1},
    };

    private static void lodge(Chunk c, Terrain t) {
        Site s = ground(t, c, LODGE_CELL, 0x4C4F444745L, 29, 25, 6, Megaliths.RANK_LODGE);
        if (s == null) return;
        int x0 = s.x, z0 = s.z, y0 = s.y;
        // The cabin. Log walls, a porch, a stone chimney, and one bedroom per guest.
        int cx = x0 + 8, cz = z0 + 8;
        // The pad is cut level first, porch and a step of ground round it, and the bank the cut
        // leaves is faced with a dry cobblestone wall (structure audit 2026-09-23: on a hillside the
        // slope stood on the porch and against the east windows).
        fill(c, cx - 2, y0 + 1, cz - 2, cx + 15, y0 + 12, cz + 13, 0, 0);
        for (int d = -3; d <= 16; d++) {
            retain(c, cx + d, y0, cz - 3);
            retain(c, cx + d, y0, cz + 14);
            if (d <= 14) {
                retain(c, cx - 3, y0, cz + d);
                retain(c, cx + 16, y0, cz + d);
            }
        }
        for (int dx = -1; dx <= 14; dx++) for (int dz = -1; dz <= 12; dz++)
            footing(c, s, cx + dx, cz + dz, 4, 0);
        fill(c, cx - 1, y0, cz - 1, cx + 14, y0, cz + 12, 5, 1);
        shell(c, cx, y0 + 1, cz, cx + 13, y0 + 5, cz + 11, 17, 1);
        fill(c, cx + 1, y0 + 1, cz + 1, cx + 12, y0 + 4, cz + 10, 0, 0);
        fill(c, cx + 6, y0 + 1, cz, cx + 7, y0 + 3, cz, 0, 0);
        for (int dz = 2; dz <= 9; dz += 3) {
            fill(c, cx, y0 + 2, cz + dz, cx, y0 + 3, cz + dz + 1, 102, 0);
            fill(c, cx + 13, y0 + 2, cz + dz, cx + 13, y0 + 3, cz + dz + 1, 102, 0);
        }
        for (int i = 0; i <= 6; i++) {                                 // pitched roof
            fill(c, cx - 1 + i, y0 + 5 + i, cz - 1, cx - 1 + i, y0 + 5 + i, cz + 12, 53, 0);
            fill(c, cx + 14 - i, y0 + 5 + i, cz - 1, cx + 14 - i, y0 + 5 + i, cz + 12, 53, 1);
            // The attic is packed out under every course of the roof and closed flush with the gable
            // walls (structure audit 2026-09-23: it stood a block in from both, so the courses above
            // the eaves touched nothing but each other's corners and the gables stood open).
            if (i == 1) fill(c, cx + 1, y0 + 5, cz + 1, cx + 12, y0 + 5, cz + 10, 5, 1);
            if (i > 1) fill(c, cx + i - 1, y0 + 4 + i, cz, cx + 14 - i, y0 + 4 + i, cz + 11, 5, 1);
        }
        fill(c, cx + 6, y0 + 11, cz - 1, cx + 7, y0 + 11, cz + 12, 5, 0);   // ridge board over the open groove
        fill(c, cx + 11, y0 + 1, cz + 8, cx + 12, y0 + 11, cz + 9, 4, 0);
        // One fire on the netherrack, an open flue above it: a second fire stacked on the first had
        // nothing to burn on and went out at once (structure audit 2026-09-22).
        fill(c, cx + 11, y0 + 2, cz + 8, cx + 11, y0 + 2, cz + 8, 0, 0);
        fill(c, cx + 11, y0 + 1, cz + 8, cx + 11, y0 + 1, cz + 8, 51, 0);
        fill(c, cx + 11, y0, cz + 8, cx + 11, y0, cz + 8, 87, 0);
        // A hearth round it (structure audit 2026-09-23): the spruce floor, ceiling and log wall stood
        // within the fire's reach. Cobblestone hearth, breast and cheek, and a grate on its open faces.
        fill(c, cx + 10, y0, cz + 7, cx + 10, y0, cz + 9, 4, 0);
        fill(c, cx + 11, y0, cz + 7, cx + 12, y0, cz + 7, 4, 0);
        fill(c, cx + 10, y0 + 5, cz + 7, cx + 10, y0 + 5, cz + 9, 4, 0);
        fill(c, cx + 11, y0 + 5, cz + 7, cx + 12, y0 + 5, cz + 7, 4, 0);
        fill(c, cx + 12, y0 + 1, cz + 7, cx + 12, y0 + 4, cz + 7, 4, 0);
        put(c, cx + 10, y0 + 1, cz + 8, 101, 0);
        put(c, cx + 11, y0 + 1, cz + 7, 101, 0);
        for (int i = 0; i < 4; i++) {                                  // beds, table, lamp
            put(c, cx + 2 + i * 3, y0 + 1, cz + 2, 26, 2);
            put(c, cx + 2 + i * 3, y0 + 1, cz + 1, 26, 10);
        }
        put(c, cx + 6, y0 + 1, cz + 6, 58, 0);
        put(c, cx + 5, y0 + 1, cz + 6, 53, 1);
        put(c, cx + 7, y0 + 4, cz + 6, 89, 0);
        box(c, s, cx + 2, y0 + 1, cz + 9, LODGE_LOOT, false);
        // The cellar, reached through the floor, full of other people's things. Its belongings, box
        // and spawner go in after the shaft (below), whose cap used to be laid over all of them.
        fill(c, cx + 4, y0 - 5, cz + 4, cx + 9, y0, cz + 9, 0, 0);
        shell(c, cx + 3, y0 - 6, cz + 3, cx + 10, y0, cz + 10, 4, 0);
        fill(c, cx + 4, y0 - 6, cz + 4, cx + 9, y0 - 6, cz + 9, 5, 1);
        // Floorboards over the cellar, the hearth keeping its stone (the shell's top used to lay a
        // square of cobblestone in the middle of the cabin floor).
        fill(c, cx + 3, y0, cz + 3, cx + 10, y0, cz + 10, 5, 1);
        fill(c, cx + 10, y0, cz + 7, cx + 10, y0, cz + 9, 4, 0);
        fill(c, cx + 4, y0, cz + 4, cx + 4, y0, cz + 4, 0, 0);
        // The shaft down, and the ring of cells at the bottom. Structure audit 2026-09-23: the shaft
        // opens in the cellar floor inside a flush concrete collar (its casing's top used to stand a
        // block proud as a 5x5 cap over the cellar and seal the shaft).
        int sx = cx + 6, sz = cz + 6, fy = y0 - 30;
        fill(c, sx - 1, fy, sz - 1, sx + 1, y0 - 6, sz + 1, 0, 0);
        shell(c, sx - 2, fy - 1, sz - 2, sx + 2, y0 - 6, sz + 2, 251, 7);
        fill(c, sx - 1, fy, sz - 1, sx + 1, y0 - 6, sz + 1, 0, 0);
        fill(c, sx - 1, fy - 1, sz - 1, sx + 1, fy - 1, sz + 1, 251, 0);   // white concrete, was iron block
        fill(c, sx + 1, fy, sz + 1, sx + 1, y0 - 6, sz + 1, 65, 4);
        for (int dy = fy + 4; dy < y0 - 6; dy += 8) put(c, sx - 2, dy, sz, 89, 0);
        // A gallery round the shaft with eight glass cells opening onto it.
        fill(c, sx - 11, fy, sz - 11, sx + 11, fy + 7, sz + 11, 0, 0);
        shell(c, sx - 12, fy - 1, sz - 12, sx + 12, fy + 8, sz + 12, 251, 7);
        fill(c, sx - 11, fy - 1, sz - 11, sx + 11, fy - 1, sz + 11, 251, 8);
        EntityType[] kept = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.WITCH,
                             EntityType.CAVE_SPIDER, EntityType.SLIME, EntityType.HUSK, EntityType.SILVERFISH};
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int px = sx + (int) Math.round(Math.cos(ang) * 8), pz = sz + (int) Math.round(Math.sin(ang) * 8);
            shell(c, px - 2, fy, pz - 2, px + 2, fy + 5, pz + 2, 95, 0);
            fill(c, px - 1, fy, pz - 1, px + 1, fy + 4, pz + 1, 0, 0);
            fill(c, px - 1, fy - 1, pz - 1, px + 1, fy - 1, pz + 1, 251, 15);
            // The cells are dark under plain glass (a light in every lid kept anything from waking);
            // the gallery is lit instead, below.
            mob(c, px, fy, pz, kept[i]);
        }
        // The control room, one floor up, with the board still lit. Structure audit 2026-09-23: its
        // glass is a strip in the floor over the gallery (it was a wall facing concrete), the board is
        // lit from behind, there are chairs at the desks, and the way up is a ladder on the lift core
        // through the gallery ceiling into a notch in the south wall (it was a one-high hole behind
        // the glass, on a ladder with nothing behind it).
        fill(c, sx - 11, fy + 9, sz - 11, sx + 11, fy + 13, sz - 4, 0, 0);
        shell(c, sx - 12, fy + 8, sz - 12, sx + 12, fy + 14, sz - 3, 251, 7);
        fill(c, sx - 11, fy + 9, sz - 11, sx + 11, fy + 9, sz - 4, 251, 8);
        fill(c, sx - 11, fy + 8, sz - 5, sx + 11, fy + 9, sz - 4, 95, 0);
        for (int dx = -9; dx <= 9; dx += 2) {
            put(c, sx + dx, fy + 10, sz - 10, 251, 15);
            put(c, sx + dx, fy + 11, sz - 10, 95, ((dx + 9) / 2) % 3 == 0 ? 14 : 5);
            put(c, sx + dx, fy + 10, sz - 11, 251, 15);
            put(c, sx + dx, fy + 11, sz - 11, 89, 0);
            put(c, sx + dx, fy + 10, sz - 8, 44, 7);
            if (((dx + 9) & 3) == 0) put(c, sx + dx, fy + 10, sz - 7, 156, 2);
        }
        put(c, sx, fy + 13, sz - 7, 89, 0);
        // The lift core: concrete round the shaft down to the gallery floor, open through the gallery
        // ceiling, a door on its south side, and lights in its faces for the aisle.
        fill(c, sx - 2, fy, sz - 2, sx + 2, fy + 7, sz + 2, 251, 7);
        fill(c, sx - 1, fy, sz - 1, sx + 1, fy + 8, sz + 1, 0, 0);
        fill(c, sx, fy, sz + 2, sx, fy + 1, sz + 2, 0, 0);
        fill(c, sx + 1, fy, sz + 1, sx + 1, y0 - 6, sz + 1, 65, 4);
        put(c, sx - 2, fy + 4, sz, 89, 0);
        put(c, sx + 2, fy + 4, sz, 89, 0);
        put(c, sx - 1, fy + 4, sz - 2, 89, 0);
        put(c, sx, fy + 4, sz + 2, 89, 0);
        for (int d = -12; d <= 12; d += 24) {                          // and in the outer walls, high
            put(c, sx + d, fy + 6, sz, 89, 0);
            put(c, sx, fy + 6, sz + d, 89, 0);
        }
        fill(c, sx + 1, fy, sz - 3, sx + 1, fy + 9, sz - 3, 65, 2);
        fill(c, sx + 1, fy + 10, sz - 3, sx + 1, fy + 11, sz - 3, 0, 0);
        box(c, s, sx - 9, fy + 10, sz - 9, LODGE_LOOT, true);
        box(c, s, sx + 9, fy + 10, sz - 5, LODGE_LOOT, false);
        mob(c, sx - 4, fy + 10, sz - 6, EntityType.ZOMBIE);
        mob(c, sx + 3, fy, sz + 3, EntityType.ZOMBIE);                  // in the aisle, not a cage corner
        // The cellar's things, round the shaft collar and against the walls, with the way from the
        // hatch ladder to the shaft ladder left clear along the north and east.
        fill(c, cx + 4, y0 - 5, cz + 4, cx + 4, y0, cz + 4, 65, 3);    // on the wall (it faced into the room)
        fill(c, cx + 4, y0 - 5, cz + 5, cx + 4, y0 - 4, cz + 6, 47, 0);
        put(c, cx + 4, y0 - 5, cz + 7, 145, 4);
        put(c, cx + 4, y0 - 5, cz + 8, 35, 12);
        put(c, cx + 4, y0 - 5, cz + 9, 84, 0);
        put(c, cx + 5, y0 - 5, cz + 9, 145, 8);
        put(c, cx + 6, y0 - 5, cz + 9, 35, 14);
        put(c, cx + 6, y0 - 4, cz + 9, 35, 11);
        put(c, cx + 7, y0 - 5, cz + 9, 25, 0);
        fill(c, cx + 9, y0 - 5, cz + 5, cx + 9, y0 - 4, cz + 5, 47, 0);
        put(c, cx + 9, y0 - 5, cz + 7, 145, 1);
        put(c, cx + 9, y0 - 1, cz + 4, 30, 0);
        put(c, cx + 4, y0 - 1, cz + 9, 30, 0);
        put(c, cx + 6, y0 - 1, cz + 6, 89, 0);                        // hung from the ceiling, not in the air
        // An iron grating flush over the shaft mouth, open only over the ladder: the cellar floor was
        // half a 3x3 hole with a thirty-block drop under it (structure audit 2026-09-23).
        for (int gx = -1; gx <= 1; gx++) for (int gz = -1; gz <= 1; gz++)
            if (gx != 1 || gz != 1) put(c, sx + gx, y0 - 6, sz + gz, 167, 8);
        box(c, s, cx + 8, y0 - 5, cz + 9, LODGE_LOOT, true);
        mob(c, cx + 9, y0 - 5, cz + 9, EntityType.ZOMBIE);
    }

    // == 17. The Hive ============================================================
    // A corporate laboratory under a hillside, shut from the inside when something got
    // loose in it. A tram tunnel in, white corridors, a hall with a lit column in the
    // middle that is still talking to itself, a passage nobody should have walked down
    // twice, and a sealed lab at the far end that the seal did not help.

    private static final int[][] HIVE_LOOT = {
        {331, 0, 13, 6, 18}, {152, 0,  8, 1, 3}, {348, 0, 12, 4, 12}, {264, 0,  8, 1,  3},
        {363, 0, 12, 4, 12}, {306, 0,  7, 1, 1}, {308, 0,  7, 1,  1}, {373, 0, 10, 1,  3},
        {146, 0, 8, 2, 6}, {167, 0, 5, 1, 1}, { 89, 0, 10, 2,  6}, { 42, 0,  8, 1,  3},
    };

    private static void hive(Chunk c, Terrain t) {
        Site s = deep(t, c, HIVE_CELL, 0x48495645L, 43, 35, 16, Megaliths.RANK_HIVE);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 42, z1 = z0 + 34, y1 = s.y + 15;
        fill(c, x0, y0, z0, x1, y1, z1, 251, 8);
        // The tram tunnel along the south edge, with the car still in it.
        fill(c, x0 + 1, y0 + 1, z0 + 2, x1 - 1, y0 + 5, z0 + 6, 0, 0);
        fill(c, x0 + 1, y0 + 1, z0 + 2, x1 - 1, y0 + 1, z0 + 6, 251, 7);
        fill(c, x0 + 1, y0 + 2, z0 + 4, x1 - 1, y0 + 2, z0 + 4, 66, 1);       // east-west track
        for (int dx = 3; dx <= 39; dx += 6) {
            fill(c, x0 + dx, y0 + 2, z0 + 2, x0 + dx, y0 + 5, z0 + 2, 251, 0);   // rib: white concrete, was iron
            put(c, x0 + dx, y0 + 5, z0 + 4, 169, 0);
        }
        fill(c, x0 + 6, y0 + 2, z0 + 3, x0 + 13, y0 + 4, z0 + 5, 155, 0);
        fill(c, x0 + 7, y0 + 3, z0 + 3, x0 + 12, y0 + 3, z0 + 5, 95, 3);
        fill(c, x0 + 7, y0 + 2, z0 + 4, x0 + 12, y0 + 3, z0 + 4, 0, 0);
        // Its platform-side door stands open (the car was a sealed glass box; structure audit 2026-09-23).
        fill(c, x0 + 10, y0 + 2, z0 + 5, x0 + 10, y0 + 3, z0 + 5, 0, 0);
        box(c, s, x0 + 11, y0 + 2, z0 + 4, HIVE_LOOT, false);
        mob(c, x0 + 8, y0 + 2, z0 + 4, EntityType.ZOMBIE);
        // White corridors: a spine north from the platform with labs off it.
        fill(c, x0 + 18, y0 + 1, z0 + 6, x0 + 21, y0 + 5, z1 - 2, 0, 0);
        fill(c, x0 + 18, y0 + 1, z0 + 6, x0 + 21, y0 + 1, z1 - 2, 155, 0);
        for (int dz = 9; dz <= 31; dz += 4) {
            put(c, x0 + 19, y0 + 5, z0 + dz, 89, 0);
            // Pilasters in the white corridors: white concrete 251:0 (was iron block).
            fill(c, x0 + 17, y0 + 1, z0 + dz, x0 + 17, y0 + 5, z0 + dz, 251, 0);
            fill(c, x0 + 22, y0 + 1, z0 + dz, x0 + 22, y0 + 5, z0 + dz, 251, 0);
        }
        // The laser hall, running west off the spine. Nothing about it looks dangerous.
        fill(c, x0 + 2, y0 + 1, z0 + 10, x0 + 17, y0 + 5, z0 + 14, 0, 0);
        fill(c, x0 + 2, y0 + 1, z0 + 10, x0 + 17, y0 + 1, z0 + 14, 251, 7);
        for (int dx = 3; dx <= 15; dx += 2) {
            // Emitters: red concrete 251:14 (was redstone block, which powered nothing).
            fill(c, x0 + dx, y0 + 2, z0 + 10, x0 + dx, y0 + 4, z0 + 10, 251, 14);
            fill(c, x0 + dx, y0 + 2, z0 + 14, x0 + dx, y0 + 4, z0 + 14, 251, 14);
            fill(c, x0 + dx, y0 + 2, z0 + 11, x0 + dx, y0 + 4, z0 + 13, 101, 0);
            // One gap in each curtain of beams, on alternate sides, so the hall is a slalom between
            // them (seven full curtains used to close it; structure audit 2026-09-23).
            int gap = ((dx - 3) / 2 & 1) == 0 ? z0 + 13 : z0 + 11;
            fill(c, x0 + dx, y0 + 2, gap, x0 + dx, y0 + 4, gap, 0, 0);
        }
        put(c, x0 + 2, y0 + 4, z0 + 12, 89, 0);
        box(c, s, x0 + 3, y0 + 2, z0 + 12, HIVE_LOOT, true);
        mob(c, x0 + 9, y0 + 2, z0 + 12, EntityType.SKELETON);
        // The column room: a cube with a lit core that is still running.
        int qx = x0 + 32, qz = z0 + 14;
        fill(c, qx - 8, y0 + 1, qz - 6, qx + 8, y0 + 11, qz + 6, 0, 0);
        shell(c, qx - 9, y0, qz - 7, qx + 9, y0 + 12, qz + 7, 251, 15);
        fill(c, qx - 8, y0 + 1, qz - 6, qx + 8, y0 + 1, qz + 6, 251, 7);
        // Core, glass and pillars run up to the ceiling (they stopped a block short of it).
        fill(c, qx - 1, y0 + 2, qz - 1, qx + 1, y0 + 11, qz + 1, 95, 14);
        fill(c, qx, y0 + 2, qz, qx, y0 + 11, qz, 251, 14);          // core: red concrete, was redstone block
        for (int a = 0; a < 4; a++) {
            double ang = a * Math.PI / 2 + Math.PI / 4;
            int px = qx + (int) Math.round(Math.cos(ang) * 5), pz = qz + (int) Math.round(Math.sin(ang) * 4);
            fill(c, px, y0 + 2, pz, px, y0 + 11, pz, 251, 0);          // pillar: white concrete, was iron block
            put(c, px, y0 + 6, pz, 169, 0);
        }
        // The desks that watched it: a row of black consoles with their screens either side of the core.
        for (int side = -1; side <= 1; side += 2) {
            fill(c, qx - 2, y0 + 2, qz + 5 * side, qx + 2, y0 + 2, qz + 5 * side, 251, 15);
            fill(c, qx - 2, y0 + 3, qz + 5 * side, qx + 2, y0 + 3, qz + 5 * side, 160, 15);
        }
        fill(c, qx - 9, y0 + 2, qz - 1, qx - 9, y0 + 4, qz + 1, 0, 0);
        fill(c, x0 + 21, y0 + 2, qz, qx - 9, y0 + 4, qz, 0, 0);
        mob(c, qx + 5, y0 + 2, qz + 4, EntityType.WOLF);
        mob(c, qx - 5, y0 + 2, qz - 4, EntityType.WOLF);
        box(c, s, qx + 7, y0 + 2, qz, HIVE_LOOT, false);
        // The labs off the spine, and the one at the end that was sealed.
        int[][] labs = {{x0 + 3, z0 + 18}, {x0 + 3, z0 + 26}, {x0 + 24, z0 + 24}};
        for (int i = 0; i < 3; i++) {
            int bx = labs[i][0], bz = labs[i][1];
            fill(c, bx, y0 + 1, bz, bx + 13, y0 + 5, bz + 6, 0, 0);
            fill(c, bx, y0 + 1, bz, bx + 13, y0 + 1, bz + 6, 155, 0);
            shell(c, bx - 1, y0, bz - 1, bx + 14, y0 + 6, bz + 7, 251, 0);
            fill(c, bx, y0 + 1, bz, bx + 13, y0 + 5, bz + 6, 0, 0);
            fill(c, bx, y0 + 1, bz, bx + 13, y0 + 1, bz + 6, 155, 0);
            if (i < 2) fill(c, bx + 14, y0 + 2, bz + 2, x0 + 18, y0 + 4, bz + 4, 0, 0);
            else fill(c, bx - 1, y0 + 2, bz + 2, x0 + 21, y0 + 4, bz + 4, 0, 0);
            for (int dx = 2; dx <= 11; dx += 3) {
                fill(c, bx + dx, y0 + 2, bz + 1, bx + dx, y0 + 2, bz + 5, 44, 7);
                put(c, bx + dx, y0 + 3, bz + 2, 118, 0);
                put(c, bx + dx, y0 + 3, bz + 4, 117, 0);
            }
            put(c, bx + 6, y0 + 5, bz + 3, 89, 0);
            if (i < 2) {                                               // two more lights over the benches;
                put(c, bx + 2, y0 + 5, bz + 3, 89, 0);                 // the sealed lab keeps its one
                put(c, bx + 11, y0 + 5, bz + 3, 89, 0);
            }
            box(c, s, bx + 1, y0 + 2, bz + 5, HIVE_LOOT, i == 2);
            mob(c, bx + 9, y0 + 2, bz + 3, i == 2 ? EntityType.HUSK : EntityType.ZOMBIE);
            if (i == 2) {                                              // the seal, from inside
                // Structure audit 2026-09-23: the seal is plated over the doorway and torn open in the
                // middle (it was a plate on a blind wall while the doorway stood open), and the webs
                // hang from the ceiling rather than in the air; same draws as before.
                fill(c, bx - 1, y0 + 2, bz + 2, bx - 1, y0 + 4, bz + 4, 43, 8);
                fill(c, bx - 1, y0 + 2, bz + 3, bx - 1, y0 + 3, bz + 3, 0, 0);
                put(c, bx, y0 + 4, bz + 2, 30, 0);
                put(c, bx, y0 + 4, bz + 4, 30, 0);
                for (int k = 0; k < 10; k++) {
                    int wx = bx + 1 + r.nextInt(12);
                    r.nextInt(3);
                    int wz = bz + 1 + r.nextInt(5);
                    if (wx != bx + 6 || wz != bz + 3) put(c, wx, y0 + 5, wz, 30, 0);
                }
                box(c, s, bx + 12, y0 + 2, bz + 1, HIVE_LOOT, true);
            }
        }
        mob(c, x0 + 19, y0 + 2, z0 + 24, EntityType.ZOMBIE);
        // The way out (structure audit 2026-09-23): the shaft used to start on the roof of the block,
        // nine blocks of concrete over the spine. It now starts at the spine's floor, in a niche in
        // the corridor's east wall at its far end, open to the corridor.
        riser(c, t, x0 + 22, z1 - 2, y0 + 2, 251, 8);
        fill(c, x0 + 21, y0 + 2, z1 - 2, x0 + 21, y0 + 5, z1 - 2, 0, 0);
    }
}
