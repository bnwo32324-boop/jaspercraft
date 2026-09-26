package chat.jaspr.lostcities;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.IBlockData;

/**
 * Block states as the mod's primer chars: {@code id << 4 | meta}, which is exactly 1.12's
 * {@code Block.BLOCK_STATE_IDS} value for vanilla blocks. Names are resolved (and metas normalised the
 * way {@code getStateFromMeta}/{@code getMetaFromState} do) through the server's own block registry.
 */
final class B {
    private B() {}

    static final char AIR = 0;
    static final char STONE = c(1, 0);
    static final char GRASS = c(2, 0);
    static final char DIRT = c(3, 0);
    static final char BEDROCK = c(7, 0);
    static final char WATER = c(9, 0);          // minecraft:water (still), the profile's liquid block
    static final char GRAVEL = c(13, 0);
    static final char GLASS = c(20, 0);
    static final char GLOWSTONE = c(89, 0);
    static final char IRON_BARS = c(101, 0);
    static final char COMMAND_BLOCK = c(137, 0);   // the mod's "hard air" marker; never placed
    // Leaves with decayable=false (check_decay stays set, as the mod's withProperty left it)
    static final char LEAVES = c(18, 12);
    static final char LEAVES2 = c(18, 15);      // jungle
    static final char LEAVES3 = c(18, 13);      // spruce
    static final char VINE = c(106, 0);

    static char c(int id, int meta) { return (char) ((id << 4) | (meta & 15)); }
    static int id(char c) { return c >> 4; }
    static int meta(char c) { return c & 15; }

    static boolean isAir(char c) { return (c >> 4) == 0; }
    static boolean isLiquid(char c) { int id = c >> 4; return id >= 8 && id <= 11; }

    private static final Map<String, Character> PARSED = new HashMap<>();
    static int unknownBlocks = 0;

