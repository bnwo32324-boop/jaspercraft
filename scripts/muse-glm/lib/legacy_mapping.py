"""Deterministic Java 1.12.2 block-state conversion, without network access.

Public API: convert_state(state) -> (numeric_id, metadata, explanation).
read_legacy(path) -> (ids, metadata), both NumPy arrays indexed [x, y, z].
Unknown names fail closed. Every approximation/property loss is explained.
No block entity NBT is used or returned by the conversion API.
"""
from __future__ import annotations

import json
import re
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "vendor"))
import nbtlib

REGISTRY = json.loads((Path(__file__).parent / "vendor" / "minecraft-data-1.12-blocks.json").read_text())
LEGACY_NAMES = {int(b["id"]): b["name"] for b in REGISTRY}
LEGACY_IDS = {v: k for k, v in LEGACY_NAMES.items()}
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
WOODS = "oak spruce birch jungle acacia dark_oak".split()
WOOD_ALIASES = {"cherry": "birch", "pale_oak": "birch", "mangrove": "acacia", "crimson": "dark_oak", "warped": "jungle", "bamboo": "birch", "bamboo_mosaic": "birch"}
FACING6 = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
SOUTH4 = {"south": 0, "west": 1, "north": 2, "east": 3}
# Verified against bundled Paper BlockTrapdoor.fromLegacyData, not attached-face.
TRAPDOOR = {"north": 0, "south": 1, "west": 2, "east": 3}
STAIRS = {"oak": 53, "spruce": 134, "birch": 135, "jungle": 136, "acacia": 163, "dark_oak": 164,
          "cobblestone": 67, "brick": 108, "stone_brick": 109, "nether_brick": 114, "sandstone": 128,
          "quartz": 156, "red_sandstone": 180, "purpur": 203}
SLABS = {"smooth_stone": (44, 43, 0), "stone": (44, 43, 0), "sandstone": (44, 43, 1),
         "petrified_oak": (44, 43, 2), "cobblestone": (44, 43, 3), "brick": (44, 43, 4),
         "stone_brick": (44, 43, 5), "nether_brick": (44, 43, 6), "quartz": (44, 43, 7),
         "red_sandstone": (182, 181, 0), "purpur": (205, 204, 0)}
