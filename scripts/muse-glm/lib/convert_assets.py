"""Build and independently validate the isolated 45-file Paper 1.12.2 bundle.

Run: python -B tools/convert_assets.py
Test only: python -B tools/convert_assets.py --self-test
Rebuild in memory and verify existing outputs: ... --verify

Only writes assets/** and reports/conversion*. Inputs and the parent agent's
geometry/content files are never modified. Public coordinate convention is
[x,y,z], identical to source_reader. SE45 storage order is y,z,x (x fastest).
convert_state and read_legacy are re-exported for the parent's anchor checks.
"""
from __future__ import annotations

import argparse
from collections import Counter, deque
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import tempfile

sys.dont_write_bytecode = True
import numpy as np
from legacy_mapping import (convert_state, read_legacy, sanitize_legacy, parse_state,
                            LEGACY_NAMES, FORBIDDEN_IDS, nbtlib)
from source_reader import load_structure, AIR_NAMES

ROOT = Path(__file__).resolve().parents[1]
HEADER = struct.Struct(">4sIIII")
RECORD = np.dtype([("index", ">u4"), ("id", ">u2"), ("meta", "u1")], align=False)
assert HEADER.size == 20 and RECORD.itemsize == 7
TRUE_AIR = {"air", "cave_air", "void_air"}
# Blocks through which an air flood can travel; glass/panes, doors, fences,
# stairs/slabs and fluids form conservative voxel boundaries. Foliage cannot
# falsely enclose a room. Exact sub-voxel collision is not available in SE45.
FLOOD_PASSABLE = {0, 6, 18, 30, 31, 32, 37, 38, 39, 40, 50, 55, 59, 63, 65, 68,
                  69, 70, 72, 75, 76, 77, 78, 83, 93, 94, 104, 105, 106, 111,
                  115, 127, 131, 132, 140, 141, 142, 143, 144, 147, 148, 149,
                  150, 157, 161, 171, 175, 176, 177, 198, 207, 27, 28, 66}
# Strong roof/floor/wall evidence for externally-connected room recovery.
# Excludes vegetation, fluids, thin posts/panes, doors and all block entities.
ARCHITECTURAL = {1, 2, 3, 4, 5, 7, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22,
                 24, 35, 41, 42, 43, 44, 45, 47, 48, 49, 53, 56, 57, 58, 67,
                 73, 74, 79, 80, 82, 86, 87, 88, 89, 91, 95, 98, 99, 100,
                 103, 108, 109, 110, 112, 114, 121, 123, 124, 125, 126, 128,
                 129, 133, 134, 135, 136, 152, 153, 155, 156, 159, 162, 163,
                 164, 165, 168, 169, 170, 172, 173, 174, 179, 180, 181, 182,
                 201, 202, 203, 204, 205, 206, 208, 212, 213, 214, 215, 216,
                 *range(235, 253)}
