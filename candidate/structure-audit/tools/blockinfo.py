"""Per-id block properties for Minecraft 1.12.2 used by analyze.py and render.py.

All tables are numpy lookup arrays indexed by block id (0..4095) so they can be applied to whole
dumps at once:  PASSABLE[ids], OPAQUE[ids], LIGHT[ids] ...
"""
import numpy as np

N = 4096


def _arr(ids, dtype=bool, value=True, default=None):
    a = np.zeros(N, dtype=dtype)
    if default is not None:
        a[:] = default
    for i in ids:
        a[i] = value
    return a


AIR = 0
WATER = {8, 9}
LAVA = {10, 11}
LIQUID = WATER | LAVA

# no collision box: the player can occupy the cell (feet or head)
NO_COLLISION = {0, 6, 8, 9, 27, 28, 30, 31, 32, 36, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 66, 68, 69, 70, 72, 75, 76,
                77, 78, 83, 90, 104, 105, 106, 115, 119, 131, 132, 141, 142, 143, 147, 148, 157, 171, 175, 176, 177,
                207, 209, 217}
DOORS = {64, 71, 193, 194, 195, 196, 197}
TRAPDOORS = {96, 167}
GATES = {107, 183, 184, 185, 186, 187}
OPENABLE = DOORS | TRAPDOORS | GATES
CLIMBABLE = {65, 106}
LADDER = 65
VINE = 106
STAIRS = {53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180, 203}
SLABS = {44, 126, 182, 205}
DOUBLE_SLABS = {43, 125, 181, 204}
FENCES = {85, 113, 188, 189, 190, 191, 192}
WALLS = {139}
PANES = {101, 102, 160}
TALL = FENCES | WALLS | GATES          # 1.5-high collision: cannot be stepped/jumped onto
# low blocks the player walks onto without jumping (<= 0.5625 high)
LOW = {26, 92, 93, 94, 111, 140, 144, 149, 150, 151, 178} | SLABS  # slabs: only bottom halves (checked by data)

CHESTS = {54, 146}
SPAWNER = 52
BEDS = {26}
FUNCTIONAL = {23, 25, 26, 54, 58, 61, 62, 84, 116, 117, 118, 130, 138, 145, 146, 154, 158} | set(range(219, 235))
DECOR = {30, 47, 63, 68, 69, 70, 72, 77, 92, 140, 143, 144, 147, 148, 171, 176, 177, 120, 122, 131, 132, 93, 94, 149,
         150, 151, 178, 37, 38, 39, 40, 31, 32, 175, 6, 83, 111, 199, 200, 198}
LIGHTS = {50: 14, 51: 15, 62: 13, 74: 9, 76: 7, 89: 15, 90: 11, 91: 15, 10: 15, 11: 15, 119: 15, 124: 15, 130: 7,
          138: 15, 169: 15, 198: 14, 209: 15, 213: 3, 117: 1, 122: 1, 39: 1, 94: 9, 150: 9}
FURNISHING = FUNCTIONAL | DECOR          # counted for room furnishing density (lights counted separately)

# full, light-blocking cubes (for light propagation and render culling)
NON_OPAQUE = NO_COLLISION | DOORS | TRAPDOORS | GATES | STAIRS | SLABS | FENCES | WALLS | PANES | {
    18, 161, 20, 95, 52, 54, 146, 130, 26, 60, 64, 71, 79, 81, 88, 89, 92, 93, 94, 111, 116, 117, 118, 120, 122, 127,
    138, 140, 144, 145, 149, 150, 151, 154, 165, 166, 169, 171, 174, 178, 198, 199, 200, 208, 212, 29, 33, 34, 212,
    79, 90, 20, 213, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 124, 123,
    89, 169, 62, 91, 47}
# NB: glowstone/sea lantern/lamps are opaque in MC but emit; we keep them opaque below
for _i in (89, 169, 123, 124, 62, 91, 47, 29, 33, 218, 88, 60, 208):
    NON_OPAQUE.discard(_i)
