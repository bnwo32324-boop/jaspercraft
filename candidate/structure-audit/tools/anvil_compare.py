"""Compare capture dumps with an Anvil world (READ-ONLY), e.g. the live overworld, cell by cell.

    python anvil_compare.py <dump.jsd ...> [--world GAME/server/world] [--json out.json]

For each natural dump: reads the chunks covering the dump box from <world>/region/r.X.Z.mca (opened read-only,
never written), rebuilds the (id<<4|data) states over the same box and reports how many cells differ --
overall, on structure cells (mask 1), carved cells (mask 2) and terrain (mask 0) -- plus the most common
(capture -> world) pairs. Chunks the world has never generated are reported and skipped.
Purpose: proof that the capture harness reproduces what the live generator actually built.
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import struct
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jsd import read_jsd  # noqa: E402
from stgw import parse_nbt  # noqa: E402

LIVE_WORLD = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\world"


class Region:
    def __init__(self, path):
        with open(path, "rb") as f:          # read-only, one read
            self.b = f.read()

    def chunk(self, cx, cz):
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        loc = struct.unpack_from(">I", self.b, i)[0]
        off, cnt = loc >> 8, loc & 255
        if off == 0 or cnt == 0:
            return None
        n, comp = struct.unpack_from(">IB", self.b, off * 4096)
        raw = self.b[off * 4096 + 5: off * 4096 + 4 + n]
        data = zlib.decompress(raw) if comp == 2 else __import__("gzip").decompress(raw)
        return parse_nbt(data)["Level"]


def chunk_states(level):
    """(256,16,16) uint16 states [y,z,x] of one chunk."""
    out = np.zeros((256, 16, 16), np.uint16)
    for sec in level.get("Sections", []):
        y = sec["Y"] & 255
        blocks = np.frombuffer(sec["Blocks"], np.uint8).astype(np.uint16)
        data = np.frombuffer(sec["Data"], np.uint8)
        nib = np.empty(4096, np.uint16)
        nib[0::2] = data & 15
        nib[1::2] = data >> 4
        ids = blocks
        if "Add" in sec:
            add = np.frombuffer(sec["Add"], np.uint8)
            a = np.empty(4096, np.uint16)
            a[0::2] = add & 15
            a[1::2] = add >> 4
            ids = ids | (a << 8)
        out[y * 16:(y + 1) * 16] = ((ids << 4) | nib).reshape(16, 16, 16)
    return out


def compare(path, world):
    d = read_jsd(path)
    h = d.header
    ox, oy, oz = h["origin"]
    sx, sy, sz = h["size"]
    live = np.zeros_like(d.states)
    have = np.zeros((sz, sx), bool)
    regions, missing, unpopulated = {}, [], []
    for cx in range(ox >> 4, (ox + sx - 1 >> 4) + 1):
        for cz in range(oz >> 4, (oz + sz - 1 >> 4) + 1):
            key = (cx >> 5, cz >> 5)
            if key not in regions:
                p = os.path.join(world, "region", f"r.{key[0]}.{key[1]}.mca")
                regions[key] = Region(p) if os.path.isfile(p) else None
            reg = regions[key]
            lv = reg.chunk(cx, cz) if reg else None
            if lv is None:
                missing.append([cx, cz])
                continue
            if not lv.get("TerrainPopulated"):
                unpopulated.append([cx, cz])
            cs = chunk_states(lv)
            x0, x1 = max(ox, cx * 16), min(ox + sx, cx * 16 + 16)
            z0, z1 = max(oz, cz * 16), min(oz + sz, cz * 16 + 16)
            live[:, z0 - oz:z1 - oz, x0 - ox:x1 - ox] = cs[oy:oy + sy, z0 - cz * 16:z1 - cz * 16, x0 - cx * 16:x1 - cx * 16]
            have[z0 - oz:z1 - oz, x0 - ox:x1 - ox] = True
    cmp = np.broadcast_to(have[None], d.states.shape)
    diff = (live != d.states) & cmp
    m = d.mask if d.mask is not None else np.zeros_like(d.states, np.uint8)
    r = {"file": os.path.basename(path), "id": h["id"], "variant": h["variant"], "name": h["name"],
         "chunksCompared": int(len(set(map(tuple, np.argwhere(have) // 16)))) if have.any() else 0,
         "chunksMissingInWorld": missing, "chunksUnpopulatedInWorld": unpopulated,
         "cellsCompared": int(cmp.sum()), "cellsDiffer": int(diff.sum()),
         "structureCells": int(((m == 1) & cmp).sum()), "structureCellsDiffer": int((diff & (m == 1)).sum()),
         "carvedCells": int(((m == 2) & cmp).sum()), "carvedCellsDiffer": int((diff & (m == 2)).sum()),
         "terrainCellsDiffer": int((diff & (m == 0)).sum())}
    pairs = collections.Counter()
    ys, zs, xs = np.nonzero(diff)
    for y, z, x in zip(ys[:200000], zs[:200000], xs[:200000]):
        a, b = int(d.states[y, z, x]), int(live[y, z, x])
        pairs[(f"{a >> 4}:{a & 15}", f"{b >> 4}:{b & 15}", int(m[y, z, x]))] += 1
    r["topDiffs(capture,world,mask)"] = [[*k, v] for k, v in pairs.most_common(12)]
    return r


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dumps", nargs="+")
    ap.add_argument("--world", default=LIVE_WORLD)
    ap.add_argument("--json")
    a = ap.parse_args()
    rows = []
    for p in a.dumps:
        r = compare(p, a.world)
        rows.append(r)
        print(json.dumps(r))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=1)


if __name__ == "__main__":
    main()
