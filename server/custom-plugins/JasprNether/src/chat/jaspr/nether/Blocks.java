package chat.jaspr.nether;

/**
 * Block constants. Vanilla ids are fixed; every BetterNether / NetherEx stand-in is loaded from resources/blocks.tsv
 * (BlockMap) at start-up and held here as a combined value (id << 4 | meta) or, where only the id matters, an id.
 * Nothing in the generator or the mechanics hard-codes a stand-in: change blocks.tsv to change the look.
 */
final class Blocks {
    private Blocks() {}

    // ---- vanilla ----
    static final int AIR = 0, STONE = 1, BEDROCK = 7, WATER = 9, LAVA_FLOW = 10, LAVA = 11, GRAVEL = 13, LEAVES = 18;
    static final int BROWN_MUSHROOM = 39, RED_MUSHROOM = 40, OBSIDIAN = 49, FIRE = 51, SPAWNER = 52, CHEST = 54;
    static final int NETHERRACK = 87, SOUL_SAND = 88, GLOWSTONE = 89, HUGE_BROWN = 99, HUGE_RED = 100;
    static final int NETHER_BRICK = 112, NETHER_BRICK_FENCE = 113, NETHER_WART = 115, CAULDRON = 118, QUARTZ_ORE = 153;
    static final int TERRACOTTA = 159, LEAVES2 = 161, MAGMA = 213, NETHER_WART_BLOCK = 214, RED_NETHER_BRICK = 215;
    static final int BONE_BLOCK = 216, SKULL = 144;

    // ---- NetherEx stand-ins (combined) ----
    static int GLOOMY_NETHERRACK, FIERY_NETHERRACK, LIVELY_NETHERRACK, ICY_NETHERRACK, BASALT, HYPHAE, FROSTBURN_ICE, ICHOR;
    static int NEX_QUARTZ_ORE, AMETHYST_ORE, RIME_ORE, THORNSTALK, ELDER_STEM, ENOKI_STEM, ENOKI_CAP, BLUE_FIRE, URN;
    static int RIME_BLOCK, AMETHYST_BLOCK;
    static int ELDER_CAP_BROWN_ID, ELDER_CAP_RED_ID;
    // ---- BetterNether stand-ins (combined) ----
    static int NETHER_MYCELIUM, NETHERRACK_MOSS, NETHER_GRASS, CINCINNASITE_ORE, NETHER_REED, SMOKER, EGG_PLANT, RED_MOLD, GRAY_MOLD;
    static int ORANGE_MUSHROOM, STALAGNATE_BOTTOM, STALAGNATE_MIDDLE, STALAGNATE_TOP, LUCIS_CENTER, LUCIS_RIM, EYE_VINE, EYEBALL, EYEBALL_SMALL;
    static int NETHER_CACTUS, BARREL_CACTUS, AGAVE, BLACK_BUSH, INK_BUSH, BLACK_APPLE, MAGMA_FLOWER, RED_LARGE_STALK, RED_LARGE_CAP;
    static int BROWN_LARGE_ID, BONE_MUSHROOM, STATUE, CINCINNASITE_PILLAR, CINCINNASITE_WALL, CINCINNASITE_BOWL;

    // ids of mechanic blocks, for fast event checks
    static int THORNSTALK_ID, EGG_PLANT_ID, AMETHYST_ORE_ID, RIME_ORE_ID, CINCINNASITE_ORE_ID, BLACK_APPLE_ID, RIME_BLOCK_ID;

