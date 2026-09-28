package chat.jaspr.atlas;

import java.util.List;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * A chunk-clipped drawing surface. Every structure in Atlas is a pure function of the seed (and the realm's liberation
 * state): it draws in world space and only the blocks inside the chunk being generated are kept, so a city can span many
 * chunks without ever loading a neighbour. Tile blocks and entities (chests, signs, spawners, people, banners, beds,
 * flower pots, statues) are recorded so the populator can finish them; the populator recomputes them by drawing again.
 */
final class Canvas {
    // Block ids (1.12).
    static final int AIR = 0, STONE = 1, GRASS = 2, DIRT = 3, COBBLE = 4, PLANKS = 5, SAPLING = 6, WATER = 9, LAVA = 11, SAND = 12,
        GRAVEL = 13, LOG = 17, LEAVES = 18, GLASS = 20, SANDSTONE = 24, NOTE = 25, BED = 26, RAIL = 66, WEB = 30, TALLGRASS = 31,
        DEADBUSH = 32, WOOL = 35, YELLOW_FLOWER = 37, RED_FLOWER = 38, DOUBLE_SLAB = 43, SLAB = 44, BRICK_BLOCK = 45,
        BOOKSHELF = 47, MOSSY = 48, OBSIDIAN = 49, TORCH = 50, FIRE = 51, SPAWNER = 52, OAK_STAIRS = 53, CHEST = 54,
        WORKBENCH = 58, CROPS = 59, FARMLAND = 60, FURNACE = 61, SIGN_POST = 63, WOOD_DOOR = 64, LADDER = 65,
        COBBLE_STAIRS = 67, WALL_SIGN = 68, LEVER = 69, PLATE = 70, IRON_DOOR = 71, WOOD_PLATE = 72, SNOW_LAYER = 78,
        CACTUS = 81, CLAY_BLOCK = 82, REEDS = 83, FENCE = 85, PUMPKIN = 86, NETHERRACK = 87, SOUL_SAND = 88, GLOWSTONE = 89,
        PORTAL = 90, TRAPDOOR = 96, BRICK = 98, BARS = 101, PANE = 102, MELON = 103, VINE = 106, GATE = 107,
        BRICK_STAIRS = 108, STONE_BRICK_STAIRS = 109, LILY = 111, NETHER_BRICK = 112, NETHER_FENCE = 113,
        NETHER_STAIRS = 114, NETHER_WART = 115, ENCHANT_TABLE = 116, BREWING = 117, CAULDRON = 118, END_STONE = 121,
        LAMP = 123, WOOD_DOUBLE_SLAB = 125, WOOD_SLAB = 126, SANDSTONE_STAIRS = 128, SPRUCE_STAIRS = 134,
        BIRCH_STAIRS = 135, WALL = 139, FLOWER_POT = 140, CARROTS = 141, POTATOES = 142, SKULL = 144, ANVIL = 145,
        QUARTZ = 155, QUARTZ_STAIRS = 156, CLAY = 159, STAINED_PANE = 160, LEAVES2 = 161, LOG2 = 162,
        DARK_OAK_STAIRS = 164, IRON_TRAPDOOR = 167, PRISMARINE = 168, SEA_LANTERN = 169, HAY = 170, CARPET = 171,
        HARD_CLAY = 172, DOUBLE_PLANT = 175, BANNER = 176, WALL_BANNER = 177, RED_SANDSTONE = 179, SPRUCE_FENCE = 188,
        DARK_OAK_FENCE = 191, END_ROD = 198, PURPUR = 201, PURPUR_PILLAR = 202, BEETROOT = 207, PATH = 208, MAGMA = 213,
        WART_BLOCK = 214, RED_NETHER_BRICK = 215, BONE = 216, GLAZED_WHITE = 235, GLAZED_LIGHT_BLUE = 238,
        GLAZED_CYAN = 244, GLAZED_BLUE = 246, GLAZED_GRAY = 242, GLAZED_BLACK = 250, CONCRETE = 251, POWDER = 252,
        STAINED_GLASS = 95, COAL_ORE = 16, IRON_ORE = 15;

