"""Build optimized, read-only inspection models from the frozen 45-file snapshot.

The script never reads from or writes to JasperCraft or the active extraction
workspace. Inputs are copied snapshot files under snapshot/source; outputs stay
under dist. Geometry is a diagnostic full-cube voxel rendering intended for
crop and interior inspection, not a Minecraft block-model renderer.
"""

from __future__ import annotations

import hashlib
import json
import math
import struct
import sys
from array import array
from collections import Counter
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / "snapshot"
SOURCE = SNAPSHOT / "source"
DIST = ROOT / "dist"
MODELS = DIST / "models"
DATA = DIST / "data"

sys.path.insert(0, str(Path(__file__).parent / "vendor"))
import litemapy  # noqa: E402
import nbtlib  # noqa: E402


AIR_NAMES = {
    "minecraft:air",
    "minecraft:cave_air",
    "minecraft:void_air",
    "minecraft:structure_void",
}

COLOR_WORDS = {
    "white": (224, 222, 211), "orange": (220, 130, 58),
    "magenta": (174, 78, 159), "light_blue": (104, 151, 199),
    "yellow": (238, 194, 48), "lime": (112, 185, 26),
    "pink": (215, 112, 143), "gray": (68, 73, 76),
    "light_gray": (147, 147, 137), "cyan": (22, 135, 145),
    "purple": (118, 42, 143), "blue": (49, 55, 155),
    "brown": (109, 76, 39), "green": (78, 103, 43),
    "red": (161, 39, 34), "black": (29, 30, 34),
}


def safe_output(path: Path) -> Path:
    resolved = path.resolve()
    if ROOT.resolve() not in resolved.parents and resolved != ROOT.resolve():
        raise ValueError(f"Refusing output outside viewer root: {resolved}")
    return resolved


def state_base(state: str) -> str:
    return state.split("[", 1)[0].lower()


def color_for_state(state: str) -> tuple[int, int, int, bool]:
    base = state_base(state).split(":", 1)[-1]
    transparent = any(word in base for word in ("glass", "water", "ice"))

    for prefix, color in COLOR_WORDS.items():
        if base == prefix or base.startswith(prefix + "_"):
            if "glass" in base:
                return (*color, True)
            if any(word in base for word in ("concrete", "wool", "terracotta", "carpet", "banner", "bed")):
                return (*color, transparent)

    rules = (
        (("water",), (48, 105, 176)),
        (("lava", "magma"), (235, 91, 28)),
        (("gold", "honey", "hay"), (232, 184, 49)),
        (("diamond", "prismarine", "warped"), (54, 168, 164)),
        (("emerald",), (45, 185, 83)),
        (("redstone",), (176, 34, 34)),
        (("copper",), (185, 108, 72)),
        (("iron", "quartz", "calcite", "snow", "bone"), (205, 205, 196)),
        (("deepslate", "blackstone", "bedrock", "coal"), (52, 54, 59)),
        (("stone", "cobble", "andesite", "gravel", "tuff"), (119, 121, 119)),
        (("diorite",), (190, 188, 180)),
        (("granite",), (148, 96, 76)),
        (("sandstone", "sand"), (210, 190, 132)),
        (("brick", "terracotta"), (151, 82, 65)),
        (("netherrack", "nether_brick", "crimson"), (103, 48, 53)),
        (("end_stone",), (215, 220, 157)),
        (("grass", "leaves", "moss", "vine", "cactus", "bamboo"), (76, 126, 62)),
        (("dirt", "mud", "podzol", "mycelium"), (124, 91, 58)),
        (("dark_oak",), (70, 48, 30)),
        (("spruce",), (96, 69, 42)),
        (("birch",), (197, 178, 124)),
        (("acacia",), (165, 91, 55)),
        (("jungle",), (141, 99, 57)),
        (("mangrove",), (115, 53, 50)),
        (("oak", "wood", "planks", "log"), (145, 108, 64)),
        (("torch", "lantern", "glow", "light", "sea_lantern"), (242, 193, 83)),
        (("glass", "ice"), (151, 194, 206)),
    )
    for words, color in rules:
        if any(word in base for word in words):
            return (*color, transparent)

    digest = hashlib.blake2b(base.encode("utf-8"), digest_size=3).digest()
    average = sum(digest) / 3
    muted = tuple(int(average * 0.55 + channel * 0.45) for channel in digest)
    return (*muted, transparent)