    static void load() {
        GLOOMY_NETHERRACK = BlockMap.get("netherex:gloomy_netherrack");
        FIERY_NETHERRACK = BlockMap.get("netherex:fiery_netherrack");
        LIVELY_NETHERRACK = BlockMap.get("netherex:lively_netherrack");
        ICY_NETHERRACK = BlockMap.get("netherex:icy_netherrack");
        BASALT = BlockMap.get("netherex:basalt");
        HYPHAE = BlockMap.get("netherex:hyphae");
        FROSTBURN_ICE = BlockMap.get("netherex:frostburn_ice");
        ICHOR = BlockMap.get("netherex:ichor");
        NEX_QUARTZ_ORE = BlockMap.get("netherex:quartz_ore");
        AMETHYST_ORE = BlockMap.get("netherex:amethyst_ore");
        RIME_ORE = BlockMap.get("netherex:rime_ore");
        THORNSTALK = BlockMap.get("netherex:thornstalk");
        ELDER_STEM = BlockMap.get("netherex:elder_mushroom_stem");
        ELDER_CAP_BROWN_ID = BlockMap.id("netherex:brown_elder_mushroom_cap");
        ELDER_CAP_RED_ID = BlockMap.id("netherex:red_elder_mushroom_cap");
        ENOKI_STEM = BlockMap.get("netherex:enoki_mushroom_stem");
        ENOKI_CAP = BlockMap.get("netherex:enoki_mushroom_cap");
        BLUE_FIRE = BlockMap.get("netherex:blue_fire");
        URN = BlockMap.get("netherex:urn_of_sorrow");
        RIME_BLOCK = BlockMap.get("netherex:rime_block");
        AMETHYST_BLOCK = BlockMap.get("netherex:amethyst_block");
        NETHER_MYCELIUM = BlockMap.get("betternether:nether_mycelium");
        NETHERRACK_MOSS = BlockMap.get("betternether:netherrack_moss");
        NETHER_GRASS = BlockMap.get("betternether:nether_grass");
        CINCINNASITE_ORE = BlockMap.get("betternether:cincinnasite_ore");
        NETHER_REED = BlockMap.get("betternether:nether_reed");
        SMOKER = BlockMap.get("betternether:smoker");
        EGG_PLANT = BlockMap.get("betternether:egg_plant");
        RED_MOLD = BlockMap.get("betternether:red_mold");
        GRAY_MOLD = BlockMap.get("betternether:gray_mold");
        ORANGE_MUSHROOM = BlockMap.get("betternether:orange_mushroom");
        STALAGNATE_BOTTOM = BlockMap.get("betternether:stalagnate_bottom");
        STALAGNATE_MIDDLE = BlockMap.get("betternether:stalagnate_middle");
        STALAGNATE_TOP = BlockMap.get("betternether:stalagnate_top");
        LUCIS_CENTER = BlockMap.resolve("betternether:lucis_mushroom", 0);
        LUCIS_RIM = BlockMap.resolve("betternether:lucis_mushroom", 1);
        EYE_VINE = BlockMap.get("betternether:eye_vine");
        EYEBALL = BlockMap.get("betternether:block_eyeball");
        EYEBALL_SMALL = BlockMap.get("betternether:block_eyeball_small");
        NETHER_CACTUS = BlockMap.get("betternether:nether_cactus");
        BARREL_CACTUS = BlockMap.get("betternether:barrel_cactus");
        AGAVE = BlockMap.get("betternether:agave");
        BLACK_BUSH = BlockMap.get("betternether:black_bush");
        INK_BUSH = BlockMap.get("betternether:ink_bush");
        BLACK_APPLE = BlockMap.get("betternether:black_apple");
        MAGMA_FLOWER = BlockMap.get("betternether:magma_flower");
        RED_LARGE_STALK = BlockMap.resolve("betternether:red_large_mushroom", 0);
        RED_LARGE_CAP = BlockMap.resolve("betternether:red_large_mushroom", 1);
        BROWN_LARGE_ID = BlockMap.id("betternether:brown_large_mushroom");
        BONE_MUSHROOM = BlockMap.resolve("betternether:bone_mushroom", 0);
        STATUE = BlockMap.get("betternether:pig_statue_01");
        CINCINNASITE_PILLAR = BlockMap.get("betternether:cincinnasite_pillar");
        CINCINNASITE_WALL = BlockMap.get("betternether:cincinnasite_wall");
        CINCINNASITE_BOWL = BlockMap.get("betternether:cincinnasite_fire_bowl");
        THORNSTALK_ID = THORNSTALK >> 4;
        EGG_PLANT_ID = EGG_PLANT >> 4;
        AMETHYST_ORE_ID = AMETHYST_ORE >> 4;
        RIME_ORE_ID = RIME_ORE >> 4;
        CINCINNASITE_ORE_ID = CINCINNASITE_ORE >> 4;
        BLACK_APPLE_ID = BLACK_APPLE >> 4;
        RIME_BLOCK_ID = RIME_BLOCK >> 4;
        for (int v : new int[]{GLOOMY_NETHERRACK, FIERY_NETHERRACK, LIVELY_NETHERRACK, ICY_NETHERRACK, BASALT, HYPHAE, FROSTBURN_ICE,
            NETHER_MYCELIUM, NETHERRACK_MOSS, AMETHYST_ORE, RIME_ORE, CINCINNASITE_ORE, NEX_QUARTZ_ORE})
            if (v >= 0) TERRAIN[v >> 4] = true;
    }

    private static final boolean[] FULL = new boolean[4096];
    private static final boolean[] TE = new boolean[4096];
    private static final boolean[] REPLACEABLE = new boolean[4096];
    private static final boolean[] TERRAIN = new boolean[4096];
    static {
        for (int id : new int[]{1, 2, 3, 4, 5, 7, 12, 13, 14, 15, 16, 17, 19, 21, 22, 24, 35, 41, 42, 43, 45, 47, 48, 49, 56, 57, 73, 74,
            80, 82, 87, 88, 89, 97, 98, 99, 100, 103, 110, 112, 121, 125, 129, 133, 152, 153, 155, 159, 162, 168, 169, 170, 172, 173,
            174, 179, 201, 202, 206, 213, 214, 215, 216, 251, 252})
            FULL[id] = true;
        for (int id : new int[]{23, 25, 26, 52, 54, 61, 62, 63, 68, 84, 116, 117, 130, 138, 140, 144, 146, 154, 158, 176, 177, 209, 210,
            211, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234})
            TE[id] = true;
        for (int id : new int[]{0, 31, 32, 51, 78, 106, 8, 9, 10, 11})
            REPLACEABLE[id] = true;
        for (int id : new int[]{87, 88, 13, 213, 153})
            TERRAIN[id] = true;
    }
    static boolean isFullSolid(int id) { return id >= 0 && id < 4096 && FULL[id]; }
    static boolean isTileEntity(int id) { return id >= 0 && id < 4096 && TE[id]; }
    static boolean replaceable(int id) { return id >= 0 && id < 4096 && REPLACEABLE[id]; }
    static boolean terrain(int id) { return id >= 0 && id < 4096 && TERRAIN[id]; }

    /** Netherrack or one of the NetherEx netherrack stand-ins. */
    static boolean rack(int combined) {
        return combined == (NETHERRACK << 4) || combined == GLOOMY_NETHERRACK || combined == FIERY_NETHERRACK
            || combined == LIVELY_NETHERRACK || combined == ICY_NETHERRACK;
    }
}
