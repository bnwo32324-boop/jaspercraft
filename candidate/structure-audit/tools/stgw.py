"""Structure Testing Grounds world (STGW v3) reader + plot exporter.

Container (big-endian), as read by JasprGroundsUnpack in site/classes.js:
    "STGW"  uint32 version (=3)
    uint16 n + n bytes UTF-8 folder name
    uint8  dimension                         (chunks live in <folder>/level<dim>/)
    uint32 named-file count, each: uint16 n + name, uint32 size + bytes      (level.dat)
    uint32 chunk count, each: int32 cx, int32 cz, uint32 size + bytes (gzip NBT {Level:{...}})
    nothing left over.
Chunk NBT is the EaglercraftX per-chunk save: root {Level:{xPos,zPos,Sections[{Y,Blocks,Data,Add?,...}],
TileEntities[...], ...}}; Blocks index = y*256 + z*16 + x, Data nibble low for even index.

CLI:
    python stgw.py info
    python stgw.py export [--out DIR] [--only 1,2,45]
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import struct
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jsd import Dump, write_jsd, id_to_filename  # noqa: E402
import mcids  # noqa: E402

GAME = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale"
SA = os.path.join(GAME, "candidate", "structure-audit")
WORLD_JS = os.path.join(GAME, "site", "jaspercraft-grounds-world.js")
INDEX_JS = os.path.join(GAME, "site", "jaspercraft-grounds-index.js")
SRC = os.path.join(GAME, "server", "custom-plugins", "JasprHorrorBiomes")
MEGALITHS = os.path.join(SRC, "src", "chat", "jaspr", "biomes", "Megaliths.java")
DUNGEONS = os.path.join(SRC, "src", "chat", "jaspr", "biomes", "Dungeons.java")
CATALOG = os.path.join(SRC, "resources", "structures", "catalog-v1.tsv")

GLASS_IDS = {20, 95, 102, 160}


# ------------------------------------------------------------------------------ NBT
class _NBT:
    __slots__ = ("b", "p")

    def __init__(self, b: bytes):
        self.b = b
        self.p = 0

    def u8(self):
        v = self.b[self.p]
        self.p += 1
        return v

    def s(self, fmt, n):
        v = struct.unpack_from(fmt, self.b, self.p)[0]
        self.p += n
        return v

    def string(self):
        n = self.s(">H", 2)
        v = self.b[self.p:self.p + n].decode("utf-8", "replace")
        self.p += n
        return v

    def payload(self, t):
        if t == 1:
            return self.s(">b", 1)
        if t == 2:
            return self.s(">h", 2)
        if t == 3:
            return self.s(">i", 4)
        if t == 4:
            return self.s(">q", 8)
        if t == 5:
            return self.s(">f", 4)
        if t == 6:
            return self.s(">d", 8)
        if t == 7:
            n = self.s(">i", 4)
            v = self.b[self.p:self.p + n]
            self.p += n
            return v
        if t == 8:
            return self.string()
        if t == 9:
            et = self.u8()
            n = self.s(">i", 4)
            return [self.payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = self.u8()
                if tt == 0:
                    return d
                name = self.string()
                d[name] = self.payload(tt)
        if t == 11:
            n = self.s(">i", 4)
            v = struct.unpack_from(">%di" % n, self.b, self.p)
            self.p += 4 * n
            return list(v)
        if t == 12:
            n = self.s(">i", 4)
            v = struct.unpack_from(">%dq" % n, self.b, self.p)
            self.p += 8 * n
            return list(v)
        raise ValueError(f"bad NBT tag {t} at {self.p}")


def parse_nbt(b: bytes) -> dict:
    r = _NBT(b)
    t = r.u8()
    if t != 10:
        raise ValueError("root is not a compound")
    r.string()
    return r.payload(10)


# ------------------------------------------------------------------------ container
def read_container(path: str = WORLD_JS) -> dict:
    with open(path, "rb") as f:
        b = f.read()
    if b[:4] != b"STGW":
        raise ValueError("not an STGW container")
    version = struct.unpack_from(">I", b, 4)[0]
    p = 8
    n = struct.unpack_from(">H", b, p)[0]
    p += 2
    folder = b[p:p + n].decode("utf-8")
    p += n
    dim = b[p]
    p += 1
    named = struct.unpack_from(">I", b, p)[0]
    p += 4
    files = {}
    for _ in range(named):
        ln = struct.unpack_from(">H", b, p)[0]
        p += 2
        name = b[p:p + ln].decode("utf-8")
        p += ln
        size = struct.unpack_from(">I", b, p)[0]
        p += 4
        files[name] = b[p:p + size]
        p += size
    nch = struct.unpack_from(">I", b, p)[0]
    p += 4
    chunks = {}
    for _ in range(nch):
        cx, cz, ln = struct.unpack_from(">iiI", b, p)
        p += 12
        chunks[(cx, cz)] = b[p:p + ln]
        p += ln
    if p != len(b):
        raise ValueError(f"{len(b) - p} bytes left over")
    return {"version": version, "folder": folder, "dim": dim, "files": files, "chunks": chunks, "bytes": len(b)}


def chunk_file_name(cx: int, cz: int) -> str:
    """EaglerChunkLoader.chunkFileName (for reference / verification)."""
    hexd = "0123456789ABCDEF"
    d, e = cx + 1900000, cz + 1900000
    a = [hexd[(d >> (h * 4)) & 15] for h in range(6)] + [hexd[(e >> (h * 4)) & 15] for h in range(6)]
    return "".join(a) + ".dat"


class World:
    """Lazy block access over the container's chunks."""

    def __init__(self, path: str = WORLD_JS):
        self.c = read_container(path)
        self.raw = self.c["chunks"]
        self.cache = {}

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.cache:
            return self.cache[key]
        raw = self.raw.get(key)
        if raw is None:
            self.cache[key] = None
            return None
        root = parse_nbt(gzip.decompress(raw))
        lvl = root["Level"]
        if lvl.get("xPos") != cx or lvl.get("zPos") != cz:
            raise ValueError(f"chunk {key} claims {lvl.get('xPos')},{lvl.get('zPos')}")
        states = np.zeros((256, 16, 16), dtype=np.uint16)
        for sec in lvl.get("Sections", []):
            y = sec["Y"]
            if y < 0 or y > 15:
                continue
            blocks = np.frombuffer(sec["Blocks"], dtype=np.uint8).astype(np.uint16)
            if "Add" in sec:
                add = np.frombuffer(sec["Add"], dtype=np.uint8)
                addn = np.empty(4096, dtype=np.uint16)
                addn[0::2] = add & 15
                addn[1::2] = add >> 4
                blocks = blocks | (addn << 8)
            dat = np.frombuffer(sec["Data"], dtype=np.uint8)
            datn = np.empty(4096, dtype=np.uint16)
            datn[0::2] = dat & 15
            datn[1::2] = dat >> 4
            states[y * 16:(y + 1) * 16] = ((blocks << 4) | datn).reshape(16, 16, 16)
        tiles = lvl.get("TileEntities", []) or []
        out = {"states": states, "tiles": tiles, "entities": lvl.get("Entities", []) or []}
        self.cache[key] = out
        return out

    def box(self, x0, y0, z0, x1, y1, z1):
        """World box half-open -> (states[y,z,x] uint16, tile entity compounds inside)."""
        st = np.zeros((y1 - y0, z1 - z0, x1 - x0), dtype=np.uint16)
        tiles = []
        for cx in range(x0 >> 4, ((x1 - 1) >> 4) + 1):
            for cz in range(z0 >> 4, ((z1 - 1) >> 4) + 1):
                ch = self.chunk(cx, cz)
                if ch is None:
                    continue
                bx0, bx1 = max(x0, cx * 16), min(x1, cx * 16 + 16)
                bz0, bz1 = max(z0, cz * 16), min(z1, cz * 16 + 16)
                st[:, bz0 - z0:bz1 - z0, bx0 - x0:bx1 - x0] = \
                    ch["states"][y0:y1, bz0 - cz * 16:bz1 - cz * 16, bx0 - cx * 16:bx1 - cx * 16]
                for t in ch["tiles"]:
                    if x0 <= t.get("x", -1 << 30) < x1 and y0 <= t.get("y", -1) < y1 and z0 <= t.get("z", -1 << 30) < z1:
                        tiles.append(t)
        return st, tiles


