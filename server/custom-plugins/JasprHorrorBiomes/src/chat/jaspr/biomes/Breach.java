package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import static chat.jaspr.biomes.Megaliths.Site;
import static chat.jaspr.biomes.Megaliths.deep;
import static chat.jaspr.biomes.Megaliths.fill;
import static chat.jaspr.biomes.Megaliths.footing;
import static chat.jaspr.biomes.Megaliths.graded;
import static chat.jaspr.biomes.Megaliths.ground;
import static chat.jaspr.biomes.Megaliths.mob;
import static chat.jaspr.biomes.Megaliths.put;
import static chat.jaspr.biomes.Megaliths.riser;
import static chat.jaspr.biomes.Megaliths.shell;
import static chat.jaspr.biomes.Megaliths.trove;

/**
 * Breach: five more containment sites, and the worst of what is in them.
 *
 * A pit with a lid on it, a kennel block, a warren with no lights in it at all, one cell
 * with one photograph in it, and a mast on a hill that is still transmitting. What makes
 * these different from the rest of the plugin is that Containment re-dresses everything
 * that spawns inside them, so the things in the cells are not zombies with a different
 * spawner -- they are named, re-statted and, in one case, invisible.
 *
 * Each also has a boss in its deepest room. Containment promotes the first thing that
 * spawns there, once per site per run of the server.
 */
public final class Breach {
    private Breach() { }

