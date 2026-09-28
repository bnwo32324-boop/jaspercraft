package chat.jaspr.ruins;

import java.util.List;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * A chunk-clipped drawing surface. Ruins are pure functions of world coordinates; every structure draws in world
 * space and only the blocks inside the chunk being generated are kept, so a ruin can span many chunks without
 * ever loading a neighbour. Tile blocks (loot chests, spawners) are recorded so the populator can fill them.
 */
final class Canvas {
    static final int AIR = 0, STONE = 1, GRASS = 2, DIRT = 3, COBBLE = 4, PLANKS = 5, WATER = 9, SAND = 12, GRAVEL = 13,
        LOG = 17, LEAVES = 18, SPONGE = 19, GLASS = 20, SANDSTONE = 24, WEB = 30, TALLGRASS = 31, DOUBLE_SLAB = 43, SLAB = 44,
        BRICKS = 45, BOOKSHELF = 47, MOSSY = 48, OBSIDIAN = 49, TORCH = 50, SPAWNER = 52, CHEST = 54, LADDER = 65,
        STONE_STAIRS = 67, SNOW = 78, FENCE = 85, GLOWSTONE = 89, PORTAL = 90, TRAPDOOR = 96, BRICK = 98, BARS = 101,
        VINE = 106, BRICK_STAIRS = 109, WALL = 139, CAULDRON = 118, QUARTZ = 155, DOUBLE_PLANT = 175, SEA_LANTERN = 169,
        PRISMARINE = 168, SKULL = 144, BONE = 216, CLAY = 159, WALL_SIGN = 68, SLIME = 165, IRON_BARS = 101, MAGMA = 213,
        PLATE = 70, DISPENSER = 23;

    static final int CHEST_TILE = 0, SPAWNER_TILE = 1, SIGN_TILE = 2, DISPENSER_TILE = 3;

    /** Marker for a chest, spawner or carved sign to fill after generation. */
    static final class Tile {
        final int x, y, z, kind;
        final boolean chest;
        final String what;    // loot table key, entity type name, or sign text (lines separated by newlines)
        final String extras;  // chests: extra contents ("lore:<n>;trinket:<chance>"), or null
        Tile(int x, int y, int z, int kind, String what, String extras) {
            this.x = x; this.y = y; this.z = z; this.kind = kind; this.chest = kind == CHEST_TILE; this.what = what; this.extras = extras;
        }
        Tile(int x, int y, int z, boolean chest, String what) { this(x, y, z, chest ? CHEST_TILE : SPAWNER_TILE, what, null); }
    }

    final int cx, cz, x0, z0;
    final long seed;
    private final ChunkData data;
    private final int[][] heights;       // surface y per chunk column (after any city terracing)
    private final List<Tile> tiles;      // null while stamping in generateChunkData when tiles are not wanted
    private java.util.BitSet fixed;      // tile positions in this chunk: the first tile placed there wins, later drawing skips it

    Canvas(long seed, int cx, int cz, ChunkData data, int[][] heights, List<Tile> tiles) {
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.x0 = cx << 4;
        this.z0 = cz << 4;
        this.data = data;
        this.heights = heights;
        this.tiles = tiles;
    }

    boolean inside(int x, int z) { return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16; }

    /** Whether the box [minX..maxX] x [minZ..maxZ] touches this chunk (cheap reject for whole structures). */
    boolean touches(int minX, int minZ, int maxX, int maxZ) { return maxX >= x0 && minX < x0 + 16 && maxZ >= z0 && minZ < z0 + 16; }

    /** Surface height of a column in this chunk. */
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

    /** Decay: keep a block with probability {@code keep}, decided per position (stable across chunks). */
    boolean keep(int x, int y, int z, double keep) { return Hash.unit(seed, x, y, z) < keep; }

    double roll(int x, int y, int z, int salt) { return Hash.unit(seed ^ salt * 0x51ED27L, x, y, z); }

    /** Ancient masonry: mossy, cracked and plain stone brick mixed by position. */
    void masonry(int x, int y, int z) {
        double r = roll(x, y, z, 1);
        if (r < 0.38) set(x, y, z, BRICK, 1);
        else if (r < 0.58) set(x, y, z, BRICK, 2);
        else if (r < 0.80) set(x, y, z, BRICK, 0);
        else if (r < 0.93) set(x, y, z, MOSSY);
        else set(x, y, z, COBBLE);
    }

    /** Rough rubble fill: mossy cobblestone and cobblestone. */
    void rubble(int x, int y, int z) { set(x, y, z, roll(x, y, z, 2) < 0.65 ? MOSSY : COBBLE); }

    /** Paving: worn, cracked stone; only the odd tuft of grass survives. */
    void paving(int x, int y, int z) {
        double r = roll(x, y, z, 3);
        if (r < 0.26) set(x, y, z, BRICK, 2);
        else if (r < 0.46) set(x, y, z, BRICK, 1);
        else if (r < 0.58) set(x, y, z, GRAVEL);
        else if (r < 0.70) set(x, y, z, STONE, 5);
        else if (r < 0.80) set(x, y, z, PRISMARINE, 2);
        else if (r < 0.88) set(x, y, z, COBBLE);
        else if (r < 0.97) set(x, y, z, BRICK, 0);
        else set(x, y, z, GRASS);
    }