    // Colours (wool, clay, concrete, carpet, glass metas).
    static final int WHITE = 0, ORANGE = 1, MAGENTA = 2, LIGHT_BLUE = 3, YELLOW = 4, LIME = 5, PINK = 6, GRAY = 7, SILVER = 8,
        CYAN = 9, PURPLE = 10, BLUE = 11, BROWN = 12, GREEN = 13, RED = 14, BLACK = 15;

    // Tile kinds: finished by the populator.
    static final int CHEST_TILE = 0, SIGN_TILE = 1, SPAWNER_TILE = 2, NPC_TILE = 3, BANNER_TILE = 4, BED_TILE = 5, POT_TILE = 6,
        STAND_TILE = 7, DISPENSER_TILE = 8, STANDING_SIGN_TILE = 9;

    /** Something to finish after generation: a chest, sign, spawner, person, banner, bed, flower pot or statue. */
    static final class Tile {
        final int x, y, z, kind, meta;
        final String what;    // chest: loot key; sign: text (lines split by newlines); spawner/npc/stand: kind; banner: pattern
        final String extras;  // chest extras ("book:<id>;item:<key>"), npc id, or null
        Tile(int x, int y, int z, int kind, int meta, String what, String extras) {
            this.x = x; this.y = y; this.z = z; this.kind = kind; this.meta = meta; this.what = what; this.extras = extras;
        }
    }

    final int cx, cz, x0, z0;
    final long seed;
    /** Liberation state the chunk is drawn for (bit per province, see {@link Realm.Province}). */
    final int mask;
    private final ChunkData data;
    private final int[][] heights;
    private final List<Tile> tiles;
    private java.util.BitSet fixed;   // tile positions: the first tile placed on a spot keeps it from later drawing

    Canvas(long seed, int cx, int cz, int mask, ChunkData data, int[][] heights, List<Tile> tiles) {
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.x0 = cx << 4;
        this.z0 = cz << 4;
        this.mask = mask;
        this.data = data;
        this.heights = heights;
        this.tiles = tiles;
    }

    boolean liberated(Realm.Province p) { return (mask & p.bit) != 0; }

    boolean inside(int x, int z) { return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16; }

    /** Whether the box [minX..maxX] x [minZ..maxZ] touches this chunk (cheap reject for whole structures). */
    boolean touches(int minX, int minZ, int maxX, int maxZ) { return maxX >= x0 && minX < x0 + 16 && maxZ >= z0 && minZ < z0 + 16; }

    /** Surface height of a column in this chunk (-1 outside it, or while only tiles are recomputed). */
    int ground(int x, int z) { return inside(x, z) ? heights[x - x0][z - z0] : -1; }

    void set(int x, int y, int z, int id, int meta) {
        if (y < 1 || y > 254 || !inside(x, z)) return;
        if (fixed != null && fixed.get(key(x, y, z))) return;
        if (data != null) data.setBlock(x - x0, y, z - z0, id, (byte) meta);
    }

    void set(int x, int y, int z, int id) { set(x, y, z, id, 0); }

    int get(int x, int y, int z) {
        if (data == null || y < 0 || y > 255 || !inside(x, z)) return -1;
        return data.getTypeId(x - x0, y, z - z0);
    }

    boolean keep(int x, int y, int z, double keep) { return Hash.unit(seed, x, y, z) < keep; }

    double roll(int x, int y, int z, int salt) { return Hash.unit(seed ^ salt * 0x51ED27L, x, y, z); }

    // ------------------------------------------------------------------ Asterian materials

    /** Asterian marble: quartz, white concrete and polished diorite, faintly varied by position. */
    void marble(int x, int y, int z) {
        double r = roll(x, y, z, 11);
        if (r < 0.55) set(x, y, z, QUARTZ, 0);
        else if (r < 0.85) set(x, y, z, CONCRETE, WHITE);
        else if (r < 0.95) set(x, y, z, STONE, 4);
        else set(x, y, z, DOUBLE_SLAB, 7);
    }