# ------------------------------------------------------------------------ index/maps
def load_index(path: str = INDEX_JS) -> dict:
    s = open(path, encoding="utf-8").read()
    return json.loads(s[s.index("{"):s.rindex("}") + 1])


def _java_string_array(src: str, name: str):
    m = re.search(r"\b" + name + r"\s*=\s*(?:new\s+String\s*\[\s*\]\s*)?\{(.*?)\};", src, re.S)
    if not m:
        raise ValueError(f"{name} not found")
    return re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))


def id_maps():
    c_name = _java_string_array(open(MEGALITHS, encoding="utf-8").read(), "C_NAME")
    d_name = _java_string_array(open(DUNGEONS, encoding="utf-8").read(), "D_NAME")
    cat = {}
    for line in open(CATALOG, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        f = line.rstrip("\n").split("|")
        cat[f[0]] = {"name": f[1], "family": f[2], "tier": int(f[3]), "mode": f[6]}
    return c_name, d_name, cat


def plot_id(p, c_name, d_name, cat):
    """Map an index plot to its CONVENTIONS id. Returns (id, note)."""
    k = p["k"]
    if k == "setpiece":
        hits = [i for i, n in enumerate(c_name) if n == p["n"]]
        if len(hits) != 1:
            raise ValueError(f"#{p['d']} {p['n']}: {len(hits)} C_NAME matches")
        return f"reg:{hits[0]}", ""
    if k == "dungeon":
        hits = [i for i, n in enumerate(d_name) if n == p["n"]]
        if len(hits) == 1:
            return f"dun:{hits[0]}", ""
        if p["i"] == "dun_spawner":
            # Dungeons.plain(): the classic spawner room; it has no D_NAME entry (D_NAME has 14 names,
            # 0..13). CONVENTIONS counts 15 dungeon rooms (0..14), so it takes the next index.
            return f"dun:{len(d_name)}", "Spawner Room = Dungeons.plain(), not in D_NAME; assigned index len(D_NAME)"
        raise ValueError(f"#{p['d']} {p['n']}: dungeon not in D_NAME")
    if k == "catalogue":
        cid = p["i"][4:] if p["i"].startswith("cat_") else p["i"]
        r = cat.get(cid)
        if r is None:
            hits = [c for c, v in cat.items() if v["name"] == p["n"] and v["family"] == p["f"] and v["tier"] == p["t"]]
            if len(hits) != 1:
                raise ValueError(f"#{p['d']} {p['n']}: catalogue design not found")
            cid = hits[0]
            r = cat[cid]
        if r["name"] != p["n"] or r["family"] != p["f"] or r["tier"] != p["t"]:
            raise ValueError(f"#{p['d']}: index/tsv mismatch {p['n']} vs {r['name']}")
        return f"cat:{cid}", ""
    if k == "ruin":
        fam = p["i"][5:] if p["i"].startswith("ruin_") else p["n"].lower()
        return f"ruin:{fam}", ""
    raise ValueError(f"unknown kind {k}")


# ---------------------------------------------------------------------------- tiles
_MOB_BUKKIT = {"zombie_pigman": "PIG_ZOMBIE", "villager_golem": "IRON_GOLEM", "snowman": "SNOWMAN",
               "mooshroom": "MUSHROOM_COW", "ocelot": "OCELOT", "evocation_illager": "EVOKER",
               "vindication_illager": "VINDICATOR", "illusion_illager": "ILLUSIONER", "zombie_villager": "ZOMBIE_VILLAGER",
               "ender_dragon": "ENDER_DRAGON", "cave_spider": "CAVE_SPIDER", "wither_skeleton": "WITHER_SKELETON",
               "magma_cube": "MAGMA_CUBE", "polar_bear": "POLAR_BEAR", "elder_guardian": "ELDER_GUARDIAN",
               "zombie_horse": "ZOMBIE_HORSE", "skeleton_horse": "SKELETON_HORSE"}


def _sign_text(v):
    if not isinstance(v, str):
        return ""
    try:
        j = json.loads(v)
    except Exception:
        return v

    def flat(o):
        if isinstance(o, str):
            return o
        if isinstance(o, list):
            return "".join(flat(x) for x in o)
        if isinstance(o, dict):
            return (o.get("text", "") or "") + "".join(flat(x) for x in o.get("extra", []) or [])
        return ""
    return flat(j)


def convert_tile(t, origin, unknown_items):
    tid = str(t.get("id", "")).split(":")[-1].lower()
    rx, ry, rz = t["x"] - origin[0], t["y"] - origin[1], t["z"] - origin[2]
    base = {"x": rx, "y": ry, "z": rz}
    if tid in ("chest", "trapped_chest", "dispenser", "dropper", "hopper", "furnace", "brewing_stand",
               "shulker_box") or tid.endswith("shulker_box"):
        items = []
        for it in t.get("Items", []) or []:
            name = it.get("id", "")
            num = mcids.item_id(name) if isinstance(name, str) else name
            if num is None:
                unknown_items.add(name)
                num = name
            items.append([num, int(it.get("Damage", 0)), int(it.get("Count", 0))])
        kind = "chest" if tid in ("chest", "trapped_chest") else tid
        d = dict(base, kind=kind, items=items)
        if tid == "trapped_chest":
            d["trapped"] = True
        if t.get("LootTable"):
            d["lootTable"] = t["LootTable"]
        return d
    if tid in ("mob_spawner",):
        sd = t.get("SpawnData") or {}
        mid = sd.get("id") or t.get("EntityId") or ""
        m = str(mid).split(":")[-1]
        mob = _MOB_BUKKIT.get(m, m.upper())
        return dict(base, kind="spawner", mob=mob, mobId=str(mid), delay=t.get("Delay"))
    if tid == "sign":
        return dict(base, kind="sign", lines=[_sign_text(t.get(f"Text{i}")) for i in range(1, 5)])
    if tid in ("flower_pot",):
        return dict(base, kind="flower_pot", item=t.get("Item"), data=t.get("Data"))
    if tid in ("skull",):
        return dict(base, kind="skull", type=t.get("SkullType"), rot=t.get("Rot"))
    if tid in ("bed",):
        return dict(base, kind="bed", color=t.get("color"))
    if tid in ("banner",):
        return dict(base, kind="banner", base_color=t.get("Base"))
    return dict(base, kind=tid or "tile")


# --------------------------------------------------------------------------- export
def plot_interior(p):
    """World-x/z half-open range of the plot interior (inside the stained-clay border ring)."""
    return p["x"] + 1, p["z"] + 1, p["x"] + p["c"] - 1, p["z"] + p["c"] - 1


def export_plot(w: World, p: dict, sid: str, note: str, outdir: str, set_name="grounds-w3", unknown_items=None):
    unknown_items = set() if unknown_items is None else unknown_items
    base_y = 4
    x0, z0 = p["px"], p["pz"]
    x1, z1 = x0 + p["sx"], z0 + p["sz"]
    y0, y1 = p["py"], p["py"] + p["sy"]
    # scan the plot interior (inside the border ring, away from corner posts / gate signs) for any
    # non-air block above the ground outside the footprint (glass tank or stray structure parts)
    ix0, iz0, ix1, iz1 = plot_interior(p)
    ix0, iz0, ix1, iz1 = ix0 + 1, iz0 + 1, ix1 - 1, iz1 - 1   # skip the posts / signs next to the ring
    top = 256
    st, _ = w.box(ix0, base_y, iz0, ix1, top, iz1)
    ids = st >> 4
    occ = ids != 0
    fx0, fz0, fx1, fz1 = x0 - ix0, z0 - iz0, x1 - ix0, z1 - iz0
    outside = occ.copy()
    outside[:, fz0:fz1, fx0:fx1] = False
    outside[y1 - base_y:, fz0:fz1, fx0:fx1] = occ[y1 - base_y:, fz0:fz1, fx0:fx1]   # above the footprint too
    flags = {}
    cx0, cz0, cx1, cz1, cy1 = x0, z0, x1, z1, y1
    if outside.any():
        oys, ozs, oxs = np.nonzero(outside)
        oid = ids[outside]
        glass = np.isin(oid, list(GLASS_IDS))
        # outside blocks that are glass (the tank) or enclosed by the tank's bbox get included
        bx0, bx1 = int(oxs.min()) + ix0, int(oxs.max()) + ix0 + 1
        bz0, bz1 = int(ozs.min()) + iz0, int(ozs.max()) + iz0 + 1
        by1 = int(oys.max()) + base_y + 1
        flags["outsideFootprint"] = {"blocks": int(outside.sum()), "glass": int(glass.sum()),
                                     "bbox": [bx0, base_y, bz0, bx1, by1, bz1]}
        if glass.sum() >= 0.5 * outside.sum():
            flags["glassTank"] = True
        cx0, cz0 = min(cx0, bx0), min(cz0, bz0)
        cx1, cz1 = max(cx1, bx1), max(cz1, bz1)
        cy1 = max(cy1, by1)
    # does the footprint itself contain a tank? (glass shell on the footprint's outer faces with water inside)
    st, tiles = w.box(cx0, y0, cz0, cx1, cy1, cz1)
    sid_ = st >> 4
    if not flags.get("glassTank"):
        wall = np.concatenate([sid_[:, :, 0].ravel(), sid_[:, :, -1].ravel(), sid_[:, 0, :].ravel(), sid_[:, -1, :].ravel()])
        nz = wall[wall != 0]
        liquid = np.isin(sid_, [8, 9, 10, 11]).sum()
        if nz.size and np.isin(nz, list(GLASS_IDS)).mean() > 0.6 and liquid > 0:
            flags["glassTank"] = True
            flags["glassTankInFootprint"] = True
    origin = [cx0, y0, cz0]
    ctiles = [convert_tile(t, origin, unknown_items) for t in tiles]
    ctiles.sort(key=lambda t: (t["y"], t["z"], t["x"]))
    header = {
        "format": "JSD1", "id": sid, "variant": "g", "set": set_name, "name": p["n"], "context": "grounds",
        "origin": origin, "size": [cx1 - cx0, cy1 - y0, cz1 - cz0],
        "site": {"x": p["px"], "z": p["pz"], "floorY": p["py"], "sizeX": p["sx"], "sizeZ": p["sz"], "height": p["sy"],
                 "mode": p["m"]},
        "tiles": ctiles, "hasMask": False, "seed": "", "notes": note,
        "grounds": {"number": p["d"], "indexId": p["i"], "author": p["a"], "group": p["g"], "family": p["f"],
                    "tier": p["t"], "kind": p["k"], "mode": p["m"], "blurb": p.get("b", ""),
                    "plot": {"x": p["x"], "z": p["z"], "cell": p["c"]},
                    "footprint": [p["px"], p["py"], p["pz"], p["sx"], p["sy"], p["sz"]],
                    "landing": [p.get("tx"), p.get("ty"), p.get("tz")],
                    "groundY": base_y - 1, "groundBlock": "grass (superflat, y<=3)"},
    }
    header["grounds"].update(flags)
    # the structure's own footprint inside this dump (x0,z0,x1,z1 half-open, relative); cells outside it
    # are the grounds' glass tank (not part of the live structure)
    header["grounds"]["footprintRel"] = [x0 - cx0, z0 - cz0, x1 - cx0, z1 - cz0]
    if flags.get("glassTank"):
        header["notes"] = (note + "; " if note else "") + \
            "glass tank of the Testing Grounds included (cells outside grounds.footprintRel); not part of the structure"
    d = Dump(header, st, None)
    fn = os.path.join(outdir, id_to_filename(sid) + "__g.jsd")
    write_jsd(fn, d)
    return fn, header


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["info", "export"])
    ap.add_argument("--out", default=os.path.join(SA, "dumps", "grounds-w3"))
    ap.add_argument("--only", default="")
    a = ap.parse_args(argv)
    t0 = time.time()
    w = World()
    c = w.c
    idx = load_index()
    if a.cmd == "info":
        print(f"STGW v{c['version']} folder={c['folder']} dim={c['dim']} files={list(c['files'])} "
              f"chunks={len(c['chunks'])} bytes={c['bytes']}")
        lvl = parse_nbt(gzip.decompress(c["files"]["level.dat"]))
        print(json.dumps({k: v for k, v in lvl.get("Data", {}).items() if not isinstance(v, (bytes, dict, list))})[:800])
        print("plots:", len(idx["plots"]))
        return
    os.makedirs(a.out, exist_ok=True)
    c_name, d_name, cat = id_maps()
    only = {int(s) for s in a.only.split(",") if s.strip()}
    mp = {}
    unknown = set()
    seen = {}
    for p in idx["plots"]:
        if only and p["d"] not in only:
            continue
        sid, note = plot_id(p, c_name, d_name, cat)
        if sid in seen:
            raise ValueError(f"duplicate id {sid}: #{seen[sid]} and #{p['d']}")
        seen[sid] = p["d"]
        fn, h = export_plot(w, p, sid, note, a.out, unknown_items=unknown)
        g = h["grounds"]
        mp[str(p["d"])] = {"id": sid, "name": p["n"], "author": p["a"], "kind": p["k"], "group": p["g"],
                           "family": p["f"], "tier": p["t"], "mode": p["m"],
                           "footprint": {"x": p["px"], "y": p["py"], "z": p["pz"], "sx": p["sx"], "sy": p["sy"],
                                         "sz": p["sz"]},
                           "exported": {"origin": h["origin"], "size": h["size"]},
                           "file": os.path.basename(fn),
                           "glassTank": bool(g.get("glassTank")),
                           "outsideFootprint": g.get("outsideFootprint"),
                           "tiles": {k: sum(1 for t in h["tiles"] if t["kind"] == k) for k in
                                     sorted({t["kind"] for t in h["tiles"]})},
                           "note": note}
    if not only:
        with open(os.path.join(a.out, "_map.json"), "w", encoding="utf-8") as f:
            json.dump({"set": "grounds-w3", "source": WORLD_JS, "index": INDEX_JS,
                       "idRules": {"reg": "index into Megaliths.C_NAME", "dun": "index into Dungeons.D_NAME "
                                   "(dun:14 = Spawner Room, Dungeons.plain, has no D_NAME entry)",
                                   "cat": "catalog-v1.tsv design id", "ruin": "JasprApocalypse Ruins.Family"},
                       "unknownItemNames": sorted(unknown), "plots": mp}, f, indent=1)
    print(f"exported {len(mp)} plots to {a.out} in {time.time() - t0:.1f}s; unknown item names: {sorted(unknown)}")


if __name__ == "__main__":
    main()