def decode_varints(raw, volume: int) -> np.ndarray:
    ids = np.empty(volume, dtype=np.int32)
    index = value = shift = 0
    for byte in np.asarray(raw, dtype=np.uint8):
        byte = int(byte)
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            if shift > 28:
                raise ValueError("Invalid palette varint")
        else:
            if index >= volume:
                raise ValueError("Too many palette indexes")
            ids[index] = value
            index += 1
            value = shift = 0
    if index != volume or shift:
        raise ValueError(f"Expected {volume} palette indexes; got {index}")
    return ids


def load_litematic(path: Path):
    root = nbtlib.load(path)
    metadata = root["Metadata"]
    for key, default in (("Author", "Unknown"), ("Name", path.stem), ("Description", "")):
        if key not in metadata:
            metadata[key] = nbtlib.String(default)
    for region in root["Regions"].values():
        for key in ("PendingBlockTicks", "PendingFluidTicks"):
            if key not in region:
                region[key] = nbtlib.List[nbtlib.Compound]()
    schematic = litemapy.Schematic.from_nbt(root)
    if len(schematic.regions) != 1:
        raise ValueError(f"Expected one region, found {len(schematic.regions)}")
    region = next(iter(schematic.regions.values()))
    names = [str(block) for block in region.palette]
    cube_yzx = np.transpose(region._Region__blocks, (1, 2, 0)).astype(np.int32)
    tile_entities = sum(len(r.get("TileEntities", [])) for r in root["Regions"].values())
    entities = sum(len(r.get("Entities", [])) for r in root["Regions"].values())
    return np.transpose(cube_yzx, (2, 0, 1)), names, "Litematica", tile_entities, entities


def load_sponge(path: Path):
    root = nbtlib.load(path)
    schematic = root.get("Schematic", root)
    width, height, length = [int(schematic[key]) for key in ("Width", "Height", "Length")]
    volume = width * height * length
    if "Blocks" in schematic:
        block_root = schematic["Blocks"]
        palette = block_root["Palette"]
        raw = block_root["Data"]
        tile_entities = len(block_root.get("BlockEntities", []))
        fmt = "Sponge v3"
    else:
        palette = schematic["Palette"]
        raw = schematic["BlockData"]
        tile_entities = len(schematic.get("BlockEntities", schematic.get("TileEntities", [])))
        fmt = "Sponge v2"
    names = [None] * (max(int(value) for value in palette.values()) + 1)
    for name, value in palette.items():
        names[int(value)] = str(name)
    ids = decode_varints(raw, volume)
    if int(ids.max()) >= len(names):
        raise ValueError("Palette index out of range")
    cube_yzx = ids.reshape((height, length, width))
    return np.transpose(cube_yzx, (2, 0, 1)), names, fmt, tile_entities, len(schematic.get("Entities", []))