for _i in range(219, 235):
    NON_OPAQUE.discard(_i)

VALUABLE = {41: "gold block", 42: "iron block", 57: "diamond block", 133: "emerald block", 22: "lapis block",
            152: "redstone block", 173: "coal block", 138: "beacon",
            14: "gold ore", 15: "iron ore", 16: "coal ore", 21: "lapis ore", 56: "diamond ore", 73: "redstone ore",
            74: "lit redstone ore", 129: "emerald ore", 153: "quartz ore"}
VALUABLE_REPLACEMENT = {41: "251:4 yellow concrete (or 159:4)", 42: "43:8 smooth stone / 251:0 white concrete",
                        57: "251:3 light blue concrete / 168:1 prismarine bricks", 133: "251:5 lime concrete",
                        22: "251:11 blue concrete", 152: "251:14 red concrete (if powering: self-lit block)",
                        173: "251:15 black concrete", 138: "169 sea lantern",
                        14: "1:0 stone", 15: "1:0 stone", 16: "1:0 stone", 21: "1:0 stone", 56: "1:0 stone",
                        73: "1:0 stone", 74: "1:0 stone", 129: "1:0 stone", 153: "87 netherrack"}
WORKSTATIONS = {145: "anvil", 116: "enchanting table", 117: "brewing stand", 130: "ender chest"}
REDSTONE_CONSUMERS = {123, 124, 29, 33, 64, 71, 193, 194, 195, 196, 197, 96, 167, 23, 158, 154, 25, 46, 27, 157,
                      107, 183, 184, 185, 186, 187, 93, 94, 149, 150, 55}

PASSABLE = _arr(NO_COLLISION | OPENABLE)          # player can be in the cell (doors etc. opened)
PASSABLE[10] = PASSABLE[11] = False                # lava: deadly
COLLIDE = ~_arr(NO_COLLISION)                      # has a collision box (supports standing)
COLLIDE[0] = False
OPAQUE = ~_arr(NON_OPAQUE)
OPAQUE[0] = False
IS_LIQUID = _arr(LIQUID)
IS_WATER = _arr(WATER)
IS_CLIMB = _arr(CLIMBABLE)
IS_TALL = _arr(TALL)
IS_STAIRS = _arr(STAIRS)
IS_SLAB = _arr(SLABS)
IS_LOWISH = _arr(LOW | STAIRS)
IS_DOOR = _arr(DOORS)
IS_OPENABLE = _arr(OPENABLE)
IS_FURNISH = _arr(FURNISHING)
IS_FUNCTIONAL = _arr(FUNCTIONAL)
LIGHT = np.zeros(N, dtype=np.int8)
for _k, _v in LIGHTS.items():
    LIGHT[_k] = _v
# extra light attenuation when passing through (besides the normal 1 per step)
ATTEN = np.zeros(N, dtype=np.int8)
for _i in (8, 9, 79, 212):
    ATTEN[_i] = 2
for _i in (18, 161, 30):
    ATTEN[_i] = 0
IS_VALUABLE = _arr(VALUABLE.keys())
# "solid" for structure connectivity: every placed block except air and liquids and fire
IS_SOLID = np.ones(N, dtype=bool)
IS_SOLID[0] = False
for _i in LIQUID | {51}:
    IS_SOLID[_i] = False

STRUCTURAL_FULL = OPAQUE.copy()   # rough "full block" notion

INTERACT_KINDS = {54: "chest", 146: "trapped chest", 52: "spawner", 26: "bed", 58: "crafting table",
                  61: "furnace", 62: "furnace", 145: "anvil", 116: "enchanting table", 117: "brewing stand",
                  130: "ender chest", 118: "cauldron", 84: "jukebox", 154: "hopper", 23: "dispenser", 158: "dropper"}