MATERIAL_ALIASES = {
    "smooth_stone": "stone_brick",
    "andesite": "stone_brick", "polished_andesite": "stone_brick", "granite": "brick", "polished_granite": "brick",
    "diorite": "quartz", "polished_diorite": "quartz", "smooth_quartz": "quartz", "smooth_sandstone": "sandstone",
    "cut_sandstone": "sandstone", "smooth_red_sandstone": "red_sandstone", "cut_red_sandstone": "red_sandstone",
    "mossy_cobblestone": "cobblestone", "mossy_stone_brick": "stone_brick", "end_stone_brick": "sandstone",
    "prismarine": "stone_brick", "prismarine_brick": "stone_brick", "dark_prismarine": "nether_brick",
    "blackstone": "cobblestone", "polished_blackstone": "nether_brick", "polished_blackstone_brick": "nether_brick",
    "cobbled_deepslate": "cobblestone", "deepslate_brick": "stone_brick", "deepslate_tile": "nether_brick",
    "polished_deepslate": "stone_brick", "tuff": "cobblestone", "polished_tuff": "stone_brick", "tuff_brick": "stone_brick",
    "mud_brick": "brick", "red_nether_brick": "nether_brick", "resin_brick": "brick",
}
# Full-cube material substitutions. Partial blocks are dispatched before this table.
APPROX = {
    # Creator-built downloads occasionally contain post-1.12 details. These
    # substitutions are confined to the derived integration assets; originals
    # stay untouched. In particular, thousands of ancient-debris blocks must
    # not become mineable treasure in a generated structure.
    "ancient_debris": (159, 12), "conduit": (169, 0),
    "respawn_anchor": (49, 0), "muddy_mangrove_roots": (159, 12),
    "crafter": (58, 0), "warped_nylium": (159, 9),
    "crimson_nylium": (159, 14), "sculk_catalyst": (168, 2),
    "nether_gold_ore": (87, 0), "map": (35, 0),
    "deepslate": (1, 5), "cobbled_deepslate": (4, 0), "polished_deepslate": (1, 6),
    "deepslate_bricks": (98, 0), "cracked_deepslate_bricks": (98, 2), "deepslate_tiles": (112, 0),
    "cracked_deepslate_tiles": (112, 0), "chiseled_deepslate": (98, 3), "reinforced_deepslate": (7, 0),
    "blackstone": (4, 0), "gilded_blackstone": (112, 0), "polished_blackstone": (1, 6),
    "polished_blackstone_bricks": (112, 0), "cracked_polished_blackstone_bricks": (112, 0),
    "chiseled_polished_blackstone": (98, 3), "basalt": (1, 5), "polished_basalt": (1, 6), "smooth_basalt": (1, 6),
    "tuff": (1, 5), "polished_tuff": (1, 6), "tuff_bricks": (98, 0), "chiseled_tuff": (98, 3), "chiseled_tuff_bricks": (98, 3),
    "calcite": (1, 3), "dripstone_block": (1, 1), "mud": (159, 12), "packed_mud": (172, 0), "mud_bricks": (45, 0),
    "rooted_dirt": (3, 0), "moss_block": (159, 13), "pale_moss_block": (159, 8), "dried_kelp_block": (159, 13),
    "soul_soil": (88, 0), "crying_obsidian": (49, 0), "netherite_block": (173, 0), "amethyst_block": (159, 10), "warped_wart_block": (159, 9),
    "budding_amethyst": (159, 10), "sculk": (159, 9), "lodestone": (98, 3), "target": (170, 0),
    "raw_copper_block": (159, 1), "raw_gold_block": (41, 0), "raw_iron_block": (42, 0), "copper_ore": (15, 0),
    "honey_block": (165, 0), "honeycomb_block": (159, 4), "beehive": (5, 0), "bee_nest": (5, 2),
    "chiseled_nether_bricks": (112, 0), "chiseled_resin_bricks": (179, 1), "resin_block": (159, 1), "resin_bricks": (45, 0),
    "quartz_bricks": (155, 0), "smooth_quartz": (155, 0), "smooth_stone": (43, 8),
    "blue_ice": (174, 0), "powder_snow": (80, 0), "tinted_glass": (95, 15),
    "suspicious_sand": (12, 0), "suspicious_gravel": (13, 0),
    "shroomlight": (89, 0), "ochre_froglight": (89, 0), "verdant_froglight": (169, 0), "pearlescent_froglight": (169, 0),
    "cartography_table": (58, 0), "fletching_table": (58, 0), "smithing_table": (58, 0), "loom": (58, 0),
    "chiseled_bookshelf": (47, 0), "composter": (118, 0), "polished_sulfur": (159, 4), "potent_sulfur": (159, 4),
}
EXACT = {
    "nether_quartz_ore": (153, 0), "cracked_nether_bricks": (112, 0),
    "brick": (45, 0),
    "grass_block": (2, 0), "coarse_dirt": (3, 1), "podzol": (3, 2), "granite": (1, 1), "polished_granite": (1, 2),
    "diorite": (1, 3), "polished_diorite": (1, 4), "andesite": (1, 5), "polished_andesite": (1, 6), "red_sand": (12, 1),
    "wet_sponge": (19, 1), "bricks": (45, 0), "cobweb": (30, 0), "dead_bush": (32, 0), "dandelion": (37, 0),
    "stone_bricks": (98, 0), "mossy_stone_bricks": (98, 1), "cracked_stone_bricks": (98, 2), "chiseled_stone_bricks": (98, 3),
    "chiseled_sandstone": (24, 1), "cut_sandstone": (24, 2), "smooth_sandstone": (43, 9),
    "chiseled_red_sandstone": (179, 1), "cut_red_sandstone": (179, 2), "smooth_red_sandstone": (181, 8),
    "chiseled_quartz_block": (155, 1), "prismarine_bricks": (168, 1), "dark_prismarine": (168, 2),
    "nether_bricks": (112, 0), "red_nether_bricks": (215, 0), "end_stone_bricks": (206, 0),
    "terracotta": (172, 0), "melon": (103, 0), "lily_pad": (111, 0), "slime_block": (165, 0),
    "magma_block": (213, 0), "dirt_path": (208, 0), "grass_path": (208, 0), "snow_block": (80, 0),
}
FLOWERS = "poppy blue_orchid allium azure_bluet red_tulip orange_tulip white_tulip pink_tulip oxeye_daisy".split()
DOUBLES = {"sunflower": 0, "lilac": 1, "tall_grass": 2, "large_fern": 3, "rose_bush": 4, "peony": 5, "pitcher_plant": 5, "small_dripleaf": 2}
SAFETY = {
    36: (1, 0, "moving-piston payload removed; stable stone replacement"),
    46: (159, 14, "TNT neutralized as red terracotta"),
    51: (50, 5, "uncontrolled fire replaced by a non-spreading torch"),
    52: (101, 0, "native spawner replaced by inert iron bars; no default pig spawner"),
    90: (95, 10, "active portal replaced by purple stained glass"),
    119: (95, 15, "active end portal replaced by black stained glass"),
    120: (44, 1, "portal frame replaced by inert sandstone slab; portal activation removed; 13/16 height approximated by 1/2"),
    130: (54, 2, "ender chest replaced by empty ordinary chest; no player ender inventory access"),
    137: (98, 3, "command block and command payload removed; chiseled stone bricks"),
    166: (20, 0, "invisible barrier replaced by visible transparent glass"),
    209: (95, 15, "active gateway replaced by black stained glass"),
    210: (98, 3, "repeating command block and command payload removed; chiseled stone bricks"),
    211: (98, 3, "chain command block and command payload removed; chiseled stone bricks"),
    217: (0, 0, "structure-void sentinel omitted; never treated as source room air"),
    255: (98, 0, "structure block and payload removed; stone bricks"),
}
FORBIDDEN_IDS = set(SAFETY) | {97}


