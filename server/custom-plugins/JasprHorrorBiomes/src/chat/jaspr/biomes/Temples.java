package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

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
 * Temples: one long run of jumps and a locked door, built six ways.
 *
 * Every one of them is the same expedition -- a stepped face with one door in it, a
 * checkerboard floor where only half the tiles are really there, a chasm crossed on
 * pillars, a gallery with the ceiling coming down, a span with most of its planks gone,
 * a gate that only opens for whoever finds the lever, and a sanctum under all of it --
 * and every one of them is built out of a different rock, traps you differently and is
 * held by different things.
 *
 * They are also the only structures in the plugin that care where they are. Each variant
 * is gated to one of the world's skies in the claim register, so the ember stair is only
 * ever in the ash, the tidewell only in the mist, and finding a particular one means
 * going to the right part of the map rather than walking until the dice come up.
 */
public final class Temples {
    private Temples() { }

    /**
     * A variant. The palette is
     * {wall, wallData, floor, floorData, trim, trimData, light, hazard, hazardData, growth, growthData}.
     */
    private static final class Skin {
        final int rank, cell;
        final long salt;
        final int[] p;
        final EntityType[] mobs;
        final int[][] loot, hoard;
        Skin(int rank, int cell, long salt, int[] p, EntityType[] mobs, int[][] loot, int[][] hoard) {
            this.rank = rank; this.cell = cell; this.salt = salt; this.p = p;
            this.mobs = mobs; this.loot = loot; this.hoard = hoard;
        }
    }

    // -- the six --------------------------------------------------------------------

