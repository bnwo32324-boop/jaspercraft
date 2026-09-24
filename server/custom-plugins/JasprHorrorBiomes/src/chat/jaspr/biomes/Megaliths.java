package chat.jaspr.biomes;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Megaliths: the second tier of set pieces, larger than the Landmarks and mostly buried.
 *
 * Landmarks are things you walk up to. These are things you walk into and lose an hour in:
 * a computer that is also a cathedral, two military bases at opposite ends of the same
 * idea, a shopping centre with the roof down, a sewer big enough to get lost in, an
 * airliner spread over two hundred metres of ground, a shaft full of pods, a cathedral
 * built round a boss, and three separate descents into a tech base that stopped being one.
 *
 * Every one of them is its own architecture, its own loot table and its own idea of what
 * will kill you there. Nothing in here reuses another structure's chest contents, and no
 * two of them threaten you the same way.
 */
public final class Megaliths {
    private Megaliths() { }

    /* Lattice spacing in chunks, all mutually distinct and distinct from Landmarks'. */
    private static final int AM_CELL = 92, OUTPOST_CELL = 81, GARRISON_CELL = 124;
    private static final int MALL_CELL = 98, SEWER_CELL = 106, AIRLINER_CELL = 86;
    private static final int MATRIX_CELL = 130, SOULS_CELL = 104;
    private static final int DOOM1_CELL = 88, DOOM2_CELL = 90, DOOM3_CELL = 94;

    public static void populate(World w, Chunk c, Terrain t, Caves caves) {
        // disabled-structures.txt (3.23.0) stops new placement only. located() and the other
        // register lookups are not gated, so copies already built are still recognised.
        if (!DisabledStructures.any("AM")) amCore(c, t);
        if (!DisabledStructures.any("The Outpost")) outpost(c, t);
        if (!DisabledStructures.any("The Garrison")) garrison(c, t);
        if (!DisabledStructures.any("The Super Mall", "The Store")) mall(c, t);
        if (!DisabledStructures.any("The Interceptor")) sewer(c, t);
        if (!DisabledStructures.any("Flight 226", "The Airliner")) airliner(c, t);
        if (!DisabledStructures.any("The Field")) matrix(c, t);
        if (!DisabledStructures.any("The Hallowed Reach")) souls(c, t);
        if (!DisabledStructures.any("Phobos Anomaly")) doomOne(c, t);
        if (!DisabledStructures.any("The Gate")) doomTwo(c, t);
        if (!DisabledStructures.any("Delta Labs")) doomThree(c, t);
    }

    // == placement ===============================================================

    // == the claim register ======================================================
    /**
     * Which set piece gets a patch of ground when two of them want it.
     *
     * Every site in this plugin is a pure function of the seed and a lattice cell, and
     * with twenty-nine lattices running at once a few of them inevitably land on top of
     * one another -- measured at about one site in fifty. Rather than shuffle the cells
     * until the clashes move somewhere else, each structure is given a rank and asks,
     * before it builds, whether anything above it in this list has already claimed the
     * ground plus a two block cordon. The order runs rarest first: a structure that only
     * fits on one cell in six is worth more than one that fits nearly anywhere. The
     * cordon is deliberately thin: two large ruins four blocks apart read as a district,
     * while dropping one of them costs the map a place worth walking to.
     *
     * The test costs one modulo per higher-ranked structure in the common case, because
     * a lattice that does not anchor within four chunks of the footprint is rejected
     * before any terrain is sampled.
     */
    static final int RANK_COLUMBIA = 0, RANK_RAPTURE = 1, RANK_OLDTOWN = 2, RANK_ALIEN = 3;
    static final int RANK_MATRIX = 4, RANK_CITY = 5, RANK_PYRAMID = 6, RANK_GARRISON = 7;
    static final int RANK_NUCLEAR = 8, RANK_HIGHWAY = 9, RANK_SITE19 = 10, RANK_STRIP = 11;
    static final int RANK_CHANCEL = 12, RANK_VAULT = 13, RANK_THEMEPARK = 14, RANK_CHOIR = 15;
    static final int RANK_STORE = 16, RANK_SEWER = 17, RANK_WATERPARK = 18, RANK_SOULS = 19;
    static final int RANK_PIT = 20, RANK_KENNELS = 21, RANK_WARREN = 22, RANK_VIEWING = 23;
    static final int RANK_SIGNAL = 24, RANK_EMBER = 25, RANK_TIDEWELL = 26, RANK_SANCTUM = 27;
    static final int RANK_RELIQUARY = 28, RANK_OXIDE = 29, RANK_FROSTVAULT = 30, RANK_HIVE = 31;
    static final int RANK_IKEA = 32, RANK_MALL = 33, RANK_POCKET = 34, RANK_DOOM3 = 35;
    static final int RANK_TESTTOWN = 36, RANK_MEGATON = 37, RANK_AM = 38, RANK_DOOM2 = 39;
    static final int RANK_DOOM1 = 40, RANK_SPIRE = 41, RANK_AIRLINER = 42, RANK_HOTEL = 43;
    static final int RANK_STAIR = 44, RANK_LODGE = 45, RANK_OUTPOST = 46, RANK_MANOR = 47;
    static final int RANK_TACO = 48, RANK_ARCOLOGY = 49, RANK_MCD = 50, RANK_PRECINCT = 51;
    static final int RANK_MOTEL = 52, RANK_HOUSE = 53, RANK_VILLAGE = 54, RANK_STAIRWELL = 55;
    static final int RANK_FOUNDRY = 56, RANK_STREET = 57, RANK_TOWER = 58, RANK_BLOCKS = 59;
    static final int RANK_ROOM = 60, RANK_METRO = 61;

    /* Row 1 (Rapture) says 121 and 45x45 since the 3.23 structure audit: that is what Wonders.rapture
     * has always anchored on. The row used to say 136 and 55x55, so located() named empty sea bed as
     * Rapture and missed every real one. claimed() still asks the old row; see RAPTURE_CLAIM_CELL. */
    private static final int[] C_CELL = {
        138, 121, 134, 132, 130, 128, 126, 124, 122, 120, 118, 116, 114, 112, 111, 110, 108,
        106, 105, 104, 97, 91, 89, 95, 69, 43, 55, 53, 59, 67, 35, 102, 100, 98,
        96, 94, 73, 93, 92, 90, 88, 87, 86, 85, 84, 83, 81, 80, 79, 78, 77,
        76, 75, 74, 72, 71, 70, 68, 66, 64, 62, 58,
    };
    private static final long[] C_SALT = {
        0x434F4C55L, 0x52415054L, 0x464F5543L, 0x414C49454EL, 0x4D545258L, 0x43495459L, 0x50595241L,
        0x47415252L, 0x4E554B45L, 0x48574159L, 0x53313900L, 0x56454741L, 0x52554E45L, 0x5641554CL,
        0x5041524BL, 0x43554C54L, 0x33303038L, 0x53455752L, 0x57415452L, 0x534F554CL, 0x36383200L,
        0x39333900L, 0x39363600L, 0x30393600L, 0x31343731L, 0x454D424552L, 0x54494445L, 0x47524545L,
        0x44555354L, 0x4F58494445L, 0x46524F5354L, 0x48495645L, 0x494B4541L, 0x4D414C4CL, 0x31303600L,
        0x444F4F4D33L, 0x54455354L, 0x4D454741L, 0x414D434F5245L, 0x444F4F4D32L, 0x444F4F4D31L, 0x54575200L,
        0x504C414EL, 0x484F54454CL, 0x53544149L, 0x4C4F444745L, 0x4F555450L, 0x4D414E4F52L, 0x5441434FL,
        0x4E454F4EL, 0x4D43444EL, 0x50524543L, 0x4D4F54454CL, 0x484F574C00L, 0x56494C4CL, 0x30383700L,
        0x42524153L, 0x53545254L, 0x544F5752L, 0x424C4B53L, 0x524F4F4DL, 0x4D455452L,
    };
    /*
     * 3.25.0 secondary lattices (StructureRates), one per register entry, each with its own salt. A secondary
     * site yields to everything older (secondaryFree), so a lattice sqrt 2 wider than the primary -- half the
     * sites -- would only have added 20-47% (measured over 96,000 x 96,000 blocks). Each cell below is that
     * sqrt 2 lattice rescaled by the measured shortfall, so that every set piece is built about 1.5 times as
     * often as under 3.24 (rates probe, SA/work/r-rates). The large, rare set pieces lose the most ground to
     * older neighbours, so theirs are the densest.
     */
    private static final int[] C_CELL2 = {
        137, 151, 127, 134, 157, 134, 122, 131, 118, 137, 134, 134, 131, 134, 118, 127, 131,
        113, 122, 127, 118, 118, 106, 122, 89, 53, 67, 61, 61, 79, 43, 122, 118, 113,
        118, 122, 83, 113, 113, 109, 113, 107, 103, 109, 107, 109, 106, 106, 106, 101, 101,
        97, 97, 101, 89, 97, 94, 94, 89, 86, 86, 79,
    };
    private static final long[] C_SALT2 = new long[C_SALT.length];
    static {
        if (C_CELL2.length != C_CELL.length) throw new IllegalStateException("secondary lattice table");
        for (int k = 0; k < C_CELL.length; k++) C_SALT2[k] = StructureRates.secondarySalt(C_SALT[k]);
    }

    /** The secondary lattice of register entry `rank` (a builder's own cell is used only past the table). */
    static int cell2(int rank, int cell) {
        return rank >= 0 && rank < C_CELL2.length ? C_CELL2[rank] : StructureRates.secondaryCell(cell);
    }
    static long salt2(int rank, long salt) {
        return rank >= 0 && rank < C_SALT2.length ? C_SALT2[rank] : StructureRates.secondarySalt(salt);
    }
    private static final int[] C_SX = {
        51, 45, 61, 57, 45, 61, 49, 57, 57, 61, 53, 45, 43, 45, 55, 43, 45,
        61, 45, 39, 37, 35, 41, 31, 25, 33, 33, 33, 33, 33, 33, 43, 41, 41,
        37, 39, 45, 41, 33, 35, 37, 21, 49, 33, 21, 29, 29, 21, 25, 15, 27,
        21, 31, 17, 23, 15, 19, 25, 15, 25, 13, 23,
    };
    private static final int[] C_SZ = {
        51, 45, 61, 57, 45, 61, 49, 45, 49, 29, 45, 45, 35, 37, 47, 35, 45,
        61, 41, 39, 37, 27, 41, 27, 21, 45, 45, 45, 45, 45, 45, 35, 37, 35,
        37, 29, 37, 41, 29, 33, 31, 21, 25, 25, 21, 25, 25, 17, 21, 15, 23,
        17, 19, 15, 19, 15, 15, 13, 15, 15, 11, 13,
    };
    /** 0 stands on the ground, 1 is buried in rock, 2 sits on a sea bed. */
    private static final int[] C_MODE = {
        0, 2, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 0,
        0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
    };
    /** A slope limit for modes 0 and 2; the height that has to fit under the rock for mode 1. */
    private static final int[] C_FIT = {
        9, 8, 10, 10, 64, 26, 8, 9, 8, 12, 20, 7, 7, 18, 8, 7, 14,
        16, 7, 8, 20, 14, 12, 16, 7, 7, 7, 7, 7, 7, 7, 16, 7, 7,
        14, 11, 6, 9, 26, 16, 12, 6, 7, 6, 10, 6, 6, 6, 6, 8, 6,
        6, 5, 7, 8, 56, 6, 5, 8, 7, 5, 9,
    };
    /** How far a site reaches above its floor, used to say which one you are standing in. */
    private static final int[] C_HEIGHT = {
        40, 24, 30, 26, 64, 26, 30, 18, 34, 18, 20, 42, 34, 18, 26, 34, 14,
        16, 22, 34, 20, 14, 12, 16, 26, 22, 22, 22, 22, 22, 22, 16, 18, 22,
        14, 11, 16, 20, 26, 16, 12, 62, 12, 34, 22, 12, 12, 14, 12, 30, 14,
        14, 12, 18, 14, 56, 20, 10, 26, 20, 12, 12,
    };
    /**
     * The weather a site insists on, or empty for anywhere.
     *
     * Six of these are the same temple built six ways, and each one only exists under
     * one sky: the ember stair wants ash, the tidewell wants mist, and so on. Gating
     * on the biome the anchor stands in rather than on a second lattice means the
     * variants can never turn up beside each other, and going looking for a
     * particular one means going to the right part of the world.
     */
    private static final String[] C_ATMO = {
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "ash", "mist", "spores", "dust", "rust",
        "snow", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "", "", "", "", "",
        "", "",
    };
    /* Which source file each set piece came from, in register order. */
    static final String[] C_GROUP = {
        "Wonders", "Wonders", "Metropolis", "Relics",
        "Megaliths", "Relics", "Wonders", "Megaliths",
        "Relics", "Metropolis", "Anomalies", "Relics",
        "Wonders", "Relics", "Wonders", "Wonders",
        "Anomalies", "Megaliths", "Wonders", "Megaliths",
        "Breach", "Breach", "Breach", "Breach",
        "Breach", "Temples", "Temples", "Temples",
        "Temples", "Temples", "Temples", "Anomalies",
        "Relics", "Megaliths", "Anomalies", "Megaliths",
        "Temples", "Relics", "Megaliths", "Megaliths",
        "Megaliths", "Metropolis", "Megaliths", "Metropolis",
        "Landmarks", "Anomalies", "Megaliths", "Landmarks",
        "Metropolis", "Landmarks", "Relics", "Landmarks",
        "Metropolis", "Landmarks", "Landmarks", "Anomalies",
        "Landmarks", "Landmarks", "Landmarks", "Landmarks",
        "Landmarks", "Landmarks",
    };
    static final String[] C_KIND = {
        "surface", "underwater", "surface", "surface", "buried", "buried",
        "surface", "surface", "surface", "surface", "buried", "surface",
        "surface", "buried", "surface", "surface", "buried", "buried",
        "surface", "surface", "buried", "buried", "buried", "buried",
        "surface", "surface", "surface", "surface", "surface", "surface",
        "surface", "buried", "surface", "surface", "buried", "buried",
        "surface", "surface", "buried", "buried", "buried", "surface",
        "surface", "surface", "surface", "surface", "surface", "surface",
        "surface", "surface", "surface", "surface", "surface", "surface",
        "surface", "buried", "surface", "surface", "surface", "surface",
        "surface", "surface",
    };
    static final String[] C_NAME = {
        "Columbia", "Rapture", "Old Town", "The Visitor",
        "The Field", "The Buried City", "The Mastaba", "The Garrison",
        "Ostrovets Station", "The Interchange", "Site-19", "The Strip",
        "The Burnt Chancel", "Vault 44", "Wonderland", "The Long Choir",
        "The Store", "The Interceptor", "The Lagoon", "The Hallowed Reach",
        "The Pit", "The Kennels", "The Warren", "The Viewing Room",
        "The Signal", "The Ember Ziggurat", "The Tidewell", "The Green Sanctum",
        "The Dust Reliquary", "The Oxide Sepulchre", "The Frostvault", "The Hive",
        "The Flatpack", "The Super Mall", "The Corroded Place", "Delta Labs",
        "Survival Town", "Bombfall", "AM", "The Gate",
        "Phobos Anomaly", "The Spire", "Flight 226", "The Grand Meridian",
        "The Impossible Stair", "The Lodge", "The Outpost", "Spencer Manor",
        "The Bell", "The Neon Arcology", "The Golden Arches", "Raccoon Precinct",
        "The Sundowner", "The Walking House", "The Village", "The Stairwell",
        "The Brass Foundry", "Raccoon Street", "The Lost Tower", "The Lost Blocks",
        "The Room", "The Lost Metro",
    };
    static final String[] C_BLURB = {
        "a white city that never got off the ground",
        "a city under the sea, and the sea is winning",
        "a downtown that took the blast and stayed standing",
        "something came down here and it was not built by anyone",
        "rows of pods going up past where the light reaches",
        "a city with rock where the sky should be",
        "a pyramid with the lights on inside it",
        "a base built to hold a province",
        "the cores are cold. mostly.",
        "six lanes going nowhere in both directions",
        "six cells, six reasons for the glass being that thick",
        "the lights are still on and nobody is playing",
        "they came for the congregation and left the roof",
        "the door opened from the outside",
        "the rides still turn when the wind gets under them",
        "the congregation is still here and still singing",
        "the doors at the front open onto more store",
        "a sewer for a city that is not up there any more",
        "slides into four feet of standing green",
        "they were losing, and they built this anyway",
        "they gave up on killing it and built a lid instead",
        "it makes the noise of somebody you know",
        "there are no lights down here and that is on purpose",
        "do not look at the photograph",
        "a mast still sending, and something answering it",
        "a stair of black stone with the heat still coming off it",
        "the steps go down into the water and keep going",
        "the forest grew over the door and then into it",
        "sand to the lintel and a hall behind it",
        "iron doors, and everything behind them has gone orange",
        "cut into the ice, and the ice has been cut back",
        "it sealed itself with everybody still inside",
        "everything you need for a home you will not get back to",
        "three storeys of retail with the roof down the middle",
        "not a room -- rooms, laid out wrong",
        "the corridor lights fail one in six",
        "two houses, a bus, and a great many mannequins",
        "a town built in the crater, around what made it",
        "hate. and a face with no mouth on it.",
        "a city block where the rock between the buildings went wrong",
        "a tech base with a nukage moat",
        "twenty floors, and something is waiting on the roof",
        "it came down flat and broke into three",
        "two hundred rooms and every door shut",
        "the stairs disagree about which way is down",
        "a cabin, and a lift under the cellar",
        "a forward base somebody left in a hurry",
        "a colonial house with a great deal of locked door",
        "open late, once",
        "neon over a megablock",
        "two storeys of the same meal, over and over",
        "the last place anybody handed out orders",
        "vacancy, permanently",
        "a house that was never one house",
        "a hill village that kept its gate shut",
        "further down than the rock it is cut into",
        "a workshop built round its chimney",
        "a road that stopped being a road",
        "glass, and a long way down",
        "somebody's downtown, from above",
        "a flat that exists twice",
        "rails, water, and the smell of it",
    };

    /** Four samples in five under the water line, and no rock more than six above it. */
    private static boolean drowned(Terrain t, int x, int z, int sizeX, int sizeZ) {
        int under = 0, count = 0, hi = -999;
        for (int dx = 0; dx < sizeX; dx += 4) for (int dz = 0; dz < sizeZ; dz += 4) {
            int h = t.sample(x + dx, z + dz).y;
            count++;
            if (h <= SEABED) under++;
            if (h > hi) hi = h;
        }
        return under * 5 >= count * 4 && hi <= SEABED + 6;
    }

    private static boolean fits(Terrain t, int k, int x, int z) {
        if (!C_ATMO[k].isEmpty()
                && !C_ATMO[k].equals(t.sample(x + C_SX[k] / 2, z + C_SZ[k] / 2).profile.atmosphere)) return false;
        int lo = 999, hi = -999;
        for (int dx = 0; dx < C_SX[k]; dx += 4) for (int dz = 0; dz < C_SZ[k]; dz += 4) {
            int h = t.sample(x + dx, z + dz).y;
            if (h < lo) lo = h;
            if (h > hi) hi = h;
        }
        switch (C_MODE[k]) {
            case 1:  return lo - 10 - C_FIT[k] >= 5;
            case 2:  return drowned(t, x, z, C_SX[k], C_SZ[k]);
            default: return hi - lo <= C_FIT[k] && lo >= 64 && hi <= 136;
        }
    }

    /** The floor a site sits on: the low corner for a surface one, the seeded depth for a buried one. */
    private static int baseY(Terrain t, int k, int acx, int acz) {
        return baseY(t, k, acx, acz, C_SALT[k]);
    }

    /** baseY on a given lattice: a secondary (3.25.0) site draws its depth from its own salt, as deep() does. */
    private static int baseY(Terrain t, int k, int acx, int acz, long salt) {
        int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999;
        for (int dx = 0; dx < C_SX[k]; dx += 4) for (int dz = 0; dz < C_SZ[k]; dz += 4)
            lo = Math.min(lo, t.sample(x + dx, z + dz).y);
        if (C_MODE[k] != 1) return lo + 1;
        int ceiling = lo - 10 - C_FIT[k];
        if (ceiling < 5) return -1;
        long seed = Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
        return 5 + (int) Math.floorMod(seed >>> 19, (long) (ceiling - 4));
    }