    /**
     * The Choir's cyclopean stone: green-black dark prismarine, prismarine brick, cracked stone brick and andesite, the
     * masonry of the cult monuments and the giant pillars.
     */
    void eldritch(int x, int y, int z) {
        double r = roll(x, y, z, 9);
        if (r < 0.34) set(x, y, z, PRISMARINE, 2);
        else if (r < 0.52) set(x, y, z, PRISMARINE, 1);
        else if (r < 0.68) set(x, y, z, BRICK, 2);
        else if (r < 0.80) set(x, y, z, STONE, 6);
        else if (r < 0.90) set(x, y, z, CLAY, 9);
        else if (r < 0.96) set(x, y, z, BRICK, 0);
        else set(x, y, z, PRISMARINE, 0);
    }

    /** A carved glyph band: chiseled stone, obsidian and, rarely, a pale sea-lantern eye. */
    void glyph(int x, int y, int z) {
        double r = roll(x, y, z, 10);
        if (r < 0.45) set(x, y, z, BRICK, 3);
        else if (r < 0.85) set(x, y, z, OBSIDIAN);
        else if (r < 0.95) set(x, y, z, CLAY, 15);
        else set(x, y, z, SEA_LANTERN);
    }

    /** Fill from the column's ground up to {@code top} (foundations under ruins on uneven land). */
    void foundation(int x, int z, int top) {
        int g = ground(x, z);
        if (g < 0) return;
        for (int y = Math.max(1, g - 2); y <= top; y++) rubble(x, y, z);
    }

    /** Clear air from y0 to y1 (rooms, arches); never below bedrock. */
    void clear(int x, int z, int y0, int y1) { for (int y = Math.max(1, y0); y <= y1; y++) set(x, y, z, AIR); }

    /** A decayed wall column: full at the base, crumbling toward {@code base + height}. */
    void wallColumn(int x, int z, int base, int height, double sturdiness) {
        if (!inside(x, z)) return;
        int top = base + height;
        for (int y = base; y < top; y++) {
            double t = (y - base) / (double) Math.max(1, height);
            if (!keep(x, y, z, sturdiness - t * 0.75)) {
                // A gap above the lower third ends the column (a broken edge, nothing left floating); lower gaps are holes.
                if (t > 0.3) break;
                continue;
            }
            masonry(x, y, z);
        }
    }

    void pillar(int x, int z, int base, int height, boolean broken) {
        if (!inside(x, z)) return;
        int top = base + height;
        if (broken) top = base + 1 + (int) (roll(x, base, z, 4) * height);
        set(x, base, z, BRICK, 3);
        for (int y = base + 1; y < top; y++) masonry(x, y, z);
        if (!broken) set(x, top, z, BRICK, 3);
        else if (roll(x, top, z, 5) < 0.5) set(x, top, z, SLAB, 5);
    }

    /** Vines are rare here: most would-be vines never grew. */
    void vine(int x, int y, int z, int facingMeta, int length) {
        if (roll(x, y, z, 77) >= 0.25) return;
        for (int i = 0; i < length; i++) {
            if (get(x, y - i, z) != AIR) return;
            set(x, y - i, z, VINE, facingMeta);
        }
    }

    private int key(int x, int y, int z) { return (x - x0) << 12 | (z - z0) << 8 | y; }

    /** Places a tile block unless another tile already holds the spot; the spot is then kept from later drawing. */
    private boolean tile(int x, int y, int z, int id, int meta) {
        if (!inside(x, z) || y < 1 || y > 254 || fixed != null && fixed.get(key(x, y, z))) return false;
        set(x, y, z, id, meta);
        if (fixed == null) fixed = new java.util.BitSet(65536);
        fixed.set(key(x, y, z));
        return true;
    }

    void chest(int x, int y, int z, int facing, String lootTable) {
        if (!tile(x, y, z, CHEST, facing)) return;
        if (tiles != null) tiles.add(new Tile(x, y, z, true, lootTable));
    }

    /** A chest with extra contents for the populator: "lore:<book>" and/or "trinket:<chance>" (semicolon-separated). */
    void chest(int x, int y, int z, int facing, String lootTable, String extras) {
        if (!tile(x, y, z, CHEST, facing)) return;
        if (tiles != null) tiles.add(new Tile(x, y, z, CHEST_TILE, lootTable, extras));
    }

    /** A wall sign (meta 2 north .. 5 east) whose text the populator carves; lines separated by newlines. */
    void sign(int x, int y, int z, int facing, String text) {
        if (!tile(x, y, z, WALL_SIGN, facing)) return;
        if (tiles != null) tiles.add(new Tile(x, y, z, SIGN_TILE, text, null));
    }

    /** A trap dispenser (meta 1 up, 2 north .. 5 east) the populator loads with arrows. */
    void dispenser(int x, int y, int z, int facing) {
        if (!tile(x, y, z, DISPENSER, facing)) return;
        if (tiles != null) tiles.add(new Tile(x, y, z, DISPENSER_TILE, "ARROW", null));
    }

    void spawner(int x, int y, int z, String entity) {
        if (!tile(x, y, z, SPAWNER, 0)) return;
        if (tiles != null) tiles.add(new Tile(x, y, z, false, entity));
    }
}