def read_legacy(path):
    """Decode original Blocks + Data + AddBlocks losslessly; arrays use [x,y,z].

    This function does NOT sanitize; it exposes the original states for validation.
    Invalid metadata >15 is rejected rather than silently masked.
    """
    root = nbtlib.load(Path(path))
    r = root.get("Schematic", root)
    x, y, z = (int(r[k]) for k in ("Width", "Height", "Length"))
    if min(x, y, z) <= 0:
        raise ValueError("Non-positive legacy dimensions")
    ids = np.asarray(r["Blocks"]).astype(np.uint8).astype(np.uint16)
    meta = np.asarray(r["Data"]).astype(np.uint8)
    if ids.size != x*y*z or meta.size != ids.size or np.any(meta > 15):
        raise ValueError("Invalid legacy Blocks/Data length or metadata")
    if "AddBlocks" in r:
        extra = np.asarray(r["AddBlocks"]).astype(np.uint8)
        if extra.size != (ids.size + 1)//2:
            raise ValueError("Invalid AddBlocks length")
        ids[0::2] |= (extra & 15).astype(np.uint16) << 8
        ids[1::2] |= (extra[:ids[1::2].size] >> 4).astype(np.uint16) << 8
    return tuple(a.reshape(y, z, x).transpose(2, 0, 1) for a in (ids, meta))


def sanitize_legacy(block_id, meta):
    """Preserve valid original numeric states, except explicit safety replacements."""
    block_id, meta = int(block_id), int(meta)
    if block_id not in LEGACY_NAMES or not 0 <= meta <= 15:
        raise ValueError(f"Not a registered 1.12.2 state: {block_id}:{meta}")
    if block_id in SAFETY:
        return SAFETY[block_id]
    if block_id == 97:
        return [(1, 0), (4, 0), (98, 0), (98, 1), (98, 2), (98, 3)][min(meta, 5)] + ("silverfish infestation removed",)
    if block_id == 117:
        return block_id, 0, "brewing bottle display cleared with inventory"
    if block_id == 84:
        return block_id, 0, "record display cleared with inventory"
    if block_id == 26 and meta & 4:
        return block_id, meta & ~4, "bed occupancy cleared; decorative color NBT stripped"
    reason = "exact legacy ID/Data preserved"
    if block_id in {26, 140, 144, 176, 177}:
        reason += "; decorative block-entity color/type/text/contents removed; 1.12 defaults apply"
    return block_id, meta, reason


def parse_state(state):
    match = re.fullmatch(r"(?:minecraft:)?([a-z0-9_]+)(?:\[([^\]]*)\])?", str(state))
    if not match:
        raise ValueError(f"Invalid or non-vanilla block state: {state!r}")
    props = {}
    for part in (match[2] or "").split(","):
        if part:
            k, v = part.split("=", 1)
            if k in props:
                raise ValueError(f"Duplicate property in {state}")
            props[k] = v
    return match[1], props


