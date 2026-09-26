package chat.jaspr.lostcities;

/**
 * The mod's LostCityProfile defaults (1.12-2.0.22 "default" profile) with the "onlycities" city settings,
 * restricted to what the DEFAULT landscape generator reads. Deviations are marked.
 */
final class Profile {
    private Profile() {}

    static final int DEBRIS_TO_NEARBYCHUNK_FACTOR = 200;
    static final float VINE_CHANCE = 0.009f;
    static final float CHANCE_OF_RANDOM_LEAFBLOCKS = .1f;
    static final int THICKNESS_OF_RANDOM_LEAFBLOCKS = 2;
    static final boolean AVOID_FOLIAGE = false;

    static final boolean RUBBLELAYER = true;
    static final float RUBBLE_DIRT_SCALE = 3.0f;
    static final float RUBBLE_LEAVE_SCALE = 6.0f;

    static final boolean RUINS = true;
    static final float RUIN_CHANCE = 0.05f;
    static final float RUIN_MINLEVEL_PERCENT = 0.8f;
    static final float RUIN_MAXLEVEL_PERCENT = 1.0f;

    static final int GROUNDLEVEL = 71;
    static final int WATERLEVEL_OFFSET = 8;
    static final int WATERLEVEL = GROUNDLEVEL - WATERLEVEL_OFFSET;   // 63, the HorrorBiomes sea level too

    static final boolean HIGHWAY_REQUIRES_TWO_CITIES = true;
    static final int HIGHWAY_LEVEL_FROM_CITIES_MODE = 0;
    static final float HIGHWAY_MAINPERLIN_SCALE = 50.0f;
    static final float HIGHWAY_SECONDARYPERLIN_SCALE = 10.0f;
    static final float HIGHWAY_PERLIN_FACTOR = 2.0f;
    static final int HIGHWAY_DISTANCE_MASK = 7;
    static final boolean HIGHWAY_SUPPORTS = true;

    static final float RAILWAY_DUNGEON_CHANCE = .01f;
    /** Deviation: true (the mod's option for worlds where cities are rare) so the subway ends near cities
     *  instead of tunnelling under the whole world. */
    static final boolean RAILWAYS_CAN_END = true;
    static final boolean RAILWAYS_ENABLED = true;
    static final boolean RAILWAY_STATIONS_ENABLED = true;

    static final float DESTROY_LONE_BLOCKS_FACTOR = .05f;
    static final float DESTROY_OR_MOVE_CHANCE = .4f;
    static final int DESTROY_SMALL_SECTIONS_SIZE = 50;
    static final boolean EXPLOSIONS_IN_CITIES_ONLY = true;

    static final boolean GENERATE_SPAWNERS = true;
    static final boolean GENERATE_LOOT = true;
    static final boolean GENERATE_LIGHTING = false;
    static final boolean AVOID_WATER = false;

    static final float EXPLOSION_CHANCE = .002f;
    static final int EXPLOSION_MINRADIUS = 15;
    static final int EXPLOSION_MAXRADIUS = 35;
    static final int EXPLOSION_MINHEIGHT = 75;
    static final int EXPLOSION_MAXHEIGHT = 90;

    static final float MINI_EXPLOSION_CHANCE = .03f;
    static final int MINI_EXPLOSION_MINRADIUS = 5;
    static final int MINI_EXPLOSION_MAXRADIUS = 12;
    static final int MINI_EXPLOSION_MINHEIGHT = 60;
    static final int MINI_EXPLOSION_MAXHEIGHT = 100;

    static final int CITY_LEVEL0_HEIGHT = 75;
    static final int CITY_LEVEL1_HEIGHT = 83;
    static final int CITY_LEVEL2_HEIGHT = 91;
    static final int CITY_LEVEL3_HEIGHT = 99;

    static final float CHEST_WITHOUT_LOOT_CHANCE = .2f;
    static final float BUILDING_WITHOUT_LOOT_CHANCE = .2f;
    static final float BUILDING_CHANCE = .3f;
    static final int BUILDING_MINFLOORS = 0;
    static final int BUILDING_MAXFLOORS = 9;
    static final int BUILDING_MINFLOORS_CHANCE = 4;
    static final int BUILDING_MAXFLOORS_CHANCE = 6;
    static final int BUILDING_MINCELLARS = 0;
    static final int BUILDING_MAXCELLARS = 4;
    static final float BUILDING_DOORWAYCHANCE = .6f;
    static final float BUILDING_FRONTCHANCE = .2f;
    static final float PARK_CHANCE = .2f;

    static final float CORRIDOR_CHANCE = .7f;
    static final float BRIDGE_CHANCE = .7f;
    static final float FOUNTAIN_CHANCE = .05f;
    static final float BUILDING2X2_CHANCE = .03f;
    static final boolean BRIDGE_SUPPORTS = true;

    static final int BEDROCK_LAYER = 1;

    /** "onlycities": one continuous city, the city factor is 1 everywhere inside a region. */
    static final float CITY_FACTOR = 1.0f;

    static final String WORLD_STYLE = "standard";
}
