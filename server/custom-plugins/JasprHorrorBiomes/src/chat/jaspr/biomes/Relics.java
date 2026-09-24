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
import static chat.jaspr.biomes.Megaliths.trove;

/**
 * Relics: the eight places worth crossing the map for.
 *
 * A station with two cooling towers and a reactor that is not entirely finished, a city
 * with rock where its sky was, something that came down from a long way off, a town built
 * inside the crater of the thing that made it, a walled strip with the lights still on, a
 * vault that was sealed from the outside, and two shops everybody would recognise with
 * the roof gone.
 *
 * Each one hides exactly one trove: a box that is not where the others are, behind a
 * wall, under a floor, or at the top of something. It pays several times what the site's
 * ordinary chests do, carries two or three things that exist nowhere else in the world,
 * and always has a weapon in it. Finding it is the reason to go in rather than past.
 */
public final class Relics {
    private Relics() { }

    private static final int NUKE_CELL = 122, CITY_CELL = 128, ALIEN_CELL = 132;
    private static final int MEGATON_CELL = 93, STRIP_CELL = 116, VAULT_CELL = 112;
    private static final int IKEA_CELL = 100, MCD_CELL = 77;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        nuclear(c, t);
        buriedCity(c, t);
        alienShip(c, t);
        megaton(c, t);
        strip(c, t);
        vault(c, t);
        flatpack(c, t);
        arches(c, t);
    }

    // == 30. Ostrovets Station ===================================================
    // Two cooling towers you can see from a long way off, a turbine hall with three
    // machines still bolted down, a control room with the board dark, a fuel pond that
    // is still lit from underneath, and a containment dome with a core in it that
    // somebody got about eighty per cent of the way through shutting down.

    private static final int[][] NUKE_LOOT = {
        {331, 0, 14, 8, 24}, {348, 0, 12, 4, 12}, { 22, 0,  8, 1,  3}, {327, 0,  6, 1, 2},
        {436, 0,  9,  1, 3}, {265, 0, 12, 4, 12}, { 42, 0,  8, 1,  3}, { 89, 0, 10, 2, 6},
        {264, 0,  7,  1, 2}, {263, 0, 12, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  4, 1, 1},
    };
    private static final int[][] NUKE_TROVE = {
        { 57, 0, 1, 1, 2}, {419, 0, 1, 1, 1}, {384, 0, 1, 12, 20},
    };

    /** One hyperboloid of revolution, which is the only shape a cooling tower is. */
    private static void coolingTower(Chunk c, int mx, int my, int mz, int height) {
        for (int dy = 0; dy <= height; dy++) {
            double waist = height * 0.62;
            double rr = 6.4 + Math.abs(dy - waist) / waist * (dy < waist ? 4.3 : 3.6);
            int ri = (int) Math.round(rr);
            for (int dx = -ri - 1; dx <= ri + 1; dx++) for (int dz = -ri - 1; dz <= ri + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr + 0.5 || d < rr - 0.9) continue;
                put(c, mx + dx, my + dy, mz + dz, 251, (dy % 9 == 0) ? 0 : 8);
            }
        }
        // The pond and the fill inside the base.
        fill(c, mx - 7, my, mz - 7, mx + 7, my, mz + 7, 251, 7);
        fill(c, mx - 5, my, mz - 5, mx + 5, my, mz + 5, 9, 0);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = mx + (int) Math.round(Math.cos(ang) * 7), pz = mz + (int) Math.round(Math.sin(ang) * 7);
            fill(c, px, my + 1, pz, px, my + 6, pz, 43, 8);           // leg: 43:8, was iron block
        }
        // The grating is a ring inside the shell (structure audit 2026-09-23): the square one stuck its
        // corners out through the concrete.
        double r7 = 6.4 + Math.abs(7 - height * 0.62) / (height * 0.62) * 4.3 - 1.0;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 5.5 && d <= r7) put(c, mx + dx, my + 7, mz + dz, 101, 0);
        }
    }

    /** The fuel pond's cells: its box, less the drum and a block of ground round it. */
    private static boolean pond(int x, int z, int x0, int z0, int rx, int rz) {
        if (x < x0 + 40 || x > x0 + 52 || z < z0 + 21 || z > z0 + 29) return false;
        return (x - rx) * (x - rx) + (z - rz) * (z - rz) > 12.5 * 12.5;
    }

    private static void nuclear(Chunk c, Terrain t) {
        Site s = ground(t, c, NUKE_CELL, 0x4E554B45L, 57, 49, 8, Megaliths.RANK_NUCLEAR);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 56, z1 = z0 + 48;
        for (int dx = 0; dx <= 56; dx++) for (int dz = 0; dz <= 48; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);
        // Fence and gate.
        for (int dx = 0; dx <= 56; dx++) for (int dz = 0; dz <= 48; dz++) {
            if (dx != 0 && dx != 56 && dz != 0 && dz != 48) continue;
            if (dz == 48 && dx >= 26 && dx <= 30) continue;
            put(c, x0 + dx, y0 + 1, z0 + dz, 251, 7);
            fill(c, x0 + dx, y0 + 2, z0 + dz, x0 + dx, y0 + 4, z0 + dz, 101, 0);
        }
        // The gate meets the ground outside (structure audit 2026-09-23): a bank or a drop used to stand
        // in front of it.
        apron(c, t, x0 + 26, x0 + 30, z1 + 1, y0, 14, 251, 7, 109, true);
        coolingTower(c, x0 + 11, y0, z0 + 12, 30);
        coolingTower(c, x0 + 11, y0, z0 + 36, 30);
        // Containment: a drum with a dome on it.
        int rx = x0 + 36, rz = z0 + 15;
        for (int dy = 0; dy <= 15; dy++) for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 11.5) continue;
            // Bands every fifth course: smooth stone double slab 43:8 (was iron block).
            if (d > 10.2) put(c, rx + dx, y0 + dy, rz + dz, dy % 5 == 0 ? 43 : 251, dy % 5 == 0 ? 8 : 0);
            // dy 0 cleared nothing on purpose, but fill() swaps a reversed range and took the drum's
            // floor at y0 with it (structure audit 2026-09-23).
            else if (dy > 0) fill(c, rx + dx, y0 + 1, rz + dz, rx + dx, y0 + dy, rz + dz, 0, 0);
        }
        // The dome: each course runs in to the next one's radius so the shell is closed, and the crown
        // is a cap (structure audit 2026-09-23: the thin top rings used to float free of each other).
        for (int dy = 0; dy <= 9; dy++) {
            double rr = Math.sqrt(Math.max(0, 1 - Math.pow(dy / 9.5, 2))) * 11.2;
            double rn = dy == 9 ? -1 : Math.sqrt(Math.max(0, 1 - Math.pow((dy + 1) / 9.5, 2))) * 11.2 - 0.5;
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr + 0.5 || d < Math.min(rn, rr - 1.0)) continue;
                put(c, rx + dx, y0 + 16 + dy, rz + dz, 251, 0);
            }
        }
        fill(c, rx - 1, y0 + 26, rz - 1, rx + 1, y0 + 26, rz + 1, 251, 0);
        // The core: fuel channels in a grid, glass over them, and a long way down beneath.
        fill(c, rx - 5, y0 - 3, rz - 5, rx + 5, y0, rz + 5, 0, 0);
        fill(c, rx - 5, y0 - 4, rz - 5, rx + 5, y0 - 4, rz + 5, 49, 0);
        fill(c, rx - 5, y0 - 3, rz - 5, rx + 5, y0 - 3, rz + 5, 10, 0);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean rod = (dx & 1) == 0 && (dz & 1) == 0;
            // Rods blue concrete, channels red concrete (were lapis / redstone blocks).
            put(c, rx + dx, y0 - 1, rz + dz, 251, rod ? 11 : 14);
            put(c, rx + dx, y0, rz + dz, rod ? 20 : 95, rod ? 0 : 5);
        }
        // The lid is framed to the pit walls (structure audit 2026-09-23): the pit is a block wider
        // than the rods, and the gap round the glass dropped straight into the lava.
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
            if (Math.abs(dx) == 5 || Math.abs(dz) == 5) fill(c, rx + dx, y0 - 1, rz + dz, rx + dx, y0, rz + dz, 251, 7);
        fill(c, rx - 6, y0 + 1, rz - 6, rx + 6, y0 + 1, rz + 6, 44, 7);
        fill(c, rx - 5, y0 + 1, rz - 5, rx + 5, y0 + 1, rz + 5, 0, 0);
        // The crane runs wall to wall on the y0+10 band (it stopped two blocks short of both walls), and
        // the lamp hangs from the crown on a chain (structure audit 2026-09-23).
        fill(c, rx - 1, y0 + 10, rz - 11, rx + 1, y0 + 10, rz + 11, 43, 8);    // the crane (43:8, was iron)
        fill(c, rx, y0 + 2, rz, rx, y0 + 9, rz, 101, 0);
        fill(c, rx - 11, y0 + 1, rz - 1, rx - 11, y0 + 4, rz + 1, 0, 0);       // the airlock
        // The airlock is a lock now, not a hole in the wall (structure audit 2026-09-23): a steel frame
        // with a shut door in the drum wall, a chamber inside it, and the inner door left open the day
        // they walked out. A button either side of the outer door.
        fill(c, rx - 10, y0 + 1, rz - 2, rx - 8, y0 + 5, rz + 2, 251, 7);
        fill(c, rx - 10, y0 + 1, rz - 1, rx - 9, y0 + 4, rz + 1, 0, 0);
        fill(c, rx - 11, y0 + 1, rz - 1, rx - 11, y0 + 4, rz + 1, 43, 8);
        ironDoor(c, rx - 11, y0 + 1, rz, 0, false);
        put(c, rx - 8, y0 + 1, rz, 71, 4);                                      // inner door, open
        put(c, rx - 8, y0 + 2, rz, 71, 8);
        put(c, rx - 12, y0 + 2, rz + 1, 77, 2);
        put(c, rx - 10, y0 + 2, rz + 1, 77, 1);
        put(c, rx - 9, y0 + 5, rz, 169, 0);
        put(c, rx, y0 + 14, rz, 89, 0);
        fill(c, rx, y0 + 15, rz, rx, y0 + 24, rz, 101, 0);
        mob(c, rx + 7, y0 + 1, rz + 7, EntityType.MAGMA_CUBE);
        mob(c, rx - 7, y0 + 1, rz - 7, EntityType.BLAZE);
        graded(c, s, rx + 8, y0 + 1, rz, r, NUKE_LOOT, true);
        // Turbine hall and the control room bolted onto the side of it.
        shell(c, x0 + 26, y0 + 1, z0 + 30, x0 + 54, y0 + 10, z0 + 46, 251, 8);
        fill(c, x0 + 27, y0 + 1, z0 + 31, x0 + 53, y0 + 9, z0 + 45, 0, 0);
        fill(c, x0 + 27, y0 + 10, z0 + 31, x0 + 53, y0 + 10, z0 + 45, 44, 7);
        for (int i = 0; i < 3; i++) {
            int tx = x0 + 29 + i * 8;
            fill(c, tx, y0 + 2, z0 + 34, tx + 5, y0 + 4, z0 + 42, 43, 8);    // turbine: 43:8, was iron block
            fill(c, tx + 1, y0 + 5, z0 + 36, tx + 4, y0 + 5, z0 + 40, 155, 2);
            fill(c, tx, y0 + 1, z0 + 33, tx + 5, y0 + 1, z0 + 43, 251, 7);
            put(c, tx + 2, y0 + 9, z0 + 38, 169, 0);                      // flush under the roof
            // Steam lines from each machine to the roof, on a concrete flange (structure audit
            // 2026-09-23: the hall was three machines on a bare floor).
            fill(c, tx + 1, y0 + 5, z0 + 34, tx + 1, y0 + 8, z0 + 34, 101, 0);
            fill(c, tx + 4, y0 + 5, z0 + 42, tx + 4, y0 + 8, z0 + 42, 101, 0);
            put(c, tx + 1, y0 + 9, z0 + 34, 251, 7);
            put(c, tx + 4, y0 + 9, z0 + 42, 251, 7);
            if (i < 2) fill(c, tx + 6, y0 + 1, z0 + 34, tx + 7, y0 + 2, z0 + 35, 251, 7);   // condenser
            if (i == 1) graded(c, s, tx + 6, y0 + 1, z0 + 38, r, NUKE_LOOT, false);
        }
        mob(c, x0 + 40, y0 + 1, z0 + 32, EntityType.HUSK);
        mob(c, x0 + 48, y0 + 1, z0 + 44, EntityType.HUSK);
        shell(c, x0 + 14, y0 + 1, z0 + 30, x0 + 26, y0 + 7, z0 + 42, 251, 7);
        fill(c, x0 + 15, y0 + 1, z0 + 31, x0 + 25, y0 + 6, z0 + 41, 0, 0);
        // The room is carved through cooling tower 2's basin: it gets its own floor over the water
        // (structure audit 2026-09-23).
        fill(c, x0 + 15, y0, z0 + 31, x0 + 25, y0, z0 + 41, 251, 7);
        fill(c, x0 + 26, y0 + 3, z0 + 33, x0 + 26, y0 + 5, z0 + 39, 95, 0);
        for (int dz = 32; dz <= 40; dz += 2) {
            put(c, x0 + 15, y0 + 1, z0 + dz, 251, 15);                    // the panel stands on the floor
            put(c, x0 + 16, y0 + 1, z0 + dz, 251, 7);                     // desk pedestal
            put(c, x0 + 15, y0 + 2, z0 + dz, 251, 15);
            // Indicator light: sea lantern. A lit redstone lamp (124) with nothing powering it
            // switched itself off the moment it was placed (structure audit 2026-09-22).
            put(c, x0 + 15, y0 + 3, z0 + dz, 169, 0);
            put(c, x0 + 16, y0 + 2, z0 + dz, 44, 7);
        }
        put(c, x0 + 20, y0 + 6, z0 + 36, 89, 0);
        // The operators' seats at the desks, a second console of switches and gauges behind them, and
        // a bank of cabinets on the south wall (structure audit 2026-09-23: one board and nothing else).
        for (int dz = 32; dz <= 40; dz += 4) put(c, x0 + 17, y0 + 1, z0 + dz, 156, 0);
        fill(c, x0 + 22, y0 + 1, z0 + 33, x0 + 22, y0 + 1, z0 + 35, 251, 15);
        put(c, x0 + 22, y0 + 2, z0 + 33, 69, 5);
        put(c, x0 + 22, y0 + 2, z0 + 34, 151, 0);
        put(c, x0 + 22, y0 + 2, z0 + 35, 77, 5);
        fill(c, x0 + 16, y0 + 1, z0 + 41, x0 + 20, y0 + 2, z0 + 41, 251, 7);
        graded(c, s, x0 + 24, y0 + 1, z0 + 32, r, NUKE_LOOT, false);
        mob(c, x0 + 20, y0 + 1, z0 + 38, EntityType.ZOMBIE);
        // Doors (structure audit 2026-09-23): both buildings were closed shells. They open onto the
        // forecourt inside the gate, iron doors with a button either side.
        fill(c, x0 + 26, y0 + 1, z0 + 44, x0 + 26, y0 + 2, z0 + 45, 0, 0);
        ironDoor(c, x0 + 26, y0 + 1, z0 + 44, 0, false);
        ironDoor(c, x0 + 26, y0 + 1, z0 + 45, 0, true);
        put(c, x0 + 25, y0 + 2, z0 + 43, 77, 2);
        put(c, x0 + 27, y0 + 2, z0 + 43, 77, 1);
        ironDoor(c, x0 + 24, y0 + 1, z0 + 42, 3, false);
        put(c, x0 + 23, y0 + 2, z0 + 43, 77, 3);
        put(c, x0 + 23, y0 + 2, z0 + 41, 77, 4);
        // The fuel pond, lit from below because that is how those work. It keeps clear of the drum and
        // stops short of the hall's north wall (structure audit 2026-09-23: it undercut both and took
        // the drum's magma-cube spawner with it).
        for (int px = x0 + 40; px <= x0 + 52; px++) for (int pz = z0 + 21; pz <= z0 + 29; pz++) {
            if (!pond(px, pz, x0, z0, rx, rz)) continue;
            if (!pond(px - 1, pz, x0, z0, rx, rz) || !pond(px + 1, pz, x0, z0, rx, rz)
                    || !pond(px, pz - 1, x0, z0, rx, rz) || !pond(px, pz + 1, x0, z0, rx, rz)) {
                fill(c, px, y0 - 6, pz, px, y0, pz, 251, 0);
                put(c, px, y0 + 1, pz, 101, 0);
                continue;
            }
            put(c, px, y0 - 6, pz, 89, 0);
            fill(c, px, y0 - 5, pz, px, y0, pz, 9, 0);
            put(c, px, y0 + 1, pz, 0, 0);
            int fdx = px - x0, fdz = pz - z0;
            if (fdx >= 42 && fdx <= 50 && (fdx & 1) == 0 && fdz >= 23 && (fdz & 1) == 1)
                fill(c, px, y0 - 5, pz, px, y0 - 3, pz, 251, 5);
        }
        mob(c, x0 + 39, y0 + 1, z0 + 27, EntityType.CREEPER);           // was set into the drum wall
        graded(c, s, x0 + 53, y0 + 1, z0 + 26, r, NUKE_LOOT, false);
        // THE SURPRISE: the core catcher. A shaft under the fuel channels into the basement
        // they built in case the core ever came through the floor, which it has.
        int by = y0 - 16;
        fill(c, rx - 9, by, rz - 9, rx + 9, by + 8, rz + 9, 0, 0);
        shell(c, rx - 10, by - 1, rz - 10, rx + 10, by + 9, rz + 10, 49, 0);
        fill(c, rx - 9, by - 1, rz - 9, rx + 9, by - 1, rz + 9, 251, 15);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 6.2) continue;
            put(c, rx + dx, by - 1, rz + dz, d > 4.4 ? 213 : 10, 0);
            if (d > 4.4 && d < 5.4) put(c, rx + dx, by, rz + dz, 87, 0);
        }
        fill(c, rx - 9, by, rz + 7, rx - 5, by + 4, rz + 9, 251, 7);
        fill(c, rx - 8, by, rz + 8, rx - 6, by + 3, rz + 9, 0, 0);
        put(c, rx - 7, by + 3, rz + 8, 89, 0);
        // Structure audit 2026-09-23. The ladder that was here went in before the basement was dug and
        // the shell capped its stub, so the basement had no way in, and the bunker had none either.
        // The old shaft is the corium chute now: magma from the lava sheet down to the pool it fed.
        fill(c, rx + 4, y0 - 7, rz + 4, rx + 4, y0 - 4, rz + 4, 213, 0);
        fill(c, rx + 4, by, rz + 4, rx + 4, by + 8, rz + 4, 213, 0);
        // A maintenance shaft from a hatch in the drum floor, down the basement's east wall.
        // The ladder comes up through the floor; the hatch lid stands open against the drum wall.
        fill(c, rx + 9, by, rz - 4, rx + 9, y0, rz - 4, 65, 4);
        put(c, rx + 9, y0 + 1, rz - 4, 96, 6);
        // The bunker gets a door, with a button either side.
        ironDoor(c, rx - 6, by, rz + 7, 1, false);
        put(c, rx - 5, by + 1, rz + 6, 77, 4);
        put(c, rx - 8, by + 1, rz + 8, 77, 3);
        mob(c, rx + 6, by, rz - 6, EntityType.BLAZE);
        mob(c, rx - 6, by, rz + 2, EntityType.MAGMA_CUBE);
        mob(c, rx + 2, by, rz + 8, EntityType.HUSK);
        trove(c, rx - 7, by, rz + 8, r, NUKE_LOOT, NUKE_TROVE, 14 + r.nextInt(6));
    }

    // == 31. The Buried City =====================================================
    // Somebody's downtown with rock where the sky used to be: a grid of streets, six
    // blocks of building with the top floors fallen in, lamps that somebody kept lit,
    // standing water in the low end, and a cathedral on the square whose spire goes all
    // the way up into the ceiling.

    private static final int[][] CITY_LOOT = {
        { 98, 0, 14, 8, 24}, { 44, 0, 12, 6, 18}, {188, 0,  9, 2,  6}, {371, 0, 13, 6, 18},
        {340, 0, 10,  2, 6}, {351, 7, 11, 6, 18}, {263, 0, 12, 6, 18}, {266, 0, 10, 3,  9},
        {388, 0,  7,  1, 3}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1}, {352, 0, 11, 4, 12},
    };
    private static final int[][] CITY_TROVE = {
        {133, 0, 1, 1, 2}, {130, 0, 1, 1, 1}, {403, 0, 1, 2, 3},
    };

    /**
     * Where a loose block dropped at (wx, wy, wz) comes to rest: the first cell above something that is
     * not air, and no lower than {@code floor}. Only the chunk being built is read; a column outside it
     * keeps its height (put() clips that column anyway).
     */
    private static int rest(Chunk c, int wx, int wy, int wz, int floor) {
        int x = wx - c.getX() * 16, z = wz - c.getZ() * 16;
        if (x < 0 || x > 15 || z < 0 || z > 15) return wy;
        while (wy > floor && wy > 1 && c.getBlock(x, wy - 1, z).getTypeId() == 0) wy--;
        return wy;
    }

    // == local helpers (structure audit 2026-09-23) =================================
    // Reads only ever look at the chunk being built, and only decide what happens to that same
    // column or cell, so every chunk of a site reaches the same answer.

    /** The block id at a world position when it lies in the chunk being built, else -1. */
    private static int idAt(Chunk c, int wx, int wy, int wz) {
        int x = wx - c.getX() * 16, z = wz - c.getZ() * 16;
        if (x < 0 || x > 15 || z < 0 || z > 15 || wy < 1 || wy > 250) return -1;
        return c.getBlock(x, wy, z).getTypeId();
    }

    /** True for a block a vine or web can hang on (in this chunk). */
    private static boolean holds(Chunk c, int wx, int wy, int wz) {
        int x = wx - c.getX() * 16, z = wz - c.getZ() * 16;
        if (x < 0 || x > 15 || z < 0 || z > 15 || wy < 1 || wy > 250) return false;
        return c.getBlock(x, wy, z).getType().isOccluding();
    }

    /** Trees, ground cover and snow: nothing a wall or a base stands on. */
    private static boolean soft(int b) {
        return b == 17 || b == 18 || b == 161 || b == 162 || b == 106 || b == 31 || b == 32 || b == 37
            || b == 38 || b == 175 || b == 6 || b == 78 || b == 81 || b == 83 || b == 99 || b == 100;
    }

    /** An iron door (lower half facing 0 E / 1 S / 2 W / 3 N) with its upper half. */
    private static void ironDoor(Chunk c, int wx, int wy, int wz, int facing, boolean rightHinge) {
        put(c, wx, wy, wz, 71, facing);
        put(c, wx, wy + 1, wz, 71, rightHinge ? 9 : 8);
    }

    /**
     * Leaves and vines round a shaft mouth (a 5x5 from the ground to four over it) go: a crown standing
     * over the lip left nowhere to step off the top rung. Logs stay, so no tree is cut in half.
     */
    private static void clearMouth(Chunk c, Terrain t, int wx, int wz) {
        int g = t.sample(wx, wz).y;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = g + 1; y <= g + 5; y++) {
            int b = idAt(c, wx + dx, y, wz + dz);
            if (b == 18 || b == 161 || b == 106) put(c, wx + dx, y, wz + dz, 0, 0);
        }
    }

    /**
     * Packs a column under a floor block down to the first solid ground (at most 24 blocks): soil blocks
     * of dirt first, then id (stone), so a packed bank over a drop shows as earth over rock.
     */
    private static void underpin(Chunk c, int wx, int fromY, int wz, int id, int data, int soil) {
        for (int y = fromY; y > fromY - 24 && y > 1; y--) {
            int b = idAt(c, wx, y, wz);
            if (b < 0) return;
            if (b != 0 && b != 8 && b != 9 && b != 10 && b != 11) return;
            if (fromY - y < soil) put(c, wx, y, wz, 3, 0);
            else put(c, wx, y, wz, id, data);
        }
    }

    /** The top of the real ground (not trees or snow; water counts) in a column of this chunk, else -1. */
    private static int surface(Chunk c, int wx, int wz, int from) {
        for (int y = Math.min(250, from); y > 1; y--) {
            int b = idAt(c, wx, y, wz);
            if (b < 0) return -1;
            if (b != 0 && !soft(b)) return y;
        }
        return -1;
    }

    /**
     * A walk out of a gate on the +z side to the natural ground. The compounds are laid on the lowest
     * corner of their footprint and nothing met the gate from outside, so a gate could open onto a bank
     * of earth or over a drop. Rows run outward from zFrom; each row's deck moves at most one block
     * toward the natural surface (Terrain.sample, so every chunk agrees), as a stair where it steps.
     * Where the ground is higher the walk is a cut with retaining walls, where it is lower it stands on
     * a retaining base. It stops on the first row that meets the ground, and after len rows at most.
     */
    private static void apron(Chunk c, Terrain t, int ax0, int ax1, int zFrom, int y, int len,
                              int id, int data, int stairId, boolean real) {
        // real: the whole walk lies in one chunk (the caller's geometry guarantees it), so that chunk
        // alone builds it and follows the real ground, ravines and all; the others leave it be.
        int prev = y, cx = (ax0 + ax1) / 2;
        if (real && idAt(c, cx, 64, zFrom) < 0) return;
        for (int k = 0; k < len; k++) {
            int z = zFrom + k;
            int g = real ? surface(c, cx, z, Math.max(y, t.sample(cx, z).y) + 12) : t.sample(cx, z).y;
            if (g < 0) break;
            int h = Math.max(prev - 1, Math.min(prev + 1, g));
            for (int x = ax0; x <= ax1; x++) {
                int gx = t.sample(x, z).y;
                // The base goes down to the real ground in this column: the sampled surface knows nothing
                // of ravines and caves, and a flight over one hung in the air.
                for (int yy = h - 1; yy > h - 25 && yy > 1; yy--) {
                    int b = idAt(c, x, yy, z);
                    if (b < 0 || (b != 0 && b != 8 && b != 9 && b != 10 && b != 11 && !soft(b))) break;
                    put(c, x, yy, z, id, data);
                }
                if (h < prev) { put(c, x, h, z, id, data); put(c, x, h + 1, z, stairId, 3); }     // step down, faces the gate
                else if (h > prev) put(c, x, h, z, stairId, 2);                                  // step up, faces out
                else put(c, x, h, z, id, data);
                int clear = h < prev ? h + 2 : h + 1;
                for (int yy = clear; yy <= Math.max(h + 4, gx); yy++) put(c, x, yy, z, 0, 0);
                for (int yy = Math.max(h + 5, gx + 1); yy <= h + 16; yy++) {           // no canopy left hanging
                    int b = idAt(c, x, yy, z);
                    if (b == 17 || b == 18 || b == 161 || b == 162 || b == 106) put(c, x, yy, z, 0, 0);
                }
            }
            for (int x = ax0 - 1; x <= ax1 + 1; x += ax1 - ax0 + 2) {               // retaining walls
                // Only where there is ground to hold back (a wall stood in the air over a drop).
                for (int yy = h + 1; yy <= h + 8; yy++) {
                    int b = idAt(c, x, yy, z);
                    if (b > 0 && !soft(b) && b != 8 && b != 9) put(c, x, yy, z, id, data);
                }
            }
            prev = h;
            if (h == g) break;
        }
    }

    private static void buriedCity(Chunk c, Terrain t) {
        Site s = deep(t, c, CITY_CELL, 0x43495459L, 61, 61, 26, Megaliths.RANK_CITY);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 60, z1 = z0 + 60, y1 = s.y + 25;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);
        shell(c, x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 4, 0);
        fill(c, x0, y1, z0, x1, y1, z1, 1, 5);
        // Streets on a fifteen-block grid, paved, with water lying in the north-west.
        for (int dx = 0; dx <= 60; dx++) for (int dz = 0; dz <= 60; dz++) {
            boolean street = dx % 15 < 5 || dz % 15 < 5;
            if (!street) continue;
            put(c, x0 + dx, y0, z0 + dz, ((dx + dz) & 3) == 0 ? 4 : 1, 0);
            if (dx < 20 && dz < 20 && ((dx * 7 + dz * 5) & 3) != 0) put(c, x0 + dx, y0 + 1, z0 + dz, 9, 0);
        }
        // Six by six blocks of building, ten across, of varying height and varying luck.
        for (int gx = 0; gx < 4; gx++) for (int gz = 0; gz < 4; gz++) {
            int bx = x0 + 5 + gx * 15, bz = z0 + 5 + gz * 15;
            int hash = (int) (Terrain.mix(s.seed + gx * 131L + gz * 977L) >>> 8);
            int h = 7 + Math.floorMod(hash, 13);
            int wall = new int[]{98, 45, 48, 98, 24, 98}[Math.floorMod(hash >> 5, 6)];
            int data = wall == 98 ? Math.floorMod(hash >> 9, 3) : 0;
            boolean fallen = Math.floorMod(hash >> 13, 5) == 0;
            // The four lots round the square are the square's (structure audit 2026-09-23): their
            // buildings were stamped through the cathedral, one of them growing out of its roof. What
            // stood there came down to the ground; a kiosk and the rubble are left.
            boolean square = (gx == 1 || gx == 2) && (gz == 1 || gz == 2);
            // Floors that stand: under the roof, and below the break where the top came down.
            int top = fallen ? h - 6 : h - 3;
            if (!square) {
                shell(c, bx, y0 + 1, bz, bx + 9, y0 + h, bz + 9, wall, data);
                fill(c, bx + 1, y0 + 1, bz + 1, bx + 8, y0 + h - 1, bz + 8, 0, 0);
                for (int f = 1; f < h - 2; f += 4) {
                    fill(c, bx + 1, y0 + f, bz + 1, bx + 8, y0 + f, bz + 8, 5, 1);
                    for (int dz = 2; dz <= 7; dz += 2) {
                        fill(c, bx, y0 + f + 1, bz + dz, bx, y0 + f + 2, bz + dz, 102, 0);
                        fill(c, bx + 9, y0 + f + 1, bz + dz, bx + 9, y0 + f + 2, bz + dz, 102, 0);
                    }
                    if (f == 1) fill(c, bx + 4, y0 + 1, bz, bx + 5, y0 + 3, bz, 0, 0);
                    // Every storey is reached (structure audit 2026-09-23): a ladder on the east wall
                    // through a hatch in the corner. Storeys were sealed plank boxes.
                    if (f > 1 && f <= top) fill(c, bx + 8, y0 + f - 3, bz + 8, bx + 8, y0 + f, bz + 8, 65, 4);
                }
                fill(c, bx + 1, y0 + h, bz + 1, bx + 8, y0 + h, bz + 8, 44, 5);
            }
            if (fallen) {                                            // the ones that came down
                if (!square) fill(c, bx, y0 + h - 5, bz, bx + 9, y0 + h, bz + 9, 0, 0);
                for (int i = 0; i < 40; i++) {
                    // The rubble lies where it fell, on what is left of the building: it used to hang where
                    // the storeys had been, and the mossy pieces were pruned as floaters (structure audit
                    // 2026-09-22). Same draws as before; nothing comes to rest on the chest's lid.
                    int px = bx + r.nextInt(10), py = y0 + h - 6 + r.nextInt(3), pz = bz + r.nextInt(10);
                    int stone = r.nextInt(3) == 0 ? 48 : 4;
                    if (square) continue;                                    // draws kept, nothing built
                    py = rest(c, px, py, pz, y0 + 1);
                    if (px == bx + 2 && pz == bz + 7 && py <= y0 + 2) continue;
                    // Nor on the ladder's head or the cells it is stepped off onto (rubble capped it).
                    if (px >= bx + 7 && px <= bx + 8 && pz >= bz + 7 && pz <= bz + 8) continue;
                    put(c, px, py, pz, stone, 0);
                }
            }
            if (square) {
                int kx = bx + 2, kz = bz + 7, qx = bx + 7, qz = bz + 2;
                boolean heap = true;
                if (((gx + gz) & 1) == 0) {
                    // A news kiosk that stayed up, with the lot's box in it (same place, same draws).
                    for (int ex = -1; ex <= 1; ex += 2) for (int ez = -1; ez <= 1; ez += 2)
                        fill(c, kx + ex, y0 + 1, kz + ez, kx + ex, y0 + 2, kz + ez, 85, 0);
                    fill(c, kx - 1, y0 + 3, kz - 1, kx + 1, y0 + 3, kz + 1, 126, 1);
                    graded(c, s, kx, y0 + 1, kz, r, CITY_LOOT, (gx + gz) == 4);
                    mob(c, qx, y0 + 1, qz, EntityType.ZOMBIE);
                } else if (((gx * 5 + gz * 3) & 3) == 1) {
                    qx = bx + 4; qz = bz + 4;
                    mob(c, qx, y0 + 1, qz, EntityType.SILVERFISH);          // silverfish live in the stone
                } else heap = false;
                // What came down: a low heap round the lot's spawner, cobble and moss.
                if (heap) for (int ex = -1; ex <= 1; ex++) for (int ez = -1; ez <= 1; ez++) {
                    if (ex == 0 && ez == 0) continue;
                    int bits = (int) (Terrain.mix(s.seed + (qx + ex) * 31L + (qz + ez) * 17L) >>> 20);
                    if ((bits & 3) == 0) continue;
                    put(c, qx + ex, y0 + 1, qz + ez, (bits & 4) == 0 ? 4 : 48, 0);
                    if ((bits & 24) == 0) put(c, qx + ex, y0 + 2, qz + ez, 4, 0);
                }
            } else {
                // Chest and spawner stand on the floorboards (they were set into them).
                if (((gx + gz) & 1) == 0) {
                    graded(c, s, bx + 2, y0 + 2, bz + 7, r, CITY_LOOT, (gx + gz) == 4);
                    // (gx*3+gz) is always even here, so it was never a cave spider (structure audit 2026-09-23).
                    mob(c, bx + 7, y0 + 2, bz + 2, (gx & 1) == 0 ? EntityType.ZOMBIE : EntityType.CAVE_SPIDER);
                }
                // The silverfish keep to the second storey where it still stands (a hatch leads up to it
                // now); where it came down or never was, they are in the ground floor, not in mid-air.
                if (((gx * 5 + gz * 3) & 3) == 1)
                    mob(c, bx + 4, top >= 5 ? y0 + 5 : y0 + 2, bz + 4, EntityType.SILVERFISH);
                cityFitOut(c, bx, bz, y0, h, top, fallen, hash);
            }
            put(c, bx - 2, y0 + 1, bz - 2, 85, 0);                   // the street lamp
            put(c, bx - 2, y0 + 2, bz - 2, 85, 0);
            put(c, bx - 2, y0 + 3, bz - 2, 89, 0);
        }
        // The square, the cathedral on it, and a spire that goes into the roof.
        int mx = x0 + 30, mz = z0 + 30;
        fill(c, mx - 8, y0, mz - 8, mx + 8, y0, mz + 8, 155, 0);
        shell(c, mx - 6, y0 + 1, mz - 6, mx + 6, y0 + 14, mz + 6, 98, 0);
        fill(c, mx - 5, y0 + 1, mz - 5, mx + 5, y0 + 13, mz + 5, 0, 0);
        fill(c, mx - 1, y0 + 1, mz - 6, mx + 1, y0 + 4, mz - 6, 0, 0);
        for (int dz = -4; dz <= 4; dz += 4) {
            fill(c, mx - 6, y0 + 6, mz + dz - 1, mx - 6, y0 + 10, mz + dz + 1, 95, 0);
            fill(c, mx + 6, y0 + 6, mz + dz - 1, mx + 6, y0 + 10, mz + dz + 1, 95, 0);
        }
        fill(c, mx - 6, y0 + 15, mz - 6, mx + 6, y0 + 15, mz + 6, 44, 5);
        for (int dy = 16; dy <= 24; dy++) {
            int ri = Math.max(0, 4 - (dy - 16) / 2);
            shell(c, mx - ri, y0 + dy, mz - ri, mx + ri, y0 + dy, mz + ri, 98, 3);
        }
        fill(c, mx - 1, y0 + 1, mz + 4, mx + 1, y0 + 3, mz + 4, 155, 1);
        put(c, mx, y0 + 4, mz + 4, 155, 1);
        // The nave is furnished and lit (structure audit 2026-09-23): the chandelier hangs from the
        // vault on a chain instead of in mid-air, two banks of pews face the altar across a centre
        // aisle, a chancel step, and lamps in the window piers.
        put(c, mx, y0 + 12, mz, 89, 0);
        put(c, mx, y0 + 13, mz, 85, 0);
        for (int pz = mz - 4; pz <= mz; pz += 2) {
            fill(c, mx - 4, y0 + 1, pz, mx - 2, y0 + 1, pz, 134, 3);
            fill(c, mx + 2, y0 + 1, pz, mx + 4, y0 + 1, pz, 134, 3);
        }
        fill(c, mx - 5, y0 + 1, mz + 3, mx + 5, y0 + 1, mz + 3, 44, 5);
        for (int ex = -6; ex <= 6; ex += 12) for (int ez = -2; ez <= 2; ez += 4) put(c, mx + ex, y0 + 8, mz + ez, 89, 0);
        mob(c, mx - 5, y0 + 1, mz + 1, EntityType.WITCH);                 // in the side aisle, off the centre
        mob(c, mx - 4, y0 + 1, mz + 4, EntityType.ZOMBIE);
        graded(c, s, mx + 4, y0 + 1, mz + 4, r, CITY_LOOT, true);
        // Growth and damp. Same draws; each piece only goes into open air and only onto something
        // that can hold it (structure audit 2026-09-23: they punched holes in walls and floors and
        // hung in mid-air).
        for (int i = 0; i < 120; i++) {
            int gx = x0 + 1 + r.nextInt(59), gz = z0 + 1 + r.nextInt(59);
            int gy = y0 + 1 + r.nextInt(14), hang = r.nextInt(3) == 0 ? 30 : 106, side = 1 << r.nextInt(4);
            if (idAt(c, gx, gy, gz) != 0) continue;
            if (hang == 106) {
                int sx = side == 2 ? -1 : side == 8 ? 1 : 0, sz = side == 1 ? 1 : side == 4 ? -1 : 0;
                if (!holds(c, gx + sx, gy, gz + sz)) continue;
            } else {
                int walls = (holds(c, gx - 1, gy, gz) ? 1 : 0) + (holds(c, gx + 1, gy, gz) ? 1 : 0)
                          + (holds(c, gx, gy, gz - 1) ? 1 : 0) + (holds(c, gx, gy, gz + 1) ? 1 : 0);
                if (!holds(c, gx, gy + 1, gz) && walls < 2) continue;
            }
            put(c, gx, gy, gz, hang, hang == 30 ? 0 : side);             // webs take no data; same draws
        }
        // THE SURPRISE: the crypt under the square, reached by lifting the wrong flagstone.
        // Structure audit 2026-09-23: the crypt is kept above bedrock on a low site (it was clamped into
        // it: no floor, tombs, spawners or trove), the ladder goes in after the crypt so the crypt no
        // longer erases it and caps it, and the flagstone is one that no tomb stands under.
        int cy = Math.max(y0 - 9, 2), ctop = Math.min(cy + 6, y0 - 1);
        fill(c, mx - 7, cy, mz - 7, mx + 7, ctop - 1, mz + 7, 0, 0);
        shell(c, mx - 8, cy - 1, mz - 8, mx + 8, ctop, mz + 8, 98, 1);
        fill(c, mx - 7, cy - 1, mz - 7, mx + 7, cy - 1, mz + 7, 98, 2);
        for (int i = 0; i < 6; i++) {                                // the tombs
            int px = mx - 5 + (i % 3) * 5, pz = mz - 4 + (i / 3) * 8;
            fill(c, px, cy, pz, px + 2, cy + 1, pz + 3, 155, 0);
            if (cy + 2 < ctop) put(c, px + 1, cy + 2, pz + 1, 44, 7);
            if (i == 2) put(c, px + 1, cy + 1, pz + 1, 216, 0);
        }
        for (int dx = -6; dx <= 6; dx += 6) for (int dz = -6; dz <= 6; dz += 12) {
            fill(c, mx + dx, cy, mz + dz, mx + dx, ctop - 1, mz + dz, 98, 3);
            put(c, mx + dx, Math.min(cy + 3, ctop - 1), mz + dz, 89, 0);
        }
        if (ctop + 1 <= y0 - 2) fill(c, mx + 8, ctop + 1, mz - 7, mx + 8, y0 - 2, mz - 7, 98, 1);
        fill(c, mx + 7, cy, mz - 7, mx + 7, y0, mz - 7, 65, 4);          // up into the missing flagstone
        mob(c, mx, cy, mz, EntityType.WITHER_SKELETON);
        mob(c, mx - 4, cy, mz + 2, EntityType.SKELETON);                   // was inside a tomb
        trove(c, mx + 2, cy, mz + 2, r, CITY_LOOT, CITY_TROVE, 14 + r.nextInt(6));   // was inside a pillar
        riser(c, t, x0 + 2, z0 + 2, y1, 1, 0);
        // The second shaft comes down on a street against the east wall (it came down on the roof of a
        // building nobody could get off).
        riser(c, t, x1, z0 + 46, y1, 1, 0);
        // Both shafts stopped in the rock sky, twenty-odd blocks over the street (structure audit
        // 2026-09-23): the ladder carries on down a stone-brick service pier to the street, on the side
        // the shaft's own ladder hangs on.
        for (int[] sh : new int[][]{{x0 + 2, z0 + 2}, {x1, z0 + 46}}) {
            fill(c, sh[0], y0 + 1, sh[1] - 1, sh[0], y1 - 1, sh[1] - 1, 98, 0);
            fill(c, sh[0], y0 + 1, sh[1], sh[0], y1 - 1, sh[1], 65, 3);
        }
    }

    /**
     * What the buildings were (structure audit 2026-09-23: every room was an empty dark shell). A shop
     * on the ground floor, flats and offices above, chosen per building and storey from its hash; one
     * light per storey set in the ceiling, with about a third of them gone.
     */
    private static void cityFitOut(Chunk c, int bx, int bz, int y0, int h, int top, boolean fallen, int hash) {
        // Shop: a spruce counter across from the door, stock shelves on the back wall, a barrel.
        int fy = y0 + 2;
        fill(c, bx + 1, fy, bz + 4, bx + 3, fy, bz + 4, 5, 1);
        fill(c, bx + 1, fy + 1, bz + 4, bx + 3, fy + 1, bz + 4, 126, 1);
        fill(c, bx + 4, fy, bz + 8, bx + 6, fy + 1, bz + 8, 47, 0);
        put(c, bx + 7, fy, bz + 8, 118, 0);
        for (int f = 1; f <= top; f += 4) {
            int feet = y0 + f + 1;
            int ceil = f + 4 <= top ? y0 + f + 4 : (fallen ? -1 : y0 + h);
            int bits = hash >>> (f % 16);
            if (ceil > 0 && (bits & 3) != 0) put(c, bx + 5, ceil, bz + 5, 89, 0);
            if (f == 1) continue;
            if ((bits & 4) == 0) {                                   // a flat
                put(c, bx + 2, feet, bz + 2, 26, 0); put(c, bx + 2, feet, bz + 3, 26, 8);
                put(c, bx + 6, feet, bz + 1, 61, 3);
                put(c, bx + 7, feet, bz + 1, 58, 0);
                fill(c, bx + 4, feet, bz + 5, bx + 6, feet, bz + 6, 171, (bits >> 3) & 15);
                put(c, bx + 1, feet, bz + 8, 140, 0);
            } else {                                                 // an office
                fill(c, bx + 2, feet, bz + 3, bx + 3, feet, bz + 3, 126, 9);
                fill(c, bx + 5, feet, bz + 3, bx + 6, feet, bz + 3, 126, 9);
                put(c, bx + 2, feet, bz + 4, 134, 2); put(c, bx + 5, feet, bz + 4, 134, 2);
                fill(c, bx + 2, feet, bz + 8, bx + 4, feet + 1, bz + 8, 47, 0);
                put(c, bx + 6, feet, bz + 8, 84, 0);                 // the safe
            }
        }
    }

    // == 32. The Visitor =========================================================
    // A lens of dark stone nearly sixty across, tipped over and driven a third of the way
    // into the ground at the end of a furrow you can follow from the ridge. There are no
    // seams in it. Inside are ribbed corridors that do not meet at right angles, a bay
    // full of things suspended in something, and at the middle a sphere nobody opened.

    private static final int[][] ALIEN_LOOT = {
        {409, 0, 13, 4, 12}, {168, 0, 11, 3,  9}, {201, 0, 11, 4, 12}, {202, 0,  9, 2, 6},
        {121, 0, 10,  4, 12}, {368, 0,  8, 1,  3}, {382, 0,  7, 1,  2}, {348, 0, 10, 4, 12},
        {264, 0,  8,  1,  3}, {388, 0,  8, 1,  3}, {384, 0,  7, 2,  6}, {403, 0,  4, 1, 1},
    };
    private static final int[][] ALIEN_TROVE = {
        {399, 0, 1, 1, 1}, {443, 0, 1, 1, 1}, {437, 0, 1, 2, 4},
    };

    /** The bay floor's height at dx from the hull's centre: it leans with the hull. */
    private static int floorY(int cy, int dx) {
        return cy - 4 + (int) Math.round(dx * 0.22);
    }

    private static void alienShip(Chunk c, Terrain t) {
        Site s = ground(t, c, ALIEN_CELL, 0x414C49454EL, 57, 57, 10, Megaliths.RANK_ALIEN);
        if (s == null) return;
        Random r = new Random(s.seed);
        int mx = s.x + 28, mz = s.z + 28, cy = s.y + 7;
        // The furrow it cut on the way in, and the burn either side.
        for (int dx = -28; dx <= 28; dx++) for (int dz = -28; dz <= 28; dz++) {
            double along = dz + 34, across = Math.abs(dx) * (1.0 - Math.min(1.0, Math.max(0, along) / 70.0));
            if (along < 30 || across > 9) continue;
            for (int dy = 0; dy <= 3; dy++) put(c, mx + dx, s.y + dy, mz + dz, 0, 0);
            // Clear of the hull, the furrow is open to the sky: higher ground over it was left as a roof
            // of loose dirt over the burn (structure audit 2026-09-23).
            if (dx * dx + dz * dz > 27.5 * 27.5)
                for (int y = s.y + 4; y <= s.y + 14 && idAt(c, mx + dx, y, mz + dz) != 0; y++) put(c, mx + dx, y, mz + dz, 0, 0);
            // Burn: black concrete 251:15 (was coal block); same draws as before.
            int burn = r.nextInt(6) == 0 ? 251 : (r.nextInt(3) == 0 ? 87 : 49);
            put(c, mx + dx, s.y - 1, mz + dz, burn, burn == 251 ? 15 : 0);
        }
        // The hull: a lens, tipped, with no seams in it.
        for (int dx = -28; dx <= 28; dx++) for (int dz = -28; dz <= 28; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 27.5) continue;
            double th = 9.5 * Math.sqrt(Math.max(0, 1 - (d / 27.5) * (d / 27.5)));
            int lean = (int) Math.round(dx * 0.22);
            int top = cy + (int) Math.round(th) + lean, bot = cy - (int) Math.round(th) + lean;
            for (int y = bot; y <= top; y++) {
                boolean skin = y >= top - 1 || y <= bot + 1 || d > 25.6;
                boolean band = skin && (((int) d) % 7 == 0);
                boolean lamp = skin && d > 24.0 && ((dx + dz) % 11 == 0);
                if (skin) put(c, mx + dx, y, mz + dz, lamp ? 169 : 168, lamp ? 0 : (band ? 1 : 2));
                else put(c, mx + dx, y, mz + dz, 0, 0);
            }
            // Ribs, every so often, holding the two skins apart.
            if (((dx + 64) % 9 == 0 || (dz + 64) % 9 == 0) && d < 25.0)
                for (int y = bot + 2; y <= top - 2; y++)
                    if (((dx + dz) & 1) == 0) put(c, mx + dx, y, mz + dz, 202, 0);
        }
        // A way in: the furrow side of the hull is torn open.
        for (int dz = 20; dz <= 28; dz++) for (int dx = -4; dx <= 4; dx++)
            for (int dy = -4; dy <= 4; dy++) {
                if (dx * dx + dy * dy > 16) continue;
                put(c, mx + dx, cy + dy + (int) Math.round(dx * 0.22), mz + dz, 0, 0);
            }
        // Structure audit 2026-09-23: the tear's lowest edge stood four blocks over the furrow floor, so
        // nothing inside could be reached. The plating it lost lies in the furrow under it, heaped to
        // the lip in one-block steps, and the slot at the bottom of the tear is closed.
        for (int dx = -3; dx <= 3; dx++) {
            int edge = Math.abs(dx) == 3 ? 1 : 0;                   // the heap slumps at its edges
            for (int k = 0; k < 3; k++) {
                int hgt = k + 1 - edge;
                if (hgt > 0) fill(c, mx + dx, s.y, mz + 28 - k, mx + dx, s.y + hgt - 1, mz + 28 - k, edge == 1 ? 201 : 168, edge == 1 ? 0 : 2);
            }
        }
        fill(c, mx, cy - 4, mz + 23, mx, cy - 4, mz + 25, 168, 2);
        // The bay: things suspended in something, in rows. Tanks, chests and spawners stand on the
        // floor, which leans with the hull: they were placed on a level plane and hovered or sank.
        for (int i = 0; i < 10; i++) {
            double ang = i * 2 * Math.PI / 10;
            int px = mx + (int) Math.round(Math.cos(ang) * 15), pz = mz + (int) Math.round(Math.sin(ang) * 15);
            int py = cy + (int) Math.round(px - mx) / 5;
            shell(c, px - 1, Math.min(py - 3, floorY(cy, px - mx) + 1), pz - 1, px + 1, py + 3, pz + 1, 95, 13);
            fill(c, px, py - 2, pz, px, py + 2, pz, 165, 0);
            put(c, px, py + 3, pz, 169, 0);
            if (i % 3 == 0) mob(c, px + 2, floorY(cy, px + 2 - mx) + 1, pz, i == 0 ? EntityType.SLIME : EntityType.ENDERMITE);
            if (i % 4 == 1) graded(c, s, px - 2, floorY(cy, px - 2 - mx) + 1, pz, r, ALIEN_LOOT, i == 5);
        }
        // (A level air course at cy-3 used to be cleared here: it cut every rib and two tanks in half,
        // took a spawner and slit the hull. The hull is already hollow.)
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 20.5) continue;
            int lean = (int) Math.round(dx * 0.22);
            boolean ring = d % 6 < 1;
            // Every fourth ring block is a lamp set in the floor: the bay was dark.
            put(c, mx + dx, cy - 4 + lean, mz + dz, ring ? (((dx * 7 + dz * 13) & 3) == 0 ? 169 : 168) : 201, 0);
        }
        mob(c, mx + 10, floorY(cy, 10) + 1, mz + 4, EntityType.ENDERMAN);
        mob(c, mx - 11, floorY(cy, -11) + 1, mz - 4, EntityType.ENDERMAN);        // off the rib line
        mob(c, mx + 4, floorY(cy, 4) + 1, mz + 18, EntityType.CAVE_SPIDER);
        graded(c, s, mx + 7, floorY(cy, 7) + 1, mz - 8, r, ALIEN_LOOT, true);    // off the rib line
        graded(c, s, mx - 7, floorY(cy, -7) + 1, mz + 8, r, ALIEN_LOOT, false);
        // THE SURPRISE: the sphere at the middle. It has no door, and the only way in is
        // through it, so the shell is left solid and the chamber is sealed.
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) for (int dy = -7; dy <= 7; dy++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 7.3) continue;
            int wy = cy + dy;
            if (d > 6.1) put(c, mx + dx, wy, mz + dz, 49, 0);
            else if (d > 5.4) put(c, mx + dx, wy, mz + dz, 121, 0);
            else put(c, mx + dx, wy, mz + dz, 0, 0);
        }
        // The spiral of lights is set into the lining (it hung in the air of the chamber), and the core
        // runs from the lining's floor to its roof (structure audit 2026-09-23).
        for (int dy = -4; dy <= 4; dy++) {
            int ri = 4 - Math.abs(dy) / 2;
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4 + dy * 0.35;
                for (double rr = ri; rr <= 7.0; rr += 0.25) {
                    int lx = (int) Math.round(Math.cos(ang) * rr), lz = (int) Math.round(Math.sin(ang) * rr);
                    double d = Math.sqrt(lx * lx + dy * dy + lz * lz);
                    if (d <= 5.4) continue;
                    if (d <= 6.1) {
                        int rod = Math.abs(lx) >= Math.abs(lz) ? (lx > 0 ? 4 : 5) : (lz > 0 ? 2 : 3);
                        put(c, mx + lx, cy + dy, mz + lz, (a & 1) == 0 ? 89 : 198, (a & 1) == 0 ? 0 : rod);
                    }
                    break;
                }
            }
        }
        fill(c, mx, cy - 5, mz, mx, cy + 5, mz, 202, 0);
        put(c, mx, cy, mz, 169, 0);                                   // sea lantern, was a beacon (light only)
        mob(c, mx + 3, cy - 4, mz + 3, EntityType.ENDERMAN);
        trove(c, mx - 3, cy - 4, mz - 3, r, ALIEN_LOOT, ALIEN_TROVE, 16 + r.nextInt(6));
    }

    // == 33. Bombfall ============================================================
    // A crater with a town in it. The thing that made the crater is still lying at the
    // bottom of it, unexploded, in water up to its fins, and somebody has built a ring of
    // benches around it. Everything else is scrap on stilts up the crater wall, joined by
    // walkways, with a gate made out of an aircraft door.

    private static final int[][] MEGATON_LOOT = {
        {289, 0, 14, 6, 18}, {262, 0, 13, 8, 24}, {265, 0, 12, 4, 12}, {263, 0, 12, 6, 18},
        {394, 0,  9, 2,  6}, {329, 0,  6, 1,  1}, {365, 0, 10, 2,  6}, {367, 0, 12, 4, 12},
        {266, 0,  9, 2,  6}, {373, 0,  8, 1,  2}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] MEGATON_TROVE = {
        { 46, 0, 1, 8, 16}, { 57, 0, 1, 1, 1}, {322, 0, 1, 2, 3},
    };

    /** How deep the crater is at distance d from its middle. */
    private static int craterDepth(double d) {
        return (int) Math.round(9 * Math.cos(Math.min(1.0, d / 19.5) * Math.PI / 2));
    }

    /** Where a player's feet are on the crater floor at (dx, dz) from its middle. */
    private static int craterFeet(int y0, int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d > 19.5 ? y0 : y0 - craterDepth(d);
    }

    private static void megaton(Chunk c, Terrain t) {
        Site s = ground(t, c, MEGATON_CELL, 0x4D454741L, 41, 41, 9, Megaliths.RANK_MEGATON);
        if (s == null) return;
        Random r = new Random(s.seed);
        int mx = s.x + 20, mz = s.z + 20, y0 = s.y;
        // The crater. Its floor is packed down to the ground below it (structure audit 2026-09-23: over
        // a cliff or a cave the rim was a one-block skin, and loose dirt floated), and the pool under
        // the bomb's belly is a block deeper, so there is room to swim under it to the hatch.
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 19.5) continue;
            int depth = craterDepth(d);
            for (int y = y0 - depth; y <= y0 + 10; y++) put(c, mx + dx, y, mz + dz, 0, 0);
            // Nothing stands higher than that over the crater but the crowns of trees the crater cut
            // through, which hung over it (structure audit 2026-09-23).
            for (int y = y0 + 11; y <= y0 + 30; y++) if (idAt(c, mx + dx, y, mz + dz) > 0) put(c, mx + dx, y, mz + dz, 0, 0);
            // Scorched floor: black concrete 251:15 (was coal block); same draws as before.
            int bed = d > 14 ? 3 : (r.nextInt(5) == 0 ? 251 : 1);
            int under = depth == 9 && d <= 4.2 ? 2 : 1;
            put(c, mx + dx, y0 - depth - under, mz + dz, bed, bed == 251 ? 15 : 0);
            underpin(c, mx + dx, y0 - depth - under - 1, mz + dz, 1, 0, bed == 3 ? 2 : 0);
            if (depth >= 8) fill(c, mx + dx, y0 - depth - under + 1, mz + dz, mx + dx, y0 - depth, mz + dz, 9, 0);
        }
        // Where the ground round the crater stands higher than its lip, the cut is faced with the town's
        // scrap: sheet metal and rusted panels holding the bank back (structure audit 2026-09-23: on
        // high ground the crater was a raw vertical shaft of earth). Only the bank rising unbroken from the
        // lip is faced: never air, nor a tree's crown of ice or clay standing over it.
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 19.5 || d > 21.0) continue;
            for (int y = y0; y <= y0 + 10; y++) {
                int b = idAt(c, mx + dx, y, mz + dz);
                if (b <= 0 || soft(b) || b == 8 || b == 9) break;
                int k = (int) (Terrain.mix(s.seed + (mx + dx) * 31L + y * 7L + (mz + dz) * 17L) >>> 40) & 7;
                put(c, mx + dx, y, mz + dz, k < 4 ? 43 : 159, k < 4 ? 8 : (k < 6 ? 12 : 1));
            }
        }
        // Round the rim, crowns of trees whose trunks stood in the crater hang there too; a crown with no
        // trunk left near it and nothing but air under it goes (a tree standing outside keeps its own).
        for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 19.5 || d > 24.0) continue;
            for (int y = y0 + 30; y > y0 + 10; y--) {
                int b = idAt(c, mx + dx, y, mz + dz);
                if (!crown(b) || trunk(c, mx + dx, y, mz + dz)) continue;
                int yy = y - 1;
                while (yy > y0 && crown(idAt(c, mx + dx, yy, mz + dz))) yy--;
                if (idAt(c, mx + dx, yy, mz + dz) == 0) put(c, mx + dx, y, mz + dz, 0, 0);
            }
        }
        int by = y0 - 9;
        // The bomb, lying in it.
        for (int dy = 0; dy <= 5; dy++) for (int dx = -3; dx <= 6; dx++) for (int dz = -3; dz <= 3; dz++) {
            double rr = Math.sqrt(dy * dy * 0.8 + dz * dz) ;
            double taper = dx > 3 ? (6 - dx) * 1.1 : 3.0;
            if (rr > taper) continue;
            put(c, mx + dx, by + 1 + dy, mz + dz, dx > 4 ? 155 : 43, dx > 4 ? 2 : 8);   // casing 43:8, was iron
        }
        for (int dz = -4; dz <= 4; dz += 8) fill(c, mx - 4, by + 2, mz + dz, mx - 2, by + 5, mz + dz, 44, 7);
        fill(c, mx - 4, by + 2, mz - 1, mx - 4, by + 3, mz + 1, 43, 8);      // tail cone: the fin hung off nothing
        fill(c, mx - 5, by + 3, mz - 1, mx - 5, by + 4, mz + 1, 44, 7);
        // The ring of benches round it, because of course there is one. They stand on the floor (they
        // were set into it) and the lamps stand behind them.
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            int px = mx + (int) Math.round(Math.cos(ang) * 9), pz = mz + (int) Math.round(Math.sin(ang) * 9);
            int fy = craterFeet(y0, px - mx, pz - mz);
            if ((a & 1) == 0) { put(c, px, fy, pz, 5, 1); put(c, px, fy + 1, pz, 171, 14); }
            else put(c, px, fy, pz, 44, 5);
            if ((a & 3) == 0) {
                int lx = mx + (int) Math.round(Math.cos(ang) * 10.5), lz = mz + (int) Math.round(Math.sin(ang) * 10.5);
                int ly = craterFeet(y0, lx - mx, lz - mz);
                fill(c, lx, ly, lz, lx, ly + 1, lz, 85, 0);
                put(c, lx, ly + 2, lz, 89, 0);
            }
        }
        mob(c, mx + 8, craterFeet(y0, 8, 2), mz + 2, EntityType.CREEPER);
        graded(c, s, mx - 8, craterFeet(y0, -8, 0), mz, r, MEGATON_LOOT, false);
        // The town: scrap shacks on stilts up the crater wall. Structure audit 2026-09-23: each door faces
        // the middle, the floor is laid one step above the ground outside it, the stilts run down to the
        // ground (they used to cut holes in it), and there is a stove and a barrel in each.
        int[] sx = new int[9], sz = new int[9], sf = new int[9], ux = new int[9], uz = new int[9];
        for (int i = 0; i < 9; i++) {
            double ang = i * 2 * Math.PI / 9 + 0.3;
            int ring = 13 + (i % 3);
            int px = mx + (int) Math.round(Math.cos(ang) * ring), pz = mz + (int) Math.round(Math.sin(ang) * ring);
            int ox = px - mx, oz = pz - mz;
            if (Math.abs(ox) >= Math.abs(oz)) { ux[i] = ox > 0 ? -1 : 1; uz[i] = 0; }
            else { ux[i] = 0; uz[i] = oz > 0 ? -1 : 1; }
            int floor = craterFeet(y0, ox + 3 * ux[i], oz + 3 * uz[i]) + 1;
            sx[i] = px; sz[i] = pz; sf[i] = floor;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (((dx + dz) & 1) != 0) continue;
                int g = craterFeet(y0, ox + dx, oz + dz);
                if (g < floor) fill(c, px + dx, g, pz + dz, px + dx, floor - 1, pz + dz, 85, 0);
            }
            fill(c, px - 2, floor, pz - 2, px + 2, floor, pz + 2, 5, 1);
            int skin = new int[]{43, 159, 35, 159}[i % 4];           // 43 (data 8 below): sheet metal, was iron
            shell(c, px - 2, floor + 1, pz - 2, px + 2, floor + 4, pz + 2, skin, skin == 159 ? (i % 2 == 0 ? 1 : 12) : 8);
            fill(c, px - 1, floor + 1, pz - 1, px + 1, floor + 3, pz + 1, 0, 0);
            fill(c, px - 2, floor + 5, pz - 2, px + 2, floor + 5, pz + 2, 44, 7);
            fill(c, px + 2 * ux[i], floor + 1, pz + 2 * uz[i], px + 2 * ux[i], floor + 2, pz + 2 * uz[i], 0, 0);
            put(c, px, floor + 3, pz, 89, 0);
            put(c, px - 1, floor + 1, pz - 1, (i & 1) == 0 ? 61 : 58, (i & 1) == 0 ? 3 : 0);
            put(c, px + 1, floor + 1, pz - 1, 118, 2);
            if ((i & 1) == 1) put(c, px + 1, floor + 1, pz + 1, 171, 12);
            if (i % 2 == 0) graded(c, s, px + 1, floor + 1, pz + 1, r, MEGATON_LOOT, i == 4);
            mob(c, px - 1, floor + 1, pz + 1, i % 3 == 0 ? EntityType.SKELETON : EntityType.ZOMBIE);
        }
        // The walk down from each door: a spruce stair toward the middle, on posts where it stands clear,
        // to the ground (the old walkways all ran north into mid-air, stilts and the crater wall).
        for (int i = 0; i < 9; i++) {
            int y = sf[i];
            for (int k = 3; k <= 10; k++) {
                int wx = sx[i] + k * ux[i], wz = sz[i] + k * uz[i];
                int g = craterFeet(y0, wx - mx, wz - mz);
                if (y < g || bench(wx - mx, wz - mz)) break;
                put(c, wx, y, wz, 134, stairFacing(-ux[i], -uz[i]));
                if (g < y) fill(c, wx, g, wz, wx, y - 1, wz, 85, 0);
                y--;
            }
        }
        // The gate, and the pump everyone queues at. The gate stands on the rim (it hung a block over it),
        // with its lever on the inside face, and the walk out of it meets the ground.
        fill(c, mx - 4, y0, mz + 19, mx + 4, y0 + 7, mz + 19, 43, 8);       // gate plate: 43:8, was iron block
        fill(c, mx - 2, y0, mz + 19, mx + 2, y0 + 4, mz + 19, 0, 0);
        fill(c, mx - 4, y0 + 8, mz + 19, mx + 4, y0 + 8, mz + 19, 44, 7);
        put(c, mx + 3, y0 + 2, mz + 18, 69, 4);
        put(c, mx - 5, y0 + 3, mz + 19, 89, 0);
        apron(c, t, mx - 2, mx + 2, mz + 20, y0 - 1, 7, 3, 0, 134, true);
        // The pump is an open standpipe on the floor by the gate (it was a solid cage of bars stamped
        // into shack 3); its chest is as far in as it was.
        put(c, mx + 3, craterFeet(y0, 3, 11), mz + 11, 118, 3);
        int py = craterFeet(y0, 3, 12);
        fill(c, mx + 3, py, mz + 12, mx + 3, py + 2, mz + 12, 101, 0);
        put(c, mx + 3, py + 3, mz + 12, 89, 0);
        graded(c, s, mx + 2, craterFeet(y0, 2, 11), mz + 11, r, MEGATON_LOOT, false);
        // THE SURPRISE: the inspection hatch on the underside of the bomb. Structure audit 2026-09-23:
        // the chamber was dug after its ladder and its shell closed the shaft over it; it is dug first
        // now, the shaft comes down between the charges behind a trapdoor in the pool floor.
        fill(c, mx - 2, by - 10, mz - 3, mx + 4, by - 6, mz + 3, 0, 0);
        shell(c, mx - 3, by - 11, mz - 4, mx + 5, by - 5, mz + 4, 43, 8);   // inner shell: 43:8, was iron block
        fill(c, mx - 2, by - 11, mz - 3, mx + 4, by - 11, mz + 3, 251, 15);
        // Red concrete under the charges (was redstone block, which primed the TNT as soon as
        // anything nudged it): the bomb reads as a bomb and stays inert until a player lights it.
        for (int dz = -2; dz <= 2; dz += 2) {
            fill(c, mx - 1, by - 10, mz + dz, mx + 3, by - 10, mz + dz, 251, 14);
            put(c, mx + 1, by - 9, mz + dz, 46, 0);
        }
        put(c, mx + 1, by - 6, mz, 89, 0);
        fill(c, mx, by - 4, mz, mx + 2, by - 3, mz + 2, 43, 8);            // the shaft's collar
        fill(c, mx + 2, by - 10, mz + 1, mx + 2, by - 6, mz + 1, 43, 8);    // what the ladder hangs on
        fill(c, mx + 1, by - 10, mz + 1, mx + 1, by - 3, mz + 1, 65, 4);
        put(c, mx + 1, by - 2, mz + 1, 96, 10);                            // the hatch
        mob(c, mx + 4, by - 10, mz + 3, EntityType.CREEPER);
        trove(c, mx - 2, by - 10, mz - 3, r, MEGATON_LOOT, MEGATON_TROVE, 13 + r.nextInt(6));
    }

    /** What a tree's crown is made of here: leaves, the biome trees' clay and ice, snow and vines on them. */
    private static boolean crown(int b) {
        return b == 18 || b == 161 || b == 159 || b == 174 || b == 80 || b == 78 || b == 106;
    }

    /** A log within four blocks (in this chunk): the crown still has its tree. */
    private static boolean trunk(Chunk c, int wx, int y, int wz) {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -4; dy <= 4; dy++) {
            int b = idAt(c, wx + dx, y + dy, wz + dz);
            if (b == 17 || b == 162) return true;
        }
        return false;
    }

    /** The ring of benches: true at a bench's cell (dx, dz from the middle). */
    private static boolean bench(int dx, int dz) {
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            if (dx == (int) Math.round(Math.cos(ang) * 9) && dz == (int) Math.round(Math.sin(ang) * 9)) return true;
        }
        return false;
    }

    /** Stair data for a stair that climbs toward (fx, fz). */
    private static int stairFacing(int fx, int fz) {
        return fx > 0 ? 0 : fx < 0 ? 1 : fz > 0 ? 2 : 3;
    }

    // == 34. The Strip ===========================================================
    // A wall with a gate in it, a boulevard behind the gate, two houses either side of
    // the boulevard still advertising, and a tower at the end with a saucer on top that
    // nobody was ever let into. The power has not gone off. That is the strange part.

    private static final int[][] STRIP_LOOT = {
        { 266, 0, 14, 6, 18}, {387, 0,  8, 1, 2}, {2257, 0, 5, 1, 1}, {2264, 0, 5, 1, 1},
        {  84, 0,  6, 1,  1}, {322, 0,  7, 1, 2}, { 354, 0, 8, 1, 2}, {351, 5, 10, 6, 18},
        { 264, 0,  8, 1,  3}, {388, 0,  9, 2, 6}, { 384, 0, 8, 2, 6}, { 403, 0,  5, 1, 1},
    };
    private static final int[][] STRIP_TROVE = {
        { 41, 0, 1, 3, 6}, {266, 0, 1, 24, 48}, {403, 0, 1, 2, 3},
    };

    /** The saucer's radius at course dy (0..5); nothing outside it. */
    private static double saucerR(int dy) {
        return dy < 0 || dy > 5 ? 0 : dy < 3 ? 6.5 + dy * 1.6 : 11.3 - (dy - 3) * 2.4;
    }

    private static void strip(Chunk c, Terrain t) {
        Site s = ground(t, c, STRIP_CELL, 0x56454741L, 45, 45, 7, Megaliths.RANK_STRIP);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 44, z1 = z0 + 44;
        for (int dx = 0; dx <= 44; dx++) for (int dz = 0; dz <= 44; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 7);
        // The wall, and the gate under a neon arch.
        for (int dx = 0; dx <= 44; dx++) for (int dz = 0; dz <= 44; dz++) {
            if (dx != 0 && dx != 44 && dz != 0 && dz != 44) continue;
            if (dz == 44 && dx >= 20 && dx <= 24) continue;
            fill(c, x0 + dx, y0 + 1, z0 + dz, x0 + dx, y0 + 6, z0 + dz, 251, 15);
            put(c, x0 + dx, y0 + 7, z0 + dz, 44, 7);
        }
        for (int dx = 19; dx <= 25; dx++) {
            int h = 7 + (dx == 22 ? 3 : (Math.abs(dx - 22) == 1 ? 2 : 1));
            fill(c, x0 + dx, y0 + 7, z1, x0 + dx, y0 + h, z1, 95, (dx * 3) % 16);
            put(c, x0 + dx, y0 + h + 1, z1, 169, 0);
        }
        // The boulevard runs on out of the gate to the ground (structure audit 2026-09-23).
        apron(c, t, x0 + 20, x0 + 24, z1 + 1, y0, 6, 251, 15, 156, false);
        mob(c, x0 + 22, y0 + 1, z1 - 2, EntityType.SKELETON);
        mob(c, x0 + 19, y0 + 1, z1 - 3, EntityType.SKELETON);
        // The boulevard.
        fill(c, x0 + 18, y0, z0 + 12, x0 + 26, y0, z1 - 1, 251, 15);
        for (int dz = 14; dz <= 42; dz += 4) fill(c, x0 + 22, y0, z0 + dz, x0 + 22, y0, z0 + dz + 1, 251, 0);
        // Lamp posts: the first pair used to stand inside the tower wall, and one pair stood on the casino
        // doors' axis; they flank the doors now (structure audit 2026-09-23).
        for (int dz : new int[]{20, 26, 30, 34, 38}) {
            for (int side = 0; side < 2; side++) {
                int px = side == 0 ? x0 + 17 : x0 + 27;
                fill(c, px, y0 + 1, z0 + dz, px, y0 + 6, z0 + dz, 43, 8);   // lamp post: 43:8, was iron block
                put(c, px, y0 + 7, z0 + dz, 169, 0);
                put(c, px, y0 + 4, z0 + dz + 1, 95, (dz * 5) % 16);
            }
        }
        // Two houses, both still advertising.
        for (int side = 0; side < 2; side++) {
            int bx = side == 0 ? x0 + 2 : x0 + 28, bz = z0 + 24;
            int back = side == 0 ? bx + 1 : bx + 13;                   // the inside of the back wall
            shell(c, bx, y0 + 1, bz, bx + 14, y0 + 12, bz + 16, 159, side == 0 ? 14 : 11);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 13, y0 + 11, bz + 15, 0, 0);
            // The upper floor is a top slab, so what stands on it stands on it.
            fill(c, bx + 1, y0 + 6, bz + 1, bx + 13, y0 + 6, bz + 15, 44, 15);
            fill(c, bx + 1, y0 + 13, bz + 1, bx + 13, y0 + 13, bz + 15, 44, 7);
            int face = side == 0 ? bx + 14 : bx;
            fill(c, face, y0 + 2, bz + 4, face, y0 + 5, bz + 12, 95, side == 0 ? 14 : 3);
            // The doors are at street level (they were cut two blocks up the glass).
            fill(c, face, y0 + 1, bz + 7, face, y0 + 3, bz + 9, 0, 0);
            for (int dy = 8; dy <= 11; dy++)
                for (int dz = 3; dz <= 13; dz += 2) put(c, face, y0 + dy, bz + dz, 169, 0);
            // Slot machines, in rows, all of them still lit: the cabinet's base is the lamp.
            for (int dx = 2; dx <= 12; dx += 3) for (int dz = 2; dz <= 14; dz += 3) {
                put(c, bx + dx, y0 + 1, bz + dz, 169, 0);
                put(c, bx + dx, y0 + 2, bz + dz, 95, ((dx + dz) * 3) % 16);
                put(c, bx + dx, y0 + 3, bz + dz, 154, 0);
            }
            // A ladder up the back wall to the floor above, which nothing reached.
            fill(c, back, y0 + 1, bz + 1, back, y0 + 6, bz + 1, 65, side == 0 ? 5 : 4);
            fill(c, bx + 4, y0 + 7, bz + 4, bx + 10, y0 + 7, bz + 12, 171, 14);
            put(c, bx + 7, y0 + 11, bz + 8, 89, 0);                       // under the roof, not below it
            // The high-roller room: two card tables with stools, a wheel, a short bar.
            for (int k = 0; k < 2; k++) {
                int cx = bx + 5 + k * 3, cz = bz + 6 + k * 4;
                fill(c, cx, y0 + 7, cz, cx + 1, y0 + 7, cz, 85, 0);
                fill(c, cx, y0 + 8, cz, cx + 1, y0 + 8, cz, 171, 13);
                put(c, cx - 1, y0 + 7, cz, 156, 1);
                put(c, cx + 2, y0 + 7, cz, 156, 0);
            }
            for (int dx = 7; dx <= 9; dx++) for (int dz = 2; dz <= 4; dz++)
                put(c, bx + dx, y0 + 7, bz + dz, dx == 8 && dz == 3 ? 155 : 35, ((dx + dz) & 1) == 0 ? 14 : 15);
            fill(c, bx + 11, y0 + 7, bz + 9, bx + 12, y0 + 7, bz + 11, 43, 7);
            put(c, bx + 11, y0 + 8, bz + 10, 117, 0);
            graded(c, s, bx + 2, y0 + 7, bz + 2, r, STRIP_LOOT, side == 1);
            graded(c, s, bx + 12, y0 + 1, bz + 14, r, STRIP_LOOT, false);
            // The hall's zombie spawner went upstairs, into the dark back corner: in a lit hall it
            // would never have spawned anything.
            mob(c, side == 0 ? bx + 2 : bx + 12, y0 + 7, bz + 14, EntityType.ZOMBIE);
            mob(c, bx + 4, y0 + 7, bz + 10, EntityType.ZOMBIE);
        }
        // The tower, and the saucer on top of it.
        int tx = x0 + 22, tz = z0 + 10;
        for (int dy = 1; dy <= 34; dy++) for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 6.6) continue;
            if (d > 5.4) put(c, tx + dx, y0 + dy, tz + dz, (dy % 4 == 1 && ((dx + dz) & 1) == 0) ? 95 : 251,
                (dy % 4 == 1 && ((dx + dz) & 1) == 0) ? 3 : 8);
            else put(c, tx + dx, y0 + dy, tz + dz, 0, 0);
        }
        fill(c, tx, y0 + 1, tz, tx, y0 + 34, tz, 43, 8);            // core column: 43:8, was iron block
        fill(c, tx + 1, y0 + 1, tz, tx + 1, y0 + 33, tz, 65, 5);
        // Structure audit 2026-09-23: a front door where the boulevard ends (the tower had none), round
        // floors that stay inside the wall (the square ones cut it and stuck out), the ladder and the
        // pole carried through every floor (the hatches cut both), lamps under the ceilings instead of
        // in mid-room, and each floor furnished for what it was.
        fill(c, tx, y0 + 1, tz + 6, tx + 1, y0 + 2, tz + 6, 0, 0);
        ironDoor(c, tx, y0 + 1, tz + 6, 3, false);
        ironDoor(c, tx + 1, y0 + 1, tz + 6, 3, true);
        fill(c, tx, y0 + 3, tz + 6, tx + 1, y0 + 3, tz + 6, 169, 0);
        put(c, tx + 2, y0 + 2, tz + 7, 77, 3);
        put(c, tx - 1, y0 + 2, tz + 5, 77, 4);
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
            if (dx * dx + dz * dz <= 29) put(c, tx + dx, y0, tz + dz, 155, 0);      // the lobby floor
        fill(c, tx, y0 + 1, tz + 1, tx + 1, y0 + 1, tz + 5, 171, 14);
        put(c, tx + 1, y0 + 1, tz, 65, 5);
        fill(c, tx - 3, y0 + 1, tz - 1, tx - 3, y0 + 1, tz + 1, 155, 0);             // the cashier
        fill(c, tx - 3, y0 + 2, tz - 1, tx - 3, y0 + 2, tz + 1, 101, 0);
        put(c, tx - 2, y0 + 1, tz + 4, 140, 0);
        put(c, tx + 3, y0 + 1, tz + 4, 140, 0);
        for (int dy = 6; dy <= 30; dy += 6) {
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
                if (dx * dx + dz * dz <= 29) put(c, tx + dx, y0 + dy, tz + dz, 44, 15);
            put(c, tx, y0 + dy, tz, 43, 8);
            put(c, tx + 1, y0 + dy, tz, 65, 5);
            put(c, tx - 4, y0 + dy, tz, 89, 0);                          // set in the floor: lights the room below
            int f = y0 + dy + 1;
            if (dy == 6) {                                              // the lounge
                fill(c, tx - 3, f, tz - 3, tx - 1, f, tz - 3, 43, 7);
                put(c, tx - 2, f + 1, tz - 3, 117, 0);
                put(c, tx - 2, f, tz - 4, 118, 3);
                fill(c, tx - 3, f, tz - 2, tx - 1, f, tz - 2, 156, 2);
                fill(c, tx + 1, f, tz + 2, tx + 3, f, tz + 3, 171, 14);
                put(c, tx + 2, f, tz + 2, 44, 7);
            } else if (dy == 12) {                                      // the suite
                put(c, tx + 2, f, tz - 3, 26, 0); put(c, tx + 2, f, tz - 2, 26, 8);
                put(c, tx + 3, f, tz - 3, 26, 0); put(c, tx + 3, f, tz - 2, 26, 8);
                fill(c, tx - 2, f, tz - 4, tx - 1, f + 1, tz - 4, 47, 0);
                fill(c, tx - 1, f, tz + 2, tx + 1, f, tz + 3, 171, 14);
                put(c, tx + 4, f, tz - 1, 140, 0);
                mob(c, tx - 3, f, tz + 3, EntityType.ZOMBIE);
            } else if (dy == 18) {                                      // the counting room
                put(c, tx + 3, f, tz + 1, 101, 0);
                put(c, tx + 3, f, tz + 3, 101, 0);
                fill(c, tx - 3, f, tz - 2, tx - 3, f, tz - 1, 44, 15);
                put(c, tx - 2, f, tz - 2, 156, 0);
                graded(c, s, tx + 4, f, tz + 2, r, STRIP_LOOT, false);
            } else if (dy == 24) {                                      // security
                fill(c, tx - 5, f + 1, tz - 1, tx - 5, f + 2, tz + 1, 160, 15);
                fill(c, tx - 4, f, tz - 1, tx - 4, f, tz + 1, 43, 7);
                put(c, tx - 4, f, tz + 2, 25, 0);
                put(c, tx - 3, f, tz, 156, 0);
            } else {                                                    // the machine room
                fill(c, tx - 3, f, tz - 2, tx - 3, f, tz - 1, 23, 1);
                put(c, tx - 3, f + 1, tz - 2, 154, 0);
                put(c, tx + 3, f, tz - 2, 33, 1);
                put(c, tx + 3, f, tz + 2, 33, 1);
            }
        }
        // The saucer. Each course reaches in to its smaller neighbour so the courses overlap (they only
        // touched at the corners: the lid floated, and the floor of the room was full of holes).
        for (int dy = 0; dy <= 5; dy++) {
            double rr = saucerR(dy);
            double rIn = Math.min(rr, Math.min(saucerR(dy - 1), saucerR(dy + 1))) - 1.1;
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr) continue;
                boolean skin = d > rIn || dy == 0 || dy == 5;
                put(c, tx + dx, y0 + 35 + dy, tz + dz, skin ? (dy == 2 ? 95 : 251) : 0, skin ? (dy == 2 ? 3 : 8) : 0);
            }
        }
        // The running lights sit in the rim (they hung in the air four blocks under it).
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            put(c, tx + (int) Math.round(Math.cos(ang) * 11), y0 + 38, tz + (int) Math.round(Math.sin(ang) * 11), 169, 0);
        }
        put(c, tx - 4, y0 + 35, tz, 89, 0);                              // the machine room's light
        // THE SURPRISE: the top floor, which has no stair to it and one ladder nobody found.
        fill(c, tx - 4, y0 + 36, tz - 4, tx + 4, y0 + 38, tz + 4, 0, 0);
        fill(c, tx - 4, y0 + 36, tz - 4, tx + 4, y0 + 36, tz + 4, 251, 4);   // yellow concrete, was gold block
        fill(c, tx + 1, y0 + 31, tz, tx + 1, y0 + 36, tz, 0, 0);
        fill(c, tx + 1, y0 + 31, tz, tx + 1, y0 + 36, tz, 65, 5);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            put(c, tx + (int) Math.round(Math.cos(ang) * 3), y0 + 39, tz + (int) Math.round(Math.sin(ang) * 3), 169, 0);
        }
        put(c, tx - 3, y0 + 37, tz - 3, 84, 0);
        put(c, tx + 3, y0 + 37, tz + 3, 145, 0);
        mob(c, tx, y0 + 37, tz + 3, EntityType.SKELETON);
        trove(c, tx - 3, y0 + 37, tz + 3, r, STRIP_LOOT, STRIP_TROVE, 15 + r.nextInt(6));
    }

    // == 35. Vault 44 ============================================================
    // A door in a hillside the size of a house, rolled aside, and behind it a corridor
    // that keeps going. Somebody sealed this from the outside and the numbers on the
    // board by the atrium say they did it on day one. The east wing is walled off in
    // plate steel. Whatever the experiment was, the experiment is in there.

    private static final int[][] VAULT_LOOT = {
        {265, 0, 13, 4, 12}, { 42, 0,  9, 2,  6}, {331, 0, 12, 6, 18}, {348, 0, 10, 4, 12},
        {297, 0, 12, 3,  9}, {396, 0,  8, 1,  4}, {131, 0,  7, 1,  3}, { 69, 0,  9, 2,  8},
        {306, 0,  6, 1,  1}, {264, 0,  7, 1,  2}, {384, 0,  8, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] VAULT_TROVE = {
        {448, 0, 1, 1, 1}, {310, 0, 1, 1, 1}, {311, 0, 1, 1, 1}, {313, 0, 1, 1, 1},
    };

    private static void vault(Chunk c, Terrain t) {
        Site s = deep(t, c, VAULT_CELL, 0x5641554CL, 45, 37, 18, Megaliths.RANK_VAULT);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 44, z1 = z0 + 36, y1 = s.y + 17;
        fill(c, x0, y0, z0, x1, y1, z1, 251, 8);
        // The door, at the end of an adit from the surface.
        int dx0 = x0 + 4, dz0 = z0 + 2;
        fill(c, dx0 - 3, y0 + 1, dz0, dx0 + 3, y0 + 7, dz0 + 3, 0, 0);
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) {
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d > 4.4) continue;
            // Rim and hub: smooth stone double slab 43:8 (was iron block); hazard stripes between.
            int id = d > 3.6 ? 43 : (d > 1.4 ? 251 : 43);
            int dat = d > 3.6 ? 8 : (d > 1.4 ? ((dx + dy) % 3 == 0 ? 4 : 15) : 8);
            put(c, dx0 + dx + 5, y0 + 4 + dy, dz0, id, dat);       // rolled aside, to one side
        }
        // Structure audit 2026-09-23: the shaft comes down to the adit's floor and the adit is cut after
        // it (the ladder stopped a block above the adit and the shaft's own wall sealed it: nobody could
        // get in). The rolled door gets a pocket in front of it so its face shows.
        riser(c, t, dx0, dz0 - 2, y0 + 1, 251, 8);
        clearMouth(c, t, dx0, dz0 - 2);
        fill(c, dx0 - 1, y0 + 1, dz0 - 1, dx0 + 1, y0 + 4, dz0 - 1, 0, 0);
        fill(c, x0 + 8, y0 + 2, z0 + 3, x0 + 13, y0 + 8, z0 + 3, 0, 0);
        fill(c, x0 + 8, y0 + 1, z0 + 3, x0 + 13, y0 + 1, z0 + 3, 251, 0);
        // The atrium, with the number on the wall.
        fill(c, x0 + 2, y0 + 1, z0 + 4, x0 + 18, y0 + 9, z0 + 18, 0, 0);
        fill(c, x0 + 2, y0 + 1, z0 + 4, x0 + 18, y0 + 1, z0 + 18, 251, 0);
        fill(c, x0 + 2, y0 + 10, z0 + 4, x0 + 18, y0 + 10, z0 + 18, 44, 7);
        fill(c, x0 + 2, y0 + 4, z0 + 4, x0 + 2, y0 + 8, z0 + 18, 251, 15);
        // "44", read facing the wall (two closed squares were drawn before, structure audit 2026-09-23).
        for (int zr = z0 + 8; zr <= z0 + 13; zr += 5) {
            fill(c, x0 + 2, y0 + 4, zr, x0 + 2, y0 + 8, zr, 251, 4);
            fill(c, x0 + 2, y0 + 6, zr + 2, x0 + 2, y0 + 8, zr + 2, 251, 4);
            put(c, x0 + 2, y0 + 6, zr + 1, 251, 4);
        }
        for (int dx = 4; dx <= 16; dx += 4) put(c, x0 + dx, y0 + 9, z0 + 11, 89, 0);
        for (int lx = 6; lx <= 14; lx += 8) for (int lz = 7; lz <= 15; lz += 8) put(c, x0 + lx, y0 + 10, z0 + lz, 169, 0);
        for (int bxz = 8; bxz <= 12; bxz += 4) {                   // benches facing the board
            fill(c, x0 + bxz, y0 + 2, z0 + 7, x0 + bxz, y0 + 2, z0 + 9, 156, 0);
            fill(c, x0 + bxz, y0 + 2, z0 + 13, x0 + bxz, y0 + 2, z0 + 15, 156, 0);
        }
        fill(c, dx0, y0 + 1, dz0 + 3, dx0 + 1, y0 + 4, z0 + 4, 0, 0);
        // A bank of steel lockers along the atrium's south wall, doors hanging open (structure audit
        // 2026-09-23: the reception hall held a spawner, a chest and the board).
        fill(c, x0 + 6, y0 + 2, z0 + 18, x0 + 12, y0 + 3, z0 + 18, 251, 8);
        fill(c, x0 + 6, y0 + 2, z0 + 17, x0 + 12, y0 + 3, z0 + 17, 167, 4);
        // Furnishings, chests and spawners stand on the floors throughout (they were set into them).
        graded(c, s, x0 + 3, y0 + 2, z0 + 17, r, VAULT_LOOT, false);
        mob(c, x0 + 10, y0 + 2, z0 + 11, EntityType.ZOMBIE);
        // The office glass looks down into a bay off the atrium (it looked into solid concrete).
        fill(c, x0 + 19, y0 + 2, z0 + 16, x0 + 20, y0 + 14, z0 + 20, 0, 0);
        fill(c, x0 + 19, y0 + 1, z0 + 16, x0 + 20, y0 + 1, z0 + 20, 251, 0);
        put(c, x0 + 19, y0 + 14, z0 + 18, 89, 0);
        // The spine corridor, hazard-striped, with the wings off it.
        fill(c, x0 + 18, y0 + 1, z0 + 10, x1 - 2, y0 + 5, z0 + 13, 0, 0);
        fill(c, x0 + 18, y0 + 1, z0 + 10, x1 - 2, y0 + 1, z0 + 13, 251, 8);
        for (int dx = 18; dx <= 42; dx++) {
            put(c, x0 + dx, y0 + 1, z0 + 10, (dx & 1) == 0 ? 251 : 251, (dx & 1) == 0 ? 4 : 15);
            put(c, x0 + dx, y0 + 1, z0 + 13, (dx & 1) == 0 ? 251 : 251, (dx & 1) == 0 ? 4 : 15);
            if (dx % 5 == 0) put(c, x0 + dx, y0 + 5, z0 + 11, 89, 0);
        }
        // Quarters, canteen, clinic. The two north wings get a wall to the corridor with a doorway in it
        // (they were open bays, and their connector cut pits in the corridor floor).
        int[][] wings = {{x0 + 20, z0 + 2, 0}, {x0 + 32, z0 + 2, 1}, {x0 + 20, z0 + 21, 2}};
        for (int[] wg : wings) {
            int bx = wg[0], bz = wg[1];
            fill(c, bx, y0 + 1, bz, bx + 10, y0 + 5, bz + 7, 0, 0);
            fill(c, bx, y0 + 1, bz, bx + 10, y0 + 1, bz + 7, 251, wg[2] == 2 ? 0 : 8);
            if (wg[2] == 2) fill(c, bx + 4, y0 + 2, bz - 8, bx + 5, y0 + 4, bz - 1, 0, 0);
            else {
                fill(c, bx, y0 + 2, bz + 7, bx + 10, y0 + 5, bz + 7, 251, 8);
                fill(c, bx + 4, y0 + 2, bz + 7, bx + 5, y0 + 4, bz + 7, 0, 0);
            }
            if (wg[2] == 0) for (int dz = 1; dz <= 6; dz += 2) {
                put(c, bx + 1, y0 + 2, bz + dz, 26, 3); put(c, bx + 2, y0 + 2, bz + dz, 26, 11);
                put(c, bx + 8, y0 + 2, bz + dz, 26, 3); put(c, bx + 9, y0 + 2, bz + dz, 26, 11);
            }
            if (wg[2] == 1) for (int dx = 2; dx <= 8; dx += 3) {
                fill(c, bx + dx, y0 + 2, bz + 2, bx + dx, y0 + 2, bz + 5, 44, 15);
                put(c, bx + dx - 1, y0 + 2, bz + 2, 53, 1); put(c, bx + dx + 1, y0 + 2, bz + 5, 53, 0);
            }
            if (wg[2] == 2) for (int dx = 2; dx <= 8; dx += 3) {
                fill(c, bx + dx, y0 + 2, bz + 2, bx + dx, y0 + 2, bz + 3, 155, 0);
                put(c, bx + dx, y0 + 3, bz + 2, 117, 0); put(c, bx + dx, y0 + 2, bz + 4, 118, 0);
            }
            put(c, bx + 5, y0 + 5, bz + 3, 89, 0);
            put(c, bx + 2, y0 + 5, bz + 5, 89, 0);
            graded(c, s, bx + 9, y0 + 2, bz + 6, r, VAULT_LOOT, wg[2] == 1);
            // (The canteen's spawner stood inside a table; it is in the corner now.)
            mob(c, wg[2] == 1 ? bx + 9 : bx + 5, y0 + 2, wg[2] == 1 ? bz + 1 : bz + 5,
                wg[2] == 2 ? EntityType.SILVERFISH : EntityType.ZOMBIE);
        }
        // The east wing, walled off in plate. Whatever this was for, it is still in there.
        fill(c, x0 + 32, y0 + 1, z0 + 21, x1 - 2, y0 + 6, z1 - 2, 0, 0);
        fill(c, x0 + 32, y0 + 1, z0 + 21, x1 - 2, y0 + 1, z1 - 2, 251, 15);
        fill(c, x0 + 32, y0 + 1, z0 + 20, x1 - 2, y0 + 6, z0 + 20, 43, 8);   // plate: 43:8, was iron block
        // A hazard-striped antechamber off the corridor stands before the plate, and the viewing slot is
        // at eye level: the plate and its bars faced solid concrete.
        fill(c, x0 + 35, y0 + 2, z0 + 14, x0 + 40, y0 + 5, z0 + 19, 0, 0);
        for (int dx = 35; dx <= 40; dx++) put(c, x0 + dx, y0 + 1, z0 + 19, 251, (dx & 1) == 0 ? 4 : 15);
        put(c, x0 + 38, y0 + 5, z0 + 16, 89, 0);
        fill(c, x0 + 36, y0 + 2, z0 + 20, x0 + 37, y0 + 4, z0 + 20, 101, 0);
        for (int i = 0; i < 28; i++) {                                   // webs hang from the ceiling
            int wx = x0 + 33 + r.nextInt(9), wy = r.nextInt(4), wz = z0 + 22 + r.nextInt(12);
            put(c, wx, y0 + 6, wz, 30, 0);
        }
        for (int dz = 22; dz <= 32; dz += 5) {
            fill(c, x0 + 34, y0 + 2, z0 + dz, x0 + 34, y0 + 5, z0 + dz, 101, 0);
            fill(c, x0 + 40, y0 + 2, z0 + dz, x0 + 40, y0 + 5, z0 + dz, 101, 0);
        }
        mob(c, x0 + 36, y0 + 2, z0 + 26, EntityType.HUSK);
        mob(c, x0 + 40, y0 + 2, z0 + 30, EntityType.CAVE_SPIDER);
        mob(c, x0 + 34, y0 + 2, z0 + 32, EntityType.CAVE_SPIDER);
        graded(c, s, x0 + 41, y0 + 2, z1 - 3, r, VAULT_LOOT, true);
        // THE SURPRISE: the overseer's office, up behind glass, still locked.
        int ox = x0 + 22, oz = z0 + 15;
        fill(c, ox, y0 + 10, oz, ox + 12, y0 + 15, oz + 10, 0, 0);
        shell(c, ox - 1, y0 + 9, oz - 1, ox + 13, y0 + 16, oz + 11, 251, 8);
        fill(c, ox, y0 + 10, oz, ox + 12, y0 + 10, oz + 10, 251, 0);
        fill(c, ox - 1, y0 + 11, oz + 2, ox - 1, y0 + 14, oz + 8, 95, 3);
        // The way up: the old five steps climbed away from the office into a dead end, nine short. A
        // quartz flight of nine rises east along the corridor's south side, in its own stairwell, to a
        // doorway in the office's north wall (structure audit 2026-09-23).
        for (int k = 0; k <= 8; k++) {
            int sx = x0 + 26 + k;
            if (k > 0) fill(c, sx, y0 + 2, z0 + 12, sx, y0 + 1 + k, z0 + 13, 251, 8);
            // The ninth is the landing, level with the office floor (a stair there ran into the wall).
            fill(c, sx, y0 + 2 + k, z0 + 12, sx, y0 + 2 + k, z0 + 13, k == 8 ? 251 : 156, k == 8 ? 8 : 0);
            fill(c, sx, y0 + 3 + k, z0 + 12, sx, y0 + 14, z0 + 13, 0, 0);
        }
        fill(c, x0 + 33, y0 + 11, z0 + 14, x0 + 34, y0 + 13, z0 + 14, 0, 0);
        fill(c, ox + 4, y0 + 11, oz + 7, ox + 8, y0 + 11, oz + 7, 44, 15);
        put(c, ox + 6, y0 + 12, oz + 7, 251, 15);
        put(c, ox + 6, y0 + 13, oz + 7, 95, 5);
        put(c, ox + 5, y0 + 11, oz + 6, 156, 3);
        fill(c, ox + 1, y0 + 11, oz + 1, ox + 1, y0 + 13, oz + 3, 47, 0);
        put(c, ox + 6, y0 + 15, oz + 5, 89, 0);
        put(c, ox + 2, y0 + 15, oz + 5, 89, 0);
        mob(c, ox + 10, y0 + 11, oz + 2, EntityType.HUSK);
        trove(c, ox + 6, y0 + 11, oz + 6, r, VAULT_LOOT, VAULT_TROVE, 14 + r.nextInt(6));
    }

    // == 36. The Flatpack ========================================================
    // A blue box with a yellow band round it and half the roof gone. The arrows are still
    // on the floor and they still only go one way: through the showrooms upstairs, down
    // into the market hall, along the racking, and out past a restaurant. One of the
    // showroom flats was glassed in for a photograph and never opened again.

    private static final int[][] IKEA_LOOT = {
        {126, 0, 14, 8, 24}, {136, 0, 11, 4, 12}, {359, 0,  7, 1,  1}, {351, 4, 12, 6, 18},
        {340, 0,  9,  2, 6}, {367, 0, 11, 4, 12}, {263, 0, 11, 6, 18}, {265, 0, 10, 3,  9},
        {297, 0, 10,  2, 6}, {171, 4, 12, 6, 18}, {384, 0,  6, 1,  3}, {403, 0,  4, 1,  1},
    };
    private static final int[][] IKEA_TROVE = {
        { 58, 0, 1, 1, 1}, { 47, 0, 1, 6, 10}, {355, 0, 1, 2, 2}, {390, 0, 1, 3, 5},
    };

    /**
     * Where the Flatpack's roof is gone: one ragged opening over the middle of the showrooms (dx, dz from
     * the site corner), which a cell also counts as when it would be left as a one-wide spur.
     */
    private static boolean roofGone(long seed, int dx, int dz) {
        return roofHole(seed, dx, dz) || (roofHole(seed, dx - 1, dz) && roofHole(seed, dx + 1, dz))
            || (roofHole(seed, dx, dz - 1) && roofHole(seed, dx, dz + 1));
    }

    private static boolean roofHole(long seed, int dx, int dz) {
        if (dx < 3 || dx > 37 || dz < 3 || dz > 27) return false;
        double ex = (dx - 21.5) / 10.0, ez = (dz - 14.5) / 9.0;
        double jitter = ((Terrain.mix(seed + dx * 31L + dz * 17L) >>> 40) & 7) / 20.0 - 0.15;
        return ex * ex + ez * ez <= 1.0 + jitter;
    }

    private static void flatpack(Chunk c, Terrain t) {
        Site s = ground(t, c, IKEA_CELL, 0x494B4541L, 41, 37, 7, Megaliths.RANK_IKEA);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 40, z1 = z0 + 36;
        for (int dx = 0; dx <= 40; dx++) for (int dz = 0; dz <= 36; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);
        // The box: blue, with the band, and a car park in front of it.
        shell(c, x0 + 2, y0 + 1, z0 + 2, x1 - 2, y0 + 14, z0 + 28, 251, 11);
        fill(c, x0 + 3, y0 + 1, z0 + 3, x1 - 3, y0 + 13, z0 + 27, 0, 0);
        fill(c, x0 + 2, y0 + 9, z0 + 2, x1 - 2, y0 + 9, z0 + 2, 251, 4);
        fill(c, x0 + 2, y0 + 9, z0 + 28, x1 - 2, y0 + 9, z0 + 28, 251, 4);
        fill(c, x0 + 2, y0 + 9, z0 + 2, x0 + 2, y0 + 9, z0 + 28, 251, 4);
        fill(c, x1 - 2, y0 + 9, z0 + 2, x1 - 2, y0 + 9, z0 + 28, 251, 4);
        fill(c, x0 + 3, y0 + 7, z0 + 3, x1 - 3, y0 + 7, z0 + 27, 5, 2);       // the upper floor
        fill(c, x0 + 3, y0 + 14, z0 + 3, x1 - 3, y0 + 14, z0 + 27, 44, 7);
        fill(c, x0 + 18, y0 + 1, z0 + 28, x0 + 22, y0 + 5, z0 + 28, 0, 0);
        for (int dx = 4; dx <= 36; dx += 8) for (int dz = 30; dz <= 34; dz += 2)
            fill(c, x0 + dx, y0, z0 + dz, x0 + dx + 4, y0, z0 + dz, 251, 7);
        // The sign outside, on two legs.
        // Legs: smooth stone double slab 43:8 (was iron block).
        fill(c, x0 + 6, y0 + 1, z0 + 32, x0 + 6, y0 + 10, z0 + 32, 43, 8);
        fill(c, x0 + 12, y0 + 1, z0 + 32, x0 + 12, y0 + 10, z0 + 32, 43, 8);
        fill(c, x0 + 6, y0 + 8, z0 + 32, x0 + 12, y0 + 12, z0 + 32, 251, 4);
        fill(c, x0 + 7, y0 + 9, z0 + 32, x0 + 11, y0 + 11, z0 + 32, 251, 11);
        // The roof, half of it on the floor. Structure audit 2026-09-23: seventy scattered single blocks
        // made a sieve of pinholes and loose water sources on the market floor. The roof is gone in one
        // ragged opening over the showrooms now, and what came down lies on the upper floor under it:
        // slabs and pieces of the blue box, a little growth, the partition tops knocked off. The same
        // draws are taken here, in the same order, and used once the showrooms stand.
        for (int dx = 3; dx <= 37; dx++) for (int dz = 3; dz <= 27; dz++)
            if (roofGone(s.seed, dx, dz)) put(c, x0 + dx, y0 + 14, z0 + dz, 0, 0);
        int[][] fall = new int[70][5];
        for (int i = 0; i < 70; i++) {
            int gx = x0 + 12 + r.nextInt(20), gz = z0 + 6 + r.nextInt(18);
            fall[i][0] = gx; fall[i][1] = gz;
            fall[i][2] = r.nextInt(3) == 0 ? 1 : 0;                         // a piece of the box
            fall[i][3] = r.nextInt(4) == 0 ? 1 + r.nextInt(2) : 0;          // growth on it
            fall[i][4] = r.nextInt(5) == 0 ? 1 : 0;                         // knocks a partition top off
        }
        // Upstairs: the showroom maze, with the arrows still on it.
        for (int gx = 0; gx < 4; gx++) for (int gz = 0; gz < 3; gz++) {
            int bx = x0 + 4 + gx * 9, bz = z0 + 4 + gz * 8;
            shell(c, bx, y0 + 8, bz, bx + 7, y0 + 10, bz + 6, 5, (gx + gz) % 6);
            fill(c, bx + 1, y0 + 8, bz + 1, bx + 6, y0 + 12, bz + 5, 0, 0);
            fill(c, bx + 2, y0 + 8, bz, bx + 4, y0 + 10, bz, 0, 0);
            // The rug lies on the upper floor; it had replaced the planks, a carpet over the market's air.
            // It stops short of the stair hole to the market.
            for (int rx = bx + 1; rx <= bx + 6; rx++) for (int rz = bz + 1; rz <= bz + 5; rz++)
                if (rx < x0 + 25 || rx > x0 + 26 || rz < z0 + 6 || rz > z0 + 8)
                    put(c, rx, y0 + 8, rz, 171, (gx * 3 + gz * 5) % 16);
            // A bed made of two halves of the same bed (the head was put beside the foot).
            put(c, bx + 2, y0 + 8, bz + 2, 26, 0); put(c, bx + 2, y0 + 8, bz + 3, 26, 8);
            put(c, bx + 5, y0 + 8, bz + 4, 58, 0);
            put(c, bx + 5, y0 + 8, bz + 2, 47, 0);
            put(c, bx + 3, y0 + 10, bz + 6, 89, 0);                  // a wall light (it floated over the room)
            // A sofa against the back wall and a pot plant: a showroom was a bed and a shelf.
            fill(c, bx + 3, y0 + 8, bz + 5, bx + 4, y0 + 8, bz + 5, 53, 2);
            put(c, bx + 1, y0 + 8, bz + 5, 140, 0);
            if (((gx + gz) & 1) == 0) graded(c, s, bx + 6, y0 + 8, bz + 5, r, IKEA_LOOT, gx == 3);
            if (((gx * 3 + gz) & 3) == 1) mob(c, bx + 3, y0 + 8, bz + 3, EntityType.ZOMBIE);
        }
        // The last column of showrooms had overwritten the east facade and its band with planks.
        fill(c, x1 - 2, y0 + 8, z0 + 3, x1 - 2, y0 + 10, z0 + 27, 251, 11);
        fill(c, x1 - 2, y0 + 9, z0 + 2, x1 - 2, y0 + 9, z0 + 28, 251, 4);
        // The arrows run down the corridor between the showrooms (they crossed three showrooms' floors).
        for (int dz = 4; dz <= 26; dz++) if (dz % 3 != 0) put(c, x0 + 30, y0 + 7, z0 + dz, 251, 4);
        // What came down, where it came down.
        for (int dx = 3; dx <= 37; dx++) for (int dz = 3; dz <= 27; dz++) {
            if (!roofGone(s.seed, dx, dz) || idAt(c, x0 + dx, y0 + 10, z0 + dz) != 5) continue;
            put(c, x0 + dx, y0 + 10, z0 + dz, 0, 0);
            if ((Terrain.mix(s.seed + dx * 7L + dz * 13L) & 3) == 0 && idAt(c, x0 + dx, y0 + 9, z0 + dz) == 5)
                put(c, x0 + dx, y0 + 9, z0 + dz, 0, 0);
        }
        for (int[] f : fall) {
            int gx = f[0], gz = f[1];
            if (!roofGone(s.seed, gx - x0, gz - z0)) continue;
            if (gx >= x0 + 24 && gx <= x0 + 27 && gz >= z0 + 4 && gz <= z0 + 9) continue;   // the stairhead
            if (f[4] == 1 && idAt(c, gx, y0 + 9, gz) == 5) put(c, gx, y0 + 9, gz, 0, 0);
            int py = rest(c, gx, y0 + 10, gz, y0 + 8);
            if (py > y0 + 9 || idAt(c, gx, py, gz) != 0) continue;
            put(c, gx, py, gz, f[2] == 1 ? 251 : 44, f[2] == 1 ? 11 : 7);
            if (f[3] != 0 && py < y0 + 9) put(c, gx, py + 1, gz, 18, 4);   // leaves that never decay
        }
        // The stair down to the market (structure audit 2026-09-23): it was cut inside the glass flat,
        // which is built later and wiped every step, so nothing joined the floors. It runs down the free
        // aisle between the shelving and the flat now, from a hole in showroom (2,0).
        fill(c, x0 + 25, y0 + 7, z0 + 7, x0 + 26, y0 + 7, z0 + 8, 0, 0);       // the hole to the market
        for (int i = 0; i <= 6; i++) fill(c, x0 + 25, y0 + 1 + i, z0 + 12 - i, x0 + 26, y0 + 1 + i, z0 + 12 - i, 109, 3);
        // The flight stands on stone brick (its middle steps met only at their edges, in the air).
        for (int i = 1; i <= 6; i++) fill(c, x0 + 25, y0 + 1, z0 + 12 - i, x0 + 26, y0 + i, z0 + 12 - i, 98, 0);
        // And the arrows lead to it: along the north corridor and in at showroom (2,0)'s door.
        for (int dx = 29; dx >= 25; dx -= 2) put(c, x0 + dx, y0 + 7, z0 + 3, 251, 4);
        put(c, x0 + 25, y0 + 7, z0 + 4, 251, 4);
        // Downstairs: market shelving, then the racking, then the restaurant.
        for (int dz = 5; dz <= 15; dz += 3) {
            fill(c, x0 + 5, y0 + 1, z0 + dz, x0 + 24, y0 + 4, z0 + dz, 5, 2);
            for (int dx = 6; dx <= 23; dx += 2) {
                put(c, x0 + dx, y0 + 2, z0 + dz, 171, (dx + dz) % 16);
                put(c, x0 + dx, y0 + 4, z0 + dz, 35, (dx * 3 + dz) % 16);
            }
        }
        for (int dz = 18; dz <= 26; dz += 4) {
            fill(c, x0 + 5, y0 + 1, z0 + dz, x0 + 24, y0 + 6, z0 + dz, 101, 0);
            for (int dx = 6; dx <= 23; dx += 3) {
                fill(c, x0 + dx, y0 + 2, z0 + dz, x0 + dx + 1, y0 + 2, z0 + dz, 44, 0);
                fill(c, x0 + dx, y0 + 5, z0 + dz, x0 + dx + 1, y0 + 5, z0 + dz, 44, 0);
            }
            mob(c, x0 + 14, y0 + 1, z0 + dz, EntityType.SPIDER);
        }
        // Store lights under the upper floor over the aisles, a few tubes out, and kept back from the
        // racks' spider spawners (the hall was dark from missing fittings).
        for (int[] l : new int[][]{{8, 7}, {14, 7}, {8, 10}, {14, 10}, {8, 13}, {14, 13}, {20, 13}, {8, 16},
                                   {20, 16}, {8, 20}, {20, 20}, {8, 24}, {20, 24}})
            put(c, x0 + l[0], y0 + 6, z0 + l[1], 169, 0);
        // The restaurant: its floor is a step up, and the tables, chairs, stove and chest stand on it
        // (they were set into it, and the chest had taken a chair's place).
        fill(c, x0 + 28, y0 + 1, z0 + 18, x1 - 3, y0 + 6, z0 + 27, 0, 0);
        fill(c, x0 + 28, y0 + 1, z0 + 18, x1 - 3, y0 + 1, z0 + 27, 155, 0);
        for (int dx = 30; dx <= 36; dx += 3) for (int dz = 20; dz <= 26; dz += 3) {
            put(c, x0 + dx, y0 + 2, z0 + dz, 85, 0);
            put(c, x0 + dx, y0 + 3, z0 + dz, 44, 7);
            put(c, x0 + dx - 1, y0 + 2, z0 + dz, 53, 1);
        }
        put(c, x0 + 32, y0 + 6, z0 + 22, 89, 0);
        put(c, x0 + 29, y0 + 2, z0 + 19, 61, 2);
        put(c, x0 + 30, y0 + 2, z0 + 19, 118, 3);
        graded(c, s, x0 + 28, y0 + 2, z0 + 25, r, IKEA_LOOT, false);
        graded(c, s, x0 + 6, y0 + 1, z0 + 5, r, IKEA_LOOT, true);
        mob(c, x0 + 20, y0 + 1, z0 + 8, EntityType.ZOMBIE);
        mob(c, x0 + 33, y0 + 2, z0 + 24, EntityType.ZOMBIE);
        // THE SURPRISE: the flat they glassed in for the catalogue, never opened since.
        int fx = x0 + 28, fz = z0 + 4;
        shell(c, fx, y0 + 1, fz, fx + 9, y0 + 6, fz + 10, 20, 0);
        fill(c, fx + 1, y0 + 1, fz + 1, fx + 8, y0 + 5, fz + 9, 0, 0);
        fill(c, fx + 1, y0 + 1, fz + 1, fx + 8, y0 + 1, fz + 9, 5, 2);
        fill(c, fx + 1, y0 + 1, fz + 1, fx + 8, y0 + 1, fz + 9, 171, 14);
        fill(c, fx + 1, y0 + 6, fz + 1, fx + 8, y0 + 6, fz + 9, 5, 2);
        put(c, fx + 2, y0 + 2, fz + 2, 26, 0); put(c, fx + 2, y0 + 2, fz + 3, 26, 8);
        fill(c, fx + 6, y0 + 2, fz + 2, fx + 7, y0 + 4, fz + 4, 47, 0);
        put(c, fx + 4, y0 + 2, fz + 6, 58, 0);
        put(c, fx + 3, y0 + 2, fz + 6, 53, 1);
        put(c, fx + 5, y0 + 2, fz + 6, 53, 0);
        put(c, fx + 7, y0 + 2, fz + 8, 140, 0);
        put(c, fx + 4, y0 + 5, fz + 5, 169, 0);
        put(c, fx + 2, y0 + 5, fz + 8, 169, 0);
        trove(c, fx + 7, y0 + 2, fz + 6, r, IKEA_LOOT, IKEA_TROVE, 13 + r.nextInt(6));
    }

    // == 37. The Golden Arches ===================================================
    // Brick to the waist, white above it, a red band, and a pole out front with two gold
    // arcs on it that have not tarnished. Drive-through round the back with the board
    // still up. Tubes in the side yard going nowhere. The walk-in at the back of the
    // kitchen is still, somehow, at temperature, and the door has not been opened.

    private static final int[][] MCD_LOOT = {
        {424, 0, 12, 2,  6}, {412, 0, 11, 2,  6}, {413, 0,  7, 1,  2}, {320, 0, 13, 3,  9},
        {367, 0, 12, 4, 12}, {263, 0, 11, 6, 18}, {351, 14, 10, 6, 18}, {265, 0, 10, 3,  9},
        { 41, 0,  5, 1,  1}, {266, 0,  9, 2,  6}, {384, 0,  6, 1,  3}, {403, 0,  4, 1,  1},
    };
    private static final int[][] MCD_TROVE = {
        {354, 0, 1, 2, 3}, {400, 0, 1, 6, 10}, { 41, 0, 1, 2, 3}, {322, 0, 1, 1, 2},
    };

    private static void arches(Chunk c, Terrain t) {
        Site s = ground(t, c, MCD_CELL, 0x4D43444EL, 27, 23, 6, Megaliths.RANK_MCD);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 26, z1 = z0 + 22;
        for (int dx = 0; dx <= 26; dx++) for (int dz = 0; dz <= 22; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);
        for (int dx = 1; dx <= 25; dx += 4) fill(c, x0 + dx, y0, z0 + 18, x0 + dx, y0, z1 - 1, 251, 0);
        // The restaurant.
        int bx = x0 + 2, bz = z0 + 2;
        shell(c, bx, y0 + 1, bz, bx + 18, y0 + 3, bz + 12, 45, 0);
        shell(c, bx, y0 + 4, bz, bx + 18, y0 + 7, bz + 12, 251, 0);
        fill(c, bx, y0 + 6, bz, bx + 18, y0 + 6, bz + 12, 251, 14);
        fill(c, bx + 1, y0 + 1, bz + 1, bx + 17, y0 + 7, bz + 11, 0, 0);
        fill(c, bx + 1, y0 + 8, bz + 1, bx + 17, y0 + 8, bz + 11, 44, 7);
        fill(c, bx, y0 + 8, bz, bx + 18, y0 + 8, bz + 12, 44, 7);
        for (int dz = 2; dz <= 10; dz += 2) {
            fill(c, bx, y0 + 2, bz + dz, bx, y0 + 5, bz + dz, 102, 0);
            fill(c, bx + 18, y0 + 2, bz + dz, bx + 18, y0 + 5, bz + dz, 102, 0);
        }
        fill(c, bx + 8, y0 + 1, bz + 12, bx + 10, y0 + 4, bz + 12, 0, 0);
        // Dining room: booths down one side, the counter across the back. The booths' red backs stand
        // behind the seats (they sat on top of them), and the menu board hangs from the roof on bars
        // (it floated over the counter); the room has lights over the booths and the fryers
        // (structure audit 2026-09-23).
        for (int dz = 2; dz <= 10; dz += 3) {
            put(c, bx + 2, y0 + 1, bz + dz, 53, 1); fill(c, bx + 1, y0 + 1, bz + dz, bx + 1, y0 + 2, bz + dz, 35, 14);
            put(c, bx + 5, y0 + 1, bz + dz, 53, 0); fill(c, bx + 6, y0 + 1, bz + dz, bx + 6, y0 + 2, bz + dz, 35, 14);
            fill(c, bx + 3, y0 + 1, bz + dz, bx + 4, y0 + 1, bz + dz, 44, 7);
        }
        fill(c, bx + 9, y0 + 1, bz + 2, bx + 9, y0 + 2, bz + 10, 155, 0);
        fill(c, bx + 9, y0 + 5, bz + 3, bx + 9, y0 + 5, bz + 9, 251, 14);
        for (int dz = 3; dz <= 9; dz += 2) put(c, bx + 9, y0 + 4, bz + dz, 95, 4);
        for (int dz = 3; dz <= 9; dz += 6) fill(c, bx + 9, y0 + 6, bz + dz, bx + 9, y0 + 7, bz + dz, 101, 0);
        put(c, bx + 6, y0 + 7, bz + 6, 89, 0);
        put(c, bx + 3, y0 + 7, bz + 3, 89, 0);
        put(c, bx + 3, y0 + 7, bz + 9, 89, 0);
        put(c, bx + 12, y0 + 7, bz + 3, 89, 0);
        mob(c, bx + 4, y0 + 1, bz + 8, EntityType.ZOMBIE);
        graded(c, s, bx + 2, y0 + 1, bz + 11, r, MCD_LOOT, false);
        // Kitchen: fryers, a pass, and the walk-in at the back of it.
        for (int dx = 11; dx <= 16; dx += 2) {
            put(c, bx + dx, y0 + 1, bz + 3, 61, 3); put(c, bx + dx, y0 + 1, bz + 5, 154, 0);
            put(c, bx + dx, y0 + 2, bz + 3, 44, 7);
        }
        fill(c, bx + 11, y0 + 1, bz + 7, bx + 17, y0 + 1, bz + 7, 155, 0);
        put(c, bx + 14, y0 + 7, bz + 6, 89, 0);                        // flush under the roof
        // Chest and spawner stand clear of the walk-in (it was built over both; same depth, same draws).
        graded(c, s, bx + 12, y0 + 1, bz + 11, r, MCD_LOOT, true);
        mob(c, bx + 11, y0 + 1, bz + 9, EntityType.ZOMBIE);
        // The pole, and the two arcs on it.
        // Pole 43:8 (was iron block); the arcs yellow concrete 251:4 (were gold block).
        fill(c, x0 + 23, y0 + 1, z0 + 8, x0 + 23, y0 + 9, z0 + 8, 43, 8);
        for (int i = 0; i < 2; i++) {
            int az = z0 + 6 + i * 4;
            for (int dy = 0; dy <= 4; dy++) {
                int spread = (int) Math.round(Math.sqrt(Math.max(0, 16 - dy * dy)) * 0.5);
                put(c, x0 + 23, y0 + 9 + dy, az - spread, 251, 4);
                put(c, x0 + 23, y0 + 9 + dy, az + spread, 251, 4);
            }
            fill(c, x0 + 23, y0 + 13, az - 1, x0 + 23, y0 + 13, az + 1, 251, 4);
        }
        // One block on top of each leg joins the M face to face (it only met at the corners).
        for (int dz = 4; dz <= 12; dz += 4) put(c, x0 + 23, y0 + 12, z0 + dz, 251, 4);
        // Drive-through, with the board still up.
        fill(c, x0 + 21, y0, z0 + 12, x0 + 25, y0, z1 - 1, 251, 15);
        for (int dz = 13; dz <= 20; dz += 2) {
            fill(c, x0 + 21, y0 + 5, z0 + dz, x0 + 25, y0 + 5, z0 + dz, 44, 7);
            put(c, x0 + 25, y0 + 1, z0 + dz, 85, 0);
            fill(c, x0 + 25, y0 + 2, z0 + dz, x0 + 25, y0 + 4, z0 + dz, 85, 0);
        }
        fill(c, x0 + 20, y0 + 2, z0 + 16, x0 + 20, y0 + 4, z0 + 18, 251, 15);
        fill(c, x0 + 20, y0 + 3, z0 + 17, x0 + 20, y0 + 3, z0 + 17, 95, 4);
        put(c, x0 + 20, y0 + 1, z0 + 16, 251, 15);                      // the board's legs
        put(c, x0 + 20, y0 + 1, z0 + 18, 251, 15);
        put(c, x0 + 20, y0 + 5, z0 + 17, 89, 0);
        // The tubes in the side yard, going nowhere, with a pit under them.
        int px = x0 + 4, pz = z0 + 17;
        fill(c, px, y0 + 1, pz, px + 8, y0 + 1, pz + 4, 35, 4);
        for (int i = 0; i < 3; i++) {
            int tx = px + 1 + i * 3;
            for (int dy = 2; dy <= 7; dy++) {
                int col = new int[]{14, 4, 5}[i];
                shell(c, tx, y0 + dy, pz + 1, tx + 2, y0 + dy, pz + 3, dy % 2 == 0 ? 95 : 251, col);
                fill(c, tx + 1, y0 + dy, pz + 2, tx + 1, y0 + dy, pz + 2, 0, 0);
            }
            fill(c, tx, y0 + 8, pz + 1, tx + 2, y0 + 8, pz + 3, 251, new int[]{14, 4, 5}[i]);
        }
        fill(c, px + 1, y0 + 8, pz + 2, px + 7, y0 + 8, pz + 2, 20, 0);
        // Each tube has a way in at the bottom, so it reads as a crawl tube that goes nowhere; the
        // carpets lie round the tubes, not under them; the spider spawner sits in the pit (it was set
        // into a tube wall four blocks up, where nothing could spawn or reach it). Same draws.
        for (int i = 0; i < 3; i++) fill(c, px + 2 + i * 3, y0 + 2, pz + 3, px + 2 + i * 3, y0 + 3, pz + 3, 0, 0);
        for (int i = 0; i < 26; i++) {
            int cx = px + r.nextInt(9), cz = pz + r.nextInt(5), col = r.nextInt(16);
            if (cx > px && cz > pz && cz < pz + 4) continue;
            put(c, cx, y0 + 1, cz, 171, col);
        }
        mob(c, px + 4, y0 + 1, pz, EntityType.SPIDER);
        graded(c, s, px + 8, y0 + 1, pz + 4, r, MCD_LOOT, false);
        // THE SURPRISE: the walk-in. Still cold, still shut, and not empty.
        int wx = bx + 13, wz = bz + 9;
        shell(c, wx, y0 + 1, wz, wx + 4, y0 + 5, wz + 3, 174, 0);
        fill(c, wx + 1, y0 + 1, wz + 1, wx + 3, y0 + 4, wz + 2, 0, 0);
        fill(c, wx + 1, y0 + 1, wz + 1, wx + 3, y0 + 1, wz + 2, 80, 0);
        // Its back is the street wall, brick and white as the rest of the front (the ice showed on the
        // street); and it has a real door, shut, on a steel sill, opened by a button either side
        // (the door was three stacked blocks). Structure audit 2026-09-23.
        fill(c, wx, y0 + 1, wz + 3, wx + 4, y0 + 3, wz + 3, 45, 0);
        fill(c, wx, y0 + 4, wz + 3, wx + 4, y0 + 5, wz + 3, 251, 0);
        put(c, wx + 2, y0 + 1, wz, 43, 8);
        ironDoor(c, wx + 2, y0 + 2, wz, 1, false);
        put(c, wx + 1, y0 + 2, wz - 1, 77, 4);
        put(c, wx + 1, y0 + 3, wz + 1, 77, 3);
        put(c, wx + 2, y0 + 4, wz + 1, 169, 0);
        for (int dx = 1; dx <= 3; dx++) put(c, wx + dx, y0 + 2, wz + 2, 79, 0);
        mob(c, wx + 1, y0 + 1, wz + 1, EntityType.HUSK);
        trove(c, wx + 3, y0 + 1, wz + 1, r, MCD_LOOT, MCD_TROVE, 12 + r.nextInt(6));
    }
}