@lru_cache(maxsize=None)
def convert_state(state):
    """Return (legacyId, meta, reason), retaining orientation and partial shape.

    'exact' means the name/properties can be represented by an ID/Data state.
    Neighbor-computed shapes and block-entity properties are separately reported.
    A name outside known explicit families raises ValueError (no silent fallback).
    """
    name, p = parse_state(state)
    used, notes = set(), []

    def prop(k, default):
        used.add(k)
        return p.get(k, default)

    def flag(k):
        return prop(k, "false") == "true"

    def number(k, default=0, maximum=15):
        value = int(prop(k, str(default)))
        if not 0 <= value <= maximum:
            raise ValueError(f"Invalid {k}={value} in {state}")
        return value

    def facing(table=FACING6, default="north"):
        value = prop("facing", default)
        if value not in table:
            raise ValueError(f"Invalid facing in {state}")
        return table[value]

    def axis():
        return {"y": 0, "x": 4, "z": 8}[prop("axis", "y")]

    def finish(b, d=0, reason=None):
        if reason:
            notes.append(reason)
        if b in FORBIDDEN_IDS:
            b, d, why = sanitize_legacy(b, d)
            notes.append(why)
        if "waterlogged" in p:
            used.add("waterlogged")
            if p["waterlogged"] == "true":
                notes.append("waterlogging lost: 1.12 cannot share fluid and partial block")
        for k in sorted(p.keys() - used):
            if k in {"north", "south", "east", "west", "up", "down", "shape", "in_wall", "distance", "snowy"}:
                notes.append(f"{k}={p[k]} is neighbor-derived/not independently serialized in 1.12")
            else:
                notes.append(f"property {k}={p[k]} not represented in 1.12 metadata")
        if b not in LEGACY_NAMES or not 0 <= d <= 15 or b in FORBIDDEN_IDS:
            raise AssertionError((state, b, d))
        return b, d, "; ".join(notes) or "exact"

    if name in {"air", "cave_air", "void_air"}:
        return finish(0, reason=None if name == "air" else "special source air normalized to legacy air")
    if name == "__reserved__":
        raise ValueError("Reserved palette entry is not a real block")
    if name == "iron_wall":
        return finish(101, 0, "non-vanilla iron wall replaced by iron bars; shape differs")
    if name == "structure_void":
        return finish(217)
    if name in {"spawner", "trial_spawner", "vault"}:
        return finish(52, reason="native encounter functionality removed")
    if name == "jigsaw":
        return finish(98, 0, "jigsaw generation controller replaced by stone bricks")
    if name in LEGACY_IDS and LEGACY_IDS[name] in SAFETY:
        return finish(LEGACY_IDS[name])
    if name in {"fire", "soul_fire"}:
        return finish(51)
    if name in {"nether_portal", "end_portal", "end_gateway"}:
        return finish({"nether_portal": 90, "end_portal": 119, "end_gateway": 209}[name])
    if name.startswith("infested_"):
        target = name.removeprefix("infested_")
        b, d, why = convert_state("minecraft:" + target)
        return finish(b, d, "silverfish infestation removed" + ("; " + why if why != "exact" else ""))

    # Every wood family uses the same directional legacy grammar.
    wood_name = name.removeprefix("stripped_")
    stripped = wood_name != name
    for wood in sorted(WOODS + list(WOOD_ALIASES), key=len, reverse=True):
        if not wood_name.startswith(wood + "_"):
            continue
        kind = wood_name[len(wood)+1:]
        if kind not in {"log", "wood", "stem", "hyphae", "block", "planks", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "sign", "wall_sign", "hanging_sign", "wall_hanging_sign", "leaves", "sapling", "roots", "shelf"}:
            continue
        original = wood
        wood = WOOD_ALIASES.get(wood, wood)
        w = WOODS.index(wood)
        if wood != original:
            notes.append(f"post-1.12 {original} wood replaced by nearest {wood} family; shape retained")
        if stripped:
            notes.append("stripped texture unavailable; original bark texture retained with axis")
        if kind in {"log", "wood", "stem", "hyphae", "block"}:
            a = axis()
            return finish(17 if w < 4 else 162, w % 4 + (12 if kind in {"wood", "hyphae"} else a))
        if kind == "planks":
            return finish(5, w)
        if kind == "stairs":
            return finish(STAIRS[wood], facing({"east": 0, "west": 1, "south": 2, "north": 3}) + (4 if prop("half", "bottom") == "top" else 0))
        if kind == "slab":
            t = prop("type", "bottom")
            return finish(125 if t == "double" else 126, w + (8 if t == "top" else 0))
        if kind == "fence":
            return finish([85, 188, 189, 190, 192, 191][w])
        if kind == "fence_gate":
            return finish([107, 183, 184, 185, 187, 186][w], facing(SOUTH4) + (4 if flag("open") else 0) + (8 if flag("powered") else 0))
        if kind == "door":
            b = [64, 193, 194, 195, 196, 197][w]
            if prop("half", "lower") == "upper":
                d = 8 + (1 if prop("hinge", "left") == "right" else 0) + (2 if flag("powered") else 0)
                used.update({"facing", "open"})
            else:
                d = facing({"east": 0, "south": 1, "west": 2, "north": 3}) + (4 if flag("open") else 0)
                used.update({"hinge", "powered"})
            return finish(b, d)
        if kind == "trapdoor":
            return finish(96, facing(TRAPDOOR) + (4 if flag("open") else 0) + (8 if prop("half", "bottom") == "top" else 0), None if wood == "oak" else f"{wood} trapdoor texture replaced by oak")
        if kind == "button":
            f = prop("face", "wall")
            d = facing({"east": 1, "west": 2, "south": 3, "north": 4})
            return finish(143, (0 if f == "ceiling" else 5 if f == "floor" else d) + (8 if flag("powered") else 0), None if wood == "oak" else "wooden button texture unified to oak")
        if kind == "pressure_plate":
            return finish(72, int(flag("powered")), None if wood == "oak" else "wooden pressure-plate texture unified to oak")
        if kind in {"sign", "wall_sign", "hanging_sign", "wall_hanging_sign"}:
            wall = "wall" in kind
            return finish(68 if wall else 63, facing() if wall else number("rotation"), "sign text stripped; legacy oak sign; hanging hardware lost" if "hanging" in kind else "sign text stripped; legacy oak sign")
        if kind == "leaves":
            # Persist leaves in a staged structure, including unattached decorative foliage.
            persistent = prop("persistent", "false")
            return finish(18 if w < 4 else 161, w % 4 + 4, None if persistent == "true" else "leaf decay disabled for stable imported decoration")
        if kind == "sapling":
            return finish(6, w + 8 * number("stage", 0, 1))
        if kind == "roots":
            return finish(85, 0, "decorative roots replaced by narrow oak fence")
        if kind == "shelf":
            return finish(96, facing(TRAPDOOR) + 8, "shelf replaced by top wooden trapdoor; storage stripped; slab-thickness decoration")

    # Colored blocks must be handled before generic suffix families.
    for color, c in zip(COLORS, range(16)):
        if not name.startswith(color + "_"):
            continue
        kind = name[len(color)+1:]
        if kind in {"wool", "stained_glass", "stained_glass_pane", "terracotta", "carpet", "concrete", "concrete_powder"}:
            return finish({"wool": 35, "stained_glass": 95, "stained_glass_pane": 160, "terracotta": 159, "carpet": 171, "concrete": 251, "concrete_powder": 252}[kind], c)
        if kind == "glazed_terracotta":
            return finish(235 + c, facing(SOUTH4))
        if kind == "shulker_box":
            return finish(219 + c, facing(default="up"), "empty shulker box; inventory/loot table stripped")
        if kind in {"banner", "wall_banner"}:
            return finish(177 if kind == "wall_banner" else 176, facing() if kind == "wall_banner" else number("rotation"), f"banner {color} base color and patterns require NBT; removed; 1.12 default black banner")
        if kind == "bed":
            return finish(26, facing(SOUTH4) + (8 if prop("part", "foot") == "head" else 0), f"bed color {color} requires tile NBT; removed; 1.12 default red; occupancy cleared")
        if kind == "candle":
            return finish(50, 5, f"{color} candle replaced by non-full standing torch; count/wax color lost")
        if kind == "candle_cake":
            return finish(92, 0, f"{color} candle cake replaced by ordinary cake")

    # Shape-preserving stone/copper substitutions, deliberately never full cubes.
    for suffix in ("_stairs", "_slab", "_wall"):
        if not name.endswith(suffix):
            continue
        material = name[:-len(suffix)]
        mapped = MATERIAL_ALIASES.get(material, material)
        if suffix == "_stairs" and material == "stone":
            mapped = "cobblestone"
        if "copper" in material:
            mapped = "stone_brick" if any(t in material for t in ("oxidized", "weathered")) else "brick"
        if mapped != material:
            notes.append(f"{material}{suffix} uses nearest {mapped} legacy shape/material")
        if suffix == "_stairs" and mapped in STAIRS:
            return finish(STAIRS[mapped], facing({"east": 0, "west": 1, "south": 2, "north": 3}) + (4 if prop("half", "bottom") == "top" else 0))
        if suffix == "_slab" and mapped in SLABS:
            single, double, d = SLABS[mapped]
            t = prop("type", "bottom")
            return finish(double if t == "double" else single, d + (8 if t == "top" else 0))
        if suffix == "_wall" and (mapped in STAIRS or material in {"mossy_cobblestone", "stone_brick", "nether_brick"}):
            return finish(139, int(material.startswith("mossy")), None if material in {"cobblestone", "mossy_cobblestone"} else "wall shape retained with cobblestone texture; connections recomputed")

    if "copper" in name or name.endswith("lightning_rod"):
        if name.endswith("_bars"):
            return finish(101, 0, "copper bars replaced by iron bars; transparency retained")
        if name.endswith("_trapdoor"):
            return finish(167, facing(TRAPDOOR) + (4 if flag("open") else 0) + (8 if prop("half", "bottom") == "top" else 0), "copper trapdoor replaced by iron trapdoor")
        if name.endswith("_door"):
            b, d, reason = convert_state("minecraft:iron_door" + ("[" + ",".join(f"{k}={v}" for k, v in p.items()) + "]" if p else ""))
            used.update(p)
            return finish(b, d, "copper door replaced by iron door; " + reason)
        if name.endswith("_chest"):
            return finish(54, facing(), "copper chest replaced by empty ordinary chest")
        if name.endswith("_golem_statue"):
            return finish(85, 0, "copper statue replaced by narrow wooden post; no golem entity")
        if name.endswith("lightning_rod"):
            return finish(198, facing(default="up"), "lightning rod replaced by narrow end rod; no lightning behavior")
        if name.endswith("_bulb"):
            return finish(124 if flag("lit") else 123, 0, "copper bulb replaced by legacy redstone lamp")
        if name.endswith("_grate"):
            return finish(101, 0, "copper grate replaced by transparent iron bars")
        if name in APPROX:
            return finish(*APPROX[name], f"{name} replaced by nearest legacy ore/storage material")
        if any(t in name for t in ("oxidized", "weathered")):
            return finish(168, 0, "aged copper replaced by teal prismarine")
        return finish(159, 12 if "exposed" in name else 1, "copper replaced by similar-color terracotta")

    if name.startswith("deepslate_") and name.endswith("_ore"):
        b, d, _ = convert_state(name.removeprefix("deepslate_"))
        return finish(b, d, "deepslate ore replaced by ordinary legacy ore")
    if name in APPROX:
        return finish(*APPROX[name], f"post-1.12 {name} replaced by {LEGACY_NAMES[APPROX[name][0]]}:{APPROX[name][1]}")
    if name in EXACT:
        return finish(*EXACT[name])
    if name in FLOWERS:
        return finish(38, FLOWERS.index(name))
    if name in DOUBLES:
        return finish(175, 8 if prop("half", "lower") == "upper" else DOUBLES[name], "modern tall plant replaced by legacy double plant" if name in {"pitcher_plant", "small_dripleaf"} else None)
    if name in {"short_grass", "grass", "fern"}:
        return finish(31, 2 if name == "fern" else 1)
    if name in {"azalea_leaves", "flowering_azalea_leaves"}:
        return finish(18, 4, "azalea foliage replaced by persistent oak leaves; flowers lost")
    if name in {"azalea", "flowering_azalea", "bush", "firefly_bush", "sweet_berry_bush"}:
        return finish(31, 2, "modern shrub replaced by non-full fern; special behavior lost")
    if name in {"cornflower", "lily_of_the_valley", "torchflower", "wither_rose", "warped_fungus", "crimson_fungus", "spore_blossom"}:
        return finish(38, {"cornflower": 1, "lily_of_the_valley": 3, "torchflower": 5, "wither_rose": 0, "warped_fungus": 1, "crimson_fungus": 0, "spore_blossom": 2}[name], "modern flower/fungus replaced by non-full legacy flower")
    if name.startswith("potted_") or name == "flower_pot":
        return finish(140, 0, "empty flower pot; plant contents require block-entity NBT and are stripped")
    if name in {"moss_carpet", "pale_moss_carpet", "pink_petals", "leaf_litter"}:
        return finish(171, {"moss_carpet": 13, "pale_moss_carpet": 8, "pink_petals": 6, "leaf_litter": 12}[name], "thin ground decoration replaced by matching carpet")
    if name in {"chain", "iron_chain"}:
        a = prop("axis", "y")
        return finish(198, {"y": 0, "x": 4, "z": 2}[a], "chain replaced by axis-aligned narrow end rod; adds light")
    if name in {"lantern", "soul_lantern"}:
        return finish(198, 0 if flag("hanging") else 1, "lantern replaced by attached non-full end rod; light/color differs")
    if name in {"torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch"}:
        d = facing({"east": 1, "west": 2, "south": 3, "north": 4}) if "wall" in name else 5
        b = (76 if prop("lit", "true") == "true" else 75) if "redstone" in name else 50
        return finish(b, d, "soul flame color unavailable" if "soul" in name else None)
    if name in {"candle", "sea_pickle", "turtle_egg", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud", "pointed_dripstone", "light", "bell"}:
        if name == "candle":
            return finish(50, 5, "candle replaced by standing torch; wax/count lost")
        d = facing(default="up") if "facing" in p else (0 if prop("vertical_direction", "up") == "down" else 1)
        return finish(198, d, f"{name} replaced by narrow non-full end rod; shape/light/mechanic approximated")
    if name in {"campfire", "soul_campfire", "stonecutter", "grindstone", "lectern"}:
        return finish(126 if "campfire" in name or name == "lectern" else 44, 1 if "campfire" in name else 0, f"{name} replaced by non-full lower slab; processing/fire/inventory stripped")
    if name == "decorated_pot":
        return finish(140, 0, "decorated pot replaced by small empty flower pot; contents/patterns stripped")
    if name in {"calibrated_sculk_sensor", "sculk_sensor", "sculk_shrieker"}:
        return finish(44, 0, "sculk device replaced by inert lower stone slab; no vibration/warden behavior")
    if name in {"bamboo", "bamboo_sapling", "scaffolding", "big_dripleaf", "big_dripleaf_stem"}:
        return finish(85, 0, "modern stalk/platform replaced by non-full oak fence; growth/platform behavior lost")
    if name == "bamboo_mosaic":
        return finish(5, 2, "bamboo mosaic replaced by birch planks")
    if name in {"hanging_roots", "cave_vines", "cave_vines_plant", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant"}:
        return finish(106, 0, "hanging plant replaced by non-full vine; fruit/light/growth differs")
    if name in {"glow_lichen", "sculk_vein", "vine"}:
        d = sum(bit for key, bit in (("south", 1), ("west", 2), ("north", 4), ("east", 8)) if flag(key))
        return finish(106, d, None if name == "vine" else "surface growth replaced by attached vine; light/mechanics lost")
    if name in {"seagrass", "tall_seagrass", "kelp", "kelp_plant", "bubble_column"} or "coral" in name:
        if name.endswith("_block"):
            return finish(1, 5, "coral block replaced by andesite")
        return finish(9, 0, "aquatic non-solid plant/fan/column replaced by water; decoration/bubble motion lost")
    if name in {"water", "lava", "flowing_water", "flowing_lava"}:
        level = number("level")
        return finish((8 if level else 9) if "water" in name else (10 if level else 11), level)
    if name in {"barrel", "chest", "trapped_chest", "ender_chest"}:
        f = prop("facing", "north")
        d = FACING6.get(f, 2)
        if d < 2:
            d = 2
            notes.append("vertical container facing replaced by north-facing chest")
        return finish(146 if name == "trapped_chest" else 54, d, "empty chest; inventory/loot table stripped" + (f"; {name} replaced by ordinary chest" if name in {"barrel", "ender_chest"} else ""))
    if name == "shulker_box":
        return finish(229, facing(default="up"), "uncolored shulker replaced by empty purple shulker; contents stripped")
    if name in {"furnace", "blast_furnace", "smoker"}:
        return finish(62 if flag("lit") else 61, facing(), "inventory stripped" + (f"; {name} uses legacy furnace" if name != "furnace" else ""))
    if name in {"dispenser", "dropper", "observer", "piston", "sticky_piston", "piston_head", "end_rod"}:
        b = {"dispenser": 23, "dropper": 158, "observer": 218, "piston": 33, "sticky_piston": 29, "piston_head": 34, "end_rod": 198}[name]
        d = facing()
        if name in {"piston", "sticky_piston"}:
            d += 8 * flag("extended")
        elif name == "piston_head":
            d += 8 * (prop("type", "normal") == "sticky")
        elif name in {"dispenser", "dropper", "observer"}:
            d += 8 * flag("powered" if name == "observer" else "triggered")
        return finish(b, d, "inventory stripped" if name in {"dispenser", "dropper"} else None)
    if name in {"iron_door", "iron_trapdoor"}:
        b, d, why = convert_state(state.replace(name, "oak_door" if name == "iron_door" else "oak_trapdoor", 1))
        used.update(p)
        return finish(71 if name == "iron_door" else 167, d, None if why == "exact" else why)
    if name in {"ladder", "wall_sign"}:
        return finish(65 if name == "ladder" else 68, facing(), "text stripped" if name == "wall_sign" else None)
    if name in {"stone_button", "polished_blackstone_button"}:
        f = prop("face", "wall")
        d = facing({"east": 1, "west": 2, "south": 3, "north": 4})
        return finish(77, (0 if f == "ceiling" else 5 if f == "floor" else d) + 8 * flag("powered"), "blackstone replaced by stone" if "blackstone" in name else None)
    if name in {"stone_pressure_plate", "polished_blackstone_pressure_plate"}:
        return finish(70, int(flag("powered")), "blackstone replaced by stone" if "blackstone" in name else None)
    if name in {"light_weighted_pressure_plate", "heavy_weighted_pressure_plate"}:
        return finish(147 if name.startswith("light") else 148, number("power"))
    if name == "lever":
        f = prop("face", "wall")
        direction = prop("facing", "north")
        d = {"east": 1, "west": 2, "south": 3, "north": 4}[direction] if f == "wall" else (6 if direction in {"east", "west"} else 5) if f == "floor" else (0 if direction in {"east", "west"} else 7)
        return finish(69, d + 8 * flag("powered"))
    if name in {"rail", "powered_rail", "detector_rail", "activator_rail"}:
        shapes = "north_south east_west ascending_east ascending_west ascending_north ascending_south south_east south_west north_west north_east".split()
        d = shapes.index(prop("shape", "north_south"))
        b = {"rail": 66, "powered_rail": 27, "detector_rail": 28, "activator_rail": 157}[name]
        if b != 66:
            if d > 5:
                raise ValueError(f"Invalid curved powered rail: {state}")
            d += 8 * flag("powered")
        return finish(b, d)
    if name in {"repeater", "comparator"}:
        powered = flag("powered")
        d = facing(SOUTH4)
        if name == "repeater":
            return finish(94 if powered else 93, d + 4 * (number("delay", 1, 4) - 1))
        return finish(150 if powered else 149, d + 4 * (prop("mode", "compare") == "subtract") + 8 * powered)
    if name == "tripwire_hook":
        return finish(131, facing(SOUTH4) + 4 * flag("attached") + 8 * flag("powered"))
    if name == "tripwire":
        return finish(132, int(flag("powered")) + 4 * flag("attached") + 8 * flag("disarmed"))
    if name == "redstone_wire":
        return finish(55, number("power"))
    if name in {"redstone_lamp", "redstone_ore"}:
        return finish((124 if flag("lit") else 123) if name.endswith("lamp") else (74 if flag("lit") else 73))
    if name == "daylight_detector":
        return finish(178 if flag("inverted") else 151, number("power"))
    if name in {"hay_block", "bone_block", "purpur_pillar", "quartz_pillar"}:
        a = axis()
        return finish({"hay_block": 170, "bone_block": 216, "purpur_pillar": 202, "quartz_pillar": 155}[name], {0: 2, 4: 3, 8: 4}[a] if name == "quartz_pillar" else a)
    if name in {"anvil", "chipped_anvil", "damaged_anvil"}:
        return finish(145, facing(SOUTH4) + 4 * {"anvil": 0, "chipped_anvil": 1, "damaged_anvil": 2}[name])
    if name in {"pumpkin", "carved_pumpkin", "jack_o_lantern"}:
        return finish(91 if name == "jack_o_lantern" else 86, facing(SOUTH4), "uncarved pumpkin gains legacy carved face" if name == "pumpkin" else None)
    if name in {"wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cactus", "sugar_cane", "pumpkin_stem", "melon_stem"}:
        b = {"wheat": 59, "carrots": 141, "potatoes": 142, "beetroots": 207, "nether_wart": 115, "cactus": 81, "sugar_cane": 83, "pumpkin_stem": 104, "melon_stem": 105}[name]
        return finish(b, number("age", 0, 3 if name in {"beetroots", "nether_wart"} else 15 if name in {"cactus", "sugar_cane"} else 7))
    if name in {"attached_pumpkin_stem", "attached_melon_stem"}:
        return finish(104 if "pumpkin" in name else 105, 7, "stem attachment direction is neighbor-derived in 1.12")
    if name == "cocoa":
        return finish(127, facing(SOUTH4) + 4 * number("age", 0, 2))
    if name == "farmland":
        return finish(60, number("moisture", 0, 7))
    if name == "snow":
        return finish(78, number("layers", 1, 8) - 1)
    if name == "cake":
        return finish(92, number("bites", 0, 6))
    if name == "hopper":
        return finish(154, facing(default="down") + (0 if prop("enabled", "true") == "true" else 8), "empty hopper; inventory/loot table stripped")
    if name == "brewing_stand":
        return finish(117, 0, "bottle inventory and displayed bottles cleared")
    if name in {"cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron"}:
        return finish(118, number("level", 1, 3) if name == "water_cauldron" else 0, "non-water cauldron contents removed" if name in {"lava_cauldron", "powder_snow_cauldron"} else None)
    if name == "note_block":
        return finish(25, 0, "note/instrument data requires NBT; stripped; default note")
    if name == "jukebox":
        return finish(84, 0, "record/inventory stripped")
    if name.endswith(("_head", "_skull")):
        wall = "wall" in name
        if not wall:
            used.add("rotation")
        return finish(144, facing() if wall else 1, "skull type/profile/floor rotation requires NBT; stripped; default skeleton skull")
    if name in {"brown_mushroom_block", "red_mushroom_block", "mushroom_stem"}:
        sides = {k: flag(k) for k in ("north", "south", "east", "west", "up", "down")}
        if name == "mushroom_stem":
            d = 15 if all(sides.values()) else 10
        elif all(sides.values()):
            d = 14
        elif not any(sides.values()):
            d = 0
        else:
            d = (0 if sides["north"] else 2 if sides["south"] else 1)*3 + (0 if sides["west"] else 2 if sides["east"] else 1) + 1
            notes.append("mushroom face mask approximated by nearest legacy cap metadata")
        return finish(100 if name == "red_mushroom_block" else 99, d)
    if name in LEGACY_IDS:
        return finish(LEGACY_IDS[name])
    raise ValueError(f"Unmapped block name: {state}; add an explicit shape/material rule")
