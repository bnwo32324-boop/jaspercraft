"""Sanity checks for capture-harness dumps (natural + iso), using jsd.py.

    python capture_check.py <dumps dir or .jsd files...> [--sites SA/sites.json] [--json out.json]

Per natural dump <id>__<v>.jsd:
  header   format/id/variant/set/context/origin/size/site/hasMask/seed present and consistent with the file name
  mask     number of mask==1 (structure solid) and mask==2 (carved) cells; FAIL if no mask==1 cell
  place    tight XZ box of mask==1 vs the predicted footprint (site x/z/sizeX/sizeZ): overlap fraction of the
           mask inside the footprint and of the footprint covered by the mask; offsets of the four edges
  tiles    chests (with item stacks), spawners (with mob), signs; how many sit on mask==1 cells
  iso      <id>__<v>iso.jsd exists, equals the natural dump's mask==1 cells, box = tight mask==1 box
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jsd import read_jsd, split_stem  # noqa: E402

REQ = ["format", "id", "variant", "set", "name", "context", "origin", "size", "site", "tiles", "hasMask", "seed"]


def check(path, sites=None):
    out = {"file": os.path.basename(path), "problems": [], "notes": []}
    p = out["problems"]      # hard failures of the capture itself
    notes = out["notes"]     # observations about the structure / world (not capture failures)
    d = read_jsd(path)
    h = d.header
    for k in REQ:
        if k not in h:
            p.append(f"header missing {k}")
    sid, var = split_stem(os.path.splitext(os.path.basename(path))[0])
    if h.get("id") != sid or h.get("variant") != var:
        p.append(f"name/header mismatch {sid}@{var} vs {h.get('id')}@{h.get('variant')}")
    if h.get("format") != "JSD1" or h.get("context") != "natural" or not h.get("hasMask") or d.mask is None:
        p.append("not a natural dump with mask")
    out.update(id=h.get("id"), variant=h.get("variant"), name=h.get("name"), size=h.get("size"),
               builder=h.get("builder"), mode=(h.get("site") or {}).get("mode"))
    if d.mask is None:
        return out
    m1, m2 = int((d.mask == 1).sum()), int((d.mask == 2).sum())
    out["mask"] = {"solid": m1, "air": m2}
    if m1 == 0:
        p.append("EMPTY MASK")
        return out
    # consistency with the header's own counts
    mc = h.get("maskCounts", {})
    if mc and (mc.get("solid") != m1 or mc.get("air") != m2):
        p.append(f"maskCounts header {mc} != file {m1}/{m2}")
    # structure blocks: mask==1 must never be air in the natural states, mask==2 must be air unless changed later
    ids = d.ids
    solid_air = int(((d.mask == 1) & (ids == 0)).sum())
    if solid_air:
        notes.append(f"{solid_air} mask==1 cells are air in the world (removed after the builder wrote them)")
    ox, oy, oz = h["origin"]
    bb = d.nonair_bbox(use_mask=True)
    x0, y0, z0, x1, y1, z1 = bb
    mbox = [ox + x0, oy + y0, oz + z0, ox + x1, oy + y1, oz + z1]
    out["maskBox"] = mbox
    if h.get("maskBox") and h["maskBox"] != mbox:
        p.append(f"header maskBox {h['maskBox']} != {mbox}")
    s = h.get("site") or {}
    fx0, fz0, fx1, fz1 = s.get("x"), s.get("z"), s.get("x", 0) + s.get("sizeX", 0), s.get("z", 0) + s.get("sizeZ", 0)
    # overlap in XZ between mask==1 columns and the predicted footprint
    cols = (d.mask == 1).any(axis=0)                     # (SZ, SX)
    zs, xs = np.nonzero(cols)
    wx, wz = xs + ox, zs + oz
    inside = (wx >= fx0) & (wx < fx1) & (wz >= fz0) & (wz < fz1)
    foot_cells = max(1, (fx1 - fx0) * (fz1 - fz0))
    out["place"] = {"footprint": [fx0, fz0, fx1, fz1], "maskColsInFootprint": round(float(inside.mean()), 4),
                    "footprintCovered": round(float(inside.sum()) / foot_cells, 4),
                    "edgeOffsets(dx0,dz0,dx1,dz1)": [mbox[0] - fx0, mbox[2] - fz0, mbox[3] - fx1, mbox[5] - fz1],
                    "floorY": s.get("floorY"), "maskY": [mbox[1], mbox[4]]}
    if inside.mean() < 0.5:
        notes.append(f"only {inside.mean():.0%} of mask columns inside the predicted footprint")
    # tiles
    tl = h.get("tiles", [])
    kinds = {}
    for t in tl:
        kk = t["kind"] + ("(no te)" if t.get("te") is False else "")
        kinds[kk] = kinds.get(kk, 0) + 1
    chests = [t for t in tl if t["kind"] == "chest" and t.get("te") is not False]
    out["tiles"] = {"kinds": kinds,
                    "chestsOnMask": sum(1 for t in tl if t["kind"] == "chest" and d.mask[t["y"], t["z"], t["x"]] == 1),
                    "chestItemStacks": sum(len(t.get("items", [])) for t in chests),
                    "emptyChests": sum(1 for t in chests if not t.get("items")),
                    "spawners": sorted({t.get("mob") for t in tl if t["kind"] == "spawner"}),
                    "spawnersOnMask": sum(1 for t in tl if t["kind"] == "spawner" and d.mask[t["y"], t["z"], t["x"]] == 1),
                    "namedItems": sorted({n for t in chests for n in (t.get("itemNames") or []) if n})[:8]}
    for t in tl:   # tile cell must hold the right block
        bid = int(ids[t["y"], t["z"], t["x"]])
        want = {"chest": (54, 146), "spawner": (52,), "sign": (63, 68)}.get(t["kind"])
        if want and bid not in want:
            p.append(f"{t['kind']} tile on block {bid}")
    out["hookMismatch"] = h.get("hookMismatch")
    # iso
    iso_path = path[:-4] + "iso.jsd"
    if not os.path.isfile(iso_path):
        p.append("iso dump missing")
    else:
        iso = read_jsd(iso_path)
        ih = iso.header
        if ih.get("context") != "iso" or ih.get("hasMask") or ih.get("variant") != h["variant"] + "iso":
            p.append("iso header wrong")
        if list(ih["origin"]) != mbox[:3] or list(ih["size"]) != [x1 - x0, y1 - y0, z1 - z0]:
            p.append(f"iso box {ih['origin']}+{ih['size']} != mask box {mbox}")
        else:
            crop = d.states[y0:y1, z0:z1, x0:x1]
            keep = d.mask[y0:y1, z0:z1, x0:x1] == 1
            expect = np.where(keep, crop, 0)
            if not np.array_equal(expect, iso.states):
                p.append(f"iso states differ from natural mask==1 cells in {int((expect != iso.states).sum())} cells")
            if len(ih.get("tiles", [])) != sum(1 for t in tl if d.mask[t["y"], t["z"], t["x"]] == 1):
                p.append("iso tile count != masked tiles")
        out["iso"] = {"size": ih.get("size")}
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--json")
    a = ap.parse_args()
    files = []
    for q in a.paths:
        if os.path.isdir(q):
            files += sorted(f for f in glob.glob(os.path.join(q, "*.jsd")) if not f.endswith("iso.jsd"))
        else:
            files.append(q)
    rows, bad = [], 0
    for f in files:
        r = check(f)
        rows.append(r)
        bad += bool(r["problems"])
        pl = r.get("place", {})
        tl = r.get("tiles", {})
        print(f"{r['id']}@{r['variant']:<2} {str(r.get('name'))[:26]:<26} {str(r.get('mode')):<10} builder={r.get('builder')} "
              f"mask={r.get('mask')} box={r.get('maskBox')} inFoot={pl.get('maskColsInFootprint')} "
              f"cover={pl.get('footprintCovered')} edges={pl.get('edgeOffsets(dx0,dz0,dx1,dz1)')} "
              f"tiles={tl.get('kinds')} chestItems={tl.get('chestItemStacks')} empty={tl.get('emptyChests')} "
              f"mobs={tl.get('spawners')} mismatch={r.get('hookMismatch')}")
        for prob in r["problems"]:
            print(f"     PROBLEM: {prob}")
        for n in r["notes"]:
            print(f"     note: {n}")
    print(f"{len(rows)} dumps, {bad} with problems")
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=1)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