def load_legacy(path: Path):
    root = nbtlib.load(path)
    schematic = root.get("Schematic", root)
    width, height, length = [int(schematic[key]) for key in ("Width", "Height", "Length")]
    volume = width * height * length
    ids = np.asarray(schematic["Blocks"], dtype=np.uint8).astype(np.uint16)
    data = np.asarray(schematic["Data"], dtype=np.uint8)
    if len(ids) != volume or len(data) != volume:
        raise ValueError("Legacy Blocks/Data length mismatch")
    if "AddBlocks" in schematic:
        extra = np.asarray(schematic["AddBlocks"], dtype=np.uint8)
        ids[0::2] |= (extra[: len(ids[0::2])] & 15).astype(np.uint16) << 8
        ids[1::2] |= (extra[: len(ids[1::2])] >> 4).astype(np.uint16) << 8
    mapping = json.loads((Path(__file__).parent / "vendor" / "minecraft-data-1.12-blocks.json").read_text(encoding="utf-8"))
    legacy_names = {int(item["id"]): item["name"] for item in mapping}
    dye = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
    colored = {"wool", "stained_glass", "stained_glass_pane", "carpet", "stained_hardened_clay", "concrete", "concrete_powder"}
    packed = ids.astype(np.int32) * 16 + (data & 15).astype(np.int32)
    unique, inverse = np.unique(packed, return_inverse=True)
    names = []
    for value in unique:
        block_id, variant = divmod(int(value), 16)
        name = legacy_names.get(block_id, f"unknown_legacy_{block_id}")
        if name in colored:
            name = f"{dye[variant]}_{name}"
        names.append("minecraft:" + name)
    cube_yzx = inverse.reshape((height, length, width)).astype(np.int32)
    return np.transpose(cube_yzx, (2, 0, 1)), names, "MCEdit legacy", len(schematic.get("TileEntities", [])), len(schematic.get("Entities", []))


def load_structure(path: Path):
    if path.suffix.lower() == ".litematic":
        return load_litematic(path)
    if path.suffix.lower() == ".schem":
        return load_sponge(path)
    if path.suffix.lower() == ".schematic":
        return load_legacy(path)
    raise ValueError(f"Unsupported format: {path.suffix}")


class MeshData:
    def __init__(self):
        self.positions = array("f")
        self.normals = array("f")
        self.colors = bytearray()
        self.indices = array("I")
        self.quads = 0

    def add_quad(self, points, axis: int, sign: int, color):
        start = len(self.positions) // 3
        normal = [0.0, 0.0, 0.0]
        normal[axis] = float(sign)
        shade = (0.78, 1.0, 0.87)[axis] * (1.0 if sign > 0 else 0.78)
        rgb = tuple(max(0, min(255, int(channel * shade))) for channel in color[:3])
        for point in points:
            self.positions.extend(point)
            self.normals.extend(normal)
            self.colors.extend(rgb)
        if sign > 0:
            order = (0, 1, 2, 0, 2, 3)
        else:
            order = (0, 3, 2, 0, 2, 1)
        self.indices.extend(start + offset for offset in order)
        self.quads += 1