    private static final int PIT_CELL = 97, KENNELS_CELL = 91, WARREN_CELL = 89;
    private static final int VIEWING_CELL = 95, SIGNAL_CELL = 69;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        pit(c, t);
        kennels(c, t);
        warren(c, t);
        viewing(c, t);
        signal(c, t);
    }

    // == 58. The Pit =============================================================
    // They stopped trying to kill it somewhere around the two hundredth attempt and
    // poured a lid over the hole instead. Gantries round the rim, acid in the bottom,
    // an island in the middle of the acid, and a procedure on the wall nobody followed.

    private static final int[][] PIT_LOOT = {
        {433, 0,  7, 1,  2}, {430, 0,  8, 2,  6}, {362, 0,  9, 2,  8}, {266, 0, 11, 3,  9},
        {265, 0, 11, 4, 12}, {331, 0, 11, 6, 18}, {348, 0, 10, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351,  2, 10, 6, 18}, {384, 0,  9, 2,  8}, {403, 0,  5, 1,  1},
    };
    private static final int[][] PIT_HOARD = {{57, 0, 1, 2, 2}, {310, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static void pit(Chunk c, Terrain t) {
        Site s = deep(t, c, PIT_CELL, 0x36383200L, 37, 37, 20, Megaliths.RANK_PIT);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, mx = x0 + 18, mz = z0 + 18, y1 = y0 + 19;
        fill(c, x0, y0, z0, x0 + 36, y1, z0 + 36, 0, 0);
        shell(c, x0 - 1, y0 - 1, z0 - 1, x0 + 37, y1 + 1, z0 + 37, 251, 8);
        fill(c, x0, y1, z0, x0 + 36, y1, z0 + 36, 49, 0);            // the lid
        // The shaft, the acid, and the island they left in the middle of it.
        // Structure audit 2026-09-23: the base runs out to the walls (+-18; +-17 left a 1-wide, 6-deep
        // slot round the rim), and the acid is still lava (11), which does not drain off chunk seams.
        for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 15.5) { fill(c, mx + dx, y0, mz + dz, mx + dx, y0 + 5, mz + dz, 251, 7); continue; }
            put(c, mx + dx, y0 - 1, mz + dz, 49, 0);
            if (d > 4.5) put(c, mx + dx, y0, mz + dz, 11, 0);
            else put(c, mx + dx, y0, mz + dz, d > 3.2 ? 49 : 251, d > 3.2 ? 0 : 15);
        }
        fill(c, mx - 1, y0 + 1, mz - 1, mx + 1, y0 + 3, mz + 1, 101, 0);
        fill(c, mx, y0 + 1, mz, mx, y0 + 2, mz, 0, 0);
        mob(c, mx, y0 + 1, mz, EntityType.MAGMA_CUBE);                 // the boss spawns here
        // Gantries round the rim, three of them, with ladders between.
        // Structure audit 2026-09-23: the decks are walkways (bars only along the shaft edge, not over
        // every deck cell), the deck lights hang under the deck above (they floated between decks), and
        // the per-level ladders (unbacked, ending under the next deck, the top one through the lid) are
        // replaced by one ladder from the catwalk up to the top gantry, below.
        for (int level = 0; level < 3; level++) {
            int gy = y0 + 5 + level * 5;
            for (int dx = -17; dx <= 17; dx++) for (int dz = -17; dz <= 17; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 15.4 || d < 12.4) continue;
                put(c, mx + dx, gy, mz + dz, 43, 8);                     // gantry deck: 43:8, was iron block
                put(c, mx + dx, gy + 1, mz + dz, d < 13.4 ? 101 : 0, 0);  // railing on the shaft side
                put(c, mx + dx, gy + 2, mz + dz, 0, 0);
            }
            for (int a = 0; a < 4; a++) {
                double ang = a * Math.PI / 2 + 0.6;
                put(c, mx + (int) Math.round(Math.cos(ang) * 14), gy + 4,
                    mz + (int) Math.round(Math.sin(ang) * 14), 169, 0);
            }
            if (level != 1) graded(c, s, mx - 14, gy + 1, mz + 4, r, PIT_LOOT, level == 2);
            mob(c, mx + 14, gy + 1, mz - 4, level == 0 ? EntityType.BLAZE : EntityType.MAGMA_CUBE);
        }
        // The lid's columns: eight pylons from the rim up to the lid, carrying all three gantries.
        for (int[] p : new int[][] {{16, 0}, {-16, 0}, {0, 16}, {0, -16}, {11, 11}, {11, -11}, {-11, 11}, {-11, -11}})
            fill(c, mx + p[0], y0 + 6, mz + p[1], mx + p[0], y1 - 1, mz + p[1], 251, 7);
        // One ladder from the end of the catwalk to the top gantry, on the east pylon, through a
        // hatch in each deck.
        fill(c, mx + 15, y0 + 2, mz, mx + 15, y0 + 15, mz, 65, 4);
        // The catwalk out to the island, most of it gone.
        for (int dx = 5; dx <= 15; dx++)
            if (dx % 4 != 0) fill(c, mx + dx, y0 + 1, mz - 1, mx + dx, y0 + 1, mz + 1, 43, 8);   // 43:8, was iron
        // The control room off the rim, with the procedure still on the wall.
        fill(c, x0 + 1, y0 + 11, z0 + 1, x0 + 9, y0 + 15, z0 + 9, 0, 0);
        shell(c, x0, y0 + 10, z0, x0 + 10, y0 + 16, z0 + 10, 251, 8);
        fill(c, x0 + 1, y0 + 11, z0 + 1, x0 + 9, y0 + 11, z0 + 9, 251, 0);
        // Structure audit 2026-09-23: the observation window is in the east wall, over the gantries
        // (it stood one block inside a blank wall), with the console under it and a seat at it.
        fill(c, x0 + 10, y0 + 13, z0 + 3, x0 + 10, y0 + 14, z0 + 7, 95, 14);
        fill(c, x0 + 9, y0 + 12, z0 + 3, x0 + 9, y0 + 12, z0 + 7, 251, 15);
        put(c, x0 + 9, y0 + 13, z0 + 3, 77, 5); put(c, x0 + 9, y0 + 13, z0 + 4, 69, 5);
        put(c, x0 + 9, y0 + 13, z0 + 5, 151, 0);
        put(c, x0 + 9, y0 + 13, z0 + 6, 69, 5); put(c, x0 + 9, y0 + 13, z0 + 7, 77, 5);
        put(c, x0 + 8, y0 + 12, z0 + 5, 109, 1);
        for (int dz = 2; dz <= 8; dz += 2) { put(c, x0 + 1, y0 + 12, z0 + dz, 251, 15); put(c, x0 + 1, y0 + 13, z0 + dz, 95, 14); }
        sign(c, x0 + 1, y0 + 14, z0 + 3, 68, 5, "PROCEDURE", "- 682 -", "KEEP THE ACID", "AT FULL DEPTH");
        sign(c, x0 + 1, y0 + 14, z0 + 5, 68, 5, "DO NOT", "OPEN THE LID", "DO NOT", "FEED IT");
        sign(c, x0 + 1, y0 + 14, z0 + 7, 68, 5, "IF IT SPEAKS", "DO NOT", "ANSWER", "");
        put(c, x0 + 5, y0 + 15, z0 + 5, 89, 0);
        graded(c, s, x0 + 8, y0 + 12, z0 + 8, r, PIT_LOOT, true);
        mob(c, x0 + 5, y0 + 12, z0 + 5, EntityType.BLAZE);
        trove(c, x0 + 2, y0 + 12, z0 + 8, r, PIT_LOOT, PIT_HOARD, 16 + r.nextInt(6));
        // Structure audit 2026-09-23: the way in. The riser used to stop on top of the lid with the
        // room sealed underneath; it now comes down through the lid into the room's north-west corner
        // (ladder on the north wall to the floor), and an iron door in the south wall lets out onto
        // the middle gantry, one step down.
        fill(c, x0 + 1, y0 + 12, z0 + 1, x0 + 1, y0 + 15, z0 + 1, 65, 3);
        put(c, x0 + 6, y0 + 12, z0 + 10, 71, 1); put(c, x0 + 6, y0 + 13, z0 + 10, 71, 8);
        put(c, x0 + 7, y0 + 13, z0 + 9, 77, 4); put(c, x0 + 7, y0 + 13, z0 + 11, 77, 3);
        put(c, x0 + 6, y0 + 11, z0 + 11, 44, 0);
        riser(c, t, x0 + 1, z0 + 1, y0 + 16, 251, 8);
    }

    // == 59. The Kennels =========================================================
    // Two rows of cages down a red-lit corridor, a feeding trench, and a room at the end
    // with a chair in it. Whatever is in here is fast, it is not quite blind, and the
    // reason the cages are open is on the clipboard by the door.

    private static final int[][] KENNELS_LOOT = {
        {418, 0,  5, 1,  1}, {446, 0,  6, 1,  1}, {361, 0,  9, 2,  8}, {266, 0, 11, 3,  9},
        {265, 0, 11, 4, 12}, {352, 0, 12, 4, 12}, {367, 0, 12, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351,  1, 10, 6, 18}, {384, 0,  9, 2,  8}, {403, 0,  5, 1,  1},
    };
    private static final int[][] KENNELS_HOARD = {{57, 0, 1, 1, 2}, {313, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static void kennels(Chunk c, Terrain t) {
        Site s = deep(t, c, KENNELS_CELL, 0x39333900L, 35, 27, 14, Megaliths.RANK_KENNELS);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, y1 = y0 + 13;
        fill(c, x0, y0, z0, x0 + 34, y1, z0 + 26, 251, 8);
        // The corridor, lit the colour they lit it.
        fill(c, x0 + 2, y0 + 1, z0 + 11, x0 + 32, y0 + 5, z0 + 15, 0, 0);
        fill(c, x0 + 2, y0 + 1, z0 + 11, x0 + 32, y0 + 1, z0 + 15, 251, 15);
        fill(c, x0 + 2, y0 + 1, z0 + 13, x0 + 32, y0 + 1, z0 + 13, 9, 0);     // the feeding trench
        // Structure audit 2026-09-23: the red fittings are lit (glowstone behind the red glass; they
        // were red concrete and gave no light).
        for (int dx = 4; dx <= 30; dx += 4) {
            put(c, x0 + dx, y0 + 5, z0 + 12, 89, 0);
            put(c, x0 + dx, y0 + 4, z0 + 12, 95, 14);
            put(c, x0 + dx, y0 + 5, z0 + 14, 89, 0);
            put(c, x0 + dx, y0 + 4, z0 + 14, 95, 14);
        }
        // Cages, both sides, most of them open.
        // Structure audit 2026-09-23: the bars stand on a kerb and the open mouths no longer cut into
        // the floor; the chests stand on the cage floor against the back wall (they were set flush into
        // the floor, and the middle north one sat where the chair room's floor erased it).
        for (int i = 0; i < 6; i++) for (int side = 0; side < 2; side++) {
            int bx = x0 + 3 + i * 5, bz = side == 0 ? z0 + 4 : z0 + 16;
            shell(c, bx, y0 + 1, bz, bx + 4, y0 + 5, bz + 6, 251, 8);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 3, y0 + 4, bz + 5, 0, 0);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 3, y0 + 1, bz + 5, 251, 7);
            int face = side == 0 ? bz + 6 : bz;
            fill(c, bx + 1, y0 + 1, face, bx + 3, y0 + 1, face, 251, 7);
            fill(c, bx + 1, y0 + 2, face, bx + 3, y0 + 4, face, 101, 0);
            if ((i + side) % 3 != 0) fill(c, bx + 2, y0 + 2, face, bx + 2, y0 + 3, face, 0, 0);
            put(c, bx + 2, y0 + 4, bz + 3, 89, 0);
            for (int k = 0; k < 3; k++) put(c, bx + 1 + r.nextInt(3), y0 + 1, bz + 1 + r.nextInt(5), 352 > 0 ? 216 : 0, 0);
            if ((i & 1) == 0) mob(c, bx + 2, y0 + 1, bz + 3, EntityType.CAVE_SPIDER);
            if (i == 2 || i == 5) graded(c, s, bx + 3, y0 + 2, side == 0 ? bz + 1 : bz + 5, r, KENNELS_LOOT, i == 5);
        }
        // The room at the end, with the chair in it. The boss comes up here.
        fill(c, x0 + 12, y0 + 1, z0 + 9, x0 + 22, y0 + 7, z0 + 17, 0, 0);
        shell(c, x0 + 11, y0, z0 + 8, x0 + 23, y0 + 8, z0 + 18, 251, 15);
        fill(c, x0 + 12, y0 + 1, z0 + 9, x0 + 22, y0 + 1, z0 + 17, 251, 7);
        fill(c, x0 + 16, y0 + 2, z0 + 12, x0 + 18, y0 + 3, z0 + 14, 101, 0);
        fill(c, x0 + 17, y0 + 2, z0 + 13, x0 + 17, y0 + 2, z0 + 13, 53, 2);
        put(c, x0 + 17, y0 + 7, z0 + 13, 169, 0);
        mob(c, x0 + 17, y0 + 1, z0 + 13, EntityType.CAVE_SPIDER);      // the boss spawns here
        // Structure audit 2026-09-23: the chair room stands across the middle of the corridor (the
        // boss offset in Containment is fixed there), and it used to wall the corridor into two sealed
        // halves and cut the four middle cages off into sealed pockets. The corridor now runs through
        // it by a doorway in each end wall, the cut-off cages look into it through barred fronts in its
        // side walls (open like the rest), and it is fitted out as the handlers' room: red lights like
        // the corridor's, a desk facing the chair, a hose-down pail and a drain, and the clipboard by
        // the door that says why the cages are open.
        fill(c, x0 + 11, y0 + 2, z0 + 12, x0 + 11, y0 + 4, z0 + 14, 0, 0);
        fill(c, x0 + 23, y0 + 2, z0 + 12, x0 + 23, y0 + 4, z0 + 14, 0, 0);
        for (int bx = x0 + 14; bx <= x0 + 19; bx += 5) for (int wz = z0 + 8; wz <= z0 + 18; wz += 10) {
            fill(c, bx, y0 + 2, wz, bx + 2, y0 + 4, wz, 101, 0);
            if (bx != x0 + 19 || wz != z0 + 8) fill(c, bx + 1, y0 + 2, wz, bx + 1, y0 + 3, wz, 0, 0);
        }
        for (int dx = 13; dx <= 21; dx += 8) { put(c, x0 + dx, y0 + 7, z0 + 13, 89, 0); put(c, x0 + dx, y0 + 6, z0 + 13, 95, 14); }
        fill(c, x0 + 17, y0 + 2, z0 + 17, x0 + 18, y0 + 2, z0 + 17, 251, 15);
        put(c, x0 + 17, y0 + 3, z0 + 17, 69, 5);
        put(c, x0 + 12, y0 + 2, z0 + 17, 118, 0);
        put(c, x0 + 17, y0 + 1, z0 + 15, 167, 8);
        sign(c, x0 + 24, y0 + 3, z0 + 11, 68, 5, "04:10 OPENED", "THE CAGES.", "I HEARD MY", "SISTER IN 4.");
        graded(c, s, x0 + 21, y0 + 2, z0 + 16, r, KENNELS_LOOT, true);
        trove(c, x0 + 12, y0 + 2, z0 + 10, r, KENNELS_LOOT, KENNELS_HOARD, 15 + r.nextInt(6));
        // The way in comes down into the east end of the corridor (it stopped on the roof of the
        // block, seven blocks of concrete above it); the riser's wall is cut back out of the corridor.
        riser(c, t, x0 + 33, z0 + 12, y0 + 2, 251, 8);
        fill(c, x0 + 32, y0 + 2, z0 + 12, x0 + 32, y0 + 5, z0 + 12, 0, 0);
    }

    // == 60. The Warren ==========================================================
    // Forty metres square of tunnel with no light fitting anywhere in it, a dormitory at
    // the middle with the beds made, and a door log by the stair that stops mid-sentence.
    // Bring your own light. The things down here can see you either way.

    private static final int[][] WARREN_LOOT = {
        {411, 0,  9, 2,  6}, {415, 0,  9, 2,  6}, {124, 0,  7, 1,  3}, {266, 0, 11, 3,  9},
        {265, 0, 11, 4, 12}, {297, 0, 11, 2,  6}, {367, 0, 12, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351,  8, 10, 6, 18}, {384, 0,  9, 2,  8}, {403, 0,  5, 1,  1},
    };
    private static final int[][] WARREN_HOARD = {{57, 0, 1, 1, 2}, {138, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static void warren(Chunk c, Terrain t) {
        Site s = deep(t, c, WARREN_CELL, 0x39363600L, 41, 41, 12, Megaliths.RANK_WARREN);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, y1 = y0 + 11;
        fill(c, x0, y0, z0, x0 + 40, y1, z0 + 40, 251, 15);
        // A lattice of tunnels, three wide and four high, and nothing to see by.
        for (int lane = 4; lane <= 36; lane += 8) {
            fill(c, x0 + 2, y0 + 1, z0 + lane, x0 + 38, y0 + 4, z0 + lane + 2, 0, 0);
            fill(c, x0 + lane, y0 + 1, z0 + 2, x0 + lane + 2, y0 + 4, z0 + 38, 0, 0);
        }
        for (int lane = 4; lane <= 36; lane += 8) {
            fill(c, x0 + 2, y0, z0 + lane, x0 + 38, y0, z0 + lane + 2, 251, 7);
            fill(c, x0 + lane, y0, z0 + 2, x0 + lane + 2, y0, z0 + 38, 251, 7);
        }
        // Dormitory at the middle, beds made, nobody in them.
        fill(c, x0 + 15, y0 + 1, z0 + 15, x0 + 25, y0 + 6, z0 + 25, 0, 0);
        shell(c, x0 + 14, y0, z0 + 14, x0 + 26, y0 + 7, z0 + 26, 251, 8);
        fill(c, x0 + 15, y0, z0 + 15, x0 + 25, y0, z0 + 25, 5, 1);
        // Structure audit 2026-09-23: the doorways line up with the tunnels (the north/south ones
        // were one block off, and the middle east-west tunnel ran blind into the dorm walls).
        fill(c, x0 + 20, y0 + 1, z0 + 14, x0 + 22, y0 + 4, z0 + 14, 0, 0);
        fill(c, x0 + 20, y0 + 1, z0 + 26, x0 + 22, y0 + 4, z0 + 26, 0, 0);
        fill(c, x0 + 14, y0 + 1, z0 + 20, x0 + 14, y0 + 4, z0 + 22, 0, 0);
        fill(c, x0 + 26, y0 + 1, z0 + 20, x0 + 26, y0 + 4, z0 + 22, 0, 0);
        // The beds run north-south, pillow to the north by the nightstand (they were two orphan halves
        // side by side, which the first neighbour update would have removed).
        for (int i = 0; i < 4; i++) {
            int bx = x0 + 16 + (i % 2) * 7, bz = z0 + 17 + (i / 2) * 5;
            put(c, bx, y0 + 1, bz, 26, 10); put(c, bx, y0 + 1, bz + 1, 26, 2);
            put(c, bx + 1, y0 + 1, bz, 44, 7);
        }
        // Lockers along the east wall, and a table with stools in the south-west corner.
        fill(c, x0 + 25, y0 + 1, z0 + 15, x0 + 25, y0 + 2, z0 + 17, 5, 1);
        fill(c, x0 + 25, y0 + 1, z0 + 23, x0 + 25, y0 + 2, z0 + 25, 5, 1);
        fill(c, x0 + 18, y0 + 1, z0 + 23, x0 + 18, y0 + 1, z0 + 24, 126, 9);
        fill(c, x0 + 19, y0 + 1, z0 + 23, x0 + 19, y0 + 1, z0 + 24, 44, 7);
        mob(c, x0 + 20, y0 + 1, z0 + 20, EntityType.STRAY);            // the boss spawns here
        graded(c, s, x0 + 24, y0 + 1, z0 + 24, r, WARREN_LOOT, true);
        trove(c, x0 + 16, y0 + 1, z0 + 24, r, WARREN_LOOT, WARREN_HOARD, 15 + r.nextInt(6));
        // Side rooms off the lattice, and the things waiting in them.
        int[][] rooms = {{6, 6}, {30, 6}, {6, 30}, {30, 30}, {18, 6}, {6, 18}};
        for (int i = 0; i < rooms.length; i++) {
            int bx = x0 + rooms[i][0], bz = z0 + rooms[i][1];
            fill(c, bx, y0 + 1, bz, bx + 4, y0 + 4, bz + 4, 0, 0);
            shell(c, bx - 1, y0, bz - 1, bx + 5, y0 + 5, bz + 5, 251, 8);
            fill(c, bx, y0, bz, bx + 4, y0, bz + 4, 251, 7);
            // Structure audit 2026-09-23: each cell has an iron door, left open, with a barred
            // viewing slot beside it and a slop pail in the far corner. The cell at (6, 18) has its
            // door in the east wall: the south one opened into the solid band between two tunnels
            // and sealed the cell and its spawner in.
            boolean east = rooms[i][0] == 6 && rooms[i][1] == 18;
            int dx = east ? bx + 5 : bx + 2, dz = east ? bz + 2 : bz + 5;
            put(c, dx, y0 + 1, dz, 71, east ? 4 : 5); put(c, dx, y0 + 2, dz, 71, 8);
            put(c, east ? dx : dx - 1, y0 + 2, east ? dz - 1 : dz, 101, 0);
            put(c, bx, y0 + 1, bz, 118, 0);
            // Claw rakes worn into the back wall, opposite the door, down to the black behind it.
            for (int k = 1; k <= 3; k += 2) {
                if (east) fill(c, bx - 1, y0 + 2, bz + k, bx - 1, y0 + 3, bz + k, 251, 15);
                else fill(c, bx + k, y0 + 2, bz - 1, bx + k, y0 + 3, bz - 1, 251, 15);
            }
            if ((i & 1) == 0) graded(c, s, bx + 4, y0 + 1, bz + 4, r, WARREN_LOOT, i == 4);
            mob(c, bx + 2, y0 + 1, bz + 2, EntityType.STRAY);
            // The webs hang from the ceiling, or lower down against a wall; they used to float in
            // mid-cell and could land on (and delete) the cell's chest or spawner. Same draws.
            for (int k = 0; k < 4; k++) {
                int wx = bx + r.nextInt(5), wy = r.nextInt(3), wz = bz + r.nextInt(5);
                boolean wall = wx == bx || wx == bx + 4 || wz == bz || wz == bz + 4;
                put(c, wx, y0 + (wall ? 2 + wy : 4), wz, 30, 0);
            }
        }
        fill(c, x0 + 20, y0 + 1, z0 + 2, x0 + 20, y1 - 1, z0 + 2, 0, 0);
        fill(c, x0 + 20, y0 + 1, z0 + 2, x0 + 20, y1 - 1, z0 + 2, 65, 3);
        // The door log by the stair, on a plank ledge; it stops mid-entry.
        put(c, x0 + 22, y0 + 1, z0 + 2, 5, 1);
        sign(c, x0 + 22, y0 + 2, z0 + 2, 68, 3, "DOOR LOG", "03:12 IN   4", "03:40 OUT  3", "04:0");
        riser(c, t, x0 + 20, z0 + 2, y1, 251, 15);
    }

    // == 61. The Viewing Room ====================================================
    // One cell, one photograph on a stand in the middle of it, and a gallery behind
    // fifteen centimetres of glass so the staff could watch what happened when somebody
    // looked. The glass is broken. The photograph is still on the stand.

    private static final int[][] VIEWING_LOOT = {
        { 25, 0,  8, 1,  3}, { 23, 0,  7, 1,  2}, {147, 0,  8, 1,  3}, {266, 0, 11, 3,  9},
        {339, 0, 12, 6, 18}, {340, 0, 11, 2,  6}, {265, 0, 11, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351,  7, 10, 6, 18}, {384, 0,  9, 2,  8}, {403, 0,  5, 1,  1},
    };
    private static final int[][] VIEWING_HOARD = {{57, 0, 1, 1, 2}, {311, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static void viewing(Chunk c, Terrain t) {
        Site s = deep(t, c, VIEWING_CELL, 0x30393600L, 31, 27, 16, Megaliths.RANK_VIEWING);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, y1 = y0 + 15;
        fill(c, x0, y0, z0, x0 + 30, y1, z0 + 26, 251, 0);
        // The cell: bare, white, and nine metres across.
        fill(c, x0 + 6, y0 + 1, z0 + 5, x0 + 24, y0 + 9, z0 + 21, 0, 0);
        shell(c, x0 + 5, y0, z0 + 4, x0 + 25, y0 + 10, z0 + 22, 251, 0);
        fill(c, x0 + 6, y0, z0 + 5, x0 + 24, y0, z0 + 21, 251, 8);
        for (int dx = 8; dx <= 22; dx += 4) put(c, x0 + dx, y0 + 9, z0 + 13, 89, 0);
        // The stand, and what is on it.
        fill(c, x0 + 15, y0 + 1, z0 + 12, x0 + 15, y0 + 2, z0 + 14, 43, 8);   // 43:8, was iron block
        fill(c, x0 + 14, y0 + 3, z0 + 12, x0 + 16, y0 + 4, z0 + 14, 155, 0);
        // Structure audit 2026-09-23: the photograph is a sheet lying face down on the stand (it was a
        // wool cube), and the instruction stands in front of it, facing the ladder.
        put(c, x0 + 15, y0 + 5, z0 + 13, 171, 0);
        sign(c, x0 + 15, y0 + 1, z0 + 9, 63, 8, "DO NOT", "LOOK AT THE", "PHOTOGRAPH", "");
        mob(c, x0 + 15, y0 + 1, z0 + 13, EntityType.HUSK);             // the boss spawns here
        for (int i = 0; i < 30; i++) {                                 // the marks on the walls
            // Structure audit 2026-09-23: in the wall itself (they stood a block in front of it).
            int wx = x0 + 6 + r.nextInt(19), wz = (r.nextBoolean() ? z0 + 4 : z0 + 22);
            put(c, wx, y0 + 1 + r.nextInt(6), wz, 159, 14);
        }
        // Lights set into the cell walls at head height; the ones under the ceiling do not reach the
        // floor of a room this tall (none on the ladder's wall behind the ladder).
        for (int[] p : new int[][] {{5, 9}, {5, 17}, {25, 9}, {25, 17}, {10, 4}, {20, 4}, {10, 22}, {15, 22}, {20, 22}})
            put(c, x0 + p[0], y0 + 4, z0 + p[1], 89, 0);
        // The gallery, behind the glass that did not hold.
        fill(c, x0 + 6, y0 + 11, z0 + 5, x0 + 24, y0 + 14, z0 + 12, 0, 0);
        fill(c, x0 + 6, y0 + 11, z0 + 5, x0 + 24, y0 + 11, z0 + 12, 251, 8);
        fill(c, x0 + 6, y0 + 12, z0 + 13, x0 + 24, y0 + 14, z0 + 13, 95, 0);
        fill(c, x0 + 15, y0 + 12, z0 + 13, x0 + 16, y0 + 14, z0 + 13, 0, 0);
        // Structure audit 2026-09-23: the gallery sits on the cell's ceiling, so its glass wall faced
        // concrete. The strip of floor in front of the glass is now the window -- two courses of glass
        // looking down into the cell -- and the break is in it, over the room (the two breaks in the
        // wall opened onto concrete; the one left in the wall is the way through to the corridor).
        fill(c, x0 + 7, y0 + 10, z0 + 10, x0 + 23, y0 + 11, z0 + 12, 95, 0);
        for (int dx = 10; dx <= 20; dx += 10) fill(c, x0 + dx, y0 + 10, z0 + 11, x0 + dx + 1, y0 + 11, z0 + 12, 0, 0);
        for (int dx = 8; dx <= 22; dx += 3) {
            put(c, x0 + dx, y0 + 12, z0 + 6, 251, 15);
            put(c, x0 + dx, y0 + 13, z0 + 6, 95, 5);
            put(c, x0 + dx, y0 + 12, z0 + 8, 44, 7);
        }
        put(c, x0 + 15, y0 + 14, z0 + 9, 89, 0);
        graded(c, s, x0 + 7, y0 + 12, z0 + 7, r, VIEWING_LOOT, false);
        graded(c, s, x0 + 23, y0 + 12, z0 + 11, r, VIEWING_LOOT, true);
        mob(c, x0 + 20, y0 + 12, z0 + 9, EntityType.HUSK);
        // The camera corridor out, and the store at the end of it.
        fill(c, x0 + 14, y0 + 11, z0 + 14, x0 + 16, y0 + 14, z0 + 24, 0, 0);
        fill(c, x0 + 14, y0 + 11, z0 + 14, x0 + 16, y0 + 11, z0 + 24, 251, 8);
        for (int dz = 15; dz <= 23; dz += 3) {
            put(c, x0 + 14, y0 + 13, z0 + dz, 251, 15);
            put(c, x0 + 16, y0 + 13, z0 + dz, 251, 15);
            put(c, x0 + 15, y0 + 14, z0 + dz, 89, 0);
        }
        // Structure audit 2026-09-23: the store is a room off the end of the corridor, with shelves
        // on its wall and the box under them (it was one chest standing in the corridor).
        fill(c, x0 + 11, y0 + 12, z0 + 22, x0 + 13, y0 + 14, z0 + 24, 0, 0);
        fill(c, x0 + 11, y0 + 11, z0 + 22, x0 + 13, y0 + 11, z0 + 24, 251, 8);
        fill(c, x0 + 11, y0 + 13, z0 + 24, x0 + 13, y0 + 13, z0 + 24, 44, 15);
        put(c, x0 + 11, y0 + 13, z0 + 22, 44, 15);
        put(c, x0 + 12, y0 + 15, z0 + 23, 89, 0);
        trove(c, x0 + 11, y0 + 12, z0 + 23, r, VIEWING_LOOT, VIEWING_HOARD, 15 + r.nextInt(6));
        fill(c, x0 + 15, y0 + 12, z0 + 5, x0 + 15, y0 + 10, z0 + 5, 0, 0);
        // The ladder down into the cell hangs on the cell's north wall (65:2 hung it on open air).
        fill(c, x0 + 15, y0 + 1, z0 + 5, x0 + 15, y0 + 11, z0 + 5, 65, 3);
        // The riser's ladder comes down to the corridor floor (it stopped three blocks above it).
        fill(c, x0 + 15, y0 + 12, z0 + 24, x0 + 15, y0 + 14, z0 + 24, 65, 2);
        riser(c, t, x0 + 15, z0 + 24, y1, 251, 0);
    }

    // == 62. The Signal ==========================================================
    // A mast on a hill, a hut at the foot of it with the transmitter still warm, and a
    // loop playing on the loudspeaker that is not the loop they left running. Whatever
    // is answering it comes up the hill at night and waits at the fence.

    private static final int[][] SIGNAL_LOOT = {
        {333, 0,  6, 1,  1}, {428, 0,  8, 2,  6}, {431, 0,  8, 2,  6}, {331, 0, 12, 6, 18},
        {265, 0, 11, 4, 12}, {348, 0, 10, 4, 12}, {266, 0, 10, 3,  9}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351,  9, 10, 6, 18}, {384, 0,  9, 2,  8}, {403, 0,  5, 1,  1},
    };
    private static final int[][] SIGNAL_HOARD = {{57, 0, 1, 1, 1}, {138, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static void signal(Chunk c, Terrain t) {
        Site s = ground(t, c, SIGNAL_CELL, 0x31343731L, 25, 21, 7, Megaliths.RANK_SIGNAL);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        for (int dx = 0; dx <= 24; dx++) for (int dz = 0; dz <= 20; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x0 + 24, y0, z0 + 20, 251, 8);
        // The fence, and the gate in it.
        for (int dx = 0; dx <= 24; dx++) for (int dz = 0; dz <= 20; dz++) {
            if (dx != 0 && dx != 24 && dz != 0 && dz != 20) continue;
            if (dz == 20 && dx >= 11 && dx <= 13) continue;
            put(c, x0 + dx, y0 + 1, z0 + dz, 251, 7);
            fill(c, x0 + dx, y0 + 2, z0 + dz, x0 + dx, y0 + 4, z0 + dz, 101, 0);
        }
        // The mast: four legs, cross-bracing, and the dishes at the top.
        int tx = x0 + 11, tz = z0 + 9;
        for (int dy = 1; dy <= 22; dy++) {
            int spread = dy > 16 ? 1 : 2;
            // Mast legs: smooth stone double slab 43:8 (was iron block).
            put(c, tx - spread, y0 + dy, tz - spread, 43, 8);
            put(c, tx + spread, y0 + dy, tz - spread, 43, 8);
            put(c, tx - spread, y0 + dy, tz + spread, 43, 8);
            put(c, tx + spread, y0 + dy, tz + spread, 43, 8);
            if (dy % 3 == 0) shell(c, tx - spread, y0 + dy, tz - spread, tx + spread, y0 + dy, tz + spread, 101, 0);
            if (dy % 6 == 0) put(c, tx, y0 + dy, tz, 169, 0);
        }
        fill(c, tx - 2, y0 + 20, tz - 2, tx + 2, y0 + 20, tz + 2, 44, 7);
        // Structure audit 2026-09-23: the ladder hangs on a feeder duct up the inside of the mast (it
        // hung on open air and bars) and comes up through a hatch in the platform (the platform was laid
        // over its top); the corner leg above the hatch is left off so the climber can step out.
        fill(c, tx - 1, y0 + 1, tz, tx - 1, y0 + 21, tz, 251, 8);
        fill(c, tx - 1, y0 + 1, tz - 1, tx - 1, y0 + 21, tz - 1, 65, 2);
        put(c, tx - 1, y0 + 22, tz - 1, 0, 0);
        for (int a = 0; a < 4; a++) {
            double ang = a * Math.PI / 2;
            int px = tx + (int) Math.round(Math.cos(ang) * 3), pz = tz + (int) Math.round(Math.sin(ang) * 3);
            // The dish posts are fixed to the platform's edge (they stood on nothing a block outside it).
            fill(c, px, y0 + 20, pz, px, y0 + 23, pz, 155, 0);
            put(c, px, y0 + 24, pz, 169, 0);
        }
        mob(c, tx, y0 + 20, tz, EntityType.WOLF);                      // the boss waits on the platform
        graded(c, s, tx + 1, y0 + 20, tz + 1, r, SIGNAL_LOOT, true);
        // The hut, with the transmitter still warm.
        int hx = x0 + 17, hz = z0 + 3;
        shell(c, hx, y0 + 1, hz, hx + 6, y0 + 5, hz + 8, 159, 8);
        fill(c, hx + 1, y0 + 1, hz + 1, hx + 5, y0 + 4, hz + 7, 0, 0);
        fill(c, hx, y0 + 6, hz - 1, hx + 6, y0 + 6, hz + 9, 44, 7);
        fill(c, hx + 2, y0 + 1, hz + 8, hx + 3, y0 + 3, hz + 8, 0, 0);
        for (int dz = 2; dz <= 6; dz += 2) {
            put(c, hx + 5, y0 + 1, hz + dz, 251, 15);
            put(c, hx + 5, y0 + 2, hz + dz, 95, 5);
            put(c, hx + 5, y0 + 3, hz + dz, 251, 15);
        }
        put(c, hx + 1, y0 + 1, hz + 1, 25, 0);
        // Structure audit 2026-09-23: the loop player beside the transmitter, a seat at the console,
        // controls on it, and two windows looking at the mast.
        put(c, hx + 1, y0 + 1, hz + 2, 84, 0);
        put(c, hx + 4, y0 + 1, hz + 2, 156, 1);
        put(c, hx + 4, y0 + 3, hz + 4, 77, 2); put(c, hx + 4, y0 + 3, hz + 6, 69, 2);
        put(c, hx, y0 + 2, hz + 3, 160, 8); put(c, hx, y0 + 2, hz + 5, 160, 8);
        put(c, hx + 3, y0 + 4, hz + 4, 89, 0);
        graded(c, s, hx + 1, y0 + 1, hz + 6, r, SIGNAL_LOOT, false);
        mob(c, hx + 3, y0 + 1, hz + 4, EntityType.WOLF);
        // The cable trench down to the buried repeater, which is where the rest of it is.
        int ry = y0 - 10;
        fill(c, x0 + 4, ry, z0 + 4, x0 + 4, y0, z0 + 4, 0, 0);
        fill(c, x0 + 4, ry, z0 + 4, x0 + 4, y0 - 1, z0 + 4, 65, 4);
        fill(c, x0 + 2, ry, z0 + 2, x0 + 9, ry + 4, z0 + 9, 0, 0);
        shell(c, x0 + 1, ry - 1, z0 + 1, x0 + 10, ry + 5, z0 + 10, 251, 8);
        fill(c, x0 + 2, ry - 1, z0 + 2, x0 + 9, ry - 1, z0 + 9, 251, 15);
        for (int dx = 3; dx <= 8; dx += 2) fill(c, x0 + dx, ry, z0 + 8, x0 + dx, ry + 2, z0 + 8, 251, 14);   // was redstone
        // Structure audit 2026-09-23: the shaft reaches the room (its shell used to be drawn over the
        // foot of the ladder, sealing the room in). The ladder runs from the room floor up through the
        // roof into the pad, on the cable conduit where the room has no wall to hang it on; the cable
        // runs on in a tray under the ceiling to the racks, which carry their repeaters.
        fill(c, x0 + 5, ry, z0 + 4, x0 + 5, ry + 4, z0 + 4, 251, 7);
        fill(c, x0 + 4, ry, z0 + 4, x0 + 4, y0, z0 + 4, 65, 4);
        fill(c, x0 + 6, ry + 4, z0 + 4, x0 + 6, ry + 4, z0 + 8, 251, 7);
        fill(c, x0 + 3, ry + 4, z0 + 8, x0 + 7, ry + 4, z0 + 8, 251, 7);
        for (int dx = 3; dx <= 8; dx += 2) put(c, x0 + dx, ry + 3, z0 + 8, 93, 0);
        // The technician's bench on the east wall: a stone-slab top and the test switch on it.
        fill(c, x0 + 9, ry, z0 + 5, x0 + 9, ry, z0 + 6, 44, 8);
        put(c, x0 + 9, ry + 1, z0 + 6, 69, 5);
        put(c, x0 + 5, ry + 4, z0 + 5, 89, 0);
        mob(c, x0 + 8, ry, z0 + 3, EntityType.WOLF);
        trove(c, x0 + 3, ry, z0 + 3, r, SIGNAL_LOOT, SIGNAL_HOARD, 14 + r.nextInt(6));
    }

    // == local helpers (structure audit 2026-09-23) ================================

    /** A wall (68) or standing (63) sign with its text, in this chunk only. */
    private static void sign(Chunk c, int wx, int wy, int wz, int id, int data, String... lines) {
        org.bukkit.block.Block b = Megaliths.blockAt(c, wx, wy, wz);
        if (b == null) return;
        put(c, wx, wy, wz, id, data);
        try {
            org.bukkit.block.Sign sign = (org.bukkit.block.Sign) b.getState();
            for (int i = 0; i < lines.length && i < 4; i++) sign.setLine(i, lines[i]);
            sign.update(true, false);
        } catch (RuntimeException ignored) { }
    }
}
