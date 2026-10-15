package chat.jaspr.lairs;

/**
 * Block states as 1.12 legacy (id << 4 | data) and their rotation. Directions in the lair's local frame: N = -v, E = +u,
 * S = +v, W = -u; a rotation of r quarter turns clockwise (seen from above) maps N to E to S to W.
 */
final class M {
    private M() {}

    static int s(int id, int data) { return id << 4 | (data & 15); }
    static int id(int state) { return state >>> 4; }
    static int data(int state) { return state & 15; }

    static final int N = 0, E = 1, S = 2, W = 3;

    // ---- plain blocks
    static final int AIR = s(0, 0), STONE = s(1, 0), GRANITE = s(1, 1), DIORITE = s(1, 3), ANDESITE = s(1, 5),
        POL_ANDESITE = s(1, 6), POL_DIORITE = s(1, 4), POL_GRANITE = s(1, 2), GRASS = s(2, 0), DIRT = s(3, 0),
        COARSE = s(3, 1), PODZOL = s(3, 2), COBBLE = s(4, 0), OAK = s(5, 0), SPRUCE = s(5, 1), BIRCH = s(5, 2),
        JUNGLE = s(5, 3), ACACIA = s(5, 4), DARK_OAK = s(5, 5), SAND = s(12, 0), RED_SAND = s(12, 1), GRAVEL = s(13, 0),
        GLASS = s(20, 0), SANDSTONE = s(24, 0), CUT_SANDSTONE = s(24, 2), WEB = s(30, 0), FERN = s(31, 2),
        TALL_GRASS = s(31, 1), DEAD_BUSH = s(32, 0), MUSHROOM_BROWN = s(39, 0), MUSHROOM_RED = s(40, 0),
        STONE_SLAB = s(44, 0), STONE_SLAB_TOP = s(44, 8), COBBLE_SLAB = s(44, 3), COBBLE_SLAB_TOP = s(44, 11),
        BRICK_SLAB = s(44, 4), SB_SLAB = s(44, 5), SB_SLAB_TOP = s(44, 13), BRICKS = s(45, 0), BOOKSHELF = s(47, 0),
        MOSSY_COBBLE = s(48, 0), OBSIDIAN = s(49, 0), CRAFTING = s(58, 0), SNOW_LAYER = s(78, 0), CLAY = s(82, 0),
        FENCE = s(85, 0), SOUL_SAND = s(88, 0), GLOWSTONE = s(89, 0), SB = s(98, 0), MOSSY_SB = s(98, 1),
        CRACKED_SB = s(98, 2), CHISELED_SB = s(98, 3), IRON_BARS = s(101, 0), PANE = s(102, 0), MELON = s(103, 0),
        MYCELIUM = s(110, 0), NETHER_BRICK = s(112, 0), NB_FENCE = s(113, 0), CAULDRON = s(118, 0), END_STONE = s(121, 0),
        OAK_SLAB = s(126, 0), SPRUCE_SLAB = s(126, 1), DARK_OAK_SLAB = s(126, 5), DARK_OAK_SLAB_TOP = s(126, 13),
        SPRUCE_SLAB_TOP = s(126, 9), COBBLE_WALL = s(139, 0), MOSSY_WALL = s(139, 1), FLOWER_POT = s(140, 0),
        ANVIL = s(145, 0), QUARTZ = s(155, 0), CHISELED_QUARTZ = s(155, 1), TERRACOTTA = s(172, 0), HAY = s(170, 0),
        COAL_ORE = s(16, 0), PACKED_ICE = s(174, 0), RED_SANDSTONE = s(179, 0), SPRUCE_FENCE = s(188, 0),
        DARK_OAK_FENCE = s(191, 0), END_ROD_UP = s(198, 1), END_ROD_DOWN = s(198, 0), PURPUR = s(201, 0),
        PURPUR_PILLAR = s(202, 0), PURPUR_SLAB = s(205, 0), PURPUR_SLAB_TOP = s(205, 8), END_BRICKS = s(206, 0),
        GRASS_PATH = s(208, 0), MAGMA = s(213, 0), RED_NETHER_BRICK = s(215, 0), BONE = s(216, 0), BONE_X = s(216, 4),
        BONE_Z = s(216, 8), PUMPKIN = s(86, 0), JACK = s(91, 0), LADDER = s(65, 3), CHEST = s(54, 3), SPAWNER = s(52, 0),
        SIGN = s(63, 0), WALL_SIGN = s(68, 3), TORCH = s(50, 5), HOPPER = s(154, 0), BREWING = s(117, 0),
        IRON_TRAPDOOR = s(167, 0), TRAPDOOR = s(96, 0), RAIL_NS = s(66, 0), RAIL_EW = s(66, 1), SKULL = s(144, 1),
        VINE_N = s(106, 4), NETHERRACK = s(87, 0), ICE = s(79, 0), SNOW = s(80, 0), FURNACE = s(61, 3),
        LOG_OAK = s(17, 0), LOG_SPRUCE = s(17, 1), LOG_BIRCH = s(17, 2), LOG_DARK = s(162, 1), LOG_DARK_X = s(162, 5),
        LOG_DARK_Z = s(162, 9), LOG_SPRUCE_X = s(17, 5), LOG_SPRUCE_Z = s(17, 9), LOG_OAK_X = s(17, 4), LOG_OAK_Z = s(17, 8),
        BARK_DARK = s(162, 13), BARK_SPRUCE = s(17, 13), LEAVES_OAK = s(18, 4), LEAVES_SPRUCE = s(18, 5),
        LEAVES_DARK = s(161, 5), LEAVES_JUNGLE = s(18, 7), RED_MUSH_BLOCK = s(100, 14), BROWN_MUSH_BLOCK = s(99, 14),
        MUSH_STEM = s(99, 10), CHORUS_PLANT = s(199, 0), CHORUS_FLOWER = s(200, 0), DOUBLE_SLAB = s(43, 0),
        SMOOTH_STONE = s(43, 8), NETHER_WART_BLOCK = s(214, 0), PRISMARINE_DARK = s(168, 2), SEA_LANTERN = s(169, 0),
        REDSTONE_LAMP = s(123, 0), STONE_BUTTON = s(77, 1), LEVER = s(69, 5), JUKEBOX = s(84, 0), NOTE = s(25, 0),
        DISPENSER = s(23, 1), CARPET_RED = s(171, 14), CARPET_BLACK = s(171, 15), CARPET_GRAY = s(171, 7),
        CARPET_BROWN = s(171, 12), CARPET_PURPLE = s(171, 10), RED_FLOWER = s(38, 0), BLUE_ORCHID = s(38, 1),
        ALLIUM = s(38, 2), AZURE = s(38, 3), OXEYE = s(38, 8), DANDELION = s(37, 0), LILY = s(111, 0), REEDS = s(83, 0),
        CACTUS = s(81, 0), BEETROOT = s(207, 3), WHEAT = s(59, 7), FARMLAND = s(60, 7), WATER = s(9, 0), LAVA = s(11, 0),
        OBSERVER = s(218, 1), SLIME = s(165, 0);