def greedy_mesh(blocks: np.ndarray, names: list[str]):
    bases = [state_base(name) for name in names]
    solid_palette = np.asarray([base not in AIR_NAMES for base in bases], dtype=bool)
    occupied = solid_palette[blocks]
    visual_keys = [color_for_state(name) for name in names]
    unique_visuals = []
    visual_lookup = {}
    palette_visual = []
    for visual in visual_keys:
        if visual not in visual_lookup:
            visual_lookup[visual] = len(unique_visuals)
            unique_visuals.append(visual)
        palette_visual.append(visual_lookup[visual])
    palette_visual = np.asarray(palette_visual, dtype=np.int32)

    opaque = MeshData()
    translucent = MeshData()
    dims = blocks.shape

    for axis in range(3):
        u = (axis + 1) % 3
        v = (axis + 2) % 3
        natural_axes = [value for value in range(3) if value != axis]
        for plane in range(dims[axis] + 1):
            if plane == 0:
                right_ids = np.take(blocks, 0, axis=axis)
                right_solid = np.take(occupied, 0, axis=axis)
                mask = np.where(right_solid, -(palette_visual[right_ids] + 1), 0)
            elif plane == dims[axis]:
                left_ids = np.take(blocks, plane - 1, axis=axis)
                left_solid = np.take(occupied, plane - 1, axis=axis)
                mask = np.where(left_solid, palette_visual[left_ids] + 1, 0)
            else:
                left_ids = np.take(blocks, plane - 1, axis=axis)
                right_ids = np.take(blocks, plane, axis=axis)
                left_solid = np.take(occupied, plane - 1, axis=axis)
                right_solid = np.take(occupied, plane, axis=axis)
                boundary = left_solid != right_solid
                mask = np.where(boundary, np.where(left_solid, palette_visual[left_ids] + 1, -(palette_visual[right_ids] + 1)), 0)

            if natural_axes != [u, v]:
                mask = mask.T
            else:
                mask = mask.copy()
            size_u, size_v = mask.shape

            for start_v in range(size_v):
                start_u = 0
                while start_u < size_u:
                    key = int(mask[start_u, start_v])
                    if key == 0:
                        start_u += 1
                        continue
                    width = 1
                    while start_u + width < size_u and int(mask[start_u + width, start_v]) == key:
                        width += 1
                    height = 1
                    while start_v + height < size_v and np.all(mask[start_u:start_u + width, start_v + height] == key):
                        height += 1
                    mask[start_u:start_u + width, start_v:start_v + height] = 0

                    sign = 1 if key > 0 else -1
                    visual = unique_visuals[abs(key) - 1]
                    base = [0.0, 0.0, 0.0]
                    base[axis] = float(plane)
                    base[u] = float(start_u)
                    base[v] = float(start_v)
                    du = [0.0, 0.0, 0.0]
                    dv = [0.0, 0.0, 0.0]
                    du[u] = float(width)
                    dv[v] = float(height)
                    points = (
                        tuple(base),
                        tuple(base[i] + du[i] for i in range(3)),
                        tuple(base[i] + du[i] + dv[i] for i in range(3)),
                        tuple(base[i] + dv[i] for i in range(3)),
                    )
                    target = translucent if visual[3] else opaque
                    target.add_quad(points, axis, sign, visual)
                    start_u += width
    return opaque, translucent, occupied


def align4(data: bytearray):
    while len(data) % 4:
        data.append(0)


def native_bytes(values: array) -> bytes:
    if sys.byteorder != "little":
        values = array(values.typecode, values)
        values.byteswap()
    return values.tobytes()


