package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import static chat.jaspr.biomes.Megaliths.sunken;
import static chat.jaspr.biomes.Megaliths.trove;

/**
 * Wonders: the places built to be looked at.
 *
 * The same church twice -- once with the roof on and the congregation still in it, once
 * burnt out with something else in it -- a pyramid that is clearly not old, a city on
 * the sea bed, a white city that never got off the ground, a water park, and a funfair.
 *
 * As everywhere else the boxes pay by depth: the porch of the choir is worth very little
 * and the crypt under it is worth a great deal, and that is decided by where the box is
 * rather than by a number typed next to it.
 */
public final class Wonders {
    private Wonders() { }

    private static final int CHOIR_CELL = 110, CHANCEL_CELL = 114, PYRAMID_CELL = 126;
    private static final int RAPTURE_CELL = 121, COLUMBIA_CELL = 138;
    private static final int LAGOON_CELL = 105, PARK_CELL = 111;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        choir(c, t);
        chancel(c, t);
        mastaba(c, t);
        rapture(c, t);
        columbia(c, t);
        lagoon(c, t);
        wonderland(c, t);
    }

    /**
     * The shell both churches share.
     *
     * They are the same building. One of them still has a roof and a congregation, the
     * other has neither, and the only honest way to make that land is to raise the same
     * nave, the same aisles and the same tower in both and then take things away.
     */
    private static void basilica(Chunk c, Site s, Random r, boolean burnt) {
        int x0 = s.x, z0 = s.z, y0 = s.y;
        int wall = burnt ? 98 : 251, data = burnt ? 2 : 15;
        for (int dx = 0; dx <= 42; dx++) for (int dz = 0; dz <= 34; dz++)
            footing(c, s, x0 + dx, z0 + dz, 98, 0);
        fill(c, x0, y0, z0, x0 + 42, y0, z0 + 34, 98, burnt ? 2 : 0);
        // Nave and aisles.
        shell(c, x0 + 5, y0 + 1, z0 + 2, x0 + 37, y0 + 21, z0 + 32, wall, data);
        fill(c, x0 + 6, y0 + 1, z0 + 3, x0 + 36, y0 + 20, z0 + 31, 0, 0);
        fill(c, x0 + 6, y0 + 1, z0 + 3, x0 + 36, y0 + 1, z0 + 31, burnt ? 4 : 155, 0);
        // Burnt: soot-black concrete 251:15 (was coal block); intact: the red runner, as red wool laid in the
        // floor course (audit 2026-09-23: it was carpet in place of the floor block, a step down from the
        // aisles, so every pew, spawner and the altar plinth stood 15/16 of a block over it).
        fill(c, x0 + 15, y0 + 1, z0 + 3, x0 + 27, y0 + 1, z0 + 31, burnt ? 251 : 35, burnt ? 15 : 14);
        for (int dz = 5; dz <= 29; dz += 4) {
            for (int side = 0; side < 2; side++) {
                int px = side == 0 ? x0 + 13 : x0 + 29;
                fill(c, px, y0 + 2, z0 + dz, px, y0 + 14, z0 + dz, wall, burnt ? 3 : 15);
                fill(c, px, y0 + 15, z0 + dz - 1, px, y0 + 15, z0 + dz + 1, wall, data);
            }
            fill(c, x0 + 5, y0 + 8, z0 + dz - 1, x0 + 5, y0 + 13, z0 + dz + 1, burnt ? 0 : 95, burnt ? 0 : 15);
            fill(c, x0 + 37, y0 + 8, z0 + dz - 1, x0 + 37, y0 + 13, z0 + dz + 1, burnt ? 0 : 95, burnt ? 0 : 15);
        }
        if (!burnt) {
            fill(c, x0 + 13, y0 + 17, z0 + 3, x0 + 29, y0 + 17, z0 + 31, 44, 7);
            fill(c, x0 + 16, y0 + 19, z0 + 3, x0 + 26, y0 + 19, z0 + 31, wall, data);
            fill(c, x0 + 19, y0 + 21, z0 + 3, x0 + 23, y0 + 21, z0 + 31, 89, 0);
            // Audit 2026-09-23: an architrave along the arcade so the ceiling bears on it (it hung a block
            // over the pier heads), and the middle of the ceiling and the attic floor in black glass, so the
            // lit ridge shows from the nave as the sky of glass the exterior promises.
            fill(c, x0 + 13, y0 + 16, z0 + 3, x0 + 13, y0 + 16, z0 + 31, wall, data);
            fill(c, x0 + 29, y0 + 16, z0 + 3, x0 + 29, y0 + 16, z0 + 31, wall, data);
            fill(c, x0 + 19, y0 + 17, z0 + 3, x0 + 23, y0 + 17, z0 + 31, 95, 15);
            fill(c, x0 + 19, y0 + 19, z0 + 3, x0 + 23, y0 + 19, z0 + 31, 95, 15);
        }
        // The west front, the rose window, and the door.
        fill(c, x0 + 18, y0 + 1, z0 + 2, x0 + 24, y0 + 6, z0 + 2, 0, 0);
        // Audit: the threshold is floor, flush with the nave (it was a hole a block below it), and where the
        // ground in front of the door stands higher than the plinth, steps go up to it -- the door is the only
        // way in or out. Only the row just outside the footprint is read, which is in this chunk.
        fill(c, x0 + 18, y0 + 1, z0 + 2, x0 + 24, y0 + 1, z0 + 2, burnt ? 4 : 35, burnt ? 0 : 14);
        for (int x = x0 + 18; x <= x0 + 24; x++) {
            int g = top(c, x, z0 - 1, y0 - 1, y0 + 6);
            if (g == Integer.MIN_VALUE || g - y0 < 2) continue;
            if (g - y0 == 2) { put(c, x, y0 + 1, z0, 109, 3); continue; }
            put(c, x, y0 + 1, z0 + 1, 109, 3);
            put(c, x, y0 + 1, z0, 98, 0);
            put(c, x, y0 + 2, z0, 109, 3);
            if (g - y0 >= 4) {
                put(c, x, y0 + 3, z0 - 1, 109, 3);
                fill(c, x, y0 + 4, z0 - 1, x, y0 + 6, z0 - 1, 0, 0);
            }
        }
        for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dy * dy + dz * dz);
            if (d > 4.4) continue;
            put(c, x0 + 21 + dz, y0 + 13 + dy, z0 + 2, burnt ? 0 : 95, burnt ? 0 : (int) (d) % 16);
        }
        // The tower.
        int tx = x0 + 21, tz = z0 + 33;
        int top = burnt ? 16 : 32;
        shell(c, tx - 4, y0 + 1, tz - 5, tx + 4, y0 + top, tz + 1, wall, data);
        fill(c, tx - 3, y0 + 1, tz - 4, tx + 3, y0 + top - 1, tz, 0, 0);
        fill(c, tx - 3, y0 + 1, tz - 4, tx - 3, y0 + top - 2, tz - 4, 65, 3);
        for (int dy = 8; dy <= top - 4; dy += 8) fill(c, tx - 4, y0 + dy, tz - 3, tx - 4, y0 + dy + 2, tz - 1,
                burnt ? 0 : 95, burnt ? 0 : 15);
        if (!burnt) {
            fill(c, tx - 5, y0 + top + 1, tz - 6, tx + 5, y0 + top + 1, tz + 2, 44, 7);
            for (int dy = 0; dy <= 5; dy++) {
                int ri = 4 - dy * 4 / 5;
                shell(c, tx - ri, y0 + top + 2 + dy, tz - 2 - ri, tx + ri, y0 + top + 2 + dy, tz - 2 + ri, wall, data);
            }
            put(c, tx, y0 + top - 3, tz - 2, 89, 0);
        }
    }

    // == 44. The Long Choir ======================================================
    // The roof is on, the glass is in, the candles are lit and the pews are not empty.
    // Whatever they were waiting for, they are still waiting for it, and they do not
    // take the interruption well.

    private static final int[][] CHOIR_LOOT = {
        {438, 0,  8, 1,  2}, {441, 0,  6, 1,  1}, {439, 0, 10, 4, 12}, {375, 0, 11, 3,  9},
        {377, 0, 10, 2,  6}, {331, 0, 11, 6, 18}, {340, 0, 10, 2,  6}, {266, 0, 10, 3,  9},
        {426, 0, 12, 4, 12}, {388, 0,  8, 1,  3}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] CHOIR_TROVE = {
        {116, 0, 1, 1, 1}, {403, 0, 1, 3, 4}, {384, 0, 1, 12, 18},
    };

    private static void choir(Chunk c, Terrain t) {
        Site s = ground(t, c, CHOIR_CELL, 0x43554C54L, 43, 35, 7, Megaliths.RANK_CHOIR);
        if (s == null) return;
        Random r = new Random(s.seed);
        basilica(c, s, r, false);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        // Pews, candles, and the congregation in them.
        for (int dz = 6; dz <= 24; dz += 2) {
            fill(c, x0 + 16, y0 + 2, z0 + dz, x0 + 19, y0 + 2, z0 + dz, 53, 2);
            fill(c, x0 + 23, y0 + 2, z0 + dz, x0 + 26, y0 + 2, z0 + dz, 53, 2);
            if ((dz & 3) == 2) {
                put(c, x0 + 14, y0 + 2, z0 + dz, 85, 0);
                put(c, x0 + 14, y0 + 3, z0 + dz, 89, 0);
                put(c, x0 + 28, y0 + 2, z0 + dz, 85, 0);
                put(c, x0 + 28, y0 + 3, z0 + dz, 89, 0);
            }
            if (dz % 6 == 0) mob(c, x0 + 21, y0 + 2, z0 + dz, EntityType.VINDICATOR);
        }
        // Audit 2026-09-23: shrines along the aisles between the window bays -- an obsidian altar with a candle
        // on it and a kneeler -- which also light the aisles (the far end of each was pitch dark), and a
        // candle at the south end of each aisle.
        for (int dz = 7; dz <= 25; dz += 6) for (int side = 0; side < 2; side++) {
            int ax = side == 0 ? x0 + 6 : x0 + 36, kx = side == 0 ? x0 + 7 : x0 + 35;
            put(c, ax, y0 + 2, z0 + dz, 49, 0);
            put(c, ax, y0 + 3, z0 + dz, 85, 0);
            put(c, ax, y0 + 4, z0 + dz, 89, 0);
            put(c, kx, y0 + 2, z0 + dz, 126, 0);
        }
        for (int side = 0; side < 2; side++) {
            put(c, side == 0 ? x0 + 8 : x0 + 34, y0 + 2, z0 + 30, 85, 0);
            put(c, side == 0 ? x0 + 8 : x0 + 34, y0 + 3, z0 + 30, 89, 0);
        }
        // The altar: a black sun on a gold plinth, and a sky of glass over it.
        fill(c, x0 + 17, y0 + 2, z0 + 27, x0 + 25, y0 + 3, z0 + 30, 251, 4);   // plinth: yellow concrete, was gold
        fill(c, x0 + 18, y0 + 4, z0 + 28, x0 + 24, y0 + 4, z0 + 29, 49, 0);
        for (int dy = 0; dy <= 6; dy++) for (int dx = -6; dx <= 6; dx++) {
            double d = Math.sqrt(dx * dx + (dy - 3) * (dy - 3) * 2.2);
            if (d > 6.2 || d < 4.4) continue;
            // Obsidian / red concrete checker (red concrete was redstone block, which powered nothing).
            put(c, x0 + 21 + dx, y0 + 6 + dy, z0 + 31, ((dx + dy) & 1) == 0 ? 49 : 251, ((dx + dy) & 1) == 0 ? 0 : 14);
        }
        fill(c, x0 + 19, y0 + 7, z0 + 31, x0 + 23, y0 + 11, z0 + 31, 49, 0);
        for (int a = 0; a < 12; a++) {
            double ang = a * Math.PI / 6;
            put(c, x0 + 21 + (int) Math.round(Math.cos(ang) * 5), y0 + 9 + (int) Math.round(Math.sin(ang) * 3),
                z0 + 31, 89, 0);
        }
        mob(c, x0 + 21, y0 + 2, z0 + 26, EntityType.EVOKER);
        mob(c, x0 + 18, y0 + 2, z0 + 26, EntityType.VEX);
        mob(c, x0 + 24, y0 + 2, z0 + 26, EntityType.VEX);
        // Audit 2026-09-23. The tower stands over the south end of the nave, so its north wall ran across
        // the altar: the sun was walled up inside the sealed tower with two stubs of its ring showing, the
        // altar chests were set into the tower's side walls under solid concrete, and the tower's ladder
        // climbed past a floating spawner and lamp to no floor. Now a chancel arch opens the wall behind the
        // altar, so the sun is the altarpiece in a niche, standing on a black dais; steps between the
        // spawners go up onto the plinth; the chests stand on the plinth's front corners; and the ladder,
        // reached from the niche sill, climbs to a bell chamber with the vex on its floor.
        fill(c, x0 + 19, y0 + 5, z0 + 28, x0 + 23, y0 + 13, z0 + 28, 0, 0);          // the chancel arch
        put(c, x0 + 19, y0 + 12, z0 + 28, 251, 15); put(c, x0 + 19, y0 + 13, z0 + 28, 251, 15);
        put(c, x0 + 23, y0 + 12, z0 + 28, 251, 15); put(c, x0 + 23, y0 + 13, z0 + 28, 251, 15);
        fill(c, x0 + 15, y0 + 6, z0 + 31, x0 + 16, y0 + 12, z0 + 31, 0, 0);          // the ring's stubs outside the tower
        fill(c, x0 + 26, y0 + 6, z0 + 31, x0 + 27, y0 + 12, z0 + 31, 0, 0);
        fill(c, x0 + 18, y0 + 1, z0 + 29, x0 + 24, y0 + 1, z0 + 30, 35, 14);         // floor under the plinth (and the stray rung)
        fill(c, x0 + 18, y0 + 1, z0 + 31, x0 + 24, y0 + 3, z0 + 33, 251, 15);        // the sun's dais
        put(c, x0 + 19, y0 + 2, z0 + 26, 156, 2); put(c, x0 + 20, y0 + 2, z0 + 26, 156, 2);
        put(c, x0 + 22, y0 + 2, z0 + 26, 156, 2); put(c, x0 + 23, y0 + 2, z0 + 26, 156, 2);
        fill(c, x0 + 18, y0 + 23, z0 + 29, x0 + 24, y0 + 23, z0 + 33, 251, 15);      // the bell chamber floor
        put(c, x0 + 18, y0 + 23, z0 + 29, 65, 3);                                     // the ladder comes up through it
        fill(c, x0 + 18, y0 + 24, z0 + 29, x0 + 18, y0 + 30, z0 + 29, 0, 0);         // and stops there
        fill(c, x0 + 21, y0 + 30, z0 + 31, x0 + 21, y0 + 31, z0 + 31, 85, 0);        // the lamp hangs from the roof
        fill(c, x0 + 22, y0 + 28, z0 + 32, x0 + 22, y0 + 31, z0 + 32, 85, 0);        // and the bell beside it
        put(c, x0 + 22, y0 + 27, z0 + 32, 25, 0);
        fill(c, x0 + 25, y0 + 24, z0 + 30, x0 + 25, y0 + 26, z0 + 32, 95, 15);       // belfry openings, like the west one
        fill(c, x0 + 20, y0 + 24, z0 + 34, x0 + 22, y0 + 26, z0 + 34, 95, 15);
        graded(c, s, x0 + 17, y0 + 4, z0 + 27, r, CHOIR_LOOT, false);
        graded(c, s, x0 + 25, y0 + 4, z0 + 27, r, CHOIR_LOOT, true);
        graded(c, s, x0 + 9, y0 + 2, z0 + 8, r, CHOIR_LOOT, false);
        graded(c, s, x0 + 33, y0 + 2, z0 + 28, r, CHOIR_LOOT, false);
        mob(c, x0 + 9, y0 + 2, z0 + 20, EntityType.WITCH);
        mob(c, x0 + 33, y0 + 2, z0 + 12, EntityType.VINDICATOR);
        mob(c, x0 + 21, y0 + 24, z0 + 31, EntityType.VEX);
        // THE SURPRISE: the undercroft, down behind the altar, where they keep it.
        // (Audit: its ladder shaft is cut after the undercroft is built -- cut before, the undercroft's roof
        // capped it and a pillar filled it.)
        int cy = y0 - 8;
        fill(c, x0 + 14, cy, z0 + 20, x0 + 28, cy + 6, z0 + 30, 0, 0);
        shell(c, x0 + 13, cy - 1, z0 + 19, x0 + 29, cy + 7, z0 + 31, 49, 0);
        fill(c, x0 + 14, cy - 1, z0 + 20, x0 + 28, cy - 1, z0 + 30, 251, 15);
        for (int a = 0; a < 5; a++) {
            double a1 = a * 2 * Math.PI / 5 - Math.PI / 2, a2 = (a + 2) * 2 * Math.PI / 5 - Math.PI / 2;
            int ax = x0 + 21 + (int) Math.round(Math.cos(a1) * 5), az = z0 + 25 + (int) Math.round(Math.sin(a1) * 4);
            int bx = x0 + 21 + (int) Math.round(Math.cos(a2) * 5), bz = z0 + 25 + (int) Math.round(Math.sin(a2) * 4);
            for (int step = 0; step <= 12; step++)
                put(c, ax + (bx - ax) * step / 12, cy - 1, az + (bz - az) * step / 12, 251, 14);   // was redstone
        }
        for (int dx = 15; dx <= 27; dx += 6) {
            fill(c, x0 + dx, cy, z0 + 21, x0 + dx, cy + 6, z0 + 21, 49, 0);
            // (audit: the south-east pillar stands a block east, clear of the shaft, and backs its ladder;
            // the south row is lit like the north one)
            int sx = dx == 27 ? x0 + 28 : x0 + dx;
            fill(c, sx, cy, z0 + 29, sx, cy + 6, z0 + 29, 49, 0);
            put(c, x0 + dx, cy + 3, z0 + 21, 89, 0);
            if (dx < 27) put(c, x0 + dx, cy + 3, z0 + 29, 89, 0);
        }
        fill(c, x0 + 27, cy, z0 + 29, x0 + 27, y0 + 1, z0 + 29, 0, 0);
        fill(c, x0 + 27, cy, z0 + 29, x0 + 27, y0 + 1, z0 + 29, 65, 4);
        fill(c, x0 + 20, cy, z0 + 25, x0 + 22, cy + 1, z0 + 25, 251, 4);   // dais: yellow concrete, was gold block
        mob(c, x0 + 21, cy, z0 + 23, EntityType.EVOKER);
        mob(c, x0 + 16, cy, z0 + 28, EntityType.VINDICATOR);
        mob(c, x0 + 26, cy, z0 + 22, EntityType.VINDICATOR);
        trove(c, x0 + 21, cy, z0 + 26, r, CHOIR_LOOT, CHOIR_TROVE, 16 + r.nextInt(6));
    }

    // == 45. The Burnt Chancel ===================================================
    // The same church, somewhere else, with the roof gone, the glass out, the tower
    // snapped off across the nave and everything black to the waist. The congregation
    // is still here too. It has simply been here longer.

    private static final int[][] CHANCEL_LOOT = {
        { 263, 1, 14, 8, 24}, {2262, 0,  5, 1,  1}, {397, 1,  4, 1,  1}, {352, 0, 13, 6, 18},
        { 367, 0, 12, 4, 12}, { 340, 0,  9, 2,  6}, {432, 0, 10, 4, 12}, {266, 0,  9, 2,  6},
        { 375, 0, 10, 3,  9}, { 388, 0,  7, 1,  3}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] CHANCEL_TROVE = {
        {397, 1, 1, 2, 2}, {133, 0, 1, 1, 2}, {403, 0, 1, 2, 3},
    };

    private static void chancel(Chunk c, Terrain t) {
        Site s = ground(t, c, CHANCEL_CELL, 0x52554E45L, 43, 35, 7, Megaliths.RANK_CHANCEL);
        if (s == null) return;
        Random r = new Random(s.seed);
        basilica(c, s, r, true);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        // What the fire left: soot up the walls, pews down, and the tower across the nave.
        // (Audit 2026-09-23: the char used to be cubes dropped at random heights through the nave, most of
        // them left floating; the same draws now only scorch the floor, and the soot is a band up the
        // walls and piers below. The draw that set the height is kept so the loot rolled later is unchanged.)
        for (int i = 0; i < 150; i++) {
            int gx = x0 + 5 + r.nextInt(33), gz = z0 + 2 + r.nextInt(31);
            r.nextInt(9);
            boolean in = gx > x0 + 5 && gx < x0 + 37 && gz > z0 + 2 && gz < z0 + 32;
            if (r.nextInt(4) == 0) { if (in) put(c, gx, y0 + 1, gz, 251, 15); }   // char: black concrete, was coal block
            else if (r.nextInt(3) == 0) { if (in) put(c, gx, y0 + 1, gz, 4, 0); }
        }
        for (int x = x0 + 15; x <= x0 + 27; x++) for (int z = z0 + 3; z <= z0 + 31; z++)
            if (pick(s, x, 0, z, 3) == 0) put(c, x, y0 + 1, z, 159, 15);       // the black strip, matte in places
        for (int x = x0 + 5; x <= x0 + 37; x++) { soot(c, s, x, y0, z0 + 2); soot(c, s, x, y0, z0 + 32); }
        for (int z = z0 + 3; z <= z0 + 31; z++) { soot(c, s, x0 + 5, y0, z); soot(c, s, x0 + 37, y0, z); }
        for (int dz = 5; dz <= 29; dz += 4) { soot(c, s, x0 + 13, y0, z0 + dz); soot(c, s, x0 + 29, y0, z0 + dz); }
        // Roof gone (audit: shell() had left the whole lid on): the lid inside the walls comes off, the wall
        // tops are broken down one to three courses -- except at the corners, round the rose window, and
        // where a charred beam still rests -- and the nave wall over the stump's inside goes with its roof.
        fill(c, x0 + 6, y0 + 21, z0 + 3, x0 + 36, y0 + 21, z0 + 31, 0, 0);
        fill(c, x0 + 18, y0 + 16, z0 + 32, x0 + 24, y0 + 21, z0 + 32, 0, 0);
        int[][] beams = {{x0 + 5, z0 + 9, 6}, {x0 + 37, z0 + 17, -6}, {x0 + 5, z0 + 25, 4}, {x0 + 37, z0 + 21, -4}};
        for (int x = x0 + 5; x <= x0 + 37; x++) for (int z = z0 + 2; z <= z0 + 32; z++) {
            if (x != x0 + 5 && x != x0 + 37 && z != z0 + 2 && z != z0 + 32) continue;
            boolean corner = (x <= x0 + 6 || x >= x0 + 36) && (z <= z0 + 3 || z >= z0 + 31);
            if (corner || (z == z0 + 2 && x >= x0 + 14 && x <= x0 + 28) || (z == z0 + 32 && x >= x0 + 17 && x <= x0 + 25)) continue;
            boolean held = false;
            for (int[] b : beams) held |= b[0] == x && b[1] == z;
            int p = z == z0 + 2 || z == z0 + 32 ? x : z, w = x == x0 + 5 ? 0 : x == x0 + 37 ? 1 : z == z0 + 2 ? 2 : 3;
            int drop = (int) Math.round(1.5 + 1.5 * Math.sin(p * 0.41 + pick(s, w, 30, 0, 628) / 100.0))
                     + (pick(s, x, 1, z, 6) == 0 ? 1 : 0);             // a ragged line, not a comb
            drop = held ? 0 : Math.min(3, drop);
            if (drop > 0) fill(c, x, y0 + 22 - drop, z, x, y0 + 21, z, 0, 0);
        }
        for (int[] b : beams)                                        // charred beams, broken off mid-span
            for (int k = 1; k <= Math.abs(b[2]); k++) put(c, b[0] + Integer.signum(b[2]) * k, y0 + 20, b[1], 17, 5);
        // Pews down: what is left of them lies on the floor either side of the fallen tower -- toppled onto
        // their backs, broken to a slab, a few still standing (audit: they were an inlay flush with the floor).
        for (int dz = 6; dz <= 24; dz += 2) {
            if ((dz & 3) == 0) continue;
            int[] xs = dz == 6 ? new int[]{16, 17, 18, 24, 25, 26} : new int[]{14, 15, 16, 26, 27, 28};
            for (int ox : xs) {
                int p = pick(s, x0 + ox, 2, z0 + dz, 6);
                if (p < 2) continue;
                put(c, x0 + ox, y0 + 2, z0 + dz, p == 5 ? 126 : 53, p == 2 ? 2 : p == 5 ? 1 : 4 + pick(s, x0 + ox, 3, z0 + dz, 4));
            }
            // (the dz 18 spawner lay under the fallen tower and was lost; it stands beside it now)
            if (dz == 6) mob(c, x0 + 21, y0 + 1, z0 + dz, EntityType.ZOMBIE);
            if (dz == 18) mob(c, x0 + 15, y0 + 1, z0 + 20, EntityType.ZOMBIE);
        }
        // The fallen tower (audit: it was a flat strip of paving flush with the floor, with a pit at its
        // end): the top of the tower lies on its side down the middle of the nave, north from the stump --
        // its two side walls broken into lengths, spans of the face that was uppermost still across them
        // near the stump, a window in each side, the rubble it threw, and the fire still in it.
        for (int i = 0; i <= 17; i++) {
            int fz = z0 + 26 - i;
            int base = i <= 4 ? 6 : i == 5 ? 0 : i <= 9 ? 4 : i == 10 ? 1 : i <= 14 ? 3 : 1;
            for (int side = 0; side < 2; side++) {
                int wx = side == 0 ? x0 + 17 : x0 + 25;
                int h = base == 6 || base <= 1 ? base : base - pick(s, wx, 0, fz, 2);
                if (i == 5 && side == 1) h = 2;
                if (i >= 15 && pick(s, wx, 1, fz, 2) == 0) h = 0;
                for (int dy = 0; dy < h; dy++) {
                    if (i >= 2 && i <= 3 && dy >= 2 && dy <= 3) continue;            // its window
                    boolean quoin = dy == h - 1 && (i == 0 || i == 4 || i == 6 || i == 9 || i == 11 || i == 14);
                    put(c, wx, y0 + 2 + dy, fz, 98, quoin ? 3 : pick(s, wx, dy, fz, 3) == 0 ? 0 : 2);
                }
            }
            int span = i <= 4 ? new int[]{7, 7, 5, 3, 6}[i] : 0;
            for (int dx = 1; dx <= span; dx++) put(c, x0 + 17 + dx, y0 + 7, fz, 98, 2);
            if (i <= 4) fill(c, x0 + 18, y0 + 2, fz, x0 + 24, y0 + 6, fz, 0, 0);   // hollow, as a tower is
            if (i % 6 == 3) { put(c, x0 + 21, y0 + 2, fz, 87, 0); put(c, x0 + 21, y0 + 3, fz, 51, 0); }
        }
        for (int z = z0 + 8; z <= z0 + 26; z++) for (int x = x0 + 14; x <= x0 + 28; x++) {
            boolean fan = z <= z0 + 12 && x >= x0 + 15 && x <= x0 + 27;             // the broken end
            boolean spill = x == x0 + 15 || x == x0 + 16 || x == x0 + 26 || x == x0 + 27;
            if (!fan && !spill) continue;
            if (!air(c, x, y0 + 2, z) || solidIs(c, x, y0 + 1, z, 52)) continue;
            int p = pick(s, x, 4, z, fan ? 4 : 7);
            if (p == 0) put(c, x, y0 + 2, z, 4, 0);
            else if (p == 1) put(c, x, y0 + 2, z, 98, 2);
            else if (p == 2) put(c, x, y0 + 2, z, 109, pick(s, x, 5, z, 8));
        }
        fill(c, x0 + 17, y0 + 1, z0 + 27, x0 + 25, y0 + 2, z0 + 30, 98, 2);
        fill(c, x0 + 19, y0 + 3, z0 + 28, x0 + 23, y0 + 3, z0 + 29, 251, 15);   // black concrete, was coal block
        // The stump (audit: it was a sealed box with a flat roof, lower than the nave, whose ladder ended
        // under its own ceiling): no roof, a broken rim standing over the nave walls, a door from the nave,
        // a floor, and a ledge at the top of its ladder with the ends of burnt joists.
        int tx = x0 + 21, tz = z0 + 33;
        fill(c, tx - 3, y0 + 16, tz - 4, tx + 3, y0 + 16, tz, 0, 0);
        for (int x = tx - 4; x <= tx + 4; x++) for (int z = tz - 5; z <= tz + 1; z++) {
            if (x != tx - 4 && x != tx + 4 && z != tz - 5 && z != tz + 1) continue;
            // (the break slopes down to the north, the way the top went, ragged by a course or two)
            int h = 16 + (int) Math.round(9.0 * (z - (tz - 5)) / 6.0) + pick(s, x, 6, z, 3);
            if (h > 16) fill(c, x, y0 + 17, z, x, y0 + h, z, 98, pick(s, x, 7, z, 4) == 0 ? 0 : 2);
        }
        fill(c, x0 + 17, y0 + 2, z0 + 31, x0 + 17, y0 + 4, z0 + 31, 0, 0);
        fill(c, x0 + 18, y0 + 1, z0 + 31, x0 + 24, y0 + 1, z0 + 33, 4, 0);
        fill(c, x0 + 19, y0 + 14, z0 + 29, x0 + 24, y0 + 14, z0 + 30, 98, 2);
        put(c, x0 + 20, y0 + 14, z0 + 31, 5, 1);
        put(c, x0 + 23, y0 + 14, z0 + 31, 5, 1);
        // The rest of the nave: a scorched pulpit, a dry font by the door, snuffed candle stands, rubble at
        // the foot of the walls, what is left of the congregation, cobwebs in the arcade, and ash.
        put(c, x0 + 15, y0 + 2, z0 + 26, 98, 3);
        put(c, x0 + 15, y0 + 3, z0 + 26, 113, 0);
        put(c, x0 + 14, y0 + 2, z0 + 26, 109, 0);
        put(c, x0 + 15, y0 + 2, z0 + 4, 98, 3);
        put(c, x0 + 15, y0 + 3, z0 + 4, 118, 0);
        for (int dz = 8; dz <= 24; dz += 8) for (int side = 0; side < 2; side++)
            fill(c, side == 0 ? x0 + 14 : x0 + 28, y0 + 2, z0 + dz, side == 0 ? x0 + 14 : x0 + 28, y0 + 3, z0 + dz, 113, 0);
        for (int z = z0 + 4; z <= z0 + 30; z++) for (int side = 0; side < 2; side++) {
            int x = side == 0 ? x0 + 6 : x0 + 36, p = pick(s, x, 8, z, 6);
            if (p < 3 && air(c, x, y0 + 2, z)) put(c, x, y0 + 2, z, p == 0 ? 4 : p == 1 ? 98 : 109, p == 1 ? 2 : p == 2 ? pick(s, x, 9, z, 8) : 0);
        }
        int[][] bones = {{16, 12}, {27, 19}, {10, 22}, {33, 9}, {12, 29}};
        for (int[] b : bones) if (air(c, x0 + b[0], y0 + 2, z0 + b[1])) put(c, x0 + b[0], y0 + 2, z0 + b[1], 216, 4);
        put(c, x0 + 14, y0 + 13, z0 + 9, 30, 0);  put(c, x0 + 14, y0 + 13, z0 + 21, 30, 0);
        put(c, x0 + 28, y0 + 13, z0 + 13, 30, 0); put(c, x0 + 28, y0 + 13, z0 + 25, 30, 0);
        graded(c, s, x0 + 8, y0 + 2, z0 + 6, r, CHANCEL_LOOT, false);
        graded(c, s, x0 + 34, y0 + 2, z0 + 30, r, CHANCEL_LOOT, false);
        graded(c, s, x0 + 21, y0 + 3, z0 + 27, r, CHANCEL_LOOT, true);    // on the altar, not in the tower wall
        mob(c, x0 + 10, y0 + 1, z0 + 14, EntityType.ZOMBIE);
        mob(c, x0 + 32, y0 + 1, z0 + 20, EntityType.HUSK);
        mob(c, x0 + 9, y0 + 1, z0 + 26, EntityType.ZOMBIE);                 // was beside the one at dz 6
        mob(c, x0 + 26, y0 + 1, z0 + 24, EntityType.ZOMBIE);
        for (int x = x0 + 6; x <= x0 + 36; x++) for (int z = z0 + 3; z <= z0 + 31; z++) {
            int p = pick(s, x, 10, z, 14);
            if (p < 2 && air(c, x, y0 + 2, z) && !air(c, x, y0 + 1, z) && !solidIs(c, x, y0 + 1, z, 52))
                put(c, x, y0 + 2, z, 171, p == 0 ? 15 : 7);
        }
        // THE SURPRISE: the crypt, which the fire never got into.
        // (Audit: the tombs are laid a block further west -- the east pair ran through the crypt's wall --
        // which leaves the trove on the floor between two of them; the shaft is cut after the crypt is built,
        // against its south wall, where the crypt's ceiling used to cap it.)
        int cy = y0 - 8;
        fill(c, x0 + 14, cy, z0 + 20, x0 + 28, cy + 5, z0 + 30, 0, 0);
        shell(c, x0 + 13, cy - 1, z0 + 19, x0 + 29, cy + 6, z0 + 31, 98, 1);
        fill(c, x0 + 14, cy - 1, z0 + 20, x0 + 28, cy - 1, z0 + 30, 98, 0);
        for (int i = 0; i < 8; i++) {
            int px = x0 + 14 + (i % 4) * 4, pz = z0 + 22 + (i / 4) * 5;
            fill(c, px, cy, pz, px + 2, cy + 1, pz + 2, 155, 0);
            put(c, px + 1, cy + 2, pz + 1, 44, 7);
            if (i == 3) put(c, px + 1, cy + 1, pz + 1, 216, 0);
        }
        for (int dx = 15; dx <= 27; dx += 6) {
            fill(c, x0 + dx, cy, z0 + 20, x0 + dx, cy + 5, z0 + 20, 98, 3);
            put(c, x0 + dx, cy + 3, z0 + 20, 89, 0);
        }
        fill(c, x0 + 15, cy, z0 + 30, x0 + 15, y0 + 1, z0 + 30, 0, 0);
        fill(c, x0 + 15, cy, z0 + 30, x0 + 15, y0 + 1, z0 + 30, 65, 2);
        mob(c, x0 + 21, cy, z0 + 25, EntityType.ZOMBIE);
        mob(c, x0 + 17, cy, z0 + 29, EntityType.HUSK);
        mob(c, x0 + 25, cy, z0 + 21, EntityType.WITHER_SKELETON);
        trove(c, x0 + 21, cy, z0 + 28, r, CHANCEL_LOOT, CHANCEL_TROVE, 15 + r.nextInt(6));
    }

    // == 46. The Mastaba =========================================================
    // A stepped pyramid forty-nine across with seams of light up every face and a black
    // glass cap, which would be remarkable enough if it were old. It is not old. There
    // is a hall under the cap, three ring galleries under the hall, and under the
    // foundation a room with columns in it that the pyramid appears to have been built
    // on top of in order to keep shut.

    private static final int[][] PYRAMID_LOOT = {
        { 24, 0, 13, 8, 24}, {179, 0, 11, 6, 18}, {155, 0, 10, 4, 12}, {153, 0,  8, 2, 6},
        {266, 0, 12,  4, 12}, { 41, 0,  6, 1,  2}, {348, 0, 10, 4, 12}, {264, 0, 8, 1, 3},
        {388, 0,  9,  2, 6}, {331, 0, 10, 4, 12}, {384, 0,  8, 2,  6}, {403, 0, 5, 1, 1},
    };
    private static final int[][] PYRAMID_TROVE = {
        { 41, 0, 1, 4, 8}, {133, 0, 1, 2, 2}, {403, 0, 1, 2, 3},
    };

    private static void mastaba(Chunk c, Terrain t) {
        Site s = ground(t, c, PYRAMID_CELL, 0x50595241L, 49, 49, 8, Megaliths.RANK_PYRAMID);
        if (s == null) return;
        Random r = new Random(s.seed);
        int mx = s.x + 24, mz = s.z + 24, y0 = s.y;
        // (Audit 2026-09-23: quartz only for the two courses that show as a plinth; below them, where a
        // cave beside the site used to open onto sheer white walls, the footing is stone.)
        for (int dx = 0; dx <= 48; dx++) for (int dz = 0; dz <= 48; dz++)
            for (int y = y0 - 1; y >= s.lo - 6; y--) put(c, s.x + dx, y, s.z + dz, y >= y0 - 2 ? 155 : 1, 0);
        // The mass: stepped, with the seams between steps lit.
        // (Audit: solid. Only the outer ring of each course and every fourth course were laid, so the
        // pyramid was a skin over stacked voids and the galleries, passages and shaft cut "into" it had no
        // walls, floors or ceilings; whatever terrain and trees stood there stayed inside.)
        for (int dy = 0; dy <= 24; dy++) {
            int half = 24 - dy;
            boolean step = (dy % 4 == 0);
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                boolean face = Math.abs(dx) == half || Math.abs(dz) == half;
                int id = 251, data = (dy % 4 == 3) ? 8 : 0;
                if (step && face) { id = 169; data = 0; }
                put(c, mx + dx, y0 + dy, mz + dz, id, data);
            }
        }
        for (int dy = 25; dy <= 29; dy++) {
            int half = 29 - dy;
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++)
                put(c, mx + dx, y0 + dy, mz + dz, 95, 15);
        }
        // The hall under the cap, hollowed out of the top eight courses.
        fill(c, mx - 7, y0 + 17, mz - 7, mx + 7, y0 + 24, mz + 7, 0, 0);
        fill(c, mx - 7, y0 + 16, mz - 7, mx + 7, y0 + 16, mz + 7, 155, 0);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 5), pz = mz + (int) Math.round(Math.sin(ang) * 5);
            fill(c, px, y0 + 17, pz, px, y0 + 23, pz, 155, 2);
            put(c, px, y0 + 24, pz, 169, 0);
        }
        // Cap core: yellow concrete, was gold. (Audit: it hangs from the glass cap now; standing on the hall
        // floor it sat over the shaft, which cut its bottom course away and left it floating over the hole.)
        fill(c, mx - 1, y0 + 22, mz - 1, mx + 1, y0 + 24, mz + 1, 251, 4);
        graded(c, s, mx + 6, y0 + 17, mz + 6, r, PYRAMID_LOOT, true);
        // Three ring galleries, and the shaft that threads them.
        for (int g = 0; g < 3; g++) {
            int gy = y0 + 3 + g * 4, ring = 18 - g * 4;
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d > ring || d < ring - 3) continue;
                fill(c, mx + dx, gy, mz + dz, mx + dx, gy + 2, mz + dz, 0, 0);
                put(c, mx + dx, gy - 1, mz + dz, 251, 8);
            }
            for (int a = 0; a < 4; a++) {
                int sx = (a & 1) == 0 ? ring - 1 : -(ring - 1);
                int sz = (a & 2) == 0 ? ring - 1 : -(ring - 1);
                put(c, mx + sx, gy + 2, mz + sz, 169, 0);
                graded(c, s, mx + sx, gy, mz + sz, r, PYRAMID_LOOT, g == 2);
                // (audit: at the east and west midpoints -- at the opposite corners, where they were, the
                // chests of the later corners replaced them)
                if (a < 2) mob(c, mx + sx, gy, mz, g == 0 ? EntityType.SILVERFISH
                        : (g == 1 ? EntityType.HUSK : EntityType.BLAZE));
            }
            fill(c, mx - 1, gy, mz - ring + 1, mx + 1, gy + 2, mz - 2, 0, 0);
            // Lights in the gallery ceiling between the corners and a lamp in the shaft wall (audit: "the
            // lights on inside" had four corner lamps per ring). The spawners' midpoints stay dim.
            int h = ring / 2, e = ring - 2;
            int[][] lamps = {{-h, -e}, {h, -e}, {-h, e}, {h, e}, {-e, -h}, {-e, h}, {e, -h}, {e, h}};
            for (int[] l : lamps) put(c, mx + l[0], gy + 3, mz + l[1], 169, 0);
            put(c, mx + 2, gy + 1, mz, 169, 0);
        }
        fill(c, mx - 1, y0 + 3, mz - 1, mx + 1, y0 + 17, mz + 1, 0, 0);
        // (audit: a landing at each gallery, round the ladder, so the passages can be stepped into from it)
        for (int g = 1; g < 3; g++) fill(c, mx - 1, y0 + 2 + g * 4, mz - 1, mx + 1, y0 + 2 + g * 4, mz + 1, 251, 8);
        // (audit: on the south wall, backed by the mass the whole way; on the north wall, where the three
        // passages open, it hung over nothing and started two blocks off the floor)
        fill(c, mx + 1, y0 + 3, mz + 1, mx + 1, y0 + 16, mz + 1, 65, 2);
        fill(c, mx - 2, y0 + 3, mz - 20, mx + 2, y0 + 6, mz - 18, 0, 0);   // the way in
        fill(c, mx - 2, y0 + 3, mz - 24, mx + 2, y0 + 6, mz - 18, 0, 0);
        // The two spawners of the open top terrace stood in full light and could never spawn; they keep
        // their kinds on the south sides of the two upper galleries (audit).
        mob(c, mx, y0 + 7, mz + 13, EntityType.HUSK);
        mob(c, mx, y0 + 11, mz + 9, EntityType.ENDERMAN);
        // THE SURPRISE: the hall that was already here, under the foundation.
        int uy = y0 - 14;
        fill(c, mx - 14, uy, mz - 14, mx + 14, uy + 9, mz + 14, 0, 0);
        shell(c, mx - 15, uy - 1, mz - 15, mx + 15, uy + 10, mz + 15, 24, 0);
        fill(c, mx - 14, uy - 1, mz - 14, mx + 14, uy - 1, mz + 14, 179, 0);
        for (int dx = -12; dx <= 12; dx += 6) for (int dz = -12; dz <= 12; dz += 6) {
            if (dx == 0 && dz == 12) continue;                           // (audit: it stood on the trove)
            fill(c, mx + dx, uy, mz + dz, mx + dx, uy + 9, mz + dz, 155, 2);
            put(c, mx + dx, uy + 9, mz + dz, 251, 4);                 // capital: yellow concrete, was gold block
            if (((dx + dz) / 6 & 1) == 0) put(c, mx + dx + 1, uy + 4, mz + dz, 169, 0);
        }
        fill(c, mx - 3, uy, mz + 10, mx + 3, uy + 1, mz + 13, 251, 4);    // altar: yellow concrete, was gold block
        fill(c, mx - 2, uy + 2, mz + 11, mx + 2, uy + 2, mz + 12, 49, 0);
        // Audit 2026-09-23: a step up onto the altar, chiseled pilasters on the walls facing each row of
        // columns, and the way down -- a ladder on the west face of the middle column, from the floor of the
        // central shaft. (It was cut before the hall was hollowed and roofed, which took it away and capped
        // the stub, so this hall was sealed.)
        fill(c, mx - 1, uy, mz + 9, mx + 1, uy, mz + 9, 128, 2);
        for (int d = -12; d <= 12; d += 6) {
            fill(c, mx + d, uy, mz - 15, mx + d, uy + 9, mz - 15, 24, 1);
            fill(c, mx + d, uy, mz + 15, mx + d, uy + 9, mz + 15, 24, 1);
            fill(c, mx - 15, uy, mz + d, mx - 15, uy + 9, mz + d, 24, 1);
            fill(c, mx + 15, uy, mz + d, mx + 15, uy + 9, mz + d, 24, 1);
        }
        fill(c, mx - 1, uy + 10, mz, mx - 1, y0 + 2, mz, 0, 0);
        fill(c, mx - 1, uy, mz, mx - 1, y0 + 2, mz, 65, 4);
        mob(c, mx, uy, mz + 8, EntityType.BLAZE);
        mob(c, mx - 9, uy, mz - 9, EntityType.ENDERMAN);
        mob(c, mx + 9, uy, mz + 9, EntityType.SILVERFISH);
        mob(c, mx + 9, uy, mz - 9, EntityType.HUSK);
        trove(c, mx, uy + 2, mz + 12, r, PYRAMID_LOOT, PYRAMID_TROVE, 16 + r.nextInt(6));
    }

    // == 47. Rapture =============================================================
    // Towers on the sea bed joined by glass tubes, most of them still holding. Deco
    // lettering on the frontages, neon that has not gone out, and a good deal of the
    // place open to the water where the glass did not hold. The top of the tallest
    // tower is dry and has not been opened.

    private static final int[][] RAPTURE_LOOT = {
        {2266, 0,  5, 1,  1}, {111, 0,  8, 2,  6}, {445, 0, 6, 1, 1}, { 41, 0, 7, 1, 2},
        { 266, 0, 12, 4, 12}, {351, 12, 10, 6, 18}, {264, 0, 9, 1, 3}, {388, 0, 9, 2, 6},
        { 384, 0,  9, 2,  8}, {331, 0, 10, 4, 12}, {340, 0, 9, 2, 6}, {403, 0, 6, 1, 1},
    };
    private static final int[][] RAPTURE_TROVE = {
        { 57, 0, 1, 1, 2}, {169, 0, 1, 6, 10}, {403, 0, 1, 2, 3},
    };

    private static void rapture(Chunk c, Terrain t) {
        Site s = sunken(t, c, RAPTURE_CELL, 0x52415054L, 45, 45, 13, Megaliths.RANK_RAPTURE);
        if (s == null) return;
        Random r = new Random(s.seed);
        int mx = s.x + 22, mz = s.z + 22, y0 = s.y;
        // (Audit 2026-09-23: packed underneath, so a cave in the sea bed no longer opens under a floor.)
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) footing(c, s, mx + dx, mz + dz, 1, 0);
        // The plaza dome at the middle, half its glass gone.
        for (int dy = 0; dy <= 10; dy++) {
            double rr = Math.sqrt(Math.max(0, 1 - Math.pow(dy / 10.5, 2))) * 10.5;
            for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr + 0.5) continue;
                if (d > rr - 1.0) {
                    // Ribs: smooth stone double slab 43:8 (was iron block) between light blue glass.
                    int rib = (dx + dz + dy) % 9 == 0 ? 0 : (((dx + dy) & 3) == 0 ? 43 : 95);
                    put(c, mx + dx, y0 + dy, mz + dz, rib, rib == 43 ? 8 : rib == 95 ? 3 : 0);
                }
                else fill(c, mx + dx, y0 + 1, mz + dz, mx + dx, y0 + dy, mz + dz, 0, 0);
            }
        }
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {   // (audit: seat the crown ring on the
            double d = Math.sqrt(dx * dx + dz * dz);                           // course below; it touched it only
            if (d > 3.0 && d <= 4.4) put(c, mx + dx, y0 + 9, mz + dz, 95, 3);   // at its corners, and floated)
        }
        fill(c, mx - 10, y0, mz - 10, mx + 10, y0, mz + 10, 155, 0);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 7), pz = mz + (int) Math.round(Math.sin(ang) * 7);
            fill(c, px, y0 + 1, pz, px, y0 + 7, pz, 155, 2);
            put(c, px, y0 + 8, pz, 169, 0);
            if ((a & 1) == 0) put(c, px + 1, y0 + 1, pz, 251, 4);      // statue base: yellow concrete, was gold
        }
        fill(c, mx - 3, y0 + 1, mz - 3, mx + 3, y0 + 2, mz + 3, 9, 0);      // what got in
        // (audit: benches round the plaza, against the glass between the columns, facing what got in)
        fill(c, mx - 4, y0 + 1, mz - 8, mx - 2, y0 + 1, mz - 8, 156, 3);
        fill(c, mx + 8, y0 + 1, mz - 4, mx + 8, y0 + 1, mz - 2, 156, 0);
        fill(c, mx + 2, y0 + 1, mz + 8, mx + 4, y0 + 1, mz + 8, 156, 2);
        fill(c, mx - 8, y0 + 1, mz - 4, mx - 8, y0 + 1, mz - 2, 156, 1);
        // (Audit: the chest stood outside the dome under the south-east tube with iron over its lid; it is
        // inside the south wall now. The guardians spawn in the water that got in, not in a dry corner.)
        graded(c, s, mx, y0 + 1, mz + 9, r, RAPTURE_LOOT, false);
        mob(c, mx, y0 + 1, mz, EntityType.GUARDIAN);
        mob(c, mx - 8, y0 + 1, mz + 2, EntityType.ZOMBIE);
        // Four towers on the corners, joined back to the plaza by tubes.
        int[][] towers = {{-15, -15, 22}, {15, -15, 16}, {-15, 15, 14}, {15, 15, 19}};
        for (int i = 0; i < 4; i++) {
            int tx = mx + towers[i][0], tz = mz + towers[i][1], th = towers[i][2];
            int sx = Integer.signum(-towers[i][0]), sz = Integer.signum(-towers[i][1]);
            for (int dy = 0; dy <= th; dy++) {
                int half = setback(th, dy);
                for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                    boolean face = Math.abs(dx) == half || Math.abs(dz) == half;
                    if (!face) { fill(c, tx + dx, y0 + 1, tz + dz, tx + dx, y0 + dy, tz + dz, 0, 0); continue; }
                    boolean band = dy % 5 == 0, win = !band && ((dx + dz + dy) & 3) == 0;
                    // Deco bands: yellow concrete 251:4 (was gold block).
                    put(c, tx + dx, y0 + dy, tz + dz, band ? 251 : (win ? 95 : 155), band ? 4 : (win ? 4 : 0));
                }
            }
            fill(c, tx - 5, y0, tz - 5, tx + 5, y0, tz + 5, 155, 0);
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) footing(c, s, tx + dx, tz + dz, 1, 0);
            // Audit 2026-09-23. The towers were shells: roofless, each narrower tier resting on the edge of the
            // one below, floors that cut a slot through the wall where they reached it, a 3x3 grid of ladders
            // standing in open air with the floor lamps overwritten by it, and bare slab platforms for rooms.
            // Now: a ledge under each setback; full quartz floors inside their own tier's walls with one
            // ladder hole; a quartz spine up the middle carrying the ladder and a lamp on every floor; a roof;
            // and each floor fitted for what it was -- a lobby, a bar, an apartment, an office.
            ledge(c, tx, y0 + th - 9, tz, 4, sx, sz, th - 9 >= 5 && th - 9 <= 7);
            ledge(c, tx, y0 + th - 4, tz, 3, sx, sz, false);
            int top = 0;
            for (int f = 4; f < th - 3; f += 5) {
                int e = setback(th, f) - 1;
                fill(c, tx - e, y0 + f, tz - e, tx + e, y0 + f, tz + e, 155, 0);
                top = f;
                if ((f / 5 + i) % 2 == 0) graded(c, s, tx + 3, y0 + f + 1, tz + 3, r, RAPTURE_LOOT, f > th / 2);
                if ((f / 5 + i) % 3 == 1) mob(c, tx - 3, y0 + f + 1, tz - 3, EntityType.ZOMBIE);
            }
            if (i == 0) top = th - 4;                                   // on up through the vault's floor
            fill(c, tx, y0 + 1, tz, tx, y0 + th - 1, tz, 155, 2);
            fill(c, tx, y0 + 1, tz - 1, tx, y0 + top, tz - 1, 65, 2);
            for (int f = 0; f < th - 3; f += f == 0 ? 4 : 5) {
                put(c, tx, y0 + f + 3, tz + 1, 169, 0);
                furnish(c, tx, y0 + f, tz, setback(th, f + 2) - 1, f == 0 ? 0 : Math.min(3, (f + 1) / 5), sx, sz, f == 4);
            }
            if (i != 0) {
                fill(c, tx - 2, y0 + th, tz - 2, tx + 2, y0 + th, tz + 2, 155, 0);
                put(c, tx, y0 + th + 1, tz, 169, 0);
            }
            // The tube back to the plaza, which is the only dry way across.
            // (Audit: each sphere of the sweep wrote its shell and its bore at once, so each shell filled the
            // last one's bore; the tube was solid glass, sealed at both ends, with a glass ball hanging in the
            // dome. Now all the shells go first and then the bore; it runs level with the first floor, into
            // the tower through its corner but never into its rooms, and only as far as the dome's glass, where
            // a landing takes it down to the plaza. A quartz strip floors it, a lamp hangs in its crown, and a
            // column holds up its middle.)
            int ty = y0 + 6;
            for (int pass = 0; pass < 2; pass++) for (int k = pass == 0 ? 5 : 4; k <= 9; k++) {
                int px = tx + sx * k, pz = tz + sz * k;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    int x = px + dx, y = ty + dy, z = pz + dz;
                    if (d > 2.6 || domed(mx, mz, y0, x, y, z)) continue;
                    int ring = Math.max(Math.abs(x - tx), Math.abs(z - tz)), hf = setback(th, y - y0);
                    if (pass == 0) {
                        if (d <= 1.7 || ring <= hf) continue;
                        // Tube ribs: 43:8 (was iron block) between light blue glass.
                        put(c, x, y, z, (k & 3) == 0 ? 43 : 95, (k & 3) == 0 ? 8 : 3);
                    } else if (d <= 1.7 && ring >= hf) {
                        put(c, x, y, z, 0, 0);
                        if (dy == -1 && ring > hf) put(c, x, y - 1, z, 155, 0);
                    }
                }
            }
            if (i == 2)                                               // the one where the glass let a little in
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
                    int x = tx + sx * 8 + dx, y = ty + dy, z = tz + sz * 8 + dz;
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= 1.7 && !domed(mx, mz, y0, x, y, z)) put(c, x, y, z, 9, 0);
                }
            put(c, tx + sx * 7, ty + 2, tz + sz * 7, 169, 0);
            fill(c, tx + sx * 7, y0, tz + sz * 7, tx + sx * 7, ty - 3, tz + sz * 7, 155, 2);
            footing(c, s, tx + sx * 7, tz + sz * 7, 1, 0);
            // The landing inside the dome, round the plaza column on this diagonal, and a flight down to the
            // plaza floor; the four flights turn the same way round, so no two meet.
            int lx = mx - sx * 5, lz = mz - sz * 5, ux = (sx + sz) / 2, uz = (sz - sx) / 2;
            fill(c, lx - 1, y0 + 1, lz - 1, lx + 1, y0 + 4, lz + 1, 155, 0);
            int sd = ux > 0 ? 1 : ux < 0 ? 0 : uz > 0 ? 3 : 2;
            for (int j = 0; j < 4; j++) {
                int qx = lx + ux * (2 + j), qz = lz + uz * (2 + j);
                if (j < 3) fill(c, qx, y0 + 1, qz, qx, y0 + 3 - j, qz, 155, 0);
                put(c, qx, y0 + 4 - j, qz, 156, sd);
            }
            if (i == 1) {        // (audit: its guardian stood in a dry room; a pool of what got in, sunk in the floor)
                fill(c, tx + 2, y0, tz + 2, tx + 4, y0, tz + 4, 9, 0);
                mob(c, tx + 3, y0, tz + 3, EntityType.GUARDIAN);
            }
        }
        // THE SURPRISE: the top of the tallest tower, which is dry and was never opened.
        // (Audit: the room is cleared inside its walls -- it was cleared through them, which left the chest,
        // the anvil and the spawner on an open roof under a floating ring and lamp -- with a roof and a yellow
        // skylight, the spine's lamp under it, and a cistern in a corner for the elder guardian.)
        int tx = mx - 15, tz = mz - 15, th = 22;
        fill(c, tx - 2, y0 + th - 3, tz - 2, tx + 2, y0 + th - 1, tz + 2, 0, 0);
        fill(c, tx - 3, y0 + th - 4, tz - 3, tx + 3, y0 + th - 4, tz + 3, 251, 4);   // yellow concrete, was gold
        put(c, tx, y0 + th - 4, tz - 1, 65, 2);
        fill(c, tx - 2, y0 + th, tz - 2, tx + 2, y0 + th, tz + 2, 155, 0);
        fill(c, tx - 1, y0 + th, tz - 1, tx + 1, y0 + th, tz + 1, 95, 4);
        fill(c, tx, y0 + th - 3, tz, tx, y0 + th - 2, tz, 155, 2);
        put(c, tx, y0 + th - 1, tz, 169, 0);
        put(c, tx + 2, y0 + th - 3, tz - 2, 145, 0);
        put(c, tx - 1, y0 + th - 3, tz, 155, 0); put(c, tx - 2, y0 + th - 3, tz, 155, 0);
        put(c, tx, y0 + th - 3, tz + 1, 155, 0); put(c, tx, y0 + th - 3, tz + 2, 155, 0);
        fill(c, tx - 2, y0 + th - 3, tz + 1, tx - 1, y0 + th - 3, tz + 2, 9, 0);
        mob(c, tx - 2, y0 + th - 3, tz + 2, EntityType.ELDER_GUARDIAN);
        trove(c, tx + 2, y0 + th - 3, tz + 2, r, RAPTURE_LOOT, RAPTURE_TROVE, 17 + r.nextInt(6));
    }

    // == 48. Columbia ============================================================
    // White stone on enormous plinths with open air between them, bridges from one to
    // the next, gilded statuary, bunting still up for a holiday, a dome with a gold
    // drum, and two mooring masts for something that never came back.

    private static final int[][] COLUMBIA_LOOT = {
        {2259, 0,  5, 1,  1}, {155, 2, 10, 4, 12}, {35, 3, 11, 4, 12}, {266, 0, 12, 4, 12},
        {  41, 0,  7, 1,  2}, {340, 0, 10, 2,  6}, {322,  0,  7, 1,  2}, {388, 0,  9, 2,  6},
        { 384, 0,  8, 2,  6}, {331, 0, 10, 4, 12}, {262,  0, 11, 6, 18}, {403, 0,  5, 1,  1},
    };
    private static final int[][] COLUMBIA_TROVE = {
        { 41, 0, 1, 4, 8}, {322, 0, 1, 3, 3}, {403, 0, 1, 2, 3},
    };

    private static void columbia(Chunk c, Terrain t) {
        Site s = ground(t, c, COLUMBIA_CELL, 0x434F4C55L, 51, 51, 9, Megaliths.RANK_COLUMBIA);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        int[][] plinths = {{10, 10, 9}, {40, 10, 7}, {10, 40, 7}, {40, 40, 8}, {25, 25, 12}};
        // Five plinths with nothing between them but a long way down.
        for (int i = 0; i < 5; i++) {
            int px = x0 + plinths[i][0], pz = z0 + plinths[i][1], rad = plinths[i][2];
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad + 0.4) continue;
                for (int y = y0 + 12; y >= y0 - 10; y--)
                    put(c, px + dx, y, pz + dz, y > y0 + 11 ? 155 : (d > rad - 1.3 ? 24 : 1), 0);
                put(c, px + dx, y0 + 12, pz + dz, d > rad - 1.3 ? 155 : 155, d > rad - 1.3 ? 1 : 0);
                // (audit 2026-09-23: over a ravine or a cave the column is carried on down to rock)
                pier(c, px + dx, y0 - 11, pz + dz, d > rad - 1.3 ? 24 : 1, 0, 48);
            }
            for (int a = 0; a < 12; a++) {
                // (audit: no bunting post on the way up to the drum's door, or in front of the hall's)
                if ((i == 4 && a == 9) || (i == 1 && a == 3)) continue;
                double ang = a * Math.PI / 6;
                int bx = px + (int) Math.round(Math.cos(ang) * (rad - 1));
                int bz = pz + (int) Math.round(Math.sin(ang) * (rad - 1));
                put(c, bx, y0 + 13, bz, 85, 0);
                if ((a & 1) == 0) put(c, bx, y0 + 14, bz, 35, ((a * 3) % 16));
            }
        }
        // Bridges from the middle out to each of the four.
        for (int i = 0; i < 4; i++) {
            int px = x0 + plinths[i][0], pz = z0 + plinths[i][1];
            int sx = Integer.signum(px - (x0 + 25)), sz = Integer.signum(pz - (z0 + 25));
            for (int k = 0; k <= 16; k++) {
                int bx = x0 + 25 + sx * k, bz = z0 + 25 + sz * k;
                int lift = (int) Math.round(Math.sin(k / 16.0 * Math.PI) * 3);
                fill(c, bx - 1, y0 + 12 + lift, bz - 1, bx + 1, y0 + 12 + lift, bz + 1, 155, 0);
                put(c, bx - 2, y0 + 13 + lift, bz, 85, 0);
                put(c, bx + 2, y0 + 13 + lift, bz, 85, 0);
                if (k % 5 == 0) { put(c, bx - 2, y0 + 14 + lift, bz, 169, 0); put(c, bx + 2, y0 + 14 + lift, bz, 169, 0); }
            }
        }
        int mx = x0 + 25, mz = z0 + 25;
        // The way up (audit: there was none -- every plinth top stood seven to twelve blocks over the ground,
        // sheer, so nothing here could be reached): a processional stair five wide from the drum's north
        // door down to wherever the ground is, each step footed in sandstone like the plinths, lamps at its head.
        for (int x = mx - 2; x <= mx + 2; x++) for (int j = 0; j <= 13; j++) {
            int z = mz - 13 - j, y = y0 + 12 - j;
            if (!loose(c, x, y, z)) break;
            put(c, x, y, z, 156, 2);
            pier(c, x, y - 1, z, 24, 0, 24);
        }
        for (int side = -1; side <= 1; side += 2) {
            put(c, mx + 3 * side, y0 + 13, mz - 12, 155, 1);
            put(c, mx + 3 * side, y0 + 14, mz - 12, 85, 0);
            put(c, mx + 3 * side, y0 + 15, mz - 12, 169, 0);
        }
        // The dome on the centre plinth, with a gold drum under it.
        shell(c, mx - 9, y0 + 13, mz - 9, mx + 9, y0 + 22, mz + 9, 155, 0);
        fill(c, mx - 8, y0 + 13, mz - 8, mx + 8, y0 + 21, mz + 8, 0, 0);
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            int px = mx + (int) Math.round(Math.cos(ang) * 8), pz = mz + (int) Math.round(Math.sin(ang) * 8);
            fill(c, px, y0 + 13, pz, px, y0 + 21, pz, 155, 2);
            if ((a & 1) == 0) put(c, px, y0 + 21, pz, 169, 0);         // (audit: the rotunda had no light)
        }
        for (int w = -2; w <= 2; w += 4) {                             // (audit: and no windows)
            fill(c, mx - 9, y0 + 17, mz + w, mx - 9, y0 + 19, mz + w, 102, 0);
            fill(c, mx + 9, y0 + 17, mz + w, mx + 9, y0 + 19, mz + w, 102, 0);
            fill(c, mx + w, y0 + 17, mz - 9, mx + w, y0 + 19, mz - 9, 102, 0);
            fill(c, mx + w, y0 + 17, mz + 9, mx + w, y0 + 19, mz + 9, 102, 0);
        }
        // Gilding throughout is yellow concrete 251:4 (was gold block); the masts are 43:8 (was iron).
        fill(c, mx - 9, y0 + 23, mz - 9, mx + 9, y0 + 23, mz + 9, 251, 4);
        for (int dy = 0; dy <= 9; dy++) {
            double rr = Math.sqrt(Math.max(0, 1 - Math.pow(dy / 9.5, 2))) * 9.2;
            for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr + 0.5 || d < rr - 1.0) continue;
                put(c, mx + dx, y0 + 24 + dy, mz + dz, (dy & 1) == 0 ? 155 : 251, (dy & 1) == 0 ? 0 : 4);
            }
        }
        fill(c, mx - 1, y0 + 33, mz - 1, mx + 1, y0 + 37, mz + 1, 251, 4);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {   // (audit: seat the lantern's ring)
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2.5 && d < 4.0) put(c, mx + dx, y0 + 32, mz + dz, 155, 0);
        }
        // (Audit: the dais, the statues, their spawners and the corner chest all stood a block too high over
        // an air gap, with the dais top out of reach; a step goes up its north face.)
        fill(c, mx - 3, y0 + 13, mz - 3, mx + 3, y0 + 14, mz + 3, 251, 4);
        fill(c, mx - 1, y0 + 13, mz - 4, mx + 1, y0 + 13, mz - 4, 156, 2);
        for (int sx = -3; sx <= 3; sx += 6) for (int sz = -3; sz <= 3; sz += 6)   // lamps set in its corners
            put(c, mx + sx, y0 + 14, mz + sz, 169, 0);
        for (int a = 0; a < 4; a++) {
            double ang = a * Math.PI / 2 + Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 6), pz = mz + (int) Math.round(Math.sin(ang) * 6);
            fill(c, px, y0 + 13, pz, px, y0 + 16, pz, 251, 4);
            put(c, px, y0 + 17, pz, 155, 1);
            mob(c, px, y0 + 13, pz + 1, a == 0 ? EntityType.ILLUSIONER : EntityType.SKELETON);
        }
        fill(c, mx - 2, y0 + 13, mz - 9, mx + 2, y0 + 16, mz - 9, 0, 0);
        graded(c, s, mx, y0 + 15, mz, r, COLUMBIA_LOOT, true);
        graded(c, s, mx + 7, y0 + 13, mz + 4, r, COLUMBIA_LOOT, false);   // (was in the corner a landing takes)
        // The bridges' ends (audit: the drum had been stamped over them, so all four ran into blank corners
        // and the plinths were five islands): a portal through each corner at deck level, a landing inside
        // and two steps down the wall to the rotunda floor. The corners also stood a cell past the plinth's
        // round edge over nothing; each has a pier under it now.
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            int cx = mx + 9 * sx, cz = mz + 9 * sz;
            fill(c, cx, y0 + 16, cz, cx, y0 + 18, cz, 0, 0);
            fill(c, cx - sx, y0 + 16, cz, cx - sx, y0 + 18, cz, 0, 0);
            fill(c, cx, y0 + 16, cz - sz, cx, y0 + 18, cz - sz, 0, 0);
            put(c, cx, y0 + 19, cz, 155, 1); put(c, cx - sx, y0 + 19, cz, 155, 1); put(c, cx, y0 + 19, cz - sz, 155, 1);
            fill(c, mx + 7 * sx, y0 + 13, mz + 7 * sz, mx + 8 * sx, y0 + 15, mz + 8 * sz, 155, 0);
            put(c, mx + 6 * sx, y0 + 13, mz + 8 * sz, 155, 0);
            put(c, mx + 6 * sx, y0 + 14, mz + 8 * sz, 156, sx < 0 ? 1 : 0);
            put(c, mx + 5 * sx, y0 + 13, mz + 8 * sz, 156, sx < 0 ? 1 : 0);
            fill(c, cx, y0 - 10, cz, cx, y0 + 11, cz, 24, 0);
            put(c, cx, y0 + 12, cz, 155, 1);
            pier(c, cx, y0 - 11, cz, 24, 0, 48);
        }
        // The other four plinths: a bandstand, a hall, a park, and the masts.
        // The bandstand (audit: its columns were a floor block and a capital with air between, so its roof
        // floated, low enough to block the bridge that lands in it, and nothing in it said bandstand): a
        // round stage the bridge lands on, seven whole columns -- the eighth stood on the bridge, which is the
        // way in -- a round roof high enough to walk under, instruments, music stands, and benches facing it.
        int bx = x0 + 10, bz = z0 + 10;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 4.4) put(c, bx + dx, y0 + 13, bz + dz, 155, 0);
            if (d <= 5.4) put(c, bx + dx, y0 + 18, bz + dz, 44, 7);
        }
        graded(c, s, bx - 3, y0 + 14, bz, r, COLUMBIA_LOOT, false);
        mob(c, bx, y0 + 14, bz - 3, EntityType.ZOMBIE);
        for (int a = 0; a < 8; a++) {
            if (a == 1) continue;
            double ang = a * Math.PI / 4;
            int px = bx + (int) Math.round(Math.cos(ang) * 5), pz = bz + (int) Math.round(Math.sin(ang) * 5);
            fill(c, px, y0 + 13, pz, px, y0 + 17, pz, 155, 2);
        }
        put(c, bx - 2, y0 + 14, bz - 2, 84, 0);
        put(c, bx - 3, y0 + 14, bz - 2, 25, 0); put(c, bx - 2, y0 + 14, bz - 3, 25, 0);
        put(c, bx + 1, y0 + 14, bz - 2, 85, 0); put(c, bx + 1, y0 + 15, bz - 2, 72, 0);
        put(c, bx - 2, y0 + 14, bz + 1, 85, 0); put(c, bx - 2, y0 + 15, bz + 1, 72, 0);
        for (int k = -1; k <= 1; k++) {
            put(c, bx + 7, y0 + 13, bz + k, 156, 0);
            put(c, bx - 7, y0 + 13, bz + k, 156, 1);
            put(c, bx + k, y0 + 13, bz + 7, 156, 2);
            put(c, bx + k, y0 + 13, bz - 7, 156, 3);
        }
        shell(c, x0 + 34, y0 + 13, z0 + 5, x0 + 46, y0 + 19, z0 + 15, 155, 0);
        fill(c, x0 + 35, y0 + 13, z0 + 6, x0 + 45, y0 + 18, z0 + 14, 0, 0);
        fill(c, x0 + 39, y0 + 13, z0 + 15, x0 + 41, y0 + 16, z0 + 15, 0, 0);
        fill(c, x0 + 35, y0 + 20, z0 + 6, x0 + 45, y0 + 20, z0 + 14, 251, 4);
        put(c, x0 + 40, y0 + 18, z0 + 10, 169, 0);
        // The hall (audit: a windowless empty box): a civic hall -- a carpeted aisle to a rostrum with a
        // lectern, the founder gilded behind it, pews, tall windows down both sides, and two more lamps.
        put(c, x0 + 37, y0 + 18, z0 + 10, 169, 0); put(c, x0 + 43, y0 + 18, z0 + 10, 169, 0);
        for (int z = z0 + 8; z <= z0 + 11; z += 3) {
            fill(c, x0 + 34, y0 + 14, z, x0 + 34, y0 + 16, z + 1, 102, 0);
            fill(c, x0 + 46, y0 + 14, z, x0 + 46, y0 + 16, z + 1, 102, 0);
        }
        fill(c, x0 + 39, y0 + 13, z0 + 9, x0 + 41, y0 + 13, z0 + 14, 171, 14);
        fill(c, x0 + 37, y0 + 13, z0 + 6, x0 + 43, y0 + 13, z0 + 7, 155, 0);
        fill(c, x0 + 39, y0 + 13, z0 + 8, x0 + 41, y0 + 13, z0 + 8, 156, 3);
        put(c, x0 + 40, y0 + 14, z0 + 7, 47, 0);
        fill(c, x0 + 40, y0 + 14, z0 + 6, x0 + 40, y0 + 16, z0 + 6, 251, 4);
        put(c, x0 + 40, y0 + 17, z0 + 6, 155, 1);
        put(c, x0 + 38, y0 + 14, z0 + 6, 140, 0); put(c, x0 + 42, y0 + 14, z0 + 6, 140, 0);
        for (int z = z0 + 10; z <= z0 + 12; z += 2) {
            fill(c, x0 + 36, y0 + 13, z, x0 + 38, y0 + 13, z, 156, 2);
            fill(c, x0 + 42, y0 + 13, z, x0 + 44, y0 + 13, z, 156, 2);
        }
        graded(c, s, x0 + 44, y0 + 13, z0 + 13, r, COLUMBIA_LOOT, true);
        mob(c, x0 + 36, y0 + 13, z0 + 7, EntityType.VEX);                 // (was in the middle of the aisle)
        // The NE bridge (audit: the hall took all but a stub of it, wedged between the drum and the hall):
        // three steps down off its end to the plinth in front of the hall door.
        put(c, x0 + 36, y0 + 16, z0 + 16, 0, 0);
        put(c, x0 + 36, y0 + 15, z0 + 16, 156, 1);
        put(c, x0 + 37, y0 + 14, z0 + 16, 156, 1);
        put(c, x0 + 38, y0 + 13, z0 + 16, 156, 1);
        fill(c, x0 + 36, y0 + 13, z0 + 16, x0 + 36, y0 + 14, z0 + 16, 155, 0);
        put(c, x0 + 37, y0 + 13, z0 + 16, 155, 0);
        for (int i = 0; i < 2; i++) {
            int px = x0 + 40, pz = z0 + 36 + i * 8;
            fill(c, px, y0 + 13, pz, px, y0 + 30, pz, 43, 8);
            for (int a = 0; a < 4; a++) {
                double ang = a * Math.PI / 2;
                fill(c, px, y0 + 30, pz, px + (int) Math.round(Math.cos(ang) * 3), y0 + 28,
                     pz + (int) Math.round(Math.sin(ang) * 3), 101, 0);
            }
            put(c, px, y0 + 31, pz, 169, 0);
        }
        // The park (audit: never built -- a bare disc with a chest and a spawner in the bridge's landing):
        // two lawns either side of the path the bridge lands on, kerbed in chiseled quartz, a tree in each,
        // flowers, a little basin, and two lamps like the bridges'. The chest and the spawner are in the lawns.
        int qx = x0 + 10, qz = z0 + 40;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > 5.6) continue;
            int band = Math.abs(dx + dz);
            if (band <= 1) continue;
            if (band == 2) { put(c, qx + dx, y0 + 12, qz + dz, 155, 1); continue; }
            put(c, qx + dx, y0 + 12, qz + dz, 2, 0);
            put(c, qx + dx, y0 + 11, qz + dz, 3, 0);
            int p = pick(s, qx + dx, 11, qz + dz, 5);
            if (p == 0) put(c, qx + dx, y0 + 13, qz + dz, 38, pick(s, qx + dx, 12, qz + dz, 9));
            else if (p == 1) put(c, qx + dx, y0 + 13, qz + dz, 37, 0);
        }
        tree(c, qx - 3, y0 + 13, qz - 2);
        tree(c, qx + 3, y0 + 13, qz + 2);
        put(c, qx + 4, y0 + 12, qz - 1, 9, 0);
        int[][] rim = {{5, -1}, {3, -1}, {4, 0}, {4, -2}};
        for (int[] b : rim) { put(c, qx + b[0], y0 + 12, qz + b[1], 155, 1); put(c, qx + b[0], y0 + 13, qz + b[1], 0, 0); }
        put(c, qx + 4, y0 + 13, qz - 1, 0, 0);
        for (int[] l : new int[][]{{-2, 4}, {2, -4}}) {
            put(c, qx + l[0], y0 + 13, qz + l[1], 85, 0);
            put(c, qx + l[0], y0 + 14, qz + l[1], 169, 0);
        }
        graded(c, s, qx - 2, y0 + 13, qz - 4, r, COLUMBIA_LOOT, false);
        mob(c, qx + 4, y0 + 13, qz + 3, EntityType.SKELETON);
        // THE SURPRISE: the gallery inside the drum, reached only from the dome's lantern.
        // (Audit: cleared inside the dome's rings only -- the square used to cut through them at the corners.)
        for (int dy = 1; dy <= 4; dy++) {
            double rr = Math.sqrt(Math.max(0, 1 - Math.pow(dy / 9.5, 2))) * 9.2;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
                if (Math.sqrt(dx * dx + dz * dz) < rr - 1.0) put(c, mx + dx, y0 + 24 + dy, mz + dz, 0, 0);
        }
        fill(c, mx - 6, y0 + 24, mz - 6, mx + 6, y0 + 24, mz + 6, 251, 4);
        fill(c, mx - 1, y0 + 22, mz - 1, mx + 1, y0 + 24, mz + 1, 0, 0);
        // (audit: the ladder hung in the rotunda's air for six rungs; a column stands behind it now)
        fill(c, mx - 2, y0 + 15, mz - 1, mx - 2, y0 + 21, mz - 1, 155, 2);
        fill(c, mx - 1, y0 + 15, mz - 1, mx - 1, y0 + 24, mz - 1, 65, 5);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 5), pz = mz + (int) Math.round(Math.sin(ang) * 5);
            fill(c, px, y0 + 25, pz, px, y0 + 28, pz, 155, 2);
            if ((a & 1) == 0) put(c, px, y0 + 27, pz, 169, 0);
        }
        mob(c, mx + 4, y0 + 25, mz, EntityType.ILLUSIONER);
        mob(c, mx - 4, y0 + 25, mz, EntityType.VEX);
        trove(c, mx, y0 + 25, mz + 4, r, COLUMBIA_LOOT, COLUMBIA_TROVE, 16 + r.nextInt(6));
        // (Audit: rail posts and lamps that stood over a lower step of their bridge are carried down to it.)
        for (int i = 0; i < 4; i++) {
            int sx = Integer.signum(plinths[i][0] - 25), sz = Integer.signum(plinths[i][1] - 25);
            for (int k = 0; k <= 16; k++) {
                int lift = (int) Math.round(Math.sin(k / 16.0 * Math.PI) * 3);
                settle(c, mx + sx * k - 2, y0 + 13 + lift, mz + sz * k);
                settle(c, mx + sx * k + 2, y0 + 13 + lift, mz + sz * k);
            }
        }
    }

    // == 49. The Lagoon ==========================================================
    // Three flume towers, a wave pool with about four feet of standing green in it, a
    // lazy river that still goes round, changing huts, and a ticket arch with the prices
    // on it. The pump house under the wave pool is dry and locked.

    private static final int[][] LAGOON_LOOT = {
        { 444, 0,  6, 1,  1}, {2267, 0,  5, 1,  1}, {171, 3, 11, 4, 12}, {297, 0, 11, 2,  6},
        {351, 12, 11, 6, 18}, { 263, 0, 11, 6, 18}, {265, 0, 10, 3,  9}, {266, 0,  9, 2,  6},
        { 331, 0,  9, 4, 12}, { 367, 0, 11, 4, 12}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] LAGOON_TROVE = {
        { 57, 0, 1, 1, 1}, {322, 0, 1, 2, 2}, {384, 0, 1, 8, 12},
    };

    private static void lagoon(Chunk c, Terrain t) {
        Site s = ground(t, c, LAGOON_CELL, 0x57415452L, 45, 41, 7, Megaliths.RANK_WATERPARK);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        for (int dx = 0; dx <= 44; dx++) for (int dz = 0; dz <= 40; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x0 + 44, y0, z0 + 40, 155, 0);
        // The wave pool, with what is in it now.
        fill(c, x0 + 14, y0 - 3, z0 + 20, x0 + 34, y0, z0 + 36, 0, 0);
        shell(c, x0 + 13, y0 - 4, z0 + 19, x0 + 35, y0 + 1, z0 + 37, 95, 5);
        fill(c, x0 + 14, y0 - 4, z0 + 20, x0 + 34, y0 - 4, z0 + 36, 251, 3);
        fill(c, x0 + 14, y0 - 3, z0 + 20, x0 + 34, y0 - 2, z0 + 36, 9, 0);
        fill(c, x0 + 14, y0 + 1, z0 + 20, x0 + 34, y0 + 1, z0 + 36, 0, 0);
        for (int i = 0; i < 22; i++) put(c, x0 + 15 + r.nextInt(19), y0 - 1, z0 + 21 + r.nextInt(15), 111, 0);
        mob(c, x0 + 20, y0 - 2, z0 + 26, EntityType.SLIME);
        mob(c, x0 + 30, y0 - 2, z0 + 32, EntityType.SLIME);
        // Three flume towers, with the flumes coming off them.
        int[][] flumes = {{x0 + 6, z0 + 8, 16, 5}, {x0 + 22, z0 + 6, 20, 11}, {x0 + 38, z0 + 10, 14, 14}};
        for (int i = 0; i < 3; i++) {
            int tx = flumes[i][0], tz = flumes[i][1], th = flumes[i][2], col = flumes[i][3];
            shell(c, tx - 2, y0 + 1, tz - 2, tx + 2, y0 + th, tz + 2, 155, 0);
            fill(c, tx - 1, y0 + 1, tz - 1, tx + 1, y0 + th - 1, tz + 1, 0, 0);
            fill(c, tx - 1, y0 + 1, tz - 1, tx - 1, y0 + th - 2, tz - 1, 65, 3);
            fill(c, tx - 2, y0 + th + 1, tz - 2, tx + 2, y0 + th + 1, tz + 2, 44, 7);
            fill(c, tx + 2, y0 + th - 3, tz - 1, tx + 2, y0 + th - 2, tz + 1, 0, 0);
            // Audit 2026-09-23: the towers had no door, and their ladders ended in the air of the shaft beside
            // a floating spawner, a floating lamp and a chest with the roof on its lid. Now: a door with a hood,
            // a landing at the top of the ladder with the chest and the spawner on it, a lamp in the roof, and
            // a glass guard across the lower half of the east opening.
            fill(c, tx, y0 + 1, tz - 2, tx, y0 + 2, tz - 2, 0, 0);
            put(c, tx, y0 + 3, tz - 3, 44, 15);
            fill(c, tx - 1, y0 + th - 4, tz - 1, tx + 1, y0 + th - 4, tz + 1, 155, 0);
            put(c, tx - 1, y0 + th - 4, tz - 1, 65, 3);
            fill(c, tx + 2, y0 + th - 3, tz - 1, tx + 2, y0 + th - 3, tz + 1, 160, 3);
            put(c, tx, y0 + th, tz, 169, 0);
            // The flume itself. (Audit: each step of the sweep wrote its shell and its bore at once, so each
            // shell filled the bore before it: a chain of sealed bubbles, dry, with glass left hanging at the
            // ends. Now all the shells go first and then the bore, kept out of the tower but through its wall at
            // the mouth, over half steps as well as whole ones and a little thicker (bore 1.3, glass to 2.5, was
            // 1.5 and 2.4) so the glass is one skin and not a scatter of pieces; it is wet every fourth step and
            // open at its foot; and the first flume, which ran out of the
            // park and was cut off at its edge, keeps inside it.)
            int last = 0;
            while (last < 26 && flumeAt(last + 1, tx, tz, th, x0, y0)[1] > y0 + 1) last++;
            for (int pass = 0; pass < 2; pass++)
                for (int h = 0; h <= 2 * (pass == 0 ? last : last + 2); h++) {        // whole steps and half steps
                    int k = h / 2;
                    int[] p = flumeAt(k, tx, tz, th, x0, y0), q = flumeAt((h + 1) / 2, tx, tz, th, x0, y0);
                    double cx = (p[0] + q[0]) / 2.0, cy = (p[1] + q[1]) / 2.0, cz = (p[2] + q[2]) / 2.0;
                    for (int x = (int) Math.floor(cx - 2.5); x <= (int) Math.ceil(cx + 2.5); x++)
                    for (int y = (int) Math.floor(cy - 2.5); y <= (int) Math.ceil(cy + 2.5); y++)
                    for (int z = (int) Math.floor(cz - 2.5); z <= (int) Math.ceil(cz + 2.5); z++) {
                        double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
                        int ring = Math.max(Math.abs(x - tx), Math.abs(z - tz));
                        if (d > 2.5 || y <= y0) continue;
                        if (pass == 0) {
                            if (d <= 1.3 || (ring <= 2 && y <= y0 + th + 1)) continue;
                            put(c, x, y, z, (k & 3) == 0 ? 251 : 95, col);
                        } else if (d <= 1.3 && ring >= 2) put(c, x, y, z, 0, 0);
                    }
                    if (pass == 1 && h % 8 == 0 && k <= last) put(c, p[0], p[1] - 1, p[2], 9, 0);
                }
            put(c, tx, y0 + th - 4, tz + 2, 155, 0);                  // the sill of the mouth
            fill(c, tx, y0 + th - 3, tz + 2, tx, y0 + th - 2, tz + 2, 0, 0);   // and the mouth, a door's size
            graded(c, s, tx, y0 + th - 3, tz - 1, r, LAGOON_LOOT, i == 1);
            mob(c, tx + 1, y0 + th - 3, tz - 1, i == 0 ? EntityType.CAVE_SPIDER : EntityType.ZOMBIE);
        }
        // The lazy river, the huts, and the arch you came in through.
        // (Audit: it goes round the wave pool, the towers and the huts -- it was laid on a glass shelf across
        // the pool, breaching its wall, and under two towers and a hut, whose floors it turned to water.)
        for (int a = 0; a < 40; a++) {
            double ang = a * Math.PI / 20;
            int px = x0 + 24 + (int) Math.round(Math.cos(ang) * 17);
            int pz = z0 + 20 + (int) Math.round(Math.sin(ang) * 15);
            for (int x = px - 1; x <= px + 1; x++) for (int z = pz - 1; z <= pz + 1; z++) {
                if (kept(x0, z0, flumes, x, z)) continue;
                put(c, x, y0 - 1, z, 95, 3);
                put(c, x, y0, z, 9, 0);
            }
            if (a % 7 == 0 && !kept(x0, z0, flumes, px, pz)) put(c, px, y0 + 1, pz, 111, 0);
        }
        // (Audit: the first flume runs out on the plaza by the west edge; a splash pool for it to land in.)
        fill(c, x0 + 1, y0 - 1, z0 + 24, x0 + 5, y0 - 1, z0 + 28, 95, 3);
        fill(c, x0 + 1, y0, z0 + 24, x0 + 5, y0, z0 + 28, 9, 0);
        // (Audit: the row of huts starts at the west edge, one against the next -- spaced four apart from
        // x0 + 3 the last two stood in the wave pool's wall and over its water.)
        for (int i = 0; i < 4; i++) {
            int hx = x0 + 1 + i * 3, hz = z0 + 34;
            shell(c, hx, y0 + 1, hz, hx + 2, y0 + 3, hz + 3, 5, 1);
            fill(c, hx + 1, y0 + 1, hz + 1, hx + 1, y0 + 2, hz + 2, 0, 0);
            fill(c, hx + 1, y0 + 1, hz, hx + 1, y0 + 2, hz, 0, 0);
            fill(c, hx, y0 + 4, hz, hx + 2, y0 + 4, hz + 3, 44, 7);
            // (audit: a door on each hut, a bench in the empty one, and hut two's spawner moved to hut three,
            // where it no longer stands in the only way to hut two's chest)
            put(c, hx + 1, y0 + 1, hz, 193, 1);
            put(c, hx + 1, y0 + 2, hz, 193, 8);
            if ((i & 1) == 0) graded(c, s, hx + 1, y0 + 1, hz + 2, r, LAGOON_LOOT, false);
            if (i == 1) put(c, hx + 1, y0 + 1, hz + 2, 126, 1);
            if (i == 3) mob(c, hx + 1, y0 + 1, hz + 2, EntityType.ZOMBIE);
        }
        fill(c, x0 + 18, y0 + 1, z0, x0 + 18, y0 + 6, z0, 251, 3);
        fill(c, x0 + 26, y0 + 1, z0, x0 + 26, y0 + 6, z0, 251, 3);
        fill(c, x0 + 18, y0 + 7, z0, x0 + 26, y0 + 8, z0, 251, 4);
        put(c, x0 + 22, y0 + 9, z0, 169, 0);
        // (Audit: "a ticket arch with the prices on it" had none: the prices on the lintel's outer face, and a
        // ticket booth with a window beside the west post.)
        // (Audit: where the ground outside the arch stands higher than the plaza, a flight of quartz steps
        // comes down from it through the arch, so the way in is a way in and out; read a row outside the
        // footprint, which is in this chunk.)
        for (int x = x0 + 19; x <= x0 + 25; x++) {
            int d = top(c, x, z0 - 1, y0 - 1, y0 + 7) - y0;
            if (d < 2 || d > 5) continue;
            for (int j = 1; j < d; j++) {
                int z = z0 + d - 1 - j;
                if (j > 1) fill(c, x, y0 + 1, z, x, y0 + j - 1, z, 155, 0);
                put(c, x, y0 + j, z, 156, 3);
            }
        }
        sign(c, x0 + 21, y0 + 7, z0 - 1, 2, "THE LAGOON", "", "OPEN DAILY", "");
        sign(c, x0 + 22, y0 + 7, z0 - 1, 2, "ADMISSION", "ADULTS  25c", "CHILDREN 10c", "");
        sign(c, x0 + 23, y0 + 7, z0 - 1, 2, "NO REFUNDS", "", "SWIM AT YOUR", "OWN RISK");
        shell(c, x0 + 15, y0 + 1, z0 + 1, x0 + 17, y0 + 3, z0 + 3, 5, 1);
        fill(c, x0 + 16, y0 + 1, z0 + 2, x0 + 16, y0 + 2, z0 + 2, 0, 0);
        put(c, x0 + 17, y0 + 2, z0 + 2, 102, 0);
        put(c, x0 + 16, y0 + 1, z0 + 3, 193, 3);
        put(c, x0 + 16, y0 + 2, z0 + 3, 193, 8);
        fill(c, x0 + 15, y0 + 4, z0 + 1, x0 + 17, y0 + 4, z0 + 3, 126, 1);
        // THE SURPRISE: the pump house under the wave pool.
        int py = y0 - 12;
        fill(c, x0 + 22, py, z0 + 22, x0 + 36, py + 5, z0 + 32, 0, 0);
        shell(c, x0 + 21, py - 1, z0 + 21, x0 + 37, py + 6, z0 + 33, 251, 8);
        fill(c, x0 + 22, py - 1, z0 + 22, x0 + 36, py - 1, z0 + 32, 251, 7);
        for (int dx = 24; dx <= 34; dx += 5) {
            fill(c, x0 + dx, py, z0 + 25, x0 + dx + 2, py + 2, z0 + 29, 43, 8);   // pump: 43:8, was iron block
            fill(c, x0 + dx, py + 3, z0 + 26, x0 + dx + 2, py + 3, z0 + 28, 155, 2);
            put(c, x0 + dx + 1, py + 4, z0 + 27, 169, 0);
        }
        fill(c, x0 + 22, py, z0 + 22, x0 + 23, py, z0 + 23, 9, 0);          // the leak (audit: one layer, not two)
        // The way down, cut after the room (audit: cut before, the room's clearing took the lower ladder and
        // its ceiling capped the shaft, so the room was sealed), with its hatch lid standing open over it.
        // (It comes up two blocks further north than it did, clear of the third flume's run-out.)
        fill(c, x0 + 36, py, z0 + 22, x0 + 36, y0, z0 + 22, 0, 0);
        fill(c, x0 + 36, py, z0 + 22, x0 + 36, y0, z0 + 22, 65, 4);
        put(c, x0 + 36, y0 + 1, z0 + 22, 96, 6);
        mob(c, x0 + 26, py, z0 + 23, EntityType.CAVE_SPIDER);
        mob(c, x0 + 33, py, z0 + 31, EntityType.SLIME);
        trove(c, x0 + 23, py, z0 + 31, r, LAGOON_LOOT, LAGOON_TROVE, 14 + r.nextInt(6));
    }

    /** Centre of step k of a Lagoon flume (kept a block and more inside the park's west edge). */
    private static int[] flumeAt(int k, int tx, int tz, int th, int x0, int y0) {
        double ang = k / 26.0 * Math.PI * 1.9;
        return new int[]{Math.max(x0 + 2, tx + (int) Math.round(Math.sin(ang) * (5 + k / 7))), y0 + th - 3 - k / 2,
                         tz + 3 + k / 2 + (int) Math.round((1 - Math.cos(ang)) * 2)};
    }

    /** Lagoon ground the lazy river keeps off: the wave pool, the flume towers and the changing huts. */
    private static boolean kept(int x0, int z0, int[][] flumes, int x, int z) {
        if (x >= x0 + 13 && x <= x0 + 35 && z >= z0 + 19 && z <= z0 + 37) return true;
        for (int[] f : flumes) if (Math.abs(x - f[0]) <= 2 && Math.abs(z - f[1]) <= 2) return true;
        return x >= x0 + 1 && x <= x0 + 12 && z >= z0 + 34 && z <= z0 + 37;
    }

    // == 50. Wonderland ==========================================================
    // A wheel that still turns when the wind gets under it, a carousel with most of its
    // horses, a coaster that stops being a coaster about eighty feet up, a row of stalls
    // with the prizes still hanging, and a haunted house that was the least frightening
    // thing here when the place was open.

    private static final int[][] PARK_LOOT = {
        {2256, 0,  5, 1,  1}, {2261, 0,  5, 1,  1}, {414, 0,  7, 1, 2}, { 35, 4, 11, 4, 12},
        {351, 9, 12, 6, 18}, { 297, 0, 11, 2,  6}, {263, 0, 11, 6, 18}, {265, 0, 10, 3,  9},
        { 266, 0,  9, 2,  6}, { 322, 0,  6, 1,  1}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] PARK_TROVE = {
        {133, 0, 1, 2, 2}, {2256, 0, 1, 1, 1}, {322, 0, 1, 2, 2},
    };

    private static void wonderland(Chunk c, Terrain t) {
        Site s = ground(t, c, PARK_CELL, 0x5041524BL, 55, 47, 8, Megaliths.RANK_THEMEPARK);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y;
        for (int dx = 0; dx <= 54; dx++) for (int dz = 0; dz <= 46; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x0 + 54, y0, z0 + 46, 251, 8);
        for (int dx = 0; dx <= 54; dx += 2) fill(c, x0 + dx, y0, z0 + 20, x0 + dx, y0, z0 + 24, 251, 14);
        // The wheel.
        // (Audit 2026-09-23: the rim was 48 rounded points, which left holes and corner-only joints, and each
        // spoke a diagonal of posts that never touched; the hub was empty. The rim is a closed ring now and
        // the spokes are joined lines out from an axle on the tower.)
        int wx = x0 + 12, wz = z0 + 12, wy = y0 + 13;
        for (int a = 0; a < 12; a++) {
            double ang = a * Math.PI / 6;
            int ex = (int) Math.round(Math.cos(ang) * 11), ey = (int) Math.round(Math.sin(ang) * 11);
            int n = Math.max(Math.abs(ex), Math.abs(ey)) * 2, lx = 0, ly = 0;
            for (int k = 1; k <= n; k++) {
                int qx = (int) Math.round(ex * k / (double) n), qy = (int) Math.round(ey * k / (double) n);
                if (qx != lx && qy != ly) put(c, wx + lx, wy + qy, wz, 101, 0);
                put(c, wx + qx, wy + qy, wz, 101, 0);
                lx = qx; ly = qy;
            }
        }
        for (int a = 1, lx = 11, ly = 0; a <= 180; a++) {
            double ang = a * Math.PI / 90;
            int qx = (int) Math.round(Math.cos(ang) * 11), qy = (int) Math.round(Math.sin(ang) * 11);
            if (qx != lx && qy != ly) put(c, wx + lx, wy + qy, wz, 43, 8);
            put(c, wx + qx, wy + qy, wz, 43, 8);                         // rim: 43:8, was iron block
            lx = qx; ly = qy;
        }
        for (int a = 0; a < 12; a++) {
            double ang = a * Math.PI / 6;
            int px = wx + (int) Math.round(Math.cos(ang) * 11), py = wy + (int) Math.round(Math.sin(ang) * 11);
            put(c, px, py + (py > wy ? -1 : 1), wz, 35, a % 16);
            if (a == 0) put(c, px, py - 1, wz + 1, 169, 0);
        }
        fill(c, wx, wy, wz - 1, wx, wy, wz + 1, 43, 8);                  // the axle
        fill(c, wx - 1, y0 + 1, wz - 1, wx + 1, y0 + 12, wz + 1, 43, 8);   // tower: 43:8, was iron block
        fill(c, wx - 4, y0 + 1, wz - 4, wx + 4, y0 + 1, wz + 4, 251, 7);
        graded(c, s, wx + 3, y0 + 1, wz + 3, r, PARK_LOOT, false);
        // The carousel.
        // (Audit: a block further north on a platform a block smaller, so the midway in front of stalls three
        // and four is open -- the platform ran into their fronts; and the canopy's rings overlap, from the
        // pole tops up, where they used to touch only at their corners and float a block over the poles.)
        int cx = x0 + 40, cz = z0 + 11;
        fill(c, cx - 6, y0 + 1, cz - 6, cx + 6, y0 + 1, cz + 6, 155, 0);
        fill(c, cx, y0 + 1, cz, cx, y0 + 9, cz, 251, 4);            // centre pole: yellow concrete, was gold
        for (int a = 0; a < 12; a++) {
            double ang = a * Math.PI / 6;
            int px = cx + (int) Math.round(Math.cos(ang) * 5), pz = cz + (int) Math.round(Math.sin(ang) * 5);
            fill(c, px, y0 + 2, pz, px, y0 + 6, pz, 251, 4);            // horse pole: yellow concrete, was gold
            if (a % 3 != 1) { put(c, px, y0 + 2, pz + 1, 35, (a * 2) % 16); put(c, px, y0 + 3, pz + 1, 35, 0); }
        }
        for (int dy = 0; dy <= 3; dy++) {
            int ri = 7 - dy * 2;
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > ri + 0.4) continue;
                if (Math.sqrt(dx * dx + dz * dz) < ri - 2.9 && dy < 3) continue;
                put(c, cx + dx, y0 + 7 + dy, cz + dz, ((dx + dz + dy) & 1) == 0 ? 251 : 251,
                    ((dx + dz + dy) & 1) == 0 ? 14 : 0);
            }
        }
        put(c, cx, y0 + 6, cz, 169, 0);
        graded(c, s, cx + 5, y0 + 2, cz - 5, r, PARK_LOOT, false);
        mob(c, cx - 4, y0 + 2, cz + 4, EntityType.ZOMBIE);
        // The coaster, which stops being a coaster about eighty feet up.
        // (Audit: the rails are laid in the track's own direction and slope, and the break reads as one:
        // what came down lies on the plaza under it, with a snapped post; a boarding platform at the low end.)
        for (int k = 0; k <= 40; k++) {
            int px = x0 + 6 + k, py = y0 + 2 + (k < 26 ? k / 2 : Math.max(0, 13 - (k - 26)));
            int pz = z0 + 32 + (int) Math.round(Math.sin(k / 6.0) * 4);
            if (k > 30) continue;
            fill(c, px, py, pz - 1, px, py, pz + 1, 5, 1);
            int next = k + 1 < 26 ? (k + 1) / 2 : Math.max(0, 13 - (k + 1 - 26));
            int prev = k - 1 < 0 ? -1 : k - 1 < 26 ? (k - 1) / 2 : Math.max(0, 13 - (k - 1 - 26));
            int here = py - y0 - 2;
            put(c, px, py + 1, pz, 66, k < 30 && next > here ? 2 : prev > here ? 3 : 1);
            for (int y = py - 1; y >= y0 + 1 && (k % 4 == 0); y--) put(c, px, y, pz, 85, 0);
        }
        int[][] fallen = {{37, 27, 5, 1}, {38, 26, 5, 1}, {40, 26, 5, 1}, {39, 25, 66, 1}, {41, 27, 66, 0}, {39, 28, 134, 5}};
        for (int[] f : fallen) put(c, x0 + f[0], y0 + 1, z0 + f[1], f[2], f[3]);
        fill(c, x0 + 38, y0 + 1, z0 + 28, x0 + 38, y0 + 2, z0 + 28, 85, 0);
        fill(c, x0 + 3, y0 + 1, z0 + 31, x0 + 5, y0 + 2, z0 + 33, 5, 1);
        for (int x = x0 + 3; x <= x0 + 5; x++) { put(c, x, y0 + 3, z0 + 31, 85, 0); put(c, x, y0 + 3, z0 + 33, 85, 0); }
        put(c, x0 + 2, y0 + 1, z0 + 32, 134, 0);
        // (Audit: the rare chest hung six blocks over the end of the track and the spider spawner in mid-air:
        // the chest is on the last plank at the break, and the spiders nest under the track by its posts.)
        graded(c, s, x0 + 36, y0 + 12, z0 + 29, r, PARK_LOOT, true);
        mob(c, x0 + 20, y0 + 1, z0 + 35, EntityType.SPIDER);
        put(c, x0 + 19, y0 + 1, z0 + 36, 30, 0); put(c, x0 + 18, y0 + 3, z0 + 35, 30, 0);
        put(c, x0 + 21, y0 + 2, z0 + 34, 30, 0); put(c, x0 + 22, y0 + 1, z0 + 35, 30, 0);
        // The stalls along the midway, with the prizes still hanging.
        for (int i = 0; i < 5; i++) {
            int sx = x0 + 6 + i * 9, sz = z0 + 20;
            shell(c, sx, y0 + 1, sz, sx + 5, y0 + 4, sz + 4, 5, 1);
            fill(c, sx + 1, y0 + 1, sz + 1, sx + 4, y0 + 3, sz + 3, 0, 0);
            fill(c, sx + 1, y0 + 1, sz, sx + 4, y0 + 2, sz, 0, 0);
            fill(c, sx, y0 + 5, sz - 1, sx + 5, y0 + 5, sz + 5, 35, (i * 3) % 16);
            for (int dx = 1; dx <= 4; dx++) put(c, sx + dx, y0 + 3, sz + 3, 35, (dx * 4 + i) % 16);
            put(c, sx + 2, y0 + 4, sz + 2, 89, 0);
            if ((i & 1) == 0) graded(c, s, sx + 4, y0 + 1, sz + 3, r, PARK_LOOT, false);
            if (i == 3) mob(c, sx + 2, y0 + 1, sz + 2, EntityType.WITCH);
        }
        // THE SURPRISE: the back room of the haunted house.
        int hx = x0 + 38, hz = z0 + 30;
        shell(c, hx, y0 + 1, hz, hx + 14, y0 + 10, hz + 14, 251, 15);
        fill(c, hx + 1, y0 + 1, hz + 1, hx + 13, y0 + 10, hz + 13, 0, 0);       // (audit: open to the roof)
        fill(c, hx + 1, y0 + 5, hz + 1, hx + 13, y0 + 5, hz + 13, 5, 1);
        fill(c, hx + 6, y0 + 1, hz, hx + 8, y0 + 4, hz, 0, 0);
        // Each course of the roof is two blocks deep so it bears on the one below: courses that only met
        // at their edges were pruned as floating clay, all but the lowest two (structure audit 2026-09-22).
        // (2026-09-23: it closes at the ridge, and its gable ends are walled, with a face in the front one.)
        for (int dy = 0; dy <= 7; dy++) {
            fill(c, hx + dy, y0 + 10 + dy, hz, hx + dy, y0 + 11 + dy, hz + 14, 159, 15);
            fill(c, hx + 14 - dy, y0 + 10 + dy, hz, hx + 14 - dy, y0 + 11 + dy, hz + 14, 159, 15);
        }
        for (int dy = 0; dy <= 6; dy++) {
            fill(c, hx + dy + 1, y0 + 11 + dy, hz, hx + 13 - dy, y0 + 11 + dy, hz, 251, 15);
            fill(c, hx + dy + 1, y0 + 11 + dy, hz + 14, hx + 13 - dy, y0 + 11 + dy, hz + 14, 251, 15);
        }
        put(c, hx + 7, y0 + 13, hz, 91, 2);
        int[][] webs = new int[26][];                                 // (audit: never in the floor between)
        for (int i = 0; i < 26; i++) {
            int gx = hx + 1 + r.nextInt(13), gy = y0 + 1 + r.nextInt(8), gz = hz + 1 + r.nextInt(13);
            if (gy != y0 + 5) put(c, gx, gy, gz, 30, 0);
            webs[i] = new int[]{gx, gy, gz};
        }
        fill(c, hx + 2, y0 + 5, hz + 2, hx + 3, y0 + 5, hz + 3, 0, 0);
        fill(c, hx + 2, y0 + 1, hz + 2, hx + 2, y0 + 5, hz + 2, 65, 3);
        fill(c, hx + 2, y0 + 1, hz + 1, hx + 2, y0 + 4, hz + 1, 251, 15);   // (audit: the ladder had nothing behind it)
        put(c, hx + 7, y0 + 4, hz + 7, 89, 0);
        // The ride (audit: the house was an empty dark box): a ghost train's track in at the door, round the
        // house past its scenes and out again -- a caged monster on soul sand, a grave, lanterns, a black
        // wool screen -- and, up the ladder, the ride's loft: the control desk and its dead lamps, prop crates,
        // a broken car, and two red lamps. A porch and a sign board on the front, and barred windows.
        fill(c, hx + 5, y0 + 1, hz + 6, hx + 10, y0 + 3, hz + 6, 35, 15);
        int[][] track = {{7, 0, 0}, {7, 1, 0}, {7, 2, 9}, {8, 2, 1}, {9, 2, 1}, {10, 2, 1}, {11, 2, 7},
            {11, 3, 0}, {11, 4, 0}, {11, 5, 0}, {11, 6, 0}, {11, 7, 0}, {11, 8, 0}, {11, 9, 0}, {11, 10, 0},
            {11, 11, 8}, {10, 11, 1}, {9, 11, 1}, {8, 11, 1}, {7, 11, 1}, {6, 11, 1}, {5, 11, 1}, {4, 11, 9},
            {4, 10, 0}, {4, 9, 0}, {4, 8, 0}, {4, 7, 0}, {4, 6, 0}, {4, 5, 6}, {5, 5, 1}, {6, 5, 8},
            {6, 4, 0}, {6, 3, 0}, {6, 2, 0}, {6, 1, 0}, {6, 0, 0}};
        for (int[] p : track) put(c, hx + p[0], y0 + 1, hz + p[1], 66, p[2]);
        fill(c, hx + 7, y0, hz + 7, hx + 9, y0, hz + 9, 88, 0);
        for (int x = hx + 7; x <= hx + 9; x++) for (int z = hz + 7; z <= hz + 9; z++)
            if (x != hx + 8 || z != hz + 8) fill(c, x, y0 + 1, z, x, y0 + 2, z, 101, 0);
        mob(c, hx + 8, y0 + 1, hz + 8, EntityType.SPIDER);            // (was loose on the floor at hx + 4, hz + 10)
        mob(c, hx + 10, y0 + 1, hz + 4, EntityType.ZOMBIE);
        put(c, hx + 9, y0 + 1, hz + 4, 85, 0); put(c, hx + 9, y0 + 2, hz + 4, 144, 1);
        put(c, hx + 10, y0 + 1, hz + 3, 88, 0);
        put(c, hx + 8, y0 + 1, hz + 3, 91, 2); put(c, hx + 10, y0 + 1, hz + 9, 91, 3);
        put(c, hx + 6, y0 + 1, hz + 10, 91, 0); put(c, hx + 5, y0 + 1, hz + 7, 91, 1);
        put(c, hx + 13, y0 + 2, hz + 7, 96, 6); put(c, hx + 13, y0 + 2, hz + 9, 96, 6);
        fill(c, hx + 4, y0 + 6, hz + 1, hx + 6, y0 + 6, hz + 1, 126, 9);
        put(c, hx + 4, y0 + 7, hz + 1, 69, 5); put(c, hx + 6, y0 + 7, hz + 1, 69, 5);
        fill(c, hx + 4, y0 + 8, hz, hx + 6, y0 + 8, hz, 123, 0);
        fill(c, hx + 11, y0 + 6, hz + 2, hx + 12, y0 + 6, hz + 3, 35, 0);
        put(c, hx + 11, y0 + 7, hz + 2, 35, 15); put(c, hx + 12, y0 + 7, hz + 3, 35, 15);
        put(c, hx + 4, y0 + 6, hz + 10, 126, 1); put(c, hx + 5, y0 + 6, hz + 10, 126, 1);
        put(c, hx + 4, y0 + 6, hz + 11, 134, 2); put(c, hx + 5, y0 + 6, hz + 11, 134, 2);
        put(c, hx + 1, y0 + 7, hz + 6, 76, 1); put(c, hx + 13, y0 + 7, hz + 5, 76, 2);
        fill(c, hx + 5, y0 + 5, hz - 2, hx + 9, y0 + 5, hz - 1, 5, 1);
        fill(c, hx + 5, y0 + 1, hz - 2, hx + 5, y0 + 4, hz - 2, 85, 0);
        fill(c, hx + 9, y0 + 1, hz - 2, hx + 9, y0 + 4, hz - 2, 85, 0);
        for (int x = hx + 5; x <= hx + 9; x++) put(c, x, y0 + 6, hz, 35, (x & 1) == 0 ? 0 : 15);
        fill(c, hx + 3, y0 + 2, hz, hx + 4, y0 + 3, hz, 101, 0);
        fill(c, hx + 10, y0 + 2, hz, hx + 11, y0 + 3, hz, 101, 0);
        put(c, hx + 4, y0 + 1, hz + 1, 35, 15); put(c, hx + 4, y0 + 2, hz + 1, 91, 2);
        put(c, hx + 10, y0 + 1, hz + 1, 35, 15); put(c, hx + 10, y0 + 2, hz + 1, 91, 2);
        shell(c, hx + 8, y0 + 6, hz + 8, hx + 13, y0 + 9, hz + 13, 251, 15);
        fill(c, hx + 9, y0 + 6, hz + 9, hx + 12, y0 + 8, hz + 12, 0, 0);
        fill(c, hx + 9, y0 + 6, hz + 8, hx + 9, y0 + 7, hz + 8, 0, 0);
        fill(c, hx + 9, y0 + 6, hz + 9, hx + 12, y0 + 6, hz + 12, 171, 14);
        put(c, hx + 10, y0 + 8, hz + 10, 89, 0);
        put(c, hx + 12, y0 + 6, hz + 9, 145, 0);
        mob(c, hx + 11, y0 + 6, hz + 11, EntityType.WITCH);
        trove(c, hx + 12, y0 + 6, hz + 12, r, PARK_LOOT, PARK_TROVE, 15 + r.nextInt(6));
        for (int[] w : webs) {                                        // (audit: a web spun on nothing goes)
            if (!solidIs(c, w[0], w[1], w[2], 30)) continue;
            boolean held = false;
            for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                Block b = Megaliths.blockAt(c, w[0] + n[0], w[1] + n[1], w[2] + n[2]);
                held |= b != null && b.getTypeId() != 0 && b.getTypeId() != 30;
            }
            if (!held) put(c, w[0], w[1], w[2], 0, 0);
        }
    }

    // == structure audit 2026-09-23: helpers for the repairs above ================

    /**
     * A per-block choice that does not draw from the builder's Random, so the loot rolled after it stays
     * what it was, and every chunk the structure spans makes the same choice for the same block.
     */
    private static int pick(Site s, int x, int y, int z, int n) {
        long h = Terrain.mix(s.seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L);
        return (int) Math.floorMod(h >>> 1, (long) n);
    }

    private static boolean air(Chunk c, int x, int y, int z) {
        Block b = Megaliths.blockAt(c, x, y, z);
        return b != null && b.getTypeId() == 0;
    }

    private static boolean solidIs(Chunk c, int x, int y, int z, int id) {
        Block b = Megaliths.blockAt(c, x, y, z);
        return b != null && b.getTypeId() == id;
    }

    /** Air, a liquid or a plant: nothing a column can stand on. False outside this chunk. */
    private static boolean loose(Chunk c, int x, int y, int z) {
        Block b = Megaliths.blockAt(c, x, y, z);
        if (b == null) return false;
        int id = b.getTypeId();
        return id == 0 || b.isLiquid() || id == 31 || id == 32 || id == 37 || id == 38 || id == 175 || id == 106 || id == 78;
    }

    /** The highest block in lo..hi of a column that something can stand on (lo - 1 if none), or MIN_VALUE off this chunk. */
    private static int top(Chunk c, int x, int z, int lo, int hi) {
        if (Megaliths.blockAt(c, x, lo, z) == null) return Integer.MIN_VALUE;
        for (int y = hi; y >= lo; y--) if (!loose(c, x, y, z)) return y;
        return lo - 1;
    }

    /** Carries a column of id down from y while it stands over nothing, at most max blocks. */
    private static void pier(Chunk c, int x, int y, int z, int id, int data, int max) {
        for (int i = 0; i < max && y - i >= 1 && loose(c, x, y - i, z); i++) put(c, x, y - i, z, id, data);
    }

    /** A bridge rail post over a lower step: carried down to the deck, or taken away with its lamp if there is none. */
    private static void settle(Chunk c, int x, int y, int z) {
        if (!solidIs(c, x, y, z, 85)) return;
        int below = y - 1;
        while (below > y - 4 && air(c, x, below, z)) below--;
        if (below == y - 1) return;
        if (air(c, x, below, z)) {
            put(c, x, y, z, 0, 0);
            if (solidIs(c, x, y + 1, z, 169)) put(c, x, y + 1, z, 0, 0);
            return;
        }
        fill(c, x, below + 1, z, x, y - 1, z, 85, 0);
    }

    /** A wall sign with its lines (signs, unlike chests, are written through their state). */
    private static void sign(Chunk c, int x, int y, int z, int data, String... lines) {
        Block b = Megaliths.blockAt(c, x, y, z);
        if (b == null) return;
        put(c, x, y, z, 68, data);
        try {
            org.bukkit.block.Sign sg = (org.bukkit.block.Sign) b.getState();
            for (int i = 0; i < lines.length && i < 4; i++) sg.setLine(i, lines[i]);
            sg.update(true, false);
        } catch (RuntimeException ignored) { }
    }

    /** A small park tree: an oak trunk and a crown of leaves that do not decay. */
    private static void tree(Chunk c, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            put(c, x + dx, y + 2, z + dz, 18, 4);
            put(c, x + dx, y + 3, z + dz, 18, 4);
        }
        put(c, x, y + 4, z, 18, 4); put(c, x + 1, y + 4, z, 18, 4); put(c, x - 1, y + 4, z, 18, 4);
        put(c, x, y + 4, z + 1, 18, 4); put(c, x, y + 4, z - 1, 18, 4);
        fill(c, x, y, z, x, y + 2, z, 17, 0);
    }

    /** The Burnt Chancel's soot: black to the waist on its stone brick, concrete and terracotta, some brick left showing. */
    private static void soot(Chunk c, Site s, int x, int y0, int z) {
        int top = y0 + 3 + pick(s, x, 20, z, 2);
        for (int y = y0 + 2; y <= top; y++) {
            if (!solidIs(c, x, y, z, 98)) continue;
            int p = pick(s, x, y, z, 6);
            if (p > 0) put(c, x, y, z, p < 4 ? 251 : 159, 15);
        }
    }

    /** Half-width of a Rapture tower's tier dy courses up: five, then four, then three near the top. */
    private static int setback(int th, int dy) { return dy > th - 4 ? 3 : (dy > th - 9 ? 4 : 5); }

    /** Inside Rapture's plaza dome (not in its glass): tubes and landings leave that alone. */
    private static boolean domed(int mx, int mz, int y0, int x, int y, int z) {
        int dy = y - y0;
        if (dy < 0 || dy > 10) return false;
        double rr = Math.sqrt(Math.max(0, 1 - Math.pow(dy / 10.5, 2))) * 10.5;
        return Math.sqrt((x - mx) * (x - mx) + (z - mz) * (z - mz)) <= rr - 1.0;
    }

    /** A ring of quartz under a Rapture setback, leaving out the corner its tube comes in by when asked. */
    private static void ledge(Chunk c, int tx, int y, int tz, int n, int sx, int sz, boolean door) {
        for (int dx = -n; dx <= n; dx++) for (int dz = -n; dz <= n; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != n) continue;
            if (door && sx * dx >= 3 && sz * dz >= 3) continue;
            put(c, tx + dx, y, tz + dz, 155, 0);
        }
    }

    /**
     * What a Rapture tower floor was for: 0 a lobby (benches, a runner, plants, a puddle of what got in),
     * 1 a bar (a counter with a still on it and neon behind, stools, vending machines), 2 an apartment (a
     * bed, bookshelves, a gramophone, a sink, a rug, a chair), 3 an office (a desk and chair, shelves, a
     * piano). e is the room's half-width and fy its floor. Nothing goes on the spine, the ladder or the
     * cell in front of it, the chest and spawner corners, the corner the tube comes in by, or anything
     * already standing there; the neon and the puddle go only into wall and floor.
     */
    private static void furnish(Chunk c, int tx, int fy, int tz, int e, int kind, int sx, int sz, boolean door) {
        int[][] items;   // {dx, dz, dy, id, data}
        if (kind == 0) items = new int[][]{
            {-e, -1, 0, 156, 1}, {-e, 0, 0, 156, 1}, {-e, 1, 0, 156, 1}, {e, -1, 0, 156, 0}, {e, 0, 0, 156, 0},
            {e, 1, 0, 156, 0}, {0, 1, 0, 171, 14}, {0, 2, 0, 171, 14}, {0, 3, 0, 171, 14}, {0, 4, 0, 171, 14},
            {-e, e, 0, 140, 0}, {e, -e, 0, 140, 0}, {-e, e - 1, -1, 9, 0}};
        else if (kind == 1) items = new int[][]{
            {-e, -1, 0, 44, 15}, {-e, 0, 0, 44, 15}, {-e, 1, 0, 44, 15}, {-e, 0, 1, 117, 0},
            {-e - 1, -1, 2, 95, 4}, {-e - 1, 0, 2, 95, 4}, {-e - 1, 1, 2, 95, 4}, {-e + 2, -1, 0, 44, 7},
            {-e + 2, 1, 0, 44, 7}, {1, -e, 0, 23, 3}, {2, -e, 0, 23, 3}, {1, -e + 1, 0, 77, 3}, {2, -e + 1, 0, 77, 3}};
        else if (kind == 2) items = new int[][]{
            {-e, 2, 0, 26, 9}, {-e + 1, 2, 0, 26, 1}, {e, -2, 0, 47, 0}, {e, -1, 0, 47, 0}, {e, 0, 0, 47, 0},
            {e, -2, 1, 47, 0}, {e, -1, 1, 47, 0}, {e, 0, 1, 47, 0}, {-e, -1, 0, 84, 0}, {e, 2, 0, 118, 0},
            {-1, 2, 0, 171, 15}, {0, 2, 0, 171, 15}, {1, 2, 0, 171, 15}, {-1, 3, 0, 171, 15}, {0, 3, 0, 171, 15},
            {1, 3, 0, 171, 15}, {1, -3, 0, 156, 3}};
        else items = new int[][]{
            {-2, 1, 0, 44, 15}, {-1, 1, 0, 44, 15}, {-1, 2, 0, 156, 2}, {-2, 1, 1, 140, 0},
            {e, -2, 0, 47, 0}, {e, -1, 0, 47, 0}, {e, 0, 0, 47, 0}, {e, 1, 0, 47, 0},
            {e, -2, 1, 47, 0}, {e, -1, 1, 47, 0}, {e, 0, 1, 47, 0}, {e, 1, 1, 47, 0}, {-e, -2, 0, 25, 0}};
        for (int[] it : items) {
            int dx = it[0], dz = it[1], x = tx + dx, y = fy + 1 + it[2], z = tz + dz;
            boolean into = it[3] == 95 || it[3] == 9;
            if (Math.abs(dz) > e || (Math.abs(dx) > e && !into)) continue;
            if (dx == 0 && dz >= -2 && dz <= 0) continue;
            if (Math.abs(dx) == 3 && dx == dz) continue;
            if (door && sx * dx >= 3 && sz * dz >= 3) continue;
            if (Megaliths.blockAt(c, x, y, z) == null || into == air(c, x, y, z)) continue;
            put(c, x, y, z, it[3], it[4]);
        }
    }
}