    /** Smooth ashlar for plinths and lower courses: smooth stone, polished andesite and white concrete. */
    void ashlar(int x, int y, int z) {
        double r = roll(x, y, z, 12);
        if (r < 0.45) set(x, y, z, DOUBLE_SLAB, 8);
        else if (r < 0.75) set(x, y, z, STONE, 4);
        else if (r < 0.9) set(x, y, z, CONCRETE, WHITE);
        else set(x, y, z, QUARTZ, 0);
    }

    /** Asterian paving: pale slabs with lapis-blue and white glazed inlays in a steady rhythm. */
    void pave(int x, int y, int z) {
        if (Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0) { set(x, y, z, GLAZED_LIGHT_BLUE, Math.floorMod(x + z, 4)); return; }
        double r = roll(x, y, z, 13);
        if (r < 0.55) set(x, y, z, DOUBLE_SLAB, 8);
        else if (r < 0.8) set(x, y, z, STONE, 4);
        else if (r < 0.93) set(x, y, z, QUARTZ, 0);
        else set(x, y, z, CONCRETE, SILVER);
    }

    /** A country road: gravel-bound path with pale kerb stones. */
    void roadbed(int x, int y, int z) {
        double r = roll(x, y, z, 14);
        if (r < 0.62) set(x, y, z, PATH);
        else if (r < 0.82) set(x, y, z, DOUBLE_SLAB, 8);
        else if (r < 0.93) set(x, y, z, STONE, 5);
        else set(x, y, z, GRAVEL);
    }

    /** Terracotta roof tiles. */
    void roofTile(int x, int y, int z) { set(x, y, z, roll(x, y, z, 15) < 0.7 ? HARD_CLAY : CLAY, roll(x, y, z, 15) < 0.7 ? 0 : ORANGE); }

    // ------------------------------------------------------------------ Dominion materials

    /** Cinder masonry: black concrete, black clay, nether brick and obsidian courses. */
    void cinder(int x, int y, int z) {
        double r = roll(x, y, z, 21);
        if (r < 0.34) set(x, y, z, CONCRETE, BLACK);
        else if (r < 0.60) set(x, y, z, CLAY, BLACK);
        else if (r < 0.80) set(x, y, z, NETHER_BRICK);
        else if (r < 0.90) set(x, y, z, OBSIDIAN);
        else if (r < 0.96) set(x, y, z, CONCRETE, GRAY);
        else set(x, y, z, RED_NETHER_BRICK);
    }

    /** Slag and rough black rubble. */
    void slag(int x, int y, int z) {
        double r = roll(x, y, z, 22);
        if (r < 0.3) set(x, y, z, COBBLE);
        else if (r < 0.55) set(x, y, z, CLAY, BLACK);
        else if (r < 0.75) set(x, y, z, GRAVEL);
        else if (r < 0.9) set(x, y, z, STONE, 5);
        else set(x, y, z, MAGMA);
    }

    /** Ash ground: grey and black powder, gravel, coarse dirt and soul sand. */
    void ash(int x, int y, int z) {
        double r = roll(x, y, z, 23);
        if (r < 0.34) set(x, y, z, POWDER, GRAY);
        else if (r < 0.46) set(x, y, z, POWDER, BLACK);
        else if (r < 0.62) set(x, y, z, GRAVEL);
        else if (r < 0.76) set(x, y, z, DIRT, 1);
        else if (r < 0.86) set(x, y, z, SOUL_SAND);
        else if (r < 0.95) set(x, y, z, CLAY, GRAY);
        else set(x, y, z, STONE, 5);
    }

    /** Dominion roads: packed black gravel, cracked black clay and obsidian cobbles. */
    void blackRoad(int x, int y, int z) {
        double r = roll(x, y, z, 24);
        if (r < 0.4) set(x, y, z, CLAY, BLACK);
        else if (r < 0.65) set(x, y, z, CONCRETE, BLACK);
        else if (r < 0.85) set(x, y, z, GRAVEL);
        else set(x, y, z, OBSIDIAN);
    }

    /** Fill from the column's ground up to {@code top} (plinths on uneven land) in marble or cinder. */
    void foundation(int x, int z, int top, boolean asterian) {
        int g = ground(x, z);
        if (g < 0) return;
        for (int y = Math.max(1, g - 2); y <= top; y++) { if (asterian) ashlar(x, y, z); else cinder(x, y, z); }
    }