    /** Tools.stringToState + BLOCK_STATE_IDS.get, with the owner's valuables policy applied. */
    static synchronized char parse(String s) {
        Character known = PARSED.get(s);
        if (known != null) return known;
        String name = s;
        int meta = -1;
        int at = s.indexOf('@');
        if (at >= 0) {
            name = s.substring(0, at);
            try { meta = Integer.parseInt(s.substring(at + 1)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Bad meta for: '" + s + "'!"); }
        }
        char result;
        if (!name.startsWith("minecraft:") && name.indexOf(':') >= 0) {
            // Only the chisel style uses non-vanilla blocks (7 concrete variants). The browser client renders
            // vanilla 1.12 only, so they become vanilla concrete of the nearest colour.
            result = chisel(name);
        } else {
            Block block = Block.getByName(name);
            if (block == null) throw new IllegalArgumentException("Cannot find block: '" + name + "'!");
            IBlockData state = meta >= 0 ? block.fromLegacyData(meta) : block.getBlockData();
            int id = Block.REGISTRY_ID.getId(state);
            if (id < 0) id = (Block.getId(block) << 4) | block.toLegacyData(state);
            result = (char) id;
        }
        result = sanitize(result);
        PARSED.put(s, result);
        return result;
    }

    private static char chisel(String name) {
        unknownBlocks++;
        if (name.endsWith("_gray") && !name.endsWith("lightgray")) return c(251, 7);
        if (name.endsWith("lightgray")) return c(251, 8);
        if (name.endsWith("_white")) return c(251, 0);
        return c(251, 0);
    }

    /**
     * Owner rule (CONVENTIONS.md "Block valuables policy"): structures are never built from valuable mineral
     * blocks. The mod's '!' palette (diamond/gold/emerald/iron blocks) becomes ordinary blocks of the same hue.
     * command_block is the mod's hard-air marker and is resolved before placement; as a last resort it is air.
     */
    static char sanitize(char c) {
        switch (c >> 4) {
            case 41: return c(159, 4);   // gold block -> yellow terracotta
            case 42: return c(43, 8);    // iron block -> smooth stone double slab
            case 57: return c(251, 3);   // diamond block -> light blue concrete
            case 133: return c(251, 5);  // emerald block -> lime concrete
            case 22: return c(251, 11);  // lapis block -> blue concrete
            case 152: return c(251, 14); // redstone block -> red concrete
            case 173: return c(251, 15); // coal block -> black concrete
            case 138: return c(169, 0);  // beacon -> sea lantern
            case 14: case 15: case 16: case 21: case 56: case 73: case 74: case 129: return STONE;  // ores as decor
            case 153: return c(87, 0);   // quartz ore -> netherrack
            default: return c;
        }
    }

    static boolean valuable(int id) {
        return id == 41 || id == 42 || id == 57 || id == 133 || id == 22 || id == 152 || id == 173 || id == 138;
    }

    /** Block ids that carry a tile entity (chests, spawners, furnaces, pots, ...). */
    private static final boolean[] TILE = new boolean[4096];
    private static boolean tileInit;
    static boolean tile(char c) {
        if (!tileInit) {
            synchronized (B.class) {
                if (!tileInit) {
                    for (int id = 0; id < 4096; id++) {
                        try { Block b = Block.getById(id); TILE[id] = b != null && b.isTileEntity() && id != 0; }
                        catch (Throwable t) { TILE[id] = false; }
                    }
                    tileInit = true;
                }
            }
        }
        return TILE[c >> 4];
    }

    private static final IBlockData[] STATES = new IBlockData[65536];
    static IBlockData state(char c) {
        IBlockData s = STATES[c];
        if (s == null) {
            Block b = Block.getById(c >> 4);
            s = b.fromLegacyData(c & 15);
            STATES[c] = s;
        }
        return s;
    }

    static char of(IBlockData s) {
        int id = Block.REGISTRY_ID.getId(s);
        if (id < 0) id = (Block.getId(s.getBlock()) << 4) | s.getBlock().toLegacyData(s);
        return (char) id;
    }

    // ---------------------------------------------------------------- the mod's char sets

    static boolean isRail(char c) { int id = c >> 4; return id == 66 || id == 27; }

    static boolean isGlass(char c) { int id = c >> 4; return id == 20 || id == 95 || id == 102 || id == 160; }

    /** Stairs of the listed kinds and ladders (LostCitiesTerrainGenerator.getRotatableChars). */
    static boolean isRotatable(char c) {
        switch (c >> 4) {
            case 163: case 135: case 108: case 156: case 109: case 164: case 136: case 114: case 53:
            case 203: case 180: case 128: case 134: case 67: case 65:
                return true;
            default:
                return false;
        }
    }

    /** Saplings (meta 0-5 at stage 0) and default flowers (getCharactersNeedingTodo). */
    static boolean needsTodo(char c) {
        int id = c >> 4, m = c & 15;
        return (id == 6 && m <= 5) || c == c(38, 0) || c == c(37, 0);
    }

    // Rotation.CLOCKWISE_90 on horizontal facings: N->E->S->W->N. Encoded 0=N,1=E,2=S,3=W.
    private static final int[] STAIR_META_TO_DIR = {1, 3, 2, 0};   // meta 0 EAST, 1 WEST, 2 SOUTH, 3 NORTH
    private static final int[] DIR_TO_STAIR_META = {3, 0, 2, 1};

    /** IBlockState.withRotation(rot) for the rotatable chars; rot: 0 none, 1 cw90, 2 cw180, 3 ccw90. */
    static char rotate(char c, int rot) {
        if (rot == 0) return c;
        int id = c >> 4, m = c & 15;
        if (id == 65) {  // ladder: 2 N, 3 S, 4 W, 5 E
            int d;
            switch (m) { case 2: d = 0; break; case 3: d = 2; break; case 4: d = 3; break; case 5: d = 1; break; default: return c; }
            d = (d + rot) & 3;
            int[] toMeta = {2, 5, 3, 4};
            return c(id, toMeta[d]);
        }
        int d = STAIR_META_TO_DIR[m & 3];
        d = (d + rot) & 3;
        return c(id, (m & 4) | DIR_TO_STAIR_META[d]);
    }
}