    /**
     * Which named site this position is inside, or -1.
     *
     * The same register that decides where things go decides what to call the place you
     * are standing in, so a structure cannot be announced under a name it does not have
     * or in ground it was never built on. A lattice that does not anchor over this column
     * is rejected on one modulo, so the whole sweep is cheap enough to run per player.
     */
    static int located(Terrain t, int wx, int wy, int wz) {
        for (int k = 0; k < C_CELL.length; k++) {
            int cell = C_CELL[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
            for (int acx = (wx - C_SX[k]) >> 4; acx <= (wx >> 4); acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = (wz - C_SZ[k]) >> 4; acz <= (wz >> 4); acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int x = acx * 16 + 1, z = acz * 16 + 1;
                    if (wx < x || wx >= x + C_SX[k] || wz < z || wz >= z + C_SZ[k]) continue;
                    if (!fits(t, k, x, z)) continue;
                    if (claimed(t, x, z, C_SX[k], C_SZ[k], k)) continue;
                    int base = baseY(t, k, acx, acz);
                    if (base < 0 || wy < base - 10 || wy > base + C_HEIGHT[k] + 6) continue;
                    return k;
                }
            }
        }
        // 3.25.0: the secondary lattices, asked only when no primary site holds this position. A built
        // secondary never overlaps a built primary, so no position that answered before answers otherwise.
        for (int k = 0; k < C_CELL.length; k++) {
            int cell = C_CELL2[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k] + 17L) >>> 3, (long) cell);
            for (int acx = (wx - C_SX[k]) >> 4; acx <= (wx >> 4); acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = (wz - C_SZ[k]) >> 4; acz <= (wz >> 4); acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int x = acx * 16 + 1, z = acz * 16 + 1;
                    if (wx < x || wx >= x + C_SX[k] || wz < z || wz >= z + C_SZ[k]) continue;
                    int base = secondaryBase(t, k, acx, acz);
                    if (base < 0 || wy < base - 10 || wy > base + C_HEIGHT[k] + 6) continue;
                    return k;
                }
            }
        }
        return -1;
    }

    /** {acx, acz, floorY} of the built secondary (3.25.0) site of kind k covering this column, or null. */
    private static int[] secondaryAt(Terrain t, int k, int wx, int wz) {
        int cell = C_CELL2[k];
        int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k]) >>> 3, (long) cell);
        int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k] + 17L) >>> 3, (long) cell);
        for (int acx = (wx - C_SX[k]) >> 4; acx <= (wx >> 4); acx++) {
            if (Math.floorMod(acx, cell) != ax) continue;
            for (int acz = (wz - C_SZ[k]) >> 4; acz <= (wz >> 4); acz++) {
                if (Math.floorMod(acz, cell) != az) continue;
                int x = acx * 16 + 1, z = acz * 16 + 1;
                if (wx < x || wx >= x + C_SX[k] || wz < z || wz >= z + C_SZ[k]) continue;
                int base = secondaryBase(t, k, acx, acz);
                if (base >= 0) return new int[]{acx, acz, base};
            }
        }
        return null;
    }

    /**
     * The floor of the secondary (3.25.0) site of kind k at lattice cell acx, acz when it is really built,
     * else -1: it fits its ground exactly as a primary would (fits(), the builders' own test) and
     * secondaryFree() gives it the ground. acx, acz must lie on the secondary lattice.
     */
    static int secondaryBase(Terrain t, int k, int acx, int acz) {
        int x = acx * 16 + 1, z = acz * 16 + 1;
        if (!fits(t, k, x, z)) return -1;
        if (!secondaryFree(t, x, z, C_SX[k], C_SZ[k], k)) return -1;
        return baseY(t, k, acx, acz, C_SALT2[k]);
    }

    /** Secondary lattice geometry for other placement code (the importer's claim guard): {cell, salt}. */
    static long[] secondaryLattice(int k) { return new long[]{C_CELL2[k], C_SALT2[k]}; }

    /**
     * Where the site containing this position starts, and what its floor is.
     *
     * Returns {originX, originZ, floorY} or null. Runtime services need this to work out
     * how deep into a place something is standing without re-deriving the lattice, which
     * is how the anomalies and the bosses know they are in the right room.
     */
    static int[] originOf(Terrain t, int k, int wx, int wz) {
        int[] second = secondaryAt(t, k, wx, wz);
        if (second != null) return new int[]{second[0] * 16 + 1, second[1] * 16 + 1, second[2]};
        int cell = C_CELL[k];
        int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
        int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
        for (int acx = (wx - C_SX[k]) >> 4; acx <= (wx >> 4); acx++) {
            if (Math.floorMod(acx, cell) != ax) continue;
            for (int acz = (wz - C_SZ[k]) >> 4; acz <= (wz >> 4); acz++) {
                if (Math.floorMod(acz, cell) != az) continue;
                int base = baseY(t, k, acx, acz);
                if (base < 0) return null;
                return new int[]{acx * 16 + 1, acz * 16 + 1, base};
            }
        }
        return null;
    }

    /** A site's world key, so a place found once is not announced again. */
    static String key(Terrain t, int k, int wx, int wz) {
        int[] second = secondaryAt(t, k, wx, wz);
        if (second != null) return k + ".s" + second[0] + "." + second[1];
        int cell = C_CELL[k];
        int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
        int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
        for (int acx = (wx - C_SX[k]) >> 4; acx <= (wx >> 4); acx++) {
            if (Math.floorMod(acx, cell) != ax) continue;
            for (int acz = (wz - C_SZ[k]) >> 4; acz <= (wz >> 4); acz++) {
                if (Math.floorMod(acz, cell) != az) continue;
                return k + "." + acx + "." + acz;
            }
        }
        return k + ".?";
    }

    static int siteCount() { return C_CELL.length; }
    static String siteName(int k) { return C_NAME[k]; }

    /** Gear tier (3.26.0) of a chest at this position inside located() site k: its depth tier 0-4, plus one. */
    static int gearTier(Terrain t, int k, int wx, int wy, int wz) {
        int[] o = originOf(t, k, wx, wz);
        if (o == null) return 1;
        return tier(new Site(o[0], o[2], o[1], o[2], o[2], 0L, C_SX[k], C_SZ[k]), wx, wy, wz) + 1;
    }
    static String siteGroup(int k) { return C_GROUP[k]; }
    static String siteKind(int k) { return C_KIND[k]; }
    static int siteSizeX(int k) { return C_SX[k]; }
    static int siteSizeZ(int k) { return C_SZ[k]; }
    static int siteHeight(int k) { return C_HEIGHT[k]; }
    static String siteBlurb(int k) { return C_BLURB[k]; }

    /**
     * The claim Rapture's register row made before the audit corrected it: lattice 136, 55x55
     * of drowned sea bed. Every set piece ranked below Rapture was placed against this claim, so
     * claimed() keeps asking it: moving the claim to where Raptures really stand would move some
     * of those set pieces and change which of the ones already built are recognised.
     */
    private static final int RAPTURE_CLAIM_CELL = 136, RAPTURE_CLAIM_SIZE = 55;

    /** True when something that outranks this one has already taken the ground. */
    static boolean claimed(Terrain t, int x, int z, int sizeX, int sizeZ, int rank) {
        int c0 = (x >> 4) - 4, c1 = ((x + sizeX) >> 4) + 1;
        int d0 = (z >> 4) - 4, d1 = ((z + sizeZ) >> 4) + 1;
        for (int k = 0; k < rank; k++) {
            boolean old = k == RANK_RAPTURE;       // the pre-audit claim; see RAPTURE_CLAIM_CELL
            int cell = old ? RAPTURE_CLAIM_CELL : C_CELL[k];
            int sx = old ? RAPTURE_CLAIM_SIZE : C_SX[k], sz = old ? RAPTURE_CLAIM_SIZE : C_SZ[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
            for (int acx = c0; acx <= c1; acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = d0; acz <= d1; acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int ox = acx * 16 + 1, oz = acz * 16 + 1;
                    if (ox + sx + 2 <= x || x + sizeX + 2 <= ox) continue;
                    if (oz + sz + 2 <= z || z + sizeZ + 2 <= oz) continue;
                    if (old ? drowned(t, ox, oz, sx, sz) : fits(t, k, ox, oz)) return true;
                }
            }
        }
        return false;
    }

    /**
     * True when a register set piece that is really built -- it fits its ground and nothing that
     * outranks it has the ground, exactly as located() decides -- takes any part of this box,
     * counting the same two block cordon claimed() keeps between set pieces.
     *
     * The dungeon rooms and the expedition catalogue ask this before they build (3.23 structure
     * audit). Both used to be placed without looking, and whichever ran later wrote through the
     * other: the catalogue is stamped first, in the chunk generator, and the rooms run either
     * side of the set pieces in the populator. The set pieces win because they are the ones
     * located() already names; nothing here changes where any of them goes.
     *
     * top is the highest block the asker would write. A set piece reaches from its cellars up
     * to the sky -- the buried ones by their shafts to daylight -- so the only thing it leaves
     * alone is something wholly beneath it: ten blocks under a buried floor (the margin located()
     * already allows) or thirty-two under a surface one, whose lift shafts and footings go that
     * deep. Disabled set pieces keep their ground, as they do in claimed().
     */
    static boolean occupied(Terrain t, int x, int z, int sizeX, int sizeZ, int top) {
        int c0 = (x >> 4) - 4, c1 = ((x + sizeX) >> 4) + 1;
        int d0 = (z >> 4) - 4, d1 = ((z + sizeZ) >> 4) + 1;
        for (int k = 0; k < C_CELL.length; k++) {
            int cell = C_CELL[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
            for (int acx = c0; acx <= c1; acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = d0; acz <= d1; acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int ox = acx * 16 + 1, oz = acz * 16 + 1;
                    if (ox + C_SX[k] + 2 <= x || x + sizeX + 2 <= ox) continue;
                    if (oz + C_SZ[k] + 2 <= z || z + sizeZ + 2 <= oz) continue;
                    if (!fits(t, k, ox, oz) || claimed(t, ox, oz, C_SX[k], C_SZ[k], k)) continue;
                    int base = baseY(t, k, acx, acz);
                    if (base < 0 || top < base - (C_MODE[k] == 1 ? 10 : 32)) continue;
                    return true;
                }
            }
        }
        return false;
    }


    /** occupied(), counting the secondary (3.25.0) sites too: what every NEW catalogue site and room asks. */
    static boolean occupiedAll(Terrain t, int x, int z, int sizeX, int sizeZ, int top) {
        if (occupied(t, x, z, sizeX, sizeZ, top)) return true;
        int c0 = (x >> 4) - 4, c1 = ((x + sizeX) >> 4) + 1;
        int d0 = (z >> 4) - 4, d1 = ((z + sizeZ) >> 4) + 1;
        for (int k = 0; k < C_CELL2.length; k++) {
            int cell = C_CELL2[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k] + 17L) >>> 3, (long) cell);
            for (int acx = c0; acx <= c1; acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = d0; acz <= d1; acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int ox = acx * 16 + 1, oz = acz * 16 + 1;
                    if (ox + C_SX[k] + 2 <= x || x + sizeX + 2 <= ox) continue;
                    if (oz + C_SZ[k] + 2 <= z || z + sizeZ + 2 <= oz) continue;
                    int base = secondaryBase(t, k, acx, acz);
                    if (base < 0 || top < base - (C_MODE[k] == 1 ? 10 : 32)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when a secondary-lattice (3.25.0) site of this rank may have this ground. It is never asked for a
     * primary site, so every primary is placed and recognised exactly as before. A secondary site yields to
     * chunks that predate 3.25.0 and to sanctuary halos; to higher-ranked secondaries that fit (as claimed()
     * ranks primaries); to a built primary of its own kind that shares any chunk with it (a builder is asked
     * once per chunk, so two of one kind must never meet in a chunk); to every built primary of any rank
     * (occupied(), with its two block cordon); and to the older catalogue sites and lattice rooms, which were
     * placed without knowing about it.
     */
    static boolean secondaryFree(Terrain t, int x, int z, int sizeX, int sizeZ, int rank) {
        if (!StructureRates.permits(t.seed, x, z, sizeX, sizeZ)) return false;
        if (StructureRates.nearSanctuary(t, x, z, sizeX, sizeZ)) return false;
        if (secondaryClaimed(t, x, z, sizeX, sizeZ, rank)) return false;
        if (sharesChunk(t, x, z, sizeX, sizeZ, rank)) return false;
        if (occupied(t, x, z, sizeX, sizeZ, 255)) return false;
        if (StructurePlanner.catalogueTier0(t, x, z, sizeX, sizeZ)) return false;
        return !Dungeons.oldRoomNear(t, x, z, sizeX, sizeZ);
    }

    /** claimed() among the secondary lattices: a higher-ranked secondary that fits (and may be placed) outranks. */
    private static boolean secondaryClaimed(Terrain t, int x, int z, int sizeX, int sizeZ, int rank) {
        int c0 = (x >> 4) - 4, c1 = ((x + sizeX) >> 4) + 1;
        int d0 = (z >> 4) - 4, d1 = ((z + sizeZ) >> 4) + 1;
        for (int k = 0; k < rank && k < C_CELL2.length; k++) {
            int cell = C_CELL2[k];
            int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k]) >>> 3, (long) cell);
            int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT2[k] + 17L) >>> 3, (long) cell);
            for (int acx = c0; acx <= c1; acx++) {
                if (Math.floorMod(acx, cell) != ax) continue;
                for (int acz = d0; acz <= d1; acz++) {
                    if (Math.floorMod(acz, cell) != az) continue;
                    int ox = acx * 16 + 1, oz = acz * 16 + 1;
                    if (ox + C_SX[k] + 2 <= x || x + sizeX + 2 <= ox) continue;
                    if (oz + C_SZ[k] + 2 <= z || z + sizeZ + 2 <= oz) continue;
                    if (fits(t, k, ox, oz) && StructureRates.permits(t.seed, ox, oz, C_SX[k], C_SZ[k])) return true;
                }
            }
        }
        return false;
    }

    /** True when a built primary site of kind k reaches any chunk this box (plus eight blocks) reaches. */
    private static boolean sharesChunk(Terrain t, int x, int z, int sizeX, int sizeZ, int k) {
        if (k < 0 || k >= C_CELL.length) return false;
        int a0 = (x - 8) >> 4, a1 = (x + sizeX + 7) >> 4, b0 = (z - 8) >> 4, b1 = (z + sizeZ + 7) >> 4;
        int cell = C_CELL[k];
        int ax = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k]) >>> 3, (long) cell);
        int az = (int) Math.floorMod(Terrain.mix(t.seed + C_SALT[k] + 17L) >>> 3, (long) cell);
        for (int acx = a0 - 6; acx <= a1 + 1; acx++) {
            if (Math.floorMod(acx, cell) != ax) continue;
            for (int acz = b0 - 6; acz <= b1 + 1; acz++) {
                if (Math.floorMod(acz, cell) != az) continue;
                int ox = acx * 16 + 1, oz = acz * 16 + 1;
                if (((ox + C_SX[k] + 7) >> 4) < a0 || ((ox - 8) >> 4) > a1) continue;
                if (((oz + C_SZ[k] + 7) >> 4) < b0 || ((oz - 8) >> 4) > b1) continue;
                if (fits(t, k, ox, oz) && !claimed(t, ox, oz, C_SX[k], C_SZ[k], k)) return true;
            }
        }
        return false;
    }

    static final class Site {
        final int x, y, z, lo, hi, sizeX, sizeZ;
        final long seed;
        Site(int x, int y, int z, int lo, int hi, long s, int sizeX, int sizeZ) {
            this.x = x; this.y = y; this.z = z; this.lo = lo; this.hi = hi; this.seed = s;
            this.sizeX = sizeX; this.sizeZ = sizeZ;
        }
    }

    /**
     * Water lies at sixty-three. A sea bed site wants most of its footprint under this
     * and nothing on it more than six above, which allows the reef a city like that
     * would have grown round itself without allowing it to be built on a hill.
     */
    static final int SEABED = 60;

    /** True when the lattice for this salt anchors at acx,acz and the footprint reaches c. */
    private static boolean anchored(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int acx, int acz) {
        if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) return false;
        if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) return false;
        int x = acx * 16 + 1, z = acz * 16 + 1;
        if (x + sizeX <= c.getX() * 16 || x > c.getX() * 16 + 15) return false;
        if (z + sizeZ <= c.getZ() * 16 || z > c.getZ() * 16 + 15) return false;
        return true;
    }

    /** A site that insists on one sky only stands under it. */
    static boolean weather(Terrain t, int rank, int x, int z) {
        return C_ATMO[rank].isEmpty()
            || C_ATMO[rank].equals(t.sample(x + C_SX[rank] / 2, z + C_SZ[rank] / 2).profile.atmosphere);
    }

    private static long siteSeed(Terrain t, int acx, int acz, long salt) {
        return Terrain.mix(t.seed + acx * 6364136223846793005L + acz * 1442695040888963407L + salt);
    }

    /** Surface anchor: tolerant of slope, reports the range so a builder can pack its own footing. */
    static Site ground(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int maxSlope, int rank) {
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell, salt, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999, hi = -999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
                if (h > hi) hi = h;
            }
            if (!weather(t, rank, x, z)) continue;
            if (hi - lo > maxSlope || lo < 64 || hi > 136) continue;
            if (claimed(t, x, z, sizeX, sizeZ, rank)) continue;
            Site site = new Site(x, lo + 1, z, lo, hi, siteSeed(t, acx, acz, salt), sizeX, sizeZ);
            excavate(c, site, rank);
            return site;
        }
        // 3.25.0: nothing on the primary lattice reaches this chunk -- the secondary one (StructureRates).
        int cell2 = cell2(rank, cell);
        long salt2 = salt2(rank, salt);
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell2, salt2, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999, hi = -999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
                if (h > hi) hi = h;
            }
            if (!weather(t, rank, x, z)) continue;
            if (hi - lo > maxSlope || lo < 64 || hi > 136) continue;
            if (!secondaryFree(t, x, z, sizeX, sizeZ, rank)) continue;
            Site site = new Site(x, lo + 1, z, lo, hi, siteSeed(t, acx, acz, salt2), sizeX, sizeZ);
            excavate(c, site, rank);
            return site;
        }
        return null;
    }

    /*
     * The pad a surface piece stands on (structure audit 2026-09-23, shared by every ground() caller).
     *
     * ground() sets the floor at the lowest sampled corner and footing() only packs downward, so on
     * a slope everything uphill of the low corner stayed where it was: hills through the yards, dunes
     * over the car parks, doorways and ground floors buried, trees growing up through the rooms.
     * Before the builder writes anything, the footprint is now cut down to the floor -- terrain,
     * standing water and trees from site.y up to a tall crown's height over the highest sample --
     * and the terrain outside the footprint is left alone. A column whose grass was cut
     * away gets its grass back on the new top. Leaves just outside the footprint whose tree stood
     * inside it (no log left within four blocks) go too, so no crown is left hanging at the edge.
     * Only the chunk being built is read and written, so every chunk the footprint spans agrees.
     *
     * The crash sites, the crater and the interchange are the exceptions: their design sits in the
     * terrain (a furrow, a crater, piers standing on whatever the ground does), so on those only
     * the vegetation inside the footprint is cleared. Package-visible so an anchor elsewhere that
     * hands out the same Site (Landmarks.site) can clear its pad the same way.
     */
    static void excavate(Chunk c, Site s, int rank) {
        boolean plantsOnly = rank == RANK_HIGHWAY || rank == RANK_ALIEN || rank == RANK_AIRLINER || rank == RANK_MEGATON;
        // Terrain, the biome trees' terracotta/ice crowns and whole trees, as high as a crown stands: a
        // lower cut left an overhang or a crown of those trees floating over the site once it was open.
        int crown = Math.min(250, Math.max(s.hi, s.y) + 28);
        int bx = c.getX() * 16, bz = c.getZ() * 16;
        for (int pass = 0; pass < 2; pass++)                    // the footprint first, then its margin
        for (int wx = Math.max(s.x - 3, bx); wx <= Math.min(s.x + s.sizeX + 2, bx + 15); wx++) {
            for (int wz = Math.max(s.z - 3, bz); wz <= Math.min(s.z + s.sizeZ + 2, bz + 15); wz++) {
                boolean inside = wx >= s.x && wx < s.x + s.sizeX && wz >= s.z && wz < s.z + s.sizeZ;
                if (inside != (pass == 0)) continue;
                boolean grass = false, did = false;
                for (int y = crown; y >= s.y; y--) {
                    Block b = blockAt(c, wx, y, wz);
                    if (b == null) continue;
                    int id = b.getTypeId();
                    if (id == 0) continue;
                    if (!inside) {                          // the margin: only crowns left orphaned
                        if ((id == 18 || id == 161) && !rooted(c, wx, y, wz)) put(c, wx, y, wz, 0, 0);
                        continue;
                    }
                    if (!plant(id) && plantsOnly) continue;
                    if (id == 2) grass = true;
                    put(c, wx, y, wz, 0, 0);
                    did = true;
                }
                Block floor = blockAt(c, wx, s.y - 1, wz);
                if (did && grass && floor != null && floor.getTypeId() == 3) put(c, wx, s.y - 1, wz, 2, 0);
            }
        }
        orphans(c, s, crown, plantsOnly);
    }

    /*
     * Fragments the cut left floating in the margin (structure audit 2026-09-23, merge fix-up).
     * A tree whose trunk stood in the footprint lost its trunk to the cut, but its side branches
     * (horizontal logs), the leaves they kept "rooted" and the terracotta or ice crowns over the
     * margin stayed in the air beside the building; an overhang whose rock went with the footprint
     * did the same. Every piece of anything in the margin ring (three blocks round the footprint) from
     * eight under the floor up to the crown height is followed through its neighbours: a piece that
     * reaches ground (solid below that band, the uncut land beyond the ring, the rock under the floor)
     * stays, a piece confined to the ring with nothing under it is removed. Whatever lies in another
     * chunk counts as holding it up (unknown ground is kept), except the footprint above the floor,
     * which every chunk cuts. Only this chunk is read and written, so the result does not depend on
     * the order in which chunks are populated.
     */
    private static void orphans(Chunk c, Site s, int crown, boolean plantsOnly) {
        int bx = c.getX() * 16, bz = c.getZ() * 16, yMin = Math.max(1, s.y - 8);
        int rx0 = s.x - 3, rx1 = s.x + s.sizeX + 2, rz0 = s.z - 3, rz1 = s.z + s.sizeZ + 2;
        int ax = Math.max(rx0, bx), bxe = Math.min(rx1, bx + 15), az = Math.max(rz0, bz), bze = Math.min(rz1, bz + 15);
        if (ax > bxe || az > bze) return;
        byte[] seen = new byte[16 * 16 * 256];                  // 0 new, 1 held up, 2 removed
        int[] queue = new int[4096];
        for (int wx = ax; wx <= bxe; wx++)
            for (int wz = az; wz <= bze; wz++) {
                if (inFoot(s, wx, wz)) continue;
                for (int y = s.y; y <= crown; y++) {
                    int k = ((y << 8) | ((wz - bz) << 4) | (wx - bx));
                    if (seen[k] != 0 || !filled(c, wx, y, wz)) continue;
                    // Follow the piece.
                    int n = 0, head = 0;
                    boolean held = false;
                    queue[n++] = k;
                    seen[k] = 3;
                    while (head < n && !held) {
                        int q = queue[head++];
                        int qy = q >>> 8, qz = bz + ((q >> 4) & 15), qx = bx + (q & 15);
                        for (int d = 0; d < 6 && !held; d++) {
                            int nx = qx + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = qy + (d == 2 ? 1 : d == 3 ? -1 : 0);
                            int nz = qz + (d == 4 ? 1 : d == 5 ? -1 : 0);
                            if (ny < 1 || ny > 250) continue;
                            boolean here = nx >= bx && nx <= bx + 15 && nz >= bz && nz <= bz + 15;
                            if (inFoot(s, nx, nz)) {
                                if (ny < s.y || plantsOnly) { if (!here || filled(c, nx, ny, nz)) held = true; }
                                continue;                                   // above the floor it is cut
                            }
                            boolean ring = nx >= rx0 && nx <= rx1 && nz >= rz0 && nz <= rz1;
                            if (!here) { held = true; continue; }          // another chunk: unknown ground
                            if (!filled(c, nx, ny, nz)) continue;
                            if (!ring || ny < yMin || ny > crown) { held = true; continue; }
                            int nk = ((ny << 8) | ((nz - bz) << 4) | (nx - bx));
                            if (seen[nk] == 1) { held = true; continue; }
                            if (seen[nk] != 0) continue;
                            if (n >= queue.length) { held = true; continue; } // too big to be a loose piece
                            seen[nk] = 3;
                            queue[n++] = nk;
                        }
                    }
                    for (int i = 0; i < n; i++) {
                        int q = queue[i];
                        if (held) { seen[q] = 1; continue; }
                        seen[q] = 2;
                        put(c, bx + (q & 15), q >>> 8, bz + ((q >> 4) & 15), 0, 0);
                    }
                    // Cells still queued but not expanded belong to the held piece too.
                }
            }
    }

    private static boolean inFoot(Site s, int wx, int wz) {
        return wx >= s.x && wx < s.x + s.sizeX && wz >= s.z && wz < s.z + s.sizeZ;
    }

    /** Anything but air and liquid (plants included: they go with the piece they grow on). */
    private static boolean filled(Chunk c, int wx, int y, int wz) {
        Block b = blockAt(c, wx, y, wz);
        return b != null && b.getTypeId() != 0 && !b.isLiquid();
    }

    /** A leaf block with a log within four blocks (the vanilla decay reach); unknown ground outside the chunk counts. */
    static boolean rooted(Chunk c, int wx, int y, int wz) {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -4; dy <= 4; dy++) {
            Block b = blockAt(c, wx + dx, y + dy, wz + dz);
            if (b == null) { if (y + dy >= 1 && y + dy <= 250) return true; continue; }
            int id = b.getTypeId();
            if (id == 17 || id == 162) return true;
        }
        return false;
    }

    /** Trees and ground cover: logs, leaves, huge mushrooms, vines, grass, flowers, bushes, cactus, reeds, snow. */
    static boolean plant(int id) {
        return id == 17 || id == 162 || id == 18 || id == 161 || id == 99 || id == 100 || id == 106 || id == 31
            || id == 32 || id == 37 || id == 38 || id == 175 || id == 6 || id == 81 || id == 83 || id == 78 || id == 111;
    }

    /**
     * Buried anchor.
     *
     * Nothing above ground constrains these, so the test is simply whether the column has
     * room: the roof is kept ten blocks under the shallowest point of the footprint, and
     * the floor is dropped to whatever depth is left. A structure that cannot fit under the
     * terrain it landed on is skipped rather than shaved, because half a megastructure
     * poking out of a hillside looks like a bug and not a ruin.
     */
    static Site deep(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int height, int rank) {
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell, salt, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
            }
            if (!weather(t, rank, x, z)) continue;
            int ceiling = lo - 10 - height;
            if (ceiling < 5) continue;
            if (claimed(t, x, z, sizeX, sizeZ, rank)) continue;
            long seed = siteSeed(t, acx, acz, salt);
            int y = 5 + (int) Math.floorMod(seed >>> 19, (long) (ceiling - 4));
            Site site = new Site(x, y, z, y, y + height, seed, sizeX, sizeZ);
            bed(c, site);
            return site;
        }
        // 3.25.0: nothing on the primary lattice reaches this chunk -- the secondary one (StructureRates).
        int cell2 = cell2(rank, cell);
        long salt2 = salt2(rank, salt);
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell2, salt2, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
            }
            if (!weather(t, rank, x, z)) continue;
            int ceiling = lo - 10 - height;
            if (ceiling < 5) continue;
            if (!secondaryFree(t, x, z, sizeX, sizeZ, rank)) continue;
            long seed = siteSeed(t, acx, acz, salt2);
            int y = 5 + (int) Math.floorMod(seed >>> 19, (long) (ceiling - 4));
            Site site = new Site(x, y, z, y, y + height, seed, sizeX, sizeZ);
            bed(c, site);
            return site;
        }
        return null;
    }

    /*
     * A buried floor over a cave (structure audit 2026-09-23, shared by every deep() caller).
     * deep() picks its depth without looking under the footprint, so a one-block floor could span a
     * cavern and show from below as a thin built ceiling hanging over the void. The two courses under
     * the footprint are packed with stone wherever they are cave air or liquid, before the builder
     * runs (anything it cuts below its floor it still cuts), so a cave meets rock under the floor.
     */
    private static void bed(Chunk c, Site s) {
        int x0 = Math.max(s.x, c.getX() * 16), x1 = Math.min(s.x + s.sizeX - 1, c.getX() * 16 + 15);
        int z0 = Math.max(s.z, c.getZ() * 16), z1 = Math.min(s.z + s.sizeZ - 1, c.getZ() * 16 + 15);
        for (int wx = x0; wx <= x1; wx++) for (int wz = z0; wz <= z1; wz++) for (int y = s.y - 2; y < s.y; y++) {
            Block b = blockAt(c, wx, y, wz);
            if (b != null && (b.getTypeId() == 0 || b.isLiquid())) put(c, wx, y, wz, 1, 0);
        }
    }

    /**
     * Sea bed anchor.
     *
     * Water in this world stands at sixty-three and never flows, so anything built on a
     * bed below it is sealed in by the sea rather than by a wall: the builder hollows its
     * own rooms and the ocean does the rest. The test is simply that the highest corner
     * of the footprint is under the water line with room to spare, and that the bed is
     * not so broken up that a floor would hang off it.
     */
    static Site sunken(Terrain t, Chunk c, int cell, long salt, int sizeX, int sizeZ, int maxSlope, int rank) {
        int span = (Math.max(sizeX, sizeZ) >> 4) + 1;
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell, salt, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999, hi = -999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
                if (h > hi) hi = h;
            }
            if (!weather(t, rank, x, z)) continue;
            if (!drowned(t, x, z, sizeX, sizeZ)) continue;
            if (claimed(t, x, z, sizeX, sizeZ, rank)) continue;
            return new Site(x, lo + 1, z, lo, hi, siteSeed(t, acx, acz, salt), sizeX, sizeZ);
        }
        // 3.25.0: nothing on the primary lattice reaches this chunk -- the secondary one (StructureRates).
        int cell2 = cell2(rank, cell);
        long salt2 = salt2(rank, salt);
        for (int ox = -span; ox <= span; ox++) for (int oz = -span; oz <= span; oz++) {
            int acx = c.getX() + ox, acz = c.getZ() + oz;
            if (!anchored(t, c, cell2, salt2, sizeX, sizeZ, acx, acz)) continue;
            int x = acx * 16 + 1, z = acz * 16 + 1, lo = 999, hi = -999;
            for (int sx = 0; sx < sizeX; sx += 4) for (int sz = 0; sz < sizeZ; sz += 4) {
                int h = t.sample(x + sx, z + sz).y;
                if (h < lo) lo = h;
                if (h > hi) hi = h;
            }
            if (!weather(t, rank, x, z)) continue;
            if (!drowned(t, x, z, sizeX, sizeZ)) continue;
            if (!secondaryFree(t, x, z, sizeX, sizeZ, rank)) continue;
            return new Site(x, lo + 1, z, lo, hi, siteSeed(t, acx, acz, salt2), sizeX, sizeZ);
        }
        return null;
    }

    // == block helpers ===========================================================

    private static int lx(Chunk c, int wx) { int v = wx - c.getX() * 16; return v < 0 || v > 15 ? -1 : v; }
    private static int lz(Chunk c, int wz) { int v = wz - c.getZ() * 16; return v < 0 || v > 15 ? -1 : v; }

    static void put(Chunk c, int wx, int wy, int wz, int id, int data) {
        int x = lx(c, wx), z = lz(c, wz);
        if (x < 0 || z < 0 || wy < 1 || wy > 250) return;
        Dungeons.set(c, x, wy, z, id, data);
    }

    /** Inclusive box fill in world coordinates; the chunk clip happens per block. */
    static void fill(Chunk c, int x0, int y0, int z0, int x1, int y1, int z1, int id, int data) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        int az = Math.min(z0, z1), bz = Math.max(z0, z1);
        int cx0 = c.getX() * 16, cz0 = c.getZ() * 16;
        if (bx < cx0 || ax > cx0 + 15 || bz < cz0 || az > cz0 + 15) return;
        ax = Math.max(ax, cx0); bx = Math.min(bx, cx0 + 15);
        az = Math.max(az, cz0); bz = Math.min(bz, cz0 + 15);
        ay = Math.max(ay, 1); by = Math.min(by, 250);
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = ay; y <= by; y++)
            Dungeons.set(c, x - cx0, y, z - cz0, id, data);
    }

    /** Six faces of a box, interior untouched. */
    static void shell(Chunk c, int x0, int y0, int z0, int x1, int y1, int z1, int id, int data) {
        fill(c, x0, y0, z0, x1, y0, z1, id, data);
        fill(c, x0, y1, z0, x1, y1, z1, id, data);
        fill(c, x0, y0, z0, x0, y1, z1, id, data);
        fill(c, x1, y0, z0, x1, y1, z1, id, data);
        fill(c, x0, y0, z0, x1, y1, z0, id, data);
        fill(c, x0, y0, z1, x1, y1, z1, id, data);
    }

    /**
     * Packs a column from the site floor down so nothing hangs over a dip.
     *
     * Structure audit 2026-09-23 (merge fix-up): the column used to stop six under the lowest sample
     * whatever was below, so where the footprint ran over a hollow, a cave mouth or a cliff lip the
     * plinth's edge ended in a stub hanging in the air. Below that course it now carries on through
     * air, liquid and loose growth to the ground when the ground is within DROP blocks. Over a deeper
     * cave or cavern the last course is left as the cave's roof -- rock over a cave, which is what the
     * stone footings read as -- rather than plugging the cave with a 50-block column. Column by column
     * in this chunk, so every chunk agrees.
     */
    static void footing(Chunk c, Site s, int wx, int wz, int id, int data) {
        if (blockAt(c, wx, 64, wz) == null) return;
        for (int y = s.y - 1; y >= s.lo - 6; y--) put(c, wx, y, wz, id, data);
        int foot = reach(c, wx, s.lo - 7, wz);
        for (int y = s.lo - 7; y > foot; y--) put(c, wx, y, wz, id, data);
    }

    /** How far a footing or pier carries on down (see footing). */
    static final int DROP = 12;

    /**
     * The ground under a column: the first solid cell at or below y through loose cells, if that is
     * within DROP blocks; otherwise y itself (nothing to carry down to). Returns y when y is solid.
     */
    static int reach(Chunk c, int wx, int y, int wz) {
        int g = y;
        while (g >= 1 && g > y - DROP && loose(c, wx, g, wz)) g--;
        return g >= 1 && g < y && blockAt(c, wx, g, wz) != null && !loose(c, wx, g, wz) ? g : y;
    }

    /** Cave air, liquid, or growth that nothing can stand on: what a footing or a pier continues through. */
    static boolean loose(Chunk c, int wx, int y, int wz) {
        Block b = blockAt(c, wx, y, wz);
        if (b == null) return false;
        int id = b.getTypeId();
        return id == 0 || b.isLiquid() || plant(id) || id == 30 || id == 51;
    }

    /**
     * A ladder shaft from a buried roof up to daylight, with a lip at the top.
     *
     * Structure audit 2026-09-23 (shared by every riser() caller, and by the buried dungeon rooms):
     * - the last rung is in the lip course, level with the lip, so the climber steps straight out onto
     *   it (it used to stop one short, under a one-block lip nobody can get onto from a ladder);
     * - the mouth is found in the real column, not the sampled surface: through a mesa overhang that
     *   used to cap the shaft, up through a lake to one block over the water instead of opening on the
     *   bed, and down to the real ground of a basin instead of a free-standing chimney. Each wall of
     *   the shaft rises to one block over its own column's ground, so every chunk agrees on it;
     * - the foot follows the room under the roof: the ladder is carried down through open air to the
     *   floor, through one thin ceiling course if that is all that separates the shaft from the room,
     *   against the shaft's own wall material where the room has no wall to hang it on.
     */
    static void riser(Chunk c, Terrain t, int wx, int wz, int fromY, int wallId, int wallData) {
        if (shaft(c, t, wx, wz, fromY, fromY - 1, -1, wallId, wallData)) descend(c, wx, fromY - 1, wz, wallId, wallData);
    }

    /* The four sides of a ladder cell: offset, and the ladder data that hangs it on that side. */
    private static final int[][] SIDE = {{0, -1, 3}, {0, 1, 2}, {-1, 0, 5}, {1, 0, 4}};

    /**
     * A walled ladder shaft at (wx, wz) from fromY up to daylight (see riser). The ladder hangs on
     * side hang (an index into SIDE), or on the first side inside this chunk when hang is -1. Below
     * roofY + 1 only the back and the two flanks are walled -- the front stays open onto the room --
     * which is how a buried room gets a ladder recess in its own wall. Returns false when the ground
     * over this column is not above fromY, or the column is not in this chunk.
     */
    static boolean shaft(Chunk c, Terrain t, int wx, int wz, int fromY, int roofY, int hang, int wallId, int wallData) {
        int guess = t.sample(wx, wz).y + 1;
        boolean here = blockAt(c, wx, fromY, wz) != null;
        int top = here ? mouth(c, wx, wz, guess) : guess;
        if (top <= fromY) return false;
        if (here && hang < 0) {
            for (int i = 0; i < 4; i++) if (blockAt(c, wx + SIDE[i][0], fromY, wz + SIDE[i][1]) != null) { hang = i; break; }
        }
        for (int i = 0; i < 4; i++) {
            int sx = wx + SIDE[i][0], sz = wz + SIDE[i][1];
            if (blockAt(c, sx, fromY, sz) == null) continue;              // that chunk walls its own side
            int lip = mouth(c, sx, sz, guess);
            if (here) lip = Math.max(lip, top);                           // never lower than the rungs it carries
            boolean front = hang >= 0 && SIDE[i][0] == -SIDE[hang][0] && SIDE[i][1] == -SIDE[hang][1];
            for (int y = front ? Math.max(fromY, roofY + 1) : fromY; y <= lip; y++) put(c, sx, y, sz, wallId, wallData);
        }
        if (!here) return false;
        for (int y = fromY; y <= top; y++) {
            put(c, wx, y, wz, 0, 0);
            put(c, wx, y, wz, 65, SIDE[hang][2]);
        }
        // Headroom at the mouth, and any tree standing over it.
        for (int y = top + 1; y <= Math.min(250, top + 32); y++) {
            Block b = blockAt(c, wx, y, wz);
            if (b != null && b.getTypeId() != 0 && (y <= top + 2 || plant(b.getTypeId()))) put(c, wx, y, wz, 0, 0);
        }
        return true;
    }

    /**
     * One above the real ground of a column in this chunk, found from the sampled height: up through
     * rock that carries on over it (an overhang, a mesa shelf, one open course inside it included),
     * down to the ground when the sampled height is open (a basin, a cave mouth). A lake counts as
     * ground to its surface, and there the lip is flush with the water so a swimmer can get onto it.
     * Trees and ground cover never count, nor a crown standing clear of the ground (the biome trees
     * grow terracotta ones). A column outside the chunk keeps the sampled answer.
     */
    static int mouth(Chunk c, int wx, int wz, int guess) {
        if (blockAt(c, wx, 64, wz) == null) return guess;
        int y = Math.max(1, Math.min(249, guess - 1));
        if (ground(c, wx, y, wz)) {
            for (int k = 0; k < 40 && y < 248; k++) {
                if (ground(c, wx, y + 1, wz)) y++;
                else if (ground(c, wx, y + 2, wz)) y += 2;
                else break;
            }
        } else {
            for (int k = 0; k < 40 && y > 1 && !ground(c, wx, y, wz); k++) y--;
        }
        Block b = blockAt(c, wx, y, wz);
        return b != null && b.isLiquid() ? y : y + 1;
    }

    private static boolean ground(Chunk c, int wx, int y, int wz) {
        Block b = blockAt(c, wx, y, wz);
        return b != null && (b.isLiquid() || (b.getType().isSolid() && !plant(b.getTypeId())));
    }

    /** Carries a ladder down from y through open air to the floor below (see riser). */
    private static void descend(Chunk c, int wx, int y, int wz, int wallId, int wallData) {
        Block b = blockAt(c, wx, y, wz);
        if (b == null) return;
        if (!open(c, wx, y, wz)) {
            // One thin course (a slab layer, bars, a lamp row) between the shaft and a room: cut a hatch.
            if (open(c, wx, y - 1, wz) && open(c, wx, y - 2, wz)) put(c, wx, y, wz, 0, 0);
            else return;
        }
        int foot = y;
        while (foot > y - 40 && open(c, wx, foot - 1, wz)) foot--;
        Block under = blockAt(c, wx, foot - 1, wz);
        if (under == null || foot <= y - 40 || !(under.getType().isSolid() || under.isLiquid())) return;
        // Hang it on whichever side already has the most wall in this chunk, and build the rest of that
        // side -- never a side with glass, a lamp or anything else in the way that a ladder cannot hang on.
        // Standing water (a flooded street) is no obstacle: the side is built up through it as a pier.
        int best = -1, bestCount = -1;
        for (int i = 0; i < 4; i++) {
            if (blockAt(c, wx + SIDE[i][0], y, wz + SIDE[i][1]) == null) continue;
            int n = 0;
            boolean fits = true;
            for (int yy = foot; yy <= y && fits; yy++) {
                Block s = blockAt(c, wx + SIDE[i][0], yy, wz + SIDE[i][1]);
                if (backs(s)) n++;
                else fits = open(c, wx + SIDE[i][0], yy, wz + SIDE[i][1]) || s.isLiquid();
            }
            if (fits && n > bestCount) { best = i; bestCount = n; }
        }
        if (best < 0) return;
        int bx = wx + SIDE[best][0], bz = wz + SIDE[best][1];
        for (int yy = foot; yy <= y; yy++) {
            Block s = blockAt(c, bx, yy, bz);
            if (open(c, bx, yy, bz) || s.isLiquid()) put(c, bx, yy, bz, wallId, wallData);   // never over anything built
            put(c, wx, yy, wz, 65, SIDE[best][2]);
        }
    }

    /** Air, or a cobweb a ladder can replace. */
    private static boolean open(Chunk c, int wx, int y, int wz) {
        Block b = blockAt(c, wx, y, wz);
        return b != null && (b.getTypeId() == 0 || b.getTypeId() == 30);
    }

    /** A block a ladder can hang on: a full opaque cube, not glass, lamps, ice or leaves. */
    static boolean backs(Block b) {
        if (b == null || !b.getType().isOccluding()) return false;
        int id = b.getTypeId();
        return id != 89 && id != 169 && id != 79 && id != 138 && id != 18 && id != 161 && id != 20 && id != 95 && id != 52;
    }

    // == danger and reward =======================================================

    static Block blockAt(Chunk c, int wx, int wy, int wz) {
        int x = lx(c, wx), z = lz(c, wz);
        if (x < 0 || z < 0 || wy < 1 || wy > 250) return null;
        return c.getBlock(x, wy, z);
    }

    static void mob(Chunk c, int wx, int wy, int wz, EntityType type) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return;
        Dungeons.set(c, lx(c, wx), wy, lz(c, wz), 52, 0);
        try {
            org.bukkit.block.CreatureSpawner cs = (org.bukkit.block.CreatureSpawner) b.getState();
            cs.setSpawnedType(type);
            cs.update(true, false);
        } catch (RuntimeException ignored) { }
    }

    /**
     * What the deep end of a place pays out.
     *
     * Nothing in here belongs to any one structure. It is the premium for having gone
     * further in, on top of whatever that structure's own table gives you, and the only
     * way to draw from it is to open a box that is a long way from the door.
     */
    private static final int[][] DEEP = {
        {264, 0, 0, 1, 3}, {403, 0, 0, 1, 1}, {322, 0, 0, 1, 2}, {384, 0, 0, 2, 6},
        {388, 0, 0, 2, 6}, { 41, 0, 0, 1, 2}, { 57, 0, 0, 1, 1}, {133, 0, 0, 1, 1},
    };

    /**
     * How far into a place a box is, from nought at the door to four at the worst of it.
     *
     * Two things count: how deep inside the footprint the box sits, and how far it is
     * from the floor the structure was laid on -- which is the top of a tower and the
     * bottom of a shaft alike. Both are the same measurement really, which is how much
     * of the building you had to get through, and both are things a builder pays for in
     * mobs. Tying the payout to them rather than to a number typed at each chest means a
     * chest cannot be generous by accident, and the deep end of every structure in the
     * plugin pays the same premium for the same trouble.
     */
    static int tier(Site s, int wx, int wy, int wz) {
        int inset = Math.min(Math.min(wx - s.x, s.x + s.sizeX - 1 - wx),
                             Math.min(wz - s.z, s.z + s.sizeZ - 1 - wz));
        int climb = Math.abs(wy - s.y);
        return Math.max(0, Math.min(4, Math.max(0, inset) / 5 + climb / 6));
    }

    /** A chest whose contents are decided by how far in it is. */
    static void graded(Chunk c, Site s, int wx, int wy, int wz, Random r, int[][] pool, boolean gun) {
        int tier = tier(s, wx, wy, wz);
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return;
        Dungeons.set(c, lx(c, wx), wy, lz(c, wz), 54, 2);
        try {
            Chest chest = (Chest) b.getState();
            Inventory inv = chest.getBlockInventory();
            int total = 0;
            for (int[] row : pool) total += row[2];
            int rolls = 5 + tier * 2 + r.nextInt(3);
            for (int i = 0; i < rolls; i++) {
                int pick = r.nextInt(total);
                for (int[] row : pool) {
                    pick -= row[2];
                    if (pick >= 0) continue;
                    int count = row[3] + (row[4] > row[3] ? r.nextInt(row[4] - row[3] + 1) : 0);
                    inv.setItem(r.nextInt(inv.getSize()), new ItemStack(row[0], count, (short) row[1]));
                    break;
                }
            }
            for (int i = 1; i < tier; i++) {
                int[] row = DEEP[r.nextInt(DEEP.length)];
                inv.addItem(new ItemStack(row[0], row[3] + (row[4] > row[3] ? r.nextInt(row[4] - row[3] + 1) : 0),
                        (short) row[1]));
            }
            if (gun && r.nextInt(Math.max(2, 6 - tier)) == 0) {
                ItemStack weapon = ExpeditionLoot.rareWeapon(r);
                if (weapon != null) {
                    inv.addItem(weapon);
                    inv.addItem(new ItemStack(org.bukkit.Material.IRON_NUGGET, 8 + r.nextInt(9 + tier * 8)));
                }
            }
            // Survivor Gear (3.26.0): at most one trinket, depth tier 0-4 as gear tier 1-5, from a copy of r keyed
            // to this chest (GearLoot.at), so r and everything the builder places after this chest are unchanged.
            GearLoot.add(inv, GearLoot.at(r, wx, wy, wz), tier + 1);
            // No chest.update() here (removed 3.21.0): getBlockInventory() is the LIVE tile
            // inventory, and update() would copy the empty pre-fill snapshot back over it.
        } catch (RuntimeException ignored) { }
    }

    /**
     * The surprise.
     *
     * Every one of these places has one box that is not where the others are: behind a
     * wall, under a floor, at the top of something you have to climb. It pays the site's
     * own table several times over, adds a short list of things that exist nowhere else
     * in the world, and always carries a weapon. Finding it is the point of going in.
     */
    static void trove(Chunk c, int wx, int wy, int wz, Random r, int[][] pool, int[][] special, int rolls) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return;
        Dungeons.set(c, lx(c, wx), wy, lz(c, wz), 54, 2);
        try {
            Chest chest = (Chest) b.getState();
            Inventory inv = chest.getBlockInventory();
            int total = 0;
            for (int[] row : pool) total += row[2];
            for (int i = 0; i < rolls; i++) {
                int pick = r.nextInt(total);
                for (int[] row : pool) {
                    pick -= row[2];
                    if (pick >= 0) continue;
                    int count = row[3] + (row[4] > row[3] ? r.nextInt(row[4] - row[3] + 1) : 0);
                    inv.setItem(r.nextInt(inv.getSize()), new ItemStack(row[0], count, (short) row[1]));
                    break;
                }
            }
            for (int[] row : special) {
                int count = row[3] + (row[4] > row[3] ? r.nextInt(row[4] - row[3] + 1) : 0);
                inv.addItem(new ItemStack(row[0], count, (short) row[1]));
            }
            ItemStack weapon = ExpeditionLoot.rareWeapon(r);
            if (weapon != null) {
                inv.addItem(weapon);
                inv.addItem(new ItemStack(org.bukkit.Material.IRON_NUGGET, 24 + r.nextInt(33)));
            }
            // Survivor Gear (3.26.0): the trove is the site's hardest box, gear tier 5 (see graded()).
            GearLoot.add(inv, GearLoot.at(r, wx, wy, wz), 5);
            // No chest.update() here (removed 3.21.0): getBlockInventory() is the LIVE tile
            // inventory, and update() would copy the empty pre-fill snapshot back over it.
        } catch (RuntimeException ignored) { }
    }

    /** Every pool row is {id, data, weight, least, most}. */
    static void loot(Chunk c, int wx, int wy, int wz, Random r, int[][] pool, int rolls, boolean gun) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return;
        Dungeons.set(c, lx(c, wx), wy, lz(c, wz), 54, 2);
        try {
            Chest chest = (Chest) b.getState();
            Inventory inv = chest.getBlockInventory();
            int total = 0;
            for (int[] row : pool) total += row[2];
            for (int i = 0; i < rolls; i++) {
                int pick = r.nextInt(total);
                for (int[] row : pool) {
                    pick -= row[2];
                    if (pick >= 0) continue;
                    int count = row[3] + (row[4] > row[3] ? r.nextInt(row[4] - row[3] + 1) : 0);
                    inv.setItem(r.nextInt(inv.getSize()), new ItemStack(row[0], count, (short) row[1]));
                    break;
                }
            }
            if (gun && r.nextInt(5) == 0) {
                ItemStack weapon = ExpeditionLoot.rareWeapon(r);
                if (weapon != null) {
                    inv.setItem(r.nextInt(inv.getSize()), weapon);
                    inv.addItem(new ItemStack(org.bukkit.Material.IRON_NUGGET, 8 + r.nextInt(17)));
                }
            }
            // Survivor Gear (3.26.0): a flat table without a depth grade, gear tier 1 (see graded()).
            GearLoot.add(inv, GearLoot.at(r, wx, wy, wz), 1);
            // No chest.update() here (removed 3.21.0): getBlockInventory() is the LIVE tile
            // inventory, and update() would copy the empty pre-fill snapshot back over it.
        } catch (RuntimeException ignored) { }
    }

    // == 1. AM ===================================================================
    // Hate. A computer the size of a cathedral, built in a hole it dug for itself: an
    // obsidian floor, a nave lined with machine banks that still have power, cable trunks
    // coming down out of the dark, five cages along one wall, and a face at the far end
    // made of the only thing it had to make a face out of. There is no mouth on it.

    private static final int[][] AM_LOOT = {
        {331, 0, 15, 8, 24}, {152, 0,  9, 1,  3}, {348, 0, 12, 4, 12}, {356, 0, 10, 2, 6},
        {404, 0,  8,  1, 3}, {403, 0,  5, 1,  1}, {367, 0, 13, 4, 12}, {264, 0,  7, 1, 2},
        { 49, 0,  9,  2, 6}, { 76, 0, 11, 3,  9}, {145, 0,  4, 1,  1}, {386, 0,  6, 1, 2},
    };

    private static void amCore(Chunk c, Terrain t) {
        Site s = deep(t, c, AM_CELL, 0x414D434F5245L, 33, 29, 26, RANK_AM);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 32, z1 = z0 + 28, y1 = y0 + 25;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);                       // hollow the rock out
        shell(c, x0, y0, z0, x1, y1, z1, 251, 15);                   // black concrete casing
        fill(c, x0, y0, z0, x1, y0, z1, 49, 0);                      // obsidian floor
        for (int dz = 0; dz <= 28; dz += 4) {                        // ribs up the casing
            fill(c, x0, y0, z0 + dz, x0, y1, z0 + dz, 251, 7);
            fill(c, x1, y0, z0 + dz, x1, y1, z0 + dz, 251, 7);
        }
        // The nave: a lava trench under a grate, an inlaid strip of live redstone either side.
        // (Red concrete 251:14 stands in for the redstone blocks, which powered nothing.)
        // The trench is lined in obsidian: its lava lay against bare rock, and where a cave ran beside it
        // the chunk-seam sweep drained it to air (structure audit 2026-09-22).
        fill(c, x0 + 1, y0 - 2, z0 + 13, x1 - 3, y0 - 1, z0 + 15, 49, 0);
        fill(c, x0 + 2, y0 - 1, z0 + 14, x1 - 4, y0 - 1, z0 + 14, 10, 0);
        fill(c, x0 + 2, y0, z0 + 14, x1 - 4, y0, z0 + 14, 101, 0);
        fill(c, x0 + 2, y0, z0 + 12, x1 - 4, y0, z0 + 12, 251, 14);
        fill(c, x0 + 2, y0, z0 + 16, x1 - 4, y0, z0 + 16, 251, 14);
        // Machine banks in the side halls, eight blocks of casing with a lit core.
        // Structure audit 2026-09-23: the north row of banks stood where the cages are built (the cages
        // cut through six of them) and in the corner the entry ladder comes down; that row is left out.
        for (int dx = 2; dx <= 28; dx += 4) for (int dz = 2; dz <= 26; dz += 3) {
            if (dz >= 11 && dz <= 17) continue;                      // leave the nave clear
            if (dz == 2) continue;
            int bx = x0 + dx, bz = z0 + dz;
            fill(c, bx, y0 + 1, bz, bx + 1, y0 + 8, bz, 251, 15);
            put(c, bx, y0 + 3, bz, 251, 14);                          // red concrete, was redstone block
            put(c, bx + 1, y0 + 6, bz, 251, 14);
            put(c, bx, y0 + 8, bz, 89, 0);
            if (((dx + dz) & 3) == 0) put(c, bx + 1, y0 + 2, bz, 76, 5);    // standing on the casing
        }
        // Cable trunks out of the ceiling, and arches over the nave.
        for (int dx = 4; dx <= 22; dx += 6) {                          // (the last pair hung in front of the face's eyes)
            fill(c, x0 + dx, y0 + 10, z0 + 11, x0 + dx, y1 - 1, z0 + 11, 49, 0);
            fill(c, x0 + dx, y0 + 10, z0 + 17, x0 + dx, y1 - 1, z0 + 17, 49, 0);
            fill(c, x0 + dx, y1 - 1, z0 + 12, x0 + dx, y1 - 1, z0 + 16, 49, 0);
            fill(c, x0 + dx + 2, y1 - 8, z0 + 14, x0 + dx + 2, y1 - 1, z0 + 14, 101, 0);
        }
        // Five cages along the near wall. One of them still has its occupant.
        for (int i = 0; i < 5; i++) {
            int bx = x0 + 4 + i * 6, bz = z0 + 2;
            shell(c, bx, y0 + 1, bz, bx + 2, y0 + 4, bz + 2, 101, 0);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 1, y0 + 3, bz + 1, 0, 0);
            put(c, bx + 1, y0 + 1, bz + 1, 88, 0);
            if (i == 1 || i == 3) mob(c, bx + 1, y0 + 2, bz + 1, i == 1 ? EntityType.ZOMBIE : EntityType.SKELETON);
            else put(c, bx + 1, y0 + 2, bz + 1, 144, 1);                // what is left of the others
        }
        // The face: a field of redstone with two obsidian eyes and nothing under them.
        // Structure audit 2026-09-23: the face hung two blocks off the wall from one bar chain, and had
        // an obsidian mouth. It is mounted on a black backing plate against the casing, and has no mouth.
        fill(c, x1 - 1, y0 + 6, z0 + 8, x1 - 1, y0 + 20, z0 + 20, 251, 15);
        fill(c, x1 - 2, y0 + 6, z0 + 8, x1 - 2, y0 + 20, z0 + 20, 251, 14);   // red concrete, was redstone block
        fill(c, x1 - 2, y0 + 15, z0 + 11, x1 - 2, y0 + 17, z0 + 12, 49, 0);
        fill(c, x1 - 2, y0 + 15, z0 + 16, x1 - 2, y0 + 17, z0 + 17, 49, 0);
        fill(c, x1 - 3, y0 + 1, z0 + 12, x1 - 3, y0 + 4, z0 + 16, 87, 0);
        // Whatever is left of the people it kept. Cobweb in the corners, flesh under the face.
        for (int i = 0; i < 26; i++) {                                 // only where a web can hang (audit 2026-09-23)
            int wx = x0 + 1 + r.nextInt(31), wz = z0 + 1 + r.nextInt(27);
            int wy = y0 + 1 + r.nextInt(3);
            if (webSpot(c, wx, wy, wz)) put(c, wx, wy, wz, 30, 0);
        }
        mob(c, x0 + 16, y0 + 1, z0 + 6, EntityType.CAVE_SPIDER);
        mob(c, x0 + 16, y0 + 1, z0 + 22, EntityType.CAVE_SPIDER);
        mob(c, x1 - 5, y0 + 1, z0 + 14, EntityType.ZOMBIE);
        mob(c, x0 + 8, y0 + 1, z0 + 14, EntityType.SKELETON);
        graded(c, s, x0 + 5, y0 + 1, z0 + 8, r, AM_LOOT, true);          // beside its bank, not in it (lid was blocked)
        graded(c, s, x0 + 20, y0 + 1, z0 + 20, r, AM_LOOT, false);
        graded(c, s, x1 - 4, y0 + 1, z0 + 10, r, AM_LOOT, false);
        graded(c, s, x0 + 14, y0 + 1, z0 + 24, r, AM_LOOT, true);
        riser(c, t, x0 + 2, z0 + 2, y1, 251, 15);
    }

    // == 2. The Outpost ==========================================================
    // A forward base somebody left in a hurry: sandbag berm, two watchtowers, a barracks
    // with the beds still made, a motor pool with one truck up on blocks, and an armoury
    // that was emptied from the inside.

    private static final int[][] OUTPOST_LOOT = {
        {262, 0, 15,  8, 24}, {289, 0, 13, 4, 14}, {298, 0, 12, 3, 10}, {450, 0, 14, 8, 24},
        {306, 0,  7,  1,  1}, {307, 0,  7, 1,  1}, {308, 0,  7, 1,  1}, {309, 0,  7, 1, 1},
        {267, 0,  8,  1,  1}, {297, 0, 11, 2,  6}, {299, 0, 5, 1, 1}, {261, 0,  6, 1, 1},
    };

    private static void outpost(Chunk c, Terrain t) {
        Site s = ground(t, c, OUTPOST_CELL, 0x4F555450L, 29, 25, 6, RANK_OUTPOST);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 28, z1 = z0 + 24;
        for (int dx = 0; dx <= 28; dx++) for (int dz = 0; dz <= 24; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 159, 8);                      // compacted hardstand
        // Sandbag berm with two gaps for the road.
        for (int dx = 0; dx <= 28; dx++) for (int dz = 0; dz <= 24; dz++) {
            boolean edge = dx == 0 || dx == 28 || dz == 0 || dz == 24;
            if (!edge) continue;
            if (dz == 24 && dx >= 12 && dx <= 16) continue;
            fill(c, x0 + dx, y0 + 1, z0 + dz, x0 + dx, y0 + 2, z0 + dz, 159, 12);
            if (((dx + dz) & 1) == 0) put(c, x0 + dx, y0 + 3, z0 + dz, 159, 13);
        }
        fill(c, x0 + 12, y0, z0 + 18, x0 + 16, y0, z1, 251, 8);       // the road in
        // Structure audit 2026-09-23: where the hill outside stands over the pad, the road gap opened
        // onto a wall of rock. The road carries on out as a cutting that climbs one block a row to meet
        // the ground (the pad itself is cleared by excavate()).
        for (int k = 1; k <= 6; k++) for (int dx = 11; dx <= 17; dx++) {
            int wx = x0 + dx, wz = z1 + k, bed = y0 + k - 1;
            Block cut = blockAt(c, wx, bed + 1, wz);
            if (cut == null || !cut.getType().isSolid() || plant(cut.getTypeId())) continue;   // already this low
            for (int y = bed + 1; y <= bed + 12; y++) {                   // up to open air: no overhang left
                Block b = blockAt(c, wx, y, wz);
                if (b == null || b.getTypeId() == 0) break;
                put(c, wx, y, wz, 0, 0);
            }
            if (dx >= 12 && dx <= 16) put(c, wx, bed, wz, 251, 8);
        }
        // Two watchtowers on the far corners.
        int[][] towers = {{x0 + 2, z0 + 2}, {x1 - 4, z0 + 2}};
        for (int[] tw : towers) {
            int bx = tw[0], bz = tw[1];
            for (int dy = 1; dy <= 8; dy++) {
                put(c, bx, y0 + dy, bz, 17, 1); put(c, bx + 2, y0 + dy, bz, 17, 1);
                put(c, bx, y0 + dy, bz + 2, 17, 1); put(c, bx + 2, y0 + dy, bz + 2, 17, 1);
            }
            fill(c, bx - 1, y0 + 9, bz - 1, bx + 3, y0 + 9, bz + 3, 5, 1);
            shell(c, bx - 1, y0 + 10, bz - 1, bx + 3, y0 + 11, bz + 3, 85, 0);
            fill(c, bx, y0 + 10, bz, bx + 2, y0 + 11, bz + 2, 0, 0);
            fill(c, bx - 1, y0 + 12, bz - 1, bx + 3, y0 + 12, bz + 3, 44, 1);
            // Structure audit 2026-09-23: the only rung was the one in the hatch, eight blocks over the
            // pad, with the sentry's spawner standing on it in a sealed cage. A plank brace between the
            // two south legs carries a ladder from the pad up through the hatch, and the spawner moves
            // off the hatch into the north-west corner of the nest.
            for (int dy = 1; dy <= 8; dy++) put(c, bx + 1, y0 + dy, bz + 2, 5, 1);
            for (int dy = 1; dy <= 9; dy++) put(c, bx + 1, y0 + dy, bz + 1, 65, 2);   // on the brace/planks to its south
            mob(c, bx, y0 + 10, bz, EntityType.SKELETON);
        }
        // Barracks: bunks down both walls, a stove, and the lights still on.
        shell(c, x0 + 4, y0 + 1, z0 + 8, x0 + 13, y0 + 5, z0 + 15, 159, 13);
        fill(c, x0 + 5, y0 + 1, z0 + 9, x0 + 12, y0 + 4, z0 + 14, 0, 0);
        fill(c, x0 + 4, y0 + 6, z0 + 8, x0 + 13, y0 + 6, z0 + 15, 44, 5);
        fill(c, x0 + 4, y0 + 1, z0 + 11, x0 + 4, y0 + 2, z0 + 12, 0, 0);
        // Structure audit 2026-09-23: the bunks were single grey blocks and the west one at the door
        // blocked it, with the chest in the doorway behind it and the stove facing the wall. The door
        // bunk is gone; the rest are two-high (a spruce post and top-slab upper bunk with a mattress),
        // the chest is a footlocker at the foot of a bunk, the stove faces the room, and there is a
        // mess table with a stool.
        for (int dz = 9; dz <= 14; dz += 2) {
            if (dz != 11) {
                put(c, x0 + 5, y0 + 1, z0 + dz, 44, 0); put(c, x0 + 6, y0 + 1, z0 + dz, 35, 8);
                put(c, x0 + 6, y0 + 2, z0 + dz, 188, 0);
                fill(c, x0 + 5, y0 + 3, z0 + dz, x0 + 6, y0 + 3, z0 + dz, 126, 9);
                fill(c, x0 + 5, y0 + 4, z0 + dz, x0 + 6, y0 + 4, z0 + dz, 171, 8);
            }
            put(c, x0 + 11, y0 + 1, z0 + dz, 44, 0); put(c, x0 + 12, y0 + 1, z0 + dz, 35, 8);
            put(c, x0 + 12, y0 + 2, z0 + dz, 188, 0);
            fill(c, x0 + 11, y0 + 3, z0 + dz, x0 + 12, y0 + 3, z0 + dz, 126, 9);
            fill(c, x0 + 11, y0 + 4, z0 + dz, x0 + 12, y0 + 4, z0 + dz, 171, 8);
        }
        put(c, x0 + 8, y0 + 1, z0 + 11, 188, 0); put(c, x0 + 8, y0 + 2, z0 + 11, 72, 0);   // mess table
        put(c, x0 + 9, y0 + 1, z0 + 11, 134, 0);                                        // and its stool
        put(c, x0 + 8, y0 + 4, z0 + 11, 89, 0);
        put(c, x0 + 9, y0 + 1, z0 + 9, 61, 3);
        mob(c, x0 + 9, y0 + 1, z0 + 13, EntityType.ZOMBIE);
        graded(c, s, x0 + 7, y0 + 1, z0 + 13, r, OUTPOST_LOOT, false);
        // Armoury: iron door frame, racks, and what the last patrol did not carry out.
        shell(c, x0 + 17, y0 + 1, z0 + 8, x0 + 25, y0 + 5, z0 + 14, 98, 0);
        fill(c, x0 + 18, y0 + 1, z0 + 9, x0 + 24, y0 + 4, z0 + 13, 0, 0);
        fill(c, x0 + 17, y0 + 1, z0 + 11, x0 + 17, y0 + 2, z0 + 11, 0, 0);
        put(c, x0 + 17, y0 + 3, z0 + 11, 101, 0);
        for (int dz = 9; dz <= 13; dz += 2) {
            put(c, x0 + 24, y0 + 1, z0 + dz, 43, 8);                  // smooth stone double slab, was iron block
            put(c, x0 + 24, y0 + 2, z0 + dz, 101, 0);
        }
        // Structure audit 2026-09-23: a second rack row along the north wall and the cleaning bench.
        for (int dx = 19; dx <= 21; dx += 2) {
            put(c, x0 + dx, y0 + 1, z0 + 9, 43, 8);
            put(c, x0 + dx, y0 + 2, z0 + 9, 101, 0);
        }
        put(c, x0 + 23, y0 + 1, z0 + 9, 58, 0);
        put(c, x0 + 21, y0 + 4, z0 + 11, 89, 0);
        graded(c, s, x0 + 19, y0 + 1, z0 + 10, r, OUTPOST_LOOT, true);
        graded(c, s, x0 + 23, y0 + 1, z0 + 12, r, OUTPOST_LOOT, false);
        mob(c, x0 + 21, y0 + 1, z0 + 12, EntityType.ZOMBIE);
        // Motor pool: one truck on blocks, fuel drums, and a lot of empty bays.
        fill(c, x0 + 4, y0, z0 + 18, x0 + 24, y0, z0 + 22, 251, 8);
        // Structure audit 2026-09-23: the truck (43:8 since the valuables pass) sat flat on the pad and
        // read as a grey box. Olive-drab body lifted onto stone-brick chocks, cab glass and bumper with it.
        fill(c, x0 + 6, y0 + 2, z0 + 19, x0 + 11, y0 + 3, z0 + 20, 159, 13);
        fill(c, x0 + 6, y0 + 4, z0 + 19, x0 + 8, y0 + 4, z0 + 20, 20, 0);
        put(c, x0 + 5, y0 + 2, z0 + 19, 44, 3); put(c, x0 + 5, y0 + 2, z0 + 20, 44, 3);
        for (int dx = 6; dx <= 11; dx += 5) for (int dz = 19; dz <= 20; dz++) put(c, x0 + dx, y0 + 1, z0 + dz, 98, 0);
        for (int i = 0; i < 4; i++) put(c, x0 + 16 + i * 2, y0 + 1, z0 + 21, 118, 0);
        for (int i = 0; i < 3; i++) put(c, x0 + 16 + i * 2, y0 + 1, z0 + 19, 159, 14);
        graded(c, s, x0 + 20, y0 + 1, z0 + 20, r, OUTPOST_LOOT, false);
        // Moved out of the middle of the road lane (structure audit 2026-09-23), to the end of the drums.
        mob(c, x0 + 22, y0 + 1, z0 + 19, EntityType.ZOMBIE);
    }

    // == 3. The Garrison =========================================================
    // The outpost's opposite number: a base built to hold a province. Apron and runway,
    // two hangars with their doors still open, a command block with three floors of
    // nothing, a silo with the bird still in it, and a strongroom cut into the ground
    // that whoever got here first did not manage to open.

    private static final int[][] GARRISON_LOOT = {
        {264, 0,  8, 1,  3}, {310, 0, 4, 1,  1}, {311, 0,  4, 1,  1}, {312, 0,  4, 1,  1},
        {313, 0,  4, 1,  1}, {276, 0, 5, 1,  1}, { 46, 0,  9, 2,  6}, { 42, 0,  9, 2,  5},
        {262, 0, 14, 12, 32}, {289, 0, 12, 6, 18}, {403, 0,  4, 1,  1}, {417, 0,  5, 1, 1},
    };

    private static void garrison(Chunk c, Terrain t) {
        Site s = ground(t, c, GARRISON_CELL, 0x47415252L, 57, 45, 9, RANK_GARRISON);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 56, z1 = z0 + 44;
        for (int dx = 0; dx <= 56; dx++) for (int dz = 0; dz <= 44; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 251, 8);                      // concrete over everything
        // Perimeter: curb, wire, and a tower on each corner.
        for (int dx = 0; dx <= 56; dx++) for (int dz = 0; dz <= 44; dz++) {
            if (dx != 0 && dx != 56 && dz != 0 && dz != 44) continue;
            if (dz == 44 && dx >= 26 && dx <= 30) continue;
            put(c, x0 + dx, y0 + 1, z0 + dz, 251, 7);
            fill(c, x0 + dx, y0 + 2, z0 + dz, x0 + dx, y0 + 4, z0 + dz, 101, 0);
        }
        int[][] corners = {{x0 + 2, z0 + 2}, {x1 - 4, z0 + 2}, {x0 + 2, z1 - 4}, {x1 - 4, z1 - 4}};
        // Structure audit 2026-09-23: each tower was a sealed shaft whose ladder ended under a concrete
        // ceiling, below a one-high glass cab with the sentry's spawner set in its floor over the ladder
        // and a lamp floating at head height. Now: a doorway at the foot on the yard side (never the
        // ladder wall), the ladder carried up through the ceiling and the cab floor, a cab two blocks
        // high under a concrete roof with the lamp set in it, and the spawner in the cab's inner corner.
        for (int[] tw : corners) {
            boolean north = tw[1] == z0 + 2, west = tw[0] == x0 + 2;
            shell(c, tw[0], y0 + 1, tw[1], tw[0] + 2, y0 + 9, tw[1] + 2, 251, 7);
            shell(c, tw[0] - 1, y0 + 10, tw[1] - 1, tw[0] + 3, y0 + 13, tw[1] + 3, 20, 0);
            fill(c, tw[0], y0 + 10, tw[1], tw[0] + 2, y0 + 12, tw[1] + 2, 0, 0);
            fill(c, tw[0] - 1, y0 + 10, tw[1] - 1, tw[0] + 3, y0 + 10, tw[1] + 3, 251, 7);
            fill(c, tw[0] - 1, y0 + 13, tw[1] - 1, tw[0] + 3, y0 + 13, tw[1] + 3, 251, 7);
            put(c, tw[0] + 1, y0 + 13, tw[1] + 1, 89, 0);
            fill(c, tw[0] + 1, y0 + 1, tw[1] + 1, tw[0] + 1, y0 + 10, tw[1] + 1, 65, 3);
            if (north) fill(c, tw[0] + 1, y0 + 1, tw[1] + 2, tw[0] + 1, y0 + 2, tw[1] + 2, 0, 0);
            else if (west) fill(c, tw[0] + 2, y0 + 1, tw[1] + 1, tw[0] + 2, y0 + 2, tw[1] + 1, 0, 0);
            else fill(c, tw[0], y0 + 1, tw[1] + 1, tw[0], y0 + 2, tw[1] + 1, 0, 0);
            mob(c, west ? tw[0] + 2 : tw[0], y0 + 11, north ? tw[1] + 2 : tw[1], EntityType.SKELETON);
        }
        // Runway markings along the south apron.
        for (int dx = 4; dx <= 52; dx += 6) fill(c, x0 + dx, y0, z0 + 6, x0 + dx + 2, y0, z0 + 6, 251, 0);
        fill(c, x0 + 2, y0, z0 + 2, x1 - 2, y0, z0 + 2, 251, 4);
        fill(c, x0 + 2, y0, z0 + 10, x1 - 2, y0, z0 + 10, 251, 4);
        // Two hangars, barrel roofs, doors left open.
        for (int h = 0; h < 2; h++) {
            int hx = x0 + 4 + h * 26, hz = z0 + 14;
            shell(c, hx, y0 + 1, hz, hx + 18, y0 + 8, hz + 12, 155, 0);
            fill(c, hx + 1, y0 + 1, hz + 1, hx + 17, y0 + 8, hz + 11, 0, 0);
            fill(c, hx + 6, y0 + 1, hz, hx + 12, y0 + 6, hz, 0, 0);    // the open door
            for (int dx = 0; dx <= 18; dx += 3)
                fill(c, hx + dx, y0 + 9, hz, hx + dx, y0 + 9, hz + 12, 43, 8);   // rib: 43:8, was iron block
            fill(c, hx + 1, y0 + 9, hz + 1, hx + 17, y0 + 9, hz + 11, 44, 7);
            for (int dz = 2; dz <= 10; dz += 4) put(c, hx + 9, y0 + 8, hz + dz, 169, 0);
            fill(c, hx + 3, y0 + 1, hz + 4, hx + 15, y0 + 1, hz + 8, 251, 7);
            // Structure audit 2026-09-23: the halls were bare pads, dark at night. Lights set in the back
            // wall, the door leaves slid back against the front wall, and each hall given its job: an
            // aircraft on the pad in the first (its two hoppers now the main gear under the wings), a
            // maintenance bay in the second (bench, anvil, parts rack, fuel drums; its hoppers stay).
            for (int dx = 4; dx <= 14; dx += 5) put(c, hx + dx, y0 + 4, hz + 12, 169, 0);
            fill(c, hx + 1, y0 + 1, hz + 1, hx + 5, y0 + 6, hz + 1, 43, 8);
            fill(c, hx + 13, y0 + 1, hz + 1, hx + 17, y0 + 6, hz + 1, 43, 8);
            if (h == 0) {
                fill(c, hx + 5, y0 + 3, hz + 6, hx + 13, y0 + 3, hz + 6, 251, 0);          // fuselage
                put(c, hx + 14, y0 + 3, hz + 6, 156, 1);                                   // nose
                fill(c, hx + 11, y0 + 4, hz + 6, hx + 12, y0 + 4, hz + 6, 20, 0);           // canopy
                fill(c, hx + 8, y0 + 3, hz + 3, hx + 9, y0 + 3, hz + 5, 44, 7);             // wings
                fill(c, hx + 8, y0 + 3, hz + 7, hx + 9, y0 + 3, hz + 9, 44, 7);
                fill(c, hx + 5, y0 + 3, hz + 4, hx + 5, y0 + 3, hz + 5, 44, 7);             // tailplane
                fill(c, hx + 5, y0 + 3, hz + 7, hx + 5, y0 + 3, hz + 8, 44, 7);
                fill(c, hx + 5, y0 + 4, hz + 6, hx + 5, y0 + 5, hz + 6, 251, 0);           // fin
                put(c, hx + 9, y0 + 2, hz + 5, 154, 0);
                put(c, hx + 9, y0 + 2, hz + 7, 154, 0);
                put(c, hx + 12, y0 + 2, hz + 6, 101, 0);                                   // nose gear
            } else {
                put(c, hx + 4, y0 + 2, hz + 6, 154, 0);
                put(c, hx + 14, y0 + 2, hz + 6, 154, 0);
                put(c, hx + 3, y0 + 1, hz + 11, 58, 0);
                put(c, hx + 4, y0 + 1, hz + 11, 145, 0);
                for (int dx = 6; dx <= 7; dx++) { put(c, hx + dx, y0 + 1, hz + 11, 43, 8); put(c, hx + dx, y0 + 2, hz + 11, 101, 0); }
                for (int dx = 10; dx <= 12; dx++) put(c, hx + dx, y0 + 1, hz + 11, 118, 0);
            }
            graded(c, s, hx + 3, y0 + 1, hz + 2, r, GARRISON_LOOT, h == 0);
            graded(c, s, hx + 15, y0 + 1, hz + 10, r, GARRISON_LOOT, false);
            mob(c, hx + 9, y0 + 1, hz + 3, EntityType.ZOMBIE);
            mob(c, hx + 9, y0 + 1, hz + 9, EntityType.ZOMBIE);
        }
        // Command block: three floors, stairwell, glass on the top.
        int mx = x0 + 8, mz = z0 + 30;
        for (int f = 0; f < 3; f++) {
            int fy = y0 + 1 + f * 5;
            shell(c, mx, fy, mz, mx + 18, fy + 4, mz + 12, f == 2 ? 20 : 251, f == 2 ? 0 : 7);
            fill(c, mx + 1, fy, mz + 1, mx + 17, fy + 3, mz + 11, 0, 0);
            fill(c, mx + 1, fy, mz + 1, mx + 17, fy, mz + 11, 251, 8);
            put(c, mx + 9, f == 2 ? fy + 4 : fy + 3, mz + 6, 89, 0);        // top floor: in the ceiling, over the map table
            if (f < 2) fill(c, mx + 6, fy + 4, mz + 4, mx + 12, fy + 4, mz + 8, 44, 7);
            put(c, mx + 3, fy + 4, mz + 6, 169, 0); put(c, mx + 15, fy + 4, mz + 6, 169, 0);   // ceiling lights
        }
        fill(c, mx, y0 + 16, mz, mx + 18, y0 + 16, mz + 12, 44, 7);
        // Structure audit 2026-09-23: the block had no door on any floor, and its 'stairwell' was a 2x2
        // hole through the floors with one unsupported rung per storey (floor to floor is five). Now a
        // doorway faces the hangars with a slab step up to it, and one ladder runs up the west wall
        // through a hatch in each floor. The three floors were empty: the ground floor is the
        // guardroom (desk inside the door, a weapon cage on the east wall), the first the barracks
        // (beds, filing, a bench), the glass top the operations room (map table, radio consoles). The
        // chest and the two spawners stood sunk in the floor courses; they now stand on the floors.
        fill(c, mx + 8, y0 + 2, mz, mx + 10, y0 + 4, mz, 0, 0);
        fill(c, mx + 8, y0 + 1, mz - 1, mx + 10, y0 + 1, mz - 1, 44, 7);
        put(c, mx, y0 + 11, mz + 2, 251, 7);                              // the ladder's back in the glass course
        fill(c, mx + 1, y0 + 2, mz + 2, mx + 1, y0 + 11, mz + 2, 65, 5);
        put(c, mx + 10, y0 + 2, mz + 3, 155, 0); put(c, mx + 11, y0 + 2, mz + 3, 44, 15);
        put(c, mx + 12, y0 + 2, mz + 3, 155, 0); put(c, mx + 11, y0 + 2, mz + 4, 156, 2);
        put(c, mx + 12, y0 + 3, mz + 3, 69, 5);                            // the gate switch on the desk
        fill(c, mx + 15, y0 + 2, mz + 5, mx + 15, y0 + 4, mz + 10, 101, 0);
        fill(c, mx + 15, y0 + 2, mz + 7, mx + 15, y0 + 3, mz + 7, 0, 0);    // the cage's way in
        for (int dz = 6; dz <= 10; dz += 2) { put(c, mx + 17, y0 + 2, mz + dz, 43, 8); put(c, mx + 17, y0 + 3, mz + dz, 101, 0); }
        for (int dx = 4; dx <= 10; dx += 2) { put(c, mx + dx, y0 + 7, mz + 10, 26, 0); put(c, mx + dx, y0 + 7, mz + 11, 26, 8); }
        fill(c, mx + 6, y0 + 7, mz + 1, mx + 11, y0 + 8, mz + 1, 47, 0);
        put(c, mx + 16, y0 + 7, mz + 1, 58, 0);
        fill(c, mx + 7, y0 + 12, mz + 5, mx + 11, y0 + 12, mz + 7, 155, 0);
        fill(c, mx + 7, y0 + 13, mz + 5, mx + 11, y0 + 13, mz + 7, 171, 13);
        put(c, mx + 8, y0 + 13, mz + 6, 171, 7); put(c, mx + 10, y0 + 13, mz + 5, 171, 7);
        for (int dx = 6; dx <= 12; dx++) {
            boolean set = (dx & 1) == 0;
            put(c, mx + dx, y0 + 12, mz + 11, set ? 155 : 25, 0);
            put(c, mx + dx, y0 + 13, mz + 11, set ? 151 : 69, set ? 0 : 5);
            if (set) put(c, mx + dx, y0 + 12, mz + 10, 156, 3);
        }
        graded(c, s, mx + 4, y0 + 12, mz + 10, r, GARRISON_LOOT, true);    // same depth tier as before
        mob(c, mx + 14, y0 + 7, mz + 6, EntityType.ZOMBIE);
        mob(c, mx + 6, y0 + 2, mz + 9, EntityType.SKELETON);
        // The silo: a shaft with the bird still standing in it.
        // Structure audit 2026-09-23: it was dug through the east end of the second hangar (deleting a
        // chest and a hopper and holing its wall) under a solid lid, with a spawner in its ladder and
        // another hanging in the air. It now stands in the seven-wide strip between the hangars, open
        // at the mouth behind a railing, the bird in white under a quartz nose with its tip above the
        // apron, the ladder unbroken, and the box and both spawners on the shaft floor round the bird.
        // The box is as deep in the compound as before, so it pays the same.
        int sx = x0 + 26, sz = z0 + 20;
        fill(c, sx - 3, y0 - 18, sz - 3, sx + 3, y0, sz + 3, 0, 0);
        shell(c, sx - 3, y0 - 18, sz - 3, sx + 3, y0, sz + 3, 251, 7);
        fill(c, sx - 2, y0, sz - 2, sx + 2, y0, sz + 2, 0, 0);            // the open mouth
        fill(c, sx - 1, y0 - 17, sz - 1, sx + 1, y0 - 3, sz + 1, 251, 0);   // the bird: white concrete (was iron block)
        fill(c, sx - 1, y0 - 2, sz - 1, sx + 1, y0 - 1, sz + 1, 155, 2);
        fill(c, sx - 2, y0 - 17, sz - 2, sx - 2, y0, sz - 2, 65, 3);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
            if (dx == -2 && dz == -3) continue;                            // the way off the ladder
            put(c, sx + dx, y0 + 1, sz + dz, 251, 7);
            put(c, sx + dx, y0 + 2, sz + dz, 101, 0);
        }
        fill(c, sx, y0, sz, sx, y0 + 1, sz, 155, 1);
        for (int dy = -16; dy <= -2; dy += 5) put(c, sx + 3, y0 + dy, sz, 89, 0);
        graded(c, s, sx + 2, y0 - 17, sz + 2, r, GARRISON_LOOT, true);
        mob(c, sx + 2, y0 - 17, sz - 2, EntityType.CREEPER);
        mob(c, sx - 2, y0 - 17, sz + 2, EntityType.CREEPER);
        // The strongroom, cut under the north-east quarter and still locked from inside.
        int bx = x0 + 34, bz = z0 + 32, by = y0 - 9;
        fill(c, bx, by, bz, bx + 14, by + 5, bz + 8, 0, 0);
        shell(c, bx, by, bz, bx + 14, by + 5, bz + 8, 49, 0);
        fill(c, bx + 1, by, bz + 1, bx + 13, by, bz + 7, 251, 7);
        // Structure audit 2026-09-23: the stair from the yard ran south out of the footprint, where the
        // chunks that build the compound stop writing, so it dead-ended in rock two blocks past the
        // fence and the vault was sealed. The doorway now opens onto a landing, and the flight turns
        // west under the yard, lined in concrete, to come up through the apron inside the south gate
        // behind a railing that is open to the west.
        fill(c, bx + 7, by + 1, bz + 8, bx + 8, by + 3, bz + 8, 0, 0);
        fill(c, bx + 7, by, bz + 9, bx + 9, by + 4, bz + 11, 251, 7);        // the landing's box
        fill(c, bx + 7, by + 1, bz + 9, bx + 8, by + 3, bz + 10, 0, 0);
        for (int j = 1; j <= 9; j++) {                                        // down from the yard
            int wx = bx + 7 - j, top = by + j;
            for (int wz = bz + 8; wz <= bz + 11; wz++) for (int y = top; y <= Math.min(top + 4, y0); y++) {
                boolean tread = wz == bz + 9 || wz == bz + 10;
                if (tread && y > top && y < top + 4) { put(c, wx, y, wz, 0, 0); continue; }
                Block b = blockAt(c, wx, y, wz);
                if (b != null && b.getTypeId() != 49) put(c, wx, y, wz, 251, 7);   // the vault's own wall stays
            }
            if (top + 4 > y0) fill(c, wx, top + 1, bz + 9, wx, y0, bz + 10, 0, 0);
        }
        for (int dx = -1; dx <= 2; dx++) for (int wz = bz + 8; wz <= bz + 11; wz++) {
            if (dx < 2 && wz != bz + 8 && wz != bz + 11) continue;
            put(c, bx + dx, y0 + 1, wz, 251, 7);
            put(c, bx + dx, y0 + 2, wz, 101, 0);
        }
        for (int dx = 2; dx <= 12; dx += 2) {
            put(c, bx + dx, by + 1, bz + 2, 43, 8);                   // racks: 43:8, was iron block
            put(c, bx + dx, by + 1, bz + 6, 43, 8);
        }
        put(c, bx + 7, by + 4, bz + 4, 89, 0);
        graded(c, s, bx + 2, by + 1, bz + 4, r, GARRISON_LOOT, true);
        graded(c, s, bx + 12, by + 1, bz + 4, r, GARRISON_LOOT, false);
        mob(c, bx + 7, by + 1, bz + 2, EntityType.CREEPER);
        mob(c, bx + 5, by + 1, bz + 6, EntityType.SKELETON);
        // Structure audit 2026-09-23: the pad is now cut out of the ground (excavate()), which left raw
        // rock and sand faces round the fence and, where the land stands higher, no way down to the
        // gate. The cut is faced with concrete, and the road out of the south gate climbs as a quartz
        // stair ramp -- inside the fence and out through the two rows the compound's chunks still
        // write -- to the ground outside. On level ground there is nothing to climb and no ramp.
        for (int dx = -1; dx <= 57; dx++) for (int dz = -1; dz <= 45; dz++) {
            if (dx != -1 && dx != 57 && dz != -1 && dz != 45) continue;
            if (dz == 45 && dx >= 26 && dx <= 31) continue;
            for (int y = y0 + 1; y <= y0 + 16 && ground(c, x0 + dx, y, z0 + dz); y++) put(c, x0 + dx, y, z0 + dz, 251, 7);
        }
        int rise = Math.min(10, Math.max(Math.max(t.sample(x0 + 26, z0 + 46).y, t.sample(x0 + 30, z0 + 46).y),
                t.sample(x0 + 28, z0 + 48).y) - y0);
        for (int j = 1; j <= rise; j++) {
            int wz = z0 + 46 - (rise - j);
            for (int wx = x0 + 27; wx <= x0 + 30; wx++) {
                if (j > 1) fill(c, wx, y0 + 1, wz, wx, y0 + j - 1, wz, 251, 8);
                put(c, wx, y0 + j, wz, 156, 2);
                for (int y = y0 + j + 1; y <= y0 + j + 12; y++) {             // headroom, and the cut through the bank
                    Block b = blockAt(c, wx, y, wz);
                    if (b == null || (b.getTypeId() == 0 && y > y0 + j + 3)) break;
                    put(c, wx, y, wz, 0, 0);
                }
            }
        }
    }

    // == 4. The Super Mall =======================================================
    // Three storeys of retail with the roof down the middle of it. Escalators to nowhere,
    // a food court somebody slept in, planters gone feral, and a fountain that is still,
    // for reasons nobody wants to think about, full.

    private static final int[][] MALL_LOOT = {
        {354, 0,  8, 1,  2}, {357, 0, 13, 5, 14}, {400, 0,  9, 1,  3}, {360, 0, 11, 4, 10},
        {35, 9, 12, 6, 18}, {171, 9, 12, 6, 18}, {351, 9, 13, 8, 22}, {340, 0, 10, 2,  6},
        {390, 0,  8, 1,  4}, {425, 0,  6, 1,  2}, {395, 0,  6, 1,  1}, {322, 0,  5, 1,  1},
    };

    /** An open cell with something solid beside or over it, for a cobweb to hang from (structure audit 2026-09-23). */
    private static boolean clings(Chunk c, int wx, int wy, int wz) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null || b.getTypeId() != 0) return false;
        int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
        for (int[] d : n) {
            Block o = blockAt(c, wx + d[0], wy + d[1], wz + d[2]);
            if (o != null && o.getType().isSolid()) return true;
        }
        return false;
    }

    private static void mall(Chunk c, Terrain t) {
        Site s = ground(t, c, MALL_CELL, 0x4D414C4CL, 41, 35, 7, RANK_MALL);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 40, z1 = z0 + 34;
        // Footing in plain stone under one stone-brick course (structure audit 2026-09-23): where a
        // ravine runs past, the foundation showed as a sheer masonry wall; it now reads as the rock.
        for (int dx = 0; dx <= 40; dx++) for (int dz = 0; dz <= 34; dz++)
            footing(c, s, x0 + dx, z0 + dz, 1, 0);
        fill(c, x0, y0 - 1, z0, x1, y0 - 1, z1, 98, 0);
        int[] tile = {235, 236, 237, 238, 239, 240, 241, 242};
        // Three floors of shell, glass at street level, clay above.
        for (int f = 0; f < 3; f++) {
            int fy = y0 + f * 6;
            fill(c, x0, fy, z0, x1, fy, z1, 159, f == 0 ? 8 : 0);
            shell(c, x0, fy, z0, x1, fy + 5, z1, f == 0 ? 95 : 159, f == 0 ? 0 : 8);
            fill(c, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 5, z1 - 1, 0, 0);
            fill(c, x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, tile[(f * 3) & 7], 0);
            // Storefront bays: counters, a pane front and a back room.
            for (int dx = 3; dx <= 33; dx += 10) {
                fill(c, x0 + dx, fy + 1, z0 + 3, x0 + dx + 6, fy + 4, z0 + 3, 102, 0);
                fill(c, x0 + dx + 2, fy + 1, z0 + 3, x0 + dx + 3, fy + 4, z0 + 3, 0, 0);
                fill(c, x0 + dx, fy + 1, z0 + 1, x0 + dx, fy + 4, z0 + 3, 159, 14);
                fill(c, x0 + dx + 6, fy + 1, z0 + 1, x0 + dx + 6, fy + 4, z0 + 3, 159, 14);
                fill(c, x0 + dx + 1, fy + 1, z0 + 1, x0 + dx + 1, fy + 1, z0 + 2, 155, 0);
                put(c, x0 + dx + 5, fy + 5, z0 + 2, 89, 0);
                fill(c, x0 + dx, fy + 1, z1 - 3, x0 + dx + 6, fy + 4, z1 - 3, 102, 0);
                fill(c, x0 + dx + 3, fy + 1, z1 - 3, x0 + dx + 4, fy + 4, z1 - 3, 0, 0);
                fill(c, x0 + dx, fy + 1, z1 - 1, x0 + dx, fy + 4, z1 - 3, 159, 3);
                fill(c, x0 + dx + 6, fy + 1, z1 - 1, x0 + dx + 6, fy + 4, z1 - 3, 159, 3);
                // Structure audit 2026-09-23: the south bays were empty boxes. Each gets the north
                // bays' fittings, mirrored: a counter and a light.
                fill(c, x0 + dx + 5, fy + 1, z1 - 2, x0 + dx + 5, fy + 1, z1 - 1, 155, 0);
                put(c, x0 + dx + 1, fy + 5, z1 - 2, 89, 0);
                if (f > 0 && (dx == 13 || dx == 33)) {                    // clothes racks in two upper shops
                    for (int k = 2; k <= 4; k++) {
                        put(c, x0 + dx + k, fy + 1, z0 + 1, 85, 0);
                        put(c, x0 + dx + k, fy + 2, z0 + 1, 171, (dx + k + f * 3) % 16);
                    }
                }
            }
            // Concourse lights: flush under the ceiling (they hung a block below it), two more a floor.
            put(c, x0 + 8, fy + 5, z0 + 17, 169, 0);
            put(c, x0 + 32, fy + 5, z0 + 17, 169, 0);
            put(c, x0 + 8, fy + 5, z0 + 8, 169, 0);
            put(c, x0 + 32, fy + 5, z0 + 26, 169, 0);
            if (f > 0) {
                // Structure audit 2026-09-23: the upper wings were bare plates. Each gets a kiosk (a
                // counter ring under the concourse light, open on its south side), a pair of benches
                // back to back, and a planter.
                for (int kx = 8; kx <= 32; kx += 24) {
                    for (int ddx = -1; ddx <= 1; ddx++) for (int ddz = -1; ddz <= 1; ddz++)
                        if ((ddx != 0 || ddz != 0) && !(ddx == 0 && ddz == 1)) put(c, x0 + kx + ddx, fy + 1, z0 + 17 + ddz, 155, 0);
                    put(c, x0 + kx, fy + 1, z0 + 23, 109, 2); put(c, x0 + kx, fy + 1, z0 + 24, 109, 3);
                    put(c, x0 + kx + 1, fy + 1, z0 + 23, 109, 2); put(c, x0 + kx + 1, fy + 1, z0 + 24, 109, 3);
                }
                put(c, x0 + 3, fy + 1, z0 + 17, 3, 0); put(c, x0 + 3, fy + 2, z0 + 17, 18, 4);
                put(c, x0 + 37, fy + 1, z0 + 17, 3, 0); put(c, x0 + 37, fy + 2, z0 + 17, 18, 4);
            }
        }
        // The atrium, open all the way up, and the escalators that cross it.
        fill(c, x0 + 14, y0 + 1, z0 + 12, x0 + 26, y0 + 17, z0 + 22, 0, 0);
        // Structure audit 2026-09-23: every escalator topped out mid-atrium a floor-height short of the
        // next level, the gallery strips it should have reached were solid fields of fence (which also
        // fenced in the next flight's foot), and the steps hung by their corners. Each flight now has a
        // stringer under it and a seventh step onto a quartz bridge to the far gallery; the galleries
        // are railed along the void and the bridges only, with gaps where a bridge lands and a flight
        // starts.
        for (int f = 0; f < 2; f++) {
            int fy = y0 + f * 6;
            for (int i = 0; i <= 6; i++) {
                fill(c, x0 + 15 + i, fy + i, z0 + 13, x0 + 15 + i, fy + i, z0 + 15, 109, 0);
                fill(c, x0 + 25 - i, fy + i, z0 + 19, x0 + 25 - i, fy + i, z0 + 21, 109, 1);
                if (i < 2) continue;
                fill(c, x0 + 15 + i, fy + i - 1, z0 + 13, x0 + 15 + i, fy + i - 1, z0 + 15, 109, 5);   // stringer
                fill(c, x0 + 25 - i, fy + i - 1, z0 + 19, x0 + 25 - i, fy + i - 1, z0 + 21, 109, 4);
            }
            fill(c, x0 + 14, fy + 6, z0 + 12, x0 + 16, fy + 6, z0 + 22, 155, 0);
            fill(c, x0 + 24, fy + 6, z0 + 12, x0 + 26, fy + 6, z0 + 22, 155, 0);
            fill(c, x0 + 22, fy + 6, z0 + 13, x0 + 23, fy + 6, z0 + 15, 155, 0);    // bridge A to the east gallery
            fill(c, x0 + 17, fy + 6, z0 + 19, x0 + 18, fy + 6, z0 + 21, 155, 0);    // bridge B to the west gallery
            fill(c, x0 + 16, fy + 7, z0 + 12, x0 + 16, fy + 7, z0 + 22, 85, 0);
            fill(c, x0 + 24, fy + 7, z0 + 12, x0 + 24, fy + 7, z0 + 22, 85, 0);
            fill(c, x0 + 17, fy + 7, z0 + 11, x0 + 23, fy + 7, z0 + 11, 85, 0);
            fill(c, x0 + 17, fy + 7, z0 + 23, x0 + 23, fy + 7, z0 + 23, 85, 0);
            fill(c, x0 + 22, fy + 7, z0 + 12, x0 + 23, fy + 7, z0 + 12, 85, 0);
            fill(c, x0 + 22, fy + 7, z0 + 16, x0 + 23, fy + 7, z0 + 16, 85, 0);
            fill(c, x0 + 17, fy + 7, z0 + 18, x0 + 18, fy + 7, z0 + 18, 85, 0);
            fill(c, x0 + 17, fy + 7, z0 + 22, x0 + 18, fy + 7, z0 + 22, 85, 0);
            fill(c, x0 + 24, fy + 7, z0 + 13, x0 + 24, fy + 7, z0 + 15, 0, 0);      // where bridge A lands
            fill(c, x0 + 16, fy + 7, z0 + 19, x0 + 16, fy + 7, z0 + 21, 0, 0);      // where bridge B lands
            if (f == 0) {                                                        // where the next flights start
                fill(c, x0 + 16, fy + 7, z0 + 13, x0 + 16, fy + 7, z0 + 15, 109, 0);
                fill(c, x0 + 24, fy + 7, z0 + 19, x0 + 24, fy + 7, z0 + 21, 109, 1);
            }
        }
        // Skylight, and the part of it that is now on the ground floor.
        fill(c, x0, y0 + 18, z0, x1, y0 + 18, z1, 44, 5);
        fill(c, x0 + 13, y0 + 18, z0 + 11, x0 + 27, y0 + 18, z0 + 23, 95, 0);
        // Debris: the skylight's own white glass, and never on an escalator lane or the fountain
        // (structure audit 2026-09-23; same draws as before).
        for (int i = 0; i < 40; i++) {
            int gx = x0 + 14 + r.nextInt(13), gz = z0 + 12 + r.nextInt(11);
            put(c, gx, y0 + 18, gz, 0, 0);
            boolean glass = r.nextInt(3) == 0;
            int ddz = gz - z0;
            boolean lane = (ddz >= 13 && ddz <= 15) || (ddz >= 19 && ddz <= 21);
            boolean fountain = gx >= x0 + 18 && gx <= x0 + 22 && ddz >= 15 && ddz <= 19;
            if (!lane && !fountain) put(c, gx, y0 + 1, gz, glass ? 95 : 98, 0);
        }
        // The fountain, the planters, and the food court above it.
        fill(c, x0 + 18, y0 + 1, z0 + 15, x0 + 22, y0 + 1, z0 + 19, 155, 0);
        fill(c, x0 + 19, y0 + 1, z0 + 16, x0 + 21, y0 + 1, z0 + 18, 9, 0);
        put(c, x0 + 20, y0 + 2, z0 + 17, 155, 1);
        for (int i = 0; i < 4; i++) {
            int px = x0 + 16 + (i % 2) * 8, pz = z0 + 13 + (i / 2) * 8;
            // Two of them stood on the escalators' feet (structure audit 2026-09-23): moved to the free
            // corners of the atrium floor.
            if (i == 0) { px = x0 + 22; pz = z0 + 12; }
            if (i == 3) { px = x0 + 18; pz = z0 + 21; }
            fill(c, px, y0 + 1, pz, px + 1, y0 + 1, pz + 1, 3, 0);
            // Planter shrubs: leaves that never decay (18:0 has no log near it and would drop away).
            put(c, px, y0 + 2, pz, 18, 4); put(c, px + 1, y0 + 2, pz + 1, 18, 4);
        }
        // Structure audit 2026-09-23: the food court had no floor under it -- half its tables and a
        // zombie spawner hung in the atrium over the fountain. It is now on the first-floor gallery
        // north of the atrium, overlooking it: tables with stools, a serving counter under a light,
        // a bedroll among the tables, and the spawner on the floor.
        for (int dx = 15; dx <= 24; dx += 3) for (int dz = 7; dz <= 9; dz += 2) {
            put(c, x0 + dx, y0 + 7, z0 + dz, 44, 7);
            put(c, x0 + dx, y0 + 8, z0 + dz, 155, 1);
            put(c, x0 + dx + 1, y0 + 7, z0 + dz, 109, 0);
        }
        fill(c, x0 + 18, y0 + 7, z0 + 5, x0 + 22, y0 + 7, z0 + 5, 155, 0);
        put(c, x0 + 20, y0 + 11, z0 + 5, 169, 0);
        put(c, x0 + 26, y0 + 7, z0 + 10, 171, 14); put(c, x0 + 27, y0 + 7, z0 + 10, 171, 14);
        // Doors, and the crowd that never left. The doors were cut one block west of the corridor
        // between the storefronts, so a bay wall blocked a third of each; now they line up with it.
        fill(c, x0 + 20, y0 + 1, z0, x0 + 22, y0 + 3, z0, 0, 0);
        fill(c, x0 + 20, y0 + 1, z1, x0 + 22, y0 + 3, z1, 0, 0);
        mob(c, x0 + 20, y0 + 1, z0 + 24, EntityType.ZOMBIE);
        mob(c, x0 + 10, y0 + 1, z0 + 8, EntityType.ZOMBIE);
        mob(c, x0 + 30, y0 + 1, z0 + 28, EntityType.ZOMBIE);
        mob(c, x0 + 20, y0 + 7, z0 + 8, EntityType.ZOMBIE);
        mob(c, x0 + 8, y0 + 13, z0 + 6, EntityType.SPIDER);
        mob(c, x0 + 33, y0 + 13, z0 + 27, EntityType.SPIDER);
        // Cobwebs only where they can hang from something (they floated over the void); same draws.
        for (int i = 0; i < 20; i++) {
            int wx = x0 + 2 + r.nextInt(37), wy = y0 + 13 + r.nextInt(4), wz = z0 + 2 + r.nextInt(31);
            if (clings(c, wx, wy, wz)) put(c, wx, wy, wz, 30, 0);
        }
        graded(c, s, x0 + 5, y0 + 1, z0 + 2, r, MALL_LOOT, false);
        graded(c, s, x0 + 25, y0 + 1, z1 - 2, r, MALL_LOOT, false);
        graded(c, s, x0 + 15, y0 + 7, z0 + 5, r, MALL_LOOT, true);
        graded(c, s, x0 + 35, y0 + 13, z0 + 17, r, MALL_LOOT, true);
        graded(c, s, x0 + 20, y0 + 13, z0 + 30, r, MALL_LOOT, false);
    }

    // == 5. The Interceptor ======================================================
    // A sewer built for a city that is not up there any more: two trunk tunnels nine
    // across crossing at a junction shaft, side pipes going off into the rock, a weir,
    // and a great deal of standing water nobody has had to look at in a long time.

    private static final int[][] SEWER_LOOT = {
        {367, 0, 15, 6, 18}, {375, 0, 12, 4, 12}, {318, 0, 13, 6, 18}, {287, 0, 12, 6, 18},
        {352, 0, 11, 5, 16}, {331, 0, 11, 4, 14}, {326, 0, 8, 1, 3}, {388, 0,  6, 1,  3},
        {384, 0,  5, 1,  2}, {346, 0,  7, 1,  1}, {349, 0,  9, 2,  6}, {263, 0, 10, 4, 12},
    };

    /** One barrel-vaulted run. Axis 0 is along x, axis 1 along z. */
    private static void trunk(Chunk c, Random r, int axis, int a0, int a1, int cross, int y, int half) {
        // Structure audit 2026-09-23: each column had only its air run and one vault block, so the
        // lower walls and the band between vault courses were raw rock, open to caves and lava in
        // places. The whole cross-section is laid in brick first and then carved.
        if (axis == 0) fill(c, a0, y, cross - half - 1, a1, y + 2 * half + 1, cross + half + 1, 98, 0);
        else fill(c, cross - half - 1, y, a0, cross + half + 1, y + 2 * half + 1, a1, 98, 0);
        for (int d = -half; d <= half; d++) {
            int h = (int) Math.round(Math.sqrt(Math.max(0, half * half - d * d)) * 1.15) + 1;
            int lo = y + 1, hi = y + 1 + h;
            if (axis == 0) {
                fill(c, a0, lo, cross + d, a1, hi, cross + d, 0, 0);
                fill(c, a0, hi + 1, cross + d, a1, hi + 1, cross + d, d == 0 ? 98 : 98, d == 0 ? 3 : 1);
                fill(c, a0, y, cross + d, a1, y, cross + d, Math.abs(d) <= 1 ? 4 : 98, Math.abs(d) <= 1 ? 0 : 0);
                if (Math.abs(d) <= 1) fill(c, a0, y + 1, cross + d, a1, y + 1, cross + d, 9, 0);
                if (Math.abs(d) == 2) fill(c, a0, y + 1, cross + d, a1, y + 1, cross + d, 44, 5);
            } else {
                fill(c, cross + d, lo, a0, cross + d, hi, a1, 0, 0);
                fill(c, cross + d, hi + 1, a0, cross + d, hi + 1, a1, 98, d == 0 ? 3 : 1);
                fill(c, cross + d, y, a0, cross + d, y, a1, Math.abs(d) <= 1 ? 4 : 98, 0);
                if (Math.abs(d) <= 1) fill(c, cross + d, y + 1, a0, cross + d, y + 1, a1, 9, 0);
                if (Math.abs(d) == 2) fill(c, cross + d, y + 1, a0, cross + d, y + 1, a1, 44, 5);
            }
        }
        // Ribs every six, and a lamp on every other one. The rib used to be written into the vault's
        // own cell and never showed; it now stands a course proud under the vault (audit 2026-09-23).
        for (int a = a0 + 3; a <= a1 - 3; a += 6) {
            for (int d = -half + 1; d <= half - 1; d++) {
                int h = (int) Math.round(Math.sqrt(Math.max(0, half * half - d * d)) * 1.15) + 1;
                if (axis == 0) put(c, a, y + 1 + h, cross + d, 98, d == 0 ? 3 : 0);
                else put(c, cross + d, y + 1 + h, a, 98, d == 0 ? 3 : 0);
            }
            if (((a / 6) & 1) == 0) {
                if (axis == 0) put(c, a, y + 4, cross + half - 1, 89, 0);
                else put(c, cross + half - 1, y + 4, a, 89, 0);
            }
        }
    }

    /** Sewer growth (structure audit 2026-09-23): a vine or web only in an open cell against brick. */
    private static void growth(Chunk c, int wx, int wy, int wz, int id) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null || b.getTypeId() != 0) return;
        int bits = (brick(c, wx, wy, wz + 1) ? 1 : 0) | (brick(c, wx - 1, wy, wz) ? 2 : 0)
                 | (brick(c, wx, wy, wz - 1) ? 4 : 0) | (brick(c, wx + 1, wy, wz) ? 8 : 0);
        boolean roof = brick(c, wx, wy + 1, wz);
        if (id == 106) { if (bits != 0 || roof) put(c, wx, wy, wz, 106, bits); }
        else if (roof || Integer.bitCount(bits) >= 2) put(c, wx, wy, wz, 30, 0);
    }

    private static boolean brick(Chunk c, int wx, int wy, int wz) {
        Block b = blockAt(c, wx, wy, wz);
        if (b == null) return false;
        int id = b.getTypeId();
        return id == 98 || id == 97 || id == 4;
    }

    private static void sewer(Chunk c, Terrain t) {
        Site s = deep(t, c, SEWER_CELL, 0x53455752L, 61, 61, 16, RANK_SEWER);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 60, z1 = z0 + 60;
        int mx = x0 + 30, mz = z0 + 30;
        trunk(c, r, 0, x0, x1, mz, y0, 4);
        trunk(c, r, 1, z0, z1, mx, y0, 4);
        // Junction: a round shaft with galleries, and a weir into the sump.
        fill(c, mx - 9, y0 + 1, mz - 9, mx + 9, y0 + 12, mz + 9, 0, 0);
        shell(c, mx - 10, y0, mz - 10, mx + 10, y0 + 13, mz + 10, 98, 1);
        // Structure audit 2026-09-23: that shell walled all four trunks off from the junction. Each
        // mouth is cut back to the trunk's profile, and a slab lip holds the channel at the weir.
        trunk(c, r, 0, mx - 10, mx - 10, mz, y0, 4);
        trunk(c, r, 0, mx + 10, mx + 10, mz, y0, 4);
        trunk(c, r, 1, mz - 10, mz - 10, mx, y0, 4);
        trunk(c, r, 1, mz + 10, mz + 10, mx, y0, 4);
        fill(c, mx - 9, y0, mz - 9, mx + 9, y0, mz + 9, 4, 0);
        fill(c, mx - 5, y0 - 2, mz - 5, mx + 5, y0, mz + 5, 9, 0);
        fill(c, mx - 6, y0, mz - 6, mx + 6, y0, mz + 6, 44, 5);
        fill(c, mx - 5, y0, mz - 5, mx + 5, y0, mz + 5, 9, 0);
        fill(c, mx - 9, y0 + 1, mz - 1, mx - 9, y0 + 1, mz + 1, 44, 5);
        fill(c, mx + 9, y0 + 1, mz - 1, mx + 9, y0 + 1, mz + 1, 44, 5);
        fill(c, mx - 1, y0 + 1, mz - 9, mx + 1, y0 + 1, mz - 9, 44, 5);
        fill(c, mx - 1, y0 + 1, mz + 9, mx + 1, y0 + 1, mz + 9, 44, 5);
        // Galleries (structure audit 2026-09-23): shell() on a one-high box fills the whole layer, so
        // bars stood across the full walkway, and the third pass erased the junction's roof. Two
        // galleries now, each a slab walkway along the wall behind a bar rail on its inner edge; the
        // roof stays, with a chiseled cross for a boss. The ladder stands against the wall, from the
        // floor through a hatch in the lower gallery to the upper one, where riser 1 comes down.
        for (int dy = 4; dy <= 8; dy += 4) {
            shell(c, mx - 9, y0 + dy, mz - 9, mx + 9, y0 + dy, mz + 9, 44, 5);
            fill(c, mx - 7, y0 + dy, mz - 7, mx + 7, y0 + dy, mz + 7, 0, 0);
            shell(c, mx - 8, y0 + dy + 1, mz - 8, mx + 8, y0 + dy + 1, mz + 8, 101, 0);
            fill(c, mx - 7, y0 + dy + 1, mz - 7, mx + 7, y0 + dy + 1, mz + 7, 0, 0);
        }
        fill(c, mx - 9, y0 + 13, mz, mx + 9, y0 + 13, mz, 98, 3);
        fill(c, mx, y0 + 13, mz - 9, mx, y0 + 13, mz + 9, 98, 3);
        fill(c, mx + 9, y0 + 1, mz + 8, mx + 9, y0 + 9, mz + 8, 65, 4);
        put(c, mx - 10, y0 + 2, mz + 6, 89, 0);                            // lamps set in the wall, clear of the mouths
        put(c, mx + 10, y0 + 7, mz + 6, 89, 0);
        put(c, mx - 10, y0 + 12, mz - 6, 89, 0);
        // Side pipes off the trunks, small and dark.
        int[][] pipes = {{x0 + 12, mz - 12, 0}, {x0 + 44, mz + 12, 0}, {mx - 12, z0 + 14, 1}, {mx + 12, z0 + 46, 1}};
        // Structure audit 2026-09-23: the pipes were bores through raw rock that also cut the trunk's
        // channel and walkway where they met it. Each is now a brick culvert from its capped end to the
        // trunk wall, and stops there.
        for (int[] p : pipes) {
            if (p[2] == 0) {
                int dir = Integer.signum(p[1] - mz), near = mz + dir * 4, far = p[1] + dir;
                fill(c, p[0] - 2, y0, Math.min(near + dir, far), p[0] + 2, y0 + 4, Math.max(near + dir, far), 98, 0);
                fill(c, p[0] - 1, y0 + 1, Math.min(near, p[1]), p[0] + 1, y0 + 3, Math.max(near, p[1]), 0, 0);
                fill(c, p[0] - 2, y0, Math.min(near + dir, p[1]), p[0] + 2, y0, Math.max(near + dir, p[1]), 98, 2);
                fill(c, p[0] - 1, y0 + 1, p[1], p[0] + 1, y0 + 1, p[1], 9, 0);
                put(c, p[0], y0 + 4, p[1], 89, 0);
            } else {
                int dir = Integer.signum(p[0] - mx), near = mx + dir * 4, far = p[0] + dir;
                fill(c, Math.min(near + dir, far), y0, p[1] - 2, Math.max(near + dir, far), y0 + 4, p[1] + 2, 98, 0);
                fill(c, Math.min(near, p[0]), y0 + 1, p[1] - 1, Math.max(near, p[0]), y0 + 3, p[1] + 1, 0, 0);
                fill(c, Math.min(near + dir, p[0]), y0, p[1] - 2, Math.max(near + dir, p[0]), y0, p[1] + 2, 98, 2);
                fill(c, p[0], y0 + 1, p[1] - 1, p[0], y0 + 1, p[1] + 1, 9, 0);
                put(c, p[0], y0 + 4, p[1], 89, 0);
            }
        }
        // The trunk ends were open cross-sections against the rock: each is closed by a brick
        // bulkhead with a barred outfall over the channel, so the sewer runs on past a locked grate.
        for (int e = 0; e < 4; e++) {
            int ex = e == 0 ? x0 : e == 1 ? x1 : mx, ez = e == 2 ? z0 : e == 3 ? z1 : mz;
            for (int d = -5; d <= 5; d++) for (int y = y0 + 1; y <= y0 + 8; y++) {
                int wx = e < 2 ? ex : ex + d, wz = e < 2 ? ez + d : ez;
                put(c, wx, y, wz, Math.abs(d) <= 1 && y <= y0 + 2 ? 101 : 98, Math.abs(d) <= 1 && y <= y0 + 2 ? 0 : 1);
            }
        }
        // Growth, grates, and the things that live behind the brick.
        // Structure audit 2026-09-23: the growth landed anywhere -- vines and webs in mid-air or cut
        // into the rock, lone cobblestone 'eggs' floating in the tunnels. Growth now only takes open
        // cells against the brick (a vine on the side it touches), and the eggs are bricks of the
        // wall itself. Same draws as before.
        for (int i = 0; i < 90; i++) {
            int gx = x0 + 2 + r.nextInt(57), gz = z0 + 2 + r.nextInt(57);
            int gy = y0 + 2 + r.nextInt(6);
            int hang = r.nextInt(4) == 0 ? 30 : 106, side = r.nextInt(4) == 0 ? 0 : 1 << r.nextInt(4);
            if (side >= 0) growth(c, gx, gy, gz, hang);
            if (r.nextInt(6) == 0) {
                int ey = y0 + 1 + r.nextInt(3);
                Block e = blockAt(c, gx, ey, gz);
                if (e != null && e.getTypeId() == 98) put(c, gx, ey, gz, 97, 2 + (e.getData() & 3));
            }
        }
        for (int a = x0 + 8; a <= x1 - 8; a += 14) {
            if (Math.abs(a - mx) <= 10) continue;                             // no loose grate posts in the junction
            fill(c, a, y0 + 1, mz - 4, a, y0 + 3, mz - 4, 101, 0);
            fill(c, a, y0 + 1, mz + 4, a, y0 + 3, mz + 4, 101, 0);
        }
        // Riser 1 comes down on the upper gallery walkway against the wall (it landed on the bars over
        // the void); riser 2 starts in the west trunk's vault and runs down to its floor on a pier (it
        // stopped in the rock two blocks over the vault).
        riser(c, t, mx, mz - 9, y0 + 13, 98, 1);
        if (shaft(c, t, x0 + 6, mz + 3, y0 + 6, y0 + 5, 1, 98, 2)) descend(c, x0 + 6, y0 + 5, mz + 3, 98, 2);
        mob(c, mx - 6, y0 + 1, mz - 6, EntityType.CAVE_SPIDER);
        mob(c, mx + 6, y0 + 1, mz + 6, EntityType.CAVE_SPIDER);
        mob(c, x0 + 8, y0 + 1, mz + 3, EntityType.SLIME);
        mob(c, x1 - 8, y0 + 1, mz - 3, EntityType.SLIME);
        mob(c, mx + 3, y0 + 1, z0 + 8, EntityType.SILVERFISH);
        mob(c, mx - 3, y0 + 1, z1 - 8, EntityType.SILVERFISH);
        // The two junction chests and the gallery spawner hung in the void at the galleries' inner edge
        // (structure audit 2026-09-23): each now stands in a niche in the junction wall off a walkway,
        // at the same depth as before.
        put(c, mx + 10, y0 + 9, mz - 5, 0, 0);
        mob(c, mx + 10, y0 + 9, mz - 5, EntityType.CAVE_SPIDER);
        put(c, mx - 10, y0 + 6, mz + 7, 0, 0);
        graded(c, s, mx - 10, y0 + 5, mz + 7, r, SEWER_LOOT, false);
        put(c, mx + 10, y0 + 10, mz - 7, 0, 0);
        graded(c, s, mx + 10, y0 + 9, mz - 7, r, SEWER_LOOT, true);
        graded(c, s, x0 + 12, y0 + 1, mz - 12, r, SEWER_LOOT, false);
        graded(c, s, mx + 12, y0 + 1, z0 + 46, r, SEWER_LOOT, true);
        graded(c, s, x0 + 3, y0 + 1, mz + 3, r, SEWER_LOOT, false);
    }

    // == 6. The Airliner =========================================================
    // Came down flat and broke into three. Tail on its side in the scorch mark, the
    // centre section upright with the wings still on, the nose two hundred feet further
    // on with everything in it thrown forward. The cabin lights are, absurdly, still lit.

    private static final int[][] AIRLINER_LOOT = {
        {401, 0, 6, 1, 1}, {364, 0, 11, 3,  9}, {297, 0, 12, 3,  9}, {373, 0,  8, 1,  2},
        {335, 0, 10, 2, 6}, {395, 0,  7, 1,  1}, {345, 0,  7, 1,  1}, {347, 0,  7, 1,  1},
        {351, 12, 9, 4, 12}, {366, 0, 11, 3, 9}, { 20, 0, 10, 4, 12}, {171, 12, 9, 3, 10},
    };

    /** One barrel of fuselage, centred on cz, running along x. */
    private static void barrel(Chunk c, int ax, int bx, int cy, int cz, int rad, int roll) {
        for (int dz = -rad; dz <= rad; dz++) for (int dy = -rad; dy <= rad; dy++) {
            double d = Math.sqrt(dz * dz + dy * dy);
            int wz = cz + dz + roll * (dy > 0 ? 1 : 0), wy = cy + dy;
            if (d > rad + 0.4) continue;
            if (d > rad - 0.9) {
                fill(c, ax, wy, wz, bx, wy, wz, 155, 0);
                // Window band (structure audit 2026-09-23): the old test ran on the wall's z, which is
                // never a multiple of four on these sites, so no window was ever cut. It is now a row of
                // light-blue panes every other block along the side walls, solid skin kept at the ends.
                if (dy == 0) for (int wx = ax + 1; wx < bx; wx++) if (((wx - ax) & 1) == 1) put(c, wx, wy, wz, 95, 3);
            } else {
                fill(c, ax, wy, wz, bx, wy, wz, 0, 0);
            }
        }
        fill(c, ax, cy - rad + 1, cz - rad + 2, bx, cy - rad + 1, cz + rad - 2, 43, 8);   // floor strip: 43:8, was iron
    }

    /*
     * A torn end of fuselage (structure audit 2026-09-23). Every break used to be a clean circle with
     * the cabin floor two blocks over the furrow and nothing to climb it by. The rim loses a few skin
     * panels, which lie on the scorch beside it, and a 3-wide quartz step at the foot of the opening
     * (dir +1: the tube runs east of x, -1: west) takes the player up into the cabin. The step and the
     * panels go only where the cell is open and has ground under it (a panel reaches down to it).
     */
    private static void tornEnd(Chunk c, Random d, int x, int dir, int y0, int cy, int cz, int rad, int roll) {
        for (int k = 0; k < 3; k++) {                                   // skin torn off the rim, upper half
            int side = d.nextInt(2), dy = 1 + d.nextInt(rad - 1);
            int tz = (int) Math.round(Math.sqrt(Math.max(0, rad * rad - dy * dy)));
            put(c, x, cy + dy, cz + roll + (side == 0 ? -tz : tz), 0, 0);
            int px = x - dir * (2 + d.nextInt(3)), pz = cz + d.nextInt(2 * rad + 1) - rad;
            int py = openFoot(c, px, y0, pz);
            if (py > 0) put(c, px, py, pz, k == 1 ? 43 : 155, k == 1 ? 8 : 0);
        }
        for (int dz = -1; dz <= 1; dz++) {
            int sx = x - dir, sz = cz + dz;
            Block b = blockAt(c, sx, y0, sz);
            if (b == null || (b.getTypeId() != 0 && b.getTypeId() != 51)) continue;
            for (int y = y0 - 1; y >= y0 - 4; y--) {                          // over a crack: hull plates
                Block u = blockAt(c, sx, y, sz);
                if (u == null || u.getType().isSolid()) break;
                put(c, sx, y, sz, 251, 8);
            }
            put(c, sx, y0, sz, 156, dir > 0 ? 0 : 1);                          // quartz step, rising into the tube
        }
    }

    /**
     * Where a case of baggage lies (structure audit 2026-09-23): the open cell on the ground of its
     * column from the furrow up to seven over it -- never on a seat, a light or another fixture of a
     * cabin it fell into -- or y0, the old place, when the column has none.
     */
    private static int baggageFoot(Chunk c, int wx, int y0, int wz) {
        for (int y = y0 - 1; y <= y0 + 7; y++) {
            Block b = blockAt(c, wx, y, wz), under = blockAt(c, wx, y - 1, wz), over = blockAt(c, wx, y + 1, wz);
            if (b == null || under == null || over == null) return y0;
            int u = under.getTypeId();
            if (b.getTypeId() == 0 && over.getTypeId() == 0 && under.getType().isSolid()
                    && u != 35 && u != 53 && u != 156 && u != 52 && u != 54 && u != 89 && u != 169 && u != 51) return y;
        }
        return y0;
    }

    /** The open cell on the ground at or just under y0 (the furrow), or -1: for debris that must lie on something. */
    private static int openFoot(Chunk c, int wx, int y0, int wz) {
        for (int y = y0 + 1; y >= y0 - 2; y--) {
            Block b = blockAt(c, wx, y, wz), under = blockAt(c, wx, y - 1, wz);
            if (b == null || under == null) return -1;
            if (b.getTypeId() == 0 && under.getType().isSolid() && under.getTypeId() != 51) return y;
        }
        return -1;
    }

    /**
     * The block of a furrow column that takes the burn (structure audit 2026-09-23): y0 - 1 where the
     * column is open at the furrow's level (y0 is then cut clear), else the top of the ground standing
     * over it (up to seven higher, open to the air above); -1 over a crack or outside this chunk.
     */
    private static int scorchTop(Chunk c, int wx, int y0, int wz) {
        Block at = blockAt(c, wx, y0, wz), above = blockAt(c, wx, y0 + 1, wz);
        if (at == null || above == null) return -1;
        if (above.getTypeId() == 0 || above.isLiquid() || !ground(c, wx, y0, wz)) {
            Block base = blockAt(c, wx, y0 - 2, wz);
            return base != null && base.getType().isSolid() ? y0 - 1 : -1;
        }
        for (int y = y0 + 1; y <= y0 + 7; y++) if (!ground(c, wx, y + 1, wz)) return ground(c, wx, y, wz) && !blockAt(c, wx, y, wz).isLiquid() ? y : -1;
        return -1;
    }

    private static void airliner(Chunk c, Terrain t) {
        Site s = ground(t, c, AIRLINER_CELL, 0x504C414EL, 49, 25, 7, RANK_AIRLINER);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, cz = z0 + 12;
        // The furrow it dug, and the burn either side of it.
        // Structure audit 2026-09-23: the scorch follows the real ground. Where the hill stands over the
        // furrow's level the top of the ground is burnt instead of a one-high slot being cut under it
        // (sealed slots, some with fires in them), and nothing is laid over a crack (a lone netherrack
        // floating over a crevasse). Same draws as before.
        for (int dx = 0; dx <= 48; dx++) for (int dz = 4; dz <= 20; dz++) {
            double edge = Math.abs(dz - 12) / 8.0;
            if (r.nextDouble() < 0.72 - edge * 0.5) {
                // Scorch: black concrete 251:15 (was coal block); same draw as before.
                boolean soot = r.nextInt(5) == 0;
                int top = scorchTop(c, x0 + dx, y0, z0 + dz);
                if (top < 0) continue;
                put(c, x0 + dx, top, z0 + dz, soot ? 251 : 87, soot ? 15 : 0);
                if (top == y0 - 1) put(c, x0 + dx, y0, z0 + dz, 0, 0);
            }
        }
        for (int i = 0; i < 18; i++) {
            int fx = x0 + r.nextInt(30), fz = z0 + 6 + r.nextInt(13);
            int top = scorchTop(c, fx, y0, fz);
            if (top < 0) continue;
            put(c, fx, top, fz, 87, 0);
            put(c, fx, top + 1, fz, 51, 0);
        }
        // Tail, on its side in the crater.
        barrel(c, x0 + 1, x0 + 9, y0 + 3, cz - 1, 3, 1);
        fill(c, x0 + 1, y0 + 4, cz - 4, x0 + 3, y0 + 12, cz - 4, 155, 0);
        fill(c, x0 + 2, y0 + 10, cz - 6, x0 + 4, y0 + 10, cz + 2, 155, 0);
        put(c, x0 + 1, y0 + 11, cz - 4, 159, 14);
        // Structure audit 2026-09-23: the tail held one chest on a bare floor. It gets the last two seat
        // rows (as in the centre section) and the rear galley -- cabinets, the coffee urn and a cabin
        // light still lit -- with the chest left in the aisle between them.
        for (int dx = 3; dx <= 4; dx++) {
            put(c, x0 + dx, y0 + 2, cz - 2, 35, 14); put(c, x0 + dx, y0 + 3, cz - 2, 53, 2);
            put(c, x0 + dx, y0 + 2, cz, 35, 14);     put(c, x0 + dx, y0 + 3, cz, 53, 3);
        }
        fill(c, x0 + 7, y0 + 2, cz, x0 + 8, y0 + 2, cz, 251, 0);
        put(c, x0 + 8, y0 + 2, cz - 2, 118, 2);
        put(c, x0 + 7, y0 + 5, cz, 169, 0);
        graded(c, s, x0 + 5, y0 + 2, cz - 1, r, AIRLINER_LOOT, false);
        // Centre section, upright, wings on.
        barrel(c, x0 + 14, x0 + 32, y0 + 4, cz, 4, 0);
        for (int dx = 16; dx <= 28; dx++) {
            int span = 8 - Math.abs(dx - 22) / 2;
            // Wing skins: smooth stone double slab 43:8 (was iron block).
            fill(c, x0 + dx, y0 + 3, cz - 4 - span, x0 + dx, y0 + 3, cz - 5, 43, 8);
            fill(c, x0 + dx, y0 + 3, cz + 5, x0 + dx, y0 + 3, cz + 4 + span, 43, 8);
        }
        fill(c, x0 + 20, y0 + 2, cz - 10, x0 + 23, y0 + 4, cz - 8, 251, 8);
        fill(c, x0 + 21, y0 + 3, cz - 11, x0 + 22, y0 + 3, cz - 11, 0, 0);
        fill(c, x0 + 20, y0 + 2, cz + 8, x0 + 23, y0 + 4, cz + 10, 251, 8);
        for (int dx = 16; dx <= 30; dx += 2) {                        // seat rows and cabin lights
            put(c, x0 + dx, y0 + 2, cz - 2, 35, 14); put(c, x0 + dx, y0 + 3, cz - 2, 53, 2);
            put(c, x0 + dx, y0 + 2, cz + 2, 35, 14); put(c, x0 + dx, y0 + 3, cz + 2, 53, 3);
            if ((dx & 3) == 0) put(c, x0 + dx, y0 + 7, cz, 169, 0);
        }
        fill(c, x0 + 22, y0 + 1, cz - 3, x0 + 26, y0 + 1, cz + 3, 251, 8);
        graded(c, s, x0 + 18, y0 + 2, cz, r, AIRLINER_LOOT, true);
        graded(c, s, x0 + 29, y0 + 2, cz - 1, r, AIRLINER_LOOT, false);
        mob(c, x0 + 24, y0 + 2, cz, EntityType.ZOMBIE);
        mob(c, x0 + 16, y0 + 2, cz + 1, EntityType.ZOMBIE);
        // Nose, further on and face down, with the flight deck crushed into it.
        barrel(c, x0 + 38, x0 + 46, y0 + 3, cz + 3, 3, 0);
        fill(c, x0 + 45, y0 + 2, cz + 1, x0 + 47, y0 + 5, cz + 5, 95, 3);
        // Structure audit 2026-09-23: the glass was hollowed only from x+46, so the slice at x+45 sealed
        // the cabin off from an empty, open-fronted glass box. The flight deck now opens off the cabin:
        // a deck floor, two pilot seats facing a black console under the windscreen (one pane gone),
        // switches on the console, and a hull panel pushed in over the first officer's seat.
        fill(c, x0 + 45, y0 + 2, cz + 2, x0 + 46, y0 + 4, cz + 4, 0, 0);
        fill(c, x0 + 45, y0 + 1, cz + 2, x0 + 47, y0 + 1, cz + 4, 251, 8);
        fill(c, x0 + 47, y0 + 2, cz + 2, x0 + 47, y0 + 2, cz + 4, 251, 15);
        put(c, x0 + 45, y0 + 2, cz + 2, 53, 1);
        put(c, x0 + 45, y0 + 2, cz + 4, 53, 1);
        put(c, x0 + 46, y0 + 2, cz + 3, 69, 2);
        put(c, x0 + 46, y0 + 2, cz + 2, 77, 2);
        put(c, x0 + 46, y0 + 2, cz + 4, 77, 2);
        put(c, x0 + 47, y0 + 4, cz + 3, 0, 0);
        put(c, x0 + 46, y0 + 4, cz + 4, 155, 0);
        put(c, x0 + 44, y0 + 5, cz + 3, 89, 0);
        for (int dx = 39; dx <= 40; dx++) {                               // first class, two rows
            put(c, x0 + dx, y0 + 2, cz + 2, 35, 14); put(c, x0 + dx, y0 + 3, cz + 2, 53, 2);
            put(c, x0 + dx, y0 + 2, cz + 4, 35, 14); put(c, x0 + dx, y0 + 3, cz + 4, 53, 3);
        }
        graded(c, s, x0 + 41, y0 + 2, cz + 3, r, AIRLINER_LOOT, true);
        mob(c, x0 + 43, y0 + 2, cz + 3, EntityType.ZOMBIE);
        // Structure audit 2026-09-23: every break had its cabin floor two blocks over the furrow with no
        // way up (the centre and the nose could not be entered on flat ground). Each open end is torn and
        // gets a quartz step. Its own generator, so the loot and baggage draws below are unchanged.
        Random tear = new Random(s.seed ^ 0x544541524EL);
        tornEnd(c, tear, x0 + 1, 1, y0, y0 + 3, cz - 1, 3, 1);
        tornEnd(c, tear, x0 + 9, -1, y0, y0 + 3, cz - 1, 3, 1);
        tornEnd(c, tear, x0 + 14, 1, y0, y0 + 4, cz, 4, 0);
        tornEnd(c, tear, x0 + 32, -1, y0, y0 + 4, cz, 4, 0);
        tornEnd(c, tear, x0 + 38, 1, y0, y0 + 3, cz + 3, 3, 0);
        // Baggage thrown out along the furrow. Structure audit 2026-09-23: each case lies on the real
        // ground of its column (it used to go in at site level, so on a slope a case and its chest were
        // buried in the hillside with stone on the lid); where the column has no open ground it keeps
        // the old place and the cell over its chest is cleared so the lid still opens.
        for (int i = 0; i < 7; i++) {
            int bx = x0 + 8 + r.nextInt(36), bz = cz - 7 + r.nextInt(15);
            int by = baggageFoot(c, bx, y0, bz);
            put(c, bx, by, bz, 251, r.nextInt(2) == 0 ? 11 : 14);
            if (i == 2 || i == 5) {
                Block lid = blockAt(c, bx, by + 2, bz);
                if (lid != null && lid.getType().isOccluding()) put(c, bx, by + 2, bz, 0, 0);
                graded(c, s, bx, by + 1, bz, r, AIRLINER_LOOT, false);
            }
        }
    }

    // == 7. The Field ============================================================
    // A shaft cut a long way down and then widened into something with no business being
    // underground: a tower of pods on scaffolding, ring after ring of them going up past
    // where the light reaches, a spine of machinery down the middle, and catwalks for
    // whatever it is that comes to service them.

    private static final int[][] MATRIX_LOOT = {
        {331, 0, 14, 10, 28}, {152, 0,  8, 1,  3}, {165, 0, 10, 2,  6}, {341, 0, 12, 4, 14},
        {348, 0, 11,  4, 12}, {368, 0,  7, 1,  3}, {264, 0,  8, 1,  3}, {403, 0,  5, 1,  1},
        { 20, 0, 12,  6, 18}, { 95, 0, 11, 4, 12}, { 89, 0,  9, 2,  6}, {138, 0,  2, 1,  1},
    };

    private static void matrix(Chunk c, Terrain t) {
        Site s = deep(t, c, MATRIX_CELL, 0x4D545258L, 45, 45, 64, RANK_MATRIX);
        if (s == null) return;
        Random r = new Random(s.seed);
        int mx = s.x + 22, mz = s.z + 22, y0 = s.y, y1 = s.y + 63;
        // The bore: a cylinder hollowed out of the rock and cased in black.
        for (int dx = -22; dx <= 22; dx++) for (int dz = -22; dz <= 22; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 22.4) continue;
            if (d > 20.6) fill(c, mx + dx, y0, mz + dz, mx + dx, y1, mz + dz, 251, 15);
            else fill(c, mx + dx, y0 + 1, mz + dz, mx + dx, y1 - 1, mz + dz, 0, 0);
        }
        fill(c, mx - 21, y0, mz - 21, mx + 21, y0, mz + 21, 251, 7);
        fill(c, mx - 21, y1, mz - 21, mx + 21, y1, mz + 21, 251, 15);
        // The spine: obsidian round a live core, with cable runs dropping off it.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 3.4) continue;
            // Obsidian spine round a red concrete core (was redstone block, which powered nothing).
            fill(c, mx + dx, y0 + 1, mz + dz, mx + dx, y1 - 1, mz + dz, d > 2.2 ? 49 : 251, d > 2.2 ? 0 : 14);
        }
        for (int dy = y0 + 4; dy < y1 - 2; dy += 7) {
            fill(c, mx - 5, dy, mz, mx + 5, dy, mz, 101, 0);
            fill(c, mx, dy, mz - 5, mx, dy, mz + 5, 101, 0);
            put(c, mx - 4, dy, mz, 169, 0); put(c, mx + 4, dy, mz, 169, 0);
        }
        // Pod scaffolding: two rings of vertical spines, pods cantilevered off both sides.
        int[] radii = {10, 16};
        for (int ri = 0; ri < 2; ri++) {
            int rad = radii[ri], steps = ri == 0 ? 12 : 18;
            for (int a = 0; a < steps; a++) {
                double ang = a * 2 * Math.PI / steps + ri * 0.13;
                int px = mx + (int) Math.round(Math.cos(ang) * rad);
                int pz = mz + (int) Math.round(Math.sin(ang) * rad);
                fill(c, px, y0 + 1, pz, px, y1 - 1, pz, 43, 8);           // spine: 43:8, was iron block
                int ox = (int) Math.round(Math.cos(ang) * 1.9), oz = (int) Math.round(Math.sin(ang) * 1.9);
                if (ox == 0 && oz == 0) ox = 1;
                // Structure audit 2026-09-23: every pod hung a block off its spine with nothing joining
                // them (the offset rounds to two). Each pod now hangs on a feed line of iron bars from
                // the spine's joint to its slime core, and an outer pod that the catwalk band would cut
                // into a lone glass cap or stub is left out.
                int qx = px + ox, qz = pz + oz;
                boolean outer = Math.sqrt((qx - mx) * (qx - mx) + (qz - mz) * (qz - mz)) >= 18.2;
                for (int dy = y0 + 3; dy < y1 - 4; dy += 5) {
                    int band = Math.floorMod(dy - (y0 + 8), 14);
                    if (outer && (band >= 12 || band <= 2)) { put(c, px, dy + 1, pz, 101, 0); continue; }
                    fill(c, qx, dy, qz, qx, dy + 2, qz, 95, ri == 0 ? 5 : 2);
                    put(c, qx, dy + 1, qz, 165, 0);
                    put(c, px, dy + 1, pz, 101, 0);
                    int ax = px, az = pz;
                    while (ax != qx || az != qz) {                        // the feed line, x first then z
                        if (ax != qx) ax += Integer.signum(qx - ax); else az += Integer.signum(qz - az);
                        if (ax != qx || az != qz) put(c, ax, dy + 1, az, 101, 0);
                    }
                    if (((dy + a) & 7) == 0) put(c, px, dy + 3, pz, 169, 0);
                }
            }
        }
        // Catwalks, and the ladder between them.
        // Structure audit 2026-09-23: the catwalks were paved with iron bars across their width, with
        // no rail over the drop and a one-block gap along the casing; their ladders hung on nothing,
        // ran up into the next catwalk's slab, and none came down from the surface riser or up from
        // the floor; their lamps floated over the walkway. Now each ring is a quartz-slab walkway out
        // to the casing with an iron-bar rail on the inner edge, one ladder on the casing runs from
        // the floor to the riser through a hatch in every catwalk, and the lamps are set in the wall.
        for (int dy = y0 + 8; dy < y1 - 6; dy += 14) {
            for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 18.2 || d > 20.6) continue;
                put(c, mx + dx, dy, mz + dz, 44, 7);
                put(c, mx + dx, dy + 1, mz + dz, d < 19.2 ? 101 : 0, 0);
                put(c, mx + dx, dy + 2, mz + dz, 0, 0);
            }
            for (int a = 0; a < 4; a++) {
                double ang = a * Math.PI / 2 + 0.4;
                put(c, mx + (int) Math.round(Math.cos(ang) * 21.3), dy + 2, mz + (int) Math.round(Math.sin(ang) * 21.3), 169, 0);
            }
        }
        fill(c, mx + 20, y0 + 1, mz, mx + 20, y1 - 1, mz, 65, 4);              // on the casing at mx + 21
        // The floor of the thing, where whatever it grows gets taken away.
        fill(c, mx - 12, y0 + 1, mz - 3, mx + 12, y0 + 1, mz + 3, 251, 7);
        fill(c, mx - 3, y0 + 1, mz - 12, mx + 3, y0 + 1, mz + 12, 251, 7);
        for (int i = 0; i < 5; i++) {
            double ang = i * 2 * Math.PI / 5;
            int qx = mx + (int) Math.round(Math.cos(ang) * 13), qz = mz + (int) Math.round(Math.sin(ang) * 13);
            fill(c, qx - 1, y0 + 1, qz - 1, qx + 1, y0 + 3, qz + 1, 95, 5);
            fill(c, qx, y0 + 1, qz, qx, y0 + 2, qz, 165, 0);
            if (i < 3) mob(c, qx + 2, y0 + 1, qz, i == 0 ? EntityType.SLIME : EntityType.SKELETON);
        }
        // Structure audit 2026-09-23: the floor said nothing about 'taken away'. Rails run along the
        // raised cross from the spine to the arm ends, drain grates close the arms, two tanks feed out
        // through hoppers under their cores, and each floor chest has a cauldron by it.
        for (int k = 4; k <= 11; k++) {
            put(c, mx + k, y0 + 2, mz, 66, 1); put(c, mx - k, y0 + 2, mz, 66, 1);
            put(c, mx, y0 + 2, mz + k, 66, 0); put(c, mx, y0 + 2, mz - k, 66, 0);
        }
        put(c, mx - 12, y0 + 2, mz, 167, 0); put(c, mx, y0 + 2, mz + 12, 167, 0); put(c, mx, y0 + 2, mz - 12, 167, 0);
        put(c, mx + 12, y0 + 1, mz, 154, 0);                              // feed-outs in the tank walls
        put(c, mx - 10, y0 + 1, mz + 8, 154, 0);
        put(c, mx - 11, y0 + 2, mz + 2, 118, 0); put(c, mx + 11, y0 + 2, mz - 2, 118, 0);
        mob(c, mx + 8, y0 + 1, mz + 8, EntityType.ENDERMAN);
        mob(c, mx - 8, y0 + 1, mz - 8, EntityType.ENDERMAN);
        mob(c, mx + 19, y0 + 9, mz + 2, EntityType.SKELETON);
        mob(c, mx - 19, y0 + 23, mz - 2, EntityType.SKELETON);
        graded(c, s, mx - 10, y0 + 1, mz + 2, r, MATRIX_LOOT, true);
        graded(c, s, mx + 10, y0 + 1, mz - 2, r, MATRIX_LOOT, false);
        graded(c, s, mx + 19, y0 + 9, mz - 2, r, MATRIX_LOOT, false);
        graded(c, s, mx - 19, y0 + 37, mz + 2, r, MATRIX_LOOT, true);
        // The surface shaft carries on the casing ladder's facing (the bore ladder already reaches it).
        shaft(c, t, mx + 20, mz, y1, y1 - 1, 3, 251, 15);
    }

    // == 8. The Hallowed Reach ===================================================
    // A cathedral on a plinth, built by people who were losing and knew it. Grand stair,
    // a nave you could fly a banner down, a gold thing at the end of it that is still
    // giving off light, and a sunken ring behind the chancel with the fog still standing
    // in the arch. Whatever the ring was for, it is still there.

    private static final int[][] SOULS_LOOT = {
        {266, 0, 13, 4, 12}, { 41, 0,  6, 1, 2}, {388, 0, 10, 2, 8}, {403, 0,  6, 1, 1},
        {384, 0,  9,  2, 8}, {322, 0,  7, 1, 2}, {283, 0,  8, 1, 1}, {314, 0,  7, 1, 1},
        {315, 0,  7,  1, 1}, {316, 0,  7, 1, 1}, {317, 0,  7, 1, 1}, {171, 11, 9, 4, 12},
    };

    private static void souls(Chunk c, Terrain t) {
        Site s = ground(t, c, SOULS_CELL, 0x534F554CL, 39, 39, 8, RANK_SOULS);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 38, z1 = z0 + 38;
        for (int dx = 0; dx <= 38; dx++) for (int dz = 0; dz <= 38; dz++)
            footing(c, s, x0 + dx, z0 + dz, 98, 0);
        // Plinth and the stair up to it.
        // Structure audit 2026-09-23: the grand stair ran north out of the footprint, where no chunk
        // builds this site, so only its top step was ever written (a floating row, or one buried in
        // the hill). The stair is now two flights cut into the front of the plinth, rising along the
        // facade from each side to the landing, which runs out to the plinth's edge.
        fill(c, x0, y0, z0, x1, y0 + 2, z1, 98, 0);
        fill(c, x0 + 1, y0 + 3, z0 + 1, x1 - 1, y0 + 3, z1 - 1, 98, 0);
        for (int i = 0; i < 4; i++) {
            if (i < 3) fill(c, x0 + 10 + i, y0 + i + 1, z0, x0 + 10 + i, y0 + 3, z0, 0, 0);
            put(c, x0 + 10 + i, y0 + i, z0, 109, 0);
            if (i < 3) fill(c, x0 + 28 - i, y0 + i + 1, z0, x0 + 28 - i, y0 + 3, z0, 0, 0);
            put(c, x0 + 28 - i, y0 + i, z0, 109, 1);
        }
        fill(c, x0 + 14, y0 + 3, z0, x0 + 24, y0 + 3, z0, 98, 0);
        fill(c, x0 + 15, y0 + 3, z0 + 1, x0 + 23, y0 + 3, z0 + 3, 98, 0);
        // Where the land stands over the plinth the cut is faced with a stone-brick revetment.
        for (int dx = -1; dx <= 39; dx++) for (int dz = -1; dz <= 39; dz++) {
            if (dx != -1 && dx != 39 && dz != -1 && dz != 39) continue;
            for (int y = y0 + 4; y <= y0 + 16 && ground(c, x0 + dx, y, z0 + dz); y++) put(c, x0 + dx, y, z0 + dz, 98, 0);
        }
        // The shell: aisles either side of a nave, arcades, clerestory.
        // Structure audit 2026-09-23: the nave's air ran one course too high and took the roof off
        // (aisles open to the sky, the lantern strip hovering); the carpet was laid into the floor, so
        // the spawner and pews hung over a trench; the arcade corbels were upright steps; the nave was
        // dark at night. The roof stays, the runner lies on the quartz, the corbels are upside down,
        // and a glowstone course is set in every pier.
        int ny = y0 + 4;
        shell(c, x0 + 6, ny, z0 + 2, x0 + 32, ny + 17, z0 + 32, 98, 0);
        fill(c, x0 + 7, ny, z0 + 3, x0 + 31, ny + 16, z0 + 31, 0, 0);
        fill(c, x0 + 7, ny, z0 + 3, x0 + 31, ny, z0 + 31, 155, 0);
        fill(c, x0 + 15, ny + 1, z0 + 3, x0 + 23, ny + 1, z0 + 25, 171, 14);
        for (int dz = 5; dz <= 29; dz += 4) {                        // the two arcades
            for (int side = 0; side < 2; side++) {
                int px = side == 0 ? x0 + 12 : x0 + 26;
                fill(c, px, ny + 1, z0 + dz, px, ny + 11, z0 + dz, 98, 3);
                put(c, px, ny + 6, z0 + dz, 89, 0);
                put(c, px, ny + 12, z0 + dz, 44, 5);
                fill(c, px, ny + 13, z0 + dz - 1, px, ny + 13, z0 + dz + 1, 98, 0);
                fill(c, px, ny + 3, z0 + dz - 1, px, ny + 3, z0 + dz + 1, 109, side == 0 ? 4 : 5);
            }
            fill(c, x0 + 6, ny + 8, z0 + dz - 1, x0 + 6, ny + 11, z0 + dz + 1, 95, 0);
            fill(c, x0 + 32, ny + 8, z0 + dz - 1, x0 + 32, ny + 11, z0 + dz + 1, 95, 0);
        }
        fill(c, x0 + 12, ny + 14, z0 + 3, x0 + 26, ny + 14, z0 + 31, 44, 5);   // the vault
        fill(c, x0 + 15, ny + 15, z0 + 3, x0 + 23, ny + 15, z0 + 31, 98, 3);
        fill(c, x0 + 17, ny + 17, z0 + 3, x0 + 21, ny + 17, z0 + 31, 89, 0);
        fill(c, x0 + 15, ny, z0 + 2, x0 + 23, ny + 5, z0 + 2, 0, 0);           // the west door
        // Two towers at the front, because they always built two.
        // Structure audit 2026-09-23: neither tower had a door; each ladder ran 25 rungs into open air
        // under the lamp with the skeleton spawner floating mid-shaft, and the east tower's windows
        // looked into the nave. Each now opens off its aisle (a step down to the tower floor), the
        // ladder climbs to a belfry floor at the top window, where the spawner stands, and the east
        // tower's windows are in its outer wall.
        for (int side = 0; side < 2; side++) {
            int tx = side == 0 ? x0 + 3 : x0 + 31;
            shell(c, tx, y0 + 3, z0 + 2, tx + 4, y0 + 30, z0 + 6, 98, 0);
            fill(c, tx + 1, y0 + 4, z0 + 3, tx + 3, y0 + 29, z0 + 5, 0, 0);
            fill(c, tx + 1, y0 + 25, z0 + 3, tx + 3, y0 + 25, z0 + 5, 98, 0);
            fill(c, tx + 1, y0 + 4, z0 + 3, tx + 1, y0 + 25, z0 + 3, 65, 3);
            for (int dy = 10; dy <= 26; dy += 8)
                fill(c, side == 0 ? tx : tx + 4, y0 + dy, z0 + 4, side == 0 ? tx : tx + 4, y0 + dy + 2, z0 + 4, 95, 0);
            fill(c, side == 0 ? tx + 4 : tx, y0 + 5, z0 + 4, side == 0 ? tx + 4 : tx, y0 + 6, z0 + 4, 0, 0);
            fill(c, tx - 1, y0 + 31, z0 + 1, tx + 5, y0 + 31, z0 + 7, 44, 5);
            fill(c, tx + 1, y0 + 32, z0 + 3, tx + 3, y0 + 34, z0 + 5, 98, 3);
            put(c, tx + 2, y0 + 29, z0 + 4, 89, 0);
            mob(c, tx + 3, y0 + 26, z0 + 5, EntityType.SKELETON);
        }
        // The chancel: gold, a fenced altar, and a fog gate across the arch.
        fill(c, x0 + 16, ny + 1, z0 + 26, x0 + 22, ny + 6, z0 + 30, 251, 4);   // gilding: yellow concrete, was gold block
        fill(c, x0 + 17, ny + 1, z0 + 27, x0 + 21, ny + 5, z0 + 29, 0, 0);
        fill(c, x0 + 18, ny + 1, z0 + 28, x0 + 20, ny + 3, z0 + 28, 89, 0);
        fill(c, x0 + 17, ny + 1, z0 + 26, x0 + 21, ny + 2, z0 + 26, 85, 0);
        // A gate in the altar rail (structure audit 2026-09-23): the two chests were shut in the box
        // behind a solid rail; they are reached round the glowing core.
        put(c, x0 + 19, ny + 1, z0 + 26, 107, 0);
        put(c, x0 + 19, ny + 2, z0 + 26, 0, 0);
        fill(c, x0 + 15, ny + 1, z0 + 24, x0 + 23, ny + 5, z0 + 24, 95, 0);
        fill(c, x0 + 18, ny + 1, z0 + 24, x0 + 20, ny + 4, z0 + 24, 0, 0);
        fill(c, x0 + 18, ny + 1, z0 + 24, x0 + 20, ny + 1, z0 + 24, 171, 14);
        graded(c, s, x0 + 17, ny + 1, z0 + 29, r, SOULS_LOOT, false);
        graded(c, s, x0 + 21, ny + 1, z0 + 29, r, SOULS_LOOT, true);
        // Banners down the nave, and the congregation. Structure audit 2026-09-23: the banners hung in
        // mid-air a block inside the arcade and the congregation was four lone slabs. The banners now
        // hang in the arches from the lintels, and pews in rows either side of the processional aisle
        // face the altar.
        for (int dz = 6; dz <= 22; dz += 4) {
            fill(c, x0 + 12, ny + 9, z0 + dz, x0 + 12, ny + 12, z0 + dz, 35, 14);
            fill(c, x0 + 26, ny + 9, z0 + dz, x0 + 26, ny + 12, z0 + dz, 35, 14);
        }
        for (int dz = 6; dz <= 22; dz += 2) {
            fill(c, x0 + 15, ny + 1, z0 + dz, x0 + 17, ny + 1, z0 + dz, 109, 3);
            fill(c, x0 + 21, ny + 1, z0 + dz, x0 + 23, ny + 1, z0 + dz, 109, 3);
        }
        mob(c, x0 + 19, ny + 1, z0 + 10, EntityType.ZOMBIE);
        mob(c, x0 + 10, ny + 1, z0 + 18, EntityType.SKELETON);
        mob(c, x0 + 28, ny + 1, z0 + 14, EntityType.SKELETON);
        // The ring behind the chancel, sunk into the plinth.
        int rx = x0 + 19, rz = z0 + 35;
        fill(c, rx - 8, y0 - 6, rz - 5, rx + 8, y0 + 3, rz + 3, 0, 0);
        shell(c, rx - 9, y0 - 7, rz - 6, rx + 9, y0 + 4, rz + 4, 98, 1);
        fill(c, rx - 8, y0 - 6, rz - 5, rx + 8, y0 - 6, rz + 3, 98, 2);
        // Structure audit 2026-09-23: the ring's roof capped its entry under the reliquary and the
        // ladder hung the wrong way off air, so the ring was sealed. The way down is now a hatch in the
        // chancel floor beside the reliquary, with a ladder on the ring's north wall to its floor.
        fill(c, x0 + 24, y0 - 5, rz - 5, x0 + 24, y0 + 4, rz - 5, 65, 3);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int px = rx + (int) Math.round(Math.cos(ang) * 6), pz = rz - 1 + (int) Math.round(Math.sin(ang) * 3);
            fill(c, px, y0 - 5, pz, px, y0 + 3, pz, 98, 3);
            if ((a & 1) == 0) put(c, px, y0 - 2, pz, 89, 0);
        }
        mob(c, rx, y0 - 5, rz - 1, EntityType.WITHER_SKELETON);
        mob(c, rx - 5, y0 - 5, rz + 1, EntityType.WITHER_SKELETON);
        mob(c, rx + 5, y0 - 5, rz + 1, EntityType.ZOMBIE);
        graded(c, s, rx, y0 - 5, rz + 1, r, SOULS_LOOT, true);          // was in the base of a pillar
    }

    // == 9. Phobos Anomaly =======================================================
    // The first one. A tech base with a nukage moat, a zigzag of green corridors, three
    // coloured doors that only open one way, a lift, and a room shaped like a star that
    // is obviously a trap and that everybody walks into anyway.

    private static final int[][] DOOM1_LOOT = {
        {289, 0, 15,  8, 24}, {262, 0, 14, 10, 30}, {385, 0, 11, 3,  9}, {259, 0,  9, 1, 1},
        {302, 0, 12, 4, 12}, {306, 0,  8,  1,  1}, {309, 0,  8, 1,  1}, {267, 0,  8, 1, 1},
        {320, 0, 10,  2,  6}, {303, 0, 7, 2, 6}, {348, 0, 10, 4, 12}, { 89, 0,  9, 2, 6},
    };

    private static void doomOne(Chunk c, Terrain t) {
        Site s = deep(t, c, DOOM1_CELL, 0x444F4F4D31L, 37, 31, 12, RANK_DOOM1);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 36, z1 = z0 + 30, y1 = s.y + 11;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);
        shell(c, x0, y0, z0, x1, y1, z1, 251, 13);
        fill(c, x0, y0, z0, x1, y0, z1, 159, 13);
        fill(c, x0, y1, z0, x1, y1, z1, 251, 7);
        // The nukage moat and the walkway across it.
        // Structure audit 2026-09-23: still lava (a flowing cell drained away in one capture); the
        // walkway, a dead-end pier that stopped short over the lava, now runs to a railed landing at the
        // west wall, where the secret stands (it used to be a sealed stone box in the hall corridor, in
        // front of the computer wall and cut into the red room): a computer-wall panel with a lamp and
        // an iron door opened by a button, and the box behind it.
        fill(c, x0 + 1, y0, z0 + 1, x0 + 11, y0, z0 + 29, 11, 0);
        fill(c, x0 + 1, y0 - 1, z0 + 1, x0 + 11, y0 - 1, z0 + 29, 49, 0);
        fill(c, x0 + 4, y0 + 1, z0 + 14, x0 + 12, y0 + 1, z0 + 16, 155, 0);
        fill(c, x0 + 4, y0 + 2, z0 + 13, x0 + 12, y0 + 2, z0 + 13, 101, 0);
        fill(c, x0 + 4, y0 + 2, z0 + 17, x0 + 12, y0 + 2, z0 + 17, 101, 0);
        fill(c, x0 + 1, y0 + 1, z0 + 12, x0 + 4, y0 + 1, z0 + 18, 155, 0);
        fill(c, x0 + 1, y0 + 2, z0 + 12, x0 + 4, y0 + 2, z0 + 12, 101, 0);
        fill(c, x0 + 1, y0 + 2, z0 + 18, x0 + 4, y0 + 2, z0 + 18, 101, 0);
        fill(c, x0 + 1, y0 + 2, z0 + 14, x0 + 2, y0 + 4, z0 + 16, 1, 0);
        fill(c, x0 + 2, y0 + 2, z0 + 14, x0 + 2, y0 + 4, z0 + 14, 251, 13);
        fill(c, x0 + 2, y0 + 2, z0 + 16, x0 + 2, y0 + 4, z0 + 16, 251, 13);
        fill(c, x0 + 1, y0 + 2, z0 + 15, x0 + 1, y0 + 3, z0 + 15, 0, 0);
        put(c, x0 + 2, y0 + 4, z0 + 15, 89, 0);
        put(c, x0 + 2, y0 + 2, z0 + 15, 71, 0); put(c, x0 + 2, y0 + 3, z0 + 15, 71, 8);
        put(c, x0 + 3, y0 + 3, z0 + 14, 77, 1);
        // Computer walls down the main corridor, with the doorway onto the walkway in them (the only
        // gap, at z0 + 11, was filled by the wall's own lights and walled off behind by the secret box).
        fill(c, x0 + 13, y0 + 1, z0 + 1, x0 + 13, y0 + 5, z1 - 1, 251, 13);
        for (int dz = 2; dz <= 28; dz += 3) {
            put(c, x0 + 13, y0 + 2, z0 + dz, 251, 14);                // red concrete, was redstone block
            put(c, x0 + 13, y0 + 4, z0 + dz, 89, 0);
        }
        fill(c, x0 + 13, y0 + 1, z0 + 14, x0 + 13, y0 + 3, z0 + 16, 0, 0);
        // Three keyed rooms off the spine, each sealed with its own colour.
        int[][] rooms = {{x0 + 16, z0 + 2, 14}, {x0 + 16, z0 + 12, 11}, {x0 + 16, z0 + 22, 4}};
        for (int i = 0; i < 3; i++) {
            int bx = rooms[i][0], bz = rooms[i][1], col = rooms[i][2];
            shell(c, bx, y0 + 1, bz, bx + 10, y0 + 6, bz + 7, 251, 7);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 9, y0 + 5, bz + 6, 0, 0);
            // Structure audit 2026-09-23: the 'keyed door' was a solid plug of its colour, so the red and
            // yellow rooms were sealed. The colour is now the door's frame, round a pair of iron doors
            // with a button beside each on both sides.
            fill(c, bx, y0 + 1, bz + 2, bx, y0 + 3, bz + 5, 251, col);
            put(c, bx, y0 + 1, bz + 3, 71, 0); put(c, bx, y0 + 2, bz + 3, 71, 8);
            put(c, bx, y0 + 1, bz + 4, 71, 0); put(c, bx, y0 + 2, bz + 4, 71, 9);
            put(c, bx - 1, y0 + 2, bz + 2, 77, 2); put(c, bx - 1, y0 + 2, bz + 5, 77, 2);
            put(c, bx + 1, y0 + 2, bz + 2, 77, 1); put(c, bx + 1, y0 + 2, bz + 5, 77, 1);
            put(c, bx + 5, y0 + 5, bz + 3, 89, 0);
            fill(c, bx + 2, y0 + 1, bz + 2, bx + 3, y0 + 2, bz + 2, 43, 8);   // console: 43:8, was iron block
            put(c, bx + 2, y0 + 3, bz + 2, 69, 5);
            graded(c, s, bx + 8, y0 + 1, bz + 5, r, DOOM1_LOOT, i == 2);
            mob(c, bx + 5, y0 + 1, bz + 4, i == 1 ? EntityType.SKELETON : EntityType.ZOMBIE);
        }
        // The star room, and what is standing in the middle of it.
        // Structure audit 2026-09-23: the star's carve ran on into the blue room, taking its east wall
        // and its chest; it now stops at that wall, the two spawners on its west points stand inside it,
        // and its lamp hangs from the ceiling on a cable instead of floating.
        int sx = x0 + 28, sz = z0 + 15;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            if (sx + dx < x0 + 27) continue;
            double ang = Math.atan2(dz, dx), d = Math.sqrt(dx * dx + dz * dz);
            double edge = 4.2 + 2.6 * Math.abs(Math.cos(ang * 2.5));
            if (d > edge) continue;
            fill(c, sx + dx, y0 + 1, sz + dz, sx + dx, y0 + 7, sz + dz, 0, 0);
            put(c, sx + dx, y0, sz + dz, d > edge - 1.1 ? 251 : 159, d > edge - 1.1 ? 13 : 14);
        }
        fill(c, sx - 1, y0 + 1, sz - 1, sx + 1, y0 + 1, sz + 1, 251, 14);   // red concrete, was redstone block
        put(c, sx, y0 + 6, sz, 89, 0);
        fill(c, sx, y0 + 7, sz, sx, y1 - 1, sz, 101, 0);
        mob(c, sx - 1, y0 + 1, sz - 3, EntityType.ZOMBIE);
        mob(c, sx + 3, y0 + 1, sz + 3, EntityType.ZOMBIE);
        mob(c, sx + 3, y0 + 1, sz - 3, EntityType.SKELETON);
        mob(c, sx - 1, y0 + 1, sz + 3, EntityType.SKELETON);
        graded(c, s, sx, y0 + 1, sz - 4, r, DOOM1_LOOT, true);
        // The lift out, and the secret behind the computer wall.
        // Structure audit 2026-09-23: the lift ladder hung on hall air; it now runs up a concrete lift
        // column, and the surface shaft keeps its facing. The hall was near-black away from the computer
        // wall: lamps are set in the shell and the key rooms' walls along the aisles, over red
        // indicator blocks on the east wall.
        fill(c, x0 + 34, y0 + 1, z0 + 2, x0 + 35, y0 + 10, z0 + 3, 0, 0);
        fill(c, x0 + 33, y0 + 1, z0 + 2, x0 + 33, y0 + 10, z0 + 2, 251, 7);
        fill(c, x0 + 34, y0 + 1, z0 + 2, x0 + 34, y0 + 10, z0 + 2, 65, 5);
        shaft(c, t, x0 + 34, z0 + 2, y1, y1 - 1, 2, 251, 7);
        for (int dz = 5; dz <= 25; dz += 6) { put(c, x1, y0 + 3, z0 + dz, 89, 0); put(c, x1, y0 + 2, z0 + dz, 251, 14); }
        put(c, x0 + 24, y0 + 3, z0, 89, 0); put(c, x0 + 31, y0 + 3, z0, 89, 0);
        put(c, x0 + 24, y0 + 3, z1, 89, 0); put(c, x0 + 31, y0 + 3, z1, 89, 0);
        put(c, x0 + 21, y0 + 3, z0 + 9, 89, 0); put(c, x0 + 21, y0 + 3, z0 + 22, 89, 0);
        graded(c, s, x0 + 1, y0 + 2, z0 + 15, r, DOOM1_LOOT, true);
    }

    // == 10. The Gate ============================================================
    // The second one, where it stopped being a base. A city block with the buildings
    // still standing and the rock between them turned to something else: a courtyard
    // paved in a mark somebody cut into the floor, cages hung off the walls, and heat
    // coming up through the cracks.

    private static final int[][] DOOM2_LOOT = {
        {369, 0, 12, 2,  6}, {377, 0, 10, 2,  6}, {378, 0, 12, 4, 12}, {372, 0, 11, 3,  9},
        { 87, 0, 13, 6, 18}, {112, 0, 11, 4, 12}, {405, 0, 10, 4, 12}, {370, 0,  7, 1,  2},
        {364, 0,  9, 2,  6}, {385, 0, 11, 3,  9}, {266, 0, 10, 3,  9}, {403, 0,  4, 1,  1},
    };

    private static void doomTwo(Chunk c, Terrain t) {
        Site s = deep(t, c, DOOM2_CELL, 0x444F4F4D32L, 35, 33, 16, RANK_DOOM2);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 34, z1 = z0 + 32, y1 = s.y + 15;
        fill(c, x0, y0, z0, x1, y1, z1, 0, 0);
        shell(c, x0, y0, z0, x1, y1, z1, 45, 0);
        fill(c, x0, y0, z0, x1, y0, z1, 4, 0);
        fill(c, x0, y1, z0, x1, y1, z1, 87, 0);
        // Four buildings round a courtyard, brick going to nether brick as it gets worse.
        int[][] blocks = {{x0 + 2, z0 + 2}, {x0 + 23, z0 + 2}, {x0 + 2, z0 + 22}, {x0 + 23, z0 + 22}};
        for (int i = 0; i < 4; i++) {
            int bx = blocks[i][0], bz = blocks[i][1];
            shell(c, bx, y0 + 1, bz, bx + 9, y0 + 9, bz + 8, i < 2 ? 45 : 112, 0);
            fill(c, bx + 1, y0 + 1, bz + 1, bx + 8, y0 + 8, bz + 7, 0, 0);
            fill(c, bx + 1, y0 + 4, bz + 1, bx + 8, y0 + 4, bz + 7, 44, i < 2 ? 4 : 6);
            fill(c, bx + 4, y0 + 1, bz + (i < 2 ? 8 : 0), bx + 5, y0 + 3, bz + (i < 2 ? 8 : 0), 0, 0);
            for (int dy = 2; dy <= 7; dy += 5)
                fill(c, bx, y0 + dy, bz + 3, bx, y0 + dy + 1, bz + 5, 0, 0);
            put(c, bx + 4, y0 + 3, bz + 4, 89, 0);
            // The brazier stands on a nether brick post on the upper floor. It used to hang in the air,
            // so its netherrack was pruned as a floater and the fire went out (structure audit 2026-09-22).
            put(c, bx + 4, y0 + 5, bz + 4, 113, 0);
            put(c, bx + 4, y0 + 6, bz + 4, 87, 0);
            put(c, bx + 4, y0 + 7, bz + 4, 51, 0);                    // after the netherrack, or it goes out
            // Structure audit 2026-09-23: no stair or ladder reached any upper floor (a chest and a
            // spawner up there in every building), and each room held only its chest, spawner and
            // light. A masonry stair now climbs the east wall into each upper floor, and the rooms say
            // what they were: a shop and an office with flats over them in brick, the same rooms
            // further gone in the nether-brick pair.
            int stair = i < 2 ? 108 : 114, brick = i < 2 ? 45 : 112;
            fill(c, bx + 8, y0 + 4, bz + 1, bx + 8, y0 + 4, bz + 3, 0, 0);
            for (int k = 0; k < 4; k++) {                                   // rising south onto the floor
                if (k > 0) fill(c, bx + 8, y0 + 1, bz + 1 + k, bx + 8, y0 + k, bz + 1 + k, brick, 0);
                put(c, bx + 8, y0 + 1 + k, bz + 1 + k, stair, 2);
            }
            if (i == 0) {                                                  // the shop
                fill(c, bx + 2, y0 + 1, bz + 6, bx + 3, y0 + 1, bz + 6, 45, 0);
                put(c, bx + 1, y0 + 1, bz + 7, 118, 0);
                put(c, bx + 2, y0 + 1, bz + 7, 61, 2);
                fill(c, bx + 1, y0 + 1, bz + 1, bx + 1, y0 + 2, bz + 2, 47, 0);
            } else if (i == 1) {                                           // the office
                put(c, bx + 2, y0 + 1, bz + 6, 58, 0); put(c, bx + 3, y0 + 1, bz + 6, 113, 0);
                put(c, bx + 2, y0 + 1, bz + 4, 58, 0); put(c, bx + 3, y0 + 1, bz + 4, 113, 0);
                fill(c, bx + 1, y0 + 1, bz + 1, bx + 1, y0 + 2, bz + 2, 47, 0);
            } else {                                                       // gone over
                fill(c, bx + 2, y0, bz + 5, bx + 3, y0, bz + 6, 88, 0);
                fill(c, bx + 2, y0 + 1, bz + 5, bx + 3, y0 + 1, bz + 6, 115, 3);
                put(c, bx + 1, y0 + 1, bz + 7, 216, 0);
                put(c, bx + 2, y0 + 1, bz + 1, 144, 1);
                put(c, bx + 6, y0 + 1, bz + 7, 87, 0);
            }
            if (i < 2) {                                                   // the flat upstairs
                put(c, bx + 2, y0 + 5, bz + 6, 26, 0); put(c, bx + 2, y0 + 5, bz + 7, 26, 8);
                put(c, bx + 4, y0 + 5, bz + 7, 85, 0); put(c, bx + 4, y0 + 6, bz + 7, 72, 0);
            } else {
                put(c, bx + 2, y0 + 5, bz + 6, 216, 0); put(c, bx + 2, y0 + 6, bz + 6, 144, 1);
                put(c, bx + 5, y0 + 5, bz + 7, 87, 0);
            }
            graded(c, s, bx + 7, y0 + 1, bz + 6, r, DOOM2_LOOT, i == 3);
            graded(c, s, bx + 2, y0 + 5, bz + 2, r, DOOM2_LOOT, false);
            mob(c, bx + 4, y0 + 1, bz + 2, i % 2 == 0 ? EntityType.BLAZE : EntityType.MAGMA_CUBE);
            mob(c, bx + 6, y0 + 5, bz + 5, EntityType.PIG_ZOMBIE);
        }
        // The courtyard, and the mark cut into it.
        int mx = x0 + 17, mz = z0 + 16;
        fill(c, x0 + 12, y0, z0 + 10, x0 + 22, y0, z0 + 22, 215, 0);
        for (int a = 0; a < 5; a++) {
            double a1 = a * 2 * Math.PI / 5 - Math.PI / 2, a2 = (a + 2) * 2 * Math.PI / 5 - Math.PI / 2;
            int ax = mx + (int) Math.round(Math.cos(a1) * 5), az = mz + (int) Math.round(Math.sin(a1) * 5);
            int bx2 = mx + (int) Math.round(Math.cos(a2) * 5), bz2 = mz + (int) Math.round(Math.sin(a2) * 5);
            for (int step = 0; step <= 12; step++) {
                int px = ax + (bx2 - ax) * step / 12, pz = az + (bz2 - az) * step / 12;
                put(c, px, y0, pz, 251, 14);                          // pentagram: red concrete, was redstone
            }
        }
        fill(c, mx - 1, y0 - 3, mz - 1, mx + 1, y0 - 1, mz + 1, 10, 0);
        fill(c, mx - 1, y0, mz - 1, mx + 1, y0, mz + 1, 213, 0);
        // Cages on chains off the ceiling, with what is left in them.
        // Structure audit 2026-09-23: the cages and cracks were drawn after the chests, whose draws
        // happen only in the chunk holding each chest, so every chunk put its cages somewhere else --
        // clusters, overlaps, cages cut open at chunk borders and driven through buildings. They now
        // come from their own generator (the same in every chunk), hang only over the streets, apart,
        // and the cracks never cut a wall; the alley lamps are set in the wall they floated beside.
        Random lay = new Random(s.seed ^ 0x4341474553L);
        int[][] hung = new int[6][];
        for (int i = 0; i < 6; i++) {
            int gx = 0, gz = 0;
            for (int tries = 0; tries < 24; tries++) {
                if (lay.nextBoolean()) { gx = x0 + 15 + lay.nextInt(5); gz = z0 + 6 + lay.nextInt(21); }
                else { gx = x0 + 6 + lay.nextInt(23); gz = z0 + 14 + lay.nextInt(5); }
                boolean apart = true;
                for (int j = 0; j < i; j++) if (Math.abs(hung[j][0] - gx) < 4 && Math.abs(hung[j][1] - gz) < 4) apart = false;
                if (apart) break;
            }
            hung[i] = new int[]{gx, gz};
            fill(c, gx, y0 + 10, gz, gx, y1 - 1, gz, 101, 0);
            shell(c, gx - 1, y0 + 7, gz - 1, gx + 1, y0 + 10, gz + 1, 101, 0);
            fill(c, gx, y0 + 8, gz, gx, y0 + 9, gz, 0, 0);
            put(c, gx, y0 + 8, gz, 216, 0);
        }
        // Heat coming up out of the floor.
        for (int i = 0; i < 26; i++) {
            int fx = x0 + 2 + lay.nextInt(31), fz = z0 + 2 + lay.nextInt(29);
            boolean flame = lay.nextInt(3) == 0;
            Block over = blockAt(c, fx, y0 + 1, fz);
            if (over == null || over.getTypeId() != 0) continue;
            put(c, fx, y0, fz, 87, 0);
            if (flame) put(c, fx, y0 + 1, fz, 51, 0);
        }
        for (int dy = 3; dy <= 13; dy += 5) {
            put(c, x0, y0 + dy, z0 + 16, 89, 0);
            put(c, x1, y0 + dy, z0 + 16, 89, 0);
        }
        mob(c, mx, y0 + 1, mz - 6, EntityType.BLAZE);
        mob(c, mx, y0 + 1, mz + 6, EntityType.MAGMA_CUBE);
        mob(c, mx - 6, y0 + 1, mz, EntityType.WITHER_SKELETON);
        graded(c, s, mx - 4, y0 + 1, mz - 4, r, DOOM2_LOOT, true);
        graded(c, s, mx + 4, y0 + 1, mz + 4, r, DOOM2_LOOT, false);
        // The riser came down on building 0's roof, nine blocks over the street with no way down or
        // back (structure audit 2026-09-23). It now comes down the alley corner, on the outer wall,
        // to the street.
        riser(c, t, x0 + 1, z0 + 1, y1, 45, 0);
    }

    // == 11. Delta Labs ==========================================================
    // The third one, and the worst lit. Corridors a metre and a half wide with the
    // ceiling panels hanging off, storage bays full of crates, one light in six still
    // working, and a room at the end with a ring of stone in it that is definitely off.

    private static final int[][] DOOM3_LOOT = {
        {348, 0, 13, 4, 14}, { 89, 0, 10, 2,  6}, { 50, 0, 14, 8, 24}, {385, 0, 10, 2, 6},
        {367, 0, 13, 6, 18}, {263, 0, 12, 6, 18}, {380, 0, 11, 3, 9}, {264, 0,  7, 1, 2},
        {310, 0,  4, 1,  1}, {313, 0,  4, 1,  1}, {402, 0, 8, 2, 6}, {379, 0, 4, 1, 1},
    };

    /** An open cell under something solid, or in a corner of two solid sides: somewhere a web hangs (audit 2026-09-23). */
    private static boolean webSpot(Chunk c, int wx, int wy, int wz) {
        Block b = blockAt(c, wx, wy, wz), up = blockAt(c, wx, wy + 1, wz);
        if (b == null || b.getTypeId() != 0) return false;
        if (up != null && up.getType().isSolid()) return true;
        int n = 0;
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : side) {
            Block o = blockAt(c, wx + d[0], wy, wz + d[1]);
            if (o != null && o.getType().isSolid()) n++;
        }
        return n >= 2;
    }

    private static void doomThree(Chunk c, Terrain t) {
        Site s = deep(t, c, DOOM3_CELL, 0x444F4F4D33L, 39, 29, 11, RANK_DOOM3);
        if (s == null) return;
        Random r = new Random(s.seed);
        int x0 = s.x, z0 = s.z, y0 = s.y, x1 = x0 + 38, z1 = z0 + 28, y1 = s.y + 10;
        fill(c, x0, y0, z0, x1, y1, z1, 251, 7);                     // solid, then cut into
        fill(c, x0, y0, z0, x1, y0, z1, 251, 15);
        // A spine corridor with four bays off it, all of it two wide and four high.
        fill(c, x0 + 2, y0 + 1, z0 + 13, x1 - 2, y0 + 4, z0 + 15, 0, 0);
        // Structure audit 2026-09-23: the dead panels are unlit lamps now (they read as dead fittings,
        // not black blocks), with floor grates and wall vents between the ribs.
        for (int dx = 4; dx <= 34; dx += 6) {
            put(c, x0 + dx, y0 + 4, z0 + 14, ((dx / 6) & 1) == 0 ? 89 : 123, 0);
            // Bay frames: smooth stone double slab 43:8 (was iron block).
            fill(c, x0 + dx, y0 + 1, z0 + 12, x0 + dx, y0 + 4, z0 + 12, 43, 8);
            fill(c, x0 + dx, y0 + 1, z0 + 16, x0 + dx, y0 + 4, z0 + 16, 43, 8);
            if (dx <= 16) {
                put(c, x0 + dx + 3, y0 + 1, z0 + 14, 167, 0);
                put(c, x0 + dx + 3, y0 + 3, z0 + 12, 101, 0);
                put(c, x0 + dx + 3, y0 + 3, z0 + 16, 101, 0);
            }
        }
        int[][] bays = {{x0 + 4, z0 + 2, 0}, {x0 + 20, z0 + 2, 0}, {x0 + 4, z0 + 18, 1}, {x0 + 20, z0 + 18, 1}};
        for (int i = 0; i < 4; i++) {
            int bx = bays[i][0], bz = bays[i][1];
            fill(c, bx, y0 + 1, bz, bx + 12, y0 + 5, bz + 8, 0, 0);
            fill(c, bx, y0 + 1, bz, bx + 12, y0 + 1, bz + 8, 251, 8);
            fill(c, bx + 6, y0 + 1, bays[i][2] == 0 ? bz + 9 : bz - 2, bx + 7, y0 + 4, bays[i][2] == 0 ? bz + 11 : bz - 1, 0, 0);
            for (int dx = 1; dx <= 11; dx += 3) for (int dz = 1; dz <= 7; dz += 3) {
                if (((dx + dz + i) & 3) == 0) continue;
                fill(c, bx + dx, y0 + 2, bz + dz, bx + dx + 1, y0 + 3, bz + dz + 1, 251, i == 1 ? 1 : 8);
            }
            put(c, bx + 6, y0 + 5, bz + 4, ((i & 1) == 0) ? 89 : 123, 0);
            graded(c, s, bx + 2, y0 + 2, bz + 6, r, DOOM3_LOOT, i == 0);
            mob(c, bx + 9, y0 + 2, bz + 2, i < 2 ? EntityType.CAVE_SPIDER : EntityType.ZOMBIE);
        }
        // The ring at the end, on a dais, with the floor around it gone wrong.
        int px = x1 - 8, pz = z0 + 14;
        fill(c, px - 6, y0 + 1, pz - 7, px + 5, y0 + 8, pz + 7, 0, 0);
        fill(c, px - 6, y0 + 1, pz - 7, px + 5, y0 + 1, pz + 7, 87, 0);
        fill(c, px - 3, y0 + 1, pz - 4, px + 2, y0 + 1, pz + 4, 49, 0);
        // Structure audit 2026-09-23: the ellipse was measured from the ring's foot, so only its upper
        // arch was ever set (the 2026-09-22 pass stood it on two jambs). It is now centred on the ring:
        // a closed ring of obsidian from the dais to the ceiling.
        for (int dy = 0; dy <= 6; dy++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt((dy - 3) * (dy - 3) + dz * dz * 0.6);
            if (d > 3.6 || d < 2.2) continue;
            put(c, px, y0 + 2 + dy, pz + dz, 49, 0);
        }
        // The gate burns along its sill: fire on a netherrack threshold inside the ring's foot, and
        // fires on the netherrack round the dais where the floor went wrong. A sheet of fire hung in the
        // ring with nothing under it went out as soon as it was placed (structure audit 2026-09-22).
        fill(c, px, y0 + 2, pz - 1, px, y0 + 2, pz + 1, 87, 0);
        fill(c, px, y0 + 3, pz - 1, px, y0 + 3, pz + 1, 51, 0);
        put(c, px - 2, y0 + 2, pz - 6, 51, 0); put(c, px - 2, y0 + 2, pz + 6, 51, 0);
        put(c, px + 3, y0 + 2, pz - 5, 51, 0); put(c, px + 3, y0 + 2, pz + 5, 51, 0);
        put(c, px - 5, y0 + 8, pz, 89, 0);                                  // flush under the ceiling (it hung a block below)
        // Structure audit 2026-09-23: the chamber was carved over the east halves of bays 1 and 3 (their
        // walls gone, crates cut, bay 3's spawner lost). It has its own walls now, with a doorway into
        // each of the two bays, the corridor's dead-end stub past it is closed, bay 3's spawner is back
        // in its aisle, and a UAC control desk faces the ring.
        fill(c, px - 6, y0 + 2, pz - 7, px + 5, y0 + 8, pz - 7, 251, 7);
        fill(c, px - 6, y0 + 2, pz + 7, px + 5, y0 + 8, pz + 7, 251, 7);
        fill(c, px - 6, y0 + 2, pz - 6, px - 6, y0 + 8, pz - 4, 251, 7);
        fill(c, px - 6, y0 + 2, pz + 4, px - 6, y0 + 8, pz + 6, 251, 7);
        fill(c, px - 4, y0 + 2, pz - 7, px - 3, y0 + 4, pz - 7, 0, 0);
        fill(c, px - 4, y0 + 2, pz + 7, px - 3, y0 + 4, pz + 7, 0, 0);
        fill(c, x1 - 2, y0 + 1, z0 + 13, x1 - 2, y0 + 4, z0 + 15, 251, 7);
        mob(c, x0 + 29, y0 + 2, z0 + 24, EntityType.ZOMBIE);
        fill(c, px - 4, y0 + 2, pz - 1, px - 4, y0 + 2, pz + 1, 251, 8);
        fill(c, px - 4, y0 + 3, pz - 1, px - 4, y0 + 3, pz + 1, 44, 0);
        fill(c, px - 5, y0 + 2, pz - 1, px - 5, y0 + 4, pz + 1, 160, 15);
        put(c, px - 3, y0 + 2, pz, 69, 1);
        put(c, px - 3, y0 + 2, pz - 1, 77, 1); put(c, px - 3, y0 + 2, pz + 1, 77, 1);
        mob(c, px - 4, y0 + 2, pz - 3, EntityType.ENDERMAN);
        mob(c, px - 4, y0 + 2, pz + 3, EntityType.CAVE_SPIDER);
        mob(c, px + 3, y0 + 2, pz, EntityType.ZOMBIE);
        graded(c, s, px - 5, y0 + 2, pz - 5, r, DOOM3_LOOT, true);
        graded(c, s, px - 5, y0 + 2, pz + 5, r, DOOM3_LOOT, false);
        // The dark, and what is hanging in it.
        // Webs only where they can hang: under a ceiling or in a corner (structure audit 2026-09-23;
        // some floated mid-corridor or replaced concrete). Same draws.
        for (int i = 0; i < 34; i++) {
            int wx = x0 + 3 + r.nextInt(33), wz = z0 + 2 + r.nextInt(25);
            int wy = y0 + 2 + r.nextInt(3);
            if (webSpot(c, wx, wy, wz)) put(c, wx, wy, wz, 30, 0);
        }
        fill(c, x0 + 2, y0 + 1, z0 + 13, x0 + 2, y0 + 9, z0 + 14, 0, 0);
        fill(c, x0 + 2, y0 + 1, z0 + 13, x0 + 2, y0 + 9, z0 + 13, 65, 5);
        riser(c, t, x0 + 2, z0 + 13, y1, 251, 7);
    }
}