    static int wool(int color) { return s(35, color); }
    static int clay(int color) { return s(159, color); }
    static int glass(int color) { return s(95, color); }
    static int pane(int color) { return s(160, color); }
    static int concrete(int color) { return s(251, color); }
    static int powder(int color) { return s(252, color); }
    static int carpet(int color) { return s(171, color); }

    static final int WHITE = 0, ORANGE = 1, MAGENTA = 2, LIGHT_BLUE = 3, YELLOW = 4, LIME = 5, PINK = 6, GRAY = 7,
        SILVER = 8, CYAN = 9, PURPLE = 10, BLUE = 11, BROWN = 12, GREEN = 13, RED = 14, BLACK = 15;

    // ---- directional blocks (dir is N/E/S/W in the local frame)
    /** Stairs ascending toward dir (the full back of the step faces dir); upside-down when top. */
    static int stairs(int id, int dir, boolean top) { return s(id, STAIRS[dir] | (top ? 4 : 0)); }
    static final int OAK_STAIRS = 53, COBBLE_STAIRS = 67, BRICK_STAIRS = 108, SB_STAIRS = 109, NB_STAIRS = 114,
        SANDSTONE_STAIRS = 128, SPRUCE_STAIRS = 134, BIRCH_STAIRS = 135, QUARTZ_STAIRS = 156, DARK_OAK_STAIRS = 164,
        PURPUR_STAIRS = 203, RED_SANDSTONE_STAIRS = 180, ACACIA_STAIRS = 163;
    /** A torch on the wall, pointing toward dir (attached to the block on the opposite side). */
    static int wallTorch(int dir) { return s(50, TORCH_DIR[dir]); }
    /** Ladder / wall sign / chest / furnace / end rod (side) facing dir. */
    static int ladder(int dir) { return s(65, CHESTLIKE[dir]); }
    static int wallSign(int dir) { return s(68, CHESTLIKE[dir]); }
    static int chest(int dir) { return s(54, CHESTLIKE[dir]); }
    static int furnace(int dir) { return s(61, CHESTLIKE[dir]); }
    static int endRod(int dir) { return s(198, CHESTLIKE[dir]); }
    static int pumpkin(int dir) { return s(86, PUMPKIN_DIR[dir]); }
    static int jack(int dir) { return s(91, PUMPKIN_DIR[dir]); }
    static int gate(int id, int dir, boolean open) { return s(id, PUMPKIN_DIR[dir] | (open ? 4 : 0)); }
    static int skullWall(int dir) { return s(144, CHESTLIKE[dir]); }
    /** Logs, bone, hay, purpur pillars along an axis: 0 = y, 1 = x (u), 2 = z (v). */
    static int axis(int state, int axis) {
        int id = id(state), d = data(state) & 3;
        if (id == 155) return s(155, axis == 0 ? 2 : axis == 1 ? 3 : 4);
        return s(id, d | (axis == 0 ? 0 : axis == 1 ? 4 : 8));
    }