    void clear(int x, int z, int y0, int y1) { for (int y = Math.max(1, y0); y <= y1; y++) set(x, y, z, AIR); }

    // ------------------------------------------------------------------ tiles

    private int key(int x, int y, int z) { return (x - x0) << 12 | (z - z0) << 8 | y; }

    /** Places a tile block unless another tile already holds the spot; the spot is then kept from later drawing. */
    private boolean tile(int x, int y, int z, int id, int meta) {
        if (!inside(x, z) || y < 1 || y > 254 || fixed != null && fixed.get(key(x, y, z))) return false;
        if (id >= 0) set(x, y, z, id, meta);
        if (fixed == null) fixed = new java.util.BitSet(65536);
        fixed.set(key(x, y, z));
        return true;
    }

    private void add(int x, int y, int z, int kind, int meta, String what, String extras) {
        if (tiles != null) tiles.add(new Tile(x, y, z, kind, meta, what, extras));
    }

    void chest(int x, int y, int z, int facing, String loot, String extras) {
        if (tile(x, y, z, CHEST, facing)) add(x, y, z, CHEST_TILE, facing, loot, extras);
    }

    /** A wall sign (meta 2 north .. 5 east); lines separated by newlines. */
    void sign(int x, int y, int z, int facing, String text) {
        if (tile(x, y, z, WALL_SIGN, facing)) add(x, y, z, SIGN_TILE, facing, text, null);
    }

    /** A standing sign (meta 0-15 rotation). */
    void post(int x, int y, int z, int rotation, String text) {
        if (tile(x, y, z, SIGN_POST, rotation)) add(x, y, z, STANDING_SIGN_TILE, rotation, text, null);
    }

    void spawner(int x, int y, int z, String entity) {
        if (tile(x, y, z, SPAWNER, 0)) add(x, y, z, SPAWNER_TILE, 0, entity, null);
    }

    void dispenser(int x, int y, int z, int facing) {
        if (tile(x, y, z, 23, facing)) add(x, y, z, DISPENSER_TILE, facing, "ARROW", null);
    }

    /**
     * A person or creature placed with the chunk: {@code kind} names what (see the populator), {@code id} is its identity
     * (a key figure's or captive's id, or null for an ordinary citizen or guard), {@code yaw} its facing in degrees.
     */
    void npc(int x, int y, int z, int yaw, String kind, String id) {
        if (tile(x, y, z, -1, 0)) add(x, y, z, NPC_TILE, yaw, kind, id);
    }

    /** A wall banner on the face of the block behind it (meta 2-5) or a standing banner (rotation 0-15). */
    void banner(int x, int y, int z, int meta, boolean wall, String pattern) {
        if (tile(x, y, z, wall ? WALL_BANNER : BANNER, meta)) add(x, y, z, BANNER_TILE, meta, pattern, wall ? "wall" : "stand");
    }

    /** A bed: foot at (x, y, z), head one block toward (dx, dz); coloured by the populator. */
    void bed(int x, int y, int z, int dx, int dz, int color) {
        int dir = dz > 0 ? 0 : dx < 0 ? 1 : dz < 0 ? 2 : 3;   // 0 south, 1 west, 2 north, 3 east
        if (!inside(x, z) || !inside(x + dx, z + dz)) return;
        if (tile(x, y, z, BED, dir)) {
            set(x + dx, y, z + dz, BED, dir | 8);
            add(x, y, z, BED_TILE, color, "foot", null);
            add(x + dx, y, z + dz, BED_TILE, color, "head", null);
        }
    }

    /** A flower pot holding a plant ("red_flower:3", "sapling:0", "cactus", "deadbush", ...). */
    void pot(int x, int y, int z, String plant) {
        if (tile(x, y, z, FLOWER_POT, 0)) add(x, y, z, POT_TILE, 0, plant, null);
    }

    /** An armor stand statue or figure (kind names its dress and pose, see the populator). */
    void stand(int x, int y, int z, int yaw, String kind) {
        if (tile(x, y, z, -1, 0)) add(x, y, z, STAND_TILE, yaw, kind, null);
    }
}