AIR_POLICY = {
    "enclosed": "Six-connected boundary flood through air and passable decoration; retain all true source air unreachable from the six outer faces.",
    "openRooms": "Also retain exterior-connected true air with architectural roof and floor in the same column (span <=64), plus architectural walls within 48 cells in at least three horizontal directions. No cells are invented or moved.",
    "sentinels": "structure_void is omitted and never treated as true source air; non-air is never silently dropped.",
    "risk": "Open entrances can connect a room to outside. Roofed-room recovery is conservative and can include covered exterior galleries/courtyards. Large doorless/open-roof spaces can remain uncarved. Partial-block boundaries are voxel approximations; doors are flood barriers regardless of open metadata. Review reported externally connected air before world placement.",
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def output(path):
    path = path.resolve()
    if not (path.is_relative_to(ROOT / "assets") or
            (path.parent == ROOT / "reports" and path.name.startswith("conversion"))):
        raise ValueError(f"Outside converter write scope: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def write_json(path, value):
    output(path).write_text(json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def gzip_bytes(raw):
    # Filename and timestamp suppressed; same bytes in repeated local builds.
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, filename="", mode="wb", mtime=0, compresslevel=9) as f:
        f.write(raw)
    return buf.getvalue()


def source_payload_counts(path):
    """Count payloads without copying any source NBT into derived assets."""
    root = nbtlib.load(path)
    regions = list(root["Regions"].values()) if "Regions" in root else [root.get("Schematic", root)]
    tiles, entities, ticks, inventories, commands, spawners = 0, 0, 0, 0, 0, 0
    for r in regions:
        b = r.get("Blocks", r)
        if not isinstance(b, dict):
            b = r
        block_entities = b.get("BlockEntities", r.get("BlockEntities", r.get("TileEntities", [])))
        tiles += len(block_entities)
        entities += len(r.get("Entities", []))
        ticks += sum(len(r.get(k, [])) for k in ("TileTicks", "PendingBlockTicks", "PendingFluidTicks"))
        for entity in block_entities:
            data = entity.get("Data", entity)
            inventories += int(any(k in data for k in ("Items", "Inventory", "LootTable", "RecordItem")))
            commands += int("Command" in data)
            spawners += int(any(k in data for k in ("SpawnData", "SpawnPotentials")))
    result = {"blockEntities": tiles, "entities": entities, "pendingTicks": ticks,
              "inventoryOrLootTablePayloads": inventories, "commandPayloads": commands,
              "spawnerPayloads": spawners, "allSourceNBTDiscarded": True}
    if "Regions" in root:
        result["sourceRegions"] = [{"position": [int(r["Position"][a]) for a in "xyz"],
                                    "signedSize": [int(r["Size"][a]) for a in "xyz"]} for r in regions]
    return result


def load_converted(path):
    """Return ids, metadata, true_air, source_occupied, state_report, format.

    All arrays indexed [x,y,z], normalized source minimum corner, no crop,
    mirroring, rotation or rescaling. Litemapy storage already normalizes signed
    region sizes; do NOT reverse arrays for negative Size axes.
    """
    if path.suffix.lower() == ".schematic":
        original_ids, original_meta = read_legacy(path)
        packed = original_ids.astype(np.uint32) * 16 + original_meta
        unique, inverse, counts = np.unique(packed, return_inverse=True, return_counts=True)
        states = []
        for value, count in zip(unique, counts):
            b, d = divmod(int(value), 16)
            ob, od, why = sanitize_legacy(b, d)
            states.append({"sourceState": f"legacy:{b}:{d}", "sourceName": LEGACY_NAMES.get(b, f"unknown_{b}"),
                           "sourceLegacyId": b, "sourceLegacyMeta": d, "legacyId": ob, "meta": od,
                           "reason": why, "count": int(count)})
        ids = np.array([s["legacyId"] for s in states], dtype=np.uint16)[inverse].reshape(original_ids.shape)
        meta = np.array([s["meta"] for s in states], dtype=np.uint8)[inverse].reshape(original_ids.shape)
        return ids, meta, original_ids == 0, (original_ids != 0) & (original_ids != 217), states, "MCEdit legacy (direct Blocks/Data/AddBlocks)"
    blocks, names, fmt, _, _ = load_structure(path)
    counts = np.bincount(blocks.ravel(), minlength=len(names))
    lut = np.zeros((len(names), 2), dtype=np.uint16)
    source_air = np.zeros(len(names), dtype=bool)
    occupied = np.zeros(len(names), dtype=bool)
    states = []
    for index, (name, count) in enumerate(zip(names, counts)):
        if not count:
            continue
        if name is None:
            raise ValueError("Referenced empty palette entry")
        b, d, why = convert_state(name)
        base = parse_state(name)[0]
        lut[index] = b, d
        source_air[index] = base in TRUE_AIR
        occupied[index] = ("minecraft:" + base) not in AIR_NAMES
        states.append({"sourceState": name, "sourceName": base, "legacyId": b, "meta": d,
                       "reason": why, "count": int(count)})
        if occupied[index] and b == 0:
            raise AssertionError(f"Non-air block mapped to air: {name}")
    return lut[blocks, 0], lut[blocks, 1].astype(np.uint8), source_air[blocks], occupied[blocks], states, fmt


def flood_exterior(passable):
    """6-neighbor boundary flood using x-spans, NumPy + deque only (no scipy).

    Input/output order [y,z,x]. Mark spans on enqueue, so large external air
    regions take roughly one queue entry per row, not one per voxel.
    """
    remaining = np.ascontiguousarray(passable, dtype=bool).copy()
    sy, sz, sx = remaining.shape
    queue = deque()

    def claim(y, z, x):
        row = remaining[y, z]
        if not row[x]:
            return x + 1
        low, high = x, x + 1
        # Vector searches avoid a Python loop for every exterior voxel.
        stops = np.flatnonzero(~row[:x])
        low = int(stops[-1]) + 1 if stops.size else 0
        stops = np.flatnonzero(~row[x+1:])
        high = x + 1 + int(stops[0]) if stops.size else sx
        row[low:high] = False
        queue.append((y, z, low, high))
        return high

    def seed_row(y, z):
        indexes = np.flatnonzero(remaining[y, z])
        for x in indexes:
            if remaining[y, z, x]:
                claim(y, z, int(x))

    for y in {0, sy - 1}:
        for z in range(sz):
            seed_row(y, z)
    for z in {0, sz - 1}:
        for y in range(sy):
            seed_row(y, z)
    for y in range(sy):
        for z in range(sz):
            claim(y, z, 0)
            claim(y, z, sx - 1)
    while queue:
        y, z, low, high = queue.popleft()
        for yy, zz in ((y-1, z), (y+1, z), (y, z-1), (y, z+1)):
            if not (0 <= yy < sy and 0 <= zz < sz):
                continue
            indexes = np.flatnonzero(remaining[yy, zz, low:high]) + low
            for x in indexes:
                if remaining[yy, zz, x]:
                    claim(yy, zz, int(x))
    return passable & ~remaining


def distances_to_solid(solid, axis):
    """Nearest architectural voxel strictly before/after each cell on axis."""
    shape = [1] * solid.ndim
    shape[axis] = solid.shape[axis]
    coords = np.arange(solid.shape[axis], dtype=np.int32).reshape(shape)
    before = np.maximum.accumulate(np.where(solid, coords, -100000), axis=axis)
    after = np.flip(np.minimum.accumulate(np.flip(np.where(solid, coords, 100000), axis=axis), axis=axis), axis=axis)
    return coords - before, after - coords


def select_air(ids, true_air):
    """Return placement-air mask [x,y,z] and auditable air accounting."""
    b = ids.transpose(1, 2, 0)
    air = true_air.transpose(1, 2, 0)
    passable = np.isin(b, list(FLOOD_PASSABLE))
    exterior = flood_exterior(passable)
    enclosed = air & ~exterior
    solid = np.isin(b, list(ARCHITECTURAL))
    below, above = distances_to_solid(solid, 0)
    roofed = (below > 0) & (above > 0) & (below + above <= 64)
    sides = np.zeros(b.shape, dtype=np.uint8)
    for axis in (1, 2):
        before, after = distances_to_solid(solid, axis)
        sides += ((before > 0) & (before <= 48)).astype(np.uint8)
        sides += ((after > 0) & (after <= 48)).astype(np.uint8)
    recovered = air & exterior & roofed & (sides >= 3)
    # Never recover directly on a source boundary; these have outside exposure.
    for axis in range(3):
        for edge in (0, recovered.shape[axis] - 1):
            sl = [slice(None)] * 3
            sl[axis] = edge
            recovered[tuple(sl)] = False
    selected = enclosed | recovered
    stats = {"trueSourceAir": int(air.sum()), "enclosedInteriorAir": int(enclosed.sum()),
             "roofedOpenInteriorAir": int(recovered.sum()), "retainedAir": int(selected.sum()),
             "omittedExteriorAir": int((air & ~selected).sum()),
             "openRoomHeuristicUsed": bool(recovered.any())}
    assert stats["retainedAir"] + stats["omittedExteriorAir"] == stats["trueSourceAir"]
    assert not np.any(selected & ~air)
    return selected.transpose(2, 0, 1), stats


def encode_se45(ids, meta, keep_air):
    x, y, z = ids.shape
    if min(x, y, z) <= 0 or x*y*z > 0xffffffff:
        raise ValueError("Invalid SE45 dimensions")
    flat_ids = ids.transpose(1, 2, 0).ravel()
    flat_meta = meta.transpose(1, 2, 0).ravel()
    indexes = np.flatnonzero((flat_ids != 0) | keep_air.transpose(1, 2, 0).ravel())
    records = np.empty(indexes.size, dtype=RECORD)
    records["index"], records["id"], records["meta"] = indexes, flat_ids[indexes], flat_meta[indexes]
    raw = HEADER.pack(b"SE45", x, y, z, indexes.size) + records.tobytes()
    return gzip_bytes(raw)


def decode_se45(payload):
    """Independent strict decoder: returns ids, meta, presence in [x,y,z]."""
    raw = gzip.decompress(payload)
    if len(raw) < HEADER.size:
        raise ValueError("Truncated SE45 header")
    magic, x, y, z, count = HEADER.unpack_from(raw)
    if magic != b"SE45" or min(x, y, z) == 0 or x*y*z > 0xffffffff or count > x*y*z:
        raise ValueError("Invalid header/dimensions/count")
    if len(raw) != 20 + 7 * count:
        raise ValueError("SE45 record count/payload length mismatch")
    rec = np.frombuffer(raw, dtype=RECORD, offset=20, count=count)
    indexes = rec["index"].astype(np.int64)
    if indexes.size and (indexes[-1] >= x*y*z or np.any(np.diff(indexes) <= 0)):
        raise ValueError("SE45 indexes not strictly ascending/unique/in bounds")
    if np.any(rec["meta"] > 15) or not np.all(np.isin(rec["id"], list(LEGACY_NAMES))):
        raise ValueError("Invalid 1.12 ID/Data")
    if np.any(np.isin(rec["id"], list(FORBIDDEN_IDS))):
        raise ValueError("Forbidden native behavior in asset")
    ids = np.zeros(x*y*z, dtype=np.uint16)
    meta = np.zeros(x*y*z, dtype=np.uint8)
    present = np.zeros(x*y*z, dtype=bool)
    ids[indexes], meta[indexes], present[indexes] = rec["id"], rec["meta"], True
    return tuple(a.reshape(y, z, x).transpose(2, 0, 1) for a in (ids, meta, present))


def encode_schematic(ids, meta):
    x, y, z = ids.shape
    if max(x, y, z) > 32767 or np.any(ids > 255):
        raise ValueError("Legacy schematic dimension/ID limit")
    r = nbtlib.File({"Width": nbtlib.Short(x), "Height": nbtlib.Short(y), "Length": nbtlib.Short(z),
                     "Materials": nbtlib.String("Alpha"),
                     "Blocks": nbtlib.ByteArray(ids.transpose(1, 2, 0).ravel().astype(np.uint8).view(np.int8)),
                     "Data": nbtlib.ByteArray(meta.transpose(1, 2, 0).ravel().astype(np.int8)),
                     "Entities": nbtlib.List[nbtlib.Compound](), "TileEntities": nbtlib.List[nbtlib.Compound]()}, root_name="Schematic")
    buf = io.BytesIO()
    r.write(buf)
    return gzip_bytes(buf.getvalue())


def bounds(mask):
    if not mask.any():
        return None
    result = []
    for axis in range(3):
        occupied = np.flatnonzero(np.any(mask, axis=tuple(i for i in range(3) if i != axis)))
        result.append((int(occupied[0]), int(occupied[-1])))
    return {"min": dict(zip("xyz", (v[0] for v in result))), "max": dict(zip("xyz", (v[1] for v in result)))}


def self_tests():
    checks = []
    cases = {
        "oak_stairs[facing=east,half=top,shape=straight]": (53, 4),
        "quartz_stairs[facing=north,half=bottom]": (156, 3),
        "stone_stairs[facing=south,half=top]": (67, 6),
        "cherry_slab[type=top]": (126, 10), "cherry_slab[type=double]": (125, 2),
        "oak_log[axis=x]": (17, 4), "dark_oak_wood[axis=z]": (162, 13),
        "quartz_pillar[axis=z]": (155, 4), "blue_wool": (35, 11),
        "iron_door[half=upper,hinge=right,powered=true]": (71, 11),
        "oak_door[half=lower,facing=north,open=true]": (64, 7),
        "spruce_trapdoor[facing=east,half=top,open=true]": (96, 15),
        "oak_trapdoor[facing=north,half=bottom,open=true]": (96, 4),
        "lever[face=floor,facing=east,powered=true]": (69, 14),
        "lever[face=floor,facing=north,powered=false]": (69, 5),
        "rail[shape=north_east]": (66, 9), "powered_rail[shape=ascending_south,powered=true]": (27, 13),
        "repeater[facing=east,delay=4,powered=true]": (94, 15),
        "comparator[facing=north,mode=subtract,powered=true]": (150, 14),
        "water[level=4]": (8, 4), "snow[layers=8]": (78, 7),
        "red_stained_glass_pane[east=true]": (160, 14),
        "chain[axis=x]": (198, 4), "command_block[facing=up]": (98, 3),
        "spawner": (101, 0), "structure_block": (98, 0), "chest[facing=west]": (54, 4),
        "end_portal_frame[eye=true,facing=north]": (44, 1),
        "prismarine_slab[type=top]": (44, 13), "mossy_cobblestone_wall": (139, 1),
    }
    for state, expected in cases.items():
        assert convert_state(state)[:2] == expected, (state, convert_state(state), expected)
    checks.append(f"{len(cases)} directional/shape/color/safety mapping expectations")
    assert "waterlogging lost" in convert_state("oak_slab[type=bottom,waterlogged=true]")[2]
    try:
        convert_state("minecraft:unknown_block_xyz")
        raise AssertionError("Unknown block silently accepted")
    except ValueError:
        pass
    checks.append("unknown block fails closed and waterlogging loss is explicit")

    # Compare optimized flood to a deliberately simple voxel BFS on random grids.
    rng = np.random.default_rng(45)
    for shape in ((1, 1, 1), (2, 4, 6), (7, 6, 9), (9, 5, 4)):
        for _ in range(5):
            mask = rng.random(shape) > .35
            expected = np.zeros(shape, bool)
            todo = deque()
            for c in np.ndindex(shape):
                if mask[c] and any(c[i] in (0, shape[i]-1) for i in range(3)):
                    expected[c] = True
                    todo.append(c)
            while todo:
                c = todo.popleft()
                for axis in range(3):
                    for sign in (-1, 1):
                        n = list(c); n[axis] += sign; n = tuple(n)
                        if all(0 <= n[i] < shape[i] for i in range(3)) and mask[n] and not expected[n]:
                            expected[n] = True; todo.append(n)
            assert np.array_equal(flood_exterior(mask), expected)
    checks.append("20 random/degenerate flood fills equal independent voxel BFS")

    ids = np.zeros((11, 9, 11), dtype=np.uint16)
    ids[2:9, 1:7, 2:9] = 1
    ids[3:8, 2:6, 3:8] = 0
    kept, stats = select_air(ids, ids == 0)
    assert stats["enclosedInteriorAir"] == 100 and stats["roofedOpenInteriorAir"] == 0
    ids[5, 2:4, 2] = 0  # Open two-high doorway into same room.
    kept, stats = select_air(ids, ids == 0)
    assert kept[5, 2, 5] and stats["roofedOpenInteriorAir"] >= 100
    assert not kept[0, 0, 0] and not np.any(kept & (ids != 0))
    checks.append("closed and open-door rooms retained; exterior corner untouched")

    meta = np.zeros_like(ids, dtype=np.uint8)
    ids[6, 2, 6] = 53; meta[6, 2, 6] = 7; kept[6, 2, 6] = False
    payload = encode_se45(ids, meta, kept)
    decoded = decode_se45(payload)
    assert np.array_equal(ids, decoded[0]) and np.array_equal(meta, decoded[1])
    assert np.array_equal(decoded[2], (ids != 0) | kept)
    assert payload == encode_se45(ids, meta, kept)
    assert struct.unpack_from(">4sIII", gzip.decompress(payload)) == (b"SE45", 11, 9, 11)
    checks.append("SE45 binary layout, asymmetric coordinates, round trip and deterministic gzip")
    raw = gzip.decompress(payload)
    for corrupt in (raw[:-1], b"FAIL" + raw[4:], raw[:16] + struct.pack(">I", 0) + raw[20:],
                    raw[:20] + struct.pack(">IHB", ids.size, 1, 0) + raw[27:]):
        try:
            decode_se45(gzip_bytes(corrupt))
            raise AssertionError("Corrupt SE45 accepted")
        except ValueError:
            pass
    checks.append("truncated payload, bad magic/count and out-of-bounds index rejected")

    # Temporary fixtures stay within reports/conversion* and are removed afterward.
    (ROOT / "reports").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="conversion-test-", dir=ROOT / "reports") as directory:
        p = Path(directory) / "roundtrip.schematic"
        p.write_bytes(encode_schematic(ids, meta))
        a, b = read_legacy(p)
        assert np.array_equal(ids, a) and np.array_equal(meta, b)
        root = nbtlib.load(p)
        assert len(root["Entities"]) == len(root["TileEntities"]) == 0
        # Odd-volume AddBlocks exercises both nibbles and the final low nibble.
        fixture = nbtlib.File({"Width": nbtlib.Short(3), "Height": nbtlib.Short(1), "Length": nbtlib.Short(1),
            "Blocks": nbtlib.ByteArray([1, 2, 3]), "Data": nbtlib.ByteArray([6, 11, 15]),
            "AddBlocks": nbtlib.ByteArray([33, 3])}, root_name="Schematic")
        fixture.save(p, gzipped=True)
        a, b = read_legacy(p)
        assert a[:, 0, 0].tolist() == [257, 514, 771] and b[:, 0, 0].tolist() == [6, 11, 15]
        try:
            sanitize_legacy(771, 15)
            raise AssertionError("Modded numeric ID silently accepted")
        except ValueError:
            pass
    checks.append("sanitized MCEdit round trip; odd AddBlocks high IDs and Data preserved; modded ID rejected")
    return checks


def convert_entry(entry, verify=False):
    path = (ROOT / "inputs" / "source" / entry["file"]).resolve()
    if not path.is_relative_to(ROOT / "inputs" / "source"):
        raise ValueError("Source outside frozen inputs/source")
    digest = sha(path.read_bytes())
    assert digest == entry["sha256"], f"Source hash mismatch: {entry['id']}"
    ids, meta, true_air, occupied, states, fmt = load_converted(path)
    dimensions = dict(zip("xyz", ids.shape))
    assert dimensions == entry["dimensions"], (entry["id"], dimensions, entry["dimensions"])
    assert int(occupied.sum()) == entry["blockCount"], (entry["id"], int(occupied.sum()), entry["blockCount"])
    assert bounds(occupied) == entry["occupiedBounds"], (entry["id"], "source bounds changed")
    assert bounds(ids != 0) == entry["occupiedBounds"], (entry["id"], "converted bounds changed")
    assert np.array_equal(ids != 0, occupied), "Non-air positions must be unchanged"
    assert sum(s["count"] for s in states) == ids.size
    keep_air, air_stats = select_air(ids, true_air)
    payload = encode_se45(ids, meta, keep_air)
    schematic = encode_schematic(ids, meta)
    decoded_ids, decoded_meta, presence = decode_se45(payload)
    assert np.array_equal(ids, decoded_ids) and np.array_equal(meta, decoded_meta)
    assert np.array_equal(presence, occupied | keep_air)
    assert int(presence.sum()) == entry["blockCount"] + air_stats["retainedAir"]
    asset_path = ROOT / "assets" / "structures" / f"{entry['id']}.se45.gz"
    schematic_path = ROOT / "assets" / "schematics" / f"{entry['id']}.schematic"
    for target, data in ((asset_path, payload), (schematic_path, schematic)):
        if verify:
            assert target.read_bytes() == data, f"Rebuild not byte-identical: {target}"
        else:
            output(target).write_bytes(data)
    a, b = read_legacy(schematic_path)
    assert np.array_equal(a, ids) and np.array_equal(b, meta), "Saved schematic differs"
    root = nbtlib.load(schematic_path)
    assert set(root) == {"Width", "Height", "Length", "Materials", "Blocks", "Data", "Entities", "TileEntities"}
    assert not root["Entities"] and not root["TileEntities"]
    assert digest == sha(path.read_bytes()), "Source changed during conversion"
    substitutions = [s for s in states if s["reason"] not in {"exact", "exact legacy ID/Data preserved"}]
    used_pairs, pair_counts = np.unique(ids.astype(np.uint16) * 16 + meta, return_counts=True)
    report = {"id": entry["id"], "title": entry["title"], "source": "inputs/source/" + entry["file"],
              "sourceSha256": digest, "sourceFormat": fmt, "dimensions": dimensions,
              "sourceOccupiedBounds": entry["occupiedBounds"], "convertedOccupiedBounds": bounds(ids != 0),
              "sourceBlockCount": int(occupied.sum()), "convertedNonAir": int((ids != 0).sum()),
              "recordCount": int(presence.sum()), "volume": int(ids.size), "air": air_stats,
              "asset": "structures/" + asset_path.name, "assetSha256": sha(payload), "assetBytes": len(payload),
              "schematic": "schematics/" + schematic_path.name, "schematicSha256": sha(schematic), "schematicBytes": len(schematic),
              "sourcePayloadsDiscarded": source_payload_counts(path),
              "mappedSourceStates": len(states), "substitutedOrLossyStates": len(substitutions),
              "substitutedOrLossyVoxels": sum(s["count"] for s in substitutions),
              "unmappedSourceStates": 0, "silentlyDroppedBlocks": 0,
              "outputStates": [{"legacyId": int(pair)//16, "meta": int(pair)%16, "count": int(count)} for pair, count in zip(used_pairs, pair_counts)],
              "mappings": sorted(states, key=lambda s: s["sourceState"]),
              "checks": ["source SHA256", "catalog dimensions/occupied bounds/count", "non-air positions identical",
                         "SE45 exact length/order/ID/meta/index bounds", "all retained air is true source air",
                         "SE45 decoded arrays equal converted arrays", "saved MCEdit arrays equal converted arrays",
                         "no source NBT/entities/commands/inventories/spawners", "source unchanged after conversion"]}
    if not verify:
        write_json(ROOT / "reports" / f"conversion-{entry['id']}.json", report)
    print(f"{entry['id']}: {report['convertedNonAir']:,} non-air + {air_stats['retainedAir']:,} interior air; "
          f"{len(states)} states; bounds/hash/round-trips OK", flush=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--verify", action="store_true", help="rebuild all assets in memory; assert byte-identical existing outputs")
    args = parser.parse_args()
    checks = self_tests()
    print("Self-tests passed: " + "; ".join(checks), flush=True)
    if args.self_test:
        return
    catalog = json.loads((ROOT / "inputs" / "catalog.json").read_text(encoding="utf-8-sig"))
    entries = catalog["structures"]
    assert len(entries) == 45 and len({e["id"] for e in entries}) == 45
    reports = [convert_entry(entry, verify=args.verify) for entry in entries]
    if args.verify:
        print("VERIFIED: 45 SE45 + 45 MCEdit assets byte-identical to full rebuild; all source hashes/counts/bounds unchanged.", flush=True)
        return
    by_state = {}
    by_name = {}
    for report in reports:
        for state in report["mappings"]:
            name = state["sourceName"]
            item = by_name.setdefault(name, {"count": 0, "states": set(), "structures": set(), "targets": set()})
            item["count"] += state["count"]; item["states"].add(state["sourceState"])
            item["structures"].add(report["id"]); item["targets"].add((state["legacyId"], state["meta"]))
            key = state["sourceState"]
            if key not in by_state:
                by_state[key] = {**state, "count": 0, "structures": {}}
            by_state[key]["count"] += state["count"]
            by_state[key]["structures"][report["id"]] = state["count"]
    mapping_report = {"sourceStates": [by_state[k] for k in sorted(by_state)],
                      "sourceBlockIds": [{"name": name, "count": item["count"], "stateCount": len(item["states"]),
                                          "structures": sorted(item["structures"]), "targets": [list(t) for t in sorted(item["targets"])]}
                                         for name, item in sorted(by_name.items())]}
    write_json(ROOT / "reports" / "conversion-mappings.json", mapping_report)
    totals = {key: sum(r[key] for r in reports) for key in ("sourceBlockCount", "convertedNonAir", "recordCount", "assetBytes", "schematicBytes", "substitutedOrLossyVoxels")}
    totals.update({key: sum(r["air"][key] for r in reports) for key in ("trueSourceAir", "enclosedInteriorAir", "roofedOpenInteriorAir", "retainedAir", "omittedExteriorAir")})
    totals["discardedEntities"] = sum(r["sourcePayloadsDiscarded"]["entities"] for r in reports)
    totals["discardedBlockEntities"] = sum(r["sourcePayloadsDiscarded"]["blockEntities"] for r in reports)
    manifest = {"schemaVersion": 1, "snapshotId": catalog["snapshotId"], "category": "Salvage Expeditions", "namespace": "se45",
                "assetFormat": "gzip(SE45 + big-endian u32 x,y,z,count + count*(u32 index,u16 blockId,u8 meta))",
                "indexFormula": "((y*zSize+z)*xSize+x)", "arrayCoordinates": "[x,y,z]; normalized source minimum corner; no rotation/crop/mirroring",
                "structures": [{k: v for k, v in r.items() if k not in {"mappings", "outputStates"}} | {"mappingReport": f"reports/conversion-{r['id']}.json"} for r in reports],
                "structureCount": 45, "schematicCount": 45, "unmappedSourceStates": 0, "silentlyDroppedBlocks": 0,
                "uniqueSourceStates": len(by_state), "uniqueSourceBlockIds": len(by_name), "totals": totals,
                "airPolicy": AIR_POLICY, "tests": checks,
                "schematicCaution": "Dense sanitized MCEdit files contain external bounding-box air. Use SE45 for safe sparse placement; generic paste with air can clear outside geometry, while ignore-air would fill rooms. A custom importer must use the SE45 presence mask.",
                "runtimeCaution": "Block ID/Data only: empty native containers, default bed/banner/skull/pot appearance, no source NBT. Waterlogging and newer textures/mechanics are approximated. Physics/redstone/fluid updates can still change legacy blocks at runtime; importing requires the parent's staged safe placement boundary."}
    write_json(ROOT / "reports" / "conversion.json", manifest)
    lines = ["# Curated 45 isolated conversion", "", f"Snapshot: `{catalog['snapshotId']}`. All 45 frozen input hashes verified.", "",
             "45 gzip SE45 runtime assets and 45 sanitized MCEdit schematics generated. No source bounds, non-air coordinates, or geometry dimensions changed. No network or server access.", "",
             f"- Source/non-air blocks: {totals['convertedNonAir']:,}", f"- Retained true source air: {totals['retainedAir']:,} ({totals['enclosedInteriorAir']:,} flood-enclosed; {totals['roofedOpenInteriorAir']:,} roofed open-room recovery)",
             f"- Exterior air omitted: {totals['omittedExteriorAir']:,}", f"- SE45 records: {totals['recordCount']:,}",
             f"- Source entities/block entities discarded: {totals['discardedEntities']:,}/{totals['discardedBlockEntities']:,}",
             f"- Unique source states/names: {len(by_state):,}/{len(by_name):,}; unmapped: 0; silently dropped: 0", "",
             "## Air and placement limits", "", *AIR_POLICY.values(), "", manifest["schematicCaution"], "", manifest["runtimeCaution"], "",
             "## Parent integration", "", "`from legacy_mapping import convert_state, read_legacy` (add tools/ to sys.path). `convert_state(state)` returns `(legacyId, meta, reason)`. `read_legacy(path)` returns original lossless `(ids, metadata)` arrays in `[x,y,z]`; apply `sanitize_legacy` to native states. `convert_assets.load_converted(path)` returns the actual sanitized arrays, true-air and occupancy masks, mapping records, format. `decode_se45(bytes)` returns `(ids, metadata, presence)` in `[x,y,z]`; use presence to distinguish retained air from omitted exterior air.", "",
             "Litematic signed dimensions are already normalized in source_reader/litemapy storage. Do not reverse negative axes. Source region positions and signed sizes are recorded per structure. No crop, geometry anchor or content edits were made.", "",
             "`assets/structures/Bxx.se45.gz` matches bundle asset `structures/Bxx.se45.gz`. Detailed per-structure state counts/losses: `reports/conversion-Bxx.json`. Aggregated per-name/per-state report: `reports/conversion-mappings.json`. Manifest: `reports/conversion.json`.", "",
             "## Checks", "", *["- " + c for c in checks], "",
             "Every structure additionally checks frozen SHA256, catalog dimensions/non-air count/occupied bounds, exact non-air positions, retained-air subset, binary record length/order/limits, sanitized NBT, saved MCEdit round trip, SE45 array round trip, and unchanged source hash. Run `python -B tools/convert_assets.py --verify` for a full independent rebuild and byte-for-byte comparison of all 90 outputs.", "",
             "## Asset counts", "", "| ID | Dimensions X/Y/Z | Non-air | Enclosed air | Open-room air | Records |", "|---|---|---:|---:|---:|---:|"]
    for r in reports:
        lines.append(f"| {r['id']} | {'/'.join(str(r['dimensions'][a]) for a in 'xyz')} | {r['convertedNonAir']:,} | {r['air']['enclosedInteriorAir']:,} | {r['air']['roofedOpenInteriorAir']:,} | {r['recordCount']:,} |")
    output(ROOT / "reports" / "conversion.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"completed": 45, "totals": totals, "unmapped": 0}), flush=True)


if __name__ == "__main__":
    main()