    private static final int[] STAIRS = {3, 0, 2, 1};         // N, E, S, W -> 1.12 stairs facing
    private static final int[] TORCH_DIR = {4, 1, 3, 2};      // N, E, S, W -> torch data
    private static final int[] CHESTLIKE = {2, 5, 3, 4};      // N, E, S, W -> ladder / sign / chest data
    private static final int[] PUMPKIN_DIR = {2, 3, 0, 1};    // N, E, S, W -> pumpkin / gate data

    private static int index(int[] table, int value) { for (int i = 0; i < 4; i++) if (table[i] == value) return i; return -1; }

    /** The state turned r quarter turns clockwise. */
    static int rotate(int state, int r) {
        r &= 3;
        if (r == 0) return state;
        int id = id(state), d = data(state);
        switch (id) {
            case 53: case 67: case 108: case 109: case 114: case 128: case 134: case 135: case 136: case 156: case 163:
            case 164: case 180: case 203: {
                int dir = index(STAIRS, d & 3);
                return s(id, STAIRS[(dir + r) & 3] | (d & 4));
            }
            case 50: case 75: case 76: {
                int dir = index(TORCH_DIR, d);
                return dir < 0 ? state : s(id, TORCH_DIR[(dir + r) & 3]);
            }
            case 54: case 61: case 62: case 65: case 68: case 130: case 146: case 177: {
                int dir = index(CHESTLIKE, d & 7);
                return dir < 0 ? state : s(id, CHESTLIKE[(dir + r) & 3]);
            }
            case 23: case 158: case 198: case 218: case 144: {
                int low = d & 7, dir = index(CHESTLIKE, low);
                return dir < 0 ? state : s(id, (d & 8) | CHESTLIKE[(dir + r) & 3]);
            }
            case 86: case 91: case 107: case 183: case 184: case 185: case 186: case 187: case 145: {
                int dir = index(PUMPKIN_DIR, d & 3);
                return s(id, (d & ~3) | PUMPKIN_DIR[(dir + r) & 3]);
            }
            case 17: case 162: case 170: case 202: case 216: {
                if ((r & 1) == 0) return state;
                int a = d & 12, rest = d & 3;
                return s(id, rest | (a == 4 ? 8 : a == 8 ? 4 : a));
            }
            case 155: {
                if ((r & 1) == 0 || (d != 3 && d != 4)) return state;
                return s(id, d == 3 ? 4 : 3);
            }
            case 66: {
                if ((r & 1) == 0 || d > 1) return state;
                return s(id, d ^ 1);
            }
            case 63: return s(id, (d + 4 * r) & 15);
            case 106: {
                // vine bits: south 1, west 2, north 4, east 8 -> clockwise: north->east, east->south, south->west, west->north
                int out = d;
                for (int i = 0; i < r; i++) out = ((out & 4) != 0 ? 8 : 0) | ((out & 8) != 0 ? 1 : 0) | ((out & 1) != 0 ? 2 : 0) | ((out & 2) != 0 ? 4 : 0);
                return s(id, out);
            }
            case 96: case 167: {
                // trapdoor hinge side: 0 north, 1 south, 2 west, 3 east
                int[] t = {0, 3, 1, 2};   // N, E, S, W
                int dir = index(t, d & 3);
                return s(id, (d & 12) | t[(dir + r) & 3]);
            }
            default: return state;
        }
    }

