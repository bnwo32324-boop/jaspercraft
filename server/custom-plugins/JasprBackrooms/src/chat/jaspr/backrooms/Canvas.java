package chat.jaspr.backrooms;

import java.util.List;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * A chunk-clipped drawing surface in world coordinates: levels draw column by column as pure functions of the seed and
 * the coordinates, and only the blocks inside the chunk being generated are kept. Chests and signs are recorded as
 * tiles for the populator, which fills them once the chunk exists.
 */
final class Canvas {
    // Block ids (1.12.2) used by the levels.
    static final int AIR = 0, STONE = 1, GRASS = 2, DIRT = 3, COBBLE = 4, PLANKS = 5, BEDROCK = 7, WATER = 9, LAVA = 11, SAND = 12,
        LOG = 17, LEAVES = 18, SPONGE = 19, GLASS = 20, DISPENSER = 23, SANDSTONE = 24, NOTE = 25, WEB = 30, WOOL = 35,
        DOUBLE_SLAB = 43, SLAB = 44, BRICKS = 45, BOOKSHELF = 47, MOSSY = 48, OBSIDIAN = 49, TORCH = 50, OAK_STAIRS = 53, CHEST = 54,
        WORKBENCH = 58, FURNACE_LIT = 62, OAK_DOOR = 64, LADDER = 65, WALL_SIGN = 68, LEVER = 69, STONE_PLATE = 70, IRON_DOOR = 71,
        REDSTONE_ORE = 73, REDSTONE_TORCH = 76, STONE_BUTTON = 77, JUKEBOX = 84, FENCE = 85, PUMPKIN = 86, GLOWSTONE = 89, PORTAL = 90,
        TRAPDOOR = 96, STONE_BRICK = 98, IRON_BARS = 101, PANE = 102, MELON = 103, BRICK_STAIRS = 108, STONEBRICK_STAIRS = 109,
        NETHER_BRICK = 112, NETHER_FENCE = 113, CAULDRON = 118, END_STONE = 121, LAMP_OFF = 123, WOOD_SLAB = 126, SANDSTONE_STAIRS = 128,
        COBBLE_WALL = 139, ANVIL = 145, TRAPPED_CHEST = 146, DAYLIGHT = 151, HOPPER = 154, QUARTZ = 155, QUARTZ_STAIRS = 156,
        DROPPER = 158, CLAY = 159, STAINED_PANE = 160, BARRIER = 166, IRON_TRAPDOOR = 167, PRISMARINE = 168, SEA_LANTERN = 169,
        HAY = 170, CARPET = 171, COAL_BLOCK = 173, SPRUCE_FENCE = 188, BIRCH_DOOR = 194, END_ROD = 198, PURPUR = 201, MAGMA = 213,
        BONE = 216, OBSERVER = 218, WHITE_GLAZED = 235, YELLOW_GLAZED = 239, CONCRETE = 251, STAINED_GLASS = 95;
    // Colours (wool, carpet, concrete, clay, glass).
    static final int WHITE = 0, ORANGE = 1, MAGENTA = 2, LIGHT_BLUE = 3, YELLOW = 4, LIME = 5, PINK = 6, GRAY = 7, SILVER = 8,
        CYAN = 9, PURPLE = 10, BLUE = 11, BROWN = 12, GREEN = 13, RED = 14, BLACK = 15;

    static final int CHEST_TILE = 0, SIGN_TILE = 1;

    /** A chest to fill or a sign to write once the chunk exists. */
    static final class Tile {
        final int x, y, z, kind, level;
        final double danger;
        final String text;   // chest: loot table key; sign: lines separated by '\n'
        Tile(int x, int y, int z, int kind, int level, double danger, String text) {
            this.x = x; this.y = y; this.z = z; this.kind = kind; this.level = level; this.danger = danger; this.text = text;
        }
    }

    final int cx, cz, x0, z0;
    final long seed;
    private final ChunkData data;
    private final List<Tile> tiles;   // null while generating block data only

    Canvas(long seed, int cx, int cz, ChunkData data, List<Tile> tiles) {
        this.seed = seed; this.cx = cx; this.cz = cz; this.x0 = cx << 4; this.z0 = cz << 4; this.data = data; this.tiles = tiles;
    }

    boolean inside(int x, int z) { return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16; }
    boolean touches(int minX, int minZ, int maxX, int maxZ) { return maxX >= x0 && minX < x0 + 16 && maxZ >= z0 && minZ < z0 + 16; }

    @SuppressWarnings("deprecation")
    void set(int x, int y, int z, int id, int meta) {
        if (y < 0 || y > 255 || !inside(x, z) || data == null) return;
        data.setBlock(x - x0, y, z - z0, id, (byte) meta);
    }

    void set(int x, int y, int z, int id) { set(x, y, z, id, 0); }

    /** A vertical run y0..y1 (inclusive) of one block in a column. */
    @SuppressWarnings("deprecation")
    void column(int x, int z, int y0, int y1, int id, int meta) {
        if (!inside(x, z) || data == null || y1 < y0) return;
        data.setRegion(x - x0, Math.max(0, y0), z - z0, x - x0 + 1, Math.min(256, y1 + 1), z - z0 + 1, id, meta);
    }

    @SuppressWarnings("deprecation")
    int id(int x, int y, int z) { return inside(x, z) && data != null && y >= 0 && y < 256 ? data.getTypeId(x - x0, y, z - z0) : -1; }

    double roll(int x, int y, int z, int salt) { return Hash.unit(seed ^ salt, x, y, z); }

    /** A loot chest facing meta (2 north, 3 south, 4 west, 5 east); filled by the populator from the level's table. */
    void chest(int x, int y, int z, int facing, Level level, double danger, String table) {
        if (!inside(x, z)) return;
        set(x, y, z, CHEST, facing);
        if (tiles != null) tiles.add(new Tile(x, y, z, CHEST_TILE, level.number, danger, table));
    }

    /** A wall sign on the face of the block behind it (meta 2 north .. 5 east), written by the populator. */
    void sign(int x, int y, int z, int facing, String text) {
        if (!inside(x, z)) return;
        set(x, y, z, WALL_SIGN, facing);
        if (tiles != null) tiles.add(new Tile(x, y, z, SIGN_TILE, 0, 0, text));
    }
}