    private static final int[][] EMBER_LOOT = {
        {214, 0, 10, 2,  6}, {215, 0, 11, 4, 12}, {213, 0,  9, 2,  6}, {263, 0, 12, 6, 18},
        {266, 0, 11, 3,  9}, {41, 0,  6, 1,  2}, {348, 0, 10, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351, 1, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] EMBER_HOARD = {{41, 0, 1, 4, 8}, {57, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static final int[][] TIDE_LOOT = {
        { 30, 0, 10, 4, 12}, {106, 0, 11, 4, 12}, { 39, 0,  9, 2,  6}, {266, 0, 11, 3,  9},
        {265, 0, 11, 4, 12}, {352, 0, 12, 4, 12}, {348, 0,  9, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351, 6, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] TIDE_HOARD = {{133, 0, 1, 2, 2}, {57, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static final int[][] SANCTUM_LOOT = {
        { 99, 0, 10, 3,  9}, {100, 0, 10, 3,  9}, { 40, 0,  9, 2,  6}, {266, 0, 11, 3,  9},
        {296, 0, 11, 4, 12}, {352, 0, 11, 4, 12}, {348, 0,  9, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0, 10, 2,  8}, {351, 10, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] SANCTUM_HOARD = {{133, 0, 1, 3, 3}, {41, 0, 1, 2, 4}, {403, 0, 1, 2, 3}};

    private static final int[][] RELIQUARY_LOOT = {
        { 12, 0, 12, 8, 24}, {128, 0, 10, 4, 12}, { 81, 0,  8, 2,  6}, {266, 0, 12, 4, 12},
        { 41, 0,  7, 1,  2}, {352, 0, 11, 4, 12}, {348, 0,  9, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351, 11, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] RELIQUARY_HOARD = {{41, 0, 1, 5, 9}, {57, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static final int[][] OXIDE_LOOT = {
        {172, 0, 11, 6, 18}, {101, 0, 11, 4, 12}, {107, 0,  8, 2,  6}, {265, 0, 12, 4, 12},
        {266, 0, 10, 3,  9}, {263, 0, 11, 6, 18}, {331, 0, 10, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351, 14, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] OXIDE_HOARD = {{42, 0, 1, 6, 10}, {57, 0, 1, 1, 1}, {403, 0, 1, 2, 3}};

    private static final int[][] FROST_LOOT = {
        {174, 0, 11, 4, 12}, { 80, 0, 10, 4, 12}, {332, 0, 10, 6, 18}, {265, 0, 11, 4, 12},
        {266, 0, 10, 3,  9}, {352, 0, 11, 4, 12}, {348, 0,  9, 4, 12}, {264, 0,  8, 1,  3},
        {388, 0,  9, 2,  6}, {351, 12, 10, 6, 18}, {384, 0,  8, 2,  6}, {403, 0,  5, 1,  1},
    };
    private static final int[][] FROST_HOARD = {{57, 0, 1, 2, 2}, {133, 0, 1, 1, 2}, {403, 0, 1, 2, 3}};

    private static final Skin[] SKINS = {
        // ash: obsidian and nether brick, lava in every hole, and things that are on fire
        new Skin(Megaliths.RANK_EMBER, 43, 0x454D424552L,
            new int[]{112, 0, 215, 0, 49, 0, 89, 10, 0, 213, 0},
            new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON},
            EMBER_LOOT, EMBER_HOARD),
        // mist: prismarine gone green, water in every hole, and what lives in it
        new Skin(Megaliths.RANK_TIDEWELL, 55, 0x54494445L,
            new int[]{168, 1, 98, 1, 168, 2, 169, 9, 0, 106, 1},
            new EntityType[]{EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SLIME},
            TIDE_LOOT, TIDE_HOARD),
        // spores: mossy stone under a canopy that got in through the roof
        new Skin(Megaliths.RANK_SANCTUM, 53, 0x47524545L,
            new int[]{98, 1, 48, 0, 159, 4, 89, 213, 0, 106, 1},        // trim: yellow terracotta, was gold block
            new EntityType[]{EntityType.CAVE_SPIDER, EntityType.SPIDER, EntityType.SLIME},
            SANCTUM_LOOT, SANCTUM_HOARD),
        // dust: sandstone, gold, and a great deal of sand waiting to come down
        new Skin(Megaliths.RANK_RELIQUARY, 59, 0x44555354L,
            new int[]{24, 2, 24, 0, 159, 4, 89, 213, 0, 12, 0},         // trim: yellow terracotta, was gold block
            new EntityType[]{EntityType.HUSK, EntityType.ZOMBIE, EntityType.ENDERMAN},
            RELIQUARY_LOOT, RELIQUARY_HOARD),
        // rust: plate and orange clay, and the traps are the explosive kind
        new Skin(Megaliths.RANK_OXIDE, 67, 0x4F58494445L,
            new int[]{172, 0, 159, 1, 43, 8, 169, 10, 0, 101, 0},       // plate: 43:8, was iron block
            new EntityType[]{EntityType.CREEPER, EntityType.VEX, EntityType.VINDICATOR},
            OXIDE_LOOT, OXIDE_HOARD),
        // snow: cut into the ice, and the floor is exactly as slippery as it looks
        new Skin(Megaliths.RANK_FROSTVAULT, 35, 0x46524F5354L,
            new int[]{174, 0, 79, 0, 155, 0, 169, 9, 0, 80, 0},
            new EntityType[]{EntityType.STRAY, EntityType.WITCH, EntityType.SILVERFISH},
            FROST_LOOT, FROST_HOARD),
    };

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        for (Skin skin : SKINS) temple(c, t, skin);
        survivalTown(c, t);
    }

    // == 51-56. The six temples ==================================================

    private static void temple(Chunk c, Terrain t, Skin k) {
        Site s = ground(t, c, k.cell, k.salt, 33, 45, 7, k.rank);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, mx = x0 + 16;
        int wall = k.p[0], wallD = k.p[1], floor = k.p[2], floorD = k.p[3];
        int trim = k.p[4], trimD = k.p[5], light = k.p[6];
        int haz = k.p[7], hazD = k.p[8], grow = k.p[9], growD = k.p[10];
        for (int dx = 0; dx <= 32; dx++) for (int dz = 0; dz <= 44; dz++)
            footing(c, s, x0 + dx, z0 + dz, wall, wallD);
        underpin(c, s, x0, y0, z0, wall, wallD);

        // -- A. the face. Six steps back and up, one door at the bottom of it.
        for (int dy = 0; dy <= 21; dy++) {
            int inset = dy / 4;
            for (int dx = inset; dx <= 32 - inset; dx++)
                for (int dz = 0; dz <= 5 - Math.min(5, inset); dz++)
                    put(c, x0 + dx, y0 + dy, z0 + dz, ((dy % 4) == 3) ? trim : wall, ((dy % 4) == 3) ? trimD : wallD);
            if (dy % 4 == 3) { put(c, x0 + inset, y0 + dy, z0, light, 0); put(c, x0 + 32 - inset, y0 + dy, z0, light, 0); }
        }
        fill(c, mx - 1, y0 + 1, z0, mx + 1, y0 + 4, z0 + 5, 0, 0);
        fill(c, mx - 2, y0 + 5, z0, mx + 2, y0 + 5, z0 + 5, trim, trimD);
        put(c, mx - 2, y0 + 3, z0 + 1, light, 0);
        put(c, mx + 2, y0 + 3, z0 + 1, light, 0);

        // -- B. the tile hall. Half the floor is not there, and the carving says which half.
        fill(c, x0 + 5, y0 + 1, z0 + 6, x0 + 27, y0 + 8, z0 + 17, 0, 0);
        shell(c, x0 + 4, y0 - 8, z0 + 5, x0 + 28, y0 + 9, z0 + 18, wall, wallD);
        fill(c, x0 + 5, y0 - 8, z0 + 6, x0 + 27, y0 - 7, z0 + 17, haz, hazD);
        // Each pit is poured over its shell's own floor, so it gets a floor of its own beneath: the lava or
        // water lay straight on the terrain and ran into any cave under the temple (structure audit 2026-09-22).
        fill(c, x0 + 5, y0 - 9, z0 + 6, x0 + 27, y0 - 9, z0 + 17, wall, wallD);
        for (int dx = 5; dx <= 27; dx++) for (int dz = 6; dz <= 17; dz++) {
            boolean safe = ((dx + dz) & 1) == 0;
            if (safe) put(c, x0 + dx, y0, z0 + dz, trim, trimD);
            else {
                // A missing tile is an open hole over the hazard one below (structure audit 2026-09-23): the
                // footing packs the hall's pit solid, so the "missing" tiles were only terrain or a dip.
                put(c, x0 + dx, y0, z0 + dz, 0, 0);
                put(c, x0 + dx, y0 - 1, z0 + dz, haz, hazD);
            }
        }
        fill(c, mx - 1, y0, z0 + 5, mx + 1, y0, z0 + 5, trim, trimD);
        fill(c, mx - 1, y0, z0 + 18, mx + 1, y0, z0 + 18, trim, trimD);
        for (int dx = 8; dx <= 24; dx += 2) {                          // the carving: the safe pattern
            put(c, x0 + dx, y0 + 6, z0 + 5, trim, trimD);
            put(c, x0 + dx, y0 + 7, z0 + 5, wall, wallD);
        }
        put(c, mx, y0 + 8, z0 + 11, light, 0);
        for (int dz = 8; dz <= 14; dz += 3) {                          // lamps set in the side walls
            put(c, x0 + 4, y0 + 4, z0 + dz, light, 0);
            put(c, x0 + 28, y0 + 4, z0 + dz, light, 0);
        }
        // The chest stands on a safe tile by the west wall (it was set into a missing tile at floor level).
        graded(c, s, x0 + 5, y0 + 1, z0 + 7, chestRng(s, x0 + 5, y0 + 1, z0 + 7), k.loot, false);
        mob(c, x0 + 26, y0 + 1, z0 + 16, k.mobs[0]);

        // -- C. the chasm. Pillars, a long way down, and whatever is at the bottom of it.
        fill(c, x0 + 3, y0 - 14, z0 + 18, x0 + 29, y0 + 10, z0 + 30, 0, 0);
        shell(c, x0 + 2, y0 - 15, z0 + 17, x0 + 30, y0 + 11, z0 + 31, wall, wallD);
        fill(c, x0 + 3, y0 - 15, z0 + 18, x0 + 29, y0 - 14, z0 + 30, haz, hazD);
        fill(c, x0 + 3, y0 - 16, z0 + 18, x0 + 29, y0 - 16, z0 + 30, wall, wallD);     // its floor, as above
        fill(c, mx - 2, y0, z0 + 18, mx + 2, y0, z0 + 19, trim, trimD);
        int[][] steps = {{0, 21, 0}, {-6, 23, -2}, {5, 25, 1}, {-4, 27, -1}, {3, 29, 2}};
        // Each pillar rises from the pit floor through the hazard (it stood on the liquid), and its lamp hangs
        // from the chasm roof on a chain instead of floating four above the top (structure audit 2026-09-23).
        int chain = wall == 112 ? 113 : 85;
        for (int[] st : steps) {
            int px = mx + st[0], pz = z0 + st[1], top = y0 + st[2];
            fill(c, px - 1, y0 - 15, pz - 1, px + 1, top, pz + 1, wall, wallD);
            fill(c, px - 1, top, pz - 1, px + 1, top, pz + 1, trim, trimD);
            fill(c, px, top + 5, pz, px, y0 + 10, pz, chain, 0);
            put(c, px, top + 4, pz, light, 0);
        }
        fill(c, mx - 3, y0 + 2, z0 + 30, mx + 3, y0 + 2, z0 + 31, trim, trimD);
        // Growth that cannot hang in mid-air goes where it would end up (structure audit 2026-09-22): the
        // dust temple's sand fell the moment it was placed, and the frost temple's snow was pruned as a
        // floater. Sand lies on the pit floor; everything else clings to the nearer side wall (vines hung
        // on it, magma seeping through it) instead of floating in the chasm (2026-09-23). Same draws as before.
        boolean falls = grow == 12, seeps = grow == 213;
        for (int i = 0; i < 14; i++) {
            int gx = x0 + 4 + r.nextInt(25), gy = y0 - 12 + r.nextInt(8), gz = z0 + 19 + r.nextInt(11);
            boolean west = gx < mx;
            if (!falls) gx = west ? x0 + (seeps ? 2 : 3) : x0 + (seeps ? 30 : 29);
            put(c, gx, falls ? y0 - 13 : gy, gz, grow, cling(grow, growD, west));
        }
        // What is at the bottom stands on an islet against pillar two's foot (it was sealed inside the pillar),
        // and a ladder on the near wall is the way back up to the landing for whoever survives the fall.
        fill(c, mx - 6, y0 - 15, z0 + 25, mx - 6, y0 - 14, z0 + 25, wall, wallD);
        mob(c, mx - 6, y0 - 13, z0 + 25, k.mobs[1]);
        fill(c, mx - 3, y0 - 15, z0 + 18, mx - 3, y0 + 1, z0 + 18, 65, 3);
        // The reward for the crossing waits on the far landing (it was walled into pillar three's base).
        graded(c, s, mx - 3, y0 + 3, z0 + 30, chestRng(s, mx - 3, y0 + 3, z0 + 30), k.loot, true);

        // -- D. the gallery. Three wide, the ceiling loose, and holes in the walls.
        fill(c, mx - 1, y0 + 3, z0 + 31, mx + 1, y0 + 6, z0 + 37, 0, 0);
        fill(c, mx - 2, y0 + 2, z0 + 31, mx + 2, y0 + 2, z0 + 38, trim, trimD);
        if (falls) {
            // The dust temple's loose ceiling is sand held up on a sandstone course: sand laid straight
            // over the passage came down the moment it was placed (structure audit 2026-09-22).
            fill(c, mx - 2, y0 + 7, z0 + 31, mx + 2, y0 + 7, z0 + 38, floor, floorD);
            fill(c, mx - 2, y0 + 8, z0 + 31, mx + 2, y0 + 8, z0 + 38, grow, growD);
        } else fill(c, mx - 2, y0 + 7, z0 + 31, mx + 2, y0 + 7, z0 + 38, grow, growD);
        // The walls the darts are set in, the gallery's footing and a roof over the loose ceiling (structure
        // audit 2026-09-23): the code assumed rock round the passage, so above the ground it was an open deck
        // between the chasm and the span, the dispensers standing free and the growth ceiling lying bare.
        fill(c, mx - 2, y0, z0 + 32, mx + 2, y0 + 1, z0 + 35, wall, wallD);
        fill(c, mx - 2, y0 + 3, z0 + 32, mx - 2, y0 + 7, z0 + 35, wall, wallD);
        fill(c, mx + 2, y0 + 3, z0 + 32, mx + 2, y0 + 7, z0 + 35, wall, wallD);
        fill(c, mx - 2, y0 + 7, z0 + 31, mx + 2, y0 + 8, z0 + 31, wall, wallD);     // the chasm's back wall
        if (!falls) fill(c, mx - 2, y0 + 8, z0 + 32, mx + 2, y0 + 8, z0 + 35, wall, wallD);
        for (int dz = 32; dz <= 36; dz += 2) darts(c, mx, y0, z0 + dz);   // a hole with something in it
        for (int i = 0; i < 12; i++)
            put(c, mx - 1 + r.nextInt(3), y0 + 4 + r.nextInt(3), z0 + 32 + r.nextInt(5), 30, 0);
        mob(c, mx, y0 + 3, z0 + 34, k.mobs[2]);

        // -- E. the span. Most of the planks are gone and it is a long way to the floor.
        fill(c, x0 + 8, y0 - 18, z0 + 37, x0 + 24, y0 + 8, z0 + 43, 0, 0);
        shell(c, x0 + 7, y0 - 19, z0 + 36, x0 + 25, y0 + 9, z0 + 44, wall, wallD);
        fill(c, x0 + 8, y0 - 19, z0 + 37, x0 + 24, y0 - 18, z0 + 43, haz, hazD);
        fill(c, x0 + 8, y0 - 20, z0 + 37, x0 + 24, y0 - 20, z0 + 43, wall, wallD);     // its floor, as above
        for (int dz = 37; dz <= 43; dz++) {
            if (dz == 39 || dz == 42) continue;                        // the missing planks
            fill(c, mx - 1, y0 + 2, z0 + dz, mx + 1, y0 + 2, z0 + dz, 5, 1);
            put(c, mx - 2, y0 + 3, z0 + dz, 85, 0);
            put(c, mx + 2, y0 + 3, z0 + dz, 85, 0);
        }
        for (int i = 0; i < 10; i++) {
            // As in the chasm: sand settles on the sanctum roof below (not down the idol shaft or onto the
            // walkway), everything else clings to the nearer wall. Same draws as before.
            int gx = x0 + 9 + r.nextInt(15), gy = y0 - 1 + r.nextInt(8), gz = z0 + 37 + r.nextInt(7);
            if (falls && Math.abs(gx - mx) <= 1) continue;
            boolean west = gx < mx;
            if (!falls) gx = west ? x0 + (seeps ? 7 : 8) : x0 + (seeps ? 25 : 24);
            put(c, gx, falls ? y0 - 6 : gy, gz, grow, cling(grow, growD, west));
        }
        put(c, mx, y0 + 8, z0 + 40, light, 0);
        // The span's chest lies where a faller lands, on the sanctum roof that is the pit's floor (its old place
        // at the bottom of the pit is inside the sanctum's floor).
        graded(c, s, x0 + 9, y0 - 6, z0 + 42, chestRng(s, x0 + 9, y0 - 6, z0 + 42), k.loot, false);

        // -- F. the gate, and under it the sanctum.
        // The gate is a false door set in the back wall. Only the landing row in front of it is cleared: the
        // clearance also took the back wall around the gate, leaving holes to the outside (2026-09-23).
        fill(c, mx - 3, y0 + 2, z0 + 43, mx + 3, y0 + 6, z0 + 43, 0, 0);
        fill(c, mx - 1, y0 + 2, z0 + 44, mx + 1, y0 + 4, z0 + 44, trim, trimD);   // the gate itself
        // The levers hang on the back wall (data 3 hung them on the open span). The true one works the hatch
        // below it; the two that do not sit on either side of a charge that a pull sets off (the charges hung
        // in the pit under them, wired to nothing).
        put(c, mx - 3, y0 + 3, z0 + 43, 69, 4);                        // the lever that opens it
        put(c, mx + 3, y0 + 3, z0 + 43, 69, 4);                        // and the two that do not
        put(c, mx + 3, y0 + 5, z0 + 43, 69, 4);
        put(c, mx + 3, y0 + 2, z0 + 43, 46, 0);
        put(c, mx + 3, y0 + 4, z0 + 43, 46, 0);
        put(c, mx, y0 + 6, z0 + 44, light, 0);
        int sy = y0 - 16;
        fill(c, x0 + 9, sy, z0 + 29, x0 + 23, sy + 8, z0 + 43, 0, 0);
        shell(c, x0 + 8, sy - 1, z0 + 28, x0 + 24, sy + 9, z0 + 44, wall, wallD);
        fill(c, x0 + 9, sy - 1, z0 + 29, x0 + 23, sy - 1, z0 + 43, floor, floorD);
        for (int dx = 11; dx <= 21; dx += 5) for (int dz = 31; dz <= 41; dz += 5) {
            if (dx == 16 && dz == 36) continue;                        // the idol stands there, under the shaft
            fill(c, x0 + dx, sy, z0 + dz, x0 + dx, sy + 8, z0 + dz, trim, trimD);
            put(c, x0 + dx, sy + 5, z0 + dz, light, 0);
        }
        // the idol, on its plinth, with the hoard behind it
        fill(c, mx - 2, sy, z0 + 34, mx + 2, sy + 1, z0 + 38, trim, trimD);
        fill(c, mx - 1, sy + 2, z0 + 35, mx + 1, sy + 4, z0 + 37, 251, 4);   // the idol: yellow concrete, was gold
        put(c, mx, sy + 5, z0 + 36, light, 0);
        fill(c, mx - 1, sy + 2, z0 + 34, mx + 1, sy + 2, z0 + 34, grow, growD);
        mob(c, mx - 4, sy, z0 + 32, k.mobs[0]);
        mob(c, mx + 4, sy, z0 + 40, k.mobs[1]);
        mob(c, mx + 4, sy, z0 + 32, k.mobs[2]);
        graded(c, s, x0 + 10, sy, z0 + 30, chestRng(s, x0 + 10, sy, z0 + 30), k.loot, true);
        trove(c, mx, sy + 2, z0 + 38, r, k.loot, k.hoard, 16 + r.nextInt(6));
        // the shaft above the idol, with what was poised at the top of it
        fill(c, mx - 1, sy + 9, z0 + 35, mx + 1, y0 - 2, z0 + 37, 0, 0);
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            put(c, mx + dx, y0 - 3 + dy, z0 + 36 + dz, wall, wallD);
        // Its last row is the span pit's floor: close it, so the shaft is a recess over the idol and not a
        // drop from the pit onto it. The ladder that climbed the shaft into the poised block is gone.
        fill(c, mx - 1, y0 - 7, z0 + 37, mx + 1, y0 - 5, z0 + 37, wall, wallD);

        // -- G. the joins (structure audit 2026-09-23). Each section was carved and the next one's shell then
        // drawn over the carve, so the run was walled shut at three doorways, the span lost its last row and
        // the sanctum its way in. They are opened and finished here, after everything else is standing.
        fill(c, mx - 1, y0 + 1, z0 + 5, mx + 1, y0 + 4, z0 + 5, 0, 0);            // face -> tile hall
        fill(c, mx - 2, y0 + 5, z0 + 5, mx + 2, y0 + 5, z0 + 5, trim, trimD);
        if (k.rank == Megaliths.RANK_OXIDE) {
            // The iron doors of the register ("iron doors, and everything behind them has gone orange"), which the
            // builder never hung: a pair either side of a plate mullion, three in from the face where the passage
            // floor is level whatever the approach, a grille over each and a button on the mullion both sides.
            int dz = z0 + 3;
            fill(c, mx, y0 + 1, dz, mx, y0 + 4, dz, trim, trimD);
            for (int side = -1; side <= 1; side += 2) {
                put(c, mx + side, y0 + 1, dz, 71, 1);
                put(c, mx + side, y0 + 2, dz, 71, side < 0 ? 9 : 8);
                fill(c, mx + side, y0 + 3, dz, mx + side, y0 + 4, dz, 101, 0);
            }
            put(c, mx, y0 + 2, dz - 1, 77, 4);
            put(c, mx, y0 + 2, dz + 1, 77, 3);
        }
        fill(c, mx - 1, y0 + 1, z0 + 17, mx + 1, y0 + 4, z0 + 17, 0, 0);          // tile hall -> chasm
        fill(c, mx - 1, y0, z0 + 17, mx + 1, y0, z0 + 17, trim, trimD);
        fill(c, mx - 1, y0 + 3, z0 + 36, mx + 1, y0 + 6, z0 + 36, 0, 0);          // gallery -> span
        fill(c, mx - 1, y0 + 2, z0 + 36, mx + 1, y0 + 2, z0 + 36, trim, trimD);
        darts(c, mx, y0, z0 + 36);
        // The span's planks lie on two stringers wall to wall (the middle section floated, the rails stood on
        // nothing); the planks stay missing at the two gaps. The last row is the landing in front of the gate.
        fill(c, mx - 2, y0 + 2, z0 + 37, mx - 2, y0 + 2, z0 + 43, 17, 9);
        fill(c, mx + 2, y0 + 2, z0 + 37, mx + 2, y0 + 2, z0 + 43, 17, 9);
        fill(c, mx - 1, y0 + 2, z0 + 43, mx + 1, y0 + 2, z0 + 43, 5, 1);
        // The way down is a hatch under the true lever and a ladder on the back wall through the pit and the
        // sanctum roof to its floor (it stopped on the roof, and the pit had no way out).
        fill(c, mx - 3, sy, z0 + 43, mx - 3, y0 + 1, z0 + 43, 65, 2);
        put(c, mx - 3, y0 + 2, z0 + 43, 96, 8);
        approach(c, t, mx, y0, z0, wall, wallD);
        // A coping course of the face's trim round the roofs of the hall, the chasm and the span, so the
        // halls behind the stepped face read as the same temple from the sides and the back.
        coping(c, x0 + 4, y0 + 9, z0 + 5, x0 + 28, z0 + 16, trim, trimD);
        coping(c, x0 + 2, y0 + 11, z0 + 17, x0 + 30, z0 + 31, trim, trimD);
        coping(c, x0 + 7, y0 + 9, z0 + 36, x0 + 25, z0 + 44, trim, trimD);
    }

    /**
     * Foundations under the deep halls (structure audit 2026-09-23). The footing stops seven below the floor, but the
     * tile pit, the chasm, the sanctum and the span go down nine to twenty, so where a cave or a cavern lies under
     * the footprint their floors hung into it as a lid over the void. Every column of the footprint in this chunk is
     * carried down in the wall stone from under the deepest floor above it to the first solid ground. (Merge fix-up,
     * 2026-09-23: it used to give up on a void deeper than 32 blocks, which left the sanctum and span hanging as a
     * lid over the big caverns at both Tidewell sites, and the filled columns beside them read as pillars that stop
     * short. It now goes on down to the cavern floor; bedrock is always there.) Column by column, so the chunks the
     * footprint spans agree.
     */
    private static void underpin(Chunk c, Site s, int x0, int y0, int z0, int wall, int wallD) {
        int bx = c.getX() * 16, bz = c.getZ() * 16;
        for (int wx = Math.max(x0, bx); wx <= Math.min(x0 + 32, bx + 15); wx++)
            for (int wz = Math.max(z0, bz); wz <= Math.min(z0 + 44, bz + 15); wz++) {
                int dx = wx - x0, dz = wz - z0, base = s.lo - 6;                    // the footing's last course
                if (dx >= 4 && dx <= 28 && dz >= 5 && dz <= 18) base = Math.min(base, y0 - 9);     // tile pit
                if (dx >= 2 && dx <= 30 && dz >= 17 && dz <= 31) base = Math.min(base, y0 - 16);   // chasm
                if (dx >= 8 && dx <= 24 && dz >= 28 && dz <= 44) base = Math.min(base, y0 - 17);   // sanctum
                if (dx >= 7 && dx <= 25 && dz >= 36 && dz <= 44) base = Math.min(base, y0 - 20);   // span
                int top = hollow(c, wx, base, wz) ? base : base - 1, y = top;
                while (y > 1 && hollow(c, wx, y, wz)) y--;
                if (y == top || hollow(c, wx, y, wz) || Megaliths.blockAt(c, wx, y, wz) == null) continue;
                fill(c, wx, y + 1, wz, wx, top, wz, wall, wallD);
            }
    }

    /** Cave air or cave liquid. */
    private static boolean hollow(Chunk c, int wx, int y, int wz) {
        Block b = Megaliths.blockAt(c, wx, y, wz);
        return b != null && (b.getTypeId() == 0 || b.isLiquid());
    }

    /** The outer ring of one course. */
    private static void coping(Chunk c, int xa, int y, int za, int xb, int zb, int id, int data) {
        fill(c, xa, y, za, xb, y, za, id, data);
        fill(c, xa, y, zb, xb, y, zb, id, data);
        fill(c, xa, y, za, xa, y, zb, id, data);
        fill(c, xb, y, za, xb, y, zb, id, data);
    }

    /** Vines hang on the wall they are next to; other growth keeps its own data. */
    private static int cling(int id, int data, boolean west) {
        return id == 106 ? (west ? 2 : 8) : data;
    }

    /**
     * A dart trap across the gallery at z: a plate between two dispensers set in the walls, both facing
     * into the passage (they faced out) and loaded (they were empty). The west one takes the wire directly,
     * the east one, a block higher, through the wall block the wire runs into.
     */
    private static void darts(Chunk c, int mx, int y0, int z) {
        dispenser(c, mx - 2, y0 + 3, z, 5);
        dispenser(c, mx + 2, y0 + 4, z, 4);
        put(c, mx, y0 + 3, z, 70, 0);
        put(c, mx - 1, y0 + 3, z, 55, 0);
        put(c, mx + 1, y0 + 3, z, 55, 0);
    }

    private static void dispenser(Chunk c, int wx, int wy, int wz, int facing) {
        put(c, wx, wy, wz, 23, facing);
        Block b = Megaliths.blockAt(c, wx, wy, wz);
        if (b == null) return;
        try {
            ((org.bukkit.block.Dispenser) b.getState()).getInventory().addItem(new ItemStack(Material.ARROW, 9));
            // No update(): getInventory() of a placed dispenser is its live inventory (as graded() notes).
        } catch (RuntimeException ignored) { }
    }

    /**
     * The way in from the ground in front (structure audit 2026-09-23). The face stands on the lowest ground
     * under the footprint, so where the slope rises in front of it the door was buried to the lintel. Only the
     * row just outside the face lies in a chunk the temple writes, so the approach is cut there: up to two
     * steps down inside the mouth of the passage, a landing dug down level with the top one, and a flight
     * rising either way along the foot of the face until it meets the ground.
     */
    private static void approach(Chunk c, Terrain t, int mx, int y0, int z0, int wall, int wallD) {
        int zf = z0 - 1, hi = y0;
        for (int x = mx - 1; x <= mx + 1; x++) hi = Math.max(hi, t.sample(x, zf).y);
        int n = Math.min(2, hi - y0);
        if (n <= 0) return;
        for (int i = 0; i < n; i++) fill(c, mx - 1, y0 + 1, z0 + i, mx + 1, y0 + n - i, z0 + i, wall, wallD);
        for (int x = mx - 1; x <= mx + 1; x++) cut(c, t, x, zf, y0 + n, wall, wallD);
        for (int side = -1; side <= 1; side += 2)
            for (int i = 1; i <= 8; i++) {
                int x = mx + side * (1 + i), step = y0 + n + i;
                if (t.sample(x, zf).y <= step) break;
                cut(c, t, x, zf, step, wall, wallD);
            }
    }

    /** One column of the approach: ground above the step dug out, the step itself laid in the wall stone. */
    private static void cut(Chunk c, Terrain t, int x, int z, int step, int wall, int wallD) {
        int h = t.sample(x, z).y;
        if (h > step) fill(c, x, step + 1, z, x, h + 3, z, 0, 0);
        fill(c, x, Math.min(h + 1, step), z, x, step, z, wall, wallD);
    }

    /**
     * Loot draws from a generator of its own, seeded by the box (structure audit 2026-09-23). graded() and
     * trove() only draw when their chest lies in the chunk being populated, so sharing the builder's Random
     * made everything drawn after a chest differ from chunk to chunk: pieces straddling a seam came out half
     * built. The tables and the draws inside them are unchanged.
     */
    private static Random chestRng(Site s, int wx, int wy, int wz) {
        return new Random(s.seed ^ (wx * 341873128712L) ^ (wz * 132897987541L) ^ wy);
    }

    // == 57. Survival Town =======================================================
    // Two houses facing each other across a strip of tarmac, a bus, a picnic set and
    // about thirty mannequins, all of it put up by somebody who wanted to know what a
    // blast does to a street. The blast happened. The mannequins are still standing and
    // the paint is still that particular yellow. There is a shelter under one garage.
    //
    // This is an original build in the idiom of the real test suburbs -- the mannequin
    // towns put up on proving grounds in the fifties -- rather than a copy of any
    // particular game's map.

    private static final int[][] TOWN_LOOT = {
        { 19, 0,  7, 1,  3}, {397, 3,  5, 1,  1}, { 86, 0,  9, 2,  6}, {262, 0, 12, 8, 24},
        {289, 0, 12, 4, 12}, {265, 0, 11, 4, 12}, {297, 0, 11, 2,  6}, {367, 0, 11, 4, 12},
        {263, 0, 11, 6, 18}, {351, 4, 10, 6, 18}, {384, 0,  7, 2,  6}, {403, 0,  4, 1,  1},
    };
    private static final int[][] TOWN_HOARD = {{57, 0, 1, 1, 2}, {322, 0, 1, 2, 3}, {403, 0, 1, 2, 3}};

    /**
     * What stands on the lot, {x0, z0, x1, z1} relative to its corner: houses (with the roof overhang), porches,
     * garages, driveways, the bus, the tower, the picnic set and the swing frame.
     */
    private static final int[][] TOWN_LOTS = {
        {4, 5, 16, 19}, {17, 8, 17, 16}, {4, 19, 12, 25}, {13, 20, 17, 24},
        {28, 17, 40, 31}, {27, 20, 27, 28}, {32, 10, 40, 16}, {27, 11, 31, 15},
        {12, 2, 30, 5}, {37, 3, 42, 8}, {5, 26, 11, 28}, {14, 30, 19, 30},
    };

    /** Open lawn for a yard figure at (dx, dz): its post and both arms a block clear of everything on the lot. */
    private static boolean lawn(int dx, int dz) {
        for (int[] b : TOWN_LOTS)
            if (dx + 1 >= b[0] - 1 && dx - 1 <= b[2] + 1 && dz >= b[1] - 1 && dz <= b[3] + 1) return false;
        return true;
    }

    /** A figure on a post: legs, a torso, arms and a head, all of it painted. */
    private static void mannequin(Chunk c, int wx, int wy, int wz, int coat) {
        put(c, wx, wy, wz, 85, 0);
        put(c, wx, wy + 1, wz, 35, coat);
        put(c, wx, wy + 2, wz, 35, 0);
        put(c, wx - 1, wy + 2, wz, 44, 7);
        put(c, wx + 1, wy + 2, wz, 44, 7);
        put(c, wx, wy + 3, wz, 86, 0);
    }

    private static void survivalTown(Chunk c, Terrain t) {
        Site s = ground(t, c, 73, 0x54455354L, 45, 37, 6, Megaliths.RANK_TESTTOWN);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 44, z1 = z0 + 36, mx = x0 + 22;
        for (int dx = 0; dx <= 44; dx++) for (int dz = 0; dz <= 36; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 2, 0);
        // The street, its centre line, and the two driveways off it.
        fill(c, mx - 4, y0, z0, mx + 4, y0, z1, 251, 7);
        for (int dz = 2; dz <= 34; dz += 4) fill(c, mx, y0, z0 + dz, mx, y0, z0 + dz + 1, 251, 4);
        // The driveways run to the garages beside the houses (they ran under the house floors).
        fill(c, x0 + 13, y0, z0 + 20, mx - 5, y0, z0 + 24, 251, 8);
        fill(c, mx + 5, y0, z0 + 11, x0 + 31, y0, z0 + 15, 251, 8);
        // Two houses, the same plan mirrored, in the two colours they always painted them.
        for (int side = 0; side < 2; side++) {
            int hx = side == 0 ? x0 + 4 : x0 + 28, hz = side == 0 ? z0 + 6 : z0 + 18;
            int coat = side == 0 ? 13 : 1, trim = side == 0 ? 4 : 0;
            shell(c, hx, y0 + 1, hz, hx + 12, y0 + 5, hz + 12, 159, coat);
            fill(c, hx + 1, y0 + 1, hz + 1, hx + 11, y0 + 4, hz + 11, 0, 0);
            fill(c, hx + 1, y0, hz + 1, hx + 11, y0, hz + 11, 5, 1);      // a boarded floor (it was the lawn)
            fill(c, hx + 1, y0 + 5, hz + 1, hx + 11, y0 + 5, hz + 11, 5, 1);
            shell(c, hx, y0 + 6, hz, hx + 12, y0 + 9, hz + 12, 159, coat);
            fill(c, hx + 1, y0 + 6, hz + 1, hx + 11, y0 + 9, hz + 11, 0, 0);
            for (int i = 0; i <= 6; i++) {                             // the pitched roof
                fill(c, hx + i, y0 + 10 + i, hz - 1, hx + i, y0 + 10 + i, hz + 13, 159, trim);
                fill(c, hx + 12 - i, y0 + 10 + i, hz - 1, hx + 12 - i, y0 + 10 + i, hz + 13, 159, trim);
                if (i > 0) fill(c, hx + i, y0 + 9 + i, hz + 1, hx + 12 - i, y0 + 9 + i, hz + 11, 5, 1);
            }
            for (int dz = 2; dz <= 10; dz += 3) {
                fill(c, hx, y0 + 2, hz + dz, hx, y0 + 3, hz + dz, 102, 0);
                fill(c, hx + 12, y0 + 2, hz + dz, hx + 12, y0 + 3, hz + dz, 102, 0);
                fill(c, hx, y0 + 7, hz + dz, hx, y0 + 8, hz + dz, 102, 0);
                fill(c, hx + 12, y0 + 7, hz + dz, hx + 12, y0 + 8, hz + dz, 102, 0);
            }
            int door = side == 0 ? hx + 12 : hx;                       // both front doors face the street
            fill(c, door, y0 + 1, hz + 5, door, y0 + 3, hz + 6, 0, 0);
            // A pair of real doors under a transom (it was a bare hole), and a porch one board deep at
            // ground level with its posts either side of the door: the three-deep raised porch stood two
            // blocks out into the street (structure audit 2026-09-23).
            int face = side == 0 ? 0 : 2, px = side == 0 ? door + 1 : door - 1;
            put(c, door, y0 + 1, hz + 5, 64, face);
            put(c, door, y0 + 2, hz + 5, 64, side == 0 ? 8 : 9);
            put(c, door, y0 + 1, hz + 6, 64, face);
            put(c, door, y0 + 2, hz + 6, 64, side == 0 ? 9 : 8);
            fill(c, door, y0 + 3, hz + 5, door, y0 + 3, hz + 6, 102, 0);
            fill(c, px, y0, hz + 3, px, y0, hz + 9, 5, 1);                // the porch
            for (int dz = 3; dz <= 9; dz += 6) fill(c, px, y0 + 1, hz + dz, px, y0 + 4, hz + dz, 85, 0);
            fill(c, px, y0 + 5, hz + 2, px, y0 + 5, hz + 10, 44, 7);
            // Inside: a table set for four, beds upstairs, and the family.
            fill(c, hx + 3, y0 + 1, hz + 3, hx + 5, y0 + 1, hz + 5, 44, 7);
            for (int i = 0; i < 4; i++) put(c, hx + 2 + (i % 2) * 4, y0 + 1, hz + 2 + (i / 2) * 4, 53, i % 2);
            put(c, hx + 6, y0 + 4, hz + 6, 89, 0);
            // Beds run north-south, so they face south (data 3 split every one into two half-beds that pop).
            for (int i = 0; i < 2; i++) {
                put(c, hx + 2 + i * 7, y0 + 6, hz + 3, 26, 0);
                put(c, hx + 2 + i * 7, y0 + 6, hz + 4, 26, 8);
            }
            put(c, hx + 6, y0 + 9, hz + 6, 89, 0);
            // The ladder climbs the north wall it hangs on (it stood two blocks out with nothing behind it).
            put(c, hx + 2, y0 + 5, hz + 1, 0, 0);
            fill(c, hx + 2, y0 + 1, hz + 1, hx + 2, y0 + 5, hz + 1, 65, 3);
            mannequin(c, hx + 4, y0 + 1, hz + 2, coat);
            mannequin(c, hx + 8, y0 + 1, hz + 8, 15);
            mannequin(c, hx + 3, y0 + 6, hz + 8, 0);
            // The rest of a house somebody could live in (structure audit 2026-09-23): a kitchen along the
            // back, the stove moved into it; a sofa facing the radio and the shelf; more lamps; nightstands,
            // a runner and a wardrobe upstairs. Fixed places, clear of both doors and the ladder.
            put(c, hx + 7, y0 + 1, hz + 11, 5, 1);
            put(c, hx + 7, y0 + 2, hz + 11, 140, 0);
            put(c, hx + 8, y0 + 1, hz + 11, 58, 0);
            put(c, hx + 9, y0 + 1, hz + 11, 118, 3);
            put(c, hx + 10, y0 + 1, hz + 11, 61, 2);
            fill(c, hx + 11, y0 + 1, hz + 3, hx + 11, y0 + 2, hz + 3, 47, 0);
            put(c, hx + 11, y0 + 1, hz + 4, 84, 0);
            fill(c, hx + 9, y0 + 1, hz + 3, hx + 9, y0 + 1, hz + 4, 134, 1);
            fill(c, hx + 10, y0 + 1, hz + 3, hx + 10, y0 + 1, hz + 4, 171, 14);
            put(c, hx + 3, y0 + 4, hz + 9, 89, 0);
            put(c, hx + 9, y0 + 4, hz + 3, 89, 0);
            put(c, hx + 1, y0 + 6, hz + 4, 126, 1);
            put(c, hx + 10, y0 + 6, hz + 4, 126, 1);
            fill(c, hx + 4, y0 + 6, hz + 4, hx + 7, y0 + 6, hz + 4, 171, 14);
            fill(c, hx + 11, y0 + 6, hz + 10, hx + 11, y0 + 7, hz + 11, 5, 1);
            fill(c, hx + 10, y0 + 6, hz + 10, hx + 10, y0 + 7, hz + 11, 96, 6);
            put(c, hx + 3, y0 + 9, hz + 3, 89, 0);
            put(c, hx + 9, y0 + 9, hz + 9, 89, 0);
            graded(c, s, hx + 10, y0 + 1, hz + 2, chestRng(s, hx + 10, y0 + 1, hz + 2), TOWN_LOOT, false);
            graded(c, s, hx + 9, y0 + 6, hz + 9, chestRng(s, hx + 9, y0 + 6, hz + 9), TOWN_LOOT, side == 1);
            mob(c, hx + 6, y0 + 1, hz + 8, EntityType.HUSK);
            mob(c, hx + 6, y0 + 6, hz + 6, EntityType.ZOMBIE);
            // The garage, beside its own house and off the street (structure audit 2026-09-23): at hx+13 / hx-9
            // both stood on the carriageway against the front door. gh stands where hz stood in the old plan.
            int gx = side == 0 ? hx : hx + 4, gh = side == 0 ? hz + 11 : hz - 10;
            shell(c, gx, y0 + 1, gh + 2, gx + 8, y0 + 5, gh + 8, 159, trim);
            fill(c, gx + 1, y0 + 1, gh + 3, gx + 7, y0 + 4, gh + 7, 0, 0);
            fill(c, gx + 1, y0 + 6, gh + 2, gx + 7, y0 + 6, gh + 8, 44, 7);
            fill(c, side == 0 ? gx + 8 : gx, y0 + 1, gh + 4, side == 0 ? gx + 8 : gx, y0 + 3, gh + 6, 0, 0);
            fill(c, side == 0 ? gx + 1 : gx, y0, gh + 3, side == 0 ? gx + 8 : gx + 7, y0, gh + 7, 251, 8);
            fill(c, gx + 2, y0 + 1, gh + 4, gx + 5, y0 + 2, gh + 6, 43, 8);     // the car (43:8, was iron block)
            fill(c, gx + 3, y0 + 3, gh + 4, gx + 4, y0 + 3, gh + 6, 20, 0);
            int bench = side == 0 ? gx + 1 : gx + 7;                    // workbench and oil drum at the back
            put(c, bench, y0 + 1, gh + 3, 58, 0);
            put(c, bench, y0 + 1, gh + 7, 118, 0);
            if (side == 0) {
                // THE SURPRISE: the shelter under the first garage. Room, shell and floor first, then the shaft:
                // the room's clearance took the lower ladder and the shell's lid closed the shaft over it, so
                // the ladder went three blocks down onto the lid (structure audit 2026-09-23).
                int sy = y0 - 9;
                fill(c, gx + 1, sy, gh + 1, gx + 8, sy + 4, gh + 8, 0, 0);
                shell(c, gx, sy - 1, gh, gx + 9, sy + 5, gh + 9, 251, 8);
                fill(c, gx + 1, sy - 1, gh + 1, gx + 8, sy - 1, gh + 8, 251, 15);
                for (int i = 0; i < 3; i++) {
                    put(c, gx + 2, sy, gh + 2 + i * 2, 26, 0);
                    put(c, gx + 2, sy, gh + 3 + i * 2, 26, 8);
                }
                fill(c, gx + 7, sy, gh + 2, gx + 7, sy + 2, gh + 4, 47, 0);
                put(c, gx + 5, sy + 4, gh + 5, 89, 0);
                put(c, gx + 6, sy, gh + 7, 118, 3);
                mannequin(c, gx + 4, sy, gh + 2, 15);
                mob(c, gx + 5, sy, gh + 6, EntityType.HUSK);
                fill(c, gx + 7, sy + 5, gh + 7, gx + 7, y0, gh + 7, 0, 0);
                fill(c, gx + 7, sy, gh + 7, gx + 7, y0 - 1, gh + 7, 65, 4);
                fill(c, gx + 8, sy, gh + 7, gx + 8, sy + 4, gh + 7, 251, 8);   // the post the ladder hangs on
                put(c, gx + 7, y0, gh + 7, 96, 10);                            // the hatch in the garage floor
                // Its own generator, as for graded(): trove() only draws in the chunk that holds it, so the yard
                // figures and burn marks drawn after it came out differently in each chunk (a figure split at a seam).
                trove(c, gx + 8, sy, gh + 8, chestRng(s, gx + 8, sy, gh + 8), TOWN_LOOT, TOWN_HOARD, 14 + r.nextInt(6));
            }
        }
        // The bus, across the street at the north end.
        int bx = x0 + 12, bz = z0 + 2;
        fill(c, bx, y0 + 1, bz, bx + 18, y0 + 3, bz + 3, 251, 4);
        fill(c, bx + 1, y0 + 1, bz + 1, bx + 17, y0 + 2, bz + 2, 0, 0);
        fill(c, bx + 1, y0 + 2, bz, bx + 17, y0 + 2, bz, 95, 4);
        fill(c, bx + 1, y0 + 2, bz + 3, bx + 17, y0 + 2, bz + 3, 95, 4);
        fill(c, bx, y0 + 4, bz, bx + 18, y0 + 4, bz + 3, 251, 4);
        fill(c, bx + 6, y0 + 1, bz + 3, bx + 6, y0 + 2, bz + 3, 0, 0);   // its door, onto the street (it was sealed)
        // One row of seats along the far side and the aisle along the door side (structure audit 2026-09-23): a
        // seat in both rows every third block walled the two-wide, two-high cabin into compartments, so the
        // driver's chest and the husks' spawner could not be reached from the door.
        for (int i = 2; i <= 16; i += 3) put(c, bx + i, y0 + 1, bz + 1, 53, 2);
        put(c, bx, y0 + 1, bz + 1, 251, 15); put(c, bx + 18, y0 + 1, bz + 2, 251, 15);   // wheels, were coal block
        mannequin(c, bx + 4, y0 + 1, bz + 2, 11);
        graded(c, s, bx + 16, y0 + 1, bz + 2, chestRng(s, bx + 16, y0 + 1, bz + 2), TOWN_LOOT, false);
        mob(c, bx + 9, y0 + 1, bz + 1, EntityType.HUSK);
        // The yard: a picnic set, a swing frame, and the rest of them standing about.
        fill(c, x0 + 6, y0 + 1, z0 + 26, x0 + 10, y0 + 1, z0 + 28, 44, 7);
        put(c, x0 + 5, y0 + 1, z0 + 27, 53, 1); put(c, x0 + 11, y0 + 1, z0 + 27, 53, 0);
        for (int i = 0; i < 2; i++) {
            fill(c, x0 + 14 + i * 5, y0 + 1, z0 + 30, x0 + 14 + i * 5, y0 + 4, z0 + 30, 85, 0);
        }
        fill(c, x0 + 14, y0 + 4, z0 + 30, x0 + 19, y0 + 4, z0 + 30, 85, 0);
        for (int i = 0; i < 14; i++) {
            int px = x0 + 2 + r.nextInt(41), pz = z0 + 2 + r.nextInt(33);
            if (px > mx - 6 && px < mx + 6) continue;
            int coat = r.nextInt(16);
            // Only on open lawn (structure audit 2026-09-23): they were built into house walls. Same draws.
            if (!lawn(px - x0, pz - z0)) continue;
            mannequin(c, px, y0 + 1, pz, coat);
        }
        // The camera tower, the perimeter wire, and the burn beyond it.
        int tx = x0 + 38, tz = z0 + 4;
        for (int dy = 1; dy <= 14; dy++) {
            // Legs: smooth stone double slab 43:8 (was iron block).
            put(c, tx, y0 + dy, tz, 43, 8); put(c, tx + 3, y0 + dy, tz, 43, 8);
            put(c, tx, y0 + dy, tz + 3, 43, 8); put(c, tx + 3, y0 + dy, tz + 3, 43, 8);
            if (dy % 3 == 0) shell(c, tx, y0 + dy, tz, tx + 3, y0 + dy, tz + 3, 101, 0);
        }
        fill(c, tx - 1, y0 + 15, tz - 1, tx + 4, y0 + 15, tz + 4, 44, 7);
        shell(c, tx - 1, y0 + 16, tz - 1, tx + 4, y0 + 17, tz + 4, 85, 0);
        fill(c, tx, y0 + 16, tz, tx + 3, y0 + 17, tz + 3, 0, 0);
        put(c, tx + 1, y0 + 16, tz + 1, 169, 0);
        // The ladder runs up the outside of the north-west leg, which stands whole to the deck, and out through
        // the rail onto it: it replaced that leg, hung on nothing and stopped under the deck (2026-09-23).
        fill(c, tx, y0 + 1, tz, tx, y0 + 15, tz, 43, 8);
        fill(c, tx - 1, y0 + 1, tz, tx - 1, y0 + 15, tz, 65, 4);
        fill(c, tx - 1, y0 + 16, tz, tx - 1, y0 + 17, tz, 0, 0);
        mob(c, tx + 1, y0 + 16, tz + 2, EntityType.SKELETON);
        graded(c, s, tx + 2, y0 + 16, tz + 1, chestRng(s, tx + 2, y0 + 16, tz + 1), TOWN_LOOT, true);
        for (int dx = 0; dx <= 44; dx++) for (int dz = 0; dz <= 36; dz++) {
            if (dx != 0 && dx != 44 && dz != 0 && dz != 36) continue;
            if (dx > mx - x0 - 5 && dx < mx - x0 + 5 && (dz == 0 || dz == 36)) continue;
            put(c, x0 + dx, y0 + 1, z0 + dz, 85, 0);
            if ((dx + dz) % 6 == 0) { put(c, x0 + dx, y0 + 2, z0 + dz, 251, 4); put(c, x0 + dx, y0 + 3, z0 + dz, 251, 15); }
            else fill(c, x0 + dx, y0 + 2, z0 + dz, x0 + dx, y0 + 3, z0 + dz, 101, 0);
        }
        for (int i = 0; i < 60; i++) {
            int px = x0 + r.nextInt(45), pz = z0 + r.nextInt(37);
            if (px > x0 + 4 && px < x1 - 4 && pz > z0 + 4 && pz < z1 - 4) continue;
            // Scorch is black concrete (was coal block: no valuables in builds). The worn track is coarse
            // dirt: grass path under the fence line or a terrain lip turned to dirt the moment it was
            // placed (structure audit 2026-09-22). Same draw as before.
            boolean scorch = r.nextInt(3) == 0;
            put(c, px, y0, pz, scorch ? 251 : 3, scorch ? 15 : 1);
        }
        mob(c, mx, y0 + 1, z0 + 18, EntityType.CREEPER);
        mob(c, mx - 3, y0 + 1, z0 + 30, EntityType.HUSK);
    }
}