    /** Ids a lair site must never contain before it is built: anything made, placed or crafted (see Director.scan). */
    static boolean built(int id) {
        switch (id) {
            case 4: case 5: case 19: case 20: case 22: case 23: case 25: case 26: case 27: case 28: case 29: case 30: case 33:
            case 34: case 35: case 36: case 41: case 42: case 43: case 44: case 45: case 46: case 47: case 48: case 49: case 50:
            case 51: case 52: case 53: case 54: case 55: case 57: case 58: case 60: case 61: case 62: case 63: case 64: case 65:
            case 66: case 67: case 68: case 69: case 70: case 71: case 72: case 75: case 76: case 77: case 84: case 85: case 89:
            case 90: case 91: case 92: case 93: case 94: case 95: case 96: case 97: case 98: case 101: case 102: case 107:
            case 108: case 109: case 112: case 113: case 114: case 116: case 117: case 118: case 119: case 120: case 121:
            case 122: case 123: case 124: case 125: case 126: case 128: case 130: case 131: case 132: case 133: case 134:
            case 135: case 136: case 137: case 138: case 139: case 140: case 143: case 144: case 145: case 146: case 147:
            case 148: case 149: case 150: case 151: case 152: case 154: case 155: case 156: case 157: case 158: case 160:
            case 163: case 164: case 165: case 166: case 167: case 168: case 169: case 170: case 171: case 173: case 176:
            case 177: case 178: case 180: case 181: case 182: case 183: case 184: case 185: case 186: case 187: case 188:
            case 189: case 190: case 191: case 192: case 193: case 194: case 195: case 196: case 197: case 198: case 199:
            case 200: case 201: case 202: case 203: case 204: case 205: case 206: case 208: case 209: case 210: case 211:
            case 214: case 215: case 216: case 217: case 218: case 255:
                return true;
            default:
                return id >= 219 && id <= 252;   // shulker boxes, glazed terracotta, concrete, concrete powder
        }
    }

    /** Ids that are the land itself (the surface a lair is levelled on). */
    static boolean ground(int id) {
        switch (id) {
            case 1: case 2: case 3: case 7: case 12: case 13: case 14: case 15: case 16: case 21: case 24: case 56: case 73:
            case 74: case 80: case 82: case 87: case 88: case 110: case 129: case 153: case 159: case 172: case 174: case 179:
            case 213: case 79:
                return true;
            default:
                return false;
        }
    }

    /** Surface materials a levelled pad may take its top from (else grass). */
    static boolean topsoil(int id) {
        switch (id) { case 2: case 3: case 12: case 13: case 82: case 110: case 159: case 172: case 80: case 87: case 88: return true; default: return false; }
    }
}
