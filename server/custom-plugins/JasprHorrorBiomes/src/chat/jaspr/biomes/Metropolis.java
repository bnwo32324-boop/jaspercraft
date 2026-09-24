package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import static chat.jaspr.biomes.Megaliths.Site;
import static chat.jaspr.biomes.Megaliths.fill;
import static chat.jaspr.biomes.Megaliths.footing;
import static chat.jaspr.biomes.Megaliths.graded;
import static chat.jaspr.biomes.Megaliths.ground;
import static chat.jaspr.biomes.Megaliths.mob;
import static chat.jaspr.biomes.Megaliths.put;
import static chat.jaspr.biomes.Megaliths.shell;
import static chat.jaspr.biomes.Megaliths.trove;

/**
 * Metropolis: the built environment, after.
 *
 * A downtown that took the blast and stayed up, a stretch of motorway on piers with the
 * middle span in the ditch, a curtain-walled tower with twenty floors and something on
 * the roof, a hotel, a motel, and a fast food place with a bell on it.
 *
 * Everything here pays by depth rather than by a number written at each chest: the
 * graded() call works out how far inside the footprint and how far off the ground floor
 * a box is and stocks it accordingly, so the top of the tower and the bottom of the bank
 * vault are worth the walk and the lobby is not.
 */
public final class Metropolis {
    private Metropolis() { }