def write_glb(path: Path, opaque: MeshData, translucent: MeshData, name: str):
    binary = bytearray()
    buffer_views = []
    accessors = []
    primitives = []
    materials = [
        {
            "name": "Opaque voxels",
            "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1], "metallicFactor": 0, "roughnessFactor": 1},
        },
        {
            "name": "Translucent voxels",
            "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 0.48], "metallicFactor": 0, "roughnessFactor": 1},
            "alphaMode": "BLEND",
            "doubleSided": True,
        },
    ]

    def append_view(raw: bytes, target: int):
        align4(binary)
        offset = len(binary)
        binary.extend(raw)
        index = len(buffer_views)
        buffer_views.append({"buffer": 0, "byteOffset": offset, "byteLength": len(raw), "target": target})
        return index

    def append_accessor(view: int, component_type: int, count: int, kind: str, **extra):
        item = {"bufferView": view, "componentType": component_type, "count": count, "type": kind}
        item.update(extra)
        index = len(accessors)
        accessors.append(item)
        return index

    for material_index, mesh in enumerate((opaque, translucent)):
        if not mesh.indices:
            continue
        count = len(mesh.positions) // 3
        positions_np = np.frombuffer(native_bytes(mesh.positions), dtype="<f4").reshape((-1, 3))
        pos_view = append_view(native_bytes(mesh.positions), 34962)
        normal_view = append_view(native_bytes(mesh.normals), 34962)
        color_view = append_view(bytes(mesh.colors), 34962)
        index_view = append_view(native_bytes(mesh.indices), 34963)
        pos_accessor = append_accessor(
            pos_view, 5126, count, "VEC3",
            min=positions_np.min(axis=0).astype(float).tolist(),
            max=positions_np.max(axis=0).astype(float).tolist(),
        )
        normal_accessor = append_accessor(normal_view, 5126, count, "VEC3")
        color_accessor = append_accessor(color_view, 5121, count, "VEC3", normalized=True)
        index_accessor = append_accessor(index_view, 5125, len(mesh.indices), "SCALAR")
        primitives.append({
            "attributes": {"POSITION": pos_accessor, "NORMAL": normal_accessor, "COLOR_0": color_accessor},
            "indices": index_accessor,
            "material": material_index,
        })

    gltf = {
        "asset": {"version": "2.0", "generator": "Jasper structure inspection greedy voxel mesher"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": name}],
        "meshes": [{"name": name, "primitives": primitives}],
        "materials": materials,
        "buffers": [{"byteLength": len(binary)}],
        "bufferViews": buffer_views,
        "accessors": accessors,
    }
    json_bytes = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    while len(json_bytes) % 4:
        json_bytes += b" "
    align4(binary)
    total = 12 + 8 + len(json_bytes) + 8 + len(binary)
    payload = bytearray(struct.pack("<4sII", b"glTF", 2, total))
    payload.extend(struct.pack("<I4s", len(json_bytes), b"JSON"))
    payload.extend(json_bytes)
    payload.extend(struct.pack("<I4s", len(binary), b"BIN\x00"))
    payload.extend(binary)
    safe_output(path).write_bytes(payload)


def main():
    snapshot = json.loads((SNAPSHOT / "snapshot.json").read_text(encoding="utf-8"))
    if len(snapshot["structures"]) != 45:
        raise ValueError("Snapshot must contain exactly 45 structures")
    MODELS.mkdir(parents=True, exist_ok=True)
    DATA.mkdir(parents=True, exist_ok=True)
    catalog = []
    checksums = {}

    for index, entry in enumerate(snapshot["structures"], start=1):
        source = SOURCE / entry["file"]
        if not source.is_file():
            raise FileNotFoundError(source)
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        blocks, names, source_format, tile_entities, entities = load_structure(source)
        opaque, translucent, occupied = greedy_mesh(blocks, names)
        locations = np.argwhere(occupied)
        low = locations.min(axis=0)
        high = locations.max(axis=0)
        counts = Counter(names[int(value)] for value in blocks[occupied])
        model_path = MODELS / f"{entry['id']}.glb"
        write_glb(model_path, opaque, translucent, f"{entry['id']} — {entry['title']}")
        row = dict(entry)
        row.update({
            "bay": index,
            "model": f"models/{entry['id']}.glb",
            "preview": f"previews/{entry['id']}.png",
            "sha256": digest,
            "sourceFormat": source_format,
            "dimensions": {"x": int(blocks.shape[0]), "y": int(blocks.shape[1]), "z": int(blocks.shape[2])},
            "occupiedBounds": {
                "min": {"x": int(low[0]), "y": int(low[1]), "z": int(low[2])},
                "max": {"x": int(high[0]), "y": int(high[1]), "z": int(high[2])},
            },
            "blockCount": int(occupied.sum()),
            "paletteSize": len(names),
            "blockEntities": int(tile_entities),
            "entities": int(entities),
            "quads": int(opaque.quads + translucent.quads),
            "modelBytes": model_path.stat().st_size,
            "topBlocks": [{"state": state, "count": int(count)} for state, count in counts.most_common(8)],
        })
        catalog.append(row)
        checksums[entry["file"]] = digest
        print(f"[{index:02d}/45] {entry['id']} {entry['title']}: {row['blockCount']:,} blocks -> {row['quads']:,} quads")

    output = {
        "snapshotId": snapshot["snapshotId"],
        "description": snapshot["description"],
        "count": len(catalog),
        "baySize": 620,
        "columns": 7,
        "structures": catalog,
    }
    safe_output(DATA / "catalog.json").write_text(json.dumps(output, indent=2), encoding="utf-8")
    safe_output(SNAPSHOT / "checksums.json").write_text(json.dumps(checksums, indent=2), encoding="utf-8")
    print(f"Built {len(catalog)} isolated inspection models in {MODELS}")


if __name__ == "__main__":
    main()