    private static final int OLDTOWN_CELL = 134, HIGHWAY_CELL = 120, SPIRE_CELL = 87;
    private static final int HOTEL_CELL = 85, TACO_CELL = 79, MOTEL_CELL = 75;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        oldTown(c, t);
        interchange(c, t);
        spire(c, t);
        hotel(c, t);
        bell(c, t);
        motel(c, t);
    }

    // == 38. Old Town ============================================================
    // Six blocks of a downtown that was hit and did not fall over. Mid-rise frames with
    // the cladding off them, cars burnt where they stopped, a checkpoint nobody manned in
    // time, a subway entrance full of water, and a municipal building leaning about four
    // degrees with its clock still on the front.

    private static final int[][] OLDTOWN_LOOT = {
        {262, 0, 13, 8, 24}, {289, 0, 13, 6, 18}, {265, 0, 12, 4, 12}, {263, 0, 12, 6, 18},
        {342, 0,  6, 1,  1}, {407, 0,  5, 1,  1}, {427, 0,  9, 2,  6}, {367, 0, 12, 4, 12},
        {266, 0,  9, 2,  6}, {351, 8, 10, 6, 18}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] OLDTOWN_TROVE = {
        { 57, 0, 1, 1, 2}, {130, 0, 1, 1, 1}, {403, 0, 1, 2, 3},
    };

    private static void oldTown(Chunk c, Terrain t) {
        Site s = ground(t, c, OLDTOWN_CELL, 0x464F5543L, 61, 61, 10, Megaliths.RANK_OLDTOWN);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 60, z1 = z0 + 60;
        for (int dx = 0; dx <= 60; dx++) for (int dz = 0; dz <= 60; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 7);
        // The ground the district stood in comes off down to the street, and a kerb wall holds
        // back whatever stands higher round the edge (the hillside used to bury the streets, the
        // yards and half the doors, and dead trees stood in the avenues).
        levelled(c, t, x0, z0, x1, z1, y0, y0 + s.hi - s.lo + 12, 98, 2, 251, 7);
        // Two avenues, kerbs, and the drifts of rubble that collected against them.
        fill(c, x0, y0, z0 + 28, x1, y0, z0 + 32, 251, 15);
        fill(c, x0 + 28, y0, z0, x0 + 32, y0, z1, 251, 15);
        for (int i = 0; i < 260; i++) {
            int gx = x0 + r.nextInt(61), gz = z0 + r.nextInt(61);
            int rubble = r.nextInt(4) == 0 ? 98 : (r.nextInt(3) == 0 ? 4 : 48), wear = r.nextInt(3);   // same draws
            put(c, gx, y0 + 1, gz, rubble, rubble == 98 ? wear : 0);
        }
        // Where each avenue meets the kerb wall a flight of steps goes up through it; how high
        // each one climbs is worked out first so the cars can be kept off the steps.
        int riseN = rise(t, x0 + 30, z0 - 1, y0), riseS = rise(t, x0 + 30, z1 + 1, y0);
        int riseW = rise(t, x0 - 1, z0 + 30, y0), riseE = rise(t, x1 + 1, z0 + 30, y0);
        // Six blocks of frame, most of them sheared off partway up.
        for (int gx = 0; gx < 2; gx++) for (int gz = 0; gz < 2; gz++) {
            int bx = x0 + 3 + gx * 33, bz = z0 + 3 + gz * 33;
            int hash = (int) (Terrain.mix(s.seed + gx * 7717L + gz * 3313L) >>> 9);
            int h = 9 + Math.floorMod(hash, 16);
            int wall = new int[]{98, 45, 251, 159}[Math.floorMod(hash >> 4, 4)];
            int data = wall == 98 ? 2 : (wall == 251 ? 8 : (wall == 159 ? 8 : 0));
            int shear = h - 3 - Math.floorMod(hash >> 11, 6);          // where it came off
            shell(c, bx, y0 + 1, bz, bx + 21, y0 + h, bz + 21, wall, data);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 20, y0 + h - 1, bz + 20, 0, 0);
            // The ground floor is the street slab itself; the storeys above are top slabs, so
            // what stands on them sits on them. The glazing now runs round all four faces.
            int top = 1;                                               // highest floor still standing
            for (int f = 1; f < h - 1; f += 4) {
                if (f > 1) fill(c, bx + 1, y0 + f, bz + 1, bx + 20, y0 + f, bz + 20, 44, 15);
                for (int dz = 2; dz <= 19; dz += 3) {
                    fill(c, bx, y0 + f + 1, bz + dz, bx, y0 + f + 2, bz + dz, 102, 0);
                    fill(c, bx + 21, y0 + f + 1, bz + dz, bx + 21, y0 + f + 2, bz + dz, 102, 0);
                }
                for (int dx = 2; dx <= 19; dx += 3) {                  // the shop keeps its back wall blind
                    if (f > 1 || gz == 1) fill(c, bx + dx, y0 + f + 1, bz, bx + dx, y0 + f + 2, bz, 102, 0);
                    if (f > 1 || gz == 0) fill(c, bx + dx, y0 + f + 1, bz + 21, bx + dx, y0 + f + 2, bz + 21, 102, 0);
                }
                // The columns stop at the break (their tops used to hang on above it).
                if (f <= shear - 1) {
                    top = f;
                    for (int dx = 4; dx <= 18; dx += 6) for (int dz = 4; dz <= 18; dz += 6)
                        fill(c, bx + dx, y0 + f, bz + dz, bx + dx, y0 + Math.min(f + 3, shear - 1), bz + dz, 251, 7);
                }
                // Upper-floor box in the corner office (it was drawn inside the ladder shaft, which
                // then erased it).
                if (f > 1 && ((f + gx) & 3) == 1)
                    graded(c, s, bx + 18, y0 + f + 1, bz + 18, r, OLDTOWN_LOOT, false);
                if (((f * 3 + gz) & 3) == 2)
                    mob(c, bx + 17, y0 + f + 1, bz + 17, f > 8 ? EntityType.SKELETON : EntityType.ZOMBIE);
                if (f > 1 && f + 2 < shear) frameOffice(c, bx, y0 + f, bz, hash + f);
            }
            // The door faces the street: the north row opens south onto the cross avenue (it
            // opened onto the site edge, where the ground is highest).
            int door = gz == 0 ? bz + 21 : bz;
            fill(c, bx + 8, y0 + 1, door, bx + 12, y0 + 4, door, 0, 0);
            fill(c, bx, y0 + shear, bz, bx + 21, y0 + h, bz + 21, 0, 0);
            // The break is ragged: the east and south walls came off lower toward their far ends.
            if (shear >= 6) for (int d = 6; d <= 21; d++) {
                fill(c, bx + 21, y0 + shear - d / 6, bz + d, bx + 21, y0 + shear - 1, bz + d, 0, 0);
                fill(c, bx + d, y0 + shear - d / 6, bz + 21, bx + d, y0 + shear - 1, bz + 21, 0, 0);
            }
            // The ladder is on the north wall in the north-west corner, from the floor up flush
            // with the last floor standing (it stood clear of every wall, started a jump off the
            // floor and ran on up past the break).
            if (top > 1) fill(c, bx + 1, y0 + 1, bz + 1, bx + 1, y0 + top, bz + 1, 65, 3);
            // The rubble is drawn where it always was, and laid once the rooms are furnished.
            int[] rx = new int[70], ry = new int[70], rz = new int[70], rid = new int[70];
            for (int i = 0; i < 70; i++) {
                rx[i] = bx + r.nextInt(22); ry[i] = y0 + shear - 1 + r.nextInt(2); rz[i] = bz + r.nextInt(22);
                rid[i] = r.nextInt(3) == 0 ? 98 : 4;
            }
            // The husk off the column it was set into (the south-east block's used to be under
            // the subway kiosk), and the box beside a column instead of under one, behind the
            // counter.
            mob(c, bx + 17, y0 + 1, bz + 7, EntityType.HUSK);
            graded(c, s, bx + 11, y0 + 1, gz == 0 ? bz + 5 : bz + 16, r, OLDTOWN_LOOT, gx == gz);
            // Ground floor: a shop, counter across it and shelving up the back wall. The
            // south-east block's is the subway concourse: benches along the back wall instead.
            if (shear > 5) {
                int cz = gz == 0 ? bz + 7 : bz + 14, sz = gz == 0 ? bz + 1 : bz + 20;
                boolean concourse = gx == 1 && gz == 1;
                if (!concourse) for (int dx = 6; dx <= 15; dx++) {
                    putIfAir(c, bx + dx, y0 + 1, cz, 251, 0);
                    putIfAir(c, bx + dx, y0 + 2, cz, 44, 7);
                }
                for (int dx = 3; dx <= 18; dx++) {
                    if (concourse) { if ((dx & 3) != 2) putIfAir(c, bx + dx, y0 + 1, sz, 109, 2); continue; }
                    for (int y = y0 + 1; y <= y0 + 3; y++) putIfAir(c, bx + dx, y, sz, 47, 0);
                }
            }
            // Rubble falls onto whatever stopped it (it hung in the air where it was drawn), but
            // not onto a box, a spawner, a pane or the way off the ladder.
            for (int i = 0; i < 70; i++) {
                if (rx[i] - bx <= 2 && rz[i] - bz <= 2) continue;
                debris(c, rx[i], ry[i], rz[i], rid[i], y0);
            }
        }
        // Cars, burnt where they stopped, and a checkpoint that went up too late. The cars are
        // black terracotta (black concrete vanished into the asphalt), drawn from their own
        // sequence so every chunk agrees where they are (a box stocked in one chunk used to move
        // the cars in that chunk only, leaving half cars), and kept off the steps, the checkpoint
        // and the municipal building's front.
        Random rc = new Random(s.seed ^ 0x43415253L);
        int nsLo = Math.max(4, riseN + 1), nsLen = 19 - nsLo;
        for (int i = 0; i < 12; i++) {
            boolean along = (i & 1) == 0;
            int cx2 = along ? x0 + 4 + rc.nextInt(53) : x0 + 29 + rc.nextInt(3);
            int cz2 = along ? z0 + 29 + rc.nextInt(3) : z0 + 4 + rc.nextInt(53);
            if (along) cx2 = Math.max(x0 + riseW + 1, Math.min(x1 - riseE - 4, cx2));
            else {
                int k = Math.floorMod(cz2 - z0 - 4, nsLen + 7);
                cz2 = z0 + (k < nsLen ? nsLo + k : 26 + k - nsLen);
            }
            fill(c, cx2, y0 + 1, cz2, cx2 + (along ? 3 : 1), y0 + 2, cz2 + (along ? 1 : 3), 159, 15);
            put(c, cx2, y0 + 3, cz2, 101, 0);
            if (i % 4 == 0) mob(c, cx2, y0 + 1, cz2 + 2, EntityType.CREEPER);
        }
        for (int dx = 25; dx <= 35; dx++) {                           // between the frames, not into them
            fill(c, x0 + dx, y0 + 1, z0 + 24, x0 + dx, y0 + 2, z0 + 24, 159, 12);
            if ((dx & 1) == 0) put(c, x0 + dx, y0 + 3, z0 + 24, 159, 13);
        }
        fill(c, x0 + 29, y0 + 1, z0 + 24, x0 + 31, y0 + 3, z0 + 24, 0, 0);
        mob(c, x0 + 30, y0 + 1, z0 + 22, EntityType.SKELETON);
        graded(c, s, x0 + 25, y0 + 1, z0 + 23, r, OLDTOWN_LOOT, false);
        // The subway entrance, with the water in it: a stair down from the south-east block's
        // lobby, each step on solid stone, into two feet of water on a mossy floor, landings at
        // the bottom where whoever last came down left things, and a grating where the tunnel
        // to the platforms was. (The water had replaced the floor and drained into a cave, the
        // stairs faced the wrong way and hung in the air, and the lid covered their headroom.)
        fill(c, x0 + 44, y0 - 7, z0 + 44, x0 + 50, y0, z0 + 50, 0, 0);
        shell(c, x0 + 43, y0 - 8, z0 + 43, x0 + 51, y0 + 1, z0 + 51, 98, 1);
        encase(c, x0 + 42, y0 - 9, z0 + 42, x0 + 52, y0 - 8, z0 + 52);
        fill(c, x0 + 44, y0 - 7, z0 + 44, x0 + 50, y0 - 6, z0 + 50, 9, 0);
        fill(c, x0 + 46, y0 + 1, z0 + 44, x0 + 48, y0 + 4, z0 + 46, 0, 0);   // the stairwell, and the column that stood over it
        for (int i = 0; i <= 6; i++) {
            if (i < 6) fill(c, x0 + 46, y0 - 7, z0 + 44 + i, x0 + 48, y0 - i - 1, z0 + 44 + i, 98, 0);
            fill(c, x0 + 46, y0 - i, z0 + 44 + i, x0 + 48, y0 - i, z0 + 44 + i, 109, 3);
        }
        put(c, x0 + 46, y0 - 7, z0 + 50, 98, 0); put(c, x0 + 47, y0 - 7, z0 + 50, 98, 0); put(c, x0 + 48, y0 - 7, z0 + 50, 98, 0);
        fill(c, x0 + 44, y0 - 7, z0 + 49, x0 + 45, y0 - 6, z0 + 50, 98, 0);
        fill(c, x0 + 49, y0 - 7, z0 + 49, x0 + 50, y0 - 6, z0 + 50, 98, 0);
        fill(c, x0 + 46, y0 - 5, z0 + 51, x0 + 48, y0 - 3, z0 + 51, 101, 0);
        fill(c, x0 + 46, y0 - 5, z0 + 52, x0 + 48, y0 - 3, z0 + 52, 0, 0);
        fill(c, x0 + 45, y0 + 2, z0 + 44, x0 + 45, y0 + 2, z0 + 46, 85, 0);   // railings round the stairwell
        fill(c, x0 + 49, y0 + 2, z0 + 44, x0 + 49, y0 + 2, z0 + 46, 85, 0);
        fill(c, x0 + 45, y0 + 2, z0 + 47, x0 + 49, y0 + 2, z0 + 47, 85, 0);
        put(c, x0 + 51, y0 - 3, z0 + 47, 89, 0);                      // set in the walls, not in the headroom
        put(c, x0 + 43, y0 - 3, z0 + 47, 89, 0);
        mob(c, x0 + 45, y0 - 5, z0 + 49, EntityType.CAVE_SPIDER);
        graded(c, s, x0 + 49, y0 - 5, z0 + 49, r, OLDTOWN_LOOT, true);
        // The municipal building, leaning, with its clock still on the front. It is nine wide so
        // it stands clear of the south-east block (it used to cut three blocks into it), the top
        // storey has slipped a block east over a cracked course -- the lean -- and it has
        // windows, a stair, and rooms that are for something (it was a blank box of four sealed
        // floors with the clock on a side wall).
        int mx = x0 + 26, mz = z0 + 40;
        shell(c, mx, y0 + 1, mz, mx + 8, y0 + 12, mz + 14, 155, 0);
        fill(c, mx + 1, y0 + 1, mz + 1, mx + 7, y0 + 12, mz + 13, 0, 0);
        shell(c, mx + 1, y0 + 13, mz, mx + 9, y0 + 18, mz + 14, 155, 0);
        fill(c, mx + 2, y0 + 13, mz + 1, mx + 8, y0 + 17, mz + 13, 0, 0);
        fill(c, mx + 1, y0 + 13, mz, mx + 9, y0 + 13, mz, 98, 2);         // the crack course
        fill(c, mx + 1, y0 + 13, mz + 14, mx + 9, y0 + 13, mz + 14, 98, 2);
        fill(c, mx + 1, y0 + 13, mz, mx + 1, y0 + 13, mz + 14, 98, 2);
        fill(c, mx + 9, y0 + 13, mz, mx + 9, y0 + 13, mz + 14, 98, 2);
        for (int d = 0; d <= 14; d += 3) { put(c, mx + 1, y0 + 13, mz + d, 4, 0); put(c, mx + 9, y0 + 13, mz + 14 - d, 4, 0); }
        put(c, mx + 9, y0 + 1, mz + 4, 155, 0); put(c, mx + 9, y0 + 1, mz + 9, 155, 0); put(c, mx + 9, y0 + 2, mz + 9, 44, 7);
        fill(c, mx + 1, y0 + 5, mz + 1, mx + 7, y0 + 5, mz + 13, 44, 15);
        fill(c, mx + 1, y0 + 9, mz + 1, mx + 7, y0 + 9, mz + 13, 44, 15);
        fill(c, mx + 2, y0 + 13, mz + 1, mx + 8, y0 + 13, mz + 13, 44, 15);
        fill(c, mx + 2, y0 + 1, mz, mx + 6, y0 + 4, mz, 0, 0);
        for (int dx = 1; dx <= 7; dx += 2) fill(c, mx + dx, y0 + 5, mz, mx + dx, y0 + 12, mz, 155, 2);
        for (int d = 2; d <= 12; d += 2) {                            // windows, some blown out
            for (int wy = y0 + 2; wy <= y0 + 11; wy += wy == y0 + 2 ? 5 : 4) {
                if (d <= 6 && wy > y0 + 2) pane(c, mx + d, wy, mz, s.seed);
                if (d <= 6) pane(c, mx + d, wy, mz + 14, s.seed);
                pane(c, mx, wy, mz + d, s.seed);
                pane(c, mx + 8, wy, mz + d, s.seed);
            }
            if (d <= 6) pane(c, mx + 1 + d, y0 + 15, mz + 14, s.seed);
            pane(c, mx + 1, y0 + 15, mz + d, s.seed);
            pane(c, mx + 9, y0 + 15, mz + d, s.seed);
        }
        pane(c, mx + 2, y0 + 15, mz, s.seed); pane(c, mx + 8, y0 + 15, mz, s.seed);
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {   // the clock, on the front
            int d = dy * dy + dx * dx;
            if (d <= 5) put(c, mx + 5 + dx, y0 + 15 + dy, mz, 251, d > 2 ? 7 : 0);
        }
        put(c, mx + 5, y0 + 15, mz, 251, 15); put(c, mx + 5, y0 + 16, mz, 251, 15); put(c, mx + 6, y0 + 15, mz, 251, 15);
        fill(c, mx + 3, y0 + 19, mz + 3, mx + 7, y0 + 19, mz + 11, 44, 7);
        // A stair a storey, alternating sides so each can stand on solid quartz, with its well
        // cut out of the floor above.
        for (int k = 0; k <= 4; k++) {
            if (k > 0) fill(c, mx + 6, y0 + 1, mz + 5 + k, mx + 7, y0 + k, mz + 5 + k, 155, 0);
            fill(c, mx + 6, y0 + 1 + k, mz + 5 + k, mx + 7, y0 + 1 + k, mz + 5 + k, 156, 2);
        }
        fill(c, mx + 6, y0 + 5, mz + 7, mx + 7, y0 + 5, mz + 8, 0, 0);
        for (int k = 0; k <= 3; k++) {
            if (k > 0) fill(c, mx + 1, y0 + 6, mz + 9 - k, mx + 2, y0 + 5 + k, mz + 9 - k, 155, 0);
            fill(c, mx + 1, y0 + 6 + k, mz + 9 - k, mx + 2, y0 + 6 + k, mz + 9 - k, 156, 3);
            if (k > 0) fill(c, mx + 6, y0 + 10, mz + 5 + k, mx + 7, y0 + 9 + k, mz + 5 + k, 155, 0);
            fill(c, mx + 6, y0 + 10 + k, mz + 5 + k, mx + 7, y0 + 10 + k, mz + 5 + k, 156, 2);
        }
        fill(c, mx + 1, y0 + 9, mz + 7, mx + 2, y0 + 9, mz + 8, 0, 0);
        fill(c, mx + 6, y0 + 13, mz + 6, mx + 7, y0 + 13, mz + 7, 0, 0);
        // Lobby: a desk facing the door, benches, a light. Records on the second floor, the
        // council table on the third, the mayor's office at the top.
        fill(c, mx + 2, y0 + 1, mz + 9, mx + 4, y0 + 1, mz + 9, 155, 0);
        put(c, mx + 2, y0 + 2, mz + 9, 140, 0); put(c, mx + 4, y0 + 2, mz + 9, 140, 0);
        fill(c, mx + 3, y0 + 1, mz + 1, mx + 5, y0 + 1, mz + 8, 171, 14);
        fill(c, mx + 1, y0 + 1, mz + 5, mx + 1, y0 + 1, mz + 6, 156, 1);
        put(c, mx + 4, y0 + 5, mz + 4, 89, 0);
        for (int dz = 2; dz <= 12; dz += 2) if (dz != 8 && dz != 10) fill(c, mx + 3, y0 + 6, mz + dz, mx + 5, y0 + 7, mz + dz, 47, 0);
        put(c, mx + 4, y0 + 9, mz + 11, 89, 0);
        fill(c, mx + 2, y0 + 10, mz + 9, mx + 5, y0 + 10, mz + 13, 171, 14);
        fill(c, mx + 3, y0 + 10, mz + 10, mx + 4, y0 + 10, mz + 12, 44, 15);
        put(c, mx + 3, y0 + 11, mz + 11, 140, 0);
        fill(c, mx + 2, y0 + 10, mz + 10, mx + 2, y0 + 10, mz + 12, 156, 1);
        fill(c, mx + 5, y0 + 10, mz + 10, mx + 5, y0 + 10, mz + 12, 156, 0);
        put(c, mx + 4, y0 + 13, mz + 11, 89, 0);
        fill(c, mx + 3, y0 + 14, mz + 10, mx + 5, y0 + 14, mz + 10, 44, 15);
        put(c, mx + 4, y0 + 14, mz + 11, 156, 2);
        fill(c, mx + 2, y0 + 14, mz + 2, mx + 2, y0 + 15, mz + 5, 47, 0);
        put(c, mx + 5, y0 + 18, mz + 7, 89, 0);
        mob(c, mx + 4, y0 + 10, mz + 7, EntityType.HUSK);                  // on the council floor (it was set in a slab)
        graded(c, s, mx + 4, y0 + 14, mz + 12, r, OLDTOWN_LOOT, true);
        // THE SURPRISE: the vault under the municipal building, still shut.
        int vy = y0 - 11;
        fill(c, mx + 1, vy, mz + 1, mx + 11, vy + 6, mz + 13, 0, 0);
        shell(c, mx, vy - 1, mz, mx + 12, vy + 7, mz + 14, 251, 7);
        fill(c, mx + 1, vy - 1, mz + 1, mx + 11, vy - 1, mz + 13, 251, 15);
        encase(c, mx - 1, vy - 2, mz - 1, mx + 13, vy + 4, mz + 15);      // rock round it where a cave ran close
        // Vault box: smooth stone double slab 43:8 (was iron block).
        fill(c, mx + 5, vy, mz + 7, mx + 11, vy + 5, mz + 13, 43, 8);
        fill(c, mx + 6, vy, mz + 8, mx + 10, vy + 4, mz + 12, 0, 0);
        // The round door stands open: a light grey ring proud of the box, the doorway inside it
        // over a sill at the floor, and the leaf swung back on its hinge against the south side
        // (the disc was the same metal as the box and flush with it, and the hole in it was two
        // blocks off the floor).
        for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            int d = dy * dy + dz * dz;
            if (d > 6) continue;
            if (d > 2) put(c, mx + 4, vy + 2 + dy, mz + 10 + dz, 251, 8);
            else put(c, mx + 5, vy + 2 + dy, mz + 10 + dz, 0, 0);
        }
        for (int dy = -2; dy <= 2; dy++) for (int dx = -1; dx <= 2; dx++)
            if (dy * dy + dx * dx <= 5) put(c, mx + 2 + dx, vy + 2 + dy, mz + 12, 251, 8);
        put(c, mx + 2, vy + 2, mz + 12, 43, 8);
        for (int dx = 7; dx <= 9; dx++) for (int dz = 9; dz <= 11; dz += 2)
            fill(c, mx + dx, vy, mz + dz, mx + dx, vy + 1, mz + dz, 251, 4);   // bullion: yellow concrete, was gold
        put(c, mx + 8, vy + 4, mz + 10, 89, 0);
        fill(c, mx + 5, vy + 1, mz + 1, mx + 10, vy + 3, mz + 1, 167, 5);  // the deposit boxes, doors hanging open
        put(c, mx + 3, vy + 7, mz + 8, 89, 0);
        fill(c, mx + 2, vy, mz + 6, mx + 3, vy, mz + 6, 44, 8);            // the teller's counter
        put(c, mx + 2, vy, mz + 5, 109, 2);
        mob(c, mx + 3, vy, mz + 4, EntityType.HUSK);
        mob(c, mx + 9, vy, mz + 3, EntityType.SKELETON);
        trove(c, mx + 8, vy, mz + 12, r, OLDTOWN_LOOT, OLDTOWN_TROVE, 15 + r.nextInt(6));
        // The way down, laid after the vault so its roof does not cut it off: a ladder on the
        // vault's west wall from the floor to a hatch in the lobby floor (only three rungs of it
        // used to survive, sealed between the vault roof and the lobby).
        fill(c, mx + 1, vy, mz + 2, mx + 1, y0, mz + 2, 65, 5);
        put(c, mx + 1, y0 + 1, mz + 2, 96, 3);
        // Steps up through the kerb wall at the ends of the avenues.
        ramp(c, x0 + 28, z0, 0, -1, 5, y0, riseN, 3);
        ramp(c, x0 + 28, z1, 0, 1, 5, y0, riseS, 2);
        ramp(c, x0, z0 + 28, -1, 0, 5, y0, riseW, 1);
        ramp(c, x1, z0 + 28, 1, 0, 5, y0, riseE, 0);
    }

    // == 39. The Interchange =====================================================
    // Six lanes on piers, a cloverleaf coming off it, and the middle span down in the
    // ditch with everything that was on it. The gantries are still up and the signs on
    // them still say where this was going.

    private static final int[][] HIGHWAY_LOOT = {
        { 265, 0, 13, 4, 12}, {263, 0, 12, 6, 18}, {2260, 0,  5, 1,  1}, {2258, 0, 5, 1, 1},
        {  70, 0,  8, 2,  6}, {351, 4, 10, 6, 18}, { 289, 0, 11, 4, 12}, { 262, 0, 12, 6, 18},
        { 367, 0, 11, 4, 12}, {266, 0,  9, 2,  6}, { 384, 0,  7, 2,  6}, { 403, 0, 4, 1, 1},
    };
    private static final int[][] HIGHWAY_TROVE = {
        { 42, 0, 1, 4, 8}, { 57, 0, 1, 1, 1}, {384, 0, 1, 8, 14},
    };

    private static void interchange(Chunk c, Terrain t) {
        Site s = ground(t, c, HIGHWAY_CELL, 0x48574159L, 61, 29, 12, Megaliths.RANK_HIGHWAY);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, dy = y0 + 9, cz = z0 + 10;
        // The service road under it all, laid first and on the ground as it goes (it was laid
        // flat at the lowest point and most of it vanished under the hill), never so high it
        // has no room under the deck, and never more than a block up or down from the last.
        int[] road = new int[61];
        for (int dx = 0; dx <= 60; dx++) road[dx] = Math.min(t.sample(x0 + dx, cz).y, dy - 4);
        for (int dx = 1; dx <= 60; dx++) road[dx] = Math.min(road[dx], road[dx - 1] + 1);
        for (int dx = 59; dx >= 0; dx--) road[dx] = Math.min(road[dx], road[dx + 1] + 1);
        for (int dx = 0; dx <= 60; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = x0 + dx, z = cz + dz, g = t.sample(x, z).y;
            if (g < road[dx] - 1) fill(c, x, Math.max(g + 1, road[dx] - 8), z, x, road[dx] - 1, z, 251, 7);
            put(c, x, road[dx], z, 251, 8);
            fill(c, x, road[dx] + 1, z, x, road[dx] + 3, z, 0, 0);
        }
        fill(c, x0, dy + 1, cz - 5, x0 + 60, dy + 7, cz + 5, 0, 0);     // nothing grows through the deck
        // Piers, every eight, down to whatever the ground is doing.
        for (int dx = 2; dx <= 58; dx += 8) {
            if (dx >= 26 && dx <= 34) continue;                       // the span that went
            for (int dz = -1; dz <= 1; dz++)
                for (int y = dy - 1; y >= y0 - 7; y--)
                    fill(c, x0 + dx, y, cz + dz * 3, x0 + dx + 1, y, cz + dz * 3, 251, 7);
            fill(c, x0 + dx - 1, dy - 1, cz - 4, x0 + dx + 2, dy - 1, cz + 4, 251, 7);
        }
        // The deck: nine wide, central barrier, and the paint still on it.
        for (int dx = 0; dx <= 60; dx++) {
            if (dx >= 27 && dx <= 33) continue;
            fill(c, x0 + dx, dy, cz - 4, x0 + dx, dy, cz + 4, 251, 15);
            put(c, x0 + dx, dy, cz, 251, 7);
            fill(c, x0 + dx, dy + 1, cz, x0 + dx, dy + 1, cz, 44, 7);
            if ((dx & 3) == 0) { put(c, x0 + dx, dy, cz - 2, 251, 0); put(c, x0 + dx, dy, cz + 2, 251, 0); }
            put(c, x0 + dx, dy + 1, cz - 4, 85, 0);
            put(c, x0 + dx, dy + 1, cz + 4, 85, 0);
        }
        // The gantries, with the signs still on them.
        for (int dx = 10; dx <= 50; dx += 20) {
            if (dx >= 26 && dx <= 34) continue;
            // Gantry steel: smooth stone double slab 43:8 (was iron block).
            fill(c, x0 + dx, dy + 1, cz - 5, x0 + dx, dy + 5, cz - 5, 43, 8);
            fill(c, x0 + dx, dy + 1, cz + 5, x0 + dx, dy + 5, cz + 5, 43, 8);
            fill(c, x0 + dx, dy + 5, cz - 5, x0 + dx, dy + 5, cz + 5, 43, 8);
            // Each leg stands on the pier under it (they hung in the air beside the deck).
            fill(c, x0 + dx, dy - 1, cz - 5, x0 + dx, dy, cz - 5, 251, 7);
            fill(c, x0 + dx, dy - 1, cz + 5, x0 + dx, dy, cz + 5, 251, 7);
            fill(c, x0 + dx, dy + 3, cz - 3, x0 + dx, dy + 4, cz + 3, 251, 13);
            put(c, x0 + dx, dy + 4, cz, 251, 0);
            mob(c, x0 + dx, dy + 1, cz + 3, EntityType.SKELETON);
        }
        // The cloverleaf, curling down off the eastern end. It holds the deck's level until it is
        // clear of the deck (it dipped under the deck and never came up through it), is five
        // wide with its rails along its edges (they stood across the lanes), and where the ground
        // comes up to meet it, it runs on the ground.
        int lcx = x0 + 52, lcz = cz + 9;
        int[][] surf = new int[27][27], owner = new int[27][27];
        for (int a = 0; a <= 28; a++) {
            double ang = a / 28.0 * Math.PI * 1.35;
            int rx = lcx + (int) Math.round(Math.sin(ang) * 9);
            int rz = lcz - (int) Math.round(Math.cos(ang) * 9);
            int ry = a <= 7 ? dy : Math.max(dy - ((a - 7) * 9 + 20) / 21, t.sample(rx, rz).y);
            fill(c, rx - 2, ry, rz - 2, rx + 2, ry, rz + 2, 251, 15);
            for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
                int u = rx + i - lcx + 13, v = rz + j - lcz + 13;
                if (surf[u][v] == 0 || ry > surf[u][v]) { surf[u][v] = ry; owner[u][v] = a; }
                if (a <= 7) {                                          // off the deck across its rail
                    Block b = Megaliths.blockAt(c, rx + i, dy + 1, rz + j);
                    if (b != null && b.getType() == Material.FENCE) put(c, rx + i, dy + 1, rz + j, 0, 0);
                }
            }
            if (a % 9 == 0) for (int y = ry - 1; y >= y0 - 5; y--) put(c, rx, y, rz, 251, 7);
        }
        for (int u = 0; u < 27; u++) for (int v = 0; v < 27; v++) {
            if (surf[u][v] == 0) continue;
            int x = lcx + u - 13, z = lcz + v - 13;
            if (z <= cz + 4 && x <= x0 + 60) continue;                // on the deck itself
            fill(c, x, surf[u][v] + 1, z, x, surf[u][v] + 3, z, 0, 0);
            if (owner[u][v] <= 7) continue;
            double rad = Math.sqrt((u - 13) * (u - 13) + (v - 13) * (v - 13));
            if (rad > 10.6 || rad < 7.4) put(c, x, surf[u][v] + 1, z, 85, 0);
        }
        // The wrecks, and the span that is now in the ditch. Every wreck is drawn before any of
        // them is stocked, so every chunk agrees where the wrecks are (a box stocked in one chunk
        // moved the later wrecks in that chunk only). Burnt ones are black terracotta (black
        // concrete disappeared into the deck); the zombie stands beside its car on the deck (it
        // hung off the edge); the boot is open (the box was sealed inside the car); and no car
        // hangs over the gap.
        int[] wxs = new int[10], wzs = new int[10];
        boolean[] burnt = new boolean[10];
        for (int i = 0; i < 10; i++) {
            wxs[i] = x0 + 3 + r.nextInt(55);
            if (wxs[i] >= 26 + x0 && wxs[i] <= 34 + x0) { wxs[i] = -1; continue; }
            if (wxs[i] == x0 + 25) wxs[i] = x0 + 24;
            wzs[i] = cz - 3 + r.nextInt(7);
            burnt[i] = r.nextInt(3) == 0;
        }
        for (int i = 0; i < 10; i++) {
            int wx = wxs[i], wz = wzs[i];
            if (wx < 0) continue;
            fill(c, wx, dy + 1, wz, wx + 2, dy + 2, wz + 1, burnt[i] ? 159 : 43, burnt[i] ? 15 : 8);
            put(c, wx + 1, dy + 3, wz, 101, 0);
            if (i % 3 == 0) mob(c, wx - 1, dy + 1, wz, EntityType.ZOMBIE);
            if (i % 4 == 1) {
                graded(c, s, wx + 2, dy + 1, wz + 1, r, HIGHWAY_LOOT, false);
                put(c, wx + 2, dy + 2, wz + 1, 0, 0);
            }
        }
        // The span lies on the real ground under the gap, not at the site's grade (where it was
        // buried under a hill in one place and flush with the dirt in another), with the creeper
        // and the box on it, and the ends it broke from bent down over the gap.
        for (int dx = 26; dx <= 34; dx++) for (int dz = -4; dz <= 4; dz++) {
            int gy = t.sample(x0 + dx, cz + dz).y;
            int fall = gy + 1 + Math.abs(dz) / 3;
            fill(c, x0 + dx, fall + 1, cz + dz, x0 + dx, fall + 3, cz + dz, 0, 0);
            if (((dx + dz) & 3) == 0) continue;
            fill(c, x0 + dx, gy + 1, cz + dz, x0 + dx, fall, cz + dz, 251, 15);
        }
        for (int dz = -4; dz <= 4; dz++) for (int k = 1; k <= 2; k++) {
            if (k == 2 && ((dz + 4) % 3) == 1) continue;              // a ragged edge
            fill(c, x0 + 26 + k, dy - k, cz + dz, x0 + 26 + k, dy - k + 1, cz + dz, 251, 15);
            fill(c, x0 + 34 - k, dy - k, cz + dz, x0 + 34 - k, dy - k + 1, cz + dz, 251, 15);
        }
        mob(c, x0 + 30, t.sample(x0 + 30, cz).y + 2, cz, EntityType.CREEPER);
        graded(c, s, x0 + 30, t.sample(x0 + 30, cz + 3).y + 3, cz + 3, r, HIGHWAY_LOOT, true);
        mob(c, x0 + 12, road[12] + 1, cz, EntityType.ZOMBIE);
        // THE SURPRISE: a maintenance room inside the second pier from the west.
        int px = x0 + 10;
        fill(c, px - 1, y0 - 6, cz - 2, px + 2, y0 - 1, cz + 2, 0, 0);
        shell(c, px - 2, y0 - 7, cz - 3, px + 3, y0, cz + 3, 251, 7);
        encase(c, px - 3, y0 - 8, cz - 4, px + 4, y0 - 1, cz + 4);
        fill(c, px - 1, y0 - 7, cz - 2, px + 2, y0 - 7, cz + 2, 251, 15);
        // The way in is a hatch in the service road: a ladder down the room's south wall in the
        // west corner (it was in the wall under the pier, which capped it).
        int hatch = Math.max(y0, road[9]);
        for (int y = y0 + 1; y <= hatch; y++) put(c, px - 1, y, cz + 3, 251, 7);
        fill(c, px - 1, y0 - 6, cz + 2, px - 1, hatch, cz + 2, 65, 2);
        put(c, px - 1, hatch + 1, cz + 2, 96, 0);
        put(c, px, y0 - 1, cz, 89, 0);                                 // against the ceiling (it hung below it)
        put(c, px + 2, y0 - 6, cz - 2, 61, 4);                         // facing the room, not the wall
        // What the crew kept down here: a bench, a cauldron, cones and spare barrier in a corner,
        // and the panel on the north wall.
        put(c, px + 2, y0 - 6, cz - 1, 58, 0);
        put(c, px - 1, y0 - 6, cz - 2, 118, 0);
        put(c, px, y0 - 6, cz - 2, 251, 1); put(c, px, y0 - 5, cz - 2, 171, 1);
        put(c, px + 2, y0 - 6, cz + 2, 251, 0); put(c, px + 2, y0 - 5, cz + 2, 44, 7);
        put(c, px + 1, y0 - 4, cz - 2, 69, 3); put(c, px, y0 - 4, cz - 2, 77, 3);
        mob(c, px - 1, y0 - 6, cz + 1, EntityType.CAVE_SPIDER);
        trove(c, px + 2, y0 - 6, cz + 1, r, HIGHWAY_LOOT, HIGHWAY_TROVE, 13 + r.nextInt(6));
        // A service ladder up the same pier to the deck, so the west half -- cut off from the
        // slip road by the span that went -- still has a way up.
        fill(c, px - 1, Math.max(y0 + 1, road[9] + 1), cz - 3, px - 1, dy, cz - 3, 65, 4);
    }

    // == 40. The Spire ===========================================================
    // Twenty floors of curtain wall on a concrete frame, a stair core in the middle of
    // it, and one floor in three given over to whatever moved in. It gets worse the
    // higher you go, which is also where the boxes get worth opening, and the roof is
    // not a floor -- it is an arena with a cage on it and the thing that was in the cage.

    private static final int[][] SPIRE_LOOT = {
        {440, 0,  9, 4, 12}, {449, 0,  4, 1,  2}, {304, 0,  7, 1,  1}, {305, 0, 7, 1, 1},
        {262, 0, 13, 8, 24}, {264, 0,  9, 1,  3}, {266, 0, 10, 3,  9}, {384, 0, 9, 2, 8},
        {388, 0,  9, 2,  6}, {331, 0, 10, 4, 12}, {263, 0, 11, 6, 18}, {403, 0, 6, 1, 1},
    };
    private static final int[][] SPIRE_TROVE = {
        {138, 0, 1, 1, 1}, {276, 0, 1, 1, 1}, {403, 0, 1, 3, 4},
    };

    private static void spire(Chunk c, Terrain t) {
        Site s = ground(t, c, SPIRE_CELL, 0x54575200L, 21, 21, 6, Megaliths.RANK_SPIRE);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 20, z1 = z0 + 20;
        for (int dx = 0; dx <= 20; dx++) for (int dz = 0; dz <= 20; dz++)
            footing(c, s, x0 + dx, z0 + dz, 251, 7);
        EntityType[] ladder = {EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON,
                               EntityType.SPIDER, EntityType.HUSK, EntityType.CAVE_SPIDER,
                               EntityType.WITCH, EntityType.BLAZE, EntityType.WITHER_SKELETON};
        // How high the ground stands against each face, so no glass is set against earth: where
        // a bay is buried it is closed in concrete. The lobby door goes in the lowest face.
        int[] bank = new int[84];
        for (int k = 0; k <= 20; k++) {
            bank[k] = t.sample(x0 + k, z0 - 1).y;
            bank[21 + k] = t.sample(x0 + k, z1 + 1).y;
            bank[42 + k] = t.sample(x0 - 1, z0 + k).y;
            bank[63 + k] = t.sample(x1 + 1, z0 + k).y;
        }
        int face = 0;
        for (int k = 1; k < 4; k++) if (bank[k * 21 + 10] < bank[face * 21 + 10]) face = k;
        for (int f = 0; f < 20; f++) {
            int fy = y0 + f * 3;
            fill(c, x0, fy, z0, x1, fy, z1, 251, 7);                   // the slab
            fill(c, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 3, z1 - 1, 0, 0);
            for (int dx = 0; dx <= 20; dx++) for (int dz = 0; dz <= 20; dz++) {
                if (dx != 0 && dx != 20 && dz != 0 && dz != 20) continue;
                // Mullions every fifth block along the face (the test used the coordinate across
                // the face, always 0 or 20, so every bay was solid concrete).
                boolean ns = dz == 0 || dz == 20;
                boolean mullion = ns ? dx % 5 == 0 : dz % 5 == 0;
                int earth = dz == 0 ? bank[dx] : dz == 20 ? bank[21 + dx] : dx == 0 ? bank[42 + dz] : bank[63 + dz];
                for (int y = fy + 1; y <= fy + 3; y++) {
                    boolean glass = !mullion && y <= fy + 2 && y > earth;
                    boolean broken = glass && f % 3 == 2 && f > 2 && (dx * 7 + dz * 3 + f) % 11 == 0;
                    if (broken) put(c, x0 + dx, y, z0 + dz, 0, 0);
                    else put(c, x0 + dx, y, z0 + dz, glass ? 95 : 251, glass ? 3 : 7);
                }
            }
            // The core: stair up one side of it, lift shaft the other.
            shell(c, x0 + 8, fy + 1, z0 + 8, x0 + 12, fy + 3, z0 + 12, 251, 7);
            fill(c, x0 + 9, fy + 1, z0 + 9, x0 + 11, fy + 3, z0 + 11, 0, 0);
            fill(c, x0 + 9, fy + 1, z0 + 8, x0 + 9, fy + 3, z0 + 8, 0, 0);
            // Partitions, different on every floor, so no two read the same. The lobby has none;
            // a cross wall now runs along x at its own z (it used an x offset as a z and landed
            // outside the tower), and every wall stops short of the core and has two doorways.
            int hash = (int) (Terrain.mix(s.seed + f * 8861L) >>> 7);
            if (f > 0) for (int i = 0; i < 3; i++) {
                int k = 2 + Math.floorMod(hash >> (i * 5), 16);
                boolean along = ((hash >> (i + 9)) & 1) == 0;
                for (int j = 2; j <= 18; j++) {
                    if (j == 5 || j == 15) continue;
                    int px = along ? x0 + k : x0 + j, pz = along ? z0 + j : z0 + k;
                    if (Math.abs(px - x0 - 10) <= 3 && Math.abs(pz - z0 - 10) <= 3) continue;
                    fill(c, px, fy + 1, pz, px, fy + 2, pz, 251, 0);
                }
            }
            // A box on every floor, in the corner furthest from the stair, and a tenant.
            int lx = ((f & 1) == 0) ? x0 + 17 : x0 + 3, lz = ((f & 2) == 0) ? z0 + 17 : z0 + 3;
            graded(c, s, lx, fy + 1, lz, r, SPIRE_LOOT, f >= 8);
            put(c, lx, fy + 2, lz, 0, 0);                              // no partition over the lid
            if (f % 3 != 1) {
                EntityType mobType = ladder[Math.min(ladder.length - 1, f / 3 + (f > 15 ? 1 : 0))];
                mob(c, x0 + 20 - (lx - x0), fy + 1, z0 + 20 - (lz - z0), mobType);
            }
            if (f == 0) spireLobby(c, x0, y0, z0);
            else towerOffice(c, x0, fy, z0, f);
        }
        // The lobby doors, in the face where the ground is lowest (there was no way in at all),
        // with a paved step out to the ground.
        int ex = face == 3 ? x1 : face == 2 ? x0 : x0 + 9, ez = face == 1 ? z1 : face == 0 ? z0 : z0 + 9;
        int sx = face >= 2 ? 0 : 2, sz = face >= 2 ? 2 : 0, facing = new int[]{1, 3, 0, 2}[face];
        for (int k = 0; k <= 2; k += 2) {
            put(c, ex + sx * k / 2, y0 + 1, ez + sz * k / 2, 197, facing);
            put(c, ex + sx * k / 2, y0 + 2, ez + sz * k / 2, 197, 8);
        }
        approach(c, t, ex, ez, face == 2 ? -1 : face == 3 ? 1 : 0, face == 0 ? -1 : face == 1 ? 1 : 0,
                 3, 3, y0, 251, 7);
        // The stair side of the core is a ladder on the core's back wall, through every floor to
        // the top one (it stood on the front with nothing behind it, and every slab capped it).
        fill(c, x0 + 9, y0 + 1, z0 + 11, x0 + 9, y0 + 58, z0 + 11, 65, 2);
        // The lift shaft: open from the lobby to the top floor behind iron gates.
        for (int f = 0; f < 20; f++) {
            int fy = y0 + f * 3;
            if (f > 0) fill(c, x0 + 11, fy, z0 + 10, x0 + 11, fy, z0 + 11, 0, 0);
            fill(c, x0 + 10, fy + 1, z0 + 10, x0 + 10, fy + 2, z0 + 11, 101, 0);
            fill(c, x0 + 11, fy + 1, z0 + 9, x0 + 11, fy + 2, z0 + 9, 101, 0);
        }
        // Lights set into the ceilings once every slab is down (each floor's lamp went in at the
        // height of the next slab, which then covered it). Fewer of them the higher you go, and
        // one out on each floor something took over.
        for (int f = 0; f < 20; f++) {
            int cy = y0 + f * 3 + 3, n = 4 - f / 5 - (f % 3 == 2 ? 1 : 0);
            for (int q = 0; q < n; q++) put(c, x0 + (q == 0 || q == 3 ? 5 : 15), cy, z0 + (q < 2 ? 5 : 15), 169, 0);
            put(c, x0 + 10, cy, z0 + 9, 89, 0);
        }
        // THE SURPRISE: the roof. No walls, a canopy on four columns, and a cage on it. The arena
        // floor runs out to a parapet with the rail on top (there was a gutter a block down with
        // the rail in it), and the canopy has edge beams between its columns.
        int ry = y0 + 60;
        fill(c, x0, ry, z0, x1, ry, z1, 251, 7);
        fill(c, x0 + 1, ry + 1, z0 + 1, x1 - 1, ry + 1, z1 - 1, 251, 15);
        fill(c, x0, ry + 1, z0, x1, ry + 1, z0, 251, 7); fill(c, x0, ry + 1, z1, x1, ry + 1, z1, 251, 7);
        fill(c, x0, ry + 1, z0, x0, ry + 1, z1, 251, 7); fill(c, x1, ry + 1, z0, x1, ry + 1, z1, 251, 7);
        for (int dx = 0; dx <= 20; dx++) {
            put(c, x0 + dx, ry + 2, z0, 85, 0); put(c, x0 + dx, ry + 2, z1, 85, 0);
            put(c, x0, ry + 2, z0 + dx, 85, 0); put(c, x1, ry + 2, z0 + dx, 85, 0);
        }
        for (int dx = 0; dx <= 20; dx += 20) for (int dz = 0; dz <= 20; dz += 20)
            fill(c, x0 + dx, ry + 1, z0 + dz, x0 + dx, ry + 7, z0 + dz, 43, 8);   // column: 43:8, was iron block
        fill(c, x0, ry + 7, z0, x1, ry + 7, z0, 251, 7); fill(c, x0, ry + 7, z1, x1, ry + 7, z1, 251, 7);
        fill(c, x0, ry + 7, z0, x0, ry + 7, z1, 251, 7); fill(c, x1, ry + 7, z0, x1, ry + 7, z1, 251, 7);
        fill(c, x0, ry + 8, z0, x1, ry + 8, z1, 44, 7);
        shell(c, x0 + 7, ry + 1, z0 + 7, x0 + 13, ry + 6, z0 + 13, 101, 0);
        fill(c, x0 + 8, ry + 1, z0 + 8, x0 + 12, ry + 5, z0 + 12, 0, 0);
        fill(c, x0 + 9, ry + 1, z0 + 7, x0 + 11, ry + 3, z0 + 7, 0, 0);
        fill(c, x0 + 8, ry + 1, z0 + 8, x0 + 12, ry + 1, z0 + 12, 49, 0);
        put(c, x0 + 10, ry + 5, z0 + 10, 89, 0);
        mob(c, x0 + 10, ry + 1, z0 + 10, EntityType.WITHER_SKELETON);
        mob(c, x0 + 8, ry + 1, z0 + 12, EntityType.WITHER_SKELETON);
        mob(c, x0 + 12, ry + 1, z0 + 8, EntityType.BLAZE);
        mob(c, x0 + 3, ry + 1, z0 + 3, EntityType.WITCH);
        trove(c, x0 + 10, ry + 1, z0 + 12, r, SPIRE_LOOT, SPIRE_TROVE, 17 + r.nextInt(6));
        graded(c, s, x0 + 17, ry + 1, z0 + 17, r, SPIRE_LOOT, true);
        // The way up is a hatch by the north parapet, from a ladder on the top floor against a
        // mullion (the old roof ladder came up under the cage's obsidian floor).
        fill(c, x0 + 5, y0 + 58, z0 + 1, x0 + 5, ry + 1, z0 + 1, 65, 3);
        put(c, x0 + 5, ry + 2, z0 + 1, 96, 1);
    }

    /** The Spire's ground floor: a reception desk, benches and plants (it was a bare slab). */
    private static void spireLobby(Chunk c, int x0, int y0, int z0) {
        for (int dx = 13; dx <= 15; dx++) putIfAir(c, x0 + dx, y0 + 1, z0 + 4, 251, 0);
        putIfAir(c, x0 + 15, y0 + 1, z0 + 5, 251, 0);
        putIfAir(c, x0 + 14, y0 + 1, z0 + 3, 134, 3);
        for (int dx = 2; dx <= 18; dx++) if (dx < 5 || dx > 15) putIfAir(c, x0 + dx, y0 + 1, z0 + 19, 44, 7);
        putIfAir(c, x0 + 1, y0 + 1, z0 + 1, 140, 0); putIfAir(c, x0 + 19, y0 + 1, z0 + 1, 140, 0);
        putIfAir(c, x0 + 1, y0 + 1, z0 + 19, 140, 0); putIfAir(c, x0 + 19, y0 + 1, z0 + 19, 140, 0);
    }

    /**
     * One office floor of the Spire (they were bare 2-high plates): desk pairs with chairs in the
     * four quarters, filing along the back of the core and a water cooler. On the floors
     * something took over, a desk has gone over, a scorch is on the slab and, higher up,
     * cobwebs in the corners.
     */
    private static void towerOffice(Chunk c, int x0, int fy, int z0, int f) {
        int y = fy + 1;
        boolean taken = f % 3 != 1;
        for (int q = 0; q < 4; q++) {
            int dx = (q & 1) == 0 ? 3 : 15, dz = (q & 2) == 0 ? 5 : 15;
            if (taken && ((f + q) & 3) == 0) {                          // gone over
                putIfAir(c, x0 + dx + 1, y, z0 + dz + 1, 134, 6);
                continue;
            }
            putIfAir(c, x0 + dx, y, z0 + dz, 44, 15); putIfAir(c, x0 + dx + 1, y, z0 + dz, 44, 15);
            putIfAir(c, x0 + dx, y, z0 + dz + 1, 134, 2); putIfAir(c, x0 + dx + 1, y, z0 + dz + 1, 134, 2);
        }
        for (int d = 8; d <= 12; d++) if (!taken || (d & 1) == 0) putIfAir(c, x0 + d, y, z0 + 13, 47, 0);
        putIfAir(c, x0 + 1, y, z0 + 10, 118, 0);
        if (taken) {
            put(c, x0 + 6, fy, z0 + 9, 251, 15); put(c, x0 + 6, fy, z0 + 10, 251, 15); put(c, x0 + 5, fy, z0 + 10, 251, 15);
            if (f >= 6) {
                putIfAir(c, x0 + 1, y + 1, z0 + 1, 30, 0); putIfAir(c, x0 + 19, y + 1, z0 + 19, 30, 0);
                putIfAir(c, x0 + 19, y + 1, z0 + 1, 30, 0); putIfAir(c, x0 + 1, y + 1, z0 + 19, 30, 0);
            }
        }
    }

    // == 41. The Grand Meridian ==================================================
    // Eight floors of hotel with the lobby chandelier still hanging, a corridor on every
    // floor with the doors shut, a restaurant off the mezzanine, and a pool on the roof
    // that has not been drained.

    private static final int[][] HOTEL_LOOT = {
        {2265, 0,  5, 1,  1}, {425, 14, 7, 1, 2}, {281, 0, 10, 3,  9}, {322, 0, 7, 1, 2},
        { 266, 0, 11, 3,  9}, {351, 14, 11, 6, 18}, {340, 0, 10, 2, 6}, {297, 0, 11, 2, 6},
        { 264, 0,  8, 1,  3}, {388,  0, 8, 1, 3}, {384, 0,  8, 2,  6}, {403, 0, 5, 1, 1},
    };
    private static final int[][] HOTEL_TROVE = {
        { 41, 0, 1, 2, 4}, {264, 0, 1, 4, 8}, {403, 0, 1, 2, 3},
    };

    private static void hotel(Chunk c, Terrain t) {
        Site s = ground(t, c, HOTEL_CELL, 0x484F54454CL, 33, 25, 6, Megaliths.RANK_HOTEL);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 32, z1 = z0 + 24;
        for (int dx = 0; dx <= 32; dx++) for (int dz = 0; dz <= 24; dz++)
            planted(c, s, x0 + dx, z0 + dz, 98, 0, 12);                // down to ground (a corner hung over a drop)
        // Where the hillside stands against the front or the back, the windows it covers are
        // left as wall rather than glass against earth.
        int[] bankN = new int[33], bankS = new int[33];
        for (int dx = 0; dx <= 32; dx++) { bankN[dx] = t.sample(x0 + dx, z0 - 1).y; bankS[dx] = t.sample(x0 + dx, z1 + 1).y; }
        for (int f = 0; f < 8; f++) {
            int fy = y0 + f * 4;
            fill(c, x0, fy, z0, x1, fy, z1, f == 0 ? 155 : 251, f == 0 ? 0 : 8);
            shell(c, x0, fy, z0, x1, fy + 3, z1, f == 0 ? 159 : 159, f == 0 ? 8 : 1);
            fill(c, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 3, z1 - 1, 0, 0);
            for (int dx = 2; dx <= 30; dx += 2) {
                if (fy + 1 > bankN[dx]) fill(c, x0 + dx, fy + 1, z0, x0 + dx, fy + 2, z0, 95, 3);
                if (fy + 1 > bankS[dx]) fill(c, x0 + dx, fy + 1, z1, x0 + dx, fy + 2, z1, 95, 3);
            }
            if (f == 0) {                                              // the lobby
                fill(c, x0 + 4, fy + 1, z0 + 4, x0 + 12, fy + 1, z0 + 5, 155, 0);
                fill(c, x0 + 4, fy + 2, z0 + 4, x0 + 12, fy + 2, z0 + 4, 44, 7);
                put(c, x0 + 7, fy + 1, z0 + 5, 169, 0); put(c, x0 + 10, fy + 1, z0 + 5, 169, 0);
                for (int dx = 20; dx <= 28; dx += 4) for (int dz = 6; dz <= 18; dz += 6) {
                    put(c, x0 + dx, fy + 1, z0 + dz, 85, 0);
                    put(c, x0 + dx, fy + 2, z0 + dz, 44, 7);
                    put(c, x0 + dx - 1, fy + 1, z0 + dz, 53, 1);
                    put(c, x0 + dx + 1, fy + 1, z0 + dz, 53, 0);       // a second chair, facing the first
                }
                fill(c, x0 + 14, fy + 3, z0 + 12, x0 + 18, fy + 3, z0 + 12, 85, 0);
                put(c, x0 + 16, fy + 2, z0 + 12, 89, 0);
                fill(c, x0 + 7, fy + 3, z0 + 12, x0 + 9, fy + 3, z0 + 12, 85, 0);   // a second chandelier
                put(c, x0 + 8, fy + 2, z0 + 12, 89, 0);
                fill(c, x0 + 14, fy + 1, z0, x0 + 18, fy + 3, z0, 0, 0);
                mob(c, x0 + 16, fy + 1, z0 + 18, EntityType.ZOMBIE);
                graded(c, s, x0 + 5, fy + 1, z0 + 6, r, HOTEL_LOOT, false);
                for (int dx = 5; dx <= 11; dx++) for (int dz = 7; dz <= 9; dz++) putIfAir(c, x0 + dx, fy + 1, z0 + dz, 171, 14);
                continue;
            }
            // Guest floors: a corridor with rooms off both sides, and a bed in each.
            fill(c, x0 + 1, fy + 1, z0 + 11, x1 - 1, fy + 3, z0 + 13, 0, 0);
            for (int dx = 2; dx <= 28; dx += 6) for (int side = 0; side < 2; side++) {
                int rz = side == 0 ? z0 + 2 : z0 + 15;
                shell(c, x0 + dx, fy + 1, rz, x0 + dx + 5, fy + 3, rz + 7, 159, 1);
                fill(c, x0 + dx + 1, fy + 1, rz + 1, x0 + dx + 4, fy + 3, rz + 6, 0, 0);
                // The room runs out to the facade, so its two windows are its own (there was a
                // dead slot a block wide between them and its back wall).
                int back = side == 0 ? rz : rz + 7, win = side == 0 ? z0 + 1 : z1 - 1;
                fill(c, x0 + dx + 1, fy + 1, back, x0 + dx + 4, fy + 3, back, 0, 0);
                fill(c, x0 + dx, fy + 1, win, x0 + dx, fy + 3, win, 159, 1);
                fill(c, x0 + dx + 5, fy + 1, win, x0 + dx + 5, fy + 3, win, 159, 1);
                int door = side == 0 ? rz + 7 : rz;
                // Every door shut: a pair of dark oak doors (the doorways were empty).
                int dface = side == 0 ? 3 : 1;
                put(c, x0 + dx + 2, fy + 1, door, 197, dface); put(c, x0 + dx + 2, fy + 2, door, 197, side == 0 ? 8 : 9);
                put(c, x0 + dx + 3, fy + 1, door, 197, dface); put(c, x0 + dx + 3, fy + 2, door, 197, side == 0 ? 9 : 8);
                // The bed against the outside wall, head to it, with a nightstand and an armchair.
                int foot = side == 0 ? win + 1 : win - 1;
                put(c, x0 + dx + 3, fy + 1, foot, 26, side == 0 ? 2 : 0);
                put(c, x0 + dx + 3, fy + 1, win, 26, side == 0 ? 10 : 8);
                put(c, x0 + dx + 4, fy + 1, win, 44, 15);
                put(c, x0 + dx + 4, fy + 2, win, 140, 0);
                put(c, x0 + dx + 1, fy + 1, win, 53, side == 0 ? 3 : 2);
                put(c, x0 + dx + 1, fy + 3, rz + 3, 89, 0);
                if (((dx + side + f) & 3) == 0) graded(c, s, x0 + dx + 4, fy + 1, rz + 5, r, HOTEL_LOOT, f >= 5);
                if (((dx * 3 + side * 5 + f) & 3) == 1)
                    mob(c, x0 + dx + 3, fy + 1, rz + 4, f >= 5 ? EntityType.HUSK : EntityType.ZOMBIE);
                for (int ix = 1; ix <= 4; ix++) for (int iz = 0; iz <= 7; iz++)
                    putIfAir(c, x0 + dx + ix, fy + 1, (side == 0 ? z0 + 1 : z0 + 16) + iz, 171, 14);
            }
            // The corridor: a runner in the old colours, and a fitting every six blocks, every
            // other one dead (the corridors had no light at all).
            for (int dx = 2; dx <= 31; dx++) for (int dz = 11; dz <= 13; dz++) {
                int k = Math.floorMod(dx + dz, 3);
                putIfAir(c, x0 + dx, fy + 1, z0 + dz, 171, k == 0 ? 14 : k == 1 ? 1 : 12);
            }
            for (int dx = 4; dx <= 28; dx += 6) put(c, x0 + dx, fy + 3, z0 + 12, ((dx / 6) & 1) == 0 ? 89 : 123, 0);
            // The strip between the west wall and the first rooms is walled in (it was a dead-end
            // slot a block wide); the corridor end keeps the ladder.
            fill(c, x0 + 1, fy + 1, z0 + 1, x0 + 1, fy + 3, z0 + 10, 159, 1);
            fill(c, x0 + 1, fy + 1, z0 + 14, x0 + 1, fy + 3, z1 - 1, 159, 1);
        }
        // One ladder links the floors, at the west end of the corridor on the blank west wall,
        // from the lobby floor to the roof (it started three blocks up, on the chandelier, beside
        // a drop and with nothing behind it).
        approach(c, t, x0 + 14, z0, 0, -1, 5, 3, y0, 98, 0);
        // The roof: a pool nobody drained, raised on the roof slab with a glass floor, so the top
        // corridor looks up through the water (it was sunk into the top floor and cut its rooms,
        // a box and the ends of two beds), and a bar at the end of it.
        int ry = y0 + 32;
        fill(c, x0, ry, z0, x1, ry, z1, 251, 8);
        for (int dx = 0; dx <= 32; dx++) { put(c, x0 + dx, ry + 1, z0, 85, 0); put(c, x0 + dx, ry + 1, z1, 85, 0); }
        for (int dz = 0; dz <= 24; dz++) { put(c, x0, ry + 1, z0 + dz, 85, 0); put(c, x1, ry + 1, z0 + dz, 85, 0); }
        fill(c, x0 + 1, y0 + 1, z0 + 12, x0 + 1, ry, z0 + 12, 65, 5);
        fill(c, x0 + 5, ry + 1, z0 + 5, x0 + 21, ry + 2, z0 + 19, 155, 0);
        fill(c, x0 + 6, ry, z0 + 6, x0 + 20, ry, z0 + 18, 95, 3);
        fill(c, x0 + 6, ry + 1, z0 + 6, x0 + 20, ry + 2, z0 + 18, 9, 0);
        put(c, x0 + 22, ry + 1, z0 + 12, 155, 0); put(c, x0 + 22, ry + 2, z0 + 12, 156, 1); put(c, x0 + 23, ry + 1, z0 + 12, 156, 1);
        fill(c, x0 + 24, ry + 1, z0 + 8, x0 + 29, ry + 1, z0 + 16, 155, 0);
        put(c, x0 + 26, ry + 1, z0 + 8, 156, 2); put(c, x0 + 27, ry + 1, z0 + 8, 156, 2);   // a step up from the suite door
        // The bar: one slab of counter (two stacked read as a slatted fence), stools, a back bar,
        // and the lamp on a standard (it floated two blocks over the floor).
        fill(c, x0 + 24, ry + 2, z0 + 8, x0 + 24, ry + 2, z0 + 16, 44, 7);
        for (int dz = 9; dz <= 15; dz++) if (dz <= 10 || dz >= 14) { put(c, x0 + 23, ry + 1, z0 + dz, 85, 0); put(c, x0 + 23, ry + 2, z0 + dz, 171, 14); }
        fill(c, x0 + 29, ry + 2, z0 + 9, x0 + 29, ry + 2, z0 + 10, 47, 0); fill(c, x0 + 29, ry + 2, z0 + 14, x0 + 29, ry + 2, z0 + 15, 47, 0);
        put(c, x0 + 29, ry + 3, z0 + 9, 140, 0); put(c, x0 + 29, ry + 3, z0 + 15, 140, 0);
        fill(c, x0 + 27, ry + 2, z0 + 12, x0 + 27, ry + 3, z0 + 12, 85, 0);
        put(c, x0 + 27, ry + 4, z0 + 12, 169, 0);
        mob(c, x0 + 27, ry + 1, z0 + 14, EntityType.HUSK);
        // THE SURPRISE: the suite behind the bar, still made up.
        shell(c, x0 + 23, ry + 1, z0 + 2, x0 + 31, ry + 5, z0 + 7, 155, 0);
        fill(c, x0 + 24, ry + 1, z0 + 3, x0 + 30, ry + 4, z0 + 6, 0, 0);
        fill(c, x0 + 24, ry + 1, z0 + 3, x0 + 30, ry + 1, z0 + 6, 171, 14);
        fill(c, x0 + 26, ry + 1, z0 + 7, x0 + 27, ry + 3, z0 + 7, 0, 0);
        put(c, x0 + 26, ry + 1, z0 + 7, 197, 3); put(c, x0 + 26, ry + 2, z0 + 7, 197, 8);
        put(c, x0 + 27, ry + 1, z0 + 7, 197, 3); put(c, x0 + 27, ry + 2, z0 + 7, 197, 9);
        put(c, x0 + 25, ry + 1, z0 + 4, 26, 0); put(c, x0 + 25, ry + 1, z0 + 5, 26, 8);   // a real pair (both halves faced east)
        fill(c, x0 + 29, ry + 2, z0 + 3, x0 + 30, ry + 4, z0 + 4, 47, 0);
        put(c, x0 + 27, ry + 4, z0 + 5, 89, 0);
        put(c, x0 + 29, ry + 1, z0 + 6, 145, 0);
        mob(c, x0 + 24, ry + 1, z0 + 6, EntityType.HUSK);
        trove(c, x0 + 30, ry + 1, z0 + 5, r, HOTEL_LOOT, HOTEL_TROVE, 14 + r.nextInt(6));
    }

    // == 42. The Bell ============================================================
    // Stucco and a purple band, a tiled roof, a drive-through round one side, a patio
    // with the umbrellas still up, and an arch out front with a bell hung in it that is
    // considerably more solid than the building it advertises.

    private static final int[][] TACO_LOOT = {
        {434, 0, 11, 3,  9}, {435, 0,  9, 2,  8}, {423, 0, 10, 2,  6}, {297, 0, 12, 3, 9},
        {367, 0, 12, 4, 12}, {263, 0, 11, 6, 18}, {351, 5, 10, 6, 18}, {265, 0, 10, 3, 9},
        {266, 0,  8, 2,  6}, {384, 0,  6, 1,  3}, {403, 0,  4, 1,  1}, {331, 0,  9, 4, 12},
    };
    private static final int[][] TACO_TROVE = {
        { 41, 0, 1, 2, 3}, {322, 0, 1, 2, 2}, {400, 0, 1, 6, 10},
    };

    private static void bell(Chunk c, Terrain t) {
        Site s = ground(t, c, TACO_CELL, 0x5441434FL, 25, 21, 6, Megaliths.RANK_TACO);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 24, z1 = z0 + 20;
        for (int dx = 0; dx <= 24; dx++) for (int dz = 0; dz <= 20; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        // Whatever grew or drifted on the lot comes off it first (dead trees stood in the drive
        // lane and in the arch, and a skin of dirt lay over the patio).
        fill(c, x0, y0 + 1, z0, x1, y0 + 14, z1, 0, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);
        // Stucco box with the band round it and a tiled roof. The room stops under the box's
        // top, which is its ceiling (it was carved away, and the menu board and the light hung
        // in the air under the tiles).
        int bx = x0 + 2, bz = z0 + 2;
        shell(c, bx, y0 + 1, bz, bx + 15, y0 + 5, bz + 12, 172, 0);
        fill(c, bx, y0 + 4, bz, bx + 15, y0 + 4, bz + 12, 251, 10);
        fill(c, bx + 1, y0 + 1, bz + 1, bx + 14, y0 + 4, bz + 11, 0, 0);
        for (int i = 0; i <= 2; i++) {
            // Each tile course is two deep so it bears on the one below (courses that met only at their
            // edges were pruned as floating clay along a chunk line -- structure audit 2026-09-22).
            fill(c, bx - 1 + i, y0 + 5 + i, bz - 1, bx - 1 + i, y0 + 6 + i, bz + 13, 159, 14);
            fill(c, bx + 16 - i, y0 + 5 + i, bz - 1, bx + 16 - i, y0 + 6 + i, bz + 13, 159, 14);
            if (i > 0) fill(c, bx + i, y0 + 5 + i, bz, bx + 15 - i, y0 + 5 + i, bz + 12, 159, 14);
        }
        for (int dz = 2; dz <= 10; dz += 2) {
            fill(c, bx, y0 + 2, bz + dz, bx, y0 + 3, bz + dz, 102, 0);
            fill(c, bx + 15, y0 + 2, bz + dz, bx + 15, y0 + 3, bz + dz, 102, 0);
        }
        fill(c, bx + 6, y0 + 1, bz + 12, bx + 8, y0 + 3, bz + 12, 0, 0);
        // Dining side, counter, kitchen behind it.
        for (int dz = 2; dz <= 10; dz += 3) {
            put(c, bx + 2, y0 + 1, bz + dz, 53, 1); put(c, bx + 2, y0 + 2, bz + dz, 35, 10);
            put(c, bx + 5, y0 + 1, bz + dz, 53, 0); put(c, bx + 5, y0 + 2, bz + dz, 35, 10);
            fill(c, bx + 3, y0 + 1, bz + dz, bx + 4, y0 + 1, bz + dz, 44, 7);
        }
        fill(c, bx + 8, y0 + 1, bz + 2, bx + 8, y0 + 2, bz + 10, 155, 0);
        fill(c, bx + 8, y0 + 4, bz + 3, bx + 8, y0 + 4, bz + 9, 251, 10);
        for (int dx = 10; dx <= 13; dx += 3) {
            put(c, bx + dx, y0 + 1, bz + 3, 61, 3); put(c, bx + dx, y0 + 1, bz + 6, 154, 0);
            put(c, bx + dx, y0 + 2, bz + 3, 44, 7);
        }
        // The kitchen fitted out (it was two cookers and two fryers in an unlit room): cauldrons on
        // the fry line, a prep counter under the pick-up hatch, a drinks machine, a light.
        put(c, bx + 11, y0 + 1, bz + 6, 118, 0); put(c, bx + 12, y0 + 1, bz + 6, 118, 0);
        fill(c, bx + 14, y0 + 1, bz + 7, bx + 14, y0 + 1, bz + 9, 251, 0);
        fill(c, bx + 14, y0 + 2, bz + 7, bx + 14, y0 + 2, bz + 9, 44, 7);
        put(c, bx + 9, y0 + 1, bz + 10, 251, 0); put(c, bx + 9, y0 + 2, bz + 10, 95, 10);
        put(c, bx + 11, y0 + 5, bz + 8, 89, 0);
        put(c, bx + 5, y0 + 5, bz + 6, 89, 0);                             // in the ceiling
        mob(c, bx + 4, y0 + 1, bz + 8, EntityType.ZOMBIE);
        graded(c, s, bx + 13, y0 + 1, bz + 10, r, TACO_LOOT, false);
        graded(c, s, bx + 2, y0 + 1, bz + 11, r, TACO_LOOT, false);
        // Drive-through and the patio. The pick-up window is in the wall (its frame stood a block
        // out from the wall, over two real windows): black surround, purple glass, the hatch.
        fill(c, x0 + 19, y0, z0 + 2, x0 + 23, y0, z1, 251, 15);
        for (int dz = 4; dz <= 13; dz += 3) {                          // the last beam is where the bell hangs
            put(c, x0 + 23, y0 + 1, z0 + dz, 85, 0);
            fill(c, x0 + 23, y0 + 2, z0 + dz, x0 + 23, y0 + 4, z0 + dz, 85, 0);
            fill(c, x0 + 19, y0 + 5, z0 + dz, x0 + 23, y0 + 5, z0 + dz, 44, 7);
        }
        fill(c, bx + 15, y0 + 2, bz + 6, bx + 15, y0 + 4, bz + 8, 251, 15);
        put(c, bx + 15, y0 + 3, bz + 7, 95, 10);
        put(c, bx + 15, y0 + 2, bz + 7, 0, 0);
        for (int i = 0; i < 3; i++) {
            int px = x0 + 4 + i * 5, pz = z0 + 17;
            fill(c, px, y0 + 1, pz, px, y0 + 3, pz, 85, 0);
            fill(c, px - 1, y0 + 4, pz - 1, px + 1, y0 + 4, pz + 1, 35, 10);
            put(c, px - 1, y0 + 1, pz, 53, 1); put(c, px + 1, y0 + 1, pz, 53, 0);
        }
        mob(c, x0 + 9, y0 + 1, z0 + 17, EntityType.ZOMBIE);
        // The arch out front, with the bell in it: a bell the right way up, five wide at the lip
        // and three deep, hollow above its lip, and the arch drawn after it so the arch stays
        // whole (the bell was an upside-down wedge one block thick that cut the arch in two and
        // left its crown and a clapper floating). Bell metal 43:8, was iron block.
        int ax = x0 + 20, az = z0 + 17;
        fill(c, ax - 2, y0 + 6, az - 1, ax + 2, y0 + 8, az + 1, 43, 8);
        fill(c, ax - 1, y0 + 7, az, ax + 1, y0 + 8, az, 0, 0);
        fill(c, ax - 1, y0 + 9, az - 1, ax + 1, y0 + 9, az + 1, 43, 8);
        fill(c, ax - 3, y0 + 1, az, ax - 3, y0 + 7, az, 251, 0);
        fill(c, ax + 3, y0 + 1, az, ax + 3, y0 + 7, az, 251, 0);
        for (int dx = -3; dx <= 3; dx++) {
            int lift = 8 + (3 - Math.abs(dx));
            put(c, ax + dx, y0 + lift, az, 251, 10);
            if (Math.abs(dx) < 3) put(c, ax + dx, y0 + lift - 1, az, 251, 0);
        }
        // THE SURPRISE: the bell is hollow, and there is a ladder up the near column to a way in
        // through the side of it.
        fill(c, ax - 3, y0 + 1, az - 1, ax - 3, y0 + 7, az - 1, 65, 2);
        fill(c, ax - 2, y0 + 7, az - 1, ax - 1, y0 + 8, az - 1, 0, 0);
        put(c, ax, y0 + 9, az, 89, 0);
        mob(c, ax + 1, y0 + 7, az, EntityType.SPIDER);
        trove(c, ax, y0 + 7, az, r, TACO_LOOT, TACO_TROVE, 12 + r.nextInt(6));
    }

    // == 43. The Sundowner =======================================================
    // One storey, eleven rooms in an L round a car park, a walkway, an office with the
    // board of keys behind it, a sign that still says VACANCY, and a pool with about a
    // foot of something in the deep end. Room thirteen is boarded from the inside.

    private static final int[][] MOTEL_LOOT = {
        {2263, 0,  5, 1,  1}, {143, 0,  9, 2,  8}, { 72, 0,  8, 2,  6}, {297, 0, 12, 3,  9},
        { 367, 0, 12, 4, 12}, {263, 0, 11, 6, 18}, {265, 0, 10, 3,  9}, {351, 1, 10, 6, 18},
        { 331, 0,  9, 4, 12}, {266, 0,  8, 2,  6}, {384, 0,  6, 1,  3}, {403, 0,  4, 1,  1},
    };
    private static final int[][] MOTEL_TROVE = {
        { 57, 0, 1, 1, 1}, {384, 0, 1, 8, 12}, {403, 0, 1, 1, 2},
    };

    private static void motel(Chunk c, Terrain t) {
        Site s = ground(t, c, MOTEL_CELL, 0x4D4F54454CL, 31, 19, 5, Megaliths.RANK_MOTEL);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 30, z1 = z0 + 18;
        for (int dx = 0; dx <= 30; dx++) for (int dz = 0; dz <= 18; dz++)
            planted(c, s, x0 + dx, z0 + dz, 1, 0, 20);                 // down to ground (a cliff lip left it hanging)
        // Anything standing on the lot comes off it (a third of it was under the hill, and dead
        // trees stood on the roof and in a doorway).
        fill(c, x0, y0 + 1, z0, x1, y0 + 12, z1, 0, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);
        for (int dx = 2; dx <= 28; dx += 5) fill(c, x0 + dx, y0, z0 + 12, x0 + dx, y0, z0 + 16, 251, 0);
        // The long run of rooms, and the short one turning the corner. Rooms share their party
        // walls, are two wide inside and floored at the ground (the floor was a block up, so no
        // doorway could be climbed through, and each room was a one-wide slot). The short run
        // starts south of the walkway and opens east onto a walkway of its own (it was built over
        // the first two rooms of the long run).
        int[][] runs = {{x0 + 1, z0 + 1, 8, 0}, {x0 + 1, z0 + 9, 3, 1}};
        for (int run = 0; run < 2; run++) {
            int n = runs[run][2];
            for (int i = 0; i < n; i++) {
                int rx = run == 0 ? runs[run][0] + i * 3 : runs[run][0];
                int rz = run == 0 ? runs[run][1] : runs[run][1] + 3 * (i - 1);
                if (run == 1 && i == 0) continue;
                int wid = run == 0 ? 3 : 6, dep = run == 0 ? 6 : 3;
                shell(c, rx, y0 + 1, rz, rx + wid, y0 + 4, rz + dep, 159, i == 4 && run == 0 ? 12 : 1);
                fill(c, rx + 1, y0 + 1, rz + 1, rx + wid - 1, y0 + 3, rz + dep - 1, 0, 0);
                fill(c, rx + 1, y0, rz + 1, rx + wid - 1, y0, rz + dep - 1, 5, 1);
                if (run == 0) {
                    if (i != 6) {                                      // one door gone, one left open
                        put(c, rx + 1, y0 + 1, rz + dep, 193, i == 2 ? 7 : 3);
                        put(c, rx + 1, y0 + 2, rz + dep, 193, 8);
                    } else fill(c, rx + 1, y0 + 1, rz + dep, rx + 1, y0 + 2, rz + dep, 0, 0);
                    put(c, rx + 1, y0 + 1, rz + 2, 26, 2); put(c, rx + 1, y0 + 1, rz + 1, 26, 10);
                    put(c, rx + 2, y0 + 1, rz + 1, 44, 7);
                    put(c, rx + 1, y0 + 4, rz + 3, 89, 0);
                    if ((i & 1) == 0) graded(c, s, rx + 2, y0 + 1, rz + 2, r, MOTEL_LOOT, false);
                    if ((i % 3) == 1) mob(c, rx + 2, y0 + 1, rz + 4, EntityType.ZOMBIE);
                } else {
                    put(c, rx + wid, y0 + 1, rz + 2, 193, 2); put(c, rx + wid, y0 + 2, rz + 2, 193, 8);
                    put(c, rx + 2, y0 + 1, rz + 1, 26, 1); put(c, rx + 1, y0 + 1, rz + 1, 26, 9);
                    put(c, rx + 1, y0 + 1, rz + 2, 44, 7);
                    put(c, rx + 3, y0 + 4, rz + 1, 89, 0);
                    graded(c, s, rx + 5, y0 + 1, rz + 1, r, MOTEL_LOOT, false);
                    mob(c, rx + 4, y0 + 1, rz + 1, EntityType.ZOMBIE);
                }
            }
        }
        // Canopy over the rooms and both walkways, posts on the party walls (one stood in front of
        // the first door), and a light in it every six blocks (the walkway was dark).
        fill(c, x0 + 1, y0 + 5, z0 + 1, x0 + 25, y0 + 5, z0 + 8, 44, 7);
        fill(c, x0 + 1, y0 + 5, z0 + 1, x0 + 8, y0 + 5, z0 + 15, 44, 7);
        for (int dx = 4; dx <= 22; dx += 6) fill(c, x0 + dx, y0 + 1, z0 + 8, x0 + dx, y0 + 4, z0 + 8, 85, 0);
        for (int dz = 12; dz <= 15; dz += 3) fill(c, x0 + 8, y0 + 1, z0 + dz, x0 + 8, y0 + 4, z0 + dz, 85, 0);
        for (int dx = 4; dx <= 22; dx += 6) put(c, x0 + dx, y0 + 5, z0 + 8, 89, 0);
        put(c, x0 + 8, y0 + 5, z0 + 12, 89, 0);
        // The office, and the sign that still says VACANCY. The counter is one high and runs
        // across the room with the way behind it at the west end (it was a two-high wall), the
        // keys hang on the back wall, and one leaf of the doorway has its door.
        shell(c, x0 + 25, y0 + 1, z0 + 1, x0 + 29, y0 + 4, z0 + 6, 159, 8);
        fill(c, x0 + 26, y0 + 1, z0 + 2, x0 + 28, y0 + 3, z0 + 5, 0, 0);
        fill(c, x0 + 26, y0 + 1, z0 + 6, x0 + 27, y0 + 2, z0 + 6, 0, 0);
        put(c, x0 + 26, y0 + 1, z0 + 6, 193, 3); put(c, x0 + 26, y0 + 2, z0 + 6, 193, 8);
        fill(c, x0 + 27, y0 + 1, z0 + 4, x0 + 28, y0 + 1, z0 + 4, 44, 15);
        put(c, x0 + 28, y0 + 2, z0 + 4, 140, 0);
        fill(c, x0 + 27, y0 + 2, z0 + 2, x0 + 28, y0 + 3, z0 + 2, 131, 0);
        put(c, x0 + 27, y0 + 4, z0 + 3, 89, 0);
        graded(c, s, x0 + 26, y0 + 1, z0 + 2, r, MOTEL_LOOT, true);
        fill(c, x0 + 29, y0 + 1, z0 + 15, x0 + 29, y0 + 9, z0 + 15, 43, 8);   // sign pole: 43:8, was iron block
        fill(c, x0 + 27, y0 + 6, z0 + 14, x0 + 29, y0 + 10, z0 + 15, 251, 15);
        for (int dy = 7; dy <= 9; dy++) {                              // lit from behind now
            put(c, x0 + 28, y0 + dy, z0 + 15, 95, dy == 8 ? 14 : 4);
            put(c, x0 + 28, y0 + dy, z0 + 14, 89, 0);
        }
        sign(c, x0 + 27, y0 + 8, z0 + 16, 3, "THE", "SUNDOWNER", "MOTEL", "");
        sign(c, x0 + 29, y0 + 8, z0 + 16, 3, "", "VACANCY", "", "");
        // The pool: dry at the shallow end, a foot of something over murk at the deep end, a
        // ladder out, and a fence along it with a gate and two panels down (the spawner hung
        // on the water, and there was no way out of the pool).
        fill(c, x0 + 10, y0 - 2, z0 + 12, x0 + 20, y0, z0 + 16, 0, 0);
        shell(c, x0 + 9, y0 - 3, z0 + 11, x0 + 21, y0 + 1, z0 + 17, 155, 0);
        fill(c, x0 + 10, y0 + 1, z0 + 12, x0 + 20, y0 + 1, z0 + 16, 0, 0);
        fill(c, x0 + 10, y0 - 2, z0 + 12, x0 + 15, y0 - 2, z0 + 16, 155, 0);
        fill(c, x0 + 16, y0 - 3, z0 + 12, x0 + 20, y0 - 3, z0 + 16, 88, 0);
        fill(c, x0 + 16, y0 - 2, z0 + 12, x0 + 20, y0 - 2, z0 + 16, 9, 0);
        fill(c, x0 + 10, y0 - 1, z0 + 14, x0 + 10, y0 + 1, z0 + 14, 65, 5);
        for (int dx = 9; dx <= 21; dx++) if (dx != 12 && dx != 18) put(c, x0 + dx, y0 + 1, z0 + 10, dx == 15 ? 107 : 85, 0);
        mob(c, x0 + 12, y0 - 1, z0 + 14, EntityType.SPIDER);
        // THE SURPRISE: room thirteen, boarded from the inside, with what is left of it.
        int kx = x0 + 13, kz = z0 + 1;
        shell(c, kx, y0 + 1, kz, kx + 3, y0 + 4, kz + 6, 159, 12);
        fill(c, kx + 1, y0 + 1, kz + 1, kx + 2, y0 + 3, kz + 5, 0, 0);
        fill(c, kx + 1, y0 + 1, kz + 6, kx + 1, y0 + 2, kz + 6, 5, 1);
        put(c, kx + 1, y0 + 1, kz + 2, 26, 2); put(c, kx + 1, y0 + 1, kz + 1, 26, 10);
        for (int i = 0; i < 6; i++) putIfAir(c, kx + 1 + (i & 1), y0 + 1 + r.nextInt(3), kz + 1 + r.nextInt(5), 30, 0);
        put(c, kx + 1, y0 + 3, kz + 4, 89, 0);
        mob(c, kx + 1, y0 + 1, kz + 3, EntityType.HUSK);
        trove(c, kx + 1, y0 + 1, kz + 5, r, MOTEL_LOOT, MOTEL_TROVE, 12 + r.nextInt(6));
        // A rail along any edge of the lot where the ground falls away below it (read from the
        // blocks, since a carved cliff is not in the height samples).
        for (int dx = 0; dx <= 30; dx++) for (int dz = 0; dz <= 18; dz++) {
            if (dx != 0 && dx != 30 && dz != 0 && dz != 18) continue;
            int x = x0 + dx, z = z0 + dz;
            if (!open(c, x, y0 + 1, z)) continue;
            int ox = dx == 0 ? x - 1 : dx == 30 ? x + 1 : x, oz = dz == 0 ? z - 1 : dz == 18 ? z + 1 : z;
            if (hollow(c, ox, y0 - 1, oz) && hollow(c, ox, y0 - 2, oz)) put(c, x, y0 + 1, z, 85, 0);
        }
    }

    // == audit helpers (structure audit 2026-09-23) ==============================
    // Everything below is local to this file. The block reads are chunk-local, like every
    // write here, so each chunk decides the same cell the same way.

    /** True when the cell is air (and in this chunk). */
    private static boolean open(Chunk c, int wx, int wy, int wz) {
        Block b = Megaliths.blockAt(c, wx, wy, wz);
        return b != null && b.getType() == Material.AIR;
    }

    /** True when the cell is in this chunk and nothing stands in it (air or liquid). */
    private static boolean hollow(Chunk c, int wx, int wy, int wz) {
        Block b = Megaliths.blockAt(c, wx, wy, wz);
        return b != null && (b.getType() == Material.AIR || b.isLiquid());
    }

    /** Furniture that must not cut into a partition, a core wall or another fitting. */
    private static void putIfAir(Chunk c, int wx, int wy, int wz, int id, int data) {
        if (open(c, wx, wy, wz)) put(c, wx, wy, wz, id, data);
    }

    /**
     * Takes the ground off a lot. ground() accepts several blocks of relief and footing()
     * only packs downward, so a hillside or a dead tree standing above the grade was left
     * in the streets and the doorways. This clears the footprint from the grade up to top
     * and faces every edge where the land outside stands higher with a retaining wall capped
     * flush with that land, so the lot reads as cut and levelled.
     */
    private static void levelled(Chunk c, Terrain t, int x0, int z0, int x1, int z1, int y0, int top,
                                 int wallId, int wallData, int capId, int capData) {
        fill(c, x0, y0 + 1, z0, x1, top, z1, 0, 0);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (x != x0 && x != x1 && z != z0 && z != z1) continue;
            if (Megaliths.blockAt(c, x, y0, z) == null) continue;
            int ox = x == x0 ? x - 1 : (x == x1 ? x + 1 : x), oz = z == z0 ? z - 1 : (z == z1 ? z + 1 : z);
            int h = Math.min(top, t.sample(ox, oz).y);
            if (h <= y0) continue;
            if (h > y0 + 1) fill(c, x, y0 + 1, z, x, h - 1, z, wallId, wallData);
            put(c, x, h, z, capId, capData);
        }
    }

    /** How far the land just outside a lot's edge stands above its grade, if steps can climb it. */
    private static int rise(Terrain t, int x, int z, int y0) {
        int d = t.sample(x, z).y - y0;
        return d >= 1 && d <= 12 ? d : 0;
    }

    /**
     * A stair flight cut through a retaining wall from the grade up to the land outside.
     * (ex, ez) is the edge cell of the first lane, (dx, dz) points out of the lot, and the
     * lanes run sideways from there; each step stands on stone brick.
     */
    private static void ramp(Chunk c, int ex, int ez, int dx, int dz, int width, int y0, int rise, int stairData) {
        if (rise < 1) return;
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0;
        for (int w = 0; w < width; w++) for (int k = 0; k < rise; k++) {
            int x = ex + px * w - dx * k, z = ez + pz * w - dz * k, y = y0 + rise - k;
            if (y - 1 >= y0 + 1) fill(c, x, y0 + 1, z, x, y - 1, z, 98, 0);
            put(c, x, y, z, 109, stairData);
            fill(c, x, y + 1, z, x, y + 3, z, 0, 0);
        }
    }

    /**
     * footing() stops six under the lowest sample, which leaves a plinth on a cliff lip or
     * over a hollow hanging in the air. This carries the column on down, through air,
     * water and anything loose, until it meets solid ground (at most depth further).
     */
    private static void planted(Chunk c, Site s, int wx, int wz, int id, int data, int depth) {
        footing(c, s, wx, wz, id, data);
        for (int y = s.lo - 7; y >= s.lo - 6 - depth; y--) {
            Block b = Megaliths.blockAt(c, wx, y, wz);
            if (b == null || (b.getType().isSolid() && b.getType() != Material.LEAVES
                    && b.getType() != Material.LEAVES_2)) return;
            put(c, wx, y, wz, id, data);
        }
    }

    /** A clear, paved way in to a door from outside the footprint, depth deep. */
    private static void approach(Chunk c, Terrain t, int x, int z, int dx, int dz, int width, int depth,
                                 int y0, int pave, int paveData) {
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0;
        for (int k = 1; k <= depth; k++) for (int w = 0; w < width; w++) {
            int ax = x + dx * k + px * w, az = z + dz * k + pz * w;
            fill(c, ax, y0 + 1, az, ax, y0 + 4, az, 0, 0);
            put(c, ax, y0, az, pave, paveData);
            int g = t.sample(ax, az).y;
            if (g < y0 - 1) fill(c, ax, Math.max(g + 1, y0 - 6), az, ax, y0 - 1, az, pave, paveData);
        }
    }

    /**
     * Rubble brought down from where it was drawn onto whatever stopped it, instead of hanging
     * there; never onto a box lid, a spawner, a ladder top, a pane or a fence.
     */
    private static void debris(Chunk c, int wx, int wy, int wz, int id, int floor) {
        if (Megaliths.blockAt(c, wx, wy, wz) == null) return;
        while (!open(c, wx, wy, wz) && wy < floor + 48) wy++;
        while (wy > floor && open(c, wx, wy - 1, wz)) wy--;
        Block below = Megaliths.blockAt(c, wx, wy - 1, wz);
        Material m = below == null ? Material.AIR : below.getType();
        if (m == Material.CHEST || m == Material.MOB_SPAWNER || m == Material.LADDER
                || m == Material.THIN_GLASS || m == Material.FENCE) return;
        put(c, wx, wy, wz, id, 0);
    }

    /**
     * Stone packed into any open space against the sides and the underside of a buried box,
     * so a cave that ran close shows rock rather than the builder's walls, and nothing in the
     * box drains out into it.
     */
    private static void encase(Chunk c, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            if (x != x0 && x != x1 && z != z0 && z != z1 && y != y0) continue;
            Block b = Megaliths.blockAt(c, x, y, z);
            if (b != null && (b.getType() == Material.AIR || b.isLiquid())) put(c, x, y, z, 1, 0);
        }
    }

    /** A two-high window, glazed or blown out; the pattern belongs to the building, not the RNG. */
    private static void pane(Chunk c, int wx, int wy, int wz, long seed) {
        boolean out = (Terrain.mix(seed + wx * 31L + wy * 17L + wz * 13L) & 3) == 0;
        fill(c, wx, wy, wz, wx, wy + 1, wz, out ? 0 : 102, 0);
    }

    /** A wall sign with its lettering. */
    private static void sign(Chunk c, int wx, int wy, int wz, int facing, String... lines) {
        Block b = Megaliths.blockAt(c, wx, wy, wz);
        if (b == null) return;
        put(c, wx, wy, wz, 68, facing);
        try {
            org.bukkit.block.BlockState st = b.getState();
            if (st instanceof org.bukkit.block.Sign) {
                org.bukkit.block.Sign sg = (org.bukkit.block.Sign) st;
                for (int i = 0; i < lines.length && i < 4; i++) sg.setLine(i, lines[i]);
                sg.update(true, false);
            }
        } catch (RuntimeException ignored) { }
    }

    /**
     * An office storey of an Old Town frame (they were bare slabs between the columns): four
     * pairs of desks with a chair at each, a glass-walled corner office with the storey's box
     * in it, filing on the west wall, and, floor by floor, cobwebs and a scorch.
     */
    private static void frameOffice(Chunk c, int bx, int fy, int bz, int seed) {
        int y = fy + 1;
        for (int dx = 6; dx <= 14; dx += 8) for (int dz = 6; dz <= 13; dz += 7) {
            putIfAir(c, bx + dx, y, bz + dz, 44, 15); putIfAir(c, bx + dx + 1, y, bz + dz, 44, 15);
            putIfAir(c, bx + dx, y, bz + dz + 1, 109, 2); putIfAir(c, bx + dx + 1, y, bz + dz + 1, 109, 2);
        }
        for (int d = 12; d <= 20; d++) {
            if (d != 18) { putIfAir(c, bx + d, y, bz + 15, 102, 0); putIfAir(c, bx + d, y + 1, bz + 15, 102, 0); }
            putIfAir(c, bx + 12, y, bz + d, 102, 0); putIfAir(c, bx + 12, y + 1, bz + d, 102, 0);
        }
        for (int dz = 3; dz <= 13; dz++) if (dz == 3 || dz == 4 || dz == 12 || dz == 13) {
            putIfAir(c, bx + 1, y, bz + dz, 47, 0); putIfAir(c, bx + 1, y + 1, bz + dz, 47, 0);
        }
        if ((seed & 1) == 0) putIfAir(c, bx + 20, y + 2, bz + 1, 30, 0);
        if ((seed & 2) == 0) putIfAir(c, bx + 1, y + 2, bz + 20, 30, 0);
        if ((seed & 4) == 0) for (int d = 0; d < 3; d++) {
            Block b = Megaliths.blockAt(c, bx + 8 + d, fy, bz + 17 - (d & 1));
            if (b != null && b.getType() == Material.STEP) put(c, bx + 8 + d, fy, bz + 17 - (d & 1), 251, 15);
        }
    }
}
